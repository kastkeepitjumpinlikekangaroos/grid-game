package com.gridgame.client.net

import com.gridgame.common.Constants
import com.gridgame.common.observability.Attrs
import com.gridgame.common.observability.Metrics
import com.gridgame.common.protocol.Packet
import com.gridgame.common.protocol.PacketSerializer
import com.gridgame.common.protocol.PacketSigner
import com.gridgame.common.protocol.PacketType
import com.gridgame.common.protocol.SessionEndPacket
import com.gridgame.common.protocol.SessionEndReason
import com.gridgame.common.protocol.SessionTokenPacket
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.Unpooled
import io.netty.channel._
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.DatagramPacket
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioDatagramChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.LengthFieldBasedFrameDecoder
import io.netty.handler.codec.LengthFieldPrepender
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.SslHandler
import io.netty.handler.timeout.IdleStateEvent
import io.netty.handler.timeout.IdleStateHandler

import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * The client's connection to the server: TLS over TCP for what must arrive, UDP for what may be
 * lost, and a heartbeat every few seconds. Every packet the server sends has to carry the session's
 * signature once there is a session (sessionToken); what passes goes to `onPacket`, on a Netty
 * thread.
 *
 * The server renews a session's token while it lasts (server/net/Sessions): it sends the new one,
 * signed with the old, and goes on signing with the old until it hears us use the new one. So a
 * token is taken up the moment it is read ([[takeSessionToken]]), and the one it replaces is still
 * taken for SESSION_RENEWAL_WINDOW_MS. Why the server ended the session, when it says
 * (SESSION_END), is read here too, as it arrives ([[sessionEndReason]]).
 *
 * The server proves who it is before anything is sent (design/networking.md, layer 1): its TLS key
 * has to be the one `knownServers` has pinned for its host and port (PinnedTrustManager), and the
 * connection isn't ready until the handshake is through. A server met for the first time is pinned
 * then; one that shows another key is refused, and [[waitForReady]] throws
 * ServerIdentityChangedException.
 *
 * @param onPacket       a packet from the server, checked
 * @param onHeartbeatDue time to send the server a heartbeat
 * @param nanoClock      the clock the old token's time is kept on (a test's own, in a test)
 * @param knownServers   the servers met before, and their identities
 */
