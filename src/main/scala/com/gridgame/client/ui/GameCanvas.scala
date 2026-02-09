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
import scala.collection.mutable
import scala.jdk.CollectionConverters._

class GameCanvas(client: GameClient) extends Canvas(Constants.VIEWPORT_SIZE_PX, Constants.VIEWPORT_SIZE_PX) {
  private val gc: GraphicsContext = getGraphicsContext2D
  gc.setImageSmoothing(false)
  private var animationTick: Int = 0
  private val FRAMES_PER_STEP = 5
  private val lastRemotePositions: mutable.Map[UUID, Position] = mutable.Map.empty
  private val remoteMovingUntil: mutable.Map[UUID, Long] = mutable.Map.empty

  private val HW = Constants.ISO_HALF_W  // 20
  private val HH = Constants.ISO_HALF_H  // 10

  private def world: WorldData = client.getWorld

  // --- Isometric coordinate transforms ---

  /** Convert world tile coordinates to screen pixel coordinates (center of tile diamond). */
  private def worldToScreenX(wx: Double, wy: Double, camOffX: Double): Double =
    (wx - wy) * HW + camOffX

  private def worldToScreenY(wx: Double, wy: Double, camOffY: Double): Double =
    (wx + wy) * HH + camOffY

  /** Inverse: screen pixel coordinates to world tile coordinates. */
  private def screenToWorldX(sx: Double, sy: Double, camOffX: Double, camOffY: Double): Double = {
    val rx = sx - camOffX
    val ry = sy - camOffY
    (rx / HW + ry / HH) / 2.0
  }

  private def screenToWorldY(sx: Double, sy: Double, camOffX: Double, camOffY: Double): Double = {
    val rx = sx - camOffX
    val ry = sy - camOffY
    (ry / HH - rx / HW) / 2.0
  }

  def render(): Unit = {
    animationTick += 1

    // Check if player is dead
    if (client.getIsDead) {
      drawGameOverScreen()
      return
    }

    val localPos = client.getLocalPosition
    val currentWorld = world
    val canvasW = getWidth
    val canvasH = getHeight

    // Camera offset: center the local player's iso position on screen
    val playerSx = (localPos.getX.toDouble - localPos.getY.toDouble) * HW
    val playerSy = (localPos.getX.toDouble + localPos.getY.toDouble) * HH
    val camOffX = canvasW / 2.0 - playerSx
    val camOffY = canvasH / 2.0 - playerSy

    // Fill background
    gc.setFill(Color.rgb(30, 30, 40))
    gc.fillRect(0, 0, canvasW, canvasH)

    drawTerrain(camOffX, camOffY, currentWorld)
    drawItems(camOffX, camOffY)
    drawProjectiles(camOffX, camOffY)

    // Draw remote players (sorted by worldY for depth)
    val remotePlayers = client.getPlayers.values().asScala.filter(!_.isDead).toSeq.sortBy(_.getPosition.getY)
    remotePlayers.foreach { player =>
      drawPlayer(player, camOffX, camOffY)
    }

    drawLocalPlayer(localPos, camOffX, camOffY)

    drawCoordinates(currentWorld)
    drawInventory()
  }

  private def drawTerrain(camOffX: Double, camOffY: Double, world: WorldData): Unit = {
    val canvasW = getWidth
    val canvasH = getHeight
    val cellH = Constants.TILE_CELL_HEIGHT

    // Determine visible world tile range using inverse transform of viewport corners
    val corners = Seq(
      (screenToWorldX(0, 0, camOffX, camOffY), screenToWorldY(0, 0, camOffX, camOffY)),
      (screenToWorldX(canvasW, 0, camOffX, camOffY), screenToWorldY(canvasW, 0, camOffX, camOffY)),
      (screenToWorldX(0, canvasH, camOffX, camOffY), screenToWorldY(0, canvasH, camOffX, camOffY)),
      (screenToWorldX(canvasW, canvasH, camOffX, camOffY), screenToWorldY(canvasW, canvasH, camOffX, camOffY))
    )

    // Margin of 6 to account for tall tiles extending above their grid position
    val minWX = corners.map(_._1).min.floor.toInt - 6
    val maxWX = corners.map(_._1).max.ceil.toInt + 6
    val minWY = corners.map(_._2).min.floor.toInt - 6
    val maxWY = corners.map(_._2).max.ceil.toInt + 6

    // Clamp to world bounds
    val startX = Math.max(0, minWX)
    val endX = Math.min(world.width - 1, maxWX)
    val startY = Math.max(0, minWY)
    val endY = Math.min(world.height - 1, maxWY)

    for (wy <- startY to endY) {
      for (wx <- startX to endX) {
        val tile = world.getTile(wx, wy)
        val sx = worldToScreenX(wx, wy, camOffX)
        val sy = worldToScreenY(wx, wy, camOffY)

        val img = TileRenderer.getTileImage(tile.id)
        // Align: diamond center in image is at (HW, cellH - HH)
        // Screen diamond center is at (sx, sy)
        gc.drawImage(img, sx - HW, sy - (cellH - HH))
      }
    }
  }

