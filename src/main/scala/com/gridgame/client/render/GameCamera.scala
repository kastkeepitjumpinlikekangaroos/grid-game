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

  // Screen shake state
  private var shakeIntensity: Double = 0.0
  private var shakeDecayUntil: Long = 0L
  private var shakeStartTime: Long = 0L

  // Last computed camera offsets
  private var _camOffX: Double = 0.0
  private var _camOffY: Double = 0.0

  def camOffX: Double = _camOffX
  def camOffY: Double = _camOffY

  def triggerShake(intensity: Double, durationMs: Long): Unit = {
    val now = System.currentTimeMillis()
    val remaining = if (now < shakeDecayUntil) {
      val progress = (now - shakeStartTime).toDouble / (shakeDecayUntil - shakeStartTime)
      shakeIntensity * (1.0 - progress)
    } else 0.0
    if (intensity > remaining) {
      shakeIntensity = intensity
      shakeStartTime = now
      shakeDecayUntil = now + durationMs
    }
  }

  def getShakeOffset: (Double, Double) = {
    val now = System.currentTimeMillis()
    if (now >= shakeDecayUntil || shakeIntensity <= 0) return (0.0, 0.0)
    val elapsed = (now - shakeStartTime).toDouble
    val duration = (shakeDecayUntil - shakeStartTime).toDouble
    val progress = elapsed / duration
    val decay = 1.0 - progress
    val freq = 35.0
    val ox = Math.sin(elapsed * freq * 0.001) * shakeIntensity * decay
    val oy = Math.sin(elapsed * freq * 0.0013 + 1.7) * shakeIntensity * decay * 0.7
    (ox, oy)
  }

  /** Update camera position with smooth interpolation. Returns (camOffX, camOffY). */
  def update(targetX: Double, targetY: Double, deltaSec: Double, canvasW: Double, canvasH: Double): (Double, Double) = {
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
    val (shakeOx, shakeOy) = getShakeOffset
    _camOffX = canvasW / 2.0 - playerSx + shakeOx
    _camOffY = canvasH / 2.0 - playerSy + shakeOy
    (_camOffX, _camOffY)
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
