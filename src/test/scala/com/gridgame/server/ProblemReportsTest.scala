package com.gridgame.server

import com.gridgame.common.BuildInfo
import com.gridgame.common.Constants
import com.gridgame.common.protocol._
import com.gridgame.server.game.MatchJournal
import com.gridgame.server.net.{PacketRouter, RateLimiter, TcpHandler, UploadReceiver}
import com.google.gson.JsonParser
import io.netty.buffer.{ByteBuf, Unpooled}
import io.netty.channel.Channel
import io.netty.handler.codec.{LengthFieldBasedFrameDecoder, LengthFieldPrepender}
import org.junit.Assert._
import org.junit.{After, Test}

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.zip.CRC32
import scala.jdk.CollectionConverters._

/**
 * A player's problem report, as the server takes it (ProblemReports; design/diagnostics.md): asked
 * for on the session (BEGIN) and answered with a ticket, refused, or the code of one it has already;
 * then sent whole, raw, down a connection of its own that logs in with the ticket and is never a
 * session (UploadReceiver), and kept in a folder of its own with what the server knows of it. Each
 * upload connection here has a pipeline as ServerNetwork gives one (length framing, then the real
 * TcpHandler), and an address of its own, as the server allows five connections a minute from one.
 */
class ProblemReportsTest {
  import ServerTestKit._

  private val folder: Path = Files.createTempDirectory("problem-reports")
  // The test's own clock, which the reports' limits and tickets are timed on
  private var now = 1793000000000L
  private val journals = scala.collection.mutable.Map[UUID, MatchJournal]()
  private val rateLimiter = new RateLimiter()
  private var hosts = 0

  /** Reports kept in `at`, timed on the test's clock and stored as soon as they are whole. */
  private def reportsIn(at: Path, limits: ProblemReports.Limits = ProblemReports.Limits()): ProblemReports =
    new ProblemReports(at, server.outbox, server.sessions, rateLimiter, id => journals.get(id), _ => "alice", limits,
      () => now, (r: Runnable) => r.run())

  private lazy val reports = reportsIn(folder)

  @After def tidy(): Unit = deleteAll(folder)

  private def deleteAll(p: Path): Unit = if (Files.exists(p)) {
    val walk = Files.walk(p)
    try walk.iterator().asScala.toVector.reverse.foreach(Files.deleteIfExists) finally walk.close()
  }

  private def crcOf(b: Array[Byte]): Int = { val c = new CRC32(); c.update(b); c.getValue.toInt }

  private def bytes(n: Int, seed: Int = 1): Array[Byte] = { val b = new Array[Byte](n); new scala.util.Random(seed).nextBytes(b); b }

  /** A logged-in player: the server answers on its session's channel. */
  private class Reporter(val id: UUID = UUID.randomUUID()) {
    val channel = new PeerChannel()
    server.sessions.generateSessionToken(id, channel)

    def begin(r: ProblemReports, reportId: UUID, report: Array[Byte], matchId: UUID = null,
              kind: Byte = ProblemReportKind.MATCH, size: Int = -1, crc: Option[Int] = None): ProblemReportPacket = {
      r.handleBegin(ProblemReportPacket.begin(1, id, reportId, if (size >= 0) size else report.length,
        crc.getOrElse(crcOf(report)), matchId, kind), channel)
      val got = replies()
      assertEquals("one answer to a BEGIN", 1, got.size)
      got.head
    }

    def replies(): Seq[ProblemReportPacket] = {
      channel.runPendingTasks()
      Iterator.continually(channel.readOutbound[ByteBuf]()).takeWhile(_ != null).map(decode)
        .collect { case p: ProblemReportPacket => p }.toVector
    }
  }

  /** A new connection to the server, as ServerNetwork makes one, whose logins with a ticket go to `r`. */
  private def uploadConnection(r: ProblemReports, host: String = null): PeerChannel = {
    hosts += 1
    val ch = new PeerChannel(if (host != null) host else s"127.0.${1 + hosts / 250}.${1 + hosts % 250}")
    val router = new PacketRouter {
      def handleIncomingPacket(p: Packet, tcpCh: Channel, udpSender: InetSocketAddress): Unit = p match {
        case a: AuthRequestPacket if a.getAction == AuthAction.UPLOAD => r.handleUploadLogin(a, tcpCh)
        case _ =>
      }
      def handleDisconnect(c: Channel): Unit = server.sessions.channelClosed(c)
    }
    ch.pipeline().addLast(new LengthFieldBasedFrameDecoder(Constants.PACKET_SIZE + 2, 0, 2, 0, 2), new LengthFieldPrepender(2),
      new TcpHandler(server.sessions, rateLimiter, server.replayGuard, router))
    ch.pipeline().fireChannelActive()
    ch
  }

