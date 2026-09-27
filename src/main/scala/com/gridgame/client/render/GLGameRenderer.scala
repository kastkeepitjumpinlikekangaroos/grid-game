package com.gridgame.client.render

import com.gridgame.client.game.{FadingProjectile, GameClient}
import com.gridgame.client.gl.{GLFontRenderer, GpuProfiler, LightSystem, Matrix4, PostProcessor, RenderQuality, ShaderProgram, ShapeBatch, SpriteBatch}
import com.gridgame.client.render.blasts.GLBlastRenderers
import com.gridgame.client.render.hud._
import com.gridgame.client.render.world._
import com.gridgame.common.Constants
import com.gridgame.common.model._

/**
 * The game's frame, drawn with OpenGL into the match's GLFW window: [[render]] draws one, pass by
 * pass, in the order below. What each pass draws is a painter's (render/world for the world,
 * render/hud for the HUD); what they all share — the batches, the fonts, the camera, the frame's
 * clock and size — is the RenderContext.
 *
 *  1. The background, only where the screen runs off the map (BackgroundPainter)
 *  2. The ground: every flat tile as an opaque diamond, then its animated overlays and reflections
 *     (TerrainPainter), the traps lying on it (TrapPainter), the blasts' ground layer
 *     (BlastPainter) and the aim arrow (AimArrowPainter)
 *  3. The depth pass: blocks and props, and the entities standing among them, row by row, far to
 *     near — players (PlayerPainter, StatusEffectPainter), items (ItemPainter), projectiles
 *     (ProjectilePainter)
 *  4. Over the terrain: flying projectiles, raised barriers (BarrierPainter), the opening's wall
 *     (OpeningPainter), the glowing effects deferred from the depth pass (GlowEffectPainter),
 *     deaths and teleports (DeathAndTeleportPainter), the blasts' air layer, particles
 *     (ParticleSpawner)
 *  5. The post-processing composite: bloom, the light map, the grade, the vignette
 *  6. Over the finished frame, at the display's full resolution: name plates, health bars and
 *     damage numbers (WorldOverlayPainter), then the HUD (HudPainter)
 */
class GLGameRenderer(val client: GameClient, clock: FrameClock = FrameClock.System) {
  private val ctx = new RenderContext(client, clock)
  import ctx._

  // The painters, each below the ones it uses
  private val particles = new ParticleSpawner(ctx)
  private val backgrounds = new BackgroundPainter(ctx)
  private val terrain = new TerrainPainter(ctx)
  private val effects = new StatusEffectPainter(ctx)
  private val glows = new GlowEffectPainter(ctx)
  private val items = new ItemPainter(ctx)
  private val projectiles = new ProjectilePainter(ctx)
  private val barriers = new BarrierPainter(ctx)
  private val opening = new OpeningPainter(ctx)
  private val traps = new TrapPainter(ctx)
  private val aimArrow = new AimArrowPainter(ctx)
  private val blasts = new BlastPainter(ctx)
  private val deaths = new DeathAndTeleportPainter(ctx, particles)
  private val overlay = new WorldOverlayPainter(ctx, particles)
  private val players = new PlayerPainter(ctx, barriers, effects, glows, overlay)
  private val abilityBar = new AbilityBarPainter(ctx)
  private val chat = new ChatPainter(ctx)
  private val matchInfo = new MatchInfoPainter(ctx, opening)
  private val practice = new PracticePainter(ctx)
  private val hud = new HudPainter(ctx, abilityBar, chat, matchInfo, practice, items)
  private val deathScreens = new DeathScreenPainter(ctx)

  /** The camera, which the mouse handler reads to turn the cursor into a world position. */
  def camera: GameCamera = ctx.camera

  // Rendering infrastructure (initialized lazily on first render when GL context is current)
  private var initialized = false

  // Vertical room a name plate + health bar take above a player sprite, used when deciding
  // how far past the visible tiles the entity pass has to walk.
  private val NAME_PLATE_HEADROOM_PX = 40

  /** Particles and damage numbers scatter at random; GoldenFrames seeds it to draw a scene the same way twice. */
  private[client] def seedRandom(seed: Long): Unit = rng.setSeed(seed)

