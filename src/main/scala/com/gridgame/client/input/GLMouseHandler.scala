package com.gridgame.client.input

import com.gridgame.client.render.{GameCamera, IsometricTransform}

import org.lwjgl.glfw.GLFW._
import com.gridgame.client.game.GameClient

/**
 * GLFW mouse handler. Mirrors MouseHandler logic but uses GLFW callbacks.
 * Tracks cursor position and mouse button state for aiming and shooting: the left button casts the
 * Q or E being aimed as it goes down, and is otherwise the primary, held to aim (or to charge) and
 * fired as it comes up; the right button puts away whatever is aimed or held without it going off
 * (Attacks).
 */
class GLMouseHandler(client: GameClient, camera: GameCamera) {
  private var _cursorX: Double = 0
  private var _cursorY: Double = 0

  def cursorX: Double = _cursorX
  def cursorY: Double = _cursorY

  /** Called from GLFW cursor position callback. */
  def onCursorPos(x: Double, y: Double): Unit = {
    _cursorX = x
    _cursorY = y
    camera.screenToWorldInto(x, y)
    client.setMouseWorldPosition(IsometricTransform.lastWorldX, IsometricTransform.lastWorldY)
  }

  /** Recompute mouse world position from current cursor and camera state.
    * Call each frame after camera.update() so the world position stays accurate
    * even when the mouse is stationary but the camera is moving. */
  def refreshWorldPosition(): Unit = {
    camera.screenToWorldInto(_cursorX, _cursorY)
    client.setMouseWorldPosition(IsometricTransform.lastWorldX, IsometricTransform.lastWorldY)
  }

  /** Called from GLFW mouse button callback. */
  def onMouseButton(button: Int, action: Int, mods: Int): Unit = {
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
