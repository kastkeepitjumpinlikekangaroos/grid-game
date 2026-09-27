package com.gridgame.client.render.projectiles

import com.gridgame.common.model.Projectile
import com.gridgame.client.gl.ShapeBatch
import GLProjectileRenderers.Renderer
import ProjectileKit._
import Silhouettes._

/** What is fired straight and fast, drawn head-on-the-hitbox with its streak behind: blaster bolts
  * (laserBolt), the rail slug, the frost comet, Medusa's gaze (gorgonEye) and the drain vortices
  * (siphonVortex). */
private[render] object Beams {
  // ── Blaster bolts (lasers, eye beam) ──
  final val BOLT_LASER = 0
  final val BOLT_PRISM = 1
  final val BOLT_EYE = 2

  /**
   * Blaster bolt: a short capsule, round at the front and drawn to a point at the back,
   * whose front IS the hitbox, with an afterglow dissolving behind it — the shape every
   * sci-fi laser reads as. `kind` adds prismatic fringes (Photon) or rings of force
   * pulsing off the head (Cyclops).
   */
  private[projectiles] def laserBolt(kind: Int, r: Float, g: Float, b: Float, len: Float = 40f,
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
  private[projectiles] def railSlug(r: Float, g: Float, b: Float, size: Float = 12f): Renderer =
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
  private[projectiles] def drawFrostComet(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
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
  private[projectiles] def gorgonEye(petrify: Boolean): Renderer =
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
  final val SIPH_BLOOD = 0
  final val SIPH_LIFE = 1
  final val SIPH_SOUL = 2

  /** Drain abilities as a travelling whirlpool: motes spiral INTO a dark core, which is what a
   *  siphon is. The old drain beam — a tube with beads streaming along it — was the purest
   *  case of a shiny snake in the roster. Blood sheds drips, life beats a heart in the dark,
   *  a soul drain stares back. */
  private[projectiles] def siphonVortex(kind: Int, r: Float, g: Float, b: Float, size: Float = 15f): Renderer =
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
}
