package com.gridgame.client.render
package hud

import com.gridgame.client.i18n.Messages

import com.gridgame.client.game.ClientState
import com.gridgame.client.render.world.OpeningPainter

/** The match's information along the top: the clock, our kills and deaths, the opening's countdown
  * and the kill feed. */
private[render] final class MatchInfoPainter(ctx: RenderContext, opening: OpeningPainter) {
  import ctx._

  private var _cachedKills = -1; private var _cachedDeaths = -1; private var _cachedKillStr = "0"; private var _cachedDeathStr = "0"

  // Cached timer text (avoids per-frame format string allocation)
  private var _cachedTimerRemaining = -1
  private var _cachedTimerStr = ""

  // Deferred kill feed entries (batch backgrounds in shapes, then text in sprites)
  private val MAX_FEED_ENTRIES = 10
  private val _feedX = new Array[Float](MAX_FEED_ENTRIES)
  private val _feedY = new Array[Float](MAX_FEED_ENTRIES)
  private val _feedW = new Array[Float](MAX_FEED_ENTRIES)
  private val _feedA = new Array[Float](MAX_FEED_ENTRIES)
  private val _feedText = new Array[String](MAX_FEED_ENTRIES)
  private val _feedR = new Array[Float](MAX_FEED_ENTRIES)
  private val _feedG = new Array[Float](MAX_FEED_ENTRIES)
  private val _feedB = new Array[Float](MAX_FEED_ENTRIES)
  private var _feedCount = 0

  def draw(screenW: Int, screenH: Int): Unit = {
    if (client.clientState != ClientState.PLAYING) return

    // Timer panel at top center (skip in practice mode — practice HUD draws its own label)
    if (!client.isPracticeMode) {
      val remaining = client.gameTimeRemaining
      if (remaining != _cachedTimerRemaining) {
        _cachedTimerRemaining = remaining
        val minutes = remaining / 60; val seconds = remaining % 60
        _cachedTimerStr = f"$minutes%d:$seconds%02d"
      }
      val timerText = _cachedTimerStr
      val isLowTime = remaining > 0 && remaining <= 30
      val timerPulse = if (isLowTime) (Math.sin(animTickF * 0.15) * 0.3 + 0.7).toFloat else 1f

      val timerTextW = fontMedium.measureWidth(timerText)
      val timerW = timerTextW + 50
      val timerH = 32f
      val timerX = (screenW / 2 - timerW / 2).toFloat
      val timerY = 6f
      beginShapes()
      if (isLowTime) {
        shapeBatch.fillRoundedRectGradient(timerX, timerY, timerW.toFloat, timerH, 8f,
          0.25f * timerPulse, 0.02f, 0.02f, 0.7f,
          0.12f * timerPulse, 0.01f, 0.01f, 0.6f)
      } else {
        shapeBatch.fillRoundedRectGradient(timerX, timerY, timerW.toFloat, timerH, 8f,
          0.08f, 0.08f, 0.14f, 0.60f,
          0.04f, 0.04f, 0.08f, 0.50f)
        // Subtle top edge highlight
        shapeBatch.strokeLine(timerX + 8, timerY, timerX + timerW.toFloat - 8, timerY, 1f, 1f, 1f, 1f, 0.07f)
      }
      // Detailed clock icon with tick marks, animated hands, and glow
      val clockCX = timerX + 16f
      val clockCY = timerY + timerH / 2f
      val clockR = 7.5f
      // Subtle outer glow
      shapeBatch.fillOvalSoft(clockCX, clockCY, clockR + 3f, clockR + 3f, 0.5f, 0.6f, 0.9f, 0.08f, 0f, 12)
      // Clock face fill (very subtle)
      shapeBatch.fillOval(clockCX, clockCY, clockR - 0.5f, clockR - 0.5f, 0.15f, 0.15f, 0.25f, 0.3f, 14)
      // Outer ring
      shapeBatch.strokeOval(clockCX, clockCY, clockR, clockR, 1.5f, 0.75f, 0.75f, 0.9f, 0.85f, 16)
      // Tick marks at 12, 3, 6, 9 positions
      shapeBatch.strokeLine(clockCX, clockCY - clockR + 1f, clockCX, clockCY - clockR + 2.8f, 1.2f, 0.85f, 0.85f, 0.95f, 0.75f) // 12
      shapeBatch.strokeLine(clockCX + clockR - 1f, clockCY, clockCX + clockR - 2.8f, clockCY, 1.2f, 0.85f, 0.85f, 0.95f, 0.75f) // 3
      shapeBatch.strokeLine(clockCX, clockCY + clockR - 1f, clockCX, clockCY + clockR - 2.8f, 1.2f, 0.85f, 0.85f, 0.95f, 0.75f) // 6
      shapeBatch.strokeLine(clockCX - clockR + 1f, clockCY, clockCX - clockR + 2.8f, clockCY, 1.2f, 0.85f, 0.85f, 0.95f, 0.75f) // 9
      // Minor tick marks at other hours
      ;{ var h = 0; while (h < 12) {
        if (h % 3 != 0) {
          val tickAngle = h * Math.PI / 6 - Math.PI / 2
          val outerR = clockR - 0.8f; val innerR = clockR - 2f
          shapeBatch.strokeLine(
            clockCX + (Math.cos(tickAngle) * innerR).toFloat, clockCY + (Math.sin(tickAngle) * innerR).toFloat,
            clockCX + (Math.cos(tickAngle) * outerR).toFloat, clockCY + (Math.sin(tickAngle) * outerR).toFloat,
            0.7f, 0.6f, 0.6f, 0.75f, 0.5f)
        }
      ; h += 1 } }
      // Minute hand — animated, sweeps based on game time remaining
      val minuteAngle = (remaining % 60) * Math.PI / 30 - Math.PI / 2
      val minuteLen = clockR * 0.7f
      shapeBatch.strokeLine(clockCX, clockCY,
        clockCX + (Math.cos(minuteAngle) * minuteLen).toFloat,
        clockCY + (Math.sin(minuteAngle) * minuteLen).toFloat, 1.3f, 0.95f, 0.95f, 1f, 0.85f)
      // Hour hand (shorter, thicker)
      val hourAngle = (remaining / 60.0) * Math.PI / 6 - Math.PI / 2
      val hourLen = clockR * 0.45f
      shapeBatch.strokeLine(clockCX, clockCY,
        clockCX + (Math.cos(hourAngle) * hourLen).toFloat,
        clockCY + (Math.sin(hourAngle) * hourLen).toFloat, 1.8f, 0.9f, 0.9f, 1f, 0.8f)
      // Center pivot dot
      shapeBatch.fillOval(clockCX, clockCY, 1.2f, 1.2f, 0.95f, 0.95f, 1f, 0.9f, 6)

      beginSprites()
      val timerTextX = (screenW / 2 - timerTextW / 2 + 8).toFloat
      val timerTextY = timerY + (timerH - fontMedium.charHeight) / 2f
      if (isLowTime) {
        fontMedium.drawTextOutlined(spriteBatch, timerText, timerTextX, timerTextY, 1f * timerPulse, 0.25f * timerPulse, 0.25f * timerPulse)
      } else {
        fontMedium.drawTextOutlined(spriteBatch, timerText, timerTextX, timerTextY)
      }
    }

    opening.drawCountdown(screenW)

    // Kill feed — collect entries first (needed before shapes pass)
    val now = frameTimeMs
    var feedY = 10f
    // The feed names the local player "You", not by character; matching the character name
    // missed our own kills and lit up anyone else playing the same character.
    val localName = Messages.t("You")
    _feedCount = 0
    val feedIter = client.killFeed.iterator()
    while (feedIter.hasNext && _feedCount < MAX_FEED_ENTRIES) {
      val entry = feedIter.next()
      val timestamp = entry(0).asInstanceOf[java.lang.Long].longValue()
      val elapsed = now - timestamp
      if (elapsed < 6000) {
        val alpha = Math.max(0.15f, (1.0 - elapsed / 6000.0).toFloat)
        val killer = entry(1).asInstanceOf[String]
        val victim = entry(2).asInstanceOf[String]
        val feedText = entry(3).asInstanceOf[String]
        val textW = fontSmall.measureWidth(feedText)
        val slideOffset = if (elapsed < 200) ((1.0 - elapsed / 200.0) * 60).toFloat else 0f
        val flashBoost = if (elapsed < 200) (1.0 - elapsed / 200.0).toFloat * 0.3f else 0f
        val fx = screenW - textW - 22f + slideOffset
        val fi = _feedCount
        _feedX(fi) = fx; _feedY(fi) = feedY; _feedW(fi) = textW; _feedA(fi) = Math.min(1f, alpha + flashBoost)
        _feedText(fi) = feedText
        val isLocalKiller = killer == localName
        val isLocalVictim = victim == localName
        if (isLocalKiller) { _feedR(fi) = 0.4f; _feedG(fi) = 1f; _feedB(fi) = 0.4f }
        else if (isLocalVictim) { _feedR(fi) = 1f; _feedG(fi) = 0.4f; _feedB(fi) = 0.4f }
        else { _feedR(fi) = 1f; _feedG(fi) = 1f; _feedB(fi) = 1f }
        _feedCount += 1
        feedY += 22
      }
    }

    // K/D display — styled panel top-left
    val kc = client.killCount; val dc = client.deathCount
    if (kc != _cachedKills || dc != _cachedDeaths) {
      _cachedKills = kc; _cachedDeaths = dc
      _cachedKillStr = kc.toString
      _cachedDeathStr = dc.toString
    }
    val kdPanelX = 10f; val kdPanelY = 10f
    val kdPanelW = 130f; val kdPanelH = 56f
    beginShapes()
    shapeBatch.fillRoundedRectGradient(kdPanelX, kdPanelY, kdPanelW, kdPanelH, 8f,
      0.08f, 0.08f, 0.14f, 0.75f,
      0.04f, 0.04f, 0.08f, 0.65f)
    // Top edge highlight
    shapeBatch.strokeLine(kdPanelX + 8, kdPanelY, kdPanelX + kdPanelW - 8, kdPanelY, 1f, 1f, 1f, 1f, 0.10f)
    // Subtle border for definition
    shapeBatch.strokeRect(kdPanelX, kdPanelY, kdPanelW, kdPanelH, 1f, 0.3f, 0.3f, 0.4f, 0.25f)
    // Divider line
    shapeBatch.strokeLine(kdPanelX + kdPanelW / 2, kdPanelY + 6, kdPanelX + kdPanelW / 2, kdPanelY + kdPanelH - 6, 1f, 0.3f, 0.3f, 0.4f, 0.4f)
    // Crossed swords icon (kills) — detailed blades with guard and pommel
    val swordCX = kdPanelX + 16f; val swordCY = kdPanelY + 15f
    // Left sword blade (top-left to bottom-right)
    shapeBatch.strokeLine(swordCX - 5.5f, swordCY - 6.5f, swordCX + 5.5f, swordCY + 6.5f, 2f, 0.55f, 0.95f, 0.55f, 0.9f)
    shapeBatch.strokeLine(swordCX - 5f, swordCY - 6f, swordCX + 4f, swordCY + 5f, 1f, 0.75f, 1f, 0.75f, 0.4f) // blade highlight
    // Right sword blade (top-right to bottom-left)
    shapeBatch.strokeLine(swordCX + 5.5f, swordCY - 6.5f, swordCX - 5.5f, swordCY + 6.5f, 2f, 0.55f, 0.95f, 0.55f, 0.9f)
    shapeBatch.strokeLine(swordCX + 5f, swordCY - 6f, swordCX - 4f, swordCY + 5f, 1f, 0.75f, 1f, 0.75f, 0.4f) // blade highlight
    // Left sword guard (perpendicular to left blade, at crossing point)
    shapeBatch.strokeLine(swordCX - 1f, swordCY + 3.5f, swordCX + 4.5f, swordCY - 0.5f, 1.8f, 0.4f, 0.7f, 0.4f, 0.85f)
    // Right sword guard
    shapeBatch.strokeLine(swordCX + 1f, swordCY + 3.5f, swordCX - 4.5f, swordCY - 0.5f, 1.8f, 0.4f, 0.7f, 0.4f, 0.85f)
    // Pommel dots at the bottom of each blade
    shapeBatch.fillOval(swordCX + 5.5f, swordCY + 7f, 1.5f, 1.5f, 0.6f, 0.95f, 0.6f, 0.8f, 6)
    shapeBatch.fillOval(swordCX - 5.5f, swordCY + 7f, 1.5f, 1.5f, 0.6f, 0.95f, 0.6f, 0.8f, 6)
    // Blade tips (bright dots at top)
    shapeBatch.fillOval(swordCX - 6f, swordCY - 7f, 1.2f, 1.2f, 0.8f, 1f, 0.8f, 0.7f, 4)
    shapeBatch.fillOval(swordCX + 6f, swordCY - 7f, 1.2f, 1.2f, 0.8f, 1f, 0.8f, 0.7f, 4)

    // Skull icon (deaths) — detailed skull with jaw, cracks, and crossbones
    val skullCX = kdPanelX + kdPanelW / 2 + 16f; val skullCY = kdPanelY + 13f
    // Crossbones behind skull
    shapeBatch.strokeLine(skullCX - 7f, skullCY + 5f, skullCX + 7f, skullCY - 1f, 1.5f, 0.7f, 0.18f, 0.18f, 0.5f)
    shapeBatch.strokeLine(skullCX + 7f, skullCY + 5f, skullCX - 7f, skullCY - 1f, 1.5f, 0.7f, 0.18f, 0.18f, 0.5f)
    // Bone end knobs
    shapeBatch.fillOval(skullCX - 7.5f, skullCY + 5.5f, 1.3f, 1.3f, 0.75f, 0.2f, 0.2f, 0.5f, 4)
    shapeBatch.fillOval(skullCX + 7.5f, skullCY + 5.5f, 1.3f, 1.3f, 0.75f, 0.2f, 0.2f, 0.5f, 4)
    shapeBatch.fillOval(skullCX - 7.5f, skullCY - 1.5f, 1.3f, 1.3f, 0.75f, 0.2f, 0.2f, 0.5f, 4)
    shapeBatch.fillOval(skullCX + 7.5f, skullCY - 1.5f, 1.3f, 1.3f, 0.75f, 0.2f, 0.2f, 0.5f, 4)
    // Main cranium
    shapeBatch.fillOval(skullCX, skullCY, 7f, 8f, 0.88f, 0.22f, 0.22f, 0.92f, 14)
    // Cranium highlight (upper left)
    shapeBatch.fillOval(skullCX - 2f, skullCY - 3f, 3f, 2.5f, 1f, 0.4f, 0.4f, 0.3f, 8)
    // Eye sockets (dark, slightly sunken)
    shapeBatch.fillOval(skullCX - 2.8f, skullCY - 1.5f, 2f, 2.2f, 0.08f, 0.02f, 0.02f, 0.95f, 8)
    shapeBatch.fillOval(skullCX + 2.8f, skullCY - 1.5f, 2f, 2.2f, 0.08f, 0.02f, 0.02f, 0.95f, 8)
    // Nose cavity (tiny triangle)
    shapeBatch.fillOval(skullCX, skullCY + 1.5f, 1f, 1.2f, 0.12f, 0.04f, 0.04f, 0.85f, 6)
    // Jaw (separate rect below cranium with teeth marks)
    shapeBatch.fillRect(skullCX - 4.5f, skullCY + 5f, 9f, 3.5f, 0.82f, 0.2f, 0.2f, 0.8f)
    // Teeth lines
    shapeBatch.strokeLine(skullCX - 2.5f, skullCY + 5f, skullCX - 2.5f, skullCY + 8f, 0.5f, 0.12f, 0.04f, 0.04f, 0.6f)
    shapeBatch.strokeLine(skullCX, skullCY + 5f, skullCX, skullCY + 8f, 0.5f, 0.12f, 0.04f, 0.04f, 0.6f)
    shapeBatch.strokeLine(skullCX + 2.5f, skullCY + 5f, skullCX + 2.5f, skullCY + 8f, 0.5f, 0.12f, 0.04f, 0.04f, 0.6f)
    // Crack line on cranium
    shapeBatch.strokeLine(skullCX + 1f, skullCY - 7f, skullCX + 3f, skullCY - 3f, 0.8f, 0.5f, 0.1f, 0.1f, 0.5f)
    shapeBatch.strokeLine(skullCX + 3f, skullCY - 3f, skullCX + 1.5f, skullCY, 0.8f, 0.5f, 0.1f, 0.1f, 0.4f)

    // Kill feed backgrounds — drawn in shapes batch (same batch as K/D panel, proven to work)
    { var fi = 0; while (fi < _feedCount) {
      val entryAlpha = _feedA(fi)
      val bgA = if (entryAlpha > 0.5f) 0.88f else entryAlpha * 1.76f
      val entryW = _feedW(fi) + 28f
      val entryH = 20f
      val ex = _feedX(fi) - 14f
      val ey = _feedY(fi) - 3f
      // Dark background panel
      shapeBatch.fillRect(ex, ey, entryW, entryH, 0.02f, 0.02f, 0.06f, bgA)
      // Left accent bar (colored by kill/death)
      shapeBatch.fillRect(ex, ey + 2f, 3f, entryH - 4f, _feedR(fi), _feedG(fi), _feedB(fi), entryAlpha)
      // Subtle border for visibility
      shapeBatch.strokeRect(ex, ey, entryW, entryH, 1f, 0.3f, 0.3f, 0.5f, bgA * 0.3f)
    ; fi += 1 } }

    beginSprites()
    // Kill count in green (large font for readability)
    fontLarge.drawTextOutlined(spriteBatch, _cachedKillStr, kdPanelX + 29f, kdPanelY + 18f, 0.4f, 1f, 0.4f)
    // Death count in red (large font for readability)
    fontLarge.drawTextOutlined(spriteBatch, _cachedDeathStr, kdPanelX + kdPanelW / 2 + 29f, kdPanelY + 18f, 1f, 0.4f, 0.4f)

    // Kill feed text
    { var fi = 0; while (fi < _feedCount) {
      fontSmall.drawTextOutlined(spriteBatch, _feedText(fi), _feedX(fi) + 2f, _feedY(fi), _feedR(fi), _feedG(fi), _feedB(fi), _feedA(fi))
    ; fi += 1 } }
  }
}
