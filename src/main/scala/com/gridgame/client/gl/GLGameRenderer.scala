package com.gridgame.client.gl

import com.gridgame.client.ClientState
import com.gridgame.client.GameClient
import com.gridgame.client.render.{EntityCollector, GameCamera, IsometricTransform}
import com.gridgame.common.Constants
import com.gridgame.common.model._

import org.lwjgl.opengl.GL11._

import java.nio.FloatBuffer
import java.util.UUID
import scala.collection.mutable
import scala.jdk.CollectionConverters._

/**
 * OpenGL game renderer. Replaces GameCanvas for all in-game rendering.
 * Uses ShapeBatch for colored primitives, SpriteBatch for textured quads,
 * GLFontRenderer for text, and PostProcessor for screen effects.
 */
class GLGameRenderer(val client: GameClient) {
  val camera = new GameCamera()
  private val entityCollector = new EntityCollector()

  // Rendering infrastructure (initialized lazily on first render when GL context is current)
  private var initialized = false
  private var shapeBatch: ShapeBatch = _
  private var spriteBatch: SpriteBatch = _
  private var fontSmall: GLFontRenderer = _
  private var fontMedium: GLFontRenderer = _
  private var fontLarge: GLFontRenderer = _
  private var postProcessor: PostProcessor = _
  private var lightSystem: LightSystem = _
  private val damageNumbers = new DamageNumberSystem()
  private val weatherParticles = new ParticleSystem(1024)
  private val combatParticles = new ParticleSystem(512)

  // Previous health tracking for damage number detection
  private val prevHealthMap: mutable.Map[UUID, Int] = mutable.Map.empty
  // Smooth health drain animation tracking
  private val smoothHealthMap: mutable.Map[UUID, Float] = mutable.Map.empty

  // Tile overlay collection (reused each frame)
  private val specialTiles = new mutable.ArrayBuffer[(Int, Int, Int)]() // (wx, wy, tileId)

  // Projection matrix
  private var projection: FloatBuffer = _

  // Animation state
  private var animationTick: Int = 0
  private val FRAMES_PER_STEP = 5
  private val TILE_ANIM_SPEED = 15
  private val HIT_ANIMATION_MS = 500
  private val DEATH_ANIMATION_MS = 1200
  private val TELEPORT_ANIMATION_MS = 800

  // Remote player movement detection
  private val lastRemotePositions: mutable.Map[UUID, Position] = mutable.Map.empty
  private val remoteMovingUntil: mutable.Map[UUID, Long] = mutable.Map.empty

  private val HW = Constants.ISO_HALF_W
  private val HH = Constants.ISO_HALF_H

  // Particle tracking
  private var weatherSpawnAccum: Float = 0f
  private var prevLocalVX: Double = Double.NaN
  private var prevLocalVY: Double = Double.NaN
  private var footstepAccum: Float = 0f
  private val rng = new java.util.Random()

  // Item spawn/pickup animation tracking
  private val itemSpawnTimes: mutable.Map[Int, Long] = mutable.Map.empty
  private val itemLastWorldPos: mutable.Map[Int, (Int, Int, Int)] = mutable.Map.empty // id -> (cellX, cellY, colorRGB)
  private val drawnItemIdsThisFrame: mutable.Set[Int] = mutable.Set.empty

  // Cooldown ready flash tracking (Q=0, E=1)
  private val prevCooldownReady = Array(true, true)
  private val cooldownReadyFlashTime = Array(0L, 0L)

  // Current frame's camera offsets (set during render)
  private var camOffX: Double = 0
  private var camOffY: Double = 0
  private var canvasW: Double = 0
  private var canvasH: Double = 0

  // Pre-allocated HUD data (avoids per-frame Seq allocations)
  private val inventorySlotTypes = Array((ItemType.Heart, "1"), (ItemType.Star, "2"), (ItemType.Gem, "3"), (ItemType.Shield, "4"), (ItemType.Fence, "5"))
  private val abilityColors = Array((0.2f, 0.8f, 0.3f), (0.4f, 0.7f, 1f))

  // Tile dimensions for draw calls
  private val tileW = (HW * 2).toFloat // 40
  private val tileCellH = Constants.TILE_CELL_HEIGHT.toFloat // 56

  // Cached framebuffer size to skip redundant PostProcessor.resize() calls
  private var lastFbWidth = 0
  private var lastFbHeight = 0

  // Pre-allocated arrays for aim arrow (avoids per-frame allocations)
  private val _aimGlowXs = new Array[Float](22)  // glowN=10 → 10*2+2=22
  private val _aimGlowYs = new Array[Float](22)
  private val _aimBodyXs = new Array[Float](42)   // bodyN=20 → 20*2+2=42
  private val _aimBodyYs = new Array[Float](42)

  // Track death IDs that have already spawned a particle burst
  private val deathBurstSpawned = new mutable.HashSet[Any]()

  // Pre-allocated arrays for item shapes
  private val _starXs = new Array[Float](10)
  private val _starYs = new Array[Float](10)
  // Pre-allocated arrays for cooldown sweep polygon
  private val _sweepXs = new Array[Float](34)
  private val _sweepYs = new Array[Float](34)
  // Pre-allocated arrays for item shape highlight
  private val _hlXs = new Array[Float](10)
  private val _hlYs = new Array[Float](10)

  // ── Batch management ──
  // Track active batch to avoid redundant begin/end pairs.
  // Individual drawing methods call beginShapes()/beginSprites() instead of begin/end.
  // Batches are ended by switching type, by endAll(), or at render phase boundaries.
  private var _shapeActive = false
  private var _spriteActive = false

  private def beginShapes(): Unit = {
    if (_spriteActive) { spriteBatch.end(); _spriteActive = false }
    if (!_shapeActive) {
      shapeBatch.begin(projection)
      _shapeActive = true
    }
  }

  private def beginSprites(): Unit = {
    if (_shapeActive) { shapeBatch.end(); _shapeActive = false }
    if (!_spriteActive) {
      spriteBatch.begin(projection)
      _spriteActive = true
    }
  }

  private def endAll(): Unit = {
    if (_shapeActive) { shapeBatch.end(); _shapeActive = false }
    if (_spriteActive) { spriteBatch.end(); _spriteActive = false }
  }

  private def ensureInitialized(width: Int, height: Int): Unit = {
    if (initialized) return
    val colorShader = new ShaderProgram(ShaderProgram.COLOR_VERT, ShaderProgram.COLOR_FRAG)
    val textureShader = new ShaderProgram(ShaderProgram.TEXTURE_VERT, ShaderProgram.TEXTURE_FRAG)
    shapeBatch = new ShapeBatch(colorShader)
    spriteBatch = new SpriteBatch(textureShader)
    fontSmall = new GLFontRenderer(14)
    fontMedium = new GLFontRenderer(22)
    fontLarge = new GLFontRenderer(44)
    postProcessor = new PostProcessor(width, height)
    lightSystem = new LightSystem(width, height)
    initialized = true
  }

  def render(deltaSec: Double, fbWidth: Int, fbHeight: Int, windowWidth: Int, windowHeight: Int): Unit = {
    ensureInitialized(fbWidth, fbHeight)
    animationTick += 1
    val dt = deltaSec.toFloat

    // Update particle systems
    weatherParticles.update(dt)
    combatParticles.update(dt)

    val zoom = Constants.CAMERA_ZOOM
    canvasW = windowWidth / zoom
    canvasH = windowHeight / zoom

    val localDeathTime = client.getLocalDeathTime
    val localDeathAnimActive = client.getIsDead && localDeathTime > 0 &&
      (System.currentTimeMillis() - localDeathTime) < DEATH_ANIMATION_MS

    // In non-lobby mode, show full game over screen when dead (after death anim)
    if (client.getIsDead && !localDeathAnimActive && !client.isRespawning) {
      renderGameOverScreen(fbWidth, fbHeight)
      return
    }

    val localPos = client.getLocalPosition
    val world = client.getWorld

    // Use interpolated position for smooth camera tracking between grid tiles
    val (visualPosX, visualPosY) = client.getVisualPosition

    // Update camera with smooth interpolation
    val (cox, coy) = camera.update(
      visualPosX, visualPosY,
      deltaSec, canvasW, canvasH
    )
    camOffX = cox
    camOffY = coy

    // Post-processing: render to FBO (only resize when dimensions actually change)
    if (fbWidth != lastFbWidth || fbHeight != lastFbHeight) {
      postProcessor.resize(fbWidth, fbHeight)
      lightSystem.resize(fbWidth, fbHeight)
      lastFbWidth = fbWidth
      lastFbHeight = fbHeight
    }

    // Dynamic lighting: clear lights and set ambient for current background
    lightSystem.clear()
    lightSystem.setAmbientForBackground(world.background)

    // Update damage numbers
    damageNumbers.update(deltaSec.toFloat)

    postProcessor.beginScene()

    // Set up projection for zoomed world-space rendering
    projection = Matrix4.orthographic(0f, canvasW.toFloat, canvasH.toFloat, 0f)

    // === Background ===
    beginShapes()
    renderBackground(world.background)

    // === Weather particles ===
    spawnWeatherParticles(world.background, dt)
    weatherParticles.render(shapeBatch)

    // === Calculate visible tile bounds (no allocations) ===
    val cx0 = IsometricTransform.screenToWorldX(0, 0, camOffX, camOffY)
    val cy0 = IsometricTransform.screenToWorldY(0, 0, camOffX, camOffY)
    val cx1 = IsometricTransform.screenToWorldX(canvasW, 0, camOffX, camOffY)
    val cy1 = IsometricTransform.screenToWorldY(canvasW, 0, camOffX, camOffY)
    val cx2 = IsometricTransform.screenToWorldX(0, canvasH, camOffX, camOffY)
    val cy2 = IsometricTransform.screenToWorldY(0, canvasH, camOffX, camOffY)
    val cx3 = IsometricTransform.screenToWorldX(canvasW, canvasH, camOffX, camOffY)
    val cy3 = IsometricTransform.screenToWorldY(canvasW, canvasH, camOffX, camOffY)
    val minWX = Math.min(Math.min(cx0, cx1), Math.min(cx2, cx3))
    val maxWX = Math.max(Math.max(cx0, cx1), Math.max(cx2, cx3))
    val minWY = Math.min(Math.min(cy0, cy1), Math.min(cy2, cy3))
    val maxWY = Math.max(Math.max(cy0, cy1), Math.max(cy2, cy3))
    val startX = Math.max(0, minWX.floor.toInt - 6)
    val endX = Math.min(world.width - 1, maxWX.ceil.toInt + 6)
    val startY = Math.max(0, minWY.floor.toInt - 6)
    val endY = Math.min(world.height - 1, maxWY.ceil.toInt + 6)

    val tileFrame = (animationTick / TILE_ANIM_SPEED) % GLTileRenderer.getNumFrames
    val cellH = Constants.TILE_CELL_HEIGHT

    // === Collect entities by cell ===
    drawnItemIdsThisFrame.clear()
    val localVX = camera.visualX
    val localVY = camera.visualY
    val entitiesByCell = entityCollector.collect(
      client, deltaSec,
      item => drawSingleItem(item),
      proj => drawSingleProjectile(proj),
      (player, rvx, rvy) => drawPlayerInterp(player, rvx, rvy),
      () => drawLocalPlayer(localVX, localVY),
      () => drawDeathEffect(
        worldToScreenX(localVX, localVY), worldToScreenY(localVX, localVY),
        client.getLocalColorRGB, client.getLocalDeathTime, client.selectedCharacterId
      ),
      localVX, localVY, localDeathAnimActive,
      startX, endX, startY, endY
    )

    // === Phase 1: Ground tiles ===
    // Use fixed position-based variant (no tileFrame) so ground doesn't animate
    val numTileFrames = GLTileRenderer.getNumFrames
    specialTiles.clear()
    beginSprites()
    for (wy <- startY to endY; wx <- startX to endX) {
      val tile = world.getTile(wx, wy)
      if (tile.walkable) {
        val variantFrame = ((wx * 7 + wy * 13) & 0x7FFFFFFF) % numTileFrames
        val region = GLTileRenderer.getTileRegion(tile.id, variantFrame)
        if (region != null) {
          val sx = worldToScreenX(wx, wy).toFloat
          val sy = worldToScreenY(wx, wy).toFloat
          spriteBatch.draw(region, sx - HW, sy - (cellH - HH), tileW, tileCellH)
        }
        // Collect special tiles for overlay pass
        val tid = tile.id
        if (tid == 1 || tid == 7 || tid == 9 || tid == 10 || tid == 15 || tid == 18 || tid == 24) {
          specialTiles += ((wx, wy, tid))
        }
        // Lava and energy field tiles emit light
        if (tid == 10) { // Lava
          val lsx = worldToScreenX(wx, wy).toFloat
          val lsy = worldToScreenY(wx, wy).toFloat
          val lavaPulse = (0.8 + 0.2 * Math.sin(animationTick * 0.06 + wx * 1.3 + wy * 0.7)).toFloat
          lightSystem.addLight(lsx, lsy, 40f, 1f, 0.5f, 0.1f, 0.12f * lavaPulse)
        } else if (tid == 15) { // EnergyField
          val esx = worldToScreenX(wx, wy).toFloat
          val esy = worldToScreenY(wx, wy).toFloat
          lightSystem.addLight(esx, esy, 35f, 0.5f, 0.2f, 0.8f, 0.08f)
        }
      }
    }

    // === Animated tile overlays ===
    if (specialTiles.nonEmpty) {
      drawTileOverlays()
    }

    // === Water reflections ===
    drawWaterReflections()

    // === Aim arrow ===
    drawAimArrow()

    // === Elevated tile edge shadows ===
    beginShapes()
    for (wy <- startY to endY; wx <- startX to endX) {
      val tile = world.getTile(wx, wy)
      if (!tile.walkable) {
        drawElevatedTileShadow(wx, wy, world)
      }
    }

    // === Phase 2: Elevated tiles + entities interleaved by depth ===
    for (wy <- startY to endY; wx <- startX to endX) {
      val tile = world.getTile(wx, wy)
      if (!tile.walkable) {
        val variantFrame = (tileFrame + wx * 7 + wy * 13) % numTileFrames
        val region = GLTileRenderer.getTileRegion(tile.id, variantFrame)
        if (region != null) {
          beginSprites()
          val sx = worldToScreenX(wx, wy).toFloat
          val sy = worldToScreenY(wx, wy).toFloat
          spriteBatch.draw(region, sx - HW, sy - (cellH - HH), tileW, tileCellH)
        }
      }
      entitiesByCell.remove((wx, wy)).foreach(_.foreach(_()))
    }
    // Any entities outside visible range
    entitiesByCell.values.foreach(_.foreach(_()))

    // === Item pickup detection ===
    detectItemChanges()

    // === Overlay animations ===
    drawDeathAnimations()
    drawTeleportAnimations()
    drawExplosionAnimations()
    drawAoeSplashAnimations()

    // === Gameplay particles (trails, footsteps, impacts) ===
    spawnGameplayParticles(dt)
    beginShapes()
    combatParticles.render(shapeBatch)

    // === Damage number detection ===
    detectDamageNumbers()

    // === Render damage numbers (in world space) ===
    if (damageNumbers.hasActive) {
      beginSprites()
      damageNumbers.render(fontMedium, spriteBatch, camOffX, camOffY,
        (wx: Double, wy: Double) => worldToScreenX(wx, wy),
        (wx: Double, wy: Double) => worldToScreenY(wx, wy))
    }

    // === Populate lights from entities ===
    populateLights()

    // === End scene, render light map, apply post-processing ===
    // Damage perimeter vignette (replaces full-screen overlay)
    val localHitTime = client.getPlayerHitTime(client.getLocalPlayerId)
    postProcessor.overlayA = 0f
    postProcessor.damageVignette = 0f
    postProcessor.chromaticAberration = 0f
    if (localHitTime > 0) {
      val elapsed = System.currentTimeMillis() - localHitTime
      if (elapsed < HIT_ANIMATION_MS) {
        val progress = elapsed.toDouble / HIT_ANIMATION_MS
        val intensity = 1f - progress.toFloat
        postProcessor.damageVignette = intensity
        postProcessor.chromaticAberration = 0.004f * intensity
      }
    }

    // Screen distortion from nearby explosions
    updateExplosionDistortion()

    endAll()

    // Render light map
    lightSystem.renderLightMap(shapeBatch, canvasW.toFloat, canvasH.toFloat)
    postProcessor.lightMapTexture = lightSystem.getLightMapTexture
    postProcessor.useLightMap = true

    postProcessor.animationTime = animationTick.toFloat
    postProcessor.endScene(fbWidth, fbHeight)

    // === HUD (rendered at screen-pixel scale, not zoomed) ===
    projection = Matrix4.orthographic(0f, windowWidth.toFloat, windowHeight.toFloat, 0f)
    renderHUD(windowWidth, windowHeight)

    // === Respawn countdown ===
    if (client.getIsDead && client.isRespawning && !localDeathAnimActive) {
      renderRespawnCountdown(windowWidth, windowHeight)
    }
    endAll()
  }

  // ═══════════════════════════════════════════════════════════════════
  //  COORDINATE TRANSFORMS
  // ═══════════════════════════════════════════════════════════════════

