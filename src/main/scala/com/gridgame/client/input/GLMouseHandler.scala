package com.gridgame.client.input

import com.gridgame.client.GameClient
import com.gridgame.client.render.GameCamera
import com.gridgame.common.Constants
import com.gridgame.common.model.ProjectileDef

import org.lwjgl.glfw.GLFW._

/**
 * GLFW mouse handler. Mirrors MouseHandler logic but uses GLFW callbacks.
 * Tracks cursor position and mouse button state for aiming and shooting.
 */
class GLMouseHandler(client: GameClient, camera: GameCamera) {
  private var lastShootTime: Long = 0
  private var _cursorX: Double = 0
  private var _cursorY: Double = 0

  def cursorX: Double = _cursorX
  def cursorY: Double = _cursorY

  private def canCharge: Boolean = {
    val pt = client.getSelectedCharacterDef.primaryProjectileType
    ProjectileDef.get(pt).chargeSpeedScaling.isDefined
  }

  /** Called from GLFW cursor position callback. */
  def onCursorPos(x: Double, y: Double): Unit = {
    _cursorX = x
    _cursorY = y
    val (worldX, worldY) = camera.screenToWorld(x, y)
    client.setMouseWorldPosition(worldX, worldY)
  }

  /** Called from GLFW mouse button callback. */
  def onMouseButton(button: Int, action: Int, mods: Int): Unit = {
    if (button != GLFW_MOUSE_BUTTON_LEFT) return

    if (action == GLFW_PRESS) {
      // Update aim position
      val (worldX, worldY) = camera.screenToWorld(_cursorX, _cursorY)
      client.setMouseWorldPosition(worldX, worldY)

      if (client.getIsDead) return
      val now = System.currentTimeMillis()
      if (now - lastShootTime < Constants.SHOOT_COOLDOWN_MS) return

      if (canCharge) {
        client.startCharging()
      } else {
        lastShootTime = now
        val playerPos = client.getLocalPosition
        val deltaX = worldX - playerPos.getX
        val deltaY = worldY - playerPos.getY
        val magnitude = Math.sqrt(deltaX * deltaX + deltaY * deltaY)
        if (magnitude < 0.001) return
        val dx = (deltaX / magnitude).toFloat
        val dy = (deltaY / magnitude).toFloat
        client.shootToward(dx, dy, 0)
      }
    } else if (action == GLFW_RELEASE) {
      if (!client.isCharging) return
      if (client.getIsDead) {
        client.cancelCharging()
        return
      }

      val chargeLevel = client.getChargeLevel
      client.cancelCharging()
      lastShootTime = System.currentTimeMillis()

      val playerPos = client.getLocalPosition
      val (worldX, worldY) = camera.screenToWorld(_cursorX, _cursorY)
      val deltaX = worldX - playerPos.getX
      val deltaY = worldY - playerPos.getY
      val magnitude = Math.sqrt(deltaX * deltaX + deltaY * deltaY)
      if (magnitude < 0.001) return
      val dx = (deltaX / magnitude).toFloat
      val dy = (deltaY / magnitude).toFloat
      client.shootToward(dx, dy, chargeLevel)
    }
  }
}
