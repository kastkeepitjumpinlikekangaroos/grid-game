package com.gridgame.client.diagnostics

import com.gridgame.common.diagnostics.Recording.IncidentKind
import com.gridgame.common.protocol._
import com.google.gson.{JsonObject, JsonParser}
import org.junit.Assert._
import org.junit.{After, Test}

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.UUID
import java.util.zip.{CRC32, ZipFile}
import scala.jdk.CollectionConverters._

/** A report put together (ReportBuilder): what goes into its zip and its report.json, for a match and for
  * a crash; and the outbox keeping it until the server has it (ReportOutbox). */
class ReportBuilderTest {
  private val me = UUID.fromString("0f1e2d3c-4b5a-6978-8796-a5b4c3d2e1f0")

  @After def reset(): Unit = Diagnostics.resetForTest()

  private def dir(): Path = Files.createTempDirectory("reports")

  private def gameStarting: Array[Byte] =
    new LobbyActionPacket(1, me, Packet.getCurrentTimestamp, LobbyAction.GAME_STARTING, 7.toShort, 0.toByte, 5.toByte,
      2.toByte, 8.toByte, 0.toByte, "Lobby", 0.toByte, 0.toByte, 2.toByte).serialize()

  private def contents(zip: Path): Map[String, Array[Byte]] = {
    val z = new ZipFile(zip.toFile)
    try z.entries().asScala.map(e => e.getName -> z.getInputStream(e).readAllBytes()).toMap finally z.close()
  }

  private def json(bytes: Array[Byte]): JsonObject = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject

  private def connection: JsonObject = {
    val o = new JsonObject
    o.addProperty("server", "play.example:25565")
    o.addProperty("player", "bob")
    o
  }

  @Test def aMatchReportHoldsWhatThePlayerSaidAndTheMatch(): Unit = {
    Diagnostics.install(Nil, Some(dir()), teeConsole = false)
    Diagnostics.packetTap.received(gameStarting, tcp = true)
    Diagnostics.noteMatch("map", "meadow.json")
    Diagnostics.noteMatch("matchId", "01234567-89ab-cdef-0123-456789abcdef")
    Diagnostics.markMoment(null, 0, 0)
    Diagnostics.markMoment(null, 0, 0)
    Diagnostics.incident(IncidentKind.Freeze, "not answering for 4200ms")
    Diagnostics.matchEnded("finished")
    val record = Diagnostics.lastMatch.get
    val marks = record.marks.asScala.toVector
    // The first mark's picture, as the screenshot thread would have written it
    val shot = record.dir.resolve("mark-1.jpg")
    Files.write(shot, Array[Byte](-1, -40, -1, -32, 1, 2, 3))
    val withShot = marks.head.copy(screenshot = shot)
    val now = new JsonObject
    now.addProperty("kills", 3)

    val entry = ReportBuilder.build(ReportBuilder.Request(ProblemReportKind.MATCH, "movement", "My dash went through the wall\nby the pond",
      "2:30", Some(record), includeRecording = true, Seq(withShot), None, connection, now), Diagnostics.outbox)

    val zip = Diagnostics.outbox.zip(entry.id)
    assertEquals(Files.size(zip), entry.size)
    val crc = new CRC32
    crc.update(Files.readAllBytes(zip))
    assertEquals("the CRC the server checks it by", crc.getValue.toInt, entry.crc)
    assertEquals(UUID.fromString("01234567-89ab-cdef-0123-456789abcdef"), entry.matchId)
    assertEquals("movement: My dash went through the wall", entry.summary)

    val files = contents(zip)
    assertTrue(files.keySet.toString, Set("report.json", "recording/seg-0000.rec", "screenshots/mark-1.jpg", "logs/client.log").subsetOf(files.keySet))
    assertFalse("the second mark had no picture", files.contains("screenshots/mark-2.jpg"))
    assertArrayEquals(Files.readAllBytes(record.segments.head), files("recording/seg-0000.rec"))

    val r = json(files("report.json"))
    assertEquals(1, r.get("format").getAsInt)
    assertEquals(entry.id.toString, r.get("reportId").getAsString)
    assertEquals("match", r.get("kind").getAsString)
    assertEquals("movement", r.get("category").getAsString)
    assertEquals("My dash went through the wall\nby the pond", r.get("description").getAsString)
    assertEquals("2:30", r.get("whenInMatch").getAsString)
    assertTrue(r.getAsJsonObject("client").has("commit"))
    assertTrue(r.getAsJsonObject("system").get("os").getAsString.nonEmpty)
    assertEquals("play.example:25565", r.getAsJsonObject("connection").get("server").getAsString)
    val m = r.getAsJsonObject("match")
    assertEquals("meadow.json", m.get("map").getAsString)
    assertEquals("finished", m.get("ended").getAsString)
    assertEquals("what the client knew at the end, over what was noted", 3, m.get("kills").getAsInt)
    val mark = r.getAsJsonArray("marks").get(0).getAsJsonObject
    assertEquals(1, mark.get("number").getAsInt)
    assertEquals("screenshots/mark-1.jpg", mark.get("screenshot").getAsString)
    assertTrue(mark.get("matchTime").getAsString.matches("\\d+:\\d\\d"))
    assertEquals("freeze", r.getAsJsonArray("incidents").get(0).getAsJsonObject.get("kind").getAsString)
    val listed = r.getAsJsonArray("files").asScala.map(_.getAsJsonObject.get("path").getAsString).toSet
    assertEquals(files.keySet - "report.json", listed)
  }