  // Damage-proportional flash tracking
  private var _prevLocalHealthForFlash: Int = -1
  private var _lastDamageAmount: Int = 0

  // Match start fade-in (overlay alpha: 1.0 → 0.0 over 500ms)
  private var matchStartTime: Long = 0L
  private val MATCH_FADE_IN_MS = 500

  // Cached framebuffer size to skip redundant PostProcessor.resize() calls
  private var lastFbWidth = 0
  private var lastFbHeight = 0

  // Cached background name (avoids per-frame string match in setAmbientForBackground)
  private var _cachedBackground: String = _

  private def ensureInitialized(width: Int, height: Int): Unit = {
    if (initialized) return
    val colorShader = new ShaderProgram(ShaderProgram.COLOR_VERT, ShaderProgram.COLOR_FRAG)
    val textureShader = new ShaderProgram(ShaderProgram.TEXTURE_VERT, ShaderProgram.TEXTURE_FRAG)
    shapeBatch = new ShapeBatch(colorShader)
    spriteBatch = new SpriteBatch(textureShader)
    postProcessor = new PostProcessor(width, height)
    lightSystem = new LightSystem(width, height)
    backgrounds.create(width, height)
    initialized = true
    matchStartTime = clock.nowMs()
  }

  private var fontPixelScale = 0f
  private var _preloadedPlayers = -1

  /** Fonts rasterized for the display's pixel density (GLFontRenderer's pixelScale): 2 on a HiDPI
    * screen. Made again if the window moves to a screen of another density. */
  private def ensureFonts(pixelScale: Float): Unit = {
    if (pixelScale == fontPixelScale) return
    if (fontSmall != null) { fontSmall.dispose(); fontMedium.dispose(); fontLarge.dispose() }
    fontSmall = new GLFontRenderer(14, pixelScale)
    fontMedium = new GLFontRenderer(22, pixelScale)
    fontLarge = new GLFontRenderer(44, pixelScale)
    // Pre-rasterize the active language's glyphs (cheap ASCII for English, the CJK
    // set for Chinese/Korean) so the first HUD frame with translated text doesn't hitch.
    val i18nGlyphs = com.gridgame.client.i18n.Messages.currentCodepoints
    fontSmall.prewarm(i18nGlyphs)
    fontMedium.prewarm(i18nGlyphs)
    // Damage numbers are drawn in the overlay over the world, in its units (see render)
    damageNumbers.setFont(fontMedium)
    damageNumbers.setFontLarge(fontLarge)
    fontPixelScale = pixelScale
  }

