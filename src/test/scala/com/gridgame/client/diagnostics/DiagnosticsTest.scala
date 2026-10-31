package com.gridgame.client.diagnostics

import com.gridgame.common.diagnostics.Recording
import com.gridgame.common.diagnostics.Recording.{IncidentKind, NoticedKind, NoticedRecord, SegmentReader}
import com.gridgame.common.model.Position
import com.gridgame.common.protocol._
import org.junit.Assert._
import org.junit.{After, Test}

import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, StandardOpenOption}
import java.util.UUID

/**
 * The client's diagnostics as a whole (Diagnostics): the run that never closed found at the next start,
 * with its log, the JVM's crash file and the match it was recording; a match recorded from the packet
 * that starts it to its end; the last match kept for a report until the next starts (or a report holds
 * it); the same trouble counted rather than repeated; ten marks a match; a second client keeping a
 * folder of its own.
 */
class DiagnosticsTest {
  private val me = UUID.fromString("0f1e2d3c-4b5a-6978-8796-a5b4c3d2e1f0")

  @After def reset(): Unit = Diagnostics.resetForTest()

  private def dir(): Path = Files.createTempDirectory("diagnostics")
  private def install(d: Path): Unit = Diagnostics.install(Nil, Some(d), teeConsole = false)

  private def gameStarting: Array[Byte] =
    new LobbyActionPacket(1, me, Packet.getCurrentTimestamp, LobbyAction.GAME_STARTING, 7.toShort, 0.toByte, 5.toByte,
      2.toByte, 8.toByte, 0.toByte, "Lobby", 0.toByte, 0.toByte, 2.toByte).serialize()
  private def update(n: Int): Array[Byte] =
    new PlayerUpdatePacket(n, me, Packet.getCurrentTimestamp, new Position(n, 1), 0, 100, 0, 0, 0.toByte, 0.toByte, 0).serialize()

  private def records(r: MatchRecord): Vector[Recording.Record] =
    r.segments.toVector.flatMap { s =>
      val in = Files.newInputStream(s)
      try new SegmentReader(in).records.toVector finally in.close()
    }

  @Test def aRunThatNeverClosedIsFoundAtTheNextStartWithWhatItLeft(): Unit = {
    val d = dir()
    val cwd = Files.createDirectories(d.resolve("started-from"))
    Files.writeString(d.resolve("running.properties"),
      s"pid=424242\nstartedAt=1793000000000\nlastSeenAt=1793000060000\ninMatch=true\nversion=1.0 (abc)\ncwd=${cwd.toString.replace("\\", "\\\\")}\n")
    val hsErr = Files.writeString(cwd.resolve("hs_err_pid424242.log"), "# A fatal error has been detected by the Java Runtime Environment")
    val left = Files.createDirectories(d.resolve("match").resolve("20261030-120000-000"))
    Files.write(left.resolve("seg-0000.rec"), Array[Byte](1, 2, 3))
    Files.writeString(d.resolve("client.log"), "the run that crashed\n")

    install(d)
    val run = Diagnostics.previousRun.getOrElse(throw new AssertionError("the crashed run wasn't found"))
    assertEquals(424242L, run.pid)
    assertEquals(1793000060000L, run.lastSeenAtMs)
    assertTrue(run.inMatch)
    assertEquals(Some(hsErr), run.hsErr)
    assertEquals("its match, kept for its report", Some(left), run.recording)
    assertTrue(Files.isDirectory(left))
    assertEquals("its log, moved aside", "the run that crashed", Files.readAllLines(run.log.get).get(0))
    val marker = Files.readString(d.resolve("running.properties"))
    assertTrue("this run's marker in its place", marker.contains(s"pid=${ProcessHandle.current().pid()}"))

    Diagnostics.forgetPreviousRun()
    assertEquals(None, Diagnostics.previousRun)
    assertFalse("its recording goes once its report is done with", Files.exists(left))
  }

  @Test def aRunThatClosedProperlyLeavesNothingToOffer(): Unit = {
    val d = dir()
    install(d)
    assertEquals(None, Diagnostics.previousRun)
    Diagnostics.packetTap.received(gameStarting, tcp = true)
    val matchDir = Diagnostics.lastMatch.get.dir
    Diagnostics.shutdown()
    assertFalse("the marker is taken down", Files.exists(d.resolve("running.properties")))
    assertFalse("the match is let go", Files.exists(matchDir))
    Diagnostics.resetForTest()
    install(d)
    assertEquals(None, Diagnostics.previousRun)
  }

