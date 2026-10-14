package com.gridgame.client.render.projectiles

import com.gridgame.common.model.Projectile
import com.gridgame.client.gl.ShapeBatch
import ProjectileKit._
import Silhouettes._
import Lobbed._

/** Jaws, claws and stings, closing on the hitbox with the foot, the jaw or the tail behind them: the
  * birds' talons (a raptor's foot kicking), the claw marks, the Vampire's bite, the Scorpion's sting,
  * the Minotaur's horn, the Wolf's spirit lunging with its jaws open, and the Shark lunging (drawn on
  * its surge by Tethers). */
private[render] object Bites {
  // ── A bird of prey's foot: the Raptor's, the Hawk's and the Griffin's talons ──
  // Side on, +x the way it strikes and +y down its side (turned over when it flies left, so its
  // talons still hook down), in units of about a third of its length: the feathered thigh, the
  // scaled leg, three toes fanned forward and the hind toe back, each ending in a black talon that
  // hooks down, the four of them closing on the hitbox as the foot clenches.
  private final val FOOT_ANKLE_X = -0.85f
  private final val FOOT_ANKLE_Y = -0.15f
  private final val FOOT_KNEE_X = -1.75f
  private final val FOOT_KNEE_Y = -1.0f
  // Each toe's end open (struck out) and shut (clenched): three forward, the hind toe last
  private val TOE_OPEN_X = Array(0.05f, 0.27f, 0.02f, -1.32f)
  private val TOE_OPEN_Y = Array(-0.64f, -0.12f, 0.44f, 0.3f)
  private val TOE_SHUT_X = Array(-0.08f, 0.06f, -0.14f, -1.12f)
  private val TOE_SHUT_Y = Array(-0.36f, -0.02f, 0.24f, 0.2f)
  // The feathered thigh: feathers fanned back from the knee, each a pointed leaf (base, a side,
  // its tip, the other side), the furthest back drawn first so each lies over the one behind it
  private val FEATHER = Array(
    -1.9f, -0.82f,  -2.5f, -0.5f,   -2.95f, -0.72f,  -2.42f, -1.0f,
    -1.82f, -1.02f, -2.6f, -0.9f,   -3.15f, -1.2f,   -2.55f, -1.42f,
    -1.78f, -1.18f, -2.42f, -1.38f, -2.86f, -1.78f,  -2.2f, -1.72f,
    -1.55f, -1.05f, -1.98f, -1.62f, -2.12f, -2.02f,  -1.6f, -1.6f)
  // The foot's frame: its local origin on screen, along it and across it (down its side)
  private var _tox = 0f; private var _toy = 0f
  private var _tfx = 1f; private var _tfy = 0f; private var _tqx = 0f; private var _tqy = 1f
  @inline private def tx(lx: Float, ly: Float): Float = _tox + lx * _tfx + ly * _tqx
  @inline private def ty(lx: Float, ly: Float): Float = _toy + lx * _tfy + ly * _tqy
  private val _tlx = new Array[Float](8)
  private val _tly = new Array[Float](8)

  /** The talon of toe `t` (3 the hind toe), from its end (ex, ey) along its unit direction (dx, dy),
    * into _pvX/_pvY, four points: on from the toe a little, then hooking down. */
  private def talonPts(t: Int, ex: Float, ey: Float, dx: Float, dy: Float, len: Float): Unit = {
    // Down its side: the hind toe points back, so its talon hooks the other way round to come forward
    val nx = if (t == 3) dy else -dy; val ny = if (t == 3) -dx else dx
    var i = 0
    while (i < 4) {
      val u = i / 3f
      val lx = ex + dx * len * (0.2f + 0.75f * u - 0.35f * u * u) + nx * len * (0.95f * u * u)
      val ly = ey + dy * len * (0.2f + 0.75f * u - 0.35f * u * u) + ny * len * (0.95f * u * u)
      _pvX(i) = tx(lx, ly); _pvY(i) = ty(lx, ly)
      i += 1
    }
  }

  /**
   * A bird of prey's foot striking, side on, its talons on (sx, sy): flying along (ndx, ndy), `open`
   * 1 struck out wide and 0 clenched shut. `s` virtual units to a unit of it; the feathers of its
   * thigh in (fr, fg, fb), lighter at their tips (lr, lg, lb). Inked all round, then filled.
   */
  private[projectiles] def birdFoot(sb: ShapeBatch, sx: Float, sy: Float, ndx: Float, ndy: Float, s: Float,
                                    open: Float, fr: Float, fg: Float, fb: Float, lr: Float, lg: Float, lb: Float,
                                    a: Float): Unit = {
    val flip = if (ndx < -0.05f) -1f else 1f
    _tfx = ndx * s; _tfy = ndy * s
    _tqx = -ndy * s * flip; _tqy = ndx * s * flip
    // The talons meet about here: on the hitbox
    _tox = sx - (0.45f * _tfx + 0.18f * _tqx); _toy = sy - (0.45f * _tfy + 0.18f * _tqy)
    val ink = 0.07f * s + 1.6f
    var pass = 0
    while (pass < 2) {
      val inkPass = pass == 0
      // The thigh's feathers, the furthest back first
      var k = 0
      while (k < 4) {
        var q = 0
        while (q < 4) { _tlx(q) = tx(FEATHER(k * 8 + q * 2), FEATHER(k * 8 + q * 2 + 1)); _tly(q) = ty(FEATHER(k * 8 + q * 2), FEATHER(k * 8 + q * 2 + 1)); q += 1 }
        if (inkPass) sb.strokePolygon(_tlx, _tly, 4, ink, 0.12f, 0.07f, 0.03f, 0.9f * a)
        else {
          val lit = k * 0.2f
          sb.fillPolygon(_tlx, _tly, 4, mix(fr, lr, lit), mix(fg, lg, lit), mix(fb, lb, lit), a)
          // Its quill
          sb.strokeLine(_tlx(0), _tly(0), mix(_tlx(0), _tlx(2), 0.8f), mix(_tly(0), _tly(2), 0.8f), 0.04f * s + 0.4f,
            mix(fr, 0f, 0.45f), mix(fg, 0f, 0.45f), mix(fb, 0f, 0.45f), 0.6f * a)
        }
        k += 1
      }
      // The leg, knee to ankle
      _pvX(0) = tx(FOOT_KNEE_X, FOOT_KNEE_Y); _pvY(0) = ty(FOOT_KNEE_X, FOOT_KNEE_Y)
      _pvX(1) = tx(-1.28f, -0.56f); _pvY(1) = ty(-1.28f, -0.56f)
      _pvX(2) = tx(FOOT_ANKLE_X, FOOT_ANKLE_Y); _pvY(2) = ty(FOOT_ANKLE_X, FOOT_ANKLE_Y)
      if (inkPass) sb.strokePolylineTapered(_pvX, _pvY, 3, 0.46f * s + ink, 0.38f * s + ink, 0.18f, 0.11f, 0.04f, 0.92f * a, 0.92f * a)
      else sb.strokePolylineTapered(_pvX, _pvY, 3, 0.46f * s, 0.38f * s, 1f, 0.8f, 0.26f, a, a)
      // The toes, the hind one first, each from the ankle by a knuckle to its end
      var t = 3
      while (t >= 0) {
        val ex = mix(TOE_SHUT_X(t), TOE_OPEN_X(t), open); val ey = mix(TOE_SHUT_Y(t), TOE_OPEN_Y(t), open)
        val mx = mix(FOOT_ANKLE_X, ex, 0.55f); val my = mix(FOOT_ANKLE_Y, ey, 0.55f) - 0.06f
        _pvX(0) = tx(FOOT_ANKLE_X, FOOT_ANKLE_Y); _pvY(0) = ty(FOOT_ANKLE_X, FOOT_ANKLE_Y)
        _pvX(1) = tx(mx, my); _pvY(1) = ty(mx, my)
        _pvX(2) = tx(ex, ey); _pvY(2) = ty(ex, ey)
        val w0 = (if (t == 3) 0.36f else 0.32f) * s; val w1 = 0.22f * s
        if (inkPass) sb.strokePolylineTapered(_pvX, _pvY, 3, w0 + ink, w1 + ink, 0.18f, 0.11f, 0.04f, 0.92f * a, 0.92f * a)
        else sb.strokePolylineTapered(_pvX, _pvY, 3, w0, w1, 1f, 0.8f, 0.26f, a, a)
        // Its talon
        val dl = Math.max(1e-3f, Math.sqrt((ex - mx) * (ex - mx) + (ey - my) * (ey - my)).toFloat)
        talonPts(t, ex, ey, (ex - mx) / dl, (ey - my) / dl, if (t == 3) 0.72f else 0.62f)
        if (inkPass) sb.strokePolylineTapered(_pvX, _pvY, 4, 0.27f * s + ink, ink * 0.7f, 0.05f, 0.04f, 0.06f, 0.95f * a, 0.95f * a)
        else {
          sb.strokePolylineTapered(_pvX, _pvY, 4, 0.27f * s, 0.6f, 0.1f, 0.08f, 0.11f, a, a)
          // A glint along its back
          sb.strokeLine(_pvX(0), _pvY(0), _pvX(1), _pvY(1), 0.05f * s + 0.4f, 0.62f, 0.62f, 0.7f, 0.7f * a)
        }
        t -= 1
      }
      pass += 1
    }
    // Scales across the leg and the toes, and the knuckles
    var b = 0
    while (b < 3) {
      val u = 0.28f + b * 0.24f
      val cx = mix(FOOT_KNEE_X, FOOT_ANKLE_X, u); val cy = mix(FOOT_KNEE_Y, FOOT_ANKLE_Y, u)
      // Across the leg: its direction is (0.9, 0.85) in the frame, so across it is (-0.85, 0.9)
      sb.strokeLine(tx(cx - 0.15f, cy + 0.16f), ty(cx - 0.15f, cy + 0.16f), tx(cx + 0.15f, cy - 0.16f), ty(cx + 0.15f, cy - 0.16f),
        0.05f * s + 0.5f, 0.66f, 0.42f, 0.08f, 0.85f * a)
      b += 1
    }
    var t = 0
    while (t < 3) {
      val ex = mix(TOE_SHUT_X(t), TOE_OPEN_X(t), open); val ey = mix(TOE_SHUT_Y(t), TOE_OPEN_Y(t), open)
      val kx = mix(FOOT_ANKLE_X, ex, 0.55f); val ky = mix(FOOT_ANKLE_Y, ey, 0.55f) - 0.06f
      sb.fillOval(tx(kx, ky), ty(kx, ky), 0.1f * s, 0.09f * s, 0.7f, 0.46f, 0.1f, 0.7f * a, 8)
      t += 1
    }
    // Lit from the upper left: a sheen down the leg
    sb.strokeLine(tx(-1.68f, -1.04f), ty(-1.68f, -1.04f), tx(-1.0f, -0.38f), ty(-1.0f, -0.38f), 0.1f * s, 1f, 0.95f, 0.7f, 0.6f * a)
  }

  /** Where the thigh of a bird's foot striking on (sx, sy) is, into (_ptX, _ptY): a leash ties on there. */
  private[projectiles] def birdFootThigh(sx: Float, sy: Float, ndx: Float, ndy: Float, s: Float): Unit = {
    val flip = if (ndx < -0.05f) -1f else 1f
    val fx = ndx * s; val fy = ndy * s
    val qx = -ndy * s * flip; val qy = ndx * s * flip
    val ox = sx - (0.45f * fx + 0.18f * qx); val oy = sy - (0.45f * fy + 0.18f * qy)
    _ptX = ox - 2.2f * fx - 1.25f * qx; _ptY = oy - 2.2f * fy - 1.25f * qy
  }

  /**
   * A bird of prey's kick: its foot struck out side on, talons first onto the hitbox, wide open as it
   * leaves and clenching shut as it reaches the end of its strike, with the rush of air streaming off
   * it and a feather or two shaken loose. The Raptor's in its brown, the Hawk's in its slate blue and
   * the Griffin's in its gold. It used to be a brown ball with three white sticks springing out of it,
   * red at their ends: a matchstick, not a foot.
   */
  private[projectiles] def talonKick(fr: Float, fg: Float, fb: Float, lr: Float, lg: Float, lb: Float,
                                     gr: Float, gg: Float, gb: Float): GLProjectileRenderers.Renderer =
    (proj, sx, sy, sb, tick) => {
      val phase = (tick + proj.id * 29) * 0.4
      computeAllDynamics(proj, gr, gg, gb, phase)
      val a = dynAlpha
      val ds = Math.min(dynScale, 1.3f)
      screenDir(proj)
      val ndx = _sdx; val ndy = _sdy
      val px = -ndy; val py = ndx
      val s = 11.5f * ds
      // Struck out wide, clenching shut over the last of the strike
      val shut = clampF((_lifePct - 0.45f) / 0.45f, 0f, 1f)
      val open = 1f - shut * shut * (3f - 2f * shut)

      sb.fillOvalSoft(sx - ndx * 14f * ds, sy - ndy * 14f * ds, 34f * ds * dynGlow, 24f * ds * dynGlow,
        gr, gg, gb, 0.26f * a, 0f, 16)
      // The rush of air off the foot: three streaks from its toes and its heel
      var k = 0
      while (k < 3) {
        val off = (k - 1) * 9f * ds
        val x0 = sx - ndx * (8f + k * 6f) * ds + px * off; val y0 = sy - ndy * (8f + k * 6f) * ds + py * off
        fadeLine(sb, x0, y0, x0 - ndx * (34f + k * 8f) * ds, y0 - ndy * (34f + k * 8f) * ds, (3.2f - k * 0.5f) * ds,
          mix(gr, 1f, 0.7f), mix(gg, 1f, 0.7f), mix(gb, 1f, 0.7f), 0.75f * a, 5)
        k += 1
      }
      // A feather or two shaken loose, tumbling back
      var f = 0
      while (f < 2) {
        val t = Glyphs.loop(tick, 0.045, f * 0.5 + proj.id * 0.31)
        val lat = (f * 2 - 1) * (6f + 10f * t) * ds
        val fxx = sx - ndx * (20f + t * 36f) * ds + px * lat; val fyy = sy - ndy * (20f + t * 36f) * ds + py * lat + t * t * 8f * ds
        val rot = phase * 0.9 + f * 2.1
        val c = Math.cos(rot).toFloat * 5f * ds; val sn = Math.sin(rot).toFloat * 5f * ds
        _polyXs4(0) = fxx + c; _polyYs4(0) = fyy + sn * 0.6f
        _polyXs4(1) = fxx - sn * 0.35f; _polyYs4(1) = fyy + c * 0.21f
        _polyXs4(2) = fxx - c; _polyYs4(2) = fyy - sn * 0.6f
        _polyXs4(3) = fxx + sn * 0.35f; _polyYs4(3) = fyy - c * 0.21f
        val fa = (1f - t) * a
        sb.strokePolygon(_polyXs4, _polyYs4, 4, 1.4f, 0.12f, 0.07f, 0.03f, 0.8f * fa)
        sb.fillPolygon(_polyXs4, _polyYs4, 4, mix(fr, lr, 0.5f), mix(fg, lg, 0.5f), mix(fb, lb, 0.5f), fa)
        f += 1
      }
      birdFoot(sb, sx, sy, ndx, ndy, s, open, fr, fg, fb, lr, lg, lb, a)
      drawChargeCrackle(sx, sy, 20f * ds, gr, gg, gb, a, sb, phase, proj.chargeLevel)
    }


  // A side-on frame for a bite or a sting, in the foot's frame (tx/ty): its local origin `o`, +x the
  // way it flies, +y down its side, turned over when it flies left so its top stays up
  private def sideFrame(ox: Float, oy: Float, ndx: Float, ndy: Float, s: Float): Unit = {
    val flip = if (ndx < -0.05f) -1f else 1f
    _tfx = ndx * s; _tfy = ndy * s
    _tqx = -ndy * s * flip; _tqy = ndx * s * flip
    _tox = ox; _toy = oy
  }

  private val _bx = new Array[Float](24)
  private val _by = new Array[Float](24)

  /** An ellipse in the side frame, tilted by `tilt` in it, as `n` points into _bx/_by (convex). */
  private def sideOval(cx: Float, cy: Float, rx: Float, ry: Float, tilt: Float, n: Int): Int = {
    val ct = Math.cos(tilt).toFloat; val st = Math.sin(tilt).toFloat
    var i = 0
    while (i < n) {
      val an = i * (2 * Math.PI / n)
      val ex = Math.cos(an).toFloat * rx; val ey = Math.sin(an).toFloat * ry
      val lx = cx + ex * ct - ey * st; val ly = cy + ex * st + ey * ct
      _bx(i) = tx(lx, ly); _by(i) = ty(lx, ly)
      i += 1
    }
    n
  }

  /**
   * One of the Vampire's jaws, side on, swung `open` radians off its hinge at (-1, 0): the gum a
   * crescent (`upper` above the mouth, else below it), its teeth along the gum — two long fangs at the
   * front of the upper one — into _bx/_by as the gum's outer edge then its inner edge back.
   */
  private def vampJawPts(upper: Boolean, open: Float): Int = {
    val sgn = if (upper) -1f else 1f
    val c = Math.cos(open).toFloat; val sn = Math.sin(open).toFloat * sgn
    var i = 0
    while (i < 7) {
      val u = i / 6f
      // Outer edge: back of the jaw round over to the front; inner edge: the gum line
      val ox = -1.05f + u * 1.9f; val oy = sgn * (0.07f + 0.6f * Math.sin(u * Math.PI).toFloat * (1.1f - 0.25f * u))
      val ix = -0.95f + u * 1.8f; val iy = sgn * (0.05f + 0.16f * Math.sin(u * Math.PI).toFloat)
      // Swung about the hinge
      val hx = ox + 1f; val hy = oy
      _bx(i) = tx(-1f + hx * c - hy * sn, hx * sn + hy * c); _by(i) = ty(-1f + hx * c - hy * sn, hx * sn + hy * c)
      val jx = ix + 1f; val jy = iy
      _bx(13 - i) = tx(-1f + jx * c - jy * sn, jx * sn + jy * c); _by(13 - i) = ty(-1f + jx * c - jy * sn, jx * sn + jy * c)
      i += 1
    }
    14
  }

  /** A tooth on a jaw swung `open`: its root `u` of the way along the gum, `len` long, pointing into
    * the mouth. Into _polyXs3/_polyYs3. */
  private def vampTooth(upper: Boolean, open: Float, u: Float, len: Float, w: Float): Unit = {
    val sgn = if (upper) -1f else 1f
    val c = Math.cos(open).toFloat; val sn = Math.sin(open).toFloat * sgn
    val rx = -0.95f + u * 1.8f; val ry = sgn * (0.05f + 0.16f * Math.sin(u * Math.PI).toFloat)
    def put(i: Int, lx: Float, ly: Float): Unit = {
      val hx = lx + 1f; val hy = ly
      val wx = -1f + hx * c - hy * sn; val wy = hx * sn + hy * c
      _polyXs3(i) = tx(wx, wy); _polyYs3(i) = ty(wx, wy)
    }
    put(0, rx - w, ry); put(1, rx + w, ry); put(2, rx + w * 0.3f, ry - sgn * len)
  }

  /**
   * The Vampire's bite: its jaws side on, snapping shut on the hitbox as the bite lands — crimson gums,
   * two long fangs, a dark mouth — with blood flicked off behind it. It used to be a red ball with two
   * white fangs springing off it: a drawing pin.
   */
  private[projectiles] def drawBloodFang(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 37) * 0.35
    computeAllDynamics(proj, 0.85f, 0.12f, 0.1f, phase)
    val a = dynAlpha
    val ds = Math.min(dynScale, 1.3f)
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val px = -ndy; val py = ndx
    val s = 12.5f * ds
    // Wide as it leaves, snapping shut as it reaches the end of the bite
    val shut = clampF((_lifePct - 0.3f) / 0.6f, 0f, 1f)
    val open = 0.55f * (1f - shut * shut)
    sideFrame(sx - ndx * 0.75f * s, sy - ndy * 0.75f * s, ndx, ndy, s)

    sb.fillOvalSoft(sx - ndx * 6f * ds, sy - ndy * 6f * ds, 2.6f * s * dynGlow, 2f * s * dynGlow, 0.8f, 0.05f, 0.08f,
      0.3f * a, 0f, 16)
    // Blood flicked off behind it
    var i = 0
    while (i < 6) {
      val t = Glyphs.loop(tick, 0.05, i / 6.0 + proj.id * 0.13)
      val lat = hash2(proj.id + 7, i) * 0.9f * s
      val bx = sx - ndx * (0.9f * s + t * 3f * s) + px * lat
      val by = sy - ndy * (0.9f * s + t * 3f * s) + py * lat + t * t * s
      val rr = (0.14f + (1f - t) * 0.12f) * s
      sb.fillOval(bx, by, rr + 0.8f, rr * 1.35f + 0.8f, 0.2f, 0.01f, 0.02f, 0.75f * (1f - t) * a, 8)
      sb.fillOval(bx, by, rr, rr * 1.35f, 0.78f, 0.04f, 0.06f, (1f - t) * a, 8)
      i += 1
    }
    // The dark of the mouth between the jaws
    var n = vampJawPts(upper = true, open)
    var q = 0
    while (q < 7) { _gx2(q) = _bx(13 - q); _gy2(q) = _by(13 - q); q += 1 }
    n = vampJawPts(upper = false, open)
    q = 0
    while (q < 7) { _gx2(13 - q) = _bx(13 - q); _gy2(13 - q) = _by(13 - q); q += 1 }
    sb.fillRibbon(_gx2, _gy2, 14, 0.22f, 0.01f, 0.04f, 0.95f * a)
    // Teeth, then the gums over their roots, inked
    var jaw = 0
    while (jaw < 2) {
      val upper = jaw == 0
      var k = 0
      while (k < 5) {
        val u = 0.2f + k * 0.15f
        val fang = upper && (k == 3 || k == 4)
        vampTooth(upper, open, u, if (fang) 0.62f else 0.2f, if (fang) 0.085f else 0.06f)
        sb.strokePolygon(_polyXs3, _polyYs3, 3, 1.6f, 0.14f, 0.02f, 0.04f, 0.9f * a)
        sb.fillPolygon(_polyXs3, _polyYs3, 3, 0.98f, 0.96f, 0.9f, a)
        k += 1
      }
      n = vampJawPts(upper, open)
      sb.strokePolygon(_bx, _by, n, 0.16f * s + 1.2f, 0.12f, 0.01f, 0.03f, 0.95f * a)
      sb.fillRibbon(_bx, _by, n, 0.78f, 0.07f, 0.12f, a)
      // A lit edge along its top
      q = 0
      while (q < 7) { _gx2(q) = _bx(q); _gy2(q) = _by(q); q += 1 }
      sb.strokePolylineTapered(_gx2, _gy2, 7, 0.08f * s, 0.04f * s, 1f, 0.45f, 0.5f, 0.55f * a, 0.2f * a)
      jaw += 1
    }
    drawChargeCrackle(sx, sy, 1.6f * s, 0.85f, 0.12f, 0.1f, a, sb, phase, proj.chargeLevel)
    drawReturnGhosts(sx, sy, 1.6f * s, 0.85f, 0.12f, 0.1f, a, sb, proj)
  }

  private val _gx2 = new Array[Float](16)
  private val _gy2 = new Array[Float](16)

  // The scorpion's tail, side on, from its stinger back: each segment's middle, in the stinger's
  // frame (its point at the origin)
  private val STING_SEG_X = Array(-0.98f, -1.3f, -1.76f, -2.22f, -2.52f)
  private val STING_SEG_Y = Array(-1.02f, -1.42f, -1.6f, -1.42f, -1.0f)

  /**
   * The Scorpion's sting: its tail side on, arched over its back, striking — one armoured length
   * jointed into segments, the venom bulb, and the hooked stinger, its point on the hitbox, venom
   * beading on it. `vr/vg/vb` the
   * venom: green poison (Stinger), or the paralytic's (Paralytic Sting) with sparks crackling off it.
   * It used to run its stinger forward of the hitbox, a stick with a green arrowhead on the end.
   */
  private[projectiles] def scorpionSting(vr: Float, vg: Float, vb: Float, paralytic: Boolean): GLProjectileRenderers.Renderer =
    (proj, sx, sy, sb, tick) => {
      val phase = (tick + proj.id * 31) * 0.4
      computeAllDynamics(proj, vr, vg, vb, phase)
      val a = dynAlpha
      val ds = Math.min(dynScale, 1.3f)
      screenDir(proj)
      val ndx = _sdx; val ndy = _sdy
      val s = 11.5f * ds
      // The strike: the tail curls in a little as it lands
      val curl = 0.1f * Math.sin(Math.min(1f, _lifePct * 1.4f) * Math.PI).toFloat
      sideFrame(sx, sy, ndx, ndy, s)

      sb.fillOvalSoft(tx(-0.9f, -0.6f), ty(-0.9f, -0.6f), 2.6f * s * dynGlow, 2f * s * dynGlow, vr, vg, vb, 0.22f * a, 0f, 16)
      // The speed of it, off the arch of the tail
      var k = 0
      while (k < 2) {
        val lx = STING_SEG_X(3 + k); val ly = STING_SEG_Y(3 + k)
        val x0 = tx(lx, ly); val y0 = ty(lx, ly)
        fadeLine(sb, x0, y0, x0 - ndx * (2.6f + k) * s, y0 - ndy * (2.6f + k) * s, 0.3f * s, 1f, 0.9f, 0.8f, 0.5f * a, 4)
        k += 1
      }
      // The tail: one armoured length from its root at the back, arched over, to the bulb, thick at
      // the root, inked, lit along its top, with a joint across it between each segment
      var i = 0
      while (i < 5) {
        val j = 4 - i
        _pvX(i) = tx(STING_SEG_X(j) + curl * j * 0.25f, STING_SEG_Y(j) + curl * j * 0.12f)
        _pvY(i) = ty(STING_SEG_X(j) + curl * j * 0.25f, STING_SEG_Y(j) + curl * j * 0.12f)
        i += 1
      }
      _pvX(5) = tx(-0.72f, -0.62f); _pvY(5) = ty(-0.72f, -0.62f)
      // Its root fades out behind it, as if out of the Scorpion
      var q = 0
      while (q < 6) { _pvW(q) = (0.66f - 0.16f * q / 5f) * s + 2.4f; _pvA(q) = (if (q == 0) 0f else 0.95f) * a; q += 1 }
      sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, 6, 0.2f, 0.05f, 0.02f)
      q = 0
      while (q < 6) { _pvW(q) -= 2.4f; _pvA(q) = (if (q == 0) 0f else 1f) * a; q += 1 }
      sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, 6, 0.88f, 0.38f, 0.14f)
      q = 0
      while (q < 6) { _gx2(q) = _pvX(q); _gy2(q) = _pvY(q); q += 1 }
      // Its lit top: the line shifted toward the outside of the arch
      q = 0
      while (q < 6) {
        val cx = if (q == 5) -0.72f else STING_SEG_X(4 - q); val cy = if (q == 5) -0.62f else STING_SEG_Y(4 - q)
        // Outward from the arch's middle
        val ox = cx + 1.75f; val oy = cy + 0.75f
        val ol = Math.max(1e-3f, Math.sqrt(ox * ox + oy * oy).toFloat)
        _pvX(q) = tx(cx + ox / ol * 0.15f, cy + oy / ol * 0.15f); _pvY(q) = ty(cx + ox / ol * 0.15f, cy + oy / ol * 0.15f)
        q += 1
      }
      sb.strokePolylineTapered(_pvX, _pvY, 6, 0.2f * s, 0.14f * s, 1f, 0.68f, 0.4f, 0.2f * a, 0.8f * a)
      // The joints, across it halfway between its segments
      q = 1
      while (q < 5) {
        val x0 = _gx2(q); val y0 = _gy2(q); val x1 = _gx2(q + 1); val y1 = _gy2(q + 1)
        val mx = (x0 + x1) * 0.5f; val my = (y0 + y1) * 0.5f
        val dx = x1 - x0; val dy = y1 - y0
        val dl = Math.max(1e-3f, Math.sqrt(dx * dx + dy * dy).toFloat)
        val hw = (0.66f - 0.16f * (q + 0.5f) / 5f) * s * 0.5f
        sb.strokeLine(mx - dy / dl * hw, my + dx / dl * hw, mx + dy / dl * hw, my - dx / dl * hw, 0.07f * s + 0.6f,
          0.4f, 0.1f, 0.04f, 0.9f * a)
        q += 1
      }
      // The stinger, hooked down off the bulb onto the hitbox, under the bulb's front
      _pvX(0) = tx(-0.78f, -0.5f); _pvY(0) = ty(-0.78f, -0.5f)
      _pvX(1) = tx(-0.36f, -0.42f); _pvY(1) = ty(-0.36f, -0.42f)
      _pvX(2) = tx(-0.06f, -0.22f); _pvY(2) = ty(-0.06f, -0.22f)
      _pvX(3) = tx(0.04f, 0.08f); _pvY(3) = ty(0.04f, 0.08f)
      sb.strokePolylineTapered(_pvX, _pvY, 4, 0.46f * s + 2.2f, 1.8f, 0.08f, 0.03f, 0.02f, 0.95f * a, 0.95f * a)
      sb.strokePolylineTapered(_pvX, _pvY, 4, 0.46f * s, 0.5f, 0.16f, 0.07f, 0.06f, a, a)
      sb.strokeLine(_pvX(0), _pvY(0), _pvX(2), _pvY(2), 0.06f * s + 0.5f, 0.55f, 0.45f, 0.42f, 0.8f * a)
      // The venom bulb
      val n = sideOval(-0.72f, -0.62f, 0.44f, 0.33f, -0.55f, 16)
      sb.strokePolygon(_bx, _by, n, 0.11f * s + 1.4f, 0.2f, 0.05f, 0.02f, 0.95f * a)
      sb.fillPolygon(_bx, _by, n, 0.94f, 0.5f, 0.16f, a)
      sb.fillOval(tx(-0.82f, -0.78f), ty(-0.82f, -0.78f), 0.17f * s, 0.1f * s, 1f, 0.86f, 0.6f, 0.85f * a, 8)
      // Venom beading on its point, and dripping
      val bead = 0.5f + 0.5f * Math.sin(phase * 1.3).toFloat
      sb.fillOvalSoft(sx, sy, 0.7f * s, 0.6f * s, vr, vg, vb, 0.55f * a, 0f, 10)
      sb.fillOval(sx, sy + 0.12f * s, (0.1f + 0.06f * bead) * s, (0.13f + 0.08f * bead) * s, vr, vg, vb, a, 8)
      val t = Glyphs.loop(tick, 0.05, proj.id * 0.17)
      sb.fillOval(sx, sy + (0.3f + t * 1.4f) * s, 0.09f * s, 0.12f * s, vr, vg, vb, (1f - t) * a, 6)
      if (paralytic) {
        // Sparks crackling off the stinger: what stops a body dead
        var j = 0
        while (j < 3) {
          val an = phase * 2.1 + j * 2.09
          val ex = sx + Math.cos(an).toFloat * 0.9f * s; val ey = sy + Math.sin(an).toFloat * 0.6f * s
          sparkArc(sb, sx, sy, ex, ey, 3, 0.18f * s, proj.id * 7 + j + (tick / 3), 1.4f, vr, vg, vb, 0.85f * a)
          j += 1
        }
      }
      drawChargeCrackle(sx, sy, 1.6f * s, vr, vg, vb, a, sb, phase, proj.chargeLevel)
    }

  /** Minotaur's thrown horn. Built from the same tapering silhouette the lobbed horn
   *  uses, flying point-first — the previous version stacked two untapered strokes under
   *  a fat base knob, so what reached the screen was a circle on a stick. */
  private[projectiles] def drawHorn(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 37) * 0.3
    computeAllDynamics(proj, 0.80f, 0.74f, 0.58f, phase)
    val p = (0.88f + 0.12f * Math.sin(phase * _stPulseMult).toFloat) * dynAlpha
    val dr = _evoR; val dg = _evoG; val db = _evoB
    val ds = Math.min(dynScale, 1.3f)
    val s = 26f * ds
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy

    drawSpeedLines(sx, sy, ndx, ndy, dr, dg, db, 0.32f * p, sb, 6 + (_lifePct * 2).toInt, s * 1.5f)
    drawRibbonTrail(sx, sy, ndx, ndy, 0.55f, 0.48f, 0.34f, 0.28f * p, sb, tick, proj.id, 8,
      s * 2.0f * dynTrail, s * 0.4f, 1.5f)
    sb.fillOval(sx + 3f, sy + s * 0.5f, s * 0.8f, s * 0.2f, 0f, 0f, 0f, 0.24f * p, 12)
    sb.fillOvalSoft(sx, sy, s * 1.2f * dynGlow, s * 0.95f * dynGlow, dr, dg, db, 0.2f * p, 0f, 16)

    var ghost = 2; while (ghost >= 1) {
      drawPartsDirFlat(sb, HORN_PARTS, sx - ndx * ghost * 10f, sy - ndy * ghost * 10f, ndx, ndy,
        s * (1f - ghost * 0.05f), dr * 0.85f, dg * 0.85f, db * 0.85f, 0.15f * (1f - (ghost - 1) * 0.35f) * p)
      ghost -= 1
    }
    drawPartsDir(sb, HORN_PARTS, sx, sy, ndx, ndy, s, dr, dg, db, 0.97f * dynAlpha,
      clampF(s * 0.12f, 1.5f, 3f))

    // Tip glint, then the dust the charge kicks up
    dirPoint(1.05f, -0.6f, sx, sy, ndx, ndy, s)
    sb.fillStarFlare(_ptX, _ptY, s * 0.4f, 2.2f, (phase * 0.6).toFloat, 0.5f, 1f, 0.98f, 0.88f, 0.6f * p)
    var i = 0; while (i < 8) {
      val t = ((tick * 0.05 + i * 0.125 + proj.id * 0.11) % 1.0).toFloat
      val dustX = sx - ndx * t * s * 1.8f + Math.sin(phase + i * 2.3).toFloat * 7f
      val dustY = sy - ndy * t * s * 1.8f + Math.sin(phase * 0.7 + i * 1.5).toFloat * 5f + t * t * 8f
      val dsz = (5f + t * 11f) * ds
      sb.fillOval(dustX, dustY, dsz, dsz * 0.6f, 0.62f, 0.55f, 0.42f, 0.34f * (1f - t) * p, 8)
      i += 1
    }
    drawChargeCrackle(sx, sy, s * 0.6f, 0.8f, 0.74f, 0.58f, p, sb, phase, proj.chargeLevel)
    drawReturnGhosts(sx, sy, s * 0.5f, dr, dg, db, p, sb, proj)
  }

  // ── Claw marks: the Wolf's, the Bear's, Fenrir's, the Ghoul's, the Shark's, the Mantis's ──
  final val RAKE_SPARKS = 0  // white sparks off the claws
  final val RAKE_BLOOD = 1   // drops of blood, falling
  final val RAKE_ICHOR = 2   // the Ghoul's green ichor, falling
  final val RAKE_SPRAY = 3   // sea spray
  final val RAKE_DUST = 4    // the Bear's dust and fur

  /**
   * Claws raked through the air: `n` slashes, pointed at both ends and widest in the middle, slanting
   * across the way they fly side by side (`///`), each swept out from one end to the other as the swipe
   * begins — slanted, not upright, and only a little curved, or a set of them is `)))`, which says sound — or, `cross`, two sets of them crossed in an X. A white-hot core inside
   * the claw's colour (er, eg, eb) inside its ink, the middle one on the hitbox, with what it tears off
   * streaming back (`bits`: RAKE_*). `jag` tears them ragged (the Ghoul's). `size` scales the whole.
   * It used to be four straight red bars side by side, uniformly thick: a bundle of dynamite.
   */
  private[projectiles] def clawRake(n: Int, cross: Boolean, jag: Float, size: Float,
                                    er: Float, eg: Float, eb: Float, cr: Float, cg: Float, cb: Float,
                                    ir: Float, ig: Float, ib: Float, bits: Int): GLProjectileRenderers.Renderer =
    (proj, sx, sy, sb, tick) => {
      val phase = (tick + proj.id * 29) * 0.4
      computeAllDynamics(proj, er, eg, eb, phase)
      val a = dynAlpha
      val ds = Math.min(dynScale, 1.3f)
      screenDir(proj)
      val ndx = _sdx; val ndy = _sdy
      val px = -ndy; val py = ndx
      val s = size * ds
      // Swept out as the swipe begins, each claw a beat after the one before
      val sweep = 0.4f + _lifePct * 3.2f

      sb.fillOvalSoft(sx - ndx * 4f * ds, sy - ndy * 4f * ds, 2.3f * s * dynGlow, 1.8f * s * dynGlow, er, eg, eb,
        0.24f * a, 0f, 16)
      // What it tears off, streaming back
      var i = 0
      while (i < 6) {
        val t = Glyphs.loop(tick, 0.06, i / 6.0 + proj.id * 0.13)
        val lat = hash2(proj.id + 3, i) * 1.1f * s
        val bx = sx - ndx * (0.6f * s + t * 3.2f * s) + px * lat
        val by = sy - ndy * (0.6f * s + t * 3.2f * s) + py * lat + (if (bits == RAKE_BLOOD || bits == RAKE_ICHOR) t * t * 0.9f * s else 0f)
        val fa = (1f - t) * a
        bits match {
          case RAKE_BLOOD | RAKE_ICHOR =>
            val dr = if (bits == RAKE_BLOOD) 0.72f else 0.42f; val dg = if (bits == RAKE_BLOOD) 0.04f else 0.78f
            val db = if (bits == RAKE_BLOOD) 0.05f else 0.16f
            val rr = (0.16f + (1f - t) * 0.12f) * s
            sb.fillOval(bx, by, rr + 0.8f, rr * 1.3f + 0.8f, dr * 0.3f, dg * 0.3f, db * 0.3f, 0.75f * fa, 8)
            sb.fillOval(bx, by, rr, rr * 1.3f, dr, dg, db, fa, 8)
          case RAKE_SPRAY =>
            val rr = (0.12f + (1f - t) * 0.14f) * s
            sb.fillOval(bx, by, rr + 0.7f, rr + 0.7f, 0.1f, 0.25f, 0.45f, 0.6f * fa, 8)
            sb.fillOval(bx, by, rr, rr, 0.85f, 0.95f, 1f, fa, 8)
          case RAKE_DUST =>
            val rr = (0.2f + t * 0.35f) * s
            sb.fillOval(bx, by, rr, rr * 0.7f, 0.62f, 0.5f, 0.36f, 0.45f * fa, 10)
          case _ =>
            fadeLine(sb, bx, by, bx - ndx * 0.7f * s, by - ndy * 0.7f * s, 0.16f * s, mix(er, 1f, 0.6f),
              mix(eg, 1f, 0.6f), mix(eb, 1f, 0.6f), 0.9f * fa, 3)
        }
        i += 1
      }
      // The claws: ink round all of them, then their colour, then their hot cores
      var pass = 0
      while (pass < 3) {
        var k = 0
        val sets = if (cross) 2 else 1
        while (k < n * sets) {
          val set = k / n; val j = k % n
          val tilt = if (cross) (if (set == 0) 0.95f else -0.95f) else 0.78f
          rakePts(sx, sy, ndx, ndy, s, n, j, tilt, jag, proj.id + k, clampF(sweep - j * 0.28f - set * 0.2f, 0f, 1f),
            if (cross) 1.25f else 1f)
          val m = _rakeN
          if (m >= 2) {
            var q = 0
            while (q < m) {
              val w = _rakeW(q)
              _pvW(q) = pass match { case 0 => w + 2.6f; case 1 => w; case _ => w * 0.38f }
              _pvA(q) = a * (if (pass == 0) 0.92f else 1f)
              q += 1
            }
            pass match {
              case 0 => sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, m, ir, ig, ib)
              case 1 => sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, m, er, eg, eb)
              case _ => sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, m, cr, cg, cb)
            }
          }
          k += 1
        }
        pass += 1
      }
      drawChargeCrackle(sx, sy, 1.6f * s, er, eg, eb, a, sb, phase, proj.chargeLevel)
    }

  // The last claw laid by rakePts: how many points, and each one's width
  private var _rakeN = 0
  private val _rakeW = new Array[Float](12)

  /**
   * Claw `j` of `n` into _pvX/_pvY and _rakeW: a curve `1.1 * len` either side of its middle, at
   * `tilt` radians to the way it flies, bulging forward, the claws stacked one behind another, only the
   * first `shown` of it swept out yet. Pointed at both ends, widest in the middle.
   */
  private def rakePts(sx: Float, sy: Float, ndx: Float, ndy: Float, s: Float, n: Int, j: Int, tilt: Float,
                      jag: Float, seed: Int, shown: Float, len: Float): Unit = {
    _rakeN = 0
    if (shown <= 0.02f) return
    val ct = Math.cos(tilt).toFloat; val st = Math.sin(tilt).toFloat
    // Along the claw and across it (toward the way it flies), on screen
    val cx = ndx * ct - ndy * st; val cy = ndy * ct + ndx * st
    var nx = cy; var ny = -cx
    if (nx * ndx + ny * ndy < 0f) { nx = -nx; ny = -ny }
    val off = (j - (n - 1) * 0.5f) * 0.62f * s
    val mx = sx + nx * off; val my = sy + ny * off
    val pts = 9
    val half = 1.25f * len * s
    var q = 0
    while (q < pts) {
      val u = q / (pts - 1f) * shown
      val along = (u - 0.5f) * 2f * half
      val bulge = Math.sin(u * Math.PI).toFloat * 0.22f * s
      val tear = if (jag > 0f && q > 0 && q < pts - 1) (if ((q & 1) == 0) jag else -jag) * s * (0.6f + 0.4f * Glyphs.hash01(seed, q)) else 0f
      _pvX(q) = mx + cx * along + nx * (bulge + tear)
      _pvY(q) = my + cy * along + ny * (bulge + tear)
      _rakeW(q) = Math.pow(Math.max(0f, Math.sin(u * Math.PI).toFloat), 0.7).toFloat * 0.34f * s * (if (len > 1f) 1.15f else 1f)
      q += 1
    }
    _rakeN = pts
  }


  // ── The Shark, lunging (its jaw drag, Tethers) ──
  // Side on, +x its snout and +y down its flank, in units of about a third of its length: a body
  // that tapers back into the water, the snout over a gaping jaw, the dorsal fin and a pectoral fin
  private val SHARK_SPINE_X = Array(0.05f, -0.6f, -1.3f, -2.0f, -2.6f)
  private val SHARK_SPINE_Y = Array(-0.3f, -0.34f, -0.28f, -0.16f, -0.04f)
  private val SHARK_SPINE_W = Array(0.95f, 1.08f, 0.92f, 0.62f, 0.34f)
  private val SHARK_SNOUT = Array(0.38f, -0.28f, 0.22f, -0.62f, -0.25f, -0.82f, -0.62f, -0.72f, -0.66f, -0.04f, 0.12f, -0.06f)
  private val SHARK_DORSAL = Array(-0.8f, -0.74f, -1.62f, -1.5f, -1.46f, -0.66f)
  private val SHARK_FIN = Array(-0.62f, 0.12f, -1.28f, 0.66f, -1.14f, 0.08f)

  /** The shark's frame for a strike on (sx, sy), flying along (ndx, ndy): its jaws on the hitbox. */
  private def sharkFrame(sx: Float, sy: Float, ndx: Float, ndy: Float, s: Float): Unit =
    sideFrame(sx - ndx * 0.3f * s, sy - ndy * 0.3f * s, ndx, ndy, s)

  /** Where the Shark's body sinks into the water behind it, into (_ptX, _ptY): its surge ties on there. */
  private[projectiles] def sharkTail(sx: Float, sy: Float, ndx: Float, ndy: Float, s: Float): Unit = {
    sharkFrame(sx, sy, ndx, ndy, s)
    _ptX = tx(-2.3f, -0.08f); _ptY = ty(-2.3f, -0.08f)
  }

  /** Local points through the side frame into _bx/_by. */
  private def sidePts(local: Array[Float]): Int = {
    val n = local.length / 2
    var i = 0
    while (i < n) { _bx(i) = tx(local(i * 2), local(i * 2 + 1)); _by(i) = ty(local(i * 2), local(i * 2 + 1)); i += 1 }
    n
  }

  /**
   * The shark lunging, side on, its jaws on (sx, sy): its slate-blue back over a white belly, its snout
   * over a jaw gaping `open` (1 wide, 0 snapped shut) with rows of white teeth in a red mouth, its black
   * eye, its dorsal fin up and a pectoral fin out, its body tapering back into the water.
   */
  private[projectiles] def sharkHead(sb: ShapeBatch, sx: Float, sy: Float, ndx: Float, ndy: Float, s: Float,
                                     open: Float, a: Float, phase: Double): Unit = {
    sharkFrame(sx, sy, ndx, ndy, s)
    val gape = 0.12f + 0.5f * open
    val gc = Math.cos(gape).toFloat; val gs = Math.sin(gape).toFloat
    // The lower jaw, swung down about its hinge at (-0.62, -0.04)
    @inline def jawX(lx: Float, ly: Float): Float = { val hx = lx + 0.62f; val hy = ly + 0.04f; tx(-0.62f + hx * gc - hy * gs, -0.04f + hx * gs + hy * gc) }
    @inline def jawY(lx: Float, ly: Float): Float = { val hx = lx + 0.62f; val hy = ly + 0.04f; ty(-0.62f + hx * gc - hy * gs, -0.04f + hx * gs + hy * gc) }
    val inkR = 0.03f; val inkG = 0.08f; val inkB = 0.18f
    // A splash thrown up round its jaws
    sb.fillOvalSoft(sx, sy, 1.4f * s, 1.1f * s, 0.7f, 0.88f, 1f, 0.3f * a, 0f, 14)
    // Ink round everything: the fins, the body, the snout and the jaw
    var pass = 0
    while (pass < 2) {
      val ink = pass == 0
      var n = sidePts(SHARK_DORSAL)
      if (ink) sb.strokePolygon(_bx, _by, n, 2.6f, inkR, inkG, inkB, 0.95f * a)
      else sb.fillPolygon(_bx, _by, n, 0.32f, 0.44f, 0.62f, a)
      n = sidePts(SHARK_FIN)
      if (ink) sb.strokePolygon(_bx, _by, n, 2.6f, inkR, inkG, inkB, 0.95f * a)
      else sb.fillPolygon(_bx, _by, n, 0.34f, 0.46f, 0.64f, a)
      var i = 0
      while (i < 5) {
        _pvX(i) = tx(SHARK_SPINE_X(i), SHARK_SPINE_Y(i)); _pvY(i) = ty(SHARK_SPINE_X(i), SHARK_SPINE_Y(i))
        _pvW(i) = SHARK_SPINE_W(i) * s + (if (ink) 2.6f else 0f)
        _pvA(i) = (if (i == 4) 0.2f else if (ink) 0.95f else 1f) * a
        i += 1
      }
      if (ink) sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, 5, inkR, inkG, inkB)
      else sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, 5, 0.4f, 0.53f, 0.72f)
      // The lower jaw
      _bx(0) = jawX(-0.62f, -0.02f); _by(0) = jawY(-0.62f, -0.02f)
      _bx(1) = jawX(0.18f, 0.0f); _by(1) = jawY(0.18f, 0.0f)
      _bx(2) = jawX(0.1f, 0.26f); _by(2) = jawY(0.1f, 0.26f)
      _bx(3) = jawX(-0.52f, 0.36f); _by(3) = jawY(-0.52f, 0.36f)
      if (ink) sb.strokePolygon(_bx, _by, 4, 2.6f, inkR, inkG, inkB, 0.95f * a)
      else sb.fillPolygon(_bx, _by, 4, 0.94f, 0.96f, 1f, a)
      n = sidePts(SHARK_SNOUT)
      if (ink) sb.strokePolygon(_bx, _by, n, 2.6f, inkR, inkG, inkB, 0.95f * a)
      else sb.fillPolygon(_bx, _by, n, 0.4f, 0.53f, 0.72f, a)
      pass += 1
    }
    // Its white belly, down the body under the snout
    var i = 0
    while (i < 5) {
      _pvX(i) = tx(SHARK_SPINE_X(i), SHARK_SPINE_Y(i) + SHARK_SPINE_W(i) * 0.26f)
      _pvY(i) = ty(SHARK_SPINE_X(i), SHARK_SPINE_Y(i) + SHARK_SPINE_W(i) * 0.26f)
      _pvW(i) = SHARK_SPINE_W(i) * 0.46f * s; _pvA(i) = (if (i == 4) 0.2f else 1f) * a
      i += 1
    }
    sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, 5, 0.94f, 0.96f, 1f)
    // The mouth: red inside, between the snout's underside and the jaw
    _bx(0) = tx(-0.6f, -0.04f); _by(0) = ty(-0.6f, -0.04f)
    _bx(1) = tx(0.14f, -0.08f); _by(1) = ty(0.14f, -0.08f)
    _bx(2) = jawX(0.16f, 0.02f); _by(2) = jawY(0.16f, 0.02f)
    _bx(3) = jawX(-0.6f, 0.0f); _by(3) = jawY(-0.6f, 0.0f)
    sb.fillPolygon(_bx, _by, 4, 0.55f, 0.06f, 0.1f, a)
    // Teeth: down from the snout and up from the jaw
    var t = 0
    while (t < 5) {
      val u = -0.5f + t * 0.15f
      _polyXs3(0) = tx(u - 0.05f, -0.06f); _polyYs3(0) = ty(u - 0.05f, -0.06f)
      _polyXs3(1) = tx(u + 0.05f, -0.06f); _polyYs3(1) = ty(u + 0.05f, -0.06f)
      _polyXs3(2) = tx(u + 0.01f, 0.1f); _polyYs3(2) = ty(u + 0.01f, 0.1f)
      sb.fillPolygon(_polyXs3, _polyYs3, 3, 1f, 1f, 0.97f, a)
      _polyXs3(0) = jawX(u - 0.05f, 0.02f); _polyYs3(0) = jawY(u - 0.05f, 0.02f)
      _polyXs3(1) = jawX(u + 0.05f, 0.02f); _polyYs3(1) = jawY(u + 0.05f, 0.02f)
      _polyXs3(2) = jawX(u + 0.01f, -0.12f); _polyYs3(2) = jawY(u + 0.01f, -0.12f)
      sb.fillPolygon(_polyXs3, _polyYs3, 3, 1f, 1f, 0.97f, a)
      t += 1
    }
    // Gills, the eye, and the light along its back
    var g = 0
    while (g < 3) {
      val gx = -0.86f - g * 0.13f
      sb.strokeLine(tx(gx, -0.42f), ty(gx, -0.42f), tx(gx - 0.05f, -0.06f), ty(gx - 0.05f, -0.06f), 0.05f * s + 0.4f,
        0.16f, 0.24f, 0.4f, 0.85f * a)
      g += 1
    }
    sb.fillOval(tx(-0.22f, -0.44f), ty(-0.22f, -0.44f), 0.12f * s, 0.11f * s, 0.02f, 0.03f, 0.06f, a, 10)
    sb.fillOval(tx(-0.25f, -0.47f), ty(-0.25f, -0.47f), 0.04f * s, 0.035f * s, 1f, 1f, 1f, 0.9f * a, 6)
    sb.strokeLine(tx(-0.1f, -0.72f), ty(-0.1f, -0.72f), tx(-1.3f, -0.74f), ty(-1.3f, -0.74f), 0.07f * s, 0.62f, 0.74f, 0.9f, 0.7f * a)
  }

  // ── The Wolf ──
  // A wolf's head in profile, +x its nose, +y down its face, in units of about half its length:
  // skull, snout, the lower jaw on its hinge, an ear, and tufts of mane streaming back. Parts convex.
  private val WOLF_SKULL_X = Array(0.42f, -0.08f, -0.55f, -0.72f, -0.55f, 0.1f, 0.42f)
  private val WOLF_SKULL_Y = Array(-0.44f, -0.6f, -0.5f, -0.1f, 0.3f, 0.36f, 0.12f)
  private val WOLF_SNOUT_X = Array(1.3f, 1.08f, 0.35f, 0.2f, 0.9f)
  private val WOLF_SNOUT_Y = Array(-0.06f, -0.3f, -0.44f, -0.1f, 0.04f)
  private val WOLF_JAW_X = Array(0.22f, 1.02f, 0.96f, 0.26f)
  private val WOLF_JAW_Y = Array(0.02f, 0.1f, 0.24f, 0.3f)
  private val WOLF_EAR_X = Array(-0.1f, -0.34f, -0.58f)
  private val WOLF_EAR_Y = Array(-0.52f, -1.12f, -0.46f)
  private val WOLF_MANE_X = Array(-0.45f, -1.35f, -0.7f, -0.62f, -1.55f, -0.66f, -0.5f, -1.25f, -0.2f)
  private val WOLF_MANE_Y = Array(-0.48f, -0.78f, -0.12f, -0.14f, 0.02f, 0.26f, 0.22f, 0.62f, 0.34f)
  private final val WOLF_HINGE_X = 0.22f
  private final val WOLF_HINGE_Y = 0.08f

  private val _wlX = new Array[Float](8)
  private val _wlY = new Array[Float](8)
  // The wolf's frame: its local origin on screen, along it and across it (flipped when it runs
  // left, so its ear stays up), and how far its jaw has dropped
  private var _wox = 0f; private var _woy = 0f
  private var _wfx = 1f; private var _wfy = 0f; private var _wqx = 0f; private var _wqy = 1f
  private var _wjc = 1f; private var _wjs = 0f

  /** A part of the wolf into _wlX/_wlY: points `from` to `from + n` of (xs, ys), swung about the
    * jaw's hinge when `jaw`. */
  private def wolfPts(xs: Array[Float], ys: Array[Float], from: Int, n: Int, jaw: Boolean): Unit = {
    var i = 0
    while (i < n) {
      var lx = xs(from + i); var ly = ys(from + i)
      if (jaw) {
        val hx = lx - WOLF_HINGE_X; val hy = ly - WOLF_HINGE_Y
        lx = WOLF_HINGE_X + hx * _wjc - hy * _wjs; ly = WOLF_HINGE_Y + hx * _wjs + hy * _wjc
      }
      _wlX(i) = _wox + lx * _wfx + ly * _wqx; _wlY(i) = _woy + lx * _wfy + ly * _wqy
      i += 1
    }
  }

  private def wolfPart(sb: ShapeBatch, xs: Array[Float], ys: Array[Float], from: Int, n: Int, jaw: Boolean,
                       ink: Boolean, r: Float, g: Float, b: Float, a: Float): Unit = {
    wolfPts(xs, ys, from, n, jaw)
    if (ink) sb.strokePolygon(_wlX, _wlY, n, 2.4f, 0.07f, 0.09f, 0.24f, 0.85f * a)
    else sb.fillPolygon(_wlX, _wlY, n, r, g, b, a)
  }

  /**
   * The Wolf's momentum strike: the wolf's spirit lunging, a steel-blue head in profile with its jaws
   * opening as it leaps and snapping shut as it reaches the end, its nose on the hitbox, its gold
   * eye fixed ahead, its mane streaming back into wisps of spirit where a body would be.
   */
  private[projectiles] def drawSpiritWolf(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 23) * 0.4
    computeAllDynamics(proj, 0.78f, 0.86f, 1f, phase)
    val a = dynAlpha
    val ds = Math.min(dynScale, 1.3f)
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val flip = if (ndx < -0.05f) -1f else 1f
    val s = 13.5f * ds
    _wox = sx - ndx * 1.3f * s; _woy = sy - ndy * 1.3f * s
    _wfx = ndx * s; _wfy = ndy * s
    _wqx = -ndy * s * 0.9f * flip; _wqy = ndx * s * 0.9f * flip
    // The jaw drops as it leaps and snaps shut as it arrives
    val open = 0.12f + 0.5f * Math.sin(Math.min(1f, _lifePct * 1.15f) * Math.PI).toFloat
    _wjc = Math.cos(open).toFloat; _wjs = Math.sin(open).toFloat

    // Wisps of spirit streaming back off the mane, and the speed of the lunge
    var w = 0
    while (w < 3) {
      val mi = w * 3 + 1
      val bx = _wox + WOLF_MANE_X(mi) * _wfx + WOLF_MANE_Y(mi) * _wqx
      val by = _woy + WOLF_MANE_X(mi) * _wfy + WOLF_MANE_Y(mi) * _wqy
      var i = 0
      while (i < 6) {
        val t = i / 5f
        val sway = Math.sin(phase * 1.5 - t * 4.0 + w * 1.9).toFloat * 3.5f * ds * t
        _pvX(i) = bx - ndx * t * 34f * ds - ndy * sway; _pvY(i) = by - ndy * t * 34f * ds + ndx * sway
        _pvW(i) = (6f * (1f - t) + 0.5f) * ds; _pvA(i) = 0.75f * a * (1f - t)
        i += 1
      }
      sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, 6, 0.66f, 0.76f, 0.95f)
      w += 1
    }
    var side = -1
    while (side <= 1) {
      val ox = -ndy * side * 10f * ds; val oy = ndx * side * 10f * ds
      fadeLine(sb, sx - ndx * 14f * ds + ox, sy - ndy * 14f * ds + oy, sx - ndx * 50f * ds + ox, sy - ndy * 50f * ds + oy,
        2.4f * ds, 1f, 1f, 1f, 0.5f * a, 4)
      side += 2
    }
    sb.fillOvalSoft(sx - ndx * 12f * ds, sy - ndy * 12f * ds, 28f * ds * dynGlow, 22f * ds * dynGlow, 0.7f, 0.82f, 1f,
      0.26f * a, 0f, 16)

    // Ink round the whole head, then its parts: mane, ear, skull, jaw, snout
    var pass = 0
    while (pass < 2) {
      val ink = pass == 0
      var m = 0
      while (m < 3) { wolfPart(sb, WOLF_MANE_X, WOLF_MANE_Y, m * 3, 3, jaw = false, ink, 0.9f, 0.95f, 1f, 0.88f * a); m += 1 }
      wolfPart(sb, WOLF_EAR_X, WOLF_EAR_Y, 0, 3, jaw = false, ink, 0.44f, 0.53f, 0.76f, 0.95f * a)
      wolfPart(sb, WOLF_SKULL_X, WOLF_SKULL_Y, 0, 7, jaw = false, ink, 0.58f, 0.68f, 0.9f, 0.95f * a)
      wolfPart(sb, WOLF_JAW_X, WOLF_JAW_Y, 0, 4, jaw = true, ink, 0.66f, 0.75f, 0.94f, 0.95f * a)
      wolfPart(sb, WOLF_SNOUT_X, WOLF_SNOUT_Y, 0, 5, jaw = false, ink, 0.76f, 0.84f, 0.98f, 0.95f * a)
      pass += 1
    }
    // Fangs, top and bottom
    _wlX(0) = 0.98f; _wlY(0) = 0.02f; _wlX(1) = 0.91f; _wlY(1) = 0.2f; _wlX(2) = 0.84f; _wlY(2) = 0.02f
    _wlX(3) = 0.88f; _wlY(3) = 0.1f; _wlX(4) = 0.82f; _wlY(4) = -0.06f; _wlX(5) = 0.76f; _wlY(5) = 0.1f
    var f = 0
    while (f < 2) {
      var i = 0
      while (i < 3) {
        var lx = _wlX(f * 3 + i); var ly = _wlY(f * 3 + i)
        if (f == 1) {
          val hx = lx - WOLF_HINGE_X; val hy = ly - WOLF_HINGE_Y
          lx = WOLF_HINGE_X + hx * _wjc - hy * _wjs; ly = WOLF_HINGE_Y + hx * _wjs + hy * _wjc
        }
        _polyXs3(i) = _wox + lx * _wfx + ly * _wqx; _polyYs3(i) = _woy + lx * _wfy + ly * _wqy
        i += 1
      }
      sb.fillPolygon(_polyXs3, _polyYs3, 3, 1f, 1f, 1f, a)
      f += 1
    }
    // The nose, and the eye, gold, fixed ahead
    sb.fillOval(_wox + 1.24f * _wfx - 0.1f * _wqx, _woy + 1.24f * _wfy - 0.1f * _wqy, 2.4f * ds, 2f * ds,
      0.07f, 0.09f, 0.24f, a, 8)
    val ex = _wox + 0.3f * _wfx - 0.28f * _wqx; val ey = _woy + 0.3f * _wfy - 0.28f * _wqy
    sb.fillOval(ex, ey, 3f * ds, 2.3f * ds, 0.07f, 0.09f, 0.24f, 0.9f * a, 10)
    sb.fillOval(ex, ey, 2.2f * ds, 1.6f * ds, 1f, 0.82f, 0.22f, a, 10)
    sb.fillOval(ex + ndx * 0.5f * ds, ey + ndy * 0.5f * ds, 0.9f * ds, 1.3f * ds, 0.1f, 0.05f, 0.02f, a, 8)
  }
}
