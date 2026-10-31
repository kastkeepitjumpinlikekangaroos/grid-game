package com.gridgame.client

import com.gridgame.client.devtools.{ReplayRecording, ReplaySession}
import com.gridgame.common.diagnostics.Recording
import com.gridgame.common.diagnostics.Recording._
import com.gridgame.common.Constants
import com.gridgame.common.model.{CharacterDef, CharacterId, ItemType, Position, ProjectileType}
import com.gridgame.common.protocol._
import org.junit.Assert._
import org.junit.Test

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.UUID
import java.util.zip.{Deflater, ZipEntry, ZipOutputStream}

/**
 * A problem report's recording played into a client that connects nowhere (devtools ReplaySession, what
 * the replay tool draws): fed to a time, the client knows exactly what the packets up to it said; we
 * stand and are drawn where the frames put us; a packet cut to its header (the player's own) is left
 * out; going back to a time is the same as playing to it fresh; and the cooldowns and the items we
 * carried, which only our own game knew, are as the recording had them.
 */
class ReplaySessionTest {
  private val me = UUID.fromString("0f1e2d3c-4b5a-6978-8796-a5b4c3d2e1f0")
  private val them = UUID.fromString("11111111-2222-3333-4444-555555555555")
  private var seq = 100
  private def next(): Int = { seq += 1; seq }
  private val ms = 1000L // a millisecond, in the recording's microseconds

  /** A record: its kind, flags, time (µs) and payload. */
  private case class Rec(kind: Int, flags: Int, at: Long, payload: Array[Byte])

  private def in(at: Long, p: Packet, tcp: Boolean = true): Rec = Rec(PacketIn, if (tcp) FlagTcp else 0, at, p.serialize())
  private def frame(at: Long, cell: (Int, Int), drawn: (Float, Float), aim: (Float, Float) = (0f, 0f), moving: Boolean = false): Rec = {
    val b = new Array[Byte](FrameRecord.Size)
    putFloat(b, FrameRecord.DeltaMs, 16.6f)
    putFloat(b, FrameRecord.CellX, cell._1.toFloat); putFloat(b, FrameRecord.CellY, cell._2.toFloat)
    putFloat(b, FrameRecord.DrawnX, drawn._1); putFloat(b, FrameRecord.DrawnY, drawn._2)
    putFloat(b, FrameRecord.AimX, aim._1); putFloat(b, FrameRecord.AimY, aim._2)
    putShort(b, FrameRecord.Flags, FrameFlags.Focused | (if (moving) FrameFlags.Moving else 0))
    Rec(Frame, 0, at, b)
  }

  /** The match as its client recorded it: the start, the map, the two of us, them walking east, a shot of
    * theirs, us stepping east, a chat line cut to its header, and a step we sent. */
  private def theMatch: Seq[Rec] = {
    val chat = new ChatMessagePacket(next(), them, Packet.getCurrentTimestamp, ChatScope.GAME, "meet me by the pond").serialize()
    java.util.Arrays.fill(chat, 21, chat.length, 0.toByte)
    Seq(
      in(0, new LobbyActionPacket(next(), them, Packet.getCurrentTimestamp, LobbyAction.GAME_STARTING, 7.toShort, 0.toByte,
        1.toByte, 2.toByte, 8.toByte, 0.toByte, "Lobby", 0.toByte, 0.toByte, 2.toByte)),
      in(1 * ms, new WorldInfoPacket(next(), Packet.getCurrentTimestamp, "the_cell.json")),
      in(2 * ms, new PlayerJoinPacket(next(), me, Packet.getCurrentTimestamp, new Position(40, 40), 0xFF00AA00, "me", 100,
        CharacterId.Wizard.id, 0.toByte)),
      in(3 * ms, new PlayerJoinPacket(next(), them, Packet.getCurrentTimestamp, new Position(45, 40), 0xFFAA0000, "them", 100,
        CharacterId.Wizard.id, 0.toByte)),
      frame(50 * ms, (40, 40), (40f, 40f)),
      Rec(PacketOut, 0, 60 * ms, new PlayerUpdatePacket(1, me, Packet.getCurrentTimestamp, new Position(40, 40), 0, 100, 0, 0,
        CharacterId.Wizard.id, 0.toByte, 0).serialize())) ++
    (1 to 5).map(i => in(i * 100 * ms, new PlayerUpdatePacket(next(), them, Packet.getCurrentTimestamp, new Position(45 + i, 40),
      0xFFAA0000, 100, 0, 0, CharacterId.Wizard.id, 0.toByte, 0), tcp = false)) ++
    Seq(
      in(300 * ms + 500, new ProjectilePacket(next(), them, 10, 48.5f, 40.5f, 0xFFAA0000, 1, 1f, 0f, ProjectileAction.SPAWN,
        null, 0.toByte, ProjectileType.ARROW), tcp = false),
      Rec(PacketIn, FlagTcp | FlagRedacted, 350 * ms, chat),
      frame(400 * ms, (41, 40), (40.5f, 40f), aim = (44f, 41f), moving = true),
      frame(600 * ms, (42, 40), (42f, 40f)))
  }.sortBy(_.at)