  def render(deltaSec: Double, fbWidth: Int, fbHeight: Int, windowWidth: Int, windowHeight: Int): Unit = {
    // The world is rendered into off-screen targets whose size the quality tier controls;
    // the composite still upscales to the real framebuffer and the HUD is drawn on top of
    // it at full resolution, so lowering the scale costs sharpness in the world only.
    val scale = RenderQuality.sceneScale(fbWidth, windowWidth)
    val sceneW = Math.max(64, (fbWidth * scale).toInt)
    val sceneH = Math.max(64, (fbHeight * scale).toInt)
    ensureInitialized(sceneW, sceneH)
    ensureFonts(Math.max(1f, Math.min(4f, fbWidth.toFloat / Math.max(1, windowWidth))))
    // Safe point to free a character atlas that a grow replaced: no batch has queued work.
    GLSpriteGenerator.disposeRetired()
    // Every character in the match loaded now, not when it first walks into view: looked over when
    // someone joins or leaves, and twice a second in case one changes character
    if (client.getPlayers.size != _preloadedPlayers || animationTick % 30 == 0) {
      _preloadedPlayers = client.getPlayers.size
      GLSpriteGenerator.preload(client.selectedCharacterId)
      val it = client.getPlayers.values().iterator()
      while (it.hasNext) GLSpriteGenerator.preload(it.next().getCharacterId)
    }
    animationTick += 1
    frameTimeMs = clock.nowMs()
    animTickF = animationTick.toFloat
    val dt = deltaSec.toFloat

    // Update particle systems
    weatherParticles.update(dt)
    combatParticles.update(dt)

    val zoom = Constants.CAMERA_ZOOM
    canvasW = windowWidth / zoom
    canvasH = windowHeight / zoom

    val localDeathTime = client.getLocalDeathTime
    val localDeathAnimActive = client.getIsDead && localDeathTime > 0 &&
      (frameTimeMs - localDeathTime) < DEATH_ANIMATION_MS

    // In non-lobby mode, show full game over screen when dead (after death anim)
    if (client.getIsDead && !localDeathAnimActive && !client.isRespawning) {
      deathScreens.drawGameOver(fbWidth, fbHeight)
      return
    }

    GpuProfiler.beginFrame()
    val world = client.getWorld

    // Use interpolated position for smooth camera tracking between grid tiles
    client.updateVisualPosition()
    // and every projectile flown to where it is now, between the server's ticks, before anything
    // this frame reads where one is
    client.flyProjectiles(client.nanoClock(), deltaSec)
    val visualPosX = client.visualPosX
    val visualPosY = client.visualPosY

    // Update camera with smooth interpolation
    camera.update(visualPosX, visualPosY, deltaSec, canvasW, canvasH, sceneW / canvasW)
    camOffX = camera.camOffX
    camOffY = camera.camOffY

    // Post-processing: render to FBO (only resize when dimensions actually change)
    if (sceneW != lastFbWidth || sceneH != lastFbHeight) {
      postProcessor.resize(sceneW, sceneH)
      lightSystem.resize(sceneW, sceneH)
      backgrounds.resize(sceneW, sceneH)
      lastFbWidth = sceneW
      lastFbHeight = sceneH
    }

    // Dynamic lighting: clear lights and set ambient for current background (only recompute on change)
    lightSystem.clear()
    val bg = world.background
    if (bg ne _cachedBackground) {
      _cachedBackground = bg
      setGrade(bg)
    }

    // Update damage numbers
    damageNumbers.update(deltaSec.toFloat)

    // Set up projection for zoomed world-space rendering
    projection = Matrix4.orthographic(0f, canvasW.toFloat, canvasH.toFloat, 0f)
    shapeBatch.pixelsPerUnit = sceneW / canvasW.toFloat

    // The background only shows past the edge of the world: every cell in it is covered by its
    // ground diamond or its block (a prop has its ground drawn under it). Mid-map, which is most
    // of a match, drawing it was a full-screen redraw every few frames and a full-screen blit
    // every frame — a fifth of the frame's pixels — all of it painted over by the ground.
    val bgVisible = !IsometricTransform.viewInsideWorld(camOffX, camOffY, canvasW, canvasH, world.width, world.height)
    backgrounds.refresh(world, bgVisible)
    gpuMark("bg render")

    postProcessor.beginScene()

    // Blit cached background
    if (bgVisible) backgrounds.blit()
    gpuMark("bg blit")

    beginShapes()

    // === Visible tile bounds ===
    // The screen rect maps to a *diamond* in world space, so its axis-aligned bounding
    // box holds nearly three times as many cells as are actually on screen. Culling per
    // row against the diamond instead of the box is exact and costs two comparisons.
    //
    // Work in the projection's own axes: u = wx - wy (screen x) and v = wx + wy (screen y),
    // where sx = u*HW + camOffX and sy = v*HH + camOffY. A cell's sprite covers
    // [sx-HW, sx+HW] x [sy-(cellH-HH), sy+HH], so it is on screen exactly when its u and v
    // fall in the ranges below.
    val cellH = Constants.TILE_CELL_HEIGHT
    val invHW = 1.0 / HW; val invHH = 1.0 / HH
    val uMin = (-camOffX - HW) * invHW
    val uMax = (canvasW - camOffX + HW) * invHW
    val vMin = (-camOffY - HH) * invHH
    val vMax = (canvasH - camOffY + (cellH - HH)) * invHH
    // Entities hang above their own cell — a player sprite is drawn upward from it, with a
    // name plate and health bar above that — so the pass that interleaves them walks
    // further than the tiles need, or a player just off the bottom edge would pop in.
    val entPad = ((Constants.PLAYER_DISPLAY_SIZE_PX + NAME_PLATE_HEADROOM_PX) * invHH).toInt + 2

    val startY = Math.max(0, ((vMin - uMax) * 0.5).floor.toInt - entPad)
    val endY = Math.min(world.height - 1, ((vMax - uMin) * 0.5).ceil.toInt + entPad)
    val startX = Math.max(0, ((uMin + vMin) * 0.5).floor.toInt - entPad)
    val endX = Math.min(world.width - 1, ((uMax + vMax) * 0.5).ceil.toInt + entPad)
    setView(startX, endX, startY, endY, uMin, uMax, vMin, vMax)

    terrain.beginFrame()

    // === Collect entities by cell ===
    items.beginFrame()
    overlay.beginFrame()
    glows.beginFrame()
    barriers.beginFrame()
    val localVX = camera.visualX
    val localVY = camera.visualY
    entityCollector.collect(
      client, deltaSec,
      localVX, localVY, localDeathAnimActive,
      startX, endX, startY, endY
    )

    // === Phase 1: Ground tiles ===
    terrain.drawGround(world)
    gpuMark("ground")

    // === Animated tile overlays ===
    terrain.drawOverlays()

    // === Water reflections ===
    terrain.drawReflections()

    // === Traps: flat decals on the ground, so walls in front of them cover them in phase 2 ===
    traps.draw()

    // === Blasts: what they leave on the ground, under the players standing in them ===
    blasts.collect()
    blasts.draw(GLBlastRenderers.GROUND)

    // === Aim arrow ===
    aimArrow.draw()

    gpuMark("overlays")

    // === Elevated tile edge shadows (batched in one shapes pass — more efficient than per-tile) ===
    terrain.drawBlockShadows(world)

    gpuMark("block shadows")

    // === Phase 2: Elevated tiles + entities interleaved by depth ===
    var wy = startY
    while (wy <= endY) {
      // Tiles stop at the visible diamond; entity cells run a few further so a sprite
      // standing just off the bottom edge still draws (and still draws in depth order).
      val tileLoX = rowLo(wy, 0)
      val tileHiX = rowHi(wy, 0)
      val loX = rowLo(wy, entPad)
      val hiX = rowHi(wy, entPad)
      var wx = loX
      while (wx <= hiX) {
        if (wx >= tileLoX && wx <= tileHiX) terrain.drawBlock(world, wx, wy)
        val cellEntries = entityCollector.takeCell(wx, wy)
        if (cellEntries != null) dispatchEntries(cellEntries, localVX, localVY)
        wx += 1
      }
      wy += 1
    }
    // Any entities outside visible range
    var leftover = entityCollector.takeRemaining()
    while (leftover != null) {
      dispatchEntries(leftover, localVX, localVY)
      leftover = entityCollector.takeRemaining()
    }

    gpuMark("depth pass")

    // === Projectiles flying over terrain: bodies after every wall and entity ===
    projectiles.drawFlying()

    // === Raised barriers: walls of light standing on the ground in front of their holders ===
    barriers.draw()

    // === The wall between the two teams while a team match opens ===
    opening.drawWall(uMin, uMax, vMin, vMax)

    gpuMark("fliers+walls")

    // === Deferred additive effects (single blend toggle for all player/item effects) ===
    if (glows.hasDeferred || items.hasDeferred) {
      beginShapes()
      shapeBatch.setAdditiveBlend(true)
      glows.drawDeferred()
      items.drawDeferred()
      shapeBatch.setAdditiveBlend(false)
    }

    // === Item pickup detection ===
    items.detectChanges()

    // === Overlay animations ===
    deaths.drawDeaths()
    deaths.drawTeleports()
    blasts.draw(GLBlastRenderers.AIR)

    gpuMark("effects")

    // === Gameplay particles (trails, footsteps, impacts) ===
    particles.spawnGameplay(dt)
    beginShapes()
    combatParticles.render(shapeBatch)

    // === Falling snow, in front of everything, over a snowy map ===
    if (bgType == 5) {
      particles.spawnWeather(world.background, dt)
      weatherParticles.render(shapeBatch)
    }

    gpuMark("particles")

    // === Damage number detection (they are drawn over the finished frame, below) ===
    overlay.detectDamageNumbers()

    // === Lights: every player's warm glow, and the blasts' ===
    players.addLights()
    blasts.addLights()

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
      val elapsed = frameTimeMs - localHitTime
      if (elapsed < HIT_ANIMATION_MS) {
        val progress = elapsed.toDouble / HIT_ANIMATION_MS
        val damageScale = Math.min(1f, _lastDamageAmount / 30f)
        val intensity = (1f - progress.toFloat) * (0.4f + 0.6f * damageScale)
        postProcessor.damageVignette = intensity
        postProcessor.chromaticAberration = 0.004f * intensity * (0.5f + 0.5f * damageScale)
      }
    }

