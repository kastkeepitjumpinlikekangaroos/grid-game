package com.gridgame.common.model

import org.junit.Assert._
import org.junit.Test

/**
 * The two halves of the map a team match is played in, and the wall that stands between them
 * while it opens. The wall hangs off the world, so the whole game's walkability — steps, blinks,
 * trap throws, spawn points — refuses its cells without knowing it is there; what ignores walls
 * (a star's jump, a phase) is held by the side rule instead.
 */
class TeamDividerTest {
  private val world = WorldData.createEmpty(60, 60)
  private val now = System.currentTimeMillis()
  private def raise(msLeft: Long = 30000L): TeamDivider = {
    world.divider = TeamDivider.raise(world, now + msLeft)
    world.divider
  }

  // --- The halves ---

  @Test def theMapIsSplitDownTheMiddle(): Unit = {
    assertTrue("a square map splits on x", TeamDivider.axisX(world))
    assertEquals(30, TeamDivider.line(world))
    assertEquals(-1, TeamDivider.sideOf(world, 29, 0))
    assertEquals(0, TeamDivider.sideOf(world, 30, 59))
    assertEquals(1, TeamDivider.sideOf(world, 31, 40))
  }

  @Test def aTallMapSplitsAcrossItsLongerAxis(): Unit = {
    val tall = WorldData.createEmpty(40, 90)
    assertFalse(TeamDivider.axisX(tall))
    assertEquals(45, TeamDivider.line(tall))
    assertEquals(-1, TeamDivider.sideOf(tall, 39, 44))
    assertEquals(1, TeamDivider.sideOf(tall, 0, 46))
  }

  @Test def eachTeamOwnsOneHalf(): Unit = {
    assertEquals(-1, TeamDivider.sideOfTeam(1))
    assertEquals(1, TeamDivider.sideOfTeam(2))
    assertEquals("nobody's team is at home anywhere", 0, TeamDivider.sideOfTeam(0))
  }

  @Test def aSpawnPointLandsInItsTeamsOwnHalf(): Unit = {
    raise()
    for (team <- Seq[Byte](1, 2)) {
      val side = TeamDivider.sideOfTeam(team)
      for (_ <- 0 until 30) {
        val p = world.getValidSpawnPoint(Set.empty, (x, y) => TeamDivider.sideOf(world, x, y) == side)
        assertEquals(s"team $team spawned at $p", side, TeamDivider.sideOf(world, p.getX, p.getY))
        assertTrue(s"and not inside the wall: $p", world.isWalkable(p))
      }
    }
  }

  // --- The wall ---

  @Test def theWorldRefusesTheCellsItStandsOn(): Unit = {
    val d = raise()
    assertFalse(world.isWalkable(30, 12))
    assertTrue(world.isWalkable(29, 12))
    assertTrue(world.isWalkable(31, 12))
    assertTrue("the tile itself is untouched", world.getTile(30, 12).walkable)
    assertTrue(d.blocks(30, 12))
  }

  @Test def itLapsesOnItsOwn(): Unit = {
    world.divider = TeamDivider.raise(world, now - 1)
    assertFalse(world.divider.up)
    assertTrue("the ground is open again", world.isWalkable(30, 12))
    assertFalse(world.divider.stops(29, 12, 31, 12))
  }

  @Test def nobodyCrossesIt(): Unit = {
    val d = raise()
    assertFalse("a step across", d.allowsMove(29, 12, 31, 12))
    assertFalse("from the other side", d.allowsMove(31, 12, 29, 12))
    assertFalse("or into the wall itself", d.allowsMove(29, 12, 30, 12))
    assertFalse("or a dash clean over it", d.allowsMove(25, 12, 35, 12))
    assertTrue("along your own side", d.allowsMove(29, 12, 29, 13))
    assertTrue("and anyone it came up on top of can step off", d.allowsMove(30, 12, 29, 12))
  }

  @Test def aShotAcrossMeetsIt(): Unit = {
    val d = raise()
    assertEquals(0.4f, d.crossing(28f, 12f, 33f, 12f), 0.001f)
    assertTrue("and from the other side too", d.crosses(33f, 12f, 28f, 12f))
    assertFalse("one that stays on its side doesn't", d.crosses(20f, 12f, 29f, 40f))
    assertFalse("nor one flying away from it", d.crosses(29f, 12f, 10f, 12f))
  }

  // --- What the world's walkability already covers ---

  @Test def aBlinkStopsShortOfIt(): Unit = {
    raise()
    assertEquals(new Position(29, 12), Teleport.blinkTarget(world, 25, 12, 1.0, 0.0, 8))
  }

  @Test def aStarLandsShortOfIt(): Unit = {
    raise()
    // A star jumps over whatever lies between, so the wall has to turn it back by hand
    assertEquals(Some(new Position(29, 12)), Teleport.starTarget(world, 25, 12, 40.0, 12.0))
    assertFalse(Teleport.isValidStarTarget(world, 25, 12, 35, 12))
    assertTrue(Teleport.isValidStarTarget(world, 25, 12, 29, 12))
  }

  @Test def aTrapCannotBeThrownOverIt(): Unit = {
    raise()
    assertEquals(Some(new Position(29, 12)), TrapPlacement.target(world, 25, 12, 34.0, 12.0, 6))
    assertFalse(TrapPlacement.isValidTarget(world, 27, 12, 32, 12, 6))
    assertTrue(TrapPlacement.isValidTarget(world, 27, 12, 29, 12, 6))
  }

  @Test def aKnockbackStopsAtIt(): Unit = {
    raise()
    assertEquals(new Position(29, 12), Teleport.slide(world, 27, 12, 1.0, 0.0, 6))
  }
}
