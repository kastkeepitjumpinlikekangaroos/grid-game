package com.gridgame.client.gl

import com.gridgame.common.model.{Projectile, ProjectileType}

/**
 * Projectile renderers for OpenGL ShapeBatch. All 112 projectile types mapped.
 * Rendered with additive blending for glow effects (set by GLGameRenderer).
 */
object GLProjectileRenderers {

  type Renderer = (Projectile, Float, Float, ShapeBatch, Int) => Unit

  def get(pType: Byte): Option[Renderer] = registry.get(pType)

  def drawGeneric(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int,
                  r: Float, g: Float, b: Float): Unit = {
    val phase = (tick + proj.id * 37) * 0.35
    val p = (0.85 + 0.15 * Math.sin(phase)).toFloat
    val cr = Math.min(1f, r * 0.3f + 0.7f)
    val cg = Math.min(1f, g * 0.3f + 0.7f)
    val cb = Math.min(1f, b * 0.3f + 0.7f)
    sb.fillOvalSoft(sx, sy, 28f, 20f, r, g, b, 0.2f * p, 0f, 18)
    sb.fillOvalSoft(sx, sy, 18f, 12f, r, g, b, 0.35f * p, 0.05f, 14)
    sb.fillOval(sx, sy, 12f, 8f, r, g, b, 0.7f * p, 12)
    sb.fillOval(sx, sy, 5f, 3.5f, cr, cg, cb, 0.9f, 8)
    val (ndx, ndy, _) = screenDir(proj)
    for (i <- 0 until 5) {
      val t = ((tick * 0.07 + i * 0.2 + proj.id * 0.13) % 1.0).toFloat
      sb.fillOval(sx - ndx * t * 24, sy - ndy * t * 24, 5f * (1f - t), 3.5f * (1f - t), r, g, b, 0.3f * (1f - t) * p, 8)
    }
  }

  // ═══════════════════════════════════════════════════════════════
  //  HELPERS
  // ═══════════════════════════════════════════════════════════════

  private def intToRGB(argb: Int): (Float, Float, Float) = {
    val r = ((argb >> 16) & 0xFF) / 255f
    val g = ((argb >> 8) & 0xFF) / 255f
    val b = (argb & 0xFF) / 255f
    (r, g, b)
  }

  private def screenDir(proj: Projectile): (Float, Float, Float) = {
    val sdx = ((proj.dx - proj.dy) * 20).toFloat
    val sdy = ((proj.dx + proj.dy) * 10).toFloat
    val len = Math.sqrt(sdx * sdx + sdy * sdy).toFloat
    if (len < 0.01f) (1f, 0f, 0f) else (sdx / len, sdy / len, len)
  }

  private def beamTip(sx: Float, sy: Float, proj: Projectile, worldLen: Float): (Float, Float) = {
    val (ndx, ndy, _) = screenDir(proj)
    val screenLen = worldLen * 20f
    (sx + ndx * screenLen, sy + ndy * screenLen)
  }

  @inline private def bright(c: Float, amt: Float = 0.3f): Float = Math.min(1f, c * (1f - amt) + amt)

  // ═══════════════════════════════════════════════════════════════
  //  PATTERN FACTORIES
  // ═══════════════════════════════════════════════════════════════

  /** Round glowing energy projectile with trail and orbiting sparkles */
  private def energyBolt(r: Float, g: Float, b: Float, size: Float = 14f): Renderer =
    (proj, sx, sy, sb, tick) => {
      val phase = (tick + proj.id * 37) * 0.35
      val p = (0.85 + 0.15 * Math.sin(phase)).toFloat
      val cr = bright(r); val cg = bright(g); val cb = bright(b)

      // Layered glow halos
      sb.fillOvalSoft(sx, sy, size * 3f, size * 2f, r, g, b, 0.18f * p, 0f, 18)
      sb.fillOvalSoft(sx, sy, size * 1.8f, size * 1.2f, r, g, b, 0.35f * p, 0.04f, 14)
      // Solid core
      sb.fillOval(sx, sy, size, size * 0.7f, r, g, b, 0.8f * p, 12)
      // Hot white center
      sb.fillOval(sx, sy, size * 0.4f, size * 0.28f, cr, cg, cb, 0.95f, 8)

      // Orbiting sparkles
      for (s <- 0 until 3) {
        val sa = phase * 2.5 + s * Math.PI * 2 / 3
        val sd = size * 0.85f + Math.sin(phase * 3 + s * 1.7).toFloat * size * 0.15f
        val spx = sx + Math.cos(sa).toFloat * sd
        val spy = sy + Math.sin(sa).toFloat * sd * 0.6f
        sb.fillOval(spx, spy, 2.5f, 2f, cr, cg, cb, (0.4 + 0.3 * Math.sin(phase * 4 + s * 2.1)).toFloat * p, 6)
      }

      // Pulsing ring
      val rp = ((tick * 0.12 + proj.id * 0.17) % 1.0).toFloat
      val ringR = size * (0.9f + rp * 0.7f)
      sb.strokeOval(sx, sy, ringR, ringR * 0.6f, 1.5f * (1f - rp), r, g, b, Math.max(0f, 0.25f * (1f - rp) * p), 12)

      // Trail particles
      val (ndx, ndy, _) = screenDir(proj)
      for (i <- 0 until 5) {
        val t = ((tick * 0.07 + i * 0.2 + proj.id * 0.13) % 1.0).toFloat
        val spread = Math.sin(phase * 2 + i * 2.3).toFloat * 3f
        val a = Math.max(0f, 0.35f * (1f - t) * p)
        val s = size * 0.35f * (1f - t * 0.4f)
        sb.fillOval(sx - ndx * t * size * 2f + spread, sy - ndy * t * size * 2f, s, s * 0.7f, r, g, b, a, 8)
      }
    }

  /** Line projectile with layered glow and energy nodes */
  private def beamProj(r: Float, g: Float, b: Float, worldLen: Float = 5f, width: Float = 6f): Renderer =
    (proj, sx, sy, sb, tick) => {
      val (tipX, tipY) = beamTip(sx, sy, proj, worldLen)
      val phase = (tick + proj.id * 29) * 0.35
      val p = (0.85 + 0.15 * Math.sin(phase)).toFloat
      val cr = bright(r); val cg = bright(g); val cb = bright(b)

      // Soft outer glow
      sb.strokeLineSoft(sx, sy, tipX, tipY, width * 5f, r, g, b, 0.1f * p)
      // Mid glow
      sb.strokeLineSoft(sx, sy, tipX, tipY, width * 2.8f, r, g, b, 0.18f * p)
      // Main beam
      sb.strokeLine(sx, sy, tipX, tipY, width, r, g, b, 0.7f * p)
      // Hot core
      sb.strokeLine(sx, sy, tipX, tipY, width * 0.3f, cr, cg, cb, 0.9f * p)

      // Energy nodes along beam
      val dx = tipX - sx; val dy = tipY - sy
      for (i <- 0 until 4) {
        val t = ((tick * 0.1 + i * 0.25 + proj.id * 0.11) % 1.0).toFloat
        val nx = sx + dx * t; val ny = sy + dy * t
        val ns = width * 0.5f * (0.5f + 0.5f * Math.sin(phase * 3 + i * 2.1).toFloat)
        sb.fillOval(nx, ny, ns, ns * 0.7f, cr, cg, cb, 0.4f * p, 8)
      }

      // Tip flare
      sb.fillOvalSoft(tipX, tipY, width * 2.8f, width * 2f, r, g, b, 0.2f * p, 0f, 12)
      sb.fillOval(tipX, tipY, width, width * 0.7f, cr, cg, cb, 0.6f * p, 8)

      // Tip sparks
      for (s <- 0 until 3) {
        val sa = phase * 2 + s * Math.PI * 2 / 3
        val sl = width * 1.5f + Math.sin(phase * 3 + s * 1.7).toFloat * width * 0.4f
        sb.strokeLine(tipX, tipY, tipX + Math.cos(sa).toFloat * sl, tipY + Math.sin(sa).toFloat * sl * 0.6f,
          1f, cr, cg, cb, (0.25 + 0.2 * Math.sin(phase * 4 + s * 2.3)).toFloat * p)
      }
    }

  /** Spinning polygon weapon with motion blur trail */
  private def spinner(r: Float, g: Float, b: Float, size: Float = 16f, pts: Int = 4): Renderer =
    (proj, sx, sy, sb, tick) => {
      val spin = tick * 0.3 + proj.id * 2.1
      val p = (0.85 + 0.15 * Math.sin(spin * 2)).toFloat
      val (ndx, ndy, _) = screenDir(proj)
      val n = pts * 2

      // Motion blur ghosts
      for (ghost <- 1 to 2) {
        val gAlpha = 0.1f * (1f - ghost * 0.35f) * p
        val gx = sx - ndx * ghost * 7f
        val gy = sy - ndy * ghost * 7f
        val gSpin = spin - ghost * 0.25
        val gxs = new Array[Float](n); val gys = new Array[Float](n)
        for (i <- 0 until n) {
          val angle = gSpin + i * Math.PI / pts
          val rad = if (i % 2 == 0) size * 0.85f else size * 0.3f
          gxs(i) = (gx + Math.cos(angle) * rad).toFloat
          gys(i) = (gy + Math.sin(angle) * rad * 0.6f).toFloat
        }
        sb.fillPolygon(gxs, gys, r, g, b, gAlpha)
      }

      // Outer glow
      sb.fillOvalSoft(sx, sy, size * 1.5f, size * 1f, r, g, b, 0.08f * p, 0f, 14)

      // Main shape
      val xs = new Array[Float](n); val ys = new Array[Float](n)
      for (i <- 0 until n) {
        val angle = spin + i * Math.PI / pts
        val rad = if (i % 2 == 0) size else size * 0.32f
        xs(i) = (sx + Math.cos(angle) * rad).toFloat
        ys(i) = (sy + Math.sin(angle) * rad * 0.6f).toFloat
      }
      sb.fillPolygon(xs, ys, r, g, b, 0.85f * p)
      sb.strokePolygon(xs, ys, 1.5f, bright(r), bright(g), bright(b), 0.7f * p)

      // Center gleam
      sb.fillOval(sx, sy, size * 0.2f, size * 0.13f, 1f, 1f, 1f, 0.45f * p, 6)

      // Edge glint
      val ga = spin * 1.3
      val gx = (sx + Math.cos(ga) * size * 0.85f).toFloat
      val gy = (sy + Math.sin(ga) * size * 0.51f).toFloat
      sb.fillOval(gx, gy, 2.5f, 2f, 1f, 1f, 1f, 0.5f * p, 6)
    }

