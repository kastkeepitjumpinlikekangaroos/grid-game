package com.gridgame.server

import com.gridgame.server.bots.BotController
import com.gridgame.common.Constants
import com.gridgame.common.model._
import com.gridgame.common.protocol._
import org.junit.Assert._
import org.junit.Test

import java.util.UUID

/**
 * R (design/characters.md, *R resets the cooldowns*): a player's ability cooldowns all over at once,
 * once a minute, the first minute counted from the match's start (practice's first is ready at
 * once). The server takes it as it would a cast, from someone who could cast now and with its own
 * minute run, and clears every clock it holds the abilities to: a shot's, a trap's, a phase's and a
 * barrier's. A refusal is said, with how long R still has to wait when that was why. A death doesn't
 * give it back. Bots press it too.
 */
class CooldownResetTest {
  private val m = new TestMatch()
  private var seq = 0
  private def east = Seq((1f, 0f))

  /** R's own minute run out, as though the match had been going on a while. */
  private def minuteRun(tm: TestMatch = m): Unit = tm.instance.startedAtMs -= Constants.COOLDOWN_RESET_MS

  /** R from the player's client, as the wire carries it. */
  private def pressR(p: Player, tm: TestMatch = m): Unit = {
    seq += 1
    tm.instance.handler.processPacket(PacketSerializer.deserialize(
      new CooldownResetPacket(seq, p.getId, CooldownResetAction.REQUEST, 0).serialize()), null, null)
  }

  /** What the player was told of their resets since the last look. */
  private def refusals(p: Player, tm: TestMatch = m): Seq[CooldownResetPacket] =
    tm.tcpSent(p).collect { case r: CooldownResetPacket => r }

  private def grenade(p: Player): Int = m.fire(p, AttackSlot.Q, ProjectileType.GRENADE, east).size
  private def rocket(p: Player): Int = m.fire(p, AttackSlot.E, ProjectileType.ROCKET, east).size

  // --- What it does ---

  @Test def bothAbilitiesAreReadyAgain(): Unit = {
    minuteRun()
    val p = m.join(CharacterId.Soldier, 20, 20)
    assertEquals(1, grenade(p))
    assertEquals(1, rocket(p))
    assertEquals("both cooling down", 0, grenade(p) + rocket(p))
    m.clearSent()
    pressR(p)
    assertTrue("a reset taken says nothing", refusals(p).isEmpty)
    assertEquals(1, grenade(p))
    assertEquals(1, rocket(p))
    assertEquals("and each is on its cooldown again from that cast", 0, grenade(p) + rocket(p))
  }

  @Test def aTrapAPhaseAndABarrierComeBackToo(): Unit = {
    minuteRun()
    // A trap: placed, refused a second time on its cooldown, placed once R is pressed
    val warden = m.join(CharacterId.Warden, 20, 20)
    assertNotNull(m.placeTrap(warden, AttackSlot.E, TrapType.BEAR_TRAP, 22, 20))
    assertNull(m.placeTrap(warden, AttackSlot.E, TrapType.BEAR_TRAP, 23, 20))
    pressR(warden)
    assertNotNull(m.placeTrap(warden, AttackSlot.E, TrapType.BEAR_TRAP, 23, 20))

    // A phase, run out by hand: not honoured again on its cooldown, honoured once R is pressed
    val wraith = m.join(CharacterId.Wraith, 30, 30)
    m.move(wraith, 31, 30, flags = 0x08)
    assertTrue(wraith.isPhased)
    wraith.setPhasedUntil(System.currentTimeMillis() - 1)
    m.move(wraith, 32, 30, flags = 0x08)
    assertFalse("its cooldown holds it", wraith.isPhased)
    pressR(wraith)
    m.move(wraith, 33, 30, flags = 0x08)
    assertTrue(wraith.isPhased)

    // A barrier, dropped: not raised again on its cooldown, raised once R is pressed
    val crusader = m.join(CharacterId.Crusader, 40, 40)
    m.move(crusader, 40, 40, flags2 = 0x04)
    assertTrue(crusader.hasBarrier)
    m.move(crusader, 40, 40)
    assertFalse(crusader.hasBarrier)
    m.move(crusader, 40, 40, flags2 = 0x04)
    assertFalse("its cooldown holds it", crusader.hasBarrier)
    pressR(crusader)
    m.move(crusader, 40, 40, flags2 = 0x04)
    assertTrue(crusader.hasBarrier)
  }

