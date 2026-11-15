package com.gridgame.client.render

import com.gridgame.client.gl.RenderQuality

/**
 * The menu Esc brings up in a match (drawn by hud/MatchMenuPainter): the graphics settings — the
 * quality, the resolution the world is drawn at, the camera's zoom — each taken and saved as it is
 * picked, as the settings' card in the menus takes them (ui/screens/SettingsDialog), and the way out
 * of the match. Esc again, or Resume, closes it. Nothing pauses a match other people are playing, so
 * the match goes on under it and shows through it: a zoom or a quality picked is seen at once.
 *
 * While it is open it has the keyboard, the mouse and a controller (the input handlers ask
 * [[isOpen]]): nothing walks, aims, fires or casts, and what was aimed or held is put away as it
 * opens. The mouse works it as any menu (a press and a release on the same control, the zoom's track
 * dragged); the arrow keys or a controller's pad move between its rows and change what is on them,
 * Enter or the pad's A presses the button picked.
 *
 * Leaving takes two presses of its button, three seconds apart at most, the first only arming it: it
 * replaced Esc pressed twice, whose first press armed it so that a stray Esc (one meant to close the
 * chat) couldn't end a match.
 *
 * Everything here is in the window's units (the HUD's), laid out for the window it was last drawn in
 * ([[layout]]). Only the render thread touches it: GLFW's callbacks come on it, in the frame's event
 * poll.
 */
final class MatchMenu(clock: () => Long = () => System.currentTimeMillis()) {
  import MatchMenu._

  private var _open = false
  private var _hover = NoControl
  private var _pressed = NoControl
  private var dragging = false
  private var _focusRow = RowActions
  private var _focusAction = Resume
  private var _focusVisible = false
  private var leaveArmedUntil = 0L

  /** What leaving does (MatchWindow's leaveMatch): called once the second press confirms it. */
  var onLeave: () => Unit = () => ()

  def isOpen: Boolean = _open
  /** The control under the mouse, or NoControl. */
  def hover: Int = _hover
  /** The control the mouse went down on and hasn't come up from, or NoControl. */
  def pressed: Int = _pressed
  /** The row the keyboard or a controller is on, and on the actions' row, which of the two. */
  def focusRow: Int = _focusRow
  def focusAction: Int = _focusAction
  /** Whether to show where the keyboard is: only once it has been used, never for the mouse. */
  def focusVisible: Boolean = _focusVisible
  /** Whether the leave button has been pressed once and waits for the second. */
  def leaveArmed: Boolean = leaveArmedUntil != 0L && clock() < leaveArmedUntil

  def open(): Unit = {
    _open = true
    _hover = NoControl
    _pressed = NoControl
    dragging = false
    _focusRow = RowActions
    _focusAction = Resume
    _focusVisible = false
    leaveArmedUntil = 0L
  }

  def close(): Unit = {
    _open = false
    _pressed = NoControl
    dragging = false
    leaveArmedUntil = 0L
  }

  def toggle(): Unit = if (_open) close() else open()

  // ── The layout ─────────────────────────────────────────────────────────

  // Each control's rectangle, by id
  private val cx = new Array[Float](ControlCount)
  private val cy = new Array[Float](ControlCount)
  private val cw = new Array[Float](ControlCount)
  private val ch = new Array[Float](ControlCount)
  private var laidOut = false
  // The resolutions offered on this display (RenderQuality.resolutionsFor), in the order shown
  private val _resolutions = new Array[Int](MaxResolutions)
  private var _resolutionCount = 0
  private var lastNativeLines = -1

  var panelX = 0f; var panelY = 0f; var panelW = 0f; var panelH = 0f
  /** Where each row's label goes (its top), the title's middle, and the line under the title. */
  var titleMidY = 0f; var ruleY = 0f
  val rowLabelY = new Array[Float](RowCount)
  /** The zoom's track, inside its control: its two ends and its middle. */
  var trackX0 = 0f; var trackX1 = 0f; var trackMidY = 0f
  /** Where the line of keys under the buttons goes (its middle). */
  var hintMidY = 0f

  def x(id: Int): Float = cx(id)
  def y(id: Int): Float = cy(id)
  def w(id: Int): Float = cw(id)
  def h(id: Int): Float = ch(id)
  def resolutionCount: Int = _resolutionCount
  def resolutionAt(i: Int): Int = _resolutions(i)

  private def place(id: Int, px: Float, py: Float, pw: Float, ph: Float): Unit = {
    cx(id) = px; cy(id) = py; cw(id) = pw; ch(id) = ph
  }

