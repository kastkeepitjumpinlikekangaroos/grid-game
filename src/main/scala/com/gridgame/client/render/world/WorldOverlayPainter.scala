package com.gridgame.client.render
package world

import com.gridgame.common.Constants

import java.util.UUID

/**
 * Name plates, health bars and damage numbers: noted during the entity pass and drawn over the
 * finished frame at the display's full resolution, so the grade and the scene's scale never touch
 * them. Damage is noticed here too, as health drops between frames ([[detectDamageNumbers]]).
 */
private[render] final class WorldOverlayPainter(ctx: RenderContext, particles: ParticleSpawner) {
  import ctx._

  // Previous health tracking for damage number detection — Java HashMap avoids Option wrapping
  private val prevHealthMap = new java.util.HashMap[UUID, java.lang.Integer]()

  // Smooth health drain animation tracking — a one-element array per player, updated in
  // place; a java.lang.Float value was boxed afresh for every bar every frame
  private val smoothHealthMap = new java.util.HashMap[UUID, Array[Float]]()

  // Cached closures for damage number rendering (avoids per-frame lambda allocation)
  private val _worldToScreenXFn: (Double, Double) => Double = (wx, wy) => worldToScreenX(wx, wy)
  private val _worldToScreenYFn: (Double, Double) => Double = (wx, wy) => worldToScreenY(wx, wy)
  private val _indicatorXs = new Array[Float](4)
  private val _indicatorYs = new Array[Float](4)

  // Deferred health bars + names (batched after entity dispatch to reduce batch switches)
  private val MAX_DEFERRED_BARS = 32
  private val _deferBarCX = new Array[Double](MAX_DEFERRED_BARS) // screenCenterX
  private val _deferBarTY = new Array[Double](MAX_DEFERRED_BARS) // spriteTopY
  private val _deferBarHP = new Array[Int](MAX_DEFERRED_BARS)    // health
  private val _deferBarMax = new Array[Int](MAX_DEFERRED_BARS)   // maxHealth
  private val _deferBarTeam = new Array[Byte](MAX_DEFERRED_BARS) // teamId
  private val _deferBarId = new Array[UUID](MAX_DEFERRED_BARS)   // playerId
  private val _deferBarName = new Array[String](MAX_DEFERRED_BARS) // charName
  private var _deferBarCount = 0

  def deferHealthBar(cx: Double, topY: Double, hp: Int, maxHp: Int, team: Byte, pid: UUID, name: String): Unit = {
    if (_deferBarCount >= MAX_DEFERRED_BARS) return
    val i = _deferBarCount
    _deferBarCX(i) = cx
    _deferBarTY(i) = topY
    _deferBarHP(i) = hp
    _deferBarMax(i) = maxHp
    _deferBarTeam(i) = team
    _deferBarId(i) = pid
    _deferBarName(i) = name
    _deferBarCount += 1
  }

  private def flushDeferredBars(): Unit = {
    if (_deferBarCount == 0) return
    // Draw all health bar shapes in one shapes batch, faded in with the match
    beginShapes()
    shapeBatch.setAlphaMultiplier(overlayAlpha)
    var i = 0
    while (i < _deferBarCount) {
      drawHealthBar(_deferBarCX(i), _deferBarTY(i), _deferBarHP(i), _deferBarMax(i), _deferBarTeam(i), _deferBarId(i))
      i += 1
    }
    // Draw all name backgrounds in one shapes batch (dark pill behind each name)
    i = 0
    while (i < _deferBarCount) {
      drawNameBackground(_deferBarName(i), _deferBarCX(i), _deferBarTY(i))
      i += 1
    }
    shapeBatch.resetModifiers()
    // Draw all character names in one sprites batch
    beginSprites()
    i = 0
    while (i < _deferBarCount) {
      drawCharacterNameDirect(_deferBarName(i), _deferBarCX(i), _deferBarTY(i))
      i += 1
    }
    _deferBarCount = 0
  }

  private def drawHealthBar(screenCenterX: Double, spriteTopY: Double, health: Int, maxHealth: Int, teamId: Byte, playerId: UUID): Unit = {
    val barW = Constants.HEALTH_BAR_WIDTH_PX.toFloat
    val barH = Constants.HEALTH_BAR_HEIGHT_PX.toFloat
    val barX = (screenCenterX - barW / 2).toFloat
    val barY = (spriteTopY - Constants.HEALTH_BAR_OFFSET_Y - barH).toFloat

    beginShapes()
    // Dark halo behind health bar for contrast on any terrain
    shapeBatch.fillRoundedRect(barX - 3f, barY - 3f, barW + 6f, barH + 6f, 3f, 0.01f, 0.01f, 0.03f, 0.35f)
    // Dark background
    shapeBatch.fillRect(barX, barY, barW, barH, 0.15f, 0.05f, 0.05f, 0.85f)

    // Clamped: a health reported above the max (a heal racing a change of character) ran the fill
    // off the end of the bar
    val pct = if (maxHealth <= 0) 0f else Math.max(0f, Math.min(1f, health.toFloat / maxHealth))
    var fr = 0f; var fg = 0f; var fb = 0f
    teamId match {
      case 1 => fr = 0.29f; fg = 0.51f; fb = 1f
      case 2 => fr = 0.91f; fg = 0.25f; fb = 0.34f
      case 3 => fr = 0.2f; fg = 0.8f; fb = 0.2f
      case 4 => fr = 0.95f; fg = 0.77f; fb = 0.06f
      case _ => fr = 0.2f; fg = 0.8f; fb = 0.2f
    }

    // Smooth drain bar (white ghost segment that shrinks behind the real bar)
    var smoothSlot = smoothHealthMap.get(playerId)
    if (smoothSlot == null) { smoothSlot = Array(pct); smoothHealthMap.put(playerId, smoothSlot) }
    val smoothPct = smoothSlot(0)
    val newSmooth = if (smoothPct > pct) Math.max(pct, smoothPct - 0.8f * (1f / 60f))
                    else pct // snap to actual on heal
    smoothSlot(0) = newSmooth
    if (newSmooth > pct) {
      shapeBatch.fillRect(barX + barW * pct, barY, barW * (newSmooth - pct), barH, 1f, 1f, 1f, 0.45f)
    }

    // Health bar with inner gradient (lighter top, darker bottom)
    val fillW = barW * pct
    if (fillW > 0) {
      shapeBatch.fillRect(barX, barY + barH / 2, fillW, barH / 2, fr * 0.75f, fg * 0.75f, fb * 0.75f, 1f)
      shapeBatch.fillRect(barX, barY, fillW, barH / 2, fr, fg, fb, 1f)
      // Bright highlight line at very top (1px white at 30% alpha)
      shapeBatch.fillRect(barX, barY, fillW, 1f, clamp(fr + 0.3f), clamp(fg + 0.3f), clamp(fb + 0.3f), 0.3f)
    }

    // Segment markers at 25%/50%/75% (subtle dark tick marks)
    { var seg = 1; while (seg < 4) {
      val segX = barX + barW * seg / 4f
      shapeBatch.fillRect(segX - 0.5f, barY, 1f, barH, 0f, 0f, 0f, 0.25f)
    ; seg += 1 } }

    // Bright highlight along top edge when health > 0 (stronger 0.4 alpha)
    if (fillW > 0) {
      shapeBatch.fillRect(barX, barY, fillW, 1.2f, clamp(fr + 0.4f), clamp(fg + 0.4f), clamp(fb + 0.4f), 0.4f)
    }

    // Low health pulse (<25%): dramatic red glow (0.25 alpha, larger)
    if (pct < 0.25f && pct > 0f) {
      val pulse = (0.5f + 0.5f * Math.sin(animationTick * 0.15f)).toFloat
      shapeBatch.setAdditiveBlend(true)
      shapeBatch.fillRect(barX, barY - 2, fillW, barH + 4, 1f, 0.2f, 0.1f, 0.25f * pulse)
      // Larger edge glow around bar
      shapeBatch.fillOvalSoft(barX + fillW * 0.5f, barY + barH * 0.5f, fillW * 0.8f + 4f, barH * 2f,
        1f, 0.15f, 0.05f, 0.15f * pulse, 0f, 10)
      // Pulsing red border
      shapeBatch.strokeOval(barX + barW * 0.5f, barY + barH * 0.5f, barW * 0.52f, barH * 0.8f,
        1.2f, 1f, 0.2f, 0.1f, 0.12f * pulse, 10)
      shapeBatch.setAdditiveBlend(false)
    }

    // Dark outline for contrast against any background
    shapeBatch.strokeRect(barX - 1, barY - 1, barW + 2, barH + 2, 1f, 0f, 0f, 0f, 0.7f)
    shapeBatch.strokeRect(barX, barY, barW, barH, 1f, 0f, 0f, 0f, 1f)
    // Subtle highlight along top edge
    shapeBatch.fillRect(barX, barY, barW, 1f, 1f, 1f, 1f, 0.2f)
    if (teamId != 0) {
      val indY = barY + barH + 2
      val indS = 4f
      val scx = screenCenterX.toFloat
      _indicatorXs(0) = scx; _indicatorXs(1) = scx + indS; _indicatorXs(2) = scx; _indicatorXs(3) = scx - indS
      _indicatorYs(0) = indY; _indicatorYs(1) = indY + indS; _indicatorYs(2) = indY + indS * 2; _indicatorYs(3) = indY + indS
      shapeBatch.fillPolygon(_indicatorXs, _indicatorYs, 4, fr, fg, fb, 1f)
    }
  }

  // How tall a name's text stands over the world, in world units; its plate is sized to it. It
  // was the 14-unit font with a 3-unit margin, and a crowd's plates covered the heads of whoever
  // stood a row behind.
  private val NAME_UNITS = 12f

  @inline private def nameScale: Float = NAME_UNITS / fontMedium.fontSize

  private def nameTop(spriteTopY: Double): Float =
    (spriteTopY - Constants.HEALTH_BAR_OFFSET_Y - Constants.HEALTH_BAR_HEIGHT_PX - fontMedium.charHeight * nameScale - 2).toFloat

  /** Draw a character's name without calling beginSprites() — used by flushDeferredBars. On its
    * dark plate it needs no outline, which was four more copies of every glyph. */
  private def drawCharacterNameDirect(name: String, screenCenterX: Double, spriteTopY: Double): Unit = {
    val sc = nameScale
    val textW = fontMedium.measureWidth(name, sc)
    fontMedium.drawText(spriteBatch, name, (screenCenterX - textW / 2).toFloat, nameTop(spriteTopY),
      1f, 1f, 1f, overlayAlpha, sc)
  }

  /** Draw dark pill background behind character name — called before name sprites pass. */
  private def drawNameBackground(name: String, screenCenterX: Double, spriteTopY: Double): Unit = {
    val sc = nameScale
    val nameY = nameTop(spriteTopY)
    val textW = fontMedium.measureWidth(name, sc)
    val textX = (screenCenterX - textW / 2).toFloat
    val padW = 5f; val padH = 2f
    val bgX = textX - padW; val bgY = nameY - padH
    val bgW = textW + padW * 2; val bgH = fontMedium.charHeight * sc + padH * 2
    beginShapes()
    // Gradient background: darker at bottom
    shapeBatch.fillRoundedRectGradient(bgX, bgY, bgW, bgH, 4f,
      0.06f, 0.06f, 0.12f, 0.70f,
      0.02f, 0.02f, 0.05f, 0.80f)
    // Subtle border, rounded like the plate (a square one stuck out past its corners)
    shapeBatch.strokeRoundedRect(bgX, bgY, bgW, bgH, 4f, 1f, 0.4f, 0.4f, 0.5f, 0.20f)
    // Top edge highlight
    shapeBatch.strokeLine(bgX + 4f, bgY + 0.5f, bgX + bgW - 4f, bgY + 0.5f, 1f, 1f, 1f, 1f, 0.08f)
  }

  def detectDamageNumbers(): Unit = {
    // Check local player
    val localId = client.getLocalPlayerId
    val localHealth = client.getLocalHealth
    val prevLocal = prevHealthMap.get(localId)
    if (prevLocal != null && prevLocal.intValue() > localHealth) {
      val dmg = prevLocal.intValue() - localHealth
      val lvx = client.visualPosX; val lvy = client.visualPosY
      damageNumbers.spawn(lvx.toFloat, lvy.toFloat, dmg, 1f, 0.3f, 0.2f)
      particles.spawnImpactSparks(lvx.toFloat, lvy.toFloat, 1f, 0.3f, 0.2f)
      // Screen shake proportional to damage (3px base + 0.3px per damage, capped at 18)
      camera.addShake(3.0 + dmg * 0.3)
      // Hit ripple particles
      val lsx = worldToScreenX(lvx.toFloat, lvy.toFloat).toFloat
      val lsy = worldToScreenY(lvx.toFloat, lvy.toFloat).toFloat - 10f
      combatParticles.emitRing(lsx, lsy, 10, 80f, 0.25f, 1f, 0.8f, 0.3f, 0.7f, 3f,
        pkind = ParticleSystem.KIND_STREAK)
    }
    prevHealthMap.put(localId, localHealth)

    // Check remote players — use Java iterator + getRemoteVisualPos to avoid tuple allocation
    val players = client.getPlayers
    val iter = players.entrySet().iterator()
    while (iter.hasNext) {
      val entry = iter.next()
      val playerId = entry.getKey
      val player = entry.getValue
      val health = player.getHealth
      val prev = prevHealthMap.get(playerId)
      if (prev != null && prev.intValue() > health) {
        val dmg = prev.intValue() - health
        val hasVisPos = entityCollector.getRemoteVisualPos(playerId)
        val pvx = if (hasVisPos) entityCollector.lastRVX else player.getPosition.getX.toDouble
        val pvy = if (hasVisPos) entityCollector.lastRVY else player.getPosition.getY.toDouble
        damageNumbers.spawn(pvx.toFloat, pvy.toFloat, dmg, 1f, 1f, 0.3f)
        particles.spawnImpactSparks(pvx.toFloat, pvy.toFloat, 1f, 1f, 0.3f)
        // Hit ripple particles
        val rsx = worldToScreenX(pvx.toFloat, pvy.toFloat).toFloat
        val rsy = worldToScreenY(pvx.toFloat, pvy.toFloat).toFloat - 10f
        combatParticles.emitRing(rsx, rsy, 10, 80f, 0.25f, 1f, 0.8f, 0.3f, 0.7f, 3f,
          pkind = ParticleSystem.KIND_STREAK)
      }
      prevHealthMap.put(playerId, health)
    }
  }

  def beginFrame(): Unit = _deferBarCount = 0

  def reset(): Unit = {
    prevHealthMap.clear()
    smoothHealthMap.clear()
  }

  /** Over the finished frame: every name plate and health bar noted this frame, and the damage
    * numbers. */
  def draw(): Unit = {
    flushDeferredBars()
    if (damageNumbers.hasActive) {
      beginSprites()
      damageNumbers.render(spriteBatch, _worldToScreenXFn, _worldToScreenYFn)
    }
  }
}
