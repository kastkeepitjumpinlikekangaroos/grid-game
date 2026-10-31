package com.gridgame.client.net

import com.gridgame.common.Constants
import com.gridgame.common.protocol._
import org.junit.After
import org.junit.Assert._
import org.junit.Test

import java.io.{DataInputStream, OutputStream}
import java.net.{InetAddress, ServerSocket}
import java.nio.file.{Files, Paths}
import java.security.KeyStore
import java.util.UUID
import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}
import javax.net.ssl.{KeyManagerFactory, SSLContext, SSLServerSocket}
import scala.jdk.CollectionConverters._

/**
 * A problem report's own connection to the server (UploadConnection), against a TLS server of the test's
 * own that speaks the upload's side: the ticket comes first, the report's bytes only once the server has
 * said go, and the server's word on them is what comes back. A server that isn't the one pinned for its
 * host and port gets nothing at all.
 */
class UploadConnectionTest {
  private val dir = Files.createTempDirectory("gridgame-upload")
  private val sockets = scala.collection.mutable.Buffer[ServerSocket]()
  private val reportId = UUID.fromString("9a8b7c6d-5e4f-3a2b-1c0d-efdecdbcab9a")
  private val ticket = Array.tabulate[Byte](ProblemReportPacket.TicketSize)(i => (i * 17 + 3).toByte)

  @After def cleanUp(): Unit = {
    sockets.foreach(_.close())
    Files.walk(dir).iterator.asScala.toSeq.reverse.foreach(p => Files.deleteIfExists(p))
  }

  /** A server identity, made by keytool: its TLS context and fingerprint. */
  private def identity(name: String): (SSLContext, String) = {
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
    val ctx = SSLContext.getInstance("TLSv1.3")
    ctx.init(kmf.getKeyManagers, null, null)
    (ctx, ServerIdentity.fingerprint(ks.getCertificate("server").getPublicKey))
  }

  private def frame(out: OutputStream, p: Packet): Unit = {
    out.write(Constants.PACKET_SIZE >>> 8); out.write(Constants.PACKET_SIZE & 0xFF)
    out.write(p.serialize()); out.write(new Array[Byte](Constants.HMAC_SIZE)); out.flush()
  }

  /** What the test's server heard: the token the connection logged in with, and the bytes after the go-ahead. */
  private final class Heard {
    val token = new AtomicReference[Array[Byte]]()
    val report = new AtomicReference[Array[Byte]]()
    val readAfterHandshake = new AtomicInteger()
  }

  /** A server on loopback answering one upload: `goAhead` to the ticket, then (if it said go) `verdict` to
    * the report's `size` bytes. */
  private def server(ctx: SSLContext, size: Int, goAhead: Packet, verdict: Packet): (Int, Heard) = {
    val socket = ctx.getServerSocketFactory.createServerSocket(0, 1, InetAddress.getLoopbackAddress).asInstanceOf[SSLServerSocket]
    socket.setEnabledProtocols(Array("TLSv1.3"))
    sockets += socket
    val heard = new Heard
    val t = new Thread(() => {
      try {
        val s = socket.accept()
        s.setSoTimeout(5000)
        try {
          val in = new DataInputStream(s.getInputStream)
          val out = s.getOutputStream
          assertEquals(Constants.PACKET_SIZE, in.readUnsignedShort())
          val f = new Array[Byte](Constants.PACKET_SIZE)
          in.readFully(f)
          heard.readAfterHandshake.addAndGet(2 + f.length)
          val login = PacketSerializer.deserialize(f.take(Constants.PACKET_PAYLOAD_SIZE)).asInstanceOf[AuthRequestPacket]
          assertEquals(AuthAction.UPLOAD, login.getAction)
          heard.token.set(login.getRefreshToken)
          frame(out, goAhead)
          if (goAhead.asInstanceOf[ProblemReportPacket].getAction == ProblemReportAction.READY) {
            val bytes = new Array[Byte](size)
            in.readFully(bytes)
            heard.report.set(bytes)
            frame(out, verdict)
          }
        } finally s.close()
      } catch { case _: Exception => }
    }, "TestUploadServer")
    t.setDaemon(true)
    t.start()
    (socket.getLocalPort, heard)
  }

