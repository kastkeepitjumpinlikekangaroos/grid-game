package com.gridgame.server.net

import com.gridgame.common.Constants
import com.gridgame.common.model.Player
import com.gridgame.common.observability.Attrs
import com.gridgame.common.observability.Metrics
import com.gridgame.common.protocol.Packet
import com.gridgame.common.protocol.PacketSigner
import com.gridgame.common.protocol.PacketType
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.socket.DatagramPacket

import java.util.concurrent.atomic.AtomicInteger

/**
 * Everything the server sends goes out through here: to a player over the transport its packet
 * type names (TCP or UDP), signed with that player's session token — or padded to size, unsigned,
 * before they have one — and counted in the metrics. The server's own packets are numbered from
 * one counter ([[nextSeq]]).
 */
final class Outbox(sessions: Sessions) {
  @volatile private var udpChannel: Channel = _
  private val sequenceNumber = new AtomicInteger(0)

  /** The channel UDP packets are sent from: the server binds the real one; tests attach their own
    * to see what each player is sent. */
  def attachUdpChannel(ch: Channel): Unit = udpChannel = ch

  /** The number the server's next packet carries. */
  def nextSeq(): Int = sequenceNumber.getAndIncrement() & 0x7FFFFFFF

  /** Send a packet to a player, over the transport its type names. */
  def send(packet: Packet, player: Player): Unit = {
    val token = sessions.sessionTokens.get(player.getId)
    val pktAttrs = Attrs.packet(packet.getType)
    if (packet.getType.tcp) {
      val raw = player.getTcpChannel
      if (raw != null) {
        val ch = raw.asInstanceOf[Channel]
        if (ch.isActive) {
          val payload = packet.serialize()
          val data = if (token != null) PacketSigner.sign(payload, token) else padToPacketSize(payload)
          ch.writeAndFlush(Unpooled.wrappedBuffer(data))
          Metrics.packetsSent.add(1L, pktAttrs)
          Metrics.bandwidthBytes.add(data.length.toLong, Attrs.DirOut)
        }
      }
    } else {
      val addr = player.getUdpAddress
      if (addr != null && udpChannel != null) {
        val payload = packet.serialize()
        val data = if (token != null) PacketSigner.sign(payload, token) else padToPacketSize(payload)
        val dgram = new DatagramPacket(Unpooled.wrappedBuffer(data), addr)
        udpChannel.writeAndFlush(dgram)
        Metrics.packetsSent.add(1L, pktAttrs)
        Metrics.bandwidthBytes.add(data.length.toLong, Attrs.DirOut)
      }
    }
  }

  /** Send a packet over a particular TCP channel, signed with that channel's session (a login's
    * replies, and the first packets of a join). */
  def sendVia(packet: Packet, channel: Channel): Unit = {
    if (channel != null && channel.isActive) {
      val payload = packet.serialize()
      val token = sessions.channelToToken.get(channel)
      val data = if (token != null) PacketSigner.sign(payload, token) else padToPacketSize(payload)
      channel.writeAndFlush(Unpooled.wrappedBuffer(data))
      Metrics.packetsSent.add(1L, Attrs.packet(packet.getType))
      Metrics.bandwidthBytes.add(data.length.toLong, Attrs.DirOut)
    }
  }

  /** Send a packet over the player's TCP channel whatever its type: a heartbeat's echo, which
    * keeps the client's TCP read timeout from firing. The packet is only made if the channel is
    * open to take it. Not counted, as it never was. */
  def sendOverTcp(player: Player)(packet: => Packet): Unit = {
    val tcpCh = player.getTcpChannel
    if (tcpCh != null) {
      val ch = tcpCh.asInstanceOf[Channel]
      if (ch.isActive) {
        val payload = packet.serialize()
        val token = sessions.sessionTokens.get(player.getId)
        val data = if (token != null) PacketSigner.sign(payload, token) else padToPacketSize(payload)
        ch.writeAndFlush(Unpooled.wrappedBuffer(data))
      }
    }
  }