  private def worldToScreenX(wx: Double, wy: Double): Double =
    IsometricTransform.worldToScreenX(wx, wy, camOffX)

  private def worldToScreenY(wx: Double, wy: Double): Double =
    IsometricTransform.worldToScreenY(wx, wy, camOffY)

  // ═══════════════════════════════════════════════════════════════════
  //  BACKGROUND RENDERER (5a)
  // ═══════════════════════════════════════════════════════════════════

  private def hash(seed: Int): Double = {
    val x = Math.sin(seed.toDouble * 127.1 + 311.7) * 43758.5453
    x - x.floor
  }

  private def renderBackground(background: String): Unit = {
    background match {
      case "sky"       => drawSkyBg()
      case "cityscape" => drawCityscapeBg()
      case "space"     => drawSpaceBg()
      case "desert"    => drawDesertBg()
      case "ocean"     => drawOceanBg()
      case _           => drawSkyBg()
    }
  }

  private def drawSkyBg(): Unit = {
    val w = canvasW.toFloat; val h = canvasH.toFloat
    val bands = 12
    for (i <- 0 until bands) {
      val t = i.toFloat / bands
      val r = 0.35f + t * 0.15f
      val g = 0.55f + t * 0.15f
      val b = 0.92f - t * 0.15f
      val y0 = h * t
      val bandH = h / bands + 1
      shapeBatch.fillRect(0, y0, w, bandH, r, g, b, 1f)
    }
    // Clouds
    val px = (camOffX * 0.03).toFloat
    val py = (camOffY * 0.02).toFloat
    val layers = Array((0.10f, 0.15f, 1.6f, 0.18f, 6), (0.20f, 0.3f, 1.2f, 0.25f, 8), (0.05f, 0.5f, 1.0f, 0.35f, 7))
    for ((yFrac, speed, scale, alpha, count) <- layers) {
      for (i <- 0 until count) {
        val seed = yFrac.hashCode + i * 73
        val baseX = hash(seed).toFloat * w * 1.5f - w * 0.25f
        val baseY = h * yFrac + hash(seed + 1).toFloat * h * 0.25f
        val drift = ((animationTick * speed + px * (1f + yFrac)) % (w + 300)) - 150
        val cx = ((baseX + drift) % (w + 300)) - 150
        val cy = baseY + py * (0.5f + yFrac) + Math.sin(animationTick * 0.01 + i).toFloat * 3
        drawCloud(cx, cy, scale, alpha)
      }
    }
  }

  private def drawCloud(cx: Float, cy: Float, scale: Float, alpha: Float): Unit = {
    val s = 30 * scale
    shapeBatch.fillOval(cx, cy - s * 0.15f, s * 1.5f, s * 0.35f, 1f, 1f, 1f, alpha)
    shapeBatch.fillOval(cx + s * 0.1f, cy - s * 0.3f, s * 0.9f, s * 0.4f, 1f, 1f, 1f, alpha)
    shapeBatch.fillOval(cx + s * 0.5f, cy - s * 0.25f, s * 0.7f, s * 0.35f, 1f, 1f, 1f, alpha)
    shapeBatch.fillOval(cx - s * 0.35f, cy - s * 0.075f, s * 1.25f, s * 0.25f, 1f, 1f, 1f, alpha)
  }

  private def drawCityscapeBg(): Unit = {
    val w = canvasW.toFloat; val h = canvasH.toFloat
    // Dark gradient sky
    for (i <- 0 until 12) {
      val t = i.toFloat / 12
      shapeBatch.fillRect(0, h * t, w, h / 12 + 1, 0.05f + t * 0.08f, 0.02f + t * 0.05f, 0.15f - t * 0.05f, 1f)
    }
    // Horizon glow
    shapeBatch.fillRect(0, h * 0.55f, w, h * 0.15f, 0.2f, 0.08f, 0.3f, 0.3f)
    shapeBatch.fillRect(0, h * 0.5f, w, h * 0.1f, 0.3f, 0.1f, 0.4f, 0.15f)

    val px = (camOffX * 0.02).toFloat
    // Buildings
    for (i <- 0 until 30) {
      val bx = ((hash(i * 7).toFloat * (w + 200) - 100 + px * 0.5f) % (w + 200)) - 100
      val bw = 25 + hash(i * 7 + 1).toFloat * 45
      val bh = h * (0.12f + hash(i * 7 + 2).toFloat * 0.30f)
      val by = h - bh
      shapeBatch.fillRect(bx, by, bw, bh, 0.06f, 0.04f, 0.1f, 0.9f)
      // Windows
      var wy = by + 5
      var windowIdx = 0
      while (wy < h - 8) {
        var wx = bx + 4
        while (wx < bx + bw - 5) {
          val windowSeed = i * 1000 + windowIdx
          val isLit = hash(windowSeed) > 0.4
          if (isLit) {
            val flicker = if (hash(windowSeed + 500) > 0.7)
              (0.6 + 0.4 * Math.sin(animationTick * 0.05 + hash(windowSeed + 300) * 20)).toFloat
            else 1f
            val warmth = hash(windowSeed + 100)
            val (wr, wg, wb) = if (warmth < 0.6) (1f, 0.85f, 0.3f)
              else if (warmth < 0.85) (0.8f, 0.85f, 1f)
              else if (hash(windowSeed + 200) < 0.5) (0f, 0.9f, 1f)
              else (1f, 0.2f, 0.8f)
            shapeBatch.fillRect(wx, wy, 3f, 4f, wr, wg, wb, 0.7f * flicker)
          }
          wx += 7
          windowIdx += 1
        }
        wy += 8
      }
    }
    // Near buildings
    for (i <- 0 until 10) {
      val bx = ((hash(i * 13 + 200).toFloat * (w + 300) - 150 + px) % (w + 300)) - 150
      val bw = 50 + hash(i * 13 + 201).toFloat * 60
      val bh = h * (0.08f + hash(i * 13 + 202).toFloat * 0.15f)
      shapeBatch.fillRect(bx, h - bh, bw, bh, 0.03f, 0.02f, 0.06f, 0.95f)
    }
  }

  private def drawSpaceBg(): Unit = {
    val w = canvasW.toFloat; val h = canvasH.toFloat
    for (i <- 0 until 8) {
      val t = i.toFloat / 8
      val b = 0.02f + t * 0.04f
      shapeBatch.fillRect(0, h * t, w, h / 8 + 1, b * 0.3f, b * 0.2f, b, 1f)
    }
    val px = (camOffX * 0.01).toFloat; val py = (camOffY * 0.01).toFloat

    // Nebulae
    drawNebula(w * 0.3f + px * 2, h * 0.35f + py * 2, w * 0.35f, h * 0.3f, 0.4f, 0.1f, 0.6f,
      (0.06 + 0.02 * Math.sin(animationTick * 0.008)).toFloat)
    drawNebula(w * 0.75f + px * 1.5f, h * 0.6f + py * 1.5f, w * 0.25f, h * 0.2f, 0.1f, 0.3f, 0.5f,
      (0.04 + 0.015 * Math.sin(animationTick * 0.01 + 2)).toFloat)

    // Stars with additive blending for glow
    shapeBatch.setAdditiveBlend(true)
    for (i <- 0 until 120) {
      val sx = ((hash(i * 3).toFloat * w + px * (0.5f + hash(i * 3 + 10).toFloat * 2)) % w)
      val sy = ((hash(i * 3 + 1).toFloat * h + py * (0.5f + hash(i * 3 + 11).toFloat * 2)) % h)
      val baseBrightness = 0.3f + hash(i * 3 + 2).toFloat * 0.7f
      val twinkleSpeed = 0.02f + hash(i * 3 + 5).toFloat * 0.04f
      val twinklePhase = hash(i * 3 + 6).toFloat * Math.PI.toFloat * 2
      val twinkle = (0.5 + 0.5 * Math.sin(animationTick * twinkleSpeed + twinklePhase)).toFloat
      val brightness = baseBrightness * (0.4f + 0.6f * twinkle)
      val colorSeed = hash(i * 3 + 7)
      val (sr, sg, sb) = if (colorSeed < 0.6) (1f, 1f, 1f)
        else if (colorSeed < 0.8) (0.7f, 0.8f, 1f) else (1f, 0.95f, 0.7f)
      val size = 1f + hash(i * 3 + 8).toFloat * 2f
      // Star core
      shapeBatch.fillOval(sx, sy, size, size, sr, sg, sb, brightness)
      // Glow halo (additive)
      if (baseBrightness > 0.6f) {
        shapeBatch.fillOvalSoft(sx, sy, size * 3, size * 3, sr, sg, sb, brightness * 0.3f, 0f, 12)
      }
      // Cross glint for bright stars
      if (baseBrightness > 0.8f && twinkle > 0.7f) {
        val glintLen = size * 2.5f * twinkle
        shapeBatch.strokeLine(sx - glintLen, sy, sx + glintLen, sy, 0.5f, sr, sg, sb, brightness * 0.4f)
        shapeBatch.strokeLine(sx, sy - glintLen, sx, sy + glintLen, 0.5f, sr, sg, sb, brightness * 0.4f)
      }
    }
    shapeBatch.setAdditiveBlend(false)

    // Moon
    val moonX = w * 0.8f + px * 3; val moonY = h * 0.2f + py * 3; val moonR = 25f
    shapeBatch.fillOval(moonX, moonY, moonR, moonR, 0.25f, 0.22f, 0.35f, 0.6f)
    shapeBatch.fillOval(moonX + moonR * 0.4f, moonY, moonR, moonR, 0.02f, 0.01f, 0.05f, 0.7f)
  }

  private def drawNebula(cx: Float, cy: Float, rw: Float, rh: Float, nr: Float, ng: Float, nb: Float, alpha: Float): Unit = {
    shapeBatch.setAdditiveBlend(true)
    for (i <- 5 to 1 by -1) {
      val t = i.toFloat / 5
      shapeBatch.fillOvalSoft(cx, cy, rw * t * 0.5f, rh * t * 0.5f, nr, ng, nb, alpha * t * 0.6f, 0f, 16)
    }
    shapeBatch.setAdditiveBlend(false)
  }

  private def drawDesertBg(): Unit = {
    val w = canvasW.toFloat; val h = canvasH.toFloat
    for (i <- 0 until 12) {
      val t = i.toFloat / 12
      val r = clamp(0.95f - t * 0.25f); val g = clamp(0.65f - t * 0.20f); val b = clamp(0.25f - t * 0.10f)
      shapeBatch.fillRect(0, h * t, w, h / 12 + 1, r, g, b, 1f)
    }
    val px = (camOffX * 0.02).toFloat
    // Sun with glow
    val sunX = w * 0.75f + px * 0.5f; val sunY = h * 0.18f; val sunR = 35f
    val pulse = (0.9 + 0.1 * Math.sin(animationTick * 0.015)).toFloat
    shapeBatch.setAdditiveBlend(true)
    shapeBatch.fillOvalSoft(sunX, sunY, sunR * 5, sunR * 5, 1f, 0.9f, 0.5f, 0.04f * pulse, 0f, 24)
    shapeBatch.fillOvalSoft(sunX, sunY, sunR * 3, sunR * 3, 1f, 0.85f, 0.4f, 0.08f * pulse, 0f, 24)
    shapeBatch.fillOvalSoft(sunX, sunY, sunR * 1.8f, sunR * 1.8f, 1f, 0.8f, 0.3f, 0.15f * pulse, 0f, 24)
    shapeBatch.setAdditiveBlend(false)
    shapeBatch.fillOval(sunX, sunY, sunR, sunR, 1f, 0.95f, 0.7f, 0.9f)
    // Heat shimmer
    for (i <- 0 until 15) {
      val ly = h * 0.45f + i * h * 0.035f
      val phase = animationTick * 0.03f + i * 0.7f
      val points = 30
      val xStep = w / points
      for (j <- 0 until points) {
        val x1 = j * xStep; val x2 = (j + 1) * xStep
        val y1 = ly + Math.sin(phase + j * 0.3).toFloat * 2.5f
        val y2 = ly + Math.sin(phase + (j + 1) * 0.3).toFloat * 2.5f
        shapeBatch.strokeLine(x1, y1, x2, y2, 1.5f, 1f, 0.9f, 0.6f, 0.06f)
      }
    }
    // Sand dunes
    drawDuneLayer(w, h, 0.70f, 0.65f, 0.45f, 0.20f, 0.7f, px * 0.3f, 0)
    drawDuneLayer(w, h, 0.60f, 0.50f, 0.30f, 0.12f, 0.85f, px * 0.6f, 100)
  }

  private def drawDuneLayer(w: Float, h: Float, r: Float, g: Float, b: Float, alpha: Float,
                            yFrac: Float, parallax: Float, seedOff: Int): Unit = {
    val baseY = h * yFrac
    val points = 60
    val xStep = (w + 40) / points
    val xs = new Array[Float](points + 3)
    val ys = new Array[Float](points + 3)
    xs(0) = -20; ys(0) = h + 10
    for (i <- 0 to points) {
      xs(i + 1) = -20 + i * xStep + parallax
      ys(i + 1) = (baseY +
        Math.sin(i * 0.15 + seedOff * 0.1) * h * 0.04 +
        Math.sin(i * 0.07 + seedOff * 0.3 + animationTick * 0.002) * h * 0.02 +
        Math.sin(i * 0.3 + seedOff * 0.5) * h * 0.015).toFloat
    }
    xs(points + 2) = w + 20; ys(points + 2) = h + 10
    shapeBatch.fillPolygon(xs, ys, r, g, b, alpha)
  }

  private def drawOceanBg(): Unit = {
    val w = canvasW.toFloat; val h = canvasH.toFloat
    for (i <- 0 until 12) {
      val t = i.toFloat / 12
      shapeBatch.fillRect(0, h * t, w, h / 12 + 1, 0.02f + t * 0.04f, 0.08f + t * 0.12f, 0.25f + t * 0.15f, 1f)
    }
    val px = (camOffX * 0.02).toFloat
    // Caustics
    shapeBatch.setAdditiveBlend(true)
    for (i <- 0 until 18) {
      val cx = ((hash(i * 5).toFloat * w * 1.3f - w * 0.15f + px * (0.3f + hash(i * 5 + 3).toFloat)) % (w + 100)) - 50
      val cy = hash(i * 5 + 1).toFloat * h
      val rad = 30 + hash(i * 5 + 2).toFloat * 60
      val pulse = (0.5 + 0.5 * Math.sin(animationTick * 0.02 + hash(i * 5 + 4) * Math.PI * 2)).toFloat
      shapeBatch.fillOvalSoft(cx, cy, rad, rad, 0.2f, 0.6f, 0.8f, 0.03f + 0.03f * pulse, 0f, 16)
    }
    shapeBatch.setAdditiveBlend(false)
    // Wave layers
    drawWaveLayer(w, h, 0.15f, px, 0.05f, 0.18f, 0.38f, 0.15f, 0.008f, 8f, 0)
    drawWaveLayer(w, h, 0.30f, px, 0.04f, 0.15f, 0.35f, 0.12f, 0.012f, 6f, 50)
    drawWaveLayer(w, h, 0.50f, px, 0.03f, 0.12f, 0.32f, 0.10f, 0.015f, 5f, 100)
    drawWaveLayer(w, h, 0.65f, px, 0.03f, 0.10f, 0.30f, 0.10f, 0.018f, 4.5f, 150)
    drawWaveLayer(w, h, 0.80f, px, 0.02f, 0.08f, 0.28f, 0.08f, 0.022f, 4f, 200)
    // Foam
    shapeBatch.setAdditiveBlend(true)
    for (i <- 0 until 12) {
      val foamY = h * (0.2f + hash(i * 11).toFloat * 0.7f)
      val foamX = ((hash(i * 11 + 1).toFloat * w * 1.5f - w * 0.25f + animationTick * (0.2f + hash(i * 11 + 2).toFloat * 0.3f) + px) % (w + 200)) - 100
      val foamW = 20 + hash(i * 11 + 3).toFloat * 50
      val foamH = 2 + hash(i * 11 + 4).toFloat * 3
      val pulse = (0.5 + 0.5 * Math.sin(animationTick * 0.025 + hash(i * 11 + 5) * 10)).toFloat
      shapeBatch.fillOval(foamX + foamW / 2, foamY + foamH / 2, foamW / 2, foamH / 2, 0.6f, 0.8f, 0.9f, 0.08f * pulse)
    }
    shapeBatch.setAdditiveBlend(false)
  }

  private def drawWaveLayer(w: Float, h: Float, yFrac: Float, parallax: Float,
                            r: Float, g: Float, b: Float, alpha: Float,
                            speed: Float, amplitude: Float, seedOff: Int): Unit = {
    val baseY = h * yFrac
    val points = 60
    val xStep = (w + 40) / points
    val n = points + 3 // exact vertex count needed
    val xs = new Array[Float](n)
    val ys = new Array[Float](n)
    xs(0) = -20; ys(0) = h + 10
    for (i <- 0 to points) {
      xs(i + 1) = -20 + i * xStep + parallax * (0.3f + yFrac)
      ys(i + 1) = (baseY +
        Math.sin(i * 0.12 + seedOff * 0.1 + animationTick * speed) * amplitude +
        Math.sin(i * 0.25 + seedOff * 0.3 + animationTick * speed * 1.3) * amplitude * 0.5 +
        Math.sin(i * 0.06 + seedOff * 0.7 + animationTick * speed * 0.7) * amplitude * 1.5).toFloat
    }
    xs(points + 2) = w + 20; ys(points + 2) = h + 10
    shapeBatch.fillPolygon(xs, ys, r, g, b, alpha)
  }