  @Test def aBarrierUpStaysUpForItsTime(): Unit = {
    minuteRun()
    val p = m.join(CharacterId.Crusader, 20, 20)
    m.move(p, 20, 20, flags2 = 0x04)
    val until = p.getBarrierUntil
    pressR(p)
    assertTrue(p.hasBarrier)
    assertEquals("neither cut short nor drawn out", until, p.getBarrierUntil)
  }

  // --- Its own minute ---

  @Test def theFirstMinuteCountsFromTheMatchsStart(): Unit = {
    val p = m.join(CharacterId.Soldier, 20, 20)
    assertEquals(1, grenade(p))
    m.clearSent()
    pressR(p)
    val refused = refusals(p)
    assertEquals(1, refused.size)
    assertEquals(CooldownResetAction.REJECTED, refused.head.getAction)
    assertTrue(s"how long it still has: ${refused.head.getMsLeft}ms",
      refused.head.getMsLeft > Constants.COOLDOWN_RESET_MS - 5000 && refused.head.getMsLeft <= Constants.COOLDOWN_RESET_MS)
    assertEquals("nothing was reset", 0, grenade(p))
  }

  @Test def practiceHasItFromTheStart(): Unit = {
    // Practice is where a player tries a character out, R and all: it is taken the moment practice
    // begins, and is on its minute from then, as anywhere
    val practice = new TestMatch(practice = true)
    val p = practice.join(CharacterId.Soldier, 20, 20)
    assertEquals(1, practice.fire(p, AttackSlot.Q, ProjectileType.GRENADE, east).size)
    practice.clearSent()
    pressR(p, practice)
    assertTrue("taken at once", refusals(p, practice).isEmpty)
    assertEquals("the grenade is ready again", 1, practice.fire(p, AttackSlot.Q, ProjectileType.GRENADE, east).size)
    pressR(p, practice)
    val refused = refusals(p, practice)
    assertEquals(1, refused.size)
    assertTrue(s"then its minute: ${refused.head.getMsLeft}ms", refused.head.getMsLeft > Constants.COOLDOWN_RESET_MS - 5000)
  }

  @Test def itIsOnItsMinuteAgainOncePressed(): Unit = {
    minuteRun()
    val p = m.join(CharacterId.Soldier, 20, 20)
    grenade(p)
    pressR(p)
    assertEquals(1, grenade(p))
    m.clearSent()
    pressR(p)
    val refused = refusals(p)
    assertEquals(1, refused.size)
    assertTrue(refused.head.getMsLeft > Constants.COOLDOWN_RESET_MS - 5000)
    assertEquals("the grenade is still cooling down", 0, grenade(p))
  }

  @Test def itIsTakenAfterFourFifthsOfItsMinuteAsACastIs(): Unit = {
    // Every attack's clock lets a cast through after 80% of its cooldown, for the wire's slack
    m.instance.startedAtMs -= (Constants.COOLDOWN_RESET_MS * 0.8).toLong + 50
    val p = m.join(CharacterId.Soldier, 20, 20)
    grenade(p)
    m.clearSent()
    pressR(p)
    assertTrue(refusals(p).isEmpty)
    assertEquals(1, grenade(p))
  }

  @Test def aDeathDoesNotGiveItBack(): Unit = {
    minuteRun()
    val p = m.join(CharacterId.Soldier, 20, 20)
    grenade(p)
    pressR(p)
    p.setHealth(0)
    m.instance.respawn(p.getId)
    assertFalse(p.isDead)
    grenade(p)
    m.clearSent()
    pressR(p)
    assertEquals("still on its minute", 1, refusals(p).size)
    assertEquals(0, grenade(p))
  }

  @Test def norDoesARejoin(): Unit = {
    minuteRun()
    val p = m.join(CharacterId.Soldier, 20, 20)
    grenade(p)
    pressR(p)
    m.rejoin(p)
    grenade(p)
    m.clearSent()
    pressR(p)
    assertEquals(1, refusals(p).size)
  }

  // --- Who can't ---