  private def drawItems(camOffX: Double, camOffY: Double): Unit = {
    client.getItems.values().asScala.foreach { item =>
      val ix = item.getCellX
      val iy = item.getCellY

      val centerX = worldToScreenX(ix, iy, camOffX)
      val centerY = worldToScreenY(ix, iy, camOffY)
      val halfSize = Constants.ITEM_SIZE_PX / 2.0

      // Skip if off-screen
      if (centerX > -halfSize && centerX < getWidth + halfSize &&
          centerY > -halfSize && centerY < getHeight + halfSize) {

        gc.setFill(intToColor(item.colorRGB))
        gc.setStroke(Color.BLACK)
        gc.setLineWidth(1)

        drawItemShape(item.itemType, centerX, centerY, halfSize)
      }
    }
  }

  private def drawItemShape(itemType: ItemType, centerX: Double, centerY: Double, halfSize: Double): Unit = {
    itemType match {
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

      case ItemType.Fence =>
        val barW = halfSize * 0.35
        gc.fillRect(centerX - halfSize, centerY - halfSize, barW, halfSize * 2)
        gc.strokeRect(centerX - halfSize, centerY - halfSize, barW, halfSize * 2)
        gc.fillRect(centerX - barW / 2, centerY - halfSize, barW, halfSize * 2)
        gc.strokeRect(centerX - barW / 2, centerY - halfSize, barW, halfSize * 2)
        gc.fillRect(centerX + halfSize - barW, centerY - halfSize, barW, halfSize * 2)
        gc.strokeRect(centerX + halfSize - barW, centerY - halfSize, barW, halfSize * 2)

      case _ =>
        val xPoints = Array(centerX, centerX + halfSize, centerX, centerX - halfSize)
        val yPoints = Array(centerY - halfSize, centerY, centerY + halfSize, centerY)
        gc.fillPolygon(xPoints, yPoints, 4)
        gc.strokePolygon(xPoints, yPoints, 4)
    }
  }

