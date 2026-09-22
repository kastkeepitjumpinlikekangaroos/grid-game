package com.gridgame.common.model

/**
 * The shape of a raised barrier ([[BarrierCast]]), shared by the server's collision and the
 * client's drawing so the two agree about where it is.
 *
 * A barrier is a shallow arc carried in front of its holder, facing their aim: a polyline of
 * [[SEGMENTS]] straight pieces centred [[DISTANCE]] cells out along the aim, [[WIDTH]] cells
 * across, with its two ends bent [[BEND]] cells back toward the holder. It is authored in the
 * holder's frame — f along the aim, a across it — and placed in the world by the holder's
 * position and the cosine and sine of the aim, so turning it is a rotation of that frame.
 *
 * It has a front and a back. Only a path that crosses it from the front (heading in, toward the
 * holder's side) meets it; a shot fired from behind the holder, or by an enemy who has got
 * inside the arc, goes out through it as through the back of a shield.
 */
object Barrier {
  /** How far out along the aim the middle of the barrier stands, in cells. */
  val DISTANCE: Float = 2.0f
  /** From end to end, across the aim. */
  val WIDTH: Float = 6.0f
  /** How far back toward the holder the two ends are bent. */
  val BEND: Float = 0.6f
  val SEGMENTS: Int = 3
  val POINTS: Int = SEGMENTS + 1

  // The polyline in the holder's frame, end to end across the aim: a flat middle a third of the
  // width wide, and the two outer thirds bent back
  private val F = Array(DISTANCE - BEND, DISTANCE, DISTANCE, DISTANCE - BEND)
  private val A = Array(-WIDTH / 2, -WIDTH / 6, WIDTH / 6, WIDTH / 2)

  /** How far from its holder any part of the barrier is: nothing further away can meet it. */
  val REACH: Float = {
    var r = 0f
    var i = 0
    while (i < POINTS) { r = Math.max(r, Math.sqrt(F(i) * F(i) + A(i) * A(i)).toFloat); i += 1 }
    r
  }

  /** Point `i` of the polyline in the holder's frame: along the aim, and across it. */
  def localF(i: Int): Float = F(i)
  def localA(i: Int): Float = A(i)

  /** Point `i` of the barrier of a holder at (hx, hy) facing (cos, sin), in world coordinates. */
  def pointX(hx: Float, cos: Float, sin: Float, i: Int): Float = hx + F(i) * cos - A(i) * sin
  def pointY(hy: Float, cos: Float, sin: Float, i: Int): Float = hy + F(i) * sin + A(i) * cos

  /** A world point in the frame of a holder at (hx, hy) facing (cos, sin): along the aim... */
  def forwardOf(hx: Float, hy: Float, cos: Float, sin: Float, x: Float, y: Float): Float =
    (x - hx) * cos + (y - hy) * sin

  /** ...and across it. */
  def acrossOf(hx: Float, hy: Float, cos: Float, sin: Float, x: Float, y: Float): Float =
    (y - hy) * cos - (x - hx) * sin

  /** How far out along the aim the barrier stands at `across` (clamped to its ends). */
  def forwardAt(across: Float): Float = {
    val a = Math.max(A(0), Math.min(A(POINTS - 1), across))
    var i = 0
    while (i < SEGMENTS - 1 && a > A(i + 1)) i += 1
    val t = (a - A(i)) / (A(i + 1) - A(i))
    F(i) + (F(i + 1) - F(i)) * t
  }

  // A joint between two segments must not let a path slip through between them
  private val JOINT_SLACK = 1e-4f

  /**
   * Where the path from (fa, aa) to (fb, ab), in the holder's frame, first meets the front of
   * the barrier: the fraction of the way along the path, in [0, 1], or -1 if it never does.
   * A path that crosses from behind, runs past an end or stops short doesn't meet it.
   */
  def crossingLocal(fa: Float, aa: Float, fb: Float, ab: Float): Float = {
    // Nowhere near: both ends short of the barrier's nearest point, both past its furthest, or
    // both wide of the same end
    if ((fa < F(0) && fb < F(0)) || (fa > DISTANCE && fb > DISTANCE)) return -1f
    val half = WIDTH / 2
    if ((aa < -half && ab < -half) || (aa > half && ab > half)) return -1f
    val rf = fb - fa
    val ra = ab - aa
    var best = -1f
    var i = 0
    while (i < SEGMENTS) {
      val sf = F(i + 1) - F(i)
      val sa = A(i + 1) - A(i)
      // The path's motion against the segment's outward normal (sa, -sf). Negative is heading in,
      // through the front; positive is going out through the back, and zero runs along it.
      val denom = rf * sa - ra * sf
      if (denom < 0f) {
        val qf = F(i) - fa
        val qa = A(i) - aa
        val t = (qf * sa - qa * sf) / denom
        val u = (qf * ra - qa * rf) / denom
        if (t >= 0f && t <= 1f && u >= -JOINT_SLACK && u <= 1f + JOINT_SLACK && (best < 0f || t < best)) best = t
      }
      i += 1
    }
    best
  }

  /**
   * Where the path from (ax, ay) to (bx, by) first meets the front of the barrier of a holder at
   * (hx, hy) facing (cos, sin): the fraction of the way along the path, in [0, 1], or -1 if it
   * never does.
   */
  def crossing(hx: Float, hy: Float, cos: Float, sin: Float, ax: Float, ay: Float, bx: Float, by: Float): Float =
    crossingLocal(forwardOf(hx, hy, cos, sin, ax, ay), acrossOf(hx, hy, cos, sin, ax, ay),
      forwardOf(hx, hy, cos, sin, bx, by), acrossOf(hx, hy, cos, sin, bx, by))

  /** Does the path from (ax, ay) to (bx, by) meet the front of this barrier? */
  def crosses(hx: Float, hy: Float, angle: Float, ax: Float, ay: Float, bx: Float, by: Float): Boolean =
    crossing(hx, hy, Math.cos(angle).toFloat, Math.sin(angle).toFloat, ax, ay, bx, by) >= 0f
}
