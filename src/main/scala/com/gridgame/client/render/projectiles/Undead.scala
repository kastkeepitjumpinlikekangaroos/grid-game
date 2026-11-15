package com.gridgame.client.render.projectiles

import com.gridgame.common.model.Projectile
import com.gridgame.client.gl.ShapeBatch
import ProjectileKit._
import Silhouettes._

/** The dead and the dark: the Vampire's bats, the dead raised, the banshee's wail, the Ghoul's head, the
  * Reaper's scythe and its reap. */
private[render] object Undead {
  // Ink for the dead and the dark
  private final val INK_R = 0.06f; private final val INK_G = 0.03f; private final val INK_B = 0.08f

  /**
   * The Vampire's bat swarm: five bats flying the way it goes, a big one at their head on the hitbox
   * and the rest in a ragged V behind it, each beating its wings in its own time, plum bodies on
   * crimson wings, red eyes. It used to be seven near-black specks circling a dark smudge whichever
   * way it flew, their wings two triangles flapping faster than the eye could follow.
   */
  private[projectiles] def drawBatSwarm(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 31) * 0.4
    computeAllDynamics(proj, 0.75f, 0.12f, 0.22f, phase)
    val a = dynAlpha
    val ds = Math.min(dynScale, 1.3f)
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val px = -ndy; val py = ndx
    // A dusk behind them, so the dark of them holds on dark ground
    sb.fillOvalSoft(sx - ndx * 14f * ds, sy - ndy * 14f * ds, 36f * ds * dynGlow, 24f * ds * dynGlow,
      0.7f, 0.1f, 0.2f, 0.2f * a, 0f, 14)
    var i = 4
    while (i >= 0) {
      val rank = (i + 1) / 2
      val side = if (i == 0) 0f else if ((i & 1) == 1) 1f else -1f
      val back = (rank * 13f + hash2(proj.id, i) * 2.5f) * ds
      val bob = Math.sin(phase * 0.5 + i * 1.9).toFloat
      val lat = (side * rank * 10f + bob * 1.5f) * ds
      val s = (if (i == 0) 1.25f else if (rank == 1) 0.95f else 0.8f) * ds
      bat(sb, sx - ndx * back + px * lat, sy - ndy * back + py * lat - bob * 2f * ds, s,
        Math.sin(phase * 0.8 + i * 1.7).toFloat, a)
      i -= 1
    }
    drawChargeCrackle(sx, sy, 20f * ds, 0.75f, 0.12f, 0.22f, a, sb, phase, proj.chargeLevel)
  }

  /** One bat, facing us whichever way the swarm flies (a bat seen from above is a moth): its wings
    * out either side, each two spans of membrane scalloped between its fingers, drawn in as they
    * beat (`flap`, -1 to 1), its ears up. */
  private def bat(sb: ShapeBatch, bx: Float, by: Float, s: Float, flap: Float, a: Float): Unit = {
    val ndx = 0f; val ndy = -1f
    val px = 1f; val py = 0f
    val span = (11f + 4.5f * flap) * s
    val lift = flap * 2.5f * s
    val shx = bx + ndx * 2.5f * s; val shy = by + ndy * 2.5f * s
    val hpx = bx - ndx * 3.5f * s; val hpy = by - ndy * 3.5f * s
    var w = 0
    while (w < 2) {
      val sg = if (w == 0) 1f else -1f
      val tipX = bx + px * sg * span + ndx * 1.5f * s; val tipY = by + py * sg * span + ndy * 1.5f * s - lift
      val midX = bx + px * sg * span * 0.56f - ndx * 5f * s; val midY = by + py * sg * span * 0.56f - ndy * 5f * s - lift * 0.4f
      _polyXs3(0) = shx; _polyYs3(0) = shy; _polyXs3(1) = tipX; _polyYs3(1) = tipY; _polyXs3(2) = midX; _polyYs3(2) = midY
      _polyXs4(0) = shx; _polyYs4(0) = shy; _polyXs4(1) = midX; _polyYs4(1) = midY; _polyXs4(2) = hpx; _polyYs4(2) = hpy
      sb.strokePolygon(_polyXs3, _polyYs3, 3, 1.8f, INK_R, INK_G, INK_B, 0.9f * a)
      sb.strokePolygon(_polyXs4, _polyYs4, 3, 1.8f, INK_R, INK_G, INK_B, 0.9f * a)
      sb.fillPolygon(_polyXs3, _polyYs3, 3, 0.62f, 0.15f, 0.30f, a)
      sb.fillPolygon(_polyXs4, _polyYs4, 3, 0.47f, 0.10f, 0.26f, a)
      // The finger the membrane hangs from
      sb.strokeLine(shx, shy, tipX, tipY, 1.1f * s, 0.24f, 0.07f, 0.2f, a)
      w += 1
    }
    sb.fillOval(bx, by, 3.2f * s + 1f, 3.8f * s + 1f, INK_R, INK_G, INK_B, 0.9f * a, 10)
    sb.fillOval(bx, by, 3.2f * s, 3.8f * s, 0.24f, 0.11f, 0.30f, a, 10)
    val hx = bx + ndx * 4.2f * s; val hy = by + ndy * 4.2f * s
    // Ears up off the head, then the head over their roots
    var e = 0
    while (e < 2) {
      val ex = hx + (e * 2 - 1) * 1.9f * s
      _polyXs3(0) = ex - 1.3f * s; _polyYs3(0) = hy - 1f * s
      _polyXs3(1) = ex + 1.3f * s; _polyYs3(1) = hy - 1f * s
      _polyXs3(2) = ex + (e * 2 - 1) * 0.6f * s; _polyYs3(2) = hy - 5.2f * s
      sb.fillPolygon(_polyXs3, _polyYs3, 3, 0.12f, 0.05f, 0.16f, a)
      e += 1
    }
    sb.fillOval(hx, hy, 2.8f * s + 1f, 2.6f * s + 1f, INK_R, INK_G, INK_B, 0.9f * a, 10)
    sb.fillOval(hx, hy, 2.8f * s, 2.6f * s, 0.26f, 0.12f, 0.32f, a, 10)
    sb.fillOval(hx - 1.2f * s, hy - 0.2f * s, 0.95f * s, 0.95f * s, 1f, 0.3f, 0.22f, a, 6)
    sb.fillOval(hx + 1.2f * s, hy - 0.2f * s, 0.95f * s, 0.95f * s, 1f, 0.3f, 0.22f, a, 6)
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

  // The Ghoul's head in two pieces, side on, each hinged at (-1, 0) with its teeth along y = 0
  private val DEV_UP = Array(-1.05f,0f, -1f,-0.55f, -0.55f,-0.95f, 0.15f,-0.98f, 0.75f,-0.62f, 0.98f,-0.12f, 0.9f,0f)
  private val DEV_LO = Array(-1.05f,0f, 0.85f,0f, 0.92f,0.14f, 0.6f,0.5f, -0.1f,0.62f, -0.8f,0.42f)
  private var _swc = 1f; private var _sws = 0f
  private var _swx = 0f; private var _swy = 0f

  /** A point of a jaw swung about its hinge (by _swc/_sws), through the frame, into (_swx, _swy). */
  @inline private def swung(lx: Float, ly: Float): Unit = {
    val hx = lx + 1f
    val wx = hx * _swc - ly * _sws - 1f; val wy = hx * _sws + ly * _swc
    _swx = Glyphs.fx(wx, wy); _swy = Glyphs.fy(wx, wy)
  }

  private def swungPts(local: Array[Float]): Int = {
    val n = local.length / 2
    var i = 0
    while (i < n) { swung(local(i * 2), local(i * 2 + 1)); Glyphs._gx(i) = _swx; Glyphs._gy(i) = _swy; i += 1 }
    n
  }

  private def swungTri(x0: Float, y0: Float, x1: Float, y1: Float, x2: Float, y2: Float): Unit = {
    swung(x0, y0); _polyXs3(0) = _swx; _polyYs3(0) = _swy
    swung(x1, y1); _polyXs3(1) = _swx; _polyYs3(1) = _swy
    swung(x2, y2); _polyXs3(2) = _swx; _polyYs3(2) = _swy
  }

  /**
   * The Ghoul's Devour: its own head flying jaws first, side on, the top of it up whichever way it
   * flies — green skin, the yellow eye with its red pupil, a ragged ear, crooked yellow teeth round a
   * dark mouth with its tongue out — chewing as it comes and snapping shut on the hitbox, ichor
   * slobbered off behind it. It used to be two maroon dentures one above the other whichever way it
   * flew, in a dark haze with tendrils: a hamburger.
   */
  private[projectiles] def drawDevour(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 23) * 0.45
    computeAllDynamics(proj, 0.5f, 0.8f, 0.4f, phase)
    val a = dynAlpha
    val ds = Math.min(dynScale, 1.3f)
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val px = -ndy; val py = ndx
    val s = 17f * ds
    // Chewing as it comes, shut over the last of its reach
    val shut = clampF((_lifePct - 0.7f) / 0.25f, 0f, 1f)
    val open = (0.14f + 0.4f * (0.5f + 0.5f * Math.sin(phase * 0.7).toFloat)) * (1f - shut * shut)
    val m = if (ndx < -0.05f) -1f else 1f
    Glyphs.frame(sx - ndx * 0.8f * s, sy - ndy * 0.8f * s, Math.atan2(m * ndy, m * ndx).toFloat, s, m)

    sb.fillOvalSoft(sx - ndx * 0.8f * s, sy - ndy * 0.8f * s, 2.3f * s * dynGlow, 1.8f * s * dynGlow,
      0.35f, 0.6f, 0.25f, 0.24f * a, 0f, 16)
    // Ichor slobbered off behind it
    var i = 0
    while (i < 6) {
      val t = Glyphs.loop(tick, 0.045, i / 6.0 + proj.id * 0.13)
      val lat = hash2(proj.id + 5, i) * 0.7f * s
      val bx = sx - ndx * (1.7f * s + t * 2.6f * s) + px * lat
      val by = sy - ndy * (1.7f * s + t * 2.6f * s) + py * lat + t * t * 0.8f * s
      val rr = (0.11f + (1f - t) * 0.1f) * s
      sb.fillOval(bx, by, rr + 0.8f, rr * 1.3f + 0.8f, 0.08f, 0.2f, 0.05f, 0.75f * (1f - t) * a, 8)
      sb.fillOval(bx, by, rr, rr * 1.3f, 0.62f, 0.9f, 0.3f, (1f - t) * a, 8)
      i += 1
    }
    val uc = Math.cos(-open).toFloat; val us = Math.sin(-open).toFloat
    val lc = Math.cos(open * 0.8f).toFloat; val ls = Math.sin(open * 0.8f).toFloat
    // The dark of the mouth, and the tongue in it
    _swc = uc; _sws = us; swung(0.9f, 0f); _polyXs3(1) = _swx; _polyYs3(1) = _swy
    _swc = lc; _sws = ls; swung(0.85f, 0f); _polyXs3(2) = _swx; _polyYs3(2) = _swy
    _polyXs3(0) = Glyphs.fx(-1f, 0f); _polyYs3(0) = Glyphs.fy(-1f, 0f)
    sb.fillPolygon(_polyXs3, _polyYs3, 3, 0.2f, 0.03f, 0.1f, 0.96f * a)
    if (open > 0.12f) {
      swungTri(-0.5f, -0.02f, 0.5f, -0.03f, 0.3f, -0.2f * open / 0.54f - 0.04f)
      sb.fillPolygon(_polyXs3, _polyYs3, 3, 0.95f, 0.5f, 0.6f, a)
    }
    // Each jaw: its teeth, then itself over their roots
    var jaw = 0
    while (jaw < 2) {
      val upper = jaw == 1
      if (upper) { _swc = uc; _sws = us } else { _swc = lc; _sws = ls }
      val sg = if (upper) 1f else -1f
      var k = 0
      while (k < 5) {
        val x = (if (upper) -0.22f else -0.1f) + k * 0.24f
        val len = (0.2f + 0.16f * Glyphs.hash01(k * 7 + jaw, 3)) * sg
        swungTri(x - 0.11f, 0f, x + 0.11f, 0f, x + 0.04f * sg, len)
        sb.strokePolygon(_polyXs3, _polyYs3, 3, 1.6f, 0.1f, 0.12f, 0.04f, 0.9f * a)
        sb.fillPolygon(_polyXs3, _polyYs3, 3, 0.99f, 0.95f, 0.72f, a)
        k += 1
      }
      if (upper) {
        // Its ear, behind the head
        swungTri(-0.92f, -0.45f, -0.55f, -0.88f, -1.2f, -1.4f)
        sb.strokePolygon(_polyXs3, _polyYs3, 3, 0.1f * s + 1f, 0.06f, 0.14f, 0.07f, 0.95f * a)
        sb.fillPolygon(_polyXs3, _polyYs3, 3, 0.4f, 0.58f, 0.38f, a)
      }
      val n = swungPts(if (upper) DEV_UP else DEV_LO)
      val sh = if (upper) 1f else 0.86f
      Glyphs.inked(sb, n, 0.12f * s + 1.2f, 0.06f, 0.14f, 0.07f, 0.95f * a, 0.5f * sh, 0.7f * sh, 0.46f * sh, a)
      jaw += 1
    }
    // On the head: a tuft, the eye, a nostril
    swungTri(-0.4f, -0.9f, -0.12f, -0.96f, -0.42f, -1.3f)
    sb.fillPolygon(_polyXs3, _polyYs3, 3, 0.16f, 0.22f, 0.16f, a)
    swung(0.26f, -0.5f)
    sb.fillOval(_swx, _swy, 0.27f * s + 1f, 0.27f * s + 1f, 0.06f, 0.14f, 0.07f, 0.95f * a, 12)
    sb.fillOval(_swx, _swy, 0.27f * s, 0.27f * s, 0.99f, 0.9f, 0.36f, a, 12)
    sb.fillOval(_swx + ndx * 0.06f * s, _swy + ndy * 0.06f * s, 0.12f * s, 0.12f * s, 0.85f, 0.1f, 0.1f, a, 8)
    swung(0.8f, -0.3f)
    sb.fillOval(_swx, _swy, 0.06f * s, 0.06f * s, 0.1f, 0.2f, 0.1f, 0.9f * a, 6)
    drawChargeCrackle(sx, sy, 1.4f * s, 0.5f, 0.8f, 0.4f, a, sb, phase, proj.chargeLevel)
    drawReturnGhosts(sx, sy, 1.4f * s, 0.5f, 0.8f, 0.4f, a, sb, proj)
  }

  // The scythe's blade in its own frame (the snath along x, its head at +x): a band from its heel on
  // the snath's end round to its point, its spine the outer run and its cutting edge, on the inside
  // of the curve, the inner run reversed (fillRibbon's layout)
  private final val SCY_N = 9
  private val SCY_BLADE: Array[Float] = {
    val out = new Array[Float](SCY_N * 4)
    var i = 0
    while (i < SCY_N) {
      val t = i.toFloat / (SCY_N - 1); val m = 1f - t
      val bx = m * m * 1.02f + 2 * t * m * 1.3f + t * t * -0.3f
      val by = m * m * -0.02f + 2 * t * m * -1.2f + t * t * -1.5f
      val dx = 2 * m * (1.3f - 1.02f) + 2 * t * (-0.3f - 1.3f)
      val dy = 2 * m * (-1.2f + 0.02f) + 2 * t * (-1.5f + 1.2f)
      val len = Math.sqrt(dx * dx + dy * dy).toFloat
      var nx = -dy / len; var ny = dx / len
      if (nx * (0.2f - bx) + ny * (-0.4f - by) < 0f) { nx = -nx; ny = -ny }
      val w = 0.36f * Math.pow(m, 0.85).toFloat + 0.02f
      out(i * 2) = bx; out(i * 2 + 1) = by
      val j = 2 * SCY_N - 1 - i
      out(j * 2) = bx + nx * w; out(j * 2 + 1) = by + ny * w
      i += 1
    }
    out
  }
  private val SCY_SNATH = Array(-1.5f,-0.075f, 1.1f,-0.075f, 1.1f,0.075f, -1.5f,0.075f)
  private val SCY_COLLAR = Array(0.84f,-0.12f, 1.14f,-0.12f, 1.14f,0.12f, 0.84f,0.12f)

  /**
   * The Reaper's scythe, thrown: the scythe off its shoulder tumbling end over end over the ground —
   * the long dark snath with its two grips, the steel blade curving off its head to a point, its
   * cutting edge lit — with the violet of the Reaper's swing swept round behind the blade. It used to
   * be a grey pie wedge on a stub of handle, in a mist with arcs, rings and stars round it.
   */
  private[projectiles] def drawScythe(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val spin = tick * 0.3 + proj.id * 2.3
    computeAllDynamics(proj, 0.62f, 0.45f, 0.95f, spin)
    val a = dynAlpha
    val ds = Math.min(dynScale, 1.3f)
    val k = 26f * ds
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy

    sb.fillOval(sx + 3f, sy + 15f * ds, 30f * ds, 9f * ds, 0f, 0f, 0f, 0.2f * a, 12)
    // The swing, swept round behind the blade's point (which lies 1.77 radians back of the snath)
    val bn = Slashes.comma(sx, sy, spin.toFloat - 1.77f, 3.2f, k * 1.58f, k * 0.66f, ISO_Y, 16)
    Slashes.smear(sb, bn, 0.62f, 0.42f, 0.98f, 0.9f, 0.82f, 1f, a)
    // Where the blade was a moment ago
    var ghost = 2
    while (ghost >= 1) {
      Glyphs.frame(sx - ndx * ghost * 6f, sy - ndy * ghost * 6f, (spin - ghost * 0.34).toFloat, k, 1f, ISO_Y)
      val gn = Glyphs.pts(SCY_BLADE)
      sb.fillRibbon(Glyphs._gx, Glyphs._gy, gn, 0.5f, 0.42f, 0.78f, (0.3f - ghost * 0.1f) * a)
      ghost -= 1
    }
    Glyphs.frame(sx, sy, spin.toFloat, k, 1f, ISO_Y)
    // The snath, its grips and the collar the blade is set in
    var n = Glyphs.pts(SCY_SNATH)
    Glyphs.inked(sb, n, 2.6f, INK_R, INK_G, INK_B, 0.9f * a, 0.34f, 0.23f, 0.16f, a)
    Glyphs.gLine(sb, -1.4f, -0.02f, 0.8f, -0.02f, 0.035f, 0.6f, 0.44f, 0.3f, 0.8f * a)
    Glyphs.gLine(sb, -0.75f, 0.05f, -0.75f, 0.32f, 0.17f, INK_R, INK_G, INK_B, 0.9f * a)
    Glyphs.gLine(sb, -0.75f, 0.05f, -0.75f, 0.29f, 0.08f, 0.34f, 0.23f, 0.16f, a)
    Glyphs.gLine(sb, 0.05f, 0.05f, 0.05f, 0.32f, 0.17f, INK_R, INK_G, INK_B, 0.9f * a)
    Glyphs.gLine(sb, 0.05f, 0.05f, 0.05f, 0.29f, 0.08f, 0.34f, 0.23f, 0.16f, a)
    n = Glyphs.pts(SCY_BLADE)
    sb.strokePolygon(Glyphs._gx, Glyphs._gy, n, 3f, INK_R, INK_G, INK_B, 0.92f * a)
    sb.fillRibbon(Glyphs._gx, Glyphs._gy, n, 0.66f, 0.72f, 0.84f, a)
    // The cutting edge, lit, and a darker spine
    var i = 0
    while (i < SCY_N) {
      val j = n - 1 - i
      _pvX(i) = Glyphs._gx(j) * 0.86f + Glyphs._gx(i) * 0.14f; _pvY(i) = Glyphs._gy(j) * 0.86f + Glyphs._gy(i) * 0.14f
      i += 1
    }
    sb.strokePolylineTapered(_pvX, _pvY, SCY_N, 0.12f * k, 0.03f * k, 0.97f, 0.98f, 1f, a, a)
    i = 0
    while (i < SCY_N) {
      val j = n - 1 - i
      _pvX(i) = Glyphs._gx(i) * 0.84f + Glyphs._gx(j) * 0.16f; _pvY(i) = Glyphs._gy(i) * 0.84f + Glyphs._gy(j) * 0.16f
      i += 1
    }
    sb.strokePolylineTapered(_pvX, _pvY, SCY_N, 0.08f * k, 0.02f * k, 0.44f, 0.5f, 0.66f, 0.9f * a, 0.9f * a)
    n = Glyphs.pts(SCY_COLLAR)
    Glyphs.inked(sb, n, 2.2f, INK_R, INK_G, INK_B, 0.9f * a, 0.42f, 0.46f, 0.56f, a)
    // A glint as the edge comes round to the light
    val glint = Math.sin(spin * 2 + proj.id).toFloat
    if (glint > 0.72f)
      sb.fillStarFlare(Glyphs.fx(0.75f, -1.25f), Glyphs.fy(0.75f, -1.25f), k * 0.8f * (glint - 0.72f) / 0.28f, 2.2f,
        spin.toFloat * 0.5f, 0.45f, 1f, 0.96f, 1f, 0.8f * a)
    drawChargeCrackle(sx, sy, k, 0.62f, 0.45f, 0.95f, a, sb, spin, proj.chargeLevel)
    drawReturnGhosts(sx, sy, k, 0.62f, 0.45f, 0.95f, a, sb, proj)
  }
}
