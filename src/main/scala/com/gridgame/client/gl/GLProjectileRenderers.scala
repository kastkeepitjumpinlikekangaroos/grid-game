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
    val p = (0.9 + 0.1 * Math.sin(phase * _stPulseMult)).toFloat * dynAlpha
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
    // Hamon: the temper line that makes a katana read as a katana and not a steel bar.
    // A wedge along the spine rather than a run out and back — the doubled-back outline
    // was non-convex, and at two pixels wide what it fanned into was not a temper line.
    part(Array(-0.16f,-0.05f, 0.98f,-0.09f, 1.16f,-0.03f, -0.16f,-0.01f), 1f, 1f, 1f, 0.06f)
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
    // The blade's back edge bows just outside the chord, so the span stays convex: bowed
    // the other way it was a notch, and fanning it cut the blade's own middle out.
    part(Array(0.34f,-0.30f, 0.80f,-0.18f, 1.30f,-0.02f, 0.36f,0.12f), 0.22f, 0.08f, 0.13f, 0.45f),
    // Glowing edge — this is the part that carries the character's colour
    part(Array(0.30f,-0.24f, 0.80f,-0.14f, 1.24f,-0.02f, 0.32f,-0.16f), 1f, 0.55f, 0.55f, 0.75f)
  )
  private val CARD_PARTS = Array(
    part(Array(-0.72f,-0.50f, 0.72f,-0.50f, 0.72f,0.50f, -0.72f,0.50f), 0.97f, 0.97f, 0.99f, 0.06f)
  )
  /** Four-bladed throwing star: one swept blade, stamped at exact quarter turns. A star is
   *  the one silhouette a polar radius per vertex really does describe — but it is also
   *  non-convex, so the old `spinner` handed `fillPolygon` a shape it fans into a blob with
   *  two of the notches bridged over. Four convex blades stay exact at every spin angle. */
  private val SHURIKEN_PARTS: Array[Part] = Array.tabulate(4) { q =>
    val blade = Array(0.16f,-0.34f, 0.74f,-0.30f, 1.30f,-0.02f, 0.60f,0.26f, 0.14f,0.30f)
    val pts = new Array[Float](blade.length)
    var i = 0
    while (i < blade.length / 2) {
      val x = blade(i * 2); val y = blade(i * 2 + 1)
      pts(i * 2)     = q match { case 0 => x; case 1 => -y; case 2 => -x; case _ => y }
      pts(i * 2 + 1) = q match { case 0 => y; case 1 => x;  case 2 => -y; case _ => -x }
      i += 1
    }
    part(pts, STEEL_R, STEEL_G, STEEL_B, 0.26f)
  }

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

  // ── energyBolt styles ──
  // Constants, not literals, because `registry` is a val built in textual order: a style
  // id declared below it reads 0 and silently renders the wrong shape.
  private val ORB_PLAIN  = 0   // the bare orb: a lit sphere and its comet tail
  private val ORB_FIRE   = 1   // flames swept back off a burning core
  private val ORB_RUNE   = 2   // a magic circle turning round it in the ground plane
  private val ORB_ORBIT  = 3   // orb inside a tilted orbit
  private val ORB_HEART  = 4   // a beating heart (charms)
  private val ORB_ASTRAL = 5   // star with a corona and an orbit of smaller stars
  private val ORB_SPIRIT = 6   // a soul: wisps streaming off it, a skull's sockets in it
  private val ORB_TOXIC  = 7   // bubbling, dripping plague
  private val ORB_SWARM  = 8   // a cloud of nanites round a small core
  private val ORB_SAND   = 9   // a ball of whirling sand shedding grains
  private val ORB_MUD    = 10  // a wet glob of mud flinging drops, with no glow at all
  private val ORB_SHOCK  = 11  // a plasma ball crackling with arcs

  /**
   * Half of a ring orbiting an orb, plus the motes riding that half. `front` picks the half
   * nearer the camera, which the caller draws *over* the body while the other half goes
   * behind it — the orb passing through the ring is the whole reason this reads as an orbit
   * and not as a halo painted around the outside.
   *
   * The ring's major axis is screen-horizontal at every heading. Turning it onto the travel
   * vector would make "front" swing to whichever side of the ellipse currently has the
   * larger screen y, and the ring would flip through the orb every time the shot changed
   * direction.
   */
  private def orbitHalf(sb: ShapeBatch, sx: Float, sy: Float, a: Float, b: Float,
                        spin: Double, front: Boolean, motes: Int, star: Boolean,
                        r: Float, g: Float, bl: Float, alpha: Float): Unit = {
    if (alpha <= 0.01f) return
    val base = if (front) 0f else Math.PI.toFloat
    val br = bright(r); val bg = bright(g); val bb = bright(bl)
    // Three passes: a wide dim glow, an ink line, then the lit edge on top. The ink is what
    // keeps the ring visible where it crosses the orb — bright() of an already pale colour
    // is near white, and a white ring over a white orb is not a ring.
    sb.strokeArc(sx, sy, a, b, base, Math.PI.toFloat, 5.5f, r, g, bl, 0.20f * alpha, 10)
    sb.strokeArc(sx, sy, a, b, base, Math.PI.toFloat, 3.4f, outline(r), outline(g), outline(bl), 0.62f * alpha, 10)
    sb.strokeArc(sx, sy, a, b, base, Math.PI.toFloat, 1.9f, br, bg, bb, 0.85f * alpha, 10)
    var i = 0
    while (i < motes) {
      val t = spin + i * (Math.PI * 2 / motes)
      val st = Math.sin(t).toFloat
      if ((st >= 0f) == front) {
        val ct = Math.cos(t).toFloat
        val mx = sx + a * ct; val my = sy + b * st
        // Streak along the orbit behind the mote, not out on a radius: a radial spike
        // reads as something stuck to the orb, a tangential one as something going round.
        val t2 = t - 0.62
        fadeLine(sb, mx, my, sx + a * Math.cos(t2).toFloat, sy + b * Math.sin(t2).toFloat,
          3.4f, br, bg, bb, 0.55f * alpha, 3)
        if (star) drawSparkleStar(mx, my, 5.4f, 1f, 1f, 1f, 0.9f * alpha, sb, spin * 1.7 + i)
        else {
          sb.fillOval(mx, my, 3.8f, 3.4f, br, bg, bb, 0.92f * alpha, 8)
          sb.fillOval(mx, my, 1.8f, 1.6f, 1f, 1f, 1f, 0.85f * alpha, 6)
        }
      }
      i += 1
    }
  }

  // ── Shared pieces of the orbs and the electricity ──

  /** A deterministic hash of two ints to [-1, 1]. The same inputs give the same kink on every
   *  frame, which is what lets a bolt's channel hold its shape while its head flies on. */
  @inline private def hash2(a: Int, b: Int): Float = {
    var h = a * 0x27D4EB2D + b * 0x165667B1
    h = (h ^ (h >>> 15)) * 0x2C1B3C6D
    h = (h ^ (h >>> 12)) * 0x297A2D39
    h ^= h >>> 15
    (h & 0xFFFF) / 32767.5f - 1f
  }

  // Per-point scratch for strokePolylineVar: positions, widths, alphas, and a parameter along
  // the line. One primitive at a time uses them; the batch has read them when it returns.
  private val _pvX = new Array[Float](48)
  private val _pvY = new Array[Float](48)
  private val _pvW = new Array[Float](48)
  private val _pvA = new Array[Float](48)
  private val _pvT = new Array[Float](48)

  /**
   * A little arc of electricity from (x0, y0) to (x1, y1): `segs` kinks, each pushed aside by
   * up to `amp`, the same kinks for the same seed. Ink, colour and a white core, one mitred
   * stroke each, thinning toward the far end.
   */
  private def sparkArc(sb: ShapeBatch, x0: Float, y0: Float, x1: Float, y1: Float, segs: Int,
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

  // A comet tail's four points, as fractions of its length, and its width and alpha at each.
  // Widths are per point, so where the points sit shapes the taper: nearly the orb's own width
  // where it leaves the orb (a teardrop, not a stalk), then a long thin wisp.
  private val TAIL_T = Array(0f, 0.36f, 0.66f, 1f)
  private val TAIL_W = Array(1f, 0.84f, 0.42f, 0.02f)
  private val TAIL_A = Array(1f, 0.78f, 0.36f, 0f)

  /**
   * A comet tail `len` long streaming back along (-ndx, -ndy) from (sx, sy), `w0` wide at its
   * root. Straight, and made of light: a tail swung from side to side behind a round head is a
   * tadpole swimming (see ORB_SPIRIT's history), and a narrow opaque one is a stalk.
   */
  private def cometTail(sb: ShapeBatch, sx: Float, sy: Float, ndx: Float, ndy: Float,
                        len: Float, w0: Float, r: Float, g: Float, b: Float, a0: Float): Unit = {
    if (a0 <= 0.01f || len < 2f) return
    var i = 0
    while (i < 4) {
      val t = TAIL_T(i)
      _pvX(i) = sx - ndx * len * t; _pvY(i) = sy - ndy * len * t
      _pvW(i) = w0 * TAIL_W(i); _pvA(i) = a0 * TAIL_A(i)
      i += 1
    }
    sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, 4, r, g, b)
  }

  /**
   * The body every energy orb shares: an inked rim, the colour at full strength at the edge
   * lightening toward a hot core that breathes (`hot`), and a small glint. It glows from inside.
   * The first pass of this was lit from outside — a hard specular spot and a rim light on the
   * shadow side — and every orb came out a glass marble; with a skull's sockets in it, the soul
   * bolt's rim light was a smile. `ghost` thins the body for something that isn't quite there.
   */
  private def orbBody(sb: ShapeBatch, sx: Float, sy: Float, rx: Float, ry: Float,
                      r: Float, g: Float, b: Float, p: Float, hot: Float, ghost: Float = 1f): Unit = {
    val lr = bright(r); val lg = bright(g); val lb = bright(b)
    sb.fillOval(sx, sy, rx + 1.9f, ry + 1.9f, outline(r), outline(g), outline(b), 0.9f * p * ghost, 20)
    sb.fillOval(sx, sy, rx, ry, r * 0.84f, g * 0.84f, b * 0.84f, 0.97f * p * ghost, 20)
    sb.fillOvalSoft(sx + KEY_LIGHT_X * rx * 0.1f, sy + KEY_LIGHT_Y * ry * 0.1f, rx * 0.92f, ry * 0.92f,
      mix(r, lr, 0.6f), mix(g, lg, 0.6f), mix(b, lb, 0.6f), 0.95f * p, 0f, 20)
    val hk = Math.min(1f, 0.6f + 0.4f * hot)
    sb.fillOvalSoft(sx, sy, rx * 0.5f * hot, ry * 0.5f * hot, mix(lr, 1f, 0.78f), mix(lg, 1f, 0.78f),
      mix(lb, 1f, 0.78f), hk * p, 0.2f * hk * p, 14)
    sb.fillOval(sx + KEY_LIGHT_X * rx * 0.52f, sy + KEY_LIGHT_Y * ry * 0.54f, rx * 0.13f, ry * 0.1f,
      1f, 1f, 1f, 0.6f * p, 8)
  }

  /** Bright specks shed off the back of an orb and left behind, fading. */
  private def shedMotes(sb: ShapeBatch, sx: Float, sy: Float, ndx: Float, ndy: Float, rad: Float,
                        n: Int, r: Float, g: Float, b: Float, p: Float, tick: Int, id: Int): Unit = {
    val px = -ndy; val py = ndx
    var i = 0
    while (i < n) {
      val t = ((tick * 0.045 + i.toFloat / n + id * 0.37) % 1.0).toFloat
      val side = if ((i & 1) == 0) 1f else -1f
      val back = rad * (0.8f + t * 2.6f)
      val lat = side * rad * (0.3f + 0.45f * t) * (0.7f + 0.3f * Math.sin(i * 2.3 + id).toFloat)
      val ms = (1f - t) * rad * 0.085f + 0.7f
      sb.fillOval(sx - ndx * back + px * lat, sy - ndy * back + py * lat, ms, ms, r, g, b, 0.85f * (1f - t) * p, 6)
      i += 1
    }
  }

  /**
   * Energy orb. Every style shares one anatomy — a comet tail of light behind, a glow, a lit
   * sphere, specks shed off its back — and a style is whatever it adds *outside* that sphere:
   * inner detail is invisible at the size an orb is displayed, and whatever a style draws
   * inside the body's 0.90 x 0.68 sz ellipse is painted over. Nearly thirty characters' primary
   * attacks are one of these, so each style has to read as its element at a glance.
   *
   * What it used to be, and why it isn't: the whole orb pulsed between 100% and 30% alpha three
   * times a second (a strobe, and on pale ground its dim frames weren't there); a dozen white
   * specks orbited *inside* the body and read as dirt on a flat disc; and a halo 3.2 times its
   * size — about 0.7 million fragments an orb at 4K — tinted the ground round it for no read.
   */
  private def energyBolt(r: Float, g: Float, b: Float, size: Float = 20f, style: Int = ORB_PLAIN): Renderer =
    (proj, sx, sy, sb, tick) => {
      val phase = (tick + proj.id * 37) * 0.35
      computeAllDynamics(proj, r, g, b, phase)
      // Steady: the body holds its strength and only the core breathes
      val p = (0.95f + 0.05f * Math.sin(phase * _stPulseMult).toFloat) * dynAlpha
      val dr = _evoR; val dg = _evoG; val db = _evoB
      val lr = bright(dr); val lg = bright(dg); val lb = bright(db)
      val breath = 0.97f + 0.03f * Math.sin(phase * 0.8).toFloat
      val sz = size * 1.4f * dynScale * breath
      val rx = sz * 0.9f; val ry = sz * 0.68f
      screenDir(proj)
      val ndx = _sdx; val ndy = _sdy
      val pxv = -ndy; val pyv = ndx
      // The body's width across the flight line, which is where its tail starts
      val across = 2f * Math.sqrt(rx * pxv * rx * pxv + ry * pyv * ry * pyv).toFloat
      val hot = 1f + 0.16f * Math.sin(phase * 1.9).toFloat + _chgBright
      val tailLen = sz * 2.5f * dynTrail * (0.93f + 0.07f * Math.sin(phase * 2.3).toFloat)

      // ── What streams out behind it ──
      style match {
        case ORB_MUD => mudTrail(sb, sx, sy, ndx, ndy, sz, p, tick, proj.id)
        case ORB_SAND =>
          cometTail(sb, sx, sy, ndx, ndy, tailLen * 1.05f, across * 1.05f, dr, dg, db, 0.3f * p)
          cometTail(sb, sx, sy, ndx, ndy, tailLen * 0.7f, across * 0.5f, lr, lg, lb, 0.45f * p)
          sandGrains(sb, sx, sy, ndx, ndy, sz, tailLen, p, tick, proj.id)
        case ORB_SWARM =>
          cometTail(sb, sx, sy, ndx, ndy, tailLen * 0.9f, across * 0.9f, dr, dg, db, 0.22f * p)
          naniteStream(sb, sx, sy, ndx, ndy, sz, tailLen, dr, dg, db, p, tick, proj.id)
        case ORB_FIRE => fireTail(sb, sx, sy, ndx, ndy, rx, sz, across, tailLen, dr, dg, db, p, phase)
        case ORB_SPIRIT =>
          cometTail(sb, sx, sy, ndx, ndy, tailLen * 1.1f, across * 1.1f, dr, dg, db, 0.3f * p)
          spiritWisps(sb, sx, sy, ndx, ndy, rx, across, tailLen, lr, lg, lb, p, phase)
          cometTail(sb, sx, sy, ndx, ndy, tailLen * 0.7f, across * 0.4f, mix(lr, 1f, 0.5f), mix(lg, 1f, 0.5f), mix(lb, 1f, 0.5f), 0.7f * p)
        case _ =>
          cometTail(sb, sx, sy, ndx, ndy, tailLen * 1.12f, across * 1.2f, dr, dg, db, 0.26f * p)
          cometTail(sb, sx, sy, ndx, ndy, tailLen, across * 0.88f, mix(dr, lr, 0.3f), mix(dg, lg, 0.3f), mix(db, lb, 0.3f), 0.6f * p)
          cometTail(sb, sx, sy, ndx, ndy, tailLen * 0.78f, across * 0.38f, mix(lr, 1f, 0.55f), mix(lg, 1f, 0.55f), mix(lb, 1f, 0.55f), 0.8f * p)
      }

      // ── Glow: modest, and none on mud ──
      if (style != ORB_MUD)
        sb.fillOvalSoft(sx, sy, rx * 1.75f * dynGlow, ry * 1.85f * dynGlow, dr, dg, db,
          (if (style == ORB_SAND) 0.18f else 0.3f) * p, 0f, 16)

      // ── Behind the body ──
      val orbits = style == ORB_ORBIT || style == ORB_ASTRAL
      val ringA = if (orbits) sz * 1.24f else 0f
      // Never quite edge-on (which reads as a bar through the orb) and never a circle
      // (which reads as a flat halo) — the nod between the two is what sells the tilt.
      val ringB = ringA * (0.17f + 0.33f * (0.5f + 0.5f * Math.sin(phase * 0.55).toFloat))
      val ringSpin = phase * 1.15
      if (orbits)
        orbitHalf(sb, sx, sy, ringA, ringB, ringSpin, front = false, 3, style == ORB_ASTRAL, dr, dg, db, 0.95f * p)
      style match {
        case ORB_RUNE => runeCircle(sb, sx, sy, sz, front = false, dr, dg, db, p, phase)
        case ORB_SWARM => nanites(sb, sx, sy, rx, front = false, dr, dg, db, p, phase)
        case _ => ()
      }

      // ── The body ──
      style match {
        case ORB_HEART => heartBody(sb, sx, sy, sz, dr, dg, db, p, phase)
        case ORB_MUD => mudBody(sb, sx, sy, ndx, ndy, rx, ry, p, phase)
        case ORB_SWARM => orbBody(sb, sx, sy, rx * 0.62f, ry * 0.62f, dr, dg, db, p, hot)
        case ORB_ASTRAL => starBody(sb, sx, sy, sz, dr, dg, db, p, phase)
        case ORB_SPIRIT => orbBody(sb, sx, sy, rx, ry, dr, dg, db, p, hot, ghost = 0.7f)
        case _ => orbBody(sb, sx, sy, rx, ry, dr, dg, db, p, hot)
      }

      // ── Over the body ──
      style match {
        case ORB_FIRE =>
          // Embers kicked up off the back, rising as they fall behind
          var e = 0
          while (e < 4) {
            val t = ((tick * 0.05 + e * 0.25 + proj.id * 0.29) % 1.0).toFloat
            val side = if ((e & 1) == 0) 1f else -1f
            val ex = sx - ndx * (rx * 0.7f + t * tailLen * 0.85f) + pxv * side * sz * (0.2f + 0.35f * t)
            val ey = sy - ndy * (rx * 0.7f + t * tailLen * 0.85f) + pyv * side * sz * (0.2f + 0.35f * t) - t * t * sz * 0.6f
            val es = (1f - t) * 2.3f + 0.7f
            sb.fillOval(ex, ey, es, es, 1f, mix(0.95f, 0.45f, t), mix(0.6f, 0.05f, t), 0.95f * (1f - t) * p, 6)
            e += 1
          }
        case ORB_RUNE => runeCircle(sb, sx, sy, sz, front = true, dr, dg, db, p, phase)
        case ORB_ASTRAL =>
          // The glint, over the core rather than behind it. Behind the body only the spike tips
          // showed, and a pair of hairlines poking past a round orb reads as a scratch.
          val flare = 0.85f + 0.15f * Math.sin(phase * 1.3).toFloat
          sb.fillStarFlare(sx, sy, sz * 1.75f * flare, sz * 0.26f,
            Math.sin(phase * 0.2).toFloat * 0.16f, 0.82f,
            1f, mix(lg, 1f, 0.7f), mix(lb, 1f, 0.5f), 0.9f * p)
        case ORB_TOXIC =>
          toxicBubbles(sb, sx, sy, rx, ry, dr, dg, db, p, tick, proj.id)
          // Drops of it falling away behind
          var d = 0
          while (d < 3) {
            val t = ((tick * 0.04 + d / 3.0 + proj.id * 0.17) % 1.0).toFloat
            val side = hash2(proj.id + 3, d) * sz * 0.5f
            val dx2 = sx - ndx * (rx * 0.5f + t * tailLen * 0.6f) + pxv * side
            val dy2 = sy - ndy * (rx * 0.5f + t * tailLen * 0.6f) + pyv * side + t * t * sz * 1.1f
            val ds2 = sz * 0.11f * (1f - t * 0.4f)
            sb.fillOval(dx2, dy2, ds2 + 1.2f, ds2 * 1.35f + 1.2f, outline(dr), outline(dg), outline(db), 0.7f * (1f - t) * p, 8)
            sb.fillOval(dx2, dy2, ds2, ds2 * 1.35f, dr, dg, db, 0.95f * (1f - t) * p, 8)
            d += 1
          }
        case ORB_SAND => sandSwirl(sb, sx, sy, rx, ry, dr, dg, db, p, phase)
        case ORB_SWARM => nanites(sb, sx, sy, rx, front = true, dr, dg, db, p, phase)
        case ORB_SHOCK =>
          // Arcs crackling off the ball, re-struck twenty times a second
          val epoch = (tick + proj.id * 5) / 3
          var a = 0
          while (a < 4) {
            val ang = a * (Math.PI / 2) + hash2(proj.id * 71 + a, epoch) * 0.7
            val al = rx * (1.55f + 0.4f * hash2(proj.id * 73 + a, epoch))
            val ca2 = Math.cos(ang).toFloat; val sa2 = Math.sin(ang).toFloat
            sparkArc(sb, sx + ca2 * rx * 0.55f, sy + sa2 * ry * 0.55f, sx + ca2 * al, sy + sa2 * al * 0.8f, 4,
              rx * 0.3f, proj.id * 13 + epoch * 7 + a, 3f, lr, lg, lb, p)
            a += 1
          }
        case _ => ()
      }
      if (orbits)
        orbitHalf(sb, sx, sy, ringA, ringB, ringSpin, front = true, 3, style == ORB_ASTRAL, dr, dg, db, p)

      // Specks shed off its back — not off mud, sand or the swarm, which shed their own
      if (style != ORB_MUD && style != ORB_SAND && style != ORB_SWARM)
        shedMotes(sb, sx, sy, ndx, ndy, rx, 3, lr, lg, lb, p, tick, proj.id)
      if (style == ORB_HEART) {
        // A pair of glints twinkling round the heart
        var k = 0
        while (k < 2) {
          val t = ((tick * 0.03 + k * 0.5 + proj.id * 0.23) % 1.0).toFloat
          val a = k * Math.PI + proj.id + t * 1.4
          val gx = sx + Math.cos(a).toFloat * sz * 1.05f; val gy = sy + Math.sin(a).toFloat * sz * 0.8f - sz * 0.1f
          sb.fillStarFlare(gx, gy, sz * 0.42f * Math.sin(t * Math.PI).toFloat, 1.8f, 0f, 1f, 1f, 0.92f, 0.96f,
            0.9f * p)
          k += 1
        }
      }

      drawChargeCrackle(sx, sy, sz, r, g, b, p, sb, phase, proj.chargeLevel)
      drawReturnGhosts(sx, sy, sz * 0.9f, dr, dg, db, p, sb, proj)
    }

  /**
   * Fire: a teardrop of flame trailing the ball — orange round a yellow heart — with its edge
   * broken into tongues, three down each side, peeling off backward and curling up as flames do.
   * Each tongue is one tapering stroke along a bent path. Drawn in layers so the ink outlines the
   * whole mane rather than every tongue: ink, the red-orange tongues, the orange teardrop, the
   * yellow tongues, the yellow heart.
   *
   * Straight triangles stood on the rim (the first pass of this) read as a chestnut; stood all
   * round the ball at even angles (the pass before), as a mine or a sun.
   */
  private def fireTail(sb: ShapeBatch, sx: Float, sy: Float, ndx: Float, ndy: Float, rx: Float, sz: Float,
                       across: Float, tailLen: Float, r: Float, g: Float, b: Float, p: Float, phase: Double): Unit = {
    val px = -ndy; val py = ndx
    cometTail(sb, sx, sy, ndx, ndy, tailLen * 1.2f, across * 1.35f, r, g * 0.5f, b * 0.25f, 0.28f * p)
    var pass = 0
    while (pass < 3) {
      if (pass == 2) cometTail(sb, sx, sy, ndx, ndy, tailLen, across * 0.95f, r, g * 0.8f, b * 0.55f, 0.9f * p)
      val inner = pass == 2
      var j = 0
      while (j < 6) {
        val side = if (j < 3) 1f else -1f
        val k = j % 3
        val flick = 0.7f + 0.3f * Math.sin(phase * 3.9 + j * 2.1).toFloat
        val start = rx * (0.1f + k * 0.62f)
        val len = tailLen * (0.62f - k * 0.13f) * flick * (if (inner) 0.6f else 1f)
        val w0 = sz * (0.5f - k * 0.09f) * (if (inner) 0.55f else 1f)
        // Rooted on the teardrop's edge, heading back and out from it
        val hw = across * 0.5f * Math.max(0.25f, 1f - start / (tailLen * 1.1f))
        val rootX = sx - ndx * start + px * side * hw * (if (inner) 0.45f else 0.8f)
        val rootY = sy - ndy * start + py * side * hw * (if (inner) 0.45f else 0.8f)
        val dx = -ndx * 0.9f + px * side * 0.44f; val dy = -ndy * 0.9f + py * side * 0.44f
        var i = 0
        while (i < 4) {
          val t = i / 3f
          val sway = Math.sin(phase * 2.9 + j * 1.3 + t * 3.0).toFloat * len * 0.08f * t
          _pvX(i) = rootX + dx * len * t + px * sway
          _pvY(i) = rootY + dy * len * t + py * sway - len * 0.12f * t * t
          _pvW(i) = (if (pass == 0) w0 + 2.8f else w0) * (1f - 0.97f * t)
          _pvA(i) = p * (if (pass == 0) 0.55f else 0.95f) * (1f - t * t)
          i += 1
        }
        if (pass == 0) sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, 4, outline(r), outline(g), outline(b))
        else if (!inner) sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, 4, r, g * 0.55f, b * 0.3f)
        else sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, 4, 1f, mix(g, 1f, 0.72f), mix(b, 0.45f, 0.5f))
        j += 1
      }
      pass += 1
    }
    cometTail(sb, sx, sy, ndx, ndy, tailLen * 0.6f, across * 0.45f, 1f, mix(g, 1f, 0.8f), mix(b, 0.6f, 0.5f), 0.9f * p)
  }

  /** A magic circle lying in the ground plane round the orb: an inked double ring with glyphs
   *  on it turning slowly. Drawn in halves, the far one before the body and the near one after,
   *  so the orb sits inside the circle. The old rune style stood two grey rings round the orb on
   *  screen, and a ring round a disc drawn flat on the screen is a tyre. */
  private def runeCircle(sb: ShapeBatch, sx: Float, sy: Float, sz: Float, front: Boolean,
                         r: Float, g: Float, b: Float, p: Float, phase: Double): Unit = {
    val cy = sy + sz * 0.08f
    val a0 = sz * 1.34f; val b0 = a0 * 0.42f
    val a1 = a0 * 0.8f; val b1 = b0 * 0.8f
    val start = if (front) 0f else Math.PI.toFloat
    val lr = mix(bright(r), 1f, 0.25f); val lg = mix(bright(g), 1f, 0.25f); val lb = mix(bright(b), 1f, 0.25f)
    sb.strokeArc(sx, cy, a0, b0, start, Math.PI.toFloat, 3.6f, outline(r), outline(g), outline(b), 0.55f * p, 14)
    sb.strokeArc(sx, cy, a0, b0, start, Math.PI.toFloat, 1.9f, lr, lg, lb, 0.9f * p, 14)
    sb.strokeArc(sx, cy, a1, b1, start, Math.PI.toFloat, 1.3f, lr, lg, lb, 0.6f * p, 12)
    // Glyphs between the rings: small diamonds, each lying along the circle
    var k = 0
    while (k < 6) {
      val a = phase * 0.45 + k * Math.PI / 3
      val sa = Math.sin(a).toFloat
      if ((sa >= 0f) == front) {
        val ca = Math.cos(a).toFloat
        val gx = sx + ca * a0 * 0.9f; val gy = cy + sa * b0 * 0.9f
        // Tangent to the ellipse at a, and the way out of it
        val tx0 = -sa * a0; val ty0 = ca * b0
        val tl = Math.max(1e-3f, Math.sqrt(tx0 * tx0 + ty0 * ty0).toFloat)
        val tx = tx0 / tl; val ty = ty0 / tl
        val gl = sz * 0.2f; val gw = sz * 0.09f
        _polyXs4(0) = gx - tx * gl; _polyYs4(0) = gy - ty * gl
        _polyXs4(1) = gx - ty * gw; _polyYs4(1) = gy + tx * gw
        _polyXs4(2) = gx + tx * gl; _polyYs4(2) = gy + ty * gl
        _polyXs4(3) = gx + ty * gw; _polyYs4(3) = gy - tx * gw
        sb.strokePolygon(_polyXs4, _polyYs4, 4, 2f, outline(r), outline(g), outline(b), 0.6f * p)
        sb.fillPolygon(_polyXs4, _polyYs4, 4, lr, lg, lb, 0.95f * p)
      }
      k += 1
    }
  }

  /** A soul: two thin wisps peeling off its sides and curling away behind it, like the hem of
   *  something that isn't there. They bow outward and hold still — no swing. */
  private def spiritWisps(sb: ShapeBatch, sx: Float, sy: Float, ndx: Float, ndy: Float, rx: Float,
                          across: Float, tailLen: Float, r: Float, g: Float, b: Float, p: Float,
                          phase: Double): Unit = {
    val px = -ndy; val py = ndx
    var w = 0
    while (w < 2) {
      val side = if (w == 0) 1f else -1f
      val reach = 0.8f + 0.2f * Math.sin(phase * 1.3 + w * 2.2).toFloat
      var i = 0
      while (i < 4) {
        val t = i / 3f
        val back = rx * 0.4f + tailLen * 0.9f * t * reach
        val lat = side * across * (0.36f + 0.34f * t * t)
        _pvX(i) = sx - ndx * back + px * lat; _pvY(i) = sy - ndy * back + py * lat
        _pvW(i) = across * 0.22f * (1f - t * 0.92f)
        _pvA(i) = 0.62f * p * (1f - t)
        i += 1
      }
      sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, 4, r, g, b)
      w += 1
    }
  }

  /** A charm: a heart, upright on screen whatever the heading, beating lub-dub. Its outline is
   *  the union of two lobes and a point: every piece is inked slightly larger first and filled
   *  over, so the ink shows only round the outside. */
  private def heartBody(sb: ShapeBatch, sx: Float, sy: Float, sz: Float, r: Float, g: Float, b: Float,
                        p: Float, phase: Double): Unit = {
    val bt = ((phase * 0.08) % 1.0).toFloat
    val beat = if (bt < 0.12f) Math.sin(bt / 0.12f * Math.PI).toFloat
      else if (bt > 0.2f && bt < 0.32f) 0.6f * Math.sin((bt - 0.2f) / 0.12f * Math.PI).toFloat else 0f
    val h = sz * 0.98f * (1f + 0.1f * beat)
    val ly = sy - h * 0.2f; val lo = h * 0.4f; val lrad = h * 0.47f
    var pass = 0
    while (pass < 2) {
      val grow = if (pass == 0) 2f else 0f
      val cr = if (pass == 0) outline(r) else r * 0.72f
      val cg = if (pass == 0) outline(g) else g * 0.72f
      val cb = if (pass == 0) outline(b) else b * 0.72f
      val ca = if (pass == 0) 0.9f * p else 0.98f * p
      sb.fillOval(sx - lo, ly, lrad + grow, lrad + grow, cr, cg, cb, ca, 18)
      sb.fillOval(sx + lo, ly, lrad + grow, lrad + grow, cr, cg, cb, ca, 18)
      _polyXs3(0) = sx - h * 0.84f - grow * 0.9f; _polyYs3(0) = sy - h * 0.06f
      _polyXs3(1) = sx + h * 0.84f + grow * 0.9f; _polyYs3(1) = sy - h * 0.06f
      _polyXs3(2) = sx; _polyYs3(2) = sy + h * 0.86f + grow * 1.4f
      sb.fillPolygon(_polyXs3, _polyYs3, 3, cr, cg, cb, ca)
      pass += 1
    }
    val lr = bright(r); val lg = bright(g); val lb = bright(b)
    // Lit from the upper left, as every orb is
    sb.fillOvalSoft(sx - lo * 0.7f, ly + h * 0.05f, h * 0.62f, h * 0.55f, r, g, b, 0.95f * p, 0f, 16)
    sb.fillOvalSoft(sx - lo * 0.3f, sy + h * 0.02f, h * 0.4f, h * 0.36f, mix(lr, 1f, 0.35f), mix(lg, 1f, 0.35f),
      mix(lb, 1f, 0.35f), (0.6f + 0.3f * beat) * p, 0f, 14)
    sb.fillOval(sx - lo - lrad * 0.25f, ly - lrad * 0.35f, lrad * 0.36f, lrad * 0.24f, 1f, 1f, 1f, 0.82f * p, 10)
    sb.strokeArc(sx + lo, ly, lrad * 0.8f, lrad * 0.8f, -0.4f, 1.3f, Math.max(1.3f, h * 0.07f),
      mix(lr, 1f, 0.45f), mix(lg, 1f, 0.45f), mix(lb, 1f, 0.45f), 0.6f * p, 8)
  }

  /** A shooting star: five points turning slowly, gold, facing the camera. Inked as a larger
   *  star filled behind it rather than stroked, since the mitre at a star's points is past the
   *  batch's limit and a clipped mitre folds over. Fanned from its centre (it isn't convex). */
  private def starBody(sb: ShapeBatch, sx: Float, sy: Float, sz: Float, r: Float, g: Float, b: Float,
                       p: Float, phase: Double): Unit = {
    val spin = phase * 0.22 - Math.PI / 2
    var pass = 0
    while (pass < 3) {
      val ro = sz * (if (pass == 0) 1.08f else if (pass == 1) 0.98f else 0.56f) + (if (pass == 0) 2.2f else 0f)
      val ri = ro * 0.47f
      var i = 0
      while (i < 10) {
        val a = spin + i * (Math.PI / 5)
        val rad = if ((i & 1) == 0) ro else ri
        _shpXs(i) = sx + Math.cos(a).toFloat * rad
        _shpYs(i) = sy + Math.sin(a).toFloat * rad * 0.92f
        i += 1
      }
      pass match {
        case 0 => sb.fillFan(sx, sy, _shpXs, _shpYs, 10, outline(r), outline(g), outline(b), 0.9f * p)
        case 1 => sb.fillFan(sx, sy, _shpXs, _shpYs, 10, r * 0.9f, g * 0.86f, b * 0.7f, 0.98f * p)
        case _ => sb.fillFan(sx, sy, _shpXs, _shpYs, 10, mix(bright(r), 1f, 0.3f), mix(bright(g), 1f, 0.3f),
          mix(bright(b), 1f, 0.2f), 0.9f * p)
      }
      pass += 1
    }
    sb.fillOvalSoft(sx, sy, sz * 0.4f, sz * 0.37f, 1f, 1f, 0.9f, 0.95f * p, 0f, 12)
  }

  /** Plague: bubbles swelling on the skin of the orb and bursting, and a blotch or two under it. */
  private def toxicBubbles(sb: ShapeBatch, sx: Float, sy: Float, rx: Float, ry: Float,
                           r: Float, g: Float, b: Float, p: Float, tick: Int, id: Int): Unit = {
    sb.fillOval(sx + rx * 0.3f, sy + ry * 0.28f, rx * 0.26f, ry * 0.22f, r * 0.45f, g * 0.5f, b * 0.35f, 0.55f * p, 10)
    sb.fillOval(sx - rx * 0.36f, sy + ry * 0.12f, rx * 0.16f, ry * 0.14f, r * 0.45f, g * 0.5f, b * 0.35f, 0.5f * p, 8)
    var k = 0
    while (k < 3) {
      val t = ((tick * 0.022 + k * 0.33 + id * 0.19) % 1.0).toFloat
      val a = k * 2.1 + id * 0.7 - 0.9
      val bx = sx + Math.cos(a).toFloat * rx * 0.72f; val by = sy + Math.sin(a).toFloat * ry * 0.72f
      if (t < 0.84f) {
        val bs = rx * 0.24f * Math.min(1f, t * 2.4f)
        sb.fillOval(bx, by, bs + 1.4f, bs + 1.4f, outline(r), outline(g), outline(b), 0.75f * p, 10)
        sb.fillOval(bx, by, bs, bs, mix(r, 1f, 0.3f), mix(g, 1f, 0.3f), mix(b, 1f, 0.2f), 0.95f * p, 10)
        sb.fillOval(bx - bs * 0.32f, by - bs * 0.36f, bs * 0.34f, bs * 0.28f, 1f, 1f, 1f, 0.8f * p, 6)
      } else {
        // Popped: a ring flung wide and gone
        val pt = (t - 0.84f) / 0.16f
        sb.strokeOval(bx, by, rx * (0.24f + pt * 0.3f), rx * (0.24f + pt * 0.3f) * 0.8f, 1.6f * (1f - pt),
          mix(r, 1f, 0.4f), mix(g, 1f, 0.4f), mix(b, 1f, 0.3f), 0.8f * (1f - pt) * p, 10)
      }
      k += 1
    }
  }

  /** Sand: three arms of darker grit spiralling out of the middle and past the rim, whirling.
   *  Concentric bands, the first try, read as the rings of a coin. */
  private def sandSwirl(sb: ShapeBatch, sx: Float, sy: Float, rx: Float, ry: Float,
                        r: Float, g: Float, b: Float, p: Float, phase: Double): Unit = {
    var k = 0
    while (k < 3) {
      val a0 = phase * 1.9 + k * (Math.PI * 2 / 3)
      var i = 0
      while (i < 5) {
        val t = i / 4f
        val a = a0 + t * 2.0
        val rad = 0.15f + 0.97f * t
        _pvX(i) = sx + Math.cos(a).toFloat * rx * rad
        _pvY(i) = sy + Math.sin(a).toFloat * ry * rad
        _pvW(i) = rx * (0.24f - 0.17f * t)
        _pvA(i) = 0.85f * p * (1f - 0.3f * t)
        i += 1
      }
      sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, 5, r * 0.58f, g * 0.46f, b * 0.3f)
      k += 1
    }
  }

  /** Sand: grains streaming off the back and spreading out, darker than the dust they fly in. */
  private def sandGrains(sb: ShapeBatch, sx: Float, sy: Float, ndx: Float, ndy: Float, sz: Float,
                         tailLen: Float, p: Float, tick: Int, id: Int): Unit = {
    val px = -ndy; val py = ndx
    var i = 0
    while (i < 8) {
      val t = ((tick * 0.06 + i * 0.125 + id * 0.31) % 1.0).toFloat
      val lat = hash2(id, i) * sz * (0.3f + 0.7f * t)
      val gx = sx - ndx * (sz * 0.6f + t * tailLen) + px * lat
      val gy = sy - ndy * (sz * 0.6f + t * tailLen) + py * lat + t * t * 6f
      val gs = 1.2f + (1f - t) * 1.4f
      sb.fillRect(gx - gs * 0.5f, gy - gs * 0.5f, gs, gs, 0.45f, 0.34f, 0.18f, 0.9f * (1f - t) * p)
      i += 1
    }
  }

  /** Mud flies unlit: no tail of light, just drops flung back off it and a dark smear. */
  private def mudTrail(sb: ShapeBatch, sx: Float, sy: Float, ndx: Float, ndy: Float, sz: Float,
                       p: Float, tick: Int, id: Int): Unit = {
    val px = -ndy; val py = ndx
    var i = 0
    while (i < 3) {
      val t = i / 2f
      _pvX(i) = sx - ndx * sz * (0.3f + t * 1.5f); _pvY(i) = sy - ndy * sz * (0.3f + t * 1.5f) + t * t * 3f
      _pvW(i) = sz * 0.9f * (1f - t * 0.95f); _pvA(i) = 0.5f * p * (1f - t)
      i += 1
    }
    sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, 3, 0.24f, 0.17f, 0.09f)
    i = 0
    while (i < 4) {
      val t = ((tick * 0.05 + i * 0.25 + id * 0.41) % 1.0).toFloat
      val lat = (if ((i & 1) == 0) 1f else -1f) * sz * (0.25f + 0.5f * t)
      val mx = sx - ndx * (sz * 0.7f + t * sz * 2.2f) + px * lat
      val my = sy - ndy * (sz * 0.7f + t * sz * 2.2f) + py * lat + t * t * sz * 0.8f
      val ms = sz * (0.2f - t * 0.1f) * (0.8f + 0.2f * (i % 3))
      sb.fillOval(mx, my, ms + 1.3f, ms * 0.85f + 1.3f, 0.12f, 0.08f, 0.04f, 0.8f * (1f - t) * p, 8)
      sb.fillOval(mx, my, ms, ms * 0.85f, 0.40f, 0.29f, 0.15f, 0.95f * (1f - t) * p, 8)
      i += 1
    }
  }

  /** A glob of wet mud: lumpy — three lobes inked as one — dark, and glossy where it catches the
   *  light. Mud doesn't glow, so none of the orb's light is on it. */
  private def mudBody(sb: ShapeBatch, sx: Float, sy: Float, ndx: Float, ndy: Float, rx: Float, ry: Float,
                      p: Float, phase: Double): Unit = {
    val px = -ndy; val py = ndx
    val wob = Math.sin(phase * 1.7).toFloat
    val l2x = sx - ndx * rx * 0.45f + px * rx * 0.34f; val l2y = sy - ndy * ry * 0.45f + py * ry * 0.34f
    val l3x = sx - ndx * rx * 0.38f - px * rx * 0.36f; val l3y = sy - ndy * ry * 0.38f - py * ry * 0.36f
    val r1 = 0.94f + 0.05f * wob; val r2 = 0.64f - 0.05f * wob; val r3 = 0.56f + 0.04f * wob
    var pass = 0
    while (pass < 2) {
      val grow = if (pass == 0) 2f else 0f
      val cr = if (pass == 0) 0.10f else 0.33f
      val cg = if (pass == 0) 0.07f else 0.23f
      val cb = if (pass == 0) 0.03f else 0.12f
      val ca = if (pass == 0) 0.9f * p else 0.98f * p
      sb.fillOval(l2x, l2y, rx * r2 + grow, ry * r2 + grow, cr, cg, cb, ca, 14)
      sb.fillOval(l3x, l3y, rx * r3 + grow, ry * r3 + grow, cr, cg, cb, ca, 14)
      sb.fillOval(sx, sy, rx * r1 + grow, ry * r1 + grow, cr, cg, cb, ca, 18)
      pass += 1
    }
    sb.fillOvalSoft(sx + KEY_LIGHT_X * rx * 0.25f, sy + KEY_LIGHT_Y * ry * 0.25f, rx * 0.8f, ry * 0.8f,
      0.52f, 0.38f, 0.2f, 0.9f * p, 0f, 16)
    // Wet: two hard highlights
    sb.fillOval(sx + KEY_LIGHT_X * rx * 0.45f, sy + KEY_LIGHT_Y * ry * 0.5f, rx * 0.2f, ry * 0.13f,
      1f, 0.97f, 0.9f, 0.85f * p, 10)
    sb.fillOval(sx + KEY_LIGHT_X * rx * 0.1f, sy + KEY_LIGHT_Y * ry * 0.62f, rx * 0.07f, ry * 0.06f,
      1f, 0.97f, 0.9f, 0.7f * p, 6)
  }

  /** Nanites: a dozen specks on tilted orbits of their own round a small core, drawn in two
   *  halves like the orbit style so the swarm has depth. Squares, since they are machines. */
  private def nanites(sb: ShapeBatch, sx: Float, sy: Float, rx: Float, front: Boolean,
                      r: Float, g: Float, b: Float, p: Float, phase: Double): Unit = {
    val lr = mix(r, bright(r), 0.5f); val lg = mix(g, bright(g), 0.5f); val lb = mix(b, bright(b), 0.5f)
    var i = 0
    while (i < 16) {
      val th = phase * (1.35 + 0.18 * (i % 4)) + i * (Math.PI * 2 / 16)
      val st = Math.sin(th).toFloat
      if ((st >= 0f) == front) {
        val ct = Math.cos(th).toFloat
        val oa = rx * (0.8f + 0.2f * (i % 3)); val ob = oa * (0.3f + 0.16f * (i % 2))
        val tilt = i * 0.9f
        val c = Math.cos(tilt).toFloat; val s = Math.sin(tilt).toFloat
        val ox = oa * ct; val oy = ob * st
        val nx = sx + ox * c - oy * s; val ny = sy + (ox * s + oy * c) * 0.8f
        val ns = 3.2f + (if (front) 0.8f else 0f)
        var pass = 0
        while (pass < 2) {
          val e = if (pass == 0) ns + 1.3f else ns
          _polyXs4(0) = nx; _polyYs4(0) = ny - e
          _polyXs4(1) = nx + e; _polyYs4(1) = ny
          _polyXs4(2) = nx; _polyYs4(2) = ny + e
          _polyXs4(3) = nx - e; _polyYs4(3) = ny
          if (pass == 0) sb.fillPolygon(_polyXs4, _polyYs4, 4, outline(r), outline(g), outline(b), 0.9f * p)
          else sb.fillPolygon(_polyXs4, _polyYs4, 4, lr, lg, lb, (if (front) 1f else 0.82f) * p)
          pass += 1
        }
      }
      i += 1
    }
  }

  /** The swarm's wake: nanites falling out of formation behind it. */
  private def naniteStream(sb: ShapeBatch, sx: Float, sy: Float, ndx: Float, ndy: Float, sz: Float,
                           tailLen: Float, r: Float, g: Float, b: Float, p: Float, tick: Int, id: Int): Unit = {
    val px = -ndy; val py = ndx
    val lr = mix(bright(r), 1f, 0.2f); val lg = mix(bright(g), 1f, 0.2f); val lb = mix(bright(b), 1f, 0.2f)
    var i = 0
    while (i < 7) {
      val t = ((tick * 0.055 + i / 7.0 + id * 0.27) % 1.0).toFloat
      val lat = hash2(id + 5, i) * sz * (0.35f + 0.4f * t)
      val nx = sx - ndx * (sz * 0.5f + t * tailLen) + px * lat
      val ny = sy - ndy * (sz * 0.5f + t * tailLen) + py * lat
      val ns = 2.4f * (1f - t * 0.6f)
      sb.fillRect(nx - ns, ny - ns, ns * 2, ns * 2, lr, lg, lb, 0.9f * (1f - t) * p)
      i += 1
    }
  }

  /**
   * Four-bladed throwing star. Its plate lies in the ground plane, so it is stamped like a
   * tumbling weapon and sells its spin with a swept band across the blade tips rather than
   * with smeared copies of itself. Nothing is drawn on a radius out from the hub: the four
   * straight "swoosh" lines the old version fired off past the blades read as stray
   * geometry poking out of the star, not as motion.
   */
  private def shuriken(r: Float, g: Float, b: Float, size: Float = 26f): Renderer =
    (proj, sx, sy, sb, tick) => {
      val spin = tick * 0.55 + proj.id * 2.1
      computeAllDynamics(proj, r, g, b, spin)
      val p = (0.88f + 0.12f * Math.sin(spin * 2 * _stPulseMult).toFloat) * dynAlpha
      val dr = _evoR; val dg = _evoG; val db = _evoB
      // Steel doesn't inflate with charge — a 2x throwing star reads as a bug.
      val ds = Math.min(dynScale, 1.3f)
      val s = size * 0.62f * ds
      val reach = s * 1.3f
      screenDir(proj)
      val ndx = _sdx; val ndy = _sdy
      val ca = Math.cos(spin).toFloat; val sa = Math.sin(spin).toFloat

      drawSpeedLines(sx, sy, ndx, ndy, dr, dg, db, 0.22f * p, sb, 4, reach * 1.2f)

      // Ground shadow — anchors the star to the arena instead of floating over it
      sb.fillOval(sx + 3f, sy + reach * 0.40f, reach * 0.55f, reach * 0.18f, 0f, 0f, 0f, 0.22f * p, 12)

      // Swept band across the blade tips: the spin read, drawn as geometry
      sb.fillArcBand(sx, sy, reach * 0.66f, reach * 0.66f * ISO_Y, reach * 1.02f, reach * 1.02f * ISO_Y,
        spin.toFloat - 1.9f, 1.9f, 10, bright(r), bright(g), bright(b), 0.02f * p, 0.34f * p)

      // Silhouette ghosts back along the flight path
      var ghost = 3; while (ghost >= 1) {
        val gA = 0.14f * (1f - (ghost - 1) * 0.3f) * p
        val gSpin = spin - ghost * 0.45
        drawPartsFlat(sb, SHURIKEN_PARTS, sx - ndx * ghost * 7f, sy - ndy * ghost * 7f,
          Math.cos(gSpin).toFloat, Math.sin(gSpin).toFloat, s * (1f - ghost * 0.05f),
          dr * 0.8f, dg * 0.8f, db * 0.8f, gA)
        ghost -= 1
      }

      // Halo so steel separates from busy ground without washing it out
      sb.fillOvalSoft(sx, sy, reach * 1.35f * dynGlow, reach * 1.35f * ISO_Y * dynGlow,
        dr, dg, db, 0.18f * p, 0f, 14)

      drawParts(sb, SHURIKEN_PARTS, sx, sy, ca, sa, s, dr, dg, db, 0.97f * dynAlpha,
        clampF(s * 0.13f, 1.2f, 2.6f))

      // Rimmed centre hole — what separates a throwing star from a pinwheel
      sb.fillOval(sx, sy, s * 0.30f, s * 0.30f * ISO_Y, 0.10f, 0.10f, 0.12f, 0.85f * p, 10)
      sb.strokeOval(sx, sy, s * 0.30f, s * 0.30f * ISO_Y, 1.6f, bright(r), bright(g), bright(b), 0.55f * p, 10)

      // Edge glint as a blade sweeps through the light direction
      val glint = Math.sin(spin * 2 + proj.id).toFloat
      if (glint > 0.74f) {
        blitPoint(1.18f, -0.10f, sx, sy, ca, sa, s)
        sb.fillStarFlare(_ptX, _ptY, s * 0.8f * (glint - 0.74f) / 0.26f, 2f,
          spin.toFloat * 0.5f, 0.45f, 1f, 1f, 0.96f, 0.7f * p)
      }

      drawChargeCrackle(sx, sy, reach, r, g, b, p, sb, spin, proj.chargeLevel)
      drawReturnGhosts(sx, sy, reach * 0.7f, dr, dg, db, p, sb, proj)
    }

  /**
   * Thrown weapon tumbling end over end. Rather than building a polar star — which
   * renders every melee weapon as the same lens — this stamps the weapon's own
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

  /** As [[drawPartsDir]], but every part's contour before any part's body, so a shape split into
   *  spans for convexity — a curved blade — is inked as one piece. Contour then body part by part
   *  draws each span's ink across the one before it, and the blade comes out segmented. */
  private def drawPartsDirUnion(sb: ShapeBatch, parts: Array[Part], cx: Float, cy: Float,
                                fx: Float, fy: Float, s: Float, tr: Float, tg: Float, tb: Float,
                                alpha: Float, outlineW: Float): Unit = {
    if (alpha <= 0.01f) return
    var i = 0
    while (i < parts.length) {
      val pt = parts(i)
      blitPartDir(pt, cx, cy, fx, fy, s, 1f)
      sb.strokePolygon(_shpXs, _shpYs, pt.n, outlineW, 0.06f, 0.05f, 0.07f, 0.85f * alpha)
      i += 1
    }
    i = 0
    while (i < parts.length) {
      val pt = parts(i)
      blitPartDir(pt, cx, cy, fx, fy, s, 1f)
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
        sb.fillFan(cx, cy, _shpXs, _shpYs, 7, dr * 0.72f, dg * 0.82f, db * 0.95f, a)
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
  private val CHN_HOOK = 3   // the rope, with a single meat hook on it rather than a grapple

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
      val ds = Math.min(dynScale, 1.3f) * (if (kind == CHN_ROPE || kind == CHN_HOOK) 1f else 1.25f)
      screenDir(proj)
      val ndx = _sdx; val ndy = _sdy
      val px = -ndy; val py = ndx
      val heading = Math.atan2(ndy, ndx)
      val hiR = mix(bright(r), 1f, 0.3f); val hiG = mix(bright(g), 1f, 0.3f); val hiB = mix(bright(b), 1f, 0.3f)

      kind match {
        case CHN_ROPE | CHN_HOOK =>
          // The rope, paid out all the way back to the hand that threw it (see TETHERS) — two
          // strands laid round each other, hanging a little in the middle. It used to stop 2.4
          // world units back, measured by getDistanceTraveled, which the client never advances:
          // in a real match no rope was ever drawn at all, only a hook flying on its own.
          if (layTether(proj, sx, sy, phase, 7f * ds, 5f * ds, 7f * ds)) {
            shapeTether(3.4f * ds, 4f * ds, 1f, p, 0.1f)
            var strand = 0
            while (strand < 2) {
              val ph = strand * Math.PI
              var i = 0
              while (i < _tN) {
                val twist = Math.sin(i * (Math.PI / 2) + ph + phase * 0.8).toFloat * 1.5f * ds
                _pvX(i) = _tX(i) + _tNX(i) * twist; _pvY(i) = _tY(i) + _tNY(i) * twist
                _pvW(i) = _tW(i) + 2.2f; _pvA(i) = _tA(i) * 0.75f
                i += 1
              }
              sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, _tN, 0.10f, 0.07f, 0.05f)
              i = 0
              while (i < _tN) { _pvW(i) = _tW(i); _pvA(i) = _tA(i) * 0.97f; i += 1 }
              val k = if (strand == 0) 1f else 0.8f
              sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, _tN, dr * k, dg * k, db * k)
              strand += 1
            }
          }
          if (kind == CHN_HOOK) {
            // A single hook, point back toward the one hauling on it
            sb.fillOvalSoft(sx, sy, 17f * ds * dynGlow, 14f * ds * dynGlow, dr, dg, db, 0.24f * p, 0f, 12)
            drawPartsDirUnion(sb, DEATH_HOOK_PARTS, sx, sy, ndx, ndy, 15f * ds, STEEL_R, STEEL_G, STEEL_B, 0.97f * p, 2.4f)
          } else {
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
          }

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
      val p = (0.9 + 0.1 * Math.sin(phase * _stPulseMult)).toFloat * dynAlpha
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
      // A shallow pulse: at 0.72 the whole front thinned by a quarter every beat
      val p = (0.86f + 0.14f * Math.sin(phase * _stPulseMult).toFloat) * dynAlpha
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

      // Body of the wave front: three nested bands, densest at the leading edge. Denser than it
      // was: at 42/34/40% a pale front (wind, sand, water) vanished on sand and snow, and even a
      // saturated one (acid) into grass of its own colour (render_audit)
      sb.fillArcBand(cxA, cyA, R * (1f - thickness), R * (1f - thickness) * ISO_Y, R, R * ISO_Y,
        start, sweep, 16, dr, dg, db, 0.58f * p, 0.58f * p)
      sb.fillArcBand(cxA, cyA, R * (1f - thickness * 0.6f), R * (1f - thickness * 0.6f) * ISO_Y, R, R * ISO_Y,
        start, sweep, 16, dr, dg, db, 0.42f * p, 0.42f * p)
      sb.fillArcBand(cxA, cyA, R * (1f - thickness * 0.25f), R * (1f - thickness * 0.25f) * ISO_Y, R, R * ISO_Y,
        start, sweep, 16, mix(dr, 1f, 0.25f), mix(dg, 1f, 0.25f), mix(db, 1f, 0.25f), 0.46f * p, 0.46f * p)
      // Ink round the whole crescent: the trailing edge and both horns as well as the front, so
      // the shape holds on ground as pale as it is. Only the leading edge used to be inked, and
      // the rest of the band faded into the ground behind it
      val rIn = R * (1f - thickness)
      sb.strokeArc(cxA, cyA, rIn, rIn * ISO_Y, start, sweep, 2.2f, 0.06f, 0.05f, 0.07f, 0.5f * p, 16)
      val c0 = Math.cos(start).toFloat; val s0 = Math.sin(start).toFloat
      val c1 = Math.cos(start + sweep).toFloat; val s1 = Math.sin(start + sweep).toFloat
      sb.strokeLine(cxA + c0 * rIn, cyA + s0 * rIn * ISO_Y, cxA + c0 * R * 1.02f, cyA + s0 * R * 1.02f * ISO_Y,
        2.2f, 0.06f, 0.05f, 0.07f, 0.5f * p)
      sb.strokeLine(cxA + c1 * rIn, cyA + s1 * rIn * ISO_Y, cxA + c1 * R * 1.02f, cyA + s1 * R * 1.02f * ISO_Y,
        2.2f, 0.06f, 0.05f, 0.07f, 0.5f * p)
      // Dark contour behind the leading edge, then the bright edge itself
      sb.strokeArc(cxA, cyA, R * 1.015f, R * 1.015f * ISO_Y, start, sweep,
        if (kind == WAV_WIND || kind == WAV_WATER) 6f else 4.5f,
        0.06f, 0.05f, 0.07f, if (kind == WAV_WIND || kind == WAV_WATER) 0.8f * p else 0.7f * p, 16)
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

    // A comet tail of its light and a halo no wider than it needs (see energyBolt): speed lines,
    // a ribbon of soft strokes and two halos, one pulsing out to 70 units, were a smear and a fill cost
    cometTail(sb, sx, sy, ndx, ndy, 58f * ds * dynTrail, 36f * ds, 0.3f, 0.05f, 0.5f, 0.3f * p)
    cometTail(sb, sx, sy, ndx, ndy, 46f * ds * dynTrail, 22f * ds, 0.5f, 0.22f, 0.78f, 0.55f * p)
    sb.fillOvalSoft(sx, sy, 30f * ds * dynGlow, 24f * ds * dynGlow, 0.3f, 0.05f, 0.5f, 0.28f * p, 0f, 18)

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

    // A comet tail of its light and a halo no wider than it needs (see energyBolt): speed lines,
    // a ribbon of soft strokes and two halos, one pulsing out to 70 units, were a smear and a fill cost
    cometTail(sb, sx, sy, ndx, ndy, 54f * ds * dynTrail, 36f * ds, 0.2f, 0.08f, 0.4f, 0.3f * p)
    cometTail(sb, sx, sy, ndx, ndy, 42f * ds * dynTrail, 22f * ds, 0.4f, 0.25f, 0.7f, 0.5f * p)
    sb.fillOvalSoft(sx, sy, 30f * ds * dynGlow, 24f * ds * dynGlow, 0.2f, 0.08f, 0.4f, 0.26f * p, 0f, 18)

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

    // A comet tail of its light and a halo no wider than it needs (see energyBolt): speed lines,
    // a ribbon of soft strokes and two halos, one pulsing out to 70 units, were a smear and a fill cost
    cometTail(sb, sx, sy, ndx, ndy, 58f * ds * dynTrail, 36f * ds, 1f, 0.9f, 0.5f, 0.3f * p)
    cometTail(sb, sx, sy, ndx, ndy, 46f * ds * dynTrail, 20f * ds, 1f, 0.97f, 0.8f, 0.6f * p)
    sb.fillOvalSoft(sx, sy, 32f * ds * dynGlow, 26f * ds * dynGlow, 1f, 0.92f, 0.55f, 0.32f * p, 0f, 18)

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
    // Golden star fill, fanned from the centre the radii were measured from — a star is
    // non-convex, and fanned from vertex 0 it fills as a blob with its notches bridged.
    sb.fillFan(sx, sy, _holyXs, _holyYs, 12, dr, dg, db, 0.88f * p);
    // Bright highlight layer (smaller star)
    { var i = 0; while (i < 12) {
      val a = starSpin + i * Math.PI / 6
      val rad = if (i % 2 == 0) outerR * 0.7f else innerR * 0.8f
      _holyXs(i) = (sx + Math.cos(a).toFloat * rad).toFloat
      _holyYs(i) = (sy + Math.sin(a).toFloat * rad * 0.65f).toFloat
    ; i += 1 } }
    sb.fillFan(sx, sy, _holyXs, _holyYs, 12, 1f, 0.98f, 0.75f, 0.5f * p)
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

    // A comet tail of its light and a halo no wider than it needs (see energyBolt): speed lines,
    // a ribbon of soft strokes and two halos, one pulsing out to 70 units, were a smear and a fill cost
    cometTail(sb, sx, sy, ndx, ndy, 56f * ds * dynTrail, 32f * ds, 0.15f, 0.03f, 0.22f, 0.4f * p)
    cometTail(sb, sx, sy, ndx, ndy, 44f * ds * dynTrail, 18f * ds, 0.45f, 0.12f, 0.6f, 0.5f * p)
    sb.fillOvalSoft(sx, sy, 28f * ds * dynGlow, 22f * ds * dynGlow, 0.2f, 0.02f, 0.3f, 0.26f * p, 0f, 18)

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

    // A comet tail of its light and a halo no wider than it needs (see energyBolt): speed lines,
    // a ribbon of soft strokes and two halos, one pulsing out to 70 units, were a smear and a fill cost
    cometTail(sb, sx, sy, ndx, ndy, 40f * ds * dynTrail, 22f * ds, 0.92f, 0.92f, 0.88f, 0.35f * p)
    sb.fillOvalSoft(sx, sy, 24f * ds * dynGlow, 19f * ds * dynGlow, 0.97f, 0.97f, 1f, 0.3f * p, 0f, 18)

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

    // A comet tail of its light and a halo no wider than it needs (see energyBolt): speed lines,
    // a ribbon of soft strokes and two halos, one pulsing out to 70 units, were a smear and a fill cost
    cometTail(sb, sx, sy, ndx, ndy, 50f * ds * dynTrail, 32f * ds, 0.3f, 0.75f, 0.15f, 0.3f * p)
    cometTail(sb, sx, sy, ndx, ndy, 40f * ds * dynTrail, 18f * ds, 0.55f, 0.95f, 0.4f, 0.5f * p)
    sb.fillOvalSoft(sx, sy, 28f * ds * dynGlow, 22f * ds * dynGlow, 0.35f, 0.8f, 0.2f, 0.26f * p, 0f, 18)

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

  // ═══════════════════════════════════════════════════════════════
  //  TETHERS: A PULL IS TIED TO WHOEVER IS PULLING
  // ═══════════════════════════════════════════════════════════════
  //
  // A grab that flies free — a hand, a paw, a claw with nothing behind it — reads as a glove
  // somebody threw, and that is what the old ones were: a brown mitten on a box-shaped cuff for
  // the Bear, the Gorilla and the Griffin, and a round knot with three sausage fingers for the
  // Kraken, the Spaceman, the Druid, the Thornweaver, the Treant, the Death Knight and the
  // Gravedigger. Every pull is drawn tied back to its thrower instead: a tentacle out of the
  // Kraken, a vine out of the Druid, a leash of spirit out of the Bear, a chain out of the Death
  // Knight, a rope out of the Gladiator's hand. GLGameRenderer says where the thrower is standing
  // (setAnchor); when it can't — they have died, or it is a dev tool with no players in it — the
  // tether runs back to where the projectile started (Projectile.originX/Y). The thing that hits
  // is still at (sx, sy); the tether only trails it, so it never arrives before the damage does.

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

  private val TETH_TENTACLE = 0
  private val TETH_VINE = 1
  private val TETH_PAW = 2     // a spirit paw on a leash (bear hug, primate grab)
  private val TETH_TALON = 3   // a spirit talon on a leash (the griffin)
  private val TETH_DEATH = 4   // a sickle hook on a spectral chain (death grip)

  private val TETHER_MAX = 40
  private val _tX = new Array[Float](TETHER_MAX)   // points from the head (0) back to the thrower
  private val _tY = new Array[Float](TETHER_MAX)
  private val _tNX = new Array[Float](TETHER_MAX)  // unit normal at each
  private val _tNY = new Array[Float](TETHER_MAX)
  private val _tT = new Array[Float](TETHER_MAX)   // 0 at the head, 1 at the thrower
  private val _tW = new Array[Float](TETHER_MAX)   // the limb's own width at each
  private val _tA = new Array[Float](TETHER_MAX)   // and its alpha
  private var _tN = 0
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
  private def layTether(proj: Projectile, sx: Float, sy: Float, phase: Double, spacing: Float, bow: Float,
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
  private def shapeTether(w0: Float, w1: Float, swell: Float, a: Float, fadeIn: Float): Unit = {
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
  private def tether(kind: Int, r: Float, g: Float, b: Float): Renderer =
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
  private val DEATH_HOOK_PARTS: Array[Part] = {
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

  // The hands of the grasping dead, sorted far to near before they are drawn
  private val _ghX = new Array[Float](6)
  private val _ghY = new Array[Float](6)
  private val _ghR = new Array[Float](6)
  private val _ghA = new Array[Float](6)

  /**
   * Gravedigger's Grasping Dead: the dead clawing up out of the ground along the path — a hand
   * bursting out at the hitbox, reaching, and behind it the ones it has passed sinking back into
   * their graves. The hands stand on points fixed to the ground along the flight line, as the
   * lightning's kinks do, so each stays where it came up. It used to be the Kraken's tentacle in
   * bone white: a three-fingered glove with nothing to do with graves.
   */
  private def drawGraspingDead(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 29) * 0.35
    computeAllDynamics(proj, 0.8f, 0.76f, 0.64f, phase)
    val p = dynAlpha
    // A size up from the other grabs: a hand out of the ground is small beside a character
    val ds = Math.min(dynScale, 1.3f) * 1.3f
    val wl = Math.sqrt(proj.dx * proj.dx + proj.dy * proj.dy).toFloat
    val ux = if (wl > 1e-4f) proj.dx / wl else 1f
    val uy = if (wl > 1e-4f) proj.dy / wl else 0f
    val esx = (ux - uy) * 20f; val esy = (ux + uy) * 10f
    val spp = Math.max(1f, Math.sqrt(esx * esx + esy * esy).toFloat)
    val ndx = esx / spp; val ndy = esy / spp
    val nx = -ndy; val ny = ndx
    val along = proj.getX * ux + proj.getY * uy
    val gap = 15f * ds
    val stepW = gap / spp
    val grip = 0.5f + 0.5f * Math.sin(phase * 1.6).toFloat

    // The furrow it tears along the ground behind it
    fadeLine(sb, sx, sy + 1f, sx - ndx * gap * 4.4f, sy - ndy * gap * 4.4f + 1f, 7f * ds, 0.2f, 0.14f, 0.08f, 0.5f * p, 4)
    // A sickly grave-light round the one at the head
    sb.fillOvalSoft(sx, sy - 10f * ds, 20f * ds, 16f * ds, 0.45f, 0.95f, 0.5f, 0.22f * p, 0f, 12)

    // The hand at the head, fully up, and the four behind it sinking
    _ghX(0) = sx; _ghY(0) = sy; _ghR(0) = 1f; _ghA(0) = p
    var n = 1
    val k0 = Math.floor(along / stepW).toInt
    var j = 0
    while (j < 5) {
      val k = k0 - j
      val d = (along - k * stepW) * spp
      val age = d / (gap * 4.4f)
      if (d > gap * 0.55f && age < 1f) {
        val lat = hash2(proj.id * 17, k) * 5f * ds
        _ghX(n) = sx - ndx * d + nx * lat; _ghY(n) = sy - ndy * d + ny * lat
        _ghR(n) = 0.75f * (1f - age * 0.7f); _ghA(n) = p * (1f - age * 0.6f)
        n += 1
      }
      j += 1
    }
    // Far to near, so a nearer grave covers a further one
    var i = 1
    while (i < n) {
      var m = i
      while (m > 0 && _ghY(m - 1) > _ghY(m)) {
        var tmp = _ghX(m); _ghX(m) = _ghX(m - 1); _ghX(m - 1) = tmp
        tmp = _ghY(m); _ghY(m) = _ghY(m - 1); _ghY(m - 1) = tmp
        tmp = _ghR(m); _ghR(m) = _ghR(m - 1); _ghR(m - 1) = tmp
        tmp = _ghA(m); _ghA(m) = _ghA(m - 1); _ghA(m - 1) = tmp
        m -= 1
      }
      i += 1
    }
    i = 0
    while (i < n) {
      val head = _ghR(i) >= 1f
      deadHand(sb, _ghX(i), _ghY(i), _ghR(i), ndx * (if (head) 0.35f else 0.15f), if (head) grip else 0.3f,
        _ghA(i), ds * (if (head) 1.25f else 0.95f))
      i += 1
    }
    // Clods of earth thrown up off the one at the head
    var c = 0
    while (c < 4) {
      val t = ((tick * 0.06 + c * 0.25 + proj.id * 0.13) % 1.0).toFloat
      val side = if ((c & 1) == 0) 1f else -1f
      val cx = sx + nx * side * (5f + t * 10f) * ds - ndx * t * 6f * ds
      val cy = sy + ny * side * (5f + t * 10f) * ds - (Math.sin(t * Math.PI) * 14f * ds).toFloat
      val cs = (2.6f - t * 1.2f) * ds
      sb.fillOval(cx, cy, cs + 0.9f, cs * 0.8f + 0.9f, 0.1f, 0.07f, 0.04f, 0.8f * (1f - t) * p, 6)
      sb.fillOval(cx, cy, cs, cs * 0.8f, 0.42f, 0.3f, 0.17f, (1f - t) * p, 6)
      c += 1
    }
    drawChargeCrackle(sx, sy - 12f * ds, 16f * ds, 0.8f, 0.76f, 0.64f, p, sb, phase, proj.chargeLevel)
  }

  /** One of the dead: a mound of turned earth with a skeletal hand and forearm `rise` of the way
   *  up out of it, leaning `lean` (screen x per unit of height), fingers open by `open`. */
  private def deadHand(sb: ShapeBatch, x: Float, y: Float, rise: Float, lean: Float, open: Float,
                       a: Float, s: Float): Unit = {
    if (a <= 0.01f) return
    sb.fillOval(x, y + 1f, 9f * s + 1.6f, 3.8f * s + 1.6f, 0.1f, 0.07f, 0.04f, 0.85f * a, 12)
    sb.fillOval(x, y + 1f, 9f * s, 3.8f * s, 0.36f, 0.26f, 0.15f, a, 12)
    sb.fillOval(x - 1.6f * s, y, 6f * s, 2.2f * s, 0.5f, 0.38f, 0.23f, a, 10)
    sb.fillOval(x, y + 0.6f * s, 4.6f * s, 1.7f * s, 0.07f, 0.05f, 0.03f, 0.9f * a, 10)
    if (rise < 0.06f) return
    if (rise < 0.8f) {
      // Sinking back: only the fingers still out of the ground, curled like a claw
      var f = 0
      while (f < 4) {
        val fx = x + (f - 1.5f) * 2.4f * s
        val up = (5f + 3f * (1 - Math.abs(f - 1.5f) / 1.5f)) * s * (0.4f + 0.6f * rise)
        val bend = (if (f < 2) 1f else -1f) * 2.6f * s * rise
        sb.strokeLine(fx, y, fx + bend * 0.3f, y - up, 2.6f * s, 0.12f, 0.1f, 0.08f, 0.9f * a)
        sb.strokeLine(fx + bend * 0.3f, y - up, fx + bend, y - up * 0.72f, 2.3f * s, 0.12f, 0.1f, 0.08f, 0.9f * a)
        sb.strokeLine(fx, y, fx + bend * 0.3f, y - up, 1.4f * s, 0.93f, 0.9f, 0.8f, a)
        sb.strokeLine(fx + bend * 0.3f, y - up, fx + bend, y - up * 0.72f, 1.2f * s, 0.93f, 0.9f, 0.8f, a)
        f += 1
      }
      return
    }
    val h = 16f * s * rise
    val wx = x + lean * h; val wy = y - h
    val br = 0.93f; val bg = 0.9f; val bb = 0.8f
    val ir = 0.12f; val ig = 0.1f; val ib = 0.08f
    sb.strokeLine(x, y, wx, wy, 4.8f * s, ir, ig, ib, 0.9f * a)
    sb.strokeLine(x, y, wx, wy, 3f * s, br, bg, bb, a)
    // Fingers: four spread up and out, each in two joints, the tips curling in as it closes
    var pass = 0
    while (pass < 2) {
      var f = 0
      while (f < 5) {
        val thumb = f == 4
        val base = if (thumb) -Math.PI / 2 - 1.05 else -Math.PI / 2 + (f - 1.5) * 0.38 * (0.55 + 0.45 * open)
        val seg1 = (if (thumb) 4f else 6f) * s * rise
        val seg2 = (if (thumb) 3f else 5f) * s * rise
        val curl = (0.35 + 0.8 * (1 - open)) * (if (f < 2 || thumb) 1.0 else -1.0)
        val kx = wx + Math.cos(base).toFloat * seg1; val ky = wy + Math.sin(base).toFloat * seg1
        val tx = kx + Math.cos(base + curl).toFloat * seg2; val ty = ky + Math.sin(base + curl).toFloat * seg2
        if (pass == 0) {
          sb.strokeLine(wx, wy, kx, ky, 2.9f * s, ir, ig, ib, 0.9f * a)
          sb.strokeLine(kx, ky, tx, ty, 2.5f * s, ir, ig, ib, 0.9f * a)
        } else {
          sb.strokeLine(wx, wy, kx, ky, 1.6f * s, br, bg, bb, a)
          sb.strokeLine(kx, ky, tx, ty, 1.3f * s, br, bg, bb, a)
        }
        f += 1
      }
      if (pass == 0) sb.fillOval(wx, wy, 3.2f * s + 1.3f, 2.6f * s + 1.3f, ir, ig, ib, 0.9f * a, 10)
      else sb.fillOval(wx, wy, 3.2f * s, 2.6f * s, br, bg, bb, a, 10)
      pass += 1
    }
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
  //  REGISTRY (all 176 types)
  // ═══════════════════════════════════════════════════════════════

  private val registry: Map[Byte, Renderer] = Map(
    // ── Original (0-30) ──
    ProjectileType.NORMAL       -> asRenderer(drawNormal),
    ProjectileType.TENTACLE     -> tether(TETH_TENTACLE, 0.24f, 0.74f, 0.44f),
    ProjectileType.ICE_BEAM     -> (drawFrostComet _),
    ProjectileType.AXE          -> bladeSpinner(WPN_AXE, 0.78f, 0.70f, 0.55f, 30f),
    ProjectileType.ROPE         -> chainProj(CHN_ROPE, 0.72f, 0.54f, 0.30f, 6f),
    ProjectileType.SPEAR        -> flyingShaft(SHF_SPEAR, 0.95f, 0.82f, 0.42f, 28f),
    ProjectileType.SOUL_BOLT    -> energyBolt(0.30f, 0.84f, 0.82f, 20f, ORB_SPIRIT),
    ProjectileType.HAUNT        -> energyBolt(0.5f, 0.7f, 0.95f, 24f, ORB_SPIRIT),
    ProjectileType.ARCANE_BOLT  -> energyBolt(0.65f, 0.3f, 0.95f, 22f, ORB_RUNE),
    ProjectileType.FIREBALL     -> energyBolt(1f, 0.46f, 0.08f, 28f, ORB_FIRE),
    ProjectileType.SPLASH       -> aoeRing(AOE_WATER, 0.32f, 0.62f, 1f, 46f),
    ProjectileType.TIDAL_WAVE   -> wave(WAV_WATER, 0.35f, 0.66f, 1f, 40f),
    ProjectileType.GEYSER       -> asRenderer(drawGeyser),
    ProjectileType.BULLET       -> bulletProj(0.75f, 0.7f, 0.55f),
    ProjectileType.GRENADE      -> lobbed(LOB_BOMB, 0.95f, 0.62f, 0.25f, 17f),
    ProjectileType.ROCKET       -> asRenderer(drawRocket),
    ProjectileType.TALON        -> asRenderer(drawTalon),
    ProjectileType.GUST         -> wave(WAV_WIND, 0.82f, 0.92f, 1f, 33f),
    ProjectileType.SHURIKEN     -> shuriken(0.72f, 0.74f, 0.82f, 30f),
    ProjectileType.POISON_DART  -> flyingShaft(SHF_DART, 0.4f, 0.9f, 0.35f, 26f),
    ProjectileType.CHAIN_BOLT   -> chainProj(CHN_SHACKLE, 0.62f, 0.64f, 0.72f, 5.5f),
    ProjectileType.LOCKDOWN_CHAIN -> chainProj(CHN_LOCK, 0.52f, 0.54f, 0.62f, 6f),
    ProjectileType.SNARE_MINE   -> lobbed(LOB_MINE, 0.35f, 0.7f, 1f, 17f),
    ProjectileType.KATANA       -> bladeSpinner(WPN_KATANA, 0.78f, 0.84f, 0.95f, 32f),
    ProjectileType.SWORD_WAVE   -> asRenderer(drawSwordWave),
    ProjectileType.PLAGUE_BOLT  -> energyBolt(0.52f, 0.82f, 0.12f, 20f, ORB_TOXIC),
    ProjectileType.MIASMA       -> aoeRing(AOE_TOXIC, 0.42f, 0.72f, 0.18f, 42f),
    ProjectileType.BLIGHT_BOMB  -> lobbed(LOB_FLASK, 0.45f, 0.8f, 0.2f, 17f),
    ProjectileType.BLOOD_FANG   -> asRenderer(drawBloodFang),
    ProjectileType.BLOOD_SIPHON -> siphonVortex(SIPH_BLOOD, 0.88f, 0.12f, 0.14f, 19f),
    ProjectileType.BAT_SWARM    -> asRenderer(drawBatSwarm),

    // ── Elemental (31-52) ──
    ProjectileType.FLAME_BOLT   -> energyBolt(1f, 0.5f, 0.1f, 20f, ORB_FIRE),
    ProjectileType.FROST_SHARD  -> flyingShaft(SHF_ICE, 0.55f, 0.85f, 1f, 22f),
    ProjectileType.LIGHTNING    -> lightningBolt(1f, 0.90f, 0.30f),
    ProjectileType.CHAIN_LIGHTNING -> lightningBolt(0.95f, 0.85f, 0.35f, heavy = true),
    ProjectileType.THUNDER_STRIKE -> asRenderer(drawThunderStrike),
    ProjectileType.BOULDER      -> asRenderer((proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int) => drawBoulder(proj, sx, sy, sb, tick, 24f)),
    ProjectileType.SEISMIC_SLAM -> aoeRing(AOE_QUAKE, 0.68f, 0.50f, 0.28f, 50f),
    ProjectileType.WIND_BLADE   -> wave(WAV_WIND, 0.78f, 0.96f, 1f, 31f),
    ProjectileType.MAGMA_BALL   -> energyBolt(1f, 0.45f, 0.05f, 24f, ORB_FIRE),
    ProjectileType.ERUPTION     -> aoeRing(AOE_FIRE, 1f, 0.45f, 0.05f, 46f),
    ProjectileType.FROST_TRAP   -> lobbed(LOB_ICE, 0.62f, 0.85f, 1f, 17f),
    ProjectileType.SAND_SHOT    -> energyBolt(0.9f, 0.75f, 0.4f, 18f, ORB_SAND),
    ProjectileType.SAND_BLAST   -> wave(WAV_SAND, 0.88f, 0.76f, 0.42f, 30f),
    ProjectileType.THORN        -> flyingShaft(SHF_THORN, 0.42f, 0.75f, 0.28f, 26f),
    ProjectileType.VINE_WHIP    -> tether(TETH_VINE, 0.30f, 0.62f, 0.20f),
    ProjectileType.THORN_WALL   -> aoeRing(AOE_NATURE, 0.30f, 0.62f, 0.18f, 34f),
    ProjectileType.INFERNO_BLAST -> asRenderer(drawInfernoBlast),
    ProjectileType.GLACIER_SPIKE -> flyingShaft(SHF_ICE, 0.60f, 0.88f, 1f, 27f),
    ProjectileType.MUD_GLOB     -> energyBolt(0.4f, 0.3f, 0.15f, 22f, ORB_MUD),
    ProjectileType.MUD_BOMB     -> lobbed(LOB_GLOB, 0.40f, 0.30f, 0.14f, 18f),
    ProjectileType.EMBER_SHOT   -> energyBolt(1f, 0.55f, 0.15f, 18f, ORB_FIRE),
    ProjectileType.AVALANCHE_CRUSH -> lobbed(LOB_ICE, 0.72f, 0.85f, 0.98f, 40f),

    // ── Undead/Dark (53-69) ──
    ProjectileType.DEATH_BOLT   -> energyBolt(0.36f, 0.16f, 0.52f, 22f, ORB_ORBIT),
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
    ProjectileType.CHARM        -> energyBolt(1f, 0.38f, 0.62f, 18f, ORB_HEART),
    ProjectileType.CARD         -> bladeSpinner(WPN_CARD, 0.92f, 0.25f, 0.32f, 26f, 0.22),

    // ── Sci-Fi/Tech (80-89) ──
    ProjectileType.DATA_BOLT    -> asRenderer((proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int) => drawDataBolt(proj, sx, sy, sb, tick, isVirus = false)),
    ProjectileType.VIRUS        -> asRenderer((proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int) => drawDataBolt(proj, sx, sy, sb, tick, isVirus = true)),
    ProjectileType.LASER        -> laserBolt(BOLT_LASER, 1f, 0.25f, 0.2f, 40f, 9f),
    ProjectileType.GRAVITY_BALL -> asRenderer(drawGravityBall),
    ProjectileType.GRAVITY_WELL -> asRenderer(drawGravityBall),
    ProjectileType.TESLA_COIL   -> lightningBolt(0.35f, 0.85f, 1f),
    ProjectileType.NANO_BOLT    -> energyBolt(0.25f, 0.85f, 0.65f, 16f, ORB_SWARM),
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
    ProjectileType.STING        -> energyBolt(0.45f, 0.9f, 1f, 16f, ORB_SHOCK),
    ProjectileType.HAMMER       -> lobbed(LOB_HAMMER, 0.60f, 0.62f, 0.70f, 27f),
    ProjectileType.HORN         -> asRenderer(drawHorn),
    ProjectileType.MYSTIC_BOLT  -> energyBolt(0.92f, 0.26f, 0.70f, 20f, ORB_RUNE),
    ProjectileType.PETRIFY      -> gorgonEye(petrify = true),
    ProjectileType.GRAB         -> tether(TETH_PAW, 0.98f, 0.64f, 0.26f),
    ProjectileType.JAW          -> asRenderer(drawJaw),
    ProjectileType.TONGUE       -> (drawTongueLash _),
    ProjectileType.ACID_FLASK   -> asRenderer(drawVenomBolt),

    // ── Roster audit: new differentiation projectiles (112+) ──
    ProjectileType.BOOMERANG_BLADE -> bladeSpinner(WPN_CURSED, 0.90f, 0.18f, 0.28f, 30f),
    ProjectileType.VORTEX_BOMB     -> aoeRing(AOE_VOID, 0.55f, 0.25f, 0.85f, 42f),
    ProjectileType.CHAIN_LIGHTNING_FORK -> lightningBolt(1f, 0.92f, 0.42f, heavy = true),
    ProjectileType.SNIPER_BEAM     -> railSlug(0.40f, 0.70f, 1f, 11f),
    ProjectileType.MOMENTUM_STRIKE -> wave(WAV_IMPACT, 0.97f, 0.62f, 0.22f, 30f),
    ProjectileType.LEECH_BOLT      -> energyBolt(0.88f, 0.09f, 0.20f, 22f, ORB_ORBIT),
    ProjectileType.RICOCHET_SHARD  -> flyingShaft(SHF_ICE, 0.62f, 0.90f, 1f, 21f),
    ProjectileType.FLAME_WAVE      -> wave(WAV_FLAME, 1f, 0.5f, 0.1f, 35f),
    ProjectileType.POISON_CLOUD    -> aoeRing(AOE_TOXIC, 0.36f, 0.80f, 0.24f, 46f),
    ProjectileType.BONE_BOOMERANG  -> bladeSpinner(WPN_BONE, 0.94f, 0.92f, 0.84f, 24f, 0.34),
    ProjectileType.GRAVITY_LANCE   -> flyingShaft(SHF_LANCE, 0.55f, 0.25f, 0.85f, 26f),
    ProjectileType.SHADOW_HAUNT    -> asRenderer(drawVoidBolt),
    ProjectileType.CHARGE_FIST     -> fistProj(0.85f, 0.65f, 0.35f, 28f),
    ProjectileType.ACID_SPRAY      -> wave(WAV_ACID, 0.35f, 0.9f, 0.32f, 29f),
    ProjectileType.ECHO_BOLT       -> energyBolt(0.45f, 0.55f, 1f, 20f, ORB_RUNE),
    ProjectileType.FLAME_TRAIL     -> energyBolt(1f, 0.6f, 0.15f, 22f, ORB_FIRE),
    ProjectileType.STAR_BOLT       -> energyBolt(1f, 0.78f, 0.22f, 22f, ORB_ASTRAL),
    ProjectileType.RUNE_BOLT       -> energyBolt(0.32f, 0.58f, 0.98f, 18f, ORB_RUNE),
    ProjectileType.SOUL_HARVEST    -> aoeRing(AOE_VOID, 0.34f, 0.90f, 0.40f, 42f),
    ProjectileType.OVERCLOCK_BEAM  -> aoeRing(AOE_SONIC, 0.22f, 0.95f, 0.75f, 40f),
    ProjectileType.NAPALM_STRIKE   -> lobbed(LOB_BOMB, 1f, 0.5f, 0.12f, 20f),
    ProjectileType.THROWN_BOULDER  -> asRenderer((proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int) => drawBoulder(proj, sx, sy, sb, tick, 28f)),
    ProjectileType.EYE_BEAM        -> laserBolt(BOLT_EYE, 1f, 0.35f, 0.15f, 42f, 11f),

    // ── DPS balance variants (same visual as base type) ──
    ProjectileType.SOUL_BOLT_HEAVY   -> energyBolt(0.30f, 0.84f, 0.82f, 20f, ORB_SPIRIT),
    ProjectileType.FROST_SHARD_LIGHT -> flyingShaft(SHF_ICE, 0.55f, 0.85f, 1f, 20f),
    ProjectileType.FLAME_BOLT_HEAVY  -> energyBolt(1f, 0.5f, 0.1f, 20f, ORB_FIRE),
    ProjectileType.FLAME_BOLT_LIGHT  -> energyBolt(1f, 0.5f, 0.1f, 20f, ORB_FIRE),
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
    ProjectileType.VENOM_BOLT_LIGHT  -> asRenderer(drawVenomBolt),

    // ── Plan 5a: melee and skirmisher kits (150-169), on existing looks ──
    ProjectileType.SHOCKWAVE       -> aoeRing(AOE_SONIC, 1f, 0.90f, 0.55f, 44f),
    ProjectileType.ICE_QUAKE       -> aoeRing(AOE_QUAKE, 0.62f, 0.85f, 1f, 46f),
    ProjectileType.HOWL            -> aoeRing(AOE_SONIC, 0.72f, 0.78f, 0.90f, 60f),
    ProjectileType.FERAL_ROAR      -> aoeRing(AOE_SONIC, 0.95f, 0.62f, 0.25f, 44f),
    ProjectileType.EARTHSPLITTER   -> wave(WAV_IMPACT, 0.62f, 0.48f, 0.28f, 34f),
    ProjectileType.GRASPING_DEAD   -> asRenderer(drawGraspingDead),
    ProjectileType.DEATH_GRIP      -> tether(TETH_DEATH, 0.46f, 0.2f, 0.64f),
    ProjectileType.TALON_GRAB      -> tether(TETH_TALON, 1f, 0.8f, 0.34f),
    ProjectileType.PARALYTIC_STING -> asRenderer(drawStinger),
    ProjectileType.HAMMER_THROW    -> lobbed(LOB_HAMMER, 0.60f, 0.62f, 0.70f, 30f),
    ProjectileType.MEAT_HOOK       -> chainProj(CHN_HOOK, 0.80f, 0.80f, 0.84f, 6f),
    ProjectileType.FLAMBE          -> energyBolt(1f, 0.55f, 0.15f, 22f, ORB_FIRE),
    ProjectileType.HOLY_NOVA       -> asRenderer(drawHolyBolt),
    ProjectileType.AXE_SPIN        -> bladeSpinner(WPN_AXE, 0.78f, 0.70f, 0.55f, 30f),
    ProjectileType.VENOM_DART      -> flyingShaft(SHF_DART, 0.4f, 0.9f, 0.35f, 26f),
    ProjectileType.TOXIC_SHURIKEN  -> shuriken(0.45f, 0.78f, 0.40f, 30f),
    ProjectileType.BLOOD_FRENZY    -> asRenderer(drawClawSwipe),
    ProjectileType.FLURRY          -> fistProj(0.75f, 0.6f, 0.45f, 28f),
    ProjectileType.ROOTING_BOULDER -> asRenderer((proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int) => drawBoulder(proj, sx, sy, sb, tick, 24f)),
    ProjectileType.EMBER_FAN       -> energyBolt(1f, 0.55f, 0.15f, 18f, ORB_FIRE),
    // ── Plan 5a: primaries with an identity of their own (170-175), the base type's look ──
    ProjectileType.FENRIR_CLAW     -> asRenderer(drawClawSwipe),
    ProjectileType.GHOUL_CLAW      -> asRenderer(drawClawSwipe),
    ProjectileType.SHARK_CLAW      -> asRenderer(drawClawSwipe),
    ProjectileType.ICE_BOULDER     -> asRenderer((proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int) => drawBoulder(proj, sx, sy, sb, tick, 24f)),
    ProjectileType.DRAIN_BLADE     -> bladeSpinner(WPN_CURSED, 0.90f, 0.18f, 0.28f, 30f),
    ProjectileType.CHILL_BLADE     -> bladeSpinner(WPN_CURSED, 0.90f, 0.18f, 0.28f, 30f)
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

  /** Spaceman's charge shot: a plasma orb in the player's colour — the orbs' lit sphere and comet
   *  tail (see energyBolt) — with energy rings spinning round it. It used to be a wobbling 13px
   *  tube running seven world units ahead of the hitbox to an orb, the roster's most visible
   *  snake; then a string of beads behind a disc, which was a caterpillar. */
  private def drawNormal(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 37) * 0.3
    intToRGB(proj.colorRGB)
    val r = _r; val g = _g; val b = _b
    computeAllDynamics(proj, r, g, b, phase)
    val p = (0.95f + 0.05f * Math.sin(phase * 2 * _stPulseMult).toFloat) * dynAlpha
    val dr = _evoR; val dg = _evoG; val db = _evoB
    val ds = dynScale
    val R = 10.5f * ds
    val hw = 0.5f + _chgBright
    val cr = mix(bright(r), 1f, hw); val cg = mix(bright(g), 1f, hw); val cb = mix(bright(b), 1f, hw)
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val across = 2f * R * Math.sqrt(ndy * ndy + 0.85f * ndx * ndx).toFloat
    val tail = R * 5.2f * dynTrail * (0.93f + 0.07f * Math.sin(phase * 2.3).toFloat)
    cometTail(sb, sx, sy, ndx, ndy, tail * 1.12f, across * 1.25f, dr, dg, db, 0.28f * p)
    cometTail(sb, sx, sy, ndx, ndy, tail, across * 0.9f, mix(dr, cr, 0.3f), mix(dg, cg, 0.3f), mix(db, cb, 0.3f), 0.65f * p)
    cometTail(sb, sx, sy, ndx, ndy, tail * 0.75f, across * 0.4f, cr, cg, cb, 0.85f * p)
    sb.fillOvalSoft(sx, sy, R * 2.2f * dynGlow, R * 2f * dynGlow, dr, dg, db, 0.32f * p, 0f, 16)
    orbBody(sb, sx, sy, R, R * 0.92f, dr, dg, db, p, 1f + 0.15f * Math.sin(phase * 2.3).toFloat + _chgBright)
    val ringA = (phase * 1.9).toFloat
    sb.strokeArc(sx, sy, R * 1.55f, R * 1.55f * ISO_Y, ringA, 2.4f, 2.2f, cr, cg, cb, 0.75f * p, 10)
    sb.strokeArc(sx, sy, R * 1.55f, R * 1.55f * ISO_Y, ringA + 3.1416f, 2.4f, 2.2f, cr, cg, cb, 0.75f * p, 10)
    sb.strokeArc(sx, sy, R * 1.2f * ISO_Y, R * 1.35f, -ringA * 0.8f, 2.0f, 1.6f, cr, cg, cb, 0.55f * p, 10)
    shedMotes(sb, sx, sy, ndx, ndy, R, 3, cr, cg, cb, p, tick, proj.id)
    drawChargeCrackle(sx, sy, R * 1.8f, r, g, b, p, sb, phase, proj.chargeLevel)
    drawReturnGhosts(sx, sy, R, dr, dg, db, p, sb, proj)
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

  // ── Electricity ──
  //
  // A bolt is a channel through the air, and the air doesn't move: its kinks are fixed to the
  // ground along the flight line, so as the head flies on it draws the channel and leaves it
  // standing behind, fading. It is straight runs between sharp kinks — a smooth meander, tried
  // first, is a worm — laid out as two levels of midpoint displacement: a kink of its own every
  // other node, and each node between knocked aside from the middle of its neighbours by a
  // crackle re-struck twenty times a second. The big kinks hold while the small ones flicker.
  //
  // The bolt this replaced was a zigzag built relative to the head and re-rolled whole every
  // three frames, with kinks up to 23px either side of segments 7px long: a sawtooth stick
  // carried along by the head, jumping to a new shape twenty times a second, every hairpin
  // corner mitred into a needle. That, and three prongs striking out ahead of the head like
  // the legs of a bug, was the glitch.

  private val BOLT_MAX = 16
  private val _bnX = new Array[Float](BOLT_MAX + 1)
  private val _bnY = new Array[Float](BOLT_MAX + 1)
  private val _bnT = new Array[Float](BOLT_MAX + 1)
  private val _brX = new Array[Float](5)
  private val _brY = new Array[Float](5)

  /** The kink at an even lattice node: on alternate sides of the flight line from one to the next,
    * by an uneven amount — the lightning-bolt zigzag, never the same twice, and at most ~55° off
    * the line, so no bend is sharp enough for its mitre to fold into a needle. */
  @inline private def kink(seed: Int, k: Int): Float = {
    val side = if ((Math.floorDiv(k, 2) & 1) == 0) 1f else -1f
    side * (5f + 6f * Math.abs(hash2(seed, k)))
  }

  private def lightningBolt(r: Float, g: Float, b: Float, heavy: Boolean = false): Renderer =
    (proj, sx, sy, sb, tick) => drawLightning(proj, sx, sy, sb, tick, r, g, b, heavy)

  /** A channel of `n` points, 0 at the head, stroked as glow, ink, colour and white core, each
   *  one mitred band narrowing and fading toward where the bolt has been (`ts` from 0 to 1). */
  private def strokeChannel(sb: ShapeBatch, xs: Array[Float], ys: Array[Float], ts: Array[Float], n: Int,
                            w: Float, r: Float, g: Float, b: Float, a: Float): Unit = {
    if (n < 2) return
    var layer = 0
    while (layer < 4) {
      var i = 0
      while (i < n) {
        val t = ts(i); val k = 1f - t
        (layer: @scala.annotation.switch) match {
          case 0 => _pvW(i) = w * 2.8f * (1f - 0.5f * t); _pvA(i) = 0.16f * a * k
          case 1 => _pvW(i) = w * (1f - 0.5f * t * t) + 4f; _pvA(i) = 0.85f * a * Math.min(1f, k * 1.5f)
          case 2 => _pvW(i) = w * (1f - 0.5f * t * t); _pvA(i) = a * Math.min(1f, k * 1.8f)
          case _ => _pvW(i) = w * 0.3f * (1f - 0.6f * t); _pvA(i) = a * Math.min(1f, k * 1.5f)
        }
        i += 1
      }
      (layer: @scala.annotation.switch) match {
        case 0 => sb.strokePolylineVar(xs, ys, _pvW, _pvA, n, r, g, b)
        case 1 => sb.strokePolylineVar(xs, ys, _pvW, _pvA, n, r * 0.16f, g * 0.16f, b * 0.22f + 0.05f)
        case 2 => sb.strokePolylineVar(xs, ys, _pvW, _pvA, n, r, g, b)
        case _ => sb.strokePolylineVar(xs, ys, _pvW, _pvA, n, mix(r, 1f, 0.8f), mix(g, 1f, 0.8f), mix(b, 1f, 0.8f))
      }
      layer += 1
    }
  }

  /**
   * Lightning: a crackling ball of light at the hitbox, and the channel it has just drawn left
   * standing behind it (see Electricity above), with a fork flashing off it now and then and
   * sparks spat out of it. `heavy` (chain lightning, the Tesla's fork) is thicker, reaches
   * further back, forks twice and crackles more round the head.
   */
  private def drawLightning(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int,
                            lr: Float, lg: Float, lb: Float, heavy: Boolean): Unit = {
    val phase = (tick + proj.id * 41) * 0.5
    computeAllDynamics(proj, lr, lg, lb, phase)
    val a = dynAlpha
    val ds = Math.min(dynScale, 1.35f)
    val hR = mix(lr, 1f, 0.55f); val hG = mix(lg, 1f, 0.55f); val hB = mix(lb, 1f, 0.55f)
    // Its flight line in world units, and how that lies on screen
    val wl = Math.sqrt(proj.dx * proj.dx + proj.dy * proj.dy).toFloat
    val ux = if (wl > 1e-4f) proj.dx / wl else 1f
    val uy = if (wl > 1e-4f) proj.dy / wl else 0f
    val esx = (ux - uy) * 20f; val esy = (ux + uy) * 10f
    val spp = Math.max(1f, Math.sqrt(esx * esx + esy * esy).toFloat) // screen units per world unit
    val ndx = esx / spp; val ndy = esy / spp
    val nx = -ndy; val ny = ndx
    // How far along its line it is: a coordinate fixed to the ground, which the kinks are keyed on
    val along = proj.getX * ux + proj.getY * uy
    val gap = 8f * ds
    val stepW = gap / spp
    val trail = (if (heavy) 84f else 70f) * ds
    val epoch = (tick + proj.id * 7) / 3
    val seed = proj.id * 131
    val strike = 0.9f + 0.1f * hash2(seed + 3, epoch)

    _bnX(0) = sx; _bnY(0) = sy; _bnT(0) = 0f
    var n = 1
    var k = Math.floor(along / stepW).toInt
    var more = true
    while (more && n < BOLT_MAX) {
      val d = (along - k * stepW) * spp
      if (d > trail) more = false
      else {
        if (d > gap * 0.4f) {
          val jag =
            if ((k & 1) == 0) kink(seed, k) + hash2(seed + 31 * epoch, k) * 1.5f
            else (kink(seed, k - 1) + kink(seed, k + 1)) * 0.5f + hash2(seed + 31 * epoch, k) * 2.8f
          // The kinks die away into the head, which is the hitbox
          val off = jag * ds * Math.min(1f, d / (gap * 2.2f))
          _bnX(n) = sx - ndx * d + nx * off; _bnY(n) = sy - ndy * d + ny * off
          _bnT(n) = d / trail
          n += 1
        }
        k -= 1
      }
    }
    val w = (if (heavy) 8.5f else 7f) * ds * strike
    strokeChannel(sb, _bnX, _bnY, _bnT, n, w, lr, lg, lb, a)

    // Forks: off a node partway back, forward and out the way the bolt was going, and never
    // reaching past the head. Not on every re-strike, so they flash in and out.
    var f = 0
    while (f < (if (heavy) 2 else 1)) {
      val hf = hash2(seed + 97, epoch * 3 + f)
      if (n > 5 && hf > -0.3f) {
        val at = 2 + (((hash2(seed + 13, epoch * 5 + f) + 1f) * 0.5f) * (n - 4)).toInt
        val side = if (hash2(seed + 29, epoch * 7 + f) > 0f) 1f else -1f
        val ang = 0.55f + 0.35f * (hash2(seed + 41, epoch * 11 + f) + 1f) * 0.5f
        val ca = Math.cos(ang).toFloat; val sa = Math.sin(ang).toFloat * side
        val fx = ndx * ca - ndy * sa; val fy = ndx * sa + ndy * ca
        val dAt = _bnT(at) * trail
        val flen = Math.min((16f + 12f * (hf + 1f) * 0.5f) * ds, (dAt - 4f) / ca)
        if (flen > 6f) {
          val qx = -fy; val qy = fx
          var i = 0
          while (i < 5) {
            val t = i / 4f
            val off = if (i == 0) 0f else hash2(seed + 53 + epoch, f * 7 + i) * 3.2f * ds
            _brX(i) = _bnX(at) + fx * flen * t + qx * off
            _brY(i) = _bnY(at) + fy * flen * t + qy * off
            i += 1
          }
          val fa = a * (1f - _bnT(at))
          val fw = w * 0.5f * (1f - _bnT(at) * 0.5f)
          sb.strokePolylineTapered(_brX, _brY, 5, fw + 3f, 1.8f, lr * 0.16f, lg * 0.16f, lb * 0.22f + 0.05f, 0.75f * fa, 0.1f * fa)
          sb.strokePolylineTapered(_brX, _brY, 5, fw, 0.5f, lr, lg, lb, 0.95f * fa, 0.2f * fa)
          sb.strokePolylineTapered(_brX, _brY, 5, fw * 0.4f, 0.3f, hR, hG, hB, fa, 0.2f * fa)
        }
      }
      f += 1
    }

    // Sparks spat sideways out of the channel, falling as they fade
    var s = 0
    while (s < 3 && n > 3) {
      val life = ((tick + proj.id * 3 + s * 3) % 9) / 9f
      val born = (tick + proj.id * 3 + s * 3) / 9
      val at = 1 + (((hash2(seed + 71, born * 3 + s) + 1f) * 0.5f) * (n - 2)).toInt
      val side = if (hash2(seed + 73, born + s) > 0f) 1f else -1f
      val ox = nx * side * 0.85f - ndx * 0.5f; val oy = ny * side * 0.85f - ndy * 0.5f
      val dist = (3f + life * 14f) * ds
      val px2 = _bnX(Math.min(at, n - 1)) + ox * dist
      val py2 = _bnY(Math.min(at, n - 1)) + oy * dist + life * life * 9f
      fadeLine(sb, px2, py2, px2 - ox * 5f * ds, py2 - oy * 5f * ds, 2.2f * ds, hR, hG, hB, 0.9f * (1f - life) * a, 2)
      s += 1
    }

    // The head: a ball of light with arcs crackling off it, re-struck with the channel. The arcs
    // run all round it and stop short — the three prongs this used to throw out ahead of the
    // head read as the legs of a bug.
    val hr = (if (heavy) 8f else 6.5f) * ds
    sb.fillOvalSoft(sx, sy, hr * 2.8f * dynGlow, hr * 2.4f * dynGlow, lr, lg, lb, 0.4f * a, 0f, 14)
    val arcs = if (heavy) 4 else 3
    var j = 0
    while (j < arcs) {
      val ang = (j * (2 * Math.PI / arcs) + hash2(seed + 61, epoch * 13 + j) * 0.9).toFloat
      val al = hr * (1.6f + 0.6f * hash2(seed + 67, epoch * 17 + j))
      val ca = Math.cos(ang).toFloat; val sa = Math.sin(ang).toFloat
      sparkArc(sb, sx + ca * hr * 0.5f, sy + sa * hr * 0.45f, sx + ca * al, sy + sa * al * 0.8f, 3, al * 0.22f,
        seed + epoch * 19 + j * 3, 2.8f * ds, lr, lg, lb, a)
      j += 1
    }
    sb.fillOvalSoft(sx, sy, hr * 1.25f, hr * 1.1f, lr, lg, lb, a, 0.2f * a, 14)
    sb.fillOvalSoft(sx, sy, hr * 0.7f, hr * 0.62f, 1f, 1f, 1f, a, 0.6f * a, 12)
    sb.fillStarFlare(sx, sy, hr * 3.2f * strike, 3f, (epoch * 0.7f) % 6.2832f, 0.5f, 1f, 1f, mix(lb, 1f, 0.6f), 0.9f * a)
    drawChargeCrackle(sx, sy, hr * 2f, lr, lg, lb, a, sb, phase, proj.chargeLevel)
  }

  // A thunder strike: its bolt and the puffs of its cloud
  private val _tsX = new Array[Float](17)
  private val _tsY = new Array[Float](17)
  private val CLOUD_PUFFS = Array(
    -18f, 3f, 9f,   -9f, -4f, 12f,   3f, -7f, 13f,   15f, -2f, 10f,   21f, 4f, 7.5f,   0f, 4f, 11f,   -10f, 5f, 9f)

  /**
   * Thunder Strike: a storm cloud rolling over the ground with lightning striking down out of it
   * onto the spot it is over — which is the projectile, so the strike lands on the hitbox. It
   * re-strikes fifteen times a second, every fifth strike a full flash, each laying a ring on the
   * ground. The old one was the same fixed zigzag on every frame, with its splash drawn 36px
   * below the hitbox.
   */
  private def drawThunderStrike(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 31) * 0.5
    computeAllDynamics(proj, 1f, 0.9f, 0.35f, phase)
    val a = dynAlpha
    val ds = Math.min(dynScale, 1.3f)
    val bc = _chgBright
    val lr = 1f; val lg = 0.9f; val lb = 0.38f
    val clock = tick + proj.id * 5
    val epoch = clock / 4
    val within = (clock % 4) / 4f
    val big = epoch % 5 == 0
    val flash = if (big) 1f else 0.62f + 0.2f * hash2(proj.id, epoch)
    val cx = sx + Math.sin(phase * 0.21).toFloat * 2f * ds
    val cy = sy - 84f * ds + Math.sin(phase * 0.33).toFloat * 2.5f * ds

    // On the ground: the cloud's shadow, the strike's light, and a ring from the last strike
    sb.fillOvalSoft(sx, sy + 2f, 34f * ds, 12f * ds, 0.05f, 0.05f, 0.08f, 0.32f * a, 0f, 18)
    sb.fillOvalSoft(sx, sy, 30f * ds * flash, 11f * ds * flash, lr, lg, lb, 0.5f * flash * a, 0f, 18)
    val rr = (6f + within * 22f) * ds
    sb.strokeOval(sx, sy, rr, rr * 0.38f, 3f * (1f - within) * ds, lr, lg, lb, 0.85f * (1f - within) * a, 18)

    // The bolt, from the cloud's belly down to the ground point: the lightning-bolt zigzag, a
    // kink to alternate sides at every other point by an uneven amount, and the points between
    // knocked aside a little afresh with every strike. Midpoint displacement, tried first, came
    // out as a thin wavering thread — not what a storm's strike is drawn as.
    val n = 9
    val topX = cx + hash2(proj.id + 1, epoch) * 5f * ds; val topY = cy + 12f * ds
    var i = 0
    while (i < n) {
      val t = i.toFloat / (n - 1)
      val kinkX =
        if (i == 0 || i == n - 1) 0f
        else if ((i & 1) == 1) (if (((i >> 1) & 1) == 0) 1f else -1f) * (6f + 5f * Math.abs(hash2(proj.id * 7 + epoch * 131, i)))
        else hash2(proj.id * 11 + epoch * 137, i) * 3f
      _tsX(i) = topX + (sx - topX) * t + kinkX * ds
      _tsY(i) = topY + (sy - topY) * t + hash2(proj.id * 13 + epoch * 139, i) * 2f * ds
      i += 1
    }
    val w = (if (big) 14f else 11f) * ds
    sb.strokePolylineTapered(_tsX, _tsY, n, w * 2.2f, w * 3f, lr, lg, lb, 0.16f * flash * a, 0.24f * flash * a)
    sb.strokePolylineTapered(_tsX, _tsY, n, w * 0.75f + 4f, w + 4f, 0.16f, 0.14f, 0.1f, 0.88f * a, 0.9f * a)
    sb.strokePolylineTapered(_tsX, _tsY, n, w * 0.75f, w, lr, lg, lb, a, a)
    sb.strokePolylineTapered(_tsX, _tsY, n, w * 0.3f, w * 0.38f, 1f, 1f, mix(0.85f, 1f, bc), a, a)
    // Forks down and away from it
    var f = 0
    while (f < (if (big) 2 else 1)) {
      val at = 3 + ((hash2(proj.id + 17, epoch * 3 + f) + 1f) * 1.5f).toInt
      val side = if (hash2(proj.id + 19, epoch * 5 + f) > 0f) 1f else -1f
      val flen = (18f + 8f * hash2(proj.id + 23, epoch * 7 + f)) * ds
      val fx = side * 0.72f; val fy = 0.69f
      var i = 0
      while (i < 5) {
        val t = i / 4f
        val off = if (i == 0) 0f else hash2(proj.id + 29 + epoch, f * 7 + i) * 3.4f * ds
        _brX(i) = _tsX(at) + fx * flen * t - fy * off
        _brY(i) = _tsY(at) + fy * flen * t + fx * off
        i += 1
      }
      sb.strokePolylineTapered(_brX, _brY, 5, w * 0.45f + 3f, 1.8f, 0.16f, 0.14f, 0.1f, 0.8f * a, 0.1f * a)
      sb.strokePolylineTapered(_brX, _brY, 5, w * 0.45f, 0.5f, lr, lg, lb, 0.95f * a, 0.2f * a)
      sb.strokePolylineTapered(_brX, _brY, 5, w * 0.18f, 0.3f, 1f, 1f, 0.9f, a, 0.2f * a)
      f += 1
    }
    // Where it lands: a white-hot flash, and sparks thrown up off it
    sb.fillOvalSoft(sx, sy, 10f * ds * flash, 5.5f * ds * flash, 1f, 1f, 0.9f, a, 0.3f * a, 12)
    sb.fillStarFlare(sx, sy - 2f, 20f * ds * flash, 2.8f, 0f, 0.45f, 1f, 1f, 0.9f, 0.85f * a)
    var k = 0
    while (k < 5) {
      val ang = -Math.PI * (0.12 + 0.76 * ((hash2(proj.id + 31, epoch * 7 + k) + 1f) * 0.5f))
      val ca = Math.cos(ang).toFloat; val sa = Math.sin(ang).toFloat
      val dist = (4f + within * 16f) * ds
      val px2 = sx + ca * dist; val py2 = sy + sa * dist * 0.7f + within * within * 10f * ds
      fadeLine(sb, px2, py2, px2 - ca * 5f * ds, py2 - sa * 3.5f * ds, 2f * ds, 1f, 0.95f, 0.6f,
        0.9f * (1f - within) * a, 2)
      k += 1
    }

    // The cloud, over the top of its own bolt: seven puffs inked as one, slate grey, lit on top
    // and lit from underneath by the strike
    var pass = 0
    while (pass < 3) {
      var i = 0
      while (i < CLOUD_PUFFS.length) {
        val pr = CLOUD_PUFFS(i + 2) * ds * 1.2f * (1f + 0.04f * Math.sin(phase * 0.9 + i).toFloat)
        val px2 = cx + CLOUD_PUFFS(i) * ds * 1.2f; val py2 = cy + CLOUD_PUFFS(i + 1) * ds * 1.2f
        (pass: @scala.annotation.switch) match {
          case 0 => sb.fillOval(px2, py2, pr + 2f, pr * 0.82f + 2f, 0.07f, 0.07f, 0.1f, 0.92f * a, 14)
          case 1 => sb.fillOval(px2, py2, pr, pr * 0.82f, 0.33f, 0.35f, 0.44f, 0.98f * a, 14)
          case _ => sb.fillOval(px2 - pr * 0.18f, py2 - pr * 0.26f, pr * 0.62f, pr * 0.5f, 0.5f, 0.52f, 0.61f, 0.9f * a, 12)
        }
        i += 3
      }
      pass += 1
    }
    sb.fillOvalSoft(cx, cy + 7f * ds, 24f * ds, 7f * ds, lr, lg, lb, 0.55f * flash * a, 0f, 14)
    if (hash2(proj.id + 37, epoch) > 0.2f)
      sparkArc(sb, cx - 12f * ds, cy + hash2(proj.id + 41, epoch) * 3f * ds, cx + 10f * ds, cy + 2f * ds, 4, 3f * ds,
        proj.id * 3 + epoch, 1.8f * ds, lr, lg, lb, 0.7f * a)
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
    // Fanned from the centre the vertex radii were measured from: the jagged hull is
    // non-convex, so fanning from vertex 0 cuts its own corners off.
    sb.fillFan(sx, sy - bounce, _shpXs, _shpYs, hullN, 0.42f, 0.34f, 0.22f, 0.97f * p)
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
      sb.fillRibbon(_scyXs, _scyYs, segs * 2 + 2, 0.4f, 0.42f, 0.48f, gAlpha)
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
    // Steel body (3-layer fill). A ribbon, not a polygon: a fanned crescent fills its own
    // hollow, which turned the scythe blade into a solid plate.
    sb.fillRibbon(_scyXs, _scyYs, segs * 2 + 2, 0.55f, 0.57f, 0.62f, 0.85f * p);
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
    sb.fillRibbon(_scyXs, _scyYs, segs * 2 + 2, 0.72f, 0.74f, 0.8f, 0.5f * p)

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
    // Steady (see energyBolt): it used to throb down to 20% alpha three times a second
    val p = (0.9 + 0.1 * Math.sin(phase * _stPulseMult)).toFloat * dynAlpha
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

    // 3 curved claw prongs — each with dark outline and red tip. The claws close ON the
    // hitbox: the knuckle sits a claw's length behind it and the points land at (sx, sy).
    // Run forward from the hitbox instead, as they were, and the talons that read as the
    // projectile arrive a third of a tile before the damage does.
    val tipDist = 28f * ds
    val kx = sx - ndx * tipDist; val ky = sy - ndy * tipDist

    { var c = -1; while (c <= 1) {
      val spreadAngle = c * 0.35
      val clawNdx = ndx * Math.cos(spreadAngle).toFloat - perpX * Math.sin(spreadAngle).toFloat
      val clawNdy = ndy * Math.cos(spreadAngle).toFloat - perpY * Math.sin(spreadAngle).toFloat
      val clawPerp = -clawNdy
      val clawPerpY = clawNdx
      val baseX = kx + perpX * c * 5f * ds; val baseY = ky + perpY * c * 5f * ds
      // Hooked, not bowed: the claw swings out at the middle and comes back at the point,
      // which is what separates a raptor's talon from a bent wire.
      val curve = (3.2f + Math.sin(phase * 0.5 + c).toFloat * 1.2f) * ds
      val midX = baseX + clawNdx * tipDist * 0.58f + clawPerp * curve * 1.6f
      val midY = baseY + clawNdy * tipDist * 0.58f + clawPerpY * curve * 1.6f
      val endX = baseX + clawNdx * tipDist + clawPerp * curve * 0.4f
      val endY = baseY + clawNdy * tipDist + clawPerpY * curve * 0.4f

      // Dark outline
      sb.strokeLine(baseX, baseY, midX, midY, 6.4f * ds, 0.11f, 0.07f, 0.05f, 0.92f * p)
      sb.strokeLine(midX, midY, endX, endY, 4.4f * ds, 0.11f, 0.07f, 0.05f, 0.92f * p)
      // Horn. Brown claws on brown ground were a low-contrast smudge on two of the three
      // terrain bands; pale horn inside a near-black line reads on all of them.
      sb.strokeLine(baseX, baseY, midX, midY, 4.2f * ds, BONE_R, BONE_G, BONE_B, 0.96f * p)
      sb.strokeLine(midX, midY, endX, endY, 2.4f * ds, BONE_R, BONE_G, BONE_B, 0.96f * p)
      // Blood-wet point
      sb.fillOval(endX, endY, 2.6f * ds, 2.1f * ds, 0.9f, 0.14f, 0.1f, 0.95f * p, 8)
    ; c += 1 } }

    // Foot behind the points, where the claws spring from — small, or it is a knob on a
    // stick and the claws read as its legs.
    sb.strokeOval(kx, ky, 6f * ds, 4.8f * ds, 2.6f, 0.11f, 0.07f, 0.05f, 0.85f * p, 12)
    sb.fillOval(kx, ky, 5.4f * ds, 4.2f * ds, dr * 0.8f, dg * 0.8f, db * 0.8f, 0.94f * p, 12)

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

    // Two curved fangs, biting down ON the hitbox: the jaw root sits a fang's length
    // behind it and the points land at (sx, sy). Run forward from the hitbox instead, as
    // they were, and the fangs arrive a third of a tile before the bite lands.
    val jawX = sx - ndx * 24f * ds; val jawY = sy - ndy * 24f * ds

    { var f = -1; while (f <= 1) {
      if (f != 0) {
        // A solid tapered triangle per fang, wide at the gum and converging to a needle
        // on the hitbox. Built as a wide dark stroke with a narrower ivory one inside, a
        // fang is a tube with an outline — and two tubes springing apart from one root
        // drew the sides of a rhombus, which is all this projectile used to read as.
        val gumX = jawX + perpX * f * 4.5f * ds
        val gumY = jawY + perpY * f * 4.5f * ds
        val tipX = sx + perpX * f * 1.5f * ds
        val tipY = sy + perpY * f * 1.5f * ds
        val axX = tipX - gumX; val axY = tipY - gumY
        val axL = Math.max(0.001f, Math.sqrt(axX * axX + axY * axY).toFloat)
        val ux = axX / axL; val uy = axY / axL
        @inline def fang(halfW: Float, over: Float, cr: Float, cg: Float, cb: Float, ca: Float): Unit = {
          _polyXs3(0) = tipX + ux * over;       _polyYs3(0) = tipY + uy * over
          _polyXs3(1) = gumX + perpX * halfW;   _polyYs3(1) = gumY + perpY * halfW
          _polyXs3(2) = gumX - perpX * halfW;   _polyYs3(2) = gumY - perpY * halfW
          sb.fillPolygon(_polyXs3, _polyYs3, 3, cr, cg, cb, ca * p)
        }
        fang(5f * ds, 1.8f * ds, 0.14f, 0.02f, 0.02f, 0.92f)
        fang(3.2f * ds, 0f, 0.94f, 0.91f, 0.85f, 0.96f)
        // Blood-wet point
        sb.fillOval(tipX, tipY, 2.4f * ds, 1.9f * ds, 0.9f, 0.08f, 0.05f, 0.95f * p, 8)
      }
    ; f += 2 } }

    // Center base (gum/jaw root)
    sb.strokeOval(jawX - ndx * 4f * ds, jawY - ndy * 4f * ds, 6.5f * ds, 5f * ds, 2.6f, 0.14f, 0.02f, 0.02f, 0.85f * p, 12)
    sb.fillOval(jawX - ndx * 4f * ds, jawY - ndy * 4f * ds, 5.8f * ds, 4.4f * ds, 0.6f, 0.08f, 0.08f, 0.92f * p, 12)
    sb.fillOval(jawX - ndx * 4f * ds, jawY - ndy * 4f * ds, 3f * ds, 2.4f * ds,
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
