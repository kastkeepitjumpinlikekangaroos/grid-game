package com.gridgame.client

import com.gridgame.client.net.{KnownServers, NetworkThread, ServerIdentityChangedException}
import com.gridgame.common.protocol.{AuthAction, AuthRequestPacket, ServerIdentity}
import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.ByteBuf
import io.netty.channel.{Channel, ChannelHandlerContext, ChannelInboundHandlerAdapter, ChannelInitializer}
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.ssl.{SslContext, SslContextBuilder}
import org.junit.After
import org.junit.Assert._
import org.junit.Test

import java.net.InetSocketAddress
import java.nio.file.{Files, Paths}
import java.security.KeyStore
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger}
import javax.net.ssl.KeyManagerFactory
import scala.jdk.CollectionConverters._

/**
 * The client holding a server to the identity it met at its host and port before
 * (design/networking.md, layer 1): real TLS handshakes over loopback, between the client's own
 * connection and a server of the test's own, each server's key made by the JDK's keytool. Above all:
 * a server that shows another key gets nothing of the login, however eager the client is to send it.
 */
class ServerPinningTest {
  private val dir = Files.createTempDirectory("gridgame-pinning")
  private val servers = scala.collection.mutable.Buffer[TlsServer]()
  private val connections = scala.collection.mutable.Buffer[NetworkThread]()

  @After def cleanUp(): Unit = {
    connections.foreach(_.shutdown())
    servers.foreach(_.stop())
    Files.walk(dir).iterator.asScala.toSeq.reverse.foreach(p => Files.deleteIfExists(p))
  }

  /** A server identity: its TLS context, and its fingerprint. */
  private def identity(name: String): (SslContext, String) = {
    val file = dir.resolve(s"$name.p12")
    val keytool = Paths.get(System.getProperty("java.home"), "bin", "keytool").toString
    val p = new ProcessBuilder(keytool, "-genkeypair", "-alias", "server", "-keyalg", "EC", "-groupname", "secp256r1",
      "-validity", "2", "-storetype", "PKCS12", "-keystore", file.toString, "-storepass", "password",
      "-dname", s"CN=$name").redirectErrorStream(true).start()
    p.getInputStream.readAllBytes()
    assertEquals("keytool", 0, p.waitFor())
    val ks = KeyStore.getInstance("PKCS12")
    val in = Files.newInputStream(file)
    try ks.load(in, "password".toCharArray) finally in.close()
    val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
    kmf.init(ks, "password".toCharArray)
    (SslContextBuilder.forServer(kmf).protocols("TLSv1.3").build(), ServerIdentity.fingerprint(ks.getCertificate("server").getPublicKey))
  }

  /** A server on loopback, counting the bytes the client sent it once the handshake was through. */
  private class TlsServer(ctx: SslContext) {
    val received = new AtomicInteger()
    private val group = new NioEventLoopGroup(1)
    private val channel: Channel = new ServerBootstrap().group(group).channel(classOf[NioServerSocketChannel])
      .childHandler(new ChannelInitializer[SocketChannel] {
        override def initChannel(ch: SocketChannel): Unit =
          ch.pipeline().addLast(ctx.newHandler(ch.alloc())).addLast(new ChannelInboundHandlerAdapter {
            override def channelRead(c: ChannelHandlerContext, msg: AnyRef): Unit = msg match {
              case b: ByteBuf => received.addAndGet(b.readableBytes()); b.release()
              case _ =>
            }
            override def exceptionCaught(c: ChannelHandlerContext, cause: Throwable): Unit = c.close()
          })
      }).bind("127.0.0.1", 0).sync().channel()
    servers += this
    def port: Int = channel.localAddress().asInstanceOf[InetSocketAddress].getPort
    def stop(): Unit = { channel.close().sync(); group.shutdownGracefully() }
  }

