package com.gridgame.client.render

import com.gridgame.client.render.world.AimArrowPainter
import com.gridgame.common.model._
import org.junit.Assert._
import org.junit.Test

/**
 * Where the lane a charged shot is aimed along stops (AimArrowPainter.stopAlong): where the shot
 * would. It used to run the shot's whole range whatever was in the way, under a wall's top and out
 * of its far side, promising a hit on whoever stood behind it.
 */
class AimLaneTest {
  private def world(tiles: ((Int, Int), Tile)*): WorldData = {
    val w = WorldData.createEmpty(20, 20)
    tiles.foreach { case ((x, y), t) => w.setTile(x, y, t) }
    w
  }

  private def stop(w: WorldData, from: (Double, Double), dir: (Double, Double), limit: Float = 15f,
                   passesWalls: Boolean = false): Float = {
    val len = Math.sqrt(dir._1 * dir._1 + dir._2 * dir._2)
    AimArrowPainter.stopAlong(w, from._1, from._2, dir._1 / len, dir._2 / len, limit, passesWalls)
  }

  @Test def itStopsAtTheFaceOfAWall(): Unit = {
    // Tile (c, r) covers [c - 0.5, c + 0.5): a wall in column 8 faces x = 7.5, 5.5 cells from x = 2
    val w = world((8, 5) -> Tile.Wall)
    assertEquals(5.5f, stop(w, (2, 5), (1, 0)), 0.02f)
    // and a tree, a rock, a snowman are as solid as a wall
    assertEquals(5.5f, stop(world((8, 5) -> Tile.Tree), (2, 5), (1, 0)), 0.02f)
    assertEquals(5.5f, stop(world((8, 5) -> Tile.Snowman), (2, 5), (1, 0)), 0.02f)
  }

  @Test def itRunsItsRangeWhenNothingIsInTheWay(): Unit = {
    assertEquals(9f, stop(world(), (2, 5), (1, 0), limit = 9f), 0f)
    // A wall past the end of its range doesn't shorten it
    assertEquals(4f, stop(world((8, 5) -> Tile.Wall), (2, 5), (1, 0), limit = 4f), 0f)
  }

  @Test def itCrossesWaterButNotLava(): Unit = {
    // A shot flies over water as over the ground (Tile.stopsProjectiles); lava, a pool too, stops it
    val w = world((5, 5) -> Tile.Water, (6, 5) -> Tile.DeepWater, (9, 5) -> Tile.Lava)
    assertEquals(6.5f, stop(w, (2, 5), (1, 0)), 0.02f)
  }

  @Test def itStopsAtTheEdgeOfTheMap(): Unit = {
    // The last column is 19, so the edge is x = 19.5
    assertEquals(4.5f, stop(world(), (15, 5), (1, 0)), 0.02f)
    assertEquals(5.5f, stop(world(), (5, 5), (0, -1)), 0.02f)
  }

  @Test def aShotThatFliesOverWallsIsStoppedOnlyByAFenceAndTheEdge(): Unit = {
    val w = world((5, 5) -> Tile.Wall, (6, 5) -> Tile.Tree, (10, 5) -> Tile.Fence)
    assertEquals(7.5f, stop(w, (2, 5), (1, 0), passesWalls = true), 0.02f)
    assertEquals(4.5f, stop(world((5, 5) -> Tile.Wall), (15, 5), (1, 0), passesWalls = true), 0.02f)
  }

  @Test def aDiagonalStopsAtTheFirstCellItEnters(): Unit = {
    // Heading +x+y from (2, 2), the aim enters (6, 6) where x = y = 5.5: 3.5 * sqrt(2) out
    val w = world((6, 6) -> Tile.Wall)
    assertEquals((3.5 * Math.sqrt(2)).toFloat, stop(w, (2, 2), (1, 1)), 0.03f)
  }

  @Test def itStopsFromWhereverWeAreDrawn(): Unit = {
    // Walking, we are drawn between two cells, and the lane runs from there
    val w = world((8, 5) -> Tile.Wall)
    assertEquals(4.9f, stop(w, (2.6, 5), (1, 0)), 0.02f)
  }
}
