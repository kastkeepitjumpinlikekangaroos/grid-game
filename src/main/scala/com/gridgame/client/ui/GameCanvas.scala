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

class GameCanvas(client: GameClient) extends Canvas() {
  private val gc: GraphicsContext = getGraphicsContext2D
  gc.setImageSmoothing(false)
  private var animationTick: Int = 0
  private val FRAMES_PER_STEP = 5
  private val TILE_ANIM_SPEED = 15
  private val lastRemotePositions: mutable.Map[UUID, Position] = mutable.Map.empty
  private val remoteMovingUntil: mutable.Map[UUID, Long] = mutable.Map.empty

  // Visual interpolation for smooth movement
  private var visualX: Double = Double.NaN
  private var visualY: Double = Double.NaN
  private val remoteVisualPositions: mutable.Map[UUID, (Double, Double)] = mutable.Map.empty

  private val HW = Constants.ISO_HALF_W  // 20
  private val HH = Constants.ISO_HALF_H  // 10
  private val HIT_ANIMATION_MS = 500
  private val DEATH_ANIMATION_MS = 1200
  private val TELEPORT_ANIMATION_MS = 800

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

    val localDeathTime = client.getLocalDeathTime
    val localDeathAnimActive = client.getIsDead && localDeathTime > 0 &&
      (System.currentTimeMillis() - localDeathTime) < DEATH_ANIMATION_MS

    // Show game over screen only after death animation completes
    if (client.getIsDead && !localDeathAnimActive) {
      drawGameOverScreen()
      return
    }

    val localPos = client.getLocalPosition
    val currentWorld = world
    val canvasW = getWidth
    val canvasH = getHeight

    // Lerp visual position toward actual grid position for smooth movement
    val targetX = localPos.getX.toDouble
    val targetY = localPos.getY.toDouble
    if (visualX.isNaN) {
      visualX = targetX
      visualY = targetY
    } else {
      val lerpFactor = 0.3
      visualX += (targetX - visualX) * lerpFactor
      visualY += (targetY - visualY) * lerpFactor
      if (Math.abs(visualX - targetX) < 0.01) visualX = targetX
      if (Math.abs(visualY - targetY) < 0.01) visualY = targetY
    }

    // Camera offset: center the interpolated player position on screen
    val playerSx = (visualX - visualY) * HW
    val playerSy = (visualX + visualY) * HH
    val camOffX = canvasW / 2.0 - playerSx
    val camOffY = canvasH / 2.0 - playerSy

    // Draw themed animated background
    BackgroundRenderer.render(gc, canvasW, canvasH, currentWorld.background, animationTick, camOffX, camOffY)

    // Calculate visible tile bounds
    val corners = Seq(
      (screenToWorldX(0, 0, camOffX, camOffY), screenToWorldY(0, 0, camOffX, camOffY)),
      (screenToWorldX(canvasW, 0, camOffX, camOffY), screenToWorldY(canvasW, 0, camOffX, camOffY)),
      (screenToWorldX(0, canvasH, camOffX, camOffY), screenToWorldY(0, canvasH, camOffX, camOffY)),
      (screenToWorldX(canvasW, canvasH, camOffX, camOffY), screenToWorldY(canvasW, canvasH, camOffX, camOffY))
    )
    val startX = Math.max(0, corners.map(_._1).min.floor.toInt - 6)
    val endX = Math.min(currentWorld.width - 1, corners.map(_._1).max.ceil.toInt + 6)
    val startY = Math.max(0, corners.map(_._2).min.floor.toInt - 6)
    val endY = Math.min(currentWorld.height - 1, corners.map(_._2).max.ceil.toInt + 6)

    val tileFrame = (animationTick / TILE_ANIM_SPEED) % TileRenderer.getNumFrames
    val cellH = Constants.TILE_CELL_HEIGHT

    // Collect entities by cell for depth-interleaved rendering
    val entitiesByCell = mutable.Map.empty[(Int, Int), mutable.ArrayBuffer[() => Unit]]

    def addEntity(cx: Int, cy: Int, drawFn: () => Unit): Unit = {
      entitiesByCell.getOrElseUpdate((cx, cy), mutable.ArrayBuffer.empty) += drawFn
    }

    // Items
    client.getItems.values().asScala.foreach { item =>
      addEntity(item.getCellX, item.getCellY, () => drawSingleItem(item, camOffX, camOffY))
    }

    // Projectiles
    client.getProjectiles.values().asScala.foreach { proj =>
      val cx = Math.round(proj.getX).toInt
      val cy = Math.round(proj.getY).toInt
      addEntity(cx, cy, () => drawSingleProjectile(proj, camOffX, camOffY))
    }

