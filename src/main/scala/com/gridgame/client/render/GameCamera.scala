package com.gridgame.client.render

import com.gridgame.common.Constants

/**
 * Camera state for the game view: smooth-lerped position, screen shake, zoom.
 * Shared between the JavaFX and OpenGL renderers.
 */
class GameCamera {
  private val HW = Constants.ISO_HALF_W
  private val HH = Constants.ISO_HALF_H

  // Visual interpolation for smooth movement
  var visualX: Double = Double.NaN
  var visualY: Double = Double.NaN

  // Last computed camera offsets
  private var _camOffX: Double = 0.0
  private var _camOffY: Double = 0.0

  // Screen shake state
  private var shakeIntensity: Double = 0.0
  private var shakeSeed: Long = 0L

  def camOffX: Double = _camOffX
  def camOffY: Double = _camOffY

  /** Trigger screen shake. Intensity is in pixels, decays exponentially. */
  def addShake(intensity: Double): Unit = {
    shakeIntensity = Math.min(shakeIntensity + intensity, 18.0) // cap total shake
    shakeSeed = System.nanoTime()
  }

  /**
   * Update camera position with smooth interpolation. Results available via camOffX/camOffY.
   *
   * `pixelsPerUnit` is how many pixels of the target the world is rendered into one unit covers;
   * when it is given, the offsets are rounded to whole pixels of it. The terrain is pixel art
   * sampled nearest-neighbour at a scale that isn't a whole number (80 texels onto 64 or 128
   * pixels), so at a sub-pixel offset which texels get doubled or dropped depends on the offset:
   * with the camera gliding between pixels, the detail on every tile crawled whenever anyone
   * moved. On the pixel grid each tile is sampled the same way wherever it is on screen. Entities
   * still move smoothly; they are drawn at their own positions.
   */
  def update(targetX: Double, targetY: Double, deltaSec: Double, canvasW: Double, canvasH: Double,
             pixelsPerUnit: Double = 0.0): Unit = {
    if (visualX.isNaN) {
      visualX = targetX
      visualY = targetY
    } else {
      val lerpFactor = 1.0 - Math.exp(-30.0 * deltaSec)
      visualX += (targetX - visualX) * lerpFactor
      visualY += (targetY - visualY) * lerpFactor
      if (Math.abs(visualX - targetX) < 0.01) visualX = targetX
      if (Math.abs(visualY - targetY) < 0.01) visualY = targetY
    }

    val playerSx = (visualX - visualY) * HW
    val playerSy = (visualX + visualY) * HH
    _camOffX = canvasW / 2.0 - playerSx
    _camOffY = canvasH / 2.0 - playerSy

    // Apply screen shake
    if (shakeIntensity > 0.3) {
      shakeSeed = shakeSeed * 6364136223846793005L + 1442695040888963407L
      val rx = ((shakeSeed >> 16) & 0xFFFF).toDouble / 32768.0 - 1.0
      shakeSeed = shakeSeed * 6364136223846793005L + 1442695040888963407L
      val ry = ((shakeSeed >> 16) & 0xFFFF).toDouble / 32768.0 - 1.0
      _camOffX += rx * shakeIntensity
      _camOffY += ry * shakeIntensity * 0.6 // less vertical shake
      // Exponential decay
      shakeIntensity *= Math.exp(-12.0 * deltaSec)
      if (shakeIntensity < 0.3) shakeIntensity = 0.0
    }

    if (pixelsPerUnit > 0.0) {
      _camOffX = Math.round(_camOffX * pixelsPerUnit) / pixelsPerUnit
      _camOffY = Math.round(_camOffY * pixelsPerUnit) / pixelsPerUnit
    }
  }

  /** Reset visual position (e.g., on rejoin). */
  def resetVisualPosition(): Unit = {
    visualX = Double.NaN
    visualY = Double.NaN
  }

  /** Convert screen coordinates to world coordinates, accounting for zoom. */
  def screenToWorld(sx: Double, sy: Double): (Double, Double) = {
    val zoom = Constants.CAMERA_ZOOM
    IsometricTransform.screenToWorld(sx, sy, _camOffX, _camOffY, zoom)
  }

  /** Allocation-free screenToWorld. Results available via IsometricTransform.lastWorldX/lastWorldY. */
  def screenToWorldInto(sx: Double, sy: Double): Unit = {
    val zoom = Constants.CAMERA_ZOOM
    IsometricTransform.screenToWorldInto(sx, sy, _camOffX, _camOffY, zoom)
  }
}