  // ═══════════════════════════════════════════════════════════════════
  //  PLAYER RENDERING (5c)
  // ═══════════════════════════════════════════════════════════════════

  private def drawShadow(screenX: Double, screenY: Double): Unit = {
    beginShapes()
    // Soft directional shadow — offset toward light direction, blue-tinted for atmosphere
    shapeBatch.fillOvalSoft(screenX.toFloat + 2f, screenY.toFloat + 1f,
      20f, 8f, 0.02f, 0.01f, 0.05f, 0.35f, 0f, 14)
  }

  private def drawPlayerInterp(player: Player, wx: Double, wy: Double): Unit = {
    val screenX = worldToScreenX(wx, wy)
    val screenY = worldToScreenY(wx, wy)
    val displaySz = Constants.PLAYER_DISPLAY_SIZE_PX
    val spriteCenter = screenY - displaySz / 2.0

    // Movement detection
    val now = System.currentTimeMillis()
    val playerId = player.getId
    val pos = player.getPosition
    val lastPos = lastRemotePositions.get(playerId)
    if (lastPos.isEmpty || lastPos.get.getX != pos.getX || lastPos.get.getY != pos.getY) {
      remoteMovingUntil.put(playerId, now + 200)
    }
    lastRemotePositions.put(playerId, pos)
    val isMoving = now < remoteMovingUntil.getOrElse(playerId, 0L)

    val animSpeed = if (player.getChargeLevel > 0) {
      val chargePct = player.getChargeLevel / 100.0
      (FRAMES_PER_STEP * (1.0 + chargePct * 4.0)).toInt
    } else FRAMES_PER_STEP
    val frame = if (isMoving) (animationTick / animSpeed) % 4 else 0

    val playerIsPhased = player.isPhased

    // Pre-player effects
    if (!playerIsPhased) {
      if (player.hasShield) drawShieldBubble(screenX, spriteCenter)
      if (player.hasGemBoost) drawGemGlow(screenX, spriteCenter)
      drawChargingEffect(screenX, spriteCenter, player.getChargeLevel)
    }

    // Phased shimmer
    if (playerIsPhased) drawPhasedEffect(screenX, spriteCenter)

    drawShadow(screenX, screenY)

    // Sprite — flash red/white on hit
    val hitTime = client.getPlayerHitTime(playerId)
    val hitFlash = if (hitTime > 0) {
      val el = System.currentTimeMillis() - hitTime
      if (el < HIT_ANIMATION_MS) (1.0 - el.toDouble / HIT_ANIMATION_MS).toFloat else 0f
    } else 0f
    val region = GLSpriteGenerator.getSpriteRegion(player.getDirection, frame, player.getCharacterId)
    if (region != null) {
      val alpha = if (playerIsPhased) 0.4f else 1f
      val g = 1f - hitFlash * 0.7f
      val b = 1f - hitFlash * 0.7f
      beginSprites()
      spriteBatch.draw(region,
        (screenX - displaySz / 2.0).toFloat, (screenY - displaySz).toFloat,
        displaySz.toFloat, displaySz.toFloat, 1f, g, b, alpha)
    }

    // Post-player effects
    if (player.isFrozen) drawFrozenEffect(screenX, spriteCenter)
    if (player.isRooted) drawRootedEffect(screenX, spriteCenter)
    if (player.isSlowed) drawSlowedEffect(screenX, spriteCenter)
    if (player.isBurning) drawBurnEffect(screenX, spriteCenter)
    if (player.hasSpeedBoost) drawSpeedBoostEffect(screenX, spriteCenter)
    drawHitEffect(screenX, spriteCenter, hitTime)

    // Health bar + name
    drawHealthBar(screenX, screenY - displaySz, player.getHealth, player.getMaxHealth, player.getTeamId, player.getId)
    val charName = CharacterDef.get(player.getCharacterId).displayName
    drawCharacterName(charName, screenX, screenY - displaySz)
  }

  private def drawLocalPlayer(wx: Double, wy: Double): Unit = {
    val screenX = worldToScreenX(wx, wy)
    val screenY = worldToScreenY(wx, wy)
    val displaySz = Constants.PLAYER_DISPLAY_SIZE_PX
    val spriteCenter = screenY - displaySz / 2.0
    val localIsPhased = client.isPhased

    if (!localIsPhased) {
      if (client.hasShield) drawShieldBubble(screenX, spriteCenter)
      if (client.hasGemBoost) drawGemGlow(screenX, spriteCenter)
      drawChargingEffect(screenX, spriteCenter, client.getChargeLevel)
    }

    if (localIsPhased) drawPhasedEffect(screenX, spriteCenter)

    // Dash afterimage
    if (client.isSwooping) drawSwoopTrail(wx, wy, displaySz)

    drawShadow(screenX, screenY)

    val animSpeed = if (client.isCharging) {
      (FRAMES_PER_STEP * (1.0 + client.getChargeLevel / 100.0 * 4.0)).toInt
    } else FRAMES_PER_STEP
    val frame = if (client.getIsMoving) (animationTick / animSpeed) % 4 else 0
    // Sprite — flash red/white on hit
    val localHitTime = client.getPlayerHitTime(client.getLocalPlayerId)
    val localHitFlash = if (localHitTime > 0) {
      val el = System.currentTimeMillis() - localHitTime
      if (el < HIT_ANIMATION_MS) (1.0 - el.toDouble / HIT_ANIMATION_MS).toFloat else 0f
    } else 0f
    val region = GLSpriteGenerator.getSpriteRegion(client.getLocalDirection, frame, client.selectedCharacterId)
    if (region != null) {
      val alpha = if (localIsPhased) 0.4f else 1f
      val g = 1f - localHitFlash * 0.7f
      val b = 1f - localHitFlash * 0.7f
      beginSprites()
      spriteBatch.draw(region,
        (screenX - displaySz / 2.0).toFloat, (screenY - displaySz).toFloat,
        displaySz.toFloat, displaySz.toFloat, 1f, g, b, alpha)
    }

    drawCastFlash(screenX, spriteCenter)
    if (client.isFrozen) drawFrozenEffect(screenX, spriteCenter)
    if (client.isRooted) drawRootedEffect(screenX, spriteCenter)
    if (client.isSlowed) drawSlowedEffect(screenX, spriteCenter)
    if (client.isBurning) drawBurnEffect(screenX, spriteCenter)
    if (client.hasSpeedBoost) drawSpeedBoostEffect(screenX, spriteCenter)

    drawHitEffect(screenX, spriteCenter, localHitTime)

    drawHealthBar(screenX, screenY - displaySz, client.getLocalHealth, client.getSelectedCharacterMaxHealth, client.localTeamId, client.getLocalPlayerId)
    val charName = client.getSelectedCharacterDef.displayName
    drawCharacterName(charName, screenX, screenY - displaySz)
  }

  private def drawSwoopTrail(wx: Double, wy: Double, displaySz: Int): Unit = {
    val swoopProg = client.getSwoopProgress
    val sx0 = client.getSwoopStartX.toDouble
    val sy0 = client.getSwoopStartY.toDouble
    val sx1 = client.getSwoopTargetX.toDouble
    val sy1 = client.getSwoopTargetY.toDouble
    val ghostRegion = GLSpriteGenerator.getSpriteRegion(client.getLocalDirection,
      (animationTick / FRAMES_PER_STEP) % 4, client.selectedCharacterId)
    if (ghostRegion == null) return

    beginSprites()
    for (i <- 0 until 5) {
      val ghostT = swoopProg - (i + 1) * 0.12
      if (ghostT > 0 && ghostT < 1.0) {
        val gx = sx0 + (sx1 - sx0) * ghostT
        val gy = sy0 + (sy1 - sy0) * ghostT
        val gsx = worldToScreenX(gx, gy)
        val gsy = worldToScreenY(gx, gy)
        val alpha = Math.max(0.03f, (0.25 - i * 0.04).toFloat)
        val scale = (1.0 - i * 0.04).toFloat
        val gSize = displaySz * scale
        spriteBatch.draw(ghostRegion,
          (gsx - gSize / 2).toFloat, (gsy - gSize).toFloat,
          gSize, gSize, 1f, 1f, 1f, alpha)
      }
    }
  }

  // ═══════════════════════════════════════════════════════════════════
  //  STATUS EFFECTS (5f)
  // ═══════════════════════════════════════════════════════════════════

  private def drawShieldBubble(cx: Double, cy: Double): Unit = {
    beginShapes()
    val pulse = (0.7 + 0.3 * Math.sin(animationTick * 0.1)).toFloat
    shapeBatch.fillOvalSoft(cx.toFloat, cy.toFloat, 22f, 18f, 0.3f, 0.6f, 1f, 0.05f * pulse, 0.15f * pulse, 24)
    val segs = 24
    val step = (2.0 * Math.PI / segs).toFloat
    var angle = 0f
    for (_ <- 0 until segs) {
      val x1 = cx.toFloat + 22 * Math.cos(angle).toFloat
      val y1 = cy.toFloat + 18 * Math.sin(angle).toFloat
      val x2 = cx.toFloat + 22 * Math.cos(angle + step).toFloat
      val y2 = cy.toFloat + 18 * Math.sin(angle + step).toFloat
      shapeBatch.strokeLine(x1, y1, x2, y2, 1.5f, 0.4f, 0.7f, 1f, 0.25f * pulse)
      angle += step
    }
  }

  private def drawGemGlow(cx: Double, cy: Double): Unit = {
    beginShapes()
    shapeBatch.setAdditiveBlend(true)
    val pulse = (0.7 + 0.3 * Math.sin(animationTick * 0.08)).toFloat
    shapeBatch.fillOvalSoft(cx.toFloat, cy.toFloat, 18f, 14f, 0f, 0.9f, 0.8f, 0.12f * pulse, 0f, 16)
    shapeBatch.setAdditiveBlend(false)
  }

  private def drawChargingEffect(cx: Double, cy: Double, chargeLevel: Int): Unit = {
    if (chargeLevel <= 0) return
    val pct = chargeLevel / 100f
    beginShapes()
    shapeBatch.setAdditiveBlend(true)
    val ringR = 12f + pct * 8f
    val pulse = (0.8 + 0.2 * Math.sin(animationTick * 0.15)).toFloat
    shapeBatch.fillOvalSoft(cx.toFloat, cy.toFloat, ringR, ringR * 0.7f, 1f, 0.8f * (1 - pct * 0.5f), 0.2f, 0.1f * pct * pulse, 0f, 20)
    shapeBatch.setAdditiveBlend(false)
  }

  private def drawPhasedEffect(cx: Double, cy: Double): Unit = {
    beginShapes()
    shapeBatch.setAdditiveBlend(true)
    val shimmer = (0.5 + 0.5 * Math.sin(animationTick * 0.12)).toFloat
    shapeBatch.fillOvalSoft(cx.toFloat, cy.toFloat, 16f, 14f, 0.5f, 0.3f, 0.8f, 0.08f * shimmer, 0f, 16)
    shapeBatch.setAdditiveBlend(false)
  }

  private def drawFrozenEffect(cx: Double, cy: Double): Unit = {
    beginShapes()
    shapeBatch.fillOval(cx.toFloat, cy.toFloat, 14f, 12f, 0.6f, 0.85f, 1f, 0.2f, 16)
    for (i <- 0 until 6) {
      val angle = (i * Math.PI / 3 + animationTick * 0.01).toFloat
      val dist = 12f
      val sx = cx.toFloat + dist * Math.cos(angle).toFloat
      val sy = cy.toFloat + dist * Math.sin(angle).toFloat * 0.6f
      shapeBatch.fillRect(sx - 1, sy - 3, 2f, 6f, 0.7f, 0.9f, 1f, 0.4f)
    }
  }

  private def drawRootedEffect(cx: Double, cy: Double): Unit = {
    beginShapes()
    for (i <- 0 until 4) {
      val angle = (i * Math.PI / 2 + animationTick * 0.02).toFloat
      val dist = 10f
      val sx = cx.toFloat + dist * Math.cos(angle).toFloat
      val sy = cy.toFloat + 8 + dist * Math.sin(angle).toFloat * 0.3f
      shapeBatch.strokeLine(sx, sy, sx + Math.cos(angle + 0.5f).toFloat * 5, sy - 8, 2f, 0.3f, 0.6f, 0.15f, 0.5f)
    }
  }

  private def drawSlowedEffect(cx: Double, cy: Double): Unit = {
    beginShapes()
    shapeBatch.fillOval(cx.toFloat, cy.toFloat, 16f, 12f, 0.4f, 0.4f, 0.8f, 0.1f, 16)
  }

  private def drawBurnEffect(cx: Double, cy: Double): Unit = {
    beginShapes()
    shapeBatch.setAdditiveBlend(true)
    for (i <- 0 until 5) {
      val angle = (animationTick * 0.15 + i * 1.3).toFloat
      val dist = 8f + Math.sin(animationTick * 0.1 + i).toFloat * 4
      val fx = cx.toFloat + dist * Math.cos(angle).toFloat
      val fy = cy.toFloat + dist * Math.sin(angle).toFloat * 0.5f - (animationTick * 0.5f + i * 2) % 10
      shapeBatch.fillOval(fx, fy, 3f, 4f, 1f, 0.6f, 0.1f, 0.4f, 8)
    }
    shapeBatch.setAdditiveBlend(false)
  }

  private def drawSpeedBoostEffect(cx: Double, cy: Double): Unit = {
    beginShapes()
    for (i <- 0 until 3) {
      val offset = i * 5f
      shapeBatch.strokeLine(
        cx.toFloat - 8 - offset, cy.toFloat + 4,
        cx.toFloat - 14 - offset, cy.toFloat + 4,
        1.5f, 0.2f, 0.8f, 1f, 0.3f - i * 0.08f)
    }
  }

  private def drawHitEffect(cx: Double, cy: Double, hitTime: Long): Unit = {
    if (hitTime <= 0) return
    val elapsed = System.currentTimeMillis() - hitTime
    if (elapsed > HIT_ANIMATION_MS) return
    val progress = elapsed.toDouble / HIT_ANIMATION_MS
    val fadeOut = (1.0 - progress).toFloat
    val x = cx.toFloat
    val y = cy.toFloat
    beginShapes()
    shapeBatch.setAdditiveBlend(true)
    // Larger central glow
    shapeBatch.fillOval(x, y, 24f * fadeOut, 18f * fadeOut, 1f, 0.4f, 0.1f, 0.4f * fadeOut, 14)
    shapeBatch.fillOval(x, y, 14f * fadeOut, 10f * fadeOut, 1f, 0.9f, 0.7f, 0.3f * fadeOut, 10)
    // Radiating sparks
    val sparkDist = 8f + 20f * progress.toFloat
    val sparkSz = 3f * fadeOut
    var i = 0
    while (i < 6) {
      val angle = i * 1.047f + hitTime * 0.001f // 60 degree spacing + offset per hit
      val sx = x + math.cos(angle).toFloat * sparkDist
      val sy = y + math.sin(angle).toFloat * sparkDist * 0.6f
      shapeBatch.fillOval(sx, sy, sparkSz, sparkSz, 1f, 0.8f, 0.2f, 0.5f * fadeOut, 6)
      i += 1
    }
    shapeBatch.setAdditiveBlend(false)
  }

  private def drawCastFlash(cx: Double, cy: Double): Unit = {
    val castTime = client.getLastCastTime
    if (castTime <= 0) return
    val elapsed = System.currentTimeMillis() - castTime
    if (elapsed > 200) return
    val fadeOut = (1.0 - elapsed / 200.0).toFloat
    beginShapes()
    shapeBatch.setAdditiveBlend(true)
    shapeBatch.fillOvalSoft(cx.toFloat, cy.toFloat, 14f, 10f, 1f, 1f, 0.8f, 0.3f * fadeOut, 0f, 16)
    shapeBatch.setAdditiveBlend(false)
  }

  // ═══════════════════════════════════════════════════════════════════
  //  ITEM RENDERING (5e)
  // ═══════════════════════════════════════════════════════════════════