  @Test def aMatchIsRecordedFromThePacketThatStartsItToItsEnd(): Unit = {
    install(dir())
    assertNull("nothing recorded outside a match", Diagnostics.recorder)
    Diagnostics.packetTap.received(update(1), tcp = false)
    Diagnostics.packetTap.received(gameStarting, tcp = true)
    assertNotNull(Diagnostics.recorder)
    Diagnostics.packetTap.received(update(2), tcp = false)
    Diagnostics.packetTap.sent(new PlayerLeavePacket(3, me).serialize(), tcp = true)
    Diagnostics.noteMatch("map", "meadow.json")
    Diagnostics.event("focus: lost")
    Diagnostics.matchEnded("finished")
    assertNull(Diagnostics.recorder)

    val r = Diagnostics.lastMatch.getOrElse(throw new AssertionError("the match wasn't kept"))
    assertEquals("finished", r.ended)
    assertFalse(r.isRecording)
    val back = records(r)
    val packets = back.filter(x => x.kind == Recording.PacketIn || x.kind == Recording.PacketOut)
    assertArrayEquals("the start is the first packet in it", gameStarting, packets.head.payload)
    assertArrayEquals(update(2), packets(1).payload)
    assertEquals(Recording.PacketOut, packets(2).kind)
    assertTrue(back.exists(x => x.kind == Recording.Event && x.text == "focus: lost"))
    assertTrue(back.exists(x => x.kind == Recording.Meta && x.text.contains("meadow.json")))
    val info = Files.readString(r.dir.resolve("match.json"), StandardCharsets.UTF_8)
    assertTrue("what is known of it beside it, for a run after a crash", info.contains("meadow.json") && info.contains("finished"))
  }

  @Test def theNextMatchLetsTheLastGoUnlessAReportHoldsIt(): Unit = {
    install(dir())
    Diagnostics.packetTap.received(gameStarting, tcp = true)
    Diagnostics.matchEnded("finished")
    val first = Diagnostics.lastMatch.get
    Diagnostics.packetTap.received(gameStarting, tcp = true)
    assertFalse("let go as the next starts", Files.exists(first.dir))
    Diagnostics.matchEnded("left")

    val second = Diagnostics.lastMatch.get
    Diagnostics.pin(second)
    Diagnostics.packetTap.received(gameStarting, tcp = true)
    assertTrue("a report being made of it keeps it", Files.exists(second.dir))
    Diagnostics.unpin(second)
    assertFalse("and it goes when the report is done with it", Files.exists(second.dir))
  }

  @Test def theSameTroubleIsCountedNotRepeated(): Unit = {
    install(dir())
    Diagnostics.incidentQuietMs = 300L
    Diagnostics.packetTap.received(gameStarting, tcp = true)
    for (_ <- 0 until 4) Diagnostics.incident(IncidentKind.BadSignature, "tcp signature")
    Diagnostics.incident(IncidentKind.Freeze, "stuck")
    Thread.sleep(400)
    Diagnostics.incident(IncidentKind.BadSignature, "tcp signature")
    Diagnostics.matchEnded("finished")
    val notes = Diagnostics.lastMatch.get.incidents.toArray.map(_.asInstanceOf[IncidentNote]).toSeq
    assertEquals(Seq(IncidentKind.BadSignature, IncidentKind.Freeze, IncidentKind.BadSignature), notes.map(_.kind))
    assertTrue("the quiet ones counted in the next", notes(2).detail.contains("3 more"))
  }

  @Test def aMatchTakesTenMarksAtMost(): Unit = {
    install(dir())
    assertEquals("no match, no mark", 0, Diagnostics.markMoment(null, 0, 0))
    Diagnostics.packetTap.received(gameStarting, tcp = true)
    val numbers = (0 until 12).map(_ => Diagnostics.markMoment(null, 0, 0))
    assertEquals((1 to Diagnostics.MaxMarks) ++ Seq(0, 0), numbers)
    assertTrue("the HUD says it is full", Diagnostics.marksFull)
    Diagnostics.matchEnded("finished")
    val marks = Diagnostics.lastMatch.get.marks.toArray.map(_.asInstanceOf[MarkedMoment]).toSeq
    assertEquals((1 to 10).toSeq, marks.map(_.number))
    assertTrue("each at its own moment", marks.map(_.atMicros).sliding(2).forall(p => p(0) <= p(1)))
  }

