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
  private var whitePixel: GLTexture = _
  private var whiteRegion: TextureRegion = _
  // Background FBO cache — re-render every N frames to amortize procedural generation cost
  private var bgCacheFBO: GLTexture = _
  private var bgCacheRegion: TextureRegion = _
  private var bgCacheValid = false
  private var bgCacheTick = 0
  private var bgCacheType: Byte = -1
  private val BG_CACHE_INTERVAL = 3
  private val damageNumbers = new DamageNumberSystem()
  private val weatherParticles = new ParticleSystem(1024)
  private val combatParticles = new ParticleSystem(768)

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

  // Damage-proportional flash tracking
  private var _prevLocalHealthForFlash: Int = -1
  private var _lastDamageAmount: Int = 0

  // Match start fade-in (overlay alpha: 1.0 → 0.0 over 500ms)
  private var matchStartTime: Long = 0L
  private val MATCH_FADE_IN_MS = 500

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
  private var _cachedChargeLevel = -1; private var _cachedChargeStr = ""
  private var _cachedKills = -1; private var _cachedDeaths = -1; private var _cachedKillStr = "0"; private var _cachedDeathStr = "0"
  // Cached inventory slot counts — avoids double getItemCount calls and per-frame toString
  private val _cachedSlotCounts = new Array[Int](5) // one per inventory slot
  private val _cachedSlotCountStrs = new Array[String](5)
  // Cached respawn text
  private var _cachedRespawnSeconds = -1; private var _cachedRespawnStr = ""
  private var _cachedKilledByName: String = null; private var _cachedKilledByStr = ""
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
  // Pre-allocated arrays for detailed HUD icons
  private val _iconXs = new Array[Float](12)
  private val _iconYs = new Array[Float](12)
  private val _iconXs2 = new Array[Float](8)
  private val _iconYs2 = new Array[Float](8)

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

  // Deferred chat entries (same pattern as kill feed)
  private val MAX_CHAT_DISPLAY = 8
  private val _chatX = new Array[Float](MAX_CHAT_DISPLAY)
  private val _chatY = new Array[Float](MAX_CHAT_DISPLAY)
  private val _chatW = new Array[Float](MAX_CHAT_DISPLAY)
  private val _chatA = new Array[Float](MAX_CHAT_DISPLAY)
  private val _chatText = new Array[String](MAX_CHAT_DISPLAY)
  private val _chatR = new Array[Float](MAX_CHAT_DISPLAY)
  private val _chatG = new Array[Float](MAX_CHAT_DISPLAY)
  private val _chatB = new Array[Float](MAX_CHAT_DISPLAY)
  private var _chatCount = 0

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

  // Deferred additive effects — batched to minimize blend mode toggles
  private val MAX_DEFERRED_ADD_FX = 64
  private val _dafCX = new Array[Double](MAX_DEFERRED_ADD_FX)
  private val _dafCY = new Array[Double](MAX_DEFERRED_ADD_FX)
  private val _dafFlags = new Array[Int](MAX_DEFERRED_ADD_FX)
  private val _dafChargeLevel = new Array[Int](MAX_DEFERRED_ADD_FX)
  private val _dafHitTime = new Array[Long](MAX_DEFERRED_ADD_FX)
  private val _dafHitColorR = new Array[Float](MAX_DEFERRED_ADD_FX)
  private val _dafHitColorG = new Array[Float](MAX_DEFERRED_ADD_FX)
  private val _dafHitColorB = new Array[Float](MAX_DEFERRED_ADD_FX)
  private val _dafHitDirX = new Array[Float](MAX_DEFERRED_ADD_FX)
  private val _dafHitDirY = new Array[Float](MAX_DEFERRED_ADD_FX)
  private var _dafCount = 0
  private final val FX_GEM = 1; private final val FX_CHARGE = 2; private final val FX_PHASED = 4
  private final val FX_BURN = 8; private final val FX_HIT = 16; private final val FX_CAST = 32

  // Deferred item additive effects (ground glow + sparkles)
  private val MAX_DEFERRED_ITEM_FX = 64
  private val _difCX = new Array[Float](MAX_DEFERRED_ITEM_FX)
  private val _difCY = new Array[Float](MAX_DEFERRED_ITEM_FX)
  private val _difHS = new Array[Float](MAX_DEFERRED_ITEM_FX)
  private val _difR = new Array[Float](MAX_DEFERRED_ITEM_FX)
  private val _difG = new Array[Float](MAX_DEFERRED_ITEM_FX)
  private val _difB = new Array[Float](MAX_DEFERRED_ITEM_FX)
  private val _difGlowPulse = new Array[Float](MAX_DEFERRED_ITEM_FX)
  private val _difBobPhase = new Array[Float](MAX_DEFERRED_ITEM_FX)
  private var _difCount = 0

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
    // Draw all name backgrounds in one shapes batch (dark pill behind each name)
    i = 0
    while (i < _deferBarCount) {
      drawNameBackground(_deferBarName(i), _deferBarCX(i), _deferBarTY(i))
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

  private def flushDeferredAdditiveFx(): Unit = {
    if (_dafCount == 0 && _difCount == 0) return
    beginShapes()
    shapeBatch.setAdditiveBlend(true)
    // Player effects
    var i = 0
    while (i < _dafCount) {
      val cx = _dafCX(i); val cy = _dafCY(i); val flags = _dafFlags(i)
      if ((flags & FX_GEM) != 0) drawGemGlowInner(cx, cy)
      if ((flags & FX_CHARGE) != 0) drawChargingEffectInner(cx, cy, _dafChargeLevel(i))
      if ((flags & FX_PHASED) != 0) drawPhasedEffectInner(cx, cy)
      if ((flags & FX_BURN) != 0) drawBurnEffectInner(cx, cy)
      if ((flags & FX_HIT) != 0) drawHitEffectInner(cx, cy, _dafHitTime(i),
        _dafHitColorR(i), _dafHitColorG(i), _dafHitColorB(i),
        _dafHitDirX(i), _dafHitDirY(i))
      if ((flags & FX_CAST) != 0) drawCastFlashInner(cx, cy)
      i += 1
    }
    // Item additive effects (ground glow + sparkles)
    i = 0
    while (i < _difCount) {
      val cx = _difCX(i); val cy = _difCY(i); val hs = _difHS(i)
      val ir = _difR(i); val ig = _difG(i); val ib = _difB(i)
      val glowPulse = _difGlowPulse(i); val bobPhase = _difBobPhase(i)
      // Ground glow
      shapeBatch.fillOvalSoft(cx, cy + hs * 0.3f, hs * 2.5f, hs * 0.7f, ir, ig, ib, 0.22f * glowPulse, 0f, 14)
      shapeBatch.fillOvalSoft(cx, cy, hs * 2.8f, hs * 2f, ir, ig, ib, 0.1f * glowPulse, 0f, 14)
      // Sparkles
      var j = 0
      while (j < 3) {
        val sparkAngle = bobPhase * 0.8f + j * (2 * Math.PI / 3).toFloat
        val sparkDist = hs * 1.1f
        val sx = cx + sparkDist * Math.cos(sparkAngle).toFloat
        val sy = cy + sparkDist * Math.sin(sparkAngle).toFloat * 0.6f
        val sparkAlpha = (0.3 + 0.4 * Math.sin(bobPhase * 2.5 + j * 2.1)).toFloat
        val sparkSize = (1.5 + Math.sin(bobPhase * 3.0 + j) * 0.7).toFloat
        shapeBatch.fillDot(sx, sy, sparkSize, 1f, 1f, 1f, clamp(sparkAlpha))
        j += 1
      }
      i += 1
    }
    shapeBatch.setAdditiveBlend(false)
    _dafCount = 0; _difCount = 0
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
    bgCacheFBO = GLTexture.createFBO(width, height)
    bgCacheRegion = bgCacheFBO.fullRegion
    whitePixel = GLTexture.createWhitePixel()
    whiteRegion = whitePixel.fullRegion
    damageNumbers.setFont(fontSmall)
    damageNumbers.setFontLarge(fontMedium)
    initialized = true
    matchStartTime = System.currentTimeMillis()
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
      bgCacheFBO = GLTexture.resizeFBO(bgCacheFBO, fbWidth, fbHeight)
      bgCacheRegion = bgCacheFBO.fullRegion
      bgCacheValid = false
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

    // Set up projection for zoomed world-space rendering
    projection = Matrix4.orthographic(0f, canvasW.toFloat, canvasH.toFloat, 0f)

    // === Background cache: re-render to dedicated FBO when stale ===
    val bgStale = !bgCacheValid || _bgType != bgCacheType ||
      (animationTick - bgCacheTick) >= BG_CACHE_INTERVAL
    if (bgStale) {
      bgCacheFBO.bindAsTarget()  // sets viewport to FBO dimensions
      glClearColor(0f, 0f, 0f, 1f)
      glClear(GL_COLOR_BUFFER_BIT)
      shapeBatch.begin(projection)
      renderBackground(world.background)
      shapeBatch.end()
      bgCacheFBO.unbindTarget()
      bgCacheValid = true; bgCacheTick = animationTick; bgCacheType = _bgType
    }

    postProcessor.beginScene()

    // Blit cached background
    beginSprites()
    spriteBatch.draw(bgCacheRegion, 0f, 0f, canvasW.toFloat, canvasH.toFloat)

    beginShapes()

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
    _dafCount = 0
    _difCount = 0
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

    // === Deferred additive effects (single blend toggle for all player/item effects) ===
    flushDeferredAdditiveFx()

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
      damageNumbers.render(fontSmall, fontMedium, spriteBatch, camOffX, camOffY,
        _worldToScreenXFn, _worldToScreenYFn)
    }

    // === Populate lights from entities ===
    populateLights()

    // === End scene, render light map, apply post-processing ===
    // Damage-proportional vignette — scales with damage amount
    val localHitTime = client.getPlayerHitTime(client.getLocalPlayerId)
    postProcessor.overlayA = 0f
    postProcessor.damageVignette = 0f
    postProcessor.chromaticAberration = 0f
    // Track health changes for damage scaling
    val curHealth = client.getLocalHealth
    if (_prevLocalHealthForFlash >= 0 && curHealth < _prevLocalHealthForFlash) {
      _lastDamageAmount = _prevLocalHealthForFlash - curHealth
    }
    _prevLocalHealthForFlash = curHealth
    if (localHitTime > 0) {
      val elapsed = _frameTimeMs - localHitTime
      if (elapsed < HIT_ANIMATION_MS) {
        val progress = elapsed.toDouble / HIT_ANIMATION_MS
        val damageScale = Math.min(1f, _lastDamageAmount / 30f)
        val intensity = (1f - progress.toFloat) * (0.4f + 0.6f * damageScale)
        postProcessor.damageVignette = intensity
        postProcessor.chromaticAberration = 0.004f * intensity * (0.5f + 0.5f * damageScale)
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

    // Match start fade-in from black
    if (matchStartTime > 0) {
      val fadeElapsed = _frameTimeMs - matchStartTime
      if (fadeElapsed < MATCH_FADE_IN_MS) {
        val fadeAlpha = 1f - (fadeElapsed.toFloat / MATCH_FADE_IN_MS)
        postProcessor.overlayR = 0f; postProcessor.overlayG = 0f; postProcessor.overlayB = 0f
        postProcessor.overlayA = fadeAlpha
      } else if (fadeElapsed < MATCH_FADE_IN_MS + 100) {
        postProcessor.overlayA = 0f
        matchStartTime = 0
      }
    }

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
    // Far mountain silhouette (0.01x parallax)
    val mpx = (camOffX * 0.01).toFloat
    val mountainBaseY = h * 0.65f
    var mi = 0
    while (mi < 40) {
      val mx = -20f + mi * (w + 40) / 40f + mpx
      val mh = (Math.sin(mi * 0.4 + 1.3) * h * 0.06 + Math.sin(mi * 0.15 + 0.7) * h * 0.04).toFloat
      val my = mountainBaseY - Math.abs(mh)
      if (mi > 0) {
        val prevX = -20f + (mi - 1) * (w + 40) / 40f + mpx
        val prevH = (Math.sin((mi - 1) * 0.4 + 1.3) * h * 0.06 + Math.sin((mi - 1) * 0.15 + 0.7) * h * 0.04).toFloat
        val prevY = mountainBaseY - Math.abs(prevH)
        // Fill triangle from prev point to current to base
        shapeBatch.fillRect(prevX, Math.min(prevY, my), mx - prevX + 1, mountainBaseY - Math.min(prevY, my), 0.25f, 0.35f, 0.55f, 0.25f)
      }
      mi += 1
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
      shapeBatch.fillRect(bx, by, bw, bh, 0.08f, 0.06f, 0.16f, 0.85f)
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
    // Near foreground atmospheric overlay (0.15x parallax, very subtle)
    val nearAlpha = 0.04f
    shapeBatch.fillRect(0, h * 0.85f, w, h * 0.15f, 0.05f, 0.02f, 0.12f, nearAlpha)
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
      // Star core — use fillDot for small stars (6 verts vs 60)
      if (size <= 2f) shapeBatch.fillDot(sx, sy, size, sr, sg, sb, brightness)
      else shapeBatch.fillOval(sx, sy, size, size, sr, sg, sb, brightness, 8)
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
    // Near atmospheric haze (warm dust overlay)
    shapeBatch.fillRect(0, h * 0.75f, w, h * 0.25f, 0.85f, 0.7f, 0.45f, 0.04f)
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
    // Near surface light rays (0.15x parallax)
    val nearPx = (camOffX * 0.15).toFloat
    shapeBatch.setAdditiveBlend(true)
    var ri = 0
    while (ri < 5) {
      val rx = ((hash(ri * 11 + 400).toFloat * w + nearPx * 0.5f) % (w + 100)) - 50
      val rayW = 30f + hash(ri * 11 + 401).toFloat * 50f
      val rayA = 0.03f + 0.02f * Math.sin(animationTick * 0.015 + ri * 1.5).toFloat
      shapeBatch.fillRectGradient(rx, 0, rayW, h,
        0.3f, 0.6f, 0.8f, rayA, 0.3f, 0.6f, 0.8f, rayA,
        0.2f, 0.5f, 0.7f, 0f, 0.2f, 0.5f, 0.7f, 0f)
      ri += 1
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

  private def dispatchEntries(entries: mutable.ArrayBuffer[EntityCollector.MutableCellEntry], localVX: Double, localVY: Double): Unit = {
    val size = entries.size
    // Fast path for single-entity cells (no reordering needed)
    if (size == 1) {
      val entry = entries(0)
      entry.entryType match {
        case EntityCollector.TYPE_ITEM => drawSingleItem(entry.ref.asInstanceOf[Item])
        case EntityCollector.TYPE_PROJECTILE => drawSingleProjectile(entry.ref.asInstanceOf[Projectile])
        case EntityCollector.TYPE_PLAYER => drawPlayerInterp(entry.ref.asInstanceOf[Player], entry.vx, entry.vy)
        case EntityCollector.TYPE_LOCAL_PLAYER => drawLocalPlayer(localVX, localVY)
        case EntityCollector.TYPE_LOCAL_DEATH => // handled by drawDeathAnimations()
        case _ =>
      }
      return
    }
    // Two-pass dispatch: shape-only entities first (items, projectiles), then
    // sprite entities (players). Reduces batch switches from up to N per cell to at most 1.
    // Pass 1: items and projectiles (shape-based)
    var i = 0
    while (i < size) {
      val entry = entries(i)
      entry.entryType match {
        case EntityCollector.TYPE_ITEM => drawSingleItem(entry.ref.asInstanceOf[Item])
        case EntityCollector.TYPE_PROJECTILE => drawSingleProjectile(entry.ref.asInstanceOf[Projectile])
        case _ => // skip players in this pass
      }
      i += 1
    }
    // Pass 2: players (shape shadow + sprite body)
    i = 0
    while (i < size) {
      val entry = entries(i)
      entry.entryType match {
        case EntityCollector.TYPE_PLAYER => drawPlayerInterp(entry.ref.asInstanceOf[Player], entry.vx, entry.vy)
        case EntityCollector.TYPE_LOCAL_PLAYER => drawLocalPlayer(localVX, localVY)
        case EntityCollector.TYPE_LOCAL_DEATH => // handled by drawDeathAnimations()
        case _ => // skip items/projectiles in this pass
      }
      i += 1
    }
  }

  // ═══════════════════════════════════════════════════════════════════
  //  PLAYER RENDERING (5c)
  // ═══════════════════════════════════════════════════════════════════

  private def drawShadow(screenX: Double, screenY: Double): Unit = {
    beginShapes()
    val sx = screenX.toFloat + 2f
    val sy = screenY.toFloat + 1f
    // 3-layer composite shadow for depth
    // Outer soft blur (large, very transparent)
    shapeBatch.fillOvalSoft(sx, sy, 28f, 11f, 0.02f, 0.01f, 0.05f, 0.18f, 0f, 16)
    // Mid contact shadow (medium, darker)
    shapeBatch.fillOvalSoft(sx, sy, 22f, 9f, 0.02f, 0.01f, 0.05f, 0.32f, 0f, 14)
    // Inner hard contact (small, darkest)
    shapeBatch.fillOvalSoft(sx, sy, 14f, 6f, 0.01f, 0.005f, 0.03f, 0.45f, 0.12f, 12)
    // Subtle bright ring for visibility on dark terrain
    shapeBatch.strokeOval(sx, sy, 24f, 10f, 1f, 0.7f, 0.7f, 0.8f, 0.12f, 14)
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

    // Player light — color-matched to character, charge-reactive
    intToRGB(player.getColorRGB)
    val playerLR = _rgb_r; val playerLG = _rgb_g; val playerLB = _rgb_b
    val chargeIntensity = if (player.getChargeLevel > 0) 0.2f + (player.getChargeLevel / 100f) * 0.4f else 0.2f
    val playerLightRadius = 50f + (player.getChargeLevel / 100f) * 40f
    lightSystem.addLight(screenX.toFloat, spriteCenter.toFloat, playerLightRadius, playerLR, playerLG, playerLB, chargeIntensity)
    // Shield blue glow
    if (player.hasShield) lightSystem.addLight(screenX.toFloat, spriteCenter.toFloat, 60f, 0.3f, 0.5f, 1f, 0.15f)
    // Burn orange glow
    if (player.isBurning) lightSystem.addLight(screenX.toFloat, spriteCenter.toFloat, 50f, 1f, 0.45f, 0.05f, 0.15f)
    // Frozen blue glow
    if (player.isFrozen) lightSystem.addLight(screenX.toFloat, spriteCenter.toFloat, 45f, 0.5f, 0.8f, 1f, 0.12f)

    // Pre-player non-additive effects
    if (!playerIsPhased) {
      if (player.hasShield) drawShieldBubble(screenX, spriteCenter)
    }

    drawShadow(screenX, screenY)

    // Remote player footstep dust (sparse, distance-culled)
    val dustDx = wx - camera.visualX; val dustDy = wy - camera.visualY
    if (isMoving && dustDx * dustDx + dustDy * dustDy < 64 && rng.nextFloat() < 0.03f) {
      val sx = screenX.toFloat; val sy = screenY.toFloat
      combatParticles.emit(
        sx + rng.nextFloat() * 6f - 3f, sy + rng.nextFloat() * 2f,
        rng.nextFloat() * 6f - 3f, -(2f + rng.nextFloat() * 3f),
        plife = 0.35f + rng.nextFloat() * 0.2f,
        pr = 0.55f, pg = 0.50f, pb = 0.42f, palpha = 0.18f,
        psize = 1.5f + rng.nextFloat() * 1.5f,
        pgravity = 5f, soft = true
      )
    }

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
      // Idle breathing — subtle 2% scale oscillation when standing still
      val breathScale = if (!isMoving) 1f + 0.02f * Math.sin(_animTickF * 0.05 + playerId.hashCode() * 0.1).toFloat else 1f
      // Hit scale pulse — sprites grow 18% on impact then shrink back
      val hitScale = breathScale + hitFlash * 0.18f
      val spriteW = displaySz.toFloat * hitScale
      val spriteH = displaySz.toFloat * hitScale
      val drawX = (screenX - spriteW / 2.0).toFloat
      val drawY = (screenY - spriteH).toFloat
      beginSprites()
      // Movement afterimage — ghost sprite trailing behind nearby moving players
      val aimDx = wx - camera.visualX; val aimDy = wy - camera.visualY
      if (isMoving && hitFlash < 0.1f && aimDx * aimDx + aimDy * aimDy < 36) {
        spriteBatch.draw(region, drawX + 1.5f, drawY + 0.8f, spriteW, spriteH, 0.6f, 0.6f, 0.8f, 0.12f)
      }
      // Player outline: glow brighter on hit
      val isLocal = playerId == client.getLocalPlayerId
      val outR = if (isLocal) 1f else 0.8f + hitFlash * 0.2f
      val outG = if (isLocal) 1f else 0.2f
      val outB = if (isLocal) 1f else 0.2f
      val outA = 0.5f + hitFlash * 0.3f
      val outOff = 1f + hitFlash * 0.5f
      spriteBatch.draw(region, drawX - outOff, drawY, spriteW, spriteH, outR, outG, outB, outA)
      spriteBatch.draw(region, drawX + outOff, drawY, spriteW, spriteH, outR, outG, outB, outA)
      spriteBatch.draw(region, drawX, drawY - outOff, spriteW, spriteH, outR, outG, outB, outA)
      spriteBatch.draw(region, drawX, drawY + outOff, spriteW, spriteH, outR, outG, outB, outA)
      spriteBatch.draw(region, drawX, drawY, spriteW, spriteH, 1f, g, b, alpha)
    }

    // Post-player non-additive effects
    if (player.isFrozen) drawFrozenEffect(screenX, spriteCenter)
    if (player.isRooted) drawRootedEffect(screenX, spriteCenter)
    if (player.isSlowed) drawSlowedEffect(screenX, spriteCenter)
    if (player.hasSpeedBoost) drawSpeedBoostEffect(screenX, spriteCenter)

    // Defer additive effects to batch pass
    var fxFlags = 0
    if (player.hasGemBoost && !playerIsPhased) fxFlags |= FX_GEM
    if (player.getChargeLevel > 0 && !playerIsPhased) fxFlags |= FX_CHARGE
    if (playerIsPhased) fxFlags |= FX_PHASED
    if (player.isBurning) fxFlags |= FX_BURN
    fxFlags |= FX_HIT // hitTime checked inside Inner method
    if (fxFlags != 0) {
      val di = _dafCount
      if (di < MAX_DEFERRED_ADD_FX) {
        _dafCX(di) = screenX; _dafCY(di) = spriteCenter
        _dafFlags(di) = fxFlags; _dafChargeLevel(di) = player.getChargeLevel
        _dafHitTime(di) = hitTime
        // Themed hit FX inputs — color and screen-space direction of the hit projectile
        val hitColorRGB = client.getPlayerHitColorRGB(playerId)
        intToRGB(if (hitColorRGB != 0) hitColorRGB else player.getColorRGB)
        _dafHitColorR(di) = _rgb_r; _dafHitColorG(di) = _rgb_g; _dafHitColorB(di) = _rgb_b
        val hDx = client.getPlayerHitDx(playerId).toDouble
        val hDy = client.getPlayerHitDy(playerId).toDouble
        // Convert world dx/dy to screen-space using same isometric mapping (2:1, dy halved)
        val sdx = (hDx - hDy).toFloat
        val sdy = ((hDx + hDy) * 0.5f).toFloat
        val sLen = Math.sqrt(sdx * sdx + sdy * sdy).toFloat
        if (sLen > 0.01f) {
          _dafHitDirX(di) = sdx / sLen
          _dafHitDirY(di) = sdy / sLen
        } else {
          _dafHitDirX(di) = 1f; _dafHitDirY(di) = 0f
        }
        _dafCount += 1
      }
    }

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

    // Local player light — color-matched, charge-reactive
    intToRGB(client.getLocalColorRGB)
    val lpLR = _rgb_r; val lpLG = _rgb_g; val lpLB = _rgb_b
    val localChargeLevel = client.getChargeLevel
    val localChargeIntensity = if (localChargeLevel > 0) 0.2f + (localChargeLevel / 100f) * 0.4f else 0.2f
    val localLightRadius = 50f + (localChargeLevel / 100f) * 40f
    lightSystem.addLight(screenX.toFloat, spriteCenter.toFloat, localLightRadius, lpLR, lpLG, lpLB, localChargeIntensity)
    if (client.hasShield) lightSystem.addLight(screenX.toFloat, spriteCenter.toFloat, 60f, 0.3f, 0.5f, 1f, 0.15f)
    if (client.isBurning) lightSystem.addLight(screenX.toFloat, spriteCenter.toFloat, 50f, 1f, 0.45f, 0.05f, 0.15f)
    if (client.isFrozen) lightSystem.addLight(screenX.toFloat, spriteCenter.toFloat, 45f, 0.5f, 0.8f, 1f, 0.12f)

    if (!localIsPhased) {
      if (client.hasShield) drawShieldBubble(screenX, spriteCenter)
    }

    // Dash afterimage
    if (client.isSwooping) drawSwoopTrail(wx, wy, displaySz)

    // Local player ground indicator — pulsing gold ring + soft glow
    {
      val sx = screenX.toFloat + 2f
      val sy = screenY.toFloat + 1f
      val pulse = (0.45f + 0.20f * Math.sin(_animTickF * 0.08).toFloat)
      beginShapes()
      // Soft glow under feet
      shapeBatch.fillOvalSoft(sx, sy, 34f, 14f, 1f, 0.85f, 0.3f, (0.08f + 0.06f * pulse), 0f, 16)
      // Outer bright ring
      shapeBatch.strokeOval(sx, sy, 30f, 12f, 1.5f, 1f, 0.85f, 0.3f, pulse, 16)
      // Inner tighter ring
      shapeBatch.strokeOval(sx, sy, 26f, 10f, 0.8f, 1f, 0.85f, 0.3f, pulse * 0.6f, 14)
    }

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
      // Idle breathing — subtle 2% scale oscillation when standing still
      val localBreathScale = if (!client.getIsMoving) 1f + 0.02f * Math.sin(_animTickF * 0.05).toFloat else 1f
      // Hit scale pulse — sprites grow 18% on impact then shrink back
      val hitScale = localBreathScale + localHitFlash * 0.18f
      val spriteW = displaySz.toFloat * hitScale
      val spriteH = displaySz.toFloat * hitScale
      val drawX = (screenX - spriteW / 2.0).toFloat
      val drawY = (screenY - spriteH).toFloat
      beginSprites()
      // Movement afterimage — ghost sprite trailing behind moving player
      if (client.getIsMoving && localHitFlash < 0.1f) {
        spriteBatch.draw(region, drawX + 1.5f, drawY + 0.8f, spriteW, spriteH, 0.6f, 0.6f, 0.8f, 0.12f)
      }
      // Player outline: glow brighter on hit (local player = white)
      val outR = 1f; val outG = 1f; val outB = 1f
      val outA = 0.5f + localHitFlash * 0.3f
      val outOff = 1f + localHitFlash * 0.5f
      spriteBatch.draw(region, drawX - outOff, drawY, spriteW, spriteH, outR, outG, outB, outA)
      spriteBatch.draw(region, drawX + outOff, drawY, spriteW, spriteH, outR, outG, outB, outA)
      spriteBatch.draw(region, drawX, drawY - outOff, spriteW, spriteH, outR, outG, outB, outA)
      spriteBatch.draw(region, drawX, drawY + outOff, spriteW, spriteH, outR, outG, outB, outA)
      spriteBatch.draw(region, drawX, drawY, spriteW, spriteH, 1f, g, b, alpha)
    }

    // Small downward-pointing chevron above name area
    {
      val chevPulse = (0.50f + 0.25f * Math.sin(_animTickF * 0.08).toFloat)
      val cx = screenX.toFloat
      val cy = (screenY - displaySz - 14).toFloat
      val chevW = 8f
      val chevH = 5f
      beginShapes()
      shapeBatch.strokeLine(cx - chevW, cy, cx, cy + chevH, 1.8f, 1f, 0.85f, 0.3f, chevPulse)
      shapeBatch.strokeLine(cx + chevW, cy, cx, cy + chevH, 1.8f, 1f, 0.85f, 0.3f, chevPulse)
    }

    // Non-additive post-player effects
    if (client.isFrozen) drawFrozenEffect(screenX, spriteCenter)
    if (client.isRooted) drawRootedEffect(screenX, spriteCenter)
    if (client.isSlowed) drawSlowedEffect(screenX, spriteCenter)
    if (client.hasSpeedBoost) drawSpeedBoostEffect(screenX, spriteCenter)

    // Defer additive effects to batch pass
    var fxFlags = 0
    if (client.hasGemBoost && !localIsPhased) fxFlags |= FX_GEM
    if (client.getChargeLevel > 0 && !localIsPhased) fxFlags |= FX_CHARGE
    if (localIsPhased) fxFlags |= FX_PHASED
    if (client.isBurning) fxFlags |= FX_BURN
    fxFlags |= FX_HIT | FX_CAST
    if (fxFlags != 0) {
      val di = _dafCount
      if (di < MAX_DEFERRED_ADD_FX) {
        _dafCX(di) = screenX; _dafCY(di) = spriteCenter
        _dafFlags(di) = fxFlags; _dafChargeLevel(di) = client.getChargeLevel
        _dafHitTime(di) = localHitTime
        val lpId = client.getLocalPlayerId
        val hitColorRGB = client.getPlayerHitColorRGB(lpId)
        intToRGB(if (hitColorRGB != 0) hitColorRGB else client.getLocalColorRGB)
        _dafHitColorR(di) = _rgb_r; _dafHitColorG(di) = _rgb_g; _dafHitColorB(di) = _rgb_b
        val hDx = client.getPlayerHitDx(lpId).toDouble
        val hDy = client.getPlayerHitDy(lpId).toDouble
        val sdx = (hDx - hDy).toFloat
        val sdy = ((hDx + hDy) * 0.5f).toFloat
        val sLen = Math.sqrt(sdx * sdx + sdy * sdy).toFloat
        if (sLen > 0.01f) {
          _dafHitDirX(di) = sdx / sLen
          _dafHitDirY(di) = sdy / sLen
        } else {
          _dafHitDirX(di) = 1f; _dafHitDirY(di) = 0f
        }
        _dafCount += 1
      }
    }

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
    val x = cx.toFloat; val y = cy.toFloat
    val tick = animationTick

    // Large pulsing frost ground circle (35px radius, unmissable)
    val groundPulse = (0.7 + 0.3 * Math.sin(tick * 0.05)).toFloat
    shapeBatch.fillOvalSoft(x, y + 8f, 38f, 18f, 0.55f, 0.8f, 1f, 0.18f * groundPulse, 0f, 18)
    shapeBatch.fillOvalSoft(x, y + 8f, 30f, 14f, 0.65f, 0.88f, 1f, 0.22f * groundPulse, 0f, 16)
    shapeBatch.fillOval(x, y + 8f, 22f, 10f, 0.75f, 0.93f, 1f, 0.12f, 14)

    // 8 crystalline ice shards radiating inward — thick multi-layer strokes
    var i = 0
    while (i < 8) {
      val baseAngle = (i * Math.PI / 4.0 + tick * 0.006).toFloat
      val dist = 22f + Math.sin(tick * 0.04 + i * 0.9).toFloat * 3f
      val shardX = x + dist * Math.cos(baseAngle).toFloat
      val shardY = y + dist * Math.sin(baseAngle).toFloat * 0.55f
      val shardLen = 16f + Math.sin(tick * 0.03 + i * 1.7).toFloat * 2f
      val innerDist = 4f
      val innerX = x + innerDist * Math.cos(baseAngle).toFloat
      val innerY = y + innerDist * Math.sin(baseAngle).toFloat * 0.55f

      // Dark blue outline (3px thick)
      shapeBatch.strokeLine(shardX, shardY, innerX, innerY, 5f, 0.15f, 0.3f, 0.55f, 0.6f)
      // Ice blue body (2px)
      shapeBatch.strokeLine(shardX, shardY, innerX, innerY, 3.2f, 0.5f, 0.78f, 1f, 0.75f)
      // White highlight edge (0.8px offset)
      shapeBatch.strokeLine(shardX + 0.6f, shardY - 0.6f, innerX + 0.6f, innerY - 0.6f, 1.2f, 0.9f, 0.96f, 1f, 0.55f)

      // Crystal tip facet (larger, brighter)
      shapeBatch.fillOval(shardX, shardY, 4.5f, 3.8f, 0.6f, 0.85f, 1f, 0.6f, 6)

      // Icicle drip at shard tip (tiny elongated dot below tip)
      val dripPhase = (tick * 0.06f + i * 1.4f) % 6.28f
      val dripLen = 2.5f + Math.sin(dripPhase).toFloat * 1.5f
      val dripX = shardX + Math.cos(baseAngle).toFloat * 1.5f
      val dripY = shardY + Math.sin(baseAngle).toFloat * 0.55f + dripLen
      shapeBatch.strokeLineSoft(shardX, shardY, dripX, dripY + 2f, 1.2f, 0.7f, 0.9f, 1f, 0.35f)
      shapeBatch.fillOval(dripX, dripY + 2.5f, 1.5f, 2f, 0.8f, 0.95f, 1f, 0.5f, 4)

      // Sparkle on crystal tips (cycling which 4 flash)
      val sparkle = (0.2 + 0.8 * Math.sin(tick * 0.14 + i * 2.3)).toFloat
      if (sparkle > 0.55f) {
        val sparkAlpha = (sparkle - 0.55f) / 0.45f * 0.8f
        shapeBatch.fillOval(shardX, shardY, 3f, 3f, 1f, 1f, 1f, sparkAlpha, 4)
        // Cross sparkle lines
        shapeBatch.strokeLine(shardX - 3f, shardY, shardX + 3f, shardY, 0.8f, 1f, 1f, 1f, sparkAlpha * 0.6f)
        shapeBatch.strokeLine(shardX, shardY - 3f, shardX, shardY + 3f, 0.8f, 1f, 1f, 1f, sparkAlpha * 0.6f)
      }
      i += 1
    }

    // 8 frost particles drifting in slow orbit
    { var p = 0; while (p < 8) {
      val orbitAngle = tick * 0.018f + p * Math.PI.toFloat * 2f / 8f
      val orbitR = 26f + Math.sin(tick * 0.03 + p * 1.1).toFloat * 4f
      val px = x + orbitR * Math.cos(orbitAngle).toFloat
      val py = y + 4f + orbitR * Math.sin(orbitAngle).toFloat * 0.4f
      val pAlpha = (0.4 + 0.3 * Math.sin(tick * 0.06 + p * 1.9)).toFloat
      shapeBatch.fillOval(px, py, 2.5f, 2.5f, 0.8f, 0.95f, 1f, pAlpha, 6)
      // Tiny trail behind particle
      val prevAngle = orbitAngle - 0.3f
      val prevX = x + orbitR * Math.cos(prevAngle).toFloat
      val prevY = y + 4f + orbitR * Math.sin(prevAngle).toFloat * 0.4f
      shapeBatch.strokeLineSoft(prevX, prevY, px, py, 1.2f, 0.6f, 0.85f, 1f, pAlpha * 0.3f)
    ; p += 1 } }

    // Inner ice glow pulse (strong core glow)
    val pulse = (0.5 + 0.5 * Math.sin(tick * 0.07)).toFloat
    shapeBatch.fillOvalSoft(x, y, 14f, 11f, 0.6f, 0.88f, 1f, 0.25f * pulse, 0f, 14)
    shapeBatch.fillOval(x, y, 8f, 6f, 0.8f, 0.95f, 1f, 0.15f * pulse, 10)
  }

  private def drawRootedEffect(cx: Double, cy: Double): Unit = {
    beginShapes()
    val x = cx.toFloat; val y = cy.toFloat
    val tick = animationTick

    // Large dark brown disturbed earth oval (30px wide)
    shapeBatch.fillOvalSoft(x, y + 10f, 34f, 15f, 0.22f, 0.15f, 0.06f, 0.25f, 0f, 16)
    shapeBatch.fillOval(x, y + 10f, 28f, 12f, 0.3f, 0.22f, 0.1f, 0.2f, 14)
    shapeBatch.strokeOval(x, y + 10f, 30f, 13f, 2.5f, 0.2f, 0.14f, 0.05f, 0.45f, 14)

    // Ground cracks radiating from earth patch (6 crack lines)
    { var c = 0; while (c < 6) {
      val crackAngle = c * Math.PI.toFloat / 3f + 0.3f
      val crackLen = 14f + Math.sin(tick * 0.02 + c * 1.3).toFloat * 3f
      val crackX1 = x + 12f * Math.cos(crackAngle).toFloat
      val crackY1 = y + 10f + 5f * Math.sin(crackAngle).toFloat
      val crackX2 = x + (12f + crackLen) * Math.cos(crackAngle).toFloat
      val crackY2 = y + 10f + (5f + crackLen * 0.4f) * Math.sin(crackAngle).toFloat
      shapeBatch.strokeLine(crackX1, crackY1, crackX2, crackY2, 1.8f, 0.18f, 0.12f, 0.04f, 0.4f)
      // Branch crack
      val branchAngle = crackAngle + 0.5f
      val bx = crackX2 + 5f * Math.cos(branchAngle).toFloat
      val by = crackY2 + 2f * Math.sin(branchAngle).toFloat
      shapeBatch.strokeLine(crackX2, crackY2, bx, by, 1.2f, 0.18f, 0.12f, 0.04f, 0.3f)
    ; c += 1 } }

    // Pulsing green glow at base
    val basePulse = (0.5 + 0.5 * Math.sin(tick * 0.08)).toFloat
    shapeBatch.fillOvalSoft(x, y + 10f, 22f, 10f, 0.2f, 0.6f, 0.1f, 0.12f * basePulse, 0f, 12)

    // 6 thick vine tendrils climbing up with 4 curve segments each
    var i = 0
    while (i < 6) {
      val baseAngle = (i * Math.PI / 3.0 + tick * 0.012).toFloat
      val dist = 15f
      val rootBaseX = x + dist * Math.cos(baseAngle).toFloat
      val rootBaseY = y + 10f + dist * Math.sin(baseAngle).toFloat * 0.3f
      // Curved tendril climbing up (4 segments for more sinuous shape)
      val wave = Math.sin(tick * 0.04 + i * 1.7).toFloat * 4f
      val wave2 = Math.cos(tick * 0.035 + i * 2.1).toFloat * 2.5f
      val mid1X = rootBaseX + wave * 0.4f
      val mid1Y = rootBaseY - 7f
      val mid2X = rootBaseX - wave * 0.8f
      val mid2Y = rootBaseY - 15f
      val mid3X = rootBaseX + wave2
      val mid3Y = rootBaseY - 22f
      val tipX = rootBaseX + wave * 0.2f + Math.sin(tick * 0.06 + i * 2.3).toFloat * 3f
      val tipY = rootBaseY - 28f - Math.sin(tick * 0.03 + i).toFloat * 2f

      // Dark outline (5px thick) for each segment
      shapeBatch.strokeLine(rootBaseX, rootBaseY, mid1X, mid1Y, 5.5f, 0.1f, 0.06f, 0.01f, 0.65f)
      shapeBatch.strokeLine(mid1X, mid1Y, mid2X, mid2Y, 4.8f, 0.1f, 0.06f, 0.01f, 0.6f)
      shapeBatch.strokeLine(mid2X, mid2Y, mid3X, mid3Y, 3.8f, 0.1f, 0.06f, 0.01f, 0.55f)
      shapeBatch.strokeLine(mid3X, mid3Y, tipX, tipY, 3f, 0.1f, 0.06f, 0.01f, 0.45f)
      // Green body fill (3.5px)
      shapeBatch.strokeLine(rootBaseX, rootBaseY, mid1X, mid1Y, 3.8f, 0.28f, 0.58f, 0.14f, 0.75f)
      shapeBatch.strokeLine(mid1X, mid1Y, mid2X, mid2Y, 3.2f, 0.32f, 0.62f, 0.16f, 0.7f)
      shapeBatch.strokeLine(mid2X, mid2Y, mid3X, mid3Y, 2.6f, 0.36f, 0.66f, 0.18f, 0.6f)
      shapeBatch.strokeLine(mid3X, mid3Y, tipX, tipY, 2f, 0.4f, 0.7f, 0.2f, 0.5f)
      // Lighter highlight (1.5px)
      shapeBatch.strokeLine(rootBaseX + 0.5f, rootBaseY, mid1X + 0.5f, mid1Y, 1.5f, 0.5f, 0.78f, 0.28f, 0.4f)
      shapeBatch.strokeLine(mid1X + 0.5f, mid1Y, mid2X + 0.5f, mid2Y, 1.2f, 0.5f, 0.78f, 0.28f, 0.35f)

      // Thorns along the vine (2-3 per vine)
      { var t = 0; while (t < 3) {
        val thornFrac = 0.25f + t * 0.25f
        val thornBaseX = rootBaseX + (tipX - rootBaseX) * thornFrac + Math.sin(tick * 0.05 + i + t).toFloat * 1f
        val thornBaseY = rootBaseY + (tipY - rootBaseY) * thornFrac
        val thornDir = if ((i + t) % 2 == 0) 1f else -1f
        val thornTipX = thornBaseX + thornDir * 4f
        val thornTipY = thornBaseY - 2f
        shapeBatch.strokeLine(thornBaseX, thornBaseY, thornTipX, thornTipY, 2f, 0.15f, 0.4f, 0.08f, 0.55f)
        shapeBatch.strokeLine(thornBaseX, thornBaseY, thornTipX, thornTipY, 1f, 0.35f, 0.6f, 0.15f, 0.4f)
      ; t += 1 } }

      // Leaf at vine tip (bigger and more visible)
      val leafAngle = tick * 0.03f + i * 1.5f
      val leafX = tipX + Math.cos(leafAngle).toFloat * 5f
      val leafY = tipY + Math.sin(leafAngle).toFloat * 2.5f
      shapeBatch.fillOval(leafX, leafY, 5f, 3f, 0.22f, 0.5f, 0.1f, 0.65f, 6)
      shapeBatch.fillOval(leafX + 0.5f, leafY - 0.5f, 3f, 1.8f, 0.4f, 0.72f, 0.22f, 0.4f, 4)
      // Second leaf offset
      val leaf2X = tipX - Math.cos(leafAngle + 1f).toFloat * 4f
      val leaf2Y = tipY - Math.sin(leafAngle + 1f).toFloat * 2f - 1f
      shapeBatch.fillOval(leaf2X, leaf2Y, 4f, 2.5f, 0.2f, 0.48f, 0.08f, 0.55f, 6)
      i += 1
    }

    // Small dirt particles kicked up (6 particles)
    { var d = 0; while (d < 6) {
      val driftPhase = (tick * 0.025f + d * 1.05f) % 6.28f
      val driftH = Math.abs(Math.sin(driftPhase)).toFloat * 12f
      val driftX = x + Math.cos(tick * 0.02 + d * 1.8).toFloat * (10f + d * 3f)
      val driftY = y + 8f - driftH
      val dAlpha = (1f - driftH / 12f) * 0.5f
      shapeBatch.fillOval(driftX, driftY, 2f, 1.5f, 0.35f, 0.25f, 0.12f, dAlpha, 4)
    ; d += 1 } }
  }

  private def drawSlowedEffect(cx: Double, cy: Double): Unit = {
    beginShapes()
    val x = cx.toFloat; val y = cy.toFloat
    val tick = animationTick

    // Large pulsing blue-purple tint overlay (25px radius, strong alpha)
    val pulse = (0.6 + 0.4 * Math.sin(tick * 0.07)).toFloat
    shapeBatch.fillOvalSoft(x, y, 28f, 22f, 0.25f, 0.3f, 0.7f, 0.18f * pulse, 0f, 18)
    shapeBatch.fillOval(x, y, 20f, 16f, 0.3f, 0.35f, 0.8f, 0.12f * pulse, 16)

    // Blue frost-like ground tint below feet
    shapeBatch.fillOvalSoft(x, y + 10f, 22f, 8f, 0.35f, 0.45f, 0.9f, 0.15f * pulse, 0f, 12)

    // 8 blue particles in slow orbit with soft trails
    var i = 0
    while (i < 8) {
      val orbitAngle = tick * 0.025f + i * Math.PI.toFloat * 2f / 8f
      val orbitR = 18f + Math.sin(tick * 0.04 + i * 1.3).toFloat * 4f
      val px = x + orbitR * Math.cos(orbitAngle).toFloat
      val py = y + 5f + orbitR * Math.sin(orbitAngle).toFloat * 0.35f
      val particleAlpha = (0.35 + 0.3 * Math.sin(tick * 0.06 + i * 2.1)).toFloat
      shapeBatch.fillOval(px, py, 3.5f, 3f, 0.4f, 0.5f, 0.95f, particleAlpha, 6)
      // Soft trail behind particle (longer)
      val prevAngle1 = orbitAngle - 0.3f
      val prevAngle2 = orbitAngle - 0.6f
      val prevX1 = x + orbitR * Math.cos(prevAngle1).toFloat
      val prevY1 = y + 5f + orbitR * Math.sin(prevAngle1).toFloat * 0.35f
      val prevX2 = x + orbitR * Math.cos(prevAngle2).toFloat
      val prevY2 = y + 5f + orbitR * Math.sin(prevAngle2).toFloat * 0.35f
      shapeBatch.strokeLineSoft(prevX2, prevY2, prevX1, prevY1, 1.5f, 0.3f, 0.4f, 0.85f, particleAlpha * 0.25f)
      shapeBatch.strokeLineSoft(prevX1, prevY1, px, py, 2f, 0.35f, 0.45f, 0.9f, particleAlpha * 0.45f)
      i += 1
    }

    // Animated clock/hourglass symbol above player (bigger, 10px tall, 2px thick strokes, glowing blue)
    val hx = x; val hy = y - 26f
    val hPulse = (0.6 + 0.4 * Math.sin(tick * 0.12)).toFloat
    // Glow behind hourglass
    shapeBatch.fillOvalSoft(hx, hy, 10f, 8f, 0.4f, 0.5f, 1f, 0.12f * hPulse, 0f, 10)
    // Top triangle (bigger)
    shapeBatch.strokeLine(hx - 6f, hy - 6f, hx + 6f, hy - 6f, 2.2f, 0.5f, 0.6f, 1f, 0.6f * hPulse)
    shapeBatch.strokeLine(hx - 6f, hy - 6f, hx, hy, 2.2f, 0.5f, 0.6f, 1f, 0.6f * hPulse)
    shapeBatch.strokeLine(hx + 6f, hy - 6f, hx, hy, 2.2f, 0.5f, 0.6f, 1f, 0.6f * hPulse)
    // Bottom triangle (bigger)
    shapeBatch.strokeLine(hx - 6f, hy + 6f, hx + 6f, hy + 6f, 2.2f, 0.5f, 0.6f, 1f, 0.6f * hPulse)
    shapeBatch.strokeLine(hx - 6f, hy + 6f, hx, hy, 2.2f, 0.5f, 0.6f, 1f, 0.6f * hPulse)
    shapeBatch.strokeLine(hx + 6f, hy + 6f, hx, hy, 2.2f, 0.5f, 0.6f, 1f, 0.6f * hPulse)
    // Sand grains falling through center
    val sandY = hy - 4f + ((tick * 0.15f) % 8f)
    shapeBatch.fillOval(hx, sandY, 1.5f, 1.5f, 0.7f, 0.75f, 1f, 0.5f * hPulse, 4)
    val sandY2 = hy - 4f + ((tick * 0.15f + 4f) % 8f)
    shapeBatch.fillOval(hx + 1f, sandY2, 1f, 1f, 0.7f, 0.75f, 1f, 0.4f * hPulse, 4)

    // Trailing afterimage effect: 3 semi-transparent blue diamond shapes at 10px, 18px, 26px behind
    { var a = 0; while (a < 3) {
      val afterDist = 10f + a * 8f
      val afterAlpha = (0.2f - a * 0.055f) * pulse
      val ax = x - afterDist * 0.7f
      val ay = y + afterDist * 0.1f
      val dSize = 5f - a * 0.8f
      _indicatorXs(0) = ax; _indicatorXs(1) = ax + dSize; _indicatorXs(2) = ax; _indicatorXs(3) = ax - dSize
      _indicatorYs(0) = ay - dSize * 1.3f; _indicatorYs(1) = ay; _indicatorYs(2) = ay + dSize * 1.3f; _indicatorYs(3) = ay
      shapeBatch.fillPolygon(_indicatorXs, _indicatorYs, 4, 0.35f, 0.45f, 0.9f, afterAlpha)
    ; a += 1 } }

    // 4 blue energy wisps drifting slowly upward
    { var w = 0; while (w < 4) {
      val wispPhase = (tick * 0.02f + w * 1.57f) % 6.28f
      val wispX = x + Math.sin(tick * 0.03 + w * 2.1).toFloat * 10f
      val wispY = y - 6f - Math.abs(Math.sin(wispPhase)).toFloat * 20f
      val wispAlpha = (1f - Math.abs(Math.sin(wispPhase)).toFloat) * 0.35f
      shapeBatch.fillOvalSoft(wispX, wispY, 3f, 4f, 0.4f, 0.55f, 1f, wispAlpha, 0f, 6)
      shapeBatch.fillOval(wispX, wispY, 1.5f, 2f, 0.6f, 0.7f, 1f, wispAlpha * 1.2f, 4)
    ; w += 1 } }
  }

  private def drawBurnEffect(cx: Double, cy: Double): Unit = {
    beginShapes()
    val x = cx.toFloat; val y = cy.toFloat
    val tick = animationTick

    shapeBatch.setAdditiveBlend(true)
    drawBurnEffectCore(x, y, tick)
    shapeBatch.setAdditiveBlend(false)
  }

  /** Core burn visuals shared by drawBurnEffect and drawBurnEffectInner. */
  private def drawBurnEffectCore(x: Float, y: Float, tick: Int): Unit = {
    // BRIGHT base glow (25px radius, strong alpha)
    val basePulse = (0.6 + 0.4 * Math.sin(tick * 0.1)).toFloat
    shapeBatch.fillOvalSoft(x, y + 4f, 28f, 16f, 1f, 0.5f, 0.05f, 0.2f * basePulse, 0f, 14)
    shapeBatch.fillOvalSoft(x, y + 4f, 20f, 11f, 1f, 0.65f, 0.1f, 0.15f * basePulse, 0f, 12)

    // Occasional bright flash pulse at base (every ~60 ticks)
    val flashPhase = (tick % 60).toFloat / 60f
    if (flashPhase < 0.1f) {
      val flashAlpha = (1f - flashPhase / 0.1f) * 0.3f
      shapeBatch.fillOvalSoft(x, y + 2f, 30f, 18f, 1f, 0.8f, 0.3f, flashAlpha, 0f, 16)
    }

    // 12 flame tongues — each with dark red outline + orange body + yellow tip
    var i = 0
    while (i < 12) {
      val angle = (tick * 0.14 + i * 0.5236).toFloat  // 2*PI/12 spacing
      val dist = 10f + Math.sin(tick * 0.1 + i * 0.8).toFloat * 6f
      val rise = (tick * 0.55f + i * 2.2f) % 18f
      val lifeAlpha = 1f - rise / 18f
      val fx = x + dist * Math.cos(angle).toFloat
      val fy = y + dist * Math.sin(angle).toFloat * 0.45f - rise
      val flameSz = 4.5f + Math.sin(tick * 0.18 + i * 1.7).toFloat * 2.5f
      val flameH = flameSz * 1.6f
      // Dark red outline
      shapeBatch.fillOval(fx, fy, flameSz + 2f, flameH + 2f, 0.7f, 0.12f, 0.02f, 0.3f * lifeAlpha, 8)
      // Orange body
      shapeBatch.fillOval(fx, fy, flameSz + 0.5f, flameH + 0.5f, 1f, 0.45f, 0.06f, 0.45f * lifeAlpha, 8)
      // Yellow-white tip (upper portion)
      shapeBatch.fillOval(fx, fy - flameH * 0.2f, flameSz * 0.5f, flameH * 0.5f, 1f, 0.85f, 0.25f, 0.5f * lifeAlpha, 6)
      // White-hot core at flame base
      if (rise < 5f) {
        shapeBatch.fillOval(fx, fy + flameH * 0.3f, flameSz * 0.3f, flameH * 0.25f, 1f, 0.95f, 0.7f, 0.35f * lifeAlpha, 4)
      }
      i += 1
    }

    // 10 rising embers with drift and color variation (white-hot -> orange -> red as they rise)
    { var e = 0; while (e < 10) {
      val et = ((tick * 0.035 + e * 0.1) % 1.0).toFloat
      val drift = Math.sin(tick * 0.07 + e * 2.3).toFloat * 10f * et
      val drift2 = Math.cos(tick * 0.05 + e * 1.7).toFloat * 4f * et
      val ex = x + drift + drift2
      val ey = y - et * 35f - 4f
      val emberSz = 2.5f + (1f - et) * 2f
      // Color transitions from white-hot to orange to red
      val eR = 1f
      val eG = if (et < 0.3f) 0.9f + (1f - et / 0.3f) * 0.1f else 0.5f * (1f - (et - 0.3f) / 0.7f)
      val eB = if (et < 0.2f) 0.6f * (1f - et / 0.2f) else 0f
      shapeBatch.fillOval(ex, ey, emberSz, emberSz, eR, eG, eB, 0.6f * (1f - et), 6)
      // Tiny ember trail
      shapeBatch.strokeLineSoft(ex, ey + 3f, ex - drift * 0.05f, ey + 6f, 1f, eR, eG * 0.8f, eB, 0.25f * (1f - et))
    ; e += 1 } }

    // 6 heat shimmer waves above
    { var s = 0; while (s < 6) {
      val shimY = y - 18f - s * 6f
      val shimX = x + Math.sin(tick * 0.07 + s * 1.5).toFloat * 8f
      val shimR = 10f + s * 4f
      val shimAlpha = 0.06f * (1f - s / 6f)
      shapeBatch.fillOvalSoft(shimX, shimY, shimR, shimR * 0.5f, 1f, 0.55f, 0.12f, shimAlpha, 0f, 8)
    ; s += 1 } }

    // 4 smoke wisps at top (dark gray rising puffs)
    { var sm = 0; while (sm < 4) {
      val smokePhase = ((tick * 0.02f + sm * 0.25f) % 1f)
      val smokeX = x + Math.sin(tick * 0.04 + sm * 2.5).toFloat * 7f
      val smokeY = y - 28f - smokePhase * 22f
      val smokeR = 5f + smokePhase * 6f
      val smokeAlpha = (1f - smokePhase) * 0.15f
      shapeBatch.fillOvalSoft(smokeX, smokeY, smokeR, smokeR * 0.7f, 0.3f, 0.25f, 0.22f, smokeAlpha, 0f, 8)
    ; sm += 1 } }
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
    drawHitEffectCore(x, y, progress, fadeOut, hitTime, 1f, 0.7f, 0.25f, 1f, 0f)
    shapeBatch.setAdditiveBlend(false)
  }

  /** Core hit visuals shared by drawHitEffect and drawHitEffectInner.
   *  hr/hg/hb is the projectile color (themed); ddx/ddy is the screen-space unit direction
   *  the projectile was traveling (for directional impact streak / knockback arrow). */
  private def drawHitEffectCore(x: Float, y: Float, progress: Double, fadeOut: Float, hitTime: Long,
                                hr: Float, hg: Float, hb: Float, ddx: Float, ddy: Float): Unit = {
    // Brighter, fuller hit color (mix with white so themed flash still pops)
    val br = Math.min(1f, hr * 0.4f + 0.6f)
    val bg = Math.min(1f, hg * 0.4f + 0.6f)
    val bb = Math.min(1f, hb * 0.4f + 0.6f)

    // White-out flash overlay (longer + bigger — unmistakable IMPACT FRAME, first 24% / ~120ms)
    if (progress < 0.24) {
      val flashAlpha = 0.55f * (1f - (progress / 0.24).toFloat)
      shapeBatch.fillOval(x, y, 56f, 42f, 1f, 1f, 1f, flashAlpha, 18)
      shapeBatch.fillOval(x, y, 38f, 28f, 1f, 1f, 0.95f, flashAlpha * 1.2f, 14)
      shapeBatch.fillOval(x, y, 20f, 14f, 1f, 1f, 1f, flashAlpha * 1.5f, 10)
    }

    // Themed central glow burst (3 layers, projectile-colored)
    shapeBatch.fillOvalSoft(x, y, 50f * fadeOut, 38f * fadeOut, hr, hg, hb, 0.45f * fadeOut, 0f, 18)
    shapeBatch.fillOvalSoft(x, y, 32f * fadeOut, 22f * fadeOut, br, bg, bb, 0.5f * fadeOut, 0f, 14)
    shapeBatch.fillOval(x, y, 16f * fadeOut, 12f * fadeOut, 1f, 1f, 1f, 0.45f * fadeOut, 10)

    // Directional IMPACT STREAK — a fat slash that points along the projectile direction
    // (so the hit player can tell where the shot came from)
    if (progress < 0.5 && (ddx * ddx + ddy * ddy) > 0.01f) {
      val streakProgress = (progress / 0.5).toFloat
      val streakFade = 1f - streakProgress
      val streakLen = 38f * (0.5f + streakProgress * 0.8f)
      val backLen = 18f
      val sx0 = x - ddx * backLen
      val sy0 = y - ddy * backLen * 0.6f
      val sx1 = x + ddx * streakLen
      val sy1 = y + ddy * streakLen * 0.6f
      // Outer halo
      shapeBatch.strokeLineSoft(sx0, sy0, sx1, sy1, 12f, hr, hg, hb, 0.45f * streakFade)
      // Mid layer
      shapeBatch.strokeLineSoft(sx0, sy0, sx1, sy1, 6f, br, bg, bb, 0.7f * streakFade)
      // Bright white core
      shapeBatch.strokeLine(sx0, sy0, sx1, sy1, 2.5f, 1f, 1f, 1f, 0.85f * streakFade)
      // Streak head — bright orb at the impact entry side
      shapeBatch.fillOval(sx1, sy1, 7f * streakFade, 5.5f * streakFade, 1f, 1f, 1f, 0.85f * streakFade, 10)
    }

    // 12 directional sparks with thick trailing streaks — themed
    val sparkDist = 12f + 40f * progress.toFloat
    val sparkSz = 5.5f * fadeOut
    var i = 0
    while (i < 12) {
      val angle = i * 0.5236f + hitTime * 0.001f // 30 degree spacing
      val jitter = Math.sin(hitTime * 0.003 + i * 1.7).toFloat * 0.15f
      val finalAngle = angle + jitter
      val sx = x + math.cos(finalAngle).toFloat * sparkDist
      val sy = y + math.sin(finalAngle).toFloat * sparkDist * 0.6f
      val trailStartDist = sparkDist * 0.3f
      val trailX = x + math.cos(finalAngle).toFloat * trailStartDist
      val trailY = y + math.sin(finalAngle).toFloat * trailStartDist * 0.6f
      shapeBatch.strokeLineSoft(trailX, trailY, sx, sy, 3.5f, hr, hg, hb, 0.35f * fadeOut)
      shapeBatch.strokeLine(trailX, trailY, sx, sy, 1.5f, br, bg, bb, 0.55f * fadeOut)
      shapeBatch.fillOval(sx, sy, sparkSz, sparkSz * 0.8f, br, bg, bb, 0.7f * fadeOut, 6)
      shapeBatch.fillOval(sx, sy, sparkSz * 0.5f, sparkSz * 0.4f, 1f, 1f, 1f, 0.55f * fadeOut, 4)
      i += 1
    }

    // PRIMARY shockwave ring — thick, themed, expands fast (impact ring)
    val ringProgress = progress * 0.6
    if (ringProgress < 1.0) {
      val ringR = (10f + 50f * ringProgress.toFloat)
      val ringAlpha = 0.55f * (1f - ringProgress.toFloat)
      shapeBatch.strokeOval(x, y, ringR, ringR * 0.65f, 3.5f, hr, hg, hb, ringAlpha, 22)
      shapeBatch.strokeOval(x, y, ringR * 0.9f, ringR * 0.6f, 2f, br, bg, bb, ringAlpha * 0.7f, 20)
      shapeBatch.strokeOval(x, y, ringR * 0.78f, ringR * 0.5f, 1f, 1f, 1f, ringAlpha * 0.5f, 18)
    }

    // SECONDARY flash ring — adds a "double pulse" for clarity (35-65% progress)
    if (progress > 0.35 && progress < 0.65) {
      val ring2Phase = ((progress - 0.35) / 0.3).toFloat
      val ring2R = 5f + 36f * ring2Phase
      val ring2Alpha = 0.35f * (1f - ring2Phase)
      shapeBatch.strokeOval(x, y, ring2R, ring2R * 0.65f, 2.5f, hr, hg, hb, ring2Alpha, 18)
      shapeBatch.strokeOval(x, y, ring2R * 0.7f, ring2R * 0.45f, 1.4f, 1f, 1f, 1f, ring2Alpha * 0.6f, 16)
    }

    // 8 debris chunks flying outward — themed color
    { var d = 0; while (d < 8) {
      val debrisAngle = d * 0.785f + hitTime * 0.002f // 45 degree spacing
      val debrisDist = 6f + 38f * progress.toFloat + Math.sin(d * 2.3).toFloat * 6f
      val debrisGravity = progress.toFloat * progress.toFloat * 18f
      val dx = x + math.cos(debrisAngle).toFloat * debrisDist
      val dy = y + math.sin(debrisAngle).toFloat * debrisDist * 0.5f + debrisGravity
      val debrisSz = 3.5f * fadeOut
      val debrisAlpha = 0.55f * fadeOut
      shapeBatch.fillRect(dx - debrisSz * 0.5f, dy - debrisSz * 0.5f, debrisSz, debrisSz,
        br, bg, bb, debrisAlpha)
      // Bright pip on each debris
      shapeBatch.fillRect(dx - 0.5f, dy - 0.5f, 1f, 1f, 1f, 1f, 1f, debrisAlpha)
    ; d += 1 } }
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

  // Inner methods for deferred additive rendering (no blend toggle, no beginShapes)
  private def drawGemGlowInner(cx: Double, cy: Double): Unit = {
    val pulse = (0.7 + 0.3 * Math.sin(animationTick * 0.08)).toFloat
    shapeBatch.fillOvalSoft(cx.toFloat, cy.toFloat, 18f, 14f, 0f, 0.9f, 0.8f, 0.12f * pulse, 0f, 16)
  }

  private def drawChargingEffectInner(cx: Double, cy: Double, chargeLevel: Int): Unit = {
    if (chargeLevel <= 0) return
    val pct = chargeLevel / 100f
    val ringR = 12f + pct * 8f
    val pulse = (0.8 + 0.2 * Math.sin(animationTick * 0.15)).toFloat
    shapeBatch.fillOvalSoft(cx.toFloat, cy.toFloat, ringR, ringR * 0.7f, 1f, 0.8f * (1 - pct * 0.5f), 0.2f, 0.1f * pct * pulse, 0f, 20)
  }

  private def drawPhasedEffectInner(cx: Double, cy: Double): Unit = {
    val shimmer = (0.5 + 0.5 * Math.sin(animationTick * 0.12)).toFloat
    shapeBatch.fillOvalSoft(cx.toFloat, cy.toFloat, 16f, 14f, 0.5f, 0.3f, 0.8f, 0.08f * shimmer, 0f, 16)
  }

  private def drawBurnEffectInner(cx: Double, cy: Double): Unit = {
    drawBurnEffectCore(cx.toFloat, cy.toFloat, animationTick)
  }

  private def drawHitEffectInner(cx: Double, cy: Double, hitTime: Long,
                                 hr: Float, hg: Float, hb: Float,
                                 ddx: Float, ddy: Float): Unit = {
    if (hitTime <= 0) return
    val elapsed = _frameTimeMs - hitTime
    if (elapsed > HIT_ANIMATION_MS) return
    val progress = elapsed.toDouble / HIT_ANIMATION_MS
    val fadeOut = (1.0 - progress).toFloat
    drawHitEffectCore(cx.toFloat, cy.toFloat, progress, fadeOut, hitTime, hr, hg, hb, ddx, ddy)
  }

  private def drawCastFlashInner(cx: Double, cy: Double): Unit = {
    val castTime = client.getLastCastTime
    if (castTime <= 0) return
    val elapsed = _frameTimeMs - castTime
    if (elapsed > 200) return
    val fadeOut = (1.0 - elapsed / 200.0).toFloat
    shapeBatch.fillOvalSoft(cx.toFloat, cy.toFloat, 14f, 10f, 1f, 1f, 0.8f, 0.3f * fadeOut, 0f, 16)
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

    // Item emits colored light — type-specific radius, color, and intensity
    item.itemType match {
      case ItemType.Gem =>
        lightSystem.addLight(centerX, centerY, 90f, ir, ig, ib, 0.25f * glowPulse)
      case ItemType.Heart =>
        lightSystem.addLight(centerX, centerY, 85f, 1f, 0.3f, 0.2f, 0.22f * glowPulse)
      case ItemType.Star =>
        lightSystem.addLight(centerX, centerY, 95f, 1f, 0.95f, 0.5f, 0.28f * glowPulse)
        // Secondary twinkle light
        val twinkle = (0.5f + 0.5f * Math.sin(animationTick * 0.4 + item.id * 1.3).toFloat)
        lightSystem.addLight(centerX, centerY, 55f, 1f, 1f, 0.8f, 0.12f * twinkle)
      case ItemType.Shield =>
        lightSystem.addLight(centerX, centerY, 80f, 0.3f, 0.5f, 1f, 0.2f * glowPulse)
      case _ =>
        lightSystem.addLight(centerX, centerY, 70f, ir, ig, ib, 0.15f * glowPulse)
    }

    beginShapes()
    // Pulsing ground ring — draws attention to pickups
    val ringPulse = (0.4f + 0.6f * Math.sin(bobPhase * 1.5f).toFloat).abs
    val ringRadius = hs * 1.8f + ringPulse * 4f
    shapeBatch.strokeOval(centerX, centerY + hs * 0.85f, ringRadius, ringRadius * 0.4f, 0.8f,
      ir, ig, ib, 0.12f * ringPulse, 14)
    // Drop shadow on ground
    shapeBatch.fillOvalSoft(centerX, centerY + hs * 0.85f, hs * 1.2f, hs * 0.3f, 0f, 0f, 0f, 0.22f, 0f, 12)

    // Main item shape with highlights and outline
    drawItemShape(item.itemType, centerX, centerY, hs, ir, ig, ib, rotAngle)

    // Defer additive effects (ground glow + sparkles) to batch pass
    val di = _difCount
    if (di < MAX_DEFERRED_ITEM_FX) {
      _difCX(di) = centerX; _difCY(di) = centerY; _difHS(di) = hs
      _difR(di) = ir; _difG(di) = ig; _difB(di) = ib
      _difGlowPulse(di) = glowPulse; _difBobPhase(di) = bobPhase
      _difCount += 1
    }
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

    // Dynamic projectile lighting — charge-reactive, distance-boosted
    intToRGB(proj.colorRGB)
    val plr = _rgb_r; val plg = _rgb_g; val plb = _rgb_b
    val chargeT = proj.chargeLevel / 100f
    // Charge whitening for light color
    val lr = Math.min(1f, plr + chargeT * (1f - plr) * 0.35f)
    val lg = Math.min(1f, plg + chargeT * (1f - plg) * 0.35f)
    val lb = Math.min(1f, plb + chargeT * (1f - plb) * 0.35f)
    // Distance boost
    val pDef = ProjectileDef.get(proj.projectileType)
    val maxR = pDef.effectiveMaxRange(proj.chargeLevel).toFloat
    val lifePct = if (maxR > 0f) Math.min(1f, proj.getDistanceTraveled / maxR) else 0f
    val distBoost = 1f + lifePct * 0.3f
    // Charge-reactive radius (55→125) and intensity (0.15→0.35)
    val lightRadius = (55f + chargeT * 70f) * distBoost
    var lightIntensity = 0.15f + chargeT * 0.2f
    // Boomerang return pulse
    if (proj.isReturning) {
      lightIntensity *= 0.8f + 0.2f * Math.sin(animationTick * 0.5).toFloat
    }
    lightSystem.addLight(sx, sy, lightRadius, lr, lg, lb, lightIntensity)

    // Element-type-specific secondary lighting overlay
    proj.projectileType match {
      // Fire types: warm orange-red glow
      case ProjectileType.FIREBALL | ProjectileType.FLAME_BOLT | ProjectileType.FLAME_BOLT_HEAVY |
           ProjectileType.FLAME_BOLT_LIGHT | ProjectileType.MAGMA_BALL | ProjectileType.ERUPTION |
           ProjectileType.INFERNO_BLAST | ProjectileType.EMBER_SHOT | ProjectileType.NAPALM_STRIKE |
           ProjectileType.FLAME_WAVE | ProjectileType.FLAME_TRAIL =>
        lightSystem.addLight(sx, sy, 80f, 1.0f, 0.4f, 0.05f, 0.25f)

      // Ice types: cool blue-white glow
      case ProjectileType.ICE_BEAM | ProjectileType.FROST_SHARD | ProjectileType.FROST_SHARD_LIGHT |
           ProjectileType.FROST_TRAP | ProjectileType.GLACIER_SPIKE | ProjectileType.AVALANCHE_CRUSH =>
        lightSystem.addLight(sx, sy, 60f, 0.4f, 0.7f, 1.0f, 0.18f)

      // Lightning types: bright white-blue with flicker
      case ProjectileType.LIGHTNING | ProjectileType.CHAIN_LIGHTNING | ProjectileType.CHAIN_LIGHTNING_FORK |
           ProjectileType.THUNDER_STRIKE | ProjectileType.TESLA_COIL =>
        val flicker = 0.5f + 0.5f * Math.sin(animationTick * 0.8).toFloat
        lightSystem.addLight(sx, sy, 100f, 0.8f, 0.85f, 1.0f, 0.3f * flicker)

      // Shadow/dark types: deep purple glow
      case ProjectileType.SHADOW_BOLT | ProjectileType.SHADOW_HAUNT | ProjectileType.DEATH_BOLT |
           ProjectileType.CURSE | ProjectileType.SOUL_BOLT | ProjectileType.SOUL_BOLT_HEAVY |
           ProjectileType.HAUNT | ProjectileType.SOUL_DRAIN | ProjectileType.SOUL_HARVEST =>
        lightSystem.addLight(sx, sy, 65f, 0.4f, 0.1f, 0.6f, 0.15f)

      // Holy/light types: warm golden glow
      case ProjectileType.HOLY_BOLT | ProjectileType.HOLY_BOLT_HEAVY | ProjectileType.HOLY_BLADE |
           ProjectileType.SMITE | ProjectileType.STAR_BOLT =>
        lightSystem.addLight(sx, sy, 75f, 1.0f, 0.9f, 0.5f, 0.22f)

      // Poison/venom: sickly green glow
      case ProjectileType.VENOM_BOLT | ProjectileType.VENOM_BOLT_LIGHT | ProjectileType.POISON_DART |
           ProjectileType.POISON_ARROW | ProjectileType.PLAGUE_BOLT | ProjectileType.MIASMA |
           ProjectileType.BLIGHT_BOMB | ProjectileType.ACID_BOMB | ProjectileType.ACID_FLASK |
           ProjectileType.ACID_SPRAY | ProjectileType.POISON_CLOUD =>
        lightSystem.addLight(sx, sy, 55f, 0.3f, 0.8f, 0.15f, 0.15f)

      // Water types: ocean blue glow
      case ProjectileType.SPLASH | ProjectileType.TIDAL_WAVE | ProjectileType.GEYSER |
           ProjectileType.SNARE_MINE =>
        lightSystem.addLight(sx, sy, 60f, 0.2f, 0.5f, 0.9f, 0.15f)

      // Void/gravity: dark indigo glow
      case ProjectileType.VOID_BOLT | ProjectileType.GRAVITY_BALL | ProjectileType.GRAVITY_WELL |
           ProjectileType.GRAVITY_LOCK | ProjectileType.GRAVITY_LANCE | ProjectileType.VORTEX_BOMB =>
        lightSystem.addLight(sx, sy, 70f, 0.2f, 0.05f, 0.4f, 0.18f)

      // Explosive: bright orange flash
      case ProjectileType.ROCKET | ProjectileType.GRENADE | ProjectileType.CLUSTER_BOMB |
           ProjectileType.MUD_BOMB =>
        lightSystem.addLight(sx, sy, 90f, 1.0f, 0.7f, 0.3f, 0.25f)

      // Tech/data: neon cyan glow
      case ProjectileType.DATA_BOLT | ProjectileType.VIRUS | ProjectileType.NANO_BOLT |
           ProjectileType.LASER | ProjectileType.LASER_HEAVY | ProjectileType.LASER_LIGHT |
           ProjectileType.RAILGUN | ProjectileType.OVERCLOCK_BEAM =>
        lightSystem.addLight(sx, sy, 55f, 0.1f, 0.9f, 0.6f, 0.15f)

      case _ => // No additional element light for uncategorized projectiles
    }

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
    shapeBatch.fillPolygon(_aimGlowXs, _aimGlowYs, glowN * 2 + 2, blendR, blendG, blendB, 0.12f * pulse)

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
    shapeBatch.fillPolygon(_aimBodyXs, _aimBodyYs, bodyN * 2 + 2, blendR * 0.5f, blendG * 0.5f, blendB * 0.5f, 0.25f + chargePct * 0.10f)

    // Energy spine
    shapeBatch.setAdditiveBlend(true)
    val spineSegs = 14
    i = 0
    while (i < spineSegs) {
      val t0 = i.toDouble / spineSegs; val t1 = (i + 1).toDouble / spineSegs
      val fadeAlpha = ((1.0 - t0 * 0.7) * (0.20 + chargePct * 0.15) * pulse).toFloat
      val lw = ((2.0 + chargePct * 1.5) * (1.0 - t0 * 0.5)).toFloat
      shapeBatch.strokeLine(cylSX(t0, 0), cylSY(t0, 0), cylSX(t1, 0), cylSY(t1, 0), lw, brightR, brightG, brightB, clamp(fadeAlpha))
      i += 1
    }

    // Energy rings
    val ringCount = 4 + (chargePct * 4).toInt
    i = 0
    while (i < ringCount) {
      val t = ((animationTick * 0.035 + i.toDouble / ringCount) % 1.0)
      val ringAlpha = ((1.0 - Math.abs(t - 0.5) * 2.0) * (0.30 + chargePct * 0.25) * pulse).toFloat
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
    shapeBatch.fillOvalSoft(tipSX, tipSY, orbR * 2, orbR, brightR, brightG, brightB, 0.45f * pulse, 0f, 12)

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

    // Dynamic lighting — bright flash that fades over the animation
    val lightRadius = (120f * (1f - progress * 0.8f)).toFloat
    val lightIntensity = (0.5f * fadeOut).toFloat
    lightSystem.addLight(cx, cy, lightRadius, cr, cg, cb, lightIntensity)

    beginShapes()

    // ── Phase 1 (0-20%): White-hot 3-layer flash ──
    if (progress < 0.2) {
      val flashP = (progress / 0.2).toFloat
      val flashGrow = 0.4f + flashP * 0.6f
      val flashFade = 1f - flashP
      shapeBatch.setAdditiveBlend(true)
      // Layer 1: Huge white-hot outer glow (60px)
      shapeBatch.fillOvalSoft(cx, cy, 60f * flashGrow, 30f * flashGrow,
        1f, 1f, 0.95f, 0.5f * flashFade, 0f, 20);
      // Layer 2: Colored mid glow (45px)
      shapeBatch.fillOvalSoft(cx, cy, 45f * flashGrow, 22f * flashGrow,
        bright(cr), bright(cg), bright(cb), 0.65f * flashFade, 0f, 18);
      // Layer 3: Bright white center (20px)
      shapeBatch.fillOval(cx, cy, 20f * flashGrow, 10f * flashGrow,
        1f, 1f, 1f, 0.8f * flashFade, 12)
      shapeBatch.setAdditiveBlend(false)
    }

    // ── Phase 2 (0-40%): Dual expanding shockwave rings with dark outlines ──
    if (progress < 0.4) {
      val ringP = (progress / 0.4).toFloat
      val ringFade = 1f - ringP

      // Ground ripple — dark ellipse beneath
      val rippleR = 20f + ringP * 40f
      shapeBatch.fillOval(cx, cy + 4f, rippleR, rippleR * 0.3f,
        0.05f, 0.02f, 0.05f, 0.3f * ringFade, 20);

      // Outer ring (starts immediately)
      val r1W = ringP * 55f
      val r1H = r1W * 0.5f
      // Dark outline
      shapeBatch.strokeOval(cx, cy, r1W + 2f, r1H + 1f, 5f * ringFade,
        0.05f, 0.02f, 0.02f, 0.7f * ringFade, 28);
      // Colored body
      shapeBatch.strokeOval(cx, cy, r1W, r1H, 4f * ringFade,
        cr, cg, cb, 0.8f * ringFade, 28);
      // Bright inner edge
      shapeBatch.strokeOval(cx, cy, r1W - 1f, r1H - 0.5f, 1.5f * ringFade,
        bright(cr), bright(cg), bright(cb), 0.6f * ringFade, 28)

      // Inner ring (delayed slightly)
      if (progress > 0.05) {
        val r2P = ((progress - 0.05) / 0.35).toFloat
        val r2Fade = 1f - r2P
        val r2W = r2P * 40f
        val r2H = r2W * 0.5f
        shapeBatch.strokeOval(cx, cy, r2W + 1.5f, r2H + 0.75f, 4f * r2Fade,
          0.05f, 0.02f, 0.02f, 0.5f * r2Fade, 24);
        shapeBatch.strokeOval(cx, cy, r2W, r2H, 3f * r2Fade,
          bright(cr), bright(cg), bright(cb), 0.65f * r2Fade, 24)
      }
    }

    // ── Phase 3 (0-80%): Soul fragment particles — 20 in 3 tiers with trails ──
    if (progress < 0.8) {
      val partP = (progress / 0.8).toFloat
      val partFade = 1f - partP
      shapeBatch.setAdditiveBlend(true)
      var i = 0
      while (i < 20) {
        val tier = i % 3 // 0=large bright, 1=medium colored, 2=small dim
        val pAngle = i * (2 * Math.PI / 20) + i * 0.47
        val baseSpeed = if (tier == 0) 1.0 else if (tier == 1) 0.75 else 0.55
        val gravity = if (tier == 0) 30.0 else if (tier == 1) 22.0 else 15.0
        val dist = (partP * (30 + (i % 5) * 10) * baseSpeed).toFloat
        val rise = (partP * partP * gravity * (0.8 + (i % 4) * 0.1)).toFloat
        val px = cx + dist * Math.cos(pAngle).toFloat
        val py = cy + dist * Math.sin(pAngle).toFloat * 0.5f - rise

        // Particle color and size per tier
        var pr = 0f; var pg = 0f; var pb = 0f; var pSize = 0f; var pAlpha = 0f
        if (tier == 0) { pr = 1f; pg = 1f; pb = 0.9f; pSize = 4f * partFade; pAlpha = 0.8f * partFade }
        else if (tier == 1) { pr = clamp(cr * 0.4f + 0.6f); pg = clamp(cg * 0.4f + 0.6f); pb = clamp(cb * 0.4f + 0.6f); pSize = 3f * partFade; pAlpha = 0.7f * partFade }
        else { pr = cr; pg = cg; pb = cb; pSize = 2f * partFade; pAlpha = 0.55f * partFade }

        // Trail segments (2 segments behind the particle)
        if (partP > 0.05f) {
          val trailDist1 = dist * 0.75f
          val trailRise1 = rise * 0.75f
          val tx1 = cx + trailDist1 * Math.cos(pAngle).toFloat
          val ty1 = cy + trailDist1 * Math.sin(pAngle).toFloat * 0.5f - trailRise1
          shapeBatch.fillOval(tx1, ty1, pSize * 0.6f, pSize * 0.6f, pr, pg, pb, pAlpha * 0.4f, 6);
          val trailDist2 = dist * 0.5f
          val trailRise2 = rise * 0.5f
          val tx2 = cx + trailDist2 * Math.cos(pAngle).toFloat
          val ty2 = cy + trailDist2 * Math.sin(pAngle).toFloat * 0.5f - trailRise2
          shapeBatch.fillOval(tx2, ty2, pSize * 0.35f, pSize * 0.35f, pr, pg, pb, pAlpha * 0.2f, 6)
        }

        // Dark outline for particle
        shapeBatch.fillOval(px, py, pSize + 1f, pSize + 1f, 0.02f, 0.01f, 0.02f, pAlpha * 0.5f, 8);
        // Particle body
        shapeBatch.fillOval(px, py, pSize, pSize, pr, pg, pb, pAlpha, 8)
        i += 1
      }
      shapeBatch.setAdditiveBlend(false)
    }

    // ── Phase 4 (10-70%): Dissolve ring with crack lines ──
    if (progress > 0.1 && progress < 0.7) {
      val dissP = ((progress - 0.1) / 0.6).toFloat
      val dissFade = 1f - dissP
      val dissR = 10f + dissP * 35f
      // Dark dissolve ring
      shapeBatch.strokeOval(cx, cy, dissR, dissR * 0.5f, 3f * dissFade,
        0.08f, 0.03f, 0.08f, 0.6f * dissFade, 24)

      // Crack lines radiating from center (6 lines)
      var c = 0
      while (c < 6) {
        val cAngle = c * (Math.PI / 3.0) + 0.3
        val crackLen = (8f + dissP * 22f)
        val outerX = cx + crackLen * Math.cos(cAngle).toFloat
        val outerY = cy + crackLen * Math.sin(cAngle).toFloat * 0.5f
        // Dark line
        shapeBatch.strokeLine(cx, cy, outerX, outerY, 2.5f * dissFade,
          0.05f, 0.02f, 0.05f, 0.5f * dissFade);
        // Bright edge
        shapeBatch.strokeLine(cx, cy, outerX, outerY, 1f * dissFade,
          bright(cr), bright(cg), bright(cb), 0.35f * dissFade)
        c += 1
      }
    }

    // ── Phase 5 (0-100%): Rising ghost sprite with glow aura + soul wisps ──
    // Soul wisps drawn under the ghost (in shape batch)
    shapeBatch.setAdditiveBlend(true)
    // Glowing aura around ghost position
    val ghostRise = (progress * 40).toFloat
    val ghostCy = cy - ghostRise
    val auraAlpha = fadeOut * 0.3f
    if (auraAlpha > 0.02f) {
      shapeBatch.fillOvalSoft(cx, ghostCy, 22f, 28f,
        cr, cg, cb, auraAlpha, 0f, 16)
    }

    // 6 soul wisps rising with sinusoidal drift
    var w = 0
    while (w < 6) {
      val wPhase = w * (Math.PI / 3.0) + progress * 4.0
      val wRise = (progress * (20 + w * 8)).toFloat
      val wDrift = (Math.sin(wPhase) * 8).toFloat
      val wX = cx + wDrift
      val wY = cy - wRise
      val wAlpha = fadeOut * (0.4f - w * 0.04f)
      val wSize = (2.5f - w * 0.2f) * fadeOut
      if (wAlpha > 0.02f) {
        shapeBatch.fillOval(wX, wY, wSize, wSize * 1.4f,
          bright(cr), bright(cg), bright(cb), wAlpha, 8);
        // Wisp trail
        shapeBatch.fillOval(wX, wY + wSize * 2f, wSize * 0.5f, wSize * 0.8f,
          cr, cg, cb, wAlpha * 0.3f, 6)
      }
      w += 1
    }
    shapeBatch.setAdditiveBlend(false)

    // Ghost sprite rendering
    val ghostAlpha = Math.max(0f, fadeOut * 0.6f)
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
        drawTeleportDeparture(worldToScreenX(oldX, oldY).toFloat, worldToScreenY(oldX, oldY).toFloat, timestamp, colorRGB)
        drawTeleportArrival(worldToScreenX(newX, newY).toFloat, worldToScreenY(newX, newY).toFloat, timestamp, colorRGB)
      }
    }
  }

  private def drawTeleportDeparture(sx: Float, sy: Float, startTime: Long, colorRGB: Int = 0xFFD700): Unit = {
    val elapsed = _frameTimeMs - startTime
    if (elapsed > TELEPORT_ANIMATION_MS) return
    val progress = elapsed.toDouble / TELEPORT_ANIMATION_MS
    val fadeOut = Math.max(0f, (1.0 - progress * 1.3).toFloat)
    intToRGB(colorRGB)
    val cr = _rgb_r; val cg = _rgb_g; val cb = _rgb_b

    // Dynamic light — golden-white intensifying then snapping off
    val lightInt = if (progress < 0.7) (0.4f * (progress / 0.7).toFloat) else 0f
    lightSystem.addLight(sx, sy, 80f * (1f - progress.toFloat * 0.5f), bright(cr), bright(cg), bright(cb), lightInt)

    beginShapes()

    // ── Ground rune circle: expanding then shrinking magic circle ──
    val runeScale = if (progress < 0.5) (progress / 0.5).toFloat else (1f - ((progress - 0.5) / 0.5).toFloat)
    val runeR = 28f * runeScale
    val runeAlpha = 0.6f * runeScale
    if (runeAlpha > 0.02f) {
      // Outer ring (dark outline + colored body)
      shapeBatch.strokeOval(sx, sy + 4f, runeR + 1.5f, runeR * 0.35f + 1f, 3f,
        0.05f, 0.03f, 0.05f, runeAlpha * 0.7f, 24);
      shapeBatch.strokeOval(sx, sy + 4f, runeR, runeR * 0.35f, 2f,
        bright(cr), bright(cg), bright(cb), runeAlpha, 24);
      // Inner ring
      shapeBatch.strokeOval(sx, sy + 4f, runeR * 0.6f, runeR * 0.21f, 1.5f,
        cr, cg, cb, runeAlpha * 0.5f, 20)

      // 6 rune marks orbiting the circle
      var rm = 0
      while (rm < 6) {
        val rmAngle = rm * (Math.PI / 3.0) + progress * 6.0
        val rmX = sx + (runeR * 0.8f * Math.cos(rmAngle)).toFloat
        val rmY = sy + 4f + (runeR * 0.28f * Math.sin(rmAngle)).toFloat
        val rmSz = 2.5f * runeScale
        shapeBatch.fillRect(rmX - rmSz * 0.5f, rmY - rmSz * 0.5f, rmSz, rmSz,
          bright(cr), bright(cg), bright(cb), runeAlpha * 0.8f)
        rm += 1
      }
    }

    // ── Vertical energy pillar: 8 layered horizontal rings stacking vertically ──
    shapeBatch.setAdditiveBlend(true)
    var ring = 0
    while (ring < 8) {
      val ringFrac = ring / 8f
      val yOff = -ringFrac * 55f // rings stack upward
      val converge = progress.toFloat // rings converge inward as progress increases
      val ringW = (18f - converge * 14f) * (1f - ringFrac * 0.3f)
      val ringH = ringW * 0.35f
      val ringAlpha = fadeOut * (0.55f - ringFrac * 0.04f)
      val ry = sy + yOff
      if (ringAlpha > 0.02f) {
        // Dark outline
        shapeBatch.strokeOval(sx, ry, ringW + 1.5f, ringH + 0.75f, 2.5f,
          0.03f, 0.01f, 0.03f, ringAlpha * 0.6f, 16);
        // Colored body
        shapeBatch.strokeOval(sx, ry, ringW, ringH, 2f,
          cr, cg, cb, ringAlpha, 16);
        // Bright core
        shapeBatch.strokeOval(sx, ry, ringW * 0.6f, ringH * 0.6f, 1f,
          bright(cr), bright(cg), bright(cb), ringAlpha * 0.7f, 12)
      }
      ring += 1
    }

    // ── 4 lightning bolts crackling around departure ──
    var bolt = 0
    while (bolt < 4) {
      val bAngle = bolt * (Math.PI / 2.0) + progress * 8.0
      val bDist = 12f + 8f * fadeOut
      val bx = sx + (bDist * Math.cos(bAngle)).toFloat
      val by = sy - 10f + (bDist * 0.4f * Math.sin(bAngle)).toFloat
      // 3-segment zigzag bolt
      var seg = 0
      var segX = bx; var segY = by
      while (seg < 3) {
        val jitterX = ((((bolt * 7 + seg * 13 + animationTick) % 17) - 8) * 1.2f).toFloat
        val jitterY = -6f - ((((bolt * 11 + seg * 5 + animationTick) % 11) - 3) * 0.8f).toFloat
        val nextX = segX + jitterX
        val nextY = segY + jitterY
        val boltAlpha = fadeOut * 0.7f
        // Dark outline
        shapeBatch.strokeLine(segX, segY, nextX, nextY, 3f, 0.02f, 0.01f, 0.02f, boltAlpha * 0.5f);
        // Colored body
        shapeBatch.strokeLine(segX, segY, nextX, nextY, 2f, cr, cg, cb, boltAlpha);
        // White core
        shapeBatch.strokeLine(segX, segY, nextX, nextY, 0.8f, 1f, 1f, 1f, boltAlpha * 0.8f)
        segX = nextX; segY = nextY
        seg += 1
      }
      bolt += 1
    }

    // ── Spiral particle vortex: 12 particles spiraling inward with trails ──
    var p = 0
    while (p < 12) {
      val spiralAngle = p * (Math.PI * 2.0 / 12) + progress * 10.0
      val spiralR = (30f * (1f - progress.toFloat) * (0.6f + (p % 3) * 0.15f))
      val px = sx + (spiralR * Math.cos(spiralAngle)).toFloat
      val py = sy + (spiralR * 0.4f * Math.sin(spiralAngle)).toFloat - progress.toFloat * 15f
      val pAlpha = fadeOut * 0.65f
      val pSize = 2.5f * fadeOut

      // Trail
      if (pAlpha > 0.02f) {
        val trailAngle = spiralAngle - 0.4
        val trailR = spiralR * 1.15f
        val tx = sx + (trailR * Math.cos(trailAngle)).toFloat
        val ty = sy + (trailR * 0.4f * Math.sin(trailAngle)).toFloat - progress.toFloat * 14f
        shapeBatch.fillOval(tx, ty, pSize * 0.5f, pSize * 0.5f, cr, cg, cb, pAlpha * 0.3f, 6)
      }
      // Particle
      shapeBatch.fillOval(px, py, pSize, pSize, bright(cr), bright(cg), bright(cb), pAlpha, 8)
      p += 1
    }

    // ── Bright implosion flash at end (progress > 0.7) ──
    if (progress > 0.7) {
      val impP = ((progress - 0.7) / 0.3).toFloat
      val impAlpha = 0.8f * impP
      val impR = 20f * (1f - impP * 0.5f)
      shapeBatch.fillOvalSoft(sx, sy - 15f, impR, impR * 0.6f,
        1f, 1f, 1f, impAlpha, 0f, 16);
      shapeBatch.fillOval(sx, sy - 15f, impR * 0.4f, impR * 0.25f,
        1f, 1f, 0.95f, impAlpha * 0.9f, 12)
    }

    shapeBatch.setAdditiveBlend(false)
  }

  private def drawTeleportArrival(sx: Float, sy: Float, startTime: Long, colorRGB: Int = 0xFFD700): Unit = {
    val elapsed = _frameTimeMs - startTime
    if (elapsed < 200 || elapsed > TELEPORT_ANIMATION_MS) return
    val progress = (elapsed - 200).toDouble / (TELEPORT_ANIMATION_MS - 200)
    val fadeOut = (1.0 - progress).toFloat
    intToRGB(colorRGB)
    val cr = _rgb_r; val cg = _rgb_g; val cb = _rgb_b

    // Dynamic light — bright flash that fades
    val lightInt = (0.5f * fadeOut).toFloat
    lightSystem.addLight(sx, sy, 100f * fadeOut, bright(cr), bright(cg), bright(cb), lightInt)

    beginShapes()

    // ── Arrival flash at start (progress < 0.2): Massive 3-layer flash ──
    if (progress < 0.2) {
      val flashP = (progress / 0.2).toFloat
      val flashFade = 1f - flashP
      shapeBatch.setAdditiveBlend(true)
      // Layer 1: Huge outer glow (80px)
      shapeBatch.fillOvalSoft(sx, sy - 10f, 80f * flashFade, 40f * flashFade,
        1f, 1f, 0.95f, 0.55f * flashFade, 0f, 20);
      // Layer 2: Colored mid glow
      shapeBatch.fillOvalSoft(sx, sy - 10f, 50f * flashFade, 25f * flashFade,
        bright(cr), bright(cg), bright(cb), 0.7f * flashFade, 0f, 16);
      // Layer 3: White-hot center
      shapeBatch.fillOval(sx, sy - 10f, 20f * flashFade, 10f * flashFade,
        1f, 1f, 1f, 0.85f * flashFade, 12)
      shapeBatch.setAdditiveBlend(false)
    }

    // ── Vertical energy pillar: rings expanding outward from center ──
    shapeBatch.setAdditiveBlend(true)
    if (progress < 0.6) {
      val pillarP = (progress / 0.6).toFloat
      var ring = 0
      while (ring < 8) {
        val ringFrac = ring / 8f
        val yOff = -ringFrac * 55f
        val expand = pillarP // rings expand outward
        val ringW = (4f + expand * 16f) * (1f - ringFrac * 0.2f)
        val ringH = ringW * 0.35f
        val ringAlpha = (1f - pillarP) * (0.55f - ringFrac * 0.04f)
        val ry = sy + yOff
        if (ringAlpha > 0.02f) {
          shapeBatch.strokeOval(sx, ry, ringW + 1f, ringH + 0.5f, 2.5f,
            0.03f, 0.01f, 0.03f, ringAlpha * 0.5f, 16);
          shapeBatch.strokeOval(sx, ry, ringW, ringH, 2f,
            cr, cg, cb, ringAlpha, 16);
          shapeBatch.strokeOval(sx, ry, ringW * 0.5f, ringH * 0.5f, 1f,
            bright(cr), bright(cg), bright(cb), ringAlpha * 0.6f, 12)
        }
        ring += 1
      }
    }

    // ── Expanding shockwave ring with dark outline ──
    if (progress < 0.7) {
      val ringP = (progress / 0.7).toFloat
      val ringFade = 1f - ringP
      val ringW = ringP * 50f
      val ringH = ringW * 0.5f
      shapeBatch.setAdditiveBlend(false)
      // Dark outline
      shapeBatch.strokeOval(sx, sy, ringW + 2f, ringH + 1f, 4.5f * ringFade,
        0.04f, 0.02f, 0.04f, 0.6f * ringFade, 28);
      // Colored body
      shapeBatch.strokeOval(sx, sy, ringW, ringH, 3.5f * ringFade,
        cr, cg, cb, 0.75f * ringFade, 28);
      // Bright inner edge
      shapeBatch.setAdditiveBlend(true)
      shapeBatch.strokeOval(sx, sy, ringW - 1f, ringH - 0.5f, 1.5f * ringFade,
        bright(cr), bright(cg), bright(cb), 0.5f * ringFade, 28)
    }

    // ── Ground impact cracks: 6 lines radiating from center ──
    shapeBatch.setAdditiveBlend(false)
    if (progress < 0.6) {
      val crackP = (progress / 0.6).toFloat
      val crackFade = 1f - crackP
      var c = 0
      while (c < 6) {
        val cAngle = c * (Math.PI / 3.0) + 0.5
        val crackLen = 10f + crackP * 30f
        val outerX = sx + (crackLen * Math.cos(cAngle)).toFloat
        val outerY = sy + (crackLen * Math.sin(cAngle) * 0.5).toFloat
        // Dark crack
        shapeBatch.strokeLine(sx, sy, outerX, outerY, 2.5f * crackFade,
          0.06f, 0.03f, 0.06f, 0.55f * crackFade);
        // Bright edge
        shapeBatch.strokeLine(sx, sy, outerX, outerY, 1f * crackFade,
          bright(cr), bright(cg), bright(cb), 0.4f * crackFade)
        c += 1
      }
    }

    // ── Scatter particles: 12 particles exploding outward with gravity and trails ──
    shapeBatch.setAdditiveBlend(true)
    var p = 0
    while (p < 12) {
      val pAngle = p * (Math.PI * 2.0 / 12) + p * 0.35
      val speed = 0.7 + (p % 4) * 0.15
      val dist = (progress * (25 + (p % 4) * 10) * speed).toFloat
      val gravity = (progress * progress * 20 * (0.7 + (p % 3) * 0.15)).toFloat
      val px = sx + dist * Math.cos(pAngle).toFloat
      val py = sy + dist * Math.sin(pAngle).toFloat * 0.5f + gravity
      val pSize = (3f - (p % 3) * 0.5f) * fadeOut
      val pAlpha = fadeOut * (0.7f - (p % 3) * 0.1f)

      if (pAlpha > 0.02f) {
        // Trail
        val tDist = dist * 0.7f
        val tGrav = gravity * 0.7f
        val tx = sx + tDist * Math.cos(pAngle).toFloat
        val ty = sy + tDist * Math.sin(pAngle).toFloat * 0.5f + tGrav
        shapeBatch.fillOval(tx, ty, pSize * 0.4f, pSize * 0.4f, cr, cg, cb, pAlpha * 0.3f, 6);
        // Dark outline
        shapeBatch.fillOval(px, py, pSize + 1f, pSize + 1f, 0.02f, 0.01f, 0.02f, pAlpha * 0.4f, 8);
        // Particle body
        shapeBatch.fillOval(px, py, pSize, pSize, bright(cr), bright(cg), bright(cb), pAlpha, 8)
      }
      p += 1
    }

    // ── Residual sparkles: 8 sparkle stars that pop and fade ──
    var s = 0
    while (s < 8) {
      val sparkDelay = s * 0.1
      val sparkLife = progress - sparkDelay
      if (sparkLife > 0 && sparkLife < 0.5) {
        val sparkP = (sparkLife / 0.5).toFloat
        val sparkFade = if (sparkP < 0.3f) sparkP / 0.3f else (1f - sparkP) / 0.7f
        val sAngle = s * (Math.PI * 2.0 / 8) + s * 1.1
        val sDist = 15f + s * 5f
        val sparkX = sx + (sDist * Math.cos(sAngle)).toFloat
        val sparkY = sy + (sDist * Math.sin(sAngle) * 0.5).toFloat - sparkP * 8f
        val sparkSz = 3f * sparkFade
        // 4-pointed star (two crossed lines)
        shapeBatch.strokeLine(sparkX - sparkSz, sparkY, sparkX + sparkSz, sparkY,
          1.5f, 1f, 1f, 1f, 0.7f * sparkFade);
        shapeBatch.strokeLine(sparkX, sparkY - sparkSz, sparkX, sparkY + sparkSz,
          1.5f, 1f, 1f, 1f, 0.7f * sparkFade);
        // Diagonal cross
        val dSz = sparkSz * 0.6f
        shapeBatch.strokeLine(sparkX - dSz, sparkY - dSz, sparkX + dSz, sparkY + dSz,
          1f, bright(cr), bright(cg), bright(cb), 0.5f * sparkFade);
        shapeBatch.strokeLine(sparkX - dSz, sparkY + dSz, sparkX + dSz, sparkY - dSz,
          1f, bright(cr), bright(cg), bright(cb), 0.5f * sparkFade)
      }
      s += 1
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
        val seed = entry.getKey.toLong

        beginShapes()

        // ── Phase 1: 4-layer bright flash (0-15%) ──
        if (progress < 0.15f) {
          val flashP = progress / 0.15f
          val flashScale = 0.3f + flashP * 0.7f
          val flashAlpha = 0.9f * (1f - flashP)
          shapeBatch.setAdditiveBlend(true)
          // Layer 1: Huge white outer glow (80px equivalent)
          shapeBatch.fillOvalSoft(sx, sy, blastW * flashScale * 1.4f, blastH * flashScale * 1.4f,
            1f, 1f, 0.97f, flashAlpha * 0.4f, 0f, 22);
          // Layer 2: Colored glow (60px equivalent)
          shapeBatch.fillOvalSoft(sx, sy, blastW * flashScale * 1.0f, blastH * flashScale * 1.0f,
            bright(er), bright(eg), bright(eb), flashAlpha * 0.65f, 0f, 20);
          // Layer 3: Bright core (40px equivalent)
          shapeBatch.fillOval(sx, sy, blastW * flashScale * 0.6f, blastH * flashScale * 0.6f,
            1f, 1f, 0.95f, flashAlpha * 0.85f, 16);
          // Layer 4: White-hot center (15px equivalent)
          shapeBatch.fillOval(sx, sy, blastW * flashScale * 0.2f, blastH * flashScale * 0.2f,
            1f, 1f, 1f, flashAlpha, 12)
          // Screen-wide subtle flash overlay
          if (flashP < 0.4f) {
            val overlayAlpha = 0.08f * (1f - flashP / 0.4f)
            shapeBatch.fillRect(sx - 400f, sy - 300f, 800f, 600f, 1f, 1f, 0.95f, overlayAlpha)
          }
          shapeBatch.setAdditiveBlend(false)
        }

        // ── Phase 2: Expanding fireball with outline and fire tongues (0-50%) ──
        if (progress < 0.5f) {
          val fireP = progress / 0.5f
          val fireAlpha = 0.85f * (1f - fireP * 0.7f)
          val fireScale = 0.2f + fireP * 0.8f
          val fireW = blastW * fireScale
          val fireH = blastH * fireScale

          // Outer fire glow
          shapeBatch.fillOvalSoft(sx, sy, fireW * 1.3f, fireH * 1.3f,
            er, eg * 0.5f, eb * 0.2f, fireAlpha * 0.4f, 0f, 18)

          // Dark cartoon outline ring around fireball
          shapeBatch.strokeOval(sx, sy, fireW * 0.75f, fireH * 0.75f, 4f * (1f - fireP * 0.4f),
            0.06f, 0.02f, 0.02f, fireAlpha * 0.7f, 20)

          // Core fireball
          shapeBatch.fillOval(sx, sy, fireW * 0.7f, fireH * 0.7f,
            er, eg, eb, fireAlpha, 16)

          // 8 fire tongue licks radiating outward
          shapeBatch.setAdditiveBlend(true)
          var t = 0
          while (t < 8) {
            val tongueAngle = t * (Math.PI / 4.0) + fireP * 2.5 + seed * 0.3
            val tongueLen = fireW * 0.5f * (0.6f + 0.4f * Math.sin(tongueAngle * 3 + animationTick * 0.2).toFloat)
            val tBaseX = sx + (fireW * 0.55f * Math.cos(tongueAngle)).toFloat
            val tBaseY = sy + (fireH * 0.55f * Math.sin(tongueAngle)).toFloat
            val tTipX = sx + ((fireW * 0.55f + tongueLen) * Math.cos(tongueAngle)).toFloat
            val tTipY = sy + ((fireH * 0.55f + tongueLen) * Math.sin(tongueAngle)).toFloat
            shapeBatch.strokeLineSoft(tBaseX, tBaseY, tTipX, tTipY, 4f * (1f - fireP * 0.5f),
              er, bright(eg), eb * 0.3f, fireAlpha * 0.6f)
            t += 1
          }
          shapeBatch.setAdditiveBlend(false)

          // Inner swirling detail
          val swirlAngle = _animTickF * 0.12
          val swirlR = fireW * 0.3f
          val swirlX = sx + (swirlR * Math.cos(swirlAngle)).toFloat
          val swirlY = sy + (swirlR * 0.5f * Math.sin(swirlAngle)).toFloat
          shapeBatch.fillOval(swirlX, swirlY, fireW * 0.15f, fireH * 0.15f,
            bright(er), bright(eg), bright(eb), fireAlpha * 0.7f, 10)

          // Hot center
          shapeBatch.fillOval(sx, sy, fireW * 0.35f, fireH * 0.35f,
            bright(er), bright(eg), bright(eb), fireAlpha * 0.9f, 12)
        }

        // ── Phase 3: Dual shockwave rings with dark outlines + ground distortion (10-80%) ──
        if (progress > 0.1f && progress < 0.8f) {
          val ringP = (progress - 0.1f) / 0.7f
          val ringScale = 0.3f + ringP * 0.7f
          val ringAlpha = 0.8f * (1f - ringP)
          val ringW = blastW * ringScale * 1.1f
          val ringH = blastH * ringScale * 1.1f
          val thickness = 3.5f * (1f - ringP * 0.5f)

          // Ground distortion — dark oval beneath expanding
          shapeBatch.fillOval(sx, sy + 3f, ringW * 0.9f, ringH * 0.4f,
            0.05f, 0.03f, 0.03f, ringAlpha * 0.3f, 20)

          // Primary ring — dark outline
          shapeBatch.strokeOval(sx, sy, ringW + 2f, ringH + 1f, thickness + 2f,
            0.05f, 0.02f, 0.02f, ringAlpha * 0.6f, 28);
          // Primary ring — colored body
          shapeBatch.strokeOval(sx, sy, ringW, ringH, thickness, er, eg, eb, ringAlpha, 28)

          // Second ring (delayed by 10%)
          if (progress > 0.2f && progress < 0.75f) {
            val ring2P = (progress - 0.2f) / 0.55f
            val ring2Scale = 0.2f + ring2P * 0.8f
            val ring2Alpha = 0.6f * (1f - ring2P)
            val ring2W = blastW * ring2Scale * 1.0f
            val ring2H = blastH * ring2Scale * 1.0f
            // Dark outline
            shapeBatch.strokeOval(sx, sy, ring2W + 1.5f, ring2H + 0.75f, 2.5f,
              0.05f, 0.02f, 0.02f, ring2Alpha * 0.5f, 28);
            // Bright body
            shapeBatch.strokeOval(sx, sy, ring2W, ring2H, 1.8f,
              bright(er), bright(eg), bright(eb), ring2Alpha, 28)
          }
        }

        // ── Phase 4: 18 debris particles with outlines + trails + 6 rising embers (15-100%) ──
        if (progress > 0.15f) {
          val debrisP = (progress - 0.15f) / 0.85f
          // 18 debris particles in 3 size tiers
          var i = 0
          while (i < 18) {
            val tier = i % 3 // 0=large, 1=medium, 2=small
            val angle = i.toDouble * Math.PI * 2 / 18 + seed * 0.7
            val speed = 0.5f + (((i * 7 + seed * 3) % 10) / 10f) * 0.7f
            val dist = debrisP * speed
            val dx = sx + (Math.cos(angle) * dist * blastW * 1.2f).toFloat
            val dy = sy + (Math.sin(angle) * dist * blastH * 1.2f).toFloat - debrisP * debrisP * 18f
            val debrisAlpha = 0.75f * (1f - debrisP)
            val sz = if (tier == 0) 4f * (1f - debrisP * 0.5f)
                     else if (tier == 1) 3f * (1f - debrisP * 0.5f)
                     else 2f * (1f - debrisP * 0.6f)

            if (debrisAlpha > 0.05f) {
              // 2-segment trail behind debris
              val td1 = dist * 0.75f
              val td1X = sx + (Math.cos(angle) * td1 * blastW * 1.2f).toFloat
              val td1Y = sy + (Math.sin(angle) * td1 * blastH * 1.2f).toFloat - (debrisP * 0.75f) * (debrisP * 0.75f) * 18f
              shapeBatch.fillOval(td1X, td1Y, sz * 0.5f, sz * 0.35f, er, eg * 0.5f, eb * 0.2f, debrisAlpha * 0.3f, 4);
              val td2 = dist * 0.5f
              val td2X = sx + (Math.cos(angle) * td2 * blastW * 1.2f).toFloat
              val td2Y = sy + (Math.sin(angle) * td2 * blastH * 1.2f).toFloat - (debrisP * 0.5f) * (debrisP * 0.5f) * 18f
              shapeBatch.fillOval(td2X, td2Y, sz * 0.3f, sz * 0.2f, er, eg * 0.4f, eb * 0.15f, debrisAlpha * 0.15f, 4)

              // Dark outline
              shapeBatch.fillOval(dx, dy, sz + 1f, sz * 0.7f + 0.7f, 0.05f, 0.02f, 0.02f, debrisAlpha * 0.5f, 6);
              // Debris body
              shapeBatch.fillOval(dx, dy, sz, sz * 0.7f, er, eg * 0.6f, eb * 0.3f, debrisAlpha, 6)
            }
            i += 1
          }

          // 6 ember particles that rise and drift
          shapeBatch.setAdditiveBlend(true)
          var e = 0
          while (e < 6) {
            val eAngle = e * (Math.PI / 3.0) + seed * 1.1
            val eDrift = (Math.sin(eAngle + debrisP * 4) * 12f).toFloat
            val eRise = debrisP * debrisP * 40f
            val ex = sx + eDrift + (e * 4f - 12f)
            val ey = sy - eRise - e * 3f
            val eAlpha = 0.6f * (1f - debrisP)
            val eSz = 2f * (1f - debrisP * 0.4f)
            if (eAlpha > 0.03f) {
              shapeBatch.fillOval(ex, ey, eSz, eSz * 1.3f,
                1f, bright(eg) * 0.8f, eb * 0.2f, eAlpha, 6);
              // Ember trail
              shapeBatch.fillOval(ex, ey + eSz * 2f, eSz * 0.4f, eSz * 0.8f,
                er, eg * 0.5f, eb * 0.1f, eAlpha * 0.3f, 4)
            }
            e += 1
          }
          shapeBatch.setAdditiveBlend(false)
        }

        // ── Phase 5: 4 smoke puffs drifting upward with dark outlines (30-100%) ──
        if (progress > 0.3f) {
          val smokeP = (progress - 0.3f) / 0.7f
          var s = 0
          while (s < 4) {
            val sDelay = s * 0.08f
            val sP = Math.max(0f, (smokeP - sDelay) / (1f - sDelay))
            if (sP > 0f) {
              val sFade = 1f - sP
              val smokeAlpha = 0.25f * sFade
              val smokeScale = 0.4f + sP * 0.6f
              // Each puff at slightly different position
              val sOffX = (s * 7f - 10.5f) * smokeScale
              val sOffY = -sP * (14f + s * 4f)
              val smkW = blastW * smokeScale * (0.6f + s * 0.08f)
              val smkH = blastH * smokeScale * (0.45f + s * 0.06f)
              if (smokeAlpha > 0.02f) {
                // Dark outline ring
                shapeBatch.strokeOval(sx + sOffX, sy + sOffY, smkW + 1.5f, smkH + 1f, 2f,
                  0.08f, 0.06f, 0.05f, smokeAlpha * 0.6f, 14);
                // Smoke puff body
                shapeBatch.fillOvalSoft(sx + sOffX, sy + sOffY, smkW, smkH,
                  0.25f, 0.22f, 0.2f, smokeAlpha, 0f, 14)
              }
            }
            s += 1
          }
        }

        // ── Phase 6: Ground scorch mark (40-100%) ──
        if (progress > 0.4f) {
          val scorchP = (progress - 0.4f) / 0.6f
          val scorchAlpha = 0.35f * (1f - scorchP * 0.6f) // fades slowly, persists
          val scorchScale = 0.5f + scorchP * 0.2f
          if (scorchAlpha > 0.02f) {
            shapeBatch.fillOval(sx, sy + 2f, blastW * scorchScale, blastH * scorchScale * 0.5f,
              0.06f, 0.03f, 0.03f, scorchAlpha, 18)
          }
        }
      }
    }
  }

  private def drawAoeSplashAnimations(): Unit = {
    val now = _frameTimeMs
    val AOE_DURATION = 1200L
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
        val seed = entry.getKey.toLong

        val aoeW = aoeRadius * HW
        val aoeH = aoeRadius * HH

        // Dynamic light — bright flash that fades
        val lightInt = if (progress < 0.15f) 0.4f else 0.4f * (1f - progress)
        lightSystem.addLight(sx, sy, aoeW * 1.5f, ar, ag, ab, Math.max(0f, lightInt))

        beginShapes()

        // Full-area ground highlight — colored zone showing AoE radius (0-100%)
        val zoneAlpha = if (progress < 0.1f) progress / 0.1f * 0.35f
                        else 0.35f * (1f - (progress - 0.1f) / 0.9f)
        if (zoneAlpha > 0.02f) {
          shapeBatch.fillOval(sx, sy, aoeW, aoeH, ar * 0.5f, ag * 0.5f, ab * 0.5f, zoneAlpha, 24)
        }

        // Bright center impact flash (0-25%) — 3-layer
        if (progress < 0.25f) {
          val flashP = progress / 0.25f
          val flashAlpha = 0.9f * (1f - flashP)
          val flashScale = 0.2f + flashP * 0.6f
          shapeBatch.setAdditiveBlend(true)
          // Layer 1: Wide outer glow
          shapeBatch.fillOvalSoft(sx, sy, aoeW * flashScale * 1.2f, aoeH * flashScale * 1.2f,
            bright(ar), bright(ag), bright(ab), flashAlpha * 0.4f, 0f, 22);
          // Layer 2: Colored glow
          shapeBatch.fillOvalSoft(sx, sy, aoeW * flashScale * 0.8f, aoeH * flashScale * 0.8f,
            bright(ar), bright(ag), bright(ab), flashAlpha * 0.6f, 0f, 20);
          // Layer 3: White-hot core
          shapeBatch.fillOval(sx, sy, aoeW * flashScale * 0.4f, aoeH * flashScale * 0.4f,
            1f, 1f, 0.95f, flashAlpha, 16)
          shapeBatch.setAdditiveBlend(false)
        }

        // Bold shockwave ring with dark outline (0-60%)
        if (progress < 0.6f) {
          val swP = progress / 0.6f
          val swFade = 1f - swP
          val swScale = 0.1f + swP * 0.9f
          val swW = aoeW * swScale
          val swH = aoeH * swScale
          val swThick = 4f * (1f - swP * 0.4f)
          // Dark outline
          shapeBatch.strokeOval(sx, sy, swW + 2f, swH + 1f, swThick + 2f,
            0.05f, 0.02f, 0.02f, 0.6f * swFade, 28);
          // Colored body
          shapeBatch.strokeOval(sx, sy, swW, swH, swThick,
            ar, ag, ab, 0.8f * swFade, 28);
          // Bright inner edge
          shapeBatch.setAdditiveBlend(true)
          shapeBatch.strokeOval(sx, sy, swW - 1f, swH - 0.5f, 1.5f,
            bright(ar), bright(ag), bright(ab), 0.5f * swFade, 28)
          shapeBatch.setAdditiveBlend(false)
        }

        // Bold expanding ring waves (4 rings with stagger + dark outlines)
        var ring = 0
        while (ring < 4) {
          val ringDelay = ring * 0.1f
          val ringP = Math.max(0f, (progress - ringDelay) / (0.8f - ringDelay))
          if (ringP > 0f && ringP < 1f) {
            val ringScale = 0.05f + ringP * 0.95f
            val ringAlpha = 0.9f * (1f - ringP) / (ring * 0.5f + 1f)
            val rw = aoeW * ringScale
            val rh = aoeH * ringScale
            val thickness = (4f - ring * 0.7f) * (1f - ringP * 0.3f)
            // Dark outline on each ring
            shapeBatch.strokeOval(sx, sy, rw + 1f, rh + 0.5f, thickness + 1.5f,
              0.04f, 0.02f, 0.02f, ringAlpha * 0.4f, 28);
            shapeBatch.strokeOval(sx, sy, rw, rh, thickness, ar, ag, ab, ringAlpha, 28)
          }
          ring += 1
        }

        // Outer boundary ring — shows exact AoE radius (5-90%)
        if (progress > 0.05f && progress < 0.9f) {
          val boundP = if (progress < 0.15f) (progress - 0.05f) / 0.1f else 1f
          val boundFade = if (progress > 0.7f) (0.9f - progress) / 0.2f else 1f
          val boundAlpha = 0.7f * boundP * boundFade
          shapeBatch.strokeOval(sx, sy, aoeW, aoeH, 2.5f, bright(ar), bright(ag), bright(ab), boundAlpha, 32)
        }

        // Ground scorch mark (fades slowly)
        if (progress > 0.15f) {
          val scorchP = (progress - 0.15f) / 0.85f
          val scorchAlpha = 0.4f * (1f - scorchP)
          shapeBatch.fillOval(sx, sy, aoeW * 0.65f, aoeH * 0.65f,
            ar * 0.3f, ag * 0.3f, ab * 0.3f, scorchAlpha, 16)
        }

        // Ground impact crack lines (0-50%) — 8 lines radiating from center
        if (progress < 0.5f) {
          val crackP = progress / 0.5f
          val crackFade = 1f - crackP
          var c = 0
          while (c < 8) {
            val cAngle = c * (Math.PI / 4.0) + seed * 0.5
            val crackLen = aoeW * (0.2f + crackP * 0.5f)
            val outerX = sx + (crackLen * Math.cos(cAngle)).toFloat
            val outerY = sy + (crackLen * 0.5 * Math.sin(cAngle)).toFloat
            // Dark crack line
            shapeBatch.strokeLine(sx, sy, outerX, outerY, 2.5f * crackFade,
              0.06f, 0.03f, 0.06f, 0.5f * crackFade);
            // Bright edge
            shapeBatch.strokeLine(sx, sy, outerX, outerY, 1f * crackFade,
              bright(ar), bright(ag), bright(ab), 0.4f * crackFade)
            c += 1
          }
        }

        // Radial line burst from center (0-50%)
        if (progress < 0.5f) {
          val burstP = progress / 0.5f
          val numLines = 12
          val burstAlpha = 0.7f * (1f - burstP)
          val innerDist = burstP * 0.3f
          val outerDist = 0.3f + burstP * 0.7f
          var i = 0
          while (i < numLines) {
            val angle = i.toDouble * Math.PI * 2 / numLines + seed * 0.9
            val ix = sx + (Math.cos(angle) * aoeW * innerDist).toFloat
            val iy = sy + (Math.sin(angle) * aoeH * innerDist).toFloat
            val ox = sx + (Math.cos(angle) * aoeW * outerDist).toFloat
            val oy = sy + (Math.sin(angle) * aoeH * outerDist).toFloat
            shapeBatch.strokeLine(ix, iy, ox, oy, 2f, bright(ar), bright(ag), bright(ab), burstAlpha)
            i += 1
          }
        }

        // Sparkle particles around perimeter with outlines (16 particles)
        if (progress > 0.05f && progress < 0.8f) {
          val sparkP = (progress - 0.05f) / 0.75f
          val numSparks = 16
          var i = 0
          while (i < numSparks) {
            val angle = i.toDouble * Math.PI * 2 / numSparks + seed * 1.3
            val dist = 0.4f + sparkP * 0.6f
            val px = sx + (Math.cos(angle) * aoeW * dist).toFloat
            val py = sy + (Math.sin(angle) * aoeH * dist).toFloat
            val sparkAlpha = 0.8f * (1f - sparkP)
            val sparkSz = 3.5f * (1f - sparkP * 0.3f)
            if (sparkAlpha > 0.03f) {
              // Dark outline
              shapeBatch.fillOval(px, py, sparkSz + 1f, sparkSz * 0.7f + 0.7f,
                0.04f, 0.02f, 0.02f, sparkAlpha * 0.4f, 6);
              // Bright particle body
              shapeBatch.fillOval(px, py, sparkSz, sparkSz * 0.7f,
                bright(ar), bright(ag), bright(ab), sparkAlpha, 6)
            }
            i += 1
          }
        }

        // Sparkle stars (8 stars that pop and fade)
        shapeBatch.setAdditiveBlend(true)
        var s = 0
        while (s < 8) {
          val sDelay = s * 0.08f
          val sLife = progress - sDelay
          if (sLife > 0f && sLife < 0.4f) {
            val sP = sLife / 0.4f
            val sFade = if (sP < 0.25f) sP / 0.25f else (1f - sP) / 0.75f
            val sAngle = s * (Math.PI * 2.0 / 8) + seed * 1.7
            val sDist = aoeW * (0.3f + s * 0.08f)
            val starX = sx + (sDist * Math.cos(sAngle)).toFloat
            val starY = sy + (sDist * 0.5 * Math.sin(sAngle)).toFloat
            val starSz = 4f * sFade
            if (sFade > 0.02f) {
              // 4-pointed star (two crossed lines)
              shapeBatch.strokeLine(starX - starSz, starY, starX + starSz, starY,
                1.5f, 1f, 1f, 1f, 0.7f * sFade);
              shapeBatch.strokeLine(starX, starY - starSz, starX, starY + starSz,
                1.5f, 1f, 1f, 1f, 0.7f * sFade);
              // Diagonal cross
              val dSz = starSz * 0.6f
              shapeBatch.strokeLine(starX - dSz, starY - dSz, starX + dSz, starY + dSz,
                1f, bright(ar), bright(ag), bright(ab), 0.5f * sFade);
              shapeBatch.strokeLine(starX - dSz, starY + dSz, starX + dSz, starY - dSz,
                1f, bright(ar), bright(ag), bright(ab), 0.5f * sFade)
            }
          }
          s += 1
        }
        shapeBatch.setAdditiveBlend(false)
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
    // Dark halo behind health bar for contrast on any terrain
    shapeBatch.fillRoundedRect(barX - 3f, barY - 3f, barW + 6f, barH + 6f, 3f, 0.01f, 0.01f, 0.03f, 0.35f)
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
      // Bright highlight line at very top (1px white at 30% alpha)
      shapeBatch.fillRect(barX, barY, fillW, 1f, clamp(fr + 0.3f), clamp(fg + 0.3f), clamp(fb + 0.3f), 0.3f)
    }

    // Segment markers at 25%/50%/75% (subtle dark tick marks)
    { var seg = 1; while (seg < 4) {
      val segX = barX + barW * seg / 4f
      shapeBatch.fillRect(segX - 0.5f, barY, 1f, barH, 0f, 0f, 0f, 0.25f)
    ; seg += 1 } }

    // Bright highlight along top edge when health > 0 (stronger 0.4 alpha)
    if (fillW > 0) {
      shapeBatch.fillRect(barX, barY, fillW, 1.2f, clamp(fr + 0.4f), clamp(fg + 0.4f), clamp(fb + 0.4f), 0.4f)
    }

    // Low health pulse (<25%): dramatic red glow (0.25 alpha, larger)
    if (pct < 0.25f && pct > 0f) {
      val pulse = (0.5f + 0.5f * Math.sin(animationTick * 0.15f)).toFloat
      shapeBatch.setAdditiveBlend(true)
      shapeBatch.fillRect(barX, barY - 2, fillW, barH + 4, 1f, 0.2f, 0.1f, 0.25f * pulse)
      // Larger edge glow around bar
      shapeBatch.fillOvalSoft(barX + fillW * 0.5f, barY + barH * 0.5f, fillW * 0.8f + 4f, barH * 2f,
        1f, 0.15f, 0.05f, 0.15f * pulse, 0f, 10)
      // Pulsing red border
      shapeBatch.strokeOval(barX + barW * 0.5f, barY + barH * 0.5f, barW * 0.52f, barH * 0.8f,
        1.2f, 1f, 0.2f, 0.1f, 0.12f * pulse, 10)
      shapeBatch.setAdditiveBlend(false)
    }

    // Dark outline for contrast against any background
    shapeBatch.strokeRect(barX - 1, barY - 1, barW + 2, barH + 2, 1f, 0f, 0f, 0f, 0.7f)
    shapeBatch.strokeRect(barX, barY, barW, barH, 1f, 0f, 0f, 0f, 1f)
    // Subtle highlight along top edge
    shapeBatch.fillRect(barX, barY, barW, 1f, 1f, 1f, 1f, 0.2f)
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

  /** Draw dark pill background behind character name — called before name sprites pass. */
  private def drawNameBackground(name: String, screenCenterX: Double, spriteTopY: Double): Unit = {
    val barH = Constants.HEALTH_BAR_HEIGHT_PX
    val nameY = (spriteTopY - Constants.HEALTH_BAR_OFFSET_Y - barH - fontSmall.charHeight - 2).toFloat
    val textW = fontSmall.measureWidth(name)
    val textX = (screenCenterX - textW / 2).toFloat
    val padW = 6f; val padH = 3f
    val bgX = textX - padW; val bgY = nameY - padH
    val bgW = textW + padW * 2; val bgH = fontSmall.charHeight + padH * 2
    beginShapes()
    // Gradient background: darker at bottom
    shapeBatch.fillRoundedRectGradient(bgX, bgY, bgW, bgH, 5f,
      0.06f, 0.06f, 0.12f, 0.70f,
      0.02f, 0.02f, 0.05f, 0.80f)
    // Subtle border
    shapeBatch.strokeRect(bgX, bgY, bgW, bgH, 1f, 0.4f, 0.4f, 0.5f, 0.20f)
    // Top edge highlight
    shapeBatch.strokeLine(bgX + 5f, bgY, bgX + bgW - 5f, bgY, 1f, 1f, 1f, 1f, 0.08f)
  }

  // ═══════════════════════════════════════════════════════════════════
  //  HUD RENDERING (5i)
  // ═══════════════════════════════════════════════════════════════════

  private def renderHUD(screenW: Int, screenH: Int): Unit = {
    // Dark backing panel behind the entire bottom HUD bar
    val hudBarH = 78f
    beginShapes()
    shapeBatch.fillRoundedRectGradient(0f, screenH - hudBarH, screenW.toFloat, hudBarH, 0f,
      0.02f, 0.02f, 0.06f, 0.50f,
      0.01f, 0.01f, 0.03f, 0.40f)
    // Top edge subtle highlight
    shapeBatch.strokeLine(0f, screenH - hudBarH, screenW.toFloat, screenH - hudBarH, 1f, 1f, 1f, 1f, 0.06f)

    renderInventory(screenW, screenH)
    renderAbilityHUD(screenW, screenH)
    renderChargeBar(screenW, screenH)
    renderLobbyHUD(screenW, screenH)
    renderChat(screenW, screenH)
    if (client.isPracticeMode) renderPracticeHUD(screenW, screenH)
  }

  private def renderInventory(screenW: Int, screenH: Int): Unit = {
    val slotSize = 54f; val slotGap = 8f; val numSlots = 5
    val totalW = numSlots * slotSize + (numSlots - 1) * slotGap
    val startX = (screenW - totalW) / 2f
    val startY = screenH - slotSize - 14f

    // Compute and cache slot counts once per frame
    var i = 0
    while (i < inventorySlotTypes.length) {
      val count = client.getItemCount(inventorySlotTypes(i)._1.id)
      if (count != _cachedSlotCounts(i)) {
        _cachedSlotCounts(i) = count
        _cachedSlotCountStrs(i) = if (count > 0) count.toString else null
      }
      i += 1
    }

    // Draw all slot backgrounds + icons (shapes)
    beginShapes()
    i = 0
    while (i < inventorySlotTypes.length) {
      val pair = inventorySlotTypes(i)
      val itemType = pair._1
      val count = _cachedSlotCounts(i)
      val slotX = startX + i * (slotSize + slotGap)
      intToRGB(itemType.colorRGB)
      val tr = _rgb_r; val tg = _rgb_g; val tb = _rgb_b

      if (count > 0) {
        shapeBatch.fillRoundedRect(slotX - 2, startY - 2, slotSize + 4, slotSize + 4, 6f, tr * 0.3f, tg * 0.3f, tb * 0.3f, 0.5f)
        shapeBatch.fillRoundedRect(slotX, startY, slotSize, slotSize, 5f, 0.08f, 0.08f, 0.14f, 0.80f)
        // Top edge highlight
        shapeBatch.strokeLine(slotX + 4, startY, slotX + slotSize - 4, startY, 1f, 1f, 1f, 1f, 0.08f)
      } else {
        shapeBatch.fillRoundedRect(slotX, startY, slotSize, slotSize, 5f, 0.06f, 0.06f, 0.1f, 0.50f)
      }

      val iconCX = slotX + slotSize / 2; val iconCY = startY + slotSize / 2 - 1; val iconSize = 17f
      if (count > 0) drawItemShape(itemType, iconCX, iconCY, iconSize, tr, tg, tb)
      else drawItemShape(itemType, iconCX, iconCY, iconSize, tr * 0.3f, tg * 0.3f, tb * 0.3f)

      if (count > 0) {
        val countStr = _cachedSlotCountStrs(i)
        val badgeW = Math.max(14f, 7f + countStr.length * 6f)
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
      val keyLabel = pair._2
      val count = _cachedSlotCounts(i)
      val slotX = startX + i * (slotSize + slotGap)

      if (count > 0) {
        fontSmall.drawText(spriteBatch, _cachedSlotCountStrs(i), slotX + slotSize - 8, startY - 3)
      }
      fontSmall.drawText(spriteBatch, keyLabel, slotX + slotSize / 2 - 4, startY + slotSize - 16,
        if (count > 0) 0.9f else 0.43f, if (count > 0) 0.9f else 0.43f, if (count > 0) 0.9f else 0.47f, 1f)
      i += 1
    }
  }

  private def renderAbilityHUD(screenW: Int, screenH: Int): Unit = {
    val slotSize = 54f; val slotGap = 8f
    val invSlotSize = 54f; val invSlotGap = 8f; val invNumSlots = 5
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

      // Slot background (glassmorphism) with bright border
      shapeBatch.fillRoundedRect(slotX, startY, slotSize, slotSize, 5f, 0.08f, 0.08f, 0.14f, 0.75f)
      shapeBatch.strokeRect(slotX, startY, slotSize, slotSize, 1f, ar * 0.5f, ag * 0.5f, ab * 0.5f, 0.4f)
      shapeBatch.strokeLine(slotX + 4, startY, slotX + slotSize - 4, startY, 1f, 1f, 1f, 1f, 0.07f)

      // Ability icon inside the slot
      val cx = slotX + slotSize / 2; val cy = startY + slotSize / 2
      val iconAlpha = if (onCooldown) 0.35f else 0.9f
      drawAbilityIcon(aDef.castBehavior, cx, cy, 17f, ar, ag, ab, iconAlpha)

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
        // Muted border when on cooldown
      } else {
        // No border stroke needed - glassmorphism panel is enough
        // Ready flash pulse (300ms glow when cooldown completes)
        val flashElapsed = now - cooldownReadyFlashTime(i)
        if (flashElapsed < 300 && cooldownReadyFlashTime(i) > 0) {
          val flashAlpha = (1f - flashElapsed / 300f) * 0.4f
          shapeBatch.setAdditiveBlend(true)
          shapeBatch.fillRoundedRect(slotX, startY, slotSize, slotSize, 5f, ar, ag, ab, flashAlpha)
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

  /** Draw a detailed icon representing the cast behavior type. */
  private def drawAbilityIcon(behavior: CastBehavior, cx: Float, cy: Float, sz: Float,
                              r: Float, g: Float, b: Float, a: Float): Unit = {
    val t = _animTickF
    behavior match {
      case StandardProjectile =>
        // Detailed arrow/bolt shape flying right with speed lines
        // Arrow shaft
        shapeBatch.fillRect(cx - sz * 0.6f, cy - sz * 0.12f, sz * 0.9f, sz * 0.24f, r, g, b, a * 0.85f)
        // Arrowhead triangle
        _iconXs(0) = cx + sz * 0.7f; _iconYs(0) = cy
        _iconXs(1) = cx + sz * 0.2f; _iconYs(1) = cy - sz * 0.4f
        _iconXs(2) = cx + sz * 0.2f; _iconYs(2) = cy + sz * 0.4f
        shapeBatch.fillPolygon(_iconXs, _iconYs, 3, r, g, b, a);
        // Bright core highlight on shaft
        shapeBatch.fillRect(cx - sz * 0.4f, cy - sz * 0.05f, sz * 0.6f, sz * 0.1f, clamp(r + 0.3f), clamp(g + 0.3f), clamp(b + 0.3f), a * 0.6f)
        // Fletching notches at tail
        shapeBatch.strokeLine(cx - sz * 0.55f, cy - sz * 0.12f, cx - sz * 0.7f, cy - sz * 0.3f, 1.5f, r, g, b, a * 0.7f)
        shapeBatch.strokeLine(cx - sz * 0.55f, cy + sz * 0.12f, cx - sz * 0.7f, cy + sz * 0.3f, 1.5f, r, g, b, a * 0.7f)
        // Speed lines trailing behind
        val sl = (Math.sin(t * 0.2) * 0.15 + 0.35).toFloat
        shapeBatch.strokeLine(cx - sz * 0.85f, cy - sz * 0.25f, cx - sz * (0.85f + sl), cy - sz * 0.25f, 1f, r, g, b, a * 0.35f)
        shapeBatch.strokeLine(cx - sz * 0.8f, cy, cx - sz * (0.8f + sl * 1.2f), cy, 1f, r, g, b, a * 0.4f)
        shapeBatch.strokeLine(cx - sz * 0.85f, cy + sz * 0.25f, cx - sz * (0.85f + sl), cy + sz * 0.25f, 1f, r, g, b, a * 0.35f)

      case FanProjectile(count, _) =>
        // Fan pattern with distinct diverging bolts
        val spread = Math.min(count, 7)
        // Origin circle at bottom center
        shapeBatch.fillOval(cx, cy + sz * 0.35f, sz * 0.18f, sz * 0.18f, r, g, b, a * 0.7f, 8);
        // Draw spread bolts diverging upward
        { var i = 0; while (i < spread) {
          val frac = if (spread > 1) (i.toFloat / (spread - 1)) - 0.5f else 0f
          val angle = -Math.PI / 2 + frac * Math.PI * 0.6
          val boltLen = sz * 0.75f
          val tipX = cx + (Math.cos(angle) * boltLen).toFloat
          val tipY = cy + sz * 0.35f + (Math.sin(angle) * boltLen).toFloat
          // Bolt line
          shapeBatch.strokeLine(cx, cy + sz * 0.35f, tipX, tipY, 1.8f, r, g, b, a * 0.8f)
          // Bolt tip dot
          shapeBatch.fillOval(tipX, tipY, sz * 0.12f, sz * 0.12f, r, g, b, a, 6)
        ; i += 1 } }
        // Arc at the spread width
        shapeBatch.strokeOval(cx, cy + sz * 0.35f, sz * 0.7f, sz * 0.7f, 1f, r, g, b, a * 0.2f, 16)

      case _: PhaseShiftBuff =>
        // Ghost/spirit with flowing wisps
        // Ghost body (rounded top, wavy bottom)
        shapeBatch.fillOval(cx, cy - sz * 0.15f, sz * 0.55f, sz * 0.6f, r, g, b, a * 0.45f, 14)
        // Inner brighter ghost core
        shapeBatch.fillOval(cx, cy - sz * 0.2f, sz * 0.38f, sz * 0.42f, r, g, b, a * 0.65f, 12)
        // Wavy bottom tendrils (3 wisps)
        val waveOff = (Math.sin(t * 0.15) * sz * 0.08).toFloat;
        { var i = 0; while (i < 3) {
          val wx = cx + (i - 1) * sz * 0.3f + waveOff * (if (i % 2 == 0) 1f else -1f)
          val wy = cy + sz * 0.35f
          shapeBatch.fillOvalSoft(wx, wy, sz * 0.15f, sz * 0.25f, r, g, b, a * 0.4f, 0f, 8)
        ; i += 1 } }
        // Eyes (bright, glowing)
        shapeBatch.fillOval(cx - sz * 0.18f, cy - sz * 0.22f, sz * 0.1f, sz * 0.12f, 1f, 1f, 1f, a * 0.9f, 6)
        shapeBatch.fillOval(cx + sz * 0.18f, cy - sz * 0.22f, sz * 0.1f, sz * 0.12f, 1f, 1f, 1f, a * 0.9f, 6)
        // Outer ethereal glow
        shapeBatch.fillOvalSoft(cx, cy - sz * 0.1f, sz * 0.8f, sz * 0.85f, r, g, b, a * 0.12f, 0f, 14)

      case _: DashBuff =>
        // Dynamic swoosh/dash trail with speed blur
        // Main swoosh curve — thick leading edge tapering to thin
        _iconXs(0) = cx + sz * 0.75f; _iconYs(0) = cy
        _iconXs(1) = cx + sz * 0.3f; _iconYs(1) = cy - sz * 0.35f
        _iconXs(2) = cx - sz * 0.5f; _iconYs(2) = cy - sz * 0.15f
        _iconXs(3) = cx - sz * 0.75f; _iconYs(3) = cy - sz * 0.05f
        _iconXs(4) = cx - sz * 0.5f; _iconYs(4) = cy + sz * 0.08f
        _iconXs(5) = cx + sz * 0.3f; _iconYs(5) = cy + sz * 0.35f
        shapeBatch.fillPolygon(_iconXs, _iconYs, 6, r, g, b, a * 0.8f)
        // Bright leading point
        shapeBatch.fillOval(cx + sz * 0.7f, cy, sz * 0.15f, sz * 0.15f, clamp(r + 0.3f), clamp(g + 0.3f), clamp(b + 0.3f), a, 8)
        // Speed motion lines behind the swoosh
        val dashPulse = (Math.sin(t * 0.2) * 0.15 + 0.5).toFloat;
        { var i = 0; while (i < 4) {
          val ly = cy + (i - 1.5f) * sz * 0.22f
          val lx0 = cx - sz * (0.5f + i * 0.08f)
          val lx1 = cx - sz * (0.85f + i * 0.05f)
          shapeBatch.strokeLine(lx0, ly, lx1, ly, 1.2f, r, g, b, a * dashPulse * (0.3f + i * 0.1f))
        ; i += 1 } }
        // Secondary lighter swoosh layer
        shapeBatch.strokeLine(cx - sz * 0.4f, cy - sz * 0.1f, cx + sz * 0.5f, cy, 1.5f, clamp(r + 0.2f), clamp(g + 0.2f), clamp(b + 0.2f), a * 0.4f)

      case _: TeleportCast =>
        // Portal ring with sparkle effect
        // Outer portal ring
        shapeBatch.strokeOval(cx, cy, sz * 0.7f, sz * 0.7f, 2.2f, r, g, b, a * 0.85f, 16)
        // Inner ring
        shapeBatch.strokeOval(cx, cy, sz * 0.45f, sz * 0.45f, 1.5f, r, g, b, a * 0.5f, 12)
        // Center glow
        shapeBatch.fillOvalSoft(cx, cy, sz * 0.3f, sz * 0.3f, clamp(r + 0.2f), clamp(g + 0.2f), clamp(b + 0.2f), a * 0.5f, 0f, 10);
        // Sparkle dots orbiting the ring
        { var i = 0; while (i < 4) {
          val sparkAngle = t * 0.12 + i * Math.PI / 2
          val sparkR = sz * 0.58f
          val sx = cx + (Math.cos(sparkAngle) * sparkR).toFloat
          val sy = cy + (Math.sin(sparkAngle) * sparkR).toFloat
          shapeBatch.fillOval(sx, sy, sz * 0.08f, sz * 0.08f, 1f, 1f, 1f, a * 0.8f, 6)
        ; i += 1 } }
        // Cross sparkle at center
        val sparkSz = sz * 0.15f * ((Math.sin(t * 0.18) * 0.3 + 0.7).toFloat)
        shapeBatch.strokeLine(cx - sparkSz, cy, cx + sparkSz, cy, 1.5f, 1f, 1f, 1f, a * 0.6f)
        shapeBatch.strokeLine(cx, cy - sparkSz, cx, cy + sparkSz, 1.5f, 1f, 1f, 1f, a * 0.6f)

      case _: GroundSlam =>
        // Impact crater with radiating cracks
        // Central impact point
        shapeBatch.fillOval(cx, cy, sz * 0.25f, sz * 0.16f, r, g, b, a, 10)
        // Inner bright flash
        shapeBatch.fillOvalSoft(cx, cy, sz * 0.18f, sz * 0.12f, clamp(r + 0.3f), clamp(g + 0.3f), clamp(b + 0.3f), a * 0.7f, 0f, 8)
        // Shockwave rings (isometric perspective)
        shapeBatch.strokeOval(cx, cy, sz * 0.5f, sz * 0.3f, 2f, r, g, b, a * 0.9f, 14)
        shapeBatch.strokeOval(cx, cy, sz * 0.85f, sz * 0.52f, 1.5f, r, g, b, a * 0.6f, 14)
        shapeBatch.strokeOval(cx, cy, sz * 1.15f, sz * 0.7f, 1f, r, g, b, a * 0.3f, 14);
        // Radiating crack lines from center
        { var i = 0; while (i < 6) {
          val crackAngle = i * Math.PI / 3 + Math.PI / 6
          val crackLen = sz * (0.55f + (i % 2) * 0.2f)
          val endX = cx + (Math.cos(crackAngle) * crackLen).toFloat
          val endY = cy + (Math.sin(crackAngle) * crackLen * 0.62f).toFloat // perspective squash
          shapeBatch.strokeLine(cx, cy, endX, endY, 1.2f, r, g, b, a * 0.5f)
        ; i += 1 } }
        // Debris particles floating upward
        { var i = 0; while (i < 3) {
          val dAngle = t * 0.1 + i * 2.1
          val dR = sz * 0.35f
          val dx = cx + (Math.cos(dAngle) * dR).toFloat
          val dy = cy - sz * 0.15f + (Math.sin(dAngle * 1.3) * sz * 0.12f).toFloat
          shapeBatch.fillOval(dx, dy, sz * 0.06f, sz * 0.06f, r, g, b, a * 0.5f, 4)
        ; i += 1 } }
    }
  }

  private def renderChargeBar(screenW: Int, screenH: Int): Unit = {
    if (!client.isCharging) return
    val cpt = client.getSelectedCharacterDef.primaryProjectileType
    if (!ProjectileDef.get(cpt).chargeSpeedScaling.isDefined) return

    val chargeLevel = client.getChargeLevel
    val barW = 100f; val barH = 8f
    val barX = (screenW - barW) / 2f; val barY = screenH - 80f
    val tick = animationTick

    beginShapes()
    // Background with subtle pulse based on charge level
    val bgPulse = (0.95 + 0.05 * Math.sin(tick * 0.08 * (1f + chargeLevel / 100f))).toFloat
    shapeBatch.fillRoundedRect(barX - 2, barY - 2, barW + 4, barH + 4, 4f, 0.08f * bgPulse, 0.08f * bgPulse, 0.12f * bgPulse, 0.78f)
    shapeBatch.fillRoundedRect(barX, barY, barW, barH, 3f, 0.18f, 0.18f, 0.22f, 0.9f)

    val pct = chargeLevel / 100f
    // 3-stage color: GREEN (0-33%) -> YELLOW (34-66%) -> ORANGE-RED (67-100%)
    val cr = if (pct < 0.33f) 0.2f + pct * 2f
             else if (pct < 0.66f) 0.85f + (pct - 0.33f) * 0.45f
             else Math.min(1f, 1f)
    val cg = if (pct < 0.33f) 0.8f
             else if (pct < 0.66f) 0.8f - (pct - 0.33f) * 1.2f
             else Math.max(0f, 0.4f - (pct - 0.66f) * 1.2f)
    val cb = 0f

    // Fill bar
    shapeBatch.fillRoundedRect(barX, barY, barW * pct, barH, 3f, cr, cg, cb, 1f)
    // Top highlight (brighter)
    if (barW * pct > 2f) {
      shapeBatch.fillRect(barX + 1f, barY + 1f, barW * pct - 2f, 1.8f,
        Math.min(1f, cr + 0.3f), Math.min(1f, cg + 0.3f), Math.min(1f, cb + 0.3f), 0.4f)
    }

    // Threshold markers at 33% and 66%
    shapeBatch.fillRect(barX + barW * 0.33f - 0.5f, barY, 1f, barH, 0f, 0f, 0f, 0.3f)
    shapeBatch.fillRect(barX + barW * 0.66f - 0.5f, barY, 1f, barH, 0f, 0f, 0f, 0.3f)

    // Brief bright flash pulse at each 33% threshold crossing
    val threshFlash33 = Math.abs(pct - 0.33f)
    val threshFlash66 = Math.abs(pct - 0.66f)
    if (threshFlash33 < 0.03f) {
      val flashI = (1f - threshFlash33 / 0.03f) * 0.3f
      shapeBatch.fillRect(barX, barY - 1f, barW * 0.33f, barH + 2f, 1f, 1f, 0.5f, flashI)
    }
    if (threshFlash66 < 0.03f) {
      val flashI = (1f - threshFlash66 / 0.03f) * 0.3f
      shapeBatch.fillRect(barX, barY - 1f, barW * 0.66f, barH + 2f, 1f, 0.8f, 0.3f, flashI)
    }

    // Energy crackling along the bar (small lightning-like segments)
    { var seg = 0; while (seg < 5) {
      val segPhase = ((tick * 0.12f + seg * 20f) % barW)
      if (segPhase < barW * pct - 4f) {
        val segX = barX + segPhase
        val segY1 = barY + barH * 0.5f + Math.sin(tick * 0.3 + seg * 2.7).toFloat * 3f
        val segY2 = barY + barH * 0.5f - Math.sin(tick * 0.35 + seg * 1.9).toFloat * 3f
        val segX2 = segX + 4f + Math.sin(tick * 0.2 + seg).toFloat * 2f
        shapeBatch.strokeLine(segX, segY1, segX2, segY2, 1.2f,
          Math.min(1f, cr + 0.3f), Math.min(1f, cg + 0.3f), Math.min(1f, cb + 0.5f), 0.3f * pct)
      }
    ; seg += 1 } }

    shapeBatch.setAdditiveBlend(true)
    // Leading edge bright dot + soft glow trail
    val leadX = barX + barW * pct
    val leadY = barY + barH / 2
    shapeBatch.fillOvalSoft(leadX, leadY, 10f, 10f, cr, cg, 0.2f, 0.4f * pct, 0f, 12)
    shapeBatch.fillOval(leadX, leadY, 3f, 3f, 1f, 1f, 0.8f, 0.6f * pct, 8)
    // Glow trail behind leading edge
    shapeBatch.fillOvalSoft(leadX - 6f, leadY, 8f, 6f, cr, cg, 0.1f, 0.2f * pct, 0f, 8)

    // At 100%: dramatic pulsing glow border, 6+ spark particles, expanding ring effect
    if (chargeLevel >= 100) {
      val fullPulse = (0.5 + 0.5 * Math.sin(tick * 0.2)).toFloat
      // Pulsing glow border around entire bar
      shapeBatch.fillOvalSoft(barX + barW * 0.5f, barY + barH * 0.5f, barW * 0.58f, barH * 2.5f,
        cr, cg, 0.15f, 0.15f * fullPulse, 0f, 14)
      // Bright edge glow top and bottom
      shapeBatch.fillRect(barX - 1f, barY - 2f, barW + 2f, 1.5f, cr, cg, 0.2f, 0.2f * fullPulse)
      shapeBatch.fillRect(barX - 1f, barY + barH + 0.5f, barW + 2f, 1.5f, cr, cg, 0.2f, 0.2f * fullPulse)

      // 6+ spark particles emitting from bar
      { var s = 0; while (s < 6) {
        val sparkX = barX + ((tick * 0.6f + s * 17f) % barW)
        val sparkY = barY + barH * 0.5f + Math.sin(tick * 0.18 + s * 1.7).toFloat * 8f
        val sparkA = (0.4 + 0.3 * Math.sin(tick * 0.12 + s * 2.1)).toFloat * fullPulse
        shapeBatch.fillOval(sparkX, sparkY, 2.5f, 2.5f, 1f, 0.95f, 0.4f, sparkA, 4)
        // Spark trail
        val trailX = sparkX - 3f
        val trailY = sparkY + Math.sin(tick * 0.15 + s * 1.2).toFloat * 2f
        shapeBatch.strokeLineSoft(trailX, trailY, sparkX, sparkY, 1.5f, 1f, 0.8f, 0.3f, sparkA * 0.5f)
      ; s += 1 } }

      // Expanding ring effect at bar center
      val ringPhase = (tick * 0.04f) % 1f
      val ringR = 10f + ringPhase * 20f
      val ringAlpha = (1f - ringPhase) * 0.15f * fullPulse
      shapeBatch.strokeOval(barX + barW * 0.5f, barY + barH * 0.5f, ringR, ringR * 0.4f, 1.5f,
        cr, cg, 0.3f, ringAlpha, 14)
    }
    shapeBatch.setAdditiveBlend(false)

    beginSprites()
    if (chargeLevel != _cachedChargeLevel) { _cachedChargeLevel = chargeLevel; _cachedChargeStr = chargeLevel + "%" }
    fontSmall.drawText(spriteBatch, _cachedChargeStr, barX + barW / 2 - 12, barY - 16)
  }

  private def renderLobbyHUD(screenW: Int, screenH: Int): Unit = {
    if (client.clientState != ClientState.PLAYING) return

    // Timer panel at top center (skip in practice mode — practice HUD draws its own label)
    if (!client.isPracticeMode) {
      val remaining = client.gameTimeRemaining
      if (remaining != _cachedTimerRemaining) {
        _cachedTimerRemaining = remaining
        val minutes = remaining / 60; val seconds = remaining % 60
        _cachedTimerStr = f"$minutes%d:$seconds%02d"
      }
      val timerText = _cachedTimerStr
      val isLowTime = remaining > 0 && remaining <= 30
      val timerPulse = if (isLowTime) (Math.sin(_animTickF * 0.15) * 0.3 + 0.7).toFloat else 1f

      val timerTextW = fontMedium.measureWidth(timerText)
      val timerW = timerTextW + 50
      val timerH = 32f
      val timerX = (screenW / 2 - timerW / 2).toFloat
      val timerY = 6f
      beginShapes()
      if (isLowTime) {
        shapeBatch.fillRoundedRectGradient(timerX, timerY, timerW.toFloat, timerH, 8f,
          0.25f * timerPulse, 0.02f, 0.02f, 0.7f,
          0.12f * timerPulse, 0.01f, 0.01f, 0.6f)
      } else {
        shapeBatch.fillRoundedRectGradient(timerX, timerY, timerW.toFloat, timerH, 8f,
          0.08f, 0.08f, 0.14f, 0.60f,
          0.04f, 0.04f, 0.08f, 0.50f)
        // Subtle top edge highlight
        shapeBatch.strokeLine(timerX + 8, timerY, timerX + timerW.toFloat - 8, timerY, 1f, 1f, 1f, 1f, 0.07f)
      }
      // Detailed clock icon with tick marks, animated hands, and glow
      val clockCX = timerX + 16f
      val clockCY = timerY + timerH / 2f
      val clockR = 7.5f
      // Subtle outer glow
      shapeBatch.fillOvalSoft(clockCX, clockCY, clockR + 3f, clockR + 3f, 0.5f, 0.6f, 0.9f, 0.08f, 0f, 12)
      // Clock face fill (very subtle)
      shapeBatch.fillOval(clockCX, clockCY, clockR - 0.5f, clockR - 0.5f, 0.15f, 0.15f, 0.25f, 0.3f, 14)
      // Outer ring
      shapeBatch.strokeOval(clockCX, clockCY, clockR, clockR, 1.5f, 0.75f, 0.75f, 0.9f, 0.85f, 16)
      // Tick marks at 12, 3, 6, 9 positions
      shapeBatch.strokeLine(clockCX, clockCY - clockR + 1f, clockCX, clockCY - clockR + 2.8f, 1.2f, 0.85f, 0.85f, 0.95f, 0.75f) // 12
      shapeBatch.strokeLine(clockCX + clockR - 1f, clockCY, clockCX + clockR - 2.8f, clockCY, 1.2f, 0.85f, 0.85f, 0.95f, 0.75f) // 3
      shapeBatch.strokeLine(clockCX, clockCY + clockR - 1f, clockCX, clockCY + clockR - 2.8f, 1.2f, 0.85f, 0.85f, 0.95f, 0.75f) // 6
      shapeBatch.strokeLine(clockCX - clockR + 1f, clockCY, clockCX - clockR + 2.8f, clockCY, 1.2f, 0.85f, 0.85f, 0.95f, 0.75f) // 9
      // Minor tick marks at other hours
      ;{ var h = 0; while (h < 12) {
        if (h % 3 != 0) {
          val tickAngle = h * Math.PI / 6 - Math.PI / 2
          val outerR = clockR - 0.8f; val innerR = clockR - 2f
          shapeBatch.strokeLine(
            clockCX + (Math.cos(tickAngle) * innerR).toFloat, clockCY + (Math.sin(tickAngle) * innerR).toFloat,
            clockCX + (Math.cos(tickAngle) * outerR).toFloat, clockCY + (Math.sin(tickAngle) * outerR).toFloat,
            0.7f, 0.6f, 0.6f, 0.75f, 0.5f)
        }
      ; h += 1 } }
      // Minute hand — animated, sweeps based on game time remaining
      val minuteAngle = (remaining % 60) * Math.PI / 30 - Math.PI / 2
      val minuteLen = clockR * 0.7f
      shapeBatch.strokeLine(clockCX, clockCY,
        clockCX + (Math.cos(minuteAngle) * minuteLen).toFloat,
        clockCY + (Math.sin(minuteAngle) * minuteLen).toFloat, 1.3f, 0.95f, 0.95f, 1f, 0.85f)
      // Hour hand (shorter, thicker)
      val hourAngle = (remaining / 60.0) * Math.PI / 6 - Math.PI / 2
      val hourLen = clockR * 0.45f
      shapeBatch.strokeLine(clockCX, clockCY,
        clockCX + (Math.cos(hourAngle) * hourLen).toFloat,
        clockCY + (Math.sin(hourAngle) * hourLen).toFloat, 1.8f, 0.9f, 0.9f, 1f, 0.8f)
      // Center pivot dot
      shapeBatch.fillOval(clockCX, clockCY, 1.2f, 1.2f, 0.95f, 0.95f, 1f, 0.9f, 6)

      beginSprites()
      val timerTextX = (screenW / 2 - timerTextW / 2 + 8).toFloat
      val timerTextY = timerY + (timerH - fontMedium.charHeight) / 2f
      if (isLowTime) {
        fontMedium.drawTextOutlined(spriteBatch, timerText, timerTextX, timerTextY, 1f * timerPulse, 0.25f * timerPulse, 0.25f * timerPulse)
      } else {
        fontMedium.drawTextOutlined(spriteBatch, timerText, timerTextX, timerTextY)
      }
    }

    // Kill feed — collect entries first (needed before shapes pass)
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
        val feedText = entry(3).asInstanceOf[String]
        val textW = fontSmall.measureWidth(feedText)
        val slideOffset = if (elapsed < 200) ((1.0 - elapsed / 200.0) * 60).toFloat else 0f
        val flashBoost = if (elapsed < 200) (1.0 - elapsed / 200.0).toFloat * 0.3f else 0f
        val fx = screenW - textW - 22f + slideOffset
        val fi = _feedCount
        _feedX(fi) = fx; _feedY(fi) = feedY; _feedW(fi) = textW; _feedA(fi) = Math.min(1f, alpha + flashBoost)
        _feedText(fi) = feedText
        val isLocalKiller = killer == localName
        val isLocalVictim = victim == localName
        if (isLocalKiller) { _feedR(fi) = 0.4f; _feedG(fi) = 1f; _feedB(fi) = 0.4f }
        else if (isLocalVictim) { _feedR(fi) = 1f; _feedG(fi) = 0.4f; _feedB(fi) = 0.4f }
        else { _feedR(fi) = 1f; _feedG(fi) = 1f; _feedB(fi) = 1f }
        _feedCount += 1
        feedY += 22
      }
    }

    // K/D display — styled panel top-left
    val kc = client.killCount; val dc = client.deathCount
    if (kc != _cachedKills || dc != _cachedDeaths) {
      _cachedKills = kc; _cachedDeaths = dc
      _cachedKillStr = kc.toString
      _cachedDeathStr = dc.toString
    }
    val kdPanelX = 10f; val kdPanelY = 10f
    val kdPanelW = 130f; val kdPanelH = 56f
    beginShapes()
    shapeBatch.fillRoundedRectGradient(kdPanelX, kdPanelY, kdPanelW, kdPanelH, 8f,
      0.08f, 0.08f, 0.14f, 0.75f,
      0.04f, 0.04f, 0.08f, 0.65f)
    // Top edge highlight
    shapeBatch.strokeLine(kdPanelX + 8, kdPanelY, kdPanelX + kdPanelW - 8, kdPanelY, 1f, 1f, 1f, 1f, 0.10f)
    // Subtle border for definition
    shapeBatch.strokeRect(kdPanelX, kdPanelY, kdPanelW, kdPanelH, 1f, 0.3f, 0.3f, 0.4f, 0.25f)
    // Divider line
    shapeBatch.strokeLine(kdPanelX + kdPanelW / 2, kdPanelY + 6, kdPanelX + kdPanelW / 2, kdPanelY + kdPanelH - 6, 1f, 0.3f, 0.3f, 0.4f, 0.4f)
    // Crossed swords icon (kills) — detailed blades with guard and pommel
    val swordCX = kdPanelX + 16f; val swordCY = kdPanelY + 15f
    // Left sword blade (top-left to bottom-right)
    shapeBatch.strokeLine(swordCX - 5.5f, swordCY - 6.5f, swordCX + 5.5f, swordCY + 6.5f, 2f, 0.55f, 0.95f, 0.55f, 0.9f)
    shapeBatch.strokeLine(swordCX - 5f, swordCY - 6f, swordCX + 4f, swordCY + 5f, 1f, 0.75f, 1f, 0.75f, 0.4f) // blade highlight
    // Right sword blade (top-right to bottom-left)
    shapeBatch.strokeLine(swordCX + 5.5f, swordCY - 6.5f, swordCX - 5.5f, swordCY + 6.5f, 2f, 0.55f, 0.95f, 0.55f, 0.9f)
    shapeBatch.strokeLine(swordCX + 5f, swordCY - 6f, swordCX - 4f, swordCY + 5f, 1f, 0.75f, 1f, 0.75f, 0.4f) // blade highlight
    // Left sword guard (perpendicular to left blade, at crossing point)
    shapeBatch.strokeLine(swordCX - 1f, swordCY + 3.5f, swordCX + 4.5f, swordCY - 0.5f, 1.8f, 0.4f, 0.7f, 0.4f, 0.85f)
    // Right sword guard
    shapeBatch.strokeLine(swordCX + 1f, swordCY + 3.5f, swordCX - 4.5f, swordCY - 0.5f, 1.8f, 0.4f, 0.7f, 0.4f, 0.85f)
    // Pommel dots at the bottom of each blade
    shapeBatch.fillOval(swordCX + 5.5f, swordCY + 7f, 1.5f, 1.5f, 0.6f, 0.95f, 0.6f, 0.8f, 6)
    shapeBatch.fillOval(swordCX - 5.5f, swordCY + 7f, 1.5f, 1.5f, 0.6f, 0.95f, 0.6f, 0.8f, 6)
    // Blade tips (bright dots at top)
    shapeBatch.fillOval(swordCX - 6f, swordCY - 7f, 1.2f, 1.2f, 0.8f, 1f, 0.8f, 0.7f, 4)
    shapeBatch.fillOval(swordCX + 6f, swordCY - 7f, 1.2f, 1.2f, 0.8f, 1f, 0.8f, 0.7f, 4)

    // Skull icon (deaths) — detailed skull with jaw, cracks, and crossbones
    val skullCX = kdPanelX + kdPanelW / 2 + 16f; val skullCY = kdPanelY + 13f
    // Crossbones behind skull
    shapeBatch.strokeLine(skullCX - 7f, skullCY + 5f, skullCX + 7f, skullCY - 1f, 1.5f, 0.7f, 0.18f, 0.18f, 0.5f)
    shapeBatch.strokeLine(skullCX + 7f, skullCY + 5f, skullCX - 7f, skullCY - 1f, 1.5f, 0.7f, 0.18f, 0.18f, 0.5f)
    // Bone end knobs
    shapeBatch.fillOval(skullCX - 7.5f, skullCY + 5.5f, 1.3f, 1.3f, 0.75f, 0.2f, 0.2f, 0.5f, 4)
    shapeBatch.fillOval(skullCX + 7.5f, skullCY + 5.5f, 1.3f, 1.3f, 0.75f, 0.2f, 0.2f, 0.5f, 4)
    shapeBatch.fillOval(skullCX - 7.5f, skullCY - 1.5f, 1.3f, 1.3f, 0.75f, 0.2f, 0.2f, 0.5f, 4)
    shapeBatch.fillOval(skullCX + 7.5f, skullCY - 1.5f, 1.3f, 1.3f, 0.75f, 0.2f, 0.2f, 0.5f, 4)
    // Main cranium
    shapeBatch.fillOval(skullCX, skullCY, 7f, 8f, 0.88f, 0.22f, 0.22f, 0.92f, 14)
    // Cranium highlight (upper left)
    shapeBatch.fillOval(skullCX - 2f, skullCY - 3f, 3f, 2.5f, 1f, 0.4f, 0.4f, 0.3f, 8)
    // Eye sockets (dark, slightly sunken)
    shapeBatch.fillOval(skullCX - 2.8f, skullCY - 1.5f, 2f, 2.2f, 0.08f, 0.02f, 0.02f, 0.95f, 8)
    shapeBatch.fillOval(skullCX + 2.8f, skullCY - 1.5f, 2f, 2.2f, 0.08f, 0.02f, 0.02f, 0.95f, 8)
    // Nose cavity (tiny triangle)
    shapeBatch.fillOval(skullCX, skullCY + 1.5f, 1f, 1.2f, 0.12f, 0.04f, 0.04f, 0.85f, 6)
    // Jaw (separate rect below cranium with teeth marks)
    shapeBatch.fillRect(skullCX - 4.5f, skullCY + 5f, 9f, 3.5f, 0.82f, 0.2f, 0.2f, 0.8f)
    // Teeth lines
    shapeBatch.strokeLine(skullCX - 2.5f, skullCY + 5f, skullCX - 2.5f, skullCY + 8f, 0.5f, 0.12f, 0.04f, 0.04f, 0.6f)
    shapeBatch.strokeLine(skullCX, skullCY + 5f, skullCX, skullCY + 8f, 0.5f, 0.12f, 0.04f, 0.04f, 0.6f)
    shapeBatch.strokeLine(skullCX + 2.5f, skullCY + 5f, skullCX + 2.5f, skullCY + 8f, 0.5f, 0.12f, 0.04f, 0.04f, 0.6f)
    // Crack line on cranium
    shapeBatch.strokeLine(skullCX + 1f, skullCY - 7f, skullCX + 3f, skullCY - 3f, 0.8f, 0.5f, 0.1f, 0.1f, 0.5f)
    shapeBatch.strokeLine(skullCX + 3f, skullCY - 3f, skullCX + 1.5f, skullCY, 0.8f, 0.5f, 0.1f, 0.1f, 0.4f)

    // Kill feed backgrounds — drawn in shapes batch (same batch as K/D panel, proven to work)
    { var fi = 0; while (fi < _feedCount) {
      val entryAlpha = _feedA(fi)
      val bgA = if (entryAlpha > 0.5f) 0.88f else entryAlpha * 1.76f
      val entryW = _feedW(fi) + 28f
      val entryH = 20f
      val ex = _feedX(fi) - 14f
      val ey = _feedY(fi) - 3f
      // Dark background panel
      shapeBatch.fillRect(ex, ey, entryW, entryH, 0.02f, 0.02f, 0.06f, bgA)
      // Left accent bar (colored by kill/death)
      shapeBatch.fillRect(ex, ey + 2f, 3f, entryH - 4f, _feedR(fi), _feedG(fi), _feedB(fi), entryAlpha)
      // Subtle border for visibility
      shapeBatch.strokeRect(ex, ey, entryW, entryH, 1f, 0.3f, 0.3f, 0.5f, bgA * 0.3f)
    ; fi += 1 } }

    beginSprites()
    // Kill count in green (large font for readability)
    fontLarge.drawTextOutlined(spriteBatch, _cachedKillStr, kdPanelX + 29f, kdPanelY + 18f, 0.4f, 1f, 0.4f)
    // Death count in red (large font for readability)
    fontLarge.drawTextOutlined(spriteBatch, _cachedDeathStr, kdPanelX + kdPanelW / 2 + 29f, kdPanelY + 18f, 1f, 0.4f, 0.4f)

    // Kill feed text
    { var fi = 0; while (fi < _feedCount) {
      fontSmall.drawTextOutlined(spriteBatch, _feedText(fi), _feedX(fi) + 2f, _feedY(fi), _feedR(fi), _feedG(fi), _feedB(fi), _feedA(fi))
    ; fi += 1 } }
  }

  // ═══════════════════════════════════════════════════════════════════
  //  CHAT HUD
  // ═══════════════════════════════════════════════════════════════════

  private def renderChat(screenW: Int, screenH: Int): Unit = {
    if (client.clientState != ClientState.PLAYING) return

    val now = _frameTimeMs
    val chatOpen = client.isChatOpen
    val baseY = screenH - 120f // above inventory bar

    // Collect visible messages into deferred arrays
    _chatCount = 0
    val iter = client.chatMessages.iterator()
    // Collect all eligible messages first, then take last MAX_CHAT_DISPLAY
    val tempEntries = new java.util.ArrayList[Array[AnyRef]]()
    while (iter.hasNext) {
      val entry = iter.next()
      val timestamp = entry(0).asInstanceOf[java.lang.Long].longValue()
      val elapsed = now - timestamp
      if (chatOpen || elapsed < 10000) {
        tempEntries.add(entry)
      }
    }

    // Take last MAX_CHAT_DISPLAY entries
    val startIdx = Math.max(0, tempEntries.size() - MAX_CHAT_DISPLAY)
    var i = startIdx
    while (i < tempEntries.size()) {
      val entry = tempEntries.get(i)
      val timestamp = entry(0).asInstanceOf[java.lang.Long].longValue()
      val sender = entry(1).asInstanceOf[String]
      val msg = entry(2).asInstanceOf[String]
      val scope = entry(3).asInstanceOf[java.lang.Byte].byteValue()
      val elapsed = now - timestamp

      val alpha = if (chatOpen) 0.9f
                  else Math.max(0.1f, (1.0 - elapsed / 10000.0).toFloat)

      val displayText = if (sender.isEmpty) msg else sender + ": " + msg
      val textW = fontSmall.measureWidth(displayText)
      val ci = _chatCount
      val yPos = baseY - (tempEntries.size() - startIdx - _chatCount) * 18f
      _chatX(ci) = 12f
      _chatY(ci) = yPos
      _chatW(ci) = textW
      _chatA(ci) = alpha
      _chatText(ci) = displayText

      if (sender.isEmpty) {
        // System message
        _chatR(ci) = 0.5f; _chatG(ci) = 0.55f; _chatB(ci) = 0.6f
      } else if (scope == 2) {
        // Team chat - green
        _chatR(ci) = 0.3f; _chatG(ci) = 1f; _chatB(ci) = 0.4f
      } else {
        // Game/lobby chat - white
        _chatR(ci) = 1f; _chatG(ci) = 1f; _chatB(ci) = 1f
      }
      _chatCount += 1
      i += 1
    }

    // Render backgrounds (shapes pass)
    if (_chatCount > 0 || chatOpen) {
      beginShapes()
      var ci = 0
      while (ci < _chatCount) {
        shapeBatch.fillRoundedRect(_chatX(ci) - 4f, _chatY(ci) - 2f, _chatW(ci) + 8f, 18f, 4f, 0.04f, 0.04f, 0.08f, 0.4f * _chatA(ci))
        ci += 1
      }

      // Input box when chat is open
      if (chatOpen) {
        val inputY = baseY + 4f
        shapeBatch.fillRoundedRect(8f, inputY, 340f, 24f, 6f, 0.06f, 0.06f, 0.12f, 0.85f)
      }

      // Render text (sprites pass)
      beginSprites()
      ci = 0
      while (ci < _chatCount) {
        fontSmall.drawText(spriteBatch, _chatText(ci), _chatX(ci), _chatY(ci), _chatR(ci), _chatG(ci), _chatB(ci), _chatA(ci))
        ci += 1
      }

      if (chatOpen) {
        val inputY = baseY + 4f
        val inputText = client.chatInputText
        val cursor = if ((now / 500) % 2 == 0) "_" else ""
        fontSmall.drawText(spriteBatch, "> " + inputText + cursor, 14f, inputY + 4f, 0.9f, 0.9f, 0.95f, 1f)
        fontSmall.drawText(spriteBatch, "[Enter] Send  [Shift+Enter] Team  [Esc] Cancel", 14f, inputY + 28f, 0.45f, 0.45f, 0.5f, 0.7f)
      }
    }
  }

  // ═══════════════════════════════════════════════════════════════════
  //  PRACTICE HUD
  // ═══════════════════════════════════════════════════════════════════

  // Hit marker timestamps (small ring buffer for simultaneous kill markers)
  private val practiceHitMarkers = new Array[Long](8)
  private var practiceHitMarkerIdx = 0
  private var prevPracticeHits = 0

  private def renderPracticeHUD(screenW: Int, screenH: Int): Unit = {
    val now = _frameTimeMs
    val cx = screenW / 2f
    val cy = screenH / 2f

    // Reset combo if 2 seconds since last hit
    if (client.practiceLastHitTime > 0 && now - client.practiceLastHitTime > 2000) {
      client.practiceCombo = 0
    }

    // Detect new kills for hit markers
    val currentHits = client.practiceHits
    if (currentHits > prevPracticeHits) {
      practiceHitMarkers(practiceHitMarkerIdx % practiceHitMarkers.length) = now
      practiceHitMarkerIdx += 1
      prevPracticeHits = currentHits
    }

    // --- Combo counter (center, below timer) ---
    val combo = client.practiceCombo
    if (combo > 0) {
      val comboText = "COMBO x" + combo
      val textW = fontMedium.measureWidth(comboText)

      // Color scales white -> yellow -> orange -> red with combo size
      val t = Math.min(1f, combo / 10f)
      val cr = 1f
      val cg = Math.max(0.2f, 1f - t * 0.8f)
      val cb = Math.max(0.1f, 1f - t)

      // Gentle pulse animation
      val pulse = 1f + 0.05f * Math.sin(now * 0.006).toFloat

      beginShapes()
      val comboW = textW + 24
      val comboH = 32f
      val comboX = cx - comboW / 2
      val comboY = 42f
      shapeBatch.fillRoundedRect(comboX, comboY, comboW * pulse, comboH, 6f, cr * 0.15f, cg * 0.15f, cb * 0.15f, 0.6f)

      beginSprites()
      fontMedium.drawText(spriteBatch, comboText, cx - textW / 2, comboY + 6, cr, cg, cb, 1f)
    }

    // --- Hit markers (screen center) ---
    beginShapes()
    var mi = 0
    while (mi < practiceHitMarkers.length) {
      val markerTime = practiceHitMarkers(mi)
      if (markerTime > 0) {
        val elapsed = now - markerTime
        if (elapsed < 400) {
          val alpha = (1f - elapsed / 400f) * 0.9f
          val size = 12f
          // Draw X marker (4 strokes)
          shapeBatch.strokeLine(cx - size, cy - size, cx + size, cy + size, 2f, 1f, 1f, 1f, alpha)
          shapeBatch.strokeLine(cx + size, cy - size, cx - size, cy + size, 2f, 1f, 1f, 1f, alpha)
        } else {
          practiceHitMarkers(mi) = 0
        }
      }
      mi += 1
    }

    // --- Accuracy display (top-left, below K/D) ---
    beginSprites()
    val accuracy = if (client.practiceShots > 0) (client.practiceHits * 100.0 / client.practiceShots).toInt else 0
    val accText = "Accuracy: " + accuracy + "%"
    fontSmall.drawTextOutlined(spriteBatch, accText, 12, 126)
    val bestText = "Best Combo: " + client.practiceBestCombo
    fontSmall.drawTextOutlined(spriteBatch, bestText, 12, 144)

    // --- "PRACTICE" label replacing timer ---
    val practiceText = "PRACTICE"
    val ptw = fontMedium.measureWidth(practiceText)
    fontMedium.drawText(spriteBatch, practiceText, cx - ptw / 2, 8, 0.24f, 0.86f, 0.5f, 0.9f)
  }

  // ═══════════════════════════════════════════════════════════════════
  //  GAME OVER + RESPAWN SCREENS
  // ═══════════════════════════════════════════════════════════════════

  private def renderGameOverScreen(fbWidth: Int, fbHeight: Int): Unit = {
    val proj = Matrix4.orthographic(0f, fbWidth.toFloat, fbHeight.toFloat, 0f)
    glViewport(0, 0, fbWidth, fbHeight)
    glClearColor(0f, 0f, 0f, 1f)
    glClear(GL_COLOR_BUFFER_BIT)

    val cx = fbWidth / 2f; val cy = fbHeight / 2f
    val t = _animTickF

    shapeBatch.begin(proj)
    // Dark vignette background
    shapeBatch.fillRect(0, 0, fbWidth.toFloat, fbHeight.toFloat, 0f, 0f, 0f, 0.8f)
    // Red vignette edges
    val vigAlpha = 0.2f
    shapeBatch.fillRectGradient(0, 0, fbWidth.toFloat, fbHeight * 0.3f,
      0.35f, 0f, 0f, vigAlpha, 0.35f, 0f, 0f, vigAlpha,
      0.35f, 0f, 0f, 0f, 0.35f, 0f, 0f, 0f)
    shapeBatch.fillRectGradient(0, fbHeight * 0.7f, fbWidth.toFloat, fbHeight * 0.3f,
      0.35f, 0f, 0f, 0f, 0.35f, 0f, 0f, 0f,
      0.35f, 0f, 0f, vigAlpha, 0.35f, 0f, 0f, vigAlpha)

    // Central glassmorphism panel
    val panelW = 380f; val panelH = 160f
    val panelX = cx - panelW / 2; val panelY = cy - panelH / 2
    shapeBatch.fillRoundedRectGradient(panelX, panelY, panelW, panelH, 12f,
      0.12f, 0.04f, 0.04f, 0.75f,
      0.06f, 0.02f, 0.02f, 0.65f)
    // Top edge highlight
    shapeBatch.strokeLine(panelX + 12, panelY, panelX + panelW - 12, panelY, 1f, 1f, 1f, 1f, 0.06f)

    // Large skull icon above text
    val skullY = panelY + 30f
    val skullPulse = (Math.sin(t * 0.08) * 0.1 + 0.9).toFloat
    // Skull glow
    shapeBatch.fillOvalSoft(cx, skullY, 22f, 22f, 0.9f, 0.15f, 0.15f, 0.15f * skullPulse, 0f, 12)
    // Cranium
    shapeBatch.fillOval(cx, skullY, 14f, 16f, 0.92f * skullPulse, 0.2f, 0.2f, 0.95f, 16)
    shapeBatch.fillOval(cx - 3.5f, skullY - 1f, 2.5f, 2.5f, 0.08f, 0.02f, 0.02f, 0.95f, 8)
    // Eye sockets
    shapeBatch.fillOval(cx - 5f, skullY - 2.5f, 3.5f, 4f, 0.08f, 0.02f, 0.02f, 0.95f, 8)
    shapeBatch.fillOval(cx + 5f, skullY - 2.5f, 3.5f, 4f, 0.08f, 0.02f, 0.02f, 0.95f, 8)
    // Nose
    shapeBatch.fillOval(cx, skullY + 3f, 1.5f, 2f, 0.12f, 0.04f, 0.04f, 0.9f, 6)
    // Jaw with teeth
    shapeBatch.fillRect(cx - 8f, skullY + 9f, 16f, 5f, 0.85f * skullPulse, 0.18f, 0.18f, 0.85f)
    shapeBatch.strokeLine(cx - 4f, skullY + 9f, cx - 4f, skullY + 13.5f, 0.5f, 0.12f, 0.04f, 0.04f, 0.6f)
    shapeBatch.strokeLine(cx, skullY + 9f, cx, skullY + 13.5f, 0.5f, 0.12f, 0.04f, 0.04f, 0.6f)
    shapeBatch.strokeLine(cx + 4f, skullY + 9f, cx + 4f, skullY + 13.5f, 0.5f, 0.12f, 0.04f, 0.04f, 0.6f)

    // Decorative horizontal line separator
    shapeBatch.strokeLine(panelX + 40, panelY + 60f, panelX + panelW - 40, panelY + 60f, 1f,
      0.9f, 0.25f, 0.25f, 0.3f)
    shapeBatch.end()

    spriteBatch.begin(proj)
    // "GAME OVER" text with dramatic styling
    fontLarge.drawTextOutlined(spriteBatch, "GAME OVER", cx - fontLarge.measureWidth("GAME OVER") / 2, panelY + 74f, 0.95f * skullPulse, 0.2f, 0.2f, 1f)
    val subText = "Press Enter to continue"
    val subPulse = (Math.sin(t * 0.1) * 0.3 + 0.7).toFloat
    fontMedium.drawTextOutlined(spriteBatch, subText, cx - fontMedium.measureWidth(subText) / 2, panelY + 118f, 0.7f, 0.7f, 0.7f, subPulse)
    spriteBatch.end()
  }

  private def renderRespawnCountdown(screenW: Int, screenH: Int): Unit = {
    val deathTime = client.getLocalDeathTime
    val elapsed = _frameTimeMs - deathTime
    val remaining = Math.max(0, Constants.RESPAWN_DELAY_MS - elapsed)
    val secondsLeft = Math.ceil(remaining / 1000.0).toInt
    val progress = Math.min(1.0, elapsed.toDouble / Constants.RESPAWN_DELAY_MS).toFloat

    val cx = screenW / 2f; val cy = screenH / 2f

    // Red vignette overlay across entire screen
    val vigAlpha = 0.25f * (1f - progress * 0.5f)
    beginShapes()
    // Top edge
    shapeBatch.fillRectGradient(0, 0, screenW.toFloat, screenH * 0.25f,
      0.4f, 0f, 0f, vigAlpha, 0.4f, 0f, 0f, vigAlpha,
      0.4f, 0f, 0f, 0f, 0.4f, 0f, 0f, 0f)
    // Bottom edge
    shapeBatch.fillRectGradient(0, screenH * 0.75f, screenW.toFloat, screenH * 0.25f,
      0.4f, 0f, 0f, 0f, 0.4f, 0f, 0f, 0f,
      0.4f, 0f, 0f, vigAlpha, 0.4f, 0f, 0f, vigAlpha)
    // Left edge
    shapeBatch.fillRectGradient(0, 0, screenW * 0.2f, screenH.toFloat,
      0.4f, 0f, 0f, vigAlpha, 0.4f, 0f, 0f, 0f,
      0.4f, 0f, 0f, 0f, 0.4f, 0f, 0f, vigAlpha)
    // Right edge
    shapeBatch.fillRectGradient(screenW * 0.8f, 0, screenW * 0.2f, screenH.toFloat,
      0.4f, 0f, 0f, 0f, 0.4f, 0f, 0f, vigAlpha,
      0.4f, 0f, 0f, vigAlpha, 0.4f, 0f, 0f, 0f)

    // Central panel (glassmorphism)
    val panelW = 280f; val panelH = 120f
    val panelX = cx - panelW / 2; val panelY = cy - panelH / 2
    shapeBatch.fillRoundedRectGradient(panelX, panelY, panelW, panelH, 10f,
      0.10f, 0.03f, 0.03f, 0.70f,
      0.05f, 0.01f, 0.01f, 0.60f)
    // Top edge highlight
    shapeBatch.strokeLine(panelX + 10, panelY, panelX + panelW - 10, panelY, 1f, 1f, 1f, 1f, 0.06f)

    // Progress bar at bottom of panel
    val barX = panelX + 20f; val barY = panelY + panelH - 18f
    val barW = panelW - 40f; val barH = 6f
    shapeBatch.fillRoundedRect(barX, barY, barW, barH, 3f, 0.15f, 0.05f, 0.05f, 0.6f)
    shapeBatch.fillRoundedRect(barX, barY, barW * progress, barH, 3f, 0.8f, 0.2f, 0.2f, 0.8f)

    // Detailed skull icon above text
    val skullY = panelY + 20f
    // Skull glow
    shapeBatch.fillOvalSoft(cx, skullY, 18f, 18f, 0.9f, 0.15f, 0.15f, 0.12f, 0f, 12)
    // Cranium
    shapeBatch.fillOval(cx, skullY, 11f, 13f, 0.92f, 0.22f, 0.22f, 0.92f, 14)
    // Highlight on cranium
    shapeBatch.fillOval(cx - 3f, skullY - 4f, 4f, 3f, 1f, 0.4f, 0.4f, 0.25f, 8)
    // Eye sockets
    shapeBatch.fillOval(cx - 4f, skullY - 2f, 2.8f, 3f, 0.08f, 0.02f, 0.02f, 0.95f, 8)
    shapeBatch.fillOval(cx + 4f, skullY - 2f, 2.8f, 3f, 0.08f, 0.02f, 0.02f, 0.95f, 8)
    // Nose
    shapeBatch.fillOval(cx, skullY + 2.5f, 1.2f, 1.8f, 0.12f, 0.04f, 0.04f, 0.85f, 6)
    // Jaw with teeth
    shapeBatch.fillRect(cx - 6.5f, skullY + 7f, 13f, 4.5f, 0.85f, 0.2f, 0.2f, 0.8f)
    shapeBatch.strokeLine(cx - 3f, skullY + 7f, cx - 3f, skullY + 11f, 0.5f, 0.12f, 0.04f, 0.04f, 0.55f)
    shapeBatch.strokeLine(cx, skullY + 7f, cx, skullY + 11f, 0.5f, 0.12f, 0.04f, 0.04f, 0.55f)
    shapeBatch.strokeLine(cx + 3f, skullY + 7f, cx + 3f, skullY + 11f, 0.5f, 0.12f, 0.04f, 0.04f, 0.55f)
    // Crack line
    shapeBatch.strokeLine(cx + 2f, skullY - 11f, cx + 4f, skullY - 5f, 0.8f, 0.5f, 0.1f, 0.1f, 0.4f)

    beginSprites()
    // "Killed by [Name]" — prominent
    val killerName = client.lastKillerCharacterName
    if (killerName != null && killerName.nonEmpty) {
      if (killerName ne _cachedKilledByName) {
        _cachedKilledByName = killerName
        _cachedKilledByStr = "Killed by " + killerName
      }
      val kbW = fontMedium.measureWidth(_cachedKilledByStr)
      fontMedium.drawTextOutlined(spriteBatch, _cachedKilledByStr, cx - kbW / 2, panelY + 42f, 1f, 0.5f, 0.5f)
    }

    // Respawn countdown
    if (secondsLeft != _cachedRespawnSeconds) {
      _cachedRespawnSeconds = secondsLeft
      _cachedRespawnStr = "Respawning in " + secondsLeft + "s"
    }
    val rsW = fontSmall.measureWidth(_cachedRespawnStr)
    fontSmall.drawTextOutlined(spriteBatch, _cachedRespawnStr, cx - rsW / 2, panelY + 72f, 0.7f, 0.7f, 0.7f)
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
          // Animated caustic bright spots
          var ci = 0
          while (ci < 2) {
            val caustPhase = time * 0.025f + wx * 3.1f + wy * 2.7f + ci * 4.2f
            val cxOff = Math.sin(caustPhase).toFloat * 8f
            val cyOff = Math.cos(caustPhase * 0.7f).toFloat * 4f
            val ca = (0.08f + 0.06f * Math.sin(caustPhase * 1.8f)).toFloat
            shapeBatch.fillOvalSoft(sx + cxOff, sy + cyOff, 6f, 3f, 0.5f, 0.8f, 1f, ca, 0f, 8)
            ci += 1
          }

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
          // Occasional upward lava ember particles
          if (rng.nextFloat() < 0.02f) {
            val ex = sx + rng.nextFloat() * 16f - 8f
            val ey = sy + rng.nextFloat() * 4f - 2f
            combatParticles.emit(ex, ey,
              rng.nextFloat() * 4f - 2f, -(8f + rng.nextFloat() * 12f),
              plife = 0.6f + rng.nextFloat() * 0.4f,
              pr = 1f, pg = 0.5f + rng.nextFloat() * 0.3f, pb = 0.1f,
              palpha = 0.7f, psize = 1.5f + rng.nextFloat(),
              pgravity = -5f, additive = true, shrink = true)
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
        lightSystem.addLight(exsx, exsy, 180f * decay, er, eg, eb, 0.6f * decay)
        // Brief secondary white flash for the first 200ms of explosion
        if (elapsed < 200) {
          val flashIntensity = 1f - elapsed.toFloat / 200f
          lightSystem.addLight(exsx, exsy, 140f * flashIntensity, 1f, 1f, 1f, 0.5f * flashIntensity)
        }
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
      // Screen shake proportional to damage (3px base + 0.3px per damage, capped at 18)
      camera.addShake(3.0 + dmg * 0.3)
      // Hit ripple particles
      val lsx = worldToScreenX(lvx.toFloat, lvy.toFloat).toFloat
      val lsy = worldToScreenY(lvx.toFloat, lvy.toFloat).toFloat - 10f
      combatParticles.emitRing(lsx, lsy, 8, 80f, 0.25f, 1f, 0.8f, 0.3f, 0.7f, 3f)
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
        // Hit ripple particles
        val rsx = worldToScreenX(pvx.toFloat, pvy.toFloat).toFloat
        val rsy = worldToScreenY(pvx.toFloat, pvy.toFloat).toFloat - 10f
        combatParticles.emitRing(rsx, rsy, 8, 80f, 0.25f, 1f, 0.8f, 0.3f, 0.7f, 3f)
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

  private var ambientSpawnAccum: Float = 0f

  private def spawnGameplayParticles(dt: Float): Unit = {
    spawnFootstepDust(dt)
    spawnProjectileTrails(dt)
    spawnAmbientMotes(dt)
  }

  /** Spawn ambient energy motes floating near the terrain — adds life to the world. */
  private def spawnAmbientMotes(dt: Float): Unit = {
    ambientSpawnAccum += dt * 3f // ~3 particles per second
    while (ambientSpawnAccum >= 1f) {
      ambientSpawnAccum -= 1f
      // Random visible screen position
      val w = canvasW.toFloat; val h = canvasH.toFloat
      val px = rng.nextFloat() * w
      val py = rng.nextFloat() * h * 0.85f + h * 0.05f
      // Gentle floating motion (slowly upward with slight horizontal drift)
      val vxp = rng.nextFloat() * 6f - 3f
      val vyp = -(1.5f + rng.nextFloat() * 3f)
      // Color varies: warm gold, cool cyan, or soft white
      val colorRoll = rng.nextFloat()
      val (mr, mg, mb) = if (colorRoll < 0.35f) (1f, 0.9f, 0.5f) // warm gold
        else if (colorRoll < 0.65f) (0.5f, 0.85f, 1f) // cool cyan
        else (0.9f, 0.9f, 0.95f) // soft white
      combatParticles.emit(
        px, py, vxp, vyp,
        plife = 2.5f + rng.nextFloat() * 2f,
        pr = mr, pg = mg, pb = mb,
        palpha = 0.06f + rng.nextFloat() * 0.06f,
        psize = 1f + rng.nextFloat() * 1.5f,
        pdrag = 0.3f, additive = true, soft = true
      )
    }
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
      if (footstepAccum >= 0.10f) {
        footstepAccum = 0f
        val sx = worldToScreenX(lvx, lvy).toFloat
        val sy = worldToScreenY(lvx, lvy).toFloat
        // Dust puffs at feet — more particles, slightly larger
        var _k = 0
        while (_k < 4) {
          combatParticles.emit(
            sx + rng.nextFloat() * 8f - 4f, sy + rng.nextFloat() * 3f - 1f,
            rng.nextFloat() * 10f - 5f, -(3f + rng.nextFloat() * 5f),
            plife = 0.5f + rng.nextFloat() * 0.35f,
            pr = 0.55f, pg = 0.50f, pb = 0.42f, palpha = 0.28f,
            psize = 2f + rng.nextFloat() * 2f,
            pgravity = 6f, soft = true
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
    while (iter.hasNext) {
      val proj = iter.next()
      val chargeT = proj.chargeLevel / 100f
      // Spawn probability: 35% base → 70% at full charge
      val spawnChance = 0.35f + chargeT * 0.35f
      if (rng.nextFloat() < spawnChance) {
        val sx = worldToScreenX(proj.getX, proj.getY).toFloat
        val sy = worldToScreenY(proj.getX, proj.getY).toFloat
        intToRGB(proj.colorRGB)
        val pr = _rgb_r; val pg = _rgb_g; val pb = _rgb_b
        // Particle alpha and size scale with charge
        val chgAlpha = 0.30f + chargeT * 0.25f
        val chgSize = 2f + rng.nextFloat() * 1.5f + chargeT * 2f
        // Brighter trails for charged projectiles
        val bright = 0.3f + chargeT * 0.2f
        // Core trail particle
        combatParticles.emit(
          sx + rng.nextFloat() * 4f - 2f, sy + rng.nextFloat() * 2f - 1f,
          rng.nextFloat() * 4f - 2f, rng.nextFloat() * 4f - 2f,
          plife = 0.3f + rng.nextFloat() * 0.2f,
          pr = clamp(pr * (1f - bright) + bright), pg = clamp(pg * (1f - bright) + bright), pb = clamp(pb * (1f - bright) + bright),
          palpha = chgAlpha,
          psize = chgSize,
          additive = true
        )
        // High-charge radial sparks (>60%): shoot outward with gravity
        if (chargeT > 0.6f && rng.nextFloat() < (chargeT - 0.5f) * 0.8f) {
          val angle = rng.nextFloat() * Math.PI.toFloat * 2f
          val speed = 20f + rng.nextFloat() * 30f
          combatParticles.emit(
            sx, sy,
            Math.cos(angle).toFloat * speed, Math.sin(angle).toFloat * speed - 8f,
            plife = 0.2f + rng.nextFloat() * 0.15f,
            pr = clamp(pr * 0.4f + 0.6f), pg = clamp(pg * 0.4f + 0.6f), pb = clamp(pb * 0.4f + 0.6f),
            palpha = 0.4f + chargeT * 0.2f,
            psize = 1.2f + rng.nextFloat() * 1.2f,
            pgravity = 15f
          )
        }
        // Boomerang pulsing soft particles when returning
        if (proj.isReturning && rng.nextFloat() < 0.5f) {
          combatParticles.emit(
            sx + rng.nextFloat() * 6f - 3f, sy + rng.nextFloat() * 4f - 2f,
            rng.nextFloat() * 2f - 1f, rng.nextFloat() * 2f - 1f,
            plife = 0.3f + rng.nextFloat() * 0.2f,
            pr = clamp(pr * 0.5f + 0.5f), pg = clamp(pg * 0.5f + 0.5f), pb = clamp(pb * 0.5f + 0.5f),
            palpha = 0.35f,
            psize = 2.5f + rng.nextFloat() * 2.5f,
            soft = true
          )
        }
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

    // Single-layer shadow for ambient occlusion
    val shadowDist = 5f; val shadowAlpha = 0.22f

    // Check south neighbor (wx, wy+1)
    if (wy + 1 < world.height && world.getTile(wx, wy + 1).walkable) {
      _shadowXs(0) = sx; _shadowXs(1) = sx + hw; _shadowXs(2) = sx + hw; _shadowXs(3) = sx
      _shadowYs(0) = sy + hh; _shadowYs(1) = sy; _shadowYs(2) = sy + shadowDist; _shadowYs(3) = sy + hh + shadowDist
      shapeBatch.fillPolygon(_shadowXs, _shadowYs, 4, 0.0f, 0.0f, 0.03f, shadowAlpha)
    }
    // Check east neighbor (wx+1, wy)
    if (wx + 1 < world.width && world.getTile(wx + 1, wy).walkable) {
      _shadowXs(0) = sx; _shadowXs(1) = sx - hw; _shadowXs(2) = sx - hw; _shadowXs(3) = sx
      _shadowYs(0) = sy + hh; _shadowYs(1) = sy; _shadowYs(2) = sy + shadowDist; _shadowYs(3) = sy + hh + shadowDist
      shapeBatch.fillPolygon(_shadowXs, _shadowYs, 4, 0.0f, 0.0f, 0.03f, shadowAlpha)
    }
    // Check north neighbor (wx, wy-1) — subtler shadows
    if (wy - 1 >= 0 && world.getTile(wx, wy - 1).walkable) {
      _shadowXs(0) = sx; _shadowXs(1) = sx - hw; _shadowXs(2) = sx - hw; _shadowXs(3) = sx
      _shadowYs(0) = sy - hh; _shadowYs(1) = sy; _shadowYs(2) = sy - shadowDist; _shadowYs(3) = sy - hh - shadowDist
      shapeBatch.fillPolygon(_shadowXs, _shadowYs, 4, 0.0f, 0.0f, 0.03f, shadowAlpha * 0.4f)
    }
    // Check west neighbor (wx-1, wy) — subtler shadows
    if (wx - 1 >= 0 && world.getTile(wx - 1, wy).walkable) {
      _shadowXs(0) = sx; _shadowXs(1) = sx + hw; _shadowXs(2) = sx + hw; _shadowXs(3) = sx
      _shadowYs(0) = sy - hh; _shadowYs(1) = sy; _shadowYs(2) = sy - shadowDist; _shadowYs(3) = sy - hh - shadowDist
      shapeBatch.fillPolygon(_shadowXs, _shadowYs, 4, 0.0f, 0.0f, 0.03f, shadowAlpha * 0.4f)
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
      if (bgCacheFBO != null) bgCacheFBO.dispose()
      if (whitePixel != null) whitePixel.dispose()
      GLTileRenderer.dispose()
      GLSpriteGenerator.clearCache()
      initialized = false
    }
  }
}
