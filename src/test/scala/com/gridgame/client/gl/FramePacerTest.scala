package com.gridgame.client.gl

import org.junit.Assert._
import org.junit.Test

/**
 * When each frame is drawn for (FramePacer): on the display's cadence, not when the window's loop
 * happened to wake. On a 60 Hz screen the match's frames began 15.7 to 17.7ms apart (1st to 99th
 * percentile) while the refreshes that showed them were 16.67ms apart, so everything that moves was
 * placed up to a millisecond off its time in a frame and as much the other way in the next. The
 * frames here wander more than that, up to 1.5ms either way.
 */
class FramePacerTest {
  private val Period = 1e9 / 60

  /** Frames begun a refresh apart (or `refreshes(i)` of them), each `wander(i)` ns late or early. */
  private def times(n: Int, wander: Int => Double, refreshes: Int => Int = _ => 1, period: Double = Period): Seq[Long] = {
    var at = 5e9
    (0 until n).map { i =>
      at += refreshes(i) * period
      Math.round(at + wander(i))
    }
  }

  // Up to 1.5ms either way, at random
  private val wanders: Array[Double] = {
    val rng = new java.util.Random(11)
    Array.fill(1000)((rng.nextDouble() * 2 - 1) * 1.5e6)
  }
  private val wander: Int => Double = i => wanders(i)

  @Test def framesThatWanderAreDrawnARefreshApart(): Unit = {
    val pacer = new FramePacer(60)
    val began = times(600, wander)
    val drawn = began.map(pacer.next)
    val gaps = (1 until drawn.length).map(i => drawn(i) - drawn(i - 1))
    // The raw gaps are as much as 2.5ms either side of a refresh
    assertTrue((1 until began.length).exists(i => Math.abs(began(i) - began(i - 1) - Period) > 2.5e6))
    // Each drawn a refresh after the last, to within a third of a millisecond, once settled
    gaps.drop(30).foreach(g => assertEquals("a refresh apart", Period, g.toDouble, 0.3e6))
    // And never more than the wander from when it began: it keeps the display's phase
    drawn.zip(began).drop(30).foreach { case (d, b) => assertTrue(s"${(d - b) / 1e6}ms off", Math.abs(d - b) < 2e6) }
    assertEquals(Period / 1e9, pacer.deltaSec, 0.0002)
  }

  @Test def aMissedRefreshIsTwoOfThem(): Unit = {
    // A frame that took too long is shown a refresh late: the next is drawn two after the last
    val pacer = new FramePacer(60)
    val drawn = times(200, wander, refreshes = i => if (i == 100) 2 else 1).map(pacer.next)
    assertEquals(2 * Period, (drawn(100) - drawn(99)).toDouble, 0.4e6)
    assertEquals(Period, (drawn(101) - drawn(100)).toDouble, 0.4e6)
  }

  @Test def aStallStartsItAgainFromNow(): Unit = {
    // Half a second with no frame (the window hidden, a hitch): drawn for when it began
    val pacer = new FramePacer(60)
    val before = times(100, wander)
    before.foreach(pacer.next)
    val late = before.last + 500000000L + 1234567L
    assertEquals(late, pacer.next(late))
  }

  @Test def framesOffTheDisplaysCadenceAreTakenAsTheyCome(): Unit = {
    // A loop that doesn't keep the display's pace (here every 18ms on a 60 Hz screen, as the match's
    // did once under load, or with no vsync at all) gains nothing from being put on it: each frame is
    // drawn for when it began
    val pacer = new FramePacer(60)
    val began = times(300, _ => 0.0, period = 18e6)
    val drawn = began.map(pacer.next)
    drawn.zip(began).drop(60).foreach { case (d, b) => assertEquals(b.toDouble, d.toDouble, 1.0) }
  }

  @Test def onA120HzScreenTwoRefreshesAFrameIsTheCadence(): Unit = {
    // A 60 fps loop on a 120 Hz screen is shown every other refresh
    val pacer = new FramePacer(120)
    val drawn = times(300, wander).map(pacer.next)
    (31 until drawn.length).foreach(i => assertEquals(Period, (drawn(i) - drawn(i - 1)).toDouble, 0.3e6))
  }

  @Test def anUnknownRefreshRateIsSixtyHz(): Unit = {
    val pacer = new FramePacer(0)
    val drawn = times(200, wander).map(pacer.next)
    (31 until drawn.length).foreach(i => assertEquals(Period, (drawn(i) - drawn(i - 1)).toDouble, 0.3e6))
  }
}