  private def drawSingleItem(item: Item): Unit = {
    val now = System.currentTimeMillis()
    val ix = item.getCellX; val iy = item.getCellY
    val centerX = worldToScreenX(ix, iy).toFloat
    val halfSize = Constants.ITEM_SIZE_PX / 2f

    // Track for pickup detection
    drawnItemIdsThisFrame.add(item.id)
    itemLastWorldPos.put(item.id, (ix, iy, item.colorRGB))

    // Spawn pop animation (bounce ease over 400ms)
    val spawnTime = itemSpawnTimes.getOrElseUpdate(item.id, now)
    val spawnElapsed = (now - spawnTime).toFloat
    val spawnScale = if (spawnElapsed < 400f) {
      val t = spawnElapsed / 400f
      if (t < 0.5f) 1f + 0.4f * Math.sin(t * Math.PI).toFloat
      else 1f + 0.15f * Math.sin(t * Math.PI).toFloat * (1f - t)
    } else 1f
    val hs = halfSize * spawnScale

    val bobPhase = animationTick * 0.06f + item.id * 1.7f
    val bobOffset = Math.sin(bobPhase).toFloat * 4f
    val centerY = worldToScreenY(ix, iy).toFloat + bobOffset

    // Rotation angle (slow spin)
    val rotAngle = animationTick * 0.02f + item.id * 0.5f

    val (ir, ig, ib) = intToRGB(item.colorRGB)
    val glowPulse = (0.6 + 0.4 * Math.sin(bobPhase * 1.3)).toFloat

    // Item emits colored light
    lightSystem.addLight(centerX, centerY, 60f, ir, ig, ib, 0.14f * glowPulse)

    beginShapes()
    // Ground glow (additive) — larger and more visible
    shapeBatch.setAdditiveBlend(true)
    shapeBatch.fillOvalSoft(centerX, centerY + hs * 0.3f, hs * 2f, hs * 0.6f, ir, ig, ib, 0.18f * glowPulse, 0f, 14)
    shapeBatch.fillOvalSoft(centerX, centerY, hs * 2.4f, hs * 1.8f, ir, ig, ib, 0.08f * glowPulse, 0f, 14)
    shapeBatch.setAdditiveBlend(false)

    // Main item shape with highlights and outline
    drawItemShape(item.itemType, centerX, centerY, hs, ir, ig, ib, rotAngle)

    // Sparkle particles (additive)
    shapeBatch.setAdditiveBlend(true)
    for (i <- 0 until 3) {
      val sparkAngle = bobPhase * 0.8f + i * (2 * Math.PI / 3).toFloat
      val sparkDist = hs * 1.1f
      val sx = centerX + sparkDist * Math.cos(sparkAngle).toFloat
      val sy = centerY + sparkDist * Math.sin(sparkAngle).toFloat * 0.6f
      val sparkAlpha = (0.3 + 0.4 * Math.sin(bobPhase * 2.5 + i * 2.1)).toFloat
      val sparkSize = (1.5 + Math.sin(bobPhase * 3.0 + i) * 0.7).toFloat
      shapeBatch.fillOval(sx, sy, sparkSize, sparkSize, 1f, 1f, 1f, clamp(sparkAlpha))
    }
    shapeBatch.setAdditiveBlend(false)
  }

  private def drawItemShape(itemType: ItemType, cx: Float, cy: Float, hs: Float, r: Float, g: Float, b: Float, rotation: Float = 0f): Unit = {
    val cos = Math.cos(rotation).toFloat
    val sin = Math.sin(rotation).toFloat
    // Highlight and shadow colors for 3D depth
    val hr = clamp(r * 0.4f + 0.6f); val hg = clamp(g * 0.4f + 0.6f); val hb = clamp(b * 0.4f + 0.6f)
    val dr = r * 0.6f; val dg = g * 0.6f; val db = b * 0.6f

    itemType match {
      case ItemType.Heart =>
        val rr = hs * 0.55f
        // Main shape
        shapeBatch.fillOval(cx - hs * 0.5f, cy - hs * 0.45f, rr, rr, r, g, b, 1f, 12)
        shapeBatch.fillOval(cx + hs * 0.5f, cy - hs * 0.45f, rr, rr, r, g, b, 1f, 12)
        shapeBatch.fillPolygon(
          Array(cx - hs * 1.05f, cx, cx + hs * 1.05f),
          Array(cy - hs * 0.1f, cy + hs, cy - hs * 0.1f), r, g, b, 1f)
        // Inner highlight (upper-left lobe)
        shapeBatch.fillOval(cx - hs * 0.55f, cy - hs * 0.55f, rr * 0.45f, rr * 0.4f, hr, hg, hb, 0.4f, 8)
        // Outline
        shapeBatch.strokeOval(cx - hs * 0.5f, cy - hs * 0.45f, rr, rr, 1f, dr, dg, db, 0.6f, 12)
        shapeBatch.strokeOval(cx + hs * 0.5f, cy - hs * 0.45f, rr, rr, 1f, dr, dg, db, 0.6f, 12)

      case ItemType.Star =>
        val outerR = hs; val innerR = hs * 0.4f
        for (i <- 0 until 10) {
          val angle = Math.PI / 2 + i * Math.PI / 5 + rotation
          val rad = if (i % 2 == 0) outerR else innerR
          _starXs(i) = cx + (rad * Math.cos(angle)).toFloat
          _starYs(i) = cy - (rad * Math.sin(angle)).toFloat
        }
        shapeBatch.fillPolygon(_starXs, _starYs, 10, r, g, b, 1f)
        // Inner highlight (smaller, brighter, offset up-left)
        for (i <- 0 until 10) {
          val angle = Math.PI / 2 + i * Math.PI / 5 + rotation
          val rad = if (i % 2 == 0) outerR * 0.55f else innerR * 0.55f
          _hlXs(i) = cx - 1f + (rad * Math.cos(angle)).toFloat
          _hlYs(i) = cy - 1f - (rad * Math.sin(angle)).toFloat
        }
        shapeBatch.fillPolygon(_hlXs, _hlYs, 10, hr, hg, hb, 0.35f)
        // Outline
        shapeBatch.strokePolygon(_starXs, _starYs, 10, 1f, dr, dg, db, 0.7f)

      case ItemType.Gem =>
        // Apply rotation to gem vertices
        val gxs = Array(
          cx + 0f * cos - (-hs) * sin, cx + hs * cos - 0f * sin,
          cx + 0f * cos - hs * sin, cx + (-hs) * cos - 0f * sin)
        val gys = Array(
          cy + 0f * sin + (-hs) * cos, cy + hs * sin + 0f * cos,
          cy + 0f * sin + hs * cos, cy + (-hs) * sin + 0f * cos)
        shapeBatch.fillPolygon(gxs, gys, r, g, b, 1f)
        // Inner highlight (upper half diamond)
        val hhs = hs * 0.5f
        shapeBatch.fillPolygon(
          Array(gxs(0), cx + hhs * cos, cx, cx - hhs * cos),
          Array(gys(0), cy + hhs * sin, cy, cy - hhs * sin), hr, hg, hb, 0.3f)
        // Outline
        shapeBatch.strokePolygon(gxs, gys, 1f, dr, dg, db, 0.7f)

      case ItemType.Shield =>
        val sxs = Array(cx - hs * 0.85f, cx - hs * 0.5f, cx + hs * 0.5f, cx + hs * 0.85f, cx + hs * 0.7f, cx, cx - hs * 0.7f)
        val sys = Array(cy - hs * 0.6f, cy - hs, cy - hs, cy - hs * 0.6f, cy + hs * 0.4f, cy + hs, cy + hs * 0.4f)
        shapeBatch.fillPolygon(sxs, sys, r, g, b, 1f)
        // Inner highlight (upper portion)
        shapeBatch.fillPolygon(
          Array(cx - hs * 0.4f, cx - hs * 0.25f, cx + hs * 0.25f, cx + hs * 0.4f),
          Array(cy - hs * 0.5f, cy - hs * 0.8f, cy - hs * 0.8f, cy - hs * 0.5f), hr, hg, hb, 0.3f)
        // Outline
        shapeBatch.strokePolygon(sxs, sys, 1f, dr, dg, db, 0.7f)

      case ItemType.Fence =>
        val barW = hs * 0.35f
        // Main bars
        shapeBatch.fillRect(cx - hs, cy - hs, barW, hs * 2, r, g, b, 1f)
        shapeBatch.fillRect(cx - barW / 2, cy - hs, barW, hs * 2, r, g, b, 1f)
        shapeBatch.fillRect(cx + hs - barW, cy - hs, barW, hs * 2, r, g, b, 1f)
        // Inner highlight (left edge of each bar)
        val hlW = barW * 0.35f
        shapeBatch.fillRect(cx - hs, cy - hs, hlW, hs * 2, hr, hg, hb, 0.3f)
        shapeBatch.fillRect(cx - barW / 2, cy - hs, hlW, hs * 2, hr, hg, hb, 0.3f)
        shapeBatch.fillRect(cx + hs - barW, cy - hs, hlW, hs * 2, hr, hg, hb, 0.3f)
        // Outline
        shapeBatch.strokeRect(cx - hs, cy - hs, barW, hs * 2, 1f, dr, dg, db, 0.7f)
        shapeBatch.strokeRect(cx - barW / 2, cy - hs, barW, hs * 2, 1f, dr, dg, db, 0.7f)
        shapeBatch.strokeRect(cx + hs - barW, cy - hs, barW, hs * 2, 1f, dr, dg, db, 0.7f)

      case _ =>
        val dxs = Array(cx, cx + hs, cx, cx - hs)
        val dys = Array(cy - hs, cy, cy + hs, cy)
        shapeBatch.fillPolygon(dxs, dys, r, g, b, 1f)
        shapeBatch.strokePolygon(dxs, dys, 1f, dr, dg, db, 0.7f)
    }
  }

  // ═══════════════════════════════════════════════════════════════════
  //  PROJECTILE RENDERING (5d)
  // ═══════════════════════════════════════════════════════════════════

  private def drawSingleProjectile(proj: Projectile): Unit = {
    val px = proj.getX.toDouble
    val py = proj.getY.toDouble
    val sx = worldToScreenX(px, py).toFloat
    val sy = worldToScreenY(px, py).toFloat

    // Projectile emits colored light
    val (plr, plg, plb) = intToRGB(proj.colorRGB)
    lightSystem.addLight(sx, sy, 55f, plr, plg, plb, 0.15f)

    beginShapes()
    GLProjectileRenderers.get(proj.projectileType) match {
      case Some(renderer) => renderer(proj, sx, sy, shapeBatch, animationTick)
      case None =>
        val (r, g, b) = intToRGB(proj.colorRGB)
        GLProjectileRenderers.drawGeneric(proj, sx, sy, shapeBatch, animationTick, r, g, b)
    }
  }

  // ═══════════════════════════════════════════════════════════════════
  //  AIM ARROW (5g)
  // ═══════════════════════════════════════════════════════════════════

  private def drawAimArrow(): Unit = {
    if (!client.isCharging || client.getIsDead) return
    val pt = client.getSelectedCharacterDef.primaryProjectileType
    if (!ProjectileDef.get(pt).chargeSpeedScaling.isDefined) return

    val pos = client.getLocalPosition
    val gridX = pos.getX.toDouble; val gridY = pos.getY.toDouble
    val drawX = if (camera.visualX.isNaN) gridX else camera.visualX
    val drawY = if (camera.visualY.isNaN) gridY else camera.visualY

    val mouseWX = client.getMouseWorldX; val mouseWY = client.getMouseWorldY
    val adx = mouseWX - gridX; val ady = mouseWY - gridY
    val dist = Math.sqrt(adx * adx + ady * ady)
    if (dist < 0.01) return
    val ndx = adx / dist; val ndy = ady / dist

    val chargeLevel = client.getChargeLevel
    val chargePct = chargeLevel / 100f
    val pDef = ProjectileDef.get(pt)
    val (minRange, maxRange) = pDef.chargeRangeScaling match {
      case Some(s) => (s.min.toInt, s.max.toInt)
      case None => (pDef.maxRange, pDef.maxRange)
    }
    val cylLength = 1.0 + minRange + chargePct * (maxRange - minRange)
    val (cr, cg, cb) = intToRGB(client.getLocalColorRGB)
    val phase = animationTick * 0.12
    val pulse = (0.85 + 0.15 * Math.sin(phase * 2.0)).toFloat

    // Heat color blend
    val heatR = Math.min(1f, chargePct * 2f)
    val heatG = Math.min(1f, Math.max(0f, 1f - (chargePct - 0.5f) * 2f))
    val blendR = Math.min(1f, cr * 0.4f + heatR * 0.6f)
    val blendG = Math.min(1f, cg * 0.4f + heatG * 0.6f)
    val blendB = Math.min(1f, cb * 0.3f)
    val brightR = Math.min(1f, blendR * 0.3f + 0.7f)
    val brightG = Math.min(1f, blendG * 0.3f + 0.7f)
    val brightB = Math.min(1f, blendB * 0.3f + 0.7f)

    val perpX = -ndy; val perpY = ndx; val cylRadius = 0.6

    def cylSX(t: Double, w: Double): Float =
      worldToScreenX(drawX + ndx * cylLength * t + perpX * cylRadius * w,
                     drawY + ndy * cylLength * t + perpY * cylRadius * w).toFloat
    def cylSY(t: Double, w: Double): Float =
      worldToScreenY(drawX + ndx * cylLength * t + perpX * cylRadius * w,
                     drawY + ndy * cylLength * t + perpY * cylRadius * w).toFloat

    beginShapes()

    // Ground glow
    val glowN = 10
    for (i <- 0 to glowN) {
      val t = i.toDouble / glowN
      val gw = 1.8 * pulse
      val cwx = drawX + ndx * cylLength * t
      val cwy = drawY + ndy * cylLength * t
      _aimGlowXs(i) = worldToScreenX(cwx + perpX * gw, cwy + perpY * gw).toFloat
      _aimGlowYs(i) = worldToScreenY(cwx + perpX * gw, cwy + perpY * gw).toFloat
      _aimGlowXs(glowN * 2 + 1 - i) = worldToScreenX(cwx - perpX * gw, cwy - perpY * gw).toFloat
      _aimGlowYs(glowN * 2 + 1 - i) = worldToScreenY(cwx - perpX * gw, cwy - perpY * gw).toFloat
    }
    shapeBatch.fillPolygon(_aimGlowXs, _aimGlowYs, glowN * 2 + 2, blendR, blendG, blendB, 0.05f * pulse)

    // Cylinder body
    val bodyN = 20
    for (i <- 0 to bodyN) {
      val t = i.toDouble / bodyN
      _aimBodyXs(i) = cylSX(t, 1.0)
      _aimBodyYs(i) = cylSY(t, 1.0)
      _aimBodyXs(bodyN * 2 + 1 - i) = cylSX(t, -1.0)
      _aimBodyYs(bodyN * 2 + 1 - i) = cylSY(t, -1.0)
    }
    shapeBatch.fillPolygon(_aimBodyXs, _aimBodyYs, bodyN * 2 + 2, blendR * 0.5f, blendG * 0.5f, blendB * 0.5f, 0.10f + chargePct * 0.06f)

    // Energy spine
    shapeBatch.setAdditiveBlend(true)
    val spineSegs = 14
    for (i <- 0 until spineSegs) {
      val t0 = i.toDouble / spineSegs; val t1 = (i + 1).toDouble / spineSegs
      val fadeAlpha = ((1.0 - t0 * 0.7) * (0.08 + chargePct * 0.12) * pulse).toFloat
      val lw = ((2.0 + chargePct * 1.5) * (1.0 - t0 * 0.5)).toFloat
      shapeBatch.strokeLine(cylSX(t0, 0), cylSY(t0, 0), cylSX(t1, 0), cylSY(t1, 0), lw, brightR, brightG, brightB, clamp(fadeAlpha))
    }

    // Energy rings
    val ringCount = 4 + (chargePct * 4).toInt
    for (i <- 0 until ringCount) {
      val t = ((animationTick * 0.035 + i.toDouble / ringCount) % 1.0)
      val ringAlpha = ((1.0 - Math.abs(t - 0.5) * 2.0) * (0.15 + chargePct * 0.2) * pulse).toFloat
      val ringSegs = 8
      for (j <- 0 until ringSegs) {
        val a1 = Math.PI * j.toDouble / ringSegs - Math.PI * 0.5
        val a2 = Math.PI * (j + 1).toDouble / ringSegs - Math.PI * 0.5
        val w1 = Math.cos(a1); val w2 = Math.cos(a2)
        val f1 = Math.sin(a1) * (cylRadius / cylLength) * 0.3
        val f2 = Math.sin(a2) * (cylRadius / cylLength) * 0.3
        shapeBatch.strokeLine(cylSX(t + f1, w1), cylSY(t + f1, w1),
          cylSX(t + f2, w2), cylSY(t + f2, w2), 1.5f + chargePct, brightR, brightG, brightB, clamp(ringAlpha))
      }
    }

    // Tip glow
    val tipSX = cylSX(1.0, 0); val tipSY = cylSY(1.0, 0)
    val orbR = (4f + chargePct * 5f) * pulse
    shapeBatch.fillOvalSoft(tipSX, tipSY, orbR * 2, orbR, brightR, brightG, brightB, 0.25f * pulse, 0f, 12)

    shapeBatch.setAdditiveBlend(false)
  }

  // ═══════════════════════════════════════════════════════════════════
  //  ANIMATIONS (5h)
  // ═══════════════════════════════════════════════════════════════════

