package com.gridgame.client.gl

import org.lwjgl.opengl.GL11._
import org.lwjgl.opengl.GL30._

/**
 * A frame's lights, as parallel arrays: position (screen space), radius, colour and intensity.
 * Pre-allocated, so a frame allocates nothing.
 *
 * A busy fight asks for more lights than the pool holds. When it is full a new light takes the
 * place of the weakest one (by radius^2 x intensity, roughly how much of the frame it lights),
 * if it is stronger. The pool used to drop whatever came after the 96th, in depth order, so an
 * explosion's flash — added last — went missing in exactly the fights that have them, and lights
 * blinked on and off as the entities ahead of them in the depth order moved between cells.
 */
private[gl] final class LightPool(val capacity: Int) {
  val x = new Array[Float](capacity)
  val y = new Array[Float](capacity)
  val radius = new Array[Float](capacity)
  val r = new Array[Float](capacity)
  val g = new Array[Float](capacity)
  val b = new Array[Float](capacity)
  val intensity = new Array[Float](capacity)
  private var _count = 0
  // Once the pool is full, the weakest light in it and its weight (-1 until looked for)
  private var weakest = -1
  private var weakestWeight = 0f

  def count: Int = _count

  def clear(): Unit = { _count = 0; weakest = -1 }

  def add(px: Float, py: Float, rad: Float, cr: Float, cg: Float, cb: Float, i: Float): Unit = {
    if (i <= 0.002f || rad <= 0f) return
    val idx = if (_count < capacity) { _count += 1; _count - 1 } else {
      if (weakest < 0) findWeakest()
      if (rad * rad * i <= weakestWeight) return
      val w = weakest
      weakest = -1
      w
    }
    x(idx) = px; y(idx) = py; radius(idx) = rad
    r(idx) = cr; g(idx) = cg; b(idx) = cb
    intensity(idx) = i
  }

  private def findWeakest(): Unit = {
    var best = 0
    var bestW = Float.MaxValue
    var k = 0
    while (k < _count) {
      val w = radius(k) * radius(k) * intensity(k)
      if (w < bestW) { bestW = w; best = k }
      k += 1
    }
    weakest = best
    weakestWeight = bestW
  }
}

/**
 * 2D dynamic lighting system. Renders colored soft circles (lights) into a quarter-resolution
 * FBO, which is then multiplied with the scene during compositing.
 */
class LightSystem(var width: Int, var height: Int) {
  private val pool = new LightPool(96)

  // Quarter-res FBO for light accumulation. Every light is a soft blob fading to nothing at its
  // edge, which bilinear upsampling reproduces exactly, and at half res the blobs of a busy fight
  // shaded several full half-res screens of additive blending.
  private val Downscale = 4
  private var lightFBO: GLTexture = GLTexture.createFBO(Math.max(1, width / Downscale), Math.max(1, height / Downscale))

  // Ambient light level (set per background type)
  var ambientLevel: Float = 0.35f

  /** Scales every light added: 1 on the dark maps, lower on a bright one (set per background). */
  var gain: Float = 1f

  // Cache tracking — skip renderLightMap when nothing changed
  private var _lastAmbient: Float = -1f
  private var _lastLightCount: Int = -1

  /** Clear all lights for a new frame. */
  def clear(): Unit = pool.clear()

  /** Add a light source. Coordinates are in screen space (zoomed world space). See [[LightPool]]
    * for what happens when there are more than it holds. */
  def addLight(screenX: Float, screenY: Float, radius: Float, r: Float, g: Float, b: Float, intensity: Float): Unit =
    pool.add(screenX, screenY, radius, r, g, b, intensity * gain)

  /** Set ambient light based on background type. */
  def setAmbientForBackground(background: String): Unit = {
    ambientLevel = background match {
      case "space"     => 0.38f
      case "cityscape" => 0.45f
      case "ocean"     => 0.50f
      case "sky"       => 0.625f // x1.6 in the composite: exactly the art's own brightness
      case "snow"      => 0.625f
      case "sea"       => 0.625f
      case "desert"    => 0.58f
      case _           => 0.48f
    }
  }

  /**
   * Render all lights into the light FBO using the given ShapeBatch.
   * The batch should NOT be in a begin/end block when this is called.
   * canvasW/canvasH are the zoomed world-space dimensions used by the main projection.
   */
  def renderLightMap(shapeBatch: ShapeBatch, canvasW: Float, canvasH: Float): Unit = {
    val lightCount = pool.count
    if (lightCount == 0 && lightCount == _lastLightCount && ambientLevel == _lastAmbient) return
    _lastLightCount = lightCount
    _lastAmbient = ambientLevel

    lightFBO.bindAsTarget()
    glViewport(0, 0, lightFBO.width, lightFBO.height)
    // Clear to ambient level
    glClearColor(ambientLevel, ambientLevel, ambientLevel, 1f)
    glClear(GL_COLOR_BUFFER_BIT)

    if (lightCount > 0) {
      // Use the same coordinate system as the scene renderer
      val lightProj = Matrix4.orthographic(0f, canvasW, canvasH, 0f)

      shapeBatch.begin(lightProj)
      val ppu = shapeBatch.pixelsPerUnit
      shapeBatch.pixelsPerUnit = lightFBO.width / canvasW // its blobs are a quarter the size on screen
      shapeBatch.setAdditiveBlend(true)

      var idx = 0
      while (idx < lightCount) {
        shapeBatch.fillOvalSoft(pool.x(idx), pool.y(idx), pool.radius(idx), pool.radius(idx),
          pool.r(idx), pool.g(idx), pool.b(idx), pool.intensity(idx), 0f, 16)
        idx += 1
      }

      shapeBatch.setAdditiveBlend(false)
      shapeBatch.end()
      shapeBatch.pixelsPerUnit = ppu
    }

    lightFBO.unbindTarget()
  }

  /** Get the light map texture for compositing. */
  def getLightMapTexture: GLTexture = lightFBO

  /** Resize the light FBO when window size changes. */
  def resize(newWidth: Int, newHeight: Int): Unit = {
    if (newWidth == width && newHeight == height) return
    width = newWidth
    height = newHeight
    lightFBO = GLTexture.resizeFBO(lightFBO, Math.max(1, width / Downscale), Math.max(1, height / Downscale))
  }

  def dispose(): Unit = {
    lightFBO.dispose()
  }
}