  /** Resolve the {type, transport} attribute set for a serialized payload's first byte.
   *  Returns the UNKNOWN sentinel if the byte doesn't match a known packet type. */
  private def rawSendAttrs(data: Array[Byte], isTcp: Boolean): io.opentelemetry.api.common.Attributes = {
    if (data.length == 0) return if (isTcp) Attrs.UnknownPacket else Attrs.UnknownPacketUdp
    try Attrs.packet(PacketType.fromId(data(0)))
    catch { case _: Throwable => if (isTcp) Attrs.UnknownPacket else Attrs.UnknownPacketUdp }
  }

  /** Send pre-serialized packet bytes to a player. Serialization is done once by the caller. */
  def sendRaw(data: Array[Byte], isTcp: Boolean, player: Player): Unit = {
    val token = sessions.sessionTokens.get(player.getId)
    val signed = if (token != null) PacketSigner.sign(data, token) else padToPacketSize(data)
    val pktAttrs = rawSendAttrs(data, isTcp)
    if (isTcp) {
      val raw = player.getTcpChannel
      if (raw != null) {
        val ch = raw.asInstanceOf[Channel]
        if (ch.isActive) {
          ch.writeAndFlush(Unpooled.wrappedBuffer(signed))
          Metrics.packetsSent.add(1L, pktAttrs)
          Metrics.bandwidthBytes.add(signed.length.toLong, Attrs.DirOut)
        }
      }
    } else {
      val addr = player.getUdpAddress
      if (addr != null && udpChannel != null) {
        val dgram = new DatagramPacket(Unpooled.wrappedBuffer(signed), addr)
        udpChannel.writeAndFlush(dgram)
        Metrics.packetsSent.add(1L, pktAttrs)
        Metrics.bandwidthBytes.add(signed.length.toLong, Attrs.DirOut)
      }
    }
  }

  /** Buffered send: write without flushing. Call flush() or flushUdp() after a batch. */
  def sendRawBuffered(data: Array[Byte], isTcp: Boolean, player: Player): Unit = {
    val token = sessions.sessionTokens.get(player.getId)
    val signed = if (token != null) PacketSigner.sign(data, token) else padToPacketSize(data)
    val pktAttrs = rawSendAttrs(data, isTcp)
    if (isTcp) {
      val raw = player.getTcpChannel
      if (raw != null) {
        val ch = raw.asInstanceOf[Channel]
        if (ch.isActive) {
          ch.write(Unpooled.wrappedBuffer(signed))
          Metrics.packetsSent.add(1L, pktAttrs)
          Metrics.bandwidthBytes.add(signed.length.toLong, Attrs.DirOut)
        }
      }
    } else {
      val addr = player.getUdpAddress
      if (addr != null && udpChannel != null) {
        val dgram = new DatagramPacket(Unpooled.wrappedBuffer(signed), addr)
        udpChannel.write(dgram)
        Metrics.packetsSent.add(1L, pktAttrs)
        Metrics.bandwidthBytes.add(signed.length.toLong, Attrs.DirOut)
      }
    }
  }

  /** Flush a player's TCP channel after buffered writes. */
  def flush(player: Player): Unit = {
    val raw = player.getTcpChannel
    if (raw != null) {
      val ch = raw.asInstanceOf[Channel]
      if (ch.isActive) ch.flush()
    }
  }

  /** Flush the server UDP channel after buffered writes. */
  def flushUdp(): Unit = {
    if (udpChannel != null) udpChannel.flush()
  }

  /** Pad a 64-byte payload to 80 bytes (for pre-auth packets without HMAC). */
  private def padToPacketSize(payload: Array[Byte]): Array[Byte] = {
    if (payload.length == Constants.PACKET_SIZE) return payload
    val padded = new Array[Byte](Constants.PACKET_SIZE)
    System.arraycopy(payload, 0, padded, 0, Math.min(payload.length, Constants.PACKET_PAYLOAD_SIZE))
    padded
  }
}
