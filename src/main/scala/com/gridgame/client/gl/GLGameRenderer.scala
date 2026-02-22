package com.gridgame.client.gl

import com.gridgame.client.ClientState
import com.gridgame.client.GameClient
import com.gridgame.client.render.{EntityCollector, GameCamera, IsometricTransform}
import com.gridgame.client.render.EntityCollector._
import com.gridgame.common.Constants
import com.gridgame.common.model._

import org.lwjgl.opengl.GL11._

import java.nio.FloatBuffer
import java.util.UUID
import scala.collection.mutable

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

  // Previous health tracking for damage number detection — Java HashMap avoids Option wrapping
  private val prevHealthMap = new java.util.HashMap[UUID, java.lang.Integer]()
  // Smooth health drain animation tracking
  private val smoothHealthMap = new java.util.HashMap[UUID, java.lang.Float]()

  // Tile overlay collection (parallel primitive arrays, reused each frame)
  private val MAX_SPECIAL_TILES = 512
  private val _specialTileWX = new Array[Int](MAX_SPECIAL_TILES)
  private val _specialTileWY = new Array[Int](MAX_SPECIAL_TILES)
  private val _specialTileTid = new Array[Int](MAX_SPECIAL_TILES)
  private var _specialTileCount = 0

  // Projection matrix
  private var projection: FloatBuffer = _

  // Animation state
  private var animationTick: Int = 0
  private val FRAMES_PER_STEP = 5
  private val TILE_ANIM_SPEED = 15
  private val HIT_ANIMATION_MS = 500
  private val DEATH_ANIMATION_MS = 1200
  private val TELEPORT_ANIMATION_MS = 800

  // Remote player movement detection — Java HashMaps avoid Option wrapping
  private val lastRemotePositions = new java.util.HashMap[UUID, Position]()
  private val remoteMovingUntil = new java.util.HashMap[UUID, java.lang.Long]()

  private val HW = Constants.ISO_HALF_W
  private val HH = Constants.ISO_HALF_H

  // Particle tracking
  private var weatherSpawnAccum: Float = 0f
  private var prevLocalVX: Double = Double.NaN
  private var prevLocalVY: Double = Double.NaN
  private var footstepAccum: Float = 0f
  private val rng = new java.util.Random()

  // Item spawn/pickup animation tracking — Java HashMaps avoid Option wrapping + tuple allocation
  private val itemSpawnTimes = new java.util.HashMap[java.lang.Integer, java.lang.Long]()
  private val itemLastWorldPos = new java.util.HashMap[java.lang.Integer, Array[Int]]() // id -> reusable int[3]{cellX, cellY, colorRGB}
  private val drawnItemIdsThisFrame = new java.util.HashSet[java.lang.Integer]()

  // Pre-allocated buffer for item removal during detectItemChanges (avoids per-frame ArrayBuffer allocation)
  private val _itemRemoveBuffer = new mutable.ArrayBuffer[java.lang.Integer]()

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
  // Ability colors unpacked into parallel arrays (see _abilityR/G/B below)

  // Frame-level cached values (set once per render() call, used everywhere)
  private var _frameTimeMs: Long = 0L   // _frameTimeMs cached once per frame
  private var _animTickF: Float = 0f    // animationTick as Float (avoids Int→Float per use)

  // Cached HUD strings — only re-allocated when values change
  private var _cachedPosX = Int.MinValue; private var _cachedPosY = Int.MinValue
  private var _cachedPosStr = ""
  private var _cachedPlayerCount = -1; private var _cachedPlayersStr = ""
  private var _cachedItemCount = -1; private var _cachedItemsStr = ""
  private var _cachedChargeLevel = -1; private var _cachedChargeStr = ""
  private var _cachedKills = -1; private var _cachedDeaths = -1; private var _cachedKDStr = ""
  // Reusable StringBuilder for kill feed text (avoids per-entry string interpolation)
  private val _feedTextBuilder = new java.lang.StringBuilder(64)
  // Cached closures for damage number rendering (avoids per-frame lambda allocation)
  private val _worldToScreenXFn: (Double, Double) => Double = (wx, wy) => worldToScreenX(wx, wy)
  private val _worldToScreenYFn: (Double, Double) => Double = (wx, wy) => worldToScreenY(wx, wy)

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
  private val deathBurstSpawned = new java.util.HashSet[Any]()

  // Pre-allocated arrays for inline fillPolygon calls (avoids per-call Array allocation)
  private val _heartTriXs = new Array[Float](3)
  private val _heartTriYs = new Array[Float](3)
  private val _indicatorXs = new Array[Float](4)
  private val _indicatorYs = new Array[Float](4)
  private val _shadowXs = new Array[Float](4)
  private val _shadowYs = new Array[Float](4)
  private val _dashArrowXs = new Array[Float](3)
  private val _dashArrowYs = new Array[Float](3)
  private val _teleportBoltXs = new Array[Float](6)
  private val _teleportBoltYs = new Array[Float](6)

  // Pre-unpacked ability colors (avoids Tuple3 allocation on each access)
  private val _abilityR = Array(0.2f, 0.4f)
  private val _abilityG = Array(0.8f, 0.7f)
  private val _abilityB = Array(0.3f, 1f)

  // Cached timer text (avoids per-frame format string allocation)
  private var _cachedTimerRemaining = -1
  private var _cachedTimerStr = ""
  // Cached background name (avoids per-frame string match in setAmbientForBackground)
  private var _cachedBackground: String = _
  // Cached flipped TextureRegion for water reflections (avoids per-frame case class allocation)
  private var _reflectionRegion: TextureRegion = _
  private var _reflectionSourceRegion: TextureRegion = _
  // Cached background type ID (avoids per-frame string matching in 3 places)
  private var _bgType: Byte = 0 // 0=sky, 1=cityscape, 2=space, 3=desert, 4=ocean

  // Deferred kill feed entries (batch backgrounds in shapes, then text in sprites)
  private val MAX_FEED_ENTRIES = 10
  private val _feedX = new Array[Float](MAX_FEED_ENTRIES)
  private val _feedY = new Array[Float](MAX_FEED_ENTRIES)
  private val _feedW = new Array[Float](MAX_FEED_ENTRIES)
  private val _feedA = new Array[Float](MAX_FEED_ENTRIES)
  private val _feedText = new Array[String](MAX_FEED_ENTRIES)
  private val _feedR = new Array[Float](MAX_FEED_ENTRIES)
  private val _feedG = new Array[Float](MAX_FEED_ENTRIES)
  private val _feedB = new Array[Float](MAX_FEED_ENTRIES)
  private var _feedCount = 0

  // Pre-allocated arrays for item shapes
  private val _starXs = new Array[Float](10)
  private val _starYs = new Array[Float](10)
  // Pre-allocated arrays for cooldown sweep polygon
  private val _sweepXs = new Array[Float](34)
  private val _sweepYs = new Array[Float](34)
  // Pre-allocated arrays for item shape highlight
  private val _hlXs = new Array[Float](10)
  private val _hlYs = new Array[Float](10)
  // Pre-allocated arrays for gem/shield/default item shapes (avoids per-item allocation)
  private val _gemXs = new Array[Float](6)
  private val _gemYs = new Array[Float](6)
  private val _gemHlXs = new Array[Float](4)
  private val _gemHlYs = new Array[Float](4)
  private val _shieldXs = new Array[Float](7)
  private val _shieldYs = new Array[Float](7)
  private val _shieldHlXs = new Array[Float](4)
  private val _shieldHlYs = new Array[Float](4)
  private val _defItemXs = new Array[Float](4)
  private val _defItemYs = new Array[Float](4)
  // Pre-allocated arrays for star triangle decomposition
  private val _starTriXs = new Array[Float](3)
  private val _starTriYs = new Array[Float](3)
  private val _starPentXs = new Array[Float](5)
  private val _starPentYs = new Array[Float](5)
  // Pre-allocated arrays for dune/wave background layers (avoids per-call allocation)
  private val _bgPolyXs = new Array[Float](63) // points=60 → 60+3=63
  private val _bgPolyYs = new Array[Float](63)

  // Deferred health bars + names (batched after entity dispatch to reduce batch switches)
  private val MAX_DEFERRED_BARS = 32
  private val _deferBarCX = new Array[Double](MAX_DEFERRED_BARS) // screenCenterX
  private val _deferBarTY = new Array[Double](MAX_DEFERRED_BARS) // spriteTopY
  private val _deferBarHP = new Array[Int](MAX_DEFERRED_BARS)    // health
  private val _deferBarMax = new Array[Int](MAX_DEFERRED_BARS)   // maxHealth
  private val _deferBarTeam = new Array[Byte](MAX_DEFERRED_BARS) // teamId
  private val _deferBarId = new Array[UUID](MAX_DEFERRED_BARS)   // playerId
  private val _deferBarName = new Array[String](MAX_DEFERRED_BARS) // charName
  private var _deferBarCount = 0

  private def deferHealthBar(cx: Double, topY: Double, hp: Int, maxHp: Int, team: Byte, pid: UUID, name: String): Unit = {
    if (_deferBarCount >= MAX_DEFERRED_BARS) return
    val i = _deferBarCount
    _deferBarCX(i) = cx
    _deferBarTY(i) = topY
    _deferBarHP(i) = hp
    _deferBarMax(i) = maxHp
    _deferBarTeam(i) = team
    _deferBarId(i) = pid
    _deferBarName(i) = name
    _deferBarCount += 1
  }

  private def flushDeferredBars(): Unit = {
    if (_deferBarCount == 0) return
    // Draw all health bar shapes in one shapes batch
    var i = 0
    while (i < _deferBarCount) {
      drawHealthBar(_deferBarCX(i), _deferBarTY(i), _deferBarHP(i), _deferBarMax(i), _deferBarTeam(i), _deferBarId(i))
      i += 1
    }
    // Draw all character names in one sprites batch
    beginSprites()
    i = 0
    while (i < _deferBarCount) {
      drawCharacterNameDirect(_deferBarName(i), _deferBarCX(i), _deferBarTY(i))
      i += 1
    }
    _deferBarCount = 0
  }

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
    damageNumbers.setFont(fontMedium)
    initialized = true
  }

  def render(deltaSec: Double, fbWidth: Int, fbHeight: Int, windowWidth: Int, windowHeight: Int): Unit = {
    ensureInitialized(fbWidth, fbHeight)
    animationTick += 1
    _frameTimeMs = System.currentTimeMillis()
    _animTickF = animationTick.toFloat
    val dt = deltaSec.toFloat

    // Update particle systems
    weatherParticles.update(dt)
    combatParticles.update(dt)

    val zoom = Constants.CAMERA_ZOOM
    canvasW = windowWidth / zoom
    canvasH = windowHeight / zoom

    val localDeathTime = client.getLocalDeathTime
    val localDeathAnimActive = client.getIsDead && localDeathTime > 0 &&
      (_frameTimeMs - localDeathTime) < DEATH_ANIMATION_MS

    // In non-lobby mode, show full game over screen when dead (after death anim)
    if (client.getIsDead && !localDeathAnimActive && !client.isRespawning) {
      renderGameOverScreen(fbWidth, fbHeight)
      return
    }

    val localPos = client.getLocalPosition
    val world = client.getWorld

    // Use interpolated position for smooth camera tracking between grid tiles
    client.updateVisualPosition()
    val visualPosX = client.visualPosX
    val visualPosY = client.visualPosY

    // Update camera with smooth interpolation
    camera.update(visualPosX, visualPosY, deltaSec, canvasW, canvasH)
    camOffX = camera.camOffX
    camOffY = camera.camOffY

    // Post-processing: render to FBO (only resize when dimensions actually change)
    if (fbWidth != lastFbWidth || fbHeight != lastFbHeight) {
      postProcessor.resize(fbWidth, fbHeight)
      lightSystem.resize(fbWidth, fbHeight)
      lastFbWidth = fbWidth
      lastFbHeight = fbHeight
    }

    // Dynamic lighting: clear lights and set ambient for current background (only recompute on change)
    lightSystem.clear()
    val bg = world.background
    if (bg ne _cachedBackground) {
      _cachedBackground = bg
      _bgType = bg match {
        case "sky" => 0; case "cityscape" => 1; case "space" => 2; case "desert" => 3; case "ocean" => 4; case _ => 0
      }
      lightSystem.setAmbientForBackground(bg)
    }

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

    // === Calculate visible tile bounds (optimized: share rx/ry intermediates) ===
    val invHW = 1.0 / HW; val invHH = 1.0 / HH
    // Corner 0: (0, 0)
    val rx0 = -camOffX; val ry0 = -camOffY
    val h0 = rx0 * invHW; val v0 = ry0 * invHH
    val cx0 = (h0 + v0) * 0.5; val cy0 = (v0 - h0) * 0.5
    // Corner 1: (canvasW, 0)
    val rx1 = canvasW - camOffX
    val h1 = rx1 * invHW
    val cx1 = (h1 + v0) * 0.5; val cy1 = (v0 - h1) * 0.5
    // Corner 2: (0, canvasH)
    val ry2 = canvasH - camOffY
    val v2 = ry2 * invHH
    val cx2 = (h0 + v2) * 0.5; val cy2 = (v2 - h0) * 0.5
    // Corner 3: (canvasW, canvasH)
    val cx3 = (h1 + v2) * 0.5; val cy3 = (v2 - h1) * 0.5
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
    _specialTileCount = 0
    _deferBarCount = 0
    val localVX = camera.visualX
    val localVY = camera.visualY
    val entitiesByCell = entityCollector.collect(
      client, deltaSec,
      localVX, localVY, localDeathAnimActive,
      startX, endX, startY, endY
    )

    // === Phase 1: Ground tiles ===
    // Use fixed position-based variant (no tileFrame) so ground doesn't animate
    val numTileFrames = GLTileRenderer.getNumFrames
    beginSprites()
    var wy = startY
    while (wy <= endY) {
      var wx = startX
      while (wx <= endX) {
        val tile = world.getTile(wx, wy)
        if (tile.walkable) {
          val tid = tile.id
          val sx = worldToScreenX(wx, wy).toFloat
          val sy = worldToScreenY(wx, wy).toFloat
          val variantFrame = ((wx * 7 + wy * 13) & 0x7FFFFFFF) % numTileFrames
          val region = GLTileRenderer.getTileRegion(tid, variantFrame)
          if (region != null) {
            spriteBatch.draw(region, sx - HW, sy - (cellH - HH), tileW, tileCellH)
          }
          // Collect special tiles for overlay pass
          if (tid == 1 || tid == 7 || tid == 9 || tid == 10 || tid == 15 || tid == 18 || tid == 24) {
            if (_specialTileCount < MAX_SPECIAL_TILES) {
              _specialTileWX(_specialTileCount) = wx
              _specialTileWY(_specialTileCount) = wy
              _specialTileTid(_specialTileCount) = tid
              _specialTileCount += 1
            }
          }
          // Lava and energy field tiles emit light (reuse sx/sy computed above)
          if (tid == 10) { // Lava
            val lavaPulse = (0.8 + 0.2 * Math.sin(animationTick * 0.06 + wx * 1.3 + wy * 0.7)).toFloat
            lightSystem.addLight(sx, sy, 40f, 1f, 0.5f, 0.1f, 0.12f * lavaPulse)
          } else if (tid == 15) { // EnergyField
            lightSystem.addLight(sx, sy, 35f, 0.5f, 0.2f, 0.8f, 0.08f)
          }
        }
        wx += 1
      }
      wy += 1
    }

    // === Animated tile overlays ===
    if (_specialTileCount > 0) {
      drawTileOverlays()
    }

    // === Water reflections ===
    drawWaterReflections()

    // === Aim arrow ===
    drawAimArrow()

    // === Elevated tile edge shadows (batched in one shapes pass — more efficient than per-tile) ===
    beginShapes()
    wy = startY
    while (wy <= endY) {
      var wx = startX
      while (wx <= endX) {
        if (!world.getTile(wx, wy).walkable) {
          drawElevatedTileShadow(wx, wy, world)
        }
        wx += 1
      }
      wy += 1
    }

    // === Phase 2: Elevated tiles + entities interleaved by depth ===
    wy = startY
    while (wy <= endY) {
      var wx = startX
      while (wx <= endX) {
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
        val cellKey = entityCollector.cellKey(wx, wy)
        val cellEntries = entitiesByCell.getOrElse(cellKey, null)
        if (cellEntries != null) {
          entitiesByCell -= cellKey  // -= avoids Option allocation from .remove()
          dispatchEntries(cellEntries, localVX, localVY)
        }
        wx += 1
      }
      wy += 1
    }
    // Any entities outside visible range
    val remainIter = entitiesByCell.valuesIterator
    while (remainIter.hasNext) dispatchEntries(remainIter.next(), localVX, localVY)

    // === Deferred health bars + names (batched to reduce batch switches) ===
    flushDeferredBars()

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
        _worldToScreenXFn, _worldToScreenYFn)
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
      val elapsed = _frameTimeMs - localHitTime
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

    postProcessor.animationTime = _animTickF
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
    (_bgType: @scala.annotation.switch) match {
      case 0 => drawSkyBg()
      case 1 => drawCityscapeBg()
      case 2 => drawSpaceBg()
      case 3 => drawDesertBg()
      case 4 => drawOceanBg()
      case _ => drawSkyBg()
    }
  }

  private def drawSkyBg(): Unit = {
    val w = canvasW.toFloat; val h = canvasH.toFloat
    val bands = 12
    var i = 0
    while (i < bands) {
      val t = i.toFloat / bands
      val r = 0.35f + t * 0.15f
      val g = 0.55f + t * 0.15f
      val b = 0.92f - t * 0.15f
      val y0 = h * t
      val bandH = h / bands + 1
      shapeBatch.fillRect(0, y0, w, bandH, r, g, b, 1f)
      i += 1
    }
    // Clouds
    val px = (camOffX * 0.03).toFloat
    val py = (camOffY * 0.02).toFloat
    drawCloudLayer(w, px, py, 0.10f, 0.15f, 1.6f, 0.18f, 6)
    drawCloudLayer(w, px, py, 0.20f, 0.3f, 1.2f, 0.25f, 8)
    drawCloudLayer(w, px, py, 0.05f, 0.5f, 1.0f, 0.35f, 7)
  }

  private def drawCloudLayer(w: Float, px: Float, py: Float, yFrac: Float, speed: Float, scale: Float, alpha: Float, count: Int): Unit = {
    val h = canvasH.toFloat
    var i = 0
    while (i < count) {
      val seed = java.lang.Float.floatToIntBits(yFrac) + i * 73
      val baseX = hash(seed).toFloat * w * 1.5f - w * 0.25f
      val baseY = h * yFrac + hash(seed + 1).toFloat * h * 0.25f
      val drift = ((animationTick * speed + px * (1f + yFrac)) % (w + 300)) - 150
      val cx = ((baseX + drift) % (w + 300)) - 150
      val cy = baseY + py * (0.5f + yFrac) + Math.sin(animationTick * 0.01 + i).toFloat * 3
      drawCloud(cx, cy, scale, alpha)
      i += 1
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
    var i = 0
    while (i < 12) {
      val t = i.toFloat / 12
      shapeBatch.fillRect(0, h * t, w, h / 12 + 1, 0.05f + t * 0.08f, 0.02f + t * 0.05f, 0.15f - t * 0.05f, 1f)
      i += 1
    }
    // Horizon glow
    shapeBatch.fillRect(0, h * 0.55f, w, h * 0.15f, 0.2f, 0.08f, 0.3f, 0.3f)
    shapeBatch.fillRect(0, h * 0.5f, w, h * 0.1f, 0.3f, 0.1f, 0.4f, 0.15f)

    val px = (camOffX * 0.02).toFloat
    // Buildings
    i = 0
    while (i < 30) {
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
            var wr = 0f; var wg = 0f; var wb = 0f
            if (warmth < 0.6) { wr = 1f; wg = 0.85f; wb = 0.3f }
            else if (warmth < 0.85) { wr = 0.8f; wg = 0.85f; wb = 1f }
            else if (hash(windowSeed + 200) < 0.5) { wr = 0f; wg = 0.9f; wb = 1f }
            else { wr = 1f; wg = 0.2f; wb = 0.8f }
            shapeBatch.fillRect(wx, wy, 3f, 4f, wr, wg, wb, 0.7f * flicker)
          }
          wx += 7
          windowIdx += 1
        }
        wy += 8
      }
      i += 1
    }
    // Near buildings
    i = 0
    while (i < 10) {
      val bx = ((hash(i * 13 + 200).toFloat * (w + 300) - 150 + px) % (w + 300)) - 150
      val bw = 50 + hash(i * 13 + 201).toFloat * 60
      val bh = h * (0.08f + hash(i * 13 + 202).toFloat * 0.15f)
      shapeBatch.fillRect(bx, h - bh, bw, bh, 0.03f, 0.02f, 0.06f, 0.95f)
      i += 1
    }
  }

  private def drawSpaceBg(): Unit = {
    val w = canvasW.toFloat; val h = canvasH.toFloat
    var i = 0
    while (i < 8) {
      val t = i.toFloat / 8
      val b = 0.02f + t * 0.04f
      shapeBatch.fillRect(0, h * t, w, h / 8 + 1, b * 0.3f, b * 0.2f, b, 1f)
      i += 1
    }
    val px = (camOffX * 0.01).toFloat; val py = (camOffY * 0.01).toFloat

    // Nebulae
    drawNebula(w * 0.3f + px * 2, h * 0.35f + py * 2, w * 0.35f, h * 0.3f, 0.4f, 0.1f, 0.6f,
      (0.06 + 0.02 * Math.sin(animationTick * 0.008)).toFloat)
    drawNebula(w * 0.75f + px * 1.5f, h * 0.6f + py * 1.5f, w * 0.25f, h * 0.2f, 0.1f, 0.3f, 0.5f,
      (0.04 + 0.015 * Math.sin(animationTick * 0.01 + 2)).toFloat)

    // Stars with additive blending for glow
    shapeBatch.setAdditiveBlend(true)
    i = 0
    while (i < 120) {
      val sx = ((hash(i * 3).toFloat * w + px * (0.5f + hash(i * 3 + 10).toFloat * 2)) % w)
      val sy = ((hash(i * 3 + 1).toFloat * h + py * (0.5f + hash(i * 3 + 11).toFloat * 2)) % h)
      val baseBrightness = 0.3f + hash(i * 3 + 2).toFloat * 0.7f
      val twinkleSpeed = 0.02f + hash(i * 3 + 5).toFloat * 0.04f
      val twinklePhase = hash(i * 3 + 6).toFloat * Math.PI.toFloat * 2
      val twinkle = (0.5 + 0.5 * Math.sin(animationTick * twinkleSpeed + twinklePhase)).toFloat
      val brightness = baseBrightness * (0.4f + 0.6f * twinkle)
      val colorSeed = hash(i * 3 + 7)
      var sr = 0f; var sg = 0f; var sb = 0f
      if (colorSeed < 0.6) { sr = 1f; sg = 1f; sb = 1f }
      else if (colorSeed < 0.8) { sr = 0.7f; sg = 0.8f; sb = 1f }
      else { sr = 1f; sg = 0.95f; sb = 0.7f }
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
      i += 1
    }
    shapeBatch.setAdditiveBlend(false)

    // Moon
    val moonX = w * 0.8f + px * 3; val moonY = h * 0.2f + py * 3; val moonR = 25f
    shapeBatch.fillOval(moonX, moonY, moonR, moonR, 0.25f, 0.22f, 0.35f, 0.6f)
    shapeBatch.fillOval(moonX + moonR * 0.4f, moonY, moonR, moonR, 0.02f, 0.01f, 0.05f, 0.7f)
  }

  private def drawNebula(cx: Float, cy: Float, rw: Float, rh: Float, nr: Float, ng: Float, nb: Float, alpha: Float): Unit = {
    shapeBatch.setAdditiveBlend(true)
    var i = 5
    while (i >= 1) {
      val t = i.toFloat / 5
      shapeBatch.fillOvalSoft(cx, cy, rw * t * 0.5f, rh * t * 0.5f, nr, ng, nb, alpha * t * 0.6f, 0f, 16)
      i -= 1
    }
    shapeBatch.setAdditiveBlend(false)
  }

  private def drawDesertBg(): Unit = {
    val w = canvasW.toFloat; val h = canvasH.toFloat
    var i = 0
    while (i < 12) {
      val t = i.toFloat / 12
      val r = clamp(0.95f - t * 0.25f); val g = clamp(0.65f - t * 0.20f); val b = clamp(0.25f - t * 0.10f)
      shapeBatch.fillRect(0, h * t, w, h / 12 + 1, r, g, b, 1f)
      i += 1
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
    i = 0
    while (i < 15) {
      val ly = h * 0.45f + i * h * 0.035f
      val phase = animationTick * 0.03f + i * 0.7f
      val points = 30
      val xStep = w / points
      var j = 0
      while (j < points) {
        val x1 = j * xStep; val x2 = (j + 1) * xStep
        val y1 = ly + Math.sin(phase + j * 0.3).toFloat * 2.5f
        val y2 = ly + Math.sin(phase + (j + 1) * 0.3).toFloat * 2.5f
        shapeBatch.strokeLine(x1, y1, x2, y2, 1.5f, 1f, 0.9f, 0.6f, 0.06f)
        j += 1
      }
      i += 1
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
    _bgPolyXs(0) = -20; _bgPolyYs(0) = h + 10
    var i = 0
    while (i <= points) {
      _bgPolyXs(i + 1) = -20 + i * xStep + parallax
      _bgPolyYs(i + 1) = (baseY +
        Math.sin(i * 0.15 + seedOff * 0.1) * h * 0.04 +
        Math.sin(i * 0.07 + seedOff * 0.3 + animationTick * 0.002) * h * 0.02 +
        Math.sin(i * 0.3 + seedOff * 0.5) * h * 0.015).toFloat
      i += 1
    }
    _bgPolyXs(points + 2) = w + 20; _bgPolyYs(points + 2) = h + 10
    shapeBatch.fillPolygon(_bgPolyXs, _bgPolyYs, points + 3, r, g, b, alpha)
  }

  private def drawOceanBg(): Unit = {
    val w = canvasW.toFloat; val h = canvasH.toFloat
    var i = 0
    while (i < 12) {
      val t = i.toFloat / 12
      shapeBatch.fillRect(0, h * t, w, h / 12 + 1, 0.02f + t * 0.04f, 0.08f + t * 0.12f, 0.25f + t * 0.15f, 1f)
      i += 1
    }
    val px = (camOffX * 0.02).toFloat
    // Caustics
    shapeBatch.setAdditiveBlend(true)
    i = 0
    while (i < 18) {
      val cx = ((hash(i * 5).toFloat * w * 1.3f - w * 0.15f + px * (0.3f + hash(i * 5 + 3).toFloat)) % (w + 100)) - 50
      val cy = hash(i * 5 + 1).toFloat * h
      val rad = 30 + hash(i * 5 + 2).toFloat * 60
      val pulse = (0.5 + 0.5 * Math.sin(animationTick * 0.02 + hash(i * 5 + 4) * Math.PI * 2)).toFloat
      shapeBatch.fillOvalSoft(cx, cy, rad, rad, 0.2f, 0.6f, 0.8f, 0.03f + 0.03f * pulse, 0f, 16)
      i += 1
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
    i = 0
    while (i < 12) {
      val foamY = h * (0.2f + hash(i * 11).toFloat * 0.7f)
      val foamX = ((hash(i * 11 + 1).toFloat * w * 1.5f - w * 0.25f + animationTick * (0.2f + hash(i * 11 + 2).toFloat * 0.3f) + px) % (w + 200)) - 100
      val foamW = 20 + hash(i * 11 + 3).toFloat * 50
      val foamH = 2 + hash(i * 11 + 4).toFloat * 3
      val pulse = (0.5 + 0.5 * Math.sin(animationTick * 0.025 + hash(i * 11 + 5) * 10)).toFloat
      shapeBatch.fillOval(foamX + foamW / 2, foamY + foamH / 2, foamW / 2, foamH / 2, 0.6f, 0.8f, 0.9f, 0.08f * pulse)
      i += 1
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
    _bgPolyXs(0) = -20; _bgPolyYs(0) = h + 10
    var i = 0
    while (i <= points) {
      _bgPolyXs(i + 1) = -20 + i * xStep + parallax * (0.3f + yFrac)
      _bgPolyYs(i + 1) = (baseY +
        Math.sin(i * 0.12 + seedOff * 0.1 + animationTick * speed) * amplitude +
        Math.sin(i * 0.25 + seedOff * 0.3 + animationTick * speed * 1.3) * amplitude * 0.5 +
        Math.sin(i * 0.06 + seedOff * 0.7 + animationTick * speed * 0.7) * amplitude * 1.5).toFloat
      i += 1
    }
    _bgPolyXs(points + 2) = w + 20; _bgPolyYs(points + 2) = h + 10
    shapeBatch.fillPolygon(_bgPolyXs, _bgPolyYs, n, r, g, b, alpha)
  }

  // ═══════════════════════════════════════════════════════════════════
  //  ENTITY DISPATCH (depth-sorted)
  // ═══════════════════════════════════════════════════════════════════

  private def dispatchEntries(entries: mutable.ArrayBuffer[CellEntry], localVX: Double, localVY: Double): Unit = {
    var i = 0
    while (i < entries.size) {
      entries(i) match {
        case ItemEntry(item) => drawSingleItem(item)
        case ProjectileEntry(proj) => drawSingleProjectile(proj)
        case PlayerEntry(player, vx, vy) => drawPlayerInterp(player, vx, vy)
        case LocalPlayerEntry => drawLocalPlayer(localVX, localVY)
        case LocalDeathEntry => // handled by drawDeathAnimations()
      }
      i += 1
    }
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
    val now = _frameTimeMs
    val playerId = player.getId
    val pos = player.getPosition
    val lastPos = lastRemotePositions.get(playerId)
    if (lastPos == null || lastPos.getX != pos.getX || lastPos.getY != pos.getY) {
      remoteMovingUntil.put(playerId, now + 200)
    }
    lastRemotePositions.put(playerId, pos)
    val movUntil = remoteMovingUntil.get(playerId)
    val isMoving = movUntil != null && now < movUntil.longValue()

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
      val el = _frameTimeMs - hitTime
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

    // Health bar + name (deferred to batch pass)
    val charName = CharacterDef.get(player.getCharacterId).displayName
    deferHealthBar(screenX, screenY - displaySz, player.getHealth, player.getMaxHealth, player.getTeamId, player.getId, charName)
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
      val el = _frameTimeMs - localHitTime
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

    // Health bar + name (deferred to batch pass)
    val charName = client.getSelectedCharacterDef.displayName
    deferHealthBar(screenX, screenY - displaySz, client.getLocalHealth, client.getSelectedCharacterMaxHealth, client.localTeamId, client.getLocalPlayerId, charName)
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
    var i = 0
    while (i < 5) {
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
      i += 1
    }
  }

  // ═══════════════════════════════════════════════════════════════════
  //  STATUS EFFECTS (5f)
  // ═══════════════════════════════════════════════════════════════════

  private def drawShieldBubble(cx: Double, cy: Double): Unit = {
    beginShapes()
    val pulse = (0.7 + 0.3 * Math.sin(animationTick * 0.1)).toFloat
    shapeBatch.fillOvalSoft(cx.toFloat, cy.toFloat, 22f, 18f, 0.3f, 0.6f, 1f, 0.05f * pulse, 0.15f * pulse, 24)
    // Use strokeOval which leverages pre-computed sin/cos LUTs (avoids 48 Math.sin/cos calls)
    shapeBatch.strokeOval(cx.toFloat, cy.toFloat, 22f, 18f, 1.5f, 0.4f, 0.7f, 1f, 0.25f * pulse, 24)
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
    var i = 0
    while (i < 6) {
      val angle = (i * Math.PI / 3 + animationTick * 0.01).toFloat
      val dist = 12f
      val sx = cx.toFloat + dist * Math.cos(angle).toFloat
      val sy = cy.toFloat + dist * Math.sin(angle).toFloat * 0.6f
      shapeBatch.fillRect(sx - 1, sy - 3, 2f, 6f, 0.7f, 0.9f, 1f, 0.4f)
      i += 1
    }
  }

  private def drawRootedEffect(cx: Double, cy: Double): Unit = {
    beginShapes()
    var i = 0
    while (i < 4) {
      val angle = (i * Math.PI / 2 + animationTick * 0.02).toFloat
      val dist = 10f
      val sx = cx.toFloat + dist * Math.cos(angle).toFloat
      val sy = cy.toFloat + 8 + dist * Math.sin(angle).toFloat * 0.3f
      shapeBatch.strokeLine(sx, sy, sx + Math.cos(angle + 0.5f).toFloat * 5, sy - 8, 2f, 0.3f, 0.6f, 0.15f, 0.5f)
      i += 1
    }
  }

  private def drawSlowedEffect(cx: Double, cy: Double): Unit = {
    beginShapes()
    shapeBatch.fillOval(cx.toFloat, cy.toFloat, 16f, 12f, 0.4f, 0.4f, 0.8f, 0.1f, 16)
  }

  private def drawBurnEffect(cx: Double, cy: Double): Unit = {
    beginShapes()
    shapeBatch.setAdditiveBlend(true)
    var i = 0
    while (i < 5) {
      val angle = (animationTick * 0.15 + i * 1.3).toFloat
      val dist = 8f + Math.sin(animationTick * 0.1 + i).toFloat * 4
      val fx = cx.toFloat + dist * Math.cos(angle).toFloat
      val fy = cy.toFloat + dist * Math.sin(angle).toFloat * 0.5f - (animationTick * 0.5f + i * 2) % 10
      shapeBatch.fillOval(fx, fy, 3f, 4f, 1f, 0.6f, 0.1f, 0.4f, 8)
      i += 1
    }
    shapeBatch.setAdditiveBlend(false)
  }

  private def drawSpeedBoostEffect(cx: Double, cy: Double): Unit = {
    beginShapes()
    var i = 0
    while (i < 3) {
      val offset = i * 5f
      shapeBatch.strokeLine(
        cx.toFloat - 8 - offset, cy.toFloat + 4,
        cx.toFloat - 14 - offset, cy.toFloat + 4,
        1.5f, 0.2f, 0.8f, 1f, 0.3f - i * 0.08f)
      i += 1
    }
  }

  private def drawHitEffect(cx: Double, cy: Double, hitTime: Long): Unit = {
    if (hitTime <= 0) return
    val elapsed = _frameTimeMs - hitTime
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
    val elapsed = _frameTimeMs - castTime
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
    val now = _frameTimeMs
    val ix = item.getCellX; val iy = item.getCellY
    val centerX = worldToScreenX(ix, iy).toFloat
    val halfSize = Constants.ITEM_SIZE_PX / 2f

    // Track for pickup detection
    drawnItemIdsThisFrame.add(item.id)
    val existingPos = itemLastWorldPos.get(item.id)
    if (existingPos != null) {
      existingPos(0) = ix; existingPos(1) = iy; existingPos(2) = item.colorRGB
    } else {
      itemLastWorldPos.put(item.id, Array(ix, iy, item.colorRGB))
    }

    // Spawn pop animation (bounce ease over 400ms)
    val existingSpawn = itemSpawnTimes.get(item.id)
    val spawnTime = if (existingSpawn != null) existingSpawn.longValue() else { itemSpawnTimes.put(item.id, now); now }
    val spawnElapsed = (now - spawnTime).toFloat
    val spawnScale = if (spawnElapsed < 400f) {
      val t = spawnElapsed / 400f
      if (t < 0.5f) 1f + 0.4f * Math.sin(t * Math.PI).toFloat
      else 1f + 0.15f * Math.sin(t * Math.PI).toFloat * (1f - t)
    } else 1f

    val bobPhase = animationTick * 0.06f + item.id * 1.7f
    val bobOffset = Math.sin(bobPhase).toFloat * 4f

    // Item-specific scale pulses
    val itemPulse = item.itemType match {
      case ItemType.Heart => 1f + 0.06f * Math.max(0.0, Math.sin(bobPhase * 3.5)).toFloat
      case ItemType.Star  => 1f + 0.03f * Math.sin(bobPhase * 2.0).toFloat
      case _              => 1f
    }
    val hs = halfSize * spawnScale * itemPulse

    val centerY = worldToScreenY(ix, iy).toFloat + bobOffset

    // Rotation angle (slow spin)
    val rotAngle = animationTick * 0.02f + item.id * 0.5f

    intToRGB(item.colorRGB)
    val ir = _rgb_r; val ig = _rgb_g; val ib = _rgb_b
    val glowPulse = (0.6 + 0.4 * Math.sin(bobPhase * 1.3)).toFloat

    // Item emits colored light — stronger radius
    lightSystem.addLight(centerX, centerY, 80f, ir, ig, ib, 0.18f * glowPulse)

    beginShapes()
    // Drop shadow on ground
    shapeBatch.fillOvalSoft(centerX, centerY + hs * 0.85f, hs * 1.2f, hs * 0.3f, 0f, 0f, 0f, 0.2f, 0f, 12)

    // Ground glow (additive) — larger and more visible
    shapeBatch.setAdditiveBlend(true)
    shapeBatch.fillOvalSoft(centerX, centerY + hs * 0.3f, hs * 2.5f, hs * 0.7f, ir, ig, ib, 0.22f * glowPulse, 0f, 14)
    shapeBatch.fillOvalSoft(centerX, centerY, hs * 2.8f, hs * 2f, ir, ig, ib, 0.1f * glowPulse, 0f, 14)
    shapeBatch.setAdditiveBlend(false)

    // Main item shape with highlights and outline
    drawItemShape(item.itemType, centerX, centerY, hs, ir, ig, ib, rotAngle)

    // Sparkle particles (additive)
    shapeBatch.setAdditiveBlend(true)
    var i = 0
    while (i < 3) {
      val sparkAngle = bobPhase * 0.8f + i * (2 * Math.PI / 3).toFloat
      val sparkDist = hs * 1.1f
      val sx = centerX + sparkDist * Math.cos(sparkAngle).toFloat
      val sy = centerY + sparkDist * Math.sin(sparkAngle).toFloat * 0.6f
      val sparkAlpha = (0.3 + 0.4 * Math.sin(bobPhase * 2.5 + i * 2.1)).toFloat
      val sparkSize = (1.5 + Math.sin(bobPhase * 3.0 + i) * 0.7).toFloat
      shapeBatch.fillOval(sx, sy, sparkSize, sparkSize, 1f, 1f, 1f, clamp(sparkAlpha))
      i += 1
    }
    shapeBatch.setAdditiveBlend(false)
  }

  /** Fill a 10-vertex star polygon (concave) by decomposing into 5 point triangles + center pentagon. */
  private def fillStarPolygon(xs: Array[Float], ys: Array[Float], r: Float, g: Float, b: Float, a: Float): Unit = {
    // 5 triangles for the star points: each outer vertex + its two adjacent inner vertices
    var k = 0
    while (k < 5) {
      val outer = k * 2
      val innerPrev = (k * 2 + 9) % 10
      val innerNext = (k * 2 + 1) % 10
      _starTriXs(0) = xs(outer);     _starTriYs(0) = ys(outer)
      _starTriXs(1) = xs(innerPrev); _starTriYs(1) = ys(innerPrev)
      _starTriXs(2) = xs(innerNext); _starTriYs(2) = ys(innerNext)
      shapeBatch.fillPolygon(_starTriXs, _starTriYs, 3, r, g, b, a)
      k += 1
    }
    // Center pentagon from the 5 inner vertices (convex, fan triangulation works)
    _starPentXs(0) = xs(1); _starPentYs(0) = ys(1)
    _starPentXs(1) = xs(3); _starPentYs(1) = ys(3)
    _starPentXs(2) = xs(5); _starPentYs(2) = ys(5)
    _starPentXs(3) = xs(7); _starPentYs(3) = ys(7)
    _starPentXs(4) = xs(9); _starPentYs(4) = ys(9)
    shapeBatch.fillPolygon(_starPentXs, _starPentYs, 5, r, g, b, a)
  }

  private def drawItemShape(itemType: ItemType, cx: Float, cy: Float, hs: Float, r: Float, g: Float, b: Float, rotation: Float = 0f): Unit = {
    val cos = Math.cos(rotation).toFloat
    val sin = Math.sin(rotation).toFloat
    // Highlight and shadow colors for 3D depth
    val hr = clamp(r * 0.4f + 0.6f); val hg = clamp(g * 0.4f + 0.6f); val hb = clamp(b * 0.4f + 0.6f)
    val dr = r * 0.6f; val dg = g * 0.6f; val db = b * 0.6f

    itemType match {
      case ItemType.Heart =>
        val rr = hs * 0.6f
        // Dark outline layer (slightly larger)
        val or = rr + 1.5f
        shapeBatch.fillOval(cx - hs * 0.48f, cy - hs * 0.45f, or, or, dr * 0.7f, dg * 0.7f, db * 0.7f, 1f, 14)
        shapeBatch.fillOval(cx + hs * 0.48f, cy - hs * 0.45f, or, or, dr * 0.7f, dg * 0.7f, db * 0.7f, 1f, 14)
        _heartTriXs(0) = cx - hs * 1.08f; _heartTriXs(1) = cx; _heartTriXs(2) = cx + hs * 1.08f
        _heartTriYs(0) = cy - hs * 0.08f; _heartTriYs(1) = cy + hs * 1.05f; _heartTriYs(2) = cy - hs * 0.08f
        shapeBatch.fillPolygon(_heartTriXs, _heartTriYs, 3, dr * 0.7f, dg * 0.7f, db * 0.7f, 1f)
        // Main fill
        shapeBatch.fillOval(cx - hs * 0.48f, cy - hs * 0.45f, rr, rr, r, g, b, 1f, 14)
        shapeBatch.fillOval(cx + hs * 0.48f, cy - hs * 0.45f, rr, rr, r, g, b, 1f, 14)
        _heartTriXs(0) = cx - hs * 1.02f; _heartTriXs(1) = cx; _heartTriXs(2) = cx + hs * 1.02f
        _heartTriYs(0) = cy - hs * 0.1f; _heartTriYs(1) = cy + hs; _heartTriYs(2) = cy - hs * 0.1f
        shapeBatch.fillPolygon(_heartTriXs, _heartTriYs, 3, r, g, b, 1f)
        // Upper highlight band across both lobes
        shapeBatch.fillOval(cx - hs * 0.48f, cy - hs * 0.52f, rr * 0.7f, rr * 0.5f, hr, hg, hb, 0.5f, 10)
        shapeBatch.fillOval(cx + hs * 0.42f, cy - hs * 0.52f, rr * 0.5f, rr * 0.4f, hr, hg, hb, 0.35f, 10)
        // Specular highlight (white dot on left lobe)
        shapeBatch.fillOval(cx - hs * 0.5f, cy - hs * 0.58f, rr * 0.22f, rr * 0.18f, 1f, 1f, 1f, 0.6f, 8)
        // Lower shadow for depth
        shapeBatch.fillOvalSoft(cx, cy + hs * 0.5f, hs * 0.7f, hs * 0.3f, dr * 0.4f, dg * 0.4f, db * 0.4f, 0.3f, 0f, 8)

      case ItemType.Star =>
        val outerR = hs; val innerR = hs * 0.35f
        // Dark outline star (slightly larger)
        var i = 0
        while (i < 10) {
          val angle = Math.PI / 2 + i * Math.PI / 5 + rotation
          val rad = if (i % 2 == 0) outerR + 1.5f else innerR + 0.5f
          _hlXs(i) = cx + (rad * Math.cos(angle)).toFloat
          _hlYs(i) = cy - (rad * Math.sin(angle)).toFloat
          i += 1
        }
        fillStarPolygon(_hlXs, _hlYs, dr * 0.7f, dg * 0.7f, db * 0.7f, 1f)
        // Main star
        i = 0
        while (i < 10) {
          val angle = Math.PI / 2 + i * Math.PI / 5 + rotation
          val rad = if (i % 2 == 0) outerR else innerR
          _starXs(i) = cx + (rad * Math.cos(angle)).toFloat
          _starYs(i) = cy - (rad * Math.sin(angle)).toFloat
          i += 1
        }
        fillStarPolygon(_starXs, _starYs, r, g, b, 1f)
        // Inner bright star (50% size, lighter)
        i = 0
        while (i < 10) {
          val angle = Math.PI / 2 + i * Math.PI / 5 + rotation
          val rad = if (i % 2 == 0) outerR * 0.5f else innerR * 0.7f
          _hlXs(i) = cx + (rad * Math.cos(angle)).toFloat
          _hlYs(i) = cy - (rad * Math.sin(angle)).toFloat
          i += 1
        }
        fillStarPolygon(_hlXs, _hlYs, hr, hg, hb, 0.45f)
        // Bright center core
        shapeBatch.fillOvalSoft(cx, cy, hs * 0.4f, hs * 0.4f, 1f, 1f, 0.9f, 0.45f, 0f, 10)
        shapeBatch.fillOval(cx, cy, hs * 0.15f, hs * 0.15f, 1f, 1f, 1f, 0.65f, 8)

      case ItemType.Gem =>
        // Hexagonal gem with 6 distinct facets
        val n = 6
        var i = 0
        while (i < n) {
          val angle = rotation + i * Math.PI / 3.0
          _gemXs(i) = cx + (hs * Math.cos(angle)).toFloat
          _gemYs(i) = cy + (hs * Math.sin(angle)).toFloat
          i += 1
        }
        // Dark outline
        shapeBatch.strokePolygon(_gemXs, _gemYs, n, 2f, dr * 0.5f, dg * 0.5f, db * 0.5f, 0.8f)
        // Draw 6 triangular facets with alternating brightness
        i = 0
        while (i < n) {
          val ni = (i + 1) % n
          _gemHlXs(0) = cx;        _gemHlYs(0) = cy
          _gemHlXs(1) = _gemXs(i); _gemHlYs(1) = _gemYs(i)
          _gemHlXs(2) = _gemXs(ni); _gemHlYs(2) = _gemYs(ni)
          val facetBright = if (i % 2 == 0) 1.0f else 0.72f
          shapeBatch.fillPolygon(_gemHlXs, _gemHlYs, 3, clamp(r * facetBright), clamp(g * facetBright), clamp(b * facetBright), 1f)
          // Facet edge line from vertex to center
          shapeBatch.strokeLine(_gemXs(i), _gemYs(i), cx, cy, 0.5f, dr, dg, db, 0.25f)
          i += 1
        }
        // Top-left highlight for glassy look
        shapeBatch.fillOvalSoft(cx - hs * 0.2f, cy - hs * 0.3f, hs * 0.4f, hs * 0.35f, 1f, 1f, 1f, 0.3f, 0f, 8)
        // Center bright point
        shapeBatch.fillOval(cx, cy, hs * 0.12f, hs * 0.12f, 1f, 1f, 1f, 0.5f, 8)
        // Final outline
        shapeBatch.strokePolygon(_gemXs, _gemYs, n, 1f, dr, dg, db, 0.7f)

      case ItemType.Shield =>
        // Dark outline (slightly larger shape)
        _shieldXs(0) = cx - hs * 0.88f; _shieldYs(0) = cy - hs * 0.6f
        _shieldXs(1) = cx - hs * 0.52f; _shieldYs(1) = cy - hs * 1.03f
        _shieldXs(2) = cx + hs * 0.52f; _shieldYs(2) = cy - hs * 1.03f
        _shieldXs(3) = cx + hs * 0.88f; _shieldYs(3) = cy - hs * 0.6f
        _shieldXs(4) = cx + hs * 0.72f; _shieldYs(4) = cy + hs * 0.42f
        _shieldXs(5) = cx;              _shieldYs(5) = cy + hs * 1.03f
        _shieldXs(6) = cx - hs * 0.72f; _shieldYs(6) = cy + hs * 0.42f
        shapeBatch.fillPolygon(_shieldXs, _shieldYs, 7, dr * 0.6f, dg * 0.6f, db * 0.6f, 1f)
        // Main shield fill
        _shieldXs(0) = cx - hs * 0.85f; _shieldYs(0) = cy - hs * 0.6f
        _shieldXs(1) = cx - hs * 0.5f;  _shieldYs(1) = cy - hs
        _shieldXs(2) = cx + hs * 0.5f;  _shieldYs(2) = cy - hs
        _shieldXs(3) = cx + hs * 0.85f; _shieldYs(3) = cy - hs * 0.6f
        _shieldXs(4) = cx + hs * 0.7f;  _shieldYs(4) = cy + hs * 0.4f
        _shieldXs(5) = cx;              _shieldYs(5) = cy + hs
        _shieldXs(6) = cx - hs * 0.7f;  _shieldYs(6) = cy + hs * 0.4f
        shapeBatch.fillPolygon(_shieldXs, _shieldYs, 7, r, g, b, 1f)
        // Upper gradient highlight
        _shieldHlXs(0) = cx - hs * 0.45f; _shieldHlYs(0) = cy - hs * 0.55f
        _shieldHlXs(1) = cx - hs * 0.35f; _shieldHlYs(1) = cy - hs * 0.85f
        _shieldHlXs(2) = cx + hs * 0.35f; _shieldHlYs(2) = cy - hs * 0.85f
        _shieldHlXs(3) = cx + hs * 0.45f; _shieldHlYs(3) = cy - hs * 0.55f
        shapeBatch.fillPolygon(_shieldHlXs, _shieldHlYs, 4, hr, hg, hb, 0.35f)
        // Cross emblem in center
        val cw = hs * 0.12f; val ch = hs * 0.6f
        shapeBatch.fillRect(cx - cw, cy - ch * 0.55f, cw * 2, ch, hr, hg, hb, 0.3f)
        shapeBatch.fillRect(cx - ch * 0.35f, cy - cw * 0.8f, ch * 0.7f, cw * 1.6f, hr, hg, hb, 0.3f)
        // Metallic top-edge highlight
        shapeBatch.strokeLine(cx - hs * 0.48f, cy - hs * 0.98f, cx + hs * 0.48f, cy - hs * 0.98f, 1.5f, 1f, 1f, 1f, 0.2f)
        // Rivets at shoulder and tip
        shapeBatch.fillOval(cx - hs * 0.85f, cy - hs * 0.6f, 2f, 2f, hr, hg, hb, 0.5f, 6)
        shapeBatch.fillOval(cx + hs * 0.85f, cy - hs * 0.6f, 2f, 2f, hr, hg, hb, 0.5f, 6)
        shapeBatch.fillOval(cx, cy + hs * 0.95f, 2f, 2f, hr, hg, hb, 0.4f, 6)
        // Outline
        shapeBatch.strokePolygon(_shieldXs, _shieldYs, 7, 1f, dr, dg, db, 0.7f)

      case ItemType.Fence =>
        val barW = hs * 0.28f
        val tipH = barW * 0.8f
        val p0x = cx - hs * 0.75f; val p1x = cx; val p2x = cx + hs * 0.75f
        val postTop = cy - hs + tipH
        val postBot = cy + hs
        val postH = postBot - postTop
        // Dark post shadows (offset right)
        shapeBatch.fillRect(p0x + 1f, postTop, barW, postH, dr * 0.5f, dg * 0.5f, db * 0.5f, 0.6f)
        shapeBatch.fillRect(p1x - barW / 2 + 1f, postTop, barW, postH, dr * 0.5f, dg * 0.5f, db * 0.5f, 0.6f)
        shapeBatch.fillRect(p2x - barW + 1f, postTop, barW, postH, dr * 0.5f, dg * 0.5f, db * 0.5f, 0.6f)
        // Main posts
        shapeBatch.fillRect(p0x, postTop, barW, postH, r, g, b, 1f)
        shapeBatch.fillRect(p1x - barW / 2, postTop, barW, postH, r, g, b, 1f)
        shapeBatch.fillRect(p2x - barW, postTop, barW, postH, r, g, b, 1f)
        // Pointed tops (triangles)
        _heartTriXs(0) = p0x; _heartTriYs(0) = postTop
        _heartTriXs(1) = p0x + barW / 2; _heartTriYs(1) = cy - hs
        _heartTriXs(2) = p0x + barW; _heartTriYs(2) = postTop
        shapeBatch.fillPolygon(_heartTriXs, _heartTriYs, 3, clamp(r * 1.1f), clamp(g * 1.1f), clamp(b * 1.1f), 1f)
        _heartTriXs(0) = p1x - barW / 2; _heartTriYs(0) = postTop
        _heartTriXs(1) = p1x; _heartTriYs(1) = cy - hs
        _heartTriXs(2) = p1x + barW / 2; _heartTriYs(2) = postTop
        shapeBatch.fillPolygon(_heartTriXs, _heartTriYs, 3, clamp(r * 1.1f), clamp(g * 1.1f), clamp(b * 1.1f), 1f)
        _heartTriXs(0) = p2x - barW; _heartTriYs(0) = postTop
        _heartTriXs(1) = p2x - barW / 2; _heartTriYs(1) = cy - hs
        _heartTriXs(2) = p2x; _heartTriYs(2) = postTop
        shapeBatch.fillPolygon(_heartTriXs, _heartTriYs, 3, clamp(r * 1.1f), clamp(g * 1.1f), clamp(b * 1.1f), 1f)
        // Horizontal cross rails
        val railH = barW * 0.65f
        val rail1Y = cy - hs * 0.3f; val rail2Y = cy + hs * 0.3f
        shapeBatch.fillRect(p0x, rail1Y, p2x - p0x, railH, r * 0.9f, g * 0.9f, b * 0.9f, 1f)
        shapeBatch.fillRect(p0x, rail2Y, p2x - p0x, railH, r * 0.9f, g * 0.9f, b * 0.9f, 1f)
        // Rail top-edge highlight
        shapeBatch.fillRect(p0x, rail1Y, p2x - p0x, railH * 0.3f, hr, hg, hb, 0.25f)
        shapeBatch.fillRect(p0x, rail2Y, p2x - p0x, railH * 0.3f, hr, hg, hb, 0.25f)
        // Post left-edge highlights
        val hlW = barW * 0.3f
        shapeBatch.fillRect(p0x, postTop, hlW, postH, hr, hg, hb, 0.3f)
        shapeBatch.fillRect(p1x - barW / 2, postTop, hlW, postH, hr, hg, hb, 0.3f)
        shapeBatch.fillRect(p2x - barW, postTop, hlW, postH, hr, hg, hb, 0.3f)

      case _ =>
        _defItemXs(0) = cx;      _defItemYs(0) = cy - hs
        _defItemXs(1) = cx + hs; _defItemYs(1) = cy
        _defItemXs(2) = cx;      _defItemYs(2) = cy + hs
        _defItemXs(3) = cx - hs; _defItemYs(3) = cy
        shapeBatch.fillPolygon(_defItemXs, _defItemYs, 4, r, g, b, 1f)
        shapeBatch.strokePolygon(_defItemXs, _defItemYs, 1f, dr, dg, db, 0.7f)
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
    intToRGB(proj.colorRGB)
    val plr = _rgb_r; val plg = _rgb_g; val plb = _rgb_b
    lightSystem.addLight(sx, sy, 55f, plr, plg, plb, 0.15f)

    beginShapes()
    val renderer = GLProjectileRenderers.getRenderer(proj.projectileType)
    if (renderer != null) {
      renderer(proj, sx, sy, shapeBatch, animationTick)
    } else {
      // Reuse plr/plg/plb from intToRGB call above (same proj.colorRGB)
      GLProjectileRenderers.drawGeneric(proj, sx, sy, shapeBatch, animationTick, plr, plg, plb)
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
    val crs = pDef.chargeRangeScaling
    val minRange = if (crs.isDefined) crs.get.min.toInt else pDef.maxRange
    val maxRange = if (crs.isDefined) crs.get.max.toInt else pDef.maxRange
    val cylLength = 1.0 + minRange + chargePct * (maxRange - minRange)
    intToRGB(client.getLocalColorRGB)
    val cr = _rgb_r; val cg = _rgb_g; val cb = _rgb_b
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
    var i = 0
    while (i <= glowN) {
      val t = i.toDouble / glowN
      val gw = 1.8 * pulse
      val cwx = drawX + ndx * cylLength * t
      val cwy = drawY + ndy * cylLength * t
      _aimGlowXs(i) = worldToScreenX(cwx + perpX * gw, cwy + perpY * gw).toFloat
      _aimGlowYs(i) = worldToScreenY(cwx + perpX * gw, cwy + perpY * gw).toFloat
      _aimGlowXs(glowN * 2 + 1 - i) = worldToScreenX(cwx - perpX * gw, cwy - perpY * gw).toFloat
      _aimGlowYs(glowN * 2 + 1 - i) = worldToScreenY(cwx - perpX * gw, cwy - perpY * gw).toFloat
      i += 1
    }
    shapeBatch.fillPolygon(_aimGlowXs, _aimGlowYs, glowN * 2 + 2, blendR, blendG, blendB, 0.05f * pulse)

    // Cylinder body
    val bodyN = 20
    i = 0
    while (i <= bodyN) {
      val t = i.toDouble / bodyN
      _aimBodyXs(i) = cylSX(t, 1.0)
      _aimBodyYs(i) = cylSY(t, 1.0)
      _aimBodyXs(bodyN * 2 + 1 - i) = cylSX(t, -1.0)
      _aimBodyYs(bodyN * 2 + 1 - i) = cylSY(t, -1.0)
      i += 1
    }
    shapeBatch.fillPolygon(_aimBodyXs, _aimBodyYs, bodyN * 2 + 2, blendR * 0.5f, blendG * 0.5f, blendB * 0.5f, 0.10f + chargePct * 0.06f)

    // Energy spine
    shapeBatch.setAdditiveBlend(true)
    val spineSegs = 14
    i = 0
    while (i < spineSegs) {
      val t0 = i.toDouble / spineSegs; val t1 = (i + 1).toDouble / spineSegs
      val fadeAlpha = ((1.0 - t0 * 0.7) * (0.08 + chargePct * 0.12) * pulse).toFloat
      val lw = ((2.0 + chargePct * 1.5) * (1.0 - t0 * 0.5)).toFloat
      shapeBatch.strokeLine(cylSX(t0, 0), cylSY(t0, 0), cylSX(t1, 0), cylSY(t1, 0), lw, brightR, brightG, brightB, clamp(fadeAlpha))
      i += 1
    }

    // Energy rings
    val ringCount = 4 + (chargePct * 4).toInt
    i = 0
    while (i < ringCount) {
      val t = ((animationTick * 0.035 + i.toDouble / ringCount) % 1.0)
      val ringAlpha = ((1.0 - Math.abs(t - 0.5) * 2.0) * (0.15 + chargePct * 0.2) * pulse).toFloat
      val ringSegs = 8
      var j = 0
      while (j < ringSegs) {
        val a1 = Math.PI * j.toDouble / ringSegs - Math.PI * 0.5
        val a2 = Math.PI * (j + 1).toDouble / ringSegs - Math.PI * 0.5
        val w1 = Math.cos(a1); val w2 = Math.cos(a2)
        val f1 = Math.sin(a1) * (cylRadius / cylLength) * 0.3
        val f2 = Math.sin(a2) * (cylRadius / cylLength) * 0.3
        shapeBatch.strokeLine(cylSX(t + f1, w1), cylSY(t + f1, w1),
          cylSX(t + f2, w2), cylSY(t + f2, w2), 1.5f + chargePct, brightR, brightG, brightB, clamp(ringAlpha))
        j += 1
      }
      i += 1
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
    val now = _frameTimeMs
    val iter = client.getDeathAnimations.entrySet().iterator()
    // Clean up old death burst tracking — use Java iterator to avoid lambda allocation
    val burstIter = deathBurstSpawned.iterator()
    while (burstIter.hasNext) {
      if (!client.getDeathAnimations.containsKey(burstIter.next())) burstIter.remove()
    }
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
    val elapsed = _frameTimeMs - deathTime
    if (elapsed < 0 || elapsed > DEATH_ANIMATION_MS) return

    val progress = elapsed.toDouble / DEATH_ANIMATION_MS
    val fadeOut = (1.0 - progress).toFloat
    intToRGB(colorRGB)
    val cr = _rgb_r; val cg = _rgb_g; val cb = _rgb_b
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

    // Shockwave rings — use strokeOval which leverages pre-computed sin/cos LUTs
    val ring1R = (progress * 55).toFloat
    shapeBatch.strokeOval(cx, cy, ring1R, ring1R * 0.5f, 3f * fadeOut, cr, cg, cb, 0.55f * fadeOut, 24)

    // Particles
    var i = 0
    while (i < 14) {
      val pAngle = i * (2 * Math.PI / 14) + i * 0.4
      val speed = 0.7 + (i % 4) * 0.15
      val dist = (progress * (25 + (i % 4) * 12) * speed).toFloat
      val rise = (progress * progress * 25 * (0.8 + (i % 3) * 0.15)).toFloat
      val px = cx + dist * Math.cos(pAngle).toFloat
      val py = cy + dist * Math.sin(pAngle).toFloat * 0.5f - rise
      val pSize = ((2.5 + (i % 3) * 1.5) * fadeOut).toFloat
      var pr = 0f; var pg = 0f; var pb = 0f
      val im3 = i % 3
      if (im3 == 0) { pr = 1f; pg = 1f; pb = 0.9f }
      else if (im3 == 1) { pr = clamp(cr * 0.4f + 0.6f); pg = clamp(cg * 0.4f + 0.6f); pb = clamp(cb * 0.4f + 0.6f) }
      else { pr = cr; pg = cg; pb = cb }
      val pAlpha = (if (i % 3 == 0) 0.7 else if (i % 3 == 1) 0.65 else 0.55).toFloat * fadeOut
      shapeBatch.fillOval(px, py, pSize, pSize, pr, pg, pb, pAlpha, 8)
      i += 1
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
    val now = _frameTimeMs
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
    val elapsed = _frameTimeMs - startTime
    if (elapsed > TELEPORT_ANIMATION_MS) return
    val progress = elapsed.toDouble / TELEPORT_ANIMATION_MS
    val fadeOut = Math.max(0, 1.0 - progress * 1.5).toFloat

    beginShapes()
    shapeBatch.setAdditiveBlend(true)
    var i = 0
    while (i < 8) {
      val angle = (i * (2 * Math.PI / 8) + progress * 2).toFloat
      val dist = (25 * (1 - progress)).toFloat
      val px = sx + dist * Math.cos(angle).toFloat
      val py = sy + dist * Math.sin(angle).toFloat * 0.5f
      shapeBatch.fillOval(px, py, 3f * fadeOut, 3f * fadeOut, 1f, 0.92f, 0.3f, 0.7f * fadeOut, 8)
      i += 1
    }
    if (progress < 0.2) {
      val flashAlpha = (0.5 * (1 - progress / 0.2)).toFloat
      val flashR = (15 * (1 - progress / 0.2)).toFloat
      shapeBatch.fillOvalSoft(sx, sy, flashR, flashR * 0.5f, 1f, 1f, 0.8f, flashAlpha, 0f, 12)
    }
    shapeBatch.setAdditiveBlend(false)
  }

  private def drawTeleportArrival(sx: Float, sy: Float, startTime: Long): Unit = {
    val elapsed = _frameTimeMs - startTime
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
    var i = 0
    while (i < 8) {
      val angle = (i * (2 * Math.PI / 8) - progress * 2).toFloat
      val dist = (progress * 30).toFloat
      val px = sx + dist * Math.cos(angle).toFloat
      val py = sy + dist * Math.sin(angle).toFloat * 0.5f
      shapeBatch.fillOval(px, py, 3f * fadeOut, 3f * fadeOut, 1f, 0.92f, 0.3f, 0.5f * fadeOut, 8)
      i += 1
    }
    shapeBatch.setAdditiveBlend(false)
  }

  private def drawExplosionAnimations(): Unit = {
    val now = _frameTimeMs
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
        intToRGB(colorRGB)
        val er = _rgb_r; val eg = _rgb_g; val eb = _rgb_b

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
          var i = 0
          while (i < numDebris) {
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
            i += 1
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
    val now = _frameTimeMs
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
        intToRGB(colorRGB)
        val ar = _rgb_r; val ag = _rgb_g; val ab = _rgb_b

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
        var ring = 0
        while (ring < 3) {
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
          ring += 1
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
          var i = 0
          while (i < numSparks) {
            val angle = i.toDouble * Math.PI * 2 / numSparks + entry.getKey * 1.3
            val dist = 0.5f + sparkP * 0.5f
            val px = sx + (Math.cos(angle) * aoeW * dist).toFloat
            val py = sy + (Math.sin(angle) * aoeH * dist).toFloat
            val sparkAlpha = 0.6f * (1f - sparkP)
            shapeBatch.fillOval(px, py, 2.5f, 2f, bright(ar), bright(ag), bright(ab), sparkAlpha, 6)
            i += 1
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
    var fr = 0f; var fg = 0f; var fb = 0f
    teamId match {
      case 1 => fr = 0.29f; fg = 0.51f; fb = 1f
      case 2 => fr = 0.91f; fg = 0.25f; fb = 0.34f
      case 3 => fr = 0.2f; fg = 0.8f; fb = 0.2f
      case 4 => fr = 0.95f; fg = 0.77f; fb = 0.06f
      case _ => fr = 0.2f; fg = 0.8f; fb = 0.2f
    }

    // Smooth drain bar (white ghost segment that shrinks behind the real bar)
    val existingSmooth = smoothHealthMap.get(playerId)
    val smoothPct = if (existingSmooth != null) existingSmooth.floatValue() else pct
    val newSmooth = if (smoothPct > pct) Math.max(pct, smoothPct - 0.8f * (1f / 60f))
                    else pct // snap to actual on heal
    smoothHealthMap.put(playerId, newSmooth: java.lang.Float)
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
      val scx = screenCenterX.toFloat
      _indicatorXs(0) = scx; _indicatorXs(1) = scx + indS; _indicatorXs(2) = scx; _indicatorXs(3) = scx - indS
      _indicatorYs(0) = indY; _indicatorYs(1) = indY + indS; _indicatorYs(2) = indY + indS * 2; _indicatorYs(3) = indY + indS
      shapeBatch.fillPolygon(_indicatorXs, _indicatorYs, 4, fr, fg, fb, 1f)
    }
  }

  private def drawCharacterName(name: String, screenCenterX: Double, spriteTopY: Double): Unit = {
    beginSprites()
    drawCharacterNameDirect(name, screenCenterX, spriteTopY)
  }

  /** Draw character name without calling beginSprites() — used by flushDeferredBars. */
  private def drawCharacterNameDirect(name: String, screenCenterX: Double, spriteTopY: Double): Unit = {
    val barH = Constants.HEALTH_BAR_HEIGHT_PX
    val nameY = (spriteTopY - Constants.HEALTH_BAR_OFFSET_Y - barH - fontSmall.charHeight - 2).toFloat
    val textW = fontSmall.measureWidth(name)
    val textX = (screenCenterX - textW / 2).toFloat
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
    fontSmall.drawTextOutlined(spriteBatch, world.name, 12, 12)
    // Cache HUD strings only when values change (avoids per-frame string interpolation)
    val lpx = localPos.getX; val lpy = localPos.getY
    if (lpx != _cachedPosX || lpy != _cachedPosY) {
      _cachedPosX = lpx; _cachedPosY = lpy
      _cachedPosStr = "Pos: (" + lpx + ", " + lpy + ")"
    }
    fontSmall.drawTextOutlined(spriteBatch, _cachedPosStr, 12, 30)
    val pc = playerCount + 1
    if (pc != _cachedPlayerCount) { _cachedPlayerCount = pc; _cachedPlayersStr = "Players: " + pc }
    fontSmall.drawTextOutlined(spriteBatch, _cachedPlayersStr, 12, 48)
    val ic = client.getInventoryCount
    if (ic != _cachedItemCount) { _cachedItemCount = ic; _cachedItemsStr = "Items: " + ic }
    fontSmall.drawTextOutlined(spriteBatch, _cachedItemsStr, 12, 66)

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
    var i = 0
    while (i < inventorySlotTypes.length) {
      val pair = inventorySlotTypes(i)
      val itemType = pair._1
      val count = client.getItemCount(itemType.id)
      val slotX = startX + i * (slotSize + slotGap)
      intToRGB(itemType.colorRGB)
      val tr = _rgb_r; val tg = _rgb_g; val tb = _rgb_b

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
      i += 1
    }

    // Draw all text labels (sprites) in one batch
    beginSprites()
    i = 0
    while (i < inventorySlotTypes.length) {
      val pair = inventorySlotTypes(i)
      val itemType = pair._1; val keyLabel = pair._2
      val count = client.getItemCount(itemType.id)
      val slotX = startX + i * (slotSize + slotGap)

      if (count > 0) {
        fontSmall.drawText(spriteBatch, count.toString, slotX + slotSize - 8, startY - 3)
      }
      fontSmall.drawText(spriteBatch, keyLabel, slotX + slotSize / 2 - 4, startY + slotSize - 16,
        if (count > 0) 0.9f else 0.43f, if (count > 0) 0.9f else 0.43f, if (count > 0) 0.9f else 0.47f, 1f)
      i += 1
    }
  }

  private def renderAbilityHUD(screenW: Int, screenH: Int): Unit = {
    val slotSize = 44f; val slotGap = 8f
    val invSlotSize = 44f; val invSlotGap = 8f; val invNumSlots = 5
    val totalInvW = invNumSlots * invSlotSize + (invNumSlots - 1) * invSlotGap
    val inventoryStartX = (screenW - totalInvW) / 2f
    val startY = screenH - slotSize - 14f

    val charDef = client.getSelectedCharacterDef
    val qAbility = charDef.qAbility; val eAbility = charDef.eAbility
    val qCooldown = client.getQCooldownFraction; val eCooldown = client.getECooldownFraction
    val numAbilities = 2

    val now = _frameTimeMs
    val abilityGap = 12f
    beginShapes()
    var i = 0
    while (i < numAbilities) {
      val aDef = if (i == 0) qAbility else eAbility
      val cooldownFrac = if (i == 0) qCooldown else eCooldown
      val ar = _abilityR(i); val ag = _abilityG(i); val ab = _abilityB(i)
      val slotX = inventoryStartX - (numAbilities - i) * (slotSize + slotGap) - abilityGap
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
      i += 1
    }

    // Key labels
    beginSprites()
    i = 0
    while (i < numAbilities) {
      val aDef = if (i == 0) qAbility else eAbility
      val slotX = inventoryStartX - (numAbilities - i) * (slotSize + slotGap) - abilityGap
      val ar = _abilityR(i); val ag = _abilityG(i); val ab = _abilityB(i)
      val onCooldown = (if (i == 0) qCooldown else eCooldown) > 0.001f
      val ka = if (onCooldown) 0.5f else 1f
      fontSmall.drawTextOutlined(spriteBatch, aDef.keybind,
        slotX + slotSize - fontSmall.measureWidth(aDef.keybind) - 3, startY + slotSize - fontSmall.charHeight - 1,
        ar * ka, ag * ka, ab * ka, ka)
      i += 1
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
        var i = 0
        while (i < spread) {
          val angle = -Math.PI / 2 + (i - (spread - 1) / 2.0) * Math.PI / 6
          val dx = Math.cos(angle).toFloat * sz * 0.7f
          val dy = Math.sin(angle).toFloat * sz * 0.7f
          shapeBatch.fillOval(cx + dx, cy + dy, sz * 0.25f, sz * 0.25f, r, g, b, a, 6)
          i += 1
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
        _dashArrowXs(0) = cx + sz * 0.6f; _dashArrowXs(1) = cx - sz * 0.3f; _dashArrowXs(2) = cx - sz * 0.3f
        _dashArrowYs(0) = cy; _dashArrowYs(1) = cy - sz * 0.45f; _dashArrowYs(2) = cy + sz * 0.45f
        shapeBatch.fillPolygon(_dashArrowXs, _dashArrowYs, 3, r, g, b, a)
        // Speed lines
        var i = 0
        while (i < 3) {
          val ly = cy + (i - 1) * sz * 0.35f
          shapeBatch.strokeLine(cx - sz * 0.9f, ly, cx - sz * 0.4f, ly, 1.5f, r, g, b, a * 0.5f)
          i += 1
        }

      case _: TeleportCast =>
        // Lightning bolt / flash
        _teleportBoltXs(0) = cx - sz * 0.15f; _teleportBoltXs(1) = cx + sz * 0.35f; _teleportBoltXs(2) = cx + sz * 0.05f
        _teleportBoltXs(3) = cx + sz * 0.45f; _teleportBoltXs(4) = cx - sz * 0.1f; _teleportBoltXs(5) = cx + sz * 0.1f
        _teleportBoltYs(0) = cy - sz * 0.7f; _teleportBoltYs(1) = cy - sz * 0.1f; _teleportBoltYs(2) = cy - sz * 0.1f
        _teleportBoltYs(3) = cy + sz * 0.7f; _teleportBoltYs(4) = cy + sz * 0.1f; _teleportBoltYs(5) = cy + sz * 0.1f
        shapeBatch.fillPolygon(_teleportBoltXs, _teleportBoltYs, 6, r, g, b, a)

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
    if (chargeLevel != _cachedChargeLevel) { _cachedChargeLevel = chargeLevel; _cachedChargeStr = chargeLevel + "%" }
    fontSmall.drawText(spriteBatch, _cachedChargeStr, barX + barW / 2 - 12, barY - 16)
  }

  private def renderLobbyHUD(screenW: Int, screenH: Int): Unit = {
    if (client.clientState != ClientState.PLAYING) return

    val remaining = client.gameTimeRemaining
    if (remaining != _cachedTimerRemaining) {
      _cachedTimerRemaining = remaining
      val minutes = remaining / 60; val seconds = remaining % 60
      _cachedTimerStr = f"$minutes%d:$seconds%02d"
    }
    val timerText = _cachedTimerStr

    // Timer panel at top center (cache measureWidth — called twice otherwise)
    val timerTextW = fontMedium.measureWidth(timerText)
    val timerW = timerTextW + 20
    beginShapes()
    shapeBatch.fillRect((screenW / 2 - timerW / 2).toFloat, 4f, timerW.toFloat, 30f, 0.04f, 0.04f, 0.08f, 0.5f)
    shapeBatch.strokeRect((screenW / 2 - timerW / 2).toFloat, 4f, timerW.toFloat, 30f, 1f, 0.3f, 0.3f, 0.4f, 0.3f)

    beginSprites()
    fontMedium.drawTextOutlined(spriteBatch, timerText, (screenW / 2 - timerTextW / 2).toFloat, 8)

    // K/D display below status panel
    val kc = client.killCount; val dc = client.deathCount
    if (kc != _cachedKills || dc != _cachedDeaths) {
      _cachedKills = kc; _cachedDeaths = dc
      _cachedKDStr = "K: " + kc + "  D: " + dc
    }
    fontSmall.drawTextOutlined(spriteBatch, _cachedKDStr, 12, 108)

    // Kill feed — collect entries, then batch all backgrounds (shapes) and all text (sprites)
    // to avoid per-entry batch switches (was 2 flushes × N entries, now just 1 switch)
    val now = _frameTimeMs
    var feedY = 10f
    val localName = client.getSelectedCharacterDef.displayName
    _feedCount = 0
    val feedIter = client.killFeed.iterator()
    while (feedIter.hasNext && _feedCount < MAX_FEED_ENTRIES) {
      val entry = feedIter.next()
      val timestamp = entry(0).asInstanceOf[java.lang.Long].longValue()
      val elapsed = now - timestamp
      if (elapsed < 6000) {
        val alpha = Math.max(0.15f, (1.0 - elapsed / 6000.0).toFloat)
        val killer = entry(1).asInstanceOf[String]
        val victim = entry(2).asInstanceOf[String]
        _feedTextBuilder.setLength(0)
        _feedTextBuilder.append(killer).append(" killed ").append(victim)
        val feedText = _feedTextBuilder.toString
        val textW = fontSmall.measureWidth(feedText)
        val slideOffset = if (elapsed < 200) ((1.0 - elapsed / 200.0) * 60).toFloat else 0f
        val fx = screenW - textW - 12f + slideOffset
        val fi = _feedCount
        _feedX(fi) = fx; _feedY(fi) = feedY; _feedW(fi) = textW; _feedA(fi) = alpha
        _feedText(fi) = feedText
        val isLocalKiller = killer == localName
        val isLocalVictim = victim == localName
        if (isLocalKiller) { _feedR(fi) = 0.4f; _feedG(fi) = 1f; _feedB(fi) = 0.4f }
        else if (isLocalVictim) { _feedR(fi) = 1f; _feedG(fi) = 0.4f; _feedB(fi) = 0.4f }
        else { _feedR(fi) = 1f; _feedG(fi) = 1f; _feedB(fi) = 1f }
        _feedCount += 1
        feedY += 18
      }
    }
    // Batch all background strips in one shapes pass
    if (_feedCount > 0) {
      beginShapes()
      var fi = 0
      while (fi < _feedCount) {
        shapeBatch.fillRect(_feedX(fi) - 4f, _feedY(fi) - 2f, _feedW(fi) + 8f, 18f, 0.04f, 0.04f, 0.08f, 0.4f * _feedA(fi))
        fi += 1
      }
      // Then all text in one sprites pass
      beginSprites()
      fi = 0
      while (fi < _feedCount) {
        fontSmall.drawText(spriteBatch, _feedText(fi), _feedX(fi), _feedY(fi), _feedR(fi), _feedG(fi), _feedB(fi), _feedA(fi))
        fi += 1
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
    val elapsed = _frameTimeMs - deathTime
    val remaining = Math.max(0, Constants.RESPAWN_DELAY_MS - elapsed)
    val secondsLeft = Math.ceil(remaining / 1000.0).toInt

    val cx = screenW / 2f; val cy = screenH / 2f

    beginShapes()
    shapeBatch.fillRect(cx - 120, cy - 50, 240, 90, 0f, 0f, 0f, 0.55f)

    beginSprites()
    val respawnText = "Respawning in " + secondsLeft + "s"
    fontMedium.drawTextOutlined(spriteBatch, respawnText, cx - fontMedium.measureWidth(respawnText) / 2, cy - 30)

    val killerName = client.lastKillerCharacterName
    if (killerName != null && killerName.nonEmpty) {
      val killedByText = "Killed by " + killerName
      fontSmall.drawTextOutlined(spriteBatch, killedByText, cx - fontSmall.measureWidth(killedByText) / 2, cy)
    }
  }

  // ═══════════════════════════════════════════════════════════════════
  //  ANIMATED TILE OVERLAYS
  // ═══════════════════════════════════════════════════════════════════

  private def drawTileOverlays(): Unit = {
    beginShapes()
    shapeBatch.setAdditiveBlend(true)
    val time = _animTickF
    var i = 0
    while (i < _specialTileCount) {
      val wx = _specialTileWX(i); val wy = _specialTileWY(i); val tid = _specialTileTid(i)
      val sx = worldToScreenX(wx, wy).toFloat
      val sy = worldToScreenY(wx, wy).toFloat
      tid match {
        case 1 | 7 => // Water / DeepWater — shimmer lines + specular highlight
          val phase = time * 0.04f + wx * 1.1f + wy * 0.7f
          // Moving shimmer lines across diamond
          var j = 0
          while (j < 3) {
            val linePhase = phase + j * 2.1f
            val t = (linePhase % 3.0f) / 3.0f
            val lx1 = sx - HW * (1f - t) + HW * t
            val ly1 = sy - HH * t + HH * (1f - t) * 0.3f
            val lx2 = lx1 + HW * 0.4f
            val ly2 = ly1 + HH * 0.2f
            val shimmerA = (0.3f * Math.sin(linePhase * 1.5).toFloat).abs
            shapeBatch.strokeLine(lx1, ly1, lx2, ly2, 1f, 0.6f, 0.8f, 1f, shimmerA)
            j += 1
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
          var j = 0
          while (j < 2) {
            val crackPhase = lavaPhase + j * 1.5f
            val ca = (0.2f + 0.1f * Math.sin(crackPhase * 2.0)).toFloat
            val cx1 = sx - 6 + Math.sin(crackPhase + j).toFloat * 4
            val cy1 = sy - 2 + Math.cos(crackPhase * 0.8).toFloat * 2
            val cx2 = cx1 + 8 + Math.sin(crackPhase * 1.3).toFloat * 3
            val cy2 = cy1 + 3
            shapeBatch.strokeLine(cx1, cy1, cx2, cy2, 1.5f, 1f, 0.85f, 0.3f, ca)
            j += 1
          }

        case 9 => // Ice — flashing sparkle points
          val icePhase = time * 0.08f + wx * 3.7f + wy * 2.3f
          var j = 0
          while (j < 3) {
            val sparkPhase = icePhase + j * 2.094f
            val flash = Math.max(0f, Math.sin(sparkPhase * 1.5).toFloat)
            if (flash > 0.3f) {
              // Deterministic positions based on tile coords
              val offX = ((wx * 13 + wy * 7 + j * 17) % 11 - 5).toFloat
              val offY = ((wx * 7 + wy * 11 + j * 23) % 7 - 3).toFloat * 0.5f
              shapeBatch.fillOval(sx + offX, sy + offY, 1.5f, 1.5f, 0.85f, 0.95f, 1f, flash * 0.5f, 4)
            }
            j += 1
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
    // Local player light (use cached visual position — already updated this frame)
    val lvx = client.visualPosX; val lvy = client.visualPosY
    val lpsx = worldToScreenX(lvx, lvy).toFloat
    val lpsy = worldToScreenY(lvx, lvy).toFloat
    lightSystem.addLight(lpsx, lpsy, 80f, 1f, 0.9f, 0.7f, 0.15f)

    // Remote players — use getRemoteVisualPos to avoid tuple allocation
    val players = client.getPlayers
    val iter = players.values().iterator()
    while (iter.hasNext) {
      val player = iter.next()
      val hasVisPos = entityCollector.getRemoteVisualPos(player.getId)
      val pvx = if (hasVisPos) entityCollector.lastRVX else player.getPosition.getX.toDouble
      val pvy = if (hasVisPos) entityCollector.lastRVY else player.getPosition.getY.toDouble
      val psx = worldToScreenX(pvx, pvy).toFloat
      val psy = worldToScreenY(pvx, pvy).toFloat
      lightSystem.addLight(psx, psy, 65f, 1f, 0.85f, 0.65f, 0.10f)
    }

    // Explosion lights
    val now = _frameTimeMs
    val expIter = client.getExplosionAnimations.values().iterator()
    while (expIter.hasNext) {
      val data = expIter.next()
      val timestamp = data(0)
      val elapsed = now - timestamp
      if (elapsed < 1400) {
        val wx = data(1).toDouble / 1000.0; val wy = data(2).toDouble / 1000.0
        val colorRGB = data(3).toInt
        intToRGB(colorRGB)
        val er = _rgb_r; val eg = _rgb_g; val eb = _rgb_b
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
    val prevLocal = prevHealthMap.get(localId)
    if (prevLocal != null && prevLocal.intValue() > localHealth) {
      val dmg = prevLocal.intValue() - localHealth
      val lvx = client.visualPosX; val lvy = client.visualPosY
      damageNumbers.spawn(lvx.toFloat, lvy.toFloat, dmg, 1f, 0.3f, 0.2f)
      spawnImpactSparks(lvx.toFloat, lvy.toFloat, 1f, 0.3f, 0.2f)
    }
    prevHealthMap.put(localId, localHealth)

    // Check remote players — use Java iterator + getRemoteVisualPos to avoid tuple allocation
    val players = client.getPlayers
    val iter = players.entrySet().iterator()
    while (iter.hasNext) {
      val entry = iter.next()
      val playerId = entry.getKey
      val player = entry.getValue
      val health = player.getHealth
      val prev = prevHealthMap.get(playerId)
      if (prev != null && prev.intValue() > health) {
        val dmg = prev.intValue() - health
        val hasVisPos = entityCollector.getRemoteVisualPos(playerId)
        val pvx = if (hasVisPos) entityCollector.lastRVX else player.getPosition.getX.toDouble
        val pvy = if (hasVisPos) entityCollector.lastRVY else player.getPosition.getY.toDouble
        damageNumbers.spawn(pvx.toFloat, pvy.toFloat, dmg, 1f, 1f, 0.3f)
        spawnImpactSparks(pvx.toFloat, pvy.toFloat, 1f, 1f, 0.3f)
      }
      prevHealthMap.put(playerId, health)
    }
  }

  // ═══════════════════════════════════════════════════════════════════
  //  POST-PROCESSING EFFECT HELPERS
  // ═══════════════════════════════════════════════════════════════════

  private def updateExplosionDistortion(): Unit = {
    val now = _frameTimeMs
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

        // Check proximity to local player (use cached visual position)
        val lvx = client.visualPosX; val lvy = client.visualPosY
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
    val rate = (_bgType: @scala.annotation.switch) match {
      case 0 => 12f  // sky: gentle leaf/pollen drift
      case 4 => 20f  // ocean: spray mist
      case 2 => 4f   // space: drifting dust motes
      case 3 => 15f  // desert: sand particles
      case 1 => 8f   // cityscape: dust/ash
      case _ => 8f
    }
    weatherSpawnAccum += rate * dt
    while (weatherSpawnAccum >= 1f) {
      weatherSpawnAccum -= 1f
      (_bgType: @scala.annotation.switch) match {
        case 0 => spawnSkyParticle(w, h)
        case 4 => spawnOceanParticle(w, h)
        case 2 => spawnSpaceParticle(w, h)
        case 3 => spawnDesertParticle(w, h)
        case 1 => spawnCityParticle(w, h)
        case _ => spawnSkyParticle(w, h)
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
    val lvx = camera.visualX; val lvy = camera.visualY
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
        var _k = 0
        while (_k < 3) {
          combatParticles.emit(
            sx + rng.nextFloat() * 6f - 3f, sy + rng.nextFloat() * 2f,
            rng.nextFloat() * 8f - 4f, -(2f + rng.nextFloat() * 4f),
            plife = 0.4f + rng.nextFloat() * 0.3f,
            pr = 0.6f, pg = 0.55f, pb = 0.45f, palpha = 0.2f,
            psize = 1.5f + rng.nextFloat() * 1.5f,
            pgravity = 5f, soft = true
          )
          _k += 1
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
        intToRGB(proj.colorRGB)
        val pr = _rgb_r; val pg = _rgb_g; val pb = _rgb_b
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
    var _k = 0
    while (_k < 8) {
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
      _k += 1
    }
  }

  /** Spawn a burst of particles on player death. */
  private def spawnDeathBurst(worldX: Float, worldY: Float, colorRGB: Int): Unit = {
    val sx = worldToScreenX(worldX, worldY).toFloat
    val sy = worldToScreenY(worldX, worldY).toFloat - 12f
    intToRGB(colorRGB)
    val cr = _rgb_r; val cg = _rgb_g; val cb = _rgb_b
    var _k = 0
    while (_k < 20) {
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
      _k += 1
    }
  }

  // ═══════════════════════════════════════════════════════════════════
  //  WATER REFLECTIONS
  // ═══════════════════════════════════════════════════════════════════

  private def drawWaterReflections(): Unit = {
    val world = client.getWorld
    val lvx = camera.visualX; val lvy = camera.visualY
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

    // Create flipped region (swap v and v2 for vertical flip) — cached to avoid per-frame allocation
    if (region ne _reflectionSourceRegion) {
      _reflectionSourceRegion = region
      _reflectionRegion = TextureRegion(region.texture, region.u, region.v2, region.u2, region.v)
    }
    val flippedRegion = _reflectionRegion
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
    _itemRemoveBuffer.clear()
    val iter = itemLastWorldPos.entrySet().iterator()
    while (iter.hasNext) {
      val entry = iter.next()
      val id = entry.getKey
      if (!drawnItemIdsThisFrame.contains(id)) {
        val posData = entry.getValue
        val wx = posData(0); val wy = posData(1); val colorRGB = posData(2)
        // Spawn pickup ring burst particles
        val sx = worldToScreenX(wx, wy).toFloat
        val sy = worldToScreenY(wx, wy).toFloat
        intToRGB(colorRGB)
        val cr = _rgb_r; val cg = _rgb_g; val cb = _rgb_b
        var _k = 0
        while (_k < 12) {
          val angle = rng.nextFloat() * Math.PI.toFloat * 2f
          val speed = 25f + rng.nextFloat() * 35f
          combatParticles.emit(sx, sy,
            Math.cos(angle).toFloat * speed, Math.sin(angle).toFloat * speed,
            plife = 0.3f + rng.nextFloat() * 0.25f,
            pr = clamp(cr * 0.5f + 0.5f), pg = clamp(cg * 0.5f + 0.5f), pb = clamp(cb * 0.5f + 0.5f),
            palpha = 0.6f,
            psize = 2f + rng.nextFloat() * 2f,
            pgravity = 15f, additive = true)
          _k += 1
        }
        _itemRemoveBuffer += id
        itemSpawnTimes.remove(id)
      }
    }
    var ri = 0
    while (ri < _itemRemoveBuffer.size) {
      itemLastWorldPos.remove(_itemRemoveBuffer(ri))
      ri += 1
    }
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
      _shadowXs(0) = sx; _shadowXs(1) = sx + hw; _shadowXs(2) = sx + hw; _shadowXs(3) = sx
      _shadowYs(0) = sy + hh; _shadowYs(1) = sy; _shadowYs(2) = sy + shadowDist; _shadowYs(3) = sy + hh + shadowDist
      shapeBatch.fillPolygon(_shadowXs, _shadowYs, 4, 0.0f, 0.0f, 0.05f, shadowAlpha)
    }
    // Check east neighbor (wx+1, wy) — shadow falls on bottom-left edge
    if (wx + 1 < world.width && world.getTile(wx + 1, wy).walkable) {
      _shadowXs(0) = sx; _shadowXs(1) = sx - hw; _shadowXs(2) = sx - hw; _shadowXs(3) = sx
      _shadowYs(0) = sy + hh; _shadowYs(1) = sy; _shadowYs(2) = sy + shadowDist; _shadowYs(3) = sy + hh + shadowDist
      shapeBatch.fillPolygon(_shadowXs, _shadowYs, 4, 0.0f, 0.0f, 0.05f, shadowAlpha)
    }
    // Check north neighbor (wx, wy-1) — shadow falls on top-left edge (subtle)
    if (wy - 1 >= 0 && world.getTile(wx, wy - 1).walkable) {
      _shadowXs(0) = sx; _shadowXs(1) = sx - hw; _shadowXs(2) = sx - hw; _shadowXs(3) = sx
      _shadowYs(0) = sy - hh; _shadowYs(1) = sy; _shadowYs(2) = sy - shadowDist; _shadowYs(3) = sy - hh - shadowDist
      shapeBatch.fillPolygon(_shadowXs, _shadowYs, 4, 0.0f, 0.0f, 0.05f, shadowAlpha * 0.4f)
    }
    // Check west neighbor (wx-1, wy) — shadow falls on top-right edge (subtle)
    if (wx - 1 >= 0 && world.getTile(wx - 1, wy).walkable) {
      _shadowXs(0) = sx; _shadowXs(1) = sx + hw; _shadowXs(2) = sx + hw; _shadowXs(3) = sx
      _shadowYs(0) = sy - hh; _shadowYs(1) = sy; _shadowYs(2) = sy - shadowDist; _shadowYs(3) = sy - hh - shadowDist
      shapeBatch.fillPolygon(_shadowXs, _shadowYs, 4, 0.0f, 0.0f, 0.05f, shadowAlpha * 0.4f)
    }
  }

  // ═══════════════════════════════════════════════════════════════════
  //  UTILITIES
  // ═══════════════════════════════════════════════════════════════════

  // Mutable output fields for intToRGB — avoids tuple allocation per call
  private var _rgb_r = 0f; private var _rgb_g = 0f; private var _rgb_b = 0f
  private def intToRGB(argb: Int): Unit = {
    _rgb_r = ((argb >> 16) & 0xFF) / 255f
    _rgb_g = ((argb >> 8) & 0xFF) / 255f
    _rgb_b = (argb & 0xFF) / 255f
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