  /** Physical elongated projectile (spear, arrow, bullet) */
  private def physProj(r: Float, g: Float, b: Float, worldLen: Float = 4.5f): Renderer =
    (proj, sx, sy, sb, tick) => {
      val (tipX, tipY) = beamTip(sx, sy, proj, worldLen)
      val phase = (tick + proj.id * 31) * 0.4
      val p = (0.85 + 0.15 * Math.sin(phase)).toFloat
      val (ndx, ndy, _) = screenDir(proj)
      val perpX = -ndy; val perpY = ndx

      // Shaft glow
      sb.strokeLineSoft(sx, sy, tipX, tipY, 12f, r, g, b, 0.05f * p)
      // Main shaft
      sb.strokeLine(sx, sy, tipX, tipY, 4f, r * 0.65f, g * 0.65f, b * 0.65f, 0.9f * p)
      // Bright center line
      sb.strokeLine(sx, sy, tipX, tipY, 1.5f, bright(r, 0.2f), bright(g, 0.2f), bright(b, 0.2f), 0.55f * p)

      // Arrowhead
      val headLen = 10f * p; val headW = 5f * p
      val pointX = tipX + ndx * headLen; val pointY = tipY + ndy * headLen
      sb.fillPolygon(Array(pointX, tipX + perpX * headW, tipX - perpX * headW),
        Array(pointY, tipY + perpY * headW, tipY - perpY * headW),
        bright(r, 0.25f), bright(g, 0.25f), bright(b, 0.25f), 0.85f * p)
      // Tip gleam
      sb.fillOvalSoft(pointX, pointY, 5f, 3.5f, 1f, 1f, 1f, 0.12f * p, 0f, 8)

      // Fletching
      for (f <- -1 to 1 by 2) {
        val tx = sx - ndx * 5f + perpX * f * 3.5f
        val ty = sy - ndy * 5f + perpY * f * 3.5f
        sb.strokeLine(sx, sy, tx, ty, 1.2f, r * 0.5f, g * 0.5f, b * 0.5f, 0.45f * p)
      }

      // Trail
      for (i <- 0 until 3) {
        val t = ((tick * 0.07 + i * 0.33 + proj.id * 0.11) % 1.0).toFloat
        sb.fillOval(sx - ndx * t * 18 + Math.sin(phase + i * 2.3).toFloat * 2 * perpX,
          sy - ndy * t * 18 + Math.sin(phase + i * 2.3).toFloat * 2 * perpY,
          2.5f * (1f - t), 2f * (1f - t), r, g, b, Math.max(0f, 0.25f * (1f - t) * p), 6)
      }
    }

  /** Bouncing/lobbed projectile with danger aura */
  private def lobbed(r: Float, g: Float, b: Float, size: Float = 12f): Renderer =
    (proj, sx, sy, sb, tick) => {
      val phase = (tick + proj.id * 13) * 0.3
      val bounce = (Math.abs(Math.sin(phase * 1.5)) * 5f).toFloat
      val spin = tick * 0.2f + proj.id * 1.3f
      val p = (0.85 + 0.15 * Math.sin(phase)).toFloat

      // Shadow
      sb.fillOval(sx, sy + size * 0.35f, size * 0.9f, size * 0.22f, 0f, 0f, 0f, 0.1f, 12)

      // Danger aura ring
      val ap = ((tick * 0.12 + proj.id * 0.17) % 1.0).toFloat
      val aR = size * (1f + ap * 0.5f)
      sb.strokeOval(sx, sy - bounce, aR, aR * 0.55f, 2f * (1f - ap), r, g, b, Math.max(0f, 0.18f * (1f - ap) * p), 12)

      // Outer glow
      sb.fillOvalSoft(sx, sy - bounce, size * 2f, size * 1.5f, r, g, b, 0.1f * p, 0f, 14)
      // Main body
      sb.fillOval(sx, sy - bounce, size, size * 0.8f, r, g, b, 0.85f, 14)
      // Highlight
      sb.fillOval(sx - size * 0.15f, sy - bounce - size * 0.2f, size * 0.35f, size * 0.25f,
        bright(r, 0.35f), bright(g, 0.35f), bright(b, 0.35f), 0.4f, 8)

      // Surface markings
      for (v <- 0 until 3) {
        val a = spin + v * Math.PI * 2 / 3
        val vx = (sx + Math.cos(a) * size * 0.55f).toFloat
        val vy = (sy - bounce + Math.sin(a) * size * 0.4f).toFloat
        sb.strokeLine(sx, sy - bounce, vx, vy, 1f, bright(r, 0.15f), bright(g, 0.15f), bright(b, 0.15f), 0.3f * p)
      }

      // Fuse sparks
      for (s <- 0 until 3) {
        val sa = phase * 3 + s * 2.1
        val sd = 3f + Math.sin(phase * 4 + s * 1.5).toFloat * 2f
        sb.fillOval(sx + Math.cos(sa).toFloat * sd, sy - bounce - size * 0.45f + Math.sin(sa).toFloat * sd * 0.4f - 2f,
          2f, 1.5f, 1f, 0.9f, 0.4f, (0.35 + 0.3 * Math.sin(phase * 5 + s * 2)).toFloat * p, 6)
      }
    }

  /** Expanding ring AoE effect with radial sparks */
  private def aoeRing(r: Float, g: Float, b: Float, maxR: Float = 35f): Renderer =
    (proj, sx, sy, sb, tick) => {
      val phase = (tick + proj.id * 23) * 0.4
      val p = (0.8 + 0.2 * Math.sin(phase * 2)).toFloat
      val cr = bright(r); val cg = bright(g); val cb = bright(b)

      // Ground shimmer
      sb.fillOvalSoft(sx, sy, maxR * 0.7f, maxR * 0.3f, r, g, b, 0.07f * p, 0f, 18)

      // 4 expanding rings
      for (ring <- 0 until 4) {
        val rp = ((phase * 0.4 + ring * 0.25) % 1.0).toFloat
        val ringR = 5f + rp * maxR
        val a = Math.max(0f, 0.35f * (1f - rp) * p)
        sb.strokeOval(sx, sy, ringR, ringR * 0.45f, 3f * (1f - rp * 0.4f), r, g, b, a, 18)
      }

      // Radial spark lines
      for (spark <- 0 until 6) {
        val sa = phase * 0.8 + spark * Math.PI / 3
        val sl = maxR * 0.35f * p
        sb.strokeLine(sx, sy, sx + Math.cos(sa).toFloat * sl, sy + Math.sin(sa).toFloat * sl * 0.45f,
          1.2f, r, g, b, 0.12f * p)
      }

      // Core
      sb.fillOvalSoft(sx, sy, 10f, 6f, r, g, b, 0.45f * p, 0.08f, 12)
      sb.fillOval(sx, sy, 5f, 3f, cr, cg, cb, 0.7f * p, 8)
    }

  /** Zigzag chain/lightning pattern with bright joints */
  private def chainProj(r: Float, g: Float, b: Float, worldLen: Float = 5f): Renderer =
    (proj, sx, sy, sb, tick) => {
      val (tipX, tipY) = beamTip(sx, sy, proj, worldLen)
      val phase = (tick + proj.id * 23) * 0.3
      val p = (0.85 + 0.15 * Math.sin(phase)).toFloat
      val dx = tipX - sx; val dy = tipY - sy
      val len = Math.sqrt(dx * dx + dy * dy).toFloat
      if (len >= 1) {
        val nx = -dy / len; val ny = dx / len
        val zigW = 6f * p
        val segs = 12
        val cr = bright(r, 0.25f); val cg = bright(g, 0.25f); val cb = bright(b, 0.25f)

        for (i <- 0 until segs) {
          val t0 = i.toFloat / segs; val t1 = (i + 1).toFloat / segs
          val z0 = if (i % 2 == 0) zigW else -zigW
          val z1 = if ((i + 1) % 2 == 0) zigW else -zigW
          val x0 = sx + dx * t0 + nx * z0; val y0 = sy + dy * t0 + ny * z0
          val x1 = sx + dx * t1 + nx * z1; val y1 = sy + dy * t1 + ny * z1

          sb.strokeLineSoft(x0, y0, x1, y1, 10f, r, g, b, 0.08f * p)
          sb.strokeLine(x0, y0, x1, y1, 4f, r, g, b, 0.8f * p)
          if (i % 2 == 0) sb.strokeLine(x0, y0, x1, y1, 1.5f, cr, cg, cb, 0.5f * p)
        }

        // Bright nodes at joints
        for (i <- 0 to segs by 2) {
          val t = i.toFloat / segs
          val z = if (i % 2 == 0) zigW else -zigW
          val jx = sx + dx * t + nx * z; val jy = sy + dy * t + ny * z
          sb.fillOval(jx, jy, 3f, 2.2f, cr, cg, cb, 0.45f * p, 6)
        }

        // Tip glow
        sb.fillOvalSoft(tipX, tipY, 7f, 5f, r, g, b, 0.2f * p, 0f, 10)
        sb.fillOval(tipX, tipY, 3.5f, 2.5f, cr, cg, cb, 0.55f * p, 8)
      }
    }

