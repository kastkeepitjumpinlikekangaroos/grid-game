package com.gridgame.client.gl

import com.gridgame.common.model.{Projectile, ProjectileDef, ProjectileType}

/**
 * Projectile renderers for OpenGL ShapeBatch. All 112 projectile types mapped.
 * Uses standard alpha blending for solid visible shapes, with bloom post-processor
 * providing glow on bright elements.
 */
object GLProjectileRenderers {

  /**
   * Draws one projectile at screen position (sx, sy). A single-method trait rather than a
   * Function5: scala.Function5 isn't specialized, so calling one boxed both coordinates and
   * the tick — three allocations per projectile per frame. Factory lambdas convert to it as
   * they are; a method goes through [[asRenderer]].
   */
  trait Renderer { def apply(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit }

  /** A method as a [[Renderer]], without an intermediate Function5. */
  private def asRenderer(f: Renderer): Renderer = f

  /** Look up renderer by projectile type. Returns null if none registered. No Option allocation. */
  def getRenderer(pType: Byte): Renderer = _rendererLUT(pType & 0xFF)

  /** Draw a projectile with its registered renderer, or the generic fallback. */
  def draw(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int,
           r: Float, g: Float, b: Float): Unit = {
    val rend = getRenderer(proj.projectileType)
    if (rend != null) rend(proj, sx, sy, sb, tick) else drawGeneric(proj, sx, sy, sb, tick, r, g, b)
  }

  // ═══════════════════════════════════════════════════════════════
  //  TERRAIN: FLYING OVER IT, AND BEING STOPPED BY IT
  // ═══════════════════════════════════════════════════════════════

  /** How far above its ground point a projectile that travels over terrain
   *  (passesThroughWalls) is drawn. At ground height those were drawn in the same depth pass
   *  as the wall blocks, so each wall they crossed sliced through them. Lifted, drawn after
   *  the terrain, with a shadow below, they read as flying over it. */
  val FLY_ALT = 20f

  /** Current lift of a flier: FLY_ALT with a slow bob, so the height reads as altitude and
   *  not as a draw offset. */
  def flyLift(proj: Projectile, tick: Int): Float =
    FLY_ALT + Math.sin((tick + proj.id * 13) * 0.08).toFloat * 2.2f

  /** The shadow under a flier. `groundY` is the surface it is over — the top face of a wall
   *  block when it is crossing one — so the shadow rides up onto the wall it clears. */
  def drawFlightShadow(proj: Projectile, sx: Float, groundY: Float, sb: ShapeBatch, tick: Int, lift: Float): Unit = {
    val k = 1f - (lift - FLY_ALT) * 0.05f
    // Dark enough to read on a dark wall top: the gap between shadow and body is what
    // says "altitude", so a shadow that disappears makes the lift read as an offset.
    sb.fillOvalSoft(sx, groundY + 2f, 14f * k, 6f * k, 0f, 0f, 0f, 0.5f, 0f, 12)
    sb.fillOval(sx, groundY + 2f, 7.5f * k, 3.2f * k, 0f, 0f, 0f, 0.42f, 10)
  }

  /**
   * A projectile that has just been stopped, `t` running 0 (the moment it stopped) to 1
   * (gone). It sinks into the surface — shrinking toward the point of impact while it
   * fades — and, when it hit terrain rather than running out of range, throws a puff off
   * the face in the colour of what it struck: grey chips off a wall, spray off water, dust
   * off the map edge.
   */
  def drawAbsorbed(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int, t: Float,
                   hitTerrain: Boolean, tileColor: Int, r: Float, g: Float, b: Float): Unit = {
    val fade = (1f - t) * (1f - t)
    if (fade > 0.01f) {
      sb.setAlphaMultiplier(fade)
      sb.setScaleAbout(sx, sy, 1f - t * 0.6f)
      draw(proj, sx, sy, sb, tick, r, g, b)
      sb.resetModifiers()
    }
    if (!hitTerrain) return
    val tr = if (tileColor == 0) 0.55f else ((tileColor >> 16) & 0xFF) / 255f
    val tg = if (tileColor == 0) 0.56f else ((tileColor >> 8) & 0xFF) / 255f
    val tb = if (tileColor == 0) 0.62f else (tileColor & 0xFF) / 255f
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    // Contact flash, flattened against the surface
    if (t < 0.4f) {
      val ft = t / 0.4f
      sb.fillOvalSoft(sx, sy, 15f * (0.6f + ft), 7f * (0.6f + ft),
        mix(r, 1f, 0.6f), mix(g, 1f, 0.6f), mix(b, 1f, 0.6f), 0.85f * (1f - ft), 0f, 12)
    }
    // A ring spreading across the face
    val rr = 5f + t * 18f
    sb.strokeOval(sx, sy, rr, rr * 0.5f, 2.6f * (1f - t), mix(tr, 1f, 0.25f), mix(tg, 1f, 0.25f),
      mix(tb, 1f, 0.25f), 0.75f * (1f - t), 16)
    // Debris kicked back off the face, against the direction of travel
    val back = Math.atan2(-ndy, -ndx)
    var i = 0
    while (i < 6) {
      val a = back + (i - 2.5) * 0.42 + ((proj.id * 7 + i * 13) % 5) * 0.06
      val d = 3f + t * (13f + (i % 3) * 5f)
      val hop = Math.sin(t * Math.PI).toFloat * (6f + (i % 2) * 4f)
      val px = sx + Math.cos(a).toFloat * d
      val py = sy + Math.sin(a).toFloat * d * 0.6f - hop
      val ps = (2.6f - (i % 3) * 0.4f) * (1f - t * 0.5f)
      sb.fillOval(px, py, ps + 0.8f, ps * 0.85f + 0.8f, tr * 0.35f, tg * 0.35f, tb * 0.35f, 0.6f * (1f - t), 6)
      sb.fillOval(px, py, ps, ps * 0.85f, tr, tg, tb, 0.9f * (1f - t), 6)
      i += 1
    }
  }

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
  private val SHF_LANCE = 6

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

  private val LANCE_PARTS = Array(
    part(Array(-1.15f,-0.12f, 0.15f,-0.30f, 1.45f,0f, 0.15f,0.30f, -1.15f,0.12f), 0.30f, 0.12f, 0.50f, 0.55f),
    part(Array(-0.85f,-0.05f, 0.15f,-0.14f, 1.15f,0f, 0.15f,0.14f, -0.85f,0.05f), 0.05f, 0.02f, 0.09f, 0.05f),
    part(Array(0.30f,-0.22f, 0.62f,-0.17f, 1.32f,0f, 0.62f,-0.05f), 0.85f, 0.65f, 1f, 0.35f)
  )

