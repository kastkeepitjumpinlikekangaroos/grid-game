package com.gridgame.client.input

import com.gridgame.client.GameClient
import com.gridgame.client.ui.GameCanvas
import com.gridgame.common.Constants

import javafx.event.EventHandler
import javafx.scene.input.MouseEvent

class MouseHandler(client: GameClient, canvas: GameCanvas) extends EventHandler[MouseEvent] {
  private var lastShootTime: Long = 0

  override def handle(event: MouseEvent): Unit = {
    // Track mouse position for aiming (works during move and drag)
    if (event.getEventType == MouseEvent.MOUSE_MOVED ||
        event.getEventType == MouseEvent.MOUSE_PRESSED ||
        event.getEventType == MouseEvent.MOUSE_DRAGGED) {
      val (worldX, worldY) = canvas.screenToWorld(event.getX, event.getY)
      client.setMouseWorldPosition(worldX, worldY)
    }

    if (event.getEventType == MouseEvent.MOUSE_PRESSED) {
      if (client.getIsDead) return

      val now = System.currentTimeMillis()
      if (now - lastShootTime < Constants.SHOOT_COOLDOWN_MS) {
        return
      }

      // Start charging
      client.startCharging()
    }

    if (event.getEventType == MouseEvent.MOUSE_RELEASED) {
      if (!client.isCharging) return
      if (client.getIsDead) {
        client.cancelCharging()
        return
      }

      val chargeLevel = client.getChargeLevel
      client.cancelCharging()

      lastShootTime = System.currentTimeMillis()

      // Get the player's position in the world
      val playerPos = client.getLocalPosition

      // Convert screen coordinates to world coordinates using isometric inverse
      val (worldX, worldY) = canvas.screenToWorld(event.getX, event.getY)

      // Calculate direction from player to clicked position
      val deltaX = worldX - playerPos.getX
      val deltaY = worldY - playerPos.getY

      // Calculate magnitude and normalize
      val magnitude = Math.sqrt(deltaX * deltaX + deltaY * deltaY)
      if (magnitude < 0.001) {
        // Clicked on self, don't shoot
        return
      }

      val dx = (deltaX / magnitude).toFloat
      val dy = (deltaY / magnitude).toFloat

      client.shootToward(dx, dy, chargeLevel)
    }
  }
}
