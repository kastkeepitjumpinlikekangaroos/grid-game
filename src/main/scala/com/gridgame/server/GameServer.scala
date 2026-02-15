package com.gridgame.server

import com.gridgame.common.Constants
import com.gridgame.common.model.Direction
import com.gridgame.common.model.Item
import com.gridgame.common.model.Player
import com.gridgame.common.model.Projectile
import com.gridgame.common.model.WorldData
import com.gridgame.common.protocol._
import io.netty.bootstrap.Bootstrap
import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.Unpooled
import io.netty.channel._
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.DatagramPacket
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioDatagramChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.LengthFieldBasedFrameDecoder
import io.netty.handler.codec.LengthFieldPrepender

import java.net.InetSocketAddress
import java.util.UUID
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import scala.jdk.CollectionConverters._

class GameServer(port: Int, val worldFile: String = "") {
  // Global state: all connected players regardless of lobby/game
  private val connectedPlayers = new ConcurrentHashMap[UUID, Player]()
  // Channel -> UUID mapping for disconnect handling
  private val channelToPlayer = new ConcurrentHashMap[Channel, UUID]()

  val authDatabase = new AuthDatabase()
  val lobbyManager = new LobbyManager()
  val lobbyHandler = new LobbyHandler(this, lobbyManager)
  val rankedQueue = new RankedQueue(this)
  private val gameInstances = new ConcurrentHashMap[Short, GameInstance]()

  private val cleanupExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
  private val sequenceNumber = new AtomicInteger(0)
  @volatile private var running = false

  // Netty components
  private var bossGroup: NioEventLoopGroup = _
  private var workerGroup: NioEventLoopGroup = _
  private var tcpServerChannel: Channel = _
  private var udpChannel: Channel = _

  def start(): Unit = {
    running = true

    println(s"Game server starting on port $port (lobby mode)")

    // Start Netty
    bossGroup = new NioEventLoopGroup(1)
    workerGroup = new NioEventLoopGroup()

    val server = this

    // TCP Server
    val tcpBootstrap = new ServerBootstrap()
    tcpBootstrap.group(bossGroup, workerGroup)
      .channel(classOf[NioServerSocketChannel])
      .childHandler(new ChannelInitializer[SocketChannel] {
        override def initChannel(ch: SocketChannel): Unit = {
          ch.pipeline()
            .addLast(new LengthFieldBasedFrameDecoder(66, 0, 2, 0, 2))
            .addLast(new LengthFieldPrepender(2))
            .addLast(new GameServerTcpHandler(server))
        }
      })
      .option[java.lang.Integer](ChannelOption.SO_BACKLOG, 128)
      .childOption[java.lang.Boolean](ChannelOption.SO_KEEPALIVE, true)

    tcpServerChannel = tcpBootstrap.bind(port).sync().channel()
    println(s"TCP server listening on port $port")

    // UDP Server
    val udpBootstrap = new Bootstrap()
    udpBootstrap.group(workerGroup)
      .channel(classOf[NioDatagramChannel])
      .handler(new GameServerUdpHandler(server))

    udpChannel = udpBootstrap.bind(port).sync().channel()
    println(s"UDP server listening on port $port")

    // Schedule cleanup
    cleanupExecutor.scheduleAtFixedRate(
      new Runnable { def run(): Unit = cleanup() },
      5, 5, TimeUnit.SECONDS
    )

    // Start ranked queue matchmaking
    rankedQueue.start()

    println(s"Game server started on port $port")

    // Block until TCP server channel closes
    tcpServerChannel.closeFuture().sync()
  }

  /** Send a packet to a specific player, using the correct transport based on packet type. */
  def sendPacketToPlayer(packet: Packet, player: Player): Unit = {
    if (packet.getType.tcp) {
      val raw = player.getTcpChannel
      if (raw != null) {
        val ch = raw.asInstanceOf[Channel]
        if (ch.isActive) {
          val data = packet.serialize()
          ch.writeAndFlush(Unpooled.wrappedBuffer(data))
        }
      }
    } else {
      val addr = player.getUdpAddress
      if (addr != null && udpChannel != null) {
        val data = packet.serialize()
        val dgram = new DatagramPacket(Unpooled.wrappedBuffer(data), addr)
        udpChannel.writeAndFlush(dgram)
      }
    }
  }

