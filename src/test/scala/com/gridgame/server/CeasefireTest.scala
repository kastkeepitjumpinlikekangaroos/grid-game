package com.gridgame.server

import com.gridgame.common.model._
import com.gridgame.common.protocol._
import org.junit.Assert._
import org.junit.Test

/**
 * A free-for-all opens with a ceasefire ([[MatchOpening]]): for the first thirty seconds nobody
 * can attack at all — not the primary, not a charge, not the burst shot, and not an ability,
 * whatever that ability does (a projectile, a trap, a phase, a dash, a blink, a barrier).
 * Everyone gets the same half minute to find their feet and pick their ground.
 */
class CeasefireTest {
  private val m = new TestMatch(opening = true) // a free-for-all: gameMode 0

  /** Run the opening out: everything comes back. */
  private def endOpening(): Unit = m.instance.openingEndsAt = System.currentTimeMillis() - 1

  private def east = Seq((1f, 0f))

  // --- What is held ---

  @Test def nothingIsFiredAtAll(): Unit = {
    val p = m.join(CharacterId.Soldier, 20, 20)
    assertTrue(m.instance.attacksLocked)
    assertTrue("the primary", m.fire(p, AttackSlot.PRIMARY, ProjectileType.BULLET, east).isEmpty)
    assertTrue("its burst", m.fire(p, AttackSlot.BURST, ProjectileType.BULLET, AttackSlot.BurstDirections).isEmpty)
    assertTrue("Q", m.fire(p, AttackSlot.Q, ProjectileType.GRENADE, east).isEmpty)
    assertTrue("E", m.fire(p, AttackSlot.E, ProjectileType.ROCKET, east).isEmpty)
    endOpening()
    assertEquals(1, m.fire(p, AttackSlot.PRIMARY, ProjectileType.BULLET, east).size)
    assertEquals(1, m.fire(p, AttackSlot.Q, ProjectileType.GRENADE, east).size)
  }

  @Test def aTrapIsRefusedAndTheCooldownGivenBack(): Unit = {
    val p = m.join(CharacterId.Warden, 20, 20)
    m.clearSent()
    assertNull(m.placeTrap(p, AttackSlot.E, TrapType.BEAR_TRAP, 22, 20))
    // The refusal comes back, so the client gives the cooldown up again rather than losing it
    assertEquals(1, m.trapEvents(m.sent(p), TrapAction.REJECTED).size)
    endOpening()
    assertNotNull(m.placeTrap(p, AttackSlot.E, TrapType.BEAR_TRAP, 22, 20))
  }

  @Test def aPhaseIsNotHonoured(): Unit = {
    val p = m.join(CharacterId.Wraith, 20, 20)
    m.move(p, 21, 20, flags = 0x08)
    assertFalse("the client says it phased; the server doesn't have it", p.isPhased)
    endOpening()
    m.move(p, 22, 20, flags = 0x08)
    assertTrue(p.isPhased)
  }

  @Test def aBarrierDoesNotGoUp(): Unit = {
    val p = m.join(CharacterId.Crusader, 20, 20)
    m.move(p, 20, 20, flags2 = 0x04)
    assertFalse(p.hasBarrier)
    endOpening()
    m.move(p, 20, 20, flags2 = 0x04)
    assertTrue(p.hasBarrier)
  }

  @Test def aBlinkIsRefusedLikeAnyOtherJump(): Unit = {
    // A jump no walk could have made is allowed only because a blink or a dash could have made
    // it — and neither could, while they are holstered
    val p = m.join(CharacterId.Wizard, 20, 20)
    assertTrue("a step, so the speed check has a moment to measure from", m.move(p, 21, 20))
    assertFalse(m.move(p, 27, 20))
    assertEquals((21, 20), m.at(p))
    endOpening()
    assertTrue(m.move(p, 27, 20))
  }

  // --- What is not ---

  @Test def walkingIsUntouched(): Unit = {
    val p = m.join(CharacterId.Soldier, 20, 20)
    assertTrue(m.move(p, 21, 20))
    assertTrue(m.move(p, 21, 21))
    assertEquals((21, 21), m.at(p))
  }

  @Test def practiceHoldsNothingBack(): Unit = {
    // Practice is for trying a character out. Holding its fire for the first thirty seconds is
    // the one thing an opening must not do there.
    val prac = new TestMatch(opening = true, practice = true)
    assertFalse(prac.instance.attacksLocked)
    val p = prac.join(CharacterId.Soldier, 20, 20)
    assertEquals(1, prac.fire(p, AttackSlot.Q, ProjectileType.GRENADE, east).size)
    prac.clearSent()
    prac.tick()
    assertTrue("and nothing to say about an opening it doesn't have",
      prac.sent(p).collect { case g: GameEventPacket if g.getEventType == GameEvent.MATCH_OPENING => g }.isEmpty)
  }

  @Test def aTeamsMatchHoldsNobodysFire(): Unit = {
    // Teams spends its opening behind a wall instead (TeamDivider): there is nothing to shoot at
    // through it, and everything to shoot with
    val teams = new TestMatch(gameMode = 1, opening = true)
    assertFalse(teams.instance.attacksLocked)
    val p = teams.join(CharacterId.Soldier, 20, 20, team = 1)
    assertEquals(1, teams.fire(p, AttackSlot.PRIMARY, ProjectileType.BULLET, east).size)
    assertEquals(1, teams.fire(p, AttackSlot.Q, ProjectileType.GRENADE, east).size)
  }

  // --- Bots hold their fire too ---

  @Test def botsHoldTheirsAsWell(): Unit = {
    val bot = m.join(CharacterId.Soldier, 20, 20)
    m.join(CharacterId.Spaceman, 26, 20)
    val bots = new BotController(m.instance)
    try {
      bots.addBotId(bot.getId)
      val clock = System.currentTimeMillis() + 60000
      for (k <- 0 until 10) bots.tick(clock + 100L * k)
      assertEquals("it walks and it shoots at nothing", 0, m.instance.projectileManager.size)
      endOpening()
      for (k <- 10 until 20) bots.tick(clock + 100L * k)
      assertTrue("and now it opens fire", m.instance.projectileManager.size > 0)
    } finally bots.stop()
  }

  // --- Saying so ---

  @Test def everyoneIsToldWhatTheOpeningDoesAndWhenItIsOver(): Unit = {
    val p = m.join(CharacterId.Soldier, 20, 20)
    m.clearSent()
    m.tick()
    val openings = m.sent(p).collect { case g: GameEventPacket if g.getEventType == GameEvent.MATCH_OPENING => g }
    assertEquals(1, openings.size)
    assertEquals(MatchOpening.NO_ATTACKS, openings.head.getOpeningRules)
    assertTrue(openings.head.getOpeningMs > 25000)
    endOpening()
    m.tick()
    val over = m.sent(p).collect { case g: GameEventPacket if g.getEventType == GameEvent.MATCH_OPENING => g }
    assertEquals(1, over.size)
    assertEquals(0, over.head.getOpeningMs)
  }
}