  /** The match's recording as a report's zip: one compressed segment (as the client writes it now), and
    * report.json saying whose match it is, unless `withReport` is false. */
  private def report(records: Seq[Rec], withReport: Boolean = true, segments: Seq[Int] = Seq(0)): Path = {
    val zip = Files.createTempFile("replay", ".zip")
    val out = new ZipOutputStream(Files.newOutputStream(zip))
    try {
      if (withReport) {
        out.putNextEntry(new ZipEntry(ReportFiles.ReportJson))
        out.write(s"""{"connection": {"playerId": "$me", "player": "me"},
          "marks": [{"number": 1, "atMicros": ${300 * ms}}],
          "noticed": [{"atMicros": ${450 * ms}, "kind": "snap-back", "text": "the server moved us 3.2 cells, not by a push"}],
          "incidents": [{"kind": "freeze", "atMicros": ${500 * ms}, "detail": "..."}]}""".getBytes(StandardCharsets.UTF_8))
        out.closeEntry()
      }
      val per = (records.size + segments.size - 1) / segments.size
      records.grouped(per).zip(segments).foreach { case (part, n) =>
        out.putNextEntry(new ZipEntry(f"recording/seg-$n%04d.rec"))
        out.write(segment(part, n))
        out.closeEntry()
      }
    } finally out.close()
    zip
  }

