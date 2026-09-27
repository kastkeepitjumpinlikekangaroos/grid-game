package com.gridgame.client.render
package world

import java.util.UUID

/** Which glowing effects a player has this frame, for [[GlowEffectPainter.deferAdditive]]. */
private[render] object GlowEffectPainter {
  final val FX_GEM = 1; final val FX_CHARGE = 2; final val FX_PHASED = 4
  final val FX_BURN = 8; final val FX_HIT = 16; final val FX_CAST = 32
}

/**
 * The glowing effects on players — a gem's glow, a charge building, a phase, a burn's heat, a hit's
 * flash, a cast's flash — drawn additively. Each player's are noted during the depth pass
 * ([[deferAdditive]]) and all of them drawn in one additive pass after it ([[drawDeferred]]), rather
 * than toggling the blend mode for every player. The `*Inner` functions draw inside that pass.
 */
private[render] final class GlowEffectPainter(ctx: RenderContext) {
  import ctx._
  import GlowEffectPainter._

  private val _fxXs = new Array[Float](8)
  private val _fxYs = new Array[Float](8)

  // Deferred additive effects — batched to minimize blend mode toggles
  private val MAX_DEFERRED_ADD_FX = 64
  private val _dafCX = new Array[Double](MAX_DEFERRED_ADD_FX)
  private val _dafCY = new Array[Double](MAX_DEFERRED_ADD_FX)
  private val _dafFlags = new Array[Int](MAX_DEFERRED_ADD_FX)
  private val _dafChargeLevel = new Array[Int](MAX_DEFERRED_ADD_FX)
  private val _dafHitTime = new Array[Long](MAX_DEFERRED_ADD_FX)
  private val _dafHitColorR = new Array[Float](MAX_DEFERRED_ADD_FX)
  private val _dafHitColorG = new Array[Float](MAX_DEFERRED_ADD_FX)
  private val _dafHitColorB = new Array[Float](MAX_DEFERRED_ADD_FX)
  private val _dafHitDirX = new Array[Float](MAX_DEFERRED_ADD_FX)
  private val _dafHitDirY = new Array[Float](MAX_DEFERRED_ADD_FX)
  private var _dafCount = 0

  /** A burn's flames and embers (drawBurnEffectInner). */
  private def drawBurnEffectCore(x: Float, y: Float, tick: Int): Unit = {
    // BRIGHT base glow (25px radius, strong alpha)
    val basePulse = (0.6 + 0.4 * Math.sin(tick * 0.1)).toFloat
    shapeBatch.fillOvalSoft(x, y + 4f, 28f, 16f, 1f, 0.5f, 0.05f, 0.2f * basePulse, 0f, 14)
    shapeBatch.fillOvalSoft(x, y + 4f, 20f, 11f, 1f, 0.65f, 0.1f, 0.15f * basePulse, 0f, 12)

    // Occasional bright flash pulse at base (every ~60 ticks)
    val flashPhase = (tick % 60).toFloat / 60f
    if (flashPhase < 0.1f) {
      val flashAlpha = (1f - flashPhase / 0.1f) * 0.3f
      shapeBatch.fillOvalSoft(x, y + 2f, 30f, 18f, 1f, 0.8f, 0.3f, flashAlpha, 0f, 16)
    }

    // 12 flame tongues — each with dark red outline + orange body + yellow tip
    var i = 0
    while (i < 12) {
      val angle = (tick * 0.14 + i * 0.5236).toFloat  // 2*PI/12 spacing
      val dist = 10f + Math.sin(tick * 0.1 + i * 0.8).toFloat * 6f
      val rise = (tick * 0.55f + i * 2.2f) % 18f
      val lifeAlpha = 1f - rise / 18f
      val fx = x + dist * Math.cos(angle).toFloat
      val fy = y + dist * Math.sin(angle).toFloat * 0.45f - rise
      val flameSz = 4.5f + Math.sin(tick * 0.18 + i * 1.7).toFloat * 2.5f
      val flameH = flameSz * 1.6f
      // Dark red outline
      shapeBatch.fillOval(fx, fy, flameSz + 2f, flameH + 2f, 0.7f, 0.12f, 0.02f, 0.3f * lifeAlpha, 8)
      // Orange body
      shapeBatch.fillOval(fx, fy, flameSz + 0.5f, flameH + 0.5f, 1f, 0.45f, 0.06f, 0.45f * lifeAlpha, 8)
      // Yellow-white tip (upper portion)
      shapeBatch.fillOval(fx, fy - flameH * 0.2f, flameSz * 0.5f, flameH * 0.5f, 1f, 0.85f, 0.25f, 0.5f * lifeAlpha, 6)
      // White-hot core at flame base
      if (rise < 5f) {
        shapeBatch.fillOval(fx, fy + flameH * 0.3f, flameSz * 0.3f, flameH * 0.25f, 1f, 0.95f, 0.7f, 0.35f * lifeAlpha, 4)
      }
      i += 1
    }

    // 10 rising embers with drift and color variation (white-hot -> orange -> red as they rise)
    { var e = 0; while (e < 10) {
      val et = ((tick * 0.035 + e * 0.1) % 1.0).toFloat
      val drift = Math.sin(tick * 0.07 + e * 2.3).toFloat * 10f * et
      val drift2 = Math.cos(tick * 0.05 + e * 1.7).toFloat * 4f * et
      val ex = x + drift + drift2
      val ey = y - et * 35f - 4f
      val emberSz = 2.5f + (1f - et) * 2f
      // Color transitions from white-hot to orange to red
      val eR = 1f
      val eG = if (et < 0.3f) 0.9f + (1f - et / 0.3f) * 0.1f else 0.5f * (1f - (et - 0.3f) / 0.7f)
      val eB = if (et < 0.2f) 0.6f * (1f - et / 0.2f) else 0f
      shapeBatch.fillOval(ex, ey, emberSz, emberSz, eR, eG, eB, 0.6f * (1f - et), 6)
      // Tiny ember trail
      shapeBatch.strokeLineSoft(ex, ey + 3f, ex - drift * 0.05f, ey + 6f, 1f, eR, eG * 0.8f, eB, 0.25f * (1f - et))
    ; e += 1 } }

    // 6 heat shimmer waves above
    { var s = 0; while (s < 6) {
      val shimY = y - 18f - s * 6f
      val shimX = x + Math.sin(tick * 0.07 + s * 1.5).toFloat * 8f
      val shimR = 10f + s * 4f
      val shimAlpha = 0.06f * (1f - s / 6f)
      shapeBatch.fillOvalSoft(shimX, shimY, shimR, shimR * 0.5f, 1f, 0.55f, 0.12f, shimAlpha, 0f, 8)
    ; s += 1 } }

    // 4 smoke wisps at top (dark gray rising puffs)
    { var sm = 0; while (sm < 4) {
      val smokePhase = ((tick * 0.02f + sm * 0.25f) % 1f)
      val smokeX = x + Math.sin(tick * 0.04 + sm * 2.5).toFloat * 7f
      val smokeY = y - 28f - smokePhase * 22f
      val smokeR = 5f + smokePhase * 6f
      val smokeAlpha = (1f - smokePhase) * 0.15f
      shapeBatch.fillOvalSoft(smokeX, smokeY, smokeR, smokeR * 0.7f, 0.3f, 0.25f, 0.22f, smokeAlpha, 0f, 8)
    ; sm += 1 } }
  }

  /** A hit's flash (drawHitEffectInner).
   *  hr/hg/hb is the projectile color (themed); ddx/ddy is the screen-space unit direction
   *  the projectile was traveling (for directional impact streak / knockback arrow). */
  private def drawHitEffectCore(x: Float, y: Float, progress: Double, fadeOut: Float, hitTime: Long,
                                hr: Float, hg: Float, hb: Float, ddx: Float, ddy: Float): Unit = {
    // Brighter, fuller hit color (mix with white so themed flash still pops)
    val br = Math.min(1f, hr * 0.4f + 0.6f)
    val bg = Math.min(1f, hg * 0.4f + 0.6f)
    val bb = Math.min(1f, hb * 0.4f + 0.6f)

    // White-out flash overlay (longer + bigger — unmistakable IMPACT FRAME, first 24% / ~120ms)
    if (progress < 0.24) {
      val flashAlpha = 0.55f * (1f - (progress / 0.24).toFloat)
      shapeBatch.fillOval(x, y, 56f, 42f, 1f, 1f, 1f, flashAlpha, 18)
      shapeBatch.fillOval(x, y, 38f, 28f, 1f, 1f, 0.95f, flashAlpha * 1.2f, 14)
      shapeBatch.fillOval(x, y, 20f, 14f, 1f, 1f, 1f, flashAlpha * 1.5f, 10)
    }

    // Themed central glow burst (3 layers, projectile-colored)
    shapeBatch.fillOvalSoft(x, y, 50f * fadeOut, 38f * fadeOut, hr, hg, hb, 0.45f * fadeOut, 0f, 18)
    shapeBatch.fillOvalSoft(x, y, 32f * fadeOut, 22f * fadeOut, br, bg, bb, 0.5f * fadeOut, 0f, 14)
    shapeBatch.fillOval(x, y, 16f * fadeOut, 12f * fadeOut, 1f, 1f, 1f, 0.45f * fadeOut, 10)

    // Directional IMPACT STREAK — a fat slash that points along the projectile direction
    // (so the hit player can tell where the shot came from)
    if (progress < 0.5 && (ddx * ddx + ddy * ddy) > 0.01f) {
      val streakProgress = (progress / 0.5).toFloat
      val streakFade = 1f - streakProgress
      val streakLen = 38f * (0.5f + streakProgress * 0.8f)
      val backLen = 18f
      val sx0 = x - ddx * backLen
      val sy0 = y - ddy * backLen * 0.6f
      val sx1 = x + ddx * streakLen
      val sy1 = y + ddy * streakLen * 0.6f
      // Outer halo
      shapeBatch.strokeLineSoft(sx0, sy0, sx1, sy1, 12f, hr, hg, hb, 0.45f * streakFade)
      // Mid layer
      shapeBatch.strokeLineSoft(sx0, sy0, sx1, sy1, 6f, br, bg, bb, 0.7f * streakFade)
      // Bright white core
      shapeBatch.strokeLine(sx0, sy0, sx1, sy1, 2.5f, 1f, 1f, 1f, 0.85f * streakFade)
      // Streak head — bright orb at the impact entry side
      shapeBatch.fillOval(sx1, sy1, 7f * streakFade, 5.5f * streakFade, 1f, 1f, 1f, 0.85f * streakFade, 10)
    }

    // 12 directional sparks with thick trailing streaks — themed
    val sparkDist = 12f + 40f * progress.toFloat
    val sparkSz = 5.5f * fadeOut
    var i = 0
    while (i < 12) {
      val angle = i * 0.5236f + hitTime * 0.001f // 30 degree spacing
      val jitter = Math.sin(hitTime * 0.003 + i * 1.7).toFloat * 0.15f
      val finalAngle = angle + jitter
      val sx = x + math.cos(finalAngle).toFloat * sparkDist
      val sy = y + math.sin(finalAngle).toFloat * sparkDist * 0.6f
      val trailStartDist = sparkDist * 0.3f
      val trailX = x + math.cos(finalAngle).toFloat * trailStartDist
      val trailY = y + math.sin(finalAngle).toFloat * trailStartDist * 0.6f
      shapeBatch.strokeLineSoft(trailX, trailY, sx, sy, 3.5f, hr, hg, hb, 0.35f * fadeOut)
      shapeBatch.strokeLine(trailX, trailY, sx, sy, 1.5f, br, bg, bb, 0.55f * fadeOut)
      shapeBatch.fillOval(sx, sy, sparkSz, sparkSz * 0.8f, br, bg, bb, 0.7f * fadeOut, 6)
      shapeBatch.fillOval(sx, sy, sparkSz * 0.5f, sparkSz * 0.4f, 1f, 1f, 1f, 0.55f * fadeOut, 4)
      i += 1
    }

    // PRIMARY shockwave ring — thick, themed, expands fast (impact ring)
    val ringProgress = progress * 0.6
    if (ringProgress < 1.0) {
      val ringR = (10f + 50f * ringProgress.toFloat)
      val ringAlpha = 0.55f * (1f - ringProgress.toFloat)
      shapeBatch.strokeOval(x, y, ringR, ringR * 0.65f, 3.5f, hr, hg, hb, ringAlpha, 22)
      shapeBatch.strokeOval(x, y, ringR * 0.9f, ringR * 0.6f, 2f, br, bg, bb, ringAlpha * 0.7f, 20)
      shapeBatch.strokeOval(x, y, ringR * 0.78f, ringR * 0.5f, 1f, 1f, 1f, ringAlpha * 0.5f, 18)
    }

    // SECONDARY flash ring — adds a "double pulse" for clarity (35-65% progress)
    if (progress > 0.35 && progress < 0.65) {
      val ring2Phase = ((progress - 0.35) / 0.3).toFloat
      val ring2R = 5f + 36f * ring2Phase
      val ring2Alpha = 0.35f * (1f - ring2Phase)
      shapeBatch.strokeOval(x, y, ring2R, ring2R * 0.65f, 2.5f, hr, hg, hb, ring2Alpha, 18)
      shapeBatch.strokeOval(x, y, ring2R * 0.7f, ring2R * 0.45f, 1.4f, 1f, 1f, 1f, ring2Alpha * 0.6f, 16)
    }

    // 8 debris chunks flying outward — themed color
    { var d = 0; while (d < 8) {
      val debrisAngle = d * 0.785f + hitTime * 0.002f // 45 degree spacing
      val debrisDist = 6f + 38f * progress.toFloat + Math.sin(d * 2.3).toFloat * 6f
      val debrisGravity = progress.toFloat * progress.toFloat * 18f
      val dx = x + math.cos(debrisAngle).toFloat * debrisDist
      val dy = y + math.sin(debrisAngle).toFloat * debrisDist * 0.5f + debrisGravity
      val debrisSz = 3.5f * fadeOut
      val debrisAlpha = 0.55f * fadeOut
      shapeBatch.fillRect(dx - debrisSz * 0.5f, dy - debrisSz * 0.5f, debrisSz, debrisSz,
        br, bg, bb, debrisAlpha)
      // Bright pip on each debris
      shapeBatch.fillRect(dx - 0.5f, dy - 0.5f, 1f, 1f, 1f, 1f, 1f, debrisAlpha)
    ; d += 1 } }
  }

  /**
   * Gem boost — crystal shards orbiting on an isometric ellipse (scaled by depth so they
   * pass in front of and behind the player), motes of value drifting off the top, and a
   * twinkle at the crown.
   */
  private def drawGemGlowInner(cx: Double, cy: Double): Unit = {
    val x = cx.toFloat; val y = cy.toFloat
    val t = animTickF
    val pulse = (0.7 + 0.3 * Math.sin(t * 0.08)).toFloat
    shapeBatch.fillOvalSoft(x, y, 19f, 15f, 0f, 0.9f, 0.8f, 0.09f * pulse, 0f, 14)

    var i = 0
    while (i < 3) {
      val ang = t * 0.045f + i * 2.0943951f
      val ca = Math.cos(ang).toFloat; val sa = Math.sin(ang).toFloat
      val ox = x + ca * 20f
      val oy = y + sa * 9f - 2f
      val depth = 0.65f + 0.35f * (0.5f + 0.5f * sa) // nearer the viewer (lower) = larger
      val h = 7f * depth; val w = 3.4f * depth
      _fxXs(0) = ox;     _fxYs(0) = oy - h
      _fxXs(1) = ox + w; _fxYs(1) = oy
      _fxXs(2) = ox;     _fxYs(2) = oy + h
      _fxXs(3) = ox - w; _fxYs(3) = oy
      val a = clamp((0.20f + 0.13f * sa) * pulse)
      shapeBatch.fillPolygon(_fxXs, _fxYs, 4, 0.2f, 1f, 0.85f, a)
      // Facet highlight down one edge
      shapeBatch.strokeLine(ox, oy - h, ox + w * 0.6f, oy + h * 0.2f, 1f, 0.9f, 1f, 0.95f, clamp(a * 1.4f))
      shapeBatch.fillOvalSoft(ox, oy, w * 2.6f, w * 2.6f, 0.3f, 1f, 0.8f, clamp(a * 0.5f), 0f, 6)
      i += 1
    }

    i = 0
    while (i < 3) {
      val mp = (t * 0.02f + i * 0.37f) % 1f
      val mx = x + Math.sin(t * 0.06f + i * 2.3f).toFloat * 7f
      val my = y + 10f - mp * 26f
      shapeBatch.fillDot(mx, my, 1.2f + (1f - mp), 0.6f, 1f, 0.85f, (1f - mp) * 0.32f * pulse)
      i += 1
    }

    val tw = Math.sin(t * 0.11f).toFloat
    if (tw > 0.3f) {
      shapeBatch.fillStarFlare(x, y - 14f, 9f, 1.8f, 0.4f, 0.5f, 0.75f, 1f, 0.9f,
        0.32f * ((tw - 0.3f) / 0.7f))
    }
  }

  /**
   * Charging — an arc gauge that literally fills with charge, motes spiralling in to feed
   * the core, a tightening ground rune, then crackle above 70% and a ready-pulse at full.
   */
  private def drawChargingEffectInner(cx: Double, cy: Double, chargeLevel: Int): Unit = {
    if (chargeLevel <= 0) return
    val x = cx.toFloat; val y = cy.toFloat
    val t = animTickF
    val pct = chargeLevel / 100f
    val pulse = (0.8 + 0.2 * Math.sin(t * 0.15)).toFloat
    // Heat ramp: gold while building → white-hot at full
    val cr = 1f
    val cg = 0.75f + pct * 0.25f
    val cb = 0.25f + pct * 0.6f

    // Core bulge
    val coreR = 6f + pct * 7f
    shapeBatch.fillOvalSoft(x, y, coreR * 2.2f, coreR * 1.6f, cr, cg, cb, 0.07f * pct * pulse, 0f, 16)
    shapeBatch.fillOvalSoft(x, y, coreR, coreR * 0.8f, 1f, 1f, 0.92f, 0.15f * pct * pulse, 0f, 12)

    // Gauge: band sweeping clockwise from the top, so charge is readable at a glance
    val gRx = 17f; val gRy = 13f
    val sweep = pct * 6.2831853f
    val segs = Math.max(2, (sweep * 3f).toInt)
    shapeBatch.fillArcBand(x, y, gRx, gRy, gRx + 2.4f, gRy + 2f, -1.5707963f, sweep, segs,
      cr, cg, cb, 0.26f * pulse, 0.38f * pulse)
    // Spark riding the head of the gauge
    val headA = -1.5707963f + sweep
    shapeBatch.fillStarFlare(x + gRx * Math.cos(headA).toFloat, y + gRy * Math.sin(headA).toFloat,
      7f + pct * 4f, 1.6f, t * 0.2f, 0.55f, 1f, 1f, 0.9f, 0.5f * pulse)

    // Motes spiralling inward — more of them, faster, as charge builds
    val motes = 3 + (pct * 4f).toInt
    var i = 0
    while (i < motes) {
      val mt = (t * (0.012f + pct * 0.02f) + i.toFloat / motes) % 1f // 1 = far out, 0 = arrived
      val rad = 30f * mt
      val ang = t * 0.05f + i * 2.4f + (1f - mt) * 3.5f
      val ca = Math.cos(ang).toFloat; val sa = Math.sin(ang).toFloat * 0.75f
      val ma = (1f - mt) * 0.42f * pct
      shapeBatch.strokeLine(x + ca * rad, y + sa * rad, x + ca * rad * 0.82f, y + sa * rad * 0.82f,
        1.3f, cr, cg, cb, clamp(ma * 0.7f))
      shapeBatch.fillDot(x + ca * rad, y + sa * rad, 1.3f + (1f - mt) * 1.2f, 1f, 1f, 0.9f, clamp(ma))
      i += 1
    }

    // Ground rune ring with rising ticks, tightening as charge builds
    val groundY = y + 22f
    val gr = 26f - pct * 5f
    shapeBatch.strokeOval(x, groundY, gr, gr * 0.42f, 1.2f, cr, cg, cb, 0.13f * pct * pulse, 16)
    i = 0
    while (i < 6) {
      val ta = i * 1.0471976f - t * 0.03f
      val tx = x + Math.cos(ta).toFloat * gr
      val ty = groundY + Math.sin(ta).toFloat * gr * 0.42f
      shapeBatch.strokeLine(tx, ty, tx, ty - 3f - pct * 4f, 1.4f, cr, cg, cb, clamp(0.20f * pct * pulse))
      i += 1
    }

    // Crackle above 70%
    if (pct > 0.7f) {
      val ci = (pct - 0.7f) / 0.3f
      i = 0
      while (i < 3) {
        val ang = t * 0.16f + i * 2.0943951f
        val len = 12f + 8f * Math.sin(t * 0.31f + i * 1.7f).toFloat * ci
        val ca = Math.cos(ang).toFloat; val sa = Math.sin(ang).toFloat
        val mx = x + ca * len * 0.55f + Math.sin(t * 0.4f + i).toFloat * 3f
        val my = y + sa * len * 0.4f + Math.cos(t * 0.37f + i).toFloat * 2.5f
        shapeBatch.strokeLine(x, y, mx, my, 1.6f * ci, 1f, 1f, 0.95f, clamp(0.36f * ci))
        shapeBatch.strokeLine(mx, my, x + ca * len, y + sa * len * 0.7f, 1.2f * ci, 1f, 1f, 1f, clamp(0.28f * ci))
        i += 1
      }
    }

    // Fully charged: ready-pulse breathing outward
    if (pct >= 0.99f) {
      val rp = (t * 0.04f) % 1f
      shapeBatch.strokeOval(x, y, 18f + rp * 16f, 14f + rp * 12f, 2f * (1f - rp),
        1f, 1f, 0.9f, 0.32f * (1f - rp), 16)
    }
  }

  /**
   * Phased — a void rim (dark centre, glowing edge) with a displaced phase echo sliding
   * through it, scanline bands drifting up the body, and wisps peeling off the silhouette.
   */
  private def drawPhasedEffectInner(cx: Double, cy: Double): Unit = {
    val x = cx.toFloat; val y = cy.toFloat
    val t = animTickF
    val shimmer = (0.5 + 0.5 * Math.sin(t * 0.12)).toFloat

    // Rim only — a filled body would light the whole silhouette additively and bury the
    // sprite underneath. A thin band leaves the middle clear, so they read as see-through.
    val ra = 0.13f + 0.07f * shimmer
    shapeBatch.fillArcBand(x, y, 15f, 18f, 18f, 21.5f, 0f, 6.2831853f, 20,
      0.5f, 0.3f, 0.85f, ra, ra)

    // Phase echo — two chromatically split outlines sliding apart and back
    val ex = Math.sin(t * 0.09f).toFloat * 5f
    val ea = 0.10f * (0.5f + shimmer * 0.5f)
    shapeBatch.strokeOval(x + ex, y, 11f, 17f, 1.4f, 0.6f, 0.4f, 1f, ea, 14)
    shapeBatch.strokeOval(x - ex, y, 11f, 17f, 1.4f, 0.35f, 0.65f, 1f, ea * 0.85f, 14)

    // Scanline bands travelling up the body
    var i = 0
    while (i < 4) {
      val bp = (t * 0.03f + i * 0.25f) % 1f
      val env = Math.sin(bp * 3.1415927f).toFloat
      val bw = 15f * (0.45f + 0.55f * env)
      shapeBatch.fillRect(x - bw, y + 20f - bp * 42f, bw * 2f, 1.4f, 0.75f, 0.6f, 1f, 0.15f * env)
      i += 1
    }

    // Wisps dissolving off the edge
    i = 0
    while (i < 5) {
      val wp = (t * 0.025f + i * 0.2f) % 1f
      val wa = i * 1.2566371f + t * 0.02f
      val wx = x + Math.cos(wa).toFloat * (9f + wp * 12f)
      val wy = y - wp * 16f + Math.sin(wa).toFloat * 6f
      val ws = 1f - wp * 0.5f
      shapeBatch.fillOvalSoft(wx, wy, 2.5f * ws, 3.5f * ws, 0.6f, 0.45f, 1f, 0.16f * (1f - wp), 0f, 6)
      i += 1
    }
  }

  private def drawBurnEffectInner(cx: Double, cy: Double): Unit = {
    drawBurnEffectCore(cx.toFloat, cy.toFloat, animationTick)
  }

  private def drawHitEffectInner(cx: Double, cy: Double, hitTime: Long,
                                 hr: Float, hg: Float, hb: Float,
                                 ddx: Float, ddy: Float): Unit = {
    if (hitTime <= 0) return
    val elapsed = frameTimeMs - hitTime
    if (elapsed > HIT_ANIMATION_MS) return
    val progress = elapsed.toDouble / HIT_ANIMATION_MS
    val fadeOut = (1.0 - progress).toFloat
    drawHitEffectCore(cx.toFloat, cy.toFloat, progress, fadeOut, hitTime, hr, hg, hb, ddx, ddy)
  }

  /**
   * Cast flash — a collapsing core, an expanding shockwave band, radial spikes of
   * alternating length and sparks thrown clear. 200ms, so it has to land in a few frames.
   */
  private def drawCastFlashInner(cx: Double, cy: Double): Unit = {
    val castTime = client.getLastCastTime
    if (castTime <= 0) return
    val elapsed = frameTimeMs - castTime
    if (elapsed > 200) return
    val x = cx.toFloat; val y = cy.toFloat
    val prog = (elapsed / 200.0).toFloat
    val fade = 1f - prog

    // Collapsing core
    val coreS = 1f - prog * 0.6f
    shapeBatch.fillOvalSoft(x, y, 15f * coreS, 11f * coreS, 1f, 1f, 0.85f, 0.32f * fade, 0f, 14)

    // Expanding shockwave, thinning as it goes
    val rr = 8f + prog * 26f
    val rw = 3f * fade
    shapeBatch.fillArcBand(x, y, rr, rr * 0.72f, rr + rw, (rr + rw) * 0.72f,
      0f, 6.2831853f, 16, 1f, 0.95f, 0.7f, 0.28f * fade, 0.28f * fade)

    // Radial spikes
    var i = 0
    while (i < 8) {
      val ang = i * 0.7853982f + 0.2f
      val len = (14f + (if ((i & 1) == 0) 10f else 0f)) * (0.4f + prog * 1.1f)
      val ca = Math.cos(ang).toFloat; val sa = Math.sin(ang).toFloat * 0.7f
      shapeBatch.strokeLineSoft(x + ca * 4f, y + sa * 4f, x + ca * len, y + sa * len,
        2.6f * fade, 1f, 0.97f, 0.8f, 0.32f * fade)
      i += 1
    }

    // Sparks outrunning the ring
    i = 0
    while (i < 6) {
      val ang = i * 1.0471976f + 0.5f
      val d = 10f + prog * 30f
      shapeBatch.fillDot(x + Math.cos(ang).toFloat * d, y + Math.sin(ang).toFloat * d * 0.7f,
        1.6f * fade, 1f, 1f, 0.9f, 0.38f * fade)
      i += 1
    }
  }

  /** Queue a player's glowing effects (the FX_ flags) for the additive pass ([[drawDeferred]]), with
    * what a hit's flash is made of: the colour of whatever last struck them (their own if nothing
    * has) and which way on screen it was going. */
  def deferAdditive(screenX: Double, spriteCenter: Double, fxFlags: Int, chargeLevel: Int, hitTime: Long,
                    playerId: UUID, ownColorRGB: Int): Unit = {
    val di = _dafCount
    if (di < MAX_DEFERRED_ADD_FX) {
      _dafCX(di) = screenX; _dafCY(di) = spriteCenter
      _dafFlags(di) = fxFlags; _dafChargeLevel(di) = chargeLevel
      _dafHitTime(di) = hitTime
      // Themed hit FX inputs — color and screen-space direction of the hit projectile
      val hitColorRGB = client.getPlayerHitColorRGB(playerId)
      intToRGB(if (hitColorRGB != 0) hitColorRGB else ownColorRGB)
      _dafHitColorR(di) = _rgb_r; _dafHitColorG(di) = _rgb_g; _dafHitColorB(di) = _rgb_b
      val hDx = client.getPlayerHitDx(playerId).toDouble
      val hDy = client.getPlayerHitDy(playerId).toDouble
      // Convert world dx/dy to screen-space using same isometric mapping (2:1, dy halved)
      val sdx = (hDx - hDy).toFloat
      val sdy = ((hDx + hDy) * 0.5f).toFloat
      val sLen = Math.sqrt(sdx * sdx + sdy * sdy).toFloat
      if (sLen > 0.01f) {
        _dafHitDirX(di) = sdx / sLen
        _dafHitDirY(di) = sdy / sLen
      } else {
        _dafHitDirX(di) = 1f; _dafHitDirY(di) = 0f
      }
      _dafCount += 1
    }
  }

  def beginFrame(): Unit = _dafCount = 0

  /** Whether any player's glow was deferred this frame. */
  def hasDeferred: Boolean = _dafCount != 0

  /** Every player's deferred glow, in the additive blend the renderer has set (see GLGameRenderer). */
  def drawDeferred(): Unit = {
    var i = 0
    while (i < _dafCount) {
      val cx = _dafCX(i); val cy = _dafCY(i); val flags = _dafFlags(i)
      if ((flags & FX_GEM) != 0) drawGemGlowInner(cx, cy)
      if ((flags & FX_CHARGE) != 0) drawChargingEffectInner(cx, cy, _dafChargeLevel(i))
      if ((flags & FX_PHASED) != 0) drawPhasedEffectInner(cx, cy)
      if ((flags & FX_BURN) != 0) drawBurnEffectInner(cx, cy)
      if ((flags & FX_HIT) != 0) drawHitEffectInner(cx, cy, _dafHitTime(i),
        _dafHitColorR(i), _dafHitColorG(i), _dafHitColorB(i),
        _dafHitDirX(i), _dafHitDirY(i))
      if ((flags & FX_CAST) != 0) drawCastFlashInner(cx, cy)
      i += 1
    }
    _dafCount = 0
  }
}