  /** A frame as the client writes one: its length, the payload, and no signature. */
  private def frame(p: Packet): ByteBuf = {
    val b = Unpooled.buffer(2 + Constants.PACKET_SIZE)
    b.writeShort(Constants.PACKET_SIZE)
    b.writeBytes(p.serialize())
    b.writeZero(Constants.HMAC_SIZE)
    b
  }

  private def uploadLogin(ticket: Array[Byte], reportId: UUID): ByteBuf =
    frame(new AuthRequestPacket(0, AuthAction.UPLOAD, ProblemReportPacket.uploadToken(ticket, reportId)))

  /** The frames the server wrote down an upload connection since the last call. */
  private def framesFrom(ch: PeerChannel): Seq[ProblemReportPacket] = {
    ch.runPendingTasks()
    val all = Unpooled.buffer()
    Iterator.continually(ch.readOutbound[ByteBuf]()).takeWhile(_ != null).foreach { b => all.writeBytes(b); b.release() }
    val out = Vector.newBuilder[ProblemReportPacket]
    while (all.readableBytes() >= 2) {
      val len = all.readUnsignedShort()
      assertEquals("every frame is a packet's 80 bytes", Constants.PACKET_SIZE, len)
      val payload = new Array[Byte](Constants.PACKET_PAYLOAD_SIZE)
      all.readBytes(payload)
      val signature = new Array[Byte](Constants.HMAC_SIZE)
      all.readBytes(signature)
      assertTrue("an upload connection's frames are unsigned: it has no session", signature.forall(_ == 0))
      out += PacketSerializer.deserialize(payload).asInstanceOf[ProblemReportPacket]
    }
    all.release()
    out.result()
  }

  /** Sends `report` (BEGIN, then the upload, in `chunks` pieces): what the upload connection was
    * told after the go-ahead, and the connection. */
  private def send(r: ProblemReports, who: Reporter, reportId: UUID, report: Array[Byte], matchId: UUID = null,
                   chunks: Int = 3): (Seq[ProblemReportPacket], PeerChannel) = {
    val ready = who.begin(r, reportId, report, matchId)
    assertEquals(s"a ticket for it (${ready.getReason})", ProblemReportAction.READY, ready.getAction)
    val ch = uploadConnection(r)
    ch.writeInbound(uploadLogin(ready.getTicket, reportId))
    val goAhead = framesFrom(ch)
    assertEquals(Seq(ProblemReportAction.READY), goAhead.map(_.getAction))
    report.grouped(Math.max(1, (report.length + chunks - 1) / chunks)).foreach(c => ch.writeInbound(Unpooled.wrappedBuffer(c)))
    (framesFrom(ch), ch)
  }

  private def keptFolders(at: Path = folder): Seq[Path] = {
    if (!Files.exists(at)) return Nil
    val walk = Files.walk(at, 2)
    try walk.iterator().asScala.filter(p => at.relativize(p).getNameCount == 2 && Files.isDirectory(p) &&
      !p.startsWith(at.resolve(ProblemReports.IncomingFolder))).toVector
    finally walk.close()
  }

  private def incomingParts(at: Path = folder): Seq[Path] = {
    val in = at.resolve(ProblemReports.IncomingFolder)
    if (!Files.exists(in)) Nil else { val l = Files.list(in); try l.iterator().asScala.toVector finally l.close() }
  }

  // --- On the session ---

  @Test def aReportReadyToSendIsGivenATicket(): Unit = {
    val who = new Reporter()
    val reportId = UUID.randomUUID()
    val ready = who.begin(reports, reportId, bytes(1000))
    assertEquals(ProblemReportAction.READY, ready.getAction)
    assertEquals(reportId, ready.getReportId)
    assertEquals(ProblemReportPacket.TicketSize, ready.getTicket.length)
    assertFalse("a ticket is random bytes", ready.getTicket.forall(_ == 0))
    val again = new Reporter().begin(reports, UUID.randomUUID(), bytes(1000))
    assertFalse("and every one is its own", java.util.Arrays.equals(ready.getTicket, again.getTicket))
  }

