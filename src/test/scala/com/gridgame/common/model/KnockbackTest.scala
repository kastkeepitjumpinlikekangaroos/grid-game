package com.gridgame.common.model

import org.junit.Assert._
import org.junit.Test

/**
 * Teleport.slide: where a knockback or a vortex pull leaves a player. It used to take the
 * farthest open cell along the line whatever lay between, so a push carried players through
 * walls.
 */
class KnockbackTest {
  private def world(): WorldData = WorldData.createEmpty(30, 30)

  private def at(p: Position): (Int, Int) = (p.getX, p.getY)

  @Test def aPushInTheOpenGoesItsFullDistance(): Unit = {
    assertEquals((13, 10), at(Teleport.slide(world(), 10, 10, 1.0, 0.0, 3)))
    assertEquals((10, 7), at(Teleport.slide(world(), 10, 10, 0.0, -1.0, 3)))
  }

  @Test def aPushStopsAgainstAWall(): Unit = {
    val w = world()
    w.setTile(12, 10, Tile.Wall)
    assertEquals((11, 10), at(Teleport.slide(w, 10, 10, 1.0, 0.0, 3)))
  }

  @Test def aPushNeverCarriesAPlayerThroughAWall(): Unit = {
    // A one-cell wall with open ground behind it: the old walk came out on the far side
    val w = world()
    w.setTile(11, 10, Tile.Wall)
    assertEquals((10, 10), at(Teleport.slide(w, 10, 10, 1.0, 0.0, 3)))
    w.setTile(11, 10, Tile.Fence)
    assertEquals("a fence too", (10, 10), at(Teleport.slide(w, 10, 10, 1.0, 0.0, 3)))
  }

  @Test def aPushStopsAtTheEdgeOfTheMap(): Unit = {
    assertEquals((29, 5), at(Teleport.slide(world(), 28, 5, 1.0, 0.0, 3)))
    assertEquals((0, 5), at(Teleport.slide(world(), 1, 5, -1.0, 0.0, 3)))
  }

  @Test def aDiagonalPushFollowsItsLine(): Unit = {
    val d = Math.sqrt(0.5)
    assertEquals((12, 12), at(Teleport.slide(world(), 10, 10, d, d, 3)))
  }

  @Test def aPushOfNoCellsLeavesThePlayerWhereTheyAre(): Unit = {
    assertEquals((10, 10), at(Teleport.slide(world(), 10, 10, 1.0, 0.0, 0)))
  }
}
