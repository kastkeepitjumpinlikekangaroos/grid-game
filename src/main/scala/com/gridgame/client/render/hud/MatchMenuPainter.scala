package com.gridgame.client.render
package hud

import com.gridgame.client.gl.{GLFontRenderer, RenderQuality}
import com.gridgame.client.i18n.Messages

/**
 * The menu Esc brings up in a match (MatchMenu, which lays it out and says what each control does),
 * drawn over the whole frame — the HUD and the scoreboard included — in the window's units: a shade
 * over the game, which still shows and still moves under it, and a card of the menus' dark glass
 * (ui/CLAUDE.md, *The look*) with the graphics settings, their picked option in the HUD's gold, and
 * the way back to the match (gold) or out of it (red, and asking for its second press). Where the
 * keyboard or a controller is, once either has been used, is ringed in sky blue.
 */
private[render] final class MatchMenuPainter(ctx: RenderContext, menu: MatchMenu) {
  import ctx._
  import MatchMenu._

  // Its words, made once: a match's language doesn't change while it is played
  private var wordsMade = false
  private var sTitle, sBack, sQuality, sResolution, sZoom, sResume, sLeave, sEndPractice, sLeaveAgain,
    sEndAgain, sScoreboard, sFullScreen, sMark: String = _
  private val qualityNames = new Array[String](3)
  private val resolutionNames = new Array[String](MaxResolutions)
  private var resolutionNamesFor = -1
  private val zoomNames = new Array[String](RenderQuality.Zooms.size)

  private def makeWords(): Unit = {
    if (wordsMade) return
    val upper = (s: String) => s.toUpperCase(Messages.currentLocale)
    sTitle = Messages.t("Menu")
    sBack = Messages.t("Back to the match")
    sQuality = upper(Messages.t("Graphics quality"))
    sResolution = upper(Messages.t("Resolution"))
    sZoom = upper(Messages.t("Camera zoom"))
    sResume = Messages.t("Resume")
    sLeave = Messages.t("Leave match")
    sEndPractice = Messages.t("End practice")
    sLeaveAgain = Messages.t("Press again to leave")
    sEndAgain = Messages.t("Press again to end practice")
    sScoreboard = Messages.t("Scoreboard")
    sFullScreen = Messages.t("Full screen")
    sMark = Messages.t("Mark a moment")
    qualityNames(0) = Messages.t("High"); qualityNames(1) = Messages.t("Medium"); qualityNames(2) = Messages.t("Low")
    var i = 0
    RenderQuality.Zooms.foreach { z =>
      zoomNames(i) = if (z == RenderQuality.MinZoom) Messages.t("{0}% (widest)", z.toString) else s"$z%"
      i += 1
    }
    wordsMade = true
  }

  /** The resolutions' names as offered on this display ("Native", "1080p"), made when they change. */
  private def nameResolutions(): Unit = {
    val key = menu.resolutionCount * 10000 + (if (menu.resolutionCount > 1) menu.resolutionAt(1) else 0)
    if (key == resolutionNamesFor) return
    var i = 0
    while (i < menu.resolutionCount) {
      val r = menu.resolutionAt(i)
      resolutionNames(i) = if (r == 0) Messages.t("Native") else s"${r}p"
      i += 1
    }
    resolutionNamesFor = key
  }

  // The palette, the menus' own (ui/theme.css): text, muted, gold, sky for focus, berry for leaving
  private final val InkR = 0.933f; private final val InkG = 0.949f; private final val InkB = 1.0f
  private final val MutedR = 0.545f; private final val MutedG = 0.588f; private final val MutedB = 0.733f
  private final val GoldR = 1.0f; private final val GoldG = 0.788f; private final val GoldB = 0.247f
  private final val OnGoldR = 0.10f; private final val OnGoldG = 0.07f; private final val OnGoldB = 0.02f
  private final val SkyR = 0.30f; private final val SkyG = 0.72f; private final val SkyB = 1.0f
  private final val BerryR = 1.0f; private final val BerryG = 0.37f; private final val BerryB = 0.46f

  def draw(screenW: Int, screenH: Int, nativeLines: Int): Unit = {
    if (!menu.isOpen) return
    makeWords()
    menu.layout(screenW.toFloat, screenH.toFloat, nativeLines)
    nameResolutions()

    beginShapes()
    // The game, dimmed but there
    shapeBatch.fillRect(0f, 0f, screenW.toFloat, screenH.toFloat, 0.01f, 0.015f, 0.04f, 0.52f)
    // The card: navy glass, a hairline of light round it, brightest along the top. One you can't see
    // through: the name plates under it are white text too, and with no blur behind it they read
    // through it even at 97%
    val px = menu.panelX; val py = menu.panelY; val pw = menu.panelW; val ph = menu.panelH
    shapeBatch.fillRoundedRectGradient(px, py, pw, ph, 16f,
      0.055f, 0.075f, 0.16f, 0.995f,
      0.03f, 0.04f, 0.095f, 1f)
    shapeBatch.strokeRoundedRect(px, py, pw, ph, 16f, 1f, 1f, 1f, 1f, 0.11f)
    shapeBatch.strokeLine(px + 18f, py + 0.5f, px + pw - 18f, py + 0.5f, 1f, 1f, 1f, 1f, 0.16f)
    // Under the title
    shapeBatch.strokeLine(px + Pad, menu.ruleY, px + pw - Pad, menu.ruleY, 1f, 1f, 1f, 1f, 0.08f)

    // The quality and the resolution: a row of options each, the picked one gold
    segments(QualityBase, 3, RenderQuality.tier, RowQuality)
    segments(ResolutionBase, menu.resolutionCount, menu.pickedResolution, RowResolution)

    // The zoom: a step out, its track, a step in
    stepButton(ZoomOut, plus = false, enabled = RenderQuality.zoomPercent > RenderQuality.MinZoom)
    stepButton(ZoomIn, plus = true, enabled = RenderQuality.zoomPercent < RenderQuality.MaxZoom)
    track()
    if (showFocus(RowZoom)) focusRing(menu.x(ZoomOut), menu.y(ZoomOut),
      menu.x(ZoomIn) + menu.w(ZoomIn) - menu.x(ZoomOut), menu.h(ZoomIn))

    // Out of the match, and back to it
    leaveButton()
    resumeButton()

    // The keys worth knowing, along the bottom
    val hintW = hintsWidth()
    var hx = px + (pw - hintW) / 2
    hx = keycapShape(hx, menu.hintMidY, "Tab") + 6f + fontSmall.measureWidth(sScoreboard) + HintGap
    hx = keycapShape(hx, menu.hintMidY, "F11") + 6f + fontSmall.measureWidth(sFullScreen) + HintGap
    keycapShape(hx, menu.hintMidY, "F8")
    // and Esc, beside the title
    val backW = fontSmall.measureWidth(sBack)
    val escX = px + pw - Pad - backW - 6f - keycapWidth("Esc")
    keycapShape(escX, menu.titleMidY, "Esc")

    // ── The words ──
    beginSprites()
    val titleScale = 0.62f
    fontLarge.drawText(spriteBatch, sTitle, px + Pad, menu.titleMidY + titleScale * fontLarge.digitsCentredOn(0f),
      InkR, InkG, InkB, 1f, titleScale)
    fontSmall.drawText(spriteBatch, sBack, px + pw - Pad - backW, fontSmall.digitsCentredOn(menu.titleMidY),
      MutedR, MutedG, MutedB, 1f)
    keycapText(escX, menu.titleMidY, "Esc")

    label(RowQuality, sQuality)
    label(RowResolution, sResolution)
    label(RowZoom, sZoom)
    // The zoom's value, at the end of its label's line
    val zi = Math.max(0, Math.min(zoomNames.length - 1, (RenderQuality.zoomPercent - RenderQuality.MinZoom) / RenderQuality.ZoomStep))
    val zoomName = zoomNames(zi)
    fontSmall.drawText(spriteBatch, zoomName, px + pw - Pad - fontSmall.measureWidth(zoomName),
      fontSmall.digitsCentredOn(menu.rowLabelY(RowZoom) + LabelH / 2), GoldR, GoldG, GoldB, 1f)

    segmentWords(QualityBase, 3, RenderQuality.tier, qualityNames)
    segmentWords(ResolutionBase, menu.resolutionCount, menu.pickedResolution, resolutionNames)

    val practice = client.isPracticeMode
    val leaveWords = if (menu.leaveArmed) (if (practice) sEndAgain else sLeaveAgain) else (if (practice) sEndPractice else sLeave)
    centred(fontMedium, leaveWords, Leave, if (menu.leaveArmed) 1f else BerryR, if (menu.leaveArmed) 1f else 0.78f,
      if (menu.leaveArmed) 1f else 0.80f)
    centred(fontMedium, sResume, Resume, OnGoldR, OnGoldG, OnGoldB)

    hx = px + (pw - hintW) / 2
    hx = hint(hx, "Tab", sScoreboard)
    hx = hint(hx, "F11", sFullScreen)
    hint(hx, "F8", sMark)
  }

  private def showFocus(row: Int): Boolean = menu.focusVisible && menu.focusRow == row

  // ── Shapes ─────────────────────────────────────────────────────────────

  /** A row of `n` options from control `base`, the `picked` one gold. */
  private def segments(base: Int, n: Int, picked: Int, row: Int): Unit = {
    if (n <= 0) return
    val x0 = menu.x(base); val y0 = menu.y(base); val h = menu.h(base)
    val w = menu.x(base + n - 1) + menu.w(base + n - 1) - x0
    // The well they sit in
    shapeBatch.fillRoundedRect(x0, y0, w, h, 10f, 1f, 1f, 1f, 0.05f)
    shapeBatch.strokeRoundedRect(x0, y0, w, h, 10f, 1f, 1f, 1f, 1f, 0.10f)
    var i = 0
    while (i < n) {
      val id = base + i
      val sx = menu.x(id); val sw = menu.w(id)
      if (i == picked) {
        shapeBatch.fillRoundedRectGradient(sx + 3f, y0 + 3f, sw - 6f, h - 6f, 8f,
          1.0f, 0.86f, 0.42f, 1f,
          0.97f, 0.70f, 0.20f, 1f)
      } else {
        if (menu.pressed == id) shapeBatch.fillRoundedRect(sx + 3f, y0 + 3f, sw - 6f, h - 6f, 8f, 1f, 1f, 1f, 0.14f)
        else if (menu.hover == id) shapeBatch.fillRoundedRect(sx + 3f, y0 + 3f, sw - 6f, h - 6f, 8f, 1f, 1f, 1f, 0.08f)
        // A hairline between two options neither of which is picked
        if (i > 0 && i - 1 != picked) shapeBatch.strokeLine(sx, y0 + 10f, sx, y0 + h - 10f, 1f, 1f, 1f, 1f, 0.10f)
      }
      i += 1
    }
    if (showFocus(row)) focusRing(x0, y0, w, h)
  }

  /** The zoom's − or +: a glass square with its sign, dim when there is no further to go. */
  private def stepButton(id: Int, plus: Boolean, enabled: Boolean): Unit = {
    val x = menu.x(id); val y = menu.y(id); val w = menu.w(id); val h = menu.h(id)
    val fill = if (!enabled) 0.03f else if (menu.pressed == id) 0.16f else if (menu.hover == id) 0.10f else 0.06f
    shapeBatch.fillRoundedRect(x, y, w, h, 10f, 1f, 1f, 1f, fill)
    shapeBatch.strokeRoundedRect(x, y, w, h, 10f, 1f, 1f, 1f, 1f, if (enabled) 0.12f else 0.06f)
    val a = if (enabled) 0.95f else 0.3f
    val cx = x + w / 2; val cy = y + h / 2
    shapeBatch.strokeLine(cx - 7f, cy, cx + 7f, cy, 2.2f, InkR, InkG, InkB, a)
    if (plus) shapeBatch.strokeLine(cx, cy - 7f, cx, cy + 7f, 2.2f, InkR, InkG, InkB, a)
  }

  /** The zoom's track: a groove, gold as far as the zoom, a dot at each step, and the thumb. */
  private def track(): Unit = {
    val x0 = menu.trackX0; val x1 = menu.trackX1; val cy = menu.trackMidY
    val t = menu.zoomFraction
    val tx = x0 + (x1 - x0) * t
    val hot = menu.hover == ZoomTrack || menu.pressed == ZoomTrack
    shapeBatch.fillRoundedRect(x0, cy - 3f, x1 - x0, 6f, 3f, 1f, 1f, 1f, if (hot) 0.16f else 0.12f)
    if (tx > x0 + 1f) shapeBatch.fillRoundedRect(x0, cy - 3f, tx - x0, 6f, 3f, GoldR, GoldG, GoldB, 0.95f)
    val steps = (RenderQuality.MaxZoom - RenderQuality.MinZoom) / RenderQuality.ZoomStep
    var s = 0
    while (s <= steps) {
      val sx = x0 + (x1 - x0) * s / steps
      val on = sx <= tx + 0.5f
      shapeBatch.fillOvalSmooth(sx, cy + 11f, 1.6f, 1.6f, 1f, 1f, 1f, if (on) 0.45f else 0.22f)
      s += 1
    }
    // The thumb: gold, inked round its edge, lit at its heart
    val r = if (menu.pressed == ZoomTrack) 10.5f else if (hot) 10f else 9f
    shapeBatch.fillOvalSmooth(tx, cy, r + 1.8f, r + 1.8f, 0.06f, 0.05f, 0.03f, 0.85f)
    shapeBatch.fillOvalSmooth(tx, cy, r, r, GoldR, GoldG, GoldB, 1f)
    shapeBatch.fillOvalSmooth(tx, cy - 1f, r * 0.35f, r * 0.35f, 1f, 0.97f, 0.85f, 0.9f)
  }

  /** Out of the match: red glass, solid red once pressed and waiting for its second press. */
  private def leaveButton(): Unit = {
    val x = menu.x(Leave); val y = menu.y(Leave); val w = menu.w(Leave); val h = menu.h(Leave)
    if (menu.leaveArmed) {
      shapeBatch.fillRoundedRectGradient(x, y, w, h, 12f, 0.86f, 0.20f, 0.27f, 1f, 0.68f, 0.10f, 0.17f, 1f)
      shapeBatch.strokeRoundedRect(x, y, w, h, 12f, 1f, 1f, 0.55f, 0.6f, 0.9f)
    } else {
      val lift = if (menu.pressed == Leave) 0.10f else if (menu.hover == Leave) 0.06f else 0f
      shapeBatch.fillRoundedRect(x, y, w, h, 12f, 0.26f + lift, 0.05f + lift * 0.4f, 0.09f + lift * 0.4f, 0.85f)
      shapeBatch.strokeRoundedRect(x, y, w, h, 12f, 1f, BerryR, BerryG, BerryB, if (menu.hover == Leave) 0.75f else 0.5f)
    }
    if (menu.focusVisible && menu.focusRow == RowActions && menu.focusAction == Leave) focusRing(x, y, w, h)
  }

  /** Back to the match: the HUD's gold, lit from above, a darker lip a press closes. */
  private def resumeButton(): Unit = {
    val x = menu.x(Resume); val w = menu.w(Resume); val h = menu.h(Resume)
    val down = menu.pressed == Resume
    val y = menu.y(Resume) + (if (down) 2f else 0f)
    if (!down) shapeBatch.fillRoundedRect(x, y + 3f, w, h - 1f, 12f, 0.62f, 0.38f, 0.06f, 1f)
    val lift = if (menu.hover == Resume && !down) 0.04f else 0f
    shapeBatch.fillRoundedRectGradient(x, y, w, h - (if (down) 2f else 3f), 12f,
      1.0f, 0.88f + lift, 0.48f + lift, 1f,
      0.98f, 0.72f + lift, 0.22f + lift, 1f)
    shapeBatch.strokeLine(x + 12f, y + 1f, x + w - 12f, y + 1f, 1f, 1f, 1f, 0.85f, 0.7f)
    if (menu.focusVisible && menu.focusRow == RowActions && menu.focusAction == Resume) focusRing(x, menu.y(Resume), w, h)
  }

  /** Where the keyboard or a controller is: sky blue, just outside. */
  private def focusRing(x: Float, y: Float, w: Float, h: Float): Unit =
    shapeBatch.strokeRoundedRect(x - 3f, y - 3f, w + 6f, h + 6f, 13f, 2f, SkyR, SkyG, SkyB, 0.95f)

  private def keycapWidth(key: String): Float = fontSmall.measureWidth(key) + 12f

  /** A key's cap, at x along a line through cy: where whatever follows it starts. */
  private def keycapShape(x: Float, cy: Float, key: String): Float = {
    val w = keycapWidth(key)
    shapeBatch.fillRoundedRect(x, cy - 11f, w, 22f, 5f, 1f, 1f, 1f, 0.07f)
    shapeBatch.strokeRoundedRect(x, cy - 11f, w, 22f, 5f, 1f, 1f, 1f, 1f, 0.22f)
    shapeBatch.strokeLine(x + 3f, cy + 10.5f, x + w - 3f, cy + 10.5f, 1f, 1f, 1f, 1f, 0.12f)
    x + w
  }

  private final val HintGap = 22f

  private def hintsWidth(): Float =
    keycapWidth("Tab") + 6f + fontSmall.measureWidth(sScoreboard) + HintGap +
      keycapWidth("F11") + 6f + fontSmall.measureWidth(sFullScreen) + HintGap +
      keycapWidth("F8") + 6f + fontSmall.measureWidth(sMark)

  // ── Words ──────────────────────────────────────────────────────────────

  private def keycapText(x: Float, cy: Float, key: String): Unit =
    fontSmall.drawText(spriteBatch, key, x + 6f, fontSmall.digitsCentredOn(cy), InkR, InkG, InkB, 0.9f)

  /** A key and what it does, from x: where the next starts. */
  private def hint(x: Float, key: String, what: String): Float = {
    keycapText(x, menu.hintMidY, key)
    val tx = x + keycapWidth(key) + 6f
    fontSmall.drawText(spriteBatch, what, tx, fontSmall.digitsCentredOn(menu.hintMidY), MutedR, MutedG, MutedB, 1f)
    tx + fontSmall.measureWidth(what) + HintGap
  }

  private def label(row: Int, words: String): Unit =
    fontSmall.drawText(spriteBatch, words, menu.panelX + Pad, fontSmall.digitsCentredOn(menu.rowLabelY(row) + LabelH / 2),
      MutedR, MutedG, MutedB, 1f)

  private def segmentWords(base: Int, n: Int, picked: Int, names: Array[String]): Unit = {
    var i = 0
    while (i < n) {
      if (i == picked) centred(fontMedium, names(i), base + i, OnGoldR, OnGoldG, OnGoldB)
      else centred(fontMedium, names(i), base + i, InkR, InkG, InkB)
      i += 1
    }
  }

  /** `words` in the middle of control `id`, made smaller if they would run past its sides (a long
    * translation, "Press again to end practice"). */
  private def centred(font: GLFontRenderer, words: String, id: Int, r: Float, g: Float, b: Float): Unit = {
    val down = if ((id == Resume || id == Leave) && menu.pressed == id) 2f else 0f
    val cy = menu.y(id) + menu.h(id) / 2 + down - (if (id == Resume) 1.5f else 0f)
    val room = menu.w(id) - 20f
    val width = font.measureWidth(words)
    if (width <= room)
      font.drawText(spriteBatch, words, menu.x(id) + (menu.w(id) - width) / 2, font.digitsCentredOn(cy), r, g, b, 1f)
    else {
      val scale = room / width
      font.drawText(spriteBatch, words, menu.x(id) + 10f, cy + scale * font.digitsCentredOn(0f), r, g, b, 1f, scale)
    }
  }
}
