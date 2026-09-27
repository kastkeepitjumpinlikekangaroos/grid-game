package com.gridgame.server

import com.gridgame.common.Constants
import com.gridgame.common.model.Player
import com.gridgame.common.observability.Attrs
import com.gridgame.common.observability.Metrics
import com.gridgame.common.observability.Telemetry
import com.gridgame.common.protocol._
import com.gridgame.server.account.{AccountQueries, AuthDatabase, AuthService}
import com.gridgame.server.game.GameInstance
import com.gridgame.server.lobby._
import com.gridgame.server.net._
import io.netty.channel.Channel

import java.net.InetSocketAddress
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import scala.jdk.CollectionConverters._

/**
 * The server: its parts put together, and where every packet that passes the network's checks is
 * sent on to the part that handles it.
 *
 *  - net:     sockets, sessions, replay and rate limits, and the Outbox everything is sent through
 *  - account: logins, profiles and the leaderboard, over the account database
 *  - lobby:   lobbies, ranked matchmaking, and starting a lobby's match (MatchLauncher)
 *  - game:    one running match (GameInstance), which a lobby holds; bots play in one
 *
 * What spans them lives here: the players who are connected at all, a player joining, leaving or
 * going quiet, chat (ChatRelay) and the end of a match (MatchEnd).
 */
class GameServer(port: Int) extends PacketRouter with LobbyHost {
  // Global state: all connected players regardless of lobby/game
  private val connectedPlayers = new ConcurrentHashMap[UUID, Player]()

  val rateLimiter = new RateLimiter()
  val replayGuard = new ReplayGuard()
  val sessions = new Sessions(rateLimiter, replayGuard)
  val outbox = new Outbox(sessions)

  val authDatabase = new AuthDatabase()
  private val auth = new AuthService(authDatabase, sessions, rateLimiter, outbox)
  private val accountQueries = new AccountQueries(authDatabase, outbox, id => connectedPlayers.containsKey(id))

  val lobbyManager = new LobbyManager()
  private val launcher = new MatchLauncher(this)
  val rankedQueue = new RankedQueue(this, lobbyManager, launcher)
  val lobbyHandler = new LobbyHandler(this, lobbyManager, rankedQueue, launcher)
  private val gameInstances = new ConcurrentHashMap[Short, GameInstance]()
  private val chat = new ChatRelay(lobbyManager, rateLimiter, outbox, getConnectedPlayer)
  private val matchEnd = new MatchEnd(lobbyManager, authDatabase, outbox, getConnectedPlayer, unregisterGameInstance)

  private val cleanupExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()

  private val network = new ServerNetwork(port, TlsProvider.serverContext(), () => tcpHandler(), () => udpHandler(), outbox)

  // A login that replaces a session the server still holds open ends that session as leaving would
  sessions.onSessionReplaced = playerId => leaveEverything(playerId, connectedPlayers.get(playerId))

  /** The handler each TCP connection's pipeline ends in. */
  private[server] def tcpHandler(): TcpHandler = new TcpHandler(sessions, rateLimiter, replayGuard, this)

  /** The handler of the UDP socket. */
  private[server] def udpHandler(): UdpHandler = new UdpHandler(sessions, rateLimiter, replayGuard, this)

  // Async gauges — read these atomically on each metric scrape
  private val meter = Telemetry.meter("com.gridgame.server")
  private val connectedPlayersGauge = meter.gaugeBuilder("gridgame.connections.active")
    .setDescription("Currently connected players")
    .setUnit("{player}")
    .ofLongs()
    .buildWithCallback { obs =>
      obs.record(connectedPlayers.size().toLong, io.opentelemetry.api.common.Attributes.empty())
    }
  private val activeLobbiesGauge = meter.gaugeBuilder("gridgame.lobbies.active")
    .setDescription("Active lobbies")
    .setUnit("{lobby}")
    .ofLongs()
    .buildWithCallback { obs =>
      obs.record(lobbyManager.getActiveLobbies.size.toLong, io.opentelemetry.api.common.Attributes.empty())
    }
  private val activeInstancesGauge = meter.gaugeBuilder("gridgame.instances.active")
    .setDescription("Active game instances")
    .setUnit("{instance}")
    .ofLongs()
    .buildWithCallback { obs =>
      obs.record(gameInstances.size().toLong, io.opentelemetry.api.common.Attributes.empty())
    }
  private val activeSessionsGauge = meter.gaugeBuilder("gridgame.sessions.active")
    .setDescription("Active session tokens")
    .setUnit("{session}")
    .ofLongs()
    .buildWithCallback { obs =>
      obs.record(sessions.sessionTokens.size().toLong, io.opentelemetry.api.common.Attributes.empty())
    }

