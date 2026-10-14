package com.gridgame.client.render.projectiles

import com.gridgame.common.model.{Projectile, ProjectileDef, ProjectileType}
import com.gridgame.client.gl.ShapeBatch
import GLProjectileRenderers.Renderer
import ProjectileKit._
import Silhouettes._
import Glyphs._

/**
 * A pull is tied to whoever is pulling (tether): the Kraken's and the Spaceman's tentacles, a vine,
 * the Bear's and the Gorilla's arms reaching out, the Griffin's leash of golden feathers to its
 * talons, a spectral chain to a sickle hook, the Chameleon's tongue, the Mummy's bandage, the Shark
 * lunging on a surge of water. The tether runs from the hitbox back to the thrower where the renderer
 * says they stand (setAnchor), or to where the projectile started, bowed and swaying, cut off at the
 * projectile's range. It is as solid at the end of its reach as anywhere: what a pull does there is
 * be reeled back in (ProjectilePainter.drawReeled, which says where its head has got to, setHead).
 */
private[render] object Tethers {
  private var _anchorOn = false
  private var _anchorWX = 0f
  private var _anchorWY = 0f
  // Where the head is, in world units, when it isn't where the projectile is: reeled back in, and
  // whether it has hold of someone
  private var _headOn = false
  private var _headWX = 0f
  private var _headWY = 0f
  private[projectiles] var _holding = false

  /** Where the thrower of the next projectile drawn is standing, in world units. */
  def setAnchor(wx: Float, wy: Float): Unit = { _anchorOn = true; _anchorWX = wx; _anchorWY = wy }

  def clearAnchor(): Unit = { _anchorOn = false }

  /** Is a thrower's position set for the next projectile drawn? Then it is at (anchorX, anchorY). */
  def hasAnchor: Boolean = _anchorOn
  def anchorX: Float = _anchorWX
  def anchorY: Float = _anchorWY

  /** The head of the next tether drawn is at world (wx, wy), not where its projectile is: it is
    * being reeled back in, empty or `holding` what it caught, its grip shut on them. */
  def setHead(wx: Float, wy: Float, holding: Boolean): Unit = {
    _headOn = true; _headWX = wx; _headWY = wy; _holding = holding
  }

  def clearHead(): Unit = { _headOn = false; _holding = false }

  /** Whether a type is drawn tied back to its thrower. The caller looks the thrower up only then. */
  def wantsAnchor(pType: Byte): Boolean = _tethered(pType & 0xFF)

  private val _tethered: Array[Boolean] = {
    val a = new Array[Boolean](256)
    for (t <- Seq(ProjectileType.TENTACLE, ProjectileType.VINE_WHIP, ProjectileType.GRAB, ProjectileType.TALON_GRAB,
      ProjectileType.DEATH_GRIP, ProjectileType.ROPE, ProjectileType.MEAT_HOOK, ProjectileType.TONGUE,
      ProjectileType.BANDAGE_WHIP, ProjectileType.JAW)) a(t & 0xFF) = true
    a
  }

  final val TETH_TENTACLE = 0  // the Spaceman's alien tentacle
  final val TETH_VINE = 1      // a thorned vine (the Druid's, the Treant's)
  final val TETH_PAW = 2       // the Bear's arm reaching out, its paw open
  final val TETH_TALON = 3     // the Griffin's leash of golden feathers, and its talons
  final val TETH_DEATH = 4     // a sickle hook on a spectral chain (death grip)
  final val TETH_TONGUE = 5    // the Chameleon's tongue, its sticky tip on what it caught
  final val TETH_BANDAGE = 6   // the Mummy's bandage, twisting, wrapped round what it caught
  final val TETH_HAND = 7      // the Gorilla's arm reaching out, its hand open
  final val TETH_KRAKEN = 8    // the Kraken's own tentacle, navy and spotted white
  final val TETH_SHARK = 9     // the Shark lunging on a surge of water
  final val TETH_BLOSSOM = 10  // the Thornweaver's vine, in flower
  private val TETHER_MAX = 40
  private[projectiles] val _tX = new Array[Float](TETHER_MAX)   // points from the head (0) back to the thrower
  private[projectiles] val _tY = new Array[Float](TETHER_MAX)
  private[projectiles] val _tNX = new Array[Float](TETHER_MAX)  // unit normal at each
  private[projectiles] val _tNY = new Array[Float](TETHER_MAX)
  private[projectiles] val _tT = new Array[Float](TETHER_MAX)   // 0 at the head, 1 at the thrower
  private[projectiles] val _tW = new Array[Float](TETHER_MAX)   // the limb's own width at each
  private[projectiles] val _tA = new Array[Float](TETHER_MAX)   // and its alpha
  private[projectiles] var _tN = 0
  private var _tLen = 0f

  /** How high a thrower holds what they throw: about their hands, not their feet. */
  private val TETHER_HAND = 15f

  /**
   * Lay a tether out from the head back to the thrower as `_tN` points about `spacing` apart:
   * bowed to one side by up to `bow` (and an S by a little less), swaying slowly, hanging `sag`
   * lower in the middle, and rippling `ripple` either side in a wave running out along it to the
   * head, as a limb reaching does. It starts at (startX, startY) on screen when one is given — where a
   * head that is longer than the hitbox ties on — and at the hitbox otherwise. Returns false when the
   * head is on its thrower. A thrower who has blinked away doesn't drag a tether across the screen
   * after them: it stops at the range the projectile could have flown and fades out there.
   */
  private[projectiles] def layTether(proj: Projectile, sx: Float, sy: Float, phase: Double, spacing: Float, bow: Float,
                                     sag: Float, ripple: Float = 0f, startX: Float = Float.NaN,
                                     startY: Float = Float.NaN): Boolean = {
    val live = _anchorOn
    val hx = if (_headOn) _headWX else proj.getX
    val hy = if (_headOn) _headWY else proj.getY
    var dxw = (if (live) _anchorWX else proj.originX) - hx
    var dyw = (if (live) _anchorWY else proj.originY) - hy
    val maxW = ProjectileDef.get(proj.projectileType).maxRange * 1.25f + 2f
    val dw = Math.sqrt(dxw * dxw + dyw * dyw).toFloat
    if (dw > maxW) { dxw *= maxW / dw; dyw *= maxW / dw }
    // The thrower's hand on screen, and the line to it from where the tether ties on
    val rootX = sx + (dxw - dyw) * 20f
    val rootY = sy + (dxw + dyw) * 10f - (if (live) TETHER_HAND else 0f)
    val x0 = if (startX.isNaN) sx else startX
    val y0 = if (startY.isNaN) sy else startY
    val vx = rootX - x0; val vy = rootY - y0
    val len = Math.sqrt(vx * vx + vy * vy).toFloat
    if (len < 4f) { _tN = 0; return false }
    val qx = -vy / len; val qy = vx / len
    val n = Math.max(3, Math.min(TETHER_MAX, (len / spacing).toInt + 1))
    val b1 = Math.min(bow, len * 0.12f) * Math.sin(phase * 0.5 + proj.id * 1.3).toFloat
    val b2 = Math.min(bow, len * 0.12f) * 0.45f * Math.cos(phase * 0.37 + proj.id * 2.1).toFloat
    // A ripple of a wave or two along it, whatever its length, its crests running slowly out toward
    // the head: one every few units at full stretch was an eel swimming
    val rip = Math.min(ripple, len * 0.06f)
    val waves = clampF(len / 170f, 0.6f, 2.2f)
    var i = 0
    while (i < n) {
      val t = i.toFloat / (n - 1)
      val hump = 4f * t * (1f - t)
      var off = b1 * hump + b2 * Math.sin(t * 2.0 * Math.PI).toFloat
      if (rip > 0f) off += rip * hump * Math.sin(t * waves * 2.0 * Math.PI + phase * 0.45).toFloat
      _tX(i) = x0 + vx * t + qx * off
      _tY(i) = y0 + vy * t + qy * off + sag * hump
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
   *  (`swell` < 1 swells early, as a limb does), full alpha fading out over its last `fadeLen`
   *  units into whoever is holding it — the same length whatever the tether's, so a long one isn't a
   *  long smear into its thrower. */
  private[projectiles] def shapeTether(w0: Float, w1: Float, swell: Float, a: Float, fadeLen: Float): Unit = {
    val fade = Math.max(1e-3f, Math.min(0.5f, fadeLen / Math.max(1f, _tLen)))
    var i = 0
    while (i < _tN) {
      val t = _tT(i)
      _tW(i) = w0 + (w1 - w0) * Math.pow(t, swell).toFloat
      _tA(i) = a * (if (t > 1f - fade) Math.max(0f, (1f - t) / fade) else 1f)
      i += 1
    }
  }

  /** Stroke the laid tether: each point's width times `wk` plus `wAdd`, alpha times `ak`, pushed
   *  `shift` of its width along its normal. */
  private[projectiles] def strokeTether(sb: ShapeBatch, wk: Float, wAdd: Float, ak: Float, r: Float, g: Float, b: Float,
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
  private[projectiles] def tetherAt(t: Float): Unit = {
    val f = clampF(t, 0f, 1f) * (_tN - 1)
    val i = Math.min(_tN - 2, f.toInt); val u = f - i
    _ptX = _tX(i) + (_tX(i + 1) - _tX(i)) * u
    _ptY = _tY(i) + (_tY(i + 1) - _tY(i)) * u
  }

  /**
   * The tip of a limb curling round what it has caught: from the head on along (fx, fy), turning
   * by `sweep` on a circle of radius `rad` to the `side` it turns, tapering from `w0`. Ink and body,
   * like the limb it ends.
   */
  private def curlTip(sb: ShapeBatch, sx: Float, sy: Float, fx: Float, fy: Float, side: Float, sweep: Float,
                      rad: Float, w0: Float, r: Float, g: Float, b: Float, ink: Float, a: Float): Unit = {
    val n = 7
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
    sb.strokePolylineTapered(_pvX, _pvY, n, w0 + 3f, 2.6f, r * ink, g * ink, b * ink, 0.92f * a, 0.92f * a)
    sb.strokePolylineTapered(_pvX, _pvY, n, w0, 0.9f, r, g, b, a, a)
  }

  /** A tethered grab (see the object's doc). (r, g, b) is the limb's own colour where its kind has
    * one to be given (the tentacles, the death grip). */
  private[projectiles] def tether(kind: Int, r: Float, g: Float, b: Float): Renderer =
    (proj, sx, sy, sb, tick) => {
      val phase = (tick + proj.id * 23) * 0.35
      computeAllDynamics(proj, r, g, b, phase)
      // As solid at the end of its reach as anywhere, and as big: a shot's burn-out there (dynAlpha,
      // dynScale) turned a pull at full stretch into a swollen ghost of itself
      val p = 1f
      val ds = 1f
      screenDir(proj)
      val ndx = _sdx; val ndy = _sdy
      // Opening and shutting as it reaches; shut on whatever it has hold of
      val grip = if (_holding) 1f else 0.5f + 0.5f * Math.sin(phase * 1.5).toFloat
      kind match {
        case TETH_TENTACLE => tentacleLimb(sb, proj, sx, sy, ndx, ndy, ds, r, g, b, p, phase, grip, spots = false)
        case TETH_KRAKEN => tentacleLimb(sb, proj, sx, sy, ndx, ndy, ds, r, g, b, p, phase, grip, spots = true)
        case TETH_VINE => vineLimb(sb, proj, sx, sy, ndx, ndy, ds, p, phase, grip, blossom = false)
        case TETH_BLOSSOM => vineLimb(sb, proj, sx, sy, ndx, ndy, ds, p, phase, grip, blossom = true)
        case TETH_DEATH =>
          deathChain(sb, proj, sx, sy, ds, r, g, b, p, phase, tick)
          deathHook(sb, sx, sy, ndx, ndy, ds, r, g, b, p, phase)
        case TETH_TONGUE => chameleonTongue(sb, proj, sx, sy, ndx, ndy, ds, p, phase, tick)
        case TETH_BANDAGE => mummyBandage(sb, proj, sx, sy, ndx, ndy, ds, p, phase, tick)
        case TETH_PAW =>
          furArm(sb, proj, sx, sy, ndx, ndy, ds, p, phase, 0.5f, 0.31f, 0.15f, 0.7f, 0.5f, 0.28f, 8.5f, 12.5f, 9f)
          bearPaw(sb, sx, sy, ndx, ndy, ds, p, grip)
        case TETH_HAND =>
          furArm(sb, proj, sx, sy, ndx, ndy, ds, p, phase, 0.27f, 0.27f, 0.31f, 0.46f, 0.46f, 0.52f, 9f, 14f, 13f)
          gorillaHand(sb, sx, sy, ndx, ndy, ds, p, grip)
        case TETH_TALON => griffinLeash(sb, proj, sx, sy, ndx, ndy, ds, p, phase, tick, grip)
        case TETH_SHARK => sharkSurge(sb, proj, sx, sy, ndx, ndy, ds, p, phase, tick, grip)
        case _ =>
      }
      drawChargeCrackle(sx, sy, 16f * ds, r, g, b, p, sb, phase, proj.chargeLevel)
    }

  /**
   * A tentacle: thick where it leaves its thrower, tapering to a tip that curls round what it has
   * caught, rippling as it reaches; its own colour (r, g, b) on top and a paler underside toward the
   * camera with a sucker every few units down it — no lit ridge or stripe down its length, which made
   * a long one a garden hose. The Kraken's (`spots`) is its own navy, spotted white as it is.
   */
  private def tentacleLimb(sb: ShapeBatch, proj: Projectile, sx: Float, sy: Float, ndx: Float, ndy: Float,
                           ds: Float, r: Float, g: Float, b: Float, p: Float, phase: Double, grip: Float,
                           spots: Boolean): Unit = {
    val ink = 0.2f
    val belR = mix(r, 1f, 0.5f); val belG = mix(g, 1f, 0.45f); val belB = mix(b, 1f, 0.4f)
    // Which way its underside faces: toward the camera, whichever side of the line that is
    val side = if (-ndx > 0f) 1f else -1f
    if (layTether(proj, sx, sy, phase, 8f * ds, 14f * ds, 0f, ripple = 5f * ds)) {
      shapeTether(4.6f * ds, 13.5f * ds, 0.8f, p, 16f)
      strokeTether(sb, 1f, 3.2f, 0.92f, r * ink, g * ink, b * ink, 0f)
      strokeTether(sb, 1f, 0f, 1f, r, g, b, 0f)
      strokeTether(sb, 0.44f, 0f, 1f, belR, belG, belB, 0.27f * side)
      // Suckers down the underside, a ring with its hollow, larger toward the root
      var i = 1
      while (i < _tN - 1) {
        val t = _tT(i)
        if (t > 0.03f && t < 0.88f) {
          val w = _tW(i); val a = _tA(i)
          val cx = _tX(i) + _tNX(i) * side * w * 0.29f; val cy = _tY(i) + _tNY(i) * side * w * 0.29f
          val rr = w * 0.15f + 0.7f
          sb.fillOval(cx, cy, rr + 0.9f, rr * 0.85f + 0.9f, r * ink, g * ink, b * ink, 0.75f * a, 8)
          sb.fillOval(cx, cy, rr, rr * 0.85f, mix(belR, 1f, 0.5f), mix(belG, 1f, 0.5f), mix(belB, 1f, 0.5f), a, 8)
          sb.fillOval(cx, cy + rr * 0.1f, rr * 0.45f, rr * 0.38f, mix(r, belR, 0.3f), mix(g, belG, 0.3f),
            mix(b, belB, 0.3f), 0.9f * a, 6)
        }
        if (spots && (i % 3) == 1 && t < 0.92f) {
          // White spots along its top, as the Kraken's skin has
          val w = _tW(i); val a = _tA(i)
          val off = -side * w * (0.18f + 0.12f * hash01(proj.id, i))
          sb.fillOval(_tX(i) + _tNX(i) * off, _tY(i) + _tNY(i) * off, w * 0.11f + 0.5f, w * 0.1f + 0.5f,
            0.95f, 0.97f, 1f, 0.9f * a, 6)
        }
        i += 1
      }
    }
    curlTip(sb, sx, sy, ndx, ndy, side, 2.8f + 1.9f * grip, 9f * ds, 5.6f * ds, r, g, b, ink, p)
  }

  /** A thorned vine: a dark stem with a paler strand wound round it, thorns down both sides raked
   *  back toward the root, a leaf now and then — or, the Thornweaver's (`blossom`), a pink flower —
   *  and a tendril curling shut round what it has caught. */
  private def vineLimb(sb: ShapeBatch, proj: Projectile, sx: Float, sy: Float, ndx: Float, ndy: Float,
                       ds: Float, p: Float, phase: Double, grip: Float, blossom: Boolean): Unit = {
    if (layTether(proj, sx, sy, phase, 8f * ds, 12f * ds, 0f, ripple = 3f * ds)) {
      shapeTether(4.8f * ds, 10.5f * ds, 0.6f, p, 14f)
      strokeTether(sb, 1f, 3f, 0.92f, 0.08f, 0.15f, 0.05f, 0f)
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
      // Thorns, raked back toward the root, and a leaf or a flower every so often on the other side
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
          val ox = _tX(i) - nx * w * 0.35f; val oy = _tY(i) - ny * w * 0.35f
          if (blossom) vineFlower(sb, ox - nx * 4f * ds, oy - ny * 4f * ds, 4.6f * ds, phase + k, a)
          else {
            // A leaf, angled back along the stem
            val lx = -nx * 0.7f + tx * 0.7f; val ly = -ny * 0.7f + ty * 0.7f
            val lb = Math.sqrt(lx * lx + ly * ly).toFloat
            val dx = lx / lb; val dy = ly / lb
            val L = 9f * ds; val W = 3.4f * ds
            _polyXs4(0) = ox; _polyYs4(0) = oy
            _polyXs4(1) = ox + dx * L * 0.5f - dy * W; _polyYs4(1) = oy + dy * L * 0.5f + dx * W
            _polyXs4(2) = ox + dx * L; _polyYs4(2) = oy + dy * L
            _polyXs4(3) = ox + dx * L * 0.5f + dy * W; _polyYs4(3) = oy + dy * L * 0.5f - dx * W
            sb.strokePolygon(_polyXs4, _polyYs4, 4, 1.4f, 0.06f, 0.14f, 0.04f, 0.85f * a)
            sb.fillPolygon(_polyXs4, _polyYs4, 4, 0.40f, 0.74f, 0.27f, a)
            sb.strokeLine(ox, oy, ox + dx * L * 0.85f, oy + dy * L * 0.85f, 1f, 0.2f, 0.42f, 0.13f, 0.8f * a)
          }
        }
        i += 3; k += 1
      }
    }
    // The tendril at its tip, curling shut, with a bud (a flower, in flower) where it starts
    curlTip(sb, sx, sy, ndx, ndy, 1f, 3.2f + 1.6f * grip, 8f * ds, 5.2f * ds, 0.34f, 0.6f, 0.2f, 0.28f, p)
    if (blossom) vineFlower(sb, sx, sy, 6.4f * ds, phase, p)
    else {
      sb.fillOval(sx, sy, 5.4f * ds + 1.6f, 4.5f * ds + 1.6f, 0.08f, 0.15f, 0.05f, 0.92f * p, 12)
      sb.fillOval(sx, sy, 5.4f * ds, 4.5f * ds, 0.52f, 0.78f, 0.3f, p, 12)
      sb.fillOval(sx - 1.4f * ds, sy - 1.5f * ds, 2f * ds, 1.4f * ds, 0.75f, 0.92f, 0.55f, 0.85f * p, 8)
    }
  }

  /** A five-petalled pink flower, `r` across, turning slowly: the Thornweaver's. */
  private def vineFlower(sb: ShapeBatch, cx: Float, cy: Float, r: Float, phase: Double, a: Float): Unit = {
    var k = 0
    while (k < 5) {
      val an = phase * 0.15 + k * (2 * Math.PI / 5)
      val px = cx + Math.cos(an).toFloat * r * 0.55f; val py = cy + Math.sin(an).toFloat * r * 0.42f
      sb.fillOval(px, py, r * 0.48f + 1.2f, r * 0.4f + 1.2f, 0.36f, 0.08f, 0.2f, 0.85f * a, 10)
      k += 1
    }
    k = 0
    while (k < 5) {
      val an = phase * 0.15 + k * (2 * Math.PI / 5)
      val px = cx + Math.cos(an).toFloat * r * 0.55f; val py = cy + Math.sin(an).toFloat * r * 0.42f
      sb.fillOval(px, py, r * 0.48f, r * 0.4f, 1f, 0.62f, 0.8f, a, 10)
      k += 1
    }
    sb.fillOval(cx, cy, r * 0.3f, r * 0.26f, 1f, 0.86f, 0.3f, a, 8)
  }

  /**
   * An arm reaching out, furred: the Bear's brown one and the Gorilla's black one, thick at the shoulder
   * and as thick as a wrist at the hand, with tufts of fur along both edges raked back toward the
   * shoulder and its underside in shade. (fr, fg, fb) its fur, (lr, lg, lb) its lighter tufts. It
   * replaced a band of glowing "spirit" between the thrower and a paw print, which reached across the
   * screen like a laser.
   */
  private def furArm(sb: ShapeBatch, proj: Projectile, sx: Float, sy: Float, ndx: Float, ndy: Float, ds: Float,
                     p: Float, phase: Double, fr: Float, fg: Float, fb: Float, lr: Float, lg: Float, lb: Float,
                     wrist: Float, shoulder: Float, tie: Float): Unit = {
    // It ties on at the wrist, behind the hand
    if (!layTether(proj, sx, sy, phase, 7f * ds, 10f * ds, 0f, ripple = 2f * ds,
      sx - ndx * tie * ds, sy - ndy * tie * ds)) return
    shapeTether(wrist * ds, shoulder * ds, 1f, p, 16f)
    val ink = 0.22f
    // Tufts of fur along its edges: the ink of them, then of the arm, then their fur, then the arm's,
    // so the arm cuts them off at their roots
    var pass = 0
    while (pass < 2) {
      if (pass == 1) strokeTether(sb, 1f, 3f, 0.92f, fr * ink, fg * ink, fb * ink, 0f)
      var i = 1
      while (i < _tN - 1) {
        if (_tT(i) < 0.9f) {
          val side = if ((i & 1) == 0) 1f else -1f
          val w = _tW(i); val a = _tA(i)
          val tx0 = _tX(i + 1) - _tX(i - 1); val ty0 = _tY(i + 1) - _tY(i - 1)
          val tl = Math.max(1e-3f, Math.sqrt(tx0 * tx0 + ty0 * ty0).toFloat)
          val tx = tx0 / tl; val ty = ty0 / tl
          val nx = _tNX(i) * side; val ny = _tNY(i) * side
          val bx = _tX(i) + nx * w * 0.38f; val by = _tY(i) + ny * w * 0.38f
          _polyXs3(0) = bx - tx * w * 0.34f; _polyYs3(0) = by - ty * w * 0.34f
          _polyXs3(1) = bx + tx * w * 0.3f; _polyYs3(1) = by + ty * w * 0.3f
          _polyXs3(2) = bx + nx * w * 0.3f + tx * w * 0.42f; _polyYs3(2) = by + ny * w * 0.3f + ty * w * 0.42f
          if (pass == 0) sb.strokePolygon(_polyXs3, _polyYs3, 3, 1.8f, fr * ink, fg * ink, fb * ink, 0.9f * a)
          else sb.fillPolygon(_polyXs3, _polyYs3, 3, mix(fr, lr, 0.5f), mix(fg, lg, 0.5f), mix(fb, lb, 0.5f), a)
        }
        i += 1
      }
      pass += 1
    }
    strokeTether(sb, 1f, 0f, 1f, fr, fg, fb, 0f)
    // Its underside in shade, toward the camera, and a lighter run of fur along its top
    val side = if (-ndx > 0f) 1f else -1f
    strokeTether(sb, 0.4f, 0f, 0.8f, fr * 0.72f, fg * 0.72f, fb * 0.72f, 0.3f * side)
    strokeTether(sb, 0.28f, 0f, 0.7f, lr, lg, lb, -0.26f * side)
  }

  // The Bear's paw, open, in its local frame (+x the way it reaches): the paw, its four toes in an arc
  // ahead of it, a black claw off each, and its pads
  private val BEAR_TOE_ANGLE = Array(-1.05f, -0.36f, 0.36f, 1.05f)

  /**
   * The Bear's paw at the end of its arm, reaching: brown, its four toes splayed (drawn in as it closes
   * on what it has caught), a long black claw hooking off each, its dark pads showing — the paw print
   * every player knows, on a paw rather than floating free.
   */
  private def bearPaw(sb: ShapeBatch, sx: Float, sy: Float, ndx: Float, ndy: Float, ds: Float, p: Float,
                      grip: Float): Unit = {
    val k = 13f * ds
    headingFrame(sx - ndx * 0.55f * k, sy - ndy * 0.55f * k, ndx, ndy, k)
    val spread = 1.05f - 0.3f * grip
    val reach = 0.78f - 0.12f * grip
    sb.fillOvalSoft(fx(0.1f, 0f), fy(0.1f, 0f), 1.6f * k, 1.4f * k, 0.95f, 0.7f, 0.4f, 0.22f * p, 0f, 14)
    // Claws, under the toes
    var t = 0
    while (t < 4) {
      val an = BEAR_TOE_ANGLE(t) * spread
      val cx = Math.cos(an).toFloat * reach; val cy = Math.sin(an).toFloat * reach
      val dx = Math.cos(an * 0.55f).toFloat; val dy = Math.sin(an * 0.55f).toFloat
      val hook = -Math.signum(cy) * (0.08f + 0.22f * grip)
      _pvX(0) = fx(cx + dx * 0.12f, cy + dy * 0.12f); _pvY(0) = fy(cx + dx * 0.12f, cy + dy * 0.12f)
      _pvX(1) = fx(cx + dx * 0.4f, cy + dy * 0.4f); _pvY(1) = fy(cx + dx * 0.4f, cy + dy * 0.4f)
      _pvX(2) = fx(cx + dx * 0.6f - dy * hook, cy + dy * 0.6f + dx * hook)
      _pvY(2) = fy(cx + dx * 0.6f - dy * hook, cy + dy * 0.6f + dx * hook)
      sb.strokePolylineTapered(_pvX, _pvY, 3, 0.2f * k + 2.2f, 1.8f, 0.05f, 0.03f, 0.02f, 0.95f * p, 0.95f * p)
      sb.strokePolylineTapered(_pvX, _pvY, 3, 0.2f * k, 0.5f, 0.16f, 0.12f, 0.1f, p, p)
      t += 1
    }
    // The paw and its toes, inked as one, then filled
    var pass = 0
    while (pass < 2) {
      var n = ovalPts(0f, 0f, 0.62f, 0.66f, 0f, 18)
      if (pass == 0) sb.strokePolygon(_gx, _gy, n, 2.6f, 0.2f, 0.1f, 0.04f, 0.95f * p)
      else sb.fillPolygon(_gx, _gy, n, 0.56f, 0.36f, 0.18f, p)
      t = 0
      while (t < 4) {
        val an = BEAR_TOE_ANGLE(t) * spread
        n = ovalPts(Math.cos(an).toFloat * reach, Math.sin(an).toFloat * reach, 0.25f, 0.22f, an, 12)
        if (pass == 0) sb.strokePolygon(_gx, _gy, n, 2.6f, 0.2f, 0.1f, 0.04f, 0.95f * p)
        else sb.fillPolygon(_gx, _gy, n, 0.6f, 0.4f, 0.2f, p)
        t += 1
      }
      pass += 1
    }
    // Its pads: the heel, three lobes, and a bean on each toe
    var n = ovalPts(-0.06f, 0f, 0.32f, 0.38f, 0f, 14)
    sb.fillPolygon(_gx, _gy, n, 0.24f, 0.14f, 0.1f, p)
    n = ovalPts(0.16f, 0f, 0.2f, 0.42f, 0f, 12)
    sb.fillPolygon(_gx, _gy, n, 0.24f, 0.14f, 0.1f, p)
    t = 0
    while (t < 4) {
      val an = BEAR_TOE_ANGLE(t) * spread
      n = ovalPts(Math.cos(an).toFloat * (reach + 0.02f), Math.sin(an).toFloat * (reach + 0.02f), 0.13f, 0.11f, an, 10)
      sb.fillPolygon(_gx, _gy, n, 0.24f, 0.14f, 0.1f, p)
      t += 1
    }
    // A glint of light on the fur, from the upper left
    sb.fillOval(fx(-0.25f, -0.38f), fy(-0.25f, -0.38f), 0.16f * k, 0.1f * k, 0.86f, 0.66f, 0.42f, 0.7f * p, 8)
  }

  /**
   * The Gorilla's hand at the end of its arm, reaching: a broad black-leather palm, four thick fingers
   * spread wide (curling in as it closes on what it has caught) and a thumb out to one side, pale nails.
   */
  private def gorillaHand(sb: ShapeBatch, sx: Float, sy: Float, ndx: Float, ndy: Float, ds: Float, p: Float,
                          grip: Float): Unit = {
    val k = 16f * ds
    headingFrame(sx - ndx * 0.72f * k, sy - ndy * 0.72f * k, ndx, ndy, k)
    val open = 1f - grip
    sb.fillOvalSoft(fx(0.2f, 0f), fy(0.2f, 0f), 1.4f * k, 1.25f * k, 0.75f, 0.75f, 0.85f, 0.16f * p, 0f, 14)
    var pass = 0
    while (pass < 2) {
      val ink = pass == 0
      // The palm, then the fingers and the thumb over it, each from its knuckle out to its tip, curling
      // in as it shuts
      val n = ovalPts(-0.05f, 0f, 0.5f, 0.52f, 0f, 16)
      if (ink) sb.strokePolygon(_gx, _gy, n, 2.8f, 0.04f, 0.04f, 0.05f, 0.95f * p)
      else sb.fillPolygon(_gx, _gy, n, 0.24f, 0.23f, 0.27f, p)
      var f = 0
      while (f < 5) {
        val thumb = f == 4
        val by = if (thumb) -0.4f else -0.36f + f * 0.24f
        val bx = if (thumb) -0.12f else 0.32f
        val ex = if (thumb) mix(0.2f, 0.42f, open) else mix(0.62f, 1.02f, open)
        val ey = if (thumb) mix(-0.62f, -0.88f, open) else by * mix(0.8f, 1.3f, open)
        val curl = (if (thumb) 0.08f else 0.18f) * grip
        _pvX(0) = fx(bx, by); _pvY(0) = fy(bx, by)
        _pvX(1) = fx((bx + ex) * 0.5f + curl * 0.5f, (by + ey) * 0.5f); _pvY(1) = fy((bx + ex) * 0.5f + curl * 0.5f, (by + ey) * 0.5f)
        _pvX(2) = fx(ex - curl, ey * (1f - curl)); _pvY(2) = fy(ex - curl, ey * (1f - curl))
        val w = (if (thumb) 0.26f else 0.25f) * k
        if (ink) sb.strokePolylineTapered(_pvX, _pvY, 3, w + 2.8f, w * 0.85f + 2.8f, 0.04f, 0.04f, 0.05f, 0.95f * p, 0.95f * p)
        else {
          sb.strokePolylineTapered(_pvX, _pvY, 3, w, w * 0.85f, 0.27f, 0.26f, 0.3f, p, p)
          // A knuckle's crease and the nail
          sb.fillOval(_pvX(1), _pvY(1), w * 0.3f, w * 0.22f, 0.4f, 0.38f, 0.42f, 0.8f * p, 8)
          sb.fillOval(_pvX(2), _pvY(2), w * 0.28f, w * 0.24f, 0.64f, 0.6f, 0.62f, 0.95f * p, 8)
        }
        f += 1
      }
      pass += 1
    }
    // The palm's leather, paler, with its creases
    val n = ovalPts(0.0f, 0.02f, 0.3f, 0.34f, 0f, 14)
    sb.fillPolygon(_gx, _gy, n, 0.42f, 0.38f, 0.4f, p)
    gLine(sb, -0.22f, -0.12f, 0.16f, -0.18f, 0.05f, 0.2f, 0.18f, 0.2f, 0.8f * p)
    gLine(sb, -0.22f, 0.1f, 0.14f, 0.05f, 0.05f, 0.2f, 0.18f, 0.2f, 0.8f * p)
  }

  /**
   * The Griffin's talon grab: a leash of golden feathers laid along a golden cord, every feather
   * pointing back to the Griffin and fluttering, tied to the thigh of an eagle's foot — the same foot
   * as the Griffin's kick (Bites.birdFoot), its talons opening and closing as it reaches and clenched
   * on what it has caught.
   */
  private def griffinLeash(sb: ShapeBatch, proj: Projectile, sx: Float, sy: Float, ndx: Float, ndy: Float,
                           ds: Float, p: Float, phase: Double, tick: Int, grip: Float): Unit = {
    val s = 10.5f * ds
    Bites.birdFootThigh(sx, sy, ndx, ndy, s)
    if (layTether(proj, sx, sy, phase, 8f * ds, 9f * ds, 0f, ripple = 2f * ds, _ptX, _ptY)) {
      shapeTether(2.6f * ds, 3.4f * ds, 1f, p, 18f)
      strokeTether(sb, 1f, 2.4f, 0.92f, 0.3f, 0.18f, 0.04f, 0f)
      strokeTether(sb, 1f, 0f, 1f, 0.98f, 0.8f, 0.32f, 0f)
      // Feathers along it, every other point, fluttering, pointing back toward the Griffin
      var i = 1
      while (i < _tN - 1) {
        val t = _tT(i)
        if (t < 0.9f) {
          val a = _tA(i)
          val side = if ((i & 2) == 0) 1f else -1f
          val tx0 = _tX(i + 1) - _tX(i - 1); val ty0 = _tY(i + 1) - _tY(i - 1)
          val tl = Math.max(1e-3f, Math.sqrt(tx0 * tx0 + ty0 * ty0).toFloat)
          val dx = tx0 / tl; val dy = ty0 / tl
          val flutter = 0.35f + 0.15f * Math.sin(phase * 1.7 + i * 0.9).toFloat
          val nx = _tNX(i) * side; val ny = _tNY(i) * side
          // From the cord out along the tether toward the Griffin, splayed a little off it
          val ox = _tX(i); val oy = _tY(i)
          val ax = dx + nx * flutter; val ay = dy + ny * flutter
          val al = Math.sqrt(ax * ax + ay * ay).toFloat
          val ux = ax / al; val uy = ay / al
          val L = 11f * ds; val W = 3.2f * ds
          _polyXs4(0) = ox; _polyYs4(0) = oy
          _polyXs4(1) = ox + ux * L * 0.45f - uy * W; _polyYs4(1) = oy + uy * L * 0.45f + ux * W
          _polyXs4(2) = ox + ux * L; _polyYs4(2) = oy + uy * L
          _polyXs4(3) = ox + ux * L * 0.45f + uy * W; _polyYs4(3) = oy + uy * L * 0.45f - ux * W
          sb.strokePolygon(_polyXs4, _polyYs4, 4, 1.6f, 0.3f, 0.18f, 0.04f, 0.9f * a)
          sb.fillPolygon(_polyXs4, _polyYs4, 4, 1f, 0.84f, 0.42f, a)
          sb.strokeLine(ox, oy, ox + ux * L * 0.85f, oy + uy * L * 0.85f, 0.9f, 0.62f, 0.42f, 0.12f, 0.8f * a)
        }
        i += 2
      }
    }
    Bites.birdFoot(sb, sx, sy, ndx, ndy, s, 1f - grip, 0.92f, 0.7f, 0.26f, 1f, 0.9f, 0.6f, p)
  }

  /**
   * The Shark's jaw drag: the shark itself lunging out of a surge of water that runs back to the Shark —
   * its snout and its jaws gaping (snapping shut on what it caught), rows of teeth, its black eye, its
   * fin cutting up through the foam — and the water curling and foaming along the surge. It used to be a
   * grey disc with a fin stuck on it, two red eyes and teeth round its edge, flying free.
   */
  private def sharkSurge(sb: ShapeBatch, proj: Projectile, sx: Float, sy: Float, ndx: Float, ndy: Float,
                         ds: Float, p: Float, phase: Double, tick: Int, grip: Float): Unit = {
    val s = 11.5f * ds
    // Where its body sinks into the water: the surge ties on there
    Bites.sharkTail(sx, sy, ndx, ndy, s)
    if (layTether(proj, sx, sy, phase, 8f * ds, 10f * ds, 0f, ripple = 3f * ds, _ptX, _ptY)) {
      shapeTether(10f * ds, 6f * ds, 1f, p, 18f)
      strokeTether(sb, 1f, 3f, 0.9f, 0.04f, 0.14f, 0.32f, 0f)
      strokeTether(sb, 1f, 0f, 1f, 0.2f, 0.5f, 0.88f, 0f)
      strokeTether(sb, 0.5f, 0f, 0.9f, 0.45f, 0.75f, 1f, -0.18f)
      // Foam curling over along its crest, every other point, and spray thrown off it
      val side = if (-ndx > 0f) 1f else -1f
      var i = 1
      while (i < _tN - 1) {
        val a = _tA(i); val w = _tW(i)
        if ((i & 1) == 1) {
          val tx0 = _tX(i + 1) - _tX(i - 1); val ty0 = _tY(i + 1) - _tY(i - 1)
          val tl = Math.max(1e-3f, Math.sqrt(tx0 * tx0 + ty0 * ty0).toFloat)
          // Toward the head: the way the surge runs
          val ux = -tx0 / tl; val uy = -ty0 / tl
          val nx = -_tNX(i) * side; val ny = -_tNY(i) * side
          val curl = 0.6f + 0.4f * Math.sin(phase * 1.4 - i * 0.8).toFloat
          val bx = _tX(i) + nx * w * 0.3f; val by = _tY(i) + ny * w * 0.3f
          _pvX(0) = bx - ux * w * 0.5f; _pvY(0) = by - uy * w * 0.5f
          _pvX(1) = bx + nx * w * 0.32f * curl; _pvY(1) = by + ny * w * 0.32f * curl
          _pvX(2) = bx + ux * w * 0.5f + nx * w * 0.12f * curl; _pvY(2) = by + uy * w * 0.5f + ny * w * 0.12f * curl
          _pvX(3) = bx + ux * w * 0.62f - nx * w * 0.06f; _pvY(3) = by + uy * w * 0.62f - ny * w * 0.06f
          sb.strokePolylineTapered(_pvX, _pvY, 4, 3.4f + 2f, 1.6f + 1.4f, 0.04f, 0.14f, 0.32f, 0.6f * a, 0.4f * a)
          sb.strokePolylineTapered(_pvX, _pvY, 4, 3.4f, 1.6f, 0.95f, 0.98f, 1f, a, 0.8f * a)
        }
        i += 1
      }
    }
    // Spray thrown up round the shark
    var d = 0
    while (d < 5) {
      val t = loop(tick, 0.05, d / 5.0 + proj.id * 0.21)
      val an = -Math.PI * 0.5 + (d - 2) * 0.55
      val dx = Math.cos(an).toFloat; val dy = Math.sin(an).toFloat
      val ex = sx - ndx * 6f * ds + dx * (6f + t * 16f) * ds
      val ey = sy - ndy * 6f * ds + dy * (6f + t * 16f) * ds + t * t * 10f * ds
      val rr = (1.2f + (1f - t) * 1.2f) * ds
      sb.fillOval(ex, ey, rr + 0.7f, rr + 0.7f, 0.04f, 0.14f, 0.32f, 0.5f * (1f - t) * p, 6)
      sb.fillOval(ex, ey, rr, rr, 0.9f, 0.96f, 1f, (1f - t) * p, 6)
      d += 1
    }
    Bites.sharkHead(sb, sx, sy, ndx, ndy, s, 1f - grip, p, phase)
  }

  /** Death Grip's chain: a band of dark violet with links riding it, and souls drifting back
   *  along it toward the Death Knight — the pull. */
  private def deathChain(sb: ShapeBatch, proj: Projectile, sx: Float, sy: Float, ds: Float,
                         r: Float, g: Float, b: Float, p: Float, phase: Double, tick: Int): Unit = {
    if (!layTether(proj, sx, sy, phase, 6.5f * ds, 8f * ds, 5f * ds)) return
    shapeTether(4.2f * ds, 4.6f * ds, 1f, p, 16f)
    strokeTether(sb, 2.6f, 0f, 0.18f, r, g, b, 0f)
    strokeTether(sb, 1f, 2f, 0.92f, 0.08f, 0.03f, 0.12f, 0f)
    // Links, alternately lying flat and standing on edge
    var i = 0
    while (i < _tN - 1) {
      val x0 = _tX(i); val y0 = _tY(i); val x1 = _tX(i + 1); val y1 = _tY(i + 1)
      val mx = (x0 + x1) * 0.5f; val my = (y0 + y1) * 0.5f
      val ex = (x1 - x0) * 0.42f; val ey = (y1 - y0) * 0.42f
      val a = _tA(i)
      if ((i & 1) == 0) {
        sb.strokeLine(mx - ex, my - ey, mx + ex, my + ey, 4.4f * ds, 0.55f, 0.46f, 0.7f, 0.95f * a)
        sb.strokeLine(mx - ex * 0.6f, my - ey * 0.6f, mx + ex * 0.6f, my + ey * 0.6f, 1.6f * ds, 0.1f, 0.04f, 0.14f, a)
      } else sb.strokeLine(mx - ex, my - ey, mx + ex, my + ey, 1.9f * ds, mix(r, 1f, 0.4f), mix(g, 1f, 0.4f), mix(b, 1f, 0.4f), a)
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
    val s = 20f * ds
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

  /**
   * The Chameleon's tongue lash: its tongue, shot out of its mouth and still attached — pink and glossy,
   * a groove down it, thick where it leaves the Chameleon and thinning toward its tip, quivering as it
   * stretches — with the sticky club of its tip on what it has caught, cupped, glistening, strands of
   * spit hanging off it. It was a pink ball flying free, then a pink line with a ball on it.
   */
  private def chameleonTongue(sb: ShapeBatch, proj: Projectile, sx: Float, sy: Float, ndx: Float, ndy: Float,
                              ds: Float, p: Float, phase: Double, tick: Int): Unit = {
    val R = 9f * ds
    if (layTether(proj, sx, sy, phase, 7f * ds, 4f * ds, 0f, ripple = 2.5f * ds, sx - ndx * R * 0.7f, sy - ndy * R * 0.7f)) {
      shapeTether(3.6f * ds, 9f * ds, 1.6f, p, 12f)
      strokeTether(sb, 1f, 2.6f, 0.92f, 0.35f, 0.05f, 0.12f, 0f)
      strokeTether(sb, 1f, 0f, 1f, 0.95f, 0.45f, 0.58f, 0f)
      strokeTether(sb, 0.18f, 0f, 0.75f, 0.72f, 0.22f, 0.36f, 0.06f)
      strokeTether(sb, 0.22f, 0f, 0.8f, 1f, 0.85f, 0.9f, -0.28f)
    }
    // The sticky pad of its tip, broader across than along, its cupped face to what it struck, glossy
    val px = -ndy; val py = ndx
    var n = rotEllipsePts(sx - ndx * R * 0.2f, sy - ndy * R * 0.2f, px, py, R * 1.05f, R * 0.78f, 18)
    sb.strokePolygon(_gx, _gy, n, 2.6f, 0.35f, 0.05f, 0.12f, 0.95f * p)
    sb.fillPolygon(_gx, _gy, n, 0.95f, 0.42f, 0.56f, p)
    // The cup: a darker hollow toward the front, and a lit lip round it
    n = rotEllipsePts(sx + ndx * R * 0.12f, sy + ndy * R * 0.12f, px, py, R * 0.72f, R * 0.4f, 16)
    sb.fillPolygon(_gx, _gy, n, 0.7f, 0.15f, 0.3f, p)
    sb.fillOval(sx - ndx * R * 0.45f - R * 0.3f, sy - ndy * R * 0.45f - R * 0.35f, R * 0.32f, R * 0.2f, 1f, 1f, 1f,
      0.95f * p, 8)
    sb.fillOval(sx - ndx * R * 0.45f + R * 0.25f, sy - ndy * R * 0.45f - R * 0.5f, R * 0.12f, R * 0.09f, 1f, 1f, 1f,
      0.85f * p, 6)
    var d = 0
    while (d < 2) {
      val t = ((tick * 0.04 + d * 0.5 + proj.id * 0.27) % 1.0).toFloat
      val dx0 = sx + (d * 2 - 1) * R * 0.45f - ndx * t * 6f * ds; val dy0 = sy + R * 0.7f + t * 8f * ds
      sb.strokeLine(sx + (d * 2 - 1) * R * 0.45f, sy + R * 0.6f, dx0, dy0, 1f, 1f, 0.75f, 0.85f, 0.7f * p * (1f - t))
      sb.fillOval(dx0, dy0, 1.5f * ds, 1.9f * ds, 1f, 0.75f, 0.85f, 0.85f * p * (1f - t), 6)
      d += 1
    }
  }

  /**
   * The Mummy's bandage whip: a strip of its own grave linen paid out from the Mummy, twisting as it
   * goes so it shows its face and its darker back by turns, stained and fraying, its end wrapped round
   * what it has caught in a loop with the loose end flapping. It was a ball of bandage flying free.
   */
  private def mummyBandage(sb: ShapeBatch, proj: Projectile, sx: Float, sy: Float, ndx: Float, ndy: Float,
                           ds: Float, p: Float, phase: Double, tick: Int): Unit = {
    if (layTether(proj, sx, sy, phase, 6f * ds, 16f * ds, 6f * ds)) {
      shapeTether(8.6f * ds, 9.6f * ds, 1f, p, 14f)
      // Twisting: as wide as it is where it faces us, edge-on where it turns
      var i = 0
      while (i < _tN) {
        val c = Math.cos(_tT(i) * _tLen / 15f + phase * 0.8).toFloat
        _tW(i) = _tW(i) * (0.28f + 0.72f * Math.abs(c))
        _pvX(i) = _tX(i); _pvY(i) = _tY(i)
        i += 1
      }
      i = 0
      while (i < _tN) { _pvW(i) = _tW(i) + 3.4f; _pvA(i) = 0.95f * _tA(i); i += 1 }
      sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, _tN, 0.15f, 0.09f, 0.04f)
      // Its face and its back, each where it shows
      var face = 0
      while (face < 2) {
        i = 0
        while (i < _tN) {
          val c = Math.cos(_tT(i) * _tLen / 15f + phase * 0.8).toFloat
          _pvW(i) = _tW(i); _pvA(i) = if ((c >= 0f) == (face == 0)) _tA(i) else 0f
          i += 1
        }
        if (face == 0) sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, _tN, 0.93f, 0.88f, 0.74f)
        else sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, _tN, 0.72f, 0.64f, 0.48f)
        face += 1
      }
      // A stitch down its middle, stains, threads fraying off its edges
      i = 0
      while (i < _tN) { _pvW(i) = 0.8f * ds; _pvA(i) = 0.55f * _tA(i); i += 1 }
      sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, _tN, 0.6f, 0.52f, 0.36f)
      i = 2
      while (i < _tN - 2) {
        val h = ((proj.id * 7 + i * 13) & 7)
        if (h < 3) sb.fillOval(_tX(i), _tY(i), _tW(i) * 0.3f + 0.6f, _tW(i) * 0.2f + 0.5f, 0.55f, 0.42f, 0.22f, 0.7f * _tA(i), 8)
        else if (h == 5) sb.strokeLine(_tX(i) + _tNX(i) * _tW(i) * 0.45f, _tY(i) + _tNY(i) * _tW(i) * 0.45f,
          _tX(i) + _tNX(i) * (_tW(i) * 0.45f + 3.5f * ds), _tY(i) + _tNY(i) * (_tW(i) * 0.45f + 3.5f * ds) + 1.5f * ds,
          0.9f, 0.9f, 0.85f, 0.7f, 0.85f * _tA(i))
        i += 1
      }
    }
    // Its end, wrapped round what it caught, the loose end flapping
    val lr = 9f * ds
    sb.strokeOval(sx, sy, lr, lr * 0.62f, 5f * ds + 2.4f, 0.22f, 0.15f, 0.07f, 0.92f * p, 18)
    sb.strokeOval(sx, sy, lr, lr * 0.62f, 5f * ds, 0.93f, 0.88f, 0.74f, p, 18)
    sb.strokeArc(sx, sy, lr, lr * 0.62f, 0.3f, 2.4f, 1.5f * ds, 0.72f, 0.64f, 0.48f, p, 8)
    val flap = Math.sin(phase * 2.6).toFloat
    _pvX(0) = sx + lr; _pvY(0) = sy
    _pvX(1) = sx + lr + 6f * ds; _pvY(1) = sy + 3.5f * ds + flap * 2f * ds
    _pvX(2) = sx + lr + 11f * ds; _pvY(2) = sy + 4.5f * ds + flap * 4.5f * ds
    sb.strokePolylineTapered(_pvX, _pvY, 3, 4.4f * ds + 2.4f, 2f * ds + 2f, 0.22f, 0.15f, 0.07f, 0.9f * p, 0.9f * p)
    sb.strokePolylineTapered(_pvX, _pvY, 3, 4.4f * ds, 2f * ds, 0.93f, 0.88f, 0.74f, p, p)
  }

}