  private def report(size: Int): java.nio.file.Path = {
    val f = dir.resolve(s"report-$size.zip")
    Files.write(f, Array.tabulate[Byte](size)(i => (i * 31 + 7).toByte))
  }

  private val me = new UUID(0L, 0L)
  private def ready = ProblemReportPacket.ready(1, me, reportId, null)

  @Test def aReportGoesDownItsOwnConnectionWithItsTicket(): Unit = {
    val (ctx, fingerprint) = identity("server")
    val size = 300000 // a few of its chunks
    val (port, heard) = server(ctx, size, ready, ProblemReportPacket.accepted(2, me, reportId, "GG-TEST0001"))
    val known = KnownServers.inMemory()
    known.trust("127.0.0.1", port, fingerprint)
    val file = report(size)
    val progress = new java.util.concurrent.CopyOnWriteArrayList[java.lang.Long]()
    val outcome = UploadConnection.send("127.0.0.1", port, known, reportId, ticket, file, n => progress.add(n))
    assertEquals(UploadConnection.Accepted("GG-TEST0001"), outcome)
    assertArrayEquals("logged in with its ticket and its report", ProblemReportPacket.uploadToken(ticket, reportId), heard.token.get)
    assertArrayEquals("the report, byte for byte", Files.readAllBytes(file), heard.report.get)
    assertEquals("told as it went, to the last byte", size.toLong, progress.asScala.last.longValue)
    assertTrue(progress.size > 1)
  }

  @Test def aTicketTheServerWontTakeSendsNothingMore(): Unit = {
    val (ctx, fingerprint) = identity("server")
    val (port, heard) = server(ctx, 1000, ProblemReportPacket.refused(1, me, reportId, ProblemReportRefusal.BAD_TICKET, 0), null)
    val known = KnownServers.inMemory()
    known.trust("127.0.0.1", port, fingerprint)
    assertEquals(UploadConnection.Refused(ProblemReportRefusal.BAD_TICKET, 0),
      UploadConnection.send("127.0.0.1", port, known, reportId, ticket, report(1000)))
    assertNull("none of the report was sent", heard.report.get)
  }

  @Test def bytesTheServerFoundWrongAreAFailure(): Unit = {
    val (ctx, fingerprint) = identity("server")
    val (port, _) = server(ctx, 1000, ready, ProblemReportPacket.failed(2, me, reportId, ProblemReportRefusal.CORRUPT))
    val known = KnownServers.inMemory()
    known.trust("127.0.0.1", port, fingerprint)
    assertEquals(UploadConnection.Failed(ProblemReportRefusal.CORRUPT),
      UploadConnection.send("127.0.0.1", port, known, reportId, ticket, report(1000)))
  }

  @Test def aServerThatIsntTheOneMetThereGetsNothing(): Unit = {
    val (ctx, _) = identity("impostor")
    val (_, pinned) = identity("server")
    val (port, heard) = server(ctx, 1000, ready, ProblemReportPacket.accepted(2, me, reportId, "GG-STOLEN01"))
    val known = KnownServers.inMemory()
    known.trust("127.0.0.1", port, pinned)
    UploadConnection.send("127.0.0.1", port, known, reportId, ticket, report(1000)) match {
      case UploadConnection.Broken(message) => assertTrue(message, message.contains("isn't the one met there before"))
      case other => fail(s"sent to another server: $other")
    }
    Thread.sleep(200)
    assertEquals("not the ticket, not a byte", 0, heard.readAfterHandshake.get)
    assertNull(heard.token.get)
  }

  @Test def noServerThereIsBroken(): Unit = {
    val free = new ServerSocket(0)
    val port = free.getLocalPort
    free.close()
    UploadConnection.send("127.0.0.1", port, KnownServers.inMemory(), reportId, ticket, report(10)) match {
      case UploadConnection.Broken(_) =>
      case other => fail(s"$other")
    }
  }
}