  @Test def aReportOfNothingOrTooMuchIsRefused(): Unit = {
    val small = reportsIn(folder, ProblemReports.Limits(maxBytes = 5000))
    val who = new Reporter()
    for (size <- Seq(0, -1, 5001, Int.MaxValue)) {
      val r = who.begin(small, UUID.randomUUID(), Array.emptyByteArray, size = size)
      assertEquals(s"$size bytes", ProblemReportAction.REFUSED, r.getAction)
      assertEquals(ProblemReportRefusal.TOO_BIG, r.getReason)
      assertEquals("no point trying again", 0, r.getRetryAfterSeconds)
    }
    assertEquals("5000 is taken", ProblemReportAction.READY, who.begin(small, UUID.randomUUID(), bytes(5000)).getAction)
  }

  @Test def anAccountSendsSixAnHour(): Unit = {
    val who = new Reporter()
    for (i <- 0 until 6) {
      val (told, _) = send(reports, who, UUID.randomUUID(), bytes(200, i))
      assertEquals(ProblemReportAction.ACCEPTED, told.last.getAction)
      now += 60000
    }
    val seventh = who.begin(reports, UUID.randomUUID(), bytes(200))
    assertEquals(ProblemReportAction.REFUSED, seventh.getAction)
    assertEquals(ProblemReportRefusal.TOO_MANY, seventh.getReason)
    assertEquals("until the hour of the first is up", 3600 - 6 * 60, seventh.getRetryAfterSeconds)
    assertEquals("another player's is taken", ProblemReportAction.READY,
      new Reporter().begin(reports, UUID.randomUUID(), bytes(200)).getAction)
    now += seventh.getRetryAfterSeconds * 1000L
    assertEquals("and theirs again once it is", ProblemReportAction.READY, who.begin(reports, UUID.randomUUID(), bytes(200)).getAction)
  }

  @Test def anAccountSendsTwentyADay(): Unit = {
    val daily = reportsIn(folder, ProblemReports.Limits(perHour = 100))
    val who = new Reporter()
    val first = now
    for (i <- 0 until 20) {
      assertEquals(ProblemReportAction.ACCEPTED, send(daily, who, UUID.randomUUID(), bytes(100, i))._1.last.getAction)
      now += 10 * 60000
    }
    val next = who.begin(daily, UUID.randomUUID(), bytes(100))
    assertEquals(ProblemReportRefusal.TOO_MANY, next.getReason)
    assertEquals(((first + 24 * 3600000L - now) / 1000).toInt, next.getRetryAfterSeconds)
  }

  @Test def onePlayerSendsOneAtATimeAndTheServerFourInAll(): Unit = {
    val who = new Reporter()
    val reportId = UUID.randomUUID()
    val first = who.begin(reports, reportId, bytes(300))
    val other = who.begin(reports, UUID.randomUUID(), bytes(300))
    assertEquals("a second report while the first has its ticket", ProblemReportRefusal.BUSY, other.getReason)
    assertEquals(30, other.getRetryAfterSeconds)

    // The same report asked for again (its answer lost): a new ticket, and the old one is no good
    val again = who.begin(reports, reportId, bytes(300))
    assertEquals(ProblemReportAction.READY, again.getAction)
    val stale = uploadConnection(reports)
    stale.writeInbound(uploadLogin(first.getTicket, reportId))
    assertEquals(Seq(ProblemReportRefusal.BAD_TICKET), framesFrom(stale).map(_.getReason))

    // Three more players make four under way; a fifth waits
    for (_ <- 0 until 3) assertEquals(ProblemReportAction.READY, new Reporter().begin(reports, UUID.randomUUID(), bytes(10)).getAction)
    val fifth = new Reporter().begin(reports, UUID.randomUUID(), bytes(10))
    assertEquals(ProblemReportRefusal.BUSY, fifth.getReason)

    // A ticket not used within its minute gives its place back
    now += 61000
    assertEquals(ProblemReportAction.READY, new Reporter().begin(reports, UUID.randomUUID(), bytes(10)).getAction)
  }

