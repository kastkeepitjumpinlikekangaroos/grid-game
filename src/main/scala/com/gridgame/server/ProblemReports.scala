package com.gridgame.server

import com.gridgame.common.BuildInfo
import com.gridgame.common.Constants
import com.gridgame.common.observability.Attrs
import com.gridgame.common.observability.Metrics
import com.gridgame.common.protocol._
import com.gridgame.server.game.MatchJournal
import com.gridgame.server.net.{Outbox, RateLimiter, Sessions, UploadReceiver}
import com.google.gson.{GsonBuilder, JsonNull, JsonObject, JsonParser}
import io.netty.buffer.Unpooled
import io.netty.channel.{Channel, ChannelFuture, ChannelFutureListener}

import java.io.{File, IOException}
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.nio.file.{AtomicMoveNotSupportedException, Files, Path, Paths, StandardCopyOption}
import java.security.SecureRandom
import java.time.{Instant, ZoneOffset}
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.{ExecutorService, Executors, ThreadFactory}
import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal

/**
 * Players' problem reports (design/diagnostics.md, *Sending a report*): the server's half of
 * sending one, and keeping it on disk for whoever runs the server to read.
 *
 * A client with a report ready says so on its session (PROBLEM_REPORT BEGIN: the report's id, its
 * size and CRC-32, the match it is about). The server refuses it (too big, too many from this
 * account, too many under way, no room) or answers READY with a ticket: 16 random bytes, good for
 * one upload of that report by that player within TicketMs. The report itself never travels as
 * packets. The client opens a second connection, logs it in with the ticket (AUTH_REQUEST UPLOAD,
 * [[handleUploadLogin]]), and that connection is given over to the upload (UploadReceiver): it
 * never becomes a session, it reads exactly the report's bytes into a file, and it closes with
 * ACCEPTED and the report's code, or FAILED. A report the server has already (a resend after a
 * lost reply) is answered ACCEPTED at once, with its code.
 *
 * Kept in `folder` as `<yyyy-MM-dd>/<code>/`: `report.zip` (the bytes as they came), `server.json`
 * (what the server knows of it: when, from whom, about which match, which build of the server; not
 * the address it came from), and `match-journal.txt` (what the server saw and decided in that match,
 * MatchJournal) while the server still has the match's journal. A part not yet whole waits in
 * `.incoming`, and is thrown away if the server stops first.
 *
 * @param journalOf the journal of the match with this id: a running one's, or one that ended lately
 * @param nameOf    the name the player logged in with
 * @param storeOn   where a whole report is put in its folder: a thread of its own, unless a test
 *                  wants it done there and then
 */
