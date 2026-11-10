package com.gridgame.server

import com.gridgame.common.model._
import org.junit.Assert._
import org.junit.Test

import java.util.UUID

/**
 * A shot that bounces: how many times, and which way each bounce turns it.
 *
 * A bounce is a quarter turn away from the wall, not a mirror's reflection (at 45 degrees they are
 * the same thing): off a wall to its east or west a shot keeps going up or down the way it was, off
 * one to its north or south left or right the way it was. Square on, it has neither, and turns north
 * off a wall to its east or west and west off one to its north or south; into a corner with both
 * ways shut, or onto the outside of one from the diagonal, it goes back the way it came. It bounces
 * its number of times and stops at the next wall, and never off a fence.
 *
 * SymmetryTest turns scenes with bouncing shots eight ways; KitReworkTest lands one on someone round
 * a corner. Nothing counted the bounces: a shot that bounced for ever failed no test.
 */
class RicochetTest {
  private val bouncing: Seq[RosterAttack] = RosterAttack.all.filter(_.defn.ricochetCount > 0)

  private def sign(v: Float): Int = Math.signum(v).toInt

  // --- In a match ---

  /** A corridor a cell wide along y = 30, walled above and below. */
  private def corridor(): WorldData = {
    val w = WorldData.createEmpty(80, 60)
    for (x <- 5 to 75) { w.setTile(x, 29, Tile.Wall); w.setTile(x, 31, Tile.Wall) }
    w
  }

  /** Fire the attack down the corridor at 45 degrees and fly it out: how many times it turned back
    * across the corridor, and how far it had flown when it was gone. */
  private def flownDownTheCorridor(a: RosterAttack, world: WorldData): (Int, Float) = Sim() { clock =>
    val m = new SimMatch(clock, world, respawns = false)
    val shooter = m.join(a.character.id, 20, 30)
    val h = (Math.sqrt(0.5)).toFloat
    val shot = m.launch(shooter, a.projectileType, h, h)
    var turns = 0
    var heading = sign(shot.dy)
    var flown = 0f
    var ticks = 0
    while (m.instance.projectileManager.getProjectile(shot.id) != null && ticks < 400) {
      m.tick()
      ticks += 1
      if (m.instance.projectileManager.getProjectile(shot.id) != null) {
        assertEquals(s"${a.name}: still going along the corridor", 1, sign(shot.dx))
        assertTrue(s"${a.name}: and inside it, at (${shot.getX}, ${shot.getY})", shot.getY > 29.5f && shot.getY < 30.5f)
        if (sign(shot.dy) != heading) { turns += 1; heading = sign(shot.dy) }
        flown = shot.getDistanceTraveled
      }
    }
    (turns, flown)
  }

  @Test def aShotBouncesItsNumberOfTimesAndStopsAtTheNextWall(): Unit = {
    assertTrue("the roster's bouncing shots", bouncing.size >= 8)
    assertTrue("with one bounce and with several among them", Set(1, 2, 3).subsetOf(bouncing.map(_.defn.ricochetCount).toSet))
    for (a <- bouncing) {
      val (turns, flown) = flownDownTheCorridor(a, corridor())
      assertEquals(s"${a.name}: bounces", a.defn.ricochetCount, turns)
      assertTrue(s"${a.name}: stopped by the wall after them, ${flown} cells into its ${a.defn.maxRange}", flown < a.defn.maxRange - 2)
    }
  }

  @Test def nothingBothFliesThroughWallsAndBouncesOffThem(): Unit = {
    // Only a wall turns a shot (not a fence, not the map's edge), and a shot that flies through
    // walls meets none: bounces written on one would be bounces it never makes. (The Shade's Shadow
    // Bolt had two, which only the Shadow Bind made from it ever used.)
    for (a <- RosterAttack.all)
      assertFalse(s"${a.name} flies through walls and is given ${a.defn.ricochetCount} bounces off them",
        a.defn.passesThroughWalls && a.defn.ricochetCount > 0)
  }

