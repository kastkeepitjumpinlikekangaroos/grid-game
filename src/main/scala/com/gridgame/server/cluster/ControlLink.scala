package com.gridgame.server.cluster

import com.google.gson.{JsonObject, JsonParser}
import io.netty.buffer.{ByteBuf, Unpooled}
import io.netty.channel._
import io.netty.handler.codec.{LengthFieldBasedFrameDecoder, LengthFieldPrepender}
import io.netty.handler.ssl.SslContext
import io.netty.handler.timeout.{IdleStateEvent, IdleStateHandler}

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.{MessageDigest, SecureRandom}
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * One end of the link between the server players log in to (the central server) and a match server
 * (design/networking.md, *Match servers*): JSON messages, a frame each, over TLS.
 *
 * Who is at the other end is proved by the cluster's secret, which both were given and which never
 * crosses the link: each side sends a nonce, each proves it holds the secret with an HMAC over both,
 * and from then on every frame carries an HMAC under a key made of the secret and the two nonces,
 * numbered in each direction, so nothing can be added, dropped, replayed or changed on the way. TLS
 * keeps what is said private (tickets cross it). The handshake:
 *
 * {{{
 * match server -> central   {"type":"hello","nonce":Nm}
 * central -> match server   {"type":"challenge","nonce":Nc,"proof":HMAC(secret,"central"|Nm|Nc)}
 * match server -> central   {"type":"auth","proof":HMAC(secret,"match"|Nm|Nc)}
 * }}}
 *
 * Then both sides are open, and each says something at least every [[ControlLink.PingMs]]; a link
 * that hears nothing for [[ControlLink.DeadMs]] is closed.
 */
final class ControlLink private[cluster] (val channel: Channel, key: Array[Byte], central: Boolean) {
  private val sendDir: Byte = if (central) 'C' else 'M'
  private val recvDir: Byte = if (central) 'M' else 'C'
  private var sendSeq = 0L
  private var recvSeq = 0L

  /** Send a message. */
  def send(msg: JsonObject): Unit = {
    val bytes = msg.toString.getBytes(StandardCharsets.UTF_8)
    synchronized {
      val mac = ControlLink.mac(key, sendDir, sendSeq, bytes)
      sendSeq += 1
      val buf = Unpooled.buffer(bytes.length + mac.length)
      buf.writeBytes(bytes).writeBytes(mac)
      // Written under the lock, so frames leave in the order they were numbered
      channel.writeAndFlush(buf)
    }
  }

  def close(): Unit = channel.close()

  def isOpen: Boolean = channel.isActive

  /** A frame from the other end, checked: its message, or null if it isn't the next one it signed. */
  private[cluster] def open(frame: Array[Byte]): JsonObject = {
    if (frame.length < ControlLink.MacSize) return null
    val body = java.util.Arrays.copyOf(frame, frame.length - ControlLink.MacSize)
    val mac = java.util.Arrays.copyOfRange(frame, frame.length - ControlLink.MacSize, frame.length)
    val ok = synchronized {
      val good = MessageDigest.isEqual(mac, ControlLink.mac(key, recvDir, recvSeq, body))
      if (good) recvSeq += 1
      good
    }
    if (!ok) null else ControlLink.parse(body)
  }

  override def toString: String = s"${channel.remoteAddress()}"
}

/** What one end of a link does with what the other says. */
trait ControlListener {
  def onMessage(link: ControlLink, msg: JsonObject): Unit
  def onClosed(link: ControlLink): Unit
}

object ControlLink {
  val PingMs = 2000L
  val DeadMs = 10000L
  /** A match's outcome carries its journal: 20,000 lines at most. */
  val MaxFrame: Int = 32 * 1024 * 1024
  private val MacSize = 32
  private val NonceSize = 32
  private val random = new SecureRandom()

  def message(kind: String): JsonObject = {
    val m = new JsonObject
    m.addProperty("type", kind)
    m
  }

  def kindOf(msg: JsonObject): String = if (msg.has("type")) msg.get("type").getAsString else ""

