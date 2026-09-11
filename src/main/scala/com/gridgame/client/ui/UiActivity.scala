package com.gridgame.client.ui

import javafx.event.EventHandler
import javafx.scene.input.{InputEvent, KeyEvent, MouseEvent, ScrollEvent}
import javafx.stage.Window

/**
 * Whether anyone is using the menus, so their decorative animations can stop when not.
 *
 * Every frame a JavaFX screen changes costs a repaint and present of the whole window: on
 * the character select screen that is ~40% of a CPU core on a 5K display with its
 * animations at 30 fps, and on macOS presenting also builds up the window's pool of
 * surfaces (up to ~650MB there), which is only given back when the window is hidden. A
 * player waiting in a lobby or who has switched to another app gets nothing for it, so
 * animations pause when the window is in the background or has had no input for
 * [[IdleAfterMs]], and resume on the next mouse move or key press.
 */
object UiActivity {
  val IdleAfterMs = 30000L

  @volatile private var lastInputMs = System.currentTimeMillis()
  @volatile private var window: Window = _

  private val onInput: EventHandler[InputEvent] = _ => lastInputMs = System.currentTimeMillis()

  /** Track input on a window. Idempotent per window. */
  def install(w: Window): Unit = {
    if (window eq w) return
    window = w
    w.addEventFilter(MouseEvent.MOUSE_MOVED, onInput)
    w.addEventFilter(MouseEvent.MOUSE_PRESSED, onInput)
    w.addEventFilter(MouseEvent.MOUSE_DRAGGED, onInput)
    w.addEventFilter(ScrollEvent.SCROLL, onInput)
    w.addEventFilter(KeyEvent.KEY_PRESSED, onInput)
    w.focusedProperty().addListener((_, _, focused) => if (focused) lastInputMs = System.currentTimeMillis())
  }

  /** A screen just appeared: give it a full idle period before anything pauses. */
  def touch(): Unit = lastInputMs = System.currentTimeMillis()

  /** As if the idle period had already run out (UiMemoryBench). */
  private[ui] def markIdle(): Unit = lastInputMs = 0L

  /** True when menu animations should hold still. */
  def idle: Boolean = {
    val w = window
    (w != null && !w.isFocused) || System.currentTimeMillis() - lastInputMs > IdleAfterMs
  }
}
