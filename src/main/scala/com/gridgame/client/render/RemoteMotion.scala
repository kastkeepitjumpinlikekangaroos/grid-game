package com.gridgame.client.render

import com.gridgame.client.game.KnockbackSlide

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
 * they arrive at: measured, not assumed, so a bot's slower walk, a slow and a boost all come out
 * right. It keeps far enough behind the newest cell that, as each new one arrives, it still has
 * [[BUFFER_S]] of walking left to the one before (a few percent quicker when it has more, slower
 * when less), so a step that arrives a little late finds it still walking rather than stood
 * waiting. Further behind than walking leaves it (a dash), it catches up. A move no step makes is a
 * jump, drawn at once — except a knockback, which the server counts for every client to see
 * (Player.getKnockbacks): that is slid, from wherever the player was drawn to where it put them
 * (KnockbackSlide), and the walk goes on from there.
 *
 * Steps arrive unevenly even from a walker who keeps an even pace: each is taken on one of their
 * frames, so a 50ms step comes two, three or four of them after the last, and the network adds its
 * own. So the pace is measured over [[PACE_LONG_NS]] unless the last [[PACE_SHORT_NS]] says it has
 * changed (a slow, a boost, a stop), and the walking it had left is judged as each cell arrives and
 * averaged over a few. Judged every frame, as it was, how far behind it was rose a cell with every
 * arrival and fell away till the next, and the walk sped up and slowed down with it, three times a
 * step; and kept in cells (0.8), it was a third less time for a diagonal walker, whose steps are 1.4
 * cells, than for one walking straight. On a diagonal walk heard as another client steps it, drawn
 * by the real loop, the walker's step from frame to frame varied by 19% (sd), stood still in 1% of
 * frames and went less than half as far in 3%; now by 3.5%, and never under 0.8 of its pace
 * (render/CLAUDE.md, *Other players are walked along the cells they were heard on*).
 */
final class RemoteMotion {
  import RemoteMotion._

  /** Where it is drawn this frame: along the walk, and what is left of a knockback's slide. */
  var x = 0.0
  var y = 0.0

  // Where the walk along the cells heard has got to
  private var walkX = 0.0
  private var walkY = 0.0
  // Knockbacks heard (the player's count), and the slide the latest set going: how far from the
  // cell it put them it started, when, and for how long
  private var knocks = 0
  private var sliding = false
  private var slideX = 0.0
  private var slideY = 0.0
  private var slideAt = 0L
  private var slideMs = 0.0

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
  // The walking it had left to the newest cell as each new one arrived (how far behind it was then,
  // less the hop the new one brought), averaged over the last few; and the hop heard this frame, or 0
  private var leftAvg = 0.0
  private var leftKnown = false
  private var arrivedHop = 0.0

  /** The pace it is walked at, in cells a second. */
  def cellsPerSecond: Double = pace

  /** Is a knockback sliding them as of the last update? Shoved, not walking. */
  def isSliding: Boolean = sliding

  /** The server has them on cell (cx, cy) as of `nowNanos`: walk the drawn player on by `dtSec`. */
  def update(cx: Int, cy: Int, nowNanos: Long, dtSec: Double): Unit = update(cx, cy, knocks, nowNanos, dtSec)

  /** The same, with how many knockbacks they have been heard taking: a new one since the last
    * frame means the move to (cx, cy) was one. */
  def update(cx: Int, cy: Int, knockbacks: Int, nowNanos: Long, dtSec: Double): Unit = {
    if (!started) {
      started = true
      knocks = knockbacks
      jumpTo(cx, cy)
      place(nowNanos)
      return
    }
    if (knockbacks != knocks) {
      knocks = knockbacks
      if (cx != lastX || cy != lastY) {
        // Slid from where they are drawn to where it put them, and walked on from there
        val fromX = x; val fromY = y
        jumpTo(cx, cy)
        sliding = true
        slideX = fromX - cx; slideY = fromY - cy
        slideAt = nowNanos
        slideMs = KnockbackSlide.durationMs(Math.hypot(slideX, slideY))
        place(nowNanos)
        return
      }
    }
    if (cx != lastX || cy != lastY) {
      val hop = Math.hypot(cx - lastX, cy - lastY)
      if (hop > JUMP_CELLS) jumpTo(cx, cy)
      else {
        heardHop(hop, nowNanos)
        arrivedHop = hop
        if (wayCount == WAYPOINTS) { wayHead = (wayHead + 1) % WAYPOINTS; wayCount -= 1 }
        val slot = (wayHead + wayCount) % WAYPOINTS
        wayX(slot) = cx; wayY(slot) = cy
        wayCount += 1
        lastX = cx; lastY = cy
      }
    }
    walk(dtSec)
    place(nowNanos)
  }

  /** Where it is drawn: where the walk has got to, and what is left of a knockback's slide. */
  private def place(nowNanos: Long): Unit = {
    x = walkX; y = walkY
    if (sliding) {
      val left = KnockbackSlide.remaining((nowNanos - slideAt) / 1e6, slideMs)
      if (left <= 0.0) sliding = false
      else { x += slideX * left; y += slideY * left }
    }
  }

