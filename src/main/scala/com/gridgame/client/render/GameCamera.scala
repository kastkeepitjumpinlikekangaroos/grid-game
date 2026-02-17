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

  def camOffX: Double = _camOffX
  def camOffY: Double = _camOffY

  /** Update camera position with smooth interpolation. Results available via camOffX/camOffY. */
  def update(targetX: Double, targetY: Double, deltaSec: Double, canvasW: Double, canvasH: Double): Unit = {
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
}