class NetworkThread(serverHost: String, serverPort: Int, onPacket: Packet => Unit, onHeartbeatDue: () => Unit,
                    nanoClock: () => Long = () => System.nanoTime(),
                    knownServers: KnownServers = KnownServers.inMemory())
    extends Thread("NetworkThread") {
  private val heartbeatExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
  private val ready = new CountDownLatch(1)
  @volatile private var connectError: Throwable = _
  @volatile private var running = false
  @volatile var sessionToken: Array[Byte] = _
  // The token the session's last renewal replaced, and until when it is still taken
  @volatile private var retiredToken: Array[Byte] = _
  @volatile private var retiredUntilNs = 0L
  @volatile var disconnectCallback: Runnable = _
  // Ready, so a drop from here on is a connection lost; before, connecting failed, and waitForReady says so
  @volatile private var established = false
  /** Why the server ended the session (SessionEndReason), if it said before it closed the connection.
    * Read as it arrives: the disconnect right behind it stops the packet thread, which may never get
    * to it. */
  @volatile var sessionEndReason: Byte = SessionEndReason.NONE

  /**
   * A session token from the server: the login's, or a renewal's. Taken up by the handler that
   * read it, before the packets behind it: left to the packet thread, the server's next packets,
   * signed with it, could be checked before it was and dropped. The token it replaces is still taken.
   */
  def takeSessionToken(token: Array[Byte]): Unit = {
    val old = sessionToken
    if (old != null && !java.util.Arrays.equals(old, token)) {
      // Put by before the new token goes in: a handler that reads the new one finds the old by it
      retiredUntilNs = nanoClock() + Constants.SESSION_RENEWAL_WINDOW_MS * 1000000L
      retiredToken = old
    }
    sessionToken = token
  }

  /** The payload of a packet the session signed (with `token`, its token as read by the caller, or
    * for a while after a renewal the one before), or null. */
  private[net] def verify(data: Array[Byte], token: Array[Byte]): Array[Byte] = {
    val payload = PacketSigner.verify(data, token)
    if (payload != null) return payload
    val retired = retiredToken
    if (retired != null && nanoClock() - retiredUntilNs < 0) PacketSigner.verify(data, retired) else null
  }

  private var eventLoopGroup: NioEventLoopGroup = _
  private var tcpChannel: Channel = _
  private var udpChannel: Channel = _
  private val serverAddress = new InetSocketAddress(serverHost, serverPort)

  setDaemon(true)

  /** Blocks until both channels are up and the server has proved who it is; throws if connecting
    * failed, ServerIdentityChangedException if it isn't the server met there before. */
  def waitForReady(): Unit = {
    ready.await()
    connectError match {
      case null =>
      case changed: ServerIdentityChangedException => throw changed
      case err => throw new java.io.IOException(s"Could not connect to $serverHost:$serverPort: ${err.getMessage}", err)
    }
  }

  /** The TCP connection closed: a connection lost, if it was ever ready. */
  private[net] def connectionClosed(): Unit =
    if (established) {
      val cb = disconnectCallback
      if (cb != null) cb.run()
    }

  override def run(): Unit = {
    try {
      eventLoopGroup = new NioEventLoopGroup()
      running = true

      // The server's identity, checked in the handshake against the one pinned for it, if any
      val known = knownServers.identityOf(serverHost, serverPort)
      val trust = new PinnedTrustManager(serverHost, serverPort, known)
      val sslCtx = SslContextBuilder.forClient()
        .trustManager(trust)
        .protocols("TLSv1.3")
        .build()

      // TCP connection
      val tcpBootstrap = new Bootstrap()
      tcpBootstrap.group(eventLoopGroup)
        .channel(classOf[NioSocketChannel])
        .option[java.lang.Boolean](ChannelOption.TCP_NODELAY, true)
        .option[java.lang.Integer](ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
        .handler(new ChannelInitializer[SocketChannel] {
          override def initChannel(ch: SocketChannel): Unit = {
            ch.pipeline()
              .addLast(sslCtx.newHandler(ch.alloc(), serverHost, serverPort))
              .addLast(new IdleStateHandler(Constants.CLIENT_TIMEOUT_MS, 0, 0, TimeUnit.MILLISECONDS))
              .addLast(new LengthFieldBasedFrameDecoder(Constants.PACKET_SIZE + 2, 0, 2, 0, 2))
              .addLast(new LengthFieldPrepender(2))
              .addLast(new ClientTcpHandler(onPacket, NetworkThread.this))
          }
        })

      tcpChannel = tcpBootstrap.connect(serverAddress).sync().channel()
      // Nothing goes out until the server has shown it is the one met here before. (Netty holds
      // what is written before then, and drops it if the handshake fails: this makes it explicit,
      // and lets a refusal be told from a connection that failed.)
      val handshake = tcpChannel.pipeline().get(classOf[SslHandler]).handshakeFuture()
      if (!handshake.await(NetworkThread.HandshakeTimeoutMs, TimeUnit.MILLISECONDS))
        throw new java.io.IOException("The TLS handshake took too long")
      if (!handshake.isSuccess) {
        val cause = handshake.cause()
        throw NetworkThread.identityChange(cause).getOrElse(new java.io.IOException(s"TLS handshake failed: ${cause.getMessage}", cause))
      }
      // Met for the first time: the server to expect here from now on
      if (known.isEmpty && trust.seen != null) knownServers.trust(serverHost, serverPort, trust.seen)
      println(s"NetworkThread: TCP connected to $serverHost:$serverPort (server ${trust.seen})")

      // UDP channel (connected mode to server)
      val udpBootstrap = new Bootstrap()
      udpBootstrap.group(eventLoopGroup)
        .channel(classOf[NioDatagramChannel])
        .handler(new ClientUdpHandler(onPacket, this))

      udpChannel = udpBootstrap.bind(0).sync().channel()
      println(s"NetworkThread: UDP bound on local port")

      established = true
      ready.countDown()

      // Start heartbeat scheduler
      heartbeatExecutor.scheduleAtFixedRate(
        new Runnable { def run(): Unit = sendHeartbeat() },
        Constants.HEARTBEAT_INTERVAL_MS,
        Constants.HEARTBEAT_INTERVAL_MS,
        TimeUnit.MILLISECONDS
      )

      // Block until TCP channel closes
      tcpChannel.closeFuture().sync()
    } catch {
      case e: Exception =>
        if (ready.getCount > 0) connectError = e
        System.err.println(s"NetworkThread: Connection error - ${e.getMessage}")
    } finally {
      // Release waitForReady() on failure too; counting down only on success left the
      // login screen stuck on "Connecting..." forever when the server was unreachable.
      ready.countDown()
      shutdown()
    }
  }

  def send(packet: Packet): Unit = {
    if (!running) {
      System.err.println("NetworkThread: Cannot send packet, not running")
      return
    }

    val payload = packet.serialize()
    val token = sessionToken
    val data = if (token != null) {
      PacketSigner.sign(payload, token)
    } else {
      // No session token yet (pre-auth) — pad to 80 bytes
      val padded = new Array[Byte](Constants.PACKET_SIZE)
      System.arraycopy(payload, 0, padded, 0, Constants.PACKET_PAYLOAD_SIZE)
      padded
    }

    if (packet.getType.tcp) {
      if (tcpChannel != null && tcpChannel.isActive) {
        if (!tcpChannel.isWritable) {
          System.err.println("NetworkThread: TCP write buffer full, dropping packet")
          Metrics.clientErrors.add(1L, io.opentelemetry.api.common.Attributes.of(Attrs.Kind, "tcp_buffer_full"))
          return
        }
        tcpChannel.writeAndFlush(Unpooled.wrappedBuffer(data))
        Metrics.clientPacketsSent.add(1L, Attrs.packet(packet.getType))
      } else {
        System.err.println("NetworkThread: TCP channel not active, cannot send")
      }
    } else {
      if (udpChannel != null && udpChannel.isActive) {
        if (!udpChannel.isWritable) {
          System.err.println("NetworkThread: UDP write buffer full, dropping packet")
          Metrics.clientErrors.add(1L, io.opentelemetry.api.common.Attributes.of(Attrs.Kind, "udp_buffer_full"))
          return
        }
        val dgram = new DatagramPacket(Unpooled.wrappedBuffer(data), serverAddress)
        udpChannel.writeAndFlush(dgram)
        Metrics.clientPacketsSent.add(1L, Attrs.packet(packet.getType))
      } else {
        System.err.println("NetworkThread: UDP channel not active, cannot send")
      }
    }
  }

  private def sendHeartbeat(): Unit = {
    onHeartbeatDue()
  }

  def shutdown(): Unit = {
    running = false
    sessionToken = null

    heartbeatExecutor.shutdown()
    try {
      if (!heartbeatExecutor.awaitTermination(1, TimeUnit.SECONDS)) {
        heartbeatExecutor.shutdownNow()
      }
    } catch {
      case _: InterruptedException =>
        heartbeatExecutor.shutdownNow()
        Thread.currentThread().interrupt()
    }

    if (tcpChannel != null && tcpChannel.isOpen) tcpChannel.close()
    if (udpChannel != null && udpChannel.isOpen) udpChannel.close()
    if (eventLoopGroup != null) eventLoopGroup.shutdownGracefully()

    println("NetworkThread: Shutdown complete")
  }
}

object NetworkThread {
  /** How long the server has to prove who it is. */
  val HandshakeTimeoutMs = 10000L

  /** The server's identity having changed, if that is what stopped the handshake. */
  private[net] def identityChange(t: Throwable): Option[ServerIdentityChangedException] =
    Iterator.iterate(t)(_.getCause).takeWhile(_ != null).take(16).collectFirst { case e: ServerIdentityChangedException => e }
}

/** Handles TCP packets from the server. */
class ClientTcpHandler(onPacket: Packet => Unit, networkThread: NetworkThread) extends SimpleChannelInboundHandler[io.netty.buffer.ByteBuf] {
  // Reuse read buffer — safe because handler runs on a single Netty event loop thread
  // and PacketSigner.verify() copies the payload before we return
  private val readBuffer = new Array[Byte](Constants.PACKET_SIZE)

  override def channelRead0(ctx: ChannelHandlerContext, msg: io.netty.buffer.ByteBuf): Unit = {
    if (msg.readableBytes() < Constants.PACKET_SIZE) return

    val data = readBuffer
    msg.readBytes(data)

    try {
      val token = networkThread.sessionToken
      val payload = if (token != null) {
        val verified = networkThread.verify(data, token)
        if (verified == null) {
          System.err.println("ClientTcpHandler: HMAC verification failed, dropping packet")
          return
        }
        verified
      } else {
        // Pre-auth: no session token yet, accept unsigned
        val p = new Array[Byte](Constants.PACKET_PAYLOAD_SIZE)
        System.arraycopy(data, 0, p, 0, Constants.PACKET_PAYLOAD_SIZE)
        p
      }
      val packet = PacketSerializer.deserialize(payload)
      packet match {
        // Taken up here, before the packets behind it are read: they are signed with it
        case t: SessionTokenPacket => networkThread.takeSessionToken(t.getSessionToken)
        // Here too, before the disconnect behind it
        case e: SessionEndPacket => networkThread.sessionEndReason = e.getReason
        case _ =>
      }
      Metrics.clientPacketsReceived.add(1L, Attrs.packet(packet.getType))
      onPacket(packet)
    } catch {
      case e: IllegalArgumentException =>
        System.err.println(s"ClientTcpHandler: Invalid packet - ${e.getMessage}")
        Metrics.clientErrors.add(1L, io.opentelemetry.api.common.Attributes.of(Attrs.Kind, "deserialize_tcp"))
    }
  }

  override def userEventTriggered(ctx: ChannelHandlerContext, evt: AnyRef): Unit = {
    evt match {
      case _: IdleStateEvent =>
        System.err.println("ClientTcpHandler: Read timeout, closing connection")
        ctx.close()
      case _ =>
        super.userEventTriggered(ctx, evt)
    }
  }

  override def channelInactive(ctx: ChannelHandlerContext): Unit = {
    println("ClientTcpHandler: Server connection lost")
    networkThread.connectionClosed()
    super.channelInactive(ctx)
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
    System.err.println(s"ClientTcpHandler: Error - ${cause.getMessage}")
    ctx.close()
  }
}

/** Handles UDP packets from the server. */
class ClientUdpHandler(onPacket: Packet => Unit, networkThread: NetworkThread) extends SimpleChannelInboundHandler[DatagramPacket] {
  // Reuse read buffer — safe because handler runs on a single Netty event loop thread
  // and PacketSigner.verify() copies the payload before we return
  private val readBuffer = new Array[Byte](Constants.PACKET_SIZE)

  override def channelRead0(ctx: ChannelHandlerContext, msg: DatagramPacket): Unit = {
    val buf = msg.content()
    if (buf.readableBytes() < Constants.PACKET_SIZE) return

    val data = readBuffer
    buf.readBytes(data)

    try {
      val token = networkThread.sessionToken
      val payload = if (token != null) {
        val verified = networkThread.verify(data, token)
        if (verified == null) {
          System.err.println("ClientUdpHandler: HMAC verification failed, dropping packet")
          return
        }
        verified
      } else {
        // No session token yet — drop all UDP packets until authenticated
        return
      }
      val packet = PacketSerializer.deserialize(payload)
      Metrics.clientPacketsReceived.add(1L, Attrs.packet(packet.getType))
      onPacket(packet)
    } catch {
      case e: IllegalArgumentException =>
        System.err.println(s"ClientUdpHandler: Invalid packet - ${e.getMessage}")
        Metrics.clientErrors.add(1L, io.opentelemetry.api.common.Attributes.of(Attrs.Kind, "deserialize_udp"))
    }
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
    System.err.println(s"ClientUdpHandler: Error - ${cause.getMessage}")
  }
}