  /** A move no step makes — a blink, a respawn, a pull: drawn there at once. */
  private def jumpTo(cx: Int, cy: Int): Unit = {
    walkX = cx; walkY = cy
    lastX = cx; lastY = cy
    wayCount = 0
    hopCount = 0
    sliding = false
    leftKnown = false
    arrivedHop = 0.0
  }

  /** How far from where the walk has got to to the newest cell, along the cells between. */
  private def behind: Double = {
    var d = 0.0
    var px = walkX; var py = walkY
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
    // Over the long window, unless the short one has it walking at another pace by now
    val long = paceOver(at, PACE_LONG_NS)
    val short = paceOver(at, PACE_SHORT_NS)
    val p = if (long < 0.0) short
      else if (short >= 0.0 && Math.abs(short - long) > PACE_CHANGE * long) short
      else long
    if (p >= 0.0) pace = Math.max(MIN_PACE, Math.min(MAX_PACE, p))
  }

  /** Cells a second over the moves heard in the last `windowNs` before `at`, newest first, or -1
    * when they span too little time to say. The oldest of them only starts the span: its own cell
    * was walked before it began. */
  private def paceOver(at: Long, windowNs: Long): Double = {
    var oldest = at
    var oldestLen = 0.0
    var cells = 0.0
    var i = 0
    while (i < hopCount) {
      val s = Math.floorMod(hopNext - 1 - i, HOPS)
      if (at - hopAt(s) > windowNs) i = hopCount // and every older one
      else {
        cells += hopLen(s)
        oldest = hopAt(s); oldestLen = hopLen(s)
        i += 1
      }
    }
    val span = (at - oldest) / 1e9
    if (span >= MIN_SPAN_S) (cells - oldestLen) / span else -1.0
  }

  private def walk(dtSec: Double): Unit = {
    if (wayCount == 0 || dtSec <= 0.0) return
    val left = behind
    if (left > MAX_BEHIND_CELLS) {
      val s = (wayHead + wayCount - 1) % WAYPOINTS
      jumpTo(wayX(s), wayY(s))
      return
    }
    // The walking it had left when a cell arrived: how far behind it is now, less the hop it brought
    if (arrivedHop > 0.0) {
      val sample = left - arrivedHop
      if (leftKnown) leftAvg += (sample - leftAvg) / ARRIVALS_AVERAGED
      else { leftAvg = sample; leftKnown = true }
      arrivedHop = 0.0
    }
    // Its own pace, eased toward having BUFFER_S of it left as each cell arrives. Well behind —
    // further than walking leaves it — it catches up, the quicker the further behind it is
    val off = if (leftKnown) leftAvg - BUFFER_S * pace else 0.0
    val eased = pace * Math.max(1.0 - EASE_LIMIT, Math.min(1.0 + EASE_LIMIT, 1.0 + EASE * off))
    val catchUpFrom = Math.max(CATCH_UP_CELLS, CATCH_UP_LEAD_S * pace)
    val catchUp = if (left > catchUpFrom) pace + (left - catchUpFrom) / CATCH_UP_S else 0.0
    var budget = Math.max(eased, catchUp) * dtSec
    while (budget > 0.0 && wayCount > 0) {
      val tx = wayX(wayHead); val ty = wayY(wayHead)
      val dx = tx - walkX; val dy = ty - walkY
      val d = Math.hypot(dx, dy)
      if (d <= budget) {
        walkX = tx; walkY = ty
        budget -= d
        wayHead = (wayHead + 1) % WAYPOINTS
        wayCount -= 1
      } else {
        walkX += dx / d * budget; walkY += dy / d * budget
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
  /** The walking, in seconds of its pace, it keeps left to the newest cell as the next arrives: the
    * most a step comes late by its walker's frame (up to 17ms at 60 fps) and a home network's
    * jitter. Drawn walking, it is about this and half a step behind the newest cell heard. */
  val BUFFER_S = 0.03
  /** How many arrivals the walking it has left is averaged over. */
  private val ARRIVALS_AVERAGED = 4.0
  /** How much quicker (slower) it walks for each cell more (less) than that it has left... */
  private val EASE = 0.15
  /** ...but never more than this much off its pace. */
  private val EASE_LIMIT = 0.15
  /** Further behind than this it is catching up: more than walking ever leaves between them, in
    * cells or seconds of its pace, whichever is more (a diagonal step is 1.4 cells). */
  private val CATCH_UP_CELLS = 2.0
  private val CATCH_UP_LEAD_S = 0.1
  /** Catching up, the distance beyond that shrinks to a third of itself in about this long. */
  private val CATCH_UP_S = 0.07
  /** The pace is measured over the moves heard in this long... */
  private val PACE_LONG_NS = 600000000L
  /** ...unless over this long it is more than PACE_CHANGE off that: it has changed. */
  private val PACE_SHORT_NS = 250000000L
  private val PACE_CHANGE = 0.2
  private val MIN_SPAN_S = 0.06
  private val HOPS = 40
  /** A player walks 20 cells a second (Movement), until it has been seen doing otherwise. */
  val DEFAULT_PACE = 20.0
  private val MIN_PACE = 3.0
  private val MAX_PACE = 150.0
}