  def start(): Unit = {
    println(s"Game server starting on port $port (lobby mode)")
    network.bind()

    // Schedule cleanup
    cleanupExecutor.scheduleAtFixedRate(
      new Runnable { def run(): Unit = {
        try { cleanup() } catch {
          case e: Exception =>
            System.err.println(s"Cleanup loop error (will retry): ${e.getMessage}")
        }
      }},
      5, 5, TimeUnit.SECONDS
    )

    // Start ranked queue matchmaking
    rankedQueue.start()

    println(s"Game server started on port $port")

    // Block until TCP server channel closes
    network.awaitClose()
  }

  def getConnectedPlayer(playerId: UUID): Player = connectedPlayers.get(playerId)

  def unregisterGameInstance(lobbyId: Short): Unit = {
    gameInstances.remove(lobbyId)
  }

  def registerGameInstance(lobbyId: Short, instance: GameInstance): Unit = {
    gameInstances.put(lobbyId, instance)
  }

  /** Handle an incoming packet from a TCP or UDP handler. */
  def handleIncomingPacket(packet: Packet, tcpCh: Channel, udpSender: InetSocketAddress): Unit = {
    val pktAttrs = Attrs.packet(packet.getType)
    Metrics.packetsReceived.add(1L, pktAttrs)
    val startNs = System.nanoTime()
    try {
      // Block TCP-only packet types arriving via UDP
      if (udpSender != null && packet.getType.tcp) {
        Metrics.packetsDropped.add(1L, Attrs.ReasonTcpOnlyOverUdp)
        return // TCP-only packets must not arrive over UDP
      }

      // For non-auth TCP packets, verify the packet's UUID matches the authenticated channel identity
      if (packet.getType != PacketType.AUTH_REQUEST && tcpCh != null) {
        val authenticatedId = sessions.channelToPlayer.get(tcpCh)
        if (authenticatedId == null) {
          // channelToPlayer is set during generateSessionToken — if missing, channel hasn't authenticated
          System.err.println(s"TCP: Packet from unauthenticated channel (no channelToPlayer binding)")
          Metrics.packetsDropped.add(1L, Attrs.ReasonUnauthenticated)
          return
        }
        if (packet.getPlayerId != null && !authenticatedId.equals(packet.getPlayerId)) {
          System.err.println(s"TCP: UUID mismatch — channel authenticated as ${authenticatedId.toString.substring(0, 8)} but packet claims ${packet.getPlayerId.toString.substring(0, 8)}")
          Metrics.packetsDropped.add(1L, Attrs.ReasonUuidMismatch)
          return
        }
      }

      packet.getType match {
        case PacketType.AUTH_REQUEST =>
          auth.handle(packet.asInstanceOf[AuthRequestPacket], tcpCh)

        case PacketType.MATCH_HISTORY =>
          accountQueries.handleMatchHistoryRequest(packet.asInstanceOf[MatchHistoryPacket], tcpCh)

        case PacketType.LEADERBOARD =>
          accountQueries.handleLeaderboardRequest(packet.asInstanceOf[LeaderboardPacket], tcpCh)

        case PacketType.RANKED_QUEUE =>
          handleRankedQueue(packet.asInstanceOf[RankedQueuePacket])

        case PacketType.CHAT_MESSAGE =>
          chat.handle(packet.asInstanceOf[ChatMessagePacket])

        case PacketType.LOBBY_ACTION =>
          val lobbyPacket = packet.asInstanceOf[LobbyActionPacket]
          val player = connectedPlayers.get(lobbyPacket.getPlayerId)
          if (player != null) {
            lobbyHandler.processLobbyAction(lobbyPacket, player)
          }

        case PacketType.PLAYER_JOIN =>
          handleGlobalConnect(packet.asInstanceOf[PlayerJoinPacket], tcpCh)

        case PacketType.HEARTBEAT =>
          handleGlobalHeartbeat(packet, udpSender)

        case _ =>
          // Route game packets to the player's active GameInstance
          val playerId = packet.getPlayerId
          val lobby = lobbyManager.getPlayerLobby(playerId)
          if (lobby != null && lobby.gameInstance != null && lobby.status == LobbyStatus.IN_GAME) {
            val instance = lobby.gameInstance
            val shouldBroadcast = instance.handler.processPacket(packet, tcpCh, udpSender)

            if (shouldBroadcast) {
              instance.broadcastToInstanceExcluding(relayed(instance, packet), packet.getPlayerId)
            }
          }
      }
    } catch {
      case e: IllegalArgumentException =>
        System.err.println(s"Invalid packet received: ${e.getMessage}")
        Metrics.packetsDropped.add(1L, Attrs.ReasonMalformed)
    } finally {
      val elapsedMs = (System.nanoTime() - startNs) / 1e6
      Metrics.packetProcessDuration.record(elapsedMs, pktAttrs)
    }
  }