  @Test def aShotBouncesOffNoFence(): Unit = {
    // A fence stops what flies over walls, and what bounces off them
    for (a <- bouncing) {
      val world = corridor()
      for (x <- 5 to 75) world.setTile(x, 31, Tile.Fence)
      val (turns, _) = flownDownTheCorridor(a, world)
      assertEquals(s"${a.name}: off the fence it met first", 0, turns)
    }
  }

  // --- The bounce itself ---

  private val Rico = bouncing.maxBy(_.defn.ricochetCount).projectileType

  /** A shot of the roster's that is `inCell`, having flown there along (dx, dy), bounced once off
    * the walls of `world`: its heading after, and where it was put. */
  private def bounced(world: WorldData, at: (Float, Float), dx: Float, dy: Float): ((Float, Float), (Float, Float)) = {
    val p = new Projectile(1, UUID.randomUUID(), 0f, 0f, 1f, 0f, 0, 0, Rico)
    p.updatePosition(at._1, at._2, dx, dy)
    val had = p.remainingBounces
    assertTrue(p.ricochet(world))
    assertEquals("a bounce used up", had - 1, p.remainingBounces)
    ((p.dx, p.dy), (p.getX, p.getY))
  }

  private def assertHeading(what: String, expected: (Float, Float), got: (Float, Float)): Unit = {
    assertEquals(s"$what: dx", expected._1, got._1, 1e-6f)
    assertEquals(s"$what: dy", expected._2, got._2, 1e-6f)
  }

  /** Open ground with a wall down column 10 and one along row 20 (they cross at (10, 20)). */
  private def walls(): WorldData = {
    val w = WorldData.createEmpty(40, 40)
    for (i <- 0 until 40) { w.setTile(10, i, Tile.Wall); w.setTile(i, 20, Tile.Wall) }
    w
  }

  @Test def squareOnAShotTurnsNorthOffAWallBesideItAndWestOffOneAboveOrBelow(): Unit = {
    val w = walls()
    val (east, eastAt) = bounced(w, (9.6f, 5f), 1f, 0f)
    assertHeading("flying east into the wall", (0f, -1f), east)
    assertEquals("put back on the wall's open side", (9.49f, 5f), eastAt)
    val (west, westAt) = bounced(w, (10.4f, 5f), -1f, 0f)
    assertHeading("flying west into it", (0f, -1f), west)
    assertEquals((10.51f, 5f), westAt)
    val (south, southAt) = bounced(w, (5f, 19.6f), 0f, 1f)
    assertHeading("flying south into the wall", (-1f, 0f), south)
    assertEquals((5f, 19.49f), southAt)
    val (north, northAt) = bounced(w, (5f, 20.4f), 0f, -1f)
    assertHeading("flying north into it", (-1f, 0f), north)
    assertEquals((5f, 20.51f), northAt)
  }

  @Test def squareOnAPillarIsAWallLikeAnyOther(): Unit = {
    // One cell of wall with open ground all round it: the cells either side of the one struck are
    // not the way the shot came, and it turns as off any wall rather than back on itself
    val w = WorldData.createEmpty(40, 40)
    w.setTile(10, 10, Tile.Wall)
    assertHeading("east into a pillar", (0f, -1f), bounced(w, (9.6f, 10f), 1f, 0f)._1)
    assertHeading("west into it", (0f, -1f), bounced(w, (10.4f, 10f), -1f, 0f)._1)
    assertHeading("south into it", (-1f, 0f), bounced(w, (10f, 9.6f), 0f, 1f)._1)
    assertHeading("north into it", (-1f, 0f), bounced(w, (10f, 10.4f), 0f, -1f)._1)
  }

