package com.gridgame.client.render.projectiles

import com.gridgame.common.model.Projectile
import com.gridgame.client.gl.ShapeBatch
import ProjectileKit._
import Silhouettes._

/** The dead and the dark: a bat swarm, the dead raised, the banshee's wail, the ghoul's devour, the
  * reaper's scythe and reap. */
private[render] object Undead {
  /** Bat Swarm - dark cloud with varied-size bats, scattered formation, red eye trails */
  private[projectiles] def drawBatSwarm(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
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
  private[projectiles] def drawRaiseDead(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
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
  private[projectiles] def drawWail(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
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
  private[projectiles] def drawDevour(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
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
  private[projectiles] def drawScythe(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
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

  // Scythe: segs=12 → arc (26)
  private val _scyXs = new Array[Float](26)
  private val _scyYs = new Array[Float](26)

  /** Reap - massive death arc */
  private[projectiles] def drawReap(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
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
}