  /** Lay it out for a window `screenW` x `screenH` units on a display `nativeLines` tall (the
    * framebuffer's height: the resolutions offered are its own and those below it). Arithmetic only,
    * every frame it is drawn. */
  def layout(screenW: Float, screenH: Float, nativeLines: Int): Unit = {
    if (nativeLines != lastNativeLines) {
      lastNativeLines = nativeLines
      _resolutionCount = 0
      RenderQuality.resolutionsFor(nativeLines).foreach { r =>
        if (_resolutionCount < MaxResolutions) { _resolutions(_resolutionCount) = r; _resolutionCount += 1 }
      }
    }
    // Hidden resolution segments take no room and no clicks
    var i = _resolutionCount
    while (i < MaxResolutions) { place(ResolutionBase + i, 0f, 0f, 0f, 0f); i += 1 }

    panelW = Math.min(PanelWidth, screenW - 32f)
    val contentW = panelW - Pad * 2
    // Worked out from the top, then the whole moved to the middle of the window
    var yy = Pad
    titleMidY = yy + TitleH / 2
    yy += TitleH + 10f
    ruleY = yy
    yy += 18f
    var row = 0
    while (row < RowCount - 1) {
      rowLabelY(row) = yy
      yy += LabelH + 8f
      row match {
        case RowQuality =>
          val segW = contentW / 3
          var t = 0
          while (t < 3) { place(QualityBase + t, Pad + t * segW, yy, segW, ControlH); t += 1 }
        case RowResolution =>
          val n = Math.max(1, _resolutionCount)
          val segW = contentW / n
          var r = 0
          while (r < _resolutionCount) { place(ResolutionBase + r, Pad + r * segW, yy, segW, ControlH); r += 1 }
        case _ =>
          place(ZoomOut, Pad, yy, StepW, ControlH)
          place(ZoomIn, Pad + contentW - StepW, yy, StepW, ControlH)
          place(ZoomTrack, Pad + StepW + 8f, yy, contentW - (StepW + 8f) * 2, ControlH)
      }
      yy += ControlH + RowGap
      row += 1
    }
    yy += 8f
    rowLabelY(RowActions) = yy
    val buttonW = (contentW - 14f) / 2
    place(Leave, Pad, yy, buttonW, ButtonH)
    place(Resume, Pad + contentW - buttonW, yy, buttonW, ButtonH)
    yy += ButtonH + 14f
    hintMidY = yy + HintH / 2
    yy += HintH + Pad - 6f
    panelH = yy

    // To the middle of the window, the controls with it
    panelX = Math.round((screenW - panelW) / 2).toFloat
    panelY = Math.max(16f, Math.round((screenH - panelH) / 2).toFloat)
    var id = 0
    while (id < ControlCount) {
      if (cw(id) > 0f) { cx(id) += panelX; cy(id) += panelY }
      id += 1
    }
    titleMidY += panelY; ruleY += panelY; hintMidY += panelY
    row = 0
    while (row < RowCount) { rowLabelY(row) += panelY; row += 1 }
    trackX0 = cx(ZoomTrack) + TrackInset
    trackX1 = cx(ZoomTrack) + cw(ZoomTrack) - TrackInset
    trackMidY = cy(ZoomTrack) + ch(ZoomTrack) / 2
    laidOut = true
  }

  /** The control at (px, py), or NoControl: nothing before the menu is first laid out. */
  def controlAt(px: Float, py: Float): Int = {
    if (!laidOut) return NoControl
    var id = 0
    while (id < ControlCount) {
      if (cw(id) > 0f && px >= cx(id) && px < cx(id) + cw(id) && py >= cy(id) && py < cy(id) + ch(id)) return id
      id += 1
    }
    NoControl
  }

  /** Whether (px, py) is over the panel at all. */
  def overPanel(px: Float, py: Float): Boolean =
    laidOut && px >= panelX && px < panelX + panelW && py >= panelY && py < panelY + panelH

  // ── What is picked ─────────────────────────────────────────────────────

  /** The offered resolution that is the one drawn at: the chosen one, or the display's own when
    * the chosen one isn't offered here (a display no taller than it draws at its own). */
  def pickedResolution: Int = {
    var i = 0
    while (i < _resolutionCount) {
      if (_resolutions(i) == RenderQuality.resolution) return i
      i += 1
    }
    0
  }

  /** Where on its track the zoom is, 0 (the widest) to 1. */
  def zoomFraction: Float =
    (RenderQuality.zoomPercent - RenderQuality.MinZoom).toFloat / (RenderQuality.MaxZoom - RenderQuality.MinZoom)

  // ── The mouse ──────────────────────────────────────────────────────────

  def pointerMoved(px: Float, py: Float): Unit = {
    _hover = controlAt(px, py)
    if (dragging) zoomFromTrack(px)
  }

