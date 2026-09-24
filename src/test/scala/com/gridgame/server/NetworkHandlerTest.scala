package com.gridgame.server

import com.gridgame.common.Constants
import com.gridgame.common.model.Position
import com.gridgame.common.protocol._
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.channel.socket.DatagramPacket
import org.junit.Assert._
import org.junit.Test

import java.net.InetSocketAddress
import java.util.UUID

/**
 * The two doors every packet comes in by, and what each checks before anything is done with it:
 * the TCP handler (a session's HMAC, whose channel it came in on, its sequence number, a rate)
 * and the UDP one (the same HMAC, the sender's address, a replay window, a rate). Each test is a
 * packet a broken or hostile client could send.
 */
class NetworkHandlerTest {
  import ServerTestKit._

  private implicit class Tidy(s: TestSession) {
    def leaveLobby(): Unit = {
      val l = s.lobby
      if (l != null) server.lobbyManager.removeLobby(l.id)
    }
  }

  // --- TCP ---

  @Test def aPacketInAnotherPlayersNameCannotMakeTheirsLookLikeReplays(): Unit = {
    // Signed with the sender's own token, which is all it has, but naming someone else, far ahead
    // in their count. The channel's identity check threw it out — but only after the replay check
    // had taken its number as the victim's newest, so every packet the victim sent from then on
    // was dropped as a replay until they logged in again.
    val victim = new TestSession()
    victim.join()
    val attacker = new TestSession()
    attacker.join()
    attacker.sendSigned(new LobbyActionPacket(0x3FFFFFFF, victim.playerId, LobbyAction.LIST_REQUEST), attacker.token)
    victim.createLobby()
    assertNotNull("the victim's own packets still get through", victim.lobby)
    victim.leaveLobby()
  }

  @Test def aPacketInAnotherPlayersNameDoesNotSpendTheirBudget(): Unit = {
    // Numbered as replays, so they can't touch the victim's count; they still came out of the
    // victim's forty packets a second, and the victim's own next packet was refused for it
    val victim = new TestSession()
    victim.join()
    val attacker = new TestSession()
    attacker.join()
    for (_ <- 0 until 60) {
      attacker.sendSigned(new LobbyActionPacket(0, victim.playerId, LobbyAction.LIST_REQUEST), attacker.token)
    }
    victim.createLobby()
    assertNotNull("the victim's own packet was not rate limited", victim.lobby)
    victim.leaveLobby()
  }

  @Test def aPacketInAnotherPlayersNameIsNotActedOn(): Unit = {
    val victim = new TestSession()
    victim.join()
    val attacker = new TestSession()
    attacker.join()
    attacker.sendSigned(new LobbyActionPacket(attacker.nextSeq(), victim.playerId, Packet.getCurrentTimestamp,
      LobbyAction.CREATE, 0.toShort, 0.toByte, 5.toByte, 0.toByte, 8.toByte, 0.toByte, "not yours"), attacker.token)
    assertNull(victim.lobby)
    assertNull(attacker.lobby)
  }

  @Test def nothingButALoginIsTakenBeforeOne(): Unit = {
    val ch = new PeerChannel()
    ch.pipeline().addLast(new GameServerTcpHandler(server))
    val id = UUID.randomUUID()
    val payload = new PlayerJoinPacket(1, id, new Position(1, 1), 0, "p").serialize()
    val unsigned = new Array[Byte](Constants.PACKET_SIZE)
    System.arraycopy(payload, 0, unsigned, 0, Constants.PACKET_PAYLOAD_SIZE)
    ch.writeInbound(Unpooled.wrappedBuffer(unsigned))
    assertNull(server.getConnectedPlayer(id))
  }

  @Test def aPacketSignedWithTheWrongKeyIsDropped(): Unit = {
    val s = new TestSession()
    s.sendSigned(new PlayerJoinPacket(s.nextSeq(), s.playerId, new Position(1, 1), 0, "p"), new Array[Byte](32))
    assertNull(server.getConnectedPlayer(s.playerId))
    assertTrue("a bad signature alone doesn't close the channel", s.channel.isOpen)
  }

  @Test def anExpiredSessionIsClosed(): Unit = {
    val s = new TestSession()
    server.tokenCreationTime.put(s.playerId, System.currentTimeMillis() - Constants.SESSION_TOKEN_LIFETIME_MS - 1000)
    s.join()
    assertNull("nothing from it is handled", server.getConnectedPlayer(s.playerId))
    assertFalse("and it has to log in again", s.channel.isOpen)
  }

