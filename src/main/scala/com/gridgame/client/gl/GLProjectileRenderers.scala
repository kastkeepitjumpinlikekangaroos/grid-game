package com.gridgame.client.gl

import com.gridgame.common.model.{Projectile, ProjectileDef, ProjectileType}

/**
 * Projectile renderers for OpenGL ShapeBatch. All 112 projectile types mapped.
 * Uses standard alpha blending for solid visible shapes, with bloom post-processor
 * providing glow on bright elements.
 */
object GLProjectileRenderers {

  type Renderer = (Projectile, Float, Float, ShapeBatch, Int) => Unit

  /** Look up renderer by projectile type. Returns null if none registered. No Option allocation. */
  def getRenderer(pType: Byte): Renderer = _rendererLUT(pType & 0xFF)

  // ═══════════════════════════════════════════════════════════════
  //  PRE-ALLOCATED ARRAY POOLS (avoid per-frame GC pressure)
  // ═══════════════════════════════════════════════════════════════

  // Spinner: max pts=4 → n=8 per array, 4 arrays (ghost xs/ys, main xs/ys)
  private val _spinXs = new Array[Float](8)
  private val _spinYs = new Array[Float](8)
  private val _spinGhostXs = new Array[Float](8)
  private val _spinGhostYs = new Array[Float](8)

  // Lightning: segs=8 → 9 entries
  private val _boltXs = new Array[Float](9)
  private val _boltYs = new Array[Float](9)

  // Thunder strike: 9 points
  private val _thunderXs = new Array[Float](9)
  private val _thunderYs = new Array[Float](9)

  // Tidal wave: 10 points
  private val _waveXs = new Array[Float](10)
  private val _waveYs = new Array[Float](10)

  // Sword wave: segs=12 → outer/inner (13 each), crescent (26)
  private val _swOuterXs = new Array[Float](13)
  private val _swOuterYs = new Array[Float](13)
  private val _swInnerXs = new Array[Float](13)
  private val _swInnerYs = new Array[Float](13)
  private val _swCrescXs = new Array[Float](26)
  private val _swCrescYs = new Array[Float](26)

  // Scythe: segs=12 → arc (26)
  private val _scyXs = new Array[Float](26)
  private val _scyYs = new Array[Float](26)

  // Holy star: 6-point star (12 vertices)
  private val _holyXs = new Array[Float](12)
  private val _holyYs = new Array[Float](12)

  // Small polygon scratch arrays (3 and 4 vertices) — shared across all renderers
  private val _polyXs3 = new Array[Float](3)
  private val _polyYs3 = new Array[Float](3)
  private val _polyXs4 = new Array[Float](4)
  private val _polyYs4 = new Array[Float](4)

  def drawGeneric(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int,
                  r: Float, g: Float, b: Float): Unit = {
    val phase = (tick + proj.id * 37) * 0.35
    // Dynamics must be computed BEFORE reading _stPulseMult/dynAlpha — they live in
    // shared mutable state, so reading them first picked up the previously drawn
    // projectile's charge and dissipation values.
    computeAllDynamics(proj, r, g, b, phase)
    val p = (0.65 + 0.35 * Math.sin(phase * _stPulseMult)).toFloat * dynAlpha
    val dr = _evoR; val dg = _evoG; val db = _evoB
    val ds = dynScale
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy

    // Speed lines
    drawSpeedLines(sx, sy, ndx, ndy, dr, dg, db, 0.25f * p, sb, 4 + (_lifePct * 2).toInt, 28f * ds)
    // Soft glow halo
    sb.fillOvalSoft(sx, sy, 36f * ds * dynGlow, 28.8f * ds * dynGlow, dr, dg, db, 0.38f * p, 0f, 18)
    // Dark cartoon outline
    sb.strokeOval(sx, sy, 24f * ds, 18f * ds, outlineW(24f * ds), outline(r), outline(g), outline(b), 0.8f * p, 14)
    // Core
    sb.fillOval(sx, sy, 22f * ds, 17f * ds, dr, dg, db, 0.95f * p, 14)
    // Cartoon highlight — offset scales with the body so it stays put when charged
    sb.fillOval(sx + KEY_LIGHT_X * 7f * ds, sy + KEY_LIGHT_Y * 5f * ds, 7f * ds, 5f * ds, 1f, 1f, 1f, 0.35f * p, 8)
    // Bright center — charge whitening
    val bc = _chgBright
    sb.fillOval(sx, sy, 10f * ds, 7f * ds,
      mix(bright(r), 1f, bc), mix(bright(g), 1f, bc), mix(bright(b), 1f, bc), 0.98f * dynAlpha, 10)
    // Trail — scaled by dynTrail
    val trailLen = 45f * dynTrail
    var i = 0; while (i < 8) {
      val t = ((tick * 0.05 + i * 0.125 + proj.id * 0.13) % 1.0).toFloat
      val taper = 1f - t * 0.65f
      val s = 14f * taper * ds
      val colorFade = 1f - t * 0.3f
      sb.fillOval(sx - ndx * t * trailLen, sy - ndy * t * trailLen, s, s * 0.7f,
        r * colorFade, g * colorFade, b * colorFade, 0.45f * (1f - t) * p, 8)
    ; i += 1 }
    // Charge crackle
    drawChargeCrackle(sx, sy, 22f * ds, r, g, b, p, sb, phase, proj.chargeLevel)
    // Boomerang return ghosts
    drawReturnGhosts(sx, sy, 22f * ds, dr, dg, db, p, sb, proj)
  }

  // ═══════════════════════════════════════════════════════════════
  //  HELPERS
  // ═══════════════════════════════════════════════════════════════

  // Mutable output fields for intToRGB — avoids tuple allocation per call
  private var _r = 0f; private var _g = 0f; private var _b = 0f
  private def intToRGB(argb: Int): Unit = {
    _r = ((argb >> 16) & 0xFF) / 255f
    _g = ((argb >> 8) & 0xFF) / 255f
    _b = (argb & 0xFF) / 255f
  }

  // Mutable output fields for screenDir — avoids tuple allocation per call
  private var _sdx = 0f; private var _sdy = 0f; private var _sdLen = 0f
  private def screenDir(proj: Projectile): Unit = {
    val sdx = ((proj.dx - proj.dy) * 20).toFloat
    val sdy = ((proj.dx + proj.dy) * 10).toFloat
    val len = Math.sqrt(sdx * sdx + sdy * sdy).toFloat
    if (len < 0.01f) { _sdx = 1f; _sdy = 0f; _sdLen = 0f }
    else { _sdx = sdx / len; _sdy = sdy / len; _sdLen = len }
  }

  // Mutable output fields for beamTip — avoids tuple allocation per call
  private var _tipX = 0f; private var _tipY = 0f
  private def beamTip(sx: Float, sy: Float, proj: Projectile, worldLen: Float): Unit = {
    screenDir(proj)
    val screenLen = worldLen * 20f
    _tipX = sx + _sdx * screenLen
    _tipY = sy + _sdy * screenLen
  }

  @inline private def bright(c: Float): Float = Math.min(1f, c * 0.3f + 0.7f)
  @inline private def dark(c: Float): Float = c * 0.5f
  @inline private def outline(c: Float): Float = c * 0.2f
  @inline private def mix(a: Float, b: Float, t: Float): Float = a + (b - a) * t
  @inline private def clampF(v: Float, lo: Float, hi: Float): Float = Math.max(lo, Math.min(hi, v))

  // ═══════════════════════════════════════════════════════════════
  //  DYNAMIC VISUAL HELPERS (charge, lifetime, color, state)
  // ═══════════════════════════════════════════════════════════════

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
  private val KEY_LIGHT_X = -0.62f
  private val KEY_LIGHT_Y = -0.78f

  /** Outline weight proportional to shape size — a flat 4px stroke swallows a 10px shape. */
  @inline private def outlineW(size: Float): Float = clampF(size * 0.14f, 1.2f, 3.2f)

  // --- Step 1: Charge-Level Visual Escalation ---
  private var _chgScale = 1f      // size multiplier 1.0→1.5
  private var _chgGlow = 1f       // glow radius multiplier 1.0→1.7
  private var _chgBright = 0f     // whiteness mix 0.0→0.35
  private var _chgTrailLen = 1f   // trail length multiplier 1.0→1.6
  private var _chgSparkCount = 0  // extra spark particles 0→3

  private def computeChargeVisuals(chargeLevel: Int): Unit = {
    val t = chargeLevel / 100f  // 0.0→1.0
    _chgScale = 1f + t * 0.5f
    _chgGlow = 1f + t * 0.7f
    _chgBright = t * 0.35f
    _chgTrailLen = 1f + t * 0.6f
    _chgSparkCount = (t * 3f).toInt
  }

  /** Draw crackle strokes around a projectile for charge > 70 */
  private def drawChargeCrackle(sx: Float, sy: Float, size: Float, r: Float, g: Float, b: Float,
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
  private var _lifePct = 0f  // 0.0 (just spawned) → 1.0 (at max range)

  private def computeLifetimeProgress(proj: Projectile): Unit = {
    val pDef = ProjectileDef.get(proj.projectileType)
    val maxR = pDef.effectiveMaxRange(proj.chargeLevel).toFloat
    _lifePct = if (maxR > 0f) clampF(proj.getDistanceTraveled / maxR, 0f, 1f) else 0f
  }

  /** Compute dissipation alpha/scale for end-of-range burn-out (last 15%) */
  @inline private def dissipationAlpha: Float = {
    if (_lifePct > 0.85f) { val t = (_lifePct - 0.85f) / 0.15f; 1f - t * 0.7f } else 1f
  }
  @inline private def dissipationScale: Float = {
    if (_lifePct > 0.85f) { val t = (_lifePct - 0.85f) / 0.15f; 1f + t * 0.3f } else 1f
  }
  /** Trail density multiplier that increases over lifetime */
  @inline private def lifetimeTrailMult: Float = 1f + _lifePct * 0.25f
  /** Glow radius growth over lifetime */
  @inline private def lifetimeGlowMult: Float = 1f + _lifePct * 0.12f

  // --- Step 3: Dynamic Color Shifting ---
  private var _evoR = 0f; private var _evoG = 0f; private var _evoB = 0f

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
  private var _stPulseMult = 1f     // pulse speed multiplier
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
  private def drawReturnGhosts(sx: Float, sy: Float, size: Float, r: Float, g: Float, b: Float,
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
  private def computeAllDynamics(proj: Projectile, r: Float, g: Float, b: Float, phase: Double): Unit = {
    computeChargeVisuals(proj.chargeLevel)
    computeLifetimeProgress(proj)
    computeColorEvolution(r, g, b, proj, phase)
    computeStateVisuals(proj)
  }

  // All four are clamped: they multiply together at the call sites, so an
  // unclamped product turns escalation into screen-filling haze.
  /** Combined dynamic size multiplier */
  @inline private def dynScale: Float = Math.min(_chgScale * _stSizeMult * dissipationScale, MAX_DYN_SCALE)
  /** Combined dynamic alpha multiplier */
  @inline private def dynAlpha: Float = dissipationAlpha
  /** Combined glow multiplier */
  @inline private def dynGlow: Float = Math.min(_chgGlow * lifetimeGlowMult, MAX_DYN_GLOW)
  /** Combined trail length multiplier */
  @inline private def dynTrail: Float = Math.min(_chgTrailLen * _stTrailMult * lifetimeTrailMult, MAX_DYN_TRAIL)

  /** Cartoon sparkle star — 4-point star burst.
   *  Stroke width tracks size: a fixed 2px cross turned small sparkles into blobs
   *  and left large ones looking like thin scratches. */
  private def drawSparkleStar(sx: Float, sy: Float, size: Float, r: Float, g: Float, b: Float,
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
  private def drawSpeedLines(sx: Float, sy: Float, ndx: Float, ndy: Float,
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
  private def drawRibbonTrail(sx: Float, sy: Float, ndx: Float, ndy: Float,
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
  private def drawSparkBurst(sx: Float, sy: Float, r: Float, g: Float, b: Float,
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

  // ═══════════════════════════════════════════════════════════════
  //  WEAPON / OBJECT SILHOUETTES
  // ═══════════════════════════════════════════════════════════════
  //
  // A thrown object is authored once in a local frame — +x along the object, +y across
  // it, both roughly within [-1.3, 1.3] — and stamped through `blitPart`, which rotates
  // it, squashes y into the isometric ground plane and scales it to the projectile's
  // size.
  //
  // This exists because the old `spinner` built its outline from a polar radius per
  // vertex, which can only ever describe a star: an axe, a katana, a femur and a playing
  // card all came out as the same spinning lens. A silhouette in a local frame can carry
  // a haft at one end and a head at the other, so it still reads as an axe at every spin
  // angle — which is the whole point of giving a character a themed weapon.
  //
  // Parts must be convex: ShapeBatch.fillPolygon fan-triangulates from vertex 0. Curved
  // blades are therefore split into two convex spans rather than described in one loop.

  private val ISO_Y = 0.6f

  /** One convex piece of a silhouette. `tint` blends the part toward the projectile's
   *  registered colour, so steel stays steel while the energy parts take the character's
   *  palette. */
  private final class Part(val xs: Array[Float], val ys: Array[Float],
                           val r: Float, val g: Float, val b: Float, val tint: Float) {
    val n: Int = xs.length
  }
  private def part(pts: Array[Float], r: Float, g: Float, b: Float, tint: Float): Part = {
    val n = pts.length / 2
    val xs = new Array[Float](n); val ys = new Array[Float](n)
    var i = 0; while (i < n) { xs(i) = pts(i * 2); ys(i) = pts(i * 2 + 1); i += 1 }
    new Part(xs, ys, r, g, b, tint)
  }

  // Scratch for the transformed polygon. Sized for the largest part plus headroom.
  private val _shpXs = new Array[Float](16)
  private val _shpYs = new Array[Float](16)

  /** Rotate/squash/scale a part's local points into _shpXs/_shpYs. */
  @inline private def blitPart(p: Part, cx: Float, cy: Float, ca: Float, sa: Float, s: Float,
                               flipY: Float, sclX: Float): Unit = {
    var i = 0; while (i < p.n) {
      val lx = p.xs(i) * sclX; val ly = p.ys(i) * flipY
      _shpXs(i) = cx + (lx * ca - ly * sa) * s
      _shpYs(i) = cy + (lx * sa + ly * ca) * s * ISO_Y
      i += 1
    }
  }

  /** Transform a single local point into (_ptX, _ptY). */
  private var _ptX = 0f; private var _ptY = 0f
  @inline private def blitPoint(lx: Float, ly: Float, cx: Float, cy: Float,
                                ca: Float, sa: Float, s: Float): Unit = {
    _ptX = cx + (lx * ca - ly * sa) * s
    _ptY = cy + (lx * sa + ly * ca) * s * ISO_Y
  }

  /** Draw a whole silhouette: dark contour behind, then each part's body. The contour is
   *  stroked before the fill so exactly half of its width survives — the same read as the
   *  sprite sheets' `add_contour`, which is what keeps these legible over pale sand. */
  private def drawParts(sb: ShapeBatch, parts: Array[Part], cx: Float, cy: Float,
                        ca: Float, sa: Float, s: Float, tr: Float, tg: Float, tb: Float,
                        alpha: Float, outlineW: Float, flipY: Float = 1f, sclX: Float = 1f): Unit = {
    if (alpha <= 0.01f) return
    var i = 0; while (i < parts.length) {
      val pt = parts(i)
      blitPart(pt, cx, cy, ca, sa, s, flipY, sclX)
      if (outlineW > 0f) sb.strokePolygon(_shpXs, _shpYs, pt.n, outlineW, 0.06f, 0.05f, 0.07f, 0.85f * alpha)
      sb.fillPolygon(_shpXs, _shpYs, pt.n,
        mix(pt.r, tr, pt.tint), mix(pt.g, tg, pt.tint), mix(pt.b, tb, pt.tint), alpha)
      i += 1
    }
  }

  /** Flat silhouette pass used for motion-blur ghosts — one colour, no contour. */
  private def drawPartsFlat(sb: ShapeBatch, parts: Array[Part], cx: Float, cy: Float,
                            ca: Float, sa: Float, s: Float,
                            r: Float, g: Float, b: Float, alpha: Float, flipY: Float = 1f,
                            sclX: Float = 1f): Unit = {
    if (alpha <= 0.01f) return
    var i = 0; while (i < parts.length) {
      val pt = parts(i)
      blitPart(pt, cx, cy, ca, sa, s, flipY, sclX)
      sb.fillPolygon(_shpXs, _shpYs, pt.n, r, g, b, alpha)
      i += 1
    }
  }

  // ── Material palette shared by the silhouettes ──
  private val WOOD_R = 0.58f; private val WOOD_G = 0.42f; private val WOOD_B = 0.25f
  private val DKWOOD_R = 0.30f; private val DKWOOD_G = 0.20f; private val DKWOOD_B = 0.12f
  private val STEEL_R = 0.84f; private val STEEL_G = 0.87f; private val STEEL_B = 0.93f
  private val DKSTEEL_R = 0.46f; private val DKSTEEL_G = 0.50f; private val DKSTEEL_B = 0.58f
  private val BONE_R = 0.93f; private val BONE_G = 0.91f; private val BONE_B = 0.83f
  private val GOLD_R = 0.86f; private val GOLD_G = 0.70f; private val GOLD_B = 0.30f
  private val LEATHER_R = 0.24f; private val LEATHER_G = 0.19f; private val LEATHER_B = 0.20f

  // ── Weapon kinds ──
  private val WPN_AXE = 0
  private val WPN_KATANA = 1
  private val WPN_KNIFE = 2
  private val WPN_SWORD = 3
  private val WPN_BONE = 4
  private val WPN_CURSED = 5
  private val WPN_CARD = 6
  private val WPN_BONE_AXE = 7

  private val AXE_PARTS = Array(
    part(Array(-1.05f,-0.11f, 0.55f,-0.11f, 0.55f,0.11f, -1.05f,0.11f), WOOD_R, WOOD_G, WOOD_B, 0.12f),
    part(Array(-1.26f,-0.17f, -0.96f,-0.17f, -0.96f,0.17f, -1.26f,0.17f), DKSTEEL_R, DKSTEEL_G, DKSTEEL_B, 0.35f),
    part(Array(0.28f,-0.14f, 0.44f,-0.86f, 0.80f,-1.02f, 1.22f,-0.60f, 1.24f,0.02f, 0.66f,0.18f),
      STEEL_R, STEEL_G, STEEL_B, 0.30f),
    part(Array(0.52f,-0.92f, 0.84f,-1.06f, 1.30f,-0.58f, 1.10f,-0.46f), 0.98f, 0.99f, 1f, 0.10f)
  )
  private val BONE_AXE_PARTS = Array(
    part(Array(-1.05f,-0.12f, 0.48f,-0.12f, 0.48f,0.12f, -1.05f,0.12f), BONE_R * 0.92f, BONE_G * 0.90f, BONE_B * 0.86f, 0.10f),
    // Broad jawbone bit
    part(Array(0.18f,-0.18f, 0.34f,-0.84f, 0.78f,-1.04f, 1.26f,-0.66f, 1.30f,-0.02f, 0.60f,0.24f),
      BONE_R, BONE_G, BONE_B, 0.22f),
    // Teeth standing proud of the cutting edge
    part(Array(0.44f,-0.86f, 0.58f,-1.20f, 0.74f,-0.96f), BONE_R, BONE_G, BONE_B, 0.15f),
    part(Array(0.80f,-1.02f, 1.02f,-1.24f, 1.06f,-0.86f), BONE_R, BONE_G, BONE_B, 0.15f),
    part(Array(1.16f,-0.72f, 1.46f,-0.62f, 1.28f,-0.30f), BONE_R, BONE_G, BONE_B, 0.15f),
    // Socket where haft meets bone
    part(Array(0.20f,-0.24f, 0.44f,-0.24f, 0.44f,0.24f, 0.20f,0.24f), 0.52f, 0.44f, 0.36f, 0.25f)
  )
  private val KATANA_PARTS = Array(
    part(Array(-1.10f,-0.10f, -0.34f,-0.10f, -0.34f,0.10f, -1.10f,0.10f), LEATHER_R, LEATHER_G, LEATHER_B, 0.15f),
    part(Array(-0.42f,-0.30f, -0.22f,-0.30f, -0.22f,0.30f, -0.42f,0.30f), GOLD_R, GOLD_G, GOLD_B, 0.20f),
    part(Array(-0.22f,-0.11f, 0.42f,-0.17f, 0.42f,0.05f, -0.22f,0.11f), STEEL_R, STEEL_G, STEEL_B, 0.22f),
    part(Array(0.42f,-0.17f, 0.98f,-0.16f, 1.30f,-0.03f, 0.98f,0.01f, 0.42f,0.05f), STEEL_R, STEEL_G, STEEL_B, 0.22f),
    // Hamon: the temper line that makes a katana read as a katana and not a steel bar
    part(Array(-0.16f,-0.05f, 0.42f,-0.11f, 0.98f,-0.10f, 1.16f,-0.03f, 0.98f,-0.05f, 0.42f,-0.06f, -0.16f,-0.01f),
      1f, 1f, 1f, 0.06f)
  )
  private val KNIFE_PARTS = Array(
    part(Array(-1.05f,-0.13f, -0.28f,-0.11f, -0.28f,0.13f, -1.05f,0.15f), DKWOOD_R, DKWOOD_G, DKWOOD_B, 0.12f),
    part(Array(-0.36f,-0.13f, -0.18f,-0.15f, -0.18f,0.15f, -0.36f,0.15f), GOLD_R, GOLD_G, GOLD_B, 0.20f),
    part(Array(-0.18f,-0.16f, 0.75f,-0.13f, 1.18f,0.02f, 0.30f,0.22f, -0.18f,0.16f), STEEL_R, STEEL_G, STEEL_B, 0.22f),
    part(Array(-0.10f,-0.10f, 0.72f,-0.08f, 1.02f,0.0f, 0.30f,0.06f, -0.10f,0.0f), 0.98f, 0.99f, 1f, 0.08f)
  )
  private val SWORD_PARTS = Array(
    part(Array(-1.05f,-0.10f, -0.30f,-0.10f, -0.30f,0.10f, -1.05f,0.10f), DKWOOD_R, DKWOOD_G, DKWOOD_B, 0.15f),
    part(Array(-1.22f,-0.18f, -1.00f,-0.18f, -1.00f,0.18f, -1.22f,0.18f), GOLD_R, GOLD_G, GOLD_B, 0.30f),
    part(Array(-0.40f,-0.54f, -0.18f,-0.54f, -0.18f,0.54f, -0.40f,0.54f), GOLD_R, GOLD_G, GOLD_B, 0.30f),
    part(Array(-0.18f,-0.17f, 0.85f,-0.15f, 1.28f,0f, 0.85f,0.15f, -0.18f,0.17f), 0.98f, 0.96f, 0.82f, 0.35f),
    part(Array(-0.14f,-0.05f, 0.88f,-0.04f, 1.10f,0f, 0.88f,0.04f, -0.14f,0.05f), 1f, 1f, 0.94f, 0.12f)
  )
  private val BONE_PARTS = Array(
    part(Array(-0.86f,-0.15f, 0.86f,-0.15f, 0.86f,0.15f, -0.86f,0.15f), BONE_R, BONE_G, BONE_B, 0.15f)
  )
  private val CURSED_PARTS = Array(
    part(Array(-1.10f,-0.11f, -0.42f,-0.11f, -0.42f,0.11f, -1.10f,0.11f), 0.13f, 0.09f, 0.12f, 0.20f),
    part(Array(-0.52f,-0.46f, -0.30f,-0.52f, -0.30f,0.52f, -0.52f,0.46f), 0.34f, 0.10f, 0.16f, 0.55f),
    part(Array(-0.30f,-0.24f, 0.34f,-0.30f, 0.34f,0.12f, -0.30f,0.24f), 0.22f, 0.08f, 0.13f, 0.45f),
    part(Array(0.34f,-0.30f, 0.80f,-0.14f, 1.30f,-0.02f, 0.36f,0.12f), 0.22f, 0.08f, 0.13f, 0.45f),
    // Glowing edge — this is the part that carries the character's colour
    part(Array(0.30f,-0.24f, 0.80f,-0.10f, 1.24f,-0.02f, 0.78f,-0.03f, 0.32f,-0.16f), 1f, 0.55f, 0.55f, 0.75f)
  )
  private val CARD_PARTS = Array(
    part(Array(-0.72f,-0.50f, 0.72f,-0.50f, 0.72f,0.50f, -0.72f,0.50f), 0.97f, 0.97f, 0.99f, 0.06f)
  )

  private def weaponParts(kind: Int): Array[Part] = kind match {
    case WPN_AXE      => AXE_PARTS
    case WPN_KATANA   => KATANA_PARTS
    case WPN_KNIFE    => KNIFE_PARTS
    case WPN_SWORD    => SWORD_PARTS
    case WPN_BONE     => BONE_PARTS
    case WPN_CURSED   => CURSED_PARTS
    case WPN_CARD     => CARD_PARTS
    case WPN_BONE_AXE => BONE_AXE_PARTS
    case _            => AXE_PARTS
  }

  /** Per-kind extras that a convex-polygon list cannot express (round bone knobs, card
   *  pips, the cursed blade's aura). Drawn after the parts. */
  private def weaponDetail(sb: ShapeBatch, kind: Int, cx: Float, cy: Float,
                           ca: Float, sa: Float, s: Float,
                           tr: Float, tg: Float, tb: Float, alpha: Float): Unit = kind match {
    case WPN_BONE =>
      var i = 0; while (i < 4) {
        val lx = if (i < 2) -0.94f else 0.94f
        val ly = if ((i & 1) == 0) -0.22f else 0.22f
        blitPoint(lx, ly, cx, cy, ca, sa, s)
        val kr = 0.27f * s
        sb.fillOval(_ptX, _ptY, kr + 1.2f, kr + 1.2f, 0.06f, 0.05f, 0.07f, 0.8f * alpha, 10)
        sb.fillOval(_ptX, _ptY, kr, kr, BONE_R, BONE_G, BONE_B, alpha, 10)
        sb.fillOval(_ptX - kr * 0.28f, _ptY - kr * 0.28f, kr * 0.42f, kr * 0.42f, 1f, 1f, 0.97f, 0.55f * alpha, 8)
        i += 1
      }
    case WPN_BONE_AXE =>
      blitPoint(-1.14f, 0f, cx, cy, ca, sa, s)
      val kr = 0.24f * s
      sb.fillOval(_ptX, _ptY, kr + 1.2f, kr + 1.2f, 0.06f, 0.05f, 0.07f, 0.8f * alpha, 10)
      sb.fillOval(_ptX, _ptY, kr, kr, BONE_R, BONE_G, BONE_B, alpha, 10)
    case WPN_CARD =>
      // Suit pip in the middle plus corner marks, so it reads as a card and not a tile
      blitPoint(0f, 0f, cx, cy, ca, sa, s)
      val px = _ptX; val py = _ptY
      sb.fillOval(px, py, s * 0.20f, s * 0.20f * ISO_Y + s * 0.05f, tr * 0.7f, tg * 0.25f, tb * 0.3f, 0.9f * alpha, 10)
      var i = 0; while (i < 2) {
        val sgn = if (i == 0) -1f else 1f
        blitPoint(sgn * 0.48f, sgn * 0.30f, cx, cy, ca, sa, s)
        sb.fillOval(_ptX, _ptY, s * 0.09f, s * 0.09f, tr * 0.7f, tg * 0.25f, tb * 0.3f, 0.85f * alpha, 8)
        i += 1
      }
    case WPN_CURSED =>
      blitPoint(0.80f, -0.10f, cx, cy, ca, sa, s)
      sb.fillOvalSoft(_ptX, _ptY, s * 0.55f, s * 0.42f, tr, tg * 0.4f, tb * 0.5f, 0.45f * alpha, 0f, 12)
    case _ => ()
  }

  // ═══════════════════════════════════════════════════════════════
  //  PATTERN FACTORIES
  // ═══════════════════════════════════════════════════════════════

  /** Large glowing energy orb with style variants, spiral particles, ribbon trail and spark burst */
  private def energyBolt(r: Float, g: Float, b: Float, size: Float = 20f, style: Int = 0): Renderer =
    (proj, sx, sy, sb, tick) => {
      val phase = (tick + proj.id * 37) * 0.35
      computeAllDynamics(proj, r, g, b, phase)
      val p = (0.65 + 0.35 * Math.sin(phase * _stPulseMult)).toFloat * dynAlpha
      val dr = _evoR; val dg = _evoG; val db = _evoB
      // Breathing animation: subtle 8% size oscillation
      val breath = (0.92f + 0.08f * Math.sin(phase * 0.8).toFloat)
      val sz = size * 1.4f * dynScale * breath

      screenDir(proj)
      val ndx = _sdx; val ndy = _sdy

      // Manga speed lines behind
      drawSpeedLines(sx, sy, ndx, ndy, dr, dg, db, 0.25f * p, sb, 5 + (_lifePct * 2).toInt, sz * 1.5f)

      // Large soft halo — dramatic glow, charge-scaled
      sb.fillOvalSoft(sx, sy, sz * 3.2f * dynGlow, sz * 2.6f * dynGlow, dr, dg, db, 0.4f * p, 0f, 18)

      // Outer silhouette by style — drawn behind the orb so it breaks the ellipse
      val pxv = -ndy; val pyv = ndx
      style match {
        case 1 =>
          // Fire: tongues licking off the orb, longest on the trailing side
          var f = 0; while (f < 8) {
            val a = f * Math.PI * 2 / 8 + phase * 0.5
            val ca2 = Math.cos(a).toFloat; val sa2 = Math.sin(a).toFloat
            val back = 0.5f + 0.5f * (1f - (ca2 * ndx + sa2 * ndy))
            val lick = (0.45f + 0.55f * Math.sin(phase * 4.2 + f * 1.9).toFloat) * back
            sb.strokeLineSoft(sx + ca2 * sz * 0.40f, sy + sa2 * sz * 0.30f,
              sx + ca2 * sz * (0.62f + 0.85f * lick), sy + sa2 * sz * (0.48f + 0.62f * lick),
              sz * 0.34f, 1f, mix(0.32f, 0.92f, lick), 0.12f, 0.78f * p)
            f += 1
          }
        case 2 =>
          // Rune ring: glyph ticks orbiting the orb on two counter-rotating rings
          sb.strokeOval(sx, sy, sz * 0.88f, sz * 0.64f, 2.8f, outline(r), outline(g), outline(b), 0.55f * p, 16)
          sb.strokeOval(sx, sy, sz * 0.88f, sz * 0.64f, 1.8f, mix(bright(r), 1f, 0.35f),
            mix(bright(g), 1f, 0.35f), mix(bright(b), 1f, 0.35f), 0.85f * p, 16)
          sb.strokeOval(sx, sy, sz * 1.12f, sz * 0.81f, 1.4f, bright(r), bright(g), bright(b), 0.5f * p, 16)
          var k = 0; while (k < 6) {
            val a = -phase * 0.9 + k * Math.PI / 3
            val ca2 = Math.cos(a).toFloat; val sa2 = Math.sin(a).toFloat
            val gx = sx + ca2 * sz * 0.95f; val gy = sy + sa2 * sz * 0.69f
            sb.strokeLine(gx - sa2 * sz * 0.13f, gy + ca2 * sz * 0.09f,
              gx + sa2 * sz * 0.13f, gy - ca2 * sz * 0.09f, 5.5f,
              outline(r), outline(g), outline(b), 0.6f * p)
            sb.strokeLine(gx - sa2 * sz * 0.12f, gy + ca2 * sz * 0.08f,
              gx + sa2 * sz * 0.12f, gy - ca2 * sz * 0.08f, 3.2f,
              mix(bright(r), 1f, 0.45f), mix(bright(g), 1f, 0.45f), mix(bright(b), 1f, 0.45f), 0.95f * p)
            k += 1
          }
        case 3 =>
          // Soul wisp: a tapering tail streaming behind the head, plus hollow eyes
          val waver = Math.sin(phase * 2.2).toFloat * sz * 0.42f
          _polyXs4(0) = sx + pxv * sz * 0.62f; _polyYs4(0) = sy + pyv * sz * 0.62f
          _polyXs4(1) = sx - ndx * sz * 1.7f + pxv * waver; _polyYs4(1) = sy - ndy * sz * 1.7f + pyv * waver
          _polyXs4(2) = sx - ndx * sz * 2.5f + pxv * waver * 1.6f; _polyYs4(2) = sy - ndy * sz * 2.5f + pyv * waver * 1.6f
          _polyXs4(3) = sx - pxv * sz * 0.62f; _polyYs4(3) = sy - pyv * sz * 0.62f
          sb.fillPolygon(_polyXs4, _polyYs4, 4, dr, dg, db, 0.72f * p)
          _polyXs4(0) = sx + pxv * sz * 0.34f; _polyYs4(0) = sy + pyv * sz * 0.34f
          _polyXs4(1) = sx - ndx * sz * 1.3f + pxv * waver * 0.7f; _polyYs4(1) = sy - ndy * sz * 1.3f + pyv * waver * 0.7f
          _polyXs4(2) = sx - ndx * sz * 1.9f + pxv * waver; _polyYs4(2) = sy - ndy * sz * 1.9f + pyv * waver
          _polyXs4(3) = sx - pxv * sz * 0.34f; _polyYs4(3) = sy - pyv * sz * 0.34f
          sb.fillPolygon(_polyXs4, _polyYs4, 4, bright(r), bright(g), bright(b), 0.5f * p)
          // Torn hem where the wisp frays out
          var w = 0; while (w < 3) {
            val wt = (w - 1) * 0.55f
            val bxw = sx - ndx * sz * 2.4f + pxv * (waver * 1.5f + wt * sz * 0.5f)
            val byw = sy - ndy * sz * 2.4f + pyv * (waver * 1.5f + wt * sz * 0.5f)
            sb.strokeLineSoft(sx - ndx * sz * 1.5f, sy - ndy * sz * 1.5f, bxw, byw,
              sz * 0.16f, dr, dg, db, 0.42f * p)
            w += 1
          }
        case 4 =>
          // Nebula: an irregular cloud boundary so the outline is never a clean ellipse
          var c = 0; while (c < 6) {
            val a = c * Math.PI * 2 / 6 + phase * 0.35
            val puff = 0.72f + 0.28f * Math.sin(phase * 1.7 + c * 2.3).toFloat
            sb.fillOvalSoft(sx + Math.cos(a).toFloat * sz * 0.62f, sy + Math.sin(a).toFloat * sz * 0.46f,
              sz * 0.62f * puff, sz * 0.50f * puff, dr, dg, db, 0.48f * p, 0.04f * p, 10)
            c += 1
          }
        case _ =>
          // Plain orb: a hard leading crescent, which is what stops it reading as a dot
          val th0 = Math.atan2(ndy / ISO_Y, ndx).toFloat
          sb.fillArcBand(sx, sy, sz * 0.46f, sz * 0.46f * ISO_Y, sz * 0.74f, sz * 0.74f * ISO_Y,
            th0 - 1.15f, 2.3f, 12, bright(r), bright(g), bright(b), 0.7f * p, 0.7f * p)
      }

      // Bold dark cartoon outline
      sb.strokeOval(sx, sy, sz * 0.95f, sz * 0.72f, 3.5f,
        outline(r), outline(g), outline(b), 0.8f * p, 14)

      // Main orb body — use evolved colors for body
      sb.fillOval(sx, sy, sz * 0.9f, sz * 0.68f, dr, dg, db, 0.95f * p, 14)
      // Inner glow core offset in travel direction — charge whitening
      val bc = _chgBright
      sb.fillOval(sx + ndx * sz * 0.1f, sy + ndy * sz * 0.1f, sz * 0.55f, sz * 0.4f,
        mix(dr, 1f, 0.4f + bc), mix(dg, 1f, 0.3f + bc), mix(db, 1f, 0.2f + bc), 0.9f * p, 12)

      // Style-specific inner patterns (drawn between body fill and orbiting particles)
      if (style == 1) {
        // Swirl: 3 spiral arms rotating inside the orb
        var arm = 0; while (arm < 3) {
          val armBase = phase * 2.5 + arm * Math.PI * 2 / 3
          var seg = 0; while (seg < 4) {
            val t0 = seg.toFloat / 4
            val t1 = (seg + 1).toFloat / 4
            val a0 = armBase + t0 * Math.PI * 1.5
            val a1 = armBase + t1 * Math.PI * 1.5
            val r0 = sz * 0.15f + t0 * sz * 0.55f
            val r1 = sz * 0.15f + t1 * sz * 0.55f
            sb.strokeLineSoft(
              sx + Math.cos(a0).toFloat * r0, sy + Math.sin(a0).toFloat * r0 * 0.55f,
              sx + Math.cos(a1).toFloat * r1, sy + Math.sin(a1).toFloat * r1 * 0.55f,
              2f, bright(r), bright(g), bright(b), 0.4f * (1f - t0 * 0.5f) * p)
          ; seg += 1 }
        ; arm += 1 }
      } else if (style == 2) {
        // Pulse Ring: 3 concentric rings pulsing at different rates
        var ring = 0; while (ring < 3) {
          val ringPulse = (0.7f + 0.3f * Math.sin(phase * (2.0 + ring * 0.7) + ring * 1.5).toFloat)
          val ringR = sz * (0.25f + ring * 0.2f) * ringPulse
          sb.strokeOval(sx, sy, ringR, ringR * 0.7f, 1.5f,
            mix(r, 1f, 0.3f), mix(g, 1f, 0.3f), mix(b, 1f, 0.3f),
            0.35f * (1f - ring * 0.1f) * p, 10)
        ; ring += 1 }
      } else if (style == 3) {
        // Crackle: 5 internal lightning-like fractures radiating from center
        var crack = 0; while (crack < 5) {
          val cAngle = phase * 1.2 + crack * Math.PI * 2 / 5
          val cLen = sz * 0.55f * (0.5f + 0.5f * Math.sin(phase * 4.5 + crack * 3.1).toFloat)
          val jitterX = Math.sin(phase * 7.3 + crack * 5.7).toFloat * sz * 0.08f
          val jitterY = Math.cos(phase * 6.1 + crack * 4.3).toFloat * sz * 0.06f
          val cx1 = sx + Math.cos(cAngle).toFloat * cLen * 0.5f + jitterX
          val cy1 = sy + Math.sin(cAngle).toFloat * cLen * 0.5f * 0.55f + jitterY
          val cx2 = sx + Math.cos(cAngle).toFloat * cLen
          val cy2 = sy + Math.sin(cAngle).toFloat * cLen * 0.55f
          sb.strokeLine(sx, sy, cx1, cy1, 1.5f,
            bright(r), bright(g), bright(b), 0.45f * p)
          sb.strokeLine(cx1, cy1, cx2, cy2, 1f,
            bright(r), bright(g), bright(b), 0.3f * p)
        ; crack += 1 }
      } else if (style == 4) {
        // Nebula: 4 shifting color-mixed cloud patches
        var cloud = 0; while (cloud < 4) {
          val cAngle = phase * 0.9 + cloud * Math.PI * 0.5
          val cDist = sz * 0.28f * (0.7f + 0.3f * Math.sin(phase * 1.5 + cloud * 2.3).toFloat)
          val cx = sx + Math.cos(cAngle).toFloat * cDist
          val cy = sy + Math.sin(cAngle).toFloat * cDist * 0.55f
          val cSize = sz * 0.35f * (0.8f + 0.2f * Math.sin(phase * 2.1 + cloud * 1.7).toFloat)
          // Complementary hue shift: rotate RGB components
          val shift = (cloud % 3)
          val cr = if (shift == 0) mix(r, b, 0.4f) else if (shift == 1) mix(r, g, 0.3f) else r
          val cg = if (shift == 0) mix(g, r, 0.4f) else if (shift == 1) g else mix(g, b, 0.3f)
          val cb = if (shift == 0) b else if (shift == 1) mix(b, r, 0.3f) else mix(b, g, 0.4f)
          sb.fillOvalSoft(cx, cy, cSize, cSize * 0.7f, cr, cg, cb, 0.3f * p, 0f, 10)
        ; cloud += 1 }
      }

      if (style == 3) {
        // Two hollow eyes: the cheapest possible cue that this is a spirit, not an orb
        val eo = sz * 0.20f
        sb.fillOval(sx + pxv * eo + ndx * sz * 0.12f, sy + pyv * eo + ndy * sz * 0.12f,
          sz * 0.11f, sz * 0.14f, outline(r), outline(g), outline(b), 0.85f * p, 8)
        sb.fillOval(sx - pxv * eo + ndx * sz * 0.12f, sy - pyv * eo + ndy * sz * 0.12f,
          sz * 0.11f, sz * 0.14f, outline(r), outline(g), outline(b), 0.85f * p, 8)
      }

      // Cartoon highlight (top-left specular spot)
      sb.fillOval(sx - sz * 0.15f, sy - sz * 0.12f, sz * 0.25f, sz * 0.18f,
        1f, 1f, 1f, 0.55f * p, 8)
      // Hot center — charge whitening
      sb.fillOval(sx, sy, sz * 0.22f, sz * 0.16f,
        mix(bright(r), 1f, bc), mix(bright(g), 1f, bc), mix(bright(b), 1f, bc), 0.98f * dynAlpha, 8)

      // 6 + charge spark orbiting particles
      var s = 0; while (s < 6 + _chgSparkCount + _stExtraSparks) {
        val orbSpeed = 3.0 + s * 0.4
        val orbEcc = 0.45f + (s % 3) * 0.08f
        val pSize = 3f + (s % 3) * 1.5f
        val sa = phase * orbSpeed + s * Math.PI / 3
        val sd = sz * 0.55f + Math.sin(phase * 2 + s * 1.3).toFloat * sz * 0.12f
        val spx = sx + Math.cos(sa).toFloat * sd
        val spy = sy + Math.sin(sa).toFloat * sd * orbEcc
        val sparkAlpha = (0.5 + 0.4 * Math.sin(phase * 4 + s * 2.1)).toFloat * p
        // Mini trail behind each particle
        val prevA = sa - 0.4
        val prevX = sx + Math.cos(prevA).toFloat * sd
        val prevY = sy + Math.sin(prevA).toFloat * sd * orbEcc
        sb.strokeLineSoft(prevX, prevY, spx, spy, 1.5f, bright(r), bright(g), bright(b), sparkAlpha * 0.4f)
        sb.fillOval(spx, spy, pSize, pSize * 0.7f, bright(r), bright(g), bright(b), sparkAlpha, 8)
      ; s += 1 }

      // Ribbon trail — longer and denser, scaled by dynTrail
      drawRibbonTrail(sx, sy, ndx, ndy, r, g, b, p, sb, tick, proj.id,
        12, sz * 4.5f * dynTrail, sz * 0.45f, sz * 0.06f)

      // Spark burst at front — more particles
      drawSparkBurst(sx + ndx * sz * 0.6f, sy + ndy * sz * 0.6f,
        r, g, b, p, sb, tick, proj.id, 6 + _chgSparkCount, sz * 0.8f)

      // Cartoon sparkle stars popping around the orb
      var star = 0; while (star < 3) {
        val starPhase = ((phase * 0.6 + star * 0.33) % 1.0).toFloat
        val starAngle = phase * 1.5 + star * Math.PI * 2 / 3
        val starDist = sz * (0.6f + starPhase * 0.5f)
        val starX = sx + Math.cos(starAngle).toFloat * starDist
        val starY = sy + Math.sin(starAngle).toFloat * starDist * 0.55f
        val starAlpha = 0.6f * (1f - starPhase) * p
        drawSparkleStar(starX, starY, 4f + (1f - starPhase) * 4f,
          bright(r), bright(g), bright(b), starAlpha, sb, phase * 2 + star)
      ; star += 1 }

      // Charge crackle
      drawChargeCrackle(sx, sy, sz, r, g, b, p, sb, phase, proj.chargeLevel)
      // Boomerang return ghosts
      drawReturnGhosts(sx, sy, sz * 0.9f, dr, dg, db, p, sb, proj)
    }

  // Beam style constants — passed as `style` parameter to beamProj
  // Each style overlays a unique visual personality on the base tapered beam:
  //   0 LASER     — sharp focused, scan lines, shock-rings traveling forward
  //   1 DRAIN     — particles & pulse waves flow BACK to origin (siphon)
  //   2 WHIP      — sinuous curving organic beam with segmented bumps
  //   3 ICE       — angular frost shards growing outward at intervals
  //   4 VINE      — curved beam, thorns/leaves sprouting at intervals
  //   5 STONE     — chunky crystals with falling dust, slow shimmer
  //   6 RAILGUN   — pencil-thin, shock cone at origin, perpendicular shock rings
  //   7 GRAVITY   — warped path, dark core void, particles swirling inward
  private val BEAM_LASER   = 0
  private val BEAM_DRAIN   = 1
  private val BEAM_WHIP    = 2
  private val BEAM_ICE     = 3
  private val BEAM_VINE    = 4
  private val BEAM_STONE   = 5
  private val BEAM_RAILGUN = 6
  private val BEAM_GRAVITY = 7

  /** Focused energy stream — tapers from origin to tip, with style-specific personality overlays */
  private def beamProj(r: Float, g: Float, b: Float, worldLen: Float = 6f, width: Float = 8f,
                       style: Int = 0): Renderer =
    (proj, sx, sy, sb, tick) => {
      beamTip(sx, sy, proj, worldLen)
      val tipX = _tipX; val tipY = _tipY
      val phase = (tick + proj.id * 29) * 0.35
      computeAllDynamics(proj, r, g, b, phase)
      val p = (0.7 + 0.3 * Math.sin(phase * _stPulseMult)).toFloat * dynAlpha
      val dr = _evoR; val dg = _evoG; val db = _evoB
      val w = width * dynScale
      val dx = tipX - sx; val dy = tipY - sy
      val beamLen = Math.sqrt(dx * dx + dy * dy).toFloat
      val perpX = if (beamLen > 1) -dy / beamLen else 0f
      val perpY = if (beamLen > 1) dx / beamLen else 0f
      val pId = proj.id.toDouble

      // Per-style beam path bend amplitude (breaks the straight-line silhouette)
      val bendAmp = style match {
        case BEAM_WHIP    => w * 0.95f
        case BEAM_VINE    => w * 0.65f
        case BEAM_GRAVITY => w * 0.55f
        case BEAM_DRAIN   => w * 0.32f
        case BEAM_STONE   => w * 0.22f
        case BEAM_ICE     => w * 0.18f
        case BEAM_RAILGUN => 0f
        case _            => w * 0.28f
      }
      @inline def pathOff(t: Float): Float = style match {
        case BEAM_WHIP    =>
          (Math.sin(phase * 2.2 + t * Math.PI * 3.5 + pId * 0.3).toFloat * bendAmp +
            Math.sin(phase * 1.4 + t * Math.PI * 1.6).toFloat * bendAmp * 0.45f)
        case BEAM_VINE    => Math.sin(phase * 1.1 + t * Math.PI * 1.7 + pId * 0.4).toFloat * bendAmp
        case BEAM_GRAVITY => Math.sin(phase * 0.9 + t * Math.PI * 0.9).toFloat * bendAmp
        case BEAM_RAILGUN => 0f
        case _            => Math.sin(phase * 1.8 + t * 5.0 + pId * 0.5).toFloat * bendAmp
      }

      // Curved styles use more segments for smoothness
      val segs = if (style == BEAM_WHIP || style == BEAM_VINE) 10
                 else if (style == BEAM_RAILGUN) 4
                 else 6

      // Width-taper falloff (RAILGUN tapers harder, STONE/ICE less)
      val taperEnd = style match {
        case BEAM_RAILGUN => 0.78f
        case BEAM_STONE   => 0.3f
        case BEAM_ICE     => 0.32f
        case BEAM_WHIP    => 0.45f
        case _            => 0.6f
      }

      // Tapered beam path with style-specific perpendicular displacement
      var seg = 0; while (seg < segs) {
        val t0 = seg.toFloat / segs
        val t1 = (seg + 1).toFloat / segs
        val w0 = w * (1f - t0 * taperEnd)
        val w1 = w * (1f - t1 * taperEnd)
        val wAvg = (w0 + w1) * 0.5f
        val off0 = pathOff(t0); val off1 = pathOff(t1)
        val x0 = sx + dx * t0 + perpX * off0
        val y0 = sy + dy * t0 + perpY * off0
        val x1 = sx + dx * t1 + perpX * off1
        val y1 = sy + dy * t1 + perpY * off1

        // Outer glow halo
        sb.strokeLineSoft(x0, y0, x1, y1, wAvg * 3f * dynGlow, dr, dg, db, 0.25f * p)
        // Dark cartoon outline
        sb.strokeLine(x0, y0, x1, y1, wAvg * 1.15f, outline(r), outline(g), outline(b), 0.7f * p)
        // Main beam body
        sb.strokeLine(x0, y0, x1, y1, wAvg, dr, dg, db, (0.88f + t0 * 0.07f) * p)
        // Hot core (white-hot for LASER/RAILGUN, void-dark for GRAVITY/DRAIN)
        val cbc = _chgBright
        if (style == BEAM_GRAVITY || style == BEAM_DRAIN) {
          sb.strokeLine(x0, y0, x1, y1, wAvg * 0.32f,
            outline(r), outline(g), outline(b), 0.95f * p)
        } else {
          sb.strokeLine(x0, y0, x1, y1, wAvg * 0.35f,
            mix(bright(r), 1f, cbc), mix(bright(g), 1f, cbc), mix(bright(b), 1f, cbc), 0.98f * p)
        }
      ; seg += 1 }

      // ── STYLE-SPECIFIC OVERLAYS ──────────────────────────────────────

      style match {
        // ── LASER: scan lines + forward-traveling shock rings ──
        case BEAM_LASER =>
          // Internal filaments
          { var fil = 0; while (fil < 2) {
            val freq = 4.2 + fil * 1.8
            val filAmp = w * (0.35f + fil * 0.18f)
            var fs = 0; while (fs < 7) {
              val ft0 = fs.toFloat / 7
              val ft1 = (fs + 1).toFloat / 7
              val fOff0 = pathOff(ft0) + Math.sin(phase * freq + ft0 * 8.0 + fil * 2.1 + pId * 0.3).toFloat * filAmp
              val fOff1 = pathOff(ft1) + Math.sin(phase * freq + ft1 * 8.0 + fil * 2.1 + pId * 0.3).toFloat * filAmp
              sb.strokeLineSoft(sx + dx * ft0 + perpX * fOff0, sy + dy * ft0 + perpY * fOff0,
                                sx + dx * ft1 + perpX * fOff1, sy + dy * ft1 + perpY * fOff1,
                                1.4f, bright(r), bright(g), bright(b), 0.3f * (1f - ft0 * 0.5f) * p)
            ; fs += 1 }
          ; fil += 1 } }
          // 3 forward-traveling shock rings sliding tip-ward
          { var sh = 0; while (sh < 3) {
            val st = ((tick * 0.08 + sh * 0.33 + pId * 0.17) % 1.0).toFloat
            val sxR = sx + dx * st + perpX * pathOff(st)
            val syR = sy + dy * st + perpY * pathOff(st)
            val rR = w * (0.6f + st * 0.4f)
            sb.strokeOval(sxR, syR, rR * 1.4f, rR * 0.95f, 1.8f * (1f - st * 0.4f),
              bright(r), bright(g), bright(b), 0.55f * (1f - st * 0.4f) * p, 10)
          ; sh += 1 } }

        // ── DRAIN: back-flowing particles + bright bands rushing toward origin ──
        case BEAM_DRAIN =>
          // 12 small particles streaming back toward source
          { var i = 0; while (i < 7) {
            val tRev = ((1.0 - ((tick * 0.09 + i * 0.143 + pId * 0.13) % 1.0)) % 1.0).toFloat
            val ox = pathOff(tRev) + Math.sin(phase * 3.0 + i * 1.7).toFloat * w * 0.5f
            val px = sx + dx * tRev + perpX * ox
            val py = sy + dy * tRev + perpY * ox
            val pSz = (2.2f + (1f - tRev) * 1.8f)
            // Streak behind toward origin
            val streakAhead = 0.06f
            val nx = sx + dx * (tRev - streakAhead) + perpX * pathOff(tRev - streakAhead)
            val ny = sy + dy * (tRev - streakAhead) + perpY * pathOff(tRev - streakAhead)
            sb.strokeLine(nx, ny, px, py, 1.8f, bright(r), bright(g), bright(b), 0.6f * (1f - tRev * 0.4f) * p)
            sb.fillOval(px, py, pSz, pSz * 0.7f, bright(r), bright(g), bright(b), 0.75f * p, 8)
          ; i += 1 } }
          // Bright bands rushing along beam back to origin
          { var bd = 0; while (bd < 3) {
            val bt = ((1.0 - ((tick * 0.05 + bd * 0.33) % 1.0)) % 1.0).toFloat
            val bx = sx + dx * bt + perpX * pathOff(bt)
            val by = sy + dy * bt + perpY * pathOff(bt)
            val len = w * 2.2f
            val nx = sx + dx * (bt - 0.08f) + perpX * pathOff(bt - 0.08f)
            val ny = sy + dy * (bt - 0.08f) + perpY * pathOff(bt - 0.08f)
            sb.strokeLineSoft(nx, ny, bx, by, w * 1.3f * (1f - bt * 0.3f),
              bright(r), bright(g), bright(b), 0.55f * (1f - bt * 0.3f) * p)
          ; bd += 1 } }
          // Pulsing origin sink — sucking glow
          val sinkPulse = (0.7f + 0.3f * Math.sin(phase * 4).toFloat)
          sb.fillOvalSoft(sx, sy, w * 2.2f * sinkPulse * dynGlow, w * 1.6f * sinkPulse * dynGlow,
            bright(r), bright(g), bright(b), 0.45f * p, 0f, 14)
          sb.strokeOval(sx, sy, w * 1.4f * sinkPulse, w * 1.05f * sinkPulse, 2.5f,
            outline(r), outline(g), outline(b), 0.6f * p, 12)

        // ── WHIP: organic segmented bumps, alternating thickness ──
        case BEAM_WHIP =>
          // Pulsating bulges at 6 nodes along the curving path
          { var k = 0; while (k < 6) {
            val t = (k + 1).toFloat / 7f
            val bumpPhase = phase * 2 + k * 0.7
            val pulse = 0.6f + 0.4f * Math.sin(bumpPhase).toFloat
            val bx = sx + dx * t + perpX * pathOff(t)
            val by = sy + dy * t + perpY * pathOff(t)
            val bSize = w * (0.95f - t * 0.45f) * pulse
            sb.strokeOval(bx, by, bSize, bSize * 0.8f, 2.5f, outline(r), outline(g), outline(b), 0.7f * p, 10)
            sb.fillOval(bx, by, bSize, bSize * 0.8f, dr, dg, db, 0.92f * p, 10)
            sb.fillOval(bx - 1.5f, by - 1.5f, bSize * 0.4f, bSize * 0.3f, 1f, 1f, 1f, 0.45f * p, 6)
          ; k += 1 } }
          // 4 secondary strand twisting around main path
          { var s2 = 0; while (s2 < 8) {
            val t = s2.toFloat / 7
            val twist = Math.sin(phase * 3.5 + t * Math.PI * 4 + pId * 0.4).toFloat * w * 0.8f
            val ox = pathOff(t) + twist
            val xS = sx + dx * t + perpX * ox
            val yS = sy + dy * t + perpY * ox
            sb.fillOval(xS, yS, w * 0.28f, w * 0.22f, bright(r), bright(g), bright(b), 0.55f * p, 6)
          ; s2 += 1 } }

        // ── ICE: angular frost shards growing perpendicular to the beam ──
        case BEAM_ICE =>
          { var k = 0; while (k < 7) {
            val t = (k + 0.5f) / 7f
            val growth = ((tick * 0.05f + k * 0.137f + pId.toFloat * 0.07f) % 1f)
            val side = if (k % 2 == 0) 1f else -1f
            val len = w * (1.2f + growth * 0.9f) * (0.7f + 0.3f * Math.sin(phase + k * 1.7).toFloat)
            val cx = sx + dx * t + perpX * pathOff(t)
            val cy = sy + dy * t + perpY * pathOff(t)
            val tipShX = cx + perpX * side * len
            val tipShY = cy + perpY * side * len
            val midX = cx + perpX * side * (len * 0.55f) + (dx / Math.max(beamLen, 1f)) * w * 0.4f
            val midY = cy + perpY * side * (len * 0.55f) + (dy / Math.max(beamLen, 1f)) * w * 0.4f
            // Dark shard outline
            _polyXs3(0) = cx; _polyXs3(1) = midX; _polyXs3(2) = tipShX
            _polyYs3(0) = cy; _polyYs3(1) = midY; _polyYs3(2) = tipShY
            sb.fillPolygon(_polyXs3, _polyYs3, 3, outline(r), outline(g), outline(b), 0.6f * p)
            // Shard body — pale cool color
            val ix = cx + perpX * side * (w * 0.12f)
            val iy = cy + perpY * side * (w * 0.12f)
            _polyXs3(0) = ix; _polyXs3(1) = midX; _polyXs3(2) = tipShX
            _polyYs3(0) = iy; _polyYs3(1) = midY; _polyYs3(2) = tipShY
            sb.fillPolygon(_polyXs3, _polyYs3, 3, bright(r), bright(g), bright(b), 0.85f * p)
            // White-hot tip highlight
            sb.fillOval(tipShX, tipShY, 2.5f, 2f, 1f, 1f, 1f, 0.7f * p, 6)
          ; k += 1 } }
          // Frost mist particles drifting around
          { var i = 0; while (i < 8) {
            val ft = ((tick * 0.04 + i * 0.125 + pId * 0.11) % 1.0).toFloat
            val along = ft
            val drift = (i - 4f) * 0.3f
            val mx = sx + dx * along + perpX * (pathOff(along) + drift * w)
            val my = sy + dy * along + perpY * (pathOff(along) + drift * w) + ft * 6f
            val mSz = 2.5f + (1f - ft) * 2f
            sb.fillOval(mx, my, mSz, mSz * 0.7f, 0.9f, 0.95f, 1f, 0.4f * (1f - ft) * p, 6)
          ; i += 1 } }

        // ── VINE: thorny vine with sprouting thorns and leaves ──
        case BEAM_VINE =>
          { var k = 0; while (k < 6) {
            val t = (k + 0.5f) / 6f
            val side = if (k % 2 == 0) 1f else -1f
            val cx = sx + dx * t + perpX * pathOff(t)
            val cy = sy + dy * t + perpY * pathOff(t)
            val thornLen = w * (1.1f + 0.4f * Math.sin(phase + k * 1.3).toFloat)
            val ax = (dx / Math.max(beamLen, 1f)) * thornLen * 0.45f
            val ay = (dy / Math.max(beamLen, 1f)) * thornLen * 0.45f
            val thornX = cx + perpX * side * thornLen + ax
            val thornY = cy + perpY * side * thornLen + ay
            // Thorn triangle
            _polyXs3(0) = cx; _polyXs3(1) = cx + perpX * side * (w * 0.5f); _polyXs3(2) = thornX
            _polyYs3(0) = cy; _polyYs3(1) = cy + perpY * side * (w * 0.5f); _polyYs3(2) = thornY
            sb.fillPolygon(_polyXs3, _polyYs3, 3, outline(0.2f), outline(0.6f), outline(0.15f), 0.85f * p)
            _polyXs3(0) = cx; _polyXs3(1) = cx + perpX * side * (w * 0.4f) + ax * 0.3f
            _polyXs3(2) = cx + perpX * side * (thornLen * 0.85f) + ax * 0.9f
            _polyYs3(0) = cy; _polyYs3(1) = cy + perpY * side * (w * 0.4f) + ay * 0.3f
            _polyYs3(2) = cy + perpY * side * (thornLen * 0.85f) + ay * 0.9f
            sb.fillPolygon(_polyXs3, _polyYs3, 3, 0.3f, 0.7f, 0.2f, 0.9f * p)
            // Tiny leaf
            sb.fillOval(cx - perpX * side * (w * 0.4f), cy - perpY * side * (w * 0.4f),
              w * 0.35f, w * 0.22f, 0.35f, 0.78f, 0.25f, 0.7f * p, 8)
          ; k += 1 } }
          // Vine spore particles
          { var sp2 = 0; while (sp2 < 6) {
            val st = ((tick * 0.06 + sp2 * 0.167 + pId * 0.11) % 1.0).toFloat
            val ang = phase * 0.8 + sp2 * 1.5
            val px = sx + dx * st + perpX * pathOff(st) + Math.cos(ang).toFloat * w * 1.6f
            val py = sy + dy * st + perpY * pathOff(st) + Math.sin(ang).toFloat * w * 0.9f
            sb.fillOval(px, py, 2.5f, 2f, 0.6f, 1f, 0.4f, 0.55f * (1f - st * 0.3f) * p, 6)
          ; sp2 += 1 } }

        // ── STONE/PETRIFY: chunky rocky crystallization with dust ──
        case BEAM_STONE =>
          { var k = 0; while (k < 8) {
            val t = (k + 0.5f) / 8f
            val cx = sx + dx * t + perpX * pathOff(t)
            val cy = sy + dy * t + perpY * pathOff(t)
            val rot = phase * 0.3 + k * 1.7 + pId
            val sz = w * (0.85f - t * 0.3f) * (0.85f + 0.15f * Math.sin(phase + k * 1.1).toFloat)
            // Diamond chunk
            _polyXs4(0) = (cx + Math.cos(rot).toFloat * sz).toFloat
            _polyXs4(1) = (cx + Math.cos(rot + Math.PI * 0.5).toFloat * sz * 0.85f).toFloat
            _polyXs4(2) = (cx - Math.cos(rot).toFloat * sz).toFloat
            _polyXs4(3) = (cx - Math.cos(rot + Math.PI * 0.5).toFloat * sz * 0.85f).toFloat
            _polyYs4(0) = (cy + Math.sin(rot).toFloat * sz * 0.65f).toFloat
            _polyYs4(1) = (cy + Math.sin(rot + Math.PI * 0.5).toFloat * sz * 0.55f).toFloat
            _polyYs4(2) = (cy - Math.sin(rot).toFloat * sz * 0.65f).toFloat
            _polyYs4(3) = (cy - Math.sin(rot + Math.PI * 0.5).toFloat * sz * 0.55f).toFloat
            sb.strokePolygon(_polyXs4, _polyYs4, 4, 2f, outline(r), outline(g), outline(b), 0.85f * p)
            sb.fillPolygon(_polyXs4, _polyYs4, 4, dr, dg, db, 0.92f * p)
            // Cracked highlight stripe
            sb.strokeLine(_polyXs4(3), _polyYs4(3), _polyXs4(1), _polyYs4(1), 1.5f,
              bright(r), bright(g), bright(b), 0.5f * p)
          ; k += 1 } }
          // Falling dust beneath
          { var d = 0; while (d < 7) {
            val dt = ((tick * 0.04 + d * 0.143 + pId * 0.11) % 1.0).toFloat
            val along = (d.toFloat / 7f)
            val ux = sx + dx * along + perpX * pathOff(along)
            val uy = sy + dy * along + perpY * pathOff(along) + dt * 18f
            val dSz = 2.5f + (1f - dt) * 1.5f
            sb.fillOval(ux, uy, dSz, dSz * 0.7f, 0.6f, 0.55f, 0.4f, 0.45f * (1f - dt) * p, 6)
          ; d += 1 } }

        // ── RAILGUN: pencil-thin beam, shock cone at origin, periodic perpendicular rings ──
        case BEAM_RAILGUN =>
          // Muzzle blast. A hard-edged filled triangle behind the origin read as a stray
          // grey shape sitting in the field, so this is a soft flare plus two swept
          // streaks — the shape of escaping gas rather than a polygon.
          val ux = dx / Math.max(beamLen, 1f); val uy = dy / Math.max(beamLen, 1f)
          val flareP = 0.75f + 0.25f * Math.sin(phase * 5).toFloat
          sb.fillOvalSoft(sx - ux * w * 0.6f, sy - uy * w * 0.6f, w * 2.4f * flareP, w * 1.7f * flareP,
            bright(r), bright(g), bright(b), 0.5f * p, 0f, 12)
          var mc = -1
          while (mc <= 1) {
            if (mc != 0) {
              val ex = sx - ux * w * 4.2f + perpX * mc * w * 1.9f
              val ey = sy - uy * w * 4.2f + perpY * mc * w * 1.9f
              sb.strokeLineSoft(sx, sy, ex, ey, w * 0.7f, bright(r), bright(g), bright(b), 0.42f * p)
            }
            mc += 1
          }
          // 3 perpendicular shock rings traveling outward (tip-ward) along beam
          { var sh = 0; while (sh < 4) {
            val st = ((tick * 0.1 + sh * 0.25 + pId * 0.07) % 1.0).toFloat
            val cx = sx + dx * st
            val cy = sy + dy * st
            val rOuter = w * (0.4f + st * 5f)
            // Perpendicular oval shock — minor along beam axis, major perpendicular
            sb.strokeOval(cx, cy, rOuter * 0.25f, rOuter * 1.2f, 2.5f * (1f - st * 0.4f),
              bright(r), bright(g), bright(b), 0.65f * (1f - st) * p, 12)
          ; sh += 1 } }
          // A charge packet racing down the rail. Ten fat dots along the run turned the
          // beam into a bead necklace; one travelling slug reads as velocity.
          { var bp = 0; while (bp < 3) {
            val st = ((tick * 0.16 + bp * 0.34 + pId * 0.11) % 1.0).toFloat
            val cx = sx + dx * st; val cy = sy + dy * st
            val bx = sx + dx * Math.max(0f, st - 0.12f); val by = sy + dy * Math.max(0f, st - 0.12f)
            sb.strokeLineSoft(bx, by, cx, cy, w * 0.85f, 1f, 1f, 1f, 0.6f * p)
            sb.fillOval(cx, cy, w * 0.42f, w * 0.42f, 1f, 1f, 1f, 0.9f * p, 6)
          ; bp += 1 } }

        // ── GRAVITY: warped path, dark core, particles spiraling inward ──
        case BEAM_GRAVITY =>
          // Spacetime ripple ovals along beam (faint dark distortion)
          { var rp = 0; while (rp < 4) {
            val rt = ((tick * 0.04 + rp * 0.25 + pId * 0.09) % 1.0).toFloat
            val cx = sx + dx * rt + perpX * pathOff(rt)
            val cy = sy + dy * rt + perpY * pathOff(rt)
            val rR = w * (0.6f + rt * 2.2f)
            sb.strokeOval(cx, cy, rR, rR * 0.65f, 1.8f * (1f - rt * 0.5f),
              outline(r), outline(g), outline(b), 0.4f * (1f - rt) * p, 12)
          ; rp += 1 } }
          // Inward-spiraling particles (gravity well around tip)
          { var i = 0; while (i < 10) {
            val swirl = phase * 2.0 + i * Math.PI * 2 / 10
            val orbT = ((tick * 0.06 + i * 0.1 + pId * 0.11) % 1.0).toFloat
            val orbR = w * (2.4f - orbT * 1.8f)
            val cx = tipX + Math.cos(swirl).toFloat * orbR
            val cy = tipY + Math.sin(swirl).toFloat * orbR * 0.6f
            sb.fillOval(cx, cy, 3f, 2.3f, bright(r), bright(g), bright(b), 0.7f * (1f - orbT * 0.3f) * p, 6)
          ; i += 1 } }
          // 4 cosmic energy bands rotating around tip
          { var bd = 0; while (bd < 4) {
            val ba = phase * 1.5 + bd * Math.PI * 0.5
            val rIn = w * 0.6f; val rOut = w * 1.8f
            val x0 = tipX + Math.cos(ba).toFloat * rIn
            val y0 = tipY + Math.sin(ba).toFloat * rIn * 0.65f
            val x1 = tipX + Math.cos(ba).toFloat * rOut
            val y1 = tipY + Math.sin(ba).toFloat * rOut * 0.65f
            sb.strokeLineSoft(x0, y0, x1, y1, 2f, bright(r), bright(g), bright(b), 0.55f * p)
          ; bd += 1 } }

        case _ => ()
      }

      // Tip flare (style-modified)
      val tipPulse = (0.7 + 0.3 * Math.sin(phase * 3)).toFloat
      val tipScale = style match {
        case BEAM_RAILGUN => 0.7f   // small focused tip
        case BEAM_GRAVITY => 1.5f   // big collapse
        case BEAM_WHIP    => 1.3f   // splotchy sticky end
        case _            => 1f
      }
      sb.fillOvalSoft(tipX, tipY, w * 3f * tipPulse * tipScale * dynGlow, w * 2.3f * tipPulse * tipScale * dynGlow,
        dr, dg, db, 0.4f * p, 0f, 14)
      sb.strokeOval(tipX, tipY, w * 1.4f * tipPulse * tipScale, w * 1.05f * tipPulse * tipScale, 2.5f,
        outline(r), outline(g), outline(b), 0.6f * p, 10)
      sb.fillOval(tipX, tipY, w * 1.2f * tipScale, w * 0.9f * tipScale, dr, dg, db, 0.9f * p, 10)
      // Center fill — dark for GRAVITY/DRAIN, hot for others
      if (style == BEAM_GRAVITY || style == BEAM_DRAIN) {
        sb.fillOval(tipX, tipY, w * 0.55f * tipScale, w * 0.4f * tipScale,
          outline(r), outline(g), outline(b), 0.98f * p, 8)
      } else {
        sb.fillOval(tipX, tipY, w * 0.55f * tipScale, w * 0.4f * tipScale,
          mix(bright(r), 1f, _chgBright), mix(bright(g), 1f, _chgBright), mix(bright(b), 1f, _chgBright), 0.95f * p, 8)
      }

      // Sparkle at tip
      drawSparkleStar(tipX, tipY, w * 0.9f * tipPulse * tipScale, 1f, 1f, 1f, 0.5f * p, sb, phase * 1.5)

      // Expanding ring at tip (skip for RAILGUN — uses its own shock rings)
      if (style != BEAM_RAILGUN) {
        val ringPh = ((phase * 0.6) % 1.0).toFloat
        sb.strokeOval(tipX, tipY, w * (1f + ringPh * 2.5f), w * (0.7f + ringPh * 1.8f),
          2f * (1f - ringPh), bright(r), bright(g), bright(b), 0.4f * (1f - ringPh) * p, 10)
      }
    }

  /** Large spinning star/blade weapon — CARTOONISH with bold outline and motion blur */
  private def spinner(r: Float, g: Float, b: Float, size: Float = 22f, pts: Int = 4): Renderer =
    (proj, sx, sy, sb, tick) => {
      val spin = tick * 0.35 + proj.id * 2.1
      val phase = spin // alias for computeAllDynamics
      computeAllDynamics(proj, r, g, b, phase)
      val p = (0.75 + 0.25 * Math.sin(spin * 2 * _stPulseMult)).toFloat * dynAlpha
      val dr = _evoR; val dg = _evoG; val db = _evoB
      val sz = size * 1.3f * dynScale
      screenDir(proj)
      val ndx = _sdx; val ndy = _sdy
      val n = pts * 2

      // Speed lines behind
      drawSpeedLines(sx, sy, ndx, ndy, dr, dg, db, 0.3f * p, sb, 4, sz * 1.2f)

      // Impact ring — pulsing spin radius
      val ringPulse = (0.7 + 0.3 * Math.sin(spin * 3)).toFloat
      sb.strokeOval(sx, sy, sz * 1.25f * ringPulse * dynGlow, sz * 0.8f * ringPulse * dynGlow,
        2.5f, dr, dg, db, 0.2f * p, 14)

      // Motion blur trail — 5 ghosts + return ghosts
      var ghost = 1; while (ghost <= 5) {
        val taper = 1f - ghost * 0.15f
        val colorFade = 1f - ghost * 0.12f
        val ga = 0.25f * (1f - ghost * 0.17f) * p
        val gx = sx - ndx * ghost * 14f
        val gy = sy - ndy * ghost * 14f
        val gSpin = spin - ghost * 0.4
        var i = 0; while (i < n) {
          val angle = gSpin + i * Math.PI / pts
          val rad = if (i % 2 == 0) sz * 0.85f * taper else sz * 0.28f * taper
          _spinGhostXs(i) = (gx + Math.cos(angle) * rad).toFloat
          _spinGhostYs(i) = (gy + Math.sin(angle) * rad * 0.6f).toFloat
        ; i += 1 }
        sb.fillPolygon(_spinGhostXs, _spinGhostYs, n, r * colorFade, g * colorFade, b * colorFade, ga)
      ; ghost += 1 }

      // Main shape — bigger
      { var i = 0; while (i < n) {
        val angle = spin + i * Math.PI / pts
        val rad = if (i % 2 == 0) sz else sz * 0.28f
        _spinXs(i) = (sx + Math.cos(angle) * rad).toFloat
        _spinYs(i) = (sy + Math.sin(angle) * rad * 0.6f).toFloat
      ; i += 1 } }
      sb.fillPolygon(_spinXs, _spinYs, n, dr, dg, db, 0.95f * p)
      // Bold dark cartoon outline
      sb.strokePolygon(_spinXs, _spinYs, n, 3.5f, outline(r), outline(g), outline(b), 0.85f * p)

      // Charge crackle
      drawChargeCrackle(sx, sy, sz, r, g, b, p, sb, phase, proj.chargeLevel)

      // Bright inner edge
      { var i = 0; while (i < n) {
        val angle = spin + i * Math.PI / pts
        val rad = if (i % 2 == 0) sz * 0.92f else sz * 0.25f
        _spinGhostXs(i) = (sx + Math.cos(angle) * rad).toFloat
        _spinGhostYs(i) = (sy + Math.sin(angle) * rad * 0.6f).toFloat
      ; i += 1 } }
      sb.strokePolygon(_spinGhostXs, _spinGhostYs, n, 1.5f, bright(r), bright(g), bright(b), 0.65f * p)

      // Metallic specular highlight — offset cartoon shine
      { var i = 0; while (i < n) {
        val angle = spin + i * Math.PI / pts
        val rad = if (i % 2 == 0) sz * 0.5f else sz * 0.16f
        _spinGhostXs(i) = (sx - 3f + Math.cos(angle) * rad).toFloat
        _spinGhostYs(i) = (sy - 3f + Math.sin(angle) * rad * 0.6f).toFloat
      ; i += 1 } }
      sb.fillPolygon(_spinGhostXs, _spinGhostYs, n, 1f, 1f, 1f, 0.2f * p)

      // Spin swoosh arcs — curved motion lines around the spinning edge
      var sl = 0; while (sl < 4) {
        val slAngle = spin * 1.5 + sl * Math.PI / 2
        val slInner = sz * 0.5f
        val slOuter = sz * 1.3f
        val slx0 = sx + Math.cos(slAngle).toFloat * slInner
        val sly0 = sy + Math.sin(slAngle).toFloat * slInner * 0.6f
        val slx1 = sx + Math.cos(slAngle).toFloat * slOuter
        val sly1 = sy + Math.sin(slAngle).toFloat * slOuter * 0.6f
        sb.strokeLine(slx0, sly0, slx1, sly1, 1.5f, bright(r), bright(g), bright(b), 0.3f * p)
      ; sl += 1 }

      // Two-layer center hub — bolder
      sb.strokeOval(sx, sy, sz * 0.22f, sz * 0.15f, 3f, outline(r), outline(g), outline(b), 0.6f * p, 8)
      sb.fillOval(sx, sy, sz * 0.16f, sz * 0.11f, bright(r), bright(g), bright(b), 0.8f * p, 8)
    }

  /**
   * Thrown weapon tumbling end over end. Unlike `spinner` — which builds a polar star and
   * so renders every melee weapon as the same lens — this stamps the weapon's own
   * silhouette, then sells the rotation with a swept arc and silhouette ghosts rather
   * than by smearing the shape itself.
   */
  private def bladeSpinner(kind: Int, r: Float, g: Float, b: Float, size: Float = 22f,
                           spinRate: Double = 0.30): Renderer =
    (proj, sx, sy, sb, tick) => {
      val spin = tick * spinRate + proj.id * 2.1
      computeAllDynamics(proj, r, g, b, spin)
      val p = (0.85f + 0.15f * Math.sin(spin * 2 * _stPulseMult).toFloat) * dynAlpha
      val dr = _evoR; val dg = _evoG; val db = _evoB
      // Physical weapons ignore most of the charge inflation: a 2x axe reads as a bug.
      val ds = Math.min(dynScale, 1.3f)
      val s = size * 0.80f * ds
      val reach = s * 1.3f
      screenDir(proj)
      val ndx = _sdx; val ndy = _sdy
      val ca = Math.cos(spin).toFloat; val sa = Math.sin(spin).toFloat
      // A card flips about its short axis instead of tumbling in plane.
      val sclX = if (kind == WPN_CARD) Math.max(0.42f, Math.abs(Math.cos(spin * 1.1).toFloat)) else 1f

      drawSpeedLines(sx, sy, ndx, ndy, dr, dg, db, 0.22f * p, sb, 4, reach * 1.1f)

      // Ground shadow — anchors the weapon to the arena instead of floating over it
      sb.fillOval(sx + 3f, sy + reach * 0.42f, reach * 0.62f, reach * 0.20f, 0f, 0f, 0f, 0.22f * p, 12)

      // Swept arc behind the leading edge: the tumble read, drawn as geometry rather than
      // as ghosts of the whole shape, which is what used to turn the silhouette to mush.
      val sweepStart = spin.toFloat - 2.5f
      sb.fillArcBand(sx, sy, reach * 0.62f, reach * 0.62f * ISO_Y, reach * 1.04f, reach * 1.04f * ISO_Y,
        sweepStart, 2.5f, 12, bright(r), bright(g), bright(b), 0.02f * p, 0.42f * p)

      // Silhouette ghosts a few frames back along the tumble AND the flight path
      var ghost = 3; while (ghost >= 1) {
        val gA = 0.16f * (1f - (ghost - 1) * 0.28f) * p
        val gSpin = spin - ghost * 0.32
        val gx = sx - ndx * ghost * 7f; val gy = sy - ndy * ghost * 7f
        drawPartsFlat(sb, weaponParts(kind), gx, gy,
          Math.cos(gSpin).toFloat, Math.sin(gSpin).toFloat, s * (1f - ghost * 0.04f),
          dr * 0.8f, dg * 0.8f, db * 0.8f, gA, 1f, sclX)
        ghost -= 1
      }

      // Halo so the weapon separates from busy ground without washing it out
      sb.fillOvalSoft(sx, sy, reach * 1.5f * dynGlow, reach * 1.5f * ISO_Y * dynGlow,
        dr, dg, db, 0.20f * p, 0f, 14)

      drawParts(sb, weaponParts(kind), sx, sy, ca, sa, s, dr, dg, db, 0.97f * dynAlpha,
        clampF(s * 0.13f, 1.4f, 3f), 1f, sclX)
      weaponDetail(sb, kind, sx, sy, ca, sa, s, dr, dg, db, 0.97f * dynAlpha)

      // Edge glint: a star flare that fires as the cutting edge sweeps through the
      // light direction, which is what makes steel read as steel.
      val glint = Math.sin(spin * 2 + proj.id).toFloat
      if (glint > 0.72f) {
        blitPoint(1.05f, -0.30f, sx, sy, ca, sa, s)
        sb.fillStarFlare(_ptX, _ptY, s * 0.85f * (glint - 0.72f) / 0.28f, 2.2f,
          spin.toFloat * 0.5f, 0.45f, 1f, 1f, 0.96f, 0.75f * p)
      }

      drawChargeCrackle(sx, sy, reach, r, g, b, p, sb, spin, proj.chargeLevel)
      drawReturnGhosts(sx, sy, reach * 0.7f, dr, dg, db, p, sb, proj)
    }

  // ── Direction-aligned silhouettes (spears, arrows, darts, thorns) ──
  //
  // A flying shaft must NOT go through blitPart's extra ISO_Y squash: the travel vector
  // handed to the renderer is already in screen space, and squashing it again shortens a
  // spear thrown "north" to two thirds of one thrown "east". These stamp with a pure
  // screen rotation and narrow only the cross-axis, so the object still reads as lying
  // flat without changing length with heading.
  private val FLAT_Y = 0.80f

  @inline private def blitPartDir(p: Part, cx: Float, cy: Float, fx: Float, fy: Float,
                                  s: Float, flipY: Float): Unit = {
    val px = -fy * FLAT_Y; val py = fx * FLAT_Y
    var i = 0; while (i < p.n) {
      val lx = p.xs(i) * s; val ly = p.ys(i) * flipY * s
      _shpXs(i) = cx + lx * fx + ly * px
      _shpYs(i) = cy + lx * fy + ly * py
      i += 1
    }
  }

  private def drawPartsDir(sb: ShapeBatch, parts: Array[Part], cx: Float, cy: Float,
                           fx: Float, fy: Float, s: Float, tr: Float, tg: Float, tb: Float,
                           alpha: Float, outlineW: Float): Unit = {
    if (alpha <= 0.01f) return
    var i = 0; while (i < parts.length) {
      val pt = parts(i)
      blitPartDir(pt, cx, cy, fx, fy, s, 1f)
      if (outlineW > 0f) sb.strokePolygon(_shpXs, _shpYs, pt.n, outlineW, 0.06f, 0.05f, 0.07f, 0.85f * alpha)
      sb.fillPolygon(_shpXs, _shpYs, pt.n,
        mix(pt.r, tr, pt.tint), mix(pt.g, tg, pt.tint), mix(pt.b, tb, pt.tint), alpha)
      i += 1
    }
  }

  private def drawPartsDirFlat(sb: ShapeBatch, parts: Array[Part], cx: Float, cy: Float,
                               fx: Float, fy: Float, s: Float,
                               r: Float, g: Float, b: Float, alpha: Float): Unit = {
    if (alpha <= 0.01f) return
    var i = 0; while (i < parts.length) {
      val pt = parts(i)
      blitPartDir(pt, cx, cy, fx, fy, s, 1f)
      sb.fillPolygon(_shpXs, _shpYs, pt.n, r, g, b, alpha)
      i += 1
    }
  }

  @inline private def dirPoint(lx: Float, ly: Float, cx: Float, cy: Float,
                               fx: Float, fy: Float, s: Float): Unit = {
    _ptX = cx + lx * s * fx + ly * s * FLAT_Y * -fy
    _ptY = cy + lx * s * fy + ly * s * FLAT_Y * fx
  }

  private val SHF_SPEAR = 0
  private val SHF_ARROW = 1
  private val SHF_DART = 2
  private val SHF_THORN = 3
  private val SHF_PARROW = 4
  private val SHF_ICE = 5

  private val SPEAR_PARTS = Array(
    part(Array(-1.10f,-0.11f, 0.42f,-0.11f, 0.42f,0.11f, -1.10f,0.11f), WOOD_R, WOOD_G, WOOD_B, 0.15f),
    part(Array(-1.30f,-0.15f, -1.04f,-0.15f, -1.04f,0.15f, -1.30f,0.15f), GOLD_R, GOLD_G, GOLD_B, 0.35f),
    part(Array(0.16f,-0.09f, 0.27f,-0.09f, 0.27f,-0.46f, 0.16f,-0.46f), GOLD_R, GOLD_G, GOLD_B, 0.35f),
    part(Array(0.16f,0.09f, 0.27f,0.09f, 0.27f,0.46f, 0.16f,0.46f), GOLD_R, GOLD_G, GOLD_B, 0.35f),
    part(Array(0.30f,-0.17f, 0.52f,-0.17f, 0.52f,0.17f, 0.30f,0.17f), GOLD_R, GOLD_G, GOLD_B, 0.35f),
    part(Array(0.48f,-0.34f, 0.78f,-0.30f, 1.38f,0f, 0.78f,0.30f, 0.48f,0.34f), STEEL_R, STEEL_G, STEEL_B, 0.28f),
    part(Array(0.56f,-0.13f, 0.86f,-0.10f, 1.20f,0f, 0.86f,0.06f, 0.56f,0.09f), 1f, 1f, 1f, 0.10f)
  )
  private val ARROW_PARTS = Array(
    part(Array(-0.85f,-0.10f, 0.55f,-0.10f, 0.55f,0.10f, -0.85f,0.10f), WOOD_R, WOOD_G, WOOD_B, 0.18f),
    part(Array(-1.06f,-0.10f, -0.86f,-0.10f, -0.86f,0.10f, -1.06f,0.10f), DKWOOD_R, DKWOOD_G, DKWOOD_B, 0.10f),
    part(Array(-0.90f,-0.07f, -0.42f,-0.11f, -0.32f,-0.48f, -0.96f,-0.38f), 0.82f, 0.30f, 0.26f, 0.45f),
    part(Array(-0.90f,0.07f, -0.42f,0.11f, -0.32f,0.48f, -0.96f,0.38f), 0.92f, 0.92f, 0.90f, 0.35f),
    part(Array(0.50f,-0.30f, 0.72f,-0.24f, 1.28f,0f, 0.72f,0.24f, 0.50f,0.30f), STEEL_R, STEEL_G, STEEL_B, 0.30f)
  )
  private val DART_PARTS = Array(
    part(Array(-0.70f,-0.09f, 0.55f,-0.07f, 0.55f,0.07f, -0.70f,0.09f), 0.42f, 0.34f, 0.24f, 0.18f),
    part(Array(-0.74f,-0.06f, -0.46f,-0.09f, -0.38f,-0.44f, -0.84f,-0.36f), 0.92f, 0.72f, 0.24f, 0.40f),
    part(Array(-0.74f,0.06f, -0.46f,0.09f, -0.38f,0.44f, -0.84f,0.36f), 0.92f, 0.72f, 0.24f, 0.40f),
    part(Array(0.46f,-0.20f, 1.32f,0f, 0.46f,0.20f), 0.55f, 0.92f, 0.42f, 0.70f)
  )
  private val THORN_PARTS = Array(
    part(Array(-1.00f,-0.17f, 0.45f,-0.13f, 1.32f,0f, 0.45f,0.13f, -1.00f,0.17f), 0.36f, 0.28f, 0.14f, 0.25f),
    part(Array(-0.34f,-0.12f, -0.06f,-0.56f, 0.12f,-0.11f), 0.30f, 0.24f, 0.12f, 0.25f),
    part(Array(0.06f,0.11f, 0.34f,0.54f, 0.48f,0.10f), 0.30f, 0.24f, 0.12f, 0.25f),
    part(Array(-0.74f,0.13f, -0.52f,0.48f, -0.38f,0.12f), 0.30f, 0.24f, 0.12f, 0.25f),
    part(Array(-0.98f,-0.12f, -0.62f,-0.44f, -1.12f,-0.56f, -1.30f,-0.18f), 0.30f, 0.62f, 0.22f, 0.55f),
    part(Array(0.52f,-0.07f, 1.16f,0f, 0.52f,0.05f), 0.62f, 0.86f, 0.40f, 0.45f)
  )

  private val PARROW_PARTS = Array(
    part(Array(-0.85f,-0.10f, 0.55f,-0.10f, 0.55f,0.10f, -0.85f,0.10f), 0.40f, 0.34f, 0.20f, 0.15f),
    part(Array(-1.06f,-0.10f, -0.86f,-0.10f, -0.86f,0.10f, -1.06f,0.10f), DKWOOD_R, DKWOOD_G, DKWOOD_B, 0.10f),
    part(Array(-0.90f,-0.07f, -0.42f,-0.11f, -0.32f,-0.48f, -0.96f,-0.38f), 0.30f, 0.48f, 0.22f, 0.45f),
    part(Array(-0.90f,0.07f, -0.42f,0.11f, -0.32f,0.48f, -0.96f,0.38f), 0.52f, 0.62f, 0.34f, 0.35f),
    part(Array(0.50f,-0.30f, 0.72f,-0.24f, 1.28f,0f, 0.72f,0.24f, 0.50f,0.30f), 0.45f, 0.88f, 0.35f, 0.65f)
  )

  private val ICE_PARTS = Array(
    part(Array(-0.86f,-0.30f, 0.10f,-0.36f, 1.26f,0f, 0.10f,0.36f, -0.86f,0.28f), 0.30f, 0.60f, 0.88f, 0.45f),
    part(Array(-0.40f,-0.28f, -0.06f,-0.76f, 0.30f,-0.24f), 0.52f, 0.80f, 0.98f, 0.40f),
    part(Array(-0.52f,0.24f, -0.18f,0.70f, 0.18f,0.22f), 0.52f, 0.80f, 0.98f, 0.40f),
    part(Array(-0.62f,-0.16f, 0.10f,-0.18f, 1.00f,0f, 0.10f,0.06f, -0.62f,0.04f), 0.86f, 0.96f, 1f, 0.18f)
  )

  private def shaftParts(kind: Int): Array[Part] = kind match {
    case SHF_SPEAR => SPEAR_PARTS
    case SHF_ARROW => ARROW_PARTS
    case SHF_DART  => DART_PARTS
    case SHF_THORN => THORN_PARTS
    case SHF_PARROW => PARROW_PARTS
    case SHF_ICE   => ICE_PARTS
    case _         => ARROW_PARTS
  }

  /**
   * Shaft weapon flying point-first: spear, arrow, blowdart, thorn.
   *
   * These used to be drawn as a `strokeLine` from the projectile back over `worldLen`
   * world units — 6 units is 120 virtual px, so what reached the screen was a ~190px
   * hairline with a 13px head on the end. The silhouette below is a whole object about
   * two tiles long with a head that carries real area, which is what makes a spear read
   * as a spear rather than as a scratch on the display.
   */
  private def flyingShaft(kind: Int, r: Float, g: Float, b: Float, size: Float = 24f): Renderer =
    (proj, sx, sy, sb, tick) => {
      val phase = (tick + proj.id * 31) * 0.4
      computeAllDynamics(proj, r, g, b, phase)
      val p = (0.86f + 0.14f * Math.sin(phase * _stPulseMult).toFloat) * dynAlpha
      val dr = _evoR; val dg = _evoG; val db = _evoB
      // Physical shafts barely inflate with charge — they gain a hotter head instead.
      val ds = Math.min(dynScale, 1.25f)
      val s = size * ds
      screenDir(proj)
      val ndx = _sdx; val ndy = _sdy
      val parts = shaftParts(kind)

      drawSpeedLines(sx, sy, ndx, ndy, dr, dg, db, 0.28f * p, sb, 4 + (_lifePct * 2).toInt, s * 1.3f)
      drawRibbonTrail(sx, sy, ndx, ndy, dr, dg, db, 0.24f * p, sb, tick, proj.id, 7,
        s * 1.9f * dynTrail, s * 0.16f, 1f)

      // Ground shadow under the shaft
      sb.fillOval(sx + 3f, sy + s * 0.42f, s * 0.72f, s * 0.16f, 0f, 0f, 0f, 0.22f * p, 12)

      // Two afterimages down the flight path — motion without smearing the silhouette
      var ghost = 2; while (ghost >= 1) {
        drawPartsDirFlat(sb, parts, sx - ndx * ghost * 9f, sy - ndy * ghost * 9f, ndx, ndy,
          s * (1f - ghost * 0.05f), dr * 0.85f, dg * 0.85f, db * 0.85f, 0.14f * (1f - (ghost - 1) * 0.35f) * p)
        ghost -= 1
      }

      // Halo pinned to the head, not the middle, so the eye lands on the business end
      dirPoint(0.9f, 0f, sx, sy, ndx, ndy, s)
      val headX = _ptX; val headY = _ptY
      sb.fillOvalSoft(headX, headY, s * 0.85f * dynGlow, s * 0.68f * dynGlow, dr, dg, db, 0.24f * p, 0f, 14)

      drawPartsDir(sb, parts, sx, sy, ndx, ndy, s, dr, dg, db, 0.97f * dynAlpha,
        clampF(s * 0.11f, 1.3f, 2.8f))

      // Head glint. For the spear this also carries the distance-damage ramp: the point
      // brightens as the throw travels, which is exactly when it starts hitting harder.
      val heat = if (kind == SHF_SPEAR) _lifePct else 0f
      sb.fillStarFlare(headX, headY, s * (0.42f + heat * 0.5f), 2.2f,
        (phase * 0.6).toFloat, 0.5f, 1f, mix(1f, 0.85f, heat), mix(0.95f, 0.45f, heat),
        (0.55f + heat * 0.4f) * p)

      kind match {
        case SHF_DART | SHF_PARROW =>
          // Venom beading off the point and falling away
          var i = 0; while (i < 3) {
            val t = ((tick * 0.05 + i * 0.34 + proj.id * 0.19) % 1.0).toFloat
            dirPoint(1.15f - t * 0.5f, 0f, sx, sy, ndx, ndy, s)
            val dy2 = t * t * 14f
            sb.fillOval(_ptX, _ptY + dy2, 3.2f * (1f - t * 0.5f), 3.8f * (1f - t * 0.5f),
              0.45f, 0.9f, 0.35f, 0.65f * (1f - t) * p, 8)
            i += 1
          }
        case SHF_THORN =>
          // Leaf motes shaken loose behind the spike
          var i = 0; while (i < 4) {
            val t = ((tick * 0.035 + i * 0.25 + proj.id * 0.13) % 1.0).toFloat
            val drift = Math.sin(t * 6.0 + i).toFloat * 7f
            val lx = sx - ndx * t * s * 1.6f - ndy * drift
            val ly = sy - ndy * t * s * 1.6f + ndx * drift + t * t * 9f
            sb.fillOval(lx, ly, 3.6f * (1f - t * 0.5f), 2.4f * (1f - t * 0.5f),
              0.32f, 0.62f, 0.24f, 0.5f * (1f - t) * p, 6)
            i += 1
          }
        case SHF_ARROW =>
          drawSparkBurst(headX, headY, dr, dg, db, 0.34f * p, sb, tick, proj.id, 3, s * 0.4f)
        case SHF_ICE =>
          // Frost crystals shedding off the spike and settling behind it
          var i = 0; while (i < 5) {
            val t = ((tick * 0.04 + i * 0.2 + proj.id * 0.17) % 1.0).toFloat
            val drift = Math.sin(t * 5.0 + i * 2.1).toFloat * 8f
            val fx = sx - ndx * t * s * 1.7f - ndy * drift
            val fy = sy - ndy * t * s * 1.7f + ndx * drift
            drawSparkleStar(fx, fy, 5.5f * (1f - t * 0.6f), 0.85f, 0.96f, 1f, 0.6f * (1f - t) * p, sb, t * 6.0 + i)
            i += 1
          }
        case _ =>
          // Spear: a light streak riding the shaft toward the point
          val t = ((tick * 0.09 + proj.id * 0.21) % 1.0).toFloat
          dirPoint(-1f + t * 2.2f, 0f, sx, sy, ndx, ndy, s)
          sb.fillOvalSoft(_ptX, _ptY, s * 0.22f, s * 0.14f, 1f, 0.95f, 0.7f, 0.5f * (1f - t) * p, 0f, 8)
      }

      drawChargeCrackle(headX, headY, s * 0.5f, r, g, b, p, sb, phase, proj.chargeLevel)
      drawReturnGhosts(sx, sy, s * 0.5f, dr, dg, db, p, sb, proj)
    }


  // ── Thrown-object kinds for `lobbed` ──
  // A grenade, a gravedigger's shovel, a blacksmith's hammer and a minotaur's horn are
  // all "an object on an arc", and the old factory drew all four as the same grey disc
  // with a shadow. The kind selects a body so the arc still says whose ability it is.
  private val LOB_BOMB = 0
  private val LOB_FLASK = 1
  private val LOB_SHOVEL = 2
  private val LOB_HAMMER = 3
  private val LOB_HORN = 4
  private val LOB_MINE = 5
  private val LOB_ICE = 6
  private val LOB_GLOB = 7

  private val SHOVEL_PARTS = Array(
    part(Array(-1.15f,-0.10f, 0.22f,-0.10f, 0.22f,0.10f, -1.15f,0.10f), WOOD_R, WOOD_G, WOOD_B, 0.12f),
    part(Array(-1.30f,-0.34f, -1.10f,-0.34f, -1.10f,0.34f, -1.30f,0.34f), DKWOOD_R, DKWOOD_G, DKWOOD_B, 0.12f),
    part(Array(0.18f,-0.44f, 0.72f,-0.42f, 1.16f,0f, 0.72f,0.42f, 0.18f,0.44f), DKSTEEL_R, DKSTEEL_G, DKSTEEL_B, 0.30f),
    part(Array(0.30f,-0.26f, 0.70f,-0.24f, 0.96f,0f, 0.70f,0.10f, 0.30f,0.06f), STEEL_R, STEEL_G, STEEL_B, 0.18f)
  )
  private val HAMMER_PARTS = Array(
    part(Array(-1.20f,-0.11f, 0.42f,-0.11f, 0.42f,0.11f, -1.20f,0.11f), WOOD_R, WOOD_G, WOOD_B, 0.12f),
    part(Array(0.38f,-0.56f, 1.18f,-0.50f, 1.18f,0.50f, 0.38f,0.56f), DKSTEEL_R, DKSTEEL_G, DKSTEEL_B, 0.35f),
    part(Array(0.46f,-0.42f, 1.06f,-0.38f, 1.06f,-0.06f, 0.46f,-0.10f), STEEL_R, STEEL_G, STEEL_B, 0.20f),
    part(Array(-1.32f,-0.16f, -1.14f,-0.16f, -1.14f,0.16f, -1.32f,0.16f), DKSTEEL_R, DKSTEEL_G, DKSTEEL_B, 0.30f)
  )
  private val HORN_PARTS = Array(
    part(Array(-1.05f,-0.30f, -0.30f,-0.34f, -0.30f,0.28f, -1.05f,0.32f), 0.90f, 0.86f, 0.76f, 0.20f),
    part(Array(-0.32f,-0.36f, 0.42f,-0.50f, 0.42f,0.06f, -0.32f,0.26f), 0.86f, 0.81f, 0.70f, 0.20f),
    part(Array(0.40f,-0.52f, 1.12f,-0.86f, 1.02f,-0.48f, 0.40f,0.04f), 0.78f, 0.72f, 0.62f, 0.20f),
    part(Array(-1.10f,-0.24f, -0.86f,-0.26f, -0.86f,0.26f, -1.10f,0.24f), 0.46f, 0.34f, 0.28f, 0.25f)
  )

  /** The object itself, drawn at the top of the arc. `spin` tumbles the rigid kinds; the
   *  round kinds stay upright and animate their own detail instead. */
  private def drawLobBody(sb: ShapeBatch, kind: Int, cx: Float, cy: Float, sz: Float,
                          spin: Float, phase: Double, sx2: Float, sy2: Float,
                          dr: Float, dg: Float, db: Float, r: Float, g: Float, b: Float,
                          a: Float): Unit = {
    val ca = Math.cos(spin).toFloat; val sa = Math.sin(spin).toFloat
    val ow = clampF(sz * 0.12f, 1.4f, 3f)
    kind match {
      case LOB_SHOVEL => drawParts(sb, SHOVEL_PARTS, cx, cy, ca, sa, sz * 0.95f, dr, dg, db, a, ow)
      case LOB_HAMMER => drawParts(sb, HAMMER_PARTS, cx, cy, ca, sa, sz * 0.90f, dr, dg, db, a, ow)
      case LOB_HORN   => drawParts(sb, HORN_PARTS, cx, cy, ca, sa, sz * 0.95f, dr, dg, db, a, ow)

      case LOB_BOMB =>
        // Cast-iron sphere with a banded seam, a collar and a burning fuse
        sb.strokeOval(cx, cy, sz * 0.98f, sz * 0.86f, ow + 1f, 0.05f, 0.05f, 0.07f, 0.9f * a, 18)
        sb.fillOval(cx, cy, sz * 0.95f, sz * 0.83f, 0.17f, 0.17f, 0.20f, a, 18)
        sb.fillOval(cx - sz * 0.26f, cy - sz * 0.26f, sz * 0.30f, sz * 0.24f, 0.62f, 0.64f, 0.70f, 0.55f * a, 10)
        sb.strokeArc(cx, cy, sz * 0.72f, sz * 0.62f, 0.6f, 2.2f, 2f, 0.34f, 0.35f, 0.40f, 0.8f * a, 8)
        sb.fillRoundedRect(cx - sz * 0.20f, cy - sz * 1.00f, sz * 0.40f, sz * 0.28f, 2f,
          mix(0.30f, dr, 0.4f), mix(0.30f, dg, 0.4f), mix(0.34f, db, 0.4f), a)
        // Fuse: a curl with a spark that eats along it
        val fx = cx + sz * 0.12f; val fy = cy - sz * 1.06f
        val curl = Math.sin(phase * 2.2).toFloat * sz * 0.16f
        sb.strokeLine(fx, fy, fx + sz * 0.22f + curl, fy - sz * 0.40f, 2.4f, 0.42f, 0.36f, 0.26f, 0.9f * a)
        val spark = fx + sz * 0.24f + curl
        val sparkY = fy - sz * 0.44f
        sb.fillOvalSoft(spark, sparkY, sz * 0.32f, sz * 0.32f, 1f, 0.72f, 0.2f, 0.7f * a, 0f, 10)
        sb.fillStarFlare(spark, sparkY, sz * 0.30f, 2f, phase.toFloat * 3f, 0.6f, 1f, 0.92f, 0.6f, 0.9f * a)

      case LOB_FLASK =>
        // Corked glass flask with sloshing contents
        sb.strokeOval(cx, cy + sz * 0.10f, sz * 0.82f, sz * 0.72f, ow, 0.06f, 0.08f, 0.06f, 0.85f * a, 16)
        sb.fillOval(cx, cy + sz * 0.10f, sz * 0.80f, sz * 0.70f, 0.62f, 0.72f, 0.66f, 0.35f * a, 16)
        val slosh = Math.sin(phase * 2.6).toFloat * sz * 0.09f
        sb.fillOval(cx + slosh * 0.5f, cy + sz * 0.26f, sz * 0.68f, sz * 0.42f, dr, dg, db, 0.92f * a, 14)
        sb.fillOval(cx + slosh, cy + sz * 0.10f, sz * 0.62f, sz * 0.14f,
          bright(r), bright(g), bright(b), 0.75f * a, 12)
        sb.fillRect(cx - sz * 0.17f, cy - sz * 0.64f, sz * 0.34f, sz * 0.56f, 0.66f, 0.75f, 0.68f, 0.4f * a)
        sb.fillRoundedRect(cx - sz * 0.21f, cy - sz * 0.88f, sz * 0.42f, sz * 0.26f, 2f,
          0.52f, 0.36f, 0.20f, 0.95f * a)
        sb.fillOval(cx - sz * 0.26f, cy - sz * 0.06f, sz * 0.14f, sz * 0.26f, 1f, 1f, 1f, 0.35f * a, 8)
        // Bubbles rising through the liquid
        var i = 0; while (i < 3) {
          val t = ((phase * 0.35 + i * 0.34) % 1.0).toFloat
          sb.fillOval(cx + (i - 1) * sz * 0.18f, cy + sz * 0.36f - t * sz * 0.34f,
            sz * 0.07f, sz * 0.07f, bright(r), bright(g), bright(b), 0.6f * (1f - t) * a, 6)
          i += 1
        }

      case LOB_MINE =>
        // Spiked contact mine with a blinking arming light
        var i = 0; while (i < 8) {
          val ang = spin * 0.5f + i * Math.PI.toFloat / 4f
          val c2 = Math.cos(ang).toFloat; val s2 = Math.sin(ang).toFloat * ISO_Y
          _polyXs3(0) = cx + c2 * sz * 0.62f - s2 * sz * 0.16f
          _polyYs3(0) = cy + s2 * sz * 0.62f + c2 * sz * 0.16f
          _polyXs3(1) = cx + c2 * sz * 0.62f + s2 * sz * 0.16f
          _polyYs3(1) = cy + s2 * sz * 0.62f - c2 * sz * 0.16f
          _polyXs3(2) = cx + c2 * sz * 1.06f; _polyYs3(2) = cy + s2 * sz * 1.06f
          sb.fillPolygon(_polyXs3, _polyYs3, 3, 0.56f, 0.59f, 0.66f, 0.95f * a)
          i += 1
        }
        sb.strokeOval(cx, cy, sz * 0.72f, sz * 0.62f, ow + 1f, 0.05f, 0.05f, 0.07f, 0.9f * a, 16)
        sb.fillOval(cx, cy, sz * 0.70f, sz * 0.60f, 0.30f, 0.33f, 0.39f, a, 16)
        sb.strokeOval(cx, cy, sz * 0.56f, sz * 0.47f, 2f, 0.62f, 0.66f, 0.74f, 0.55f * a, 14)
        sb.fillOval(cx - sz * 0.20f, cy - sz * 0.20f, sz * 0.24f, sz * 0.18f, 0.74f, 0.78f, 0.84f, 0.55f * a, 8)
        val blink = (0.35f + 0.65f * Math.abs(Math.sin(phase * 3.2).toFloat))
        sb.fillOvalSoft(cx, cy, sz * 0.46f, sz * 0.40f, dr, dg, db, 0.55f * blink * a, 0f, 12)
        sb.fillOval(cx, cy, sz * 0.18f, sz * 0.15f, bright(r), bright(g), bright(b), blink * a, 8)

      case LOB_ICE =>
        // Faceted ice/rock chunk — an irregular hull, never a circle
        var i = 0; while (i < 7) {
          val ang = i * (Math.PI * 2 / 7) + spin * 0.35
          val rad = sz * (0.72f + 0.30f * Math.sin(i * 2.7 + proj7(i)).toFloat)
          _shpXs(i) = cx + Math.cos(ang).toFloat * rad
          _shpYs(i) = cy + Math.sin(ang).toFloat * rad * 0.82f
          i += 1
        }
        sb.strokePolygon(_shpXs, _shpYs, 7, ow + 1.5f, 0.05f, 0.09f, 0.16f, 0.92f * a)
        sb.fillPolygon(_shpXs, _shpYs, 7, dr * 0.72f, dg * 0.82f, db * 0.95f, a)
        // Facets: dark seams from the corners, then one lit face. Without the dark seams
        // a white block on pale ground is a flat cut-out.
        i = 0; while (i < 7) {
          sb.strokeLine(_shpXs(i), _shpYs(i), cx + (_shpXs(i) - cx) * 0.15f, cy + (_shpYs(i) - cy) * 0.15f,
            1.8f, 0.16f, 0.28f, 0.44f, 0.5f * a)
          i += 1
        }
        _polyXs3(0) = cx; _polyYs3(0) = cy
        _polyXs3(1) = _shpXs(5); _polyYs3(1) = _shpYs(5)
        _polyXs3(2) = _shpXs(6); _polyYs3(2) = _shpYs(6)
        sb.fillPolygon(_polyXs3, _polyYs3, 3, 1f, 1f, 1f, 0.34f * a)
        sb.fillOval(cx - sz * 0.22f, cy - sz * 0.26f, sz * 0.26f, sz * 0.18f, 1f, 1f, 1f, 0.55f * a, 8)

      case _ =>
        // LOB_GLOB — a wobbling sack of mud/ink with drips peeling off the bottom
        val wob = Math.sin(phase * 3.1).toFloat
        sb.strokeOval(cx, cy, sz * (0.92f + wob * 0.10f), sz * (0.80f - wob * 0.10f), ow + 1f,
          0.05f, 0.05f, 0.06f, 0.85f * a, 16)
        sb.fillOval(cx, cy, sz * (0.90f + wob * 0.10f), sz * (0.78f - wob * 0.10f), dr, dg, db, a, 16)
        sb.fillOval(cx - sz * 0.24f, cy - sz * 0.22f, sz * 0.24f, sz * 0.16f,
          bright(r), bright(g), bright(b), 0.4f * a, 8)
        var i = 0; while (i < 3) {
          val t = ((phase * 0.4 + i * 0.34) % 1.0).toFloat
          val dxo = (i - 1) * sz * 0.34f
          sb.fillOval(cx + dxo, cy + sz * 0.55f + t * sz * 0.7f, sz * 0.16f * (1f - t * 0.4f),
            sz * 0.22f * (1f - t * 0.4f), dark(r), dark(g), dark(b), 0.7f * (1f - t) * a, 8)
          i += 1
        }
    }
  }

  /** Deterministic per-vertex jitter for the ice chunk's hull. */
  @inline private def proj7(i: Int): Float = (i * 1.7f) % 3.1f

  /**
   * Object travelling on a lobbed arc: bomb, flask, shovel, hammer, horn, mine, ice
   * chunk, mud glob. The arc machinery (bounce, squash, landing shadow and target ring,
   * afterimages) is shared; `kind` picks the body, so a gravedigger's shovel and a
   * bombardier's grenade no longer arrive as the same grey disc.
   */
  private def lobbed(kind: Int, r: Float, g: Float, b: Float, size: Float = 18f): Renderer =
    (proj, sx, sy, sb, tick) => {
      val phase = (tick + proj.id * 13) * 0.3
      computeAllDynamics(proj, r, g, b, phase)
      val bounceRaw = Math.sin(phase * 1.5).toFloat
      val bounce = Math.abs(bounceRaw) * 16f
      val bounceContact = Math.abs(bounceRaw)
      val spin = tick * 0.16f + proj.id * 1.3f
      val p = (0.75f + 0.25f * Math.sin(phase * _stPulseMult).toFloat) * dynAlpha
      val dr = _evoR; val dg = _evoG; val db = _evoB
      val sz = size * 1.15f * Math.min(dynScale, 1.4f)
      val bodyY = sy - bounce

      screenDir(proj)
      val ndx = _sdx; val ndy = _sdy

      drawSpeedLines(sx, sy, ndx, ndy, dr, dg, db, 0.2f * p, sb, 4, sz * 1.4f)
      drawRibbonTrail(sx, sy, ndx, ndy, dr, dg, db, 0.28f * p, sb, tick, proj.id, 7,
        sz * 2.2f * dynTrail, sz * 0.36f, 1f)

      // Landing shadow that tightens as the object comes down, plus a target ring — the
      // arc's whole read is "this lands over there", so the ground marker carries it.
      val shadowScale = 1f + bounce * 0.04f
      val shadowAlpha = Math.max(0.12f, 0.48f - bounce * 0.015f)
      sb.fillOval(sx, sy + sz * 0.42f, sz * 1.2f * shadowScale, sz * 0.32f * shadowScale,
        0f, 0f, 0f, shadowAlpha, 16)
      val targetPulse = 0.5f + 0.5f * Math.sin(phase * 3).toFloat
      val targetAlpha = Math.max(0f, 0.35f - bounce * 0.015f) * targetPulse * p
      if (targetAlpha > 0.01f) {
        sb.strokeOval(sx, sy + sz * 0.42f, sz * 1.0f * shadowScale, sz * 0.27f * shadowScale,
          2f, dark(r), dark(g), dark(b), targetAlpha, 12)
        sb.strokeOval(sx, sy + sz * 0.42f, sz * 0.6f * shadowScale, sz * 0.16f * shadowScale,
          1.2f, r, g, b, targetAlpha * 0.6f, 10)
      }

      if (bounceContact < 0.15f) {
        val impactT = 1f - bounceContact / 0.15f
        drawSparkBurst(sx, sy + sz * 0.38f, dr, dg, db, impactT * 0.6f * p, sb, tick, proj.id, 6, sz * 1.1f)
      }

      // Afterimages back along the arc
      var ghost = 3; while (ghost >= 1) {
        val gt = ghost * 0.3f
        val gBounce = Math.abs(Math.sin(phase * 1.5 - gt * Math.PI)).toFloat * 14f
        val gx = sx - ndx * gt * 42f; val gy = sy - ndy * gt * 42f - gBounce
        sb.fillOval(gx, gy, sz * (0.55f - ghost * 0.08f), sz * (0.45f - ghost * 0.07f),
          dr, dg, db, 0.16f * (1f - (ghost - 1) * 0.3f) * p, 10)
        ghost -= 1
      }

      // Halo — kept modest so the body's own silhouette stays the thing you read
      sb.fillOvalSoft(sx, bodyY, sz * 1.7f * dynGlow, sz * 1.45f * dynGlow, dr, dg, db, 0.24f * p, 0f, 18)

      drawLobBody(sb, kind, sx, bodyY, sz, spin, phase, sx, sy, dr, dg, db, r, g, b, 0.97f * dynAlpha)

      // A couple of sparkles so the object still catches the eye in a busy fight
      var i = 0; while (i < 3) {
        val starPhase = ((phase * 0.45 + i * 0.34) % 1.0).toFloat
        val starAngle = phase * 1.2 + i * Math.PI * 2 / 3
        val starDist = sz * (0.85f + starPhase * 0.5f)
        drawSparkleStar(sx + Math.cos(starAngle).toFloat * starDist,
          bodyY + Math.sin(starAngle).toFloat * starDist * 0.55f,
          4.5f * (1f - starPhase * 0.5f), bright(r), bright(g), bright(b),
          0.4f * (1f - starPhase) * p, sb, phase * 2 + i)
        i += 1
      }

      drawChargeCrackle(sx, bodyY, sz, r, g, b, p, sb, phase, proj.chargeLevel)
      drawReturnGhosts(sx, bodyY, sz, dr, dg, db, p, sb, proj)
    }

  // ── Blast flavours for `aoeRing` ──
  private val AOE_QUAKE = 0
  private val AOE_WATER = 1
  private val AOE_TOXIC = 2
  private val AOE_FIRE = 3
  private val AOE_SONIC = 4
  private val AOE_VOID = 5
  private val AOE_NATURE = 6

  /**
   * Expanding ground blast.
   *
   * The rings used to be hairline strokes at low alpha, which on grass came out as a
   * barely-visible ripple — the same ripple for a tidal splash, a plague cloud, an
   * eruption and a gravity vortex. They are now filled bands over a darkened ground
   * scorch, and `kind` chooses what the blast throws off: rubble, water, spores, flame,
   * pressure rings, inward-falling motes, or roots.
   */
  private def aoeRing(kind: Int, r: Float, g: Float, b: Float, maxR: Float = 50f): Renderer =
    (proj, sx, sy, sb, tick) => {
      val phase = (tick + proj.id * 23) * 0.4
      computeAllDynamics(proj, r, g, b, phase)
      val p = (0.78f + 0.22f * Math.sin(phase * 2 * _stPulseMult).toFloat) * dynAlpha
      val dr = _evoR; val dg = _evoG; val db = _evoB
      val mR = maxR * 1.3f * dynScale
      val flat = 0.45f // ground ellipse ratio for this projection

      // Ground scorch: without something darker than the terrain underneath, a bright
      // ring on grass reads as a smudge. This is what gives the blast a floor.
      sb.fillOvalSoft(sx, sy, mR * 0.86f, mR * 0.86f * flat, dark(r) * 0.42f, dark(g) * 0.42f, dark(b) * 0.45f,
        (if (kind == AOE_QUAKE) 0.62f else 0.44f) * p, 0.02f, 20)
      val groundPulse = 0.75f + 0.25f * Math.sin(phase * 1.8).toFloat
      sb.fillOvalSoft(sx, sy, mR * 0.66f * dynGlow * groundPulse, mR * 0.66f * flat * dynGlow * groundPulse,
        dr, dg, db, 0.34f * p, 0f, 20)

      // Three filled shockwave bands, widest and brightest at the leading edge
      var ring = 0; while (ring < 3) {
        val rp = ((phase * 0.20 + ring * 0.333) % 1.0).toFloat
        val rr = 10f + rp * mR
        val a = Math.max(0f, 0.62f * (1f - rp * rp) * p)
        val bandIn = rr * (1f - 0.20f - 0.10f * rp)
        sb.fillArcBand(sx, sy, bandIn, bandIn * flat, rr, rr * flat, 0f, 6.2832f, 22,
          mix(r, bright(r), rp * 0.5f), mix(g, bright(g), rp * 0.5f), mix(b, bright(b), rp * 0.5f), a, a)
        sb.strokeOval(sx, sy, rr + 1.5f, (rr + 1.5f) * flat, 3.5f, 0.05f, 0.05f, 0.07f, a * 0.7f, 22)
        sb.strokeOval(sx, sy, rr, rr * flat, 2.2f, mix(bright(r), 1f, 0.4f), mix(bright(g), 1f, 0.4f),
          mix(bright(b), 1f, 0.4f), a * 1.1f, 22)
        ring += 1
      }

      kind match {
        case AOE_QUAKE =>
          // Forked ground cracks plus rubble thrown clear of the rim
          var c = 0; while (c < 9) {
            val ang = c * Math.PI * 2 / 9 + proj.id * 0.5
            val cl = mR * (0.36f + 0.16f * Math.sin(phase * 0.3 + c * 2.1).toFloat)
            val ex = sx + Math.cos(ang).toFloat * cl; val ey = sy + Math.sin(ang).toFloat * cl * flat
            sb.strokeLine(sx, sy, ex, ey, 3.2f, 0.06f, 0.05f, 0.05f, 0.55f * p)
            val fa = ang + 0.45
            sb.strokeLine(ex, ey, ex + Math.cos(fa).toFloat * cl * 0.35f,
              ey + Math.sin(fa).toFloat * cl * 0.35f * flat, 2f, 0.06f, 0.05f, 0.05f, 0.4f * p)
            c += 1
          }
          var d = 0; while (d < 9) {
            val t = ((phase * 0.28 + d * 0.111) % 1.0).toFloat
            val ang = phase * 0.4 + d * Math.PI * 2 / 9
            val dist = 12f + t * mR
            val dx2 = sx + Math.cos(ang).toFloat * dist
            val dy2 = sy + Math.sin(ang).toFloat * dist * flat - t * t * 26f
            val sz2 = 4f + (1f - t) * 5f
            _polyXs3(0) = dx2 - sz2; _polyYs3(0) = dy2 + sz2 * 0.5f
            _polyXs3(1) = dx2 + sz2 * 0.3f; _polyYs3(1) = dy2 - sz2 * 0.8f
            _polyXs3(2) = dx2 + sz2; _polyYs3(2) = dy2 + sz2 * 0.4f
            sb.fillPolygon(_polyXs3, _polyYs3, 3, dark(r), dark(g), dark(b), 0.75f * (1f - t) * p)
            d += 1
          }
        case AOE_WATER =>
          // Droplets thrown up and falling back, over a rippled surface
          var i = 1; while (i <= 4) {
            val rr = mR * (0.18f * i) + ((phase * 4) % 10).toFloat
            sb.strokeOval(sx, sy, rr, rr * flat, 1.4f, bright(r), bright(g), bright(b), 0.30f * p, 18)
            i += 1
          }
          var d = 0; while (d < 12) {
            val t = ((phase * 0.36 + d * 0.083) % 1.0).toFloat
            val ang = d * Math.PI * 2 / 12 + proj.id * 0.3
            val dist = 10f + t * mR * 0.85f
            val rise = Math.sin(t * Math.PI).toFloat * 30f
            sb.fillOval(sx + Math.cos(ang).toFloat * dist, sy + Math.sin(ang).toFloat * dist * flat - rise,
              3.4f * (1f - t * 0.4f), 4.6f * (1f - t * 0.4f), bright(r), bright(g), bright(b), 0.8f * (1f - t) * p, 7)
            d += 1
          }
        case AOE_TOXIC =>
          // Spore puffs boiling upward out of the cloud
          var d = 0; while (d < 10) {
            val t = ((phase * 0.22 + d * 0.1) % 1.0).toFloat
            val ang = d * 2.39f + proj.id * 0.4f
            val dist = mR * (0.2f + 0.55f * t)
            val bx = sx + Math.cos(ang).toFloat * dist
            val by = sy + Math.sin(ang).toFloat * dist * flat - t * 26f
            val bs = 7f + t * 12f
            sb.fillOvalSoft(bx, by, bs, bs * 0.8f, dr, dg, db, 0.38f * (1f - t) * p, 0f, 10)
            sb.strokeOval(bx, by, bs * 0.6f, bs * 0.48f, 1.4f, bright(r), bright(g), bright(b), 0.28f * (1f - t) * p, 8)
            d += 1
          }
        case AOE_FIRE =>
          // Flame tongues standing up around the rim, embers drifting off
          var f = 0; while (f < 10) {
            val ang = f * Math.PI * 2 / 10 + phase * 0.2
            val lick = 0.5f + 0.5f * Math.sin(phase * 3.6 + f * 1.7).toFloat
            val rr = mR * 0.52f
            val bx = sx + Math.cos(ang).toFloat * rr; val by = sy + Math.sin(ang).toFloat * rr * flat
            val fh = (14f + lick * 26f) * (0.65f + 0.55f * ((f * 5) % 7) / 6f)
            val lean = Math.sin(phase * 2.1 + f).toFloat * 7f
            _polyXs3(0) = bx - 7f; _polyYs3(0) = by + 3f
            _polyXs3(1) = bx + lean; _polyYs3(1) = by - fh
            _polyXs3(2) = bx + 7f; _polyYs3(2) = by + 3f
            sb.fillPolygon(_polyXs3, _polyYs3, 3, 1f, 0.42f + lick * 0.3f, 0.08f, 0.75f * p)
            _polyXs3(0) = bx - 3.4f; _polyYs3(0) = by + 2f
            _polyXs3(1) = bx + lean * 0.6f; _polyYs3(1) = by - fh * 0.62f
            _polyXs3(2) = bx + 3.4f; _polyYs3(2) = by + 2f
            sb.fillPolygon(_polyXs3, _polyYs3, 3, 1f, 0.92f, 0.55f, 0.8f * p)
            f += 1
          }
          var e = 0; while (e < 8) {
            val t = ((phase * 0.3 + e * 0.125) % 1.0).toFloat
            val ang = e * 0.9f + proj.id * 0.3f
            sb.fillOval(sx + Math.cos(ang).toFloat * mR * 0.4f * (0.5f + t),
              sy + Math.sin(ang).toFloat * mR * 0.4f * flat - t * 42f,
              3.5f * (1f - t), 3.5f * (1f - t), 1f, 0.72f, 0.28f, 0.8f * (1f - t) * p, 6)
            e += 1
          }
        case AOE_SONIC =>
          // A dense stack of pressure rings — the blast is the air itself
          var i = 0; while (i < 7) {
            val rp = ((phase * 0.30 + i * 0.143) % 1.0).toFloat
            val rr = 6f + rp * mR
            sb.strokeOval(sx, sy, rr, rr * flat, 2.6f * (1f - rp * 0.6f),
              bright(r), bright(g), bright(b), 0.45f * (1f - rp) * p, 20)
            i += 1
          }
        case AOE_VOID =>
          // Everything falls inward, and the middle is a hole rather than a light
          var d = 0; while (d < 14) {
            val t = ((phase * 0.4 + d * 0.0714) % 1.0).toFloat
            val ti = 1f - t
            val ang = d * 0.9f + phase * 0.8 + ti * 3.2
            val dist = mR * 0.9f * ti
            val px2 = sx + Math.cos(ang).toFloat * dist
            val py2 = sy + Math.sin(ang).toFloat * dist * flat
            sb.strokeLineSoft(px2, py2, sx + Math.cos(ang + 0.3).toFloat * dist * 0.86f,
              sy + Math.sin(ang + 0.3).toFloat * dist * 0.86f * flat, 2.6f,
              bright(r), bright(g), bright(b), 0.55f * t * p)
            d += 1
          }
          sb.fillOval(sx, sy, mR * 0.20f, mR * 0.20f * flat, 0.02f, 0.01f, 0.04f, 0.9f * p, 16)
          sb.strokeOval(sx, sy, mR * 0.21f, mR * 0.21f * flat, 2.5f, bright(r), bright(g), bright(b), 0.8f * p, 16)
        case _ =>
          // AOE_NATURE — barbed roots shoving up out of the ground
          var v = 0; while (v < 9) {
            val ang = v * Math.PI * 2 / 9 + proj.id * 0.4
            val grow = 0.55f + 0.45f * Math.sin(phase * 0.8 + v * 1.3).toFloat
            val rr = mR * 0.62f * grow
            val ca2 = Math.cos(ang).toFloat; val sa2 = Math.sin(ang).toFloat
            var seg = 0; while (seg < 3) {
              val t0 = seg / 3f; val t1 = (seg + 1) / 3f
              val wob0 = Math.sin(t0 * 5.0 + v).toFloat * 6f
              val wob1 = Math.sin(t1 * 5.0 + v).toFloat * 6f
              val x0 = sx + ca2 * rr * t0 - sa2 * wob0; val y0 = sy + sa2 * rr * t0 * flat + ca2 * wob0 * flat - t0 * 9f
              val x1 = sx + ca2 * rr * t1 - sa2 * wob1; val y1 = sy + sa2 * rr * t1 * flat + ca2 * wob1 * flat - t1 * 9f
              sb.strokeLine(x0, y0, x1, y1, 6f * (1f - t0 * 0.5f), 0.10f, 0.09f, 0.05f, 0.7f * p)
              sb.strokeLine(x0, y0, x1, y1, 3.8f * (1f - t0 * 0.5f), dr, dg, db, 0.9f * p)
              if (seg == 1) {
                sb.strokeLine(x1, y1, x1 - sa2 * 9f, y1 + ca2 * 9f * flat - 4f, 2.4f, dark(r), dark(g), dark(b), 0.7f * p)
              }
              seg += 1
            }
            v += 1
          }
      }

      // Core: bright and outlined so the epicentre is never ambiguous
      sb.strokeOval(sx, sy, 17f, 17f * flat, 3.5f, 0.05f, 0.05f, 0.07f, 0.85f * p, 14)
      sb.fillOval(sx, sy, 16f, 16f * flat, dr, dg, db, 0.92f * p, 14)
      sb.fillOval(sx, sy, 10f, 10f * flat, mix(dr, bright(r), 0.5f), mix(dg, bright(g), 0.5f),
        mix(db, bright(b), 0.5f), 0.85f * p, 12)
      if (kind != AOE_VOID) sb.fillOval(sx, sy, 4.5f, 4.5f * flat, 1f, 1f, 1f, 0.9f * p, 8)

      var star = 0; while (star < 6) {
        val starPhase = ((phase * 0.35 + star * 0.167) % 1.0).toFloat
        val starAngle = phase * 0.6 + star * Math.PI / 3
        val starDist = 12f + starPhase * mR
        drawSparkleStar(sx + Math.cos(starAngle).toFloat * starDist,
          sy + Math.sin(starAngle).toFloat * starDist * flat, 6f * (1f - starPhase * 0.5f),
          bright(r), bright(g), bright(b), 0.6f * (1f - starPhase) * p, sb, phase + star)
        star += 1
      }
    }

  private val CHN_CHAIN = 0
  private val CHN_ROPE = 1

  /**
   * Tether thrown at a target: a length of chain, or a rope with a grappling hook.
   *
   * The old version drew a zigzag with glowing nodes and forks — an electrical arc — so a
   * gladiator's rope, a warden's chain and a puppeteer's string all read as lightning.
   * A chain is now actual interlocking links and a rope is two twisted strands, both
   * sagging between the caster and the head.
   */
  private def chainProj(kind: Int, r: Float, g: Float, b: Float, worldLen: Float = 6f): Renderer =
    (proj, sx, sy, sb, tick) => {
      beamTip(sx, sy, proj, worldLen)
      val tipX = _tipX; val tipY = _tipY
      val phase = (tick + proj.id * 23) * 0.3
      computeAllDynamics(proj, r, g, b, phase)
      val p = (0.82f + 0.18f * Math.sin(phase * _stPulseMult).toFloat) * dynAlpha
      val dr = _evoR; val dg = _evoG; val db = _evoB
      val ds = Math.min(dynScale, 1.3f)
      val dx = tipX - sx; val dy = tipY - sy
      val len = Math.sqrt(dx * dx + dy * dy).toFloat
      if (len >= 1f) {
      val dirX = dx / len; val dirY = dy / len
      val nx = -dirY; val ny = dirX

      // A thrown tether hangs. The sag is what separates it from a beam, and it swings
      // slowly so the line looks like it has weight rather than being a drawn stroke.
      val sag = len * 0.16f * (0.75f + 0.25f * Math.sin(phase * 0.9).toFloat)
      @inline def px(t: Float): Float = sx + dx * t + nx * (sag * 4f * t * (1f - t))
      @inline def py(t: Float): Float = sy + dy * t + ny * (sag * 4f * t * (1f - t)) + sag * 2.2f * t * (1f - t)

      drawRibbonTrail(sx, sy, dirX, dirY, dr, dg, db, 0.18f * p, sb, tick, proj.id, 6, 26f * ds * dynTrail, 4f * ds, 1f)

      if (kind == CHN_CHAIN) {
        val links = 9
        val lw = 5.2f * ds
        var i = 0; while (i <= links) {
          val t = i.toFloat / links
          val cx = px(t); val cy = py(t)
          // Alternating link orientation: flat / on-edge, which is what reads as a chain
          // rather than as a string of beads.
          val flat = (i % 2) == 0
          val rx = if (flat) lw * 1.35f else lw * 0.52f
          val ry = if (flat) lw * 0.66f else lw * 0.95f
          val ang = Math.atan2(dirY, dirX).toFloat
          val ca2 = Math.cos(ang); val sa2 = Math.sin(ang)
          // Approximate the rotated link by an axis-aligned ring scaled along the run
          val ex = Math.abs(rx * ca2) + Math.abs(ry * sa2)
          val ey = Math.abs(rx * sa2) + Math.abs(ry * ca2) * ISO_Y + 1f
          sb.strokeOval(cx, cy, ex.toFloat + 1.2f, ey.toFloat + 1.2f, 3.4f, 0.06f, 0.06f, 0.08f, 0.85f * p, 10)
          sb.strokeOval(cx, cy, ex.toFloat, ey.toFloat, 2.6f, dr, dg, db, 0.95f * p, 10)
          sb.strokeArc(cx, cy, ex.toFloat, ey.toFloat, -2.4f, 1.5f, 1.2f,
            mix(bright(r), 1f, 0.3f), mix(bright(g), 1f, 0.3f), mix(bright(b), 1f, 0.3f), 0.7f * p, 5)
          i += 1
        }
        // Anchor hook at the head: a hooked claw, not a glowing dot
        val hx = tipX; val hy = tipY
        sb.fillOvalSoft(hx, hy, 15f * ds * dynGlow, 12f * ds * dynGlow, dr, dg, db, 0.25f * p, 0f, 12)
        var k = -1; while (k <= 1) {
          val a = Math.atan2(dirY, dirX) + k * 0.75
          val ex = hx + Math.cos(a).toFloat * 15f * ds
          val ey = hy + Math.sin(a).toFloat * 15f * ds * ISO_Y
          sb.strokeLine(hx, hy, ex, ey, 5.4f * ds, 0.06f, 0.06f, 0.08f, 0.85f * p)
          sb.strokeLine(hx, hy, ex, ey, 3.4f * ds, dr, dg, db, 0.95f * p)
          k += 1
        }
        sb.fillOval(hx, hy, 6.5f * ds, 5.5f * ds, 0.06f, 0.06f, 0.08f, 0.85f * p, 10)
        sb.fillOval(hx, hy, 5f * ds, 4f * ds, mix(dr, 1f, 0.35f), mix(dg, 1f, 0.35f), mix(db, 1f, 0.35f), 0.95f * p, 10)
      } else {
        // Rope: two counter-phased strands braiding around the run
        val segs = 22
        val amp = 3.2f * ds
        var strand = 0; while (strand < 2) {
          val ph = strand * Math.PI
          var i = 0; while (i < segs) {
            val t0 = i.toFloat / segs; val t1 = (i + 1).toFloat / segs
            val o0 = Math.sin(t0 * 20.0 + ph + phase * 0.8).toFloat * amp
            val o1 = Math.sin(t1 * 20.0 + ph + phase * 0.8).toFloat * amp
            val x0 = px(t0) + nx * o0; val y0 = py(t0) + ny * o0 * ISO_Y
            val x1 = px(t1) + nx * o1; val y1 = py(t1) + ny * o1 * ISO_Y
            sb.strokeLine(x0, y0, x1, y1, 6.4f * ds, 0.10f, 0.07f, 0.05f, 0.7f * p)
            sb.strokeLine(x0, y0, x1, y1, 4.4f * ds, dr, dg, db, 0.95f * p)
            sb.strokeLine(x0, y0, x1, y1, 1.5f * ds, bright(r), bright(g), bright(b), 0.4f * p)
            i += 1
          }
          strand += 1
        }
        // Grappling hook: shank plus three curved flukes
        val hx = tipX; val hy = tipY
        val ang = Math.atan2(dirY, dirX)
        sb.fillOvalSoft(hx, hy, 16f * ds * dynGlow, 13f * ds * dynGlow, dr, dg, db, 0.22f * p, 0f, 12)
        val shx = hx - Math.cos(ang).toFloat * 12f * ds
        val shy = hy - Math.sin(ang).toFloat * 12f * ds * ISO_Y
        sb.strokeLine(shx, shy, hx, hy, 6f * ds, 0.06f, 0.06f, 0.08f, 0.85f * p)
        sb.strokeLine(shx, shy, hx, hy, 4f * ds, DKSTEEL_R, DKSTEEL_G, DKSTEEL_B, 0.95f * p)
        var k = 0; while (k < 3) {
          val a = ang + (k - 1) * 0.9
          val mx = hx + Math.cos(a).toFloat * 9f * ds
          val my = hy + Math.sin(a).toFloat * 9f * ds * ISO_Y
          val ex = mx + Math.cos(a - 1.1).toFloat * 8f * ds
          val ey = my + Math.sin(a - 1.1).toFloat * 8f * ds * ISO_Y
          sb.strokeLine(hx, hy, mx, my, 5.4f * ds, 0.06f, 0.06f, 0.08f, 0.85f * p)
          sb.strokeLine(mx, my, ex, ey, 4.6f * ds, 0.06f, 0.06f, 0.08f, 0.85f * p)
          sb.strokeLine(hx, hy, mx, my, 3.4f * ds, STEEL_R, STEEL_G, STEEL_B, 0.95f * p)
          sb.strokeLine(mx, my, ex, ey, 2.6f * ds, STEEL_R, STEEL_G, STEEL_B, 0.95f * p)
          k += 1
        }
      }

      // Origin cleat so the tether visibly starts at the caster
      sb.fillOval(sx, sy, 6f * ds, 5f * ds, 0.06f, 0.06f, 0.08f, 0.7f * p, 8)
      sb.fillOval(sx, sy, 4.5f * ds, 3.6f * ds, dr, dg, db, 0.85f * p, 8)
      drawReturnGhosts(sx, sy, 14f * ds, dr, dg, db, p, sb, proj)
      }
    }

  /** Small fast bullet — CARTOONISH with bold outline, dramatic muzzle flash, ribbon trail, shell casings */
  private def bulletProj(r: Float, g: Float, b: Float, size: Float = 5f): Renderer =
    (proj, sx, sy, sb, tick) => {
      val phase = (tick + proj.id * 31) * 0.4
      computeAllDynamics(proj, r, g, b, phase)
      val p = (0.7 + 0.3 * Math.sin(phase * _stPulseMult)).toFloat * dynAlpha
      val dr = _evoR; val dg = _evoG; val db = _evoB
      val ds = dynScale
      val sz = size * 1.5f * ds
      screenDir(proj)
      val ndx = _sdx; val ndy = _sdy
      val perpX = -ndy; val perpY = ndx

      // Speed lines behind
      drawSpeedLines(sx, sy, ndx, ndy, dr, dg, db, 0.35f * p, sb, 6, 35f * ds)

      // Ribbon trail behind bullet
      drawRibbonTrail(sx, sy, ndx, ndy, dr, dg, db, 0.3f * p, sb, tick, proj.id, 6, 40f * ds * dynTrail, 4f * ds, 0.8f)

      // Bullet body
      val bodyLen = sz * 3f
      val bodyW = sz * 1f
      val tipX = sx + ndx * bodyLen * 0.5f
      val tipY = sy + ndy * bodyLen * 0.5f
      val tailX = sx - ndx * bodyLen * 0.5f
      val tailY = sy - ndy * bodyLen * 0.5f
      val flashX = tailX - ndx * 4f; val flashY = tailY - ndy * 4f

      // Big 3-layer muzzle flash at rear — dramatic orange-yellow fire
      val flashSize = 22f * (0.7f + 0.3f * Math.sin(phase * 3).toFloat) * ds
      // Layer 1: Dark outline puff
      sb.fillOvalSoft(flashX, flashY, flashSize * 1.3f, flashSize * 0.9f, outline(1f), outline(0.5f), outline(0.1f), 0.3f * p, 0f, 12)
      // Layer 2: Orange-yellow fire
      sb.fillOvalSoft(flashX, flashY, flashSize, flashSize * 0.7f, 1f, 0.7f, 0.2f, 0.4f * p, 0f, 10)
      sb.fillOval(flashX, flashY, flashSize * 0.7f, flashSize * 0.5f, 1f, 0.85f, 0.3f, 0.5f * p, 8)
      // Layer 3: White-hot core
      sb.fillOval(flashX, flashY, flashSize * 0.35f, flashSize * 0.25f, 1f, 1f, 0.8f, 0.65f * p, 6)

      // 4 cartridge smoke puffs with outlines
      { var puff = 0; while (puff < 4) {
        val puffT = ((tick * 0.04 + puff * 0.25 + proj.id * 0.17) % 1.0).toFloat
        val puffDrift = puff * 0.4f - 0.6f
        val puffX = flashX - ndx * (8f + puffT * 20f) + perpX * puffDrift * 12f
        val puffY = flashY - ndy * (8f + puffT * 20f) + perpY * puffDrift * 12f - puffT * 6f
        val puffSz = (5f + puffT * 8f) * ds
        val puffA = 0.3f * (1f - puffT) * p
        sb.strokeOval(puffX, puffY, puffSz + 1f, puffSz * 0.8f + 1f, 1.5f, 0.2f, 0.2f, 0.2f, puffA * 0.4f, 8)
        sb.fillOval(puffX, puffY, puffSz, puffSz * 0.8f, 0.55f, 0.5f, 0.45f, puffA, 8)
      ; puff += 1 } }

      // 3 shell casing particles tumbling behind
      { var cas = 0; while (cas < 3) {
        val casT = ((tick * 0.05 + cas * 0.33 + proj.id * 0.19) % 1.0).toFloat
        val casSpin = tick * 0.25f + cas * 2.1f
        val casX = flashX - ndx * casT * 15f + perpX * (cas - 1) * 10f * casT
        val casY = flashY - ndy * casT * 15f + perpY * (cas - 1) * 10f * casT + casT * casT * 18f
        val casLen = 4f * ds; val casW = 2f * ds
        val casA = 0.5f * (1f - casT) * p
        val ccos = Math.cos(casSpin).toFloat; val csin = Math.sin(casSpin).toFloat
        sb.strokeLine(casX - ccos * casLen, casY - csin * casLen,
          casX + ccos * casLen, casY + csin * casLen, casW + 1f, 0.15f, 0.12f, 0.08f, casA * 0.5f)
        sb.strokeLine(casX - ccos * casLen, casY - csin * casLen,
          casX + ccos * casLen, casY + csin * casLen, casW, 0.8f, 0.7f, 0.3f, casA)
      ; cas += 1 } }

      // Dark cartoon outline
      sb.strokeLine(tailX, tailY, tipX, tipY, bodyW * 2.5f, outline(r), outline(g), outline(b), 0.9f * p)
      // Metallic body — evolved colors
      sb.strokeLine(tailX, tailY, tipX, tipY, bodyW * 1.8f, dr, dg, db, 0.95f * p)
      // Specular highlight — charge whitening
      sb.strokeLine(tailX, tailY, tipX, tipY, bodyW * 0.6f,
        mix(bright(r), 1f, _chgBright), mix(bright(g), 1f, _chgBright), mix(bright(b), 1f, _chgBright), 0.7f * p)
      // Rounded tip with outline
      sb.strokeOval(tipX, tipY, bodyW * 1.6f, bodyW * 1.2f, 2f, outline(r), outline(g), outline(b), 0.8f * p, 8)
      sb.fillOval(tipX, tipY, bodyW * 1.4f, bodyW * 1f, bright(r), bright(g), bright(b), 0.9f * p, 8)

      // Impact ring pulsing at front
      val impPulse = ((phase * 0.5) % 1.0).toFloat
      val impR = 4f + impPulse * 14f * ds
      val impA = 0.45f * (1f - impPulse) * p
      sb.strokeOval(tipX, tipY, impR, impR * 0.7f, 2.5f * (1f - impPulse * 0.4f),
        bright(r), bright(g), bright(b), impA, 10)

      // Spark burst at tip
      drawSparkBurst(tipX, tipY, dr, dg, db, 0.35f * p, sb, tick, proj.id, 4, 12f * ds)

      // 3 sparkle stars
      { var star = 0; while (star < 3) {
        val starPhase = ((phase * 0.4 + star * 0.33) % 1.0).toFloat
        val starAngle = phase * 1.5 + star * Math.PI * 2 / 3
        val starDist = sz * 1.5f + starPhase * sz * 1.2f
        val starX = sx + Math.cos(starAngle).toFloat * starDist
        val starY = sy + Math.sin(starAngle).toFloat * starDist * 0.55f
        drawSparkleStar(starX, starY, 4.5f * (1f - starPhase * 0.4f) * ds,
          bright(r), bright(g), bright(b), 0.45f * (1f - starPhase) * p, sb, phase * 2 + star)
      ; star += 1 } }

      // Charge crackle
      drawChargeCrackle(sx, sy, sz * 2.5f, r, g, b, p, sb, phase, proj.chargeLevel)
      drawReturnGhosts(sx, sy, sz * 2f, dr, dg, db, p, sb, proj)
    }

  private val FIST_PARTS = Array(
    part(Array(-1.20f,-0.28f, -0.30f,-0.38f, -0.30f,0.38f, -1.20f,0.28f), 0.62f, 0.46f, 0.34f, 0.35f),
    part(Array(-0.46f,-0.50f, -0.24f,-0.52f, -0.24f,0.52f, -0.46f,0.50f), DKSTEEL_R, DKSTEEL_G, DKSTEEL_B, 0.45f),
    part(Array(-0.26f,-0.52f, 0.42f,-0.58f, 0.82f,-0.30f, 0.82f,0.30f, 0.42f,0.58f, -0.26f,0.52f),
      0.72f, 0.55f, 0.42f, 0.35f),
    part(Array(-0.20f,-0.34f, 0.40f,-0.40f, 0.66f,-0.20f, 0.10f,-0.12f), 0.86f, 0.70f, 0.56f, 0.20f)
  )

  /** Thrown/charged punch. The old version was three concentric rings over a plain
   *  ellipse, so a monk's strike and a gorilla's grab arrived as the same grey coin. */
  private def fistProj(r: Float, g: Float, b: Float, size: Float = 14f): Renderer =
    (proj, sx, sy, sb, tick) => {
      val phase = (tick + proj.id * 23) * 0.4
      computeAllDynamics(proj, r, g, b, phase)
      val p = (0.8f + 0.2f * Math.sin(phase * _stPulseMult).toFloat) * dynAlpha
      val dr = _evoR; val dg = _evoG; val db = _evoB
      val ds = Math.min(dynScale, 1.35f)
      val sz = size * 1.15f * ds
      screenDir(proj)
      val ndx = _sdx; val ndy = _sdy
      val frontX = sx + ndx * sz * 0.9f; val frontY = sy + ndy * sz * 0.9f

      drawSpeedLines(sx, sy, ndx, ndy, dr, dg, db, 0.42f * p, sb, 6, sz * 2.4f)
      drawRibbonTrail(sx, sy, ndx, ndy, dr, dg, db, 0.3f * p, sb, tick, proj.id, 6,
        sz * 2.6f * dynTrail, sz * 0.55f, 1.5f)
      sb.fillOval(sx, sy + sz * 0.62f, sz * 1.3f, sz * 0.3f, 0f, 0f, 0f, 0.22f * p, 14)

      // Impact shock stacking up in front of the knuckles
      val shock = ((phase * 0.4) % 1.0).toFloat
      if (shock < 0.6f) {
        val sr = sz * (0.7f + shock * 2.2f)
        sb.strokeOval(frontX, frontY, sr, sr * ISO_Y, 5f * (1f - shock / 0.6f),
          bright(r), bright(g), bright(b), 0.42f * (1f - shock / 0.6f) * p, 16)
      }
      sb.fillOvalSoft(frontX, frontY, sz * 1.5f * dynGlow, sz * 1.2f * dynGlow, dr, dg, db, 0.3f * p, 0f, 14)

      // Two afterimages so the punch reads as travelling, not hovering
      var ghost = 2; while (ghost >= 1) {
        drawPartsDirFlat(sb, FIST_PARTS, sx - ndx * ghost * 10f, sy - ndy * ghost * 10f, ndx, ndy,
          sz * (1f - ghost * 0.06f), dr * 0.8f, dg * 0.8f, db * 0.8f, 0.15f * (1f - (ghost - 1) * 0.35f) * p)
        ghost -= 1
      }

      drawPartsDir(sb, FIST_PARTS, sx, sy, ndx, ndy, sz, dr, dg, db, 0.97f * dynAlpha,
        clampF(sz * 0.13f, 1.5f, 3f))

      // Four knuckles across the leading face
      var k = 0; while (k < 4) {
        val ly = -0.36f + k * 0.24f
        dirPoint(0.70f, ly, sx, sy, ndx, ndy, sz)
        sb.fillOval(_ptX, _ptY, sz * 0.15f, sz * 0.15f, 0.06f, 0.05f, 0.07f, 0.6f * p, 8)
        sb.fillOval(_ptX - sz * 0.03f, _ptY - sz * 0.03f, sz * 0.11f, sz * 0.11f,
          mix(0.88f, dr, 0.3f), mix(0.72f, dg, 0.3f), mix(0.58f, db, 0.3f), 0.9f * p, 8)
        k += 1
      }

      drawSparkBurst(frontX, frontY, dr, dg, db, 0.45f * p, sb, tick, proj.id, 5, sz * 0.8f)
      drawChargeCrackle(sx, sy, sz, r, g, b, p, sb, phase, proj.chargeLevel)
      drawReturnGhosts(sx, sy, sz * 0.7f, dr, dg, db, p, sb, proj)
    }

  // ── Sweep kinds for `wave` ──
  private val WAV_WIND = 0
  private val WAV_SAND = 1
  private val WAV_SONIC = 2
  private val WAV_FLAME = 3
  private val WAV_ACID = 4
  private val WAV_IMPACT = 5
  private val WAV_WATER = 6

  /**
   * Crescent sweep travelling forward — a gust, a sand blast, a sonic wave, a wall of
   * flame, an acid spray, a shoulder charge.
   *
   * This used to be a four-vertex polygon (tip, both wingtips, tail), which renders as a
   * triangle: a bard's song, a windwalker's cyclone and an inferno's flame wall all came
   * out as the same outlined triangle in three colours. It is now a real arc band, built
   * so the arc passes exactly through the projectile's position and bulges forward,
   * which is what a wave front actually looks like from above.
   */
  private def wave(kind: Int, r: Float, g: Float, b: Float, spread: Float = 32f): Renderer =
    (proj, sx, sy, sb, tick) => {
      val phase = (tick + proj.id * 41) * 0.4
      computeAllDynamics(proj, r, g, b, phase)
      val p = (0.72f + 0.28f * Math.sin(phase * _stPulseMult).toFloat) * dynAlpha
      val dr = _evoR; val dg = _evoG; val db = _evoB
      val ds = dynScale
      val R = spread * 1.25f * ds
      screenDir(proj)
      val ndx = _sdx; val ndy = _sdy

      // Arc centre placed so the band's leading edge runs through (sx, sy). Solving in the
      // ellipse's own parameter space keeps the crescent square to the travel direction at
      // every heading — using atan2(ndy, ndx) directly skews it badly on the diagonals.
      val th = Math.atan2(ndy / ISO_Y, ndx).toFloat
      val cxA = sx - R * Math.cos(th).toFloat
      val cyA = sy - R * ISO_Y * Math.sin(th).toFloat
      val sweep = kind match {
        case WAV_WATER => 1.75f
        case WAV_SONIC => 1.9f
        case WAV_IMPACT => 1.1f
        case WAV_FLAME => 1.7f
        case _ => 1.55f
      }
      val start = th - sweep * 0.5f
      val thickness = kind match {
        case WAV_WATER => 0.48f
        case WAV_IMPACT => 0.42f
        case WAV_SONIC => 0.22f
        case _ => 0.32f
      }

      // Trailing echoes of the front, fading back
      var e = 3; while (e >= 1) {
        val eR = R * (1f - e * 0.16f)
        val eA = 0.20f * (1f - (e - 1) * 0.3f) * p
        sb.fillArcBand(cxA, cyA, eR * (1f - thickness), eR * (1f - thickness) * ISO_Y, eR, eR * ISO_Y,
          start + e * 0.05f, sweep - e * 0.1f, 12, r, g, b, eA, eA)
        e -= 1
      }

      // Soft glow hugging the front. fillArcBand ramps alpha ALONG the sweep, so the
      // radial falloff has to come from nesting bands rather than from its ramp — using
      // the ramp for it leaves one horn of the crescent bright and the other invisible.
      sb.fillArcBand(cxA, cyA, R * 0.90f, R * 0.90f * ISO_Y, R * 1.34f * dynGlow, R * 1.34f * ISO_Y * dynGlow,
        start - 0.06f, sweep + 0.12f, 14, dr, dg, db, 0.20f * p, 0.20f * p)

      // Body of the wave front: three nested bands, densest at the leading edge
      sb.fillArcBand(cxA, cyA, R * (1f - thickness), R * (1f - thickness) * ISO_Y, R, R * ISO_Y,
        start, sweep, 16, dr, dg, db, 0.42f * p, 0.42f * p)
      sb.fillArcBand(cxA, cyA, R * (1f - thickness * 0.6f), R * (1f - thickness * 0.6f) * ISO_Y, R, R * ISO_Y,
        start, sweep, 16, dr, dg, db, 0.34f * p, 0.34f * p)
      sb.fillArcBand(cxA, cyA, R * (1f - thickness * 0.25f), R * (1f - thickness * 0.25f) * ISO_Y, R, R * ISO_Y,
        start, sweep, 16, mix(dr, 1f, 0.25f), mix(dg, 1f, 0.25f), mix(db, 1f, 0.25f), 0.40f * p, 0.40f * p)
      // Dark contour behind the leading edge, then the bright edge itself
      sb.strokeArc(cxA, cyA, R * 1.015f, R * 1.015f * ISO_Y, start, sweep,
        if (kind == WAV_WIND || kind == WAV_WATER) 6f else 4.5f,
        0.06f, 0.05f, 0.07f, if (kind == WAV_WIND || kind == WAV_WATER) 0.75f * p else 0.55f * p, 16)
      sb.strokeArc(cxA, cyA, R, R * ISO_Y, start, sweep, 2.4f,
        mix(bright(r), 1f, _chgBright), mix(bright(g), 1f, _chgBright), mix(bright(b), 1f, _chgBright),
        0.95f * p, 16)

      kind match {
        case WAV_WIND =>
          // Streaks curling along the front — the wind's direction made visible
          var i = 0; while (i < 5) {
            val t = ((phase * 0.18 + i * 0.2) % 1.0).toFloat
            val a0 = start + sweep * (0.12f + 0.76f * ((i + t * 0.5f) % 1f))
            val rr = R * (0.62f + 0.3f * Math.sin(phase * 1.4 + i).toFloat)
            sb.strokeArc(cxA, cyA, rr, rr * ISO_Y, a0, 0.42f, 1.8f,
              bright(r), bright(g), bright(b), 0.4f * p, 6)
            i += 1
          }
        case WAV_SAND =>
          // Grains scoured off the front
          var i = 0; while (i < 14) {
            val t = ((phase * 0.25 + i * 0.0714) % 1.0).toFloat
            val a0 = start + sweep * ((i * 0.137f + t * 0.3f) % 1f)
            val rr = R * (0.72f + t * 0.42f)
            val gx = cxA + Math.cos(a0).toFloat * rr
            val gy = cyA + Math.sin(a0).toFloat * rr * ISO_Y
            sb.fillOval(gx, gy, 2.6f + t * 2f, 2f + t * 1.6f, dark(r), dark(g), dark(b), 0.5f * (1f - t) * p, 5)
            i += 1
          }
        case WAV_SONIC =>
          // Concentric ripples behind the front
          var i = 1; while (i <= 3) {
            val rr = R * (1f - i * 0.17f) - ((phase * 3) % 8).toFloat
            if (rr > 6f) sb.strokeArc(cxA, cyA, rr, rr * ISO_Y, start + 0.08f, sweep - 0.16f, 1.6f,
              bright(r), bright(g), bright(b), 0.34f * (1f - i * 0.22f) * p, 14)
            i += 1
          }
        case WAV_FLAME =>
          // Tongues licking forward off the crest, plus embers riding behind it
          var i = 0; while (i < 7) {
            val a0 = start + sweep * (0.08f + 0.84f * i / 6f)
            val lick = (0.5f + 0.5f * Math.sin(phase * 3.4 + i * 1.9).toFloat)
            val ca2 = Math.cos(a0).toFloat; val sa2 = Math.sin(a0).toFloat
            val bx = cxA + ca2 * R * 0.98f; val by = cyA + sa2 * R * ISO_Y * 0.98f
            val tx = cxA + ca2 * (R + R * 0.34f * lick); val ty = cyA + sa2 * (R + R * 0.34f * lick) * ISO_Y
            sb.strokeLineSoft(bx, by, tx, ty, 5f + lick * 4f, 1f, mix(0.45f, 0.9f, lick), 0.15f, 0.55f * p)
            sb.strokeLineSoft(bx, by, (bx + tx) * 0.5f, (by + ty) * 0.5f, 3f, 1f, 0.95f, 0.6f, 0.5f * p)
            i += 1
          }
          var k = 0; while (k < 6) {
            val t = ((phase * 0.3 + k * 0.167) % 1.0).toFloat
            val a0 = start + sweep * ((k * 0.19f) % 1f)
            val rr = R * (0.9f - t * 0.5f)
            sb.fillOval(cxA + Math.cos(a0).toFloat * rr, cyA + Math.sin(a0).toFloat * rr * ISO_Y - t * 10f,
              3.5f * (1f - t), 3.5f * (1f - t), 1f, 0.7f, 0.25f, 0.7f * (1f - t) * p, 6)
            k += 1
          }
        case WAV_WATER =>
          // Foam caps riding the crest and spray thrown off the top of it
          var i = 0; while (i < 9) {
            val a0 = start + sweep * (0.06f + 0.88f * i / 8f)
            val ca2 = Math.cos(a0).toFloat; val sa2 = Math.sin(a0).toFloat
            val bob = 0.5f + 0.5f * Math.sin(phase * 2.6 + i * 1.4).toFloat
            val fx = cxA + ca2 * R * 0.97f; val fy = cyA + sa2 * R * ISO_Y * 0.97f
            sb.fillOval(fx, fy - bob * 5f, 7f + bob * 4f, 5f + bob * 3f, 1f, 1f, 1f, 0.55f * p, 8)
            sb.fillOval(fx, fy - bob * 9f, 3.4f, 2.6f, 1f, 1f, 1f, 0.45f * p, 6)
            i += 1
          }
          var k = 0; while (k < 7) {
            val t = ((phase * 0.4 + k * 0.143) % 1.0).toFloat
            val a0 = start + sweep * ((k * 0.19f) % 1f)
            val rr = R * 0.95f
            sb.fillOval(cxA + Math.cos(a0).toFloat * rr, cyA + Math.sin(a0).toFloat * rr * ISO_Y
              - Math.sin(t * Math.PI).toFloat * 22f, 3.2f * (1f - t * 0.4f), 4f * (1f - t * 0.4f),
              bright(r), bright(g), bright(b), 0.8f * (1f - t) * p, 6)
            k += 1
          }
        case WAV_ACID =>
          // Droplets sagging off the underside of the spray
          var i = 0; while (i < 8) {
            val t = ((phase * 0.35 + i * 0.125) % 1.0).toFloat
            val a0 = start + sweep * (0.1f + 0.8f * i / 7f)
            val rr = R * 0.92f
            val gx = cxA + Math.cos(a0).toFloat * rr
            val gy = cyA + Math.sin(a0).toFloat * rr * ISO_Y + t * 16f
            sb.fillOval(gx, gy, 3.4f * (1f - t * 0.4f), 4.6f * (1f - t * 0.4f),
              bright(r), bright(g), bright(b), 0.65f * (1f - t) * p, 7)
            i += 1
          }
        case _ =>
          // WAV_IMPACT — a hard double crest with speed streaks raking back
          sb.strokeArc(cxA, cyA, R * 0.80f, R * 0.80f * ISO_Y, start + 0.14f, sweep - 0.28f, 3f,
            bright(r), bright(g), bright(b), 0.55f * p, 12)
          var i = 0; while (i < 5) {
            val a0 = start + sweep * (0.12f + 0.76f * i / 4f)
            val ca2 = Math.cos(a0).toFloat; val sa2 = Math.sin(a0).toFloat
            sb.strokeLineSoft(cxA + ca2 * R * 0.5f, cyA + sa2 * R * 0.5f * ISO_Y,
              cxA + ca2 * R * 0.94f, cyA + sa2 * R * 0.94f * ISO_Y, 3f,
              bright(r), bright(g), bright(b), 0.4f * p)
            i += 1
          }
      }

      // Sparks riding the crest
      var s2 = 0; while (s2 < 5) {
        val t = ((phase * 0.5 + s2 * 0.2) % 1.0).toFloat
        val a0 = start + sweep * ((s2 * 0.23f + t * 0.4f) % 1f)
        drawSparkleStar(cxA + Math.cos(a0).toFloat * R, cyA + Math.sin(a0).toFloat * R * ISO_Y,
          5f * (1f - t * 0.5f) * ds, bright(r), bright(g), bright(b), 0.55f * (1f - t) * p, sb, phase + s2)
        s2 += 1
      }

      drawChargeCrackle(sx, sy, R * 0.4f, r, g, b, p, sb, phase, proj.chargeLevel)
      drawReturnGhosts(sx, sy, R * 0.35f, dr, dg, db, p, sb, proj)
    }

  // ═══════════════════════════════════════════════════════════════
  //  TIER 1: NEW SPECIALIZED RENDERERS
  // ═══════════════════════════════════════════════════════════════

  /** Void Bolt - swirling dark vortex with reality-distortion ripple rings */
  private def drawVoidBolt(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 43) * 0.35
    computeAllDynamics(proj, 0.25f, 0.05f, 0.4f, phase)
    val p = (0.85 + 0.15 * Math.sin(phase * _stPulseMult)).toFloat * dynAlpha
    val dr = _evoR; val dg = _evoG; val db = _evoB
    val ds = dynScale
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy

    // Speed lines behind
    drawSpeedLines(sx, sy, ndx, ndy, 0.2f, 0.05f, 0.35f, 0.25f * p, sb, 5 + (_lifePct * 2).toInt, 32f * ds)

    // Ribbon trail
    drawRibbonTrail(sx, sy, ndx, ndy, 0.2f, 0.05f, 0.35f, 0.3f * p, sb, tick, proj.id,
      10, 50f * ds * dynTrail, 10f * ds, 1.5f)

    // Purple glow halo (55px+)
    val haloPulse = (0.7f + 0.3f * Math.sin(phase * 2.2).toFloat)
    sb.fillOvalSoft(sx, sy, 39.6f * ds * dynGlow * haloPulse, 31.7f * ds * dynGlow * haloPulse,
      0.3f, 0.05f, 0.5f, 0.25f * p, 0f, 20)
    sb.fillOvalSoft(sx, sy, 27.4f * ds * dynGlow, 21.6f * ds * dynGlow, 0.2f, 0.02f, 0.4f, 0.15f * p, 0f, 16)

    // 3 expanding void rings
    var vr = 0; while (vr < 3) {
      val vrP = ((phase * 0.35 + vr * 0.33) % 1.0).toFloat
      val vrR = (8f + vrP * 28f) * ds
      val vrA = 0.4f * (1f - vrP) * p
      sb.strokeOval(sx, sy, vrR + 1.5f, (vrR + 1.5f) * 0.6f, 2.5f * (1f - vrP * 0.5f),
        0.08f, 0f, 0.15f, vrA * 0.5f, 12)
      sb.strokeOval(sx, sy, vrR, vrR * 0.6f, 2f * (1f - vrP * 0.5f),
        0.35f, 0.1f, 0.55f, vrA, 12)
    ; vr += 1 }

    // 7 concentric distortion rings — rotate and pulse in opposite directions
    var ring = 0; while (ring < 7) {
      val ringR = (10f + ring * 7f) * ds
      val ringPhase = phase * (if (ring % 2 == 0) 1.2 else -0.9) + ring * 0.7
      val ringPulse = (0.8 + 0.2 * Math.sin(ringPhase * 2)).toFloat
      val ringAlpha = 0.35f * (1f - ring * 0.1f) * p
      sb.strokeOval(sx + Math.cos(ringPhase).toFloat * 2, sy + Math.sin(ringPhase).toFloat * 1.5f,
        ringR * ringPulse, ringR * 0.6f * ringPulse, 2f, 0.25f, 0.05f, 0.4f, ringAlpha, 12)
    ; ring += 1 }

    // 12 particles spiraling inward toward center
    var i = 0; while (i < 12) {
      val t = ((tick * 0.06 + i * 0.083 + proj.id * 0.13) % 1.0).toFloat
      val inward = 1f - t
      val spiralAngle = phase * 2.5 + i * Math.PI * 2 / 12 + t * Math.PI * 3
      val dist = 34f * inward * ds
      val px = sx + Math.cos(spiralAngle).toFloat * dist
      val py = sy + Math.sin(spiralAngle).toFloat * dist * 0.55f
      val s = (4f + inward * 4f) * ds
      // Particle outline
      sb.fillOval(px, py, s + 1f, s * 0.7f + 1f, 0.08f, 0f, 0.12f, 0.3f * t * p, 6)
      sb.fillOval(px, py, s, s * 0.7f, 0.35f, 0.12f, 0.55f, 0.55f * t * p, 6)
    ; i += 1 }

    // Bold 3.5px dark outline on central core
    sb.strokeOval(sx, sy, 22f * ds, 16f * ds, 3.5f, 0.02f, 0f, 0.04f, 0.85f * p, 14)
    // Dark purple body
    sb.fillOval(sx, sy, 20f * ds, 14f * ds, 0.12f, 0.02f, 0.22f, 0.92f * p, 14)
    // Mid-layer
    sb.fillOval(sx, sy, 14f * ds, 10f * ds, 0.18f, 0.04f, 0.3f, 0.7f * p, 12)
    // Black center
    sb.fillOval(sx, sy, 8f * ds, 6f * ds, 0.02f, 0f, 0.04f, 0.98f * p, 10)
    // Bright purple hot spot
    val bc = _chgBright
    sb.fillOval(sx, sy, 4f * ds, 3f * ds,
      mix(0.5f, 1f, bc), mix(0.2f, 1f, bc), mix(0.8f, 1f, bc), 0.9f * p, 8)
    // Cartoon highlight
    sb.fillOval(sx - 3f * ds, sy - 3f * ds, 5f * ds, 3.5f * ds, 0.6f, 0.4f, 0.9f, 0.35f * p, 8)

    // 6 flickering reality cracks with forks
    var c = 0; while (c < 6) {
      val crackAngle = phase * 0.8 + c * Math.PI / 3
      val jitter = Math.sin(phase * 5 + c * 3.1).toFloat * 5f
      val crackLen = (22f + Math.sin(phase * 3 + c * 2.7).toFloat * 10f) * ds
      val cx0 = sx + Math.cos(crackAngle).toFloat * 7f * ds
      val cy0 = sy + Math.sin(crackAngle).toFloat * 4f * ds
      val cx1 = sx + Math.cos(crackAngle).toFloat * crackLen + jitter
      val cy1 = sy + Math.sin(crackAngle).toFloat * crackLen * 0.5f + jitter * 0.3f
      val cAlpha = (0.25 + 0.25 * Math.sin(phase * 7 + c * 2.3)).toFloat * p
      sb.strokeLine(cx0, cy0, cx1, cy1, 2f, 0.4f, 0.15f, 0.6f, cAlpha)
      // Fork at end
      val forkAngle1 = crackAngle + 0.5 + Math.sin(phase * 4 + c).toFloat * 0.3
      val forkAngle2 = crackAngle - 0.4 + Math.cos(phase * 3.5 + c).toFloat * 0.3
      val forkLen = crackLen * 0.4f
      val fx1 = cx1 + Math.cos(forkAngle1).toFloat * forkLen
      val fy1 = cy1 + Math.sin(forkAngle1).toFloat * forkLen * 0.5f
      val fx2 = cx1 + Math.cos(forkAngle2).toFloat * forkLen
      val fy2 = cy1 + Math.sin(forkAngle2).toFloat * forkLen * 0.5f
      sb.strokeLine(cx1, cy1, fx1, fy1, 1.2f, 0.5f, 0.2f, 0.7f, cAlpha * 0.7f)
      sb.strokeLine(cx1, cy1, fx2, fy2, 1f, 0.5f, 0.2f, 0.7f, cAlpha * 0.5f)
    ; c += 1 }

    // Sparkle stars (purple)
    { var i = 0; while (i < 4) {
      val starPhase = ((phase * 0.5 + i * 0.25) % 1.0).toFloat
      val starAngle = phase * 1.3 + i * Math.PI * 2 / 4
      val starDist = (16f + starPhase * 20f) * ds
      val starX = sx + Math.cos(starAngle).toFloat * starDist
      val starY = sy + Math.sin(starAngle).toFloat * starDist * 0.55f
      drawSparkleStar(starX, starY, 5f * (1f - starPhase * 0.5f) * ds,
        0.5f, 0.2f, 0.8f, 0.5f * (1f - starPhase) * p, sb, phase * 2 + i)
    ; i += 1 } }

    // Spark burst at front
    drawSparkBurst(sx + ndx * 12f * ds, sy + ndy * 12f * ds,
      0.3f, 0.1f, 0.5f, p, sb, tick, proj.id, 5 + _chgSparkCount, 14f * ds)

    drawChargeCrackle(sx, sy, 22f * ds, 0.25f, 0.05f, 0.4f, p, sb, phase, proj.chargeLevel)
    drawReturnGhosts(sx, sy, 22f * ds, dr, dg, db, p, sb, proj)
  }

  /** Gravity Ball - dense dark sphere with orbiting debris ring */
  private def drawGravityBall(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 37) * 0.3
    computeAllDynamics(proj, 0.15f, 0.05f, 0.3f, phase)
    val p = (0.85 + 0.15 * Math.sin(phase * _stPulseMult)).toFloat * dynAlpha
    val dr = _evoR; val dg = _evoG; val db = _evoB
    val ds = dynScale
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy

    // Speed lines behind
    drawSpeedLines(sx, sy, ndx, ndy, 0.15f, 0.05f, 0.3f, 0.2f * p, sb, 5 + (_lifePct * 2).toInt, 30f * ds)

    // Ribbon trail
    drawRibbonTrail(sx, sy, ndx, ndy, 0.15f, 0.05f, 0.3f, 0.3f * p, sb, tick, proj.id,
      10, 45f * ds * dynTrail, 9f * ds, 1.5f)

    // Glow halo (55px+)
    val haloPulse = (0.7f + 0.3f * Math.sin(phase * 1.8).toFloat)
    sb.fillOvalSoft(sx, sy, 39.6f * ds * dynGlow * haloPulse, 31.7f * ds * dynGlow * haloPulse,
      0.2f, 0.08f, 0.4f, 0.22f * p, 0f, 20)
    sb.fillOvalSoft(sx, sy, 27.4f * ds * dynGlow, 21.6f * ds * dynGlow, 0.15f, 0.05f, 0.3f, 0.12f * p, 0f, 16)

    // Ground shadow/distortion oval
    sb.fillOval(sx, sy + 14f * ds, 26f * ds, 8f * ds, 0f, 0f, 0f, 0.3f * p, 12)

    // 3 gravitational lensing arcs
    var arc = 0; while (arc < 3) {
      val arcP = ((phase * 0.3 + arc * 0.33) % 1.0).toFloat
      val arcR = (12f + arcP * 22f) * ds
      val arcA = 0.35f * (1f - arcP) * p
      sb.strokeOval(sx, sy, arcR + 1f, (arcR + 1f) * 0.6f, 2.5f * (1f - arcP * 0.4f),
        0.1f, 0.02f, 0.2f, arcA * 0.4f, 12)
      sb.strokeOval(sx, sy, arcR, arcR * 0.6f, 1.8f * (1f - arcP * 0.4f),
        0.3f, 0.15f, 0.55f, arcA, 12)
    ; arc += 1 }

    // 6 inward-pulling particle streams with more segments
    var stream = 0; while (stream < 6) {
      var seg = 0; while (seg < 6) {
        val t = ((tick * 0.05 + seg * 0.167 + stream * 0.167 + proj.id * 0.11) % 1.0).toFloat
        val inward = 1f - t
        val spiralA = phase * 1.5 + stream * Math.PI / 3 + t * Math.PI * 2
        val dist = 36f * inward * ds
        val px = sx + Math.cos(spiralA).toFloat * dist
        val py = sy + Math.sin(spiralA).toFloat * dist * 0.5f
        val pSz = (3.5f * inward + 1f) * ds
        // Dark outline on each particle
        sb.fillOval(px, py, pSz + 0.8f, (pSz + 0.8f) * 0.7f, 0.05f, 0f, 0.1f, 0.25f * t * p, 6)
        sb.fillOval(px, py, pSz, pSz * 0.7f, 0.35f, 0.15f, 0.55f, 0.5f * t * p, 6)
      ; seg += 1 }
    ; stream += 1 }

    // Bold 4px dark outline on core
    sb.strokeOval(sx, sy, 22f * ds, 16f * ds, 4f, 0.03f, 0f, 0.06f, 0.85f * p, 14)
    // Heavy dark purple/indigo body
    sb.fillOval(sx, sy, 20f * ds, 14.5f * ds, 0.12f, 0.04f, 0.28f, 0.93f * p, 14)
    // Mid-layer glow
    sb.fillOval(sx, sy, 14f * ds, 10f * ds, 0.2f, 0.1f, 0.4f, 0.75f * p, 12)
    // Bright compressed center
    val bc = _chgBright
    sb.fillOval(sx, sy, 9f * ds, 7f * ds,
      mix(0.45f, 1f, bc), mix(0.25f, 1f, bc), mix(0.8f, 1f, bc), 0.88f * p, 10)
    sb.fillOval(sx, sy, 4.5f * ds, 3.5f * ds,
      mix(0.7f, 1f, bc), mix(0.5f, 1f, bc), mix(1f, 1f, bc), 0.92f * p, 8)
    // Cartoon highlight
    sb.fillOval(sx - 4f * ds, sy - 3.5f * ds, 6f * ds, 4f * ds, 0.5f, 0.35f, 0.8f, 0.35f * p, 8)

    // Orbiting debris ring — 12 particles with outlines (Saturn-like)
    var d = 0; while (d < 12) {
      val dAngle = phase * 1.8 + d * Math.PI * 2 / 12
      val dRadX = (24f + Math.sin(phase + d * 1.3).toFloat * 4f) * ds
      val dRadY = (7f + Math.sin(phase * 0.7 + d * 0.9).toFloat * 2.5f) * ds
      val debX = sx + Math.cos(dAngle).toFloat * dRadX
      val debY = sy + Math.sin(dAngle).toFloat * dRadY
      val dSize = (3.5f + Math.sin(phase * 2 + d * 1.7).toFloat * 1.5f) * ds
      // Debris dark outline
      sb.fillOval(debX, debY, dSize + 1f, dSize * 0.8f + 1f, 0.06f, 0.02f, 0.1f, 0.4f * p, 6)
      // Debris body
      sb.fillOval(debX, debY, dSize, dSize * 0.8f, 0.45f, 0.35f, 0.6f, 0.75f * p, 6)
      // Debris highlight
      sb.fillOval(debX - dSize * 0.2f, debY - dSize * 0.2f, dSize * 0.35f, dSize * 0.3f,
        0.7f, 0.6f, 0.85f, 0.3f * p, 4)
    ; d += 1 }

    // Sparkle stars
    { var i = 0; while (i < 4) {
      val starPhase = ((phase * 0.45 + i * 0.25) % 1.0).toFloat
      val starAngle = phase * 1.2 + i * Math.PI * 2 / 4
      val starDist = (14f + starPhase * 18f) * ds
      val starX = sx + Math.cos(starAngle).toFloat * starDist
      val starY = sy + Math.sin(starAngle).toFloat * starDist * 0.55f
      drawSparkleStar(starX, starY, 5f * (1f - starPhase * 0.5f) * ds,
        0.4f, 0.25f, 0.7f, 0.45f * (1f - starPhase) * p, sb, phase * 2 + i)
    ; i += 1 } }

    drawChargeCrackle(sx, sy, 22f * ds, 0.15f, 0.05f, 0.3f, p, sb, phase, proj.chargeLevel)
    drawReturnGhosts(sx, sy, 22f * ds, dr, dg, db, p, sb, proj)
  }

  /** Data Bolt - digital matrix aesthetic with pixelated structure */
  private def drawDataBolt(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int,
                           isVirus: Boolean = false): Unit = {
    val phase = (tick + proj.id * 31) * 0.4
    val mainR = if (isVirus) 0.85f else 0.1f
    val mainG = if (isVirus) 0.15f else 0.95f
    val mainB = if (isVirus) 0.15f else 0.55f
    computeAllDynamics(proj, mainR, mainG, mainB, phase)
    val p = (0.85 + 0.15 * Math.sin(phase * _stPulseMult)).toFloat * dynAlpha
    val ds = dynScale * 1.5f // this one reads far below the roster's scale unscaled
    val bc = _chgBright
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy

    // Soft halo. This renderer previously drew no glow and no outline at all, so
    // against the roster it read as a faint scatter of dots rather than a projectile.
    sb.fillOvalSoft(sx, sy, 26f * ds * dynGlow, 20f * ds * dynGlow, mainR, mainG, mainB, 0.22f * p, 0f, 14)

    // Core: grid of rectangular "pixels" that shift each frame
    val gridSize = 3
    var gx = -gridSize; while (gx <= gridSize) {
      var gy = -gridSize; while (gy <= gridSize) {
        if (gx * gx + gy * gy <= gridSize * gridSize + 1) {
          val pixelPhase = Math.sin(phase * 3 + gx * 2.7 + gy * 1.9 + proj.id * 0.5).toFloat
          if (pixelPhase > -0.3f) {
            val px = sx + (gx * 4f + Math.sin(phase * 2 + gx + gy).toFloat * 1f) * ds
            val py = sy + (gy * 3f + Math.cos(phase * 1.5 + gx - gy).toFloat * 0.8f) * ds
            val pixAlpha = (0.5f + 0.4f * pixelPhase) * p
            val hw = 1.5f * ds; val hh = 1.2f * ds
            // Dark backing gives each pixel a readable edge against bright terrain
            sb.fillRect(px - hw - 0.6f, py - hh - 0.6f, hw * 2 + 1.2f, hh * 2 + 1.2f,
              outline(mainR), outline(mainG), outline(mainB), pixAlpha * 0.55f)
            sb.fillRect(px - hw, py - hh, hw * 2, hh * 2, mainR, mainG, mainB, pixAlpha)
          }
        }
      ; gy += 1 }
    ; gx += 1 }

    // Scan-line flicker
    val scanY = sy + ((phase * 8 % 16) - 8).toFloat * ds
    sb.fillRect(sx - 10f * ds, scanY - 0.5f, 20f * ds, 1f, mainR, mainG, mainB, 0.3f * p)

    // Virus glitch-distortion: offset copies
    if (isVirus) {
      val glitchOff = Math.sin(phase * 7).toFloat * 4f * ds
      sb.fillRect(sx + glitchOff - 6f * ds, sy - 5f * ds, 12f * ds, 2f * ds, 0.9f, 0.1f, 0.1f, 0.2f * p)
      sb.fillRect(sx - glitchOff - 4f * ds, sy + 3f * ds, 8f * ds, 2f * ds, 0.1f, 0.9f, 0.1f, 0.15f * p)
    }

    // Trail: falling/streaming rectangular particles (matrix rain style)
    val trailLen = 40f * dynTrail
    var i = 0; while (i < 8) {
      val t = ((tick * 0.07 + i * 0.125 + proj.id * 0.13) % 1.0).toFloat
      val trailX = sx - ndx * t * trailLen + Math.sin(phase + i * 2.3).toFloat * 4 * ds
      val trailY = sy - ndy * t * trailLen + t * 12f * ds
      val tw = (2f + (1f - t) * 2f) * ds
      val th = (1.5f + (1f - t) * 2f) * ds
      sb.fillRect(trailX - tw * 0.5f, trailY - th * 0.5f, tw, th,
        mainR, mainG, mainB, 0.4f * (1f - t) * p)
    ; i += 1 }

    // Bright center
    sb.fillOval(sx, sy, 5f * ds, 4f * ds,
      mix(bright(mainR), 1f, bc), mix(bright(mainG), 1f, bc), mix(bright(mainB), 1f, bc), 0.8f * p, 8)
    drawChargeCrackle(sx, sy, 14f * ds, mainR, mainG, mainB, p, sb, phase, proj.chargeLevel)
  }

  /** Holy Bolt - radiant divine star with emanating light rays */
  private def drawHolyBolt(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 29) * 0.3
    computeAllDynamics(proj, 1f, 0.9f, 0.45f, phase)
    val p = (0.85 + 0.15 * Math.sin(phase * _stPulseMult)).toFloat * dynAlpha
    val dr = _evoR; val dg = _evoG; val db = _evoB
    val ds = dynScale
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy

    // Speed lines behind
    drawSpeedLines(sx, sy, ndx, ndy, 1f, 0.9f, 0.5f, 0.25f * p, sb, 5 + (_lifePct * 2).toInt, 30f * ds)

    // Ribbon trail
    drawRibbonTrail(sx, sy, ndx, ndy, 1f, 0.9f, 0.45f, 0.3f * p, sb, tick, proj.id,
      10, 45f * ds * dynTrail, 9f * ds, 1.5f)

    // Bigger glow halo (60px)
    val haloPulse = (0.7f + 0.3f * Math.sin(phase * 2.2).toFloat)
    sb.fillOvalSoft(sx, sy, 43.2f * ds * dynGlow * haloPulse, 34.6f * ds * dynGlow * haloPulse,
      1f, 0.92f, 0.55f, 0.3f * p, 0f, 20)
    sb.fillOvalSoft(sx, sy, 30.2f * ds * dynGlow, 24.5f * ds * dynGlow, 1f, 0.95f, 0.65f, 0.15f * p, 0f, 16)

    // Halo ring around star (divine circle)
    val haloRingR = 26f * ds * (0.9f + 0.1f * Math.sin(phase * 1.5).toFloat)
    sb.strokeOval(sx, sy, haloRingR, haloRingR * 0.65f, 2.5f, 1f, 0.95f, 0.6f, 0.35f * p, 14)
    sb.strokeOval(sx, sy, haloRingR * 0.95f, haloRingR * 0.62f, 1.2f, 1f, 1f, 0.85f, 0.2f * p, 14)

    // 8 radiant light rays with varying lengths
    var ray = 0; while (ray < 8) {
      val rayAngle = phase * 0.5 + ray * Math.PI / 4
      val rayLen = (26f + Math.sin(phase * 2 + ray * 1.7).toFloat * 8f + (ray % 2) * 6f) * ds
      val rx0 = sx + Math.cos(rayAngle).toFloat * 9f * ds
      val ry0 = sy + Math.sin(rayAngle).toFloat * 6f * ds
      val rx1 = sx + Math.cos(rayAngle).toFloat * rayLen
      val ry1 = sy + Math.sin(rayAngle).toFloat * rayLen * 0.6f
      val rayAlpha = (0.25 + 0.15 * Math.sin(phase * 2.5 + ray * 2.1)).toFloat * p
      sb.strokeLineSoft(rx0, ry0, rx1, ry1, 5f * ds, 1f, 0.95f, 0.6f, rayAlpha * 0.5f)
      sb.strokeLine(rx0, ry0, rx1, ry1, 2f * ds, 1f, 1f, 0.8f, rayAlpha)
    ; ray += 1 }

    // Holy cross flash detail (thin cross through center)
    val crossSize = 14f * ds * (0.8f + 0.2f * Math.sin(phase * 3).toFloat)
    val crossAlpha = (0.3 + 0.2 * Math.sin(phase * 4)).toFloat * p
    sb.strokeLine(sx - crossSize, sy, sx + crossSize, sy, 2f * ds, 1f, 1f, 0.9f, crossAlpha)
    sb.strokeLine(sx, sy - crossSize * 0.65f, sx, sy + crossSize * 0.65f, 2f * ds, 1f, 1f, 0.9f, crossAlpha)

    // 6-point star (12 vertices: alternating outer/inner) with dark outline
    val starSpin = phase * 0.3
    val outerR = 20f * ds; val innerR = 9f * ds
    var i = 0; while (i < 12) {
      val a = starSpin + i * Math.PI / 6
      val rad = if (i % 2 == 0) outerR else innerR
      _holyXs(i) = (sx + Math.cos(a).toFloat * rad).toFloat
      _holyYs(i) = (sy + Math.sin(a).toFloat * rad * 0.65f).toFloat
    ; i += 1 }
    // Bold 4px dark outline on star
    sb.strokePolygon(_holyXs, _holyYs, 12, 4f, 0.3f, 0.2f, 0.05f, 0.8f * p)
    // Golden star fill
    sb.fillPolygon(_holyXs, _holyYs, 12, dr, dg, db, 0.88f * p);
    // Bright highlight layer (smaller star)
    { var i = 0; while (i < 12) {
      val a = starSpin + i * Math.PI / 6
      val rad = if (i % 2 == 0) outerR * 0.7f else innerR * 0.8f
      _holyXs(i) = (sx + Math.cos(a).toFloat * rad).toFloat
      _holyYs(i) = (sy + Math.sin(a).toFloat * rad * 0.65f).toFloat
    ; i += 1 } }
    sb.fillPolygon(_holyXs, _holyYs, 12, 1f, 0.98f, 0.75f, 0.5f * p)
    // White-hot center
    val bc = _chgBright
    sb.fillOval(sx, sy, 7f * ds, 5f * ds,
      mix(1f, 1f, bc), mix(1f, 1f, bc), mix(0.9f, 1f, bc), 0.95f * p, 8)
    // Cartoon highlight
    sb.fillOval(sx - 3.5f * ds, sy - 3f * ds, 5f * ds, 3.5f * ds, 1f, 1f, 1f, 0.45f * p, 8)

    // 10 sparkle particles drifting upward
    { var i = 0; while (i < 10) {
      val t = ((tick * 0.05 + i * 0.1 + proj.id * 0.13) % 1.0).toFloat
      val sparkX = sx - ndx * t * 35f * ds + Math.sin(phase + i * 2.3).toFloat * 7f * ds
      val sparkY = sy - ndy * t * 35f * ds - t * 14f * ds
      val sSz = (3f + (1f - t) * 3.5f) * ds
      sb.fillOval(sparkX, sparkY, sSz + 0.5f, sSz * 0.8f + 0.5f, 0.35f, 0.25f, 0.08f, 0.2f * (1f - t) * p, 6)
      sb.fillOval(sparkX, sparkY, sSz, sSz * 0.8f, 1f, 1f, 0.75f, 0.5f * (1f - t) * p, 6)
    ; i += 1 } }

    // Sparkle stars
    { var i = 0; while (i < 4) {
      val starPhase = ((phase * 0.5 + i * 0.25) % 1.0).toFloat
      val starAngle = phase * 1.2 + i * Math.PI * 2 / 4
      val starDist = (16f + starPhase * 18f) * ds
      val starX = sx + Math.cos(starAngle).toFloat * starDist
      val starY = sy + Math.sin(starAngle).toFloat * starDist * 0.55f
      drawSparkleStar(starX, starY, 5f * (1f - starPhase * 0.5f) * ds,
        1f, 0.95f, 0.6f, 0.5f * (1f - starPhase) * p, sb, phase * 2 + i)
    ; i += 1 } }

    drawChargeCrackle(sx, sy, 22f * ds, 1f, 0.9f, 0.45f, p, sb, phase, proj.chargeLevel)
    drawReturnGhosts(sx, sy, 22f * ds, dr, dg, db, p, sb, proj)
  }

  /** Curse - dark spiraling occult energy with orbiting rune-like marks */
  private def drawCurse(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 41) * 0.35
    computeAllDynamics(proj, 0.2f, 0.02f, 0.35f, phase)
    val p = (0.8 + 0.2 * Math.sin(phase * 1.5 * _stPulseMult)).toFloat * dynAlpha
    val dr = _evoR; val dg = _evoG; val db = _evoB
    val ds = dynScale
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy

    // Speed lines behind
    drawSpeedLines(sx, sy, ndx, ndy, 0.15f, 0.03f, 0.2f, 0.2f * p, sb, 5 + (_lifePct * 2).toInt, 28f * ds)

    // Dark smoke ribbon trail behind
    drawRibbonTrail(sx, sy, ndx, ndy, 0.15f, 0.03f, 0.2f, 0.3f * p, sb, tick, proj.id,
      10, 50f * ds * dynTrail, 11f * ds, 2f)

    // Dark mist halo (50px)
    val haloPulse = (0.7f + 0.3f * Math.sin(phase * 1.8).toFloat)
    sb.fillOvalSoft(sx, sy, 36f * ds * dynGlow * haloPulse, 28.8f * ds * dynGlow * haloPulse,
      0.2f, 0.02f, 0.3f, 0.22f * p, 0f, 20)
    sb.fillOvalSoft(sx, sy, 25.2f * ds * dynGlow, 20.2f * ds * dynGlow, 0.15f, 0.01f, 0.25f, 0.12f * p, 0f, 16)

    // 3 expanding dark rings
    var dRing = 0; while (dRing < 3) {
      val drP = ((phase * 0.3 + dRing * 0.33) % 1.0).toFloat
      val drR = (10f + drP * 24f) * ds
      val drA = 0.35f * (1f - drP) * p
      sb.strokeOval(sx, sy, drR, drR * 0.6f, 2f * (1f - drP * 0.4f),
        0.15f, 0.02f, 0.25f, drA, 12)
    ; dRing += 1 }

    // 12 swirling dark particle vortex
    var v = 0; while (v < 12) {
      val vAngle = phase * 2.2 + v * Math.PI * 2 / 12
      val vDist = (12f + Math.sin(phase * 1.5 + v * 1.9).toFloat * 8f) * ds
      val vx = sx + Math.cos(vAngle).toFloat * vDist
      val vy = sy + Math.sin(vAngle).toFloat * vDist * 0.55f
      val vSz = (3f + Math.sin(phase * 3 + v * 2.1).toFloat * 1.5f) * ds
      sb.fillOval(vx, vy, vSz + 0.5f, vSz * 0.7f + 0.5f, 0.04f, 0f, 0.06f, 0.35f * p, 6)
      sb.fillOval(vx, vy, vSz, vSz * 0.7f, 0.2f, 0.04f, 0.35f, 0.5f * p, 6)
    ; v += 1 }

    // Bold 3.5px dark outline on core
    sb.strokeOval(sx, sy, 20f * ds, 15f * ds, 3.5f, 0.03f, 0f, 0.05f, 0.85f * p, 14)
    // Deep purple/black body
    sb.fillOval(sx, sy, 18f * ds, 13.5f * ds, 0.12f, 0.02f, 0.2f, 0.9f * p, 14)
    // Pulsing sinister inner glow
    val glowPulse2 = (0.5 + 0.5 * Math.sin(phase * 3)).toFloat
    sb.fillOval(sx, sy, 12f * ds, 9f * ds, 0.4f * glowPulse2, 0.05f, 0.55f * glowPulse2, 0.65f * p, 12)
    // Dark center
    val bc = _chgBright
    sb.fillOval(sx, sy, 6f * ds, 4.5f * ds,
      mix(0.3f, 1f, bc), mix(0.02f, 1f, bc), mix(0.4f, 1f, bc), 0.92f * p, 8)
    // Cartoon highlight
    sb.fillOval(sx - 3f * ds, sy - 3f * ds, 5f * ds, 3.5f * ds, 0.4f, 0.15f, 0.6f, 0.3f * p, 8)

    // 5 orbiting rune shapes (bigger with outlines)
    var rune = 0; while (rune < 5) {
      val runeSpeed = 1.2 + rune * 0.4
      val runeAngle = phase * runeSpeed + rune * Math.PI * 2 / 5
      val runeDist = (18f + Math.sin(phase + rune * 1.7).toFloat * 4f) * ds
      val rx = sx + Math.cos(runeAngle).toFloat * runeDist
      val ry = sy + Math.sin(runeAngle).toFloat * runeDist * 0.55f
      val runeSpin = phase * 3 + rune * 2.1
      val rs = 5f * ds
      _polyXs4(0) = rx + Math.cos(runeSpin).toFloat * rs
      _polyXs4(1) = rx + Math.cos(runeSpin + Math.PI / 2).toFloat * rs * 0.5f
      _polyXs4(2) = rx + Math.cos(runeSpin + Math.PI).toFloat * rs
      _polyXs4(3) = rx + Math.cos(runeSpin + Math.PI * 1.5).toFloat * rs * 0.5f
      _polyYs4(0) = ry + Math.sin(runeSpin).toFloat * rs * 0.6f
      _polyYs4(1) = ry + Math.sin(runeSpin + Math.PI / 2).toFloat * rs * 0.3f
      _polyYs4(2) = ry + Math.sin(runeSpin + Math.PI).toFloat * rs * 0.6f
      _polyYs4(3) = ry + Math.sin(runeSpin + Math.PI * 1.5).toFloat * rs * 0.3f
      // Rune outline
      sb.strokePolygon(_polyXs4, _polyYs4, 4, 1.5f, 0.08f, 0f, 0.1f, 0.6f * p)
      // Rune body
      sb.fillPolygon(_polyXs4, _polyYs4, 4, 0.5f, 0.15f, 0.7f, 0.7f * p)
      // Rune glow
      sb.fillOvalSoft(rx, ry, rs * 1.5f, rs * 1.2f, 0.4f, 0.1f, 0.6f, 0.15f * p, 0f, 6)
    ; rune += 1 }

    // Prominent skull face with glowing eyes
    val skullAlpha = (0.3 + 0.15 * Math.sin(phase * 2)).toFloat * p
    val skullSc = ds
    // Eye sockets (dark)
    sb.fillOval(sx - 5f * skullSc, sy - 3f * skullSc, 4f * skullSc, 3.5f * skullSc, 0.02f, 0f, 0.04f, skullAlpha, 8)
    sb.fillOval(sx + 5f * skullSc, sy - 3f * skullSc, 4f * skullSc, 3.5f * skullSc, 0.02f, 0f, 0.04f, skullAlpha, 8)
    // Glowing eye dots
    val eyePulse = (0.5f + 0.5f * Math.sin(phase * 4).toFloat)
    sb.fillOval(sx - 5f * skullSc, sy - 3f * skullSc, 2f * skullSc, 1.5f * skullSc,
      0.6f * eyePulse, 0.1f, 0.8f * eyePulse, skullAlpha * 0.8f, 6)
    sb.fillOval(sx + 5f * skullSc, sy - 3f * skullSc, 2f * skullSc, 1.5f * skullSc,
      0.6f * eyePulse, 0.1f, 0.8f * eyePulse, skullAlpha * 0.8f, 6)
    // Mouth (wide grin)
    sb.strokeOval(sx, sy + 4f * skullSc, 5f * skullSc, 3f * skullSc, 1.2f,
      0.05f, 0f, 0.08f, skullAlpha * 0.7f, 8)

    // Sparkle stars (purple)
    { var i = 0; while (i < 4) {
      val starPhase = ((phase * 0.5 + i * 0.25) % 1.0).toFloat
      val starAngle = phase * 1.3 + i * Math.PI * 2 / 4
      val starDist = (14f + starPhase * 18f) * ds
      val starX = sx + Math.cos(starAngle).toFloat * starDist
      val starY = sy + Math.sin(starAngle).toFloat * starDist * 0.55f
      drawSparkleStar(starX, starY, 5f * (1f - starPhase * 0.5f) * ds,
        0.5f, 0.15f, 0.7f, 0.45f * (1f - starPhase) * p, sb, phase * 2 + i)
    ; i += 1 } }

    drawChargeCrackle(sx, sy, 20f * ds, 0.2f, 0.02f, 0.35f, p, sb, phase, proj.chargeLevel)
    drawReturnGhosts(sx, sy, 20f * ds, dr, dg, db, p, sb, proj)
  }

  /** Web Shot — glossy silk web ball with dense mesh, spiral threads, and sticky drip trail */
  private def drawWebShot(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 29) * 0.3
    computeAllDynamics(proj, 0.92f, 0.92f, 0.88f, phase)
    val p = (0.85 + 0.15 * Math.sin(phase * _stPulseMult)).toFloat * dynAlpha
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val ds = 1.3f * dynScale
    val strandCount = 12

    // Speed lines behind
    drawSpeedLines(sx, sy, ndx, ndy, 0.85f, 0.85f, 0.82f, 0.25f * p, sb, 6, 30f * ds)

    // Ribbon trail
    drawRibbonTrail(sx, sy, ndx, ndy, 0.88f, 0.88f, 0.84f, 0.35f * p, sb, tick, proj.id, 8, 40f * ds * dynTrail, 8f * ds, 1f)

    // Huge soft white glow halo (50px radius)
    val haloPulse = (0.7f + 0.3f * Math.sin(phase * 2.2).toFloat)
    sb.fillOvalSoft(sx, sy, 36f * ds * dynGlow * haloPulse, 28.8f * ds * dynGlow * haloPulse, 0.97f, 0.97f, 1f, 0.3f * p, 0f, 20)
    sb.fillOvalSoft(sx, sy, 24.5f * ds * dynGlow, 18.7f * ds * dynGlow, 1f, 1f, 1f, 0.15f * p, 0f, 16)

    // 12 thick radial silk strands with 3-layer rendering
    var strand = 0; while (strand < strandCount) {
      val sAngle = phase * 0.15 + strand * Math.PI * 2 / strandCount
      val sLen = (26f + Math.sin(phase * 1.5 + strand * 1.3).toFloat * 7f) * ds
      val waveOff = Math.sin(phase * 2.5 + strand * 0.9).toFloat * 5f * ds
      val perpAngle = sAngle + Math.PI / 2
      val endX = sx + Math.cos(sAngle).toFloat * sLen + Math.cos(perpAngle).toFloat * waveOff
      val endY = sy + Math.sin(sAngle).toFloat * sLen * 0.6f + Math.sin(perpAngle).toFloat * waveOff * 0.4f
      // 4px dark outline stroke
      sb.strokeLine(sx, sy, endX, endY, 4f * ds, 0.18f, 0.18f, 0.15f, 0.55f * p)
      // 2.5px white silk body
      sb.strokeLine(sx, sy, endX, endY, 2.5f * ds, 0.92f, 0.92f, 0.88f, 0.8f * p)
      // 1px glossy highlight center
      sb.strokeLine(sx, sy, endX, endY, 1f * ds, 1f, 1f, 0.98f, 0.55f * p)
      // Knot node at strand tip with outline
      sb.strokeOval(endX, endY, 3.5f * ds, 3f * ds, 1.5f, 0.2f, 0.2f, 0.18f, 0.4f * p, 6)
      sb.fillOval(endX, endY, 3f * ds, 2.5f * ds, 0.9f, 0.9f, 0.86f, 0.7f * p, 6)
      sb.fillOval(endX - 0.5f * ds, endY - 0.5f * ds, 1.5f * ds, 1.2f * ds, 1f, 1f, 0.98f, 0.35f * p, 4)

      // 3 concentric connecting thread rings between strands
      val nextAngle = phase * 0.15 + ((strand + 1) % strandCount) * Math.PI * 2.0 / strandCount
      val nextLen = (26f + Math.sin(phase * 1.5 + ((strand + 1) % strandCount) * 1.3).toFloat * 7f) * ds
      val nextWave = Math.sin(phase * 2.5 + ((strand + 1) % strandCount) * 0.9).toFloat * 5f * ds
      val nextPerpAngle = nextAngle + Math.PI / 2
      var ring = 0; while (ring < 3) {
        val midT = 0.3f + ring * 0.2f
        val mx0 = sx + Math.cos(sAngle).toFloat * sLen * midT + Math.cos(perpAngle).toFloat * waveOff * midT
        val my0 = sy + Math.sin(sAngle).toFloat * sLen * 0.6f * midT + Math.sin(perpAngle).toFloat * waveOff * 0.4f * midT
        val mx1 = sx + Math.cos(nextAngle).toFloat * nextLen * midT + Math.cos(nextPerpAngle).toFloat * nextWave * midT
        val my1 = sy + Math.sin(nextAngle).toFloat * nextLen * 0.6f * midT + Math.sin(nextPerpAngle).toFloat * nextWave * 0.4f * midT
        // Thread outline
        sb.strokeLine(mx0, my0, mx1, my1, 2f * ds, 0.18f, 0.18f, 0.15f, 0.3f * p)
        // Thread silk
        sb.strokeLine(mx0, my0, mx1, my1, 1f * ds, 0.9f, 0.9f, 0.87f, 0.5f * p)
        // Thread glossy core
        sb.strokeLine(mx0, my0, mx1, my1, 0.4f * ds, 1f, 1f, 0.98f, 0.25f * p)
      ; ring += 1 }
    ; strand += 1 }

    // Bold dark outlined central knot (3.5px outline stroke) with multi-layer fill
    sb.strokeOval(sx, sy, 12f * ds, 9.5f * ds, 3.5f, 0.15f, 0.15f, 0.12f, 0.85f * p, 14)
    sb.fillOval(sx, sy, 11f * ds, 8.5f * ds, 0.88f, 0.88f, 0.84f, 0.95f * p, 14)
    sb.fillOval(sx, sy, 7f * ds, 5.5f * ds, 0.94f, 0.94f, 0.9f, 0.8f * p, 12)
    // Cartoon highlight
    sb.fillOval(sx - 2.5f * ds, sy - 2.5f * ds, 5f * ds, 3.5f * ds, 1f, 1f, 0.98f, 0.5f * p, 8)
    // White-hot center
    sb.fillOval(sx, sy, 4.5f * ds, 3.5f * ds, 1f, 1f, 0.96f, 0.85f * p, 8)
    sb.fillOval(sx, sy, 2f * ds, 1.5f * ds, 1f, 1f, 1f, 0.95f * p, 6)

    // 10 sticky drip particles with stretch strings and highlights
    var i = 0; while (i < 10) {
      val t = ((tick * 0.05 + i * 0.1 + proj.id * 0.13) % 1.0).toFloat
      val dripX = sx - ndx * t * 45f * dynTrail + Math.sin(phase + i * 2.1).toFloat * 6f * ds
      val dripY = sy - ndy * t * 45f * dynTrail + t * t * 20f
      val dripSz = (3.5f + (1f - t) * 4f) * ds
      // Stretch string connecting drip to web — outlined
      val stringAlpha = 0.3f * (1f - t) * p
      val anchorX = sx - ndx * t * 22f; val anchorY = sy - ndy * t * 22f
      sb.strokeLine(anchorX, anchorY, dripX, dripY, 1.2f, 0.2f, 0.2f, 0.18f, stringAlpha * 0.6f)
      sb.strokeLine(anchorX, anchorY, dripX, dripY, 0.6f, 0.88f, 0.88f, 0.85f, stringAlpha)
      // Drip droplet with outline
      sb.strokeOval(dripX, dripY, dripSz + 0.5f, dripSz * 1.5f + 0.5f, 1f, 0.2f, 0.2f, 0.18f, 0.3f * (1f - t) * p, 6)
      sb.fillOval(dripX, dripY, dripSz, dripSz * 1.5f, 0.9f, 0.9f, 0.87f, 0.55f * (1f - t) * p, 6)
      // Glossy highlight on drip
      sb.fillOval(dripX - dripSz * 0.2f, dripY - dripSz * 0.35f, dripSz * 0.4f, dripSz * 0.3f, 1f, 1f, 0.98f, 0.4f * (1f - t) * p, 4)
    ; i += 1 }

    // 6 sparkle stars popping around
    { var i = 0; while (i < 6) {
      val starPhase = ((phase * 0.45 + i * 0.167) % 1.0).toFloat
      val starAngle = phase * 1.3 + i * Math.PI * 2 / 6
      val starDist = (14f + starPhase * 18f) * ds
      val starX = sx + Math.cos(starAngle).toFloat * starDist
      val starY = sy + Math.sin(starAngle).toFloat * starDist * 0.55f
      drawSparkleStar(starX, starY, 5f * (1f - starPhase * 0.5f) * ds,
        1f, 1f, 0.95f, 0.5f * (1f - starPhase) * p, sb, phase * 2 + i)
    ; i += 1 } }

    drawChargeCrackle(sx, sy, 22f * ds, 0.92f, 0.92f, 0.88f, p, sb, phase, proj.chargeLevel)
    drawReturnGhosts(sx, sy, 22f * ds, 0.92f, 0.92f, 0.88f, p, sb, proj)
  }

  /** Venom Bolt - dripping toxic blob with bubbles */
  private def drawVenomBolt(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 37) * 0.35
    computeAllDynamics(proj, 0.3f, 0.8f, 0.18f, phase)
    val p = (0.85 + 0.15 * Math.sin(phase * _stPulseMult)).toFloat * dynAlpha
    val dr = _evoR; val dg = _evoG; val db = _evoB
    val ds = dynScale
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy

    // Speed lines behind
    drawSpeedLines(sx, sy, ndx, ndy, 0.3f, 0.75f, 0.15f, 0.22f * p, sb, 5 + (_lifePct * 2).toInt, 28f * ds)

    // Ribbon trail
    drawRibbonTrail(sx, sy, ndx, ndy, 0.25f, 0.7f, 0.12f, 0.3f * p, sb, tick, proj.id,
      10, 45f * ds * dynTrail, 9f * ds, 1.5f)

    // Glow halo (50px+)
    val haloPulse = (0.7f + 0.3f * Math.sin(phase * 2.2).toFloat)
    sb.fillOvalSoft(sx, sy, 36f * ds * dynGlow * haloPulse, 28.8f * ds * dynGlow * haloPulse,
      0.35f, 0.8f, 0.2f, 0.25f * p, 0f, 20)
    sb.fillOvalSoft(sx, sy, 25.2f * ds * dynGlow, 20.2f * ds * dynGlow, 0.3f, 0.7f, 0.15f, 0.12f * p, 0f, 16)

    // Main body: irregular pulsing blob with bold 3.5px dark outline
    val stretchX = 1f + Math.sin(phase * 2.3).toFloat * 0.12f
    val stretchY = 1f + Math.cos(phase * 1.7).toFloat * 0.1f
    val bodyW = 18f * stretchX * ds; val bodyH = 14f * stretchY * ds
    // Bold dark cartoon outline
    sb.strokeOval(sx, sy, bodyW, bodyH, 3.5f, 0.06f, 0.18f, 0.02f, 0.85f * p, 14)
    // Dark green body
    sb.fillOval(sx, sy, bodyW * 0.95f, bodyH * 0.95f, 0.2f, 0.65f, 0.1f, 0.93f * p, 14)
    // Brighter green mid-layer
    sb.fillOval(sx, sy, bodyW * 0.7f, bodyH * 0.65f, dr, dg, db, 0.88f * p, 12)
    // Bright center
    val bc = _chgBright
    sb.fillOval(sx, sy, bodyW * 0.35f, bodyH * 0.3f,
      mix(0.5f, 1f, bc), mix(0.95f, 1f, bc), mix(0.35f, 1f, bc), 0.9f * p, 10)
    // Cartoon highlight
    sb.fillOval(sx - 3.5f * ds, sy - 3f * ds, 5.5f * ds, 3.5f * ds, 0.6f, 0.95f, 0.45f, 0.4f * p, 8)

    // Surface sheen highlight that shifts position
    val sheenAngle = phase * 1.2
    val sheenX = sx + Math.cos(sheenAngle).toFloat * 5f * ds
    val sheenY = sy + Math.sin(sheenAngle).toFloat * 3f * ds - 2f * ds
    sb.fillOval(sheenX, sheenY, 6f * ds, 3.5f * ds, 0.65f, 0.98f, 0.5f, 0.35f * p, 8)

    // 8 bubbles orbiting/rising with pop animation cycle
    var bub = 0; while (bub < 8) {
      val bubPhase = ((phase * 0.8 + bub * 0.125) % 1.0)
      val bubAngle = phase * 1.5 + bub * Math.PI / 4
      val bubDist = (12f + bubPhase.toFloat * 10f) * ds
      val bx = sx + Math.cos(bubAngle).toFloat * bubDist
      val by = sy + Math.sin(bubAngle).toFloat * bubDist * 0.5f - bubPhase.toFloat * 7f * ds
      val bubSize = (3f + (bub % 3) * 1f) * ds * (if (bubPhase > 0.85) (1f - bubPhase.toFloat) * 6.67f else 1f)
      if (bubSize > 0.3f) {
        // Bubble outline
        sb.strokeOval(bx, by, bubSize + 0.5f, bubSize * 0.85f + 0.5f, 1f, 0.08f, 0.22f, 0.05f, 0.3f * p, 8)
        sb.fillOval(bx, by, bubSize, bubSize * 0.85f, 0.3f, 0.85f, 0.25f, 0.55f * p, 8)
        sb.strokeOval(bx, by, bubSize, bubSize * 0.85f, 0.8f, 0.4f, 0.9f, 0.3f, 0.4f * p, 8)
        // Bubble highlight
        sb.fillOval(bx - bubSize * 0.25f, by - bubSize * 0.25f, bubSize * 0.35f, bubSize * 0.3f,
          0.7f, 0.98f, 0.5f, 0.35f * p, 4)
      }
    ; bub += 1 }

    // 10 toxic drip particles with stretch strings
    var i = 0; while (i < 10) {
      val t = ((tick * 0.06 + i * 0.1 + proj.id * 0.13) % 1.0).toFloat
      val dripX = sx - ndx * t * 38f * ds + Math.sin(phase + i * 2.1).toFloat * 5f * ds
      val dripY = sy - ndy * t * 38f * ds + t * t * 22f * ds
      val dripSize = (3.5f + (1f - t) * 3f) * ds
      // Stretch string connecting drip to blob
      val stringAlpha = 0.25f * (1f - t) * p
      val anchorX = sx - ndx * t * 18f * ds; val anchorY = sy - ndy * t * 18f * ds
      sb.strokeLine(anchorX, anchorY, dripX, dripY, 0.8f, 0.1f, 0.3f, 0.05f, stringAlpha * 0.5f)
      sb.strokeLine(anchorX, anchorY, dripX, dripY, 0.4f, 0.3f, 0.75f, 0.18f, stringAlpha)
      // Drip outline
      sb.fillOval(dripX, dripY, dripSize + 0.5f, dripSize * 1.4f + 0.5f,
        0.06f, 0.2f, 0.03f, 0.25f * (1f - t) * p, 6)
      // Drip body
      sb.fillOval(dripX, dripY, dripSize, dripSize * 1.4f,
        0.25f, 0.75f, 0.15f, 0.5f * (1f - t) * p, 6)
      // Drip highlight
      sb.fillOval(dripX - dripSize * 0.2f, dripY - dripSize * 0.3f, dripSize * 0.35f, dripSize * 0.3f,
        0.55f, 0.95f, 0.4f, 0.3f * (1f - t) * p, 4)
    ; i += 1 }

    // Sparkle stars
    { var i = 0; while (i < 4) {
      val starPhase = ((phase * 0.5 + i * 0.25) % 1.0).toFloat
      val starAngle = phase * 1.2 + i * Math.PI * 2 / 4
      val starDist = (14f + starPhase * 16f) * ds
      val starX = sx + Math.cos(starAngle).toFloat * starDist
      val starY = sy + Math.sin(starAngle).toFloat * starDist * 0.55f
      drawSparkleStar(starX, starY, 4.5f * (1f - starPhase * 0.5f) * ds,
        0.4f, 0.9f, 0.3f, 0.45f * (1f - starPhase) * p, sb, phase * 2 + i)
    ; i += 1 } }

    drawChargeCrackle(sx, sy, 18f * ds, 0.3f, 0.8f, 0.18f, p, sb, phase, proj.chargeLevel)
    drawReturnGhosts(sx, sy, 18f * ds, dr, dg, db, p, sb, proj)
  }

  // ═══════════════════════════════════════════════════════════════
  //  REGISTRY (all 135 types)
  // ═══════════════════════════════════════════════════════════════

  private val registry: Map[Byte, Renderer] = Map(
    // ── Original (0-30) ──
    ProjectileType.NORMAL       -> (drawNormal _),
    ProjectileType.TENTACLE     -> (drawTentacle _),
    ProjectileType.ICE_BEAM     -> beamProj(0.42f, 0.82f, 1f, 6.5f, 12f, BEAM_ICE),
    ProjectileType.AXE          -> bladeSpinner(WPN_AXE, 0.78f, 0.70f, 0.55f, 30f),
    ProjectileType.ROPE         -> chainProj(CHN_ROPE, 0.72f, 0.54f, 0.30f, 6f),
    ProjectileType.SPEAR        -> flyingShaft(SHF_SPEAR, 0.95f, 0.82f, 0.42f, 28f),
    ProjectileType.SOUL_BOLT    -> energyBolt(0.3f, 0.9f, 0.4f, 20f, 3),
    ProjectileType.HAUNT        -> energyBolt(0.5f, 0.7f, 0.95f, 24f, 4),
    ProjectileType.ARCANE_BOLT  -> energyBolt(0.65f, 0.3f, 0.95f, 22f, 2),
    ProjectileType.FIREBALL     -> (drawFireball _),
    ProjectileType.SPLASH       -> aoeRing(AOE_WATER, 0.32f, 0.62f, 1f, 46f),
    ProjectileType.TIDAL_WAVE   -> wave(WAV_WATER, 0.35f, 0.66f, 1f, 40f),
    ProjectileType.GEYSER       -> (drawGeyser _),
    ProjectileType.BULLET       -> bulletProj(0.75f, 0.7f, 0.55f),
    ProjectileType.GRENADE      -> lobbed(LOB_BOMB, 0.95f, 0.62f, 0.25f, 17f),
    ProjectileType.ROCKET       -> (drawRocket _),
    ProjectileType.TALON        -> (drawTalon _),
    ProjectileType.GUST         -> wave(WAV_WIND, 0.82f, 0.92f, 1f, 33f),
    ProjectileType.SHURIKEN     -> spinner(0.72f, 0.74f, 0.82f, 30f, 4),
    ProjectileType.POISON_DART  -> flyingShaft(SHF_DART, 0.4f, 0.9f, 0.35f, 26f),
    ProjectileType.CHAIN_BOLT   -> chainProj(CHN_CHAIN, 0.62f, 0.64f, 0.72f, 5.5f),
    ProjectileType.LOCKDOWN_CHAIN -> chainProj(CHN_CHAIN, 0.46f, 0.48f, 0.56f, 6f),
    ProjectileType.SNARE_MINE   -> lobbed(LOB_MINE, 0.35f, 0.7f, 1f, 17f),
    ProjectileType.KATANA       -> bladeSpinner(WPN_KATANA, 0.78f, 0.84f, 0.95f, 32f),
    ProjectileType.SWORD_WAVE   -> (drawSwordWave _),
    ProjectileType.PLAGUE_BOLT  -> energyBolt(0.45f, 0.75f, 0.15f, 20f, 3),
    ProjectileType.MIASMA       -> aoeRing(AOE_TOXIC, 0.42f, 0.72f, 0.18f, 42f),
    ProjectileType.BLIGHT_BOMB  -> lobbed(LOB_FLASK, 0.45f, 0.8f, 0.2f, 17f),
    ProjectileType.BLOOD_FANG   -> (drawBloodFang _),
    ProjectileType.BLOOD_SIPHON -> beamProj(0.88f, 0.16f, 0.12f, 5.5f, 11f, BEAM_DRAIN),
    ProjectileType.BAT_SWARM    -> (drawBatSwarm _),

    // ── Elemental (31-52) ──
    ProjectileType.FLAME_BOLT   -> energyBolt(1f, 0.5f, 0.1f, 20f, 1),
    ProjectileType.FROST_SHARD  -> flyingShaft(SHF_ICE, 0.55f, 0.85f, 1f, 22f),
    ProjectileType.LIGHTNING    -> lightningBolt(1f, 0.90f, 0.30f),
    ProjectileType.CHAIN_LIGHTNING -> lightningBolt(0.95f, 0.85f, 0.35f),
    ProjectileType.THUNDER_STRIKE -> (drawThunderStrike _),
    ProjectileType.BOULDER      -> ((proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int) => drawBoulder(proj, sx, sy, sb, tick, 24f)),
    ProjectileType.SEISMIC_SLAM -> aoeRing(AOE_QUAKE, 0.68f, 0.50f, 0.28f, 50f),
    ProjectileType.WIND_BLADE   -> wave(WAV_WIND, 0.78f, 0.96f, 1f, 31f),
    ProjectileType.MAGMA_BALL   -> energyBolt(1f, 0.45f, 0.05f, 24f, 1),
    ProjectileType.ERUPTION     -> aoeRing(AOE_FIRE, 1f, 0.45f, 0.05f, 46f),
    ProjectileType.FROST_TRAP   -> lobbed(LOB_ICE, 0.62f, 0.85f, 1f, 17f),
    ProjectileType.SAND_SHOT    -> energyBolt(0.9f, 0.75f, 0.4f, 18f),
    ProjectileType.SAND_BLAST   -> wave(WAV_SAND, 0.88f, 0.76f, 0.42f, 30f),
    ProjectileType.THORN        -> flyingShaft(SHF_THORN, 0.42f, 0.75f, 0.28f, 26f),
    ProjectileType.VINE_WHIP    -> beamProj(0.24f, 0.66f, 0.18f, 5.5f, 10f, BEAM_VINE),
    ProjectileType.THORN_WALL   -> aoeRing(AOE_NATURE, 0.30f, 0.62f, 0.18f, 34f),
    ProjectileType.INFERNO_BLAST -> (drawInfernoBlast _),
    ProjectileType.GLACIER_SPIKE -> flyingShaft(SHF_ICE, 0.60f, 0.88f, 1f, 27f),
    ProjectileType.MUD_GLOB     -> energyBolt(0.4f, 0.3f, 0.15f, 22f),
    ProjectileType.MUD_BOMB     -> lobbed(LOB_GLOB, 0.40f, 0.30f, 0.14f, 18f),
    ProjectileType.EMBER_SHOT   -> energyBolt(1f, 0.55f, 0.15f, 18f, 1),
    ProjectileType.AVALANCHE_CRUSH -> lobbed(LOB_ICE, 0.72f, 0.85f, 0.98f, 40f),

    // ── Undead/Dark (53-69) ──
    ProjectileType.DEATH_BOLT   -> energyBolt(0.2f, 0.5f, 0.05f, 22f, 3),
    ProjectileType.RAISE_DEAD   -> (drawRaiseDead _),
    ProjectileType.BONE_AXE     -> bladeSpinner(WPN_BONE_AXE, 0.94f, 0.92f, 0.84f, 30f),
    ProjectileType.BONE_THROW   -> bladeSpinner(WPN_BONE, 0.94f, 0.92f, 0.84f, 24f, 0.38),
    ProjectileType.WAIL         -> (drawWail _),
    ProjectileType.SOUL_DRAIN   -> beamProj(0.38f, 0.88f, 0.28f, 5.5f, 11.5f, BEAM_DRAIN),
    ProjectileType.CLAW_SWIPE   -> (drawClawSwipe _),
    ProjectileType.DEVOUR       -> (drawDevour _),
    ProjectileType.SCYTHE       -> (drawScythe _),
    ProjectileType.REAP         -> (drawReap _),
    ProjectileType.SHADOW_BOLT  -> (drawShadowBolt _),
    ProjectileType.CURSED_BLADE -> bladeSpinner(WPN_CURSED, 0.90f, 0.18f, 0.28f, 30f),
    ProjectileType.LIFE_DRAIN   -> beamProj(0.88f, 0.12f, 0.12f, 5.5f, 11f, BEAM_DRAIN),
    ProjectileType.SHOVEL       -> lobbed(LOB_SHOVEL, 0.62f, 0.64f, 0.62f, 26f),
    ProjectileType.HEAD_THROW   -> lobbed(LOB_HORN, 0.86f, 0.80f, 0.68f, 22f),
    ProjectileType.BANDAGE_WHIP -> beamProj(0.88f, 0.80f, 0.64f, 5.5f, 9f, BEAM_WHIP),
    ProjectileType.CURSE        -> (drawCurse _),

    // ── Medieval/Fantasy (70-79) ──
    ProjectileType.HOLY_BLADE   -> bladeSpinner(WPN_SWORD, 1f, 0.92f, 0.45f, 31f),
    ProjectileType.HOLY_BOLT    -> (drawHolyBolt _),
    ProjectileType.ARROW        -> flyingShaft(SHF_ARROW, 0.95f, 0.72f, 0.32f, 25f),
    ProjectileType.POISON_ARROW -> flyingShaft(SHF_PARROW, 0.4f, 0.9f, 0.32f, 25f),
    ProjectileType.SONIC_WAVE   -> wave(WAV_SONIC, 0.78f, 0.58f, 0.97f, 33f),
    ProjectileType.SONIC_BOOM   -> aoeRing(AOE_SONIC, 0.78f, 0.58f, 0.97f, 50f),
    ProjectileType.FIST         -> fistProj(0.75f, 0.6f, 0.45f, 28f),
    ProjectileType.SMITE        -> (drawHolyBolt _),
    ProjectileType.CHARM        -> energyBolt(1f, 0.45f, 0.65f, 18f, 4),
    ProjectileType.CARD         -> bladeSpinner(WPN_CARD, 0.92f, 0.25f, 0.32f, 26f, 0.22),

    // ── Sci-Fi/Tech (80-89) ──
    ProjectileType.DATA_BOLT    -> ((proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int) => drawDataBolt(proj, sx, sy, sb, tick, isVirus = false)),
    ProjectileType.VIRUS        -> ((proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int) => drawDataBolt(proj, sx, sy, sb, tick, isVirus = true)),
    ProjectileType.LASER        -> beamProj(1f, 0.25f, 0.2f, 7.5f, 9f, BEAM_LASER),
    ProjectileType.GRAVITY_BALL -> (drawGravityBall _),
    ProjectileType.GRAVITY_WELL -> (drawGravityBall _),
    ProjectileType.TESLA_COIL   -> lightningBolt(0.35f, 0.85f, 1f),
    ProjectileType.NANO_BOLT    -> energyBolt(0.25f, 0.85f, 0.65f, 16f, 4),
    ProjectileType.VOID_BOLT    -> (drawVoidBolt _),
    ProjectileType.RAILGUN      -> beamProj(0.25f, 0.55f, 1f, 9f, 8.5f, BEAM_RAILGUN),
    ProjectileType.CLUSTER_BOMB -> lobbed(LOB_BOMB, 0.9f, 0.66f, 0.3f, 19f),

    // ── Nature/Beast (90-93) ──
    ProjectileType.VENOM_BOLT   -> (drawVenomBolt _),
    ProjectileType.WEB_SHOT     -> (drawWebShot _),
    ProjectileType.STINGER      -> (drawStinger _),
    ProjectileType.ACID_BOMB    -> (drawVenomBolt _),

    // ── AoE Root (94-101) ──
    ProjectileType.SEISMIC_ROOT -> aoeRing(AOE_QUAKE, 0.62f, 0.48f, 0.28f, 38f),
    ProjectileType.ROOT_GROWTH  -> aoeRing(AOE_NATURE, 0.30f, 0.66f, 0.18f, 36f),
    ProjectileType.WEB_TRAP     -> (drawWebShot _),
    ProjectileType.TREMOR_SLAM  -> aoeRing(AOE_QUAKE, 0.62f, 0.48f, 0.28f, 46f),
    ProjectileType.ENTANGLE     -> aoeRing(AOE_NATURE, 0.26f, 0.60f, 0.18f, 36f),
    ProjectileType.STONE_GAZE   -> beamProj(0.68f, 0.63f, 0.52f, 6.5f, 12f, BEAM_STONE),
    ProjectileType.INK_SNARE    -> lobbed(LOB_GLOB, 0.16f, 0.14f, 0.24f, 19f),
    ProjectileType.GRAVITY_LOCK -> (drawGravityBall _),

    // ── Character-specific (102-111) ──
    ProjectileType.KNIFE        -> bladeSpinner(WPN_KNIFE, 0.84f, 0.86f, 0.92f, 25f, 0.36),
    ProjectileType.STING        -> energyBolt(0.45f, 0.9f, 1f, 16f),
    ProjectileType.HAMMER       -> lobbed(LOB_HAMMER, 0.60f, 0.62f, 0.70f, 27f),
    ProjectileType.HORN         -> (drawHorn _),
    ProjectileType.MYSTIC_BOLT  -> energyBolt(0.55f, 0.35f, 0.85f, 20f, 2),
    ProjectileType.PETRIFY      -> beamProj(0.68f, 0.63f, 0.52f, 6.5f, 12f, BEAM_STONE),
    ProjectileType.GRAB         -> beamProj(0.55f, 0.44f, 0.28f, 7f, 11f, BEAM_WHIP),
    ProjectileType.JAW          -> (drawJaw _),
    ProjectileType.TONGUE       -> beamProj(0.92f, 0.38f, 0.42f, 7f, 10f, BEAM_WHIP),
    ProjectileType.ACID_FLASK   -> (drawVenomBolt _),

    // ── Roster audit: new differentiation projectiles (112+) ──
    ProjectileType.BOOMERANG_BLADE -> bladeSpinner(WPN_CURSED, 0.90f, 0.18f, 0.28f, 30f),
    ProjectileType.VORTEX_BOMB     -> aoeRing(AOE_VOID, 0.55f, 0.25f, 0.85f, 42f),
    ProjectileType.CHAIN_LIGHTNING_FORK -> lightningBolt(1f, 0.92f, 0.42f),
    ProjectileType.SNIPER_BEAM     -> beamProj(0.3f, 0.6f, 1f, 9f, 8f, BEAM_RAILGUN),
    ProjectileType.MOMENTUM_STRIKE -> wave(WAV_IMPACT, 0.97f, 0.62f, 0.22f, 30f),
    ProjectileType.LEECH_BOLT      -> energyBolt(0.5f, 0.15f, 0.35f, 22f, 3),
    ProjectileType.RICOCHET_SHARD  -> flyingShaft(SHF_ICE, 0.62f, 0.90f, 1f, 21f),
    ProjectileType.FLAME_WAVE      -> wave(WAV_FLAME, 1f, 0.5f, 0.1f, 35f),
    ProjectileType.POISON_CLOUD    -> aoeRing(AOE_TOXIC, 0.36f, 0.80f, 0.24f, 46f),
    ProjectileType.BONE_BOOMERANG  -> bladeSpinner(WPN_BONE, 0.94f, 0.92f, 0.84f, 24f, 0.34),
    ProjectileType.GRAVITY_LANCE   -> beamProj(0.45f, 0.20f, 0.75f, 7f, 10f, BEAM_GRAVITY),
    ProjectileType.SHADOW_HAUNT    -> (drawVoidBolt _),
    ProjectileType.CHARGE_FIST     -> fistProj(0.85f, 0.65f, 0.35f, 28f),
    ProjectileType.ACID_SPRAY      -> wave(WAV_ACID, 0.35f, 0.9f, 0.32f, 29f),
    ProjectileType.ECHO_BOLT       -> energyBolt(0.7f, 0.5f, 0.95f, 20f, 2),
    ProjectileType.FLAME_TRAIL     -> energyBolt(1f, 0.6f, 0.15f, 22f, 1),
    ProjectileType.STAR_BOLT       -> energyBolt(0.95f, 0.9f, 0.45f, 22f, 4),
    ProjectileType.RUNE_BOLT       -> energyBolt(0.9f, 0.75f, 0.35f, 18f, 2),
    ProjectileType.SOUL_HARVEST    -> aoeRing(AOE_VOID, 0.34f, 0.90f, 0.40f, 42f),
    ProjectileType.OVERCLOCK_BEAM  -> aoeRing(AOE_SONIC, 0.22f, 0.95f, 0.75f, 40f),
    ProjectileType.NAPALM_STRIKE   -> lobbed(LOB_BOMB, 1f, 0.5f, 0.12f, 20f),
    ProjectileType.THROWN_BOULDER  -> ((proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int) => drawBoulder(proj, sx, sy, sb, tick, 28f)),
    ProjectileType.EYE_BEAM        -> beamProj(0.9f, 0.25f, 0.2f, 7.5f, 10f, BEAM_LASER),

    // ── DPS balance variants (same visual as base type) ──
    ProjectileType.SOUL_BOLT_HEAVY   -> energyBolt(0.3f, 0.9f, 0.4f, 20f, 3),
    ProjectileType.FROST_SHARD_LIGHT -> flyingShaft(SHF_ICE, 0.55f, 0.85f, 1f, 20f),
    ProjectileType.FLAME_BOLT_HEAVY  -> energyBolt(1f, 0.5f, 0.1f, 20f, 1),
    ProjectileType.FLAME_BOLT_LIGHT  -> energyBolt(1f, 0.5f, 0.1f, 20f, 1),
    ProjectileType.BULLET_HEAVY      -> bulletProj(0.75f, 0.7f, 0.55f),
    ProjectileType.BULLET_LIGHT      -> bulletProj(0.75f, 0.7f, 0.55f),
    ProjectileType.SONIC_WAVE_HEAVY  -> wave(WAV_SONIC, 0.78f, 0.58f, 0.97f, 35f),
    ProjectileType.SONIC_WAVE_MED    -> wave(WAV_SONIC, 0.78f, 0.58f, 0.97f, 33f),
    ProjectileType.THORN_LIGHT       -> flyingShaft(SHF_THORN, 0.42f, 0.75f, 0.28f, 24f),
    ProjectileType.LASER_HEAVY       -> beamProj(1f, 0.25f, 0.2f, 7.5f, 9f, BEAM_LASER),
    ProjectileType.LASER_LIGHT       -> beamProj(1f, 0.25f, 0.2f, 7.5f, 9f, BEAM_LASER),
    ProjectileType.ARROW_HEAVY       -> flyingShaft(SHF_ARROW, 0.95f, 0.72f, 0.32f, 27f),
    ProjectileType.ARROW_LIGHT       -> flyingShaft(SHF_ARROW, 0.95f, 0.72f, 0.32f, 24f),
    ProjectileType.HOLY_BOLT_HEAVY   -> (drawHolyBolt _),
    ProjectileType.VENOM_BOLT_LIGHT  -> (drawVenomBolt _)
  )

  // Flat lookup table for O(1) renderer access without Option allocation.
  // Populated from registry at init time. Null entries = no renderer (use drawGeneric).
  private val _rendererLUT: Array[Renderer] = {
    val lut = new Array[Renderer](256)
    val iter = registry.iterator
    while (iter.hasNext) {
      val (pType, renderer) = iter.next()
      lut(pType & 0xFF) = renderer
    }
    lut
  }

  // ═══════════════════════════════════════════════════════════════
  //  SPECIALIZED RENDERERS
  // ═══════════════════════════════════════════════════════════════

  /** Normal — CARTOONISH: bold beam with outline, big tip orb, sparkles */
  private def drawNormal(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    beamTip(sx, sy, proj, 7f)
    val tipX = _tipX; val tipY = _tipY
    val phase = (tick + proj.id * 37) * 0.3
    intToRGB(proj.colorRGB)
    val r = _r; val g = _g; val b = _b
    computeAllDynamics(proj, r, g, b, phase)
    val p = (0.65 + 0.35 * Math.sin(phase * _stPulseMult)).toFloat * dynAlpha
    val ds = dynScale
    val cr = mix(bright(r), 1f, _chgBright); val cg = mix(bright(g), 1f, _chgBright)
    val cb = mix(bright(b), 1f, _chgBright)
    val pId = proj.id.toDouble

    val dx = tipX - sx; val dy = tipY - sy
    val beamLen = Math.sqrt(dx * dx + dy * dy).toFloat
    val nx = if (beamLen > 1) dx / beamLen else 1f
    val ny = if (beamLen > 1) dy / beamLen else 0f
    val perpX = -ny; val perpY = nx

    // Multi-segment plasma path with subtle wobble — breaks the straight-line look
    val segs = 8
    var seg = 0; while (seg < segs) {
      val t0 = seg.toFloat / segs
      val t1 = (seg + 1).toFloat / segs
      val w0 = 13f * ds * (1f - t0 * 0.45f)
      val w1 = 13f * ds * (1f - t1 * 0.45f)
      val wAvg = (w0 + w1) * 0.5f
      // Slow organic waver + faster shimmer
      val off0 = (Math.sin(phase * 1.3 + t0 * 4.0 + pId * 0.4).toFloat * 6f +
                  Math.sin(phase * 3.7 + t0 * 9.0 + pId * 0.2).toFloat * 2.5f)
      val off1 = (Math.sin(phase * 1.3 + t1 * 4.0 + pId * 0.4).toFloat * 6f +
                  Math.sin(phase * 3.7 + t1 * 9.0 + pId * 0.2).toFloat * 2.5f)
      val x0 = sx + dx * t0 + perpX * off0
      val y0 = sy + dy * t0 + perpY * off0
      val x1 = sx + dx * t1 + perpX * off1
      val y1 = sy + dy * t1 + perpY * off1
      // Outer glow halo
      sb.strokeLineSoft(x0, y0, x1, y1, wAvg * 3.2f, r, g, b, 0.22f * p)
      // Dark outline
      sb.strokeLine(x0, y0, x1, y1, wAvg * 1.25f, outline(r), outline(g), outline(b), 0.55f * p)
      // Beam body
      sb.strokeLine(x0, y0, x1, y1, wAvg, r, g, b, 0.78f * p)
      // White-hot core
      sb.strokeLine(x0, y0, x1, y1, wAvg * 0.38f, cr, cg, cb, 0.96f * p)
    ; seg += 1 }

    // 3 plasma pulse waves — fat bulges traveling tip-ward along beam
    { var pw = 0; while (pw < 3) {
      val pt = ((tick * 0.07 + pw * 0.333 + pId * 0.11) % 1.0).toFloat
      val woff = (Math.sin(phase * 1.3 + pt * 4.0 + pId * 0.4).toFloat * 6f +
                  Math.sin(phase * 3.7 + pt * 9.0 + pId * 0.2).toFloat * 2.5f)
      val px = sx + dx * pt + perpX * woff
      val py = sy + dy * pt + perpY * woff
      val sw = (12f + (1f - pt) * 6f) * (0.7f + 0.3f * Math.sin(phase * 4 + pw * 1.5).toFloat)
      sb.fillOvalSoft(px, py, sw * 1.3f, sw, r, g, b, 0.45f * (1f - pt * 0.4f) * p, 0f, 12)
      sb.fillOval(px, py, sw * 0.55f, sw * 0.45f, cr, cg, cb, 0.85f * p, 10)
      sb.fillOval(px, py, sw * 0.22f, sw * 0.18f, 1f, 1f, 1f, 0.7f * p, 6)
    ; pw += 1 } }

    // Perpendicular electric crackles branching off the beam
    { var cr2 = 0; while (cr2 < 5) {
      val ct = ((tick * 0.05 + cr2 * 0.2 + pId * 0.17) % 1.0).toFloat
      val side = if (cr2 % 2 == 0) 1f else -1f
      val woff = (Math.sin(phase * 1.3 + ct * 4.0 + pId * 0.4).toFloat * 6f)
      val originX = sx + dx * ct + perpX * woff
      val originY = sy + dy * ct + perpY * woff
      val crackLen = 22f + 8f * Math.sin(phase * 5 + cr2 * 2.1).toFloat
      val jitter1 = Math.sin(phase * 9 + cr2 * 3.7).toFloat * 6f
      val jitter2 = Math.sin(phase * 7 + cr2 * 4.3).toFloat * 8f
      val midX = originX + perpX * side * crackLen * 0.45f + nx * jitter1 * 0.3f
      val midY = originY + perpY * side * crackLen * 0.45f + ny * jitter1 * 0.3f
      val endX = originX + perpX * side * crackLen + nx * jitter2 * 0.4f
      val endY = originY + perpY * side * crackLen + ny * jitter2 * 0.4f
      val alpha = (0.4f + 0.4f * Math.sin(phase * 6 + cr2 * 1.7).toFloat) * p
      sb.strokeLine(originX, originY, midX, midY, 2.5f, cr, cg, cb, alpha)
      sb.strokeLine(midX, midY, endX, endY, 1.5f, 1f, 1f, 1f, alpha * 0.7f)
      sb.fillOval(endX, endY, 2.5f, 2f, 1f, 1f, 1f, alpha * 0.8f, 6)
    ; cr2 += 1 } }

    // Tip orb — cartoon outline and ring chase. Radius rides its own gentle
    // oscillator rather than the alpha pulse, which used to shrink the orb to a
    // third of its size exactly as it dimmed.
    val orbR = 17f * ds * (0.88f + 0.12f * Math.sin(phase * 1.3).toFloat)
    sb.fillOvalSoft(tipX, tipY, orbR * 2.8f * dynGlow, orbR * 2f * dynGlow, r, g, b, 0.4f * p, 0f, 16)
    sb.strokeOval(tipX, tipY, orbR * 1.15f, orbR * 0.85f, outlineW(orbR), outline(r), outline(g), outline(b), 0.75f * p, 12)
    sb.fillOval(tipX, tipY, orbR, orbR * 0.72f, r, g, b, 0.85f * p, 14)
    sb.fillOval(tipX, tipY, orbR * 0.45f, orbR * 0.32f, cr, cg, cb, 0.95f * p, 10)
    sb.fillOval(tipX + KEY_LIGHT_X * orbR * 0.34f, tipY + KEY_LIGHT_Y * orbR * 0.2f, orbR * 0.28f, orbR * 0.2f,
      1f, 1f, 1f, 0.5f * p, 6)

    // 4 orbiting sparks around tip
    { var orb = 0; while (orb < 4) {
      val oa = phase * 2.5 + orb * Math.PI * 0.5
      val od = orbR * 1.5f
      val ox = (tipX + Math.cos(oa).toFloat * od)
      val oy = (tipY + Math.sin(oa).toFloat * od * 0.55f)
      sb.fillOval(ox, oy, 3.5f, 2.8f, cr, cg, cb, 0.75f * p, 6)
    ; orb += 1 } }

    // Expanding rings at tip — 3 rings
    var ring = 0; while (ring < 3) {
      val rp = ((tick * 0.1 + ring * 0.33 + pId * 0.17) % 1.0).toFloat
      val ringR = 8f + rp * 38f
      sb.strokeOval(tipX, tipY, ringR, ringR * 0.6f, 2.5f * (1f - rp), r, g, b, 0.45f * (1f - rp) * p, 12)
    ; ring += 1 }

    // Sparkle star at tip
    drawSparkleStar(tipX, tipY, 8f * ds, cr, cg, cb, 0.4f * p, sb, phase * 1.5)
    drawChargeCrackle(sx, sy, 18f * ds, r, g, b, p, sb, phase, proj.chargeLevel)
    drawReturnGhosts(tipX, tipY, orbR, r, g, b, p, sb, proj)
  }

  /** Tentacle - thick wavy tendrils with suckers */
  private def drawTentacle(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    beamTip(sx, sy, proj, 5f)
    val tipX = _tipX; val tipY = _tipY
    val phase = (tick + proj.id * 23) * 0.4
    computeAllDynamics(proj, 0.2f, 0.8f, 0.35f, phase)
    val p = (0.9 + 0.1 * Math.sin(phase * _stPulseMult)).toFloat * dynAlpha
    val ds = dynScale
    val dx = tipX - sx; val dy = tipY - sy
    val len = Math.sqrt(dx * dx + dy * dy).toFloat
    if (len < 1) return
    val nx = -dy / len; val ny = dx / len

    var strand = 0; while (strand < 4) {
      val off = (strand - 1.5f) * 4f * ds
      val segs = 12
      var i = 0; while (i < segs) {
        val t0 = i.toFloat / segs; val t1 = (i + 1).toFloat / segs
        val w0 = (Math.sin(phase * 2 + t0 * Math.PI * 3.5 + strand * 1.6) * (7f + strand * 2.5f) * ds * (1f - t0 * 0.3f)).toFloat
        val w1 = (Math.sin(phase * 2 + t1 * Math.PI * 3.5 + strand * 1.6) * (7f + strand * 2.5f) * ds * (1f - t1 * 0.3f)).toFloat
        val x0 = sx + dx * t0 + nx * (off * 0.3f + w0); val y0 = sy + dy * t0 + ny * (off * 0.3f + w0)
        val x1 = sx + dx * t1 + nx * (off * 0.3f + w1); val y1 = sy + dy * t1 + ny * (off * 0.3f + w1)
        // Thick solid tentacle
        sb.strokeLine(x0, y0, x1, y1, 7f * ds, 0.12f, 0.55f, 0.2f, 0.8f * p)
        sb.strokeLine(x0, y0, x1, y1, 3f * ds, 0.2f, 0.8f, 0.35f, 0.65f * p)
        sb.strokeLine(x0, y0, x1, y1, 1.2f * ds, 0.35f, 0.95f, 0.5f, 0.5f * p)
      ; i += 1 }
      // Suckers
      var s = 0; while (s < 3) {
        val t = 0.2f + s * 0.25f
        val w = (Math.sin(phase * 2 + t * Math.PI * 3.5 + strand * 1.6) * (7f + strand * 2.5f) * ds * (1f - t * 0.3f)).toFloat
        val spx = sx + dx * t + nx * (off * 0.3f + w)
        val spy = sy + dy * t + ny * (off * 0.3f + w)
        val sp = (0.6 + 0.4 * Math.sin(phase * 3 + strand * 2 + s * 1.7)).toFloat
        sb.fillOval(spx, spy, 4f * sp * ds, 3.5f * sp * ds, 0.5f, 0.15f, 0.7f, 0.6f * sp * p, 8)
      ; s += 1 }
    ; strand += 1 }
    // Dripping
    { var i = 0; while (i < 4) {
      val t = ((tick * 0.04 + i * 0.25 + proj.id * 0.11) % 1.0).toFloat
      val dripX = sx + dx * (0.15f + i * 0.18f) + nx * Math.sin(phase + i * 2.3).toFloat * 7f * ds
      val dripY = sy + dy * (0.15f + i * 0.18f) + ny * Math.sin(phase + i * 2.3).toFloat * 7f * ds + t * 14f * ds
      sb.fillOval(dripX, dripY, 3.5f * ds, 3f * ds, 0.25f, 0.85f, 0.35f, 0.5f * (1f - t) * p, 6)
    ; i += 1 } }
  }

  /** Fireball — CARTOONISH: huge fire orb with bold outline, big tongues, dense embers */
  private def drawFireball(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 29) * 0.35
    computeAllDynamics(proj, 1f, 0.4f, 0.02f, phase)
    val p = (0.6 + 0.4 * Math.sin(phase * _stPulseMult)).toFloat * dynAlpha
    val ds = dynScale
    val bc = _chgBright
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy

    // Speed lines
    drawSpeedLines(sx, sy, ndx, ndy, 1f, 0.4f, 0f, 0.3f * p, sb, 4, 30f * ds)

    // Outer heat halo with dramatic pulse
    val haloPulse = (0.7 + 0.3 * Math.sin(phase * 2.5)).toFloat
    sb.fillOvalSoft(sx, sy, 41.8f * ds * dynGlow * haloPulse, 32.4f * ds * dynGlow * haloPulse,
      1f, 0.3f, 0f, 0.32f * p, 0f, 20)

    // Heat shimmer above — bigger
    var shim = 0; while (shim < 4) {
      val shimY = sy - (18f + shim * 10f) * ds
      val shimX = sx + Math.sin(phase * 2 + shim * 1.5).toFloat * 8f * ds
      val shimR = (10f + shim * 5f) * ds
      sb.fillOvalSoft(shimX, shimY, shimR, shimR * 0.7f, 1f, 0.5f, 0.1f, 0.08f * p, 0f, 10)
    ; shim += 1 }

    // 10 fire tongue licks — bigger and wilder
    var i = 0; while (i < 10) {
      val fa = phase * 2 + i * Math.PI * 2 / 10
      val fl = (18f + Math.sin(phase * 3 + i * 2.1).toFloat * 10f) * ds
      val fx = sx + Math.cos(fa).toFloat * 16f * ds
      val fy = sy + Math.sin(fa).toFloat * 9f * ds - fl * 0.5f
      // Flame tongue outline
      sb.fillOval(fx, fy, 8f * ds, fl * 0.4f, 0.15f, 0.02f, 0f, 0.5f * p, 8)
      sb.fillOval(fx, fy, 6f * ds, fl * 0.35f, 1f, 0.45f, 0.02f, 0.6f * p, 8)
    ; i += 1 }

    // Bold dark cartoon outline on fire body
    sb.strokeOval(sx, sy, 30f * ds, 22f * ds, outlineW(30f * ds), 0.15f, 0.02f, 0f, 0.8f * p, 16)
    // Fire body — bigger
    sb.fillOval(sx, sy, 28f * ds, 21f * ds, 0.9f, 0.3f, 0.02f, 0.95f * p, 16)
    sb.fillOval(sx, sy, 20f * ds, 14f * ds, 1f, 0.55f, 0.05f, 0.95f * p, 14)
    sb.fillOval(sx, sy, 12f * ds, 8f * ds, 1f, mix(0.8f, 1f, bc), mix(0.3f, 1f, bc), 0.98f * p, 10)
    // Cartoon highlight
    sb.fillOval(sx + KEY_LIGHT_X * 8f * ds, sy + KEY_LIGHT_Y * 6.4f * ds, 8f * ds, 5f * ds, 1f, 1f, 0.8f, 0.45f * p, 8)
    // White-hot center
    sb.fillOval(sx, sy, 5f * ds, 4f * ds, 1f, 1f, mix(0.9f, 1f, bc), 0.95f * p, 8)

    // Dense ember trail — 14 embers
    { var i = 0; while (i < 14) {
      val t = ((tick * 0.07 + i * 0.071 + proj.id * 0.13) % 1.0).toFloat
      val spread = Math.sin(phase + i * 2.3).toFloat * 12f * ds
      val trailLen = 45f * dynTrail
      val fx = sx - ndx * t * trailLen + spread
      val fy = sy - ndy * t * trailLen - t * 14f * ds
      val s = (5f + (1f - t) * 7f) * ds
      val colorPhase = (i * 0.31f + proj.id * 0.13f) % 1.0f
      val eR = 1f
      val eG = if (colorPhase < 0.25f) 1f else if (colorPhase < 0.5f) 0.85f else if (colorPhase < 0.75f) 0.45f else 0.12f
      val eB = if (colorPhase < 0.25f) 0.85f else if (colorPhase < 0.5f) 0.3f else 0f
      sb.fillOval(fx, fy, s, s * 0.7f, eR, eG * (1f - t * 0.5f), eB, 0.6f * (1f - t) * p, 8)
    ; i += 1 } }

    // Sparkle stars around fireball
    { var i = 0; while (i < 3) {
      val starPhase = ((phase * 0.5 + i * 0.33) % 1.0).toFloat
      val starAngle = phase * 1.2 + i * Math.PI * 2 / 3
      val starDist = (20f + starPhase * 16f) * ds
      val starX = sx + Math.cos(starAngle).toFloat * starDist
      val starY = sy + Math.sin(starAngle).toFloat * starDist * 0.55f
      drawSparkleStar(starX, starY, 5f * (1f - starPhase * 0.5f) * ds,
        1f, 0.9f, 0.4f, 0.5f * (1f - starPhase) * p, sb, phase * 2 + i)
    ; i += 1 } }

    drawChargeCrackle(sx, sy, 26f * ds, 1f, 0.4f, 0.02f, p, sb, phase, proj.chargeLevel)
    drawReturnGhosts(sx, sy, 26f * ds, 0.95f, 0.35f, 0.02f, p, sb, proj)
  }

  /** Rocket — CARTOONISH: bold outline, big exhaust, cartoon smoke puffs */
  private def drawRocket(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    beamTip(sx, sy, proj, 5f)
    val tipX = _tipX; val tipY = _tipY
    val phase = (tick + proj.id * 29) * 0.35
    computeAllDynamics(proj, 0.45f, 0.47f, 0.35f, phase)
    val p = (0.7 + 0.3 * Math.sin(phase * _stPulseMult)).toFloat * dynAlpha
    val ds = dynScale
    val dx = tipX - sx; val dy = tipY - sy
    val len = Math.sqrt(dx * dx + dy * dy).toFloat
    if (len < 1) return
    val nx = dx / len; val ny = dy / len
    val perpX = -ny; val perpY = nx

    // Big exhaust glow
    sb.fillOvalSoft(sx - dx * 0.2f, sy - dy * 0.2f, 25.9f * ds * dynGlow, 20.2f * ds * dynGlow, 1f, 0.5f, 0.1f, 0.4f * p, 0f, 14)

    // Exhaust flames — bigger and more dramatic
    var i = 0; while (i < 10) {
      val t = ((tick * 0.09 + i * 0.1 + proj.id * 0.07) % 1.0).toFloat
      val fx = sx - dx * t * 0.5f + perpX * Math.sin(phase * 3 + i * 1.5).toFloat * (4f + t * 16f) * 0.35f
      val fy = sy - dy * t * 0.5f + perpY * Math.sin(phase * 3 + i * 1.5).toFloat * (4f + t * 16f) * 0.35f
      val green = Math.max(0f, 0.9f - t * 0.5f)
      sb.fillOval(fx, fy, 5f + t * 6f, 4f + t * 5f, 1f, green, Math.max(0f, 0.3f - t * 0.2f), 0.65f * (1f - t), 8)
    ; i += 1 }

    // Cartoon smoke puffs — round puffy clouds
    { var i = 0; while (i < 6) {
      val t = ((tick * 0.04 + i * 0.167 + proj.id * 0.09) % 1.0).toFloat
      val smX = sx - dx * t * 0.65f + perpX * Math.sin(phase * 0.8 + i * 1.3).toFloat * (6f + t * 12f)
      val smY = sy - dy * t * 0.65f + perpY * Math.sin(phase * 0.8 + i * 1.3).toFloat * (6f + t * 12f) - t * 12f
      val gray = 0.6f + t * 0.1f
      val puffSize = 6f + t * 10f
      // Outline
      sb.strokeOval(smX, smY, puffSize + 1f, (puffSize + 1f) * 0.85f, 1.5f, 0.3f, 0.3f, 0.3f, 0.2f * (1f - t), 10)
      // Puff
      sb.fillOval(smX, smY, puffSize, puffSize * 0.85f, gray, gray, gray, 0.35f * (1f - t), 10)
    ; i += 1 } }

    // Rocket body — bold dark outline
    sb.strokeLine(sx, sy, tipX, tipY, 16f * ds, 0.1f, 0.1f, 0.08f, 0.85f * p)
    sb.strokeLine(sx, sy, tipX, tipY, 13f * ds, 0.4f, 0.42f, 0.3f, 0.95f * p)
    sb.strokeLine(sx, sy, tipX, tipY, 6f * ds, 0.55f, 0.58f, 0.45f, 0.65f * p)
    // Red band — bolder
    val bandX = sx + dx * 0.45f; val bandY = sy + dy * 0.45f
    sb.strokeLine(bandX - dx * 0.05f, bandY - dy * 0.05f, bandX + dx * 0.05f, bandY + dy * 0.05f,
      16f * ds, 0.15f, 0.02f, 0.01f, 0.7f * p)
    sb.strokeLine(bandX - dx * 0.05f, bandY - dy * 0.05f, bandX + dx * 0.05f, bandY + dy * 0.05f,
      14f * ds, 0.95f, 0.2f, 0.1f, 0.85f * p)
    // Fins — bigger with outline
    var f = -1; while (f <= 1) {
      _polyXs3(0) = sx; _polyXs3(1) = (sx - nx * 6f * ds + perpX * f * 11f * ds).toFloat; _polyXs3(2) = (sx - nx * 18f * ds).toFloat
      _polyYs3(0) = sy; _polyYs3(1) = (sy - ny * 6f * ds + perpY * f * 11f * ds).toFloat; _polyYs3(2) = (sy - ny * 18f * ds).toFloat
      sb.fillPolygon(_polyXs3, _polyYs3, 3, 0.45f, 0.48f, 0.35f, 0.85f * p)
      sb.strokePolygon(_polyXs3, _polyYs3, 3, 2f, 0.15f, 0.15f, 0.12f, 0.7f * p)
    ; f += 2 }
    // Nosecone — bigger with outline
    val noseX = tipX + nx * 20f * ds; val noseY = tipY + ny * 20f * ds
    _polyXs3(0) = noseX; _polyXs3(1) = tipX + perpX * 8f * ds; _polyXs3(2) = tipX - perpX * 8f * ds
    _polyYs3(0) = noseY; _polyYs3(1) = tipY + perpY * 8f * ds; _polyYs3(2) = tipY - perpY * 8f * ds
    sb.fillPolygon(_polyXs3, _polyYs3, 3, 0.7f, 0.7f, 0.65f, 0.95f * p)
    sb.strokePolygon(_polyXs3, _polyYs3, 3, 2.5f, 0.15f, 0.15f, 0.12f, 0.8f * p)
    // Cartoon shine on nosecone
    sb.fillOval(tipX + nx * 8f * ds + KEY_LIGHT_X * 3f * ds, tipY + ny * 8f * ds + KEY_LIGHT_Y * 3f * ds,
      4f * ds, 3f * ds, 1f, 1f, 1f, 0.35f * p, 6)
    drawChargeCrackle(sx, sy, 16f * ds, 0.9f, 0.5f, 0.2f, p, sb, phase, proj.chargeLevel)
  }

  /** Lightning — CARTOONISH: thick bold bolt with dark outline, huge branches, sparkle stars */
  private def lightningBolt(r: Float, g: Float, b: Float): Renderer =
    (proj, sx, sy, sb, tick) => drawLightning(proj, sx, sy, sb, tick, r, g, b)

  private def drawLightning(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int,
                            lr: Float, lg: Float, lb: Float): Unit = {
    // Derived once so the bolt, its branches, its sparks and its tip flash all agree on
    // one palette — the previous version hard-coded a different lavender at each layer.
    val oR = lr * 0.18f; val oG = lg * 0.18f; val oB = lb * 0.22f + 0.06f
    val hR = mix(lr, 1f, 0.55f); val hG = mix(lg, 1f, 0.55f); val hB = mix(lb, 1f, 0.55f)
    beamTip(sx, sy, proj, 6f)
    val tipX = _tipX; val tipY = _tipY
    val phase = (tick + proj.id * 41) * 0.5
    computeAllDynamics(proj, lr, lg, lb, phase)
    val flicker = (0.6 + 0.4 * Math.sin(phase * 8)).toFloat * dynAlpha
    val ds = dynScale
    val bc = _chgBright
    val dx = tipX - sx; val dy = tipY - sy
    val len = Math.sqrt(dx * dx + dy * dy).toFloat
    if (len < 1) return
    val nx = -dy / len; val ny = dx / len

    // Flickering regen
    val regenSeed = (tick / 3) * 7 + proj.id * 41
    val segs = 8
    _boltXs(0) = sx; _boltYs(0) = sy; _boltXs(segs) = tipX; _boltYs(segs) = tipY
    var i = 1; while (i < segs) {
      val t = i.toFloat / segs
      val jitter = (Math.sin(regenSeed * 0.9 + i * 2.7).toFloat * 16f +
        Math.cos(regenSeed * 1.3 + i * 3.9).toFloat * 7f) * ds
      _boltXs(i) = sx + dx * t + nx * jitter
      _boltYs(i) = sy + dy * t + ny * jitter
    ; i += 1 }

    // Dark cartoon outline bolt
    { var i = 0; while (i < segs) { sb.strokeLine(_boltXs(i), _boltYs(i), _boltXs(i + 1), _boltYs(i + 1), 14f * ds, oR, oG, oB, 0.7f * flicker); i += 1 } }
    // Main bolt — thicker
    { var i = 0; while (i < segs) { sb.strokeLine(_boltXs(i), _boltYs(i), _boltXs(i + 1), _boltYs(i + 1), 10.5f * ds, lr, lg, lb, 0.9f * flicker); i += 1 } }
    // White-hot core — thicker
    { var i = 0; while (i < segs) { sb.strokeLine(_boltXs(i), _boltYs(i), _boltXs(i + 1), _boltYs(i + 1), 5f * ds, mix(hR, 1f, 0.6f + bc * 0.4f), mix(hG, 1f, 0.6f + bc * 0.4f), mix(hB, 1f, 0.5f + bc * 0.5f), 0.98f * flicker); i += 1 } }

    // 6 branch forks — bigger with outlines
    var b = 0; while (b < 6) {
      val bSeg = 1 + b % segs
      if (bSeg < segs) {
        val bAngle = Math.PI * 0.4 * (if (b % 2 == 0) 1 else -1) +
          Math.sin(regenSeed * 0.7 + b * 2.3).toFloat * 0.5
        val bLen = (24f + Math.sin(regenSeed * 0.5 + b * 2.1).toFloat * 10f) * ds
        val bex = _boltXs(bSeg) + Math.cos(bAngle).toFloat * bLen
        val bey = _boltYs(bSeg) + Math.sin(bAngle).toFloat * bLen * 0.5f
        // Branch outline
        sb.strokeLine(_boltXs(bSeg), _boltYs(bSeg), bex, bey, 6f * ds, oR, oG, oB, 0.35f * flicker)
        sb.strokeLine(_boltXs(bSeg), _boltYs(bSeg), bex, bey, 4.5f * ds, lr, lg, lb, 0.55f * flicker)
        sb.strokeLine(_boltXs(bSeg), _boltYs(bSeg), bex, bey, 2f * ds, hR, hG, hB, 0.7f * flicker)
        // Branch tip spark
        sb.fillOval(bex, bey, 4f * ds, 3f * ds, hR, hG, hB, 0.6f * flicker, 6)
      }
    ; b += 1 }

    // Ambient electric sparks — 7, bigger, brighter
    { var i = 0; while (i < 7) {
      val sparkSeg = 1 + (i * 2) % segs
      val sparkOff = Math.sin(phase * 6 + i * 3.7).toFloat * 14f * ds
      val sparkX = _boltXs(sparkSeg) + nx * sparkOff
      val sparkY = _boltYs(sparkSeg) + ny * sparkOff
      val sparkAlpha = (0.35 + 0.45 * Math.sin(phase * 9 + i * 2.9)).toFloat * flicker
      sb.fillOval(sparkX, sparkY, 4.5f * ds, 3.5f * ds, hR, hG, hB, sparkAlpha, 8)
    ; i += 1 } }

    // Tip flash — bigger with cartoon outline and sparkle star
    val tipPulse = (0.6 + 0.4 * Math.sin(phase * 4)).toFloat
    sb.fillOvalSoft(tipX, tipY, 24f * ds * dynGlow * tipPulse, 18f * ds * dynGlow * tipPulse, lr, lg, lb, 0.35f * flicker, 0f, 12)
    sb.strokeOval(tipX, tipY, 14f * ds * tipPulse, 10f * ds * tipPulse, outlineW(14f * ds * tipPulse), oR, oG, oB, 0.6f * flicker, 10)
    sb.fillOval(tipX, tipY, 13f * ds * tipPulse, 9f * ds * tipPulse, mix(hR, 1f, bc), mix(hG, 1f, bc), mix(hB, 1f, bc), 0.75f * flicker, 10)
    drawSparkleStar(tipX, tipY, 10f * ds * tipPulse, 1f, 1f, 1f, 0.5f * flicker, sb, phase * 2)

    // Expanding ring at tip
    val ringP = ((phase * 0.7) % 1.0).toFloat
    sb.strokeOval(tipX, tipY, (8f + ringP * 20f) * ds, (5.5f + ringP * 14f) * ds,
      2.5f * (1f - ringP), hR, hG, hB, 0.4f * (1f - ringP) * flicker, 10)
    drawReturnGhosts(tipX, tipY, 13f * ds, lr, lg, lb, flicker, sb, proj)
  }

  /** Thunder Strike — CARTOONISH: huge bold bolt, massive ground impact with debris */
  private def drawThunderStrike(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 31) * 0.5
    computeAllDynamics(proj, 1f, 0.9f, 0.35f, phase)
    val flicker = (0.5 + 0.5 * Math.sin(phase * 8)).toFloat * dynAlpha
    val ds = dynScale
    val bc = _chgBright

    // Ground flash
    sb.fillOvalSoft(sx, sy, 53.3f * ds * dynGlow, 37.4f * ds * dynGlow, 1f, 0.95f, 0.4f, 0.28f * flicker, 0f, 20)

    // Zigzag bolt from above — wider
    val pts = 9
    _thunderXs(0) = sx - 8 * ds; _thunderYs(0) = sy - 70 * ds
    _thunderXs(1) = sx + 18 * ds; _thunderYs(1) = sy - 45 * ds
    _thunderXs(2) = sx + 2 * ds; _thunderYs(2) = sy - 35 * ds
    _thunderXs(3) = sx + 20 * ds; _thunderYs(3) = sy - 12 * ds
    _thunderXs(4) = sx + 7 * ds; _thunderYs(4) = sy - 6 * ds
    _thunderXs(5) = sx + 22 * ds; _thunderYs(5) = sy + 22 * ds
    _thunderXs(6) = sx + 9 * ds; _thunderYs(6) = sy + 18 * ds
    _thunderXs(7) = sx + 18 * ds; _thunderYs(7) = sy + 38 * ds
    _thunderXs(8) = sx + 5 * ds; _thunderYs(8) = sy + 38 * ds

    // Dark cartoon outline
    { var i = 0; while (i < pts - 1) { sb.strokeLine(_thunderXs(i), _thunderYs(i), _thunderXs(i + 1), _thunderYs(i + 1), 18f * ds, 0.15f, 0.12f, 0.03f, 0.7f * flicker); i += 1 } }
    // Soft glow
    { var i = 0; while (i < pts - 1) { sb.strokeLineSoft(_thunderXs(i), _thunderYs(i), _thunderXs(i + 1), _thunderYs(i + 1), 28f * ds, 1f, 0.85f, 0.2f, 0.25f * flicker); i += 1 } }
    // Main bolt — thick
    { var i = 0; while (i < pts - 1) { sb.strokeLine(_thunderXs(i), _thunderYs(i), _thunderXs(i + 1), _thunderYs(i + 1), 12f * ds, 1f, 0.9f, 0.35f, 0.85f * flicker); i += 1 } }
    // White-hot core
    { var i = 0; while (i < pts - 1) { sb.strokeLine(_thunderXs(i), _thunderYs(i), _thunderXs(i + 1), _thunderYs(i + 1), 5f * ds, 1f, 1f, mix(0.9f, 1f, bc), 0.98f * flicker); i += 1 } }

    // Ground impact — bigger with debris
    sb.strokeOval(sx, sy + 36f * ds, 26f * ds, 9f * ds, outlineW(26f * ds), 0.2f, 0.15f, 0.02f, 0.5f * flicker, 14)
    sb.fillOval(sx, sy + 36f * ds, 24f * ds, 8f * ds, 1f, 0.9f, 0.3f, 0.6f * flicker, 14)

    // Impact debris sparks — bigger and more
    var i2 = 0; while (i2 < 12) {
      val angle = phase * 2 + i2 * Math.PI / 6
      val dist = (16f + Math.sin(phase * 3 + i2).toFloat * 10f) * ds
      sb.fillOval((sx + Math.cos(angle).toFloat * dist), (sy + 34 * ds + Math.sin(angle).toFloat * dist * 0.3f).toFloat,
        4.5f * ds, 3.5f * ds, 1f, 1f, 0.6f, 0.65f * flicker, 8)
    ; i2 += 1 }

    // Sparkle star at impact point
    drawSparkleStar(sx, sy + 36f * ds, 12f * ds, 1f, 1f, 0.7f, 0.5f * flicker, sb, phase * 2)
  }

  /** Boulder — tumbling rock with weight, layered shading, cracks, dust, and debris chips */
  private def drawBoulder(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int, r: Float): Unit = {
    val phase = (tick + proj.id * 19) * 0.3
    computeAllDynamics(proj, 0.4f, 0.32f, 0.2f, phase)
    val p = (0.85 + 0.15 * Math.sin(phase * _stPulseMult)).toFloat * dynAlpha
    val bounceRaw = Math.sin(phase * 1.5).toFloat
    val bounce = (Math.abs(bounceRaw) * 10f).toFloat
    val bounceContact = Math.abs(bounceRaw)
    val ds = dynScale
    val rr = r * ds
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val spin = phase * 2.5
    val hlX = Math.cos(spin).toFloat; val hlY = Math.sin(spin).toFloat

    // Speed lines
    drawSpeedLines(sx, sy, ndx, ndy, 0.45f, 0.38f, 0.25f, 0.25f * p, sb, 6, rr * 1.5f)

    // Ribbon trail of dust
    drawRibbonTrail(sx, sy, ndx, ndy, 0.55f, 0.48f, 0.35f, 0.3f * p, sb, tick, proj.id, 8, 45f * ds * dynTrail, 10f * ds, 2f)

    // Massive ground impact shockwave ring near bounce contact
    if (bounceContact < 0.18f) {
      val impactT = 1f - bounceContact / 0.18f
      val impactAlpha = impactT * 0.55f * p
      val impactR = rr * (1.5f + (1f - impactT) * 0.8f)
      // Outer shockwave flash
      sb.strokeOval(sx, sy + rr * 0.38f, impactR * 1.2f, impactR * 0.35f, 4f * impactT,
        0.7f, 0.6f, 0.4f, impactAlpha * 0.5f, 14)
      // Inner impact ring
      sb.strokeOval(sx, sy + rr * 0.38f, impactR, impactR * 0.3f, 2.5f,
        0.5f, 0.42f, 0.3f, impactAlpha, 12)
      // Ground scorch mark
      sb.fillOval(sx, sy + rr * 0.38f, impactR * 0.9f, impactR * 0.25f,
        0.22f, 0.18f, 0.1f, impactAlpha * 0.35f, 12)
      // Spark burst at bounce impact
      drawSparkBurst(sx, sy + rr * 0.35f, 0.8f, 0.65f, 0.35f, impactAlpha * 0.7f, sb, tick, proj.id, 6, rr * 1.2f)
    }

    // Large dynamic shadow — bigger with bounce height
    val shadowScale = 1f + bounce * 0.04f
    val shadowAlpha = Math.max(0.1f, 0.42f - bounce * 0.015f)
    sb.fillOval(sx, sy + rr * 0.38f, rr * 1.4f * shadowScale, rr * 0.38f * shadowScale,
      0f, 0f, 0f, shadowAlpha, 16)
    sb.fillOval(sx, sy + rr * 0.38f, rr * 1f * shadowScale, rr * 0.25f * shadowScale,
      0f, 0f, 0f, shadowAlpha * 0.4f, 14)

    // Faceted stone hull. The vertex radii come from a fixed per-index pattern so the
    // silhouette is lumpy but stable as it tumbles — re-randomising per frame makes the
    // rock boil.
    val hullN = 9
    var hv = 0
    while (hv < hullN) {
      val a = spin * 0.6 + hv * (Math.PI * 2 / hullN)
      val jag = 0.80f + 0.26f * Math.sin(hv * 2.399f).toFloat
      _shpXs(hv) = sx + Math.cos(a).toFloat * rr * 1.05f * jag
      _shpYs(hv) = sy - bounce + Math.sin(a).toFloat * rr * 0.92f * jag
      hv += 1
    }
    sb.strokePolygon(_shpXs, _shpYs, hullN, 4.5f, 0.10f, 0.06f, 0.02f, 0.9f * p)
    sb.fillPolygon(_shpXs, _shpYs, hullN, 0.42f, 0.34f, 0.22f, 0.97f * p)
    // Facet edges from the hull corners toward the centre — what makes it read as carved
    hv = 0
    while (hv < hullN) {
      sb.strokeLine(_shpXs(hv), _shpYs(hv), sx + (_shpXs(hv) - sx) * 0.25f,
        sy - bounce + (_shpYs(hv) - sy + bounce) * 0.25f, 1.6f, 0.26f, 0.20f, 0.12f, 0.45f * p)
      hv += 1
    }
    // Rotating shadow face (bottom half follows spin)
    sb.fillOval(sx - hlX * rr * 0.12f, sy - bounce + rr * 0.18f + hlY * rr * 0.08f,
      rr * 0.85f, rr * 0.55f, 0.22f, 0.17f, 0.1f, 0.45f * p, 14)
    // Rotating highlight face
    sb.fillOval(sx + hlX * rr * 0.18f, sy - bounce + hlY * rr * 0.12f - rr * 0.15f,
      rr * 0.6f, rr * 0.48f, 0.58f, 0.5f, 0.36f, 0.6f * p, 12)
    // Top specular glint (rotating)
    sb.fillOval(sx + hlX * rr * 0.15f - rr * 0.08f, sy - bounce - rr * 0.32f + hlY * rr * 0.08f,
      rr * 0.3f, rr * 0.22f, 0.72f, 0.63f, 0.48f, 0.5f * p, 8)
    sb.fillOval(sx + hlX * rr * 0.1f - rr * 0.05f, sy - bounce - rr * 0.38f + hlY * rr * 0.05f,
      rr * 0.15f, rr * 0.1f, 0.85f, 0.78f, 0.6f, 0.35f * p, 6)

    // 8 deep crack lines with depth shading and bright edges
    var cr = 0; while (cr < 8) {
      val cAngle = spin * 0.3 + cr * Math.PI * 2 / 8 + proj.id * 0.7
      val cLen = rr * (0.35f + Math.sin(phase * 0.4 + cr * 2.3).toFloat * 0.22f)
      val cStartX = sx + Math.cos(cAngle).toFloat * rr * 0.08f
      val cStartY = sy - bounce + Math.sin(cAngle).toFloat * rr * 0.06f
      val cEndX = sx + Math.cos(cAngle).toFloat * cLen
      val cEndY = sy - bounce + Math.sin(cAngle).toFloat * cLen * 0.7f
      // Dark crack depth
      sb.strokeLine(cStartX, cStartY, cEndX, cEndY, 2.5f, 0.12f, 0.08f, 0.03f, 0.55f * p)
      // Lighter bright edge highlight
      sb.strokeLine(cStartX + 0.6f, cStartY + 0.6f, cEndX + 0.6f, cEndY + 0.6f,
        1f, 0.55f, 0.47f, 0.35f, 0.3f * p)
      // Crack fork at end
      val fAngle = cAngle + 0.5 + Math.sin(phase * 0.3 + cr) * 0.3
      val fLen = cLen * 0.3f
      sb.strokeLine(cEndX, cEndY, cEndX + Math.cos(fAngle).toFloat * fLen,
        cEndY + Math.sin(fAngle).toFloat * fLen * 0.6f, 1.5f, 0.15f, 0.1f, 0.05f, 0.35f * p)
    ; cr += 1 }

    // Dense 12-particle dust cloud trail
    var i = 0; while (i < 12) {
      val t = ((tick * 0.055 + i * 0.083 + proj.id * 0.11) % 1.0).toFloat
      val s = (5f + t * 12f) * ds
      val dustX = sx - ndx * t * rr * 2.2f + Math.sin(phase + i * 2).toFloat * 8f * ds
      val dustY = sy - ndy * t * rr * 1.6f + t * 14f * ds
      val gray = 0.52f + t * 0.15f
      // Dust puff outline
      sb.strokeOval(dustX, dustY, s + 1f, (s + 1f) * 0.6f, 1f, 0.35f, 0.3f, 0.2f, 0.15f * (1f - t) * p, 8)
      // Dust puff body
      sb.fillOval(dustX, dustY, s, s * 0.6f, gray, gray * 0.85f, gray * 0.65f, 0.4f * (1f - t) * p, 8)
    ; i += 1 }

    // 8 flying debris chips with varying sizes
    { var i = 0; while (i < 8) {
      val chipPhase = ((phase * 0.7 + i * 0.125 + proj.id * 0.17) % 1.0).toFloat
      val chipAngle = phase * 3.5 + i * Math.PI * 2 / 8
      val chipDist = rr * (0.65f + chipPhase * 1.4f)
      val chipX = sx + Math.cos(chipAngle).toFloat * chipDist
      val chipY = sy - bounce * (1f - chipPhase) + Math.sin(chipAngle).toFloat * chipDist * 0.4f + chipPhase * chipPhase * 22f
      val chipSz = (2f + (1f - chipPhase) * (3f + (i % 3).toFloat * 1.5f)) * ds
      val chipSpin = phase * 5 + i * 2.3
      val chipStrX = 1f + Math.sin(chipSpin).toFloat * 0.3f
      // Chip outline
      sb.fillOval(chipX, chipY, chipSz * chipStrX + 0.8f, chipSz * 0.85f + 0.8f,
        0.18f, 0.14f, 0.08f, 0.35f * (1f - chipPhase) * p, 5)
      // Chip body
      sb.fillOval(chipX, chipY, chipSz * chipStrX, chipSz * 0.8f,
        0.5f + (i % 3).toFloat * 0.05f, 0.42f, 0.28f, 0.55f * (1f - chipPhase) * p, 5)
    ; i += 1 } }

    drawChargeCrackle(sx, sy - bounce, rr, 0.4f, 0.32f, 0.2f, p, sb, phase, proj.chargeLevel)
    drawReturnGhosts(sx, sy - bounce, rr, 0.4f, 0.32f, 0.2f, p, sb, proj)
  }


  /** Geyser — MASSIVE towering water eruption with dense layering, mist halo, foam crown, spray arcs */
  private def drawGeyser(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
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
  private def drawSwordWave(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
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
      sb.fillPolygon(_swCrescXs, _swCrescYs, (segs + 1) * 2, 0.5f, 0.5f, 0.75f, gAlpha)
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
    // Crescent body fill (dark -> body -> bright)
    sb.fillPolygon(_swCrescXs, _swCrescYs, (segs + 1) * 2, dr, dg, db, 0.7f * p);
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

  /** Bat Swarm - dark cloud with varied-size bats, scattered formation, red eye trails */
  private def drawBatSwarm(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 31) * 0.4
    computeAllDynamics(proj, 0.2f, 0.03f, 0.3f, phase)
    val p = (0.85 + 0.15 * Math.sin(phase * _stPulseMult)).toFloat * dynAlpha
    val ds = dynScale * 1.6f // this one reads far below the roster's scale unscaled
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy

    // Dark cloud — wider for scattered formation
    sb.fillOvalSoft(sx, sy, 25.9f * ds * dynGlow, 18.7f * ds * dynGlow, 0.1f, 0f, 0.15f, 0.3f * p, 0f, 14)

    // 7 bats: 3 small, 3 medium, 1 large with scattered formation
    var bat = 0; while (bat < 7) {
      val bAngle = phase * 1.5 + bat * Math.PI * 2 / 7
      // Wider orbital radius range for more chaotic cloud
      val bDist = (6f + Math.sin(phase * 2 + bat * 1.7).toFloat * 9f + (bat % 3) * 3f) * ds
      val bx = sx + Math.cos(bAngle).toFloat * bDist
      val by = sy + Math.sin(bAngle).toFloat * bDist * 0.5f
      // Each bat flaps at slightly different speed
      val flapSpeed = 7f + bat * 0.7f
      val wingFlap = Math.sin(phase * flapSpeed + bat * 2.3).toFloat

      // Varied sizes: bat 0-2 small, 3-5 medium, 6 large
      val batScale = (if (bat < 3) 0.7f else if (bat < 6) 1f else 1.4f) * ds
      val wingW = 6f * batScale
      val wingH = 6f * batScale * wingFlap

      // Wings
      _polyXs3(0) = bx; _polyXs3(1) = bx - wingW; _polyXs3(2) = bx - wingW * 0.5f
      _polyYs3(0) = by; _polyYs3(1) = by - wingH; _polyYs3(2) = by + 2 * batScale
      sb.fillPolygon(_polyXs3, _polyYs3, 3, 0.08f, 0f, 0.12f, 0.8f * p)
      _polyXs3(0) = bx; _polyXs3(1) = bx + wingW; _polyXs3(2) = bx + wingW * 0.5f
      _polyYs3(0) = by; _polyYs3(1) = by - wingH; _polyYs3(2) = by + 2 * batScale
      sb.fillPolygon(_polyXs3, _polyYs3, 3, 0.08f, 0f, 0.12f, 0.8f * p)
      // Wing membrane highlight
      sb.strokeLine(bx, by, bx - wingW * 0.85f, by - wingH * 0.7f,
        0.8f, 0.2f, 0.05f, 0.25f, 0.4f * p)
      sb.strokeLine(bx, by, bx + wingW * 0.85f, by - wingH * 0.7f,
        0.8f, 0.2f, 0.05f, 0.25f, 0.4f * p)
      // Body
      sb.fillOval(bx, by, 2f * batScale, 2.5f * batScale, 0.06f, 0f, 0.1f, 0.85f * p, 6)
      // Eyes
      sb.fillOval(bx - 1f * batScale, by - 1f, 1.2f * batScale, 1f * batScale, 0.9f, 0.1f, 0.1f, 0.6f * p, 4)
      sb.fillOval(bx + 1f * batScale, by - 1f, 1.2f * batScale, 1f * batScale, 0.9f, 0.1f, 0.1f, 0.6f * p, 4)

      // Red eye trails — tiny red dots behind each bat
      var trail = 1; while (trail <= 3) {
        val trailT = trail * 0.15f
        val tx = bx - ndx * trailT * 12f
        val ty = by - ndy * trailT * 12f
        sb.fillOval(tx - 1f * batScale, ty - 1f, 0.8f, 0.6f, 0.9f, 0.08f, 0.08f, 0.3f * (1f - trailT) * p, 4)
        sb.fillOval(tx + 1f * batScale, ty - 1f, 0.8f, 0.6f, 0.9f, 0.08f, 0.08f, 0.3f * (1f - trailT) * p, 4)
      ; trail += 1 }
    ; bat += 1 }
  }

  /** Raise Dead - skeleton hands erupting from ground with soul energy */
  private def drawRaiseDead(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 29) * 0.35
    computeAllDynamics(proj, 0.3f, 0.85f, 0.2f, phase)
    val p = (0.85 + 0.15 * Math.sin(phase * _stPulseMult)).toFloat * dynAlpha
    val ds = dynScale
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy

    // Dark mist halo
    sb.fillOvalSoft(sx, sy - 8f * ds, 34.6f * ds * dynGlow, 27.4f * ds * dynGlow,
      0.08f, 0.2f, 0.05f, 0.2f * p, 0f, 18)

    // Ground disturbance circle (3-layer)
    sb.strokeOval(sx, sy + 5f * ds, 36f * ds, 12f * ds, 3f, 0.04f, 0.1f, 0.02f, 0.5f * p, 14)
    sb.fillOval(sx, sy + 5f * ds, 34f * ds, 11f * ds, 0.08f, 0.22f, 0.05f, 0.4f * p, 14)
    sb.fillOval(sx, sy + 5f * ds, 24f * ds, 8f * ds, 0.12f, 0.3f, 0.08f, 0.35f * p, 12)
    sb.fillOval(sx, sy + 5f * ds, 12f * ds, 4.5f * ds, 0.2f, 0.45f, 0.12f, 0.3f * p, 10)

    // Ground crack lines
    var crack = 0; while (crack < 6) {
      val cAngle = phase * 0.2 + crack * Math.PI / 3
      val cLen = (16f + Math.sin(phase * 1.5 + crack * 2.3).toFloat * 6f) * ds
      val cx = sx + Math.cos(cAngle).toFloat * cLen
      val cy = sy + 5f * ds + Math.sin(cAngle).toFloat * cLen * 0.33f
      sb.strokeLine(sx, sy + 5f * ds, cx, cy, 2f, 0.04f, 0.12f, 0.02f, 0.4f * p)
      sb.strokeLine(sx, sy + 5f * ds, cx, cy, 0.8f, 0.25f, 0.6f, 0.15f, 0.25f * p)
    ; crack += 1 }

    // 5 skeleton hands with articulated finger bones
    var hand = 0; while (hand < 5) {
      val hx = sx + (hand - 2f) * 12f * ds
      val rise = ((tick * 0.035 + hand * 0.2) % 1.0).toFloat
      val hy = sy - rise * 35f * ds
      val handAlpha = 0.85f * (1f - rise * 0.25f) * p
      val wristX = hx + Math.sin(phase + hand).toFloat * 4f * ds
      // Arm bone (bold outlined)
      sb.strokeLine(hx, sy + 2f * ds, wristX, hy, 4.5f * ds, 0.15f, 0.12f, 0.08f, handAlpha)
      sb.strokeLine(hx, sy + 2f * ds, wristX, hy, 3f * ds, 0.75f, 0.7f, 0.55f, handAlpha)
      sb.strokeLine(hx, sy + 2f * ds, wristX, hy, 1.2f * ds, 0.88f, 0.85f, 0.7f, handAlpha * 0.5f)
      // 3 finger bones per hand with articulated segments
      var f = -1; while (f <= 1) {
        val fAngle = -Math.PI / 2 + f * 0.6 + Math.sin(phase * 2 + hand + f * 1.3).toFloat * 0.3
        val seg1Len = 7f * ds; val seg2Len = 5f * ds
        val knuckleX = wristX + Math.cos(fAngle).toFloat * seg1Len
        val knuckleY = hy + Math.sin(fAngle).toFloat * seg1Len
        val tipAngle = fAngle + Math.sin(phase * 3 + hand + f * 2.1).toFloat * 0.4
        val tipX = knuckleX + Math.cos(tipAngle).toFloat * seg2Len
        val tipY = knuckleY + Math.sin(tipAngle).toFloat * seg2Len
        // Finger bone outline
        sb.strokeLine(wristX, hy, knuckleX, knuckleY, 3f * ds, 0.15f, 0.12f, 0.08f, handAlpha * 0.9f)
        sb.strokeLine(wristX, hy, knuckleX, knuckleY, 2f * ds, 0.78f, 0.73f, 0.58f, handAlpha * 0.9f)
        sb.strokeLine(knuckleX, knuckleY, tipX, tipY, 2.5f * ds, 0.15f, 0.12f, 0.08f, handAlpha * 0.85f)
        sb.strokeLine(knuckleX, knuckleY, tipX, tipY, 1.5f * ds, 0.8f, 0.75f, 0.6f, handAlpha * 0.85f)
        // Knuckle joint dot
        sb.fillOval(knuckleX, knuckleY, 2f * ds, 1.8f * ds, 0.7f, 0.65f, 0.5f, handAlpha * 0.7f, 4)
      ; f += 1 }
    ; hand += 1 }

    // Glowing green soul energy emanating from ground
    { var i = 0; while (i < 8) {
      val soulAngle = phase * 2 + i * Math.PI / 4
      val soulDist = (8f + Math.sin(phase * 1.5 + i * 1.7).toFloat * 6f) * ds
      val soulY0 = sy + 3f * ds
      val soulY1 = sy - 10f * ds + Math.sin(phase * 3 + i * 2.3).toFloat * 8f * ds
      val soulX = sx + Math.cos(soulAngle).toFloat * soulDist
      sb.strokeLineSoft(soulX, soulY0, soulX + Math.sin(phase * 2 + i).toFloat * 4f * ds, soulY1,
        3f * ds, 0.25f, 0.85f, 0.15f, 0.2f * p)
      sb.strokeLine(soulX, soulY0, soulX + Math.sin(phase * 2 + i).toFloat * 4f * ds, soulY1,
        1f * ds, 0.4f, 0.95f, 0.3f, 0.3f * p)
    ; i += 1 } }

    // 8 orbiting soul wisps with trails
    { var i = 0; while (i < 8) {
      val angle = phase * 1.5 + i * Math.PI / 4
      val dist = (16f + Math.sin(phase * 2 + i * 1.3).toFloat * 6f) * ds
      val wx = sx + Math.cos(angle).toFloat * dist
      val wy = sy + Math.sin(angle).toFloat * dist * 0.35f - 12f * ds
      val wAlpha = (0.45 + 0.25 * Math.sin(phase * 3 + i * 2)).toFloat * p
      // Wisp trail
      val prevAngle = angle - 0.5
      val prevX = sx + Math.cos(prevAngle).toFloat * dist
      val prevY = sy + Math.sin(prevAngle).toFloat * dist * 0.35f - 12f * ds
      sb.strokeLineSoft(prevX, prevY, wx, wy, 3f * ds, 0.2f, 0.7f, 0.15f, wAlpha * 0.3f)
      // Wisp glow
      sb.fillOvalSoft(wx, wy, 8f * ds, 7f * ds, 0.3f, 0.9f, 0.2f, wAlpha * 0.3f, 0f, 6)
      // Wisp outline
      sb.strokeOval(wx, wy, 4.5f * ds, 4f * ds, 1f, 0.08f, 0.25f, 0.04f, wAlpha * 0.5f, 6)
      // Wisp body
      sb.fillOval(wx, wy, 4f * ds, 3.5f * ds, 0.3f, 0.9f, 0.2f, wAlpha, 6)
    ; i += 1 } }

    // 10 dirt debris particles flying up
    { var i = 0; while (i < 10) {
      val t = ((tick * 0.06 + i * 0.1 + proj.id * 0.13) % 1.0).toFloat
      val debAngle = phase * 0.8 + i * Math.PI * 2 / 10
      val debDist = (6f + t * 18f) * ds
      val debX = sx + Math.cos(debAngle).toFloat * debDist
      val debY = sy + 3f * ds - t * 24f * ds + t * t * 8f * ds
      val debSz = (2.5f + (1f - t) * 3f) * ds
      sb.fillOval(debX, debY, debSz + 0.5f, debSz * 0.8f + 0.5f, 0.1f, 0.08f, 0.04f, 0.25f * (1f - t) * p, 5)
      sb.fillOval(debX, debY, debSz, debSz * 0.8f, 0.35f, 0.28f, 0.15f, 0.5f * (1f - t) * p, 5)
    ; i += 1 } }

    // Sparkle effects
    { var i = 0; while (i < 4) {
      val starPhase = ((phase * 0.4 + i * 0.25) % 1.0).toFloat
      val starAngle = phase * 1.3 + i * Math.PI / 2
      val starDist = (12f + starPhase * 16f) * ds
      val starX = sx + Math.cos(starAngle).toFloat * starDist
      val starY = sy + Math.sin(starAngle).toFloat * starDist * 0.35f - 12f * ds
      drawSparkleStar(starX, starY, 5f * (1f - starPhase * 0.5f) * ds,
        0.3f, 0.9f, 0.2f, 0.45f * (1f - starPhase) * p, sb, phase * 2 + i)
    ; i += 1 } }

    drawChargeCrackle(sx, sy - 12f * ds, 22f * ds, 0.3f, 0.85f, 0.2f, p, sb, phase, proj.chargeLevel)
    drawReturnGhosts(sx, sy, 22f * ds, 0.3f, 0.85f, 0.2f, p, sb, proj)
  }

  /** Wail - massive spectral ghost with sonic wave rings */
  private def drawWail(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 41) * 0.4
    computeAllDynamics(proj, 0.72f, 0.82f, 0.94f, phase)
    val p = (0.75 + 0.25 * Math.sin(phase * 3 * _stPulseMult)).toFloat * dynAlpha
    val ds = dynScale
    val wobble = Math.sin(phase * 2).toFloat * 5f * ds
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val gx = sx + wobble

    // Speed lines behind
    drawSpeedLines(sx, sy, ndx, ndy, 0.7f, 0.8f, 0.92f, 0.2f * p, sb, 5 + (_lifePct * 2).toInt, 28f * ds)

    // Ghostly ribbon trail
    drawRibbonTrail(sx, sy, ndx, ndy, 0.65f, 0.75f, 0.88f, 0.25f * p, sb, tick, proj.id,
      10, 45f * ds * dynTrail, 10f * ds, 1.5f)

    // Spectral glow halo
    val haloPulse = (0.7f + 0.3f * Math.sin(phase * 1.8).toFloat)
    sb.fillOvalSoft(gx, sy, 37.4f * ds * dynGlow * haloPulse, 30.2f * ds * dynGlow * haloPulse,
      0.7f, 0.8f, 0.95f, 0.2f * p, 0f, 20)

    // 5 hair/wisp tendrils flowing behind
    { var i = 0; while (i < 5) {
      val tAngle = -Math.PI * 0.7 + i * Math.PI * 0.35
      val tendrilLen = (18f + Math.sin(phase * 2 + i * 1.9).toFloat * 6f) * ds
      val tWave = Math.sin(phase * 3 + i * 2.3).toFloat * 5f * ds
      val tx0 = gx + Math.cos(tAngle).toFloat * 10f * ds
      val ty0 = sy - 5f * ds + Math.sin(tAngle).toFloat * 7f * ds
      val tx1 = tx0 - ndx * tendrilLen + tWave
      val ty1 = ty0 - ndy * tendrilLen + tWave * 0.3f
      sb.strokeLineSoft(tx0, ty0, tx1, ty1, 5f * ds, 0.6f, 0.72f, 0.85f, 0.15f * p)
      sb.strokeLine(tx0, ty0, tx1, ty1, 2f * ds, 0.75f, 0.85f, 0.95f, 0.3f * p)
    ; i += 1 } }

    // Bold dark outline on ghost body (3.5px)
    sb.strokeOval(gx, sy, 26f * ds, 22f * ds, 3.5f, 0.15f, 0.18f, 0.25f, 0.7f * p, 16)
    // 3-layer ghost body: transparent -> solid -> bright core
    sb.fillOval(gx, sy, 24f * ds, 20f * ds, 0.65f, 0.75f, 0.88f, 0.45f * p, 16)
    sb.fillOval(gx, sy, 18f * ds, 14f * ds, 0.75f, 0.85f, 0.95f, 0.6f * p, 14)
    sb.fillOval(gx, sy, 10f * ds, 8f * ds, 0.85f, 0.92f, 0.98f, 0.5f * p, 12)
    // Cartoon highlight
    sb.fillOval(gx - 4f * ds, sy - 5f * ds, 7f * ds, 4.5f * ds, 1f, 1f, 1f, 0.3f * p, 8)

    // Dark eye sockets with glowing pupil dots
    val eyeW = 5f * ds; val eyeH = 4.5f * ds
    // Left eye
    sb.strokeOval(gx - 7f * ds, sy - 4f * ds, eyeW + 1f, eyeH + 1f, 1.5f, 0.08f, 0.08f, 0.15f, 0.6f * p, 8)
    sb.fillOval(gx - 7f * ds, sy - 4f * ds, eyeW, eyeH, 0.04f, 0.04f, 0.1f, 0.88f * p, 10)
    val eyePulse = (0.5f + 0.5f * Math.sin(phase * 4).toFloat)
    sb.fillOval(gx - 7f * ds, sy - 4f * ds, 2.5f * ds, 2f * ds, 0.5f * eyePulse, 0.7f * eyePulse, 0.95f * eyePulse, 0.55f * p, 6)
    sb.fillOval(gx - 7f * ds, sy - 4.5f * ds, 1.2f * ds, 1f * ds, 0.8f, 0.92f, 1f, 0.4f * p, 4)
    // Right eye
    sb.strokeOval(gx + 8f * ds, sy - 4f * ds, eyeW + 1f, eyeH + 1f, 1.5f, 0.08f, 0.08f, 0.15f, 0.6f * p, 8)
    sb.fillOval(gx + 8f * ds, sy - 4f * ds, eyeW, eyeH, 0.04f, 0.04f, 0.1f, 0.88f * p, 10)
    sb.fillOval(gx + 8f * ds, sy - 4f * ds, 2.5f * ds, 2f * ds, 0.5f * eyePulse, 0.7f * eyePulse, 0.95f * eyePulse, 0.55f * p, 6)
    sb.fillOval(gx + 8f * ds, sy - 4.5f * ds, 1.2f * ds, 1f * ds, 0.8f, 0.92f, 1f, 0.4f * p, 4)

    // Huge expressive mouth with jaw animation
    val mouthOpen = (7f + Math.abs(Math.sin(phase * 4)).toFloat * 9f) * ds
    val mouthW = 8f * ds
    // Mouth outline
    sb.strokeOval(gx, sy + 3f * ds + mouthOpen * 0.5f, mouthW + 1f, mouthOpen * 0.55f + 1f, 2f,
      0.08f, 0.08f, 0.12f, 0.65f * p, 10)
    // Mouth void
    sb.fillOval(gx, sy + 3f * ds + mouthOpen * 0.5f, mouthW, mouthOpen * 0.5f, 0.03f, 0.03f, 0.08f, 0.85f * p, 10)
    // Inner mouth glow
    sb.fillOval(gx, sy + 3f * ds + mouthOpen * 0.5f, mouthW * 0.5f, mouthOpen * 0.25f,
      0.4f, 0.55f, 0.7f, 0.25f * p, 8)

    // 5 expanding sonic wave rings with outline
    var ring = 0; while (ring < 5) {
      val rp = ((phase * 0.4 + ring * 0.2) % 1.0).toFloat
      val ringR = (6f + rp * 30f) * ds
      val ringX = gx + ndx * rp * 24f * ds
      val ringY = sy + 3f * ds + ndy * rp * 14f * ds
      val ringA = 0.35f * (1f - rp) * p
      // Ring outline
      sb.strokeOval(ringX, ringY, ringR + 1.5f, (ringR + 1.5f) * 0.4f, 2.5f * (1f - rp * 0.5f),
        0.2f, 0.25f, 0.35f, ringA * 0.4f, 10)
      // Ring body
      sb.strokeOval(ringX, ringY, ringR, ringR * 0.4f, 2f * (1f - rp * 0.5f),
        0.72f, 0.82f, 0.95f, ringA, 10)
    ; ring += 1 }

    // 10 ectoplasm drip trail with gravity
    { var i = 0; while (i < 10) {
      val t = ((tick * 0.05 + i * 0.1 + proj.id * 0.13) % 1.0).toFloat
      val ectoX = gx + Math.sin(phase * 2 + i * 1.8).toFloat * 12f * ds
      val ectoY = sy + 8f * ds + t * 30f * ds
      val ectoSz = (4.5f + t * 5.5f) * ds
      // Ecto outline
      sb.fillOval(ectoX, ectoY, ectoSz + 0.5f, (ectoSz + 0.5f) * 0.75f,
        0.18f, 0.22f, 0.3f, 0.2f * (1f - t) * p, 8)
      // Ecto body
      sb.fillOval(ectoX, ectoY, ectoSz, ectoSz * 0.7f,
        0.6f, 0.75f, 0.88f, 0.45f * (1f - t) * p, 8)
    ; i += 1 } }

    // Sparkle stars
    { var i = 0; while (i < 4) {
      val starPhase = ((phase * 0.5 + i * 0.25) % 1.0).toFloat
      val starAngle = phase * 1.3 + i * Math.PI / 2
      val starDist = (14f + starPhase * 18f) * ds
      val starX = gx + Math.cos(starAngle).toFloat * starDist
      val starY = sy + Math.sin(starAngle).toFloat * starDist * 0.55f
      drawSparkleStar(starX, starY, 5f * (1f - starPhase * 0.5f) * ds,
        0.7f, 0.82f, 0.95f, 0.45f * (1f - starPhase) * p, sb, phase * 2 + i)
    ; i += 1 } }

    drawChargeCrackle(gx, sy, 24f * ds, 0.72f, 0.82f, 0.94f, p, sb, phase, proj.chargeLevel)
    drawReturnGhosts(sx, sy, 24f * ds, 0.72f, 0.82f, 0.94f, p, sb, proj)
  }

  /** Devour - massive shadowy maw with animated chomping jaws */
  private def drawDevour(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 23) * 0.45
    computeAllDynamics(proj, 0.22f, 0.06f, 0.1f, phase)
    val p = (0.9 + 0.1 * Math.sin(phase * _stPulseMult)).toFloat * dynAlpha
    val dr = _evoR; val dg = _evoG; val db = _evoB
    val ds = dynScale
    val chompCycle = Math.sin(phase * 3).toFloat
    val chomp = (Math.abs(chompCycle) * 0.55f + 0.45f).toFloat
    val jawOpen = (7f + chomp * 12f) * ds
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy

    // Speed lines behind
    drawSpeedLines(sx, sy, ndx, ndy, 0.2f, 0.06f, 0.1f, 0.22f * p, sb, 5 + (_lifePct * 2).toInt, 30f * ds)

    // Ribbon trail (dark energy)
    drawRibbonTrail(sx, sy, ndx, ndy, 0.15f, 0.03f, 0.08f, 0.25f * p, sb, tick, proj.id,
      10, 50f * ds * dynTrail, 12f * ds, 2f)

    // Dark energy halo
    val haloPulse = (0.7f + 0.3f * Math.sin(phase * 1.8).toFloat)
    sb.fillOvalSoft(sx, sy, 39.6f * ds * dynGlow * haloPulse, 31.7f * ds * dynGlow * haloPulse,
      0.15f, 0.02f, 0.08f, 0.22f * p, 0f, 20)

    // 6 dark energy tendrils reaching forward
    { var i = 0; while (i < 6) {
      val tAngle = Math.atan2(ndy, ndx) + (i - 2.5f) * 0.35
      val tLen = (18f + Math.sin(phase * 2.5 + i * 1.7).toFloat * 8f) * ds
      val tWave = Math.sin(phase * 3 + i * 2.3).toFloat * 4f * ds
      val tx0 = sx + Math.cos(tAngle).toFloat * 10f * ds
      val ty0 = sy + Math.sin(tAngle).toFloat * 6f * ds
      val tx1 = sx + Math.cos(tAngle).toFloat * tLen + tWave
      val ty1 = sy + Math.sin(tAngle).toFloat * tLen * 0.6f
      sb.strokeLineSoft(tx0, ty0, tx1, ty1, 4f * ds, 0.1f, 0.01f, 0.06f, 0.2f * p)
      sb.strokeLine(tx0, ty0, tx1, ty1, 1.5f * ds, 0.2f, 0.04f, 0.12f, 0.35f * p)
    ; i += 1 } }

    // Upper jaw polygon with 3-layer shading
    val jawW = 18f * ds; val jawH = 9f * ds
    // Upper jaw outline
    sb.strokeOval(sx, sy - jawOpen, jawW + 1f, jawH + 1f, 3.5f,
      0.04f, 0.01f, 0.02f, 0.85f * p, 14)
    // Upper jaw body
    sb.fillOval(sx, sy - jawOpen, jawW, jawH, 0.22f, 0.06f, 0.1f, 0.92f * p, 14)
    // Upper jaw highlight
    sb.fillOval(sx, sy - jawOpen - 1.5f * ds, jawW * 0.7f, jawH * 0.55f, 0.3f, 0.1f, 0.15f, 0.5f * p, 12)

    // Lower jaw outline
    sb.strokeOval(sx, sy + jawOpen, jawW + 1f, jawH + 1f, 3.5f,
      0.04f, 0.01f, 0.02f, 0.85f * p, 14)
    // Lower jaw body
    sb.fillOval(sx, sy + jawOpen, jawW, jawH, 0.2f, 0.05f, 0.08f, 0.92f * p, 14)
    // Lower jaw highlight
    sb.fillOval(sx, sy + jawOpen + 1f * ds, jawW * 0.65f, jawH * 0.5f, 0.28f, 0.08f, 0.13f, 0.45f * p, 12)

    // Gum lines (upper and lower)
    val gumLen = jawW * 0.85f
    sb.strokeLineSoft(sx - gumLen * 0.5f, sy - jawOpen + jawH * 0.55f,
      sx + gumLen * 0.5f, sy - jawOpen + jawH * 0.55f,
      4f * ds, 0.65f, 0.08f, 0.06f, 0.2f * p)
    sb.strokeLine(sx - gumLen * 0.5f, sy - jawOpen + jawH * 0.55f,
      sx + gumLen * 0.5f, sy - jawOpen + jawH * 0.55f,
      2f * ds, 0.75f, 0.12f, 0.08f, 0.5f * p)
    sb.strokeLineSoft(sx - gumLen * 0.5f, sy + jawOpen - jawH * 0.55f,
      sx + gumLen * 0.5f, sy + jawOpen - jawH * 0.55f,
      4f * ds, 0.65f, 0.08f, 0.06f, 0.2f * p)
    sb.strokeLine(sx - gumLen * 0.5f, sy + jawOpen - jawH * 0.55f,
      sx + gumLen * 0.5f, sy + jawOpen - jawH * 0.55f,
      2f * ds, 0.75f, 0.12f, 0.08f, 0.5f * p)

    // 8 teeth per jaw with dark outlines and white tips
    { var i = 0; while (i < 8) {
      val tx = sx + (i - 3.5f) * 4.2f * ds
      val toothLen = (8f + Math.sin(i * 1.7 + proj.id * 0.3).toFloat * 2f) * p * ds
      val toothW = 2.2f * ds * (1f - (i % 2) * 0.15f)
      // Upper teeth: dark outline triangle
      _polyXs3(0) = tx - toothW; _polyXs3(1) = tx; _polyXs3(2) = tx + toothW
      _polyYs3(0) = sy - jawOpen + jawH * 0.4f; _polyYs3(1) = sy - jawOpen + jawH * 0.4f + toothLen; _polyYs3(2) = sy - jawOpen + jawH * 0.4f
      sb.fillPolygon(_polyXs3, _polyYs3, 3, 0.12f, 0.1f, 0.08f, 0.85f * p)
      // White body (slightly smaller)
      _polyXs3(0) = tx - toothW * 0.75f; _polyXs3(1) = tx; _polyXs3(2) = tx + toothW * 0.75f
      _polyYs3(0) = sy - jawOpen + jawH * 0.42f; _polyYs3(1) = sy - jawOpen + jawH * 0.42f + toothLen * 0.92f; _polyYs3(2) = sy - jawOpen + jawH * 0.42f
      sb.fillPolygon(_polyXs3, _polyYs3, 3, 0.95f, 0.93f, 0.88f, 0.92f * p)
      // White tip highlight
      sb.fillOval(tx, sy - jawOpen + jawH * 0.42f + toothLen * 0.75f, 1.8f * ds, 1.8f * ds, 1f, 1f, 0.98f, 0.6f * p, 4)

      // Lower teeth (mirror): dark outline
      _polyXs3(0) = tx - toothW; _polyXs3(1) = tx; _polyXs3(2) = tx + toothW
      _polyYs3(0) = sy + jawOpen - jawH * 0.4f; _polyYs3(1) = sy + jawOpen - jawH * 0.4f - toothLen; _polyYs3(2) = sy + jawOpen - jawH * 0.4f
      sb.fillPolygon(_polyXs3, _polyYs3, 3, 0.12f, 0.1f, 0.08f, 0.85f * p)
      // White body
      _polyXs3(0) = tx - toothW * 0.75f; _polyXs3(1) = tx; _polyXs3(2) = tx + toothW * 0.75f
      _polyYs3(0) = sy + jawOpen - jawH * 0.42f; _polyYs3(1) = sy + jawOpen - jawH * 0.42f - toothLen * 0.92f; _polyYs3(2) = sy + jawOpen - jawH * 0.42f
      sb.fillPolygon(_polyXs3, _polyYs3, 3, 0.95f, 0.93f, 0.88f, 0.92f * p)
      sb.fillOval(tx, sy + jawOpen - jawH * 0.42f - toothLen * 0.75f, 1.8f * ds, 1.8f * ds, 1f, 1f, 0.98f, 0.6f * p, 4)
    ; i += 1 } }

    // Glowing gullet (inside mouth when open)
    val gulletAlpha = (0.5f + 0.3f * (1f - chomp)) * p
    sb.fillOvalSoft(sx, sy, 10f * ds, 6f * ds * chomp, 0.8f, 0.08f, 0.05f, gulletAlpha * 0.4f, 0f, 10)
    sb.fillOval(sx, sy, 8f * ds, 4.5f * ds * chomp, 0.75f, 0.06f, 0.04f, gulletAlpha * 0.6f, 10)
    sb.fillOval(sx, sy, 4f * ds, 2.5f * ds * chomp, 1f, 0.2f, 0.1f, gulletAlpha * 0.5f, 8)

    // 8 drool/saliva drip particles
    { var i = 0; while (i < 8) {
      val t = ((tick * 0.06 + i * 0.125 + proj.id * 0.13) % 1.0).toFloat
      val side = if (i % 2 == 0) 1f else -1f
      val dripX = sx + (i - 3.5f) * 3.5f * ds + Math.sin(phase + i * 1.7).toFloat * 2f * ds
      val dripStartY = sy + jawOpen * 0.5f * side
      val dripY = dripStartY + t * t * 18f * ds * side
      val dripSz = (2f + (1f - t) * 2.5f) * ds
      // Saliva string
      sb.strokeLine(dripX, dripStartY, dripX, dripY, 0.5f, 0.6f, 0.55f, 0.45f, 0.2f * (1f - t) * p)
      // Drip droplet
      sb.fillOval(dripX, dripY, dripSz, dripSz * 1.4f, 0.7f, 0.65f, 0.55f, 0.4f * (1f - t) * p, 6)
      sb.fillOval(dripX - dripSz * 0.15f, dripY - dripSz * 0.3f, dripSz * 0.35f, dripSz * 0.3f,
        0.9f, 0.9f, 0.85f, 0.25f * (1f - t) * p, 4)
    ; i += 1 } }

    // Sparkle stars
    { var i = 0; while (i < 3) {
      val starPhase = ((phase * 0.5 + i * 0.33) % 1.0).toFloat
      val starAngle = phase * 1.3 + i * Math.PI * 2 / 3
      val starDist = (16f + starPhase * 14f) * ds
      val starX = sx + Math.cos(starAngle).toFloat * starDist
      val starY = sy + Math.sin(starAngle).toFloat * starDist * 0.55f
      drawSparkleStar(starX, starY, 4.5f * (1f - starPhase * 0.5f) * ds,
        0.5f, 0.15f, 0.2f, 0.4f * (1f - starPhase) * p, sb, phase * 2 + i)
    ; i += 1 } }

    drawChargeCrackle(sx, sy, 20f * ds, 0.22f, 0.06f, 0.1f, p, sb, phase, proj.chargeLevel)
    drawReturnGhosts(sx, sy, 20f * ds, dr, dg, db, p, sb, proj)
  }

  /** Scythe - massive dark spinning blade with death effects */
  private def drawScythe(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val spin = tick * 0.3 + proj.id * 2.3
    val phase = (tick + proj.id * 31) * 0.35
    computeAllDynamics(proj, 0.7f, 0.72f, 0.78f, phase)
    val p = (0.9 + 0.1 * Math.sin(spin * 2 * _stPulseMult)).toFloat * dynAlpha
    val ds = dynScale
    val bladeR = 42f * ds
    val startAngle = spin
    val arcLen = Math.PI * 0.75
    val segs = 12
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy

    // Dark mist halo
    sb.fillOvalSoft(sx, sy, 36f * ds * dynGlow, 27.4f * ds * dynGlow,
      0.1f, 0.05f, 0.15f, 0.18f * p, 0f, 18)

    // 3 motion blur ghost copies (trailing)
    { var ghost = 0; while (ghost < 3) {
      val ghostSpin = startAngle - (ghost + 1) * 0.25
      val gAlpha = 0.2f * (1f - ghost * 0.25f) * p
      val gR = bladeR * (1f - ghost * 0.05f);
      { var i = 0; while (i <= segs) {
        val a = ghostSpin + arcLen * i / segs
        _scyXs(i) = (sx + Math.cos(a).toFloat * gR)
        _scyYs(i) = (sy + Math.sin(a).toFloat * gR * 0.6f)
      ; i += 1 } }
      { var i = 0; while (i <= segs) {
        val a = ghostSpin + arcLen * (segs - i) / segs
        _scyXs(segs + 1 + i) = (sx + Math.cos(a).toFloat * gR * 0.4f)
        _scyYs(segs + 1 + i) = (sy + Math.sin(a).toFloat * gR * 0.24f)
      ; i += 1 } }
      sb.fillPolygon(_scyXs, _scyYs, segs * 2 + 2, 0.4f, 0.42f, 0.48f, gAlpha)
    ; ghost += 1 } }

    // Main blade geometry
    { var i = 0; while (i <= segs) {
      val a = startAngle + arcLen * i / segs
      _scyXs(i) = (sx + Math.cos(a).toFloat * bladeR)
      _scyYs(i) = (sy + Math.sin(a).toFloat * bladeR * 0.6f)
    ; i += 1 } }
    { var i = 0; while (i <= segs) {
      val a = startAngle + arcLen * (segs - i) / segs
      _scyXs(segs + 1 + i) = (sx + Math.cos(a).toFloat * bladeR * 0.4f)
      _scyYs(segs + 1 + i) = (sy + Math.sin(a).toFloat * bladeR * 0.24f)
    ; i += 1 } }

    // Bold dark outline on blade
    sb.strokePolygon(_scyXs, _scyYs, segs * 2 + 2, 3.5f, 0.08f, 0.06f, 0.05f, 0.8f * p)
    // Steel body (3-layer fill)
    sb.fillPolygon(_scyXs, _scyYs, segs * 2 + 2, 0.55f, 0.57f, 0.62f, 0.85f * p);
    // Inner highlight stripe (brighter)
    { var i = 0; while (i <= segs) {
      val a = startAngle + arcLen * i / segs
      _scyXs(i) = (sx + Math.cos(a).toFloat * bladeR * 0.82f)
      _scyYs(i) = (sy + Math.sin(a).toFloat * bladeR * 0.82f * 0.6f)
    ; i += 1 } }
    { var i = 0; while (i <= segs) {
      val a = startAngle + arcLen * (segs - i) / segs
      _scyXs(segs + 1 + i) = (sx + Math.cos(a).toFloat * bladeR * 0.52f)
      _scyYs(segs + 1 + i) = (sy + Math.sin(a).toFloat * bladeR * 0.52f * 0.6f)
    ; i += 1 } }
    sb.fillPolygon(_scyXs, _scyYs, segs * 2 + 2, 0.72f, 0.74f, 0.8f, 0.5f * p)

    // Bright outer edge gleam
    { var j = 0; while (j < segs) {
      val a1 = startAngle + arcLen * j / segs
      val a2 = startAngle + arcLen * (j + 1) / segs
      sb.strokeLine((sx + Math.cos(a1).toFloat * bladeR), (sy + Math.sin(a1).toFloat * bladeR * 0.6f),
        (sx + Math.cos(a2).toFloat * bladeR), (sy + Math.sin(a2).toFloat * bladeR * 0.6f),
        2.5f, 0.92f, 0.93f, 0.96f, 0.85f * p)
    ; j += 1 } }

    // Handle with wrapped grip detail
    val ha = startAngle + arcLen + 0.3
    val handleEndX = sx + Math.cos(ha).toFloat * 24f * ds
    val handleEndY = sy + Math.sin(ha).toFloat * 15f * ds
    // Handle outline
    sb.strokeLine(sx, sy, handleEndX, handleEndY, 6f * ds, 0.1f, 0.08f, 0.05f, 0.8f * p)
    // Handle body
    sb.strokeLine(sx, sy, handleEndX, handleEndY, 4f * ds, 0.48f, 0.38f, 0.28f, 0.88f * p);
    // Grip wraps (3 bands)
    { var w = 0; while (w < 3) {
      val wt = 0.25f + w * 0.25f
      val wx = sx + (handleEndX - sx) * wt
      val wy = sy + (handleEndY - sy) * wt
      val perpX2 = -(handleEndY - sy) / (24f * ds) * 3f * ds
      val perpY2 = (handleEndX - sx) / (24f * ds) * 3f * ds
      sb.strokeLine(wx - perpX2, wy - perpY2, wx + perpX2, wy + perpY2, 1.5f * ds,
        0.25f, 0.2f, 0.15f, 0.5f * p)
    ; w += 1 } }
    // Handle highlight
    sb.strokeLine(sx, sy, handleEndX, handleEndY, 1.2f * ds, 0.6f, 0.5f, 0.4f, 0.35f * p)

    // Spinning energy arcs
    { var arc = 0; while (arc < 3) {
      val arcAngle = spin * 2 + arc * Math.PI * 2 / 3
      val arcR2 = bladeR * 0.6f
      val arcA = (0.3 + 0.2 * Math.sin(phase * 3 + arc * 2.1)).toFloat * p
      val ax0 = sx + Math.cos(arcAngle).toFloat * arcR2 * 0.3f
      val ay0 = sy + Math.sin(arcAngle).toFloat * arcR2 * 0.3f * 0.6f
      val ax1 = sx + Math.cos(arcAngle).toFloat * arcR2
      val ay1 = sy + Math.sin(arcAngle).toFloat * arcR2 * 0.6f
      sb.strokeLineSoft(ax0, ay0, ax1, ay1, 3f * ds, 0.5f, 0.3f, 0.7f, arcA * 0.4f)
      sb.strokeLine(ax0, ay0, ax1, ay1, 1.2f * ds, 0.65f, 0.45f, 0.85f, arcA)
    ; arc += 1 } }

    // Death particle trail (10 particles)
    { var i = 0; while (i < 10) {
      val t = ((tick * 0.06 + i * 0.1 + proj.id * 0.13) % 1.0).toFloat
      val dAngle = spin + i * Math.PI * 2 / 10
      val dDist = bladeR * (0.3f + t * 0.4f)
      val dx2 = sx + Math.cos(dAngle).toFloat * dDist + Math.sin(phase * 2 + i * 1.7).toFloat * 4f * ds
      val dy2 = sy + Math.sin(dAngle).toFloat * dDist * 0.6f - t * 8f * ds
      val dSz = (3f + (1f - t) * 3.5f) * ds
      sb.fillOval(dx2, dy2, dSz, dSz * 0.7f, 0.15f, 0.08f, 0.22f, 0.4f * (1f - t) * p, 6)
    ; i += 1 } }

    // Impact ring at center
    val ringP2 = ((phase * 0.35) % 1.0).toFloat
    val impR = (6f + ringP2 * 18f) * ds
    sb.strokeOval(sx, sy, impR, impR * 0.6f, 1.8f * (1f - ringP2 * 0.5f),
      0.6f, 0.55f, 0.7f, 0.3f * (1f - ringP2) * p, 10)

    // Sparkle stars
    { var i = 0; while (i < 3) {
      val starPhase = ((phase * 0.5 + i * 0.33) % 1.0).toFloat
      val starAngle = phase * 1.3 + i * Math.PI * 2 / 3
      val starDist = (16f + starPhase * 16f) * ds
      val starX = sx + Math.cos(starAngle).toFloat * starDist
      val starY = sy + Math.sin(starAngle).toFloat * starDist * 0.55f
      drawSparkleStar(starX, starY, 5f * (1f - starPhase * 0.5f) * ds,
        0.8f, 0.8f, 0.9f, 0.45f * (1f - starPhase) * p, sb, phase * 2 + i)
    ; i += 1 } }

    drawChargeCrackle(sx, sy, 24f * ds, 0.7f, 0.72f, 0.78f, p, sb, phase, proj.chargeLevel)
    drawReturnGhosts(sx, sy, 24f * ds, 0.7f, 0.72f, 0.78f, p, sb, proj)
  }

  /** Reap - massive death arc */
  private def drawReap(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 31) * 0.4
    computeAllDynamics(proj, 0.55f, 0.3f, 0.8f, phase)
    val p = (0.85 + 0.15 * Math.sin(phase * _stPulseMult)).toFloat * dynAlpha
    val ds = dynScale
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val baseAngle = Math.atan2(ndy, ndx) + Math.sin(phase * 2) * 0.3
    val arcR = 50f * ds; val arcLen = Math.PI * 1.2

    // Dark aura
    sb.fillOvalSoft(sx, sy, arcR * 1.1f * dynGlow, arcR * 0.7f * dynGlow, 0.08f, 0f, 0.12f, 0.25f * p, 0f, 16)

    // Expanding slash arcs
    var slash = 0; while (slash < 3) {
      val sp = ((phase * 0.5 + slash * 0.15) % 1.0).toFloat
      val slashR = arcR * (0.5f + sp * 0.55f)
      val slashA = 0.5f * (1f - sp * 0.6f) * p
      val segs2 = 14
      var i = 0; while (i < segs2) {
        val a1 = baseAngle - arcLen / 2 + arcLen * i / segs2
        val a2 = baseAngle - arcLen / 2 + arcLen * (i + 1) / segs2
        sb.strokeLine((sx + Math.cos(a1).toFloat * slashR), (sy + Math.sin(a1).toFloat * slashR * 0.5f),
          (sx + Math.cos(a2).toFloat * slashR), (sy + Math.sin(a2).toFloat * slashR * 0.5f),
          5f * (1f - sp * 0.4f), 0.35f, 0.12f, 0.5f, slashA)
      ; i += 1 }
    ; slash += 1 }

    // Bright spectral edge
    val segs = 14
    var i = 0; while (i < segs) {
      val a1 = baseAngle - arcLen / 2 + arcLen * i / segs
      val a2 = baseAngle - arcLen / 2 + arcLen * (i + 1) / segs
      sb.strokeLine((sx + Math.cos(a1).toFloat * arcR), (sy + Math.sin(a1).toFloat * arcR * 0.5f),
        (sx + Math.cos(a2).toFloat * arcR), (sy + Math.sin(a2).toFloat * arcR * 0.5f), 3.5f, 0.8f, 0.55f, 1f, 0.8f * p)
    ; i += 1 }

    // Soul wisps
    { var i = 0; while (i < 8) {
      val wAngle = baseAngle - arcLen / 2 + arcLen * (i + 0.5) / 8
      val wPhase = ((tick * 0.05f + i * 0.125f) % 1.0f)
      val wDist = arcR * (1f - wPhase * 0.5f)
      sb.fillOval((sx + Math.cos(wAngle).toFloat * wDist), (sy + Math.sin(wAngle).toFloat * wDist * 0.5f),
        4f * ds, 3f * ds, 0.55f, 0.3f, 0.8f, 0.5f * wPhase * p, 6)
    ; i += 1 } }
    drawChargeCrackle(sx, sy, arcR * 0.5f, 0.55f, 0.3f, 0.8f, p, sb, phase, proj.chargeLevel)
  }

  /** Shadow Bolt - dark void mass with 8 varied tendrils, void ripples, swirling core and glowing eyes */
  private def drawShadowBolt(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 43) * 0.35
    computeAllDynamics(proj, 0.2f, 0.02f, 0.35f, phase)
    val p = (0.85 + 0.15 * Math.sin(phase * _stPulseMult)).toFloat * dynAlpha
    val ds = dynScale
    val bc = _chgBright
    val w1 = Math.sin(phase * 2.3).toFloat * 4 * ds
    val w2 = Math.cos(phase * 1.7).toFloat * 3 * ds

    // Void aura
    sb.fillOvalSoft(sx + w1, sy + w2, 24.5f * ds * dynGlow, 18.7f * ds * dynGlow, 0.12f, 0f, 0.18f, 0.3f * p, 0f, 14)

    // Void ripple — 2 expanding dark rings from center
    var vr = 0; while (vr < 2) {
      val vrP = ((phase * 0.4 + vr * 0.5) % 1.0).toFloat
      val vrR = (6f + vrP * 22f) * ds
      sb.strokeOval(sx, sy, vrR, vrR * 0.6f, 2f * (1f - vrP), 0.15f, 0.02f, 0.25f,
        0.25f * (1f - vrP) * p, 10)
    ; vr += 1 }

    // 8 shadow tendrils with varying thickness (some thick, some wispy)
    var i = 0; while (i < 8) {
      val angle = phase * 1.2 + i * Math.PI * 2 / 8
      val tLen = (22f + Math.sin(phase * 2.5 + i * 1.9).toFloat * 8) * ds
      val ex = sx + Math.cos(angle).toFloat * tLen
      val ey = sy + Math.sin(angle).toFloat * tLen * 0.5f
      val thick = (if (i % 3 == 0) 5f else if (i % 3 == 1) 3.5f else 2f) * ds
      sb.strokeLine(sx, sy, ex, ey, thick, 0.2f, 0.02f, 0.35f, 0.4f * p)
      sb.strokeLine(sx, sy, ex, ey, thick * 0.5f, 0.08f, 0f, 0.15f, 0.65f * p)
      // Curling extension at tip
      val curlAngle = angle + Math.sin(phase * 3 + i * 2.1) * 0.8
      val curlX = ex + Math.cos(curlAngle).toFloat * 7f * ds
      val curlY = ey + Math.sin(curlAngle).toFloat * 4f * ds
      sb.strokeLine(ex, ey, curlX, curlY, thick * 0.4f, 0.15f, 0.01f, 0.28f, 0.3f * p)
    ; i += 1 }

    // Dark mass — bigger with bold outline
    sb.strokeOval(sx, sy, 22f * ds, 16f * ds, outlineW(22f * ds), 0.02f, 0f, 0.04f, 0.85f * p, 16)
    sb.fillOval(sx, sy, 20f * ds, 15f * ds, 0.06f, 0f, 0.1f, 0.95f * p, 16)

    // Inner swirling dark particles — more
    var sp = 0; while (sp < 6) {
      val spAngle = phase * 2.8 + sp * Math.PI / 3
      val spDist = (6f + Math.sin(phase * 1.5 + sp * 2.1).toFloat * 4f) * ds
      val spx = sx + Math.cos(spAngle).toFloat * spDist
      val spy = sy + Math.sin(spAngle).toFloat * spDist * 0.6f
      sb.fillOval(spx, spy, 3f * ds, 2.5f * ds, 0.02f, 0f, 0.05f, 0.75f * p, 6)
    ; sp += 1 }

    // Black center — whitens toward the core at high charge
    sb.fillOval(sx, sy, 7f * ds, 5.5f * ds, bc * 0.8f, bc * 0.5f, bc, 0.98f * p, 10)

    // Larger purple eyes with bright glow — more expressive
    val eyePulse1 = (0.4 + 0.6 * Math.sin(phase * 4)).toFloat
    val eyePulse2 = (0.4 + 0.6 * Math.sin(phase * 4 + 1.2)).toFloat
    // Eyes are placed symmetrically about the core; the old -6/+7 pair sat the face
    // half a pixel off-centre, which showed up as a slight leer at large sizes.
    val eyeDX = 6.5f * ds; val eyeDY = 3f * ds
    // Glow halos
    sb.fillOvalSoft(sx - eyeDX, sy - eyeDY, 12f * ds, 9f * ds, 0.5f, 0.1f, 0.8f, 0.2f * eyePulse1 * p, 0f, 10)
    sb.fillOvalSoft(sx + eyeDX, sy - eyeDY, 12f * ds, 9f * ds, 0.5f, 0.1f, 0.8f, 0.2f * eyePulse2 * p, 0f, 10)
    // Eye outline
    sb.strokeOval(sx - eyeDX, sy - eyeDY, 5f * ds, 4f * ds, 1.5f, 0f, 0f, 0f, 0.8f * p, 8)
    sb.strokeOval(sx + eyeDX, sy - eyeDY, 5f * ds, 4f * ds, 1.5f, 0f, 0f, 0f, 0.8f * p, 8)
    // Eyes — bigger and brighter
    sb.fillOval(sx - eyeDX, sy - eyeDY, 4.5f * ds, 3.5f * ds, 0.7f, 0.2f, 1f, 0.85f * eyePulse1 * p, 8)
    sb.fillOval(sx + eyeDX, sy - eyeDY, 4.5f * ds, 3.5f * ds, 0.7f, 0.2f, 1f, 0.85f * eyePulse2 * p, 8)
    // Eye highlights — both catch the shared key light from the same side
    sb.fillOval(sx - eyeDX + KEY_LIGHT_X * 1.6f * ds, sy - eyeDY + KEY_LIGHT_Y * 1.3f * ds,
      1.5f * ds, 1.2f * ds, 1f, 1f, 1f, 0.4f * eyePulse1 * p, 4)
    sb.fillOval(sx + eyeDX + KEY_LIGHT_X * 1.6f * ds, sy - eyeDY + KEY_LIGHT_Y * 1.3f * ds,
      1.5f * ds, 1.2f * ds, 1f, 1f, 1f, 0.4f * eyePulse2 * p, 4)
    drawChargeCrackle(sx, sy, 20f * ds, 0.35f, 0.05f, 0.55f, p, sb, phase, proj.chargeLevel)
    drawReturnGhosts(sx, sy, 18f * ds, 0.18f, 0.03f, 0.3f, p, sb, proj)
  }

  /** Inferno Blast — CARTOONISH: massive fire vortex with bold spirals and sparkle stars */
  private def drawInfernoBlast(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 23) * 0.4
    computeAllDynamics(proj, 1f, 0.4f, 0.02f, phase)
    val p = (0.6 + 0.4 * Math.sin(phase * _stPulseMult)).toFloat * dynAlpha
    val ds = dynScale
    val bc = _chgBright
    val r = 45f * ds

    // Heat halo. 3x the 45px vortex radius put a 135px wash over an already
    // large effect; 2x plus the clamped dynGlow still reads as furnace heat.
    sb.fillOvalSoft(sx, sy, r * 2f * dynGlow, r * 1.6f * dynGlow, 1f, 0.2f, 0f, 0.28f * p, 0f, 20)

    // 5 spinning fire spirals — bigger and bolder
    var arm = 0; while (arm < 5) {
      val segs2 = 12
      var i = 0; while (i < segs2) {
        val t = i.toFloat / segs2
        val spiralAngle = phase * 2.5 + t * Math.PI * 2.5 + arm * Math.PI * 2 / 5
        val spiralR = r * t
        val px = sx + Math.cos(spiralAngle).toFloat * spiralR
        val py = sy + Math.sin(spiralAngle).toFloat * spiralR * 0.5f
        val s = 8f + t * 10f
        // Dark outline
        sb.fillOval(px, py, s + 2f, (s + 2f) * 0.65f, 0.15f, 0.02f, 0f, 0.4f * (1f - t * 0.3f) * p, 8)
        sb.fillOval(px, py, s, s * 0.65f, 1f, Math.max(0f, 0.55f * (1f - t)), 0f, 0.65f * (1f - t * 0.35f) * p, 8)
      ; i += 1 }
    ; arm += 1 }

    // Fire core — bigger with bold outline
    sb.strokeOval(sx, sy, r * 0.52f, r * 0.4f, outlineW(r * 0.52f), 0.15f, 0.02f, 0f, 0.75f * p, 16)
    sb.fillOval(sx, sy, r * 0.5f, r * 0.38f, 0.95f, 0.4f, 0.02f, 0.9f * p, 16)
    sb.fillOval(sx, sy, r * 0.3f, r * 0.23f, 1f, 0.65f, 0.08f, 0.95f * p, 14)
    // Cartoon highlight
    sb.fillOval(sx + KEY_LIGHT_X * 8f * ds, sy + KEY_LIGHT_Y * 5f * ds, 6f * ds, 4f * ds, 1f, 1f, 0.8f, 0.4f * p, 8)
    sb.fillOval(sx, sy, 8f * ds, 6.5f * ds, 1f, 1f, mix(0.7f, 1f, bc), 0.95f * p, 10)

    // More embers — bigger
    var i = 0; while (i < 12) {
      val angle = phase * 1.5 + i * Math.PI / 6
      val fl = r + Math.sin(phase * 3 + i * 2).toFloat * 16 * ds
      sb.fillOval(sx + Math.cos(angle).toFloat * fl,
        sy + Math.sin(angle).toFloat * fl * 0.5f - Math.abs(Math.sin(phase * 4 + i)).toFloat * 10 * ds,
        6f * ds, 4.5f * ds, 1f, 0.5f, 0f, 0.55f * p, 8)
    ; i += 1 }

    // Sparkle stars orbiting
    { var i = 0; while (i < 4) {
      val starAngle = phase * 0.8 + i * Math.PI / 2
      val starDist = r * 0.7f
      val starX = sx + Math.cos(starAngle).toFloat * starDist
      val starY = sy + Math.sin(starAngle).toFloat * starDist * 0.5f
      drawSparkleStar(starX, starY, 6f * ds, 1f, 0.9f, 0.4f, 0.4f * p, sb, phase * 2 + i)
    ; i += 1 } }
    drawChargeCrackle(sx, sy, r * 0.5f, 1f, 0.4f, 0.02f, p, sb, phase, proj.chargeLevel)
  }

  /** Jaw — predatory chomping shark jaws with smooth silhouette, bite animation, teeth, eyes, wake */
  private def drawJaw(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    beamTip(sx, sy, proj, 7f)
    val tipX = _tipX; val tipY = _tipY
    val phase = (tick + proj.id * 17) * 0.4
    computeAllDynamics(proj, 0.42f, 0.47f, 0.55f, phase)
    val p = (0.9 + 0.1 * Math.sin(phase * _stPulseMult)).toFloat * dynAlpha
    val ds = dynScale * 1.3f // this one reads far below the roster's scale unscaled
    val chompCycle = Math.sin(phase * 3).toFloat
    val chomp = (Math.abs(chompCycle) * 0.45f + 0.55f).toFloat
    val dx = tipX - sx; val dy = tipY - sy
    val len = Math.sqrt(dx * dx + dy * dy).toFloat
    if (len < 1) return
    val nx = dx / len; val ny = dy / len
    val perpX = -ny; val perpY = nx

    // V-shaped water wake (8 particles per side)
    { var i = 0; while (i < 8) {
      val t = ((tick * 0.06 + i * 0.125 + proj.id * 0.13) % 1.0).toFloat
      val wakeSpread = 5f + t * 22f
      val wakeAlpha = 0.35f * (1f - t) * p
      val wakeX = tipX - nx * (t * 40f + 15f)
      val wakeY = tipY - ny * (t * 40f + 15f)
      // Left wake particle with outline
      val lwx = wakeX + perpX * wakeSpread * ds; val lwy = wakeY + perpY * wakeSpread * ds * 0.6f
      sb.fillOval(lwx, lwy, 6f * ds, 3.5f * ds, 0.2f, 0.4f, 0.65f, wakeAlpha * 0.5f, 6)
      sb.fillOval(lwx, lwy, 5f * ds, 3f * ds, 0.5f, 0.7f, 0.9f, wakeAlpha, 6)
      // Right wake particle with outline
      val rwx = wakeX - perpX * wakeSpread * ds; val rwy = wakeY - perpY * wakeSpread * ds * 0.6f
      sb.fillOval(rwx, rwy, 6f * ds, 3.5f * ds, 0.2f, 0.4f, 0.65f, wakeAlpha * 0.5f, 6)
      sb.fillOval(rwx, rwy, 5f * ds, 3f * ds, 0.5f, 0.7f, 0.9f, wakeAlpha, 6)
    ; i += 1 } }

    // Dense central water bubble trail (8 bubbles)
    { var i = 0; while (i < 8) {
      val t = ((tick * 0.05 + i * 0.125 + proj.id * 0.11) % 1.0).toFloat
      val bx = tipX - nx * (t * 45f + 10f) + Math.sin(phase * 2 + i * 1.7).toFloat * 3f * ds
      val by = tipY - ny * (t * 45f + 10f) + Math.cos(phase * 2 + i * 1.3).toFloat * 2f * ds
      val bSz = (4f + (1f - t) * 4f) * ds
      sb.strokeOval(bx, by, bSz, bSz * 0.85f, 0.8f, 0.25f, 0.45f, 0.7f, 0.15f * (1f - t) * p, 6)
      sb.fillOval(bx, by, bSz * 0.9f, bSz * 0.75f, 0.4f, 0.62f, 0.85f, 0.3f * (1f - t) * p, 6)
      sb.fillOval(bx - bSz * 0.2f, by - bSz * 0.25f, bSz * 0.3f, bSz * 0.25f, 0.8f, 0.92f, 1f, 0.2f * (1f - t) * p, 4)
    ; i += 1 } }

    // Underwater shadow below
    val shadowX = tipX - nx * 6f * ds + perpX * 2f * ds
    val shadowY = tipY - ny * 6f * ds + perpY * 2f * ds + 8f * ds
    sb.fillOval(shadowX, shadowY, 28f * ds, 10f * ds, 0f, 0f, 0.08f, 0.2f * p, 12)

    val jawLen = 26f * p * ds
    val jawW = 20f * p * chomp * ds

    // Smooth head/body silhouette with 3-layer shading
    val bodyX = tipX - nx * jawLen * 0.6f; val bodyY = tipY - ny * jawLen * 0.6f
    // Dark outline oval
    sb.strokeOval(bodyX, bodyY, jawLen * 0.7f, jawLen * 0.45f, 3.5f * ds,
      0.1f, 0.12f, 0.14f, 0.85f * p, 16)
    // Body fill
    sb.fillOval(bodyX, bodyY, jawLen * 0.65f, jawLen * 0.42f, 0.4f, 0.46f, 0.53f, 0.93f * p, 16)
    // Top highlight
    sb.fillOval(bodyX + nx * jawLen * 0.03f, bodyY + ny * jawLen * 0.03f - 2.5f * ds,
      jawLen * 0.48f, jawLen * 0.28f, 0.5f, 0.57f, 0.65f, 0.5f * p, 12)

    // Upper jaw polygon (4-point) with outline + body + inner shading
    _polyXs4(0) = tipX + nx * 5f * ds
    _polyYs4(0) = tipY + ny * 5f * ds
    _polyXs4(1) = tipX - nx * jawLen * 0.12f + perpX * jawW * 1.15f
    _polyYs4(1) = tipY - ny * jawLen * 0.12f + perpY * jawW * 1.15f
    _polyXs4(2) = tipX - nx * jawLen * 0.55f + perpX * jawW * 0.25f
    _polyYs4(2) = tipY - ny * jawLen * 0.55f + perpY * jawW * 0.25f
    _polyXs4(3) = tipX - nx * jawLen * 0.55f
    _polyYs4(3) = tipY - ny * jawLen * 0.55f
    // Jaw outline (dark)
    sb.fillPolygon(_polyXs4, _polyYs4, 4, 0.12f, 0.14f, 0.16f, 0.88f * p)
    // Jaw body
    _polyXs4(0) = tipX + nx * 4f * ds
    _polyXs4(1) = tipX - nx * jawLen * 0.14f + perpX * jawW * 1.05f
    _polyXs4(2) = tipX - nx * jawLen * 0.53f + perpX * jawW * 0.22f
    _polyXs4(3) = tipX - nx * jawLen * 0.53f
    _polyYs4(0) = tipY + ny * 4f * ds
    _polyYs4(1) = tipY - ny * jawLen * 0.14f + perpY * jawW * 1.05f
    _polyYs4(2) = tipY - ny * jawLen * 0.53f + perpY * jawW * 0.22f
    _polyYs4(3) = tipY - ny * jawLen * 0.53f
    sb.fillPolygon(_polyXs4, _polyYs4, 4, 0.44f, 0.5f, 0.56f, 0.92f * p)
    // Inner jaw shading (lighter stripe)
    _polyXs4(0) = tipX + nx * 3f * ds
    _polyXs4(1) = tipX - nx * jawLen * 0.16f + perpX * jawW * 0.85f
    _polyXs4(2) = tipX - nx * jawLen * 0.48f + perpX * jawW * 0.18f
    _polyXs4(3) = tipX - nx * jawLen * 0.48f
    _polyYs4(0) = tipY + ny * 3f * ds
    _polyYs4(1) = tipY - ny * jawLen * 0.16f + perpY * jawW * 0.85f
    _polyYs4(2) = tipY - ny * jawLen * 0.48f + perpY * jawW * 0.18f
    _polyYs4(3) = tipY - ny * jawLen * 0.48f
    sb.fillPolygon(_polyXs4, _polyYs4, 4, 0.5f, 0.56f, 0.62f, 0.4f * p)

    // Lower jaw polygon with outline + body + inner shading
    _polyXs4(0) = tipX + nx * 5f * ds
    _polyYs4(0) = tipY + ny * 5f * ds
    _polyXs4(1) = tipX - nx * jawLen * 0.12f - perpX * jawW * 1.15f
    _polyYs4(1) = tipY - ny * jawLen * 0.12f - perpY * jawW * 1.15f
    _polyXs4(2) = tipX - nx * jawLen * 0.55f - perpX * jawW * 0.25f
    _polyYs4(2) = tipY - ny * jawLen * 0.55f - perpY * jawW * 0.25f
    _polyXs4(3) = tipX - nx * jawLen * 0.55f
    _polyYs4(3) = tipY - ny * jawLen * 0.55f
    sb.fillPolygon(_polyXs4, _polyYs4, 4, 0.12f, 0.14f, 0.16f, 0.88f * p)
    _polyXs4(0) = tipX + nx * 4f * ds; _polyYs4(0) = tipY + ny * 4f * ds
    _polyXs4(1) = tipX - nx * jawLen * 0.14f - perpX * jawW * 1.05f
    _polyYs4(1) = tipY - ny * jawLen * 0.14f - perpY * jawW * 1.05f
    _polyXs4(2) = tipX - nx * jawLen * 0.53f - perpX * jawW * 0.22f
    _polyYs4(2) = tipY - ny * jawLen * 0.53f - perpY * jawW * 0.22f
    _polyXs4(3) = tipX - nx * jawLen * 0.53f; _polyYs4(3) = tipY - ny * jawLen * 0.53f
    sb.fillPolygon(_polyXs4, _polyYs4, 4, 0.4f, 0.45f, 0.5f, 0.92f * p)
    _polyXs4(0) = tipX + nx * 3f * ds; _polyYs4(0) = tipY + ny * 3f * ds
    _polyXs4(1) = tipX - nx * jawLen * 0.16f - perpX * jawW * 0.85f
    _polyYs4(1) = tipY - ny * jawLen * 0.16f - perpY * jawW * 0.85f
    _polyXs4(2) = tipX - nx * jawLen * 0.48f - perpX * jawW * 0.18f
    _polyYs4(2) = tipY - ny * jawLen * 0.48f - perpY * jawW * 0.18f
    _polyXs4(3) = tipX - nx * jawLen * 0.48f; _polyYs4(3) = tipY - ny * jawLen * 0.48f
    sb.fillPolygon(_polyXs4, _polyYs4, 4, 0.46f, 0.52f, 0.57f, 0.4f * p)

    // Blood-red gum lines glowing
    val gumStart = tipX - nx * jawLen * 0.03f; val gumStartY2 = tipY - ny * jawLen * 0.03f
    val gumEnd = tipX - nx * jawLen * 0.48f; val gumEndY2 = tipY - ny * jawLen * 0.48f
    // Upper gum — glow + line
    sb.strokeLineSoft(gumStart + perpX * jawW * 0.65f, gumStartY2 + perpY * jawW * 0.65f,
      gumEnd + perpX * jawW * 0.12f, gumEndY2 + perpY * jawW * 0.12f,
      5f * ds, 0.8f, 0.1f, 0.08f, 0.2f * p)
    sb.strokeLine(gumStart + perpX * jawW * 0.65f, gumStartY2 + perpY * jawW * 0.65f,
      gumEnd + perpX * jawW * 0.12f, gumEndY2 + perpY * jawW * 0.12f,
      2.5f * ds, 0.85f, 0.15f, 0.1f, 0.6f * p)
    // Lower gum — glow + line
    sb.strokeLineSoft(gumStart - perpX * jawW * 0.65f, gumStartY2 - perpY * jawW * 0.65f,
      gumEnd - perpX * jawW * 0.12f, gumEndY2 - perpY * jawW * 0.12f,
      5f * ds, 0.8f, 0.1f, 0.08f, 0.2f * p)
    sb.strokeLine(gumStart - perpX * jawW * 0.65f, gumStartY2 - perpY * jawW * 0.65f,
      gumEnd - perpX * jawW * 0.12f, gumEndY2 - perpY * jawW * 0.12f,
      2.5f * ds, 0.85f, 0.15f, 0.1f, 0.6f * p)

    // 8 individual sharp teeth per jaw with dark outline + white body + bright tip highlight
    { var i = 0; while (i < 8) {
      val t = (i + 0.5f) / 8f
      val toothBase = 0.06f + t * 0.44f
      val toothLen = (8f + Math.sin(i * 1.7 + proj.id * 0.3).toFloat * 2.5f) * p * ds
      val toothW = 2f * ds * (1f - t * 0.3f)
      // Upper jaw teeth
      val utx = tipX - nx * jawLen * toothBase + perpX * jawW * (1f - t * 0.7f) * 0.88f
      val uty = tipY - ny * jawLen * toothBase + perpY * jawW * (1f - t * 0.7f) * 0.88f
      val tipOffX = -perpX * toothLen; val tipOffY = -perpY * toothLen
      // Dark outline triangle
      _polyXs3(0) = utx - nx * toothW; _polyXs3(1) = utx + tipOffX; _polyXs3(2) = utx + nx * toothW
      _polyYs3(0) = uty - ny * toothW; _polyYs3(1) = uty + tipOffY; _polyYs3(2) = uty + ny * toothW
      sb.fillPolygon(_polyXs3, _polyYs3, 3, 0.15f, 0.15f, 0.12f, 0.85f * p)
      // White body (slightly smaller)
      _polyXs3(0) = utx - nx * toothW * 0.75f; _polyXs3(1) = utx + tipOffX * 0.95f + nx * 0.5f * ds; _polyXs3(2) = utx + nx * toothW * 0.75f
      _polyYs3(0) = uty - ny * toothW * 0.75f; _polyYs3(1) = uty + tipOffY * 0.95f + ny * 0.5f * ds; _polyYs3(2) = uty + ny * toothW * 0.75f
      sb.fillPolygon(_polyXs3, _polyYs3, 3, 0.94f, 0.94f, 0.9f, 0.92f * p)
      // Bright tip highlight
      sb.fillOval(utx + tipOffX * 0.75f, uty + tipOffY * 0.75f, 2.2f * ds, 2.2f * ds, 1f, 1f, 0.98f, 0.65f * p, 4)

      // Lower jaw teeth (mirror)
      val ltx = tipX - nx * jawLen * toothBase - perpX * jawW * (1f - t * 0.7f) * 0.88f
      val lty = tipY - ny * jawLen * toothBase - perpY * jawW * (1f - t * 0.7f) * 0.88f
      _polyXs3(0) = ltx - nx * toothW; _polyXs3(1) = ltx + perpX * toothLen; _polyXs3(2) = ltx + nx * toothW
      _polyYs3(0) = lty - ny * toothW; _polyYs3(1) = lty + perpY * toothLen; _polyYs3(2) = lty + ny * toothW
      sb.fillPolygon(_polyXs3, _polyYs3, 3, 0.15f, 0.15f, 0.12f, 0.85f * p)
      _polyXs3(0) = ltx - nx * toothW * 0.75f; _polyXs3(1) = ltx + perpX * toothLen * 0.95f + nx * 0.5f * ds; _polyXs3(2) = ltx + nx * toothW * 0.75f
      _polyYs3(0) = lty - ny * toothW * 0.75f; _polyYs3(1) = lty + perpY * toothLen * 0.95f + ny * 0.5f * ds; _polyYs3(2) = lty + ny * toothW * 0.75f
      sb.fillPolygon(_polyXs3, _polyYs3, 3, 0.94f, 0.94f, 0.9f, 0.92f * p)
      sb.fillOval(ltx + perpX * toothLen * 0.75f, lty + perpY * toothLen * 0.75f, 2.2f * ds, 2.2f * ds, 1f, 1f, 0.98f, 0.65f * p, 4)
    ; i += 1 } }

    // 2 glowing red eyes with glow halos, dark pupils, and angry eyebrow lines
    val eyePulse1 = (0.5f + 0.5f * Math.sin(phase * 4).toFloat)
    val eyePulse2 = (0.5f + 0.5f * Math.sin(phase * 4 + 1.2).toFloat);
    { var eye = -1; while (eye <= 1) {
      if (eye != 0) {
        val eyeX = tipX - nx * jawLen * 0.38f + perpX * 7f * eye.toFloat * ds
        val eyeY = tipY - ny * jawLen * 0.38f + perpY * 7f * eye.toFloat * ds - 3.5f * ds
        val ePulse = if (eye < 0) eyePulse1 else eyePulse2
        // Glow halo
        sb.fillOvalSoft(eyeX, eyeY, 10f * ds, 8f * ds, 1f, 0.12f, 0.05f, 0.3f * ePulse * p, 0f, 10)
        // Eye outline (dark)
        sb.strokeOval(eyeX, eyeY, 5f * ds, 3.5f * ds, 1.8f, 0.05f, 0.02f, 0.02f, 0.85f * p, 8)
        // Eye body
        sb.fillOval(eyeX, eyeY, 4.5f * ds, 3f * ds, 0.97f, 0.1f, 0.04f, 0.92f * ePulse * p, 8)
        // Dark pupil
        sb.fillOval(eyeX + nx * 1f * ds, eyeY + ny * 1f * ds, 2f * ds, 1.8f * ds, 0.15f, 0.02f, 0.02f, 0.9f * p, 6)
        // Bright pupil dot
        sb.fillOval(eyeX + nx * 0.5f * ds, eyeY + ny * 0.5f * ds - 0.5f * ds, 1f * ds, 1f * ds, 1f, 0.6f, 0.4f, 0.9f * p, 4)
        // Highlight
        sb.fillOval(eyeX - 1.2f * ds, eyeY - 1f * ds, 1.5f * ds, 1f * ds, 1f, 1f, 1f, 0.4f * ePulse * p, 4)
        // Angry eyebrow line
        val browStartX = eyeX - perpX * 5f * eye.toFloat * ds
        val browStartY = eyeY - perpY * 5f * eye.toFloat * ds - 2.5f * ds
        val browEndX = eyeX + perpX * 2f * eye.toFloat * ds
        val browEndY = eyeY + perpY * 2f * eye.toFloat * ds - 4f * ds
        sb.strokeLine(browStartX, browStartY, browEndX, browEndY, 2f * ds, 0.12f, 0.12f, 0.1f, 0.7f * p)
      }
    ; eye += 1 } }

    // Layered dorsal fin (outline, body, highlight, edge gleam)
    val finX = tipX - nx * jawLen * 0.65f; val finY = tipY - ny * jawLen * 0.65f - 13f * p * ds
    // Outline layer
    _polyXs3(0) = finX; _polyXs3(1) = finX + perpX * 6f * ds; _polyXs3(2) = finX - perpX * 6f * ds
    _polyYs3(0) = finY - 12f * p * ds; _polyYs3(1) = finY + 10f * ds; _polyYs3(2) = finY + 10f * ds
    sb.fillPolygon(_polyXs3, _polyYs3, 3, 0.1f, 0.12f, 0.14f, 0.8f * p)
    // Body layer
    _polyXs3(0) = finX; _polyXs3(1) = finX + perpX * 5f * ds; _polyXs3(2) = finX - perpX * 5f * ds
    _polyYs3(0) = finY - 11f * p * ds; _polyYs3(1) = finY + 9f * ds; _polyYs3(2) = finY + 9f * ds
    sb.fillPolygon(_polyXs3, _polyYs3, 3, 0.42f, 0.48f, 0.54f, 0.88f * p)
    // Highlight layer
    _polyXs3(0) = finX - perpX * 0.5f * ds; _polyXs3(1) = finX + perpX * 3f * ds; _polyXs3(2) = finX - perpX * 3f * ds
    _polyYs3(0) = finY - 8.5f * p * ds; _polyYs3(1) = finY + 5f * ds; _polyYs3(2) = finY + 5f * ds
    sb.fillPolygon(_polyXs3, _polyYs3, 3, 0.54f, 0.6f, 0.66f, 0.5f * p)
    // Edge gleam line
    sb.strokeLine(finX, finY - 11f * p * ds, finX + perpX * 4f * ds, finY + 7f * ds,
      1f, 0.65f, 0.72f, 0.78f, 0.3f * p)

    // Foam splash at nose
    val noseX = tipX + nx * 6f * ds; val noseY = tipY + ny * 6f * ds
    sb.fillOvalSoft(noseX, noseY, 8f * ds, 6f * ds, 0.7f, 0.85f, 1f, 0.25f * p, 0f, 8)
    sb.fillOval(noseX, noseY, 4f * ds, 3f * ds, 0.9f, 0.96f, 1f, 0.4f * p, 6)

    drawChargeCrackle(tipX, tipY, jawLen * 0.5f, 0.42f, 0.47f, 0.55f, p, sb, phase, proj.chargeLevel)
    drawReturnGhosts(tipX, tipY, jawLen * 0.4f, 0.42f, 0.47f, 0.55f, p, sb, proj)
  }

  // ═══════════════════════════════════════════════════════════════
  //  CHARACTER-SPECIFIC SPECIALIZED RENDERERS
  // ═══════════════════════════════════════════════════════════════


  /** Talon — curved 3-prong razor claw with slash trail */
  private def drawTalon(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 29) * 0.4
    computeAllDynamics(proj, 0.6f, 0.45f, 0.3f, phase)
    val p = (0.8 + 0.2 * Math.sin(phase * _stPulseMult)).toFloat * dynAlpha
    val dr = _evoR; val dg = _evoG; val db = _evoB
    val ds = dynScale * 1.3f // this one reads far below the roster's scale unscaled
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val perpX = -ndy; val perpY = ndx

    // Speed lines
    drawSpeedLines(sx, sy, ndx, ndy, 0.6f, 0.35f, 0.25f, 0.3f * p, sb, 6 + (_lifePct * 2).toInt, 35f * ds)

    // Slash mark ribbon trail (red-tinted)
    drawRibbonTrail(sx, sy, ndx, ndy, 0.8f, 0.3f, 0.2f, 0.35f * p, sb, tick, proj.id, 10, 50f * ds * dynTrail, 12f * ds, 2f)

    // Warm glow
    sb.fillOvalSoft(sx, sy, 32.4f * ds * dynGlow, 25.9f * ds * dynGlow, 0.7f, 0.35f, 0.2f, 0.22f * p, 0f, 18)

    // 3 curved claw prongs — each with dark outline and red tip
    { var c = -1; while (c <= 1) {
      val spreadAngle = c * 0.35
      val clawNdx = ndx * Math.cos(spreadAngle).toFloat - perpX * Math.sin(spreadAngle).toFloat
      val clawNdy = ndy * Math.cos(spreadAngle).toFloat - perpY * Math.sin(spreadAngle).toFloat
      val clawPerp = -clawNdy
      val clawPerpY = clawNdx
      val baseX = sx + perpX * c * 6f * ds; val baseY = sy + perpY * c * 6f * ds
      val tipDist = 28f * ds
      val curve = Math.sin(phase * 0.5 + c).toFloat * 3f * ds
      val midX = baseX + clawNdx * tipDist * 0.6f + clawPerp * curve
      val midY = baseY + clawNdy * tipDist * 0.6f + clawPerpY * curve
      val endX = baseX + clawNdx * tipDist + clawPerp * curve * 1.5f
      val endY = baseY + clawNdy * tipDist + clawPerpY * curve * 1.5f

      // Dark outline
      sb.strokeLine(baseX, baseY, midX, midY, 6f * ds, 0.12f, 0.08f, 0.06f, 0.85f * p)
      sb.strokeLine(midX, midY, endX, endY, 4f * ds, 0.12f, 0.08f, 0.06f, 0.85f * p)
      // Body
      sb.strokeLine(baseX, baseY, midX, midY, 4f * ds, dr, dg, db, 0.9f * p)
      sb.strokeLine(midX, midY, endX, endY, 2.5f * ds, dr, dg, db, 0.9f * p)
      // Bright core
      sb.strokeLine(baseX, baseY, midX, midY, 1.5f * ds, bright(0.6f), bright(0.45f), bright(0.3f), 0.5f * p)
      // Red tip
      sb.fillOval(endX, endY, 4f * ds, 3f * ds, 0.95f, 0.2f, 0.15f, 0.9f * p, 8)
      sb.fillOval(endX, endY, 2f * ds, 1.5f * ds, 1f, 0.5f, 0.3f, 0.7f * p, 6)
    ; c += 1 } }

    // Center knuckle/joint
    sb.strokeOval(sx, sy, 10f * ds, 8f * ds, 3f, 0.12f, 0.08f, 0.06f, 0.8f * p, 12)
    sb.fillOval(sx, sy, 9f * ds, 7f * ds, dr, dg, db, 0.92f * p, 12)
    sb.fillOval(sx, sy, 5f * ds, 4f * ds, bright(0.6f), bright(0.45f), bright(0.3f), 0.5f * p, 8)
    sb.fillOval(sx - 2f * ds, sy - 2f * ds, 3.5f * ds, 2.5f * ds, 1f, 1f, 1f, 0.3f * p, 6)

    // 3 slash mark trails behind
    { var s = 0; while (s < 3) {
      val st = ((tick * 0.06 + s * 0.15 + proj.id * 0.11) % 1.0).toFloat
      val slashX = sx - ndx * st * 40f * ds + perpX * (s - 1) * 8f * ds
      val slashY = sy - ndy * st * 40f * ds + perpY * (s - 1) * 8f * ds
      val slLen = 12f * (1f - st) * ds
      sb.strokeLine(slashX - perpX * slLen, slashY - perpY * slLen,
        slashX + perpX * slLen, slashY + perpY * slLen,
        2.5f * (1f - st), 0.9f, 0.25f, 0.15f, 0.4f * (1f - st) * p)
    ; s += 1 } }

    // Sparkle stars
    { var i = 0; while (i < 4) {
      val starPhase = ((phase * 0.5 + i * 0.25) % 1.0).toFloat
      val starAngle = phase * 1.2 + i * Math.PI * 2 / 4
      val starDist = (14f + starPhase * 16f) * ds
      val starX = sx + Math.cos(starAngle).toFloat * starDist
      val starY = sy + Math.sin(starAngle).toFloat * starDist * 0.55f
      drawSparkleStar(starX, starY, 4f * (1f - starPhase * 0.4f) * ds,
        0.9f, 0.5f, 0.3f, 0.45f * (1f - starPhase) * p, sb, phase * 2 + i)
    ; i += 1 } }

    drawChargeCrackle(sx, sy, 20f * ds, 0.6f, 0.45f, 0.3f, p, sb, phase, proj.chargeLevel)
    drawReturnGhosts(sx, sy, 20f * ds, dr, dg, db, p, sb, proj)
  }

  /** Blood Fang — two curved vampire fangs with dripping blood */
  private def drawBloodFang(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 37) * 0.35
    computeAllDynamics(proj, 0.85f, 0.12f, 0.1f, phase)
    val p = (0.85 + 0.15 * Math.sin(phase * _stPulseMult)).toFloat * dynAlpha
    val dr = _evoR; val dg = _evoG; val db = _evoB
    val ds = dynScale
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val perpX = -ndy; val perpY = ndx

    drawSpeedLines(sx, sy, ndx, ndy, 0.7f, 0.1f, 0.08f, 0.25f * p, sb, 5 + (_lifePct * 2).toInt, 30f * ds)
    drawRibbonTrail(sx, sy, ndx, ndy, 0.8f, 0.12f, 0.1f, 0.3f * p, sb, tick, proj.id, 10, 45f * ds * dynTrail, 9f * ds, 1.5f)

    // Dark crimson glow
    val haloPulse = 0.7f + 0.3f * Math.sin(phase * 2.0).toFloat
    sb.fillOvalSoft(sx, sy, 36f * ds * dynGlow * haloPulse, 28.8f * ds * dynGlow * haloPulse, 0.8f, 0.08f, 0.05f, 0.25f * p, 0f, 20)

    // Two curved fangs
    { var f = -1; while (f <= 1) {
      if (f != 0) {
        val fangBaseX = sx + perpX * f * 5f * ds - ndx * 4f * ds
        val fangBaseY = sy + perpY * f * 5f * ds - ndy * 4f * ds
        val fangTipX = sx + ndx * 24f * ds + perpX * f * 2f * ds
        val fangTipY = sy + ndy * 24f * ds + perpY * f * 2f * ds
        val curve = f * 4f * ds
        val midX = (fangBaseX + fangTipX) * 0.5f + perpX * curve
        val midY = (fangBaseY + fangTipY) * 0.5f + perpY * curve
        // Dark outline
        sb.strokeLine(fangBaseX, fangBaseY, midX, midY, 7f * ds, 0.15f, 0.02f, 0.02f, 0.85f * p)
        sb.strokeLine(midX, midY, fangTipX, fangTipY, 5f * ds, 0.15f, 0.02f, 0.02f, 0.85f * p)
        // Ivory body
        sb.strokeLine(fangBaseX, fangBaseY, midX, midY, 5f * ds, 0.92f, 0.88f, 0.82f, 0.9f * p)
        sb.strokeLine(midX, midY, fangTipX, fangTipY, 3f * ds, 0.92f, 0.88f, 0.82f, 0.9f * p)
        // Bright highlight
        sb.strokeLine(fangBaseX + perpX * f * -1f, fangBaseY + perpY * f * -1f,
          midX + perpX * f * -1f, midY + perpY * f * -1f, 1.5f * ds, 1f, 1f, 0.95f, 0.4f * p)
        // Blood-red tips
        sb.fillOval(fangTipX, fangTipY, 4f * ds, 3f * ds, 0.9f, 0.08f, 0.05f, 0.92f * p, 8)
        sb.fillOval(fangTipX, fangTipY, 2f * ds, 1.5f * ds, 1f, 0.3f, 0.2f, 0.7f * p, 6)
      }
    ; f += 2 } }

    // Center base (gum/jaw root)
    sb.strokeOval(sx - ndx * 4f * ds, sy - ndy * 4f * ds, 10f * ds, 8f * ds, 3f, 0.15f, 0.02f, 0.02f, 0.8f * p, 12)
    sb.fillOval(sx - ndx * 4f * ds, sy - ndy * 4f * ds, 9f * ds, 7f * ds, 0.6f, 0.08f, 0.08f, 0.9f * p, 12)
    sb.fillOval(sx - ndx * 4f * ds, sy - ndy * 4f * ds, 5f * ds, 4f * ds,
      mix(0.9f, 1f, _chgBright), mix(0.15f, 1f, _chgBright), mix(0.1f, 1f, _chgBright), 0.8f * p, 8)

    // Blood drips trailing
    { var i = 0; while (i < 8) {
      val t = ((tick * 0.05 + i * 0.125 + proj.id * 0.13) % 1.0).toFloat
      val dripX = sx - ndx * t * 40f * ds + Math.sin(phase + i * 2.3).toFloat * 4f * ds
      val dripY = sy - ndy * t * 40f * ds + t * t * 16f * ds
      val dripSz = (3f + (1f - t) * 3f) * ds
      sb.fillOval(dripX, dripY, dripSz, dripSz * 1.4f, 0.85f, 0.06f, 0.04f, 0.5f * (1f - t) * p, 6)
      sb.fillOval(dripX, dripY - dripSz * 0.3f, dripSz * 0.4f, dripSz * 0.3f, 1f, 0.3f, 0.2f, 0.3f * (1f - t) * p, 4)
    ; i += 1 } }

    { var i = 0; while (i < 4) {
      val starPhase = ((phase * 0.5 + i * 0.25) % 1.0).toFloat
      val starAngle = phase * 1.3 + i * Math.PI * 2 / 4
      val starDist = (14f + starPhase * 16f) * ds
      val starX = sx + Math.cos(starAngle).toFloat * starDist
      val starY = sy + Math.sin(starAngle).toFloat * starDist * 0.55f
      drawSparkleStar(starX, starY, 4f * (1f - starPhase * 0.5f) * ds,
        0.9f, 0.2f, 0.15f, 0.45f * (1f - starPhase) * p, sb, phase * 2 + i)
    ; i += 1 } }

    drawChargeCrackle(sx, sy, 20f * ds, 0.85f, 0.12f, 0.1f, p, sb, phase, proj.chargeLevel)
    drawReturnGhosts(sx, sy, 20f * ds, dr, dg, db, p, sb, proj)
  }


  /** Stinger — curved venomous barbed stinger with poison drip */
  private def drawStinger(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 31) * 0.4
    computeAllDynamics(proj, 0.45f, 0.85f, 0.15f, phase)
    val p = (0.8 + 0.2 * Math.sin(phase * _stPulseMult)).toFloat * dynAlpha
    val dr = _evoR; val dg = _evoG; val db = _evoB
    val ds = dynScale * 1.35f // this one reads far below the roster's scale unscaled
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val perpX = -ndy; val perpY = ndx

    drawSpeedLines(sx, sy, ndx, ndy, 0.45f, 0.8f, 0.15f, 0.25f * p, sb, 5 + (_lifePct * 2).toInt, 30f * ds)
    drawRibbonTrail(sx, sy, ndx, ndy, 0.4f, 0.75f, 0.12f, 0.3f * p, sb, tick, proj.id, 8, 40f * ds * dynTrail, 7f * ds, 1.5f)

    // Toxic glow
    val haloPulse = 0.7f + 0.3f * Math.sin(phase * 2.2).toFloat
    sb.fillOvalSoft(sx, sy, 34.6f * ds * dynGlow * haloPulse, 27.4f * ds * dynGlow * haloPulse, 0.4f, 0.85f, 0.15f, 0.22f * p, 0f, 18)

    // Curved stinger body — thick segmented tail tapering to point
    val stingerLen = 30f * ds
    val curveAmt = Math.sin(phase * 0.8).toFloat * 5f * ds
    val midX = sx + ndx * stingerLen * 0.5f + perpX * curveAmt
    val midY = sy + ndy * stingerLen * 0.5f + perpY * curveAmt
    val tipSX = sx + ndx * stingerLen + perpX * curveAmt * 0.5f
    val tipSY = sy + ndy * stingerLen + perpY * curveAmt * 0.5f

    // Dark outline (thick tapering)
    sb.strokeLine(sx, sy, midX, midY, 8f * ds, 0.1f, 0.12f, 0.05f, 0.85f * p)
    sb.strokeLine(midX, midY, tipSX, tipSY, 5f * ds, 0.1f, 0.12f, 0.05f, 0.85f * p)
    // Chitin body (amber-yellow)
    sb.strokeLine(sx, sy, midX, midY, 5.5f * ds, 0.65f, 0.55f, 0.2f, 0.9f * p)
    sb.strokeLine(midX, midY, tipSX, tipSY, 3f * ds, 0.65f, 0.55f, 0.2f, 0.9f * p)
    // Bright core
    sb.strokeLine(sx + ndx * 2f * ds, sy + ndy * 2f * ds, midX, midY, 2f * ds, 0.8f, 0.7f, 0.3f, 0.5f * p)

    // Barbed tip with venom
    _polyXs3(0) = tipSX + ndx * 10f * ds; _polyXs3(1) = tipSX + perpX * 6f * ds; _polyXs3(2) = tipSX - perpX * 6f * ds
    _polyYs3(0) = tipSY + ndy * 10f * ds; _polyYs3(1) = tipSY + perpY * 6f * ds; _polyYs3(2) = tipSY - perpY * 6f * ds
    sb.strokePolygon(_polyXs3, _polyYs3, 3, 2f, 0.08f, 0.1f, 0.04f, 0.85f * p)
    sb.fillPolygon(_polyXs3, _polyYs3, 3, 0.15f, 0.7f, 0.1f, 0.92f * p)
    sb.fillOval(tipSX + ndx * 5f * ds, tipSY + ndy * 5f * ds, 4f * ds, 3f * ds, 0.3f, 0.9f, 0.2f, 0.8f * p, 6)

    // 3 chitin segment rings
    { var seg = 0; while (seg < 3) {
      val st = 0.2f + seg * 0.2f
      val segX = sx + (tipSX - sx) * st + perpX * curveAmt * st
      val segY = sy + (tipSY - sy) * st + perpY * curveAmt * st
      sb.strokeOval(segX, segY, 4f * ds, 3f * ds, 1.5f, 0.35f, 0.3f, 0.12f, 0.5f * p, 8)
    ; seg += 1 } }

    // Venom drips
    { var i = 0; while (i < 6) {
      val t = ((tick * 0.05 + i * 0.167 + proj.id * 0.13) % 1.0).toFloat
      val dripX = tipSX + ndx * 5f * ds + Math.sin(phase + i * 2.1).toFloat * 3f * ds
      val dripY = tipSY + ndy * 5f * ds + t * t * 18f * ds
      val dSz = (2.5f + (1f - t) * 2.5f) * ds
      sb.fillOval(dripX, dripY, dSz, dSz * 1.3f, 0.2f, 0.85f, 0.1f, 0.5f * (1f - t) * p, 6)
    ; i += 1 } }

    // Pulsing venom glow at tip
    val venomPulse = (0.5f + 0.5f * Math.sin(phase * 3).toFloat)
    sb.fillOvalSoft(tipSX + ndx * 5f * ds, tipSY + ndy * 5f * ds, 12f * ds, 10f * ds,
      0.3f, 0.9f, 0.15f, 0.2f * venomPulse * p, 0f, 10)

    { var i = 0; while (i < 4) {
      val starPhase = ((phase * 0.5 + i * 0.25) % 1.0).toFloat
      val starAngle = phase * 1.3 + i * Math.PI * 2 / 4
      val starDist = (12f + starPhase * 16f) * ds
      val starX = sx + Math.cos(starAngle).toFloat * starDist
      val starY = sy + Math.sin(starAngle).toFloat * starDist * 0.55f
      drawSparkleStar(starX, starY, 4f * (1f - starPhase * 0.4f) * ds,
        0.35f, 0.85f, 0.15f, 0.4f * (1f - starPhase) * p, sb, phase * 2 + i)
    ; i += 1 } }

    drawChargeCrackle(sx, sy, 22f * ds, 0.45f, 0.85f, 0.15f, p, sb, phase, proj.chargeLevel)
    drawReturnGhosts(sx, sy, 22f * ds, dr, dg, db, p, sb, proj)
  }

  /** Minotaur's thrown horn. Built from the same tapering silhouette the lobbed horn
   *  uses, flying point-first — the previous version stacked two untapered strokes under
   *  a fat base knob, so what reached the screen was a circle on a stick. */
  private def drawHorn(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
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




  /** Claw Swipe — 3-4 visible parallel slash marks with raking motion and sparks */
  private def drawClawSwipe(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 29) * 0.4
    computeAllDynamics(proj, 0.9f, 0.2f, 0.15f, phase)
    val p = (0.8 + 0.2 * Math.sin(phase * _stPulseMult)).toFloat * dynAlpha
    val dr = _evoR; val dg = _evoG; val db = _evoB
    val ds = dynScale
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val perpX = -ndy; val perpY = ndx

    drawSpeedLines(sx, sy, ndx, ndy, 0.8f, 0.25f, 0.15f, 0.3f * p, sb, 6 + (_lifePct * 2).toInt, 35f * ds)
    drawRibbonTrail(sx, sy, ndx, ndy, 0.85f, 0.2f, 0.15f, 0.35f * p, sb, tick, proj.id, 10, 45f * ds * dynTrail, 14f * ds, 2f)

    // Blood-red glow
    sb.fillOvalSoft(sx, sy, 36f * ds * dynGlow, 28.8f * ds * dynGlow, 0.85f, 0.15f, 0.1f, 0.2f * p, 0f, 18)

    // 4 parallel slash marks — the core visual
    val slashLen = 32f * ds
    val slashSpread = 7f * ds
    val swipeAngle = phase * 0.5  // slow rotation for raking motion
    val swipeCos = Math.cos(swipeAngle).toFloat * 0.15f;
    { var c = 0; while (c < 4) {
      val offset = (c - 1.5f) * slashSpread
      val slashStartX = sx - ndx * slashLen * 0.3f + perpX * offset
      val slashStartY = sy - ndy * slashLen * 0.3f + perpY * offset
      val slashEndX = sx + ndx * slashLen * 0.7f + perpX * (offset + swipeCos * 8f * ds)
      val slashEndY = sy + ndy * slashLen * 0.7f + perpY * (offset + swipeCos * 8f * ds)

      // Tapered slash: thicker in middle, thin at tips
      val midSX = (slashStartX + slashEndX) * 0.5f; val midSY = (slashStartY + slashEndY) * 0.5f

      // Dark outline
      sb.strokeLine(slashStartX, slashStartY, midSX, midSY, 6f * ds, 0.15f, 0.04f, 0.03f, 0.8f * p)
      sb.strokeLine(midSX, midSY, slashEndX, slashEndY, 4f * ds, 0.15f, 0.04f, 0.03f, 0.7f * p)
      // Red slash body
      sb.strokeLine(slashStartX, slashStartY, midSX, midSY, 4f * ds, dr, dg, db, 0.88f * p)
      sb.strokeLine(midSX, midSY, slashEndX, slashEndY, 2.5f * ds, dr, dg, db, 0.78f * p)
      // Bright white-red core
      sb.strokeLine(slashStartX, slashStartY, midSX, midSY, 1.5f * ds, 1f, 0.5f, 0.35f, 0.5f * p)
      sb.strokeLine(midSX, midSY, slashEndX, slashEndY, 0.8f * ds, 1f, 0.5f, 0.35f, 0.35f * p)

      // Spark at slash tip
      val sparkPulse = (0.5f + 0.5f * Math.sin(phase * 3 + c * 1.5).toFloat)
      sb.fillOval(slashEndX, slashEndY, 4f * ds * sparkPulse, 3f * ds * sparkPulse,
        1f, 0.7f, 0.3f, 0.6f * sparkPulse * p, 6)
      sb.fillOvalSoft(slashEndX, slashEndY, 8f * ds, 6f * ds, 1f, 0.4f, 0.15f, 0.15f * sparkPulse * p, 0f, 6)
    ; c += 1 } }

    // Blood splatter particles behind
    { var i = 0; while (i < 8) {
      val t = ((tick * 0.06 + i * 0.125 + proj.id * 0.11) % 1.0).toFloat
      val splX = sx - ndx * t * 40f * ds + perpX * Math.sin(phase + i * 2.3).toFloat * 8f * ds
      val splY = sy - ndy * t * 40f * ds + perpY * Math.sin(phase + i * 2.3).toFloat * 8f * ds
      val sSz = (3f + (1f - t) * 3.5f) * ds
      sb.fillOval(splX, splY, sSz, sSz * 0.7f, 0.85f, 0.08f, 0.06f, 0.4f * (1f - t) * p, 6)
    ; i += 1 } }

    // Sparkle stars at slash endpoints
    { var i = 0; while (i < 4) {
      val starPhase = ((phase * 0.5 + i * 0.25) % 1.0).toFloat
      val starAngle = phase * 1.5 + i * Math.PI * 2 / 4
      val starDist = (16f + starPhase * 16f) * ds
      val starX = sx + Math.cos(starAngle).toFloat * starDist
      val starY = sy + Math.sin(starAngle).toFloat * starDist * 0.55f
      drawSparkleStar(starX, starY, 4.5f * (1f - starPhase * 0.4f) * ds,
        1f, 0.4f, 0.2f, 0.45f * (1f - starPhase) * p, sb, phase * 2 + i)
    ; i += 1 } }

    drawChargeCrackle(sx, sy, 22f * ds, 0.9f, 0.2f, 0.15f, p, sb, phase, proj.chargeLevel)
    drawReturnGhosts(sx, sy, 22f * ds, dr, dg, db, p, sb, proj)
  }
}