  @Test def whatTheGameNoticedAndWhenThePlayerSaysItHappenedGoWithIt(): Unit = {
    Diagnostics.install(Nil, Some(dir()), teeConsole = false)
    Diagnostics.packetTap.received(gameStarting, tcp = true)
    val t0 = System.nanoTime()
    Diagnostics.noticed(com.gridgame.common.diagnostics.Recording.NoticedKind.SnapBack, 320)
    Diagnostics.markMoment(null, 0, 0, (0 until 3).map(i => PastFrame(t0 + i * 250000000L, Array[Byte](1, i.toByte))))
    Diagnostics.incident(IncidentKind.RenderCrash, "boom", Seq(PastFrame(t0, Array[Byte](7))))
    Diagnostics.matchEnded("error")
    val record = Diagnostics.lastMatch.get
    val chosen = ChosenMoment(151250000L, "noticed", "The server moved you 3.2 cells")
    val entry = ReportBuilder.build(ReportBuilder.Request(ProblemReportKind.MATCH, "movement", "", "2:31", Some(record),
      includeRecording = true, record.marks.asScala.toSeq, None, connection, null, chosen = Some(chosen)), Diagnostics.outbox)
    val files = contents(Diagnostics.outbox.zip(entry.id))
    val r = json(files("report.json"))
    val noticed = r.getAsJsonArray("noticed").get(0).getAsJsonObject
    assertEquals("snap-back", noticed.get("kind").getAsString)
    assertEquals(320, noticed.get("a").getAsInt)
    assertEquals("the server moved us 3.2 cells, not by a push", noticed.get("text").getAsString)
    val c = r.getAsJsonObject("chosenMoment")
    assertEquals(("2:31", "noticed", "The server moved you 3.2 cells"), (c.get("matchTime").getAsString, c.get("source").getAsString, c.get("text").getAsString))
    val frames = r.getAsJsonArray("marks").get(0).getAsJsonObject.getAsJsonArray("frames")
    assertEquals(3, frames.size)
    val paths = (0 until 3).map(i => frames.get(i).getAsJsonObject.get("path").getAsString)
    assertTrue(paths.mkString, paths.forall(p => p.matches("screenshots/mark-1/\\d{8}\\.jpg") && files.contains(p)))
    assertArrayEquals(Array[Byte](1, 2), files(paths(2)))
    val incident = r.getAsJsonArray("incidents").get(0).getAsJsonObject
    assertEquals(1, incident.get("index").getAsInt)
    assertTrue(files.contains(incident.getAsJsonArray("frames").get(0).getAsJsonObject.get("path").getAsString))
  }

  @Test def aCrashReportTakesWhatTheRunThatCrashedLeft(): Unit = {
    val d = dir()
    Files.writeString(d.resolve("running.properties"), s"pid=31337\nstartedAt=1\nlastSeenAt=2\ninMatch=true\ncwd=${d.toString.replace("\\", "\\\\")}\n")
    Files.writeString(d.resolve("hs_err_pid31337.log"), "SIGSEGV (0xb) at pc=0x0000000102a3b840")
    Files.writeString(d.resolve("client.log"), "the last thing it said\n")
    val left = Files.createDirectories(d.resolve("match").resolve("20261030-120000-000"))
    Files.write(left.resolve("seg-0000.rec"), Array[Byte](9, 9, 9))
    Files.write(left.resolve("mark-1.jpg"), Array[Byte](1))
    Files.writeString(left.resolve("match.json"), """{"map":"the_cell.json"}""")
    Diagnostics.install(Nil, Some(d), teeConsole = false)
    val run = Diagnostics.previousRun.get

    val entry = ReportBuilder.build(ReportBuilder.Request(ProblemReportKind.CRASH, "crash", "", "", None,
      includeRecording = true, Nil, Some(run), connection, null), Diagnostics.outbox)
    val files = contents(Diagnostics.outbox.zip(entry.id))
    assertEquals("the last thing it said\n", new String(files("logs/previous-client.log"), StandardCharsets.UTF_8))
    assertEquals("SIGSEGV (0xb) at pc=0x0000000102a3b840", new String(files("crash/hs_err_pid31337.log"), StandardCharsets.UTF_8))
    assertArrayEquals(Array[Byte](9, 9, 9), files("recording/seg-0000.rec"))
    assertTrue(files.contains("screenshots/mark-1.jpg"))
    val r = json(files("report.json"))
    assertEquals("crash", r.get("kind").getAsString)
    assertEquals(31337L, r.getAsJsonObject("previousRun").get("pid").getAsLong)
    assertEquals("the match it was in, from what it kept beside it", "the_cell.json", r.getAsJsonObject("match").get("map").getAsString)
  }

