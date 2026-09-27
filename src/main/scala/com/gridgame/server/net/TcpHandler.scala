package com.gridgame.server.net

import com.gridgame.common.Constants
import com.gridgame.common.observability.Attrs
import com.gridgame.common.observability.Metrics
import com.gridgame.common.protocol.PacketSerializer
import com.gridgame.common.protocol.PacketSigner
import com.gridgame.common.protocol.PacketType
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler

import java.util.UUID
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger

/** Netty handler for incoming TCP connections. */
class TcpHandler(sessions: Sessions, rateLimiter: RateLimiter, replay: ReplayGuard, router: PacketRouter)
    extends SimpleChannelInboundHandler[io.netty.buffer.ByteBuf] {

  override def channelActive(ctx: ChannelHandlerContext): Unit = {
    val remoteAddr = ctx.channel().remoteAddress().asInstanceOf[InetSocketAddress].getAddress
    if (!rateLimiter.allowConnection(remoteAddr)) {
      System.err.println(s"TCP: Connection rate limit exceeded for $remoteAddr, closing")
      Metrics.rateLimitTriggered.add(1L, Attrs.RlConnection)
      Metrics.connectionsClosed.add(1L, Attrs.ConnTcpRejectedRateLimit)
      ctx.close()
      return
    }
    super.channelActive(ctx)
  }

  override def channelRead0(ctx: ChannelHandlerContext, msg: io.netty.buffer.ByteBuf): Unit = {
    if (msg.readableBytes() < Constants.PACKET_SIZE) {
      Metrics.packetsDropped.add(1L, Attrs.ReasonTooShort)
      return
    }

    val data = new Array[Byte](Constants.PACKET_SIZE)
    msg.readBytes(data)
    Metrics.bandwidthBytes.add(data.length.toLong, Attrs.DirIn)

    try {
      // Peek at packet type byte
      val packetTypeId = data(0)
      // Who this channel logged in as (null before a login)
      var channelPlayer: UUID = null
      val payload = if (packetTypeId == PacketType.AUTH_REQUEST.id) {
        // AUTH_REQUEST has no session token yet — extract first 64 bytes
        val p = new Array[Byte](Constants.PACKET_PAYLOAD_SIZE)
        System.arraycopy(data, 0, p, 0, Constants.PACKET_PAYLOAD_SIZE)
        p
      } else {
        // Look up session token via channel
        val token = sessions.channelToToken.get(ctx.channel())
        if (token != null) {
          // Inline token expiry check for TCP
          val chPlayerId = sessions.channelToPlayer.get(ctx.channel())
          if (chPlayerId != null) {
            if (sessions.isExpired(chPlayerId, System.currentTimeMillis())) {
              System.err.println("TCP: Session token expired, closing channel")
              Metrics.packetsDropped.add(1L, Attrs.ReasonExpired)
              ctx.close()
              return
            }
          }
          val verified = PacketSigner.verify(data, token)
          if (verified == null) {
            System.err.println("TCP: HMAC verification failed, dropping packet")
            Metrics.packetsDropped.add(1L, Attrs.ReasonHmacFail)
            Metrics.hmacFailures.add(1L, io.opentelemetry.api.common.Attributes.of(Attrs.Transport, "tcp"))
            return
          }
          channelPlayer = chPlayerId
          verified
        } else {
          // No token yet (pre-auth) — ONLY allow AUTH_REQUEST
          // All other packet types require authentication
          System.err.println(s"TCP: Non-auth packet on pre-auth channel (type=$packetTypeId), dropping")
          Metrics.packetsDropped.add(1L, Attrs.ReasonNoToken)
          return
        }
      }
      val packet = PacketSerializer.deserialize(payload)
      // Rate limit packets
      if (packetTypeId == PacketType.AUTH_REQUEST.id) {
        // Pre-auth: rate limit by channel (no player ID available yet)
        if (!rateLimiter.allowPreAuthPacket(ctx.channel())) {
          Metrics.rateLimitTriggered.add(1L, Attrs.RlPreAuth)
          Metrics.packetsDropped.add(1L, Attrs.ReasonRateLimit)
          return
        }
      } else {
        // A packet naming anyone but the player this channel logged in as is dropped before any
        // of that player's state is touched. The signature only proves who sent it, not who it
        // names: checked after the rate limit and the replay window, as it was, a packet in a
        // victim's name spent the victim's budget, and one numbered far ahead of the victim's
        // count made every packet they sent afterwards look like a replay.
        if (channelPlayer == null || !channelPlayer.equals(packet.getPlayerId)) {
          System.err.println(s"TCP: Packet names a player other than the channel's, dropping")
          Metrics.packetsDropped.add(1L, Attrs.ReasonUuidMismatch)
          return
        }
        if (!rateLimiter.allowPacket(channelPlayer, false)) {
          Metrics.rateLimitTriggered.add(1L, Attrs.RlTcp)
          Metrics.packetsDropped.add(1L, Attrs.ReasonRateLimit)
          return
        }
        // Replay protection: reject duplicate/out-of-order TCP sequence numbers
        if (!replay.validateSequence(channelPlayer, packet.getSequenceNumber, isUdp = false)) {
          Metrics.replayRejected.add(1L, io.opentelemetry.api.common.Attributes.of(Attrs.Transport, "tcp"))
          Metrics.packetsDropped.add(1L, Attrs.ReasonReplay)
          return
        }
      }
      router.handleIncomingPacket(packet, ctx.channel(), null)
    } catch {
      case e: Exception =>
        val msg = if (e.getMessage != null) e.getMessage else e.getClass.getSimpleName
        System.err.println(s"TCP: Invalid packet received: $msg")
        Metrics.packetDeserializeFailures.add(1L, io.opentelemetry.api.common.Attributes.of(Attrs.Transport, "tcp"))
        Metrics.packetsDropped.add(1L, Attrs.ReasonMalformed)
        // Track malformed packets per channel and disconnect on excessive errors
        val ch = ctx.channel()
        val counter = sessions.malformedPacketCounts.computeIfAbsent(ch, _ => new AtomicInteger(0))
        if (counter.incrementAndGet() >= sessions.MAX_MALFORMED_PACKETS) {
          System.err.println(s"TCP: Too many malformed packets, closing: ${ch.remoteAddress()}")
          Metrics.rateLimitTriggered.add(1L, Attrs.RlMalformed)
          ch.close()
        }
    }
  }

  override def channelInactive(ctx: ChannelHandlerContext): Unit = {
    router.handleDisconnect(ctx.channel())
    super.channelInactive(ctx)
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
    val msg = if (cause.getMessage != null) cause.getMessage else cause.getClass.getSimpleName
    System.err.println(s"TCP handler error: $msg")
    ctx.close()
  }
}
