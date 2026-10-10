package com.gridgame.server

import com.gridgame.server.net.Sessions
import com.gridgame.common.Constants
import com.gridgame.common.model.Position
import com.gridgame.common.protocol._
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import org.junit.Assert._
import org.junit.Test

import java.util.UUID

/**
 * Sessions, through the TCP handler every packet after login passes: signed with the session's
 * token, and checked for replays by sequence number.
 */
class SessionTest {
  import ServerTestKit._

  private class Session(val playerId: UUID) {
    val channel = new PeerChannel()
    channel.pipeline().addLast(server.tcpHandler())
    val token: Array[Byte] = server.sessions.generateSessionToken(playerId, channel) // as a login does
    private var seq = 0

    /** Send a packet as this session's client does: its own count, signed with its token. */
    def send(make: Int => Packet): Unit = sendWith(token)(make)

    /** Send the next packet signed with `key`: a renewal's token, say. */
    def sendWith(key: Array[Byte])(make: Int => Packet): Unit = {
      seq += 1
      channel.writeInbound(Unpooled.wrappedBuffer(PacketSigner.sign(make(seq).serialize(), key)))
    }

    /** A heartbeat over TCP, which the server answers with one, signed with the token it signs with. */
    def ping(key: Array[Byte]): Unit = sendWith(key)(n => new HeartbeatPacket(n, playerId))

    /** Everything the server wrote to this channel since the last call, as written: signed. */
    def written(): Seq[Array[Byte]] = {
      channel.runPendingTasks()
      Iterator.continually(channel.readOutbound[ByteBuf]()).takeWhile(_ != null).map { buf =>
        val bytes = new Array[Byte](buf.readableBytes())
        buf.readBytes(bytes)
        buf.release()
        bytes
      }.toSeq
    }
  }

  /** The packets `key` signed, of those written. */
  private def signedBy(written: Seq[Array[Byte]], key: Array[Byte]): Seq[Packet] =
    written.flatMap(b => Option(PacketSigner.verify(b, key))).map(PacketSerializer.deserialize)

  private def heartbeats(packets: Seq[Packet]): Int = packets.count(_.isInstanceOf[HeartbeatPacket])

  /** As if the session's token had been issued `ms` ago. */
  private def age(s: Session, ms: Long): Unit =
    server.sessions.tokenCreationTime.put(s.playerId, System.currentTimeMillis() - ms)

  /** The new token the server sent this session in a renewal, read off what it wrote. */
  private def renewal(s: Session): Array[Byte] = {
    val tokens = signedBy(s.written(), s.token).collect { case t: SessionTokenPacket => t.getSessionToken }
    assertEquals("one new token, signed with the token the client has", 1, tokens.size)
    tokens.head
  }

  private def join(s: Session): Unit =
    s.send(n => new PlayerJoinPacket(n, s.playerId, new Position(1, 1), 0xFF00FF00, "player"))

  private def createLobby(s: Session): Unit =
    s.send(n => new LobbyActionPacket(n, s.playerId, Packet.getCurrentTimestamp, LobbyAction.CREATE, 0.toShort,
      0.toByte, 5.toByte, 0.toByte, 8.toByte, 0.toByte, "mine"))

  @Test def aSignedPacketFromTheSessionIsHandled(): Unit = {
    val s = new Session(UUID.randomUUID())
    join(s)
    assertNotNull(server.getConnectedPlayer(s.playerId))
  }

  @Test def aLoginWhileTheLastSessionIsStillOpenStartsAFreshCount(): Unit = {
    // The first session is still open here (the client lost its network; the server hasn't
    // noticed). Logging in again closes it, but its sequence numbers stayed, and every packet of
    // the new session — which counts from zero — was dropped as a replay until it caught up.
    val id = UUID.randomUUID()
    val old = new Session(id)
    join(old)
    for (_ <- 0 until 30) old.send(n => new LobbyActionPacket(n, id, LobbyAction.LIST_REQUEST))

    val fresh = new Session(id)
    assertFalse("the old channel is closed", old.channel.isOpen)
    join(fresh)
    createLobby(fresh)
    assertNotNull("the new session's lobby was made", server.lobbyManager.getPlayerLobby(id))
    server.lobbyManager.removeLobby(server.lobbyManager.getPlayerLobby(id).id)
  }

  @Test def theOldSessionsPacketsDoNotVerifyOnTheNewOne(): Unit = {
    val id = UUID.randomUUID()
    val old = new Session(id)
    val fresh = new Session(id)
    // A packet signed with the old token, arriving on the new channel
    fresh.channel.writeInbound(Unpooled.wrappedBuffer(PacketSigner.sign(new PlayerJoinPacket(99, id,
      new Position(1, 1), 0, "player").serialize(), old.token)))
    assertNull(server.getConnectedPlayer(id))
  }

