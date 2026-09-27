package com.gridgame.client.render
package world

import com.gridgame.client.i18n.I18n
import com.gridgame.common.Constants
import com.gridgame.common.model._

import java.util.UUID

/**
 * Players: everyone else walked along the cells they were heard on (RemoteMotion), and us, each as
 * a shadow, a sprite, the effects on them, and the name plate and health bar that are drawn over the
 * finished frame (WorldOverlayPainter). A raised barrier is noted for BarrierPainter; a dash leaves a
 * trail of after-images.
 */
private[render] final class PlayerPainter(ctx: RenderContext, barriers: BarrierPainter, effects: StatusEffectPainter,
                                          glows: GlowEffectPainter, overlay: WorldOverlayPainter) {
  import ctx._
  import GlowEffectPainter._

  // Remote player movement detection — Java HashMaps avoid Option wrapping
  private val lastRemotePositions = new java.util.HashMap[UUID, Position]()
  private val remoteMovingUntil = new java.util.HashMap[UUID, java.lang.Long]()

  private def drawShadow(screenX: Double, screenY: Double): Unit = {
    beginShapes()
    val sx = screenX.toFloat + 2f
    val sy = screenY.toFloat + 1f
    // 3-layer composite shadow for depth
    // Outer soft blur (large, very transparent)
    shapeBatch.fillOvalSoft(sx, sy, 28f, 11f, 0.02f, 0.01f, 0.05f, 0.18f, 0f, 16)
    // Mid contact shadow (medium, darker)
    shapeBatch.fillOvalSoft(sx, sy, 22f, 9f, 0.02f, 0.01f, 0.05f, 0.32f, 0f, 14)
    // Inner hard contact (small, darkest)
    shapeBatch.fillOvalSoft(sx, sy, 14f, 6f, 0.01f, 0.005f, 0.03f, 0.45f, 0.12f, 12)
    // Subtle bright ring for visibility on dark terrain
    shapeBatch.strokeOval(sx, sy, 24f, 10f, 1f, 0.7f, 0.7f, 0.8f, 0.12f, 14)
  }

  def drawPlayer(player: Player, wx: Double, wy: Double): Unit = {
    val screenX = worldToScreenX(wx, wy)
    val screenY = worldToScreenY(wx, wy)
    val displaySz = Constants.PLAYER_DISPLAY_SIZE_PX
    val spriteCenter = screenY - displaySz / 2.0

    // Movement detection
    val now = frameTimeMs
    val playerId = player.getId
    val pos = player.getPosition
    val lastPos = lastRemotePositions.get(playerId)
    if (lastPos == null || lastPos.getX != pos.getX || lastPos.getY != pos.getY) {
      remoteMovingUntil.put(playerId, now + 200)
    }
    lastRemotePositions.put(playerId, pos)
    val movUntil = remoteMovingUntil.get(playerId)
    val isMoving = movUntil != null && now < movUntil.longValue()

    val animSpeed = if (player.getChargeLevel > 0) {
      val chargePct = player.getChargeLevel / 100.0
      (FRAMES_PER_STEP * (1.0 + chargePct * 4.0)).toInt
    } else FRAMES_PER_STEP
    val frame = if (isMoving) (animationTick / animSpeed) % 4 else 0

    val playerIsPhased = player.isPhased

    // Player light — color-matched to character, charge-reactive
    intToRGB(player.getColorRGB)
    val playerLR = _rgb_r; val playerLG = _rgb_g; val playerLB = _rgb_b
    val chargeIntensity = if (player.getChargeLevel > 0) 0.2f + (player.getChargeLevel / 100f) * 0.4f else 0.2f
    val playerLightRadius = 50f + (player.getChargeLevel / 100f) * 40f
    lightSystem.addLight(screenX.toFloat, spriteCenter.toFloat, playerLightRadius, playerLR, playerLG, playerLB, chargeIntensity)
    // Shield blue glow
    if (player.hasShield) lightSystem.addLight(screenX.toFloat, spriteCenter.toFloat, 60f, 0.3f, 0.5f, 1f, 0.15f)
    // Burn orange glow
    if (player.isBurning) lightSystem.addLight(screenX.toFloat, spriteCenter.toFloat, 50f, 1f, 0.45f, 0.05f, 0.15f)
    // Frozen blue glow, or a warm one for the stun that wears the same freeze
    if (player.isFrozen && !player.isStunned) lightSystem.addLight(screenX.toFloat, spriteCenter.toFloat, 45f, 0.5f, 0.8f, 1f, 0.12f)
    if (player.isStunned) lightSystem.addLight(screenX.toFloat, spriteCenter.toFloat, 45f, 1f, 0.85f, 0.3f, 0.12f)
    // Poison green glow
    if (player.isPoisoned) lightSystem.addLight(screenX.toFloat, spriteCenter.toFloat, 45f, 0.3f, 0.9f, 0.25f, 0.13f)

    // Pre-player non-additive effects
    if (!playerIsPhased) {
      if (player.hasShield) effects.drawShieldBubble(screenX, spriteCenter, client.getPlayerHitTime(playerId))
    }
    // A barrier stands in front of them; it is drawn after the entity pass (drawBarriers)
    barriers.note(playerId, wx, wy, player.getBarrierAngle, player.getBarrierRaisedAt, player.getBarrierUntil,
      client.localTeamId != 0 && player.getTeamId == client.localTeamId)

    drawShadow(screenX, screenY)

    // Remote player footstep dust (sparse, distance-culled)
    val dustDx = wx - camera.visualX; val dustDy = wy - camera.visualY
    if (isMoving && dustDx * dustDx + dustDy * dustDy < 64 && rng.nextFloat() < 0.03f) {
      val sx = screenX.toFloat; val sy = screenY.toFloat
      combatParticles.emit(
        sx + rng.nextFloat() * 6f - 3f, sy + rng.nextFloat() * 2f,
        rng.nextFloat() * 6f - 3f, -(2f + rng.nextFloat() * 3f),
        plife = 0.35f + rng.nextFloat() * 0.2f,
        pr = 0.55f, pg = 0.50f, pb = 0.42f, palpha = 0.18f,
        psize = 1.5f + rng.nextFloat() * 1.5f,
        pgravity = 5f, shrink = false, soft = true, pkind = ParticleSystem.KIND_SMOKE
      )
    }

    // Sprite — flash red/white on hit
    val hitTime = client.getPlayerHitTime(playerId)
    val hitFlash = if (hitTime > 0) {
      val el = frameTimeMs - hitTime
      if (el < HIT_ANIMATION_MS) (1.0 - el.toDouble / HIT_ANIMATION_MS).toFloat else 0f
    } else 0f
    val region = GLSpriteGenerator.getSpriteRegion(player.getDirection, frame, player.getCharacterId)
    if (region != null) {
      val alpha = if (playerIsPhased) 0.4f else 1f
      val g = 1f - hitFlash * 0.7f
      val b = 1f - hitFlash * 0.7f
      // Idle breathing — subtle 2% scale oscillation when standing still
      val breathScale = if (!isMoving) 1f + 0.02f * Math.sin(animTickF * 0.05 + playerId.hashCode() * 0.1).toFloat else 1f
      // Hit scale pulse — sprites grow 18% on impact then shrink back
      val hitScale = breathScale + hitFlash * 0.18f
      val spriteW = displaySz.toFloat * hitScale
      val spriteH = displaySz.toFloat * hitScale
      val drawX = (screenX - spriteW / 2.0).toFloat
      val drawY = (screenY - spriteH).toFloat
      beginSprites()
      // Movement afterimage — ghost sprite trailing behind nearby moving players
      val aimDx = wx - camera.visualX; val aimDy = wy - camera.visualY
      if (isMoving && hitFlash < 0.1f && aimDx * aimDx + aimDy * aimDy < 36) {
        spriteBatch.draw(region, drawX + 1.5f, drawY + 0.8f, spriteW, spriteH, 0.6f, 0.6f, 0.8f, 0.12f)
      }
      // Player outline: glow brighter on hit
      val isLocal = playerId == client.getLocalPlayerId
      val outR = if (isLocal) 1f else 0.8f + hitFlash * 0.2f
      val outG = if (isLocal) 1f else 0.2f
      val outB = if (isLocal) 1f else 0.2f
      val outA = 0.5f + hitFlash * 0.3f
      val outOff = 1f + hitFlash * 0.5f
      spriteBatch.draw(region, drawX - outOff, drawY, spriteW, spriteH, outR, outG, outB, outA)
      spriteBatch.draw(region, drawX + outOff, drawY, spriteW, spriteH, outR, outG, outB, outA)
      spriteBatch.draw(region, drawX, drawY - outOff, spriteW, spriteH, outR, outG, outB, outA)
      spriteBatch.draw(region, drawX, drawY + outOff, spriteW, spriteH, outR, outG, outB, outA)
      spriteBatch.draw(region, drawX, drawY, spriteW, spriteH, 1f, g, b, alpha)
    }

    // Post-player non-additive effects. A stun is a freeze underneath (Player.tryStun), so the
    // two flags come together and the stars stand in for the ice.
    if (player.isStunned) effects.drawStunnedEffect(screenX, spriteCenter)
    else if (player.isFrozen) effects.drawFrozenEffect(screenX, spriteCenter)
    if (player.isPoisoned) effects.drawPoisonEffect(screenX, spriteCenter)
    if (player.isRooted) effects.drawRootedEffect(screenX, spriteCenter)
    if (player.isSlowed) effects.drawSlowedEffect(screenX, spriteCenter)
    if (player.hasSpeedBoost) effects.drawSpeedBoostEffect(screenX, spriteCenter, player.getDirection.id)

    // Defer additive effects to batch pass
    var fxFlags = 0
    if (player.hasGemBoost && !playerIsPhased) fxFlags |= FX_GEM
    if (player.getChargeLevel > 0 && !playerIsPhased) fxFlags |= FX_CHARGE
    if (playerIsPhased) fxFlags |= FX_PHASED
    if (player.isBurning) fxFlags |= FX_BURN
    fxFlags |= FX_HIT // hitTime checked inside Inner method
    if (fxFlags != 0) glows.deferAdditive(screenX, spriteCenter, fxFlags, player.getChargeLevel, hitTime, playerId, player.getColorRGB)

    // Health bar + name (deferred to batch pass)
    val charName = I18n.characterName(CharacterDef.get(player.getCharacterId))
    overlay.deferHealthBar(screenX, screenY - displaySz, player.getHealth, player.getMaxHealth, player.getTeamId, player.getId, charName)
  }

  def drawLocal(wx: Double, wy: Double): Unit = {
    val screenX = worldToScreenX(wx, wy)
    val screenY = worldToScreenY(wx, wy)
    val displaySz = Constants.PLAYER_DISPLAY_SIZE_PX
    val spriteCenter = screenY - displaySz / 2.0
    val localIsPhased = client.isPhased

    // Local player light — color-matched, charge-reactive
    intToRGB(client.getLocalColorRGB)
    val lpLR = _rgb_r; val lpLG = _rgb_g; val lpLB = _rgb_b
    val localChargeLevel = client.getChargeLevel
    val localChargeIntensity = if (localChargeLevel > 0) 0.2f + (localChargeLevel / 100f) * 0.4f else 0.2f
    val localLightRadius = 50f + (localChargeLevel / 100f) * 40f
    lightSystem.addLight(screenX.toFloat, spriteCenter.toFloat, localLightRadius, lpLR, lpLG, lpLB, localChargeIntensity)
    if (client.hasShield) lightSystem.addLight(screenX.toFloat, spriteCenter.toFloat, 60f, 0.3f, 0.5f, 1f, 0.15f)
    if (client.isBurning) lightSystem.addLight(screenX.toFloat, spriteCenter.toFloat, 50f, 1f, 0.45f, 0.05f, 0.15f)
    if (client.isFrozen && !client.isStunned) lightSystem.addLight(screenX.toFloat, spriteCenter.toFloat, 45f, 0.5f, 0.8f, 1f, 0.12f)
    if (client.isStunned) lightSystem.addLight(screenX.toFloat, spriteCenter.toFloat, 45f, 1f, 0.85f, 0.3f, 0.12f)
    if (client.isPoisoned) lightSystem.addLight(screenX.toFloat, spriteCenter.toFloat, 45f, 0.3f, 0.9f, 0.25f, 0.13f)

    if (!localIsPhased) {
      if (client.hasShield) effects.drawShieldBubble(screenX, spriteCenter, client.getPlayerHitTime(client.getLocalPlayerId))
    }
    // Ours turns with the cursor from frame to frame, ahead of what the server has been told
    barriers.note(client.getLocalPlayerId, wx, wy, client.getAimAngle, client.getBarrierRaisedAt, client.getBarrierUntil,
      ally = true)

    // Dash afterimage
    if (client.isSwooping) drawSwoopTrail(wx, wy, displaySz)

    // Local player ground indicator — pulsing gold ring + soft glow
    {
      val sx = screenX.toFloat + 2f
      val sy = screenY.toFloat + 1f
      val pulse = (0.45f + 0.20f * Math.sin(animTickF * 0.08).toFloat)
      beginShapes()
      // Soft glow under feet
      shapeBatch.fillOvalSoft(sx, sy, 34f, 14f, 1f, 0.85f, 0.3f, (0.08f + 0.06f * pulse), 0f, 16)
      // Outer bright ring
      shapeBatch.strokeOval(sx, sy, 30f, 12f, 1.5f, 1f, 0.85f, 0.3f, pulse, 16)
      // Inner tighter ring
      shapeBatch.strokeOval(sx, sy, 26f, 10f, 0.8f, 1f, 0.85f, 0.3f, pulse * 0.6f, 14)
    }

    drawShadow(screenX, screenY)

    val animSpeed = if (client.isCharging) {
      (FRAMES_PER_STEP * (1.0 + client.getChargeLevel / 100.0 * 4.0)).toInt
    } else FRAMES_PER_STEP
    val frame = if (client.getIsMoving) (animationTick / animSpeed) % 4 else 0
    // Sprite — flash red/white on hit
    val localHitTime = client.getPlayerHitTime(client.getLocalPlayerId)
    val localHitFlash = if (localHitTime > 0) {
      val el = frameTimeMs - localHitTime
      if (el < HIT_ANIMATION_MS) (1.0 - el.toDouble / HIT_ANIMATION_MS).toFloat else 0f
    } else 0f
    val region = GLSpriteGenerator.getSpriteRegion(client.getLocalDirection, frame, client.selectedCharacterId)
    if (region != null) {
      val alpha = if (localIsPhased) 0.4f else 1f
      val g = 1f - localHitFlash * 0.7f
      val b = 1f - localHitFlash * 0.7f
      // Idle breathing — subtle 2% scale oscillation when standing still
      val localBreathScale = if (!client.getIsMoving) 1f + 0.02f * Math.sin(animTickF * 0.05).toFloat else 1f
      // Hit scale pulse — sprites grow 18% on impact then shrink back
      val hitScale = localBreathScale + localHitFlash * 0.18f
      val spriteW = displaySz.toFloat * hitScale
      val spriteH = displaySz.toFloat * hitScale
      val drawX = (screenX - spriteW / 2.0).toFloat
      val drawY = (screenY - spriteH).toFloat
      beginSprites()
      // Movement afterimage — ghost sprite trailing behind moving player
      if (client.getIsMoving && localHitFlash < 0.1f) {
        spriteBatch.draw(region, drawX + 1.5f, drawY + 0.8f, spriteW, spriteH, 0.6f, 0.6f, 0.8f, 0.12f)
      }
      // Player outline: glow brighter on hit (local player = white)
      val outR = 1f; val outG = 1f; val outB = 1f
      val outA = 0.5f + localHitFlash * 0.3f
      val outOff = 1f + localHitFlash * 0.5f
      spriteBatch.draw(region, drawX - outOff, drawY, spriteW, spriteH, outR, outG, outB, outA)
      spriteBatch.draw(region, drawX + outOff, drawY, spriteW, spriteH, outR, outG, outB, outA)
      spriteBatch.draw(region, drawX, drawY - outOff, spriteW, spriteH, outR, outG, outB, outA)
      spriteBatch.draw(region, drawX, drawY + outOff, spriteW, spriteH, outR, outG, outB, outA)
      spriteBatch.draw(region, drawX, drawY, spriteW, spriteH, 1f, g, b, alpha)
    }

    // Small downward-pointing chevron above name area
    {
      val chevPulse = (0.50f + 0.25f * Math.sin(animTickF * 0.08).toFloat)
      val cx = screenX.toFloat
      val cy = (screenY - displaySz - 14).toFloat
      val chevW = 8f
      val chevH = 5f
      beginShapes()
      shapeBatch.strokeLine(cx - chevW, cy, cx, cy + chevH, 1.8f, 1f, 0.85f, 0.3f, chevPulse)
      shapeBatch.strokeLine(cx + chevW, cy, cx, cy + chevH, 1.8f, 1f, 0.85f, 0.3f, chevPulse)
    }

    // Non-additive post-player effects
    if (client.isStunned) effects.drawStunnedEffect(screenX, spriteCenter)
    else if (client.isFrozen) effects.drawFrozenEffect(screenX, spriteCenter)
    if (client.isPoisoned) effects.drawPoisonEffect(screenX, spriteCenter)
    if (client.isRooted) effects.drawRootedEffect(screenX, spriteCenter)
    if (client.isSlowed) effects.drawSlowedEffect(screenX, spriteCenter)
    if (client.hasSpeedBoost) effects.drawSpeedBoostEffect(screenX, spriteCenter, client.getLocalDirection.id)

    // Defer additive effects to batch pass
    var fxFlags = 0
    if (client.hasGemBoost && !localIsPhased) fxFlags |= FX_GEM
    if (client.getChargeLevel > 0 && !localIsPhased) fxFlags |= FX_CHARGE
    if (localIsPhased) fxFlags |= FX_PHASED
    if (client.isBurning) fxFlags |= FX_BURN
    fxFlags |= FX_HIT | FX_CAST
    if (fxFlags != 0) glows.deferAdditive(screenX, spriteCenter, fxFlags, client.getChargeLevel, localHitTime,
      client.getLocalPlayerId, client.getLocalColorRGB)

    // Health bar + name (deferred to batch pass)
    val charName = I18n.characterName(client.getSelectedCharacterDef)
    overlay.deferHealthBar(screenX, screenY - displaySz, client.getLocalHealth, client.getSelectedCharacterMaxHealth, client.localTeamId, client.getLocalPlayerId, charName)
  }

  private def drawSwoopTrail(wx: Double, wy: Double, displaySz: Int): Unit = {
    val swoopProg = client.getSwoopProgress
    val sx0 = client.getSwoopStartX.toDouble
    val sy0 = client.getSwoopStartY.toDouble
    val sx1 = client.getSwoopTargetX.toDouble
    val sy1 = client.getSwoopTargetY.toDouble
    val ghostRegion = GLSpriteGenerator.getSpriteRegion(client.getLocalDirection,
      (animationTick / FRAMES_PER_STEP) % 4, client.selectedCharacterId)
    if (ghostRegion == null) return

    beginSprites()
    var i = 0
    while (i < 5) {
      val ghostT = swoopProg - (i + 1) * 0.12
      if (ghostT > 0 && ghostT < 1.0) {
        val gx = sx0 + (sx1 - sx0) * ghostT
        val gy = sy0 + (sy1 - sy0) * ghostT
        val gsx = worldToScreenX(gx, gy)
        val gsy = worldToScreenY(gx, gy)
        val alpha = Math.max(0.03f, (0.25 - i * 0.04).toFloat)
        val scale = (1.0 - i * 0.04).toFloat
        val gSize = displaySz * scale
        spriteBatch.draw(ghostRegion,
          (gsx - gSize / 2).toFloat, (gsy - gSize).toFloat,
          gSize, gSize, 1f, 1f, 1f, alpha)
      }
      i += 1
    }
  }

  /** Every player's warm glow in the light map: ours, and everyone else's where they are drawn. */
  def addLights(): Unit = {
    // Local player light (use cached visual position — already updated this frame)
    val lvx = client.visualPosX; val lvy = client.visualPosY
    val lpsx = worldToScreenX(lvx, lvy).toFloat
    val lpsy = worldToScreenY(lvx, lvy).toFloat
    lightSystem.addLight(lpsx, lpsy, 80f, 1f, 0.9f, 0.7f, 0.15f)

    // Remote players — use getRemoteVisualPos to avoid tuple allocation
    val players = client.getPlayers
    val iter = players.values().iterator()
    while (iter.hasNext) {
      val player = iter.next()
      val hasVisPos = entityCollector.getRemoteVisualPos(player.getId)
      val pvx = if (hasVisPos) entityCollector.lastRVX else player.getPosition.getX.toDouble
      val pvy = if (hasVisPos) entityCollector.lastRVY else player.getPosition.getY.toDouble
      val psx = worldToScreenX(pvx, pvy).toFloat
      val psy = worldToScreenY(pvx, pvy).toFloat
      lightSystem.addLight(psx, psy, 65f, 1f, 0.85f, 0.65f, 0.10f)
    }
  }

  def reset(): Unit = {
    lastRemotePositions.clear()
    remoteMovingUntil.clear()
  }
}
