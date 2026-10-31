package com.gridgame.client.diagnostics

import com.gridgame.common.diagnostics.Recording
import com.gridgame.common.diagnostics.Recording._
import com.gridgame.common.model.Position
import com.gridgame.common.protocol._
import org.junit.Assert._
import org.junit.Test

import java.lang.management.ManagementFactory
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.UUID

/**
 * A match's recording as the client writes it (MatchRecorder) and the report tool reads it (Recording):
 * every kind of record back as written, nothing of the player's own on disk, a long match's start and
 * its newest part kept, a full buffer saying how much it lost, and a frame recorded allocating nothing.
 */
class MatchRecorderTest {
  private val me = UUID.fromString("0f1e2d3c-4b5a-6978-8796-a5b4c3d2e1f0")

  private def dir(): Path = Files.createTempDirectory("recorder")

  private def read(segments: Seq[Path]): Vector[Record] =
    segments.toVector.flatMap { s =>
      val in = Files.newInputStream(s)
      try new SegmentReader(in).records.toVector finally in.close()
    }

  @Test def everyKindOfRecordComesBackAsItWasWritten(): Unit = {
    val r = new MatchRecorder(dir(), 1793000000000L)
    val update = new PlayerUpdatePacket(7, me, Packet.getCurrentTimestamp, new Position(10, 12), 0, 80, 0, 0, 0.toByte, 0.toByte, 3).serialize()
    r.packet(PacketIn, tcp = false, update)
    r.packet(PacketOut, tcp = true, new PlayerLeavePacket(8, me).serialize())
    r.frame(16.5f, 4.25f, 10f, 12f, 10.5f, 12.25f, 20f, 21.5f, Keys.W | Keys.Q, FrameFlags.Focused | FrameFlags.Moving, 1, 0)
    r.input(InputDevice.Keyboard, 1, 81, 4)
    val s = new StateSample
    s.health = 80; s.maxHealth = 100; s.effects = StateEffects.Burning | StateEffects.Rooted; s.qLeftMs = 4000
    s.kills = 3; s.deaths = 1; s.roundTripMs = 45; s.projectiles = 12; s.players = 5; s.serverMoves = 9; s.queued = 2
    r.state(s)
    r.text(Log, "a line of the log")
    r.text(Event, "focus: lost")
    r.mark(1, "mark-1.jpg")
    r.incident(IncidentKind.Freeze, "not answering for 4200ms")
    val back = read(r.finish())

    assertEquals(Seq(PacketIn, PacketOut, Frame, Input, State, Log, Event, Mark, Incident), back.map(_.kind))
    assertArrayEquals(update, back(0).payload)
    assertEquals("a datagram", 0, back(0).flags & FlagTcp)
    assertEquals(FlagTcp, back(1).flags & FlagTcp)

    val f = back(2).payload
    assertEquals(FrameRecord.Size, f.length)
    assertEquals(16.5f, float(f, FrameRecord.DeltaMs), 0f)
    assertEquals(4.25f, float(f, FrameRecord.WorkMs), 0f)
    assertEquals(12.25f, float(f, FrameRecord.DrawnY), 0f)
    assertEquals(21.5f, float(f, FrameRecord.AimY), 0f)
    assertEquals(Keys.W | Keys.Q, int(f, FrameRecord.HeldKeys))
    assertEquals(FrameFlags.Focused | FrameFlags.Moving, ushort(f, FrameRecord.Flags))
    assertEquals(1, f(FrameRecord.Quality).toInt)

    val i = back(3).payload
    assertEquals((InputDevice.Keyboard, 1, 81, 4), (i(InputRecord.Device).toInt, i(InputRecord.Action).toInt, short(i, InputRecord.Code), i(InputRecord.Mods).toInt))

    val st = back(4).payload
    assertEquals(80, short(st, StateRecord.Health))
    assertEquals(StateEffects.Burning | StateEffects.Rooted, int(st, StateRecord.Effects))
    assertEquals(4000, int(st, StateRecord.QLeftMs))
    assertEquals((3, 1, 45, 12, 5, 9, 2), (short(st, StateRecord.Kills), short(st, StateRecord.Deaths), short(st, StateRecord.RoundTripMs),
      ushort(st, StateRecord.Projectiles), ushort(st, StateRecord.Players), int(st, StateRecord.ServerMoves), ushort(st, StateRecord.Queued)))

    assertEquals("a line of the log", back(5).text)
    assertEquals("focus: lost", back(6).text)
    assertEquals(1, ushort(back(7).payload, 0))
    assertEquals("mark-1.jpg", new String(back(7).payload, 2, back(7).payload.length - 2, StandardCharsets.UTF_8))
    assertEquals(IncidentKind.Freeze, back(8).payload(0).toInt)
    assertEquals("not answering for 4200ms", new String(back(8).payload, 1, back(8).payload.length - 1, StandardCharsets.UTF_8))
    // In the order and at the times they were written
    assertTrue(back.map(_.timeMicros).sliding(2).forall(p => p(0) <= p(1)))
  }

