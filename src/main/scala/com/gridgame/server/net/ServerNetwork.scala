package com.gridgame.server.net

import com.gridgame.common.Constants
import io.netty.bootstrap.Bootstrap
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel._
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioDatagramChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.LengthFieldBasedFrameDecoder
import io.netty.handler.codec.LengthFieldPrepender
import io.netty.handler.ssl.SslContext

/**
 * The server's sockets: TLS over TCP for everything that must arrive, and one UDP socket for what
 * can be lost, both on `port`. Each TCP connection gets a pipeline of TLS, length framing and a
 * [[TcpHandler]]; the UDP socket a [[UdpHandler]], and it is what [[Outbox]] sends datagrams from.
 */
final class ServerNetwork(port: Int, sslCtx: SslContext, newTcpHandler: () => TcpHandler,
                          newUdpHandler: () => UdpHandler, outbox: Outbox) {
  private var bossGroup: NioEventLoopGroup = _
  private var workerGroup: NioEventLoopGroup = _
  private var tcpServerChannel: Channel = _
  @volatile private var udpChannel: Channel = _

  /** Listen on TCP and UDP. */
  def bind(): Unit = {
    bossGroup = new NioEventLoopGroup(1)
    workerGroup = new NioEventLoopGroup()

    // TCP Server
    val tcpBootstrap = new ServerBootstrap()
    tcpBootstrap.group(bossGroup, workerGroup)
      .channel(classOf[NioServerSocketChannel])
      .childHandler(new ChannelInitializer[SocketChannel] {
        override def initChannel(ch: SocketChannel): Unit = {
          ch.pipeline()
            .addLast(sslCtx.newHandler(ch.alloc()))
            .addLast(new LengthFieldBasedFrameDecoder(Constants.PACKET_SIZE + 2, 0, 2, 0, 2))
            .addLast(new LengthFieldPrepender(2))
            .addLast(newTcpHandler())
        }
      })
      .option[java.lang.Integer](ChannelOption.SO_BACKLOG, 128)
      .childOption[java.lang.Boolean](ChannelOption.SO_KEEPALIVE, true)
      .childOption[java.lang.Boolean](ChannelOption.TCP_NODELAY, true)
      .childOption(ChannelOption.WRITE_BUFFER_WATER_MARK, new WriteBufferWaterMark(64 * 1024, 256 * 1024))

    tcpServerChannel = tcpBootstrap.bind(port).sync().channel()
    println(s"TCP server listening on port $port")

    // UDP Server
    val udpBootstrap = new Bootstrap()
    udpBootstrap.group(workerGroup)
      .channel(classOf[NioDatagramChannel])
      .option[java.lang.Integer](ChannelOption.SO_SNDBUF, 1024 * 1024)
      .option[java.lang.Integer](ChannelOption.SO_RCVBUF, 1024 * 1024)
      .handler(newUdpHandler())

    udpChannel = udpBootstrap.bind(port).sync().channel()
    outbox.attachUdpChannel(udpChannel)
    println(s"UDP server listening on port $port")
  }

  /** Block until the TCP socket closes. */
  def awaitClose(): Unit = tcpServerChannel.closeFuture().sync()

  def stop(): Unit = {
    if (tcpServerChannel != null) tcpServerChannel.close().sync()
    if (udpChannel != null) udpChannel.close().sync()
    if (bossGroup != null) bossGroup.shutdownGracefully()
    if (workerGroup != null) workerGroup.shutdownGracefully()
  }
}
