package com.gridgame.server

import com.gridgame.common.model.Position
import com.gridgame.common.protocol._
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
    channel.pipeline().addLast(new GameServerTcpHandler(server))
    val token: Array[Byte] = server.generateSessionToken(playerId, channel) // as a login does
    private var seq = 0

    /** Send a packet as this session's client does: its own count, signed with its token. */
    def send(make: Int => Packet): Unit = {
      seq += 1
      channel.writeInbound(Unpooled.wrappedBuffer(PacketSigner.sign(make(seq).serialize(), token)))
    }
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
}
