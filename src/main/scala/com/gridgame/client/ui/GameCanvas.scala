package com.gridgame.client.ui

import com.gridgame.client.ClientState
import com.gridgame.client.GameClient
import com.gridgame.common.Constants
import com.gridgame.common.model.CharacterDef
import com.gridgame.common.model.Direction
import com.gridgame.common.model.Item
import com.gridgame.common.model.ItemType
import com.gridgame.common.model.Player
import com.gridgame.common.model.Position
import com.gridgame.common.model.Projectile
import com.gridgame.common.model.ProjectileType
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

    // In non-lobby mode, show full game over screen when dead (after death anim)
    // In lobby mode (respawning), keep rendering the world with a countdown overlay
    if (client.getIsDead && !localDeathAnimActive && !client.isRespawning) {
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
        localDeathTime,
        client.selectedCharacterId
      ))
    } else if (!client.getIsDead) {
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

    // Aim arrow: drawn on the ground plane, beneath elevated tiles and entities
    drawAimArrow(camOffX, camOffY)

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

    // Death/teleport/explosion animations drawn as overlays
    drawDeathAnimations(camOffX, camOffY)
    drawTeleportAnimations(camOffX, camOffY)
    drawExplosionAnimations(camOffX, camOffY)

    drawCoordinates(currentWorld)
    drawAbilityHUD()
    drawInventory()
    drawChargeBar()
    drawLobbyHUD()

    // Respawn countdown overlay (drawn on top of world)
    if (client.getIsDead && client.isRespawning && !localDeathAnimActive) {
      drawRespawnCountdown()
    }
  }

  private def drawAimArrow(camOffX: Double, camOffY: Double): Unit = {
    if (!client.isCharging || client.getIsDead) return
    if (client.getSelectedCharacterDef.primaryProjectileType != ProjectileType.NORMAL) return

    val pos = client.getLocalPosition
    // Use grid position for direction (matches actual shot trajectory in MouseHandler)
    val gridX = pos.getX.toDouble
    val gridY = pos.getY.toDouble
    // Use visual position for draw origin (matches where the player appears on screen)
    val drawX = if (visualX.isNaN) gridX else visualX
    val drawY = if (visualY.isNaN) gridY else visualY

    val mouseWX = client.getMouseWorldX
    val mouseWY = client.getMouseWorldY
    val adx = mouseWX - gridX
    val ady = mouseWY - gridY
    val dist = Math.sqrt(adx * adx + ady * ady)
    if (dist < 0.01) return

    val ndx = adx / dist
    val ndy = ady / dist

    val chargeLevel = client.getChargeLevel
    val chargePct = chargeLevel / 100.0
    // +1 for spawn offset (projectile spawns 1 tile ahead of player)
    val cylLength = 1.0 + Constants.CHARGE_MIN_RANGE +
      chargePct * (Constants.CHARGE_MAX_RANGE - Constants.CHARGE_MIN_RANGE)

    val color = intToColor(client.getLocalColorRGB)
    val phase = animationTick * 0.12
    val pulse = 0.85 + 0.15 * Math.sin(phase * 2.0)

    // Perpendicular direction (cylinder width axis on ground plane)
    val perpX = -ndy
    val perpY = ndx
    val cylRadius = 0.6 // half-width in tiles

    // Helper: world point along cylinder at parameter t (0=player, 1=tip)
    // with perpendicular offset `w` (-1 to 1 across cylinder width)
    def cylScreenX(t: Double, w: Double): Double =
      worldToScreenX(drawX + ndx * cylLength * t + perpX * cylRadius * w,
                     drawY + ndy * cylLength * t + perpY * cylRadius * w, camOffX)
    def cylScreenY(t: Double, w: Double): Double =
      worldToScreenY(drawX + ndx * cylLength * t + perpX * cylRadius * w,
                     drawY + ndy * cylLength * t + perpY * cylRadius * w, camOffY)

    // Charge color: blend player color with charge heat (yellow -> orange -> red)
    val heatR = Math.min(1.0, chargePct * 2.0)
    val heatG = Math.min(1.0, Math.max(0.0, 1.0 - (chargePct - 0.5) * 2.0))
    val blendR = Math.min(1.0, color.getRed * 0.4 + heatR * 0.6)
    val blendG = Math.min(1.0, color.getGreen * 0.4 + heatG * 0.6)
    val blendB = Math.min(1.0, color.getBlue * 0.3)
    val brightR = Math.min(1.0, blendR * 0.3 + 0.7)
    val brightG = Math.min(1.0, blendG * 0.3 + 0.7)
    val brightB = Math.min(1.0, blendB * 0.3 + 0.7)

    // --- Layer 1: Wide soft ground glow beneath cylinder ---
    val glowN = 10
    val glowPoly = glowN * 2 + 2
    val glowX = new Array[Double](glowPoly)
    val glowY = new Array[Double](glowPoly)
    for (i <- 0 to glowN) {
      val t = i.toDouble / glowN
      val gw = 1.8 * pulse // wider than cylinder
      val cwx = drawX + ndx * cylLength * t
      val cwy = drawY + ndy * cylLength * t
      glowX(i) = worldToScreenX(cwx + perpX * gw, cwy + perpY * gw, camOffX)
      glowY(i) = worldToScreenY(cwx + perpX * gw, cwy + perpY * gw, camOffY)
      glowX(glowPoly - 1 - i) = worldToScreenX(cwx - perpX * gw, cwy - perpY * gw, camOffX)
      glowY(glowPoly - 1 - i) = worldToScreenY(cwx - perpX * gw, cwy - perpY * gw, camOffY)
    }
    gc.setFill(Color.color(blendR, blendG, blendB, 0.05 * pulse))
    gc.fillPolygon(glowX, glowY, glowPoly)

    // --- Layer 2: Cylinder body (constant-width tube with end caps) ---
    // Build body polygon: left edge forward, half-ellipse cap at tip, right edge backward, half-ellipse cap at base
    val bodyN = 20 // samples along each side
    val capN = 8   // samples per end cap half-ellipse
    val bodyTotal = bodyN * 2 + capN * 2 + 2
    val bodyX = new Array[Double](bodyTotal)
    val bodyY = new Array[Double](bodyTotal)
    var idx = 0

    // Left edge (base to tip)
    for (i <- 0 to bodyN) {
      val t = i.toDouble / bodyN
      bodyX(idx) = cylScreenX(t, 1.0)
      bodyY(idx) = cylScreenY(t, 1.0)
      idx += 1
    }
    // Tip cap (half-ellipse from left to right at t=1.0)
    for (i <- 1 to capN) {
      val angle = Math.PI * 0.5 - Math.PI * i.toDouble / capN // pi/2 to -pi/2
      val tw = Math.cos(angle) // perpendicular offset (-1 to 1)
      val tf = 1.0 + Math.sin(angle) * (cylRadius / cylLength) // slight forward bulge
      bodyX(idx) = cylScreenX(tf, tw)
      bodyY(idx) = cylScreenY(tf, tw)
      idx += 1
    }
    // Right edge (tip to base)
    for (i <- bodyN to 0 by -1) {
      val t = i.toDouble / bodyN
      bodyX(idx) = cylScreenX(t, -1.0)
      bodyY(idx) = cylScreenY(t, -1.0)
      idx += 1
    }
    // Base cap (half-ellipse from right to left at t=0)
    for (i <- 1 until capN) {
      val angle = -Math.PI * 0.5 + Math.PI * i.toDouble / capN
      val tw = -Math.cos(angle)
      val tf = -Math.sin(angle) * (cylRadius / cylLength)
      bodyX(idx) = cylScreenX(tf, tw)
      bodyY(idx) = cylScreenY(tf, tw)
      idx += 1
    }
    val actualCount = idx

    // Dark bottom fill (cylinder shadow side)
    gc.setFill(Color.color(blendR * 0.5, blendG * 0.5, blendB * 0.5, 0.10 + chargePct * 0.06))
    gc.fillPolygon(bodyX.take(actualCount), bodyY.take(actualCount), actualCount)

    // Cylinder outline
    gc.setStroke(Color.color(blendR, blendG, blendB, 0.18 + chargePct * 0.12))
    gc.setLineWidth(1.0)
    gc.strokePolygon(bodyX.take(actualCount), bodyY.take(actualCount), actualCount)

    // --- Layer 3: Top highlight strip (3D cylinder shading) ---
    // A narrower filled strip along the upper edge of the cylinder
    val stripN = 16
    val stripTotal = (stripN + 1) * 2
    val stripX = new Array[Double](stripTotal)
    val stripY = new Array[Double](stripTotal)
    for (i <- 0 to stripN) {
      val t = i.toDouble / stripN
      stripX(i) = cylScreenX(t, 0.9)
      stripY(i) = cylScreenY(t, 0.9)
      stripX(stripTotal - 1 - i) = cylScreenX(t, 0.3)
      stripY(stripTotal - 1 - i) = cylScreenY(t, 0.3)
    }
    gc.setFill(Color.color(brightR, brightG, brightB, 0.08 + chargePct * 0.06))
    gc.fillPolygon(stripX, stripY, stripTotal)

    // --- Layer 4: Animated energy rings flowing along cylinder ---
    gc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND)
    val ringCount = 4 + (chargePct * 4).toInt
    for (i <- 0 until ringCount) {
      val t = ((animationTick * 0.035 + i.toDouble / ringCount) % 1.0)
      val ringAlpha = (1.0 - Math.abs(t - 0.5) * 2.0) * (0.15 + chargePct * 0.2) * pulse

      // Draw a small elliptical ring cross-section at this t
      val ringSegs = 8
      val rxs = new Array[Double](ringSegs + 1)
      val rys = new Array[Double](ringSegs + 1)
      for (j <- 0 to ringSegs) {
        val angle = Math.PI * j.toDouble / ringSegs - Math.PI * 0.5
        val w = Math.cos(angle)
        val fwd = Math.sin(angle) * (cylRadius / cylLength) * 0.3
        rxs(j) = cylScreenX(t + fwd, w)
        rys(j) = cylScreenY(t + fwd, w)
      }
      gc.setStroke(Color.color(brightR, brightG, brightB, Math.max(0.0, Math.min(1.0, ringAlpha))))
      gc.setLineWidth(1.5 + chargePct)
      for (j <- 0 until ringSegs) {
        gc.strokeLine(rxs(j), rys(j), rxs(j + 1), rys(j + 1))
      }
    }

    // --- Layer 5: Energy spine (bright center line) ---
    val spineSegs = 14
    for (i <- 0 until spineSegs) {
      val t0 = i.toDouble / spineSegs
      val t1 = (i + 1).toDouble / spineSegs
      val fadeAlpha = (1.0 - t0 * 0.7) * (0.08 + chargePct * 0.12) * pulse

      gc.setStroke(Color.color(brightR, brightG, brightB, Math.max(0.0, Math.min(1.0, fadeAlpha))))
      gc.setLineWidth((2.0 + chargePct * 1.5) * (1.0 - t0 * 0.5))
      gc.strokeLine(cylScreenX(t0, 0.0), cylScreenY(t0, 0.0),
                    cylScreenX(t1, 0.0), cylScreenY(t1, 0.0))
    }

    // --- Layer 6: Glowing tip cap ---
    val tipSX = cylScreenX(1.0, 0.0)
    val tipSY = cylScreenY(1.0, 0.0)
    val orbR = (4.0 + chargePct * 5.0) * pulse

    gc.setFill(Color.color(blendR, blendG, blendB, 0.10 * pulse))
    gc.fillOval(tipSX - orbR * 2, tipSY - orbR, orbR * 4, orbR * 2)
    gc.setFill(Color.color(brightR, brightG, brightB, 0.25 * pulse))
    gc.fillOval(tipSX - orbR, tipSY - orbR * 0.5, orbR * 2, orbR)
  }

  private def drawSingleItem(item: Item, camOffX: Double, camOffY: Double): Unit = {
    val ix = item.getCellX
    val iy = item.getCellY

    val centerX = worldToScreenX(ix, iy, camOffX)
    val halfSize = Constants.ITEM_SIZE_PX / 2.0

    // Bobbing animation - each item has a unique phase offset
    val bobPhase = animationTick * 0.06 + item.id * 1.7
    val bobOffset = Math.sin(bobPhase) * 4.0
    val centerY = worldToScreenY(ix, iy, camOffY) + bobOffset

    if (centerX > -halfSize * 2 && centerX < getWidth + halfSize * 2 &&
        centerY > -halfSize * 2 && centerY < getHeight + halfSize * 2) {

      val itemColor = intToColor(item.colorRGB)

      // Soft glow underneath the item
      val glowPulse = 0.8 + 0.2 * Math.sin(bobPhase * 1.3)
      gc.setFill(Color.color(itemColor.getRed, itemColor.getGreen, itemColor.getBlue, 0.12 * glowPulse))
      gc.fillOval(centerX - halfSize * 1.4, centerY + halfSize * 0.2, halfSize * 2.8, halfSize * 0.8)

      // Outer colored aura
      gc.setFill(Color.color(itemColor.getRed, itemColor.getGreen, itemColor.getBlue, 0.06 * glowPulse))
      gc.fillOval(centerX - halfSize * 1.8, centerY - halfSize * 1.2, halfSize * 3.6, halfSize * 2.8)

      // Main item shape
      gc.setFill(itemColor)
      gc.setStroke(Color.color(0, 0, 0, 0.6))
      gc.setLineWidth(1)
      drawItemShape(item.itemType, centerX, centerY, halfSize)

      // Sparkle particles orbiting the item
      for (i <- 0 until 3) {
        val sparkAngle = bobPhase * 0.8 + i * (2 * Math.PI / 3)
        val sparkDist = halfSize * 1.1
        val sparkX = centerX + sparkDist * Math.cos(sparkAngle)
        val sparkY = centerY + sparkDist * Math.sin(sparkAngle) * 0.6
        val sparkAlpha = 0.3 + 0.4 * Math.sin(bobPhase * 2.5 + i * 2.1)
        val sparkSize = 1.5 + Math.sin(bobPhase * 3.0 + i) * 0.7
        gc.setFill(Color.color(1.0, 1.0, 1.0, Math.max(0, Math.min(1, sparkAlpha))))
        gc.fillOval(sparkX - sparkSize, sparkY - sparkSize, sparkSize * 2, sparkSize * 2)
      }
    }
  }

  private def drawItemShape(itemType: ItemType, centerX: Double, centerY: Double, halfSize: Double): Unit = {
    // Save current fill/stroke for shape drawing
    itemType match {
      case ItemType.Heart =>
        // Improved heart with highlight
        val r = halfSize * 0.55
        gc.fillOval(centerX - halfSize * 0.5 - r, centerY - halfSize * 0.45 - r, r * 2, r * 2)
        gc.fillOval(centerX + halfSize * 0.5 - r, centerY - halfSize * 0.45 - r, r * 2, r * 2)
        val txPoints = Array(centerX - halfSize * 1.05, centerX, centerX + halfSize * 1.05)
        val tyPoints = Array(centerY - halfSize * 0.1, centerY + halfSize, centerY - halfSize * 0.1)
        gc.fillPolygon(txPoints, tyPoints, 3)
        // Highlight
        gc.setFill(Color.color(1.0, 1.0, 1.0, 0.3))
        val hr = r * 0.5
        gc.fillOval(centerX - halfSize * 0.35 - hr, centerY - halfSize * 0.55 - hr, hr * 2, hr * 2)

      case ItemType.Star =>
        // Star with subtle rotation and golden highlight
        val outerR = halfSize
        val innerR = halfSize * 0.4
        val rotOffset = Math.sin(animationTick * 0.04) * 0.08
        val xPoints = new Array[Double](10)
        val yPoints = new Array[Double](10)
        for (i <- 0 until 10) {
          val angle = Math.PI / 2 + i * Math.PI / 5 + rotOffset
          val rad = if (i % 2 == 0) outerR else innerR
          xPoints(i) = centerX + rad * Math.cos(angle)
          yPoints(i) = centerY - rad * Math.sin(angle)
        }
        gc.fillPolygon(xPoints, yPoints, 10)
        gc.strokePolygon(xPoints, yPoints, 10)
        // Golden center highlight
        gc.setFill(Color.color(1.0, 1.0, 0.7, 0.4))
        gc.fillOval(centerX - halfSize * 0.3, centerY - halfSize * 0.3, halfSize * 0.6, halfSize * 0.6)

      case ItemType.Gem =>
        // Diamond with faceted look
        val xp = Array(centerX, centerX + halfSize, centerX, centerX - halfSize)
        val yp = Array(centerY - halfSize, centerY, centerY + halfSize, centerY)
        gc.fillPolygon(xp, yp, 4)
        gc.strokePolygon(xp, yp, 4)
        // Inner facet lines
        gc.setStroke(Color.color(0.0, 0.8, 0.9, 0.35))
        gc.setLineWidth(0.5)
        gc.strokeLine(centerX, centerY - halfSize, centerX + halfSize * 0.35, centerY)
        gc.strokeLine(centerX, centerY - halfSize, centerX - halfSize * 0.35, centerY)
        gc.strokeLine(centerX - halfSize * 0.35, centerY, centerX, centerY + halfSize)
        gc.strokeLine(centerX + halfSize * 0.35, centerY, centerX, centerY + halfSize)
        // Bright highlight on upper face
        gc.setFill(Color.color(1.0, 1.0, 1.0, 0.3))
        gc.fillPolygon(
          Array(centerX, centerX + halfSize * 0.35, centerX - halfSize * 0.35),
          Array(centerY - halfSize, centerY - halfSize * 0.15, centerY - halfSize * 0.15),
          3
        )

      case ItemType.Shield =>
        // Rounded shield shape with emblem
        val xPoints = Array(
          centerX - halfSize * 0.85, centerX - halfSize * 0.5,
          centerX + halfSize * 0.5, centerX + halfSize * 0.85,
          centerX + halfSize * 0.7, centerX,
          centerX - halfSize * 0.7
        )
        val yPoints = Array(
          centerY - halfSize * 0.6, centerY - halfSize,
          centerY - halfSize, centerY - halfSize * 0.6,
          centerY + halfSize * 0.4, centerY + halfSize,
          centerY + halfSize * 0.4
        )
        gc.fillPolygon(xPoints, yPoints, 7)
        gc.strokePolygon(xPoints, yPoints, 7)
        // Cross emblem
        gc.setFill(Color.color(1.0, 1.0, 1.0, 0.25))
        gc.fillRect(centerX - halfSize * 0.08, centerY - halfSize * 0.5, halfSize * 0.16, halfSize * 0.8)
        gc.fillRect(centerX - halfSize * 0.3, centerY - halfSize * 0.18, halfSize * 0.6, halfSize * 0.16)

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
    projectile.projectileType match {
      case ProjectileType.TENTACLE => drawTentacleProjectile(projectile, camOffX, camOffY)
      case ProjectileType.ICE_BEAM => drawIceBeamProjectile(projectile, camOffX, camOffY)
      case ProjectileType.AXE => drawAxeProjectile(projectile, camOffX, camOffY)
      case ProjectileType.ROPE => drawRopeProjectile(projectile, camOffX, camOffY)
      case ProjectileType.SPEAR => drawSpearProjectile(projectile, camOffX, camOffY)
      case ProjectileType.SOUL_BOLT => drawSoulBoltProjectile(projectile, camOffX, camOffY)
      case ProjectileType.HAUNT => drawHauntProjectile(projectile, camOffX, camOffY)
      case ProjectileType.ARCANE_BOLT => drawArcaneBoltProjectile(projectile, camOffX, camOffY)
      case ProjectileType.FIREBALL => drawFireballProjectile(projectile, camOffX, camOffY)
      case ProjectileType.SPLASH => drawSplashProjectile(projectile, camOffX, camOffY)
      case ProjectileType.TIDAL_WAVE => drawTidalWaveProjectile(projectile, camOffX, camOffY)
      case ProjectileType.GEYSER => drawGeyserProjectile(projectile, camOffX, camOffY)
      case ProjectileType.BULLET => drawBulletProjectile(projectile, camOffX, camOffY)
      case ProjectileType.GRENADE => drawGrenadeProjectile(projectile, camOffX, camOffY)
      case ProjectileType.ROCKET => drawRocketProjectile(projectile, camOffX, camOffY)
      case ProjectileType.TALON => drawTalonProjectile(projectile, camOffX, camOffY)
      case ProjectileType.GUST => drawGustProjectile(projectile, camOffX, camOffY)
      case _ => drawNormalProjectile(projectile, camOffX, camOffY)
    }
  }

  private def drawNormalProjectile(projectile: Projectile, camOffX: Double, camOffY: Double): Unit = {
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

  private def drawTentacleProjectile(projectile: Projectile, camOffX: Double, camOffY: Double): Unit = {
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

      gc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND)
      val phase = (animationTick + projectile.id * 23) * 0.4
      val pulse = 0.85 + 0.15 * Math.sin(phase)

      val dx = tipX - tailX
      val dy = tipY - tailY
      val len = Math.sqrt(dx * dx + dy * dy)
      if (len < 1) return
      val nx = dx / len
      val ny = dy / len

      // Draw 3 wavy tendril strands
      for (strand <- 0 until 3) {
        val strandOffset = (strand - 1) * 5.0
        val segments = 12
        val points = (0 to segments).map { seg =>
          val t = seg.toDouble / segments
          val wave = Math.sin(phase * 2.0 + t * Math.PI * 3 + strand * 2.1) * (6.0 + strand * 2.0) * (1.0 - t * 0.5)
          val bx = tailX + dx * t + (-ny * strandOffset + ny * wave) * 0.3 + (-ny) * wave
          val by = tailY + dy * t + (nx * strandOffset - nx * wave) * 0.3 + nx * wave
          (bx, by)
        }

        // Outer glow
        gc.setStroke(Color.color(0.1, 0.7, 0.2, 0.15 * pulse))
        gc.setLineWidth(8.0 * pulse)
        for (i <- 0 until points.length - 1) {
          gc.strokeLine(points(i)._1, points(i)._2, points(i + 1)._1, points(i + 1)._2)
        }

        // Main tendril
        gc.setStroke(Color.color(0.2, 0.8, 0.3, 0.6 * pulse))
        gc.setLineWidth(4.0 * pulse)
        for (i <- 0 until points.length - 1) {
          gc.strokeLine(points(i)._1, points(i)._2, points(i + 1)._1, points(i + 1)._2)
        }

        // Bright core
        gc.setStroke(Color.color(0.5, 1.0, 0.6, 0.8 * pulse))
        gc.setLineWidth(1.5)
        for (i <- 0 until points.length - 1) {
          gc.strokeLine(points(i)._1, points(i)._2, points(i + 1)._1, points(i + 1)._2)
        }
      }

      // Purple/green glow orb at tip
      val orbR = 7.0 * pulse
      gc.setFill(Color.color(0.5, 0.2, 0.8, 0.2 * pulse))
      gc.fillOval(tipX - orbR * 2, tipY - orbR * 2, orbR * 4, orbR * 4)
      gc.setFill(Color.color(0.3, 0.9, 0.4, 0.5 * pulse))
      gc.fillOval(tipX - orbR, tipY - orbR, orbR * 2, orbR * 2)
      gc.setFill(Color.color(0.7, 1.0, 0.8, 0.85))
      gc.fillOval(tipX - orbR * 0.4, tipY - orbR * 0.4, orbR * 0.8, orbR * 0.8)
    }
  }

  private def drawIceBeamProjectile(projectile: Projectile, camOffX: Double, camOffY: Double): Unit = {
    val projX = projectile.getX.toDouble
    val projY = projectile.getY.toDouble
    val beamLength = 4.0

    val tailX = worldToScreenX(projX, projY, camOffX)
    val tailY = worldToScreenY(projX, projY, camOffY)
    val tipWX = projX + projectile.dx * beamLength
    val tipWY = projY + projectile.dy * beamLength
    val tipX = worldToScreenX(tipWX, tipWY, camOffX)
    val tipY = worldToScreenY(tipWX, tipWY, camOffY)

    val margin = 100.0
    if (Math.max(tailX, tipX) > -margin && Math.min(tailX, tipX) < getWidth + margin &&
        Math.max(tailY, tipY) > -margin && Math.min(tailY, tipY) < getHeight + margin) {

      gc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND)
      val phase = (animationTick + projectile.id * 31) * 0.25
      val pulse = 0.85 + 0.15 * Math.sin(phase)
      val fastFlicker = 0.9 + 0.1 * Math.sin(phase * 3.3)

      // Layer 1: Wide icy outer glow
      gc.setStroke(Color.color(0.3, 0.6, 1.0, 0.08 * pulse))
      gc.setLineWidth(40 * pulse)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      // Layer 2: Mid glow
      gc.setStroke(Color.color(0.4, 0.7, 1.0, 0.15 * pulse))
      gc.setLineWidth(24 * pulse)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      // Layer 3: Bright blue beam
      gc.setStroke(Color.color(0.5, 0.8, 1.0, 0.5 * fastFlicker))
      gc.setLineWidth(12 * fastFlicker)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      // Layer 4: White-blue core
      gc.setStroke(Color.color(0.8, 0.9, 1.0, 0.9 * fastFlicker))
      gc.setLineWidth(4.0)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      // Crystal particles along beam
      val dx = tipX - tailX
      val dy = tipY - tailY
      val len = Math.sqrt(dx * dx + dy * dy)
      if (len > 1) {
        val nx = dx / len
        val ny = dy / len
        for (i <- 0 until 8) {
          val t = ((animationTick * 0.06 + i.toDouble / 8 + projectile.id * 0.17) % 1.0)
          val sparkle = Math.sin(phase * 3 + i * 1.7)
          val px = tailX + dx * t + (-ny) * sparkle * 8.0
          val py = tailY + dy * t + nx * sparkle * 8.0
          val pAlpha = Math.max(0.0, Math.min(1.0, 0.6 * (1.0 - Math.abs(t - 0.5) * 2.0)))
          val pSize = 2.5 + Math.sin(phase + i * 0.8) * 1.0
          gc.setFill(Color.color(0.8, 0.95, 1.0, pAlpha))
          gc.fillOval(px - pSize, py - pSize, pSize * 2, pSize * 2)
        }
      }

      // Icy orb at the tip
      val orbR = 8.0 * pulse
      gc.setFill(Color.color(0.3, 0.6, 1.0, 0.2 * pulse))
      gc.fillOval(tipX - orbR * 2, tipY - orbR * 2, orbR * 4, orbR * 4)
      gc.setFill(Color.color(0.5, 0.8, 1.0, 0.4 * pulse))
      gc.fillOval(tipX - orbR, tipY - orbR, orbR * 2, orbR * 2)
      gc.setFill(Color.color(0.9, 0.95, 1.0, 0.85 * fastFlicker))
      gc.fillOval(tipX - orbR * 0.5, tipY - orbR * 0.5, orbR, orbR)
    }
  }

  private def drawAxeProjectile(projectile: Projectile, camOffX: Double, camOffY: Double): Unit = {
    val projX = projectile.getX.toDouble
    val projY = projectile.getY.toDouble
    val beamLength = 4.0

    val tailX = worldToScreenX(projX, projY, camOffX)
    val tailY = worldToScreenY(projX, projY, camOffY)
    val tipWX = projX + projectile.dx * beamLength
    val tipWY = projY + projectile.dy * beamLength
    val tipX = worldToScreenX(tipWX, tipWY, camOffX)
    val tipY = worldToScreenY(tipWX, tipWY, camOffY)

    val margin = 100.0
    if (Math.max(tailX, tipX) > -margin && Math.min(tailX, tipX) < getWidth + margin &&
        Math.max(tailY, tipY) > -margin && Math.min(tailY, tipY) < getHeight + margin) {

      gc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND)
      val phase = (animationTick + projectile.id * 17) * 0.5
      val pulse = 0.85 + 0.15 * Math.sin(phase)
      val spinAngle = animationTick * 0.4 + projectile.id * 1.3

      val dx = tipX - tailX
      val dy = tipY - tailY
      val len = Math.sqrt(dx * dx + dy * dy)
      if (len < 1) return
      val nx = dx / len
      val ny = dy / len

      // Spinning sweep for the whole axe
      val sweepOffset = Math.sin(spinAngle) * 14.0
      val perpX = -ny * sweepOffset
      val perpY = nx * sweepOffset

      // The handle runs from tail to ~70% of the way to tip
      // The axe blade occupies the last ~30%
      val handleEndT = 0.65
      val handleEndX = tailX + dx * handleEndT + perpX * handleEndT
      val handleEndY = tailY + dy * handleEndT + perpY * handleEndT
      val bladeX = tipX + perpX
      val bladeY = tipY + perpY

      // --- Handle: thin brown/dark stick ---
      // Handle glow
      gc.setStroke(Color.color(0.45, 0.3, 0.15, 0.12 * pulse))
      gc.setLineWidth(12 * pulse)
      gc.strokeLine(tailX + perpX * 0.2, tailY + perpY * 0.2, handleEndX, handleEndY)
      // Handle core
      gc.setStroke(Color.color(0.55, 0.35, 0.18, 0.7 * pulse))
      gc.setLineWidth(5 * pulse)
      gc.strokeLine(tailX + perpX * 0.2, tailY + perpY * 0.2, handleEndX, handleEndY)
      // Handle bright center
      gc.setStroke(Color.color(0.7, 0.5, 0.25, 0.85 * pulse))
      gc.setLineWidth(2.0)
      gc.strokeLine(tailX + perpX * 0.2, tailY + perpY * 0.2, handleEndX, handleEndY)

      // --- Axe blade: wide crescent from handle-end to tip ---
      // Perpendicular to travel direction for the wide blade spread
      val bladeSpread = 22.0 * pulse
      val bladeNx = -ny  // perpendicular
      val bladeNy = nx

      // Blade glow (wide)
      gc.setFill(Color.color(0.75, 0.75, 0.8, 0.08 * pulse))
      val glowR = bladeSpread * 2.0
      val bladeMidX = (handleEndX + bladeX) * 0.5
      val bladeMidY = (handleEndY + bladeY) * 0.5
      gc.fillOval(bladeMidX - glowR, bladeMidY - glowR, glowR * 2, glowR * 2)

      // Blade polygon: crescent shape
      // 5 points: handle-end narrow, blade-mid wide, tip narrow
      val bx = Array(
        handleEndX + bladeNx * 4.0,    // handle-end top
        bladeMidX + bladeNx * bladeSpread, // mid top (widest)
        bladeX + bladeNx * 8.0,         // tip top
        bladeX - bladeNx * 8.0,         // tip bottom
        bladeMidX - bladeNx * bladeSpread, // mid bottom (widest)
        handleEndX - bladeNx * 4.0     // handle-end bottom
      )
      val by = Array(
        handleEndY + bladeNy * 4.0,
        bladeMidY + bladeNy * bladeSpread,
        bladeY + bladeNy * 8.0,
        bladeY - bladeNy * 8.0,
        bladeMidY - bladeNy * bladeSpread,
        handleEndY - bladeNy * 4.0
      )

      // Outer blade glow
      gc.setFill(Color.color(0.7, 0.7, 0.75, 0.2 * pulse))
      gc.fillPolygon(bx, by, 6)
      // Main blade fill - metallic silver
      // Slightly inset version for the solid blade
      val inset = 0.75
      val ibx = bx.zipWithIndex.map { case (x, i) =>
        bladeMidX + (x - bladeMidX) * inset
      }
      val iby = by.zipWithIndex.map { case (y, i) =>
        bladeMidY + (y - bladeMidY) * inset
      }
      gc.setFill(Color.color(0.8, 0.8, 0.85, 0.6 * pulse))
      gc.fillPolygon(ibx, iby, 6)
      // Blade edge highlight
      gc.setStroke(Color.color(0.95, 0.95, 1.0, 0.7 * pulse))
      gc.setLineWidth(2.0)
      gc.strokePolygon(ibx, iby, 6)

      // Bright core line along the blade center
      gc.setStroke(Color.color(0.95, 0.95, 1.0, 0.5 * pulse))
      gc.setLineWidth(3.0)
      gc.strokeLine(handleEndX, handleEndY, bladeX, bladeY)
    }
  }

  private def drawRopeProjectile(projectile: Projectile, camOffX: Double, camOffY: Double): Unit = {
    val projX = projectile.getX.toDouble
    val projY = projectile.getY.toDouble
    val beamLength = 4.0

    val tailX = worldToScreenX(projX, projY, camOffX)
    val tailY = worldToScreenY(projX, projY, camOffY)
    val tipWX = projX + projectile.dx * beamLength
    val tipWY = projY + projectile.dy * beamLength
    val tipX = worldToScreenX(tipWX, tipWY, camOffX)
    val tipY = worldToScreenY(tipWX, tipWY, camOffY)

    val margin = 100.0
    if (Math.max(tailX, tipX) > -margin && Math.min(tailX, tipX) < getWidth + margin &&
        Math.max(tailY, tipY) > -margin && Math.min(tailY, tipY) < getHeight + margin) {

      gc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND)
      val phase = (animationTick + projectile.id * 29) * 0.3
      val pulse = 0.85 + 0.15 * Math.sin(phase)

      val dx = tipX - tailX
      val dy = tipY - tailY
      val len = Math.sqrt(dx * dx + dy * dy)
      if (len < 1) return
      val nx = dx / len
      val ny = dy / len

      // Single wavy strand (less wavy than tentacle)
      val segments = 10
      val points = (0 to segments).map { seg =>
        val t = seg.toDouble / segments
        val wave = Math.sin(phase * 1.5 + t * Math.PI * 2) * 4.0 * (1.0 - t * 0.3)
        val bx = tailX + dx * t + (-ny) * wave
        val by = tailY + dy * t + nx * wave
        (bx, by)
      }

      // Outer glow - brown/tan
      gc.setStroke(Color.color(0.6, 0.4, 0.2, 0.15 * pulse))
      gc.setLineWidth(10.0 * pulse)
      for (i <- 0 until points.length - 1) {
        gc.strokeLine(points(i)._1, points(i)._2, points(i + 1)._1, points(i + 1)._2)
      }

      // Main rope strand
      gc.setStroke(Color.color(0.7, 0.5, 0.3, 0.7 * pulse))
      gc.setLineWidth(5.0 * pulse)
      for (i <- 0 until points.length - 1) {
        gc.strokeLine(points(i)._1, points(i)._2, points(i + 1)._1, points(i + 1)._2)
      }

      // Bright core
      gc.setStroke(Color.color(0.85, 0.7, 0.5, 0.8 * pulse))
      gc.setLineWidth(2.0)
      for (i <- 0 until points.length - 1) {
        gc.strokeLine(points(i)._1, points(i)._2, points(i + 1)._1, points(i + 1)._2)
      }

      // Brown orb at tip
      val orbR = 6.0 * pulse
      gc.setFill(Color.color(0.6, 0.4, 0.2, 0.3 * pulse))
      gc.fillOval(tipX - orbR * 2, tipY - orbR * 2, orbR * 4, orbR * 4)
      gc.setFill(Color.color(0.7, 0.5, 0.3, 0.5 * pulse))
      gc.fillOval(tipX - orbR, tipY - orbR, orbR * 2, orbR * 2)
      gc.setFill(Color.color(0.85, 0.7, 0.5, 0.85))
      gc.fillOval(tipX - orbR * 0.4, tipY - orbR * 0.4, orbR * 0.8, orbR * 0.8)
    }
  }

  private def drawSpearProjectile(projectile: Projectile, camOffX: Double, camOffY: Double): Unit = {
    val projX = projectile.getX.toDouble
    val projY = projectile.getY.toDouble
    val beamLength = 7.0

    val tailX = worldToScreenX(projX, projY, camOffX)
    val tailY = worldToScreenY(projX, projY, camOffY)
    val tipWX = projX + projectile.dx * beamLength
    val tipWY = projY + projectile.dy * beamLength
    val tipX = worldToScreenX(tipWX, tipWY, camOffX)
    val tipY = worldToScreenY(tipWX, tipWY, camOffY)

    val margin = 100.0
    if (Math.max(tailX, tipX) > -margin && Math.min(tailX, tipX) < getWidth + margin &&
        Math.max(tailY, tipY) > -margin && Math.min(tailY, tipY) < getHeight + margin) {

      gc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND)
      val phase = (animationTick + projectile.id * 41) * 0.2
      val pulse = 0.9 + 0.1 * Math.sin(phase)
      val fastFlicker = 0.9 + 0.1 * Math.sin(phase * 3.1)

      // Bronze/gold color scheme - straight line beam
      // Layer 1: Ultra-wide outer glow
      gc.setStroke(Color.color(0.8, 0.6, 0.2, 0.06 * pulse))
      gc.setLineWidth(48 * pulse)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      // Layer 2: Outer glow
      gc.setStroke(Color.color(0.8, 0.6, 0.2, 0.10 * pulse))
      gc.setLineWidth(34 * pulse)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      // Layer 3: Mid glow
      gc.setStroke(Color.color(0.85, 0.65, 0.25, 0.22 * pulse))
      gc.setLineWidth(22 * pulse)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      // Layer 4: Bronze beam
      gc.setStroke(Color.color(0.9, 0.7, 0.3, 0.55 * fastFlicker))
      gc.setLineWidth(12 * fastFlicker)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      // Layer 5: Bright gold core
      gc.setStroke(Color.color(1.0, 0.9, 0.6, 0.9 * fastFlicker))
      gc.setLineWidth(5.0)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      // Pointed arrowhead at tip - bigger
      val dx = tipX - tailX
      val dy = tipY - tailY
      val len = Math.sqrt(dx * dx + dy * dy)
      if (len > 1) {
        val nx = dx / len
        val ny = dy / len
        val arrowSize = 16.0
        val arrowX = Array(
          tipX + nx * arrowSize,
          tipX - nx * arrowSize * 0.5 + (-ny) * arrowSize * 0.6,
          tipX - nx * arrowSize * 0.5 + ny * arrowSize * 0.6
        )
        val arrowY = Array(
          tipY + ny * arrowSize,
          tipY - ny * arrowSize * 0.5 + nx * arrowSize * 0.6,
          tipY - ny * arrowSize * 0.5 + (-nx) * arrowSize * 0.6
        )
        gc.setFill(Color.color(0.9, 0.75, 0.35, 0.85 * pulse))
        gc.fillPolygon(arrowX, arrowY, 3)
        gc.setStroke(Color.color(1.0, 0.9, 0.6, 0.6 * pulse))
        gc.setLineWidth(1.5)
        gc.strokePolygon(arrowX, arrowY, 3)

        // Trail particles
        for (i <- 0 until 6) {
          val t = ((animationTick * 0.07 + i.toDouble / 6 + projectile.id * 0.11) % 1.0)
          val px = tailX + dx * t + (-ny) * Math.sin(phase + i * 1.5) * 4.0
          val py = tailY + dy * t + nx * Math.sin(phase + i * 1.5) * 4.0
          val pAlpha = Math.max(0.0, Math.min(1.0, 0.5 * (1.0 - t)))
          val pSize = 3.0 + Math.sin(phase + i) * 1.0
          gc.setFill(Color.color(1.0, 0.85, 0.4, pAlpha))
          gc.fillOval(px - pSize, py - pSize, pSize * 2, pSize * 2)
        }
      }
    }
  }

  private def drawSoulBoltProjectile(projectile: Projectile, camOffX: Double, camOffY: Double): Unit = {
    val projX = projectile.getX.toDouble
    val projY = projectile.getY.toDouble
    val beamLength = 4.0

    val tailX = worldToScreenX(projX, projY, camOffX)
    val tailY = worldToScreenY(projX, projY, camOffY)
    val tipWX = projX + projectile.dx * beamLength
    val tipWY = projY + projectile.dy * beamLength
    val tipX = worldToScreenX(tipWX, tipWY, camOffX)
    val tipY = worldToScreenY(tipWX, tipWY, camOffY)

    val margin = 100.0
    if (Math.max(tailX, tipX) > -margin && Math.min(tailX, tipX) < getWidth + margin &&
        Math.max(tailY, tipY) > -margin && Math.min(tailY, tipY) < getHeight + margin) {

      gc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND)
      val phase = (animationTick + projectile.id * 19) * 0.35
      val pulse = 0.85 + 0.15 * Math.sin(phase)
      val fastFlicker = 0.9 + 0.1 * Math.sin(phase * 3.7)

      // Ghostly green/teal glow layers
      gc.setStroke(Color.color(0.1, 0.8, 0.6, 0.07 * pulse))
      gc.setLineWidth(36 * pulse)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      gc.setStroke(Color.color(0.1, 0.85, 0.65, 0.14 * pulse))
      gc.setLineWidth(22 * pulse)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      gc.setStroke(Color.color(0.2, 0.9, 0.7, 0.45 * fastFlicker))
      gc.setLineWidth(10 * fastFlicker)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      gc.setStroke(Color.color(0.6, 1.0, 0.85, 0.9 * fastFlicker))
      gc.setLineWidth(3.5)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      // Wispy particles
      val dx = tipX - tailX
      val dy = tipY - tailY
      val len = Math.sqrt(dx * dx + dy * dy)
      if (len > 1) {
        val nx = dx / len
        val ny = dy / len
        for (i <- 0 until 5) {
          val t = ((animationTick * 0.09 + i.toDouble / 5 + projectile.id * 0.15) % 1.0)
          val wave = Math.sin(phase * 2.5 + i * 1.9) * 7.0
          val px = tailX + dx * t + (-ny) * wave
          val py = tailY + dy * t + nx * wave
          val pAlpha = Math.max(0.0, Math.min(1.0, 0.6 * (1.0 - Math.abs(t - 0.5) * 2.0)))
          val pSize = 2.5 + Math.sin(phase + i) * 1.0
          gc.setFill(Color.color(0.5, 1.0, 0.8, pAlpha))
          gc.fillOval(px - pSize, py - pSize, pSize * 2, pSize * 2)
        }
      }

      // Ghostly orb at tip
      val orbR = 7.0 * pulse
      gc.setFill(Color.color(0.1, 0.8, 0.6, 0.2 * pulse))
      gc.fillOval(tipX - orbR * 2, tipY - orbR * 2, orbR * 4, orbR * 4)
      gc.setFill(Color.color(0.2, 0.9, 0.7, 0.45 * pulse))
      gc.fillOval(tipX - orbR, tipY - orbR, orbR * 2, orbR * 2)
      gc.setFill(Color.color(0.6, 1.0, 0.85, 0.85 * fastFlicker))
      gc.fillOval(tipX - orbR * 0.4, tipY - orbR * 0.4, orbR * 0.8, orbR * 0.8)
    }
  }

  private def drawHauntProjectile(projectile: Projectile, camOffX: Double, camOffY: Double): Unit = {
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

      gc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND)
      val phase = (animationTick + projectile.id * 43) * 0.25
      val pulse = 0.8 + 0.2 * Math.sin(phase)
      val fastFlicker = 0.85 + 0.15 * Math.sin(phase * 2.9)

      // Dark spectral glow — purple/dark teal
      gc.setStroke(Color.color(0.3, 0.1, 0.5, 0.1 * pulse))
      gc.setLineWidth(42 * pulse)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      gc.setStroke(Color.color(0.35, 0.15, 0.55, 0.18 * pulse))
      gc.setLineWidth(28 * pulse)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      gc.setStroke(Color.color(0.4, 0.2, 0.65, 0.4 * fastFlicker))
      gc.setLineWidth(14 * fastFlicker)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      gc.setStroke(Color.color(0.7, 0.5, 0.9, 0.85 * fastFlicker))
      gc.setLineWidth(4.0)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      // Shadowy tendrils along the beam
      val dx = tipX - tailX
      val dy = tipY - tailY
      val len = Math.sqrt(dx * dx + dy * dy)
      if (len > 1) {
        val nx = dx / len
        val ny = dy / len
        for (strand <- 0 until 2) {
          val strandOff = (strand - 0.5) * 6.0
          for (i <- 0 until 6) {
            val t = ((animationTick * 0.07 + i.toDouble / 6 + strand * 0.3) % 1.0)
            val wave = Math.sin(phase * 1.8 + i * 2.0 + strand * 3.14) * 5.0
            val px = tailX + dx * t + (-ny) * (wave + strandOff)
            val py = tailY + dy * t + nx * (wave + strandOff)
            val pAlpha = Math.max(0.0, Math.min(1.0, 0.5 * (1.0 - Math.abs(t - 0.5) * 2.0)))
            val pSize = 2.0 + Math.sin(phase + i + strand) * 0.8
            gc.setFill(Color.color(0.5, 0.2, 0.7, pAlpha))
            gc.fillOval(px - pSize, py - pSize, pSize * 2, pSize * 2)
          }
        }
      }

      // Dark orb at tip
      val orbR = 9.0 * pulse
      gc.setFill(Color.color(0.3, 0.1, 0.5, 0.25 * pulse))
      gc.fillOval(tipX - orbR * 2.5, tipY - orbR * 2.5, orbR * 5, orbR * 5)
      gc.setFill(Color.color(0.4, 0.2, 0.65, 0.5 * pulse))
      gc.fillOval(tipX - orbR * 1.3, tipY - orbR * 1.3, orbR * 2.6, orbR * 2.6)
      gc.setFill(Color.color(0.7, 0.5, 0.9, 0.85 * fastFlicker))
      gc.fillOval(tipX - orbR * 0.5, tipY - orbR * 0.5, orbR, orbR)
    }
  }

  private def drawArcaneBoltProjectile(projectile: Projectile, camOffX: Double, camOffY: Double): Unit = {
    val projX = projectile.getX.toDouble
    val projY = projectile.getY.toDouble
    val beamLength = 4.0

    val tailX = worldToScreenX(projX, projY, camOffX)
    val tailY = worldToScreenY(projX, projY, camOffY)
    val tipWX = projX + projectile.dx * beamLength
    val tipWY = projY + projectile.dy * beamLength
    val tipX = worldToScreenX(tipWX, tipWY, camOffX)
    val tipY = worldToScreenY(tipWX, tipWY, camOffY)

    val margin = 100.0
    if (Math.max(tailX, tipX) > -margin && Math.min(tailX, tipX) < getWidth + margin &&
        Math.max(tailY, tipY) > -margin && Math.min(tailY, tipY) < getHeight + margin) {

      gc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND)
      val phase = (animationTick + projectile.id * 23) * 0.4
      val pulse = 0.85 + 0.15 * Math.sin(phase)
      val fastFlicker = 0.9 + 0.1 * Math.sin(phase * 3.2)

      // Purple/blue arcane glow layers
      gc.setStroke(Color.color(0.4, 0.1, 0.8, 0.08 * pulse))
      gc.setLineWidth(32 * pulse)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      gc.setStroke(Color.color(0.45, 0.15, 0.85, 0.15 * pulse))
      gc.setLineWidth(20 * pulse)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      gc.setStroke(Color.color(0.5, 0.3, 0.9, 0.45 * fastFlicker))
      gc.setLineWidth(9 * fastFlicker)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      gc.setStroke(Color.color(0.7, 0.5, 1.0, 0.9 * fastFlicker))
      gc.setLineWidth(3.0)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      // Arcane sparkles along beam
      val dx = tipX - tailX
      val dy = tipY - tailY
      val len = Math.sqrt(dx * dx + dy * dy)
      if (len > 1) {
        val nx = dx / len
        val ny = dy / len
        for (i <- 0 until 5) {
          val t = ((animationTick * 0.1 + i.toDouble / 5 + projectile.id * 0.13) % 1.0)
          val wave = Math.sin(phase * 2.8 + i * 2.1) * 6.0
          val px = tailX + dx * t + (-ny) * wave
          val py = tailY + dy * t + nx * wave
          val pAlpha = Math.max(0.0, Math.min(1.0, 0.55 * (1.0 - Math.abs(t - 0.5) * 2.0)))
          val pSize = 2.2 + Math.sin(phase + i) * 0.8
          gc.setFill(Color.color(0.6, 0.4, 1.0, pAlpha))
          gc.fillOval(px - pSize, py - pSize, pSize * 2, pSize * 2)
        }
      }

      // Arcane orb at tip
      val orbR = 6.0 * pulse
      gc.setFill(Color.color(0.4, 0.1, 0.8, 0.2 * pulse))
      gc.fillOval(tipX - orbR * 2, tipY - orbR * 2, orbR * 4, orbR * 4)
      gc.setFill(Color.color(0.5, 0.3, 0.9, 0.5 * pulse))
      gc.fillOval(tipX - orbR, tipY - orbR, orbR * 2, orbR * 2)
      gc.setFill(Color.color(0.7, 0.5, 1.0, 0.85 * fastFlicker))
      gc.fillOval(tipX - orbR * 0.4, tipY - orbR * 0.4, orbR * 0.8, orbR * 0.8)
    }
  }

  private def drawFireballProjectile(projectile: Projectile, camOffX: Double, camOffY: Double): Unit = {
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

      gc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND)
      val phase = (animationTick + projectile.id * 31) * 0.3
      val pulse = 0.8 + 0.2 * Math.sin(phase)
      val fastFlicker = 0.85 + 0.15 * Math.sin(phase * 2.5)

      // Orange/red fiery glow layers
      gc.setStroke(Color.color(1.0, 0.3, 0.0, 0.1 * pulse))
      gc.setLineWidth(44 * pulse)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      gc.setStroke(Color.color(1.0, 0.4, 0.05, 0.2 * pulse))
      gc.setLineWidth(28 * pulse)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      gc.setStroke(Color.color(1.0, 0.6, 0.1, 0.45 * fastFlicker))
      gc.setLineWidth(14 * fastFlicker)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      gc.setStroke(Color.color(1.0, 0.9, 0.4, 0.85 * fastFlicker))
      gc.setLineWidth(5.0)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      // Flame particles along trail
      val dx = tipX - tailX
      val dy = tipY - tailY
      val len = Math.sqrt(dx * dx + dy * dy)
      if (len > 1) {
        val nx = dx / len
        val ny = dy / len
        for (i <- 0 until 7) {
          val t = ((animationTick * 0.08 + i.toDouble / 7 + projectile.id * 0.11) % 1.0)
          val wave = Math.sin(phase * 2.0 + i * 1.7) * 8.0
          val px = tailX + dx * t + (-ny) * wave
          val py = tailY + dy * t + nx * wave
          val pAlpha = Math.max(0.0, Math.min(1.0, 0.6 * (1.0 - Math.abs(t - 0.5) * 2.0)))
          val pSize = 3.0 + Math.sin(phase + i * 0.9) * 1.2
          val r = 1.0
          val g = 0.4 + 0.4 * t
          gc.setFill(Color.color(r, g, 0.1, pAlpha))
          gc.fillOval(px - pSize, py - pSize, pSize * 2, pSize * 2)
        }
      }

      // Fireball orb at tip (large and fiery)
      val orbR = 10.0 * pulse
      gc.setFill(Color.color(1.0, 0.2, 0.0, 0.2 * pulse))
      gc.fillOval(tipX - orbR * 2.5, tipY - orbR * 2.5, orbR * 5, orbR * 5)
      gc.setFill(Color.color(1.0, 0.5, 0.0, 0.45 * pulse))
      gc.fillOval(tipX - orbR * 1.3, tipY - orbR * 1.3, orbR * 2.6, orbR * 2.6)
      gc.setFill(Color.color(1.0, 0.85, 0.3, 0.85 * fastFlicker))
      gc.fillOval(tipX - orbR * 0.5, tipY - orbR * 0.5, orbR, orbR)
    }
  }

  private def drawBulletProjectile(projectile: Projectile, camOffX: Double, camOffY: Double): Unit = {
    val projX = projectile.getX.toDouble
    val projY = projectile.getY.toDouble
    val beamLength = 6.0

    val tailX = worldToScreenX(projX, projY, camOffX)
    val tailY = worldToScreenY(projX, projY, camOffY)
    val tipWX = projX + projectile.dx * beamLength
    val tipWY = projY + projectile.dy * beamLength
    val tipX = worldToScreenX(tipWX, tipWY, camOffX)
    val tipY = worldToScreenY(tipWX, tipWY, camOffY)

    val margin = 100.0
    if (Math.max(tailX, tipX) > -margin && Math.min(tailX, tipX) < getWidth + margin &&
        Math.max(tailY, tipY) > -margin && Math.min(tailY, tipY) < getHeight + margin) {

      gc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND)
      val phase = (animationTick + projectile.id * 31) * 0.5
      val flicker = 0.9 + 0.1 * Math.sin(phase * 5.0)

      // Outer glow — yellow/orange
      gc.setStroke(Color.color(1.0, 0.85, 0.2, 0.12 * flicker))
      gc.setLineWidth(20 * flicker)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      // Mid glow
      gc.setStroke(Color.color(1.0, 0.75, 0.1, 0.3 * flicker))
      gc.setLineWidth(10 * flicker)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      // Bright core — white-hot
      gc.setStroke(Color.color(1.0, 0.95, 0.7, 0.9 * flicker))
      gc.setLineWidth(3.0)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      // Tip spark
      val orbR = 4.0 * flicker
      gc.setFill(Color.color(1.0, 0.9, 0.3, 0.7 * flicker))
      gc.fillOval(tipX - orbR, tipY - orbR, orbR * 2, orbR * 2)
    }
  }

  private def drawGrenadeProjectile(projectile: Projectile, camOffX: Double, camOffY: Double): Unit = {
    val projX = projectile.getX.toDouble
    val projY = projectile.getY.toDouble

    val sx = worldToScreenX(projX, projY, camOffX)
    val sy = worldToScreenY(projX, projY, camOffY)

    val margin = 100.0
    if (sx > -margin && sx < getWidth + margin && sy > -margin && sy < getHeight + margin) {
      val phase = (animationTick + projectile.id * 17) * 0.4
      val bounce = Math.abs(Math.sin(phase * 2.0)) * 4.0 // bobbing arc

      // Dark green orb
      val orbR = 6.0
      gc.setFill(Color.color(0.15, 0.35, 0.1, 0.3))
      gc.fillOval(sx - orbR * 2, sy - bounce - orbR * 2, orbR * 4, orbR * 4)
      gc.setFill(Color.color(0.2, 0.45, 0.15, 0.8))
      gc.fillOval(sx - orbR, sy - bounce - orbR, orbR * 2, orbR * 2)
      gc.setStroke(Color.color(0.1, 0.25, 0.05, 0.9))
      gc.setLineWidth(1.5)
      gc.strokeOval(sx - orbR, sy - bounce - orbR, orbR * 2, orbR * 2)

      // Highlight
      gc.setFill(Color.color(0.4, 0.7, 0.3, 0.5))
      gc.fillOval(sx - 2, sy - bounce - orbR + 1, 4, 3)

      // Trailing sparks
      for (i <- 0 until 4) {
        val t = ((animationTick * 0.12 + i * 0.25 + projectile.id * 0.1) % 1.0)
        val sparkX = sx - projectile.dx * t * 15.0
        val sparkY = sy - projectile.dy * t * 15.0 + t * 3.0
        val pAlpha = Math.max(0.0, 0.6 * (1.0 - t))
        val pSize = 2.0 * (1.0 - t)
        gc.setFill(Color.color(1.0, 0.7, 0.2, pAlpha))
        gc.fillOval(sparkX - pSize, sparkY - pSize, pSize * 2, pSize * 2)
      }
    }
  }

  private def drawTalonProjectile(projectile: Projectile, camOffX: Double, camOffY: Double): Unit = {
    val projX = projectile.getX.toDouble
    val projY = projectile.getY.toDouble
    val beamLength = 2.5

    val tailX = worldToScreenX(projX, projY, camOffX)
    val tailY = worldToScreenY(projX, projY, camOffY)
    val tipWX = projX + projectile.dx * beamLength
    val tipWY = projY + projectile.dy * beamLength
    val tipX = worldToScreenX(tipWX, tipWY, camOffX)
    val tipY = worldToScreenY(tipWX, tipWY, camOffY)

    val margin = 100.0
    if (Math.max(tailX, tipX) > -margin && Math.min(tailX, tipX) < getWidth + margin &&
        Math.max(tailY, tipY) > -margin && Math.min(tailY, tipY) < getHeight + margin) {

      gc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND)
      val phase = (animationTick + projectile.id * 31) * 0.4
      val pulse = 0.85 + 0.15 * Math.sin(phase)

      // Golden slash arc — short wide sweep
      val midX = (tailX + tipX) / 2
      val midY = (tailY + tipY) / 2
      val dx = tipX - tailX
      val dy = tipY - tailY
      val len = Math.sqrt(dx * dx + dy * dy)
      if (len > 1) {
        val nx = -dy / len
        val ny = dx / len

        // Wide outer glow
        gc.setStroke(Color.color(0.85, 0.65, 0.1, 0.12 * pulse))
        gc.setLineWidth(36 * pulse)
        gc.strokeLine(tailX, tailY, tipX, tipY)

        // Golden arc stroke
        gc.setStroke(Color.color(0.9, 0.7, 0.15, 0.35 * pulse))
        gc.setLineWidth(18 * pulse)
        gc.strokeLine(tailX, tailY, tipX, tipY)

        // Bright inner
        gc.setStroke(Color.color(1.0, 0.85, 0.3, 0.8 * pulse))
        gc.setLineWidth(6.0)
        gc.strokeLine(tailX, tailY, tipX, tipY)

        // White-hot core
        gc.setStroke(Color.color(1.0, 0.95, 0.7, 0.95))
        gc.setLineWidth(2.0)
        gc.strokeLine(tailX, tailY, tipX, tipY)

        // Claw slash particles along the arc
        for (i <- 0 until 4) {
          val t = ((animationTick * 0.1 + i.toDouble / 4) % 1.0)
          val spread = Math.sin(phase * 2.0 + i * 1.5) * 8.0
          val px = tailX + dx * t + nx * spread
          val py = tailY + dy * t + ny * spread
          val pAlpha = Math.max(0.0, Math.min(1.0, 0.7 * (1.0 - Math.abs(t - 0.5) * 2.0)))
          val pSize = 2.5 + Math.sin(phase + i) * 1.0
          gc.setFill(Color.color(1.0, 0.85, 0.3, pAlpha))
          gc.fillOval(px - pSize, py - pSize, pSize * 2, pSize * 2)
        }
      }
    }
  }

  private def drawGustProjectile(projectile: Projectile, camOffX: Double, camOffY: Double): Unit = {
    val projX = projectile.getX.toDouble
    val projY = projectile.getY.toDouble
    val beamLength = 4.0

    val tailX = worldToScreenX(projX, projY, camOffX)
    val tailY = worldToScreenY(projX, projY, camOffY)
    val tipWX = projX + projectile.dx * beamLength
    val tipWY = projY + projectile.dy * beamLength
    val tipX = worldToScreenX(tipWX, tipWY, camOffX)
    val tipY = worldToScreenY(tipWX, tipWY, camOffY)

    val margin = 100.0
    if (Math.max(tailX, tipX) > -margin && Math.min(tailX, tipX) < getWidth + margin &&
        Math.max(tailY, tipY) > -margin && Math.min(tailY, tipY) < getHeight + margin) {

      gc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND)
      val phase = (animationTick + projectile.id * 29) * 0.35
      val pulse = 0.8 + 0.2 * Math.sin(phase)
      val fastFlicker = 0.9 + 0.1 * Math.sin(phase * 3.3)

      // Amber wind cone — widens toward the tip
      gc.setStroke(Color.color(0.85, 0.7, 0.3, 0.08 * pulse))
      gc.setLineWidth(40 * pulse)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      gc.setStroke(Color.color(0.9, 0.75, 0.35, 0.2 * pulse))
      gc.setLineWidth(24 * pulse)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      gc.setStroke(Color.color(0.95, 0.8, 0.4, 0.45 * fastFlicker))
      gc.setLineWidth(12 * fastFlicker)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      // Bright core
      gc.setStroke(Color.color(1.0, 0.9, 0.6, 0.8 * fastFlicker))
      gc.setLineWidth(4.0)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      // Wind streaks — flowing lines alongside the main beam
      val dx = tipX - tailX
      val dy = tipY - tailY
      val len = Math.sqrt(dx * dx + dy * dy)
      if (len > 1) {
        val nx = -dy / len
        val ny = dx / len
        for (strand <- 0 until 3) {
          val strandOff = (strand - 1) * 7.0
          for (i <- 0 until 5) {
            val t = ((animationTick * 0.08 + i.toDouble / 5 + strand * 0.2) % 1.0)
            val wave = Math.sin(phase * 2.5 + i * 1.8 + strand * 2.1) * 4.0
            val px = tailX + dx * t + nx * (wave + strandOff)
            val py = tailY + dy * t + ny * (wave + strandOff)
            val pAlpha = Math.max(0.0, Math.min(1.0, 0.5 * (1.0 - Math.abs(t - 0.5) * 2.0)))
            val pSize = 1.5 + Math.sin(phase + i + strand) * 0.6
            gc.setFill(Color.color(0.95, 0.85, 0.5, pAlpha))
            gc.fillOval(px - pSize, py - pSize, pSize * 2, pSize * 2)
          }
        }
      }
    }
  }

  private def drawSplashProjectile(projectile: Projectile, camOffX: Double, camOffY: Double): Unit = {
    val projX = projectile.getX.toDouble
    val projY = projectile.getY.toDouble
    val beamLength = 4.0

    val tailX = worldToScreenX(projX, projY, camOffX)
    val tailY = worldToScreenY(projX, projY, camOffY)
    val tipWX = projX + projectile.dx * beamLength
    val tipWY = projY + projectile.dy * beamLength
    val tipX = worldToScreenX(tipWX, tipWY, camOffX)
    val tipY = worldToScreenY(tipWX, tipWY, camOffY)

    val margin = 100.0
    if (Math.max(tailX, tipX) > -margin && Math.min(tailX, tipX) < getWidth + margin &&
        Math.max(tailY, tipY) > -margin && Math.min(tailY, tipY) < getHeight + margin) {

      gc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND)
      val phase = (animationTick + projectile.id * 31) * 0.25
      val pulse = 0.85 + 0.15 * Math.sin(phase)
      val fastFlicker = 0.9 + 0.1 * Math.sin(phase * 3.3)

      // Outer glow — deep blue
      gc.setStroke(Color.color(0.1, 0.3, 0.9, 0.08 * pulse))
      gc.setLineWidth(40 * pulse)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      // Mid glow — cyan
      gc.setStroke(Color.color(0.2, 0.6, 1.0, 0.18 * pulse))
      gc.setLineWidth(24 * pulse)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      // Bright beam — light blue
      gc.setStroke(Color.color(0.3, 0.7, 1.0, 0.55 * fastFlicker))
      gc.setLineWidth(12 * fastFlicker)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      // Core — white-cyan
      gc.setStroke(Color.color(0.7, 0.95, 1.0, 0.9 * fastFlicker))
      gc.setLineWidth(4.0)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      // Ripple ring at tip
      val dx = tipX - tailX
      val dy = tipY - tailY
      val len = Math.sqrt(dx * dx + dy * dy)
      if (len > 1) {
        val rippleSize = 8.0 + 4.0 * Math.sin(phase * 2.0)
        gc.setStroke(Color.color(0.4, 0.8, 1.0, 0.5 * pulse))
        gc.setLineWidth(2.0)
        gc.strokeOval(tipX - rippleSize, tipY - rippleSize, rippleSize * 2, rippleSize * 2)

        // Droplet particles
        val nx = dx / len
        val ny = dy / len
        for (i <- 0 until 4) {
          val t = ((animationTick * 0.08 + i.toDouble / 4 + projectile.id * 0.13) % 1.0)
          val px = tailX + dx * t + (-ny) * Math.sin(phase + i * 1.8) * 5.0
          val py = tailY + dy * t + nx * Math.sin(phase + i * 1.8) * 5.0
          val pAlpha = Math.max(0.0, Math.min(1.0, 0.6 * (1.0 - t)))
          val pSize = 2.5 + Math.sin(phase + i) * 1.0
          gc.setFill(Color.color(0.5, 0.85, 1.0, pAlpha))
          gc.fillOval(px - pSize, py - pSize, pSize * 2, pSize * 2)
        }
      }
    }
  }

  private def drawTidalWaveProjectile(projectile: Projectile, camOffX: Double, camOffY: Double): Unit = {
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

      gc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND)
      val phase = (animationTick + projectile.id * 43) * 0.3
      val pulse = 0.85 + 0.15 * Math.sin(phase)

      // Outer glow — aqua
      gc.setStroke(Color.color(0.0, 0.7, 0.7, 0.10 * pulse))
      gc.setLineWidth(36 * pulse)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      // Mid glow — teal
      gc.setStroke(Color.color(0.1, 0.8, 0.8, 0.20 * pulse))
      gc.setLineWidth(22 * pulse)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      // Bright beam — light aqua
      gc.setStroke(Color.color(0.3, 0.9, 0.9, 0.5 * pulse))
      gc.setLineWidth(10 * pulse)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      // Core — white foam
      gc.setStroke(Color.color(0.85, 1.0, 1.0, 0.85 * pulse))
      gc.setLineWidth(3.5)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      // Wave crest shapes along beam
      val dx = tipX - tailX
      val dy = tipY - tailY
      val len = Math.sqrt(dx * dx + dy * dy)
      if (len > 1) {
        val nx = dx / len
        val ny = dy / len
        for (i <- 0 until 3) {
          val t = 0.25 + 0.25 * i
          val cx = tailX + dx * t
          val cy = tailY + dy * t
          val waveSize = 6.0 + 3.0 * Math.sin(phase + i * 2.1)
          gc.setStroke(Color.color(0.6, 1.0, 1.0, 0.4 * pulse))
          gc.setLineWidth(1.5)
          gc.strokeArc(cx - waveSize, cy - waveSize, waveSize * 2, waveSize * 2,
            0, 180, javafx.scene.shape.ArcType.OPEN)
        }
      }
    }
  }

  private def drawGeyserProjectile(projectile: Projectile, camOffX: Double, camOffY: Double): Unit = {
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

      gc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND)
      val phase = (animationTick + projectile.id * 29) * 0.35
      val pulse = 0.8 + 0.2 * Math.sin(phase)
      val fastFlicker = 0.85 + 0.15 * Math.sin(phase * 4.0)

      // Outer glow — bright cyan
      gc.setStroke(Color.color(0.0, 0.9, 1.0, 0.10 * pulse))
      gc.setLineWidth(50 * pulse)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      // Mid glow
      gc.setStroke(Color.color(0.1, 0.85, 1.0, 0.22 * pulse))
      gc.setLineWidth(30 * pulse)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      // Bright column
      gc.setStroke(Color.color(0.3, 0.95, 1.0, 0.6 * fastFlicker))
      gc.setLineWidth(16 * fastFlicker)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      // Core — white hot
      gc.setStroke(Color.color(0.9, 1.0, 1.0, 0.95 * fastFlicker))
      gc.setLineWidth(6.0)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      // Pulsing rings around the projectile center
      val centerX = (tailX + tipX) / 2
      val centerY = (tailY + tipY) / 2
      for (i <- 0 until 3) {
        val ringPhase = phase + i * 2.0
        val ringSize = 10.0 + 8.0 * Math.sin(ringPhase)
        val ringAlpha = Math.max(0.0, 0.4 * (1.0 - Math.abs(Math.sin(ringPhase * 0.5))))
        gc.setStroke(Color.color(0.2, 0.9, 1.0, ringAlpha))
        gc.setLineWidth(2.0)
        gc.strokeOval(centerX - ringSize, centerY - ringSize, ringSize * 2, ringSize * 2)
      }

      // Steam particles rising upward
      for (i <- 0 until 5) {
        val t = ((animationTick * 0.06 + i.toDouble / 5 + projectile.id * 0.17) % 1.0)
        val px = centerX + Math.sin(phase + i * 1.3) * 8.0
        val py = centerY - t * 20.0 // rising
        val pAlpha = Math.max(0.0, Math.min(1.0, 0.5 * (1.0 - t)))
        val pSize = 2.0 + t * 3.0
        gc.setFill(Color.color(0.7, 0.95, 1.0, pAlpha))
        gc.fillOval(px - pSize, py - pSize, pSize * 2, pSize * 2)
      }
    }
  }

  private def drawRocketProjectile(projectile: Projectile, camOffX: Double, camOffY: Double): Unit = {
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

      gc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND)
      val phase = (animationTick + projectile.id * 29) * 0.35
      val pulse = 0.85 + 0.15 * Math.sin(phase)

      // Fire trail behind rocket
      val dx = tipX - tailX
      val dy = tipY - tailY
      val len = Math.sqrt(dx * dx + dy * dy)
      if (len > 1) {
        val nx = dx / len
        val ny = dy / len
        // Smoke trail
        for (i <- 0 until 5) {
          val t = ((animationTick * 0.08 + i * 0.2 + projectile.id * 0.07) % 1.0)
          val smokeX = tailX - dx * t * 0.5 + (-ny) * Math.sin(phase + i * 1.7) * 3.0
          val smokeY = tailY - dy * t * 0.5 + nx * Math.sin(phase + i * 1.7) * 3.0
          val pAlpha = Math.max(0.0, 0.3 * (1.0 - t))
          val pSize = 3.0 + t * 4.0
          gc.setFill(Color.color(0.5, 0.5, 0.5, pAlpha))
          gc.fillOval(smokeX - pSize, smokeY - pSize, pSize * 2, pSize * 2)
        }

        // Fire particles
        for (i <- 0 until 4) {
          val t = ((animationTick * 0.1 + i * 0.25 + projectile.id * 0.13) % 1.0)
          val fireX = tailX - dx * t * 0.3
          val fireY = tailY - dy * t * 0.3
          val pAlpha = Math.max(0.0, 0.7 * (1.0 - t))
          val pSize = 2.5 * (1.0 - t)
          gc.setFill(Color.color(1.0, 0.5 + 0.3 * (1.0 - t), 0.1, pAlpha))
          gc.fillOval(fireX - pSize, fireY - pSize, pSize * 2, pSize * 2)
        }
      }

      // Rocket body — red/orange
      gc.setStroke(Color.color(0.8, 0.2, 0.1, 0.15 * pulse))
      gc.setLineWidth(24 * pulse)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      gc.setStroke(Color.color(0.9, 0.3, 0.1, 0.4 * pulse))
      gc.setLineWidth(12 * pulse)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      gc.setStroke(Color.color(1.0, 0.6, 0.2, 0.8 * pulse))
      gc.setLineWidth(5.0)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      // Bright tip
      val orbR = 6.0 * pulse
      gc.setFill(Color.color(1.0, 0.4, 0.1, 0.3 * pulse))
      gc.fillOval(tipX - orbR * 2, tipY - orbR * 2, orbR * 4, orbR * 4)
      gc.setFill(Color.color(1.0, 0.7, 0.2, 0.7 * pulse))
      gc.fillOval(tipX - orbR, tipY - orbR, orbR * 2, orbR * 2)
    }
  }

  private val EXPLOSION_ANIMATION_MS = 800

  private def drawExplosionAnimations(camOffX: Double, camOffY: Double): Unit = {
    val now = System.currentTimeMillis()
    val iter = client.getExplosionAnimations.entrySet().iterator()
    while (iter.hasNext) {
      val entry = iter.next()
      val data = entry.getValue
      val startTime = data(0)
      if (now - startTime > EXPLOSION_ANIMATION_MS) {
        iter.remove()
      } else {
        val wx = data(1).toDouble / 1000.0
        val wy = data(2).toDouble / 1000.0
        val pType = data(3).toByte
        val sx = worldToScreenX(wx, wy, camOffX)
        val sy = worldToScreenY(wx, wy, camOffY)
        drawExplosionEffect(sx, sy, startTime, pType)
      }
    }
  }

  private def drawExplosionEffect(screenX: Double, screenY: Double, startTime: Long, projectileType: Byte): Unit = {
    val elapsed = System.currentTimeMillis() - startTime
    if (elapsed < 0 || elapsed > EXPLOSION_ANIMATION_MS) return

    val progress = elapsed.toDouble / EXPLOSION_ANIMATION_MS
    val fadeOut = 1.0 - progress

    val blastRadius = if (projectileType == ProjectileType.GRENADE) 3.0 else 2.5
    val maxScreenRadius = blastRadius * HW * 1.5

    // Phase 1: Bright white flash (0-20%)
    if (progress < 0.2) {
      val flashPct = progress / 0.2
      val flashR = maxScreenRadius * 0.6 * flashPct
      val flashAlpha = 0.7 * (1.0 - flashPct)
      gc.setFill(Color.color(1.0, 1.0, 0.9, flashAlpha))
      gc.fillOval(screenX - flashR, screenY - flashR * 0.5, flashR * 2, flashR)
    }

    // Phase 2: Expanding fireball
    val fireR = maxScreenRadius * Math.min(1.0, progress * 2.0)

    // Outer glow — orange
    gc.setFill(Color.color(1.0, 0.5, 0.1, 0.15 * fadeOut))
    gc.fillOval(screenX - fireR * 1.3, screenY - fireR * 0.65, fireR * 2.6, fireR * 1.3)

    // Main fireball — orange/red
    gc.setFill(Color.color(1.0, 0.4, 0.05, 0.35 * fadeOut))
    gc.fillOval(screenX - fireR, screenY - fireR * 0.5, fireR * 2, fireR)

    // Hot core — yellow
    val coreR = fireR * 0.5 * fadeOut
    gc.setFill(Color.color(1.0, 0.8, 0.2, 0.5 * fadeOut))
    gc.fillOval(screenX - coreR, screenY - coreR * 0.5, coreR * 2, coreR)

    // Shockwave ring
    val ringR = maxScreenRadius * progress * 1.2
    gc.setStroke(Color.color(1.0, 0.6, 0.2, 0.4 * fadeOut))
    gc.setLineWidth(2.5 * fadeOut)
    gc.strokeOval(screenX - ringR, screenY - ringR * 0.5, ringR * 2, ringR)

    // Debris particles
    val particleCount = 8
    for (i <- 0 until particleCount) {
      val angle = i * (2 * Math.PI / particleCount) + i * 0.5
      val dist = progress * maxScreenRadius * (0.7 + (i % 3) * 0.2)
      val rise = progress * progress * 15.0
      val px = screenX + dist * Math.cos(angle)
      val py = screenY + dist * Math.sin(angle) * 0.5 - rise
      val pSize = (3.0 + (i % 3)) * fadeOut

      if (i % 2 == 0) {
        gc.setFill(Color.color(1.0, 0.7, 0.2, 0.6 * fadeOut))
      } else {
        gc.setFill(Color.color(0.5, 0.5, 0.5, 0.4 * fadeOut))
      }
      gc.fillOval(px - pSize, py - pSize, pSize * 2, pSize * 2)
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

    val playerIsPhased = player.isPhased

    // Draw effects behind the player
    if (!playerIsPhased) {
      if (player.hasShield) drawShieldBubble(screenX, spriteCenter)
      if (player.hasGemBoost) drawGemGlow(screenX, spriteCenter)
      drawChargingEffect(screenX, spriteCenter, player.getChargeLevel)
    }

    // Phased players render at 40% opacity with ghostly shimmer
    if (playerIsPhased) {
      drawPhasedEffect(screenX, spriteCenter)
      gc.setGlobalAlpha(0.4)
    }

    drawShadow(screenX, screenY)

    val sprite = SpriteGenerator.getSprite(player.getColorRGB, player.getDirection, frame, player.getCharacterId)
    val spriteX = screenX - displaySz / 2.0
    val spriteY = screenY - displaySz.toDouble
    gc.drawImage(sprite, spriteX, spriteY, displaySz, displaySz)

    if (playerIsPhased) gc.setGlobalAlpha(1.0)

    // Draw frozen effect
    if (player.isFrozen) drawFrozenEffect(screenX, spriteCenter)

    // Draw hit effect on top of sprite
    drawHitEffect(screenX, spriteCenter, client.getPlayerHitTime(playerId))

    // Draw health bar above player
    drawHealthBar(screenX, spriteY, player.getHealth, player.getMaxHealth)
  }

  private def drawLocalPlayer(wx: Double, wy: Double, camOffX: Double, camOffY: Double): Unit = {
    val screenX = worldToScreenX(wx, wy, camOffX)
    val screenY = worldToScreenY(wx, wy, camOffY)

    val displaySz = Constants.PLAYER_DISPLAY_SIZE_PX
    val spriteX = screenX - displaySz / 2.0
    val spriteY = screenY - displaySz.toDouble
    val spriteCenter = screenY - displaySz / 2.0

    val localIsPhased = client.isPhased

    // Draw effects behind the player
    if (!localIsPhased) {
      if (client.hasShield) drawShieldBubble(screenX, spriteCenter)
      if (client.hasGemBoost) drawGemGlow(screenX, spriteCenter)
      drawChargingEffect(screenX, spriteCenter, client.getChargeLevel)
    }

    // Phased players render at 40% opacity with ghostly shimmer
    if (localIsPhased) {
      drawPhasedEffect(screenX, spriteCenter)
      gc.setGlobalAlpha(0.4)
    }

    drawShadow(screenX, screenY)

    val animSpeed = if (client.isCharging) {
      val chargePct = client.getChargeLevel / 100.0
      (FRAMES_PER_STEP * (1.0 + chargePct * 4.0)).toInt
    } else FRAMES_PER_STEP
    val frame = if (client.getIsMoving) (animationTick / animSpeed) % 4 else 0
    val sprite = SpriteGenerator.getSprite(client.getLocalColorRGB, client.getLocalDirection, frame, client.selectedCharacterId)
    gc.drawImage(sprite, spriteX, spriteY, displaySz, displaySz)

    if (localIsPhased) gc.setGlobalAlpha(1.0)

    // Draw frozen effect
    if (client.isFrozen) drawFrozenEffect(screenX, spriteCenter)

    // Draw hit effect on top of sprite
    drawHitEffect(screenX, spriteCenter, client.getPlayerHitTime(client.getLocalPlayerId))

    // Draw health bar above local player
    drawHealthBar(screenX, spriteY, client.getLocalHealth, client.getSelectedCharacterMaxHealth)
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

  private def drawPhasedEffect(centerX: Double, centerY: Double): Unit = {
    val phase = animationTick * 0.12
    val displaySz = Constants.PLAYER_DISPLAY_SIZE_PX
    val radius = displaySz * 0.55

    // Ghostly teal aura — drawn at full alpha before the sprite's reduced alpha
    val pulse = 0.8 + 0.2 * Math.sin(phase * 1.5)
    gc.setFill(Color.color(0.1, 0.8, 0.6, 0.08 * pulse))
    gc.fillOval(centerX - radius * 1.4, centerY - radius * 0.7, radius * 2.8, radius * 1.4)

    gc.setStroke(Color.color(0.2, 0.9, 0.7, 0.35 * pulse))
    gc.setLineWidth(1.5)
    gc.strokeOval(centerX - radius, centerY - radius * 0.5, radius * 2, radius)

    // Floating wisp particles
    for (i <- 0 until 5) {
      val angle = phase * 0.6 + i * (2 * Math.PI / 5)
      val dist = radius * (0.7 + 0.2 * Math.sin(phase + i))
      val sx = centerX + dist * Math.cos(angle)
      val sy = centerY + dist * Math.sin(angle) * 0.5
      val sparkleAlpha = 0.3 + 0.3 * Math.sin(phase * 2.5 + i * 1.5)
      val sparkleSize = 2.0 + Math.sin(phase * 2 + i) * 1.0
      gc.setFill(Color.color(0.4, 1.0, 0.8, sparkleAlpha))
      gc.fillOval(sx - sparkleSize, sy - sparkleSize, sparkleSize * 2, sparkleSize * 2)
    }
  }

  private def drawFrozenEffect(centerX: Double, centerY: Double): Unit = {
    val phase = animationTick * 0.1
    val displaySz = Constants.PLAYER_DISPLAY_SIZE_PX

    // Icy blue overlay
    val radius = displaySz * 0.5
    gc.setFill(Color.color(0.4, 0.7, 1.0, 0.2 + 0.05 * Math.sin(phase * 2)))
    gc.fillOval(centerX - radius, centerY - radius, radius * 2, radius * 2)

    // Ice crystal border
    gc.setStroke(Color.color(0.5, 0.85, 1.0, 0.6))
    gc.setLineWidth(2.0)
    gc.strokeOval(centerX - radius, centerY - radius, radius * 2, radius * 2)

    // Crystal sparkles
    for (i <- 0 until 6) {
      val angle = phase * 0.5 + i * (Math.PI / 3)
      val dist = radius * 0.7
      val sx = centerX + dist * Math.cos(angle)
      val sy = centerY + dist * Math.sin(angle) * 0.5
      val sparkleAlpha = 0.5 + 0.4 * Math.sin(phase * 3 + i * 1.3)
      val sparkleSize = 2.0 + Math.sin(phase * 2 + i) * 1.0
      gc.setFill(Color.color(0.8, 0.95, 1.0, sparkleAlpha))
      gc.fillOval(sx - sparkleSize, sy - sparkleSize, sparkleSize * 2, sparkleSize * 2)
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
        val charId = if (data.length > 4) data(4).toByte else 0.toByte
        val sx = worldToScreenX(wx, wy, camOffX)
        val sy = worldToScreenY(wx, wy, camOffY)
        drawDeathEffect(sx, sy, colorRGB, deathTime, charId)
      }
    }
  }

  private def drawDeathEffect(screenX: Double, screenY: Double, colorRGB: Int, deathTime: Long, characterId: Byte = 0): Unit = {
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
    val sprite = SpriteGenerator.getSprite(colorRGB, Direction.Down, 0, characterId)
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

  private def drawHealthBar(screenCenterX: Double, spriteTopY: Double, health: Int, maxHealth: Int = Constants.MAX_HEALTH): Unit = {
    val barWidth = Constants.HEALTH_BAR_WIDTH_PX
    val barHeight = Constants.HEALTH_BAR_HEIGHT_PX
    val barX = screenCenterX - barWidth / 2.0
    val barY = spriteTopY - Constants.HEALTH_BAR_OFFSET_Y - barHeight

    // Dark red background (empty health)
    gc.setFill(Color.DARKRED)
    gc.fillRect(barX, barY, barWidth, barHeight)

    // Green fill (current health)
    val healthPercentage = health.toDouble / maxHealth
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
    gc.setFill(Color.rgb(0, 0, 0, 0.75))
    gc.fillRect(0, 0, getWidth, getHeight)

    val cx = getWidth / 2.0
    val cy = getHeight / 2.0

    gc.setFont(javafx.scene.text.Font.font("Arial", javafx.scene.text.FontWeight.BOLD, 52))
    gc.setStroke(Color.rgb(0, 0, 0, 0.9))
    gc.setLineWidth(5)
    gc.strokeText("GAME OVER", cx - 150, cy)
    gc.setFill(Color.rgb(230, 50, 50))
    gc.fillText("GAME OVER", cx - 150, cy)

    gc.setFont(javafx.scene.text.Font.font("Arial", javafx.scene.text.FontWeight.BOLD, 22))
    gc.setStroke(Color.rgb(0, 0, 0, 0.9))
    gc.setLineWidth(3)
    gc.strokeText("Press Enter to reload", cx - 115, cy + 45)
    gc.setFill(Color.WHITE)
    gc.fillText("Press Enter to reload", cx - 115, cy + 45)
  }

  private def drawRespawnCountdown(): Unit = {
    val deathTime = client.getLocalDeathTime
    val elapsed = System.currentTimeMillis() - deathTime
    val remaining = Math.max(0, Constants.RESPAWN_DELAY_MS - elapsed)
    val secondsLeft = Math.ceil(remaining / 1000.0).toInt

    val centerX = getWidth / 2.0
    val centerY = getHeight / 2.0

    // Dark backdrop behind the text
    gc.setFill(Color.rgb(0, 0, 0, 0.55))
    gc.fillRoundRect(centerX - 120, centerY - 50, 240, 85, 16, 16)

    // "YOU DIED" text
    gc.setFont(javafx.scene.text.Font.font("Arial", javafx.scene.text.FontWeight.BOLD, 32))
    gc.setStroke(Color.rgb(0, 0, 0, 0.9))
    gc.setLineWidth(4)
    gc.strokeText("YOU DIED", centerX - 78, centerY - 12)
    gc.setFill(Color.rgb(230, 50, 50))
    gc.fillText("YOU DIED", centerX - 78, centerY - 12)

    // Countdown text
    gc.setFont(javafx.scene.text.Font.font("Arial", javafx.scene.text.FontWeight.BOLD, 18))
    val countdownText = if (secondsLeft > 0) s"Respawning in ${secondsLeft}s..." else "Respawning..."
    gc.setStroke(Color.rgb(0, 0, 0, 0.9))
    gc.setLineWidth(3)
    gc.strokeText(countdownText, centerX - 82, centerY + 20)
    gc.setFill(Color.rgb(230, 230, 230))
    gc.fillText(countdownText, centerX - 82, centerY + 20)
  }

  private def drawCoordinates(world: WorldData): Unit = {
    val localPos = client.getLocalPosition
    val playerCount = client.getPlayers.size()
    val health = client.getLocalHealth

    gc.setFont(javafx.scene.text.Font.font("Arial", javafx.scene.text.FontWeight.BOLD, 13))

    val worldText = s"World: ${world.name}"
    val coordText = s"Position: (${localPos.getX}, ${localPos.getY})"
    val playersText = s"Players: ${playerCount + 1}"
    val healthText = s"Health: $health/${client.getSelectedCharacterMaxHealth}"
    val inventoryText = s"Items: ${client.getInventoryCount}"

    drawOutlinedText(worldText, 10, 22)
    drawOutlinedText(coordText, 10, 40)
    drawOutlinedText(playersText, 10, 58)
    drawOutlinedText(healthText, 10, 76)
    drawOutlinedText(inventoryText, 10, 94)

    // Show active effects
    val effects = Seq(
      if (client.hasShield) Some("Shield") else None,
      if (client.hasGemBoost) Some("FastShot") else None
    ).flatten
    if (effects.nonEmpty) {
      drawOutlinedText(s"Effects: ${effects.mkString(" ")}", 10, 112)
    }
  }

  private def drawAbilityHUD(): Unit = {
    val slotSize = 32.0
    val slotGap = 6.0
    // Match inventory layout to position ability slots to its left
    val invSlotSize = 44.0
    val invSlotGap = 8.0
    val invNumSlots = 5
    val totalInventoryWidth = invNumSlots * invSlotSize + (invNumSlots - 1) * invSlotGap
    val inventoryStartX = (getWidth - totalInventoryWidth) / 2.0
    val startY = getHeight - slotSize - 14.0

    // Position ability slots to the left of inventory
    val abilityGap = 12.0 // gap between abilities and inventory

    val charDef = client.getSelectedCharacterDef
    val abilities = Seq(
      (charDef.qAbility.keybind, client.getQCooldownFraction, client.getQCooldownRemaining, Color.color(0.2, 0.8, 0.3), Color.color(0.1, 0.5, 0.15)),
      (charDef.eAbility.keybind, client.getECooldownFraction, client.getECooldownRemaining, Color.color(0.4, 0.7, 1.0), Color.color(0.15, 0.35, 0.6))
    )

    for (i <- abilities.indices) {
      val (key, cooldownFrac, cooldownSec, readyColor, dimColor) = abilities(i)
      val slotX = inventoryStartX - (abilities.length - i) * (slotSize + slotGap) - abilityGap

      val onCooldown = cooldownFrac > 0.001f

      // Background
      gc.setFill(Color.rgb(0, 0, 0, 0.5))
      gc.fillRect(slotX, startY, slotSize, slotSize)

      // Ability icon (simple colored circle)
      val iconColor = if (onCooldown) dimColor else readyColor
      gc.setFill(iconColor)
      gc.fillOval(slotX + 6, startY + 4, slotSize - 12, slotSize - 12)

      // Cooldown sweep overlay
      if (onCooldown) {
        gc.setFill(Color.rgb(0, 0, 0, 0.6))
        val sweepHeight = slotSize * cooldownFrac
        gc.fillRect(slotX, startY, slotSize, sweepHeight)

        // Cooldown seconds text
        gc.setFont(javafx.scene.text.Font.font("Arial", javafx.scene.text.FontWeight.BOLD, 12))
        gc.setFill(Color.WHITE)
        val cdText = f"${cooldownSec}%.1f"
        gc.fillText(cdText, slotX + 4, startY + slotSize / 2.0 + 4)
      }

      // Border - bright when ready, dim when on cooldown
      val borderColor = if (onCooldown) Color.rgb(100, 100, 100, 0.6) else readyColor
      gc.setStroke(borderColor)
      gc.setLineWidth(if (onCooldown) 1.5 else 2.5)
      gc.strokeRect(slotX, startY, slotSize, slotSize)

      // Key label
      gc.setFont(javafx.scene.text.Font.font("Arial", javafx.scene.text.FontWeight.BOLD, 10))
      gc.setFill(Color.WHITE)
      gc.fillText(key, slotX + slotSize - 10, startY + slotSize - 4)
    }
  }

  private def drawInventory(): Unit = {
    val slotSize = 44.0
    val slotGap = 8.0
    val numSlots = 5
    val totalWidth = numSlots * slotSize + (numSlots - 1) * slotGap
    val startX = (getWidth - totalWidth) / 2.0
    val startY = getHeight - slotSize - 14.0

    // Item types in slot order: 1=Heart, 2=Star, 3=Gem, 4=Shield, 5=Fence
    val slotTypes = Seq(
      (ItemType.Heart, "1"),
      (ItemType.Star, "2"),
      (ItemType.Gem, "3"),
      (ItemType.Shield, "4"),
      (ItemType.Fence, "5")
    )

    for (i <- slotTypes.indices) {
      val (itemType, keyLabel) = slotTypes(i)
      val count = client.getItemCount(itemType.id)
      val slotX = startX + i * (slotSize + slotGap)
      val typeColor = intToColor(itemType.colorRGB)

      if (count > 0) {
        // Active slot: colored border glow
        val glowPulse = 0.6 + 0.15 * Math.sin(animationTick * 0.08 + i)
        gc.setFill(Color.color(
          Math.min(1.0, typeColor.getRed * 0.3),
          Math.min(1.0, typeColor.getGreen * 0.3),
          Math.min(1.0, typeColor.getBlue * 0.3),
          0.5
        ))
        gc.fillRoundRect(slotX - 2, startY - 2, slotSize + 4, slotSize + 4, 8, 8)
        gc.setFill(Color.rgb(15, 15, 25, 0.85))
        gc.fillRoundRect(slotX, startY, slotSize, slotSize, 6, 6)
        gc.setStroke(Color.color(typeColor.getRed, typeColor.getGreen, typeColor.getBlue, glowPulse))
        gc.setLineWidth(2)
        gc.strokeRoundRect(slotX, startY, slotSize, slotSize, 6, 6)
      } else {
        // Empty slot: dim
        gc.setFill(Color.rgb(15, 15, 25, 0.5))
        gc.fillRoundRect(slotX, startY, slotSize, slotSize, 6, 6)
        gc.setStroke(Color.rgb(60, 60, 70, 0.5))
        gc.setLineWidth(1)
        gc.strokeRoundRect(slotX, startY, slotSize, slotSize, 6, 6)
      }

      // Draw item icon (always visible, dimmed if empty)
      val iconCenterX = slotX + slotSize / 2.0
      val iconCenterY = startY + slotSize / 2.0 - 1
      val iconSize = 12.0

      if (count > 0) {
        gc.setFill(typeColor)
        gc.setStroke(Color.color(0, 0, 0, 0.5))
        gc.setLineWidth(0.8)
      } else {
        gc.setFill(Color.color(typeColor.getRed * 0.3, typeColor.getGreen * 0.3, typeColor.getBlue * 0.3, 0.35))
        gc.setStroke(Color.rgb(40, 40, 40, 0.2))
        gc.setLineWidth(0.5)
      }
      drawItemShape(itemType, iconCenterX, iconCenterY, iconSize)

      // Count badge (top-right)
      if (count > 0) {
        val countStr = count.toString
        val badgeW = Math.max(14.0, 7.0 + countStr.length * 6.0)
        val badgeH = 14.0
        val badgeX = slotX + slotSize - badgeW / 2 - 1
        val badgeY = startY - badgeH / 2 + 2
        gc.setFill(Color.rgb(200, 45, 45, 0.9))
        gc.fillRoundRect(badgeX, badgeY, badgeW, badgeH, badgeH, badgeH)
        gc.setFont(javafx.scene.text.Font.font("Arial", javafx.scene.text.FontWeight.BOLD, 10))
        gc.setFill(Color.WHITE)
        gc.fillText(countStr, badgeX + badgeW / 2 - countStr.length * 3, badgeY + 11)
      }

      // Key number label (bottom-center)
      gc.setFont(javafx.scene.text.Font.font("Arial", javafx.scene.text.FontWeight.BOLD, 10))
      gc.setFill(if (count > 0) Color.rgb(220, 220, 220) else Color.rgb(90, 90, 100))
      gc.fillText(keyLabel, slotX + slotSize / 2 - 3, startY + slotSize - 3)
    }
  }

  private def drawChargeBar(): Unit = {
    if (!client.isCharging) return
    if (client.getSelectedCharacterDef.primaryProjectileType != ProjectileType.NORMAL) return

    val chargeLevel = client.getChargeLevel
    val barWidth = 100.0
    val barHeight = 8.0
    val barX = (getWidth - barWidth) / 2.0
    val barY = getHeight - 80.0 // Above inventory

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

  private def drawLobbyHUD(): Unit = {
    if (client.clientState != ClientState.PLAYING) return

    // Timer: top-center
    val remaining = client.gameTimeRemaining
    val minutes = remaining / 60
    val seconds = remaining % 60
    val timerText = f"$minutes%d:$seconds%02d"
    gc.setFont(javafx.scene.text.Font.font("Arial", javafx.scene.text.FontWeight.BOLD, 26))
    drawOutlinedText(timerText, getWidth / 2 - 30, 30)

    // Kill/Death counter: below coordinates
    gc.setFont(javafx.scene.text.Font.font("Arial", javafx.scene.text.FontWeight.BOLD, 14))
    drawOutlinedText(s"K: ${client.killCount}  D: ${client.deathCount}", 10, 134)

    // Kill feed: top-right corner, last 5 entries
    val now = System.currentTimeMillis()
    gc.setFont(javafx.scene.text.Font.font("Arial", javafx.scene.text.FontWeight.BOLD, 13))
    import scala.jdk.CollectionConverters._
    var feedY = 20.0
    client.killFeed.asScala.foreach { entry =>
      val timestamp = entry(0).asInstanceOf[java.lang.Long].longValue()
      val elapsed = now - timestamp
      if (elapsed < 5000) {
        val alpha = Math.max(0.0, 1.0 - elapsed / 5000.0)
        val killer = entry(1).asInstanceOf[String]
        val victim = entry(2).asInstanceOf[String]
        val feedText = s"$killer killed $victim"
        gc.setGlobalAlpha(alpha)
        drawOutlinedText(feedText, getWidth - 200, feedY)
        gc.setGlobalAlpha(1.0)
        feedY += 16
      }
    }
  }

  private def drawOutlinedText(text: String, x: Double, y: Double): Unit = {
    gc.setStroke(Color.rgb(0, 0, 0, 0.9))
    gc.setLineWidth(3.5)
    gc.strokeText(text, x, y)

    gc.setFill(Color.WHITE)
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