  /**
   * What the rest of the match is told about a packet the instance accepted from a player. A
   * position update goes out as the server now has that player, not as the client claimed:
   * where it held them (frozen, rooted) it held them, and every status effect is on it. Relaying
   * the claim with four of the eight flags showed a rooted player walking away, and turned a
   * burning, rooted, slowed or sped-up player's effects off on every step they took.
   */
  private[server] def relayed(instance: GameInstance, packet: Packet): Packet = packet match {
    case update: PlayerUpdatePacket =>
      val player = instance.registry.get(update.getPlayerId)
      if (player != null) instance.stateUpdate(player) else packet
    case _ => packet
  }

  private def handleGlobalConnect(packet: PlayerJoinPacket, tcpCh: Channel): Unit = {
    val playerId = packet.getPlayerId
    val existingPlayer = connectedPlayers.get(playerId)
    // A player goes by the name they logged in with, not whatever their join says: taken from the
    // join, anyone could appear in a lobby, on a name plate or in chat as anyone else
    val loggedInAs = sessions.accountNames.get(playerId)
    val name = if (loggedInAs != null) loggedInAs else packet.getPlayerName

    if (existingPlayer != null) {
      existingPlayer.setTcpChannel(tcpCh)
      existingPlayer.setName(name)
      existingPlayer.setColorRGB(packet.getColorRGB)
      existingPlayer.updateHeartbeat()
    } else {
      val player = new Player(playerId, name, packet.getPosition, packet.getColorRGB, Constants.MAX_HEALTH)
      player.setTcpChannel(tcpCh)
      connectedPlayers.put(playerId, player)
    }

    if (tcpCh != null) {
      sessions.channelToPlayer.put(tcpCh, playerId)
    }

    Metrics.connectionsOpened.add(1L, Attrs.ConnTcpOpen)
    println(s"Global connect: ${playerId.toString.substring(0, 8)} ('$name')")

    // A client coming back to a match it is still in is sent the match (a rejoin). A join never
    // puts anyone into one: everyone in a match was registered when it started, and a player who
    // had left it (PLAYER_LEAVE) and joined again came back as a fresh player — full health,
    // wherever the join said, as whichever character it named.
    val lobby = lobbyManager.getPlayerLobby(playerId)
    if (lobby != null && lobby.gameInstance != null && lobby.status == LobbyStatus.IN_GAME &&
        lobby.gameInstance.registry.contains(playerId)) {
      lobby.gameInstance.handler.processPacket(packet, tcpCh, null)
    }
  }

  /** Take a player out of everything they were in: the ranked queue, their lobby and its match (a
    * lobby LEAVE does both), as their leaving would. */
  private def leaveEverything(playerId: UUID, player: Player): Unit = {
    rankedQueue.removePlayer(playerId)
    if (player != null && lobbyManager.getPlayerLobby(playerId) != null) {
      lobbyHandler.processLobbyAction(
        new LobbyActionPacket(outbox.nextSeq(), playerId, LobbyAction.LEAVE),
        player
      )
    }
  }

  private def handleRankedQueue(packet: RankedQueuePacket): Unit = {
    val playerId = packet.getPlayerId
    val player = connectedPlayers.get(playerId)
    if (player == null) return

    packet.getAction match {
      case RankedQueueAction.QUEUE_JOIN =>
        // Block queuing while in a casual lobby/game
        val currentLobby = lobbyManager.getPlayerLobby(playerId)
        if (currentLobby != null && currentLobby.status != LobbyStatus.FINISHED) return
        val elo = authDatabase.getEloByUUID(playerId)
        rankedQueue.addPlayer(playerId, packet.getCharacterId, elo, packet.getMode)

      case RankedQueueAction.QUEUE_LEAVE =>
        rankedQueue.removePlayer(playerId)

      case RankedQueueAction.CHARACTER_CHANGE =>
        rankedQueue.updateCharacter(playerId, packet.getCharacterId)

      case _ =>
    }
  }