  @Test def whatThePlayerLeftOutStaysOut(): Unit = {
    Diagnostics.install(Nil, Some(dir()), teeConsole = false)
    Diagnostics.packetTap.received(gameStarting, tcp = true)
    Diagnostics.markMoment(null, 0, 0)
    Diagnostics.matchEnded("finished")
    val record = Diagnostics.lastMatch.get
    val entry = ReportBuilder.build(ReportBuilder.Request(ProblemReportKind.GENERAL, "other", "the menus were slow", "",
      None, includeRecording = false, Nil, None, connection, null), Diagnostics.outbox)
    val files = contents(Diagnostics.outbox.zip(entry.id))
    assertFalse(files.keys.exists(_.startsWith("recording/")))
    assertFalse(files.keys.exists(_.startsWith("screenshots/")))
    assertEquals("general", json(files("report.json")).get("kind").getAsString)
    assertNull("about no match", entry.matchId)
    assertTrue("and the match is still there for another report", Files.exists(record.dir))
  }

  @Test def theOutboxKeepsAReportUntilTheServerHasIt(): Unit = {
    val outbox = new ReportOutbox(dir())
    val id = UUID.randomUUID()
    Files.createDirectories(outbox.dir)
    Files.write(outbox.zip(id), Array[Byte](1, 2, 3))
    outbox.save(OutboxEntry(id, ProblemReportKind.MATCH, 1000L, 3L, 77, null, "movement: dash"))
    assertEquals(Seq(id), outbox.pending.map(_.id))
    outbox.markFailed(id, "the server is busy", 5000L)
    val failed = outbox.get(id).get
    assertEquals((1, "the server is busy", 5000L), (failed.attempts, failed.lastError, failed.notBeforeMs))
    assertTrue("still waiting", failed.isPending)
    outbox.markSent(id, "GG-7K3F9QX2", now = 9000L)
    assertEquals(Nil, outbox.pending)
    assertFalse("its zip goes once sent", Files.exists(outbox.zip(id)))
    val sent = outbox.get(id).get
    assertEquals((OutboxEntry.Sent, "GG-7K3F9QX2", 9000L), (sent.status, sent.code, sent.sentAtMs))
  }

  @Test def theOutboxLetsTheOldGo(): Unit = {
    val outbox = new ReportOutbox(dir())
    Files.createDirectories(outbox.dir)
    val now = 100L * 24 * 3600 * 1000
    val ids = (0 until 8).map { n =>
      val id = UUID.randomUUID()
      Files.write(outbox.zip(id), Array[Byte](1))
      // the first a month and more old, the rest a minute apart
      outbox.save(OutboxEntry(id, ProblemReportKind.MATCH, if (n == 0) now - ReportOutbox.KeepMs - 1 else now - (8 - n) * 60000L, 1L, 0, null, s"report $n"))
      id
    }
    val sentLongAgo = UUID.randomUUID()
    outbox.save(OutboxEntry(sentLongAgo, ProblemReportKind.MATCH, 0L, 1L, 0, null, "old", status = OutboxEntry.Sent, sentAtMs = now - ReportOutbox.KeepMs - 1))
    val orphan = UUID.randomUUID()
    Files.write(outbox.zip(orphan), Array[Byte](1))
    outbox.tidy(now)
    assertEquals("the newest five of those not too old", ids.drop(3), outbox.pending.map(_.id))
    assertEquals(None, outbox.get(sentLongAgo))
    assertFalse("a zip without a note goes", Files.exists(outbox.zip(orphan)))
    assertFalse(Files.exists(outbox.zip(ids(0))))
  }
}
