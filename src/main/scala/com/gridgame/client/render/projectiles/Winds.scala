package com.gridgame.client.render.projectiles

import com.gridgame.common.model.Projectile
import com.gridgame.client.gl.ShapeBatch
import ProjectileKit._
import Silhouettes._

/**
 * Wind, drawn as whoever raised it: the Windwalker's wind blade a pinwheel of three swept blades
 * trailing her teal ribbon, and her gust a flurry of curling wind carrying leaves; the Cloudrunner's
 * a cloudlet streaking on its contrails and a billow of cloud; the Hawk's a whirlwind with its
 * feathers spiralling up it; the Raptor's an eagle of wind diving. All six were one pale crescent.
 *
 * A gust strikes where its wind is, a cell behind its front (ProjectileDef.hitCentreBehind), so
 * every gust carries its bulk behind the hitbox and only its leading edge on it.
 */
private[render] object Winds {
  private val _wx = new Array[Float](24)
  private val _wy = new Array[Float](24)
  // A curl's ink: its widths and alphas, a little wider and fainter than its body's
  private val _iw = new Array[Float](24)
  private val _ia = new Array[Float](24)

  // The frame a curl is laid out in: its origin, the way it is going (_cfx, _cfy), and across it
  // toward the top of the screen (_cux, _cuy), which is the side a curl rolls up on
  private var _cox = 0f; private var _coy = 0f
  private var _cfx = 1f; private var _cfy = 0f; private var _cux = 0f; private var _cuy = -1f

  private def curlFrame(ox: Float, oy: Float, ndx: Float, ndy: Float): Unit = {
    _cox = ox; _coy = oy; _cfx = ndx; _cfy = ndy
    val qx = -ndy; val qy = ndx
    if (qy > 0f) { _cux = -qx; _cuy = -qy } else { _cux = qx; _cuy = qy }
  }

  /**
   * A curl of wind: a streak running up from `tail` behind (lx0, ly0) and rolling up into a spiral
   * `rc` across at its front, the cartoon sign for moving air. Laid out in the curl frame, inked,
   * then stroked in (r, g, b), thickest where the streak turns into the spiral.
   */
  private def curl(sb: ShapeBatch, lx0: Float, ly0: Float, rc: Float, tail: Float, w: Float,
                   r: Float, g: Float, b: Float, ir: Float, ig: Float, ib: Float, a: Float): Unit = {
    if (a <= 0.01f) return
    var n = 0
    while (n < 3) {
      val lx = lx0 - tail * (1f - n / 2f)
      _wx(n) = _cox + lx * _cfx + ly0 * _cux; _wy(n) = _coy + lx * _cfy + ly0 * _cuy
      _pvW(n) = w * (0.35f + 0.65f * n / 2f); _pvA(n) = a * n / 2f
      n += 1
    }
    val cx = lx0; val cy = ly0 + rc
    var k = 1
    while (k <= 9) {
      val t = k / 9f
      val th = -Math.PI / 2 + t * 1.75 * Math.PI
      val rr = rc * (1f - 0.68f * t)
      val lx = cx + Math.cos(th).toFloat * rr; val ly = cy + Math.sin(th).toFloat * rr
      _wx(n) = _cox + lx * _cfx + ly * _cux; _wy(n) = _coy + lx * _cfy + ly * _cuy
      _pvW(n) = w * (1f - 0.55f * t); _pvA(n) = a * (1f - 0.2f * t)
      n += 1; k += 1
    }
    var i = 0
    while (i < n) { _iw(i) = _pvW(i) + 2.4f; _ia(i) = _pvA(i) * 0.6f; i += 1 }
    sb.strokePolylineVar(_wx, _wy, _iw, _ia, n, ir, ig, ib)
    sb.strokePolylineVar(_wx, _wy, _pvW, _pvA, n, r, g, b)
  }

  // ── Leaves ──
  // A leaf in units of its half-length, +x its tip: two convex halves either side of its vein.
  private val LEAF_X = Array(1f, 0.55f, -0.2f, -0.75f, -1f)
  private val LEAF_TOP_Y = Array(0f, -0.42f, -0.5f, -0.3f, 0f)
  private val LEAF_BOT_Y = Array(0f, 0.36f, 0.44f, 0.26f, 0f)

  /** A leaf at (cx, cy) pointing along (dx, dy), `s` long and `w` wide, tumbling as `w` narrows. */
  private def leaf(sb: ShapeBatch, cx: Float, cy: Float, dx: Float, dy: Float, s: Float, w: Float,
                   r: Float, g: Float, b: Float, a: Float): Unit = {
    if (a <= 0.01f) return
    val qx = -dy * w; val qy = dx * w
    var half = 0
    while (half < 2) {
      var i = 0
      while (i < 5) {
        val ly = if (half == 0) LEAF_TOP_Y(i) else LEAF_BOT_Y(i)
        _wx(i) = cx + LEAF_X(i) * s * dx + ly * qx; _wy(i) = cy + LEAF_X(i) * s * dy + ly * qy
        i += 1
      }
      sb.strokePolygon(_wx, _wy, 5, 1.4f, 0.06f, 0.18f, 0.06f, 0.8f * a)
      sb.fillPolygon(_wx, _wy, 5, if (half == 0) r else r * 0.8f, if (half == 0) g else g * 0.8f,
        if (half == 0) b else b * 0.8f, a)
      half += 1
    }
    sb.strokeLine(cx - dx * s * 1.2f, cy - dy * s * 1.2f, cx + dx * s * 0.8f, cy + dy * s * 0.8f, 1f,
      mix(r, 1f, 0.5f), mix(g, 1f, 0.5f), mix(b, 1f, 0.5f), 0.8f * a)
  }

  // ── The Windwalker ──

  /**
   * The Windwalker's wind blade: three swept blades of air whirling round a bright hub in the
   * ground plane, each with a smear of its own spin behind its tip, her teal ribbon streaming
   * behind and two streaks of wind either side of it.
   */
  private[projectiles] def drawWindPinwheel(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 37) * 0.35
    computeAllDynamics(proj, 0.6f, 0.95f, 0.88f, phase)
    val a = dynAlpha
    val ds = Math.min(dynScale, 1.3f)
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val px = -ndy; val py = ndx

    // Her ribbon, streaming back
    var i = 0
    while (i < 9) {
      val t = i / 8f
      val w = Math.sin(phase * 1.3 - t * 5.0).toFloat * 5f * ds * t
      _wx(i) = sx - ndx * (6f + t * 60f) * ds + px * w; _wy(i) = sy - ndy * (6f + t * 60f) * ds + py * w
      _pvW(i) = (4.6f * (1f - t) + 0.6f) * ds + 2.2f; _pvA(i) = 0.5f * a * (1f - t)
      i += 1
    }
    sb.strokePolylineVar(_wx, _wy, _pvW, _pvA, 9, 0.03f, 0.22f, 0.22f)
    i = 0
    while (i < 9) { val t = i / 8f; _pvW(i) = (4.6f * (1f - t) + 0.6f) * ds; _pvA(i) = 0.92f * a * (1f - t); i += 1 }
    sb.strokePolylineVar(_wx, _wy, _pvW, _pvA, 9, 0.16f, 0.72f, 0.66f)
    // Streaks of wind either side
    var side = -1
    while (side <= 1) {
      fadeLine(sb, sx - ndx * 9f * ds + px * side * 9f * ds, sy - ndy * 9f * ds + py * side * 9f * ds,
        sx - ndx * 44f * ds + px * side * 12f * ds, sy - ndy * 44f * ds + py * side * 12f * ds,
        2.6f * ds, 0.92f, 1f, 0.98f, 0.55f * a, 4)
      side += 2
    }

    // The blades
    val spin = phase * 0.95
    val R = 21f * ds
    sb.fillOvalSoft(sx, sy, R * 1.5f * dynGlow, R * 1.5f * ISO_Y * dynGlow, 0.6f, 0.95f, 0.88f, 0.22f * a, 0f, 16)
    var blade = 0
    while (blade < 3) {
      val base = spin + blade * (2 * Math.PI / 3)
      // The smear of its spin, behind its tip
      val tipA = (base + 1.0).toFloat
      sb.fillArcBand(sx, sy, R * 0.55f, R * 0.55f * ISO_Y, R * 1.02f, R * 1.02f * ISO_Y, tipA - 1.3f, 1.3f, 8,
        0.75f, 1f, 0.95f, 0f, 0.32f * a)
      // Leading edge out to the tip, trailing edge back to the hub: a swept sickle
      var k = 0
      while (k < 6) {
        val t = k / 5f
        val rho = mix(3f * ds, R, t)
        val thL = base + t * 1.0
        val thT = base + t * 1.0 - (1f - t) * 1.25f
        _wx(k) = sx + Math.cos(thL).toFloat * rho; _wy(k) = sy + Math.sin(thL).toFloat * rho * ISO_Y
        _wx(11 - k) = sx + Math.cos(thT).toFloat * rho * (0.86f + 0.14f * t)
        _wy(11 - k) = sy + Math.sin(thT).toFloat * rho * (0.86f + 0.14f * t) * ISO_Y
        k += 1
      }
      sb.strokePolygon(_wx, _wy, 12, 2.6f, 0.03f, 0.26f, 0.26f, 0.8f * a)
      sb.fillRibbon(_wx, _wy, 12, 0.5f, 0.9f, 0.82f, 0.95f * a)
      sb.strokePolyline(_wx, _wy, 6, 2.2f, 1f, 1f, 1f, 0.95f * a)
      blade += 1
    }
    sb.fillOval(sx, sy, 5.2f * ds, 3.8f * ds, 0.03f, 0.26f, 0.26f, 0.85f * a, 12)
    sb.fillOval(sx, sy, 3.6f * ds, 2.5f * ds, 1f, 1f, 1f, a, 12)
  }

  /**
   * The Windwalker's gust: curls of wind tumbling forward in a flurry — one big one at the front,
   * two smaller behind it — over a faint breath of mint, with two leaves caught up in it.
   */
  private[projectiles] def drawWindSwirl(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 41) * 0.35
    computeAllDynamics(proj, 0.8f, 1f, 0.94f, phase)
    val a = dynAlpha
    val ds = Math.min(dynScale, 1.3f)
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val px = -ndy; val py = ndx
    val cx = sx - ndx * 15f * ds; val cy = sy - ndy * 15f * ds

    sb.fillOvalSoft(cx, cy, 26f * ds, 17f * ds, 0.7f, 1f, 0.92f, 0.2f * a, 0f, 16)
    // Streaks trailing it
    var side = -1
    while (side <= 1) {
      fadeLine(sb, cx - ndx * 10f * ds + px * side * 8f * ds, cy - ndy * 10f * ds + py * side * 8f * ds,
        cx - ndx * 40f * ds + px * side * 10f * ds, cy - ndy * 40f * ds + py * side * 10f * ds,
        2.4f * ds, 0.9f, 1f, 0.96f, 0.5f * a, 4)
      side += 2
    }

    // Leaves caught in it, going round
    var l = 0
    while (l < 2) {
      val ang = phase * 0.55 + l * Math.PI
      val lx = cx + Math.cos(ang).toFloat * 19f * ds; val ly = cy + Math.sin(ang).toFloat * 11f * ds - 3f * ds
      val tumble = phase * 1.1 + l * 1.7
      val w = 0.45f + 0.55f * Math.abs(Math.cos(tumble).toFloat)
      leaf(sb, lx, ly, Math.cos(tumble * 0.7).toFloat, Math.sin(tumble * 0.7).toFloat, 6.2f * ds, 6.2f * ds * w,
        0.46f, 0.8f, 0.36f, 0.95f * a)
      l += 1
    }

    // The curls
    val breathe = 1f + 0.08f * Math.sin(phase * 0.9).toFloat
    curlFrame(sx, sy, ndx, ndy)
    curl(sb, -3f * ds, -2f * ds, 9f * ds * breathe, 34f * ds, 4.2f * ds, 0.9f, 1f, 0.96f, 0.04f, 0.3f, 0.28f, a)
    curl(sb, -17f * ds, -13f * ds, 6.4f * ds * breathe, 26f * ds, 3.4f * ds, 0.9f, 1f, 0.96f, 0.04f, 0.3f, 0.28f, 0.95f * a)
    curl(sb, -27f * ds, 7f * ds, 5.6f * ds * breathe, 22f * ds, 3.2f * ds, 0.9f, 1f, 0.96f, 0.04f, 0.3f, 0.28f, 0.9f * a)
  }

  // ── The Cloudrunner ──
  // A cloud is inked puffs, their undersides shaded blue-grey and lit white across the top: white
  // all over, it was only its ink on snow.
  private val _pfX = new Array[Float](9)
  private val _pfY = new Array[Float](9)
  private val _pfR = new Array[Float](9)

  private def cloud(sb: ShapeBatch, n: Int, a: Float): Unit = {
    var i = 0
    while (i < n) { sb.fillOval(_pfX(i), _pfY(i), _pfR(i) + 2.3f, _pfR(i) * 0.86f + 2.3f, 0.15f, 0.22f, 0.4f, 0.9f * a, 16); i += 1 }
    i = 0
    while (i < n) { sb.fillOval(_pfX(i), _pfY(i), _pfR(i), _pfR(i) * 0.86f, 0.54f, 0.64f, 0.84f, a, 16); i += 1 }
    i = 0
    while (i < n) {
      sb.fillOval(_pfX(i) - _pfR(i) * 0.08f, _pfY(i) - _pfR(i) * 0.16f, _pfR(i) * 0.84f, _pfR(i) * 0.68f, 0.86f, 0.91f, 0.99f, a, 14)
      i += 1
    }
    i = 0
    while (i < n) {
      sb.fillOval(_pfX(i) - _pfR(i) * 0.18f, _pfY(i) - _pfR(i) * 0.3f, _pfR(i) * 0.56f, _pfR(i) * 0.42f, 1f, 1f, 1f, a, 12)
      i += 1
    }
  }

  // The cloudlet's puffs: how far behind the hitbox, how far across, and how big
  private val LET_B = Array(6f, 11.5f, 12.5f, 17.5f, 22f)
  private val LET_L = Array(0f, -4.5f, 4.2f, -1f, 2f)
  private val LET_R = Array(7.6f, 6f, 5.6f, 5f, 3.8f)

  /**
   * The Cloudrunner's wind blade: a cloudlet streaking along, puffs big at its front and small at
   * its back, on a pair of contrails, with the end of her yellow scarf flying off it and puffs of
   * it shed behind.
   */
  private[projectiles] def drawCloudlet(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 43) * 0.35
    computeAllDynamics(proj, 0.9f, 0.95f, 1f, phase)
    val a = dynAlpha
    val ds = Math.min(dynScale, 1.3f)
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val px = -ndy; val py = ndx

    // Contrails, from its flanks
    var side = -1
    while (side <= 1) {
      val bx = sx - ndx * 14f * ds + px * side * 4.5f * ds; val by = sy - ndy * 14f * ds + py * side * 4.5f * ds
      fadeLine(sb, bx, by, bx - ndx * 60f * ds, by - ndy * 60f * ds, 3.6f * ds, 0.2f, 0.28f, 0.46f, 0.3f * a, 5)
      fadeLine(sb, bx, by, bx - ndx * 60f * ds, by - ndy * 60f * ds, 2.4f * ds, 1f, 1f, 1f, 0.8f * a, 5)
      side += 2
    }
    // Puffs shed behind, shrinking away
    var k = 0
    while (k < 3) {
      val t = ((tick * 0.05 + k / 3.0 + proj.id * 0.29) % 1.0).toFloat
      val b = (24f + t * 30f) * ds; val lat = hash2(proj.id, k) * 5f * ds
      val r = 3.6f * ds * (1f - t)
      sb.fillOval(sx - ndx * b + px * lat, sy - ndy * b + py * lat, r + 1.4f, r * 0.86f + 1.4f, 0.2f, 0.28f, 0.46f, 0.5f * (1f - t) * a, 10)
      sb.fillOval(sx - ndx * b + px * lat, sy - ndy * b + py * lat, r, r * 0.86f, 1f, 1f, 1f, 0.85f * (1f - t) * a, 10)
      k += 1
    }
    // The scarf's end, flying off its top
    var i = 0
    while (i < 8) {
      val t = i / 7f
      val w = Math.sin(phase * 1.6 - t * 5.5).toFloat * 4f * ds * t
      _wx(i) = sx - ndx * (10f + t * 34f) * ds + px * w; _wy(i) = sy - ndy * (10f + t * 34f) * ds + py * w - 5f * ds
      _pvW(i) = (3.4f * (1f - t) + 0.5f) * ds + 2f; _pvA(i) = 0.6f * a * (1f - t)
      i += 1
    }
    sb.strokePolylineVar(_wx, _wy, _pvW, _pvA, 8, 0.35f, 0.22f, 0.02f)
    i = 0
    while (i < 8) { val t = i / 7f; _pvW(i) = (3.4f * (1f - t) + 0.5f) * ds; _pvA(i) = a * (1f - t * 0.8f); i += 1 }
    sb.strokePolylineVar(_wx, _wy, _pvW, _pvA, 8, 1f, 0.84f, 0.26f)

    // The cloudlet
    sb.fillOvalSoft(sx - ndx * 12f * ds, sy - ndy * 12f * ds, 24f * ds, 18f * ds, 0.85f, 0.92f, 1f, 0.18f * a, 0f, 14)
    i = 0
    while (i < 5) {
      val bob = Math.sin(phase * 0.8 + i * 1.3).toFloat * 0.5f * ds
      _pfX(i) = sx - ndx * LET_B(i) * ds + px * LET_L(i) * 0.8f * ds
      _pfY(i) = sy - ndy * LET_B(i) * ds + py * LET_L(i) * 0.8f * ds + bob
      _pfR(i) = LET_R(i) * ds
      i += 1
    }
    cloud(sb, 5, a)
  }

  /**
   * The Cloudrunner's gust: a billow of cloud rolling forward, its puffs churning round and swelling,
   * with a curl of wind off either flank and wisps of it streaming back.
   */
  private[projectiles] def drawCloudPuff(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 47) * 0.35
    computeAllDynamics(proj, 0.9f, 0.95f, 1f, phase)
    val a = dynAlpha
    val ds = Math.min(dynScale, 1.3f)
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val px = -ndy; val py = ndx
    val cx = sx - ndx * 16f * ds; val cy = sy - ndy * 16f * ds

    // Wisps streaming back
    var w = 0
    while (w < 3) {
      val lat = (w - 1) * 9f * ds
      var i = 0
      while (i < 6) {
        val t = i / 5f
        val sway = Math.sin(phase * 1.3 - t * 4.0 + w * 2.0).toFloat * 3f * ds * t
        _wx(i) = cx - ndx * (12f + t * 32f) * ds + px * (lat + sway); _wy(i) = cy - ndy * (12f + t * 32f) * ds + py * (lat + sway)
        _pvW(i) = (6f * (1f - t) + 0.5f) * ds; _pvA(i) = 0.75f * a * (1f - t)
        i += 1
      }
      sb.strokePolylineVar(_wx, _wy, _pvW, _pvA, 6, 0.96f, 0.98f, 1f)
      w += 1
    }

    // The billow: puffs churning round a big one in the middle
    sb.fillOvalSoft(cx, cy, 30f * ds, 22f * ds, 0.85f, 0.92f, 1f, 0.2f * a, 0f, 16)
    val churn = phase * 0.16
    var i = 0
    while (i < 7) {
      val ang = churn + i * (2 * Math.PI / 7)
      _pfX(i) = cx + Math.cos(ang).toFloat * 11f * ds
      _pfY(i) = cy + Math.sin(ang).toFloat * 7f * ds
      _pfR(i) = (7.2f + 1.6f * Math.sin(phase * 0.7 + i * 1.9).toFloat) * ds
      i += 1
    }
    _pfX(7) = cx; _pfY(7) = cy - 2f * ds; _pfR(7) = 10.5f * ds
    cloud(sb, 8, a)

    // A curl of wind off each flank
    curlFrame(cx, cy, ndx, ndy)
    curl(sb, 4f * ds, 14f * ds, 4.8f * ds, 20f * ds, 2.6f * ds, 1f, 1f, 1f, 0.2f, 0.28f, 0.46f, 0.95f * a)
    curl(sb, 1f * ds, -15f * ds, 4.4f * ds, 18f * ds, 2.4f * ds, 1f, 1f, 1f, 0.2f, 0.28f, 0.46f, 0.9f * a)
  }

  // ── The Hawk ──

  /**
   * The Hawk's gust: a whirlwind, a funnel of slate-tinted wind standing on the ground behind the hitbox and
   * leaning back from the way it goes, its rings turning, with four of the Hawk's slate feathers
   * spiralling up it and dust kicked up round its foot. The feathers behind the funnel go before
   * its wind, the ones in front after. (A ring of feathers lying on the ground, tried first, was a
   * wreath.)
   */
  private[projectiles] def drawFeatherGust(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 53) * 0.35
    computeAllDynamics(proj, 0.86f, 0.93f, 1f, phase)
    val a = dynAlpha
    val ds = Math.min(dynScale, 1.3f)
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val px = -ndy; val py = ndx
    val bx = sx - ndx * 11f * ds; val by = sy - ndy * 11f * ds
    val H = 34f * ds
    val lean = 7f * ds
    val spin = phase * 0.9

    // Streaks trailing its foot, and a breath of air round it
    var side = -1
    while (side <= 1) {
      fadeLine(sb, bx - ndx * 8f * ds + px * side * 8f * ds, by - ndy * 8f * ds + py * side * 8f * ds,
        bx - ndx * 38f * ds + px * side * 10f * ds, by - ndy * 38f * ds + py * side * 10f * ds,
        2.4f * ds, 0.9f, 0.95f, 1f, 0.5f * a, 4)
      side += 2
    }
    sb.fillOvalSoft(bx - ndx * lean * 0.5f, by - H * 0.5f, 20f * ds * dynGlow, 26f * ds * dynGlow, 0.8f, 0.88f, 1f,
      0.22f * a, 0f, 16)
    // Dust round its foot
    var d = 0
    while (d < 5) {
      val ang = spin * 1.3 + d * 1.26
      val rr = (9f + 3f * Math.sin(phase + d).toFloat) * ds
      sb.fillOval(bx + Math.cos(ang).toFloat * rr, by + Math.sin(ang).toFloat * rr * 0.4f,
        2.2f * ds, 1.8f * ds, 0.62f, 0.56f, 0.48f, 0.6f * a, 6)
      d += 1
    }

    var pass = 0
    while (pass < 3) {
      if (pass == 1) {
        // The funnel: a body of wind, then rings widening up it, each a lit arc across its front
        // over an inked one, the back of each ring fainter, all turning
        _wx(0) = bx - 3.5f * ds; _wy(0) = by
        _wx(1) = bx + 3.5f * ds; _wy(1) = by
        _wx(2) = bx - ndx * lean + 15f * ds; _wy(2) = by - H
        _wx(3) = bx - ndx * lean - 15f * ds; _wy(3) = by - H
        sb.fillPolygon(_wx, _wy, 4, 0.46f, 0.58f, 0.82f, 0.5f * a)
        var k = 0
        while (k < 5) {
          val t = k / 4f
          val rcx = bx - ndx * lean * t; val rcy = by - H * t
          val rr = mix(3.5f, 15f, t * t * 0.5f + t * 0.5f) * ds
          val st = (spin * (1.6f - t * 0.5f) + k * 0.9f).toFloat
          sb.strokeArc(rcx, rcy, rr, rr * 0.34f, st + 3.14f, 2.4f, 2.2f, 0.42f, 0.54f, 0.78f, 0.6f * a, 10)
          sb.strokeArc(rcx, rcy, rr, rr * 0.34f, st, 2.6f, 5.8f, 0.08f, 0.12f, 0.26f, 0.8f * a, 10)
          sb.strokeArc(rcx, rcy, rr, rr * 0.34f, st, 2.6f, 3.4f, 0.62f, 0.73f, 0.93f, a, 10)
          sb.strokeArc(rcx, rcy, rr, rr * 0.34f, st + 0.5f, 1.2f, 1.2f, 1f, 1f, 1f, 0.85f * a, 6)
          k += 1
        }
      } else {
        // Feathers spiralling up it: the far half of their turn before the wind, the near half after
        var f = 0
        while (f < 4) {
          val up = ((tick * 0.012 + f * 0.25 + proj.id * 0.13) % 1.0).toFloat
          val ang = spin * 1.2 + f * (Math.PI / 2)
          val sa = Math.sin(ang).toFloat
          if ((sa >= 0f) == (pass == 2)) {
            val ca = Math.cos(ang).toFloat
            val rr = mix(6f, 17f, up) * ds
            val fxp = bx - ndx * lean * up + ca * rr; val fyp = by - H * up + sa * rr * 0.34f
            val tumble = phase * 1.4 + f * 1.3
            val roll = 0.5f + 0.5f * Math.abs(Math.cos(tumble).toFloat)
            val fade = Math.min(1f, (1f - up) * 4f) * Math.min(1f, up * 6f)
            Songs.feather(sb, fxp, fyp, -sa, ca * 0.6f, 6.4f * ds, 5.6f * ds * roll,
              0.40f, 0.50f, 0.70f, 0.94f, 0.9f, 0.78f, 0.95f * a * fade)
          }
          f += 1
        }
      }
      pass += 1
    }
  }

  // ── The Raptor ──
  // An eagle seen from above, +x its beak, +y across it toward its right wing, in units of about a
  // third of its length. Each wing is an arm and four primaries splayed at its end; the body, the
  // fanned tail, the white head and the beak lie over them.
  private val EAGLE_BODY_X = Array(0.75f, 0.1f, -0.62f, -0.78f, -0.62f, 0.1f, 0.75f)
  private val EAGLE_BODY_Y = Array(-0.2f, -0.27f, -0.18f, 0f, 0.18f, 0.27f, 0.2f)
  private val EAGLE_TAIL_X = Array(-0.62f, -1.25f, -1.36f, -1.25f, -0.62f)
  private val EAGLE_TAIL_Y = Array(-0.14f, -0.34f, 0f, 0.34f, 0.14f)
  private val EAGLE_HEAD_X = Array(1.1f, 0.84f, 0.66f, 0.66f, 0.84f, 1.1f)
  private val EAGLE_HEAD_Y = Array(-0.15f, -0.21f, -0.13f, 0.13f, 0.21f, 0.15f)
  private val EAGLE_BEAK_X = Array(1.38f, 1.08f, 1.08f)
  private val EAGLE_BEAK_Y = Array(0f, -0.1f, 0.1f)

  // The eagle's frame: where its local origin is on screen, along it and across it
  private var _eox = 0f; private var _eoy = 0f
  private var _efx = 1f; private var _efy = 0f; private var _eqx = 0f; private var _eqy = 1f

  @inline private def ex(lx: Float, ly: Float): Float = _eox + lx * _efx + ly * _eqx
  @inline private def ey(lx: Float, ly: Float): Float = _eoy + lx * _efy + ly * _eqy

  private def eaglePart(sb: ShapeBatch, xs: Array[Float], ys: Array[Float], n: Int, ink: Boolean,
                        r: Float, g: Float, b: Float, a: Float): Unit = {
    var i = 0
    while (i < n) { _wx(i) = ex(xs(i), ys(i)); _wy(i) = ey(xs(i), ys(i)); i += 1 }
    if (ink) sb.strokePolygon(_wx, _wy, n, 2.2f, 0.26f, 0.16f, 0.06f, 0.75f * a)
    else sb.fillPolygon(_wx, _wy, n, r, g, b, a)
  }

  private val _lxs = new Array[Float](6)
  private val _lys = new Array[Float](6)

  /** The tip of a wing's leading primary, into (_ptX, _ptY): where its vortex streams from. */
  private def wingTip(side: Float, f: Float, sweep: Float): Unit = {
    val tx = 0.22f - sweep * 0.5f + 0.08f - sweep; val ty = 1.8f * f * side
    _ptX = ex(tx, ty); _ptY = ey(tx, ty)
  }

  /** One wing (`side` ±1), its span `f` of full, swept back by `sweep`: the arm, then four
    * primaries splayed off its end. */
  private def eagleWing(sb: ShapeBatch, side: Float, f: Float, sweep: Float, ink: Boolean, a: Float): Unit = {
    val ax0 = 0.22f - sweep * 0.5f; val ay0 = 1.15f * f
    val ax1 = -0.28f - sweep; val ay1 = 1.25f * f
    _lxs(0) = 0.38f; _lys(0) = 0.18f * side
    _lxs(1) = ax0; _lys(1) = ay0 * side
    _lxs(2) = ax1; _lys(2) = ay1 * side
    _lxs(3) = -0.32f; _lys(3) = 0.2f * side
    eaglePart(sb, _lxs, _lys, 4, ink, 0.96f, 0.9f, 0.76f, 0.86f * a)
    var k = 0
    while (k < 4) {
      val t = k / 3f
      val rx = mix(ax0, ax1, t); val ry = mix(ay0, ay1, t)
      val tx = rx + 0.08f - k * 0.13f - sweep; val ty = (1.25f + 0.55f - k * 0.07f) * f
      _lxs(0) = rx + 0.07f; _lys(0) = ry * side
      _lxs(1) = tx + 0.035f; _lys(1) = ty * side
      _lxs(2) = tx - 0.035f; _lys(2) = ty * side
      _lxs(3) = rx - 0.07f; _lys(3) = ry * side
      eaglePart(sb, _lxs, _lys, 4, ink, 0.52f, 0.37f, 0.22f, 0.92f * a)
      k += 1
    }
  }

  /**
   * The Raptor's gust: an eagle of wind diving along it, beak first on the hitbox, its wings beating
   * slowly — a golden spirit of one, white-headed, the primaries brown — with a vortex of wind
   * streaming off each wingtip.
   */
  private[projectiles] def drawSpiritEagle(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 59) * 0.35
    computeAllDynamics(proj, 1f, 0.92f, 0.7f, phase)
    val a = dynAlpha
    val ds = Math.min(dynScale, 1.3f)
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val s = 15f * ds
    val flap = Math.sin(phase * 0.75).toFloat
    val f = 0.8f + 0.2f * flap
    val sweep = 0.2f * (1f - f)
    _eox = sx - ndx * 1.38f * s; _eoy = sy - ndy * 1.38f * s
    _efx = ndx * s; _efy = ndy * s
    _eqx = -ndy * s * 0.8f; _eqy = ndx * s * 0.8f

    sb.fillOvalSoft(ex(-0.1f, 0f), ey(-0.1f, 0f), 28f * ds * dynGlow, 20f * ds * dynGlow, 1f, 0.88f, 0.55f, 0.24f * a, 0f, 16)
    // Vortices off the wingtips
    var side = -1
    while (side <= 1) {
      wingTip(side, f, sweep)
      val tx = _ptX; val ty = _ptY
      fadeLine(sb, tx, ty, tx - ndx * 34f * ds, ty - ndy * 34f * ds, 3.2f * ds, 1f, 1f, 1f, 0.65f * a, 4)
      side += 2
    }
    // Ink round everything, then everything
    side = -1
    while (side <= 1) { eagleWing(sb, side, f, sweep, ink = true, a); side += 2 }
    eaglePart(sb, EAGLE_TAIL_X, EAGLE_TAIL_Y, 5, ink = true, 0f, 0f, 0f, a)
    eaglePart(sb, EAGLE_BODY_X, EAGLE_BODY_Y, 7, ink = true, 0f, 0f, 0f, a)
    eaglePart(sb, EAGLE_HEAD_X, EAGLE_HEAD_Y, 6, ink = true, 0f, 0f, 0f, a)
    eaglePart(sb, EAGLE_BEAK_X, EAGLE_BEAK_Y, 3, ink = true, 0f, 0f, 0f, a)
    side = -1
    while (side <= 1) { eagleWing(sb, side, f, sweep, ink = false, a); side += 2 }
    eaglePart(sb, EAGLE_TAIL_X, EAGLE_TAIL_Y, 5, ink = false, 0.9f, 0.84f, 0.72f, 0.9f * a)
    eaglePart(sb, EAGLE_BODY_X, EAGLE_BODY_Y, 7, ink = false, 0.66f, 0.5f, 0.32f, 0.92f * a)
    eaglePart(sb, EAGLE_HEAD_X, EAGLE_HEAD_Y, 6, ink = false, 1f, 1f, 0.98f, a)
    eaglePart(sb, EAGLE_BEAK_X, EAGLE_BEAK_Y, 3, ink = false, 1f, 0.8f, 0.2f, a)
  }
}