    // Remote players (with visual interpolation)
    client.getPlayers.values().asScala.filter(!_.isDead).foreach { player =>
      val pos = player.getPosition
      val pid = player.getId
      val (rvx, rvy) = remoteVisualPositions.get(pid) match {
        case Some((prevX, prevY)) =>
          val lerpFactor = 0.3
          val nx = prevX + (pos.getX - prevX) * lerpFactor
          val ny = prevY + (pos.getY - prevY) * lerpFactor
          val sx = if (Math.abs(nx - pos.getX) < 0.01) pos.getX.toDouble else nx
          val sy = if (Math.abs(ny - pos.getY) < 0.01) pos.getY.toDouble else ny
          (sx, sy)
        case None =>
          (pos.getX.toDouble, pos.getY.toDouble)
      }
      remoteVisualPositions.put(pid, (rvx, rvy))
      addEntity(pos.getX, pos.getY, () => drawPlayerInterp(player, rvx, rvy, camOffX, camOffY))
    }

    // Clean up visual positions for disconnected players
    remoteVisualPositions.keys.filterNot(id => client.getPlayers.containsKey(id)).toSeq.foreach(remoteVisualPositions.remove)

    // Local player (use visual position for smooth rendering, grid cell for depth sorting)
    val localVX = visualX
    val localVY = visualY
    if (localDeathAnimActive) {
      addEntity(localPos.getX, localPos.getY, () => drawDeathEffect(
        worldToScreenX(localVX, localVY, camOffX),
        worldToScreenY(localVX, localVY, camOffY),
        client.getLocalColorRGB,
        localDeathTime
      ))
    } else {
      addEntity(localPos.getX, localPos.getY, () => drawLocalPlayer(localVX, localVY, camOffX, camOffY))
    }

    // Phase 1: Draw ground tiles (flat/walkable) — these never occlude entities
    for (wy <- startY to endY) {
      for (wx <- startX to endX) {
        val tile = currentWorld.getTile(wx, wy)
        if (tile.walkable) {
          val sx = worldToScreenX(wx, wy, camOffX)
          val sy = worldToScreenY(wx, wy, camOffY)
          val img = TileRenderer.getTileImage(tile.id, tileFrame)
          gc.drawImage(img, sx - HW, sy - (cellH - HH))
        }
      }
    }

    // Phase 2: Draw elevated tiles + entities interleaved by depth
    // Entities (players, items, projectiles) sit on top of ground but behind walls at higher depth.
    for (wy <- startY to endY) {
      for (wx <- startX to endX) {
        val tile = currentWorld.getTile(wx, wy)
        if (!tile.walkable) {
          val sx = worldToScreenX(wx, wy, camOffX)
          val sy = worldToScreenY(wx, wy, camOffY)
          val img = TileRenderer.getTileImage(tile.id, tileFrame)
          gc.drawImage(img, sx - HW, sy - (cellH - HH))
        }

        // Draw entities at this cell (on top of ground, behind later elevated tiles)
        entitiesByCell.remove((wx, wy)).foreach(_.foreach(_()))
      }
    }

    // Draw any entities outside visible tile range
    entitiesByCell.values.foreach(_.foreach(_()))

    // Death/teleport animations drawn as overlays
    drawDeathAnimations(camOffX, camOffY)
    drawTeleportAnimations(camOffX, camOffY)