  private def shaftParts(kind: Int): Array[Part] = kind match {
    case SHF_SPEAR => SPEAR_PARTS
    case SHF_ARROW => ARROW_PARTS
    case SHF_DART  => DART_PARTS
    case SHF_THORN => THORN_PARTS
    case SHF_PARROW => PARROW_PARTS
    case SHF_ICE   => ICE_PARTS
    case SHF_LANCE => LANCE_PARTS
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
        case SHF_LANCE =>
          // Space bending around the lance: warp rings contracting onto the shaft
          var k = 0
          while (k < 3) {
            val t = ((tick * 0.07 + k * 0.33 + proj.id * 0.13) % 1.0).toFloat
            val ti = 1f - t
            dirPoint(-0.7f + k * 0.55f, 0f, sx, sy, ndx, ndy, s)
            strokeRotEllipse(sb, _ptX, _ptY, -ndy, ndx, s * (0.22f + ti * 0.6f), s * (0.07f + ti * 0.18f),
              1.8f, bright(r), bright(g), bright(b), 0.65f * t * p, 12)
            k += 1
          }
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

  private val CHN_ROPE = 0
  private val CHN_SHACKLE = 1
  private val CHN_LOCK = 2

  /**
   * Thrown restraint, drawn as the object that hits rather than a line out ahead of it.
   *
   *  - CHN_ROPE: a grappling hook at the hitbox with the rope paying out behind it — only
   *    as far as the throw has travelled, capped, and dissolving at the far end.
   *  - CHN_SHACKLE: a heavy manacle tumbling end over end, trailing a few swinging links.
   *  - CHN_LOCK: a loop of links spinning around a padlock — lockdown, read at a glance.
   *
   * The old tether ran out AHEAD of the hitbox to a hook and began at a round "cleat", so
   * it read as a line with a ball on one end and a hook on the other, arriving early.
   */
  private def chainProj(kind: Int, r: Float, g: Float, b: Float, worldLen: Float = 6f): Renderer =
    (proj, sx, sy, sb, tick) => {
      val phase = (tick + proj.id * 23) * 0.3
      computeAllDynamics(proj, r, g, b, phase)
      val p = (0.88f + 0.12f * Math.sin(phase * _stPulseMult).toFloat) * dynAlpha
      val dr = _evoR; val dg = _evoG; val db = _evoB
      // The manacle and the padlock loop are compact objects; they carry more area than the hook
      val ds = Math.min(dynScale, 1.3f) * (if (kind == CHN_ROPE) 1f else 1.25f)
      screenDir(proj)
      val ndx = _sdx; val ndy = _sdy
      val px = -ndy; val py = ndx
      val heading = Math.atan2(ndy, ndx)
      val hiR = mix(bright(r), 1f, 0.3f); val hiG = mix(bright(g), 1f, 0.3f); val hiB = mix(bright(b), 1f, 0.3f)

      kind match {
        case CHN_ROPE =>
          val L = Math.min(proj.getDistanceTraveled, Math.min(worldLen, 2.4f)) * 20f
          if (L > 3f) {
            val sag = L * 0.12f * (0.75f + 0.25f * Math.sin(phase * 0.9).toFloat)
            val segs = 16
            val amp = 3.0f * ds
            var strand = 0
            while (strand < 2) {
              val ph = strand * Math.PI
              var i = 0
              while (i < segs) {
                val t0 = i.toFloat / segs; val t1 = (i + 1).toFloat / segs
                val o0 = Math.sin(t0 * 16.0 + ph + phase * 0.8).toFloat * amp
                val o1 = Math.sin(t1 * 16.0 + ph + phase * 0.8).toFloat * amp
                val s0 = sag * 4f * t0 * (1f - t0); val s1 = sag * 4f * t1 * (1f - t1)
                val x0 = sx - ndx * L * t0 + px * (o0 + s0); val y0 = sy - ndy * L * t0 + py * (o0 + s0) + s0 * 0.6f
                val x1 = sx - ndx * L * t1 + px * (o1 + s1); val y1 = sy - ndy * L * t1 + py * (o1 + s1) + s1 * 0.6f
                val a = (1f - t0) * (1f - t0 * 0.4f)
                sb.strokeLine(x0, y0, x1, y1, 6f * ds, 0.10f, 0.07f, 0.05f, 0.7f * a * p)
                sb.strokeLine(x0, y0, x1, y1, 4.2f * ds, dr, dg, db, 0.95f * a * p)
                sb.strokeLine(x0, y0, x1, y1, 1.4f * ds, bright(r), bright(g), bright(b), 0.4f * a * p)
                i += 1
              }
              strand += 1
            }
          }
          sb.fillOvalSoft(sx, sy, 17f * ds * dynGlow, 14f * ds * dynGlow, dr, dg, db, 0.24f * p, 0f, 12)
          val shx = sx - Math.cos(heading).toFloat * 12f * ds
          val shy = sy - Math.sin(heading).toFloat * 12f * ds * ISO_Y
          sb.strokeLine(shx, shy, sx, sy, 6.4f * ds, 0.06f, 0.06f, 0.08f, 0.85f * p)
          sb.strokeLine(shx, shy, sx, sy, 4.2f * ds, DKSTEEL_R, DKSTEEL_G, DKSTEEL_B, 0.95f * p)
          var k = 0
          while (k < 3) {
            val a = heading + (k - 1) * 0.95
            val mx = sx + Math.cos(a).toFloat * 10f * ds
            val my = sy + Math.sin(a).toFloat * 10f * ds * ISO_Y
            val ex = mx + Math.cos(a - 1.15).toFloat * 9f * ds
            val ey = my + Math.sin(a - 1.15).toFloat * 9f * ds * ISO_Y
            sb.strokeLine(sx, sy, mx, my, 5.8f * ds, 0.06f, 0.06f, 0.08f, 0.85f * p)
            sb.strokeLine(mx, my, ex, ey, 5f * ds, 0.06f, 0.06f, 0.08f, 0.85f * p)
            sb.strokeLine(sx, sy, mx, my, 3.6f * ds, STEEL_R, STEEL_G, STEEL_B, 0.97f * p)
            sb.strokeLine(mx, my, ex, ey, 2.8f * ds, STEEL_R, STEEL_G, STEEL_B, 0.97f * p)
            k += 1
          }
          sb.fillOval(sx, sy, 4f * ds, 3.4f * ds, STEEL_R, STEEL_G, STEEL_B, 0.97f * p, 8)

        case CHN_SHACKLE =>
          // A few links swinging behind, fading
          var i = 1
          while (i <= 3) {
            val t = i / 3.5f
            val swing = Math.sin(phase * 1.8 + i * 0.9).toFloat * 5f * ds * t
            val lx = sx - ndx * (9f + i * 8f) * ds + px * swing
            val ly = sy - ndy * (9f + i * 8f) * ds + py * swing
            val flat = (i & 1) == 1
            val la = if (flat) 5.4f * ds else 2.4f * ds
            val lb = if (flat) 3f * ds else 4.4f * ds
            val a = 1f - t * 0.7f
            strokeRotEllipse(sb, lx, ly, ndx, ndy, la, lb, 3.6f, 0.06f, 0.06f, 0.08f, 0.8f * a * p, 10)
            strokeRotEllipse(sb, lx, ly, ndx, ndy, la, lb, 2.2f, dr, dg, db, 0.95f * a * p, 10)
            i += 1
          }
          // The manacle, tumbling: its ring foreshortens as it turns over
          val spin = phase * 1.5
          val ma = spin * 0.35
          val mx = Math.cos(ma).toFloat; val my = Math.sin(ma).toFloat * ISO_Y
          val ml = Math.max(0.001f, Math.sqrt(mx * mx + my * my).toFloat)
          val ux = mx / ml; val uy = my / ml
          val R = 11f * ds
          val minor = R * (0.3f + 0.7f * Math.abs(Math.cos(spin).toFloat))
          sb.fillOvalSoft(sx, sy, R * 2f * dynGlow, R * 1.6f * dynGlow, dr, dg, db, 0.24f * p, 0f, 12)
          strokeRotEllipse(sb, sx, sy, ux, uy, R, minor, 7.5f * ds, 0.06f, 0.06f, 0.08f, 0.88f * p, 18)
          strokeRotEllipse(sb, sx, sy, ux, uy, R, minor, 4.8f * ds, dr, dg, db, 0.97f * p, 18)
          strokeRotEllipse(sb, sx - 0.6f, sy - 0.8f, ux, uy, R * 0.98f, minor * 0.96f, 1.4f * ds, hiR, hiG, hiB, 0.55f * p, 18)
          // Hinge at one end of the ring, lock plate at the other
          val hx = sx + ux * R; val hy = sy + uy * R
          sb.fillOval(hx, hy, 4.2f * ds, 4.2f * ds, 0.06f, 0.06f, 0.08f, 0.88f * p, 8)
          sb.fillOval(hx, hy, 3f * ds, 3f * ds, DKSTEEL_R, DKSTEEL_G, DKSTEEL_B, 0.97f * p, 8)
          val lx = sx - ux * R; val ly = sy - uy * R
          sb.fillRoundedRect(lx - 5.2f * ds, ly - 4.2f * ds, 10.4f * ds, 8.4f * ds, 2f, 0.06f, 0.06f, 0.08f, 0.88f * p)
          sb.fillRoundedRect(lx - 4f * ds, ly - 3f * ds, 8f * ds, 6f * ds, 1.5f, dr, dg, db, 0.97f * p)
          sb.fillOval(lx, ly - 0.6f * ds, 1.3f * ds, 1.3f * ds, 0.05f, 0.05f, 0.06f, 0.95f * p, 6)
          sb.strokeLine(lx, ly, lx, ly + 2f * ds, 1.2f * ds, 0.05f, 0.05f, 0.06f, 0.95f * p)

        case _ =>
          // CHN_LOCK: a loop of links spinning around a padlock
          val spin = phase * 0.9
          val R = 15f * ds
          sb.fillOvalSoft(sx, sy, R * 1.8f * dynGlow, R * 1.4f * dynGlow, dr, dg, db, 0.26f * p, 0f, 14)
          var pass = 0
          while (pass < 2) {
            var i = 0
            while (i < 8) {
              val a = spin + i * Math.PI / 4
              val lx = sx + Math.cos(a).toFloat * R; val ly = sy + Math.sin(a).toFloat * R * ISO_Y
              val tx = -Math.sin(a).toFloat; val ty = Math.cos(a).toFloat * ISO_Y
              val tl = Math.max(0.001f, Math.sqrt(tx * tx + ty * ty).toFloat)
              val flat = (i & 1) == 0
              val la = if (flat) 6f * ds else 3.6f * ds
              val lb = if (flat) 3.2f * ds else 2.2f * ds
              if (pass == 0) strokeRotEllipse(sb, lx, ly, tx / tl, ty / tl, la, lb, 3.8f, 0.06f, 0.06f, 0.08f, 0.85f * p, 10)
              else {
                strokeRotEllipse(sb, lx, ly, tx / tl, ty / tl, la, lb, 2.4f, dr, dg, db, 0.97f * p, 10)
                strokeRotEllipse(sb, lx - 0.5f, ly - 0.6f, tx / tl, ty / tl, la * 0.9f, lb * 0.8f, 1f, hiR, hiG, hiB, 0.5f * p, 8)
              }
              i += 1
            }
            pass += 1
          }
          // Padlock riding the front of the loop
          val lx = sx + Math.cos(heading).toFloat * R * 0.2f
          val ly = sy + Math.sin(heading).toFloat * R * 0.2f * ISO_Y
          sb.strokeArc(lx, ly - 4f * ds, 4.4f * ds, 5f * ds, 3.14f, 3.14f, 4f * ds, 0.06f, 0.06f, 0.08f, 0.88f * p, 8)
          sb.strokeArc(lx, ly - 4f * ds, 4.4f * ds, 5f * ds, 3.14f, 3.14f, 2.4f * ds, STEEL_R, STEEL_G, STEEL_B, 0.97f * p, 8)
          sb.fillRoundedRect(lx - 7f * ds, ly - 4.6f * ds, 14f * ds, 11.5f * ds, 2.5f, 0.06f, 0.06f, 0.08f, 0.9f * p)
          sb.fillRoundedRect(lx - 5.8f * ds, ly - 3.4f * ds, 11.6f * ds, 9.1f * ds, 2f, GOLD_R, GOLD_G, GOLD_B, 0.98f * p)
          sb.fillRect(lx - 5.8f * ds, ly - 3.4f * ds, 11.6f * ds, 2.2f * ds, 1f, 0.92f, 0.6f, 0.45f * p)
          sb.fillOval(lx, ly + 0.2f * ds, 1.6f * ds, 1.6f * ds, 0.05f, 0.04f, 0.03f, 0.95f * p, 6)
          sb.strokeLine(lx, ly + 0.2f * ds, lx, ly + 3f * ds, 1.4f * ds, 0.05f, 0.04f, 0.03f, 0.95f * p)
      }
      drawChargeCrackle(sx, sy, 14f * ds, r, g, b, p, sb, phase, proj.chargeLevel)
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
  //  HEAD-AT-HITBOX RENDERERS
  // ═══════════════════════════════════════════════════════════════
  //
  // Everything in this section replaces a renderer that drew its body as a line from the
  // projectile FORWARD to a point `worldLen` world units ahead of it (beamTip) and capped
  // that point with a disc — beams, the charge shot, the tentacle, tethers, lightning, the
  // rocket, the shark jaw. Two things were wrong with that:
  //
  //  - A stroked line with a ball on the end is the silhouette of a snake, not of an
  //    ability, and the whip and vine styles wiggled along their length so they slithered.
  //  - The hitbox is at (sx, sy), the BACK of that line. The disc that read as the
  //    projectile's head arrived 100-150px before the damage did.
  //
  // So each of these puts the thing that hits at (sx, sy), and anything elongated trails
  // BEHIND it through `fadeLine`, which dissolves instead of ending in a hard edge.

  /** A stroke that narrows and fades from full strength at (x0, y0) to nothing at (x1, y1).
   *  A stroke of uniform alpha ends in a hard edge, and a hard-edged line with a head on
   *  it is exactly the stick-with-a-ball silhouette this section exists to avoid. */
  private def fadeLine(sb: ShapeBatch, x0: Float, y0: Float, x1: Float, y1: Float,
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
  private def strokeRotEllipse(sb: ShapeBatch, cx: Float, cy: Float, mx: Float, my: Float,
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

  // ── Blaster bolts (lasers, eye beam) ──
  private val BOLT_LASER = 0
  private val BOLT_PRISM = 1
  private val BOLT_EYE = 2

  /**
   * Blaster bolt: a short capsule, round at the front and drawn to a point at the back,
   * whose front IS the hitbox, with an afterglow dissolving behind it — the shape every
   * sci-fi laser reads as. `kind` adds prismatic fringes (Photon) or rings of force
   * pulsing off the head (Cyclops).
   */
  private def laserBolt(kind: Int, r: Float, g: Float, b: Float, len: Float = 40f,
                        width: Float = 9f): Renderer =
    (proj, sx, sy, sb, tick) => {
      val phase = (tick + proj.id * 29) * 0.35
      computeAllDynamics(proj, r, g, b, phase)
      val p = (0.9f + 0.1f * Math.sin(phase * 3 * _stPulseMult).toFloat) * dynAlpha
      val dr = _evoR; val dg = _evoG; val db = _evoB
      val ds = Math.min(dynScale, 1.35f)
      val w = width * ds; val L = len * ds
      screenDir(proj)
      val ndx = _sdx; val ndy = _sdy
      val px = -ndy; val py = ndx
      val bx = sx - ndx * L; val by = sy - ndy * L
      val hw = 0.55f + _chgBright * 0.45f
      val hR = mix(bright(r), 1f, hw); val hG = mix(bright(g), 1f, hw); val hB = mix(bright(b), 1f, hw)

      fadeLine(sb, bx, by, bx - ndx * L * 0.9f, by - ndy * L * 0.9f, w * 1.1f, dr, dg, db, 0.42f * p, 5)
      sb.strokeLineSoft(bx, by, sx, sy, w * 3.2f * dynGlow, dr, dg, db, 0.26f * p)
      sb.fillOvalSoft(sx, sy, w * 2.3f * dynGlow, w * 2.3f * dynGlow, dr, dg, db, 0.36f * p, 0f, 12)

      if (kind == BOLT_PRISM) {
        // Light splitting as it travels: red and blue fringes either side of the bolt
        val sh = w * 0.42f
        sb.strokeLineSoft(bx + px * sh, by + py * sh, sx + px * sh, sy + py * sh, w * 0.9f, 1f, 0.3f, 0.35f, 0.42f * p)
        sb.strokeLineSoft(bx - px * sh, by - py * sh, sx - px * sh, sy - py * sh, w * 0.9f, 0.3f, 0.5f, 1f, 0.42f * p)
      }

      // Dark rim around the front so the bolt keeps its shape against pale ground
      sb.fillOval(sx, sy, w * 0.5f + 1.3f, w * 0.5f + 1.3f, outline(r), outline(g), outline(b), 0.5f * p, 12)
      _polyXs4(0) = sx + px * w * 0.5f; _polyYs4(0) = sy + py * w * 0.5f
      _polyXs4(1) = bx + px * w * 0.12f; _polyYs4(1) = by + py * w * 0.12f
      _polyXs4(2) = bx - px * w * 0.12f; _polyYs4(2) = by - py * w * 0.12f
      _polyXs4(3) = sx - px * w * 0.5f; _polyYs4(3) = sy - py * w * 0.5f
      sb.strokePolygon(_polyXs4, _polyYs4, 4, 2.2f, outline(r), outline(g), outline(b), 0.4f * p)
      sb.fillPolygon(_polyXs4, _polyYs4, 4, dr, dg, db, 0.94f * p)
      sb.fillOval(sx, sy, w * 0.5f, w * 0.5f, dr, dg, db, 0.94f * p, 12)
      // White-hot core, stopping short of the tail
      val cbx = bx + ndx * L * 0.15f; val cby = by + ndy * L * 0.15f
      _polyXs4(0) = sx + px * w * 0.24f; _polyYs4(0) = sy + py * w * 0.24f
      _polyXs4(1) = cbx + px * w * 0.035f; _polyYs4(1) = cby + py * w * 0.035f
      _polyXs4(2) = cbx - px * w * 0.035f; _polyYs4(2) = cby - py * w * 0.035f
      _polyXs4(3) = sx - px * w * 0.24f; _polyYs4(3) = sy - py * w * 0.24f
      sb.fillPolygon(_polyXs4, _polyYs4, 4, hR, hG, hB, 0.97f * p)
      sb.fillOval(sx, sy, w * 0.26f, w * 0.26f, 1f, 1f, 1f, 0.95f * p, 10)
      sb.fillStarFlare(sx + ndx * w * 0.25f, sy + ndy * w * 0.25f, w * 1.25f, 1.8f,
        Math.atan2(ndy, ndx).toFloat, 0.45f, 1f, 1f, 1f, 0.6f * p)

      kind match {
        case BOLT_EYE =>
          var k = 0
          while (k < 2) {
            val t = ((phase * 0.22 + k * 0.5) % 1.0).toFloat
            strokeRotEllipse(sb, sx - ndx * t * L * 0.55f, sy - ndy * t * L * 0.55f, px, py,
              w * (0.8f + t * 1.3f), w * (0.3f + t * 0.45f), 2.2f * (1f - t * 0.6f),
              hR, hG, hB, 0.7f * (1f - t) * p, 14)
            k += 1
          }
        case BOLT_LASER =>
          val t = ((phase * 0.3) % 1.0).toFloat
          strokeRotEllipse(sb, sx - ndx * t * L, sy - ndy * t * L, px, py,
            w * (0.7f - t * 0.3f), w * 0.26f, 1.6f, hR, hG, hB, 0.55f * (1f - t) * p, 12)
        case _ =>
          drawSparkBurst(sx, sy, dr, dg, db, 0.4f * p, sb, tick, proj.id, 4, w * 1.4f)
      }
      drawChargeCrackle(sx, sy, w * 1.4f, r, g, b, p, sb, phase, proj.chargeLevel)
    }

  // ── Railgun slug ──
  private val SLUG_PARTS = Array(
    part(Array(-0.95f,-0.20f, 0.40f,-0.22f, 1.12f,0f, 0.40f,0.22f, -0.95f,0.20f), DKSTEEL_R, DKSTEEL_G, DKSTEEL_B, 0.30f),
    part(Array(-0.36f,-0.25f, -0.14f,-0.25f, -0.14f,0.25f, -0.36f,0.25f), 0.80f, 0.54f, 0.30f, 0.10f),
    part(Array(-0.70f,-0.10f, 0.40f,-0.12f, 0.92f,0f, 0.40f,0f, -0.70f,-0.01f), STEEL_R, STEEL_G, STEEL_B, 0.20f)
  )

  /** Railgun slug: a dense dart at the hitbox shedding electromagnetic coil rings that widen
   *  and fade as they fall behind — acceleration made visible without a trailing rod. */
  private def railSlug(r: Float, g: Float, b: Float, size: Float = 12f): Renderer =
    (proj, sx, sy, sb, tick) => {
      val phase = (tick + proj.id * 31) * 0.4
      computeAllDynamics(proj, r, g, b, phase)
      val p = (0.9f + 0.1f * Math.sin(phase * 4 * _stPulseMult).toFloat) * dynAlpha
      val dr = _evoR; val dg = _evoG; val db = _evoB
      val ds = Math.min(dynScale, 1.3f)
      val s = size * ds
      screenDir(proj)
      val ndx = _sdx; val ndy = _sdy
      val px = -ndy; val py = ndx
      val hR = mix(bright(r), 1f, 0.5f); val hG = mix(bright(g), 1f, 0.5f); val hB = mix(bright(b), 1f, 0.5f)

      fadeLine(sb, sx - ndx * s * 0.6f, sy - ndy * s * 0.6f, sx - ndx * s * 6f, sy - ndy * s * 6f,
        s * 0.8f, dr, dg, db, 0.5f * p, 6)
      var k = 0
      while (k < 4) {
        val t = ((tick * 0.11 + k * 0.25 + proj.id * 0.1) % 1.0).toFloat
        val cx = sx - ndx * s * (0.8f + t * 4.6f); val cy = sy - ndy * s * (0.8f + t * 4.6f)
        strokeRotEllipse(sb, cx, cy, px, py, s * (0.55f + t * 0.55f), s * (0.20f + t * 0.14f),
          2.4f * (1f - t * 0.5f), hR, hG, hB, 0.8f * (1f - t) * p, 14)
        k += 1
      }
      sb.fillOvalSoft(sx, sy, s * 1.5f * dynGlow, s * 1.2f * dynGlow, dr, dg, db, 0.38f * p, 0f, 12)
      drawPartsDir(sb, SLUG_PARTS, sx, sy, ndx, ndy, s, dr, dg, db, 0.97f * dynAlpha, clampF(s * 0.14f, 1.2f, 2.6f))
      dirPoint(1.12f, 0f, sx, sy, ndx, ndy, s)
      sb.fillStarFlare(_ptX, _ptY, s * 0.9f, 2f, Math.atan2(ndy, ndx).toFloat, 0.4f, hR, hG, hB, 0.75f * p)
      drawChargeCrackle(sx, sy, s * 1.2f, r, g, b, p, sb, phase, proj.chargeLevel)
    }

  /** Ice beam as a travelling frost comet: a slowly turning six-armed ice crystal trailing a
   *  cold mist that swells and thins behind it. The ability flies slowly (0.3) and freezes
   *  on hit, so its head has to read as ice for the whole time it is on screen. */
  private def drawFrostComet(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 31) * 0.3
    computeAllDynamics(proj, 0.55f, 0.85f, 1f, phase)
    val p = (0.9f + 0.1f * Math.sin(phase * _stPulseMult).toFloat) * dynAlpha
    val dr = _evoR; val dg = _evoG; val db = _evoB
    val ds = Math.min(dynScale, 1.4f)
    val R = 15f * ds
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val px = -ndy; val py = ndx

    var i = 0
    while (i < 7) {
      val t = ((tick * 0.03 + i / 7.0 + proj.id * 0.13) % 1.0).toFloat
      val sway = Math.sin(phase * 0.9 + i * 1.7).toFloat * R * 0.5f * t
      sb.fillOvalSoft(sx - ndx * t * R * 4.4f + px * sway, sy - ndy * t * R * 4.4f + py * sway,
        R * (0.55f + t * 1.1f), R * (0.45f + t * 0.8f), 0.78f, 0.92f, 1f, 0.34f * (1f - t) * p, 0f, 12)
      i += 1
    }
    i = 0
    while (i < 5) {
      val t = ((tick * 0.045 + i * 0.2 + proj.id * 0.17) % 1.0).toFloat
      val sway = Math.sin(t * 6.0 + i * 2.1).toFloat * R * 0.7f
      drawSparkleStar(sx - ndx * t * R * 3.6f + px * sway, sy - ndy * t * R * 3.6f + py * sway + t * 6f,
        4.5f * (1f - t * 0.5f), 0.9f, 0.97f, 1f, 0.7f * (1f - t) * p, sb, t * 7.0 + i)
      i += 1
    }
    sb.fillOvalSoft(sx, sy, R * 2.3f * dynGlow, R * 1.9f * dynGlow, dr, dg, db, 0.32f * p, 0f, 16)

    // Six-armed crystal tilted into the ground plane. Each arm is a convex kite; the side
    // branches are what make it a snowflake rather than a star.
    val rot = phase * 0.22
    val sq = 0.78f
    var k = 0
    while (k < 6) {
      val a = rot + k * Math.PI / 3
      val ca = Math.cos(a).toFloat; val sa = Math.sin(a).toFloat
      val armL = R * (if ((k & 1) == 0) 1.08f else 0.82f)
      _shpXs(0) = sx + ca * R * 0.12f + sa * R * 0.10f; _shpYs(0) = sy + (sa * R * 0.12f - ca * R * 0.10f) * sq
      _shpXs(1) = sx + ca * armL * 0.55f + sa * R * 0.20f; _shpYs(1) = sy + (sa * armL * 0.55f - ca * R * 0.20f) * sq
      _shpXs(2) = sx + ca * armL; _shpYs(2) = sy + sa * armL * sq
      _shpXs(3) = sx + ca * armL * 0.55f - sa * R * 0.20f; _shpYs(3) = sy + (sa * armL * 0.55f + ca * R * 0.20f) * sq
      _shpXs(4) = sx + ca * R * 0.12f - sa * R * 0.10f; _shpYs(4) = sy + (sa * R * 0.12f + ca * R * 0.10f) * sq
      sb.strokePolygon(_shpXs, _shpYs, 5, 2.4f, 0.10f, 0.22f, 0.40f, 0.85f * p)
      sb.fillPolygon(_shpXs, _shpYs, 5, mix(0.78f, dr, 0.3f), mix(0.93f, dg, 0.3f), mix(1f, db, 0.3f), 0.95f * p)
      val bxm = sx + ca * armL * 0.62f; val bym = sy + sa * armL * 0.62f * sq
      var sgn = -1
      while (sgn <= 1) {
        val ba = a + sgn * 0.7
        sb.strokeLine(bxm, bym, bxm + Math.cos(ba).toFloat * R * 0.30f, bym + Math.sin(ba).toFloat * R * 0.30f * sq,
          1.6f, 1f, 1f, 1f, 0.8f * p)
        sgn += 2
      }
      k += 1
    }
    k = 0
    while (k < 6) {
      val a = rot + k * Math.PI / 3 + Math.PI / 6
      _shpXs(k) = sx + Math.cos(a).toFloat * R * 0.34f
      _shpYs(k) = sy + Math.sin(a).toFloat * R * 0.34f * sq
      k += 1
    }
    sb.strokePolygon(_shpXs, _shpYs, 6, 2f, 0.10f, 0.22f, 0.40f, 0.8f * p)
    sb.fillPolygon(_shpXs, _shpYs, 6, 0.92f, 0.98f, 1f, 0.97f * p)
    sb.fillStarFlare(sx, sy, R * 0.9f, 2.2f, (phase * 0.5).toFloat, 0.55f, 1f, 1f, 1f, 0.7f * p)
    // Three shards orbiting the crystal
    k = 0
    while (k < 3) {
      val a = -phase * 0.9 + k * Math.PI * 2 / 3
      val ox = sx + Math.cos(a).toFloat * R * 1.45f; val oy = sy + Math.sin(a).toFloat * R * 1.45f * ISO_Y
      _polyXs4(0) = ox; _polyYs4(0) = oy - 4.5f * ds
      _polyXs4(1) = ox + 2.4f * ds; _polyYs4(1) = oy
      _polyXs4(2) = ox; _polyYs4(2) = oy + 3.2f * ds
      _polyXs4(3) = ox - 2.4f * ds; _polyYs4(3) = oy
      sb.strokePolygon(_polyXs4, _polyYs4, 4, 1.6f, 0.10f, 0.22f, 0.40f, 0.7f * p)
      sb.fillPolygon(_polyXs4, _polyYs4, 4, 0.85f, 0.95f, 1f, 0.9f * p)
      k += 1
    }
    drawChargeCrackle(sx, sy, R, 0.55f, 0.85f, 1f, p, sb, phase, proj.chargeLevel)
  }

  /** Medusa's gaze as a travelling gorgon eye — an almond eye with a slit pupil that blinks,
   *  ringed by crumbling stone. The old stone beam, a grey rod of pebbles, said nothing
   *  about who cast it. `petrify` swaps the sickly glow for dead grey stone. */
  private def gorgonEye(petrify: Boolean): Renderer =
    (proj, sx, sy, sb, tick) => {
      val phase = (tick + proj.id * 27) * 0.3
      val ir = if (petrify) 0.80f else 0.72f
      val ig = if (petrify) 0.78f else 0.95f
      val ib = if (petrify) 0.70f else 0.30f
      computeAllDynamics(proj, ir, ig, ib, phase)
      val p = (0.9f + 0.1f * Math.sin(phase * _stPulseMult).toFloat) * dynAlpha
      val ds = Math.min(dynScale, 1.35f)
      val W = 17f * ds; val H = 10f * ds
      screenDir(proj)
      val ndx = _sdx; val ndy = _sdy

      // Stone dust and pebbles falling away behind
      var i = 0
      while (i < 6) {
        val t = ((tick * 0.04 + i / 6.0 + proj.id * 0.19) % 1.0).toFloat
        val dx2 = sx - ndx * t * W * 3.2f + Math.sin(i * 2.1 + phase).toFloat * W * 0.35f
        val dy2 = sy - ndy * t * W * 3.2f + t * t * 14f
        if ((i & 1) == 0) sb.fillOvalSoft(dx2, dy2, W * (0.3f + t * 0.5f), W * (0.24f + t * 0.4f),
          0.62f, 0.60f, 0.56f, 0.36f * (1f - t) * p, 0f, 10)
        else sb.fillOval(dx2, dy2, 2.6f * ds, 2.2f * ds, 0.46f, 0.44f, 0.40f, 0.8f * (1f - t) * p, 6)
        i += 1
      }
      // Ring of crumbling stone chips
      var c = 0
      while (c < 8) {
        val a = phase * 0.7 + c * Math.PI / 4
        val cx = sx + Math.cos(a).toFloat * W * 1.45f; val cy = sy + Math.sin(a).toFloat * W * 1.45f * ISO_Y
        val cs = (3f + (c % 3)) * ds
        _polyXs4(0) = cx - cs; _polyYs4(0) = cy
        _polyXs4(1) = cx - cs * 0.2f; _polyYs4(1) = cy - cs * 0.8f
        _polyXs4(2) = cx + cs; _polyYs4(2) = cy - cs * 0.1f
        _polyXs4(3) = cx + cs * 0.3f; _polyYs4(3) = cy + cs * 0.7f
        sb.strokePolygon(_polyXs4, _polyYs4, 4, 1.6f, 0.12f, 0.11f, 0.10f, 0.75f * p)
        sb.fillPolygon(_polyXs4, _polyYs4, 4, 0.60f, 0.58f, 0.54f, 0.95f * p)
        c += 1
      }
      sb.fillOvalSoft(sx, sy, W * 1.9f * dynGlow, W * 1.4f * dynGlow, ir, ig, ib, 0.38f * p, 0f, 16)

      // The lids close briefly every few seconds — a living eye, not a decal
      val bcyc = ((phase * 0.12 + proj.id * 0.37) % 1.0).toFloat
      val lid = if (bcyc > 0.94f) Math.abs(bcyc - 0.97f) / 0.03f else 1f
      val h = H * Math.max(0.12f, lid)
      val n = 7
      i = 0
      while (i < n) {
        val t = i.toFloat / (n - 1)
        _shpXs(i) = sx - W + 2f * W * t
        _shpYs(i) = sy - h * Math.sin(t * Math.PI).toFloat
        i += 1
      }
      i = 1
      while (i < n - 1) {
        val t = 1f - i.toFloat / (n - 1)
        _shpXs(n + i - 1) = sx - W + 2f * W * t
        _shpYs(n + i - 1) = sy + h * Math.sin(t * Math.PI).toFloat
        i += 1
      }
      val m = n + n - 2
      sb.strokePolygon(_shpXs, _shpYs, m, 3.2f, 0.07f, 0.06f, 0.05f, 0.9f * p)
      sb.fillPolygon(_shpXs, _shpYs, m, ir, ig, ib, 0.96f * p)
      if (lid > 0.4f) {
        sb.fillOval(sx, sy, h * 0.9f, h * 0.9f, ir * 0.5f, ig * 0.5f, ib * 0.4f, 0.9f * p, 14)
        sb.fillOval(sx, sy, h * 0.7f, h * 0.7f, mix(ir, 1f, 0.3f), mix(ig, 1f, 0.3f), mix(ib, 1f, 0.2f), 0.95f * p, 14)
        _polyXs4(0) = sx; _polyYs4(0) = sy - h * 0.85f
        _polyXs4(1) = sx + h * 0.17f; _polyYs4(1) = sy
        _polyXs4(2) = sx; _polyYs4(2) = sy + h * 0.85f
        _polyXs4(3) = sx - h * 0.17f; _polyYs4(3) = sy
        sb.fillPolygon(_polyXs4, _polyYs4, 4, 0.05f, 0.04f, 0.03f, 0.95f * p)
        sb.fillOval(sx - h * 0.3f, sy - h * 0.35f, h * 0.18f, h * 0.14f, 1f, 1f, 1f, 0.8f * p, 8)
      }
      // Stone brow over the eye
      sb.strokeArc(sx, sy + H * 0.25f, W * 1.08f, H * 1.6f, 3.62f, 2.18f, 3.6f, 0.44f, 0.42f, 0.39f, 0.9f * p, 10)
      drawChargeCrackle(sx, sy, W, ir, ig, ib, p, sb, phase, proj.chargeLevel)
    }

  // ── Drain vortices ──
  private val SIPH_BLOOD = 0
  private val SIPH_LIFE = 1
  private val SIPH_SOUL = 2

  /** Drain abilities as a travelling whirlpool: motes spiral INTO a dark core, which is what a
   *  siphon is. The old drain beam — a tube with beads streaming along it — was the purest
   *  case of a shiny snake in the roster. Blood sheds drips, life beats a heart in the dark,
   *  a soul drain stares back. */
  private def siphonVortex(kind: Int, r: Float, g: Float, b: Float, size: Float = 15f): Renderer =
    (proj, sx, sy, sb, tick) => {
      val phase = (tick + proj.id * 33) * 0.35
      computeAllDynamics(proj, r, g, b, phase)
      val p = (0.88f + 0.12f * Math.sin(phase * 2 * _stPulseMult).toFloat) * dynAlpha
      val dr = _evoR; val dg = _evoG; val db = _evoB
      val ds = Math.min(dynScale, 1.35f)
      val R = size * ds
      screenDir(proj)
      val ndx = _sdx; val ndy = _sdy
      val soul = kind == SIPH_SOUL

      var i = 0
      while (i < 5) {
        val t = ((tick * 0.04 + i * 0.2 + proj.id * 0.21) % 1.0).toFloat
        val tx = sx - ndx * t * R * 3f + Math.sin(i * 2.3 + phase).toFloat * R * 0.4f
        val ty = sy - ndy * t * R * 3f + (if (soul) -t * 18f else t * t * 16f)
        if (soul) sb.fillOvalSoft(tx, ty, R * 0.45f * (1f - t * 0.4f), R * 0.55f * (1f - t * 0.4f),
          dr, dg, db, 0.45f * (1f - t) * p, 0f, 10)
        else sb.fillOval(tx, ty, R * 0.16f, R * 0.22f, dr * 0.85f, dg * 0.6f, db * 0.6f, 0.8f * (1f - t) * p, 8)
        i += 1
      }
      sb.fillOvalSoft(sx, sy, R * 2.1f * dynGlow, R * 1.7f * dynGlow, dr, dg, db, 0.34f * p, 0f, 16)

      // Two spiral arms wound in toward the core
      var arm = 0
      while (arm < 2) {
        val base = phase * 1.6 + arm * Math.PI
        var s2 = 0
        while (s2 < 7) {
          val t0 = s2 / 7f; val t1 = (s2 + 1) / 7f
          val a0 = base + t0 * 3.0; val a1 = base + t1 * 3.0
          val r0 = R * (1.25f - t0 * 1.05f); val r1 = R * (1.25f - t1 * 1.05f)
          sb.strokeLineSoft(sx + Math.cos(a0).toFloat * r0, sy + Math.sin(a0).toFloat * r0 * ISO_Y,
            sx + Math.cos(a1).toFloat * r1, sy + Math.sin(a1).toFloat * r1 * ISO_Y,
            R * 0.30f * (1f - t0 * 0.6f), bright(r), bright(g), bright(b), (0.35f + 0.55f * t0) * p)
          s2 += 1
        }
        arm += 1
      }
      // Motes pulled in along the spiral, brightening as they fall
      var m = 0
      while (m < 9) {
        val t = ((tick * 0.05 + m / 9.0 + proj.id * 0.11) % 1.0).toFloat
        val a = phase * 1.6 + m * 0.7 + t * 4.2
        val rr = R * 1.4f * (1f - t)
        val mx = sx + Math.cos(a).toFloat * rr; val my = sy + Math.sin(a).toFloat * rr * ISO_Y
        val a2 = a - 0.35
        val rr2 = R * 1.4f * (1f - Math.max(0f, t - 0.06f))
        val mx2 = sx + Math.cos(a2).toFloat * rr2; val my2 = sy + Math.sin(a2).toFloat * rr2 * ISO_Y
        val ma = Math.min(1f, t * 3f) * (1f - t * 0.3f)
        sb.strokeLineSoft(mx2, my2, mx, my, R * 0.16f, bright(r), bright(g), bright(b), 0.6f * ma * p)
        sb.fillOval(mx, my, R * 0.11f, R * 0.11f, mix(bright(r), 1f, 0.3f), mix(bright(g), 1f, 0.3f),
          mix(bright(b), 1f, 0.3f), 0.9f * ma * p, 6)
        m += 1
      }
      // Core: a hole ringed in the drain's colour
      sb.fillOval(sx, sy, R * 0.56f, R * 0.48f, dr, dg, db, 0.92f * p, 14)
      sb.fillOval(sx, sy, R * 0.44f, R * 0.37f, 0.04f, 0.01f, 0.03f, 0.96f * p, 14)
      kind match {
        case SIPH_SOUL =>
          sb.fillOval(sx - R * 0.15f, sy - R * 0.05f, R * 0.08f, R * 0.11f, bright(r), bright(g), bright(b), 0.95f * p, 6)
          sb.fillOval(sx + R * 0.15f, sy - R * 0.05f, R * 0.08f, R * 0.11f, bright(r), bright(g), bright(b), 0.95f * p, 6)
          sb.fillOval(sx, sy + R * 0.16f, R * 0.06f, R * 0.04f, bright(r), bright(g), bright(b), 0.7f * p, 6)
        case SIPH_LIFE =>
          val beat = 0.8f + 0.2f * Math.abs(Math.sin(phase * 2.4).toFloat)
          val hs = R * 0.26f * beat
          sb.fillOval(sx - hs * 0.5f, sy - hs * 0.2f, hs * 0.58f, hs * 0.58f, dr, dg * 0.6f, db * 0.7f, 0.95f * p, 8)
          sb.fillOval(sx + hs * 0.5f, sy - hs * 0.2f, hs * 0.58f, hs * 0.58f, dr, dg * 0.6f, db * 0.7f, 0.95f * p, 8)
          _polyXs3(0) = sx - hs * 1.05f; _polyYs3(0) = sy - hs * 0.05f
          _polyXs3(1) = sx + hs * 1.05f; _polyYs3(1) = sy - hs * 0.05f
          _polyXs3(2) = sx; _polyYs3(2) = sy + hs * 1.05f
          sb.fillPolygon(_polyXs3, _polyYs3, 3, dr, dg * 0.6f, db * 0.7f, 0.95f * p)
        case _ =>
          sb.fillOval(sx, sy + R * 0.02f, R * 0.13f, R * 0.17f, dr, dg * 0.5f, db * 0.5f, 0.9f * p, 8)
      }
      sb.strokeOval(sx, sy, R * 0.56f, R * 0.48f, 2f, bright(r), bright(g), bright(b), 0.8f * p, 14)
      drawChargeCrackle(sx, sy, R, r, g, b, p, sb, phase, proj.chargeLevel)
    }

  // ── Grasping claws (vine whip / root pull, tentacle) ──
  private val CLAW_VINE = 0
  private val CLAW_TENTACLE = 1
  private val CLAW_SEGS = 5
  // Three talons x (CLAW_SEGS + 1) joints, built once per frame and then drawn in passes
  private val _clawXs = new Array[Float](3 * (CLAW_SEGS + 1))
  private val _clawYs = new Array[Float](3 * (CLAW_SEGS + 1))

  /**
   * Grab-and-pull ability: three talons curling shut around a knot at the hitbox, opening
   * and closing as it flies. The vine whip and the tentacle used to be sine-wiggled lines
   * running out ahead of the projectile to a disc — the literal snake. Drawn in passes (all
   * contours, then all bodies, then detail) so no talon's outline cuts across another.
   */
  private def graspingClaw(kind: Int, r: Float, g: Float, b: Float, size: Float = 26f): Renderer =
    (proj, sx, sy, sb, tick) => {
      val phase = (tick + proj.id * 23) * 0.35
      computeAllDynamics(proj, r, g, b, phase)
      val p = (0.9f + 0.1f * Math.sin(phase * _stPulseMult).toFloat) * dynAlpha
      val dr = _evoR; val dg = _evoG; val db = _evoB
      val ds = Math.min(dynScale, 1.3f)
      val s = size * ds
      screenDir(proj)
      val ndx = _sdx; val ndy = _sdy
      val px = -ndy; val py = ndx
      val vine = kind == CLAW_VINE
      val grip = 0.5f + 0.5f * Math.sin(phase * 1.5).toFloat
      val kx = sx - ndx * s * 0.30f; val ky = sy - ndy * s * 0.30f
      val heading = Math.atan2(ndy, ndx)
      val bodyR = if (vine) mix(0.26f, dr, 0.5f) else dr
      val bodyG = if (vine) mix(0.50f, dg, 0.5f) else dg
      val bodyB = if (vine) mix(0.16f, db, 0.5f) else db

      drawRibbonTrail(kx, ky, ndx, ndy, dr, dg, db, 0.2f * p, sb, tick, proj.id, 6, s * 1.4f * dynTrail, s * 0.3f, 1f)
      var i = 0
      while (i < 4) {
        val t = ((tick * 0.04 + i * 0.25 + proj.id * 0.17) % 1.0).toFloat
        val drift = Math.sin(t * 5.0 + i * 1.9).toFloat * s * 0.3f
        val lx = kx - ndx * t * s * 1.6f + px * drift
        val ly = ky - ndy * t * s * 1.6f + py * drift + t * t * 10f
        if (vine) sb.fillOval(lx, ly, 4.2f * (1f - t * 0.4f), 2.4f * (1f - t * 0.4f), 0.34f, 0.66f, 0.24f, 0.7f * (1f - t) * p, 6)
        else sb.fillOval(lx, ly, 3.2f * (1f - t * 0.4f), 4f * (1f - t * 0.4f), bright(r), bright(g), bright(b), 0.6f * (1f - t) * p, 7)
        i += 1
      }
      sb.fillOvalSoft(sx, sy, s * 1.2f * dynGlow, s * 1.0f * dynGlow, dr, dg, db, 0.26f * p, 0f, 14)

      // Joints: each talon curls in toward the middle one, tighter as the grip closes
      var k = 0
      while (k < 3) {
        val side = k - 1
        var ang = heading + side * (0.62 - grip * 0.30)
        var x = kx + px * side * s * 0.16f; var y = ky + py * side * s * 0.16f
        val base = k * (CLAW_SEGS + 1)
        _clawXs(base) = x; _clawYs(base) = y
        var j = 1
        while (j <= CLAW_SEGS) {
          val t = (j - 1).toFloat / CLAW_SEGS
          ang -= side * (0.05 + grip * 0.11) * (0.6 + t)
          if (side == 0) ang += Math.sin(phase * 1.2 + j) * 0.05
          val segL = s * 0.30f * (1f - t * 0.3f)
          x += Math.cos(ang).toFloat * segL
          y += Math.sin(ang).toFloat * segL * 0.85f
          _clawXs(base + j) = x; _clawYs(base + j) = y
          j += 1
        }
        k += 1
      }
      // Pass 1: contours
      k = 0
      while (k < 3) {
        val base = k * (CLAW_SEGS + 1)
        var j = 0
        while (j < CLAW_SEGS) {
          val w = s * 0.30f * (1f - j.toFloat / CLAW_SEGS * 0.82f) + 3f
          sb.strokeLine(_clawXs(base + j), _clawYs(base + j), _clawXs(base + j + 1), _clawYs(base + j + 1),
            w, 0.06f, 0.05f, 0.06f, 0.82f * p)
          sb.fillOval(_clawXs(base + j), _clawYs(base + j), w * 0.5f, w * 0.5f, 0.06f, 0.05f, 0.06f, 0.82f * p, 8)
          j += 1
        }
        k += 1
      }
      // Pass 2: bodies, with a lit stripe along the upper-left edge
      k = 0
      while (k < 3) {
        val base = k * (CLAW_SEGS + 1)
        var j = 0
        while (j < CLAW_SEGS) {
          val w = s * 0.30f * (1f - j.toFloat / CLAW_SEGS * 0.82f)
          val x0 = _clawXs(base + j); val y0 = _clawYs(base + j)
          val x1 = _clawXs(base + j + 1); val y1 = _clawYs(base + j + 1)
          sb.strokeLine(x0, y0, x1, y1, w, bodyR, bodyG, bodyB, 0.97f * p)
          sb.fillOval(x0, y0, w * 0.5f, w * 0.5f, bodyR, bodyG, bodyB, 0.97f * p, 8)
          sb.strokeLine(x0 + KEY_LIGHT_X * w * 0.22f, y0 + KEY_LIGHT_Y * w * 0.22f,
            x1 + KEY_LIGHT_X * w * 0.22f, y1 + KEY_LIGHT_Y * w * 0.22f, w * 0.28f,
            mix(bodyR, 1f, 0.45f), mix(bodyG, 1f, 0.45f), mix(bodyB, 1f, 0.45f), 0.5f * p)
          j += 1
        }
        k += 1
      }
      // Pass 3: thorns on the outer edge (vine) or suckers on the inner edge (tentacle)
      k = 0
      while (k < 3) {
        val side = if (k == 1) (if (((tick / 20) & 1) == 0) 1 else -1) else k - 1
        val base = k * (CLAW_SEGS + 1)
        var j = 1
        while (j < CLAW_SEGS - 1) {
          val x0 = _clawXs(base + j); val y0 = _clawYs(base + j)
          val x1 = _clawXs(base + j + 1); val y1 = _clawYs(base + j + 1)
          val sdx = x1 - x0; val sdy = y1 - y0
          val sl = Math.max(0.001f, Math.sqrt(sdx * sdx + sdy * sdy).toFloat)
          val ux = sdx / sl; val uy = sdy / sl
          val nx = -uy * side; val ny = ux * side
          val w = s * 0.30f * (1f - j.toFloat / CLAW_SEGS * 0.82f)
          val mx = (x0 + x1) * 0.5f; val my = (y0 + y1) * 0.5f
          if (vine) {
            _polyXs3(0) = mx - ux * w * 0.35f + nx * w * 0.35f; _polyYs3(0) = my - uy * w * 0.35f + ny * w * 0.35f
            _polyXs3(1) = mx + ux * w * 0.35f + nx * w * 0.35f; _polyYs3(1) = my + uy * w * 0.35f + ny * w * 0.35f
            _polyXs3(2) = mx + ux * w * 0.5f + nx * w * 1.25f; _polyYs3(2) = my + uy * w * 0.5f + ny * w * 1.25f
            sb.fillPolygon(_polyXs3, _polyYs3, 3, 0.20f, 0.16f, 0.08f, 0.9f * p)
            if (j == 2) sb.fillOval(mx - nx * w * 0.95f, my - ny * w * 0.95f, w * 0.62f, w * 0.34f,
              0.36f, 0.72f, 0.26f, 0.95f * p, 8)
          } else {
            sb.fillOval(mx - nx * w * 0.32f, my - ny * w * 0.32f, w * 0.22f, w * 0.2f, 0.78f, 0.52f, 0.86f, 0.85f * p, 8)
          }
          j += 1
        }
        k += 1
      }
      // The knot the talons grow from
      if (vine) {
        var lf = -1
        while (lf <= 1) {
          val la = heading + Math.PI + lf * 0.75
          val lx = kx + Math.cos(la).toFloat * s * 0.46f; val ly = ky + Math.sin(la).toFloat * s * 0.46f * 0.8f
          val qx = -Math.sin(la).toFloat * s * 0.14f; val qy = Math.cos(la).toFloat * s * 0.14f * 0.8f
          _polyXs4(0) = kx; _polyYs4(0) = ky
          _polyXs4(1) = (kx + lx) * 0.5f + qx; _polyYs4(1) = (ky + ly) * 0.5f + qy
          _polyXs4(2) = lx; _polyYs4(2) = ly
          _polyXs4(3) = (kx + lx) * 0.5f - qx; _polyYs4(3) = (ky + ly) * 0.5f - qy
          sb.strokePolygon(_polyXs4, _polyYs4, 4, 1.8f, 0.06f, 0.14f, 0.05f, 0.8f * p)
          sb.fillPolygon(_polyXs4, _polyYs4, 4, 0.32f, 0.66f, 0.24f, 0.95f * p)
          sb.strokeLine(kx, ky, lx, ly, 1.1f, 0.18f, 0.40f, 0.12f, 0.8f * p)
          lf += 2
        }
        sb.fillOval(kx, ky, s * 0.24f + 1.6f, s * 0.2f + 1.6f, 0.06f, 0.05f, 0.04f, 0.82f * p, 10)
        sb.fillOval(kx, ky, s * 0.24f, s * 0.2f, 0.36f, 0.26f, 0.14f, 0.97f * p, 10)
      } else {
        sb.fillOval(kx, ky, s * 0.28f + 1.6f, s * 0.24f + 1.6f, 0.06f, 0.05f, 0.06f, 0.82f * p, 12)
        sb.fillOval(kx, ky, s * 0.28f, s * 0.24f, bodyR * 0.85f, bodyG * 0.85f, bodyB * 0.85f, 0.97f * p, 12)
        sb.fillOval(kx - s * 0.08f, ky - s * 0.08f, s * 0.09f, s * 0.06f, 1f, 1f, 1f, 0.5f * p, 6)
      }
      drawChargeCrackle(sx, sy, s * 0.8f, r, g, b, p, sb, phase, proj.chargeLevel)
    }

  private val PAW_PARTS = Array(
    part(Array(-1.20f,-0.34f, -0.55f,-0.46f, -0.55f,0.46f, -1.20f,0.34f), 0.36f, 0.25f, 0.16f, 0.30f),
    part(Array(-0.62f,-0.56f, 0.05f,-0.64f, 0.42f,-0.44f, 0.42f,0.44f, 0.05f,0.64f, -0.62f,0.56f), 0.46f, 0.32f, 0.20f, 0.30f)
  )

  /** Bear hug / primate grab: a clawed paw flying open-handed and snatching shut. The old
   *  whip-beam made both of these a sine-wiggled rope with a knob on the end. */
  private def drawGrabPaw(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 19) * 0.35
    computeAllDynamics(proj, 0.55f, 0.42f, 0.28f, phase)
    val p = (0.9f + 0.1f * Math.sin(phase * _stPulseMult).toFloat) * dynAlpha
    val dr = _evoR; val dg = _evoG; val db = _evoB
    val ds = Math.min(dynScale, 1.3f)
    val s = 22f * ds
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val grip = 0.5f + 0.5f * Math.sin(phase * 1.4).toFloat
    val furR = mix(0.46f, dr, 0.3f); val furG = mix(0.32f, dg, 0.3f); val furB = mix(0.20f, db, 0.3f)

    drawSpeedLines(sx, sy, ndx, ndy, dr, dg, db, 0.32f * p, sb, 5, s * 1.8f)
    drawRibbonTrail(sx, sy, ndx, ndy, dr, dg, db, 0.22f * p, sb, tick, proj.id, 6, s * 1.8f * dynTrail, s * 0.4f, 1f)
    sb.fillOval(sx + 3f, sy + s * 0.6f, s * 1.0f, s * 0.24f, 0f, 0f, 0f, 0.22f * p, 12)
    var ghost = 2
    while (ghost >= 1) {
      drawPartsDirFlat(sb, PAW_PARTS, sx - ndx * ghost * 10f, sy - ndy * ghost * 10f, ndx, ndy,
        s * (1f - ghost * 0.05f), dr * 0.8f, dg * 0.8f, db * 0.8f, 0.14f * (1f - (ghost - 1) * 0.35f) * p)
      ghost -= 1
    }
    drawPartsDir(sb, PAW_PARTS, sx, sy, ndx, ndy, s, dr, dg, db, 0.97f * dynAlpha, clampF(s * 0.12f, 1.5f, 3f))

    // Four fingers splaying and snatching shut, each ending in a claw
    var f = 0
    while (f < 4) {
      val ly = -0.42f + f * 0.28f
      dirPoint(0.34f, ly, sx, sy, ndx, ndy, s)
      val bx = _ptX; val by = _ptY
      val spread = ly * (1.0f - grip * 0.8f)
      val cs = Math.cos(spread).toFloat; val sn = Math.sin(spread).toFloat
      val fx = ndx * cs - ndy * sn; val fy = ndx * sn + ndy * cs
      val flen = s * (0.46f - Math.abs(ly) * 0.18f) * (1f - grip * 0.25f)
      val ex = bx + fx * flen; val ey = by + fy * flen
      val fw = s * 0.26f
      sb.strokeLine(bx, by, ex, ey, fw + 3f, 0.06f, 0.05f, 0.04f, 0.85f * p)
      sb.fillOval(ex, ey, (fw + 3f) * 0.5f, (fw + 3f) * 0.5f, 0.06f, 0.05f, 0.04f, 0.85f * p, 8)
      sb.strokeLine(bx, by, ex, ey, fw, furR, furG, furB, 0.97f * p)
      sb.fillOval(ex, ey, fw * 0.5f, fw * 0.5f, furR, furG, furB, 0.97f * p, 8)
      val tx = ex + fx * s * 0.28f; val ty = ey + fy * s * 0.28f
      val qx = -fy * s * 0.085f; val qy = fx * s * 0.085f
      _polyXs3(0) = ex + qx; _polyYs3(0) = ey + qy
      _polyXs3(1) = tx; _polyYs3(1) = ty
      _polyXs3(2) = ex - qx; _polyYs3(2) = ey - qy
      sb.strokePolygon(_polyXs3, _polyYs3, 3, 1.6f, 0.06f, 0.05f, 0.04f, 0.85f * p)
      sb.fillPolygon(_polyXs3, _polyYs3, 3, 0.93f, 0.89f, 0.78f, 0.97f * p)
      f += 1
    }
    // Knuckle ridge and a lit edge so the back of the paw has volume
    dirPoint(0.10f, -0.36f, sx, sy, ndx, ndy, s)
    val k0x = _ptX; val k0y = _ptY
    dirPoint(0.10f, 0.36f, sx, sy, ndx, ndy, s)
    sb.strokeLine(k0x, k0y, _ptX, _ptY, 2f, mix(furR, 0f, 0.4f), mix(furG, 0f, 0.4f), mix(furB, 0f, 0.4f), 0.55f * p)
    dirPoint(-0.3f, -0.3f, sx, sy, ndx, ndy, s)
    sb.fillOval(_ptX, _ptY, s * 0.18f, s * 0.12f, 1f, 0.95f, 0.85f, 0.25f * p, 8)
    if (grip > 0.85f) {
      dirPoint(0.9f, 0f, sx, sy, ndx, ndy, s)
      sb.fillStarFlare(_ptX, _ptY, s * 0.6f * (grip - 0.85f) / 0.15f, 2.2f, (phase * 0.5).toFloat, 0.5f,
        1f, 0.95f, 0.8f, 0.7f * p)
    }
    drawChargeCrackle(sx, sy, s, 0.55f, 0.42f, 0.28f, p, sb, phase, proj.chargeLevel)
  }

  /** Mummy's bandage whip as a spinning wad of grave-wrappings with two short ends flapping
   *  behind and a curse glinting through a gap in the wrap. */
  private def drawBandageWad(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 21) * 0.3
    computeAllDynamics(proj, 0.88f, 0.82f, 0.64f, phase)
    val p = (0.9f + 0.1f * Math.sin(phase * _stPulseMult).toFloat) * dynAlpha
    val ds = Math.min(dynScale, 1.3f)
    val s = 15.5f * ds
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val px = -ndy; val py = ndx

    // Two short loose ends folding as they flap — wide and fading, cloth rather than rope
    var strip = 0
    while (strip < 2) {
      val side = if (strip == 0) 1f else -1f
      var x0 = sx - ndx * s * 0.6f + px * side * s * 0.3f
      var y0 = sy - ndy * s * 0.6f + py * side * s * 0.3f
      var j = 1
      while (j <= 4) {
        val t = j / 4f
        val fold = Math.sin(phase * 2.6 + j * 1.3 + strip * 2).toFloat * s * 0.35f * t
        val x1 = sx - ndx * s * (0.6f + t * 1.7f) + px * (side * s * (0.3f + t * 0.45f) + fold)
        val y1 = sy - ndy * s * (0.6f + t * 1.7f) + py * (side * s * (0.3f + t * 0.45f) + fold)
        val w = s * 0.5f * (1f - t * 0.3f)
        val a = 1f - t * 0.75f
        sb.strokeLine(x0, y0, x1, y1, w + 2.6f, 0.2f, 0.16f, 0.1f, 0.6f * a * p)
        sb.strokeLine(x0, y0, x1, y1, w, 0.86f, 0.80f, 0.64f, a * p)
        x0 = x1; y0 = y1
        j += 1
      }
      strip += 1
    }
    var d = 0
    while (d < 4) {
      val t = ((tick * 0.05 + d * 0.25 + proj.id * 0.13) % 1.0).toFloat
      sb.fillOval(sx - ndx * t * s * 2.4f + Math.sin(d * 2.1 + phase).toFloat * s * 0.4f,
        sy - ndy * t * s * 2.4f + t * t * 12f, 2f * ds, 1.6f * ds, 0.78f, 0.68f, 0.46f, 0.6f * (1f - t) * p, 5)
      d += 1
    }
    sb.fillOvalSoft(sx, sy, s * 2f * dynGlow, s * 1.7f * dynGlow, 0.5f, 0.9f, 0.4f, 0.18f * p, 0f, 14)
    sb.fillOval(sx, sy, s + 1.8f, s * 0.8f + 1.8f, 0.14f, 0.11f, 0.07f, 0.85f * p, 16)
    sb.fillOval(sx, sy, s, s * 0.8f, 0.88f, 0.82f, 0.64f, 0.98f * p, 16)
    // Wrap bands: chords across the bundle at a turning angle
    val rot = phase * 0.8
    val ca = Math.cos(rot).toFloat; val sa = Math.sin(rot).toFloat
    var k = 0
    while (k < 4) {
      val off = -0.66f + k * 0.44f
      val half = Math.sqrt(1.0 - off * off).toFloat * 0.92f
      val ux0 = -ca * half - sa * off; val uy0 = -sa * half + ca * off
      val ux1 = ca * half - sa * off; val uy1 = sa * half + ca * off
      sb.strokeLine(sx + ux0 * s, sy + uy0 * s * 0.8f, sx + ux1 * s, sy + uy1 * s * 0.8f, 2.2f,
        0.60f, 0.54f, 0.40f, 0.9f * p)
      k += 1
    }
    sb.fillOval(sx - s * 0.32f, sy - s * 0.3f, s * 0.3f, s * 0.18f, 1f, 1f, 0.95f, 0.45f * p, 8)
    // A curse glinting through a gap in the wrap
    val glow = 0.6f + 0.4f * Math.sin(phase * 2).toFloat
    sb.fillOval(sx - s * 0.2f, sy + s * 0.02f, s * 0.1f, s * 0.07f, 0.45f, 1f, 0.4f, 0.9f * glow * p, 6)
    sb.fillOval(sx + s * 0.16f, sy + s * 0.02f, s * 0.1f, s * 0.07f, 0.45f, 1f, 0.4f, 0.9f * glow * p, 6)
    drawChargeCrackle(sx, sy, s, 0.88f, 0.82f, 0.64f, p, sb, phase, proj.chargeLevel)
  }

  /** Chameleon tongue: a sticky club at the hitbox on a short, thick, matte root that bends
   *  once and dissolves behind it — a lash, where the whip-beam was a long shiny tube with a
   *  ball on the end. */
  private def drawTongueLash(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 25) * 0.35
    computeAllDynamics(proj, 0.92f, 0.42f, 0.48f, phase)
    val p = (0.92f + 0.08f * Math.sin(phase * _stPulseMult).toFloat) * dynAlpha
    val ds = Math.min(dynScale, 1.3f)
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val px = -ndy; val py = ndx
    val L = 22f * ds
    val bend = Math.sin(phase * 0.8).toFloat * L * 0.18f
    val segs = 5
    var pass = 0
    while (pass < 3) {
      var j = 0
      while (j < segs) {
        val t0 = j.toFloat / segs; val t1 = (j + 1).toFloat / segs
        val o0 = bend * 4f * t0 * (1f - t0); val o1 = bend * 4f * t1 * (1f - t1)
        val x0 = sx - ndx * L * t0 + px * o0; val y0 = sy - ndy * L * t0 + py * o0
        val x1 = sx - ndx * L * t1 + px * o1; val y1 = sy - ndy * L * t1 + py * o1
        val w = 12f * ds * (1f - t0 * 0.5f)
        val a = if (t0 < 0.4f) 1f else 1f - (t0 - 0.4f) / 0.6f * 0.85f
        pass match {
          case 0 => sb.strokeLine(x0, y0, x1, y1, w + 2.8f, 0.28f, 0.06f, 0.10f, 0.75f * a * p)
          case 1 => sb.strokeLine(x0, y0, x1, y1, w, 0.90f, 0.42f, 0.50f, 0.97f * a * p)
          case _ => sb.strokeLine(x0, y0, x1, y1, w * 0.18f, 0.62f, 0.20f, 0.28f, 0.8f * a * p)
        }
        j += 1
      }
      pass += 1
    }
    val cx = sx + ndx * 2f * ds; val cy = sy + ndy * 2f * ds
    sb.fillOval(cx, cy, 11.5f * ds + 1.6f, 9.5f * ds + 1.6f, 0.28f, 0.06f, 0.10f, 0.85f * p, 14)
    sb.fillOval(cx, cy, 11.5f * ds, 9.5f * ds, 0.92f, 0.44f, 0.52f, 0.98f * p, 14)
    sb.fillOval(cx + ndx * 4.5f * ds, cy + ndy * 4.5f * ds, 6f * ds, 4.6f * ds, 0.70f, 0.20f, 0.30f, 0.9f * p, 10)
    sb.fillOval(cx - 3f * ds, cy - 3f * ds, 3.4f * ds, 2.4f * ds, 1f, 1f, 1f, 0.6f * p, 8)
    // Saliva flung off the club
    var d = 0
    while (d < 4) {
      val t = ((tick * 0.06 + d * 0.25 + proj.id * 0.13) % 1.0).toFloat
      val a = Math.atan2(ndy, ndx) + Math.PI + (d - 1.5) * 0.7
      val dist = 8f * ds + t * 14f * ds
      sb.fillOval(cx + Math.cos(a).toFloat * dist, cy + Math.sin(a).toFloat * dist * 0.7f + t * t * 10f,
        2.4f * ds * (1f - t * 0.4f), 2.8f * ds * (1f - t * 0.4f), 0.88f, 0.95f, 1f, 0.75f * (1f - t) * p, 6)
      d += 1
    }
    drawChargeCrackle(sx, sy, 12f * ds, 0.92f, 0.42f, 0.48f, p, sb, phase, proj.chargeLevel)
  }

