package com.gridgame.client.gl

import org.lwjgl.opengl.GL11._
import org.lwjgl.opengl.GL30._

/**
 * 2D dynamic lighting system. Renders colored soft circles (lights) into a half-resolution
 * FBO, which is then multiplied with the scene during compositing.
 *
 * Uses a pre-allocated pool of lights to avoid per-frame allocations.
 */
class LightSystem(var width: Int, var height: Int) {
  private val MAX_LIGHTS = 64
  // Light pool: each light has (screenX, screenY, radius, r, g, b, intensity)
  private val lx = new Array[Float](MAX_LIGHTS)
  private val ly = new Array[Float](MAX_LIGHTS)
  private val lr = new Array[Float](MAX_LIGHTS)
  private val lcr = new Array[Float](MAX_LIGHTS)
  private val lcg = new Array[Float](MAX_LIGHTS)
  private val lcb = new Array[Float](MAX_LIGHTS)
  private val li = new Array[Float](MAX_LIGHTS)
  private var lightCount = 0

  // Half-res FBO for light accumulation
  private var lightFBO: GLTexture = GLTexture.createFBO(width / 2, height / 2)

  // Ambient light level (set per background type)
  var ambientLevel: Float = 0.35f

  // Cache tracking — skip renderLightMap when nothing changed
  private var _lastAmbient: Float = -1f
  private var _lastLightCount: Int = -1

  /** Clear all lights for a new frame. */
  def clear(): Unit = {
    lightCount = 0
  }

  /** Add a light source. Coordinates are in screen space (zoomed world space). */
  def addLight(screenX: Float, screenY: Float, radius: Float, r: Float, g: Float, b: Float, intensity: Float): Unit = {
    if (lightCount >= MAX_LIGHTS) return
    val idx = lightCount
    lx(idx) = screenX
    ly(idx) = screenY
    lr(idx) = radius
    lcr(idx) = r
    lcg(idx) = g
    lcb(idx) = b
    li(idx) = intensity
    lightCount += 1
  }

  /** Set ambient light based on background type. */
  def setAmbientForBackground(background: String): Unit = {
    ambientLevel = background match {
      case "space"     => 0.38f
      case "cityscape" => 0.45f
      case "ocean"     => 0.50f
      case "sky"       => 0.60f
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
    if (lightCount == 0 && lightCount == _lastLightCount && ambientLevel == _lastAmbient) return
    _lastLightCount = lightCount
    _lastAmbient = ambientLevel

    lightFBO.bindAsTarget()
    glViewport(0, 0, width / 2, height / 2)
    // Clear to ambient level
    glClearColor(ambientLevel, ambientLevel, ambientLevel, 1f)
    glClear(GL_COLOR_BUFFER_BIT)

    if (lightCount > 0) {
      // Use the same coordinate system as the scene renderer
      val lightProj = Matrix4.orthographic(0f, canvasW, canvasH, 0f)

      shapeBatch.begin(lightProj)
      shapeBatch.setAdditiveBlend(true)

      var idx = 0
      while (idx < lightCount) {
        shapeBatch.fillOvalSoft(lx(idx), ly(idx), lr(idx), lr(idx),
          lcr(idx), lcg(idx), lcb(idx), li(idx), 0f, 16)
        idx += 1
      }

      shapeBatch.setAdditiveBlend(false)
      shapeBatch.end()
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
    lightFBO = GLTexture.resizeFBO(lightFBO, width / 2, height / 2)
  }

  def dispose(): Unit = {
    lightFBO.dispose()
  }
}