  @Test def whatIsThePlayersOwnIsNeverWritten(): Unit = {
    val token = Array.tabulate[Byte](32)(i => (65 + i % 26).toByte) // letters, easy to find if written
    val secrets = Seq(
      new AuthRequestPacket(1, AuthAction.LOGIN, "bob", "hunter22"),
      new AuthRequestPacket(2, AuthAction.REFRESH, token),
      new SessionTokenPacket(3, me, token),
      new RefreshTokenPacket(4, me, token, 1793000000000L),
      new ChatMessagePacket(5, me, Packet.getCurrentTimestamp, ChatScope.GAME, "meet me by the pond"),
      ProblemReportPacket.ready(6, me, UUID.randomUUID(), token.take(16)))
    val d = dir()
    val r = new MatchRecorder(d, 0L)
    secrets.foreach(p => r.packet(PacketIn, tcp = true, p.serialize()))
    val update = new PlayerUpdatePacket(7, me, Packet.getCurrentTimestamp, new Position(1, 2), 0, 100, 0, 0, 0.toByte, 0.toByte, 0).serialize()
    r.packet(PacketIn, tcp = false, update)
    val segments = r.finish()
    val back = read(segments)
    for ((p, rec) <- secrets.zip(back)) {
      val sent = p.serialize()
      assertTrue(s"${p.getType} said it was cut", (rec.flags & FlagRedacted) != 0)
      assertArrayEquals(s"${p.getType}'s header kept", sent.take(MatchRecorder.HeaderBytes), rec.payload.take(MatchRecorder.HeaderBytes))
      assertTrue(s"${p.getType}'s fields zeroed", rec.payload.drop(MatchRecorder.HeaderBytes).forall(_ == 0))
    }
    assertEquals("anything else kept whole", 0, back.last.flags & FlagRedacted)
    assertArrayEquals(update, back.last.payload)
    val onDisk = new String(segments.flatMap(Files.readAllBytes(_)).toArray, StandardCharsets.ISO_8859_1)
    for (secret <- Seq("hunter22", "pond", new String(token, StandardCharsets.US_ASCII).take(12)))
      assertFalse(s"'$secret' is on disk", onDisk.contains(secret))
  }

  @Test def aLongMatchKeepsItsStartAndItsNewestSegments(): Unit = {
    val d = dir()
    val r = new MatchRecorder(d, 0L, segmentBytes = 4096, maxSegments = 4, bufferBytes = 64 * 1024, writeEveryMs = 5)
    val payload = new Array[Byte](64)
    val rnd = new scala.util.Random(3)
    for (n <- 0 until 2000) {
      rnd.nextBytes(payload) // what doesn't squeeze, so segments fill at their size
      Recording.putInt(payload, 1, n) // the packet's number, to tell where each segment is from
      payload(0) = PacketType.PLAYER_UPDATE.id
      r.packet(PacketIn, tcp = false, payload)
      if (n % 20 == 0) Thread.sleep(1) // let the writer keep up
    }
    val kept = r.finish()
    val onDisk = Files.list(d).toArray.map(_.asInstanceOf[Path]).toSeq.sortBy(_.getFileName.toString)
    assertEquals("only the segments kept are on disk", kept.sortBy(_.getFileName.toString), onDisk)
    assertEquals(4, kept.size)
    assertEquals("the first is always the match's start", "seg-0000.rec", kept.head.getFileName.toString)
    val numbers = kept.map(_.getFileName.toString.drop(4).take(4).toInt)
    assertEquals("then the newest, in a run", (numbers(1) until numbers(1) + 3).toSeq, numbers.tail)
    assertTrue("some were let go", numbers(1) > 1)
    val first = read(Seq(kept.head)).filter(_.kind == PacketIn).map(r => int(r.payload, 1))
    assertEquals(0, first.head)
    val newest = read(kept.tail)
    val last = newest.filter(_.kind == PacketIn).map(r => int(r.payload, 1))
    assertEquals("the last packet is in the newest", 1999, last.last)
    // A busy machine can hold the writer back long enough for the buffer to fill: then a Gap says so,
    // and only then may the run have a hole
    if (!newest.exists(_.kind == Gap)) assertEquals("and the newest run has no hole", (last.head to 1999).toSeq, last)
  }