  @Test def theHeldThePhasedAndTheDeadCantAndAreTold(): Unit = {
    minuteRun()
    val p = m.join(CharacterId.Soldier, 20, 20)
    grenade(p)
    m.clearSent()

    p.setFrozenUntil(System.currentTimeMillis() + 5000)
    pressR(p)
    p.setFrozenUntil(0)
    p.setPhasedUntil(System.currentTimeMillis() + 3000)
    pressR(p)
    p.setPhasedUntil(0)
    p.setHealth(0)
    pressR(p)
    p.setHealth(p.getMaxHealth)

    val refused = refusals(p)
    assertEquals(3, refused.size)
    assertTrue("none of them for its minute, which had run", refused.forall(_.getMsLeft == 0))
    assertEquals("nothing was reset", 0, grenade(p))
    // And none of it spent R: it goes now
    pressR(p)
    assertTrue(refusals(p).isEmpty)
    assertEquals(1, grenade(p))
  }

  @Test def aPhasesLastMomentsDontHoldItBack(): Unit = {
    // The client's phase ends a trip across the wire before ours, as for a shot
    minuteRun()
    val p = m.join(CharacterId.Wraith, 20, 20)
    m.fire(p, AttackSlot.E, ProjectileType.HAUNT, east)
    m.clearSent()
    p.setPhasedUntil(System.currentTimeMillis() + 100)
    pressR(p)
    assertTrue(refusals(p).isEmpty)
    assertEquals(1, m.fire(p, AttackSlot.E, ProjectileType.HAUNT, east).size)
  }

  @Test def nobodyInAFreeForAllsCeasefire(): Unit = {
    val opening = new TestMatch(opening = true)
    minuteRun(opening)
    val p = opening.join(CharacterId.Soldier, 20, 20)
    opening.clearSent()
    pressR(p, opening)
    val refused = refusals(p, opening)
    assertEquals(1, refused.size)
    assertEquals(0, refused.head.getMsLeft)
  }

  @Test def onlyARequestIsTakenFromAClient(): Unit = {
    minuteRun()
    val p = m.join(CharacterId.Soldier, 20, 20)
    grenade(p)
    seq += 1
    m.instance.handler.processPacket(new CooldownResetPacket(seq, p.getId, CooldownResetAction.REJECTED, 0), null, null)
    assertEquals(0, grenade(p))
  }

  @Test def itGoesInThroughTheServersOwnHandlerAndItsAnswerComesBack(): Unit = {
    // Signed and numbered as a client sends it, through the real TCP handler: the router hands it to
    // the player's match, and the match's refusal goes back down the same connection
    val s = new ServerTestKit.TestSession()
    m.seat(Seq(s)) // a Gladiator, whose E is the Rope
    val p = m.instance.registry.get(s.playerId)
    assertEquals(1, m.fire(p, AttackSlot.E, ProjectileType.ROPE, east).size)
    s.received()
    s.send(n => new CooldownResetPacket(n, s.playerId, CooldownResetAction.REQUEST, 0))
    val answer = s.received().collect { case r: CooldownResetPacket => r }
    assertEquals("on its first minute", 1, answer.size)
    assertTrue(answer.head.getMsLeft > Constants.COOLDOWN_RESET_MS - 5000)
    minuteRun()
    s.send(n => new CooldownResetPacket(n, s.playerId, CooldownResetAction.REQUEST, 0))
    assertTrue(s.received().collect { case r: CooldownResetPacket => r }.isEmpty)
    assertEquals("the Rope is ready again", 1, m.fire(p, AttackSlot.E, ProjectileType.ROPE, east).size)
  }

  // --- Bots ---

  /** Grenades a Soldier bot throws at a Spaceman 6 cells off over a few of its ticks. */
  private def botGrenades(tm: TestMatch): Int = {
    val bot = tm.join(CharacterId.Soldier, 20, 20, id = new UUID(0L, 7L))
    tm.join(CharacterId.Spaceman, 26, 20)
    val bots = new BotController(tm.instance)
    try {
      bots.addBotId(bot.getId)
      val clock = System.currentTimeMillis()
      for (k <- 0 until 4) bots.tick(clock + 100L * k)
      tm.instance.projectileManager.getAll.count(_.projectileType == ProjectileType.GRENADE)
    } finally bots.stop()
  }

  @Test def aBotPressesItOnceBothAreSpentInAFight(): Unit = {
    assertEquals("on its minute, one grenade and then its cooldown", 1, botGrenades(new TestMatch()))
    val later = new TestMatch()
    minuteRun(later)
    assertEquals("past it, the grenade and the rocket, R, and both again", 2, botGrenades(later))
    assertEquals(2, later.instance.projectileManager.getAll.count(_.projectileType == ProjectileType.ROCKET))
  }
}