  /** Spreading wave/slash crescent effect */
  private def wave(r: Float, g: Float, b: Float, spread: Float = 24f): Renderer =
    (proj, sx, sy, sb, tick) => {
      val phase = (tick + proj.id * 41) * 0.4
      val p = (0.8 + 0.2 * Math.sin(phase)).toFloat
      val (ndx, ndy, _) = screenDir(proj)
      val perpX = -ndy; val perpY = ndx
      val tipX = sx + ndx * spread; val tipY = sy + ndy * spread * 0.6f
      val w1x = sx + perpX * spread; val w1y = sy + perpY * spread * 0.6f
      val w2x = sx - perpX * spread; val w2y = sy - perpY * spread * 0.6f

      // Outer glow fill
      sb.fillPolygon(Array(tipX + ndx * 4, w1x + perpX * 3, sx - ndx * 4, w2x - perpX * 3),
        Array(tipY + ndy * 3, w1y + perpY * 3, sy - ndy * 3, w2y - perpY * 3), r, g, b, 0.06f * p)
      // Main crescent
      sb.fillPolygon(Array(tipX, w1x, sx - ndx * 3, w2x), Array(tipY, w1y, sy - ndy * 2, w2y), r, g, b, 0.25f * p)
      // Bright leading edge
      sb.strokePolygon(Array(tipX, w1x, sx - ndx * 3, w2x), Array(tipY, w1y, sy - ndy * 2, w2y),
        2f, bright(r), bright(g), bright(b), 0.5f * p)

      // Internal energy lines
      for (i <- 0 until 4) {
        val off = (i - 1.5f) * spread * 0.22f
        sb.strokeLine(sx + perpX * off - ndx * 3, sy + perpY * off * 0.6f - ndy * 2,
          sx + perpX * off + ndx * spread * 0.5f, sy + perpY * off * 0.6f + ndy * spread * 0.3f,
          1f, r, g, b, 0.12f * p)
      }

      // Scatter particles
      for (i <- 0 until 5) {
        val t = ((tick * 0.08 + i * 0.2 + proj.id * 0.11) % 1.0).toFloat
        val pOff = (i.toFloat / 4 - 0.5f) * 2f
        val bx = tipX * (1f - Math.abs(pOff)) + (if (pOff < 0) w2x else w1x) * Math.abs(pOff)
        val by = tipY * (1f - Math.abs(pOff)) + (if (pOff < 0) w2y else w1y) * Math.abs(pOff)
        sb.fillOval(bx + ndx * t * 6, by + ndy * t * 4, 2.5f, 2f, r, g, b, Math.max(0f, 0.3f * (1f - t) * p), 6)
      }
    }

  // ═══════════════════════════════════════════════════════════════
  //  REGISTRY (all 112 types)
  // ═══════════════════════════════════════════════════════════════

  private val registry: Map[Byte, Renderer] = Map(
    // ── Original (0-30) ──
    ProjectileType.NORMAL       -> (drawNormal _),
    ProjectileType.TENTACLE     -> (drawTentacle _),
    ProjectileType.ICE_BEAM     -> beamProj(0.4f, 0.8f, 1f, 6f, 7f),
    ProjectileType.AXE          -> spinner(0.6f, 0.55f, 0.45f, 16f, 4),
    ProjectileType.ROPE         -> chainProj(0.6f, 0.45f, 0.25f, 5.5f),
    ProjectileType.SPEAR        -> physProj(0.5f, 0.45f, 0.35f, 6f),
    ProjectileType.SOUL_BOLT    -> energyBolt(0.3f, 0.9f, 0.4f, 14f),
    ProjectileType.HAUNT        -> energyBolt(0.5f, 0.7f, 0.9f, 16f),
    ProjectileType.ARCANE_BOLT  -> energyBolt(0.6f, 0.3f, 0.9f, 15f),
    ProjectileType.FIREBALL     -> (drawFireball _),
    ProjectileType.SPLASH       -> aoeRing(0.3f, 0.6f, 1f, 35f),
    ProjectileType.TIDAL_WAVE   -> (drawTidalWave _),
    ProjectileType.GEYSER       -> (drawGeyser _),
    ProjectileType.BULLET       -> physProj(0.7f, 0.65f, 0.5f, 3f),
    ProjectileType.GRENADE      -> lobbed(0.35f, 0.4f, 0.25f, 12f),
    ProjectileType.ROCKET       -> (drawRocket _),
    ProjectileType.TALON        -> physProj(0.55f, 0.5f, 0.4f, 4.5f),
    ProjectileType.GUST         -> wave(0.8f, 0.9f, 1f, 24f),
    ProjectileType.SHURIKEN     -> spinner(0.55f, 0.55f, 0.6f, 16f, 4),
    ProjectileType.POISON_DART  -> physProj(0.2f, 0.8f, 0.1f, 5f),
    ProjectileType.CHAIN_BOLT   -> chainProj(0.45f, 0.45f, 0.5f, 5f),
    ProjectileType.LOCKDOWN_CHAIN -> chainProj(0.35f, 0.35f, 0.4f, 5.5f),
    ProjectileType.SNARE_MINE   -> lobbed(0.3f, 0.6f, 1f, 14f),
    ProjectileType.KATANA       -> spinner(0.7f, 0.7f, 0.75f, 18f, 2),
    ProjectileType.SWORD_WAVE   -> (drawSwordWave _),
    ProjectileType.PLAGUE_BOLT  -> energyBolt(0.4f, 0.7f, 0.1f, 14f),
    ProjectileType.MIASMA       -> aoeRing(0.3f, 0.6f, 0.1f, 30f),
    ProjectileType.BLIGHT_BOMB  -> lobbed(0.3f, 0.5f, 0.1f, 12f),
    ProjectileType.BLOOD_FANG   -> physProj(0.8f, 0.1f, 0.1f, 4.5f),
    ProjectileType.BLOOD_SIPHON -> beamProj(0.8f, 0.1f, 0.1f, 5f, 5f),
    ProjectileType.BAT_SWARM    -> (drawBatSwarm _),

    // ── Elemental (31-52) ──
    ProjectileType.FLAME_BOLT   -> energyBolt(1f, 0.5f, 0.05f, 14f),
    ProjectileType.FROST_SHARD  -> physProj(0.5f, 0.8f, 1f, 4.5f),
    ProjectileType.LIGHTNING    -> (drawLightning _),
    ProjectileType.CHAIN_LIGHTNING -> (drawLightning _),
    ProjectileType.THUNDER_STRIKE -> (drawThunderStrike _),
    ProjectileType.BOULDER      -> (drawBoulder _),
    ProjectileType.SEISMIC_SLAM -> aoeRing(0.55f, 0.4f, 0.2f, 40f),
    ProjectileType.WIND_BLADE   -> wave(0.8f, 0.95f, 1f, 22f),
    ProjectileType.MAGMA_BALL   -> energyBolt(1f, 0.4f, 0f, 16f),
    ProjectileType.ERUPTION     -> aoeRing(1f, 0.4f, 0f, 35f),
    ProjectileType.FROST_TRAP   -> lobbed(0.4f, 0.7f, 1f, 12f),
    ProjectileType.SAND_SHOT    -> energyBolt(0.85f, 0.72f, 0.4f, 12f),
    ProjectileType.SAND_BLAST   -> wave(0.8f, 0.7f, 0.4f, 20f),
    ProjectileType.THORN        -> physProj(0.15f, 0.5f, 0.1f, 5.5f),
    ProjectileType.VINE_WHIP    -> beamProj(0.15f, 0.55f, 0.1f, 5f, 5f),
    ProjectileType.THORN_WALL   -> aoeRing(0.2f, 0.5f, 0.1f, 22f),
    ProjectileType.INFERNO_BLAST -> (drawInfernoBlast _),
    ProjectileType.GLACIER_SPIKE -> physProj(0.55f, 0.8f, 1f, 5.5f),
    ProjectileType.MUD_GLOB     -> energyBolt(0.35f, 0.25f, 0.12f, 16f),
    ProjectileType.MUD_BOMB     -> lobbed(0.3f, 0.22f, 0.1f, 14f),
    ProjectileType.EMBER_SHOT   -> energyBolt(1f, 0.5f, 0.1f, 12f),
    ProjectileType.AVALANCHE_CRUSH -> lobbed(0.6f, 0.75f, 0.9f, 18f),

    // ── Undead/Dark (53-69) ──
    ProjectileType.DEATH_BOLT   -> energyBolt(0.15f, 0.4f, 0f, 15f),
    ProjectileType.RAISE_DEAD   -> (drawRaiseDead _),
    ProjectileType.BONE_AXE     -> spinner(0.9f, 0.88f, 0.8f, 14f, 4),
    ProjectileType.BONE_THROW   -> spinner(0.9f, 0.87f, 0.78f, 12f, 2),
    ProjectileType.WAIL         -> (drawWail _),
    ProjectileType.SOUL_DRAIN   -> beamProj(0.3f, 0.8f, 0.2f, 5f, 6f),
    ProjectileType.CLAW_SWIPE   -> wave(0.9f, 0.15f, 0.1f, 18f),
    ProjectileType.DEVOUR       -> (drawDevour _),
    ProjectileType.SCYTHE       -> (drawScythe _),
    ProjectileType.REAP         -> (drawReap _),
    ProjectileType.SHADOW_BOLT  -> (drawShadowBolt _),
    ProjectileType.CURSED_BLADE -> spinner(0.5f, 0.1f, 0.2f, 16f, 4),
    ProjectileType.LIFE_DRAIN   -> beamProj(0.8f, 0.05f, 0.05f, 5f, 5f),
    ProjectileType.SHOVEL       -> spinner(0.55f, 0.55f, 0.5f, 14f, 4),
    ProjectileType.HEAD_THROW   -> lobbed(0.72f, 0.68f, 0.58f, 14f),
    ProjectileType.BANDAGE_WHIP -> beamProj(0.8f, 0.72f, 0.55f, 5f, 5f),
    ProjectileType.CURSE        -> energyBolt(0.35f, 0.05f, 0.5f, 16f),

    // ── Medieval/Fantasy (70-79) ──
    ProjectileType.HOLY_BLADE   -> spinner(1f, 0.95f, 0.5f, 16f, 4),
    ProjectileType.HOLY_BOLT    -> energyBolt(1f, 0.95f, 0.5f, 14f),
    ProjectileType.ARROW        -> physProj(0.5f, 0.35f, 0.2f, 5.5f),
    ProjectileType.POISON_ARROW -> physProj(0.2f, 0.7f, 0.15f, 5.5f),
    ProjectileType.SONIC_WAVE   -> wave(0.7f, 0.5f, 0.9f, 24f),
    ProjectileType.SONIC_BOOM   -> aoeRing(0.7f, 0.5f, 0.9f, 40f),
    ProjectileType.FIST         -> physProj(0.7f, 0.55f, 0.4f, 3f),
    ProjectileType.SMITE        -> energyBolt(1f, 0.95f, 0.6f, 18f),
    ProjectileType.CHARM        -> energyBolt(1f, 0.4f, 0.6f, 12f),
    ProjectileType.CARD         -> spinner(0.9f, 0.9f, 0.85f, 12f, 4),

    // ── Sci-Fi/Tech (80-89) ──
    ProjectileType.DATA_BOLT    -> energyBolt(0f, 0.9f, 0.5f, 12f),
    ProjectileType.VIRUS        -> energyBolt(0.8f, 0.2f, 0.2f, 14f),
    ProjectileType.LASER        -> beamProj(1f, 0.2f, 0.2f, 7f, 4f),
    ProjectileType.GRAVITY_BALL -> energyBolt(0.3f, 0.1f, 0.5f, 16f),
    ProjectileType.GRAVITY_WELL -> aoeRing(0.3f, 0.1f, 0.5f, 35f),
    ProjectileType.TESLA_COIL   -> (drawLightning _),
    ProjectileType.NANO_BOLT    -> energyBolt(0.2f, 0.8f, 0.6f, 10f),
    ProjectileType.VOID_BOLT    -> energyBolt(0.2f, 0f, 0.3f, 16f),
    ProjectileType.RAILGUN      -> beamProj(0.2f, 0.5f, 1f, 9f, 4f),
    ProjectileType.CLUSTER_BOMB -> lobbed(0.5f, 0.5f, 0.4f, 14f),

    // ── Nature/Beast (90-93) ──
    ProjectileType.VENOM_BOLT   -> energyBolt(0.3f, 0.8f, 0.15f, 12f),
    ProjectileType.WEB_SHOT     -> energyBolt(0.85f, 0.85f, 0.8f, 14f),
    ProjectileType.STINGER      -> physProj(0.4f, 0.85f, 1f, 4.5f),
    ProjectileType.ACID_BOMB    -> lobbed(0.2f, 0.9f, 0.3f, 12f),

    // ── AoE Root (94-101) ──
    ProjectileType.SEISMIC_ROOT -> aoeRing(0.5f, 0.4f, 0.2f, 28f),
    ProjectileType.ROOT_GROWTH  -> aoeRing(0.2f, 0.55f, 0.1f, 25f),
    ProjectileType.WEB_TRAP     -> lobbed(0.85f, 0.85f, 0.8f, 14f),
    ProjectileType.TREMOR_SLAM  -> aoeRing(0.5f, 0.4f, 0.2f, 35f),
    ProjectileType.ENTANGLE     -> aoeRing(0.15f, 0.5f, 0.1f, 25f),
    ProjectileType.STONE_GAZE   -> beamProj(0.6f, 0.55f, 0.45f, 6f, 6f),
    ProjectileType.INK_SNARE    -> lobbed(0.1f, 0.1f, 0.15f, 14f),
    ProjectileType.GRAVITY_LOCK -> aoeRing(0.3f, 0.1f, 0.5f, 28f),

    // ── Character-specific (102-111) ──
    ProjectileType.KNIFE        -> spinner(0.75f, 0.78f, 0.82f, 12f, 2),
    ProjectileType.STING        -> energyBolt(0.4f, 0.85f, 1f, 10f),
    ProjectileType.HAMMER       -> spinner(0.5f, 0.5f, 0.55f, 16f, 4),
    ProjectileType.HORN         -> physProj(0.6f, 0.55f, 0.4f, 5.5f),
    ProjectileType.MYSTIC_BOLT  -> energyBolt(0.5f, 0.3f, 0.8f, 14f),
    ProjectileType.PETRIFY      -> beamProj(0.6f, 0.55f, 0.45f, 6f, 6f),
    ProjectileType.GRAB         -> beamProj(0.4f, 0.3f, 0.18f, 7f, 7f),
    ProjectileType.JAW          -> (drawJaw _),
    ProjectileType.TONGUE       -> beamProj(0.85f, 0.3f, 0.35f, 7f, 6f),
    ProjectileType.ACID_FLASK   -> lobbed(0.15f, 0.7f, 0.25f, 12f)
  )

