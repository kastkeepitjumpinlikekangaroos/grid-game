package com.gridgame.client.net

import com.gridgame.common.Constants
import com.gridgame.common.protocol.{AuthAction, AuthRequestPacket, PacketSerializer, ProblemReportAction, ProblemReportPacket}

import java.io.{BufferedInputStream, BufferedOutputStream, DataInputStream, IOException, OutputStream}
import java.net.{InetSocketAddress, Socket}
import java.nio.file.{Files, Path}
import java.security.SecureRandom
import java.util.UUID
import javax.net.ssl.{SSLContext, SSLSocket, TrustManager}

/**
 * A problem report's bytes on their way to the server, on a connection of their own (design/diagnostics.md,
 * *Sending a report*). It is TLS to the same server as the session, held to the identity met there
 * (KnownServers) as every connection is, so the report goes to no other. It logs in with the ticket the
 * session was given for this report (AUTH_REQUEST, AuthAction.UPLOAD), waits for the server's go-ahead
 * (READY), then streams the report's bytes, raw, and waits for the server's word: ACCEPTED with the
 * report's code, or FAILED. A connection of its own so a report of megabytes never stands in a match's
 * way on the session's.
 *
 * Blocking: run it on a thread of its own.
 */
object UploadConnection {
  sealed trait Outcome
  /** Kept on the server, under `code`. */
  final case class Accepted(code: String) extends Outcome
  /** Not taken: the ticket was no good, or the server said not now (ProblemReportRefusal). */
  final case class Refused(reason: Byte, retryAfterSeconds: Int) extends Outcome
  /** The server had the bytes and found them wrong, or couldn't keep them (ProblemReportRefusal). */
  final case class Failed(reason: Byte) extends Outcome
  /** It never got that far: no connection, the server not the one met before, the connection lost. */
  final case class Broken(message: String) extends Outcome

  val ConnectTimeoutMs = 5000
  /** How long to wait for a word from the server: the go-ahead, or its verdict once it has it all. */
  val ReadTimeoutMs = 30000
  private val ChunkBytes = 64 * 1024

  /**
   * Send `file` (the report `reportId`, which the session was given `ticket` for) to host:port.
   * `progress` is told how many bytes have gone, as they go; `cancelled` is asked between chunks.
   */
  def send(host: String, port: Int, knownServers: KnownServers, reportId: UUID, ticket: Array[Byte], file: Path,
           progress: Long => Unit = _ => (), cancelled: () => Boolean = () => false): Outcome = {
    val known = knownServers.identityOf(host, port)
    val trust = new PinnedTrustManager(host, port, known)
    var socket: SSLSocket = null
    try {
      val ctx = SSLContext.getInstance("TLSv1.3")
      ctx.init(null, Array[TrustManager](trust), new SecureRandom())
      val raw = new Socket()
      raw.connect(new InetSocketAddress(host, port), ConnectTimeoutMs)
      raw.setSoTimeout(ReadTimeoutMs)
      raw.setTcpNoDelay(true)
      socket = ctx.getSocketFactory.createSocket(raw, host, port, true).asInstanceOf[SSLSocket]
      socket.setEnabledProtocols(Array("TLSv1.3"))
      // The server proves who it is before anything is sent (PinnedTrustManager)
      socket.startHandshake()
      if (known.isEmpty && trust.seen != null) knownServers.trust(host, port, trust.seen)

      val out = new BufferedOutputStream(socket.getOutputStream, ChunkBytes)
      val in = new DataInputStream(new BufferedInputStream(socket.getInputStream))

      // Logged in with the ticket, as an AUTH_REQUEST is sent: unsigned, there being no session here
      writeFrame(out, new AuthRequestPacket(0, AuthAction.UPLOAD, ProblemReportPacket.uploadToken(ticket, reportId)).serialize())
      out.flush()

      readReply(in) match {
        case r if r.getAction == ProblemReportAction.READY => // go on
        case r if r.getAction == ProblemReportAction.ACCEPTED => return Accepted(r.getCode)
        case r if r.getAction == ProblemReportAction.REFUSED => return Refused(r.getReason, r.getRetryAfterSeconds)
        case r if r.getAction == ProblemReportAction.FAILED => return Failed(r.getReason)
        case r => return Broken(s"the server answered the ticket with action ${r.getAction}")
      }

      // The report itself, raw: the server counts its bytes to the size the session was told
      val fileIn = Files.newInputStream(file)
      try {
        val chunk = new Array[Byte](ChunkBytes)
        var sent = 0L
        var n = fileIn.read(chunk)
        while (n > 0) {
          if (cancelled()) return Broken("cancelled")
          out.write(chunk, 0, n)
          sent += n
          progress(sent)
          n = fileIn.read(chunk)
        }
      } finally fileIn.close()
      out.flush()

      val verdict = readReply(in)
      verdict.getAction match {
        case ProblemReportAction.ACCEPTED => Accepted(verdict.getCode)
        case ProblemReportAction.FAILED => Failed(verdict.getReason)
        case ProblemReportAction.REFUSED => Refused(verdict.getReason, verdict.getRetryAfterSeconds)
        case a => Broken(s"the server answered the upload with action $a")
      }
    } catch {
      case e: Exception =>
        NetworkThread.identityChange(e) match {
          case Some(changed) => Broken(changed.getMessage)
          case None => Broken(Option(e.getMessage).getOrElse(e.getClass.getSimpleName))
        }
    } finally {
      if (socket != null) try socket.close() catch { case _: IOException => }
    }
  }

  /** A frame as the connection's framing has it: its length in two bytes, then a packet's 80. */
  private def writeFrame(out: OutputStream, payload: Array[Byte]): Unit = {
    out.write(Constants.PACKET_SIZE >>> 8)
    out.write(Constants.PACKET_SIZE & 0xFF)
    out.write(payload)
    out.write(new Array[Byte](Constants.HMAC_SIZE))
  }

  /** The server's next word on this connection: a PROBLEM_REPORT packet, unsigned. */
  private def readReply(in: DataInputStream): ProblemReportPacket = {
    val length = in.readUnsignedShort()
    if (length != Constants.PACKET_SIZE) throw new IOException(s"a frame of $length bytes")
    val frame = new Array[Byte](Constants.PACKET_SIZE)
    in.readFully(frame)
    PacketSerializer.deserialize(java.util.Arrays.copyOf(frame, Constants.PACKET_PAYLOAD_SIZE)) match {
      case p: ProblemReportPacket => p
      case p => throw new IOException(s"the server sent ${p.getType} on an upload")
    }
  }
}