  private def drawDeathAnimations(): Unit = {
    val now = System.currentTimeMillis()
    val iter = client.getDeathAnimations.entrySet().iterator()
    // Clean up old death burst tracking
    deathBurstSpawned.filterInPlace(k => client.getDeathAnimations.containsKey(k))
    while (iter.hasNext) {
      val entry = iter.next()
      val key = entry.getKey
      val data = entry.getValue
      val deathTime = data(0)
      if (now - deathTime > DEATH_ANIMATION_MS) {
        iter.remove()
        deathBurstSpawned.remove(key)
      } else {
        val wx = data(1).toDouble; val wy = data(2).toDouble
        val colorRGB = data(3).toInt
        val charId = if (data.length > 4) data(4).toByte else 0.toByte
        // Spawn death burst particles on first frame
        if (!deathBurstSpawned.contains(key)) {
          deathBurstSpawned.add(key)
          spawnDeathBurst(wx.toFloat, wy.toFloat, colorRGB)
        }
        drawDeathEffect(worldToScreenX(wx, wy), worldToScreenY(wx, wy), colorRGB, deathTime, charId)
      }
    }
  }

  private def drawDeathEffect(screenX: Double, screenY: Double, colorRGB: Int, deathTime: Long, characterId: Byte = 0): Unit = {
    if (deathTime <= 0) return
    val elapsed = System.currentTimeMillis() - deathTime
    if (elapsed < 0 || elapsed > DEATH_ANIMATION_MS) return

    val progress = elapsed.toDouble / DEATH_ANIMATION_MS
    val fadeOut = (1.0 - progress).toFloat
    val (cr, cg, cb) = intToRGB(colorRGB)
    val displaySz = Constants.PLAYER_DISPLAY_SIZE_PX
    val cx = screenX.toFloat; val cy = (screenY - displaySz / 2.0).toFloat

    beginShapes()
    shapeBatch.setAdditiveBlend(true)

    // Flash
    if (progress < 0.2) {
      val flashAlpha = (0.7 * (1.0 - progress / 0.2)).toFloat
      val flashR = (15 + progress / 0.2 * 25).toFloat
      shapeBatch.fillOvalSoft(cx, cy, flashR, flashR * 0.5f, 1f, 1f, 0.95f, flashAlpha, 0f, 16)
    }

    // Shockwave rings
    val ring1R = (progress * 55).toFloat
    val segs = 24
    val step = (2 * Math.PI / segs).toFloat
    var angle = 0f
    for (_ <- 0 until segs) {
      val x1 = cx + ring1R * Math.cos(angle).toFloat
      val y1 = cy + ring1R * 0.5f * Math.sin(angle).toFloat
      val x2 = cx + ring1R * Math.cos(angle + step).toFloat
      val y2 = cy + ring1R * 0.5f * Math.sin(angle + step).toFloat
      shapeBatch.strokeLine(x1, y1, x2, y2, 3f * fadeOut, cr, cg, cb, 0.55f * fadeOut)
      angle += step
    }

    // Particles
    for (i <- 0 until 14) {
      val pAngle = i * (2 * Math.PI / 14) + i * 0.4
      val speed = 0.7 + (i % 4) * 0.15
      val dist = (progress * (25 + (i % 4) * 12) * speed).toFloat
      val rise = (progress * progress * 25 * (0.8 + (i % 3) * 0.15)).toFloat
      val px = cx + dist * Math.cos(pAngle).toFloat
      val py = cy + dist * Math.sin(pAngle).toFloat * 0.5f - rise
      val pSize = ((2.5 + (i % 3) * 1.5) * fadeOut).toFloat
      val (pr, pg, pb) = if (i % 3 == 0) (1f, 1f, 0.9f)
        else if (i % 3 == 1) (clamp(cr * 0.4f + 0.6f), clamp(cg * 0.4f + 0.6f), clamp(cb * 0.4f + 0.6f))
        else (cr, cg, cb)
      val pAlpha = (if (i % 3 == 0) 0.7 else if (i % 3 == 1) 0.65 else 0.55).toFloat * fadeOut
      shapeBatch.fillOval(px, py, pSize, pSize, pr, pg, pb, pAlpha, 8)
    }

    shapeBatch.setAdditiveBlend(false)

    // Ghost sprite rising
    val ghostAlpha = Math.max(0f, fadeOut * 0.55f)
    val ghostRise = (progress * 35).toFloat

    val region = GLSpriteGenerator.getSpriteRegion(Direction.Down, 0, characterId)
    if (region != null) {
      beginSprites()
      spriteBatch.draw(region,
        (screenX - displaySz / 2.0).toFloat, (screenY - displaySz - ghostRise).toFloat,
        displaySz.toFloat, displaySz.toFloat, 1f, 1f, 1f, ghostAlpha)
    }
  }

  private def drawTeleportAnimations(): Unit = {
    val now = System.currentTimeMillis()
    val iter = client.getTeleportAnimations.entrySet().iterator()
    while (iter.hasNext) {
      val entry = iter.next()
      val data = entry.getValue
      val timestamp = data(0)
      if (now - timestamp > TELEPORT_ANIMATION_MS) {
        iter.remove()
      } else {
        val oldX = data(1).toDouble; val oldY = data(2).toDouble
        val newX = data(3).toDouble; val newY = data(4).toDouble
        val colorRGB = data(5).toInt
        drawTeleportDeparture(worldToScreenX(oldX, oldY).toFloat, worldToScreenY(oldX, oldY).toFloat, timestamp)
        drawTeleportArrival(worldToScreenX(newX, newY).toFloat, worldToScreenY(newX, newY).toFloat, timestamp)
      }
    }
  }

  private def drawTeleportDeparture(sx: Float, sy: Float, startTime: Long): Unit = {
    val elapsed = System.currentTimeMillis() - startTime
    if (elapsed > TELEPORT_ANIMATION_MS) return
    val progress = elapsed.toDouble / TELEPORT_ANIMATION_MS
    val fadeOut = Math.max(0, 1.0 - progress * 1.5).toFloat

    beginShapes()
    shapeBatch.setAdditiveBlend(true)
    for (i <- 0 until 8) {
      val angle = (i * (2 * Math.PI / 8) + progress * 2).toFloat
      val dist = (25 * (1 - progress)).toFloat
      val px = sx + dist * Math.cos(angle).toFloat
      val py = sy + dist * Math.sin(angle).toFloat * 0.5f
      shapeBatch.fillOval(px, py, 3f * fadeOut, 3f * fadeOut, 1f, 0.92f, 0.3f, 0.7f * fadeOut, 8)
    }
    if (progress < 0.2) {
      val flashAlpha = (0.5 * (1 - progress / 0.2)).toFloat
      val flashR = (15 * (1 - progress / 0.2)).toFloat
      shapeBatch.fillOvalSoft(sx, sy, flashR, flashR * 0.5f, 1f, 1f, 0.8f, flashAlpha, 0f, 12)
    }
    shapeBatch.setAdditiveBlend(false)
  }

  private def drawTeleportArrival(sx: Float, sy: Float, startTime: Long): Unit = {
    val elapsed = System.currentTimeMillis() - startTime
    if (elapsed < 200 || elapsed > TELEPORT_ANIMATION_MS) return
    val progress = (elapsed - 200).toDouble / (TELEPORT_ANIMATION_MS - 200)
    val fadeOut = (1.0 - progress).toFloat

    beginShapes()
    shapeBatch.setAdditiveBlend(true)
    if (progress < 0.3) {
      val flashAlpha = (0.6 * (1 - progress / 0.3)).toFloat
      val flashR = (20 * (1 - progress / 0.3)).toFloat
      shapeBatch.fillOvalSoft(sx, sy, flashR, flashR * 0.5f, 1f, 1f, 0.8f, flashAlpha, 0f, 12)
    }
    for (i <- 0 until 8) {
      val angle = (i * (2 * Math.PI / 8) - progress * 2).toFloat
      val dist = (progress * 30).toFloat
      val px = sx + dist * Math.cos(angle).toFloat
      val py = sy + dist * Math.sin(angle).toFloat * 0.5f
      shapeBatch.fillOval(px, py, 3f * fadeOut, 3f * fadeOut, 1f, 0.92f, 0.3f, 0.5f * fadeOut, 8)
    }
    shapeBatch.setAdditiveBlend(false)
  }

  private def drawExplosionAnimations(): Unit = {
    val now = System.currentTimeMillis()
    val EXPLOSION_DURATION = 1400L
    val iter = client.getExplosionAnimations.entrySet().iterator()
    while (iter.hasNext) {
      val entry = iter.next()
      val data = entry.getValue
      val timestamp = data(0)
      if (now - timestamp > EXPLOSION_DURATION) {
        iter.remove()
      } else {
        val wx = data(1).toDouble / 1000.0; val wy = data(2).toDouble / 1000.0
        val colorRGB = data(3).toInt
        val blastRadius = if (data.length > 4) data(4).toFloat / 1000f else 3f
        val sx = worldToScreenX(wx, wy).toFloat
        val sy = worldToScreenY(wx, wy).toFloat
        val elapsed = (now - timestamp).toFloat
        val progress = elapsed / EXPLOSION_DURATION
        val (er, eg, eb) = intToRGB(colorRGB)

        // Screen-space blast radius (isometric: wider horizontally)
        val blastW = blastRadius * HW
        val blastH = blastRadius * HH

        beginShapes()

        // Phase 1: Bright flash (0-15%)
        if (progress < 0.15f) {
          val flashP = progress / 0.15f
          val flashScale = 0.3f + flashP * 0.7f
          val flashAlpha = 0.9f * (1f - flashP)
          // Bright white-hot center
          shapeBatch.fillOval(sx, sy, blastW * flashScale * 0.6f, blastH * flashScale * 0.6f,
            1f, 1f, 0.95f, flashAlpha, 16)
          // Colored glow around center
          shapeBatch.fillOvalSoft(sx, sy, blastW * flashScale, blastH * flashScale,
            bright(er), bright(eg), bright(eb), flashAlpha * 0.7f, 0f, 20)
        }

        // Phase 2: Expanding fireball (0-50%)
        if (progress < 0.5f) {
          val fireP = progress / 0.5f
          val fireAlpha = 0.85f * (1f - fireP * 0.7f)
          val fireScale = 0.2f + fireP * 0.8f
          // Outer fire glow
          shapeBatch.fillOvalSoft(sx, sy, blastW * fireScale * 1.3f, blastH * fireScale * 1.3f,
            er, eg * 0.5f, eb * 0.2f, fireAlpha * 0.4f, 0f, 18)
          // Core fireball
          shapeBatch.fillOval(sx, sy, blastW * fireScale * 0.7f, blastH * fireScale * 0.7f,
            er, eg, eb, fireAlpha, 16)
          // Hot center
          shapeBatch.fillOval(sx, sy, blastW * fireScale * 0.35f, blastH * fireScale * 0.35f,
            bright(er), bright(eg), bright(eb), fireAlpha * 0.9f, 12)
        }

        // Phase 3: Shockwave ring (10-80%)
        if (progress > 0.1f && progress < 0.8f) {
          val ringP = (progress - 0.1f) / 0.7f
          val ringScale = 0.3f + ringP * 0.7f
          val ringAlpha = 0.8f * (1f - ringP)
          val ringW = blastW * ringScale * 1.1f
          val ringH = blastH * ringScale * 1.1f
          val thickness = 3f * (1f - ringP * 0.5f)
          shapeBatch.strokeOval(sx, sy, ringW, ringH, thickness, er, eg, eb, ringAlpha, 28)
          // Second thinner outer ring
          if (ringP < 0.6f) {
            shapeBatch.strokeOval(sx, sy, ringW * 1.15f, ringH * 1.15f, 1.5f,
              bright(er), bright(eg), bright(eb), ringAlpha * 0.4f, 28)
          }
        }

        // Phase 4: Debris particles (15-100%)
        if (progress > 0.15f) {
          val debrisP = (progress - 0.15f) / 0.85f
          val numDebris = 12
          for (i <- 0 until numDebris) {
            val angle = i.toDouble * Math.PI * 2 / numDebris + entry.getKey * 0.7
            val speed = 0.6f + (((i * 7 + entry.getKey * 3) % 10) / 10f) * 0.6f
            val dist = debrisP * speed
            val dx = sx + (Math.cos(angle) * dist * blastW * 1.2f).toFloat
            val dy = sy + (Math.sin(angle) * dist * blastH * 1.2f).toFloat - debrisP * debrisP * 15f
            val debrisAlpha = 0.7f * (1f - debrisP)
            val sz = 3f * (1f - debrisP * 0.6f)
            if (debrisAlpha > 0.05f) {
              shapeBatch.fillOval(dx, dy, sz, sz * 0.7f, er, eg * 0.6f, eb * 0.3f, debrisAlpha, 6)
            }
          }
        }

        // Phase 5: Smoke (30-100%)
        if (progress > 0.3f) {
          val smokeP = (progress - 0.3f) / 0.7f
          val smokeAlpha = 0.25f * (1f - smokeP)
          val smokeScale = 0.5f + smokeP * 0.5f
          shapeBatch.fillOvalSoft(sx, sy - smokeP * 12, blastW * smokeScale * 0.8f, blastH * smokeScale * 0.6f,
            0.25f, 0.22f, 0.2f, smokeAlpha, 0f, 14)
        }

        // (camera shake removed)
      }
    }
  }

  private def drawAoeSplashAnimations(): Unit = {
    val now = System.currentTimeMillis()
    val AOE_DURATION = 900L
    val iter = client.getAoeSplashAnimations.entrySet().iterator()
    while (iter.hasNext) {
      val entry = iter.next()
      val data = entry.getValue
      val timestamp = data(0)
      if (now - timestamp > AOE_DURATION) {
        iter.remove()
      } else {
        val wx = data(1).toDouble / 1000.0; val wy = data(2).toDouble / 1000.0
        val colorRGB = data(3).toInt
        val aoeRadius = if (data.length > 4) data(4).toFloat / 1000f else 3f
        val sx = worldToScreenX(wx, wy).toFloat
        val sy = worldToScreenY(wx, wy).toFloat
        val elapsed = (now - timestamp).toFloat
        val progress = elapsed / AOE_DURATION
        val (ar, ag, ab) = intToRGB(colorRGB)

        val aoeW = aoeRadius * HW
        val aoeH = aoeRadius * HH

        beginShapes()

        // Ground impact flash (0-20%)
        if (progress < 0.2f) {
          val flashP = progress / 0.2f
          val flashAlpha = 0.6f * (1f - flashP)
          shapeBatch.fillOval(sx, sy, aoeW * flashP * 0.5f, aoeH * flashP * 0.5f,
            bright(ar), bright(ag), bright(ab), flashAlpha, 16)
        }

        // Expanding ring wave (multiple rings with stagger)
        for (ring <- 0 until 3) {
          val ringDelay = ring * 0.12f
          val ringP = Math.max(0f, (progress - ringDelay) / (1f - ringDelay))
          if (ringP > 0f && ringP < 1f) {
            val ringScale = 0.1f + ringP * 0.9f
            val ringAlpha = 0.7f * (1f - ringP) / (ring + 1)
            val rw = aoeW * ringScale
            val rh = aoeH * ringScale
            val thickness = (2.5f - ring * 0.5f) * (1f - ringP * 0.4f)
            shapeBatch.strokeOval(sx, sy, rw, rh, thickness, ar, ag, ab, ringAlpha, 24)
          }
        }

        // Ground scorch mark (fades slowly)
        if (progress > 0.15f) {
          val scorchP = (progress - 0.15f) / 0.85f
          val scorchAlpha = 0.3f * (1f - scorchP)
          shapeBatch.fillOval(sx, sy, aoeW * 0.6f, aoeH * 0.6f,
            ar * 0.3f, ag * 0.3f, ab * 0.3f, scorchAlpha, 14)
        }

        // Sparkle particles around perimeter
        if (progress > 0.05f && progress < 0.7f) {
          val sparkP = (progress - 0.05f) / 0.65f
          val numSparks = 8
          for (i <- 0 until numSparks) {
            val angle = i.toDouble * Math.PI * 2 / numSparks + entry.getKey * 1.3
            val dist = 0.5f + sparkP * 0.5f
            val px = sx + (Math.cos(angle) * aoeW * dist).toFloat
            val py = sy + (Math.sin(angle) * aoeH * dist).toFloat
            val sparkAlpha = 0.6f * (1f - sparkP)
            shapeBatch.fillOval(px, py, 2.5f, 2f, bright(ar), bright(ag), bright(ab), sparkAlpha, 6)
          }
        }
      }
    }
  }

  @inline private def bright(c: Float): Float = Math.min(1f, c * 0.4f + 0.6f)

  // ═══════════════════════════════════════════════════════════════════
  //  HEALTH BAR + NAME (5c continued)
  // ═══════════════════════════════════════════════════════════════════