  @Test def aMatchIsKeptCompressedAndReadsBackWhileItIsStillBeingWritten(): Unit = {
    val d = dir()
    val r = new MatchRecorder(d, 0L, writeEveryMs = 20)
    // A match's packets, as alike as a match's are: players' updates, a step apart
    val updates = (0 until 5000).map(n => new PlayerUpdatePacket(n, me, Packet.getCurrentTimestamp,
      new Position(10 + n % 7, 12 + n % 5), 0xFF102030, 100 - n % 50, 0, 0, 3.toByte, 0.toByte, n / 100).serialize())
    updates.take(2500).foreach(u => r.packet(PacketIn, tcp = false, u))
    Thread.sleep(200)
    // A crash here: the file as it is, mid-match, still reads to the writer's last flush
    val midway = read(Seq(Files.copy(d.resolve(MatchRecorder.segmentName(0)), d.resolve("copy.rec"))))
    assertEquals("everything flushed so far", 2500, midway.size)
    assertArrayEquals(updates(2499), midway.last.payload)
    updates.drop(2500).foreach(u => r.packet(PacketIn, tcp = false, u))
    val segments = r.finish()
    assertEquals(5000, read(segments).size)
    val raw = 5000L * (RecordHeaderSize + 64)
    assertEquals(raw, r.bytesRecorded)
    assertTrue(s"${r.bytesWritten} bytes on disk for $raw of records", r.bytesWritten < raw / 3)
  }

  @Test def aFullBufferDropsWhatDoesntFitAndSaysHowMuch(): Unit = {
    // The writer held back, so nothing empties the buffer: what doesn't fit is lost, and counted
    val r = new MatchRecorder(dir(), 0L, bufferBytes = 1024, startWriter = false)
    val payload = new Array[Byte](64)
    for (_ <- 0 until 40) r.packet(PacketIn, tcp = false, payload)
    val back = read(r.finish())
    val fit = 1024 / (RecordHeaderSize + 64)
    assertEquals(fit, back.count(_.kind == PacketIn))
    val gap = back.find(_.kind == Gap).getOrElse(throw new AssertionError("no gap recorded"))
    assertEquals("how many were lost", 40 - fit, int(gap.payload, 0))
    assertEquals("after what was kept", Gap, back.last.kind)
  }

  @Test def recordingAFrameAllocatesNothing(): Unit = {
    val r = new MatchRecorder(dir(), 0L)
    val s = new StateSample
    val packet = new Array[Byte](64)
    packet(0) = PacketType.PLAYER_UPDATE.id
    def frame(n: Int): Unit = {
      r.frame(16.6f, 3f, n.toFloat, 2f, 3f, 4f, 5f, 6f, Keys.W, FrameFlags.Focused, 0, -1)
      r.input(InputDevice.Mouse, 1, 0, 0)
      if (n % 15 == 0) r.state(s)
      r.packet(PacketIn, tcp = false, packet)
    }
    val mx = ManagementFactory.getThreadMXBean.asInstanceOf[com.sun.management.ThreadMXBean]
    val thread = Thread.currentThread().getId
    // Warmed up, the JIT done with it, and with the measuring too (its first calls allocate of their own)
    for (n <- 0 until 20000) frame(n)
    for (_ <- 0 until 2000) mx.getThreadAllocatedBytes(thread)
    var sink = 0L
    def measure(body: => Unit): Long = { val b = mx.getThreadAllocatedBytes(thread); body; mx.getThreadAllocatedBytes(thread) - b }
    val control = measure { for (n <- 0 until 100000) sink += n }
    val bytes = measure { for (n <- 0 until 100000) frame(n) }
    r.finish()
    assertTrue(s"100000 frames allocated $bytes bytes on the thread that recorded them (an empty loop $control)",
      bytes - control < 2048)
  }
}
