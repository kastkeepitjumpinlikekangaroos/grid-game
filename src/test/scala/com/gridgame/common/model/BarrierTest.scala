package com.gridgame.common.model

import org.junit.Assert._
import org.junit.Test

/**
 * The barrier's shape, which the server stops shots on and the client draws: a shallow arc 2
 * cells out along its holder's aim, 6 wide, its ends bent 0.6 back. A holder here stands at
 * (10, 10); an angle of 0 faces +x.
 */
class BarrierTest {
  private val East = 0f
  private val South = (Math.PI / 2).toFloat

  @Test def aShotAtTheFrontCrossesIt(): Unit = {
    assertTrue(Barrier.crosses(10, 10, East, 15, 10, 10, 10))
    // Where: the middle stands 2 cells out, so a path from 5 out meets it three fifths along
    assertEquals(0.6f, Barrier.crossing(10, 10, 1f, 0f, 15, 10, 10, 10), 1e-5f)
  }

  @Test def fromBehindItDoesnt(): Unit = {
    // Out through it from the holder's side, as the holder's own shots go
    assertFalse(Barrier.crosses(10, 10, East, 10, 10, 15, 10))
    // A shot at the holder from behind never gets to it
    assertFalse(Barrier.crosses(10, 10, East, 4, 10, 10, 10))
    // And one that got inside the arc, fired by an enemy who walked in, is past it
    assertFalse(Barrier.crosses(10, 10, East, 11.5f, 10, 10, 10))
  }

  @Test def wideOfTheEndsItMisses(): Unit = {
    // Straight in along the aim, 3.2 cells to the side of it: past the end, which is 3 across
    assertFalse(Barrier.crosses(10, 10, East, 15, 13.2f, 5, 13.2f))
    assertFalse(Barrier.crosses(10, 10, East, 15, 6.8f, 5, 6.8f))
    // Inside the ends it is stopped, where the bent end stands: nearer the holder than the middle
    val t = Barrier.crossing(10, 10, 1f, 0f, 15, 12.8f, 5, 12.8f)
    assertTrue(t >= 0f)
    assertEquals(10f + Barrier.forwardAt(2.8f), 15f - 10f * t, 1e-4f)
    assertTrue(Barrier.forwardAt(2.8f) < Barrier.DISTANCE - 0.4f)
  }

  @Test def itIsWholeAcrossItsJoints(): Unit = {
    // Straight in at the joints between the middle and the ends: no gap between segments
    assertTrue(Barrier.crosses(10, 10, East, 15, 11f, 5, 11f))
    assertTrue(Barrier.crosses(10, 10, East, 15, 9f, 5, 9f))
    // And at a slant, through the joint itself (2 out, 1 across)
    assertEquals(0.5f, Barrier.crossing(10, 10, 1f, 0f, 15, 13, 9, 9), 1e-4f)
  }

  @Test def aPathThatStopsShortDoesNotMeetIt(): Unit = {
    assertFalse(Barrier.crosses(10, 10, East, 15, 10, 12.5f, 10))
  }

  @Test def theShapeTurnsWithTheAngle(): Unit = {
    // Facing +y, a shot coming up from +y meets it and one from +x (now its side) doesn't
    assertTrue(Barrier.crosses(10, 10, South, 10, 15, 10, 10))
    assertFalse(Barrier.crosses(10, 10, South, 15, 10, 10, 10))
    // Every way it faces, it keeps its shape: middle 2 out, ends 3 across and 1.4 out
    for (deg <- 0 until 360 by 15) {
      val a = Math.toRadians(deg).toFloat
      val c = Math.cos(a).toFloat
      val s = Math.sin(a).toFloat
      assertEquals(s"$deg", 0.6f, Barrier.crossing(10, 10, c, s, 10 + 5 * c, 10 + 5 * s, 10, 10), 1e-4f)
      val first = 0
      val last = Barrier.POINTS - 1
      for (end <- Seq(first, last)) {
        val x = Barrier.pointX(10, c, s, end)
        val y = Barrier.pointY(10, c, s, end)
        assertEquals(s"$deg", Barrier.DISTANCE - Barrier.BEND, Barrier.forwardOf(10, 10, c, s, x, y), 1e-4f)
        assertEquals(s"$deg", if (end == first) -Barrier.WIDTH / 2 else Barrier.WIDTH / 2,
          Barrier.acrossOf(10, 10, c, s, x, y), 1e-4f)
      }
    }
  }

  @Test def itIsTheShapeThePlanAsksFor(): Unit = {
    assertEquals(2.0f, Barrier.DISTANCE, 0f)
    assertEquals(6.0f, Barrier.WIDTH, 0f)
    assertEquals(0.6f, Barrier.BEND, 0f)
    assertEquals(3, Barrier.SEGMENTS)
    assertEquals(Barrier.DISTANCE, Barrier.forwardAt(0f), 0f)
    assertEquals(Barrier.DISTANCE - Barrier.BEND, Barrier.forwardAt(Barrier.WIDTH / 2), 1e-6f)
    // Nothing of it is further from the holder than its reach: its ends are the furthest
    assertEquals(Math.sqrt(1.4 * 1.4 + 3 * 3).toFloat, Barrier.REACH, 1e-5f)
  }
}