  @Test def aFolderThatIsFullTakesNoMore(): Unit = {
    val full = Files.createTempDirectory("problem-reports-full")
    try {
      Files.createDirectories(full.resolve("2026-10-01").resolve("GG-OLD00000"))
      Files.write(full.resolve("2026-10-01").resolve("GG-OLD00000").resolve("report.zip"), bytes(900))
      val r = reportsIn(full, ProblemReports.Limits(maxStorageBytes = 1000))
      assertEquals("what is there counts", 900L, r.keptBytes)
      val who = new Reporter()
      val refused = who.begin(r, UUID.randomUUID(), bytes(200))
      assertEquals(ProblemReportRefusal.STORAGE_FULL, refused.getReason)
      assertEquals(0, refused.getRetryAfterSeconds)
      assertEquals("one that fits is taken", ProblemReportAction.READY, who.begin(r, UUID.randomUUID(), bytes(50)).getAction)
      assertEquals("and what it may take is held for it", ProblemReportRefusal.STORAGE_FULL,
        new Reporter().begin(r, UUID.randomUUID(), bytes(60)).getReason)
    } finally deleteAll(full)
  }

  @Test def aReportAlreadyKeptIsAnsweredWithItsCode(): Unit = {
    val who = new Reporter()
    val reportId = UUID.randomUUID()
    val (told, _) = send(reports, who, reportId, bytes(4000))
    val code = told.last.getCode
    val again = who.begin(reports, reportId, bytes(4000))
    assertEquals("sent again after its answer was lost: kept once, under its code",
      (ProblemReportAction.ACCEPTED, reportId, code), (again.getAction, again.getReportId, again.getCode))
    assertEquals(1, keptFolders().size)
  }

  @Test def aServerWithNowhereToKeepReportsRefusesThem(): Unit = {
    val blocked = Files.createTempFile("problem-reports", ".not-a-folder")
    try {
      val r = reportsIn(blocked)
      assertFalse(r.available)
      assertEquals(ProblemReportRefusal.UNAVAILABLE, new Reporter().begin(r, UUID.randomUUID(), bytes(10)).getReason)
    } finally Files.deleteIfExists(blocked)
  }

  // --- The upload ---

  @Test def anUploadComesWholeAndIsKeptWithWhatTheServerKnowsOfIt(): Unit = {
    val who = new Reporter()
    val reportId = UUID.randomUUID()
    val matchId = UUID.randomUUID()
    val journal = new MatchJournal(matchId)
    journal.note("the match began, for the test")
    journals(matchId) = journal
    val report = bytes(150000, 7)

    val ready = who.begin(reports, reportId, report, matchId)
    val ch = uploadConnection(reports)
    ch.writeInbound(uploadLogin(ready.getTicket, reportId))
    val goAhead = framesFrom(ch)
    assertEquals(Seq(ProblemReportAction.READY), goAhead.map(_.getAction))
    assertTrue("its go-ahead carries no ticket", goAhead.head.getTicket.forall(_ == 0))
    assertEquals(who.id, goAhead.head.getPlayerId)
    // Given over: no frames read any more, no TcpHandler, the receiver instead
    assertNull(ch.pipeline().get(classOf[LengthFieldBasedFrameDecoder]))
    assertNull(ch.pipeline().get(classOf[TcpHandler]))
    assertNotNull(ch.pipeline().get(classOf[UploadReceiver]))
    assertNull("and it never became a session", server.sessions.channelToToken.get(ch))

    report.grouped(40000).foreach(c => ch.writeInbound(Unpooled.wrappedBuffer(c)))
    val told = framesFrom(ch)
    assertEquals(Seq(ProblemReportAction.ACCEPTED), told.map(_.getAction))
    val code = told.head.getCode
    assertTrue(code, code.matches("GG-[0-9A-HJKMNP-TV-Z]{8}"))
    assertEquals(reportId, told.head.getReportId)
    assertFalse("closed once it has said so", ch.isOpen)

    val kept = keptFolders()
    assertEquals(1, kept.size)
    val dir = kept.head
    assertEquals(code, dir.getFileName.toString)
    assertEquals("filed under the day it came", java.time.LocalDate.ofInstant(java.time.Instant.ofEpochMilli(now),
      java.time.ZoneOffset.UTC).toString, dir.getParent.getFileName.toString)
    assertArrayEquals("the bytes as they came", report, Files.readAllBytes(dir.resolve(ProblemReports.ReportZip)))

    val json = JsonParser.parseString(new String(Files.readAllBytes(dir.resolve(ProblemReports.ServerJson)), StandardCharsets.UTF_8)).getAsJsonObject
    assertEquals(code, json.get("code").getAsString)
    assertEquals(reportId.toString, json.get("reportId").getAsString)
    assertEquals(java.time.Instant.ofEpochMilli(now).toString, json.get("receivedAt").getAsString)
    assertEquals("alice", json.get("account").getAsString)
    assertEquals(who.id.toString, json.get("playerId").getAsString)
    assertEquals("match", json.get("kind").getAsString)
    assertEquals(report.length, json.get("size").getAsInt)
    assertEquals(f"${crcOf(report)}%08x", json.get("crc").getAsString)
    assertEquals(matchId.toString, json.get("matchId").getAsString)
    assertTrue(json.get("matchJournal").getAsBoolean)
    assertEquals(BuildInfo.describe, json.get("server").getAsString)
    val text = json.toString
    assertFalse("never where it came from", text.contains("127.0."))

    val journalText = new String(Files.readAllBytes(dir.resolve(ProblemReports.MatchJournalTxt)), StandardCharsets.UTF_8)
    assertTrue(journalText, journalText.contains(matchId.toString))
    assertTrue(journalText, journalText.contains("the match began, for the test"))
    assertTrue("nothing left halfway", incomingParts().isEmpty)
    assertEquals(1, reports.keptCount)
  }

