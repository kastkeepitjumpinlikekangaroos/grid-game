package com.gridgame.server.net

import com.gridgame.common.Constants
import com.gridgame.common.observability.Attrs
import com.gridgame.common.observability.Metrics
import com.gridgame.common.protocol.PacketSerializer
import com.gridgame.common.protocol.PacketSigner
import com.gridgame.common.protocol.PacketType
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.socket.DatagramPacket

import java.util.UUID


/** Netty handler for incoming UDP packets. */
class UdpHandler(sessions: Sessions, rateLimiter: RateLimiter, replay: ReplayGuard, router: PacketRouter)
    extends SimpleChannelInboundHandler[DatagramPacket] {

  override def channelRead0(ctx: ChannelHandlerContext, msg: DatagramPacket): Unit = {
    val buf = msg.content()
    if (buf.readableBytes() < Constants.PACKET_SIZE) {
      Metrics.packetsDropped.add(1L, Attrs.ReasonTooShort)
      return
    }

    val data = new Array[Byte](Constants.PACKET_SIZE)
    buf.readBytes(data)
    Metrics.bandwidthBytes.add(data.length.toLong, Attrs.DirIn)

    try {
      // Extract UUID from bytes [5-20] to look up session token
      val uuidBuf = java.nio.ByteBuffer.wrap(data, 5, 16).order(java.nio.ByteOrder.BIG_ENDIAN)
      val msb = uuidBuf.getLong()
      val lsb = uuidBuf.getLong()
      val playerId = new UUID(msb, lsb)
      val token = sessions.sessionTokens.get(playerId)

      // Require HMAC for all UDP packets (no pre-auth UDP allowed)
      if (token == null) {
        Metrics.packetsDropped.add(1L, Attrs.ReasonNoToken)
        return
      }

      // Inline token expiry check (don't wait for cleanup loop)
      if (sessions.isExpired(playerId, System.currentTimeMillis())) {
        Metrics.packetsDropped.add(1L, Attrs.ReasonExpired)
        return
      }

      // Validate UDP source IP matches TCP connection IP (cheap check before expensive HMAC)
      val expectedAddr = sessions.playerTcpAddresses.get(playerId)
      if (expectedAddr == null) {
        // No TCP address registered yet — reject UDP until TCP handshake completes
        Metrics.packetsDropped.add(1L, Attrs.ReasonUnauthenticated)
        return
      }
      val senderAddr = msg.sender().getAddress
      if (!senderAddr.equals(expectedAddr)) {
        System.err.println(s"UDP: Source IP mismatch for ${playerId.toString.substring(0, 8)}, dropping")
        Metrics.packetsDropped.add(1L, Attrs.ReasonUdpSpoof)
        return
      }

      // The signature before the rate limit. A player's UUID is no secret, and a datagram's
      // source address is whatever its sender writes into it (or shared, behind one NAT), so the
      // address check above doesn't make a datagram theirs; only their session's signature does.
      // Rate limited first, a flood of unsigned datagrams in a player's name spent their budget
      // and their own position updates were dropped.
      val verified = PacketSigner.verify(data, token)
      if (verified == null) {
        System.err.println("UDP: HMAC verification failed, dropping packet")
        Metrics.packetsDropped.add(1L, Attrs.ReasonHmacFail)
        Metrics.hmacFailures.add(1L, io.opentelemetry.api.common.Attributes.of(Attrs.Transport, "udp"))
        return
      }

      if (!rateLimiter.allowPacket(playerId, true)) {
        Metrics.rateLimitTriggered.add(1L, Attrs.RlUdp)
        Metrics.packetsDropped.add(1L, Attrs.ReasonRateLimit)
        return
      }
      val payload = verified
      val packet = PacketSerializer.deserialize(payload)
      // Replay protection: reject duplicate/out-of-order UDP sequence numbers
      if (!replay.validateSequence(playerId, packet.getSequenceNumber, isUdp = true)) {
        Metrics.replayRejected.add(1L, io.opentelemetry.api.common.Attributes.of(Attrs.Transport, "udp"))
        Metrics.packetsDropped.add(1L, Attrs.ReasonReplay)
        return
      }
      router.handleIncomingPacket(packet, null, msg.sender())
    } catch {
      case e: Exception =>
        val emsg = if (e.getMessage != null) e.getMessage else e.getClass.getSimpleName
        System.err.println(s"UDP: Invalid packet received: $emsg")
        Metrics.packetDeserializeFailures.add(1L, io.opentelemetry.api.common.Attributes.of(Attrs.Transport, "udp"))
        Metrics.packetsDropped.add(1L, Attrs.ReasonMalformed)
    }
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
    val msg = if (cause.getMessage != null) cause.getMessage else cause.getClass.getSimpleName
    System.err.println(s"UDP handler error: $msg")
  }
}
