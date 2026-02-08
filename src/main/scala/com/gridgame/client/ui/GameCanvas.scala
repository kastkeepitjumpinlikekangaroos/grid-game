package com.gridgame.client.ui

import com.gridgame.client.GameClient
import com.gridgame.common.Constants
import com.gridgame.common.model.Direction
import com.gridgame.common.model.Item
import com.gridgame.common.model.ItemType
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
    drawItems(viewportX, viewportY)
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
    drawInventory()
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

  private def drawItems(viewportX: Int, viewportY: Int): Unit = {
    client.getItems.values().asScala.foreach { item =>
      val ix = item.getCellX
      val iy = item.getCellY

      if (ix >= viewportX && ix < viewportX + Constants.VIEWPORT_CELLS &&
          iy >= viewportY && iy < viewportY + Constants.VIEWPORT_CELLS) {

        val centerX = (ix - viewportX) * Constants.CELL_SIZE_PX + Constants.CELL_SIZE_PX / 2.0
        val centerY = (iy - viewportY) * Constants.CELL_SIZE_PX + Constants.CELL_SIZE_PX / 2.0
        val halfSize = Constants.ITEM_SIZE_PX / 2.0

        gc.setFill(intToColor(item.colorRGB))
        gc.setStroke(Color.BLACK)
        gc.setLineWidth(1)

        drawItemShape(item.itemType, centerX, centerY, halfSize)
      }
    }
  }

  private def drawItemShape(itemType: ItemType, centerX: Double, centerY: Double, halfSize: Double): Unit = {
    itemType match {
      case ItemType.Coin =>
        gc.fillOval(centerX - halfSize, centerY - halfSize, halfSize * 2, halfSize * 2)
        gc.strokeOval(centerX - halfSize, centerY - halfSize, halfSize * 2, halfSize * 2)

      case ItemType.Gem =>
        val xPoints = Array(centerX, centerX + halfSize, centerX, centerX - halfSize)
        val yPoints = Array(centerY - halfSize, centerY, centerY + halfSize, centerY)
        gc.fillPolygon(xPoints, yPoints, 4)
        gc.strokePolygon(xPoints, yPoints, 4)

      case ItemType.Heart =>
        val r = halfSize * 0.5
        gc.fillOval(centerX - halfSize * 0.5 - r, centerY - halfSize * 0.5 - r, r * 2, r * 2)
        gc.fillOval(centerX + halfSize * 0.5 - r, centerY - halfSize * 0.5 - r, r * 2, r * 2)
        val txPoints = Array(centerX - halfSize, centerX, centerX + halfSize)
        val tyPoints = Array(centerY - halfSize * 0.1, centerY + halfSize, centerY - halfSize * 0.1)
        gc.fillPolygon(txPoints, tyPoints, 3)

      case ItemType.Star =>
        val outerR = halfSize
        val innerR = halfSize * 0.4
        val xPoints = new Array[Double](10)
        val yPoints = new Array[Double](10)
        for (i <- 0 until 10) {
          val angle = Math.PI / 2 + i * Math.PI / 5
          val r = if (i % 2 == 0) outerR else innerR
          xPoints(i) = centerX + r * Math.cos(angle)
          yPoints(i) = centerY - r * Math.sin(angle)
        }
        gc.fillPolygon(xPoints, yPoints, 10)
        gc.strokePolygon(xPoints, yPoints, 10)

      case ItemType.Shield =>
        val xPoints = Array(centerX - halfSize, centerX + halfSize, centerX + halfSize, centerX, centerX - halfSize)
        val yPoints = Array(centerY - halfSize, centerY - halfSize, centerY + halfSize * 0.3, centerY + halfSize, centerY + halfSize * 0.3)
        gc.fillPolygon(xPoints, yPoints, 5)
        gc.strokePolygon(xPoints, yPoints, 5)

      case _ =>
        val xPoints = Array(centerX, centerX + halfSize, centerX, centerX - halfSize)
        val yPoints = Array(centerY - halfSize, centerY, centerY + halfSize, centerY)
        gc.fillPolygon(xPoints, yPoints, 4)
        gc.strokePolygon(xPoints, yPoints, 4)
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
    val inventoryText = s"Inventory: ${client.getInventory.size()}/${Constants.MAX_INVENTORY_SIZE}"

    drawOutlinedText(worldText, 10, 20)
    drawOutlinedText(coordText, 10, 40)
    drawOutlinedText(viewportText, 10, 60)
    drawOutlinedText(playersText, 10, 80)
    drawOutlinedText(healthText, 10, 100)
    drawOutlinedText(inventoryText, 10, 120)
  }

  private def drawInventory(): Unit = {
    val slotSize = 32.0
    val slotGap = 6.0
    val totalWidth = Constants.MAX_INVENTORY_SIZE * slotSize + (Constants.MAX_INVENTORY_SIZE - 1) * slotGap
    val startX = (getWidth - totalWidth) / 2.0
    val startY = getHeight - slotSize - 10.0

    val inv = client.getInventory

    for (i <- 0 until Constants.MAX_INVENTORY_SIZE) {
      val slotX = startX + i * (slotSize + slotGap)

      // Semi-transparent background
      gc.setFill(Color.rgb(0, 0, 0, 0.5))
      gc.fillRect(slotX, startY, slotSize, slotSize)

      // Border
      gc.setStroke(Color.rgb(200, 200, 200, 0.8))
      gc.setLineWidth(2)
      gc.strokeRect(slotX, startY, slotSize, slotSize)

      // Draw item if slot is filled
      if (i < inv.size()) {
        val item = inv.get(i)
        val centerX = slotX + slotSize / 2.0
        val centerY = startY + slotSize / 2.0
        val halfSize = 10.0

        gc.setFill(intToColor(item.colorRGB))
        gc.setStroke(Color.BLACK)
        gc.setLineWidth(1)
        drawItemShape(item.itemType, centerX, centerY, halfSize)
      }
    }
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
