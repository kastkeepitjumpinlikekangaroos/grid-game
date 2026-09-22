package com.gridgame.client

import com.gridgame.common.model._
import com.gridgame.common.protocol._
import org.junit.Assert._
import org.junit.Test

import java.util.UUID

/** Traps as the client throws them and as it is told about everyone else's. */
class GameClientTrapTest {
  private val t = new TestClient()
  private val c = t.client
  private val other = UUID.randomUUID()

  /** A Warden at (20, 20), whose E throws a bear trap 6 cells. */
  private def warden(aimX: Double = 24, aimY: Double = 20): Unit = {
    c.selectedCharacterId = CharacterId.Warden.id
    t.startMatch(spawn = (20, 20))
    c.setMouseWorldPosition(aimX, aimY)
    t.clearSent()
  }

  private def placements: Seq[TrapPacket] = t.sentTraps.filter(_.getAction == TrapAction.PLACE)

  // --- Throwing one ---

  @Test def castingThrowsItAtTheCursor(): Unit = {
    warden(aimX = 24, aimY = 20)
    c.shootAbility(1)
    assertEquals(1, placements.size)
    val p = placements.head
    assertEquals((24, 20), (p.getX, p.getY))
    assertEquals(TrapType.BEAR_TRAP, p.getTrapType)
    assertEquals("named by the attack that threw it", AttackSlot.E, p.getAttackSlot)
    assertTrue("it fires nothing", t.sentSpawns.isEmpty)
    assertTrue("and the cooldown is running", c.getECooldownFraction > 0.9f)
  }

  @Test def aimingPastItsRangeLandsItShort(): Unit = {
    warden(aimX = 40, aimY = 20)
    c.shootAbility(1)
    assertEquals("six cells, no further", (26, 20), (placements.head.getX, placements.head.getY))
  }

  @Test def aimingThroughAWallStopsItBeforeTheWall(): Unit = {
    warden(aimX = 26, aimY = 20)
    c.getWorld.setTile(23, 20, Tile.Wall)
    c.shootAbility(1)
    assertEquals((22, 20), (placements.head.getX, placements.head.getY))
  }

  @Test def aCastWithNowhereToPutItSendsNothingAndKeepsTheCooldown(): Unit = {
    // Nowhere at all: standing inside a wall, aiming at their own feet. Anything else lands
    // somewhere, if only at the caster's own feet — so this is the one case that must not
    // spend the ability.
    warden(aimX = 20, aimY = 20)
    c.getWorld.setTile(20, 20, Tile.Wall)
    c.shootAbility(1)
    assertTrue("nothing was sent", t.sent.isEmpty)
    assertEquals("and the ability is still ready", 0f, c.getECooldownFraction, 0.001f)
  }

  // --- Being told about them ---

  @Test def trapsTheServerSendsGoOnAndComeOffTheGround(): Unit = {
    warden()
    t.trap(TrapAction.SPAWN, 7, TrapType.MINE, who = other, x = 30, y = 31)
    assertEquals(1, c.getTraps.size())
    val trap = c.getTraps.get(7)
    assertEquals(30, trap.x)
    assertEquals(TrapType.MINE, trap.trapType)
    assertEquals(other, trap.ownerId)
    assertFalse("someone else's", c.isFriendlyTrap(trap))
    assertTrue("and not live yet", !trap.isArmed(System.currentTimeMillis()))

    t.trap(TrapAction.TRIGGER, 7, TrapType.MINE, who = other, x = 30, y = 31, victim = t.id)
    assertTrue("sprung and gone", c.getTraps.isEmpty)

    t.trap(TrapAction.SPAWN, 8, who = other, x = 32, y = 31)
    assertEquals(1, c.getTraps.size())
    t.trap(TrapAction.REMOVE, 8, who = other, x = 32, y = 31)
    assertTrue("and taken away again", c.getTraps.isEmpty)
  }

  @Test def ourOwnAndOurAlliesAreTheOnesWeSeePlainly(): Unit = {
    warden()
    t.trap(TrapAction.SPAWN, 1, who = t.id, x = 24, y = 20)
    assertTrue(c.isFriendlyTrap(c.getTraps.get(1)))
    assertEquals("ours is the one the slot counts", 1, c.myTrapCount)
    t.trap(TrapAction.SPAWN, 2, who = other, x = 26, y = 20)
    assertEquals(1, c.myTrapCount)
    assertFalse(c.isFriendlyTrap(c.getTraps.get(2)))
  }

  @Test def anAlliesTrapIsOursToSee(): Unit = {
    c.selectedCharacterId = CharacterId.Warden.id
    t.startMatch(spawn = (20, 20), team = 1)
    t.trap(TrapAction.SPAWN, 3, who = other, x = 24, y = 20, team = 1)
    assertTrue(c.isFriendlyTrap(c.getTraps.get(3)))
    assertEquals("but it is not one of ours", 0, c.myTrapCount)
  }

  @Test def aRefusedPlacementGivesTheCooldownBack(): Unit = {
    warden()
    c.shootAbility(1)
    assertTrue(c.getECooldownFraction > 0.9f)
    t.receive(new TrapPacket(1, t.id, Packet.getCurrentTimestamp, 24, 20, 0, TrapAction.REJECTED,
      TrapType.BEAR_TRAP, 0.toByte, AttackSlot.E, null))
    // Nothing was placed, so the twelve seconds are given back — but not this instant. The
    // reason a placement was refused often hasn't gone away (a trap is already on that cell),
    // and a cooldown handed back whole turns a held key into a request every frame.
    assertTrue("nearly all of it back", c.getECooldownRemaining < 0.6f)
    assertTrue("but not instantly", c.getECooldownRemaining > 0f)
    t.clearSent()
    c.shootAbility(1)
    assertTrue("so a retry in the same frame sends nothing", t.sentTraps.isEmpty)
    Thread.sleep(450)
    c.shootAbility(1)
    assertEquals("and a moment later it goes", 1, t.sentTraps.size)
  }

  @Test def theGroundIsClearedWhenAMatchStarts(): Unit = {
    warden()
    t.trap(TrapAction.SPAWN, 9, who = other, x = 24, y = 20)
    assertEquals(1, c.getTraps.size())
    t.startMatch(spawn = (20, 20))
    assertTrue("last match's traps are not this match's", c.getTraps.isEmpty)
  }
}