  def pointerPressed(px: Float, py: Float): Unit = {
    _focusVisible = false
    _pressed = controlAt(px, py)
    if (_pressed == ZoomTrack) {
      dragging = true
      zoomFromTrack(px)
    }
  }

  def pointerReleased(px: Float, py: Float): Unit = {
    val over = controlAt(px, py)
    val was = _pressed
    _pressed = NoControl
    dragging = false
    if (was != NoControl && was != ZoomTrack && over == was) activate(was)
  }

  private def zoomFromTrack(px: Float): Unit = {
    val t = Math.max(0f, Math.min(1f, (px - trackX0) / Math.max(1f, trackX1 - trackX0)))
    val percent = RenderQuality.zoomNearest(Math.round(RenderQuality.MinZoom + t * (RenderQuality.MaxZoom - RenderQuality.MinZoom)))
    if (percent != RenderQuality.zoomPercent) {
      disarm()
      RenderQuality.chooseZoom(percent)
    }
  }

  // ── The keyboard and a controller ──────────────────────────────────────

  /** An arrow key or the pad: `dy` moves between the rows, `dx` changes what is on one (the quality,
    * the resolution, the zoom) or moves between the two buttons. */
  def navigate(dx: Int, dy: Int): Unit = {
    _focusVisible = true
    if (dy != 0) _focusRow = Math.max(0, Math.min(RowCount - 1, _focusRow + Integer.signum(dy)))
    if (dx != 0) {
      val step = Integer.signum(dx)
      _focusRow match {
        case RowQuality =>
          val t = Math.max(RenderQuality.HIGH, Math.min(RenderQuality.LOW, RenderQuality.tier + step))
          activate(QualityBase + t)
        case RowResolution =>
          if (_resolutionCount > 0) activate(ResolutionBase + Math.max(0, Math.min(_resolutionCount - 1, pickedResolution + step)))
        case RowZoom => activate(if (step > 0) ZoomIn else ZoomOut)
        case _ => _focusAction = if (step > 0) Resume else Leave
      }
    }
  }

  /** Enter, Space or the pad's A: the button picked on the actions' row. */
  def activateFocused(): Unit = {
    _focusVisible = true
    if (_focusRow == RowActions) activate(_focusAction)
  }

  // ── What each control does ─────────────────────────────────────────────

  private def disarm(): Unit = leaveArmedUntil = 0L

  /** Press `id`: what a click on it, or Enter on it, does. */
  def activate(id: Int): Unit = {
    if (id != Leave) disarm()
    if (id >= QualityBase && id < QualityBase + 3) {
      if (RenderQuality.tier != id - QualityBase) RenderQuality.choose(id - QualityBase)
    } else if (id >= ResolutionBase && id < ResolutionBase + _resolutionCount) {
      val lines = _resolutions(id - ResolutionBase)
      if (RenderQuality.resolution != lines) RenderQuality.chooseResolution(lines)
    } else if (id == ZoomOut) {
      if (RenderQuality.zoomPercent > RenderQuality.MinZoom) RenderQuality.chooseZoom(RenderQuality.zoomPercent - RenderQuality.ZoomStep)
    } else if (id == ZoomIn) {
      if (RenderQuality.zoomPercent < RenderQuality.MaxZoom) RenderQuality.chooseZoom(RenderQuality.zoomPercent + RenderQuality.ZoomStep)
    } else if (id == Leave) {
      _focusRow = RowActions
      _focusAction = Leave
      if (leaveArmed) {
        close()
        onLeave()
      } else leaveArmedUntil = clock() + LeaveConfirmMs
    } else if (id == Resume) close()
  }
}

object MatchMenu {
  // The controls, by id
  final val NoControl = -1
  final val QualityBase = 0      // + the tier: High, Medium, Low
  final val ResolutionBase = 3   // + its place among those offered
  final val MaxResolutions = 4
  final val ZoomOut = 7
  final val ZoomTrack = 8
  final val ZoomIn = 9
  final val Leave = 10
  final val Resume = 11
  final val ControlCount = 12

  // Its rows, top to bottom
  final val RowQuality = 0
  final val RowResolution = 1
  final val RowZoom = 2
  final val RowActions = 3
  final val RowCount = 4

  /** How long the leave button waits for its second press. */
  final val LeaveConfirmMs = 3000L

  // Its measures, in the window's units
  final val PanelWidth = 560f
  final val Pad = 28f
  final val TitleH = 40f
  final val LabelH = 18f
  final val ControlH = 40f
  final val RowGap = 18f
  final val StepW = 44f
  final val TrackInset = 12f
  final val ButtonH = 46f
  final val HintH = 18f
}