  /** The client connecting to the server on `port`, held to `known`. */
  private def connect(port: Int, known: KnownServers, dropped: AtomicBoolean = new AtomicBoolean()): NetworkThread = {
    val net = new NetworkThread("127.0.0.1", port, _ => (), () => (), knownServers = known)
    net.disconnectCallback = () => dropped.set(true)
    connections += net
    net.start()
    net
  }

  private def waitUntil(what: String)(cond: => Boolean): Unit = {
    val end = System.currentTimeMillis() + 5000
    while (!cond) {
      if (System.currentTimeMillis() > end) throw new AssertionError(s"timed out waiting for $what")
      Thread.sleep(10)
    }
  }

  private def login = new AuthRequestPacket(0, AuthAction.LOGIN, "victim", "hunter22")

  @Test def aServerMetForTheFirstTimeIsPinned(): Unit = {
    val (ctx, fingerprint) = identity("first")
    val server = new TlsServer(ctx)
    val known = KnownServers.inMemory()
    val net = connect(server.port, known)
    net.waitForReady()
    assertEquals(Some(fingerprint), known.identityOf("127.0.0.1", server.port))
    // And it is a working connection: what is sent arrives
    net.send(login)
    waitUntil("the login to arrive")(server.received.get > 0)
  }

  @Test def theSameServerIsTakenAgain(): Unit = {
    val (ctx, fingerprint) = identity("same")
    val server = new TlsServer(ctx)
    val known = KnownServers.inMemory()
    known.trust("127.0.0.1", server.port, fingerprint)
    connect(server.port, known).waitForReady()
  }

  @Test def aServerShowingAnotherKeyIsRefusedBeforeAnythingIsSent(): Unit = {
    val (_, before) = identity("before")
    val (ctx, impostor) = identity("impostor")
    val server = new TlsServer(ctx)
    val known = KnownServers.inMemory()
    known.trust("127.0.0.1", server.port, before)
    val dropped = new AtomicBoolean()
    val net = connect(server.port, known, dropped)
    // A client as eager as can be: sending its password from the moment the socket is up (each send
    // goes to the open socket: none is turned away as "not running" or "not active", the log shows)
    val eager = new Thread(() =>
      try while (!Thread.currentThread().isInterrupted) { net.send(login); Thread.sleep(1) }
      catch { case _: InterruptedException => })
    eager.setDaemon(true)
    eager.start()
    try {
      net.waitForReady()
      fail("the impostor was taken")
    } catch {
      case changed: ServerIdentityChangedException =>
        assertEquals((before, impostor), (changed.known, changed.seen))
        assertEquals(("127.0.0.1", server.port), (changed.host, changed.port))
    } finally {
      eager.interrupt()
      eager.join(1000)
    }
    Thread.sleep(200)
    assertEquals("not a byte of the login reached it", 0, server.received.get)
    assertEquals("the pin stays", Some(before), known.identityOf("127.0.0.1", server.port))
    assertFalse("a connection that was never up is not a connection lost", dropped.get)
  }

  @Test def onceTheNewIdentityIsTrustedItIsTaken(): Unit = {
    val (_, before) = identity("old")
    val (ctx, now) = identity("new")
    val server = new TlsServer(ctx)
    val known = KnownServers.inMemory()
    known.trust("127.0.0.1", server.port, before)
    val refused = connect(server.port, known)
    assertThrows(classOf[ServerIdentityChangedException], () => refused.waitForReady())
    known.trust("127.0.0.1", server.port, now)
    connect(server.port, known).waitForReady()
  }

  @Test def aPinIsForOneHostAndPort(): Unit = {
    val (ctxA, a) = identity("a")
    val (ctxB, b) = identity("b")
    val serverA = new TlsServer(ctxA)
    val serverB = new TlsServer(ctxB)
    val known = KnownServers.inMemory()
    known.trust("127.0.0.1", serverA.port, a)
    connect(serverB.port, known).waitForReady()
    assertEquals("another port is another server, met for the first time", Some(b), known.identityOf("127.0.0.1", serverB.port))
    assertEquals(Some(a), known.identityOf("127.0.0.1", serverA.port))
  }
}
