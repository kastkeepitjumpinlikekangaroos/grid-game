package com.gridgame.tools

import com.gridgame.common.diagnostics.Recording
import com.gridgame.common.diagnostics.Recording.{FrameRecord, InputDevice, InputRecord, StateEffects, StateRecord}
import com.gridgame.common.model.{CharacterId, Position}
import com.gridgame.common.protocol._
import org.junit.Assert._
import org.junit.Test

import java.awt.image.BufferedImage
import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import java.util.UUID
import java.util.zip.{Deflater, ZipEntry, ZipOutputStream}
import javax.imageio.ImageIO
import scala.jdk.CollectionConverters._

/**
 * The report tool reads a report as the server keeps one: a synthetic match report (two segments with
 * the ones between them dropped, every kind of record, a record of a kind it doesn't know, a record cut
 * short, a redacted packet and one that won't decode, a name in the zip that would climb out of the
 * folder) into its summary, its timeline (whole, about a moment, about a mark), its tables and its
 * network numbers; and a bare zip, an unpacked one, and a report with no recording. And a report as the
 * client writes one now: compressed segments (one the client closed, one it stopped writing partway),
 * the moments it noticed for itself, the moment the player picked, and pictures before a mark and an
 * incident (one of them not a picture at all), tiled into contact sheets.
 */
class ReportToolTest {
  private val tmp: Path = Files.createTempDirectory(
    Paths.get(sys.env.getOrElse("TEST_TMPDIR", System.getProperty("java.io.tmpdir"))), "report-tool")

  private val us = UUID.fromString("aaaaaaaa-1111-2222-3333-444444444444")
  private val zed = UUID.fromString("bbbbbbbb-1111-2222-3333-444444444444")
  private val matchId = UUID.fromString("0123abcd-89ab-cdef-0123-456789abcdef")
  private val startedAt = 1793000000123L

  /** A segment being written as the client wrote one before it compressed them: version 1. */
  private final class Segment(number: Int) {
    private val bytes = new ByteArrayOutputStream()
    private val head = new Array[Byte](Recording.SegmentHeaderSize)
    Recording.writeSegmentHeader(head, 0, number, startedAt, version = 1)
    bytes.write(head)

    def add(seconds: Double, kind: Int, payload: Array[Byte], flags: Int = 0): Segment = {
      val h = new Array[Byte](Recording.RecordHeaderSize)
      Recording.writeRecordHeader(h, 0, kind, flags, payload.length, Math.round(seconds * 1e6 / 10))
      bytes.write(h)
      bytes.write(payload)
      this
    }

    /** A record whose header says 64 bytes, cut off after 10 of them. */
    def cutShort(seconds: Double): Segment = {
      val h = new Array[Byte](Recording.RecordHeaderSize)
      Recording.writeRecordHeader(h, 0, Recording.PacketIn, Recording.FlagTcp, 64, Math.round(seconds * 1e6 / 10))
      bytes.write(h)
      bytes.write(new Array[Byte](10))
      this
    }

    def result: Array[Byte] = bytes.toByteArray
  }

  /**
   * A segment as the client writes one now (version 2): its records one raw DEFLATE stream, flushed
   * (SYNC_FLUSH) after each batch as the recorder flushes each time it writes. `closed` ends it with the
   * stream's end mark, as a segment the client closed; `open` leaves it as one the client stopped writing.
   */
  private final class CompressedSegment(number: Int) {
    private val out = new ByteArrayOutputStream()
    private val head = new Array[Byte](Recording.SegmentHeaderSize)
    Recording.writeSegmentHeader(head, 0, number, startedAt)
    out.write(head)
    private val deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true)
    private val batch = new ByteArrayOutputStream()
    private val chunk = new Array[Byte](1 << 16)

    def add(seconds: Double, kind: Int, payload: Array[Byte], flags: Int = 0): CompressedSegment = {
      val h = new Array[Byte](Recording.RecordHeaderSize)
      Recording.writeRecordHeader(h, 0, kind, flags, payload.length, Math.round(seconds * 1e6 / 10))
      batch.write(h)
      batch.write(payload)
      this
    }

    /** What has been added since the last flush, out and flushed to a byte boundary. */
    def flush(): CompressedSegment = {
      deflater.setInput(batch.toByteArray)
      batch.reset()
      while (!deflater.needsInput()) out.write(chunk, 0, deflater.deflate(chunk, 0, chunk.length, Deflater.NO_FLUSH))
      var n = deflater.deflate(chunk, 0, chunk.length, Deflater.SYNC_FLUSH)
      out.write(chunk, 0, n)
      while (n == chunk.length) { n = deflater.deflate(chunk, 0, chunk.length, Deflater.SYNC_FLUSH); out.write(chunk, 0, n) }
      this
    }

    /** Its bytes so far (flushed). */
    def size: Int = out.size()

    def closed: Array[Byte] = {
      flush()
      deflater.finish()
      while (!deflater.finished()) out.write(chunk, 0, deflater.deflate(chunk))
      out.toByteArray
    }

