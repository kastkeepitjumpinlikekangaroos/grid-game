package com.gridgame.client.render
package hud

import com.gridgame.client.i18n.Messages

/**
 * Practice's part of the HUD, in the same glass as the rest (hud/HudStyle): where a match has its
 * clock, a pill saying this is practice, with a target in its green and the time the session has
 * left; and under the tallies (which MatchInfoPainter puts where a match's score goes, as practice's
 * results show them) the key that ends it. Its runs of kills are the banner's and the chip's, as a
 * match's are. It was lines of plain text down the left, a green word over the grass where the clock
 * goes, and a white cross over our own character at every kill.
 */
private[render] final class PracticePainter(ctx: RenderContext) {
  import ctx._
  import HudStyle._

  private val style = new HudStyle(ctx)

  private var sPractice, sEnd: String = _
  private val EscKey = "Esc"
  private var _timeFor = -1
  private var _timeStr = ""

  def draw(screenW: Int, screenH: Int): Unit = {
    if (sPractice == null) {
      sPractice = Messages.t("PRACTICE")
      sEnd = Messages.t("End practice")
    }
    drawPill(screenW)
    drawHint()
  }

  /** Where a match's clock is: PRACTICE, and how long the session has left. */
  private def drawPill(screenW: Int): Unit = {
    val remaining = client.gameTimeRemaining
    if (remaining != _timeFor) {
      _timeFor = remaining
      val m = remaining / 60; val s = remaining % 60
      _timeStr = m + (if (s < 10) ":0" else ":") + s
    }
    val labelW = fontBody.measureWidth(sPractice)
    val timeW = fontBody.measureWidth(_timeStr)
    val h = 42f
    val w = Math.round(14f + 20f + 10f + labelW + 14f + 1f + 14f + timeW + 18f).toFloat
    val x = Math.round(screenW / 2f - w / 2f).toFloat
    val y = 12f
    style.glass(x, y, w, h, h / 2f)
    shapeBatch.strokeRoundedRectSmooth(x, y, w, h, h / 2f, 1.5f, LeafR, LeafG, LeafB, 0.55f, 0.3f)
    // A target: rings round a dot, in practice's green (the results' title is lettered in it)
    val tx = x + 14f + 10f; val ty = y + h / 2f
    shapeBatch.strokeOvalSmooth(tx, ty, 9.5f, 9.5f, 2f, LeafR, LeafG, LeafB, 1f)
    shapeBatch.strokeOvalSmooth(tx, ty, 5.5f, 5.5f, 2f, LeafR, LeafG, LeafB, 1f)
    shapeBatch.fillOvalSmooth(tx, ty, 2f, 2f, LeafR, LeafG, LeafB, 1f)
    val divX = Math.round(x + 14f + 20f + 10f + labelW + 14f).toFloat
    shapeBatch.fillRect(divX, y + 12f, 1f, h - 24f, 1f, 1f, 1f, 0.12f)
    beginSprites()
    val mid = fontBody.digitsCentredOn(y + h / 2f)
    fontBody.drawText(spriteBatch, sPractice, x + 14f + 20f + 10f, mid, TextR, TextG, TextB, 1f)
    fontBody.drawText(spriteBatch, _timeStr, divX + 14f, mid, MutedR, MutedG, MutedB, 1f)
  }

  /** Under the tallies: the key that ends the session (Esc brings up the menu with End practice in it). */
  private def drawHint(): Unit = {
    val x = 16f; val y = 12f + 62f + 8f
    val h = 30f
    val kw = style.keycapWidth(EscKey, fontTiny)
    val w = Math.round(7f + kw + 8f + fontTiny.measureWidth(sEnd) + 12f).toFloat
    style.glass(x, y, w, h, 10f, 0.9f)
    val ky = y + (h - KeycapH) / 2f + 1f
    style.keycap(x + 7f, ky, EscKey, fontTiny)
    beginSprites()
    fontTiny.drawText(spriteBatch, sEnd, x + 7f + kw + 8f, fontTiny.digitsCentredOn(ky + (KeycapH - 2f) / 2f),
      BodyR, BodyG, BodyB, 1f)
  }
}
