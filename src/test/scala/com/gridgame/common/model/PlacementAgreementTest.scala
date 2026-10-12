package com.gridgame.common.model

import com.gridgame.common.WorldRegistry
import com.gridgame.common.world.WorldLoader
import org.junit.Assert._
import org.junit.Test

/**
 * Where the client puts something and where the server will take it, over a great many throws,
 * blinks and shoves on cluttered ground and on every map. The client picks a trap's cell and a
 * blink's landing with the same rules the server checks them by, and a pick the server refuses is
 * something the player saw happen and then didn't: a trap that never landed, a blink pulled back.
 * The server's copy of where a player is can be a step behind the client's, while that step's update
 * is on its way, so each is checked from there too. (The star's are TeleportTest's.)
 */
class PlacementAgreementTest {
  private val rnd = new scala.util.Random(20261012)

  /** Random ground, a quarter of it walls and fences, a little of it water. */
  private def cluttered(): WorldData = {
    val w = WorldData.createEmpty(40, 40)
    for (x <- 0 until 40; y <- 0 until 40) {
      val r = rnd.nextDouble()
      if (r < 0.15) w.setTile(x, y, Tile.Wall)
      else if (r < 0.25) w.setTile(x, y, Tile.Fence)
      else if (r < 0.28) w.setTile(x, y, Tile.Water)
    }
    w
  }

  private lazy val maps: Seq[(String, WorldData)] =
    (0 until WorldRegistry.size).map { i =>
      val f = WorldRegistry.getFilename(i)
      f -> WorldLoader.load("worlds/" + f)
    }

  private def grounds: Seq[(String, WorldData)] = (0 until 12).map(i => s"cluttered $i" -> cluttered()) ++ maps

  /** A cell someone can stand on, somewhere on the map. */
  private def standing(w: WorldData): (Int, Int) = {
    var (x, y) = (rnd.nextInt(w.width), rnd.nextInt(w.height))
    while (!w.isWalkable(x, y)) { x = rnd.nextInt(w.width); y = rnd.nextInt(w.height) }
    (x, y)
  }

  /** The cells the server may still have someone at who now stands on (x, y): there, or where the
    * step that brought them came from. */
  private def serverCopies(w: WorldData, x: Int, y: Int): Seq[(Int, Int)] =
    (x, y) +: (for (sx <- -1 to 1; sy <- -1 to 1 if (sx != 0 || sy != 0) && w.canStep(x + sx, y + sy, -sx, -sy))
      yield (x + sx, y + sy))

  @Test def everyTrapCellTheClientPicksTheServerTakes(): Unit = {
    // The client walked the aim a whole cell at a time and the server the straight way to the cell
    // in half cells, so a throw past the corner of a wall landed on a cell the server then refused:
    // one throw in twelve over cluttered ground, from the very cell the client threw from. And with
    // the server a step behind, 1-6% of the throws on the real maps.
    for ((name, w) <- grounds) {
      var picked = 0
      for (_ <- 0 until 3000) {
        val (fx, fy) = standing(w)
        val range = 4 + rnd.nextInt(5)
        val (ax, ay) = (fx + rnd.nextGaussian() * 6, fy + rnd.nextGaussian() * 6)
        TrapPlacement.target(w, fx, fy, ax, ay, range).foreach { t =>
          picked += 1
          assertTrue(s"$name: thrown from ($fx,$fy) at ($ax,$ay), onto a cell a trap can't lie on: $t",
            w.isWalkable(t.getX, t.getY))
          for ((sx, sy) <- serverCopies(w, fx, fy))
            assertTrue(s"$name: thrown from ($fx,$fy) at ($ax,$ay) to (${t.getX},${t.getY}), refused by a server " +
              s"that had the thrower at ($sx,$sy)", TrapPlacement.isValidTargetNear(w, sx, sy, t.getX, t.getY, range))
        }
      }
      assertTrue(s"$name: $picked throws", picked > 1000)
    }
  }

  @Test def everyBlinkLandsOnOpenGroundWithinWhatTheServerAllows(): Unit = {
    for ((name, w) <- grounds; _ <- 0 until 3000) {
      val (fx, fy) = standing(w)
      val range = 6 + rnd.nextInt(5)
      val a = rnd.nextDouble() * 2 * Math.PI
      val t = Teleport.blinkTarget(w, fx, fy, Math.cos(a), Math.sin(a), range)
      assertTrue(s"$name: a blink from ($fx,$fy) into a wall: $t", w.isWalkable(t.getX, t.getY))
      for ((sx, sy) <- serverCopies(w, fx, fy))
        assertTrue(s"$name: a blink from ($fx,$fy) to $t, out of reach for a server that had them at ($sx,$sy)",
          Teleport.withinReach(t.getX - sx, t.getY - sy, range))
    }
  }

  @Test def aShoveNeverEndsInAWallNorCarriesAnyoneThroughOne(): Unit = {
    // A push or a pull slides a player a cell at a time and stops before the first it can't stand
    // on, or the first diagonal past a wall's corner: what a step may do, cell by cell
    for ((name, w) <- grounds; _ <- 0 until 3000) {
      val (fx, fy) = standing(w)
      val a = rnd.nextDouble() * 2 * Math.PI
      val cells = 1 + rnd.nextInt(8)
      val (dx, dy) = (Math.cos(a), Math.sin(a))
      val t = Teleport.slide(w, fx, fy, dx, dy, cells)
      assertTrue(s"$name: pushed from ($fx,$fy) into a wall: $t", w.isWalkable(t.getX, t.getY))
      assertTrue(s"$name: pushed from ($fx,$fy) $cells cells to $t",
        Math.max(Math.abs(t.getX - fx), Math.abs(t.getY - fy)) <= cells)
      // The same walk, and every step of it one a player could have taken
      var (x, y) = (fx, fy)
      var step = 1
      while ((x, y) != (t.getX, t.getY) && step <= cells) {
        val nx = Math.max(0, Math.min(w.width - 1, Math.round(fx + dx * step).toInt))
        val ny = Math.max(0, Math.min(w.height - 1, Math.round(fy + dy * step).toInt))
        assertTrue(s"$name: pushed from ($fx,$fy) to $t through ($nx,$ny) from ($x,$y)",
          (nx, ny) == (x, y) || w.canStep(x, y, nx - x, ny - y))
        x = nx; y = ny
        step += 1
      }
    }
  }
}
