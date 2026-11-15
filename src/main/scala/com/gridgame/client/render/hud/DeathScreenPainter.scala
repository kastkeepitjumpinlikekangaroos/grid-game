package com.gridgame.client.render
package hud

import com.gridgame.client.gl.DisplayFont.Face
import com.gridgame.client.i18n.Messages
import com.gridgame.common.Constants

import org.lwjgl.opengl.GL11._

import com.gridgame.client.gl.Matrix4

/** The screens of a death, in the HUD's glass (hud/HudStyle): the respawn countdown (who killed us,
  * and when we are back), and the game over screen when there is no coming back. */
private[render] final class DeathScreenPainter(ctx: RenderContext) {
  import ctx._
  import HudStyle._

  private val style = new HudStyle(ctx)

  private var _cachedRespawnSeconds = -1; private var _cachedRespawnStr = ""
  private var sRespawning, sKilledBy, sGameOver, sContinue: String = _
  private val EnterKey = "Enter"

  private def words(): Unit = if (sRespawning == null) {
    sRespawning = Messages.t("RESPAWNING")
    sKilledBy = Messages.t("KILLED BY")
    sGameOver = Messages.t("GAME OVER")
    sContinue = Messages.t("to continue")
  }

  /** No coming back: the night, GAME OVER lettered in the dusk of the scoreboard's, and the key on. */
  def drawGameOver(fbWidth: Int, fbHeight: Int): Unit = {
    words()
    // In the window's units, as the HUD is: the fonts are rasterized for the display's density
    val ps = Math.max(1f, fontSmall.pixelScale)
    val w = fbWidth / ps; val h = fbHeight / ps
    projection = Matrix4.orthographic(0f, w, h, 0f)
    glViewport(0, 0, fbWidth, fbHeight)
    glClearColor(0.031f, 0.047f, 0.118f, 1f)
    glClear(GL_COLOR_BUFFER_BIT)

    val cx = w / 2f; val cy = h / 2f
    val pw = 440f; val ph = 190f
    val px = Math.round(cx - pw / 2f).toFloat; val py = Math.round(cy - ph / 2f).toFloat
    beginShapes()
    shapeBatch.pixelsPerUnit = ps
    style.glass(px, py, pw, ph, 18f)
    beginSprites()
    display.drawCentred(spriteBatch, sGameOver, cx, py + 70f, Face.Dusk, 1f, Math.min(1f, (pw - 40f) / display.measure(sGameOver)))
    val pulse = (Math.sin(animTickF * 0.08) * 0.25 + 0.75).toFloat
    val kw = style.keycapWidth(EnterKey, fontTiny)
    val tw = fontBody.measureWidth(sContinue)
    val lx = Math.round(cx - (kw + 8f + tw) / 2f).toFloat
    val ly = py + ph - 52f
    style.keycap(lx, ly, EnterKey, fontTiny, pulse)
    beginSprites()
    fontBody.drawText(spriteBatch, sContinue, lx + kw + 8f, fontBody.digitsCentredOn(ly + (KeycapH - 2f) / 2f),
      BodyR, BodyG, BodyB, pulse)
    endAll()
  }

  /**
   * Waiting to come back: the edges of the screen reddened, and a pane over the middle with the seconds
   * left lettered big, a bar running out to the respawn, and who killed us, by their character's face
   * and name.
   */
  def drawRespawnCountdown(screenW: Int, screenH: Int): Unit = {
    words()
    val deathTime = client.getLocalDeathTime
    val elapsed = frameTimeMs - deathTime
    val remaining = Math.max(0, Constants.RESPAWN_DELAY_MS - elapsed)
    val secondsLeft = Math.ceil(remaining / 1000.0).toInt
    val progress = Math.min(1.0, elapsed.toDouble / Constants.RESPAWN_DELAY_MS).toFloat
    val cx = screenW / 2f; val cy = screenH / 2f

    // The screen's edges reddened, less as the wait runs out
    val vig = 0.30f * (1f - progress * 0.5f)
    beginShapes()
    shapeBatch.fillRectGradient(0, 0, screenW.toFloat, screenH * 0.25f,
      0.35f, 0f, 0.04f, vig, 0.35f, 0f, 0.04f, vig, 0.35f, 0f, 0.04f, 0f, 0.35f, 0f, 0.04f, 0f)
    shapeBatch.fillRectGradient(0, screenH * 0.75f, screenW.toFloat, screenH * 0.25f,
      0.35f, 0f, 0.04f, 0f, 0.35f, 0f, 0.04f, 0f, 0.35f, 0f, 0.04f, vig, 0.35f, 0f, 0.04f, vig)
    shapeBatch.fillRectGradient(0, 0, screenW * 0.2f, screenH.toFloat,
      0.35f, 0f, 0.04f, vig, 0.35f, 0f, 0.04f, 0f, 0.35f, 0f, 0.04f, 0f, 0.35f, 0f, 0.04f, vig)
    shapeBatch.fillRectGradient(screenW * 0.8f, 0, screenW * 0.2f, screenH.toFloat,
      0.35f, 0f, 0.04f, 0f, 0.35f, 0f, 0.04f, vig, 0.35f, 0f, 0.04f, vig, 0.35f, 0f, 0.04f, 0f)

    if (secondsLeft != _cachedRespawnSeconds) {
      _cachedRespawnSeconds = secondsLeft
      _cachedRespawnStr = secondsLeft.toString
    }
    val killer = client.lastKillerCharacterName
    val hasKiller = killer != null && killer.nonEmpty
    val pw = 340f
    val ph = if (hasKiller) 196f else 140f
    val px = Math.round(cx - pw / 2f).toFloat; val py = Math.round(cy - ph / 2f - 20f).toFloat
    style.tintedGlass(px, py, pw, ph, 18f, BerryR, BerryG, BerryB, 1f, solid = true)
    // The wait running out, under the seconds
    val bx = px + 28f; val bw = pw - 56f; val bby = py + 112f
    shapeBatch.fillRoundedRectSmooth(bx, bby, bw, 6f, 3f, WellR, WellG, WellB, 1f, WellR, WellG, WellB, 1f)
    shapeBatch.fillRoundedRectSmooth(bx, bby, Math.max(6f, bw * progress), 6f, 3f, 1f, 0.55f, 0.62f, 1f, BerryR, BerryG, BerryB, 1f)

    beginSprites()
    val ew = fontTiny.measureWidth(sRespawning)
    fontTiny.drawText(spriteBatch, sRespawning, cx - ew / 2f, py + 16f, MutedR, MutedG, MutedB, 1f)
    // The seconds, landing as each one comes
    val intoSecond = 1f - ((remaining % 1000L) / 1000f)
    val s = if (remaining > 0 && intoSecond < 0.18f) 1f + 0.25f * (1f - intoSecond / 0.18f) else 1f
    display.drawCentred(spriteBatch, _cachedRespawnStr, cx, py + 66f, Face.Berry, 1f, 0.9f * s)

    if (hasKiller) {
      // Who killed us: their face in a well, and their name
      val bust = 40f
      val nameW = fontBody.measureWidth(killer)
      val kbW = fontTiny.measureWidth(sKilledBy)
      val rowW = bust + 12f + Math.max(nameW, kbW)
      val rx = Math.round(cx - rowW / 2f).toFloat
      val ry = py + 134f
      style.well(rx, ry, bust, bust, 10f)
      style.bust(client.lastKillerCharacter, rx, ry, bust)
      beginSprites()
      fontTiny.drawText(spriteBatch, sKilledBy, rx + bust + 12f, ry + 1f, MutedR, MutedG, MutedB, 1f)
      fontBody.drawText(spriteBatch, killer, rx + bust + 12f, fontBody.digitsCentredOn(ry + 28f), TextR, TextG, TextB, 1f)
    }
  }
}
