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
    initialized = true
  }

  def render(deltaSec: Double, fbWidth: Int, fbHeight: Int, windowWidth: Int, windowHeight: Int): Unit = {
    ensureInitialized(fbWidth, fbHeight)
    animationTick += 1

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

    // Update camera with smooth interpolation
    val (cox, coy) = camera.update(
      localPos.getX.toDouble, localPos.getY.toDouble,
      deltaSec, canvasW, canvasH
    )
    camOffX = cox
    camOffY = coy

    // Post-processing: render to FBO
    postProcessor.resize(fbWidth, fbHeight)
    postProcessor.beginScene()

    // Set up projection for zoomed world-space rendering
    projection = Matrix4.orthographic(0f, canvasW.toFloat, canvasH.toFloat, 0f)

    // === Background ===
    beginShapes()
    renderBackground(world.background)

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
      localVX, localVY, localDeathAnimActive
    )

    // === Phase 1: Ground tiles ===
    beginSprites()
    for (wy <- startY to endY; wx <- startX to endX) {
      val tile = world.getTile(wx, wy)
      if (tile.walkable) {
        val region = GLTileRenderer.getTileRegion(tile.id, tileFrame)
        if (region != null) {
          val sx = worldToScreenX(wx, wy).toFloat
          val sy = worldToScreenY(wx, wy).toFloat
          spriteBatch.draw(region, sx - HW, sy - (cellH - HH), tileW, tileCellH)
        }
      }
    }

    // === Aim arrow ===
    drawAimArrow()

    // === Phase 2: Elevated tiles + entities interleaved by depth ===
    for (wy <- startY to endY; wx <- startX to endX) {
      val tile = world.getTile(wx, wy)
      if (!tile.walkable) {
        val region = GLTileRenderer.getTileRegion(tile.id, tileFrame)
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

    // === Overlay animations ===
    drawDeathAnimations()
    drawTeleportAnimations()
    drawExplosionAnimations()
    drawAoeSplashAnimations()

    // === End scene, apply post-processing ===
    // Set damage flash overlay
    val localHitTime = client.getPlayerHitTime(client.getLocalPlayerId)
    if (localHitTime > 0) {
      val elapsed = System.currentTimeMillis() - localHitTime
      if (elapsed < HIT_ANIMATION_MS) {
        val progress = elapsed.toDouble / HIT_ANIMATION_MS
        postProcessor.overlayR = 0.8f
        postProcessor.overlayG = 0f
        postProcessor.overlayB = 0f
        postProcessor.overlayA = (0.15f * (1f - progress.toFloat))
      } else {
        postProcessor.overlayA = 0f
      }
    } else {
      postProcessor.overlayA = 0f
    }

    endAll()
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
    shapeBatch.fillOval(screenX.toFloat, screenY.toFloat, 16f, 6f, 0f, 0f, 0f, 0.3f)
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

    // Sprite
    val region = GLSpriteGenerator.getSpriteRegion(player.getDirection, frame, player.getCharacterId)
    if (region != null) {
      val alpha = if (playerIsPhased) 0.4f else 1f
      beginSprites()
      spriteBatch.draw(region,
        (screenX - displaySz / 2.0).toFloat, (screenY - displaySz).toFloat,
        displaySz.toFloat, displaySz.toFloat, 1f, 1f, 1f, alpha)
    }

    // Post-player effects
    if (player.isFrozen) drawFrozenEffect(screenX, spriteCenter)
    if (player.isRooted) drawRootedEffect(screenX, spriteCenter)
    if (player.isSlowed) drawSlowedEffect(screenX, spriteCenter)
    if (player.isBurning) drawBurnEffect(screenX, spriteCenter)
    if (player.hasSpeedBoost) drawSpeedBoostEffect(screenX, spriteCenter)
    drawHitEffect(screenX, spriteCenter, client.getPlayerHitTime(playerId))

    // Health bar + name
    drawHealthBar(screenX, screenY - displaySz, player.getHealth, player.getMaxHealth, player.getTeamId)
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
    val region = GLSpriteGenerator.getSpriteRegion(client.getLocalDirection, frame, client.selectedCharacterId)
    if (region != null) {
      val alpha = if (localIsPhased) 0.4f else 1f
      beginSprites()
      spriteBatch.draw(region,
        (screenX - displaySz / 2.0).toFloat, (screenY - displaySz).toFloat,
        displaySz.toFloat, displaySz.toFloat, 1f, 1f, 1f, alpha)
    }

    drawCastFlash(screenX, spriteCenter)
    if (client.isFrozen) drawFrozenEffect(screenX, spriteCenter)
    if (client.isRooted) drawRootedEffect(screenX, spriteCenter)
    if (client.isSlowed) drawSlowedEffect(screenX, spriteCenter)
    if (client.isBurning) drawBurnEffect(screenX, spriteCenter)
    if (client.hasSpeedBoost) drawSpeedBoostEffect(screenX, spriteCenter)

    val localHitTime = client.getPlayerHitTime(client.getLocalPlayerId)
    if (localHitTime > 0 && System.currentTimeMillis() - localHitTime < 50) {
      camera.triggerShake(2.5, 250)
    }
    drawHitEffect(screenX, spriteCenter, localHitTime)

    drawHealthBar(screenX, screenY - displaySz, client.getLocalHealth, client.getSelectedCharacterMaxHealth, client.localTeamId)
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
    beginShapes()
    shapeBatch.setAdditiveBlend(true)
    shapeBatch.fillOval(cx.toFloat, cy.toFloat, 16f * fadeOut, 12f * fadeOut, 1f, 0.3f, 0.1f, 0.3f * fadeOut, 12)
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
    val ix = item.getCellX; val iy = item.getCellY
    val centerX = worldToScreenX(ix, iy).toFloat
    val halfSize = Constants.ITEM_SIZE_PX / 2f

    val bobPhase = animationTick * 0.06f + item.id * 1.7f
    val bobOffset = Math.sin(bobPhase).toFloat * 4f
    val centerY = worldToScreenY(ix, iy).toFloat + bobOffset

    val (ir, ig, ib) = intToRGB(item.colorRGB)
    val glowPulse = (0.8 + 0.2 * Math.sin(bobPhase * 1.3)).toFloat

    beginShapes()
    // Ground glow (additive)
    shapeBatch.setAdditiveBlend(true)
    shapeBatch.fillOvalSoft(centerX, centerY + halfSize * 0.3f, halfSize * 1.4f, halfSize * 0.4f, ir, ig, ib, 0.12f * glowPulse, 0f, 12)
    shapeBatch.fillOvalSoft(centerX, centerY, halfSize * 1.8f, halfSize * 1.4f, ir, ig, ib, 0.06f * glowPulse, 0f, 12)
    shapeBatch.setAdditiveBlend(false)

    // Main item shape
    drawItemShape(item.itemType, centerX, centerY, halfSize, ir, ig, ib)

    // Sparkle particles (additive)
    shapeBatch.setAdditiveBlend(true)
    for (i <- 0 until 3) {
      val sparkAngle = bobPhase * 0.8f + i * (2 * Math.PI / 3).toFloat
      val sparkDist = halfSize * 1.1f
      val sx = centerX + sparkDist * Math.cos(sparkAngle).toFloat
      val sy = centerY + sparkDist * Math.sin(sparkAngle).toFloat * 0.6f
      val sparkAlpha = (0.3 + 0.4 * Math.sin(bobPhase * 2.5 + i * 2.1)).toFloat
      val sparkSize = (1.5 + Math.sin(bobPhase * 3.0 + i) * 0.7).toFloat
      shapeBatch.fillOval(sx, sy, sparkSize, sparkSize, 1f, 1f, 1f, clamp(sparkAlpha))
    }
    shapeBatch.setAdditiveBlend(false)
  }

  private def drawItemShape(itemType: ItemType, cx: Float, cy: Float, hs: Float, r: Float, g: Float, b: Float): Unit = {
    itemType match {
      case ItemType.Heart =>
        val rr = hs * 0.55f
        shapeBatch.fillOval(cx - hs * 0.5f, cy - hs * 0.45f, rr, rr, r, g, b, 1f, 12)
        shapeBatch.fillOval(cx + hs * 0.5f, cy - hs * 0.45f, rr, rr, r, g, b, 1f, 12)
        shapeBatch.fillPolygon(
          Array(cx - hs * 1.05f, cx, cx + hs * 1.05f),
          Array(cy - hs * 0.1f, cy + hs, cy - hs * 0.1f), r, g, b, 1f)
      case ItemType.Star =>
        val outerR = hs; val innerR = hs * 0.4f
        val xs = new Array[Float](10); val ys = new Array[Float](10)
        for (i <- 0 until 10) {
          val angle = Math.PI / 2 + i * Math.PI / 5
          val rad = if (i % 2 == 0) outerR else innerR
          xs(i) = cx + (rad * Math.cos(angle)).toFloat
          ys(i) = cy - (rad * Math.sin(angle)).toFloat
        }
        shapeBatch.fillPolygon(xs, ys, r, g, b, 1f)
      case ItemType.Gem =>
        shapeBatch.fillPolygon(
          Array(cx, cx + hs, cx, cx - hs),
          Array(cy - hs, cy, cy + hs, cy), r, g, b, 1f)
      case ItemType.Shield =>
        shapeBatch.fillPolygon(
          Array(cx - hs * 0.85f, cx - hs * 0.5f, cx + hs * 0.5f, cx + hs * 0.85f, cx + hs * 0.7f, cx, cx - hs * 0.7f),
          Array(cy - hs * 0.6f, cy - hs, cy - hs, cy - hs * 0.6f, cy + hs * 0.4f, cy + hs, cy + hs * 0.4f), r, g, b, 1f)
      case ItemType.Fence =>
        val barW = hs * 0.35f
        shapeBatch.fillRect(cx - hs, cy - hs, barW, hs * 2, r, g, b, 1f)
        shapeBatch.fillRect(cx - barW / 2, cy - hs, barW, hs * 2, r, g, b, 1f)
        shapeBatch.fillRect(cx + hs - barW, cy - hs, barW, hs * 2, r, g, b, 1f)
      case _ =>
        shapeBatch.fillPolygon(
          Array(cx, cx + hs, cx, cx - hs),
          Array(cy - hs, cy, cy + hs, cy), r, g, b, 1f)
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
    val glowXs = new Array[Float](glowN * 2 + 2); val glowYs = new Array[Float](glowN * 2 + 2)
    for (i <- 0 to glowN) {
      val t = i.toDouble / glowN
      val gw = 1.8 * pulse
      val cwx = drawX + ndx * cylLength * t
      val cwy = drawY + ndy * cylLength * t
      glowXs(i) = worldToScreenX(cwx + perpX * gw, cwy + perpY * gw).toFloat
      glowYs(i) = worldToScreenY(cwx + perpX * gw, cwy + perpY * gw).toFloat
      glowXs(glowN * 2 + 1 - i) = worldToScreenX(cwx - perpX * gw, cwy - perpY * gw).toFloat
      glowYs(glowN * 2 + 1 - i) = worldToScreenY(cwx - perpX * gw, cwy - perpY * gw).toFloat
    }
    shapeBatch.fillPolygon(glowXs, glowYs, blendR, blendG, blendB, 0.05f * pulse)

    // Cylinder body
    val bodyN = 20
    val bodyXs = new Array[Float](bodyN * 2 + 2); val bodyYs = new Array[Float](bodyN * 2 + 2)
    for (i <- 0 to bodyN) {
      val t = i.toDouble / bodyN
      bodyXs(i) = cylSX(t, 1.0)
      bodyYs(i) = cylSY(t, 1.0)
      bodyXs(bodyN * 2 + 1 - i) = cylSX(t, -1.0)
      bodyYs(bodyN * 2 + 1 - i) = cylSY(t, -1.0)
    }
    shapeBatch.fillPolygon(bodyXs, bodyYs, blendR * 0.5f, blendG * 0.5f, blendB * 0.5f, 0.10f + chargePct * 0.06f)

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
    while (iter.hasNext) {
      val entry = iter.next()
      val data = entry.getValue
      val deathTime = data(0)
      if (now - deathTime > DEATH_ANIMATION_MS) {
        iter.remove()
      } else {
        val wx = data(1).toDouble; val wy = data(2).toDouble
        val colorRGB = data(3).toInt
        val charId = if (data.length > 4) data(4).toByte else 0.toByte
        if (now - deathTime < 50) camera.triggerShake(6.0, 500)
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

        // Camera shake on first frame
        if (elapsed < 50) camera.triggerShake(3.0 + blastRadius.toDouble, 400)
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

  private def drawHealthBar(screenCenterX: Double, spriteTopY: Double, health: Int, maxHealth: Int, teamId: Byte): Unit = {
    val barW = Constants.HEALTH_BAR_WIDTH_PX.toFloat
    val barH = Constants.HEALTH_BAR_HEIGHT_PX.toFloat
    val barX = (screenCenterX - barW / 2).toFloat
    val barY = (spriteTopY - Constants.HEALTH_BAR_OFFSET_Y - barH).toFloat

    beginShapes()
    shapeBatch.fillRect(barX, barY, barW, barH, 0.4f, 0f, 0f, 1f)
    val pct = health.toFloat / maxHealth
    val (fr, fg, fb) = teamId match {
      case 1 => (0.29f, 0.51f, 1f)
      case 2 => (0.91f, 0.25f, 0.34f)
      case 3 => (0.2f, 0.8f, 0.2f)
      case 4 => (0.95f, 0.77f, 0.06f)
      case _ => (0.2f, 0.8f, 0.2f)
    }
    shapeBatch.fillRect(barX, barY, barW * pct, barH, fr, fg, fb, 1f)
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
    val health = client.getLocalHealth

    beginSprites()
    fontSmall.drawTextOutlined(spriteBatch, s"World: ${world.name}", 10, 10)
    fontSmall.drawTextOutlined(spriteBatch, s"Position: (${localPos.getX}, ${localPos.getY})", 10, 28)
    fontSmall.drawTextOutlined(spriteBatch, s"Players: ${playerCount + 1}", 10, 46)
    fontSmall.drawTextOutlined(spriteBatch, s"Health: $health/${client.getSelectedCharacterMaxHealth}", 10, 64)
    fontSmall.drawTextOutlined(spriteBatch, s"Items: ${client.getInventoryCount}", 10, 82)

    val hasShield = client.hasShield
    val hasGem = client.hasGemBoost
    if (hasShield || hasGem) {
      val effectStr = if (hasShield && hasGem) "Effects: Shield FastShot"
        else if (hasShield) "Effects: Shield"
        else "Effects: FastShot"
      fontSmall.drawTextOutlined(spriteBatch, effectStr, 10, 100)
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

    val abilityGap = 12f
    beginShapes()
    for (i <- abilityDefs.indices) {
      val aDef = abilityDefs(i)
      val cooldownFrac = cooldowns(i)
      val (ar, ag, ab) = abilityColors(i)
      val slotX = inventoryStartX - (abilityDefs.length - i) * (slotSize + slotGap) - abilityGap
      val onCooldown = cooldownFrac > 0.001f

      // Slot background
      shapeBatch.fillRect(slotX, startY, slotSize, slotSize, 0.06f, 0.06f, 0.1f, 0.8f)

      // Ability icon inside the slot
      val cx = slotX + slotSize / 2; val cy = startY + slotSize / 2
      val iconAlpha = if (onCooldown) 0.35f else 0.9f
      drawAbilityIcon(aDef.castBehavior, cx, cy, 12f, ar, ag, ab, iconAlpha)

      // Cooldown overlay (fills from bottom up)
      if (onCooldown) {
        shapeBatch.fillRect(slotX, startY, slotSize, slotSize * cooldownFrac, 0f, 0f, 0f, 0.55f)
        shapeBatch.strokeRect(slotX, startY, slotSize, slotSize, 1.5f, 0.39f, 0.39f, 0.39f, 0.6f)
      } else {
        shapeBatch.strokeRect(slotX, startY, slotSize, slotSize, 2f, ar, ag, ab, 0.9f)
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

    beginSprites()
    fontMedium.drawTextOutlined(spriteBatch, timerText, (screenW / 2 - fontMedium.measureWidth(timerText) / 2).toFloat, 10)
    fontSmall.drawTextOutlined(spriteBatch, s"K: ${client.killCount}  D: ${client.deathCount}", 10, 128)

    // Kill feed
    val now = System.currentTimeMillis()
    var feedY = 10f
    client.killFeed.asScala.foreach { entry =>
      val timestamp = entry(0).asInstanceOf[java.lang.Long].longValue()
      val elapsed = now - timestamp
      if (elapsed < 6000) {
        val alpha = Math.max(0.15f, (1.0 - elapsed / 6000.0).toFloat)
        val killer = entry(1).asInstanceOf[String]
        val victim = entry(2).asInstanceOf[String]
        fontSmall.drawText(spriteBatch, s"$killer killed $victim", screenW - 220f, feedY, 1f, 1f, 1f, alpha)
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
  }

  def dispose(): Unit = {
    if (initialized) {
      shapeBatch.dispose()
      spriteBatch.dispose()
      fontSmall.dispose()
      fontMedium.dispose()
      fontLarge.dispose()
      postProcessor.dispose()
      GLTileRenderer.dispose()
      GLSpriteGenerator.clearCache()
      initialized = false
    }
  }
}
