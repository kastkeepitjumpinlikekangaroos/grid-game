package com.gridgame.client

import com.gridgame.client.diagnostics.{Diagnostics, NoticedMoment, OutboxEntry, StateSample}
import com.gridgame.client.game.ReportSender
import com.gridgame.common.diagnostics.Recording.{NoticedKind, StateEffects}
import com.gridgame.common.model.{CharacterDef, CharacterId, ProjectileType}
import com.gridgame.common.protocol._
import org.junit.Assert._
import org.junit.{After, Test}

import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * The client's side of a problem report, short of the upload's own connection (UploadConnectionTest):
 * which match this is, the snapshot of us the recording keeps, asking the server to take a report and
 * hearing its answer, and the outbox told what became of it.
 */
class GameClientReportsTest {
  private val t = new TestClient(name = "me")
  private val matchId = UUID.fromString("01234567-89ab-cdef-0123-456789abcdef")

  @After def reset(): Unit = { Diagnostics.resetForTest(); ReportSender.answerTimeoutMs = 10000L }

  @Test def askingTheServerToTakeAReportSaysWhatItIs(): Unit = {
    val id = UUID.randomUUID()
    val answer = t.client.beginReport(id, 123456, 0xABCDEF, matchId, ProblemReportKind.MATCH)
    val begin = t.sent.collect { case p: ProblemReportPacket => p }.last
    assertEquals(ProblemReportAction.BEGIN, begin.getAction)
    assertEquals(t.id, begin.getPlayerId)
    assertEquals((id, 123456, 0xABCDEF, matchId, ProblemReportKind.MATCH),
      (begin.getReportId, begin.getSize, begin.getCrc, begin.getMatchId, begin.getKind))
    t.receive(ProblemReportPacket.accepted(1, t.id, UUID.randomUUID(), "GG-OTHER001"))
    assertFalse("another report's answer isn't this one's", answer.isDone)
    t.receive(ProblemReportPacket.refused(2, t.id, id, ProblemReportRefusal.TOO_MANY, 600))
    val heard = answer.get(1, TimeUnit.SECONDS)
    assertEquals((ProblemReportAction.REFUSED, ProblemReportRefusal.TOO_MANY, 600), (heard.getAction, heard.getReason, heard.getRetryAfterSeconds))
  }

  @Test def theServerNamesTheMatchAndANewMatchForgetsIt(): Unit = {
    t.startMatch(spawn = (5, 5))
    assertNull(t.client.matchId)
    t.gameEvent(GameEvent.MATCH_INFO, target = matchId)
    assertEquals(matchId, t.client.matchId)
    assertEquals(matchId.toString, t.client.matchSummary.get("matchId").getAsString)
    t.lobby(LobbyAction.GAME_STARTING)
    assertNull("the next match is another", t.client.matchId)
  }

  @Test def theSnapshotSaysWhatTheClientThinksOfUs(): Unit = {
    t.startMatch(spawn = (5, 5))
    val s = new StateSample
    t.client.sampleState(s)
    assertEquals(t.client.getLocalHealth, s.health)
    assertEquals(t.client.getSelectedCharacterMaxHealth, s.maxHealth)
    assertEquals(0, s.effects & StateEffects.Dead)
    val killer = UUID.randomUUID()
    t.join(killer, 9, 9)
    t.gameEvent(GameEvent.KILL, who = killer, target = t.id)
    t.client.sampleState(s)
    assertTrue("dead", (s.effects & StateEffects.Dead) != 0)
    assertEquals(1, s.deaths)
    assertEquals("the other player", 1, s.players)
  }

  /** A report waiting in the outbox, its zip and its note. */
  private def waiting(): OutboxEntry = {
    Diagnostics.install(Nil, Some(Files.createTempDirectory("reports")), teeConsole = false)
    val id = UUID.randomUUID()
    Files.createDirectories(Diagnostics.outbox.dir)
    Files.write(Diagnostics.outbox.zip(id), Array[Byte](1, 2, 3))
    val e = OutboxEntry(id, ProblemReportKind.MATCH, System.currentTimeMillis(), 3L, 77, matchId, "movement")
    Diagnostics.outbox.save(e)
    e
  }

  /** The server, as far as the session goes: whatever it is to say to a BEGIN. */
  private def answering(say: ProblemReportPacket => Packet): Unit =
    t.client.packetSink = {
      case p: ProblemReportPacket if p.getAction == ProblemReportAction.BEGIN => val a = say(p); if (a != null) t.receive(a)
      case _ =>
    }

  @Test def aReportTheServerHasAlreadyIsSentWithItsCode(): Unit = {
    val e = waiting()
    answering(p => ProblemReportPacket.accepted(1, t.id, p.getReportId, "GG-7K3F9QX2"))
    assertEquals(ReportSender.Sent("GG-7K3F9QX2"), ReportSender.send(t.client, Diagnostics.outbox, e))
    val after = Diagnostics.outbox.get(e.id).get
    assertEquals((OutboxEntry.Sent, "GG-7K3F9QX2"), (after.status, after.code))
    assertFalse(Files.exists(Diagnostics.outbox.zip(e.id)))
  }

