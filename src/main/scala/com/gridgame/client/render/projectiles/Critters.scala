package com.gridgame.client.render.projectiles

import com.gridgame.common.model.Projectile
import com.gridgame.client.gl.ShapeBatch
import ProjectileKit._
import Glyphs._

/**
 * The beasts' own, drawn as theirs: the Jellyfish's sting as a little jellyfish of itself, crackling;
 * the Spider's web, spun and flung with the spider riding it; the Mantis's strike, its head and its
 * raptorial arms lunging; the Gorilla's ground pound, turf thrown up in a wave with its knuckles
 * printed in the ground behind. They were an orb with sparks, a white ball of strands, a void orb, and
 * a brown ring on the ground.
 */
private[render] object Critters {

  // ── The Jellyfish ──
  private final val BELL_N = 13
  private val _bell = new Array[Float](BELL_N * 2)
  private val _tentX = new Array[Float](30)
  private val _tentY = new Array[Float](30)

  /**
   * The Jellyfish's sting: a little jellyfish, pink and spotted like the Jellyfish, swimming bell first —
   * its bell squeezing and opening as it swims — its tentacles and frilled arms trailing, and electricity
   * crackling between them.
   */
  private[projectiles] def drawBabyJelly(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 37) * 0.35
    computeAllDynamics(proj, 0.95f, 0.55f, 0.85f, phase)
    val a = dynAlpha
    val ds = Math.min(dynScale, 1.3f)
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val k = 1.35f * ds
    val pulse = 0.5f + 0.5f * Math.sin(phase * 1.6).toFloat
    val bx = sx - ndx * 8f * k; val by = sy - ndy * 8f * k

    sb.fillOvalSoft(bx, by, 26f * ds * dynGlow, 24f * ds * dynGlow, 0.95f, 0.5f, 0.9f, 0.24f * a, 0f, 16)
    sb.fillOvalSoft(bx - ndx * 14f * ds, by - ndy * 14f * ds, 20f * ds, 18f * ds, 0.4f, 0.9f, 1f, 0.16f * a, 0f, 14)
    headingFrame(bx, by, ndx, ndy, k)
    // Its tentacles, trailing and waving, five of them
    var tt = 0
    while (tt < 5) {
      val lat = (-1f + tt * 0.5f) * 8f
      var i = 0
      while (i < 6) {
        val u = i / 5f
        val lx = -0.5f - u * 30f
        val ly = lat * (1f - 0.35f * u) + Math.sin(phase * 2.0 - u * 6.0 + tt * 1.3).toFloat * 2.6f * u
        _pvX(i) = fx(lx, ly); _pvY(i) = fy(lx, ly)
        _tentX(tt * 6 + i) = _pvX(i); _tentY(tt * 6 + i) = _pvY(i)
        _pvW(i) = (1.7f * (1f - u) + 0.5f) * k + 1.4f; _pvA(i) = 0.6f * a * (1f - u)
        i += 1
      }
      sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, 6, 0.35f, 0.06f, 0.3f)
      i = 0
      while (i < 6) { val u = i / 5f; _pvW(i) = (1.7f * (1f - u) + 0.5f) * k; _pvA(i) = a * (1f - u * 0.8f); i += 1 }
      sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, 6, 0.98f, 0.62f, 0.9f)
      tt += 1
    }
    // Its frilled arms, down the middle
    var arm = -1f
    while (arm <= 1f) {
      var i = 0
      while (i < 6) {
        val u = i / 5f
        val lx = -1f - u * 20f; val ly = arm * 2.4f + Math.sin(phase * 2.6 - u * 8.0 + arm).toFloat * 1.8f
        _pvX(i) = fx(lx, ly); _pvY(i) = fy(lx, ly)
        _pvW(i) = (3.4f * (1f - u) + 0.8f) * k; _pvA(i) = 0.9f * a * (1f - u * 0.7f)
        i += 1
      }
      sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, 6, 1f, 0.82f, 0.95f)
      arm += 2f
    }
    // Electricity crackling between the tentacles, struck again twenty times a second
    val epoch = (tick + proj.id * 5) / 3
    var arc = 0
    while (arc < 2) {
      val t0 = (hash01(proj.id * 31 + arc, epoch) * 3.99f).toInt
      val u0 = 1 + (hash01(proj.id * 37 + arc, epoch) * 3f).toInt
      sparkArc(sb, _tentX(t0 * 6 + u0), _tentY(t0 * 6 + u0), _tentX((t0 + 1) * 6 + u0), _tentY((t0 + 1) * 6 + u0), 4,
        3f * ds, proj.id * 13 + epoch * 7 + arc, 2.2f, 0.55f, 0.95f, 1f, a)
      arc += 1
    }
    // The bell: a dome toward where it swims, squeezing and opening
    val rxB = 7.4f + 1.6f * pulse; val ryB = 11.2f - 1.8f * pulse
    var i = 0
    while (i < BELL_N) {
      val th = -Math.PI / 2 + i * (Math.PI / (BELL_N - 1))
      _bell(i * 2) = Math.cos(th).toFloat * rxB; _bell(i * 2 + 1) = Math.sin(th).toFloat * ryB
      i += 1
    }
    var n = pts(_bell)
    sb.strokePolygon(_gx, _gy, n, 2.8f, 0.35f, 0.06f, 0.3f, 0.92f * a)
    sb.fillPolygon(_gx, _gy, n, 0.96f, 0.56f, 0.86f, 0.92f * a)
    // Scallops along its rim
    var sc = 0
    while (sc < 4) {
      val ly = -ryB + (sc + 0.5f) * (2f * ryB / 4f)
      n = ovalPts(-0.3f, ly, 1.4f, ryB / 4.2f, 0f, 8)
      sb.fillPolygon(_gx, _gy, n, 0.85f, 0.4f, 0.75f, a)
      sc += 1
    }
    // Lit inside, spotted like the Jellyfish
    sb.fillOvalSoft(fx(rxB * 0.42f, 0f), fy(rxB * 0.42f, 0f), rxB * 0.6f * k, ryB * 0.62f * k, 1f, 0.82f, 0.96f, 0.85f * a, 0f, 14)
    gDot(sb, rxB * 0.5f, -ryB * 0.42f, 1.5f, 1.5f, 0.98f, 0.88f, 0.4f, a, 8)
    gDot(sb, rxB * 0.66f, ryB * 0.22f, 1.3f, 1.3f, 0.98f, 0.88f, 0.4f, a, 8)
    gDot(sb, rxB * 0.25f, ryB * 0.55f, 1.1f, 1.1f, 0.98f, 0.88f, 0.4f, a, 8)
    sb.fillOval(bx - 3f * k, by - 4f * k, 2.2f * k, 1.4f * k, 1f, 1f, 1f, 0.8f * a, 8)
  }

  // ── The Spider ──
  private final val WEB_SPOKES = 8
  private val _web = new Array[Float](WEB_SPOKES * 4)

  /** A ring of the web at radius `r`: a point on each spoke and one sagging in between, in _web. */
  private def webRing(r: Float, sag: Float): Int = {
    var s = 0
    while (s < WEB_SPOKES) {
      val th = s * (2 * Math.PI / WEB_SPOKES)
      val th2 = th + Math.PI / WEB_SPOKES
      _web(s * 4) = Math.cos(th).toFloat * r; _web(s * 4 + 1) = Math.sin(th).toFloat * r
      _web(s * 4 + 2) = Math.cos(th2).toFloat * r * sag; _web(s * 4 + 3) = Math.sin(th2).toFloat * r * sag
      s += 1
    }
    WEB_SPOKES * 2
  }

  /**
   * The Spider's web shot: a web, spun and flung — spokes and rings of silk, dew on it, turning slowly as
   * it flies, a dragline paying out behind it — with the little spider riding at its hub, purple and black
   * with the red mark on its back, as the Spider has.
   */
  private[projectiles] def drawSpiderWeb(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 29) * 0.35
    computeAllDynamics(proj, 0.95f, 0.95f, 0.92f, phase)
    val a = dynAlpha
    val ds = Math.min(dynScale, 1.3f)
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val k = 1.4f * ds
    val dir = if (ndx < 0f) -1f else 1f
    val cx = sx; val cy = sy - 2f * ds

    // The dragline paying out behind it
    fadeLine(sb, cx, cy, cx - ndx * 50f * ds, cy - ndy * 50f * ds, 3.4f * ds, 0.2f, 0.18f, 0.25f, 0.4f * a, 4)
    fadeLine(sb, cx, cy, cx - ndx * 50f * ds, cy - ndy * 50f * ds, 1.6f * ds, 0.97f, 0.97f, 0.95f, 0.85f * a, 4)
    sb.fillOvalSoft(cx, cy, 24f * ds * dynGlow, 22f * ds * dynGlow, 1f, 1f, 1f, 0.16f * a, 0f, 16)
    frame(cx, cy, (phase * 0.25).toFloat * dir, k, 1f, 0.9f)
    // The web: every thread inked under its silk, so it reads on snow
    var pass = 0
    while (pass < 2) {
      val w = if (pass == 0) 2.6f else 1.2f
      val r = if (pass == 0) 0.2f else 0.97f; val g = if (pass == 0) 0.16f else 0.97f; val b = if (pass == 0) 0.26f else 0.95f
      val al = if (pass == 0) 0.55f * a else 0.95f * a
      var s = 0
      while (s < WEB_SPOKES) {
        val th = s * (2 * Math.PI / WEB_SPOKES)
        gLine(sb, 0f, 0f, Math.cos(th).toFloat * 16.5f, Math.sin(th).toFloat * 16.5f, w * (if (pass == 0) 0.6f else 0.55f), r, g, b, al)
        s += 1
      }
      var ring = 0
      while (ring < 3) {
        val m = webRing(5f + ring * 4.8f, 0.86f)
        var q = 0
        while (q < m) { _gx(q) = fx(_web(q * 2), _web(q * 2 + 1)); _gy(q) = fy(_web(q * 2), _web(q * 2 + 1)); q += 1 }
        sb.strokePolygon(_gx, _gy, m, w * k * 0.5f, r, g, b, al)
        ring += 1
      }
      pass += 1
    }
    // Dew on it
    var d = 0
    while (d < 4) {
      val s = (hash01(proj.id + 3, d) * 7.99f).toInt
      val rr = 5f + ((d + proj.id) % 3) * 4.8f
      val th = s * (2 * Math.PI / WEB_SPOKES)
      val lx = Math.cos(th).toFloat * rr; val ly = Math.sin(th).toFloat * rr
      gDot(sb, lx, ly, 1.1f, 1.1f, 0.85f, 0.95f, 1f, a, 6)
      gDot(sb, lx - 0.35f, ly - 0.35f, 0.4f, 0.4f, 1f, 1f, 1f, a, 4)
      d += 1
    }
    // The spider riding it, at its hub
    frame(cx, cy, 0f, k)
    val step = Math.sin(phase * 2.4).toFloat * 0.5f
    var side = -1f
    while (side <= 1f) {
      var l = 0
      while (l < 4) {
        val ry0 = -1.6f + l * 1f
        val kx = side * (3.6f + l * 0.2f); val ky = -3.4f + l * 2f + (if ((l & 1) == 0) step else -step)
        val fx2 = side * (5.2f - l * 0.1f); val fy2 = -1.6f + l * 2.6f
        gLine(sb, side * 0.8f, ry0, kx, ky, 1.5f, 0.08f, 0.05f, 0.1f, a)
        gLine(sb, kx, ky, fx2, fy2, 1.2f, 0.08f, 0.05f, 0.1f, a)
        l += 1
      }
      side += 2f
    }
    var n = ovalPts(0f, 1.6f, 2.8f, 3.2f, 0f, 12)
    sb.strokePolygon(_gx, _gy, n, 1.6f, 0.95f, 0.95f, 0.95f, 0.6f * a)
    sb.fillPolygon(_gx, _gy, n, 0.22f, 0.12f, 0.3f, a)
    n = ovalPts(0f, -1.8f, 1.9f, 1.7f, 0f, 10)
    sb.fillPolygon(_gx, _gy, n, 0.16f, 0.09f, 0.22f, a)
    _gx(0) = fx(-1f, 0.8f); _gy(0) = fy(-1f, 0.8f)
    _gx(1) = fx(1f, 0.8f); _gy(1) = fy(1f, 0.8f)
    _gx(2) = fx(0f, 1.9f); _gy(2) = fy(0f, 1.9f)
    sb.fillPolygon(_gx, _gy, 3, 0.9f, 0.15f, 0.2f, a)
    _gx(0) = fx(-1f, 3f); _gy(0) = fy(-1f, 3f)
    _gx(1) = fx(1f, 3f); _gy(1) = fy(1f, 3f)
    _gx(2) = fx(0f, 1.9f); _gy(2) = fy(0f, 1.9f)
    sb.fillPolygon(_gx, _gy, 3, 0.9f, 0.15f, 0.2f, a)
    gDot(sb, -0.6f, -2.2f, 0.4f, 0.4f, 1f, 0.3f, 0.35f, a, 4)
    gDot(sb, 0.6f, -2.2f, 0.4f, 0.4f, 1f, 0.3f, 0.35f, a, 4)
  }

  // ── The Mantis ──
  private val MANTIS_HEAD = Array(-9.4f, -4f, -5f, -7.4f, 5f, -7.4f, 9.4f, -4f, 2.2f, 6.4f, -2.2f, 6.4f)
  private val _leaf = new Array[Float](8)

  /** The Mantis's raptorial arm on `side` (±1), struck out by `strike`: `pass` 0 its ink, 1 the arm. */
  private def mantisArm(sb: ShapeBatch, side: Float, strike: Float, pass: Int, a: Float): Unit = {
    val ex = side * (14.6f + 2f * strike); val ey = -6f - 5f * strike
    val hx = side * (11.6f - 4f * strike); val hy = 6.4f - 3f * strike
    val w0 = if (pass == 0) 6.2f else 4.2f
    val c0 = if (pass == 0) 0.06f else 0.42f; val c1 = if (pass == 0) 0.16f else 0.8f; val c2 = if (pass == 0) 0.04f else 0.28f
    gLine(sb, side * 3.4f, 4.4f, ex, ey, w0, c0, c1, c2, a)
    gLine(sb, ex, ey, hx, hy, w0 * 0.75f, c0, c1, c2, a)
    gLine(sb, hx, hy, hx - side * 2.4f, hy - 3f, w0 * 0.55f, c0, c1, c2, a)
    if (pass == 1) {
      // Spines down the inside of the upper arm
      var s = 0
      while (s < 3) {
        val u = 0.3f + s * 0.22f
        val px = side * 3.4f + (ex - side * 3.4f) * u; val py = 4.4f + (ey - 4.4f) * u
        _gx(0) = fx(px - 0.7f, py); _gy(0) = fy(px - 0.7f, py)
        _gx(1) = fx(px + 0.7f, py); _gy(1) = fy(px + 0.7f, py)
        _gx(2) = fx(px - side * 1.4f, py + 1.8f); _gy(2) = fy(px - side * 1.4f, py + 1.8f)
        sb.fillPolygon(_gx, _gy, 3, 0.2f, 0.42f, 0.12f, a)
        s += 1
      }
    }
  }

  /**
   * The Mantis's strike: the mantis itself lunging out of its leaf cloak — its triangle of a head, its
   * huge eyes, its feelers swept back, its spined arms snapping open and shut — a green ghost of it a
   * step behind and leaves fluttering in its wake.
   */
  private[projectiles] def drawMantisStrike(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 41) * 0.35
    computeAllDynamics(proj, 0.45f, 0.82f, 0.3f, phase)
    val a = dynAlpha
    val ds = Math.min(dynScale, 1.3f)
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val px = -ndy; val py = ndx
    val k = 1.35f * ds
    val cx = sx; val cy = sy + Math.sin(phase * 0.6).toFloat * 1.2f * ds

    // Leaves fluttering in its wake
    var l = 0
    while (l < 4) {
      val t = loop(tick, 0.035, l / 4.0 + proj.id * 0.17)
      val lat = hash2(proj.id + 5, l) * (6f + 8f * t) * ds
      val lx = cx - ndx * (12f + t * 34f) * ds + px * lat; val ly = cy - ndy * (12f + t * 34f) * ds + py * lat + t * 8f * ds
      val rot = phase * 0.8 + l * 1.7
      val c = Math.cos(rot).toFloat * 4.2f * ds; val s = Math.sin(rot).toFloat * 4.2f * ds
      _leaf(0) = lx + c; _leaf(1) = ly + s
      _leaf(2) = lx - s * 0.4f; _leaf(3) = ly + c * 0.4f
      _leaf(4) = lx - c; _leaf(5) = ly - s
      _leaf(6) = lx + s * 0.4f; _leaf(7) = ly - c * 0.4f
      var q = 0
      while (q < 4) { _gx(q) = _leaf(q * 2); _gy(q) = _leaf(q * 2 + 1); q += 1 }
      sb.strokePolygon(_gx, _gy, 4, 1.6f, 0.06f, 0.2f, 0.05f, 0.8f * (1f - t) * a)
      sb.fillPolygon(_gx, _gy, 4, 0.46f, 0.78f, 0.28f, (1f - t) * a)
      sb.strokeLine(lx - c * 0.8f, ly - s * 0.8f, lx + c * 0.8f, ly + s * 0.8f, 0.8f, 0.25f, 0.5f, 0.15f, (1f - t) * a)
      l += 1
    }
    sb.fillOvalSoft(cx, cy, 26f * ds * dynGlow, 22f * ds * dynGlow, 0.45f, 0.9f, 0.3f, 0.24f * a, 0f, 16)
    val strike = 0.5f + 0.5f * Math.sin(phase * 1.4).toFloat
    // A green ghost of it, a step behind
    frame(cx - ndx * 10f * ds, cy - ndy * 10f * ds, clampF(ndx, -1f, 1f) * 0.12f, k)
    var n = pts(MANTIS_HEAD)
    sb.fillPolygon(_gx, _gy, n, 0.5f, 0.95f, 0.45f, 0.28f * a)
    frame(cx, cy, clampF(ndx, -1f, 1f) * 0.12f, k)
    // Its arms, struck out
    var pass = 0
    while (pass < 2) {
      var side = -1f
      while (side <= 1f) { mantisArm(sb, side, strike, pass, a); side += 2f }
      pass += 1
    }
    // Its feelers, swept back
    var side = -1f
    while (side <= 1f) {
      val sw = Math.sin(phase * 1.1 + side).toFloat * 1.5f
      gLine(sb, side * 2f, -7.2f, side * 4.5f + sw, -14f, 0.9f, 0.1f, 0.25f, 0.06f, a)
      gLine(sb, side * 4.5f + sw, -14f, side * 9f + sw * 1.6f, -17.4f, 0.7f, 0.1f, 0.25f, 0.06f, a)
      side += 2f
    }
    // The head, and its eyes
    n = pts(MANTIS_HEAD)
    sb.strokePolygon(_gx, _gy, n, 2.6f, 0.06f, 0.16f, 0.04f, 0.94f * a)
    sb.fillPolygon(_gx, _gy, n, 0.46f, 0.82f, 0.3f, a)
    sb.fillOvalSoft(fx(0f, 2.6f), fy(0f, 2.6f), 4f * k, 4f * k, 0.3f, 0.6f, 0.2f, 0.5f * a, 0f, 10)
    side = -1f
    while (side <= 1f) {
      n = ovalPts(side * 7.2f, -4.6f, 3.4f, 3.8f, 0f, 14)
      sb.strokePolygon(_gx, _gy, n, 2.2f, 0.06f, 0.16f, 0.04f, 0.94f * a)
      sb.fillPolygon(_gx, _gy, n, 0.82f, 0.95f, 0.38f, a)
      gDot(sb, side * 7.2f + ndx * 1.2f, -4.4f + ndy * 1f, 1.2f, 1.3f, 0.1f, 0.2f, 0.05f, a, 8)
      gDot(sb, side * 7.2f - 1.2f, -5.8f, 0.7f, 0.5f, 1f, 1f, 0.9f, 0.9f * a, 6)
      side += 2f
    }
    gLine(sb, -1.2f, 6.2f, -0.4f, 7.6f, 0.8f, 0.06f, 0.16f, 0.04f, a)
    gLine(sb, 1.2f, 6.2f, 0.4f, 7.6f, 0.8f, 0.06f, 0.16f, 0.04f, a)
  }

  // ── The Gorilla ──
  private final val TURF_N = 7
  private val _turfX = new Array[Float](TURF_N)
  private val _turfY = new Array[Float](TURF_N)
  private val _turfH = new Array[Float](TURF_N)
  private val _order = new Array[Int](TURF_N)

  /**
   * The Gorilla's ground pound: the shock of it running through the ground — a bow of turf slabs thrown
   * up across its path, dirt and clods flying off them, a crack opening along its front — and the
   * Gorilla's knuckles printed in the ground behind it, left, right, where the shock went.
   */
  private[projectiles] def drawGroundPound(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 31) * 0.35
    computeAllDynamics(proj, 0.55f, 0.42f, 0.25f, phase)
    val a = dynAlpha
    val ds = Math.min(dynScale, 1.3f)
    val wl = Math.sqrt(proj.dx * proj.dx + proj.dy * proj.dy).toFloat
    val ux = if (wl > 1e-4f) proj.dx / wl else 1f
    val uy = if (wl > 1e-4f) proj.dy / wl else 0f
    // One world unit along the path and across it, on screen
    val ex = (ux - uy) * 20f; val ey = (ux + uy) * 10f
    val qx = (-uy - ux) * 20f; val qy = (-uy + ux) * 10f
    val rise = Math.min(1f, 0.35f + _lifePct * 6f)

    // The knuckles printed behind it, keyed to the ground so they stay where they were struck
    val along = proj.getX * ux + proj.getY * uy
    val gap = 1.5f
    val k0 = Math.floor(along / gap).toInt
    var j = 0
    while (j < 4) {
      val kk = k0 - j
      val d = along - kk * gap
      if (d > 0.7f && d < 5.5f && d <= proj.getDistanceTraveled) {
        val fade = 1f - d / 5.5f
        val side = if ((kk & 1) == 0) 1f else -1f
        val px0 = sx - ex * d + qx * side * 0.42f; val py0 = sy - ey * d + qy * side * 0.42f
        var m = 0
        while (m < 4) {
          val o = (m - 1.5f) * 0.17f
          val kx = px0 + qx * o + ex * (if (m == 0 || m == 3) -0.06f else 0f)
          val ky = py0 + qy * o + ey * (if (m == 0 || m == 3) -0.06f else 0f)
          sb.fillOval(kx, ky - 1f * ds, 3.8f * ds, 2.3f * ds, 0.86f, 0.78f, 0.58f, 0.65f * fade * a, 10)
          sb.fillOval(kx, ky, 3.6f * ds, 2.1f * ds, 0.18f, 0.12f, 0.06f, 0.92f * fade * a, 10)
          m += 1
        }
      }
      j += 1
    }
    // The crack along its front
    var i = 0
    while (i < TURF_N) {
      val u = -1f + 2f * i / (TURF_N - 1)
      _pvX(i) = sx + qx * u * 1.3f - ex * (u * u * 0.5f - 0.15f) + hash2(proj.id, i) * 1.5f * ds
      _pvY(i) = sy + qy * u * 1.3f - ey * (u * u * 0.5f - 0.15f)
      _pvW(i) = 2.6f * ds * (1f - 0.5f * u * u); _pvA(i) = 0.9f * a * rise
      i += 1
    }
    sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, TURF_N, 0.14f, 0.09f, 0.04f)
    // Dust behind the slabs
    var p = 0
    while (p < 4) {
      val t = loop(tick, 0.04, p / 4.0 + proj.id * 0.19)
      val u = -0.75f + p * 0.5f
      sb.fillOvalSoft(sx + qx * u * 1.1f - ex * (0.4f + t * 0.8f), sy + qy * u * 1.1f - ey * (0.4f + t * 0.8f) - (4f + t * 10f) * ds,
        (10f + 8f * t) * ds, (7f + 5f * t) * ds, 0.72f, 0.6f, 0.42f, 0.6f * (1f - t) * a, 0f, 12)
      p += 1
    }
    // The turf slabs: where each stands, how high it is thrown, drawn far ones first
    i = 0
    while (i < TURF_N) {
      val u = -1f + 2f * i / (TURF_N - 1)
      _turfX(i) = sx + qx * u * 1.25f - ex * u * u * 0.45f
      _turfY(i) = sy + qy * u * 1.25f - ey * u * u * 0.45f
      _turfH(i) = (12f + 8f * hash01(proj.id + 1, i)) * ds * rise * (1f - 0.4f * u * u) *
        (0.8f + 0.2f * Math.sin(phase * 1.5 + i).toFloat)
      _order(i) = i
      i += 1
    }
    i = 1
    while (i < TURF_N) {
      val v = _order(i); var m = i - 1
      while (m >= 0 && _turfY(_order(m)) > _turfY(v)) { _order(m + 1) = _order(m); m -= 1 }
      _order(m + 1) = v
      i += 1
    }
    i = 0
    while (i < TURF_N) {
      val o = _order(i)
      val gx = _turfX(o); val gy = _turfY(o); val h = _turfH(o)
      val s = (6.4f + 2.4f * hash01(proj.id + 2, o)) * ds
      val tilt = hash2(proj.id + 3, o) * 0.4f
      // Its dirt face, from its lifted top down into the ground
      _gx(0) = gx - s; _gy(0) = gy - h + s * 0.3f * (1f - tilt)
      _gx(1) = gx + s; _gy(1) = gy - h + s * 0.3f * (1f + tilt)
      _gx(2) = gx + s * 0.8f; _gy(2) = gy + 1f * ds
      _gx(3) = gx - s * 0.8f; _gy(3) = gy + 1f * ds
      sb.strokePolygon(_gx, _gy, 4, 2.2f, 0.14f, 0.09f, 0.04f, 0.92f * a)
      sb.fillPolygon(_gx, _gy, 4, 0.52f, 0.36f, 0.2f, a)
      // Its grass top, tilted as it is thrown
      _gx(0) = gx - s; _gy(0) = gy - h + s * 0.3f * (1f - tilt) - s * 0.05f
      _gx(1) = gx - s * tilt * 0.3f; _gy(1) = gy - h - s * 0.45f
      _gx(2) = gx + s; _gy(2) = gy - h + s * 0.3f * (1f + tilt) - s * 0.05f
      _gx(3) = gx + s * tilt * 0.3f; _gy(3) = gy - h + s * 0.62f
      sb.strokePolygon(_gx, _gy, 4, 2.2f, 0.08f, 0.16f, 0.04f, 0.92f * a)
      sb.fillPolygon(_gx, _gy, 4, 0.42f, 0.66f, 0.26f, a)
      sb.fillOval(gx - s * 0.25f, gy - h - s * 0.05f, s * 0.42f, s * 0.18f, 0.6f, 0.82f, 0.38f, 0.9f * a, 8)
      i += 1
    }
    // Clods and stones flying up off them
    var c = 0
    while (c < 6) {
      val t = loop(tick, 0.055, c / 6.0 + proj.id * 0.41)
      val u = hash2(proj.id + 8, c)
      val bx = sx + qx * u * 1.1f - ex * t * 0.9f; val by = sy + qy * u * 1.1f - ey * t * 0.9f - Math.sin(t * Math.PI).toFloat * 26f * ds * rise
      val cs = (2.4f - t) * ds + 0.6f
      sb.fillOval(bx, by, cs + 0.9f, cs * 0.85f + 0.9f, 0.14f, 0.09f, 0.04f, 0.85f * (1f - t * 0.5f) * a, 6)
      if ((c & 1) == 0) sb.fillOval(bx, by, cs, cs * 0.85f, 0.5f, 0.36f, 0.2f, (1f - t * 0.5f) * a, 6)
      else sb.fillOval(bx, by, cs, cs * 0.85f, 0.42f, 0.66f, 0.26f, (1f - t * 0.5f) * a, 6)
      c += 1
    }
  }
}