  private def drawHealthBar(screenCenterX: Double, spriteTopY: Double, health: Int, maxHealth: Int, teamId: Byte, playerId: UUID): Unit = {
    val barW = Constants.HEALTH_BAR_WIDTH_PX.toFloat
    val barH = Constants.HEALTH_BAR_HEIGHT_PX.toFloat
    val barX = (screenCenterX - barW / 2).toFloat
    val barY = (spriteTopY - Constants.HEALTH_BAR_OFFSET_Y - barH).toFloat

    beginShapes()
    // Dark background
    shapeBatch.fillRect(barX, barY, barW, barH, 0.15f, 0.05f, 0.05f, 0.85f)

    val pct = health.toFloat / maxHealth
    val (fr, fg, fb) = teamId match {
      case 1 => (0.29f, 0.51f, 1f)
      case 2 => (0.91f, 0.25f, 0.34f)
      case 3 => (0.2f, 0.8f, 0.2f)
      case 4 => (0.95f, 0.77f, 0.06f)
      case _ => (0.2f, 0.8f, 0.2f)
    }

    // Smooth drain bar (white ghost segment that shrinks behind the real bar)
    val smoothPct = smoothHealthMap.getOrElseUpdate(playerId, pct)
    val newSmooth = if (smoothPct > pct) Math.max(pct, smoothPct - 0.8f * (1f / 60f))
                    else pct // snap to actual on heal
    smoothHealthMap.put(playerId, newSmooth)
    if (newSmooth > pct) {
      shapeBatch.fillRect(barX + barW * pct, barY, barW * (newSmooth - pct), barH, 1f, 1f, 1f, 0.45f)
    }

    // Health bar with inner gradient (lighter top, darker bottom)
    val fillW = barW * pct
    if (fillW > 0) {
      shapeBatch.fillRect(barX, barY + barH / 2, fillW, barH / 2, fr * 0.75f, fg * 0.75f, fb * 0.75f, 1f)
      shapeBatch.fillRect(barX, barY, fillW, barH / 2, fr, fg, fb, 1f)
      // Bright highlight line at very top
      shapeBatch.fillRect(barX, barY, fillW, 1f, clamp(fr + 0.2f), clamp(fg + 0.2f), clamp(fb + 0.2f), 0.5f)
    }

    // Low health glow pulse (<25%)
    if (pct < 0.25f && pct > 0f) {
      val pulse = (0.5f + 0.5f * Math.sin(animationTick * 0.15f)).toFloat
      shapeBatch.setAdditiveBlend(true)
      shapeBatch.fillRect(barX, barY - 1, fillW, barH + 2, 1f, 0.2f, 0.1f, 0.15f * pulse)
      shapeBatch.setAdditiveBlend(false)
    }

    shapeBatch.strokeRect(barX, barY, barW, barH, 1f, 0f, 0f, 0f, 1f)
    if (teamId != 0) {
      val indY = barY + barH + 2
      val indS = 4f
      shapeBatch.fillPolygon(
        Array(screenCenterX.toFloat, screenCenterX.toFloat + indS, screenCenterX.toFloat, screenCenterX.toFloat - indS),
        Array(indY, indY + indS, indY + indS * 2, indY + indS), fr, fg, fb, 1f)
    }
  }

  private def drawCharacterName(name: String, screenCenterX: Double, spriteTopY: Double): Unit = {
    val barH = Constants.HEALTH_BAR_HEIGHT_PX
    val nameY = (spriteTopY - Constants.HEALTH_BAR_OFFSET_Y - barH - fontSmall.charHeight - 2).toFloat
    val textW = fontSmall.measureWidth(name)
    val textX = (screenCenterX - textW / 2).toFloat
    beginSprites()
    fontSmall.drawTextOutlined(spriteBatch, name, textX, nameY)
  }

  // ═══════════════════════════════════════════════════════════════════
  //  HUD RENDERING (5i)
  // ═══════════════════════════════════════════════════════════════════

  private def renderHUD(screenW: Int, screenH: Int): Unit = {
    val world = client.getWorld
    val localPos = client.getLocalPosition
    val playerCount = client.getPlayers.size()

    // Top-left status panel with semi-transparent background
    beginShapes()
    shapeBatch.fillRect(4f, 4f, 200f, 96f, 0.04f, 0.04f, 0.08f, 0.6f)
    shapeBatch.strokeRect(4f, 4f, 200f, 96f, 1f, 0.3f, 0.3f, 0.4f, 0.4f)
    // Accent line at top
    shapeBatch.fillRect(4f, 4f, 200f, 2f, 0.4f, 0.7f, 1f, 0.5f)

    beginSprites()
    fontSmall.drawTextOutlined(spriteBatch, s"${world.name}", 12, 12)
    fontSmall.drawTextOutlined(spriteBatch, s"Pos: (${localPos.getX}, ${localPos.getY})", 12, 30)
    fontSmall.drawTextOutlined(spriteBatch, s"Players: ${playerCount + 1}", 12, 48)
    fontSmall.drawTextOutlined(spriteBatch, s"Items: ${client.getInventoryCount}", 12, 66)

    val hasShield = client.hasShield
    val hasGem = client.hasGemBoost
    if (hasShield || hasGem) {
      val effectStr = if (hasShield && hasGem) "Shield FastShot"
        else if (hasShield) "Shield"
        else "FastShot"
      fontSmall.drawTextOutlined(spriteBatch, effectStr, 12, 84, 0.6f, 0.9f, 1f, 0.9f)
    }

    renderInventory(screenW, screenH)
    renderAbilityHUD(screenW, screenH)
    renderChargeBar(screenW, screenH)
    renderLobbyHUD(screenW, screenH)
  }

  private def renderInventory(screenW: Int, screenH: Int): Unit = {
    val slotSize = 44f; val slotGap = 8f; val numSlots = 5
    val totalW = numSlots * slotSize + (numSlots - 1) * slotGap
    val startX = (screenW - totalW) / 2f
    val startY = screenH - slotSize - 14f

    // Draw all slot backgrounds + icons (shapes)
    beginShapes()
    for (i <- inventorySlotTypes.indices) {
      val (itemType, _) = inventorySlotTypes(i)
      val count = client.getItemCount(itemType.id)
      val slotX = startX + i * (slotSize + slotGap)
      val (tr, tg, tb) = intToRGB(itemType.colorRGB)

      if (count > 0) {
        shapeBatch.fillRect(slotX - 2, startY - 2, slotSize + 4, slotSize + 4, tr * 0.3f, tg * 0.3f, tb * 0.3f, 0.5f)
        shapeBatch.fillRect(slotX, startY, slotSize, slotSize, 0.06f, 0.06f, 0.1f, 0.85f)
        shapeBatch.strokeRect(slotX, startY, slotSize, slotSize, 2f, tr, tg, tb, 0.7f)
      } else {
        shapeBatch.fillRect(slotX, startY, slotSize, slotSize, 0.06f, 0.06f, 0.1f, 0.5f)
        shapeBatch.strokeRect(slotX, startY, slotSize, slotSize, 1f, 0.24f, 0.24f, 0.27f, 0.5f)
      }

      val iconCX = slotX + slotSize / 2; val iconCY = startY + slotSize / 2 - 1; val iconSize = 12f
      if (count > 0) drawItemShape(itemType, iconCX, iconCY, iconSize, tr, tg, tb)
      else drawItemShape(itemType, iconCX, iconCY, iconSize, tr * 0.3f, tg * 0.3f, tb * 0.3f)

      if (count > 0) {
        val badgeW = Math.max(14f, 7f + count.toString.length * 6f)
        val badgeX = slotX + slotSize - badgeW / 2 - 1
        val badgeY = startY - 5
        shapeBatch.fillRect(badgeX, badgeY, badgeW, 14f, 0.78f, 0.18f, 0.18f, 0.9f)
      }
    }

    // Draw all text labels (sprites) in one batch
    beginSprites()
    for (i <- inventorySlotTypes.indices) {
      val (itemType, keyLabel) = inventorySlotTypes(i)
      val count = client.getItemCount(itemType.id)
      val slotX = startX + i * (slotSize + slotGap)

      if (count > 0) {
        fontSmall.drawText(spriteBatch, count.toString, slotX + slotSize - 8, startY - 3)
      }
      fontSmall.drawText(spriteBatch, keyLabel, slotX + slotSize / 2 - 4, startY + slotSize - 16,
        if (count > 0) 0.9f else 0.43f, if (count > 0) 0.9f else 0.43f, if (count > 0) 0.9f else 0.47f, 1f)
    }
  }

  private def renderAbilityHUD(screenW: Int, screenH: Int): Unit = {
    val slotSize = 44f; val slotGap = 8f
    val invSlotSize = 44f; val invSlotGap = 8f; val invNumSlots = 5
    val totalInvW = invNumSlots * invSlotSize + (invNumSlots - 1) * invSlotGap
    val inventoryStartX = (screenW - totalInvW) / 2f
    val startY = screenH - slotSize - 14f

    val charDef = client.getSelectedCharacterDef
    val abilityDefs = Array(charDef.qAbility, charDef.eAbility)
    val cooldowns = Array(client.getQCooldownFraction, client.getECooldownFraction)

    val now = System.currentTimeMillis()
    val abilityGap = 12f
    beginShapes()
    for (i <- abilityDefs.indices) {
      val aDef = abilityDefs(i)
      val cooldownFrac = cooldowns(i)
      val (ar, ag, ab) = abilityColors(i)
      val slotX = inventoryStartX - (abilityDefs.length - i) * (slotSize + slotGap) - abilityGap
      val onCooldown = cooldownFrac > 0.001f

      // Detect cooldown-to-ready transition for flash
      val isReady = !onCooldown
      if (isReady && !prevCooldownReady(i)) {
        cooldownReadyFlashTime(i) = now
      }
      prevCooldownReady(i) = isReady

      // Slot background
      shapeBatch.fillRect(slotX, startY, slotSize, slotSize, 0.06f, 0.06f, 0.1f, 0.8f)

      // Ability icon inside the slot
      val cx = slotX + slotSize / 2; val cy = startY + slotSize / 2
      val iconAlpha = if (onCooldown) 0.35f else 0.9f
      drawAbilityIcon(aDef.castBehavior, cx, cy, 12f, ar, ag, ab, iconAlpha)

      // Cooldown overlay — radial sweep (clock-wipe from 12 o'clock)
      if (onCooldown) {
        val sweepAngle = cooldownFrac * Math.PI.toFloat * 2f
        val radius = slotSize * 0.72f
        val startAngle = -Math.PI.toFloat / 2f
        val steps = Math.max(4, (cooldownFrac * 24).toInt)
        _sweepXs(0) = cx; _sweepYs(0) = cy
        var s = 0
        while (s <= steps) {
          val angle = startAngle + (s.toFloat / steps) * sweepAngle
          _sweepXs(s + 1) = cx + Math.cos(angle).toFloat * radius
          _sweepYs(s + 1) = cy + Math.sin(angle).toFloat * radius
          s += 1
        }
        shapeBatch.fillPolygon(_sweepXs, _sweepYs, steps + 2, 0f, 0f, 0f, 0.55f)
        shapeBatch.strokeRect(slotX, startY, slotSize, slotSize, 1.5f, 0.39f, 0.39f, 0.39f, 0.6f)
      } else {
        shapeBatch.strokeRect(slotX, startY, slotSize, slotSize, 2f, ar, ag, ab, 0.9f)
        // Ready flash pulse (300ms glow when cooldown completes)
        val flashElapsed = now - cooldownReadyFlashTime(i)
        if (flashElapsed < 300 && cooldownReadyFlashTime(i) > 0) {
          val flashAlpha = (1f - flashElapsed / 300f) * 0.4f
          shapeBatch.setAdditiveBlend(true)
          shapeBatch.fillRect(slotX, startY, slotSize, slotSize, ar, ag, ab, flashAlpha)
          shapeBatch.setAdditiveBlend(false)
        }
      }
    }

    // Key labels
    beginSprites()
    for (i <- abilityDefs.indices) {
      val slotX = inventoryStartX - (abilityDefs.length - i) * (slotSize + slotGap) - abilityGap
      val (ar, ag, ab) = abilityColors(i)
      val onCooldown = cooldowns(i) > 0.001f
      val ka = if (onCooldown) 0.5f else 1f
      fontSmall.drawTextOutlined(spriteBatch, abilityDefs(i).keybind,
        slotX + slotSize - fontSmall.measureWidth(abilityDefs(i).keybind) - 3, startY + slotSize - fontSmall.charHeight - 1,
        ar * ka, ag * ka, ab * ka, ka)
    }
  }

  /** Draw a simple icon representing the cast behavior type. */
  private def drawAbilityIcon(behavior: CastBehavior, cx: Float, cy: Float, sz: Float,
                              r: Float, g: Float, b: Float, a: Float): Unit = {
    behavior match {
      case StandardProjectile =>
        // Circle with a dot — basic projectile
        shapeBatch.fillOval(cx, cy, sz, sz, r, g, b, a * 0.4f, 12)
        shapeBatch.fillOval(cx, cy, sz * 0.45f, sz * 0.45f, r, g, b, a, 8)
        shapeBatch.strokeOval(cx, cy, sz, sz, 1.5f, r, g, b, a * 0.8f, 12)

      case FanProjectile(count, _) =>
        // Multiple small dots in a fan
        val spread = Math.min(count, 5)
        for (i <- 0 until spread) {
          val angle = -Math.PI / 2 + (i - (spread - 1) / 2.0) * Math.PI / 6
          val dx = Math.cos(angle).toFloat * sz * 0.7f
          val dy = Math.sin(angle).toFloat * sz * 0.7f
          shapeBatch.fillOval(cx + dx, cy + dy, sz * 0.25f, sz * 0.25f, r, g, b, a, 6)
        }
        shapeBatch.strokeOval(cx, cy, sz * 0.3f, sz * 0.3f, 1.5f, r, g, b, a * 0.6f, 8)

      case _: PhaseShiftBuff =>
        // Ghost silhouette — hollow oval with dashes
        shapeBatch.strokeOval(cx, cy, sz * 0.8f, sz, 2f, r, g, b, a * 0.5f, 12)
        shapeBatch.strokeOval(cx, cy, sz * 0.5f, sz * 0.65f, 1.5f, r, g, b, a * 0.8f, 10)
        // Eye dots
        shapeBatch.fillOval(cx - sz * 0.2f, cy - sz * 0.15f, 2f, 2f, r, g, b, a, 6)
        shapeBatch.fillOval(cx + sz * 0.2f, cy - sz * 0.15f, 2f, 2f, r, g, b, a, 6)

      case _: DashBuff =>
        // Arrow pointing right — motion lines
        shapeBatch.fillPolygon(
          Array(cx + sz * 0.6f, cx - sz * 0.3f, cx - sz * 0.3f),
          Array(cy, cy - sz * 0.45f, cy + sz * 0.45f), r, g, b, a)
        // Speed lines
        for (i <- 0 until 3) {
          val ly = cy + (i - 1) * sz * 0.35f
          shapeBatch.strokeLine(cx - sz * 0.9f, ly, cx - sz * 0.4f, ly, 1.5f, r, g, b, a * 0.5f)
        }

      case _: TeleportCast =>
        // Lightning bolt / flash
        shapeBatch.fillPolygon(
          Array(cx - sz * 0.15f, cx + sz * 0.35f, cx + sz * 0.05f, cx + sz * 0.45f, cx - sz * 0.1f, cx + sz * 0.1f),
          Array(cy - sz * 0.7f, cy - sz * 0.1f, cy - sz * 0.1f, cy + sz * 0.7f, cy + sz * 0.1f, cy + sz * 0.1f),
          r, g, b, a)

      case _: GroundSlam =>
        // Expanding rings
        shapeBatch.strokeOval(cx, cy, sz * 0.4f, sz * 0.25f, 2f, r, g, b, a, 12)
        shapeBatch.strokeOval(cx, cy, sz * 0.8f, sz * 0.5f, 1.5f, r, g, b, a * 0.7f, 12)
        shapeBatch.strokeOval(cx, cy, sz * 1.1f, sz * 0.7f, 1f, r, g, b, a * 0.4f, 12)
        // Center impact
        shapeBatch.fillOval(cx, cy, sz * 0.2f, sz * 0.13f, r, g, b, a, 8)
    }
  }

  private def renderChargeBar(screenW: Int, screenH: Int): Unit = {
    if (!client.isCharging) return
    val cpt = client.getSelectedCharacterDef.primaryProjectileType
    if (!ProjectileDef.get(cpt).chargeSpeedScaling.isDefined) return

    val chargeLevel = client.getChargeLevel
    val barW = 100f; val barH = 8f
    val barX = (screenW - barW) / 2f; val barY = screenH - 80f

    beginShapes()
    shapeBatch.fillRect(barX - 2, barY - 2, barW + 4, barH + 4, 0.12f, 0.12f, 0.12f, 0.8f)
    shapeBatch.fillRect(barX, barY, barW, barH, 0.24f, 0.24f, 0.24f, 1f)
    val pct = chargeLevel / 100f
    val cr = Math.min(1f, pct * 2f); val cg = Math.min(1f, Math.max(0f, 1f - (pct - 0.5f) * 2f))
    shapeBatch.fillRect(barX, barY, barW * pct, barH, cr, cg, 0f, 1f)
    shapeBatch.strokeRect(barX, barY, barW, barH, 1f, 0.78f, 0.78f, 0.78f, 0.8f)

    shapeBatch.setAdditiveBlend(true)
    shapeBatch.fillOvalSoft(barX + barW * pct, barY + barH / 2, 6f, 6f, cr, cg, 0.2f, 0.3f * pct, 0f, 12)
    shapeBatch.setAdditiveBlend(false)

    beginSprites()
    fontSmall.drawText(spriteBatch, s"$chargeLevel%", barX + barW / 2 - 12, barY - 16)
  }

