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

  // Lightning: segs=6 → 7 entries
  private val _boltXs = new Array[Float](7)
  private val _boltYs = new Array[Float](7)

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

  def drawGeneric(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int,
                  r: Float, g: Float, b: Float): Unit = {
    val phase = (tick + proj.id * 37) * 0.35
    val p = (0.9 + 0.1 * Math.sin(phase)).toFloat
    // Soft glow halo
    sb.fillOvalSoft(sx, sy, 36f, 28f, r, g, b, 0.35f * p, 0f, 16)
    // Core
    sb.fillOval(sx, sy, 18f, 14f, r, g, b, 0.85f * p, 12)
    // Bright center
    sb.fillOval(sx, sy, 8f, 6f, bright(r), bright(g), bright(b), 0.95f, 8)
    // Trail
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    var i = 0; while (i < 6) {
      val t = ((tick * 0.05 + i * 0.16 + proj.id * 0.13) % 1.0).toFloat
      val taper = 1f - t * 0.7f
      val s = 10f * taper
      val colorFade = 1f - t * 0.35f
      sb.fillOval(sx - ndx * t * 36, sy - ndy * t * 36, s, s * 0.7f,
        r * colorFade, g * colorFade, b * colorFade, 0.4f * (1f - t) * p, 8)
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

  @inline private def bright(c: Float): Float = Math.min(1f, c * 0.4f + 0.6f)
  @inline private def dark(c: Float): Float = c * 0.5f
  @inline private def mix(a: Float, b: Float, t: Float): Float = a + (b - a) * t

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

  /** Reusable radial spark particle burst */
  private def drawSparkBurst(sx: Float, sy: Float, r: Float, g: Float, b: Float,
      p: Float, sb: ShapeBatch, tick: Int, projId: Int, count: Int, radius: Float): Unit = {
    var i = 0; while (i < count) {
      val angle = tick * 0.12 + i * Math.PI * 2 / count + projId * 0.7
      val dist = radius * (0.5f + 0.5f * Math.sin(tick * 0.2 + i * 1.7).toFloat)
      val sparkX = sx + Math.cos(angle).toFloat * dist
      val sparkY = sy + Math.sin(angle).toFloat * dist * 0.6f
      val sparkLen = radius * 0.3f
      val ex = sparkX + Math.cos(angle).toFloat * sparkLen
      val ey = sparkY + Math.sin(angle).toFloat * sparkLen * 0.6f
      sb.strokeLine(sparkX, sparkY, ex, ey, 1.5f, bright(r), bright(g), bright(b),
        (0.3 + 0.3 * Math.sin(tick * 0.3 + i * 2.1)).toFloat * p)
    ; i += 1 }
  }

  // ═══════════════════════════════════════════════════════════════
  //  PATTERN FACTORIES
  // ═══════════════════════════════════════════════════════════════

  /** Large glowing energy orb with dual-color core, orbiting sparkles and trail */
  private def energyBolt(r: Float, g: Float, b: Float, size: Float = 20f): Renderer =
    (proj, sx, sy, sb, tick) => {
      val phase = (tick + proj.id * 37) * 0.35
      val p = (0.9 + 0.1 * Math.sin(phase)).toFloat
      val chargeScale = 1f + proj.chargeLevel * 0.002f
      val sz = size * chargeScale

      // Soft halo
      sb.fillOvalSoft(sx, sy, sz * 2.5f, sz * 2f, r, g, b, 0.35f * p, 0f, 16)

      screenDir(proj)
      val ndx = _sdx; val ndy = _sdy

      // Dual-color core
      sb.fillOval(sx, sy, sz * 0.8f, sz * 0.6f, r, g, b, 0.9f * p, 12)
      // Inner core offset in travel direction — subtle brighter shift
      sb.fillOval(sx + ndx * sz * 0.08f, sy + ndy * sz * 0.08f, sz * 0.4f, sz * 0.3f,
        mix(r, 1f, 0.3f), mix(g, 1f, 0.2f), mix(b, 1f, 0.15f), 0.85f * p, 10)
      // Hot center
      sb.fillOval(sx, sy, sz * 0.2f, sz * 0.15f, bright(r), bright(g), bright(b), 0.95f, 8)

      // Orbiting sparkles
      var s = 0; while (s < 2) {
        val sa = phase * 2.2 + s * Math.PI
        val sd = sz * 0.85f
        val spx = sx + Math.cos(sa).toFloat * sd
        val spy = sy + Math.sin(sa).toFloat * sd * 0.65f
        sb.fillOval(spx, spy, 4f, 3f, bright(r), bright(g), bright(b),
          (0.5 + 0.3 * Math.sin(phase * 4 + s * 2.1)).toFloat * p, 6)
      ; s += 1 }

      // Trail — dot trail with color shift
      var i = 0; while (i < 6) {
        val t = ((tick * 0.05 + i * 0.16 + proj.id * 0.13) % 1.0).toFloat
        val taper = 1f - t * 0.7f
        val ts = sz * 0.5f * taper
        val colorFade = 1f - t * 0.4f
        sb.fillOval(sx - ndx * t * sz * 3f, sy - ndy * t * sz * 3f,
          ts, ts * 0.65f, r * colorFade, g * colorFade, b * colorFade, 0.4f * (1f - t) * p, 8)
      ; i += 1 }
    }

  /** Thick solid beam with color gradient and tip flare */
  private def beamProj(r: Float, g: Float, b: Float, worldLen: Float = 6f, width: Float = 8f): Renderer =
    (proj, sx, sy, sb, tick) => {
      beamTip(sx, sy, proj, worldLen)
      val tipX = _tipX; val tipY = _tipY
      val phase = (tick + proj.id * 29) * 0.35
      val p = (0.9 + 0.1 * Math.sin(phase)).toFloat
      val dx = tipX - sx; val dy = tipY - sy
      val midX = sx + dx * 0.5f; val midY = sy + dy * 0.5f

      // Soft outer glow
      sb.strokeLineSoft(sx, sy, tipX, tipY, width * 4f, r, g, b, 0.25f * p)
      // Color gradient: darker base-half, brighter tip-half
      sb.strokeLine(sx, sy, midX, midY, width, dark(r), dark(g), dark(b), 0.8f * p)
      sb.strokeLine(midX, midY, tipX, tipY, width, r, g, b, 0.9f * p)
      // Bright core
      sb.strokeLine(sx, sy, tipX, tipY, width * 0.3f, bright(r), bright(g), bright(b), 0.95f * p)

      // Tip flare
      sb.fillOvalSoft(tipX, tipY, width * 3f, width * 2.2f, r, g, b, 0.4f * p, 0f, 12)
      sb.fillOval(tipX, tipY, width * 1.2f, width * 0.9f, bright(r), bright(g), bright(b), 0.8f * p, 8)

      // Energy nodes along beam
      var i = 0; while (i < 5) {
        val t = ((tick * 0.08 + i * 0.2 + proj.id * 0.11) % 1.0).toFloat
        val taper = 1f - t * 0.4f
        val ns = width * 0.6f * taper * (0.6f + 0.4f * Math.sin(phase * 3 + i * 2.1).toFloat)
        val colorFade = 1f - t * 0.3f
        sb.fillOval(sx + dx * t, sy + dy * t, ns, ns * 0.7f,
          bright(r) * colorFade, bright(g) * colorFade, bright(b) * colorFade, 0.5f * (1f - t * 0.3f) * p, 6)
      ; i += 1 }
    }

  /** Large spinning star/blade weapon with metallic highlight and motion trail */
  private def spinner(r: Float, g: Float, b: Float, size: Float = 22f, pts: Int = 4): Renderer =
    (proj, sx, sy, sb, tick) => {
      val spin = tick * 0.3 + proj.id * 2.1
      val p = (0.9 + 0.1 * Math.sin(spin * 2)).toFloat
      screenDir(proj)
      val ndx = _sdx; val ndy = _sdy
      val n = pts * 2

      // Motion blur trail — 3 ghosts with taper and color fade
      var ghost = 1; while (ghost <= 3) {
        val taper = 1f - ghost * 0.2f
        val colorFade = 1f - ghost * 0.15f
        val ga = 0.22f * (1f - ghost * 0.25f) * p
        val gx = sx - ndx * ghost * 10f
        val gy = sy - ndy * ghost * 10f
        val gSpin = spin - ghost * 0.3
        var i = 0; while (i < n) {
          val angle = gSpin + i * Math.PI / pts
          val rad = if (i % 2 == 0) size * 0.85f * taper else size * 0.3f * taper
          _spinGhostXs(i) = (gx + Math.cos(angle) * rad).toFloat
          _spinGhostYs(i) = (gy + Math.sin(angle) * rad * 0.6f).toFloat
        ; i += 1 }
        sb.fillPolygon(_spinGhostXs, _spinGhostYs, n, r * colorFade, g * colorFade, b * colorFade, ga)
      ; ghost += 1 }

      // Main shape
      { var i = 0; while (i < n) {
        val angle = spin + i * Math.PI / pts
        val rad = if (i % 2 == 0) size else size * 0.3f
        _spinXs(i) = (sx + Math.cos(angle) * rad).toFloat
        _spinYs(i) = (sy + Math.sin(angle) * rad * 0.6f).toFloat
      ; i += 1 } }
      sb.fillPolygon(_spinXs, _spinYs, n, r, g, b, 0.9f * p)
      // Edge highlight
      sb.strokePolygon(_spinXs, _spinYs, n, 2f, bright(r), bright(g), bright(b), 0.8f * p)

      // Metallic specular highlight — smaller bright polygon offset toward upper-left
      { var i = 0; while (i < n) {
        val angle = spin + i * Math.PI / pts
        val rad = if (i % 2 == 0) size * 0.55f else size * 0.18f
        _spinGhostXs(i) = (sx - 2f + Math.cos(angle) * rad).toFloat
        _spinGhostYs(i) = (sy - 2f + Math.sin(angle) * rad * 0.6f).toFloat
      ; i += 1 } }
      sb.fillPolygon(_spinGhostXs, _spinGhostYs, n, bright(r), bright(g), bright(b), 0.25f * p)

      // Two-layer center hub
      sb.strokeOval(sx, sy, size * 0.2f, size * 0.14f, 2f, dark(r), dark(g), dark(b), 0.5f * p, 8)
      sb.fillOval(sx, sy, size * 0.13f, size * 0.09f, bright(r), bright(g), bright(b), 0.7f * p, 6)
    }

  /** Solid physical projectile (arrow, spear, bullet) with prominent head and clean trail */
  private def physProj(r: Float, g: Float, b: Float, worldLen: Float = 6f): Renderer =
    (proj, sx, sy, sb, tick) => {
      beamTip(sx, sy, proj, worldLen)
      val tipX = _tipX; val tipY = _tipY
      val phase = (tick + proj.id * 31) * 0.4
      val p = (0.9 + 0.1 * Math.sin(phase)).toFloat
      screenDir(proj)
      val ndx = _sdx; val ndy = _sdy
      val perpX = -ndy; val perpY = ndx

      // Shaft — dark outer, colored inner, bright center line
      sb.strokeLine(sx, sy, tipX, tipY, 5f, dark(r), dark(g), dark(b), 0.85f * p)
      sb.strokeLine(sx, sy, tipX, tipY, 2.5f, r, g, b, 0.7f * p)
      sb.strokeLine(sx, sy, tipX, tipY, 1f, bright(r), bright(g), bright(b), 0.4f * p)

      // Bold arrowhead
      val headLen = 14f; val headW = 7f
      val pointX = tipX + ndx * headLen; val pointY = tipY + ndy * headLen
      sb.fillPolygon(Array(pointX, tipX + perpX * headW, tipX - perpX * headW),
        Array(pointY, tipY + perpY * headW, tipY - perpY * headW),
        bright(r), bright(g), bright(b), 0.9f * p)
      sb.strokePolygon(Array(pointX, tipX + perpX * headW, tipX - perpX * headW),
        Array(pointY, tipY + perpY * headW, tipY - perpY * headW),
        1.5f, r, g, b, 0.7f * p)

      // Angled fletching — swept-back lines from tail
      { var f = -1; while (f <= 1) {
        val tailX = sx - ndx * 4f; val tailY = sy - ndy * 4f
        val featherX = tailX - ndx * 12f + perpX * f * 6f
        val featherY = tailY - ndy * 12f + perpY * f * 6f
        sb.strokeLine(tailX, tailY, featherX, featherY, 2.5f, dark(r), dark(g), dark(b), 0.6f * p)
        sb.strokeLine(tailX, tailY, featherX, featherY, 1f, r, g, b, 0.35f * p)
      ; f += 2 } }

      // Trail — tapered dot trail behind projectile
      var i = 0; while (i < 6) {
        val t = ((tick * 0.05 + i * 0.16 + proj.id * 0.11) % 1.0).toFloat
        val taper = 1f - t * 0.8f
        val colorFade = 1f - t * 0.35f
        sb.fillOval(sx - ndx * t * 30, sy - ndy * t * 30,
          3.5f * taper, 2.5f * taper, r * colorFade, g * colorFade, b * colorFade, 0.35f * (1f - t) * p, 6)
      ; i += 1 }
    }

  /** Large bouncing thrown object with squash/stretch, 3D shading, and dynamic shadow */
  private def lobbed(r: Float, g: Float, b: Float, size: Float = 18f): Renderer =
    (proj, sx, sy, sb, tick) => {
      val phase = (tick + proj.id * 13) * 0.3
      val bounceRaw = Math.sin(phase * 1.5).toFloat
      val bounce = (Math.abs(bounceRaw) * 8f).toFloat
      val spin = tick * 0.15f + proj.id * 1.3f
      val p = (0.9 + 0.1 * Math.sin(phase)).toFloat

      // Squash and stretch based on bounce phase
      val stretchY = 1f + bounceRaw * bounceRaw * 0.12f
      val stretchX = 1f - bounceRaw * bounceRaw * 0.06f

      // Dynamic shadow
      val shadowScale = 1f + bounce * 0.02f
      val shadowAlpha = Math.max(0.1f, 0.25f - bounce * 0.012f)
      sb.fillOval(sx, sy + size * 0.3f, size * 0.9f * shadowScale, size * 0.25f, 0f, 0f, 0f, shadowAlpha, 12)

      // Outer glow
      sb.fillOvalSoft(sx, sy - bounce, size * 1.8f * stretchX, size * 1.4f * stretchY, r, g, b, 0.25f * p, 0f, 14)
      // Main body
      sb.fillOval(sx, sy - bounce, size * stretchX, size * 0.8f * stretchY, r, g, b, 0.95f, 14)
      // Bottom shading
      sb.fillOval(sx, sy - bounce + size * 0.15f * stretchY, size * 0.85f * stretchX, size * 0.5f * stretchY,
        dark(r), dark(g), dark(b), 0.3f, 12)
      // Top highlight
      sb.fillOval(sx - size * 0.15f, sy - bounce - size * 0.2f * stretchY,
        size * 0.35f * stretchX, size * 0.25f * stretchY, bright(r), bright(g), bright(b), 0.5f, 8)

      // Surface cross marks
      var v = 0; while (v < 3) {
        val a = spin + v * Math.PI * 2 / 3
        val vx = (sx + Math.cos(a) * size * 0.5f * stretchX).toFloat
        val vy = (sy - bounce + Math.sin(a) * size * 0.38f * stretchY).toFloat
        sb.strokeLine(sx, sy - bounce, vx, vy, 1.5f, dark(r), dark(g), dark(b), 0.3f * p)
      ; v += 1 }
    }

  /** Large expanding ring AoE with 3 color-shifting rings and radial lines */
  private def aoeRing(r: Float, g: Float, b: Float, maxR: Float = 50f): Renderer =
    (proj, sx, sy, sb, tick) => {
      val phase = (tick + proj.id * 23) * 0.4
      val p = (0.85 + 0.15 * Math.sin(phase * 2)).toFloat

      // Ground fill
      sb.fillOvalSoft(sx, sy, maxR * 0.6f, maxR * 0.28f, r, g, b, 0.2f * p, 0f, 16)

      // 3 expanding rings with color shift as they expand
      var ring = 0; while (ring < 3) {
        val rp = ((phase * 0.3 + ring * 0.33) % 1.0).toFloat
        val ringR = 6f + rp * maxR
        val a = Math.max(0f, 0.65f * (1f - rp) * p)
        val colorShift = rp * 0.3f
        sb.strokeOval(sx, sy, ringR, ringR * 0.45f, 4f * (1f - rp * 0.3f),
          mix(r, bright(r), colorShift), mix(g, bright(g), colorShift), mix(b, bright(b), colorShift), a, 12)
      ; ring += 1 }

      // Radial spark lines
      var spark = 0; while (spark < 4) {
        val sa = phase * 0.6 + spark * Math.PI / 2
        val sl = maxR * 0.35f * p
        sb.strokeLine(sx, sy, sx + Math.cos(sa).toFloat * sl, sy + Math.sin(sa).toFloat * sl * 0.45f,
          2f, r, g, b, 0.25f * p)
      ; spark += 1 }

      // Core
      sb.fillOval(sx, sy, 10f, 6f, r, g, b, 0.7f * p, 10)
      sb.fillOval(sx, sy, 5f, 3f, bright(r), bright(g), bright(b), 0.9f * p, 8)
    }

  /** Thick zigzag chain/bolt with irregular jitter and branch forks */
  private def chainProj(r: Float, g: Float, b: Float, worldLen: Float = 6f): Renderer =
    (proj, sx, sy, sb, tick) => {
      beamTip(sx, sy, proj, worldLen)
      val tipX = _tipX; val tipY = _tipY
      val phase = (tick + proj.id * 23) * 0.3
      val p = (0.9 + 0.1 * Math.sin(phase)).toFloat
      val dx = tipX - sx; val dy = tipY - sy
      val len = Math.sqrt(dx * dx + dy * dy).toFloat
      if (len >= 1) {
        val nx = -dy / len; val ny = dx / len
        val zigW = 8f * p
        val segs = 8

        var i = 0; while (i < segs) {
          val t0 = i.toFloat / segs; val t1 = (i + 1).toFloat / segs
          // Irregular jitter per-segment
          val jitter0 = Math.sin(phase * 2.3 + i * 1.7).toFloat * 3f
          val jitter1 = Math.sin(phase * 2.3 + (i + 1) * 1.7).toFloat * 3f
          val z0 = (if (i % 2 == 0) zigW else -zigW) + jitter0
          val z1 = (if ((i + 1) % 2 == 0) zigW else -zigW) + jitter1
          val x0 = sx + dx * t0 + nx * z0; val y0 = sy + dy * t0 + ny * z0
          val x1 = sx + dx * t1 + nx * z1; val y1 = sy + dy * t1 + ny * z1

          // Main segment
          sb.strokeLine(x0, y0, x1, y1, 5f, r, g, b, 0.85f * p)
          if (i % 2 == 0) sb.strokeLine(x0, y0, x1, y1, 2f, bright(r), bright(g), bright(b), 0.7f * p)

          // Short branch forks at alternating joints
          if (i % 2 == 1 && i < segs - 1) {
            val forkLen = 8f + Math.sin(phase * 3 + i * 1.3).toFloat * 3f
            val forkAngle = if (i % 4 == 1) 0.6 else -0.6
            val forkX = x1 + Math.cos(Math.atan2(ny, nx) + forkAngle).toFloat * forkLen
            val forkY = y1 + Math.sin(Math.atan2(ny, nx) + forkAngle).toFloat * forkLen
            sb.strokeLine(x1, y1, forkX, forkY, 2f, r, g, b, 0.35f * p)
          }
        ; i += 1 }

        // Bright nodes at joints
        { var i = 0; while (i <= segs) {
          val t = i.toFloat / segs
          val jitter = Math.sin(phase * 2.3 + i * 1.7).toFloat * 3f
          val z = (if (i % 2 == 0) zigW else -zigW) + jitter
          val jx = sx + dx * t + nx * z; val jy = sy + dy * t + ny * z
          sb.fillOval(jx, jy, 5f, 4f, bright(r), bright(g), bright(b), 0.7f * p, 6)
        ; i += 4 } }

        // Tip
        sb.fillOval(tipX, tipY, 6f, 4.5f, bright(r), bright(g), bright(b), 0.6f * p, 8)
      }
    }

  /** Wide crescent wave/slash effect */
  private def wave(r: Float, g: Float, b: Float, spread: Float = 32f): Renderer =
    (proj, sx, sy, sb, tick) => {
      val phase = (tick + proj.id * 41) * 0.4
      val p = (0.85 + 0.15 * Math.sin(phase)).toFloat
      screenDir(proj)
      val ndx = _sdx; val ndy = _sdy
      val perpX = -ndy; val perpY = ndx
      val tipX = sx + ndx * spread; val tipY = sy + ndy * spread * 0.6f
      val w1x = sx + perpX * spread; val w1y = sy + perpY * spread * 0.6f
      val w2x = sx - perpX * spread; val w2y = sy - perpY * spread * 0.6f

      // Main crescent fill
      sb.fillPolygon(Array(tipX, w1x, sx - ndx * 4, w2x), Array(tipY, w1y, sy - ndy * 3, w2y), r, g, b, 0.45f * p)

      // Bright leading edge
      sb.strokePolygon(Array(tipX, w1x, sx - ndx * 4, w2x), Array(tipY, w1y, sy - ndy * 3, w2y),
        3f, bright(r), bright(g), bright(b), 0.8f * p)

      // Internal energy lines
      var i = 0; while (i < 3) {
        val off = (i - 1) * spread * 0.28f
        sb.strokeLine(sx + perpX * off - ndx * 3, sy + perpY * off * 0.6f - ndy * 2,
          sx + perpX * off + ndx * spread * 0.5f, sy + perpY * off * 0.6f + ndy * spread * 0.3f,
          1.5f, r, g, b, 0.2f * p)
      ; i += 1 }

      // Scatter particles at edge
      { var i = 0; while (i < 3) {
        val t = ((tick * 0.08 + i * 0.3 + proj.id * 0.11) % 1.0).toFloat
        val pOff = (i.toFloat / 2 - 0.5f) * 2f
        val bx = tipX * (1f - Math.abs(pOff)) + (if (pOff < 0) w2x else w1x) * Math.abs(pOff)
        val by = tipY * (1f - Math.abs(pOff)) + (if (pOff < 0) w2y else w1y) * Math.abs(pOff)
        sb.fillOval(bx + ndx * t * 8, by + ndy * t * 5, 4f, 3f, bright(r), bright(g), bright(b),
          Math.max(0f, 0.5f * (1f - t) * p), 6)
      ; i += 1 } }
    }

  // ═══════════════════════════════════════════════════════════════
  //  REGISTRY (all 112 types)
  // ═══════════════════════════════════════════════════════════════

  private val registry: Map[Byte, Renderer] = Map(
    // ── Original (0-30) ──
    ProjectileType.NORMAL       -> (drawNormal _),
    ProjectileType.TENTACLE     -> (drawTentacle _),
    ProjectileType.ICE_BEAM     -> beamProj(0.4f, 0.8f, 1f, 7f, 9f),
    ProjectileType.AXE          -> spinner(0.65f, 0.55f, 0.4f, 22f, 4),
    ProjectileType.ROPE         -> chainProj(0.7f, 0.5f, 0.3f, 6f),
    ProjectileType.SPEAR        -> physProj(0.55f, 0.5f, 0.35f, 7f),
    ProjectileType.SOUL_BOLT    -> energyBolt(0.3f, 0.9f, 0.4f, 20f),
    ProjectileType.HAUNT        -> energyBolt(0.5f, 0.7f, 0.95f, 24f),
    ProjectileType.ARCANE_BOLT  -> energyBolt(0.65f, 0.3f, 0.95f, 22f),
    ProjectileType.FIREBALL     -> (drawFireball _),
    ProjectileType.SPLASH       -> aoeRing(0.3f, 0.6f, 1f, 50f),
    ProjectileType.TIDAL_WAVE   -> (drawTidalWave _),
    ProjectileType.GEYSER       -> (drawGeyser _),
    ProjectileType.BULLET       -> physProj(0.75f, 0.7f, 0.55f, 3.5f),
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
    ProjectileType.BONE_AXE     -> spinner(0.92f, 0.9f, 0.82f, 20f, 4),
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
    ProjectileType.SHOVEL       -> spinner(0.6f, 0.6f, 0.55f, 20f, 4),
    ProjectileType.HEAD_THROW   -> lobbed(0.75f, 0.7f, 0.6f, 20f),
    ProjectileType.BANDAGE_WHIP -> beamProj(0.85f, 0.75f, 0.6f, 6f, 6f),
    ProjectileType.CURSE        -> energyBolt(0.4f, 0.1f, 0.55f, 22f),

    // ── Medieval/Fantasy (70-79) ──
    ProjectileType.HOLY_BLADE   -> spinner(1f, 0.95f, 0.5f, 22f, 4),
    ProjectileType.HOLY_BOLT    -> energyBolt(1f, 0.95f, 0.55f, 20f),
    ProjectileType.ARROW        -> physProj(0.55f, 0.4f, 0.25f, 6f),
    ProjectileType.POISON_ARROW -> physProj(0.25f, 0.75f, 0.2f, 6f),
    ProjectileType.SONIC_WAVE   -> wave(0.75f, 0.55f, 0.95f, 32f),
    ProjectileType.SONIC_BOOM   -> aoeRing(0.75f, 0.55f, 0.95f, 55f),
    ProjectileType.FIST         -> physProj(0.75f, 0.6f, 0.45f, 3.5f),
    ProjectileType.SMITE        -> energyBolt(1f, 0.95f, 0.6f, 26f),
    ProjectileType.CHARM        -> energyBolt(1f, 0.45f, 0.65f, 18f),
    ProjectileType.CARD         -> spinner(0.92f, 0.92f, 0.88f, 16f, 4),

    // ── Sci-Fi/Tech (80-89) ──
    ProjectileType.DATA_BOLT    -> energyBolt(0.1f, 0.95f, 0.55f, 18f),
    ProjectileType.VIRUS        -> energyBolt(0.85f, 0.25f, 0.25f, 20f),
    ProjectileType.LASER        -> beamProj(1f, 0.25f, 0.2f, 8f, 5f),
    ProjectileType.GRAVITY_BALL -> energyBolt(0.35f, 0.15f, 0.55f, 24f),
    ProjectileType.GRAVITY_WELL -> aoeRing(0.35f, 0.15f, 0.55f, 50f),
    ProjectileType.TESLA_COIL   -> (drawLightning _),
    ProjectileType.NANO_BOLT    -> energyBolt(0.25f, 0.85f, 0.65f, 16f),
    ProjectileType.VOID_BOLT    -> energyBolt(0.25f, 0.05f, 0.35f, 24f),
    ProjectileType.RAILGUN      -> beamProj(0.25f, 0.55f, 1f, 10f, 5f),
    ProjectileType.CLUSTER_BOMB -> lobbed(0.55f, 0.55f, 0.45f, 20f),

    // ── Nature/Beast (90-93) ──
    ProjectileType.VENOM_BOLT   -> energyBolt(0.35f, 0.85f, 0.2f, 18f),
    ProjectileType.WEB_SHOT     -> energyBolt(0.9f, 0.9f, 0.85f, 20f),
    ProjectileType.STINGER      -> physProj(0.45f, 0.9f, 1f, 5f),
    ProjectileType.ACID_BOMB    -> lobbed(0.25f, 0.9f, 0.35f, 18f),

    // ── AoE Root (94-101) ──
    ProjectileType.SEISMIC_ROOT -> aoeRing(0.55f, 0.45f, 0.25f, 40f),
    ProjectileType.ROOT_GROWTH  -> aoeRing(0.25f, 0.6f, 0.15f, 38f),
    ProjectileType.WEB_TRAP     -> lobbed(0.9f, 0.9f, 0.85f, 20f),
    ProjectileType.TREMOR_SLAM  -> aoeRing(0.55f, 0.45f, 0.25f, 50f),
    ProjectileType.ENTANGLE     -> aoeRing(0.2f, 0.55f, 0.15f, 38f),
    ProjectileType.STONE_GAZE   -> beamProj(0.65f, 0.6f, 0.5f, 7f, 8f),
    ProjectileType.INK_SNARE    -> lobbed(0.15f, 0.15f, 0.2f, 20f),
    ProjectileType.GRAVITY_LOCK -> aoeRing(0.35f, 0.15f, 0.55f, 40f),

    // ── Character-specific (102-111) ──
    ProjectileType.KNIFE        -> spinner(0.8f, 0.82f, 0.85f, 16f, 2),
    ProjectileType.STING        -> energyBolt(0.45f, 0.9f, 1f, 16f),
    ProjectileType.HAMMER       -> spinner(0.55f, 0.55f, 0.6f, 24f, 4),
    ProjectileType.HORN         -> physProj(0.65f, 0.6f, 0.45f, 6f),
    ProjectileType.MYSTIC_BOLT  -> energyBolt(0.55f, 0.35f, 0.85f, 20f),
    ProjectileType.PETRIFY      -> beamProj(0.65f, 0.6f, 0.5f, 7f, 8f),
    ProjectileType.GRAB         -> beamProj(0.5f, 0.4f, 0.25f, 8f, 9f),
    ProjectileType.JAW          -> (drawJaw _),
    ProjectileType.TONGUE       -> beamProj(0.9f, 0.35f, 0.4f, 8f, 8f),
    ProjectileType.ACID_FLASK   -> lobbed(0.2f, 0.75f, 0.3f, 18f)
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

  /** Normal - uses projectile color, large energy beam with particles */
  private def drawNormal(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    beamTip(sx, sy, proj, 7f)
    val tipX = _tipX; val tipY = _tipY
    val phase = (tick + proj.id * 37) * 0.3
    val p = (0.9 + 0.1 * Math.sin(phase)).toFloat
    intToRGB(proj.colorRGB)
    val r = _r; val g = _g; val b = _b
    val cr = bright(r); val cg = bright(g); val cb = bright(b)

    // Soft glow
    sb.strokeLineSoft(sx, sy, tipX, tipY, 30f, r, g, b, 0.2f * p)
    // Layered beam
    sb.strokeLine(sx, sy, tipX, tipY, 14f, r, g, b, 0.35f * p)
    sb.strokeLine(sx, sy, tipX, tipY, 8f, r, g, b, 0.65f * p)
    sb.strokeLine(sx, sy, tipX, tipY, 3.5f, cr, cg, cb, 0.9f * p)

    // Particles along beam
    val dx = tipX - sx; val dy = tipY - sy
    val len = Math.sqrt(dx * dx + dy * dy).toFloat
    if (len > 1) {
      val nx = dx / len; val ny = dy / len
      var i = 0; while (i < 6) {
        val t = ((tick * 0.08 + i * 0.167 + proj.id * 0.13) % 1.0).toFloat
        val px = sx + dx * t + ny * Math.sin(phase + i * 2.1).toFloat * 12f
        val py = sy + dy * t - nx * Math.sin(phase + i * 2.1).toFloat * 12f
        val pa = (0.55 * (1.0 - Math.abs(t - 0.5) * 2.0)).toFloat
        sb.fillOval(px, py, 5f, 4f, cr, cg, cb, pa, 6)
      ; i += 1 }
    }

    // Tip orb
    val orbR = 12f * p
    sb.fillOvalSoft(tipX, tipY, orbR * 2f, orbR * 1.5f, r, g, b, 0.35f * p, 0f, 14)
    sb.fillOval(tipX, tipY, orbR, orbR * 0.7f, r, g, b, 0.65f * p, 12)
    sb.fillOval(tipX, tipY, orbR * 0.35f, orbR * 0.25f, cr, cg, cb, 0.9f, 8)

    // Expanding rings at tip
    var ring = 0; while (ring < 2) {
      val rp = ((tick * 0.1 + ring * 0.5 + proj.id * 0.17) % 1.0).toFloat
      val ringR = 6f + rp * 30f
      sb.strokeOval(tipX, tipY, ringR, ringR * 0.6f, 2f * (1f - rp), r, g, b, 0.4f * (1f - rp) * p, 12)
    ; ring += 1 }
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

  /** Fireball - large solid fire orb with fire tongues and varied ember trail */
  private def drawFireball(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 29) * 0.35
    val p = (0.9 + 0.1 * Math.sin(phase)).toFloat
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy

    // Outer heat halo
    sb.fillOvalSoft(sx, sy, 50f, 38f, 1f, 0.3f, 0f, 0.3f * p, 0f, 18)
    // Fire body
    sb.fillOval(sx, sy, 22f, 16f, 0.9f, 0.35f, 0.02f, 0.9f * p, 14)
    sb.fillOval(sx, sy, 14f, 10f, 1f, 0.6f, 0.08f, 0.9f * p, 12)
    sb.fillOval(sx, sy, 7f, 5f, 1f, 0.85f, 0.4f, 0.95f, 8)
    sb.fillOval(sx, sy, 3f, 2.5f, 1f, 1f, 0.85f, 0.9f, 6)

    // Fire tongue licks
    var i = 0; while (i < 5) {
      val fa = phase * 1.8 + i * Math.PI * 2 / 5
      val fl = 12f + Math.sin(phase * 3 + i * 2.1).toFloat * 6f
      val fx = sx + Math.cos(fa).toFloat * 10f
      val fy = sy + Math.sin(fa).toFloat * 6f - fl * 0.4f
      sb.fillOval(fx, fy, 5f, fl * 0.3f, 1f, 0.45f, 0.02f, 0.5f * p, 8)
    ; i += 1 }

    // Varied ember trail — yellow, orange, deep red embers
    { var i = 0; while (i < 8) {
      val t = ((tick * 0.07 + i * 0.125 + proj.id * 0.13) % 1.0).toFloat
      val spread = Math.sin(phase + i * 2.3).toFloat * 8f
      val fx = sx - ndx * t * 35 + spread
      val fy = sy - ndy * t * 35 - t * 10f
      val s = 4f + (1f - t) * 5f
      val colorPhase = (i * 0.37f + proj.id * 0.13f) % 1.0f
      val eR = 1f
      val eG = if (colorPhase < 0.33f) 0.85f else if (colorPhase < 0.66f) 0.45f else 0.12f
      val eB = if (colorPhase < 0.33f) 0.3f else 0f
      sb.fillOval(fx, fy, s, s * 0.7f, eR, eG * (1f - t * 0.5f), eB, 0.55f * (1f - t) * p, 8)
    ; i += 1 } }
  }

  /** Rocket - large solid body with exhaust plume */
  private def drawRocket(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    beamTip(sx, sy, proj, 5f)
    val tipX = _tipX; val tipY = _tipY
    val phase = (tick + proj.id * 29) * 0.35
    val p = (0.9 + 0.1 * Math.sin(phase)).toFloat
    val dx = tipX - sx; val dy = tipY - sy
    val len = Math.sqrt(dx * dx + dy * dy).toFloat
    if (len < 1) return
    val nx = dx / len; val ny = dy / len
    val perpX = -ny; val perpY = nx

    // Exhaust glow
    sb.fillOvalSoft(sx - dx * 0.15f, sy - dy * 0.15f, 24f, 18f, 1f, 0.5f, 0.1f, 0.3f * p, 0f, 12)

    // Exhaust flames
    var i = 0; while (i < 8) {
      val t = ((tick * 0.09 + i * 0.125 + proj.id * 0.07) % 1.0).toFloat
      val fx = sx - dx * t * 0.5f + perpX * Math.sin(phase * 3 + i * 1.5).toFloat * (3f + t * 12f) * 0.3f
      val fy = sy - dy * t * 0.5f + perpY * Math.sin(phase * 3 + i * 1.5).toFloat * (3f + t * 12f) * 0.3f
      val green = Math.max(0f, 0.9f - t * 0.5f)
      sb.fillOval(fx, fy, 3f + t * 4f, 3f + t * 3f, 1f, green, Math.max(0f, 0.3f - t * 0.2f), 0.6f * (1f - t), 6)
    ; i += 1 }

    // Smoke
    { var i = 0; while (i < 4) {
      val t = ((tick * 0.04 + i * 0.25 + proj.id * 0.09) % 1.0).toFloat
      val smX = sx - dx * t * 0.6f + perpX * Math.sin(phase * 0.8 + i * 1.3).toFloat * (5f + t * 8f)
      val smY = sy - dy * t * 0.6f + perpY * Math.sin(phase * 0.8 + i * 1.3).toFloat * (5f + t * 8f) - t * 8f
      val gray = 0.5f + t * 0.1f
      sb.fillOval(smX, smY, 4f + t * 7f, 3.5f + t * 6f, gray, gray, gray, 0.3f * (1f - t), 8)
    ; i += 1 } }

    // Rocket body (solid)
    sb.strokeLine(sx, sy, tipX, tipY, 12f, 0.35f, 0.38f, 0.25f, 0.9f * p)
    sb.strokeLine(sx, sy, tipX, tipY, 5f, 0.5f, 0.55f, 0.4f, 0.6f * p)
    // Red band
    val bandX = sx + dx * 0.45f; val bandY = sy + dy * 0.45f
    sb.strokeLine(bandX - dx * 0.04f, bandY - dy * 0.04f, bandX + dx * 0.04f, bandY + dy * 0.04f,
      13f, 0.9f, 0.2f, 0.1f, 0.8f * p)
    // Fins
    var f = -1; while (f <= 1) {
      sb.fillPolygon(Array(sx, (sx - nx * 5f + perpX * f * 8f).toFloat, (sx - nx * 14f).toFloat),
        Array(sy, (sy - ny * 5f + perpY * f * 8f).toFloat, (sy - ny * 14f).toFloat), 0.4f, 0.45f, 0.3f, 0.75f * p)
    ; f += 2 }
    // Nosecone
    val noseX = tipX + nx * 16f; val noseY = tipY + ny * 16f
    sb.fillPolygon(Array(noseX, tipX + perpX * 7f, tipX - perpX * 7f),
      Array(noseY, tipY + perpY * 7f, tipY - perpY * 7f), 0.65f, 0.65f, 0.6f, 0.9f * p)
  }

  /** Lightning - thick jagged bolt with flickering regen and branches */
  private def drawLightning(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    beamTip(sx, sy, proj, 6f)
    val tipX = _tipX; val tipY = _tipY
    val phase = (tick + proj.id * 41) * 0.5
    val flicker = (0.8 + 0.2 * Math.sin(phase * 8)).toFloat
    val dx = tipX - sx; val dy = tipY - sy
    val len = Math.sqrt(dx * dx + dy * dy).toFloat
    if (len < 1) return
    val nx = -dy / len; val ny = dx / len

    // Flickering regen — bolt shape changes every 3 ticks
    val regenSeed = (tick / 3) * 7 + proj.id * 41
    val segs = 6
    _boltXs(0) = sx; _boltYs(0) = sy; _boltXs(segs) = tipX; _boltYs(segs) = tipY
    var i = 1; while (i < segs) {
      val t = i.toFloat / segs
      val jitter = Math.sin(regenSeed * 0.9 + i * 2.7).toFloat * 12f +
        Math.cos(regenSeed * 1.3 + i * 3.9).toFloat * 5f
      _boltXs(i) = sx + dx * t + nx * jitter
      _boltYs(i) = sy + dy * t + ny * jitter
    ; i += 1 }

    // Main bolt — bloom handles glow
    { var i = 0; while (i < segs) { sb.strokeLine(_boltXs(i), _boltYs(i), _boltXs(i + 1), _boltYs(i + 1), 6f, 0.5f, 0.4f, 1f, 0.85f * flicker); i += 1 } }
    // White-hot core
    { var i = 0; while (i < segs) { sb.strokeLine(_boltXs(i), _boltYs(i), _boltXs(i + 1), _boltYs(i + 1), 2.5f, 0.9f, 0.88f, 1f, 0.95f * flicker); i += 1 } }

    // Branches
    var b = 0; while (b < 2) {
      val bSeg = 2 + b * 2
      if (bSeg < segs) {
        val bAngle = Math.PI * 0.35 * (if (b % 2 == 0) 1 else -1) +
          Math.sin(regenSeed * 0.7 + b * 2.3).toFloat * 0.4
        val bLen = 20f + Math.sin(regenSeed * 0.5 + b * 2.1).toFloat * 8f
        val bex = _boltXs(bSeg) + Math.cos(bAngle).toFloat * bLen
        val bey = _boltYs(bSeg) + Math.sin(bAngle).toFloat * bLen * 0.5f
        sb.strokeLine(_boltXs(bSeg), _boltYs(bSeg), bex, bey, 3.5f, 0.5f, 0.4f, 1f, 0.5f * flicker)
        sb.strokeLine(_boltXs(bSeg), _boltYs(bSeg), bex, bey, 1.5f, 0.85f, 0.8f, 1f, 0.7f * flicker)
      }
    ; b += 1 }

    // Tip flash
    sb.fillOval(tipX, tipY, 7f, 5f, 0.85f, 0.8f, 1f, 0.6f * flicker, 8)
  }

  /** Thunder Strike - vertical bolt with ground impact */
  private def drawThunderStrike(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 31) * 0.5
    val flicker = (0.8 + 0.2 * Math.sin(phase * 8)).toFloat

    // Ground flash
    sb.fillOvalSoft(sx, sy, 70f, 50f, 1f, 0.95f, 0.4f, 0.2f * flicker, 0f, 18)

    // Zigzag bolt from above
    val pts = 9
    _thunderXs(0) = sx - 5; _thunderYs(0) = sy - 55
    _thunderXs(1) = sx + 12; _thunderYs(1) = sy - 35
    _thunderXs(2) = sx + 1; _thunderYs(2) = sy - 28
    _thunderXs(3) = sx + 14; _thunderYs(3) = sy - 8
    _thunderXs(4) = sx + 5; _thunderYs(4) = sy - 4
    _thunderXs(5) = sx + 16; _thunderYs(5) = sy + 18
    _thunderXs(6) = sx + 7; _thunderYs(6) = sy + 15
    _thunderXs(7) = sx + 14; _thunderYs(7) = sy + 32
    _thunderXs(8) = sx + 4; _thunderYs(8) = sy + 32

    // Soft glow
    { var i = 0; while (i < pts - 1) { sb.strokeLineSoft(_thunderXs(i), _thunderYs(i), _thunderXs(i + 1), _thunderYs(i + 1), 20f, 1f, 0.85f, 0.2f, 0.2f * flicker); i += 1 } }
    // Main bolt
    { var i = 0; while (i < pts - 1) { sb.strokeLine(_thunderXs(i), _thunderYs(i), _thunderXs(i + 1), _thunderYs(i + 1), 8f, 1f, 0.9f, 0.35f, 0.7f * flicker); i += 1 } }
    // Core
    { var i = 0; while (i < pts - 1) { sb.strokeLine(_thunderXs(i), _thunderYs(i), _thunderXs(i + 1), _thunderYs(i + 1), 3f, 1f, 1f, 0.85f, 0.95f * flicker); i += 1 } }

    // Ground impact
    sb.fillOval(sx, sy + 32f, 18f, 6f, 1f, 0.9f, 0.3f, 0.5f * flicker, 14)
    var i2 = 0; while (i2 < 8) {
      val angle = phase * 2 + i2 * Math.PI / 4
      val dist = 12f + Math.sin(phase * 3 + i2).toFloat * 7f
      sb.fillOval((sx + Math.cos(angle).toFloat * dist), (sy + 30 + Math.sin(angle).toFloat * dist * 0.3f).toFloat,
        3f, 2.5f, 1f, 1f, 0.6f, 0.6f * flicker, 6)
    ; i2 += 1 }
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

  /** Bat Swarm - dark cloud with visible bats */
  private def drawBatSwarm(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 31) * 0.4
    val p = (0.85 + 0.15 * Math.sin(phase)).toFloat

    // Dark cloud
    sb.fillOvalSoft(sx, sy, 30f, 22f, 0.1f, 0f, 0.15f, 0.35f * p, 0f, 14)

    var bat = 0; while (bat < 7) {
      val bAngle = phase * 1.5 + bat * Math.PI * 2 / 7
      val bDist = 8f + Math.sin(phase * 2 + bat * 1.7).toFloat * 6f
      val bx = sx + Math.cos(bAngle).toFloat * bDist
      val by = sy + Math.sin(bAngle).toFloat * bDist * 0.5f
      val wingFlap = Math.sin(phase * 8 + bat * 2.3).toFloat * 6f

      sb.fillPolygon(Array(bx, bx - 6f, bx - 3f), Array(by, by - wingFlap, by + 2), 0.08f, 0f, 0.12f, 0.8f * p)
      sb.fillPolygon(Array(bx, bx + 6f, bx + 3f), Array(by, by - wingFlap, by + 2), 0.08f, 0f, 0.12f, 0.8f * p)
      // Wing membrane highlight
      sb.strokeLine(bx, by, bx - 5f, by - wingFlap * 0.7f, 0.8f, 0.2f, 0.05f, 0.25f, 0.4f * p)
      sb.strokeLine(bx, by, bx + 5f, by - wingFlap * 0.7f, 0.8f, 0.2f, 0.05f, 0.25f, 0.4f * p)
      sb.fillOval(bx, by, 2f, 2.5f, 0.06f, 0f, 0.1f, 0.85f * p, 6)
      sb.fillOval(bx - 1f, by - 1f, 1.2f, 1f, 0.9f, 0.1f, 0.1f, 0.6f * p, 4)
      sb.fillOval(bx + 1f, by - 1f, 1.2f, 1f, 0.9f, 0.1f, 0.1f, 0.6f * p, 4)
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

  /** Shadow Bolt - dark void mass with curling tendrils, layered core, and enhanced eyes */
  private def drawShadowBolt(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 43) * 0.35
    val p = (0.85 + 0.15 * Math.sin(phase)).toFloat
    val w1 = Math.sin(phase * 2.3).toFloat * 4
    val w2 = Math.cos(phase * 1.7).toFloat * 3

    // Void aura
    sb.fillOvalSoft(sx + w1, sy + w2, 32f, 24f, 0.12f, 0f, 0.18f, 0.3f * p, 0f, 14)

    // Shadow tendrils with curling tips
    var i = 0; while (i < 6) {
      val angle = phase * 1.2 + i * Math.PI * 2 / 6
      val tLen = 24f + Math.sin(phase * 2.5 + i * 1.9).toFloat * 7
      val ex = sx + Math.cos(angle).toFloat * tLen
      val ey = sy + Math.sin(angle).toFloat * tLen * 0.5f
      sb.strokeLine(sx, sy, ex, ey, 4f, 0.2f, 0.02f, 0.35f, 0.45f * p)
      sb.strokeLine(sx, sy, ex, ey, 2f, 0.08f, 0f, 0.15f, 0.7f * p)
      // Curling extension at tip
      val curlAngle = angle + Math.sin(phase * 3 + i * 2.1) * 0.8
      val curlX = ex + Math.cos(curlAngle).toFloat * 6f
      val curlY = ey + Math.sin(curlAngle).toFloat * 3f
      sb.strokeLine(ex, ey, curlX, curlY, 2f, 0.15f, 0.01f, 0.28f, 0.3f * p)
    ; i += 1 }

    // Dark mass
    sb.fillOval(sx, sy, 16f, 12f, 0.06f, 0f, 0.1f, 0.9f, 14)
    // Black center
    sb.fillOval(sx, sy, 5f, 4f, 0f, 0f, 0f, 0.95f, 8)

    // Purple eyes with offset pulse
    val eyePulse1 = (0.5 + 0.5 * Math.sin(phase * 4)).toFloat
    val eyePulse2 = (0.5 + 0.5 * Math.sin(phase * 4 + 1.2)).toFloat
    sb.fillOval(sx - 5, sy - 2.5f, 3f, 2.5f, 0.7f, 0.2f, 1f, 0.7f * eyePulse1 * p, 8)
    sb.fillOval(sx + 6, sy - 2.5f, 3f, 2.5f, 0.7f, 0.2f, 1f, 0.7f * eyePulse2 * p, 8)
  }

  /** Inferno Blast - massive fire vortex */
  private def drawInfernoBlast(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 23) * 0.4
    val p = (0.85 + 0.15 * Math.sin(phase)).toFloat
    val r = 34f

    // Heat halo
    sb.fillOvalSoft(sx, sy, r * 2.5f, r * 2f, 1f, 0.2f, 0f, 0.2f * p, 0f, 18)

    // Spinning fire spirals
    var arm = 0; while (arm < 4) {
      val segs2 = 10
      var i = 0; while (i < segs2) {
        val t = i.toFloat / segs2
        val spiralAngle = phase * 2.5 + t * Math.PI * 2 + arm * Math.PI / 2
        val spiralR = r * t
        val px = sx + Math.cos(spiralAngle).toFloat * spiralR
        val py = sy + Math.sin(spiralAngle).toFloat * spiralR * 0.5f
        val s = 6f + t * 7f
        sb.fillOval(px, py, s, s * 0.65f, 1f, Math.max(0f, 0.5f * (1f - t)), 0f, 0.55f * (1f - t * 0.4f) * p, 8)
      ; i += 1 }
    ; arm += 1 }

    // Fire core
    sb.fillOval(sx, sy, r * 0.45f, r * 0.35f, 0.95f, 0.45f, 0.02f, 0.85f * p, 14)
    sb.fillOval(sx, sy, r * 0.25f, r * 0.2f, 1f, 0.7f, 0.1f, 0.9f * p, 12)
    sb.fillOval(sx, sy, 6f, 5f, 1f, 1f, 0.6f, 0.9f, 8)

    // Embers
    var i = 0; while (i < 8) {
      val angle = phase * 1.5 + i * Math.PI / 4
      val fl = r + Math.sin(phase * 3 + i * 2).toFloat * 12
      sb.fillOval(sx + Math.cos(angle).toFloat * fl, sy + Math.sin(angle).toFloat * fl * 0.5f - Math.abs(Math.sin(phase * 4 + i)).toFloat * 7,
        4f, 3f, 1f, 0.5f, 0f, 0.5f * p, 6)
    ; i += 1 }
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
