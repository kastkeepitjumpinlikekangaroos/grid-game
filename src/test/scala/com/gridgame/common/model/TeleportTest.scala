package com.gridgame.common.model

import com.gridgame.common.Constants
import org.junit.Assert._
import org.junit.Test

/**
 * The client picks where a star or a blink lands and the server checks it; a cell the client
 * picks that the server refuses is a teleport the player sees and then gets pulled back from.
 * The client used to aim a star anywhere on screen (the server's limit is 30 cells, the bottom
 * of a 1080p screen is 34), and diagonal blinks failed a Manhattan-distance check.
 */
class TeleportTest {

  private def open(): WorldData = WorldData.createEmpty(60, 60)

  private def star(world: WorldData, fx: Int, fy: Int, ax: Double, ay: Double): Option[(Int, Int)] =
    Teleport.starTarget(world, fx, fy, ax, ay).map(p => (p.getX, p.getY))

  private val oneStep = for (sx <- -1 to 1; sy <- -1 to 1) yield (sx, sy)

  @Test
  def starLandsOnTheAimedCell(): Unit = {
    assertEquals(Some((20, 15)), star(open(), 10, 10, 20.2, 14.7))
  }

  @Test
  def starAimedPastItsRangeGoesFullRangeAlongTheLine(): Unit = {
    assertEquals(Some((10, 40)), star(open(), 10, 10, 10, 55))
    assertEquals(Some((25, 25)), star(open(), 10, 10, 50, 50))
  }

  @Test
  def starAimedOffTheMapStaysOnIt(): Unit = {
    assertEquals(Some((0, 30)), star(open(), 5, 30, -20, 30))
  }

  @Test
  def starAimedIntoAWallLandsInFrontOfIt(): Unit = {
    val world = open()
    for (x <- 20 to 22) world.setTile(x, 10, Tile.Wall)
    assertEquals(Some((19, 10)), star(world, 10, 10, 21, 10))
    // ...and aimed past the wall it goes over, as a teleport does
    assertEquals(Some((25, 10)), star(world, 10, 10, 25, 10))
  }

  @Test
  def starWithNowhereToGoIsNone(): Unit = {
    val world = open()
    assertEquals(None, star(world, 10, 10, 10.2, 9.8))
    for (x <- 11 to 13) world.setTile(x, 10, Tile.Wall)
    assertEquals(None, star(world, 10, 10, 13, 10))
  }

  @Test
  def everyStarTheClientPicksPassesTheServerCheck(): Unit = {
    val rnd = new scala.util.Random(7)
    val world = WorldData.createEmpty(40, 40)
    for (x <- 0 until 40; y <- 0 until 40 if rnd.nextDouble() < 0.3) world.setTile(x, y, Tile.Wall)
    var picked = 0
    for (_ <- 0 until 20000) {
      val fx = rnd.nextInt(40)
      val fy = rnd.nextInt(40)
      if (world.isWalkable(fx, fy)) {
        val ax = rnd.nextDouble() * 100 - 30 // off the map on every side, and far past range
        val ay = rnd.nextDouble() * 100 - 30
        Teleport.starTarget(world, fx, fy, ax, ay).foreach { t =>
          picked += 1
          assertTrue(Math.abs(t.getX - fx) + Math.abs(t.getY - fy) <= Constants.STAR_MAX_DISTANCE)
          // Where the client is, or a step behind it where the server may still have it
          for ((sx, sy) <- oneStep) {
            assertTrue(s"($fx,$fy) aiming ($ax,$ay) -> $t, server off by ($sx,$sy)",
              Teleport.isValidStarTarget(world, fx + sx, fy + sy, t.getX, t.getY))
          }
        }
      }
    }
    assertTrue(picked > 5000)
  }

  @Test
  def everyBlinkIsWithinReach(): Unit = {
    val world = open()
    for (range <- Seq(6, 7, 8, 10); i <- 0 until 3600) {
      val a = 2 * Math.PI * i / 3600
      val t = Teleport.blinkTarget(world, 30, 30, Math.cos(a), Math.sin(a), range)
      for ((sx, sy) <- oneStep) {
        assertTrue(s"range $range at $i/3600 -> $t, server off by ($sx,$sy)",
          Teleport.withinReach(t.getX - (30 + sx), t.getY - (30 + sy), range))
      }
    }
  }

  @Test
  def aDiagonalBlinkIsInReachButWasOverTheManhattanGate(): Unit = {
    val t = Teleport.blinkTarget(open(), 30, 30, Math.sqrt(0.5), Math.sqrt(0.5), 10)
    assertEquals((37, 37), (t.getX, t.getY))
    assertTrue(Teleport.withinReach(7, 7, 10))
    assertTrue("the old check refused it", 7 + 7 > 10 + 2)
  }

  @Test
  def blinkStopsBeforeAWall(): Unit = {
    val world = open()
    world.setTile(14, 10, Tile.Wall)
    val t = Teleport.blinkTarget(world, 10, 10, 1, 0, 8)
    assertEquals((13, 10), (t.getX, t.getY))
  }

  @Test
  def reachIsARadius(): Unit = {
    val r = 8 + Constants.TELEPORT_RANGE_TOLERANCE
    assertTrue(Teleport.withinReach(r, 0, 8))
    assertFalse(Teleport.withinReach(r + 1, 0, 8))
    assertFalse(Teleport.withinReach(r, 2, 8))
  }
}