  @Test def whatTheClientNoticesIsKeptWhereItBeganAndNotOverAndOver(): Unit = {
    install(dir())
    Diagnostics.noticed(NoticedKind.SnapBack, 300)
    Diagnostics.packetTap.received(gameStarting, tcp = true)
    Thread.sleep(600)
    Diagnostics.noticed(NoticedKind.Hitch, 400, msAgo = 400)
    Diagnostics.noticed(NoticedKind.Hitch, 900, msAgo = 900) // the same kind in a moment: left out
    Diagnostics.noticed(NoticedKind.Died, 3, detail = "Wizard")
    Diagnostics.noticed(NoticedKind.Died, 4, detail = "Bear") // a death never is
    Diagnostics.matchEnded("finished")
    val r = Diagnostics.lastMatch.get
    val notes = r.noticed.toArray.map(_.asInstanceOf[NoticedMoment]).toSeq
    assertEquals("none outside a match, the second hitch left out", Seq(NoticedKind.Hitch, NoticedKind.Died, NoticedKind.Died), notes.map(_.kind))
    val hitch = notes.head
    assertEquals(400, hitch.a)
    assertTrue(s"placed where it began (${hitch.atMicros}us)", hitch.atMicros >= 100000L && hitch.atMicros < 400000L)
    assertEquals("Bear", notes(2).detail)
    val inRecording = records(r).filter(_.kind == Recording.Noticed)
    assertEquals(3, inRecording.size)
    assertEquals(NoticedKind.Died, inRecording(1).payload(NoticedRecord.Kind).toInt)
    assertEquals(3, Recording.int(inRecording(1).payload, NoticedRecord.A))
    assertEquals("Wizard", new String(inRecording(1).payload, NoticedRecord.Size, inRecording(1).payload.length - NoticedRecord.Size, StandardCharsets.UTF_8))
    assertTrue(r.durationMicros >= 600000L)
  }

  @Test def theServerGoingQuietIsNoticedFromWhereItWentQuiet(): Unit = {
    install(dir())
    Diagnostics.packetTap.received(gameStarting, tcp = true)
    Diagnostics.packetTap.received(update(1), tcp = false)
    Thread.sleep(1700)
    Diagnostics.packetTap.received(update(2), tcp = false)
    Diagnostics.matchEnded("finished")
    val quiet = Diagnostics.lastMatch.get.noticed.toArray.map(_.asInstanceOf[NoticedMoment]).toSeq
    assertEquals(Seq(NoticedKind.ServerQuiet), quiet.map(_.kind))
    assertTrue(s"${quiet.head.a}ms", quiet.head.a >= 1600 && quiet.head.a < 3000)
    assertTrue("placed at the last packet before it", quiet.head.atMicros < 200000L)
  }

  @Test def theSecondsBeforeAMarkOrAnIncidentAreKeptBesideIt(): Unit = {
    install(dir())
    Diagnostics.packetTap.received(gameStarting, tcp = true)
    val t0 = System.nanoTime()
    val frames = (0 until 4).map(i => PastFrame(t0 + i * 250000000L, Array[Byte](-1, -40, i.toByte)))
    Diagnostics.markMoment(null, 0, 0, frames)
    Diagnostics.incident(IncidentKind.Freeze, "stuck", frames.take(2))
    Diagnostics.screenshotsWritten()
    Diagnostics.matchEnded("finished")
    val r = Diagnostics.lastMatch.get
    val mark = r.marks.get(0)
    assertEquals(4, mark.frames.size)
    assertTrue(mark.frames.forall(f => Files.isRegularFile(f.path) && f.path.getParent.getFileName.toString == "mark-1"))
    assertEquals("named by when they were drawn", mark.frames.map(f => f"${f.atMicros / 1000}%08d.jpg"), mark.frames.map(_.path.getFileName.toString))
    assertEquals(750000L, mark.frames(3).atMicros - mark.frames(0).atMicros, 1000L)
    assertArrayEquals(Array[Byte](-1, -40, 2), Files.readAllBytes(mark.frames(2).path))
    val incident = r.incidents.get(0)
    assertEquals(2, incident.frames.size)
    assertEquals("incident-1", incident.frames.head.path.getParent.getFileName.toString)
  }

  @Test def aRecordingIsLetGoOnceItsReportIsSent(): Unit = {
    install(dir())
    Diagnostics.packetTap.received(gameStarting, tcp = true)
    Diagnostics.matchEnded("finished")
    val r = Diagnostics.lastMatch.get
    Diagnostics.pin(r)
    Diagnostics.discard(r)
    assertFalse(Files.exists(r.dir))
    assertEquals("nothing left to report on", None, Diagnostics.lastMatch)
  }

  @Test def aSecondClientOnTheMachineKeepsAFolderOfItsOwn(): Unit = {
    val d = dir()
    val other = FileChannel.open(d.resolve(".lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE)
    val held = other.lock()
    try {
      install(d)
      assertEquals(d.resolve("instances").resolve(ProcessHandle.current().pid().toString), Diagnostics.folder)
      assertFalse("the first client's marker isn't this one's", Files.exists(d.resolve("running.properties")))
    } finally { held.release(); other.close() }
  }
}
