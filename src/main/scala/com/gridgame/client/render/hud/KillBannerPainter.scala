package com.gridgame.client.render
package hud

import com.gridgame.client.game.ClientState
import com.gridgame.client.gl.DisplayFont.Face
import com.gridgame.client.i18n.Messages

/**
 * A kill of ours announced big over the top of the screen (render/CLAUDE.md, *A kill is announced*):
 * what our run of kills in a row has come to, lettered as the menus' titles are ("DOUBLE KILL",
 * "RAMPAGE"), with the count beside it from the second on, and whom we took under it. It pops in, holds
 * a couple of seconds and goes; the next kill pops it in again a count higher, hotter in colour as the
 * run goes on (gold, ember, berry, violet). It was a line in the feed in the corner, the size of
 * everyone else's.
 *
 * Everything it says is made once: the titles as the first frame needs them, the counts the first
 * time each is shown. Drawn in the window's units, over the HUD, allocating nothing.
 */
private[render] final class KillBannerPainter(ctx: RenderContext) {
  import KillBannerPainter._
  import HudStyle._
  import ctx._

  private val style = new HudStyle(ctx)

  private var titles: Array[String] = _
  private var firstBlood: String = _
  private val counts = new Array[String](MaxCount + 1)
  // What has been rasterized by the display font, which is made again with the fonts
  private var warmedFor: AnyRef = null

  private def words(): Unit = {
    if (titles != null && (warmedFor eq display)) return
    if (titles == null) {
      titles = TitleKeys.map(k => Messages.t(k))
      firstBlood = Messages.t("FIRST BLOOD")
    }
    // The first kill is no moment to rasterize a title: every one now, as the match begins
    titles.foreach(display.prewarm)
    display.prewarm(firstBlood)
    display.prewarm(Times + "0123456789")
    warmedFor = display
  }

  private def countText(n: Int): String = {
    val i = Math.min(n, MaxCount)
    var s = counts(i)
    if (s == null) { s = Times + i; counts(i) = s }
    s
  }

  def draw(screenW: Int, screenH: Int): Unit = {
    if (client.clientState != ClientState.PLAYING) return
    words()
    val k = client.lastKill
    if (k == null) return
    val t = (frameTimeMs - k.atMs).toFloat
    if (t < 0f || t >= ShowMs) return

    val run = k.streak
    val face = faceFor(run)
    val title = if (k.firstBlood && run == 1) firstBlood else titles(titleIndex(run))
    val count = if (run >= 2) countText(run) else null

    // In over its first 70ms, out over its last 380 as it rises a little
    val a = clamp(t / 70f) * clamp((ShowMs - t) / FadeMs)
    val rise = (1f - clamp((ShowMs - t) / FadeMs)) * 12f
    val cx = screenW / 2f
    val cy = Math.round(Math.max(104f, screenH * 0.18f)) - rise

    // The title and the count as one line, centred, no wider than most of the screen
    val titleW0 = display.measure(title)
    val countW0 = if (count != null) display.measure(count) * CountScale else 0f
    val gap = if (count != null) 18f else 0f
    val fit = Math.min(1f, screenW * 0.86f / (titleW0 + gap + countW0))
    val titleS = fit * pop(t, 1.5f)
    val countS = fit * CountScale * pop(t - 70f, 2.1f)
    val lineW = (titleW0 + gap + countW0) * fit
    val titleCx = cx - lineW / 2f + titleW0 * fit / 2f
    val countCx = cx + lineW / 2f - countW0 * fit / 2f

    // A burst of the face's colour behind it, brightest as it lands
    val flash = 0.45f + 0.55f * (1f - clamp(t / 450f))
    beginShapes()
    shapeBatch.setAdditiveBlend(true)
    shapeBatch.fillOvalSoft(cx, cy, lineW * 0.62f + 40f, 74f, face.midR, face.midG, face.midB, 0.30f * a * flash, 0f, 40)
    shapeBatch.setAdditiveBlend(false)
    if (run >= 5) {
      // A ring going out from it as it lands, for a run worth one: its colour inked, so it shows on snow
      val ring = clamp(t / 520f)
      if (ring < 1f) {
        val rx = lineW * (0.45f + 0.35f * ring) + 30f; val ry = 46f + 40f * ring
        val ra = (1f - ring) * a
        shapeBatch.strokeOvalSmooth(cx, cy, rx, ry, 5f, face.inkR, face.inkG, face.inkB, 0.55f * ra)
        shapeBatch.strokeOvalSmooth(cx, cy, rx, ry, 2.5f, face.midR, face.midG, face.midB, 0.95f * ra)
      }
    }

    beginSprites()
    display.drawCentred(spriteBatch, title, titleCx, cy, face, a, titleS)
    if (count != null && t >= 70f) display.drawCentred(spriteBatch, count, countCx, cy, face, a, countS)

    // Light running over the letters once they have landed
    if (t > 140f && t < 140f + GlintMs) {
      val p = (t - 140f) / GlintMs
      val left = cx - lineW / 2f
      val litX = left - 60f + (lineW + 120f) * p
      spriteBatch.setAdditiveBlend(true)
      display.drawGlint(spriteBatch, title, titleCx - titleW0 * titleS / 2f, cy + display.capHeight * titleS / 2f,
        litX, 70f, 0.55f * a, titleS)
      if (count != null && t >= 70f)
        display.drawGlint(spriteBatch, count, countCx - countW0 / CountScale * countS / 2f,
          cy + display.capHeight * countS / 2f, litX, 70f, 0.55f * a, countS)
      spriteBatch.setAdditiveBlend(false)
    }

    // Whom it was, on a pill under the title, rising into place a moment after it
    val sub = clamp((t - 110f) / 160f)
    if (sub > 0f && k.victimName != null) {
      val sa = a * sub
      val name = k.victimName
      val bustS = 30f
      val nameW = fontBody.measureWidth(name)
      val pw = 10f + bustS + 8f + nameW + 16f
      val ph = 38f
      val px = Math.round(cx - pw / 2f).toFloat
      val py = Math.round(cy + display.capHeight * fit * 0.5f + display.shadowDrop * fit + 18f + (1f - sub) * 8f).toFloat
      style.glass(px, py, pw, ph, ph / 2f, sa)
      shapeBatch.strokeRoundedRectSmooth(px, py, pw, ph, ph / 2f, 1.5f, face.midR, face.midG, face.midB, 0.55f * sa, 0.3f * sa)
      // Rules either side, in the face's colour, fading out
      val ruleY = py + ph / 2f
      shapeBatch.fillRectGradient(px - 90f, ruleY - 1f, 80f, 2f,
        face.midR, face.midG, face.midB, 0f, face.midR, face.midG, face.midB, 0.7f * sa,
        face.midR, face.midG, face.midB, 0.7f * sa, face.midR, face.midG, face.midB, 0f)
      shapeBatch.fillRectGradient(px + pw + 10f, ruleY - 1f, 80f, 2f,
        face.midR, face.midG, face.midB, 0.7f * sa, face.midR, face.midG, face.midB, 0f,
        face.midR, face.midG, face.midB, 0f, face.midR, face.midG, face.midB, 0.7f * sa)
      style.well(px + 5f, py + (ph - bustS) / 2f, bustS, bustS, bustS / 2f, sa)
      style.bust(k.victimCharacter, px + 5f, py + (ph - bustS) / 2f, bustS, sa)
      beginSprites()
      fontBody.drawText(spriteBatch, name, px + 10f + bustS + 8f, fontBody.digitsCentredOn(py + ph / 2f), TextR, TextG, TextB, sa)
    }
  }
}

