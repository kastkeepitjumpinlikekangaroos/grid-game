package com.gridgame.client.render
package world

import com.gridgame.common.model._

/** The arrow on the ground from our player toward the cursor: where a shot will go. */
private[render] final class AimArrowPainter(ctx: RenderContext) {
  import ctx._

  // Pre-allocated arrays for aim arrow (avoids per-frame allocations)
  private val _aimGlowXs = new Array[Float](22)  // glowN=10 → 10*2+2=22
  private val _aimGlowYs = new Array[Float](22)
  private val _aimBodyXs = new Array[Float](42)   // bodyN=20 → 20*2+2=42
  private val _aimBodyYs = new Array[Float](42)

  def draw(): Unit = {
    if (!client.isCharging || client.getIsDead) return
    val pt = client.getSelectedCharacterDef.primaryProjectileType
    if (!ProjectileDef.get(pt).chargeSpeedScaling.isDefined) return

    val pos = client.getLocalPosition
    val gridX = pos.getX.toDouble; val gridY = pos.getY.toDouble
    val drawX = if (camera.visualX.isNaN) gridX else camera.visualX
    val drawY = if (camera.visualY.isNaN) gridY else camera.visualY

    val mouseWX = client.getMouseWorldX; val mouseWY = client.getMouseWorldY
    val adx = mouseWX - gridX; val ady = mouseWY - gridY
    val dist = Math.sqrt(adx * adx + ady * ady)
    if (dist < 0.01) return
    val ndx = adx / dist; val ndy = ady / dist

    val chargeLevel = client.getChargeLevel
    val chargePct = chargeLevel / 100f
    val pDef = ProjectileDef.get(pt)
    val crs = pDef.chargeRangeScaling
    val minRange = if (crs.isDefined) crs.get.min.toInt else pDef.maxRange
    val maxRange = if (crs.isDefined) crs.get.max.toInt else pDef.maxRange
    val cylLength = 1.0 + minRange + chargePct * (maxRange - minRange)
    intToRGB(client.getLocalColorRGB)
    val cr = _rgb_r; val cg = _rgb_g; val cb = _rgb_b
    val phase = animationTick * 0.12
    val pulse = (0.85 + 0.15 * Math.sin(phase * 2.0)).toFloat

    // Heat color blend
    val heatR = Math.min(1f, chargePct * 2f)
    val heatG = Math.min(1f, Math.max(0f, 1f - (chargePct - 0.5f) * 2f))
    val blendR = Math.min(1f, cr * 0.4f + heatR * 0.6f)
    val blendG = Math.min(1f, cg * 0.4f + heatG * 0.6f)
    val blendB = Math.min(1f, cb * 0.3f)
    val brightR = Math.min(1f, blendR * 0.3f + 0.7f)
    val brightG = Math.min(1f, blendG * 0.3f + 0.7f)
    val brightB = Math.min(1f, blendB * 0.3f + 0.7f)

    val perpX = -ndy; val perpY = ndx; val cylRadius = 0.6

    def cylSX(t: Double, w: Double): Float =
      worldToScreenX(drawX + ndx * cylLength * t + perpX * cylRadius * w,
                     drawY + ndy * cylLength * t + perpY * cylRadius * w).toFloat
    def cylSY(t: Double, w: Double): Float =
      worldToScreenY(drawX + ndx * cylLength * t + perpX * cylRadius * w,
                     drawY + ndy * cylLength * t + perpY * cylRadius * w).toFloat

    beginShapes()

    // Ground glow
    val glowN = 10
    var i = 0
    while (i <= glowN) {
      val t = i.toDouble / glowN
      val gw = 1.8 * pulse
      val cwx = drawX + ndx * cylLength * t
      val cwy = drawY + ndy * cylLength * t
      _aimGlowXs(i) = worldToScreenX(cwx + perpX * gw, cwy + perpY * gw).toFloat
      _aimGlowYs(i) = worldToScreenY(cwx + perpX * gw, cwy + perpY * gw).toFloat
      _aimGlowXs(glowN * 2 + 1 - i) = worldToScreenX(cwx - perpX * gw, cwy - perpY * gw).toFloat
      _aimGlowYs(glowN * 2 + 1 - i) = worldToScreenY(cwx - perpX * gw, cwy - perpY * gw).toFloat
      i += 1
    }
    shapeBatch.fillPolygon(_aimGlowXs, _aimGlowYs, glowN * 2 + 2, blendR, blendG, blendB, 0.12f * pulse)

    // Cylinder body
    val bodyN = 20
    i = 0
    while (i <= bodyN) {
      val t = i.toDouble / bodyN
      _aimBodyXs(i) = cylSX(t, 1.0)
      _aimBodyYs(i) = cylSY(t, 1.0)
      _aimBodyXs(bodyN * 2 + 1 - i) = cylSX(t, -1.0)
      _aimBodyYs(bodyN * 2 + 1 - i) = cylSY(t, -1.0)
      i += 1
    }
    shapeBatch.fillPolygon(_aimBodyXs, _aimBodyYs, bodyN * 2 + 2, blendR * 0.5f, blendG * 0.5f, blendB * 0.5f, 0.25f + chargePct * 0.10f)

    // Energy spine
    shapeBatch.setAdditiveBlend(true)
    val spineSegs = 14
    i = 0
    while (i < spineSegs) {
      val t0 = i.toDouble / spineSegs; val t1 = (i + 1).toDouble / spineSegs
      val fadeAlpha = ((1.0 - t0 * 0.7) * (0.20 + chargePct * 0.15) * pulse).toFloat
      val lw = ((2.0 + chargePct * 1.5) * (1.0 - t0 * 0.5)).toFloat
      shapeBatch.strokeLine(cylSX(t0, 0), cylSY(t0, 0), cylSX(t1, 0), cylSY(t1, 0), lw, brightR, brightG, brightB, clamp(fadeAlpha))
      i += 1
    }

    // Energy rings
    val ringCount = 4 + (chargePct * 4).toInt
    i = 0
    while (i < ringCount) {
      val t = ((animationTick * 0.035 + i.toDouble / ringCount) % 1.0)
      val ringAlpha = ((1.0 - Math.abs(t - 0.5) * 2.0) * (0.30 + chargePct * 0.25) * pulse).toFloat
      val ringSegs = 8
      var j = 0
      while (j < ringSegs) {
        val a1 = Math.PI * j.toDouble / ringSegs - Math.PI * 0.5
        val a2 = Math.PI * (j + 1).toDouble / ringSegs - Math.PI * 0.5
        val w1 = Math.cos(a1); val w2 = Math.cos(a2)
        val f1 = Math.sin(a1) * (cylRadius / cylLength) * 0.3
        val f2 = Math.sin(a2) * (cylRadius / cylLength) * 0.3
        shapeBatch.strokeLine(cylSX(t + f1, w1), cylSY(t + f1, w1),
          cylSX(t + f2, w2), cylSY(t + f2, w2), 1.5f + chargePct, brightR, brightG, brightB, clamp(ringAlpha))
        j += 1
      }
      i += 1
    }

    // Tip glow
    val tipSX = cylSX(1.0, 0); val tipSY = cylSY(1.0, 0)
    val orbR = (4f + chargePct * 5f) * pulse
    shapeBatch.fillOvalSoft(tipSX, tipSY, orbR * 2, orbR, brightR, brightG, brightB, 0.45f * pulse, 0f, 12)

    shapeBatch.setAdditiveBlend(false)
  }
}
