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

      // Eldritch outer aura — wide pulsing purple-green mist
      gc.setFill(Color.color(0.3, 0.15, 0.5, 0.06 * pulse))
      val auraR = len * 0.6
      val midX = (tailX + tipX) / 2
      val midY = (tailY + tipY) / 2
      gc.fillOval(midX - auraR, midY - auraR * 0.5, auraR * 2, auraR)

      // Draw 4 wavy tendril strands with varying thickness
      for (strand <- 0 until 4) {
        val strandOffset = (strand - 1.5) * 4.5
        val segments = 16
        val thickness = if (strand == 1 || strand == 2) 1.2 else 0.8
        val points = (0 to segments).map { seg =>
          val t = seg.toDouble / segments
          val waveFreq = 3.5 + strand * 0.7
          val waveAmp = (5.0 + strand * 2.5) * (1.0 - t * 0.3)
          val wave = Math.sin(phase * 2.0 + t * Math.PI * waveFreq + strand * 1.6) * waveAmp
          val secondaryWave = Math.sin(phase * 3.5 + t * Math.PI * 5 + strand * 0.9) * 2.0 * t
          val bx = tailX + dx * t + (-ny) * (strandOffset * 0.3 + wave + secondaryWave)
          val by = tailY + dy * t + nx * (strandOffset * 0.3 + wave + secondaryWave)
          (bx, by)
        }

        // Slimy outer glow — purple
        gc.setStroke(Color.color(0.4, 0.1, 0.6, 0.12 * pulse))
        gc.setLineWidth(10.0 * pulse * thickness)
        for (i <- 0 until points.length - 1) {
          gc.strokeLine(points(i)._1, points(i)._2, points(i + 1)._1, points(i + 1)._2)
        }

        // Main tendril — toxic green
        gc.setStroke(Color.color(0.15, 0.75, 0.25, 0.6 * pulse))
        gc.setLineWidth(5.0 * pulse * thickness)
        for (i <- 0 until points.length - 1) {
          gc.strokeLine(points(i)._1, points(i)._2, points(i + 1)._1, points(i + 1)._2)
        }

        // Bright slimy core
        gc.setStroke(Color.color(0.4, 1.0, 0.5, 0.75 * pulse))
        gc.setLineWidth(2.0 * thickness)
        for (i <- 0 until points.length - 1) {
          gc.strokeLine(points(i)._1, points(i)._2, points(i + 1)._1, points(i + 1)._2)
        }

        // Glowing suckers along each tendril
        val suckerCount = 3
        for (s <- 0 until suckerCount) {
          val t = 0.2 + s * 0.25
          val segIdx = (t * segments).toInt
          if (segIdx < points.length) {
            val sp = points(segIdx)
            val suckerPulse = 0.6 + 0.4 * Math.sin(phase * 3.0 + strand * 2.0 + s * 1.7)
            val suckerR = 3.0 * suckerPulse * thickness
            gc.setFill(Color.color(0.6, 0.2, 0.8, 0.5 * suckerPulse))
            gc.fillOval(sp._1 - suckerR, sp._2 - suckerR, suckerR * 2, suckerR * 2)
            gc.setFill(Color.color(0.3, 1.0, 0.5, 0.7 * suckerPulse))
            gc.fillOval(sp._1 - suckerR * 0.5, sp._2 - suckerR * 0.5, suckerR, suckerR)
          }
        }
      }

      // Dripping slime particles falling from tendrils
      for (i <- 0 until 6) {
        val t = ((animationTick * 0.04 + i.toDouble / 6 + projectile.id * 0.11) % 1.0)
        val dripX = tailX + dx * (0.2 + i * 0.12) + (-ny) * Math.sin(phase + i * 2.3) * 6.0
        val dripY = tailY + dy * (0.2 + i * 0.12) + nx * Math.sin(phase + i * 2.3) * 6.0 + t * 12.0
        val dripAlpha = Math.max(0.0, 0.5 * (1.0 - t))
        val dripSize = 2.0 + (1.0 - t) * 1.5
        gc.setFill(Color.color(0.2, 0.9, 0.3, dripAlpha))
        gc.fillOval(dripX - dripSize, dripY - dripSize, dripSize * 2, dripSize * 2)
      }

      // Suction vortex at tip — spinning particles pulling inward
      val vortexR = 12.0 * pulse
      gc.setFill(Color.color(0.4, 0.1, 0.6, 0.15 * pulse))
      gc.fillOval(tipX - vortexR * 1.5, tipY - vortexR * 1.5, vortexR * 3, vortexR * 3)

      for (v <- 0 until 6) {
        val angle = -phase * 2.5 + v * (Math.PI * 2 / 6)
        val dist = vortexR * (0.5 + 0.4 * Math.sin(phase * 2 + v))
        val vx = tipX + dist * Math.cos(angle)
        val vy = tipY + dist * Math.sin(angle) * 0.6
        val vAlpha = 0.4 + 0.3 * Math.sin(phase * 3 + v * 1.1)
        gc.setFill(Color.color(0.5, 1.0, 0.6, vAlpha))
        gc.fillOval(vx - 2, vy - 2, 4, 4)
      }

      // Central grasping orb at tip
      gc.setFill(Color.color(0.5, 0.2, 0.8, 0.3 * pulse))
      gc.fillOval(tipX - vortexR, tipY - vortexR, vortexR * 2, vortexR * 2)
      gc.setFill(Color.color(0.3, 0.9, 0.4, 0.55 * pulse))
      val innerR = vortexR * 0.6
      gc.fillOval(tipX - innerR, tipY - innerR, innerR * 2, innerR * 2)
      gc.setFill(Color.color(0.7, 1.0, 0.8, 0.9))
      val coreR = vortexR * 0.25
      gc.fillOval(tipX - coreR, tipY - coreR, coreR * 2, coreR * 2)
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

      val dx = tipX - tailX
      val dy = tipY - tailY
      val len = Math.sqrt(dx * dx + dy * dy)
      if (len < 1) return
      val nx = dx / len
      val ny = dy / len

      // Layer 1: Wide frosty mist aura
      gc.setStroke(Color.color(0.5, 0.8, 1.0, 0.06 * pulse))
      gc.setLineWidth(50 * pulse)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      // Layer 2: Icy outer glow
      gc.setStroke(Color.color(0.3, 0.6, 1.0, 0.1 * pulse))
      gc.setLineWidth(32 * pulse)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      // Layer 3: Bright crystalline beam
      gc.setStroke(Color.color(0.4, 0.75, 1.0, 0.4 * fastFlicker))
      gc.setLineWidth(14 * fastFlicker)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      // Layer 4: White-blue freezing core
      gc.setStroke(Color.color(0.85, 0.93, 1.0, 0.9 * fastFlicker))
      gc.setLineWidth(5.0)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      // Diamond-shaped ice crystals along the beam
      for (i <- 0 until 10) {
        val t = ((animationTick * 0.05 + i.toDouble / 10 + projectile.id * 0.13) % 1.0)
        val sparkle = Math.sin(phase * 2.5 + i * 1.4)
        val cx = tailX + dx * t + (-ny) * sparkle * 10.0
        val cy = tailY + dy * t + nx * sparkle * 10.0
        val cAlpha = Math.max(0.0, Math.min(1.0, 0.7 * (1.0 - Math.abs(t - 0.5) * 2.0)))
        val cSize = 3.0 + Math.sin(phase + i * 0.9) * 1.5
        val rot = phase * 2.0 + i * 1.1

        // Draw diamond/rhombus shape
        val dxArr = Array(cx, cx + cSize * Math.cos(rot), cx, cx - cSize * Math.cos(rot))
        val dyArr = Array(cy - cSize * Math.sin(rot + 0.5), cy, cy + cSize * Math.sin(rot + 0.5), cy)
        gc.setFill(Color.color(0.7, 0.9, 1.0, cAlpha))
        gc.fillPolygon(dxArr, dyArr, 4)
        gc.setStroke(Color.color(0.9, 0.97, 1.0, cAlpha * 0.8))
        gc.setLineWidth(1.0)
        gc.strokePolygon(dxArr, dyArr, 4)
      }

      // Frost trail particles drifting downward
      for (i <- 0 until 6) {
        val t = ((animationTick * 0.03 + i.toDouble / 6 + projectile.id * 0.19) % 1.0)
        val fx = tailX + dx * (0.1 + i * 0.15) + (-ny) * Math.sin(phase * 1.5 + i) * 7.0
        val fy = tailY + dy * (0.1 + i * 0.15) + nx * Math.sin(phase * 1.5 + i) * 7.0 + t * 8.0
        val fAlpha = Math.max(0.0, 0.4 * (1.0 - t))
        val fSize = 1.5 + (1.0 - t) * 2.0
        gc.setFill(Color.color(0.6, 0.85, 1.0, fAlpha))
        gc.fillOval(fx - fSize, fy - fSize, fSize * 2, fSize * 2)
      }

      // Crystalline snowflake burst at tip — 6-pointed star
      val starR = 10.0 * pulse
      gc.setStroke(Color.color(0.7, 0.9, 1.0, 0.6 * pulse))
      gc.setLineWidth(2.0)
      for (arm <- 0 until 6) {
        val angle = phase * 0.5 + arm * (Math.PI / 3)
        val sx = tipX + starR * Math.cos(angle)
        val sy = tipY + starR * Math.sin(angle) * 0.6
        gc.strokeLine(tipX, tipY, sx, sy)
        // Side branches on each arm
        val branchLen = starR * 0.4
        val branchT = 0.6
        val bx = tipX + starR * branchT * Math.cos(angle)
        val by = tipY + starR * branchT * Math.sin(angle) * 0.6
        val b1x = bx + branchLen * Math.cos(angle + 0.7)
        val b1y = by + branchLen * Math.sin(angle + 0.7) * 0.6
        val b2x = bx + branchLen * Math.cos(angle - 0.7)
        val b2y = by + branchLen * Math.sin(angle - 0.7) * 0.6
        gc.strokeLine(bx, by, b1x, b1y)
        gc.strokeLine(bx, by, b2x, b2y)
      }

      // Icy core at tip
      val orbR = 7.0 * pulse
      gc.setFill(Color.color(0.3, 0.6, 1.0, 0.25 * pulse))
      gc.fillOval(tipX - orbR * 2, tipY - orbR * 2, orbR * 4, orbR * 4)
      gc.setFill(Color.color(0.5, 0.8, 1.0, 0.45 * pulse))
      gc.fillOval(tipX - orbR, tipY - orbR, orbR * 2, orbR * 2)
      gc.setFill(Color.color(0.92, 0.97, 1.0, 0.9 * fastFlicker))
      gc.fillOval(tipX - orbR * 0.4, tipY - orbR * 0.4, orbR * 0.8, orbR * 0.8)
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

      // Ghost afterimage trail — 3 fading previous positions
      for (ghost <- 1 to 3) {
        val ghostPhase = spinAngle - ghost * 0.6
        val ghostAlpha = 0.15 * (1.0 - ghost * 0.3)
        val ghostSweep = Math.sin(ghostPhase) * 14.0
        val gPerpX = -ny * ghostSweep
        val gPerpY = nx * ghostSweep
        val ghostDist = ghost * 8.0

        val gTailX = tailX - nx * ghostDist + gPerpX * 0.2
        val gTailY = tailY - ny * ghostDist + gPerpY * 0.2
        val gTipX = tipX - nx * ghostDist + gPerpX
        val gTipY = tipY - ny * ghostDist + gPerpY

        // Ghost blade arc
        gc.setStroke(Color.color(0.8, 0.8, 0.85, ghostAlpha))
        gc.setLineWidth(18.0 * (1.0 - ghost * 0.2))
        gc.strokeLine(gTailX + (gTipX - gTailX) * 0.65, gTailY + (gTipY - gTailY) * 0.65, gTipX, gTipY)
      }

      val handleEndT = 0.65
      val handleEndX = tailX + dx * handleEndT + perpX * handleEndT
      val handleEndY = tailY + dy * handleEndT + perpY * handleEndT
      val bladeX = tipX + perpX
      val bladeY = tipY + perpY

      // --- Handle ---
      gc.setStroke(Color.color(0.45, 0.3, 0.15, 0.12 * pulse))
      gc.setLineWidth(12 * pulse)
      gc.strokeLine(tailX + perpX * 0.2, tailY + perpY * 0.2, handleEndX, handleEndY)
      gc.setStroke(Color.color(0.55, 0.35, 0.18, 0.7 * pulse))
      gc.setLineWidth(5 * pulse)
      gc.strokeLine(tailX + perpX * 0.2, tailY + perpY * 0.2, handleEndX, handleEndY)
      gc.setStroke(Color.color(0.7, 0.5, 0.25, 0.85 * pulse))
      gc.setLineWidth(2.0)
      gc.strokeLine(tailX + perpX * 0.2, tailY + perpY * 0.2, handleEndX, handleEndY)

      // --- Axe blade ---
      val bladeSpread = 22.0 * pulse
      val bladeNx = -ny
      val bladeNy = nx

      val bladeMidX = (handleEndX + bladeX) * 0.5
      val bladeMidY = (handleEndY + bladeY) * 0.5

      // Blade glow (wide)
      gc.setFill(Color.color(0.75, 0.75, 0.8, 0.1 * pulse))
      val glowR = bladeSpread * 2.0
      gc.fillOval(bladeMidX - glowR, bladeMidY - glowR, glowR * 2, glowR * 2)

      val bx = Array(
        handleEndX + bladeNx * 4.0,
        bladeMidX + bladeNx * bladeSpread,
        bladeX + bladeNx * 8.0,
        bladeX - bladeNx * 8.0,
        bladeMidX - bladeNx * bladeSpread,
        handleEndX - bladeNx * 4.0
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

      val inset = 0.75
      val ibx = bx.map(x => bladeMidX + (x - bladeMidX) * inset)
      val iby = by.map(y => bladeMidY + (y - bladeMidY) * inset)
      gc.setFill(Color.color(0.8, 0.8, 0.85, 0.65 * pulse))
      gc.fillPolygon(ibx, iby, 6)

      // Blade edge highlight
      gc.setStroke(Color.color(0.95, 0.95, 1.0, 0.7 * pulse))
      gc.setLineWidth(2.0)
      gc.strokePolygon(ibx, iby, 6)

      // Metallic gleam sweep — bright flash moving across the blade
      val gleamT = (animationTick * 0.06 + projectile.id * 0.3) % 1.0
      val gleamX = handleEndX + (bladeX - handleEndX) * gleamT
      val gleamY = handleEndY + (bladeY - handleEndY) * gleamT
      val gleamR = 6.0 + 3.0 * Math.sin(phase * 2)
      gc.setFill(Color.color(1.0, 1.0, 1.0, 0.6 * pulse))
      gc.fillOval(gleamX - gleamR, gleamY - gleamR, gleamR * 2, gleamR * 2)
      gc.setFill(Color.color(1.0, 1.0, 0.95, 0.3 * pulse))
      gc.fillOval(gleamX - gleamR * 2, gleamY - gleamR * 2, gleamR * 4, gleamR * 4)

      // Bright core line along the blade center
      gc.setStroke(Color.color(0.95, 0.95, 1.0, 0.5 * pulse))
      gc.setLineWidth(3.0)
      gc.strokeLine(handleEndX, handleEndY, bladeX, bladeY)

      // Spinning arc sparks — tiny bright sparks at blade edges
      for (spark <- 0 until 4) {
        val sparkT = ((animationTick * 0.12 + spark * 0.25) % 1.0)
        val sparkAngle = spinAngle + spark * Math.PI / 2
        val sparkDist = bladeSpread * 0.8
        val sx = bladeMidX + bladeNx * sparkDist * Math.sin(sparkAngle)
        val sy = bladeMidY + bladeNy * sparkDist * Math.sin(sparkAngle)
        val sparkAlpha = Math.max(0.0, 0.7 * (1.0 - sparkT))
        gc.setFill(Color.color(1.0, 0.95, 0.8, sparkAlpha))
        gc.fillOval(sx - 2, sy - 2, 4, 4)
      }
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

      // Braided rope — 2 interweaving strands creating cross-hatch pattern
      val segments = 14
      for (strand <- 0 until 2) {
        val strandPhase = if (strand == 0) 0.0 else Math.PI
        val points = (0 to segments).map { seg =>
          val t = seg.toDouble / segments
          val wave = Math.sin(strandPhase + phase * 1.2 + t * Math.PI * 4) * 4.0
          val bx = tailX + dx * t + (-ny) * wave
          val by = tailY + dy * t + nx * wave
          (bx, by)
        }

        // Outer shadow
        gc.setStroke(Color.color(0.35, 0.22, 0.1, 0.2 * pulse))
        gc.setLineWidth(7.0 * pulse)
        for (i <- 0 until points.length - 1) {
          gc.strokeLine(points(i)._1, points(i)._2, points(i + 1)._1, points(i + 1)._2)
        }

        // Main strand — alternating tan/dark brown for braid texture
        val strandColor = if (strand == 0) Color.color(0.75, 0.55, 0.3, 0.75 * pulse)
                          else Color.color(0.6, 0.4, 0.2, 0.75 * pulse)
        gc.setStroke(strandColor)
        gc.setLineWidth(4.0 * pulse)
        for (i <- 0 until points.length - 1) {
          gc.strokeLine(points(i)._1, points(i)._2, points(i + 1)._1, points(i + 1)._2)
        }

        // Highlight
        val highlightColor = if (strand == 0) Color.color(0.9, 0.75, 0.55, 0.6 * pulse)
                             else Color.color(0.8, 0.6, 0.4, 0.5 * pulse)
        gc.setStroke(highlightColor)
        gc.setLineWidth(1.5)
        for (i <- 0 until points.length - 1) {
          gc.strokeLine(points(i)._1, points(i)._2, points(i + 1)._1, points(i + 1)._2)
        }
      }

      // Knot bumps along the rope
      for (k <- 0 until 3) {
        val t = 0.2 + k * 0.25
        val kx = tailX + dx * t
        val ky = tailY + dy * t
        val kSize = 3.5 * pulse
        gc.setFill(Color.color(0.55, 0.38, 0.2, 0.5 * pulse))
        gc.fillOval(kx - kSize, ky - kSize, kSize * 2, kSize * 2)
        gc.setFill(Color.color(0.75, 0.6, 0.4, 0.4 * pulse))
        gc.fillOval(kx - kSize * 0.5, ky - kSize * 0.6, kSize, kSize * 0.8)
      }

      // Dust puff particles trailing behind
      for (i <- 0 until 4) {
        val t = ((animationTick * 0.05 + i * 0.25 + projectile.id * 0.13) % 1.0)
        val dustX = tailX - dx * 0.1 * t + (-ny) * Math.sin(phase + i * 1.5) * 3.0
        val dustY = tailY - dy * 0.1 * t + nx * Math.sin(phase + i * 1.5) * 3.0 + t * 4.0
        val dustAlpha = Math.max(0.0, 0.25 * (1.0 - t))
        val dustSize = 2.0 + t * 3.0
        gc.setFill(Color.color(0.7, 0.6, 0.5, dustAlpha))
        gc.fillOval(dustX - dustSize, dustY - dustSize, dustSize * 2, dustSize * 2)
      }

      // Metal grappling hook at tip — 3 prongs
      val hookLen = 10.0
      val prongSpread = 0.5
      gc.setStroke(Color.color(0.6, 0.6, 0.65, 0.85 * pulse))
      gc.setLineWidth(3.0)
      // Center prong
      gc.strokeLine(tipX, tipY, tipX + nx * hookLen, tipY + ny * hookLen)
      // Left prong
      val lAngle = Math.atan2(ny, nx) - prongSpread
      gc.strokeLine(tipX, tipY, tipX + Math.cos(lAngle) * hookLen * 0.8, tipY + Math.sin(lAngle) * hookLen * 0.8)
      // Right prong
      val rAngle = Math.atan2(ny, nx) + prongSpread
      gc.strokeLine(tipX, tipY, tipX + Math.cos(rAngle) * hookLen * 0.8, tipY + Math.sin(rAngle) * hookLen * 0.8)

      // Hook tips — curved barbs
      gc.setStroke(Color.color(0.8, 0.8, 0.85, 0.7 * pulse))
      gc.setLineWidth(2.0)
      val barbLen = 5.0
      val lTipX = tipX + Math.cos(lAngle) * hookLen * 0.8
      val lTipY = tipY + Math.sin(lAngle) * hookLen * 0.8
      gc.strokeLine(lTipX, lTipY, lTipX + Math.cos(lAngle + 1.2) * barbLen, lTipY + Math.sin(lAngle + 1.2) * barbLen)
      val rTipX = tipX + Math.cos(rAngle) * hookLen * 0.8
      val rTipY = tipY + Math.sin(rAngle) * hookLen * 0.8
      gc.strokeLine(rTipX, rTipY, rTipX + Math.cos(rAngle - 1.2) * barbLen, rTipY + Math.sin(rAngle - 1.2) * barbLen)

      // Metallic gleam on hook
      gc.setFill(Color.color(0.9, 0.9, 0.95, 0.5 * pulse))
      gc.fillOval(tipX - 3, tipY - 3, 6, 6)
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

      // Distance-based power intensity — spear deals more damage at range
      val distTraveled = Math.min(1.0, projectile.getDistanceTraveled / 20.0)
      val powerGlow = 0.3 + distTraveled * 0.7

      val dx = tipX - tailX
      val dy = tipY - tailY
      val len = Math.sqrt(dx * dx + dy * dy)
      if (len < 1) return
      val nx = dx / len
      val ny = dy / len

      // Layer 1: Power aura — grows with distance
      gc.setStroke(Color.color(0.9, 0.5, 0.1, 0.08 * pulse * powerGlow))
      gc.setLineWidth((40 + distTraveled * 20) * pulse)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      // Layer 2: Outer glow — intensifies with power
      gc.setStroke(Color.color(0.85, 0.55 + distTraveled * 0.15, 0.2, 0.12 * pulse * powerGlow))
      gc.setLineWidth(32 * pulse)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      // Layer 3: Mid glow
      gc.setStroke(Color.color(0.9, 0.65, 0.25, 0.25 * pulse * powerGlow))
      gc.setLineWidth(18 * pulse)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      // Layer 4: Bronze shaft
      gc.setStroke(Color.color(0.9, 0.7, 0.3, 0.6 * fastFlicker))
      gc.setLineWidth(10 * fastFlicker)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      // Layer 5: Bright gold core — white-hot when fully powered
      val coreR = Math.min(1.0, 0.85 + distTraveled * 0.15)
      val coreG = Math.min(1.0, 0.8 + distTraveled * 0.15)
      gc.setStroke(Color.color(coreR, coreG, 0.5 + distTraveled * 0.3, 0.9 * fastFlicker))
      gc.setLineWidth(4.5)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      // Air-cutting lines perpendicular to travel — speed slashes
      for (cut <- 0 until 3) {
        val cutT = 0.15 + cut * 0.25
        val cutX = tailX + dx * cutT
        val cutY = tailY + dy * cutT
        val cutLen = 10.0 + distTraveled * 6.0
        val cutAlpha = 0.3 * pulse * (1.0 - cutT * 0.5)
        gc.setStroke(Color.color(1.0, 0.9, 0.5, cutAlpha))
        gc.setLineWidth(1.5)
        gc.strokeLine(cutX + (-ny) * cutLen, cutY + nx * cutLen,
                      cutX - (-ny) * cutLen, cutY - nx * cutLen)
      }

      // Detailed arrowhead — diamond spearhead with ridgeline
      val arrowSize = 18.0 + distTraveled * 4.0
      val arrowX = Array(
        tipX + nx * arrowSize,
        tipX - nx * arrowSize * 0.3 + (-ny) * arrowSize * 0.5,
        tipX - nx * arrowSize * 0.6,
        tipX - nx * arrowSize * 0.3 + ny * arrowSize * 0.5
      )
      val arrowY = Array(
        tipY + ny * arrowSize,
        tipY - ny * arrowSize * 0.3 + nx * arrowSize * 0.5,
        tipY - ny * arrowSize * 0.6,
        tipY - ny * arrowSize * 0.3 + (-nx) * arrowSize * 0.5
      )

      // Spearhead glow
      gc.setFill(Color.color(1.0, 0.7, 0.2, 0.15 * pulse * powerGlow))
      val headGlow = arrowSize * 1.5
      gc.fillOval(tipX - headGlow * 0.5, tipY - headGlow * 0.5, headGlow, headGlow)

      gc.setFill(Color.color(0.9, 0.75, 0.35, 0.85 * pulse))
      gc.fillPolygon(arrowX, arrowY, 4)
      gc.setStroke(Color.color(1.0, 0.9, 0.6, 0.7 * pulse))
      gc.setLineWidth(1.5)
      gc.strokePolygon(arrowX, arrowY, 4)

      // Center ridge line on spearhead
      gc.setStroke(Color.color(1.0, 0.95, 0.7, 0.6 * pulse))
      gc.setLineWidth(1.5)
      gc.strokeLine(tipX - nx * arrowSize * 0.6, tipY - ny * arrowSize * 0.6,
                    tipX + nx * arrowSize, tipY + ny * arrowSize)

      // Power trail particles — more intense at higher distance
      val particleCount = 4 + (distTraveled * 4).toInt
      for (i <- 0 until particleCount) {
        val t = ((animationTick * 0.08 + i.toDouble / particleCount + projectile.id * 0.11) % 1.0)
        val px = tailX + dx * t + (-ny) * Math.sin(phase + i * 1.5) * (3.0 + distTraveled * 3.0)
        val py = tailY + dy * t + nx * Math.sin(phase + i * 1.5) * (3.0 + distTraveled * 3.0)
        val pAlpha = Math.max(0.0, Math.min(1.0, 0.5 * powerGlow * (1.0 - t)))
        val pSize = 2.5 + Math.sin(phase + i) * 1.0 + distTraveled * 1.5
        gc.setFill(Color.color(1.0, 0.85, 0.4, pAlpha))
        gc.fillOval(px - pSize, py - pSize, pSize * 2, pSize * 2)
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
      val fastFlicker = 0.8 + 0.2 * Math.sin(phase * 3.7)
      val phaseShift = 0.7 + 0.3 * Math.sin(phase * 1.3)

      val dx = tipX - tailX
      val dy = tipY - tailY
      val len = Math.sqrt(dx * dx + dy * dy)
      if (len < 1) return
      val nx = dx / len
      val ny = dy / len

      // Ethereal outer mist — wide ghostly aura
      gc.setStroke(Color.color(0.1, 0.7, 0.5, 0.05 * pulse * phaseShift))
      gc.setLineWidth(45 * pulse)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      // Ghostly green/teal glow layers — with phasing flicker
      gc.setStroke(Color.color(0.1, 0.8, 0.6, 0.1 * pulse * phaseShift))
      gc.setLineWidth(30 * pulse)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      gc.setStroke(Color.color(0.15, 0.85, 0.65, 0.35 * fastFlicker * phaseShift))
      gc.setLineWidth(14 * fastFlicker)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      gc.setStroke(Color.color(0.5, 1.0, 0.8, 0.85 * fastFlicker))
      gc.setLineWidth(4.0)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      // Curling smoke wisps trailing away from beam
      for (wisp <- 0 until 4) {
        val wispT = 0.2 + wisp * 0.2
        val wispBaseX = tailX + dx * wispT
        val wispBaseY = tailY + dy * wispT
        val segments = 6
        gc.setStroke(Color.color(0.2, 0.7, 0.5, 0.2 * pulse))
        gc.setLineWidth(2.0)
        var prevX = wispBaseX
        var prevY = wispBaseY
        for (s <- 1 to segments) {
          val st = s.toDouble / segments
          val curl = Math.sin(phase * 2.0 + wisp * 1.7 + st * Math.PI) * 8.0 * st
          val drift = st * 10.0
          val wx = wispBaseX + (-ny) * curl - nx * drift
          val wy = wispBaseY + nx * curl - ny * drift - st * 5.0
          val wAlpha = Math.max(0.0, 0.25 * (1.0 - st))
          gc.setStroke(Color.color(0.2, 0.8, 0.5, wAlpha))
          gc.setLineWidth(2.5 * (1.0 - st * 0.5))
          gc.strokeLine(prevX, prevY, wx, wy)
          prevX = wx
          prevY = wy
        }
      }

      // Ghostly face shapes that briefly form — hollow circles with eye dots
      for (face <- 0 until 2) {
        val facePhase = (phase * 0.5 + face * 3.14) % (2 * Math.PI)
        val faceAlpha = Math.max(0.0, Math.sin(facePhase) * 0.4)
        if (faceAlpha > 0.05) {
          val ft = 0.3 + face * 0.35
          val fx = tailX + dx * ft + (-ny) * Math.sin(phase + face) * 10.0
          val fy = tailY + dy * ft + nx * Math.sin(phase + face) * 10.0
          val fSize = 5.0 + Math.sin(phase + face * 2) * 2.0
          // Face outline
          gc.setStroke(Color.color(0.4, 1.0, 0.7, faceAlpha))
          gc.setLineWidth(1.0)
          gc.strokeOval(fx - fSize, fy - fSize, fSize * 2, fSize * 2)
          // Eyes — two small dots
          gc.setFill(Color.color(0.6, 1.0, 0.8, faceAlpha * 1.5))
          gc.fillOval(fx - fSize * 0.4 - 1, fy - fSize * 0.2 - 1, 2, 2)
          gc.fillOval(fx + fSize * 0.4 - 1, fy - fSize * 0.2 - 1, 2, 2)
          // Mouth — small open circle
          gc.strokeOval(fx - 1.5, fy + fSize * 0.3 - 1, 3, 2)
        }
      }

      // Spectral wisp particles with longer trails
      for (i <- 0 until 7) {
        val t = ((animationTick * 0.08 + i.toDouble / 7 + projectile.id * 0.15) % 1.0)
        val wave = Math.sin(phase * 2.5 + i * 1.9) * 9.0
        val px = tailX + dx * t + (-ny) * wave
        val py = tailY + dy * t + nx * wave
        val pAlpha = Math.max(0.0, Math.min(1.0, 0.55 * (1.0 - Math.abs(t - 0.5) * 2.0) * phaseShift))
        val pSize = 2.0 + Math.sin(phase + i) * 1.5
        gc.setFill(Color.color(0.4, 1.0, 0.75, pAlpha))
        gc.fillOval(px - pSize, py - pSize, pSize * 2, pSize * 2)
        // Particle trail
        val trailAlpha = pAlpha * 0.4
        gc.setFill(Color.color(0.3, 0.8, 0.6, trailAlpha))
        gc.fillOval(px - nx * 4 - pSize * 0.7, py - ny * 4 - pSize * 0.7, pSize * 1.4, pSize * 1.4)
      }

      // Ghostly orb at tip — pulsing with inner glow
      val orbR = 8.0 * pulse
      gc.setFill(Color.color(0.1, 0.7, 0.5, 0.15 * pulse))
      gc.fillOval(tipX - orbR * 2.5, tipY - orbR * 2.5, orbR * 5, orbR * 5)
      gc.setFill(Color.color(0.15, 0.85, 0.65, 0.35 * pulse))
      gc.fillOval(tipX - orbR * 1.3, tipY - orbR * 1.3, orbR * 2.6, orbR * 2.6)
      gc.setFill(Color.color(0.5, 1.0, 0.8, 0.8 * fastFlicker))
      gc.fillOval(tipX - orbR * 0.5, tipY - orbR * 0.5, orbR, orbR)
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

      val dx = tipX - tailX
      val dy = tipY - tailY
      val len = Math.sqrt(dx * dx + dy * dy)
      if (len < 1) return
      val nx = dx / len
      val ny = dy / len

      // Spectral purple glow — saturated purple, NOT dark
      gc.setStroke(Color.color(0.5, 0.2, 0.8, 0.07 * pulse))
      gc.setLineWidth(40 * pulse)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      gc.setStroke(Color.color(0.55, 0.25, 0.85, 0.15 * pulse))
      gc.setLineWidth(24 * pulse)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      gc.setStroke(Color.color(0.6, 0.3, 0.9, 0.4 * fastFlicker))
      gc.setLineWidth(12 * fastFlicker)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      // Bright violet core
      gc.setStroke(Color.color(0.75, 0.5, 0.95, 0.85 * fastFlicker))
      gc.setLineWidth(4.0)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      // Shadow wisp particles trailing behind
      for (trail <- 0 until 5) {
        val trailT = ((animationTick * 0.05 + trail * 0.2 + projectile.id * 0.07) % 1.0)
        val tpx = tailX - dx * 0.12 * trailT + (-ny) * Math.sin(phase * 1.5 + trail * 1.3) * 5.0
        val tpy = tailY - dy * 0.12 * trailT + nx * Math.sin(phase * 1.5 + trail * 1.3) * 5.0 - trailT * 5.0
        val trailAlpha = Math.max(0.0, 0.35 * (1.0 - trailT))
        val trailSize = 2.5 + trailT * 3.0
        gc.setFill(Color.color(0.45, 0.15, 0.65, trailAlpha))
        gc.fillOval(tpx - trailSize, tpy - trailSize, trailSize * 2, trailSize * 2)
      }

      // Shadowy tendrils along the beam
      for (strand <- 0 until 3) {
        val strandOff = (strand - 1) * 5.0
        for (i <- 0 until 8) {
          val t = ((animationTick * 0.06 + i.toDouble / 8 + strand * 0.25) % 1.0)
          val wave = Math.sin(phase * 1.8 + i * 1.7 + strand * 2.1) * 7.0
          val px = tailX + dx * t + (-ny) * (wave + strandOff)
          val py = tailY + dy * t + nx * (wave + strandOff)
          val pAlpha = Math.max(0.0, Math.min(1.0, 0.4 * (1.0 - Math.abs(t - 0.5) * 2.0)))
          val pSize = 2.5 + Math.sin(phase + i + strand) * 1.0
          gc.setFill(Color.color(0.55, 0.2, 0.8, pAlpha))
          gc.fillOval(px - pSize, py - pSize, pSize * 2, pSize * 2)
        }
      }

      // Soul-draining particles — converging toward the orb from surroundings
      for (s <- 0 until 6) {
        val sAngle = phase * 1.5 + s * (Math.PI * 2 / 6)
        val sDist = 18.0 * (1.0 - ((animationTick * 0.04 + s * 0.17) % 1.0))
        val sx = tipX + sDist * Math.cos(sAngle)
        val sy = tipY + sDist * Math.sin(sAngle) * 0.6
        val sAlpha = Math.max(0.0, 0.4 * (sDist / 18.0))
        val sSize = 1.5 + (1.0 - sDist / 18.0) * 2.0
        gc.setFill(Color.color(0.65, 0.35, 0.95, sAlpha))
        gc.fillOval(sx - sSize, sy - sSize, sSize * 2, sSize * 2)
      }

      // Skull shape at tip
      val skullR = 10.0 * pulse

      // Skull glow — purple, not dark
      gc.setFill(Color.color(0.5, 0.2, 0.75, 0.2 * pulse))
      gc.fillOval(tipX - skullR * 1.8, tipY - skullR * 1.8, skullR * 3.6, skullR * 3.6)

      // Cranium
      gc.setFill(Color.color(0.55, 0.3, 0.8, 0.5 * pulse))
      gc.fillOval(tipX - skullR, tipY - skullR * 1.1, skullR * 2, skullR * 2)

      // Jaw
      gc.setFill(Color.color(0.5, 0.25, 0.75, 0.45 * pulse))
      gc.fillOval(tipX - skullR * 0.7, tipY + skullR * 0.1, skullR * 1.4, skullR * 0.9)

      // Glowing eye sockets — bright magenta
      val eyeGlow = 0.6 + 0.4 * Math.sin(phase * 4)
      gc.setFill(Color.color(0.9, 0.4, 1.0, eyeGlow))
      gc.fillOval(tipX - skullR * 0.5 - 2.5, tipY - skullR * 0.2 - 2.5, 5, 5)
      gc.fillOval(tipX + skullR * 0.5 - 2.5, tipY - skullR * 0.2 - 2.5, 5, 5)
      // Eye glow halos
      gc.setFill(Color.color(0.95, 0.6, 1.0, eyeGlow * 0.3))
      gc.fillOval(tipX - skullR * 0.5 - 4, tipY - skullR * 0.2 - 4, 8, 8)
      gc.fillOval(tipX + skullR * 0.5 - 4, tipY - skullR * 0.2 - 4, 8, 8)

      // Bright core
      gc.setFill(Color.color(0.75, 0.45, 0.95, 0.85 * fastFlicker))
      gc.fillOval(tipX - skullR * 0.3, tipY - skullR * 0.3, skullR * 0.6, skullR * 0.6)
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

      val dx = tipX - tailX
      val dy = tipY - tailY
      val len = Math.sqrt(dx * dx + dy * dy)
      if (len < 1) return
      val nx = dx / len
      val ny = dy / len

      // Muzzle flash glow at the tail
      val muzzleFlash = Math.max(0.0, 0.4 * Math.sin(phase * 8.0))
      if (muzzleFlash > 0.05) {
        gc.setFill(Color.color(1.0, 0.9, 0.4, muzzleFlash * 0.3))
        gc.fillOval(tailX - 8, tailY - 8, 16, 16)
      }

      // Tracer outer glow — hot yellow
      gc.setStroke(Color.color(1.0, 0.85, 0.2, 0.1 * flicker))
      gc.setLineWidth(18 * flicker)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      // Tracer mid
      gc.setStroke(Color.color(1.0, 0.75, 0.1, 0.3 * flicker))
      gc.setLineWidth(8 * flicker)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      // White-hot tracer core — thin and sharp
      gc.setStroke(Color.color(1.0, 0.97, 0.8, 0.95 * flicker))
      gc.setLineWidth(2.5)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      // Speed lines — parallel streaks flanking the bullet
      for (streak <- 0 until 2) {
        val streakOff = (streak * 2 - 1) * 5.0
        val streakAlpha = 0.15 * flicker
        gc.setStroke(Color.color(1.0, 0.9, 0.5, streakAlpha))
        gc.setLineWidth(1.0)
        val s1x = tailX + (-ny) * streakOff
        val s1y = tailY + nx * streakOff
        val s2x = tailX + dx * 0.6 + (-ny) * streakOff
        val s2y = tailY + dy * 0.6 + nx * streakOff
        gc.strokeLine(s1x, s1y, s2x, s2y)
      }

      // Bullet tip — bright pointed spark
      val orbR = 4.5 * flicker
      gc.setFill(Color.color(1.0, 0.95, 0.5, 0.3 * flicker))
      gc.fillOval(tipX - orbR * 1.5, tipY - orbR * 1.5, orbR * 3, orbR * 3)
      gc.setFill(Color.color(1.0, 0.97, 0.7, 0.8 * flicker))
      gc.fillOval(tipX - orbR * 0.6, tipY - orbR * 0.6, orbR * 1.2, orbR * 1.2)

      // Shell casing sparks trailing behind
      for (i <- 0 until 3) {
        val t = ((animationTick * 0.15 + i * 0.33 + projectile.id * 0.11) % 1.0)
        val sparkX = tailX - dx * t * 0.15 + (-ny) * Math.sin(phase * 3 + i * 2.0) * 3.0
        val sparkY = tailY - dy * t * 0.15 + nx * Math.sin(phase * 3 + i * 2.0) * 3.0 + t * 3.0
        val sparkAlpha = Math.max(0.0, 0.5 * (1.0 - t))
        val sparkSize = 1.5 * (1.0 - t * 0.5)
        gc.setFill(Color.color(1.0, 0.8, 0.3, sparkAlpha))
        gc.fillOval(sparkX - sparkSize, sparkY - sparkSize, sparkSize * 2, sparkSize * 2)
      }
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
      val bounce = Math.abs(Math.sin(phase * 2.0)) * 4.0
      val spinAngle = animationTick * 0.3 + projectile.id * 1.7

      val grenY = sy - bounce
      val orbR = 7.0

      // Danger glow — pulsing red/orange warning aura
      val dangerPulse = 0.5 + 0.5 * Math.sin(phase * 3.0)
      gc.setFill(Color.color(1.0, 0.3, 0.0, 0.06 * dangerPulse))
      gc.fillOval(sx - orbR * 3, grenY - orbR * 3, orbR * 6, orbR * 6)
      gc.setFill(Color.color(1.0, 0.5, 0.1, 0.08 * dangerPulse))
      gc.fillOval(sx - orbR * 2, grenY - orbR * 2, orbR * 4, orbR * 4)

      // Dark olive body
      gc.setFill(Color.color(0.2, 0.4, 0.15, 0.85))
      gc.fillOval(sx - orbR, grenY - orbR, orbR * 2, orbR * 2)

      // Metal band around the middle (spinning)
      val bandAngle = spinAngle
      gc.setStroke(Color.color(0.5, 0.5, 0.45, 0.7))
      gc.setLineWidth(2.0)
      val bandW = orbR * 0.8
      gc.strokeLine(
        sx - bandW * Math.cos(bandAngle), grenY - bandW * Math.sin(bandAngle) * 0.5,
        sx + bandW * Math.cos(bandAngle), grenY + bandW * Math.sin(bandAngle) * 0.5
      )

      // Outline
      gc.setStroke(Color.color(0.12, 0.25, 0.08, 0.9))
      gc.setLineWidth(1.5)
      gc.strokeOval(sx - orbR, grenY - orbR, orbR * 2, orbR * 2)

      // Highlight sheen
      gc.setFill(Color.color(0.5, 0.7, 0.4, 0.4))
      gc.fillOval(sx - 3, grenY - orbR + 2, 5, 3)

      // Spoon/lever — small line on top
      gc.setStroke(Color.color(0.6, 0.6, 0.55, 0.75))
      gc.setLineWidth(1.5)
      gc.strokeLine(sx, grenY - orbR, sx + 4 * Math.cos(spinAngle * 0.5), grenY - orbR - 5)

      // Fuse sparks — bright orange sparks at the top
      for (i <- 0 until 5) {
        val t = ((animationTick * 0.15 + i * 0.2 + projectile.id * 0.1) % 1.0)
        val sparkAngle = phase * 4.0 + i * 1.3
        val sparkDist = t * 8.0
        val sparkX = sx + Math.cos(sparkAngle) * sparkDist * 0.5
        val sparkY = grenY - orbR - 3 - sparkDist + Math.sin(sparkAngle) * 2.0
        val sparkAlpha = Math.max(0.0, 0.8 * (1.0 - t))
        val sparkSize = 1.5 * (1.0 - t * 0.5)

        // Alternate hot orange and bright yellow
        if (i % 2 == 0) {
          gc.setFill(Color.color(1.0, 0.6, 0.1, sparkAlpha))
        } else {
          gc.setFill(Color.color(1.0, 0.9, 0.3, sparkAlpha))
        }
        gc.fillOval(sparkX - sparkSize, sparkY - sparkSize, sparkSize * 2, sparkSize * 2)
      }

      // Smoke trail behind grenade
      for (i <- 0 until 4) {
        val t = ((animationTick * 0.06 + i * 0.25 + projectile.id * 0.07) % 1.0)
        val smokeX = sx - projectile.dx * t * 18.0 + Math.sin(phase + i * 1.5) * 2.0
        val smokeY = sy - projectile.dy * t * 18.0 + t * 4.0
        val smokeAlpha = Math.max(0.0, 0.2 * (1.0 - t))
        val smokeSize = 2.5 + t * 4.0
        gc.setFill(Color.color(0.5, 0.5, 0.45, smokeAlpha))
        gc.fillOval(smokeX - smokeSize, smokeY - smokeSize, smokeSize * 2, smokeSize * 2)
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

      val midX = (tailX + tipX) / 2
      val midY = (tailY + tipY) / 2
      val dx = tipX - tailX
      val dy = tipY - tailY
      val len = Math.sqrt(dx * dx + dy * dy)
      if (len > 1) {
        val nx = -dy / len
        val ny = dx / len

        // Golden flash burst — wide impact glow
        gc.setFill(Color.color(1.0, 0.8, 0.2, 0.08 * pulse))
        val flashR = len * 0.8
        gc.fillOval(midX - flashR, midY - flashR * 0.5, flashR * 2, flashR)

        // Three parallel curved claw slash arcs
        for (claw <- 0 until 3) {
          val clawOffset = (claw - 1) * 8.0
          val clawSegments = 8
          val clawPoints = (0 to clawSegments).map { seg =>
            val t = seg.toDouble / clawSegments
            // Curved arc — bows outward in the middle
            val arcBow = Math.sin(t * Math.PI) * (12.0 + claw * 3.0)
            val cx = tailX + dx * t + nx * (clawOffset + arcBow)
            val cy = tailY + dy * t + ny * (clawOffset + arcBow)
            (cx, cy)
          }

          // Outer claw glow
          gc.setStroke(Color.color(0.85, 0.65, 0.1, 0.15 * pulse))
          gc.setLineWidth(10.0 * pulse)
          for (i <- 0 until clawPoints.length - 1) {
            gc.strokeLine(clawPoints(i)._1, clawPoints(i)._2, clawPoints(i + 1)._1, clawPoints(i + 1)._2)
          }

          // Golden claw mark
          gc.setStroke(Color.color(1.0, 0.8, 0.25, 0.7 * pulse))
          gc.setLineWidth(4.0 * pulse)
          for (i <- 0 until clawPoints.length - 1) {
            gc.strokeLine(clawPoints(i)._1, clawPoints(i)._2, clawPoints(i + 1)._1, clawPoints(i + 1)._2)
          }

          // White-hot cutting edge
          gc.setStroke(Color.color(1.0, 0.95, 0.7, 0.9 * pulse))
          gc.setLineWidth(1.5)
          for (i <- 0 until clawPoints.length - 1) {
            gc.strokeLine(clawPoints(i)._1, clawPoints(i)._2, clawPoints(i + 1)._1, clawPoints(i + 1)._2)
          }

          // Sharp talon point at the end of each claw
          val lastPt = clawPoints.last
          val prevPt = clawPoints(clawPoints.length - 2)
          val tipDx = lastPt._1 - prevPt._1
          val tipDy = lastPt._2 - prevPt._2
          val tipLen = Math.sqrt(tipDx * tipDx + tipDy * tipDy)
          if (tipLen > 0.5) {
            val tnx = tipDx / tipLen
            val tny = tipDy / tipLen
            gc.setStroke(Color.color(1.0, 0.9, 0.4, 0.85 * pulse))
            gc.setLineWidth(3.0)
            gc.strokeLine(lastPt._1, lastPt._2, lastPt._1 + tnx * 8, lastPt._2 + tny * 8)
          }
        }

        // Feather particles fluttering off the slash
        for (i <- 0 until 5) {
          val t = ((animationTick * 0.05 + i.toDouble / 5 + projectile.id * 0.13) % 1.0)
          val featherX = midX + nx * Math.sin(phase + i * 2.0) * 15.0 + dx * (t - 0.5) * 0.3
          val featherY = midY + ny * Math.sin(phase + i * 2.0) * 15.0 + dy * (t - 0.5) * 0.3 + t * 8.0
          val fAlpha = Math.max(0.0, 0.5 * (1.0 - t))
          val fRot = phase * 2.0 + i * 1.3
          val fLen = 5.0 * (1.0 - t * 0.5)
          val fWid = 2.0 * (1.0 - t * 0.5)
          // Draw feather as small rotated line
          gc.setStroke(Color.color(0.85, 0.7, 0.3, fAlpha))
          gc.setLineWidth(fWid)
          gc.strokeLine(
            featherX - Math.cos(fRot) * fLen, featherY - Math.sin(fRot) * fLen,
            featherX + Math.cos(fRot) * fLen, featherY + Math.sin(fRot) * fLen
          )
        }

        // Air slash speed lines — transparent streaks
        for (s <- 0 until 3) {
          val slashT = 0.2 + s * 0.3
          val sx = tailX + dx * slashT
          val sy = tailY + dy * slashT
          val slashLen = 18.0
          gc.setStroke(Color.color(1.0, 1.0, 0.9, 0.15 * pulse))
          gc.setLineWidth(1.0)
          gc.strokeLine(sx + nx * slashLen, sy + ny * slashLen,
                        sx - nx * slashLen, sy - ny * slashLen)
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

      val dx = tipX - tailX
      val dy = tipY - tailY
      val len = Math.sqrt(dx * dx + dy * dy)
      if (len < 1) return
      val nx = -dy / len
      val ny = dx / len
      val dirNx = dx / len
      val dirNy = dy / len

      // Expanding cone shape — narrow at tail, wide at tip
      for (seg <- 0 until 8) {
        val t = seg.toDouble / 8
        val t2 = (seg + 1).toDouble / 8
        val coneWidth = 4.0 + t * 32.0
        val coneWidth2 = 4.0 + t2 * 32.0
        val x1 = tailX + dx * t
        val y1 = tailY + dy * t
        val x2 = tailX + dx * t2
        val y2 = tailY + dy * t2
        val coneAlpha = 0.08 * pulse * (0.5 + t * 0.5)
        gc.setStroke(Color.color(0.85, 0.75, 0.35, coneAlpha))
        gc.setLineWidth((coneWidth + coneWidth2) / 2)
        gc.strokeLine(x1, y1, x2, y2)
      }

      // Spiral vortex lines — curving wind streaks that spiral
      for (spiral <- 0 until 4) {
        val spiralSegments = 12
        val spiralPhase = phase * 2.0 + spiral * (Math.PI / 2)
        gc.setStroke(Color.color(0.9, 0.8, 0.4, 0.35 * pulse))
        gc.setLineWidth(2.5)
        var prevSx = 0.0
        var prevSy = 0.0
        for (s <- 0 to spiralSegments) {
          val t = s.toDouble / spiralSegments
          val spiralRadius = (3.0 + t * 20.0) * pulse
          val angle = spiralPhase + t * Math.PI * 2.5
          val sx = tailX + dx * t + nx * spiralRadius * Math.cos(angle)
          val sy = tailY + dy * t + ny * spiralRadius * Math.cos(angle) * 0.5
          if (s > 0) {
            val segAlpha = 0.3 * pulse * (0.3 + t * 0.7)
            gc.setStroke(Color.color(0.92, 0.82, 0.4, segAlpha))
            gc.setLineWidth(2.0 * (1.0 - t * 0.3))
            gc.strokeLine(prevSx, prevSy, sx, sy)
          }
          prevSx = sx
          prevSy = sy
        }
      }

      // Inner wind core — brighter narrow beam
      gc.setStroke(Color.color(1.0, 0.9, 0.55, 0.5 * pulse))
      gc.setLineWidth(5.0)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      gc.setStroke(Color.color(1.0, 0.95, 0.7, 0.75 * pulse))
      gc.setLineWidth(2.5)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      // Concentric wind arcs expanding outward from beam
      for (arc <- 0 until 3) {
        val arcT = 0.3 + arc * 0.25
        val arcX = tailX + dx * arcT
        val arcY = tailY + dy * arcT
        val arcSize = 10.0 + arc * 5.0 + 3.0 * Math.sin(phase + arc)
        val arcAngle = Math.atan2(dy, dx) * 180 / Math.PI
        gc.setStroke(Color.color(0.9, 0.8, 0.4, 0.25 * pulse))
        gc.setLineWidth(1.5)
        gc.strokeArc(arcX - arcSize, arcY - arcSize * 0.5, arcSize * 2, arcSize,
          arcAngle + 70, 40, javafx.scene.shape.ArcType.OPEN)
        gc.strokeArc(arcX - arcSize, arcY - arcSize * 0.5, arcSize * 2, arcSize,
          arcAngle - 110, 40, javafx.scene.shape.ArcType.OPEN)
      }

      // Leaf/debris particles spinning in the vortex
      for (leaf <- 0 until 6) {
        val leafT = ((animationTick * 0.06 + leaf * 0.17 + projectile.id * 0.09) % 1.0)
        val leafAngle = phase * 3.0 + leaf * 1.1
        val leafDist = (3.0 + leafT * 18.0) * pulse
        val lx = tailX + dx * leafT + nx * leafDist * Math.cos(leafAngle)
        val ly = tailY + dy * leafT + ny * leafDist * Math.cos(leafAngle) * 0.5
        val lAlpha = Math.max(0.0, 0.5 * (1.0 - Math.abs(leafT - 0.5) * 2.0))
        val lRot = leafAngle * 2
        val lLen = 3.5
        // Draw as small spinning line (leaf shape)
        gc.setStroke(Color.color(0.7, 0.55, 0.2, lAlpha))
        gc.setLineWidth(2.0)
        gc.strokeLine(
          lx - Math.cos(lRot) * lLen, ly - Math.sin(lRot) * lLen * 0.5,
          lx + Math.cos(lRot) * lLen, ly + Math.sin(lRot) * lLen * 0.5
        )
        // Green tint on some
        if (leaf % 2 == 0) {
          gc.setStroke(Color.color(0.4, 0.6, 0.2, lAlpha * 0.6))
          gc.setLineWidth(1.5)
          gc.strokeLine(
            lx - Math.cos(lRot + 0.5) * lLen * 0.7, ly - Math.sin(lRot + 0.5) * lLen * 0.35,
            lx + Math.cos(lRot + 0.5) * lLen * 0.7, ly + Math.sin(lRot + 0.5) * lLen * 0.35
          )
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

      val dx = tipX - tailX
      val dy = tipY - tailY
      val len = Math.sqrt(dx * dx + dy * dy)
      if (len < 1) return
      val nx = dx / len
      val ny = dy / len

      // Liquid shimmer beam — wavy water stream
      gc.setStroke(Color.color(0.05, 0.25, 0.8, 0.06 * pulse))
      gc.setLineWidth(42 * pulse)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      gc.setStroke(Color.color(0.15, 0.5, 1.0, 0.15 * pulse))
      gc.setLineWidth(26 * pulse)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      // Wavy water core — using segmented approach for liquid look
      val segments = 10
      val points = (0 to segments).map { seg =>
        val t = seg.toDouble / segments
        val wave = Math.sin(phase * 3.0 + t * Math.PI * 4) * 3.0 * t
        (tailX + dx * t + (-ny) * wave, tailY + dy * t + nx * wave)
      }
      gc.setStroke(Color.color(0.3, 0.7, 1.0, 0.6 * fastFlicker))
      gc.setLineWidth(10 * fastFlicker)
      for (i <- 0 until points.length - 1) {
        gc.strokeLine(points(i)._1, points(i)._2, points(i + 1)._1, points(i + 1)._2)
      }
      gc.setStroke(Color.color(0.7, 0.95, 1.0, 0.9 * fastFlicker))
      gc.setLineWidth(3.5)
      for (i <- 0 until points.length - 1) {
        gc.strokeLine(points(i)._1, points(i)._2, points(i + 1)._1, points(i + 1)._2)
      }

      // Bubble particles floating alongside the stream
      for (i <- 0 until 6) {
        val t = ((animationTick * 0.06 + i.toDouble / 6 + projectile.id * 0.13) % 1.0)
        val bubbleWave = Math.sin(phase * 2.5 + i * 2.1) * 8.0
        val bx = tailX + dx * t + (-ny) * bubbleWave
        val by = tailY + dy * t + nx * bubbleWave - t * 4.0
        val bAlpha = Math.max(0.0, Math.min(1.0, 0.5 * (1.0 - Math.abs(t - 0.5) * 2.0)))
        val bSize = 2.0 + Math.sin(phase + i * 1.3) * 1.5
        // Bubble outline
        gc.setStroke(Color.color(0.6, 0.9, 1.0, bAlpha * 0.8))
        gc.setLineWidth(1.0)
        gc.strokeOval(bx - bSize, by - bSize, bSize * 2, bSize * 2)
        // Bubble highlight
        gc.setFill(Color.color(0.9, 1.0, 1.0, bAlpha * 0.5))
        gc.fillOval(bx - bSize * 0.3, by - bSize * 0.5, bSize * 0.6, bSize * 0.4)
      }

      // Trailing water drops that arc downward
      for (d <- 0 until 5) {
        val dt = ((animationTick * 0.04 + d * 0.2 + projectile.id * 0.09) % 1.0)
        val dropX = tailX + dx * (0.1 + d * 0.15) + (-ny) * Math.sin(phase + d * 1.7) * 4.0
        val dropY = tailY + dy * (0.1 + d * 0.15) + dt * dt * 15.0
        val dropAlpha = Math.max(0.0, 0.45 * (1.0 - dt))
        val dropSize = 1.5 + (1.0 - dt) * 1.5
        gc.setFill(Color.color(0.4, 0.75, 1.0, dropAlpha))
        gc.fillOval(dropX - dropSize, dropY - dropSize, dropSize * 2, dropSize * 2.5)
      }

      // Water droplet shape at tip — teardrop polygon
      val dropLen = 12.0 * pulse
      val dropWidth = 7.0 * pulse
      // Teardrop: wide rounded bottom, pointed top toward travel direction
      val tdx = Array(
        tipX + nx * dropLen,
        tipX + (-ny) * dropWidth,
        tipX - nx * dropLen * 0.3,
        tipX - (-ny) * dropWidth
      )
      val tdy = Array(
        tipY + ny * dropLen,
        tipY + nx * dropWidth,
        tipY - ny * dropLen * 0.3,
        tipY - nx * dropWidth
      )
      gc.setFill(Color.color(0.15, 0.5, 0.95, 0.5 * pulse))
      gc.fillPolygon(tdx, tdy, 4)
      gc.setFill(Color.color(0.4, 0.8, 1.0, 0.35 * pulse))
      gc.fillOval(tipX - dropWidth, tipY - dropWidth, dropWidth * 2, dropWidth * 2)
      // Bright core
      gc.setFill(Color.color(0.8, 0.97, 1.0, 0.85 * fastFlicker))
      gc.fillOval(tipX - 3, tipY - 3, 6, 6)

      // Expanding ripple rings at tip
      for (r <- 0 until 2) {
        val ripplePhase = (phase * 2.0 + r * Math.PI) % (2 * Math.PI)
        val rippleSize = 6.0 + 6.0 * ((ripplePhase / (2 * Math.PI)))
        val rippleAlpha = Math.max(0.0, 0.4 * (1.0 - ripplePhase / (2 * Math.PI)))
        gc.setStroke(Color.color(0.4, 0.8, 1.0, rippleAlpha))
        gc.setLineWidth(1.5)
        gc.strokeOval(tipX - rippleSize, tipY - rippleSize * 0.5, rippleSize * 2, rippleSize)
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

      val dx = tipX - tailX
      val dy = tipY - tailY
      val len = Math.sqrt(dx * dx + dy * dy)
      if (len < 1) return
      val nx = dx / len
      val ny = dy / len

      // Wide churning water aura
      gc.setStroke(Color.color(0.0, 0.5, 0.6, 0.07 * pulse))
      gc.setLineWidth(48 * pulse)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      // Outer glow — deep teal
      gc.setStroke(Color.color(0.0, 0.65, 0.7, 0.12 * pulse))
      gc.setLineWidth(32 * pulse)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      // Main water body — using wavy segments for rolling look
      val segments = 12
      val wavePoints = (0 to segments).map { seg =>
        val t = seg.toDouble / segments
        val roll = Math.sin(phase * 2.5 + t * Math.PI * 3) * 5.0 * (0.5 + t * 0.5)
        (tailX + dx * t + (-ny) * roll, tailY + dy * t + nx * roll)
      }

      gc.setStroke(Color.color(0.1, 0.75, 0.8, 0.45 * pulse))
      gc.setLineWidth(16 * pulse)
      for (i <- 0 until wavePoints.length - 1) {
        gc.strokeLine(wavePoints(i)._1, wavePoints(i)._2, wavePoints(i + 1)._1, wavePoints(i + 1)._2)
      }

      // Brighter inner flow
      gc.setStroke(Color.color(0.3, 0.9, 0.9, 0.6 * pulse))
      gc.setLineWidth(8 * pulse)
      for (i <- 0 until wavePoints.length - 1) {
        gc.strokeLine(wavePoints(i)._1, wavePoints(i)._2, wavePoints(i + 1)._1, wavePoints(i + 1)._2)
      }

      // White foam core
      gc.setStroke(Color.color(0.85, 1.0, 1.0, 0.8 * pulse))
      gc.setLineWidth(3.0)
      for (i <- 0 until wavePoints.length - 1) {
        gc.strokeLine(wavePoints(i)._1, wavePoints(i)._2, wavePoints(i + 1)._1, wavePoints(i + 1)._2)
      }

      // Curling wave crests along beam — more dramatic arcs
      for (i <- 0 until 4) {
        val t = 0.2 + 0.2 * i
        val cx = tailX + dx * t
        val cy = tailY + dy * t
        val waveSize = 8.0 + 4.0 * Math.sin(phase + i * 1.8)
        // Wave curl direction — perpendicular, arcing forward
        val curlAngle = Math.atan2(dy, dx) * 180 / Math.PI
        gc.setStroke(Color.color(0.5, 1.0, 1.0, 0.5 * pulse))
        gc.setLineWidth(2.0)
        gc.strokeArc(cx - waveSize, cy - waveSize, waveSize * 2, waveSize * 2,
          curlAngle, 160, javafx.scene.shape.ArcType.OPEN)
        // Foam highlight on crest
        gc.setStroke(Color.color(0.9, 1.0, 1.0, 0.4 * pulse))
        gc.setLineWidth(1.0)
        gc.strokeArc(cx - waveSize * 0.8, cy - waveSize * 0.8, waveSize * 1.6, waveSize * 1.6,
          curlAngle + 20, 100, javafx.scene.shape.ArcType.OPEN)
      }

      // Spray droplets arcing upward from the wave front
      for (spray <- 0 until 6) {
        val sprayT = ((animationTick * 0.07 + spray * 0.17 + projectile.id * 0.11) % 1.0)
        val sprayAngle = phase + spray * 1.1
        val sprayDist = sprayT * 14.0
        val sx = tipX + (-ny) * Math.sin(sprayAngle) * sprayDist
        val sy = tipY + nx * Math.sin(sprayAngle) * sprayDist - sprayT * sprayT * 12.0
        val sprayAlpha = Math.max(0.0, 0.5 * (1.0 - sprayT))
        val spraySize = 2.0 * (1.0 - sprayT * 0.5)
        gc.setFill(Color.color(0.7, 1.0, 1.0, sprayAlpha))
        gc.fillOval(sx - spraySize, sy - spraySize, spraySize * 2, spraySize * 2)
      }

      // White foam particles at the front
      for (f <- 0 until 4) {
        val foamPhase = phase + f * 1.5
        val foamX = tipX + (-ny) * Math.sin(foamPhase) * 6.0
        val foamY = tipY + nx * Math.sin(foamPhase) * 6.0
        val foamAlpha = 0.4 + 0.3 * Math.sin(foamPhase * 2)
        gc.setFill(Color.color(0.95, 1.0, 1.0, foamAlpha * pulse))
        gc.fillOval(foamX - 2, foamY - 2, 4, 4)
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

      val dx = tipX - tailX
      val dy = tipY - tailY
      val len = Math.sqrt(dx * dx + dy * dy)
      if (len < 1) return
      val nx = dx / len
      val ny = dy / len

      // Massive pressure aura
      gc.setStroke(Color.color(0.0, 0.8, 1.0, 0.08 * pulse))
      gc.setLineWidth(60 * pulse)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      // Erupting column — wider than normal, tapers at ends
      val segments = 10
      for (seg <- 0 until segments) {
        val t = seg.toDouble / segments
        val t2 = (seg + 1).toDouble / segments
        val width = 20.0 * Math.sin(t * Math.PI) * pulse // wide in middle, narrow at ends
        val width2 = 20.0 * Math.sin(t2 * Math.PI) * pulse
        val x1 = tailX + dx * t
        val y1 = tailY + dy * t
        val x2 = tailX + dx * t2
        val y2 = tailY + dy * t2

        // Turbulent water column
        val turb = Math.sin(phase * 3.0 + t * Math.PI * 4) * 3.0
        gc.setStroke(Color.color(0.1, 0.85, 1.0, 0.3 * fastFlicker))
        gc.setLineWidth(width * fastFlicker)
        gc.strokeLine(x1 + (-ny) * turb, y1 + nx * turb, x2 + (-ny) * turb, y2 + nx * turb)
      }

      // Bright pressurized core
      gc.setStroke(Color.color(0.5, 0.97, 1.0, 0.7 * fastFlicker))
      gc.setLineWidth(8.0)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      // White-hot center
      gc.setStroke(Color.color(0.92, 1.0, 1.0, 0.95 * fastFlicker))
      gc.setLineWidth(4.0)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      // Pressure distortion rings expanding outward from center
      val centerX = (tailX + tipX) / 2
      val centerY = (tailY + tipY) / 2
      for (i <- 0 until 4) {
        val ringT = ((animationTick * 0.04 + i * 0.25) % 1.0)
        val ringSize = 6.0 + ringT * 20.0
        val ringAlpha = Math.max(0.0, 0.5 * (1.0 - ringT))
        gc.setStroke(Color.color(0.2, 0.9, 1.0, ringAlpha))
        gc.setLineWidth(2.0 * (1.0 - ringT * 0.5))
        gc.strokeOval(centerX - ringSize, centerY - ringSize * 0.5, ringSize * 2, ringSize)
      }

      // Rising bubbles along the column
      for (i <- 0 until 8) {
        val bubT = ((animationTick * 0.05 + i.toDouble / 8 + projectile.id * 0.11) % 1.0)
        val bx = tailX + dx * bubT + (-ny) * Math.sin(phase * 2 + i * 1.7) * (8.0 + bubT * 5.0)
        val by = tailY + dy * bubT + nx * Math.sin(phase * 2 + i * 1.7) * (8.0 + bubT * 5.0)
        val bAlpha = Math.max(0.0, Math.min(1.0, 0.5 * (1.0 - Math.abs(bubT - 0.5) * 2.0)))
        val bSize = 2.0 + Math.sin(phase + i) * 1.5
        gc.setStroke(Color.color(0.6, 0.95, 1.0, bAlpha))
        gc.setLineWidth(1.0)
        gc.strokeOval(bx - bSize, by - bSize, bSize * 2, bSize * 2)
        // Highlight
        gc.setFill(Color.color(0.95, 1.0, 1.0, bAlpha * 0.4))
        gc.fillOval(bx - bSize * 0.3, by - bSize * 0.5, bSize * 0.5, bSize * 0.4)
      }

      // Steam cloud puffs rising upward
      for (i <- 0 until 6) {
        val t = ((animationTick * 0.04 + i.toDouble / 6 + projectile.id * 0.17) % 1.0)
        val px = centerX + Math.sin(phase + i * 1.3) * 10.0
        val py = centerY - t * 25.0
        val pAlpha = Math.max(0.0, Math.min(1.0, 0.35 * (1.0 - t)))
        val pSize = 3.0 + t * 5.0
        gc.setFill(Color.color(0.75, 0.95, 1.0, pAlpha))
        gc.fillOval(px - pSize, py - pSize, pSize * 2, pSize * 2)
        // Second layer for fluffy look
        gc.setFill(Color.color(0.9, 1.0, 1.0, pAlpha * 0.5))
        gc.fillOval(px - pSize * 0.5, py - pSize * 0.6, pSize, pSize * 0.8)
      }

      // Splash crown at tip — water droplets erupting outward
      for (crown <- 0 until 5) {
        val cAngle = phase + crown * (Math.PI * 2 / 5)
        val cDist = 8.0 + 4.0 * Math.sin(phase * 2 + crown)
        val cx = tipX + Math.cos(cAngle) * cDist
        val cy = tipY + Math.sin(cAngle) * cDist * 0.5 - 4.0
        gc.setFill(Color.color(0.5, 0.95, 1.0, 0.6 * pulse))
        gc.fillOval(cx - 2.5, cy - 2.5, 5, 5)
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

      val dx = tipX - tailX
      val dy = tipY - tailY
      val len = Math.sqrt(dx * dx + dy * dy)
      if (len < 1) return
      val nx = dx / len
      val ny = dy / len
      val perpX = -ny
      val perpY = nx

      // === Exhaust plume behind the rocket ===
      // Wide expanding fire cone from tail
      for (layer <- 0 until 3) {
        val coneSegments = 6
        for (seg <- 0 until coneSegments) {
          val t = ((animationTick * 0.1 + seg.toDouble / coneSegments + layer * 0.1 + projectile.id * 0.07) % 1.0)
          val coneWidth = (3.0 + t * 12.0 + layer * 2.0)
          val flameX = tailX - dx * t * 0.6 + perpX * Math.sin(phase * 3 + seg * 1.5 + layer) * coneWidth * 0.3
          val flameY = tailY - dy * t * 0.6 + perpY * Math.sin(phase * 3 + seg * 1.5 + layer) * coneWidth * 0.3
          val flameAlpha = Math.max(0.0, (0.6 - layer * 0.15) * (1.0 - t))
          val flameSize = (2.0 + t * 3.0) * (1.0 - layer * 0.2)

          // Color gradient: white-hot (inner) -> yellow -> orange -> red (outer)
          val flameR = 1.0
          val flameG = Math.max(0.0, Math.min(1.0, 0.9 - t * 0.5 - layer * 0.15))
          val flameB = Math.max(0.0, Math.min(1.0, 0.4 - t * 0.3 - layer * 0.1))
          gc.setFill(Color.color(flameR, flameG, flameB, flameAlpha))
          gc.fillOval(flameX - flameSize, flameY - flameSize, flameSize * 2, flameSize * 2)
        }
      }

      // Thick smoke trail — larger puffs expanding and rising
      for (i <- 0 until 7) {
        val t = ((animationTick * 0.05 + i * 0.14 + projectile.id * 0.09) % 1.0)
        val smokeX = tailX - dx * t * 0.7 + perpX * Math.sin(phase * 0.8 + i * 1.3) * (3.0 + t * 5.0)
        val smokeY = tailY - dy * t * 0.7 + perpY * Math.sin(phase * 0.8 + i * 1.3) * (3.0 + t * 5.0) - t * 6.0
        val smokeAlpha = Math.max(0.0, 0.25 * (1.0 - t))
        val smokeSize = 3.0 + t * 6.0
        val smokeGray = 0.45 + t * 0.15
        gc.setFill(Color.color(smokeGray, smokeGray, smokeGray, smokeAlpha))
        gc.fillOval(smokeX - smokeSize, smokeY - smokeSize, smokeSize * 2, smokeSize * 2)
      }

      // === Rocket body — olive/dark green military tube ===
      // Body glow
      gc.setStroke(Color.color(0.7, 0.3, 0.1, 0.1 * pulse))
      gc.setLineWidth(20 * pulse)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      // Dark olive body
      gc.setStroke(Color.color(0.3, 0.35, 0.2, 0.75 * pulse))
      gc.setLineWidth(8 * pulse)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      // Body highlight
      gc.setStroke(Color.color(0.45, 0.5, 0.3, 0.5 * pulse))
      gc.setLineWidth(3.0)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      // Stripe detail — red warning band
      val midT = 0.45
      val bandX = tailX + dx * midT
      val bandY = tailY + dy * midT
      gc.setStroke(Color.color(0.9, 0.2, 0.1, 0.7 * pulse))
      gc.setLineWidth(9.0)
      gc.strokeLine(bandX - dx * 0.03, bandY - dy * 0.03, bandX + dx * 0.03, bandY + dy * 0.03)

      // Fins at the tail — 2 small triangles
      val finLen = 10.0
      val finWidth = 6.0
      gc.setFill(Color.color(0.35, 0.4, 0.25, 0.7 * pulse))
      // Top fin
      val fin1x = Array(tailX, tailX - nx * finLen * 0.3 + perpX * finWidth, tailX - nx * finLen)
      val fin1y = Array(tailY, tailY - ny * finLen * 0.3 + perpY * finWidth, tailY - ny * finLen)
      gc.fillPolygon(fin1x, fin1y, 3)
      // Bottom fin
      val fin2x = Array(tailX, tailX - nx * finLen * 0.3 - perpX * finWidth, tailX - nx * finLen)
      val fin2y = Array(tailY, tailY - ny * finLen * 0.3 - perpY * finWidth, tailY - ny * finLen)
      gc.fillPolygon(fin2x, fin2y, 3)

      // Nosecone — pointed triangle at tip
      val noseLen = 12.0
      val noseWidth = 5.0
      val noseTipX = tipX + nx * noseLen
      val noseTipY = tipY + ny * noseLen
      val noseX = Array(noseTipX, tipX + perpX * noseWidth, tipX - perpX * noseWidth)
      val noseY = Array(noseTipY, tipY + perpY * noseWidth, tipY - perpY * noseWidth)
      gc.setFill(Color.color(0.6, 0.6, 0.55, 0.8 * pulse))
      gc.fillPolygon(noseX, noseY, 3)
      gc.setStroke(Color.color(0.7, 0.7, 0.65, 0.5 * pulse))
      gc.setLineWidth(1.0)
      gc.strokePolygon(noseX, noseY, 3)

      // Danger glow at tip
      val orbR = 5.0 * pulse
      gc.setFill(Color.color(1.0, 0.4, 0.1, 0.2 * pulse))
      gc.fillOval(noseTipX - orbR * 1.5, noseTipY - orbR * 1.5, orbR * 3, orbR * 3)
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

    // Phase 1: Intense white-hot flash (0-15%)
    if (progress < 0.15) {
      val flashPct = progress / 0.15
      val flashR = maxScreenRadius * 0.7 * flashPct
      val flashAlpha = 0.8 * (1.0 - flashPct)
      gc.setFill(Color.color(1.0, 1.0, 0.95, flashAlpha))
      gc.fillOval(screenX - flashR, screenY - flashR * 0.5, flashR * 2, flashR)
      // Inner white core
      gc.setFill(Color.color(1.0, 1.0, 1.0, flashAlpha * 0.6))
      gc.fillOval(screenX - flashR * 0.4, screenY - flashR * 0.2, flashR * 0.8, flashR * 0.4)
    }

    // Phase 2: Expanding fireball — layered with color gradient
    val fireR = maxScreenRadius * Math.min(1.0, progress * 2.0)

    // Outer heat distortion glow
    gc.setFill(Color.color(1.0, 0.4, 0.05, 0.08 * fadeOut))
    gc.fillOval(screenX - fireR * 1.6, screenY - fireR * 0.8, fireR * 3.2, fireR * 1.6)

    // Outer fireball — dark red/orange
    gc.setFill(Color.color(0.9, 0.3, 0.05, 0.2 * fadeOut))
    gc.fillOval(screenX - fireR * 1.3, screenY - fireR * 0.65, fireR * 2.6, fireR * 1.3)

    // Main fireball — orange
    gc.setFill(Color.color(1.0, 0.5, 0.1, 0.35 * fadeOut))
    gc.fillOval(screenX - fireR, screenY - fireR * 0.5, fireR * 2, fireR)

    // Inner fireball — bright yellow
    val innerR = fireR * 0.65 * fadeOut
    gc.setFill(Color.color(1.0, 0.75, 0.15, 0.45 * fadeOut))
    gc.fillOval(screenX - innerR, screenY - innerR * 0.5, innerR * 2, innerR)

    // White-hot core
    val coreR = fireR * 0.35 * fadeOut
    gc.setFill(Color.color(1.0, 0.9, 0.5, 0.55 * fadeOut))
    gc.fillOval(screenX - coreR, screenY - coreR * 0.5, coreR * 2, coreR)

    // Double shockwave rings
    val ringR = maxScreenRadius * progress * 1.3
    gc.setStroke(Color.color(1.0, 0.6, 0.2, 0.45 * fadeOut))
    gc.setLineWidth(3.0 * fadeOut)
    gc.strokeOval(screenX - ringR, screenY - ringR * 0.5, ringR * 2, ringR)

    if (progress > 0.1) {
      val ring2R = maxScreenRadius * (progress - 0.1) / 0.9 * 1.1
      gc.setStroke(Color.color(1.0, 0.5, 0.15, 0.25 * fadeOut))
      gc.setLineWidth(2.0 * fadeOut)
      gc.strokeOval(screenX - ring2R, screenY - ring2R * 0.5, ring2R * 2, ring2R)
    }

    // Fire particles — embers and burning fragments
    val particleCount = 12
    for (i <- 0 until particleCount) {
      val angle = i * (2 * Math.PI / particleCount) + i * 0.5
      val speed = 0.6 + (i % 4) * 0.15
      val dist = progress * maxScreenRadius * speed
      val rise = progress * progress * 18.0 * (0.7 + (i % 3) * 0.2)
      val px = screenX + dist * Math.cos(angle)
      val py = screenY + dist * Math.sin(angle) * 0.5 - rise
      val pSize = (2.5 + (i % 3) * 1.2) * fadeOut

      if (i % 3 == 0) {
        // Hot ember — bright yellow/white
        gc.setFill(Color.color(1.0, 0.9, 0.4, 0.7 * fadeOut))
      } else if (i % 3 == 1) {
        // Fire fragment — orange
        gc.setFill(Color.color(1.0, 0.55, 0.1, 0.6 * fadeOut))
      } else {
        // Smoke/debris — dark gray
        gc.setFill(Color.color(0.4, 0.4, 0.38, 0.4 * fadeOut))
      }
      gc.fillOval(px - pSize, py - pSize, pSize * 2, pSize * 2)
    }

    // Rising smoke puffs (late phase)
    if (progress > 0.3) {
      val smokeProgress = (progress - 0.3) / 0.7
      for (s <- 0 until 4) {
        val smokeT = smokeProgress * (0.7 + s * 0.1)
        val smokeX = screenX + Math.sin(s * 2.3) * maxScreenRadius * 0.3
        val smokeY = screenY - smokeT * 25.0
        val smokeAlpha = Math.max(0.0, 0.2 * (1.0 - smokeT))
        val smokeSize = 4.0 + smokeT * 8.0
        gc.setFill(Color.color(0.45, 0.45, 0.42, smokeAlpha))
        gc.fillOval(smokeX - smokeSize, smokeY - smokeSize * 0.5, smokeSize * 2, smokeSize)
      }
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
    drawHealthBar(screenX, spriteY, player.getHealth)
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

  private def drawPhasedEffect(centerX: Double, centerY: Double): Unit = {
    val phase = animationTick * 0.12
    val displaySz = Constants.PLAYER_DISPLAY_SIZE_PX
    val radius = displaySz * 0.55

    val pulse = 0.8 + 0.2 * Math.sin(phase * 1.5)

    // Dimensional rift aura — wide flickering field
    gc.setFill(Color.color(0.05, 0.6, 0.45, 0.06 * pulse))
    gc.fillOval(centerX - radius * 1.8, centerY - radius * 0.9, radius * 3.6, radius * 1.8)

    // Ghostly teal aura
    gc.setFill(Color.color(0.1, 0.8, 0.6, 0.1 * pulse))
    gc.fillOval(centerX - radius * 1.3, centerY - radius * 0.65, radius * 2.6, radius * 1.3)

    // Flickering outline — double ring that pulses
    val wobble = 0.03 * Math.sin(phase * 3.0)
    gc.setStroke(Color.color(0.2, 0.9, 0.7, 0.4 * pulse))
    gc.setLineWidth(1.5)
    gc.strokeOval(centerX - radius * (1.0 + wobble), centerY - radius * 0.5 * (1.0 - wobble),
      radius * 2 * (1.0 + wobble), radius * (1.0 - wobble))
    gc.setStroke(Color.color(0.3, 1.0, 0.8, 0.2 * pulse))
    gc.setLineWidth(1.0)
    gc.strokeOval(centerX - radius * (1.05 - wobble), centerY - radius * 0.52 * (1.0 + wobble),
      radius * 2 * (1.05 - wobble), radius * (1.04 + wobble))

    // Dimensional rift particles — small vertical tears in space
    for (i <- 0 until 4) {
      val riftPhase = (phase * 0.7 + i * 1.6) % (Math.PI * 2)
      val riftAlpha = Math.max(0.0, Math.sin(riftPhase) * 0.5)
      if (riftAlpha > 0.05) {
        val riftAngle = phase * 0.4 + i * (Math.PI / 2)
        val riftDist = radius * (0.6 + 0.3 * Math.sin(phase + i))
        val rx = centerX + riftDist * Math.cos(riftAngle)
        val ry = centerY + riftDist * Math.sin(riftAngle) * 0.5
        val riftLen = 6.0 + Math.sin(phase * 2 + i) * 3.0
        // Vertical tear
        gc.setStroke(Color.color(0.3, 1.0, 0.8, riftAlpha))
        gc.setLineWidth(2.0)
        gc.strokeLine(rx, ry - riftLen, rx, ry + riftLen)
        // Glow around tear
        gc.setFill(Color.color(0.2, 0.9, 0.7, riftAlpha * 0.3))
        gc.fillOval(rx - 3, ry - riftLen - 1, 6, riftLen * 2 + 2)
      }
    }

    // Floating spectral wisps — more particles with trailing motion
    for (i <- 0 until 7) {
      val angle = phase * 0.5 + i * (2 * Math.PI / 7)
      val dist = radius * (0.6 + 0.3 * Math.sin(phase * 0.8 + i))
      val sx = centerX + dist * Math.cos(angle)
      val sy = centerY + dist * Math.sin(angle) * 0.5
      val sparkleAlpha = Math.max(0.0, 0.25 + 0.35 * Math.sin(phase * 2.5 + i * 1.3))
      val sparkleSize = 1.5 + Math.sin(phase * 2 + i) * 1.0
      gc.setFill(Color.color(0.4, 1.0, 0.8, sparkleAlpha))
      gc.fillOval(sx - sparkleSize, sy - sparkleSize, sparkleSize * 2, sparkleSize * 2)
      // Trail behind each wisp
      val trailX = sx - Math.cos(angle) * 4
      val trailY = sy - Math.sin(angle) * 2
      gc.setFill(Color.color(0.3, 0.8, 0.6, Math.max(0.0, sparkleAlpha * 0.4)))
      gc.fillOval(trailX - sparkleSize * 0.7, trailY - sparkleSize * 0.7, sparkleSize * 1.4, sparkleSize * 1.4)
    }
  }

  private def drawFrozenEffect(centerX: Double, centerY: Double): Unit = {
    val phase = animationTick * 0.1
    val displaySz = Constants.PLAYER_DISPLAY_SIZE_PX
    val radius = displaySz * 0.5

    // Frost creep animation — overlay slowly expands
    val frozenPulse = 0.9 + 0.1 * Math.sin(phase * 1.5)

    // Icy blue overlay with frosted center
    gc.setFill(Color.color(0.35, 0.65, 1.0, 0.22 * frozenPulse))
    gc.fillOval(centerX - radius, centerY - radius, radius * 2, radius * 2)
    gc.setFill(Color.color(0.5, 0.8, 1.0, 0.12 * frozenPulse))
    gc.fillOval(centerX - radius * 1.2, centerY - radius * 0.6, radius * 2.4, radius * 1.2)

    // Jagged ice crystal border — hexagonal frost pattern
    gc.setStroke(Color.color(0.5, 0.85, 1.0, 0.65))
    gc.setLineWidth(2.0)
    val hexPoints = 6
    val hx = new Array[Double](hexPoints)
    val hy = new Array[Double](hexPoints)
    for (i <- 0 until hexPoints) {
      val angle = i * (Math.PI * 2 / hexPoints) + phase * 0.2
      val jagged = radius * (0.95 + 0.08 * Math.sin(phase * 2 + i * 1.5))
      hx(i) = centerX + jagged * Math.cos(angle)
      hy(i) = centerY + jagged * Math.sin(angle) * 0.6
    }
    gc.strokePolygon(hx, hy, hexPoints)

    // Inner hex for layered frost look
    gc.setStroke(Color.color(0.6, 0.9, 1.0, 0.35))
    gc.setLineWidth(1.0)
    val ihx = hx.map(x => centerX + (x - centerX) * 0.65)
    val ihy = hy.map(y => centerY + (y - centerY) * 0.65)
    gc.strokePolygon(ihx, ihy, hexPoints)

    // Ice crystal arms — 6 frost branches radiating outward
    gc.setStroke(Color.color(0.7, 0.92, 1.0, 0.5 * frozenPulse))
    gc.setLineWidth(1.5)
    for (i <- 0 until 6) {
      val angle = i * (Math.PI / 3) + phase * 0.15
      val armLen = radius * 0.85
      val sx = centerX
      val sy = centerY
      val ex = centerX + armLen * Math.cos(angle)
      val ey = centerY + armLen * Math.sin(angle) * 0.6
      gc.strokeLine(sx, sy, ex, ey)
      // Side branches
      val branchT = 0.5
      val bx = sx + (ex - sx) * branchT
      val by = sy + (ey - sy) * branchT
      val branchLen = armLen * 0.35
      gc.setStroke(Color.color(0.75, 0.93, 1.0, 0.4 * frozenPulse))
      gc.setLineWidth(1.0)
      gc.strokeLine(bx, by, bx + branchLen * Math.cos(angle + 0.8), by + branchLen * Math.sin(angle + 0.8) * 0.6)
      gc.strokeLine(bx, by, bx + branchLen * Math.cos(angle - 0.8), by + branchLen * Math.sin(angle - 0.8) * 0.6)
    }

    // Diamond-shaped crystal sparkles instead of circles
    for (i <- 0 until 8) {
      val angle = phase * 0.4 + i * (Math.PI / 4)
      val dist = radius * (0.5 + 0.25 * Math.sin(phase + i * 0.8))
      val sx = centerX + dist * Math.cos(angle)
      val sy = centerY + dist * Math.sin(angle) * 0.5
      val sparkleAlpha = Math.max(0.0, 0.4 + 0.4 * Math.sin(phase * 3 + i * 1.3))
      val sparkleSize = 2.5 + Math.sin(phase * 2 + i) * 1.0
      val rot = phase * 1.5 + i
      // Diamond shape
      val sdx = Array(sx, sx + sparkleSize * Math.cos(rot), sx, sx - sparkleSize * Math.cos(rot))
      val sdy = Array(sy - sparkleSize * 0.7, sy, sy + sparkleSize * 0.7, sy)
      gc.setFill(Color.color(0.85, 0.97, 1.0, sparkleAlpha))
      gc.fillPolygon(sdx, sdy, 4)
    }

    // Frost particles drifting slowly
    for (i <- 0 until 4) {
      val t = ((animationTick * 0.02 + i * 0.25) % 1.0)
      val fx = centerX + Math.sin(phase * 0.5 + i * 2) * radius * 0.6
      val fy = centerY - radius * 0.5 + t * radius
      val fAlpha = Math.max(0.0, 0.3 * (1.0 - Math.abs(t - 0.5) * 2.0))
      gc.setFill(Color.color(0.9, 0.97, 1.0, fAlpha))
      gc.fillOval(fx - 1.5, fy - 1.5, 3, 3)
    }
  }

  private def drawHitEffect(centerX: Double, centerY: Double, hitTime: Long): Unit = {
    if (hitTime <= 0) return
    val elapsed = System.currentTimeMillis() - hitTime
    if (elapsed < 0 || elapsed > HIT_ANIMATION_MS) return

    val progress = elapsed.toDouble / HIT_ANIMATION_MS
    val fadeOut = 1.0 - progress

    // Effect 1: Bright white-red flash (intense burst at start)
    if (progress < 0.25) {
      val flashPct = progress / 0.25
      val flashAlpha = 0.5 * (1.0 - flashPct)
      // White-hot center
      val flashR = 12.0 + flashPct * 6.0
      gc.setFill(Color.color(1.0, 1.0, 0.9, flashAlpha * 0.6))
      gc.fillOval(centerX - flashR * 0.6, centerY - flashR * 0.6, flashR * 1.2, flashR * 1.2)
      // Red outer flash
      gc.setFill(Color.color(1.0, 0.1, 0.0, flashAlpha))
      gc.fillOval(centerX - flashR, centerY - flashR, flashR * 2, flashR * 2)
    }

    // Effect 2: Double expanding shockwave rings
    val ringRadius = 8.0 + progress * 32.0
    gc.setStroke(Color.color(1.0, 0.2, 0.1, 0.75 * fadeOut))
    gc.setLineWidth(3.5 * fadeOut)
    gc.strokeOval(centerX - ringRadius, centerY - ringRadius * 0.5, ringRadius * 2, ringRadius)

    if (progress > 0.1) {
      val ring2Progress = (progress - 0.1) / 0.9
      val ring2R = 6.0 + ring2Progress * 24.0
      gc.setStroke(Color.color(1.0, 0.4, 0.15, 0.5 * (1.0 - ring2Progress)))
      gc.setLineWidth(2.0 * (1.0 - ring2Progress))
      gc.strokeOval(centerX - ring2R, centerY - ring2R * 0.5, ring2R * 2, ring2R)
    }

    // Effect 3: Red/orange particles flying outward with sparks
    val particleCount = 8
    for (i <- 0 until particleCount) {
      val angle = i * (2 * Math.PI / particleCount) + progress * 0.5 + i * 0.2
      val dist = progress * 26.0 * (0.8 + (i % 3) * 0.15)
      val rise = progress * progress * 6.0
      val px = centerX + dist * Math.cos(angle)
      val py = centerY + dist * Math.sin(angle) * 0.5 - rise
      val pSize = (2.5 + (i % 2)) * fadeOut

      if (i % 2 == 0) {
        gc.setFill(Color.color(1.0, 0.3, 0.05, 0.7 * fadeOut))
      } else {
        gc.setFill(Color.color(1.0, 0.6, 0.1, 0.6 * fadeOut))
      }
      gc.fillOval(px - pSize, py - pSize, pSize * 2, pSize * 2)

      // Trailing spark
      val trailAlpha = fadeOut * 0.3
      gc.setFill(Color.color(1.0, 0.8, 0.3, trailAlpha))
      gc.fillOval(px - Math.cos(angle) * 3 - 1, py - Math.sin(angle) * 1.5 - 1, 2, 2)
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

    val progress = elapsed.toDouble / DEATH_ANIMATION_MS
    val fadeOut = 1.0 - progress
    val color = intToColor(colorRGB)
    val displaySz = Constants.PLAYER_DISPLAY_SIZE_PX
    val centerY = screenY - displaySz / 2.0

    // Phase 1 (0-20%): Intense white-hot flash
    if (progress < 0.2) {
      val flashPct = progress / 0.2
      val flashAlpha = 0.7 * (1.0 - flashPct)
      val flashR = 15.0 + flashPct * 25.0
      gc.setFill(Color.color(1.0, 1.0, 0.95, flashAlpha))
      gc.fillOval(screenX - flashR, centerY - flashR * 0.5, flashR * 2, flashR)
      // Colored inner flash
      gc.setFill(Color.color(color.getRed, color.getGreen, color.getBlue, flashAlpha * 0.5))
      gc.fillOval(screenX - flashR * 0.7, centerY - flashR * 0.35, flashR * 1.4, flashR * 0.7)
    }

    // Triple expanding shockwave rings
    val ring1R = progress * 55.0
    gc.setStroke(Color.color(color.getRed, color.getGreen, color.getBlue, 0.55 * fadeOut))
    gc.setLineWidth(3.0 * fadeOut)
    gc.strokeOval(screenX - ring1R, centerY - ring1R * 0.5, ring1R * 2, ring1R)

    if (progress > 0.1) {
      val ring2R = (progress - 0.1) / 0.9 * 45.0
      gc.setStroke(Color.color(color.getRed, color.getGreen, color.getBlue, 0.35 * fadeOut))
      gc.setLineWidth(2.0 * fadeOut)
      gc.strokeOval(screenX - ring2R, centerY - ring2R * 0.5, ring2R * 2, ring2R)
    }

    if (progress > 0.2) {
      val ring3R = (progress - 0.2) / 0.8 * 35.0
      gc.setStroke(Color.color(1.0, 1.0, 1.0, 0.2 * fadeOut))
      gc.setLineWidth(1.5 * fadeOut)
      gc.strokeOval(screenX - ring3R, centerY - ring3R * 0.5, ring3R * 2, ring3R)
    }

    // Energy dissolution particles — more particles, varied sizes
    val particleCount = 14
    for (i <- 0 until particleCount) {
      val angle = i * (2 * Math.PI / particleCount) + i * 0.4
      val speed = 0.7 + (i % 4) * 0.15
      val dist = progress * (25.0 + (i % 4) * 12.0) * speed
      val rise = progress * progress * 25.0 * (0.8 + (i % 3) * 0.15)
      val px = screenX + dist * Math.cos(angle)
      val py = centerY + dist * Math.sin(angle) * 0.5 - rise
      val pSize = (2.5 + (i % 3) * 1.5) * fadeOut

      if (i % 3 == 0) {
        // White-hot particles
        gc.setFill(Color.color(1.0, 1.0, 0.9, 0.7 * fadeOut))
      } else if (i % 3 == 1) {
        // Bright colored particles
        val cr = Math.min(1.0, color.getRed * 0.4 + 0.6)
        val cg = Math.min(1.0, color.getGreen * 0.4 + 0.6)
        val cb = Math.min(1.0, color.getBlue * 0.4 + 0.6)
        gc.setFill(Color.color(cr, cg, cb, 0.65 * fadeOut))
      } else {
        // Player color particles
        gc.setFill(Color.color(color.getRed, color.getGreen, color.getBlue, 0.55 * fadeOut))
      }
      gc.fillOval(px - pSize, py - pSize, pSize * 2, pSize * 2)

      // Particle trail
      if (fadeOut > 0.3) {
        val trailAlpha = fadeOut * 0.2
        gc.setFill(Color.color(color.getRed, color.getGreen, color.getBlue, trailAlpha))
        val tx = px - Math.cos(angle) * 5
        val ty = py + Math.sin(angle) * 2.5 + 3
        gc.fillOval(tx - pSize * 0.5, ty - pSize * 0.5, pSize, pSize)
      }
    }

    // Fading ghost sprite rising upward with slight wobble
    val ghostAlpha = Math.max(0.0, fadeOut * 0.55)
    val ghostRise = progress * 35.0
    val ghostWobble = Math.sin(progress * Math.PI * 3) * 3.0 * fadeOut
    gc.setGlobalAlpha(ghostAlpha)
    val sprite = SpriteGenerator.getSprite(colorRGB, Direction.Down, 0, characterId)
    gc.drawImage(sprite, screenX - displaySz / 2.0 + ghostWobble, screenY - displaySz.toDouble - ghostRise, displaySz, displaySz)
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
    val healthText = s"Health: $health/${Constants.MAX_HEALTH}"
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