  private def renderLobbyHUD(screenW: Int, screenH: Int): Unit = {
    if (client.clientState != ClientState.PLAYING) return

    val remaining = client.gameTimeRemaining
    val minutes = remaining / 60; val seconds = remaining % 60
    val timerText = f"$minutes%d:$seconds%02d"

    // Timer panel at top center
    val timerW = fontMedium.measureWidth(timerText) + 20
    beginShapes()
    shapeBatch.fillRect((screenW / 2 - timerW / 2).toFloat, 4f, timerW.toFloat, 30f, 0.04f, 0.04f, 0.08f, 0.5f)
    shapeBatch.strokeRect((screenW / 2 - timerW / 2).toFloat, 4f, timerW.toFloat, 30f, 1f, 0.3f, 0.3f, 0.4f, 0.3f)

    beginSprites()
    fontMedium.drawTextOutlined(spriteBatch, timerText, (screenW / 2 - fontMedium.measureWidth(timerText) / 2).toFloat, 8)

    // K/D display below status panel
    fontSmall.drawTextOutlined(spriteBatch, s"K: ${client.killCount}  D: ${client.deathCount}", 12, 108)

    // Kill feed with slide-in animation and background strips
    val now = System.currentTimeMillis()
    var feedY = 10f
    val localName = client.getSelectedCharacterDef.displayName
    client.killFeed.asScala.foreach { entry =>
      val timestamp = entry(0).asInstanceOf[java.lang.Long].longValue()
      val elapsed = now - timestamp
      if (elapsed < 6000) {
        val alpha = Math.max(0.15f, (1.0 - elapsed / 6000.0).toFloat)
        val killer = entry(1).asInstanceOf[String]
        val victim = entry(2).asInstanceOf[String]
        val feedText = s"$killer killed $victim"
        val textW = fontSmall.measureWidth(feedText)

        // Slide-in from right edge (first 200ms)
        val slideOffset = if (elapsed < 200) ((1.0 - elapsed / 200.0) * 60).toFloat else 0f
        val feedX = screenW - textW - 12f + slideOffset

        // Semi-transparent background strip
        beginShapes()
        shapeBatch.fillRect(feedX - 4f, feedY - 2f, textW + 8f, 18f, 0.04f, 0.04f, 0.08f, 0.4f * alpha)

        // Highlight local player's name in kill feed entries
        beginSprites()
        val isLocalKiller = killer == localName
        val isLocalVictim = victim == localName
        if (isLocalKiller) {
          fontSmall.drawText(spriteBatch, feedText, feedX, feedY, 0.4f, 1f, 0.4f, alpha)
        } else if (isLocalVictim) {
          fontSmall.drawText(spriteBatch, feedText, feedX, feedY, 1f, 0.4f, 0.4f, alpha)
        } else {
          fontSmall.drawText(spriteBatch, feedText, feedX, feedY, 1f, 1f, 1f, alpha)
        }
        feedY += 18
      }
    }
  }

  // ═══════════════════════════════════════════════════════════════════
  //  GAME OVER + RESPAWN SCREENS
  // ═══════════════════════════════════════════════════════════════════

  private def renderGameOverScreen(fbWidth: Int, fbHeight: Int): Unit = {
    val proj = Matrix4.orthographic(0f, fbWidth.toFloat, fbHeight.toFloat, 0f)
    glViewport(0, 0, fbWidth, fbHeight)
    glClearColor(0f, 0f, 0f, 1f)
    glClear(GL_COLOR_BUFFER_BIT)

    shapeBatch.begin(proj)
    shapeBatch.fillRect(0, 0, fbWidth.toFloat, fbHeight.toFloat, 0f, 0f, 0f, 0.75f)
    shapeBatch.end()

    spriteBatch.begin(proj)
    val cx = fbWidth / 2f; val cy = fbHeight / 2f
    fontLarge.drawTextOutlined(spriteBatch, "GAME OVER", cx - fontLarge.measureWidth("GAME OVER") / 2, cy - 20, 0.9f, 0.2f, 0.2f, 1f)
    fontMedium.drawTextOutlined(spriteBatch, "Press Enter to reload", cx - fontMedium.measureWidth("Press Enter to reload") / 2, cy + 30)
    spriteBatch.end()
  }

  private def renderRespawnCountdown(screenW: Int, screenH: Int): Unit = {
    val deathTime = client.getLocalDeathTime
    val elapsed = System.currentTimeMillis() - deathTime
    val remaining = Math.max(0, Constants.RESPAWN_DELAY_MS - elapsed)
    val secondsLeft = Math.ceil(remaining / 1000.0).toInt

    val cx = screenW / 2f; val cy = screenH / 2f

    beginShapes()
    shapeBatch.fillRect(cx - 120, cy - 50, 240, 90, 0f, 0f, 0f, 0.55f)

    beginSprites()
    fontMedium.drawTextOutlined(spriteBatch, s"Respawning in ${secondsLeft}s", cx - fontMedium.measureWidth(s"Respawning in ${secondsLeft}s") / 2, cy - 30)

    val killerName = client.lastKillerCharacterName
    if (killerName != null && killerName.nonEmpty) {
      fontSmall.drawTextOutlined(spriteBatch, s"Killed by $killerName", cx - fontSmall.measureWidth(s"Killed by $killerName") / 2, cy)
    }
  }

  // ═══════════════════════════════════════════════════════════════════
  //  ANIMATED TILE OVERLAYS
  // ═══════════════════════════════════════════════════════════════════

  private def drawTileOverlays(): Unit = {
    beginShapes()
    shapeBatch.setAdditiveBlend(true)
    val time = animationTick.toFloat
    var i = 0
    while (i < specialTiles.size) {
      val (wx, wy, tid) = specialTiles(i)
      val sx = worldToScreenX(wx, wy).toFloat
      val sy = worldToScreenY(wx, wy).toFloat
      tid match {
        case 1 | 7 => // Water / DeepWater — shimmer lines + specular highlight
          val phase = time * 0.04f + wx * 1.1f + wy * 0.7f
          // Moving shimmer lines across diamond
          for (j <- 0 until 3) {
            val linePhase = phase + j * 2.1f
            val t = (linePhase % 3.0f) / 3.0f
            val lx1 = sx - HW * (1f - t) + HW * t
            val ly1 = sy - HH * t + HH * (1f - t) * 0.3f
            val lx2 = lx1 + HW * 0.4f
            val ly2 = ly1 + HH * 0.2f
            val shimmerA = (0.3f * Math.sin(linePhase * 1.5).toFloat).abs
            shapeBatch.strokeLine(lx1, ly1, lx2, ly2, 1f, 0.6f, 0.8f, 1f, shimmerA)
          }
          // Specular highlight dot
          val specPhase = time * 0.03f + wx * 2.3f + wy * 1.7f
          val specA = (0.15f + 0.15f * Math.sin(specPhase * 2.0)).toFloat
          shapeBatch.fillOval(sx + Math.sin(specPhase).toFloat * 5f, sy + Math.cos(specPhase * 0.7).toFloat * 2f,
            2.5f, 1.5f, 0.8f, 0.95f, 1f, specA, 6)

        case 10 => // Lava — pulsing orange glow + crack lines
          val lavaPhase = time * 0.05f + wx * 0.9f + wy * 1.3f
          val glowA = (0.10f + 0.06f * Math.sin(lavaPhase)).toFloat
          shapeBatch.fillOvalSoft(sx, sy, HW.toFloat * 0.8f, HH.toFloat * 0.6f, 1f, 0.5f, 0.1f, glowA, 0f, 10)
          // Bright crack lines
          for (j <- 0 until 2) {
            val crackPhase = lavaPhase + j * 1.5f
            val ca = (0.2f + 0.1f * Math.sin(crackPhase * 2.0)).toFloat
            val cx1 = sx - 6 + Math.sin(crackPhase + j).toFloat * 4
            val cy1 = sy - 2 + Math.cos(crackPhase * 0.8).toFloat * 2
            val cx2 = cx1 + 8 + Math.sin(crackPhase * 1.3).toFloat * 3
            val cy2 = cy1 + 3
            shapeBatch.strokeLine(cx1, cy1, cx2, cy2, 1.5f, 1f, 0.85f, 0.3f, ca)
          }

        case 9 => // Ice — flashing sparkle points
          val icePhase = time * 0.08f + wx * 3.7f + wy * 2.3f
          for (j <- 0 until 3) {
            val sparkPhase = icePhase + j * 2.094f
            val flash = Math.max(0f, Math.sin(sparkPhase * 1.5).toFloat)
            if (flash > 0.3f) {
              // Deterministic positions based on tile coords
              val offX = ((wx * 13 + wy * 7 + j * 17) % 11 - 5).toFloat
              val offY = ((wx * 7 + wy * 11 + j * 23) % 7 - 3).toFloat * 0.5f
              shapeBatch.fillOval(sx + offX, sy + offY, 1.5f, 1.5f, 0.85f, 0.95f, 1f, flash * 0.5f, 4)
            }
          }

        case 24 => // Crystal — prismatic hue cycling glow
          val crystPhase = time * 0.03f + wx * 1.3f + wy * 0.9f
          val cr = (0.5f + 0.5f * Math.sin(crystPhase)).toFloat
          val cg = (0.5f + 0.5f * Math.sin(crystPhase + 2.094f)).toFloat
          val cb = (0.5f + 0.5f * Math.sin(crystPhase + 4.189f)).toFloat
          shapeBatch.fillOvalSoft(sx, sy, HW.toFloat * 0.6f, HH.toFloat * 0.5f, cr, cg, cb, 0.10f, 0f, 10)

        case 18 => // Toxic — bubbling green glow pulse
          val toxPhase = time * 0.06f + wx * 1.7f + wy * 2.1f
          val bubbleA = (0.06f + 0.04f * Math.sin(toxPhase)).toFloat
          shapeBatch.fillOvalSoft(sx, sy, HW.toFloat * 0.7f, HH.toFloat * 0.5f, 0.2f, 0.9f, 0.1f, bubbleA, 0f, 10)
          // Rising bubble
          val bubbleY = sy - ((time * 0.3f + wx * 5) % 8)
          val bubbleSize = 1.5f + Math.sin(toxPhase * 2).toFloat
          shapeBatch.fillOval(sx + Math.sin(toxPhase * 1.5).toFloat * 3, bubbleY,
            bubbleSize, bubbleSize, 0.3f, 1f, 0.2f, 0.15f, 4)

        case 15 => // EnergyField — electric arc flicker
          val ePhase = time * 0.12f + wx * 2.1f + wy * 1.3f
          val flicker = if (Math.sin(ePhase * 3.0) > 0.2) 1f else 0.3f
          val arcA = 0.12f * flicker
          // Small electric arc
          val ax1 = sx - 5 + Math.sin(ePhase).toFloat * 3
          val ay1 = sy - 2
          val ax2 = sx + 5 + Math.cos(ePhase * 1.3).toFloat * 3
          val ay2 = sy + 1
          val amid = sx + Math.sin(ePhase * 5).toFloat * 4
          shapeBatch.strokeLine(ax1, ay1, amid, (ay1 + ay2) / 2 + Math.sin(ePhase * 7).toFloat * 2,
            1f, 0.6f, 0.3f, 1f, arcA)
          shapeBatch.strokeLine(amid, (ay1 + ay2) / 2 + Math.sin(ePhase * 7).toFloat * 2, ax2, ay2,
            1f, 0.6f, 0.3f, 1f, arcA)

        case _ => // shouldn't happen
      }
      i += 1
    }
    shapeBatch.setAdditiveBlend(false)
  }

  // ═══════════════════════════════════════════════════════════════════
  //  DYNAMIC LIGHTING HELPERS
  // ═══════════════════════════════════════════════════════════════════

  /** Populate lights from players (warm glow) and local player. */
  private def populateLights(): Unit = {
    // Local player light
    val (lvx, lvy) = client.getVisualPosition
    val lpsx = worldToScreenX(lvx, lvy).toFloat
    val lpsy = worldToScreenY(lvx, lvy).toFloat
    lightSystem.addLight(lpsx, lpsy, 80f, 1f, 0.9f, 0.7f, 0.15f)

    // Remote players
    val players = client.getPlayers
    val iter = players.values().iterator()
    while (iter.hasNext) {
      val player = iter.next()
      val (pvx, pvy) = entityCollector.remoteVisualPositions.getOrElse(
        player.getId, (player.getPosition.getX.toDouble, player.getPosition.getY.toDouble))
      val psx = worldToScreenX(pvx, pvy).toFloat
      val psy = worldToScreenY(pvx, pvy).toFloat
      lightSystem.addLight(psx, psy, 65f, 1f, 0.85f, 0.65f, 0.10f)
    }

    // Explosion lights
    val now = System.currentTimeMillis()
    val expIter = client.getExplosionAnimations.values().iterator()
    while (expIter.hasNext) {
      val data = expIter.next()
      val timestamp = data(0)
      val elapsed = now - timestamp
      if (elapsed < 1400) {
        val wx = data(1).toDouble / 1000.0; val wy = data(2).toDouble / 1000.0
        val colorRGB = data(3).toInt
        val (er, eg, eb) = intToRGB(colorRGB)
        val exsx = worldToScreenX(wx, wy).toFloat
        val exsy = worldToScreenY(wx, wy).toFloat
        val decay = Math.max(0f, 1f - elapsed.toFloat / 1400f)
        lightSystem.addLight(exsx, exsy, 120f * decay, er, eg, eb, 0.4f * decay)
      }
    }
  }

  // ═══════════════════════════════════════════════════════════════════
  //  DAMAGE NUMBER DETECTION
  // ═══════════════════════════════════════════════════════════════════

  private def detectDamageNumbers(): Unit = {
    // Check local player
    val localId = client.getLocalPlayerId
    val localHealth = client.getLocalHealth
    prevHealthMap.get(localId) match {
      case Some(prev) if prev > localHealth =>
        val dmg = prev - localHealth
        val (lvx, lvy) = client.getVisualPosition
        damageNumbers.spawn(lvx.toFloat, lvy.toFloat, dmg, 1f, 0.3f, 0.2f)
        spawnImpactSparks(lvx.toFloat, lvy.toFloat, 1f, 0.3f, 0.2f)
      case _ =>
    }
    prevHealthMap.put(localId, localHealth)

    // Check remote players
    val players = client.getPlayers
    val iter = players.entrySet().iterator()
    while (iter.hasNext) {
      val entry = iter.next()
      val playerId = entry.getKey
      val player = entry.getValue
      val health = player.getHealth
      prevHealthMap.get(playerId) match {
        case Some(prev) if prev > health =>
          val dmg = prev - health
          val (pvx, pvy) = entityCollector.remoteVisualPositions.getOrElse(
            playerId, (player.getPosition.getX.toDouble, player.getPosition.getY.toDouble))
          damageNumbers.spawn(pvx.toFloat, pvy.toFloat, dmg, 1f, 1f, 0.3f)
          spawnImpactSparks(pvx.toFloat, pvy.toFloat, 1f, 1f, 0.3f)
        case _ =>
      }
      prevHealthMap.put(playerId, health)
    }
  }

  // ═══════════════════════════════════════════════════════════════════
  //  POST-PROCESSING EFFECT HELPERS
  // ═══════════════════════════════════════════════════════════════════

  private def updateExplosionDistortion(): Unit = {
    val now = System.currentTimeMillis()
    var bestStrength = 0f
    var bestCX = 0.5f
    var bestCY = 0.5f

    val expIter = client.getExplosionAnimations.values().iterator()
    while (expIter.hasNext) {
      val data = expIter.next()
      val timestamp = data(0)
      val elapsed = now - timestamp
      if (elapsed < 600) { // distortion only in first 600ms
        val wx = data(1).toDouble / 1000.0; val wy = data(2).toDouble / 1000.0
        val exsx = worldToScreenX(wx, wy)
        val exsy = worldToScreenY(wx, wy)

        // Convert to UV space (0-1) for the shader
        val uvX = (exsx / canvasW).toFloat
        val uvY = (exsy / canvasH).toFloat

        // Check proximity to local player
        val (lvx, lvy) = client.getVisualPosition
        val dx = wx - lvx; val dy = wy - lvy
        val dist = Math.sqrt(dx * dx + dy * dy)
        if (dist < 8) { // only distort if within 8 tiles
          val proximity = Math.max(0, 1.0 - dist / 8.0).toFloat
          val timeDecay = Math.max(0f, 1f - elapsed.toFloat / 600f)
          val strength = 0.008f * proximity * timeDecay
          if (strength > bestStrength) {
            bestStrength = strength
            bestCX = uvX
            bestCY = uvY
          }
        }
      }
    }

    postProcessor.distortionStrength = bestStrength
    postProcessor.distortionCenterX = bestCX
    postProcessor.distortionCenterY = bestCY
  }

  // ═══════════════════════════════════════════════════════════════════
  //  WEATHER PARTICLES
  // ═══════════════════════════════════════════════════════════════════

