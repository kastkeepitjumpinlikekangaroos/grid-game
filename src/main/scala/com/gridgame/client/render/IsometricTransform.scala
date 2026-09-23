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

  /**
   * Whether the whole of a view `canvasW` x `canvasH` units across, at these camera offsets, lies
   * over the cells of a `width` x `height` world (tile (c, r) covers [c - 0.5, c + 0.5) on both
   * axes). The world is convex in screen space, so the view's four corners decide it. The renderer
   * skips the background when it is: every cell's ground or block covers its own diamond.
   */
  def viewInsideWorld(camOffX: Double, camOffY: Double, canvasW: Double, canvasH: Double,
                      width: Int, height: Int): Boolean = {
    @inline def over(px: Double, py: Double): Boolean = {
      val wx = screenToWorldX(px, py, camOffX, camOffY)
      val wy = screenToWorldY(px, py, camOffX, camOffY)
      wx >= -0.5 && wy >= -0.5 && wx < width - 0.5 && wy < height - 0.5
    }
    over(0, 0) && over(canvasW, 0) && over(0, canvasH) && over(canvasW, canvasH)
  }

  // Mutable output fields for allocation-free screenToWorld (single-threaded render path only)
  private var _stw_x: Double = 0.0
  private var _stw_y: Double = 0.0
  def lastWorldX: Double = _stw_x
  def lastWorldY: Double = _stw_y

  /** Allocation-free screenToWorld. Results available via lastWorldX/lastWorldY. */
  def screenToWorldInto(sx: Double, sy: Double, camOffX: Double, camOffY: Double, zoom: Double): Unit = {
    val vsx = sx / zoom
    val vsy = sy / zoom
    _stw_x = screenToWorldX(vsx, vsy, camOffX, camOffY)
    _stw_y = screenToWorldY(vsx, vsy, camOffX, camOffY)
  }
}
