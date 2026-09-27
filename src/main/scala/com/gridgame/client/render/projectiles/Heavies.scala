package com.gridgame.client.render.projectiles

import com.gridgame.common.model.Projectile
import com.gridgame.client.gl.ShapeBatch
import ProjectileKit._
import Silhouettes._

/** Big things with weight: the rocket, the tumbling boulder, the geyser, the sword wave and the
  * inferno blast. */
private[render] object Heavies {
  /** Rocket: nose on the hitbox, body, fins and exhaust trailing behind it. It used to start
   *  at the hitbox and extend five world units forward, so the warhead arrived long before
   *  the explosion did. */
  private[projectiles] def drawRocket(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 29) * 0.35
    computeAllDynamics(proj, 0.45f, 0.47f, 0.35f, phase)
    val p = (0.8f + 0.2f * Math.sin(phase * _stPulseMult).toFloat) * dynAlpha
    val ds = Math.min(dynScale, 1.3f)
    screenDir(proj)
    val nx = _sdx; val ny = _sdy
    val perpX = -ny; val perpY = nx
    val noseLen = 16f * ds; val bodyLen = 40f * ds
    val baseX = sx - nx * noseLen; val baseY = sy - ny * noseLen
    val tailX = baseX - nx * bodyLen; val tailY = baseY - ny * bodyLen
    val dx = baseX - tailX; val dy = baseY - tailY

    sb.fillOvalSoft(tailX - nx * 8f * ds, tailY - ny * 8f * ds, 24f * ds * dynGlow, 19f * ds * dynGlow,
      1f, 0.5f, 0.1f, 0.4f * p, 0f, 14)
    var i = 0
    while (i < 10) {
      val t = ((tick * 0.09 + i * 0.1 + proj.id * 0.07) % 1.0).toFloat
      val fx = tailX - nx * t * 34f * ds + perpX * Math.sin(phase * 3 + i * 1.5).toFloat * (4f + t * 16f) * 0.35f
      val fy = tailY - ny * t * 34f * ds + perpY * Math.sin(phase * 3 + i * 1.5).toFloat * (4f + t * 16f) * 0.35f
      val green = Math.max(0f, 0.9f - t * 0.5f)
      sb.fillOval(fx, fy, 5f + t * 6f, 4f + t * 5f, 1f, green, Math.max(0f, 0.3f - t * 0.2f), 0.65f * (1f - t), 8)
      i += 1
    }
    i = 0
    while (i < 6) {
      val t = ((tick * 0.04 + i * 0.167 + proj.id * 0.09) % 1.0).toFloat
      val smX = tailX - nx * t * 50f * ds + perpX * Math.sin(phase * 0.8 + i * 1.3).toFloat * (6f + t * 12f)
      val smY = tailY - ny * t * 50f * ds + perpY * Math.sin(phase * 0.8 + i * 1.3).toFloat * (6f + t * 12f) - t * 12f
      val gray = 0.6f + t * 0.1f
      val puff = 6f + t * 10f
      sb.strokeOval(smX, smY, puff + 1f, (puff + 1f) * 0.85f, 1.5f, 0.3f, 0.3f, 0.3f, 0.2f * (1f - t), 10)
      sb.fillOval(smX, smY, puff, puff * 0.85f, gray, gray, gray, 0.35f * (1f - t), 10)
      i += 1
    }
    sb.strokeLine(tailX, tailY, baseX, baseY, 16f * ds, 0.1f, 0.1f, 0.08f, 0.85f * p)
    sb.strokeLine(tailX, tailY, baseX, baseY, 13f * ds, 0.4f, 0.42f, 0.3f, 0.95f * p)
    sb.strokeLine(tailX, tailY, baseX, baseY, 6f * ds, 0.55f, 0.58f, 0.45f, 0.65f * p)
    val bandX = tailX + dx * 0.55f; val bandY = tailY + dy * 0.55f
    sb.strokeLine(bandX - dx * 0.07f, bandY - dy * 0.07f, bandX + dx * 0.07f, bandY + dy * 0.07f,
      16f * ds, 0.15f, 0.02f, 0.01f, 0.7f * p)
    sb.strokeLine(bandX - dx * 0.07f, bandY - dy * 0.07f, bandX + dx * 0.07f, bandY + dy * 0.07f,
      14f * ds, 0.95f, 0.2f, 0.1f, 0.85f * p)
    var f = -1
    while (f <= 1) {
      _polyXs3(0) = tailX + nx * 8f * ds; _polyYs3(0) = tailY + ny * 8f * ds
      _polyXs3(1) = tailX - nx * 4f * ds + perpX * f * 11f * ds; _polyYs3(1) = tailY - ny * 4f * ds + perpY * f * 11f * ds
      _polyXs3(2) = tailX - nx * 10f * ds; _polyYs3(2) = tailY - ny * 10f * ds
      sb.fillPolygon(_polyXs3, _polyYs3, 3, 0.45f, 0.48f, 0.35f, 0.9f * p)
      sb.strokePolygon(_polyXs3, _polyYs3, 3, 2f, 0.15f, 0.15f, 0.12f, 0.7f * p)
      f += 2
    }
    _polyXs3(0) = sx; _polyXs3(1) = baseX + perpX * 8f * ds; _polyXs3(2) = baseX - perpX * 8f * ds
    _polyYs3(0) = sy; _polyYs3(1) = baseY + perpY * 8f * ds; _polyYs3(2) = baseY - perpY * 8f * ds
    sb.fillPolygon(_polyXs3, _polyYs3, 3, 0.7f, 0.7f, 0.65f, 0.95f * p)
    sb.strokePolygon(_polyXs3, _polyYs3, 3, 2.5f, 0.15f, 0.15f, 0.12f, 0.8f * p)
    sb.fillOval(baseX + nx * 6f * ds + KEY_LIGHT_X * 3f * ds, baseY + ny * 6f * ds + KEY_LIGHT_Y * 3f * ds,
      4f * ds, 3f * ds, 1f, 1f, 1f, 0.35f * p, 6)
    drawChargeCrackle(sx, sy, 16f * ds, 0.9f, 0.5f, 0.2f, p, sb, phase, proj.chargeLevel)
  }

  /** Boulder — tumbling rock with weight, layered shading, cracks, dust, and debris chips */
  private[projectiles] def drawBoulder(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int, r: Float): Unit = {
    val phase = (tick + proj.id * 19) * 0.3
    computeAllDynamics(proj, 0.4f, 0.32f, 0.2f, phase)
    val p = (0.85 + 0.15 * Math.sin(phase * _stPulseMult)).toFloat * dynAlpha
    val bounceRaw = Math.sin(phase * 1.5).toFloat
    val bounce = (Math.abs(bounceRaw) * 10f).toFloat
    val bounceContact = Math.abs(bounceRaw)
    val ds = dynScale
    val rr = r * ds
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val spin = phase * 2.5
    val hlX = Math.cos(spin).toFloat; val hlY = Math.sin(spin).toFloat

    // Speed lines
    drawSpeedLines(sx, sy, ndx, ndy, 0.45f, 0.38f, 0.25f, 0.25f * p, sb, 6, rr * 1.5f)

    // Ribbon trail of dust
    drawRibbonTrail(sx, sy, ndx, ndy, 0.55f, 0.48f, 0.35f, 0.3f * p, sb, tick, proj.id, 8, 45f * ds * dynTrail, 10f * ds, 2f)

    // Massive ground impact shockwave ring near bounce contact
    if (bounceContact < 0.18f) {
      val impactT = 1f - bounceContact / 0.18f
      val impactAlpha = impactT * 0.55f * p
      val impactR = rr * (1.5f + (1f - impactT) * 0.8f)
      // Outer shockwave flash
      sb.strokeOval(sx, sy + rr * 0.38f, impactR * 1.2f, impactR * 0.35f, 4f * impactT,
        0.7f, 0.6f, 0.4f, impactAlpha * 0.5f, 14)
      // Inner impact ring
      sb.strokeOval(sx, sy + rr * 0.38f, impactR, impactR * 0.3f, 2.5f,
        0.5f, 0.42f, 0.3f, impactAlpha, 12)
      // Ground scorch mark
      sb.fillOval(sx, sy + rr * 0.38f, impactR * 0.9f, impactR * 0.25f,
        0.22f, 0.18f, 0.1f, impactAlpha * 0.35f, 12)
      // Spark burst at bounce impact
      drawSparkBurst(sx, sy + rr * 0.35f, 0.8f, 0.65f, 0.35f, impactAlpha * 0.7f, sb, tick, proj.id, 6, rr * 1.2f)
    }

    // Large dynamic shadow — bigger with bounce height
    val shadowScale = 1f + bounce * 0.04f
    val shadowAlpha = Math.max(0.1f, 0.42f - bounce * 0.015f)
    sb.fillOval(sx, sy + rr * 0.38f, rr * 1.4f * shadowScale, rr * 0.38f * shadowScale,
      0f, 0f, 0f, shadowAlpha, 16)
    sb.fillOval(sx, sy + rr * 0.38f, rr * 1f * shadowScale, rr * 0.25f * shadowScale,
      0f, 0f, 0f, shadowAlpha * 0.4f, 14)

    // Faceted stone hull. The vertex radii come from a fixed per-index pattern so the
    // silhouette is lumpy but stable as it tumbles — re-randomising per frame makes the
    // rock boil.
    val hullN = 9
    var hv = 0
    while (hv < hullN) {
      val a = spin * 0.6 + hv * (Math.PI * 2 / hullN)
      val jag = 0.80f + 0.26f * Math.sin(hv * 2.399f).toFloat
      _shpXs(hv) = sx + Math.cos(a).toFloat * rr * 1.05f * jag
      _shpYs(hv) = sy - bounce + Math.sin(a).toFloat * rr * 0.92f * jag
      hv += 1
    }
    sb.strokePolygon(_shpXs, _shpYs, hullN, 4.5f, 0.10f, 0.06f, 0.02f, 0.9f * p)
    // Fanned from the centre the vertex radii were measured from: the jagged hull is
    // non-convex, so fanning from vertex 0 cuts its own corners off.
    sb.fillFan(sx, sy - bounce, _shpXs, _shpYs, hullN, 0.42f, 0.34f, 0.22f, 0.97f * p)
    // Facet edges from the hull corners toward the centre — what makes it read as carved
    hv = 0
    while (hv < hullN) {
      sb.strokeLine(_shpXs(hv), _shpYs(hv), sx + (_shpXs(hv) - sx) * 0.25f,
        sy - bounce + (_shpYs(hv) - sy + bounce) * 0.25f, 1.6f, 0.26f, 0.20f, 0.12f, 0.45f * p)
      hv += 1
    }
    // Rotating shadow face (bottom half follows spin)
    sb.fillOval(sx - hlX * rr * 0.12f, sy - bounce + rr * 0.18f + hlY * rr * 0.08f,
      rr * 0.85f, rr * 0.55f, 0.22f, 0.17f, 0.1f, 0.45f * p, 14)
    // Rotating highlight face
    sb.fillOval(sx + hlX * rr * 0.18f, sy - bounce + hlY * rr * 0.12f - rr * 0.15f,
      rr * 0.6f, rr * 0.48f, 0.58f, 0.5f, 0.36f, 0.6f * p, 12)
    // Top specular glint (rotating)
    sb.fillOval(sx + hlX * rr * 0.15f - rr * 0.08f, sy - bounce - rr * 0.32f + hlY * rr * 0.08f,
      rr * 0.3f, rr * 0.22f, 0.72f, 0.63f, 0.48f, 0.5f * p, 8)
    sb.fillOval(sx + hlX * rr * 0.1f - rr * 0.05f, sy - bounce - rr * 0.38f + hlY * rr * 0.05f,
      rr * 0.15f, rr * 0.1f, 0.85f, 0.78f, 0.6f, 0.35f * p, 6)

    // 8 deep crack lines with depth shading and bright edges
    var cr = 0; while (cr < 8) {
      val cAngle = spin * 0.3 + cr * Math.PI * 2 / 8 + proj.id * 0.7
      val cLen = rr * (0.35f + Math.sin(phase * 0.4 + cr * 2.3).toFloat * 0.22f)
      val cStartX = sx + Math.cos(cAngle).toFloat * rr * 0.08f
      val cStartY = sy - bounce + Math.sin(cAngle).toFloat * rr * 0.06f
      val cEndX = sx + Math.cos(cAngle).toFloat * cLen
      val cEndY = sy - bounce + Math.sin(cAngle).toFloat * cLen * 0.7f
      // Dark crack depth
      sb.strokeLine(cStartX, cStartY, cEndX, cEndY, 2.5f, 0.12f, 0.08f, 0.03f, 0.55f * p)
      // Lighter bright edge highlight
      sb.strokeLine(cStartX + 0.6f, cStartY + 0.6f, cEndX + 0.6f, cEndY + 0.6f,
        1f, 0.55f, 0.47f, 0.35f, 0.3f * p)
      // Crack fork at end
      val fAngle = cAngle + 0.5 + Math.sin(phase * 0.3 + cr) * 0.3
      val fLen = cLen * 0.3f
      sb.strokeLine(cEndX, cEndY, cEndX + Math.cos(fAngle).toFloat * fLen,
        cEndY + Math.sin(fAngle).toFloat * fLen * 0.6f, 1.5f, 0.15f, 0.1f, 0.05f, 0.35f * p)
    ; cr += 1 }

    // Dense 12-particle dust cloud trail
    var i = 0; while (i < 12) {
      val t = ((tick * 0.055 + i * 0.083 + proj.id * 0.11) % 1.0).toFloat
      val s = (5f + t * 12f) * ds
      val dustX = sx - ndx * t * rr * 2.2f + Math.sin(phase + i * 2).toFloat * 8f * ds
      val dustY = sy - ndy * t * rr * 1.6f + t * 14f * ds
      val gray = 0.52f + t * 0.15f
      // Dust puff outline
      sb.strokeOval(dustX, dustY, s + 1f, (s + 1f) * 0.6f, 1f, 0.35f, 0.3f, 0.2f, 0.15f * (1f - t) * p, 8)
      // Dust puff body
      sb.fillOval(dustX, dustY, s, s * 0.6f, gray, gray * 0.85f, gray * 0.65f, 0.4f * (1f - t) * p, 8)
    ; i += 1 }

    // 8 flying debris chips with varying sizes
    { var i = 0; while (i < 8) {
      val chipPhase = ((phase * 0.7 + i * 0.125 + proj.id * 0.17) % 1.0).toFloat
      val chipAngle = phase * 3.5 + i * Math.PI * 2 / 8
      val chipDist = rr * (0.65f + chipPhase * 1.4f)
      val chipX = sx + Math.cos(chipAngle).toFloat * chipDist
      val chipY = sy - bounce * (1f - chipPhase) + Math.sin(chipAngle).toFloat * chipDist * 0.4f + chipPhase * chipPhase * 22f
      val chipSz = (2f + (1f - chipPhase) * (3f + (i % 3).toFloat * 1.5f)) * ds
      val chipSpin = phase * 5 + i * 2.3
      val chipStrX = 1f + Math.sin(chipSpin).toFloat * 0.3f
      // Chip outline
      sb.fillOval(chipX, chipY, chipSz * chipStrX + 0.8f, chipSz * 0.85f + 0.8f,
        0.18f, 0.14f, 0.08f, 0.35f * (1f - chipPhase) * p, 5)
      // Chip body
      sb.fillOval(chipX, chipY, chipSz * chipStrX, chipSz * 0.8f,
        0.5f + (i % 3).toFloat * 0.05f, 0.42f, 0.28f, 0.55f * (1f - chipPhase) * p, 5)
    ; i += 1 } }

    drawChargeCrackle(sx, sy - bounce, rr, 0.4f, 0.32f, 0.2f, p, sb, phase, proj.chargeLevel)
    drawReturnGhosts(sx, sy - bounce, rr, 0.4f, 0.32f, 0.2f, p, sb, proj)
  }

  /** Geyser — MASSIVE towering water eruption with dense layering, mist halo, foam crown, spray arcs */
  private[projectiles] def drawGeyser(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 23) * 0.4
    computeAllDynamics(proj, 0.3f, 0.6f, 1f, phase)
    val p = (0.85 + 0.15 * Math.sin(phase * _stPulseMult)).toFloat * dynAlpha
    val ds = dynScale
    val columnH = 65f * ds
    val baseW = 32f * ds

    // Huge water mist halo (70px+ radius)
    val mistPulse = (0.75f + 0.25f * Math.sin(phase * 1.8).toFloat)
    sb.fillOvalSoft(sx, sy - columnH * 0.35f, 54f * ds * dynGlow * mistPulse, 43.2f * ds * dynGlow * mistPulse,
      0.35f, 0.65f, 1f, 0.18f * p, 0f, 20)
    sb.fillOvalSoft(sx, sy - columnH * 0.5f, 36f * ds * dynGlow, 30.2f * ds * dynGlow,
      0.5f, 0.78f, 1f, 0.12f * p, 0f, 18)

    // Vertical energy lines (speed line equivalents)
    { var i = 0; while (i < 6) {
      val elX = sx + Math.sin(phase * 1.3 + i * 1.7).toFloat * baseW * 0.4f
      val elLen = columnH * (0.4f + Math.sin(phase * 2 + i * 2.3).toFloat * 0.15f)
      val elAlpha = 0.2f * p * (0.6f + Math.sin(phase * 3 + i * 1.9).toFloat * 0.4f)
      sb.strokeLineSoft(elX, sy - 5f, elX + Math.sin(phase + i) .toFloat * 3f, sy - elLen,
        2f, 0.4f, 0.7f, 1f, elAlpha)
    ; i += 1 } }

    // Large base splash with bold outlined pool + 10 foam bubbles
    sb.strokeOval(sx, sy, baseW * 1.6f, baseW * 0.45f, 3.5f, 0.12f, 0.3f, 0.6f, 0.6f * p, 16)
    sb.fillOval(sx, sy, baseW * 1.4f, baseW * 0.4f, 0.18f, 0.42f, 0.82f, 0.55f * p, 16)
    sb.fillOval(sx, sy, baseW * 1f, baseW * 0.3f, 0.3f, 0.6f, 0.95f, 0.6f * p, 14)
    sb.fillOval(sx, sy, baseW * 0.6f, baseW * 0.18f, 0.55f, 0.82f, 1f, 0.5f * p, 12);
    // 10 foam bubbles around base
    { var b = 0; while (b < 10) {
      val bAngle = phase * 0.8 + b * Math.PI * 2 / 10
      val bDist = baseW * 0.8f + Math.sin(phase * 2 + b * 1.7).toFloat * 5f * ds
      val bx = sx + Math.cos(bAngle).toFloat * bDist
      val by = sy + Math.sin(bAngle).toFloat * bDist * 0.28f
      val bSz = (3f + Math.sin(phase * 3 + b * 2.3).toFloat * 2f) * ds
      sb.strokeOval(bx, by, bSz + 0.5f, bSz * 0.85f + 0.5f, 0.8f, 0.3f, 0.5f, 0.75f, 0.2f * p, 6)
      sb.fillOval(bx, by, bSz, bSz * 0.85f, 0.72f, 0.9f, 1f, 0.5f * p, 6)
      sb.fillOval(bx - bSz * 0.2f, by - bSz * 0.25f, bSz * 0.35f, bSz * 0.3f, 1f, 1f, 1f, 0.25f * p, 4)
    ; b += 1 } }

    // 16-segment layered column (outline, dark blue, light blue, foam core)
    { var layer = 0; while (layer < 16) {
      val t = layer.toFloat / 15
      val y = sy - t * columnH
      val widthT = 1f - t * t * 0.55f
      val waveSz = Math.sin(phase * 2.5 + layer * 0.65).toFloat * 5f * ds
      val layerW = baseW * 0.6f * widthT + waveSz
      val layerH = 5.5f * ds * (1f - t * 0.3f)
      val fadeA = (1f - t * 0.35f) * p
      // Dark outline
      sb.fillOval(sx, y, layerW + 2.5f * ds, layerH + 1.5f, 0.08f, 0.2f, 0.5f, 0.65f * fadeA, 12)
      // Dark blue body
      sb.fillOval(sx, y, layerW + 0.5f * ds, layerH + 0.3f, 0.18f, 0.42f, 0.82f, 0.7f * fadeA, 12)
      // Light blue body
      sb.fillOval(sx, y, layerW * 0.7f, layerH * 0.85f, 0.3f + t * 0.18f, 0.6f + t * 0.15f, 0.92f + t * 0.08f, 0.65f * fadeA, 10)
      // Bright foam core
      sb.fillOval(sx, y, layerW * 0.35f, layerH * 0.65f, 0.65f + t * 0.2f, 0.88f + t * 0.08f, 1f, 0.55f * fadeA, 8)
    ; layer += 1 } }

    // 5 rising water rings expanding as they ascend
    { var ring = 0; while (ring < 5) {
      val ringPhase = ((phase * 0.3 + ring * 0.2) % 1.0).toFloat
      val ringY = sy - ringPhase * columnH * 0.85f
      val ringR = (9f + ringPhase * 14f) * ds
      val ringA = 0.5f * (1f - ringPhase) * p
      val ringThick = 2.5f * (1f - ringPhase * 0.4f)
      // Ring outline
      sb.strokeOval(sx, ringY, ringR + 1f, (ringR + 1f) * 0.3f, ringThick + 1.5f,
        0.15f, 0.35f, 0.7f, ringA * 0.4f, 12)
      // Ring body
      sb.strokeOval(sx, ringY, ringR, ringR * 0.3f, ringThick,
        0.55f, 0.82f, 1f, ringA, 12)
    ; ring += 1 } }

    // 12 spray particles with gravity arcs jetting from top
    { var i = 0; while (i < 12) {
      val sprayPhase = ((phase * 0.5 + i * 0.083 + proj.id * 0.09) % 1.0).toFloat
      val sprayAngle = phase * 1.5 + i * Math.PI * 2 / 12
      val sprayDist = (12f + sprayPhase * 25f) * ds
      val gravDrop = sprayPhase * sprayPhase * 30f * ds
      val spx = sx + Math.cos(sprayAngle).toFloat * sprayDist
      val spy = sy - columnH + Math.sin(sprayAngle).toFloat * sprayDist * 0.35f + gravDrop
      val spSz = (3.5f + (1f - sprayPhase) * 5f) * ds
      // Spray outline
      sb.fillOval(spx, spy, spSz + 0.8f, spSz * 0.8f + 0.8f, 0.15f, 0.35f, 0.7f, 0.25f * (1f - sprayPhase) * p, 6)
      // Spray body
      sb.fillOval(spx, spy, spSz, spSz * 0.8f, 0.5f, 0.82f, 1f, 0.6f * (1f - sprayPhase) * p, 6)
      // Spray highlight
      sb.fillOval(spx - spSz * 0.2f, spy - spSz * 0.25f, spSz * 0.35f, spSz * 0.3f,
        0.85f, 0.95f, 1f, 0.3f * (1f - sprayPhase) * p, 4)
    ; i += 1 } }

    // Dense foam crown at top with sparkle
    sb.strokeOval(sx, sy - columnH + 3f * ds, 16f * ds, 7.5f * ds, 2.5f,
      0.12f, 0.35f, 0.7f, 0.45f * p, 12)
    sb.fillOval(sx, sy - columnH + 3f * ds, 15f * ds, 7f * ds, 0.82f, 0.94f, 1f, 0.6f * p, 12)
    sb.fillOval(sx, sy - columnH + 2f * ds, 10f * ds, 5f * ds, 0.92f, 0.97f, 1f, 0.55f * p, 10)
    sb.fillOval(sx, sy - columnH + 1f * ds, 5f * ds, 3f * ds, 1f, 1f, 1f, 0.5f * p, 8);
    // Foam detail bubbles at crown
    { var fb = 0; while (fb < 4) {
      val fbAngle = phase * 2 + fb * Math.PI / 2
      val fbx = sx + Math.cos(fbAngle).toFloat * 8f * ds
      val fby = sy - columnH + 3f * ds + Math.sin(fbAngle).toFloat * 3f * ds
      sb.fillOval(fbx, fby, 3f * ds, 2.5f * ds, 0.9f, 0.97f, 1f, 0.4f * p, 6)
    ; fb += 1 } }

    // Water ribbon trail
    screenDir(proj)
    drawRibbonTrail(sx, sy, _sdx, _sdy, 0.3f, 0.6f, 1f, 0.25f * p, sb, tick, proj.id, 6, 30f * ds * dynTrail, 8f * ds, 1.5f)

    // 4 sparkle stars along column
    { var i = 0; while (i < 4) {
      val starPhase = ((phase * 0.4 + i * 0.25) % 1.0).toFloat
      val starY = sy - columnH * (0.2f + starPhase * 0.6f)
      val starX = sx + Math.sin(phase * 2 + i * 2.1).toFloat * 14f * ds
      drawSparkleStar(starX, starY, 5.5f * (1f - starPhase * 0.5f) * ds,
        0.75f, 0.95f, 1f, 0.5f * (1f - starPhase) * p, sb, phase * 2 + i)
    ; i += 1 } }

    drawChargeCrackle(sx, sy - columnH * 0.5f, 22f * ds, 0.3f, 0.6f, 1f, p, sb, phase, proj.chargeLevel)
  }

  /** Sword Wave - glowing energy crescent with afterimages */
  private[projectiles] def drawSwordWave(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 37) * 0.4
    computeAllDynamics(proj, 0.65f, 0.65f, 0.9f, phase)
    val p = (0.9 + 0.1 * Math.sin(phase * _stPulseMult)).toFloat * dynAlpha
    val dr = _evoR; val dg = _evoG; val db = _evoB
    val ds = dynScale
    screenDir(proj)
    val ndx = _sdx; val ndy = _sdy
    val arcR = 34f * ds
    val segs = 12
    val baseAngle = Math.atan2(ndy, ndx)

    // Speed lines behind
    drawSpeedLines(sx, sy, ndx, ndy, 0.65f, 0.65f, 0.9f, 0.25f * p, sb, 5 + (_lifePct * 2).toInt, 30f * ds)

    // Ribbon trail
    drawRibbonTrail(sx, sy, ndx, ndy, 0.6f, 0.6f, 0.85f, 0.28f * p, sb, tick, proj.id,
      10, 45f * ds * dynTrail, 9f * ds, 1.5f)

    // Glow halo
    val haloPulse = (0.7f + 0.3f * Math.sin(phase * 2.2).toFloat)
    sb.fillOvalSoft(sx, sy, 36f * ds * dynGlow * haloPulse, 28.8f * ds * dynGlow * haloPulse,
      0.55f, 0.55f, 1f, 0.22f * p, 0f, 20)

    // Expanding ring at center
    val ringP = ((phase * 0.4) % 1.0).toFloat
    val ringR2 = (8f + ringP * 20f) * ds
    sb.strokeOval(sx, sy, ringR2, ringR2 * 0.55f, 2f * (1f - ringP * 0.5f),
      0.7f, 0.7f, 1f, 0.3f * (1f - ringP) * p, 10)

    // 3 trailing afterimage crescents (fading behind)
    var ghost = 0; while (ghost < 3) {
      val ghostOff = (ghost + 1) * 12f * ds
      val gx = sx - ndx * ghostOff; val gy = sy - ndy * ghostOff
      val gAlpha = 0.25f * (1f - ghost * 0.25f) * p
      val gArcR = arcR * (1f - ghost * 0.08f);
      { var i = 0; while (i <= segs) {
        val a = baseAngle - Math.PI * 0.4 + Math.PI * 0.8 * i / segs
        _swOuterXs(i) = (gx + Math.cos(a).toFloat * gArcR)
        _swOuterYs(i) = (gy + Math.sin(a).toFloat * gArcR * 0.55f)
        _swInnerXs(i) = (gx + Math.cos(a).toFloat * gArcR * 0.45f)
        _swInnerYs(i) = (gy + Math.sin(a).toFloat * gArcR * 0.25f)
      ; i += 1 } }
      { var i = 0; while (i <= segs) { _swCrescXs(i) = _swOuterXs(i); _swCrescYs(i) = _swOuterYs(i); i += 1 } }
      { var i = 0; while (i <= segs) { _swCrescXs(segs + 1 + i) = _swInnerXs(segs - i); _swCrescYs(segs + 1 + i) = _swInnerYs(segs - i); i += 1 } }
      sb.fillRibbon(_swCrescXs, _swCrescYs, (segs + 1) * 2, 0.5f, 0.5f, 0.75f, gAlpha)
    ; ghost += 1 }

    // Main crescent geometry
    { var i = 0; while (i <= segs) {
      val a = baseAngle - Math.PI * 0.4 + Math.PI * 0.8 * i / segs
      _swOuterXs(i) = (sx + Math.cos(a).toFloat * arcR)
      _swOuterYs(i) = (sy + Math.sin(a).toFloat * arcR * 0.55f)
      _swInnerXs(i) = (sx + Math.cos(a).toFloat * arcR * 0.4f)
      _swInnerYs(i) = (sy + Math.sin(a).toFloat * arcR * 0.22f)
    ; i += 1 } }
    { var i = 0; while (i <= segs) { _swCrescXs(i) = _swOuterXs(i); _swCrescYs(i) = _swOuterYs(i); i += 1 } }
    { var i = 0; while (i <= segs) { _swCrescXs(segs + 1 + i) = _swInnerXs(segs - i); _swCrescYs(segs + 1 + i) = _swInnerYs(segs - i); i += 1 } }

    // Bold dark outline on outer edge (4px)
    { var j = 0; while (j < segs) {
      sb.strokeLine(_swOuterXs(j), _swOuterYs(j), _swOuterXs(j + 1), _swOuterYs(j + 1),
        4.5f, 0.15f, 0.15f, 0.25f, 0.75f * p)
    ; j += 1 } }
    // Crescent body fill (dark -> body -> bright). A ribbon, not a polygon: the crescent
    // is concave, and fanned from vertex 0 it fills its own hollow and reads as a slab.
    sb.fillRibbon(_swCrescXs, _swCrescYs, (segs + 1) * 2, dr, dg, db, 0.7f * p);
    // Bright inner edge (white-hot core)
    { var j = 0; while (j < segs) {
      sb.strokeLine(_swOuterXs(j), _swOuterYs(j), _swOuterXs(j + 1), _swOuterYs(j + 1),
        3f, 0.85f, 0.85f, 1f, 0.9f * p)
    ; j += 1 } }
    // White-hot cutting edge
    val bc = _chgBright;
    { var j = 0; while (j < segs) {
      sb.strokeLine(_swOuterXs(j), _swOuterYs(j), _swOuterXs(j + 1), _swOuterYs(j + 1),
        1.2f, mix(1f, 1f, bc), mix(1f, 1f, bc), mix(0.95f, 1f, bc), 0.75f * p)
    ; j += 1 } }

    // Slash motion blur particles
    { var i = 0; while (i < 8) {
      val t = ((tick * 0.07 + i * 0.125 + proj.id * 0.13) % 1.0).toFloat
      val blurAngle = baseAngle - Math.PI * 0.3 + Math.PI * 0.6 * i / 8
      val blurDist = arcR * (0.5f + t * 0.4f)
      val bx = sx + Math.cos(blurAngle).toFloat * blurDist + Math.sin(phase * 3 + i * 1.7).toFloat * 3f * ds
      val by = sy + Math.sin(blurAngle).toFloat * blurDist * 0.55f
      val bSz = (3f + (1f - t) * 3.5f) * ds
      sb.fillOval(bx, by, bSz, bSz * 0.6f, 0.7f, 0.7f, 1f, 0.35f * (1f - t) * p, 6)
    ; i += 1 } }

    // Sparkle stars along leading edge
    { var i = 0; while (i < 4) {
      val starPhase = ((phase * 0.5 + i * 0.25) % 1.0).toFloat
      val starIdx = 2 + (i * 2) % segs
      val starX = _swOuterXs(starIdx) + Math.sin(phase * 3 + i * 2.1).toFloat * 4f * ds
      val starY = _swOuterYs(starIdx) + Math.cos(phase * 2.5 + i * 1.7).toFloat * 3f * ds
      drawSparkleStar(starX, starY, 5f * (1f - starPhase * 0.5f) * ds,
        0.85f, 0.85f, 1f, 0.5f * (1f - starPhase) * p, sb, phase * 2 + i)
    ; i += 1 } }

    drawChargeCrackle(sx, sy, 22f * ds, 0.65f, 0.65f, 0.9f, p, sb, phase, proj.chargeLevel)
    drawReturnGhosts(sx, sy, 22f * ds, dr, dg, db, p, sb, proj)
  }

  // Sword wave: segs=12 → outer/inner (13 each), crescent (26)
  private val _swOuterXs = new Array[Float](13)
  private val _swOuterYs = new Array[Float](13)
  private val _swInnerXs = new Array[Float](13)
  private val _swInnerYs = new Array[Float](13)
  private val _swCrescXs = new Array[Float](26)
  private val _swCrescYs = new Array[Float](26)

  /** Inferno Blast — CARTOONISH: massive fire vortex with bold spirals and sparkle stars */
  private[projectiles] def drawInfernoBlast(proj: Projectile, sx: Float, sy: Float, sb: ShapeBatch, tick: Int): Unit = {
    val phase = (tick + proj.id * 23) * 0.4
    computeAllDynamics(proj, 1f, 0.4f, 0.02f, phase)
    // Steady (see energyBolt): it used to throb down to 20% alpha three times a second
    val p = (0.9 + 0.1 * Math.sin(phase * _stPulseMult)).toFloat * dynAlpha
    val ds = dynScale
    val bc = _chgBright
    val r = 45f * ds

    // Heat halo. 3x the 45px vortex radius put a 135px wash over an already
    // large effect; 2x plus the clamped dynGlow still reads as furnace heat.
    sb.fillOvalSoft(sx, sy, r * 2f * dynGlow, r * 1.6f * dynGlow, 1f, 0.2f, 0f, 0.28f * p, 0f, 20)

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
    sb.strokeOval(sx, sy, r * 0.52f, r * 0.4f, outlineW(r * 0.52f), 0.15f, 0.02f, 0f, 0.75f * p, 16)
    sb.fillOval(sx, sy, r * 0.5f, r * 0.38f, 0.95f, 0.4f, 0.02f, 0.9f * p, 16)
    sb.fillOval(sx, sy, r * 0.3f, r * 0.23f, 1f, 0.65f, 0.08f, 0.95f * p, 14)
    // Cartoon highlight
    sb.fillOval(sx + KEY_LIGHT_X * 8f * ds, sy + KEY_LIGHT_Y * 5f * ds, 6f * ds, 4f * ds, 1f, 1f, 0.8f, 0.4f * p, 8)
    sb.fillOval(sx, sy, 8f * ds, 6.5f * ds, 1f, 1f, mix(0.7f, 1f, bc), 0.95f * p, 10)

    // More embers — bigger
    var i = 0; while (i < 12) {
      val angle = phase * 1.5 + i * Math.PI / 6
      val fl = r + Math.sin(phase * 3 + i * 2).toFloat * 16 * ds
      sb.fillOval(sx + Math.cos(angle).toFloat * fl,
        sy + Math.sin(angle).toFloat * fl * 0.5f - Math.abs(Math.sin(phase * 4 + i)).toFloat * 10 * ds,
        6f * ds, 4.5f * ds, 1f, 0.5f, 0f, 0.55f * p, 8)
    ; i += 1 }

    // Sparkle stars orbiting
    { var i = 0; while (i < 4) {
      val starAngle = phase * 0.8 + i * Math.PI / 2
      val starDist = r * 0.7f
      val starX = sx + Math.cos(starAngle).toFloat * starDist
      val starY = sy + Math.sin(starAngle).toFloat * starDist * 0.5f
      drawSparkleStar(starX, starY, 6f * ds, 1f, 0.9f, 0.4f, 0.4f * p, sb, phase * 2 + i)
    ; i += 1 } }
    drawChargeCrackle(sx, sy, r * 0.5f, 1f, 0.4f, 0.02f, p, sb, phase, proj.chargeLevel)
  }
}
