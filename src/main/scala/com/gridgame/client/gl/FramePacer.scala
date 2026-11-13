package com.gridgame.client.gl

/**
 * The moment each frame of a window is drawn for, on the display's cadence: what everything that
 * moves on the screen is placed by (gl/CLAUDE.md, *A frame is timed on the display's cadence*).
 *
 * A frame is begun when the window's loop is woken (the match's is a JavaFX AnimationTimer, on a
 * pulse that follows the display) and shown at one of the display's refreshes, a whole number of them
 * after the frame before. But the loop's waking wanders: on a 60 Hz iMac its frames began 15.7 to
 * 17.7ms apart (1st to 99th percentile) while the refreshes that showed them are 16.67ms apart.
 * Placed by when each began, anything moving at a steady pace was drawn up to a millisecond too far
 * on in one frame and as much too short in the next.
 *
 * So a frame's time is the last frame's plus as many refreshes as have passed, when the gap is that
 * to within [[snapNanos]], pulled a tenth of the way toward when it really began, so it keeps the
 * loop's own phase and pace (a display a little off its nominal rate included) without its wander. A
 * loop that keeps another cadence than the display's (no vsync, a display it doesn't keep up with:
 * its gaps are off a whole number of refreshes by the same amount frame after frame) is taken as it
 * comes, and so is a gap nowhere near one, or a stall.
 *
 * `refreshHz` is the display's (GLFW's video mode), 60 when it isn't known.
 */
final class FramePacer(refreshHz: Int = 60) {
  import FramePacer._

  private val periodNanos: Double = 1e9 / (if (refreshHz >= 24 && refreshHz <= 500) refreshHz else 60)
  /** How far off a whole number of refreshes a gap may be and still be taken as one: the loop's
    * wander, as far as it can be told apart from a refresh more or less. */
  private val snapNanos: Double = Math.min(3e6, periodNanos * 0.4)

  private var started = false
  private var last = 0L       // the time the last frame was drawn for
  private var lastBegan = 0L  // and when it began
  private var gap = 0L
  // How far off a whole number of refreshes the gaps between frames have been lately, on average:
  // nothing but the wander's to and fro on the display's cadence, the same every frame off it
  private var bias = 0.0

  /** The time the frame begun at `beganNanos` (System.nanoTime) is drawn for. */
  def next(beganNanos: Long): Long = {
    if (!started) {
      started = true
      last = beganNanos
      lastBegan = beganNanos
      gap = Math.round(periodNanos)
      return beganNanos
    }
    val raw = (beganNanos - lastBegan).toDouble
    lastBegan = beganNanos
    val refreshes = Math.round(raw / periodNanos)
    val off = raw - refreshes * periodNanos
    bias += (off - bias) * BiasRate
    val onCadence = refreshes >= 1 && refreshes <= MaxRefreshes && Math.abs(off) <= snapNanos &&
      Math.abs(bias) <= MaxBiasNanos
    val t =
      if (!onCadence) beganNanos
      else {
        val due = last + refreshes * periodNanos
        val drawnFor = Math.round(due + (beganNanos - due) * Follow)
        // Never further from when the frame began than a third of a refresh
        Math.max(beganNanos - (periodNanos / 3).toLong, Math.min(beganNanos + (periodNanos / 3).toLong, drawnFor))
      }
    gap = t - last
    last = t
    t
  }

  /** The time since the frame before, in seconds. */
  def deltaSec: Double = gap / 1e9
}

object FramePacer {
  /** How much of the way toward when a frame really began its time is pulled. */
  private val Follow = 0.1
  /** The share of each new gap in the average that says whether the loop keeps the display's cadence. */
  private val BiasRate = 0.05
  /** Gaps off a whole number of refreshes by more than this on average: another cadence. */
  private val MaxBiasNanos = 0.5e6
  /** A gap of more refreshes than this is a stall. */
  private val MaxRefreshes = 4L
}