  @Test def aReportAboutAMatchTheServerHasForgottenIsKeptWithoutItsJournal(): Unit = {
    val (told, _) = send(reports, new Reporter(), UUID.randomUUID(), bytes(500), matchId = UUID.randomUUID())
    assertEquals(ProblemReportAction.ACCEPTED, told.last.getAction)
    val dir = keptFolders().head
    assertFalse(Files.exists(dir.resolve(ProblemReports.MatchJournalTxt)))
    val json = JsonParser.parseString(new String(Files.readAllBytes(dir.resolve(ProblemReports.ServerJson)), StandardCharsets.UTF_8)).getAsJsonObject
    assertFalse(json.get("matchJournal").getAsBoolean)
  }

  @Test def aTicketIsGoodForOneUploadOfItsReportWithinItsMinute(): Unit = {
    val who = new Reporter()
    val reportId = UUID.randomUUID()
    val ready = who.begin(reports, reportId, bytes(100))

    // Another report's id with it
    val wrongReport = uploadConnection(reports)
    wrongReport.writeInbound(uploadLogin(ready.getTicket, UUID.randomUUID()))
    assertEquals(Seq(ProblemReportRefusal.BAD_TICKET), framesFrom(wrongReport).map(_.getReason))
    assertFalse(wrongReport.isOpen)

    // Used once, it is gone
    val first = uploadConnection(reports)
    first.writeInbound(uploadLogin(ready.getTicket, reportId))
    assertEquals(Seq(ProblemReportAction.READY), framesFrom(first).map(_.getAction))
    val second = uploadConnection(reports)
    second.writeInbound(uploadLogin(ready.getTicket, reportId))
    assertEquals(Seq(ProblemReportRefusal.BAD_TICKET), framesFrom(second).map(_.getReason))

    // Not used within its minute, it is gone too
    val late = new Reporter()
    val lateId = UUID.randomUUID()
    val lateReady = late.begin(reports, lateId, bytes(100))
    now += 60001
    val tooLate = uploadConnection(reports)
    tooLate.writeInbound(uploadLogin(lateReady.getTicket, lateId))
    assertEquals(Seq(ProblemReportRefusal.BAD_TICKET), framesFrom(tooLate).map(_.getReason))
  }

  @Test def badTicketsCountAsFailedLoginsOfTheirAddress(): Unit = {
    val host = "127.0.9.9"
    for (_ <- 0 until RateLimiter.MAX_AUTH_FAILURES) {
      val ch = uploadConnection(reports, host)
      ch.writeInbound(uploadLogin(bytes(16, 3), UUID.randomUUID()))
      assertEquals(Seq(ProblemReportRefusal.BAD_TICKET), framesFrom(ch).map(_.getReason))
      assertFalse(ch.isOpen)
    }
    assertFalse("its address waits, as after as many wrong passwords",
      rateLimiter.allowAuthAttempt(java.net.InetAddress.getByName(host)))
    // and even a good ticket from there waits with it
    val who = new Reporter()
    val reportId = UUID.randomUUID()
    val ready = who.begin(reports, reportId, bytes(100))
    rateLimiter.allowConnection(java.net.InetAddress.getByName(host)) // (its connections a minute aren't what this is about)
    val ch = new PeerChannel(host)
    ch.pipeline().addLast(new LengthFieldBasedFrameDecoder(Constants.PACKET_SIZE + 2, 0, 2, 0, 2), new LengthFieldPrepender(2))
    reports.handleUploadLogin(new AuthRequestPacket(0, AuthAction.UPLOAD, ProblemReportPacket.uploadToken(ready.getTicket, reportId)), ch)
    assertEquals(Seq(ProblemReportRefusal.BUSY), framesFrom(ch).map(_.getReason))
  }