  // ═══════════════════════════════════════════════════════════════
  //  SPECIALIZED RENDERERS
  // ═══════════════════════════════════════════════════════════════

  /** Normal - elaborate color-based energy beam with particles */
  private def drawNormal(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val (tipX, tipY) = beamTip(sx, sy, proj, 6f)
    val phase = (tick + proj.id * 37) * 0.3
    val p = (0.85 + 0.15 * Math.sin(phase)).toFloat
    val ff = (0.9 + 0.1 * Math.sin(phase * 4.7)).toFloat
    val (r, g, b) = intToRGB(proj.colorRGB)
    val cr = bright(r); val cg = bright(g); val cb = bright(b)

    // Layered beam
    sb.strokeLineSoft(sx, sy, tipX, tipY, 34f * p, r, g, b, 0.06f * p)
    sb.strokeLineSoft(sx, sy, tipX, tipY, 20f * p, r, g, b, 0.1f * p)
    sb.strokeLine(sx, sy, tipX, tipY, 12f * ff, r, g, b, 0.22f * p)
    sb.strokeLine(sx, sy, tipX, tipY, 7f * ff, r, g, b, 0.55f * ff)
    sb.strokeLine(sx, sy, tipX, tipY, 3.5f, cr, cg, cb, 0.9f * ff)

    // Energy particles along beam
    val dx = tipX - sx; val dy = tipY - sy
    val len = Math.sqrt(dx * dx + dy * dy).toFloat
    if (len > 1) {
      val nx = dx / len; val ny = dy / len
      for (i <- 0 until 8) {
        val t = ((tick * 0.08 + i * 0.125 + proj.id * 0.13) % 1.0).toFloat
        val px = sx + dx * t + ny * Math.sin(phase + i * 2.1).toFloat * 10f
        val py = sy + dy * t - nx * Math.sin(phase + i * 2.1).toFloat * 10f
        val pa = Math.max(0f, (0.55 * (1.0 - Math.abs(t - 0.5) * 2.0)).toFloat)
        sb.fillOval(px, py, 4.5f + Math.sin(phase + i).toFloat * 2f, 3.5f, cr, cg, cb, pa, 8)
      }
    }

    // Tip orb with rings
    val orbR = 10f * p
    sb.fillOvalSoft(tipX, tipY, orbR * 2.2f, orbR * 1.6f, r, g, b, 0.15f * p, 0f, 16)
    sb.fillOval(tipX, tipY, orbR, orbR * 0.7f, r, g, b, 0.4f * p, 12)
    sb.fillOval(tipX, tipY, orbR * 0.4f, orbR * 0.3f, cr, cg, cb, 0.85f * ff, 8)

    for (ring <- 0 until 3) {
      val rp = ((tick * 0.12 + ring * 0.33 + proj.id * 0.17) % 1.0).toFloat
      val ringR = 6f + rp * 28f
      sb.strokeOval(tipX, tipY, ringR, ringR * 0.65f, 2f * (1f - rp * 0.5f), r, g, b, Math.max(0f, 0.25f * (1f - rp)), 12)
    }

    for (spark <- 0 until 6) {
      val sa = phase * 1.5 + spark * Math.PI / 3
      val sl = 8f + Math.sin(phase * 3 + spark * 2.3).toFloat * 5f
      sb.strokeLine(tipX, tipY, (tipX + Math.cos(sa).toFloat * sl), (tipY + Math.sin(sa).toFloat * sl * 0.6f),
        1.2f, cr, cg, cb, (0.25 + 0.2 * Math.sin(phase * 4 + spark * 1.7)).toFloat)
    }
  }

  /** Tentacle - wavy green tendrils with suckers and dripping slime */
  private def drawTentacle(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val (tipX, tipY) = beamTip(sx, sy, proj, 4f)
    val phase = (tick + proj.id * 23) * 0.4
    val p = (0.85 + 0.15 * Math.sin(phase)).toFloat
    val dx = tipX - sx; val dy = tipY - sy
    val len = Math.sqrt(dx * dx + dy * dy).toFloat
    if (len < 1) return
    val nx = -dy / len; val ny = dx / len

    // Slime glow
    sb.fillOvalSoft(sx + dx * 0.5f, sy + dy * 0.5f, len * 0.6f, len * 0.25f, 0.2f, 0.6f, 0.15f, 0.06f * p, 0f, 14)

    for (strand <- 0 until 4) {
      val off = (strand - 1.5f) * 3.5f
      val segs = 14
      for (i <- 0 until segs) {
        val t0 = i.toFloat / segs; val t1 = (i + 1).toFloat / segs
        val w0 = (Math.sin(phase * 2 + t0 * Math.PI * 3.5 + strand * 1.6) * (6f + strand * 2) * (1f - t0 * 0.3f)).toFloat
        val w1 = (Math.sin(phase * 2 + t1 * Math.PI * 3.5 + strand * 1.6) * (6f + strand * 2) * (1f - t1 * 0.3f)).toFloat
        val x0 = sx + dx * t0 + nx * (off * 0.3f + w0); val y0 = sy + dy * t0 + ny * (off * 0.3f + w0)
        val x1 = sx + dx * t1 + nx * (off * 0.3f + w1); val y1 = sy + dy * t1 + ny * (off * 0.3f + w1)
        sb.strokeLine(x0, y0, x1, y1, 9f * p, 0.4f, 0.1f, 0.6f, 0.08f * p)
        sb.strokeLine(x0, y0, x1, y1, 5f * p, 0.15f, 0.75f, 0.25f, 0.55f * p)
        sb.strokeLine(x0, y0, x1, y1, 2f, 0.4f, 1f, 0.5f, 0.65f * p)
      }
      // Suckers
      for (s <- 0 until 3) {
        val t = 0.2f + s * 0.25f
        val w = (Math.sin(phase * 2 + t * Math.PI * 3.5 + strand * 1.6) * (6f + strand * 2) * (1f - t * 0.3f)).toFloat
        val spx = sx + dx * t + nx * (off * 0.3f + w)
        val spy = sy + dy * t + ny * (off * 0.3f + w)
        val sp = (0.6 + 0.4 * Math.sin(phase * 3 + strand * 2 + s * 1.7)).toFloat
        sb.fillOval(spx, spy, 3f * sp, 2.5f * sp, 0.6f, 0.2f, 0.8f, 0.45f * sp, 8)
      }
    }
    // Dripping particles
    for (i <- 0 until 5) {
      val t = ((tick * 0.04 + i * 0.2 + proj.id * 0.11) % 1.0).toFloat
      val dripX = sx + dx * (0.15f + i * 0.15f) + nx * Math.sin(phase + i * 2.3).toFloat * 6f
      val dripY = sy + dy * (0.15f + i * 0.15f) + ny * Math.sin(phase + i * 2.3).toFloat * 6f + t * 12f
      sb.fillOval(dripX, dripY, 2.5f, 2f, 0.3f, 0.9f, 0.4f, Math.max(0f, 0.3f * (1f - t)), 6)
    }
  }

  /** Fireball - large fire orb with intense glow and embers */
  private def drawFireball(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 29) * 0.35
    val p = (0.85 + 0.15 * Math.sin(phase)).toFloat
    val (ndx, ndy, _) = screenDir(proj)

