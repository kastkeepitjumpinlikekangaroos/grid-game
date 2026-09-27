package com.gridgame.client.render

import com.gridgame.common.Constants

import java.nio.FloatBuffer
import com.gridgame.client.game.GameClient
import com.gridgame.client.gl.{GLFontRenderer, GLTexture, GpuProfiler, LightSystem, PostProcessor, ShapeBatch, SpriteBatch, TextureRegion}

/**
 * What every painter of a frame shares (see GLGameRenderer): the batches they draw with and which
 * of them is open, the fonts, the light map and the post-processor, the particle systems, the
 * camera, and the frame itself — its clock, its size, where the camera puts the world on it, and
 * which cells are on screen. GLGameRenderer makes the GL resources on the first frame and sets the
 * frame's values at the start of each; the painters read them (`import ctx._`). Only the render
 * thread touches any of it.
 */
private[render] final class RenderContext(val client: GameClient, clock: FrameClock) {
  val camera = new GameCamera(clock)
  val entityCollector = new EntityCollector(clock)

  // ── What the frame is drawn with (made on the first frame, when a GL context is current) ──
  var shapeBatch: ShapeBatch = _
  var spriteBatch: SpriteBatch = _
  var fontSmall: GLFontRenderer = _
  var fontMedium: GLFontRenderer = _
  var fontLarge: GLFontRenderer = _
  var postProcessor: PostProcessor = _
  var lightSystem: LightSystem = _
  val damageNumbers = new DamageNumberSystem()
  val weatherParticles = new ParticleSystem(1024)
  val combatParticles = new ParticleSystem(768)
  // Particles and damage numbers scatter at random off this: GoldenFrames seeds it
  val rng = new java.util.Random()
  // The projection the batches are begun with: the world's, then the display's for the HUD
  var projection: FloatBuffer = _

  // ── The frame (set by GLGameRenderer.render as it starts) ──
  var animationTick: Int = 0
  // Frame-level cached values (set once per render() call, used everywhere)
  var frameTimeMs: Long = 0L   // the clock's time, cached once per frame
  var animTickF: Float = 0f    // animationTick as Float (avoids Int→Float per use)
  // Current frame's camera offsets
  var camOffX: Double = 0
  var camOffY: Double = 0
  // The screen, in world units (the window's size over the camera's zoom)
  var canvasW: Double = 0
  var canvasH: Double = 0
  // How opaque the overlay over the world is: it fades in with the match, as the world does
  var overlayAlpha = 1f
  // Cached background type ID (avoids per-frame string matching in 3 places)
  var bgType: Byte = 0 // 0=sky, 1=cityscape, 2=space, 3=desert, 4=ocean, 5=snow, 6=sea
  // Whether projectiles light the ground round them: not in daylight (set with the grade)
  var projectileLights = true

  // ── How long things last, and how big a tile is ──
  val FRAMES_PER_STEP = 5
  val HIT_ANIMATION_MS = 500
  val DEATH_ANIMATION_MS = 1200
  val HW = Constants.ISO_HALF_W
  val HH = Constants.ISO_HALF_H
  // Tile dimensions for draw calls
  val tileW = (HW * 2).toFloat // 40
  val tileCellH = Constants.TILE_CELL_HEIGHT.toFloat // 56

  // ── The cells on screen ──
  // Rows viewStartY..viewEndY; in each row, the cells whose diamond is on screen, which run from
  // rowLo(wy, 0) to rowHi(wy, 0) — or `pad` cells further for the entities, which hang above their
  // own cell. In the projection's own axes, u = wx - wy across and v = wx + wy down.
  var viewStartX = 0
  var viewEndX = 0
  var viewStartY = 0
  var viewEndY = 0
  private var viewUMin = 0.0
  private var viewUMax = 0.0
  private var viewVMin = 0.0
  private var viewVMax = 0.0

  def setView(startX: Int, endX: Int, startY: Int, endY: Int, uMin: Double, uMax: Double, vMin: Double, vMax: Double): Unit = {
    viewStartX = startX; viewEndX = endX; viewStartY = startY; viewEndY = endY
    viewUMin = uMin; viewUMax = uMax; viewVMin = vMin; viewVMax = vMax
  }

  // Per-row x bounds: wx - wy in [uMin, uMax] and wx + wy in [vMin, vMax].
  @inline def rowLo(wy: Int, pad: Int): Int =
    Math.max(viewStartX, Math.max(wy + viewUMin, viewVMin - wy).floor.toInt - pad)
  @inline def rowHi(wy: Int, pad: Int): Int =
    Math.min(viewEndX, Math.min(wy + viewUMax, viewVMax - wy).ceil.toInt + pad)

  // ── Batch management ──
  // Track active batch to avoid redundant begin/end pairs.
  // Individual drawing methods call beginShapes()/beginSprites() instead of begin/end.
  // Batches are ended by switching type, by endAll(), or at render phase boundaries.
  private var _shapeActive = false
  private var _spriteActive = false

  def beginShapes(): Unit = {
    if (_spriteActive) { spriteBatch.end(); _spriteActive = false }
    if (!_shapeActive) {
      shapeBatch.begin(projection)
      _shapeActive = true
    }
  }

  def beginSprites(): Unit = {
    if (_shapeActive) { shapeBatch.end(); _shapeActive = false }
    if (!_spriteActive) {
      spriteBatch.begin(projection)
      _spriteActive = true
    }
  }

  def endAll(): Unit = {
    if (_shapeActive) { shapeBatch.end(); _shapeActive = false }
    if (_spriteActive) { spriteBatch.end(); _spriteActive = false }
  }

  /** A phase boundary for the GPU profiler (GpuProfiler): what is queued is submitted first, so
    * it is counted in the phase that drew it. Nothing at all when profiling is off. */
  @inline def gpuMark(label: String): Unit =
    if (GpuProfiler.enabled) { endAll(); GpuProfiler.timestamp(label) }

  /** A render target's texture the right way up. The background is drawn into its target with
    * the world's projection, which puts the top of the screen at the top of the target — row
    * height-1, v = 1 — and then drawn out again as a quad whose top edge is v = 0: blitted with its
    * plain full region it came out upside down, hills and trees hanging from the top of the screen
    * and clouds along the bottom, wherever the edge of a map let it show. */
  def flippedRegion(target: GLTexture): TextureRegion = TextureRegion(target, 0f, 1f, 1f, 0f)

  // ── Coordinates and colours ──

  def worldToScreenX(wx: Double, wy: Double): Double =
    IsometricTransform.worldToScreenX(wx, wy, camOffX)

  def worldToScreenY(wx: Double, wy: Double): Double =
    IsometricTransform.worldToScreenY(wx, wy, camOffY)

  @inline def bright(c: Float): Float = Math.min(1f, c * 0.4f + 0.6f)

  // Mutable output fields for intToRGB — avoids tuple allocation per call
  var _rgb_r = 0f; var _rgb_g = 0f; var _rgb_b = 0f

  def intToRGB(argb: Int): Unit = {
    _rgb_r = ((argb >> 16) & 0xFF) / 255f
    _rgb_g = ((argb >> 8) & 0xFF) / 255f
    _rgb_b = (argb & 0xFF) / 255f
  }

  def clamp(v: Float): Float = Math.max(0f, Math.min(1f, v))
}
