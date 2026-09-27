package com.gridgame.client.render.projectiles

import com.gridgame.common.model.Projectile
import com.gridgame.client.gl.ShapeBatch
import ProjectileKit._
import Silhouettes._
import Lobbed._

/** Jaws, claws and stings, closing on the hitbox with the foot, the gum or the tail trailing behind:
  * the shark's jaw, the raptor's talon, the vampire's fang, the scorpion's stinger, the minotaur's
  * horn and the claw swipe. */
private[render] object Bites {
  /** Jaw — predatory chomping shark jaws with smooth silhouette, bite animation, teeth, eyes, wake */
  private[projectiles] def drawJaw(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
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

  /** Talon — curved 3-prong razor claw with slash trail */
  private[projectiles] def drawTalon(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
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
  private[projectiles] def drawBloodFang(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
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
  private[projectiles] def drawStinger(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
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
  private[projectiles] def drawHorn(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
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
  private[projectiles] def drawClawSwipe(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
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
