package com.gridgame.client.render.projectiles

import com.gridgame.common.model.Projectile
import com.gridgame.client.gl.ShapeBatch
import ProjectileKit._

/**
 * What surges along the ground, drawn as whoever set it going: the Tidecaller's breaking wave, the
 * Sandstorm's rolling cloud of dust, the Inferno's living flame, the Scorpion's spray of acid, the
 * Barbarian's fissure splitting the earth. Each of them used to be the same flat crescent in its
 * own colour.
 */
private[render] object Surges {
  private val _sx = new Array[Float](24)
  private val _sy = new Array[Float](24)

  // ── The Tidecaller ──
  // The wave is a ridge of water: a profile along its path — a long back slope rising to the crest,
  // a steep face in front — swept across its width. WAVE_J points across it, WAVE_K along the
  // profile: its back foot, two up the slope, the crest, halfway down the face, its front foot.
  private final val WAVE_J = 9
  private final val WAVE_K = 6
  private val PROF_X = Array(-1f, -0.62f, -0.28f, 0.04f, 0.09f, 0.11f)  // the slope's in its depth
  private val PROF_Z = Array(0f, 0.42f, 0.78f, 1f, 0.55f, 0f)
  private val _mX = new Array[Float](WAVE_J * WAVE_K)
  private val _mY = new Array[Float](WAVE_J * WAVE_K)

  /** The band of the wave between profile points k and k + 1 across columns j and j + 1: two
    * triangles split along the diagonal inside it. Foreshortened, a band can fold into a dart, and a
    * fan from the wrong corner fills outside it. */
  private def waveQuad(sb: ShapeBatch, j: Int, k: Int, a: Float): Unit = {
    val p0 = j * WAVE_K + k; val p1 = (j + 1) * WAVE_K + k
    _qx(0) = _mX(p0); _qy(0) = _mY(p0)
    _qx(1) = _mX(p1); _qy(1) = _mY(p1)
    _qx(2) = _mX(p1 + 1); _qy(2) = _mY(p1 + 1)
    _qx(3) = _mX(p0 + 1); _qy(3) = _mY(p0 + 1)
    // A dart's notch is the one corner turning against the other three, and the diagonal inside
    // the quad runs from it
    val c0 = turn(3, 0, 1) > 0f; val c1 = turn(0, 1, 2) > 0f; val c2 = turn(1, 2, 3) > 0f; val c3 = turn(2, 3, 0) > 0f
    val pos = (if (c0) 1 else 0) + (if (c1) 1 else 0) + (if (c2) 1 else 0) + (if (c3) 1 else 0)
    val d = if (pos == 3) (if (!c1 || !c3) 1 else 0) else if (pos == 1) (if (c1 || c3) 1 else 0) else 0
    val r = (k: @scala.annotation.switch) match {
      case 0 => 0.13f; case 1 => 0.2f; case 2 => 0.38f; case 3 => 0.28f; case _ => 0.09f
    }
    val g = (k: @scala.annotation.switch) match {
      case 0 => 0.39f; case 1 => 0.52f; case 2 => 0.72f; case 3 => 0.6f; case _ => 0.31f
    }
    val b = (k: @scala.annotation.switch) match {
      case 0 => 0.76f; case 1 => 0.88f; case 2 => 0.97f; case 3 => 0.94f; case _ => 0.68f
    }
    tri(sb, d, d + 1, d + 2, r, g, b, a)
    tri(sb, d, d + 2, (d + 3) & 3, r, g, b, a)
  }
  private val _qx = new Array[Float](4)
  private val _qy = new Array[Float](4)

  /** Which way the quad turns at corner `i`, coming from `h` and going on to `j`. */
  @inline private def turn(h: Int, i: Int, j: Int): Float =
    (_qx(i) - _qx(h)) * (_qy(j) - _qy(i)) - (_qy(i) - _qy(h)) * (_qx(j) - _qx(i))

  private def tri(sb: ShapeBatch, i: Int, j: Int, k: Int, r: Float, g: Float, b: Float, a: Float): Unit = {
    _polyXs3(0) = _qx(i); _polyYs3(0) = _qy(i)
    _polyXs3(1) = _qx(j & 3); _polyYs3(1) = _qy(j & 3)
    _polyXs3(2) = _qx(k & 3); _polyYs3(2) = _qy(k & 3)
    sb.fillPolygon(_polyXs3, _polyYs3, 3, r, g, b, a)
  }

  /** An end of the wave: its profile, standing across the flight line, inked. */
  private def waveCap(sb: ShapeBatch, j: Int, a: Float): Unit = {
    var k = 0
    while (k < WAVE_K) { _sx(k) = _mX(j * WAVE_K + k); _sy(k) = _mY(j * WAVE_K + k); k += 1 }
    val cx = (_sx(0) + _sx(WAVE_K - 1)) * 0.5f; val cy = (_sy(0) + _sy(WAVE_K - 1)) * 0.5f * 0.7f + _sy(3) * 0.3f
    sb.strokePolygon(_sx, _sy, WAVE_K, 2.2f, 0.04f, 0.12f, 0.3f, 0.75f * a)
    sb.fillFan(cx, cy, _sx, _sy, WAVE_K, 0.16f, 0.45f, 0.82f, a)
  }

  /**
   * The Tidecaller's tidal wave: a ridge of water rolling across its path, bowed forward in the
   * middle — a long back slope deepening to blue at its foot, a steep face in front — with a crest
   * of foam heaped along its top, spray thrown off it, foam where its face meets the ground, and a
   * sheet of water washing along behind it. It rises as it leaves her.
   *
   * It is modelled rather than drawn: the profile swept across its width in the world and
   * projected, its height straight up the screen, and drawn far side first, so it holds its shape
   * at every heading. A wall with no depth, tried first, stood edge-on to a wave crossing the
   * screen and came out a sliver.
   */
  private[projectiles] def drawBreakingWave(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 41) * 0.35
    computeAllDynamics(proj, 0.35f, 0.66f, 1f, phase)
    val a = dynAlpha
    val ds = Math.min(dynScale, 1.3f)
    val wl = Math.sqrt(proj.dx * proj.dx + proj.dy * proj.dy).toFloat
    val ux = if (wl > 1e-4f) proj.dx / wl else 1f
    val uy = if (wl > 1e-4f) proj.dy / wl else 0f
    // One world unit along the flight line and across it, on screen
    val ex = (ux - uy) * 20f; val ey = (ux + uy) * 10f
    val qx = (-uy - ux) * 20f; val qy = (-uy + ux) * 10f
    val el = Math.max(1f, Math.sqrt(ex * ex + ey * ey).toFloat)
    val ndx = ex / el; val ndy = ey / el
    val rise = Math.min(1f, 0.3f + _lifePct * 7f)
    val H = 24f * ds * rise
    val W = 1.3f * ds; val B = 0.5f * ds; val D = 1.2f * ds

    var j = 0
    while (j < WAVE_J) {
      val u = -1f + 2f * j / (WAVE_J - 1)
      val h = H * (1f - 0.55f * u * u) * (1f + 0.06f * Math.sin(phase * 1.3 + u * 3.1).toFloat)
      val bx = sx + qx * u * W - ex * u * u * B; val by = sy + qy * u * W - ey * u * u * B
      var k = 0
      while (k < WAVE_K) {
        val x = if (k < 3) PROF_X(k) * D else PROF_X(k) * ds
        _mX(j * WAVE_K + k) = bx + ex * x
        _mY(j * WAVE_K + k) = by + ey * x - PROF_Z(k) * h
        k += 1
      }
      j += 1
    }

    // The sheet of water washing along behind it, with streaks of foam in it
    j = 0
    while (j < WAVE_J) {
      val p = j * WAVE_K
      _sx(j) = _mX(p); _sy(j) = _mY(p)
      _sx(2 * WAVE_J - 1 - j) = _mX(p) - ex * 0.7f * ds; _sy(2 * WAVE_J - 1 - j) = _mY(p) - ey * 0.7f * ds
      j += 1
    }
    sb.fillRibbon(_sx, _sy, 2 * WAVE_J, 0.24f, 0.55f, 0.92f, 0.3f * a)
    var f = 0
    while (f < 3) {
      val t = ((tick * 0.03 + f / 3.0 + proj.id * 0.21) % 1.0).toFloat
      val u0 = hash2(proj.id + 9, f) * 0.55f
      val fx0 = sx + qx * u0 * W - ex * (D + 0.1f + t * 0.8f * ds); val fy0 = sy + qy * u0 * W - ey * (D + 0.1f + t * 0.8f * ds)
      fadeLine(sb, fx0 + qx * 0.28f * W, fy0 + qy * 0.28f * W, fx0 - qx * 0.28f * W, fy0 - qy * 0.28f * W,
        2f * ds, 1f, 1f, 1f, 0.5f * (1f - t) * a, 3)
      f += 1
    }

    // The ridge, far side first: the far end, the columns across it from far to near — each from
    // whichever of its slope and its face is further from us — then the near end
    val nearHigh = qy >= 0f
    val farCap = if (nearHigh) 0 else WAVE_J - 1
    waveCap(sb, farCap, 0.95f * a)
    var c = 0
    while (c < WAVE_J - 1) {
      val col = if (nearHigh) c else WAVE_J - 2 - c
      var b = 0
      while (b < WAVE_K - 1) {
        waveQuad(sb, col, if (ndy >= 0f) b else WAVE_K - 2 - b, 0.95f * a)
        b += 1
      }
      c += 1
    }
    waveCap(sb, WAVE_J - 1 - farCap, 0.95f * a)

    // Ink along its feet, foam where its face meets the ground
    j = 0
    while (j < WAVE_J) { _sx(j) = _mX(j * WAVE_K); _sy(j) = _mY(j * WAVE_K); j += 1 }
    sb.strokePolyline(_sx, _sy, WAVE_J, 2f, 0.04f, 0.12f, 0.3f, 0.55f * a)
    j = 0
    while (j < WAVE_J) { _sx(j) = _mX(j * WAVE_K + 5); _sy(j) = _mY(j * WAVE_K + 5); j += 1 }
    sb.strokePolyline(_sx, _sy, WAVE_J, 4f * ds, 0.04f, 0.12f, 0.3f, 0.6f * a)
    sb.strokePolyline(_sx, _sy, WAVE_J, 2.4f * ds, 0.92f, 0.98f, 1f, 0.9f * a)

    // The crest: ink, then a band of foam heaped up in the middle, with bobbing foam along it
    j = 0
    while (j < WAVE_J) {
      val u = -1f + 2f * j / (WAVE_J - 1)
      _sx(j) = _mX(j * WAVE_K + 3); _sy(j) = _mY(j * WAVE_K + 3)
      _pvW(j) = (3.2f + 3.4f * (1f - u * u)) * ds; _pvA(j) = a
      j += 1
    }
    sb.strokePolyline(_sx, _sy, WAVE_J, 7f * ds, 0.04f, 0.12f, 0.3f, 0.75f * a)
    sb.strokePolylineVar(_sx, _sy, _pvW, _pvA, WAVE_J, 1f, 1f, 1f)
    j = 1
    while (j < WAVE_J - 1) {
      val u = -1f + 2f * j / (WAVE_J - 1)
      val bob = 0.5f + 0.5f * Math.sin(phase * 2.2 + j * 1.7).toFloat
      val fr = (2.3f + 1.5f * bob) * ds * (1f - 0.4f * u * u)
      val fx0 = _sx(j) + ndx * 1.5f * ds; val fy0 = _sy(j) - fr * 0.45f
      sb.fillOval(fx0, fy0, fr + 1.2f, fr * 0.8f + 1.2f, 0.1f, 0.3f, 0.55f, 0.5f * a, 10)
      sb.fillOval(fx0, fy0, fr, fr * 0.8f, 1f, 1f, 1f, a, 10)
      j += 1
    }

    // Spray thrown up off the crest and forward
    var d = 0
    while (d < 6) {
      val t = ((tick * 0.05 + d / 6.0 + proj.id * 0.37) % 1.0).toFloat
      val jj = 1 + ((hash2(proj.id + 17, d) + 1f) * 0.5f * (WAVE_J - 3)).toInt
      val cxj = _mX(jj * WAVE_K + 3); val cyj = _mY(jj * WAVE_K + 3)
      val px0 = cxj + ndx * t * 14f * ds; val py0 = cyj + ndy * t * 8f * ds - Math.sin(t * Math.PI).toFloat * 13f * ds
      val ps = (2.3f - t * 1.1f) * ds
      sb.fillOval(px0, py0, ps + 0.9f, ps + 0.9f, 0.1f, 0.3f, 0.55f, 0.5f * (1f - t) * a, 8)
      sb.fillOval(px0, py0, ps, ps, 0.88f, 0.96f, 1f, (1f - t) * a, 8)
      d += 1
    }
  }

  // ── The Sandstorm ──
  // A cloud of puffs: inked, shaded at the foot, lit across the top
  private val _pX = new Array[Float](9)
  private val _pY = new Array[Float](9)
  private val _pR = new Array[Float](9)

  /**
   * The Sandstorm's blast: a cloud of dust rolling forward, its puffs churning and swelling, with
   * the churn drawn across it, grit flung off it, and a low haze of dust dragging behind it.
   */
  private[projectiles] def drawDustCloud(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 43) * 0.35
    computeAllDynamics(proj, 0.86f, 0.72f, 0.45f, phase)
    val a = dynAlpha
    val ds = Math.min(dynScale, 1.3f)
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val px = -ndy; val py = ndx
    val cx = sx - ndx * 13f * ds; val cy = sy - ndy * 13f * ds

    // Haze dragging behind
    var i = 0
    while (i < 5) {
      val t = i / 4f
      _sx(i) = cx - ndx * t * 52f * ds; _sy(i) = cy - ndy * t * 52f * ds + 3f * ds
      _pvW(i) = (30f - 22f * t) * ds; _pvA(i) = 0.3f * a * (1f - t)
      i += 1
    }
    sb.strokePolylineVar(_sx, _sy, _pvW, _pvA, 5, 0.82f, 0.68f, 0.45f)
    // Puffs of it torn off its back, swelling and thinning as they fall behind
    var q = 0
    while (q < 4) {
      val t = ((tick * 0.04 + q / 4.0 + proj.id * 0.27) % 1.0).toFloat
      val lat = hash2(proj.id + 11, q) * 9f * ds
      val qx0 = cx - ndx * (12f + t * 34f) * ds + px * lat; val qy0 = cy - ndy * (12f + t * 34f) * ds + py * lat - t * 4f * ds
      val qr = (4.5f + t * 5f) * ds
      sb.fillOval(qx0, qy0, qr + 1.5f, qr * 0.85f + 1.5f, 0.4f, 0.28f, 0.12f, 0.45f * (1f - t) * a, 12)
      sb.fillOval(qx0, qy0, qr, qr * 0.85f, 0.88f, 0.76f, 0.52f, 0.8f * (1f - t) * a, 12)
      q += 1
    }

    // The cloud
    val churn = phase * 0.22
    i = 0
    while (i < 7) {
      val ang = churn + i * (2 * Math.PI / 7)
      _pX(i) = cx + Math.cos(ang).toFloat * 10.5f * ds
      _pY(i) = cy + Math.sin(ang).toFloat * 6.5f * ds
      _pR(i) = (7f + 1.8f * Math.sin(phase * 0.8 + i * 2.3).toFloat) * ds
      i += 1
    }
    _pX(7) = cx; _pY(7) = cy - 2f * ds; _pR(7) = 10f * ds
    i = 0
    while (i < 8) { sb.fillOval(_pX(i), _pY(i), _pR(i) + 1.8f, _pR(i) * 0.85f + 1.8f, 0.4f, 0.28f, 0.12f, 0.7f * a, 16); i += 1 }
    i = 0
    while (i < 8) { sb.fillOval(_pX(i), _pY(i), _pR(i), _pR(i) * 0.85f, 0.7f, 0.56f, 0.34f, a, 16); i += 1 }
    i = 0
    while (i < 8) {
      sb.fillOval(_pX(i) - _pR(i) * 0.08f, _pY(i) - _pR(i) * 0.18f, _pR(i) * 0.84f, _pR(i) * 0.66f, 0.89f, 0.76f, 0.5f, a, 14)
      i += 1
    }
    i = 0
    while (i < 8) {
      sb.fillOval(_pX(i) - _pR(i) * 0.22f, _pY(i) - _pR(i) * 0.36f, _pR(i) * 0.45f, _pR(i) * 0.3f, 0.97f, 0.88f, 0.66f, 0.9f * a, 10)
      i += 1
    }
    // The churn, across it
    var k = 0
    while (k < 2) {
      val st = (churn * 2.2 + k * Math.PI).toFloat
      sb.strokeArc(cx, cy - 1f * ds, 9f * ds, 5.5f * ds, st, 1.9f, 1.8f * ds, 0.42f, 0.28f, 0.12f, 0.7f * a, 8)
      k += 1
    }
    // Grit flung off it
    var g = 0
    while (g < 10) {
      val t = ((tick * 0.04 + g / 10.0 + proj.id * 0.31) % 1.0).toFloat
      val ang = churn * 1.8 + g * 0.63 + t * 2.2
      val rr = (12f + t * 14f) * ds
      val gx = cx + Math.cos(ang).toFloat * rr - ndx * t * 12f * ds
      val gy = cy + Math.sin(ang).toFloat * rr * 0.6f - ndy * t * 12f * ds
      val gs = (1.7f - t) * ds + 0.5f
      sb.fillOval(gx, gy, gs, gs, 0.36f, 0.24f, 0.1f, 0.9f * (1f - t) * a, 6)
      g += 1
    }
  }

  // ── The Inferno ──
  private val TONGUE_T = Array(0f, 0.3f, 0.6f, 0.85f, 1f)

  /** One tongue of a flame from (x0, y0) back along (-ndx, -ndy), `len` long and `w` wide at its
    * root, rising and swaying as flames do; into _sx/_sy/_pvW/_pvA, five points. */
  private def tongue(x0: Float, y0: Float, ndx: Float, ndy: Float, lat: Float, spread: Float, len: Float,
                     w: Float, rise: Float, sway: Float, a: Float): Unit = {
    val px = -ndy; val py = ndx
    var i = 0
    while (i < 5) {
      val t = TONGUE_T(i)
      val l = lat + spread * t + sway * t * t
      _sx(i) = x0 - ndx * len * t + px * l; _sy(i) = y0 - ndy * len * t + py * l - rise * t * t
      _pvW(i) = w * (1f - t) * (1f - t * 0.2f) + 0.4f; _pvA(i) = a * (1f - t * t * 0.5f)
      i += 1
    }
  }

  /**
   * The Inferno's flame wave: a small one of the Inferno itself — a living flame with a scowl, its
   * head on the hitbox and its fire streaming back in five tongues, rising and flickering, orange
   * round yellow round white — scattering embers in its wake.
   */
  private[projectiles] def drawLivingFlame(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 37) * 0.35
    computeAllDynamics(proj, 1f, 0.5f, 0.1f, phase)
    val a = dynAlpha
    val ds = Math.min(dynScale, 1.3f)
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val px = -ndy; val py = ndx
    val R = 11f * ds

    sb.fillOvalSoft(sx - ndx * 8f * ds, sy - ndy * 8f * ds - 3f * ds, 30f * ds * dynGlow, 24f * ds * dynGlow,
      1f, 0.5f, 0.1f, 0.3f * a, 0f, 16)
    // Embers left behind, rising as they cool
    var e = 0
    while (e < 6) {
      val t = ((tick * 0.045 + e / 6.0 + proj.id * 0.29) % 1.0).toFloat
      val lat = hash2(proj.id + 3, e) * 10f * ds
      val bx = sx - ndx * (R + t * 46f * ds) + px * lat; val by = sy - ndy * (R + t * 46f * ds) + py * lat - t * 12f * ds
      val es = (2.4f * (1f - t) + 0.6f) * ds
      sb.fillOval(bx, by, es, es, 1f, mix(0.92f, 0.4f, t), mix(0.5f, 0.05f, t), (1f - t) * a, 6)
      e += 1
    }

    // The fire streaming off it, in layers: ink, red-orange, orange, yellow
    var layer = 0
    while (layer < 4) {
      var j = 0
      while (j < 5) {
        val side = (j - 2) / 2f
        val flick = 0.8f + 0.2f * Math.sin(phase * 3.6 + j * 2.3).toFloat
        val len = (26f + 9f * (1f - Math.abs(side)) + 4f * hash2(proj.id, j)) * ds * flick
        val sway = Math.sin(phase * 2.8 + j * 1.7).toFloat * 4f * ds
        val k = if (layer <= 1) 1f else if (layer == 2) 0.62f else 0.36f
        tongue(sx - ndx * R * 0.2f, sy - ndy * R * 0.2f, ndx, ndy, side * R * 0.7f * k, side * 7f * ds,
          len * k, R * (1.05f - 0.25f * Math.abs(side)) * k * (if (layer == 0) 1f else 0.86f) + (if (layer == 0) 3f else 0f),
          9f * ds * k, sway, a)
        (layer: @scala.annotation.switch) match {
          case 0 => sb.strokePolylineVar(_sx, _sy, _pvW, _pvA, 5, 0.34f, 0.05f, 0.02f)
          case 1 => sb.strokePolylineVar(_sx, _sy, _pvW, _pvA, 5, 1f, 0.34f, 0.07f)
          case 2 => sb.strokePolylineVar(_sx, _sy, _pvW, _pvA, 5, 1f, 0.64f, 0.12f)
          case _ => sb.strokePolylineVar(_sx, _sy, _pvW, _pvA, 5, 1f, 0.9f, 0.4f)
        }
        j += 1
      }
      if (layer == 0) sb.fillOval(sx, sy, R + 2.4f, R * 0.94f + 2.4f, 0.34f, 0.05f, 0.02f, 0.9f * a, 18)
      if (layer == 1) sb.fillOval(sx, sy, R, R * 0.94f, 1f, 0.34f, 0.07f, a, 18)
      if (layer == 2) sb.fillOval(sx + ndx * R * 0.1f, sy + ndy * R * 0.1f, R * 0.8f, R * 0.74f, 1f, 0.64f, 0.12f, a, 16)
      layer += 1
    }
    val hot = 0.9f + 0.1f * Math.sin(phase * 2.1).toFloat
    sb.fillOval(sx + ndx * R * 0.18f, sy + ndy * R * 0.18f, R * 0.56f * hot, R * 0.52f * hot, 1f, 0.9f, 0.4f, a, 14)
    sb.fillOvalSoft(sx + ndx * R * 0.22f, sy + ndy * R * 0.22f, R * 0.34f, R * 0.3f, 1f, 1f, 0.85f, a, 0.4f * a, 12)

    // Its scowl, looking the way it flies
    val m = if (ndx < -0.15f) -1f else 1f
    val lk = m * R * 0.14f
    var eye = -1
    while (eye <= 1) {
      val ecx = sx + lk + eye * R * 0.36f; val ecy = sy - R * 0.1f
      _sx(0) = ecx - eye * R * 0.2f; _sy(0) = ecy + R * 0.06f
      _sx(1) = ecx + eye * R * 0.22f; _sy(1) = ecy - R * 0.2f
      _sx(2) = ecx + eye * R * 0.17f; _sy(2) = ecy + R * 0.09f
      _sx(3) = ecx - eye * R * 0.12f; _sy(3) = ecy + R * 0.12f
      sb.fillPolygon(_sx, _sy, 4, 0.36f, 0.03f, 0.02f, a)
      sb.fillOval(ecx + eye * R * 0.04f, ecy + R * 0.02f, R * 0.06f + 0.4f, R * 0.06f + 0.4f, 1f, 0.95f, 0.6f, a, 6)
      eye += 2
    }
    val mx = sx + lk; val my = sy + R * 0.2f
    _sx(0) = mx - R * 0.34f; _sy(0) = my
    _sx(1) = mx + R * 0.34f; _sy(1) = my
    _sx(2) = mx + R * 0.22f; _sy(2) = my + R * 0.2f
    _sx(3) = mx; _sy(3) = my + R * 0.26f
    _sx(4) = mx - R * 0.22f; _sy(4) = my + R * 0.2f
    sb.fillPolygon(_sx, _sy, 5, 0.36f, 0.03f, 0.02f, a)
    var f = -1
    while (f <= 1) {
      _sx(0) = mx + f * R * 0.17f - R * 0.06f; _sy(0) = my
      _sx(1) = mx + f * R * 0.17f + R * 0.06f; _sy(1) = my
      _sx(2) = mx + f * R * 0.17f; _sy(2) = my + R * 0.13f
      sb.fillPolygon(_sx, _sy, 3, 1f, 0.97f, 0.85f, a)
      f += 2
    }
  }

  // ── The Scorpion ──

  /**
   * The Scorpion's acid spray: a squirt of venom — a glossy chartreuse glob in front (lime was grass),
   * a thinning jet of it
   * behind, and droplets breaking off the jet and sagging as they fall behind — steaming and
   * sizzling as it goes.
   */
  private[projectiles] def drawAcidSpray(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 47) * 0.35
    computeAllDynamics(proj, 0.55f, 0.95f, 0.22f, phase)
    val a = dynAlpha
    val ds = Math.min(dynScale, 1.3f)
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val px = -ndy; val py = ndx
    val R = 7.6f * ds
    val gx = sx - ndx * R * 0.5f; val gy = sy - ndy * R * 0.5f

    // Steam rising off it
    var st = 0
    while (st < 3) {
      val t = ((tick * 0.035 + st / 3.0 + proj.id * 0.19) % 1.0).toFloat
      val fx0 = gx - ndx * (4f + t * 18f) * ds + px * hash2(proj.id + 1, st) * 6f * ds
      val fy0 = gy - ndy * (4f + t * 18f) * ds + py * hash2(proj.id + 1, st) * 6f * ds - (4f + t * 14f) * ds
      sb.fillOvalSoft(fx0, fy0, (4f + t * 5f) * ds, (3.4f + t * 4f) * ds, 0.8f, 1f, 0.7f, 0.4f * (1f - t) * a, 0f, 10)
      st += 1
    }

    // The jet, thinning behind the glob
    var i = 0
    while (i < 6) {
      val t = i / 5f
      val w = Math.sin(phase * 1.5 - t * 3.5).toFloat * 2.5f * ds * t
      _sx(i) = gx - ndx * t * 40f * ds + px * w; _sy(i) = gy - ndy * t * 40f * ds + py * w + t * t * 5f * ds
      _pvW(i) = (R * 1.3f * (1f - t) + 0.6f) + 2.4f; _pvA(i) = 0.75f * a * (1f - t)
      i += 1
    }
    sb.strokePolylineVar(_sx, _sy, _pvW, _pvA, 6, 0.1f, 0.26f, 0.04f)
    i = 0
    while (i < 6) { val t = i / 5f; _pvW(i) = R * 1.3f * (1f - t) + 0.6f; _pvA(i) = 0.95f * a * (1f - t); i += 1 }
    sb.strokePolylineVar(_sx, _sy, _pvW, _pvA, 6, 0.76f, 0.98f, 0.2f)

    // Droplets breaking off it and sagging
    var d = 0
    while (d < 6) {
      val t = ((tick * 0.03 + d / 6.0 + proj.id * 0.23) % 1.0).toFloat
      val back = (9f + t * 40f) * ds
      val lat = hash2(proj.id + 7, d) * (3f + t * 8f) * ds
      val dx0 = gx - ndx * back + px * lat; val dy0 = gy - ndy * back + py * lat + t * t * 10f * ds
      val dr = (3.4f - 1.8f * t) * ds
      sb.fillOval(dx0, dy0, dr + 1.3f, dr * 1.1f + 1.3f, 0.1f, 0.26f, 0.04f, 0.8f * (1f - t) * a, 8)
      sb.fillOval(dx0, dy0, dr, dr * 1.1f, 0.8f, 0.99f, 0.24f, (1f - t) * a, 8)
      sb.fillOval(dx0 - dr * 0.3f, dy0 - dr * 0.35f, dr * 0.35f, dr * 0.3f, 1f, 1f, 0.8f, 0.9f * (1f - t) * a, 6)
      d += 1
    }

    // The glob: a teardrop, ink, lime, its underside darker, glossy on top
    _sx(0) = gx + px * R * 0.9f; _sy(0) = gy + py * R * 0.9f
    _sx(1) = gx - ndx * R * 2.1f; _sy(1) = gy - ndy * R * 2.1f
    _sx(2) = gx - px * R * 0.9f; _sy(2) = gy - py * R * 0.9f
    sb.strokePolygon(_sx, _sy, 3, 2.6f, 0.1f, 0.26f, 0.04f, 0.9f * a)
    sb.fillOval(gx, gy, R + 1.6f, R * 0.92f + 1.6f, 0.1f, 0.26f, 0.04f, 0.9f * a, 16)
    sb.fillPolygon(_sx, _sy, 3, 0.7f, 0.94f, 0.16f, a)
    sb.fillOval(gx, gy, R, R * 0.92f, 0.72f, 0.95f, 0.18f, a, 16)
    sb.fillOval(gx, gy + R * 0.3f, R * 0.8f, R * 0.5f, 0.42f, 0.7f, 0.08f, 0.8f * a, 14)
    sb.fillOval(gx - R * 0.3f, gy - R * 0.34f, R * 0.4f, R * 0.26f, 1f, 1f, 0.85f, 0.95f * a, 10)
    // Sizzling on it
    var b = 0
    while (b < 3) {
      val t = ((tick * 0.07 + b / 3.0 + proj.id * 0.41) % 1.0).toFloat
      val ang = b * 2.1 + proj.id
      val bx = gx + Math.cos(ang).toFloat * R * 0.8f; val by = gy + Math.sin(ang).toFloat * R * 0.7f
      sb.strokeOval(bx, by, (0.8f + t * 2.2f) * ds, (0.8f + t * 2.2f) * ds, 1.1f, 0.9f, 1f, 0.7f, (1f - t) * a, 8)
      b += 1
    }
  }

  // ── The Barbarian ──
  private final val CRACK_MAX = 16
  private val _kX = new Array[Float](CRACK_MAX + 1)
  private val _kY = new Array[Float](CRACK_MAX + 1)
  private val _kD = new Array[Float](CRACK_MAX + 1)

  /** A shard of rock standing out of the ground at (gx, gy), `h` tall, leaning `lean` across the
    * screen: a lit face and a shaded one, inked. */
  private def shard(sb: ShapeBatch, gx: Float, gy: Float, w: Float, h: Float, lean: Float, a: Float): Unit = {
    if (h < 1f || a <= 0.01f) return
    _sx(0) = gx - w; _sy(0) = gy
    _sx(1) = gx + w; _sy(1) = gy + w * 0.3f
    _sx(2) = gx + lean + w * 0.25f; _sy(2) = gy - h
    _sx(3) = gx + lean - w * 0.35f; _sy(3) = gy - h * 0.92f
    sb.strokePolygon(_sx, _sy, 4, 2.2f, 0.16f, 0.1f, 0.06f, 0.85f * a)
    sb.fillPolygon(_sx, _sy, 4, 0.42f, 0.35f, 0.28f, a)
    _sx(1) = gx + w * 0.1f; _sy(1) = gy + w * 0.12f
    _sx(2) = gx + lean; _sy(2) = gy - h
    sb.fillPolygon(_sx, _sy, 4, 0.64f, 0.56f, 0.46f, a)
  }

  /**
   * The Barbarian's earthsplitter: the ground splitting open along its path. At the hitbox shards of
   * rock burst up out of it with dust and pebbles flying; behind, the crack it has opened — jagged,
   * glowing deep red where it is freshest, with cracks branching off it — stays where it opened (its
   * kinks are keyed to points fixed along the flight line, as a bolt's channel is), and the shards
   * it threw up sink back into it.
   */
  private[projectiles] def drawFissure(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 31) * 0.35
    computeAllDynamics(proj, 0.62f, 0.48f, 0.28f, phase)
    val a = dynAlpha
    val ds = Math.min(dynScale, 1.3f)
    val wl = Math.sqrt(proj.dx * proj.dx + proj.dy * proj.dy).toFloat
    val ux = if (wl > 1e-4f) proj.dx / wl else 1f
    val uy = if (wl > 1e-4f) proj.dy / wl else 0f
    val esx = (ux - uy) * 20f; val esy = (ux + uy) * 10f
    val spp = Math.max(1f, Math.sqrt(esx * esx + esy * esy).toFloat)
    val ndx = esx / spp; val ndy = esy / spp
    val nx = -ndy; val ny = ndx
    val along = proj.getX * ux + proj.getY * uy
    val gap = 7f * ds
    val stepW = gap / spp
    // Never further back than where it started
    val trail = Math.min(90f * ds, Math.max(gap * 2f, proj.getDistanceTraveled * spp))
    val seed = proj.id * 211

    _kX(0) = sx; _kY(0) = sy; _kD(0) = 0f
    var n = 1
    var k = Math.floor(along / stepW).toInt
    var more = true
    while (more && n < CRACK_MAX) {
      val d = (along - k * stepW) * spp
      if (d > trail) more = false
      else {
        if (d > gap * 0.4f) {
          val off = hash2(seed, k) * 4.6f * ds * Math.min(1f, d / gap)
          _kX(n) = sx - ndx * d + nx * off; _kY(n) = sy - ndy * d + ny * off; _kD(n) = d
          n += 1
        }
        k -= 1
      }
    }

    // The crack: dust along its lips, the split itself, its red-hot depths where it is freshest
    var i = 0
    while (i < n) { val t = _kD(i) / trail; _pvW(i) = (11f - 5f * t) * ds; _pvA(i) = 0.42f * a * (1f - t); i += 1 }
    sb.strokePolylineVar(_kX, _kY, _pvW, _pvA, n, 0.74f, 0.62f, 0.44f)
    i = 0
    while (i < n) { val t = _kD(i) / trail; _pvW(i) = (4.8f - 2f * t) * ds; _pvA(i) = 0.95f * a * (1f - t * t); i += 1 }
    sb.strokePolylineVar(_kX, _kY, _pvW, _pvA, n, 0.1f, 0.05f, 0.03f)
    i = 0
    while (i < n) {
      val t = _kD(i) / trail
      _pvW(i) = 1.8f * ds; _pvA(i) = 0.95f * a * Math.max(0f, 1f - t * 2.2f)
      i += 1
    }
    sb.strokePolylineVar(_kX, _kY, _pvW, _pvA, n, 1f, 0.42f, 0.12f)
    // Cracks branching off it
    i = 2
    while (i < n - 1) {
      val kk = Math.floor(along / stepW).toInt - i
      val h = hash2(seed + 5, kk)
      if (h > 0.25f) {
        val side = if (hash2(seed + 9, kk) > 0f) 1f else -1f
        val t = _kD(i) / trail
        val bx = _kX(i) + nx * side * 9f * ds - ndx * 4f * ds; val by = _kY(i) + ny * side * 9f * ds * 0.7f - ndy * 4f * ds
        sb.strokeLine(_kX(i), _kY(i), bx, by, 2.4f * ds * (1f - t * 0.5f), 0.1f, 0.05f, 0.03f, 0.85f * a * (1f - t))
      }
      i += 1
    }

    // Shards thrown up along it, rising at the head and sinking back behind, the far ones first
    i = n - 1
    while (i >= 1) {
      val d = _kD(i)
      if (d < 48f * ds) {
        val rise = if (d < 8f * ds) d / (8f * ds) else Math.max(0f, 1f - (d - 8f * ds) / (40f * ds))
        val kk = Math.floor(along / stepW).toInt - i
        val side = if ((kk & 1) == 0) 1f else -1f
        val hh = (9f + 6f * Math.abs(hash2(seed + 13, kk))) * ds * rise
        shard(sb, _kX(i) + nx * side * 5.5f * ds, _kY(i) + ny * side * 5.5f * ds * 0.7f, 3.4f * ds, hh,
          side * nx * 3f * ds, a)
      }
      i -= 1
    }

    // At the head: the ground bursting, dust and pebbles thrown up
    var p = 0
    while (p < 3) {
      val t = ((tick * 0.05 + p / 3.0 + proj.id * 0.17) % 1.0).toFloat
      sb.fillOvalSoft(sx - ndx * t * 12f * ds + nx * (p - 1) * 7f * ds, sy - ndy * t * 12f * ds - t * 14f * ds,
        (5f + t * 7f) * ds, (4f + t * 5f) * ds, 0.76f, 0.64f, 0.46f, 0.5f * (1f - t) * a, 0f, 10)
      p += 1
    }
    val burst = 0.75f + 0.25f * Math.abs(Math.sin(phase * 1.6).toFloat)
    shard(sb, sx - nx * 6f * ds, sy - ny * 4f * ds, 4.2f * ds, 15f * ds * burst, -nx * 4f * ds, a)
    shard(sb, sx + nx * 6.5f * ds, sy + ny * 4.5f * ds, 4f * ds, 13f * ds * burst, nx * 4f * ds, a)
    shard(sb, sx + ndx * 3f * ds, sy + ndy * 3f * ds + 2f * ds, 4.6f * ds, 18f * ds * burst, 0f, a)
    var r = 0
    while (r < 5) {
      val t = ((tick * 0.06 + r / 5.0 + proj.id * 0.43) % 1.0).toFloat
      val side = hash2(seed + 21, r)
      val rx0 = sx + nx * side * (4f + t * 14f) * ds - ndx * t * 16f * ds
      val ry0 = sy + ny * side * (4f + t * 14f) * ds - ndy * t * 16f * ds - Math.sin(t * Math.PI).toFloat * 18f * ds
      val rs = (2.2f - t) * ds + 0.6f
      sb.fillOval(rx0, ry0, rs + 0.8f, rs * 0.85f + 0.8f, 0.16f, 0.1f, 0.06f, 0.8f * (1f - t * 0.5f) * a, 6)
      sb.fillOval(rx0, ry0, rs, rs * 0.85f, 0.55f, 0.46f, 0.36f, (1f - t * 0.5f) * a, 6)
      r += 1
    }
  }
}