  /** Send a packet directly over a specific TCP channel (used for initial join responses). */
  def sendPacketViaChannel(packet: Packet, channel: Channel): Unit = {
    if (channel != null && channel.isActive) {
      val data = packet.serialize()
      channel.writeAndFlush(Unpooled.wrappedBuffer(data))
    }
  }

  /** Broadcast a packet to all connected players (lobby-wide). */
  def broadcastToAllPlayers(packet: Packet): Unit = {
    val data = packet.serialize()
    connectedPlayers.values().asScala.foreach { player =>
      sendRawToPlayer(data, packet.getType.tcp, player)
    }
  }

  private def sendRawToPlayer(data: Array[Byte], isTcp: Boolean, player: Player): Unit = {
    if (isTcp) {
      val raw = player.getTcpChannel
      if (raw != null) {
        val ch = raw.asInstanceOf[Channel]
        if (ch.isActive) {
          ch.writeAndFlush(Unpooled.wrappedBuffer(data.clone()))
        }
      }
    } else {
      val addr = player.getUdpAddress
      if (addr != null && udpChannel != null) {
        val dgram = new DatagramPacket(Unpooled.wrappedBuffer(data.clone()), addr)
        udpChannel.writeAndFlush(dgram)
      }
    }
  }

  def getNextSequenceNumber: Int = sequenceNumber.getAndIncrement()

  def getConnectedPlayer(playerId: UUID): Player = connectedPlayers.get(playerId)

  def registerGameInstance(lobbyId: Short, instance: GameInstance): Unit = {
    gameInstances.put(lobbyId, instance)
  }

  /** Unused in lobby mode but kept for backwards-compat with ClientHandler */
  def getWorld: WorldData = null

  def broadcastPlayerUpdate(packet: PlayerUpdatePacket): Unit = {
    // This should not be called in lobby mode; instance handles it
  }

  def broadcastTileUpdate(playerId: UUID, x: Int, y: Int, tileId: Int): Unit = {
    // This should not be called in lobby mode; instance handles it
  }

  def sendModifiedTiles(player: Player): Unit = {
    // No-op in lobby mode; instance handles it
  }

  def broadcastProjectileSpawn(projectile: Projectile): Unit = {
    // No-op in lobby mode; instance handles it
  }

  def broadcastItemPickup(item: Item, playerId: UUID): Unit = {
    // No-op in lobby mode; instance handles it
  }

