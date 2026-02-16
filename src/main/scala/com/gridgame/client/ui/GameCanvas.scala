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
import com.gridgame.common.model.ProjectileDef
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

  // Screen shake state
  private var shakeIntensity: Double = 0.0
  private var shakeDecayUntil: Long = 0L
  private var shakeStartTime: Long = 0L

  private def triggerShake(intensity: Double, durationMs: Long): Unit = {
    // Only override if new shake is stronger than remaining shake
    val now = System.currentTimeMillis()
    val remaining = if (now < shakeDecayUntil) {
      val progress = (now - shakeStartTime).toDouble / (shakeDecayUntil - shakeStartTime)
      shakeIntensity * (1.0 - progress)
    } else 0.0
    if (intensity > remaining) {
      shakeIntensity = intensity
      shakeStartTime = now
      shakeDecayUntil = now + durationMs
    }
  }

  private def getShakeOffset: (Double, Double) = {
    val now = System.currentTimeMillis()
    if (now >= shakeDecayUntil || shakeIntensity <= 0) return (0.0, 0.0)
    val elapsed = (now - shakeStartTime).toDouble
    val duration = (shakeDecayUntil - shakeStartTime).toDouble
    val progress = elapsed / duration
    val decay = 1.0 - progress
    val freq = 35.0
    val ox = Math.sin(elapsed * freq * 0.001) * shakeIntensity * decay
    val oy = Math.sin(elapsed * freq * 0.0013 + 1.7) * shakeIntensity * decay * 0.7
    (ox, oy)
  }

  private def world: WorldData = client.getWorld

  // Projectile renderer registry — new characters just add entries here
  private type ProjectileRenderer = (Projectile, Double, Double) => Unit
  private val projectileRenderers: Map[Byte, ProjectileRenderer] = Map(
    ProjectileType.TENTACLE   -> ((p, cx, cy) => drawTentacleProjectile(p, cx, cy)),
    ProjectileType.ICE_BEAM   -> ((p, cx, cy) => drawIceBeamProjectile(p, cx, cy)),
    ProjectileType.AXE        -> ((p, cx, cy) => drawAxeProjectile(p, cx, cy)),
    ProjectileType.ROPE       -> ((p, cx, cy) => drawRopeProjectile(p, cx, cy)),
    ProjectileType.SPEAR      -> ((p, cx, cy) => drawSpearProjectile(p, cx, cy)),
    ProjectileType.SOUL_BOLT  -> ((p, cx, cy) => drawSoulBoltProjectile(p, cx, cy)),
    ProjectileType.HAUNT      -> ((p, cx, cy) => drawHauntProjectile(p, cx, cy)),
    ProjectileType.ARCANE_BOLT -> ((p, cx, cy) => drawArcaneBoltProjectile(p, cx, cy)),
    ProjectileType.FIREBALL   -> ((p, cx, cy) => drawFireballProjectile(p, cx, cy)),
    ProjectileType.SPLASH     -> ((p, cx, cy) => drawSplashProjectile(p, cx, cy)),
    ProjectileType.TIDAL_WAVE -> ((p, cx, cy) => drawTidalWaveProjectile(p, cx, cy)),
    ProjectileType.GEYSER     -> ((p, cx, cy) => drawGeyserProjectile(p, cx, cy)),
    ProjectileType.BULLET     -> ((p, cx, cy) => drawBulletProjectile(p, cx, cy)),
    ProjectileType.GRENADE    -> ((p, cx, cy) => drawGrenadeProjectile(p, cx, cy)),
    ProjectileType.ROCKET     -> ((p, cx, cy) => drawRocketProjectile(p, cx, cy)),
    ProjectileType.TALON      -> ((p, cx, cy) => drawTalonProjectile(p, cx, cy)),
    ProjectileType.GUST       -> ((p, cx, cy) => drawGustProjectile(p, cx, cy)),
    ProjectileType.SHURIKEN   -> ((p, cx, cy) => drawShurikenProjectile(p, cx, cy)),
    ProjectileType.POISON_DART -> ((p, cx, cy) => drawPoisonDartProjectile(p, cx, cy)),
    ProjectileType.CHAIN_BOLT -> ((p, cx, cy) => drawChainBoltProjectile(p, cx, cy)),
    ProjectileType.LOCKDOWN_CHAIN -> ((p, cx, cy) => drawLockdownChainProjectile(p, cx, cy)),
    ProjectileType.SNARE_MINE -> ((p, cx, cy) => drawSnareMineProjectile(p, cx, cy)),
    ProjectileType.KATANA     -> ((p, cx, cy) => drawKatanaProjectile(p, cx, cy)),
    ProjectileType.SWORD_WAVE -> ((p, cx, cy) => drawSwordWaveProjectile(p, cx, cy)),
    ProjectileType.PLAGUE_BOLT -> ((p, cx, cy) => drawPlagueBoltProjectile(p, cx, cy)),
    ProjectileType.MIASMA     -> ((p, cx, cy) => drawMiasmaProjectile(p, cx, cy)),
    ProjectileType.BLIGHT_BOMB -> ((p, cx, cy) => drawBlightBombProjectile(p, cx, cy)),
    ProjectileType.BLOOD_FANG  -> ((p, cx, cy) => drawBloodFangProjectile(p, cx, cy)),
    ProjectileType.BLOOD_SIPHON -> ((p, cx, cy) => drawBloodSiphonProjectile(p, cx, cy)),
    ProjectileType.BAT_SWARM   -> ((p, cx, cy) => drawBatSwarmProjectile(p, cx, cy)),
    // Batch 1: Elemental
    ProjectileType.FLAME_BOLT  -> ((p, cx, cy) => drawFlameBoltProjectile(p, cx, cy)),
    ProjectileType.FROST_SHARD -> ((p, cx, cy) => drawFrostShardProjectile(p, cx, cy)),
    ProjectileType.LIGHTNING   -> ((p, cx, cy) => drawLightningProjectile(p, cx, cy)),
    ProjectileType.CHAIN_LIGHTNING -> ((p, cx, cy) => drawChainLightningProjectile(p, cx, cy)),
    ProjectileType.THUNDER_STRIKE -> ((p, cx, cy) => drawThunderStrikeProjectile(p, cx, cy)),
    ProjectileType.BOULDER     -> ((p, cx, cy) => drawBoulderProjectile(p, cx, cy)),
    ProjectileType.SEISMIC_SLAM -> ((p, cx, cy) => drawSeismicSlamProjectile(p, cx, cy)),
    ProjectileType.WIND_BLADE  -> ((p, cx, cy) => drawWindBladeProjectile(p, cx, cy)),
    ProjectileType.MAGMA_BALL  -> ((p, cx, cy) => drawMagmaBallProjectile(p, cx, cy)),
    ProjectileType.ERUPTION    -> ((p, cx, cy) => drawEruptionProjectile(p, cx, cy)),
    ProjectileType.FROST_TRAP  -> ((p, cx, cy) => drawFrostTrapProjectile(p, cx, cy)),
    ProjectileType.SAND_SHOT   -> ((p, cx, cy) => drawSandShotProjectile(p, cx, cy)),
    ProjectileType.SAND_BLAST  -> ((p, cx, cy) => drawSandBlastProjectile(p, cx, cy)),
    ProjectileType.THORN       -> ((p, cx, cy) => drawThornProjectile(p, cx, cy)),
    ProjectileType.VINE_WHIP   -> ((p, cx, cy) => drawVineWhipProjectile(p, cx, cy)),
    ProjectileType.THORN_WALL  -> ((p, cx, cy) => drawThornWallProjectile(p, cx, cy)),
    ProjectileType.INFERNO_BLAST -> ((p, cx, cy) => drawInfernoBlastProjectile(p, cx, cy)),
    ProjectileType.GLACIER_SPIKE -> ((p, cx, cy) => drawGlacierSpikeProjectile(p, cx, cy)),
    ProjectileType.MUD_GLOB    -> ((p, cx, cy) => drawMudGlobProjectile(p, cx, cy)),
    ProjectileType.MUD_BOMB    -> ((p, cx, cy) => drawMudBombProjectile(p, cx, cy)),
    ProjectileType.EMBER_SHOT  -> ((p, cx, cy) => drawEmberShotProjectile(p, cx, cy)),
    ProjectileType.AVALANCHE_CRUSH -> ((p, cx, cy) => drawAvalancheCrushProjectile(p, cx, cy)),
    // Batch 2: Undead/Dark
    ProjectileType.DEATH_BOLT  -> ((p, cx, cy) => drawDeathBoltProjectile(p, cx, cy)),
    ProjectileType.RAISE_DEAD  -> ((p, cx, cy) => drawRaiseDeadProjectile(p, cx, cy)),
    ProjectileType.BONE_AXE    -> ((p, cx, cy) => drawBoneAxeProjectile(p, cx, cy)),
    ProjectileType.BONE_THROW  -> ((p, cx, cy) => drawBoneThrowProjectile(p, cx, cy)),
    ProjectileType.WAIL        -> ((p, cx, cy) => drawWailProjectile(p, cx, cy)),
    ProjectileType.SOUL_DRAIN  -> ((p, cx, cy) => drawSoulDrainProjectile(p, cx, cy)),
    ProjectileType.CLAW_SWIPE  -> ((p, cx, cy) => drawClawSwipeProjectile(p, cx, cy)),
    ProjectileType.DEVOUR      -> ((p, cx, cy) => drawDevourProjectile(p, cx, cy)),
    ProjectileType.SCYTHE      -> ((p, cx, cy) => drawScytheProjectile(p, cx, cy)),
    ProjectileType.REAP        -> ((p, cx, cy) => drawReapProjectile(p, cx, cy)),
    ProjectileType.SHADOW_BOLT -> ((p, cx, cy) => drawShadowBoltProjectile(p, cx, cy)),
    ProjectileType.CURSED_BLADE -> ((p, cx, cy) => drawCursedBladeProjectile(p, cx, cy)),
    ProjectileType.LIFE_DRAIN  -> ((p, cx, cy) => drawLifeDrainProjectile(p, cx, cy)),
    ProjectileType.SHOVEL      -> ((p, cx, cy) => drawShovelProjectile(p, cx, cy)),
    ProjectileType.HEAD_THROW  -> ((p, cx, cy) => drawHeadThrowProjectile(p, cx, cy)),
    ProjectileType.BANDAGE_WHIP -> ((p, cx, cy) => drawBandageWhipProjectile(p, cx, cy)),
    ProjectileType.CURSE       -> ((p, cx, cy) => drawCurseProjectile(p, cx, cy)),
    // Batch 3: Medieval/Fantasy
    ProjectileType.HOLY_BLADE  -> ((p, cx, cy) => drawHolyBladeProjectile(p, cx, cy)),
    ProjectileType.HOLY_BOLT   -> ((p, cx, cy) => drawHolyBoltProjectile(p, cx, cy)),
    ProjectileType.ARROW       -> ((p, cx, cy) => drawArrowProjectile(p, cx, cy)),
    ProjectileType.POISON_ARROW -> ((p, cx, cy) => drawPoisonArrowProjectile(p, cx, cy)),
    ProjectileType.SONIC_WAVE  -> ((p, cx, cy) => drawSonicWaveProjectile(p, cx, cy)),
    ProjectileType.SONIC_BOOM  -> ((p, cx, cy) => drawSonicBoomProjectile(p, cx, cy)),
    ProjectileType.FIST        -> ((p, cx, cy) => drawFistProjectile(p, cx, cy)),
    ProjectileType.SMITE       -> ((p, cx, cy) => drawSmiteProjectile(p, cx, cy)),
    ProjectileType.CHARM       -> ((p, cx, cy) => drawCharmProjectile(p, cx, cy)),
    ProjectileType.CARD        -> ((p, cx, cy) => drawCardProjectile(p, cx, cy)),
    // Batch 4: Sci-Fi/Tech
    ProjectileType.DATA_BOLT   -> ((p, cx, cy) => drawDataBoltProjectile(p, cx, cy)),
    ProjectileType.VIRUS       -> ((p, cx, cy) => drawVirusProjectile(p, cx, cy)),
    ProjectileType.LASER       -> ((p, cx, cy) => drawLaserProjectile(p, cx, cy)),
    ProjectileType.GRAVITY_BALL -> ((p, cx, cy) => drawGravityBallProjectile(p, cx, cy)),
    ProjectileType.GRAVITY_WELL -> ((p, cx, cy) => drawGravityWellProjectile(p, cx, cy)),
    ProjectileType.TESLA_COIL  -> ((p, cx, cy) => drawTeslaCoilProjectile(p, cx, cy)),
    ProjectileType.NANO_BOLT   -> ((p, cx, cy) => drawNanoBoltProjectile(p, cx, cy)),
    ProjectileType.VOID_BOLT   -> ((p, cx, cy) => drawVoidBoltProjectile(p, cx, cy)),
    ProjectileType.RAILGUN     -> ((p, cx, cy) => drawRailgunProjectile(p, cx, cy)),
    ProjectileType.CLUSTER_BOMB -> ((p, cx, cy) => drawClusterBombProjectile(p, cx, cy)),
    // Batch 5: Nature/Beast
    ProjectileType.VENOM_BOLT  -> ((p, cx, cy) => drawVenomBoltProjectile(p, cx, cy)),
    ProjectileType.WEB_SHOT    -> ((p, cx, cy) => drawWebShotProjectile(p, cx, cy)),
    ProjectileType.STINGER     -> ((p, cx, cy) => drawStingerProjectile(p, cx, cy)),
    ProjectileType.ACID_BOMB   -> ((p, cx, cy) => drawAcidBombProjectile(p, cx, cy))
  )

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
    val zoom = Constants.CAMERA_ZOOM

    // Apply zoom transform — virtual canvas is smaller, making tiles appear larger
    gc.save()
    gc.scale(zoom, zoom)
    val canvasW = getWidth / zoom
    val canvasH = getHeight / zoom

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
    val (shakeOx, shakeOy) = getShakeOffset
    val camOffX = canvasW / 2.0 - playerSx + shakeOx
    val camOffY = canvasH / 2.0 - playerSy + shakeOy

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

    // Restore transform before drawing HUD at screen-pixel scale
    gc.restore()

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
    val pt = client.getSelectedCharacterDef.primaryProjectileType
    if (!ProjectileDef.get(pt).chargeSpeedScaling.isDefined) return

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
    val pDef = ProjectileDef.get(pt)
    val (minRange, maxRange) = pDef.chargeRangeScaling match {
      case Some(scaling) => (scaling.min.toInt, scaling.max.toInt)
      case None => (pDef.maxRange, pDef.maxRange)
    }
    val cylLength = 1.0 + minRange + chargePct * (maxRange - minRange)

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

  private def drawAbilityIcon(abilityName: String, centerX: Double, centerY: Double, halfSize: Double, color: Color): Unit = {
    val s = halfSize
    gc.setFill(color)
    gc.setStroke(color)
    gc.setLineWidth(1.5)

    abilityName match {
      case "Tentacle" =>
        // Wavy S-curve tendril with tip bulb
        gc.setLineWidth(2.5)
        gc.beginPath()
        gc.moveTo(centerX, centerY - s * 0.9)
        gc.bezierCurveTo(centerX + s, centerY - s * 0.3, centerX - s, centerY + s * 0.3, centerX, centerY + s * 0.9)
        gc.stroke()
        gc.fillOval(centerX - 2.5, centerY - s * 0.9 - 2, 5, 5)

      case "Ice Beam" =>
        // 6-pointed snowflake: 3 crossing lines + center dot
        gc.setLineWidth(1.5)
        for (i <- 0 until 3) {
          val angle = i * Math.PI / 3
          val dx = s * 0.85 * Math.cos(angle)
          val dy = s * 0.85 * Math.sin(angle)
          gc.strokeLine(centerX - dx, centerY - dy, centerX + dx, centerY + dy)
        }
        gc.fillOval(centerX - 2.5, centerY - 2.5, 5, 5)
        gc.setFill(Color.color(1.0, 1.0, 1.0, 0.3))
        gc.fillOval(centerX - 1.5, centerY - 1.5, 3, 3)

      case "Spear Throw" =>
        // Diagonal spear with triangular head
        gc.setLineWidth(2.0)
        gc.strokeLine(centerX - s * 0.8, centerY + s * 0.8, centerX + s * 0.3, centerY - s * 0.3)
        gc.fillPolygon(
          Array(centerX + s * 0.85, centerX + s * 0.5, centerX + s * 0.1),
          Array(centerY - s * 0.85, centerY - s * 0.1, centerY - s * 0.5),
          3
        )

      case "Rope" =>
        // Lasso loop with trailing line
        gc.setLineWidth(2.0)
        gc.strokeOval(centerX - s * 0.4, centerY - s * 0.75, s * 0.9, s * 0.85)
        gc.beginPath()
        gc.moveTo(centerX + s * 0.05, centerY + s * 0.1)
        gc.bezierCurveTo(centerX - s * 0.3, centerY + s * 0.5, centerX + s * 0.2, centerY + s * 0.7, centerX + s * 0.3, centerY + s * 0.9)
        gc.stroke()

      case "Phase Shift" =>
        // Ghost silhouette: dome + body + dark eyes
        gc.fillOval(centerX - s * 0.5, centerY - s * 0.9, s, s * 0.9)
        gc.fillRect(centerX - s * 0.5, centerY - s * 0.5, s, s * 1.0)
        gc.setFill(Color.color(0, 0, 0, 0.5))
        gc.fillOval(centerX - s * 0.3, centerY - s * 0.3, s * 0.22, s * 0.25)
        gc.fillOval(centerX + s * 0.1, centerY - s * 0.3, s * 0.22, s * 0.25)

      case "Haunt" =>
        // Spectral eye: almond outline + filled pupil
        gc.setLineWidth(2.0)
        gc.beginPath()
        gc.moveTo(centerX - s * 0.9, centerY)
        gc.quadraticCurveTo(centerX, centerY - s * 0.8, centerX + s * 0.9, centerY)
        gc.quadraticCurveTo(centerX, centerY + s * 0.8, centerX - s * 0.9, centerY)
        gc.closePath()
        gc.stroke()
        gc.fillOval(centerX - s * 0.25, centerY - s * 0.25, s * 0.5, s * 0.5)

      case "Fireball" =>
        // Flame: large base + two upper licks + bright core
        gc.fillOval(centerX - s * 0.5, centerY - s * 0.2, s, s)
        gc.fillOval(centerX - s * 0.35, centerY - s * 0.7, s * 0.55, s * 0.65)
        gc.fillOval(centerX + s * 0.0, centerY - s * 0.6, s * 0.45, s * 0.55)
        gc.setFill(Color.color(1.0, 1.0, 1.0, 0.3))
        gc.fillOval(centerX - s * 0.2, centerY + s * 0.05, s * 0.4, s * 0.4)

      case "Blink" =>
        // Lightning bolt zigzag
        gc.fillPolygon(
          Array(centerX - s * 0.2, centerX + s * 0.5, centerX + s * 0.05, centerX + s * 0.2, centerX - s * 0.5, centerX - s * 0.05),
          Array(centerY - s * 0.9, centerY - s * 0.15, centerY - s * 0.1, centerY + s * 0.9, centerY + s * 0.15, centerY + s * 0.1),
          6
        )

      case "Tidal Wave" =>
        // Three nested wave arcs
        gc.setLineWidth(2.0)
        for (i <- 0 until 3) {
          val r = s * (0.35 + i * 0.25)
          gc.strokeArc(centerX - r, centerY + s * 0.2 - r * 0.7, r * 2, r * 1.4, 30, 120, javafx.scene.shape.ArcType.OPEN)
        }

      case "Geyser" =>
        // Erupting water column with spray
        gc.setLineWidth(2.0)
        gc.fillRect(centerX - s * 0.15, centerY - s * 0.3, s * 0.3, s * 0.9)
        gc.strokeLine(centerX, centerY - s * 0.3, centerX, centerY - s * 0.9)
        gc.strokeLine(centerX, centerY - s * 0.5, centerX - s * 0.4, centerY - s * 0.85)
        gc.strokeLine(centerX, centerY - s * 0.5, centerX + s * 0.4, centerY - s * 0.85)
        gc.fillOval(centerX - s * 0.5 - 1.5, centerY - s * 0.7 - 1.5, 3, 3)
        gc.fillOval(centerX + s * 0.5 - 1.5, centerY - s * 0.75 - 1.5, 3, 3)

      case "Grenade" =>
        // Round grenade body + fuse + spark
        gc.fillOval(centerX - s * 0.5, centerY - s * 0.3, s, s)
        gc.setLineWidth(1.5)
        gc.strokeLine(centerX + s * 0.1, centerY - s * 0.3, centerX + s * 0.3, centerY - s * 0.7)
        gc.setFill(Color.color(1.0, 1.0, 1.0, 0.4))
        gc.fillOval(centerX + s * 0.2, centerY - s * 0.85, s * 0.3, s * 0.3)

      case "Rocket" =>
        // Missile: nose cone + body + fins
        gc.fillRect(centerX - s * 0.2, centerY - s * 0.4, s * 0.4, s * 0.9)
        gc.fillPolygon(
          Array(centerX, centerX - s * 0.2, centerX + s * 0.2),
          Array(centerY - s * 0.9, centerY - s * 0.4, centerY - s * 0.4),
          3
        )
        gc.fillPolygon(
          Array(centerX - s * 0.2, centerX - s * 0.5, centerX - s * 0.2),
          Array(centerY + s * 0.2, centerY + s * 0.5, centerY + s * 0.5),
          3
        )
        gc.fillPolygon(
          Array(centerX + s * 0.2, centerX + s * 0.5, centerX + s * 0.2),
          Array(centerY + s * 0.2, centerY + s * 0.5, centerY + s * 0.5),
          3
        )

      case "Swoop" =>
        // Downward chevron / wing shape
        gc.setLineWidth(2.5)
        gc.beginPath()
        gc.moveTo(centerX - s * 0.8, centerY - s * 0.5)
        gc.lineTo(centerX, centerY + s * 0.5)
        gc.lineTo(centerX + s * 0.8, centerY - s * 0.5)
        gc.stroke()
        gc.strokeLine(centerX, centerY + s * 0.5, centerX, centerY + s * 0.8)

      case "Gust" =>
        // 3 curved wind lines
        gc.setLineWidth(1.5)
        for (i <- 0 until 3) {
          val yOff = (i - 1) * s * 0.45
          gc.beginPath()
          gc.moveTo(centerX - s * 0.8, centerY + yOff)
          gc.quadraticCurveTo(centerX, centerY + yOff - s * 0.3, centerX + s * 0.8, centerY + yOff)
          gc.stroke()
        }

      case "Poison Dart" =>
        // Dart body + tail fins + drip
        gc.fillPolygon(
          Array(centerX + s * 0.9, centerX - s * 0.7, centerX - s * 0.7),
          Array(centerY, centerY - s * 0.15, centerY + s * 0.15),
          3
        )
        gc.fillPolygon(
          Array(centerX - s * 0.5, centerX - s * 0.9, centerX - s * 0.5),
          Array(centerY - s * 0.15, centerY - s * 0.4, centerY - s * 0.35),
          3
        )
        gc.fillPolygon(
          Array(centerX - s * 0.5, centerX - s * 0.9, centerX - s * 0.5),
          Array(centerY + s * 0.15, centerY + s * 0.4, centerY + s * 0.35),
          3
        )
        gc.fillOval(centerX + s * 0.7, centerY + s * 0.35, s * 0.2, s * 0.3)

      case "Shadow Step" =>
        // Dash arrow with fading shadow trail
        gc.setLineWidth(2.0)
        gc.strokeLine(centerX - s * 0.3, centerY, centerX + s * 0.7, centerY)
        gc.fillPolygon(
          Array(centerX + s * 0.9, centerX + s * 0.5, centerX + s * 0.5),
          Array(centerY, centerY - s * 0.35, centerY + s * 0.35),
          3
        )
        gc.setStroke(Color.color(color.getRed, color.getGreen, color.getBlue, 0.4))
        gc.setLineWidth(1.5)
        gc.strokeLine(centerX - s * 0.9, centerY - s * 0.3, centerX - s * 0.5, centerY - s * 0.3)
        gc.strokeLine(centerX - s * 0.7, centerY + s * 0.3, centerX - s * 0.3, centerY + s * 0.3)

      case "Lockdown" =>
        // Two interlocking chain link ovals
        gc.setLineWidth(2.0)
        gc.strokeOval(centerX - s * 0.7, centerY - s * 0.4, s * 0.8, s * 0.8)
        gc.strokeOval(centerX - s * 0.1, centerY - s * 0.4, s * 0.8, s * 0.8)

      case "Snare Mine" =>
        // Spiked circle: filled center + 4 triangle spikes
        gc.fillOval(centerX - s * 0.4, centerY - s * 0.4, s * 0.8, s * 0.8)
        for (i <- 0 until 4) {
          val angle = i * Math.PI / 2 + Math.PI / 4
          val tipDist = s * 0.9
          val baseDist = s * 0.35
          val spread = s * 0.2
          val tipX = centerX + tipDist * Math.cos(angle)
          val tipY = centerY + tipDist * Math.sin(angle)
          val perpX = Math.cos(angle + Math.PI / 2) * spread
          val perpY = Math.sin(angle + Math.PI / 2) * spread
          val baseX = centerX + baseDist * Math.cos(angle)
          val baseY = centerY + baseDist * Math.sin(angle)
          gc.fillPolygon(
            Array(tipX, baseX + perpX, baseX - perpX),
            Array(tipY, baseY + perpY, baseY - perpY),
            3
          )
        }

      case "Iaijutsu" =>
        // Curved slash arc with sparkle at tip
        gc.setLineWidth(3.0)
        gc.beginPath()
        gc.moveTo(centerX + s * 0.8, centerY - s * 0.6)
        gc.quadraticCurveTo(centerX - s * 0.2, centerY, centerX + s * 0.4, centerY + s * 0.8)
        gc.stroke()
        gc.setFill(Color.color(1.0, 1.0, 1.0, 0.4))
        gc.fillOval(centerX + s * 0.7, centerY - s * 0.7, s * 0.3, s * 0.3)

      case "Whirlwind" =>
        // Pinwheel: 4 curved blade arcs
        gc.setLineWidth(2.0)
        for (i <- 0 until 4) {
          val startAngle = i * Math.PI / 2
          val x0 = centerX + s * 0.15 * Math.cos(startAngle)
          val y0 = centerY + s * 0.15 * Math.sin(startAngle)
          val cpAngle = startAngle + Math.PI / 4
          val endAngle = startAngle + Math.PI / 2
          gc.beginPath()
          gc.moveTo(x0, y0)
          gc.quadraticCurveTo(
            centerX + s * 0.9 * Math.cos(cpAngle),
            centerY + s * 0.9 * Math.sin(cpAngle),
            centerX + s * 0.3 * Math.cos(endAngle),
            centerY + s * 0.3 * Math.sin(endAngle)
          )
          gc.stroke()
        }

      case "Miasma" =>
        // Toxic cloud: 3 overlapping circles + highlight
        gc.fillOval(centerX - s * 0.65, centerY - s * 0.2, s * 0.7, s * 0.7)
        gc.fillOval(centerX - s * 0.05, centerY - s * 0.2, s * 0.7, s * 0.7)
        gc.fillOval(centerX - s * 0.35, centerY - s * 0.7, s * 0.7, s * 0.7)
        gc.setFill(Color.color(1.0, 1.0, 1.0, 0.2))
        gc.fillOval(centerX - s * 0.2, centerY - s * 0.5, s * 0.35, s * 0.35)

      case "Blight Bomb" =>
        // Alchemical flask: round body + neck + stopper + highlight
        gc.fillOval(centerX - s * 0.5, centerY - s * 0.1, s, s * 0.9)
        gc.fillRect(centerX - s * 0.15, centerY - s * 0.55, s * 0.3, s * 0.5)
        gc.fillRect(centerX - s * 0.2, centerY - s * 0.7, s * 0.4, s * 0.2)
        gc.setFill(Color.color(1.0, 1.0, 1.0, 0.2))
        gc.fillOval(centerX - s * 0.3, centerY + s * 0.05, s * 0.3, s * 0.35)

      case "Blood Siphon" =>
        // Blood droplet with inner highlight
        gc.fillPolygon(
          Array(centerX, centerX + s * 0.5, centerX + s * 0.45, centerX, centerX - s * 0.45, centerX - s * 0.5),
          Array(centerY - s * 0.9, centerY + s * 0.15, centerY + s * 0.55, centerY + s * 0.85, centerY + s * 0.55, centerY + s * 0.15),
          6
        )
        gc.setFill(Color.color(1.0, 1.0, 1.0, 0.3))
        gc.fillOval(centerX - s * 0.15, centerY - s * 0.1, s * 0.25, s * 0.35)

      case "Bat Swarm" =>
        // Bat silhouette: two wing arcs + small body
        gc.fillOval(centerX - s * 0.12, centerY - s * 0.15, s * 0.24, s * 0.3)
        // Left wing
        gc.beginPath()
        gc.moveTo(centerX - s * 0.1, centerY)
        gc.quadraticCurveTo(centerX - s * 0.6, centerY - s * 0.9, centerX - s * 0.9, centerY - s * 0.3)
        gc.quadraticCurveTo(centerX - s * 0.55, centerY - s * 0.1, centerX - s * 0.1, centerY + s * 0.15)
        gc.closePath()
        gc.fill()
        // Right wing
        gc.beginPath()
        gc.moveTo(centerX + s * 0.1, centerY)
        gc.quadraticCurveTo(centerX + s * 0.6, centerY - s * 0.9, centerX + s * 0.9, centerY - s * 0.3)
        gc.quadraticCurveTo(centerX + s * 0.55, centerY - s * 0.1, centerX + s * 0.1, centerY + s * 0.15)
        gc.closePath()
        gc.fill()

      case _ =>
        // Default fallback: filled circle
        gc.fillOval(centerX - s, centerY - s, s * 2, s * 2)
    }
  }

  private def drawSingleProjectile(projectile: Projectile, camOffX: Double, camOffY: Double): Unit = {
    val scale = 1.8
    val pcx = worldToScreenX(projectile.getX.toDouble, projectile.getY.toDouble, camOffX)
    val pcy = worldToScreenY(projectile.getX.toDouble, projectile.getY.toDouble, camOffY)
    gc.save()
    gc.translate(pcx, pcy)
    gc.scale(scale, scale)
    gc.translate(-pcx, -pcy)
    projectileRenderers.getOrElse(projectile.projectileType,
      (p: Projectile, cx: Double, cy: Double) => drawNormalProjectile(p, cx, cy)
    )(projectile, camOffX, camOffY)
    gc.restore()
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

    val margin = 250.0
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
        val particleCount = 12
        for (i <- 0 until particleCount) {
          val t = ((animationTick * 0.08 + i.toDouble / particleCount + projectile.id * 0.13) % 1.0)
          val px = tailX + dx * t + ny * Math.sin(phase + i * 2.1) * 10.0
          val py = tailY + dy * t - nx * Math.sin(phase + i * 2.1) * 10.0
          val pAlpha = Math.max(0.0, Math.min(1.0, 0.8 * (1.0 - Math.abs(t - 0.5) * 2.0)))
          val pSize = 5.0 + Math.sin(phase + i) * 2.5
          gc.setFill(Color.color(coreR, coreG, coreB, pAlpha))
          gc.fillOval(px - pSize, py - pSize, pSize * 2, pSize * 2)
        }
      }

      // Bright orb at the tip with pulse
      val orbR = 12.0 * pulse
      gc.setFill(Color.color(color.getRed, color.getGreen, color.getBlue, 0.12 * pulse))
      gc.fillOval(tipX - orbR * 3, tipY - orbR * 3, orbR * 6, orbR * 6)
      gc.setFill(Color.color(color.getRed, color.getGreen, color.getBlue, 0.22 * pulse))
      gc.fillOval(tipX - orbR * 2, tipY - orbR * 2, orbR * 4, orbR * 4)
      gc.setFill(Color.color(color.getRed, color.getGreen, color.getBlue, 0.45 * pulse))
      gc.fillOval(tipX - orbR, tipY - orbR, orbR * 2, orbR * 2)
      gc.setFill(Color.color(coreR, coreG, coreB, 0.9 * fastFlicker))
      gc.fillOval(tipX - orbR * 0.5, tipY - orbR * 0.5, orbR, orbR)

      // Expanding shockwave rings at tip
      for (ring <- 0 until 3) {
        val ringPhase = ((animationTick * 0.12 + ring * 0.33 + projectile.id * 0.17) % 1.0)
        val ringR = 5.0 + ringPhase * 30.0
        val ringAlpha = Math.max(0.0, 0.4 * (1.0 - ringPhase))
        gc.setStroke(Color.color(color.getRed, color.getGreen, color.getBlue, ringAlpha))
        gc.setLineWidth(2.5 * (1.0 - ringPhase * 0.5))
        gc.strokeOval(tipX - ringR, tipY - ringR, ringR * 2, ringR * 2)
      }

      // Energy sparks radiating from tip
      for (spark <- 0 until 8) {
        val sparkAngle = phase * 1.5 + spark * (Math.PI * 2.0 / 8.0)
        val sparkLen = 8.0 + 5.0 * Math.sin(phase * 3.0 + spark * 2.3)
        val sparkX = tipX + Math.cos(sparkAngle) * sparkLen
        val sparkY = tipY + Math.sin(sparkAngle) * sparkLen * 0.6
        val sparkAlpha = 0.3 + 0.3 * Math.sin(phase * 4.0 + spark * 1.7)
        gc.setStroke(Color.color(coreR, coreG, coreB, sparkAlpha))
        gc.setLineWidth(1.5)
        gc.strokeLine(tipX, tipY, sparkX, sparkY)
      }
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

    val margin = 250.0
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

    val margin = 250.0
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
      for (i <- 0 until 16) {
        val t = ((animationTick * 0.05 + i.toDouble / 16 + projectile.id * 0.13) % 1.0)
        val sparkle = Math.sin(phase * 2.5 + i * 1.4)
        val cx = tailX + dx * t + (-ny) * sparkle * 14.0
        val cy = tailY + dy * t + nx * sparkle * 14.0
        val cAlpha = Math.max(0.0, Math.min(1.0, 0.8 * (1.0 - Math.abs(t - 0.5) * 2.0)))
        val cSize = 4.5 + Math.sin(phase + i * 0.9) * 2.5
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
      val starR = 15.0 * pulse
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
      val orbR = 10.0 * pulse
      gc.setFill(Color.color(0.3, 0.6, 1.0, 0.15 * pulse))
      gc.fillOval(tipX - orbR * 3, tipY - orbR * 3, orbR * 6, orbR * 6)
      gc.setFill(Color.color(0.3, 0.6, 1.0, 0.3 * pulse))
      gc.fillOval(tipX - orbR * 2, tipY - orbR * 2, orbR * 4, orbR * 4)
      gc.setFill(Color.color(0.5, 0.8, 1.0, 0.5 * pulse))
      gc.fillOval(tipX - orbR, tipY - orbR, orbR * 2, orbR * 2)
      gc.setFill(Color.color(0.92, 0.97, 1.0, 0.9 * fastFlicker))
      gc.fillOval(tipX - orbR * 0.4, tipY - orbR * 0.4, orbR * 0.8, orbR * 0.8)

      // Expanding frost rings
      for (ring <- 0 until 3) {
        val ringPhase = ((animationTick * 0.1 + ring * 0.33 + projectile.id * 0.15) % 1.0)
        val ringR = 6.0 + ringPhase * 25.0
        val ringAlpha = Math.max(0.0, 0.35 * (1.0 - ringPhase))
        gc.setStroke(Color.color(0.5, 0.85, 1.0, ringAlpha))
        gc.setLineWidth(2.0 * (1.0 - ringPhase * 0.5))
        gc.strokeOval(tipX - ringR, tipY - ringR, ringR * 2, ringR * 2)
      }
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

    val margin = 250.0
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

    val margin = 250.0
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

    val margin = 250.0
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

    val margin = 250.0
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

    val margin = 250.0
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

    val margin = 250.0
    if (Math.max(tailX, tipX) > -margin && Math.min(tailX, tipX) < getWidth + margin &&
        Math.max(tailY, tipY) > -margin && Math.min(tailY, tipY) < getHeight + margin) {

      gc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND)
      val phase = (animationTick + projectile.id * 23) * 0.4
      val pulse = 0.85 + 0.15 * Math.sin(phase)
      val fastFlicker = 0.9 + 0.1 * Math.sin(phase * 3.2)

      val dx = tipX - tailX
      val dy = tipY - tailY
      val len = Math.sqrt(dx * dx + dy * dy)
      val nx = if (len > 1) dx / len else 0.0
      val ny = if (len > 1) dy / len else 0.0

      // Dimensional rift outer glow — bolt pierces through walls
      gc.setStroke(Color.color(0.3, 0.0, 0.6, 0.06 * pulse))
      gc.setLineWidth(36 * pulse)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      gc.setStroke(Color.color(0.4, 0.1, 0.8, 0.12 * pulse))
      gc.setLineWidth(22 * pulse)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      // Crackling energy tendrils branching off the bolt
      if (len > 1) {
        for (i <- 0 until 4) {
          val branchT = ((animationTick * 0.15 + i * 0.25 + projectile.id * 0.07) % 1.0)
          val branchX = tailX + dx * branchT
          val branchY = tailY + dy * branchT
          val side = if (i % 2 == 0) 1.0 else -1.0
          val branchLen = 8.0 + 6.0 * Math.sin(phase * 3.1 + i * 1.9)
          val branchAngle = Math.sin(phase * 2.3 + i * 2.7) * 0.6
          val bx = branchX + (-ny * side) * branchLen * Math.cos(branchAngle) + nx * branchLen * Math.sin(branchAngle) * 0.3
          val by = branchY + (nx * side) * branchLen * Math.cos(branchAngle) + ny * branchLen * Math.sin(branchAngle) * 0.3
          val midX = (branchX + bx) * 0.5 + (-ny * side) * 3.0 * Math.sin(phase * 4.0 + i)
          val midY = (branchY + by) * 0.5 + (nx * side) * 3.0 * Math.sin(phase * 4.0 + i)
          val bAlpha = Math.max(0.0, 0.5 * (1.0 - Math.abs(branchT - 0.5) * 2.0))
          gc.setStroke(Color.color(0.6, 0.3, 1.0, bAlpha * 0.7))
          gc.setLineWidth(1.5)
          gc.strokeLine(branchX, branchY, midX, midY)
          gc.strokeLine(midX, midY, bx, by)
        }
      }

      // Core beam — bright arcane energy
      gc.setStroke(Color.color(0.5, 0.3, 0.9, 0.5 * fastFlicker))
      gc.setLineWidth(8 * fastFlicker)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      gc.setStroke(Color.color(0.7, 0.5, 1.0, 0.9 * fastFlicker))
      gc.setLineWidth(3.0)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      // White-hot piercing core
      gc.setStroke(Color.color(0.9, 0.8, 1.0, 0.95 * fastFlicker))
      gc.setLineWidth(1.2)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      // Spiraling arcane runes orbiting the bolt path
      if (len > 1) {
        for (i <- 0 until 3) {
          val runePhase = phase * 1.8 + i * (Math.PI * 2.0 / 3.0)
          val runeT = ((animationTick * 0.06 + i.toDouble / 3.0 + projectile.id * 0.11) % 1.0)
          val orbitR = 10.0 + 3.0 * Math.sin(phase * 2.0 + i)
          val runeX = tailX + dx * runeT + (-ny) * Math.cos(runePhase) * orbitR + nx * Math.sin(runePhase) * orbitR * 0.3
          val runeY = tailY + dy * runeT + nx * Math.cos(runePhase) * orbitR + ny * Math.sin(runePhase) * orbitR * 0.3
          val rAlpha = Math.max(0.0, Math.min(1.0, 0.7 * (1.0 - Math.abs(runeT - 0.5) * 2.0)))

          // Draw rune as a small diamond with inner cross
          val rSize = 3.5
          gc.setStroke(Color.color(0.7, 0.4, 1.0, rAlpha))
          gc.setLineWidth(1.2)
          gc.strokePolygon(
            Array(runeX, runeX + rSize, runeX, runeX - rSize),
            Array(runeY - rSize, runeY, runeY + rSize, runeY), 4)
          gc.setStroke(Color.color(0.9, 0.7, 1.0, rAlpha * 0.6))
          gc.setLineWidth(0.8)
          gc.strokeLine(runeX - rSize * 0.5, runeY, runeX + rSize * 0.5, runeY)
          gc.strokeLine(runeX, runeY - rSize * 0.5, runeX, runeY + rSize * 0.5)
        }
      }

      // Shimmering sparkle particles with phase-shift effect
      if (len > 1) {
        for (i <- 0 until 6) {
          val t = ((animationTick * 0.1 + i.toDouble / 6 + projectile.id * 0.13) % 1.0)
          val wave = Math.sin(phase * 2.8 + i * 2.1) * 7.0
          val px = tailX + dx * t + (-ny) * wave
          val py = tailY + dy * t + nx * wave
          val phaseShift = Math.sin(phase * 4.0 + i * 1.5)
          val pAlpha = Math.max(0.0, Math.min(1.0, 0.6 * (1.0 - Math.abs(t - 0.5) * 2.0) * (0.5 + 0.5 * phaseShift)))
          val pSize = 2.0 + Math.sin(phase + i) * 0.8
          // Alternate between purple and cyan sparkles
          if (i % 2 == 0) {
            gc.setFill(Color.color(0.6, 0.3, 1.0, pAlpha))
          } else {
            gc.setFill(Color.color(0.3, 0.6, 1.0, pAlpha))
          }
          gc.fillOval(px - pSize, py - pSize, pSize * 2, pSize * 2)
        }
      }

      // Arcane sigil at tip — glowing eye shape
      val orbR = 7.0 * pulse
      gc.setFill(Color.color(0.3, 0.0, 0.6, 0.15 * pulse))
      gc.fillOval(tipX - orbR * 2.5, tipY - orbR * 2.5, orbR * 5, orbR * 5)
      gc.setFill(Color.color(0.5, 0.2, 0.9, 0.4 * pulse))
      gc.fillOval(tipX - orbR * 1.2, tipY - orbR * 1.2, orbR * 2.4, orbR * 2.4)

      // Arcane eye — two arcs forming an eye shape with bright pupil
      gc.setStroke(Color.color(0.8, 0.6, 1.0, 0.8 * fastFlicker))
      gc.setLineWidth(1.5)
      val eyeW = orbR * 1.8
      val eyeH = orbR * 0.8
      gc.strokeOval(tipX - eyeW, tipY - eyeH, eyeW * 2, eyeH * 2)
      // Bright pupil
      val pupilR = orbR * 0.4
      gc.setFill(Color.color(0.9, 0.8, 1.0, 0.95 * fastFlicker))
      gc.fillOval(tipX - pupilR, tipY - pupilR, pupilR * 2, pupilR * 2)
      // Pupil glow ring
      gc.setStroke(Color.color(0.7, 0.5, 1.0, 0.6 * fastFlicker))
      gc.setLineWidth(1.0)
      gc.strokeOval(tipX - pupilR * 1.8, tipY - pupilR * 1.8, pupilR * 3.6, pupilR * 3.6)

      // Trailing crystalline shatter particles behind bolt
      if (len > 1) {
        for (i <- 0 until 4) {
          val trailT = ((animationTick * 0.12 + i * 0.22 + projectile.id * 0.17) % 1.0)
          if (trailT < 0.4) {
            val fadeT = trailT / 0.4
            val tAlpha = Math.max(0.0, 0.4 * (1.0 - fadeT))
            val drift = (1.0 - fadeT) * 5.0
            val side = if (i % 2 == 0) 1.0 else -1.0
            val shardX = tailX + (-ny * side) * drift + dx * fadeT * 0.1
            val shardY = tailY + (nx * side) * drift + dy * fadeT * 0.1
            val sSize = 2.5 * (1.0 - fadeT)
            gc.setFill(Color.color(0.6, 0.4, 1.0, tAlpha))
            gc.fillPolygon(
              Array(shardX, shardX + sSize, shardX, shardX - sSize),
              Array(shardY - sSize * 1.5, shardY, shardY + sSize * 1.5, shardY), 4)
          }
        }
      }
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

    val margin = 250.0
    if (Math.max(tailX, tipX) > -margin && Math.min(tailX, tipX) < getWidth + margin &&
        Math.max(tailY, tipY) > -margin && Math.min(tailY, tipY) < getHeight + margin) {

      gc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND)
      val phase = (animationTick + projectile.id * 31) * 0.3
      val pulse = 0.8 + 0.2 * Math.sin(phase)
      val fastFlicker = 0.85 + 0.15 * Math.sin(phase * 2.5)

      val dx = tipX - tailX
      val dy = tipY - tailY
      val len = Math.sqrt(dx * dx + dy * dy)
      val nx = if (len > 1) dx / len else 0.0
      val ny = if (len > 1) dy / len else 0.0

      // Center of the fireball (at tip)
      val cx = tipX
      val cy = tipY

      // Heat distortion outer ring
      val distortR = 22.0 * pulse
      gc.setStroke(Color.color(1.0, 0.3, 0.0, 0.06 * pulse))
      gc.setLineWidth(3.0)
      gc.strokeOval(cx - distortR, cy - distortR, distortR * 2, distortR * 2)

      // Smoke trail behind the fireball — dark billowing puffs
      if (len > 1) {
        for (i <- 0 until 10) {
          val smokeT = ((animationTick * 0.06 + i * 0.15 + projectile.id * 0.09) % 1.0)
          val age = smokeT
          val smokeX = cx - nx * (15.0 + age * 40.0) + (-ny) * Math.sin(phase * 1.5 + i * 1.3) * (3.0 + age * 6.0)
          val smokeY = cy - ny * (15.0 + age * 40.0) + nx * Math.sin(phase * 1.5 + i * 1.3) * (3.0 + age * 6.0) - age * 8.0
          val smokeR = 3.0 + age * 7.0
          val smokeAlpha = Math.max(0.0, 0.25 * (1.0 - age))
          gc.setFill(Color.color(0.3, 0.2, 0.15, smokeAlpha))
          gc.fillOval(smokeX - smokeR, smokeY - smokeR, smokeR * 2, smokeR * 2)
        }
      }

      // Fiery trailing tail — wide flame wake
      gc.setStroke(Color.color(1.0, 0.2, 0.0, 0.08 * pulse))
      gc.setLineWidth(30 * pulse)
      gc.strokeLine(tailX, tailY, cx, cy)

      gc.setStroke(Color.color(1.0, 0.4, 0.0, 0.15 * pulse))
      gc.setLineWidth(18 * pulse)
      gc.strokeLine(tailX, tailY, cx, cy)

      gc.setStroke(Color.color(1.0, 0.6, 0.1, 0.35 * fastFlicker))
      gc.setLineWidth(8 * fastFlicker)
      gc.strokeLine(tailX, tailY, cx, cy)

      // Swirling fire tongues licking outward from the fireball surface
      for (i <- 0 until 10) {
        val tongueAngle = phase * 2.0 + i * (Math.PI / 5.0)
        val tongueLen = 8.0 + 5.0 * Math.sin(phase * 3.0 + i * 2.1)
        val innerR = 8.0 * pulse
        val tongueStartX = cx + Math.cos(tongueAngle) * innerR
        val tongueStartY = cy + Math.sin(tongueAngle) * innerR
        val tongueEndX = cx + Math.cos(tongueAngle) * (innerR + tongueLen)
        val tongueEndY = cy + Math.sin(tongueAngle) * (innerR + tongueLen)
        val tongueAlpha = Math.max(0.0, 0.5 + 0.3 * Math.sin(phase * 4.0 + i * 1.7))
        val tG = 0.3 + 0.3 * Math.sin(phase * 2.5 + i)
        gc.setStroke(Color.color(1.0, tG, 0.0, tongueAlpha * 0.6))
        gc.setLineWidth(3.0 + Math.sin(phase * 3.0 + i) * 1.0)
        gc.strokeLine(tongueStartX, tongueStartY, tongueEndX, tongueEndY)
      }

      // Fireball body — layered sphere from outer inferno to molten core
      val orbR = 16.0 * pulse
      // Dark red outer
      gc.setFill(Color.color(0.8, 0.1, 0.0, 0.2 * pulse))
      gc.fillOval(cx - orbR * 1.8, cy - orbR * 1.8, orbR * 3.6, orbR * 3.6)
      // Bright orange
      gc.setFill(Color.color(1.0, 0.4, 0.0, 0.4 * pulse))
      gc.fillOval(cx - orbR * 1.2, cy - orbR * 1.2, orbR * 2.4, orbR * 2.4)
      // Yellow-orange fire
      gc.setFill(Color.color(1.0, 0.65, 0.1, 0.6 * fastFlicker))
      gc.fillOval(cx - orbR * 0.8, cy - orbR * 0.8, orbR * 1.6, orbR * 1.6)
      // White-hot molten core
      gc.setFill(Color.color(1.0, 0.95, 0.6, 0.85 * fastFlicker))
      gc.fillOval(cx - orbR * 0.35, cy - orbR * 0.35, orbR * 0.7, orbR * 0.7)

      // Swirling molten surface pattern — arcs across the fireball face
      gc.setStroke(Color.color(1.0, 0.8, 0.2, 0.5 * fastFlicker))
      gc.setLineWidth(1.5)
      for (i <- 0 until 3) {
        val arcAngle = phase * 1.5 + i * (Math.PI * 2.0 / 3.0)
        val arcR = orbR * 0.7
        val ax1 = cx + Math.cos(arcAngle) * arcR
        val ay1 = cy + Math.sin(arcAngle) * arcR
        val ax2 = cx + Math.cos(arcAngle + 1.2) * arcR
        val ay2 = cy + Math.sin(arcAngle + 1.2) * arcR
        gc.strokeLine(ax1, ay1, ax2, ay2)
      }

      // Ember and spark particles rising off the fireball
      if (len > 1) {
        for (i <- 0 until 14) {
          val embT = ((animationTick * 0.09 + i * 0.07 + projectile.id * 0.07) % 1.0)
          val angle = phase * 1.3 + i * (Math.PI * 2.0 / 14.0)
          val scatter = 12.0 + embT * 15.0
          val embX = cx + Math.cos(angle) * scatter + Math.sin(phase * 2.0 + i) * 3.0
          val embY = cy + Math.sin(angle) * scatter - embT * 12.0
          val embAlpha = Math.max(0.0, 0.7 * (1.0 - embT))
          val embSize = 1.5 + (1.0 - embT) * 1.5
          if (i % 3 == 0) {
            gc.setFill(Color.color(1.0, 0.9, 0.3, embAlpha))
          } else if (i % 3 == 1) {
            gc.setFill(Color.color(1.0, 0.5, 0.0, embAlpha))
          } else {
            gc.setFill(Color.color(1.0, 0.2, 0.0, embAlpha))
          }
          gc.fillOval(embX - embSize, embY - embSize, embSize * 2, embSize * 2)
        }
      }

      // Dripping lava particles falling from the fireball
      for (i <- 0 until 8) {
        val dripT = ((animationTick * 0.07 + i * 0.12 + projectile.id * 0.19) % 1.0)
        val dripX = cx + Math.sin(phase + i * 2.5) * 8.0
        val dripY = cy + dripT * 22.0
        val dAlpha = Math.max(0.0, 0.6 * (1.0 - dripT))
        val dSize = 2.5 * (1.0 - dripT * 0.5)
        gc.setFill(Color.color(1.0, 0.3, 0.0, dAlpha))
        gc.fillOval(dripX - dSize, dripY - dSize, dSize * 2, dSize * 2)
      }

      // Expanding heat shockwave rings
      for (ring <- 0 until 3) {
        val ringPhase = ((animationTick * 0.1 + ring * 0.33 + projectile.id * 0.13) % 1.0)
        val ringR = 12.0 + ringPhase * 40.0
        val ringAlpha = Math.max(0.0, 0.3 * (1.0 - ringPhase))
        gc.setStroke(Color.color(1.0, 0.4, 0.0, ringAlpha))
        gc.setLineWidth(3.0 * (1.0 - ringPhase))
        gc.strokeOval(cx - ringR, cy - ringR, ringR * 2, ringR * 2)
      }

      // Bright flash pulse at core
      val flashPulse = Math.max(0.0, Math.sin(phase * 5.0))
      if (flashPulse > 0.5) {
        val flashR = orbR * 2.5 * flashPulse
        gc.setFill(Color.color(1.0, 0.95, 0.7, 0.12 * flashPulse))
        gc.fillOval(cx - flashR, cy - flashR, flashR * 2, flashR * 2)
      }
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

    val margin = 250.0
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

    val margin = 250.0
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

    val margin = 250.0
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

    val margin = 250.0
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

  private def drawPlagueBoltProjectile(projectile: Projectile, camOffX: Double, camOffY: Double): Unit = {
    val projX = projectile.getX.toDouble
    val projY = projectile.getY.toDouble
    val beamLength = 4.0

    val tailX = worldToScreenX(projX, projY, camOffX)
    val tailY = worldToScreenY(projX, projY, camOffY)
    val tipWX = projX + projectile.dx * beamLength
    val tipWY = projY + projectile.dy * beamLength
    val tipX = worldToScreenX(tipWX, tipWY, camOffX)
    val tipY = worldToScreenY(tipWX, tipWY, camOffY)

    val margin = 250.0
    if (Math.max(tailX, tipX) > -margin && Math.min(tailX, tipX) < getWidth + margin &&
        Math.max(tailY, tipY) > -margin && Math.min(tailY, tipY) < getHeight + margin) {

      gc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND)
      val phase = (animationTick + projectile.id * 23) * 0.3
      val pulse = 0.85 + 0.15 * Math.sin(phase)

      val dx = tipX - tailX
      val dy = tipY - tailY
      val len = Math.sqrt(dx * dx + dy * dy)
      if (len < 1) return
      val nx = -dy / len
      val ny = dx / len

      // Sickly green outer glow
      gc.setStroke(Color.color(0.3, 0.7, 0.1, 0.08 * pulse))
      gc.setLineWidth(22 * pulse)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      // Mid glow — toxic green
      gc.setStroke(Color.color(0.4, 0.8, 0.15, 0.25 * pulse))
      gc.setLineWidth(10 * pulse)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      // Bright green-yellow core
      gc.setStroke(Color.color(0.6, 0.9, 0.2, 0.85 * pulse))
      gc.setLineWidth(3.5)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      // Orb at tip — sickly green
      val orbR = 6.0 * pulse
      gc.setFill(Color.color(0.3, 0.75, 0.1, 0.15 * pulse))
      gc.fillOval(tipX - orbR * 1.5, tipY - orbR * 1.5, orbR * 3, orbR * 3)
      gc.setFill(Color.color(0.5, 0.85, 0.2, 0.5 * pulse))
      gc.fillOval(tipX - orbR * 0.7, tipY - orbR * 0.7, orbR * 1.4, orbR * 1.4)

      // Dark smoke wisps trailing behind
      for (i <- 0 until 5) {
        val t = ((animationTick * 0.07 + i * 0.2 + projectile.id * 0.09) % 1.0)
        val wispAngle = phase * 2.0 + i * 1.4
        val wispDist = 3.0 + t * 5.0
        val smokeX = tailX - dx * t * 0.3 + nx * wispDist * Math.sin(wispAngle)
        val smokeY = tailY - dy * t * 0.3 + ny * wispDist * Math.sin(wispAngle) - t * 3.0
        val smokeAlpha = Math.max(0.0, 0.3 * (1.0 - t))
        val smokeSize = 2.0 + t * 3.5
        // Dark greenish-black smoke
        gc.setFill(Color.color(0.15, 0.2, 0.05, smokeAlpha))
        gc.fillOval(smokeX - smokeSize, smokeY - smokeSize, smokeSize * 2, smokeSize * 2)
      }

      // Tiny toxic drip particles
      for (i <- 0 until 3) {
        val t = ((animationTick * 0.1 + i * 0.33 + projectile.id * 0.13) % 1.0)
        val dripX = tipX + nx * Math.sin(phase * 3 + i * 2.1) * 4.0
        val dripY = tipY + t * 8.0
        val dripAlpha = Math.max(0.0, 0.5 * (1.0 - t))
        val dripSize = 1.5 * (1.0 - t * 0.5)
        gc.setFill(Color.color(0.3, 0.8, 0.1, dripAlpha))
        gc.fillOval(dripX - dripSize, dripY - dripSize, dripSize * 2, dripSize * 2)
      }
    }
  }

  private def drawMiasmaProjectile(projectile: Projectile, camOffX: Double, camOffY: Double): Unit = {
    val projX = projectile.getX.toDouble
    val projY = projectile.getY.toDouble

    val sx = worldToScreenX(projX, projY, camOffX)
    val sy = worldToScreenY(projX, projY, camOffY)

    val margin = 250.0
    if (sx > -margin && sx < getWidth + margin && sy > -margin && sy < getHeight + margin) {
      val phase = (animationTick + projectile.id * 19) * 0.25
      val pulse = 0.8 + 0.2 * Math.sin(phase)

      // Wide roiling gas cloud — large radius since this passes through players
      val cloudR = 18.0 * pulse

      // Outer green-purple haze
      gc.setFill(Color.color(0.3, 0.5, 0.15, 0.06 * pulse))
      gc.fillOval(sx - cloudR * 1.5, sy - cloudR * 1.5, cloudR * 3, cloudR * 3)
      gc.setFill(Color.color(0.4, 0.3, 0.5, 0.05 * pulse))
      gc.fillOval(sx - cloudR * 1.2, sy - cloudR * 1.2, cloudR * 2.4, cloudR * 2.4)

      // Main gas body — sickly green
      gc.setFill(Color.color(0.3, 0.6, 0.15, 0.2 * pulse))
      gc.fillOval(sx - cloudR, sy - cloudR, cloudR * 2, cloudR * 2)

      // Inner denser patches — rolling green-purple blobs
      for (blob <- 0 until 5) {
        val blobAngle = phase * 1.5 + blob * (Math.PI * 2 / 5)
        val blobDist = cloudR * 0.4 * Math.sin(phase * 0.7 + blob * 0.9)
        val bx = sx + Math.cos(blobAngle) * blobDist
        val by = sy + Math.sin(blobAngle) * blobDist * 0.6
        val blobSize = 5.0 + 3.0 * Math.sin(phase * 1.3 + blob * 1.7)
        // Alternate between green and purple patches
        if (blob % 2 == 0) {
          gc.setFill(Color.color(0.35, 0.7, 0.15, 0.3 * pulse))
        } else {
          gc.setFill(Color.color(0.45, 0.25, 0.55, 0.25 * pulse))
        }
        gc.fillOval(bx - blobSize, by - blobSize, blobSize * 2, blobSize * 2)
      }

      // Bright green core
      val coreR = 5.0 * pulse
      gc.setFill(Color.color(0.5, 0.85, 0.2, 0.45 * pulse))
      gc.fillOval(sx - coreR, sy - coreR, coreR * 2, coreR * 2)

      // Gas tendrils reaching outward
      for (tendril <- 0 until 6) {
        val tAngle = phase * 0.8 + tendril * (Math.PI / 3)
        val tLen = cloudR * (0.8 + 0.4 * Math.sin(phase * 1.5 + tendril * 1.1))
        val tx = sx + Math.cos(tAngle) * tLen
        val ty = sy + Math.sin(tAngle) * tLen * 0.6
        gc.setStroke(Color.color(0.35, 0.6, 0.15, 0.2 * pulse))
        gc.setLineWidth(3.0 + Math.sin(phase + tendril) * 1.5)
        gc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND)
        gc.strokeLine(sx, sy, tx, ty)
        // Wisp at tendril tip
        val wispSize = 3.0 + Math.sin(phase * 2 + tendril) * 1.5
        gc.setFill(Color.color(0.4, 0.7, 0.2, 0.15 * pulse))
        gc.fillOval(tx - wispSize, ty - wispSize, wispSize * 2, wispSize * 2)
      }

      // Floating poison particles
      for (i <- 0 until 4) {
        val t = ((animationTick * 0.08 + i * 0.25 + projectile.id * 0.11) % 1.0)
        val pAngle = phase * 2.5 + i * 1.6
        val pDist = cloudR * t
        val px = sx + Math.cos(pAngle) * pDist
        val py = sy + Math.sin(pAngle) * pDist * 0.6 - t * 5.0
        val pAlpha = Math.max(0.0, 0.4 * (1.0 - t))
        val pSize = 1.5 + t * 2.0
        gc.setFill(Color.color(0.5, 0.9, 0.2, pAlpha))
        gc.fillOval(px - pSize, py - pSize, pSize * 2, pSize * 2)
      }
    }
  }

  private def drawBlightBombProjectile(projectile: Projectile, camOffX: Double, camOffY: Double): Unit = {
    val projX = projectile.getX.toDouble
    val projY = projectile.getY.toDouble

    val sx = worldToScreenX(projX, projY, camOffX)
    val sy = worldToScreenY(projX, projY, camOffY)

    val margin = 250.0
    if (sx > -margin && sx < getWidth + margin && sy > -margin && sy < getHeight + margin) {
      val phase = (animationTick + projectile.id * 13) * 0.4
      val bounce = Math.abs(Math.sin(phase * 2.0)) * 3.0
      val spinAngle = animationTick * 0.35 + projectile.id * 1.3

      val bombY = sy - bounce
      val vialR = 6.0

      // Toxic danger glow — pulsing green aura
      val dangerPulse = 0.5 + 0.5 * Math.sin(phase * 3.0)
      gc.setFill(Color.color(0.3, 0.8, 0.1, 0.06 * dangerPulse))
      gc.fillOval(sx - vialR * 3, bombY - vialR * 3, vialR * 6, vialR * 6)
      gc.setFill(Color.color(0.4, 0.7, 0.15, 0.08 * dangerPulse))
      gc.fillOval(sx - vialR * 2, bombY - vialR * 2, vialR * 4, vialR * 4)

      // Dark glass vial body
      gc.setFill(Color.color(0.15, 0.15, 0.2, 0.85))
      gc.fillOval(sx - vialR, bombY - vialR, vialR * 2, vialR * 2)

      // Green liquid inside — visible through glass, sloshing with spin
      val liquidOff = Math.sin(spinAngle) * 2.0
      gc.setFill(Color.color(0.3, 0.8, 0.15, 0.7))
      gc.fillOval(sx - vialR * 0.6 + liquidOff, bombY - vialR * 0.3,
                  vialR * 1.2, vialR * 1.0)

      // Glass outline
      gc.setStroke(Color.color(0.25, 0.25, 0.3, 0.9))
      gc.setLineWidth(1.5)
      gc.strokeOval(sx - vialR, bombY - vialR, vialR * 2, vialR * 2)

      // Glass highlight
      gc.setFill(Color.color(0.6, 0.7, 0.6, 0.35))
      gc.fillOval(sx - 3, bombY - vialR + 2, 4, 3)

      // Cork/stopper at top
      gc.setFill(Color.color(0.55, 0.4, 0.25, 0.8))
      gc.fillOval(sx - 2, bombY - vialR - 3, 4, 4)

      // Toxic fumes leaking from cork
      for (i <- 0 until 4) {
        val t = ((animationTick * 0.12 + i * 0.25 + projectile.id * 0.08) % 1.0)
        val fumeAngle = phase * 3.0 + i * 1.5
        val fumeDist = t * 7.0
        val fumeX = sx + Math.cos(fumeAngle) * fumeDist * 0.4
        val fumeY = bombY - vialR - 3 - fumeDist
        val fumeAlpha = Math.max(0.0, 0.5 * (1.0 - t))
        val fumeSize = 1.5 + t * 2.5
        gc.setFill(Color.color(0.35, 0.7, 0.15, fumeAlpha))
        gc.fillOval(fumeX - fumeSize, fumeY - fumeSize, fumeSize * 2, fumeSize * 2)
      }

      // Green smoke trail behind
      for (i <- 0 until 4) {
        val t = ((animationTick * 0.06 + i * 0.25 + projectile.id * 0.07) % 1.0)
        val smokeX = sx - projectile.dx * t * 16.0 + Math.sin(phase + i * 1.5) * 2.0
        val smokeY = sy - projectile.dy * t * 16.0 + t * 3.0
        val smokeAlpha = Math.max(0.0, 0.2 * (1.0 - t))
        val smokeSize = 2.5 + t * 4.0
        gc.setFill(Color.color(0.25, 0.4, 0.1, smokeAlpha))
        gc.fillOval(smokeX - smokeSize, smokeY - smokeSize, smokeSize * 2, smokeSize * 2)
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

    val margin = 250.0
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

    val margin = 250.0
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

    val margin = 250.0
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

    val margin = 250.0
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

  private def drawShurikenProjectile(projectile: Projectile, camOffX: Double, camOffY: Double): Unit = {
    val projX = projectile.getX.toDouble
    val projY = projectile.getY.toDouble

    val cx = worldToScreenX(projX, projY, camOffX)
    val cy = worldToScreenY(projX, projY, camOffY)

    val margin = 250.0
    if (cx > -margin && cx < getWidth + margin && cy > -margin && cy < getHeight + margin) {
      val phase = (animationTick + projectile.id * 31) * 0.8
      val spin = phase * 4.0 // fast spin
      val pulse = 0.85 + 0.15 * Math.sin(phase)

      // Outer shadow glow
      gc.setFill(Color.color(0.2, 0.0, 0.3, 0.12 * pulse))
      gc.fillOval(cx - 14, cy - 14, 28, 28)

      // 4-pointed spinning star
      val starPoints = 4
      val outerR = 10.0 * pulse
      val innerR = 3.5 * pulse
      val xPts = new Array[Double](starPoints * 2)
      val yPts = new Array[Double](starPoints * 2)
      for (i <- 0 until starPoints) {
        val outerAngle = spin + i * Math.PI / 2.0
        val innerAngle = spin + (i + 0.5) * Math.PI / 2.0
        xPts(i * 2) = cx + Math.cos(outerAngle) * outerR
        yPts(i * 2) = cy + Math.sin(outerAngle) * outerR
        xPts(i * 2 + 1) = cx + Math.cos(innerAngle) * innerR
        yPts(i * 2 + 1) = cy + Math.sin(innerAngle) * innerR
      }

      // Metal shuriken body
      gc.setFill(Color.color(0.55, 0.55, 0.6, 0.85 * pulse))
      gc.fillPolygon(xPts, yPts, starPoints * 2)
      gc.setStroke(Color.color(0.75, 0.75, 0.8, 0.9 * pulse))
      gc.setLineWidth(1.2)
      gc.strokePolygon(xPts, yPts, starPoints * 2)

      // Bright edge glint
      gc.setFill(Color.color(0.9, 0.9, 1.0, 0.7 * pulse))
      val glintAngle = spin
      val gx = cx + Math.cos(glintAngle) * outerR * 0.7
      val gy = cy + Math.sin(glintAngle) * outerR * 0.7
      gc.fillOval(gx - 2, gy - 2, 4, 4)

      // Spin trail afterimages
      for (i <- 1 to 3) {
        val trailAngle = spin - i * 0.4
        val trailAlpha = 0.15 * (1.0 - i * 0.3) * pulse
        val trailR = outerR * (1.0 - i * 0.1)
        gc.setStroke(Color.color(0.6, 0.6, 0.7, trailAlpha))
        gc.setLineWidth(1.5)
        gc.strokeLine(
          cx + Math.cos(trailAngle) * trailR, cy + Math.sin(trailAngle) * trailR,
          cx + Math.cos(trailAngle + Math.PI) * trailR, cy + Math.sin(trailAngle + Math.PI) * trailR
        )
      }
    }
  }

  private def drawPoisonDartProjectile(projectile: Projectile, camOffX: Double, camOffY: Double): Unit = {
    val projX = projectile.getX.toDouble
    val projY = projectile.getY.toDouble
    val beamLength = 4.0

    val tailX = worldToScreenX(projX, projY, camOffX)
    val tailY = worldToScreenY(projX, projY, camOffY)
    val tipWX = projX + projectile.dx * beamLength
    val tipWY = projY + projectile.dy * beamLength
    val tipX = worldToScreenX(tipWX, tipWY, camOffX)
    val tipY = worldToScreenY(tipWX, tipWY, camOffY)

    val margin = 250.0
    if (Math.max(tailX, tipX) > -margin && Math.min(tailX, tipX) < getWidth + margin &&
        Math.max(tailY, tipY) > -margin && Math.min(tailY, tipY) < getHeight + margin) {

      gc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND)
      val phase = (animationTick + projectile.id * 31) * 0.5
      val pulse = 0.85 + 0.15 * Math.sin(phase * 3.0)

      val dx = tipX - tailX
      val dy = tipY - tailY
      val len = Math.sqrt(dx * dx + dy * dy)
      if (len < 1) return
      val nx = dx / len
      val ny = dy / len
      val perpX = -ny
      val perpY = nx

      // Poison mist trail
      for (i <- 0 until 5) {
        val t = ((animationTick * 0.08 + i * 0.2 + projectile.id * 0.07) % 1.0)
        val mistX = tailX - dx * t * 0.4 + perpX * Math.sin(phase * 2.0 + i * 1.5) * 6.0
        val mistY = tailY - dy * t * 0.4 + perpY * Math.sin(phase * 2.0 + i * 1.5) * 6.0
        val mistAlpha = Math.max(0.0, 0.25 * (1.0 - t))
        val mistR = 4.0 + t * 6.0
        gc.setFill(Color.color(0.2, 0.8, 0.1, mistAlpha * pulse))
        gc.fillOval(mistX - mistR, mistY - mistR, mistR * 2, mistR * 2)
      }

      // Outer poison glow
      gc.setStroke(Color.color(0.1, 0.7, 0.0, 0.15 * pulse))
      gc.setLineWidth(12 * pulse)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      // Dart shaft — dark wood
      gc.setStroke(Color.color(0.35, 0.2, 0.1, 0.9 * pulse))
      gc.setLineWidth(3.0)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      // Poison-coated tip — bright green
      gc.setStroke(Color.color(0.15, 0.9, 0.1, 0.95 * pulse))
      gc.setLineWidth(2.5)
      gc.strokeLine(tailX + dx * 0.6, tailY + dy * 0.6, tipX, tipY)

      // Needle point
      val pointLen = 6.0
      val pointX = tipX + nx * pointLen
      val pointY = tipY + ny * pointLen
      gc.setStroke(Color.color(0.5, 1.0, 0.3, 0.9 * pulse))
      gc.setLineWidth(1.5)
      gc.strokeLine(tipX, tipY, pointX, pointY)

      // Dripping poison drops
      for (i <- 0 until 3) {
        val t = ((animationTick * 0.06 + i * 0.33 + projectile.id * 0.11) % 1.0)
        val dropX = tailX + dx * (0.5 + i * 0.15) + perpX * 3.0
        val dropY = tailY + dy * (0.5 + i * 0.15) + perpY * 3.0 + t * 10.0
        val dropAlpha = Math.max(0.0, 0.6 * (1.0 - t))
        val dropR = 2.0 * (1.0 - t * 0.5)
        gc.setFill(Color.color(0.2, 0.9, 0.1, dropAlpha))
        gc.fillOval(dropX - dropR, dropY - dropR, dropR * 2, dropR * 2)
      }

      // Feather flights at tail
      for (f <- -1 to 1 by 2) {
        val fOff = f * 4.0
        gc.setStroke(Color.color(0.3, 0.15, 0.3, 0.6 * pulse))
        gc.setLineWidth(1.5)
        gc.strokeLine(tailX, tailY, tailX - nx * 6 + perpX * fOff, tailY - ny * 6 + perpY * fOff)
      }
    }
  }

  private def drawChainBoltProjectile(projectile: Projectile, camOffX: Double, camOffY: Double): Unit = {
    val projX = projectile.getX.toDouble
    val projY = projectile.getY.toDouble
    val beamLength = 3.5

    val tailX = worldToScreenX(projX, projY, camOffX)
    val tailY = worldToScreenY(projX, projY, camOffY)
    val tipWX = projX + projectile.dx * beamLength
    val tipWY = projY + projectile.dy * beamLength
    val tipX = worldToScreenX(tipWX, tipWY, camOffX)
    val tipY = worldToScreenY(tipWX, tipWY, camOffY)

    val margin = 250.0
    if (Math.max(tailX, tipX) > -margin && Math.min(tailX, tipX) < getWidth + margin &&
        Math.max(tailY, tipY) > -margin && Math.min(tailY, tipY) < getHeight + margin) {

      gc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND)
      val phase = (animationTick + projectile.id * 23) * 0.3
      val pulse = 0.85 + 0.15 * Math.sin(phase)

      val dx = tipX - tailX
      val dy = tipY - tailY
      val len = Math.sqrt(dx * dx + dy * dy)
      if (len < 1) return
      val nx = -dy / len
      val ny = dx / len

      // Iron chain links — zigzag pattern
      val segments = 10
      val points = (0 to segments).map { seg =>
        val t = seg.toDouble / segments
        val zigzag = (if (seg % 2 == 0) 1.0 else -1.0) * 4.0 * pulse
        val bx = tailX + dx * t + nx * zigzag
        val by = tailY + dy * t + ny * zigzag
        (bx, by)
      }

      // Shadow
      gc.setStroke(Color.color(0.15, 0.15, 0.15, 0.3 * pulse))
      gc.setLineWidth(6.0)
      for (i <- 0 until points.length - 1) {
        gc.strokeLine(points(i)._1, points(i)._2, points(i + 1)._1, points(i + 1)._2)
      }

      // Main chain — dark iron
      gc.setStroke(Color.color(0.45, 0.45, 0.5, 0.85 * pulse))
      gc.setLineWidth(3.5)
      for (i <- 0 until points.length - 1) {
        gc.strokeLine(points(i)._1, points(i)._2, points(i + 1)._1, points(i + 1)._2)
      }

      // Highlight links
      gc.setStroke(Color.color(0.7, 0.7, 0.75, 0.5 * pulse))
      gc.setLineWidth(1.5)
      for (i <- 0 until points.length - 1 by 2) {
        gc.strokeLine(points(i)._1, points(i)._2, points(i + 1)._1, points(i + 1)._2)
      }

      // Frost shimmer at tip — chain freezes on contact
      val frostPulse = 0.5 + 0.5 * Math.sin(phase * 2.5)
      gc.setFill(Color.color(0.5, 0.8, 1.0, 0.15 * frostPulse))
      gc.fillOval(tipX - 8, tipY - 8, 16, 16)
      gc.setFill(Color.color(0.7, 0.9, 1.0, 0.3 * frostPulse))
      gc.fillOval(tipX - 4, tipY - 4, 8, 8)
    }
  }

  private def drawLockdownChainProjectile(projectile: Projectile, camOffX: Double, camOffY: Double): Unit = {
    val projX = projectile.getX.toDouble
    val projY = projectile.getY.toDouble
    val beamLength = 4.0

    val tailX = worldToScreenX(projX, projY, camOffX)
    val tailY = worldToScreenY(projX, projY, camOffY)
    val tipWX = projX + projectile.dx * beamLength
    val tipWY = projY + projectile.dy * beamLength
    val tipX = worldToScreenX(tipWX, tipWY, camOffX)
    val tipY = worldToScreenY(tipWX, tipWY, camOffY)

    val margin = 250.0
    if (Math.max(tailX, tipX) > -margin && Math.min(tailX, tipX) < getWidth + margin &&
        Math.max(tailY, tipY) > -margin && Math.min(tailY, tipY) < getHeight + margin) {

      gc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND)
      val phase = (animationTick + projectile.id * 19) * 0.35
      val pulse = 0.85 + 0.15 * Math.sin(phase)

      val dx = tipX - tailX
      val dy = tipY - tailY
      val len = Math.sqrt(dx * dx + dy * dy)
      if (len < 1) return
      val nx = -dy / len
      val ny = dx / len

      // Heavy chains — thicker zigzag with more aggressive pattern
      val segments = 12
      val points = (0 to segments).map { seg =>
        val t = seg.toDouble / segments
        val zigzag = (if (seg % 2 == 0) 1.0 else -1.0) * 6.0 * pulse
        val bx = tailX + dx * t + nx * zigzag
        val by = tailY + dy * t + ny * zigzag
        (bx, by)
      }

      // Red warning glow
      gc.setStroke(Color.color(0.9, 0.2, 0.1, 0.08 * pulse))
      gc.setLineWidth(20.0 * pulse)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      // Shadow
      gc.setStroke(Color.color(0.1, 0.1, 0.1, 0.4 * pulse))
      gc.setLineWidth(7.0)
      for (i <- 0 until points.length - 1) {
        gc.strokeLine(points(i)._1, points(i)._2, points(i + 1)._1, points(i + 1)._2)
      }

      // Main chain — heavier, darker
      gc.setStroke(Color.color(0.35, 0.35, 0.4, 0.9 * pulse))
      gc.setLineWidth(4.5)
      for (i <- 0 until points.length - 1) {
        gc.strokeLine(points(i)._1, points(i)._2, points(i + 1)._1, points(i + 1)._2)
      }

      // Metallic highlights
      gc.setStroke(Color.color(0.65, 0.65, 0.7, 0.5 * pulse))
      gc.setLineWidth(2.0)
      for (i <- 0 until points.length - 1 by 2) {
        gc.strokeLine(points(i)._1, points(i)._2, points(i + 1)._1, points(i + 1)._2)
      }

      // Frost crystals at tip
      val frostPulse = 0.5 + 0.5 * Math.sin(phase * 3.0)
      gc.setFill(Color.color(0.4, 0.7, 1.0, 0.2 * frostPulse))
      gc.fillOval(tipX - 10, tipY - 10, 20, 20)
      gc.setFill(Color.color(0.6, 0.85, 1.0, 0.4 * frostPulse))
      gc.fillOval(tipX - 5, tipY - 5, 10, 10)

      // Lock icon shimmer at the tip
      gc.setStroke(Color.color(0.8, 0.85, 1.0, 0.6 * frostPulse))
      gc.setLineWidth(2.0)
      gc.strokeOval(tipX - 3, tipY - 4, 6, 5)
      gc.strokeLine(tipX - 2, tipY + 1, tipX - 2, tipY + 4)
      gc.strokeLine(tipX + 2, tipY + 1, tipX + 2, tipY + 4)
      gc.strokeLine(tipX - 2, tipY + 4, tipX + 2, tipY + 4)
    }
  }

  private def drawSnareMineProjectile(projectile: Projectile, camOffX: Double, camOffY: Double): Unit = {
    val projX = projectile.getX.toDouble
    val projY = projectile.getY.toDouble

    val sx = worldToScreenX(projX, projY, camOffX)
    val sy = worldToScreenY(projX, projY, camOffY)

    val margin = 250.0
    if (sx > -margin && sx < getWidth + margin && sy > -margin && sy < getHeight + margin) {
      val phase = (animationTick + projectile.id * 13) * 0.3
      val bounce = Math.abs(Math.sin(phase * 1.5)) * 3.0
      val spinAngle = animationTick * 0.2 + projectile.id * 1.3
      val pulse = 0.85 + 0.15 * Math.sin(phase)

      val mineY = sy - bounce
      val orbR = 8.0

      // Frost danger aura — pulsing icy blue
      val dangerPulse = 0.5 + 0.5 * Math.sin(phase * 2.5)
      gc.setFill(Color.color(0.3, 0.6, 1.0, 0.06 * dangerPulse))
      gc.fillOval(sx - orbR * 3, mineY - orbR * 3, orbR * 6, orbR * 6)
      gc.setFill(Color.color(0.4, 0.7, 1.0, 0.1 * dangerPulse))
      gc.fillOval(sx - orbR * 2, mineY - orbR * 2, orbR * 4, orbR * 4)

      // Dark iron mine body
      gc.setFill(Color.color(0.25, 0.25, 0.3, 0.9))
      gc.fillOval(sx - orbR, mineY - orbR, orbR * 2, orbR * 2)

      // Icy vein lines on the mine surface
      for (v <- 0 until 4) {
        val vAngle = spinAngle + v * Math.PI / 2
        val vx = sx + Math.cos(vAngle) * orbR * 0.7
        val vy = mineY + Math.sin(vAngle) * orbR * 0.7
        gc.setStroke(Color.color(0.5, 0.8, 1.0, 0.6 * pulse))
        gc.setLineWidth(1.5)
        gc.strokeLine(sx, mineY, vx, vy)
      }

      // Outline
      gc.setStroke(Color.color(0.15, 0.15, 0.2, 0.9))
      gc.setLineWidth(1.5)
      gc.strokeOval(sx - orbR, mineY - orbR, orbR * 2, orbR * 2)

      // Frost crystal highlight on top
      gc.setFill(Color.color(0.7, 0.9, 1.0, 0.5 * pulse))
      gc.fillOval(sx - 3, mineY - orbR + 2, 6, 4)

      // Spinning frost spikes protruding outward
      for (spike <- 0 until 6) {
        val spikeAngle = spinAngle * 1.5 + spike * Math.PI / 3
        val innerR = orbR * 0.9
        val outerR = orbR * 1.5
        val ix = sx + Math.cos(spikeAngle) * innerR
        val iy = mineY + Math.sin(spikeAngle) * innerR * 0.6
        val ox = sx + Math.cos(spikeAngle) * outerR
        val oy = mineY + Math.sin(spikeAngle) * outerR * 0.6
        gc.setStroke(Color.color(0.5, 0.8, 1.0, 0.7 * pulse))
        gc.setLineWidth(2.0)
        gc.strokeLine(ix, iy, ox, oy)
      }
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
        // Trigger screen shake on first frame of explosion
        if (now - startTime < 50) triggerShake(4.0, 400)
        drawExplosionEffect(sx, sy, startTime, pType)
      }
    }
  }

  private def drawExplosionEffect(screenX: Double, screenY: Double, startTime: Long, projectileType: Byte): Unit = {
    val elapsed = System.currentTimeMillis() - startTime
    if (elapsed < 0 || elapsed > EXPLOSION_ANIMATION_MS) return

    if (projectileType == ProjectileType.MIASMA || projectileType == ProjectileType.BLIGHT_BOMB) {
      drawToxicExplosionEffect(screenX, screenY, elapsed)
      return
    }

    val progress = elapsed.toDouble / EXPLOSION_ANIMATION_MS
    val fadeOut = 1.0 - progress

    val blastRadius = ProjectileDef.get(projectileType).explosionConfig.map(_.blastRadius.toDouble).getOrElse(3.0)
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

  private def drawToxicExplosionEffect(screenX: Double, screenY: Double, elapsed: Long): Unit = {
    val progress = elapsed.toDouble / EXPLOSION_ANIMATION_MS
    val fadeOut = 1.0 - progress
    val maxScreenRadius = 5.0 * HW * 1.5

    // Phase 1: Sickly green flash (0-15%)
    if (progress < 0.15) {
      val flashPct = progress / 0.15
      val flashR = maxScreenRadius * 0.7 * flashPct
      val flashAlpha = 0.7 * (1.0 - flashPct)
      gc.setFill(Color.color(0.6, 1.0, 0.3, flashAlpha))
      gc.fillOval(screenX - flashR, screenY - flashR * 0.5, flashR * 2, flashR)
      gc.setFill(Color.color(0.8, 1.0, 0.5, flashAlpha * 0.5))
      gc.fillOval(screenX - flashR * 0.4, screenY - flashR * 0.2, flashR * 0.8, flashR * 0.4)
    }

    // Phase 2: Expanding toxic cloud
    val cloudR = maxScreenRadius * Math.min(1.0, progress * 2.0)

    // Outer dark haze
    gc.setFill(Color.color(0.15, 0.3, 0.05, 0.1 * fadeOut))
    gc.fillOval(screenX - cloudR * 1.6, screenY - cloudR * 0.8, cloudR * 3.2, cloudR * 1.6)

    // Outer cloud — dark green
    gc.setFill(Color.color(0.2, 0.45, 0.08, 0.2 * fadeOut))
    gc.fillOval(screenX - cloudR * 1.3, screenY - cloudR * 0.65, cloudR * 2.6, cloudR * 1.3)

    // Main cloud — sickly green
    gc.setFill(Color.color(0.3, 0.65, 0.12, 0.3 * fadeOut))
    gc.fillOval(screenX - cloudR, screenY - cloudR * 0.5, cloudR * 2, cloudR)

    // Inner cloud — bright toxic green
    val innerR = cloudR * 0.65 * fadeOut
    gc.setFill(Color.color(0.45, 0.8, 0.15, 0.4 * fadeOut))
    gc.fillOval(screenX - innerR, screenY - innerR * 0.5, innerR * 2, innerR)

    // Bright green-yellow core
    val coreR = cloudR * 0.35 * fadeOut
    gc.setFill(Color.color(0.6, 0.9, 0.25, 0.5 * fadeOut))
    gc.fillOval(screenX - coreR, screenY - coreR * 0.5, coreR * 2, coreR)

    // Purple-tinged shockwave rings
    val ringR = maxScreenRadius * progress * 1.3
    gc.setStroke(Color.color(0.4, 0.8, 0.2, 0.4 * fadeOut))
    gc.setLineWidth(3.0 * fadeOut)
    gc.strokeOval(screenX - ringR, screenY - ringR * 0.5, ringR * 2, ringR)

    if (progress > 0.1) {
      val ring2R = maxScreenRadius * (progress - 0.1) / 0.9 * 1.1
      gc.setStroke(Color.color(0.5, 0.3, 0.6, 0.25 * fadeOut))
      gc.setLineWidth(2.0 * fadeOut)
      gc.strokeOval(screenX - ring2R, screenY - ring2R * 0.5, ring2R * 2, ring2R)
    }

    // Toxic particles — green droplets and purple wisps
    val particleCount = 14
    for (i <- 0 until particleCount) {
      val angle = i * (2 * Math.PI / particleCount) + i * 0.5
      val speed = 0.5 + (i % 4) * 0.15
      val dist = progress * maxScreenRadius * speed
      val rise = progress * progress * 12.0 * (0.7 + (i % 3) * 0.2)
      val px = screenX + dist * Math.cos(angle)
      val py = screenY + dist * Math.sin(angle) * 0.5 - rise
      val pSize = (2.5 + (i % 3) * 1.5) * fadeOut

      if (i % 3 == 0) {
        // Bright toxic green droplet
        gc.setFill(Color.color(0.4, 0.9, 0.2, 0.65 * fadeOut))
      } else if (i % 3 == 1) {
        // Dark green glob
        gc.setFill(Color.color(0.2, 0.55, 0.1, 0.55 * fadeOut))
      } else {
        // Purple miasma wisp
        gc.setFill(Color.color(0.45, 0.25, 0.55, 0.4 * fadeOut))
      }
      gc.fillOval(px - pSize, py - pSize, pSize * 2, pSize * 2)
    }

    // Rising toxic smoke (late phase)
    if (progress > 0.25) {
      val smokeProgress = (progress - 0.25) / 0.75
      for (s <- 0 until 5) {
        val smokeT = smokeProgress * (0.6 + s * 0.1)
        val smokeX = screenX + Math.sin(s * 2.1 + progress * 3) * maxScreenRadius * 0.35
        val smokeY = screenY - smokeT * 30.0
        val smokeAlpha = Math.max(0.0, 0.25 * (1.0 - smokeT))
        val smokeSize = 5.0 + smokeT * 10.0
        if (s % 2 == 0) {
          gc.setFill(Color.color(0.2, 0.35, 0.1, smokeAlpha))
        } else {
          gc.setFill(Color.color(0.35, 0.2, 0.4, smokeAlpha * 0.7))
        }
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
    if (player.isRooted) drawRootedEffect(screenX, spriteCenter)
    if (player.isBurning) drawBurnEffect(screenX, spriteCenter)
    if (player.hasSpeedBoost) drawSpeedBoostEffect(screenX, spriteCenter)

    // Draw hit effect on top of sprite
    drawHitEffect(screenX, spriteCenter, client.getPlayerHitTime(playerId))

    // Draw health bar above player
    drawHealthBar(screenX, spriteY, player.getHealth, player.getMaxHealth, player.getTeamId)
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

    // Dash afterimage trail when swooping
    if (client.isSwooping) {
      val swoopProg = client.getSwoopProgress
      val sx0 = client.getSwoopStartX.toDouble
      val sy0 = client.getSwoopStartY.toDouble
      val sx1 = client.getSwoopTargetX.toDouble
      val sy1 = client.getSwoopTargetY.toDouble
      val ghostSprite = SpriteGenerator.getSprite(client.getLocalColorRGB, client.getLocalDirection, (animationTick / FRAMES_PER_STEP) % 4, client.selectedCharacterId)
      val ghostCount = 5
      for (i <- 0 until ghostCount) {
        val ghostT = swoopProg - (i + 1) * 0.12
        if (ghostT > 0 && ghostT < 1.0) {
          val gx = sx0 + (sx1 - sx0) * ghostT
          val gy = sy0 + (sy1 - sy0) * ghostT
          val gsx = worldToScreenX(gx, gy, camOffX)
          val gsy = worldToScreenY(gx, gy, camOffY)
          val alpha = 0.25 - i * 0.04
          val scale = 1.0 - i * 0.04
          val gSize = displaySz * scale
          gc.setGlobalAlpha(Math.max(0.03, alpha))
          gc.drawImage(ghostSprite, gsx - gSize / 2.0, gsy - gSize, gSize, gSize)
        }
      }
      gc.setGlobalAlpha(if (localIsPhased) 0.4 else 1.0)

      // Speed lines radiating in movement direction
      val dx = sx1 - sx0
      val dy = sy1 - sy0
      val dist = Math.sqrt(dx * dx + dy * dy)
      if (dist > 0.1) {
        val ndx = dx / dist
        val ndy = dy / dist
        // Convert direction to screen-space
        val sdx = (ndx - ndy) * HW
        val sdy = (ndx + ndy) * HH
        val sdist = Math.sqrt(sdx * sdx + sdy * sdy)
        if (sdist > 0.1) {
          val snx = sdx / sdist
          val sny = sdy / sdist
          val perpX = -sny
          val perpY = snx
          gc.setStroke(Color.color(1.0, 1.0, 1.0, 0.3))
          gc.setLineWidth(1.2)
          for (i <- 0 until 6) {
            val spread = (i - 2.5) * 4.0
            val lx = screenX - snx * 12 + perpX * spread
            val ly = spriteCenter - sny * 12 + perpY * spread
            gc.strokeLine(lx, ly, lx - snx * (8 + (i % 3) * 4), ly - sny * (8 + (i % 3) * 4))
          }
        }
      }
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

    // Ability cast flash effect
    drawCastFlash(screenX, spriteCenter)

    // Draw frozen effect
    if (client.isFrozen) drawFrozenEffect(screenX, spriteCenter)
    if (client.isRooted) drawRootedEffect(screenX, spriteCenter)
    if (client.isBurning) drawBurnEffect(screenX, spriteCenter)
    if (client.hasSpeedBoost) drawSpeedBoostEffect(screenX, spriteCenter)

    // Draw hit effect on top of sprite — trigger light screen shake for local player hits
    val localHitTime = client.getPlayerHitTime(client.getLocalPlayerId)
    if (localHitTime > 0 && System.currentTimeMillis() - localHitTime < 50) {
      triggerShake(2.5, 250)
    }
    drawHitEffect(screenX, spriteCenter, localHitTime)

    // Draw health bar above local player
    drawHealthBar(screenX, spriteY, client.getLocalHealth, client.getSelectedCharacterMaxHealth, client.localTeamId)
  }

  private val CAST_FLASH_MS = 200

  private def drawCastFlash(centerX: Double, centerY: Double): Unit = {
    val castTime = client.getLastCastTime
    if (castTime <= 0) return
    val elapsed = System.currentTimeMillis() - castTime
    if (elapsed < 0 || elapsed > CAST_FLASH_MS) return

    val progress = elapsed.toDouble / CAST_FLASH_MS
    val fadeOut = 1.0 - progress

    // Get screen-space direction from world aim direction
    val wdx = client.getLastCastDirX.toDouble
    val wdy = client.getLastCastDirY.toDouble
    val sdx = (wdx - wdy) * HW
    val sdy = (wdx + wdy) * HH
    val sdist = Math.sqrt(sdx * sdx + sdy * sdy)
    if (sdist < 0.01) return
    val snx = sdx / sdist
    val sny = sdy / sdist
    val perpX = -sny
    val perpY = snx

    // Player color for tinting
    val rgb = client.getLocalColorRGB
    val pr = ((rgb >> 16) & 0xFF) / 255.0
    val pg = ((rgb >> 8) & 0xFF) / 255.0
    val pb = (rgb & 0xFF) / 255.0

    // Directional cone flash — bright white fading to player color
    val coneLen = 16.0 + progress * 8.0
    val coneWidth = 10.0 * fadeOut
    val coneAlpha = 0.5 * fadeOut
    val cx = centerX + snx * 8
    val cy = centerY + sny * 8
    val tipX = cx + snx * coneLen
    val tipY = cy + sny * coneLen
    val leftX = cx + perpX * coneWidth
    val leftY = cy + perpY * coneWidth
    val rightX = cx - perpX * coneWidth
    val rightY = cy - perpY * coneWidth
    gc.setGlobalAlpha(coneAlpha)
    gc.setFill(Color.color(
      Math.min(1.0, 0.7 + pr * 0.3),
      Math.min(1.0, 0.7 + pg * 0.3),
      Math.min(1.0, 0.7 + pb * 0.3)
    ))
    gc.fillPolygon(Array(tipX, leftX, rightX), Array(tipY, leftY, rightY), 3)
    gc.setGlobalAlpha(1.0)

    // Energy sparks radiating outward from player in cast direction
    val sparkCount = 7
    for (i <- 0 until sparkCount) {
      val angle = (i.toDouble / sparkCount - 0.5) * 1.2
      val cos = Math.cos(angle)
      val sin = Math.sin(angle)
      val sparkDx = snx * cos - sny * sin
      val sparkDy = snx * sin + sny * cos
      val sparkDist = (6.0 + progress * 22.0) * (0.7 + (i % 3) * 0.2)
      val sx = centerX + sparkDx * sparkDist
      val sy = centerY + sparkDy * sparkDist
      val sparkSize = 2.0 * fadeOut
      val sparkAlpha = 0.6 * fadeOut * (0.5 + 0.5 * ((i + 1) % 2))
      gc.setFill(Color.color(
        Math.min(1.0, 0.8 + pr * 0.2),
        Math.min(1.0, 0.8 + pg * 0.2),
        Math.min(1.0, 0.5 + pb * 0.5),
        sparkAlpha
      ))
      gc.fillOval(sx - sparkSize, sy - sparkSize, sparkSize * 2, sparkSize * 2)
    }

    // Expanding ring at player feet
    val ringRadius = 4.0 + progress * 14.0
    val ringAlpha = 0.4 * fadeOut
    gc.setStroke(Color.color(pr, pg, pb, ringAlpha))
    gc.setLineWidth(1.5 * fadeOut)
    gc.strokeOval(centerX - ringRadius, centerY - ringRadius * 0.5, ringRadius * 2, ringRadius)
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

  private def drawBurnEffect(centerX: Double, centerY: Double): Unit = {
    val phase = animationTick * 0.15
    val displaySz = Constants.PLAYER_DISPLAY_SIZE_PX
    val radius = displaySz * 0.45

    // Flickering orange-red glow
    val flicker = 0.7 + 0.3 * Math.sin(phase * 3.0)

    // Warm overlay
    gc.setFill(Color.color(1.0, 0.3, 0.0, 0.15 * flicker))
    gc.fillOval(centerX - radius, centerY - radius, radius * 2, radius * 2)

    // Flame tongues — 5 small fire wisps rising upward
    for (i <- 0 until 5) {
      val t = ((animationTick * 0.04 + i * 0.2) % 1.0)
      val fx = centerX + Math.sin(phase * 1.5 + i * 1.3) * radius * 0.7
      val fy = centerY + radius * 0.3 - t * radius * 1.2
      val fAlpha = Math.max(0.0, 0.6 * (1.0 - t) * flicker)
      val size = 2.0 + (1.0 - t) * 3.0
      // Gradient from yellow core to red tip
      val red = 1.0
      val green = Math.max(0.0, 0.7 - t * 0.7)
      gc.setFill(Color.color(red, green, 0.0, fAlpha))
      gc.fillOval(fx - size / 2, fy - size / 2, size, size)
    }

    // Ember sparkle ring
    gc.setStroke(Color.color(1.0, 0.5, 0.0, 0.35 * flicker))
    gc.setLineWidth(1.5)
    val points = 8
    val rx = new Array[Double](points)
    val ry = new Array[Double](points)
    for (i <- 0 until points) {
      val angle = i * (Math.PI * 2 / points) + phase * 0.5
      val jag = radius * (0.8 + 0.15 * Math.sin(phase * 4 + i * 2))
      rx(i) = centerX + jag * Math.cos(angle)
      ry(i) = centerY + jag * Math.sin(angle) * 0.6
    }
    gc.strokePolygon(rx, ry, points)
  }

  private def drawSpeedBoostEffect(centerX: Double, centerY: Double): Unit = {
    val phase = animationTick * 0.2
    val displaySz = Constants.PLAYER_DISPLAY_SIZE_PX
    val radius = displaySz * 0.4

    // Cyan-green speed aura
    val pulse = 0.8 + 0.2 * Math.sin(phase * 2.0)
    gc.setFill(Color.color(0.2, 1.0, 0.4, 0.12 * pulse))
    gc.fillOval(centerX - radius * 1.2, centerY - radius * 0.6, radius * 2.4, radius * 1.2)

    // Speed lines trailing behind
    for (i <- 0 until 4) {
      val t = ((animationTick * 0.05 + i * 0.25) % 1.0)
      val lx = centerX + Math.sin(phase + i * 1.6) * radius * 0.5
      val ly = centerY - radius * 0.3 + t * radius * 0.8
      val lAlpha = Math.max(0.0, 0.5 * (1.0 - t) * pulse)
      gc.setStroke(Color.color(0.3, 1.0, 0.5, lAlpha))
      gc.setLineWidth(1.5)
      gc.strokeLine(lx - 4, ly, lx + 4, ly)
    }

    // Orbiting spark
    val sparkAngle = phase * 3.0
    val sx = centerX + radius * 0.7 * Math.cos(sparkAngle)
    val sy = centerY + radius * 0.35 * Math.sin(sparkAngle)
    gc.setFill(Color.color(0.4, 1.0, 0.6, 0.7 * pulse))
    gc.fillOval(sx - 2, sy - 2, 4, 4)
  }

  private def drawRootedEffect(centerX: Double, centerY: Double): Unit = {
    val phase = animationTick * 0.15
    val displaySz = Constants.PLAYER_DISPLAY_SIZE_PX
    val radius = displaySz * 0.45

    // Earthy brown-green aura pulsing at the player's feet
    val pulse = 0.7 + 0.3 * Math.sin(phase * 2.5)
    gc.setFill(Color.color(0.4, 0.3, 0.1, 0.15 * pulse))
    gc.fillOval(centerX - radius * 1.1, centerY - radius * 0.3, radius * 2.2, radius * 0.9)

    // Vine tendrils wrapping upward
    for (i <- 0 until 5) {
      val angle = phase + i * (2.0 * Math.PI / 5.0)
      val t = ((animationTick * 0.03 + i * 0.2) % 1.0)
      val vx = centerX + Math.cos(angle) * radius * 0.6
      val vy = centerY + radius * 0.2 - t * radius * 0.7
      val vAlpha = Math.max(0.0, 0.6 * (1.0 - t) * pulse)
      gc.setStroke(Color.color(0.3, 0.5, 0.15, vAlpha))
      gc.setLineWidth(2.0)
      val curl = Math.sin(angle * 2.0 + phase) * 3.0
      gc.strokeLine(vx, vy, vx + curl, vy - 4)
    }

    // Small root nodes at the base
    for (i <- 0 until 3) {
      val nodeAngle = phase * 0.5 + i * (2.0 * Math.PI / 3.0)
      val nx = centerX + Math.cos(nodeAngle) * radius * 0.5
      val ny = centerY + radius * 0.1
      gc.setFill(Color.color(0.5, 0.35, 0.1, 0.5 * pulse))
      gc.fillOval(nx - 2, ny - 2, 4, 4)
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
        // Trigger screen shake on first frame of death
        if (now - deathTime < 50) triggerShake(6.0, 500)
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

  private def drawHealthBar(screenCenterX: Double, spriteTopY: Double, health: Int, maxHealth: Int = Constants.MAX_HEALTH, teamId: Byte = 0): Unit = {
    val barWidth = Constants.HEALTH_BAR_WIDTH_PX
    val barHeight = Constants.HEALTH_BAR_HEIGHT_PX
    val barX = screenCenterX - barWidth / 2.0
    val barY = spriteTopY - Constants.HEALTH_BAR_OFFSET_Y - barHeight

    // Dark red background (empty health)
    gc.setFill(Color.DARKRED)
    gc.fillRect(barX, barY, barWidth, barHeight)

    // Fill color based on team
    val fillColor: Color = teamId match {
      case 1 => Color.web("#4a82ff") // blue
      case 2 => Color.web("#e84057") // red
      case 3 => Color.LIMEGREEN     // green
      case 4 => Color.web("#f1c40f") // yellow
      case _ => Color.LIMEGREEN     // FFA default
    }

    // Health fill
    val healthPercentage = health.toDouble / maxHealth
    val fillWidth = barWidth * healthPercentage
    gc.setFill(fillColor)
    gc.fillRect(barX, barY, fillWidth, barHeight)

    // Black border
    gc.setStroke(Color.BLACK)
    gc.setLineWidth(1)
    gc.strokeRect(barX, barY, barWidth, barHeight)

    // Team indicator diamond below health bar
    if (teamId != 0) {
      val indicatorSize = 4.0
      val indicatorY = barY + barHeight + 2.0
      gc.setFill(fillColor)
      gc.fillPolygon(
        Array(screenCenterX, screenCenterX + indicatorSize, screenCenterX, screenCenterX - indicatorSize),
        Array(indicatorY, indicatorY + indicatorSize, indicatorY + indicatorSize * 2, indicatorY + indicatorSize),
        4
      )
    }
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

    gc.setFont(javafx.scene.text.Font.font("Arial", javafx.scene.text.FontWeight.BOLD, 15))

    val worldText = s"World: ${world.name}"
    val coordText = s"Position: (${localPos.getX}, ${localPos.getY})"
    val playersText = s"Players: ${playerCount + 1}"
    val healthText = s"Health: $health/${client.getSelectedCharacterMaxHealth}"
    val inventoryText = s"Items: ${client.getInventoryCount}"

    drawOutlinedText(worldText, 10, 24)
    drawOutlinedText(coordText, 10, 44)
    drawOutlinedText(playersText, 10, 64)
    drawOutlinedText(healthText, 10, 84)
    drawOutlinedText(inventoryText, 10, 104)

    // Show active effects
    val effects = Seq(
      if (client.hasShield) Some("Shield") else None,
      if (client.hasGemBoost) Some("FastShot") else None
    ).flatten
    if (effects.nonEmpty) {
      drawOutlinedText(s"Effects: ${effects.mkString(" ")}", 10, 124)
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
      (charDef.qAbility.keybind, client.getQCooldownFraction, client.getQCooldownRemaining, Color.color(0.2, 0.8, 0.3), Color.color(0.1, 0.5, 0.15), charDef.qAbility.name),
      (charDef.eAbility.keybind, client.getECooldownFraction, client.getECooldownRemaining, Color.color(0.4, 0.7, 1.0), Color.color(0.15, 0.35, 0.6), charDef.eAbility.name)
    )

    for (i <- abilities.indices) {
      val (key, cooldownFrac, cooldownSec, readyColor, dimColor, abilityName) = abilities(i)
      val slotX = inventoryStartX - (abilities.length - i) * (slotSize + slotGap) - abilityGap

      val onCooldown = cooldownFrac > 0.001f

      // Background
      gc.setFill(Color.rgb(0, 0, 0, 0.5))
      gc.fillRect(slotX, startY, slotSize, slotSize)

      // Ability icon
      val iconColor = if (onCooldown) dimColor else readyColor
      drawAbilityIcon(abilityName, slotX + slotSize / 2.0, startY + slotSize / 2.0 - 2, (slotSize - 12) / 2.0, iconColor)

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
      gc.setFont(javafx.scene.text.Font.font("Arial", javafx.scene.text.FontWeight.BOLD, 11))
      gc.setFill(Color.WHITE)
      gc.fillText(key, slotX + slotSize - 11, startY + slotSize - 4)
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
        gc.setFont(javafx.scene.text.Font.font("Arial", javafx.scene.text.FontWeight.BOLD, 11))
        gc.setFill(Color.WHITE)
        gc.fillText(countStr, badgeX + badgeW / 2 - countStr.length * 3, badgeY + 11)
      }

      // Key number label (bottom-center)
      gc.setFont(javafx.scene.text.Font.font("Arial", javafx.scene.text.FontWeight.BOLD, 11))
      gc.setFill(if (count > 0) Color.rgb(230, 230, 230) else Color.rgb(110, 110, 120))
      gc.fillText(keyLabel, slotX + slotSize / 2 - 3, startY + slotSize - 3)
    }
  }

  private def drawChargeBar(): Unit = {
    if (!client.isCharging) return
    val cpt = client.getSelectedCharacterDef.primaryProjectileType
    if (!ProjectileDef.get(cpt).chargeSpeedScaling.isDefined) return

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
    gc.setFont(javafx.scene.text.Font.font("Arial", javafx.scene.text.FontWeight.BOLD, 12))
    gc.setFill(Color.WHITE)
    val text = s"$chargeLevel%"
    gc.fillText(text, barX + barWidth / 2 - 12, barY - 4)
  }

  private def drawLobbyHUD(): Unit = {
    if (client.clientState != ClientState.PLAYING) return

    // Timer: top-center
    val remaining = client.gameTimeRemaining
    val minutes = remaining / 60
    val seconds = remaining % 60
    val timerText = f"$minutes%d:$seconds%02d"
    gc.setFont(javafx.scene.text.Font.font("Arial", javafx.scene.text.FontWeight.BOLD, 30))
    drawOutlinedText(timerText, getWidth / 2 - 36, 34)

    // Kill/Death counter: below coordinates
    gc.setFont(javafx.scene.text.Font.font("Arial", javafx.scene.text.FontWeight.BOLD, 16))
    drawOutlinedText(s"K: ${client.killCount}  D: ${client.deathCount}", 10, 148)

    // Kill feed: top-right corner, last 5 entries
    val now = System.currentTimeMillis()
    gc.setFont(javafx.scene.text.Font.font("Arial", javafx.scene.text.FontWeight.BOLD, 14))
    import scala.jdk.CollectionConverters._
    var feedY = 24.0
    client.killFeed.asScala.foreach { entry =>
      val timestamp = entry(0).asInstanceOf[java.lang.Long].longValue()
      val elapsed = now - timestamp
      if (elapsed < 6000) {
        val alpha = Math.max(0.15, 1.0 - elapsed / 6000.0)
        val killer = entry(1).asInstanceOf[String]
        val victim = entry(2).asInstanceOf[String]
        val feedText = s"$killer killed $victim"
        gc.setGlobalAlpha(alpha)
        drawOutlinedText(feedText, getWidth - 220, feedY)
        gc.setGlobalAlpha(1.0)
        feedY += 20
      }
    }
  }

  private def drawOutlinedText(text: String, x: Double, y: Double): Unit = {
    gc.setStroke(Color.rgb(0, 0, 0, 0.9))
    gc.setLineWidth(4)
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
    val zoom = Constants.CAMERA_ZOOM
    val vx = if (visualX.isNaN) client.getLocalPosition.getX.toDouble else visualX
    val vy = if (visualY.isNaN) client.getLocalPosition.getY.toDouble else visualY
    val playerSx = (vx - vy) * HW
    val playerSy = (vx + vy) * HH
    val camOffX = (getWidth / zoom) / 2.0 - playerSx
    val camOffY = (getHeight / zoom) / 2.0 - playerSy
    (camOffX, camOffY)
  }

  /** Convert screen coordinates to world coordinates. Used by MouseHandler. */
  def screenToWorld(sx: Double, sy: Double): (Double, Double) = {
    val zoom = Constants.CAMERA_ZOOM
    val (camOffX, camOffY) = getCameraOffsets
    // Convert screen pixels to virtual (zoomed) coordinates
    val vsx = sx / zoom
    val vsy = sy / zoom
    (screenToWorldX(vsx, vsy, camOffX, camOffY), screenToWorldY(vsx, vsy, camOffX, camOffY))
  }

  private def drawKatanaProjectile(projectile: Projectile, camOffX: Double, camOffY: Double): Unit = {
    val projX = projectile.getX.toDouble
    val projY = projectile.getY.toDouble
    val beamLength = 2.0

    val tailX = worldToScreenX(projX, projY, camOffX)
    val tailY = worldToScreenY(projX, projY, camOffY)
    val tipWX = projX + projectile.dx * beamLength
    val tipWY = projY + projectile.dy * beamLength
    val tipX = worldToScreenX(tipWX, tipWY, camOffX)
    val tipY = worldToScreenY(tipWX, tipWY, camOffY)

    val margin = 250.0
    if (Math.max(tailX, tipX) > -margin && Math.min(tailX, tipX) < getWidth + margin &&
        Math.max(tailY, tipY) > -margin && Math.min(tailY, tipY) < getHeight + margin) {

      gc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND)
      val phase = (animationTick + projectile.id * 37) * 0.5
      val pulse = 0.85 + 0.15 * Math.sin(phase)

      val midX = (tailX + tipX) / 2
      val midY = (tailY + tipY) / 2
      val dx = tipX - tailX
      val dy = tipY - tailY
      val len = Math.sqrt(dx * dx + dy * dy)
      if (len > 1) {
        val nx = -dy / len
        val ny = dx / len

        // Steel flash — wide impact glow
        gc.setFill(Color.color(0.7, 0.75, 0.85, 0.1 * pulse))
        val flashR = len * 0.7
        gc.fillOval(midX - flashR, midY - flashR * 0.5, flashR * 2, flashR)

        // Curved katana slash arc — single sweeping blade
        val arcSegments = 10
        val arcPoints = (0 to arcSegments).map { seg =>
          val t = seg.toDouble / arcSegments
          val arcBow = Math.sin(t * Math.PI) * 18.0
          val cx = tailX + dx * t + nx * arcBow
          val cy = tailY + dy * t + ny * arcBow
          (cx, cy)
        }

        // Outer steel glow
        gc.setStroke(Color.color(0.6, 0.65, 0.8, 0.15 * pulse))
        gc.setLineWidth(12.0 * pulse)
        for (i <- 0 until arcPoints.length - 1) {
          gc.strokeLine(arcPoints(i)._1, arcPoints(i)._2, arcPoints(i + 1)._1, arcPoints(i + 1)._2)
        }

        // Steel blade edge
        gc.setStroke(Color.color(0.8, 0.85, 0.95, 0.7 * pulse))
        gc.setLineWidth(4.0 * pulse)
        for (i <- 0 until arcPoints.length - 1) {
          gc.strokeLine(arcPoints(i)._1, arcPoints(i)._2, arcPoints(i + 1)._1, arcPoints(i + 1)._2)
        }

        // White-hot cutting edge
        gc.setStroke(Color.color(1.0, 1.0, 1.0, 0.9 * pulse))
        gc.setLineWidth(1.5)
        for (i <- 0 until arcPoints.length - 1) {
          gc.strokeLine(arcPoints(i)._1, arcPoints(i)._2, arcPoints(i + 1)._1, arcPoints(i + 1)._2)
        }

        // Katana tip point
        val lastPt = arcPoints.last
        val prevPt = arcPoints(arcPoints.length - 2)
        val tipDx = lastPt._1 - prevPt._1
        val tipDy = lastPt._2 - prevPt._2
        val tipLen = Math.sqrt(tipDx * tipDx + tipDy * tipDy)
        if (tipLen > 0.5) {
          val tnx = tipDx / tipLen
          val tny = tipDy / tipLen
          gc.setStroke(Color.color(1.0, 1.0, 1.0, 0.85 * pulse))
          gc.setLineWidth(2.5)
          gc.strokeLine(lastPt._1, lastPt._2, lastPt._1 + tnx * 6, lastPt._2 + tny * 6)
        }

        // Spark particles along the slash
        for (i <- 0 until 4) {
          val t = ((animationTick * 0.07 + i.toDouble / 4 + projectile.id * 0.11) % 1.0)
          val sparkX = midX + nx * Math.sin(phase + i * 1.8) * 12.0 + dx * (t - 0.5) * 0.3
          val sparkY = midY + ny * Math.sin(phase + i * 1.8) * 12.0 + dy * (t - 0.5) * 0.3
          val sAlpha = Math.max(0.0, 0.6 * (1.0 - t))
          gc.setFill(Color.color(0.9, 0.92, 1.0, sAlpha))
          val sparkR = 2.0 * (1.0 - t)
          gc.fillOval(sparkX - sparkR, sparkY - sparkR, sparkR * 2, sparkR * 2)
        }

        // Speed lines — steel streaks
        for (s <- 0 until 2) {
          val slashT = 0.3 + s * 0.4
          val sx = tailX + dx * slashT
          val sy = tailY + dy * slashT
          val slashLen = 14.0
          gc.setStroke(Color.color(0.85, 0.88, 1.0, 0.12 * pulse))
          gc.setLineWidth(1.0)
          gc.strokeLine(sx + nx * slashLen, sy + ny * slashLen,
                        sx - nx * slashLen, sy - ny * slashLen)
        }
      }
    }
  }

  private def drawSwordWaveProjectile(projectile: Projectile, camOffX: Double, camOffY: Double): Unit = {
    val projX = projectile.getX.toDouble
    val projY = projectile.getY.toDouble
    val beamLength = 1.8

    val tailX = worldToScreenX(projX, projY, camOffX)
    val tailY = worldToScreenY(projX, projY, camOffY)
    val tipWX = projX + projectile.dx * beamLength
    val tipWY = projY + projectile.dy * beamLength
    val tipX = worldToScreenX(tipWX, tipWY, camOffX)
    val tipY = worldToScreenY(tipWX, tipWY, camOffY)

    val margin = 250.0
    if (Math.max(tailX, tipX) > -margin && Math.min(tailX, tipX) < getWidth + margin &&
        Math.max(tailY, tipY) > -margin && Math.min(tailY, tipY) < getHeight + margin) {

      gc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND)
      val phase = (animationTick + projectile.id * 23) * 0.45
      val pulse = 0.8 + 0.2 * Math.sin(phase)

      val midX = (tailX + tipX) / 2
      val midY = (tailY + tipY) / 2
      val dx = tipX - tailX
      val dy = tipY - tailY
      val len = Math.sqrt(dx * dx + dy * dy)
      if (len > 1) {
        val nx = -dy / len
        val ny = dx / len

        // Indigo energy glow behind the crescent
        gc.setFill(Color.color(0.3, 0.2, 0.6, 0.08 * pulse))
        val glowR = len * 0.9
        gc.fillOval(midX - glowR, midY - glowR * 0.5, glowR * 2, glowR)

        // Crescent wave — curved blade shape bowing outward
        val crescentSegments = 10
        val crescentPoints = (0 to crescentSegments).map { seg =>
          val t = seg.toDouble / crescentSegments
          val bow = Math.sin(t * Math.PI) * 14.0
          val cx = tailX + dx * t + nx * bow
          val cy = tailY + dy * t + ny * bow
          (cx, cy)
        }

        // Outer indigo glow
        gc.setStroke(Color.color(0.4, 0.3, 0.7, 0.2 * pulse))
        gc.setLineWidth(10.0 * pulse)
        for (i <- 0 until crescentPoints.length - 1) {
          gc.strokeLine(crescentPoints(i)._1, crescentPoints(i)._2, crescentPoints(i + 1)._1, crescentPoints(i + 1)._2)
        }

        // Bright crescent edge
        gc.setStroke(Color.color(0.6, 0.5, 0.9, 0.65 * pulse))
        gc.setLineWidth(4.0 * pulse)
        for (i <- 0 until crescentPoints.length - 1) {
          gc.strokeLine(crescentPoints(i)._1, crescentPoints(i)._2, crescentPoints(i + 1)._1, crescentPoints(i + 1)._2)
        }

        // White core edge
        gc.setStroke(Color.color(0.9, 0.85, 1.0, 0.85 * pulse))
        gc.setLineWidth(1.5)
        for (i <- 0 until crescentPoints.length - 1) {
          gc.strokeLine(crescentPoints(i)._1, crescentPoints(i)._2, crescentPoints(i + 1)._1, crescentPoints(i + 1)._2)
        }

        // Inner crescent (thinner, opposite bow for crescent shape)
        val innerPoints = (0 to crescentSegments).map { seg =>
          val t = seg.toDouble / crescentSegments
          val bow = Math.sin(t * Math.PI) * 6.0
          val cx = tailX + dx * t + nx * bow
          val cy = tailY + dy * t + ny * bow
          (cx, cy)
        }
        gc.setStroke(Color.color(0.5, 0.4, 0.8, 0.3 * pulse))
        gc.setLineWidth(2.0)
        for (i <- 0 until innerPoints.length - 1) {
          gc.strokeLine(innerPoints(i)._1, innerPoints(i)._2, innerPoints(i + 1)._1, innerPoints(i + 1)._2)
        }

        // Energy particles trailing the wave
        for (i <- 0 until 5) {
          val t = ((animationTick * 0.06 + i.toDouble / 5 + projectile.id * 0.15) % 1.0)
          val pX = midX + nx * Math.sin(phase + i * 1.5) * 10.0 + dx * (t - 0.5) * 0.4
          val pY = midY + ny * Math.sin(phase + i * 1.5) * 10.0 + dy * (t - 0.5) * 0.4 + t * 5.0
          val pAlpha = Math.max(0.0, 0.5 * (1.0 - t))
          gc.setFill(Color.color(0.5, 0.4, 0.9, pAlpha))
          val pR = 2.5 * (1.0 - t * 0.5)
          gc.fillOval(pX - pR, pY - pR, pR * 2, pR * 2)
        }
      }
    }
  }

  // === Vampire projectile renderers ===

  private def drawBloodFangProjectile(projectile: Projectile, camOffX: Double, camOffY: Double): Unit = {
    // Very short-range bite/drain — blood swirling inward toward the projectile tip
    val projX = projectile.getX.toDouble
    val projY = projectile.getY.toDouble

    val sx = worldToScreenX(projX, projY, camOffX)
    val sy = worldToScreenY(projX, projY, camOffY)

    val margin = 250.0
    if (sx > -margin && sx < getWidth + margin && sy > -margin && sy < getHeight + margin) {
      val phase = (animationTick + projectile.id * 29) * 0.45
      val pulse = 0.8 + 0.2 * Math.sin(phase)
      val fastFlicker = 0.85 + 0.15 * Math.sin(phase * 3.1)

      // Dark crimson aura — the drain field
      gc.setFill(Color.color(0.6, 0.0, 0.05, 0.08 * pulse))
      gc.fillOval(sx - 28, sy - 28, 56, 56)
      gc.setFill(Color.color(0.7, 0.0, 0.08, 0.12 * pulse))
      gc.fillOval(sx - 18, sy - 18, 36, 36)

      // Blood particles spiraling inward — the draining effect
      for (i <- 0 until 10) {
        val t = ((animationTick * 0.08 + i.toDouble / 10 + projectile.id * 0.11) % 1.0)
        val spiralAngle = phase * 2.5 + i * (Math.PI * 2 / 10) + t * Math.PI * 3
        val spiralDist = (1.0 - t) * 22.0
        val px = sx + spiralDist * Math.cos(spiralAngle)
        val py = sy + spiralDist * Math.sin(spiralAngle) * 0.6 // isometric squash
        val pAlpha = Math.max(0.0, Math.min(1.0, 0.7 * t * fastFlicker))
        val pSize = 1.5 + t * 2.5
        gc.setFill(Color.color(0.8, 0.05, 0.1, pAlpha))
        gc.fillOval(px - pSize, py - pSize, pSize * 2, pSize * 2)

        // Blood droplet trail behind each particle
        val trailDist = spiralDist + 5.0
        val trailX = sx + trailDist * Math.cos(spiralAngle - 0.3)
        val trailY = sy + trailDist * Math.sin(spiralAngle - 0.3) * 0.6
        val trailAlpha = pAlpha * 0.4
        gc.setFill(Color.color(0.5, 0.0, 0.05, trailAlpha))
        gc.fillOval(trailX - pSize * 0.6, trailY - pSize * 0.6, pSize * 1.2, pSize * 1.2)
      }

      // Converging blood streams — tendrils pulling inward
      for (strand <- 0 until 4) {
        val angle = phase * 0.8 + strand * (Math.PI / 2)
        val segments = 6
        gc.setLineWidth(2.0)
        gc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND)
        var prevX = sx + 20.0 * Math.cos(angle)
        var prevY = sy + 20.0 * Math.sin(angle) * 0.6
        for (seg <- 1 to segments) {
          val st = seg.toDouble / segments
          val dist = 20.0 * (1.0 - st)
          val wobble = Math.sin(phase * 3.0 + strand * 2.0 + st * Math.PI) * 4.0 * (1.0 - st)
          val perpAngle = angle + Math.PI / 2
          val cx = sx + dist * Math.cos(angle) + wobble * Math.cos(perpAngle)
          val cy = sy + dist * Math.sin(angle) * 0.6 + wobble * Math.sin(perpAngle) * 0.6
          val sAlpha = Math.max(0.0, 0.5 * st * pulse)
          gc.setStroke(Color.color(0.75, 0.05, 0.1, sAlpha))
          gc.strokeLine(prevX, prevY, cx, cy)
          prevX = cx
          prevY = cy
        }
      }

      // Central blood orb — pulsing bright crimson core
      val orbR = 6.0 * pulse
      gc.setFill(Color.color(0.7, 0.0, 0.08, 0.4 * pulse))
      gc.fillOval(sx - orbR * 1.5, sy - orbR * 1.5, orbR * 3, orbR * 3)
      gc.setFill(Color.color(0.85, 0.1, 0.15, 0.7 * fastFlicker))
      gc.fillOval(sx - orbR, sy - orbR, orbR * 2, orbR * 2)
      gc.setFill(Color.color(1.0, 0.3, 0.25, 0.9 * fastFlicker))
      gc.fillOval(sx - orbR * 0.4, sy - orbR * 0.4, orbR * 0.8, orbR * 0.8)
    }
  }

  private def drawBloodSiphonProjectile(projectile: Projectile, camOffX: Double, camOffY: Double): Unit = {
    // Medium-range blood beam with trailing droplets
    val projX = projectile.getX.toDouble
    val projY = projectile.getY.toDouble
    val beamLength = 3.0

    val tailX = worldToScreenX(projX, projY, camOffX)
    val tailY = worldToScreenY(projX, projY, camOffY)
    val tipWX = projX + projectile.dx * beamLength
    val tipWY = projY + projectile.dy * beamLength
    val tipX = worldToScreenX(tipWX, tipWY, camOffX)
    val tipY = worldToScreenY(tipWX, tipWY, camOffY)

    val margin = 250.0
    if (Math.max(tailX, tipX) > -margin && Math.min(tailX, tipX) < getWidth + margin &&
        Math.max(tailY, tipY) > -margin && Math.min(tailY, tipY) < getHeight + margin) {

      gc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND)
      val phase = (animationTick + projectile.id * 37) * 0.35
      val pulse = 0.85 + 0.15 * Math.sin(phase)
      val fastFlicker = 0.8 + 0.2 * Math.sin(phase * 2.7)

      val dx = tipX - tailX
      val dy = tipY - tailY
      val len = Math.sqrt(dx * dx + dy * dy)
      if (len < 1) return
      val nx = dx / len
      val ny = dy / len

      // Dark crimson outer mist
      gc.setStroke(Color.color(0.5, 0.0, 0.05, 0.06 * pulse))
      gc.setLineWidth(35 * pulse)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      // Blood red glow layers
      gc.setStroke(Color.color(0.6, 0.0, 0.08, 0.15 * pulse))
      gc.setLineWidth(20 * pulse)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      gc.setStroke(Color.color(0.75, 0.05, 0.12, 0.4 * fastFlicker))
      gc.setLineWidth(10 * fastFlicker)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      // Bright crimson core
      gc.setStroke(Color.color(0.9, 0.2, 0.2, 0.85 * fastFlicker))
      gc.setLineWidth(3.5)
      gc.strokeLine(tailX, tailY, tipX, tipY)

      // Blood droplet particles along the beam
      for (i <- 0 until 8) {
        val t = ((animationTick * 0.07 + i.toDouble / 8 + projectile.id * 0.13) % 1.0)
        val wave = Math.sin(phase * 2.0 + i * 1.5) * 6.0
        val px = tailX + dx * t + (-ny) * wave
        val py = tailY + dy * t + nx * wave
        val pAlpha = Math.max(0.0, Math.min(1.0, 0.6 * (1.0 - Math.abs(t - 0.5) * 2.0) * pulse))
        val pSize = 2.0 + Math.sin(phase + i) * 1.0
        // Dark blood droplet
        gc.setFill(Color.color(0.7, 0.02, 0.08, pAlpha))
        gc.fillOval(px - pSize, py - pSize, pSize * 2, pSize * 2)
        // Bright highlight
        gc.setFill(Color.color(0.95, 0.2, 0.2, pAlpha * 0.5))
        gc.fillOval(px - pSize * 0.4, py - pSize * 0.4, pSize * 0.8, pSize * 0.8)
      }

      // Dripping blood trailing behind
      for (i <- 0 until 4) {
        val t = ((animationTick * 0.05 + i * 0.25 + projectile.id * 0.09) % 1.0)
        val dropX = tailX - dx * 0.1 * t + Math.sin(phase + i * 1.8) * 2.0
        val dropY = tailY - dy * 0.1 * t + t * 6.0  // drip downward
        val dropAlpha = Math.max(0.0, 0.35 * (1.0 - t))
        val dropSize = 1.5 + t * 1.5
        gc.setFill(Color.color(0.5, 0.0, 0.05, dropAlpha))
        gc.fillOval(dropX - dropSize, dropY - dropSize, dropSize * 2, dropSize * 2)
      }

      // Tip orb — pulsing blood orb
      val orbR = 7.0 * pulse
      gc.setFill(Color.color(0.5, 0.0, 0.05, 0.15 * pulse))
      gc.fillOval(tipX - orbR * 2, tipY - orbR * 2, orbR * 4, orbR * 4)
      gc.setFill(Color.color(0.7, 0.05, 0.1, 0.4 * pulse))
      gc.fillOval(tipX - orbR, tipY - orbR, orbR * 2, orbR * 2)
      gc.setFill(Color.color(0.95, 0.2, 0.2, 0.8 * fastFlicker))
      gc.fillOval(tipX - orbR * 0.4, tipY - orbR * 0.4, orbR * 0.8, orbR * 0.8)
    }
  }

  private def drawBatSwarmProjectile(projectile: Projectile, camOffX: Double, camOffY: Double): Unit = {
    // Dark bat-like projectile with flapping wing silhouettes
    val projX = projectile.getX.toDouble
    val projY = projectile.getY.toDouble

    val sx = worldToScreenX(projX, projY, camOffX)
    val sy = worldToScreenY(projX, projY, camOffY)

    val margin = 250.0
    if (sx > -margin && sx < getWidth + margin && sy > -margin && sy < getHeight + margin) {
      val phase = (animationTick + projectile.id * 23) * 0.5
      val pulse = 0.8 + 0.2 * Math.sin(phase)
      val flapAngle = Math.sin(phase * 4.0) * 0.5  // wing flap

      // Dark crimson aura
      gc.setFill(Color.color(0.4, 0.0, 0.05, 0.08 * pulse))
      gc.fillOval(sx - 20, sy - 20, 40, 40)

      // Calculate projectile direction for orienting the bat
      val pdx = projectile.dx.toDouble
      val pdy = projectile.dy.toDouble
      val pLen = Math.sqrt(pdx * pdx + pdy * pdy)
      val dirX = if (pLen > 0.01) pdx / pLen else 1.0
      val dirY = if (pLen > 0.01) pdy / pLen else 0.0

      // Bat body — small dark ellipse
      gc.setFill(Color.color(0.15, 0.05, 0.1, 0.85 * pulse))
      gc.fillOval(sx - 3, sy - 2, 6, 4)

      // Bat wings — two triangular shapes that flap
      val wingSpan = 8.0
      val wingLift = flapAngle * 6.0
      val perpX = -dirY  // perpendicular to flight direction
      val perpY = dirX

      // Left wing
      val lwTipX = sx + perpX * wingSpan
      val lwTipY = sy + perpY * wingSpan * 0.6 - wingLift
      val lwMidX = sx + perpX * wingSpan * 0.5 - dirX * 3
      val lwMidY = sy + perpY * wingSpan * 0.3 * 0.6 - wingLift * 0.5 - dirY * 3 * 0.6
      gc.setFill(Color.color(0.2, 0.05, 0.1, 0.75 * pulse))
      gc.fillPolygon(
        Array(sx - 1, lwMidX, lwTipX, sx - 1),
        Array(sy, lwMidY, lwTipY, sy + 1),
        4
      )

      // Right wing
      val rwTipX = sx - perpX * wingSpan
      val rwTipY = sy - perpY * wingSpan * 0.6 - wingLift
      val rwMidX = sx - perpX * wingSpan * 0.5 - dirX * 3
      val rwMidY = sy - perpY * wingSpan * 0.3 * 0.6 - wingLift * 0.5 - dirY * 3 * 0.6
      gc.setFill(Color.color(0.2, 0.05, 0.1, 0.75 * pulse))
      gc.fillPolygon(
        Array(sx + 1, rwMidX, rwTipX, sx + 1),
        Array(sy, rwMidY, rwTipY, sy + 1),
        4
      )

      // Red glowing eyes on the bat
      gc.setFill(Color.color(1.0, 0.15, 0.1, 0.9))
      gc.fillOval(sx + perpX * 1.5 + dirX * 2 - 1, sy + perpY * 1.5 * 0.6 + dirY * 2 * 0.6 - 1, 2, 2)
      gc.fillOval(sx - perpX * 1.5 + dirX * 2 - 1, sy - perpY * 1.5 * 0.6 + dirY * 2 * 0.6 - 1, 2, 2)

      // Blood mist trail behind
      for (i <- 0 until 5) {
        val t = ((animationTick * 0.09 + i * 0.2 + projectile.id * 0.11) % 1.0)
        val trailX = sx - dirX * t * 18.0 + Math.sin(phase * 2.0 + i * 1.7) * 3.0
        val trailY = sy - dirY * t * 18.0 * 0.6 + t * 4.0
        val trailAlpha = Math.max(0.0, 0.3 * (1.0 - t) * pulse)
        val trailSize = 2.0 + t * 3.0
        gc.setFill(Color.color(0.5, 0.0, 0.08, trailAlpha))
        gc.fillOval(trailX - trailSize, trailY - trailSize, trailSize * 2, trailSize * 2)
      }
    }
  }

  // ===== Batch 1: Elemental projectile renderers (types 31-52) =====

  private def drawFlameBoltProjectile(projectile: Projectile, camOffX: Double, camOffY: Double): Unit = {
    val projX = projectile.getX.toDouble
    val projY = projectile.getY.toDouble
    val beamLength = 4.0
    val tailX = worldToScreenX(projX, projY, camOffX)
    val tailY = worldToScreenY(projX, projY, camOffY)
    val tipWX = projX + projectile.dx * beamLength
    val tipWY = projY + projectile.dy * beamLength
    val tipX = worldToScreenX(tipWX, tipWY, camOffX)
    val tipY = worldToScreenY(tipWX, tipWY, camOffY)
    val margin = 250.0
    if (Math.max(tailX, tipX) > -margin && Math.min(tailX, tipX) < getWidth + margin &&
        Math.max(tailY, tipY) > -margin && Math.min(tailY, tipY) < getHeight + margin) {
      gc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND)
      val phase = (animationTick + projectile.id * 29) * 0.4
      val pulse = 0.85 + 0.15 * Math.sin(phase)
      // Outer fire glow
      gc.setStroke(Color.color(1.0, 0.3, 0.0, 0.08 * pulse))
      gc.setLineWidth(28 * pulse)
      gc.strokeLine(tailX, tailY, tipX, tipY)
      // Orange mid glow
      gc.setStroke(Color.color(1.0, 0.5, 0.0, 0.2 * pulse))
      gc.setLineWidth(14 * pulse)
      gc.strokeLine(tailX, tailY, tipX, tipY)
      // Bright yellow core
      gc.setStroke(Color.color(1.0, 0.85, 0.2, 0.7 * pulse))
      gc.setLineWidth(5)
      gc.strokeLine(tailX, tailY, tipX, tipY)
      // White-hot center
      gc.setStroke(Color.color(1.0, 1.0, 0.7, 0.9))
      gc.setLineWidth(2)
      gc.strokeLine(tailX, tailY, tipX, tipY)
      // Ember sparks
      val dx = tipX - tailX; val dy = tipY - tailY
      val len = Math.sqrt(dx * dx + dy * dy)
      if (len > 1) {
        val nx = dx / len; val ny = dy / len
        for (i <- 0 until 5) {
          val t = ((animationTick * 0.1 + i * 0.2 + projectile.id * 0.13) % 1.0)
          val sparkX = tailX + dx * t + (-ny) * Math.sin(phase * 2 + i * 1.8) * 8.0
          val sparkY = tailY + dy * t + nx * Math.sin(phase * 2 + i * 1.8) * 8.0 - t * 5.0
          val a = Math.max(0.0, Math.min(1.0, 0.8 * (1.0 - t)))
          val s = 2.0 + (1.0 - t) * 2.0
          gc.setFill(Color.color(1.0, 0.6 * (1.0 - t), 0.0, a))
          gc.fillOval(sparkX - s, sparkY - s, s * 2, s * 2)
        }
      }
    }
  }

  private def drawFrostShardProjectile(projectile: Projectile, camOffX: Double, camOffY: Double): Unit = {
    val projX = projectile.getX.toDouble
    val projY = projectile.getY.toDouble
    val sx = worldToScreenX(projX, projY, camOffX)
    val sy = worldToScreenY(projX, projY, camOffY)
    val margin = 250.0
    if (sx > -margin && sx < getWidth + margin && sy > -margin && sy < getHeight + margin) {
      val phase = (animationTick + projectile.id * 41) * 0.3
      val spin = animationTick * 0.25 + projectile.id * 2.3
      val pulse = 0.85 + 0.15 * Math.sin(phase)
      // Icy aura
      gc.setFill(Color.color(0.5, 0.8, 1.0, 0.06 * pulse))
      gc.fillOval(sx - 16, sy - 16, 32, 32)
      // Crystal diamond shape (rotating)
      val size = 8.0
      val xs = new Array[Double](4)
      val ys = new Array[Double](4)
      for (i <- 0 until 4) {
        val angle = spin + i * Math.PI / 2
        val r = if (i % 2 == 0) size else size * 0.5
        xs(i) = sx + Math.cos(angle) * r
        ys(i) = sy + Math.sin(angle) * r * 0.6
      }
      gc.setFill(Color.color(0.6, 0.85, 1.0, 0.7 * pulse))
      gc.fillPolygon(xs, ys, 4)
      gc.setStroke(Color.color(0.8, 0.95, 1.0, 0.9))
      gc.setLineWidth(1)
      gc.strokePolygon(xs, ys, 4)
      // Bright center
      gc.setFill(Color.color(0.9, 0.97, 1.0, 0.8))
      gc.fillOval(sx - 2, sy - 1.5, 4, 3)
      // Ice dust trail
      for (i <- 0 until 4) {
        val t = ((animationTick * 0.08 + i * 0.25 + projectile.id * 0.17) % 1.0)
        val pdx = projectile.dx.toDouble; val pdy = projectile.dy.toDouble
        val tX = sx - pdx * t * 20.0 + Math.sin(phase + i * 2.0) * 4.0
        val tY = sy - pdy * t * 12.0 + Math.cos(phase + i * 2.0) * 2.0
        val a = Math.max(0.0, 0.4 * (1.0 - t) * pulse)
        val s = 1.5 + t * 2.0
        gc.setFill(Color.color(0.7, 0.9, 1.0, a))
        gc.fillOval(tX - s, tY - s, s * 2, s * 2)
      }
    }
  }

  private def drawLightningProjectile(projectile: Projectile, camOffX: Double, camOffY: Double): Unit = {
    val projX = projectile.getX.toDouble
    val projY = projectile.getY.toDouble
    val beamLength = 5.0
    val tailX = worldToScreenX(projX, projY, camOffX)
    val tailY = worldToScreenY(projX, projY, camOffY)
    val tipWX = projX + projectile.dx * beamLength
    val tipWY = projY + projectile.dy * beamLength
    val tipX = worldToScreenX(tipWX, tipWY, camOffX)
    val tipY = worldToScreenY(tipWX, tipWY, camOffY)
    val margin = 250.0
    if (Math.max(tailX, tipX) > -margin && Math.min(tailX, tipX) < getWidth + margin &&
        Math.max(tailY, tipY) > -margin && Math.min(tailY, tipY) < getHeight + margin) {
      gc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND)
      val phase = (animationTick + projectile.id * 37) * 0.6
      val flicker = 0.7 + 0.3 * Math.sin(phase * 7.0)
      val dx = tipX - tailX; val dy = tipY - tailY
      val len = Math.sqrt(dx * dx + dy * dy)
      if (len < 1) return
      val nx = dx / len; val ny = dy / len
      // Yellow glow
      gc.setStroke(Color.color(1.0, 1.0, 0.3, 0.06 * flicker))
      gc.setLineWidth(30)
      gc.strokeLine(tailX, tailY, tipX, tipY)
      // Zigzag bolt segments
      val segs = 8
      val zigX = new Array[Double](segs + 1)
      val zigY = new Array[Double](segs + 1)
      zigX(0) = tailX; zigY(0) = tailY
      zigX(segs) = tipX; zigY(segs) = tipY
      for (i <- 1 until segs) {
        val t = i.toDouble / segs
        val jitter = Math.sin(phase * 3.0 + i * 4.7) * 10.0 * flicker
        zigX(i) = tailX + dx * t + (-ny) * jitter
        zigY(i) = tailY + dy * t + nx * jitter
      }
      // Outer glow
      gc.setStroke(Color.color(1.0, 1.0, 0.4, 0.3 * flicker))
      gc.setLineWidth(8)
      for (i <- 0 until segs) gc.strokeLine(zigX(i), zigY(i), zigX(i + 1), zigY(i + 1))
      // Mid glow
      gc.setStroke(Color.color(1.0, 1.0, 0.6, 0.5 * flicker))
      gc.setLineWidth(4)
      for (i <- 0 until segs) gc.strokeLine(zigX(i), zigY(i), zigX(i + 1), zigY(i + 1))
      // Core bolt
      gc.setStroke(Color.color(1.0, 1.0, 0.85, 0.95 * flicker))
      gc.setLineWidth(2.5)
      for (i <- 0 until segs) gc.strokeLine(zigX(i), zigY(i), zigX(i + 1), zigY(i + 1))

      // Branching side bolts
      for (branch <- 0 until 4) {
        val branchSeg = 1 + branch * 2
        if (branchSeg < segs) {
          val bx = zigX(branchSeg)
          val by = zigY(branchSeg)
          val bAngle = Math.atan2(ny, nx) + Math.PI / 2.0 + Math.sin(phase * 2.0 + branch * 3.7) * 0.8
          val bLen = 18.0 + 12.0 * Math.sin(phase * 3.0 + branch * 2.1)
          val bMidX = bx + Math.cos(bAngle) * bLen * 0.5 + Math.sin(phase * 5.0 + branch) * 5.0
          val bMidY = by + Math.sin(bAngle) * bLen * 0.5 + Math.cos(phase * 5.0 + branch) * 5.0
          val bEndX = bx + Math.cos(bAngle) * bLen
          val bEndY = by + Math.sin(bAngle) * bLen
          gc.setStroke(Color.color(1.0, 1.0, 0.5, 0.4 * flicker))
          gc.setLineWidth(2.0)
          gc.strokeLine(bx, by, bMidX, bMidY)
          gc.strokeLine(bMidX, bMidY, bEndX, bEndY)
        }
      }

      // Spark particles at zigzag joints
      for (i <- 1 until segs) {
        val sparkPulse = Math.sin(phase * 8.0 + i * 3.1)
        if (sparkPulse > 0.2) {
          val sparkR = 4.0 * sparkPulse
          gc.setFill(Color.color(1.0, 1.0, 0.9, 0.7 * sparkPulse * flicker))
          gc.fillOval(zigX(i) - sparkR, zigY(i) - sparkR, sparkR * 2, sparkR * 2)
        }
      }

      // Bright flash at tip
      val tipFlash = 0.5 + 0.5 * Math.sin(phase * 6.0)
      val flashR = 10.0 * tipFlash * flicker
      gc.setFill(Color.color(1.0, 1.0, 0.8, 0.25 * tipFlash))
      gc.fillOval(tipX - flashR, tipY - flashR, flashR * 2, flashR * 2)
      gc.setFill(Color.color(1.0, 1.0, 0.95, 0.6 * tipFlash * flicker))
      gc.fillOval(tipX - flashR * 0.3, tipY - flashR * 0.3, flashR * 0.6, flashR * 0.6)
    }
  }

  private def drawChainLightningProjectile(projectile: Projectile, camOffX: Double, camOffY: Double): Unit = {
    val projX = projectile.getX.toDouble
    val projY = projectile.getY.toDouble
    val beamLength = 4.0
    val tailX = worldToScreenX(projX, projY, camOffX)
    val tailY = worldToScreenY(projX, projY, camOffY)
    val tipWX = projX + projectile.dx * beamLength
    val tipWY = projY + projectile.dy * beamLength
    val tipX = worldToScreenX(tipWX, tipWY, camOffX)
    val tipY = worldToScreenY(tipWX, tipWY, camOffY)
    val margin = 250.0
    if (Math.max(tailX, tipX) > -margin && Math.min(tailX, tipX) < getWidth + margin &&
        Math.max(tailY, tipY) > -margin && Math.min(tailY, tipY) < getHeight + margin) {
      gc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND)
      val phase = (animationTick + projectile.id * 43) * 0.5
      val flicker = 0.7 + 0.3 * Math.sin(phase * 6.0)
      val dx = tipX - tailX; val dy = tipY - tailY
      val len = Math.sqrt(dx * dx + dy * dy)
      if (len < 1) return
      val nx = dx / len; val ny = dy / len
      // Purple-blue glow
      gc.setStroke(Color.color(0.6, 0.3, 1.0, 0.06 * flicker))
      gc.setLineWidth(26)
      gc.strokeLine(tailX, tailY, tipX, tipY)
      // Main arc + 2 forking arcs
      for (arc <- 0 until 3) {
        val segs = 6
        val arcX = new Array[Double](segs + 1)
        val arcY = new Array[Double](segs + 1)
        arcX(0) = tailX; arcY(0) = tailY
        val forkOff = (arc - 1) * 12.0
        arcX(segs) = tipX + (-ny) * forkOff
        arcY(segs) = tipY + nx * forkOff
        for (i <- 1 until segs) {
          val t = i.toDouble / segs
          val jitter = Math.sin(phase * 4.0 + i * 3.1 + arc * 2.0) * 8.0 * flicker
          arcX(i) = tailX + (arcX(segs) - tailX) * t + (-ny) * jitter
          arcY(i) = tailY + (arcY(segs) - tailY) * t + nx * jitter
        }
        val alpha = if (arc == 1) 0.8 else 0.4
        gc.setStroke(Color.color(0.6, 0.4, 1.0, alpha * flicker))
        gc.setLineWidth(if (arc == 1) 3.5 else 1.5)
        for (i <- 0 until segs) gc.strokeLine(arcX(i), arcY(i), arcX(i + 1), arcY(i + 1))
      }
      // White core on main arc
      gc.setStroke(Color.color(0.9, 0.8, 1.0, 0.9 * flicker))
      gc.setLineWidth(1.5)
      gc.strokeLine(tailX, tailY, tipX, tipY)
    }
  }

  private def drawThunderStrikeProjectile(projectile: Projectile, camOffX: Double, camOffY: Double): Unit = {
    val projX = projectile.getX.toDouble
    val projY = projectile.getY.toDouble
    val sx = worldToScreenX(projX, projY, camOffX)
    val sy = worldToScreenY(projX, projY, camOffY)
    val margin = 250.0
    if (sx > -margin && sx < getWidth + margin && sy > -margin && sy < getHeight + margin) {
      val phase = (animationTick + projectile.id * 31) * 0.5
      val flicker = 0.7 + 0.3 * Math.sin(phase * 8.0)
      // Radial lightning flash
      gc.setFill(Color.color(1.0, 0.95, 0.4, 0.05 * flicker))
      gc.fillOval(sx - 30, sy - 30, 60, 60)
      // Thunderbolt shape - zigzag down from above
      val boltX = new Array[Double](7)
      val boltY = new Array[Double](7)
      boltX(0) = sx - 2; boltY(0) = sy - 18
      boltX(1) = sx + 4; boltY(1) = sy - 8
      boltX(2) = sx - 1; boltY(2) = sy - 6
      boltX(3) = sx + 5; boltY(3) = sy + 4
      boltX(4) = sx + 1; boltY(4) = sy + 2
      boltX(5) = sx + 7; boltY(5) = sy + 12
      boltX(6) = sx; boltY(6) = sy + 12
      // Golden glow
      gc.setStroke(Color.color(1.0, 0.85, 0.2, 0.4 * flicker))
      gc.setLineWidth(6)
      gc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND)
      for (i <- 0 until 6) gc.strokeLine(boltX(i), boltY(i), boltX(i + 1), boltY(i + 1))
      // Bright core
      gc.setStroke(Color.color(1.0, 1.0, 0.7, 0.9 * flicker))
      gc.setLineWidth(2.5)
      for (i <- 0 until 6) gc.strokeLine(boltX(i), boltY(i), boltX(i + 1), boltY(i + 1))
      // Impact sparks
      for (i <- 0 until 4) {
        val angle = phase * 2 + i * Math.PI / 2
        val dist = 6.0 + Math.sin(phase * 3 + i) * 4.0
        val spX = sx + Math.cos(angle) * dist
        val spY = sy + 10 + Math.sin(angle) * dist * 0.4
        gc.setFill(Color.color(1.0, 1.0, 0.5, 0.6 * flicker))
        gc.fillOval(spX - 1.5, spY - 1.5, 3, 3)
      }
    }
  }

  private def drawBoulderProjectile(projectile: Projectile, camOffX: Double, camOffY: Double): Unit = {
    val projX = projectile.getX.toDouble
    val projY = projectile.getY.toDouble
    val sx = worldToScreenX(projX, projY, camOffX)
    val sy = worldToScreenY(projX, projY, camOffY)
    val margin = 250.0
    if (sx > -margin && sx < getWidth + margin && sy > -margin && sy < getHeight + margin) {
      val phase = (animationTick + projectile.id * 19) * 0.3
      val spin = animationTick * 0.15 + projectile.id * 1.3
      val bounce = Math.abs(Math.sin(phase)) * 3.0
      val r = 9.0
      // Shadow on ground
      gc.setFill(Color.color(0.0, 0.0, 0.0, 0.15))
      gc.fillOval(sx - r, sy + r * 0.3, r * 2, r * 0.6)
      // Rock body
      gc.setFill(Color.color(0.45, 0.35, 0.25, 0.9))
      gc.fillOval(sx - r, sy - bounce - r, r * 2, r * 2)
      // Craggy highlights
      gc.setFill(Color.color(0.6, 0.5, 0.35, 0.5))
      gc.fillOval(sx - r * 0.5 + Math.cos(spin) * 3, sy - bounce - r * 0.7, r * 0.7, r * 0.5)
      // Dark cracks
      gc.setStroke(Color.color(0.25, 0.18, 0.12, 0.6))
      gc.setLineWidth(1)
      gc.strokeLine(sx - 3, sy - bounce - 2, sx + 4, sy - bounce + 3)
      gc.strokeLine(sx + 1, sy - bounce - 5, sx - 2, sy - bounce + 1)
      // Dust trail
      for (i <- 0 until 4) {
        val t = ((animationTick * 0.08 + i * 0.25 + projectile.id * 0.11) % 1.0)
        val pdx = projectile.dx.toDouble; val pdy = projectile.dy.toDouble
        val dustX = sx - pdx * t * 16.0 + Math.sin(phase + i * 2.0) * 3.0
        val dustY = sy - pdy * t * 10.0 + t * 6.0
        val a = Math.max(0.0, 0.25 * (1.0 - t))
        val s = 2.5 + t * 3.0
        gc.setFill(Color.color(0.5, 0.4, 0.3, a))
        gc.fillOval(dustX - s, dustY - s, s * 2, s * 2)
      }
    }
  }

  private def drawSeismicSlamProjectile(projectile: Projectile, camOffX: Double, camOffY: Double): Unit = {
    val projX = projectile.getX.toDouble
    val projY = projectile.getY.toDouble
    val sx = worldToScreenX(projX, projY, camOffX)
    val sy = worldToScreenY(projX, projY, camOffY)
    val margin = 250.0
    if (sx > -margin && sx < getWidth + margin && sy > -margin && sy < getHeight + margin) {
      val phase = (animationTick + projectile.id * 23) * 0.4
      val expand = 0.6 + 0.4 * Math.sin(phase * 2.0)
      // Expanding brown shockwave rings
      for (ring <- 0 until 3) {
        val ringPhase = ((phase * 0.5 + ring * 0.33) % 1.0)
        val ringR = 8.0 + ringPhase * 25.0
        val ringA = Math.max(0.0, 0.3 * (1.0 - ringPhase))
        gc.setStroke(Color.color(0.55, 0.4, 0.2, ringA))
        gc.setLineWidth(3.0 * (1.0 - ringPhase))
        gc.strokeOval(sx - ringR, sy - ringR * 0.5, ringR * 2, ringR)
      }
      // Center impact
      gc.setFill(Color.color(0.5, 0.35, 0.15, 0.4 * expand))
      gc.fillOval(sx - 6, sy - 3, 12, 6)
      // Debris chunks
      for (i <- 0 until 5) {
        val angle = phase + i * Math.PI * 2 / 5
        val dist = 5.0 + expand * 10.0
        val cx = sx + Math.cos(angle) * dist
        val cy = sy + Math.sin(angle) * dist * 0.5
        gc.setFill(Color.color(0.4, 0.3, 0.2, 0.5 * (1.0 - expand * 0.5)))
        gc.fillRect(cx - 1.5, cy - 1.5, 3, 3)
      }
    }
  }

  private def drawWindBladeProjectile(projectile: Projectile, camOffX: Double, camOffY: Double): Unit = {
    val projX = projectile.getX.toDouble
    val projY = projectile.getY.toDouble
    val sx = worldToScreenX(projX, projY, camOffX)
    val sy = worldToScreenY(projX, projY, camOffY)
    val margin = 250.0
    if (sx > -margin && sx < getWidth + margin && sy > -margin && sy < getHeight + margin) {
      val phase = (animationTick + projectile.id * 47) * 0.4
      val pulse = 0.8 + 0.2 * Math.sin(phase)
      val pdx = projectile.dx.toDouble; val pdy = projectile.dy.toDouble
      val pLen = Math.sqrt(pdx * pdx + pdy * pdy)
      val dirX = if (pLen > 0.01) pdx / pLen else 1.0
      val dirY = if (pLen > 0.01) pdy / pLen else 0.0
      val perpSX = -dirY; val perpSY = dirX
      // Translucent crescent blade
      val tipX = sx + dirX * 12.0
      val tipY = sy + dirY * 7.0
      val backX = sx - dirX * 4.0
      val backY = sy - dirY * 2.5
      val wing1X = sx + perpSX * 10.0
      val wing1Y = sy + perpSY * 6.0
      val wing2X = sx - perpSX * 10.0
      val wing2Y = sy - perpSY * 6.0
      gc.setFill(Color.color(0.8, 0.95, 1.0, 0.2 * pulse))
      gc.fillPolygon(Array(tipX, wing1X, backX, wing2X), Array(tipY, wing1Y, backY, wing2Y), 4)
      gc.setStroke(Color.color(0.9, 1.0, 1.0, 0.5 * pulse))
      gc.setLineWidth(1.5)
      gc.strokePolygon(Array(tipX, wing1X, backX, wing2X), Array(tipY, wing1Y, backY, wing2Y), 4)
      // Wind streaks
      for (i <- 0 until 3) {
        val offset = (i - 1) * 5.0
        val streakA = 0.15 * pulse
        gc.setStroke(Color.color(0.85, 0.95, 1.0, streakA))
        gc.setLineWidth(1)
        gc.strokeLine(sx + perpSX * offset - dirX * 8, sy + perpSY * offset * 0.6 - dirY * 5,
                      sx + perpSX * offset + dirX * 8, sy + perpSY * offset * 0.6 + dirY * 5)
      }
    }
  }

  private def drawMagmaBallProjectile(projectile: Projectile, camOffX: Double, camOffY: Double): Unit = {
    val projX = projectile.getX.toDouble
    val projY = projectile.getY.toDouble
    val sx = worldToScreenX(projX, projY, camOffX)
    val sy = worldToScreenY(projX, projY, camOffY)
    val margin = 250.0
    if (sx > -margin && sx < getWidth + margin && sy > -margin && sy < getHeight + margin) {
      val phase = (animationTick + projectile.id * 31) * 0.35
      val pulse = 0.85 + 0.15 * Math.sin(phase)
      val r = 8.0
      // Heat glow
      gc.setFill(Color.color(1.0, 0.3, 0.0, 0.08 * pulse))
      gc.fillOval(sx - r * 3, sy - r * 3, r * 6, r * 6)
      gc.setFill(Color.color(1.0, 0.5, 0.0, 0.12 * pulse))
      gc.fillOval(sx - r * 2, sy - r * 2, r * 4, r * 4)
      // Dark rock shell
      gc.setFill(Color.color(0.3, 0.15, 0.05, 0.85))
      gc.fillOval(sx - r, sy - r, r * 2, r * 2)
      // Lava cracks (glowing orange lines)
      gc.setStroke(Color.color(1.0, 0.6, 0.0, 0.8 * pulse))
      gc.setLineWidth(1.5)
      gc.strokeLine(sx - 4, sy - 2, sx + 3, sy + 4)
      gc.strokeLine(sx + 2, sy - 5, sx - 1, sy + 2)
      gc.strokeLine(sx - 5, sy + 1, sx + 5, sy - 1)
      // Bright magma center
      gc.setFill(Color.color(1.0, 0.8, 0.2, 0.6 * pulse))
      gc.fillOval(sx - 3, sy - 3, 6, 6)
      // Dripping lava drops
      for (i <- 0 until 3) {
        val t = ((animationTick * 0.07 + i * 0.33 + projectile.id * 0.17) % 1.0)
        val dripX = sx + Math.sin(phase + i * 2.1) * 5.0
        val dripY = sy + t * 15.0
        val a = Math.max(0.0, 0.6 * (1.0 - t))
        gc.setFill(Color.color(1.0, 0.4, 0.0, a))
        gc.fillOval(dripX - 1.5, dripY - 1.5, 3, 3)
      }
    }
  }

  private def drawEruptionProjectile(projectile: Projectile, camOffX: Double, camOffY: Double): Unit = {
    val projX = projectile.getX.toDouble
    val projY = projectile.getY.toDouble
    val sx = worldToScreenX(projX, projY, camOffX)
    val sy = worldToScreenY(projX, projY, camOffY)
    val margin = 250.0
    if (sx > -margin && sx < getWidth + margin && sy > -margin && sy < getHeight + margin) {
      val phase = (animationTick + projectile.id * 29) * 0.4
      val expand = 0.5 + 0.5 * Math.sin(phase * 1.5)
      // Volcanic blast rings
      for (ring <- 0 until 3) {
        val rp = ((phase * 0.4 + ring * 0.33) % 1.0)
        val rr = 6.0 + rp * 28.0
        val ra = Math.max(0.0, 0.35 * (1.0 - rp))
        gc.setStroke(Color.color(1.0, 0.35, 0.0, ra))
        gc.setLineWidth(3.0 * (1.0 - rp))
        gc.strokeOval(sx - rr, sy - rr * 0.5, rr * 2, rr)
      }
      // Fiery center
      gc.setFill(Color.color(1.0, 0.6, 0.0, 0.5 * expand))
      gc.fillOval(sx - 8, sy - 5, 16, 10)
      gc.setFill(Color.color(1.0, 0.9, 0.3, 0.7 * expand))
      gc.fillOval(sx - 4, sy - 2.5, 8, 5)
      // Flying debris
      for (i <- 0 until 6) {
        val angle = phase * 1.5 + i * Math.PI / 3
        val dist = 8.0 + expand * 18.0
        val debrisX = sx + Math.cos(angle) * dist
        val debrisY = sy + Math.sin(angle) * dist * 0.5 - expand * 8.0
        val a = Math.max(0.0, 0.5 * (1.0 - expand * 0.5))
        gc.setFill(Color.color(0.6, 0.25, 0.05, a))
        gc.fillRect(debrisX - 2, debrisY - 2, 4, 4)
      }
    }
  }

  private def drawFrostTrapProjectile(projectile: Projectile, camOffX: Double, camOffY: Double): Unit = {
    val projX = projectile.getX.toDouble
    val projY = projectile.getY.toDouble
    val sx = worldToScreenX(projX, projY, camOffX)
    val sy = worldToScreenY(projX, projY, camOffY)
    val margin = 250.0
    if (sx > -margin && sx < getWidth + margin && sy > -margin && sy < getHeight + margin) {
      val phase = (animationTick + projectile.id * 53) * 0.25
      val spin = animationTick * 0.2 + projectile.id * 1.1
      val pulse = 0.8 + 0.2 * Math.sin(phase * 2)
      // Frosty aura
      gc.setFill(Color.color(0.4, 0.7, 1.0, 0.06 * pulse))
      gc.fillOval(sx - 18, sy - 12, 36, 24)
      // Hexagonal mine shape (spinning)
      val hexR = 7.0
      val hxs = new Array[Double](6)
      val hys = new Array[Double](6)
      for (i <- 0 until 6) {
        val angle = spin + i * Math.PI / 3
        hxs(i) = sx + Math.cos(angle) * hexR
        hys(i) = sy + Math.sin(angle) * hexR * 0.6
      }
      gc.setFill(Color.color(0.3, 0.6, 0.9, 0.6 * pulse))
      gc.fillPolygon(hxs, hys, 6)
      gc.setStroke(Color.color(0.7, 0.9, 1.0, 0.8))
      gc.setLineWidth(1.5)
      gc.strokePolygon(hxs, hys, 6)
      // Ice crystal center
      gc.setFill(Color.color(0.85, 0.95, 1.0, 0.8))
      gc.fillOval(sx - 2.5, sy - 2, 5, 4)
      // Frost particles orbiting
      for (i <- 0 until 4) {
        val angle = spin * 2 + i * Math.PI / 2
        val dist = hexR + 3.0
        val px = sx + Math.cos(angle) * dist
        val py = sy + Math.sin(angle) * dist * 0.5
        gc.setFill(Color.color(0.7, 0.9, 1.0, 0.5 * pulse))
        gc.fillOval(px - 1.5, py - 1, 3, 2)
      }
    }
  }

  private def drawSandShotProjectile(projectile: Projectile, camOffX: Double, camOffY: Double): Unit = {
    val projX = projectile.getX.toDouble; val projY = projectile.getY.toDouble
    val sx = worldToScreenX(projX, projY, camOffX)
    val sy = worldToScreenY(projX, projY, camOffY)
    val margin = 250.0
    if (sx > -margin && sx < getWidth + margin && sy > -margin && sy < getHeight + margin) {
      val phase = (animationTick + projectile.id * 37) * 0.5
      val pulse = 0.85 + 0.15 * Math.sin(phase)
      val spin = animationTick * 0.15 + projectile.id * 1.3
      val pdx = projectile.dx.toDouble; val pdy = projectile.dy.toDouble
      val pLen = Math.sqrt(pdx * pdx + pdy * pdy)
      val dirX = if (pLen > 0.01) pdx / pLen else 1.0
      val dirY = if (pLen > 0.01) pdy / pLen else 0.0
      val perpX = -dirY; val perpY = dirX
      // Layer 1: Wide sandstorm aura
      gc.setFill(Color.color(0.85, 0.72, 0.4, 0.06 * pulse))
      gc.fillOval(sx - 22, sy - 16, 44, 32)
      // Layer 2: Mid sandy glow
      gc.setFill(Color.color(0.9, 0.78, 0.45, 0.12 * pulse))
      gc.fillOval(sx - 14, sy - 10, 28, 20)
      // Swirling sand ring (6 orbiting grains)
      for (i <- 0 until 6) {
        val angle = spin * 2.5 + i * Math.PI / 3
        val dist = 8.0 + Math.sin(phase + i * 1.5) * 2.0
        val px = sx + Math.cos(angle) * dist
        val py = sy + Math.sin(angle) * dist * 0.6
        val s = 2.5 + Math.sin(phase * 2 + i) * 0.8
        gc.setFill(Color.color(0.85, 0.72, 0.4, 0.5 * pulse))
        gc.fillOval(px - s, py - s * 0.7, s * 2, s * 1.4)
      }
      // Core sand cluster - elongated in direction of travel
      val tipX = sx + dirX * 6; val tipY = sy + dirY * 3.5
      val backX = sx - dirX * 5; val backY = sy - dirY * 3
      gc.setFill(Color.color(0.82, 0.7, 0.38, 0.85 * pulse))
      gc.fillOval(sx - 6, sy - 4, 12, 8)
      gc.setFill(Color.color(0.95, 0.88, 0.6, 0.9))
      gc.fillOval(sx - 4, sy - 3, 8, 6)
      // Bright hot center
      gc.setFill(Color.color(1.0, 0.95, 0.75, 0.7 * pulse))
      gc.fillOval(sx - 2, sy - 1.5, 4, 3)
      // Sand dust trail (8 particles, larger)
      for (i <- 0 until 8) {
        val t = ((animationTick * 0.1 + i * 0.125 + projectile.id * 0.13) % 1.0)
        val spread = Math.sin(phase + i * 1.8) * 6.0
        val tX = sx - dirX * t * 22.0 + perpX * spread
        val tY = sy - dirY * t * 13.0 + perpY * spread * 0.6 + t * 4.0
        val a = Math.max(0.0, 0.4 * (1.0 - t) * pulse)
        val s = 2.0 + t * 3.5
        gc.setFill(Color.color(0.78, 0.68, 0.4, a))
        gc.fillOval(tX - s, tY - s * 0.7, s * 2, s * 1.4)
      }
    }
  }

  private def drawSandBlastProjectile(projectile: Projectile, camOffX: Double, camOffY: Double): Unit = {
    val projX = projectile.getX.toDouble; val projY = projectile.getY.toDouble
    val sx = worldToScreenX(projX, projY, camOffX)
    val sy = worldToScreenY(projX, projY, camOffY)
    val margin = 250.0
    if (sx > -margin && sx < getWidth + margin && sy > -margin && sy < getHeight + margin) {
      val phase = (animationTick + projectile.id * 41) * 0.35
      val pulse = 0.8 + 0.2 * Math.sin(phase)
      val pdx = projectile.dx.toDouble; val pdy = projectile.dy.toDouble
      val pLen = Math.sqrt(pdx * pdx + pdy * pdy)
      val dirX = if (pLen > 0.01) pdx / pLen else 1.0
      val dirY = if (pLen > 0.01) pdy / pLen else 0.0
      val perpX = -dirY; val perpY = dirX
      // Wide tan spray cone
      for (i <- 0 until 8) {
        val spread = (i - 3.5) * 3.5
        val dist = 4.0 + Math.sin(phase * 2 + i * 1.3) * 3.0
        val px = sx + dirX * dist + perpX * spread
        val py = sy + dirY * dist * 0.6 + perpY * spread * 0.6
        val a = Math.max(0.0, Math.min(1.0, 0.4 * pulse * (1.0 - Math.abs(i - 3.5) / 5.0)))
        val s = 2.5 + Math.sin(phase + i) * 1.0
        gc.setFill(Color.color(0.8, 0.7, 0.4, a))
        gc.fillOval(px - s, py - s, s * 2, s * 2)
      }
      // Center dense core
      gc.setFill(Color.color(0.85, 0.75, 0.5, 0.5 * pulse))
      gc.fillOval(sx - 5, sy - 3, 10, 6)
    }
  }

  private def drawThornProjectile(projectile: Projectile, camOffX: Double, camOffY: Double): Unit = {
    val projX = projectile.getX.toDouble; val projY = projectile.getY.toDouble
    val sx = worldToScreenX(projX, projY, camOffX)
    val sy = worldToScreenY(projX, projY, camOffY)
    val margin = 250.0
    if (sx > -margin && sx < getWidth + margin && sy > -margin && sy < getHeight + margin) {
      val phase = (animationTick + projectile.id * 29) * 0.35
      val pulse = 0.85 + 0.15 * Math.sin(phase)
      val spin = animationTick * 0.12 + projectile.id * 1.7
      val pdx = projectile.dx.toDouble; val pdy = projectile.dy.toDouble
      val pLen = Math.sqrt(pdx * pdx + pdy * pdy)
      val dirX = if (pLen > 0.01) pdx / pLen else 1.0
      val dirY = if (pLen > 0.01) pdy / pLen else 0.0
      val perpX = -dirY; val perpY = dirX
      // Layer 1: Wide nature energy aura
      gc.setFill(Color.color(0.15, 0.7, 0.1, 0.06 * pulse))
      gc.fillOval(sx - 20, sy - 15, 40, 30)
      // Layer 2: Green glow
      gc.setFill(Color.color(0.2, 0.8, 0.15, 0.12 * pulse))
      gc.fillOval(sx - 12, sy - 9, 24, 18)
      // Thorny vine trail - small barbs along path
      for (i <- 0 until 4) {
        val t = 0.15 + i * 0.2
        val bx = sx - dirX * t * 20 + perpX * Math.sin(phase + i * 2.0) * 3
        val by = sy - dirY * t * 12 + perpY * Math.sin(phase + i * 2.0) * 2
        val barbTipX = bx + perpX * 3.5 * (if (i % 2 == 0) 1 else -1)
        val barbTipY = by + perpY * 2.5 * (if (i % 2 == 0) 1 else -1)
        gc.setFill(Color.color(0.2, 0.55, 0.12, 0.5 * pulse * (1.0 - t)))
        gc.fillPolygon(Array(bx, barbTipX, bx - dirX * 3), Array(by, barbTipY, by - dirY * 2), 3)
      }
      // Main thorn spike - BIGGER (elongated diamond shape)
      val tipX = sx + dirX * 14; val tipY = sy + dirY * 8.5
      val backX = sx - dirX * 8; val backY = sy - dirY * 5
      val w1X = sx + perpX * 5; val w1Y = sy + perpY * 3
      val w2X = sx - perpX * 5; val w2Y = sy - perpY * 3
      // Dark green base
      gc.setFill(Color.color(0.15, 0.5, 0.1, 0.9 * pulse))
      gc.fillPolygon(Array(tipX, w1X, backX, w2X), Array(tipY, w1Y, backY, w2Y), 4)
      // Bright green highlight
      val hTipX = sx + dirX * 12; val hTipY = sy + dirY * 7
      val hBackX = sx - dirX * 3; val hBackY = sy - dirY * 2
      gc.setFill(Color.color(0.3, 0.75, 0.2, 0.6 * pulse))
      gc.fillPolygon(Array(hTipX, sx + perpX * 2.5, hBackX, sx - perpX * 2.5),
                     Array(hTipY, sy + perpY * 1.5, hBackY, sy - perpY * 1.5), 4)
      // Outline
      gc.setStroke(Color.color(0.08, 0.35, 0.05, 0.75))
      gc.setLineWidth(1.2)
      gc.strokePolygon(Array(tipX, w1X, backX, w2X), Array(tipY, w1Y, backY, w2Y), 4)
      // Bright tip point
      gc.setFill(Color.color(0.5, 1.0, 0.3, 0.8 * pulse))
      gc.fillOval(tipX - 2, tipY - 1.5, 4, 3)
      // Orbiting leaf particles (5 leaves)
      for (i <- 0 until 5) {
        val angle = spin * 2.0 + i * Math.PI * 2 / 5.0
        val dist = 10.0 + Math.sin(phase + i * 1.3) * 2.0
        val lx = sx + Math.cos(angle) * dist
        val ly = sy + Math.sin(angle) * dist * 0.55
        val a = 0.4 + 0.2 * Math.sin(phase * 2 + i)
        gc.setFill(Color.color(0.3, 0.75, 0.2, Math.max(0, Math.min(1, a * pulse))))
        gc.fillOval(lx - 2.5, ly - 1.5, 5, 3)
      }
      // Leaf trail (6 particles)
      for (i <- 0 until 6) {
        val t = ((animationTick * 0.08 + i * 0.167 + projectile.id * 0.19) % 1.0)
        val spread = Math.sin(phase + i * 2.5) * 5
        val lX = sx - dirX * t * 20 + perpX * spread
        val lY = sy - dirY * t * 12 + perpY * spread * 0.6 + Math.cos(phase + i * 1.7) * 3
        val a = Math.max(0.0, 0.45 * (1.0 - t) * pulse)
        val s = 2.5 + (1.0 - t) * 1.5
        gc.setFill(Color.color(0.25, 0.7, 0.2, a))
        gc.fillOval(lX - s, lY - s * 0.6, s * 2, s * 1.2)
      }
    }
  }

  private def drawVineWhipProjectile(projectile: Projectile, camOffX: Double, camOffY: Double): Unit = {
    val projX = projectile.getX.toDouble; val projY = projectile.getY.toDouble
    val beamLength = 4.0
    val tailX = worldToScreenX(projX, projY, camOffX)
    val tailY = worldToScreenY(projX, projY, camOffY)
    val tipWX = projX + projectile.dx * beamLength
    val tipWY = projY + projectile.dy * beamLength
    val tipX = worldToScreenX(tipWX, tipWY, camOffX)
    val tipY = worldToScreenY(tipWX, tipWY, camOffY)
    val margin = 250.0
    if (Math.max(tailX, tipX) > -margin && Math.min(tailX, tipX) < getWidth + margin &&
        Math.max(tailY, tipY) > -margin && Math.min(tailY, tipY) < getHeight + margin) {
      val phase = (animationTick + projectile.id * 31) * 0.4
      val dx = tipX - tailX; val dy = tipY - tailY
      val len = Math.sqrt(dx * dx + dy * dy)
      if (len < 1) return
      val nx = dx / len; val ny = dy / len
      // Sinusoidal vine
      val segs = 10
      val pts = segs + 1
      val vxs = new Array[Double](pts); val vys = new Array[Double](pts)
      for (i <- 0 to segs) {
        val t = i.toDouble / segs
        val wave = Math.sin(t * Math.PI * 3 + phase * 2) * 6.0
        vxs(i) = tailX + dx * t + (-ny) * wave
        vys(i) = tailY + dy * t + nx * wave
      }
      // Green vine body
      gc.setStroke(Color.color(0.15, 0.55, 0.1, 0.8))
      gc.setLineWidth(3.5)
      gc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND)
      for (i <- 0 until segs) gc.strokeLine(vxs(i), vys(i), vxs(i + 1), vys(i + 1))
      // Lighter highlight
      gc.setStroke(Color.color(0.3, 0.75, 0.2, 0.5))
      gc.setLineWidth(1.5)
      for (i <- 0 until segs) gc.strokeLine(vxs(i), vys(i), vxs(i + 1), vys(i + 1))
      // Thorns along the vine
      for (i <- 1 until segs by 2) {
        val thornX = vxs(i) + (-ny) * 4
        val thornY = vys(i) + nx * 4
        gc.setFill(Color.color(0.2, 0.5, 0.1, 0.7))
        gc.fillOval(thornX - 1.5, thornY - 1.5, 3, 3)
      }
    }
  }

  private def drawThornWallProjectile(projectile: Projectile, camOffX: Double, camOffY: Double): Unit = {
    val projX = projectile.getX.toDouble; val projY = projectile.getY.toDouble
    val sx = worldToScreenX(projX, projY, camOffX)
    val sy = worldToScreenY(projX, projY, camOffY)
    val margin = 250.0
    if (sx > -margin && sx < getWidth + margin && sy > -margin && sy < getHeight + margin) {
      val phase = (animationTick + projectile.id * 37) * 0.3
      val pulse = 0.8 + 0.2 * Math.sin(phase)
      // Dense thorny cluster
      for (i <- 0 until 7) {
        val angle = i * Math.PI * 2 / 7 + phase * 0.3
        val dist = 4.0 + Math.sin(phase + i * 1.5) * 2.0
        val tx = sx + Math.cos(angle) * dist
        val ty = sy + Math.sin(angle) * dist * 0.6
        // Thorn spike
        val spikeAngle = angle + Math.PI * 0.5
        val tipPx = tx + Math.cos(spikeAngle) * 5.0
        val tipPy = ty + Math.sin(spikeAngle) * 3.0
        gc.setStroke(Color.color(0.2, 0.5, 0.1, 0.7 * pulse))
        gc.setLineWidth(2)
        gc.strokeLine(tx, ty, tipPx, tipPy)
      }
      // Central mass
      gc.setFill(Color.color(0.15, 0.45, 0.1, 0.6 * pulse))
      gc.fillOval(sx - 5, sy - 3, 10, 6)
      gc.setFill(Color.color(0.25, 0.6, 0.15, 0.4 * pulse))
      gc.fillOval(sx - 3, sy - 2, 6, 4)
    }
  }

  private def drawInfernoBlastProjectile(projectile: Projectile, camOffX: Double, camOffY: Double): Unit = {
    val projX = projectile.getX.toDouble; val projY = projectile.getY.toDouble
    val sx = worldToScreenX(projX, projY, camOffX)
    val sy = worldToScreenY(projX, projY, camOffY)
    val margin = 250.0
    if (sx > -margin && sx < getWidth + margin && sy > -margin && sy < getHeight + margin) {
      val phase = (animationTick + projectile.id * 23) * 0.4
      val pulse = 0.8 + 0.2 * Math.sin(phase)
      val r = 12.0
      // Massive fire aura
      gc.setFill(Color.color(1.0, 0.2, 0.0, 0.06 * pulse))
      gc.fillOval(sx - r * 3, sy - r * 2.5, r * 6, r * 5)
      gc.setFill(Color.color(1.0, 0.4, 0.0, 0.1 * pulse))
      gc.fillOval(sx - r * 2, sy - r * 1.8, r * 4, r * 3.5)
      // Fire body
      gc.setFill(Color.color(1.0, 0.5, 0.0, 0.6 * pulse))
      gc.fillOval(sx - r, sy - r * 0.8, r * 2, r * 1.6)
      gc.setFill(Color.color(1.0, 0.8, 0.2, 0.7 * pulse))
      gc.fillOval(sx - r * 0.6, sy - r * 0.5, r * 1.2, r)
      // White-hot core
      gc.setFill(Color.color(1.0, 1.0, 0.7, 0.8))
      gc.fillOval(sx - 3, sy - 2, 6, 4)
      // Flame tongues
      for (i <- 0 until 6) {
        val angle = phase * 1.5 + i * Math.PI / 3
        val fl = r + Math.sin(phase * 3 + i * 2) * 5.0
        val fx = sx + Math.cos(angle) * fl
        val fy = sy + Math.sin(angle) * fl * 0.6 - 4.0
        gc.setFill(Color.color(1.0, 0.5, 0.0, 0.4 * pulse))
        gc.fillOval(fx - 3, fy - 3, 6, 6)
      }
    }
  }

  private def drawGlacierSpikeProjectile(projectile: Projectile, camOffX: Double, camOffY: Double): Unit = {
    val projX = projectile.getX.toDouble; val projY = projectile.getY.toDouble
    val sx = worldToScreenX(projX, projY, camOffX)
    val sy = worldToScreenY(projX, projY, camOffY)
    val margin = 250.0
    if (sx > -margin && sx < getWidth + margin && sy > -margin && sy < getHeight + margin) {
      val phase = (animationTick + projectile.id * 43) * 0.3
      val pulse = 0.85 + 0.15 * Math.sin(phase)
      val pdx = projectile.dx.toDouble; val pdy = projectile.dy.toDouble
      val pLen = Math.sqrt(pdx * pdx + pdy * pdy)
      val dirX = if (pLen > 0.01) pdx / pLen else 1.0
      val dirY = if (pLen > 0.01) pdy / pLen else 0.0
      val perpX = -dirY; val perpY = dirX
      // Icy glow
      gc.setFill(Color.color(0.5, 0.8, 1.0, 0.08 * pulse))
      gc.fillOval(sx - 20, sy - 14, 40, 28)
      // Large icy lance shape
      val tipPx = sx + dirX * 16; val tipPy = sy + dirY * 10
      val backPx = sx - dirX * 8; val backPy = sy - dirY * 5
      val w1x = sx + perpX * 5; val w1y = sy + perpY * 3
      val w2x = sx - perpX * 5; val w2y = sy - perpY * 3
      gc.setFill(Color.color(0.55, 0.8, 1.0, 0.65 * pulse))
      gc.fillPolygon(Array(tipPx, w1x, backPx, w2x), Array(tipPy, w1y, backPy, w2y), 4)
      gc.setStroke(Color.color(0.8, 0.95, 1.0, 0.8))
      gc.setLineWidth(1)
      gc.strokePolygon(Array(tipPx, w1x, backPx, w2x), Array(tipPy, w1y, backPy, w2y), 4)
      // Inner highlight
      gc.setFill(Color.color(0.85, 0.95, 1.0, 0.5))
      gc.fillPolygon(
        Array(tipPx, sx + perpX * 2, sx - dirX * 3, sx - perpX * 2),
        Array(tipPy, sy + perpY * 1.2, sy - dirY * 2, sy - perpY * 1.2), 4)
      // Frost particles
      for (i <- 0 until 5) {
        val t = ((animationTick * 0.07 + i * 0.2 + projectile.id * 0.13) % 1.0)
        val fx = sx - dirX * t * 18 + Math.sin(phase + i * 2.1) * 5
        val fy = sy - dirY * t * 11 + t * 4
        val a = Math.max(0.0, 0.35 * (1.0 - t) * pulse)
        gc.setFill(Color.color(0.7, 0.9, 1.0, a))
        gc.fillOval(fx - 2, fy - 1.5, 4, 3)
      }
    }
  }

  private def drawMudGlobProjectile(projectile: Projectile, camOffX: Double, camOffY: Double): Unit = {
    val projX = projectile.getX.toDouble; val projY = projectile.getY.toDouble
    val sx = worldToScreenX(projX, projY, camOffX)
    val sy = worldToScreenY(projX, projY, camOffY)
    val margin = 250.0
    if (sx > -margin && sx < getWidth + margin && sy > -margin && sy < getHeight + margin) {
      val phase = (animationTick + projectile.id * 19) * 0.35
      val pulse = 0.85 + 0.15 * Math.sin(phase * 2)
      val wobble = Math.sin(phase * 3) * 2.5
      val r = 10.0
      // Layer 1: Wide murky aura
      gc.setFill(Color.color(0.35, 0.25, 0.1, 0.06 * pulse))
      gc.fillOval(sx - r * 2.5, sy - r * 2, r * 5, r * 4)
      // Layer 2: Brown splash ring
      gc.setFill(Color.color(0.4, 0.3, 0.12, 0.1 * pulse))
      gc.fillOval(sx - r * 1.5, sy - r * 1.2, r * 3, r * 2.4)
      // Splatter blobs orbiting the main glob
      for (i <- 0 until 5) {
        val angle = phase * 1.5 + i * Math.PI * 2 / 5.0
        val dist = r + Math.sin(phase * 2 + i * 1.9) * 3.0
        val bx = sx + Math.cos(angle) * dist + wobble * 0.3
        val by = sy + Math.sin(angle) * dist * 0.6
        val bs = 2.5 + Math.sin(phase * 3 + i * 2.1) * 1.0
        gc.setFill(Color.color(0.38, 0.28, 0.12, 0.55 * pulse))
        gc.fillOval(bx - bs, by - bs * 0.7, bs * 2, bs * 1.4)
      }
      // Main mud body - bigger, wobbling
      gc.setFill(Color.color(0.35, 0.25, 0.12, 0.85))
      gc.fillOval(sx - r + wobble, sy - r * 0.7, r * 2, r * 1.4)
      // Wet shine highlight
      gc.setFill(Color.color(0.55, 0.42, 0.22, 0.5))
      gc.fillOval(sx - r * 0.5 + wobble, sy - r * 0.5, r, r * 0.7)
      // Lighter mud rim
      gc.setFill(Color.color(0.5, 0.38, 0.18, 0.35))
      gc.fillOval(sx - r * 0.3 + wobble + 1, sy - r * 0.35, r * 0.5, r * 0.35)
      // Dripping mud trail (6 drips, bigger)
      for (i <- 0 until 6) {
        val t = ((animationTick * 0.07 + i * 0.167 + projectile.id * 0.15) % 1.0)
        val dx = Math.sin(phase + i * 1.7) * 6.0
        val dripY = sy + t * 16.0
        val a = Math.max(0.0, 0.55 * (1.0 - t) * pulse)
        val ds = 2.0 + (1.0 - t) * 2.5
        gc.setFill(Color.color(0.32, 0.22, 0.1, a))
        gc.fillOval(sx + dx - ds, dripY - ds * 0.5, ds * 2, ds)
      }
    }
  }

  private def drawMudBombProjectile(projectile: Projectile, camOffX: Double, camOffY: Double): Unit = {
    val projX = projectile.getX.toDouble; val projY = projectile.getY.toDouble
    val sx = worldToScreenX(projX, projY, camOffX)
    val sy = worldToScreenY(projX, projY, camOffY)
    val margin = 250.0
    if (sx > -margin && sx < getWidth + margin && sy > -margin && sy < getHeight + margin) {
      val phase = (animationTick + projectile.id * 23) * 0.35
      val pulse = 0.8 + 0.2 * Math.sin(phase * 2)
      val bounce = Math.abs(Math.sin(phase * 1.5)) * 3.0
      val r = 9.0
      // Danger aura
      gc.setFill(Color.color(0.5, 0.3, 0.1, 0.06 * pulse))
      gc.fillOval(sx - r * 2.5, sy - r * 2, r * 5, r * 4)
      // Dark mud body
      gc.setFill(Color.color(0.25, 0.18, 0.08, 0.85))
      gc.fillOval(sx - r, sy - bounce - r, r * 2, r * 2)
      // Mud texture
      gc.setFill(Color.color(0.4, 0.3, 0.15, 0.4))
      gc.fillOval(sx - 3, sy - bounce - r * 0.6, 6, 4)
      // Warning ring
      gc.setStroke(Color.color(0.6, 0.35, 0.1, 0.3 * pulse))
      gc.setLineWidth(1.5)
      gc.strokeOval(sx - r * 1.5, sy - bounce - r * 1.3, r * 3, r * 2.6)
    }
  }

  private def drawEmberShotProjectile(projectile: Projectile, camOffX: Double, camOffY: Double): Unit = {
    val projX = projectile.getX.toDouble; val projY = projectile.getY.toDouble
    val sx = worldToScreenX(projX, projY, camOffX)
    val sy = worldToScreenY(projX, projY, camOffY)
    val margin = 250.0
    if (sx > -margin && sx < getWidth + margin && sy > -margin && sy < getHeight + margin) {
      val phase = (animationTick + projectile.id * 47) * 0.5
      val flicker = 0.7 + 0.3 * Math.sin(phase * 6)
      val fastFlicker = 0.8 + 0.2 * Math.sin(phase * 9)
      val pdx = projectile.dx.toDouble; val pdy = projectile.dy.toDouble
      val pLen = Math.sqrt(pdx * pdx + pdy * pdy)
      val dirX = if (pLen > 0.01) pdx / pLen else 1.0
      val dirY = if (pLen > 0.01) pdy / pLen else 0.0
      val perpX = -dirY; val perpY = dirX
      // Layer 1: Wide heat shimmer
      gc.setFill(Color.color(1.0, 0.4, 0.0, 0.05 * flicker))
      gc.fillOval(sx - 24, sy - 18, 48, 36)
      // Layer 2: Orange fire glow
      gc.setFill(Color.color(1.0, 0.5, 0.0, 0.1 * flicker))
      gc.fillOval(sx - 16, sy - 12, 32, 24)
      // Layer 3: Inner fire glow
      gc.setFill(Color.color(1.0, 0.6, 0.1, 0.2 * flicker))
      gc.fillOval(sx - 10, sy - 7, 20, 14)
      // Flickering flame tongues (5 surrounding flames)
      for (i <- 0 until 5) {
        val angle = phase * 3.0 + i * Math.PI * 2.0 / 5.0
        val dist = 5.0 + Math.sin(phase * 4 + i * 2.3) * 2.0
        val fx = sx + Math.cos(angle) * dist
        val fy = sy + Math.sin(angle) * dist * 0.6 - Math.abs(Math.sin(phase * 5 + i)) * 3.0
        val fSize = 3.0 + Math.sin(phase * 6 + i * 1.7) * 1.5
        gc.setFill(Color.color(1.0, 0.55 + 0.2 * Math.sin(phase + i), 0.0, 0.5 * flicker))
        gc.fillOval(fx - fSize, fy - fSize * 0.8, fSize * 2, fSize * 1.6)
      }
      // Core ember - bright orange/yellow
      gc.setFill(Color.color(1.0, 0.7, 0.15, 0.9 * flicker))
      gc.fillOval(sx - 5, sy - 3.5, 10, 7)
      // White-hot center
      gc.setFill(Color.color(1.0, 0.95, 0.6, 0.85 * fastFlicker))
      gc.fillOval(sx - 3, sy - 2, 6, 4)
      gc.setFill(Color.color(1.0, 1.0, 0.85, 0.7))
      gc.fillOval(sx - 1.5, sy - 1, 3, 2)
      // Fire spark trail (6 ascending embers)
      for (i <- 0 until 6) {
        val t = ((animationTick * 0.1 + i * 0.167 + projectile.id * 0.17) % 1.0)
        val spread = Math.sin(phase + i * 2.3) * 5
        val tX = sx - dirX * t * 18 + perpX * spread
        val tY = sy - dirY * t * 11 + perpY * spread * 0.6 - t * 5.0
        val a = Math.max(0.0, 0.6 * (1.0 - t) * flicker)
        val s = 2.5 + (1.0 - t) * 2.0
        gc.setFill(Color.color(1.0, 0.5 * (1.0 - t), 0.0, a))
        gc.fillOval(tX - s, tY - s * 0.7, s * 2, s * 1.4)
      }
    }
  }

  private def drawAvalancheCrushProjectile(projectile: Projectile, camOffX: Double, camOffY: Double): Unit = {
    val projX = projectile.getX.toDouble; val projY = projectile.getY.toDouble
    val sx = worldToScreenX(projX, projY, camOffX)
    val sy = worldToScreenY(projX, projY, camOffY)
    val margin = 250.0
    if (sx > -margin && sx < getWidth + margin && sy > -margin && sy < getHeight + margin) {
      val phase = (animationTick + projectile.id * 17) * 0.3
      val spin = animationTick * 0.12 + projectile.id * 1.7
      val bounce = Math.abs(Math.sin(phase)) * 4.0
      val r = 12.0
      // Snow dust cloud
      gc.setFill(Color.color(0.85, 0.9, 1.0, 0.08))
      gc.fillOval(sx - r * 2.5, sy - r * 2, r * 5, r * 4)
      // Shadow
      gc.setFill(Color.color(0.0, 0.0, 0.0, 0.12))
      gc.fillOval(sx - r, sy + r * 0.3, r * 2, r * 0.5)
      // Icy boulder
      gc.setFill(Color.color(0.6, 0.75, 0.9, 0.85))
      gc.fillOval(sx - r, sy - bounce - r, r * 2, r * 2)
      // Ice cracks
      gc.setStroke(Color.color(0.8, 0.92, 1.0, 0.6))
      gc.setLineWidth(1.5)
      gc.strokeLine(sx - 5, sy - bounce - 3, sx + 4, sy - bounce + 4)
      gc.strokeLine(sx + 2, sy - bounce - 6, sx - 3, sy - bounce + 1)
      // Snow particles
      for (i <- 0 until 6) {
        val t = ((animationTick * 0.06 + i * 0.17 + projectile.id * 0.11) % 1.0)
        val angle = spin + i * Math.PI / 3
        val dist = r + t * 10
        val px = sx + Math.cos(angle) * dist
        val py = sy + Math.sin(angle) * dist * 0.5 + t * 5
        val a = Math.max(0.0, 0.4 * (1.0 - t))
        gc.setFill(Color.color(0.9, 0.95, 1.0, a))
        gc.fillOval(px - 1.5, py - 1, 3, 2)
      }
    }
  }

  // ===== Batch 2: Undead/Dark projectile renderers (types 53-69) =====

  private def drawDeathBoltProjectile(projectile: Projectile, camOffX: Double, camOffY: Double): Unit = {
    val projX = projectile.getX.toDouble; val projY = projectile.getY.toDouble
    val beamLength = 4.5
    val tailX = worldToScreenX(projX, projY, camOffX)
    val tailY = worldToScreenY(projX, projY, camOffY)
    val tipWX = projX + projectile.dx * beamLength
    val tipWY = projY + projectile.dy * beamLength
    val tipX = worldToScreenX(tipWX, tipWY, camOffX)
    val tipY = worldToScreenY(tipWX, tipWY, camOffY)
    val margin = 250.0
    if (Math.max(tailX, tipX) > -margin && Math.min(tailX, tipX) < getWidth + margin &&
        Math.max(tailY, tipY) > -margin && Math.min(tailY, tipY) < getHeight + margin) {
      gc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND)
      val phase = (animationTick + projectile.id * 31) * 0.4
      val pulse = 0.85 + 0.15 * Math.sin(phase)
      // Dark purple outer glow
      gc.setStroke(Color.color(0.3, 0.0, 0.4, 0.08 * pulse))
      gc.setLineWidth(26 * pulse)
      gc.strokeLine(tailX, tailY, tipX, tipY)
      gc.setStroke(Color.color(0.4, 0.0, 0.5, 0.18 * pulse))
      gc.setLineWidth(14 * pulse)
      gc.strokeLine(tailX, tailY, tipX, tipY)
      // Purple core
      gc.setStroke(Color.color(0.6, 0.1, 0.8, 0.6 * pulse))
      gc.setLineWidth(5)
      gc.strokeLine(tailX, tailY, tipX, tipY)
      gc.setStroke(Color.color(0.8, 0.4, 1.0, 0.9))
      gc.setLineWidth(2)
      gc.strokeLine(tailX, tailY, tipX, tipY)
      // Skull mote particles
      val dx = tipX - tailX; val dy = tipY - tailY
      val len = Math.sqrt(dx * dx + dy * dy)
      if (len > 1) {
        val nx = dx / len; val ny = dy / len
        for (i <- 0 until 4) {
          val t = ((animationTick * 0.08 + i * 0.25 + projectile.id * 0.13) % 1.0)
          val px = tailX + dx * t + (-ny) * Math.sin(phase * 2 + i * 2.1) * 6
          val py = tailY + dy * t + nx * Math.sin(phase * 2 + i * 2.1) * 6
          val a = Math.max(0.0, Math.min(1.0, 0.6 * (1.0 - Math.abs(t - 0.5) * 2)))
          gc.setFill(Color.color(0.5, 0.1, 0.6, a))
          gc.fillOval(px - 2, py - 2, 4, 4)
        }
      }
    }
  }

  private def drawRaiseDeadProjectile(projectile: Projectile, camOffX: Double, camOffY: Double): Unit = {
    val projX = projectile.getX.toDouble; val projY = projectile.getY.toDouble
    val sx = worldToScreenX(projX, projY, camOffX)
    val sy = worldToScreenY(projX, projY, camOffY)
    val margin = 250.0
    if (sx > -margin && sx < getWidth + margin && sy > -margin && sy < getHeight + margin) {
      val phase = (animationTick + projectile.id * 29) * 0.35
      val pulse = 0.8 + 0.2 * Math.sin(phase)
      // Green necrotic aura
      gc.setFill(Color.color(0.2, 0.8, 0.1, 0.06 * pulse))
      gc.fillOval(sx - 22, sy - 16, 44, 32)
      // Central necrotic orb
      gc.setFill(Color.color(0.15, 0.5, 0.1, 0.5 * pulse))
      gc.fillOval(sx - 8, sy - 6, 16, 12)
      gc.setFill(Color.color(0.3, 0.8, 0.15, 0.6 * pulse))
      gc.fillOval(sx - 5, sy - 4, 10, 8)
      gc.setFill(Color.color(0.5, 1.0, 0.3, 0.7))
      gc.fillOval(sx - 2.5, sy - 2, 5, 4)
      // Rising spirit wisps
      for (i <- 0 until 5) {
        val angle = phase * 1.5 + i * Math.PI * 2 / 5
        val rise = ((animationTick * 0.06 + i * 0.2) % 1.0) * 15
        val wx = sx + Math.cos(angle) * 8
        val wy = sy + Math.sin(angle) * 5 - rise
        val a = Math.max(0.0, 0.4 * (1.0 - rise / 15.0) * pulse)
        gc.setFill(Color.color(0.3, 0.9, 0.2, a))
        gc.fillOval(wx - 2, wy - 2, 4, 4)
      }
    }
  }

  private def drawBoneAxeProjectile(projectile: Projectile, camOffX: Double, camOffY: Double): Unit = {
    val projX = projectile.getX.toDouble; val projY = projectile.getY.toDouble
    val sx = worldToScreenX(projX, projY, camOffX)
    val sy = worldToScreenY(projX, projY, camOffY)
    val margin = 80.0
    if (sx > -margin && sx < getWidth + margin && sy > -margin && sy < getHeight + margin) {
      val spin = animationTick * 0.35 + projectile.id * 2.1
      val pulse = 0.85 + 0.15 * Math.sin(spin)
      // Spinning bone axe blade
      val bladeLen = 9.0
      val xs = new Array[Double](4)
      val ys = new Array[Double](4)
      // Axe: handle + wide blade top
      xs(0) = sx + Math.cos(spin) * bladeLen
      ys(0) = sy + Math.sin(spin) * bladeLen * 0.6
      xs(1) = sx + Math.cos(spin + 0.5) * bladeLen * 0.7
      ys(1) = sy + Math.sin(spin + 0.5) * bladeLen * 0.4
      xs(2) = sx + Math.cos(spin + Math.PI) * bladeLen * 0.4
      ys(2) = sy + Math.sin(spin + Math.PI) * bladeLen * 0.25
      xs(3) = sx + Math.cos(spin - 0.5) * bladeLen * 0.7
      ys(3) = sy + Math.sin(spin - 0.5) * bladeLen * 0.4
      gc.setFill(Color.color(0.9, 0.88, 0.8, 0.85 * pulse))
      gc.fillPolygon(xs, ys, 4)
      gc.setStroke(Color.color(0.7, 0.65, 0.55, 0.7))
      gc.setLineWidth(1)
      gc.strokePolygon(xs, ys, 4)
      // Handle
      gc.setStroke(Color.color(0.75, 0.7, 0.6, 0.8))
      gc.setLineWidth(2.5)
      gc.strokeLine(sx, sy, sx + Math.cos(spin + Math.PI) * bladeLen * 0.6,
                    sy + Math.sin(spin + Math.PI) * bladeLen * 0.35)
    }
  }

  private def drawBoneThrowProjectile(projectile: Projectile, camOffX: Double, camOffY: Double): Unit = {
    val projX = projectile.getX.toDouble; val projY = projectile.getY.toDouble
    val sx = worldToScreenX(projX, projY, camOffX)
    val sy = worldToScreenY(projX, projY, camOffY)
    val margin = 250.0
    if (sx > -margin && sx < getWidth + margin && sy > -margin && sy < getHeight + margin) {
      val phase = (animationTick + projectile.id * 23) * 0.35
      val spin = animationTick * 0.3 + projectile.id * 1.9
      val pulse = 0.85 + 0.15 * Math.sin(spin * 2)
      val pdx = projectile.dx.toDouble; val pdy = projectile.dy.toDouble
      val pLen = Math.sqrt(pdx * pdx + pdy * pdy)
      val dirX = if (pLen > 0.01) pdx / pLen else 1.0
      val dirY = if (pLen > 0.01) pdy / pLen else 0.0
      // Ghostly bone aura
      gc.setFill(Color.color(0.9, 0.88, 0.78, 0.06 * pulse))
      gc.fillOval(sx - 18, sy - 13, 36, 26)
      gc.setFill(Color.color(0.92, 0.9, 0.8, 0.1 * pulse))
      gc.fillOval(sx - 12, sy - 9, 24, 18)
      // Spinning bone (bigger)
      val boneLen = 11.0
      val c = Math.cos(spin); val s2 = Math.sin(spin)
      val x1 = sx + c * boneLen; val y1 = sy + s2 * boneLen * 0.5
      val x2 = sx - c * boneLen; val y2 = sy - s2 * boneLen * 0.5
      // Bone shaft
      gc.setStroke(Color.color(0.9, 0.87, 0.78, 0.85 * pulse))
      gc.setLineWidth(3.5)
      gc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND)
      gc.strokeLine(x1, y1, x2, y2)
      // Bone knobs at ends (bigger)
      gc.setFill(Color.color(0.92, 0.9, 0.82, 0.9))
      gc.fillOval(x1 - 3.5, y1 - 2.5, 7, 5)
      gc.fillOval(x2 - 3.5, y2 - 2.5, 7, 5)
      // Bright highlights on knobs
      gc.setFill(Color.color(1.0, 0.98, 0.92, 0.6))
      gc.fillOval(x1 - 1.5, y1 - 1.5, 3, 3)
      gc.fillOval(x2 - 1.5, y2 - 1.5, 3, 3)
      // Bone dust trail
      for (i <- 0 until 5) {
        val t = ((animationTick * 0.09 + i * 0.2 + projectile.id * 0.15) % 1.0)
        val spread = Math.sin(phase + i * 2.1) * 5
        val tX = sx - dirX * t * 18 + spread
        val tY = sy - dirY * t * 11 + t * 4
        val a = Math.max(0.0, 0.35 * (1.0 - t) * pulse)
        val s = 2.0 + t * 2.5
        gc.setFill(Color.color(0.88, 0.85, 0.75, a))
        gc.fillOval(tX - s, tY - s * 0.7, s * 2, s * 1.4)
      }
    }
  }

  private def drawWailProjectile(projectile: Projectile, camOffX: Double, camOffY: Double): Unit = {
    val projX = projectile.getX.toDouble; val projY = projectile.getY.toDouble
    val sx = worldToScreenX(projX, projY, camOffX)
    val sy = worldToScreenY(projX, projY, camOffY)
    val margin = 250.0
    if (sx > -margin && sx < getWidth + margin && sy > -margin && sy < getHeight + margin) {
      val phase = (animationTick + projectile.id * 41) * 0.4
      val pulse = 0.7 + 0.3 * Math.sin(phase * 3)
      val pdx = projectile.dx.toDouble; val pdy = projectile.dy.toDouble
      val pLen = Math.sqrt(pdx * pdx + pdy * pdy)
      val dirX = if (pLen > 0.01) pdx / pLen else 1.0
      val dirY = if (pLen > 0.01) pdy / pLen else 0.0
      // Expanding translucent shriek waves
      for (ring <- 0 until 4) {
        val rp = ((phase * 0.3 + ring * 0.25) % 1.0)
        val rr = 5.0 + rp * 22.0
        val ra = Math.max(0.0, 0.25 * (1.0 - rp) * pulse)
        val cx = sx + dirX * rp * 8
        val cy = sy + dirY * rp * 5
        gc.setStroke(Color.color(0.7, 0.8, 0.9, ra))
        gc.setLineWidth(2.0 * (1.0 - rp))
        gc.strokeOval(cx - rr, cy - rr * 0.5, rr * 2, rr)
      }
      // Ghostly mouth shape at center
      gc.setFill(Color.color(0.6, 0.7, 0.85, 0.3 * pulse))
      gc.fillOval(sx - 4, sy - 3, 8, 6)
    }
  }

  private def drawSoulDrainProjectile(projectile: Projectile, camOffX: Double, camOffY: Double): Unit = {
    val projX = projectile.getX.toDouble; val projY = projectile.getY.toDouble
    val beamLength = 4.0
    val tailX = worldToScreenX(projX, projY, camOffX)
    val tailY = worldToScreenY(projX, projY, camOffY)
    val tipWX = projX + projectile.dx * beamLength
    val tipWY = projY + projectile.dy * beamLength
    val tipX = worldToScreenX(tipWX, tipWY, camOffX)
    val tipY = worldToScreenY(tipWX, tipWY, camOffY)
    val margin = 250.0
    if (Math.max(tailX, tipX) > -margin && Math.min(tailX, tipX) < getWidth + margin &&
        Math.max(tailY, tipY) > -margin && Math.min(tailY, tipY) < getHeight + margin) {
      gc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND)
      val phase = (animationTick + projectile.id * 37) * 0.4
      val pulse = 0.85 + 0.15 * Math.sin(phase)
      val dx = tipX - tailX; val dy = tipY - tailY
      val len = Math.sqrt(dx * dx + dy * dy)
      if (len < 1) return
      val nx = dx / len; val ny = dy / len
      // Green-purple siphon beam
      gc.setStroke(Color.color(0.3, 0.7, 0.2, 0.1 * pulse))
      gc.setLineWidth(20)
      gc.strokeLine(tailX, tailY, tipX, tipY)
      gc.setStroke(Color.color(0.4, 0.2, 0.7, 0.2 * pulse))
      gc.setLineWidth(10)
      gc.strokeLine(tailX, tailY, tipX, tipY)
      // Core
      gc.setStroke(Color.color(0.5, 0.9, 0.3, 0.6 * pulse))
      gc.setLineWidth(3)
      gc.strokeLine(tailX, tailY, tipX, tipY)
      // Spiraling particles
      for (i <- 0 until 5) {
        val t = ((animationTick * 0.08 + i * 0.2 + projectile.id * 0.11) % 1.0)
        val spiralAngle = t * Math.PI * 4 + phase
        val spiralR = 6.0 * (1.0 - t * 0.5)
        val px = tailX + dx * t + (-ny) * Math.cos(spiralAngle) * spiralR
        val py = tailY + dy * t + nx * Math.cos(spiralAngle) * spiralR
        val a = Math.max(0.0, 0.5 * (1.0 - Math.abs(t - 0.5) * 2))
        val isGreen = i % 2 == 0
        if (isGreen) gc.setFill(Color.color(0.3, 0.8, 0.2, a))
        else gc.setFill(Color.color(0.5, 0.2, 0.7, a))
        gc.fillOval(px - 2, py - 2, 4, 4)
      }
    }
  }

  private def drawClawSwipeProjectile(projectile: Projectile, camOffX: Double, camOffY: Double): Unit = {
    val projX = projectile.getX.toDouble; val projY = projectile.getY.toDouble
    val sx = worldToScreenX(projX, projY, camOffX)
    val sy = worldToScreenY(projX, projY, camOffY)
    val margin = 80.0
    if (sx > -margin && sx < getWidth + margin && sy > -margin && sy < getHeight + margin) {
      val phase = (animationTick + projectile.id * 19) * 0.5
      val fade = Math.max(0.0, Math.min(1.0, 0.8 + 0.2 * Math.sin(phase)))
      val pdx = projectile.dx.toDouble; val pdy = projectile.dy.toDouble
      val pLen = Math.sqrt(pdx * pdx + pdy * pdy)
      val dirX = if (pLen > 0.01) pdx / pLen else 1.0
      val dirY = if (pLen > 0.01) pdy / pLen else 0.0
      val perpX = -dirY; val perpY = dirX
      // 3 red scratch lines
      for (claw <- 0 until 3) {
        val offset = (claw - 1) * 5.0
        val startX = sx - dirX * 8 + perpX * offset
        val startY = sy - dirY * 5 + perpY * offset * 0.6
        val endX = sx + dirX * 8 + perpX * offset
        val endY = sy + dirY * 5 + perpY * offset * 0.6
        gc.setStroke(Color.color(0.9, 0.15, 0.1, 0.6 * fade))
        gc.setLineWidth(2.5)
        gc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND)
        gc.strokeLine(startX, startY, endX, endY)
        gc.setStroke(Color.color(1.0, 0.4, 0.3, 0.3 * fade))
        gc.setLineWidth(5)
        gc.strokeLine(startX, startY, endX, endY)
      }
    }
  }

  private def drawDevourProjectile(projectile: Projectile, camOffX: Double, camOffY: Double): Unit = {
    val projX = projectile.getX.toDouble; val projY = projectile.getY.toDouble
    val sx = worldToScreenX(projX, projY, camOffX)
    val sy = worldToScreenY(projX, projY, camOffY)
    val margin = 80.0
    if (sx > -margin && sx < getWidth + margin && sy > -margin && sy < getHeight + margin) {
      val phase = (animationTick + projectile.id * 23) * 0.45
      val chomp = Math.abs(Math.sin(phase * 3)) * 0.6
      // Dark maw — upper jaw
      val jawOpen = 4.0 + chomp * 6.0
      gc.setFill(Color.color(0.15, 0.05, 0.08, 0.8))
      gc.fillOval(sx - 8, sy - jawOpen - 4, 16, 8)
      // Lower jaw
      gc.setFill(Color.color(0.15, 0.05, 0.08, 0.8))
      gc.fillOval(sx - 8, sy + jawOpen - 4, 16, 8)
      // Red inner glow
      gc.setFill(Color.color(0.7, 0.1, 0.05, 0.4 * (1.0 - chomp)))
      gc.fillOval(sx - 5, sy - 3, 10, 6)
      // Teeth
      for (i <- 0 until 4) {
        val tx = sx - 5 + i * 3.3
        gc.setFill(Color.color(0.9, 0.85, 0.8, 0.8))
        gc.fillPolygon(Array(tx, tx + 1.5, tx + 3), Array(sy - jawOpen + 2, sy - jawOpen + 5, sy - jawOpen + 2), 3)
        gc.fillPolygon(Array(tx, tx + 1.5, tx + 3), Array(sy + jawOpen - 2, sy + jawOpen - 5, sy + jawOpen - 2), 3)
      }
    }
  }

  private def drawScytheProjectile(projectile: Projectile, camOffX: Double, camOffY: Double): Unit = {
    val projX = projectile.getX.toDouble; val projY = projectile.getY.toDouble
    val sx = worldToScreenX(projX, projY, camOffX)
    val sy = worldToScreenY(projX, projY, camOffY)
    val margin = 80.0
    if (sx > -margin && sx < getWidth + margin && sy > -margin && sy < getHeight + margin) {
      val spin = animationTick * 0.3 + projectile.id * 2.3
      val pulse = 0.85 + 0.15 * Math.sin(spin * 2)
      // Silver curved scythe blade
      val bladeR = 10.0
      val startAngle = spin
      val arcLen = Math.PI * 0.7
      val segs = 8
      // Outer edge
      gc.setStroke(Color.color(0.75, 0.78, 0.82, 0.85 * pulse))
      gc.setLineWidth(2.5)
      gc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND)
      for (i <- 0 until segs) {
        val a1 = startAngle + arcLen * i / segs
        val a2 = startAngle + arcLen * (i + 1) / segs
        gc.strokeLine(sx + Math.cos(a1) * bladeR, sy + Math.sin(a1) * bladeR * 0.6,
                      sx + Math.cos(a2) * bladeR, sy + Math.sin(a2) * bladeR * 0.6)
      }
      // Inner edge (sharp)
      gc.setStroke(Color.color(0.9, 0.92, 0.95, 0.9))
      gc.setLineWidth(1)
      val innerR = bladeR * 0.55
      for (i <- 0 until segs) {
        val a1 = startAngle + arcLen * i / segs
        val a2 = startAngle + arcLen * (i + 1) / segs
        gc.strokeLine(sx + Math.cos(a1) * innerR, sy + Math.sin(a1) * innerR * 0.6,
                      sx + Math.cos(a2) * innerR, sy + Math.sin(a2) * innerR * 0.6)
      }
      // Handle
      val handleAngle = startAngle + arcLen + 0.3
      gc.setStroke(Color.color(0.5, 0.4, 0.3, 0.7))
      gc.setLineWidth(2)
      gc.strokeLine(sx, sy, sx + Math.cos(handleAngle) * 8, sy + Math.sin(handleAngle) * 5)
    }
  }

  private def drawReapProjectile(projectile: Projectile, camOffX: Double, camOffY: Double): Unit = {
    val projX = projectile.getX.toDouble; val projY = projectile.getY.toDouble
    val sx = worldToScreenX(projX, projY, camOffX)
    val sy = worldToScreenY(projX, projY, camOffY)
    val margin = 250.0
    if (sx > -margin && sx < getWidth + margin && sy > -margin && sy < getHeight + margin) {
      val phase = (animationTick + projectile.id * 31) * 0.4
      val sweep = Math.sin(phase * 2) * 0.5
      val pdx = projectile.dx.toDouble; val pdy = projectile.dy.toDouble
      val pLen = Math.sqrt(pdx * pdx + pdy * pdy)
      val dirX = if (pLen > 0.01) pdx / pLen else 1.0
      val dirY = if (pLen > 0.01) pdy / pLen else 0.0
      // Large dark scythe arc
      val arcR = 16.0
      val baseAngle = Math.atan2(dirY, dirX) + sweep
      val arcLen = Math.PI * 0.8
      // Dark glow
      gc.setFill(Color.color(0.1, 0.0, 0.15, 0.08))
      gc.fillOval(sx - arcR, sy - arcR * 0.6, arcR * 2, arcR * 1.2)
      // Arc slash
      gc.setStroke(Color.color(0.2, 0.1, 0.25, 0.6))
      gc.setLineWidth(5)
      gc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND)
      val segs = 10
      for (i <- 0 until segs) {
        val a1 = baseAngle - arcLen / 2 + arcLen * i / segs
        val a2 = baseAngle - arcLen / 2 + arcLen * (i + 1) / segs
        gc.strokeLine(sx + Math.cos(a1) * arcR, sy + Math.sin(a1) * arcR * 0.6,
                      sx + Math.cos(a2) * arcR, sy + Math.sin(a2) * arcR * 0.6)
      }
      // Bright edge
      gc.setStroke(Color.color(0.6, 0.5, 0.7, 0.8))
      gc.setLineWidth(2)
      for (i <- 0 until segs) {
        val a1 = baseAngle - arcLen / 2 + arcLen * i / segs
        val a2 = baseAngle - arcLen / 2 + arcLen * (i + 1) / segs
        gc.strokeLine(sx + Math.cos(a1) * arcR, sy + Math.sin(a1) * arcR * 0.6,
                      sx + Math.cos(a2) * arcR, sy + Math.sin(a2) * arcR * 0.6)
      }
    }
  }

  private def drawShadowBoltProjectile(projectile: Projectile, camOffX: Double, camOffY: Double): Unit = {
    val projX = projectile.getX.toDouble; val projY = projectile.getY.toDouble
    val beamLength = 4.0
    val tailX = worldToScreenX(projX, projY, camOffX)
    val tailY = worldToScreenY(projX, projY, camOffY)
    val tipWX = projX + projectile.dx * beamLength
    val tipWY = projY + projectile.dy * beamLength
    val tipX = worldToScreenX(tipWX, tipWY, camOffX)
    val tipY = worldToScreenY(tipWX, tipWY, camOffY)
    val margin = 250.0
    if (Math.max(tailX, tipX) > -margin && Math.min(tailX, tipX) < getWidth + margin &&
        Math.max(tailY, tipY) > -margin && Math.min(tailY, tipY) < getHeight + margin) {
      gc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND)
      val phase = (animationTick + projectile.id * 43) * 0.35
      val pulse = 0.8 + 0.2 * Math.sin(phase)
      // Deep purple darkness
      gc.setStroke(Color.color(0.15, 0.0, 0.25, 0.1 * pulse))
      gc.setLineWidth(24 * pulse)
      gc.strokeLine(tailX, tailY, tipX, tipY)
      gc.setStroke(Color.color(0.25, 0.0, 0.4, 0.25 * pulse))
      gc.setLineWidth(12)
      gc.strokeLine(tailX, tailY, tipX, tipY)
      gc.setStroke(Color.color(0.4, 0.1, 0.6, 0.6 * pulse))
      gc.setLineWidth(4)
      gc.strokeLine(tailX, tailY, tipX, tipY)
      // Dark wisps floating off
      val dx = tipX - tailX; val dy = tipY - tailY
      val len = Math.sqrt(dx * dx + dy * dy)
      if (len > 1) {
        val ny = dy / len; val nx2 = dx / len
        for (i <- 0 until 4) {
          val t = ((animationTick * 0.07 + i * 0.25 + projectile.id * 0.15) % 1.0)
          val wx = tailX + dx * t + ny * Math.sin(phase * 1.5 + i * 2) * 8
          val wy = tailY + dy * t - nx2 * Math.sin(phase * 1.5 + i * 2) * 8 - t * 5
          val a = Math.max(0.0, 0.35 * (1.0 - t) * pulse)
          gc.setFill(Color.color(0.2, 0.0, 0.3, a))
          gc.fillOval(wx - 3, wy - 2, 6, 4)
        }
      }
    }
  }

  private def drawCursedBladeProjectile(projectile: Projectile, camOffX: Double, camOffY: Double): Unit = {
    val projX = projectile.getX.toDouble; val projY = projectile.getY.toDouble
    val sx = worldToScreenX(projX, projY, camOffX)
    val sy = worldToScreenY(projX, projY, camOffY)
    val margin = 80.0
    if (sx > -margin && sx < getWidth + margin && sy > -margin && sy < getHeight + margin) {
      val spin = animationTick * 0.3 + projectile.id * 2.1
      val pulse = 0.85 + 0.15 * Math.sin(spin * 2)
      // Purple-red cursed glow
      gc.setFill(Color.color(0.6, 0.1, 0.3, 0.06 * pulse))
      gc.fillOval(sx - 14, sy - 10, 28, 20)
      // Spinning blade shape
      val bladeLen = 10.0
      val c = Math.cos(spin); val s2 = Math.sin(spin)
      val tipPx = sx + c * bladeLen; val tipPy = sy + s2 * bladeLen * 0.6
      val hiltX = sx - c * bladeLen * 0.4; val hiltY = sy - s2 * bladeLen * 0.25
      val w1x = sx + (-s2) * 3; val w1y = sy + c * 2
      val w2x = sx - (-s2) * 3; val w2y = sy - c * 2
      gc.setFill(Color.color(0.5, 0.1, 0.2, 0.8 * pulse))
      gc.fillPolygon(Array(tipPx, w1x, hiltX, w2x), Array(tipPy, w1y, hiltY, w2y), 4)
      gc.setStroke(Color.color(0.8, 0.2, 0.4, 0.7))
      gc.setLineWidth(1)
      gc.strokePolygon(Array(tipPx, w1x, hiltX, w2x), Array(tipPy, w1y, hiltY, w2y), 4)
      // Purple curse runes
      gc.setFill(Color.color(0.6, 0.1, 0.7, 0.5 * pulse))
      gc.fillOval(sx - 1.5, sy - 1, 3, 2)
    }
  }

  private def drawLifeDrainProjectile(projectile: Projectile, camOffX: Double, camOffY: Double): Unit = {
    val projX = projectile.getX.toDouble; val projY = projectile.getY.toDouble
    val beamLength = 4.0
    val tailX = worldToScreenX(projX, projY, camOffX)
    val tailY = worldToScreenY(projX, projY, camOffY)
    val tipWX = projX + projectile.dx * beamLength
    val tipWY = projY + projectile.dy * beamLength
    val tipX = worldToScreenX(tipWX, tipWY, camOffX)
    val tipY = worldToScreenY(tipWX, tipWY, camOffY)
    val margin = 250.0
    if (Math.max(tailX, tipX) > -margin && Math.min(tailX, tipX) < getWidth + margin &&
        Math.max(tailY, tipY) > -margin && Math.min(tailY, tipY) < getHeight + margin) {
      gc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND)
      val phase = (animationTick + projectile.id * 29) * 0.4
      val pulse = 0.85 + 0.15 * Math.sin(phase)
      val dx = tipX - tailX; val dy = tipY - tailY
      val len = Math.sqrt(dx * dx + dy * dy)
      if (len < 1) return
      val nx = dx / len; val ny = dy / len
      // Red-green intertwined beam
      gc.setStroke(Color.color(0.8, 0.2, 0.1, 0.15 * pulse))
      gc.setLineWidth(16)
      gc.strokeLine(tailX, tailY, tipX, tipY)
      gc.setStroke(Color.color(0.2, 0.7, 0.15, 0.15 * pulse))
      gc.setLineWidth(12)
      gc.strokeLine(tailX, tailY, tipX, tipY)
      // Swirling red and green particles
      for (i <- 0 until 6) {
        val t = ((animationTick * 0.09 + i * 0.17 + projectile.id * 0.11) % 1.0)
        val spiralAngle = t * Math.PI * 3 + phase
        val spiralR = 5.0
        val px = tailX + dx * t + (-ny) * Math.sin(spiralAngle) * spiralR
        val py = tailY + dy * t + nx * Math.sin(spiralAngle) * spiralR
        val a = Math.max(0.0, 0.6 * (1.0 - Math.abs(t - 0.5) * 2))
        if (i % 2 == 0) gc.setFill(Color.color(0.9, 0.2, 0.1, a))
        else gc.setFill(Color.color(0.2, 0.8, 0.15, a))
        gc.fillOval(px - 2, py - 2, 4, 4)
      }
    }
  }

  private def drawShovelProjectile(projectile: Projectile, camOffX: Double, camOffY: Double): Unit = {
    val projX = projectile.getX.toDouble; val projY = projectile.getY.toDouble
    val sx = worldToScreenX(projX, projY, camOffX)
    val sy = worldToScreenY(projX, projY, camOffY)
    val margin = 250.0
    if (sx > -margin && sx < getWidth + margin && sy > -margin && sy < getHeight + margin) {
      val phase = (animationTick + projectile.id * 21) * 0.35
      val spin = animationTick * 0.3 + projectile.id * 1.7
      val pulse = 0.85 + 0.15 * Math.sin(phase)
      val c = Math.cos(spin); val s2 = Math.sin(spin)
      val pdx = projectile.dx.toDouble; val pdy = projectile.dy.toDouble
      val pLen = Math.sqrt(pdx * pdx + pdy * pdy)
      val dirX = if (pLen > 0.01) pdx / pLen else 1.0
      val dirY = if (pLen > 0.01) pdy / pLen else 0.0
      // Metal gleam aura
      gc.setFill(Color.color(0.7, 0.7, 0.65, 0.06 * pulse))
      gc.fillOval(sx - 18, sy - 13, 36, 26)
      gc.setFill(Color.color(0.75, 0.75, 0.7, 0.1 * pulse))
      gc.fillOval(sx - 11, sy - 8, 22, 16)
      // Shovel handle (bigger)
      val handleLen = 12.0
      gc.setStroke(Color.color(0.5, 0.35, 0.2, 0.85))
      gc.setLineWidth(2.5)
      gc.strokeLine(sx, sy, sx + c * handleLen, sy + s2 * handleLen * 0.6)
      // Shovel head (bigger grey metal trapezoid)
      val headX = sx - c * 6; val headY = sy - s2 * 3.5
      val perpX = -s2; val perpY = c
      gc.setFill(Color.color(0.55, 0.55, 0.5, 0.9))
      gc.fillPolygon(
        Array(headX + perpX * 5, headX - perpX * 5, headX - c * 5 - perpX * 4, headX - c * 5 + perpX * 4),
        Array(headY + perpY * 3, headY - perpY * 3, headY - s2 * 3 - perpY * 2.5, headY - s2 * 3 + perpY * 2.5), 4)
      // Metal outline
      gc.setStroke(Color.color(0.4, 0.4, 0.38, 0.6))
      gc.setLineWidth(1)
      gc.strokePolygon(
        Array(headX + perpX * 5, headX - perpX * 5, headX - c * 5 - perpX * 4, headX - c * 5 + perpX * 4),
        Array(headY + perpY * 3, headY - perpY * 3, headY - s2 * 3 - perpY * 2.5, headY - s2 * 3 + perpY * 2.5), 4)
      // Metal shine highlight
      gc.setFill(Color.color(0.85, 0.85, 0.82, 0.5))
      gc.fillOval(headX - 3, headY - 2, 6, 4)
      // Dirt trail (5 particles)
      for (i <- 0 until 5) {
        val t = ((animationTick * 0.09 + i * 0.2 + projectile.id * 0.17) % 1.0)
        val spread = Math.sin(phase + i * 2.3) * 4
        val tX = sx - dirX * t * 16 + spread
        val tY = sy - dirY * t * 10 + t * 5
        val a = Math.max(0.0, 0.35 * (1.0 - t) * pulse)
        val ds = 2.0 + t * 2.5
        gc.setFill(Color.color(0.5, 0.4, 0.25, a))
        gc.fillOval(tX - ds, tY - ds * 0.6, ds * 2, ds * 1.2)
      }
    }
  }

  private def drawHeadThrowProjectile(projectile: Projectile, camOffX: Double, camOffY: Double): Unit = {
    val projX = projectile.getX.toDouble; val projY = projectile.getY.toDouble
    val sx = worldToScreenX(projX, projY, camOffX)
    val sy = worldToScreenY(projX, projY, camOffY)
    val margin = 250.0
    if (sx > -margin && sx < getWidth + margin && sy > -margin && sy < getHeight + margin) {
      val phase = (animationTick + projectile.id * 29) * 0.3
      val spin = animationTick * 0.2 + projectile.id * 1.3
      val pulse = 0.8 + 0.2 * Math.sin(phase * 2)
      val bounce = Math.abs(Math.sin(phase * 1.5)) * 4.0
      val r = 8.0
      val pdx = projectile.dx.toDouble; val pdy = projectile.dy.toDouble
      val pLen = Math.sqrt(pdx * pdx + pdy * pdy)
      val dirX = if (pLen > 0.01) pdx / pLen else 1.0
      val dirY = if (pLen > 0.01) pdy / pLen else 0.0
      // Eerie ghostly aura
      gc.setFill(Color.color(0.4, 0.6, 0.4, 0.06 * pulse))
      gc.fillOval(sx - 22, sy - bounce - 16, 44, 32)
      gc.setFill(Color.color(0.5, 0.7, 0.5, 0.1 * pulse))
      gc.fillOval(sx - 14, sy - bounce - 10, 28, 20)
      // Shadow on ground
      gc.setFill(Color.color(0.0, 0.0, 0.0, 0.15))
      gc.fillOval(sx - r, sy + 4, r * 2, r * 0.5)
      // Pale sphere (head) - bigger
      gc.setFill(Color.color(0.72, 0.68, 0.58, 0.9))
      gc.fillOval(sx - r, sy - bounce - r, r * 2, r * 2)
      // Highlight on head
      gc.setFill(Color.color(0.85, 0.82, 0.72, 0.4))
      gc.fillOval(sx - r * 0.4, sy - bounce - r * 0.8, r * 0.8, r * 0.6)
      // Face features
      val faceR = spin % (Math.PI * 2)
      val faceVisible = Math.cos(faceR) > 0
      if (faceVisible) {
        // Glowing green eyes
        gc.setFill(Color.color(0.2, 0.8, 0.2, 0.8 * pulse))
        gc.fillOval(sx - 3.5, sy - bounce - 3.5, 2.5, 2.5)
        gc.fillOval(sx + 1, sy - bounce - 3.5, 2.5, 2.5)
        // Mouth
        gc.setStroke(Color.color(0.1, 0.1, 0.1, 0.6))
        gc.setLineWidth(1)
        gc.strokeLine(sx - 2.5, sy - bounce + 1.5, sx + 2.5, sy - bounce + 1.5)
      }
      // Ghostly wisps trailing behind (5 wisps)
      for (i <- 0 until 5) {
        val t = ((animationTick * 0.08 + i * 0.2 + projectile.id * 0.17) % 1.0)
        val spread = Math.sin(phase + i * 2.1) * 5
        val tX = sx - dirX * t * 20 + spread
        val tY = sy - dirY * t * 12 - bounce * (1.0 - t) - t * 3
        val a = Math.max(0.0, 0.3 * (1.0 - t) * pulse)
        val s = 2.5 + t * 3.0
        gc.setFill(Color.color(0.5, 0.7, 0.5, a))
        gc.fillOval(tX - s, tY - s * 0.7, s * 2, s * 1.4)
      }
    }
  }

  private def drawBandageWhipProjectile(projectile: Projectile, camOffX: Double, camOffY: Double): Unit = {
    val projX = projectile.getX.toDouble; val projY = projectile.getY.toDouble
    val beamLength = 4.0
    val tailX = worldToScreenX(projX, projY, camOffX)
    val tailY = worldToScreenY(projX, projY, camOffY)
    val tipWX = projX + projectile.dx * beamLength
    val tipWY = projY + projectile.dy * beamLength
    val tipX = worldToScreenX(tipWX, tipWY, camOffX)
    val tipY = worldToScreenY(tipWX, tipWY, camOffY)
    val margin = 250.0
    if (Math.max(tailX, tipX) > -margin && Math.min(tailX, tipX) < getWidth + margin &&
        Math.max(tailY, tipY) > -margin && Math.min(tailY, tipY) < getHeight + margin) {
      val phase = (animationTick + projectile.id * 23) * 0.4
      val dx = tipX - tailX; val dy = tipY - tailY
      val len = Math.sqrt(dx * dx + dy * dy)
      if (len < 1) return
      val nx = dx / len; val ny = dy / len
      // Sinusoidal bandage lash
      val segs = 10
      val pts = segs + 1
      val bxs = new Array[Double](pts); val bys = new Array[Double](pts)
      for (i <- 0 to segs) {
        val t = i.toDouble / segs
        val wave = Math.sin(t * Math.PI * 2.5 + phase * 2) * 5.0 * (1.0 - t * 0.5)
        bxs(i) = tailX + dx * t + (-ny) * wave
        bys(i) = tailY + dy * t + nx * wave
      }
      // Tan bandage body
      gc.setStroke(Color.color(0.8, 0.72, 0.55, 0.7))
      gc.setLineWidth(3.5)
      gc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND)
      for (i <- 0 until segs) gc.strokeLine(bxs(i), bys(i), bxs(i + 1), bys(i + 1))
      // Lighter center line
      gc.setStroke(Color.color(0.9, 0.83, 0.65, 0.5))
      gc.setLineWidth(1.5)
      for (i <- 0 until segs) gc.strokeLine(bxs(i), bys(i), bxs(i + 1), bys(i + 1))
    }
  }

  private def drawCurseProjectile(projectile: Projectile, camOffX: Double, camOffY: Double): Unit = {
    val projX = projectile.getX.toDouble; val projY = projectile.getY.toDouble
    val sx = worldToScreenX(projX, projY, camOffX)
    val sy = worldToScreenY(projX, projY, camOffY)
    val margin = 80.0
    if (sx > -margin && sx < getWidth + margin && sy > -margin && sy < getHeight + margin) {
      val phase = (animationTick + projectile.id * 37) * 0.3
      val spin = animationTick * 0.2 + projectile.id * 1.5
      val pulse = 0.8 + 0.2 * Math.sin(phase * 2)
      // Dark purple-black aura
      gc.setFill(Color.color(0.2, 0.0, 0.3, 0.08 * pulse))
      gc.fillOval(sx - 16, sy - 12, 32, 24)
      // Rotating sigil (pentagram-ish star)
      val sigR = 8.0
      val xs = new Array[Double](10)
      val ys = new Array[Double](10)
      for (i <- 0 until 10) {
        val angle = spin + i * Math.PI / 5
        val r = if (i % 2 == 0) sigR else sigR * 0.4
        xs(i) = sx + Math.cos(angle) * r
        ys(i) = sy + Math.sin(angle) * r * 0.6
      }
      gc.setFill(Color.color(0.35, 0.05, 0.5, 0.4 * pulse))
      gc.fillPolygon(xs, ys, 10)
      gc.setStroke(Color.color(0.6, 0.15, 0.8, 0.7 * pulse))
      gc.setLineWidth(1)
      gc.strokePolygon(xs, ys, 10)
      // Bright center
      gc.setFill(Color.color(0.7, 0.2, 0.9, 0.6))
      gc.fillOval(sx - 2, sy - 1.5, 4, 3)
    }
  }

  // ===== Batch 3: Medieval/Fantasy projectile renderers (types 70-79) =====

  private def drawHolyBladeProjectile(projectile: Projectile, camOffX: Double, camOffY: Double): Unit = {
    val projX = projectile.getX.toDouble; val projY = projectile.getY.toDouble
    val sx = worldToScreenX(projX, projY, camOffX)
    val sy = worldToScreenY(projX, projY, camOffY)
    val margin = 80.0
    if (sx > -margin && sx < getWidth + margin && sy > -margin && sy < getHeight + margin) {
      val spin = animationTick * 0.3 + projectile.id * 2.3
      val pulse = 0.85 + 0.15 * Math.sin(spin * 2)
      // Golden glow
      gc.setFill(Color.color(1.0, 0.85, 0.3, 0.06 * pulse))
      gc.fillOval(sx - 14, sy - 10, 28, 20)
      // Spinning golden sword
      val bladeLen = 11.0
      val c = Math.cos(spin); val s2 = Math.sin(spin)
      val tipPx = sx + c * bladeLen; val tipPy = sy + s2 * bladeLen * 0.6
      val hiltX = sx - c * bladeLen * 0.3; val hiltY = sy - s2 * bladeLen * 0.18
      val w1x = sx + (-s2) * 3; val w1y = sy + c * 1.8
      val w2x = sx - (-s2) * 3; val w2y = sy - c * 1.8
      gc.setFill(Color.color(1.0, 0.85, 0.3, 0.8 * pulse))
      gc.fillPolygon(Array(tipPx, w1x, hiltX, w2x), Array(tipPy, w1y, hiltY, w2y), 4)
      gc.setStroke(Color.color(1.0, 0.95, 0.6, 0.9))
      gc.setLineWidth(1)
      gc.strokePolygon(Array(tipPx, w1x, hiltX, w2x), Array(tipPy, w1y, hiltY, w2y), 4)
      // Cross-guard
      val guardLen = 5.0
      gc.setStroke(Color.color(0.8, 0.65, 0.2, 0.7))
      gc.setLineWidth(2)
      gc.strokeLine(sx + (-s2) * guardLen, sy + c * guardLen * 0.6,
                    sx - (-s2) * guardLen, sy - c * guardLen * 0.6)
    }
  }

  private def drawHolyBoltProjectile(projectile: Projectile, camOffX: Double, camOffY: Double): Unit = {
    val projX = projectile.getX.toDouble; val projY = projectile.getY.toDouble
    val beamLength = 4.5
    val tailX = worldToScreenX(projX, projY, camOffX)
    val tailY = worldToScreenY(projX, projY, camOffY)
    val tipWX = projX + projectile.dx * beamLength
    val tipWY = projY + projectile.dy * beamLength
    val tipX = worldToScreenX(tipWX, tipWY, camOffX)
    val tipY = worldToScreenY(tipWX, tipWY, camOffY)
    val margin = 250.0
    if (Math.max(tailX, tipX) > -margin && Math.min(tailX, tipX) < getWidth + margin &&
        Math.max(tailY, tipY) > -margin && Math.min(tailY, tipY) < getHeight + margin) {
      gc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND)
      val phase = (animationTick + projectile.id * 41) * 0.35
      val pulse = 0.85 + 0.15 * Math.sin(phase)
      // White-gold radiant glow
      gc.setStroke(Color.color(1.0, 0.95, 0.7, 0.08 * pulse))
      gc.setLineWidth(30 * pulse)
      gc.strokeLine(tailX, tailY, tipX, tipY)
      gc.setStroke(Color.color(1.0, 0.9, 0.5, 0.2 * pulse))
      gc.setLineWidth(16 * pulse)
      gc.strokeLine(tailX, tailY, tipX, tipY)
      // Gold beam
      gc.setStroke(Color.color(1.0, 0.85, 0.3, 0.6 * pulse))
      gc.setLineWidth(6)
      gc.strokeLine(tailX, tailY, tipX, tipY)
      // White core
      gc.setStroke(Color.color(1.0, 1.0, 0.9, 0.9))
      gc.setLineWidth(2.5)
      gc.strokeLine(tailX, tailY, tipX, tipY)
      // Radiant sparkles
      val dx = tipX - tailX; val dy = tipY - tailY
      val len = Math.sqrt(dx * dx + dy * dy)
      if (len > 1) {
        for (i <- 0 until 4) {
          val t = ((animationTick * 0.09 + i * 0.25 + projectile.id * 0.13) % 1.0)
          val ny = dy / len; val nx2 = dx / len
          val px = tailX + dx * t + (-ny) * Math.sin(phase * 2 + i * 1.8) * 7
          val py = tailY + dy * t + nx2 * Math.sin(phase * 2 + i * 1.8) * 7
          val a = Math.max(0.0, Math.min(1.0, 0.6 * (1.0 - Math.abs(t - 0.5) * 2)))
          gc.setFill(Color.color(1.0, 1.0, 0.8, a))
          gc.fillOval(px - 2, py - 2, 4, 4)
        }
      }
    }
  }

  private def drawArrowProjectile(projectile: Projectile, camOffX: Double, camOffY: Double): Unit = {
    val projX = projectile.getX.toDouble; val projY = projectile.getY.toDouble
    val sx = worldToScreenX(projX, projY, camOffX)
    val sy = worldToScreenY(projX, projY, camOffY)
    val margin = 80.0
    if (sx > -margin && sx < getWidth + margin && sy > -margin && sy < getHeight + margin) {
      val phase = (animationTick + projectile.id * 31) * 0.3
      val pdx = projectile.dx.toDouble; val pdy = projectile.dy.toDouble
      val pLen = Math.sqrt(pdx * pdx + pdy * pdy)
      val dirX = if (pLen > 0.01) pdx / pLen else 1.0
      val dirY = if (pLen > 0.01) pdy / pLen else 0.0
      val perpX = -dirY; val perpY = dirX
      // Arrow shaft (brown)
      val shaftLen = 14.0
      val tipPx = sx + dirX * shaftLen * 0.6; val tipPy = sy + dirY * shaftLen * 0.35
      val tailPx = sx - dirX * shaftLen * 0.4; val tailPy = sy - dirY * shaftLen * 0.25
      gc.setStroke(Color.color(0.5, 0.35, 0.15, 0.8))
      gc.setLineWidth(2)
      gc.strokeLine(tailPx, tailPy, tipPx, tipPy)
      // Arrowhead (grey)
      val headLen = 4.0
      val ahTip = (tipPx + dirX * headLen, tipPy + dirY * headLen * 0.6)
      gc.setFill(Color.color(0.6, 0.6, 0.55, 0.85))
      gc.fillPolygon(
        Array(ahTip._1, tipPx + perpX * 2.5, tipPx - perpX * 2.5),
        Array(ahTip._2, tipPy + perpY * 1.5, tipPy - perpY * 1.5), 3)
      // Feather fletching
      for (f <- -1 to 1 by 2) {
        val fx = tailPx + perpX * 2.5 * f
        val fy = tailPy + perpY * 1.5 * f
        gc.setStroke(Color.color(0.7, 0.55, 0.35, 0.5))
        gc.setLineWidth(1)
        gc.strokeLine(tailPx, tailPy, fx, fy)
      }
    }
  }

  private def drawPoisonArrowProjectile(projectile: Projectile, camOffX: Double, camOffY: Double): Unit = {
    val projX = projectile.getX.toDouble; val projY = projectile.getY.toDouble
    val sx = worldToScreenX(projX, projY, camOffX)
    val sy = worldToScreenY(projX, projY, camOffY)
    val margin = 80.0
    if (sx > -margin && sx < getWidth + margin && sy > -margin && sy < getHeight + margin) {
      val phase = (animationTick + projectile.id * 37) * 0.35
      val pulse = 0.85 + 0.15 * Math.sin(phase)
      val pdx = projectile.dx.toDouble; val pdy = projectile.dy.toDouble
      val pLen = Math.sqrt(pdx * pdx + pdy * pdy)
      val dirX = if (pLen > 0.01) pdx / pLen else 1.0
      val dirY = if (pLen > 0.01) pdy / pLen else 0.0
      val perpX = -dirY; val perpY = dirX
      // Arrow shaft
      val shaftLen = 14.0
      val tipPx = sx + dirX * shaftLen * 0.6; val tipPy = sy + dirY * shaftLen * 0.35
      val tailPx = sx - dirX * shaftLen * 0.4; val tailPy = sy - dirY * shaftLen * 0.25
      gc.setStroke(Color.color(0.45, 0.35, 0.15, 0.8))
      gc.setLineWidth(2)
      gc.strokeLine(tailPx, tailPy, tipPx, tipPy)
      // Green-tipped arrowhead
      val headLen = 4.0
      val ahTip = (tipPx + dirX * headLen, tipPy + dirY * headLen * 0.6)
      gc.setFill(Color.color(0.2, 0.75, 0.15, 0.85))
      gc.fillPolygon(
        Array(ahTip._1, tipPx + perpX * 2.5, tipPx - perpX * 2.5),
        Array(ahTip._2, tipPy + perpY * 1.5, tipPy - perpY * 1.5), 3)
      // Venom drip trail
      for (i <- 0 until 3) {
        val t = ((animationTick * 0.1 + i * 0.33 + projectile.id * 0.15) % 1.0)
        val dripX = sx - dirX * t * 10 + Math.sin(phase + i * 2) * 2
        val dripY = sy - dirY * t * 6 + t * 6
        val a = Math.max(0.0, 0.5 * (1.0 - t) * pulse)
        gc.setFill(Color.color(0.2, 0.7, 0.1, a))
        gc.fillOval(dripX - 1.5, dripY - 1, 3, 2)
      }
    }
  }

  private def drawSonicWaveProjectile(projectile: Projectile, camOffX: Double, camOffY: Double): Unit = {
    val projX = projectile.getX.toDouble; val projY = projectile.getY.toDouble
    val sx = worldToScreenX(projX, projY, camOffX)
    val sy = worldToScreenY(projX, projY, camOffY)
    val margin = 250.0
    if (sx > -margin && sx < getWidth + margin && sy > -margin && sy < getHeight + margin) {
      val phase = (animationTick + projectile.id * 43) * 0.4
      val pulse = 0.8 + 0.2 * Math.sin(phase * 2)
      // Expanding translucent sound rings
      for (ring <- 0 until 4) {
        val rp = ((phase * 0.35 + ring * 0.25) % 1.0)
        val rr = 4.0 + rp * 20.0
        val ra = Math.max(0.0, 0.2 * (1.0 - rp) * pulse)
        gc.setStroke(Color.color(0.8, 0.85, 0.9, ra))
        gc.setLineWidth(2.0 * (1.0 - rp))
        gc.strokeOval(sx - rr, sy - rr * 0.5, rr * 2, rr)
      }
      // Center vibration
      gc.setFill(Color.color(0.85, 0.9, 0.95, 0.3 * pulse))
      gc.fillOval(sx - 3, sy - 2, 6, 4)
    }
  }

  private def drawSonicBoomProjectile(projectile: Projectile, camOffX: Double, camOffY: Double): Unit = {
    val projX = projectile.getX.toDouble; val projY = projectile.getY.toDouble
    val sx = worldToScreenX(projX, projY, camOffX)
    val sy = worldToScreenY(projX, projY, camOffY)
    val margin = 250.0
    if (sx > -margin && sx < getWidth + margin && sy > -margin && sy < getHeight + margin) {
      val phase = (animationTick + projectile.id * 29) * 0.45
      val expand = 0.5 + 0.5 * Math.sin(phase * 2)
      // White shockwave blast - large expanding rings
      for (ring <- 0 until 3) {
        val rp = ((phase * 0.4 + ring * 0.33) % 1.0)
        val rr = 8.0 + rp * 30.0
        val ra = Math.max(0.0, 0.3 * (1.0 - rp))
        gc.setStroke(Color.color(1.0, 1.0, 1.0, ra))
        gc.setLineWidth(3.0 * (1.0 - rp))
        gc.strokeOval(sx - rr, sy - rr * 0.5, rr * 2, rr)
      }
      // Bright center flash
      gc.setFill(Color.color(1.0, 1.0, 1.0, 0.4 * expand))
      gc.fillOval(sx - 6, sy - 4, 12, 8)
      gc.setFill(Color.color(1.0, 1.0, 0.9, 0.6))
      gc.fillOval(sx - 3, sy - 2, 6, 4)
    }
  }

  private def drawFistProjectile(projectile: Projectile, camOffX: Double, camOffY: Double): Unit = {
    val projX = projectile.getX.toDouble; val projY = projectile.getY.toDouble
    val sx = worldToScreenX(projX, projY, camOffX)
    val sy = worldToScreenY(projX, projY, camOffY)
    val margin = 80.0
    if (sx > -margin && sx < getWidth + margin && sy > -margin && sy < getHeight + margin) {
      val phase = (animationTick + projectile.id * 19) * 0.5
      val pulse = 0.85 + 0.15 * Math.sin(phase * 3)
      // Impact glow
      gc.setFill(Color.color(1.0, 0.7, 0.2, 0.08 * pulse))
      gc.fillOval(sx - 16, sy - 12, 32, 24)
      // Orange fist shape (oval body)
      gc.setFill(Color.color(0.9, 0.6, 0.3, 0.85 * pulse))
      gc.fillOval(sx - 7, sy - 5, 14, 10)
      // Knuckle highlights
      gc.setFill(Color.color(1.0, 0.75, 0.4, 0.6))
      gc.fillOval(sx - 5, sy - 4, 3, 3)
      gc.fillOval(sx - 1, sy - 4, 3, 3)
      gc.fillOval(sx + 3, sy - 4, 3, 3)
      // POW lines radiating
      for (i <- 0 until 4) {
        val angle = phase + i * Math.PI / 2
        val innerD = 9.0; val outerD = 14.0
        gc.setStroke(Color.color(1.0, 0.85, 0.3, 0.4 * pulse))
        gc.setLineWidth(1.5)
        gc.strokeLine(sx + Math.cos(angle) * innerD, sy + Math.sin(angle) * innerD * 0.6,
                      sx + Math.cos(angle) * outerD, sy + Math.sin(angle) * outerD * 0.6)
      }
    }
  }

  private def drawSmiteProjectile(projectile: Projectile, camOffX: Double, camOffY: Double): Unit = {
    val projX = projectile.getX.toDouble; val projY = projectile.getY.toDouble
    val sx = worldToScreenX(projX, projY, camOffX)
    val sy = worldToScreenY(projX, projY, camOffY)
    val margin = 250.0
    if (sx > -margin && sx < getWidth + margin && sy > -margin && sy < getHeight + margin) {
      val phase = (animationTick + projectile.id * 31) * 0.4
      val flash = 0.6 + 0.4 * Math.sin(phase * 5)
      // Gold radial impact flash
      gc.setFill(Color.color(1.0, 0.85, 0.2, 0.06 * flash))
      gc.fillOval(sx - 25, sy - 18, 50, 36)
      // Hammer impact glow
      gc.setFill(Color.color(1.0, 0.9, 0.4, 0.3 * flash))
      gc.fillOval(sx - 8, sy - 5, 16, 10)
      gc.setFill(Color.color(1.0, 1.0, 0.7, 0.6 * flash))
      gc.fillOval(sx - 4, sy - 2.5, 8, 5)
      // Radial lines (divine impact)
      for (i <- 0 until 8) {
        val angle = phase * 0.5 + i * Math.PI / 4
        val innerD = 6.0; val outerD = 16.0 + Math.sin(phase * 2 + i) * 4
        gc.setStroke(Color.color(1.0, 0.9, 0.4, 0.3 * flash))
        gc.setLineWidth(2)
        gc.strokeLine(sx + Math.cos(angle) * innerD, sy + Math.sin(angle) * innerD * 0.5,
                      sx + Math.cos(angle) * outerD, sy + Math.sin(angle) * outerD * 0.5)
      }
    }
  }

  private def drawCharmProjectile(projectile: Projectile, camOffX: Double, camOffY: Double): Unit = {
    val projX = projectile.getX.toDouble; val projY = projectile.getY.toDouble
    val sx = worldToScreenX(projX, projY, camOffX)
    val sy = worldToScreenY(projX, projY, camOffY)
    val margin = 80.0
    if (sx > -margin && sx < getWidth + margin && sy > -margin && sy < getHeight + margin) {
      val phase = (animationTick + projectile.id * 41) * 0.35
      val pulse = 0.8 + 0.2 * Math.sin(phase)
      // Pink glow
      gc.setFill(Color.color(1.0, 0.4, 0.6, 0.06 * pulse))
      gc.fillOval(sx - 16, sy - 12, 32, 24)
      // Floating hearts
      for (i <- 0 until 4) {
        val hPhase = phase + i * Math.PI / 2
        val hx = sx + Math.cos(hPhase) * 8
        val hy = sy + Math.sin(hPhase) * 5 - Math.sin(phase * 2 + i) * 3
        val a = 0.5 + 0.3 * Math.sin(phase * 2 + i * 1.5)
        val hr = 3.0
        gc.setFill(Color.color(1.0, 0.3, 0.5, Math.max(0, Math.min(1, a * pulse))))
        // Simple heart from two circles + triangle
        gc.fillOval(hx - hr * 0.5, hy - hr * 0.4, hr, hr)
        gc.fillOval(hx + hr * 0.5 - hr, hy - hr * 0.4, hr, hr)
        gc.fillPolygon(Array(hx - hr, hx, hx + hr), Array(hy, hy + hr * 1.2, hy), 3)
      }
    }
  }

  private def drawCardProjectile(projectile: Projectile, camOffX: Double, camOffY: Double): Unit = {
    val projX = projectile.getX.toDouble; val projY = projectile.getY.toDouble
    val sx = worldToScreenX(projX, projY, camOffX)
    val sy = worldToScreenY(projX, projY, camOffY)
    val margin = 250.0
    if (sx > -margin && sx < getWidth + margin && sy > -margin && sy < getHeight + margin) {
      val phase = (animationTick + projectile.id * 33) * 0.4
      val spin = animationTick * 0.35 + projectile.id * 2.1
      val pulse = 0.85 + 0.15 * Math.sin(spin * 2)
      val pdx = projectile.dx.toDouble; val pdy = projectile.dy.toDouble
      val pLen = Math.sqrt(pdx * pdx + pdy * pdy)
      val dirX = if (pLen > 0.01) pdx / pLen else 1.0
      val dirY = if (pLen > 0.01) pdy / pLen else 0.0
      val suitID = projectile.id % 4
      val isRed = suitID < 2
      val glowR = if (isRed) 1.0 else 0.5
      val glowG = if (isRed) 0.3 else 0.5
      val glowB = if (isRed) 0.3 else 0.8
      // Magical card aura
      gc.setFill(Color.color(glowR, glowG, glowB, 0.06 * pulse))
      gc.fillOval(sx - 20, sy - 15, 40, 30)
      gc.setFill(Color.color(glowR, glowG, glowB, 0.12 * pulse))
      gc.fillOval(sx - 12, sy - 9, 24, 18)
      // Spinning card (bigger, white rectangle with perspective)
      val cardW = 6.0 * Math.abs(Math.cos(spin)) + 1.5
      val cardH = 10.0
      gc.setFill(Color.color(1.0, 1.0, 0.97, 0.9 * pulse))
      gc.fillRect(sx - cardW, sy - cardH * 0.5, cardW * 2, cardH)
      // Card border
      gc.setStroke(Color.color(0.6, 0.5, 0.2, 0.7))
      gc.setLineWidth(1.2)
      gc.strokeRect(sx - cardW, sy - cardH * 0.5, cardW * 2, cardH)
      // Golden edge shimmer
      gc.setStroke(Color.color(1.0, 0.85, 0.3, 0.4 * pulse))
      gc.setLineWidth(0.8)
      gc.strokeRect(sx - cardW + 1, sy - cardH * 0.5 + 1, (cardW - 1) * 2, cardH - 2)
      // Suit symbol
      if (cardW > 2.5) {
        val suitColor = if (isRed) Color.color(0.85, 0.1, 0.1, 0.85)
                         else Color.color(0.1, 0.1, 0.1, 0.85)
        gc.setFill(suitColor)
        val sr = 2.5
        gc.fillPolygon(Array(sx, sx + sr, sx, sx - sr), Array(sy - sr, sy, sy + sr, sy), 4)
      }
      // Sparkle trail (5 glittering particles)
      for (i <- 0 until 5) {
        val t = ((animationTick * 0.1 + i * 0.2 + projectile.id * 0.15) % 1.0)
        val spread = Math.sin(phase + i * 2.3) * 5
        val tX = sx - dirX * t * 18 + spread
        val tY = sy - dirY * t * 11 + Math.sin(phase * 3 + i) * 3
        val a = Math.max(0.0, 0.5 * (1.0 - t) * pulse)
        val s = 1.5 + (1.0 - t) * 1.5
        gc.setFill(Color.color(1.0, 0.9, 0.5, a))
        gc.fillOval(tX - s, tY - s, s * 2, s * 2)
      }
    }
  }

  // ===== Batch 4: Sci-Fi/Tech projectile renderers (types 80-89) =====

  private def drawDataBoltProjectile(projectile: Projectile, camOffX: Double, camOffY: Double): Unit = {
    val projX = projectile.getX.toDouble; val projY = projectile.getY.toDouble
    val beamLength = 4.0
    val tailX = worldToScreenX(projX, projY, camOffX)
    val tailY = worldToScreenY(projX, projY, camOffY)
    val tipWX = projX + projectile.dx * beamLength
    val tipWY = projY + projectile.dy * beamLength
    val tipX = worldToScreenX(tipWX, tipWY, camOffX)
    val tipY = worldToScreenY(tipWX, tipWY, camOffY)
    val margin = 250.0
    if (Math.max(tailX, tipX) > -margin && Math.min(tailX, tipX) < getWidth + margin &&
        Math.max(tailY, tipY) > -margin && Math.min(tailY, tipY) < getHeight + margin) {
      gc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND)
      val phase = (animationTick + projectile.id * 47) * 0.4
      val pulse = 0.85 + 0.15 * Math.sin(phase)
      // Cyan glow
      gc.setStroke(Color.color(0.0, 0.9, 1.0, 0.08 * pulse))
      gc.setLineWidth(22 * pulse)
      gc.strokeLine(tailX, tailY, tipX, tipY)
      gc.setStroke(Color.color(0.0, 0.85, 1.0, 0.25 * pulse))
      gc.setLineWidth(8)
      gc.strokeLine(tailX, tailY, tipX, tipY)
      // White core
      gc.setStroke(Color.color(0.7, 1.0, 1.0, 0.8 * pulse))
      gc.setLineWidth(2.5)
      gc.strokeLine(tailX, tailY, tipX, tipY)
      // Binary-like particles
      val dx = tipX - tailX; val dy = tipY - tailY
      val len = Math.sqrt(dx * dx + dy * dy)
      if (len > 1) {
        val ny = dy / len; val nx2 = dx / len
        for (i <- 0 until 5) {
          val t = ((animationTick * 0.1 + i * 0.2 + projectile.id * 0.13) % 1.0)
          val px = tailX + dx * t + (-ny) * Math.sin(phase * 2 + i * 2.3) * 5
          val py = tailY + dy * t + nx2 * Math.sin(phase * 2 + i * 2.3) * 5
          val a = Math.max(0.0, 0.5 * (1.0 - Math.abs(t - 0.5) * 2))
          gc.setFill(Color.color(0.0, 1.0, 0.9, a))
          gc.fillRect(px - 1, py - 1.5, 2, 3)
        }
      }
    }
  }

  private def drawVirusProjectile(projectile: Projectile, camOffX: Double, camOffY: Double): Unit = {
    val projX = projectile.getX.toDouble; val projY = projectile.getY.toDouble
    val sx = worldToScreenX(projX, projY, camOffX)
    val sy = worldToScreenY(projX, projY, camOffY)
    val margin = 250.0
    if (sx > -margin && sx < getWidth + margin && sy > -margin && sy < getHeight + margin) {
      val phase = (animationTick + projectile.id * 37) * 0.4
      val pulse = 0.8 + 0.2 * Math.sin(phase * 3)
      val glitch = Math.sin(phase * 7) * 4.0
      val fastGlitch = Math.sin(phase * 11) * 2.0
      val pdx = projectile.dx.toDouble; val pdy = projectile.dy.toDouble
      val pLen = Math.sqrt(pdx * pdx + pdy * pdy)
      val dirX = if (pLen > 0.01) pdx / pLen else 1.0
      val dirY = if (pLen > 0.01) pdy / pLen else 0.0
      val perpX = -dirY; val perpY = dirX
      // Layer 1: Wide digital aura
      gc.setFill(Color.color(0.0, 1.0, 0.2, 0.06 * pulse))
      gc.fillOval(sx - 24, sy - 18, 48, 36)
      // Layer 2: Bright green glow
      gc.setFill(Color.color(0.0, 0.9, 0.25, 0.1 * pulse))
      gc.fillOval(sx - 16, sy - 12, 32, 24)
      // Glitchy data cascade - matrix-style falling segments
      for (i <- 0 until 8) {
        val gx = sx + Math.sin(phase * 4 + i * 1.3) * 10 + glitch * (if (i % 2 == 0) 1 else -1)
        val gy = sy + Math.cos(phase * 3 + i * 1.7) * 7 + fastGlitch * (if (i % 3 == 0) 1 else -1)
        val gw = 3.5 + Math.sin(phase * 5 + i * 0.9) * 2.5
        val gh = 2.5 + Math.cos(phase * 4 + i * 1.1) * 1.5
        val a = 0.25 + 0.35 * Math.sin(phase * 6 + i * 2)
        gc.setFill(Color.color(0.0, 1.0, 0.3, Math.max(0, Math.min(1, a * pulse))))
        gc.fillRect(gx - gw / 2, gy - gh / 2, gw, gh)
      }
      // Scan line effects (horizontal glitch bands)
      for (i <- 0 until 3) {
        val bandY = sy + (i - 1) * 6 + Math.sin(phase * 8 + i) * 2
        val bandW = 14 + Math.sin(phase * 5 + i * 3) * 6
        gc.setFill(Color.color(0.0, 1.0, 0.2, 0.15 * pulse))
        gc.fillRect(sx - bandW / 2 + glitch * 0.5, bandY - 0.8, bandW, 1.6)
      }
      // Core virus sphere - bigger with green pulse
      gc.setFill(Color.color(0.0, 0.85, 0.2, 0.75 * pulse))
      gc.fillOval(sx - 6 + fastGlitch * 0.3, sy - 4, 12, 8)
      // Bright center
      gc.setFill(Color.color(0.2, 1.0, 0.4, 0.85 * pulse))
      gc.fillOval(sx - 3.5 + fastGlitch * 0.3, sy - 2.5, 7, 5)
      // White-hot center point
      gc.setFill(Color.color(0.6, 1.0, 0.7, 0.7))
      gc.fillOval(sx - 1.5, sy - 1, 3, 2)
      // Data particle trail (6 digital fragments)
      for (i <- 0 until 6) {
        val t = ((animationTick * 0.1 + i * 0.167 + projectile.id * 0.13) % 1.0)
        val spread = Math.sin(phase * 3 + i * 2.3) * 6
        val tX = sx - dirX * t * 20 + perpX * spread + Math.sin(phase * 7 + i) * 2
        val tY = sy - dirY * t * 12 + perpY * spread * 0.6
        val a = Math.max(0.0, 0.5 * (1.0 - t) * pulse)
        val tw = 3.0 + (1.0 - t) * 2.0
        val th = 1.5 + (1.0 - t) * 1.0
        gc.setFill(Color.color(0.0, 1.0, 0.3, a))
        gc.fillRect(tX - tw / 2, tY - th / 2, tw, th)
      }
    }
  }

  private def drawLaserProjectile(projectile: Projectile, camOffX: Double, camOffY: Double): Unit = {
    val projX = projectile.getX.toDouble; val projY = projectile.getY.toDouble
    val beamLength = 6.0
    val tailX = worldToScreenX(projX, projY, camOffX)
    val tailY = worldToScreenY(projX, projY, camOffY)
    val tipWX = projX + projectile.dx * beamLength
    val tipWY = projY + projectile.dy * beamLength
    val tipX = worldToScreenX(tipWX, tipWY, camOffX)
    val tipY = worldToScreenY(tipWX, tipWY, camOffY)
    val margin = 250.0
    if (Math.max(tailX, tipX) > -margin && Math.min(tailX, tipX) < getWidth + margin &&
        Math.max(tailY, tipY) > -margin && Math.min(tailY, tipY) < getHeight + margin) {
      gc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND)
      val phase = (animationTick + projectile.id * 53) * 0.5
      val flicker = 0.85 + 0.15 * Math.sin(phase * 8)
      // Red bloom
      gc.setStroke(Color.color(1.0, 0.05, 0.0, 0.06 * flicker))
      gc.setLineWidth(28)
      gc.strokeLine(tailX, tailY, tipX, tipY)
      gc.setStroke(Color.color(1.0, 0.1, 0.0, 0.15 * flicker))
      gc.setLineWidth(12)
      gc.strokeLine(tailX, tailY, tipX, tipY)
      // Bright red core — thin and sharp
      gc.setStroke(Color.color(1.0, 0.15, 0.05, 0.8 * flicker))
      gc.setLineWidth(3)
      gc.strokeLine(tailX, tailY, tipX, tipY)
      // White-hot center line
      gc.setStroke(Color.color(1.0, 0.7, 0.6, 0.9 * flicker))
      gc.setLineWidth(1.2)
      gc.strokeLine(tailX, tailY, tipX, tipY)
    }
  }

  private def drawGravityBallProjectile(projectile: Projectile, camOffX: Double, camOffY: Double): Unit = {
    val projX = projectile.getX.toDouble; val projY = projectile.getY.toDouble
    val sx = worldToScreenX(projX, projY, camOffX)
    val sy = worldToScreenY(projX, projY, camOffY)
    val margin = 250.0
    if (sx > -margin && sx < getWidth + margin && sy > -margin && sy < getHeight + margin) {
      val phase = (animationTick + projectile.id * 29) * 0.35
      val pulse = 0.8 + 0.2 * Math.sin(phase)
      val r = 8.0
      // Warping dark aura
      gc.setFill(Color.color(0.15, 0.0, 0.3, 0.1 * pulse))
      gc.fillOval(sx - r * 3, sy - r * 2.5, r * 6, r * 5)
      gc.setFill(Color.color(0.2, 0.0, 0.4, 0.15 * pulse))
      gc.fillOval(sx - r * 2, sy - r * 1.5, r * 4, r * 3)
      // Dark sphere core
      gc.setFill(Color.color(0.1, 0.0, 0.2, 0.8))
      gc.fillOval(sx - r, sy - r * 0.8, r * 2, r * 1.6)
      // Purple warping highlights
      gc.setStroke(Color.color(0.5, 0.2, 0.8, 0.5 * pulse))
      gc.setLineWidth(1)
      gc.strokeOval(sx - r * 0.7, sy - r * 0.5, r * 1.4, r)
      // Distortion particles being pulled in
      for (i <- 0 until 5) {
        val angle = phase * 2 + i * Math.PI * 2 / 5
        val dist = r * 2 * (1.0 - ((animationTick * 0.05 + i * 0.2) % 1.0))
        val px = sx + Math.cos(angle) * dist
        val py = sy + Math.sin(angle) * dist * 0.6
        val a = Math.max(0.0, 0.4 * (dist / (r * 2)))
        gc.setFill(Color.color(0.4, 0.15, 0.7, a))
        gc.fillOval(px - 1.5, py - 1, 3, 2)
      }
    }
  }

  private def drawGravityWellProjectile(projectile: Projectile, camOffX: Double, camOffY: Double): Unit = {
    val projX = projectile.getX.toDouble; val projY = projectile.getY.toDouble
    val sx = worldToScreenX(projX, projY, camOffX)
    val sy = worldToScreenY(projX, projY, camOffY)
    val margin = 250.0
    if (sx > -margin && sx < getWidth + margin && sy > -margin && sy < getHeight + margin) {
      val phase = (animationTick + projectile.id * 37) * 0.3
      val pulse = 0.8 + 0.2 * Math.sin(phase)
      // Swirling vortex rings
      for (ring <- 0 until 4) {
        val rp = ((phase * 0.5 + ring * 0.25) % 1.0)
        val rr = 20.0 * (1.0 - rp) + 3.0
        val ra = Math.max(0.0, 0.2 * rp * pulse)
        gc.setStroke(Color.color(0.4, 0.15, 0.7, ra))
        gc.setLineWidth(1.5)
        gc.strokeOval(sx - rr, sy - rr * 0.5, rr * 2, rr)
      }
      // Dark center void
      gc.setFill(Color.color(0.05, 0.0, 0.1, 0.7))
      gc.fillOval(sx - 5, sy - 3, 10, 6)
      // Pull lines converging
      for (i <- 0 until 6) {
        val angle = phase + i * Math.PI / 3
        val outerD = 18.0; val innerD = 4.0
        gc.setStroke(Color.color(0.5, 0.2, 0.8, 0.15 * pulse))
        gc.setLineWidth(1)
        gc.strokeLine(sx + Math.cos(angle) * outerD, sy + Math.sin(angle) * outerD * 0.5,
                      sx + Math.cos(angle) * innerD, sy + Math.sin(angle) * innerD * 0.5)
      }
    }
  }

  private def drawTeslaCoilProjectile(projectile: Projectile, camOffX: Double, camOffY: Double): Unit = {
    val projX = projectile.getX.toDouble; val projY = projectile.getY.toDouble
    val sx = worldToScreenX(projX, projY, camOffX)
    val sy = worldToScreenY(projX, projY, camOffY)
    val margin = 250.0
    if (sx > -margin && sx < getWidth + margin && sy > -margin && sy < getHeight + margin) {
      val phase = (animationTick + projectile.id * 43) * 0.5
      val flicker = 0.6 + 0.4 * Math.sin(phase * 6)
      // Blue electric glow
      gc.setFill(Color.color(0.2, 0.5, 1.0, 0.06 * flicker))
      gc.fillOval(sx - 18, sy - 12, 36, 24)
      // Electric arcs radiating from center
      for (i <- 0 until 5) {
        val angle = phase * 3 + i * Math.PI * 2 / 5
        val arcLen = 10.0 + Math.sin(phase * 4 + i * 2) * 5.0
        val midAngle = angle + Math.sin(phase * 5 + i * 3) * 0.5
        val mx = sx + Math.cos(midAngle) * arcLen * 0.5
        val my = sy + Math.sin(midAngle) * arcLen * 0.3
        val ex = sx + Math.cos(angle) * arcLen
        val ey = sy + Math.sin(angle) * arcLen * 0.6
        val a = Math.max(0.0, Math.min(1.0, 0.5 * flicker))
        gc.setStroke(Color.color(0.3, 0.6, 1.0, a))
        gc.setLineWidth(2)
        gc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND)
        gc.strokeLine(sx, sy, mx, my)
        gc.strokeLine(mx, my, ex, ey)
      }
      // Bright center coil
      gc.setFill(Color.color(0.5, 0.8, 1.0, 0.7 * flicker))
      gc.fillOval(sx - 3, sy - 2, 6, 4)
      gc.setFill(Color.color(0.8, 0.95, 1.0, 0.9))
      gc.fillOval(sx - 1.5, sy - 1, 3, 2)
    }
  }

  private def drawNanoBoltProjectile(projectile: Projectile, camOffX: Double, camOffY: Double): Unit = {
    val projX = projectile.getX.toDouble; val projY = projectile.getY.toDouble
    val beamLength = 4.0
    val tailX = worldToScreenX(projX, projY, camOffX)
    val tailY = worldToScreenY(projX, projY, camOffY)
    val tipWX = projX + projectile.dx * beamLength
    val tipWY = projY + projectile.dy * beamLength
    val tipX = worldToScreenX(tipWX, tipWY, camOffX)
    val tipY = worldToScreenY(tipWX, tipWY, camOffY)
    val margin = 250.0
    if (Math.max(tailX, tipX) > -margin && Math.min(tailX, tipX) < getWidth + margin &&
        Math.max(tailY, tipY) > -margin && Math.min(tailY, tipY) < getHeight + margin) {
      gc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND)
      val phase = (animationTick + projectile.id * 41) * 0.45
      val pulse = 0.85 + 0.15 * Math.sin(phase)
      // Silver glow
      gc.setStroke(Color.color(0.8, 0.82, 0.85, 0.08 * pulse))
      gc.setLineWidth(20)
      gc.strokeLine(tailX, tailY, tipX, tipY)
      gc.setStroke(Color.color(0.85, 0.87, 0.9, 0.25 * pulse))
      gc.setLineWidth(8)
      gc.strokeLine(tailX, tailY, tipX, tipY)
      // Silver core
      gc.setStroke(Color.color(0.9, 0.92, 0.95, 0.7 * pulse))
      gc.setLineWidth(3)
      gc.strokeLine(tailX, tailY, tipX, tipY)
      // Micro-particle stream
      val dx = tipX - tailX; val dy = tipY - tailY
      val len = Math.sqrt(dx * dx + dy * dy)
      if (len > 1) {
        val ny = dy / len; val nx2 = dx / len
        for (i <- 0 until 8) {
          val t = ((animationTick * 0.12 + i * 0.125 + projectile.id * 0.09) % 1.0)
          val px = tailX + dx * t + (-ny) * Math.sin(phase * 3 + i * 1.7) * 4
          val py = tailY + dy * t + nx2 * Math.sin(phase * 3 + i * 1.7) * 4
          val a = Math.max(0.0, 0.4 * (1.0 - Math.abs(t - 0.5) * 2))
          gc.setFill(Color.color(0.85, 0.88, 0.92, a))
          gc.fillRect(px - 0.8, py - 0.8, 1.6, 1.6)
        }
      }
    }
  }

  private def drawVoidBoltProjectile(projectile: Projectile, camOffX: Double, camOffY: Double): Unit = {
    val projX = projectile.getX.toDouble; val projY = projectile.getY.toDouble
    val beamLength = 4.5
    val tailX = worldToScreenX(projX, projY, camOffX)
    val tailY = worldToScreenY(projX, projY, camOffY)
    val tipWX = projX + projectile.dx * beamLength
    val tipWY = projY + projectile.dy * beamLength
    val tipX = worldToScreenX(tipWX, tipWY, camOffX)
    val tipY = worldToScreenY(tipWX, tipWY, camOffY)
    val margin = 250.0
    if (Math.max(tailX, tipX) > -margin && Math.min(tailX, tipX) < getWidth + margin &&
        Math.max(tailY, tipY) > -margin && Math.min(tailY, tipY) < getHeight + margin) {
      gc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND)
      val phase = (animationTick + projectile.id * 31) * 0.35
      val pulse = 0.8 + 0.2 * Math.sin(phase)
      // Dark purple void glow
      gc.setStroke(Color.color(0.15, 0.0, 0.25, 0.1 * pulse))
      gc.setLineWidth(26 * pulse)
      gc.strokeLine(tailX, tailY, tipX, tipY)
      gc.setStroke(Color.color(0.25, 0.05, 0.4, 0.2 * pulse))
      gc.setLineWidth(14)
      gc.strokeLine(tailX, tailY, tipX, tipY)
      // Deep purple core
      gc.setStroke(Color.color(0.4, 0.1, 0.6, 0.5 * pulse))
      gc.setLineWidth(5)
      gc.strokeLine(tailX, tailY, tipX, tipY)
      gc.setStroke(Color.color(0.6, 0.3, 0.8, 0.8))
      gc.setLineWidth(2)
      gc.strokeLine(tailX, tailY, tipX, tipY)
      // Star particles
      val dx = tipX - tailX; val dy = tipY - tailY
      val len = Math.sqrt(dx * dx + dy * dy)
      if (len > 1) {
        val ny = dy / len; val nx2 = dx / len
        for (i <- 0 until 4) {
          val t = ((animationTick * 0.07 + i * 0.25 + projectile.id * 0.17) % 1.0)
          val px = tailX + dx * t + (-ny) * Math.sin(phase + i * 2.5) * 7
          val py = tailY + dy * t + nx2 * Math.sin(phase + i * 2.5) * 7
          val a = Math.max(0.0, 0.5 * (1.0 - Math.abs(t - 0.5) * 2))
          gc.setFill(Color.color(0.9, 0.8, 1.0, a))
          gc.fillOval(px - 1, py - 1, 2, 2)
        }
      }
    }
  }

  private def drawRailgunProjectile(projectile: Projectile, camOffX: Double, camOffY: Double): Unit = {
    val projX = projectile.getX.toDouble; val projY = projectile.getY.toDouble
    val beamLength = 8.0
    val tailX = worldToScreenX(projX, projY, camOffX)
    val tailY = worldToScreenY(projX, projY, camOffY)
    val tipWX = projX + projectile.dx * beamLength
    val tipWY = projY + projectile.dy * beamLength
    val tipX = worldToScreenX(tipWX, tipWY, camOffX)
    val tipY = worldToScreenY(tipWX, tipWY, camOffY)
    val margin = 250.0
    if (Math.max(tailX, tipX) > -margin && Math.min(tailX, tipX) < getWidth + margin &&
        Math.max(tailY, tipY) > -margin && Math.min(tailY, tipY) < getHeight + margin) {
      gc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND)
      val phase = (animationTick + projectile.id * 53) * 0.6
      val flicker = 0.8 + 0.2 * Math.sin(phase * 7)
      // Ultra-bright blue-white bloom
      gc.setStroke(Color.color(0.5, 0.7, 1.0, 0.06 * flicker))
      gc.setLineWidth(40)
      gc.strokeLine(tailX, tailY, tipX, tipY)
      gc.setStroke(Color.color(0.6, 0.8, 1.0, 0.12 * flicker))
      gc.setLineWidth(20)
      gc.strokeLine(tailX, tailY, tipX, tipY)
      gc.setStroke(Color.color(0.7, 0.9, 1.0, 0.4 * flicker))
      gc.setLineWidth(8)
      gc.strokeLine(tailX, tailY, tipX, tipY)
      // White-hot core
      gc.setStroke(Color.color(0.95, 0.97, 1.0, 0.95 * flicker))
      gc.setLineWidth(3)
      gc.strokeLine(tailX, tailY, tipX, tipY)
      // Shockwave rings along the beam
      val dx = tipX - tailX; val dy = tipY - tailY
      val len = Math.sqrt(dx * dx + dy * dy)
      if (len > 1) {
        val nx2 = dx / len; val ny = dy / len
        for (i <- 0 until 3) {
          val t = ((animationTick * 0.12 + i * 0.33 + projectile.id * 0.11) % 1.0)
          val ringX = tailX + dx * t
          val ringY = tailY + dy * t
          val ringR = 6.0 + Math.sin(phase + i * 2) * 3
          val a = Math.max(0.0, 0.3 * (1.0 - Math.abs(t - 0.5) * 2) * flicker)
          gc.setStroke(Color.color(0.7, 0.85, 1.0, a))
          gc.setLineWidth(1.5)
          gc.strokeOval(ringX - ringR, ringY - ringR * 0.5, ringR * 2, ringR)
        }
      }
    }
  }

  private def drawClusterBombProjectile(projectile: Projectile, camOffX: Double, camOffY: Double): Unit = {
    val projX = projectile.getX.toDouble; val projY = projectile.getY.toDouble
    val sx = worldToScreenX(projX, projY, camOffX)
    val sy = worldToScreenY(projX, projY, camOffY)
    val margin = 250.0
    if (sx > -margin && sx < getWidth + margin && sy > -margin && sy < getHeight + margin) {
      val phase = (animationTick + projectile.id * 23) * 0.35
      val spin = animationTick * 0.2 + projectile.id * 1.5
      val pulse = 0.8 + 0.2 * Math.sin(phase)
      val mainR = 7.0
      // Main bomb body
      gc.setFill(Color.color(0.35, 0.35, 0.4, 0.85))
      gc.fillOval(sx - mainR, sy - mainR * 0.8, mainR * 2, mainR * 1.6)
      // Danger pulse
      gc.setStroke(Color.color(1.0, 0.4, 0.1, 0.3 * pulse))
      gc.setLineWidth(1.5)
      gc.strokeOval(sx - mainR * 1.3, sy - mainR, mainR * 2.6, mainR * 2)
      // Orbiting sub-spheres (bomblets)
      for (i <- 0 until 4) {
        val angle = spin + i * Math.PI / 2
        val orbitR = mainR + 4.0
        val bx = sx + Math.cos(angle) * orbitR
        val by = sy + Math.sin(angle) * orbitR * 0.6
        val bombletR = 3.0
        gc.setFill(Color.color(0.4, 0.38, 0.35, 0.8))
        gc.fillOval(bx - bombletR, by - bombletR * 0.7, bombletR * 2, bombletR * 1.4)
        // Orange warning dot
        gc.setFill(Color.color(1.0, 0.5, 0.1, 0.6 * pulse))
        gc.fillOval(bx - 1, by - 0.5, 2, 1)
      }
    }
  }

  // ===== Batch 5: Nature/Beast projectile renderers (types 90-93) =====

  private def drawVenomBoltProjectile(projectile: Projectile, camOffX: Double, camOffY: Double): Unit = {
    val projX = projectile.getX.toDouble; val projY = projectile.getY.toDouble
    val beamLength = 4.0
    val tailX = worldToScreenX(projX, projY, camOffX)
    val tailY = worldToScreenY(projX, projY, camOffY)
    val tipWX = projX + projectile.dx * beamLength
    val tipWY = projY + projectile.dy * beamLength
    val tipX = worldToScreenX(tipWX, tipWY, camOffX)
    val tipY = worldToScreenY(tipWX, tipWY, camOffY)
    val margin = 250.0
    if (Math.max(tailX, tipX) > -margin && Math.min(tailX, tipX) < getWidth + margin &&
        Math.max(tailY, tipY) > -margin && Math.min(tailY, tipY) < getHeight + margin) {
      gc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND)
      val phase = (animationTick + projectile.id * 37) * 0.4
      val pulse = 0.85 + 0.15 * Math.sin(phase)
      // Green-purple glow
      gc.setStroke(Color.color(0.3, 0.8, 0.1, 0.08 * pulse))
      gc.setLineWidth(22 * pulse)
      gc.strokeLine(tailX, tailY, tipX, tipY)
      gc.setStroke(Color.color(0.4, 0.2, 0.6, 0.12 * pulse))
      gc.setLineWidth(14)
      gc.strokeLine(tailX, tailY, tipX, tipY)
      // Green venom core
      gc.setStroke(Color.color(0.3, 0.85, 0.15, 0.6 * pulse))
      gc.setLineWidth(5)
      gc.strokeLine(tailX, tailY, tipX, tipY)
      gc.setStroke(Color.color(0.6, 1.0, 0.4, 0.8))
      gc.setLineWidth(2)
      gc.strokeLine(tailX, tailY, tipX, tipY)
      // Dripping venom particles
      val dx = tipX - tailX; val dy = tipY - tailY
      val len = Math.sqrt(dx * dx + dy * dy)
      if (len > 1) {
        for (i <- 0 until 4) {
          val t = ((animationTick * 0.08 + i * 0.25 + projectile.id * 0.13) % 1.0)
          val px = tailX + dx * t + Math.sin(phase + i * 2.3) * 4
          val py = tailY + dy * t + t * 6
          val a = Math.max(0.0, 0.5 * (1.0 - t))
          val isGreen = i % 2 == 0
          if (isGreen) gc.setFill(Color.color(0.3, 0.8, 0.1, a))
          else gc.setFill(Color.color(0.5, 0.2, 0.6, a))
          gc.fillOval(px - 1.5, py - 1, 3, 2)
        }
      }
    }
  }

  private def drawWebShotProjectile(projectile: Projectile, camOffX: Double, camOffY: Double): Unit = {
    val projX = projectile.getX.toDouble; val projY = projectile.getY.toDouble
    val sx = worldToScreenX(projX, projY, camOffX)
    val sy = worldToScreenY(projX, projY, camOffY)
    val margin = 250.0
    if (sx > -margin && sx < getWidth + margin && sy > -margin && sy < getHeight + margin) {
      val phase = (animationTick + projectile.id * 29) * 0.3
      val spin = animationTick * 0.15 + projectile.id * 1.1
      val pulse = 0.8 + 0.2 * Math.sin(phase)
      val pdx = projectile.dx.toDouble; val pdy = projectile.dy.toDouble
      val pLen = Math.sqrt(pdx * pdx + pdy * pdy)
      val dirX = if (pLen > 0.01) pdx / pLen else 1.0
      val dirY = if (pLen > 0.01) pdy / pLen else 0.0
      // Sticky web aura
      gc.setFill(Color.color(1.0, 1.0, 0.95, 0.06 * pulse))
      gc.fillOval(sx - 22, sy - 16, 44, 32)
      gc.setFill(Color.color(0.95, 0.95, 0.9, 0.1 * pulse))
      gc.fillOval(sx - 14, sy - 10, 28, 20)
      // Radial web strands (bigger, 8 strands)
      val webR = 13.0
      for (i <- 0 until 8) {
        val angle = spin + i * Math.PI / 4
        val wobble = Math.sin(phase * 2 + i * 1.7) * 1.5
        val ex = sx + Math.cos(angle) * (webR + wobble)
        val ey = sy + Math.sin(angle) * (webR + wobble) * 0.6
        gc.setStroke(Color.color(0.95, 0.95, 0.9, 0.55 * pulse))
        gc.setLineWidth(1.2)
        gc.strokeLine(sx, sy, ex, ey)
      }
      // Concentric web rings (3 rings, bigger)
      for (ring <- 1 to 3) {
        val rr = webR * ring / 4.0
        val ringPts = 8
        for (i <- 0 until ringPts) {
          val a1 = spin + i * Math.PI * 2 / ringPts
          val a2 = spin + (i + 1) * Math.PI * 2 / ringPts
          val sag = Math.sin(phase + ring + i * 0.5) * 1.5
          gc.setStroke(Color.color(0.92, 0.92, 0.88, 0.4 * pulse))
          gc.setLineWidth(1.0)
          gc.strokeLine(sx + Math.cos(a1) * rr, sy + Math.sin(a1) * rr * 0.6 + sag,
                        sx + Math.cos(a2) * rr, sy + Math.sin(a2) * rr * 0.6 + sag)
        }
      }
      // Sticky blobs at strand tips
      for (i <- 0 until 8) {
        val angle = spin + i * Math.PI / 4
        val ex = sx + Math.cos(angle) * webR
        val ey = sy + Math.sin(angle) * webR * 0.6
        gc.setFill(Color.color(0.95, 0.95, 0.9, 0.35 * pulse))
        gc.fillOval(ex - 1.5, ey - 1, 3, 2)
      }
      // Center web mass (bigger)
      gc.setFill(Color.color(0.92, 0.92, 0.88, 0.7))
      gc.fillOval(sx - 5, sy - 3.5, 10, 7)
      gc.setFill(Color.color(0.97, 0.97, 0.94, 0.5))
      gc.fillOval(sx - 3, sy - 2, 6, 4)
      // Web strand trail (4 trailing threads)
      for (i <- 0 until 4) {
        val t = ((animationTick * 0.08 + i * 0.25 + projectile.id * 0.15) % 1.0)
        val spread = Math.sin(phase + i * 2.3) * 6
        val tX = sx - dirX * t * 18 + spread
        val tY = sy - dirY * t * 11 + Math.sin(phase + i * 1.7) * 3
        val a = Math.max(0.0, 0.3 * (1.0 - t) * pulse)
        gc.setStroke(Color.color(0.9, 0.9, 0.85, a))
        gc.setLineWidth(0.8)
        gc.strokeLine(sx - dirX * (t - 0.1) * 18, sy - dirY * (t - 0.1) * 11, tX, tY)
      }
    }
  }

  private def drawStingerProjectile(projectile: Projectile, camOffX: Double, camOffY: Double): Unit = {
    val projX = projectile.getX.toDouble; val projY = projectile.getY.toDouble
    val sx = worldToScreenX(projX, projY, camOffX)
    val sy = worldToScreenY(projX, projY, camOffY)
    val margin = 250.0
    if (sx > -margin && sx < getWidth + margin && sy > -margin && sy < getHeight + margin) {
      val phase = (animationTick + projectile.id * 31) * 0.4
      val spin = animationTick * 0.3 + projectile.id * 2.1
      val pulse = 0.85 + 0.15 * Math.sin(spin * 2)
      val pdx = projectile.dx.toDouble; val pdy = projectile.dy.toDouble
      val pLen = Math.sqrt(pdx * pdx + pdy * pdy)
      val dirX = if (pLen > 0.01) pdx / pLen else 1.0
      val dirY = if (pLen > 0.01) pdy / pLen else 0.0
      val perpX = -dirY; val perpY = dirX
      // Layer 1: Wide venom aura
      gc.setFill(Color.color(0.85, 0.8, 0.0, 0.05 * pulse))
      gc.fillOval(sx - 22, sy - 16, 44, 32)
      // Layer 2: Yellow-green venom glow
      gc.setFill(Color.color(0.9, 0.85, 0.1, 0.1 * pulse))
      gc.fillOval(sx - 14, sy - 10, 28, 20)
      // Buzzing energy particles (wing-like shimmer)
      for (i <- 0 until 4) {
        val angle = phase * 4.0 + i * Math.PI / 2
        val dist = 6.0 + Math.sin(phase * 6 + i * 2.3) * 3.0
        val bx = sx + Math.cos(angle) * dist * 0.7
        val by = sy + Math.sin(angle) * dist * 0.4
        val bs = 2.0 + Math.sin(phase * 8 + i) * 1.0
        gc.setFill(Color.color(0.9, 0.85, 0.3, 0.35 * pulse))
        gc.fillOval(bx - bs, by - bs * 0.6, bs * 2, bs * 1.2)
      }
      // Yellow-black stinger spike - BIGGER
      val tipPx = sx + dirX * 14; val tipPy = sy + dirY * 8.5
      val backPx = sx - dirX * 8; val backPy = sy - dirY * 5
      val w1x = sx + perpX * 4.5; val w1y = sy + perpY * 3
      val w2x = sx - perpX * 4.5; val w2y = sy - perpY * 3
      // Dark base with stripes
      gc.setFill(Color.color(0.12, 0.1, 0.03, 0.85 * pulse))
      gc.fillPolygon(Array(sx + dirX * 2, w1x, backPx, w2x),
                     Array(sy + dirY * 1.5, w1y, backPy, w2y), 4)
      // Middle stripe (yellow)
      val midX = sx - dirX * 1; val midY = sy - dirY * 0.5
      gc.setFill(Color.color(0.9, 0.8, 0.05, 0.7 * pulse))
      gc.fillPolygon(Array(midX + dirX * 3, midX + perpX * 3.5, midX - dirX * 3, midX - perpX * 3.5),
                     Array(midY + dirY * 2, midY + perpY * 2.3, midY - dirY * 2, midY - perpY * 2.3), 4)
      // Bright yellow-white tip
      gc.setFill(Color.color(0.98, 0.92, 0.2, 0.9 * pulse))
      gc.fillPolygon(Array(tipPx, sx + perpX * 3 + dirX * 3, sx - perpX * 3 + dirX * 3),
                     Array(tipPy, sy + perpY * 2 + dirY * 2, sy - perpY * 2 + dirY * 2), 3)
      // Hot tip glow
      gc.setFill(Color.color(1.0, 1.0, 0.6, 0.7 * pulse))
      gc.fillOval(tipPx - 2.5, tipPy - 2, 5, 4)
      // Outline
      gc.setStroke(Color.color(0.1, 0.08, 0.0, 0.65))
      gc.setLineWidth(1.2)
      gc.strokePolygon(Array(tipPx, w1x, backPx, w2x), Array(tipPy, w1y, backPy, w2y), 4)
      // Venom drip trail (5 drops)
      for (i <- 0 until 5) {
        val t = ((animationTick * 0.09 + i * 0.2 + projectile.id * 0.17) % 1.0)
        val spread = Math.sin(phase + i * 2.1) * 4
        val tX = sx - dirX * t * 18 + perpX * spread
        val tY = sy - dirY * t * 11 + perpY * spread * 0.6 + t * 5.0
        val a = Math.max(0.0, 0.5 * (1.0 - t) * pulse)
        val ds = 2.0 + (1.0 - t) * 1.5
        gc.setFill(Color.color(0.7, 0.8, 0.0, a))
        gc.fillOval(tX - ds, tY - ds * 0.6, ds * 2, ds * 1.2)
      }
    }
  }

  private def drawAcidBombProjectile(projectile: Projectile, camOffX: Double, camOffY: Double): Unit = {
    val projX = projectile.getX.toDouble; val projY = projectile.getY.toDouble
    val sx = worldToScreenX(projX, projY, camOffX)
    val sy = worldToScreenY(projX, projY, camOffY)
    val margin = 250.0
    if (sx > -margin && sx < getWidth + margin && sy > -margin && sy < getHeight + margin) {
      val phase = (animationTick + projectile.id * 23) * 0.35
      val pulse = 0.8 + 0.2 * Math.sin(phase * 2)
      val bounce = Math.abs(Math.sin(phase * 1.5)) * 3.0
      val r = 8.0
      // Bright green corrosive aura
      gc.setFill(Color.color(0.2, 1.0, 0.1, 0.06 * pulse))
      gc.fillOval(sx - r * 2.5, sy - r * 2, r * 5, r * 4)
      gc.setFill(Color.color(0.3, 0.9, 0.15, 0.1 * pulse))
      gc.fillOval(sx - r * 1.5, sy - r * 1.2, r * 3, r * 2.4)
      // Green bomb body
      gc.setFill(Color.color(0.2, 0.7, 0.1, 0.8))
      gc.fillOval(sx - r, sy - bounce - r, r * 2, r * 2)
      // Bright acid highlight
      gc.setFill(Color.color(0.5, 1.0, 0.3, 0.5))
      gc.fillOval(sx - r * 0.4, sy - bounce - r * 0.7, r * 0.8, r * 0.6)
      // Corrosive drips
      for (i <- 0 until 4) {
        val t = ((animationTick * 0.08 + i * 0.25 + projectile.id * 0.13) % 1.0)
        val dx = Math.sin(phase + i * 1.7) * 5
        val dripY = sy + t * 14
        val a = Math.max(0.0, 0.6 * (1.0 - t))
        gc.setFill(Color.color(0.3, 0.95, 0.15, a))
        gc.fillOval(sx + dx - 1.5, dripY - 1, 3, 2)
      }
    }
  }
}
