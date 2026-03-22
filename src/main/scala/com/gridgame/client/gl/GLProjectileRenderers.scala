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
    val p = (0.65 + 0.35 * Math.sin(phase * _stPulseMult)).toFloat * dynAlpha
    computeAllDynamics(proj, r, g, b, phase)
    val dr = _evoR; val dg = _evoG; val db = _evoB
    val ds = dynScale
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy

    // Speed lines
    drawSpeedLines(sx, sy, ndx, ndy, dr, dg, db, 0.25f * p, sb, 4 + (_lifePct * 2).toInt, 28f * ds)
    // Soft glow halo
    sb.fillOvalSoft(sx, sy, 50f * ds * dynGlow, 40f * ds * dynGlow, dr, dg, db, 0.38f * p, 0f, 18)
    // Dark cartoon outline
    sb.strokeOval(sx, sy, 24f * ds, 18f * ds, 3f, outline(r), outline(g), outline(b), 0.8f * p, 14)
    // Core
    sb.fillOval(sx, sy, 22f * ds, 17f * ds, dr, dg, db, 0.95f * p, 14)
    // Cartoon highlight
    sb.fillOval(sx - 4f, sy - 4f, 7f * ds, 5f * ds, 1f, 1f, 1f, 0.35f * p, 8)
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

  // --- Step 1: Charge-Level Visual Escalation ---
  private var _chgScale = 1f      // size multiplier 1.0→2.0
  private var _chgGlow = 1f       // glow radius multiplier 1.0→2.5
  private var _chgBright = 0f     // whiteness mix 0.0→0.4
  private var _chgTrailLen = 1f   // trail length multiplier 1.0→2.0
  private var _chgSparkCount = 0  // extra spark particles 0→6

  private def computeChargeVisuals(chargeLevel: Int): Unit = {
    val t = chargeLevel / 100f  // 0.0→1.0
    _chgScale = 1f + t * 1f
    _chgGlow = 1f + t * 1.5f
    _chgBright = t * 0.4f
    _chgTrailLen = 1f + t * 1f
    _chgSparkCount = (t * 6f).toInt
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
  @inline private def lifetimeTrailMult: Float = 1f + _lifePct * 0.5f
  /** Glow radius growth over lifetime */
  @inline private def lifetimeGlowMult: Float = 1f + _lifePct * 0.3f

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

  /** Combined dynamic size multiplier */
  @inline private def dynScale: Float = _chgScale * _stSizeMult * dissipationScale
  /** Combined dynamic alpha multiplier */
  @inline private def dynAlpha: Float = dissipationAlpha
  /** Combined glow multiplier */
  @inline private def dynGlow: Float = _chgGlow * lifetimeGlowMult
  /** Combined trail length multiplier */
  @inline private def dynTrail: Float = _chgTrailLen * _stTrailMult * lifetimeTrailMult

  /** Cartoon sparkle star — 4-point star burst */
  private def drawSparkleStar(sx: Float, sy: Float, size: Float, r: Float, g: Float, b: Float,
      alpha: Float, sb: ShapeBatch, rotation: Double): Unit = {
    val s = size
    val s2 = size * 0.3f
    sb.strokeLine(sx - Math.cos(rotation).toFloat * s, sy - Math.sin(rotation).toFloat * s * 0.6f,
      sx + Math.cos(rotation).toFloat * s, sy + Math.sin(rotation).toFloat * s * 0.6f,
      2f, r, g, b, alpha)
    sb.strokeLine(sx - Math.cos(rotation + Math.PI * 0.5).toFloat * s2,
      sy - Math.sin(rotation + Math.PI * 0.5).toFloat * s2 * 0.6f,
      sx + Math.cos(rotation + Math.PI * 0.5).toFloat * s2,
      sy + Math.sin(rotation + Math.PI * 0.5).toFloat * s2 * 0.6f,
      2f, r, g, b, alpha)
  }

  /** Cartoon speed lines behind a moving projectile */
  private def drawSpeedLines(sx: Float, sy: Float, ndx: Float, ndy: Float,
      r: Float, g: Float, b: Float, alpha: Float, sb: ShapeBatch, count: Int, length: Float): Unit = {
    val perpX = -ndy; val perpY = ndx
    var i = 0; while (i < count) {
      val off = (i - (count - 1) * 0.5f) * length * 0.5f / count
      val startDist = 8f + Math.abs(off) * 0.5f
      val endDist = startDist + length * (0.5f + 0.5f * (1f - Math.abs(i - (count - 1) * 0.5f) / ((count - 1) * 0.5f + 0.001f)))
      val x0 = sx - ndx * startDist + perpX * off
      val y0 = sy - ndy * startDist + perpY * off
      val x1 = sx - ndx * endDist + perpX * off
      val y1 = sy - ndy * endDist + perpY * off
      val taper = 1f - Math.abs(i - (count - 1) * 0.5f) / ((count - 1) * 0.5f + 0.001f)
      sb.strokeLine(x0, y0, x1, y1, 2f * taper + 0.5f, r, g, b, alpha * taper)
    ; i += 1 }
  }

  /** Continuous tapering ribbon trail using strokeLineSoft segments with sinusoidal wave */
  private def drawRibbonTrail(sx: Float, sy: Float, ndx: Float, ndy: Float,
      r: Float, g: Float, b: Float, p: Float, sb: ShapeBatch,
      tick: Int, projId: Int, segments: Int, length: Float,
      startWidth: Float, endWidth: Float): Unit = {
    val perpX = -ndy; val perpY = ndx
    val step = 1.0 / segments
    var i = 0; while (i < segments) {
      val t0 = ((tick * 0.05 + i * step + projId * 0.13) % 1.0).toFloat
      val t1 = ((tick * 0.05 + (i + 1) * step + projId * 0.13) % 1.0).toFloat
      val w = mix(startWidth, endWidth, t0)
      val wave = Math.sin(tick * 0.15 + i * 0.8 + projId * 0.7).toFloat * w * 0.3f
      val x0 = sx - ndx * t0 * length + perpX * wave
      val y0 = sy - ndy * t0 * length + perpY * wave
      val x1 = sx - ndx * t1 * length + perpX * wave * 0.7f
      val y1 = sy - ndy * t1 * length + perpY * wave * 0.7f
      val colorFade = 1f - t0 * 0.5f
      val alpha = 0.5f * (1f - t0) * p
      sb.strokeLineSoft(x0, y0, x1, y1, w, r * colorFade, g * colorFade, b * colorFade, alpha)
    ; i += 1 }
  }

  /** Reusable radial spark particle burst — CARTOONISH: bigger sparks with dots at tips */
  private def drawSparkBurst(sx: Float, sy: Float, r: Float, g: Float, b: Float,
      p: Float, sb: ShapeBatch, tick: Int, projId: Int, count: Int, radius: Float): Unit = {
    var i = 0; while (i < count) {
      val angle = tick * 0.12 + i * Math.PI * 2 / count + projId * 0.7
      val dist = radius * (0.4f + 0.6f * Math.sin(tick * 0.2 + i * 1.7).toFloat)
      val sparkX = sx + Math.cos(angle).toFloat * dist
      val sparkY = sy + Math.sin(angle).toFloat * dist * 0.6f
      val sparkLen = radius * 0.4f
      val ex = sparkX + Math.cos(angle).toFloat * sparkLen
      val ey = sparkY + Math.sin(angle).toFloat * sparkLen * 0.6f
      sb.strokeLine(sparkX, sparkY, ex, ey, 2.5f, bright(r), bright(g), bright(b),
        (0.35 + 0.35 * Math.sin(tick * 0.3 + i * 2.1)).toFloat * p)
      // Dot at tip of each spark
      sb.fillOval(ex, ey, 3f, 2.5f, bright(r), bright(g), bright(b),
        (0.4 + 0.3 * Math.sin(tick * 0.3 + i * 2.1)).toFloat * p, 6)
    ; i += 1 }
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

  /** Focused energy stream — tapers from origin to tip, with filaments and organic undulation */
  private def beamProj(r: Float, g: Float, b: Float, worldLen: Float = 6f, width: Float = 8f): Renderer =
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

      // 5-segment tapering beam (full width at origin, 40% at tip)
      val segs = 5
      var seg = 0; while (seg < segs) {
        val t0 = seg.toFloat / segs
        val t1 = (seg + 1).toFloat / segs
        val w0 = w * (1f - t0 * 0.6f) // taper: 100% → 40%
        val w1 = w * (1f - t1 * 0.6f)
        val wAvg = (w0 + w1) * 0.5f
        // Per-segment perpendicular ripple for organic undulation
        val ripple0 = Math.sin(phase * 1.8 + seg * 1.7 + proj.id * 0.5).toFloat * w * 0.25f
        val ripple1 = Math.sin(phase * 1.8 + (seg + 1) * 1.7 + proj.id * 0.5).toFloat * w * 0.25f
        val x0 = sx + dx * t0 + perpX * ripple0
        val y0 = sy + dy * t0 + perpY * ripple0
        val x1 = sx + dx * t1 + perpX * ripple1
        val y1 = sy + dy * t1 + perpY * ripple1

        // Soft outer glow — charge-scaled
        sb.strokeLineSoft(x0, y0, x1, y1, wAvg * 3f * dynGlow, dr, dg, db, 0.25f * p)
        // Dark cartoon outline
        sb.strokeLine(x0, y0, x1, y1, wAvg * 1.15f, outline(r), outline(g), outline(b), 0.7f * p)
        // Main beam body — evolved colors
        sb.strokeLine(x0, y0, x1, y1, wAvg, dr, dg, db, (0.88f + t0 * 0.07f) * p)
        // Hot core — charge whitening
        val cbc = _chgBright
        sb.strokeLine(x0, y0, x1, y1, wAvg * 0.35f,
          mix(bright(r), 1f, cbc), mix(bright(g), 1f, cbc), mix(bright(b), 1f, cbc), 0.98f * p)
      ; seg += 1 }

      // 3 energy filaments weaving along the beam
      var fil = 0; while (fil < 3) {
        val freq = 3.5 + fil * 1.8
        val filAmp = w * (0.4f + fil * 0.15f)
        var fs = 0; while (fs < 6) {
          val ft0 = fs.toFloat / 6
          val ft1 = (fs + 1).toFloat / 6
          val fOff0 = Math.sin(phase * freq + ft0 * 8.0 + fil * 2.1 + proj.id * 0.3).toFloat * filAmp
          val fOff1 = Math.sin(phase * freq + ft1 * 8.0 + fil * 2.1 + proj.id * 0.3).toFloat * filAmp
          val fx0 = sx + dx * ft0 + perpX * fOff0
          val fy0 = sy + dy * ft0 + perpY * fOff0
          val fx1 = sx + dx * ft1 + perpX * fOff1
          val fy1 = sy + dy * ft1 + perpY * fOff1
          sb.strokeLineSoft(fx0, fy0, fx1, fy1, 1.5f,
            bright(r), bright(g), bright(b), 0.3f * (1f - ft0 * 0.5f) * p)
        ; fs += 1 }
      ; fil += 1 }

      // Crackling sparks — 6 sparks
      var sp = 0; while (sp < 6) {
        val st = ((tick * 0.12 + sp * 0.167 + proj.id * 0.17) % 1.0).toFloat
        val sparkOff = Math.sin(phase * 5 + sp * 3.7).toFloat * w * 1.5f
        val sparkX = sx + dx * st + perpX * sparkOff
        val sparkY = sy + dy * st + perpY * sparkOff
        val sparkEndX = sparkX + perpX * Math.sin(phase * 7 + sp * 2.3).toFloat * w * 0.9f
        val sparkEndY = sparkY + perpY * Math.sin(phase * 7 + sp * 2.3).toFloat * w * 0.9f
        sb.strokeLine(sparkX, sparkY, sparkEndX, sparkEndY, 2f, bright(r), bright(g), bright(b),
          (0.35 + 0.35 * Math.sin(phase * 6 + sp * 4.1)).toFloat * p)
      ; sp += 1 }

      // Tip flare — dynamic glow
      val tipPulse = (0.7 + 0.3 * Math.sin(phase * 3)).toFloat
      sb.fillOvalSoft(tipX, tipY, w * 3f * tipPulse * dynGlow, w * 2.3f * tipPulse * dynGlow, dr, dg, db, 0.4f * p, 0f, 14)
      sb.strokeOval(tipX, tipY, w * 1.4f * tipPulse, w * 1.05f * tipPulse, 2.5f,
        outline(r), outline(g), outline(b), 0.6f * p, 10)
      sb.fillOval(tipX, tipY, w * 1.2f, w * 0.9f, dr, dg, db, 0.9f * p, 10)
      sb.fillOval(tipX, tipY, w * 0.55f, w * 0.4f,
        mix(bright(r), 1f, _chgBright), mix(bright(g), 1f, _chgBright), mix(bright(b), 1f, _chgBright), 0.95f * p, 8)

      // Sparkle at tip
      drawSparkleStar(tipX, tipY, w * 0.9f * tipPulse, 1f, 1f, 1f, 0.5f * p, sb, phase * 1.5)

      // Single expanding ring at tip
      val ringPh = ((phase * 0.6) % 1.0).toFloat
      sb.strokeOval(tipX, tipY, w * (1f + ringPh * 2.5f), w * (0.7f + ringPh * 1.8f),
        2f * (1f - ringPh), bright(r), bright(g), bright(b), 0.4f * (1f - ringPh) * p, 10)

      // 5 energy nodes with independent pulsing and tapering along beam
      var i = 0; while (i < 5) {
        val t = ((tick * 0.08 + i * 0.2 + proj.id * 0.11) % 1.0).toFloat
        val taper = 1f - t * 0.5f
        val ns = w * 0.6f * taper * (0.5f + 0.5f * Math.sin(phase * 3.5 + i * 2.3).toFloat)
        val colorFade = 1f - t * 0.3f
        sb.fillOval(sx + dx * t, sy + dy * t, ns, ns * 0.7f,
          bright(r) * colorFade, bright(g) * colorFade, bright(b) * colorFade, 0.5f * (1f - t * 0.3f) * p, 8)
      ; i += 1 }
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

  /** Solid physical projectile — CARTOONISH with bold outline, big head, speed lines, glow, ribbon trail */
  private def physProj(r: Float, g: Float, b: Float, worldLen: Float = 6f): Renderer =
    (proj, sx, sy, sb, tick) => {
      beamTip(sx, sy, proj, worldLen)
      val tipX = _tipX; val tipY = _tipY
      val phase = (tick + proj.id * 31) * 0.4
      computeAllDynamics(proj, r, g, b, phase)
      val p = (0.75 + 0.25 * Math.sin(phase * _stPulseMult)).toFloat * dynAlpha
      val dr = _evoR; val dg = _evoG; val db = _evoB
      screenDir(proj)
      val ndx = _sdx; val ndy = _sdy
      val perpX = -ndy; val perpY = ndx
      // Dampen charge scaling for physical projectiles (cap at 1.35x instead of 2x)
      val ds = Math.min(dynScale, 1.35f)

      // Speed lines behind (compact)
      drawSpeedLines(sx, sy, ndx, ndy, dr, dg, db, 0.30f * p, sb, 4 + (_lifePct * 2).toInt, 20f * ds)

      // Short ribbon trail
      drawRibbonTrail(sx, sy, ndx, ndy, dr, dg, db, 0.25f * p, sb, tick, proj.id, 6, 26f * ds * dynTrail, 3.5f * ds, 1f)

      // Subtle glow halo
      val haloPulse = 0.8f + 0.2f * Math.sin(phase * 2.0).toFloat
      sb.fillOvalSoft(sx + ndx * 8f * ds, sy + ndy * 8f * ds, 16f * ds * dynGlow * haloPulse,
        12f * ds * dynGlow * haloPulse, dr, dg, db, 0.15f * p, 0f, 12)

      // Ground shadow
      val midX = (sx + tipX) * 0.5f; val midY = (sy + tipY) * 0.5f
      sb.fillOval(midX, midY + 8f * ds, 18f * ds, 4f * ds, 0f, 0f, 0f, 0.2f * p, 10)

      // Shaft — dark outline, colored body, bright core
      sb.strokeLine(sx, sy, tipX, tipY, 5f * ds, outline(r), outline(g), outline(b), 0.85f * p)
      sb.strokeLine(sx, sy, tipX, tipY, 3.5f * ds, dr, dg, db, 0.9f * p)
      sb.strokeLine(sx, sy, tipX, tipY, 1.2f * ds,
        mix(bright(r), 1f, _chgBright), mix(bright(g), 1f, _chgBright), mix(bright(b), 1f, _chgBright), 0.6f * p)

      // Arrowhead — 3-layer fill (outline, body, bright tip)
      val headLen = 13f * ds; val headW = 6.5f * ds
      val pointX = tipX + ndx * headLen; val pointY = tipY + ndy * headLen
      // Layer 1: Dark outline
      _polyXs3(0) = pointX + ndx * 2f; _polyXs3(1) = tipX + perpX * (headW + 2f); _polyXs3(2) = tipX - perpX * (headW + 2f)
      _polyYs3(0) = pointY + ndy * 2f; _polyYs3(1) = tipY + perpY * (headW + 2f); _polyYs3(2) = tipY - perpY * (headW + 2f)
      sb.fillPolygon(_polyXs3, _polyYs3, 3, outline(r), outline(g), outline(b), 0.88f * p)
      // Layer 2: Colored body
      _polyXs3(0) = pointX; _polyXs3(1) = tipX + perpX * headW; _polyXs3(2) = tipX - perpX * headW
      _polyYs3(0) = pointY; _polyYs3(1) = tipY + perpY * headW; _polyYs3(2) = tipY - perpY * headW
      sb.fillPolygon(_polyXs3, _polyYs3, 3, dr, dg, db, 0.95f * p)
      // Layer 3: Bright tip highlight
      val tipHL = 0.6f
      _polyXs3(0) = pointX; _polyXs3(1) = tipX + perpX * headW * tipHL + ndx * headLen * 0.3f
      _polyXs3(2) = tipX - perpX * headW * 0.2f + ndx * headLen * 0.3f
      _polyYs3(0) = pointY; _polyYs3(1) = tipY + perpY * headW * tipHL + ndy * headLen * 0.3f
      _polyYs3(2) = tipY - perpY * headW * 0.2f + ndy * headLen * 0.3f
      sb.fillPolygon(_polyXs3, _polyYs3, 3,
        mix(bright(r), 1f, _chgBright), mix(bright(g), 1f, _chgBright), mix(bright(b), 1f, _chgBright), 0.55f * p)
      // Shine on arrowhead
      _polyXs3(0) = pointX - perpX * 1f; _polyXs3(1) = tipX + perpX * headW * 0.3f; _polyXs3(2) = tipX + perpX * headW * 0.6f
      _polyYs3(0) = pointY - perpY * 1f; _polyYs3(1) = tipY + perpY * headW * 0.3f; _polyYs3(2) = tipY + perpY * headW * 0.6f
      sb.fillPolygon(_polyXs3, _polyYs3, 3, 1f, 1f, 1f, 0.25f * p)

      // Compact fletching with feather barb lines
      { var f = -1; while (f <= 1) {
        val tailX = sx - ndx * 3f; val tailY = sy - ndy * 3f
        val featherX = tailX - ndx * 11f + perpX * f * 5.5f
        val featherY = tailY - ndy * 11f + perpY * f * 5.5f
        sb.strokeLine(tailX, tailY, featherX, featherY, 3f, outline(r), outline(g), outline(b), 0.6f * p)
        sb.strokeLine(tailX, tailY, featherX, featherY, 2f, r, g, b, 0.55f * p)
        sb.strokeLine(tailX, tailY, featherX, featherY, 0.7f, bright(r), bright(g), bright(b), 0.3f * p)
        // Feather barb detail lines
        var barb = 0; while (barb < 2) {
          val bt = (barb + 1).toFloat / 3f
          val bx = tailX + (featherX - tailX) * bt
          val by = tailY + (featherY - tailY) * bt
          val barbLen = 3.5f * (1f - bt * 0.5f) * ds
          sb.strokeLine(bx, by, bx + perpX * f * barbLen, by + perpY * f * barbLen,
            1.2f, r, g, b, 0.3f * p * (1f - bt * 0.4f))
        ; barb += 1 }
      ; f += 2 } }

      // Trail particles — scaled by dynTrail
      val trailLen = 26f * dynTrail
      var i = 0; while (i < 6) {
        val t = ((tick * 0.06 + i * 0.167 + proj.id * 0.11) % 1.0).toFloat
        val taper = 1f - t * 0.7f
        val colorFade = 1f - t * 0.3f
        val tx = sx - ndx * t * trailLen; val ty = sy - ndy * t * trailLen
        val tsz = 3.5f * taper * ds
        sb.fillOval(tx, ty, tsz + 1f, (tsz + 1f) * 0.65f, outline(r), outline(g), outline(b), 0.2f * (1f - t) * p, 8)
        sb.fillOval(tx, ty, tsz, tsz * 0.65f, r * colorFade, g * colorFade, b * colorFade, 0.4f * (1f - t) * p, 8)
      ; i += 1 }

      // 2 sparkle stars along trajectory
      { var star = 0; while (star < 2) {
        val starPhase = ((phase * 0.4 + star * 0.5) % 1.0).toFloat
        val starT = star.toFloat / 1.5f
        val starX = sx + (tipX - sx) * starT + perpX * Math.sin(phase * 2 + star * 1.7).toFloat * 5f
        val starY = sy + (tipY - sy) * starT + perpY * Math.sin(phase * 2 + star * 1.7).toFloat * 5f
        drawSparkleStar(starX, starY, 3.5f * (1f - starPhase * 0.4f) * ds,
          bright(r), bright(g), bright(b), 0.4f * (1f - starPhase) * p, sb, phase * 1.5 + star)
      ; star += 1 } }

      // Charge crackle
      drawChargeCrackle(sx, sy, 22f * ds, r, g, b, p, sb, phase, proj.chargeLevel)
      drawReturnGhosts(sx, sy, 22f * ds, dr, dg, db, p, sb, proj)
    }

  /** Large bouncing thrown object — CARTOONISH with squash/stretch, tumble rotation, afterimages, landing shadow */
  private def lobbed(r: Float, g: Float, b: Float, size: Float = 18f): Renderer =
    (proj, sx, sy, sb, tick) => {
      val phase = (tick + proj.id * 13) * 0.3
      computeAllDynamics(proj, r, g, b, phase)
      val bounceRaw = Math.sin(phase * 1.5).toFloat
      val bounce = (Math.abs(bounceRaw) * 16f).toFloat
      val bounceContact = Math.abs(bounceRaw)
      val spin = tick * 0.18f + proj.id * 1.3f
      val p = (0.7 + 0.3 * Math.sin(phase * _stPulseMult)).toFloat * dynAlpha
      val dr = _evoR; val dg = _evoG; val db = _evoB
      val sz = size * 1.4f * dynScale

      // Squash on apex (high bounce), stretch on descent
      val squashFactor = bounceRaw * bounceRaw
      val stretchY = 1f + squashFactor * 0.28f
      val stretchX = 1f - squashFactor * 0.14f

      screenDir(proj)
      val ndx = _sdx; val ndy = _sdy

      // Speed lines behind
      drawSpeedLines(sx, sy, ndx, ndy, dr, dg, db, 0.2f * p, sb, 5, sz * 1.4f)

      // Ribbon trail
      drawRibbonTrail(sx, sy, ndx, ndy, dr, dg, db, 0.3f * p, sb, tick, proj.id, 7, 40f * dynScale * dynTrail, 7f * dynScale, 1f)

      // Landing target shadow that grows AND a pulsing target ring
      val shadowScale = 1f + bounce * 0.04f
      val shadowAlpha = Math.max(0.1f, 0.45f - bounce * 0.015f)
      sb.fillOval(sx, sy + sz * 0.38f, sz * 1.3f * shadowScale, sz * 0.35f * shadowScale,
        0f, 0f, 0f, shadowAlpha, 16)
      sb.fillOval(sx, sy + sz * 0.38f, sz * 0.9f * shadowScale, sz * 0.22f * shadowScale,
        0f, 0f, 0f, shadowAlpha * 0.4f, 14)
      // Pulsing target ring
      val targetPulse = (0.5f + 0.5f * Math.sin(phase * 3).toFloat)
      val targetAlpha = Math.max(0f, (0.35f - bounce * 0.015f)) * targetPulse * p
      if (targetAlpha > 0.01f) {
        sb.strokeOval(sx, sy + sz * 0.38f, sz * 1f * shadowScale, sz * 0.28f * shadowScale,
          2f, dark(r), dark(g), dark(b), targetAlpha, 12)
        sb.strokeOval(sx, sy + sz * 0.38f, sz * 0.6f * shadowScale, sz * 0.16f * shadowScale,
          1.2f, r, g, b, targetAlpha * 0.6f, 10)
      }

      // Spark burst at bounce contact points
      if (bounceContact < 0.15f) {
        val impactT = 1f - bounceContact / 0.15f
        drawSparkBurst(sx, sy + sz * 0.35f, dr, dg, db, impactT * 0.6f * p, sb, tick, proj.id, 6, sz * 1.1f)
      }

      // 4 fading afterimage ghosts along arc trajectory
      { var ghost = 0; while (ghost < 4) {
        val gt = ((tick * 0.06 + ghost * 0.17 + proj.id * 0.11) % 1.0).toFloat
        val ghostBounce = Math.abs(Math.sin(phase * 1.5 - gt * Math.PI)).toFloat * 14f
        val ghostSz = sz * (0.6f - ghost * 0.1f) * (1f - gt * 0.3f)
        val ghostAlpha = 0.28f * (1f - gt) * (1f - ghost * 0.18f) * p
        val gx = sx - ndx * gt * 45; val gy = sy - ndy * gt * 45 - ghostBounce
        // Ghost outline
        sb.strokeOval(gx, gy, ghostSz * stretchX + 1f, ghostSz * 0.7f * stretchY + 0.5f,
          1.5f, outline(r), outline(g), outline(b), ghostAlpha * 0.4f, 10)
        sb.fillOval(gx, gy, ghostSz * stretchX, ghostSz * 0.7f * stretchY, dr, dg, db, ghostAlpha, 10)
      ; ghost += 1 } }

      // Huge outer glow halo (charge-scaled)
      sb.fillOvalSoft(sx, sy - bounce, sz * 2.2f * stretchX * dynGlow, sz * 1.8f * stretchY * dynGlow,
        dr, dg, db, 0.32f * p, 0f, 18)
      sb.fillOvalSoft(sx, sy - bounce, sz * 1.5f * stretchX * dynGlow, sz * 1.2f * stretchY * dynGlow,
        bright(r), bright(g), bright(b), 0.15f * p, 0f, 16)

      // 4px dark cartoon outline with squash/stretch
      sb.strokeOval(sx, sy - bounce, sz * 1.08f * stretchX, sz * 0.88f * stretchY,
        4f, outline(r), outline(g), outline(b), 0.88f * dynAlpha, 18)

      // Multi-layer body (3 concentric fills)
      sb.fillOval(sx, sy - bounce, sz * 1.02f * stretchX, sz * 0.82f * stretchY, dr, dg, db, 0.98f * dynAlpha, 18)
      sb.fillOval(sx, sy - bounce, sz * 0.75f * stretchX, sz * 0.6f * stretchY,
        mix(dr, bright(r), 0.3f), mix(dg, bright(g), 0.3f), mix(db, bright(b), 0.3f), 0.7f * dynAlpha, 14)
      sb.fillOval(sx, sy - bounce, sz * 0.4f * stretchX, sz * 0.32f * stretchY,
        mix(bright(r), 1f, _chgBright), mix(bright(g), 1f, _chgBright), mix(bright(b), 1f, _chgBright), 0.85f * dynAlpha, 10)

      // Bottom shadow shading
      sb.fillOval(sx, sy - bounce + sz * 0.2f * stretchY, sz * 0.88f * stretchX, sz * 0.45f * stretchY,
        dark(r), dark(g), dark(b), 0.4f, 14)

      // Tumble rotation with moving highlight AND moving shadow
      val hlAngle = spin * 2.5
      val hlOffX = Math.cos(hlAngle).toFloat * sz * 0.22f
      val hlOffY = Math.sin(hlAngle).toFloat * sz * 0.16f
      // Moving shadow (opposite side of highlight)
      sb.fillOval(sx - hlOffX * 0.8f, sy - bounce - hlOffY * 0.6f + sz * 0.12f * stretchY,
        sz * 0.32f * stretchX, sz * 0.24f * stretchY, dark(r), dark(g), dark(b), 0.3f * p, 8)
      // Moving highlight
      sb.fillOval(sx + hlOffX, sy - bounce + hlOffY - sz * 0.18f * stretchY,
        sz * 0.38f * stretchX, sz * 0.28f * stretchY, 1f, 1f, 1f, 0.5f, 8)
      sb.fillOval(sx + hlOffX * 0.7f, sy - bounce + hlOffY * 0.7f - sz * 0.2f * stretchY,
        sz * 0.18f * stretchX, sz * 0.12f * stretchY, 1f, 1f, 1f, 0.3f, 6)

      // 5 surface detail marks rotating
      var v = 0; while (v < 5) {
        val a = spin * 2.5 + v * Math.PI * 2 / 5
        val markR = sz * 0.48f
        val vx = (sx + Math.cos(a) * markR * stretchX).toFloat
        val vy = (sy - bounce + Math.sin(a) * markR * 0.75f * stretchY).toFloat
        // Mark outline
        sb.strokeLine(sx, sy - bounce, vx, vy, 2.5f, outline(r), outline(g), outline(b), 0.2f * p)
        // Mark body
        sb.strokeLine(sx, sy - bounce, vx, vy, 1.5f, dark(r), dark(g), dark(b), 0.35f * p)
        // Dot at end
        sb.fillOval(vx, vy, 2.5f, 2f, dark(r), dark(g), dark(b), 0.3f * p, 4)
      ; v += 1 }

      // 4 sparkle stars
      { var i = 0; while (i < 4) {
        val starPhase = ((phase * 0.45 + i * 0.25) % 1.0).toFloat
        val starAngle = phase * 1.2 + i * Math.PI * 2 / 4
        val starDist = (sz * 0.8f + starPhase * sz * 0.6f)
        val starX = sx + Math.cos(starAngle).toFloat * starDist
        val starY = sy - bounce + Math.sin(starAngle).toFloat * starDist * 0.55f
        drawSparkleStar(starX, starY, 5f * (1f - starPhase * 0.5f) * dynScale,
          bright(r), bright(g), bright(b), 0.45f * (1f - starPhase) * p, sb, phase * 2 + i)
      ; i += 1 } }

      drawChargeCrackle(sx, sy - bounce, sz, r, g, b, p, sb, phase, proj.chargeLevel)
      drawReturnGhosts(sx, sy - bounce, sz, dr, dg, db, p, sb, proj)
    }

  /** Large expanding ring AoE — maximum drama shockwave with ground cracks, debris, sparkles, blast wave */
  private def aoeRing(r: Float, g: Float, b: Float, maxR: Float = 50f): Renderer =
    (proj, sx, sy, sb, tick) => {
      val phase = (tick + proj.id * 23) * 0.4
      computeAllDynamics(proj, r, g, b, phase)
      val p = (0.65 + 0.35 * Math.sin(phase * 2 * _stPulseMult)).toFloat * dynAlpha
      val dr = _evoR; val dg = _evoG; val db = _evoB
      val mR = maxR * 1.35f * dynScale

      // Ground fill glow — charge-scaled, pulsing
      val groundPulse = (0.7f + 0.3f * Math.sin(phase * 1.8).toFloat)
      sb.fillOvalSoft(sx, sy, mR * 0.8f * dynGlow * groundPulse, mR * 0.4f * dynGlow * groundPulse,
        dr, dg, db, 0.3f * p, 0f, 20)
      sb.fillOvalSoft(sx, sy, mR * 0.5f * dynGlow, mR * 0.25f * dynGlow,
        bright(r), bright(g), bright(b), 0.15f * p, 0f, 16)

      // 10 ground crack lines with forked ends
      { var crack = 0; while (crack < 10) {
        val cAngle = crack * Math.PI * 2 / 10 + proj.id * 0.5
        val cLen = mR * (0.35f + 0.18f * Math.sin(phase * 0.3 + crack * 2.1).toFloat)
        val cx1 = sx + Math.cos(cAngle).toFloat * cLen
        val cy1 = sy + Math.sin(cAngle).toFloat * cLen * 0.45f
        // Dark crack line (thick)
        sb.strokeLine(sx, sy, cx1, cy1, 2.5f, dark(r), dark(g), dark(b), 0.4f * p)
        // Bright edge
        sb.strokeLine(sx + 0.5f, sy + 0.5f, cx1 + 0.5f, cy1 + 0.5f, 0.8f, r, g, b, 0.2f * p)
        // Fork 1
        val forkLen = cLen * 0.35f
        val fAngle1 = cAngle + 0.4 + Math.sin(phase * 0.5 + crack) * 0.2
        val fx1 = cx1 + Math.cos(fAngle1).toFloat * forkLen
        val fy1 = cy1 + Math.sin(fAngle1).toFloat * forkLen * 0.45f
        sb.strokeLine(cx1, cy1, fx1, fy1, 1.5f, dark(r), dark(g), dark(b), 0.25f * p)
        // Fork 2
        val fAngle2 = cAngle - 0.35 + Math.cos(phase * 0.5 + crack) * 0.2
        val fx2 = cx1 + Math.cos(fAngle2).toFloat * forkLen * 0.7f
        val fy2 = cy1 + Math.sin(fAngle2).toFloat * forkLen * 0.7f * 0.45f
        sb.strokeLine(cx1, cy1, fx2, fy2, 1.2f, dark(r), dark(g), dark(b), 0.2f * p)
        // Sub-fork on fork 1
        val sfAngle = fAngle1 + 0.5
        sb.strokeLine(fx1, fy1, fx1 + Math.cos(sfAngle).toFloat * forkLen * 0.4f,
          fy1 + Math.sin(sfAngle).toFloat * forkLen * 0.4f * 0.45f, 0.8f, dark(r), dark(g), dark(b), 0.15f * p)
      ; crack += 1 } }

      // Expanding blast wave effect at creation (fast expanding ring that fades)
      val blastPhase = ((phase * 0.15) % 1.0).toFloat
      if (blastPhase < 0.5f) {
        val blastR = mR * blastPhase * 2f
        val blastA = 0.4f * (1f - blastPhase * 2f) * p
        sb.strokeOval(sx, sy, blastR, blastR * 0.45f, 6f * (1f - blastPhase * 2f),
          bright(r), bright(g), bright(b), blastA, 16)
      }

      // 6 bold expanding rings with thick leading-edge shockwave flash
      var ring = 0; while (ring < 6) {
        val rp = ((phase * 0.22 + ring * 0.167) % 1.0).toFloat
        val ringR = 8f + rp * mR
        val a = Math.max(0f, 0.75f * (1f - rp) * p)
        val colorShift = rp * 0.4f
        val thickness = 5.5f * (1f - rp * 0.2f)
        // Dark outline ring
        sb.strokeOval(sx, sy, ringR + 2f, (ringR + 2f) * 0.45f, thickness + 3f,
          outline(r), outline(g), outline(b), a * 0.5f, 16)
        // Colored ring
        sb.strokeOval(sx, sy, ringR, ringR * 0.45f, thickness,
          mix(r, bright(r), colorShift), mix(g, bright(g), colorShift), mix(b, bright(b), colorShift), a, 16)
        // Thick leading-edge shockwave flash (first 2 rings)
        if (ring < 2 && rp < 0.6f) {
          val shockAlpha = a * 0.65f * (1f - rp / 0.6f)
          sb.strokeOval(sx, sy, ringR + 4f, (ringR + 4f) * 0.45f, thickness + 5f,
            bright(r), bright(g), bright(b), shockAlpha, 16)
          // Extra bright flash on outermost ring
          if (ring == 0 && rp < 0.3f) {
            sb.strokeOval(sx, sy, ringR + 6f, (ringR + 6f) * 0.45f, thickness + 8f,
              1f, 1f, 1f, shockAlpha * 0.4f * (1f - rp / 0.3f), 16)
          }
        }
      ; ring += 1 }

      // Inner pulsing glow that expands
      val innerGlowPhase = ((phase * 0.45) % 1.0).toFloat
      val innerGlowR = 8f + innerGlowPhase * mR * 0.5f
      val innerGlowA = 0.3f * (1f - innerGlowPhase) * p
      sb.fillOvalSoft(sx, sy, innerGlowR, innerGlowR * 0.45f, bright(r), bright(g), bright(b), innerGlowA, 0f, 16)
      // Second inner glow wave offset
      val innerGlow2 = ((phase * 0.45 + 0.5) % 1.0).toFloat
      val igR2 = 8f + innerGlow2 * mR * 0.5f
      sb.fillOvalSoft(sx, sy, igR2, igR2 * 0.45f, dr, dg, db, 0.18f * (1f - innerGlow2) * p, 0f, 14)

      // 10 debris particles kicked up at ring edge with gravity
      { var deb = 0; while (deb < 10) {
        val debPhase = ((phase * 0.3 + deb * 0.1) % 1.0).toFloat
        val debAngle = phase * 0.4 + deb * Math.PI * 2 / 10
        val debDist = 10f + debPhase * mR
        val debRise = debPhase * debPhase * 20f
        val debX = sx + Math.cos(debAngle).toFloat * debDist
        val debY = sy + Math.sin(debAngle).toFloat * debDist * 0.45f - debRise
        val debSz = (3.5f + (1f - debPhase) * 5f)
        val debAlpha = 0.5f * (1f - debPhase) * p
        // Debris outline
        sb.fillOval(debX, debY, debSz + 1f, (debSz + 1f) * 0.7f, outline(r), outline(g), outline(b), debAlpha * 0.4f, 6)
        // Debris body
        sb.fillOval(debX, debY, debSz, debSz * 0.7f, dark(r), dark(g), dark(b), debAlpha, 6)
      ; deb += 1 } }

      // 10 radial energy spark lines with dots
      var spark = 0; while (spark < 10) {
        val sa = phase * 0.6 + spark * Math.PI * 2 / 10
        val sl = mR * 0.5f * p
        val sparkEndX = sx + Math.cos(sa).toFloat * sl
        val sparkEndY = sy + Math.sin(sa).toFloat * sl * 0.45f
        // Spark line outline
        sb.strokeLine(sx, sy, sparkEndX, sparkEndY, 3.5f, outline(r), outline(g), outline(b), 0.2f * p)
        // Spark line body
        sb.strokeLine(sx, sy, sparkEndX, sparkEndY, 2f, r, g, b, 0.35f * p)
        // Bright core
        sb.strokeLine(sx, sy, sparkEndX, sparkEndY, 0.8f, bright(r), bright(g), bright(b), 0.2f * p)
        // Dot at end with glow
        sb.fillOvalSoft(sparkEndX, sparkEndY, 6f, 4.5f, bright(r), bright(g), bright(b), 0.25f * p, 0f, 6)
        sb.fillOval(sparkEndX, sparkEndY, 4f, 3f, bright(r), bright(g), bright(b), 0.55f * p, 6)
      ; spark += 1 }

      // Bold outlined core (3.5px) with multi-layer fill + hot center
      sb.strokeOval(sx, sy, 16f, 10f, 3.5f, outline(r), outline(g), outline(b), 0.8f * p, 14)
      sb.fillOval(sx, sy, 15f, 9.5f, dr, dg, db, 0.9f * p, 14)
      sb.fillOval(sx, sy, 10f, 6f, mix(dr, bright(r), 0.4f), mix(dg, bright(g), 0.4f), mix(db, bright(b), 0.4f), 0.8f * p, 12)
      sb.fillOval(sx, sy, 5.5f, 3.5f,
        mix(bright(r), 1f, _chgBright), mix(bright(g), 1f, _chgBright), mix(bright(b), 1f, _chgBright), 0.97f * p, 10)
      // Hot white center
      sb.fillOval(sx, sy, 2.5f, 1.5f, 1f, 1f, 1f, 0.85f * p, 6)

      // 8 sparkle stars at ring edges
      var star = 0; while (star < 8) {
        val starPhase = ((phase * 0.35 + star * 0.125) % 1.0).toFloat
        val starAngle = phase * 0.6 + star * Math.PI / 4
        val starDist = 10f + starPhase * mR
        val starX = sx + Math.cos(starAngle).toFloat * starDist
        val starY = sy + Math.sin(starAngle).toFloat * starDist * 0.45f
        val starAlpha = 0.55f * (1f - starPhase) * p
        drawSparkleStar(starX, starY, 5.5f * (1f - starPhase * 0.5f),
          bright(r), bright(g), bright(b), starAlpha, sb, phase + star)
      ; star += 1 }
    }

  /** Thick zigzag chain/bolt — CARTOONISH with bold segments, big forks, bright nodes, glow, sparks */
  private def chainProj(r: Float, g: Float, b: Float, worldLen: Float = 6f): Renderer =
    (proj, sx, sy, sb, tick) => {
      beamTip(sx, sy, proj, worldLen)
      val tipX = _tipX; val tipY = _tipY
      val phase = (tick + proj.id * 23) * 0.3
      computeAllDynamics(proj, r, g, b, phase)
      val p = (0.7 + 0.3 * Math.sin(phase * _stPulseMult)).toFloat * dynAlpha
      val dr = _evoR; val dg = _evoG; val db = _evoB
      val ds = dynScale
      val dx = tipX - sx; val dy = tipY - sy
      val len = Math.sqrt(dx * dx + dy * dy).toFloat
      if (len >= 1) {
        val nx = -dy / len; val ny = dx / len
        val dirX = dx / len; val dirY = dy / len
        val zigW = 14f * p * ds
        val segs = 8

        // Ribbon trail behind origin
        drawRibbonTrail(sx, sy, dirX, dirY, dr, dg, db, 0.25f * p, sb, tick, proj.id, 6, 35f * ds * dynTrail, 5f * ds, 1f)

        // Glow halo (40px+) along the chain
        val haloPulse = 0.75f + 0.25f * Math.sin(phase * 1.8).toFloat
        val midX = (sx + tipX) * 0.5f; val midY = (sy + tipY) * 0.5f
        sb.fillOvalSoft(midX, midY, 42f * ds * dynGlow * haloPulse, 30f * ds * dynGlow * haloPulse,
          dr, dg, db, 0.2f * p, 0f, 18)
        sb.fillOvalSoft(sx, sy, 25f * ds * dynGlow, 18f * ds * dynGlow, dr, dg, db, 0.12f * p, 0f, 14)
        sb.fillOvalSoft(tipX, tipY, 28f * ds * dynGlow, 20f * ds * dynGlow, dr, dg, db, 0.15f * p, 0f, 14)

        // Main zigzag segments
        var i = 0; while (i < segs) {
          val t0 = i.toFloat / segs; val t1 = (i + 1).toFloat / segs
          val jitter0 = Math.sin(phase * 2.3 + i * 1.7).toFloat * 5f
          val jitter1 = Math.sin(phase * 2.3 + (i + 1) * 1.7).toFloat * 5f
          val z0 = (if (i % 2 == 0) zigW else -zigW) + jitter0
          val z1 = (if ((i + 1) % 2 == 0) zigW else -zigW) + jitter1
          val x0 = sx + dx * t0 + nx * z0; val y0 = sy + dy * t0 + ny * z0
          val x1 = sx + dx * t1 + nx * z1; val y1 = sy + dy * t1 + ny * z1

          // Dark outline
          sb.strokeLine(x0, y0, x1, y1, 10f * ds, outline(r), outline(g), outline(b), 0.75f * p)
          // Main segment — evolved colors
          sb.strokeLine(x0, y0, x1, y1, 7.5f * ds, dr, dg, db, 0.9f * p)
          if (i % 2 == 0) sb.strokeLine(x0, y0, x1, y1, 3.5f * ds,
            mix(bright(r), 1f, _chgBright), mix(bright(g), 1f, _chgBright), mix(bright(b), 1f, _chgBright), 0.8f * p)

          // Bigger branch forks with sub-forks at alternating joints
          if (i % 2 == 1 && i < segs - 1) {
            val forkLen = 18f + Math.sin(phase * 3 + i * 1.3).toFloat * 6f
            val forkAngle = if (i % 4 == 1) 0.7 else -0.7
            val baseAngle = Math.atan2(ny, nx)
            val forkX = x1 + Math.cos(baseAngle + forkAngle).toFloat * forkLen
            val forkY = y1 + Math.sin(baseAngle + forkAngle).toFloat * forkLen
            sb.strokeLine(x1, y1, forkX, forkY, 5f, outline(r), outline(g), outline(b), 0.45f * p)
            sb.strokeLine(x1, y1, forkX, forkY, 3.5f, r, g, b, 0.55f * p)
            sb.strokeLine(x1, y1, forkX, forkY, 1.5f, bright(r), bright(g), bright(b), 0.3f * p)
            // Sub-fork 1
            val sf1Angle = baseAngle + forkAngle + 0.5
            val sf1Len = forkLen * 0.5f
            val sf1X = forkX + Math.cos(sf1Angle).toFloat * sf1Len
            val sf1Y = forkY + Math.sin(sf1Angle).toFloat * sf1Len
            sb.strokeLine(forkX, forkY, sf1X, sf1Y, 2.5f, r, g, b, 0.35f * p)
            sb.fillOval(sf1X, sf1Y, 2.5f, 2f, bright(r), bright(g), bright(b), 0.4f * p, 5)
            // Sub-fork 2
            val sf2Angle = baseAngle + forkAngle - 0.4
            val sf2X = forkX + Math.cos(sf2Angle).toFloat * sf1Len * 0.7f
            val sf2Y = forkY + Math.sin(sf2Angle).toFloat * sf1Len * 0.7f
            sb.strokeLine(forkX, forkY, sf2X, sf2Y, 2f, r, g, b, 0.3f * p)
            // Fork tip dot — bigger
            sb.fillOval(forkX, forkY, 4.5f, 3.5f, bright(r), bright(g), bright(b), 0.55f * p, 6)
          }
        ; i += 1 }

        // 6 electric spark particles along the chain with small trails
        { var sp = 0; while (sp < 6) {
          val spT = ((tick * 0.08 + sp * 0.167 + proj.id * 0.13) % 1.0).toFloat
          val chainT = sp.toFloat / 5f
          val spJitter = Math.sin(phase * 3.5 + sp * 2.1).toFloat * zigW * 0.8f
          val spx = sx + dx * chainT + nx * spJitter
          val spy = sy + dy * chainT + ny * spJitter
          val spTailX = spx - dirX * 8f * spT; val spTailY = spy - dirY * 8f * spT
          sb.strokeLine(spTailX, spTailY, spx, spy, 2f, bright(r), bright(g), bright(b),
            0.45f * (1f - spT * 0.5f) * p)
          sb.fillOval(spx, spy, 3.5f, 2.5f, 1f, 1f, 1f, 0.5f * (1f - spT * 0.3f) * p, 6)
        ; sp += 1 } }

        // Bright nodes at joints — bigger, with outline
        { var j = 0; while (j <= segs) {
          val t = j.toFloat / segs
          val jitter = Math.sin(phase * 2.3 + j * 1.7).toFloat * 5f
          val z = (if (j % 2 == 0) zigW else -zigW) + jitter
          val jx = sx + dx * t + nx * z; val jy = sy + dy * t + ny * z
          sb.strokeOval(jx, jy, 9f, 7f, 2.5f, outline(r), outline(g), outline(b), 0.65f * p, 8)
          sb.fillOval(jx, jy, 8f, 6.5f, bright(r), bright(g), bright(b), 0.85f * p, 8)
          // Sparkle star at every other joint
          if (j % 3 == 0) {
            drawSparkleStar(jx, jy, 6f, bright(r), bright(g), bright(b), 0.4f * p, sb, phase * 2.5 + j)
          }
        ; j += 3 } }

        // Expanding ring at tip
        val ringPhase = ((phase * 0.4) % 1.0).toFloat
        val ringR = 6f + ringPhase * 18f * ds
        val ringA = 0.5f * (1f - ringPhase) * p
        sb.strokeOval(tipX, tipY, ringR, ringR * 0.7f, 3f * (1f - ringPhase * 0.5f),
          bright(r), bright(g), bright(b), ringA, 12)

        // Spark burst at tip AND origin
        drawSparkBurst(tipX, tipY, dr, dg, db, 0.4f * p, sb, tick, proj.id, 5, 16f * ds)
        drawSparkBurst(sx, sy, dr, dg, db, 0.3f * p, sb, tick, proj.id + 7, 4, 12f * ds)

        // Tip with outline and sparkle — bigger
        sb.strokeOval(tipX, tipY, 12f, 8.5f, 3f, outline(r), outline(g), outline(b), 0.65f * p, 10)
        sb.fillOval(tipX, tipY, 11f, 8f, dr, dg, db, 0.8f * p, 10)
        sb.fillOval(tipX, tipY, 7f, 5f, bright(r), bright(g), bright(b), 0.85f * p, 8)
        drawSparkleStar(tipX, tipY, 9f, bright(r), bright(g), bright(b), 0.45f * p, sb, phase * 2)
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

  /** Compact fist/punch — CARTOONISH with bold outline, impact burst, POW lines, shockwave, ground shadow */
  private def fistProj(r: Float, g: Float, b: Float, size: Float = 14f): Renderer =
    (proj, sx, sy, sb, tick) => {
      val phase = (tick + proj.id * 23) * 0.4
      computeAllDynamics(proj, r, g, b, phase)
      val p = (0.65 + 0.35 * Math.sin(phase * _stPulseMult)).toFloat * dynAlpha
      val dr = _evoR; val dg = _evoG; val db = _evoB
      val ds = dynScale
      val sz = size * 1.4f * ds
      screenDir(proj)
      val ndx = _sdx; val ndy = _sdy
      val perpX = -ndy; val perpY = ndx
      val frontX = sx + ndx * sz * 0.3f; val frontY = sy + ndy * sz * 0.3f

      // Speed lines behind — manga punch style
      drawSpeedLines(sx, sy, ndx, ndy, dr, dg, db, 0.4f * p, sb, 7, sz * 2.2f)

      // Ribbon trail behind
      drawRibbonTrail(sx, sy, ndx, ndy, dr, dg, db, 0.3f * p, sb, tick, proj.id, 6, 35f * ds * dynTrail, 8f * ds, 1.5f)

      // Ground shadow
      sb.fillOval(sx, sy + sz * 0.45f, sz * 1.5f, sz * 0.3f, 0f, 0f, 0f, 0.22f * p, 14)

      // 3 impact burst expanding rings + shockwave flash
      val ringPulse = 0.6f + 0.4f * Math.sin(phase * 3).toFloat
      // Ring 1 — outer
      sb.strokeOval(frontX, frontY, sz * 2.4f * ringPulse, sz * 1.8f * ringPulse,
        3.5f, outline(r), outline(g), outline(b), 0.25f * p, 16)
      sb.strokeOval(frontX, frontY, sz * 2.2f * ringPulse, sz * 1.6f * ringPulse,
        3f, r, g, b, 0.35f * p, 14)
      // Ring 2 — mid
      val ringP2 = 0.5f + 0.5f * Math.sin(phase * 3 + 1.0).toFloat
      sb.strokeOval(frontX, frontY, sz * 1.5f * ringP2, sz * 1.1f * ringP2,
        2.5f, bright(r), bright(g), bright(b), 0.3f * p, 12)
      // Ring 3 — inner
      val ringP3 = 0.4f + 0.6f * Math.sin(phase * 3 + 2.0).toFloat
      sb.strokeOval(frontX, frontY, sz * 0.9f * ringP3, sz * 0.65f * ringP3,
        2f, 1f, 1f, 1f, 0.2f * p, 10)
      // Shockwave flash (expanding and fading)
      val shockPhase = ((phase * 0.35) % 1.0).toFloat
      if (shockPhase < 0.5f) {
        val shockR = sz * 1.5f + shockPhase * sz * 2.5f
        val shockA = 0.35f * (1f - shockPhase * 2f) * p
        sb.strokeOval(frontX, frontY, shockR, shockR * 0.7f, 5f * (1f - shockPhase * 2f),
          bright(r), bright(g), bright(b), shockA, 14)
      }

      // 6 comic-book "POW" impact lines — bold radial lines from front
      { var pow = 0; while (pow < 6) {
        val powAngle = phase * 0.5 + pow * Math.PI * 2 / 6
        val powInner = sz * 0.6f; val powOuter = sz * 1.6f + Math.sin(phase * 2.5 + pow * 1.3).toFloat * sz * 0.4f
        val ix = frontX + Math.cos(powAngle).toFloat * powInner
        val iy = frontY + Math.sin(powAngle).toFloat * powInner * 0.6f
        val ox = frontX + Math.cos(powAngle).toFloat * powOuter
        val oy = frontY + Math.sin(powAngle).toFloat * powOuter * 0.6f
        sb.strokeLine(ix, iy, ox, oy, 4f, outline(r), outline(g), outline(b), 0.4f * p)
        sb.strokeLine(ix, iy, ox, oy, 2.5f, bright(r), bright(g), bright(b), 0.55f * p)
      ; pow += 1 } }

      // Bold dark cartoon outline
      sb.strokeOval(sx, sy, sz * 1.1f, sz * 0.85f, 4f,
        outline(r), outline(g), outline(b), 0.88f * p, 14)
      // Fist body — evolved colors
      sb.fillOval(sx, sy, sz * 1.02f, sz * 0.78f, dr, dg, db, 0.95f * p, 14)

      // 3 knuckle detail lines across the fist
      { var k = 0; while (k < 3) {
        val kOff = (k - 1f) * sz * 0.22f
        val kx0 = sx + perpX * kOff + ndx * sz * 0.15f
        val ky0 = sy + perpY * kOff + ndy * sz * 0.15f
        val kx1 = sx + perpX * kOff + ndx * sz * 0.35f
        val ky1 = sy + perpY * kOff + ndy * sz * 0.35f
        sb.strokeLine(kx0, ky0, kx1, ky1, 2f, dark(r), dark(g), dark(b), 0.4f * p)
      ; k += 1 } }

      // Knuckle highlight
      sb.fillOval(sx + ndx * sz * 0.2f, sy + ndy * sz * 0.2f - 2f,
        sz * 0.5f, sz * 0.38f, bright(r), bright(g), bright(b), 0.6f * p, 10)
      // Cartoon shine spot
      sb.fillOval(sx - sz * 0.15f, sy - sz * 0.15f, sz * 0.22f, sz * 0.16f,
        1f, 1f, 1f, 0.45f * p, 6)
      // Dark bottom shading — larger
      sb.fillOval(sx, sy + sz * 0.18f, sz * 0.92f, sz * 0.48f,
        dark(r), dark(g), dark(b), 0.38f, 12)

      // 8 impact spark particles radiating from front
      { var sp = 0; while (sp < 8) {
        val spAngle = tick * 0.15 + sp * Math.PI * 2 / 8 + proj.id * 0.7
        val spDist = sz * (0.5f + 0.5f * Math.sin(tick * 0.2 + sp * 1.5).toFloat)
        val spx = frontX + Math.cos(spAngle).toFloat * spDist
        val spy = frontY + Math.sin(spAngle).toFloat * spDist * 0.6f
        val spLen = sz * 0.35f
        val sex = spx + Math.cos(spAngle).toFloat * spLen
        val sey = spy + Math.sin(spAngle).toFloat * spLen * 0.6f
        sb.strokeLine(spx, spy, sex, sey, 2.5f, bright(r), bright(g), bright(b),
          (0.35 + 0.25 * Math.sin(tick * 0.3 + sp * 2.0)).toFloat * p)
        sb.fillOval(sex, sey, 3f, 2.5f, 1f, 1f, 1f, 0.4f * p, 5)
      ; sp += 1 } }

      // 3 sparkle stars
      { var star = 0; while (star < 3) {
        val starPhase = ((phase * 0.45 + star * 0.33) % 1.0).toFloat
        val starAngle = phase * 1.2 + star * Math.PI * 2 / 3
        val starDist = sz * 1.0f + starPhase * sz * 0.8f
        val starX = sx + Math.cos(starAngle).toFloat * starDist
        val starY = sy + Math.sin(starAngle).toFloat * starDist * 0.55f
        drawSparkleStar(starX, starY, 5f * (1f - starPhase * 0.4f) * ds,
          bright(r), bright(g), bright(b), 0.45f * (1f - starPhase) * p, sb, phase * 2 + star)
      ; star += 1 } }

      // Charge crackle
      drawChargeCrackle(sx, sy, sz, r, g, b, p, sb, phase, proj.chargeLevel)
      drawReturnGhosts(sx, sy, sz, dr, dg, db, p, sb, proj)
    }

  /** Wide crescent wave/slash — CARTOONISH with bold outline, glow halo, energy node, wisps, sparkles */
  private def wave(r: Float, g: Float, b: Float, spread: Float = 32f): Renderer =
    (proj, sx, sy, sb, tick) => {
      val phase = (tick + proj.id * 41) * 0.4
      computeAllDynamics(proj, r, g, b, phase)
      val p = (0.65 + 0.35 * Math.sin(phase * _stPulseMult)).toFloat * dynAlpha
      val dr = _evoR; val dg = _evoG; val db = _evoB
      val ds = dynScale
      val sp = spread * 1.3f * ds
      screenDir(proj)
      val ndx = _sdx; val ndy = _sdy
      val perpX = -ndy; val perpY = ndx
      val tipX = sx + ndx * sp; val tipY = sy + ndy * sp * 0.6f
      val w1x = sx + perpX * sp; val w1y = sy + perpY * sp * 0.6f
      val w2x = sx - perpX * sp; val w2y = sy - perpY * sp * 0.6f

      // Big glow halo (50px+) behind crescent
      val haloPulse = 0.75f + 0.25f * Math.sin(phase * 1.6).toFloat
      sb.fillOvalSoft(sx + ndx * sp * 0.3f, sy + ndy * sp * 0.2f,
        52f * ds * dynGlow * haloPulse, 38f * ds * dynGlow * haloPulse, dr, dg, db, 0.22f * p, 0f, 20)
      sb.fillOvalSoft(sx + ndx * sp * 0.3f, sy + ndy * sp * 0.2f,
        35f * ds * dynGlow, 25f * ds * dynGlow, bright(r), bright(g), bright(b), 0.1f * p, 0f, 16)

      // Trailing afterimages — 3 fading copies WITH outlines
      var af = 1; while (af <= 3) {
        val afOff = af * 11f
        val afAlpha = 0.25f * (1f - af * 0.25f) * p
        _polyXs4(0) = tipX - ndx * afOff; _polyXs4(1) = w1x - ndx * afOff; _polyXs4(2) = sx - ndx * (4 + afOff); _polyXs4(3) = w2x - ndx * afOff
        _polyYs4(0) = tipY - ndy * afOff; _polyYs4(1) = w1y - ndy * afOff; _polyYs4(2) = sy - ndy * (3 + afOff); _polyYs4(3) = w2y - ndy * afOff
        sb.fillPolygon(_polyXs4, _polyYs4, 4, r, g, b, afAlpha)
        // Outline on each afterimage
        sb.strokePolygon(_polyXs4, _polyYs4, 4, 2f, outline(r), outline(g), outline(b), afAlpha * 0.5f)
      ; af += 1 }

      // Main crescent fill — evolved colors
      _polyXs4(0) = tipX; _polyXs4(1) = w1x; _polyXs4(2) = sx - ndx * 4; _polyXs4(3) = w2x
      _polyYs4(0) = tipY; _polyYs4(1) = w1y; _polyYs4(2) = sy - ndy * 3; _polyYs4(3) = w2y
      sb.fillPolygon(_polyXs4, _polyYs4, 4, dr, dg, db, 0.65f * p)

      // Prominent dark outline on all 4 polygon edges
      sb.strokePolygon(_polyXs4, _polyYs4, 4, 4.5f, outline(r), outline(g), outline(b), 0.8f * p)
      // Bright leading edge — even thicker
      sb.strokePolygon(_polyXs4, _polyYs4, 4, 2.5f, bright(r), bright(g), bright(b), 0.9f * p)

      // Internal energy flow lines — bolder with outline layer
      var i = 0; while (i < 5) {
        val off = (i - 2f) * sp * 0.2f
        val flowPhase = Math.sin(phase * 2.5 + i * 1.8).toFloat * sp * 0.15f
        val fx0 = sx + perpX * off - ndx * 3; val fy0 = sy + perpY * off * 0.6f - ndy * 2
        val fx1 = sx + perpX * off + ndx * (sp * 0.55f + flowPhase)
        val fy1 = sy + perpY * off * 0.6f + ndy * (sp * 0.35f + flowPhase * 0.5f)
        val flowA = (0.22 + 0.13 * Math.sin(phase * 3 + i * 2.3)).toFloat * p
        // Outline layer
        sb.strokeLine(fx0, fy0, fx1, fy1, 4f, outline(r), outline(g), outline(b), flowA * 0.4f)
        // Main flow line
        sb.strokeLine(fx0, fy0, fx1, fy1, 2.5f, r, g, b, flowA)
        // Bright core
        sb.strokeLine(fx0, fy0, fx1, fy1, 1f, bright(r), bright(g), bright(b), flowA * 0.6f)
      ; i += 1 }

      // Center pulsing energy node with outline
      val nodePulse = 0.7f + 0.3f * Math.sin(phase * 2.8).toFloat
      val nodeSz = sp * 0.18f * nodePulse
      sb.strokeOval(sx + ndx * sp * 0.35f, sy + ndy * sp * 0.2f, nodeSz + 2f, (nodeSz + 2f) * 0.7f,
        2.5f, outline(r), outline(g), outline(b), 0.6f * p, 10)
      sb.fillOval(sx + ndx * sp * 0.35f, sy + ndy * sp * 0.2f, nodeSz, nodeSz * 0.7f,
        bright(r), bright(g), bright(b), 0.75f * p, 10)
      sb.fillOval(sx + ndx * sp * 0.35f, sy + ndy * sp * 0.2f, nodeSz * 0.5f, nodeSz * 0.35f,
        1f, 1f, 1f, 0.45f * p, 6)

      // 6 trailing energy wisps with gravity
      { var w = 0; while (w < 6) {
        val wT = ((tick * 0.04 + w * 0.167 + proj.id * 0.13) % 1.0).toFloat
        val wOff = (w - 2.5f) * sp * 0.25f
        val wx = sx + perpX * wOff - ndx * (5f + wT * 30f)
        val wy = sy + perpY * wOff * 0.6f - ndy * (3f + wT * 20f) + wT * wT * 12f
        val wSz = (4f + (1f - wT) * 5f) * ds
        val wA = 0.35f * (1f - wT) * p
        sb.fillOval(wx, wy, wSz, wSz * 0.65f, r, g, b, wA, 6)
        sb.fillOval(wx, wy, wSz * 0.5f, wSz * 0.35f, bright(r), bright(g), bright(b), wA * 0.5f, 4)
      ; w += 1 } }

      // Sparkle stars along the leading edge
      { var s = 0; while (s < 6) {
        val starPhase = ((phase * 0.5 + s * 0.167) % 1.0).toFloat
        val edgeT = s.toFloat / 5
        val ex = tipX * (1f - edgeT) + (if (s % 2 == 0) w1x else w2x) * edgeT
        val ey = tipY * (1f - edgeT) + (if (s % 2 == 0) w1y else w2y) * edgeT
        val starAlpha = 0.6f * (1f - starPhase) * p
        drawSparkleStar(ex, ey, 5.5f * (1f - starPhase * 0.4f) * ds,
          bright(r), bright(g), bright(b), starAlpha, sb, phase * 1.5 + s)
      ; s += 1 } }

      // 8 leading edge spark particles — bigger with small trails
      { var sp2 = 0; while (sp2 < 8) {
        val t = ((tick * 0.1 + sp2 * 0.125 + proj.id * 0.11) % 1.0).toFloat
        val edgeT = sp2.toFloat / 7
        val ex = tipX * (1f - edgeT) + (if (sp2 % 2 == 0) w1x else w2x) * edgeT
        val ey = tipY * (1f - edgeT) + (if (sp2 % 2 == 0) w1y else w2y) * edgeT
        val sparkSz = 6f * ds
        // Small trail behind each spark
        sb.strokeLine(ex - ndx * 6f, ey - ndy * 4f, ex + ndx * t * 8, ey + ndy * t * 5,
          1.5f, r, g, b, Math.max(0f, 0.3f * (1f - t) * p))
        sb.fillOval(ex + ndx * t * 8, ey + ndy * t * 5, sparkSz, sparkSz * 0.7f,
          bright(r), bright(g), bright(b), Math.max(0f, 0.6f * (1f - t) * p), 8)
      ; sp2 += 1 } }

      // Charge crackle
      drawChargeCrackle(sx + ndx * sp * 0.3f, sy + ndy * sp * 0.2f, sp * 0.5f, r, g, b, p, sb, phase, proj.chargeLevel)
      drawReturnGhosts(sx, sy, sp * 0.5f, dr, dg, db, p, sb, proj)
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
    sb.fillOvalSoft(sx, sy, 55f * ds * dynGlow * haloPulse, 44f * ds * dynGlow * haloPulse,
      0.3f, 0.05f, 0.5f, 0.25f * p, 0f, 20)
    sb.fillOvalSoft(sx, sy, 38f * ds * dynGlow, 30f * ds * dynGlow, 0.2f, 0.02f, 0.4f, 0.15f * p, 0f, 16)

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
    sb.fillOvalSoft(sx, sy, 55f * ds * dynGlow * haloPulse, 44f * ds * dynGlow * haloPulse,
      0.2f, 0.08f, 0.4f, 0.22f * p, 0f, 20)
    sb.fillOvalSoft(sx, sy, 38f * ds * dynGlow, 30f * ds * dynGlow, 0.15f, 0.05f, 0.3f, 0.12f * p, 0f, 16)

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
    val p = (0.85 + 0.15 * Math.sin(phase)).toFloat
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy

    val mainR = if (isVirus) 0.85f else 0.1f
    val mainG = if (isVirus) 0.15f else 0.95f
    val mainB = if (isVirus) 0.15f else 0.55f

    // Core: grid of rectangular "pixels" that shift each frame
    val gridSize = 3
    var gx = -gridSize; while (gx <= gridSize) {
      var gy = -gridSize; while (gy <= gridSize) {
        if (gx * gx + gy * gy <= gridSize * gridSize + 1) {
          val pixelPhase = Math.sin(phase * 3 + gx * 2.7 + gy * 1.9 + proj.id * 0.5).toFloat
          if (pixelPhase > -0.3f) {
            val px = sx + gx * 4f + Math.sin(phase * 2 + gx + gy).toFloat * 1f
            val py = sy + gy * 3f + Math.cos(phase * 1.5 + gx - gy).toFloat * 0.8f
            val pixAlpha = (0.5f + 0.4f * pixelPhase) * p
            sb.fillRect(px - 1.5f, py - 1.2f, 3f, 2.4f, mainR, mainG, mainB, pixAlpha)
          }
        }
      ; gy += 1 }
    ; gx += 1 }

    // Scan-line flicker
    val scanY = sy + ((phase * 8 % 16) - 8).toFloat
    sb.fillRect(sx - 10f, scanY - 0.5f, 20f, 1f, mainR, mainG, mainB, 0.3f * p)

    // Virus glitch-distortion: offset copies
    if (isVirus) {
      val glitchOff = Math.sin(phase * 7).toFloat * 4f
      sb.fillRect(sx + glitchOff - 6f, sy - 5f, 12f, 2f, 0.9f, 0.1f, 0.1f, 0.2f * p)
      sb.fillRect(sx - glitchOff - 4f, sy + 3f, 8f, 2f, 0.1f, 0.9f, 0.1f, 0.15f * p)
    }

    // Trail: falling/streaming rectangular particles (matrix rain style)
    var i = 0; while (i < 8) {
      val t = ((tick * 0.07 + i * 0.125 + proj.id * 0.13) % 1.0).toFloat
      val trailX = sx - ndx * t * 40 + Math.sin(phase + i * 2.3).toFloat * 4
      val trailY = sy - ndy * t * 40 + t * 12f
      val tw = 2f + (1f - t) * 2f
      val th = 1.5f + (1f - t) * 2f
      sb.fillRect(trailX - tw * 0.5f, trailY - th * 0.5f, tw, th,
        mainR, mainG, mainB, 0.4f * (1f - t) * p)
    ; i += 1 }

    // Bright center
    sb.fillOval(sx, sy, 5f, 4f, bright(mainR), bright(mainG), bright(mainB), 0.8f * p, 8)
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
    sb.fillOvalSoft(sx, sy, 60f * ds * dynGlow * haloPulse, 48f * ds * dynGlow * haloPulse,
      1f, 0.92f, 0.55f, 0.3f * p, 0f, 20)
    sb.fillOvalSoft(sx, sy, 42f * ds * dynGlow, 34f * ds * dynGlow, 1f, 0.95f, 0.65f, 0.15f * p, 0f, 16)

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
    sb.fillOvalSoft(sx, sy, 50f * ds * dynGlow * haloPulse, 40f * ds * dynGlow * haloPulse,
      0.2f, 0.02f, 0.3f, 0.22f * p, 0f, 20)
    sb.fillOvalSoft(sx, sy, 35f * ds * dynGlow, 28f * ds * dynGlow, 0.15f, 0.01f, 0.25f, 0.12f * p, 0f, 16)

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
    sb.fillOvalSoft(sx, sy, 50f * ds * dynGlow * haloPulse, 40f * ds * dynGlow * haloPulse, 0.97f, 0.97f, 1f, 0.3f * p, 0f, 20)
    sb.fillOvalSoft(sx, sy, 34f * ds * dynGlow, 26f * ds * dynGlow, 1f, 1f, 1f, 0.15f * p, 0f, 16)

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
    sb.fillOvalSoft(sx, sy, 50f * ds * dynGlow * haloPulse, 40f * ds * dynGlow * haloPulse,
      0.35f, 0.8f, 0.2f, 0.25f * p, 0f, 20)
    sb.fillOvalSoft(sx, sy, 35f * ds * dynGlow, 28f * ds * dynGlow, 0.3f, 0.7f, 0.15f, 0.12f * p, 0f, 16)

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
    ProjectileType.ICE_BEAM     -> beamProj(0.4f, 0.8f, 1f, 7f, 9f),
    ProjectileType.AXE          -> spinner(0.65f, 0.55f, 0.4f, 36f, 2),
    ProjectileType.ROPE         -> chainProj(0.7f, 0.5f, 0.3f, 6f),
    ProjectileType.SPEAR        -> physProj(0.55f, 0.5f, 0.35f, 7f),
    ProjectileType.SOUL_BOLT    -> energyBolt(0.3f, 0.9f, 0.4f, 20f, 3),
    ProjectileType.HAUNT        -> energyBolt(0.5f, 0.7f, 0.95f, 24f, 4),
    ProjectileType.ARCANE_BOLT  -> energyBolt(0.65f, 0.3f, 0.95f, 22f, 2),
    ProjectileType.FIREBALL     -> (drawFireball _),
    ProjectileType.SPLASH       -> aoeRing(0.3f, 0.6f, 1f, 50f),
    ProjectileType.TIDAL_WAVE   -> (drawTidalWave _),
    ProjectileType.GEYSER       -> (drawGeyser _),
    ProjectileType.BULLET       -> bulletProj(0.75f, 0.7f, 0.55f),
    ProjectileType.GRENADE      -> lobbed(0.4f, 0.45f, 0.3f, 18f),
    ProjectileType.ROCKET       -> (drawRocket _),
    ProjectileType.TALON        -> (drawTalon _),
    ProjectileType.GUST         -> wave(0.8f, 0.9f, 1f, 32f),
    ProjectileType.SHURIKEN     -> spinner(0.6f, 0.6f, 0.65f, 32f, 4),
    ProjectileType.POISON_DART  -> (drawPoisonDart _),
    ProjectileType.CHAIN_BOLT   -> chainProj(0.5f, 0.5f, 0.55f, 5.5f),
    ProjectileType.LOCKDOWN_CHAIN -> chainProj(0.4f, 0.4f, 0.45f, 6f),
    ProjectileType.SNARE_MINE   -> lobbed(0.3f, 0.6f, 1f, 20f),
    ProjectileType.KATANA       -> spinner(0.75f, 0.75f, 0.8f, 32f, 2),
    ProjectileType.SWORD_WAVE   -> (drawSwordWave _),
    ProjectileType.PLAGUE_BOLT  -> energyBolt(0.45f, 0.75f, 0.15f, 20f, 3),
    ProjectileType.MIASMA       -> aoeRing(0.35f, 0.65f, 0.15f, 45f),
    ProjectileType.BLIGHT_BOMB  -> lobbed(0.35f, 0.55f, 0.15f, 18f),
    ProjectileType.BLOOD_FANG   -> (drawBloodFang _),
    ProjectileType.BLOOD_SIPHON -> beamProj(0.85f, 0.15f, 0.1f, 6f, 7f),
    ProjectileType.BAT_SWARM    -> (drawBatSwarm _),

    // ── Elemental (31-52) ──
    ProjectileType.FLAME_BOLT   -> energyBolt(1f, 0.5f, 0.1f, 20f, 1),
    ProjectileType.FROST_SHARD  -> (drawFrostShard _),
    ProjectileType.LIGHTNING    -> (drawLightning _),
    ProjectileType.CHAIN_LIGHTNING -> (drawLightning _),
    ProjectileType.THUNDER_STRIKE -> (drawThunderStrike _),
    ProjectileType.BOULDER      -> ((proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int) => drawBoulder(proj, sx, sy, sb, tick, 24f)),
    ProjectileType.SEISMIC_SLAM -> aoeRing(0.6f, 0.45f, 0.25f, 55f),
    ProjectileType.WIND_BLADE   -> wave(0.8f, 0.95f, 1f, 30f),
    ProjectileType.MAGMA_BALL   -> energyBolt(1f, 0.45f, 0.05f, 24f, 1),
    ProjectileType.ERUPTION     -> aoeRing(1f, 0.45f, 0.05f, 50f),
    ProjectileType.FROST_TRAP   -> lobbed(0.45f, 0.75f, 1f, 18f),
    ProjectileType.SAND_SHOT    -> energyBolt(0.9f, 0.75f, 0.4f, 18f),
    ProjectileType.SAND_BLAST   -> wave(0.85f, 0.75f, 0.4f, 28f),
    ProjectileType.THORN        -> (drawThorn _),
    ProjectileType.VINE_WHIP    -> beamProj(0.2f, 0.6f, 0.15f, 6f, 7f),
    ProjectileType.THORN_WALL   -> aoeRing(0.25f, 0.55f, 0.15f, 35f),
    ProjectileType.INFERNO_BLAST -> (drawInfernoBlast _),
    ProjectileType.GLACIER_SPIKE -> (drawFrostShard _),
    ProjectileType.MUD_GLOB     -> energyBolt(0.4f, 0.3f, 0.15f, 22f),
    ProjectileType.MUD_BOMB     -> lobbed(0.35f, 0.28f, 0.12f, 20f),
    ProjectileType.EMBER_SHOT   -> energyBolt(1f, 0.55f, 0.15f, 18f, 1),
    ProjectileType.AVALANCHE_CRUSH -> lobbed(0.65f, 0.8f, 0.95f, 48f),

    // ── Undead/Dark (53-69) ──
    ProjectileType.DEATH_BOLT   -> energyBolt(0.2f, 0.5f, 0.05f, 22f, 3),
    ProjectileType.RAISE_DEAD   -> (drawRaiseDead _),
    ProjectileType.BONE_AXE     -> spinner(0.92f, 0.9f, 0.82f, 36f, 2),
    ProjectileType.BONE_THROW   -> spinner(0.92f, 0.88f, 0.8f, 18f, 2),
    ProjectileType.WAIL         -> (drawWail _),
    ProjectileType.SOUL_DRAIN   -> beamProj(0.35f, 0.85f, 0.25f, 6f, 8f),
    ProjectileType.CLAW_SWIPE   -> (drawClawSwipe _),
    ProjectileType.DEVOUR       -> (drawDevour _),
    ProjectileType.SCYTHE       -> (drawScythe _),
    ProjectileType.REAP         -> (drawReap _),
    ProjectileType.SHADOW_BOLT  -> (drawShadowBolt _),
    ProjectileType.CURSED_BLADE -> spinner(0.55f, 0.15f, 0.25f, 32f, 4),
    ProjectileType.LIFE_DRAIN   -> beamProj(0.85f, 0.1f, 0.1f, 6f, 7f),
    ProjectileType.SHOVEL       -> lobbed(0.6f, 0.6f, 0.55f, 30f),
    ProjectileType.HEAD_THROW   -> lobbed(0.75f, 0.7f, 0.6f, 20f),
    ProjectileType.BANDAGE_WHIP -> beamProj(0.85f, 0.75f, 0.6f, 6f, 6f),
    ProjectileType.CURSE        -> (drawCurse _),

    // ── Medieval/Fantasy (70-79) ──
    ProjectileType.HOLY_BLADE   -> spinner(1f, 0.95f, 0.5f, 32f, 4),
    ProjectileType.HOLY_BOLT    -> (drawHolyBolt _),
    ProjectileType.ARROW        -> (drawEnchantedArrow _),
    ProjectileType.POISON_ARROW -> (drawPoisonArrow _),
    ProjectileType.SONIC_WAVE   -> wave(0.75f, 0.55f, 0.95f, 32f),
    ProjectileType.SONIC_BOOM   -> aoeRing(0.75f, 0.55f, 0.95f, 55f),
    ProjectileType.FIST         -> fistProj(0.75f, 0.6f, 0.45f, 28f),
    ProjectileType.SMITE        -> (drawHolyBolt _),
    ProjectileType.CHARM        -> energyBolt(1f, 0.45f, 0.65f, 18f, 4),
    ProjectileType.CARD         -> spinner(0.92f, 0.92f, 0.88f, 16f, 4),

    // ── Sci-Fi/Tech (80-89) ──
    ProjectileType.DATA_BOLT    -> ((proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int) => drawDataBolt(proj, sx, sy, sb, tick, isVirus = false)),
    ProjectileType.VIRUS        -> ((proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int) => drawDataBolt(proj, sx, sy, sb, tick, isVirus = true)),
    ProjectileType.LASER        -> beamProj(1f, 0.25f, 0.2f, 8f, 5f),
    ProjectileType.GRAVITY_BALL -> (drawGravityBall _),
    ProjectileType.GRAVITY_WELL -> (drawGravityBall _),
    ProjectileType.TESLA_COIL   -> (drawLightning _),
    ProjectileType.NANO_BOLT    -> energyBolt(0.25f, 0.85f, 0.65f, 16f, 4),
    ProjectileType.VOID_BOLT    -> (drawVoidBolt _),
    ProjectileType.RAILGUN      -> beamProj(0.25f, 0.55f, 1f, 10f, 5f),
    ProjectileType.CLUSTER_BOMB -> lobbed(0.55f, 0.55f, 0.45f, 20f),

    // ── Nature/Beast (90-93) ──
    ProjectileType.VENOM_BOLT   -> (drawVenomBolt _),
    ProjectileType.WEB_SHOT     -> (drawWebShot _),
    ProjectileType.STINGER      -> (drawStinger _),
    ProjectileType.ACID_BOMB    -> (drawVenomBolt _),

    // ── AoE Root (94-101) ──
    ProjectileType.SEISMIC_ROOT -> aoeRing(0.55f, 0.45f, 0.25f, 40f),
    ProjectileType.ROOT_GROWTH  -> aoeRing(0.25f, 0.6f, 0.15f, 38f),
    ProjectileType.WEB_TRAP     -> (drawWebShot _),
    ProjectileType.TREMOR_SLAM  -> aoeRing(0.55f, 0.45f, 0.25f, 50f),
    ProjectileType.ENTANGLE     -> aoeRing(0.2f, 0.55f, 0.15f, 38f),
    ProjectileType.STONE_GAZE   -> beamProj(0.65f, 0.6f, 0.5f, 7f, 8f),
    ProjectileType.INK_SNARE    -> lobbed(0.15f, 0.15f, 0.2f, 20f),
    ProjectileType.GRAVITY_LOCK -> (drawGravityBall _),

    // ── Character-specific (102-111) ──
    ProjectileType.KNIFE        -> spinner(0.8f, 0.82f, 0.85f, 32f, 2),
    ProjectileType.STING        -> energyBolt(0.45f, 0.9f, 1f, 16f),
    ProjectileType.HAMMER       -> lobbed(0.55f, 0.55f, 0.6f, 34f),
    ProjectileType.HORN         -> (drawHorn _),
    ProjectileType.MYSTIC_BOLT  -> energyBolt(0.55f, 0.35f, 0.85f, 20f, 2),
    ProjectileType.PETRIFY      -> beamProj(0.65f, 0.6f, 0.5f, 7f, 8f),
    ProjectileType.GRAB         -> beamProj(0.5f, 0.4f, 0.25f, 8f, 9f),
    ProjectileType.JAW          -> (drawJaw _),
    ProjectileType.TONGUE       -> beamProj(0.9f, 0.35f, 0.4f, 8f, 8f),
    ProjectileType.ACID_FLASK   -> (drawVenomBolt _),

    // ── Roster audit: new differentiation projectiles (112+) ──
    ProjectileType.BOOMERANG_BLADE -> spinner(0.55f, 0.15f, 0.25f, 32f, 2),
    ProjectileType.VORTEX_BOMB     -> aoeRing(0.35f, 0.15f, 0.55f, 45f),
    ProjectileType.CHAIN_LIGHTNING_FORK -> (drawLightning _),
    ProjectileType.SNIPER_BEAM     -> beamProj(0.3f, 0.6f, 1f, 10f, 4f),
    ProjectileType.MOMENTUM_STRIKE -> wave(0.95f, 0.6f, 0.2f, 30f),
    ProjectileType.LEECH_BOLT      -> energyBolt(0.5f, 0.15f, 0.35f, 22f, 3),
    ProjectileType.RICOCHET_SHARD  -> (drawFrostShard _),
    ProjectileType.FLAME_WAVE      -> wave(1f, 0.5f, 0.1f, 35f),
    ProjectileType.POISON_CLOUD    -> aoeRing(0.3f, 0.75f, 0.2f, 50f),
    ProjectileType.BONE_BOOMERANG  -> spinner(0.92f, 0.88f, 0.8f, 20f, 2),
    ProjectileType.GRAVITY_LANCE   -> beamProj(0.35f, 0.15f, 0.55f, 8f, 6f),
    ProjectileType.SHADOW_HAUNT    -> (drawVoidBolt _),
    ProjectileType.CHARGE_FIST     -> fistProj(0.85f, 0.65f, 0.35f, 28f),
    ProjectileType.ACID_SPRAY      -> wave(0.25f, 0.85f, 0.3f, 28f),
    ProjectileType.ECHO_BOLT       -> energyBolt(0.7f, 0.5f, 0.95f, 20f, 2),
    ProjectileType.FLAME_TRAIL     -> energyBolt(1f, 0.6f, 0.15f, 22f, 1),
    ProjectileType.STAR_BOLT       -> energyBolt(0.95f, 0.9f, 0.45f, 22f, 4),
    ProjectileType.RUNE_BOLT       -> energyBolt(0.9f, 0.75f, 0.35f, 18f, 2),
    ProjectileType.SOUL_HARVEST    -> aoeRing(0.3f, 0.85f, 0.25f, 45f),
    ProjectileType.OVERCLOCK_BEAM  -> aoeRing(0.2f, 0.9f, 0.7f, 40f),
    ProjectileType.NAPALM_STRIKE   -> lobbed(0.95f, 0.45f, 0.1f, 22f),
    ProjectileType.THROWN_BOULDER  -> ((proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int) => drawBoulder(proj, sx, sy, sb, tick, 28f)),
    ProjectileType.EYE_BEAM        -> beamProj(0.9f, 0.25f, 0.2f, 8f, 6f),

    // ── DPS balance variants (same visual as base type) ──
    ProjectileType.SOUL_BOLT_HEAVY   -> energyBolt(0.3f, 0.9f, 0.4f, 20f, 3),
    ProjectileType.FROST_SHARD_LIGHT -> (drawFrostShard _),
    ProjectileType.FLAME_BOLT_HEAVY  -> energyBolt(1f, 0.5f, 0.1f, 20f, 1),
    ProjectileType.FLAME_BOLT_LIGHT  -> energyBolt(1f, 0.5f, 0.1f, 20f, 1),
    ProjectileType.BULLET_HEAVY      -> bulletProj(0.75f, 0.7f, 0.55f),
    ProjectileType.BULLET_LIGHT      -> bulletProj(0.75f, 0.7f, 0.55f),
    ProjectileType.SONIC_WAVE_HEAVY  -> wave(0.75f, 0.55f, 0.95f, 32f),
    ProjectileType.SONIC_WAVE_MED    -> wave(0.75f, 0.55f, 0.95f, 32f),
    ProjectileType.THORN_LIGHT       -> (drawThorn _),
    ProjectileType.LASER_HEAVY       -> beamProj(1f, 0.25f, 0.2f, 8f, 5f),
    ProjectileType.LASER_LIGHT       -> beamProj(1f, 0.25f, 0.2f, 8f, 5f),
    ProjectileType.ARROW_HEAVY       -> (drawEnchantedArrow _),
    ProjectileType.ARROW_LIGHT       -> (drawEnchantedArrow _),
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
    val p = (0.65 + 0.35 * Math.sin(phase)).toFloat
    intToRGB(proj.colorRGB)
    val r = _r; val g = _g; val b = _b
    val cr = bright(r); val cg = bright(g); val cb = bright(b)

    // Soft glow — bigger
    sb.strokeLineSoft(sx, sy, tipX, tipY, 40f, r, g, b, 0.25f * p)
    // Dark outline
    sb.strokeLine(sx, sy, tipX, tipY, 16f, outline(r), outline(g), outline(b), 0.5f * p)
    // Layered beam — thicker
    sb.strokeLine(sx, sy, tipX, tipY, 12f, r, g, b, 0.7f * p)
    sb.strokeLine(sx, sy, tipX, tipY, 5f, cr, cg, cb, 0.95f * p)

    // Particles along beam — bigger
    val dx = tipX - sx; val dy = tipY - sy
    val len = Math.sqrt(dx * dx + dy * dy).toFloat
    if (len > 1) {
      val nx = dx / len; val ny = dy / len
      var i = 0; while (i < 8) {
        val t = ((tick * 0.08 + i * 0.125 + proj.id * 0.13) % 1.0).toFloat
        val px = sx + dx * t + ny * Math.sin(phase + i * 2.1).toFloat * 16f
        val py = sy + dy * t - nx * Math.sin(phase + i * 2.1).toFloat * 16f
        val pa = (0.6 * (1.0 - Math.abs(t - 0.5) * 2.0)).toFloat
        sb.fillOval(px, py, 7f, 5f, cr, cg, cb, pa, 8)
      ; i += 1 }
    }

    // Tip orb — bigger with cartoon outline
    val orbR = 16f * p
    sb.fillOvalSoft(tipX, tipY, orbR * 2.5f, orbR * 1.8f, r, g, b, 0.4f * p, 0f, 16)
    sb.strokeOval(tipX, tipY, orbR * 1.1f, orbR * 0.8f, 3f, outline(r), outline(g), outline(b), 0.7f * p, 12)
    sb.fillOval(tipX, tipY, orbR, orbR * 0.72f, r, g, b, 0.8f * p, 14)
    sb.fillOval(tipX, tipY, orbR * 0.4f, orbR * 0.28f, cr, cg, cb, 0.95f, 10)
    // Cartoon highlight
    sb.fillOval(tipX - orbR * 0.2f, tipY - orbR * 0.15f, orbR * 0.25f, orbR * 0.18f,
      1f, 1f, 1f, 0.4f * p, 6)

    // Expanding rings at tip — 3 rings
    var ring = 0; while (ring < 3) {
      val rp = ((tick * 0.1 + ring * 0.33 + proj.id * 0.17) % 1.0).toFloat
      val ringR = 8f + rp * 36f
      sb.strokeOval(tipX, tipY, ringR, ringR * 0.6f, 2.5f * (1f - rp), r, g, b, 0.45f * (1f - rp) * p, 12)
    ; ring += 1 }

    // Sparkle star at tip
    drawSparkleStar(tipX, tipY, 8f * p, cr, cg, cb, 0.4f * p, sb, phase * 1.5)
  }

  /** Tentacle - thick wavy tendrils with suckers */
  private def drawTentacle(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    beamTip(sx, sy, proj, 5f)
    val tipX = _tipX; val tipY = _tipY
    val phase = (tick + proj.id * 23) * 0.4
    val p = (0.9 + 0.1 * Math.sin(phase)).toFloat
    val dx = tipX - sx; val dy = tipY - sy
    val len = Math.sqrt(dx * dx + dy * dy).toFloat
    if (len < 1) return
    val nx = -dy / len; val ny = dx / len

    var strand = 0; while (strand < 4) {
      val off = (strand - 1.5f) * 4f
      val segs = 12
      var i = 0; while (i < segs) {
        val t0 = i.toFloat / segs; val t1 = (i + 1).toFloat / segs
        val w0 = (Math.sin(phase * 2 + t0 * Math.PI * 3.5 + strand * 1.6) * (7f + strand * 2.5f) * (1f - t0 * 0.3f)).toFloat
        val w1 = (Math.sin(phase * 2 + t1 * Math.PI * 3.5 + strand * 1.6) * (7f + strand * 2.5f) * (1f - t1 * 0.3f)).toFloat
        val x0 = sx + dx * t0 + nx * (off * 0.3f + w0); val y0 = sy + dy * t0 + ny * (off * 0.3f + w0)
        val x1 = sx + dx * t1 + nx * (off * 0.3f + w1); val y1 = sy + dy * t1 + ny * (off * 0.3f + w1)
        // Thick solid tentacle
        sb.strokeLine(x0, y0, x1, y1, 7f, 0.12f, 0.55f, 0.2f, 0.8f * p)
        sb.strokeLine(x0, y0, x1, y1, 3f, 0.2f, 0.8f, 0.35f, 0.65f * p)
        sb.strokeLine(x0, y0, x1, y1, 1.2f, 0.35f, 0.95f, 0.5f, 0.5f * p)
      ; i += 1 }
      // Suckers
      var s = 0; while (s < 3) {
        val t = 0.2f + s * 0.25f
        val w = (Math.sin(phase * 2 + t * Math.PI * 3.5 + strand * 1.6) * (7f + strand * 2.5f) * (1f - t * 0.3f)).toFloat
        val spx = sx + dx * t + nx * (off * 0.3f + w)
        val spy = sy + dy * t + ny * (off * 0.3f + w)
        val sp = (0.6 + 0.4 * Math.sin(phase * 3 + strand * 2 + s * 1.7)).toFloat
        sb.fillOval(spx, spy, 4f * sp, 3.5f * sp, 0.5f, 0.15f, 0.7f, 0.6f * sp, 8)
      ; s += 1 }
    ; strand += 1 }
    // Dripping
    { var i = 0; while (i < 4) {
      val t = ((tick * 0.04 + i * 0.25 + proj.id * 0.11) % 1.0).toFloat
      val dripX = sx + dx * (0.15f + i * 0.18f) + nx * Math.sin(phase + i * 2.3).toFloat * 7f
      val dripY = sy + dy * (0.15f + i * 0.18f) + ny * Math.sin(phase + i * 2.3).toFloat * 7f + t * 14f
      sb.fillOval(dripX, dripY, 3.5f, 3f, 0.25f, 0.85f, 0.35f, 0.5f * (1f - t), 6)
    ; i += 1 } }
  }

  /** Fireball — CARTOONISH: huge fire orb with bold outline, big tongues, dense embers */
  private def drawFireball(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 29) * 0.35
    val p = (0.6 + 0.4 * Math.sin(phase)).toFloat
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy

    // Speed lines
    drawSpeedLines(sx, sy, ndx, ndy, 1f, 0.4f, 0f, 0.3f * p, sb, 4, 30f)

    // Massive outer heat halo with dramatic pulse
    val haloPulse = (0.7 + 0.3 * Math.sin(phase * 2.5)).toFloat
    sb.fillOvalSoft(sx, sy, 80f * haloPulse, 62f * haloPulse, 1f, 0.3f, 0f, 0.4f * p, 0f, 20)

    // Heat shimmer above — bigger
    var shim = 0; while (shim < 4) {
      val shimY = sy - 18f - shim * 10f
      val shimX = sx + Math.sin(phase * 2 + shim * 1.5).toFloat * 8f
      val shimR = 10f + shim * 5f
      sb.fillOvalSoft(shimX, shimY, shimR, shimR * 0.7f, 1f, 0.5f, 0.1f, 0.08f * p, 0f, 10)
    ; shim += 1 }

    // 10 fire tongue licks — bigger and wilder
    var i = 0; while (i < 10) {
      val fa = phase * 2 + i * Math.PI * 2 / 10
      val fl = 18f + Math.sin(phase * 3 + i * 2.1).toFloat * 10f
      val fx = sx + Math.cos(fa).toFloat * 16f
      val fy = sy + Math.sin(fa).toFloat * 9f - fl * 0.5f
      // Flame tongue outline
      sb.fillOval(fx, fy, 8f, fl * 0.4f, 0.15f, 0.02f, 0f, 0.5f * p, 8)
      sb.fillOval(fx, fy, 6f, fl * 0.35f, 1f, 0.45f, 0.02f, 0.6f * p, 8)
    ; i += 1 }

    // Bold dark cartoon outline on fire body
    sb.strokeOval(sx, sy, 30f, 22f, 4f, 0.15f, 0.02f, 0f, 0.8f * p, 16)
    // Fire body — bigger
    sb.fillOval(sx, sy, 28f, 21f, 0.9f, 0.3f, 0.02f, 0.95f * p, 16)
    sb.fillOval(sx, sy, 20f, 14f, 1f, 0.55f, 0.05f, 0.95f * p, 14)
    sb.fillOval(sx, sy, 12f, 8f, 1f, 0.8f, 0.3f, 0.98f, 10)
    // Cartoon highlight
    sb.fillOval(sx - 5f, sy - 5f, 8f, 5f, 1f, 1f, 0.8f, 0.45f * p, 8)
    // White-hot center
    sb.fillOval(sx, sy, 5f, 4f, 1f, 1f, 0.9f, 0.95f, 8)

    // Dense ember trail — 14 embers
    { var i = 0; while (i < 14) {
      val t = ((tick * 0.07 + i * 0.071 + proj.id * 0.13) % 1.0).toFloat
      val spread = Math.sin(phase + i * 2.3).toFloat * 12f
      val fx = sx - ndx * t * 45 + spread
      val fy = sy - ndy * t * 45 - t * 14f
      val s = 5f + (1f - t) * 7f
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
      val starDist = 20f + starPhase * 16f
      val starX = sx + Math.cos(starAngle).toFloat * starDist
      val starY = sy + Math.sin(starAngle).toFloat * starDist * 0.55f
      drawSparkleStar(starX, starY, 5f * (1f - starPhase * 0.5f),
        1f, 0.9f, 0.4f, 0.5f * (1f - starPhase) * p, sb, phase * 2 + i)
    ; i += 1 } }
  }

  /** Rocket — CARTOONISH: bold outline, big exhaust, cartoon smoke puffs */
  private def drawRocket(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    beamTip(sx, sy, proj, 5f)
    val tipX = _tipX; val tipY = _tipY
    val phase = (tick + proj.id * 29) * 0.35
    val p = (0.7 + 0.3 * Math.sin(phase)).toFloat
    val dx = tipX - sx; val dy = tipY - sy
    val len = Math.sqrt(dx * dx + dy * dy).toFloat
    if (len < 1) return
    val nx = dx / len; val ny = dy / len
    val perpX = -ny; val perpY = nx

    // Big exhaust glow
    sb.fillOvalSoft(sx - dx * 0.2f, sy - dy * 0.2f, 36f, 28f, 1f, 0.5f, 0.1f, 0.4f * p, 0f, 14)

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
    sb.strokeLine(sx, sy, tipX, tipY, 16f, 0.1f, 0.1f, 0.08f, 0.85f * p)
    sb.strokeLine(sx, sy, tipX, tipY, 13f, 0.4f, 0.42f, 0.3f, 0.95f * p)
    sb.strokeLine(sx, sy, tipX, tipY, 6f, 0.55f, 0.58f, 0.45f, 0.65f * p)
    // Red band — bolder
    val bandX = sx + dx * 0.45f; val bandY = sy + dy * 0.45f
    sb.strokeLine(bandX - dx * 0.05f, bandY - dy * 0.05f, bandX + dx * 0.05f, bandY + dy * 0.05f,
      16f, 0.15f, 0.02f, 0.01f, 0.7f * p)
    sb.strokeLine(bandX - dx * 0.05f, bandY - dy * 0.05f, bandX + dx * 0.05f, bandY + dy * 0.05f,
      14f, 0.95f, 0.2f, 0.1f, 0.85f * p)
    // Fins — bigger with outline
    var f = -1; while (f <= 1) {
      _polyXs3(0) = sx; _polyXs3(1) = (sx - nx * 6f + perpX * f * 11f).toFloat; _polyXs3(2) = (sx - nx * 18f).toFloat
      _polyYs3(0) = sy; _polyYs3(1) = (sy - ny * 6f + perpY * f * 11f).toFloat; _polyYs3(2) = (sy - ny * 18f).toFloat
      sb.fillPolygon(_polyXs3, _polyYs3, 3, 0.45f, 0.48f, 0.35f, 0.85f * p)
      sb.strokePolygon(_polyXs3, _polyYs3, 3, 2f, 0.15f, 0.15f, 0.12f, 0.7f * p)
    ; f += 2 }
    // Nosecone — bigger with outline
    val noseX = tipX + nx * 20f; val noseY = tipY + ny * 20f
    _polyXs3(0) = noseX; _polyXs3(1) = tipX + perpX * 8f; _polyXs3(2) = tipX - perpX * 8f
    _polyYs3(0) = noseY; _polyYs3(1) = tipY + perpY * 8f; _polyYs3(2) = tipY - perpY * 8f
    sb.fillPolygon(_polyXs3, _polyYs3, 3, 0.7f, 0.7f, 0.65f, 0.95f * p)
    sb.strokePolygon(_polyXs3, _polyYs3, 3, 2.5f, 0.15f, 0.15f, 0.12f, 0.8f * p)
    // Cartoon shine on nosecone
    sb.fillOval(tipX + nx * 8f - perpX * 2f, tipY + ny * 8f - perpY * 2f,
      4f, 3f, 1f, 1f, 1f, 0.35f * p, 6)
  }

  /** Lightning — CARTOONISH: thick bold bolt with dark outline, huge branches, sparkle stars */
  private def drawLightning(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    beamTip(sx, sy, proj, 6f)
    val tipX = _tipX; val tipY = _tipY
    val phase = (tick + proj.id * 41) * 0.5
    val flicker = (0.6 + 0.4 * Math.sin(phase * 8)).toFloat
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
      val jitter = Math.sin(regenSeed * 0.9 + i * 2.7).toFloat * 16f +
        Math.cos(regenSeed * 1.3 + i * 3.9).toFloat * 7f
      _boltXs(i) = sx + dx * t + nx * jitter
      _boltYs(i) = sy + dy * t + ny * jitter
    ; i += 1 }

    // Dark cartoon outline bolt
    { var i = 0; while (i < segs) { sb.strokeLine(_boltXs(i), _boltYs(i), _boltXs(i + 1), _boltYs(i + 1), 12f, 0.1f, 0.08f, 0.2f, 0.7f * flicker); i += 1 } }
    // Main bolt — thicker
    { var i = 0; while (i < segs) { sb.strokeLine(_boltXs(i), _boltYs(i), _boltXs(i + 1), _boltYs(i + 1), 9f, 0.5f, 0.4f, 1f, 0.9f * flicker); i += 1 } }
    // White-hot core — thicker
    { var i = 0; while (i < segs) { sb.strokeLine(_boltXs(i), _boltYs(i), _boltXs(i + 1), _boltYs(i + 1), 4f, 0.95f, 0.92f, 1f, 0.98f * flicker); i += 1 } }

    // 6 branch forks — bigger with outlines
    var b = 0; while (b < 6) {
      val bSeg = 1 + b % segs
      if (bSeg < segs) {
        val bAngle = Math.PI * 0.4 * (if (b % 2 == 0) 1 else -1) +
          Math.sin(regenSeed * 0.7 + b * 2.3).toFloat * 0.5
        val bLen = 24f + Math.sin(regenSeed * 0.5 + b * 2.1).toFloat * 10f
        val bex = _boltXs(bSeg) + Math.cos(bAngle).toFloat * bLen
        val bey = _boltYs(bSeg) + Math.sin(bAngle).toFloat * bLen * 0.5f
        // Branch outline
        sb.strokeLine(_boltXs(bSeg), _boltYs(bSeg), bex, bey, 6f, 0.1f, 0.08f, 0.2f, 0.35f * flicker)
        sb.strokeLine(_boltXs(bSeg), _boltYs(bSeg), bex, bey, 4.5f, 0.5f, 0.4f, 1f, 0.55f * flicker)
        sb.strokeLine(_boltXs(bSeg), _boltYs(bSeg), bex, bey, 2f, 0.9f, 0.85f, 1f, 0.7f * flicker)
        // Branch tip spark
        sb.fillOval(bex, bey, 4f, 3f, 0.9f, 0.85f, 1f, 0.6f * flicker, 6)
      }
    ; b += 1 }

    // Ambient electric sparks — 7, bigger, brighter
    { var i = 0; while (i < 7) {
      val sparkSeg = 1 + (i * 2) % segs
      val sparkOff = Math.sin(phase * 6 + i * 3.7).toFloat * 14f
      val sparkX = _boltXs(sparkSeg) + nx * sparkOff
      val sparkY = _boltYs(sparkSeg) + ny * sparkOff
      val sparkAlpha = (0.35 + 0.45 * Math.sin(phase * 9 + i * 2.9)).toFloat * flicker
      sb.fillOval(sparkX, sparkY, 4.5f, 3.5f, 0.85f, 0.8f, 1f, sparkAlpha, 8)
    ; i += 1 } }

    // Tip flash — bigger with cartoon outline and sparkle star
    val tipPulse = (0.6 + 0.4 * Math.sin(phase * 4)).toFloat
    sb.fillOvalSoft(tipX, tipY, 24f * tipPulse, 18f * tipPulse, 0.5f, 0.4f, 1f, 0.35f * flicker, 0f, 12)
    sb.strokeOval(tipX, tipY, 14f * tipPulse, 10f * tipPulse, 3f, 0.1f, 0.08f, 0.2f, 0.6f * flicker, 10)
    sb.fillOval(tipX, tipY, 13f * tipPulse, 9f * tipPulse, 0.85f, 0.8f, 1f, 0.75f * flicker, 10)
    drawSparkleStar(tipX, tipY, 10f * tipPulse, 1f, 1f, 1f, 0.5f * flicker, sb, phase * 2)

    // Expanding ring at tip
    val ringP = ((phase * 0.7) % 1.0).toFloat
    sb.strokeOval(tipX, tipY, 8f + ringP * 20f, 5.5f + ringP * 14f,
      2.5f * (1f - ringP), 0.85f, 0.8f, 1f, 0.4f * (1f - ringP) * flicker, 10)
  }

  /** Thunder Strike — CARTOONISH: huge bold bolt, massive ground impact with debris */
  private def drawThunderStrike(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 31) * 0.5
    val flicker = (0.5 + 0.5 * Math.sin(phase * 8)).toFloat

    // Massive ground flash
    sb.fillOvalSoft(sx, sy, 100f, 70f, 1f, 0.95f, 0.4f, 0.3f * flicker, 0f, 20)

    // Zigzag bolt from above — wider
    val pts = 9
    _thunderXs(0) = sx - 8; _thunderYs(0) = sy - 70
    _thunderXs(1) = sx + 18; _thunderYs(1) = sy - 45
    _thunderXs(2) = sx + 2; _thunderYs(2) = sy - 35
    _thunderXs(3) = sx + 20; _thunderYs(3) = sy - 12
    _thunderXs(4) = sx + 7; _thunderYs(4) = sy - 6
    _thunderXs(5) = sx + 22; _thunderYs(5) = sy + 22
    _thunderXs(6) = sx + 9; _thunderYs(6) = sy + 18
    _thunderXs(7) = sx + 18; _thunderYs(7) = sy + 38
    _thunderXs(8) = sx + 5; _thunderYs(8) = sy + 38

    // Dark cartoon outline
    { var i = 0; while (i < pts - 1) { sb.strokeLine(_thunderXs(i), _thunderYs(i), _thunderXs(i + 1), _thunderYs(i + 1), 18f, 0.15f, 0.12f, 0.03f, 0.7f * flicker); i += 1 } }
    // Soft glow
    { var i = 0; while (i < pts - 1) { sb.strokeLineSoft(_thunderXs(i), _thunderYs(i), _thunderXs(i + 1), _thunderYs(i + 1), 28f, 1f, 0.85f, 0.2f, 0.25f * flicker); i += 1 } }
    // Main bolt — thick
    { var i = 0; while (i < pts - 1) { sb.strokeLine(_thunderXs(i), _thunderYs(i), _thunderXs(i + 1), _thunderYs(i + 1), 12f, 1f, 0.9f, 0.35f, 0.85f * flicker); i += 1 } }
    // White-hot core
    { var i = 0; while (i < pts - 1) { sb.strokeLine(_thunderXs(i), _thunderYs(i), _thunderXs(i + 1), _thunderYs(i + 1), 5f, 1f, 1f, 0.9f, 0.98f * flicker); i += 1 } }

    // Ground impact — bigger with debris
    sb.strokeOval(sx, sy + 36f, 26f, 9f, 3f, 0.2f, 0.15f, 0.02f, 0.5f * flicker, 14)
    sb.fillOval(sx, sy + 36f, 24f, 8f, 1f, 0.9f, 0.3f, 0.6f * flicker, 14)

    // Impact debris sparks — bigger and more
    var i2 = 0; while (i2 < 12) {
      val angle = phase * 2 + i2 * Math.PI / 6
      val dist = 16f + Math.sin(phase * 3 + i2).toFloat * 10f
      sb.fillOval((sx + Math.cos(angle).toFloat * dist), (sy + 34 + Math.sin(angle).toFloat * dist * 0.3f).toFloat,
        4.5f, 3.5f, 1f, 1f, 0.6f, 0.65f * flicker, 8)
    ; i2 += 1 }

    // Sparkle star at impact point
    drawSparkleStar(sx, sy + 36f, 12f * flicker, 1f, 1f, 0.7f, 0.5f * flicker, sb, phase * 2)
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

    // 4-layer rock: thick dark cartoon outline (4px), stone body, rotating shadow face, rotating highlight face
    // 4px dark cartoon outline
    sb.strokeOval(sx, sy - bounce, rr * 1.08f, rr * 0.95f, 4f, 0.1f, 0.06f, 0.02f, 0.88f * p, 18)
    // Stone body — main fill
    sb.fillOval(sx, sy - bounce, rr * 1.02f, rr * 0.9f, 0.42f, 0.34f, 0.22f, 0.97f * p, 18)
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

  /** Tidal Wave - massive cresting water wall */
  private def drawTidalWave(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 29) * 0.35
    computeAllDynamics(proj, 0.25f, 0.5f, 0.9f, phase)
    val p = (0.85 + 0.15 * Math.sin(phase * _stPulseMult)).toFloat * dynAlpha
    val ds = dynScale
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val perpX = -ndy; val perpY = ndx

    // Speed lines behind
    drawSpeedLines(sx, sy, ndx, ndy, 0.25f, 0.5f, 0.9f, 0.22f * p, sb, 5 + (_lifePct * 2).toInt, 32f * ds)

    // Ribbon trail
    drawRibbonTrail(sx, sy, ndx, ndy, 0.2f, 0.45f, 0.85f, 0.28f * p, sb, tick, proj.id,
      10, 45f * ds * dynTrail, 10f * ds, 1.5f)

    // Dynamic glow halo
    val haloPulse = (0.7f + 0.3f * Math.sin(phase * 2.0).toFloat)
    sb.fillOvalSoft(sx, sy - 6f * ds, 55f * ds * dynGlow * haloPulse, 44f * ds * dynGlow * haloPulse,
      0.25f, 0.55f, 1f, 0.2f * p, 0f, 20)

    val crestW = 42f * ds; val crestH = 28f * ds
    var i = 0; while (i < 10) {
      val t = i.toFloat / 9
      val off = (t - 0.5f) * 2f
      val height = (1f - off * off) * crestH * p
      _waveXs(i) = sx + perpX * off * crestW + ndx * height * 0.3f
      _waveYs(i) = sy + perpY * off * crestW * 0.6f + ndy * height * 0.3f - height
    ; i += 1 }

    // Bold dark outline on crest (4px)
    { var i = 0; while (i < 9) {
      sb.strokeLine(_waveXs(i), _waveYs(i), _waveXs(i + 1), _waveYs(i + 1),
        12f * ds, 0.06f, 0.15f, 0.35f, 0.6f * p)
    ; i += 1 } }
    // 4-layer water body: dark blue outline -> deep blue -> light blue -> foam white cap
    // Deep blue layer
    { var i = 0; while (i < 9) {
      sb.strokeLine(_waveXs(i), _waveYs(i), _waveXs(i + 1), _waveYs(i + 1),
        9f * ds, 0.15f, 0.35f, 0.7f, 0.65f * p)
    ; i += 1 } }
    // Light blue layer
    { var i = 0; while (i < 9) {
      sb.strokeLine(_waveXs(i), _waveYs(i), _waveXs(i + 1), _waveYs(i + 1),
        6f * ds, 0.3f, 0.6f, 0.92f, 0.75f * p)
    ; i += 1 } }
    // Bright blue core
    { var i = 0; while (i < 9) {
      sb.strokeLine(_waveXs(i), _waveYs(i), _waveXs(i + 1), _waveYs(i + 1),
        3f * ds, 0.5f, 0.8f, 1f, 0.85f * p)
    ; i += 1 } }
    // Foam white cap on crest top
    { var i = 2; while (i < 8) {
      sb.strokeLine(_waveXs(i), _waveYs(i), _waveXs(i + 1), _waveYs(i + 1),
        3.5f * ds, 0.88f, 0.96f, 1f, 0.55f * p)
    ; i += 1 } }

    // 12 foam bubble particles on crest with outlines
    { var i = 0; while (i < 12) {
      val foamT = ((tick * 0.09 + i * 0.083 + proj.id * 0.07) % 1.0).toFloat
      val foamIdx = 1 + (i % 8)
      if (foamIdx < 9) {
        val fx = _waveXs(foamIdx) + Math.sin(phase * 5 + i * 3.1).toFloat * 4f * ds
        val fy = _waveYs(foamIdx) + Math.cos(phase * 4 + i * 2.7).toFloat * 3f * ds
        val fSz = (3f + Math.sin(phase * 6 + i * 1.9).toFloat * 1.5f) * ds
        // Foam outline
        sb.fillOval(fx, fy, fSz + 0.5f, fSz * 0.85f + 0.5f, 0.15f, 0.35f, 0.65f, 0.2f * p, 6)
        // Foam body
        sb.fillOval(fx, fy, fSz, fSz * 0.85f, 0.82f, 0.95f, 1f, (0.35 + 0.25 * Math.sin(phase * 6 + i * 1.9)).toFloat * p, 6)
        // Foam highlight
        sb.fillOval(fx - fSz * 0.2f, fy - fSz * 0.2f, fSz * 0.35f, fSz * 0.3f, 1f, 1f, 1f, 0.25f * p, 4)
      }
    ; i += 1 } }

    // Base splash with 3-layer pool
    sb.strokeOval(sx, sy, 40f * ds, 14f * ds, 3f, 0.08f, 0.2f, 0.5f, 0.45f * p, 14)
    sb.fillOval(sx, sy, 38f * ds, 13f * ds, 0.18f, 0.4f, 0.78f, 0.4f * p, 14)
    sb.fillOval(sx, sy, 28f * ds, 10f * ds, 0.3f, 0.58f, 0.92f, 0.35f * p, 12)
    sb.fillOval(sx, sy, 16f * ds, 6f * ds, 0.5f, 0.78f, 1f, 0.3f * p, 10)

    // 10 spray particles with gravity arcs
    { var i = 0; while (i < 10) {
      val t = ((tick * 0.06 + i * 0.1 + proj.id * 0.11) % 1.0).toFloat
      val sprayIdx = Math.min(8, 1 + (i % 8))
      val spAngle = phase * 1.5 + i * Math.PI * 2 / 10
      val dropX = _waveXs(sprayIdx) + ndx * t * 14f * ds + Math.cos(spAngle).toFloat * t * 10f * ds
      val dropY = _waveYs(sprayIdx) + ndy * t * 14f * ds - t * 10f * ds + t * t * 22f * ds
      val spSz = (4f + (1f - t) * 4f) * ds
      // Spray outline
      sb.fillOval(dropX, dropY, spSz + 0.5f, spSz * 0.8f + 0.5f, 0.12f, 0.3f, 0.6f, 0.2f * (1f - t) * p, 6)
      // Spray body
      sb.fillOval(dropX, dropY, spSz, spSz * 0.8f, 0.55f, 0.82f, 1f, 0.55f * (1f - t) * p, 6)
      // Spray highlight
      sb.fillOval(dropX - spSz * 0.2f, dropY - spSz * 0.25f, spSz * 0.3f, spSz * 0.25f,
        0.85f, 0.96f, 1f, 0.3f * (1f - t) * p, 4)
    ; i += 1 } }

    // Sparkle stars
    { var i = 0; while (i < 4) {
      val starPhase = ((phase * 0.5 + i * 0.25) % 1.0).toFloat
      val starAngle = phase * 1.3 + i * Math.PI * 2 / 4
      val starDist = (16f + starPhase * 18f) * ds
      val starX = sx + Math.cos(starAngle).toFloat * starDist
      val starY = sy + Math.sin(starAngle).toFloat * starDist * 0.55f - 6f * ds
      drawSparkleStar(starX, starY, 5f * (1f - starPhase * 0.5f) * ds,
        0.65f, 0.9f, 1f, 0.45f * (1f - starPhase) * p, sb, phase * 2 + i)
    ; i += 1 } }

    drawChargeCrackle(sx, sy - crestH * 0.3f, 22f * ds, 0.25f, 0.5f, 0.9f, p, sb, phase, proj.chargeLevel)
    drawReturnGhosts(sx, sy, 22f * ds, 0.25f, 0.5f, 0.9f, p, sb, proj)
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
    sb.fillOvalSoft(sx, sy - columnH * 0.35f, 75f * ds * dynGlow * mistPulse, 60f * ds * dynGlow * mistPulse,
      0.35f, 0.65f, 1f, 0.18f * p, 0f, 20)
    sb.fillOvalSoft(sx, sy - columnH * 0.5f, 50f * ds * dynGlow, 42f * ds * dynGlow,
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
    sb.fillOvalSoft(sx, sy, 50f * ds * dynGlow * haloPulse, 40f * ds * dynGlow * haloPulse,
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
    val p = (0.85 + 0.15 * Math.sin(phase)).toFloat
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy

    // Dark cloud — wider for scattered formation
    sb.fillOvalSoft(sx, sy, 36f, 26f, 0.1f, 0f, 0.15f, 0.3f * p, 0f, 14)

    // 7 bats: 3 small, 3 medium, 1 large with scattered formation
    var bat = 0; while (bat < 7) {
      val bAngle = phase * 1.5 + bat * Math.PI * 2 / 7
      // Wider orbital radius range for more chaotic cloud
      val bDist = 6f + Math.sin(phase * 2 + bat * 1.7).toFloat * 9f + (bat % 3) * 3f
      val bx = sx + Math.cos(bAngle).toFloat * bDist
      val by = sy + Math.sin(bAngle).toFloat * bDist * 0.5f
      // Each bat flaps at slightly different speed
      val flapSpeed = 7f + bat * 0.7f
      val wingFlap = Math.sin(phase * flapSpeed + bat * 2.3).toFloat

      // Varied sizes: bat 0-2 small, 3-5 medium, 6 large
      val batScale = if (bat < 3) 0.7f else if (bat < 6) 1f else 1.4f
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
    sb.fillOvalSoft(sx, sy - 8f * ds, 48f * ds * dynGlow, 38f * ds * dynGlow,
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
    sb.fillOvalSoft(gx, sy, 52f * ds * dynGlow * haloPulse, 42f * ds * dynGlow * haloPulse,
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
    sb.fillOvalSoft(sx, sy, 55f * ds * dynGlow * haloPulse, 44f * ds * dynGlow * haloPulse,
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
    sb.fillOvalSoft(sx, sy, 50f * ds * dynGlow, 38f * ds * dynGlow,
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
    val p = (0.85 + 0.15 * Math.sin(phase)).toFloat
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val baseAngle = Math.atan2(ndy, ndx) + Math.sin(phase * 2) * 0.3
    val arcR = 50f; val arcLen = Math.PI * 1.2

    // Dark aura
    sb.fillOvalSoft(sx, sy, arcR * 1.1f, arcR * 0.7f, 0.08f, 0f, 0.12f, 0.25f * p, 0f, 16)

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
        4f, 3f, 0.55f, 0.3f, 0.8f, 0.5f * wPhase * p, 6)
    ; i += 1 } }
  }

  /** Shadow Bolt - dark void mass with 8 varied tendrils, void ripples, swirling core and glowing eyes */
  private def drawShadowBolt(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 43) * 0.35
    val p = (0.85 + 0.15 * Math.sin(phase)).toFloat
    val w1 = Math.sin(phase * 2.3).toFloat * 4
    val w2 = Math.cos(phase * 1.7).toFloat * 3

    // Void aura
    sb.fillOvalSoft(sx + w1, sy + w2, 34f, 26f, 0.12f, 0f, 0.18f, 0.3f * p, 0f, 14)

    // Void ripple — 2 expanding dark rings from center
    var vr = 0; while (vr < 2) {
      val vrP = ((phase * 0.4 + vr * 0.5) % 1.0).toFloat
      val vrR = 6f + vrP * 22f
      sb.strokeOval(sx, sy, vrR, vrR * 0.6f, 2f * (1f - vrP), 0.15f, 0.02f, 0.25f,
        0.25f * (1f - vrP) * p, 10)
    ; vr += 1 }

    // 8 shadow tendrils with varying thickness (some thick, some wispy)
    var i = 0; while (i < 8) {
      val angle = phase * 1.2 + i * Math.PI * 2 / 8
      val tLen = 22f + Math.sin(phase * 2.5 + i * 1.9).toFloat * 8
      val ex = sx + Math.cos(angle).toFloat * tLen
      val ey = sy + Math.sin(angle).toFloat * tLen * 0.5f
      val thick = if (i % 3 == 0) 5f else if (i % 3 == 1) 3.5f else 2f
      sb.strokeLine(sx, sy, ex, ey, thick, 0.2f, 0.02f, 0.35f, 0.4f * p)
      sb.strokeLine(sx, sy, ex, ey, thick * 0.5f, 0.08f, 0f, 0.15f, 0.65f * p)
      // Curling extension at tip
      val curlAngle = angle + Math.sin(phase * 3 + i * 2.1) * 0.8
      val curlX = ex + Math.cos(curlAngle).toFloat * 7f
      val curlY = ey + Math.sin(curlAngle).toFloat * 4f
      sb.strokeLine(ex, ey, curlX, curlY, thick * 0.4f, 0.15f, 0.01f, 0.28f, 0.3f * p)
    ; i += 1 }

    // Dark mass — bigger with bold outline
    sb.strokeOval(sx, sy, 22f, 16f, 3.5f, 0.02f, 0f, 0.04f, 0.85f * p, 16)
    sb.fillOval(sx, sy, 20f, 15f, 0.06f, 0f, 0.1f, 0.95f, 16)

    // Inner swirling dark particles — more
    var sp = 0; while (sp < 6) {
      val spAngle = phase * 2.8 + sp * Math.PI / 3
      val spDist = 6f + Math.sin(phase * 1.5 + sp * 2.1).toFloat * 4f
      val spx = sx + Math.cos(spAngle).toFloat * spDist
      val spy = sy + Math.sin(spAngle).toFloat * spDist * 0.6f
      sb.fillOval(spx, spy, 3f, 2.5f, 0.02f, 0f, 0.05f, 0.75f * p, 6)
    ; sp += 1 }

    // Black center
    sb.fillOval(sx, sy, 7f, 5.5f, 0f, 0f, 0f, 0.98f, 10)

    // Larger purple eyes with bright glow — more expressive
    val eyePulse1 = (0.4 + 0.6 * Math.sin(phase * 4)).toFloat
    val eyePulse2 = (0.4 + 0.6 * Math.sin(phase * 4 + 1.2)).toFloat
    // Glow halos
    sb.fillOvalSoft(sx - 6, sy - 3f, 12f, 9f, 0.5f, 0.1f, 0.8f, 0.2f * eyePulse1 * p, 0f, 10)
    sb.fillOvalSoft(sx + 7, sy - 3f, 12f, 9f, 0.5f, 0.1f, 0.8f, 0.2f * eyePulse2 * p, 0f, 10)
    // Eye outline
    sb.strokeOval(sx - 6, sy - 3f, 5f, 4f, 1.5f, 0f, 0f, 0f, 0.8f * p, 8)
    sb.strokeOval(sx + 7, sy - 3f, 5f, 4f, 1.5f, 0f, 0f, 0f, 0.8f * p, 8)
    // Eyes — bigger and brighter
    sb.fillOval(sx - 6, sy - 3f, 4.5f, 3.5f, 0.7f, 0.2f, 1f, 0.85f * eyePulse1 * p, 8)
    sb.fillOval(sx + 7, sy - 3f, 4.5f, 3.5f, 0.7f, 0.2f, 1f, 0.85f * eyePulse2 * p, 8)
    // Eye highlights
    sb.fillOval(sx - 7f, sy - 4f, 1.5f, 1.2f, 1f, 1f, 1f, 0.4f * eyePulse1 * p, 4)
    sb.fillOval(sx + 6f, sy - 4f, 1.5f, 1.2f, 1f, 1f, 1f, 0.4f * eyePulse2 * p, 4)
  }

  /** Inferno Blast — CARTOONISH: massive fire vortex with bold spirals and sparkle stars */
  private def drawInfernoBlast(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 23) * 0.4
    val p = (0.6 + 0.4 * Math.sin(phase)).toFloat
    val r = 45f

    // Massive heat halo
    sb.fillOvalSoft(sx, sy, r * 3f, r * 2.4f, 1f, 0.2f, 0f, 0.3f * p, 0f, 20)

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
    sb.strokeOval(sx, sy, r * 0.52f, r * 0.4f, 4f, 0.15f, 0.02f, 0f, 0.75f * p, 16)
    sb.fillOval(sx, sy, r * 0.5f, r * 0.38f, 0.95f, 0.4f, 0.02f, 0.9f * p, 16)
    sb.fillOval(sx, sy, r * 0.3f, r * 0.23f, 1f, 0.65f, 0.08f, 0.95f * p, 14)
    // Cartoon highlight
    sb.fillOval(sx - 5f, sy - 4f, 6f, 4f, 1f, 1f, 0.8f, 0.4f * p, 8)
    sb.fillOval(sx, sy, 8f, 6.5f, 1f, 1f, 0.7f, 0.95f, 10)

    // More embers — bigger
    var i = 0; while (i < 12) {
      val angle = phase * 1.5 + i * Math.PI / 6
      val fl = r + Math.sin(phase * 3 + i * 2).toFloat * 16
      sb.fillOval(sx + Math.cos(angle).toFloat * fl, sy + Math.sin(angle).toFloat * fl * 0.5f - Math.abs(Math.sin(phase * 4 + i)).toFloat * 10,
        6f, 4.5f, 1f, 0.5f, 0f, 0.55f * p, 8)
    ; i += 1 }

    // Sparkle stars orbiting
    { var i = 0; while (i < 4) {
      val starAngle = phase * 0.8 + i * Math.PI / 2
      val starDist = r * 0.7f
      val starX = sx + Math.cos(starAngle).toFloat * starDist
      val starY = sy + Math.sin(starAngle).toFloat * starDist * 0.5f
      drawSparkleStar(starX, starY, 6f, 1f, 0.9f, 0.4f, 0.4f * p, sb, phase * 2 + i)
    ; i += 1 } }
  }

  /** Jaw — predatory chomping shark jaws with smooth silhouette, bite animation, teeth, eyes, wake */
  private def drawJaw(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    beamTip(sx, sy, proj, 7f)
    val tipX = _tipX; val tipY = _tipY
    val phase = (tick + proj.id * 17) * 0.4
    computeAllDynamics(proj, 0.42f, 0.47f, 0.55f, phase)
    val p = (0.9 + 0.1 * Math.sin(phase * _stPulseMult)).toFloat * dynAlpha
    val ds = dynScale
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

  /** Frost Shard — jagged hexagonal ice crystal with frost particles and frozen mist trail */
  private def drawFrostShard(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 31) * 0.35
    computeAllDynamics(proj, 0.5f, 0.85f, 1f, phase)
    val p = (0.85 + 0.15 * Math.sin(phase * _stPulseMult)).toFloat * dynAlpha
    val dr = _evoR; val dg = _evoG; val db = _evoB
    val ds = dynScale
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val perpX = -ndy; val perpY = ndx

    // Speed lines (icy blue)
    drawSpeedLines(sx, sy, ndx, ndy, 0.5f, 0.8f, 1f, 0.25f * p, sb, 5 + (_lifePct * 2).toInt, 30f * ds)

    // Frozen mist ribbon trail
    drawRibbonTrail(sx, sy, ndx, ndy, 0.6f, 0.85f, 1f, 0.3f * p, sb, tick, proj.id, 10, 45f * ds * dynTrail, 10f * ds, 1.5f)

    // Icy glow halo
    val haloPulse = 0.7f + 0.3f * Math.sin(phase * 2.0).toFloat
    sb.fillOvalSoft(sx, sy, 55f * ds * dynGlow * haloPulse, 44f * ds * dynGlow * haloPulse, 0.5f, 0.85f, 1f, 0.28f * p, 0f, 20)
    sb.fillOvalSoft(sx, sy, 35f * ds * dynGlow, 28f * ds * dynGlow, 0.7f, 0.92f, 1f, 0.14f * p, 0f, 16)

    // Hexagonal crystal body (6-point) — dark outline layer
    val crystalSpin = phase * 0.25
    val outerR = 20f * ds; val innerR = 14f * ds;
    { var i = 0; while (i < 6) {
      val a = crystalSpin + i * Math.PI / 3
      val rad = if (i % 2 == 0) outerR else innerR * 0.85f
      _holyXs(i) = (sx + Math.cos(a).toFloat * rad + ndx * (if (i < 3) 6f else -4f) * ds).toFloat
      _holyYs(i) = (sy + Math.sin(a).toFloat * rad * 0.65f + ndy * (if (i < 3) 6f else -4f) * ds).toFloat
    ; i += 1 } }
    sb.strokePolygon(_holyXs, _holyYs, 6, 3.5f, 0.1f, 0.2f, 0.35f, 0.85f * p)
    sb.fillPolygon(_holyXs, _holyYs, 6, 0.45f, 0.78f, 0.95f, 0.88f * p)

    // Inner crystal facet (smaller, brighter)
    { var i = 0; while (i < 6) {
      val a = crystalSpin + i * Math.PI / 3
      val rad = if (i % 2 == 0) outerR * 0.6f else innerR * 0.55f
      _holyXs(i) = (sx + Math.cos(a).toFloat * rad + ndx * 3f * ds).toFloat
      _holyYs(i) = (sy + Math.sin(a).toFloat * rad * 0.65f + ndy * 3f * ds).toFloat
    ; i += 1 } }
    sb.fillPolygon(_holyXs, _holyYs, 6, 0.7f, 0.92f, 1f, 0.7f * p)

    // White-hot center
    val bc = _chgBright
    sb.fillOval(sx + ndx * 2f * ds, sy + ndy * 2f * ds, 8f * ds, 6f * ds,
      mix(0.85f, 1f, bc), mix(0.95f, 1f, bc), mix(1f, 1f, bc), 0.92f * p, 8)
    // Cartoon highlight
    sb.fillOval(sx - 3f * ds, sy - 3f * ds, 6f * ds, 4f * ds, 1f, 1f, 1f, 0.4f * p, 8)

    // Sharp tip spike extending forward
    val tipLen = 16f * ds
    _polyXs3(0) = sx + ndx * (outerR + tipLen); _polyXs3(1) = sx + ndx * outerR * 0.6f + perpX * 5f * ds
    _polyXs3(2) = sx + ndx * outerR * 0.6f - perpX * 5f * ds
    _polyYs3(0) = sy + ndy * (outerR + tipLen); _polyYs3(1) = sy + ndy * outerR * 0.6f + perpY * 5f * ds
    _polyYs3(2) = sy + ndy * outerR * 0.6f - perpY * 5f * ds
    sb.strokePolygon(_polyXs3, _polyYs3, 3, 2f, 0.15f, 0.25f, 0.4f, 0.8f * p)
    sb.fillPolygon(_polyXs3, _polyYs3, 3, 0.55f, 0.88f, 1f, 0.9f * p)
    sb.fillPolygon(_polyXs3, _polyYs3, 3, 0.8f, 0.95f, 1f, 0.35f * p)

    // 10 frost particles swirling around
    { var i = 0; while (i < 10) {
      val t = ((tick * 0.05 + i * 0.1 + proj.id * 0.13) % 1.0).toFloat
      val frostAngle = phase * 1.8 + i * Math.PI * 2 / 10
      val frostDist = (14f + t * 18f) * ds
      val fx = sx + Math.cos(frostAngle).toFloat * frostDist - ndx * t * 25f * ds
      val fy = sy + Math.sin(frostAngle).toFloat * frostDist * 0.55f - ndy * t * 25f * ds
      val fSz = (3f + (1f - t) * 2.5f) * ds
      sb.fillOval(fx, fy, fSz, fSz * 0.8f, 0.75f, 0.92f, 1f, 0.45f * (1f - t) * p, 6)
    ; i += 1 } }

    // Snowflake sparkle stars
    { var i = 0; while (i < 5) {
      val starPhase = ((phase * 0.45 + i * 0.2) % 1.0).toFloat
      val starAngle = phase * 1.3 + i * Math.PI * 2 / 5
      val starDist = (12f + starPhase * 20f) * ds
      val starX = sx + Math.cos(starAngle).toFloat * starDist
      val starY = sy + Math.sin(starAngle).toFloat * starDist * 0.55f
      drawSparkleStar(starX, starY, 5f * (1f - starPhase * 0.4f) * ds,
        0.7f, 0.92f, 1f, 0.5f * (1f - starPhase) * p, sb, phase * 2 + i)
    ; i += 1 } }

    drawChargeCrackle(sx, sy, 22f * ds, 0.5f, 0.85f, 1f, p, sb, phase, proj.chargeLevel)
    drawReturnGhosts(sx, sy, 22f * ds, dr, dg, db, p, sb, proj)
  }

  /** Talon — curved 3-prong razor claw with slash trail */
  private def drawTalon(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 29) * 0.4
    computeAllDynamics(proj, 0.6f, 0.45f, 0.3f, phase)
    val p = (0.8 + 0.2 * Math.sin(phase * _stPulseMult)).toFloat * dynAlpha
    val dr = _evoR; val dg = _evoG; val db = _evoB
    val ds = dynScale
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val perpX = -ndy; val perpY = ndx

    // Speed lines
    drawSpeedLines(sx, sy, ndx, ndy, 0.6f, 0.35f, 0.25f, 0.3f * p, sb, 6 + (_lifePct * 2).toInt, 35f * ds)

    // Slash mark ribbon trail (red-tinted)
    drawRibbonTrail(sx, sy, ndx, ndy, 0.8f, 0.3f, 0.2f, 0.35f * p, sb, tick, proj.id, 10, 50f * ds * dynTrail, 12f * ds, 2f)

    // Warm glow
    sb.fillOvalSoft(sx, sy, 45f * ds * dynGlow, 36f * ds * dynGlow, 0.7f, 0.35f, 0.2f, 0.22f * p, 0f, 18)

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
    sb.fillOvalSoft(sx, sy, 50f * ds * dynGlow * haloPulse, 40f * ds * dynGlow * haloPulse, 0.8f, 0.08f, 0.05f, 0.25f * p, 0f, 20)

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

  /** Thorn — woody bark spike with leaves and vine trail */
  private def drawThorn(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 33) * 0.3
    computeAllDynamics(proj, 0.25f, 0.6f, 0.18f, phase)
    val p = (0.85 + 0.15 * Math.sin(phase * _stPulseMult)).toFloat * dynAlpha
    val dr = _evoR; val dg = _evoG; val db = _evoB
    val ds = dynScale
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val perpX = -ndy; val perpY = ndx

    drawSpeedLines(sx, sy, ndx, ndy, 0.3f, 0.55f, 0.2f, 0.2f * p, sb, 4 + (_lifePct * 2).toInt, 28f * ds)

    // Vine ribbon trail
    drawRibbonTrail(sx, sy, ndx, ndy, 0.2f, 0.5f, 0.15f, 0.3f * p, sb, tick, proj.id, 10, 50f * ds * dynTrail, 7f * ds, 1.5f)

    // Nature glow
    sb.fillOvalSoft(sx, sy, 45f * ds * dynGlow, 36f * ds * dynGlow, 0.25f, 0.55f, 0.18f, 0.2f * p, 0f, 18)

    // Main thorn spike — thick woody shape
    beamTip(sx, sy, proj, 6f)
    val tipX = _tipX; val tipY = _tipY

    // Dark bark outline
    sb.strokeLine(sx - ndx * 8f * ds, sy - ndy * 8f * ds, tipX + ndx * 6f * ds, tipY + ndy * 6f * ds, 9f * ds, 0.12f, 0.08f, 0.04f, 0.85f * p)
    // Bark body (brown-green)
    sb.strokeLine(sx - ndx * 6f * ds, sy - ndy * 6f * ds, tipX + ndx * 4f * ds, tipY + ndy * 4f * ds, 6.5f * ds, 0.35f, 0.28f, 0.15f, 0.9f * p)
    // Green inner
    sb.strokeLine(sx - ndx * 4f * ds, sy - ndy * 4f * ds, tipX + ndx * 2f * ds, tipY + ndy * 2f * ds, 3.5f * ds, 0.3f, 0.55f, 0.2f, 0.7f * p)
    // Bright highlight core
    sb.strokeLine(sx, sy, tipX, tipY, 1.5f * ds, bright(0.3f), bright(0.55f), bright(0.2f), 0.4f * p)

    // Sharp green tip
    val sharpTip = 12f * ds
    _polyXs3(0) = tipX + ndx * sharpTip; _polyXs3(1) = tipX + perpX * 4f * ds; _polyXs3(2) = tipX - perpX * 4f * ds
    _polyYs3(0) = tipY + ndy * sharpTip; _polyYs3(1) = tipY + perpY * 4f * ds; _polyYs3(2) = tipY - perpY * 4f * ds
    sb.strokePolygon(_polyXs3, _polyYs3, 3, 2f, 0.1f, 0.15f, 0.05f, 0.85f * p)
    sb.fillPolygon(_polyXs3, _polyYs3, 3, 0.2f, 0.65f, 0.15f, 0.9f * p)
    sb.fillOval(tipX + ndx * sharpTip * 0.5f, tipY + ndy * sharpTip * 0.5f, 3f * ds, 2f * ds,
      bright(0.3f), bright(0.65f), bright(0.2f), 0.6f * p, 6)

    // 4 small leaves along the shaft
    { var lf = 0; while (lf < 4) {
      val lt = (lf + 1).toFloat / 5f
      val lx = sx + (tipX - sx) * lt; val ly = sy + (tipY - sy) * lt
      val side = if (lf % 2 == 0) 1f else -1f
      val leafAngle = phase * 0.3 + lf * 1.5
      val leafWave = Math.sin(leafAngle).toFloat * 2f * ds
      val leafX = lx + perpX * side * (7f + leafWave) * ds
      val leafY = ly + perpY * side * (7f + leafWave) * ds
      // Leaf outline
      sb.fillOval(leafX, leafY, 5f * ds, 3f * ds, 0.1f, 0.2f, 0.05f, 0.6f * p, 6)
      // Leaf body
      sb.fillOval(leafX, leafY, 4f * ds, 2.2f * ds, 0.25f, 0.7f, 0.2f, 0.75f * p, 6)
      // Leaf vein
      sb.strokeLine(lx + perpX * side * 2f * ds, ly + perpY * side * 2f * ds, leafX, leafY,
        0.8f, 0.18f, 0.45f, 0.12f, 0.4f * p)
    ; lf += 1 } }

    // Small bark texture lines on shaft
    { var b = 0; while (b < 3) {
      val bt = 0.25f + b * 0.2f
      val bx = sx + (tipX - sx) * bt; val by = sy + (tipY - sy) * bt
      sb.strokeLine(bx - perpX * 3f * ds, by - perpY * 3f * ds,
        bx + perpX * 3f * ds, by + perpY * 3f * ds, 1f, 0.2f, 0.15f, 0.08f, 0.5f * p)
    ; b += 1 } }

    { var i = 0; while (i < 4) {
      val starPhase = ((phase * 0.4 + i * 0.25) % 1.0).toFloat
      val starAngle = phase * 1.2 + i * Math.PI * 2 / 4
      val starDist = (12f + starPhase * 16f) * ds
      val starX = sx + Math.cos(starAngle).toFloat * starDist
      val starY = sy + Math.sin(starAngle).toFloat * starDist * 0.55f
      drawSparkleStar(starX, starY, 4f * (1f - starPhase * 0.4f) * ds,
        0.3f, 0.7f, 0.2f, 0.4f * (1f - starPhase) * p, sb, phase * 2 + i)
    ; i += 1 } }

    drawChargeCrackle(sx, sy, 20f * ds, 0.25f, 0.6f, 0.18f, p, sb, phase, proj.chargeLevel)
    drawReturnGhosts(sx, sy, 20f * ds, dr, dg, db, p, sb, proj)
  }

  /** Stinger — curved venomous barbed stinger with poison drip */
  private def drawStinger(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 31) * 0.4
    computeAllDynamics(proj, 0.45f, 0.85f, 0.15f, phase)
    val p = (0.8 + 0.2 * Math.sin(phase * _stPulseMult)).toFloat * dynAlpha
    val dr = _evoR; val dg = _evoG; val db = _evoB
    val ds = dynScale
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val perpX = -ndy; val perpY = ndx

    drawSpeedLines(sx, sy, ndx, ndy, 0.45f, 0.8f, 0.15f, 0.25f * p, sb, 5 + (_lifePct * 2).toInt, 30f * ds)
    drawRibbonTrail(sx, sy, ndx, ndy, 0.4f, 0.75f, 0.12f, 0.3f * p, sb, tick, proj.id, 8, 40f * ds * dynTrail, 7f * ds, 1.5f)

    // Toxic glow
    val haloPulse = 0.7f + 0.3f * Math.sin(phase * 2.2).toFloat
    sb.fillOvalSoft(sx, sy, 48f * ds * dynGlow * haloPulse, 38f * ds * dynGlow * haloPulse, 0.4f, 0.85f, 0.15f, 0.22f * p, 0f, 18)

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

  /** Horn — massive bull horn with impact dust and weight */
  private def drawHorn(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 37) * 0.3
    computeAllDynamics(proj, 0.65f, 0.58f, 0.4f, phase)
    val p = (0.85 + 0.15 * Math.sin(phase * _stPulseMult)).toFloat * dynAlpha
    val dr = _evoR; val dg = _evoG; val db = _evoB
    val ds = dynScale * 1.2f // larger for weight
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val perpX = -ndy; val perpY = ndx

    drawSpeedLines(sx, sy, ndx, ndy, 0.55f, 0.48f, 0.35f, 0.3f * p, sb, 6 + (_lifePct * 2).toInt, 40f * ds)
    drawRibbonTrail(sx, sy, ndx, ndy, 0.5f, 0.45f, 0.3f, 0.3f * p, sb, tick, proj.id, 10, 48f * ds * dynTrail, 12f * ds, 2f)

    // Warm dust glow
    sb.fillOvalSoft(sx, sy, 50f * ds * dynGlow, 40f * ds * dynGlow, 0.6f, 0.5f, 0.3f, 0.2f * p, 0f, 18)

    // Ground shadow
    sb.fillOval(sx, sy + 14f * ds, 30f * ds, 7f * ds, 0f, 0f, 0f, 0.3f * p, 12)

    // Thick curved horn — base to tip with curve
    val hornLen = 36f * ds
    val hornCurve = Math.sin(phase * 0.2).toFloat * 4f * ds + 6f * ds
    val baseX = sx - ndx * 8f * ds; val baseY = sy - ndy * 8f * ds
    val midX = sx + ndx * hornLen * 0.5f + perpX * hornCurve
    val midY = sy + ndy * hornLen * 0.5f + perpY * hornCurve
    val tipHX = sx + ndx * hornLen + perpX * hornCurve * 0.6f
    val tipHY = sy + ndy * hornLen + perpY * hornCurve * 0.6f

    // Dark outline (very thick base)
    sb.strokeLine(baseX, baseY, midX, midY, 12f * ds, 0.12f, 0.1f, 0.06f, 0.85f * p)
    sb.strokeLine(midX, midY, tipHX, tipHY, 7f * ds, 0.12f, 0.1f, 0.06f, 0.85f * p)
    // Bone/ivory body
    sb.strokeLine(baseX, baseY, midX, midY, 9f * ds, 0.72f, 0.65f, 0.48f, 0.92f * p)
    sb.strokeLine(midX, midY, tipHX, tipHY, 5f * ds, 0.72f, 0.65f, 0.48f, 0.92f * p)
    // Highlight streak
    sb.strokeLine(baseX + perpX * -2f * ds, baseY + perpY * -2f * ds,
      midX + perpX * -1f * ds, midY + perpY * -1f * ds, 3f * ds, 0.85f, 0.8f, 0.65f, 0.4f * p)
    // Bright tip
    sb.fillOval(tipHX, tipHY, 5f * ds, 4f * ds, 0.92f, 0.88f, 0.72f, 0.95f * p, 8)
    sb.fillOval(tipHX, tipHY, 3f * ds, 2f * ds, 1f, 0.95f, 0.85f, 0.7f * p, 6)

    // Horn ridges (3 rings)
    { var r = 0; while (r < 3) {
      val rt = 0.2f + r * 0.2f
      val rx = baseX + (tipHX - baseX) * rt + perpX * hornCurve * rt
      val ry = baseY + (tipHY - baseY) * rt + perpY * hornCurve * rt
      val rw = (9f - r * 1.5f) * ds
      sb.strokeOval(rx, ry, rw, rw * 0.6f, 1.5f, 0.45f, 0.38f, 0.25f, 0.55f * p, 8)
    ; r += 1 } }

    // Base knob (where horn meets skull)
    sb.strokeOval(baseX, baseY, 10f * ds, 8f * ds, 3f, 0.12f, 0.1f, 0.06f, 0.8f * p, 12)
    sb.fillOval(baseX, baseY, 9f * ds, 7f * ds, 0.55f, 0.48f, 0.35f, 0.9f * p, 12)
    sb.fillOval(baseX - 2f * ds, baseY - 2f * ds, 4f * ds, 3f * ds, 1f, 1f, 1f, 0.25f * p, 6)

    // Dust cloud behind (8 particles)
    { var i = 0; while (i < 8) {
      val t = ((tick * 0.05 + i * 0.125 + proj.id * 0.11) % 1.0).toFloat
      val dustX = sx - ndx * t * 45f * ds + Math.sin(phase + i * 2.3).toFloat * 6f * ds
      val dustY = sy - ndy * t * 45f * ds + Math.sin(phase * 0.7 + i * 1.5).toFloat * 4f * ds
      val dSz = (4f + t * 6f) * ds
      sb.fillOval(dustX, dustY, dSz, dSz * 0.7f, 0.55f, 0.48f, 0.35f, 0.25f * (1f - t) * p, 8)
    ; i += 1 } }

    // Impact shockwave ring at front
    val impactPhase = ((phase * 0.6) % 1.0).toFloat
    val impactR = (4f + impactPhase * 16f) * ds
    sb.strokeOval(tipHX, tipHY, impactR, impactR * 0.5f, 2f * (1f - impactPhase),
      0.7f, 0.6f, 0.4f, 0.3f * (1f - impactPhase) * p, 10)

    drawChargeCrackle(sx, sy, 24f * ds, 0.65f, 0.58f, 0.4f, p, sb, phase, proj.chargeLevel)
    drawReturnGhosts(sx, sy, 24f * ds, dr, dg, db, p, sb, proj)
  }

  /** Poison Dart — sleek thin needle with feathered tail and poison bubble trail */
  private def drawPoisonDart(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 29) * 0.45
    computeAllDynamics(proj, 0.25f, 0.75f, 0.2f, phase)
    val p = (0.8 + 0.2 * Math.sin(phase * _stPulseMult)).toFloat * dynAlpha
    val dr = _evoR; val dg = _evoG; val db = _evoB
    val ds = dynScale
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val perpX = -ndy; val perpY = ndx

    drawSpeedLines(sx, sy, ndx, ndy, 0.2f, 0.6f, 0.15f, 0.2f * p, sb, 5 + (_lifePct * 2).toInt, 28f * ds)
    drawRibbonTrail(sx, sy, ndx, ndy, 0.25f, 0.7f, 0.2f, 0.25f * p, sb, tick, proj.id, 8, 38f * ds * dynTrail, 5f * ds, 1f)

    // Subtle stealth glow (purple-green)
    sb.fillOvalSoft(sx, sy, 35f * ds * dynGlow, 28f * ds * dynGlow, 0.3f, 0.6f, 0.25f, 0.18f * p, 0f, 16)

    // Thin needle shaft
    beamTip(sx, sy, proj, 5.5f)
    val tipX = _tipX; val tipY = _tipY

    // Dark outline
    sb.strokeLine(sx - ndx * 6f * ds, sy - ndy * 6f * ds, tipX, tipY, 4f * ds, 0.06f, 0.12f, 0.04f, 0.85f * p)
    // Metallic needle body
    sb.strokeLine(sx - ndx * 4f * ds, sy - ndy * 4f * ds, tipX, tipY, 2.5f * ds, 0.5f, 0.55f, 0.5f, 0.9f * p)
    // Bright highlight
    sb.strokeLine(sx, sy, tipX, tipY, 0.8f * ds, 0.8f, 0.85f, 0.78f, 0.5f * p)

    // Sharp poisoned tip (green)
    val tipPt = 8f * ds
    _polyXs3(0) = tipX + ndx * tipPt; _polyXs3(1) = tipX + perpX * 3.5f * ds; _polyXs3(2) = tipX - perpX * 3.5f * ds
    _polyYs3(0) = tipY + ndy * tipPt; _polyYs3(1) = tipY + perpY * 3.5f * ds; _polyYs3(2) = tipY - perpY * 3.5f * ds
    sb.strokePolygon(_polyXs3, _polyYs3, 3, 1.5f, 0.05f, 0.15f, 0.03f, 0.85f * p)
    sb.fillPolygon(_polyXs3, _polyYs3, 3, 0.2f, 0.8f, 0.15f, 0.92f * p)
    // Venom gleam
    sb.fillOval(tipX + ndx * tipPt * 0.4f, tipY + ndy * tipPt * 0.4f, 2.5f * ds, 1.8f * ds,
      0.4f, 1f, 0.3f, 0.65f * p, 6)

    // Feathered tail (2 small feathers)
    { var f = -1; while (f <= 1) {
      if (f != 0) {
        val tailX = sx - ndx * 6f * ds; val tailY = sy - ndy * 6f * ds
        val featherX = tailX - ndx * 14f * ds + perpX * f * 7f * ds
        val featherY = tailY - ndy * 14f * ds + perpY * f * 7f * ds
        sb.strokeLine(tailX, tailY, featherX, featherY, 3f * ds, 0.06f, 0.12f, 0.04f, 0.6f * p)
        sb.strokeLine(tailX, tailY, featherX, featherY, 1.8f * ds, 0.35f, 0.2f, 0.5f, 0.55f * p)
        sb.strokeLine(tailX, tailY, featherX, featherY, 0.7f * ds, 0.55f, 0.35f, 0.7f, 0.3f * p)
      }
    ; f += 2 } }

    // Poison bubble trail (8 rising bubbles)
    { var i = 0; while (i < 8) {
      val t = ((tick * 0.055 + i * 0.125 + proj.id * 0.13) % 1.0).toFloat
      val bubX = sx - ndx * t * 35f * ds + Math.sin(phase + i * 2.1).toFloat * 5f * ds
      val bubY = sy - ndy * t * 35f * ds - t * 8f * ds
      val bSz = (2f + Math.sin(phase * 3 + i * 1.7).toFloat * 1f + (1f - t) * 2f) * ds
      // Bubble outline
      sb.strokeOval(bubX, bubY, bSz + 0.5f, bSz + 0.5f, 0.8f, 0.08f, 0.2f, 0.05f, 0.3f * (1f - t) * p, 6)
      // Bubble body
      sb.fillOval(bubX, bubY, bSz, bSz, 0.2f, 0.75f, 0.15f, 0.4f * (1f - t) * p, 6)
      // Bubble highlight
      sb.fillOval(bubX - bSz * 0.3f, bubY - bSz * 0.3f, bSz * 0.3f, bSz * 0.3f, 0.5f, 1f, 0.4f, 0.3f * (1f - t) * p, 4)
    ; i += 1 } }

    { var i = 0; while (i < 4) {
      val starPhase = ((phase * 0.5 + i * 0.25) % 1.0).toFloat
      val starAngle = phase * 1.2 + i * Math.PI * 2 / 4
      val starDist = (10f + starPhase * 14f) * ds
      val starX = sx + Math.cos(starAngle).toFloat * starDist
      val starY = sy + Math.sin(starAngle).toFloat * starDist * 0.55f
      drawSparkleStar(starX, starY, 3.5f * (1f - starPhase * 0.4f) * ds,
        0.3f, 0.8f, 0.2f, 0.4f * (1f - starPhase) * p, sb, phase * 2 + i)
    ; i += 1 } }

    drawChargeCrackle(sx, sy, 18f * ds, 0.25f, 0.75f, 0.2f, p, sb, phase, proj.chargeLevel)
    drawReturnGhosts(sx, sy, 18f * ds, dr, dg, db, p, sb, proj)
  }

  /** Enchanted Arrow — glowing enchanted ranger arrow with fire trail and detailed feathers */
  private def drawEnchantedArrow(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 31) * 0.4
    computeAllDynamics(proj, 0.55f, 0.4f, 0.25f, phase)
    val p = (0.8 + 0.2 * Math.sin(phase * _stPulseMult)).toFloat * dynAlpha
    val dr = _evoR; val dg = _evoG; val db = _evoB
    // Dampen charge scaling for arrows (cap at 1.35x)
    val ds = Math.min(dynScale, 1.35f)
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val perpX = -ndy; val perpY = ndx

    drawSpeedLines(sx, sy, ndx, ndy, 0.7f, 0.5f, 0.3f, 0.25f * p, sb, 4 + (_lifePct * 2).toInt, 22f * ds)
    drawRibbonTrail(sx, sy, ndx, ndy, 0.8f, 0.55f, 0.2f, 0.25f * p, sb, tick, proj.id, 6, 28f * ds * dynTrail, 4f * ds, 1.2f)

    // Warm enchanted glow (compact)
    val haloPulse = 0.7f + 0.3f * Math.sin(phase * 2.2).toFloat
    sb.fillOvalSoft(sx + ndx * 6f * ds, sy + ndy * 6f * ds, 22f * ds * dynGlow * haloPulse,
      16f * ds * dynGlow * haloPulse, 0.9f, 0.6f, 0.2f, 0.18f * p, 0f, 14)

    beamTip(sx, sy, proj, 6f)
    val tipX = _tipX; val tipY = _tipY

    // Ground shadow
    val midAX = (sx + tipX) * 0.5f; val midAY = (sy + tipY) * 0.5f
    sb.fillOval(midAX, midAY + 8f * ds, 18f * ds, 4f * ds, 0f, 0f, 0f, 0.18f * p, 10)

    // Shaft — dark outline, wooden body, bright core
    sb.strokeLine(sx, sy, tipX, tipY, 4.5f * ds, 0.12f, 0.08f, 0.04f, 0.85f * p)
    sb.strokeLine(sx, sy, tipX, tipY, 3f * ds, 0.55f, 0.38f, 0.2f, 0.9f * p)
    sb.strokeLine(sx, sy, tipX, tipY, 1f * ds, 0.75f, 0.55f, 0.3f, 0.5f * p)

    // Enchanted arrowhead — glowing
    val headLen = 12f * ds; val headW = 6f * ds
    val pointX = tipX + ndx * headLen; val pointY = tipY + ndy * headLen
    // Outline
    _polyXs3(0) = pointX + ndx * 2f; _polyXs3(1) = tipX + perpX * (headW + 1.5f); _polyXs3(2) = tipX - perpX * (headW + 1.5f)
    _polyYs3(0) = pointY + ndy * 2f; _polyYs3(1) = tipY + perpY * (headW + 1.5f); _polyYs3(2) = tipY - perpY * (headW + 1.5f)
    sb.fillPolygon(_polyXs3, _polyYs3, 3, 0.12f, 0.08f, 0.04f, 0.88f * p)
    // Body (warm orange enchantment)
    _polyXs3(0) = pointX; _polyXs3(1) = tipX + perpX * headW; _polyXs3(2) = tipX - perpX * headW
    _polyYs3(0) = pointY; _polyYs3(1) = tipY + perpY * headW; _polyYs3(2) = tipY - perpY * headW
    sb.fillPolygon(_polyXs3, _polyYs3, 3, 0.95f, 0.65f, 0.2f, 0.95f * p)
    // Bright enchanted highlight
    _polyXs3(0) = pointX; _polyXs3(1) = tipX + perpX * headW * 0.5f + ndx * headLen * 0.3f
    _polyXs3(2) = tipX - perpX * headW * 0.15f + ndx * headLen * 0.3f
    _polyYs3(0) = pointY; _polyYs3(1) = tipY + perpY * headW * 0.5f + ndy * headLen * 0.3f
    _polyYs3(2) = tipY - perpY * headW * 0.15f + ndy * headLen * 0.3f
    sb.fillPolygon(_polyXs3, _polyYs3, 3, 1f, 0.85f, 0.4f, 0.55f * p)
    // Enchantment glow at tip
    sb.fillOvalSoft(pointX, pointY, 6f * ds, 5f * ds, 1f, 0.7f, 0.2f, 0.30f * p, 0f, 8)
    sb.fillOval(pointX, pointY, 2f * ds, 1.5f * ds, 1f, 0.95f, 0.7f, 0.85f * p, 6)

    // Compact fletching (2 feathers with barbs)
    { var f = -1; while (f <= 1) {
      val tailX = sx - ndx * 3f * ds; val tailY = sy - ndy * 3f * ds
      val featherX = tailX - ndx * 10f * ds + perpX * f * 5f * ds
      val featherY = tailY - ndy * 10f * ds + perpY * f * 5f * ds
      sb.strokeLine(tailX, tailY, featherX, featherY, 2.5f * ds, 0.12f, 0.08f, 0.04f, 0.6f * p)
      sb.strokeLine(tailX, tailY, featherX, featherY, 1.5f * ds, 0.7f, 0.45f, 0.2f, 0.55f * p)
      sb.strokeLine(tailX, tailY, featherX, featherY, 0.5f * ds, 0.9f, 0.7f, 0.4f, 0.3f * p)
      // Feather barbs
      var barb = 0; while (barb < 2) {
        val bt = (barb + 1).toFloat / 3f
        val bx = tailX + (featherX - tailX) * bt
        val by = tailY + (featherY - tailY) * bt
        val barbLen = 3f * (1f - bt * 0.5f) * ds
        sb.strokeLine(bx, by, bx + perpX * f * barbLen, by + perpY * f * barbLen,
          1f, 0.6f, 0.4f, 0.2f, 0.3f * p * (1f - bt * 0.3f))
      ; barb += 1 }
    ; f += 2 } }

    // Fire ember trail (6 particles, compact)
    { var i = 0; while (i < 6) {
      val t = ((tick * 0.06 + i * 0.167 + proj.id * 0.13) % 1.0).toFloat
      val embX = sx - ndx * t * 26f * ds + Math.sin(phase + i * 2.3).toFloat * 3f * ds
      val embY = sy - ndy * t * 26f * ds - t * 4f * ds
      val eSz = (2f + (1f - t) * 2f) * ds
      sb.fillOval(embX, embY, eSz, eSz * 0.8f, 1f, 0.55f + (1f - t) * 0.3f, 0.15f, 0.4f * (1f - t) * p, 6)
    ; i += 1 } }

    { var i = 0; while (i < 2) {
      val starPhase = ((phase * 0.45 + i * 0.5) % 1.0).toFloat
      val starAngle = phase * 1.2 + i * Math.PI
      val starDist = (8f + starPhase * 12f) * ds
      val starX = sx + Math.cos(starAngle).toFloat * starDist
      val starY = sy + Math.sin(starAngle).toFloat * starDist * 0.55f
      drawSparkleStar(starX, starY, 3f * (1f - starPhase * 0.4f) * ds,
        1f, 0.7f, 0.25f, 0.4f * (1f - starPhase) * p, sb, phase * 2 + i)
    ; i += 1 } }

    drawChargeCrackle(sx, sy, 14f * ds, 0.55f, 0.4f, 0.25f, p, sb, phase, proj.chargeLevel)
    drawReturnGhosts(sx, sy, 14f * ds, dr, dg, db, p, sb, proj)
  }

  /** Poison Arrow — arrow with green dripping poison tip and toxic bubbles */
  private def drawPoisonArrow(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 33) * 0.4
    computeAllDynamics(proj, 0.25f, 0.72f, 0.2f, phase)
    val p = (0.8 + 0.2 * Math.sin(phase * _stPulseMult)).toFloat * dynAlpha
    val dr = _evoR; val dg = _evoG; val db = _evoB
    // Dampen charge scaling for arrows (cap at 1.35x)
    val ds = Math.min(dynScale, 1.35f)
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val perpX = -ndy; val perpY = ndx

    drawSpeedLines(sx, sy, ndx, ndy, 0.3f, 0.65f, 0.2f, 0.2f * p, sb, 4 + (_lifePct * 2).toInt, 20f * ds)
    drawRibbonTrail(sx, sy, ndx, ndy, 0.25f, 0.7f, 0.18f, 0.25f * p, sb, tick, proj.id, 6, 26f * ds * dynTrail, 4f * ds, 1.2f)

    // Toxic green glow (compact)
    sb.fillOvalSoft(sx + ndx * 6f * ds, sy + ndy * 6f * ds, 22f * ds * dynGlow, 16f * ds * dynGlow,
      0.2f, 0.7f, 0.15f, 0.18f * p, 0f, 14)

    beamTip(sx, sy, proj, 6f)
    val tipX = _tipX; val tipY = _tipY

    // Shaft — dark outline, wooden body
    sb.strokeLine(sx, sy, tipX, tipY, 4.5f * ds, 0.08f, 0.12f, 0.04f, 0.85f * p)
    sb.strokeLine(sx, sy, tipX, tipY, 3f * ds, 0.45f, 0.35f, 0.2f, 0.9f * p)
    sb.strokeLine(sx, sy, tipX, tipY, 1f * ds, 0.6f, 0.5f, 0.3f, 0.5f * p)

    // Poison arrowhead — dripping green
    val headLen = 11f * ds; val headW = 5.5f * ds
    val pointX = tipX + ndx * headLen; val pointY = tipY + ndy * headLen
    // Outline
    _polyXs3(0) = pointX + ndx * 1.5f; _polyXs3(1) = tipX + perpX * (headW + 1.5f); _polyXs3(2) = tipX - perpX * (headW + 1.5f)
    _polyYs3(0) = pointY + ndy * 1.5f; _polyYs3(1) = tipY + perpY * (headW + 1.5f); _polyYs3(2) = tipY - perpY * (headW + 1.5f)
    sb.fillPolygon(_polyXs3, _polyYs3, 3, 0.06f, 0.12f, 0.04f, 0.88f * p)
    // Green poison body
    _polyXs3(0) = pointX; _polyXs3(1) = tipX + perpX * headW; _polyXs3(2) = tipX - perpX * headW
    _polyYs3(0) = pointY; _polyYs3(1) = tipY + perpY * headW; _polyYs3(2) = tipY - perpY * headW
    sb.fillPolygon(_polyXs3, _polyYs3, 3, 0.2f, 0.75f, 0.15f, 0.92f * p)
    // Bright toxic highlight
    _polyXs3(0) = pointX; _polyXs3(1) = tipX + perpX * headW * 0.5f + ndx * headLen * 0.3f
    _polyXs3(2) = tipX - perpX * headW * 0.15f + ndx * headLen * 0.3f
    _polyYs3(0) = pointY; _polyYs3(1) = tipY + perpY * headW * 0.5f + ndy * headLen * 0.3f
    _polyYs3(2) = tipY - perpY * headW * 0.15f + ndy * headLen * 0.3f
    sb.fillPolygon(_polyXs3, _polyYs3, 3, 0.4f, 0.95f, 0.3f, 0.5f * p)
    // Glow at tip
    sb.fillOvalSoft(pointX, pointY, 6f * ds, 5f * ds, 0.3f, 0.9f, 0.2f, 0.25f * p, 0f, 8)

    // Poison drips from arrowhead (fewer, smaller)
    { var d = 0; while (d < 4) {
      val t = ((tick * 0.045 + d * 0.25 + proj.id * 0.11) % 1.0).toFloat
      val dripStartX = tipX + ndx * headLen * 0.5f + perpX * (d - 1.5f) * 2.5f * ds
      val dripStartY = tipY + ndy * headLen * 0.5f + perpY * (d - 1.5f) * 2.5f * ds
      val dripX = dripStartX + Math.sin(phase + d * 1.7).toFloat * 1.5f * ds
      val dripY = dripStartY + t * t * 14f * ds
      val dSz = (1.5f + (1f - t) * 1.5f) * ds
      sb.fillOval(dripX, dripY, dSz, dSz * 1.4f, 0.15f, 0.8f, 0.1f, 0.4f * (1f - t) * p, 6)
    ; d += 1 } }

    // Compact feathers
    { var f = -1; while (f <= 1) {
      val tailX = sx - ndx * 3f * ds; val tailY = sy - ndy * 3f * ds
      val featherX = tailX - ndx * 9f * ds + perpX * f * 4.5f * ds
      val featherY = tailY - ndy * 9f * ds + perpY * f * 4.5f * ds
      sb.strokeLine(tailX, tailY, featherX, featherY, 2.5f * ds, 0.08f, 0.12f, 0.04f, 0.55f * p)
      sb.strokeLine(tailX, tailY, featherX, featherY, 1.5f * ds, 0.3f, 0.55f, 0.2f, 0.5f * p)
    ; f += 2 } }

    // Toxic bubble trail (compact)
    { var i = 0; while (i < 4) {
      val t = ((tick * 0.05 + i * 0.25 + proj.id * 0.13) % 1.0).toFloat
      val bubX = sx - ndx * t * 22f * ds + Math.sin(phase + i * 2.3).toFloat * 3f * ds
      val bubY = sy - ndy * t * 22f * ds - t * 5f * ds
      val bSz = (1.8f + (1f - t) * 1.5f) * ds
      sb.strokeOval(bubX, bubY, bSz, bSz, 0.8f, 0.06f, 0.18f, 0.04f, 0.25f * (1f - t) * p, 6)
      sb.fillOval(bubX, bubY, bSz, bSz, 0.18f, 0.7f, 0.12f, 0.3f * (1f - t) * p, 6)
    ; i += 1 } }

    { var i = 0; while (i < 2) {
      val starPhase = ((phase * 0.5 + i * 0.5) % 1.0).toFloat
      val starAngle = phase * 1.2 + i * Math.PI
      val starDist = (8f + starPhase * 10f) * ds
      val starX = sx + Math.cos(starAngle).toFloat * starDist
      val starY = sy + Math.sin(starAngle).toFloat * starDist * 0.55f
      drawSparkleStar(starX, starY, 3f * (1f - starPhase * 0.4f) * ds,
        0.3f, 0.8f, 0.2f, 0.35f * (1f - starPhase) * p, sb, phase * 2 + i)
    ; i += 1 } }

    drawChargeCrackle(sx, sy, 13f * ds, 0.25f, 0.72f, 0.2f, p, sb, phase, proj.chargeLevel)
    drawReturnGhosts(sx, sy, 13f * ds, dr, dg, db, p, sb, proj)
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
    sb.fillOvalSoft(sx, sy, 50f * ds * dynGlow, 40f * ds * dynGlow, 0.85f, 0.15f, 0.1f, 0.2f * p, 0f, 18)

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
