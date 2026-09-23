package com.gridgame.common.model

import org.junit.Assert._
import org.junit.Test

/**
 * The roster's numbers: every character has a role, the role follows how far their primary
 * reaches, and it sets their health and pace. Ranged characters trade health for their range and
 * walk a little quicker, so they can kite; melee characters, who have to get into range, get the
 * health to survive getting there, and need a dash, a pull or an item to do it. Skirmishers (the
 * boulder-throwing bruisers) share melee health and sit between the two in pace.
 */
class RosterBalanceTest {
  import CombatRole.{Melee, Ranged, Skirmisher}

  private val all = CharacterDef.all
  private def ofRole(r: CombatRole): Seq[CharacterDef] = all.filter(_.role == r)

  /** As far as the character's primary can reach: charged, for those that charge. */
  private def reach(c: CharacterDef): Double = {
    val p = ProjectileDef.get(c.primaryProjectileType)
    Math.max(p.maxRange.toDouble, p.effectiveMaxRange(100))
  }

  /** Characters whose role isn't the one their primary's reach reads as. */
  private val exceptions: Map[CharacterId, CombatRole] = Map(
    // A 6-cell punch that reaches 10 fully charged: a martial artist, not a skirmisher
    CharacterId.Monk -> Melee
  )

  @Test def everyCharacterHasARoleThatFollowsTheirReach(): Unit = {
    for (c <- all) {
      assertNotNull(c.displayName, c.role)
      val expected = exceptions.getOrElse(c.id, CombatRole.forRange(reach(c)))
      assertEquals(s"${c.displayName}, whose primary reaches ${reach(c)}", expected, c.role)
    }
  }

  @Test def theExceptionsAreExceptions(): Unit = {
    // One that the rule gets right anyway no longer needs listing, and hides the next real one
    for ((id, role) <- exceptions) {
      val c = CharacterDef.get(id)
      assertNotEquals(c.displayName, CombatRole.forRange(reach(c)), role)
      assertEquals(c.displayName, role, c.role)
    }
  }

  @Test def everyRoleHasCharacters(): Unit =
    CombatRole.all.foreach(r => assertTrue(r.name, ofRole(r).nonEmpty))

  @Test def theReachRuleSplitsAtSixAndTen(): Unit = {
    assertEquals(Melee, CombatRole.forRange(3))
    assertEquals(Melee, CombatRole.forRange(6))
    assertEquals(Skirmisher, CombatRole.forRange(7))
    assertEquals(Skirmisher, CombatRole.forRange(10))
    assertEquals(Ranged, CombatRole.forRange(11))
    assertEquals(Ranged, CombatRole.forRange(28))
  }

  // --- Health ---

  @Test def healthSitsInItsRolesBand(): Unit = {
    for (c <- all) {
      val hp = c.maxHealth
      assertTrue(s"${c.displayName}: $hp in ${c.role.minHealth}-${c.role.maxHealth}",
        hp >= c.role.minHealth && hp <= c.role.maxHealth)
      assertEquals(s"${c.displayName}: $hp, a multiple of 5", 0, hp % 5)
    }
    assertEquals((60, 80), (Ranged.minHealth, Ranged.maxHealth))
    assertEquals((105, 150), (Melee.minHealth, Melee.maxHealth))
    assertEquals((105, 150), (Skirmisher.minHealth, Skirmisher.maxHealth))
  }

  @Test def theToughestRangedCharacterIsWellShortOfTheFrailestFighter(): Unit = {
    val toughestRanged = ofRole(Ranged).maxBy(_.maxHealth)
    val frailestFighter = (ofRole(Melee) ++ ofRole(Skirmisher)).minBy(_.maxHealth)
    assertTrue(s"${toughestRanged.displayName} (${toughestRanged.maxHealth}) is at least 20 below " +
      s"${frailestFighter.displayName} (${frailestFighter.maxHealth})",
      toughestRanged.maxHealth + 20 <= frailestFighter.maxHealth)
  }

  // --- Pace ---

  @Test def theRangedOutpaceSkirmishersWhoOutpaceMelee(): Unit = {
    // So a ranged character can walk away from anyone who has to close in on them
    val melee = ofRole(Melee).map(_.moveSpeed)
    val skirmisher = ofRole(Skirmisher).map(_.moveSpeed)
    val ranged = ofRole(Ranged).map(_.moveSpeed)
    assertTrue(s"every ranged pace ${ranged.distinct} above every melee one ${melee.distinct}",
      ranged.min > melee.max)
    assertTrue(s"skirmishers ${skirmisher.distinct} in between", skirmisher.max < ranged.min && skirmisher.min > melee.max)
  }

  private def interval(speed: Float): Int = Movement.stepIntervalMs(speed, charging = false, chargeLevel = 0,
    phased = false, speedBoost = false, slowed = false, slowMultiplier = 1f)

  @Test def eachRoleStepsAtItsOwnInterval(): Unit = {
    assertEquals("melee", 53, interval(Melee.speed))
    assertEquals("skirmisher", 52, interval(Skirmisher.speed))
    assertEquals("ranged", 50, interval(Ranged.speed))
  }

  @Test def butOnlyJustQuicker(): Unit = {
    // Walking away, the slowest ranged character opens the gap on the quickest melee one, but only
    // by a cell or so a second: it can kite for as long as both only walk, and what closes the gap
    // is a melee character's abilities, not a race it could never win or lose by much
    val cellsPerSec = (speed: Float) => 1000.0 / interval(speed)
    val gap = cellsPerSec(ofRole(Ranged).map(_.moveSpeed).min) - cellsPerSec(ofRole(Melee).map(_.moveSpeed).max)
    assertTrue(s"$gap cells a second", gap > 0.5 && gap < 2.0)
  }
}
