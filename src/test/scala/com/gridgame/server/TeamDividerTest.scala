package com.gridgame.server

import com.gridgame.common.model._
import com.gridgame.common.protocol._
import org.junit.Assert._
import org.junit.Test

/**
 * A team match opens with the two teams walled off from each other: each starts in its own half
 * of the map and, for the first thirty seconds, nothing at all crosses the line between them —
 * no shot, no blast, and nobody's feet. Team 1 holds the low half, team 2 the high one, and a
 * death puts a player back with their own team.
 */
class TeamDividerTest {
  private val m = new TestMatch(gameMode = 1, opening = true)
  private val MID = 30 // the divider's line on a 60x60 map

  /** Run the opening out: the next tick takes the wall down and says so. */
  private def expireDivider(): Unit = {
    m.instance.openingEndsAt = System.currentTimeMillis() - 1
    m.world.divider = TeamDivider.raise(m.world, System.currentTimeMillis() - 1)
  }

  private def unhurt(p: Player): Boolean = p.getHealth == p.getMaxHealth

  private def dividerEvents(packets: Seq[Packet]): Seq[GameEventPacket] =
    packets.collect { case g: GameEventPacket if g.getEventType == GameEvent.MATCH_OPENING => g }

  // --- The halves ---

  @Test def eachTeamStartsInItsOwnHalf(): Unit = {
    var taken = Set.empty[(Int, Int)]
    for (team <- Seq[Byte](1, 2); _ <- 0 until 8) {
      val sp = m.instance.spawnFor(team, taken)
      taken += ((sp.getX, sp.getY))
      assertEquals(s"team $team spawned at $sp", TeamDivider.sideOfTeam(team),
        TeamDivider.sideOf(m.world, sp.getX, sp.getY))
    }
  }

  @Test def aDeathPutsYouBackWithYourTeam(): Unit = {
    val p = m.join(CharacterId.Soldier, 20, 30, team = 1)
    p.setPosition(new Position(MID + 5, 30)) // however they ended up over there
    m.instance.respawn(p.getId)
    assertEquals("back on their own side", -1, TeamDivider.sideOf(m.world, m.at(p)._1, m.at(p)._2))
  }

  @Test def aFreeForAllHasNoDivider(): Unit = {
    val ffa = new TestMatch()
    assertNull(ffa.instance.divider)
    val a = ffa.join(CharacterId.Soldier, MID - 3, 30)
    val b = ffa.join(CharacterId.Soldier, MID + 3, 30)
    ffa.tickUntilGone(ffa.launch(a, ProjectileType.BULLET, 1f, 0f))
    assertFalse("nothing stands in the middle of the map", unhurt(b))
  }

  // --- What the wall stops ---

  @Test def aShotAtTheOtherTeamIsStopped(): Unit = {
    val a = m.join(CharacterId.Soldier, MID - 3, 30, team = 1)
    val b = m.join(CharacterId.Soldier, MID + 3, 30, team = 2)
    m.clearSent()
    m.tickUntilGone(m.launch(a, ProjectileType.BULLET, 1f, 0f))
    assertTrue("never touched", unhurt(b))
    val blocked = m.projectileEvents(m.sent(a), ProjectileAction.BLOCKED)
    assertEquals(1, blocked.size)
    assertNull("nobody's barrier: the wall between the teams", blocked.head.getTargetId)
    assertEquals("stopped on the line", MID.toFloat, blocked.head.getX, 0.2f)
  }

  @Test def evenOneThatFliesOverWallsIsStopped(): Unit = {
    val a = m.join(CharacterId.Lich, MID - 3, 30, team = 1)
    val b = m.join(CharacterId.Soldier, MID + 3, 30, team = 2)
    m.tickUntilGone(m.launch(a, ProjectileType.DEATH_BOLT, 1f, 0f))
    assertTrue("a wall-passer is still held by this one", unhurt(b))
  }

  @Test def aBlastDoesNotReachOverIt(): Unit = {
    val a = m.join(CharacterId.Bombardier, MID - 3, 30, team = 1)
    val b = m.join(CharacterId.Soldier, MID + 2, 30, team = 2)
    m.tickUntilGone(m.launch(a, ProjectileType.ROCKET, 1f, 0f))
    assertTrue("the rocket went off on the line, and its blast stayed there", unhurt(b))
    // ...and it really did have the reach: the same shot, once the wall is down
    expireDivider()
    m.tick()
    m.tickUntilGone(m.launch(a, ProjectileType.ROCKET, 1f, 0f))
    assertFalse(unhurt(b))
  }

  @Test def aStepAcrossIsRefused(): Unit = {
    val p = m.join(CharacterId.Soldier, MID - 1, 30, team = 1)
    assertFalse("into the wall", m.move(p, MID, 30, gapMs = 60))
    assertFalse("or over it", m.move(p, MID + 1, 30, gapMs = 60))
    assertEquals((MID - 1, 30), m.at(p))
    assertTrue("along your own side is fine", m.move(p, MID - 1, 31, gapMs = 60))
  }

  @Test def aFenceCannotBeSetDownOverIt(): Unit = {
    val p = m.join(CharacterId.Soldier, MID - 2, 30, team = 1)
    m.useItem(p, ItemType.Fence, MID + 2, 30)
    assertNotEquals("nothing of yours reaches the far half", Tile.Fence, m.world.getTile(MID + 2, 30))
    m.useItem(p, ItemType.Fence, MID - 4, 30)
    assertEquals("one on your own side is fine", Tile.Fence, m.world.getTile(MID - 4, 30))
  }

  @Test def aPhaseDoesNotPassThroughItEither(): Unit = {
    val p = m.join(CharacterId.Wraith, MID - 1, 30, team = 1)
    // Flags 0x08 is the client saying it has phased, which walks through walls
    assertFalse(m.move(p, MID + 1, 30, flags = 0x08, gapMs = 60))
    assertTrue(p.isPhased)
    assertEquals((MID - 1, 30), m.at(p))
  }

  // --- Going up and coming down ---

  @Test def bothTeamsAreToldWhenItGoesUpAndWhenItDrops(): Unit = {
    val p = m.join(CharacterId.Soldier, MID - 5, 30, team = 1)
    m.clearSent()
    m.tick()
    val up = dividerEvents(m.sent(p))
    assertEquals(1, up.size)
    assertTrue(s"how long it has left: ${up.head.getOpeningMs}", up.head.getOpeningMs > 25000)
    assertEquals("a wall down the middle", MatchOpening.DIVIDER, up.head.getOpeningRules)
    m.tick()
    assertTrue("said once, not every tick", dividerEvents(m.sent(p)).isEmpty)

    expireDivider()
    m.tick()
    val down = dividerEvents(m.sent(p))
    assertEquals(1, down.size)
    assertEquals(0, down.head.getOpeningMs)
    assertEquals(MatchOpening.DIVIDER, down.head.getOpeningRules)
    assertNull("and it is gone from the world", m.instance.divider)
  }

  @Test def onceItIsDownTheTeamsCanReachEachOther(): Unit = {
    val a = m.join(CharacterId.Soldier, MID - 3, 30, team = 1)
    val b = m.join(CharacterId.Soldier, MID + 3, 30, team = 2)
    expireDivider()
    m.tick()
    assertTrue(m.move(a, MID, 30, gapMs = 60))
    m.tickUntilGone(m.launch(a, ProjectileType.BULLET, 1f, 0f))
    assertFalse(unhurt(b))
  }
}
