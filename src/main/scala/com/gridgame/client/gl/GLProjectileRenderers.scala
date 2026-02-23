package com.gridgame.client.gl

import com.gridgame.common.model.{Projectile, ProjectileType}

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

  def drawGeneric(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int,
                  r: Float, g: Float, b: Float): Unit = {
    val phase = (tick + proj.id * 37) * 0.35
    val p = (0.65 + 0.35 * Math.sin(phase)).toFloat
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy

    // Speed lines
    drawSpeedLines(sx, sy, ndx, ndy, r, g, b, 0.25f * p, sb, 4, 28f)
    // Soft glow halo — bigger
    sb.fillOvalSoft(sx, sy, 50f, 40f, r, g, b, 0.38f * p, 0f, 18)
    // Dark cartoon outline
    sb.strokeOval(sx, sy, 24f, 18f, 3f, outline(r), outline(g), outline(b), 0.8f * p, 14)
    // Core
    sb.fillOval(sx, sy, 22f, 17f, r, g, b, 0.95f * p, 14)
    // Cartoon highlight
    sb.fillOval(sx - 4f, sy - 4f, 7f, 5f, 1f, 1f, 1f, 0.35f * p, 8)
    // Bright center
    sb.fillOval(sx, sy, 10f, 7f, bright(r), bright(g), bright(b), 0.98f, 10)
    // Trail — bigger, more particles
    var i = 0; while (i < 8) {
      val t = ((tick * 0.05 + i * 0.125 + proj.id * 0.13) % 1.0).toFloat
      val taper = 1f - t * 0.65f
      val s = 14f * taper
      val colorFade = 1f - t * 0.3f
      sb.fillOval(sx - ndx * t * 45, sy - ndy * t * 45, s, s * 0.7f,
        r * colorFade, g * colorFade, b * colorFade, 0.45f * (1f - t) * p, 8)
    ; i += 1 }
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

  /** Large glowing energy orb with spiral particles, ribbon trail and spark burst — CARTOONISH */
  private def energyBolt(r: Float, g: Float, b: Float, size: Float = 20f): Renderer =
    (proj, sx, sy, sb, tick) => {
      val phase = (tick + proj.id * 37) * 0.35
      val p = (0.65 + 0.35 * Math.sin(phase)).toFloat
      val chargeScale = 1f + proj.chargeLevel * 0.003f
      val sz = size * 1.4f * chargeScale

      screenDir(proj)
      val ndx = _sdx; val ndy = _sdy

      // Manga speed lines behind
      drawSpeedLines(sx, sy, ndx, ndy, r, g, b, 0.25f * p, sb, 5, sz * 1.5f)

      // Large soft halo — dramatic glow
      sb.fillOvalSoft(sx, sy, sz * 3.2f, sz * 2.6f, r, g, b, 0.4f * p, 0f, 18)

      // Bold dark cartoon outline
      sb.strokeOval(sx, sy, sz * 0.95f, sz * 0.72f, 3.5f,
        outline(r), outline(g), outline(b), 0.8f * p, 14)

      // Main orb body — saturated and solid
      sb.fillOval(sx, sy, sz * 0.9f, sz * 0.68f, r, g, b, 0.95f * p, 14)
      // Inner glow core offset in travel direction
      sb.fillOval(sx + ndx * sz * 0.1f, sy + ndy * sz * 0.1f, sz * 0.55f, sz * 0.4f,
        mix(r, 1f, 0.4f), mix(g, 1f, 0.3f), mix(b, 1f, 0.2f), 0.9f * p, 12)
      // Cartoon highlight (top-left specular spot)
      sb.fillOval(sx - sz * 0.15f, sy - sz * 0.12f, sz * 0.25f, sz * 0.18f,
        1f, 1f, 1f, 0.55f * p, 8)
      // Hot center
      sb.fillOval(sx, sy, sz * 0.22f, sz * 0.16f, bright(r), bright(g), bright(b), 0.98f, 8)

      // 6 spiral orbiting particles — bigger and brighter
      var s = 0; while (s < 6) {
        val sa = phase * 3.5 + s * Math.PI / 3
        val sd = sz * 0.55f + Math.sin(phase * 2 + s * 1.3).toFloat * sz * 0.12f
        val spx = sx + Math.cos(sa).toFloat * sd
        val spy = sy + Math.sin(sa).toFloat * sd * 0.55f
        sb.fillOval(spx, spy, 5f, 3.5f, bright(r), bright(g), bright(b),
          (0.5 + 0.4 * Math.sin(phase * 4 + s * 2.1)).toFloat * p, 8)
      ; s += 1 }

      // Ribbon trail — longer and denser
      drawRibbonTrail(sx, sy, ndx, ndy, r, g, b, p, sb, tick, proj.id,
        12, sz * 4.5f, sz * 0.45f, sz * 0.06f)

      // Spark burst at front — more particles
      drawSparkBurst(sx + ndx * sz * 0.6f, sy + ndy * sz * 0.6f,
        r, g, b, p, sb, tick, proj.id, 6, sz * 0.8f)

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
    }

  /** Thick solid beam with color gradient, tip flare, crackling sparks — CARTOONISH */
  private def beamProj(r: Float, g: Float, b: Float, worldLen: Float = 6f, width: Float = 8f): Renderer =
    (proj, sx, sy, sb, tick) => {
      beamTip(sx, sy, proj, worldLen)
      val tipX = _tipX; val tipY = _tipY
      val phase = (tick + proj.id * 29) * 0.35
      val p = (0.7 + 0.3 * Math.sin(phase)).toFloat
      val w = width * 1.4f
      val dx = tipX - sx; val dy = tipY - sy
      val midX = sx + dx * 0.5f; val midY = sy + dy * 0.5f
      val beamLen = Math.sqrt(dx * dx + dy * dy).toFloat
      val perpX = if (beamLen > 1) -dy / beamLen else 0f
      val perpY = if (beamLen > 1) dx / beamLen else 0f

      // Sinusoidal wave modulation
      val waveAmp = w * 0.5f
      val waveMidX = midX + perpX * Math.sin(phase * 1.5).toFloat * waveAmp
      val waveMidY = midY + perpY * Math.sin(phase * 1.5).toFloat * waveAmp

      // Large soft outer glow
      sb.strokeLineSoft(sx, sy, waveMidX, waveMidY, w * 5f, r, g, b, 0.28f * p)
      sb.strokeLineSoft(waveMidX, waveMidY, tipX, tipY, w * 5f, r, g, b, 0.3f * p)

      // Dark cartoon outline
      sb.strokeLine(sx, sy, waveMidX, waveMidY, w * 1.5f, outline(r), outline(g), outline(b), 0.7f * p)
      sb.strokeLine(waveMidX, waveMidY, tipX, tipY, w * 1.5f, outline(r), outline(g), outline(b), 0.7f * p)
      // Main beam body — brighter
      sb.strokeLine(sx, sy, waveMidX, waveMidY, w, r, g, b, 0.9f * p)
      sb.strokeLine(waveMidX, waveMidY, tipX, tipY, w, r, g, b, 0.95f * p)
      // Bright hot core
      sb.strokeLine(sx, sy, waveMidX, waveMidY, w * 0.35f, bright(r), bright(g), bright(b), 0.98f * p)
      sb.strokeLine(waveMidX, waveMidY, tipX, tipY, w * 0.35f, bright(r), bright(g), bright(b), 0.98f * p)

      // Crackling sparks — 6 sparks, bigger
      var sp = 0; while (sp < 6) {
        val st = ((tick * 0.12 + sp * 0.167 + proj.id * 0.17) % 1.0).toFloat
        val sparkOff = Math.sin(phase * 5 + sp * 3.7).toFloat * w * 2f
        val sparkX = sx + dx * st + perpX * sparkOff
        val sparkY = sy + dy * st + perpY * sparkOff
        val sparkEndX = sparkX + perpX * Math.sin(phase * 7 + sp * 2.3).toFloat * w * 1.2f
        val sparkEndY = sparkY + perpY * Math.sin(phase * 7 + sp * 2.3).toFloat * w * 1.2f
        sb.strokeLine(sparkX, sparkY, sparkEndX, sparkEndY, 2f, bright(r), bright(g), bright(b),
          (0.35 + 0.35 * Math.sin(phase * 6 + sp * 4.1)).toFloat * p)
      ; sp += 1 }

      // Big cartoon tip flare with outline
      val tipPulse = (0.7 + 0.3 * Math.sin(phase * 3)).toFloat
      sb.fillOvalSoft(tipX, tipY, w * 5f * tipPulse, w * 3.8f * tipPulse, r, g, b, 0.45f * p, 0f, 14)
      sb.strokeOval(tipX, tipY, w * 2f * tipPulse, w * 1.5f * tipPulse, 3f,
        outline(r), outline(g), outline(b), 0.6f * p, 10)
      sb.fillOval(tipX, tipY, w * 1.8f, w * 1.35f, r, g, b, 0.9f * p, 10)
      sb.fillOval(tipX, tipY, w * 0.8f, w * 0.6f, bright(r), bright(g), bright(b), 0.95f * p, 8)

      // Cartoon sparkle at tip
      drawSparkleStar(tipX, tipY, w * 1.2f * tipPulse, 1f, 1f, 1f, 0.5f * p, sb, phase * 1.5)

      // Expanding rings at tip — 2 rings
      var ring = 0; while (ring < 2) {
        val ringPh = ((phase * 0.6 + ring * 0.5) % 1.0).toFloat
        sb.strokeOval(tipX, tipY, w * (1.5f + ringPh * 3.5f), w * (1f + ringPh * 2.5f),
          2.5f * (1f - ringPh), bright(r), bright(g), bright(b), 0.45f * (1f - ringPh) * p, 10)
      ; ring += 1 }

      // Energy nodes along beam — bigger
      var i = 0; while (i < 6) {
        val t = ((tick * 0.08 + i * 0.167 + proj.id * 0.11) % 1.0).toFloat
        val taper = 1f - t * 0.4f
        val ns = w * 0.8f * taper * (0.6f + 0.4f * Math.sin(phase * 3 + i * 2.1).toFloat)
        val colorFade = 1f - t * 0.3f
        sb.fillOval(sx + dx * t, sy + dy * t, ns, ns * 0.7f,
          bright(r) * colorFade, bright(g) * colorFade, bright(b) * colorFade, 0.55f * (1f - t * 0.3f) * p, 8)
      ; i += 1 }
    }

  /** Large spinning star/blade weapon — CARTOONISH with bold outline and motion blur */
  private def spinner(r: Float, g: Float, b: Float, size: Float = 22f, pts: Int = 4): Renderer =
    (proj, sx, sy, sb, tick) => {
      val spin = tick * 0.35 + proj.id * 2.1
      val p = (0.75 + 0.25 * Math.sin(spin * 2)).toFloat
      val sz = size * 1.3f
      screenDir(proj)
      val ndx = _sdx; val ndy = _sdy
      val n = pts * 2

      // Speed lines behind
      drawSpeedLines(sx, sy, ndx, ndy, r, g, b, 0.3f * p, sb, 4, sz * 1.2f)

      // Impact ring — pulsing spin radius
      val ringPulse = (0.7 + 0.3 * Math.sin(spin * 3)).toFloat
      sb.strokeOval(sx, sy, sz * 1.25f * ringPulse, sz * 0.8f * ringPulse,
        2.5f, r, g, b, 0.2f * p, 14)

      // Motion blur trail — 5 ghosts
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
      sb.fillPolygon(_spinXs, _spinYs, n, r, g, b, 0.95f * p)
      // Bold dark cartoon outline
      sb.strokePolygon(_spinXs, _spinYs, n, 3.5f, outline(r), outline(g), outline(b), 0.85f * p)

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

  /** Solid physical projectile — CARTOONISH with bold outline, big head, speed lines */
  private def physProj(r: Float, g: Float, b: Float, worldLen: Float = 6f): Renderer =
    (proj, sx, sy, sb, tick) => {
      beamTip(sx, sy, proj, worldLen)
      val tipX = _tipX; val tipY = _tipY
      val phase = (tick + proj.id * 31) * 0.4
      val p = (0.75 + 0.25 * Math.sin(phase)).toFloat
      screenDir(proj)
      val ndx = _sdx; val ndy = _sdy
      val perpX = -ndy; val perpY = ndx

      // Manga speed lines behind
      drawSpeedLines(sx, sy, ndx, ndy, r, g, b, 0.35f * p, sb, 5, 35f)

      // Shaft — dark outline, colored body, bright core
      sb.strokeLine(sx, sy, tipX, tipY, 8f, outline(r), outline(g), outline(b), 0.85f * p)
      sb.strokeLine(sx, sy, tipX, tipY, 5.5f, r, g, b, 0.9f * p)
      sb.strokeLine(sx, sy, tipX, tipY, 2f, bright(r), bright(g), bright(b), 0.6f * p)

      // Bold arrowhead — bigger and outlined
      val headLen = 20f; val headW = 10f
      val pointX = tipX + ndx * headLen; val pointY = tipY + ndy * headLen
      // Dark outline first
      sb.fillPolygon(Array(pointX + ndx * 2f, tipX + perpX * (headW + 2f), tipX - perpX * (headW + 2f)),
        Array(pointY + ndy * 2f, tipY + perpY * (headW + 2f), tipY - perpY * (headW + 2f)),
        outline(r), outline(g), outline(b), 0.85f * p)
      // Filled head
      sb.fillPolygon(Array(pointX, tipX + perpX * headW, tipX - perpX * headW),
        Array(pointY, tipY + perpY * headW, tipY - perpY * headW),
        r, g, b, 0.95f * p)
      // Cartoon shine on arrowhead
      sb.fillPolygon(Array(pointX - perpX * 1f, tipX + perpX * headW * 0.3f, tipX + perpX * headW * 0.6f),
        Array(pointY - perpY * 1f, tipY + perpY * headW * 0.3f, tipY + perpY * headW * 0.6f),
        1f, 1f, 1f, 0.25f * p)

      // Angled fletching — bigger, bolder
      { var f = -1; while (f <= 1) {
        val tailX = sx - ndx * 4f; val tailY = sy - ndy * 4f
        val featherX = tailX - ndx * 16f + perpX * f * 8f
        val featherY = tailY - ndy * 16f + perpY * f * 8f
        sb.strokeLine(tailX, tailY, featherX, featherY, 4f, outline(r), outline(g), outline(b), 0.6f * p)
        sb.strokeLine(tailX, tailY, featherX, featherY, 2.5f, r, g, b, 0.55f * p)
        sb.strokeLine(tailX, tailY, featherX, featherY, 1f, bright(r), bright(g), bright(b), 0.3f * p)
      ; f += 2 } }

      // Trail — bigger, more dots
      var i = 0; while (i < 8) {
        val t = ((tick * 0.06 + i * 0.125 + proj.id * 0.11) % 1.0).toFloat
        val taper = 1f - t * 0.7f
        val colorFade = 1f - t * 0.3f
        sb.fillOval(sx - ndx * t * 38, sy - ndy * t * 38,
          5f * taper, 3.5f * taper, r * colorFade, g * colorFade, b * colorFade, 0.4f * (1f - t) * p, 8)
      ; i += 1 }
    }

  /** Large bouncing thrown object — CARTOONISH with big squash/stretch and bold outline */
  private def lobbed(r: Float, g: Float, b: Float, size: Float = 18f): Renderer =
    (proj, sx, sy, sb, tick) => {
      val phase = (tick + proj.id * 13) * 0.3
      val bounceRaw = Math.sin(phase * 1.5).toFloat
      val bounce = (Math.abs(bounceRaw) * 14f).toFloat
      val spin = tick * 0.18f + proj.id * 1.3f
      val p = (0.7 + 0.3 * Math.sin(phase)).toFloat
      val sz = size * 1.35f

      // Exaggerated squash and stretch
      val stretchY = 1f + bounceRaw * bounceRaw * 0.25f
      val stretchX = 1f - bounceRaw * bounceRaw * 0.12f

      // Dynamic shadow — bigger with bounce
      val shadowScale = 1f + bounce * 0.03f
      val shadowAlpha = Math.max(0.12f, 0.35f - bounce * 0.012f)
      sb.fillOval(sx, sy + sz * 0.35f, sz * 1.1f * shadowScale, sz * 0.3f, 0f, 0f, 0f, shadowAlpha, 14)

      // Motion arc — curved trail showing bounce path
      screenDir(proj)
      val ndx = _sdx; val ndy = _sdy
      var arc = 0; while (arc < 4) {
        val t = ((tick * 0.06 + arc * 0.15 + proj.id * 0.11) % 1.0).toFloat
        val arcBounce = Math.abs(Math.sin(phase * 1.5 - t * Math.PI)).toFloat * 10f
        sb.fillOval(sx - ndx * t * 35, sy - ndy * t * 35 - arcBounce,
          sz * 0.4f * (1f - t), sz * 0.3f * (1f - t), r, g, b, 0.3f * (1f - t) * p, 8)
      ; arc += 1 }

      // Outer glow
      sb.fillOvalSoft(sx, sy - bounce, sz * 2f * stretchX, sz * 1.6f * stretchY, r, g, b, 0.3f * p, 0f, 16)
      // Bold dark cartoon outline
      sb.strokeOval(sx, sy - bounce, sz * 1.05f * stretchX, sz * 0.85f * stretchY,
        3.5f, outline(r), outline(g), outline(b), 0.85f, 16)
      // Main body — solid and punchy
      sb.fillOval(sx, sy - bounce, sz * stretchX, sz * 0.8f * stretchY, r, g, b, 0.98f, 16)
      // Bottom shading
      sb.fillOval(sx, sy - bounce + sz * 0.18f * stretchY, sz * 0.85f * stretchX, sz * 0.5f * stretchY,
        dark(r), dark(g), dark(b), 0.35f, 14)
      // Cartoon highlight (top-left specular spot)
      sb.fillOval(sx - sz * 0.18f, sy - bounce - sz * 0.22f * stretchY,
        sz * 0.35f * stretchX, sz * 0.25f * stretchY, 1f, 1f, 1f, 0.4f, 8)

      // Surface cross marks
      var v = 0; while (v < 3) {
        val a = spin + v * Math.PI * 2 / 3
        val vx = (sx + Math.cos(a) * sz * 0.5f * stretchX).toFloat
        val vy = (sy - bounce + Math.sin(a) * sz * 0.38f * stretchY).toFloat
        sb.strokeLine(sx, sy - bounce, vx, vy, 2f, dark(r), dark(g), dark(b), 0.35f * p)
      ; v += 1 }
    }

  /** Large expanding ring AoE — CARTOONISH with bold rings, shockwave, sparkle stars */
  private def aoeRing(r: Float, g: Float, b: Float, maxR: Float = 50f): Renderer =
    (proj, sx, sy, sb, tick) => {
      val phase = (tick + proj.id * 23) * 0.4
      val p = (0.65 + 0.35 * Math.sin(phase * 2)).toFloat
      val mR = maxR * 1.3f

      // Ground fill — bigger
      sb.fillOvalSoft(sx, sy, mR * 0.7f, mR * 0.35f, r, g, b, 0.25f * p, 0f, 18)

      // 5 bold expanding rings with dark outlines
      var ring = 0; while (ring < 5) {
        val rp = ((phase * 0.25 + ring * 0.2) % 1.0).toFloat
        val ringR = 8f + rp * mR
        val a = Math.max(0f, 0.7f * (1f - rp) * p)
        val colorShift = rp * 0.35f
        val thickness = 5f * (1f - rp * 0.25f)
        // Dark outline ring
        sb.strokeOval(sx, sy, ringR + 1.5f, (ringR + 1.5f) * 0.45f, thickness + 2.5f,
          outline(r), outline(g), outline(b), a * 0.5f, 14)
        // Colored ring
        sb.strokeOval(sx, sy, ringR, ringR * 0.45f, thickness,
          mix(r, bright(r), colorShift), mix(g, bright(g), colorShift), mix(b, bright(b), colorShift), a, 14)
      ; ring += 1 }

      // 6 Radial energy spark lines — longer
      var spark = 0; while (spark < 6) {
        val sa = phase * 0.6 + spark * Math.PI / 3
        val sl = mR * 0.45f * p
        sb.strokeLine(sx, sy, sx + Math.cos(sa).toFloat * sl, sy + Math.sin(sa).toFloat * sl * 0.45f,
          2.5f, r, g, b, 0.3f * p)
        // Spark dot at tip
        sb.fillOval(sx + Math.cos(sa).toFloat * sl, sy + Math.sin(sa).toFloat * sl * 0.45f,
          4f, 3f, bright(r), bright(g), bright(b), 0.5f * p, 6)
      ; spark += 1 }

      // Core — bold with outline
      sb.strokeOval(sx, sy, 14f, 8.5f, 3f, outline(r), outline(g), outline(b), 0.7f * p, 12)
      sb.fillOval(sx, sy, 13f, 8f, r, g, b, 0.85f * p, 12)
      sb.fillOval(sx, sy, 6f, 3.5f, bright(r), bright(g), bright(b), 0.95f * p, 8)

      // Sparkle stars popping at the ring edges
      var star = 0; while (star < 4) {
        val starPhase = ((phase * 0.35 + star * 0.25) % 1.0).toFloat
        val starAngle = phase * 0.6 + star * Math.PI / 2
        val starDist = 8f + starPhase * mR
        val starX = sx + Math.cos(starAngle).toFloat * starDist
        val starY = sy + Math.sin(starAngle).toFloat * starDist * 0.45f
        val starAlpha = 0.5f * (1f - starPhase) * p
        drawSparkleStar(starX, starY, 5f * (1f - starPhase * 0.5f),
          bright(r), bright(g), bright(b), starAlpha, sb, phase + star)
      ; star += 1 }
    }

  /** Thick zigzag chain/bolt — CARTOONISH with bold segments, big forks, bright nodes */
  private def chainProj(r: Float, g: Float, b: Float, worldLen: Float = 6f): Renderer =
    (proj, sx, sy, sb, tick) => {
      beamTip(sx, sy, proj, worldLen)
      val tipX = _tipX; val tipY = _tipY
      val phase = (tick + proj.id * 23) * 0.3
      val p = (0.7 + 0.3 * Math.sin(phase)).toFloat
      val dx = tipX - sx; val dy = tipY - sy
      val len = Math.sqrt(dx * dx + dy * dy).toFloat
      if (len >= 1) {
        val nx = -dy / len; val ny = dx / len
        val zigW = 12f * p
        val segs = 8

        var i = 0; while (i < segs) {
          val t0 = i.toFloat / segs; val t1 = (i + 1).toFloat / segs
          val jitter0 = Math.sin(phase * 2.3 + i * 1.7).toFloat * 4f
          val jitter1 = Math.sin(phase * 2.3 + (i + 1) * 1.7).toFloat * 4f
          val z0 = (if (i % 2 == 0) zigW else -zigW) + jitter0
          val z1 = (if ((i + 1) % 2 == 0) zigW else -zigW) + jitter1
          val x0 = sx + dx * t0 + nx * z0; val y0 = sy + dy * t0 + ny * z0
          val x1 = sx + dx * t1 + nx * z1; val y1 = sy + dy * t1 + ny * z1

          // Dark outline
          sb.strokeLine(x0, y0, x1, y1, 9f, outline(r), outline(g), outline(b), 0.75f * p)
          // Main segment — thicker
          sb.strokeLine(x0, y0, x1, y1, 7f, r, g, b, 0.9f * p)
          if (i % 2 == 0) sb.strokeLine(x0, y0, x1, y1, 3f, bright(r), bright(g), bright(b), 0.8f * p)

          // Bigger branch forks at alternating joints
          if (i % 2 == 1 && i < segs - 1) {
            val forkLen = 14f + Math.sin(phase * 3 + i * 1.3).toFloat * 5f
            val forkAngle = if (i % 4 == 1) 0.7 else -0.7
            val forkX = x1 + Math.cos(Math.atan2(ny, nx) + forkAngle).toFloat * forkLen
            val forkY = y1 + Math.sin(Math.atan2(ny, nx) + forkAngle).toFloat * forkLen
            sb.strokeLine(x1, y1, forkX, forkY, 4f, outline(r), outline(g), outline(b), 0.4f * p)
            sb.strokeLine(x1, y1, forkX, forkY, 3f, r, g, b, 0.5f * p)
            // Fork tip dot
            sb.fillOval(forkX, forkY, 3f, 2.5f, bright(r), bright(g), bright(b), 0.5f * p, 6)
          }
        ; i += 1 }

        // Bright nodes at joints — bigger, with outline
        { var i = 0; while (i <= segs) {
          val t = i.toFloat / segs
          val jitter = Math.sin(phase * 2.3 + i * 1.7).toFloat * 4f
          val z = (if (i % 2 == 0) zigW else -zigW) + jitter
          val jx = sx + dx * t + nx * z; val jy = sy + dy * t + ny * z
          sb.strokeOval(jx, jy, 7f, 5.5f, 2f, outline(r), outline(g), outline(b), 0.6f * p, 8)
          sb.fillOval(jx, jy, 6f, 5f, bright(r), bright(g), bright(b), 0.8f * p, 8)
        ; i += 3 } }

        // Tip with outline and sparkle
        sb.strokeOval(tipX, tipY, 10f, 7f, 2.5f, outline(r), outline(g), outline(b), 0.6f * p, 10)
        sb.fillOval(tipX, tipY, 9f, 6.5f, bright(r), bright(g), bright(b), 0.75f * p, 10)
        drawSparkleStar(tipX, tipY, 8f, bright(r), bright(g), bright(b), 0.4f * p, sb, phase * 2)
      }
    }

  /** Small fast bullet — CARTOONISH with bold outline, big muzzle flash, speed streaks */
  private def bulletProj(r: Float, g: Float, b: Float, size: Float = 5f): Renderer =
    (proj, sx, sy, sb, tick) => {
      val phase = (tick + proj.id * 31) * 0.4
      val p = (0.7 + 0.3 * Math.sin(phase)).toFloat
      val sz = size * 1.5f
      screenDir(proj)
      val ndx = _sdx; val ndy = _sdy

      // Speed lines behind
      drawSpeedLines(sx, sy, ndx, ndy, r, g, b, 0.35f * p, sb, 5, 30f)

      // Big muzzle flash at rear — cartoon explosion puff
      val flashSize = 18f * (0.7f + 0.3f * Math.sin(phase * 3).toFloat)
      sb.fillOvalSoft(sx - ndx * 10f, sy - ndy * 10f, flashSize, flashSize * 0.7f, 1f, 0.9f, 0.4f, 0.3f * p, 0f, 10)
      sb.fillOval(sx - ndx * 10f, sy - ndy * 10f, flashSize * 0.5f, flashSize * 0.35f, 1f, 1f, 0.7f, 0.4f * p, 8)

      // Bullet body
      val bodyLen = sz * 3f
      val bodyW = sz * 1f
      val tipX = sx + ndx * bodyLen * 0.5f
      val tipY = sy + ndy * bodyLen * 0.5f
      val tailX = sx - ndx * bodyLen * 0.5f
      val tailY = sy - ndy * bodyLen * 0.5f

      // Dark cartoon outline
      sb.strokeLine(tailX, tailY, tipX, tipY, bodyW * 2.5f, outline(r), outline(g), outline(b), 0.9f * p)
      // Metallic body
      sb.strokeLine(tailX, tailY, tipX, tipY, bodyW * 1.8f, r, g, b, 0.95f * p)
      // Specular highlight
      sb.strokeLine(tailX, tailY, tipX, tipY, bodyW * 0.6f, bright(r), bright(g), bright(b), 0.7f * p)
      // Rounded tip with outline
      sb.strokeOval(tipX, tipY, bodyW * 1.6f, bodyW * 1.2f, 2f, outline(r), outline(g), outline(b), 0.8f * p, 8)
      sb.fillOval(tipX, tipY, bodyW * 1.4f, bodyW * 1f, bright(r), bright(g), bright(b), 0.9f * p, 8)

      // Fast streak trail — more lines
      var i = 0; while (i < 6) {
        val t = ((tick * 0.07 + i * 0.12 + proj.id * 0.11) % 1.0).toFloat
        val taper = 1f - t * 0.85f
        val alpha = 0.35f * (1f - t) * p
        sb.strokeLine(sx - ndx * t * 50, sy - ndy * t * 50,
          sx - ndx * (t + 0.08f) * 50, sy - ndy * (t + 0.08f) * 50,
          bodyW * 1.2f * taper, r * 0.7f, g * 0.7f, b * 0.7f, alpha)
      ; i += 1 }
    }

  /** Compact fist/punch — CARTOONISH with bold outline, impact burst, speed lines */
  private def fistProj(r: Float, g: Float, b: Float, size: Float = 14f): Renderer =
    (proj, sx, sy, sb, tick) => {
      val phase = (tick + proj.id * 23) * 0.4
      val p = (0.65 + 0.35 * Math.sin(phase)).toFloat
      val sz = size * 1.4f
      screenDir(proj)
      val ndx = _sdx; val ndy = _sdy
      val perpX = -ndy; val perpY = ndx

      // Speed lines behind — manga punch style
      drawSpeedLines(sx, sy, ndx, ndy, r, g, b, 0.4f * p, sb, 6, sz * 2f)

      // Impact burst ring — big pulsing
      val ringPulse = 0.6f + 0.4f * Math.sin(phase * 3).toFloat
      sb.strokeOval(sx + ndx * sz * 0.3f, sy + ndy * sz * 0.3f,
        sz * 2f * ringPulse, sz * 1.5f * ringPulse,
        3f, r, g, b, 0.35f * p, 14)
      // Second smaller ring
      sb.strokeOval(sx + ndx * sz * 0.3f, sy + ndy * sz * 0.3f,
        sz * 1.2f * ringPulse, sz * 0.9f * ringPulse,
        2f, bright(r), bright(g), bright(b), 0.25f * p, 10)

      // Bold dark cartoon outline
      sb.strokeOval(sx, sy, sz * 1.05f, sz * 0.8f, 3.5f,
        outline(r), outline(g), outline(b), 0.85f * p, 14)
      // Fist body — solid and punchy
      sb.fillOval(sx, sy, sz, sz * 0.75f, r, g, b, 0.95f * p, 14)
      // Knuckle highlight
      sb.fillOval(sx + ndx * sz * 0.2f, sy + ndy * sz * 0.2f - 2f,
        sz * 0.45f, sz * 0.35f, bright(r), bright(g), bright(b), 0.6f * p, 10)
      // Cartoon shine spot
      sb.fillOval(sx - sz * 0.15f, sy - sz * 0.12f, sz * 0.2f, sz * 0.15f,
        1f, 1f, 1f, 0.4f * p, 6)
      // Dark bottom shading
      sb.fillOval(sx, sy + sz * 0.15f, sz * 0.8f, sz * 0.4f,
        dark(r), dark(g), dark(b), 0.35f, 10)

      // Bold motion lines — wider spread
      var i = -2; while (i <= 2) {
        val lineStart = 8f + Math.abs(i) * 4f
        val lineEnd = lineStart + 18f
        sb.strokeLine(
          sx - ndx * lineStart + perpX * i * sz * 0.5f,
          sy - ndy * lineStart + perpY * i * sz * 0.5f,
          sx - ndx * lineEnd + perpX * i * sz * 0.5f,
          sy - ndy * lineEnd + perpY * i * sz * 0.5f,
          2.5f, r, g, b, 0.45f * (1f - Math.abs(i) * 0.15f) * p)
      ; i += 1 }
    }

  /** Wide crescent wave/slash — CARTOONISH with bold outline, more afterimages, sparkles */
  private def wave(r: Float, g: Float, b: Float, spread: Float = 32f): Renderer =
    (proj, sx, sy, sb, tick) => {
      val phase = (tick + proj.id * 41) * 0.4
      val p = (0.65 + 0.35 * Math.sin(phase)).toFloat
      val sp = spread * 1.3f
      screenDir(proj)
      val ndx = _sdx; val ndy = _sdy
      val perpX = -ndy; val perpY = ndx
      val tipX = sx + ndx * sp; val tipY = sy + ndy * sp * 0.6f
      val w1x = sx + perpX * sp; val w1y = sy + perpY * sp * 0.6f
      val w2x = sx - perpX * sp; val w2y = sy - perpY * sp * 0.6f

      // Trailing afterimages — 3 fading copies
      var af = 1; while (af <= 3) {
        val afOff = af * 10f
        val afAlpha = 0.25f * (1f - af * 0.25f) * p
        sb.fillPolygon(
          Array(tipX - ndx * afOff, w1x - ndx * afOff, sx - ndx * (4 + afOff), w2x - ndx * afOff),
          Array(tipY - ndy * afOff, w1y - ndy * afOff, sy - ndy * (3 + afOff), w2y - ndy * afOff),
          r, g, b, afAlpha)
      ; af += 1 }

      // Main crescent fill — brighter
      sb.fillPolygon(Array(tipX, w1x, sx - ndx * 4, w2x), Array(tipY, w1y, sy - ndy * 3, w2y), r, g, b, 0.6f * p)

      // Bold dark cartoon outline
      sb.strokePolygon(Array(tipX, w1x, sx - ndx * 4, w2x), Array(tipY, w1y, sy - ndy * 3, w2y),
        4f, outline(r), outline(g), outline(b), 0.75f * p)
      // Bright leading edge — even thicker
      sb.strokePolygon(Array(tipX, w1x, sx - ndx * 4, w2x), Array(tipY, w1y, sy - ndy * 3, w2y),
        2.5f, bright(r), bright(g), bright(b), 0.9f * p)

      // Internal energy flow lines
      var i = 0; while (i < 4) {
        val off = (i - 1.5f) * sp * 0.22f
        val flowPhase = Math.sin(phase * 2.5 + i * 1.8).toFloat * sp * 0.15f
        sb.strokeLine(sx + perpX * off - ndx * 3, sy + perpY * off * 0.6f - ndy * 2,
          sx + perpX * off + ndx * (sp * 0.55f + flowPhase),
          sy + perpY * off * 0.6f + ndy * (sp * 0.35f + flowPhase * 0.5f),
          2f, r, g, b, (0.2 + 0.12 * Math.sin(phase * 3 + i * 2.3)).toFloat * p)
      ; i += 1 }

      // Sparkle stars along the leading edge
      { var i = 0; while (i < 5) {
        val starPhase = ((phase * 0.5 + i * 0.2) % 1.0).toFloat
        val edgeT = i.toFloat / 4
        val ex = tipX * (1f - edgeT) + (if (i % 2 == 0) w1x else w2x) * edgeT
        val ey = tipY * (1f - edgeT) + (if (i % 2 == 0) w1y else w2y) * edgeT
        val starAlpha = 0.6f * (1f - starPhase) * p
        drawSparkleStar(ex, ey, 5f * (1f - starPhase * 0.4f),
          bright(r), bright(g), bright(b), starAlpha, sb, phase * 1.5 + i)
      ; i += 1 } }

      // Leading edge spark particles — bigger
      { var i = 0; while (i < 6) {
        val t = ((tick * 0.1 + i * 0.167 + proj.id * 0.11) % 1.0).toFloat
        val edgeT = i.toFloat / 5
        val ex = tipX * (1f - edgeT) + (if (i % 2 == 0) w1x else w2x) * edgeT
        val ey = tipY * (1f - edgeT) + (if (i % 2 == 0) w1y else w2y) * edgeT
        sb.fillOval(ex + ndx * t * 8, ey + ndy * t * 5, 5f, 3.5f, bright(r), bright(g), bright(b),
          Math.max(0f, 0.6f * (1f - t) * p), 8)
      ; i += 1 } }
    }

  // ═══════════════════════════════════════════════════════════════
  //  TIER 1: NEW SPECIALIZED RENDERERS
  // ═══════════════════════════════════════════════════════════════

  /** Void Bolt - swirling dark vortex with reality-distortion ripple rings */
  private def drawVoidBolt(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 43) * 0.35
    val p = (0.85 + 0.15 * Math.sin(phase)).toFloat
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy

    // Concentric distortion rings — rotate and pulse in opposite directions
    var ring = 0; while (ring < 5) {
      val ringR = 10f + ring * 7f
      val ringPhase = phase * (if (ring % 2 == 0) 1.2 else -0.9) + ring * 0.7
      val ringPulse = (0.8 + 0.2 * Math.sin(ringPhase * 2)).toFloat
      val ringAlpha = 0.3f * (1f - ring * 0.12f) * p
      sb.strokeOval(sx + Math.cos(ringPhase).toFloat * 2, sy + Math.sin(ringPhase).toFloat * 1.5f,
        ringR * ringPulse, ringR * 0.6f * ringPulse, 1.5f, 0.25f, 0.05f, 0.4f, ringAlpha, 12)
    ; ring += 1 }

    // Central black hole
    sb.fillOval(sx, sy, 14f, 10f, 0.05f, 0f, 0.08f, 0.95f, 14)
    // Dark purple mid-ring
    sb.fillOval(sx, sy, 20f, 15f, 0.15f, 0.02f, 0.25f, 0.5f * p, 12)
    // Black center
    sb.fillOval(sx, sy, 7f, 5f, 0f, 0f, 0f, 0.98f, 10)

    // Particles spiraling inward toward center
    var i = 0; while (i < 8) {
      val t = ((tick * 0.06 + i * 0.125 + proj.id * 0.13) % 1.0).toFloat
      val inward = 1f - t  // reverse: starts far, comes in
      val spiralAngle = phase * 2.5 + i * Math.PI * 2 / 8 + t * Math.PI * 3
      val dist = 28f * inward
      val px = sx + Math.cos(spiralAngle).toFloat * dist
      val py = sy + Math.sin(spiralAngle).toFloat * dist * 0.55f
      val s = 3f + inward * 3f
      sb.fillOval(px, py, s, s * 0.7f, 0.3f, 0.1f, 0.5f, 0.5f * t * p, 6)
    ; i += 1 }

    // Flickering cracks/tears radiating outward
    var c = 0; while (c < 4) {
      val crackAngle = phase * 0.8 + c * Math.PI / 2
      val jitter = Math.sin(phase * 5 + c * 3.1).toFloat * 4f
      val crackLen = 18f + Math.sin(phase * 3 + c * 2.7).toFloat * 8f
      val cx0 = sx + Math.cos(crackAngle).toFloat * 6f
      val cy0 = sy + Math.sin(crackAngle).toFloat * 3.5f
      val cx1 = sx + Math.cos(crackAngle).toFloat * crackLen + jitter
      val cy1 = sy + Math.sin(crackAngle).toFloat * crackLen * 0.5f + jitter * 0.3f
      sb.strokeLine(cx0, cy0, cx1, cy1, 1.5f, 0.4f, 0.15f, 0.6f,
        (0.2 + 0.2 * Math.sin(phase * 7 + c * 2.3)).toFloat * p)
    ; c += 1 }

    // Trail spiraling inward behind
    drawRibbonTrail(sx, sy, ndx, ndy, 0.2f, 0.05f, 0.35f, p, sb, tick, proj.id,
      6, 40f, 8f, 1f)
  }

  /** Gravity Ball - dense dark sphere with orbiting debris ring */
  private def drawGravityBall(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 37) * 0.3
    val p = (0.85 + 0.15 * Math.sin(phase)).toFloat
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy

    // Ground shadow/distortion oval
    sb.fillOval(sx, sy + 12f, 22f, 7f, 0f, 0f, 0f, 0.25f * p, 12)

    // Inward-pulling particle streams — 4 streams spiraling inward
    var stream = 0; while (stream < 4) {
      var seg = 0; while (seg < 5) {
        val t = ((tick * 0.05 + seg * 0.2 + stream * 0.25 + proj.id * 0.11) % 1.0).toFloat
        val inward = 1f - t
        val spiralA = phase * 1.5 + stream * Math.PI / 2 + t * Math.PI * 2
        val dist = 30f * inward
        val px = sx + Math.cos(spiralA).toFloat * dist
        val py = sy + Math.sin(spiralA).toFloat * dist * 0.5f
        sb.fillOval(px, py, 2.5f * inward, 2f * inward, 0.3f, 0.1f, 0.5f, 0.4f * t * p, 6)
      ; seg += 1 }
    ; stream += 1 }

    // Heavy dark purple/indigo core
    sb.fillOvalSoft(sx, sy, 34f, 26f, 0.15f, 0.05f, 0.3f, 0.3f * p, 0f, 16)
    sb.fillOval(sx, sy, 18f, 14f, 0.12f, 0.04f, 0.28f, 0.9f, 14)
    // Bright compressed center
    sb.fillOval(sx, sy, 8f, 6f, 0.45f, 0.25f, 0.8f, 0.85f * p, 10)
    sb.fillOval(sx, sy, 3.5f, 2.5f, 0.7f, 0.5f, 1f, 0.9f, 8)

    // Orbiting debris ring — 8 particles in elliptical plane (Saturn-like)
    var d = 0; while (d < 8) {
      val dAngle = phase * 1.8 + d * Math.PI * 2 / 8
      val dRadX = 22f + Math.sin(phase + d * 1.3).toFloat * 3f
      val dRadY = 6f + Math.sin(phase * 0.7 + d * 0.9).toFloat * 2f
      val dx = sx + Math.cos(dAngle).toFloat * dRadX
      val dy = sy + Math.sin(dAngle).toFloat * dRadY
      val dSize = 2.5f + Math.sin(phase * 2 + d * 1.7).toFloat * 1f
      sb.fillOval(dx, dy, dSize, dSize * 0.8f, 0.4f, 0.3f, 0.55f, 0.7f * p, 6)
    ; d += 1 }

    // Trail
    drawRibbonTrail(sx, sy, ndx, ndy, 0.15f, 0.05f, 0.3f, p, sb, tick, proj.id,
      6, 35f, 7f, 1f)
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
    val p = (0.85 + 0.15 * Math.sin(phase)).toFloat
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy

    // Soft warm halo
    sb.fillOvalSoft(sx, sy, 40f, 32f, 1f, 0.9f, 0.5f, 0.3f * p, 0f, 16)

    // 6-point star (12 vertices: alternating outer/inner)
    val starSpin = phase * 0.3
    var i = 0; while (i < 12) {
      val a = starSpin + i * Math.PI / 6
      val rad = if (i % 2 == 0) 18f else 8f
      _holyXs(i) = (sx + Math.cos(a).toFloat * rad).toFloat
      _holyYs(i) = (sy + Math.sin(a).toFloat * rad * 0.65f).toFloat
    ; i += 1 }
    // Golden star fill
    sb.fillPolygon(_holyXs, _holyYs, 12, 1f, 0.9f, 0.45f, 0.85f * p)
    // Bright edge
    sb.strokePolygon(_holyXs, _holyYs, 12, 2f, 1f, 1f, 0.75f, 0.7f * p)
    // White-hot center
    sb.fillOval(sx, sy, 6f, 4.5f, 1f, 1f, 0.9f, 0.95f, 8)

    // Radiant light rays — 6 slowly rotating
    var ray = 0; while (ray < 6) {
      val rayAngle = phase * 0.5 + ray * Math.PI / 3
      val rayLen = 24f + Math.sin(phase * 2 + ray * 1.7).toFloat * 6f
      val rx0 = sx + Math.cos(rayAngle).toFloat * 8f
      val ry0 = sy + Math.sin(rayAngle).toFloat * 5f
      val rx1 = sx + Math.cos(rayAngle).toFloat * rayLen
      val ry1 = sy + Math.sin(rayAngle).toFloat * rayLen * 0.6f
      sb.strokeLineSoft(rx0, ry0, rx1, ry1, 4f, 1f, 0.95f, 0.6f,
        (0.2 + 0.15 * Math.sin(phase * 2.5 + ray * 2.1)).toFloat * p)
    ; ray += 1 }

    // Sparkle particles drifting upward from trail
    { var i = 0; while (i < 6) {
      val t = ((tick * 0.05 + i * 0.167 + proj.id * 0.13) % 1.0).toFloat
      val sparkX = sx - ndx * t * 30 + Math.sin(phase + i * 2.3).toFloat * 6
      val sparkY = sy - ndy * t * 30 - t * 12f
      sb.fillOval(sparkX, sparkY, 2.5f, 2f, 1f, 1f, 0.7f, 0.45f * (1f - t) * p, 6)
    ; i += 1 } }
  }

  /** Curse - dark spiraling occult energy with orbiting rune-like marks */
  private def drawCurse(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 41) * 0.35
    val p = (0.8 + 0.2 * Math.sin(phase * 1.5)).toFloat
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy

    // Dark smoke tendrils trailing behind
    drawRibbonTrail(sx, sy, ndx, ndy, 0.15f, 0.03f, 0.2f, p, sb, tick, proj.id,
      8, 45f, 10f, 2f)

    // Deep purple/black swirling core
    sb.fillOvalSoft(sx, sy, 30f, 24f, 0.2f, 0.02f, 0.3f, 0.35f * p, 0f, 14)
    sb.fillOval(sx, sy, 16f, 12f, 0.12f, 0.02f, 0.2f, 0.85f, 14)
    // Pulsing sinister inner glow — dramatic brightens/dims
    val glowPulse = (0.5 + 0.5 * Math.sin(phase * 3)).toFloat
    sb.fillOval(sx, sy, 10f, 7f, 0.4f * glowPulse, 0.05f, 0.55f * glowPulse, 0.6f * p, 10)
    sb.fillOval(sx, sy, 4f, 3f, 0.3f, 0.02f, 0.4f, 0.9f, 8)

    // 3 orbiting "rune" shapes — small rotating polygons at different orbital speeds
    var rune = 0; while (rune < 3) {
      val runeSpeed = 1.2 + rune * 0.5
      val runeAngle = phase * runeSpeed + rune * Math.PI * 2 / 3
      val runeDist = 16f + Math.sin(phase + rune * 1.7).toFloat * 3f
      val rx = sx + Math.cos(runeAngle).toFloat * runeDist
      val ry = sy + Math.sin(runeAngle).toFloat * runeDist * 0.55f
      val runeSpin = phase * 3 + rune * 2.1
      // Small diamond shape
      val rs = 3.5f
      sb.fillPolygon(
        Array(rx + Math.cos(runeSpin).toFloat * rs, rx + Math.cos(runeSpin + Math.PI / 2).toFloat * rs * 0.5f,
          rx + Math.cos(runeSpin + Math.PI).toFloat * rs, rx + Math.cos(runeSpin + Math.PI * 1.5).toFloat * rs * 0.5f),
        Array(ry + Math.sin(runeSpin).toFloat * rs * 0.6f, ry + Math.sin(runeSpin + Math.PI / 2).toFloat * rs * 0.3f,
          ry + Math.sin(runeSpin + Math.PI).toFloat * rs * 0.6f, ry + Math.sin(runeSpin + Math.PI * 1.5).toFloat * rs * 0.3f),
        0.5f, 0.15f, 0.7f, 0.65f * p)
    ; rune += 1 }

    // Faint skull impression — 2 eye dots + mouth arc
    val skullAlpha = (0.15 + 0.1 * Math.sin(phase * 2)).toFloat * p
    sb.fillOval(sx - 4f, sy - 2f, 2.5f, 2f, 0.05f, 0f, 0.08f, skullAlpha, 6)
    sb.fillOval(sx + 4f, sy - 2f, 2.5f, 2f, 0.05f, 0f, 0.08f, skullAlpha, 6)
    sb.strokeOval(sx, sy + 3f, 3f, 2f, 1f, 0.05f, 0f, 0.08f, skullAlpha * 0.8f, 6)
  }

  /** Web Shot - radiating web strand structure */
  private def drawWebShot(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 29) * 0.3
    val p = (0.85 + 0.15 * Math.sin(phase)).toFloat
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val strandCount = 8

    // Central knot
    sb.fillOval(sx, sy, 5f, 4f, 0.92f, 0.92f, 0.88f, 0.9f * p, 8)

    // Radial silk strands — wave slightly with sine offset
    var strand = 0; while (strand < strandCount) {
      val sAngle = phase * 0.2 + strand * Math.PI * 2 / strandCount
      val sLen = 18f + Math.sin(phase * 1.5 + strand * 1.3).toFloat * 4f
      val waveOff = Math.sin(phase * 2.5 + strand * 0.9).toFloat * 3f
      val perpAngle = sAngle + Math.PI / 2
      val endX = sx + Math.cos(sAngle).toFloat * sLen + Math.cos(perpAngle).toFloat * waveOff
      val endY = sy + Math.sin(sAngle).toFloat * sLen * 0.6f + Math.sin(perpAngle).toFloat * waveOff * 0.4f
      sb.strokeLine(sx, sy, endX, endY, 1.2f, 0.92f, 0.92f, 0.88f, 0.6f * p)

      // Connecting spiral thread to next strand midpoint
      val nextAngle = phase * 0.2 + ((strand + 1) % strandCount) * Math.PI * 2 / strandCount
      val nextWave = Math.sin(phase * 2.5 + ((strand + 1) % strandCount) * 0.9).toFloat * 3f
      val nextPerpAngle = nextAngle + Math.PI / 2
      val midT = 0.55f
      val mx0 = sx + Math.cos(sAngle).toFloat * sLen * midT + Math.cos(perpAngle).toFloat * waveOff * midT
      val my0 = sy + Math.sin(sAngle).toFloat * sLen * 0.6f * midT + Math.sin(perpAngle).toFloat * waveOff * 0.4f * midT
      val mx1 = sx + Math.cos(nextAngle).toFloat * sLen * midT + Math.cos(nextPerpAngle).toFloat * nextWave * midT
      val my1 = sy + Math.sin(nextAngle).toFloat * sLen * 0.6f * midT + Math.sin(nextPerpAngle).toFloat * nextWave * 0.4f * midT
      sb.strokeLine(mx0, my0, mx1, my1, 0.8f, 0.85f, 0.85f, 0.82f, 0.35f * p)
    ; strand += 1 }

    // Sticky drip particles trailing behind
    var i = 0; while (i < 5) {
      val t = ((tick * 0.05 + i * 0.2 + proj.id * 0.13) % 1.0).toFloat
      val dripX = sx - ndx * t * 30 + Math.sin(phase + i * 2.1).toFloat * 4
      val dripY = sy - ndy * t * 30 + t * 10f
      sb.fillOval(dripX, dripY, 2.5f, 3.5f, 0.88f, 0.88f, 0.85f, 0.35f * (1f - t) * p, 6)
    ; i += 1 }
  }

  /** Venom Bolt - dripping toxic blob with bubbles */
  private def drawVenomBolt(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 37) * 0.35
    val p = (0.85 + 0.15 * Math.sin(phase)).toFloat
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy

    // Sickly green glow halo with yellowish tinge
    sb.fillOvalSoft(sx, sy, 32f, 25f, 0.35f, 0.8f, 0.2f, 0.25f * p, 0f, 14)

    // Main body: irregular pulsing blob
    val stretchX = 1f + Math.sin(phase * 2.3).toFloat * 0.12f
    val stretchY = 1f + Math.cos(phase * 1.7).toFloat * 0.1f
    sb.fillOval(sx, sy, 16f * stretchX, 12f * stretchY, 0.25f, 0.75f, 0.15f, 0.9f, 14)
    sb.fillOval(sx, sy, 10f * stretchX, 7f * stretchY, 0.35f, 0.85f, 0.2f, 0.85f * p, 12)

    // Surface sheen highlight that shifts position
    val sheenAngle = phase * 1.2
    val sheenX = sx + Math.cos(sheenAngle).toFloat * 4f
    val sheenY = sy + Math.sin(sheenAngle).toFloat * 2.5f - 2f
    sb.fillOval(sheenX, sheenY, 5f, 3f, 0.6f, 0.95f, 0.45f, 0.4f * p, 8)

    // 4 bubbles orbiting/rising with pop animation cycle
    var bub = 0; while (bub < 4) {
      val bubPhase = ((phase * 0.8 + bub * 0.25) % 1.0)
      val bubAngle = phase * 1.5 + bub * Math.PI / 2
      val bubDist = 10f + bubPhase.toFloat * 8f
      val bx = sx + Math.cos(bubAngle).toFloat * bubDist
      val by = sy + Math.sin(bubAngle).toFloat * bubDist * 0.5f - bubPhase.toFloat * 6f
      val bubSize = (2f + bub * 0.8f) * (if (bubPhase > 0.85) (1f - bubPhase.toFloat) * 6.67f else 1f)
      if (bubSize > 0.3f) {
        sb.fillOval(bx, by, bubSize, bubSize * 0.85f, 0.3f, 0.85f, 0.25f, 0.5f * p, 8)
        sb.strokeOval(bx, by, bubSize, bubSize * 0.85f, 0.8f, 0.4f, 0.9f, 0.3f, 0.4f * p, 8)
        // Bubble highlight
        sb.fillOval(bx - bubSize * 0.25f, by - bubSize * 0.25f, bubSize * 0.3f, bubSize * 0.25f,
          0.7f, 0.95f, 0.5f, 0.3f * p, 4)
      }
    ; bub += 1 }

    // Toxic drip trail: droplets falling with gravity
    var i = 0; while (i < 6) {
      val t = ((tick * 0.06 + i * 0.167 + proj.id * 0.13) % 1.0).toFloat
      val dripX = sx - ndx * t * 30 + Math.sin(phase + i * 2.1).toFloat * 4
      val dripY = sy - ndy * t * 30 + t * t * 18f  // gravity curve
      val dripSize = 3f * (1f - t * 0.6f)
      sb.fillOval(dripX, dripY, dripSize, dripSize * 1.3f, 0.25f, 0.75f, 0.15f, 0.45f * (1f - t) * p, 6)
    ; i += 1 }
  }

  // ═══════════════════════════════════════════════════════════════
  //  REGISTRY (all 135 types)
  // ═══════════════════════════════════════════════════════════════

  private val registry: Map[Byte, Renderer] = Map(
    // ── Original (0-30) ──
    ProjectileType.NORMAL       -> (drawNormal _),
    ProjectileType.TENTACLE     -> (drawTentacle _),
    ProjectileType.ICE_BEAM     -> beamProj(0.4f, 0.8f, 1f, 7f, 9f),
    ProjectileType.AXE          -> spinner(0.65f, 0.55f, 0.4f, 26f, 2),
    ProjectileType.ROPE         -> chainProj(0.7f, 0.5f, 0.3f, 6f),
    ProjectileType.SPEAR        -> physProj(0.55f, 0.5f, 0.35f, 7f),
    ProjectileType.SOUL_BOLT    -> energyBolt(0.3f, 0.9f, 0.4f, 20f),
    ProjectileType.HAUNT        -> energyBolt(0.5f, 0.7f, 0.95f, 24f),
    ProjectileType.ARCANE_BOLT  -> energyBolt(0.65f, 0.3f, 0.95f, 22f),
    ProjectileType.FIREBALL     -> (drawFireball _),
    ProjectileType.SPLASH       -> aoeRing(0.3f, 0.6f, 1f, 50f),
    ProjectileType.TIDAL_WAVE   -> (drawTidalWave _),
    ProjectileType.GEYSER       -> (drawGeyser _),
    ProjectileType.BULLET       -> bulletProj(0.75f, 0.7f, 0.55f),
    ProjectileType.GRENADE      -> lobbed(0.4f, 0.45f, 0.3f, 18f),
    ProjectileType.ROCKET       -> (drawRocket _),
    ProjectileType.TALON        -> physProj(0.6f, 0.5f, 0.35f, 5f),
    ProjectileType.GUST         -> wave(0.8f, 0.9f, 1f, 32f),
    ProjectileType.SHURIKEN     -> spinner(0.6f, 0.6f, 0.65f, 22f, 4),
    ProjectileType.POISON_DART  -> physProj(0.2f, 0.8f, 0.15f, 5.5f),
    ProjectileType.CHAIN_BOLT   -> chainProj(0.5f, 0.5f, 0.55f, 5.5f),
    ProjectileType.LOCKDOWN_CHAIN -> chainProj(0.4f, 0.4f, 0.45f, 6f),
    ProjectileType.SNARE_MINE   -> lobbed(0.3f, 0.6f, 1f, 20f),
    ProjectileType.KATANA       -> spinner(0.75f, 0.75f, 0.8f, 26f, 2),
    ProjectileType.SWORD_WAVE   -> (drawSwordWave _),
    ProjectileType.PLAGUE_BOLT  -> energyBolt(0.45f, 0.75f, 0.15f, 20f),
    ProjectileType.MIASMA       -> aoeRing(0.35f, 0.65f, 0.15f, 45f),
    ProjectileType.BLIGHT_BOMB  -> lobbed(0.35f, 0.55f, 0.15f, 18f),
    ProjectileType.BLOOD_FANG   -> physProj(0.85f, 0.15f, 0.1f, 5f),
    ProjectileType.BLOOD_SIPHON -> beamProj(0.85f, 0.15f, 0.1f, 6f, 7f),
    ProjectileType.BAT_SWARM    -> (drawBatSwarm _),

    // ── Elemental (31-52) ──
    ProjectileType.FLAME_BOLT   -> energyBolt(1f, 0.5f, 0.1f, 20f),
    ProjectileType.FROST_SHARD  -> physProj(0.5f, 0.85f, 1f, 5.5f),
    ProjectileType.LIGHTNING    -> (drawLightning _),
    ProjectileType.CHAIN_LIGHTNING -> (drawLightning _),
    ProjectileType.THUNDER_STRIKE -> (drawThunderStrike _),
    ProjectileType.BOULDER      -> (drawBoulder _),
    ProjectileType.SEISMIC_SLAM -> aoeRing(0.6f, 0.45f, 0.25f, 55f),
    ProjectileType.WIND_BLADE   -> wave(0.8f, 0.95f, 1f, 30f),
    ProjectileType.MAGMA_BALL   -> energyBolt(1f, 0.45f, 0.05f, 24f),
    ProjectileType.ERUPTION     -> aoeRing(1f, 0.45f, 0.05f, 50f),
    ProjectileType.FROST_TRAP   -> lobbed(0.45f, 0.75f, 1f, 18f),
    ProjectileType.SAND_SHOT    -> energyBolt(0.9f, 0.75f, 0.4f, 18f),
    ProjectileType.SAND_BLAST   -> wave(0.85f, 0.75f, 0.4f, 28f),
    ProjectileType.THORN        -> physProj(0.2f, 0.6f, 0.15f, 6f),
    ProjectileType.VINE_WHIP    -> beamProj(0.2f, 0.6f, 0.15f, 6f, 7f),
    ProjectileType.THORN_WALL   -> aoeRing(0.25f, 0.55f, 0.15f, 35f),
    ProjectileType.INFERNO_BLAST -> (drawInfernoBlast _),
    ProjectileType.GLACIER_SPIKE -> physProj(0.55f, 0.85f, 1f, 6.5f),
    ProjectileType.MUD_GLOB     -> energyBolt(0.4f, 0.3f, 0.15f, 22f),
    ProjectileType.MUD_BOMB     -> lobbed(0.35f, 0.28f, 0.12f, 20f),
    ProjectileType.EMBER_SHOT   -> energyBolt(1f, 0.55f, 0.15f, 18f),
    ProjectileType.AVALANCHE_CRUSH -> lobbed(0.65f, 0.8f, 0.95f, 24f),

    // ── Undead/Dark (53-69) ──
    ProjectileType.DEATH_BOLT   -> energyBolt(0.2f, 0.5f, 0.05f, 22f),
    ProjectileType.RAISE_DEAD   -> (drawRaiseDead _),
    ProjectileType.BONE_AXE     -> spinner(0.92f, 0.9f, 0.82f, 22f, 2),
    ProjectileType.BONE_THROW   -> spinner(0.92f, 0.88f, 0.8f, 18f, 2),
    ProjectileType.WAIL         -> (drawWail _),
    ProjectileType.SOUL_DRAIN   -> beamProj(0.35f, 0.85f, 0.25f, 6f, 8f),
    ProjectileType.CLAW_SWIPE   -> wave(0.9f, 0.2f, 0.15f, 28f),
    ProjectileType.DEVOUR       -> (drawDevour _),
    ProjectileType.SCYTHE       -> (drawScythe _),
    ProjectileType.REAP         -> (drawReap _),
    ProjectileType.SHADOW_BOLT  -> (drawShadowBolt _),
    ProjectileType.CURSED_BLADE -> spinner(0.55f, 0.15f, 0.25f, 22f, 4),
    ProjectileType.LIFE_DRAIN   -> beamProj(0.85f, 0.1f, 0.1f, 6f, 7f),
    ProjectileType.SHOVEL       -> lobbed(0.6f, 0.6f, 0.55f, 18f),
    ProjectileType.HEAD_THROW   -> lobbed(0.75f, 0.7f, 0.6f, 20f),
    ProjectileType.BANDAGE_WHIP -> beamProj(0.85f, 0.75f, 0.6f, 6f, 6f),
    ProjectileType.CURSE        -> (drawCurse _),

    // ── Medieval/Fantasy (70-79) ──
    ProjectileType.HOLY_BLADE   -> spinner(1f, 0.95f, 0.5f, 22f, 4),
    ProjectileType.HOLY_BOLT    -> (drawHolyBolt _),
    ProjectileType.ARROW        -> physProj(0.55f, 0.4f, 0.25f, 6f),
    ProjectileType.POISON_ARROW -> physProj(0.25f, 0.75f, 0.2f, 6f),
    ProjectileType.SONIC_WAVE   -> wave(0.75f, 0.55f, 0.95f, 32f),
    ProjectileType.SONIC_BOOM   -> aoeRing(0.75f, 0.55f, 0.95f, 55f),
    ProjectileType.FIST         -> fistProj(0.75f, 0.6f, 0.45f),
    ProjectileType.SMITE        -> (drawHolyBolt _),
    ProjectileType.CHARM        -> energyBolt(1f, 0.45f, 0.65f, 18f),
    ProjectileType.CARD         -> spinner(0.92f, 0.92f, 0.88f, 16f, 4),

    // ── Sci-Fi/Tech (80-89) ──
    ProjectileType.DATA_BOLT    -> ((proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int) => drawDataBolt(proj, sx, sy, sb, tick, isVirus = false)),
    ProjectileType.VIRUS        -> ((proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int) => drawDataBolt(proj, sx, sy, sb, tick, isVirus = true)),
    ProjectileType.LASER        -> beamProj(1f, 0.25f, 0.2f, 8f, 5f),
    ProjectileType.GRAVITY_BALL -> (drawGravityBall _),
    ProjectileType.GRAVITY_WELL -> (drawGravityBall _),
    ProjectileType.TESLA_COIL   -> (drawLightning _),
    ProjectileType.NANO_BOLT    -> energyBolt(0.25f, 0.85f, 0.65f, 16f),
    ProjectileType.VOID_BOLT    -> (drawVoidBolt _),
    ProjectileType.RAILGUN      -> beamProj(0.25f, 0.55f, 1f, 10f, 5f),
    ProjectileType.CLUSTER_BOMB -> lobbed(0.55f, 0.55f, 0.45f, 20f),

    // ── Nature/Beast (90-93) ──
    ProjectileType.VENOM_BOLT   -> (drawVenomBolt _),
    ProjectileType.WEB_SHOT     -> (drawWebShot _),
    ProjectileType.STINGER      -> physProj(0.45f, 0.9f, 1f, 5f),
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
    ProjectileType.KNIFE        -> spinner(0.8f, 0.82f, 0.85f, 16f, 2),
    ProjectileType.STING        -> energyBolt(0.45f, 0.9f, 1f, 16f),
    ProjectileType.HAMMER       -> lobbed(0.55f, 0.55f, 0.6f, 18f),
    ProjectileType.HORN         -> physProj(0.65f, 0.6f, 0.45f, 6f),
    ProjectileType.MYSTIC_BOLT  -> energyBolt(0.55f, 0.35f, 0.85f, 20f),
    ProjectileType.PETRIFY      -> beamProj(0.65f, 0.6f, 0.5f, 7f, 8f),
    ProjectileType.GRAB         -> beamProj(0.5f, 0.4f, 0.25f, 8f, 9f),
    ProjectileType.JAW          -> (drawJaw _),
    ProjectileType.TONGUE       -> beamProj(0.9f, 0.35f, 0.4f, 8f, 8f),
    ProjectileType.ACID_FLASK   -> (drawVenomBolt _),

    // ── Roster audit: new differentiation projectiles (112+) ──
    ProjectileType.BOOMERANG_BLADE -> spinner(0.55f, 0.15f, 0.25f, 24f, 2),
    ProjectileType.VORTEX_BOMB     -> aoeRing(0.35f, 0.15f, 0.55f, 45f),
    ProjectileType.CHAIN_LIGHTNING_FORK -> (drawLightning _),
    ProjectileType.SNIPER_BEAM     -> beamProj(0.3f, 0.6f, 1f, 10f, 4f),
    ProjectileType.MOMENTUM_STRIKE -> wave(0.95f, 0.6f, 0.2f, 30f),
    ProjectileType.LEECH_BOLT      -> energyBolt(0.5f, 0.15f, 0.35f, 22f),
    ProjectileType.RICOCHET_SHARD  -> physProj(0.55f, 0.9f, 1f, 5f),
    ProjectileType.FLAME_WAVE      -> wave(1f, 0.5f, 0.1f, 35f),
    ProjectileType.POISON_CLOUD    -> aoeRing(0.3f, 0.75f, 0.2f, 50f),
    ProjectileType.BONE_BOOMERANG  -> spinner(0.92f, 0.88f, 0.8f, 20f, 2),
    ProjectileType.GRAVITY_LANCE   -> beamProj(0.35f, 0.15f, 0.55f, 8f, 6f),
    ProjectileType.SHADOW_HAUNT    -> (drawVoidBolt _),
    ProjectileType.CHARGE_FIST     -> fistProj(0.85f, 0.65f, 0.35f),
    ProjectileType.ACID_SPRAY      -> wave(0.25f, 0.85f, 0.3f, 28f),
    ProjectileType.ECHO_BOLT       -> energyBolt(0.7f, 0.5f, 0.95f, 20f),
    ProjectileType.FLAME_TRAIL     -> energyBolt(1f, 0.6f, 0.15f, 22f),
    ProjectileType.STAR_BOLT       -> energyBolt(0.95f, 0.9f, 0.45f, 22f),
    ProjectileType.RUNE_BOLT       -> energyBolt(0.9f, 0.75f, 0.35f, 18f),
    ProjectileType.SOUL_HARVEST    -> aoeRing(0.3f, 0.85f, 0.25f, 45f),
    ProjectileType.OVERCLOCK_BEAM  -> aoeRing(0.2f, 0.9f, 0.7f, 40f),
    ProjectileType.NAPALM_STRIKE   -> lobbed(0.95f, 0.45f, 0.1f, 22f),
    ProjectileType.THROWN_BOULDER  -> (drawBoulder _),
    ProjectileType.EYE_BEAM        -> beamProj(0.9f, 0.25f, 0.2f, 8f, 6f)
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
      val finXs = Array(sx, (sx - nx * 6f + perpX * f * 11f).toFloat, (sx - nx * 18f).toFloat)
      val finYs = Array(sy, (sy - ny * 6f + perpY * f * 11f).toFloat, (sy - ny * 18f).toFloat)
      sb.fillPolygon(finXs, finYs, 0.45f, 0.48f, 0.35f, 0.85f * p)
      sb.strokePolygon(finXs, finYs, 2f, 0.15f, 0.15f, 0.12f, 0.7f * p)
    ; f += 2 }
    // Nosecone — bigger with outline
    val noseX = tipX + nx * 20f; val noseY = tipY + ny * 20f
    val noseXs = Array(noseX, tipX + perpX * 8f, tipX - perpX * 8f)
    val noseYs = Array(noseY, tipY + perpY * 8f, tipY - perpY * 8f)
    sb.fillPolygon(noseXs, noseYs, 0.7f, 0.7f, 0.65f, 0.95f * p)
    sb.strokePolygon(noseXs, noseYs, 2.5f, 0.15f, 0.15f, 0.12f, 0.8f * p)
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

  /** Boulder - large rolling rock with dust */
  private def drawBoulder(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 19) * 0.3
    val bounce = (Math.abs(Math.sin(phase)) * 5f).toFloat
    val r = 18f
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy

    // Shadow
    sb.fillOval(sx, sy + r * 0.35f, r * 1.1f, r * 0.35f, 0f, 0f, 0f, 0.2f, 14)
    // Main rock
    sb.fillOval(sx, sy - bounce, r, r * 0.9f, 0.4f, 0.32f, 0.2f, 0.95f, 16)
    // Cracks
    sb.strokeLine(sx - 5, sy - bounce - 4, sx + 6, sy - bounce + 5, 1.5f, 0.2f, 0.14f, 0.08f, 0.6f)
    sb.strokeLine(sx + 3, sy - bounce - 6, sx - 4, sy - bounce + 3, 1.2f, 0.2f, 0.14f, 0.08f, 0.5f)
    // Highlight
    sb.fillOval(sx + Math.cos(phase).toFloat * 4, sy - bounce - r * 0.55f, r * 0.35f, r * 0.25f, 0.55f, 0.45f, 0.3f, 0.5f, 10)

    // Dust cloud
    var i = 0; while (i < 6) {
      val t = ((tick * 0.06 + i * 0.167 + proj.id * 0.11) % 1.0).toFloat
      val s = 4f + t * 7f
      sb.fillOval(sx - ndx * t * 24 + Math.sin(phase + i * 2).toFloat * 5, sy - ndy * t * 16 + t * 8,
        s, s * 0.65f, 0.55f, 0.45f, 0.3f, 0.35f * (1f - t), 8)
    ; i += 1 }
  }

  /** Tidal Wave - tall water crescent */
  private def drawTidalWave(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 29) * 0.35
    val p = (0.85 + 0.15 * Math.sin(phase)).toFloat
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val perpX = -ndy; val perpY = ndx

    val crestW = 38f; val crestH = 24f
    var i = 0; while (i < 10) {
      val t = i.toFloat / 9
      val off = (t - 0.5f) * 2f
      val height = (1f - off * off) * crestH * p
      _waveXs(i) = sx + perpX * off * crestW + ndx * height * 0.3f
      _waveYs(i) = sy + perpY * off * crestW * 0.6f + ndy * height * 0.3f - height
    ; i += 1 }
    // Thick wave body
    { var i = 0; while (i < 8) { sb.strokeLine(_waveXs(i), _waveYs(i), _waveXs(i + 1), _waveYs(i + 1), 10f, 0.15f, 0.35f, 0.65f, 0.55f * p); i += 1 } }
    { var i = 0; while (i < 8) { sb.strokeLine(_waveXs(i), _waveYs(i), _waveXs(i + 1), _waveYs(i + 1), 6f, 0.25f, 0.5f, 0.85f, 0.7f * p); i += 1 } }
    { var i = 0; while (i < 8) { sb.strokeLine(_waveXs(i), _waveYs(i), _waveXs(i + 1), _waveYs(i + 1), 2.5f, 0.45f, 0.75f, 1f, 0.8f * p); i += 1 } }
    // Foam cap
    { var i = 2; while (i < 7) { sb.strokeLine(_waveXs(i), _waveYs(i), _waveXs(i + 1), _waveYs(i + 1), 3f, 0.85f, 0.95f, 1f, 0.5f * p); i += 1 } }
    // Flickering white foam detail dots
    { var i = 0; while (i < 5) {
      val foamT = ((tick * 0.09 + i * 0.2 + proj.id * 0.07) % 1.0).toFloat
      val foamIdx = 2 + (i % 6)
      if (foamIdx < 9) {
        val fx = _waveXs(foamIdx) + Math.sin(phase * 5 + i * 3.1).toFloat * 3f
        val fy = _waveYs(foamIdx) + Math.cos(phase * 4 + i * 2.7).toFloat * 2f
        sb.fillOval(fx, fy, 2f, 1.5f, 1f, 1f, 1f, (0.3 + 0.3 * Math.sin(phase * 6 + i * 1.9)).toFloat * p, 4)
      }
    ; i += 1 } }

    // Base splash
    sb.fillOvalSoft(sx, sy, 36f, 16f, 0.2f, 0.45f, 0.8f, 0.2f * p, 0f, 14)

    // Spray
    { var i = 0; while (i < 6) {
      val t = ((tick * 0.06 + i * 0.167 + proj.id * 0.11) % 1.0).toFloat
      val dropX = _waveXs(1 + i) + ndx * t * 12 + Math.sin(phase + i * 2).toFloat * 5
      val dropY = _waveYs(1 + i) + ndy * t * 12 - t * 8
      sb.fillOval(dropX, dropY, 4f, 3f, 0.7f, 0.9f, 1f, 0.5f * (1f - t) * p, 6)
    ; i += 1 } }
  }

  /** Geyser - tall water eruption */
  private def drawGeyser(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 23) * 0.4
    val p = (0.85 + 0.15 * Math.sin(phase)).toFloat

    // Column
    var i = 0; while (i < 12) {
      val t = ((tick * 0.05 + i * 0.083 + proj.id * 0.11) % 1.0).toFloat
      val spread = Math.sin(phase * 2 + i * 2.3).toFloat * (5f + t * 12f)
      val py = sy - t * 45f
      val s = 4f + t * 7f
      sb.fillOval(sx + spread, py, s, s * 0.7f, 0.25f, 0.55f, 0.95f, 0.55f * (1f - t) * p, 8)
    ; i += 1 }

    // Base pool
    sb.fillOval(sx, sy, 24f, 8f, 0.2f, 0.45f, 0.85f, 0.45f * p, 14)
    sb.fillOval(sx, sy, 16f, 5f, 0.45f, 0.75f, 1f, 0.55f * p, 12)

    // Crown droplets
    { var i = 0; while (i < 6) {
      val angle = phase + i * Math.PI / 3
      val dist = 8f + Math.sin(phase * 2 + i).toFloat * 5f
      sb.fillOval(sx + Math.cos(angle).toFloat * dist, sy - 40f + Math.sin(angle).toFloat * dist * 0.3f,
        4f, 3f, 0.5f, 0.85f, 1f, 0.5f * p, 6)
    ; i += 1 } }
  }

  /** Sword Wave - energy crescent */
  private def drawSwordWave(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 37) * 0.4
    val p = (0.9 + 0.1 * Math.sin(phase)).toFloat
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val arcR = 30f
    val segs = 12
    val baseAngle = Math.atan2(ndy, ndx)

    var i = 0; while (i <= segs) {
      val a = baseAngle - Math.PI * 0.4 + Math.PI * 0.8 * i / segs
      _swOuterXs(i) = (sx + Math.cos(a).toFloat * arcR)
      _swOuterYs(i) = (sy + Math.sin(a).toFloat * arcR * 0.55f)
      _swInnerXs(i) = (sx + Math.cos(a).toFloat * arcR * 0.4f)
      _swInnerYs(i) = (sy + Math.sin(a).toFloat * arcR * 0.22f)
    ; i += 1 }

    { var i = 0; while (i <= segs) { _swCrescXs(i) = _swOuterXs(i); _swCrescYs(i) = _swOuterYs(i); i += 1 } }
    { var i = 0; while (i <= segs) { _swCrescXs(segs + 1 + i) = _swInnerXs(segs - i); _swCrescYs(segs + 1 + i) = _swInnerYs(segs - i); i += 1 } }

    // Crescent fill (solid)
    sb.fillPolygon(_swCrescXs, _swCrescYs, (segs + 1) * 2, 0.65f, 0.65f, 0.85f, 0.6f * p)
    // Bright outer edge
    var j = 0; while (j < segs) { sb.strokeLine(_swOuterXs(j), _swOuterYs(j), _swOuterXs(j + 1), _swOuterYs(j + 1),
      3f, 0.9f, 0.9f, 1f, 0.9f * p); j += 1 }
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
      sb.fillPolygon(Array(bx, bx - wingW, bx - wingW * 0.5f),
        Array(by, by - wingH, by + 2 * batScale), 0.08f, 0f, 0.12f, 0.8f * p)
      sb.fillPolygon(Array(bx, bx + wingW, bx + wingW * 0.5f),
        Array(by, by - wingH, by + 2 * batScale), 0.08f, 0f, 0.12f, 0.8f * p)
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

  /** Raise Dead - skeleton hands from the ground */
  private def drawRaiseDead(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 29) * 0.35
    val p = (0.85 + 0.15 * Math.sin(phase)).toFloat

    // Ground disturbance
    sb.fillOval(sx, sy + 5, 30f, 10f, 0.05f, 0.18f, 0.02f, 0.3f * p, 14)

    var hand = 0; while (hand < 4) {
      val hx = sx + (hand - 1.5f) * 14f
      val rise = ((tick * 0.035 + hand * 0.25) % 1.0).toFloat
      val hy = sy - rise * 30f
      val a = 0.8f * (1f - rise * 0.3f) * p
      sb.strokeLine(hx, sy, hx + Math.sin(phase + hand).toFloat * 4, hy, 3.5f, 0.7f, 0.65f, 0.5f, a)
      var f = -1; while (f <= 1) {
        val fx = hx + f * 5f + Math.sin(phase + hand + f).toFloat * 3
        sb.strokeLine(hx + Math.sin(phase + hand).toFloat * 4, hy, fx, hy - 8, 2.5f, 0.75f, 0.7f, 0.55f, a * 0.85f)
      ; f += 1 }
    ; hand += 1 }

    // Soul wisps
    var i = 0; while (i < 6) {
      val angle = phase * 1.5 + i * Math.PI / 3
      val dist = 14f + Math.sin(phase * 2 + i * 1.3).toFloat * 5
      sb.fillOval(sx + Math.cos(angle).toFloat * dist, sy + Math.sin(angle).toFloat * dist * 0.35f - 12,
        3.5f, 3f, 0.3f, 0.9f, 0.2f, (0.4 + 0.2 * Math.sin(phase * 3 + i * 2)).toFloat * p, 6)
    ; i += 1 }
  }

  /** Wail - ghostly screaming face */
  private def drawWail(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 41) * 0.4
    val p = (0.75 + 0.25 * Math.sin(phase * 3)).toFloat
    val wobble = Math.sin(phase * 2).toFloat * 4

    // Ghost body
    sb.fillOvalSoft(sx + wobble, sy, 32f, 26f, 0.7f, 0.8f, 0.92f, 0.3f * p, 0f, 16)
    sb.fillOval(sx + wobble, sy, 22f, 18f, 0.75f, 0.85f, 0.95f, 0.55f * p, 14)

    // Eye sockets
    sb.fillOval(sx - 6.5f + wobble, sy - 4f, 4f, 3.5f, 0.05f, 0.05f, 0.12f, 0.85f * p, 10)
    sb.fillOval(sx + 7.5f + wobble, sy - 4f, 4f, 3.5f, 0.05f, 0.05f, 0.12f, 0.85f * p, 10)
    // Eye glow
    sb.fillOval(sx - 6.5f + wobble, sy - 4f, 2f, 1.5f, 0.5f, 0.7f, 0.9f, 0.4f * p, 6)
    sb.fillOval(sx + 7.5f + wobble, sy - 4f, 2f, 1.5f, 0.5f, 0.7f, 0.9f, 0.4f * p, 6)

    // Mouth
    val mouthOpen = 6f + Math.abs(Math.sin(phase * 4)).toFloat * 7f
    sb.fillOval(sx + wobble, sy + 3 + mouthOpen / 2, 6f, mouthOpen / 2, 0.05f, 0.05f, 0.1f, 0.8f * p, 10)

    // 3 expanding sonic rings from mouth
    screenDir(proj)
    val sndx = _sdx; val sndy = _sdy
    var ring = 0; while (ring < 3) {
      val rp = ((phase * 0.45 + ring * 0.33) % 1.0).toFloat
      val ringR = 5f + rp * 25f
      val ringX = sx + wobble + sndx * rp * 20f
      val ringY = sy + 3 + sndy * rp * 12f
      sb.strokeOval(ringX, ringY, ringR, ringR * 0.4f, 1.5f * (1f - rp),
        0.7f, 0.8f, 0.92f, 0.3f * (1f - rp) * p, 8)
    ; ring += 1 }

    // Ectoplasm trails
    var i = 0; while (i < 6) {
      val t = ((tick * 0.05 + i * 0.167 + proj.id * 0.13) % 1.0).toFloat
      val wx = sx + wobble + Math.sin(phase * 2 + i * 1.8).toFloat * 10
      sb.fillOval(wx, sy + 7 + t * 26, 4f + t * 5f, 3f + t * 3f, 0.6f, 0.75f, 0.85f, 0.5f * (1f - t) * p, 8)
    ; i += 1 }
  }

  /** Devour - large chomping jaws */
  private def drawDevour(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 23) * 0.45
    val chomp = (Math.abs(Math.sin(phase * 3)) * 0.6).toFloat
    val jawOpen = 6f + chomp * 10f
    val p = (0.9 + 0.1 * Math.sin(phase)).toFloat

    // Jaws (solid)
    sb.fillOval(sx, sy - jawOpen, 14f, 7f, 0.2f, 0.06f, 0.08f, 0.9f, 12)
    sb.fillOval(sx, sy + jawOpen, 14f, 7f, 0.2f, 0.06f, 0.08f, 0.9f, 12)
    sb.fillOval(sx, sy, 8f, 5f, 0.7f, 0.08f, 0.05f, 0.5f * (1f - chomp), 10)

    // Teeth (6 per jaw)
    var i = 0; while (i < 6) {
      val tx = sx - 9 + i * 3.6f
      sb.fillPolygon(Array(tx, tx + 1.8f, tx + 3.6f), Array(sy - jawOpen + 3, sy - jawOpen + 8, sy - jawOpen + 3),
        0.95f, 0.92f, 0.85f, 0.85f * p)
      sb.fillPolygon(Array(tx, tx + 1.8f, tx + 3.6f), Array(sy + jawOpen - 3, sy + jawOpen - 8, sy + jawOpen - 3),
        0.95f, 0.92f, 0.85f, 0.85f * p)
    ; i += 1 }
  }

  /** Scythe - large spinning curved blade */
  private def drawScythe(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val spin = tick * 0.3 + proj.id * 2.3
    val p = (0.9 + 0.1 * Math.sin(spin * 2)).toFloat
    val bladeR = 28f
    val startAngle = spin
    val arcLen = Math.PI * 0.75
    val segs = 12

    var i = 0; while (i <= segs) {
      val a = startAngle + arcLen * i / segs
      _scyXs(i) = (sx + Math.cos(a).toFloat * bladeR)
      _scyYs(i) = (sy + Math.sin(a).toFloat * bladeR * 0.6f)
    ; i += 1 }
    { var i = 0; while (i <= segs) {
      val a = startAngle + arcLen * (segs - i) / segs
      _scyXs(segs + 1 + i) = (sx + Math.cos(a).toFloat * bladeR * 0.4f)
      _scyYs(segs + 1 + i) = (sy + Math.sin(a).toFloat * bladeR * 0.24f)
    ; i += 1 } }
    // Solid blade
    sb.fillPolygon(_scyXs, _scyYs, segs * 2 + 2, 0.7f, 0.72f, 0.78f, 0.8f * p)
    // Bright outer edge
    var j = 0; while (j < segs) {
      val a1 = startAngle + arcLen * j / segs; val a2 = startAngle + arcLen * (j + 1) / segs
      sb.strokeLine((sx + Math.cos(a1).toFloat * bladeR), (sy + Math.sin(a1).toFloat * bladeR * 0.6f),
        (sx + Math.cos(a2).toFloat * bladeR), (sy + Math.sin(a2).toFloat * bladeR * 0.6f), 3f, 0.92f, 0.93f, 0.96f, 0.9f * p)
    ; j += 1 }
    // Handle
    val ha = startAngle + arcLen + 0.3
    sb.strokeLine(sx, sy, (sx + Math.cos(ha).toFloat * 16), (sy + Math.sin(ha).toFloat * 10), 4f, 0.45f, 0.35f, 0.25f, 0.85f)
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

  /** Jaw - large shark jaws with teeth */
  private def drawJaw(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    beamTip(sx, sy, proj, 7f)
    val tipX = _tipX; val tipY = _tipY
    val phase = (tick + proj.id * 17) * 0.4
    val p = (0.9 + 0.1 * Math.sin(phase)).toFloat
    val chomp = (Math.abs(Math.sin(phase * 3)) * 0.4 + 0.6).toFloat
    val dx = tipX - sx; val dy = tipY - sy
    val len = Math.sqrt(dx * dx + dy * dy).toFloat
    if (len < 1) return
    val nx = dx / len; val ny = dy / len
    val perpX = -ny; val perpY = nx

    // Water trail
    var i = 1; while (i <= 4) {
      val t = ((tick * 0.06 + i * 0.25 + proj.id * 0.13) % 1.0).toFloat
      sb.fillOval(tipX - nx * t * 30, tipY - ny * t * 30, 6f, 3f, 0.3f, 0.5f, 0.7f, 0.2f * (1f - t) * p, 8)
    ; i += 1 }

    // Upper jaw (solid)
    val jawW = 16f * p * chomp; val jawLen = 20f * p
    sb.fillPolygon(Array(tipX, tipX - nx * jawLen + perpX * jawW, tipX - nx * jawLen),
      Array(tipY, tipY - ny * jawLen + perpY * jawW, tipY - ny * jawLen), 0.45f, 0.5f, 0.55f, 0.9f * p)
    // Lower jaw
    sb.fillPolygon(Array(tipX, tipX - nx * jawLen - perpX * jawW, tipX - nx * jawLen),
      Array(tipY, tipY - ny * jawLen - perpY * jawW, tipY - ny * jawLen), 0.4f, 0.45f, 0.5f, 0.9f * p)

    // Teeth (5 per jaw)
    { var i = 0; while (i < 5) {
      val t = (i + 0.5f) / 5f
      val toothBase = 0.2f + t * 0.7f
      val utx = tipX - nx * jawLen * toothBase + perpX * jawW * (1f - t) * 0.9f
      val uty = tipY - ny * jawLen * toothBase + perpY * jawW * (1f - t) * 0.9f
      val th = 5f * p
      sb.fillPolygon(Array(utx - perpX * th, utx + nx * 2.5f, utx + perpX * 0.5f),
        Array(uty - perpY * th, uty + ny * 2.5f, uty + perpY * 0.5f), 0.95f, 0.95f, 0.9f, 0.85f * p)
      val ltx = tipX - nx * jawLen * toothBase - perpX * jawW * (1f - t) * 0.9f
      val lty = tipY - ny * jawLen * toothBase - perpY * jawW * (1f - t) * 0.9f
      sb.fillPolygon(Array(ltx + perpX * th, ltx + nx * 2.5f, ltx - perpX * 0.5f),
        Array(lty + perpY * th, lty + ny * 2.5f, lty - perpY * 0.5f), 0.95f, 0.95f, 0.9f, 0.85f * p)
    ; i += 1 } }

    // Dorsal fin
    val finX = tipX - nx * jawLen * 0.5f; val finY = tipY - ny * jawLen * 0.5f - 10f * p
    sb.fillPolygon(Array(finX, finX + perpX * 4f, finX - perpX * 4f),
      Array(finY - 8f * p, finY + 8f, finY + 8f), 0.42f, 0.47f, 0.52f, 0.8f * p)
  }
}