final class ProblemReports(folder: Path, outbox: Outbox, sessions: Sessions, rateLimiter: RateLimiter,
                           journalOf: UUID => Option[MatchJournal], nameOf: UUID => String,
                           limits: ProblemReports.Limits = ProblemReports.Limits(),
                           clock: () => Long = () => System.currentTimeMillis(),
                           storeOn: java.util.concurrent.Executor = null) {
  import ProblemReports._

  private val incoming = folder.resolve(IncomingFolder)
  private val random = new SecureRandom()

  // All of it under the lock: a few requests an hour, at the most
  private val lock = new Object
  // Tickets given and not yet used, by the ticket (hex); and the uploads under way, by player
  private val tickets = new java.util.HashMap[String, Pending]()
  private val uploading = new java.util.HashMap[UUID, Pending]()
  // What every report kept takes on disk, and what those given a ticket or under way may take
  private var storedBytes = 0L
  private var reservedBytes = 0L
  // Every report kept: its id to its code, and every code given
  private val stored = new java.util.HashMap[UUID, String]()
  private val codes = new java.util.HashSet[String]()
  // When each account's reports of the last day were kept, oldest first
  private val keptAt = new java.util.HashMap[UUID, java.util.ArrayDeque[java.lang.Long]]()

  /** Whether reports can be kept at all: the folder could be made and written to. */
  val available: Boolean =
    try {
      Files.createDirectories(incoming)
      // What a server that stopped mid-upload left: never whole, never kept
      val stale = Files.list(incoming)
      try stale.iterator().asScala.foreach(p => Files.deleteIfExists(p)) finally stale.close()
      scan()
      if (!Files.isWritable(folder)) throw new IOException(s"$folder is not writable")
      true
    } catch {
      case NonFatal(e) =>
        System.err.println(s"Problem reports: can't keep any in $folder (${e.getMessage}): every report will be refused")
        false
    }

  // Where a report is made whole: off the network's threads, as it is a few disk writes and a move
  private val ownThread: ExecutorService =
    if (storeOn != null) null
    else Executors.newSingleThreadExecutor(new ThreadFactory {
      def newThread(r: Runnable): Thread = {
        val t = new Thread(r, "ProblemReports")
        t.setDaemon(true)
        t
      }
    })
  private val storing: java.util.concurrent.Executor = if (storeOn != null) storeOn else ownThread

  /** Reports kept, and the bytes they take: for the log at startup and for tests. */
  def keptCount: Int = lock.synchronized(stored.size())
  def keptBytes: Long = lock.synchronized(storedBytes)

  /** The reports already in the folder: their ids and codes, who sent them lately, and what they take. */
  private def scan(): Unit = {
    val now = clock()
    val walk = Files.walk(folder)
    try {
      walk.iterator().asScala.foreach { p =>
        if (Files.isRegularFile(p) && !p.startsWith(incoming)) {
          storedBytes += Files.size(p)
          if (p.getFileName.toString == ServerJson) {
            try {
              val json = JsonParser.parseString(new String(Files.readAllBytes(p), StandardCharsets.UTF_8)).getAsJsonObject
              val code = json.get("code").getAsString
              codes.add(code)
              stored.put(UUID.fromString(json.get("reportId").getAsString), code)
              val at = Instant.parse(json.get("receivedAt").getAsString).toEpochMilli
              if (now - at < DayMs) keptAt.computeIfAbsent(UUID.fromString(json.get("playerId").getAsString),
                _ => new java.util.ArrayDeque[java.lang.Long]()).add(at)
            } catch {
              case NonFatal(e) => System.err.println(s"Problem reports: couldn't read $p: ${e.getMessage}")
            }
          }
        }
      }
    } finally walk.close()
    keptAt.values().forEach { times =>
      val sorted = times.asScala.toVector.sortBy(_.longValue())
      times.clear()
      sorted.foreach(times.add)
    }
    if (!stored.isEmpty) println(s"Problem reports: ${stored.size()} kept in $folder (${sizeText(storedBytes)})")
  }

  // ── The session's half ─────────────────────────────────────────────────

  /** A client's request on its session (`channel`): BEGIN, answered READY, REFUSED or ACCEPTED. */
  def handleBegin(packet: ProblemReportPacket, channel: Channel): Unit = {
    if (packet.getAction != ProblemReportAction.BEGIN) return // nothing else comes from a client
    val playerId = packet.getPlayerId
    val reportId = packet.getReportId
    val name = nameOf(playerId)
    if (reportId == null) {
      outbox.sendVia(ProblemReportPacket.refused(outbox.nextSeq(), playerId, null, ProblemReportRefusal.UNAVAILABLE, 0), channel)
      return
    }
    val (reply, outcome) = lock.synchronized {
      val now = clock()
      expire(now)
      def refuse(reason: Byte, retryAfter: Int, outcome: String) =
        (ProblemReportPacket.refused(outbox.nextSeq(), playerId, reportId, reason, retryAfter), outcome)
      val already = stored.get(reportId)
      if (already != null) {
        (ProblemReportPacket.accepted(outbox.nextSeq(), playerId, reportId, already), "duplicate")
      } else if (!available) {
        refuse(ProblemReportRefusal.UNAVAILABLE, 0, "refused_unavailable")
      } else if (packet.getSize <= 0 || packet.getSize > limits.maxBytes) {
        refuse(ProblemReportRefusal.TOO_BIG, 0, "refused_too_big")
      } else {
        val wait = waitForLimit(playerId, now)
        val theirs = tickets.values().asScala.find(_.playerId == playerId)
        if (wait > 0) refuse(ProblemReportRefusal.TOO_MANY, wait, "refused_too_many")
        else if (uploading.containsKey(playerId) || theirs.exists(_.reportId != reportId)) {
          refuse(ProblemReportRefusal.BUSY, limits.busyRetrySeconds, "refused_busy")
        } else {
          // The same report asked for again (its READY lost, say): its last ticket gives way to the new one
          theirs.foreach(drop)
          if (tickets.size() + uploading.size() >= limits.maxConcurrent) {
            refuse(ProblemReportRefusal.BUSY, limits.busyRetrySeconds, "refused_busy")
          } else if (storedBytes + reservedBytes + packet.getSize > limits.maxStorageBytes) {
            refuse(ProblemReportRefusal.STORAGE_FULL, 0, "refused_storage_full")
          } else {
            val ticket = new Array[Byte](ProblemReportPacket.TicketSize)
            random.nextBytes(ticket)
            val pending = Pending(hex(ticket), playerId, name, reportId, packet.getSize, packet.getCrc,
              packet.getMatchId, packet.getKind, now)
            tickets.put(pending.ticket, pending)
            reservedBytes += pending.size
            (ProblemReportPacket.ready(outbox.nextSeq(), playerId, reportId, ticket), "ticketed")
          }
        }
      }
    }
    outbox.sendVia(reply, channel)
    count(outcome)
    reply.getAction match {
      case ProblemReportAction.REFUSED =>
        println(s"Problem report from $name refused: ${refusalText(reply.getReason)}" +
          (if (reply.getRetryAfterSeconds > 0) s" (try again in ${reply.getRetryAfterSeconds}s)" else ""))
      case _ =>
    }
  }

  /** How long, in seconds, before the account may send another: 0 if it may now. */
  private def waitForLimit(playerId: UUID, now: Long): Int = {
    val times = keptAt.get(playerId)
    if (times == null) return 0
    while (!times.isEmpty && now - times.peekFirst().longValue() >= DayMs) times.pollFirst()
    if (times.isEmpty) { keptAt.remove(playerId); return 0 }
    val all = times.asScala.toVector.map(_.longValue())
    val lastHour = all.filter(now - _ < HourMs)
    val waitMs = Math.max(
      if (lastHour.size >= limits.perHour) lastHour(lastHour.size - limits.perHour) + HourMs - now else 0L,
      if (all.size >= limits.perDay) all(all.size - limits.perDay) + DayMs - now else 0L)
    if (waitMs <= 0) 0 else ((waitMs + 999) / 1000).toInt
  }

  /** Tickets not used in time give their room back. */
  def expire(now: Long = clock()): Unit = lock.synchronized {
    val it = tickets.values().iterator()
    while (it.hasNext) {
      val t = it.next()
      if (now - t.issuedAt > limits.ticketMs) {
        it.remove()
        reservedBytes -= t.size
      }
    }
  }

  private def drop(t: Pending): Unit = if (tickets.remove(t.ticket) != null) reservedBytes -= t.size

  // ── The upload connection's half ───────────────────────────────────────

  /**
   * A connection logging in with an upload's ticket (AUTH_REQUEST UPLOAD): a good ticket gives the
   * connection over to its report (UploadReceiver) and tells it to send (READY); anything else is
   * refused (BAD_TICKET), counted as a failed login of its address, and the connection closed. A
   * session's own connection is never given over: refused, and left as it was.
   */
  def handleUploadLogin(request: AuthRequestPacket, channel: Channel): Unit = {
    if (channel == null) return
    val token = request.getRefreshToken
    val reportId = ProblemReportPacket.reportIdOf(token)
    val address = channel.remoteAddress() match {
      case a: InetSocketAddress => a.getAddress
      case _ => null
    }
    if (sessions.channelToToken.containsKey(channel) || sessions.channelToPlayer.containsKey(channel)) {
      System.err.println(s"Problem reports: a session's connection asked to carry an upload; refused")
      outbox.sendVia(ProblemReportPacket.refused(outbox.nextSeq(), new UUID(0L, 0L), reportId, ProblemReportRefusal.BAD_TICKET, 0), channel)
      return
    }
    if (address != null && !rateLimiter.allowAuthAttempt(address)) {
      // Its address is waiting out its failed logins: a ticket is a login like any other
      refuseUpload(channel, new UUID(0L, 0L), reportId, ProblemReportRefusal.BUSY, limits.busyRetrySeconds, "refused_busy",
        s"from $address refused: it is waiting out failed logins")
      return
    }
    val pending = lock.synchronized {
      expire()
      val t = if (token == null) null else tickets.get(hex(ProblemReportPacket.ticketOf(token)))
      if (t == null || reportId == null || t.reportId != reportId) null
      else {
        // Used: it is good for this one connection, and its room is the upload's now
        tickets.remove(t.ticket)
        uploading.put(t.playerId, t)
        t
      }
    }
    if (pending == null) {
      if (address != null) rateLimiter.recordAuthFailure(address)
      refuseUpload(channel, new UUID(0L, 0L), reportId, ProblemReportRefusal.BAD_TICKET, 0, "bad_ticket",
        s"from ${channel.remoteAddress()} refused: no such ticket")
      return
    }

    val part = incoming.resolve(s"${pending.reportId}-${System.nanoTime()}.part")
    val receiver =
      try new UploadReceiver(pending.size.toLong, part, end => uploadEnded(channel, pending, part, end), limits.deadlineMs)
      catch {
        case e: IOException =>
          release(pending)
          refuseUpload(channel, pending.playerId, pending.reportId, ProblemReportRefusal.UNAVAILABLE, 0, "refused_unavailable",
            s"from ${pending.name} refused: couldn't make $part (${e.getMessage})")
          return
      }
    UploadReceiver.takeOver(channel, receiver, limits.idleMs)
    // It never logged in, but what the rate limiter kept of it before it was given over goes with it
    channel.closeFuture().addListener(new ChannelFutureListener {
      def operationComplete(f: ChannelFuture): Unit = sessions.channelClosed(channel)
    })
    // The go-ahead: the frame decoder has gone, so whatever comes next is the report
    writeUnsigned(channel, ProblemReportPacket.ready(outbox.nextSeq(), pending.playerId, pending.reportId, null), close = false)
  }

  /** The upload is over (on the connection's event loop): kept if all of it came and its CRC is
    * the one BEGIN gave, and the client told either way. */
  private def uploadEnded(channel: Channel, pending: Pending, part: Path, end: UploadReceiver.End): Unit = end match {
    case UploadReceiver.Received(crc) if crc == pending.crc =>
      storing.execute(() => store(channel, pending, part))
    case UploadReceiver.Received(crc) =>
      try Files.deleteIfExists(part) catch { case _: IOException => }
      fail(channel, pending, ProblemReportRefusal.CORRUPT, "failed_corrupt",
        f"its bytes' CRC-32 is $crc%08x, not the ${pending.crc}%08x it said")
    case UploadReceiver.TooMuch =>
      fail(channel, pending, ProblemReportRefusal.CORRUPT, "failed_corrupt", s"more than the ${pending.size} bytes it said came")
    case UploadReceiver.Cut(why) =>
      fail(channel, pending, ProblemReportRefusal.TRUNCATED, "failed_truncated", why)
  }

  private def fail(channel: Channel, pending: Pending, reason: Byte, outcome: String, why: String): Unit = {
    release(pending)
    count(outcome)
    println(s"Problem report from ${pending.name} failed: $why")
    if (channel.isActive) writeUnsigned(channel, ProblemReportPacket.failed(outbox.nextSeq(), pending.playerId, pending.reportId, reason))
    else channel.close()
  }

  /** The upload came whole: give it a code, put it in its folder with what the server knows of it
    * (and of its match), and tell the client its code. On the storing thread. */
  private def store(channel: Channel, pending: Pending, part: Path): Unit = {
    var dir: Path = null
    try {
      val now = clock()
      val day = DayFormat.format(Instant.ofEpochMilli(now))
      val code = lock.synchronized {
        var c = newCode()
        while (codes.contains(c) || Files.exists(folder.resolve(day).resolve(c))) c = newCode()
        codes.add(c)
        c
      }
      dir = folder.resolve(day).resolve(code)
      Files.createDirectories(dir)
      move(part, dir.resolve(ReportZip))
      val journal = if (pending.matchId != null) journalOf(pending.matchId) else None
      journal.foreach(j => Files.write(dir.resolve(MatchJournalTxt), j.render.getBytes(StandardCharsets.UTF_8)))
      Files.write(dir.resolve(ServerJson), serverJson(code, pending, now, journal.isDefined).getBytes(StandardCharsets.UTF_8))
      val bytes = {
        val files = Files.list(dir)
        try files.iterator().asScala.map(Files.size).sum finally files.close()
      }
      lock.synchronized {
        uploading.remove(pending.playerId, pending)
        reservedBytes -= pending.size
        storedBytes += bytes
        stored.put(pending.reportId, code)
        keptAt.computeIfAbsent(pending.playerId, _ => new java.util.ArrayDeque[java.lang.Long]()).add(now)
      }
      count("stored")
      println(s"Problem report $code from ${pending.name} (${kindName(pending.kind)}, ${sizeText(pending.size)}, " +
        s"match ${if (pending.matchId != null) pending.matchId else "none"}${if (journal.isDefined) ", with its journal" else ""}): $dir")
      writeUnsigned(channel, ProblemReportPacket.accepted(outbox.nextSeq(), pending.playerId, pending.reportId, code))
    } catch {
      case NonFatal(e) =>
        try Files.deleteIfExists(part) catch { case _: IOException => }
        if (dir != null) deleteFolder(dir)
        fail(channel, pending, ProblemReportRefusal.UNAVAILABLE, "failed_unavailable", s"it couldn't be kept: $e")
    }
  }

  private def serverJson(code: String, pending: Pending, now: Long, withJournal: Boolean): String = {
    val o = new JsonObject
    o.addProperty("code", code)
    o.addProperty("reportId", pending.reportId.toString)
    o.addProperty("receivedAt", Instant.ofEpochMilli(now).toString)
    o.addProperty("account", pending.name)
    o.addProperty("playerId", pending.playerId.toString)
    o.addProperty("kind", kindName(pending.kind))
    o.addProperty("size", pending.size)
    o.addProperty("crc", f"${pending.crc}%08x")
    if (pending.matchId != null) o.addProperty("matchId", pending.matchId.toString) else o.add("matchId", JsonNull.INSTANCE)
    o.addProperty("matchJournal", withJournal)
    o.addProperty("server", BuildInfo.describe)
    Gson.toJson(o) + "\n"
  }

  private def release(pending: Pending): Unit = lock.synchronized {
    if (uploading.remove(pending.playerId, pending)) reservedBytes -= pending.size
  }

  private def refuseUpload(channel: Channel, playerId: UUID, reportId: UUID, reason: Byte, retryAfter: Int,
                           outcome: String, logLine: String): Unit = {
    count(outcome)
    println(s"Problem report upload $logLine")
    writeUnsigned(channel, ProblemReportPacket.refused(outbox.nextSeq(), playerId, reportId, reason, retryAfter))
  }

  /** A frame on an upload's connection: the payload and 16 zeros, as an AUTH_REQUEST is (it has no
    * session to be signed with); then closed, unless the upload is still to come. */
  private def writeUnsigned(channel: Channel, packet: Packet, close: Boolean = true): Unit = {
    if (!channel.isActive) { channel.close(); return }
    val frame = new Array[Byte](Constants.PACKET_SIZE)
    System.arraycopy(packet.serialize(), 0, frame, 0, Constants.PACKET_PAYLOAD_SIZE)
    val written = channel.writeAndFlush(Unpooled.wrappedBuffer(frame))
    if (close) written.addListener(ChannelFutureListener.CLOSE)
  }

  private def newCode(): String = {
    val sb = new StringBuilder("GG-")
    for (_ <- 0 until 8) sb.append(CodeAlphabet.charAt(random.nextInt(CodeAlphabet.length)))
    sb.toString
  }

  private def count(outcome: String): Unit = Metrics.problemReports.add(1L, Attrs.problemReportOutcome(outcome))

  /** The server is stopping: nothing more is kept. */
  def shutdown(): Unit = if (ownThread != null) ownThread.shutdown()
}

