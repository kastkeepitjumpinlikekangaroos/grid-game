package com.gridgame.client.render.projectiles

import com.gridgame.common.model.Projectile
import com.gridgame.client.gl.ShapeBatch
import ProjectileKit._
import Silhouettes._
import Orbs._

/** Magic bolts with a shape of their own, rather than an orb's style: the void, gravity, data, holy
  * light and venom (the acid bomb's). */
private[render] object Bolts {
  /** Void Bolt - swirling dark vortex with reality-distortion ripple rings */
  private[projectiles] def drawVoidBolt(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
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
  private[projectiles] def drawGravityBall(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
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

  // The data bolt's arrow and the virus's spiked cell, in pixels: (along, across) of each, in steps
  private val DATA_PIX = Array(1f, 0f, 2f, 0f, 1f, 1f, 1f, -1f, 0f, 2f, 0f, -2f, 0f, 0f, 0f, 1f, 0f, -1f, -1f, 0f, -2f, 0f, -3f, 0f)
  private val VIRUS_PIX = Array(0f, 0f, 1f, 0f, -1f, 0f, 0f, 1f, 0f, -1f, 1.75f, 1.75f, -1.75f, 1.75f, 1.75f, -1.75f, -1.75f, -1.75f)

  /**
   * The Hacker's data bolt and virus, in pixels: the bolt an arrow of twelve of them pointing the way it
   * flies, the virus a cell with four spikes that turns as it goes and tears sideways a row at a time.
   * Square to the screen whatever the heading, inked as one shape, with more of them left behind and
   * going out. They were a haze with a blob in it and thirty dim specks round that.
   */
  private[projectiles] def drawDataBolt(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int,
                           isVirus: Boolean = false): Unit = {
    val phase = (tick + proj.id * 31) * 0.4
    val mainR = if (isVirus) 0.95f else 0.16f
    val mainG = if (isVirus) 0.22f else 0.92f
    val mainB = if (isVirus) 0.2f else 0.6f
    computeAllDynamics(proj, mainR, mainG, mainB, phase)
    val a = dynAlpha
    val ds = Math.min(dynScale, 1.3f)
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val step = (if (isVirus) 5.6f else 5f) * ds
    val hw = step * 0.5f
    val pix = if (isVirus) VIRUS_PIX else DATA_PIX
    val n = pix.length / 2
    // The virus turns; the arrow points
    val rot = if (isVirus) tick * 0.09f + proj.id else 0f
    val fxx = if (isVirus) Math.cos(rot).toFloat else ndx
    val fyy = if (isVirus) Math.sin(rot).toFloat else ndy
    // One row of it torn sideways for a few ticks at a time
    val torn = if (isVirus) ((tick / 3 + proj.id) % 4) - 1 else ((tick / 4 + proj.id) % 9) - 2
    val inkR = mainR * 0.14f; val inkG = mainG * 0.14f; val inkB = mainB * 0.14f + 0.03f

    // Left behind: pixels going out, each a step further back, jumping from place to place
    var t = 0
    while (t < 5) {
      val back = (2.6f + t * 1.25f) * step
      val lat = hash2(proj.id + t * 7, tick / 3 + t) * step * 1.1f
      val x = sx - ndx * back - ndy * lat; val y = sy - ndy * back + ndx * lat
      val h = hw * (0.78f - t * 0.11f)
      val fa = (1f - t / 5f) * a
      sb.fillRect(x - h - 1f, y - h - 1f, 2f * h + 2f, 2f * h + 2f, inkR, inkG, inkB, 0.85f * fa)
      sb.fillRect(x - h, y - h, 2f * h, 2f * h, mainR, mainG, mainB, fa)
      t += 1
    }
    // Each pixel a square turned with the shape: square to the screen instead, an arrow flying on a
    // diagonal was a scatter of boxes
    var pass = 0
    while (pass < 3) {
      var i = 0
      while (i < n) {
        val al = pix(i * 2) + (if (Math.round(pix(i * 2 + 1)) == torn) 0.5f else 0f); val ac = pix(i * 2 + 1)
        val x = sx + (fxx * al - fyy * ac) * step
        val y = sy + (fyy * al + fxx * ac) * step
        val lit = pass == 2
        val h = if (pass == 0) hw + 1.5f else if (lit) hw * 0.5f else hw
        // The lit corner of each sits toward the top of the screen; the middle one is lit through
        val whole = i == 0 || (!isVirus && i == 1)
        val ox = if (lit && !whole) -hw * 0.4f else 0f
        val ux = fxx * h; val uy = fyy * h
        val cx = x + (if (lit && !whole) 0f else 0f); val cy = y + (if (lit && !whole) -hw * 0.38f else 0f) + ox * 0f
        _polyXs4(0) = cx + ux - uy; _polyYs4(0) = cy + uy + ux
        _polyXs4(1) = cx - ux - uy; _polyYs4(1) = cy - uy + ux
        _polyXs4(2) = cx - ux + uy; _polyYs4(2) = cy - uy - ux
        _polyXs4(3) = cx + ux + uy; _polyYs4(3) = cy + uy - ux
        if (pass == 0) sb.fillPolygon(_polyXs4, _polyYs4, 4, inkR, inkG, inkB, 0.94f * a)
        else if (pass == 1) sb.fillPolygon(_polyXs4, _polyYs4, 4, mainR, mainG, mainB, a)
        else {
          val w = if (whole) 0.85f else 0.45f
          sb.fillPolygon(_polyXs4, _polyYs4, 4, mix(mainR, 1f, w), mix(mainG, 1f, w), mix(mainB, 1f, w), a)
        }
        i += 1
      }
      pass += 1
    }
    drawChargeCrackle(sx, sy, 14f * ds, mainR, mainG, mainB, a, sb, phase, proj.chargeLevel)
  }

  /**
   * The holy bolt, smite and judgment: a shooting star. An eight-pointed star of gold, upright and
   * rocking a little, under a halo, drawing a tail of its light behind it and shedding glints of it.
   * Inked and solid: it was a pale star in a cone of haze with a ring, rays and a cross of thin light
   * through it, which over sand was a smudge.
   */
  private[projectiles] def drawHolyBolt(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 29) * 0.3
    computeAllDynamics(proj, 1f, 0.9f, 0.45f, phase)
    val p = dynAlpha
    val ds = Math.min(dynScale, 1.3f)
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val gx = Glyphs._gx; val gy = Glyphs._gy
    val inkR = 0.42f; val inkG = 0.26f; val inkB = 0.04f

    // Glints shed behind it, each a small four-pointed star going out
    var i = 0
    while (i < 4) {
      val t = Glyphs.loop(tick, 0.05, i * 0.25 + proj.id * 0.13)
      val lat = hash2(proj.id + 5, i) * 11f * ds
      val x = sx - ndx * (20f + t * 44f) * ds - ndy * lat; val y = sy - ndy * (20f + t * 44f) * ds + ndx * lat - t * 6f * ds
      val r = (4.2f * (1f - t) + 0.8f) * ds
      _polyXs4(0) = x + r; _polyYs4(0) = y; _polyXs4(1) = x; _polyYs4(1) = y + r * 0.45f
      _polyXs4(2) = x - r; _polyYs4(2) = y; _polyXs4(3) = x; _polyYs4(3) = y - r * 0.45f
      sb.fillPolygon(_polyXs4, _polyYs4, 4, 1f, 0.95f, 0.62f, (1f - t) * p)
      _polyXs4(0) = x + r * 0.45f; _polyYs4(0) = y; _polyXs4(1) = x; _polyYs4(1) = y + r
      _polyXs4(2) = x - r * 0.45f; _polyYs4(2) = y; _polyXs4(3) = x; _polyYs4(3) = y - r
      sb.fillPolygon(_polyXs4, _polyYs4, 4, 1f, 0.95f, 0.62f, (1f - t) * p)
      i += 1
    }
    // The tail of its light: gold round pale gold round white
    val len = 50f * ds * dynTrail
    var n = Glyphs.flamePts(sx, sy, 9.5f * ds, -ndx, -ndy, len, len, 0f, 2f * ds, phase * 2.0, 10, 0.3f)
    sb.strokePolygon(gx, gy, n, 2.4f, inkR, inkG, inkB, 0.9f * p)
    sb.fillRibbon(gx, gy, n, 1f, 0.78f, 0.24f, p)
    n = Glyphs.flamePts(sx, sy, 6f * ds, -ndx, -ndy, len * 0.66f, len, 0f, 2f * ds, phase * 2.0, 8, 0.3f)
    sb.fillRibbon(gx, gy, n, 1f, 0.93f, 0.6f, p)
    n = Glyphs.flamePts(sx, sy, 3f * ds, -ndx, -ndy, len * 0.36f, len, 0f, 2f * ds, phase * 2.0, 6, 0.3f)
    sb.fillRibbon(gx, gy, n, 1f, 1f, 0.92f, p)

    // The star: four long points and four short between them
    val rock = Math.sin(phase * 0.8).toFloat * 0.2f
    val beat = 1f + 0.05f * Math.sin(phase * 2.2).toFloat
    var layer = 0
    while (layer < 2) {
      val k = (if (layer == 0) 1f else 0.58f) * ds * beat
      i = 0
      while (i < 16) {
        val ang = rock + i * Math.PI / 8 - Math.PI / 2
        val rad = (if ((i & 1) == 1) 6.6f else if ((i & 3) == 0) 20f else 12.5f) * k
        gx(i) = sx + Math.cos(ang).toFloat * rad; gy(i) = sy + Math.sin(ang).toFloat * rad
        i += 1
      }
      if (layer == 0) {
        sb.strokePolygon(gx, gy, 16, 3f, inkR, inkG, inkB, 0.94f * p)
        sb.fillFan(sx, sy, gx, gy, 16, 1f, 0.8f, 0.26f, p)
      } else sb.fillFan(sx, sy, gx, gy, 16, 1f, 0.95f, 0.66f, p)
      layer += 1
    }
    sb.fillOval(sx, sy, 4.4f * ds, 4.4f * ds, 1f, 1f, 0.95f, p, 12)
    // Its halo, over it
    val hy = sy - 25f * ds + Math.sin(phase * 1.3).toFloat * 1.2f * ds
    sb.strokeOval(sx, hy, 10f * ds, 3.4f * ds, 4.2f, inkR, inkG, inkB, 0.92f * p, 16)
    sb.strokeOval(sx, hy, 10f * ds, 3.4f * ds, 2f, 1f, 0.9f, 0.45f, p, 16)

    drawChargeCrackle(sx, sy, 22f * ds, 1f, 0.9f, 0.45f, p, sb, phase, proj.chargeLevel)
    drawReturnGhosts(sx, sy, 22f * ds, 1f, 0.8f, 0.26f, p, sb, proj)
  }


  /** Venom Bolt - dripping toxic blob with bubbles */
  private[projectiles] def drawVenomBolt(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
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
}
