package com.gridgame.client.render

/**
 * Where another player is drawn: walked along the cells the server has put them on, at the pace
 * they have been moving, and never past the newest.
 *
 * Their position arrives a cell at a time as they walk, a cell or two a frame in a dash, and many
 * cells at once for a blink or a respawn. It used to be drawn by guessing a velocity from the time
 * between the last two changes seen and running them on at it for up to 75ms after the news
 * stopped, through whatever was in the way. The guess swung with frame timing (two updates a frame
 * apart read as 55 cells a second), and a dash's 60 cells a second ran the drawn player four cells
 * past where the dash had ended — into the tree that ended it — before pulling them back. The
 * faster someone moved, the further they clipped.
 *
 * Now every cell heard is a waypoint, and the drawn player walks from one to the next at the pace
 * the last few arrived at: measured, not assumed, so a bot's slower walk, a slow and a boost all
 * come out right. It keeps about [[LAG_CELLS]] behind the newest cell, a few percent quicker when
 * further back and slower when closer, so a step that arrives a little late finds it still
 * walking rather than stood waiting. Further behind than walking leaves it (a dash), it
 * catches up. A move no step makes is a jump, drawn at once.
 */
final class RemoteMotion {
  import RemoteMotion._

  /** Where it is drawn this frame. */
  var x = 0.0
  var y = 0.0

  // Cells heard and not yet reached, oldest first (a ring)
  private val wayX = new Array[Int](WAYPOINTS)
  private val wayY = new Array[Int](WAYPOINTS)
  private var wayHead = 0
  private var wayCount = 0
  // The newest cell heard
  private var lastX = 0
  private var lastY = 0
  private var started = false
  // The last few moves heard, when and how far, which set the pace (a ring)
  private val hopAt = new Array[Long](HOPS)
  private val hopLen = new Array[Double](HOPS)
  private var hopNext = 0
  private var hopCount = 0
  private var pace = DEFAULT_PACE

  /** The pace it is walked at, in cells a second. */
  def cellsPerSecond: Double = pace

  /** The server has them on cell (cx, cy) as of `nowNanos`: walk the drawn player on by `dtSec`. */
  def update(cx: Int, cy: Int, nowNanos: Long, dtSec: Double): Unit = {
    if (!started) {
      started = true
      jumpTo(cx, cy)
      return
    }
    if (cx != lastX || cy != lastY) {
      val hop = Math.hypot(cx - lastX, cy - lastY)
      if (hop > JUMP_CELLS) jumpTo(cx, cy)
      else {
        heardHop(hop, nowNanos)
        if (wayCount == WAYPOINTS) { wayHead = (wayHead + 1) % WAYPOINTS; wayCount -= 1 }
        val slot = (wayHead + wayCount) % WAYPOINTS
        wayX(slot) = cx; wayY(slot) = cy
        wayCount += 1
        lastX = cx; lastY = cy
      }
    }
    walk(dtSec)
  }

  /** A move no step makes — a blink, a respawn, a long knockback: drawn there at once. */
  private def jumpTo(cx: Int, cy: Int): Unit = {
    x = cx; y = cy
    lastX = cx; lastY = cy
    wayCount = 0
    hopCount = 0
  }

  /** How far from where it is drawn to the newest cell, along the cells between. */
  private def behind: Double = {
    var d = 0.0
    var px = x; var py = y
    var i = 0
    while (i < wayCount) {
      val s = (wayHead + i) % WAYPOINTS
      d += Math.hypot(wayX(s) - px, wayY(s) - py)
      px = wayX(s); py = wayY(s)
      i += 1
    }
    d
  }

  private def heardHop(len: Double, at: Long): Unit = {
    hopAt(hopNext) = at; hopLen(hopNext) = len
    hopNext = (hopNext + 1) % HOPS
    if (hopCount < HOPS) hopCount += 1
    // Cells covered over the span of the moves heard in the last PACE_WINDOW, newest first. The
    // oldest of them only starts the span: its own cell was walked before it began.
    var oldest = at
    var oldestLen = 0.0
    var cells = 0.0
    var i = 0
    while (i < hopCount) {
      val s = Math.floorMod(hopNext - 1 - i, HOPS)
      if (at - hopAt(s) > PACE_WINDOW_NS) i = hopCount // and every older one
      else {
        cells += hopLen(s)
        oldest = hopAt(s); oldestLen = hopLen(s)
        i += 1
      }
    }
    val span = (at - oldest) / 1e9
    if (span >= MIN_SPAN_S) pace = Math.max(MIN_PACE, Math.min(MAX_PACE, (cells - oldestLen) / span))
  }

  private def walk(dtSec: Double): Unit = {
    if (wayCount == 0 || dtSec <= 0.0) return
    val left = behind
    if (left > MAX_BEHIND_CELLS) {
      val s = (wayHead + wayCount - 1) % WAYPOINTS
      jumpTo(wayX(s), wayY(s))
      return
    }
    // Its own pace, eased toward keeping LAG_CELLS behind. Well behind — further than the cell a
    // step puts between them — it catches up, the quicker the further behind it is
    val eased = pace * Math.max(1.0 - EASE_LIMIT, Math.min(1.0 + EASE_LIMIT, 1.0 + EASE * (left - LAG_CELLS)))
    val catchUp = if (left > CATCH_UP_CELLS) pace + (left - CATCH_UP_CELLS) / CATCH_UP_S else 0.0
    var budget = Math.max(eased, catchUp) * dtSec
    while (budget > 0.0 && wayCount > 0) {
      val tx = wayX(wayHead); val ty = wayY(wayHead)
      val dx = tx - x; val dy = ty - y
      val d = Math.hypot(dx, dy)
      if (d <= budget) {
        x = tx; y = ty
        budget -= d
        wayHead = (wayHead + 1) % WAYPOINTS
        wayCount -= 1
      } else {
        x += dx / d * budget; y += dy / d * budget
        budget = 0.0
      }
    }
  }
}

object RemoteMotion {
  /** How many cells heard it can be behind on; more than that and it is drawn at the newest. */
  private val WAYPOINTS = 24
  /** A move longer than this between two updates is not a step but a jump. */
  val JUMP_CELLS = 4.5
  /** Further behind than this, it is drawn at the newest cell at once. */
  val MAX_BEHIND_CELLS = 16.0
  /** How far behind the newest cell it keeps while walking. */
  val LAG_CELLS = 0.8
  /** How much quicker (slower) it walks for each cell it is further behind (closer) than that... */
  private val EASE = 0.1
  /** ...but never more than this much off its pace. */
  private val EASE_LIMIT = 0.15
  /** Further behind than this it is catching up: more than walking ever leaves between them. */
  private val CATCH_UP_CELLS = 2.0
  /** Catching up, the distance beyond that shrinks to a third of itself in about this long. */
  private val CATCH_UP_S = 0.07
  /** The pace is measured over the moves heard in this long. */
  private val PACE_WINDOW_NS = 250000000L
  private val MIN_SPAN_S = 0.06
  private val HOPS = 16
  /** A player walks 20 cells a second (Movement), until it has been seen doing otherwise. */
  val DEFAULT_PACE = 20.0
  private val MIN_PACE = 3.0
  private val MAX_PACE = 150.0
}