  /** Handle an incoming packet from a TCP or UDP handler. */
  def handleIncomingPacket(packet: Packet, tcpCh: Channel, udpSender: InetSocketAddress): Unit = {
    try {
      packet.getType match {
        case PacketType.AUTH_REQUEST =>
          handleAuthRequest(packet.asInstanceOf[AuthRequestPacket], tcpCh)

        case PacketType.MATCH_HISTORY =>
          handleMatchHistoryRequest(packet.asInstanceOf[MatchHistoryPacket], tcpCh)

        case PacketType.LEADERBOARD =>
          handleLeaderboardRequest(packet.asInstanceOf[LeaderboardPacket], tcpCh)

        case PacketType.RANKED_QUEUE =>
          handleRankedQueue(packet.asInstanceOf[RankedQueuePacket])

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
              val packetToBroadcast = packet.getType match {
                case PacketType.PLAYER_UPDATE =>
                  val updatePacket = packet.asInstanceOf[PlayerUpdatePacket]
                  val player = instance.registry.get(updatePacket.getPlayerId)
                  if (player != null) {
                    // Reject position updates from frozen players
                    val pos = if (player.isFrozen) player.getPosition else updatePacket.getPosition

                    // Handle phased flag from client
                    val clientFlags = updatePacket.getEffectFlags
                    if ((clientFlags & 0x08) != 0 && !player.isPhased) {
                      // Determine phase duration from character's ability CastBehavior
                      val charDef = com.gridgame.common.model.CharacterDef.get(player.getCharacterId)
                      val phaseDuration = charDef.qAbility.castBehavior match {
                        case com.gridgame.common.model.PhaseShiftBuff(d) => d
                        case com.gridgame.common.model.DashBuff(_, d, _) => d
                        case _ => 5000
                      }
                      player.setPhasedUntil(System.currentTimeMillis() + phaseDuration)
                    }

                    val flags = (if (player.hasShield) 0x01 else 0) |
                                (if (player.hasGemBoost) 0x02 else 0) |
                                (if (player.isFrozen) 0x04 else 0) |
                                (if (player.isPhased) 0x08 else 0)
                    new PlayerUpdatePacket(
                      updatePacket.getSequenceNumber,
                      updatePacket.getPlayerId,
                      updatePacket.getTimestamp,
                      pos,
                      updatePacket.getColorRGB,
                      player.getHealth,
                      updatePacket.getChargeLevel,
                      flags,
                      player.getCharacterId,
                      player.getTeamId
                    )
                  } else {
                    packet
                  }
                case PacketType.PLAYER_JOIN =>
                  val joinPacket = packet.asInstanceOf[PlayerJoinPacket]
                  val player = instance.registry.get(joinPacket.getPlayerId)
                  if (player != null) {
                    new PlayerJoinPacket(
                      joinPacket.getSequenceNumber,
                      joinPacket.getPlayerId,
                      joinPacket.getTimestamp,
                      joinPacket.getPosition,
                      joinPacket.getColorRGB,
                      joinPacket.getPlayerName,
                      player.getHealth,
                      player.getCharacterId,
                      player.getTeamId
                    )
                  } else {
                    packet
                  }
                case _ => packet
              }
              // Broadcast to instance players only, excluding sender
              val data = packetToBroadcast.serialize()
              instance.registry.getAll.asScala.foreach { p =>
                if (!p.getId.equals(packet.getPlayerId)) {
                  sendRawToPlayer(data, packetToBroadcast.getType.tcp, p)
                }
              }
            }
          }
      }
    } catch {
      case e: IllegalArgumentException =>
        System.err.println(s"Invalid packet received: ${e.getMessage}")
    }
  }

  private def handleGlobalConnect(packet: PlayerJoinPacket, tcpCh: Channel): Unit = {
    val playerId = packet.getPlayerId
    val existingPlayer = connectedPlayers.get(playerId)

    if (existingPlayer != null) {
      existingPlayer.setTcpChannel(tcpCh)
      existingPlayer.setName(packet.getPlayerName)
      existingPlayer.setColorRGB(packet.getColorRGB)
      existingPlayer.updateHeartbeat()
    } else {
      val player = new Player(playerId, packet.getPlayerName, packet.getPosition, packet.getColorRGB, Constants.MAX_HEALTH)
      player.setTcpChannel(tcpCh)
      connectedPlayers.put(playerId, player)
    }

    if (tcpCh != null) {
      channelToPlayer.put(tcpCh, playerId)
    }

    println(s"Global connect: ${playerId.toString.substring(0, 8)} ('${packet.getPlayerName}')")

    // Check if player is in a lobby that's in-game; if so, register in instance
    val lobby = lobbyManager.getPlayerLobby(playerId)
    if (lobby != null && lobby.gameInstance != null && lobby.status == LobbyStatus.IN_GAME) {
      val instance = lobby.gameInstance
      val player = connectedPlayers.get(playerId)
      instance.handler.processPacket(packet, tcpCh, null)
    }
  }

  private def handleAuthRequest(packet: AuthRequestPacket, tcpCh: Channel): Unit = {
    val username = packet.getUsername
    val password = packet.getPassword
    val isSignup = packet.getAction == AuthAction.SIGNUP

    if (isSignup) {
      val registered = authDatabase.register(username, password)
      if (registered) {
        val uuid = authDatabase.getOrCreateUUID(username)
        println(s"Auth: New account registered - '$username' (${uuid.toString.substring(0, 8)})")
        val response = new AuthResponsePacket(getNextSequenceNumber, true, uuid, "Account created")
        sendPacketViaChannel(response, tcpCh)
      } else {
        println(s"Auth: Signup failed - '$username' (already exists)")
        val response = new AuthResponsePacket(getNextSequenceNumber, false, null, "Username taken")
        sendPacketViaChannel(response, tcpCh)
      }
    } else {
      val authenticated = authDatabase.authenticate(username, password)
      if (authenticated) {
        val uuid = authDatabase.getOrCreateUUID(username)
        println(s"Auth: Login successful - '$username' (${uuid.toString.substring(0, 8)})")
        val response = new AuthResponsePacket(getNextSequenceNumber, true, uuid, "Login successful")
        sendPacketViaChannel(response, tcpCh)
      } else {
        println(s"Auth: Login failed - '$username' (invalid credentials)")
        val response = new AuthResponsePacket(getNextSequenceNumber, false, null, "Invalid credentials")
        sendPacketViaChannel(response, tcpCh)
      }
    }
  }

  private def handleMatchHistoryRequest(packet: MatchHistoryPacket, tcpCh: Channel): Unit = {
    if (packet.getAction != MatchHistoryAction.QUERY) return

    val playerId = packet.getPlayerId
    val player = connectedPlayers.get(playerId)
    if (player == null) return

    // Send stats
    val (totalKills, totalDeaths, matchesPlayed, wins, elo) = authDatabase.getPlayerStats(playerId)
    val statsPacket = new MatchHistoryPacket(
      getNextSequenceNumber, playerId, Packet.getCurrentTimestamp, MatchHistoryAction.STATS,
      totalKills = totalKills, totalDeaths = totalDeaths,
      matchesPlayed = matchesPlayed, wins = wins, elo = elo.toShort
    )
    sendPacketViaChannel(statsPacket, tcpCh)

    // Send history entries
    val history = authDatabase.getMatchHistory(playerId)
    history.foreach { case (matchId, mapIndex, durationMin, playedAt, kills, deaths, rank, playerCount) =>
      val entryPacket = new MatchHistoryPacket(
        getNextSequenceNumber, playerId, Packet.getCurrentTimestamp, MatchHistoryAction.ENTRY,
        matchId.toInt, mapIndex.toByte, durationMin.toByte, (playedAt / 1000).toInt,
        kills.toShort, deaths.toShort, rank.toByte, playerCount.toByte
      )
      sendPacketViaChannel(entryPacket, tcpCh)
    }

    // Send end marker
    val endPacket = new MatchHistoryPacket(getNextSequenceNumber, playerId, MatchHistoryAction.END)
    sendPacketViaChannel(endPacket, tcpCh)
  }

  private def handleLeaderboardRequest(packet: LeaderboardPacket, tcpCh: Channel): Unit = {
    if (packet.getAction != LeaderboardAction.QUERY) return

    val playerId = packet.getPlayerId
    val player = connectedPlayers.get(playerId)
    if (player == null) return

    val leaderboard = authDatabase.getLeaderboard()
    var rank: Byte = 1
    leaderboard.foreach { case (username, elo, wins, matchesPlayed) =>
      val entryPacket = new LeaderboardPacket(
        getNextSequenceNumber, playerId, Packet.getCurrentTimestamp, LeaderboardAction.ENTRY,
        rank, elo.toShort, wins, matchesPlayed, username
      )
      sendPacketViaChannel(entryPacket, tcpCh)
      rank = (rank + 1).toByte
    }

    val endPacket = new LeaderboardPacket(getNextSequenceNumber, playerId, LeaderboardAction.END)
    sendPacketViaChannel(endPacket, tcpCh)
  }

  private def handleRankedQueue(packet: RankedQueuePacket): Unit = {
    val playerId = packet.getPlayerId
    val player = connectedPlayers.get(playerId)
    if (player == null) return

    packet.getAction match {
      case RankedQueueAction.QUEUE_JOIN =>
        val elo = getPlayerElo(playerId)
        rankedQueue.addPlayer(playerId, packet.getCharacterId, elo, packet.getMode)

      case RankedQueueAction.QUEUE_LEAVE =>
        rankedQueue.removePlayer(playerId)

      case RankedQueueAction.CHARACTER_CHANGE =>
        rankedQueue.updateCharacter(playerId, packet.getCharacterId)

      case _ =>
    }
  }

  def getPlayerElo(playerId: UUID): Int = {
    authDatabase.getEloByUUID(playerId)
  }

  def getPlayerUsername(playerId: UUID): String = {
    authDatabase.getUsernameByUUID(playerId)
  }

  private def handleGlobalHeartbeat(packet: Packet, udpSender: InetSocketAddress): Unit = {
    val playerId = packet.getPlayerId
    val player = connectedPlayers.get(playerId)
    if (player != null) {
      player.updateHeartbeat()
      if (udpSender != null) {
        player.setUdpAddress(udpSender)
      }
      // Also update in the game instance if they're in one
      val lobby = lobbyManager.getPlayerLobby(playerId)
      if (lobby != null && lobby.gameInstance != null) {
        val instancePlayer = lobby.gameInstance.registry.get(playerId)
        if (instancePlayer != null) {
          instancePlayer.updateHeartbeat()
          if (udpSender != null) {
            instancePlayer.setUdpAddress(udpSender)
          }
        }
      }
    }
  }

  def handleDisconnect(channel: Channel): Unit = {
    val playerId = channelToPlayer.remove(channel)
    if (playerId != null) {
      val player = connectedPlayers.remove(playerId)
      if (player != null) {
        println(s"Global disconnect: ${playerId.toString.substring(0, 8)} ('${player.getName}')")

        // Remove from ranked queue
        rankedQueue.removePlayer(playerId)

        // Remove from lobby
        val lobby = lobbyManager.getPlayerLobby(playerId)
        if (lobby != null) {
          if (lobby.gameInstance != null) {
            lobby.gameInstance.handler.handleDisconnect(channel)
          }
          lobbyHandler.processLobbyAction(
            new LobbyActionPacket(getNextSequenceNumber, playerId, LobbyAction.LEAVE),
            player
          )
        }
      }
    }
  }

  def endGame(lobbyId: Short): Unit = {
    val lobby = lobbyManager.getLobby(lobbyId)
    if (lobby == null) return

    val instance = lobby.gameInstance
    if (instance == null) return

    lobby.status = LobbyStatus.FINISHED

    // Stop the instance (shutdownNow() interrupts worker threads including the
    // current thread when called from syncTimer). Clear the interrupt flag so
    // subsequent JDBC operations in saveMatch aren't disrupted.
    instance.stop()
    Thread.interrupted()

    // Broadcast GAME_OVER
    val zeroUUID = new UUID(0L, 0L)
    val gameOverPacket = new GameEventPacket(
      getNextSequenceNumber, zeroUUID, GameEvent.GAME_OVER, lobbyId,
      0, 0.toShort, 0.toShort, null, 0.toByte, 0.toShort, 0.toShort
    )
    instance.broadcastToInstance(gameOverPacket)

    // Send SCORE_ENTRY for each player and collect results for persistence
    val scoreboard = instance.killTracker.getScoreboard
    val matchResults = scala.collection.mutable.ArrayBuffer[(UUID, Int, Int, Byte)]()

    if (instance.gameMode == 1) {
      // Teams mode: group by team, rank teams by total kills, assign same rank to team members
      val playerScores = scoreboard.map { case (pid, kills, deaths) =>
        val teamId = instance.teamAssignments.getOrDefault(pid, 0.toByte)
        (pid, kills, deaths, teamId)
      }
      val teamTotals = playerScores.groupBy(_._4).toSeq.map { case (teamId, members) =>
        (teamId, members.map(_._2).sum)
      }.sortBy(-_._2)

      val teamRanks = teamTotals.zipWithIndex.map { case ((teamId, _), idx) =>
        teamId -> (idx + 1).toByte
      }.toMap

      playerScores.foreach { case (pid, kills, deaths, teamId) =>
        val rank = teamRanks.getOrElse(teamId, 1.toByte)
        val scorePacket = new GameEventPacket(
          getNextSequenceNumber, pid, GameEvent.SCORE_ENTRY, lobbyId,
          0, kills.toShort, deaths.toShort, null, rank, 0.toShort, 0.toShort, teamId
        )
        instance.broadcastToInstance(scorePacket)
        matchResults += ((pid, kills, deaths, rank))
      }
    } else {
      // FFA mode: rank individually
      var rank: Byte = 1
      scoreboard.foreach { case (pid, kills, deaths) =>
        val scorePacket = new GameEventPacket(
          getNextSequenceNumber, pid, GameEvent.SCORE_ENTRY, lobbyId,
          0, kills.toShort, deaths.toShort, null, rank, 0.toShort, 0.toShort
        )
        instance.broadcastToInstance(scorePacket)
        matchResults += ((pid, kills, deaths, rank))
        rank = (rank + 1).toByte
      }
    }

    // Persist match results (exclude bots)
    val humanResults = matchResults.filter { case (pid, _, _, _) => !BotManager.isBotUUID(pid) }.toSeq
    authDatabase.saveMatch(lobby.mapIndex, lobby.durationMinutes, humanResults)

    // Update ELO for ranked matches (exclude bots)
    if (lobby.isRanked && humanResults.size >= 2) {
      updateRankedElo(humanResults)
    }

    // Send SCORE_END
    val scoreEndPacket = new GameEventPacket(
      getNextSequenceNumber, zeroUUID, GameEvent.SCORE_END, lobbyId,
      0, 0.toShort, 0.toShort, null, 0.toByte, 0.toShort, 0.toShort
    )
    instance.broadcastToInstance(scoreEndPacket)

    // Clean up
    gameInstances.remove(lobbyId)
    lobbyManager.removeLobby(lobbyId)

    println(s"Game ended for lobby $lobbyId")
  }

  private def updateRankedElo(results: Seq[(UUID, Int, Int, Byte)]): Unit = {
    val n = results.size
    if (n < 2) return

    val kAdjusted = 32.0 / (n - 1)

    // Get current ELOs
    val elos = results.map { case (uuid, _, _, _) =>
      uuid -> authDatabase.getEloByUUID(uuid)
    }.toMap

    // Calculate new ELOs using FFA formula
    results.foreach { case (uuid, _, _, rank) =>
      val myElo = elos(uuid)
      var delta = 0.0

      results.foreach { case (opponentUuid, _, _, opponentRank) =>
        if (!opponentUuid.equals(uuid)) {
          val opponentElo = elos(opponentUuid)
          val expected = 1.0 / (1.0 + Math.pow(10, (opponentElo - myElo) / 400.0))
          val actual = if ((rank & 0xFF) < (opponentRank & 0xFF)) 1.0 else 0.0
          delta += kAdjusted * (actual - expected)
        }
      }

      val newElo = Math.max(0, (myElo + Math.round(delta)).toInt)
      val username = authDatabase.getUsernameByUUID(uuid)
      if (username != null) {
        authDatabase.updateElo(username, newElo)
        println(s"RankedELO: ${username} $myElo -> $newElo (delta=${Math.round(delta)})")
      }
    }
  }

  private def cleanup(): Unit = {
    val now = System.currentTimeMillis()
    val timeout = Constants.CLIENT_TIMEOUT_MS

    connectedPlayers.values().asScala.foreach { player =>
      if (now - player.getLastUpdateTime > timeout) {
        val playerId = player.getId
        connectedPlayers.remove(playerId)

        // Remove from ranked queue
        rankedQueue.removePlayer(playerId)

        // Remove from lobby
        val lobby = lobbyManager.getPlayerLobby(playerId)
        if (lobby != null) {
          if (lobby.gameInstance != null) {
            lobby.gameInstance.registry.remove(playerId)
          }
          lobbyManager.leaveLobby(playerId)
        }

        println(s"Player timed out: ${playerId.toString.substring(0, 8)}")
      }
    }

    if (connectedPlayers.size() > 0) {
      println(s"Server stats: ${connectedPlayers.size()} players connected, ${lobbyManager.getActiveLobbies.size} active lobbies")
    }
  }

  def stop(): Unit = {
    println("Stopping server...")
    running = false

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

    if (tcpServerChannel != null) tcpServerChannel.close().sync()
    if (udpChannel != null) udpChannel.close().sync()
    if (bossGroup != null) bossGroup.shutdownGracefully()
    if (workerGroup != null) workerGroup.shutdownGracefully()

    println("Server stopped.")
  }

  def getPlayerCount: Int = connectedPlayers.size()
}

