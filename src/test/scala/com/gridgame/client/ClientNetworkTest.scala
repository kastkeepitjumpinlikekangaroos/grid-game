package com.gridgame.client

import com.gridgame.common.Constants
import com.gridgame.common.model._
import com.gridgame.common.protocol._
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.channel.socket.DatagramPacket
import org.junit.Assert._
import org.junit.Test

import java.net.InetSocketAddress
import java.util.UUID
import scala.collection.mutable
import com.gridgame.client.game.GameClient
import com.gridgame.client.net.{ClientTcpHandler, ClientUdpHandler, NetworkThread}

/**
 * The client's two doors. Once logged in, every packet from the server is signed with the
 * session's token, and nothing else is taken. Over UDP that signature is all there is: anyone can
 * write the server's address as a datagram's sender, and the handler doesn't look.
 */
class ClientNetworkTest {
  private val heard = mutable.Buffer[Packet]()
  private val client = new GameClient("localhost", 0, WorldData.createEmpty(60, 60)) {
    override def enqueuePacket(packet: Packet): Unit = heard.synchronized(heard += packet)
  }
  private val net = new NetworkThread("localhost", 0, client.enqueuePacket, () => client.sendHeartbeat())
  private val tcp = new EmbeddedChannel(new ClientTcpHandler(client.enqueuePacket, net))
  private val udp = new EmbeddedChannel(new ClientUdpHandler(client.enqueuePacket, net))
  private val token = Array.tabulate[Byte](32)(i => (i * 7 + 1).toByte)
  private val me = UUID.randomUUID()

  private def signed(p: Packet, key: Array[Byte] = token): Array[Byte] = PacketSigner.sign(p.serialize(), key)

  private def unsigned(p: Packet): Array[Byte] = {
    val bytes = new Array[Byte](Constants.PACKET_SIZE)
    System.arraycopy(p.serialize(), 0, bytes, 0, Constants.PACKET_PAYLOAD_SIZE)
    bytes
  }

  private def overTcp(bytes: Array[Byte]): Unit = tcp.writeInbound(Unpooled.wrappedBuffer(bytes))

  private def overUdp(bytes: Array[Byte]): Unit =
    udp.writeInbound(new DatagramPacket(Unpooled.wrappedBuffer(bytes),
      new InetSocketAddress("127.0.0.1", 40000), new InetSocketAddress("127.0.0.1", Constants.SERVER_PORT)))

  private def update(seq: Int): Packet = new PlayerUpdatePacket(seq, me, new Position(5, 5), 0)

  @Test def beforeLoginTheServersRepliesAreTakenUnsigned(): Unit = {
    // A login's reply, and the session token itself, come before there is a token to check them by
    overTcp(unsigned(new SessionTokenPacket(1, me, token)))
    assertEquals(1, heard.size)
    assertArrayEquals(token, heard.head.asInstanceOf[SessionTokenPacket].getSessionToken)
  }

  @Test def beforeLoginNoDatagramIsTaken(): Unit = {
    overUdp(unsigned(update(1)))
    overUdp(signed(update(2)))
    assertTrue(heard.isEmpty)
  }

  @Test def afterLoginOnlyWhatTheSessionSignedIsTaken(): Unit = {
    net.sessionToken = token
    overTcp(signed(update(1)))
    overUdp(signed(update(2)))
    assertEquals(2, heard.size)
    overTcp(unsigned(update(3)))
    overUdp(unsigned(update(4)))
    overTcp(signed(update(5), new Array[Byte](32)))
    overUdp(signed(update(6), new Array[Byte](32)))
    assertEquals("nothing unsigned, and nothing signed with another key", 2, heard.size)
  }

  @Test def aDatagramTooShortToBeAPacketIsIgnored(): Unit = {
    net.sessionToken = token
    overUdp(new Array[Byte](10))
    assertTrue(heard.isEmpty)
  }

  @Test def signedGarbageIsDroppedAndTheConnectionKept(): Unit = {
    net.sessionToken = token
    val garbage = new Array[Byte](Constants.PACKET_PAYLOAD_SIZE)
    garbage(0) = 0x7F // no such packet type
    overTcp(PacketSigner.sign(garbage, token))
    assertTrue(heard.isEmpty)
    assertTrue(tcp.isOpen)
    overTcp(signed(update(7)))
    assertEquals(1, heard.size)
  }

  // --- Why the server ended the session (SESSION_END) ---

  @Test def whyTheServerEndedTheSessionIsReadAsItArrives(): Unit = {
    // On the packet thread it would be too late: the disconnect right behind it stops that thread,
    // and the client couldn't tell being logged in elsewhere from losing the connection
    net.sessionToken = token
    assertEquals(SessionEndReason.NONE, net.sessionEndReason)
    overTcp(signed(new SessionEndPacket(9, me, SessionEndReason.REPLACED)))
    assertEquals(SessionEndReason.REPLACED, net.sessionEndReason)
  }

  @Test def aSessionEndTheSessionDidNotSignIsIgnored(): Unit = {
    net.sessionToken = token
    overTcp(unsigned(new SessionEndPacket(9, me, SessionEndReason.REVOKED)))
    overTcp(signed(new SessionEndPacket(10, me, SessionEndReason.REVOKED), new Array[Byte](32)))
    assertEquals(SessionEndReason.NONE, net.sessionEndReason)
  }

  // --- An hour in: the server renews the session's token (server/net/Sessions) ---

  private val renewed = Array.tabulate[Byte](32)(i => (i * 13 + 5).toByte)

  @Test def aRenewedTokenIsTakenUpBeforeThePacketsBehindIt(): Unit = {
    // Taken up on the packet thread, as it was, it came after the packets behind it had been
    // checked, and those signed with it were dropped
    net.sessionToken = token
    overTcp(signed(new SessionTokenPacket(2, me, renewed)))
    overTcp(signed(update(3), renewed))
    overUdp(signed(update(4), renewed))
    assertEquals(3, heard.size)
    assertArrayEquals("what we send is signed with it from now on", renewed, net.sessionToken)
  }

  @Test def theTokenARenewalReplacedIsTakenForAWhile(): Unit = {
    // The server goes on signing with the old one until it has heard us use the new one
    var now = 0L
    val clocked = new NetworkThread("localhost", 0, client.enqueuePacket, () => (), () => now)
    val tcp2 = new EmbeddedChannel(new ClientTcpHandler(client.enqueuePacket, clocked))
    val udp2 = new EmbeddedChannel(new ClientUdpHandler(client.enqueuePacket, clocked))
    def udpIn(bytes: Array[Byte]): Unit =
      udp2.writeInbound(new DatagramPacket(Unpooled.wrappedBuffer(bytes),
        new InetSocketAddress("127.0.0.1", 40000), new InetSocketAddress("127.0.0.1", Constants.SERVER_PORT)))
    clocked.sessionToken = token
    tcp2.writeInbound(Unpooled.wrappedBuffer(signed(new SessionTokenPacket(2, me, renewed))))
    udpIn(signed(update(3)))
    assertEquals(2, heard.size)
    now += Constants.SESSION_RENEWAL_WINDOW_MS * 1000000L + 1
    udpIn(signed(update(4)))
    assertEquals("and not for ever", 2, heard.size)
    udpIn(signed(update(5), renewed))
    assertEquals(3, heard.size)
  }
}