  // ═══════════════════════════════════════════════════════════════
  //  REGISTRY (all 150 types)
  // ═══════════════════════════════════════════════════════════════

  private val registry: Map[Byte, Renderer] = Map(
    // ── Original (0-30) ──
    ProjectileType.NORMAL       -> asRenderer(drawNormal),
    ProjectileType.TENTACLE     -> graspingClaw(CLAW_TENTACLE, 0.24f, 0.74f, 0.44f, 30f),
    ProjectileType.ICE_BEAM     -> (drawFrostComet _),
    ProjectileType.AXE          -> bladeSpinner(WPN_AXE, 0.78f, 0.70f, 0.55f, 30f),
    ProjectileType.ROPE         -> chainProj(CHN_ROPE, 0.72f, 0.54f, 0.30f, 6f),
    ProjectileType.SPEAR        -> flyingShaft(SHF_SPEAR, 0.95f, 0.82f, 0.42f, 28f),
    ProjectileType.SOUL_BOLT    -> energyBolt(0.3f, 0.9f, 0.4f, 20f, 3),
    ProjectileType.HAUNT        -> energyBolt(0.5f, 0.7f, 0.95f, 24f, 4),
    ProjectileType.ARCANE_BOLT  -> energyBolt(0.65f, 0.3f, 0.95f, 22f, 2),
    ProjectileType.FIREBALL     -> asRenderer(drawFireball),
    ProjectileType.SPLASH       -> aoeRing(AOE_WATER, 0.32f, 0.62f, 1f, 46f),
    ProjectileType.TIDAL_WAVE   -> wave(WAV_WATER, 0.35f, 0.66f, 1f, 40f),
    ProjectileType.GEYSER       -> asRenderer(drawGeyser),
    ProjectileType.BULLET       -> bulletProj(0.75f, 0.7f, 0.55f),
    ProjectileType.GRENADE      -> lobbed(LOB_BOMB, 0.95f, 0.62f, 0.25f, 17f),
    ProjectileType.ROCKET       -> asRenderer(drawRocket),
    ProjectileType.TALON        -> asRenderer(drawTalon),
    ProjectileType.GUST         -> wave(WAV_WIND, 0.82f, 0.92f, 1f, 33f),
    ProjectileType.SHURIKEN     -> spinner(0.72f, 0.74f, 0.82f, 30f, 4),
    ProjectileType.POISON_DART  -> flyingShaft(SHF_DART, 0.4f, 0.9f, 0.35f, 26f),
    ProjectileType.CHAIN_BOLT   -> chainProj(CHN_SHACKLE, 0.62f, 0.64f, 0.72f, 5.5f),
    ProjectileType.LOCKDOWN_CHAIN -> chainProj(CHN_LOCK, 0.52f, 0.54f, 0.62f, 6f),
    ProjectileType.SNARE_MINE   -> lobbed(LOB_MINE, 0.35f, 0.7f, 1f, 17f),
    ProjectileType.KATANA       -> bladeSpinner(WPN_KATANA, 0.78f, 0.84f, 0.95f, 32f),
    ProjectileType.SWORD_WAVE   -> asRenderer(drawSwordWave),
    ProjectileType.PLAGUE_BOLT  -> energyBolt(0.45f, 0.75f, 0.15f, 20f, 3),
    ProjectileType.MIASMA       -> aoeRing(AOE_TOXIC, 0.42f, 0.72f, 0.18f, 42f),
    ProjectileType.BLIGHT_BOMB  -> lobbed(LOB_FLASK, 0.45f, 0.8f, 0.2f, 17f),
    ProjectileType.BLOOD_FANG   -> asRenderer(drawBloodFang),
    ProjectileType.BLOOD_SIPHON -> siphonVortex(SIPH_BLOOD, 0.88f, 0.12f, 0.14f, 19f),
    ProjectileType.BAT_SWARM    -> asRenderer(drawBatSwarm),

    // ── Elemental (31-52) ──
    ProjectileType.FLAME_BOLT   -> energyBolt(1f, 0.5f, 0.1f, 20f, 1),
    ProjectileType.FROST_SHARD  -> flyingShaft(SHF_ICE, 0.55f, 0.85f, 1f, 22f),
    ProjectileType.LIGHTNING    -> lightningBolt(1f, 0.90f, 0.30f),
    ProjectileType.CHAIN_LIGHTNING -> lightningBolt(0.95f, 0.85f, 0.35f),
    ProjectileType.THUNDER_STRIKE -> asRenderer(drawThunderStrike),
    ProjectileType.BOULDER      -> asRenderer((proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int) => drawBoulder(proj, sx, sy, sb, tick, 24f)),
    ProjectileType.SEISMIC_SLAM -> aoeRing(AOE_QUAKE, 0.68f, 0.50f, 0.28f, 50f),
    ProjectileType.WIND_BLADE   -> wave(WAV_WIND, 0.78f, 0.96f, 1f, 31f),
    ProjectileType.MAGMA_BALL   -> energyBolt(1f, 0.45f, 0.05f, 24f, 1),
    ProjectileType.ERUPTION     -> aoeRing(AOE_FIRE, 1f, 0.45f, 0.05f, 46f),
    ProjectileType.FROST_TRAP   -> lobbed(LOB_ICE, 0.62f, 0.85f, 1f, 17f),
    ProjectileType.SAND_SHOT    -> energyBolt(0.9f, 0.75f, 0.4f, 18f),
    ProjectileType.SAND_BLAST   -> wave(WAV_SAND, 0.88f, 0.76f, 0.42f, 30f),
    ProjectileType.THORN        -> flyingShaft(SHF_THORN, 0.42f, 0.75f, 0.28f, 26f),
    ProjectileType.VINE_WHIP    -> graspingClaw(CLAW_VINE, 0.30f, 0.62f, 0.20f, 30f),
    ProjectileType.THORN_WALL   -> aoeRing(AOE_NATURE, 0.30f, 0.62f, 0.18f, 34f),
    ProjectileType.INFERNO_BLAST -> asRenderer(drawInfernoBlast),
    ProjectileType.GLACIER_SPIKE -> flyingShaft(SHF_ICE, 0.60f, 0.88f, 1f, 27f),
    ProjectileType.MUD_GLOB     -> energyBolt(0.4f, 0.3f, 0.15f, 22f),
    ProjectileType.MUD_BOMB     -> lobbed(LOB_GLOB, 0.40f, 0.30f, 0.14f, 18f),
    ProjectileType.EMBER_SHOT   -> energyBolt(1f, 0.55f, 0.15f, 18f, 1),
    ProjectileType.AVALANCHE_CRUSH -> lobbed(LOB_ICE, 0.72f, 0.85f, 0.98f, 40f),

    // ── Undead/Dark (53-69) ──
    ProjectileType.DEATH_BOLT   -> energyBolt(0.2f, 0.5f, 0.05f, 22f, 3),
    ProjectileType.RAISE_DEAD   -> asRenderer(drawRaiseDead),
    ProjectileType.BONE_AXE     -> bladeSpinner(WPN_BONE_AXE, 0.94f, 0.92f, 0.84f, 30f),
    ProjectileType.BONE_THROW   -> bladeSpinner(WPN_BONE, 0.94f, 0.92f, 0.84f, 24f, 0.38),
    ProjectileType.WAIL         -> asRenderer(drawWail),
    ProjectileType.SOUL_DRAIN   -> siphonVortex(SIPH_SOUL, 0.40f, 0.95f, 0.55f, 19f),
    ProjectileType.CLAW_SWIPE   -> asRenderer(drawClawSwipe),
    ProjectileType.DEVOUR       -> asRenderer(drawDevour),
    ProjectileType.SCYTHE       -> asRenderer(drawScythe),
    ProjectileType.REAP         -> asRenderer(drawReap),
    ProjectileType.SHADOW_BOLT  -> asRenderer(drawShadowBolt),
    ProjectileType.CURSED_BLADE -> bladeSpinner(WPN_CURSED, 0.90f, 0.18f, 0.28f, 30f),
    ProjectileType.LIFE_DRAIN   -> siphonVortex(SIPH_LIFE, 0.80f, 0.14f, 0.30f, 19f),
    ProjectileType.SHOVEL       -> lobbed(LOB_SHOVEL, 0.62f, 0.64f, 0.62f, 26f),
    ProjectileType.HEAD_THROW   -> lobbed(LOB_HORN, 0.86f, 0.80f, 0.68f, 22f),
    ProjectileType.BANDAGE_WHIP -> (drawBandageWad _),
    ProjectileType.CURSE        -> asRenderer(drawCurse),

    // ── Medieval/Fantasy (70-79) ──
    ProjectileType.HOLY_BLADE   -> bladeSpinner(WPN_SWORD, 1f, 0.92f, 0.45f, 31f),
    ProjectileType.HOLY_BOLT    -> asRenderer(drawHolyBolt),
    ProjectileType.ARROW        -> flyingShaft(SHF_ARROW, 0.95f, 0.72f, 0.32f, 25f),
    ProjectileType.POISON_ARROW -> flyingShaft(SHF_PARROW, 0.4f, 0.9f, 0.32f, 25f),
    ProjectileType.SONIC_WAVE   -> wave(WAV_SONIC, 0.78f, 0.58f, 0.97f, 33f),
    ProjectileType.SONIC_BOOM   -> aoeRing(AOE_SONIC, 0.78f, 0.58f, 0.97f, 50f),
    ProjectileType.FIST         -> fistProj(0.75f, 0.6f, 0.45f, 28f),
    ProjectileType.SMITE        -> asRenderer(drawHolyBolt),
    ProjectileType.CHARM        -> energyBolt(1f, 0.45f, 0.65f, 18f, 4),
    ProjectileType.CARD         -> bladeSpinner(WPN_CARD, 0.92f, 0.25f, 0.32f, 26f, 0.22),

    // ── Sci-Fi/Tech (80-89) ──
    ProjectileType.DATA_BOLT    -> asRenderer((proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int) => drawDataBolt(proj, sx, sy, sb, tick, isVirus = false)),
    ProjectileType.VIRUS        -> asRenderer((proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int) => drawDataBolt(proj, sx, sy, sb, tick, isVirus = true)),
    ProjectileType.LASER        -> laserBolt(BOLT_LASER, 1f, 0.25f, 0.2f, 40f, 9f),
    ProjectileType.GRAVITY_BALL -> asRenderer(drawGravityBall),
    ProjectileType.GRAVITY_WELL -> asRenderer(drawGravityBall),
    ProjectileType.TESLA_COIL   -> lightningBolt(0.35f, 0.85f, 1f),
    ProjectileType.NANO_BOLT    -> energyBolt(0.25f, 0.85f, 0.65f, 16f, 4),
    ProjectileType.VOID_BOLT    -> asRenderer(drawVoidBolt),
    ProjectileType.RAILGUN      -> railSlug(0.35f, 0.65f, 1f, 14f),
    ProjectileType.CLUSTER_BOMB -> lobbed(LOB_BOMB, 0.9f, 0.66f, 0.3f, 19f),

    // ── Nature/Beast (90-93) ──
    ProjectileType.VENOM_BOLT   -> asRenderer(drawVenomBolt),
    ProjectileType.WEB_SHOT     -> asRenderer(drawWebShot),
    ProjectileType.STINGER      -> asRenderer(drawStinger),
    ProjectileType.ACID_BOMB    -> asRenderer(drawVenomBolt),

    // ── AoE Root (94-101) ──
    ProjectileType.SEISMIC_ROOT -> aoeRing(AOE_QUAKE, 0.62f, 0.48f, 0.28f, 38f),
    ProjectileType.ROOT_GROWTH  -> aoeRing(AOE_NATURE, 0.30f, 0.66f, 0.18f, 36f),
    ProjectileType.WEB_TRAP     -> asRenderer(drawWebShot),
    ProjectileType.TREMOR_SLAM  -> aoeRing(AOE_QUAKE, 0.62f, 0.48f, 0.28f, 46f),
    ProjectileType.ENTANGLE     -> aoeRing(AOE_NATURE, 0.26f, 0.60f, 0.18f, 36f),
    ProjectileType.STONE_GAZE   -> gorgonEye(petrify = false),
    ProjectileType.INK_SNARE    -> lobbed(LOB_GLOB, 0.16f, 0.14f, 0.24f, 19f),
    ProjectileType.GRAVITY_LOCK -> asRenderer(drawGravityBall),

    // ── Character-specific (102-111) ──
    ProjectileType.KNIFE        -> bladeSpinner(WPN_KNIFE, 0.84f, 0.86f, 0.92f, 25f, 0.36),
    ProjectileType.STING        -> energyBolt(0.45f, 0.9f, 1f, 16f),
    ProjectileType.HAMMER       -> lobbed(LOB_HAMMER, 0.60f, 0.62f, 0.70f, 27f),
    ProjectileType.HORN         -> asRenderer(drawHorn),
    ProjectileType.MYSTIC_BOLT  -> energyBolt(0.55f, 0.35f, 0.85f, 20f, 2),
    ProjectileType.PETRIFY      -> gorgonEye(petrify = true),
    ProjectileType.GRAB         -> (drawGrabPaw _),
    ProjectileType.JAW          -> asRenderer(drawJaw),
    ProjectileType.TONGUE       -> (drawTongueLash _),
    ProjectileType.ACID_FLASK   -> asRenderer(drawVenomBolt),

    // ── Roster audit: new differentiation projectiles (112+) ──
    ProjectileType.BOOMERANG_BLADE -> bladeSpinner(WPN_CURSED, 0.90f, 0.18f, 0.28f, 30f),
    ProjectileType.VORTEX_BOMB     -> aoeRing(AOE_VOID, 0.55f, 0.25f, 0.85f, 42f),
    ProjectileType.CHAIN_LIGHTNING_FORK -> lightningBolt(1f, 0.92f, 0.42f),
    ProjectileType.SNIPER_BEAM     -> railSlug(0.40f, 0.70f, 1f, 11f),
    ProjectileType.MOMENTUM_STRIKE -> wave(WAV_IMPACT, 0.97f, 0.62f, 0.22f, 30f),
    ProjectileType.LEECH_BOLT      -> energyBolt(0.5f, 0.15f, 0.35f, 22f, 3),
    ProjectileType.RICOCHET_SHARD  -> flyingShaft(SHF_ICE, 0.62f, 0.90f, 1f, 21f),
    ProjectileType.FLAME_WAVE      -> wave(WAV_FLAME, 1f, 0.5f, 0.1f, 35f),
    ProjectileType.POISON_CLOUD    -> aoeRing(AOE_TOXIC, 0.36f, 0.80f, 0.24f, 46f),
    ProjectileType.BONE_BOOMERANG  -> bladeSpinner(WPN_BONE, 0.94f, 0.92f, 0.84f, 24f, 0.34),
    ProjectileType.GRAVITY_LANCE   -> flyingShaft(SHF_LANCE, 0.55f, 0.25f, 0.85f, 26f),
    ProjectileType.SHADOW_HAUNT    -> asRenderer(drawVoidBolt),
    ProjectileType.CHARGE_FIST     -> fistProj(0.85f, 0.65f, 0.35f, 28f),
    ProjectileType.ACID_SPRAY      -> wave(WAV_ACID, 0.35f, 0.9f, 0.32f, 29f),
    ProjectileType.ECHO_BOLT       -> energyBolt(0.7f, 0.5f, 0.95f, 20f, 2),
    ProjectileType.FLAME_TRAIL     -> energyBolt(1f, 0.6f, 0.15f, 22f, 1),
    ProjectileType.STAR_BOLT       -> energyBolt(0.95f, 0.9f, 0.45f, 22f, 4),
    ProjectileType.RUNE_BOLT       -> energyBolt(0.9f, 0.75f, 0.35f, 18f, 2),
    ProjectileType.SOUL_HARVEST    -> aoeRing(AOE_VOID, 0.34f, 0.90f, 0.40f, 42f),
    ProjectileType.OVERCLOCK_BEAM  -> aoeRing(AOE_SONIC, 0.22f, 0.95f, 0.75f, 40f),
    ProjectileType.NAPALM_STRIKE   -> lobbed(LOB_BOMB, 1f, 0.5f, 0.12f, 20f),
    ProjectileType.THROWN_BOULDER  -> asRenderer((proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int) => drawBoulder(proj, sx, sy, sb, tick, 28f)),
    ProjectileType.EYE_BEAM        -> laserBolt(BOLT_EYE, 1f, 0.35f, 0.15f, 42f, 11f),

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
    ProjectileType.LASER_HEAVY       -> laserBolt(BOLT_PRISM, 1f, 0.9f, 0.55f, 44f, 10f),
    ProjectileType.LASER_LIGHT       -> laserBolt(BOLT_LASER, 0.3f, 0.95f, 0.9f, 34f, 7.5f),
    ProjectileType.ARROW_HEAVY       -> flyingShaft(SHF_ARROW, 0.95f, 0.72f, 0.32f, 27f),
    ProjectileType.ARROW_LIGHT       -> flyingShaft(SHF_ARROW, 0.95f, 0.72f, 0.32f, 24f),
    ProjectileType.HOLY_BOLT_HEAVY   -> asRenderer(drawHolyBolt),
    ProjectileType.VENOM_BOLT_LIGHT  -> asRenderer(drawVenomBolt)
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

  /** Spaceman's charge shot: a plasma orb in the player's colour with a comet tail of
   *  separate plasma blobs, and energy rings that spin around it. It used to be a wobbling
   *  13px tube running seven world units ahead of the hitbox to an orb — the roster's most
   *  visible snake, since every Spaceman fires it constantly. */
  private def drawNormal(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 37) * 0.3
    intToRGB(proj.colorRGB)
    val r = _r; val g = _g; val b = _b
    computeAllDynamics(proj, r, g, b, phase)
    val p = (0.85f + 0.15f * Math.sin(phase * 2 * _stPulseMult).toFloat) * dynAlpha
    val dr = _evoR; val dg = _evoG; val db = _evoB
    val ds = dynScale
    val R = 10.5f * ds
    val hw = 0.5f + _chgBright
    val cr = mix(bright(r), 1f, hw); val cg = mix(bright(g), 1f, hw); val cb = mix(bright(b), 1f, hw)
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val px = -ndy; val py = ndx

    var i = 4
    while (i >= 1) {
      val t = i / 5f
      val wob = Math.sin(phase * 2.2 + i * 1.4).toFloat * R * 0.22f * t
      val bx = sx - ndx * R * 1.25f * i + px * wob; val by = sy - ndy * R * 1.25f * i + py * wob
      val br = R * (0.9f - t * 0.55f)
      sb.fillOvalSoft(bx, by, br * 1.7f, br * 1.5f, dr, dg, db, 0.3f * (1f - t) * p, 0f, 10)
      sb.fillOval(bx, by, br * 0.7f, br * 0.62f, cr, cg, cb, 0.6f * (1f - t) * p, 10)
      i -= 1
    }
    sb.fillOvalSoft(sx, sy, R * 2.8f * dynGlow, R * 2.4f * dynGlow, dr, dg, db, 0.38f * p, 0f, 16)
    val ringA = (phase * 1.9).toFloat
    sb.strokeArc(sx, sy, R * 1.55f, R * 1.55f * ISO_Y, ringA, 2.4f, 2.2f, cr, cg, cb, 0.75f * p, 10)
    sb.strokeArc(sx, sy, R * 1.55f, R * 1.55f * ISO_Y, ringA + 3.1416f, 2.4f, 2.2f, cr, cg, cb, 0.75f * p, 10)
    sb.strokeArc(sx, sy, R * 1.2f * ISO_Y, R * 1.35f, -ringA * 0.8f, 2.0f, 1.6f, cr, cg, cb, 0.55f * p, 10)
    sb.fillOval(sx, sy, R + 1.6f, R * 0.92f + 1.6f, outline(r), outline(g), outline(b), 0.75f * p, 16)
    sb.fillOval(sx, sy, R, R * 0.92f, dr, dg, db, 0.96f * p, 16)
    sb.fillOval(sx + ndx * R * 0.12f, sy + ndy * R * 0.12f, R * 0.6f, R * 0.54f, cr, cg, cb, 0.95f * p, 12)
    sb.fillOval(sx + KEY_LIGHT_X * R * 0.38f, sy + KEY_LIGHT_Y * R * 0.36f, R * 0.26f, R * 0.2f, 1f, 1f, 1f, 0.6f * p, 8)
    drawChargeCrackle(sx, sy, R * 1.8f, r, g, b, p, sb, phase, proj.chargeLevel)
    drawReturnGhosts(sx, sy, R, dr, dg, db, p, sb, proj)
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

  /** Rocket: nose on the hitbox, body, fins and exhaust trailing behind it. It used to start
   *  at the hitbox and extend five world units forward, so the warhead arrived long before
   *  the explosion did. */
  private def drawRocket(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 29) * 0.35
    computeAllDynamics(proj, 0.45f, 0.47f, 0.35f, phase)
    val p = (0.8f + 0.2f * Math.sin(phase * _stPulseMult).toFloat) * dynAlpha
    val ds = Math.min(dynScale, 1.3f)
    screenDir(proj)
    val nx = _sdx; val ny = _sdy
    val perpX = -ny; val perpY = nx
    val noseLen = 16f * ds; val bodyLen = 40f * ds
    val baseX = sx - nx * noseLen; val baseY = sy - ny * noseLen
    val tailX = baseX - nx * bodyLen; val tailY = baseY - ny * bodyLen
    val dx = baseX - tailX; val dy = baseY - tailY

    sb.fillOvalSoft(tailX - nx * 8f * ds, tailY - ny * 8f * ds, 24f * ds * dynGlow, 19f * ds * dynGlow,
      1f, 0.5f, 0.1f, 0.4f * p, 0f, 14)
    var i = 0
    while (i < 10) {
      val t = ((tick * 0.09 + i * 0.1 + proj.id * 0.07) % 1.0).toFloat
      val fx = tailX - nx * t * 34f * ds + perpX * Math.sin(phase * 3 + i * 1.5).toFloat * (4f + t * 16f) * 0.35f
      val fy = tailY - ny * t * 34f * ds + perpY * Math.sin(phase * 3 + i * 1.5).toFloat * (4f + t * 16f) * 0.35f
      val green = Math.max(0f, 0.9f - t * 0.5f)
      sb.fillOval(fx, fy, 5f + t * 6f, 4f + t * 5f, 1f, green, Math.max(0f, 0.3f - t * 0.2f), 0.65f * (1f - t), 8)
      i += 1
    }
    i = 0
    while (i < 6) {
      val t = ((tick * 0.04 + i * 0.167 + proj.id * 0.09) % 1.0).toFloat
      val smX = tailX - nx * t * 50f * ds + perpX * Math.sin(phase * 0.8 + i * 1.3).toFloat * (6f + t * 12f)
      val smY = tailY - ny * t * 50f * ds + perpY * Math.sin(phase * 0.8 + i * 1.3).toFloat * (6f + t * 12f) - t * 12f
      val gray = 0.6f + t * 0.1f
      val puff = 6f + t * 10f
      sb.strokeOval(smX, smY, puff + 1f, (puff + 1f) * 0.85f, 1.5f, 0.3f, 0.3f, 0.3f, 0.2f * (1f - t), 10)
      sb.fillOval(smX, smY, puff, puff * 0.85f, gray, gray, gray, 0.35f * (1f - t), 10)
      i += 1
    }
    sb.strokeLine(tailX, tailY, baseX, baseY, 16f * ds, 0.1f, 0.1f, 0.08f, 0.85f * p)
    sb.strokeLine(tailX, tailY, baseX, baseY, 13f * ds, 0.4f, 0.42f, 0.3f, 0.95f * p)
    sb.strokeLine(tailX, tailY, baseX, baseY, 6f * ds, 0.55f, 0.58f, 0.45f, 0.65f * p)
    val bandX = tailX + dx * 0.55f; val bandY = tailY + dy * 0.55f
    sb.strokeLine(bandX - dx * 0.07f, bandY - dy * 0.07f, bandX + dx * 0.07f, bandY + dy * 0.07f,
      16f * ds, 0.15f, 0.02f, 0.01f, 0.7f * p)
    sb.strokeLine(bandX - dx * 0.07f, bandY - dy * 0.07f, bandX + dx * 0.07f, bandY + dy * 0.07f,
      14f * ds, 0.95f, 0.2f, 0.1f, 0.85f * p)
    var f = -1
    while (f <= 1) {
      _polyXs3(0) = tailX + nx * 8f * ds; _polyYs3(0) = tailY + ny * 8f * ds
      _polyXs3(1) = tailX - nx * 4f * ds + perpX * f * 11f * ds; _polyYs3(1) = tailY - ny * 4f * ds + perpY * f * 11f * ds
      _polyXs3(2) = tailX - nx * 10f * ds; _polyYs3(2) = tailY - ny * 10f * ds
      sb.fillPolygon(_polyXs3, _polyYs3, 3, 0.45f, 0.48f, 0.35f, 0.9f * p)
      sb.strokePolygon(_polyXs3, _polyYs3, 3, 2f, 0.15f, 0.15f, 0.12f, 0.7f * p)
      f += 2
    }
    _polyXs3(0) = sx; _polyXs3(1) = baseX + perpX * 8f * ds; _polyXs3(2) = baseX - perpX * 8f * ds
    _polyYs3(0) = sy; _polyYs3(1) = baseY + perpY * 8f * ds; _polyYs3(2) = baseY - perpY * 8f * ds
    sb.fillPolygon(_polyXs3, _polyYs3, 3, 0.7f, 0.7f, 0.65f, 0.95f * p)
    sb.strokePolygon(_polyXs3, _polyYs3, 3, 2.5f, 0.15f, 0.15f, 0.12f, 0.8f * p)
    sb.fillOval(baseX + nx * 6f * ds + KEY_LIGHT_X * 3f * ds, baseY + ny * 6f * ds + KEY_LIGHT_Y * 3f * ds,
      4f * ds, 3f * ds, 1f, 1f, 1f, 0.35f * p, 6)
    drawChargeCrackle(sx, sy, 16f * ds, 0.9f, 0.5f, 0.2f, p, sb, phase, proj.chargeLevel)
  }