    drawCoordinates(currentWorld)
    drawInventory()
    drawChargeBar()
  }

  private def drawSingleItem(item: Item, camOffX: Double, camOffY: Double): Unit = {
    val ix = item.getCellX
    val iy = item.getCellY

    val centerX = worldToScreenX(ix, iy, camOffX)
    val centerY = worldToScreenY(ix, iy, camOffY)
    val halfSize = Constants.ITEM_SIZE_PX / 2.0

    if (centerX > -halfSize && centerX < getWidth + halfSize &&
        centerY > -halfSize && centerY < getHeight + halfSize) {

      gc.setFill(intToColor(item.colorRGB))
      gc.setStroke(Color.BLACK)
      gc.setLineWidth(1)

      drawItemShape(item.itemType, centerX, centerY, halfSize)
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

  private def drawSingleProjectile(projectile: Projectile, camOffX: Double, camOffY: Double): Unit = {
    val projX = projectile.getX.toDouble
    val projY = projectile.getY.toDouble

    val beamLength = 5.0

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

      // Animated pulse based on tick + projectile id for variation
      val phase = (animationTick + projectile.id * 37) * 0.3
      val pulse = 0.85 + 0.15 * Math.sin(phase)
      val fastFlicker = 0.9 + 0.1 * Math.sin(phase * 4.7)

      // Layer 1: Ultra-wide soft outer glow
      gc.setStroke(Color.color(color.getRed, color.getGreen, color.getBlue, 0.07 * pulse))
      gc.setLineWidth(44 * pulse)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      // Layer 2: Wide outer glow
      gc.setStroke(Color.color(color.getRed, color.getGreen, color.getBlue, 0.12 * pulse))
      gc.setLineWidth(30 * pulse)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      // Layer 3: Mid glow
      gc.setStroke(Color.color(color.getRed, color.getGreen, color.getBlue, 0.25 * pulse))
      gc.setLineWidth(18 * pulse)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      // Layer 4: Bright beam
      gc.setStroke(Color.color(color.getRed, color.getGreen, color.getBlue, 0.6 * fastFlicker))
      gc.setLineWidth(10 * fastFlicker)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      // Layer 5: White-hot core with flicker
      val coreR = Math.min(1.0, color.getRed * 0.3 + 0.7)
      val coreG = Math.min(1.0, color.getGreen * 0.3 + 0.7)
      val coreB = Math.min(1.0, color.getBlue * 0.3 + 0.7)
      gc.setStroke(Color.color(coreR, coreG, coreB, 0.95 * fastFlicker))
      gc.setLineWidth(4.5)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      // Animated energy particles along the beam
      val dx = tipX - tailX
      val dy = tipY - tailY
      val len = Math.sqrt(dx * dx + dy * dy)
      if (len > 1) {
        val nx = dx / len
        val ny = dy / len
        val particleCount = 6
        for (i <- 0 until particleCount) {
          val t = ((animationTick * 0.08 + i.toDouble / particleCount + projectile.id * 0.13) % 1.0)
          val px = tailX + dx * t + ny * Math.sin(phase + i * 2.1) * 6.0
          val py = tailY + dy * t - nx * Math.sin(phase + i * 2.1) * 6.0
          val pAlpha = Math.max(0.0, Math.min(1.0, 0.7 * (1.0 - Math.abs(t - 0.5) * 2.0)))
          val pSize = 3.0 + Math.sin(phase + i) * 1.2
          gc.setFill(Color.color(coreR, coreG, coreB, pAlpha))
          gc.fillOval(px - pSize, py - pSize, pSize * 2, pSize * 2)
        }
      }

      // Bright orb at the tip with pulse
      val orbR = 9.0 * pulse
      gc.setFill(Color.color(color.getRed, color.getGreen, color.getBlue, 0.2 * pulse))
      gc.fillOval(tipX - orbR * 2, tipY - orbR * 2, orbR * 4, orbR * 4)
      gc.setFill(Color.color(color.getRed, color.getGreen, color.getBlue, 0.4 * pulse))
      gc.fillOval(tipX - orbR, tipY - orbR, orbR * 2, orbR * 2)
      gc.setFill(Color.color(coreR, coreG, coreB, 0.85 * fastFlicker))
      gc.fillOval(tipX - orbR * 0.5, tipY - orbR * 0.5, orbR, orbR)
    }
  }

  private def drawShadow(screenX: Double, screenY: Double): Unit = {
    gc.setFill(Color.rgb(0, 0, 0, 0.3))
    gc.fillOval(screenX - 16, screenY - 6, 32, 12)
  }

  private def drawPlayerInterp(player: Player, wx: Double, wy: Double, camOffX: Double, camOffY: Double): Unit = {
    val pos = player.getPosition
    val playerId = player.getId

    val screenX = worldToScreenX(wx, wy, camOffX)
    val screenY = worldToScreenY(wx, wy, camOffY)

    val displaySz = Constants.PLAYER_DISPLAY_SIZE_PX

    // Skip if off-screen
    if (screenX < -displaySz || screenX > getWidth + displaySz ||
        screenY < -displaySz || screenY > getHeight + displaySz) return

    // Detect remote player movement
    val now = System.currentTimeMillis()
    val lastPos = lastRemotePositions.get(playerId)
    if (lastPos.isEmpty || lastPos.get.getX != pos.getX || lastPos.get.getY != pos.getY) {
      remoteMovingUntil.put(playerId, now + 200)
    }
    lastRemotePositions.put(playerId, pos)

    val isMoving = now < remoteMovingUntil.getOrElse(playerId, 0L)
    val remoteAnimSpeed = if (player.getChargeLevel > 0) {
      val chargePct = player.getChargeLevel / 100.0
      (FRAMES_PER_STEP * (1.0 + chargePct * 4.0)).toInt
    } else FRAMES_PER_STEP
    val frame = if (isMoving) (animationTick / remoteAnimSpeed) % 4 else 0

    val spriteCenter = screenY - displaySz / 2.0

    // Draw effects behind the player
    if (player.hasShield) drawShieldBubble(screenX, spriteCenter)
    if (player.hasGemBoost) drawGemGlow(screenX, spriteCenter)
    drawChargingEffect(screenX, spriteCenter, player.getChargeLevel)

    drawShadow(screenX, screenY)

    val sprite = SpriteGenerator.getSprite(player.getColorRGB, player.getDirection, frame)
    val spriteX = screenX - displaySz / 2.0
    val spriteY = screenY - displaySz.toDouble
    gc.drawImage(sprite, spriteX, spriteY, displaySz, displaySz)

    // Draw hit effect on top of sprite
    drawHitEffect(screenX, spriteCenter, client.getPlayerHitTime(playerId))

    // Draw health bar above player
    drawHealthBar(screenX, spriteY, player.getHealth)
  }

  private def drawLocalPlayer(wx: Double, wy: Double, camOffX: Double, camOffY: Double): Unit = {
    val screenX = worldToScreenX(wx, wy, camOffX)
    val screenY = worldToScreenY(wx, wy, camOffY)

    val displaySz = Constants.PLAYER_DISPLAY_SIZE_PX
    val spriteX = screenX - displaySz / 2.0
    val spriteY = screenY - displaySz.toDouble
    val spriteCenter = screenY - displaySz / 2.0

    // Draw effects behind the player
    if (client.hasShield) drawShieldBubble(screenX, spriteCenter)
    if (client.hasGemBoost) drawGemGlow(screenX, spriteCenter)
    drawChargingEffect(screenX, spriteCenter, client.getChargeLevel)

    drawShadow(screenX, screenY)

    val animSpeed = if (client.isCharging) {
      val chargePct = client.getChargeLevel / 100.0
      (FRAMES_PER_STEP * (1.0 + chargePct * 4.0)).toInt
    } else FRAMES_PER_STEP
    val frame = if (client.getIsMoving) (animationTick / animSpeed) % 4 else 0
    val sprite = SpriteGenerator.getSprite(client.getLocalColorRGB, client.getLocalDirection, frame)
    gc.drawImage(sprite, spriteX, spriteY, displaySz, displaySz)

    // Draw hit effect on top of sprite
    drawHitEffect(screenX, spriteCenter, client.getPlayerHitTime(client.getLocalPlayerId))

    // Draw health bar above local player
    drawHealthBar(screenX, spriteY, client.getLocalHealth)
  }

  private def drawShieldBubble(centerX: Double, centerY: Double): Unit = {
    val phase = animationTick * 0.08
    val baseRadius = Constants.PLAYER_DISPLAY_SIZE_PX * 0.55
    val pulse = 1.0 + 0.06 * Math.sin(phase * 1.5)
    val radius = baseRadius * pulse

    // Layer 1: Outer soft glow
    gc.setFill(Color.rgb(156, 39, 176, 0.08 + 0.04 * Math.sin(phase)))
    gc.fillOval(centerX - radius * 1.3, centerY - radius * 1.3 * 0.5, radius * 2.6, radius * 1.3)

    // Layer 2: Main bubble fill
    gc.setFill(Color.rgb(156, 39, 176, 0.12))
    gc.fillOval(centerX - radius, centerY - radius * 0.5, radius * 2, radius)

    // Layer 3: Wobbling outline
    val wobble1 = 0.02 * Math.sin(phase * 2.3)
    val wobble2 = 0.02 * Math.cos(phase * 1.7)
    gc.setStroke(Color.rgb(180, 80, 220, 0.6))
    gc.setLineWidth(1.5)
    gc.strokeOval(
      centerX - radius * (1.0 + wobble1),
      centerY - radius * 0.5 * (1.0 + wobble2),
      radius * 2 * (1.0 + wobble1),
      radius * (1.0 + wobble2)
    )

    // Layer 4: Rotating sheen highlight
    val sheenAngle = phase * 0.7
    val sheenX = centerX + radius * 0.6 * Math.cos(sheenAngle)
    val sheenY = centerY + radius * 0.3 * Math.sin(sheenAngle) * 0.5
    val sheenSize = radius * 0.3
    gc.setFill(Color.rgb(220, 180, 255, 0.3))
    gc.fillOval(sheenX - sheenSize, sheenY - sheenSize * 0.5, sheenSize * 2, sheenSize)

    // Layer 5: Sparkles on the bubble surface
    for (i <- 0 until 4) {
      val angle = phase * 0.5 + i * Math.PI / 2
      val sx = centerX + radius * 0.85 * Math.cos(angle)
      val sy = centerY + radius * 0.42 * Math.sin(angle)
      val sparkleAlpha = 0.4 + 0.3 * Math.sin(phase * 3 + i * 1.5)
      gc.setFill(Color.rgb(220, 180, 255, sparkleAlpha))
      gc.fillOval(sx - 2, sy - 2, 4, 4)
    }
  }

  private def drawGemGlow(centerX: Double, centerY: Double): Unit = {
    val phase = animationTick * 0.1
    val baseRadius = Constants.PLAYER_DISPLAY_SIZE_PX * 0.6
    val pulse = 0.85 + 0.15 * Math.sin(phase * 1.8)
    val radius = baseRadius * pulse

    // Layer 1: Wide soft glow
    gc.setFill(Color.rgb(0, 188, 212, 0.06 * pulse))
    gc.fillOval(centerX - radius * 1.5, centerY - radius * 0.75, radius * 3, radius * 1.5)

    // Layer 2: Inner bright glow
    gc.setFill(Color.rgb(0, 230, 255, 0.15 * pulse))
    gc.fillOval(centerX - radius * 0.8, centerY - radius * 0.4, radius * 1.6, radius * 0.8)

    // Layer 3: Cyan outline ring
    gc.setStroke(Color.rgb(0, 220, 240, 0.5 * pulse))
    gc.setLineWidth(2.0)
    gc.strokeOval(centerX - radius, centerY - radius * 0.5, radius * 2, radius)

    // Layer 4: Orbiting sparkle particles
    val particleCount = 5
    val orbitRadius = radius * 0.9
    for (i <- 0 until particleCount) {
      val angle = phase * 0.6 + i * (2 * Math.PI / particleCount)
      val px = centerX + orbitRadius * Math.cos(angle)
      val py = centerY + orbitRadius * Math.sin(angle) * 0.5
      val pSize = 2.0 + 1.0 * Math.sin(phase * 2 + i * 1.2)
      val pAlpha = 0.5 + 0.3 * Math.sin(phase * 3 + i)
      gc.setFill(Color.rgb(150, 255, 255, pAlpha))
      gc.fillOval(px - pSize, py - pSize, pSize * 2, pSize * 2)
    }
  }

  private def drawChargingEffect(centerX: Double, centerY: Double, chargeLevel: Int): Unit = {
    if (chargeLevel <= 0) return

    val pct = chargeLevel / 100.0
    val phase = animationTick * 0.15

    // Color shifts from yellow (0%) -> orange (50%) -> red (100%)
    val r = Math.min(1.0, pct * 2.0)
    val g = Math.min(1.0, Math.max(0.0, 1.0 - (pct - 0.5) * 2.0))
    val b = 0.0

    // Layer 1: Outer pulsing glow - grows with charge
    val outerRadius = 12.0 + pct * 20.0
    val pulse = 0.8 + 0.2 * Math.sin(phase * 2.0)
    gc.setFill(Color.color(r, g, b, 0.12 * pulse * pct))
    gc.fillOval(centerX - outerRadius, centerY - outerRadius, outerRadius * 2, outerRadius * 2)

    // Layer 2: Inner bright ring
    val innerRadius = 8.0 + pct * 12.0
    gc.setStroke(Color.color(r, g, b, 0.5 * pct))
    gc.setLineWidth(2.0 + pct * 2.0)
    gc.strokeOval(centerX - innerRadius, centerY - innerRadius, innerRadius * 2, innerRadius * 2)

    // Layer 3: Rotating energy particles (3-6 based on charge)
    val particleCount = 3 + (pct * 3).toInt
    val orbitRadius = 10.0 + pct * 14.0
    for (i <- 0 until particleCount) {
      val angle = phase + i * (2 * Math.PI / particleCount)
      val px = centerX + orbitRadius * Math.cos(angle)
      val py = centerY + orbitRadius * Math.sin(angle) * 0.5 // squash Y for iso perspective
      val pSize = 2.0 + pct * 2.5
      gc.setFill(Color.color(
        Math.min(1.0, r * 0.3 + 0.7),
        Math.min(1.0, g * 0.3 + 0.7),
        0.7,
        0.6 * pct
      ))
      gc.fillOval(px - pSize, py - pSize, pSize * 2, pSize * 2)
    }
  }

  private def drawHitEffect(centerX: Double, centerY: Double, hitTime: Long): Unit = {
    if (hitTime <= 0) return
    val elapsed = System.currentTimeMillis() - hitTime
    if (elapsed < 0 || elapsed > HIT_ANIMATION_MS) return

    val progress = elapsed.toDouble / HIT_ANIMATION_MS // 0.0 -> 1.0
    val fadeOut = 1.0 - progress

    // Effect 1: Red flash (fades quickly in first half)
    val flashAlpha = Math.max(0.0, 0.4 * (1.0 - progress * 2.0))
    if (flashAlpha > 0) {
      gc.setFill(Color.color(1.0, 0.0, 0.0, flashAlpha))
      val flashR = 16.0
      gc.fillOval(centerX - flashR, centerY - flashR, flashR * 2, flashR * 2)
    }

    // Effect 2: Expanding red shockwave ring
    val ringRadius = 8.0 + progress * 28.0
    gc.setStroke(Color.color(1.0, 0.2, 0.1, 0.7 * fadeOut))
    gc.setLineWidth(3.0 * fadeOut)
    gc.strokeOval(centerX - ringRadius, centerY - ringRadius * 0.5, ringRadius * 2, ringRadius)

    // Effect 3: Red particles flying outward
    val particleCount = 6
    for (i <- 0 until particleCount) {
      val angle = i * (2 * Math.PI / particleCount) + progress * 0.5
      val dist = progress * 22.0
      val px = centerX + dist * Math.cos(angle)
      val py = centerY + dist * Math.sin(angle) * 0.5 // iso squash
      val pSize = 2.5 * fadeOut
      gc.setFill(Color.color(1.0, 0.3, 0.1, 0.6 * fadeOut))
      gc.fillOval(px - pSize, py - pSize, pSize * 2, pSize * 2)
    }
  }

  private def drawDeathAnimations(camOffX: Double, camOffY: Double): Unit = {
    val now = System.currentTimeMillis()
    val iter = client.getDeathAnimations.entrySet().iterator()
    while (iter.hasNext) {
      val entry = iter.next()
      val data = entry.getValue
      val deathTime = data(0)
      if (now - deathTime > DEATH_ANIMATION_MS) {
        iter.remove()
      } else {
        val wx = data(1).toDouble
        val wy = data(2).toDouble
        val colorRGB = data(3).toInt
        val sx = worldToScreenX(wx, wy, camOffX)
        val sy = worldToScreenY(wx, wy, camOffY)
        drawDeathEffect(sx, sy, colorRGB, deathTime)
      }
    }
  }

  private def drawDeathEffect(screenX: Double, screenY: Double, colorRGB: Int, deathTime: Long): Unit = {
    if (deathTime <= 0) return
    val elapsed = System.currentTimeMillis() - deathTime
    if (elapsed < 0 || elapsed > DEATH_ANIMATION_MS) return

    val progress = elapsed.toDouble / DEATH_ANIMATION_MS // 0.0 -> 1.0
    val fadeOut = 1.0 - progress
    val color = intToColor(colorRGB)
    val displaySz = Constants.PLAYER_DISPLAY_SIZE_PX
    val centerY = screenY - displaySz / 2.0

    // Phase 1 (0-30%): Bright flash at death location
    if (progress < 0.3) {
      val flashPct = progress / 0.3
      val flashAlpha = 0.6 * (1.0 - flashPct)
      val flashR = 20.0 + flashPct * 15.0
      gc.setFill(Color.color(1.0, 1.0, 1.0, flashAlpha))
      gc.fillOval(screenX - flashR, centerY - flashR * 0.5, flashR * 2, flashR)
    }

    // Expanding shockwave rings
    val ring1R = progress * 50.0
    gc.setStroke(Color.color(color.getRed, color.getGreen, color.getBlue, 0.5 * fadeOut))
    gc.setLineWidth(2.5 * fadeOut)
    gc.strokeOval(screenX - ring1R, centerY - ring1R * 0.5, ring1R * 2, ring1R)

    if (progress > 0.15) {
      val ring2R = (progress - 0.15) / 0.85 * 40.0
      gc.setStroke(Color.color(color.getRed, color.getGreen, color.getBlue, 0.3 * fadeOut))
      gc.setLineWidth(1.5 * fadeOut)
      gc.strokeOval(screenX - ring2R, centerY - ring2R * 0.5, ring2R * 2, ring2R)
    }

    // Particles scattering outward and rising
    val particleCount = 10
    for (i <- 0 until particleCount) {
      val angle = i * (2 * Math.PI / particleCount) + i * 0.3
      val dist = progress * (25.0 + (i % 3) * 10.0)
      val rise = progress * progress * 20.0 // accelerating upward drift
      val px = screenX + dist * Math.cos(angle)
      val py = centerY + dist * Math.sin(angle) * 0.5 - rise
      val pSize = (3.0 + (i % 3)) * fadeOut

      // Alternate between player color and bright white
      if (i % 2 == 0) {
        val cr = Math.min(1.0, color.getRed * 0.4 + 0.6)
        val cg = Math.min(1.0, color.getGreen * 0.4 + 0.6)
        val cb = Math.min(1.0, color.getBlue * 0.4 + 0.6)
        gc.setFill(Color.color(cr, cg, cb, 0.7 * fadeOut))
      } else {
        gc.setFill(Color.color(color.getRed, color.getGreen, color.getBlue, 0.6 * fadeOut))
      }
      gc.fillOval(px - pSize, py - pSize, pSize * 2, pSize * 2)
    }

    // Fading ghost sprite rising upward
    val ghostAlpha = Math.max(0.0, fadeOut * 0.6)
    val ghostRise = progress * 30.0
    gc.setGlobalAlpha(ghostAlpha)
    val sprite = SpriteGenerator.getSprite(colorRGB, Direction.Down, 0)
    gc.drawImage(sprite, screenX - displaySz / 2.0, screenY - displaySz.toDouble - ghostRise, displaySz, displaySz)
    gc.setGlobalAlpha(1.0)
  }

  private def drawTeleportAnimations(camOffX: Double, camOffY: Double): Unit = {
    val now = System.currentTimeMillis()
    val iter = client.getTeleportAnimations.entrySet().iterator()
    while (iter.hasNext) {
      val entry = iter.next()
      val data = entry.getValue
      val timestamp = data(0)
      if (now - timestamp > TELEPORT_ANIMATION_MS) {
        iter.remove()
      } else {
        val oldX = data(1).toDouble
        val oldY = data(2).toDouble
        val newX = data(3).toDouble
        val newY = data(4).toDouble
        val colorRGB = data(5).toInt
        drawTeleportDeparture(
          worldToScreenX(oldX, oldY, camOffX),
          worldToScreenY(oldX, oldY, camOffY),
          colorRGB, timestamp
        )
        drawTeleportArrival(
          worldToScreenX(newX, newY, camOffX),
          worldToScreenY(newX, newY, camOffY),
          colorRGB, timestamp
        )
      }
    }
  }

  private def drawTeleportDeparture(screenX: Double, screenY: Double, colorRGB: Int, startTime: Long): Unit = {
    val elapsed = System.currentTimeMillis() - startTime
    if (elapsed < 0 || elapsed > TELEPORT_ANIMATION_MS) return

    val progress = elapsed.toDouble / TELEPORT_ANIMATION_MS
    val fadeOut = Math.max(0.0, 1.0 - progress * 1.5) // Fades fully by ~67%

    // Particles collapse inward
    val particleCount = 8
    for (i <- 0 until particleCount) {
      val angle = i * (2 * Math.PI / particleCount) + progress * 2.0
      val dist = 25.0 * (1.0 - progress)
      val px = screenX + dist * Math.cos(angle)
      val py = screenY + dist * Math.sin(angle) * 0.5
      val pSize = 3.0 * fadeOut
      gc.setFill(Color.color(1.0, 0.92, 0.3, 0.7 * fadeOut))
      gc.fillOval(px - pSize, py - pSize, pSize * 2, pSize * 2)
    }

    // Contracting ring
    val ringRadius = 20.0 * (1.0 - progress)
    if (ringRadius > 0.5) {
      gc.setStroke(Color.color(1.0, 0.9, 0.3, 0.5 * fadeOut))
      gc.setLineWidth(2.0 * fadeOut)
      gc.strokeOval(screenX - ringRadius, screenY - ringRadius * 0.5, ringRadius * 2, ringRadius)
    }

    // White flash at start
    if (progress < 0.2) {
      val flashAlpha = 0.5 * (1.0 - progress / 0.2)
      val flashR = 15.0 * (1.0 - progress / 0.2)
      gc.setFill(Color.color(1.0, 1.0, 0.8, flashAlpha))
      gc.fillOval(screenX - flashR, screenY - flashR * 0.5, flashR * 2, flashR)
    }
  }

  private def drawTeleportArrival(screenX: Double, screenY: Double, colorRGB: Int, startTime: Long): Unit = {
    val elapsed = System.currentTimeMillis() - startTime
    val delay = 200
    if (elapsed < delay || elapsed > TELEPORT_ANIMATION_MS) return

    val adjustedElapsed = elapsed - delay
    val duration = TELEPORT_ANIMATION_MS - delay
    val progress = adjustedElapsed.toDouble / duration
    val fadeOut = 1.0 - progress

    // Bright flash at arrival
    if (progress < 0.3) {
      val flashPct = progress / 0.3
      val flashAlpha = 0.6 * (1.0 - flashPct)
      val flashR = 10.0 + flashPct * 20.0
      gc.setFill(Color.color(1.0, 1.0, 0.8, flashAlpha))
      gc.fillOval(screenX - flashR, screenY - flashR * 0.5, flashR * 2, flashR)
    }

    // Expanding ring
    val ringRadius = progress * 35.0
    gc.setStroke(Color.color(1.0, 0.9, 0.3, 0.6 * fadeOut))
    gc.setLineWidth(2.5 * fadeOut)
    gc.strokeOval(screenX - ringRadius, screenY - ringRadius * 0.5, ringRadius * 2, ringRadius)

    // Particles expanding outward
    val particleCount = 8
    for (i <- 0 until particleCount) {
      val angle = i * (2 * Math.PI / particleCount) + progress * 1.5
      val dist = progress * 30.0
      val px = screenX + dist * Math.cos(angle)
      val py = screenY + dist * Math.sin(angle) * 0.5
      val pSize = 2.5 * fadeOut
      gc.setFill(Color.color(1.0, 0.92, 0.3, 0.6 * fadeOut))
      gc.fillOval(px - pSize, py - pSize, pSize * 2, pSize * 2)
    }

    // Rising star sparkles
    for (i <- 0 until 4) {
      val sparkX = screenX + (i - 1.5) * 8.0
      val rise = progress * 20.0
      val sparkAlpha = Math.max(0.0, 0.5 * fadeOut)
      gc.setFill(Color.color(1.0, 1.0, 0.6, sparkAlpha))
      gc.fillOval(sparkX - 1.5, screenY - rise - 1.5, 3, 3)
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

  private def drawChargeBar(): Unit = {
    if (!client.isCharging) return

    val chargeLevel = client.getChargeLevel
    val barWidth = 100.0
    val barHeight = 8.0
    val barX = (getWidth - barWidth) / 2.0
    val barY = getHeight - 60.0 // Above inventory

    // Dark background
    gc.setFill(Color.rgb(30, 30, 30, 0.8))
    gc.fillRect(barX - 2, barY - 2, barWidth + 4, barHeight + 4)

    // Empty bar background
    gc.setFill(Color.rgb(60, 60, 60))
    gc.fillRect(barX, barY, barWidth, barHeight)

    // Charge fill — yellow to orange to red
    val pct = chargeLevel / 100.0
    val fillWidth = barWidth * pct
    val r = Math.min(1.0, pct * 2.0)
    val g = Math.min(1.0, Math.max(0.0, 1.0 - (pct - 0.5) * 2.0))
    gc.setFill(Color.color(r, g, 0.0))
    gc.fillRect(barX, barY, fillWidth, barHeight)

    // Border
    gc.setStroke(Color.rgb(200, 200, 200, 0.8))
    gc.setLineWidth(1)
    gc.strokeRect(barX, barY, barWidth, barHeight)

    // Charge percentage text
    gc.setFont(javafx.scene.text.Font.font("Arial", javafx.scene.text.FontWeight.BOLD, 10))
    gc.setFill(Color.WHITE)
    val text = s"$chargeLevel%"
    gc.fillText(text, barX + barWidth / 2 - 10, barY - 4)
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

  /** Reset visual interpolation (e.g. on respawn or world change). */
  def resetVisualPosition(): Unit = {
    visualX = Double.NaN
    visualY = Double.NaN
    remoteVisualPositions.clear()
  }

  /** Get the camera offsets for the current local player position. Used by MouseHandler. */
  def getCameraOffsets: (Double, Double) = {
    val vx = if (visualX.isNaN) client.getLocalPosition.getX.toDouble else visualX
    val vy = if (visualY.isNaN) client.getLocalPosition.getY.toDouble else visualY
    val playerSx = (vx - vy) * HW
    val playerSy = (vx + vy) * HH
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
