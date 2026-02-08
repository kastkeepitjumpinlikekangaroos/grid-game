package com.gridgame.client.input

import com.gridgame.client.GameClient
import com.gridgame.common.Constants

import javafx.event.EventHandler
import javafx.scene.input.MouseEvent

class MouseHandler(client: GameClient) extends EventHandler[MouseEvent] {
  private var lastShootTime: Long = 0

  override def handle(event: MouseEvent): Unit = {
    if (event.getEventType == MouseEvent.MOUSE_PRESSED) {
      if (client.getIsDead) return

      val now = System.currentTimeMillis()
      if (now - lastShootTime < Constants.SHOOT_COOLDOWN_MS) {
        return
      }
      lastShootTime = now

      // Get the player's position in the world
      val playerPos = client.getLocalPosition
      val world = client.getWorld

      // Calculate the viewport offset (same logic as GameCanvas)
      val viewportX = calculateViewportOffset(playerPos.getX, world.width)
      val viewportY = calculateViewportOffset(playerPos.getY, world.height)

      // Convert screen coordinates to world coordinates
      val worldX = viewportX + (event.getX / Constants.CELL_SIZE_PX)
      val worldY = viewportY + (event.getY / Constants.CELL_SIZE_PX)

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

      client.shootToward(dx, dy)
    }
  }

  private def calculateViewportOffset(playerCoord: Int, worldSize: Int): Int = {
    val offset = playerCoord - (Constants.VIEWPORT_CELLS / 2)
    Math.max(0, Math.min(worldSize - Constants.VIEWPORT_CELLS, offset))
  }
}
