package com.gridgame.client.render

import org.junit.Assert._
import org.junit.Test

import scala.collection.mutable

/**
 * Where another player is drawn (RemoteMotion): walked along the cells the server puts them on,
 * never past the newest. It used to run them on at a velocity guessed from the last two updates
 * for 75ms after the news stopped, so anyone moving fast — a dash most of all — was drawn cells
 * past where they stopped, through the wall that stopped them, and pulled back.
 */
class RemoteMotionTest {
  private val FRAME = 1000.0 / 60

  /**
   * Cells heard at times (ms), seen a frame at a time the way the renderer sees them (two in one
   * frame's gap, only the newer): where it was drawn in each frame up to `toMs`.
   */
  private def play(heard: Seq[(Double, Int, Int)], toMs: Double): IndexedSeq[(Double, Double, Double)] = {
    val m = new RemoteMotion
    val drawn = mutable.ArrayBuffer[(Double, Double, Double)]()
    var next = 0
    var cur = (heard.head._2, heard.head._3)
    var t = 0.0
    while (t <= toMs) {
      while (next < heard.length && heard(next)._1 <= t) { cur = (heard(next)._2, heard(next)._3); next += 1 }
      m.update(cur._1, cur._2, (t * 1e6).toLong, FRAME / 1000)
      drawn += ((t, m.x, m.y))
      t += FRAME
    }
    drawn.toIndexedSeq
  }

  /** How far it went from each frame to the next, over the frames from `fromMs` to `toMs`. */
  private def steps(drawn: Seq[(Double, Double, Double)], fromMs: Double, toMs: Double): Seq[Double] = {
    val in = drawn.filter { case (t, _, _) => t >= fromMs && t <= toMs }
    (1 until in.length).map(i => Math.hypot(in(i)._2 - in(i - 1)._2, in(i)._3 - in(i - 1)._3))
  }

  /** A walk east from (x0, y) a cell every `stepMs`, `n` cells, each arriving up to `jitterMs` late. */
  private def walk(x0: Int, y: Int, n: Int, stepMs: Double, fromMs: Double = 0, jitterMs: Int => Double = _ => 0.0) =
    (0 to n).map(i => (fromMs + i * stepMs + jitterMs(i), x0 + i, y))

  // Up to 8ms late, the least of it none
  private val jitter: Int => Double = i => (i * 5 % 9).toDouble

  @Test def aWalkerIsDrawnAtAnEvenPace(): Unit = {
    // 20 cells a second (a step every 50ms), each step up to 8ms late
    val drawn = play(walk(10, 10, 60, 50, jitterMs = jitter), 3000)
    val s = steps(drawn, 600, 2800)
    val perFrame = 20.0 * FRAME / 1000
    assertTrue(s.nonEmpty)
    s.foreach(step => assertEquals("a third of a cell a frame, every frame", perFrame, step, perFrame * 0.2))
  }

  @Test def aSlowerWalkerIsWalkedAtItsOwnPace(): Unit = {
    // A bot's walk, a step every 100ms: its pace is measured, not assumed to be a player's
    val drawn = play(walk(10, 10, 30, 100, jitterMs = jitter), 3000)
    val s = steps(drawn, 800, 2800)
    val perFrame = 10.0 * FRAME / 1000
    s.foreach(step => assertEquals(perFrame, step, perFrame * 0.2))
  }

  @Test def itIsNeverDrawnPastWhereItStopped(): Unit = {
    // Ten cells east and then nothing more: drawn up to x 20, never beyond, and there
    val drawn = play(walk(10, 10, 10, 50, jitterMs = jitter), 1500)
    drawn.foreach { case (_, x, _) => assertTrue(s"drawn at $x", x <= 20.0 + 1e-9) }
    assertEquals(20.0, drawn.last._2, 1e-9)
  }

  @Test def aDashIsFollowedAndNotOverrun(): Unit = {
    // Walking, then a dash: 12 cells in 150ms, the cells arriving a frame apart, and a stop at its
    // end — where the old smoothing ran the drawn player on four cells past, into the wall
    val w = walk(10, 10, 8, 50)
    val dashFrom = 400.0
    val dash = (1 to 9).map(i => (dashFrom + i * FRAME, 18 + Math.round(i * 12 / 9.0).toInt, 10))
    val drawn = play(w ++ dash, 1500)
    drawn.foreach { case (_, x, _) => assertTrue(s"drawn at $x, past the end of the dash", x <= 30.0 + 1e-9) }
    // It keeps up: never more than a few cells behind, and at the end within a quarter second
    val end = drawn.find { case (t, x, _) => t >= dashFrom && x >= 30.0 - 1e-9 }
    assertTrue("it got there", end.isDefined)
    assertTrue(s"at ${end.get._1}ms", end.get._1 - (dashFrom + 9 * FRAME) < 250)
    steps(drawn, 0, 1500).foreach(s => assertTrue(s"a jump of $s cells", s < 3.0))
  }

  @Test def itTurnsCornersWhereItWasHeardTurning(): Unit = {
    // East five cells, then south five: drawn on those cells' line, not across the corner, where
    // a wall standing in it would have had the player drawn inside it
    val east = walk(10, 10, 5, 50)
    val south = (1 to 5).map(i => (250.0 + i * 50, 15, 10 + i))
    val drawn = play(east ++ south, 1200)
    drawn.foreach { case (t, x, y) =>
      val onEast = Math.abs(y - 10.0) < 1e-6 && x >= 10 - 1e-6 && x <= 15 + 1e-6
      val onSouth = Math.abs(x - 15.0) < 1e-6 && y >= 10 - 1e-6 && y <= 15 + 1e-6
      assertTrue(s"drawn at ($x, $y) at ${t}ms, off the path", onEast || onSouth)
    }
  }

  @Test def aJumpIsDrawnAtOnce(): Unit = {
    // A blink or a respawn is not walked
    val heard = walk(10, 10, 4, 50) :+ ((300.0, 30, 30))
    val drawn = play(heard, 600)
    val after = drawn.filter(_._1 >= 300.0)
    assertEquals(30.0, after.head._2, 1e-9)
    assertEquals(30.0, after.head._3, 1e-9)
  }

  @Test def aBurstOfUpdatesIsNotASprint(): Unit = {
    // The same walk, but its steps arriving in pairs, two in one frame's gap and then none for a
    // hundred milliseconds: the old velocity guess read each pair as 55 cells a second
    val heard = (0 to 60).map(i => (100.0 * (i / 2) + (i % 2) * 5.0, 10 + i, 10))
    val drawn = play(heard, 3000)
    val s = steps(drawn, 600, 2800)
    val perFrame = 20.0 * FRAME / 1000
    s.foreach(step => assertTrue(s"a frame's step of $step", step < perFrame * 1.6))
  }
}
