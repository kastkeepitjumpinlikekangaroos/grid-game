package com.gridgame.client.game

import com.gridgame.client.diagnostics.Diagnostics
import com.gridgame.client.net.{KnownServers, NetworkThread}
import com.gridgame.common.diagnostics.Recording.IncidentKind
import com.gridgame.common.protocol._

import java.net.InetAddress
import java.util.concurrent.atomic.AtomicLong

/**
 * The connection to a match server, while a match is played on one (design/networking.md, *Match
 * servers*). The server we are logged in to says where (MATCH_ASSIGN: the address with our ticket,
 * then the key the server there will show); we connect, holding it to that key and no other, log in
 * with the ticket (AUTH_REQUEST MATCH_JOIN), and from then on what the match server sends us goes to
 * `processPacket` like anything else, and what we send about the match goes to it ([[goesToMatch]]).
 * The session we are logged in on stays open beside it, for the lobby, chat and the rest.
 *
 * It ends when the match does: we leave it, the match server lets us go after the scoreboard, or the
 * connection drops. Dropped while we are still playing, we are out of the match, as we would be on
 * one server: the lobby is told we left and we are back in the browser (`lobbyClosedListener`).
 */
trait MatchConnection { this: GameClient =>
  @volatile private var matchThread: NetworkThread = _
  // Logged in to it: from then on, the match's packets go to it
  @volatile private var matchJoined = false
  // The ADDRESS part of a MATCH_ASSIGN, waiting for its IDENTITY
  @volatile private var assignedAddress: MatchAssignPacket = _
  private val matchHeartbeatSentAtNs = new AtomicLong(0L)

  /** Whether we are playing on a match server. */
  def onMatchServer: Boolean = matchJoined

  /** What a packet of ours is about: a match's, which goes to its match server when there is one. */
  private[game] def matchPacketSender(packet: Packet): NetworkThread = {
    val t = matchThread
    if (t != null && matchJoined && MatchConnection.goesToMatch(packet)) t else null
  }

  private[game] def handleMatchAssign(packet: MatchAssignPacket): Unit = packet.getPart match {
    case MatchAssignPart.ADDRESS => assignedAddress = packet
    case MatchAssignPart.IDENTITY =>
      val at = assignedAddress
      assignedAddress = null
      if (at != null) {
        // Connecting waits for the handshake: not on the packet thread
        val t = new Thread(() => connectToMatch(at.getAddress, at.getPort, at.getTicket, packet.fingerprint), "MatchConnect")
        t.setDaemon(true)
        t.start()
      }
    case _ =>
  }

  private def connectToMatch(address: InetAddress, port: Int, ticket: Array[Byte], fingerprint: String): Unit = {
    closeMatchConnection()
    val host = address.getHostAddress
    // Held to the key the server we trust said it has: no first meeting taken on trust
    val known = KnownServers.inMemory()
    known.trust(host, port, fingerprint)
    val thread = new NetworkThread(host, port, p => matchPacket(p), () => sendMatchHeartbeat(), knownServers = known)
    thread.tap = packetTap
    thread.disconnectCallback = () => matchConnectionLost(thread)
    try {
      thread.start()
      thread.waitForReady()
    } catch {
      case e: Exception =>
        System.err.println(s"GameClient: Couldn't reach the match server at $host:$port: ${e.getMessage}")
        Diagnostics.incident(IncidentKind.Disconnect, s"couldn't reach the match server at $host:$port: ${e.getMessage}")
        thread.shutdown()
        outOfTheMatch()
        return
    }
    matchThread = thread
    println(s"GameClient: Connected to the match server at $host:$port")
    val token = java.util.Arrays.copyOf(ticket, com.gridgame.common.Constants.REFRESH_TOKEN_SIZE)
    thread.send(new AuthRequestPacket(sequenceNumber.getAndIncrement(), AuthAction.MATCH_JOIN, token))
  }

  /** A packet from the match server, on its network thread: our login's answer and the heartbeat's echo
    * are the connection's own; everything else is the match's. */
  private def matchPacket(packet: Packet): Unit = packet.getType match {
    case PacketType.AUTH_RESPONSE =>
      val answer = packet.asInstanceOf[AuthResponsePacket]
      if (answer.getSuccess) {
        matchJoined = true
        // At once, not a second on: the match server learns where to send our datagrams from it, and
        // until it has, everything the match says of everyone else by datagram is lost
        sendMatchHeartbeat()
      } else {
        System.err.println(s"GameClient: The match server refused us: ${answer.getMessage}")
        Diagnostics.incident(IncidentKind.Disconnect, s"the match server refused us: ${answer.getMessage}")
        closeMatchConnection()
        outOfTheMatch()
      }
    case PacketType.SESSION_TOKEN => // taken up by the network thread as it arrived
    case PacketType.HEARTBEAT =>
      val sentNs = matchHeartbeatSentAtNs.getAndSet(0L)
      if (sentNs > 0) noteRoundTrip((System.nanoTime() - sentNs) / 1e6)
    case _ => enqueuePacket(packet)
  }

  private def sendMatchHeartbeat(): Unit = {
    val t = matchThread
    if (t != null && matchJoined) {
      matchHeartbeatSentAtNs.set(System.nanoTime())
      t.send(new HeartbeatPacket(sequenceNumber.getAndIncrement(), localPlayerId))
    }
  }

  private def matchConnectionLost(thread: NetworkThread): Unit = {
    if (!(matchThread eq thread)) return
    matchThread = null
    matchJoined = false
    if (clientState == ClientState.PLAYING) {
      Diagnostics.incident(IncidentKind.Disconnect, "the connection to the match server dropped in a match")
      outOfTheMatch()
    }
  }

  /** We can't play the match: the lobby is told we left it, and we are back in the browser. */
  private def outOfTheMatch(): Unit = {
    send(new LobbyActionPacket(sequenceNumber.getAndIncrement(), localPlayerId, LobbyAction.LEAVE))
    returnToLobbyBrowser()
    val listener = lobbyClosedListener
    if (listener != null) listener()
  }

  /** Done with the match server: the match is over for us, we left it, or our session ended. */
  def closeMatchConnection(): Unit = {
    val t = matchThread
    matchThread = null
    matchJoined = false
    assignedAddress = null
    if (t != null) {
      t.sessionToken = null
      t.shutdown()
    }
  }
}

object MatchConnection {
  /** Packets about the match we are playing: to its match server, while there is one. */
  def goesToMatch(packet: Packet): Boolean = packet.getType match {
    case PacketType.PLAYER_UPDATE | PacketType.PROJECTILE_UPDATE | PacketType.ITEM_UPDATE | PacketType.TRAP_UPDATE |
         PacketType.TILE_UPDATE | PacketType.COOLDOWN_RESET => true
    case PacketType.CHAT_MESSAGE => packet.asInstanceOf[ChatMessagePacket].getScope != ChatScope.LOBBY
    case _ => false
  }
}