  @Test def bytesThatArentTheReportAreNotKept(): Unit = {
    val who = new Reporter()
    // Not the bytes its CRC says
    val report = bytes(3000)
    val reportId = UUID.randomUUID()
    val ready = who.begin(reports, reportId, report, crc = Some(crcOf(report) ^ 1))
    val ch = uploadConnection(reports)
    ch.writeInbound(uploadLogin(ready.getTicket, reportId))
    framesFrom(ch)
    ch.writeInbound(Unpooled.wrappedBuffer(report))
    assertEquals(Seq((ProblemReportAction.FAILED, ProblemReportRefusal.CORRUPT)), framesFrom(ch).map(p => (p.getAction, p.getReason)))
    assertFalse(ch.isOpen)

    // More than it said
    val otherId = UUID.randomUUID()
    val more = who.begin(reports, otherId, report)
    val ch2 = uploadConnection(reports)
    ch2.writeInbound(uploadLogin(more.getTicket, otherId))
    framesFrom(ch2)
    ch2.writeInbound(Unpooled.wrappedBuffer(report ++ bytes(10)))
    assertEquals(Seq((ProblemReportAction.FAILED, ProblemReportRefusal.CORRUPT)), framesFrom(ch2).map(p => (p.getAction, p.getReason)))

    assertTrue("nothing kept", keptFolders().isEmpty)
    assertTrue("nothing left halfway", incomingParts().isEmpty)
    assertEquals("and the player may send again", ProblemReportAction.READY, who.begin(reports, otherId, report).getAction)
  }

  @Test def anUploadCutShortLeavesNothingBehind(): Unit = {
    val who = new Reporter()
    val reportId = UUID.randomUUID()
    val report = bytes(10000)
    val ready = who.begin(reports, reportId, report)
    val ch = uploadConnection(reports)
    ch.writeInbound(uploadLogin(ready.getTicket, reportId))
    framesFrom(ch)
    ch.writeInbound(Unpooled.wrappedBuffer(report.take(4000)))
    assertEquals("half of it waits as a part", 1, incomingParts().size)
    ch.close()
    ch.runPendingTasks()
    assertTrue(keptFolders().isEmpty)
    assertTrue("the part is thrown away", incomingParts().isEmpty)
    assertEquals("and the player may send it again", ProblemReportAction.READY, who.begin(reports, reportId, report).getAction)
  }

  @Test def anUploadThatGoesQuietIsCutOff(): Unit = {
    val quick = reportsIn(folder, ProblemReports.Limits(idleMs = 100))
    val who = new Reporter()
    val reportId = UUID.randomUUID()
    val report = bytes(1000)
    val ready = who.begin(quick, reportId, report)
    val ch = uploadConnection(quick)
    ch.writeInbound(uploadLogin(ready.getTicket, reportId))
    framesFrom(ch)
    ch.writeInbound(Unpooled.wrappedBuffer(report.take(10)))
    Thread.sleep(300)
    ch.advanceTimeBy(300, TimeUnit.MILLISECONDS)
    ch.runScheduledPendingTasks()
    assertEquals(Seq((ProblemReportAction.FAILED, ProblemReportRefusal.TRUNCATED)), framesFrom(ch).map(p => (p.getAction, p.getReason)))
    assertFalse(ch.isOpen)
    assertTrue(incomingParts().isEmpty)
  }

  @Test def theLoginDeadlineLeavesAnUploadBe(): Unit = {
    // Every connection that hasn't logged in within 15s is closed; an upload's never does, and may
    // take minutes
    val r = reportsIn(folder, ProblemReports.Limits(idleMs = 120000))
    val who = new Reporter()
    val reportId = UUID.randomUUID()
    val report = bytes(1000)
    val ready = who.begin(r, reportId, report)
    val ch = uploadConnection(r)
    ch.writeInbound(uploadLogin(ready.getTicket, reportId))
    framesFrom(ch)
    ch.writeInbound(Unpooled.wrappedBuffer(report.take(500)))
    ch.advanceTimeBy(Constants.LOGIN_DEADLINE_MS + 5000, TimeUnit.MILLISECONDS)
    ch.runScheduledPendingTasks()
    assertTrue("still open, past the login deadline", ch.isOpen)
    ch.writeInbound(Unpooled.wrappedBuffer(report.drop(500)))
    assertEquals(Seq(ProblemReportAction.ACCEPTED), framesFrom(ch).map(_.getAction))
  }

