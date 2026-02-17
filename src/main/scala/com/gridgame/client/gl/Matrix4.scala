package com.gridgame.client.gl

import java.nio.FloatBuffer
import org.lwjgl.BufferUtils

/** Minimal 4x4 matrix for 2D orthographic projection. Column-major layout for OpenGL. */
object Matrix4 {
  // Pre-allocated buffer reused across all orthographic() calls to avoid per-frame direct buffer allocation
  private val buf: FloatBuffer = BufferUtils.createFloatBuffer(16)

  /** Create an orthographic projection matrix suitable for 2D rendering. */
  def orthographic(left: Float, right: Float, bottom: Float, top: Float): FloatBuffer = {
    val w = right - left
    val h = top - bottom
    buf.clear()
    // Column-major order
    buf.put(2f / w).put(0f).put(0f).put(0f)       // col 0
    buf.put(0f).put(2f / h).put(0f).put(0f)        // col 1
    buf.put(0f).put(0f).put(-1f).put(0f)            // col 2 (z: -1 for near plane)
    buf.put(-(right + left) / w).put(-(top + bottom) / h).put(0f).put(1f) // col 3
    buf.flip()
    buf
  }
}