  private def segment(records: Seq[Rec], n: Int): Array[Byte] = {
    val bytes = new ByteArrayOutputStream()
    val head = new Array[Byte](SegmentHeaderSize)
    writeSegmentHeader(head, 0, n, 1793000000000L)
    bytes.write(head)
    val deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true)
    val buf = new Array[Byte](1 << 16)
    for (r <- records) {
      val b = new Array[Byte](RecordHeaderSize + r.payload.length)
      writeRecordHeader(b, 0, r.kind, r.flags, r.payload.length, r.at / 10)
      System.arraycopy(r.payload, 0, b, RecordHeaderSize, r.payload.length)
      deflater.setInput(b)
      while (!deflater.needsInput()) bytes.write(buf, 0, deflater.deflate(buf, 0, buf.length, Deflater.NO_FLUSH))
    }
    var n2 = deflater.deflate(buf, 0, buf.length, Deflater.SYNC_FLUSH)
    while (n2 > 0) { bytes.write(buf, 0, n2); n2 = deflater.deflate(buf, 0, buf.length, Deflater.SYNC_FLUSH) }
    bytes.toByteArray
  }

  private def themAt(s: ReplaySession): Int = s.client.getPlayers.get(them).getPosition.getX

  @Test def fedToATimeTheClientKnowsWhatThePacketsUpToItSaid(): Unit = {
    val s = new ReplaySession(ReplayRecording.load(report(theMatch)))
    s.advanceTo(250 * ms)
    assertEquals("ours", me, s.client.getLocalPlayerId)
    assertEquals("the map the server named", "the_cell.json".stripSuffix(".json"), s.client.getWorld.name.toLowerCase.replace(' ', '_').stripSuffix(".json"))
    assertEquals("their updates at 100 and 200ms, not 300", 47, themAt(s))
    assertEquals("their shot comes at 300.5ms", 0, s.client.getProjectiles.size())
    s.advanceTo(300 * ms + 499)
    assertEquals(0, s.client.getProjectiles.size())
    s.advanceTo(300 * ms + 500)
    assertEquals(1, s.client.getProjectiles.size())
    assertEquals(48, themAt(s))
    s.advanceTo(1000 * ms)
    assertEquals(50, themAt(s))
    assertEquals(0, s.packetErrors)
  }

  @Test def weStandAndAreDrawnWhereTheFramesPutUs(): Unit = {
    val s = new ReplaySession(ReplayRecording.load(report(theMatch)))
    s.advanceTo(100 * ms)
    assertEquals(new Position(40, 40), s.client.getLocalPosition)
    s.advanceTo(450 * ms)
    assertEquals(new Position(41, 40), s.client.getLocalPosition)
    assertEquals("drawn between the cells, as the frame had it", 40.5, s.client.visualPosX, 1e-6)
    assertEquals("and the frame's own update of the drawing isn't undone", 40.5, { s.client.updateVisualPosition(); s.client.visualPosX }, 1e-6)
    assertEquals(44.0, s.client.getMouseWorldX, 1e-6)
    assertEquals("facing the way we stepped", com.gridgame.common.model.Direction.Right, s.client.getLocalDirection)
    assertTrue("walking", s.client.getIsMoving)
    s.advanceTo(700 * ms)
    assertEquals(new Position(42, 40), s.client.getLocalPosition)
    assertEquals(42.0, s.client.visualPosX, 1e-6)
  }

  @Test def aPacketCutToItsHeaderIsLeftOut(): Unit = {
    val s = new ReplaySession(ReplayRecording.load(report(theMatch)))
    s.advanceTo(1000 * ms)
    assertEquals("the chat line the recording kept only the header of never went in", 0, s.client.chatMessages.size())
    assertEquals(0, s.packetErrors)
  }

  @Test def goingBackIsTheSameAsPlayingThereFresh(): Unit = {
    val rec = ReplayRecording.load(report(theMatch))
    val s = new ReplaySession(rec)
    s.advanceTo(1000 * ms)
    val before = s.client
    assertTrue("a new client", s.seek(250 * ms))
    assertNotSame(before, s.client)
    val fresh = new ReplaySession(rec)
    fresh.advanceTo(250 * ms)
    assertEquals(themAt(fresh), themAt(s))
    assertEquals(fresh.client.getLocalPosition, s.client.getLocalPosition)
    assertEquals(fresh.client.visualPosX, s.client.visualPosX, 1e-9)
    assertEquals(fresh.client.getProjectiles.size(), s.client.getProjectiles.size())
    assertEquals(250 * ms, s.timeMicros)
    assertFalse("forward is the same client", s.seek(450 * ms))
    assertEquals("their updates to 400ms", 49, themAt(s))
  }

  @Test def afterABurstTheMatchClockAndTheOpeningReadAsTheyStood(): Unit = {
    // A match of 3 minutes (GAME_STARTING says), its opening announced at the start, a TIME_SYNC 10s in
    val opening = new GameEventPacket(next(), new UUID(0L, 0L), Packet.getCurrentTimestamp, GameEvent.MATCH_OPENING,
      openingMs = 30000, openingRules = com.gridgame.common.model.MatchOpening.NO_ATTACKS)
    val sync = new GameEventPacket(next(), new UUID(0L, 0L), Packet.getCurrentTimestamp, GameEvent.TIME_SYNC, remainingSeconds = 170)
    val records = Seq(
      in(0, new LobbyActionPacket(next(), them, Packet.getCurrentTimestamp, LobbyAction.GAME_STARTING, 7.toShort, 0.toByte,
        3.toByte, 2.toByte, 8.toByte, 0.toByte, "Lobby", 0.toByte, 0.toByte, 2.toByte)),
      in(1 * ms, new WorldInfoPacket(next(), Packet.getCurrentTimestamp, "the_cell.json")),
      in(2 * ms, new PlayerJoinPacket(next(), me, Packet.getCurrentTimestamp, new Position(40, 40), 0xFF00AA00, "me", 100,
        CharacterId.Wizard.id, 0.toByte)),
      in(3 * ms, opening),
      in(10000 * ms, sync),
      frame(19000 * ms, (40, 40), (40f, 40f)))
    val s = new ReplaySession(ReplayRecording.load(report(records)))
    s.advanceTo(3 * ms)
    assertEquals("the lobby's length, from GAME_STARTING", 180, s.client.gameTimeRemaining)
    // Before the first TIME_SYNC, the start counts as one: 6 seconds in, in one go
    val early = new ReplaySession(ReplayRecording.load(report(records)))
    early.advanceTo(6000 * ms)
    assertEquals(174, early.client.gameTimeRemaining)
    // 18 seconds in, in one go: the sync (8s old) and the opening (18s old) as they stood then
    s.advanceTo(18000 * ms)
    assertEquals(162, s.client.gameTimeRemaining)
    assertEquals(12000.0, s.client.openingMsLeft.toDouble, 300.0)
    // A seek back from further on, the same
    s.advanceTo(19000 * ms)
    s.seek(15000 * ms)
    assertEquals(165, s.client.gameTimeRemaining)
    assertEquals(15000.0, s.client.openingMsLeft.toDouble, 300.0)
  }

  /** A state sample: Q's, E's and R's ms left to cool down. */
  private def state(at: Long, q: Int, e: Int, r: Int): Rec = {
    val b = new Array[Byte](StateRecord.Size)
    putInt(b, StateRecord.QLeftMs, q); putInt(b, StateRecord.ELeftMs, e); putInt(b, StateRecord.RLeftMs, r)
    Rec(State, 0, at, b)
  }

  private def joined: Seq[Rec] = Seq(
    in(0, new LobbyActionPacket(next(), them, Packet.getCurrentTimestamp, LobbyAction.GAME_STARTING, 7.toShort, 0.toByte,
      3.toByte, 2.toByte, 8.toByte, 0.toByte, "Lobby", 0.toByte, 0.toByte, 2.toByte)),
    in(1 * ms, new WorldInfoPacket(next(), Packet.getCurrentTimestamp, "the_cell.json")),
    in(2 * ms, new PlayerJoinPacket(next(), me, Packet.getCurrentTimestamp, new Position(40, 40), 0xFF00AA00, "me", 100,
      CharacterId.Wizard.id, 0.toByte)))

  @Test def theCooldownsAreAsTheRecordingHadThem(): Unit = {
    val qCd = CharacterDef.get(CharacterId.Wizard).qAbility.cooldownMs
    val r = Constants.COOLDOWN_RESET_MS
    // Samples a quarter of a second apart; Q cast at 600ms (the sample at 750 shows it), R pressed at 1100,
    // and Q's cooldown halved by a hit before 1500
    val records = joined ++ Seq(
      state(250 * ms, 0, 0, r - 250), state(500 * ms, 0, 0, r - 500),
      state(750 * ms, qCd - 150, 0, r - 750), state(1000 * ms, qCd - 400, 0, r - 1000),
      state(1250 * ms, qCd - 650, 0, r - 150), state(1500 * ms, (qCd - 900) / 2, 0, r - 400),
      frame(2000 * ms, (40, 40), (40f, 40f)))
    val s = new ReplaySession(ReplayRecording.load(report(records)))
    s.advanceTo(550 * ms)
    assertEquals("Q ready before its cast", 0f, s.client.getQCooldownRemaining, 0.02f)
    s.advanceTo(650 * ms)
    assertEquals("cast at 600, not shown only from the sample at 750", (qCd - 50) / 1000f, s.client.getQCooldownRemaining, 0.02f)
    s.advanceTo(900 * ms)
    assertEquals((qCd - 300) / 1000f, s.client.getQCooldownRemaining, 0.02f)
    assertEquals("R's minute from the start", (r - 900) / 1000f, s.client.getResetCooldownRemaining, 0.02f)
    s.advanceTo(1200 * ms)
    assertEquals("R pressed at 1100", (r - 100) / 1000f, s.client.getResetCooldownRemaining, 0.02f)
    s.advanceTo(1400 * ms)
    assertEquals("a cooldown a hit cut short is no cast", (qCd - 800) / 1000f, s.client.getQCooldownRemaining, 0.02f)
    // Back again, in a burst
    s.seek(700 * ms)
    assertEquals((qCd - 100) / 1000f, s.client.getQCooldownRemaining, 0.02f)
    assertEquals((r - 700) / 1000f, s.client.getResetCooldownRemaining, 0.02f)
    assertEquals(0f, s.client.getECooldownRemaining, 0.02f)
  }

  @Test def anItemWeUsedLeavesWhatWeCarry(): Unit = {
    def item(at: Long, kind: ItemType, id: Int, action: Byte, out: Boolean = false): Rec = {
      val p = new ItemPacket(next(), me, 40, 40, kind.id, id, action)
      if (out) Rec(PacketOut, FlagTcp, at, p.serialize()) else in(at, p)
    }
    val records = joined ++ Seq(
      item(100 * ms, ItemType.Heart, 7, ItemAction.PICKUP), item(150 * ms, ItemType.Heart, 8, ItemAction.PICKUP),
      item(200 * ms, ItemType.Shield, 9, ItemAction.PICKUP),
      item(300 * ms, ItemType.Heart, 7, ItemAction.USE, out = true), item(301 * ms, ItemType.Heart, 7, ItemAction.USE, out = true),
      item(400 * ms, ItemType.Shield, 9, ItemAction.USE, out = true),
      frame(500 * ms, (40, 40), (40f, 40f)))
    val s = new ReplaySession(ReplayRecording.load(report(records)))
    s.advanceTo(250 * ms)
    assertEquals(3, s.client.getInventoryCount)
    s.advanceTo(350 * ms)
    assertEquals("one heart used, its copy nothing", 1, s.client.getItemCount(ItemType.Heart.id))
    s.advanceTo(450 * ms)
    assertEquals(0, s.client.getItemCount(ItemType.Shield.id))
    assertTrue("the shield we used is up", s.client.hasShield)
    assertEquals(1, s.client.getInventoryCount)
    assertEquals(0, s.packetErrors)
  }

  @Test def whatTheReportSaysOfItselfIsRead(): Unit = {
    val rec = ReplayRecording.load(report(theMatch))
    assertEquals(me, rec.playerId)
    assertEquals("me", rec.playerName)
    assertEquals(Seq(300 * ms -> "mark 1"), rec.marks)
    assertEquals(Seq(450 * ms -> "the server moved us 3.2 cells, not by a push", 500 * ms -> "freeze"), rec.moments)
    assertEquals(600 * ms, rec.durationMicros)
    assertEquals(Nil, rec.gaps)
  }

  @Test def withoutItsReportItIsOursByWhatWeSent(): Unit = {
    val rec = ReplayRecording.load(report(theMatch, withReport = false, segments = Seq(0, 2)))
    assertEquals("the player our own packets name", me, rec.playerId)
    assertEquals("a segment missing between those kept", 1, rec.gaps.size)
    assertEquals(Seq(0, 2), rec.segments)
    val s = new ReplaySession(rec)
    s.advanceTo(1000 * ms)
    assertEquals(50, themAt(s))
  }
}
