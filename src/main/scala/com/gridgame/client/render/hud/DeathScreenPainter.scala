package com.gridgame.client.render
package hud

import com.gridgame.client.i18n.Messages
import com.gridgame.common.Constants

import org.lwjgl.opengl.GL11._

import com.gridgame.client.gl.Matrix4

/** The screens of a death: the respawn countdown (who killed us, and when we are back), and the
  * game over screen when there is no coming back. */
private[render] final class DeathScreenPainter(ctx: RenderContext) {
  import ctx._

  // Cached respawn text
  private var _cachedRespawnSeconds = -1; private var _cachedRespawnStr = ""
  private var _cachedKilledByName: String = null; private var _cachedKilledByStr = ""

  def drawGameOver(fbWidth: Int, fbHeight: Int): Unit = {
    val proj = Matrix4.orthographic(0f, fbWidth.toFloat, fbHeight.toFloat, 0f)
    glViewport(0, 0, fbWidth, fbHeight)
    glClearColor(0f, 0f, 0f, 1f)
    glClear(GL_COLOR_BUFFER_BIT)

    val cx = fbWidth / 2f; val cy = fbHeight / 2f
    val t = animTickF

    shapeBatch.begin(proj)
    // Dark vignette background
    shapeBatch.fillRect(0, 0, fbWidth.toFloat, fbHeight.toFloat, 0f, 0f, 0f, 0.8f)
    // Red vignette edges
    val vigAlpha = 0.2f
    shapeBatch.fillRectGradient(0, 0, fbWidth.toFloat, fbHeight * 0.3f,
      0.35f, 0f, 0f, vigAlpha, 0.35f, 0f, 0f, vigAlpha,
      0.35f, 0f, 0f, 0f, 0.35f, 0f, 0f, 0f)
    shapeBatch.fillRectGradient(0, fbHeight * 0.7f, fbWidth.toFloat, fbHeight * 0.3f,
      0.35f, 0f, 0f, 0f, 0.35f, 0f, 0f, 0f,
      0.35f, 0f, 0f, vigAlpha, 0.35f, 0f, 0f, vigAlpha)

    // Central glassmorphism panel
    val panelW = 380f; val panelH = 160f
    val panelX = cx - panelW / 2; val panelY = cy - panelH / 2
    shapeBatch.fillRoundedRectGradient(panelX, panelY, panelW, panelH, 12f,
      0.12f, 0.04f, 0.04f, 0.75f,
      0.06f, 0.02f, 0.02f, 0.65f)
    // Top edge highlight
    shapeBatch.strokeLine(panelX + 12, panelY, panelX + panelW - 12, panelY, 1f, 1f, 1f, 1f, 0.06f)

    // Large skull icon above text
    val skullY = panelY + 30f
    val skullPulse = (Math.sin(t * 0.08) * 0.1 + 0.9).toFloat
    // Skull glow
    shapeBatch.fillOvalSoft(cx, skullY, 22f, 22f, 0.9f, 0.15f, 0.15f, 0.15f * skullPulse, 0f, 12)
    // Cranium
    shapeBatch.fillOval(cx, skullY, 14f, 16f, 0.92f * skullPulse, 0.2f, 0.2f, 0.95f, 16)
    shapeBatch.fillOval(cx - 3.5f, skullY - 1f, 2.5f, 2.5f, 0.08f, 0.02f, 0.02f, 0.95f, 8)
    // Eye sockets
    shapeBatch.fillOval(cx - 5f, skullY - 2.5f, 3.5f, 4f, 0.08f, 0.02f, 0.02f, 0.95f, 8)
    shapeBatch.fillOval(cx + 5f, skullY - 2.5f, 3.5f, 4f, 0.08f, 0.02f, 0.02f, 0.95f, 8)
    // Nose
    shapeBatch.fillOval(cx, skullY + 3f, 1.5f, 2f, 0.12f, 0.04f, 0.04f, 0.9f, 6)
    // Jaw with teeth
    shapeBatch.fillRect(cx - 8f, skullY + 9f, 16f, 5f, 0.85f * skullPulse, 0.18f, 0.18f, 0.85f)
    shapeBatch.strokeLine(cx - 4f, skullY + 9f, cx - 4f, skullY + 13.5f, 0.5f, 0.12f, 0.04f, 0.04f, 0.6f)
    shapeBatch.strokeLine(cx, skullY + 9f, cx, skullY + 13.5f, 0.5f, 0.12f, 0.04f, 0.04f, 0.6f)
    shapeBatch.strokeLine(cx + 4f, skullY + 9f, cx + 4f, skullY + 13.5f, 0.5f, 0.12f, 0.04f, 0.04f, 0.6f)

    // Decorative horizontal line separator
    shapeBatch.strokeLine(panelX + 40, panelY + 60f, panelX + panelW - 40, panelY + 60f, 1f,
      0.9f, 0.25f, 0.25f, 0.3f)
    shapeBatch.end()

    spriteBatch.begin(proj)
    // "GAME OVER" text with dramatic styling
    val gameOverText = Messages.t("GAME OVER")
    fontLarge.drawTextOutlined(spriteBatch, gameOverText, cx - fontLarge.measureWidth(gameOverText) / 2, panelY + 74f, 0.95f * skullPulse, 0.2f, 0.2f, 1f)
    val subText = Messages.t("Press Enter to continue")
    val subPulse = (Math.sin(t * 0.1) * 0.3 + 0.7).toFloat
    fontMedium.drawTextOutlined(spriteBatch, subText, cx - fontMedium.measureWidth(subText) / 2, panelY + 118f, 0.7f, 0.7f, 0.7f, subPulse)
    spriteBatch.end()
  }

  def drawRespawnCountdown(screenW: Int, screenH: Int): Unit = {
    val deathTime = client.getLocalDeathTime
    val elapsed = frameTimeMs - deathTime
    val remaining = Math.max(0, Constants.RESPAWN_DELAY_MS - elapsed)
    val secondsLeft = Math.ceil(remaining / 1000.0).toInt
    val progress = Math.min(1.0, elapsed.toDouble / Constants.RESPAWN_DELAY_MS).toFloat

    val cx = screenW / 2f; val cy = screenH / 2f

    // Red vignette overlay across entire screen
    val vigAlpha = 0.25f * (1f - progress * 0.5f)
    beginShapes()
    // Top edge
    shapeBatch.fillRectGradient(0, 0, screenW.toFloat, screenH * 0.25f,
      0.4f, 0f, 0f, vigAlpha, 0.4f, 0f, 0f, vigAlpha,
      0.4f, 0f, 0f, 0f, 0.4f, 0f, 0f, 0f)
    // Bottom edge
    shapeBatch.fillRectGradient(0, screenH * 0.75f, screenW.toFloat, screenH * 0.25f,
      0.4f, 0f, 0f, 0f, 0.4f, 0f, 0f, 0f,
      0.4f, 0f, 0f, vigAlpha, 0.4f, 0f, 0f, vigAlpha)
    // Left edge
    shapeBatch.fillRectGradient(0, 0, screenW * 0.2f, screenH.toFloat,
      0.4f, 0f, 0f, vigAlpha, 0.4f, 0f, 0f, 0f,
      0.4f, 0f, 0f, 0f, 0.4f, 0f, 0f, vigAlpha)
    // Right edge
    shapeBatch.fillRectGradient(screenW * 0.8f, 0, screenW * 0.2f, screenH.toFloat,
      0.4f, 0f, 0f, 0f, 0.4f, 0f, 0f, vigAlpha,
      0.4f, 0f, 0f, vigAlpha, 0.4f, 0f, 0f, 0f)

    // Central panel (glassmorphism)
    val panelW = 280f; val panelH = 120f
    val panelX = cx - panelW / 2; val panelY = cy - panelH / 2
    shapeBatch.fillRoundedRectGradient(panelX, panelY, panelW, panelH, 10f,
      0.10f, 0.03f, 0.03f, 0.70f,
      0.05f, 0.01f, 0.01f, 0.60f)
    // Top edge highlight
    shapeBatch.strokeLine(panelX + 10, panelY, panelX + panelW - 10, panelY, 1f, 1f, 1f, 1f, 0.06f)

    // Progress bar at bottom of panel
    val barX = panelX + 20f; val barY = panelY + panelH - 18f
    val barW = panelW - 40f; val barH = 6f
    shapeBatch.fillRoundedRect(barX, barY, barW, barH, 3f, 0.15f, 0.05f, 0.05f, 0.6f)
    shapeBatch.fillRoundedRect(barX, barY, barW * progress, barH, 3f, 0.8f, 0.2f, 0.2f, 0.8f)

    // Detailed skull icon above text
    val skullY = panelY + 20f
    // Skull glow
    shapeBatch.fillOvalSoft(cx, skullY, 18f, 18f, 0.9f, 0.15f, 0.15f, 0.12f, 0f, 12)
    // Cranium
    shapeBatch.fillOval(cx, skullY, 11f, 13f, 0.92f, 0.22f, 0.22f, 0.92f, 14)
    // Highlight on cranium
    shapeBatch.fillOval(cx - 3f, skullY - 4f, 4f, 3f, 1f, 0.4f, 0.4f, 0.25f, 8)
    // Eye sockets
    shapeBatch.fillOval(cx - 4f, skullY - 2f, 2.8f, 3f, 0.08f, 0.02f, 0.02f, 0.95f, 8)
    shapeBatch.fillOval(cx + 4f, skullY - 2f, 2.8f, 3f, 0.08f, 0.02f, 0.02f, 0.95f, 8)
    // Nose
    shapeBatch.fillOval(cx, skullY + 2.5f, 1.2f, 1.8f, 0.12f, 0.04f, 0.04f, 0.85f, 6)
    // Jaw with teeth
    shapeBatch.fillRect(cx - 6.5f, skullY + 7f, 13f, 4.5f, 0.85f, 0.2f, 0.2f, 0.8f)
    shapeBatch.strokeLine(cx - 3f, skullY + 7f, cx - 3f, skullY + 11f, 0.5f, 0.12f, 0.04f, 0.04f, 0.55f)
    shapeBatch.strokeLine(cx, skullY + 7f, cx, skullY + 11f, 0.5f, 0.12f, 0.04f, 0.04f, 0.55f)
    shapeBatch.strokeLine(cx + 3f, skullY + 7f, cx + 3f, skullY + 11f, 0.5f, 0.12f, 0.04f, 0.04f, 0.55f)
    // Crack line
    shapeBatch.strokeLine(cx + 2f, skullY - 11f, cx + 4f, skullY - 5f, 0.8f, 0.5f, 0.1f, 0.1f, 0.4f)

    beginSprites()
    // "Killed by [Name]" — prominent
    val killerName = client.lastKillerCharacterName
    if (killerName != null && killerName.nonEmpty) {
      if (killerName ne _cachedKilledByName) {
        _cachedKilledByName = killerName
        _cachedKilledByStr = Messages.t("Killed by {0}", killerName)
      }
      val kbW = fontMedium.measureWidth(_cachedKilledByStr)
      fontMedium.drawTextOutlined(spriteBatch, _cachedKilledByStr, cx - kbW / 2, panelY + 42f, 1f, 0.5f, 0.5f)
    }

    // Respawn countdown
    if (secondsLeft != _cachedRespawnSeconds) {
      _cachedRespawnSeconds = secondsLeft
      _cachedRespawnStr = Messages.t("Respawning in {0}s", secondsLeft)
    }
    val rsW = fontSmall.measureWidth(_cachedRespawnStr)
    fontSmall.drawTextOutlined(spriteBatch, _cachedRespawnStr, cx - rsW / 2, panelY + 72f, 0.7f, 0.7f, 0.7f)
  }
}