    def open: Array[Byte] = { flush(); out.toByteArray }
  }

  private def utf8(s: String) = s.getBytes(StandardCharsets.UTF_8)

  private def noticedRec(kind: Int, a: Int, b: Int = 0, detail: String = ""): Array[Byte] = {
    val p = new Array[Byte](Recording.NoticedRecord.Size)
    p(Recording.NoticedRecord.Kind) = kind.toByte
    Recording.putInt(p, Recording.NoticedRecord.A, a)
    Recording.putInt(p, Recording.NoticedRecord.B, b)
    p ++ utf8(detail)
  }

  /** A small real JPEG, of a colour of its own. */
  private def jpeg(rgb: Int): Array[Byte] = {
    val image = new BufferedImage(160, 90, BufferedImage.TYPE_INT_RGB)
    for (x <- 0 until 160; y <- 0 until 90) image.setRGB(x, y, if ((x / 20 + y / 20) % 2 == 0) rgb else 0x202020)
    val out = new ByteArrayOutputStream()
    ImageIO.write(image, "jpg", out)
    out.toByteArray
  }

  private def frame(deltaMs: Float, workMs: Float, x: Float, y: Float, keys: Int): Array[Byte] = {
    val b = new Array[Byte](FrameRecord.Size)
    Recording.putFloat(b, FrameRecord.DeltaMs, deltaMs)
    Recording.putFloat(b, FrameRecord.WorkMs, workMs)
    Recording.putFloat(b, FrameRecord.CellX, x)
    Recording.putFloat(b, FrameRecord.CellY, y)
    Recording.putFloat(b, FrameRecord.DrawnX, x + 0.5f)
    Recording.putFloat(b, FrameRecord.DrawnY, y)
    Recording.putFloat(b, FrameRecord.AimX, 20f)
    Recording.putFloat(b, FrameRecord.AimY, 21f)
    Recording.putInt(b, FrameRecord.HeldKeys, keys)
    Recording.putShort(b, FrameRecord.Flags, Recording.FrameFlags.Focused)
    b(FrameRecord.Quality) = 2
    b(FrameRecord.Aiming) = -1
    b
  }

  private def state(health: Int, effects: Int, qLeftMs: Int, deaths: Int): Array[Byte] = {
    val b = new Array[Byte](StateRecord.Size)
    Recording.putShort(b, StateRecord.Health, health)
    Recording.putShort(b, StateRecord.MaxHealth, 100)
    Recording.putInt(b, StateRecord.Effects, effects)
    Recording.putInt(b, StateRecord.QLeftMs, qLeftMs)
    Recording.putShort(b, StateRecord.Deaths, deaths)
    Recording.putShort(b, StateRecord.RoundTripMs, 45)
    Recording.putShort(b, StateRecord.Players, 1)
    b
  }

  private def input(device: Int, code: Int, mods: Int): Array[Byte] = {
    val b = new Array[Byte](InputRecord.Size)
    b(InputRecord.Device) = device.toByte
    b(InputRecord.Action) = 1
    Recording.putShort(b, InputRecord.Code, code)
    b(InputRecord.Mods) = mods.toByte
    b
  }

  private def textAfter(prefix: Array[Byte], s: String): Array[Byte] = prefix ++ utf8(s)

  private def redacted(p: Packet): Array[Byte] = {
    val b = p.serialize()
    java.util.Arrays.fill(b, 21, b.length, 0.toByte)
    b
  }

  private val wizard = CharacterId.Wizard.id

  private def segmentZero: Array[Byte] = new Segment(0)
    .add(0.000, Recording.Meta, utf8("""{"recording":"match"}"""))
    .add(0.001, Recording.PacketIn, new LobbyActionPacket(1, us, LobbyAction.GAME_STARTING, 3.toShort, 4.toByte, 5.toByte,
      2.toByte, 8.toByte, 1.toByte, "Bob's lobby").serialize(), Recording.FlagTcp)
    .add(0.002, Recording.PacketIn, new GameEventPacket(2, new UUID(0L, 0L), Packet.getCurrentTimestamp, GameEvent.MATCH_INFO,
      gameId = 3.toShort, targetId = matchId).serialize(), Recording.FlagTcp)
    .add(0.003, Recording.PacketIn, new WorldInfoPacket(3, "the_meadow.json").serialize(), Recording.FlagTcp)
    .add(0.0035, Recording.PacketIn, new PlayerUpdatePacket(4, zed, new Position(40, 40), 0).serialize())
    .add(0.004, Recording.PacketIn, new PlayerJoinPacket(5, zed, new Position(40, 41), 0xFF336699, "Zed", 100, wizard).serialize(), Recording.FlagTcp)
    .add(0.005, Recording.PacketIn, new PlayerJoinPacket(6, us, new Position(12, 30), 0xFF993366, "Bob", 100, wizard).serialize(), Recording.FlagTcp)
    .add(0.100, Recording.State, state(100, 0, 0, 0))
    .add(0.500, Recording.Frame, frame(16.7f, 4f, 12f, 30f, 0))
    .add(1.000, Recording.PacketOut, new HeartbeatPacket(7, us).serialize())
    .add(1.045, Recording.PacketIn, new HeartbeatPacket(8, us).serialize(), Recording.FlagTcp)
    .add(1.200, Recording.PacketOut, new PlayerUpdatePacket(9, us, new Position(12, 30), 0).serialize())
    .add(1.500, Recording.Input, input(InputDevice.Keyboard, 81, 0x01))
    .add(1.600, Recording.Input, input(InputDevice.Keyboard, -1, 0))
    .add(1.700, Recording.Input, input(InputDevice.Mouse, 0, 0))
    .add(2.000, Recording.PacketIn, new ProjectilePacket(10, zed, 1, 12.5f, 30.25f, 0, 77, 1f, 0f, ProjectileAction.SPAWN).serialize())
    .add(2.030, Recording.PacketIn, new ProjectilePacket(11, zed, 2, 13.5f, 30.25f, 0, 77, 1f, 0f, ProjectileAction.MOVE).serialize())
    .add(2.500, Recording.Frame, frame(120f, 95f, 12f, 30f, Recording.Keys.W | Recording.Keys.Q))
    .add(3.000, Recording.State, state(80, StateEffects.Burning, 4000, 0))
    .add(3.200, Recording.PacketOut, redacted(new ChatMessagePacket(12, us, 0, ChatScope.GAME, "my secret")),
      Recording.FlagTcp | Recording.FlagRedacted)
    .add(3.300, Recording.PacketIn, { val b = new Array[Byte](64); b(0) = 0x7F; b }, Recording.FlagTcp)
    .add(3.400, Recording.Log, utf8("NetworkThread: something \u001b[31mred"))
    .add(3.500, Recording.Event, utf8("window: 2880x1800"))
    .add(5.000, Recording.Mark, textAfter(Array[Byte](0, 1), "mark-1.jpg"))
    .add(5.500, Recording.Incident, textAfter(Array(Recording.IncidentKind.Freeze.toByte), "the FX thread didn't answer for 3.2s"))
    .add(6.000, Recording.Gap, { val b = new Array[Byte](4); Recording.putInt(b, 0, 3); b })
    .add(6.100, 99, Array[Byte](1, 2, 3))
    // Written last, from another thread, a moment before the records ahead of it
    .add(2.900, Recording.Log, utf8("late line"))
    .result

  private def segmentThree: Array[Byte] = new Segment(3)
    .add(30.000, Recording.State, state(80, 0, 0, 1))
    .add(30.500, Recording.PacketIn, new PlayerUpdatePacket(13, zed, new Position(44, 41), 0).serialize())
    .add(31.000, Recording.Frame, frame(16.6f, 4f, 13f, 30f, 0))
    .cutShort(31.500)
    .result

  private val reportJson =
    s"""{"format":1,"reportId":"9a8b7c6d-5e4f-3a2b-1c0d-efdecdbcab9a","kind":"match","createdAt":"2026-10-31T00:00:00Z",
       |"category":"movement","description":"My dash went through the wall\\nnear the pond","whenInMatch":"0:05",
       |"client":{"version":"1.0","commit":"abc1234","dirty":false,"built":"2026-10-30T12:00:00Z"},
       |"system":{"os":"Mac OS X 26.0 aarch64","java":"21.0.5","cpus":10,"maxHeapMb":768,"locale":"en_GB","language":"en",
       |  "screens":["2880x1800 @2x"],"args":[],"launch":"app","gpuFan":"loud"},
       |"graphics":{"vendor":"Apple","renderer":"Apple M2","version":"4.1","glsl":"4.10","quality":"high","auto":true},
       |"connection":{"server":"game.example:25565","player":"Bob","playerId":"$us"},
       |"match":{"matchId":"$matchId","map":"the_meadow.json","mode":"free-for-all","practice":false,"character":"Wizard",
       |  "durationMinutes":5,"ended":"finished","kills":2,"deaths":1,
       |  "players":[{"id":"$us","name":"Bob","character":"Wizard","team":0,"bot":false}]},
       |"marks":[{"number":1,"atMicros":5000000,"matchTime":"0:05","screenshot":"screenshots/mark-1.jpg"}],
       |"incidents":[{"kind":"freeze","atMicros":5500000,"detail":"the FX thread didn't answer for 3.2s"}],
       |"files":[{"path":"recording/seg-0000.rec","bytes":1}],
       |"futureField":{"x":1}}""".stripMargin

  private def zip(entries: (String, Array[Byte])*): Array[Byte] = {
    val bytes = new ByteArrayOutputStream()
    val z = new ZipOutputStream(bytes)
    entries.foreach { case (name, b) => z.putNextEntry(new ZipEntry(name)); z.write(b); z.closeEntry() }
    z.close()
    bytes.toByteArray
  }

  private def reportEntries: Seq[(String, Array[Byte])] = Seq(
    "report.json" -> utf8(reportJson),
    "recording/seg-0000.rec" -> segmentZero,
    "recording/seg-0003.rec" -> segmentThree,
    "screenshots/mark-1.jpg" -> Array[Byte](0xFF.toByte, 0xD8.toByte, 1, 2),
    "logs/client.log" -> utf8("12:00:00.000 [main] Grid Game 1.0\n"),
    "crash/hs_err_pid123.log" -> utf8("# A fatal error has been detected\n"),
    "../evil.txt" -> utf8("climbed out"))

  /** The server's folder for the report: the zip, its note, its journal of the match. */
  private def serverFolder(): Path = {
    val folder = Files.createDirectories(tmp.resolve("problem_reports/2026-10-31/GG-TEST1234"))
    Files.write(folder.resolve("report.zip"), zip(reportEntries: _*))
    Files.write(folder.resolve("server.json"), utf8(
      s"""{"code":"GG-TEST1234","reportId":"9a8b7c6d-5e4f-3a2b-1c0d-efdecdbcab9a","receivedAt":"2026-10-31T00:00:05Z",
         |"account":"Bob","playerId":"$us","kind":"match","size":1234,"matchId":"$matchId"}""".stripMargin))
    Files.write(folder.resolve("match-journal.txt"), utf8("00:01.000  aaaaaaaa Bob  step refused: movement_speed\n"))
    folder
  }

  private def read(p: Path): String = new String(Files.readAllBytes(p), StandardCharsets.UTF_8)
  private def lines(p: Path): Vector[String] = read(p).split('\n').toVector.filter(_.nonEmpty)
  private def timeOf(line: String): Long = {
    val Array(m, rest) = line.take(9).split(':')
    val Array(s, ms) = rest.split('.')
    (m.toLong * 60000L + s.toLong * 1000L + ms.toLong) * 1000L
  }

  @Test def theSummarySaysWhoWhatAndWhatTheRecordingHolds(): Unit = {
    val out = tmp.resolve("read")
    val summary = ReportTool.run(serverFolder(), out, None).summary
    assertEquals(summary, read(out.resolve("summary.txt")))
    for (expected <- Seq("Problem report GG-TEST1234: a match report from Bob", "Category:      Movement",
      "    | My dash went through the wall", "    | near the pond", "0:05 into the match", "Apple M2",
      "Players (1):", "#1 at 00:05.000 of the recording, 0:05 by the match's clock; screenshot screenshots/mark-1.jpg",
      "freeze  the FX thread didn't answer", "Segments: 0, 3",
      "Missing: segments 1-2 (nothing kept from 00:06.100 to 00:30.000)", "(31.0s, 7.1s of it recorded)",
      "its last 18 bytes were a record cut short", "Skipped: 1 records of kinds this tool doesn't know (kind 99: 1)",
      "Lost: 3 records, in 1 gaps", "gpuFan:", "futureField:", "The server's journal of the match: match-journal.txt (1 lines)",
      "receivedAt:", "crash/hs_err_pid123.log", "(not copied out: its name would climb out of the folder)"))
      assertTrue(s"the summary says $expected:\n$summary", summary.contains(expected))
  }

  @Test def theTimelineIsEveryRecordInTimeOrderInWords(): Unit = {
    val out = tmp.resolve("read")
    ReportTool.run(serverFolder(), out, None)
    val timeline = lines(out.resolve("timeline.txt"))
    val text = timeline.mkString("\n")
    val times = timeline.map(timeOf)
    assertEquals("in time order, the late line among it", times.sorted, times)

    def has(s: String): Unit = assertTrue(s"the timeline says $s:\n$text", text.contains(s))
    has("in tcp    LOBBY_ACTION #1 aaaaaaaa/Bob (us) GAME_STARTING lobby 3 The Meadow (the_meadow.json) 5 min 2/8")
    has(s"GAME_EVENT #2 - MATCH_INFO match $matchId")
    has("WORLD_INFO #3 - world the_meadow.json")
    // Zed by his id until his join names him, by name after
    has("PLAYER_UPDATE #4 bbbbbbbb at (40,40)")
    has("PLAYER_JOIN #5 bbbbbbbb/Zed at (40,41) name \"Zed\" Wizard")
    has("PROJECTILE_UPDATE #10 bbbbbbbb/Zed SPAWN at (12.50,30.25) #77")
    has("out udp   HEARTBEAT #7 aaaaaaaa/Bob (us)")
    has("key       Q down (Shift)")
    has("key       (typed into chat: not kept)")
    has("mouse     left down")
    has("frame     HITCH 120.0ms since the last (this one took 95.0ms) at (12,30) quality low holding W+Q")
    assertEquals("only the hitch of all the frames", 1, timeline.count(_.contains("  frame ")))
    has("state     health 100/100 Q ready")
    has("state     health 100 -> 80, +burning, Q cooling 4.0s")
    has("state     -burning, deaths 0 -> 1, Q ready")
    has("CHAT_MESSAGE #12 aaaaaaaa/Bob (us) (redacted: what it carried is the player's own)")
    assertFalse("nothing of a redacted packet", text.contains("my secret"))
    has("type 0x7f")
    has("undecodable")
    has("log       NetworkThread: something ·[31mred")
    assertFalse("a terminal's escape codes don't get through", text.contains("\u001b"))
    has("event     window: 2880x1800")
    has(">>> MARK 1 screenshot screenshots/mark-1.jpg")
    has("!!! FREEZE the FX thread didn't answer for 3.2s")
    has("--- GAP   3 records lost here")
    has("===       segments 1-2 are missing")
    has("===       segment 3")
    assertTrue("the late line between the hitch and the next state",
      text.indexOf("HITCH") < text.indexOf("late line") && text.indexOf("late line") < text.indexOf("+burning"))
    assertFalse("a kind this tool doesn't know is skipped", text.contains("kind99"))
  }

  @Test def aWindowShowsOnlyItsStretchAndEveryMarkGetsOne(): Unit = {
    val out = tmp.resolve("read")
    ReportTool.run(serverFolder(), out, Some(ReportTool.Around(ReportTool.parseMoment("0:31"), 1.0)))
    val around = lines(out.resolve("timeline.txt"))
    assertEquals("# around 00:31.000: 00:30.000 to 00:32.000", around.head)
    assertTrue(around.tail.forall(l => timeOf(l) >= 30000000L && timeOf(l) <= 32000000L))
    val text = around.mkString("\n")
    assertTrue("the first state in a window is shown whole", text.contains("state     health 80/100"))
    assertTrue("players named from before the window", text.contains("PLAYER_UPDATE #13 bbbbbbbb/Zed at (44,41)"))
    assertTrue(text.contains("segments 1-2 are missing"))

    val mark = lines(out.resolve("timeline-mark-1.txt"))
    assertEquals("# mark 1: 00:00.000 to 00:15.000", mark.head)
    assertTrue(mark.exists(_.contains(">>> MARK 1")))
    assertFalse("nothing from half a minute on", mark.exists(_.contains("#13")))

    ReportTool.run(serverFolder(), out, Some(ReportTool.AtMark(1, 0.5)))
    val atMark = lines(out.resolve("timeline.txt"))
    assertEquals("# mark 1: 00:04.500 to 00:05.500", atMark.head)
    assertEquals(Seq(">>> MARK 1", "!!! FREEZE"), atMark.tail.map(_.drop(11).take(10).trim))

    try { ReportTool.run(serverFolder(), out, Some(ReportTool.AtMark(9, 1))); fail("a mark the report hasn't") }
    catch { case e: ReportTool.ReportToolException => assertTrue(e.getMessage, e.getMessage.contains("it has 1")) }
  }

  @Test def theTablesHoldARowARecordAndNetTxtTheConnection(): Unit = {
    val out = tmp.resolve("read")
    ReportTool.run(serverFolder(), out, None)
    val frames = lines(out.resolve("frames.csv"))
    assertEquals("time_s,delta_ms,work_ms,cell_x,cell_y,drawn_x,drawn_y,aim_x,aim_y,keys,flags,quality,aiming", frames.head)
    assertEquals(3, frames.size - 1)
    assertEquals("2.500000,120.000,95.000,12,30,12.500,30.000,20.000,21.000,W+Q,focused,low,", frames(2))
    val states = lines(out.resolve("state.csv"))
    assertEquals(3, states.size - 1)
    assertTrue(states(2), states(2).startsWith("3.000000,80,100,burning,4000,"))
    val packets = lines(out.resolve("packets.csv"))
    assertEquals("every packet record but the one cut short", 14, packets.size - 1)
    assertTrue(packets.exists(_.startsWith("0.004000,in,tcp,PLAYER_JOIN,5,bbbbbbbb/Zed,,40,41,")))
    assertTrue("a field with a comma is quoted", packets.exists(_.contains("\"lobby 3 The Meadow")))

    val net = read(out.resolve("net.txt"))
    for (expected <- Seq("1 answered, 0 not answered", "p50 45.0ms", "PROJECTILE_UPDATE", "3 frames", "1 hitches",
      "the client's own reckoning (its states): p50 45.0ms", "(7.1s of it recorded; nothing of 00:06.100-00:30.000)",
      "tcp: 2.800s from 00:03.300, 2.255s from 00:01.045", "1.000s from 00:30.000"))
      assertTrue(s"net.txt says $expected:\n$net", net.contains(expected))
    assertFalse("the missing segments' stretch is no silence of the server's", net.contains("27.700s"))
  }

  @Test def whatTheReportCarriedIsCopiedOutAndNothingClimbsOutOfTheFolder(): Unit = {
    val folder = serverFolder()
    val out = tmp.resolve("read")
    val written = ReportTool.run(folder, out, None).written
    for (f <- Seq("screenshots/mark-1.jpg", "logs/client.log", "crash/hs_err_pid123.log", "report.json", "server.json",
      "match-journal.txt", "timeline.txt", "timeline-mark-1.txt", "frames.csv", "state.csv", "packets.csv", "net.txt")) {
      assertTrue(s"$f written", Files.isRegularFile(out.resolve(f)))
      assertTrue(s"$f listed", written.contains(f))
    }
    assertArrayEquals(Array[Byte](0xFF.toByte, 0xD8.toByte, 1, 2), Files.readAllBytes(out.resolve("screenshots/mark-1.jpg")))
    val evil = Files.walk(tmp).iterator().asScala.filter(_.getFileName.toString == "evil.txt").toVector
    assertEquals("../evil.txt is written nowhere", Vector.empty, evil)
  }

  @Test def aBareZipAnUnpackedOneAndOneWithNoRecordingAreReadToo(): Unit = {
    // A player's saved copy: the zip alone, read beside itself
    val saved = Files.createDirectories(tmp.resolve("saved"))
    val zipFile = saved.resolve("grid-game-report.zip")
    Files.write(zipFile, zip(reportEntries: _*))
    val (input, out, focus) = ReportTool.parseArgs(Array(zipFile.toString), tmp)
    assertEquals(saved.resolve("grid-game-report-read"), out)
    assertEquals(None, focus)
    val bare = ReportTool.run(input, out, None).summary
    assertTrue(bare, bare.contains("Problem report (no code: the server never had it)"))
    assertTrue(bare, bare.contains("none: not a folder the server kept"))

    // Unpacked into a folder of its own
    val unpacked = Files.createDirectories(tmp.resolve("unpacked"))
    reportEntries.filterNot(_._1.contains("..")).foreach { case (name, b) =>
      val f = unpacked.resolve(name)
      Files.createDirectories(f.getParent)
      Files.write(f, b)
    }
    val fromFolder = ReportTool.run(unpacked, tmp.resolve("unpacked-read"), None).summary
    assertTrue(fromFolder, fromFolder.contains("Segments: 0, 3"))

    // About no match: the menus, say
    val general = Files.createDirectories(tmp.resolve("general"))
    Files.write(general.resolve("report.zip"), zip("report.json" -> utf8("""{"kind":"general","description":"the lobby list is empty"}""")))
    val result = ReportTool.run(general, tmp.resolve("general-read"), None)
    assertTrue(result.summary, result.summary.contains("none (a report about no match)"))
    assertTrue(result.summary, result.summary.contains("    | the lobby list is empty"))
    assertFalse(Files.exists(tmp.resolve("general-read/timeline.txt")))
  }

  @Test def momentsAndOptionsAreReadAsTheUsageSays(): Unit = {
    assertEquals(151000000L, ReportTool.parseMoment("2:31"))
    assertEquals(151500000L, ReportTool.parseMoment("2:31.5"))
    assertEquals(3751000000L, ReportTool.parseMoment("1:02:31"))
    assertEquals(151000000L, ReportTool.parseMoment("151"))
    val (_, out, focus) = ReportTool.parseArgs(Array("--mark=2,4", "r", "--out=elsewhere"), tmp)
    assertEquals(tmp.resolve("elsewhere"), out)
    assertEquals(Some(ReportTool.AtMark(2, 4.0)), focus)
    assertEquals(Some(ReportTool.Around(90000000L, 10.0)), ReportTool.parseArgs(Array("r", "--around=1:30"), tmp)._3)
    for (bad <- Seq(Array("r", "--around=soon"), Array("r", "--mark=one"), Array("r", "--nope"), Array[String](), Array("a", "b")))
      try { ReportTool.parseArgs(bad, tmp); fail(bad.mkString(" ")) } catch { case _: ReportTool.ReportToolException => }
  }

  // ── A report as the client writes one now ─────────────────────────────

  private val noticedKinds = Recording.NoticedKind

  private def compressedReport(): (Path, Array[Byte]) = {
    val zero = new CompressedSegment(0)
      .add(0.000, Recording.Meta, utf8("""{"recording":"match"}"""))
      .add(0.001, Recording.PacketIn, new LobbyActionPacket(1, us, LobbyAction.GAME_STARTING, 3.toShort, 4.toByte, 5.toByte,
        2.toByte, 8.toByte, 1.toByte, "Bob's lobby").serialize(), Recording.FlagTcp)
      .add(0.004, Recording.PacketIn, new PlayerJoinPacket(5, zed, new Position(40, 41), 0xFF336699, "Zed", 100, wizard).serialize(), Recording.FlagTcp)
      .add(0.100, Recording.State, state(100, 0, 0, 0))
      .add(1.000, Recording.PacketOut, new HeartbeatPacket(7, us).serialize())
      .add(1.040, Recording.PacketIn, new HeartbeatPacket(8, us).serialize(), Recording.FlagTcp)
      .flush()
      .add(2.000, Recording.Frame, frame(16.7f, 4f, 12f, 30f, 0))
      .add(4.000, Recording.Noticed, noticedRec(noticedKinds.SnapBack, 320))
      .add(6.000, Recording.Noticed, noticedRec(noticedKinds.ShotUnanswered, 1))
      .add(8.000, Recording.Noticed, noticedRec(noticedKinds.Hitch, 340))
      .add(10.300, Recording.PacketIn, new PlayerUpdatePacket(9, zed, new Position(41, 41), 0).serialize())
      .add(12.500, Recording.Noticed, noticedRec(noticedKinds.ServerQuiet, 2100))
      .add(14.000, Recording.Noticed, noticedRec(noticedKinds.Died, wizard, 0, "Zed"))
      .add(15.000, Recording.Noticed, noticedRec(noticedKinds.Refused, 0, 23500))
      .add(16.000, Recording.Noticed, noticedRec(noticedKinds.PingSpike, 450))
      .flush()
      .add(20.000, Recording.Mark, textAfter(Array[Byte](0, 1), "mark-1.jpg"))
      .add(22.000, Recording.Incident, textAfter(Array(Recording.IncidentKind.Freeze.toByte), "not answering for 4200ms"))
      .add(23.000, Recording.Frame, frame(16.6f, 4f, 13f, 30f, 0))
      .closed
    // The next segment the client stopped writing: its last batch cut part of the way through
    val one = new CompressedSegment(1)
      .add(24.000, Recording.State, state(80, 0, 0, 1))
      .add(24.500, Recording.PacketIn, new PlayerUpdatePacket(13, zed, new Position(44, 41), 0).serialize())
      .flush()
    val flushedAt = one.size
    (0 until 200).foreach(i => one.add(25.0 + i * 0.01, Recording.PacketIn,
      new PlayerUpdatePacket(14 + i, zed, new Position(44, 41 + i % 3), 0).serialize()))
    val whole = one.open
    val cut = java.util.Arrays.copyOf(whole, flushedAt + (whole.length - flushedAt) * 6 / 10)

    val json =
      s"""{"format":1,"reportId":"1a2b3c4d-5e4f-3a2b-1c0d-efdecdbcab9a","kind":"match","createdAt":"2026-10-31T01:00:00Z",
         |"category":"lag","description":"everything froze, then I was somewhere else",
         |"connection":{"server":"game.example:25565","player":"Bob","playerId":"$us"},
         |"chosenMoment":{"atMicros":12400000,"matchTime":"0:12","source":"noticed","text":"the server said nothing for 2.1s"},
         |"noticed":[{"kind":"server-quiet","atMicros":12500000,"matchTime":"0:12","a":2100,"b":0,"detail":"","text":"x"},
         |  {"kind":"ping-spike","atMicros":30000000,"matchTime":"0:30","a":600,"b":0,"detail":"","text":"a heartbeat took 600ms"}],
         |"marks":[{"number":1,"atMicros":20000000,"matchTime":"0:20","screenshot":"screenshots/mark-1.jpg",
         |  "frames":[{"path":"screenshots/mark-1/00016000.jpg","atMicros":16000000},{"path":"screenshots/mark-1/00017000.jpg","atMicros":17000000},
         |    {"path":"screenshots/mark-1/00018000.jpg","atMicros":18000000},{"path":"screenshots/mark-1/00019000.jpg","atMicros":19000000}]}],
         |"incidents":[{"kind":"freeze","atMicros":22000000,"index":0,"detail":"not answering for 4200ms",
         |  "frames":[{"path":"screenshots/incident-0/00021000.jpg","atMicros":21000000},{"path":"screenshots/incident-0/00021500.jpg","atMicros":21500000}]}]}""".stripMargin
    val folder = Files.createDirectories(tmp.resolve("problem_reports/2026-10-31/GG-SQUEEZED"))
    val zipBytes = zip(
      "report.json" -> utf8(json),
      "recording/seg-0000.rec" -> zero,
      "recording/seg-0001.rec" -> cut,
      "screenshots/mark-1.jpg" -> jpeg(0xC03030),
      "screenshots/mark-1/00016000.jpg" -> jpeg(0x3060C0),
      "screenshots/mark-1/00017000.jpg" -> jpeg(0x30C060),
      "screenshots/mark-1/00018000.jpg" -> Array[Byte](1, 2, 3, 4), // not a picture: skipped, and said so
      "screenshots/mark-1/00019000.jpg" -> jpeg(0xC0C030),
      // One report.json doesn't list, found by its name
      "screenshots/mark-1/00019500.jpg" -> jpeg(0xC030C0),
      "screenshots/incident-0/00021000.jpg" -> jpeg(0x30C0C0),
      "screenshots/incident-0/00021500.jpg" -> jpeg(0x808080))
    Files.write(folder.resolve("report.zip"), zipBytes)
    (folder, zero)
  }

  @Test def aCompressedRecordingIsReadAndWhatTheClientNoticedSaid(): Unit = {
    val (folder, zero) = compressedReport()
    val out = tmp.resolve("squeezed-read")
    val result = ReportTool.run(folder, out, None)
    val summary = result.summary

    def says(s: String): Unit = assertTrue(s"the summary says $s:\n$summary", summary.contains(s))
    says("The player says it was around 0:12: the server said nothing for 2.1s, from a moment the client noticed (timeline-chosen.txt)")
    says("What the client noticed (8)")
    says("00:12.500  server-quiet: the server said nothing for 2.1s (from 00:10.400)")
    says("00:04.000  snap-back: the server moved us 3.2 cells, not by a push")
    says("00:06.000  shot-unanswered: our Q got no answer from the server")
    says("00:14.000  died: we died (killed by Zed); the killer played Wizard")
    says("00:15.000  refused: the server refused our R (23.5s of its minute left)")
    says("00:30.000  ping-spike: a heartbeat took 600ms to come back")
    says("By kind:")
    says(s"version 2, ${zero.length} bytes compressed (")
    val segmentLines = summary.split('\n').filter(_.trim.matches("[01]: .*"))
    assertEquals(segmentLines.mkString("\n"), 2, segmentLines.length)
    assertFalse("the segment the client closed isn't cut short", segmentLines(0).contains("cut short"))
    assertTrue("the one it stopped writing is", segmentLines(1).contains("cut short"))
    says("Pictures before marks and incidents (2)")
    says("mark 1: 5 pictures from 00:16.000 to 00:19.500 (4.00s to 0.50s before it): mark-1-frames.png (4 of them); " +
      "couldn't read 1: screenshots/mark-1/00018000.jpg")
    says("incident 0: 2 pictures from 00:21.000 to 00:21.500 (1.00s to 0.50s before it): incident-0-frames.png")

    val timeline = read(out.resolve("timeline.txt"))
    for (line <- Seq("~~~ NOTICED server-quiet: the server said nothing for 2.1s (from 00:10.400)",
      "~~~ NOTICED snap-back: the server moved us 3.2 cells, not by a push", "~~~ NOTICED hitch: a frame came 340ms after the last",
      "~~~ NOTICED died: we died (killed by Zed); the killer played Wizard",
      "*** CHOSEN the player says it was around here: the server said nothing for 2.1s (from a moment the client noticed)",
      ">>> MARK 1 screenshot screenshots/mark-1.jpg  (5 pictures before it: mark-1-frames.png)",
      "!!! FREEZE not answering for 4200ms  (2 pictures before it: incident-0-frames.png)", "PLAYER_UPDATE #13 bbbbbbbb/Zed at (44,41)"))
      assertTrue(s"the timeline says $line:\n$timeline", timeline.contains(line))
    val timelineLines = timeline.split('\n').toVector.filter(_.nonEmpty)
    assertEquals("in time order", timelineLines.map(timeOf).sorted, timelineLines.map(timeOf))
    assertTrue("the chosen moment where it was picked, before the silence's end is noticed",
      timeline.indexOf("*** CHOSEN") < timeline.indexOf("~~~ NOTICED server-quiet"))
    assertTrue("some of the cut segment's last batch read", timelineLines.count(_.contains("PLAYER_UPDATE #")) > 2)

    val chosen = lines(out.resolve("timeline-chosen.txt"))
    assertEquals("# the moment the player picked: 00:02.400 to 00:22.400", chosen.head)
    assertTrue(chosen.exists(_.contains("*** CHOSEN")))
    assertFalse(chosen.exists(_.contains("00:24.500")))

    val csv = lines(out.resolve("noticed.csv"))
    assertEquals("time_s,kind,a,b,detail,text", csv.head)
    assertEquals(8, csv.size - 1)
    assertTrue(csv.mkString("\n"), csv.contains("12.500000,server-quiet,2100,0,,the server said nothing for 2.1s"))
    assertTrue(csv.exists(_.startsWith("14.000000,died,")))

    // The contact sheets: as many tiles as pictures that could be read, at the tool's own size
    val sheet = ImageIO.read(out.resolve("mark-1-frames.png").toFile)
    assertNotNull(sheet)
    assertEquals(ReportFrames.sheetSize(4, ReportFrames.ThumbWidth * 90 / 160), (sheet.getWidth, sheet.getHeight))
    val incidentSheet = ImageIO.read(out.resolve("incident-0-frames.png").toFile)
    assertEquals(ReportFrames.sheetSize(2, ReportFrames.ThumbWidth * 90 / 160), (incidentSheet.getWidth, incidentSheet.getHeight))
    assertTrue("the pictures copied out", Files.isRegularFile(out.resolve("screenshots/mark-1/00019500.jpg")))
    for (f <- Seq("mark-1-frames.png", "incident-0-frames.png", "noticed.csv", "timeline-chosen.txt"))
      assertTrue(s"$f listed", result.written.contains(f))

    // The tables and the connection's numbers read a compressed recording as a plain one
    assertTrue(read(out.resolve("net.txt")).contains("1 answered"))
    assertEquals(2, lines(out.resolve("frames.csv")).size - 1)
  }

  @Test def aReportWithNoPicturesOrMomentsSaysSo(): Unit = {
    val out = tmp.resolve("read")
    val summary = ReportTool.run(serverFolder(), out, None).summary
    assertTrue(summary, summary.contains("What the client noticed (0)"))
    assertFalse(summary.contains("Pictures before marks and incidents"))
    assertFalse(summary.contains("The player says it was around"))
    assertFalse(Files.exists(out.resolve("noticed.csv")))
    assertFalse(Files.exists(out.resolve("timeline-chosen.txt")))
    assertTrue("a version 1 segment says so", summary.contains("version 1, "))
  }
}
