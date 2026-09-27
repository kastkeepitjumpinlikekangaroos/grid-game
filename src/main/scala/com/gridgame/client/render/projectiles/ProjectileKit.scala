package com.gridgame.client.render.projectiles

import com.gridgame.common.model.{Projectile, ProjectileDef}
import com.gridgame.client.gl.ShapeBatch

/**
 * What the projectile renderers share: colour and direction helpers that write into fields rather
 * than allocate (intToRGB -> _r/_g/_b, screenDir -> _sdx/_sdy), scratch arrays for polygons and
 * polylines, and the dynamics every renderer can dress itself in — how charged the shot is, how far
 * through its life, whether it is on its way back — as computeAllDynamics writes them (dynScale,
 * dynAlpha, dynGlow, dynTrail, _evoR/G/B...). Read them only after computing them for the projectile
 * being drawn: they belong to whichever projectile last did.
 */
private[render] object ProjectileKit {
  // Small polygon scratch arrays (3 and 4 vertices) — shared across all renderers
  private[projectiles] val _polyXs3 = new Array[Float](3)
  private[projectiles] val _polyYs3 = new Array[Float](3)
  private[projectiles] val _polyXs4 = new Array[Float](4)
  private[projectiles] val _polyYs4 = new Array[Float](4)

  // Mutable output fields for intToRGB — avoids tuple allocation per call
  private[projectiles] var _r = 0f; private[projectiles] var _g = 0f; private[projectiles] var _b = 0f

  private[projectiles] def intToRGB(argb: Int): Unit = {
    _r = ((argb >> 16) & 0xFF) / 255f
    _g = ((argb >> 8) & 0xFF) / 255f
    _b = (argb & 0xFF) / 255f
  }

  // Mutable output fields for screenDir — avoids tuple allocation per call
  private[projectiles] var _sdx = 0f; private[projectiles] var _sdy = 0f; private[projectiles] var _sdLen = 0f

  private[projectiles] def screenDir(proj: Projectile): Unit = {
    val sdx = ((proj.dx - proj.dy) * 20).toFloat
    val sdy = ((proj.dx + proj.dy) * 10).toFloat
    val len = Math.sqrt(sdx * sdx + sdy * sdy).toFloat
    if (len < 0.01f) { _sdx = 1f; _sdy = 0f; _sdLen = 0f }
    else { _sdx = sdx / len; _sdy = sdy / len; _sdLen = len }
  }

  @inline private[projectiles] def bright(c: Float): Float = Math.min(1f, c * 0.3f + 0.7f)

  @inline private[projectiles] def dark(c: Float): Float = c * 0.5f

  @inline private[projectiles] def outline(c: Float): Float = c * 0.2f

  @inline private[projectiles] def mix(a: Float, b: Float, t: Float): Float = a + (b - a) * t

  @inline private[projectiles] def clampF(v: Float, lo: Float, hi: Float): Float = Math.max(lo, Math.min(hi, v))

  // ── Refinement budget ────────────────────────────────────────────
  // These caps exist because the dynamic multipliers COMPOUND. Before they were
  // added, a fully-charged projectile near max range multiplied its halo by
  // _chgGlow(2.5) * lifetimeGlowMult(1.3) = 3.25x on top of a 50-60px base radius,
  // painting a ~190px glow over a ~20px core. Twenty of those on screen is mush.
  // Escalation should read as "this one is stronger", not swamp the silhouette.
  private val MAX_DYN_SCALE = 1.6f
  private val MAX_DYN_GLOW  = 1.7f
  private val MAX_DYN_TRAIL = 2.0f

  // Shared key light: highlights sit up-and-left on every projectile so the whole
  // roster reads as lit from one direction instead of each shape inventing its own.
  private[projectiles] val KEY_LIGHT_X = -0.62f
  private[projectiles] val KEY_LIGHT_Y = -0.78f

  /** Outline weight proportional to shape size — a flat 4px stroke swallows a 10px shape. */
  @inline private[projectiles] def outlineW(size: Float): Float = clampF(size * 0.14f, 1.2f, 3.2f)

  // --- Step 1: Charge-Level Visual Escalation ---
  private var _chgScale = 1f      // size multiplier 1.0→1.5
  private var _chgGlow = 1f       // glow radius multiplier 1.0→1.7
  private[projectiles] var _chgBright = 0f     // whiteness mix 0.0→0.35
  private var _chgTrailLen = 1f   // trail length multiplier 1.0→1.6
  private[projectiles] var _chgSparkCount = 0  // extra spark particles 0→3

  private def computeChargeVisuals(chargeLevel: Int): Unit = {
    val t = chargeLevel / 100f  // 0.0→1.0
    _chgScale = 1f + t * 0.5f
    _chgGlow = 1f + t * 0.7f
    _chgBright = t * 0.35f
    _chgTrailLen = 1f + t * 0.6f
    _chgSparkCount = (t * 3f).toInt
  }

  /** Draw crackle strokes around a projectile for charge > 70 */
  private[projectiles] def drawChargeCrackle(sx: Float, sy: Float, size: Float, r: Float, g: Float, b: Float,
      alpha: Float, sb: ShapeBatch, phase: Double, chargeLevel: Int): Unit = {
    if (chargeLevel <= 70) return
    val intensity = (chargeLevel - 70) / 30f  // 0→1 over 70→100
    var i = 0; while (i < 3) {
      val angle = phase * 5.3 + i * Math.PI * 2 / 3
      val forkLen = size * 0.6f * (0.5f + 0.5f * Math.sin(phase * 7.1 + i * 2.7).toFloat) * intensity
      val jx = Math.sin(phase * 9.3 + i * 4.1).toFloat * size * 0.12f
      val jy = Math.cos(phase * 8.1 + i * 3.3).toFloat * size * 0.09f
      val ex = sx + Math.cos(angle).toFloat * forkLen + jx
      val ey = sy + Math.sin(angle).toFloat * forkLen * 0.6f + jy
      val mx = sx + Math.cos(angle).toFloat * forkLen * 0.5f + jx * 1.5f
      val my = sy + Math.sin(angle).toFloat * forkLen * 0.3f + jy * 1.5f
      sb.strokeLine(sx, sy, mx, my, 2f * intensity, bright(r), bright(g), bright(b), alpha * intensity * 0.7f)
      sb.strokeLine(mx, my, ex, ey, 1.5f * intensity, 1f, 1f, 1f, alpha * intensity * 0.5f)
    ; i += 1 }
  }

  // --- Step 2: Distance-Based Visual Evolution ---
  private[projectiles] var _lifePct = 0f  // 0.0 (just spawned) → 1.0 (at max range)

  private def computeLifetimeProgress(proj: Projectile): Unit = {
    val pDef = ProjectileDef.get(proj.projectileType)
    val maxR = pDef.effectiveMaxRange(proj.chargeLevel).toFloat
    _lifePct = if (maxR > 0f) clampF(proj.getDistanceTraveled / maxR, 0f, 1f) else 0f
  }

  /** Compute dissipation alpha/scale for end-of-range burn-out (last 15%) */
  @inline private[projectiles] def dissipationAlpha: Float = {
    if (_lifePct > 0.85f) { val t = (_lifePct - 0.85f) / 0.15f; 1f - t * 0.7f } else 1f
  }

  @inline private[projectiles] def dissipationScale: Float = {
    if (_lifePct > 0.85f) { val t = (_lifePct - 0.85f) / 0.15f; 1f + t * 0.3f } else 1f
  }

  /** Trail density multiplier that increases over lifetime */
  @inline private[projectiles] def lifetimeTrailMult: Float = 1f + _lifePct * 0.25f

  /** Glow radius growth over lifetime */
  @inline private[projectiles] def lifetimeGlowMult: Float = 1f + _lifePct * 0.12f

  // --- Step 3: Dynamic Color Shifting ---
  private[projectiles] var _evoR = 0f; private[projectiles] var _evoG = 0f; private[projectiles] var _evoB = 0f

  private def computeColorEvolution(r: Float, g: Float, b: Float, proj: Projectile, phase: Double): Unit = {
    var er = r; var eg = g; var eb = b
    // Charge whitening (up to 35%)
    val chgWhite = (proj.chargeLevel / 100f) * 0.35f
    er = mix(er, 1f, chgWhite); eg = mix(eg, 1f, chgWhite); eb = mix(eb, 1f, chgWhite)
    // Distance warm-up
    er = clampF(er + _lifePct * 0.08f, 0f, 1f)
    eg = clampF(eg - _lifePct * 0.03f, 0f, 1f)
    // Return color inversion (25% toward complementary)
    if (proj.isReturning) {
      er = mix(er, 1f - r, 0.25f); eg = mix(eg, 1f - g, 0.25f); eb = mix(eb, 1f - b, 0.25f)
    }
    // Temporal shimmer
    val shimmer = Math.sin(phase * 8.0).toFloat * 0.04f
    er = clampF(er + shimmer, 0f, 1f)
    eg = clampF(eg - shimmer * 0.5f, 0f, 1f)
    eb = clampF(eb + shimmer * 0.7f, 0f, 1f)
    _evoR = er; _evoG = eg; _evoB = eb
  }

  // --- Step 4: State-Reactive Rendering ---
  private[projectiles] var _stPulseMult = 1f     // pulse speed multiplier
  private var _stTrailMult = 1f     // trail length multiplier
  private var _stSizeMult = 1f      // size multiplier
  private var _stSaturation = 1f    // color saturation (1 = normal, <1 = desaturated)
  private var _stExtraSparks = 0    // extra orbiting sparks

  private def computeStateVisuals(proj: Projectile): Unit = {
    _stPulseMult = 1f; _stTrailMult = 1f; _stSizeMult = 1f; _stSaturation = 1f; _stExtraSparks = 0
    // Boomerang return
    if (proj.isReturning) {
      _stPulseMult = 1.5f
    }
    // Pierce hits
    val hits = proj.hitPlayers.size
    if (hits > 0) {
      _stTrailMult = 1f + hits * 0.2f
      _stSizeMult = Math.max(0.6f, 1f - hits * 0.1f)
      _stSaturation = Math.max(0.4f, 1f - hits * 0.15f)
    }
    // Ricochet bounces
    val pDef = ProjectileDef.get(proj.projectileType)
    val totalBounces = pDef.ricochetCount
    if (totalBounces > 0) {
      val bouncesUsed = totalBounces - proj.remainingBounces
      _stPulseMult += bouncesUsed * 0.3f
      _stExtraSparks = bouncesUsed
    }
  }

  /** Draw boomerang afterimage ghosts when projectile is returning */
  private[projectiles] def drawReturnGhosts(sx: Float, sy: Float, size: Float, r: Float, g: Float, b: Float,
      alpha: Float, sb: ShapeBatch, proj: Projectile): Unit = {
    if (!proj.isReturning) return
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    var ghost = 1; while (ghost <= 3) {
      val ga = alpha * 0.3f * (1f - ghost * 0.25f)
      val gs = size * (1f - ghost * 0.12f)
      val gx = sx - ndx * ghost * 16f
      val gy = sy - ndy * ghost * 16f
      sb.fillOval(gx, gy, gs, gs * 0.7f, r, g, b, ga, 10)
    ; ghost += 1 }
  }

  /** Apply all dynamic helpers for a projectile — call at start of any factory renderer */
  private[projectiles] def computeAllDynamics(proj: Projectile, r: Float, g: Float, b: Float, phase: Double): Unit = {
    computeChargeVisuals(proj.chargeLevel)
    computeLifetimeProgress(proj)
    computeColorEvolution(r, g, b, proj, phase)
    computeStateVisuals(proj)
  }

  // All four are clamped: they multiply together at the call sites, so an
  // unclamped product turns escalation into screen-filling haze.
  /** Combined dynamic size multiplier */
  @inline private[projectiles] def dynScale: Float = Math.min(_chgScale * _stSizeMult * dissipationScale, MAX_DYN_SCALE)

  /** Combined dynamic alpha multiplier */
  @inline private[projectiles] def dynAlpha: Float = dissipationAlpha

  /** Combined glow multiplier */
  @inline private[projectiles] def dynGlow: Float = Math.min(_chgGlow * lifetimeGlowMult, MAX_DYN_GLOW)

  /** Combined trail length multiplier */
  @inline private[projectiles] def dynTrail: Float = Math.min(_chgTrailLen * _stTrailMult * lifetimeTrailMult, MAX_DYN_TRAIL)

  /** Cartoon sparkle star — 4-point star burst.
   *  Stroke width tracks size: a fixed 2px cross turned small sparkles into blobs
   *  and left large ones looking like thin scratches. */
  private[projectiles] def drawSparkleStar(sx: Float, sy: Float, size: Float, r: Float, g: Float, b: Float,
      alpha: Float, sb: ShapeBatch, rotation: Double): Unit = {
    if (alpha <= 0.01f || size <= 0.2f) return
    val s = size
    val s2 = size * 0.3f
    val wMain = clampF(size * 0.26f, 0.9f, 2.4f)
    val wCross = wMain * 0.7f
    sb.strokeLine(sx - Math.cos(rotation).toFloat * s, sy - Math.sin(rotation).toFloat * s * 0.6f,
      sx + Math.cos(rotation).toFloat * s, sy + Math.sin(rotation).toFloat * s * 0.6f,
      wMain, r, g, b, alpha)
    sb.strokeLine(sx - Math.cos(rotation + Math.PI * 0.5).toFloat * s2,
      sy - Math.sin(rotation + Math.PI * 0.5).toFloat * s2 * 0.6f,
      sx + Math.cos(rotation + Math.PI * 0.5).toFloat * s2,
      sy + Math.sin(rotation + Math.PI * 0.5).toFloat * s2 * 0.6f,
      wCross, r, g, b, alpha)
  }

  /** Cartoon speed lines behind a moving projectile.
   *  Capped at 5 lines and drawn thin/soft — these sit behind every projectile, so
   *  the count is what separates "sense of speed" from "hatched smear". */
  private[projectiles] def drawSpeedLines(sx: Float, sy: Float, ndx: Float, ndy: Float,
      r: Float, g: Float, b: Float, alpha: Float, sb: ShapeBatch, count: Int, length: Float): Unit = {
    val n = Math.min(count, 5)
    if (n <= 0 || alpha <= 0.01f) return
    val perpX = -ndy; val perpY = ndx
    val half = (n - 1) * 0.5f
    val norm = half + 0.001f
    var i = 0; while (i < n) {
      val off = (i - half) * length * 0.5f / n
      val taper = 1f - Math.abs(i - half) / norm
      val startDist = 8f + Math.abs(off) * 0.5f
      val endDist = startDist + length * (0.5f + 0.5f * taper)
      val x0 = sx - ndx * startDist + perpX * off
      val y0 = sy - ndy * startDist + perpY * off
      val x1 = sx - ndx * endDist + perpX * off
      val y1 = sy - ndy * endDist + perpY * off
      sb.strokeLineSoft(x0, y0, x1, y1, 1.5f * taper + 0.4f, r, g, b, alpha * taper * 0.75f)
    ; i += 1 }
  }

  /** Continuous tapering ribbon trail using strokeLineSoft segments with a travelling wave.
   *
   *  Segment `t` is now a fixed fraction along the ribbon rather than a wrapping
   *  `(tick + i) % 1.0` phase. The old form made t1 < t0 once per cycle, so a single
   *  segment stretched backwards across the entire ribbon and flicked a stray streak
   *  through the projectile every loop. Motion now comes from the sine travelling
   *  along a stable ribbon, which also removes the per-segment kinks the mismatched
   *  0.7x endpoint offset used to leave. */
  private[projectiles] def drawRibbonTrail(sx: Float, sy: Float, ndx: Float, ndy: Float,
      r: Float, g: Float, b: Float, p: Float, sb: ShapeBatch,
      tick: Int, projId: Int, segments: Int, length: Float,
      startWidth: Float, endWidth: Float): Unit = {
    if (p <= 0.01f || segments <= 0) return
    val perpX = -ndy; val perpY = ndx
    val inv = 1f / segments
    @inline def waveAt(t: Float): Float =
      Math.sin(tick * 0.15 + t * 4.2 + projId * 0.7).toFloat * mix(startWidth, endWidth, t) * 0.35f
    var i = 0; while (i < segments) {
      val t0 = i * inv
      val t1 = (i + 1) * inv
      val w = mix(startWidth, endWidth, t0)
      val o0 = waveAt(t0); val o1 = waveAt(t1)
      val x0 = sx - ndx * t0 * length + perpX * o0
      val y0 = sy - ndy * t0 * length + perpY * o0
      val x1 = sx - ndx * t1 * length + perpX * o1
      val y1 = sy - ndy * t1 * length + perpY * o1
      val colorFade = 1f - t0 * 0.5f
      val alpha = 0.38f * (1f - t0) * p
      sb.strokeLineSoft(x0, y0, x1, y1, w, r * colorFade, g * colorFade, b * colorFade, alpha)
    ; i += 1 }
  }

  /** Reusable radial spark particle burst — tapered streaks radiating outward.
   *  The blunt dot that used to cap every spark read as a cluster of floating pills
   *  rather than sparks; a tapered soft streak alone carries the same energy. */
  private[projectiles] def drawSparkBurst(sx: Float, sy: Float, r: Float, g: Float, b: Float,
      p: Float, sb: ShapeBatch, tick: Int, projId: Int, count: Int, radius: Float): Unit = {
    val n = Math.min(count, 6)
    if (n <= 0 || p <= 0.01f) return
    val br = bright(r); val bg = bright(g); val bb = bright(b)
    var i = 0; while (i < n) {
      val angle = tick * 0.12 + i * Math.PI * 2 / n + projId * 0.7
      val osc = Math.sin(tick * 0.2 + i * 1.7).toFloat
      val dist = radius * (0.4f + 0.6f * osc)
      val ca = Math.cos(angle).toFloat; val sa = Math.sin(angle).toFloat
      val sparkX = sx + ca * dist
      val sparkY = sy + sa * dist * 0.6f
      val sparkLen = radius * 0.38f
      val ex = sparkX + ca * sparkLen
      val ey = sparkY + sa * sparkLen * 0.6f
      val a = (0.3 + 0.3 * Math.sin(tick * 0.3 + i * 2.1)).toFloat * p
      sb.strokeLineSoft(sparkX, sparkY, ex, ey, 1.8f, br, bg, bb, a)
    ; i += 1 }
  }

  /** A deterministic hash of two ints to [-1, 1]. The same inputs give the same kink on every
   *  frame, which is what lets a bolt's channel hold its shape while its head flies on. */
  @inline private[projectiles] def hash2(a: Int, b: Int): Float = {
    var h = a * 0x27D4EB2D + b * 0x165667B1
    h = (h ^ (h >>> 15)) * 0x2C1B3C6D
    h = (h ^ (h >>> 12)) * 0x297A2D39
    h ^= h >>> 15
    (h & 0xFFFF) / 32767.5f - 1f
  }

  // Per-point scratch for strokePolylineVar: positions, widths, alphas, and a parameter along
  // the line. One primitive at a time uses them; the batch has read them when it returns.
  private[projectiles] val _pvX = new Array[Float](48)
  private[projectiles] val _pvY = new Array[Float](48)
  private[projectiles] val _pvW = new Array[Float](48)
  private[projectiles] val _pvA = new Array[Float](48)
  private val _pvT = new Array[Float](48)

  /**
   * A little arc of electricity from (x0, y0) to (x1, y1): `segs` kinks, each pushed aside by
   * up to `amp`, the same kinks for the same seed. Ink, colour and a white core, one mitred
   * stroke each, thinning toward the far end.
   */
  private[projectiles] def sparkArc(sb: ShapeBatch, x0: Float, y0: Float, x1: Float, y1: Float, segs: Int,
                       amp: Float, seed: Int, w: Float, r: Float, g: Float, b: Float, a: Float): Unit = {
    if (a <= 0.01f) return
    val dx = x1 - x0; val dy = y1 - y0
    val len = Math.sqrt(dx * dx + dy * dy).toFloat
    if (len < 1f) return
    val nx = -dy / len; val ny = dx / len
    val n = Math.min(segs, 10) + 1
    var i = 0
    while (i < n) {
      val t = i.toFloat / (n - 1)
      val off = if (i == 0 || i == n - 1) 0f else hash2(seed, i) * amp
      _pvX(i) = x0 + dx * t + nx * off; _pvY(i) = y0 + dy * t + ny * off
      i += 1
    }
    sb.strokePolylineTapered(_pvX, _pvY, n, w + 2.4f, 1.8f, r * 0.16f, g * 0.16f, b * 0.2f + 0.05f, 0.7f * a, 0.25f * a)
    sb.strokePolylineTapered(_pvX, _pvY, n, w, w * 0.3f, r, g, b, 0.95f * a, 0.45f * a)
    sb.strokePolylineTapered(_pvX, _pvY, n, w * 0.42f, 0.4f, mix(r, 1f, 0.75f), mix(g, 1f, 0.75f),
      mix(b, 1f, 0.75f), a, 0.45f * a)
  }

  /** A stroke that narrows and fades from full strength at (x0, y0) to nothing at (x1, y1).
   *  A stroke of uniform alpha ends in a hard edge, and a hard-edged line with a head on
   *  it is exactly the stick-with-a-ball silhouette this section exists to avoid. */
  private[projectiles] def fadeLine(sb: ShapeBatch, x0: Float, y0: Float, x1: Float, y1: Float,
                       w0: Float, r: Float, g: Float, b: Float, a0: Float, segs: Int): Unit = {
    if (a0 <= 0.01f) return
    var i = 0
    while (i < segs) {
      val t0 = i.toFloat / segs; val t1 = (i + 1).toFloat / segs
      val k = 1f - (t0 + t1) * 0.5f
      sb.strokeLineSoft(x0 + (x1 - x0) * t0, y0 + (y1 - y0) * t0,
        x0 + (x1 - x0) * t1, y0 + (y1 - y0) * t1, w0 * (0.3f + 0.7f * k), r, g, b, a0 * k * k)
      i += 1
    }
  }

  /** Stroke an ellipse with semi-axis `a` along the unit vector (mx, my) and `b` across it.
   *  ShapeBatch's ovals are axis-aligned, and a ring standing across a diagonal flight path
   *  has to turn with it or it reads as a smudge on every heading but one. */
  private[projectiles] def strokeRotEllipse(sb: ShapeBatch, cx: Float, cy: Float, mx: Float, my: Float,
                               a: Float, b: Float, w: Float, r: Float, g: Float, bl: Float,
                               alpha: Float, segs: Int): Unit = {
    if (alpha <= 0.01f) return
    val qx = -my; val qy = mx
    var prevX = cx + mx * a; var prevY = cy + my * a
    var i = 1
    while (i <= segs) {
      val t = i * (Math.PI * 2 / segs)
      val c = Math.cos(t).toFloat; val s = Math.sin(t).toFloat
      val x = cx + mx * a * c + qx * b * s
      val y = cy + my * a * c + qy * b * s
      sb.strokeLine(prevX, prevY, x, y, w, r, g, bl, alpha)
      prevX = x; prevY = y
      i += 1
    }
  }
}