  private def handleGlobalHeartbeat(packet: Packet, udpSender: InetSocketAddress): Unit = {
    val playerId = packet.getPlayerId
    val player = connectedPlayers.get(playerId)
    if (player != null) {
      player.updateHeartbeat()
      if (udpSender != null) {
        // Only update UDP address if sender IP matches the TCP connection IP
        val expectedAddr = sessions.playerTcpAddresses.get(playerId)
        if (expectedAddr != null && udpSender.getAddress.equals(expectedAddr)) {
          player.setUdpAddress(udpSender)
        }
      }
      // Echo heartbeat back over TCP to keep client's TCP read timeout alive
      outbox.sendOverTcp(player)(new HeartbeatPacket(outbox.nextSeq(), playerId))
      // Also update in the game instance if they're in one
      val lobby = lobbyManager.getPlayerLobby(playerId)
      if (lobby != null && lobby.gameInstance != null) {
        val instancePlayer = lobby.gameInstance.registry.get(playerId)
        if (instancePlayer != null) {
          instancePlayer.updateHeartbeat()
          if (udpSender != null) {
            val instanceExpectedAddr = sessions.playerTcpAddresses.get(playerId)
            if (instanceExpectedAddr != null && udpSender.getAddress.equals(instanceExpectedAddr)) {
              instancePlayer.setUdpAddress(udpSender)
            }
          }
        }
      }
    }
  }

  def handleDisconnect(channel: Channel): Unit = {
    Metrics.connectionsClosed.add(1L, Attrs.ConnTcpClose)
    val playerId = sessions.channelClosed(channel)
    if (playerId != null) {
      sessions.endSession(playerId)
      accountQueries.forget(playerId)
      lobbyHandler.cleanupPlayer(playerId)
      val player = connectedPlayers.remove(playerId)
      if (player != null) {
        println(s"Global disconnect: ${playerId.toString.substring(0, 8)} ('${player.getName}')")

        // Remove from ranked queue
        rankedQueue.removePlayer(playerId)

        // Remove from lobby
        val lobby = lobbyManager.getPlayerLobby(playerId)
        if (lobby != null) {
          val gi = lobby.gameInstance // snapshot to avoid NPE from concurrent game start
          if (gi != null) {
            gi.handler.handleDisconnect(channel)
          }
          lobbyHandler.processLobbyAction(
            new LobbyActionPacket(outbox.nextSeq(), playerId, LobbyAction.LEAVE),
            player
          )
        }
      }
    }
  }

  /** A lobby's match is over (its time is up, or its practice was left): see MatchEnd. */
  def endGame(lobbyId: Short): Unit = matchEnd.endGame(lobbyId)

  private def cleanup(): Unit = {
    val now = System.currentTimeMillis()
    val timeout = Constants.CLIENT_TIMEOUT_MS

    connectedPlayers.values().asScala.foreach { player =>
      if (now - player.getLastUpdateTime > timeout) {
        val playerId = player.getId
        connectedPlayers.remove(playerId)

        // Clean up session state (fixes leak)
        sessions.endSession(playerId)
        accountQueries.forget(playerId)
        sessions.playerLocks.remove(playerId)

        // Close TCP channel to force re-auth. Its disconnect forgets the rest of what the channel had.
        val raw = player.getTcpChannel
        if (raw != null) {
          val ch = raw.asInstanceOf[Channel]
          sessions.channelToToken.remove(ch)
          sessions.channelAuthFailures.remove(ch)
          sessions.channelToPlayer.remove(ch)
          if (ch.isOpen) ch.close()
        }

        // Leave the queue and the lobby the same way a disconnect does, so the other members are
        // told and a host who timed out closes their lobby instead of orphaning it.
        leaveEverything(playerId, player)

        println(s"Player timed out: ${playerId.toString.substring(0, 8)}")
      }
    }

    sessions.expireStaleTokens(now, playerId => {
      val player = connectedPlayers.get(playerId)
      if (player == null || player.getTcpChannel == null) null else player.getTcpChannel.asInstanceOf[Channel]
    })

    rateLimiter.cleanup()
    replayGuard.cleanupStale(connectedPlayers.keySet())

    if (connectedPlayers.size() > 0) {
      println(s"Server stats: ${connectedPlayers.size()} players connected, ${lobbyManager.getActiveLobbies.size} active lobbies")
    }
  }

  def stop(): Unit = {
    println("Stopping server...")

    cleanupExecutor.shutdown()
    rankedQueue.stop()

    // Stop all game instances
    gameInstances.values().asScala.foreach(_.stop())

    try {
      if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
        cleanupExecutor.shutdownNow()
      }
    } catch {
      case _: InterruptedException =>
        cleanupExecutor.shutdownNow()
        Thread.currentThread().interrupt()
    }

    authDatabase.close()
    network.stop()

    println("Server stopped.")
  }
}