    // Massive outer heat glow
    sb.fillOvalSoft(sx, sy, 40f, 28f, 1f, 0.25f, 0f, 0.1f * p, 0f, 20)
    sb.fillOvalSoft(sx, sy, 26f, 18f, 1f, 0.4f, 0f, 0.18f * p, 0.02f, 16)
    // Fire body layers
    sb.fillOval(sx, sy, 16f, 11f, 1f, 0.4f, 0.03f, 0.7f * p, 14)
    sb.fillOval(sx, sy, 10f, 7f, 1f, 0.65f, 0.1f, 0.8f * p, 12)
    sb.fillOval(sx, sy, 5f, 3.5f, 1f, 0.9f, 0.5f, 0.9f, 8)
    // White-hot center
    sb.fillOval(sx, sy, 2.5f, 2f, 1f, 1f, 0.85f, 0.8f, 6)

    // Fire licks (rising flame tongues)
    for (i <- 0 until 5) {
      val flAngle = phase * 2 + i * Math.PI * 2 / 5
      val flLen = 10f + Math.sin(phase * 3 + i * 2.1).toFloat * 6f
      val fx = sx + Math.cos(flAngle).toFloat * 8f
      val fy = sy + Math.sin(flAngle).toFloat * 5f - flLen * 0.5f
      sb.fillOval(fx, fy, 4f, flLen * 0.3f, 1f, 0.5f, 0f, 0.3f * p, 8)
    }

