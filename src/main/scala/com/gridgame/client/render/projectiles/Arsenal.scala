package com.gridgame.client.render.projectiles

import com.gridgame.common.model.Projectile
import com.gridgame.client.gl.ShapeBatch
import ProjectileKit._
import Glyphs._

/**
 * Guns and machines, drawn as whoever fired them: the Soldier's rifle round on its red tracer; the
 * Pirate's musket ball in a puff of black powder; the Pilot's twin yellow tracers, and the strafing run
 * he calls in as a biplane low over the ground; the Spaceman's ray-gun zap, rings rippling down its
 * corkscrew; the Mech Pilot's fist, fired off its arm on a rocket; the Graviton's own cubes, tumbling
 * round the singularity that holds them. They were one cartoon slug with a muzzle flash, an orb in the
 * player's random colour, and a dark purple ball.
 */
private[render] object Arsenal {

  // ── Rounds ──
  private val ROUND = Array(-9f, -2.2f, -3f, -2.3f, -0.8f, -1.2f, 0f, 0f, -0.8f, 1.2f, -3f, 2.3f, -9f, 2.2f)
  private val ROUND_SHADE = Array(-9f, 0.4f, -1f, 0.4f, -0.8f, 1.2f, -3f, 2.3f, -9f, 2.2f)

  /** A round point first at the frame's origin, its tracer streaming back `len` from its base in
    * (r, g, b), shading to white-hot at the base; the jacket copper or `brass`. */
  private def tracerRound(sb: ShapeBatch, tick: Int, id: Int, len: Float, w: Float,
                          r: Float, g: Float, b: Float, a: Float): Unit = {
    // The tracer: a wide glow, its colour, and a white-hot heart at the base
    val bx = fx(-9f, 0f); val by = fy(-9f, 0f)
    val ex = fx(-9f - len, 0f); val ey = fy(-9f - len, 0f)
    fadeLine(sb, bx, by, ex, ey, w * 2.6f, r, g * 0.8f, b * 0.8f, 0.35f * a, 5)
    fadeLine(sb, bx, by, ex, ey, w * 1.6f, r * 0.3f, g * 0.12f, b * 0.1f, 0.6f * a, 5)
    fadeLine(sb, bx, by, ex, ey, w, r, g, b, 0.95f * a, 5)
    fadeLine(sb, bx, by, fx(-9f - len * 0.4f, 0f), fy(-9f - len * 0.4f, 0f), w * 0.45f, 1f, 0.95f, 0.8f, a, 3)
    // The round: ink, copper, its shaded underside, a glint turning round it as it spins
    var n = pts(ROUND)
    sb.strokePolygon(_gx, _gy, n, 2.8f, 0.12f, 0.05f, 0.02f, 0.98f * a)
    sb.fillPolygon(_gx, _gy, n, 0.93f, 0.62f, 0.34f, a)
    n = pts(ROUND_SHADE)
    sb.fillPolygon(_gx, _gy, n, 0.6f, 0.34f, 0.16f, a)
    val gt = loop(tick, 0.09, id * 0.17)
    gLine(sb, -8f + gt * 5f, -1.1f, -6f + gt * 5f, -1.1f, 0.8f, 1f, 0.95f, 0.85f, 0.9f * a)
  }

  /**
   * The Soldier's rifle round: a copper-jacketed bullet, spinning, on a long red tracer white-hot at its
   * base, the shock it pushes through the air peeling back off its point.
   */
  private[projectiles] def drawRifleTracer(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 31) * 0.4
    computeAllDynamics(proj, 1f, 0.3f, 0.15f, phase)
    val a = dynAlpha
    val ds = Math.min(dynScale, 1.3f)
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val k = 1.85f * ds

    sb.fillOvalSoft(sx - ndx * 14f * k, sy - ndy * 14f * k, 22f * ds * dynGlow, 10f * ds * dynGlow, 1f, 0.3f, 0.15f, 0.3f * a, 0f, 12)
    headingFrame(sx, sy, ndx, ndy, k)
    // The shock off its point
    gLine(sb, 0.6f, 0f, -7f, -5f, 0.7f, 0.9f, 0.92f, 0.96f, 0.4f * a)
    gLine(sb, 0.6f, 0f, -7f, 5f, 0.7f, 0.9f, 0.92f, 0.96f, 0.4f * a)
    tracerRound(sb, tick, proj.id, 40f, 3.8f, 1f, 0.26f, 0.14f, a)
  }

  /**
   * The Pilot's guns: a pair of rounds side by side off his twin guns, a step apart, on yellow tracers.
   */
  private[projectiles] def drawTwinTracers(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 31) * 0.4
    computeAllDynamics(proj, 1f, 0.8f, 0.2f, phase)
    val a = dynAlpha
    val ds = Math.min(dynScale, 1.3f)
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val px = -ndy; val py = ndx
    val k = 1.55f * ds

    sb.fillOvalSoft(sx - ndx * 12f * k, sy - ndy * 12f * k, 24f * ds * dynGlow, 13f * ds * dynGlow, 1f, 0.8f, 0.2f, 0.28f * a, 0f, 12)
    var g = -1f
    while (g <= 1f) {
      val lead = if (g < 0f) 0f else -4f
      headingFrame(sx + px * g * 5.5f * ds + ndx * lead * k, sy + py * g * 5.5f * ds + ndy * lead * k, ndx, ndy, k)
      tracerRound(sb, tick, proj.id + (if (g < 0f) 0 else 5), 36f, 3.4f, 1f, 0.82f, 0.22f, a)
      g += 2f
    }
  }

  /**
   * The Pirate's flintlock: a ball of black iron, a puff of black powder smoke billowing after it and
   * sparks of the burning powder spitting off it.
   */
  private[projectiles] def drawMusketBall(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 31) * 0.4
    computeAllDynamics(proj, 0.5f, 0.5f, 0.5f, phase)
    val a = dynAlpha
    val ds = Math.min(dynScale, 1.3f)
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val px = -ndy; val py = ndx
    val R = 5.6f * ds

    // Powder smoke billowing after it, puffs swelling and drifting up as they fall behind
    var p = 4
    while (p >= 0) {
      val t = loop(tick, 0.035, p / 5.0 + proj.id * 0.13)
      val lat = hash2(proj.id + 3, p) * (2f + 7f * t) * ds
      val qx = sx - ndx * (R + 6f * ds + t * 40f * ds) + px * lat
      val qy = sy - ndy * (R + 6f * ds + t * 40f * ds) + py * lat - t * 8f * ds
      val qr = (4.5f + t * 8f) * ds
      val qa = 0.85f * (1f - t * t) * a
      sb.fillOval(qx, qy, qr + 1.6f, qr * 0.86f + 1.6f, 0.2f, 0.2f, 0.22f, 0.6f * qa, 14)
      sb.fillOval(qx, qy, qr, qr * 0.86f, 0.72f, 0.72f, 0.74f, qa, 14)
      sb.fillOval(qx - qr * 0.2f, qy - qr * 0.25f, qr * 0.62f, qr * 0.45f, 0.9f, 0.9f, 0.92f, qa, 12)
      p -= 1
    }
    // Sparks of the powder spitting off it
    var s = 0
    while (s < 3) {
      val t = loop(tick, 0.11, s / 3.0 + proj.id * 0.29)
      val ang = Math.atan2(-ndy, -ndx) + hash2(proj.id + s, (tick + s * 3) / 6) * 1.1
      val d = R + t * 14f * ds
      sb.fillStarFlare(sx + Math.cos(ang).toFloat * d, sy + Math.sin(ang).toFloat * d, 4.5f * ds * (1f - t), 1.3f,
        ang.toFloat, 0.6f, 1f, 0.7f, 0.25f, (1f - t) * a)
      s += 1
    }
    // The ball: ink, iron, lit from the upper left
    sb.fillOval(sx, sy, R + 1.8f, R + 1.8f, 0.04f, 0.04f, 0.05f, 0.95f * a, 16)
    sb.fillOval(sx, sy, R, R, 0.24f, 0.24f, 0.28f, a, 16)
    sb.fillOvalSoft(sx - R * 0.25f, sy - R * 0.25f, R * 0.7f, R * 0.7f, 0.5f, 0.52f, 0.58f, 0.9f * a, 0f, 12)
    sb.fillOval(sx - R * 0.42f, sy - R * 0.42f, R * 0.26f, R * 0.2f, 1f, 1f, 1f, 0.9f * a, 8)
  }

  // ── The Pilot's biplane ──
  private val FUSELAGE = Array(-11f, -1f, -4f, -2.3f, 5f, -2.5f, 8.2f, -1.6f, 8.2f, 1.6f, 5f, 2.5f, -4f, 2.3f, -11f, 1f)
  private val WING_TOP = Array(0.6f, -12.5f, 5f, -12.5f, 5f, 12.5f, 0.6f, 12.5f)
  private val WING_LOW = Array(-1.4f, -10.5f, 2.4f, -10.5f, 2.4f, 10.5f, -1.4f, 10.5f)
  private val TAILPLANE = Array(-11.4f, -4.6f, -8f, -4.2f, -8f, 4.2f, -11.4f, 4.6f)
  private final val PLANE_ALT = 14f

  /**
   * The Pilot's strafing run: a red biplane, nose first and low over the ground, its propeller a blur,
   * roundels on its wings, banking as it goes, its shadow racing over the ground under it — where it
   * strikes — and contrails streaming off its wingtips.
   */
  private[projectiles] def drawBiplane(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 37) * 0.35
    computeAllDynamics(proj, 0.85f, 0.25f, 0.2f, phase)
    val a = dynAlpha
    val ds = Math.min(dynScale, 1.3f)
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val k = 1.35f * ds
    val bank = 0.82f + 0.18f * Math.cos(phase * 0.7).toFloat
    val alt = PLANE_ALT * ds + Math.sin(phase * 0.5).toFloat * 1.5f * ds

    // Its shadow, on the ground where it strikes
    headingFrame(sx, sy, ndx, ndy, k)
    sb.fillOvalSoft(sx, sy + 1f, 15f * k, 7f * k, 0f, 0f, 0f, 0.38f * a, 0f, 14)
    var n = pts(FUSELAGE); sb.fillPolygon(_gx, _gy, n, 0f, 0f, 0f, 0.28f * a)
    n = ptsAt(WING_TOP, 0f, 0f, 1f); sb.fillPolygon(_gx, _gy, n, 0f, 0f, 0f, 0.28f * a)
    // The plane, above it
    val cx = sx; val cy = sy - alt
    headingFrame(cx, cy, ndx, ndy, k)
    // Contrails off its wingtips
    var side = -1f
    while (side <= 1f) {
      fadeLine(sb, fx(2f, side * 12.5f * bank), fy(2f, side * 12.5f * bank), fx(-32f, side * 12f * bank), fy(-32f, side * 12f * bank),
        2.2f * ds, 1f, 1f, 1f, 0.6f * a, 4)
      side += 2f
    }
    val ir = 0.12f; val ig = 0.05f; val ib = 0.04f
    // The lower wing, behind and in shadow, then the tailplane and fuselage, then the upper wing
    n = ptsAt(WING_LOW, 0f, 0f, 1f)
    bankPts(n, bank, cx, cy)
    sb.strokePolygon(_gx, _gy, n, 2.2f, ir, ig, ib, 0.92f * a)
    sb.fillPolygon(_gx, _gy, n, 0.55f, 0.13f, 0.11f, a)
    n = pts(TAILPLANE)
    sb.strokePolygon(_gx, _gy, n, 2.2f, ir, ig, ib, 0.92f * a)
    sb.fillPolygon(_gx, _gy, n, 0.78f, 0.2f, 0.17f, a)
    n = pts(FUSELAGE)
    sb.strokePolygon(_gx, _gy, n, 2.4f, ir, ig, ib, 0.92f * a)
    sb.fillPolygon(_gx, _gy, n, 0.84f, 0.24f, 0.19f, a)
    gLine(sb, -10.5f, 0f, -4.5f, 0f, 1f, 0.5f, 0.1f, 0.08f, a)
    n = ptsAt(WING_TOP, 0f, 0f, 1f)
    bankPts(n, bank, cx, cy)
    sb.strokePolygon(_gx, _gy, n, 2.4f, ir, ig, ib, 0.92f * a)
    sb.fillPolygon(_gx, _gy, n, 0.9f, 0.27f, 0.21f, a)
    // A cream stripe down each wing, and the roundels on them
    side = -1f
    while (side <= 1f) {
      gLine(sb, 0.8f, side * 4f * bank, 4.8f, side * 4f * bank, 1.2f, 0.98f, 0.92f, 0.78f, a)
      val rx = fx(2.8f, side * 9f * bank); val ry = fy(2.8f, side * 9f * bank)
      sb.fillOval(rx, ry, 2.2f * k, 2.2f * k, 0.15f, 0.25f, 0.65f, a, 10)
      sb.fillOval(rx, ry, 1.5f * k, 1.5f * k, 0.98f, 0.95f, 0.9f, a, 10)
      sb.fillOval(rx, ry, 0.75f * k, 0.75f * k, 0.86f, 0.12f, 0.12f, a, 8)
      side += 2f
    }
    // The cockpit and the Pilot's cap in it
    gDot(sb, -2.6f, 0f, 2f, 1.6f, 0.1f, 0.08f, 0.1f, a, 10)
    gDot(sb, -2.8f, 0f, 1.2f, 1.1f, 0.48f, 0.32f, 0.18f, a, 8)
    // The propeller, a blur with its blades flickering round
    val pxp = fx(8.8f, 0f); val pyp = fy(8.8f, 0f)
    sb.fillOvalSoft(pxp, pyp, 5.6f * k, 5.6f * k, 0.85f, 0.85f, 0.85f, 0.5f * a, 0.15f * a, 14)
    val pa = tick * 1.7 + proj.id
    val bx = Math.cos(pa).toFloat * 5.2f * k; val by = Math.sin(pa).toFloat * 5.2f * k
    sb.strokeLine(pxp - bx, pyp - by * 0.5f, pxp + bx, pyp + by * 0.5f, 1.4f, 0.25f, 0.2f, 0.18f, 0.8f * a)
    gDot(sb, 9f, 0f, 1f, 1f, 0.3f, 0.25f, 0.22f, a, 8)
  }

  /** Bank the wing in _gx/_gy: draw it in toward the fuselage's line by `bank` across the heading. */
  private def bankPts(n: Int, bank: Float, cx: Float, cy: Float): Unit = {
    // The frame's across axis is (-_fs, _fc); a point's distance along it is scaled by the bank
    val ax = -_fs; val ay = _fc
    var i = 0
    while (i < n) {
      val dx = _gx(i) - cx; val dy = _gy(i) - cy
      val d = dx * ax + dy * ay
      _gx(i) -= ax * d * (1f - bank); _gy(i) -= ay * d * (1f - bank)
      i += 1
    }
  }

  // ── The Spaceman ──
  private final val ZAP_N = 12

  /**
   * The Spaceman's ray-gun zap: a capsule of cyan plasma with a star glinting at its point — the star
   * on his helmet — rings of energy rippling back down a corkscrew of blue and white, the old space
   * operas' ray. It grows with the charge. It was an orb in the player's colour, whatever that was.
   */
  private[projectiles] def drawRayZap(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 37) * 0.3
    computeAllDynamics(proj, 0.3f, 0.9f, 1f, phase)
    val a = dynAlpha
    val ds = dynScale
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val k = 1.35f * ds
    val hot = _chgBright

    sb.fillOvalSoft(sx - ndx * 8f * k, sy - ndy * 8f * k, 24f * ds * dynGlow, 16f * ds * dynGlow, 0.3f, 0.85f, 1f, 0.3f * a, 0f, 14)
    headingFrame(sx, sy, ndx, ndy, k)
    // The beam it leaves, and the corkscrew round it: behind first, then in front
    fadeLine(sb, fx(-12f, 0f), fy(-12f, 0f), fx(-58f, 0f), fy(-58f, 0f), 4f * k, 0.55f, 0.95f, 1f, 0.7f * a, 5)
    var pass = 0
    while (pass < 2) {
      var strand = 0
      while (strand < 2) {
        var i = 0
        while (i < ZAP_N) {
          val t = i.toFloat / (ZAP_N - 1)
          val ang = phase * 3.2 + strand * Math.PI - t * 11.0
          val s = Math.sin(ang).toFloat
          val lx = -10f - t * 46f; val ly = Math.cos(ang).toFloat * 4f * (1f - 0.3f * t)
          _pvX(i) = fx(lx, ly); _pvY(i) = fy(lx, ly) - s * 1.2f * k
          _pvW(i) = (2.6f * (1f - 0.7f * t)) * k
          _pvA(i) = (if ((s >= 0f) == (pass == 1)) 1f else 0.25f) * a * (1f - t)
          i += 1
        }
        if (strand == 0) sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, ZAP_N, 0.25f, 0.4f, 1f)
        else sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, ZAP_N, 0.82f, 1f, 1f)
        strand += 1
      }
      if (pass == 0) {
        // Rings rippling back down the beam
        var r = 0
        while (r < 3) {
          val t = loop(tick, 0.045, r / 3.0 + proj.id * 0.11)
          val lx = -12f - t * 42f
          val n = rotEllipsePts(fx(lx, 0f), fy(lx, 0f), ndx, ndy, (1.6f + t * 1.2f) * k, (5.5f + t * 4f) * k, 16)
          sb.strokePolygon(_gx, _gy, n, 2.6f * (1f - t) + 0.8f, 0.04f, 0.12f, 0.3f, 0.5f * (1f - t) * a)
          sb.strokePolygon(_gx, _gy, n, 1.6f * (1f - t) + 0.5f, 0.6f, 1f, 1f, 0.9f * (1f - t) * a)
          r += 1
        }
      }
      pass += 1
    }
    // The capsule: ink, cyan, its white-hot heart
    var n = ovalPts(-7f, 0f, 7.6f, 3.9f, 0f, 18)
    sb.strokePolygon(_gx, _gy, n, 2.4f, 0.04f, 0.12f, 0.3f, 0.92f * a)
    sb.fillPolygon(_gx, _gy, n, 0.3f, 0.82f, 1f, a)
    n = ovalPts(-6f, 0f, 5.4f, 2f, 0f, 14)
    sb.fillPolygon(_gx, _gy, n, mix(0.85f, 1f, hot), 1f, 1f, a)
    // The star glinting at its point
    val tw = 0.85f + 0.15f * Math.sin(phase * 2.2).toFloat
    sb.fillStarFlare(fx(-1.5f, 0f), fy(-1.5f, 0f), 9f * k * tw, 2f * k, (phase * 0.4).toFloat, 0.75f, 1f, 0.92f, 0.5f, a)
    drawChargeCrackle(sx, sy, 16f * ds, 0.3f, 0.9f, 1f, a, sb, phase, proj.chargeLevel)
  }

  // ── The Mech Pilot ──
  private val FIST = Array(-4f, -7f, 4f, -7.6f, 6f, -5.6f, 6.4f, 5.6f, 4f, 7.6f, -4f, 7f)
  private val FIST_SHADE = Array(-4f, 2.6f, 6.3f, 2.6f, 6.4f, 5.6f, 4f, 7.6f, -4f, 7f)
  private val THUMB = Array(-2.4f, -9.8f, 3f, -10.2f, 3.8f, -7.2f, -2.4f, -7f)
  private val FOREARM = Array(-13.4f, -4.8f, -4f, -5.2f, -4f, 5.2f, -13.4f, 4.8f)
  private val NOZZLE = Array(-17.2f, -6f, -13.4f, -3.8f, -13.4f, 3.8f, -17.2f, 6f)
  private val CUFF = Array(-7f, -5.4f, -4f, -5.6f, -4f, 5.6f, -7f, 5.4f)
  private val FIST_PARTS = Array(FIST, THUMB, FOREARM, NOZZLE)

  /**
   * The Mech Pilot's mech slam: the mech's own fist, fired off its arm — yellow armour, steel knuckles,
   * hazard stripes on its cuff, a power light in its wrist — on a roaring rocket, knuckles first.
   */
  private[projectiles] def drawRocketFist(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 37) * 0.35
    computeAllDynamics(proj, 1f, 0.75f, 0.15f, phase)
    val a = dynAlpha
    val ds = Math.min(dynScale, 1.3f)
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val px = -ndy; val py = ndx
    val k = 1.55f * ds
    val bx = sx - ndx * 6.4f * k; val by = sy - ndy * 6.4f * k

    // Smoke behind the flame
    var p = 0
    while (p < 3) {
      val t = loop(tick, 0.04, p / 3.0 + proj.id * 0.19)
      val lat = hash2(proj.id + 4, p) * 5f * ds
      val qx = bx - ndx * (30f * k + t * 30f * ds) + px * lat; val qy = by - ndy * (30f * k + t * 30f * ds) + py * lat - t * 6f * ds
      sb.fillOvalSoft(qx, qy, (5f + 7f * t) * ds, (4.4f + 6f * t) * ds, 0.5f, 0.5f, 0.52f, 0.45f * (1f - t) * a, 0f, 10)
      p += 1
    }
    sb.fillOvalSoft(bx - ndx * 18f * k, by - ndy * 18f * k, 26f * ds * dynGlow, 18f * ds * dynGlow, 1f, 0.6f, 0.2f, 0.3f * a, 0f, 14)
    headingFrame(bx, by, ndx, ndy, k)
    // The rocket's flame, roaring out of the nozzle
    val flick = 0.85f + 0.15f * Math.sin(phase * 5.3).toFloat
    var layer = 0
    while (layer < 3) {
      val len = (if (layer == 0) 22f else if (layer == 1) 16f else 9f) * flick
      val w = if (layer == 0) 9.6f else if (layer == 1) 7f else 3.8f
      var i = 0
      while (i < 5) {
        val t = i / 4f
        _pvX(i) = fx(-17.2f - len * t, 0f); _pvY(i) = fy(-17.2f - len * t, 0f)
        _pvW(i) = w * k * (1f - t * 0.9f); _pvA(i) = a * (1f - t * t * 0.4f)
        i += 1
      }
      (layer: @scala.annotation.switch) match {
        case 0 => sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, 5, 1f, 0.36f, 0.08f)
        case 1 => sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, 5, 1f, 0.75f, 0.2f)
        case _ => sb.strokePolylineVar(_pvX, _pvY, _pvW, _pvA, 5, 1f, 1f, 0.85f)
      }
      layer += 1
    }
    // The fist: inked as one, armour yellow and shaded, the forearm steel, the nozzle dark
    val ir = 0.1f; val ig = 0.08f; val ib = 0.04f
    inkAll(sb, FIST_PARTS, 2.8f, ir, ig, ib, 0.95f * a)
    var n = pts(NOZZLE); sb.fillPolygon(_gx, _gy, n, 0.3f, 0.32f, 0.36f, a)
    n = pts(FOREARM); sb.fillPolygon(_gx, _gy, n, 0.5f, 0.53f, 0.58f, a)
    gLine(sb, -13f, -2.4f, -7f, -2.6f, 1.2f, 0.75f, 0.78f, 0.84f, a)
    n = pts(CUFF); sb.fillPolygon(_gx, _gy, n, 0.98f, 0.8f, 0.15f, a)
    var st = 0
    while (st < 3) {
      val y0 = -4.8f + st * 3.6f
      gLine(sb, -6.6f, y0, -4.4f, y0 + 2.2f, 1f, 0.08f, 0.06f, 0.04f, a)
      st += 1
    }
    n = pts(THUMB); sb.fillPolygon(_gx, _gy, n, 0.95f, 0.76f, 0.14f, a)
    n = pts(FIST); sb.fillPolygon(_gx, _gy, n, 0.98f, 0.8f, 0.16f, a)
    n = pts(FIST_SHADE); sb.fillPolygon(_gx, _gy, n, 0.82f, 0.6f, 0.08f, a)
    // The fingers' grooves and the steel plates on the knuckles
    var f = 0
    while (f < 3) {
      val y = -3.8f + f * 3.8f
      gLine(sb, 1.6f, y, 6.2f, y, 0.9f, 0.3f, 0.2f, 0.04f, a)
      f += 1
    }
    f = 0
    while (f < 4) {
      val y = -5.7f + f * 3.8f
      gDot(sb, 5.2f, y, 1f, 1.3f, 0.78f, 0.8f, 0.86f, a, 6)
      f += 1
    }
    gDot(sb, -2f, -4.6f, 2.4f, 0.9f, 1f, 0.97f, 0.8f, 0.8f * a, 8)
    // The power light in its wrist
    sb.fillOvalSoft(fx(-9.6f, 0f), fy(-9.6f, 0f), 3.6f * k, 3.6f * k, 0.3f, 0.95f, 1f, 0.7f * a, 0f, 10)
    gDot(sb, -9.6f, 0f, 1f, 1f, 0.8f, 1f, 1f, a, 6)
  }

  // ── The Graviton ──
  private val CUBE_V = Array(-1f, -1f, -1f, 1f, -1f, -1f, 1f, 1f, -1f, -1f, 1f, -1f,
    -1f, -1f, 1f, 1f, -1f, 1f, 1f, 1f, 1f, -1f, 1f, 1f)
  // Faces as four vertex indices each, wound the same way seen from outside
  private val CUBE_F = Array(0, 3, 2, 1, 4, 5, 6, 7, 0, 1, 5, 4, 2, 3, 7, 6, 1, 2, 6, 5, 0, 4, 7, 3)
  private val _cvX = new Array[Float](8)
  private val _cvY = new Array[Float](8)
  private val _cvZ = new Array[Float](8)

  /** A cube of side 2s at (cx, cy) on screen, turned about x by `ax` and about y by `ay`, its faces lit
    * from the upper left: only those facing us are drawn, which on a cube never overlap. */
  private def cube(sb: ShapeBatch, cx: Float, cy: Float, s: Float, ax: Double, ay: Double,
                   r: Float, g: Float, b: Float, a: Float): Unit = {
    val cxr = Math.cos(ax).toFloat; val sxr = Math.sin(ax).toFloat
    val cyr = Math.cos(ay).toFloat; val syr = Math.sin(ay).toFloat
    var v = 0
    while (v < 8) {
      val x0 = CUBE_V(v * 3); val y0 = CUBE_V(v * 3 + 1); val z0 = CUBE_V(v * 3 + 2)
      val y1 = y0 * cxr - z0 * sxr; val z1 = y0 * sxr + z0 * cxr
      val x2 = x0 * cyr + z1 * syr; val z2 = -x0 * syr + z1 * cyr
      _cvX(v) = cx + x2 * s; _cvY(v) = cy + y1 * s; _cvZ(v) = z2
      v += 1
    }
    var f = 0
    while (f < 6) {
      val i0 = CUBE_F(f * 4); val i1 = CUBE_F(f * 4 + 1); val i2 = CUBE_F(f * 4 + 2); val i3 = CUBE_F(f * 4 + 3)
      // The face's normal toward us: the screen winding of its corners
      val ex = _cvX(i1) - _cvX(i0); val ey = _cvY(i1) - _cvY(i0)
      val fx2 = _cvX(i2) - _cvX(i0); val fy2 = _cvY(i2) - _cvY(i0)
      val cross = ex * fy2 - ey * fx2
      if (cross < 0f) {
        // Lit by how far up and left it faces
        val mz = (_cvZ(i0) + _cvZ(i2)) * 0.5f
        val my = (_cvY(i0) + _cvY(i2)) * 0.5f - cy; val mx = (_cvX(i0) + _cvX(i2)) * 0.5f - cx
        // (z runs into the screen: a face nearer us is lit a little more)
        val lit = clampF(0.62f + (-mx * 0.4f - my * 0.6f) / (s + 1e-3f) * 0.32f - mz * 0.12f, 0.35f, 1.1f)
        _polyXs4(0) = _cvX(i0); _polyYs4(0) = _cvY(i0)
        _polyXs4(1) = _cvX(i1); _polyYs4(1) = _cvY(i1)
        _polyXs4(2) = _cvX(i2); _polyYs4(2) = _cvY(i2)
        _polyXs4(3) = _cvX(i3); _polyYs4(3) = _cvY(i3)
        sb.strokePolygon(_polyXs4, _polyYs4, 4, 2f, r * 0.15f, g * 0.12f, b * 0.2f, 0.9f * a)
        sb.fillPolygon(_polyXs4, _polyYs4, 4, Math.min(1f, r * lit), Math.min(1f, g * lit), Math.min(1f, b * lit), a)
      }
      f += 1
    }
  }

  /**
   * The Graviton's gravity ball: the cubes that float round the Graviton, tumbling on their orbits round
   * a pinprick of a singularity — black, ringed in violet light, space bending round it in arcs.
   */
  private[projectiles] def drawGravityCubes(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 37) * 0.3
    computeAllDynamics(proj, 0.6f, 0.35f, 0.95f, phase)
    val a = dynAlpha
    val ds = Math.min(dynScale, 1.3f)
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val orb = 18f * ds

    Orbs.cometTail(sb, sx, sy, ndx, ndy, 40f * ds, 16f * ds, 0.55f, 0.35f, 0.95f, 0.3f * a)
    sb.fillOvalSoft(sx, sy, 26f * ds * dynGlow, 22f * ds * dynGlow, 0.55f, 0.3f, 0.95f, 0.28f * a, 0f, 16)
    // Space bending round it
    var arc = 0
    while (arc < 2) {
      val st = (phase * (if (arc == 0) 0.4 else -0.55) + arc * 2.0).toFloat
      sb.strokeArc(sx, sy, (19f + arc * 4f) * ds, (15f + arc * 3f) * ds, st, 2.2f, 1.6f * ds, 0.75f, 0.6f, 1f, 0.55f * a, 10)
      arc += 1
    }
    // The cubes behind it, the singularity, the cubes in front
    var pass = 0
    while (pass < 2) {
      var c = 0
      while (c < 4) {
        val th = phase * 0.9 + c * (Math.PI / 2)
        val behind = Math.sin(th) < 0
        if (behind == (pass == 0)) {
          val cx = sx + Math.cos(th).toFloat * orb; val cy = sy + Math.sin(th).toFloat * orb * 0.38f - 1f * ds
          val sz = (4.4f + 0.6f * Math.sin(th).toFloat) * ds
          (c: @scala.annotation.switch) match {
            case 0 => cube(sb, cx, cy, sz, phase * 1.1 + c, phase * 1.4 + c * 0.7, 0.95f, 0.4f, 0.8f, a)
            case 1 => cube(sb, cx, cy, sz, phase * 1.3 + c, phase * 0.9 + c * 0.7, 0.6f, 0.42f, 1f, a)
            case 2 => cube(sb, cx, cy, sz, phase * 0.8 + c, phase * 1.2 + c * 0.7, 0.8f, 0.7f, 1f, a)
            case _ => cube(sb, cx, cy, sz, phase * 1.2 + c, phase * 1.0 + c * 0.7, 0.4f, 0.75f, 1f, a)
          }
        }
        c += 1
      }
      if (pass == 0) {
        // The singularity: black, a violet rim of light, a ring of bent light round it
        sb.fillOval(sx, sy, 7.4f * ds, 7.4f * ds, 0.03f, 0.01f, 0.06f, a, 18)
        sb.strokeOval(sx, sy, 7.4f * ds, 7.4f * ds, 2.4f * ds, 0.75f, 0.45f, 1f, a, 18)
        sb.strokeOval(sx, sy, 10.6f * ds, 10.6f * ds, 1.1f * ds, 0.9f, 0.8f, 1f, 0.6f * a, 20)
        sb.fillOval(sx - 2.6f * ds, sy - 2.6f * ds, 1.4f * ds, 1.1f * ds, 0.85f, 0.75f, 1f, 0.8f * a, 8)
      }
      pass += 1
    }
  }
}