  /** The cluster's secret as the environment gives it (GRIDGAME_CLUSTER_SECRET), or null. */
  def secretFromEnv(): Array[Byte] = {
    val s = System.getenv("GRIDGAME_CLUSTER_SECRET")
    if (s == null || s.isEmpty) null else s.getBytes(StandardCharsets.UTF_8)
  }

  private[cluster] def parse(bytes: Array[Byte]): JsonObject =
    try {
      val e = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8))
      if (e.isJsonObject) e.getAsJsonObject else null
    } catch { case _: Exception => null }

  private def hmac(key: Array[Byte], parts: Array[Byte]*): Array[Byte] = {
    val m = Mac.getInstance("HmacSHA256")
    m.init(new SecretKeySpec(key, "HmacSHA256"))
    parts.foreach(m.update)
    m.doFinal()
  }

  private def mac(key: Array[Byte], dir: Byte, seq: Long, body: Array[Byte]): Array[Byte] =
    hmac(key, Array(dir), ByteBuffer.allocate(8).putLong(seq).array(), body)

  private def tag(s: String): Array[Byte] = s.getBytes(StandardCharsets.UTF_8)
  private def b64(b: Array[Byte]): String = Base64.getEncoder.encodeToString(b)
  private def unb64(s: String): Array[Byte] = try Base64.getDecoder.decode(s) catch { case _: Exception => null }
  private def str(m: JsonObject, k: String): String =
    if (m != null && m.has(k) && m.get(k).isJsonPrimitive) m.get(k).getAsString else null

  /**
   * A channel's pipeline: TLS, then nothing heard for DeadMs closes it, the frames (a 4-byte length),
   * and the handshake, which hands the open link to `open` and its messages to the listener `open`
   * returns.
   */
  def pipeline(ch: Channel, tls: io.netty.handler.ssl.SslHandler, central: Boolean, secret: Array[Byte],
               open: ControlLink => ControlListener): Unit =
    ch.pipeline()
      .addLast(tls)
      .addLast(new IdleStateHandler(DeadMs, 0, 0, TimeUnit.MILLISECONDS))
      .addLast(new LengthFieldBasedFrameDecoder(MaxFrame, 0, 4, 0, 4))
      .addLast(new LengthFieldPrepender(4))
      .addLast(new Handshake(central, secret, open))

  /** A server's TLS handler for a channel, or a client's that takes the server's key if it is the
    * one `pinned` says (any key if none: the secret is what proves who is there). */
  def serverTls(ctx: SslContext, ch: Channel): io.netty.handler.ssl.SslHandler = ctx.newHandler(ch.alloc())

  def clientTls(ch: Channel, pinned: String): io.netty.handler.ssl.SslHandler = {
    val trust: javax.net.ssl.TrustManager = new javax.net.ssl.X509TrustManager {
      def checkClientTrusted(chain: Array[java.security.cert.X509Certificate], authType: String): Unit = ()
      def checkServerTrusted(chain: Array[java.security.cert.X509Certificate], authType: String): Unit =
        if (pinned != null) {
          val shown = com.gridgame.common.protocol.ServerIdentity.fingerprint(chain(0).getPublicKey)
          if (shown != pinned) throw new java.security.cert.CertificateException(
            s"the central server showed $shown, not the $pinned it was pinned to")
        }
      def getAcceptedIssuers: Array[java.security.cert.X509Certificate] = Array.empty
    }
    io.netty.handler.ssl.SslContextBuilder.forClient()
      .protocols("TLSv1.3")
      .trustManager(trust)
      .build()
      .newHandler(ch.alloc())
  }

  /** The handshake, then the open link's frames, checked, to its listener. */
  private final class Handshake(central: Boolean, secret: Array[Byte], open: ControlLink => ControlListener)
      extends SimpleChannelInboundHandler[ByteBuf] {
    private var myNonce: Array[Byte] = _
    private var link: ControlLink = _
    private var listener: ControlListener = _

    private def sendPlain(ctx: ChannelHandlerContext, m: JsonObject): Unit = {
      val bytes = m.toString.getBytes(StandardCharsets.UTF_8)
      ctx.writeAndFlush(Unpooled.wrappedBuffer(bytes))
    }

    private def fail(ctx: ChannelHandlerContext, why: String): Unit = {
      System.err.println(s"Control link ${ctx.channel().remoteAddress()}: $why; closed")
      ctx.close()
    }

    private var pings: java.util.concurrent.ScheduledFuture[_] = _

    private def becomeOpen(ctx: ChannelHandlerContext, nm: Array[Byte], nc: Array[Byte]): Unit = {
      link = new ControlLink(ctx.channel(), hmac(secret, tag("key"), nm, nc), central)
      listener = open(link)
      val l = link
      pings = ctx.executor().scheduleAtFixedRate(new Runnable {
        def run(): Unit = if (l.isOpen) l.send(message("ping"))
      }, PingMs, PingMs, TimeUnit.MILLISECONDS)
    }

    override def channelActive(ctx: ChannelHandlerContext): Unit = {
      if (!central) {
        myNonce = new Array[Byte](NonceSize)
        random.nextBytes(myNonce)
        val hello = message("hello")
        hello.addProperty("nonce", b64(myNonce))
        sendPlain(ctx, hello)
      }
      super.channelActive(ctx)
    }

    override def channelRead0(ctx: ChannelHandlerContext, buf: ByteBuf): Unit = {
      val frame = new Array[Byte](buf.readableBytes())
      buf.readBytes(frame)
      if (listener != null) {
        val msg = link.open(frame)
        if (msg == null) fail(ctx, "a frame that wasn't the next one the other end signed")
        else if (kindOf(msg) != "ping") listener.onMessage(link, msg)
        return
      }
      val m = parse(frame)
      (central, kindOf(m)) match {
        case (true, "hello") if myNonce == null =>
          val nm = unb64(str(m, "nonce"))
          if (nm == null || nm.length != NonceSize) return fail(ctx, "a hello without a nonce")
          myNonce = new Array[Byte](NonceSize)
          random.nextBytes(myNonce)
          theirNonce = nm
          val c = message("challenge")
          c.addProperty("nonce", b64(myNonce))
          c.addProperty("proof", b64(hmac(secret, tag("central"), nm, myNonce)))
          sendPlain(ctx, c)
        case (true, "auth") if theirNonce != null =>
          val proof = unb64(str(m, "proof"))
          if (proof == null || !MessageDigest.isEqual(proof, hmac(secret, tag("match"), theirNonce, myNonce)))
            return fail(ctx, "a match server that doesn't hold the cluster's secret")
          becomeOpen(ctx, theirNonce, myNonce)
        case (false, "challenge") =>
          val nc = unb64(str(m, "nonce"))
          val proof = unb64(str(m, "proof"))
          if (nc == null || nc.length != NonceSize || proof == null ||
              !MessageDigest.isEqual(proof, hmac(secret, tag("central"), myNonce, nc)))
            return fail(ctx, "a central server that doesn't hold the cluster's secret")
          val a = message("auth")
          a.addProperty("proof", b64(hmac(secret, tag("match"), myNonce, nc)))
          sendPlain(ctx, a)
          becomeOpen(ctx, myNonce, nc)
        case _ => fail(ctx, s"'${kindOf(m)}' before the handshake was through")
      }
    }

    private var theirNonce: Array[Byte] = _

    override def userEventTriggered(ctx: ChannelHandlerContext, evt: AnyRef): Unit = evt match {
      case _: IdleStateEvent => fail(ctx, s"nothing heard for ${DeadMs}ms")
      case _ => super.userEventTriggered(ctx, evt)
    }

    override def channelInactive(ctx: ChannelHandlerContext): Unit = {
      if (pings != null) pings.cancel(false)
      if (listener != null) listener.onClosed(link)
      super.channelInactive(ctx)
    }

    override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit =
      fail(ctx, s"${cause.getClass.getSimpleName}: ${cause.getMessage}")
  }
}