  @Test def aRefusedReportWaitsAsLongAsTheServerAsked(): Unit = {
    val e = waiting()
    answering(p => ProblemReportPacket.refused(1, t.id, p.getReportId, ProblemReportRefusal.TOO_MANY, 600))
    val before = System.currentTimeMillis()
    assertEquals(ReportSender.NotSent(ProblemReportRefusal.TOO_MANY, 600, "refused"), ReportSender.send(t.client, Diagnostics.outbox, e))
    val after = Diagnostics.outbox.get(e.id).get
    assertTrue("still waiting", after.isPending)
    assertEquals(1, after.attempts)
    assertTrue(after.notBeforeMs >= before + 600000L)
    // Not due yet: the outbox sent after a login leaves it be
    var asked = 0
    answering(p => { asked += 1; null })
    ReportSender.sendPending(t.client, Diagnostics.outbox, (_, _) => ())
    assertEquals(0, asked)
  }

  // ── What the client notices for itself ──────────────────────────────

  /** A match being recorded (Diagnostics in a folder of the test's own), started as the server starts one. */
  private def recordedMatch(): Unit = {
    Diagnostics.install(Nil, Some(Files.createTempDirectory("noticed")), teeConsole = false)
    Diagnostics.packetTap.received(new LobbyActionPacket(1, t.id, Packet.getCurrentTimestamp, LobbyAction.GAME_STARTING,
      7.toShort, 0.toByte, 5.toByte, 1.toByte, 8.toByte, 0.toByte, "Lobby", 0.toByte, 0.toByte, 2.toByte).serialize(), tcp = true)
    t.startMatch(spawn = (20, 20))
  }

  private def noticed: Seq[NoticedMoment] =
    Diagnostics.lastMatch.get.noticed.toArray.map(_.asInstanceOf[NoticedMoment]).toSeq

  @Test def theServerPuttingUsSomewhereWeDidntWalkIsNoticedButAPushIsnt(): Unit = {
    recordedMatch()
    t.update(t.id, 23, 24, serverMoves = 1) // a correction: 5 cells, no push
    t.update(t.id, 23, 23, serverMoves = 2) // a step's worth: nothing to notice
    assertEquals(Seq(NoticedKind.SnapBack), noticed.map(_.kind))
    assertEquals(500, noticed.head.a)
    Thread.sleep(2100) // past the quiet between two of a kind
    t.update(t.id, 30, 30, serverMoves = 3, knockbacks = 1) // a push: drawn as one, not noticed
    assertEquals(1, noticed.size)
  }

  @Test def ourDeathIsNoticedWithWhoKilledUs(): Unit = {
    recordedMatch()
    val killer = UUID.randomUUID()
    t.join(killer, 25, 25, name = "them", charId = CharacterId.Wizard.id)
    t.gameEvent(GameEvent.KILL, who = killer, target = t.id)
    val died = noticed.filter(_.kind == NoticedKind.Died)
    assertEquals(1, died.size)
    assertEquals(CharacterId.Wizard.id.toInt, died.head.a)
    assertEquals(CharacterDef.get(CharacterId.Wizard.id).displayName, died.head.detail)
  }

  @Test def aShotTheServerNeverAnsweredIsNoticedWhereItWasFired(): Unit = {
    recordedMatch()
    val now = System.nanoTime()
    t.client.pendingSpawns.requested(ProjectileType.ARROW, com.gridgame.common.protocol.AttackSlot.Q, now)
    t.client.pendingSpawns.expire(now + 1500000000L)
    val shot = noticed.filter(_.kind == NoticedKind.ShotUnanswered)
    assertEquals(1, shot.size)
    assertEquals(com.gridgame.common.protocol.AttackSlot.Q, shot.head.a)
    assertEquals("how long ago it went", 1500, shot.head.b)
  }

  @Test def aRefusedRAndASlowHeartbeatAreNoticed(): Unit = {
    // A Soldier's Q is a shot: cast it once R's minute has run, then R, which the server refuses
    t.client.selectedCharacterId = CharacterId.Soldier.id
    recordedMatch()
    t.client.setMouseWorldPosition(28.0, 20.0)
    t.nanos += com.gridgame.common.Constants.COOLDOWN_RESET_MS * 1000000L
    assertTrue(t.client.shootAbility(0))
    t.client.pressReset()
    t.receive(new CooldownResetPacket(9, t.id, CooldownResetAction.REJECTED, 12000))
    val refused = noticed.filter(_.kind == NoticedKind.Refused)
    assertEquals(Seq((0, 12000)), refused.map(n => (n.a, n.b)))
    t.client.sendHeartbeat()
    Thread.sleep(330)
    t.receive(new HeartbeatPacket(10, t.id))
    val ping = noticed.filter(_.kind == NoticedKind.PingSpike)
    assertEquals(1, ping.size)
    assertTrue(ping.head.a >= 300)
  }

  @Test def aServerThatSaysNothingLeavesTheReportToTryAgain(): Unit = {
    val e = waiting()
    ReportSender.answerTimeoutMs = 200L
    answering(_ => null) // an older server, which doesn't know the packet
    assertEquals(ReportSender.NoAnswer, ReportSender.send(t.client, Diagnostics.outbox, e).asInstanceOf[ReportSender.NotSent].reason)
    assertTrue(Diagnostics.outbox.get(e.id).get.isPending)
    assertTrue(ReportSender.worthRetrying(ReportSender.NoAnswer))
    assertFalse(ReportSender.worthRetrying(ProblemReportRefusal.TOO_BIG))
  }
}
