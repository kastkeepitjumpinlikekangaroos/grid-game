package com.gridgame.client.render

import com.gridgame.client.TestClient
import com.gridgame.common.Constants
import com.gridgame.common.model._
import org.junit.Assert._
import org.junit.Test

import java.util.UUID
import scala.collection.mutable

/**
 * The depth pass's order: a diagonal of the map at a time (v = wx + wy), its blocks and then
 * everything standing on it, back to front, each sorted where it is drawn (EntityCollector).
 *
 * It was walked a row at a time, with each entity on the cell it was rounded to, and us on the
 * cell we stand on. We are drawn a step's glide and the camera's ease behind that cell — a cell
 * and more as we walk, several in a knockback's slide — so a wall in front of where we were drawn
 * but in an earlier row was drawn under us, and we stood on top of it; one behind us in a later
 * row was drawn over us. And with someone half way between two rows and a wall on either side,
 * no cell in row order was right: the wall at (x+1, y) needs drawing after someone stepping from
 * (x, y) to (x, y+1), and the one at (x-1, y+1) before them, and rows put (x+1, y) first.
 */
class DepthOrderTest {

  @Test def weAreSortedWhereWeAreDrawnNotOnTheCellWeStandOn(): Unit = {
    val t = new TestClient()
    t.startMatch(spawn = (10, 10))
    val c = t.client
    c.updateVisualPosition()
    // Pushed three cells: we stand on (10, 13) at once, and are slid there from (10, 10), on the
    // test's clock, which stands still
    t.update(t.id, 10, 13, serverMoves = 1, knockbacks = 1)
    c.updateVisualPosition()
    assertEquals((10, 13), t.at)
    assertEquals(10.0, c.visualPosY, 1e-9)

    val ec = new EntityCollector()
    ec.collect(c, 1.0 / 60, c.visualPosX, c.visualPosY, false, 0, 59, 0, 59)
    val drawnOn = ec.takeDiagonal(20)
    assertNotNull("on the diagonal we are drawn on", drawnOn)
    assertEquals(EntityCollector.TYPE_LOCAL_PLAYER, drawnOn.entryType)
    assertNull("not the one we stand on", ec.takeDiagonal(23))
  }

  @Test def onOneDiagonalTheyComeBackToFront(): Unit = {
    val t = new TestClient()
    t.startMatch(spawn = (10, 10))
    val c = t.client
    val owner = UUID.randomUUID()
    // A shot further down the screen than us and one further up, on our diagonal (20), filed
    // before us; and an item on our cell, at our depth
    c.getProjectiles.put(1, new Projectile(1, owner, 10.8f, 10f, 1f, 0f, 0, 0, ProjectileType.ARROW))
    c.getProjectiles.put(2, new Projectile(2, owner, 10.3f, 10f, 1f, 0f, 0, 0, ProjectileType.ARROW))
    c.getItems.put(1, new Item(1, 10, 10, ItemType.Gem))
    val ec = new EntityCollector()
    ec.collect(c, 1.0 / 60, 10.0, 10.6, false, 0, 59, 0, 59)

    val order = mutable.ArrayBuffer[Double]()
    var e = ec.takeDiagonal(20)
    val types = mutable.ArrayBuffer[Byte]()
    while (e != null) { order += e.depth; types += e.entryType; e = e.next }
    assertEquals(Seq(20.0, 20.3, 20.6, 20.8), order.map(d => Math.round(d * 10) / 10.0).toSeq)
    assertEquals(EntityCollector.TYPE_LOCAL_PLAYER, types(2))

    // Level with one already there, it goes after it: an item on the cell we stand on goes under us
    ec.collect(c, 1.0 / 60, 10.0, 10.0, false, 0, 59, 0, 59)
    val first = ec.takeDiagonal(20)
    assertEquals(EntityCollector.TYPE_ITEM, first.entryType)
    assertEquals(EntityCollector.TYPE_LOCAL_PLAYER, first.next.entryType)
  }

  /**
   * Does the wall on cell (bx, by) belong in front of a sprite `w` (in u, cells across) either side
   * of feet at (x, y)? In each column u the sprite covers, the wall's front edge is at
   * v = vb + 1 - |u - ub|; if it is nearer the screen's bottom than the feet in any of them, the
   * wall hides some of the sprite there. None when the sprite doesn't reach the wall's columns, or
   * the nearest of them is level with the feet.
   */
  private def inFront(bx: Int, by: Int, x: Double, y: Double, w: Double): Option[Boolean] = {
    val ub = bx - by; val vb = bx + by
    val u = x - y; val v = x + y
    // The sprite's column nearest the wall's middle, where its front edge is nearest
    val off = Math.abs(Math.max(u - w, Math.min(u + w, ub.toDouble)) - ub)
    if (off >= 1.0) None
    else {
      val front = vb + 1 - off
      if (Math.abs(front - v) < 1e-9) None else Some(front > v)
    }
  }

