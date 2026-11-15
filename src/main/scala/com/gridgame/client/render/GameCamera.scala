package com.gridgame.client.render

import com.gridgame.client.gl.RenderQuality
import com.gridgame.common.Constants

/**
 * Camera state for the game view: smooth-lerped position, screen shake, zoom.
 *
 * The zoom is how many of the window's units a unit of the world covers: `Constants.CAMERA_ZOOM`
 * (1.6) in the widest view, the default, and up to twice that as the player zooms in
 * (RenderQuality.zoom, the settings' camera zoom). Each [[update]] is told the frame's, and the
 * mouse is read into the world with the zoom of the last one, as its offsets are.
 */
class GameCamera(clock: FrameClock = FrameClock.System) {
  private val HW = Constants.ISO_HALF_W
  private val HH = Constants.ISO_HALF_H

  // Visual interpolation for smooth movement
  var visualX: Double = Double.NaN
  var visualY: Double = Double.NaN

  // Last computed camera offsets, and the zoom they were worked out for
  private var _camOffX: Double = 0.0
  private var _camOffY: Double = 0.0
  private var _zoom: Double = Constants.CAMERA_ZOOM

  // Screen shake state
  private var shakeIntensity: Double = 0.0
  private var shakeSeed: Long = 0L

  def camOffX: Double = _camOffX
  def camOffY: Double = _camOffY
  /** The window's units a unit of the world covered at the last [[update]]. */
  def zoom: Double = _zoom

  /** Trigger screen shake. Intensity is in pixels, decays exponentially. */
  def addShake(intensity: Double): Unit = {
    shakeIntensity = Math.min(shakeIntensity + intensity, 18.0) // cap total shake
    shakeSeed = clock.nowNanos()
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
   *
   * `zoom` is the frame's (see the class): the view `canvasW` x `canvasH` is the window over it. A
   * shake moves the view as far on the screen at any zoom.
   */
  def update(targetX: Double, targetY: Double, deltaSec: Double, canvasW: Double, canvasH: Double,
             pixelsPerUnit: Double = 0.0, zoom: Double = Constants.CAMERA_ZOOM): Unit = {
    _zoom = zoom
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

    // Apply screen shake: as far on the screen whatever the zoom (in the widest view's units)
    if (shakeIntensity > 0.3) {
      shakeSeed = shakeSeed * 6364136223846793005L + 1442695040888963407L
      val rx = ((shakeSeed >> 16) & 0xFFFF).toDouble / 32768.0 - 1.0
      shakeSeed = shakeSeed * 6364136223846793005L + 1442695040888963407L
      val ry = ((shakeSeed >> 16) & 0xFFFF).toDouble / 32768.0 - 1.0
      val onScreen = if (zoom == Constants.CAMERA_ZOOM) shakeIntensity else shakeIntensity * Constants.CAMERA_ZOOM / zoom
      _camOffX += rx * onScreen
      _camOffY += ry * onScreen * 0.6 // less vertical shake
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

  /** Convert screen coordinates (the window's units) to world coordinates, at the last update's zoom. */
  def screenToWorld(sx: Double, sy: Double): (Double, Double) =
    IsometricTransform.screenToWorld(sx, sy, _camOffX, _camOffY, _zoom)

  /** Allocation-free screenToWorld. Results available via IsometricTransform.lastWorldX/lastWorldY. */
  def screenToWorldInto(sx: Double, sy: Double): Unit =
    IsometricTransform.screenToWorldInto(sx, sy, _camOffX, _camOffY, _zoom)
}

object GameCamera {
  /** The window's units a unit of the world covers at the player's zoom (RenderQuality.zoom): the
    * widest view's CAMERA_ZOOM, times 1 to 2. */
  def playersZoom: Double = Constants.CAMERA_ZOOM * RenderQuality.zoom
}