    // Screen distortion from nearby explosions
    blasts.updateDistortion()

    endAll()

    // Render light map (an extra half-res target; the lowest tier drops it entirely)
    if (RenderQuality.lighting) {
      lightSystem.renderLightMap(shapeBatch, canvasW.toFloat, canvasH.toFloat)
      postProcessor.lightMapTexture = lightSystem.getLightMapTexture
      postProcessor.useLightMap = true
    } else {
      postProcessor.useLightMap = false
    }

    postProcessor.animationTime = animTickF

    // Match start fade-in from black
    if (matchStartTime > 0) {
      val fadeElapsed = frameTimeMs - matchStartTime
      if (fadeElapsed < MATCH_FADE_IN_MS) {
        val fadeAlpha = 1f - (fadeElapsed.toFloat / MATCH_FADE_IN_MS)
        postProcessor.overlayR = 0f; postProcessor.overlayG = 0f; postProcessor.overlayB = 0f
        postProcessor.overlayA = fadeAlpha
      } else if (fadeElapsed < MATCH_FADE_IN_MS + 100) {
        postProcessor.overlayA = 0f
        matchStartTime = 0
      }
    }

    gpuMark("light map")
    postProcessor.endScene(fbWidth, fbHeight)
    gpuMark("post")

    // === Over the finished world: name plates, health bars and damage numbers ===
    // Drawn into the display's own framebuffer, at its full resolution, after the grade. In the
    // scene they came out as soft as the scene's scale (a third of the display's pixels at Low),
    // dimmed by the light map and tinted by the grade: a white damage number on a dark map was
    // a muddy grey. Same world projection, so each lands exactly where it did.
    projection = Matrix4.orthographic(0f, canvasW.toFloat, canvasH.toFloat, 0f)
    shapeBatch.pixelsPerUnit = fbWidth / canvasW.toFloat
    overlayAlpha = 1f - postProcessor.overlayA // fade in with the match, as the world does
    overlay.draw()
    endAll()
    gpuMark("world overlay")

