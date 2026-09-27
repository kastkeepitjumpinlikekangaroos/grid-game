package com.gridgame.client.render.projectiles

import com.gridgame.common.model.Projectile
import com.gridgame.client.gl.ShapeBatch
import ProjectileKit._
import Silhouettes._
import Orbs._

/** Magic bolts with a shape of their own, rather than an orb's style: the void, gravity, data, holy
  * light, a curse, a web, venom, a shadow — and the Spaceman's charge shot (drawNormal). */
private[render] object Bolts {
  /** Spaceman's charge shot: a plasma orb in the player's colour — the orbs' lit sphere and comet
   *  tail (see energyBolt) — with energy rings spinning round it. It used to be a wobbling 13px
   *  tube running seven world units ahead of the hitbox to an orb, the roster's most visible
   *  snake; then a string of beads behind a disc, which was a caterpillar. */
  private[projectiles] def drawNormal(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
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

  /** Data Bolt - digital matrix aesthetic with pixelated structure */
  private[projectiles] def drawDataBolt(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int,
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
  private[projectiles] def drawHolyBolt(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
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

  // Holy star: 6-point star (12 vertices)
  private val _holyXs = new Array[Float](12)
  private val _holyYs = new Array[Float](12)

  /** Curse - dark spiraling occult energy with orbiting rune-like marks */
  private[projectiles] def drawCurse(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
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
  private[projectiles] def drawWebShot(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
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

  /** Shadow Bolt - dark void mass with 8 varied tendrils, void ripples, swirling core and glowing eyes */
  private[projectiles] def drawShadowBolt(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
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
}