object ProblemReports {
  /** What the server takes: how big a report, how many from an account an hour and a day, how many
    * under way at once, the folder's size, and the ticket's and an upload's times. */
  final case class Limits(maxBytes: Int = 64 << 20, perHour: Int = 6, perDay: Int = 20, maxConcurrent: Int = 4,
                          maxStorageBytes: Long = 2L << 30, ticketMs: Long = 60000L,
                          idleMs: Long = UploadReceiver.IdleMs, deadlineMs: Long = UploadReceiver.DeadlineMs,
                          busyRetrySeconds: Int = 30)

  /** A ticket given for a report, and then its upload under way. */
  private final case class Pending(ticket: String, playerId: UUID, name: String, reportId: UUID, size: Int, crc: Int,
                                   matchId: UUID, kind: Byte, issuedAt: Long)

  val FolderName = "problem_reports"
  val IncomingFolder = ".incoming"
  val ReportZip = "report.zip"
  val ServerJson = "server.json"
  val MatchJournalTxt = "match-journal.txt"

  private val HourMs = 3600000L
  private val DayMs = 24 * HourMs
  /** Crockford's base32: no I, L, O or U, so a code read aloud or copied by hand comes out right. */
  private val CodeAlphabet = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
  private val DayFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC)
  private val Gson = new GsonBuilder().setPrettyPrinting().serializeNulls().create()

  /** Where reports are kept: beside the account database (AuthDatabase.resolveDbPath), in the directory
    * `bazel run` was run from, or the working directory. */
  def defaultFolder(): Path = {
    val buildWorkDir = System.getenv("BUILD_WORKING_DIRECTORY")
    if (buildWorkDir != null) new File(buildWorkDir, FolderName).toPath.toAbsolutePath
    else Paths.get(FolderName).toAbsolutePath
  }

  def kindName(kind: Byte): String = kind match {
    case ProblemReportKind.MATCH => "match"
    case ProblemReportKind.CRASH => "crash"
    case ProblemReportKind.GENERAL => "general"
    case k => s"kind $k"
  }

  private def refusalText(reason: Byte): String = reason match {
    case ProblemReportRefusal.TOO_MANY => "too many from this account"
    case ProblemReportRefusal.TOO_BIG => "too big"
    case ProblemReportRefusal.STORAGE_FULL => "no room left for reports"
    case ProblemReportRefusal.BUSY => "busy"
    case ProblemReportRefusal.UNAVAILABLE => "reports can't be kept"
    case r => s"reason $r"
  }

  private[server] def sizeText(bytes: Long): String =
    if (bytes < 1024) s"$bytes B"
    else if (bytes < 1024 * 1024) f"${bytes / 1024.0}%.1f KB"
    else f"${bytes / (1024.0 * 1024.0)}%.1f MB"

  private def hex(b: Array[Byte]): String = b.map(x => f"${x & 0xFF}%02x").mkString

  private def move(from: Path, to: Path): Unit =
    try Files.move(from, to, StandardCopyOption.ATOMIC_MOVE)
    catch { case _: AtomicMoveNotSupportedException => Files.move(from, to) }

  private def deleteFolder(dir: Path): Unit =
    try {
      val files = Files.list(dir)
      try files.iterator().asScala.foreach(p => Files.deleteIfExists(p)) finally files.close()
      Files.deleteIfExists(dir)
    } catch { case _: IOException => }
}