    // === HUD (rendered at screen-pixel scale, not zoomed) ===
    projection = Matrix4.orthographic(0f, windowWidth.toFloat, windowHeight.toFloat, 0f)
    shapeBatch.pixelsPerUnit = fbWidth.toFloat / Math.max(1, windowWidth)
    hud.draw(windowWidth, windowHeight)

    // === Respawn countdown ===
    if (client.getIsDead && client.isRespawning && !localDeathAnimActive) {
      deathScreens.drawRespawnCountdown(windowWidth, windowHeight)
    }
    endAll()
    gpuMark("hud")
    GpuProfiler.endFrame()
  }

  /** The grade for a map's background, set whenever the background changes. */
  private def setGrade(bg: String): Unit = {
    bgType = bg match {
      case "sky" => 0; case "cityscape" => 1; case "space" => 2; case "desert" => 3; case "ocean" => 4
      case "snow" => 5; case "sea" => 6; case _ => 0
    }
    lightSystem.setAmbientForBackground(bg)
    // The bright maps show their art's own colours. ACES lifts mid-tones and pulls highlights
    // down, which a dark map wants and a meadow doesn't (it came out pastel), and at the dark
    // maps' threshold sunlit sand and snow bloomed into a haze over everything; here only
    // what really glows blooms.
    val bright = bgType == 0 || bgType == 5 || bgType == 6
    postProcessor.toneMap = if (bright) 0f else 1f
    postProcessor.bloomThreshold = if (bright) 0.97f else 0.80f
    postProcessor.bloomStrength = if (bright) 0.16f else 0.22f
    // and a lighter vignette, which greys a snowfield's edges
    postProcessor.vignetteStrength = if (bright) 0.12f else 0.25f
    // In daylight a warm pool of light round every player reads as a spotlight on the grass;
    // explosions still flash
    lightSystem.gain = if (bright) 0.35f else 1f
    // and a shot carries no light at all: on sand or snow its pool only brightened the ground
    // round it toward white, washing out the pale ones (render_audit measured it)
    projectileLights = !bright
    weatherParticles.clear()
  }

  /** Draw one cell's entities; `head` is the first, the rest follow through `next`. */
  private def dispatchEntries(head: EntityCollector.MutableCellEntry, localVX: Double, localVY: Double): Unit = {
    // Fast path for single-entity cells (no reordering needed)
    if (head.next == null) {
      val entry = head
      entry.entryType match {
        case EntityCollector.TYPE_ITEM => items.draw(entry.ref.asInstanceOf[Item])
        case EntityCollector.TYPE_PROJECTILE => projectiles.draw(entry.ref.asInstanceOf[Projectile])
        case EntityCollector.TYPE_FADING_PROJECTILE => projectiles.drawFading(entry.ref.asInstanceOf[FadingProjectile])
        case EntityCollector.TYPE_PLAYER => players.drawPlayer(entry.ref.asInstanceOf[Player], entry.vx, entry.vy)
        case EntityCollector.TYPE_LOCAL_PLAYER => players.drawLocal(localVX, localVY)
        case EntityCollector.TYPE_LOCAL_DEATH => // handled by drawDeaths
        case _ =>
      }
      return
    }
    // Two-pass dispatch: shape-only entities first (items, projectiles), then
    // sprite entities (players). Reduces batch switches from up to N per cell to at most 1.
    // Pass 1: items and projectiles (shape-based)
    var entry = head
    while (entry != null) {
      entry.entryType match {
        case EntityCollector.TYPE_ITEM => items.draw(entry.ref.asInstanceOf[Item])
        case EntityCollector.TYPE_PROJECTILE => projectiles.draw(entry.ref.asInstanceOf[Projectile])
        case EntityCollector.TYPE_FADING_PROJECTILE => projectiles.drawFading(entry.ref.asInstanceOf[FadingProjectile])
        case _ => // skip players in this pass
      }
      entry = entry.next
    }
    // Pass 2: players (shape shadow + sprite body)
    entry = head
    while (entry != null) {
      entry.entryType match {
        case EntityCollector.TYPE_PLAYER => players.drawPlayer(entry.ref.asInstanceOf[Player], entry.vx, entry.vy)
        case EntityCollector.TYPE_LOCAL_PLAYER => players.drawLocal(localVX, localVY)
        case EntityCollector.TYPE_LOCAL_DEATH => // handled by drawDeaths
        case _ => // skip items/projectiles in this pass
      }
      entry = entry.next
    }
  }

  /** A new life, or the match starting over: nothing drawn from before it carries on. */
  def resetVisualPosition(): Unit = {
    camera.resetVisualPosition()
    entityCollector.remoteVisualPositions.clear()
    players.reset()
    overlay.reset()
    items.reset()
    abilityBar.reset()
    weatherParticles.clear()
    combatParticles.clear()
    deaths.reset()
    particles.reset()
  }

  def dispose(): Unit = {
    if (initialized) {
      shapeBatch.dispose()
      spriteBatch.dispose()
      if (fontSmall != null) { fontSmall.dispose(); fontMedium.dispose(); fontLarge.dispose() }
      fontSmall = null; fontMedium = null; fontLarge = null
      fontPixelScale = 0f
      postProcessor.dispose()
      lightSystem.dispose()
      backgrounds.dispose()
      GLTileRenderer.dispose()
      GLSpriteGenerator.clearCache()
      initialized = false
    }
  }
}
