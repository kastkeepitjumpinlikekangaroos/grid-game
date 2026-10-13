package com.gridgame.client.ui

import com.gridgame.client.Fx
import javafx.collections.FXCollections
import javafx.event.Event
import javafx.scene.control.{ComboBox, PopupControl}
import javafx.scene.input.{MouseButton, MouseEvent}
import javafx.scene.layout.StackPane
import javafx.stage.Stage
import org.junit.Assert._
import org.junit.Test

/** A click on a closed drop-down opens it, whatever closed it last (DropDowns). */
class DropDownsTest {
  private def box(): ComboBox[String] = Fx {
    val c = new ComboBox[String](FXCollections.observableArrayList("Free-For-All", "Teams"))
    c.getSelectionModel.select(0)
    val stage = new Stage()
    stage.setScene(Theme.newScene(new StackPane(c)))
    c.applyCss()
    c.getParent.layout()
    c
  }

  private def mouse(c: ComboBox[String], tpe: javafx.event.EventType[MouseEvent]): Unit =
    Event.fireEvent(c, new MouseEvent(tpe, 10, 10, 10, 10, MouseButton.PRIMARY, 1,
      false, false, false, false, tpe == MouseEvent.MOUSE_PRESSED, false, false, true, false, false, null))

  /** A click as the player makes one; then whatever it left to run on the FX thread has run. */
  private def click(c: ComboBox[String]): Unit = {
    Fx { mouse(c, MouseEvent.MOUSE_PRESSED); mouse(c, MouseEvent.MOUSE_RELEASED); mouse(c, MouseEvent.MOUSE_CLICKED) }
    Fx(())
  }

  /** The list closing by itself (another app in front, a click outside the window, Escape). */
  private def autoHide(c: ComboBox[String]): Unit = Fx {
    val getPopup = classOf[javafx.scene.control.skin.ComboBoxPopupControl[_]].getDeclaredMethod("getPopup")
    getPopup.setAccessible(true)
    val popup = getPopup.invoke(c.getSkin).asInstanceOf[PopupControl]
    popup.getOnAutoHide.handle(new Event(popup, popup, Event.ANY))
  }

  @Test def aClickOpensTheListAfterItClosedByItselfUnderTheMouse(): Unit = {
    val c = box()
    Fx(mouse(c, MouseEvent.MOUSE_ENTERED))
    click(c)
    assertTrue("the first click opens it", Fx(c.isShowing))
    Thread.sleep(300) // the list was open a while: closing now isn't the press's doing
    autoHide(c)
    assertFalse(Fx(c.isShowing))
    Thread.sleep(300)
    click(c)
    assertTrue("the next click opens it again", Fx(c.isShowing))
  }

  @Test def aClickOnTheOpenBoxStillClosesIt(): Unit = {
    val c = box()
    Fx(mouse(c, MouseEvent.MOUSE_ENTERED))
    click(c)
    assertTrue(Fx(c.isShowing))
    Thread.sleep(300)
    // On macOS the press on the box ends the popup's grab, which closes it just before the press lands
    Fx { autoHide(c); mouse(c, MouseEvent.MOUSE_PRESSED); mouse(c, MouseEvent.MOUSE_RELEASED); mouse(c, MouseEvent.MOUSE_CLICKED) }
    Fx(())
    assertFalse("closing it isn't undone", Fx(c.isShowing))
    click(c)
    assertTrue("and the click after opens it", Fx(c.isShowing))
  }
}
