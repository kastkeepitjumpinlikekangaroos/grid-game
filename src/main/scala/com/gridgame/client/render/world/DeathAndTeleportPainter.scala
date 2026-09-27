package com.gridgame.client.render
package world

import com.gridgame.common.Constants
import com.gridgame.common.model._

/**
 * A player dying (a burst, a spirit rising, what they leave behind) and teleporting (a departure where
 * they were and an arrival where they land), for as long as each animation lasts.
 */
private[render] final class DeathAndTeleportPainter(ctx: RenderContext, particles: ParticleSpawner) {
  import ctx._

  private val TELEPORT_ANIMATION_MS = 800

  // Track death IDs that have already spawned a particle burst
  private val deathBurstSpawned = new java.util.HashSet[Any]()

  def drawDeaths(): Unit = {
    val now = frameTimeMs
    val iter = client.getDeathAnimations.entrySet().iterator()
    // Clean up old death burst tracking — use Java iterator to avoid lambda allocation
    val burstIter = deathBurstSpawned.iterator()
    while (burstIter.hasNext) {
      if (!client.getDeathAnimations.containsKey(burstIter.next())) burstIter.remove()
    }
    while (iter.hasNext) {
      val entry = iter.next()
      val key = entry.getKey
      val data = entry.getValue
      val deathTime = data(0)
      if (now - deathTime > DEATH_ANIMATION_MS) {
        iter.remove()
        deathBurstSpawned.remove(key)
      } else {
        val wx = data(1).toDouble; val wy = data(2).toDouble
        val colorRGB = data(3).toInt
        val charId = if (data.length > 4) data(4).toByte else 0.toByte
        // Spawn death burst particles on first frame
        if (!deathBurstSpawned.contains(key)) {
          deathBurstSpawned.add(key)
          particles.spawnDeathBurst(wx.toFloat, wy.toFloat, colorRGB)
        }
        drawDeathEffect(worldToScreenX(wx, wy), worldToScreenY(wx, wy), colorRGB, deathTime, charId)
      }
    }
  }

  private def drawDeathEffect(screenX: Double, screenY: Double, colorRGB: Int, deathTime: Long, characterId: Byte = 0): Unit = {
    if (deathTime <= 0) return
    val elapsed = frameTimeMs - deathTime
    if (elapsed < 0 || elapsed > DEATH_ANIMATION_MS) return

    val progress = elapsed.toDouble / DEATH_ANIMATION_MS
    val fadeOut = (1.0 - progress).toFloat
    intToRGB(colorRGB)
    val cr = _rgb_r; val cg = _rgb_g; val cb = _rgb_b
    val displaySz = Constants.PLAYER_DISPLAY_SIZE_PX
    val cx = screenX.toFloat; val cy = (screenY - displaySz / 2.0).toFloat

    // Dynamic lighting — bright flash that fades over the animation
    val lightRadius = (120f * (1f - progress * 0.8f)).toFloat
    val lightIntensity = (0.5f * fadeOut).toFloat
    lightSystem.addLight(cx, cy, lightRadius, cr, cg, cb, lightIntensity)

    beginShapes()

    // ── Phase 1 (0-20%): White-hot 3-layer flash ──
    if (progress < 0.2) {
      val flashP = (progress / 0.2).toFloat
      val flashGrow = 0.4f + flashP * 0.6f
      val flashFade = 1f - flashP
      shapeBatch.setAdditiveBlend(true)
      // Layer 1: Huge white-hot outer glow (60px)
      shapeBatch.fillOvalSoft(cx, cy, 60f * flashGrow, 30f * flashGrow,
        1f, 1f, 0.95f, 0.5f * flashFade, 0f, 20);
      // Layer 2: Colored mid glow (45px)
      shapeBatch.fillOvalSoft(cx, cy, 45f * flashGrow, 22f * flashGrow,
        bright(cr), bright(cg), bright(cb), 0.65f * flashFade, 0f, 18);
      // Layer 3: Bright white center (20px)
      shapeBatch.fillOval(cx, cy, 20f * flashGrow, 10f * flashGrow,
        1f, 1f, 1f, 0.8f * flashFade, 12)
      shapeBatch.setAdditiveBlend(false)
    }

    // ── Phase 2 (0-40%): Dual expanding shockwave rings with dark outlines ──
    if (progress < 0.4) {
      val ringP = (progress / 0.4).toFloat
      val ringFade = 1f - ringP

      // Ground ripple — dark ellipse beneath
      val rippleR = 20f + ringP * 40f
      shapeBatch.fillOval(cx, cy + 4f, rippleR, rippleR * 0.3f,
        0.05f, 0.02f, 0.05f, 0.3f * ringFade, 20);

      // Outer ring (starts immediately)
      val r1W = ringP * 55f
      val r1H = r1W * 0.5f
      // Dark outline
      shapeBatch.strokeOval(cx, cy, r1W + 2f, r1H + 1f, 5f * ringFade,
        0.05f, 0.02f, 0.02f, 0.7f * ringFade, 28);
      // Colored body
      shapeBatch.strokeOval(cx, cy, r1W, r1H, 4f * ringFade,
        cr, cg, cb, 0.8f * ringFade, 28);
      // Bright inner edge
      shapeBatch.strokeOval(cx, cy, r1W - 1f, r1H - 0.5f, 1.5f * ringFade,
        bright(cr), bright(cg), bright(cb), 0.6f * ringFade, 28)

      // Inner ring (delayed slightly)
      if (progress > 0.05) {
        val r2P = ((progress - 0.05) / 0.35).toFloat
        val r2Fade = 1f - r2P
        val r2W = r2P * 40f
        val r2H = r2W * 0.5f
        shapeBatch.strokeOval(cx, cy, r2W + 1.5f, r2H + 0.75f, 4f * r2Fade,
          0.05f, 0.02f, 0.02f, 0.5f * r2Fade, 24);
        shapeBatch.strokeOval(cx, cy, r2W, r2H, 3f * r2Fade,
          bright(cr), bright(cg), bright(cb), 0.65f * r2Fade, 24)
      }
    }

    // ── Phase 3 (0-80%): Soul fragment particles — 20 in 3 tiers with trails ──
    if (progress < 0.8) {
      val partP = (progress / 0.8).toFloat
      val partFade = 1f - partP
      shapeBatch.setAdditiveBlend(true)
      var i = 0
      while (i < 20) {
        val tier = i % 3 // 0=large bright, 1=medium colored, 2=small dim
        val pAngle = i * (2 * Math.PI / 20) + i * 0.47
        val baseSpeed = if (tier == 0) 1.0 else if (tier == 1) 0.75 else 0.55
        val gravity = if (tier == 0) 30.0 else if (tier == 1) 22.0 else 15.0
        val dist = (partP * (30 + (i % 5) * 10) * baseSpeed).toFloat
        val rise = (partP * partP * gravity * (0.8 + (i % 4) * 0.1)).toFloat
        val px = cx + dist * Math.cos(pAngle).toFloat
        val py = cy + dist * Math.sin(pAngle).toFloat * 0.5f - rise

        // Particle color and size per tier
        var pr = 0f; var pg = 0f; var pb = 0f; var pSize = 0f; var pAlpha = 0f
        if (tier == 0) { pr = 1f; pg = 1f; pb = 0.9f; pSize = 4f * partFade; pAlpha = 0.8f * partFade }
        else if (tier == 1) { pr = clamp(cr * 0.4f + 0.6f); pg = clamp(cg * 0.4f + 0.6f); pb = clamp(cb * 0.4f + 0.6f); pSize = 3f * partFade; pAlpha = 0.7f * partFade }
        else { pr = cr; pg = cg; pb = cb; pSize = 2f * partFade; pAlpha = 0.55f * partFade }

        // Trail segments (2 segments behind the particle)
        if (partP > 0.05f) {
          val trailDist1 = dist * 0.75f
          val trailRise1 = rise * 0.75f
          val tx1 = cx + trailDist1 * Math.cos(pAngle).toFloat
          val ty1 = cy + trailDist1 * Math.sin(pAngle).toFloat * 0.5f - trailRise1
          shapeBatch.fillOval(tx1, ty1, pSize * 0.6f, pSize * 0.6f, pr, pg, pb, pAlpha * 0.4f, 6);
          val trailDist2 = dist * 0.5f
          val trailRise2 = rise * 0.5f
          val tx2 = cx + trailDist2 * Math.cos(pAngle).toFloat
          val ty2 = cy + trailDist2 * Math.sin(pAngle).toFloat * 0.5f - trailRise2
          shapeBatch.fillOval(tx2, ty2, pSize * 0.35f, pSize * 0.35f, pr, pg, pb, pAlpha * 0.2f, 6)
        }

        // Dark outline for particle
        shapeBatch.fillOval(px, py, pSize + 1f, pSize + 1f, 0.02f, 0.01f, 0.02f, pAlpha * 0.5f, 8);
        // Particle body
        shapeBatch.fillOval(px, py, pSize, pSize, pr, pg, pb, pAlpha, 8)
        i += 1
      }
      shapeBatch.setAdditiveBlend(false)
    }

    // ── Phase 4 (10-70%): Dissolve ring with crack lines ──
    if (progress > 0.1 && progress < 0.7) {
      val dissP = ((progress - 0.1) / 0.6).toFloat
      val dissFade = 1f - dissP
      val dissR = 10f + dissP * 35f
      // Dark dissolve ring
      shapeBatch.strokeOval(cx, cy, dissR, dissR * 0.5f, 3f * dissFade,
        0.08f, 0.03f, 0.08f, 0.6f * dissFade, 24)

      // Crack lines radiating from center (6 lines)
      var c = 0
      while (c < 6) {
        val cAngle = c * (Math.PI / 3.0) + 0.3
        val crackLen = (8f + dissP * 22f)
        val outerX = cx + crackLen * Math.cos(cAngle).toFloat
        val outerY = cy + crackLen * Math.sin(cAngle).toFloat * 0.5f
        // Dark line
        shapeBatch.strokeLine(cx, cy, outerX, outerY, 2.5f * dissFade,
          0.05f, 0.02f, 0.05f, 0.5f * dissFade);
        // Bright edge
        shapeBatch.strokeLine(cx, cy, outerX, outerY, 1f * dissFade,
          bright(cr), bright(cg), bright(cb), 0.35f * dissFade)
        c += 1
      }
    }

    // ── Phase 5 (0-100%): Rising ghost sprite with glow aura + soul wisps ──
    // Soul wisps drawn under the ghost (in shape batch)
    shapeBatch.setAdditiveBlend(true)
    // Glowing aura around ghost position
    val ghostRise = (progress * 40).toFloat
    val ghostCy = cy - ghostRise
    val auraAlpha = fadeOut * 0.3f
    if (auraAlpha > 0.02f) {
      shapeBatch.fillOvalSoft(cx, ghostCy, 22f, 28f,
        cr, cg, cb, auraAlpha, 0f, 16)
    }

    // 6 soul wisps rising with sinusoidal drift
    var w = 0
    while (w < 6) {
      val wPhase = w * (Math.PI / 3.0) + progress * 4.0
      val wRise = (progress * (20 + w * 8)).toFloat
      val wDrift = (Math.sin(wPhase) * 8).toFloat
      val wX = cx + wDrift
      val wY = cy - wRise
      val wAlpha = fadeOut * (0.4f - w * 0.04f)
      val wSize = (2.5f - w * 0.2f) * fadeOut
      if (wAlpha > 0.02f) {
        shapeBatch.fillOval(wX, wY, wSize, wSize * 1.4f,
          bright(cr), bright(cg), bright(cb), wAlpha, 8);
        // Wisp trail
        shapeBatch.fillOval(wX, wY + wSize * 2f, wSize * 0.5f, wSize * 0.8f,
          cr, cg, cb, wAlpha * 0.3f, 6)
      }
      w += 1
    }
    shapeBatch.setAdditiveBlend(false)

    // Ghost sprite rendering
    val ghostAlpha = Math.max(0f, fadeOut * 0.6f)
    val region = GLSpriteGenerator.getSpriteRegion(Direction.Down, 0, characterId)
    if (region != null) {
      beginSprites()
      spriteBatch.draw(region,
        (screenX - displaySz / 2.0).toFloat, (screenY - displaySz - ghostRise).toFloat,
        displaySz.toFloat, displaySz.toFloat, 1f, 1f, 1f, ghostAlpha)
    }
  }

  def drawTeleports(): Unit = {
    val now = frameTimeMs
    val iter = client.getTeleportAnimations.entrySet().iterator()
    while (iter.hasNext) {
      val entry = iter.next()
      val data = entry.getValue
      val timestamp = data(0)
      if (now - timestamp > TELEPORT_ANIMATION_MS) {
        iter.remove()
      } else {
        val oldX = data(1).toDouble; val oldY = data(2).toDouble
        val newX = data(3).toDouble; val newY = data(4).toDouble
        val colorRGB = data(5).toInt
        drawTeleportDeparture(worldToScreenX(oldX, oldY).toFloat, worldToScreenY(oldX, oldY).toFloat, timestamp, colorRGB)
        drawTeleportArrival(worldToScreenX(newX, newY).toFloat, worldToScreenY(newX, newY).toFloat, timestamp, colorRGB)
      }
    }
  }

  private def drawTeleportDeparture(sx: Float, sy: Float, startTime: Long, colorRGB: Int = 0xFFD700): Unit = {
    val elapsed = frameTimeMs - startTime
    if (elapsed > TELEPORT_ANIMATION_MS) return
    val progress = elapsed.toDouble / TELEPORT_ANIMATION_MS
    val fadeOut = Math.max(0f, (1.0 - progress * 1.3).toFloat)
    intToRGB(colorRGB)
    val cr = _rgb_r; val cg = _rgb_g; val cb = _rgb_b

    // Dynamic light — golden-white intensifying then snapping off
    val lightInt = if (progress < 0.7) (0.4f * (progress / 0.7).toFloat) else 0f
    lightSystem.addLight(sx, sy, 80f * (1f - progress.toFloat * 0.5f), bright(cr), bright(cg), bright(cb), lightInt)

    beginShapes()

    // ── Ground rune circle: expanding then shrinking magic circle ──
    val runeScale = if (progress < 0.5) (progress / 0.5).toFloat else (1f - ((progress - 0.5) / 0.5).toFloat)
    val runeR = 28f * runeScale
    val runeAlpha = 0.6f * runeScale
    if (runeAlpha > 0.02f) {
      // Outer ring (dark outline + colored body)
      shapeBatch.strokeOval(sx, sy + 4f, runeR + 1.5f, runeR * 0.35f + 1f, 3f,
        0.05f, 0.03f, 0.05f, runeAlpha * 0.7f, 24);
      shapeBatch.strokeOval(sx, sy + 4f, runeR, runeR * 0.35f, 2f,
        bright(cr), bright(cg), bright(cb), runeAlpha, 24);
      // Inner ring
      shapeBatch.strokeOval(sx, sy + 4f, runeR * 0.6f, runeR * 0.21f, 1.5f,
        cr, cg, cb, runeAlpha * 0.5f, 20)

      // 6 rune marks orbiting the circle
      var rm = 0
      while (rm < 6) {
        val rmAngle = rm * (Math.PI / 3.0) + progress * 6.0
        val rmX = sx + (runeR * 0.8f * Math.cos(rmAngle)).toFloat
        val rmY = sy + 4f + (runeR * 0.28f * Math.sin(rmAngle)).toFloat
        val rmSz = 2.5f * runeScale
        shapeBatch.fillRect(rmX - rmSz * 0.5f, rmY - rmSz * 0.5f, rmSz, rmSz,
          bright(cr), bright(cg), bright(cb), runeAlpha * 0.8f)
        rm += 1
      }
    }

    // ── Vertical energy pillar: 8 layered horizontal rings stacking vertically ──
    shapeBatch.setAdditiveBlend(true)
    var ring = 0
    while (ring < 8) {
      val ringFrac = ring / 8f
      val yOff = -ringFrac * 55f // rings stack upward
      val converge = progress.toFloat // rings converge inward as progress increases
      val ringW = (18f - converge * 14f) * (1f - ringFrac * 0.3f)
      val ringH = ringW * 0.35f
      val ringAlpha = fadeOut * (0.55f - ringFrac * 0.04f)
      val ry = sy + yOff
      if (ringAlpha > 0.02f) {
        // Dark outline
        shapeBatch.strokeOval(sx, ry, ringW + 1.5f, ringH + 0.75f, 2.5f,
          0.03f, 0.01f, 0.03f, ringAlpha * 0.6f, 16);
        // Colored body
        shapeBatch.strokeOval(sx, ry, ringW, ringH, 2f,
          cr, cg, cb, ringAlpha, 16);
        // Bright core
        shapeBatch.strokeOval(sx, ry, ringW * 0.6f, ringH * 0.6f, 1f,
          bright(cr), bright(cg), bright(cb), ringAlpha * 0.7f, 12)
      }
      ring += 1
    }

    // ── 4 lightning bolts crackling around departure ──
    var bolt = 0
    while (bolt < 4) {
      val bAngle = bolt * (Math.PI / 2.0) + progress * 8.0
      val bDist = 12f + 8f * fadeOut
      val bx = sx + (bDist * Math.cos(bAngle)).toFloat
      val by = sy - 10f + (bDist * 0.4f * Math.sin(bAngle)).toFloat
      // 3-segment zigzag bolt
      var seg = 0
      var segX = bx; var segY = by
      while (seg < 3) {
        val jitterX = ((((bolt * 7 + seg * 13 + animationTick) % 17) - 8) * 1.2f).toFloat
        val jitterY = -6f - ((((bolt * 11 + seg * 5 + animationTick) % 11) - 3) * 0.8f).toFloat
        val nextX = segX + jitterX
        val nextY = segY + jitterY
        val boltAlpha = fadeOut * 0.7f
        // Dark outline
        shapeBatch.strokeLine(segX, segY, nextX, nextY, 3f, 0.02f, 0.01f, 0.02f, boltAlpha * 0.5f);
        // Colored body
        shapeBatch.strokeLine(segX, segY, nextX, nextY, 2f, cr, cg, cb, boltAlpha);
        // White core
        shapeBatch.strokeLine(segX, segY, nextX, nextY, 0.8f, 1f, 1f, 1f, boltAlpha * 0.8f)
        segX = nextX; segY = nextY
        seg += 1
      }
      bolt += 1
    }

    // ── Spiral particle vortex: 12 particles spiraling inward with trails ──
    var p = 0
    while (p < 12) {
      val spiralAngle = p * (Math.PI * 2.0 / 12) + progress * 10.0
      val spiralR = (30f * (1f - progress.toFloat) * (0.6f + (p % 3) * 0.15f))
      val px = sx + (spiralR * Math.cos(spiralAngle)).toFloat
      val py = sy + (spiralR * 0.4f * Math.sin(spiralAngle)).toFloat - progress.toFloat * 15f
      val pAlpha = fadeOut * 0.65f
      val pSize = 2.5f * fadeOut

      // Trail
      if (pAlpha > 0.02f) {
        val trailAngle = spiralAngle - 0.4
        val trailR = spiralR * 1.15f
        val tx = sx + (trailR * Math.cos(trailAngle)).toFloat
        val ty = sy + (trailR * 0.4f * Math.sin(trailAngle)).toFloat - progress.toFloat * 14f
        shapeBatch.fillOval(tx, ty, pSize * 0.5f, pSize * 0.5f, cr, cg, cb, pAlpha * 0.3f, 6)
      }
      // Particle
      shapeBatch.fillOval(px, py, pSize, pSize, bright(cr), bright(cg), bright(cb), pAlpha, 8)
      p += 1
    }

    // ── Bright implosion flash at end (progress > 0.7) ──
    if (progress > 0.7) {
      val impP = ((progress - 0.7) / 0.3).toFloat
      val impAlpha = 0.8f * impP
      val impR = 20f * (1f - impP * 0.5f)
      shapeBatch.fillOvalSoft(sx, sy - 15f, impR, impR * 0.6f,
        1f, 1f, 1f, impAlpha, 0f, 16);
      shapeBatch.fillOval(sx, sy - 15f, impR * 0.4f, impR * 0.25f,
        1f, 1f, 0.95f, impAlpha * 0.9f, 12)
    }

    shapeBatch.setAdditiveBlend(false)
  }

  private def drawTeleportArrival(sx: Float, sy: Float, startTime: Long, colorRGB: Int = 0xFFD700): Unit = {
    val elapsed = frameTimeMs - startTime
    if (elapsed < 200 || elapsed > TELEPORT_ANIMATION_MS) return
    val progress = (elapsed - 200).toDouble / (TELEPORT_ANIMATION_MS - 200)
    val fadeOut = (1.0 - progress).toFloat
    intToRGB(colorRGB)
    val cr = _rgb_r; val cg = _rgb_g; val cb = _rgb_b

    // Dynamic light — bright flash that fades
    val lightInt = (0.5f * fadeOut).toFloat
    lightSystem.addLight(sx, sy, 100f * fadeOut, bright(cr), bright(cg), bright(cb), lightInt)

    beginShapes()

    // ── Arrival flash at start (progress < 0.2): Massive 3-layer flash ──
    if (progress < 0.2) {
      val flashP = (progress / 0.2).toFloat
      val flashFade = 1f - flashP
      shapeBatch.setAdditiveBlend(true)
      // Layer 1: Huge outer glow (80px)
      shapeBatch.fillOvalSoft(sx, sy - 10f, 80f * flashFade, 40f * flashFade,
        1f, 1f, 0.95f, 0.55f * flashFade, 0f, 20);
      // Layer 2: Colored mid glow
      shapeBatch.fillOvalSoft(sx, sy - 10f, 50f * flashFade, 25f * flashFade,
        bright(cr), bright(cg), bright(cb), 0.7f * flashFade, 0f, 16);
      // Layer 3: White-hot center
      shapeBatch.fillOval(sx, sy - 10f, 20f * flashFade, 10f * flashFade,
        1f, 1f, 1f, 0.85f * flashFade, 12)
      shapeBatch.setAdditiveBlend(false)
    }

    // ── Vertical energy pillar: rings expanding outward from center ──
    shapeBatch.setAdditiveBlend(true)
    if (progress < 0.6) {
      val pillarP = (progress / 0.6).toFloat
      var ring = 0
      while (ring < 8) {
        val ringFrac = ring / 8f
        val yOff = -ringFrac * 55f
        val expand = pillarP // rings expand outward
        val ringW = (4f + expand * 16f) * (1f - ringFrac * 0.2f)
        val ringH = ringW * 0.35f
        val ringAlpha = (1f - pillarP) * (0.55f - ringFrac * 0.04f)
        val ry = sy + yOff
        if (ringAlpha > 0.02f) {
          shapeBatch.strokeOval(sx, ry, ringW + 1f, ringH + 0.5f, 2.5f,
            0.03f, 0.01f, 0.03f, ringAlpha * 0.5f, 16);
          shapeBatch.strokeOval(sx, ry, ringW, ringH, 2f,
            cr, cg, cb, ringAlpha, 16);
          shapeBatch.strokeOval(sx, ry, ringW * 0.5f, ringH * 0.5f, 1f,
            bright(cr), bright(cg), bright(cb), ringAlpha * 0.6f, 12)
        }
        ring += 1
      }
    }

    // ── Expanding shockwave ring with dark outline ──
    if (progress < 0.7) {
      val ringP = (progress / 0.7).toFloat
      val ringFade = 1f - ringP
      val ringW = ringP * 50f
      val ringH = ringW * 0.5f
      shapeBatch.setAdditiveBlend(false)
      // Dark outline
      shapeBatch.strokeOval(sx, sy, ringW + 2f, ringH + 1f, 4.5f * ringFade,
        0.04f, 0.02f, 0.04f, 0.6f * ringFade, 28);
      // Colored body
      shapeBatch.strokeOval(sx, sy, ringW, ringH, 3.5f * ringFade,
        cr, cg, cb, 0.75f * ringFade, 28);
      // Bright inner edge
      shapeBatch.setAdditiveBlend(true)
      shapeBatch.strokeOval(sx, sy, ringW - 1f, ringH - 0.5f, 1.5f * ringFade,
        bright(cr), bright(cg), bright(cb), 0.5f * ringFade, 28)
    }

    // ── Ground impact cracks: 6 lines radiating from center ──
    shapeBatch.setAdditiveBlend(false)
    if (progress < 0.6) {
      val crackP = (progress / 0.6).toFloat
      val crackFade = 1f - crackP
      var c = 0
      while (c < 6) {
        val cAngle = c * (Math.PI / 3.0) + 0.5
        val crackLen = 10f + crackP * 30f
        val outerX = sx + (crackLen * Math.cos(cAngle)).toFloat
        val outerY = sy + (crackLen * Math.sin(cAngle) * 0.5).toFloat
        // Dark crack
        shapeBatch.strokeLine(sx, sy, outerX, outerY, 2.5f * crackFade,
          0.06f, 0.03f, 0.06f, 0.55f * crackFade);
        // Bright edge
        shapeBatch.strokeLine(sx, sy, outerX, outerY, 1f * crackFade,
          bright(cr), bright(cg), bright(cb), 0.4f * crackFade)
        c += 1
      }
    }

    // ── Scatter particles: 12 particles exploding outward with gravity and trails ──
    shapeBatch.setAdditiveBlend(true)
    var p = 0
    while (p < 12) {
      val pAngle = p * (Math.PI * 2.0 / 12) + p * 0.35
      val speed = 0.7 + (p % 4) * 0.15
      val dist = (progress * (25 + (p % 4) * 10) * speed).toFloat
      val gravity = (progress * progress * 20 * (0.7 + (p % 3) * 0.15)).toFloat
      val px = sx + dist * Math.cos(pAngle).toFloat
      val py = sy + dist * Math.sin(pAngle).toFloat * 0.5f + gravity
      val pSize = (3f - (p % 3) * 0.5f) * fadeOut
      val pAlpha = fadeOut * (0.7f - (p % 3) * 0.1f)

      if (pAlpha > 0.02f) {
        // Trail
        val tDist = dist * 0.7f
        val tGrav = gravity * 0.7f
        val tx = sx + tDist * Math.cos(pAngle).toFloat
        val ty = sy + tDist * Math.sin(pAngle).toFloat * 0.5f + tGrav
        shapeBatch.fillOval(tx, ty, pSize * 0.4f, pSize * 0.4f, cr, cg, cb, pAlpha * 0.3f, 6);
        // Dark outline
        shapeBatch.fillOval(px, py, pSize + 1f, pSize + 1f, 0.02f, 0.01f, 0.02f, pAlpha * 0.4f, 8);
        // Particle body
        shapeBatch.fillOval(px, py, pSize, pSize, bright(cr), bright(cg), bright(cb), pAlpha, 8)
      }
      p += 1
    }

    // ── Residual sparkles: 8 sparkle stars that pop and fade ──
    var s = 0
    while (s < 8) {
      val sparkDelay = s * 0.1
      val sparkLife = progress - sparkDelay
      if (sparkLife > 0 && sparkLife < 0.5) {
        val sparkP = (sparkLife / 0.5).toFloat
        val sparkFade = if (sparkP < 0.3f) sparkP / 0.3f else (1f - sparkP) / 0.7f
        val sAngle = s * (Math.PI * 2.0 / 8) + s * 1.1
        val sDist = 15f + s * 5f
        val sparkX = sx + (sDist * Math.cos(sAngle)).toFloat
        val sparkY = sy + (sDist * Math.sin(sAngle) * 0.5).toFloat - sparkP * 8f
        val sparkSz = 3f * sparkFade
        // 4-pointed star (two crossed lines)
        shapeBatch.strokeLine(sparkX - sparkSz, sparkY, sparkX + sparkSz, sparkY,
          1.5f, 1f, 1f, 1f, 0.7f * sparkFade);
        shapeBatch.strokeLine(sparkX, sparkY - sparkSz, sparkX, sparkY + sparkSz,
          1.5f, 1f, 1f, 1f, 0.7f * sparkFade);
        // Diagonal cross
        val dSz = sparkSz * 0.6f
        shapeBatch.strokeLine(sparkX - dSz, sparkY - dSz, sparkX + dSz, sparkY + dSz,
          1f, bright(cr), bright(cg), bright(cb), 0.5f * sparkFade);
        shapeBatch.strokeLine(sparkX - dSz, sparkY + dSz, sparkX + dSz, sparkY - dSz,
          1f, bright(cr), bright(cg), bright(cb), 0.5f * sparkFade)
      }
      s += 1
    }
    shapeBatch.setAdditiveBlend(false)
  }

  def reset(): Unit = deathBurstSpawned.clear()
}
