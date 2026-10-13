package com.gridgame.client.ui

import javafx.application.Platform
import javafx.event.Event
import javafx.scene.{Node, Scene}
import javafx.scene.control.ComboBoxBase
import javafx.scene.input.{MouseButton, MouseEvent}

/**
 * Every click on a closed drop-down opens it.
 *
 * JavaFX 21's combo box (`ComboBoxBaseBehavior`) opens or closes on the mouse's release by a
 * flag that a press on the box closing the list flips, so that click doesn't open it again. But
 * the flag flips whenever the list closes by itself with the mouse over the box (another app
 * comes to the front, a click outside the window, Escape), and no press follows to flip it
 * back: the next click on the box closes the closed list, and only the one after opens it. That
 * was "sometimes the drop-down doesn't appear when I click it".
 *
 * So the scene watches the presses itself: a click that started on a closed box (closed for
 * longer than the press can have closed it) opens it, if JavaFX didn't. Installed on every
 * menu scene by `Theme.newScene`.
 */
object DropDowns {
  /** A list closed this recently was closed by the press itself (the grab ending as it lands). */
  private val ClosedByPressNanos = 200L * 1000 * 1000

  private val HiddenAt = "gridgame.dropdown.hiddenAt"
  private val PressOpens = "gridgame.dropdown.pressOpens"

  def install(scene: Scene): Unit = {
    scene.addEventFilter(ComboBoxBase.ON_HIDDEN, (e: Event) => e.getTarget match {
      case box: ComboBoxBase[_] => box.getProperties.put(HiddenAt, java.lang.Long.valueOf(System.nanoTime()))
      case _ =>
    })
    scene.addEventFilter(MouseEvent.MOUSE_PRESSED, (e: MouseEvent) => boxOf(e).foreach { box =>
      val plain = e.getButton == MouseButton.PRIMARY &&
        !(e.isShiftDown || e.isControlDown || e.isAltDown || e.isMetaDown)
      box.getProperties.put(PressOpens, java.lang.Boolean.valueOf(plain && !box.isShowing && !closedJustNow(box)))
    })
    scene.addEventFilter(MouseEvent.MOUSE_RELEASED, (e: MouseEvent) => boxOf(e).foreach { box =>
      if (box.getProperties.remove(PressOpens) == java.lang.Boolean.TRUE) {
        // After JavaFX's own handling of the release, which opens it when its flag is right
        Platform.runLater(() => if (!box.isShowing && !box.isDisabled && box.getScene != null) {
          box.requestFocus()
          box.show()
        })
      }
    })
  }

  private def closedJustNow(box: ComboBoxBase[_]): Boolean = box.getProperties.get(HiddenAt) match {
    case t: java.lang.Long => System.nanoTime() - t < ClosedByPressNanos
    case _ => false
  }

  /** The (not editable) drop-down a mouse event landed on, if any. */
  private def boxOf(e: MouseEvent): Option[ComboBoxBase[_]] = {
    var n: Node = e.getTarget match { case n: Node => n; case _ => null }
    while (n != null && !n.isInstanceOf[ComboBoxBase[_]]) n = n.getParent
    n match {
      case box: ComboBoxBase[_] if !box.isEditable => Some(box)
      case _ => None
    }
  }
}
