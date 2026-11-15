package com.gridgame.client.input

import com.gridgame.client.render.{GameCamera, IsometricTransform, MatchMenu}

import org.lwjgl.glfw.GLFW._
import com.gridgame.client.diagnostics.Diagnostics
import com.gridgame.client.game.GameClient
import com.gridgame.common.diagnostics.Recording.{InputDevice, Keys}

/**
 * GLFW mouse handler. Mirrors MouseHandler logic but uses GLFW callbacks.
 * Tracks cursor position and mouse button state for aiming and shooting: the left button casts the
 * Q or E being aimed as it goes down, and is otherwise the primary, held to aim (or to charge) and
 * fired as it comes up; the right button puts away whatever is aimed or held without it going off
 * (Attacks).
 *
 * While the match's menu is up (`menu`, which Esc brings up) the mouse is the menu's: its clicks
 * work it and nothing else, and the aim stays where it was. A click the menu took fires nothing let
 * go of after the menu has gone: the match never saw it go down, and the primary only goes off as
 * a button it saw go down comes up (Attacks.releasePrimary).
 */
class GLMouseHandler(client: GameClient, camera: GameCamera, menu: MatchMenu = new MatchMenu()) {
  private var _cursorX: Double = 0
  private var _cursorY: Double = 0
  // The buttons held, as a match recording's frames keep them (Recording.Keys)
  private var _heldMask = 0

  def heldMask: Int = _heldMask

  def cursorX: Double = _cursorX
  def cursorY: Double = _cursorY

  /** Called from GLFW cursor position callback. */
  def onCursorPos(x: Double, y: Double): Unit = {
    _cursorX = x
    _cursorY = y
    if (menu.isOpen) {
      menu.pointerMoved(x.toFloat, y.toFloat)
      return
    }
    camera.screenToWorldInto(x, y)
    client.setMouseWorldPosition(IsometricTransform.lastWorldX, IsometricTransform.lastWorldY)
  }

  /** Recompute mouse world position from current cursor and camera state.
    * Call each frame after camera.update() so the world position stays accurate
    * even when the mouse is stationary but the camera is moving. Not while the menu is up: the
    * pointer is on the menu, not aiming. */
  def refreshWorldPosition(): Unit = {
    if (menu.isOpen) return
    camera.screenToWorldInto(_cursorX, _cursorY)
    client.setMouseWorldPosition(IsometricTransform.lastWorldX, IsometricTransform.lastWorldY)
  }

  /** Called from GLFW mouse button callback. */
  def onMouseButton(button: Int, action: Int, mods: Int): Unit = {
    // Into the match's recording (a problem report's), with where it aimed in the frames around it
    val recorder = Diagnostics.recorder
    if (recorder != null) recorder.input(InputDevice.Mouse, action, button, mods)
    val bit = if (button == GLFW_MOUSE_BUTTON_LEFT) Keys.MouseLeft else if (button == GLFW_MOUSE_BUTTON_RIGHT) Keys.MouseRight else 0
    if (action == GLFW_PRESS) _heldMask |= bit else if (action == GLFW_RELEASE) _heldMask &= ~bit
    // The menu's while it is up: its clicks work it and nothing else
    if (menu.isOpen) {
      if (button == GLFW_MOUSE_BUTTON_LEFT) {
        if (action == GLFW_PRESS) menu.pointerPressed(_cursorX.toFloat, _cursorY.toFloat)
        else if (action == GLFW_RELEASE) menu.pointerReleased(_cursorX.toFloat, _cursorY.toFloat)
      }
      return
    }
    if (button == GLFW_MOUSE_BUTTON_RIGHT) {
      if (action == GLFW_PRESS) client.cancelAims()
      return
    }
    if (button != GLFW_MOUSE_BUTTON_LEFT) return

    // Aimed where the cursor is now, as the button goes down and as it comes up
    refreshWorldPosition()
    if (action == GLFW_PRESS) client.pressPrimary()
    else if (action == GLFW_RELEASE) client.releasePrimary()
  }
}
