package com.gridgame.client.ui

import com.gridgame.client.GameClient
import com.gridgame.common.Constants
import com.gridgame.common.model.Direction
import com.gridgame.common.model.Player
import com.gridgame.common.model.Position
import com.gridgame.common.model.Projectile
import com.gridgame.common.model.WorldData

import javafx.scene.canvas.Canvas
import javafx.scene.canvas.GraphicsContext
import javafx.scene.image.Image
import javafx.scene.paint.Color

import java.util.UUID
import scala.jdk.CollectionConverters._

class GameCanvas(client: GameClient) extends Canvas(Constants.VIEWPORT_SIZE_PX, Constants.VIEWPORT_SIZE_PX) {
  private val gc: GraphicsContext = getGraphicsContext2D

  private def world: WorldData = client.getWorld

  def render(): Unit = {
    gc.clearRect(0, 0, getWidth, getHeight)

    // Check if player is dead
    if (client.getIsDead) {
      drawGameOverScreen()
      return
    }

    val localPos = client.getLocalPosition
    val currentWorld = world

    val viewportX = calculateViewportOffsetX(localPos.getX, currentWorld)
    val viewportY = calculateViewportOffsetY(localPos.getY, currentWorld)

    drawTerrain(viewportX, viewportY, currentWorld)
    drawGrid(viewportX, viewportY)
    drawProjectiles(viewportX, viewportY)

    client.getPlayers.values().asScala.foreach { player =>
      if (!player.isDead) {
        val pos = player.getPosition
        if (isInViewport(pos, viewportX, viewportY)) {
          drawPlayer(player, viewportX, viewportY)
        }
      }
    }

    drawLocalPlayer(localPos, viewportX, viewportY)

    drawCoordinates(viewportX, viewportY, currentWorld)
  }

  private def calculateViewportOffsetX(playerCoord: Int, world: WorldData): Int = {
    val offset = playerCoord - (Constants.VIEWPORT_CELLS / 2)
    Math.max(0, Math.min(world.width - Constants.VIEWPORT_CELLS, offset))
  }

  private def calculateViewportOffsetY(playerCoord: Int, world: WorldData): Int = {
    val offset = playerCoord - (Constants.VIEWPORT_CELLS / 2)
    Math.max(0, Math.min(world.height - Constants.VIEWPORT_CELLS, offset))
  }

  private def isInViewport(pos: Position, viewportX: Int, viewportY: Int): Boolean = {
    pos.getX >= viewportX &&
    pos.getX < viewportX + Constants.VIEWPORT_CELLS &&
    pos.getY >= viewportY &&
    pos.getY < viewportY + Constants.VIEWPORT_CELLS
  }

  private def drawTerrain(viewportX: Int, viewportY: Int, world: WorldData): Unit = {
    for (dy <- 0 until Constants.VIEWPORT_CELLS) {
      for (dx <- 0 until Constants.VIEWPORT_CELLS) {
        val worldX = viewportX + dx
        val worldY = viewportY + dy
        val tile = world.getTile(worldX, worldY)
        val screenX = dx * Constants.CELL_SIZE_PX
        val screenY = dy * Constants.CELL_SIZE_PX

        gc.setFill(intToColor(tile.color))
        gc.fillRect(screenX, screenY, Constants.CELL_SIZE_PX, Constants.CELL_SIZE_PX)
      }
    }
  }

  private def drawGrid(viewportX: Int, viewportY: Int): Unit = {
    gc.setStroke(Color.rgb(0, 0, 0, 0.15))
    gc.setLineWidth(0.5)

    for (i <- 0 to Constants.VIEWPORT_CELLS) {
      val x = i * Constants.CELL_SIZE_PX
      gc.strokeLine(x, 0, x, Constants.VIEWPORT_SIZE_PX)
    }

    for (i <- 0 to Constants.VIEWPORT_CELLS) {
      val y = i * Constants.CELL_SIZE_PX
      gc.strokeLine(0, y, Constants.VIEWPORT_SIZE_PX, y)
    }
  }

  private def drawProjectiles(viewportX: Int, viewportY: Int): Unit = {
    client.getProjectiles.values().asScala.foreach { projectile =>
      val projX = projectile.getX
      val projY = projectile.getY

      // Check if projectile is in viewport (using float bounds)
      if (projX >= viewportX - 1 && projX < viewportX + Constants.VIEWPORT_CELLS + 1 &&
          projY >= viewportY - 1 && projY < viewportY + Constants.VIEWPORT_CELLS + 1) {

        // Use float coordinates for sub-cell rendering
        val screenX = (projX - viewportX) * Constants.CELL_SIZE_PX + Constants.CELL_SIZE_PX / 2
        val screenY = (projY - viewportY) * Constants.CELL_SIZE_PX + Constants.CELL_SIZE_PX / 2
        val radius = Constants.PROJECTILE_SIZE_PX / 2.0

        // Draw projectile as a filled circle with the owner's color
        gc.setFill(intToColor(projectile.colorRGB))
        gc.fillOval(screenX - radius, screenY - radius, Constants.PROJECTILE_SIZE_PX, Constants.PROJECTILE_SIZE_PX)

        // Add a dark outline for visibility
        gc.setStroke(Color.BLACK)
        gc.setLineWidth(1)
        gc.strokeOval(screenX - radius, screenY - radius, Constants.PROJECTILE_SIZE_PX, Constants.PROJECTILE_SIZE_PX)
      }
    }
  }