private[render] object KillBannerPainter {
  /** How long a kill is shown, and how long it takes to go. */
  val ShowMs = 2400f
  val FadeMs = 380f
  /** How long the light takes to cross the title. */
  val GlintMs = 520f
  /** The count beside the title, bigger than its letters. */
  val CountScale = 1.18f
  val MaxCount = 999
  val Times = "×"

  /** The title of each run of kills in a row: the first, the second, and so on; the last for every run
    * past it. Their words are the catalog's keys. */
  val TitleKeys: Array[String] =
    Array("ELIMINATED", "DOUBLE KILL", "TRIPLE KILL", "MEGA KILL", "RAMPAGE", "UNSTOPPABLE", "GODLIKE", "LEGENDARY")

  def titleIndex(run: Int): Int = Math.max(0, Math.min(TitleKeys.length - 1, run - 1))

  /** A run's colour, hotter as it goes on: gold for the first two, then ember, berry, violet. */
  def faceFor(run: Int): Face =
    if (run <= 2) Face.Gold else if (run <= 4) Face.Ember else if (run <= 6) Face.Berry else Face.Violet

  /**
   * How big a title is `t` ms after it was put up, `from` times its size at first: it lands over 110ms,
   * a little under its size, and settles on it by 190.
   */
  def pop(t: Float, from: Float): Float =
    if (t <= 0f) from
    else if (t < 110f) {
      val p = t / 110f
      val e = 1f - (1f - p) * (1f - p) * (1f - p)
      from + (0.93f - from) * e
    } else if (t < 190f) 0.93f + 0.07f * ((t - 110f) / 80f)
    else 1f

}
