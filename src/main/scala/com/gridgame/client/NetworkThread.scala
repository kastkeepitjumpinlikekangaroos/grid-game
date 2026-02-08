package com.gridgame.client

import com.gridgame.common.Constants
import com.gridgame.common.protocol.Packet
import com.gridgame.common.protocol.PacketSerializer
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

import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class NetworkThread(client: GameClient, serverHost: String, serverPort: Int) extends Thread("NetworkThread") {
  private val heartbeatExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
  private val ready = new CountDownLatch(1)
  @volatile private var running = false

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
        .handler(new ChannelInitializer[SocketChannel] {
          override def initChannel(ch: SocketChannel): Unit = {
            ch.pipeline()
              .addLast(new LengthFieldBasedFrameDecoder(66, 0, 2, 0, 2))
              .addLast(new LengthFieldPrepender(2))
              .addLast(new ClientTcpHandler(client))
          }
        })

      tcpChannel = tcpBootstrap.connect(serverAddress).sync().channel()
      println(s"NetworkThread: TCP connected to $serverHost:$serverPort")

      // UDP channel (connected mode to server)
      val udpBootstrap = new Bootstrap()
      udpBootstrap.group(eventLoopGroup)
        .channel(classOf[NioDatagramChannel])
        .handler(new ClientUdpHandler(client))

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

    val data = packet.serialize()

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
class ClientTcpHandler(client: GameClient) extends SimpleChannelInboundHandler[io.netty.buffer.ByteBuf] {

  override def channelRead0(ctx: ChannelHandlerContext, msg: io.netty.buffer.ByteBuf): Unit = {
    if (msg.readableBytes() < Constants.PACKET_SIZE) return

    val data = new Array[Byte](Constants.PACKET_SIZE)
    msg.readBytes(data)

    try {
      val packet = PacketSerializer.deserialize(data)
      client.enqueuePacket(packet)
    } catch {
      case e: IllegalArgumentException =>
        System.err.println(s"ClientTcpHandler: Invalid packet - ${e.getMessage}")
    }
  }

  override def channelInactive(ctx: ChannelHandlerContext): Unit = {
    println("ClientTcpHandler: Server connection lost")
    super.channelInactive(ctx)
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
    System.err.println(s"ClientTcpHandler: Error - ${cause.getMessage}")
    ctx.close()
  }
}

/** Handles UDP packets from the server. */
class ClientUdpHandler(client: GameClient) extends SimpleChannelInboundHandler[DatagramPacket] {

  override def channelRead0(ctx: ChannelHandlerContext, msg: DatagramPacket): Unit = {
    val buf = msg.content()
    if (buf.readableBytes() < Constants.PACKET_SIZE) return

    val data = new Array[Byte](Constants.PACKET_SIZE)
    buf.readBytes(data)

    try {
      val packet = PacketSerializer.deserialize(data)
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