  @Test def aChannelSendingGarbageIsClosedOnItsThirdPacket(): Unit = {
    val s = new TestSession()
    s.join()
    val garbage = new Array[Byte](Constants.PACKET_PAYLOAD_SIZE)
    garbage(0) = 0x7F // no such packet type
    def sendGarbage(): Unit = s.channel.writeInbound(Unpooled.wrappedBuffer(PacketSigner.sign(garbage, s.token)))
    sendGarbage()
    sendGarbage()
    assertTrue(s.channel.isOpen)
    sendGarbage()
    assertFalse(s.channel.isOpen)
  }

  // --- UDP ---

  private val udp = new EmbeddedChannel(new GameServerUdpHandler(server))
  private val serverAddress = new InetSocketAddress("127.0.0.1", Constants.SERVER_PORT)

  private def datagram(bytes: Array[Byte], from: InetSocketAddress): Unit =
    udp.writeInbound(new DatagramPacket(Unpooled.wrappedBuffer(bytes), serverAddress, from))

  private def heartbeat(s: TestSession, seq: Int, key: Array[Byte] = null): Array[Byte] =
    PacketSigner.sign(new HeartbeatPacket(seq, s.playerId).serialize(), if (key != null) key else s.token)

  private def from(host: String = "127.0.0.1"): InetSocketAddress = new InetSocketAddress(host, freshPort())

  private def echoes(s: TestSession): Int = s.received().count(_.isInstanceOf[HeartbeatPacket])

  @Test def aHeartbeatIsEchoedAndTellsTheServerWhereToSend(): Unit = {
    val s = new TestSession()
    s.join()
    s.received()
    val addr = from()
    datagram(heartbeat(s, s.nextSeq()), addr)
    assertEquals(addr, server.getConnectedPlayer(s.playerId).getUdpAddress)
    assertEquals("echoed over TCP, which keeps the client's read timeout alive", 1, echoes(s))
  }

  @Test def forgedDatagramsInAPlayersNameDoNotSpendTheirBudget(): Unit = {
    // A player's UUID is no secret, and a datagram's source address is whatever its sender
    // writes (or shared, behind one NAT). Rate limited before the signature was checked, a flood
    // of unsigned datagrams in the player's name used up their budget, and their own position
    // updates were dropped.
    val s = new TestSession()
    s.join()
    val forger = from()
    val wrongKey = new Array[Byte](32)
    for (i <- 1 to 300) datagram(heartbeat(s, 100000 + i, wrongKey), forger)
    val real = from()
    datagram(heartbeat(s, s.nextSeq()), real)
    assertEquals("the player's own datagram got through", real, server.getConnectedPlayer(s.playerId).getUdpAddress)
  }

  @Test def aDatagramFromAnotherAddressThanTheLoginIsDropped(): Unit = {
    val s = new TestSession()
    s.join()
    s.received()
    datagram(heartbeat(s, s.nextSeq()), from("10.1.2.3"))
    assertNull(server.getConnectedPlayer(s.playerId).getUdpAddress)
    assertEquals(0, echoes(s))
  }

  @Test def aDatagramWithoutASessionIsDropped(): Unit = {
    val s = new TestSession()
    s.join()
    s.received()
    server.sessionTokens.remove(s.playerId)
    datagram(heartbeat(s, s.nextSeq()), from())
    assertNull(server.getConnectedPlayer(s.playerId).getUdpAddress)
    assertEquals(0, echoes(s))
  }

  @Test def aReplayedDatagramIsHandledOnce(): Unit = {
    val s = new TestSession()
    s.join()
    s.received()
    val addr = from()
    val hb = heartbeat(s, s.nextSeq())
    datagram(hb, addr)
    datagram(hb.clone(), addr)
    assertEquals(1, echoes(s))
  }

  @Test def datagramsMayArriveOutOfOrder(): Unit = {
    val s = new TestSession()
    s.join()
    s.received()
    val addr = from()
    val first = heartbeat(s, s.nextSeq())
    val second = heartbeat(s, s.nextSeq())
    datagram(second, addr)
    datagram(first, addr)
    assertEquals(2, echoes(s))
  }

  @Test def whatMustComeOverTcpIsNotTakenFromUdp(): Unit = {
    val s = new TestSession()
    s.join()
    val create = new LobbyActionPacket(s.nextSeq(), s.playerId, Packet.getCurrentTimestamp, LobbyAction.CREATE,
      0.toShort, 0.toByte, 5.toByte, 0.toByte, 8.toByte, 0.toByte, "over udp")
    datagram(PacketSigner.sign(create.serialize(), s.token), from())
    assertNull(s.lobby)
  }
}