  private def spawnWeatherParticles(background: String, dt: Float): Unit = {
    val w = canvasW.toFloat; val h = canvasH.toFloat
    val rate = background match {
      case "sky"       => 12f  // gentle leaf/pollen drift
      case "ocean"     => 20f  // spray mist
      case "space"     => 4f   // drifting dust motes
      case "desert"    => 15f  // sand particles
      case "cityscape" => 8f   // dust/ash
      case _           => 8f
    }
    weatherSpawnAccum += rate * dt
    while (weatherSpawnAccum >= 1f) {
      weatherSpawnAccum -= 1f
      background match {
        case "sky"       => spawnSkyParticle(w, h)
        case "ocean"     => spawnOceanParticle(w, h)
        case "space"     => spawnSpaceParticle(w, h)
        case "desert"    => spawnDesertParticle(w, h)
        case "cityscape" => spawnCityParticle(w, h)
        case _           => spawnSkyParticle(w, h)
      }
    }
  }

  private def spawnSkyParticle(w: Float, h: Float): Unit = {
    // Drifting pollen/leaf particles
    val px = rng.nextFloat() * (w + 40) - 20
    weatherParticles.emit(
      px, -5f,
      rng.nextFloat() * 15f - 5f, 12f + rng.nextFloat() * 8f,
      plife = 4f + rng.nextFloat() * 3f,
      pr = 0.95f, pg = 0.95f, pb = 0.85f, palpha = 0.15f + rng.nextFloat() * 0.1f,
      psize = 1.5f + rng.nextFloat() * 1.5f,
      soft = true
    )
  }

  private def spawnOceanParticle(w: Float, h: Float): Unit = {
    // Rising spray/mist
    val px = rng.nextFloat() * w
    val py = h * 0.6f + rng.nextFloat() * h * 0.4f
    weatherParticles.emit(
      px, py,
      rng.nextFloat() * 6f - 3f, -(4f + rng.nextFloat() * 6f),
      plife = 2f + rng.nextFloat() * 2f,
      pr = 0.7f, pg = 0.85f, pb = 0.95f, palpha = 0.08f + rng.nextFloat() * 0.07f,
      psize = 2f + rng.nextFloat() * 3f,
      soft = true
    )
  }

  private def spawnSpaceParticle(w: Float, h: Float): Unit = {
    // Slow-drifting dust motes
    val px = rng.nextFloat() * w
    val py = rng.nextFloat() * h
    weatherParticles.emit(
      px, py,
      rng.nextFloat() * 4f - 2f, rng.nextFloat() * 2f - 1f,
      plife = 5f + rng.nextFloat() * 4f,
      pr = 0.6f, pg = 0.5f, pb = 0.8f, palpha = 0.06f + rng.nextFloat() * 0.06f,
      psize = 1f + rng.nextFloat() * 1.5f,
      additive = true, soft = true
    )
  }

  private def spawnDesertParticle(w: Float, h: Float): Unit = {
    // Wind-blown sand
    val px = -10f
    val py = h * 0.3f + rng.nextFloat() * h * 0.6f
    weatherParticles.emit(
      px, py,
      30f + rng.nextFloat() * 20f, rng.nextFloat() * 8f - 4f,
      plife = 2.5f + rng.nextFloat() * 2f,
      pr = 0.9f, pg = 0.8f, pb = 0.5f, palpha = 0.08f + rng.nextFloat() * 0.06f,
      psize = 1f + rng.nextFloat() * 1.5f,
      soft = true
    )
  }

  private def spawnCityParticle(w: Float, h: Float): Unit = {
    // Floating dust/ash
    val px = rng.nextFloat() * w
    weatherParticles.emit(
      px, h + 5f,
      rng.nextFloat() * 6f - 3f, -(5f + rng.nextFloat() * 4f),
      plife = 4f + rng.nextFloat() * 3f,
      pr = 0.5f, pg = 0.45f, pb = 0.55f, palpha = 0.07f + rng.nextFloat() * 0.06f,
      psize = 1f + rng.nextFloat() * 1f,
      soft = true
    )
  }

  // ═══════════════════════════════════════════════════════════════════
  //  GAMEPLAY PARTICLES
  // ═══════════════════════════════════════════════════════════════════

  private def spawnGameplayParticles(dt: Float): Unit = {
    spawnFootstepDust(dt)
    spawnProjectileTrails(dt)
  }

  private def spawnFootstepDust(dt: Float): Unit = {
    val (lvx, lvy) = (camera.visualX, camera.visualY)
    if (prevLocalVX.isNaN) {
      prevLocalVX = lvx; prevLocalVY = lvy
      return
    }
    val dx = lvx - prevLocalVX; val dy = lvy - prevLocalVY
    val moved = Math.sqrt(dx * dx + dy * dy)
    prevLocalVX = lvx; prevLocalVY = lvy

    if (moved > 0.01 && client.getIsMoving) {
      footstepAccum += dt
      if (footstepAccum >= 0.12f) {
        footstepAccum = 0f
        val sx = worldToScreenX(lvx, lvy).toFloat
        val sy = worldToScreenY(lvx, lvy).toFloat
        // Small dust puffs at feet
        for (_ <- 0 until 3) {
          combatParticles.emit(
            sx + rng.nextFloat() * 6f - 3f, sy + rng.nextFloat() * 2f,
            rng.nextFloat() * 8f - 4f, -(2f + rng.nextFloat() * 4f),
            plife = 0.4f + rng.nextFloat() * 0.3f,
            pr = 0.6f, pg = 0.55f, pb = 0.45f, palpha = 0.2f,
            psize = 1.5f + rng.nextFloat() * 1.5f,
            pgravity = 5f, soft = true
          )
        }
      }
    }
  }

  private def spawnProjectileTrails(dt: Float): Unit = {
    val projs = client.getProjectiles
    if (projs.isEmpty) return
    val iter = projs.values().iterator()
    // Spawn trail particles for ~30% of projectiles each frame to limit count
    while (iter.hasNext) {
      val proj = iter.next()
      if (rng.nextFloat() < 0.3f) {
        val sx = worldToScreenX(proj.getX, proj.getY).toFloat
        val sy = worldToScreenY(proj.getX, proj.getY).toFloat
        val (pr, pg, pb) = intToRGB(proj.colorRGB)
        combatParticles.emit(
          sx + rng.nextFloat() * 4f - 2f, sy + rng.nextFloat() * 2f - 1f,
          rng.nextFloat() * 4f - 2f, rng.nextFloat() * 4f - 2f,
          plife = 0.2f + rng.nextFloat() * 0.15f,
          pr = clamp(pr * 0.7f + 0.3f), pg = clamp(pg * 0.7f + 0.3f), pb = clamp(pb * 0.7f + 0.3f),
          palpha = 0.25f,
          psize = 1.5f + rng.nextFloat() * 1f,
          additive = true
        )
      }
    }
  }

  /** Spawn impact sparks when a player takes damage (called from detectDamageNumbers). */
  private def spawnImpactSparks(worldX: Float, worldY: Float, cr: Float, cg: Float, cb: Float): Unit = {
    val sx = worldToScreenX(worldX, worldY).toFloat
    val sy = worldToScreenY(worldX, worldY).toFloat - 12f
    for (_ <- 0 until 8) {
      val angle = rng.nextFloat() * Math.PI.toFloat * 2f
      val speed = 25f + rng.nextFloat() * 35f
      combatParticles.emit(
        sx, sy,
        Math.cos(angle).toFloat * speed, Math.sin(angle).toFloat * speed - 10f,
        plife = 0.2f + rng.nextFloat() * 0.2f,
        pr = clamp(cr * 0.5f + 0.5f), pg = clamp(cg * 0.5f + 0.5f), pb = clamp(cb * 0.5f + 0.5f),
        palpha = 0.7f,
        psize = 1.5f + rng.nextFloat() * 1.5f,
        pgravity = 60f, additive = true
      )
    }
  }

  /** Spawn a burst of particles on player death. */
  private def spawnDeathBurst(worldX: Float, worldY: Float, colorRGB: Int): Unit = {
    val sx = worldToScreenX(worldX, worldY).toFloat
    val sy = worldToScreenY(worldX, worldY).toFloat - 12f
    val (cr, cg, cb) = intToRGB(colorRGB)
    for (_ <- 0 until 20) {
      val angle = rng.nextFloat() * Math.PI.toFloat * 2f
      val speed = 15f + rng.nextFloat() * 45f
      val bright = rng.nextFloat()
      val pr = if (bright > 0.6f) 1f else cr
      val pg = if (bright > 0.6f) 1f else cg
      val pb = if (bright > 0.6f) 0.9f else cb
      combatParticles.emit(
        sx + rng.nextFloat() * 4f - 2f, sy + rng.nextFloat() * 4f - 2f,
        Math.cos(angle).toFloat * speed, Math.sin(angle).toFloat * speed * 0.7f - 15f,
        plife = 0.5f + rng.nextFloat() * 0.6f,
        pr = pr, pg = pg, pb = pb,
        palpha = 0.6f,
        psize = 2f + rng.nextFloat() * 2.5f,
        pgravity = 40f, additive = true
      )
    }
  }

  // ═══════════════════════════════════════════════════════════════════
  //  WATER REFLECTIONS
  // ═══════════════════════════════════════════════════════════════════

  private def drawWaterReflections(): Unit = {
    val world = client.getWorld
    val (lvx, lvy) = (camera.visualX, camera.visualY)
    val localPX = Math.round(lvx).toInt
    val localPY = Math.round(lvy).toInt

    // Only check tiles within 2 cells of local player
    val range = 2
    var hasWater = false
    var wy = localPY - range
    while (wy <= localPY + range && !hasWater) {
      var wx = localPX - range
      while (wx <= localPX + range && !hasWater) {
        if (wx >= 0 && wx < world.width && wy >= 0 && wy < world.height) {
          val tid = world.getTile(wx, wy).id
          if (tid == 1 || tid == 7) hasWater = true
        }
        wx += 1
      }
      wy += 1
    }
    if (!hasWater) return

    // Get local player sprite for reflection
    val frame = if (client.getIsMoving) (animationTick / FRAMES_PER_STEP) % 4 else 0
    val region = GLSpriteGenerator.getSpriteRegion(client.getLocalDirection, frame, client.selectedCharacterId)
    if (region == null) return

    // Create flipped region (swap v and v2 for vertical flip)
    val flippedRegion = TextureRegion(region.texture, region.u, region.v2, region.u2, region.v)
    val displaySz = Constants.PLAYER_DISPLAY_SIZE_PX.toFloat
    val playerScreenX = worldToScreenX(lvx, lvy).toFloat
    val playerScreenY = worldToScreenY(lvx, lvy).toFloat

    beginSprites()
    wy = localPY - range
    while (wy <= localPY + range) {
      var wx = localPX - range
      while (wx <= localPX + range) {
        if (wx >= 0 && wx < world.width && wy >= 0 && wy < world.height) {
          val tid = world.getTile(wx, wy).id
          if (tid == 1 || tid == 7) {
            val tileScreenX = worldToScreenX(wx, wy).toFloat
            val tileScreenY = worldToScreenY(wx, wy).toFloat
            val dx = tileScreenX - playerScreenX
            val dy = tileScreenY - playerScreenY
            if (Math.abs(dx) < 40 && Math.abs(dy) < 30) {
              // Shimmer distortion offset
              val shimmerOffset = Math.sin(animationTick * 0.08f + wx * 1.1f).toFloat * 2f
              val reflX = playerScreenX - displaySz / 2f + shimmerOffset
              val reflY = tileScreenY + 2f
              val dist = Math.sqrt(dx * dx + dy * dy).toFloat
              val reflAlpha = 0.15f * Math.max(0f, 1f - dist / 50f)
              if (reflAlpha > 0.01f) {
                spriteBatch.draw(flippedRegion, reflX, reflY, displaySz, displaySz * 0.6f,
                  0.6f, 0.7f, 0.9f, reflAlpha)
              }
            }
          }
        }
        wx += 1
      }
      wy += 1
    }
  }

  // ═══════════════════════════════════════════════════════════════════
  //  ITEM PICKUP DETECTION
  // ═══════════════════════════════════════════════════════════════════

  private def detectItemChanges(): Unit = {
    // Items tracked but not drawn this frame = picked up
    val toRemove = new mutable.ArrayBuffer[Int]()
    val iter = itemLastWorldPos.iterator
    while (iter.hasNext) {
      val (id, (wx, wy, colorRGB)) = iter.next()
      if (!drawnItemIdsThisFrame.contains(id)) {
        // Spawn pickup ring burst particles
        val sx = worldToScreenX(wx, wy).toFloat
        val sy = worldToScreenY(wx, wy).toFloat
        val (cr, cg, cb) = intToRGB(colorRGB)
        for (_ <- 0 until 12) {
          val angle = rng.nextFloat() * Math.PI.toFloat * 2f
          val speed = 25f + rng.nextFloat() * 35f
          combatParticles.emit(sx, sy,
            Math.cos(angle).toFloat * speed, Math.sin(angle).toFloat * speed,
            plife = 0.3f + rng.nextFloat() * 0.25f,
            pr = clamp(cr * 0.5f + 0.5f), pg = clamp(cg * 0.5f + 0.5f), pb = clamp(cb * 0.5f + 0.5f),
            palpha = 0.6f,
            psize = 2f + rng.nextFloat() * 2f,
            pgravity = 15f, additive = true)
        }
        toRemove += id
        itemSpawnTimes.remove(id)
      }
    }
    toRemove.foreach(itemLastWorldPos.remove)
  }

  // ═══════════════════════════════════════════════════════════════════
  //  ELEVATED TILE EDGE SHADOWS
  // ═══════════════════════════════════════════════════════════════════

  private def drawElevatedTileShadow(wx: Int, wy: Int, world: com.gridgame.common.model.WorldData): Unit = {
    val sx = worldToScreenX(wx, wy).toFloat
    val sy = worldToScreenY(wx, wy).toFloat
    val hw = HW.toFloat  // 20
    val hh = HH.toFloat  // 10

    // Check each adjacent tile — draw shadow on the walkable side
    // Shadow extends outward from the elevated tile onto the ground
    val shadowAlpha = 0.18f
    val shadowDist = 6f

    // Check south neighbor (wx, wy+1) — shadow falls on bottom-right edge
    if (wy + 1 < world.height && world.getTile(wx, wy + 1).walkable) {
      // Bottom-right edge of the diamond
      shapeBatch.fillPolygon(
        Array(sx, sx + hw, sx + hw, sx),
        Array(sy + hh, sy, sy + shadowDist, sy + hh + shadowDist),
        0.0f, 0.0f, 0.05f, shadowAlpha)
    }
    // Check east neighbor (wx+1, wy) — shadow falls on bottom-left edge
    if (wx + 1 < world.width && world.getTile(wx + 1, wy).walkable) {
      shapeBatch.fillPolygon(
        Array(sx, sx - hw, sx - hw, sx),
        Array(sy + hh, sy, sy + shadowDist, sy + hh + shadowDist),
        0.0f, 0.0f, 0.05f, shadowAlpha)
    }
    // Check north neighbor (wx, wy-1) — shadow falls on top-left edge (subtle)
    if (wy - 1 >= 0 && world.getTile(wx, wy - 1).walkable) {
      shapeBatch.fillPolygon(
        Array(sx, sx - hw, sx - hw, sx),
        Array(sy - hh, sy, sy - shadowDist, sy - hh - shadowDist),
        0.0f, 0.0f, 0.05f, shadowAlpha * 0.4f)
    }
    // Check west neighbor (wx-1, wy) — shadow falls on top-right edge (subtle)
    if (wx - 1 >= 0 && world.getTile(wx - 1, wy).walkable) {
      shapeBatch.fillPolygon(
        Array(sx, sx + hw, sx + hw, sx),
        Array(sy - hh, sy, sy - shadowDist, sy - hh - shadowDist),
        0.0f, 0.0f, 0.05f, shadowAlpha * 0.4f)
    }
  }

  // ═══════════════════════════════════════════════════════════════════
  //  UTILITIES
  // ═══════════════════════════════════════════════════════════════════

  private def intToRGB(argb: Int): (Float, Float, Float) = {
    val r = ((argb >> 16) & 0xFF) / 255f
    val g = ((argb >> 8) & 0xFF) / 255f
    val b = (argb & 0xFF) / 255f
    (r, g, b)
  }

  private def clamp(v: Float): Float = Math.max(0f, Math.min(1f, v))

  def resetVisualPosition(): Unit = {
    camera.resetVisualPosition()
    entityCollector.remoteVisualPositions.clear()
    lastRemotePositions.clear()
    remoteMovingUntil.clear()
    prevHealthMap.clear()
    smoothHealthMap.clear()
    itemSpawnTimes.clear()
    itemLastWorldPos.clear()
    drawnItemIdsThisFrame.clear()
    prevCooldownReady(0) = true; prevCooldownReady(1) = true
    cooldownReadyFlashTime(0) = 0L; cooldownReadyFlashTime(1) = 0L
    weatherParticles.clear()
    combatParticles.clear()
    deathBurstSpawned.clear()
    prevLocalVX = Double.NaN
    prevLocalVY = Double.NaN
  }

  def dispose(): Unit = {
    if (initialized) {
      shapeBatch.dispose()
      spriteBatch.dispose()
      fontSmall.dispose()
      fontMedium.dispose()
      fontLarge.dispose()
      postProcessor.dispose()
      lightSystem.dispose()
      GLTileRenderer.dispose()
      GLSpriteGenerator.clearCache()
      initialized = false
    }
  }
}
