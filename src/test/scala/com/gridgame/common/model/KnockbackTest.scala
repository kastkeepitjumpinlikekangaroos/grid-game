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

  @Test def aPushStopsBeforeCuttingAWallsCorner(): Unit = {
    // Drawn as a slide over the ground, a push past a wall's corner slid its player through the
    // wall, so it stops as a step does
    val d = Math.sqrt(0.5) // three cells along the diagonal is two cells over and two down
    assertEquals("in the open", (12, 12), at(Teleport.slide(world(), 10, 10, d, d, 3)))
    val w = world()
    w.setTile(12, 11, Tile.Wall)
    assertEquals("short of the corner", (11, 11), at(Teleport.slide(w, 10, 10, d, d, 3)))
    w.setTile(11, 12, Tile.Wall)
    assertEquals("nor between two blocks", (11, 11), at(Teleport.slide(w, 10, 10, d, d, 3)))
    // A blink is a jump, and still passes one
    val b = world()
    b.setTile(12, 11, Tile.Wall)
    val blink = Teleport.blinkTarget(b, 10, 10, d, d, 3)
    assertEquals((12, 12), (blink.getX, blink.getY))
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