  private def drawPlayer(player: Player, viewportX: Int, viewportY: Int): Unit = {
    val pos = player.getPosition

    val screenX = (pos.getX - viewportX) * Constants.CELL_SIZE_PX
    val screenY = (pos.getY - viewportY) * Constants.CELL_SIZE_PX

    val sprite = SpriteGenerator.getSprite(player.getColorRGB, player.getDirection)
    gc.drawImage(sprite, screenX, screenY)

    // Draw health bar above player
    drawHealthBar(screenX, screenY, player.getHealth)
  }

  private def drawLocalPlayer(pos: Position, viewportX: Int, viewportY: Int): Unit = {
    val screenX = (pos.getX - viewportX) * Constants.CELL_SIZE_PX
    val screenY = (pos.getY - viewportY) * Constants.CELL_SIZE_PX

    val sprite = SpriteGenerator.getSprite(client.getLocalColorRGB, client.getLocalDirection)
    gc.drawImage(sprite, screenX, screenY)

    // Draw health bar above local player
    drawHealthBar(screenX, screenY, client.getLocalHealth)
  }

  private def drawHealthBar(screenX: Double, screenY: Double, health: Int): Unit = {
    val barWidth = Constants.HEALTH_BAR_WIDTH_PX
    val barHeight = Constants.HEALTH_BAR_HEIGHT_PX
    val barX = screenX + (Constants.CELL_SIZE_PX - barWidth) / 2.0
    val barY = screenY - Constants.HEALTH_BAR_OFFSET_Y - barHeight

    // Dark red background (empty health)
    gc.setFill(Color.DARKRED)
    gc.fillRect(barX, barY, barWidth, barHeight)

    // Green fill (current health)
    val healthPercentage = health.toDouble / Constants.MAX_HEALTH
    val fillWidth = barWidth * healthPercentage
    gc.setFill(Color.LIMEGREEN)
    gc.fillRect(barX, barY, fillWidth, barHeight)

    // Black border
    gc.setStroke(Color.BLACK)
    gc.setLineWidth(1)
    gc.strokeRect(barX, barY, barWidth, barHeight)
  }

  private def drawGameOverScreen(): Unit = {
    // Dark overlay
    gc.setFill(Color.rgb(0, 0, 0, 0.7))
    gc.fillRect(0, 0, getWidth, getHeight)

    // Game Over text
    gc.setFill(Color.RED)
    gc.setFont(javafx.scene.text.Font.font("Arial", javafx.scene.text.FontWeight.BOLD, 48))
    val text = "GAME OVER"
    val textWidth = 220 // Approximate width
    gc.fillText(text, (getWidth - textWidth) / 2, getHeight / 2)

    // Press Enter to reload text
    gc.setFill(Color.WHITE)
    gc.setFont(javafx.scene.text.Font.font("Arial", javafx.scene.text.FontWeight.NORMAL, 20))
    val reloadText = "Press Enter to reload"
    val reloadTextWidth = 160 // Approximate width
    gc.fillText(reloadText, (getWidth - reloadTextWidth) / 2, getHeight / 2 + 40)
  }

  private def drawCoordinates(viewportX: Int, viewportY: Int, world: WorldData): Unit = {
    val localPos = client.getLocalPosition
    val playerCount = client.getPlayers.size()
    val health = client.getLocalHealth

    gc.setFill(Color.BLACK)
    gc.setStroke(Color.WHITE)
    gc.setLineWidth(1)

    val worldText = s"World: ${world.name}"
    val coordText = s"Position: (${localPos.getX}, ${localPos.getY})"
    val viewportText = s"Viewport: [$viewportX-${viewportX + Constants.VIEWPORT_CELLS - 1}, $viewportY-${viewportY + Constants.VIEWPORT_CELLS - 1}]"
    val playersText = s"Players: ${playerCount + 1}"
    val healthText = s"Health: $health/${Constants.MAX_HEALTH}"

    drawOutlinedText(worldText, 10, 20)
    drawOutlinedText(coordText, 10, 40)
    drawOutlinedText(viewportText, 10, 60)
    drawOutlinedText(playersText, 10, 80)
    drawOutlinedText(healthText, 10, 100)
  }

  private def drawOutlinedText(text: String, x: Double, y: Double): Unit = {
    gc.setStroke(Color.WHITE)
    gc.setLineWidth(3)
    gc.strokeText(text, x, y)

    gc.setFill(Color.BLACK)
    gc.fillText(text, x, y)
  }

  private def intToColor(argb: Int): Color = {
    val alpha = (argb >> 24) & 0xFF
    val red = (argb >> 16) & 0xFF
    val green = (argb >> 8) & 0xFF
    val blue = argb & 0xFF
    Color.rgb(red, green, blue, alpha / 255.0)
  }
}