    // Ember trail
    for (i <- 0 until 8) {
      val t = ((tick * 0.08 + i * 0.125 + proj.id * 0.13) % 1.0).toFloat
      val spread = Math.sin(phase + i * 2.3).toFloat * 6f
      val fx = sx - ndx * t * 28 + spread
      val fy = sy - ndy * t * 28 - t * 8f
      val a = Math.max(0f, 0.45f * (1f - t) * p)
      val s = 3.5f + (1f - t) * 4f
      val green = Math.max(0f, 0.5f * (1f - t))
      sb.fillOval(fx, fy, s, s * 0.7f, 1f, green, 0f, a, 8)
    }
  }

  /** Rocket - body with large exhaust plume and smoke */
  private def drawRocket(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val (tipX, tipY) = beamTip(sx, sy, proj, 4f)
    val phase = (tick + proj.id * 29) * 0.35
    val p = (0.85 + 0.15 * Math.sin(phase)).toFloat
    val dx = tipX - sx; val dy = tipY - sy
    val len = Math.sqrt(dx * dx + dy * dy).toFloat
    if (len < 1) return
    val nx = dx / len; val ny = dy / len
    val perpX = -ny; val perpY = nx

    // Exhaust glow
    sb.fillOvalSoft(sx - dx * 0.2f, sy - dy * 0.2f, 20f, 14f, 1f, 0.5f, 0.1f, 0.1f * p, 0f, 14)

    // Exhaust plume
    for (i <- 0 until 8) {
      val t = ((tick * 0.1 + i * 0.125 + proj.id * 0.07) % 1.0).toFloat
      val cw = 3f + t * 12f
      val fx = sx - dx * t * 0.6f + perpX * Math.sin(phase * 3 + i * 1.5).toFloat * cw * 0.3f
      val fy = sy - dy * t * 0.6f + perpY * Math.sin(phase * 3 + i * 1.5).toFloat * cw * 0.3f
      val a = Math.max(0f, 0.45f * (1f - t))
      val green = Math.max(0f, 0.9f - t * 0.5f)
      sb.fillOval(fx, fy, 2.5f + t * 4f, 2f + t * 3f, 1f, green, Math.max(0f, 0.4f - t * 0.3f), a, 8)
    }

    // Smoke trail
    for (i <- 0 until 5) {
      val t = ((tick * 0.05 + i * 0.2 + proj.id * 0.09) % 1.0).toFloat
      val smX = sx - dx * t * 0.7f + perpX * Math.sin(phase * 0.8 + i * 1.3).toFloat * (4f + t * 6f)
      val smY = sy - dy * t * 0.7f + perpY * Math.sin(phase * 0.8 + i * 1.3).toFloat * (4f + t * 6f) - t * 7f
      val gray = 0.4f + t * 0.15f
      sb.fillOval(smX, smY, 3.5f + t * 6f, 3f + t * 5f, gray, gray, gray, Math.max(0f, 0.18f * (1f - t)), 8)
    }

    // Rocket body
    sb.strokeLine(sx, sy, tipX, tipY, 10f * p, 0.3f, 0.35f, 0.2f, 0.75f * p)
    sb.strokeLine(sx, sy, tipX, tipY, 3.5f, 0.45f, 0.5f, 0.3f, 0.45f * p)

    // Red band
    val bandX = sx + dx * 0.45f; val bandY = sy + dy * 0.45f
    sb.strokeLine(bandX - dx * 0.04f, bandY - dy * 0.04f, bandX + dx * 0.04f, bandY + dy * 0.04f, 11f, 0.9f, 0.2f, 0.1f, 0.65f * p)

    // Fins
    for (f <- -1 to 1 by 2)
      sb.fillPolygon(Array(sx, (sx - nx * 4f + perpX * f * 7f).toFloat, (sx - nx * 12f).toFloat),
        Array(sy, (sy - ny * 4f + perpY * f * 7f).toFloat, (sy - ny * 12f).toFloat), 0.35f, 0.4f, 0.25f, 0.65f * p)

    // Nosecone
    val noseX = tipX + nx * 14f; val noseY = tipY + ny * 14f
    sb.fillPolygon(Array(noseX, tipX + perpX * 6f, tipX - perpX * 6f),
      Array(noseY, tipY + perpY * 6f, tipY - perpY * 6f), 0.6f, 0.6f, 0.55f, 0.8f * p)
    sb.fillOvalSoft(noseX, noseY, 9f * p, 7f * p, 1f, 0.4f, 0.1f, 0.12f * p, 0f, 10)
  }

  /** Lightning - jagged bolt with branches */
  private def drawLightning(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val (tipX, tipY) = beamTip(sx, sy, proj, 5f)
    val phase = (tick + proj.id * 41) * 0.5
    val flicker = (0.7 + 0.3 * Math.sin(phase * 8)).toFloat
    val dx = tipX - sx; val dy = tipY - sy
    val len = Math.sqrt(dx * dx + dy * dy).toFloat
    if (len < 1) return
    val nx = -dy / len; val ny = dx / len

    // Jagged bolt path
    val segs = 10
    val bxs = new Array[Float](segs + 1); val bys = new Array[Float](segs + 1)
    bxs(0) = sx; bys(0) = sy; bxs(segs) = tipX; bys(segs) = tipY
    for (i <- 1 until segs) {
      val t = i.toFloat / segs
      val jitter = Math.sin(phase * 6 + i * 2.7).toFloat * 10f
      bxs(i) = sx + dx * t + nx * jitter
      bys(i) = sy + dy * t + ny * jitter
    }

    // Wide glow
    for (i <- 0 until segs) sb.strokeLineSoft(bxs(i), bys(i), bxs(i + 1), bys(i + 1), 18f, 0.5f, 0.35f, 1f, 0.08f * flicker)
    // Glow layer
    for (i <- 0 until segs) sb.strokeLine(bxs(i), bys(i), bxs(i + 1), bys(i + 1), 10f, 0.55f, 0.4f, 1f, 0.15f * flicker)
    // Main bolt
    for (i <- 0 until segs) sb.strokeLine(bxs(i), bys(i), bxs(i + 1), bys(i + 1), 4.5f, 0.7f, 0.6f, 1f, 0.7f * flicker)
    // White core
    for (i <- 0 until segs) sb.strokeLine(bxs(i), bys(i), bxs(i + 1), bys(i + 1), 2f, 0.9f, 0.88f, 1f, 0.9f * flicker)

    // Branch bolts (3 branches)
    for (b <- 0 until 3) {
      val bSeg = 2 + b * 2
      if (bSeg < segs) {
        val bAngle = Math.PI * 0.35 * (if (b % 2 == 0) 1 else -1) + Math.sin(phase * 2 + b).toFloat * 0.3
        val bLen = 16f + Math.sin(phase * 3 + b * 2.1).toFloat * 6f
        val bex = bxs(bSeg) + Math.cos(bAngle).toFloat * bLen
        val bey = bys(bSeg) + Math.sin(bAngle).toFloat * bLen * 0.5f
        sb.strokeLine(bxs(bSeg), bys(bSeg), bex, bey, 5f, 0.55f, 0.4f, 1f, 0.12f * flicker)
        sb.strokeLine(bxs(bSeg), bys(bSeg), bex, bey, 2.5f, 0.7f, 0.6f, 1f, 0.4f * flicker)
        sb.strokeLine(bxs(bSeg), bys(bSeg), bex, bey, 1f, 0.9f, 0.85f, 1f, 0.6f * flicker)
      }
    }

    // Tip flash
    sb.fillOvalSoft(tipX, tipY, 12f, 8f, 0.7f, 0.6f, 1f, 0.15f * flicker, 0f, 12)
    sb.fillOval(tipX, tipY, 6f, 4f, 0.85f, 0.75f, 1f, 0.35f * flicker, 10)
  }

  /** Thunder Strike - vertical bolt with large ground impact */
  private def drawThunderStrike(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 31) * 0.5
    val flicker = (0.7 + 0.3 * Math.sin(phase * 8)).toFloat

    // Ground flash
    sb.fillOvalSoft(sx, sy, 60f, 45f, 1f, 0.95f, 0.4f, 0.05f * flicker, 0f, 20)

    // Zigzag bolt from above
    val pts = 9
    val bx = new Array[Float](pts); val by = new Array[Float](pts)
    bx(0) = sx - 4; by(0) = sy - 45
    bx(1) = sx + 10; by(1) = sy - 28
    bx(2) = sx + 1; by(2) = sy - 22
    bx(3) = sx + 12; by(3) = sy - 6
    bx(4) = sx + 4; by(4) = sy - 3
    bx(5) = sx + 14; by(5) = sy + 16
    bx(6) = sx + 6; by(6) = sy + 13
    bx(7) = sx + 12; by(7) = sy + 28
    bx(8) = sx + 3; by(8) = sy + 28

    for (i <- 0 until pts - 1) sb.strokeLineSoft(bx(i), by(i), bx(i + 1), by(i + 1), 18f, 1f, 0.85f, 0.2f, 0.12f * flicker)
    for (i <- 0 until pts - 1) sb.strokeLine(bx(i), by(i), bx(i + 1), by(i + 1), 12f, 1f, 0.85f, 0.2f, 0.25f * flicker)
    for (i <- 0 until pts - 1) sb.strokeLine(bx(i), by(i), bx(i + 1), by(i + 1), 6f, 1f, 0.9f, 0.4f, 0.55f * flicker)
    for (i <- 0 until pts - 1) sb.strokeLine(bx(i), by(i), bx(i + 1), by(i + 1), 3f, 1f, 1f, 0.8f, 0.9f * flicker)

    // Ground impact ring
    sb.fillOvalSoft(sx, sy + 28f, 20f, 7f, 1f, 0.9f, 0.3f, 0.18f * flicker, 0f, 16)
    for (i <- 0 until 8) {
      val angle = phase * 2 + i * Math.PI / 4
      val dist = 10f + Math.sin(phase * 3 + i).toFloat * 6f
      sb.fillOval((sx + Math.cos(angle).toFloat * dist), (sy + 26 + Math.sin(angle).toFloat * dist * 0.3f).toFloat,
        2.5f, 2f, 1f, 1f, 0.5f, 0.45f * flicker, 6)
    }
  }

  /** Boulder - large rolling rock with dust cloud */
  private def drawBoulder(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 19) * 0.3
    val bounce = (Math.abs(Math.sin(phase)) * 4f).toFloat
    val r = 14f
    val (ndx, ndy, _) = screenDir(proj)

    // Shadow
    sb.fillOval(sx, sy + r * 0.35f, r * 1.1f, r * 0.35f, 0f, 0f, 0f, 0.12f, 14)
    // Main rock body
    sb.fillOval(sx, sy - bounce, r, r * 0.9f, 0.42f, 0.33f, 0.22f, 0.9f, 16)
    // Dark cracks
    sb.strokeLine(sx - 4, sy - bounce - 3, sx + 5, sy - bounce + 4, 1.2f, 0.22f, 0.15f, 0.1f, 0.55f)
    sb.strokeLine(sx + 2, sy - bounce - 5, sx - 3, sy - bounce + 2, 1f, 0.22f, 0.15f, 0.1f, 0.45f)
    // Highlight
    sb.fillOval(sx + Math.cos(phase).toFloat * 3, sy - bounce - r * 0.6f, r * 0.35f, r * 0.25f, 0.58f, 0.48f, 0.34f, 0.5f, 10)

    // Dust cloud trail
    for (i <- 0 until 6) {
      val t = ((tick * 0.07 + i * 0.167 + proj.id * 0.11) % 1.0).toFloat
      val a = Math.max(0f, 0.22f * (1f - t))
      val s = 3f + t * 5f
      sb.fillOval(sx - ndx * t * 20 + Math.sin(phase + i * 2).toFloat * 4, sy - ndy * t * 14 + t * 7,
        s, s * 0.65f, 0.5f, 0.4f, 0.3f, a, 8)
    }
    // Small debris
    for (i <- 0 until 3) {
      val da = phase * 2 + i * 2.1
      val dd = r * 0.6f + Math.sin(phase * 3 + i * 1.5).toFloat * 4f
      sb.fillOval(sx + Math.cos(da).toFloat * dd, sy - bounce + Math.sin(da).toFloat * dd * 0.5f,
        2.5f, 2f, 0.5f, 0.38f, 0.25f, 0.4f, 6)
    }
  }

  /** Tidal Wave - tall water crescent with foam */
  private def drawTidalWave(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 29) * 0.35
    val p = (0.8 + 0.2 * Math.sin(phase)).toFloat
    val (ndx, ndy, _) = screenDir(proj)
    val perpX = -ndy; val perpY = ndx

    // Water glow
    sb.fillOvalSoft(sx, sy, 38f, 22f, 0.2f, 0.5f, 0.85f, 0.06f * p, 0f, 16)

    // Wave crest (larger)
    val crestW = 32f; val crestH = 20f
    val xs = new Array[Float](10); val ys = new Array[Float](10)
    for (i <- 0 until 10) {
      val t = i.toFloat / 9
      val off = (t - 0.5f) * 2f
      val height = (1f - off * off) * crestH * p
      xs(i) = sx + perpX * off * crestW + ndx * height * 0.3f
      ys(i) = sy + perpY * off * crestW * 0.6f + ndy * height * 0.3f - height
    }
    // Fill wave body
    for (i <- 0 until 8) sb.strokeLine(xs(i), ys(i), xs(i + 1), ys(i + 1), 7f, 0.15f, 0.4f, 0.75f, 0.35f * p)
    for (i <- 0 until 8) sb.strokeLine(xs(i), ys(i), xs(i + 1), ys(i + 1), 4f, 0.25f, 0.55f, 0.9f, 0.55f * p)
    for (i <- 0 until 8) sb.strokeLine(xs(i), ys(i), xs(i + 1), ys(i + 1), 2f, 0.45f, 0.75f, 1f, 0.7f * p)
    // Foam cap
    for (i <- 2 until 7) sb.strokeLine(xs(i), ys(i), xs(i + 1), ys(i + 1), 2f, 0.8f, 0.95f, 1f, 0.4f * p)

    // Spray foam
    for (i <- 0 until 7) {
      val t = ((tick * 0.06 + i * 0.143 + proj.id * 0.11) % 1.0).toFloat
      val dropX = xs(1 + i) + ndx * t * 10 + Math.sin(phase + i * 2).toFloat * 4
      val dropY = ys(1 + i) + ndy * t * 10 - t * 7
      sb.fillOval(dropX, dropY, 3f, 2.5f, 0.7f, 0.9f, 1f, Math.max(0f, 0.35f * (1f - t) * p), 6)
    }
  }

  /** Geyser - tall water eruption column */
  private def drawGeyser(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 23) * 0.4
    val p = (0.8 + 0.2 * Math.sin(phase)).toFloat

    // Water column
    for (i <- 0 until 10) {
      val t = ((tick * 0.055 + i * 0.1 + proj.id * 0.11) % 1.0).toFloat
      val spread = Math.sin(phase * 2 + i * 2.3).toFloat * (4f + t * 9f)
      val py = sy - t * 36f
      val a = Math.max(0f, 0.45f * (1f - t) * p)
      val s = 3.5f + t * 6f
      sb.fillOval(sx + spread, py, s, s * 0.7f, 0.3f, 0.6f, 1f, a, 8)
    }

    // Column glow
    sb.fillOvalSoft(sx, sy - 14f, 10f, 20f, 0.2f, 0.5f, 0.9f, 0.08f * p, 0f, 12)

    // Base pool
    sb.fillOval(sx, sy, 20f, 7f, 0.2f, 0.5f, 0.9f, 0.3f * p, 16)
    sb.fillOval(sx, sy, 13f, 4.5f, 0.5f, 0.8f, 1f, 0.45f * p, 12)

    // Crown droplets
    for (i <- 0 until 6) {
      val angle = phase + i * Math.PI / 3
      val dist = 7f + Math.sin(phase * 2 + i).toFloat * 4f
      sb.fillOval(sx + Math.cos(angle).toFloat * dist, sy - 32f + Math.sin(angle).toFloat * dist * 0.3f - 3f,
        3f, 2.5f, 0.5f, 0.85f, 1f, 0.35f * p, 6)
    }
  }

  /** Sword Wave - energy crescent slash */
  private def drawSwordWave(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 37) * 0.4
    val p = (0.85 + 0.15 * Math.sin(phase)).toFloat
    val (ndx, ndy, _) = screenDir(proj)
    val arcR = 24f
    val segs = 12
    val baseAngle = Math.atan2(ndy, ndx)

    // Compute crescent
    val outerXs = new Array[Float](segs + 1); val outerYs = new Array[Float](segs + 1)
    val innerXs = new Array[Float](segs + 1); val innerYs = new Array[Float](segs + 1)
    for (i <- 0 to segs) {
      val a = baseAngle - Math.PI * 0.4 + Math.PI * 0.8 * i / segs
      outerXs(i) = (sx + Math.cos(a).toFloat * arcR)
      outerYs(i) = (sy + Math.sin(a).toFloat * arcR * 0.55f)
      innerXs(i) = (sx + Math.cos(a).toFloat * arcR * 0.45f)
      innerYs(i) = (sy + Math.sin(a).toFloat * arcR * 0.25f)
    }

    // Fill crescent
    val crescXs = new Array[Float]((segs + 1) * 2)
    val crescYs = new Array[Float]((segs + 1) * 2)
    for (i <- 0 to segs) { crescXs(i) = outerXs(i); crescYs(i) = outerYs(i) }
    for (i <- 0 to segs) { crescXs(segs + 1 + i) = innerXs(segs - i); crescYs(segs + 1 + i) = innerYs(segs - i) }

    // Outer glow
    sb.fillOvalSoft(sx, sy, arcR * 1.1f, arcR * 0.6f, 0.7f, 0.7f, 0.9f, 0.06f * p, 0f, 16)
    // Crescent fill
    sb.fillPolygon(crescXs, crescYs, 0.7f, 0.7f, 0.85f, 0.4f * p)
    // Bright outer edge
    for (i <- 0 until segs) sb.strokeLine(outerXs(i), outerYs(i), outerXs(i + 1), outerYs(i + 1),
      2.5f, 0.9f, 0.9f, 1f, 0.8f * p)

    // Edge sparkles
    for (i <- 0 until 4) {
      val t = ((tick * 0.1 + i * 0.25 + proj.id * 0.13) % 1.0).toFloat
      val idx = (t * segs).toInt.min(segs - 1)
      sb.fillOval(outerXs(idx), outerYs(idx), 2.5f, 2f, 1f, 1f, 1f, (0.3 + 0.3 * Math.sin(phase * 3 + i * 2)).toFloat * p, 6)
    }
  }

  /** Bat Swarm - fluttering dark cloud with many bats */
  private def drawBatSwarm(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 31) * 0.4
    val p = (0.8 + 0.2 * Math.sin(phase)).toFloat

    // Dark cloud
    sb.fillOvalSoft(sx, sy, 26f, 18f, 0.15f, 0f, 0.2f, 0.12f * p, 0f, 16)

    for (bat <- 0 until 7) {
      val bAngle = phase * 1.5 + bat * Math.PI * 2 / 7
      val bDist = 7f + Math.sin(phase * 2 + bat * 1.7).toFloat * 5f
      val bx = sx + Math.cos(bAngle).toFloat * bDist
      val by = sy + Math.sin(bAngle).toFloat * bDist * 0.5f
      val wingFlap = Math.sin(phase * 8 + bat * 2.3).toFloat * 5f

      // Wings (larger)
      sb.fillPolygon(Array(bx, bx - 5f, bx - 2.5f), Array(by, by - wingFlap, by + 1.5f), 0.1f, 0f, 0.15f, 0.65f * p)
      sb.fillPolygon(Array(bx, bx + 5f, bx + 2.5f), Array(by, by - wingFlap, by + 1.5f), 0.1f, 0f, 0.15f, 0.65f * p)
      // Body
      sb.fillOval(bx, by, 1.5f, 2f, 0.08f, 0f, 0.12f, 0.7f * p, 6)
      // Red eyes
      sb.fillOval(bx - 0.7f, by - 0.7f, 1f, 0.8f, 0.9f, 0.1f, 0.1f, 0.55f * p, 4)
      sb.fillOval(bx + 0.7f, by - 0.7f, 1f, 0.8f, 0.9f, 0.1f, 0.1f, 0.55f * p, 4)
    }
  }

  /** Raise Dead - skeleton hands rising from the ground */
  private def drawRaiseDead(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 29) * 0.35
    val p = (0.8 + 0.2 * Math.sin(phase)).toFloat

    // Ground disturbance
    sb.fillOvalSoft(sx, sy + 5, 28f, 8f, 0.05f, 0.2f, 0.02f, 0.12f * p, 0f, 14)
    sb.fillOval(sx, sy + 5, 22f, 6f, 0.05f, 0.18f, 0.02f, 0.1f * p, 14)

    for (hand <- 0 until 4) {
      val hx = sx + (hand - 1.5f) * 12f
      val rise = ((tick * 0.04 + hand * 0.25) % 1.0).toFloat
      val hy = sy - rise * 26f
      val a = Math.max(0f, 0.7f * (1f - rise * 0.35f) * p)
      // Arm bone
      sb.strokeLine(hx, sy, hx + Math.sin(phase + hand).toFloat * 3, hy, 3f, 0.72f, 0.68f, 0.52f, a)
      // Fingers
      for (f <- -1 to 1) {
        val fx = hx + f * 4.5f + Math.sin(phase + hand + f).toFloat * 2.5f
        sb.strokeLine(hx + Math.sin(phase + hand).toFloat * 3, hy, fx, hy - 7, 1.8f, 0.78f, 0.73f, 0.58f, a * 0.85f)
      }
    }

    // Green soul wisps
    for (i <- 0 until 8) {
      val angle = phase * 1.5 + i * Math.PI / 4
      val dist = 12f + Math.sin(phase * 2 + i * 1.3).toFloat * 5
      sb.fillOval(sx + Math.cos(angle).toFloat * dist, sy + Math.sin(angle).toFloat * dist * 0.35f - 10,
        2.5f, 2f, 0.3f, 0.9f, 0.2f, Math.max(0f, Math.min(1f, (0.35 + 0.2 * Math.sin(phase * 3 + i * 2)).toFloat * p)), 6)
    }
  }

  /** Wail - ghostly screaming face with ectoplasm */
  private def drawWail(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 41) * 0.4
    val p = (0.7 + 0.3 * Math.sin(phase * 3)).toFloat
    val wobble = Math.sin(phase * 2).toFloat * 3

    // Ghostly aura
    sb.fillOvalSoft(sx + wobble, sy, 28f, 22f, 0.7f, 0.8f, 0.92f, 0.1f * p, 0f, 16)
    // Main face
    sb.fillOval(sx + wobble, sy, 22f, 18f, 0.75f, 0.85f, 0.95f, 0.3f * p, 16)
    sb.fillOval(sx + wobble, sy, 15f, 12f, 0.8f, 0.88f, 0.95f, 0.5f * p, 14)

    // Dark eye sockets
    sb.fillOval(sx - 5.5f + wobble, sy - 3.5f, 3.5f, 3f, 0.05f, 0.05f, 0.15f, 0.75f * p, 10)
    sb.fillOval(sx + 6.5f + wobble, sy - 3.5f, 3.5f, 3f, 0.05f, 0.05f, 0.15f, 0.75f * p, 10)
    // Eye glow
    sb.fillOval(sx - 5.5f + wobble, sy - 3.5f, 1.8f, 1.5f, 0.5f, 0.7f, 0.9f, 0.3f * p, 6)
    sb.fillOval(sx + 6.5f + wobble, sy - 3.5f, 1.8f, 1.5f, 0.5f, 0.7f, 0.9f, 0.3f * p, 6)

    // Screaming mouth
    val mouthOpen = 5f + Math.abs(Math.sin(phase * 4)).toFloat * 6f
    sb.fillOval(sx + wobble, sy + 3 + mouthOpen / 2, 5f, mouthOpen / 2, 0.05f, 0.05f, 0.12f, 0.7f * p, 10)

    // Ectoplasmic wisps (more)
    for (i <- 0 until 6) {
      val t = ((tick * 0.06 + i * 0.167 + proj.id * 0.13) % 1.0).toFloat
      val wx = sx + wobble + Math.sin(phase * 2 + i * 1.8).toFloat * 8
      sb.fillOval(wx, sy + 6 + t * 22, 3.5f + t * 5f, 2.5f + t * 2.5f, 0.6f, 0.75f, 0.85f, Math.max(0f, 0.35f * (1f - t) * p), 8)
    }
  }

  /** Devour - large chomping jaws with drool */
  private def drawDevour(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 23) * 0.45
    val chomp = (Math.abs(Math.sin(phase * 3)) * 0.6).toFloat
    val jawOpen = 5f + chomp * 8f
    val p = (0.85 + 0.15 * Math.sin(phase)).toFloat

    // Glow
    sb.fillOvalSoft(sx, sy, 22f, 18f, 0.4f, 0.05f, 0.08f, 0.06f * p, 0f, 12)
    // Upper jaw
    sb.fillOval(sx, sy - jawOpen, 11f, 5.5f, 0.18f, 0.05f, 0.08f, 0.8f, 12)
    // Lower jaw
    sb.fillOval(sx, sy + jawOpen, 11f, 5.5f, 0.18f, 0.05f, 0.08f, 0.8f, 12)
    // Inner mouth
    sb.fillOval(sx, sy, 7f, 4f, 0.7f, 0.08f, 0.05f, 0.4f * (1f - chomp), 10)

    // Teeth (5 per jaw)
    for (i <- 0 until 5) {
      val tx = sx - 7 + i * 3.5f
      sb.fillPolygon(Array(tx, tx + 1.5f, tx + 3), Array(sy - jawOpen + 2, sy - jawOpen + 6, sy - jawOpen + 2),
        0.92f, 0.88f, 0.82f, 0.8f * p)
      sb.fillPolygon(Array(tx, tx + 1.5f, tx + 3), Array(sy + jawOpen - 2, sy + jawOpen - 6, sy + jawOpen - 2),
        0.92f, 0.88f, 0.82f, 0.8f * p)
    }

    // Drool
    for (i <- 0 until 2) {
      val dt = ((tick * 0.05 + i * 0.5 + proj.id * 0.11) % 1.0).toFloat
      val dx2 = sx - 3 + i * 6
      sb.strokeLine(dx2.toFloat, sy + jawOpen - 3, dx2.toFloat, sy + jawOpen + dt * 10,
        1f, 0.5f, 0.6f, 0.3f, Math.max(0f, 0.3f * (1f - dt)))
    }
  }

  /** Scythe - spinning curved blade with ghost trail */
  private def drawScythe(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val spin = tick * 0.3 + proj.id * 2.3
    val p = (0.85 + 0.15 * Math.sin(spin * 2)).toFloat
    val bladeR = 22f

    // Ghost trail
    val (ndx, ndy, _) = screenDir(proj)
    for (ghost <- 1 to 2) {
      val gx = sx - ndx * ghost * 8f; val gy = sy - ndy * ghost * 8f
      val ga = 0.06f * (1f - ghost * 0.35f) * p
      val gSpin = spin - ghost * 0.3
      val segs2 = 8
      for (i <- 0 until segs2) {
        val a1 = gSpin + Math.PI * 0.75 * i / segs2
        val a2 = gSpin + Math.PI * 0.75 * (i + 1) / segs2
        sb.strokeLine((gx + Math.cos(a1).toFloat * bladeR), (gy + Math.sin(a1).toFloat * bladeR * 0.6f),
          (gx + Math.cos(a2).toFloat * bladeR), (gy + Math.sin(a2).toFloat * bladeR * 0.6f), 3f, 0.7f, 0.72f, 0.78f, ga)
      }
    }

    // Blade glow
    sb.fillOvalSoft(sx, sy, 24f, 16f, 0.78f, 0.8f, 0.85f, 0.05f * p, 0f, 14)

    // Curved blade
    val startAngle = spin
    val arcLen = Math.PI * 0.75
    val segs = 12
    val arcXs = new Array[Float](segs * 2 + 2)
    val arcYs = new Array[Float](segs * 2 + 2)
    for (i <- 0 to segs) {
      val a = startAngle + arcLen * i / segs
      arcXs(i) = (sx + Math.cos(a).toFloat * bladeR)
      arcYs(i) = (sy + Math.sin(a).toFloat * bladeR * 0.6f)
    }
    for (i <- 0 to segs) {
      val a = startAngle + arcLen * (segs - i) / segs
      arcXs(segs + 1 + i) = (sx + Math.cos(a).toFloat * bladeR * 0.45f)
      arcYs(segs + 1 + i) = (sy + Math.sin(a).toFloat * bladeR * 0.27f)
    }
    sb.fillPolygon(arcXs, arcYs, 0.75f, 0.78f, 0.83f, 0.7f * p)

    // Gleaming outer edge
    for (i <- 0 until segs) {
      val a1 = startAngle + arcLen * i / segs; val a2 = startAngle + arcLen * (i + 1) / segs
      sb.strokeLine((sx + Math.cos(a1).toFloat * bladeR), (sy + Math.sin(a1).toFloat * bladeR * 0.6f),
        (sx + Math.cos(a2).toFloat * bladeR), (sy + Math.sin(a2).toFloat * bladeR * 0.6f), 2.5f, 0.92f, 0.93f, 0.96f, 0.85f * p)
    }

    // Handle
    val ha = startAngle + arcLen + 0.3
    sb.strokeLine(sx, sy, (sx + Math.cos(ha).toFloat * 14), (sy + Math.sin(ha).toFloat * 8.5f), 3.5f, 0.48f, 0.38f, 0.28f, 0.75f)
  }

  /** Reap - massive death arc with soul wisps */
  private def drawReap(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 31) * 0.4
    val p = (0.8 + 0.2 * Math.sin(phase)).toFloat
    val (ndx, ndy, _) = screenDir(proj)
    val baseAngle = Math.atan2(ndy, ndx) + Math.sin(phase * 2) * 0.3
    val arcR = 44f; val arcLen = Math.PI * 1.2

    // Dark aura
    sb.fillOvalSoft(sx, sy, arcR * 1.2f, arcR * 0.75f, 0.1f, 0f, 0.15f, 0.12f * p, 0f, 18)

    // Ghostly slash arcs
    for (slash <- 0 until 3) {
      val sp = ((phase * 0.5 + slash * 0.15) % 1.0).toFloat
      val slashR = arcR * (0.55f + sp * 0.55f)
      val slashA = Math.max(0f, 0.5f * (1f - sp * 0.7f) * p)
      val segs2 = 14
      for (i <- 0 until segs2) {
        val a1 = baseAngle - arcLen / 2 + arcLen * i / segs2
        val a2 = baseAngle - arcLen / 2 + arcLen * (i + 1) / segs2
        sb.strokeLine((sx + Math.cos(a1).toFloat * slashR), (sy + Math.sin(a1).toFloat * slashR * 0.5f),
          (sx + Math.cos(a2).toFloat * slashR), (sy + Math.sin(a2).toFloat * slashR * 0.5f),
          5f * (1f - sp * 0.5f), 0.4f, 0.15f, 0.55f, slashA)
      }
    }

    // Bright spectral edge
    val segs = 14
    for (i <- 0 until segs) {
      val a1 = baseAngle - arcLen / 2 + arcLen * i / segs
      val a2 = baseAngle - arcLen / 2 + arcLen * (i + 1) / segs
      sb.strokeLine((sx + Math.cos(a1).toFloat * arcR), (sy + Math.sin(a1).toFloat * arcR * 0.5f),
        (sx + Math.cos(a2).toFloat * arcR), (sy + Math.sin(a2).toFloat * arcR * 0.5f), 3f, 0.8f, 0.6f, 1f, 0.75f * p)
    }

    // Soul wisps
    for (i <- 0 until 8) {
      val wAngle = baseAngle - arcLen / 2 + arcLen * (i + 0.5) / 8
      val wDist = arcR * (1f - ((tick * 0.05f + i * 0.125f) % 1.0f) * 0.55f)
      sb.fillOval((sx + Math.cos(wAngle).toFloat * wDist), (sy + Math.sin(wAngle).toFloat * wDist * 0.5f),
        3.5f, 2.5f, 0.6f, 0.35f, 0.8f,
        Math.max(0f, (0.45 * ((tick * 0.05 + i * 0.125) % 1.0) * p).toFloat), 8)
    }
  }

  /** Shadow Bolt - dark void mass with purple eyes and tendrils */
  private def drawShadowBolt(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 43) * 0.35
    val p = (0.8 + 0.2 * Math.sin(phase)).toFloat
    val w1 = Math.sin(phase * 2.3).toFloat * 4
    val w2 = Math.cos(phase * 1.7).toFloat * 3

    // Void aura
    sb.fillOvalSoft(sx + w1, sy + w2, 28f, 20f, 0.15f, 0f, 0.22f, 0.12f * p, 0f, 16)
    // Dark mass
    sb.fillOval(sx + w1, sy + w2, 18f, 12f, 0.12f, 0f, 0.18f, 0.3f * p, 14)
    sb.fillOval(sx, sy, 13f, 9f, 0.08f, 0f, 0.12f, 0.8f, 14)

    // Shadow tendrils
    for (i <- 0 until 6) {
      val angle = phase * 1.2 + i * Math.PI * 2 / 6
      val tLen = 20f + Math.sin(phase * 2.5 + i * 1.9).toFloat * 6
      val ex = sx + Math.cos(angle).toFloat * tLen
      val ey = sy + Math.sin(angle).toFloat * tLen * 0.5f
      sb.strokeLine(sx, sy, ex, ey, 5f, 0.25f, 0.03f, 0.4f, 0.2f * p)
      sb.strokeLine(sx, sy, ex, ey, 2.5f, 0.1f, 0f, 0.18f, 0.4f * p)
    }

    // Purple eyes
    val eyePulse = (0.5 + 0.5 * Math.sin(phase * 4)).toFloat
    sb.fillOval(sx - 4, sy - 2, 2.5f, 2f, 0.7f, 0.2f, 1f, 0.65f * eyePulse * p, 8)
    sb.fillOval(sx + 5, sy - 2, 2.5f, 2f, 0.7f, 0.2f, 1f, 0.65f * eyePulse * p, 8)
    // Eye glow
    sb.fillOvalSoft(sx - 4, sy - 2, 5f, 4f, 0.7f, 0.2f, 1f, 0.08f * eyePulse * p, 0f, 8)
    sb.fillOvalSoft(sx + 5, sy - 2, 5f, 4f, 0.7f, 0.2f, 1f, 0.08f * eyePulse * p, 0f, 8)
  }

  /** Inferno Blast - massive spiraling fire vortex */
  private def drawInfernoBlast(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 23) * 0.4
    val p = (0.8 + 0.2 * Math.sin(phase)).toFloat
    val r = 28f

    // Massive heat glow
    sb.fillOvalSoft(sx, sy, r * 3f, r * 2.5f, 1f, 0.2f, 0f, 0.05f * p, 0f, 20)

    // Spinning fire spirals (4 arms)
    for (arm <- 0 until 4) {
      val segs2 = 10
      for (i <- 0 until segs2) {
        val t = i.toFloat / segs2
        val spiralAngle = phase * 2.5 + t * Math.PI * 2 + arm * Math.PI / 2
        val spiralR = r * t
        val px = sx + Math.cos(spiralAngle).toFloat * spiralR
        val py = sy + Math.sin(spiralAngle).toFloat * spiralR * 0.5f
        val s = 5f + t * 6f
        val a = Math.max(0f, 0.4f * (1f - t * 0.5f) * p)
        sb.fillOval(px, py, s, s * 0.65f, 1f, Math.max(0f, 0.5f * (1f - t)), 0f, a, 8)
      }
    }

    // Fire core
    sb.fillOval(sx, sy, r * 0.5f, r * 0.4f, 1f, 0.5f, 0f, 0.55f * p, 14)
    sb.fillOval(sx, sy, r * 0.3f, r * 0.25f, 1f, 0.75f, 0.15f, 0.65f * p, 12)
    sb.fillOval(sx, sy, 5f, 4f, 1f, 1f, 0.65f, 0.75f, 8)

    // Orbiting embers
    for (i <- 0 until 8) {
      val angle = phase * 1.5 + i * Math.PI / 4
      val fl = r + Math.sin(phase * 3 + i * 2).toFloat * 10
      val fy = Math.abs(Math.sin(phase * 4 + i)).toFloat * 6
      sb.fillOval(sx + Math.cos(angle).toFloat * fl, sy + Math.sin(angle).toFloat * fl * 0.5f - fy,
        3f, 2.5f, 1f, 0.5f, 0f, Math.max(0f, 0.35f * p), 6)
    }
  }

  /** Jaw - large shark jaws with teeth and water trail */
  private def drawJaw(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val (tipX, tipY) = beamTip(sx, sy, proj, 6f)
    val phase = (tick + proj.id * 17) * 0.4
    val p = (0.85 + 0.15 * Math.sin(phase)).toFloat
    val chomp = (Math.abs(Math.sin(phase * 3)) * 0.4 + 0.6).toFloat
    val dx = tipX - sx; val dy = tipY - sy
    val len = Math.sqrt(dx * dx + dy * dy).toFloat
    if (len < 1) return
    val nx = dx / len; val ny = dy / len
    val perpX = -ny; val perpY = nx

    // Water trail
    for (i <- 1 to 4) {
      val t = ((tick * 0.06 + i * 0.25 + proj.id * 0.13) % 1.0).toFloat
      sb.fillOval(tipX - nx * t * 25, tipY - ny * t * 25, 5f, 2.5f, 0.3f, 0.5f, 0.7f, Math.max(0f, 0.12f * (1f - t) * p), 8)
    }

    // Upper jaw
    val jawW = 13f * p * chomp; val jawLen = 16f * p
    sb.fillPolygon(
      Array(tipX, tipX - nx * jawLen + perpX * jawW, tipX - nx * jawLen),
      Array(tipY, tipY - ny * jawLen + perpY * jawW, tipY - ny * jawLen),
      0.45f, 0.5f, 0.55f, 0.85f * p)
    // Lower jaw
    sb.fillPolygon(
      Array(tipX, tipX - nx * jawLen - perpX * jawW, tipX - nx * jawLen),
      Array(tipY, tipY - ny * jawLen - perpY * jawW, tipY - ny * jawLen),
      0.4f, 0.45f, 0.5f, 0.85f * p)

    // Teeth (4 per jaw)
    for (i <- 0 until 4) {
      val t = (i + 0.5f) / 4f
      val toothBase = 0.25f + t * 0.65f
      val utx = tipX - nx * jawLen * toothBase + perpX * jawW * (1f - t) * 0.9f
      val uty = tipY - ny * jawLen * toothBase + perpY * jawW * (1f - t) * 0.9f
      val th = 4f * p
      sb.fillPolygon(Array(utx - perpX * th, utx + nx * 2f, utx + perpX * 0.5f),
        Array(uty - perpY * th, uty + ny * 2f, uty + perpY * 0.5f), 0.95f, 0.95f, 0.9f, 0.8f * p)
      val ltx = tipX - nx * jawLen * toothBase - perpX * jawW * (1f - t) * 0.9f
      val lty = tipY - ny * jawLen * toothBase - perpY * jawW * (1f - t) * 0.9f
      sb.fillPolygon(Array(ltx + perpX * th, ltx + nx * 2f, ltx - perpX * 0.5f),
        Array(lty + perpY * th, lty + ny * 2f, lty - perpY * 0.5f), 0.95f, 0.95f, 0.9f, 0.8f * p)
    }

    // Mouth glow
    sb.fillOval(tipX, tipY, 5f * p * chomp, 5f * p * chomp, 0.7f, 0.8f, 0.9f, 0.2f * p, 10)

    // Dorsal fin
    val finX = tipX - nx * jawLen * 0.5f
    val finY = tipY - ny * jawLen * 0.5f - 8f * p
    sb.fillPolygon(Array(finX, finX + perpX * 3f, finX - perpX * 3f),
      Array(finY - 6f * p, finY + 6f, finY + 6f), 0.42f, 0.47f, 0.52f, 0.7f * p)
  }
}
