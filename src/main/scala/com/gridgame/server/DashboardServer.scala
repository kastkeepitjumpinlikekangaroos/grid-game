package com.gridgame.server

import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.Unpooled
import io.netty.channel._
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.http._

import java.nio.charset.StandardCharsets
import java.net.URLDecoder

class DashboardServer(port: Int, metrics: ServerMetrics, eventLog: ServerEventLog) {
  private var bossGroup: NioEventLoopGroup = _
  private var workerGroup: NioEventLoopGroup = _
  private var channel: Channel = _

  def start(): Unit = {
    bossGroup = new NioEventLoopGroup(1)
    workerGroup = new NioEventLoopGroup(2)

    val bootstrap = new ServerBootstrap()
    val m = metrics
    val el = eventLog

    bootstrap.group(bossGroup, workerGroup)
      .channel(classOf[NioServerSocketChannel])
      .childHandler(new ChannelInitializer[SocketChannel] {
        override def initChannel(ch: SocketChannel): Unit = {
          ch.pipeline()
            .addLast(new HttpServerCodec())
            .addLast(new HttpObjectAggregator(65536))
            .addLast(new DashboardHttpHandler(m, el))
        }
      })

    channel = bootstrap.bind(port).sync().channel()
    println(s"Dashboard server started at http://localhost:$port")
  }

  def stop(): Unit = {
    if (channel != null) channel.close()
    if (bossGroup != null) bossGroup.shutdownGracefully()
    if (workerGroup != null) workerGroup.shutdownGracefully()
  }
}

class DashboardHttpHandler(metrics: ServerMetrics, eventLog: ServerEventLog)
  extends SimpleChannelInboundHandler[FullHttpRequest] {

  override def channelRead0(ctx: ChannelHandlerContext, req: FullHttpRequest): Unit = {
    val uri = req.uri()
    val path = if (uri.contains("?")) uri.substring(0, uri.indexOf("?")) else uri

    val (content, contentType, status) = path match {
      case "/" =>
        (DashboardPage.html, "text/html; charset=UTF-8", HttpResponseStatus.OK)

      case "/api/metrics" =>
        (metrics.toJson, "application/json", HttpResponseStatus.OK)

      case "/api/events" =>
        val params = parseQueryParams(uri)
        val events = eventLog.query(
          category = params.get("category").filter(_.nonEmpty),
          level = params.get("level").filter(_.nonEmpty),
          player = params.get("player").filter(_.nonEmpty),
          search = params.get("search").filter(_.nonEmpty),
          since = params.get("since").flatMap(s => try { Some(s.toLong) } catch { case _: Exception => None }),
          limit = params.get("limit").flatMap(s => try { Some(s.toInt) } catch { case _: Exception => None }).getOrElse(200)
        )
        (eventLog.toJson(events), "application/json", HttpResponseStatus.OK)

      case _ =>
        ("{\"error\":\"not found\"}", "application/json", HttpResponseStatus.NOT_FOUND)
    }

    val buf = Unpooled.copiedBuffer(content, StandardCharsets.UTF_8)
    val response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, buf)
    response.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType)
    response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, buf.readableBytes())
    response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, "*")
    response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE)
    ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE)
  }

  private def parseQueryParams(uri: String): Map[String, String] = {
    if (!uri.contains("?")) return Map.empty
    val query = uri.substring(uri.indexOf("?") + 1)
    query.split("&").flatMap { param =>
      val parts = param.split("=", 2)
      if (parts.length == 2) {
        try {
          Some(URLDecoder.decode(parts(0), "UTF-8") -> URLDecoder.decode(parts(1), "UTF-8"))
        } catch {
          case _: Exception => None
        }
      } else None
    }.toMap
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
    ctx.close()
  }
}
