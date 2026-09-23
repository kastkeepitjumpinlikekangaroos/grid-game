package com.gridgame.client

import com.gridgame.common.model._
import com.gridgame.common.protocol._
import org.junit.Assert._
import org.junit.Test

/**
 * The client's copy of a match's opening ([[MatchOpening]]): the wall a Teams match is played
 * around, and the holstered abilities a free-for-all opens with. The server says how long it has
 * left and what it does; each side works the wall's shape out from the world it loaded, so nothing
 * about its geometry crosses the wire. The wall hangs off our world, which is what stops us walking
 * into it — a step the server would refuse anyway, and refusing it here is what keeps us from
 * rubber-banding.
 */
class GameClientOpeningTest {
  private val t = new TestClient()
  private val c = t.client
  private val MID = 30 // the divider's line on the 60x60 test world

  private def impactSeen: Boolean =
    (0 until c.DIVIDER_IMPACT_SLOTS).exists(i => c.getDividerImpactTime(i) > 0L)

  @Test def theWallStandsWhereBothSidesKnowItIs(): Unit = {
    t.startMatch(spawn = (MID - 2, 30), team = 1)
    assertNull("nothing until the server says so", c.divider)
    t.opening(30000)
    val d = c.divider
    assertNotNull(d)
    assertTrue(d.axisX)
    assertEquals(MID, d.at)
    assertTrue(s"${c.openingMsLeft}ms left", c.openingMsLeft > 29000 && c.openingMsLeft <= 30000)
  }

  @Test def itIsPutUpWhicheverArrivesFirst(): Unit = {
    // The world and the word about the divider come down the same connection, either order
    t.lobby(LobbyAction.GAME_STARTING)
    t.opening(30000)
    c.setWorld(t.world)
    assertNotNull(c.divider)
  }

  @Test def weCannotWalkThroughIt(): Unit = {
    t.startMatch(spawn = (MID - 2, 30), team = 1)
    t.opening(30000)
    c.movePlayer(1, 0)
    assertEquals((MID - 1, 30), t.at)
    c.movePlayer(1, 0)
    assertEquals("stopped at the wall", (MID - 1, 30), t.at)
    c.movePlayer(1, 1)
    assertEquals("a diagonal into it slides along it", (MID - 1, 31), t.at)
  }

  @Test def itComesDownWhenTheServerSaysSo(): Unit = {
    t.startMatch(spawn = (MID - 1, 30), team = 1)
    t.opening(30000)
    c.movePlayer(1, 0)
    assertEquals((MID - 1, 30), t.at)
    t.opening(0)
    assertEquals("nothing left of it", 0L, c.openingMsLeft)
    c.movePlayer(1, 0)
    assertEquals((MID, 30), t.at)
  }

  @Test def aShotStoppedOnItBelongsToNobody(): Unit = {
    t.startMatch(spawn = (MID - 4, 30), team = 1)
    t.opening(30000)
    assertFalse(impactSeen)
    // BLOCKED with no holder: the divider, not somebody's barrier
    t.projectile(ProjectileAction.BLOCKED, 11, t.id, x = MID.toFloat, y = 30f, target = null)
    assertTrue("kept for the flash where it struck", impactSeen)
  }

  // --- A free-for-all: a ceasefire, and nothing but walking ---

  @Test def aFreeForAllOpensWithNobodyAbleToAttack(): Unit = {
    c.selectedCharacterId = CharacterId.Soldier.id
    t.startMatch(spawn = (10, 10))
    t.opening(30000, MatchOpening.NO_ATTACKS)
    assertTrue(c.attacksLocked)
    assertNull("and no wall: there are no sides to keep apart", c.divider)
    t.clearSent()

    c.setMouseWorldPosition(20.0, 10.0)
    c.shootToward(1f, 0f)
    c.shootAllDirections()
    c.shootAbility(0)
    c.shootAbility(1)
    assertTrue("nothing attacks at all", t.sentSpawns.isEmpty)
    assertEquals("and no cooldown is spent on any of it", 0f, c.getQCooldownFraction, 0.001f)
    assertEquals(0f, c.getECooldownFraction, 0.001f)
    assertFalse("the burst doesn't root us in place either", c.isMovementBlocked)
  }

  @Test def aChargeDoesNotEvenStart(): Unit = {
    // A bar that fills and then fires nothing is worse than a button that does nothing
    c.selectedCharacterId = CharacterId.Wizard.id
    t.startMatch(spawn = (10, 10))
    t.opening(30000, MatchOpening.NO_ATTACKS)
    c.startCharging()
    assertFalse(c.isCharging)
    t.opening(0, MatchOpening.NO_ATTACKS)
    c.startCharging()
    assertTrue(c.isCharging)
  }

  @Test def everythingComesBackWhenTheOpeningIsOver(): Unit = {
    c.selectedCharacterId = CharacterId.Soldier.id
    t.startMatch(spawn = (10, 10))
    t.opening(30000, MatchOpening.NO_ATTACKS)
    t.opening(0, MatchOpening.NO_ATTACKS)
    assertFalse(c.attacksLocked)
    t.clearSent()
    c.setMouseWorldPosition(20.0, 10.0)
    c.shootToward(1f, 0f)
    c.shootAbility(1)
    assertEquals(1, t.sentSpawns.count(_.getAttackSlot == AttackSlot.PRIMARY))
    assertEquals(1, t.sentSpawns.count(_.getAttackSlot == AttackSlot.E))
  }

  @Test def aTeamsOpeningHoldsNobodysFire(): Unit = {
    c.selectedCharacterId = CharacterId.Soldier.id
    t.startMatch(spawn = (10, 10), team = 1)
    t.opening(30000, MatchOpening.DIVIDER)
    assertFalse("the wall is what keeps that match apart", c.attacksLocked)
    t.clearSent()
    c.setMouseWorldPosition(20.0, 10.0)
    c.shootToward(1f, 0f)
    c.shootAbility(1)
    assertEquals(1, t.sentSpawns.count(_.getAttackSlot == AttackSlot.PRIMARY))
    assertEquals(1, t.sentSpawns.count(_.getAttackSlot == AttackSlot.E))
  }

  @Test def aNewMatchStartsWithoutOne(): Unit = {
    t.startMatch(spawn = (MID - 2, 30), team = 1)
    t.opening(30000)
    assertNotNull(c.divider)
    t.startMatch(spawn = (MID + 2, 30), team = 2)
    assertNull("a free-for-all, or a team match that hasn't said yet", c.divider)
    assertFalse(c.attacksLocked)
    c.movePlayer(-1, 0)
    assertEquals((MID + 1, 30), t.at)
  }
}
