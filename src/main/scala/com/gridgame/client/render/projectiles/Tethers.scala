package com.gridgame.client.render.projectiles

import com.gridgame.common.model.{Projectile, ProjectileDef, ProjectileType}
import com.gridgame.client.gl.ShapeBatch
import GLProjectileRenderers.Renderer
import ProjectileKit._
import Silhouettes._

/**
 * A pull is tied to whoever is pulling (tether): tentacles, vines, spirit leashes to a paw or a talon,
 * a spectral chain to a sickle hook. The tether runs from the hitbox back to the thrower where the
 * renderer says they stand (setAnchor), or to where the projectile started, bowed and swaying, cut
 * off at the projectile's range.
 */
private[render] object Tethers {
  private var _anchorOn = false
  private var _anchorWX = 0f
  private var _anchorWY = 0f

  /** Where the thrower of the next projectile drawn is standing, in world units. */
  def setAnchor(wx: Float, wy: Float): Unit = { _anchorOn = true; _anchorWX = wx; _anchorWY = wy }

  def clearAnchor(): Unit = { _anchorOn = false }

  /** Whether a type is drawn tied back to its thrower. The caller looks the thrower up only then. */
  def wantsAnchor(pType: Byte): Boolean = _tethered(pType & 0xFF)

  private val _tethered: Array[Boolean] = {
    val a = new Array[Boolean](256)
    for (t <- Seq(ProjectileType.TENTACLE, ProjectileType.VINE_WHIP, ProjectileType.GRAB, ProjectileType.TALON_GRAB,
      ProjectileType.DEATH_GRIP, ProjectileType.ROPE, ProjectileType.MEAT_HOOK)) a(t & 0xFF) = true
    a
  }

  final val TETH_TENTACLE = 0
  final val TETH_VINE = 1
  final val TETH_PAW = 2     // a spirit paw on a leash (bear hug, primate grab)
  final val TETH_TALON = 3   // a spirit talon on a leash (the griffin)
  final val TETH_DEATH = 4   // a sickle hook on a spectral chain (death grip)
  private val TETHER_MAX = 40
  private[projectiles] val _tX = new Array[Float](TETHER_MAX)   // points from the head (0) back to the thrower
  private[projectiles] val _tY = new Array[Float](TETHER_MAX)
  private[projectiles] val _tNX = new Array[Float](TETHER_MAX)  // unit normal at each
  private[projectiles] val _tNY = new Array[Float](TETHER_MAX)
  private val _tT = new Array[Float](TETHER_MAX)   // 0 at the head, 1 at the thrower
  private[projectiles] val _tW = new Array[Float](TETHER_MAX)   // the limb's own width at each
  private[projectiles] val _tA = new Array[Float](TETHER_MAX)   // and its alpha
  private[projectiles] var _tN = 0
  private var _tLen = 0f

  /** How high a thrower holds what they throw: about their hands, not their feet. */
  private val TETHER_HAND = 15f

  /**
   * Lay a tether out from the head back to the thrower as `_tN` points about `spacing` apart:
   * bowed to one side by up to `bow` (and an S by a little less), swaying slowly, and hanging
   * `sag` lower in the middle. Returns false when the head is on its thrower. A thrower who has
   * blinked away doesn't drag a tether across the screen after them: it stops at the range the
   * projectile could have flown and fades out there.
   */
  private[projectiles] def layTether(proj: Projectile, sx: Float, sy: Float, phase: Double, spacing: Float, bow: Float,
                        sag: Float): Boolean = {
    val live = _anchorOn
    var dxw = (if (live) _anchorWX else proj.originX) - proj.getX
    var dyw = (if (live) _anchorWY else proj.originY) - proj.getY
    val maxW = ProjectileDef.get(proj.projectileType).maxRange * 1.25f + 2f
    val dw = Math.sqrt(dxw * dxw + dyw * dyw).toFloat
    if (dw > maxW) { dxw *= maxW / dw; dyw *= maxW / dw }
    val vx = (dxw - dyw) * 20f
    val vy = (dxw + dyw) * 10f - (if (live) TETHER_HAND else 0f)
    val len = Math.sqrt(vx * vx + vy * vy).toFloat
    if (len < 4f) { _tN = 0; return false }
    val qx = -vy / len; val qy = vx / len
    val n = Math.max(3, Math.min(TETHER_MAX, (len / spacing).toInt + 1))
    val b1 = Math.min(bow, len * 0.12f) * Math.sin(phase * 0.5 + proj.id * 1.3).toFloat
    val b2 = Math.min(bow, len * 0.12f) * 0.45f * Math.cos(phase * 0.37 + proj.id * 2.1).toFloat
    var i = 0
    while (i < n) {
      val t = i.toFloat / (n - 1)
      val hump = 4f * t * (1f - t)
      val off = b1 * hump + b2 * Math.sin(t * 2.0 * Math.PI).toFloat
      _tX(i) = sx + vx * t + qx * off
      _tY(i) = sy + vy * t + qy * off + sag * hump
      _tT(i) = t
      i += 1
    }
    i = 0
    while (i < n) {
      val a = Math.max(0, i - 1); val c = Math.min(n - 1, i + 1)
      val ex = _tX(c) - _tX(a); val ey = _tY(c) - _tY(a)
      val el = Math.max(1e-4f, Math.sqrt(ex * ex + ey * ey).toFloat)
      _tNX(i) = -ey / el; _tNY(i) = ex / el
      i += 1
    }
    _tN = n; _tLen = len
    true
  }

  /** Fill `_tW`/`_tA` for the laid tether: `w0` wide at the head swelling to `w1` at the thrower
   *  (`swell` < 1 swells early, as a limb does), full alpha fading out over the last `fadeIn` of
   *  it into whoever is holding it. */
  private[projectiles] def shapeTether(w0: Float, w1: Float, swell: Float, a: Float, fadeIn: Float): Unit = {
    var i = 0
    while (i < _tN) {
      val t = _tT(i)
      _tW(i) = w0 + (w1 - w0) * Math.pow(t, swell).toFloat
      _tA(i) = a * (if (t > 1f - fadeIn) Math.max(0f, (1f - t) / fadeIn) else 1f)
      i += 1
    }
  }

  /** Stroke the laid tether: each point's width times `wk` plus `wAdd`, alpha times `ak`, pushed
   *  `shift` of its width along its normal. */
  private def strokeTether(sb: ShapeBatch, wk: Float, wAdd: Float, ak: Float, r: Float, g: Float, b: Float,
                           shift: Float): Unit = {
    var i = 0
    while (i < _tN) {
      _pvW(i) = _tW(i) * wk + wAdd
      _pvA(i) = _tA(i) * ak
      _pvX(i) = _tX(i) + _tNX(i) * shift * _tW(i)
      _pvY(i) = _tY(i) + _tNY(i) * shift * _tW(i)
      i += 1
    }
    sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, _tN, r, g, b)
  }

  /** A point `t` of the way along the laid tether (0 the head, 1 the thrower), into (_ptX, _ptY). */
  private def tetherAt(t: Float): Unit = {
    val f = clampF(t, 0f, 1f) * (_tN - 1)
    val i = Math.min(_tN - 2, f.toInt); val u = f - i
    _ptX = _tX(i) + (_tX(i + 1) - _tX(i)) * u
    _ptY = _tY(i) + (_tY(i + 1) - _tY(i)) * u
  }

  /**
   * The tip of a limb curling round what it has caught: from the head on along (fx, fy), turning
   * by `sweep` on a circle of radius `rad` to the `side` it turns, tapering from `w0`. Ink, body
   * and a lit edge, like the limb it ends.
   */
  private def curlTip(sb: ShapeBatch, sx: Float, sy: Float, fx: Float, fy: Float, side: Float, sweep: Float,
                      rad: Float, w0: Float, r: Float, g: Float, b: Float, ink: Float, a: Float): Unit = {
    val n = 6
    var dx = fx; var dy = fy
    var x = sx; var y = sy
    val turn = sweep / (n - 1) * side
    val step = rad * Math.abs(turn)
    val c = Math.cos(turn).toFloat; val s = Math.sin(turn).toFloat
    var i = 0
    while (i < n) {
      _pvX(i) = x; _pvY(i) = y
      val ndx = dx * c - dy * s; val ndy = dx * s + dy * c
      dx = ndx; dy = ndy
      x += dx * step; y += dy * step * 0.85f
      i += 1
    }
    sb.strokePolylineTapered(_pvX, _pvY, n, w0 + 3f, 2.6f, r * ink, g * ink, b * ink, 0.9f * a, 0.9f * a)
    sb.strokePolylineTapered(_pvX, _pvY, n, w0, 0.9f, r, g, b, a, a)
  }

  /** A tethered grab (see TETHERS above). */
  private[projectiles] def tether(kind: Int, r: Float, g: Float, b: Float): Renderer =
    (proj, sx, sy, sb, tick) => {
      val phase = (tick + proj.id * 23) * 0.35
      computeAllDynamics(proj, r, g, b, phase)
      val p = dynAlpha
      val dr = _evoR; val dg = _evoG; val db = _evoB
      val ds = Math.min(dynScale, 1.3f)
      screenDir(proj)
      val ndx = _sdx; val ndy = _sdy
      val grip = 0.5f + 0.5f * Math.sin(phase * 1.5).toFloat
      kind match {
        case TETH_TENTACLE => tentacleLimb(sb, proj, sx, sy, ndx, ndy, ds, dr, dg, db, p, phase, grip)
        case TETH_VINE => vineLimb(sb, proj, sx, sy, ndx, ndy, ds, p, phase, grip)
        case TETH_DEATH =>
          deathChain(sb, proj, sx, sy, ds, dr, dg, db, p, phase, tick)
          deathHook(sb, sx, sy, ndx, ndy, ds, dr, dg, db, p, phase)
        case _ =>
          spiritLeash(sb, proj, sx, sy, ds, dr, dg, db, p, phase, tick)
          if (kind == TETH_TALON) talonHead(sb, sx, sy, ndx, ndy, ds, dr, dg, db, p, grip)
          else pawHead(sb, sx, sy, ndx, ndy, ds, dr, dg, db, p, grip)
      }
      drawChargeCrackle(sx, sy, 16f * ds, r, g, b, p, sb, phase, proj.chargeLevel)
    }

  /** The Kraken's tentacle: thick where it leaves the Kraken, tapering to a tip that curls round
   *  what it has caught; a dark top, a pale underside with suckers down it, a lit ridge. */
  private def tentacleLimb(sb: ShapeBatch, proj: Projectile, sx: Float, sy: Float, ndx: Float, ndy: Float,
                           ds: Float, r: Float, g: Float, b: Float, p: Float, phase: Double, grip: Float): Unit = {
    val ink = 0.2f
    val belR = mix(r, bright(r), 0.7f); val belG = mix(g, bright(g), 0.55f); val belB = mix(b, bright(b), 0.55f)
    // Which way its underside faces: toward the camera, whichever side of the line that is
    val side = if (-ndx > 0f) 1f else -1f
    if (layTether(proj, sx, sy, phase, 9f * ds, 16f * ds, 0f)) {
      shapeTether(5.4f * ds, 14f * ds, 0.5f, p, 0.14f)
      strokeTether(sb, 1f, 3.2f, 0.9f, r * ink, g * ink, b * ink, 0f)
      strokeTether(sb, 1f, 0f, 1f, r * 0.85f, g * 0.85f, b * 0.85f, 0f)
      strokeTether(sb, 0.36f, 0f, 1f, belR, belG, belB, 0.26f * side)
      strokeTether(sb, 0.2f, 0f, 0.55f, mix(r, 1f, 0.45f), mix(g, 1f, 0.45f), mix(b, 1f, 0.45f), -0.26f * side)
      // Suckers down the underside, largest near the root
      var i = 2
      while (i < _tN - 2) {
        val t = _tT(i)
        if (t < 0.78f) {
          val w = _tW(i)
          val cx = _tX(i) + _tNX(i) * side * w * 0.22f; val cy = _tY(i) + _tNY(i) * side * w * 0.22f
          val rr = w * 0.17f + 0.6f
          sb.fillOval(cx, cy, rr + 0.9f, rr * 0.8f + 0.9f, r * ink, g * ink, b * ink, 0.7f * _tA(i), 8)
          sb.fillOval(cx, cy, rr, rr * 0.8f, mix(belR, 1f, 0.3f), mix(belG, 1f, 0.3f), mix(belB, 1f, 0.3f), _tA(i), 8)
        }
        i += 2
      }
    }
    curlTip(sb, sx, sy, ndx, ndy, side, 2.6f + 1.8f * grip, 8f * ds, 5.6f * ds, r * 0.85f, g * 0.85f, b * 0.85f, ink, p)
  }

  /** A thorned vine: a dark stem with a paler strand wound round it, thorns down both sides raked
   *  back toward the root, a leaf now and then, and a tendril curling shut round what it has caught. */
  private def vineLimb(sb: ShapeBatch, proj: Projectile, sx: Float, sy: Float, ndx: Float, ndy: Float,
                       ds: Float, p: Float, phase: Double, grip: Float): Unit = {
    if (layTether(proj, sx, sy, phase, 8f * ds, 12f * ds, 0f)) {
      shapeTether(4.6f * ds, 10f * ds, 0.6f, p, 0.12f)
      strokeTether(sb, 1f, 3f, 0.9f, 0.08f, 0.15f, 0.05f, 0f)
      strokeTether(sb, 1f, 0f, 1f, 0.28f, 0.52f, 0.17f, 0f)
      // A paler strand winding round it
      var i = 0
      while (i < _tN) {
        val w = _tW(i)
        val wind = Math.sin(_tT(i) * _tLen / 9f + phase * 1.6).toFloat * 0.32f
        _pvX(i) = _tX(i) + _tNX(i) * w * wind; _pvY(i) = _tY(i) + _tNY(i) * w * wind
        _pvW(i) = w * 0.32f; _pvA(i) = _tA(i) * 0.9f
        i += 1
      }
      sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, _tN, 0.5f, 0.76f, 0.3f)
      // Thorns, raked back toward the root, and a leaf every so often on the other side
      i = 2
      var k = 0
      while (i < _tN - 1) {
        val side = if ((k & 1) == 0) 1f else -1f
        val w = _tW(i); val a = _tA(i)
        val tx0 = _tX(i + 1) - _tX(i); val ty0 = _tY(i + 1) - _tY(i)
        val tl = Math.max(1e-3f, Math.sqrt(tx0 * tx0 + ty0 * ty0).toFloat)
        val tx = tx0 / tl; val ty = ty0 / tl
        val nx = _tNX(i) * side; val ny = _tNY(i) * side
        val bx = _tX(i) + nx * w * 0.42f; val by = _tY(i) + ny * w * 0.42f
        _polyXs3(0) = bx - tx * w * 0.38f; _polyYs3(0) = by - ty * w * 0.38f
        _polyXs3(1) = bx + tx * w * 0.38f; _polyYs3(1) = by + ty * w * 0.38f
        _polyXs3(2) = bx + nx * w * 0.95f + tx * w * 0.6f; _polyYs3(2) = by + ny * w * 0.95f + ty * w * 0.6f
        sb.fillPolygon(_polyXs3, _polyYs3, 3, 0.30f, 0.20f, 0.09f, a)
        if (k % 3 == 1 && _tT(i) < 0.85f) {
          // A leaf, angled back along the stem
          val lx = -nx * 0.7f + tx * 0.7f; val ly = -ny * 0.7f + ty * 0.7f
          val lb = Math.sqrt(lx * lx + ly * ly).toFloat
          val dx = lx / lb; val dy = ly / lb
          val L = 8f * ds; val W = 3f * ds
          val ox = _tX(i) - nx * w * 0.35f; val oy = _tY(i) - ny * w * 0.35f
          _polyXs4(0) = ox; _polyYs4(0) = oy
          _polyXs4(1) = ox + dx * L * 0.5f - dy * W; _polyYs4(1) = oy + dy * L * 0.5f + dx * W
          _polyXs4(2) = ox + dx * L; _polyYs4(2) = oy + dy * L
          _polyXs4(3) = ox + dx * L * 0.5f + dy * W; _polyYs4(3) = oy + dy * L * 0.5f - dx * W
          sb.strokePolygon(_polyXs4, _polyYs4, 4, 1.4f, 0.06f, 0.14f, 0.04f, 0.85f * a)
          sb.fillPolygon(_polyXs4, _polyYs4, 4, 0.40f, 0.74f, 0.27f, a)
          sb.strokeLine(ox, oy, ox + dx * L * 0.85f, oy + dy * L * 0.85f, 1f, 0.2f, 0.42f, 0.13f, 0.8f * a)
        }
        i += 3; k += 1
      }
    }
    // The tendril at its tip, curling shut, with a bud where it starts
    curlTip(sb, sx, sy, ndx, ndy, 1f, 3f + 1.5f * grip, 6.5f * ds, 4.6f * ds, 0.34f, 0.6f, 0.2f, 0.28f, p)
    sb.fillOval(sx, sy, 4.6f * ds + 1.5f, 3.8f * ds + 1.5f, 0.08f, 0.15f, 0.05f, 0.9f * p, 10)
    sb.fillOval(sx, sy, 4.6f * ds, 3.8f * ds, 0.52f, 0.78f, 0.3f, p, 10)
  }

  /** A leash of spirit: a band of light out of the thrower with specks of it streaming back along
   *  it toward them, which is the pull. */
  private def spiritLeash(sb: ShapeBatch, proj: Projectile, sx: Float, sy: Float, ds: Float,
                          r: Float, g: Float, b: Float, p: Float, phase: Double, tick: Int): Unit = {
    if (!layTether(proj, sx, sy, phase, 12f * ds, 10f * ds, 0f)) return
    shapeTether(3.4f * ds, 4.6f * ds, 1f, p, 0.16f)
    val lr = mix(bright(r), 1f, 0.3f); val lg = mix(bright(g), 1f, 0.3f); val lb = mix(bright(b), 1f, 0.3f)
    strokeTether(sb, 3.2f, 0f, 0.2f, r, g, b, 0f)
    strokeTether(sb, 1f, 2.6f, 0.75f, outline(r), outline(g), outline(b), 0f)
    strokeTether(sb, 1f, 0f, 0.95f, r, g, b, 0f)
    strokeTether(sb, 0.36f, 0f, 1f, lr, lg, lb, 0f)
    var m = 0
    while (m < 4) {
      val t = ((tick * 0.035 + m * 0.25 + proj.id * 0.3) % 1.0).toFloat
      tetherAt(t)
      val ms = 2.6f * ds * (1f - t * 0.4f)
      sb.fillOvalSoft(_ptX, _ptY, ms * 2f, ms * 1.7f, r, g, b, 0.5f * p * (1f - t), 0f, 8)
      sb.fillOval(_ptX, _ptY, ms * 0.7f, ms * 0.6f, 1f, 1f, 0.95f, 0.9f * p * (1f - t), 6)
      m += 1
    }
  }

  // The spirit paw as a paw print, in its local frame (+x the way it flies): the heel pad, as
  // three lobes inked as one, and four toe beans in an arc ahead of it with a gap round each
  private val PAW_HEEL = Array(-0.28f, 0f, 0.42f,   -0.36f, -0.26f, 0.3f,   -0.36f, 0.26f, 0.3f)
  private val PAW_TOES = Array(0.34f, -0.66f,   0.66f, -0.27f,   0.66f, 0.27f,   0.34f, 0.66f)

  /**
   * Bear hug / primate grab: a paw of spirit at the end of its leash — a paw print, turned to
   * where it flies but not squashed into the ground plane, since squashed its toes ran into one
   * another. Heel pad, four toe beans, dark claws hooking in as it snatches shut. The old one was
   * a brown mitten on a box-shaped cuff, and an empty cuff is the silliest thing a projectile can be.
   */
  private def pawHead(sb: ShapeBatch, sx: Float, sy: Float, ndx: Float, ndy: Float, ds: Float,
                      r: Float, g: Float, b: Float, p: Float, grip: Float): Unit = {
    val s = 17f * ds
    val spread = 1.08f - 0.22f * grip
    val px = -ndy; val py = ndx
    sb.fillOvalSoft(sx, sy, s * 1.6f, s * 1.45f, r, g, b, 0.35f * p, 0f, 14)
    val pr = mix(bright(r), 1f, 0.3f); val pg = mix(bright(g), 1f, 0.3f); val pb = mix(bright(b), 1f, 0.3f)
    // Claws first, so the toe beans sit over their roots
    var t = 0
    while (t < 4) {
      val lx = PAW_TOES(t * 2); val ly = PAW_TOES(t * 2 + 1) * spread
      val cx = sx + (ndx * lx + px * ly) * s; val cy = sy + (ndy * lx + py * ly) * s
      val hook = -Math.signum(ly) * (0.06f + 0.14f * grip)
      val ox = ndx * 0.52f + px * hook; val oy = ndy * 0.52f + py * hook
      _polyXs3(0) = cx + px * 0.1f * s; _polyYs3(0) = cy + py * 0.1f * s
      _polyXs3(1) = cx - px * 0.1f * s; _polyYs3(1) = cy - py * 0.1f * s
      _polyXs3(2) = cx + ox * s; _polyYs3(2) = cy + oy * s
      sb.strokePolygon(_polyXs3, _polyYs3, 3, 1.6f, 0.06f, 0.04f, 0.03f, 0.9f * p)
      sb.fillPolygon(_polyXs3, _polyYs3, 3, 0.22f, 0.15f, 0.1f, p)
      t += 1
    }
    var pass = 0
    while (pass < 2) {
      val grow = if (pass == 0) 1.9f else 0f
      val cr = if (pass == 0) outline(r) else r; val cg = if (pass == 0) outline(g) else g
      val cb = if (pass == 0) outline(b) else b
      val ca = if (pass == 0) 0.92f * p else 0.97f * p
      var h = 0
      while (h < 3) {
        val lx = PAW_HEEL(h * 3); val ly = PAW_HEEL(h * 3 + 1); val rad = PAW_HEEL(h * 3 + 2) * s
        sb.fillOval(sx + (ndx * lx + px * ly) * s, sy + (ndy * lx + py * ly) * s, rad + grow, rad * 0.9f + grow, cr, cg, cb, ca, 16)
        h += 1
      }
      t = 0
      while (t < 4) {
        val lx = PAW_TOES(t * 2); val ly = PAW_TOES(t * 2 + 1) * spread
        val rad = s * (if (t == 0 || t == 3) 0.2f else 0.23f)
        sb.fillOval(sx + (ndx * lx + px * ly) * s, sy + (ndy * lx + py * ly) * s, rad + grow, rad * 0.9f + grow, cr, cg, cb, ca, 12)
        t += 1
      }
      pass += 1
    }
    // Lit from the upper left, as everything is
    sb.fillOvalSoft(sx + (-ndx * 0.28f) * s + KEY_LIGHT_X * s * 0.12f, sy + (-ndy * 0.28f) * s + KEY_LIGHT_Y * s * 0.1f,
      s * 0.36f, s * 0.32f, pr, pg, pb, 0.95f * p, 0f, 12)
    t = 0
    while (t < 4) {
      val lx = PAW_TOES(t * 2); val ly = PAW_TOES(t * 2 + 1) * spread
      sb.fillOval(sx + (ndx * lx + px * ly) * s + KEY_LIGHT_X * s * 0.05f, sy + (ndy * lx + py * ly) * s + KEY_LIGHT_Y * s * 0.05f,
        s * 0.1f, s * 0.08f, pr, pg, pb, 0.9f * p, 8)
      t += 1
    }
  }

  /** The Griffin's talon: a scaled gold foot, three toes forward and one back, black hooked
   *  talons closing as it strikes — an eagle's, which is what the front half of a griffin is. */
  private def talonHead(sb: ShapeBatch, sx: Float, sy: Float, ndx: Float, ndy: Float, ds: Float,
                        r: Float, g: Float, b: Float, p: Float, grip: Float): Unit = {
    val s = 21f * ds
    val spread = 1.15f - 0.4f * grip
    sb.fillOvalSoft(sx, sy, s * 1.6f, s * 1.3f, r, g, b, 0.32f * p, 0f, 14)
    val fr = 0.96f; val fg = 0.78f; val fb = 0.32f
    var pass = 0
    while (pass < 2) {
      val w = if (pass == 0) 0.34f * s + 3f else 0.34f * s
      val cr = if (pass == 0) 0.16f else fr; val cg = if (pass == 0) 0.11f else fg; val cb = if (pass == 0) 0.04f else fb
      var t = 0
      while (t < 4) {
        // Three toes forward, the fourth back
        val ex = if (t == 3) -0.62f else if (t == 1) 0.86f else 0.68f
        val ey = if (t == 3) 0.1f else (t - 1) * 0.46f * spread
        dirPoint(-0.18f, 0f, sx, sy, ndx, ndy, s); _pvX(0) = _ptX; _pvY(0) = _ptY
        dirPoint(ex * 0.55f - 0.08f, ey * 0.6f, sx, sy, ndx, ndy, s); _pvX(1) = _ptX; _pvY(1) = _ptY
        dirPoint(ex, ey, sx, sy, ndx, ndy, s); _pvX(2) = _ptX; _pvY(2) = _ptY
        sb.strokePolylineTapered(_pvX, _pvY, 3, w, w * 0.7f, cr, cg, cb, (if (pass == 0) 0.9f else 1f) * p,
          (if (pass == 0) 0.9f else 1f) * p)
        t += 1
      }
      pass += 1
    }
    dirPoint(-0.18f, 0f, sx, sy, ndx, ndy, s)
    sb.fillOval(_ptX, _ptY, s * 0.26f + 1.5f, s * 0.22f + 1.5f, 0.16f, 0.11f, 0.04f, 0.9f * p, 10)
    sb.fillOval(_ptX, _ptY, s * 0.26f, s * 0.22f, fr, fg, fb, p, 10)
    // Talons: black hooks on every toe, curling in as it closes
    var t = 0
    while (t < 4) {
      val back = t == 3
      val ex = if (back) -0.62f else if (t == 1) 0.86f else 0.68f
      val ey = if (back) 0.1f else (t - 1) * 0.46f * spread
      val dir = if (back) -1f else 1f
      val hook = if (back) 0.12f else -Math.signum(ey + 0.001f) * (0.05f + 0.2f * grip)
      dirPoint(ex, ey - 0.1f, sx, sy, ndx, ndy, s); _polyXs3(0) = _ptX; _polyYs3(0) = _ptY
      dirPoint(ex, ey + 0.1f, sx, sy, ndx, ndy, s); _polyXs3(1) = _ptX; _polyYs3(1) = _ptY
      dirPoint(ex + dir * 0.42f, ey + hook, sx, sy, ndx, ndy, s); _polyXs3(2) = _ptX; _polyYs3(2) = _ptY
      sb.fillPolygon(_polyXs3, _polyYs3, 3, 0.1f, 0.08f, 0.1f, p)
      t += 1
    }
    // Glints along the scales
    dirPoint(0.2f, -0.12f, sx, sy, ndx, ndy, s)
    sb.fillOval(_ptX, _ptY, s * 0.1f, s * 0.07f, 1f, 1f, 0.9f, 0.6f * p, 6)
  }

  /** Death Grip's chain: a band of dark violet with links riding it, and souls drifting back
   *  along it toward the Death Knight — the pull. */
  private def deathChain(sb: ShapeBatch, proj: Projectile, sx: Float, sy: Float, ds: Float,
                         r: Float, g: Float, b: Float, p: Float, phase: Double, tick: Int): Unit = {
    if (!layTether(proj, sx, sy, phase, 6.5f * ds, 8f * ds, 5f * ds)) return
    shapeTether(4f * ds, 4.4f * ds, 1f, p, 0.14f)
    strokeTether(sb, 3f, 0f, 0.22f, r, g, b, 0f)
    strokeTether(sb, 1f, 2f, 0.92f, 0.08f, 0.03f, 0.12f, 0f)
    // Links, alternately lying flat and standing on edge
    var i = 0
    while (i < _tN - 1) {
      val x0 = _tX(i); val y0 = _tY(i); val x1 = _tX(i + 1); val y1 = _tY(i + 1)
      val mx = (x0 + x1) * 0.5f; val my = (y0 + y1) * 0.5f
      val ex = (x1 - x0) * 0.42f; val ey = (y1 - y0) * 0.42f
      val a = _tA(i)
      if ((i & 1) == 0) {
        sb.strokeLine(mx - ex, my - ey, mx + ex, my + ey, 4.2f * ds, 0.55f, 0.46f, 0.7f, 0.95f * a)
        sb.strokeLine(mx - ex * 0.6f, my - ey * 0.6f, mx + ex * 0.6f, my + ey * 0.6f, 1.5f * ds, 0.1f, 0.04f, 0.14f, a)
      } else sb.strokeLine(mx - ex, my - ey, mx + ex, my + ey, 1.8f * ds, mix(r, 1f, 0.4f), mix(g, 1f, 0.4f), mix(b, 1f, 0.4f), a)
      i += 1
    }
    var m = 0
    while (m < 3) {
      val t = ((tick * 0.03 + m / 3.0 + proj.id * 0.21) % 1.0).toFloat
      tetherAt(t)
      val ms = 3f * ds
      sb.fillOvalSoft(_ptX, _ptY - 3f * ds, ms * 2f, ms * 1.8f, r, g, b, 0.55f * p * Math.sin(t * Math.PI).toFloat, 0f, 8)
      sb.fillOval(_ptX, _ptY - 3f * ds, ms * 0.55f, ms * 0.6f, 0.9f, 0.85f, 1f, 0.9f * p * Math.sin(t * Math.PI).toFloat, 6)
      m += 1
    }
  }

  // The death hook in its local frame (+x the way it flies): a shank and a blade curving round
  // from its front and back to a point that faces the thrower, so it catches whoever it reaches
  // as it is pulled back. Five convex spans between two circles, the inner one closing on the
  // outer at the point.
  private[projectiles] val DEATH_HOOK_PARTS: Array[Part] = {
    val cx = 0.2f; val cy = -0.42f; val ro = 0.56f
    val spans = Array(1.57f, 0.8f, 0.0f, -0.8f, -1.6f, -2.45f)
    val inner = Array(0.3f, 0.3f, 0.31f, 0.33f, 0.4f, 0.55f)
    val blade = (0 until 5).map { k =>
      val a0 = spans(k); val a1 = spans(k + 1)
      part(Array(
        cx + Math.cos(a0).toFloat * ro, cy + Math.sin(a0).toFloat * ro,
        cx + Math.cos(a1).toFloat * ro, cy + Math.sin(a1).toFloat * ro,
        cx + Math.cos(a1).toFloat * inner(k + 1), cy + Math.sin(a1).toFloat * inner(k + 1),
        cx + Math.cos(a0).toFloat * inner(k), cy + Math.sin(a0).toFloat * inner(k)),
        DKSTEEL_R, DKSTEEL_G, DKSTEEL_B, 0.4f)
    }
    (part(Array(-0.9f,-0.1f, 0.28f,-0.1f, 0.28f,0.1f, -0.9f,0.1f), 0.24f, 0.22f, 0.28f, 0.25f) +: blade).toArray
  }

  /** Death Grip's head: the sickle hook, glowing along its blade. */
  private def deathHook(sb: ShapeBatch, sx: Float, sy: Float, ndx: Float, ndy: Float, ds: Float,
                        r: Float, g: Float, b: Float, p: Float, phase: Double): Unit = {
    val s = 17f * ds
    dirPoint(0.2f, -0.42f, sx, sy, ndx, ndy, s)
    sb.fillOvalSoft(_ptX, _ptY, s * 1.1f, s * 0.95f, r, g, b, 0.4f * p, 0f, 14)
    drawPartsDirUnion(sb, DEATH_HOOK_PARTS, sx, sy, ndx, ndy, s, r, g, b, p, 2.6f)
    // The edge, lit in the curse's colour
    var k = 0
    while (k < 5) {
      val a = 1.3f - k * 0.72f
      dirPoint(0.2f + Math.cos(a).toFloat * 0.5f, -0.42f + Math.sin(a).toFloat * 0.5f, sx, sy, ndx, ndy, s)
      _pvX(k) = _ptX; _pvY(k) = _ptY
      k += 1
    }
    val glow = 0.75f + 0.25f * Math.sin(phase * 2.2).toFloat
    sb.strokePolylineTapered(_pvX, _pvY, 5, 2.4f * ds, 0.8f, mix(bright(r), 1f, 0.3f), mix(bright(g), 1f, 0.3f),
      mix(bright(b), 1f, 0.3f), glow * p, 0.4f * p)
    // The ring the chain is made fast to
    dirPoint(-0.95f, 0f, sx, sy, ndx, ndy, s)
    sb.strokeOval(_ptX, _ptY, s * 0.16f, s * 0.14f, 2.6f * ds, 0.08f, 0.03f, 0.12f, 0.95f * p, 10)
    sb.strokeOval(_ptX, _ptY, s * 0.16f, s * 0.14f, 1.3f * ds, 0.55f, 0.46f, 0.7f, p, 10)
  }
}