  @Test def aSessionsOwnConnectionIsNeverGivenOver(): Unit = {
    val who = new Reporter()
    val reportId = UUID.randomUUID()
    val ready = who.begin(reports, reportId, bytes(100))
    who.channel.pipeline().addLast(new LengthFieldBasedFrameDecoder(Constants.PACKET_SIZE + 2, 0, 2, 0, 2))
    reports.handleUploadLogin(new AuthRequestPacket(0, AuthAction.UPLOAD, ProblemReportPacket.uploadToken(ready.getTicket, reportId)),
      who.channel)
    assertEquals(Seq(ProblemReportRefusal.BAD_TICKET), who.replies().map(_.getReason))
    assertTrue("the session goes on", who.channel.isOpen)
    assertNotNull("its frames still read as frames", who.channel.pipeline().get(classOf[LengthFieldBasedFrameDecoder]))
    assertNull(who.channel.attr(TcpHandler.Upload).get())
    // and the ticket is still good for an upload's own connection
    val ch = uploadConnection(reports)
    ch.writeInbound(uploadLogin(ready.getTicket, reportId))
    assertEquals(Seq(ProblemReportAction.READY), framesFrom(ch).map(_.getAction))
  }

  @Test def aServerStartedAgainKnowsTheReportsItKept(): Unit = {
    val who = new Reporter()
    val ids = (0 until 6).map(_ => UUID.randomUUID())
    val codes = ids.zipWithIndex.map { case (id, i) => send(reports, who, id, bytes(700, i))._1.last.getCode }
    Files.write(folder.resolve(ProblemReports.IncomingFolder).resolve("left-by-a-crash.part"), bytes(50))

    val restarted = reportsIn(folder)
    assertTrue("what a stopped server left halfway is thrown away", incomingParts().isEmpty)
    assertEquals(6, restarted.keptCount)
    val walk = Files.walk(folder)
    val onDisk = try walk.iterator().asScala.filter(Files.isRegularFile(_)).map(Files.size).sum finally walk.close()
    assertEquals("and what they take", onDisk, restarted.keptBytes)
    val again = who.begin(restarted, ids(2), bytes(700, 2))
    assertEquals((ProblemReportAction.ACCEPTED, codes(2)), (again.getAction, again.getCode))
    assertEquals("and who sent how many lately", ProblemReportRefusal.TOO_MANY, who.begin(restarted, UUID.randomUUID(), bytes(10)).getReason)
  }

  // --- Through the server ---

