package com.gridgame.client.ui

import com.gridgame.client.Fx
import com.gridgame.common.Constants
import com.gridgame.common.model.{Position, Tile, WorldData}
import org.junit.Assert._
import org.junit.Test

/** The maps drawn for the menus from the game's own tile art (MapPreview, WorldBackdrop). */
class MapArtTest {
  private val HW = Constants.ISO_HALF_W
  private val HH = Constants.ISO_HALF_H

  /** A map of `tile` everywhere but where `at` says otherwise. */
  private def world(n: Int, spawns: Seq[Position] = Nil)(at: (Int, Int) => Tile = (_, _) => Tile.Grass): WorldData =
    new WorldData("test", n, n, Array.tabulate[Tile](n, n)((y, x) => at(x, y)), spawns, "sky")

  private def alpha(px: Array[Int], w: Int, x: Double, y: Double): Int = px(y.toInt * w + x.toInt) >>> 24

  @Test def thePlayAreaIsWhatTheSpawnsCanWalkTo(): Unit = {
    // A round arena in walls, and a pocket of grass in a corner nobody can reach
    val w = world(40, Seq(new Position(20, 20))) { (x, y) =>
      val inArena = (x - 20) * (x - 20) + (y - 20) * (y - 20) <= 100
      val inPocket = x >= 2 && x <= 4 && y >= 2 && y <= 4
      if (inArena || inPocket) Tile.Grass else Tile.Wall
    }
    // The arena runs 10 to 30 each way; a tenth of its 20 cells is 2 more on each side
    assertEquals(MapArt.Window(8, 8, 32, 32), MapArt.playArea(w, 0.1))
  }

  @Test def withNowhereToSpawnThePlayAreaIsTheWholeMap(): Unit =
    assertEquals(MapArt.Window(0, 0, 11, 11), MapArt.playArea(world(12)(), 0.1))

  @Test def theWholeMapIsDrawnAndNothingOffIt(): Unit = Fx {
    val w = world(12)()
    val win = MapArt.whole(w)
    val (iw, ih) = (240, 150)
    val view = MapArt.fit(win, iw, ih, 0.05)
    val px = MapArt.draw(w, win, iw, ih, view, 2)
    // Every cell's middle is covered
    for (y <- 0 until 12; x <- 0 until 12) {
      val sx = view.ox + (x - y) * HW * view.scale
      val sy = view.oy + (x + y) * HH * view.scale
      assertEquals(s"cell ($x, $y)", 255, alpha(px, iw, sx, sy))
    }
    // and nothing outside the diamond: the picture's corners and its margins are clear
    for ((x, y) <- Seq((0, 0), (iw - 1, 0), (0, ih - 1), (iw - 1, ih - 1))) assertEquals(0, alpha(px, iw, x, y))
    for (x <- 0 until iw) { assertEquals(0, alpha(px, iw, x, 0)); assertEquals(0, alpha(px, iw, x, ih - 1)) }
  }

  @Test def theBaseIsUnderTheNearEdgesAndNowhereElse(): Unit = Fx {
    val w = world(12)()
    val win = MapArt.whole(w)
    val (iw, ih) = (240, 170)
    val base = 30.0
    val view = MapArt.fit(win, iw, ih, 0.05, base, rise = 0)
    val px = MapArt.draw(w, win, iw, ih, view, 2, base)
    def at(ux: Double, uy: Double) = alpha(px, iw, view.ox + ux * view.scale, view.oy + uy * view.scale)
    // The near corner of the map (cell (11, 11)'s bottom) and just under it
    val nearX = 0.0; val nearY = (11 + 11 + 1) * HH.toDouble
    assertEquals("below the near corner", 255, at(nearX, nearY + base / 2))
    assertEquals("below the base", 0, at(nearX, nearY + base + 6))
    // Nothing above the far corner: a base is only seen from the front
    assertEquals("above the far corner", 0, at(0, -HH - 4.0))
  }

  @Test def aWindowDrawsOnlyItsOwnCells(): Unit = Fx {
    // Walls down the left of the map, and a window that leaves them out
    val w = world(12)((x, _) => if (x < 2) Tile.Wall else Tile.Grass)
    val win = MapArt.Window(2, 0, 11, 11)
    val (iw, ih) = (240, 150)
    val view = MapArt.fit(win, iw, ih, 0.05)
    val px = MapArt.draw(w, win, iw, ih, view, 2)
    // Where cell (0, 6) would be is clear, and cell (2, 6) is drawn
    def cell(x: Int, y: Int) = alpha(px, iw, view.ox + (x - y) * HW * view.scale, view.oy + (x + y) * HH * view.scale)
    assertEquals(0, cell(0, 6))
    assertEquals(255, cell(2, 6))
  }
}