/** Netty handler for incoming TCP connections. */
class GameServerTcpHandler(server: GameServer) extends SimpleChannelInboundHandler[io.netty.buffer.ByteBuf] {

  override def channelRead0(ctx: ChannelHandlerContext, msg: io.netty.buffer.ByteBuf): Unit = {
    if (msg.readableBytes() < Constants.PACKET_SIZE) return

    val data = new Array[Byte](Constants.PACKET_SIZE)
    msg.readBytes(data)

    try {
      val packet = PacketSerializer.deserialize(data)
      server.handleIncomingPacket(packet, ctx.channel(), null)
    } catch {
      case e: IllegalArgumentException =>
        System.err.println(s"TCP: Invalid packet received: ${e.getMessage}")
    }
  }

  override def channelInactive(ctx: ChannelHandlerContext): Unit = {
    server.handleDisconnect(ctx.channel())
    super.channelInactive(ctx)
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
    System.err.println(s"TCP handler error: ${cause.getMessage}")
    ctx.close()
  }
}

/** Netty handler for incoming UDP packets. */
class GameServerUdpHandler(server: GameServer) extends SimpleChannelInboundHandler[DatagramPacket] {

  override def channelRead0(ctx: ChannelHandlerContext, msg: DatagramPacket): Unit = {
    val buf = msg.content()
    if (buf.readableBytes() < Constants.PACKET_SIZE) return

    val data = new Array[Byte](Constants.PACKET_SIZE)
    buf.readBytes(data)

    try {
      val packet = PacketSerializer.deserialize(data)
      server.handleIncomingPacket(packet, null, msg.sender())
    } catch {
      case e: IllegalArgumentException =>
        System.err.println(s"UDP: Invalid packet received: ${e.getMessage}")
    }
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
    System.err.println(s"UDP handler error: ${cause.getMessage}")
  }
}