  @Test def anUploadOverARealTlsConnectionIsKept(): Unit = {
    // The server's own sockets (ServerNetwork: TLS, length framing, the TcpHandler), and a client
    // connection as the game's makes one: the go-ahead read through TLS, the report written raw
    // down it after
    val own = Files.createTempDirectory("problem-reports-tls")
    val gs = new GameServer(0, reportsFolder = own)
    val port = { val s = new java.net.ServerSocket(0); try s.getLocalPort finally s.close() }
    val network = new net.ServerNetwork(port, gs.identity.context, () => gs.tcpHandler(), () => gs.udpHandler(), gs.outbox)
    network.bind()
    val group = new io.netty.channel.nio.NioEventLoopGroup(1)
    try {
      val session = new TestSession(server = gs)
      session.join("carol")
      val report = bytes(300000, 11)
      val reportId = UUID.randomUUID()
      session.received()
      session.send(n => ProblemReportPacket.begin(n, session.playerId, reportId, report.length, crcOf(report), null,
        ProblemReportKind.GENERAL))
      val ticket = session.received().collect { case p: ProblemReportPacket => p }.head.getTicket

      val frames = new java.util.concurrent.LinkedBlockingQueue[ProblemReportPacket]()
      val ssl = io.netty.handler.ssl.SslContextBuilder.forClient()
        .trustManager(io.netty.handler.ssl.util.InsecureTrustManagerFactory.INSTANCE).protocols("TLSv1.3").build()
      val ch = new io.netty.bootstrap.Bootstrap().group(group)
        .channel(classOf[io.netty.channel.socket.nio.NioSocketChannel])
        .handler(new io.netty.channel.ChannelInitializer[io.netty.channel.socket.SocketChannel] {
          def initChannel(c: io.netty.channel.socket.SocketChannel): Unit = c.pipeline()
            .addLast(ssl.newHandler(c.alloc(), "127.0.0.1", port))
            .addLast(new LengthFieldBasedFrameDecoder(Constants.PACKET_SIZE + 2, 0, 2, 0, 2))
            .addLast(new io.netty.channel.SimpleChannelInboundHandler[ByteBuf] {
              def channelRead0(ctx: io.netty.channel.ChannelHandlerContext, b: ByteBuf): Unit = {
                val payload = new Array[Byte](Constants.PACKET_PAYLOAD_SIZE)
                b.readBytes(payload)
                frames.add(PacketSerializer.deserialize(payload).asInstanceOf[ProblemReportPacket])
              }
            })
        }).connect("127.0.0.1", port).sync().channel()
      assertTrue(ch.pipeline().get(classOf[io.netty.handler.ssl.SslHandler]).handshakeFuture().await(5000))
      ch.writeAndFlush(uploadLogin(ticket, reportId)).sync()
      val goAhead = frames.poll(5, TimeUnit.SECONDS)
      assertNotNull("told to send", goAhead)
      assertEquals(ProblemReportAction.READY, goAhead.getAction)
      report.grouped(20000).foreach(c => ch.write(Unpooled.wrappedBuffer(c)))
      ch.flush()
      val told = frames.poll(10, TimeUnit.SECONDS)
      assertNotNull("told it was kept", told)
      assertEquals(ProblemReportAction.ACCEPTED, told.getAction)
      assertTrue("and the server closed it", ch.closeFuture().await(5000))
      assertArrayEquals(report, Files.readAllBytes(keptFolders(own).head.resolve(ProblemReports.ReportZip)))
    } finally {
      group.shutdownGracefully()
      network.stop()
      gs.reports.shutdown()
      deleteAll(own)
    }
  }

  @Test def aReportGoesFromASessionToTheFolderThroughTheServer(): Unit = {
    val own = Files.createTempDirectory("problem-reports-server")
    val gs = new GameServer(0, reportsFolder = own)
    try {
      val session = new TestSession(server = gs)
      session.join("bob")
      val matchId = UUID.randomUUID()
      val journal = new MatchJournal(matchId)
      journal.note(session.playerId, "bob", "refused something, for the test")
      gs.finishedJournals.keep(journal)
      val report = bytes(70000, 9)
      val reportId = UUID.randomUUID()
      session.received()
      session.send(n => ProblemReportPacket.begin(n, session.playerId, reportId, report.length, crcOf(report), matchId,
        ProblemReportKind.CRASH))
      val ready = session.received().collect { case p: ProblemReportPacket => p }
      assertEquals(Seq(ProblemReportAction.READY), ready.map(_.getAction))

      val ch = new PeerChannel("127.0.200.1")
      ch.pipeline().addLast(new LengthFieldBasedFrameDecoder(Constants.PACKET_SIZE + 2, 0, 2, 0, 2), new LengthFieldPrepender(2),
        gs.tcpHandler())
      ch.pipeline().fireChannelActive()
      ch.writeInbound(uploadLogin(ready.head.getTicket, reportId))
      assertEquals(Seq(ProblemReportAction.READY), framesFrom(ch).map(_.getAction))
      report.grouped(16384).foreach(c => ch.writeInbound(Unpooled.wrappedBuffer(c)))
      // Kept on a thread of its own
      var told = Seq.empty[ProblemReportPacket]
      val deadline = System.currentTimeMillis() + 10000
      while (told.isEmpty && System.currentTimeMillis() < deadline) { Thread.sleep(20); told = framesFrom(ch) }
      assertEquals(Seq(ProblemReportAction.ACCEPTED), told.map(_.getAction))
      val dir = keptFolders(own).head
      assertArrayEquals(report, Files.readAllBytes(dir.resolve(ProblemReports.ReportZip)))
      val json = JsonParser.parseString(new String(Files.readAllBytes(dir.resolve(ProblemReports.ServerJson)), StandardCharsets.UTF_8)).getAsJsonObject
      assertEquals("bob", json.get("account").getAsString)
      assertEquals("crash", json.get("kind").getAsString)
      assertTrue(new String(Files.readAllBytes(dir.resolve(ProblemReports.MatchJournalTxt)), StandardCharsets.UTF_8)
        .contains("refused something, for the test"))
    } finally {
      gs.reports.shutdown()
      deleteAll(own)
    }
  }
}