  private def drawProjectiles(camOffX: Double, camOffY: Double): Unit = {
    client.getProjectiles.values().asScala.foreach { projectile =>
      val projX = projectile.getX.toDouble
      val projY = projectile.getY.toDouble

      val beamLength = 3.0

      val tailX = worldToScreenX(projX, projY, camOffX)
      val tailY = worldToScreenY(projX, projY, camOffY)

      val tipWX = projX + projectile.dx * beamLength
      val tipWY = projY + projectile.dy * beamLength
      val tipX = worldToScreenX(tipWX, tipWY, camOffX)
      val tipY = worldToScreenY(tipWX, tipWY, camOffY)

      val margin = 100.0
      if (Math.max(tailX, tipX) > -margin && Math.min(tailX, tipX) < getWidth + margin &&
          Math.max(tailY, tipY) > -margin && Math.min(tailY, tipY) < getHeight + margin) {

        val color = intToColor(projectile.colorRGB)
        gc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND)

        // 3-layer glow bloom
        gc.setStroke(Color.color(color.getRed, color.getGreen, color.getBlue, 0.15))
        gc.setLineWidth(10)
        gc.strokeLine(tailX, tailY, tipX, tipY)

        gc.setStroke(Color.color(color.getRed, color.getGreen, color.getBlue, 0.4))
        gc.setLineWidth(5)
        gc.strokeLine(tailX, tailY, tipX, tipY)

        // White-hot core
        val coreR = Math.min(1.0, color.getRed * 0.35 + 0.65)
        val coreG = Math.min(1.0, color.getGreen * 0.35 + 0.65)
        val coreB = Math.min(1.0, color.getBlue * 0.35 + 0.65)
        gc.setStroke(Color.color(coreR, coreG, coreB, 0.9))
        gc.setLineWidth(1.5)
        gc.strokeLine(tailX, tailY, tipX, tipY)

        // Bright orb at the tip
        val orbR = 4.5
        gc.setFill(Color.color(color.getRed, color.getGreen, color.getBlue, 0.3))
        gc.fillOval(tipX - orbR * 1.5, tipY - orbR * 1.5, orbR * 3, orbR * 3)
        gc.setFill(Color.color(coreR, coreG, coreB, 0.7))
        gc.fillOval(tipX - orbR * 0.6, tipY - orbR * 0.6, orbR * 1.2, orbR * 1.2)
      }
    }
  }

  private def drawShadow(screenX: Double, screenY: Double): Unit = {
    gc.setFill(Color.rgb(0, 0, 0, 0.3))
    gc.fillOval(screenX - 12, screenY - 5, 24, 10)
  }

  private def drawPlayer(player: Player, camOffX: Double, camOffY: Double): Unit = {
    val pos = player.getPosition
    val playerId = player.getId

    val screenX = worldToScreenX(pos.getX, pos.getY, camOffX)
    val screenY = worldToScreenY(pos.getX, pos.getY, camOffY)

    val spriteSz = Constants.SPRITE_SIZE_PX

    // Skip if off-screen
    if (screenX < -spriteSz || screenX > getWidth + spriteSz ||
        screenY < -spriteSz || screenY > getHeight + spriteSz) return

    // Detect remote player movement
    val now = System.currentTimeMillis()
    val lastPos = lastRemotePositions.get(playerId)
    if (lastPos.isEmpty || lastPos.get.getX != pos.getX || lastPos.get.getY != pos.getY) {
      remoteMovingUntil.put(playerId, now + 200)
    }
    lastRemotePositions.put(playerId, pos)

    val isMoving = now < remoteMovingUntil.getOrElse(playerId, 0L)
    val frame = if (isMoving) (animationTick / FRAMES_PER_STEP) % 4 else 0

    drawShadow(screenX, screenY)

    val sprite = SpriteGenerator.getSprite(player.getColorRGB, player.getDirection, frame)
    val spriteX = screenX - spriteSz / 2.0
    val spriteY = screenY - spriteSz.toDouble
    gc.drawImage(sprite, spriteX, spriteY)

    // Draw health bar above player
    drawHealthBar(screenX, spriteY, player.getHealth)
  }

  private def drawLocalPlayer(pos: Position, camOffX: Double, camOffY: Double): Unit = {
    val screenX = worldToScreenX(pos.getX, pos.getY, camOffX)
    val screenY = worldToScreenY(pos.getX, pos.getY, camOffY)

    val spriteSz = Constants.SPRITE_SIZE_PX
    val spriteX = screenX - spriteSz / 2.0
    val spriteY = screenY - spriteSz.toDouble

    // Draw effect auras behind the player
    drawEffectAuras(screenX, screenY - spriteSz / 2.0)

    drawShadow(screenX, screenY)

    val frame = if (client.getIsMoving) (animationTick / FRAMES_PER_STEP) % 4 else 0
    val sprite = SpriteGenerator.getSprite(client.getLocalColorRGB, client.getLocalDirection, frame)
    gc.drawImage(sprite, spriteX, spriteY)

    // Draw health bar above local player
    drawHealthBar(screenX, spriteY, client.getLocalHealth)
  }

  private def drawEffectAuras(centerX: Double, centerY: Double): Unit = {
    val radius = Constants.SPRITE_SIZE_PX * 0.5

    if (client.hasShield) {
      gc.setStroke(Color.rgb(156, 39, 176, 0.7)) // Purple
      gc.setLineWidth(2)
      gc.strokeOval(centerX - radius, centerY - radius, radius * 2, radius * 2)
      gc.setFill(Color.rgb(156, 39, 176, 0.15))
      gc.fillOval(centerX - radius, centerY - radius, radius * 2, radius * 2)
    }

    if (client.hasSpeedBoost) {
      gc.setStroke(Color.rgb(255, 235, 59, 0.7)) // Yellow
      gc.setLineWidth(2)
      val r = radius + 2
      gc.strokeOval(centerX - r, centerY - r, r * 2, r * 2)
    }

    if (client.hasGemBoost) {
      gc.setStroke(Color.rgb(0, 188, 212, 0.7)) // Cyan
      gc.setLineWidth(2)
      val r = radius + 4
      gc.strokeOval(centerX - r, centerY - r, r * 2, r * 2)
    }
  }

  private def drawHealthBar(screenCenterX: Double, spriteTopY: Double, health: Int): Unit = {
    val barWidth = Constants.HEALTH_BAR_WIDTH_PX
    val barHeight = Constants.HEALTH_BAR_HEIGHT_PX
    val barX = screenCenterX - barWidth / 2.0
    val barY = spriteTopY - Constants.HEALTH_BAR_OFFSET_Y - barHeight

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

  private def drawCoordinates(world: WorldData): Unit = {
    val localPos = client.getLocalPosition
    val playerCount = client.getPlayers.size()
    val health = client.getLocalHealth

    gc.setFill(Color.BLACK)
    gc.setStroke(Color.WHITE)
    gc.setLineWidth(1)

    val worldText = s"World: ${world.name}"
    val coordText = s"Position: (${localPos.getX}, ${localPos.getY})"
    val playersText = s"Players: ${playerCount + 1}"
    val healthText = s"Health: $health/${Constants.MAX_HEALTH}"
    val inventoryText = s"Inventory: ${client.getInventoryCount}/${Constants.MAX_INVENTORY_SIZE}"

    drawOutlinedText(worldText, 10, 20)
    drawOutlinedText(coordText, 10, 40)
    drawOutlinedText(playersText, 10, 60)
    drawOutlinedText(healthText, 10, 80)
    drawOutlinedText(inventoryText, 10, 100)

    // Show active effects
    val effects = Seq(
      if (client.hasSpeedBoost) Some("Speed") else None,
      if (client.hasShield) Some("Shield") else None,
      if (client.hasGemBoost) Some("FastShot") else None
    ).flatten
    if (effects.nonEmpty) {
      drawOutlinedText(s"Effects: ${effects.mkString(" ")}", 10, 120)
    }
  }

  private def drawInventory(): Unit = {
    val slotSize = 32.0
    val slotGap = 6.0
    val totalWidth = Constants.MAX_INVENTORY_SIZE * slotSize + (Constants.MAX_INVENTORY_SIZE - 1) * slotGap
    val startX = (getWidth - totalWidth) / 2.0
    val startY = getHeight - slotSize - 10.0

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
      val item = client.getInventoryItem(i)
      if (item != null) {
        val centerX = slotX + slotSize / 2.0
        val centerY = startY + slotSize / 2.0
        val halfSize = 10.0

        gc.setFill(intToColor(item.colorRGB))
        gc.setStroke(Color.BLACK)
        gc.setLineWidth(1)
        drawItemShape(item.itemType, centerX, centerY, halfSize)
      }

      // Draw slot number label in bottom-right corner
      val label = (i + 1).toString
      gc.setFont(javafx.scene.text.Font.font("Arial", javafx.scene.text.FontWeight.BOLD, 10))
      gc.setFill(Color.WHITE)
      gc.fillText(label, slotX + slotSize - 10, startY + slotSize - 4)
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

  /** Get the camera offsets for the current local player position. Used by MouseHandler. */
  def getCameraOffsets: (Double, Double) = {
    val localPos = client.getLocalPosition
    val playerSx = (localPos.getX.toDouble - localPos.getY.toDouble) * HW
    val playerSy = (localPos.getX.toDouble + localPos.getY.toDouble) * HH
    val camOffX = getWidth / 2.0 - playerSx
    val camOffY = getHeight / 2.0 - playerSy
    (camOffX, camOffY)
  }

  /** Convert screen coordinates to world coordinates. Used by MouseHandler. */
  def screenToWorld(sx: Double, sy: Double): (Double, Double) = {
    val (camOffX, camOffY) = getCameraOffsets
    (screenToWorldX(sx, sy, camOffX, camOffY), screenToWorldY(sx, sy, camOffX, camOffY))
  }
}