  @Test def onTheDiagonalAShotKeepsGoingTheWayItWasAlongTheWall(): Unit = {
    val w = walls()
    val h = Math.sqrt(0.5).toFloat
    // Off the wall down column 10 (struck from its west, then from its east): left and right change
    for (((dx, dy), x) <- Seq((h, h) -> 9.6f, (h, -h) -> 9.6f, (-h, h) -> 10.4f, (-h, -h) -> 10.4f)) {
      val (after, at) = bounced(w, (x, 5f), dx, dy)
      assertHeading(s"($dx, $dy) off the wall beside it", (-dx, dy), after)
      assertEquals(s"($dx, $dy): on its open side", if (dx > 0) 9.49f else 10.51f, at._1, 0f)
    }
    // Off the wall along row 20 (from its north, then from its south): up and down change
    for (((dx, dy), y) <- Seq((h, h) -> 19.6f, (-h, h) -> 19.6f, (h, -h) -> 20.4f, (-h, -h) -> 20.4f)) {
      val (after, at) = bounced(w, (5f, y), dx, dy)
      assertHeading(s"($dx, $dy) off the wall above or below it", (dx, -dy), after)
      assertEquals(s"($dx, $dy): on its open side", if (dy > 0) 19.49f else 20.51f, at._2, 0f)
    }
  }

  @Test def offTheDiagonalABounceIsAQuarterTurnAndNotAMirrors(): Unit = {
    // As steep as it came in shallow: (0.8, 0.6) off a wall to its east leaves at (-0.6, 0.8), where
    // a mirror would send it off at (-0.8, 0.6)
    val w = walls()
    assertHeading("(0.8, 0.6) off the wall to its east", (-0.6f, 0.8f), bounced(w, (9.6f, 5f), 0.8f, 0.6f)._1)
    assertHeading("(0.8, -0.6) off it", (-0.6f, -0.8f), bounced(w, (9.6f, 5f), 0.8f, -0.6f)._1)
    assertHeading("(-0.8, 0.6) off the wall to its west", (0.6f, 0.8f), bounced(w, (10.4f, 5f), -0.8f, 0.6f)._1)
    assertHeading("(0.6, 0.8) off the wall to its south", (0.8f, -0.6f), bounced(w, (5f, 19.6f), 0.6f, 0.8f)._1)
    assertHeading("(-0.6, 0.8) off it", (-0.8f, -0.6f), bounced(w, (5f, 19.6f), -0.6f, 0.8f)._1)
    assertHeading("(0.6, -0.8) off the wall to its north", (0.8f, 0.6f), bounced(w, (5f, 20.4f), 0.6f, -0.8f)._1)
  }

  @Test def intoACornerOrOntoOneAShotGoesBackTheWayItCame(): Unit = {
    val h = Math.sqrt(0.5).toFloat
    // The outside of a corner, met on the diagonal: open ground on both of the ways it came by
    val pillar = WorldData.createEmpty(40, 40)
    pillar.setTile(10, 10, Tile.Wall)
    for ((dx, dy) <- Seq((h, h), (h, -h), (-h, h), (-h, -h))) {
      val (after, at) = bounced(pillar, (10f - dx * 0.2f, 10f - dy * 0.2f), dx, dy)
      assertHeading(s"($dx, $dy) onto a pillar's corner", (-dx, -dy), after)
      assertEquals("where it struck", (10f - dx * 0.2f, 10f - dy * 0.2f), at)
    }
    // The inside of one: wall on both
    val inside = WorldData.createEmpty(40, 40)
    for (i <- 0 to 10) { inside.setTile(10, i, Tile.Wall); inside.setTile(i, 10, Tile.Wall) }
    inside.setTile(9, 10, Tile.Wall)
    assertHeading("into the corner two walls make", (-h, -h), bounced(inside, (9.8f, 9.8f), h, h)._1)
  }

  @Test def aShotWithNoBouncesLeftBouncesOffNothing(): Unit = {
    val w = walls()
    val p = new Projectile(1, UUID.randomUUID(), 0f, 0f, 1f, 0f, 0, 0, Rico)
    val count = ProjectileDef.get(Rico).ricochetCount
    for (i <- 1 to count) {
      p.updatePosition(9.6f, 5f, 1f, 0f)
      assertTrue(s"bounce $i of $count", p.ricochet(w))
    }
    p.updatePosition(9.6f, 5f, 1f, 0f)
    assertFalse("and no more", p.ricochet(w))
    assertEquals("left as it was", ((1f, 0f), 9.6f), ((p.dx, p.dy), p.getX))
  }
}
