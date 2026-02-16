package com.gridgame.client.render

import com.gridgame.common.Constants

/** Stateless isometric coordinate transformation utilities shared by both renderers. */
object IsometricTransform {
  private val HW = Constants.ISO_HALF_W // 20
  private val HH = Constants.ISO_HALF_H // 10

  /** Convert world tile coordinates to screen pixel X (center of tile diamond). */
  def worldToScreenX(wx: Double, wy: Double, camOffX: Double): Double =
    (wx - wy) * HW + camOffX

  /** Convert world tile coordinates to screen pixel Y (center of tile diamond). */
  def worldToScreenY(wx: Double, wy: Double, camOffY: Double): Double =
    (wx + wy) * HH + camOffY

  /** Inverse: screen pixel coordinates to world tile X. */
  def screenToWorldX(sx: Double, sy: Double, camOffX: Double, camOffY: Double): Double = {
    val rx = sx - camOffX
    val ry = sy - camOffY
    (rx / HW + ry / HH) / 2.0
  }

  /** Inverse: screen pixel coordinates to world tile Y. */
  def screenToWorldY(sx: Double, sy: Double, camOffX: Double, camOffY: Double): Double = {
    val rx = sx - camOffX
    val ry = sy - camOffY
    (ry / HH - rx / HW) / 2.0
  }

  /** Convert screen coordinates to world coordinates, accounting for zoom. */
  def screenToWorld(sx: Double, sy: Double, camOffX: Double, camOffY: Double, zoom: Double): (Double, Double) = {
    val vsx = sx / zoom
    val vsy = sy / zoom
    (screenToWorldX(vsx, vsy, camOffX, camOffY), screenToWorldY(vsx, vsy, camOffX, camOffY))
  }
}
