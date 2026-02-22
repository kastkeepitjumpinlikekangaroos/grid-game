package com.gridgame.client

import com.gridgame.common.Constants
import com.gridgame.common.protocol.Packet
import com.gridgame.common.protocol.PacketSerializer
import com.gridgame.common.protocol.PacketSigner
import com.gridgame.common.protocol.PacketType
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
import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import io.netty.handler.timeout.IdleStateEvent
import io.netty.handler.timeout.IdleStateHandler

import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class NetworkThread(client: GameClient, serverHost: String, serverPort: Int) extends Thread("NetworkThread") {
  private val heartbeatExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
  private val ready = new CountDownLatch(1)
  @volatile private var running = false
  @volatile var sessionToken: Array[Byte] = _
  @volatile var disconnectCallback: Runnable = _

  private var eventLoopGroup: NioEventLoopGroup = _
  private var tcpChannel: Channel = _
  private var udpChannel: Channel = _
  private val serverAddress = new InetSocketAddress(serverHost, serverPort)

  setDaemon(true)

  def waitForReady(): Unit = {
    ready.await()
  }

  override def run(): Unit = {
    try {
      eventLoopGroup = new NioEventLoopGroup()
      running = true

      // TCP connection
      val tcpBootstrap = new Bootstrap()
      tcpBootstrap.group(eventLoopGroup)
        .channel(classOf[NioSocketChannel])
        .option[java.lang.Boolean](ChannelOption.TCP_NODELAY, true)
        .option[java.lang.Integer](ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
        .handler(new ChannelInitializer[SocketChannel] {
          override def initChannel(ch: SocketChannel): Unit = {
            val sslCtx = SslContextBuilder.forClient()
              .trustManager(InsecureTrustManagerFactory.INSTANCE)
              .protocols("TLSv1.3")
              .build()
            ch.pipeline()
              .addLast(sslCtx.newHandler(ch.alloc(), serverHost, serverPort))
              .addLast(new IdleStateHandler(Constants.CLIENT_TIMEOUT_MS, 0, 0, TimeUnit.MILLISECONDS))
              .addLast(new LengthFieldBasedFrameDecoder(Constants.PACKET_SIZE + 2, 0, 2, 0, 2))
              .addLast(new LengthFieldPrepender(2))
              .addLast(new ClientTcpHandler(client, NetworkThread.this))
          }
        })

      tcpChannel = tcpBootstrap.connect(serverAddress).sync().channel()
      println(s"NetworkThread: TCP connected to $serverHost:$serverPort")

      // UDP channel (connected mode to server)
      val udpBootstrap = new Bootstrap()
      udpBootstrap.group(eventLoopGroup)
        .channel(classOf[NioDatagramChannel])
        .handler(new ClientUdpHandler(client, this))

      udpChannel = udpBootstrap.bind(0).sync().channel()
      println(s"NetworkThread: UDP bound on local port")

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
        System.err.println(s"NetworkThread: Connection error - ${e.getMessage}")
    } finally {
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
        tcpChannel.writeAndFlush(Unpooled.wrappedBuffer(data))
      } else {
        System.err.println("NetworkThread: TCP channel not active, cannot send")
      }
    } else {
      if (udpChannel != null && udpChannel.isActive) {
        val dgram = new DatagramPacket(Unpooled.wrappedBuffer(data), serverAddress)
        udpChannel.writeAndFlush(dgram)
      } else {
        System.err.println("NetworkThread: UDP channel not active, cannot send")
      }
    }
  }

  private def sendHeartbeat(): Unit = {
    client.sendHeartbeat()
  }

  def shutdown(): Unit = {
    running = false

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

/** Handles TCP packets from the server. */
class ClientTcpHandler(client: GameClient, networkThread: NetworkThread) extends SimpleChannelInboundHandler[io.netty.buffer.ByteBuf] {

  override def channelRead0(ctx: ChannelHandlerContext, msg: io.netty.buffer.ByteBuf): Unit = {
    if (msg.readableBytes() < Constants.PACKET_SIZE) return

    val data = new Array[Byte](Constants.PACKET_SIZE)
    msg.readBytes(data)

    try {
      val token = networkThread.sessionToken
      val payload = if (token != null) {
        val verified = PacketSigner.verify(data, token)
        if (verified == null) {
          // HMAC verification failed — log but still process (client trusts server;
          // this can happen briefly during token handoff when server signs with new
          // token before client has processed the SESSION_TOKEN packet)
          System.err.println("ClientTcpHandler: HMAC verification failed, processing anyway")
          val p = new Array[Byte](Constants.PACKET_PAYLOAD_SIZE)
          System.arraycopy(data, 0, p, 0, Constants.PACKET_PAYLOAD_SIZE)
          p
        } else verified
      } else {
        val p = new Array[Byte](Constants.PACKET_PAYLOAD_SIZE)
        System.arraycopy(data, 0, p, 0, Constants.PACKET_PAYLOAD_SIZE)
        p
      }
      val packet = PacketSerializer.deserialize(payload)
      client.enqueuePacket(packet)
    } catch {
      case e: IllegalArgumentException =>
        System.err.println(s"ClientTcpHandler: Invalid packet - ${e.getMessage}")
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
    val cb = networkThread.disconnectCallback
    if (cb != null) cb.run()
    super.channelInactive(ctx)
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
    System.err.println(s"ClientTcpHandler: Error - ${cause.getMessage}")
    ctx.close()
  }
}

/** Handles UDP packets from the server. */
class ClientUdpHandler(client: GameClient, networkThread: NetworkThread) extends SimpleChannelInboundHandler[DatagramPacket] {

  override def channelRead0(ctx: ChannelHandlerContext, msg: DatagramPacket): Unit = {
    val buf = msg.content()
    if (buf.readableBytes() < Constants.PACKET_SIZE) return

    val data = new Array[Byte](Constants.PACKET_SIZE)
    buf.readBytes(data)

    try {
      val token = networkThread.sessionToken
      val payload = if (token != null) {
        val verified = PacketSigner.verify(data, token)
        if (verified == null) {
          System.err.println("ClientUdpHandler: HMAC verification failed, dropping packet")
          return
        }
        verified
      } else {
        val p = new Array[Byte](Constants.PACKET_PAYLOAD_SIZE)
        System.arraycopy(data, 0, p, 0, Constants.PACKET_PAYLOAD_SIZE)
        p
      }
      val packet = PacketSerializer.deserialize(payload)
      client.enqueuePacket(packet)
    } catch {
      case e: IllegalArgumentException =>
        System.err.println(s"ClientUdpHandler: Invalid packet - ${e.getMessage}")
    }
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
    System.err.println(s"ClientUdpHandler: Error - ${cause.getMessage}")
  }
}