  /** Lightning — CARTOONISH: thick bold bolt with dark outline, huge branches, sparkle stars */
  private def lightningBolt(r: Float, g: Float, b: Float): Renderer =
    (proj, sx, sy, sb, tick) => drawLightning(proj, sx, sy, sb, tick, r, g, b)

  /** Lightning: a crackling head at the hitbox with the jagged stroke it just drew trailing
   *  behind and fading, forks striking ahead. The bolt used to run six world units OUT IN
   *  FRONT of the hitbox and end in a ringed disc — a zigzag stick with a ball on it. */
  private def drawLightning(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int,
                            lr: Float, lg: Float, lb: Float): Unit = {
    val oR = lr * 0.18f; val oG = lg * 0.18f; val oB = lb * 0.22f + 0.06f
    val hR = mix(lr, 1f, 0.55f); val hG = mix(lg, 1f, 0.55f); val hB = mix(lb, 1f, 0.55f)
    val phase = (tick + proj.id * 41) * 0.5
    computeAllDynamics(proj, lr, lg, lb, phase)
    val flicker = (0.62 + 0.38 * Math.sin(phase * 8)).toFloat * dynAlpha
    val ds = Math.min(dynScale, 1.35f)
    val bc = _chgBright
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val nx = -ndy; val ny = ndx
    val L = 58f * ds
    val tailX = sx - ndx * L; val tailY = sy - ndy * L
    val regenSeed = (tick / 3) * 7 + proj.id * 41
    val segs = 8
    _boltXs(0) = tailX; _boltYs(0) = tailY; _boltXs(segs) = sx; _boltYs(segs) = sy
    var i = 1
    while (i < segs) {
      val t = i.toFloat / segs
      val jitter = (Math.sin(regenSeed * 0.9 + i * 2.7).toFloat * 12f +
        Math.cos(regenSeed * 1.3 + i * 3.9).toFloat * 5f) * ds
      _boltXs(i) = tailX + (sx - tailX) * t + nx * jitter
      _boltYs(i) = tailY + (sy - tailY) * t + ny * jitter
      i += 1
    }
    // Contour, body and core — each thinning and fading toward the tail
    var pass = 0
    while (pass < 3) {
      i = 0
      while (i < segs) {
        val k = 0.15f + 0.85f * (i + 1f) / segs
        pass match {
          case 0 => sb.strokeLine(_boltXs(i), _boltYs(i), _boltXs(i + 1), _boltYs(i + 1),
            13f * ds * (0.45f + 0.55f * k), oR, oG, oB, 0.7f * flicker * k)
          case 1 => sb.strokeLine(_boltXs(i), _boltYs(i), _boltXs(i + 1), _boltYs(i + 1),
            9.5f * ds * (0.4f + 0.6f * k), lr, lg, lb, 0.92f * flicker * k)
          case _ => sb.strokeLine(_boltXs(i), _boltYs(i), _boltXs(i + 1), _boltYs(i + 1),
            4.4f * ds * (0.35f + 0.65f * k), mix(hR, 1f, 0.6f + bc * 0.4f), mix(hG, 1f, 0.6f + bc * 0.4f),
            mix(hB, 1f, 0.5f + bc * 0.5f), 0.98f * flicker * k)
        }
        i += 1
      }
      pass += 1
    }
    // Forks striking out ahead of the head
    var f = 0
    while (f < 3) {
      val fa = Math.atan2(ndy, ndx) + (f - 1) * 0.8 + Math.sin(regenSeed * 0.7 + f * 2.3) * 0.25
      val fl = (15f + Math.sin(regenSeed * 0.5 + f * 2.1).toFloat * 5f) * ds
      val kink = Math.sin(regenSeed * 1.1 + f * 1.7).toFloat * 5f * ds
      val mx = sx + Math.cos(fa).toFloat * fl * 0.5f + nx * kink
      val my = sy + Math.sin(fa).toFloat * fl * 0.5f + ny * kink
      val ex = sx + Math.cos(fa).toFloat * fl; val ey = sy + Math.sin(fa).toFloat * fl
      sb.strokeLine(sx, sy, mx, my, 5.5f * ds, oR, oG, oB, 0.4f * flicker)
      sb.strokeLine(mx, my, ex, ey, 4f * ds, oR, oG, oB, 0.3f * flicker)
      sb.strokeLine(sx, sy, mx, my, 3.2f * ds, lr, lg, lb, 0.75f * flicker)
      sb.strokeLine(mx, my, ex, ey, 2f * ds, hR, hG, hB, 0.6f * flicker)
      f += 1
    }
    i = 0
    while (i < 6) {
      val sp = 1 + (i * 2) % segs
      val so = Math.sin(phase * 6 + i * 3.7).toFloat * 11f * ds
      val sa = (0.3 + 0.45 * Math.sin(phase * 9 + i * 2.9)).toFloat * flicker * (0.3f + 0.7f * sp / segs)
      sb.fillOval(_boltXs(sp) + nx * so, _boltYs(sp) + ny * so, 3.6f * ds, 2.8f * ds, hR, hG, hB, sa, 8)
      i += 1
    }
    // The head: a crackling ball of light — glow and a star, no ringed disc
    val tipPulse = (0.7 + 0.3 * Math.sin(phase * 4)).toFloat
    sb.fillOvalSoft(sx, sy, 22f * ds * dynGlow * tipPulse, 18f * ds * dynGlow * tipPulse, lr, lg, lb, 0.42f * flicker, 0f, 12)
    sb.fillOvalSoft(sx, sy, 8.5f * ds, 7.5f * ds, hR, hG, hB, 0.95f * flicker, 0.2f * flicker, 10)
    sb.fillStarFlare(sx, sy, 17f * ds * tipPulse, 2.6f, (phase * 0.9).toFloat, 0.6f, 1f, 1f, 1f, 0.8f * flicker)
    drawReturnGhosts(sx, sy, 12f * ds, lr, lg, lb, flicker, sb, proj)
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
    // The jaws are the hitbox: anchored on the projectile with the wake trailing behind.
    // They used to sit seven world units ahead of it and bite before the damage landed.
    screenDir(proj)
    val jawNx = _sdx; val jawNy = _sdy
    val tipX = sx; val tipY = sy
    val phase = (tick + proj.id * 17) * 0.4
    computeAllDynamics(proj, 0.42f, 0.47f, 0.55f, phase)
    val p = (0.9 + 0.1 * Math.sin(phase * _stPulseMult)).toFloat * dynAlpha
    val ds = dynScale * 1.3f // this one reads far below the roster's scale unscaled
    val chompCycle = Math.sin(phase * 3).toFloat
    val chomp = (Math.abs(chompCycle) * 0.45f + 0.55f).toFloat
    val nx = jawNx; val ny = jawNy
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
