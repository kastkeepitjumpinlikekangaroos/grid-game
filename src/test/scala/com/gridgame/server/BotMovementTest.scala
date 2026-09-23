package com.gridgame.server

import com.gridgame.common.model._
import org.junit.Assert._
import org.junit.Test

/**
 * How bots walk: at their character's pace, on a step every other one a player of that character
 * would take, and by their role. Melee closes in, ranged keeps its distance, and a skirmisher
 * closes to throwing range and holds its ground there.
 *
 * Driven on a clock of the test's own (BotController.tick(now)), a 100ms tick at a time, so ten
 * seconds of walking take no time at all.
 */
class BotMovementTest {
  /** A minute on from now: the clock a bot is added with is long past by then, so its first step
    * starts afresh. */
  private def later: Long = System.currentTimeMillis() + 60000

  /** How many cells a bot of `character` covers in `ticks` of 100ms, chasing someone who stands
    * still at the far end of a long empty strip. */
  private def cellsIn(character: CharacterId, ticks: Int): Int = {
    val m = new TestMatch(WorldData.createEmpty(160, 20))
    val bot = m.join(character, 2, 10)
    m.join(CharacterId.Spaceman, 157, 10)
    val bots = new BotController(m.instance)
    try {
      bots.addBotId(bot.getId)
      val start = later
      for (k <- 0 until ticks) bots.tick(start + 100L * k)
      bot.getPosition.getX - 2
    } finally bots.stop()
  }

  @Test def eachRoleWalksAtItsOwnPace(): Unit = {
    // Ten seconds of ticks, the first step at once: every 100ms ranged, 104ms skirmisher, 106ms
    // melee. Counted from the tick, as they were, a 104ms or 106ms step waited for the second tick
    // after the last, and both came to 50: half the pace, where it is meant to be a few percent.
    assertEquals("ranged", 100, cellsIn(CharacterId.Soldier, 100))
    assertEquals("skirmisher", 96, cellsIn(CharacterId.Golem, 100))
    assertEquals("melee", 94, cellsIn(CharacterId.Barbarian, 100))
  }

  @Test def aBoostedBotTakesTwoStepsInATickWhenItOwesThem(): Unit = {
    // A speed boost brings a melee bot's step to 64ms, under the tick
    val m = new TestMatch(WorldData.createEmpty(220, 20))
    val bot = m.join(CharacterId.Barbarian, 2, 10)
    m.join(CharacterId.Spaceman, 217, 10)
    val bots = new BotController(m.instance)
    try {
      bots.addBotId(bot.getId)
      bot.setSpeedBoostUntil(System.currentTimeMillis() + 60000)
      val start = later
      for (k <- 0 until 100) bots.tick(start + 100L * k)
      assertEquals(1 + 9900 / 64, bot.getPosition.getX - 2)
    } finally bots.stop()
  }

  @Test def aBotLongOffItsFeetTakesOneStepNotTheOnesItMissed(): Unit =
    assertEquals(1, cellsIn(CharacterId.Barbarian, 1))

  /** How far from an enemy two cells off a bot of `character` stands after its first step. */
  private def distanceAfterAStep(character: CharacterId): Double = {
    val m = new TestMatch()
    val bot = m.join(character, 30, 30)
    m.join(CharacterId.Spaceman, 32, 30)
    val bots = new BotController(m.instance)
    try {
      bots.addBotId(bot.getId)
      bots.tick(later)
      val dx = bot.getPosition.getX - 32
      val dy = bot.getPosition.getY - 30
      Math.sqrt(dx * dx + dy * dy)
    } finally bots.stop()
  }

  @Test def aMeleeBotPhasesToCloseOnATargetOutOfReach(): Unit = {
    // The Vampire's Mist Form: twice the pace, toward someone its 3-cell bite can't reach
    val m = new TestMatch(WorldData.createEmpty(80, 20))
    val bot = m.join(CharacterId.Vampire, 10, 10)
    m.join(CharacterId.Spaceman, 22, 10)
    val bots = new BotController(m.instance)
    try {
      bots.addBotId(bot.getId)
      val start = later
      bots.tick(start)
      assertTrue("phased", bot.isPhased)
      val from = bot.getPosition.getX
      for (k <- 1 to 5) bots.tick(start + 100L * k)
      // 106ms a step on foot, 54 phased: two steps a tick instead of one
      assertTrue(s"walked ${bot.getPosition.getX - from} cells in half a second", bot.getPosition.getX - from >= 9)
    } finally bots.stop()
  }

  @Test def aRangedBotKeepsItsPhaseForGettingAway(): Unit = {
    // The Wraith's Phase Shift: not spent walking toward someone it can already shoot
    val m = new TestMatch(WorldData.createEmpty(80, 20))
    val bot = m.join(CharacterId.Wraith, 10, 10)
    m.join(CharacterId.Spaceman, 22, 10)
    val bots = new BotController(m.instance)
    try {
      bots.addBotId(bot.getId)
      bots.tick(later)
      assertFalse(bot.isPhased)
    } finally bots.stop()
  }

  @Test def meleeClosesInRangedBacksOffAndASkirmisherHoldsItsGround(): Unit = {
    assertTrue("melee closes in", distanceAfterAStep(CharacterId.Barbarian) < 1.5)
    assertTrue("ranged backs off", distanceAfterAStep(CharacterId.Soldier) > 2.5)
    // A boulder thrower used to walk into arm's length like a melee bot; it circles instead
    val skirmisher = distanceAfterAStep(CharacterId.Golem)
    assertTrue(s"a skirmisher does neither: $skirmisher", skirmisher > 1.5 && skirmisher < 2.5)
  }
}