  @Test def everyWallASpriteOverlapsIsOnTheRightSideOfItAllAlongAStep(): Unit = {
    // Every step a player takes, half-widths up to the widest character's (19.5 of a tile's 20),
    // drawn at 32 points along it. A diagonal step past a wall's corner is left out: the sprite
    // really is in the wall then, on every side of the corner, and no order of the two is right.
    val steps = Seq((1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (-1, -1), (1, -1), (-1, 1))
    var checked = 0
    for (w <- Seq(0.5, 0.675, 0.92, 0.975); (dx, dy) <- steps; i <- 0 until 32) {
      val f = i / 32.0
      val x = 10 + dx * f; val y = 10 + dy * f
      for (bx <- 5 to 15; by <- 5 to 15) {
        val onStep = (bx == 10 && by == 10) || (bx == 10 + dx && by == 10 + dy)
        val corner = dx != 0 && dy != 0 && ((bx == 10 + dx && by == 10) || (bx == 10 && by == 10 + dy))
        if (!onStep && !corner) inFront(bx, by, x, y, w).foreach { front =>
          val drawnAfter = bx + by > EntityCollector.diagonalOf(x, y)
          assertEquals(s"half-width $w stepping ($dx, $dy) at $f: the wall on ($bx, $by) drawn after",
            front, drawnAfter)
          checked += 1
        }
      }
    }
    assertTrue(s"checked $checked", checked > 5000)
  }

  @Test def onACellItIsOnThatCellsDiagonal(): Unit = {
    // However its position was summed: 0.1 + 0.2 is not 0.3
    assertEquals(20, EntityCollector.diagonalOf(9.7 + 0.1 + 0.2, 10.0))
    assertEquals(20, EntityCollector.diagonalOf(10.0, 10.0))
    assertEquals(19, EntityCollector.diagonalOf(10.0, 9.999))
    assertEquals(20, EntityCollector.diagonalOf(10.0, 10.999))
  }

  @Test def theDiagonalWalkVisitsEveryCellOnScreen(): Unit = {
    // Brute force: every cell whose block reaches the screen, against the cells the diagonal walk
    // visits, over the map and off its edges, on screens of every shape
    val (mapW, mapH) = (120, 90)
    val ctx = new RenderContext(new TestClient(WorldData.createEmpty(mapW, mapH)).client, FrameClock.System)
    val HW = Constants.ISO_HALF_W; val HH = Constants.ISO_HALF_H; val cellH = Constants.TILE_CELL_HEIGHT
    val rnd = new java.util.Random(7)
    var visible = 0L; var walked = 0L
    for (_ <- 0 until 400) {
      val canvasW = 300.0 + rnd.nextInt(1500); val canvasH = 200.0 + rnd.nextInt(900)
      val (cx, cy) = (rnd.nextDouble() * (mapW + 20) - 10, rnd.nextDouble() * (mapH + 20) - 10)
      val camOffX = canvasW / 2 - (cx - cy) * HW
      val camOffY = canvasH / 2 - (cx + cy) * HH
      // The view as GLGameRenderer.render sets it
      val uMin = (-camOffX - HW) / HW; val uMax = (canvasW - camOffX + HW) / HW
      val vMin = (-camOffY - HH) / HH; val vMax = (canvasH - camOffY + (cellH - HH)) / HH
      val pad = 10
      val startY = Math.max(0, ((vMin - uMax) * 0.5).floor.toInt - pad)
      val endY = Math.min(mapH - 1, ((vMax - uMin) * 0.5).ceil.toInt + pad)
      val startX = Math.max(0, ((uMin + vMin) * 0.5).floor.toInt - pad)
      val endX = Math.min(mapW - 1, ((uMax + vMax) * 0.5).ceil.toInt + pad)
      ctx.setView(startX, endX, startY, endY, uMin, uMax, vMin, vMax)

      val seen = new Array[Boolean](mapW * mapH)
      var v = startX + startY
      while (v <= endX + endY) {
        var x = ctx.diagLo(v)
        while (x <= ctx.diagHi(v)) {
          val y = v - x
          assertTrue(s"($x, $y) is on the map", x >= 0 && x < mapW && y >= 0 && y < mapH)
          assertFalse(s"($x, $y) walked once", seen(y * mapW + x))
          seen(y * mapW + x) = true
          walked += 1
          x += 1
        }
        v += 1
      }
      for (y <- 0 until mapH; x <- 0 until mapW) {
        // The block's sprite: 40 across, from 46 above its diamond's centre to 10 below
        val sx = (x - y) * HW + camOffX; val sy = (x + y) * HH + camOffY
        val onScreen = sx + HW > 0 && sx - HW < canvasW && sy + HH > 0 && sy - (cellH - HH) < canvasH
        if (onScreen) {
          visible += 1
          assertTrue(s"($x, $y) is on screen, so walked (camera at $cx, $cy, $canvasW x $canvasH)", seen(y * mapW + x))
        }
      }
    }
    // and no more than that, but for one whose block only touches the edge
    assertTrue(s"walked $walked for $visible on screen", walked <= visible * 1.01)
  }
}
