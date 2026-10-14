package com.gridgame.client.render.projectiles

import com.gridgame.common.model.Projectile
import com.gridgame.client.gl.ShapeBatch
import ProjectileKit._
import Silhouettes._

/** Big things with weight: the rocket, the tumbling boulder, the geyser and the sword wave. */
private[render] object Heavies {
  // ── The rocket ──
  // Side on, +x its nose and +y down its side, in units of about an eighth of its length: the body,
  // the nose cone, a band round it, two fins and the nozzle. Each part convex.
  private val ROCKET_BODY = Array(-6.2f, -0.85f, -1.8f, -0.85f, -1.8f, 0.85f, -6.2f, 0.85f)
  private val ROCKET_NOSE = Array(-1.8f, -0.85f, -0.9f, -0.66f, -0.3f, -0.3f, 0f, 0f, -0.3f, 0.3f, -0.9f, 0.66f, -1.8f, 0.85f)
  private val ROCKET_BAND = Array(-3.7f, -0.86f, -3.05f, -0.86f, -3.05f, 0.86f, -3.7f, 0.86f)
  private val ROCKET_FIN_A = Array(-4.5f, -0.8f, -5.9f, -2.05f, -6.6f, -2.05f, -6.25f, -0.8f)
  private val ROCKET_FIN_B = Array(-4.5f, 0.8f, -5.9f, 2.05f, -6.6f, 2.05f, -6.25f, 0.8f)
  private val ROCKET_NOZZLE = Array(-6.15f, -0.6f, -6.8f, -0.78f, -6.8f, 0.78f, -6.15f, 0.6f)
  private val ROCKET_PARTS = Array(ROCKET_FIN_A, ROCKET_FIN_B, ROCKET_NOZZLE, ROCKET_BODY, ROCKET_NOSE, ROCKET_BAND)

  /**
   * Rocket: nose on the hitbox, a red warhead and fins on a steel body with a yellow band, flame
   * roaring out of its nozzle and smoke billowing behind. Inked and solid: the old one's body was three
   * strokes and its alpha pulsed down to 0.6, so the ground showed through a wireframe of a rocket. (It
   * used to start at the hitbox and extend five world units forward, so the warhead arrived long before
   * the explosion did.)
   */
  private[projectiles] def drawRocket(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 29) * 0.35
    computeAllDynamics(proj, 0.45f, 0.47f, 0.35f, phase)
    val a = dynAlpha
    val ds = Math.min(dynScale, 1.3f)
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val k = 7.6f * ds
    Glyphs.headingFrame(sx, sy, ndx, ndy, k)
    @inline def gx(lx: Float, ly: Float): Float = Glyphs.fx(lx, ly)
    @inline def gy(lx: Float, ly: Float): Float = Glyphs.fy(lx, ly)

    // Smoke billowing out behind, rising and spreading as it goes
    var i = 0
    while (i < 7) {
      val t = Glyphs.loop(tick, 0.045, i / 7.0 + proj.id * 0.09)
      val wob = Math.sin(phase * 0.8 + i * 1.3).toFloat * (0.3f + t * 1.4f)
      val px = gx(-7.2f - t * 9f, wob); val py = gy(-7.2f - t * 9f, wob) - t * 10f * ds
      val puff = (3.5f + t * 9f) * ds
      val g = 0.62f + t * 0.16f
      sb.fillOval(px, py, puff + 1.2f, puff * 0.82f + 1.2f, 0.25f, 0.24f, 0.24f, 0.32f * (1f - t) * a, 10)
      sb.fillOval(px, py, puff, puff * 0.82f, g, g, g * 0.98f, 0.62f * (1f - t) * a, 10)
      i += 1
    }
    // The flame: an outer tongue, a hotter one inside it and a white heart, flickering
    val flick = 0.85f + 0.15f * Math.sin(phase * 4.1).toFloat + 0.08f * hash2(tick / 2, proj.id)
    var layer = 0
    while (layer < 3) {
      val len = (if (layer == 0) 3.6f else if (layer == 1) 2.5f else 1.4f) * k * flick
      val w = (if (layer == 0) 1.5f else if (layer == 1) 1.0f else 0.55f) * k
      Glyphs.flameTongue(gx(-6.75f, 0f), gy(-6.75f, 0f), -ndx, -ndy, len, w,
        Math.sin(phase * 2.3 + layer).toFloat * 0.25f * k, a)
      if (layer == 0) sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, 5, 0.95f, 0.36f, 0.08f)
      else if (layer == 1) sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, 5, 1f, 0.78f, 0.2f)
      else sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, 5, 1f, 0.98f, 0.85f)
      layer += 1
    }
    // Ink round all of it, then each part
    var q = 0
    while (q < ROCKET_PARTS.length) {
      val n = Glyphs.pts(ROCKET_PARTS(q))
      sb.strokePolygon(Glyphs._gx, Glyphs._gy, n, 2.6f, 0.08f, 0.07f, 0.09f, 0.95f * a)
      q += 1
    }
    q = 0
    while (q < ROCKET_PARTS.length) {
      val part = ROCKET_PARTS(q)
      val n = Glyphs.pts(part)
      if (part eq ROCKET_BODY) sb.fillPolygon(Glyphs._gx, Glyphs._gy, n, 0.72f, 0.75f, 0.8f, a)
      else if (part eq ROCKET_BAND) sb.fillPolygon(Glyphs._gx, Glyphs._gy, n, 1f, 0.82f, 0.22f, a)
      else if (part eq ROCKET_NOZZLE) sb.fillPolygon(Glyphs._gx, Glyphs._gy, n, 0.3f, 0.3f, 0.34f, a)
      else sb.fillPolygon(Glyphs._gx, Glyphs._gy, n, 0.86f, 0.2f, 0.16f, a)
      q += 1
    }
    // Lit along its top, shaded along its bottom, and the warhead's shine
    Glyphs.gLine(sb, -6f, -0.42f, -1.9f, -0.42f, 0.34f, 1f, 1f, 1f, 0.55f * a)
    Glyphs.gLine(sb, -6f, 0.5f, -1.9f, 0.5f, 0.3f, 0.36f, 0.38f, 0.44f, 0.5f * a)
    Glyphs.gLine(sb, -1.55f, -0.4f, -0.55f, -0.25f, 0.22f, 1f, 0.75f, 0.7f, 0.7f * a)
    drawChargeCrackle(sx, sy, 16f * ds, 0.9f, 0.5f, 0.2f, a, sb, phase, proj.chargeLevel)
  }

  // A rock's outline: eight corners, each this much of its radius out, so it is lumpy but never
  // notched (a corner pulled in further than about 0.75 of its neighbours makes a dent, and a hull
  // of dents is a crumpled star)
  private val ROCK_R = Array(1f, 0.86f, 0.95f, 0.82f, 0.98f, 0.88f, 0.93f, 0.84f)

  /**
   * Boulder: a rock tumbling end over end along the ground, bouncing, with its shadow under it and
   * dust kicked up behind — lit from the upper left, shaded on its far side, a crack or two across
   * it. `ice` for the Avalanche's, pale blue and glinting; `roots` for the Cyclops's rooting boulder,
   * roots trailing off it. It used to be fanned from a hull whose corners swung between half and the
   * whole of its radius, with eight cracks radiating from its middle and highlights spinning round
   * it: a crumpled star, not a rock.
   */
  private[projectiles] def drawBoulder(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int, r: Float,
                                       ice: Boolean = false, roots: Boolean = false): Unit = {
    val phase = (tick + proj.id * 19) * 0.3
    computeAllDynamics(proj, 0.4f, 0.32f, 0.2f, phase)
    val a = dynAlpha
    val bounceRaw = Math.sin(phase * 1.5).toFloat
    val bounce = Math.abs(bounceRaw) * 9f
    val ds = Math.min(dynScale, 1.3f)
    val rr = r * ds
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    // Rolling the way it flies, about once a second
    val rot = (tick + proj.id * 19) * 0.1 * (if (ndx < 0f) -1 else 1)
    val cy = sy - bounce
    // Palette: base, shade, lit face, glint, ink
    val br = if (ice) 0.6f else 0.55f; val bg = if (ice) 0.8f else 0.49f; val bb = if (ice) 0.94f else 0.41f
    val dr = if (ice) 0.3f else 0.31f; val dg = if (ice) 0.48f else 0.26f; val db = if (ice) 0.7f else 0.21f
    val lr = if (ice) 0.84f else 0.7f; val lg = if (ice) 0.95f else 0.64f; val lb = if (ice) 1f else 0.55f
    val ir = if (ice) 0.08f else 0.12f; val ig = if (ice) 0.16f else 0.09f; val ib = if (ice) 0.3f else 0.06f

    // Dust kicked up behind it
    var i = 0
    while (i < 7) {
      val t = Glyphs.loop(tick, 0.05, i / 7.0 + proj.id * 0.11)
      val dx = sx - ndx * (rr * 0.8f + t * rr * 2.4f) + Math.sin(phase + i * 2).toFloat * 4f * ds
      val dy = sy + rr * 0.3f - ndy * (rr * 0.8f + t * rr * 2.4f) - t * 8f * ds
      val s = (4f + t * 10f) * ds
      val g = if (ice) 0.9f else 0.6f + t * 0.12f
      sb.fillOval(dx, dy, s, s * 0.6f, g, g * (if (ice) 1f else 0.88f), g * (if (ice) 1f else 0.72f), 0.4f * (1f - t) * a, 10)
      i += 1
    }
    // A thump of dust where it lands
    if (Math.abs(bounceRaw) < 0.2f) {
      val impactT = 1f - Math.abs(bounceRaw) / 0.2f
      sb.strokeOval(sx, sy + rr * 0.36f, rr * (1.3f + 0.4f * (1f - impactT)), rr * 0.36f, 2.5f * impactT,
        if (ice) 0.85f else 0.62f, if (ice) 0.92f else 0.54f, if (ice) 1f else 0.4f, 0.45f * impactT * a, 16)
    }
    // Its shadow, smaller the higher it bounces
    val sh = 1f - bounce * 0.025f
    sb.fillOval(sx, sy + rr * 0.36f, rr * 1.15f * sh, rr * 0.36f * sh, 0f, 0f, 0f, 0.38f * a, 16)

    // The rock: corners turned by its roll
    val n = 8
    var v = 0
    while (v < n) {
      val an = rot + v * (Math.PI * 2 / n)
      _shpXs(v) = sx + Math.cos(an).toFloat * rr * ROCK_R(v)
      _shpYs(v) = cy + Math.sin(an).toFloat * rr * 0.9f * ROCK_R(v)
      v += 1
    }
    if (roots) {
      // Roots trailing off it, swinging as it rolls
      var k = 0
      while (k < 3) {
        val c = (k * 3 + 1) % n
        val x0 = _shpXs(c); val y0 = _shpYs(c)
        val sway = Math.sin(phase * 1.2 + k * 2).toFloat
        _pvX(0) = x0; _pvY(0) = y0
        _pvX(1) = x0 - ndx * rr * 0.45f + sway * rr * 0.12f; _pvY(1) = y0 - ndy * rr * 0.45f + rr * 0.18f
        _pvX(2) = x0 - ndx * rr * 0.85f + sway * rr * 0.28f; _pvY(2) = y0 - ndy * rr * 0.85f + rr * 0.42f
        sb.strokePolylineTapered(_pvX, _pvY, 3, 4.6f * ds, 2.4f, 0.1f, 0.14f, 0.05f, 0.92f * a, 0.8f * a)
        sb.strokePolylineTapered(_pvX, _pvY, 3, 2.6f * ds, 0.8f, 0.36f, 0.52f, 0.2f, a, a)
        k += 1
      }
    }
    sb.strokePolygon(_shpXs, _shpYs, n, 3.4f, ir, ig, ib, 0.95f * a)
    // Its shade: the whole rock in it, then the lit rock over it, nudged toward the light, so a rim of
    // shade is left on its far side
    sb.fillFan(sx, cy, _shpXs, _shpYs, n, dr, dg, db, a)
    rockFacet(sb, sx, cy, n, 0.9f, -0.07f * rr, -0.08f * rr, br, bg, bb, a)
    // Its lit face, toward the upper left, and a glint on it
    rockFacet(sb, sx, cy, n, 0.55f, -0.2f * rr, -0.24f * rr, lr, lg, lb, a)
    sb.fillOval(sx - 0.34f * rr, cy - 0.38f * rr, rr * 0.16f, rr * 0.11f, 1f, 1f, if (ice) 1f else 0.9f, 0.75f * a, 8)
    // Cracks across it, turned with it
    var c = 0
    while (c < 2) {
      val v0 = (c * 4 + proj.id) % n
      val x0 = _shpXs(v0); val y0 = _shpYs(v0)
      val mx = sx + (x0 - sx) * 0.35f + (if (c == 0) 0.12f else -0.1f) * rr
      val my = cy + (y0 - cy) * 0.35f + 0.06f * rr
      val ex = sx + (x0 - sx) * 0.05f; val ey = cy + (y0 - cy) * 0.05f + 0.12f * rr
      _pvX(0) = x0 + (sx - x0) * 0.08f; _pvY(0) = y0 + (cy - y0) * 0.08f
      _pvX(1) = mx; _pvY(1) = my
      _pvX(2) = ex; _pvY(2) = ey
      sb.strokePolylineTapered(_pvX, _pvY, 3, 2.2f * ds, 0.6f, ir, ig, ib, 0.75f * a, 0.4f * a)
      c += 1
    }
    if (ice) {
      // Frost glinting on it
      sb.fillStarFlare(sx + 0.3f * rr, cy - 0.28f * rr, rr * 0.4f, 1.8f, (phase * 0.4).toFloat, 0.5f, 1f, 1f, 1f,
        (0.55f + 0.35f * Math.sin(phase * 1.7).toFloat) * a)
    }
    drawChargeCrackle(sx, cy, rr, 0.4f, 0.32f, 0.2f, a, sb, phase, proj.chargeLevel)
    drawReturnGhosts(sx, cy, rr, 0.4f, 0.32f, 0.2f, a, sb, proj)
  }

  // A facet of the rock: its outline scaled `k` about (cx, cy) and moved by (ox, oy)
  private val _rfX = new Array[Float](8)
  private val _rfY = new Array[Float](8)
  private def rockFacet(sb: ShapeBatch, cx: Float, cy: Float, n: Int, k: Float, ox: Float, oy: Float,
                        r: Float, g: Float, b: Float, a: Float): Unit = {
    var v = 0
    while (v < n) {
      _rfX(v) = cx + (_shpXs(v) - cx) * k + ox
      _rfY(v) = cy + (_shpYs(v) - cy) * k + oy
      v += 1
    }
    sb.fillFan(cx + ox, cy + oy, _rfX, _rfY, n, r, g, b, a)
  }

  /** Geyser — MASSIVE towering water eruption with dense layering, mist halo, foam crown, spray arcs */
  private[projectiles] def drawGeyser(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 23) * 0.4
    computeAllDynamics(proj, 0.3f, 0.6f, 1f, phase)
    val p = (0.85 + 0.15 * Math.sin(phase * _stPulseMult)).toFloat * dynAlpha
    val ds = dynScale
    val columnH = 65f * ds
    val baseW = 32f * ds

    // Huge water mist halo (70px+ radius)
    val mistPulse = (0.75f + 0.25f * Math.sin(phase * 1.8).toFloat)
    sb.fillOvalSoft(sx, sy - columnH * 0.35f, 54f * ds * dynGlow * mistPulse, 43.2f * ds * dynGlow * mistPulse,
      0.35f, 0.65f, 1f, 0.18f * p, 0f, 20)
    sb.fillOvalSoft(sx, sy - columnH * 0.5f, 36f * ds * dynGlow, 30.2f * ds * dynGlow,
      0.5f, 0.78f, 1f, 0.12f * p, 0f, 18)

    // Vertical energy lines (speed line equivalents)
    { var i = 0; while (i < 6) {
      val elX = sx + Math.sin(phase * 1.3 + i * 1.7).toFloat * baseW * 0.4f
      val elLen = columnH * (0.4f + Math.sin(phase * 2 + i * 2.3).toFloat * 0.15f)
      val elAlpha = 0.2f * p * (0.6f + Math.sin(phase * 3 + i * 1.9).toFloat * 0.4f)
      sb.strokeLineSoft(elX, sy - 5f, elX + Math.sin(phase + i) .toFloat * 3f, sy - elLen,
        2f, 0.4f, 0.7f, 1f, elAlpha)
    ; i += 1 } }

    // Large base splash with bold outlined pool + 10 foam bubbles
    sb.strokeOval(sx, sy, baseW * 1.6f, baseW * 0.45f, 3.5f, 0.12f, 0.3f, 0.6f, 0.6f * p, 16)
    sb.fillOval(sx, sy, baseW * 1.4f, baseW * 0.4f, 0.18f, 0.42f, 0.82f, 0.55f * p, 16)
    sb.fillOval(sx, sy, baseW * 1f, baseW * 0.3f, 0.3f, 0.6f, 0.95f, 0.6f * p, 14)
    sb.fillOval(sx, sy, baseW * 0.6f, baseW * 0.18f, 0.55f, 0.82f, 1f, 0.5f * p, 12);
    // 10 foam bubbles around base
    { var b = 0; while (b < 10) {
      val bAngle = phase * 0.8 + b * Math.PI * 2 / 10
      val bDist = baseW * 0.8f + Math.sin(phase * 2 + b * 1.7).toFloat * 5f * ds
      val bx = sx + Math.cos(bAngle).toFloat * bDist
      val by = sy + Math.sin(bAngle).toFloat * bDist * 0.28f
      val bSz = (3f + Math.sin(phase * 3 + b * 2.3).toFloat * 2f) * ds
      sb.strokeOval(bx, by, bSz + 0.5f, bSz * 0.85f + 0.5f, 0.8f, 0.3f, 0.5f, 0.75f, 0.2f * p, 6)
      sb.fillOval(bx, by, bSz, bSz * 0.85f, 0.72f, 0.9f, 1f, 0.5f * p, 6)
      sb.fillOval(bx - bSz * 0.2f, by - bSz * 0.25f, bSz * 0.35f, bSz * 0.3f, 1f, 1f, 1f, 0.25f * p, 4)
    ; b += 1 } }

    // 16-segment layered column (outline, dark blue, light blue, foam core)
    { var layer = 0; while (layer < 16) {
      val t = layer.toFloat / 15
      val y = sy - t * columnH
      val widthT = 1f - t * t * 0.55f
      val waveSz = Math.sin(phase * 2.5 + layer * 0.65).toFloat * 5f * ds
      val layerW = baseW * 0.6f * widthT + waveSz
      val layerH = 5.5f * ds * (1f - t * 0.3f)
      val fadeA = (1f - t * 0.35f) * p
      // Dark outline
      sb.fillOval(sx, y, layerW + 2.5f * ds, layerH + 1.5f, 0.08f, 0.2f, 0.5f, 0.65f * fadeA, 12)
      // Dark blue body
      sb.fillOval(sx, y, layerW + 0.5f * ds, layerH + 0.3f, 0.18f, 0.42f, 0.82f, 0.7f * fadeA, 12)
      // Light blue body
      sb.fillOval(sx, y, layerW * 0.7f, layerH * 0.85f, 0.3f + t * 0.18f, 0.6f + t * 0.15f, 0.92f + t * 0.08f, 0.65f * fadeA, 10)
      // Bright foam core
      sb.fillOval(sx, y, layerW * 0.35f, layerH * 0.65f, 0.65f + t * 0.2f, 0.88f + t * 0.08f, 1f, 0.55f * fadeA, 8)
    ; layer += 1 } }

    // 5 rising water rings expanding as they ascend
    { var ring = 0; while (ring < 5) {
      val ringPhase = ((phase * 0.3 + ring * 0.2) % 1.0).toFloat
      val ringY = sy - ringPhase * columnH * 0.85f
      val ringR = (9f + ringPhase * 14f) * ds
      val ringA = 0.5f * (1f - ringPhase) * p
      val ringThick = 2.5f * (1f - ringPhase * 0.4f)
      // Ring outline
      sb.strokeOval(sx, ringY, ringR + 1f, (ringR + 1f) * 0.3f, ringThick + 1.5f,
        0.15f, 0.35f, 0.7f, ringA * 0.4f, 12)
      // Ring body
      sb.strokeOval(sx, ringY, ringR, ringR * 0.3f, ringThick,
        0.55f, 0.82f, 1f, ringA, 12)
    ; ring += 1 } }

    // 12 spray particles with gravity arcs jetting from top
    { var i = 0; while (i < 12) {
      val sprayPhase = ((phase * 0.5 + i * 0.083 + proj.id * 0.09) % 1.0).toFloat
      val sprayAngle = phase * 1.5 + i * Math.PI * 2 / 12
      val sprayDist = (12f + sprayPhase * 25f) * ds
      val gravDrop = sprayPhase * sprayPhase * 30f * ds
      val spx = sx + Math.cos(sprayAngle).toFloat * sprayDist
      val spy = sy - columnH + Math.sin(sprayAngle).toFloat * sprayDist * 0.35f + gravDrop
      val spSz = (3.5f + (1f - sprayPhase) * 5f) * ds
      // Spray outline
      sb.fillOval(spx, spy, spSz + 0.8f, spSz * 0.8f + 0.8f, 0.15f, 0.35f, 0.7f, 0.25f * (1f - sprayPhase) * p, 6)
      // Spray body
      sb.fillOval(spx, spy, spSz, spSz * 0.8f, 0.5f, 0.82f, 1f, 0.6f * (1f - sprayPhase) * p, 6)
      // Spray highlight
      sb.fillOval(spx - spSz * 0.2f, spy - spSz * 0.25f, spSz * 0.35f, spSz * 0.3f,
        0.85f, 0.95f, 1f, 0.3f * (1f - sprayPhase) * p, 4)
    ; i += 1 } }

    // Dense foam crown at top with sparkle
    sb.strokeOval(sx, sy - columnH + 3f * ds, 16f * ds, 7.5f * ds, 2.5f,
      0.12f, 0.35f, 0.7f, 0.45f * p, 12)
    sb.fillOval(sx, sy - columnH + 3f * ds, 15f * ds, 7f * ds, 0.82f, 0.94f, 1f, 0.6f * p, 12)
    sb.fillOval(sx, sy - columnH + 2f * ds, 10f * ds, 5f * ds, 0.92f, 0.97f, 1f, 0.55f * p, 10)
    sb.fillOval(sx, sy - columnH + 1f * ds, 5f * ds, 3f * ds, 1f, 1f, 1f, 0.5f * p, 8);
    // Foam detail bubbles at crown
    { var fb = 0; while (fb < 4) {
      val fbAngle = phase * 2 + fb * Math.PI / 2
      val fbx = sx + Math.cos(fbAngle).toFloat * 8f * ds
      val fby = sy - columnH + 3f * ds + Math.sin(fbAngle).toFloat * 3f * ds
      sb.fillOval(fbx, fby, 3f * ds, 2.5f * ds, 0.9f, 0.97f, 1f, 0.4f * p, 6)
    ; fb += 1 } }

    // Water ribbon trail
    screenDir(proj)
    drawRibbonTrail(sx, sy, _sdx, _sdy, 0.3f, 0.6f, 1f, 0.25f * p, sb, tick, proj.id, 6, 30f * ds * dynTrail, 8f * ds, 1.5f)

    // 4 sparkle stars along column
    { var i = 0; while (i < 4) {
      val starPhase = ((phase * 0.4 + i * 0.25) % 1.0).toFloat
      val starY = sy - columnH * (0.2f + starPhase * 0.6f)
      val starX = sx + Math.sin(phase * 2 + i * 2.1).toFloat * 14f * ds
      drawSparkleStar(starX, starY, 5.5f * (1f - starPhase * 0.5f) * ds,
        0.75f, 0.95f, 1f, 0.5f * (1f - starPhase) * p, sb, phase * 2 + i)
    ; i += 1 } }

    drawChargeCrackle(sx, sy - columnH * 0.5f, 22f * ds, 0.3f, 0.6f, 1f, p, sb, phase, proj.chargeLevel)
  }

  /** Sword Wave - glowing energy crescent with afterimages */
  private[projectiles] def drawSwordWave(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 37) * 0.4
    computeAllDynamics(proj, 0.65f, 0.65f, 0.9f, phase)
    val p = (0.9 + 0.1 * Math.sin(phase * _stPulseMult)).toFloat * dynAlpha
    val dr = _evoR; val dg = _evoG; val db = _evoB
    val ds = dynScale
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val arcR = 34f * ds
    val segs = 12
    val baseAngle = Math.atan2(ndy, ndx)

    // Speed lines behind
    drawSpeedLines(sx, sy, ndx, ndy, 0.65f, 0.65f, 0.9f, 0.25f * p, sb, 5 + (_lifePct * 2).toInt, 30f * ds)

    // Ribbon trail
    drawRibbonTrail(sx, sy, ndx, ndy, 0.6f, 0.6f, 0.85f, 0.28f * p, sb, tick, proj.id,
      10, 45f * ds * dynTrail, 9f * ds, 1.5f)

    // Glow halo
    val haloPulse = (0.7f + 0.3f * Math.sin(phase * 2.2).toFloat)
    sb.fillOvalSoft(sx, sy, 36f * ds * dynGlow * haloPulse, 28.8f * ds * dynGlow * haloPulse,
      0.55f, 0.55f, 1f, 0.22f * p, 0f, 20)

    // Expanding ring at center
    val ringP = ((phase * 0.4) % 1.0).toFloat
    val ringR2 = (8f + ringP * 20f) * ds
    sb.strokeOval(sx, sy, ringR2, ringR2 * 0.55f, 2f * (1f - ringP * 0.5f),
      0.7f, 0.7f, 1f, 0.3f * (1f - ringP) * p, 10)

    // 3 trailing afterimage crescents (fading behind)
    var ghost = 0; while (ghost < 3) {
      val ghostOff = (ghost + 1) * 12f * ds
      val gx = sx - ndx * ghostOff; val gy = sy - ndy * ghostOff
      val gAlpha = 0.25f * (1f - ghost * 0.25f) * p
      val gArcR = arcR * (1f - ghost * 0.08f);
      { var i = 0; while (i <= segs) {
        val a = baseAngle - Math.PI * 0.4 + Math.PI * 0.8 * i / segs
        _swOuterXs(i) = (gx + Math.cos(a).toFloat * gArcR)
        _swOuterYs(i) = (gy + Math.sin(a).toFloat * gArcR * 0.55f)
        _swInnerXs(i) = (gx + Math.cos(a).toFloat * gArcR * 0.45f)
        _swInnerYs(i) = (gy + Math.sin(a).toFloat * gArcR * 0.25f)
      ; i += 1 } }
      { var i = 0; while (i <= segs) { _swCrescXs(i) = _swOuterXs(i); _swCrescYs(i) = _swOuterYs(i); i += 1 } }
      { var i = 0; while (i <= segs) { _swCrescXs(segs + 1 + i) = _swInnerXs(segs - i); _swCrescYs(segs + 1 + i) = _swInnerYs(segs - i); i += 1 } }
      sb.fillRibbon(_swCrescXs, _swCrescYs, (segs + 1) * 2, 0.5f, 0.5f, 0.75f, gAlpha)
    ; ghost += 1 }

    // Main crescent geometry
    { var i = 0; while (i <= segs) {
      val a = baseAngle - Math.PI * 0.4 + Math.PI * 0.8 * i / segs
      _swOuterXs(i) = (sx + Math.cos(a).toFloat * arcR)
      _swOuterYs(i) = (sy + Math.sin(a).toFloat * arcR * 0.55f)
      _swInnerXs(i) = (sx + Math.cos(a).toFloat * arcR * 0.4f)
      _swInnerYs(i) = (sy + Math.sin(a).toFloat * arcR * 0.22f)
    ; i += 1 } }
    { var i = 0; while (i <= segs) { _swCrescXs(i) = _swOuterXs(i); _swCrescYs(i) = _swOuterYs(i); i += 1 } }
    { var i = 0; while (i <= segs) { _swCrescXs(segs + 1 + i) = _swInnerXs(segs - i); _swCrescYs(segs + 1 + i) = _swInnerYs(segs - i); i += 1 } }

    // Bold dark outline on outer edge (4px)
    { var j = 0; while (j < segs) {
      sb.strokeLine(_swOuterXs(j), _swOuterYs(j), _swOuterXs(j + 1), _swOuterYs(j + 1),
        4.5f, 0.15f, 0.15f, 0.25f, 0.75f * p)
    ; j += 1 } }
    // Crescent body fill (dark -> body -> bright). A ribbon, not a polygon: the crescent
    // is concave, and fanned from vertex 0 it fills its own hollow and reads as a slab.
    sb.fillRibbon(_swCrescXs, _swCrescYs, (segs + 1) * 2, dr, dg, db, 0.7f * p);
    // Bright inner edge (white-hot core)
    { var j = 0; while (j < segs) {
      sb.strokeLine(_swOuterXs(j), _swOuterYs(j), _swOuterXs(j + 1), _swOuterYs(j + 1),
        3f, 0.85f, 0.85f, 1f, 0.9f * p)
    ; j += 1 } }
    // White-hot cutting edge
    val bc = _chgBright;
    { var j = 0; while (j < segs) {
      sb.strokeLine(_swOuterXs(j), _swOuterYs(j), _swOuterXs(j + 1), _swOuterYs(j + 1),
        1.2f, mix(1f, 1f, bc), mix(1f, 1f, bc), mix(0.95f, 1f, bc), 0.75f * p)
    ; j += 1 } }

    // Slash motion blur particles
    { var i = 0; while (i < 8) {
      val t = ((tick * 0.07 + i * 0.125 + proj.id * 0.13) % 1.0).toFloat
      val blurAngle = baseAngle - Math.PI * 0.3 + Math.PI * 0.6 * i / 8
      val blurDist = arcR * (0.5f + t * 0.4f)
      val bx = sx + Math.cos(blurAngle).toFloat * blurDist + Math.sin(phase * 3 + i * 1.7).toFloat * 3f * ds
      val by = sy + Math.sin(blurAngle).toFloat * blurDist * 0.55f
      val bSz = (3f + (1f - t) * 3.5f) * ds
      sb.fillOval(bx, by, bSz, bSz * 0.6f, 0.7f, 0.7f, 1f, 0.35f * (1f - t) * p, 6)
    ; i += 1 } }

    // Sparkle stars along leading edge
    { var i = 0; while (i < 4) {
      val starPhase = ((phase * 0.5 + i * 0.25) % 1.0).toFloat
      val starIdx = 2 + (i * 2) % segs
      val starX = _swOuterXs(starIdx) + Math.sin(phase * 3 + i * 2.1).toFloat * 4f * ds
      val starY = _swOuterYs(starIdx) + Math.cos(phase * 2.5 + i * 1.7).toFloat * 3f * ds
      drawSparkleStar(starX, starY, 5f * (1f - starPhase * 0.5f) * ds,
        0.85f, 0.85f, 1f, 0.5f * (1f - starPhase) * p, sb, phase * 2 + i)
    ; i += 1 } }

    drawChargeCrackle(sx, sy, 22f * ds, 0.65f, 0.65f, 0.9f, p, sb, phase, proj.chargeLevel)
    drawReturnGhosts(sx, sy, 22f * ds, dr, dg, db, p, sb, proj)
  }

  // Sword wave: segs=12 → outer/inner (13 each), crescent (26)
  private val _swOuterXs = new Array[Float](13)
  private val _swOuterYs = new Array[Float](13)
  private val _swInnerXs = new Array[Float](13)
  private val _swInnerYs = new Array[Float](13)
  private val _swCrescXs = new Array[Float](26)
  private val _swCrescYs = new Array[Float](26)
}