  @Test def aReplayedPacketIsDropped(): Unit = {
    val host = new Session(UUID.randomUUID())
    join(host)
    createLobby(host)
    val lobby = server.lobbyManager.getPlayerLobby(host.playerId)
    val guest = new Session(UUID.randomUUID())
    join(guest)
    // The guest joins, leaves, and joins again; then someone replays the captured LEAVE
    val joinBytes = PacketSigner.sign(new LobbyActionPacket(10, guest.playerId, Packet.getCurrentTimestamp,
      LobbyAction.JOIN, lobby.id).serialize(), guest.token)
    val leaveBytes = PacketSigner.sign(new LobbyActionPacket(11, guest.playerId, LobbyAction.LEAVE).serialize(), guest.token)
    val joinAgain = PacketSigner.sign(new LobbyActionPacket(12, guest.playerId, Packet.getCurrentTimestamp,
      LobbyAction.JOIN, lobby.id).serialize(), guest.token)
    guest.channel.writeInbound(Unpooled.wrappedBuffer(joinBytes))
    guest.channel.writeInbound(Unpooled.wrappedBuffer(leaveBytes.clone()))
    guest.channel.writeInbound(Unpooled.wrappedBuffer(joinAgain))
    assertEquals(lobby, server.lobbyManager.getPlayerLobby(guest.playerId))
    guest.channel.writeInbound(Unpooled.wrappedBuffer(leaveBytes))
    assertEquals("the replayed LEAVE did nothing", lobby, server.lobbyManager.getPlayerLobby(guest.playerId))
    server.lobbyManager.removeLobby(lobby.id)
  }

  // --- An hour in ---

  @Test def aSessionPastItsTokensLifetimeIsRenewedNotEnded(): Unit = {
    // It used to be ended, wherever its player was: in the middle of a match, out to the login screen
    val s = new Session(UUID.randomUUID())
    join(s)
    age(s, Constants.SESSION_TOKEN_LIFETIME_MS + 1000)
    s.written()
    server.renewSessions(System.currentTimeMillis())
    assertTrue("the session goes on", s.channel.isOpen)
    val fresh = renewal(s)
    assertFalse(java.util.Arrays.equals(fresh, s.token))

    // Until the server hears the client use it, it signs with the old one, which the client has
    s.ping(s.token)
    assertEquals(1, heartbeats(signedBy(s.written(), s.token)))

    // The client's first packet signed with it switches the server over
    s.ping(fresh)
    val after = s.written()
    assertEquals("taken, and answered with the new token", 1, heartbeats(signedBy(after, fresh)))
    assertTrue(signedBy(after, s.token).isEmpty)

    // What the client signed with the old one just before it switched is still taken, for a moment
    s.ping(s.token)
    assertEquals(1, heartbeats(signedBy(s.written(), fresh)))

    // And the new token is good for another lifetime
    server.renewSessions(System.currentTimeMillis())
    assertTrue(signedBy(s.written(), fresh).collect { case t: SessionTokenPacket => t }.isEmpty)
    assertTrue(s.channel.isOpen)
  }

  @Test def theTokenARenewalReplacedIsTakenOnlyForAMoment(): Unit = {
    val s = new Session(UUID.randomUUID())
    join(s)
    age(s, Constants.SESSION_TOKEN_LIFETIME_MS + 1000)
    server.renewSessions(System.currentTimeMillis())
    val fresh = renewal(s)
    val sessions = server.sessions
    val now = System.currentTimeMillis()
    val byNew = PacketSigner.sign(new HeartbeatPacket(1, s.playerId).serialize(), fresh)
    val byOld = PacketSigner.sign(new HeartbeatPacket(2, s.playerId).serialize(), s.token)
    assertNotNull("the client is heard using the new token", sessions.verify(s.playerId, byNew, s.token, now))
    val current = sessions.sessionTokens.get(s.playerId)
    assertTrue(java.util.Arrays.equals(fresh, current))
    assertNotNull(sessions.verify(s.playerId, byOld, current, now + Constants.RETIRED_TOKEN_GRACE_MS))
    assertNull("and then no more", sessions.verify(s.playerId, byOld, current, now + Constants.RETIRED_TOKEN_GRACE_MS + 1))
  }

  @Test def aClientThatNeverTakesUpItsNewTokenIsCutOff(): Unit = {
    val s = new Session(UUID.randomUUID())
    join(s)
    age(s, Constants.SESSION_TOKEN_LIFETIME_MS + 1000)
    server.renewSessions(System.currentTimeMillis())
    assertTrue(s.channel.isOpen)
    age(s, Constants.SESSION_TOKEN_LIFETIME_MS + Constants.SESSION_RENEWAL_WINDOW_MS + 1000)
    server.renewSessions(System.currentTimeMillis())
    assertFalse("its time to switch is up: it has to log in again", s.channel.isOpen)
    assertNull(server.getConnectedPlayer(s.playerId))
  }

  @Test def aLoginThatNeverJoinedIsClosedWhenItsSessionEnds(): Unit = {
    // Only a player who had joined used to have their connection closed: one that logged in and
    // never joined kept it, and the server went on taking its packets after the token was gone
    val s = new Session(UUID.randomUUID())
    age(s, Constants.SESSION_TOKEN_LIFETIME_MS + 1000)
    server.renewSessions(System.currentTimeMillis())
    assertFalse(s.channel.isOpen)
    assertNull(server.sessions.sessionTokens.get(s.playerId))
  }

  @Test def aPlayerWhoGoesQuietLeavesNothingBehind(): Unit = {
    // Timed out, a player's channel loses its binding before it is closed, so its disconnect can't
    // find them: what the lobby handler kept of them stayed, one entry for every account
    val s = new Session(UUID.randomUUID())
    join(s)
    s.send(n => new LobbyActionPacket(n, s.playerId, LobbyAction.LIST_REQUEST))
    assertTrue(server.lobbyHandler.lastListRequestTime.containsKey(s.playerId))
    server.cleanup(System.currentTimeMillis() + Constants.CLIENT_TIMEOUT_MS + 1000)
    assertNull("timed out", server.getConnectedPlayer(s.playerId))
    assertFalse(server.lobbyHandler.lastListRequestTime.containsKey(s.playerId))
  }
}
