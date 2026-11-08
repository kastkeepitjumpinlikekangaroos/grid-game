package com.gridgame.client.gl

import org.lwjgl.opengl.GL11._
import org.lwjgl.opengl.GL15._
import org.lwjgl.opengl.GL20._
import org.lwjgl.opengl.GL30._
import org.lwjgl.opengl.GL32.glDrawElementsBaseVertex
import org.lwjgl.BufferUtils
import org.lwjgl.system.MemoryUtil

import java.nio.FloatBuffer

/**
 * Batched textured quads with per-vertex tint/alpha, four vertices and two triangles a quad, drawn
 * through a [[DrawStream]] (the main one is shared with the [[ShapeBatch]], so a switch between the
 * two draws nothing by itself). What is waiting is drawn first when a quad samples another texture.
 * Supports additive blending for glow effects.
 */
class SpriteBatch(val stream: DrawStream) {
  private var drawing = false
  private var additive = false

  /** The stream's shader, whose uniforms a batch with a shader of its own sets after [[begin]]. */
  def shader: ShaderProgram = stream.shader

  def begin(projection: FloatBuffer): Unit = {
    if (drawing) throw new IllegalStateException("Already drawing")
    stream.begin(projection)
    started()
  }

  /** Begin again where the shape batch on the same stream leaves off, as [[begin]] would but without
    * drawing what is waiting (see ShapeBatch.resume). */
  def resume(projection: FloatBuffer): Unit = {
    if (drawing) throw new IllegalStateException("Already drawing")
    stream.resume(projection)
    started()
  }

  private def started(): Unit = {
    drawing = true
    DrawStream.blendEnabled(true)
    blending = true
    setAdditiveBlend(false)
  }

  private var blending = true

  /** Turn blending off for opaque geometry that covers every pixel it touches (the ground), which
    * then costs the GPU no framebuffer read. [[begin]] and [[end]] turn it back on. */
  def setBlending(enabled: Boolean): Unit = {
    if (enabled != blending) {
      blending = enabled
      DrawStream.blendEnabled(enabled)
    }
  }

  def end(): Unit = {
    if (!drawing) throw new IllegalStateException("Not drawing")
    stream.flush()
    if (!blending) { DrawStream.blendEnabled(true); blending = true }
    drawing = false
  }

  /** Stop, leaving what is staged to be drawn with whatever the shape batch adds after it. */
  def suspend(): Unit = {
    if (!drawing) throw new IllegalStateException("Not drawing")
    if (!blending) { DrawStream.blendEnabled(true); blending = true }
    drawing = false
  }

  def setAdditiveBlend(enabled: Boolean): Unit = {
    if (enabled != additive) {
      additive = enabled
      DrawStream.blendAdditive(enabled)
    }
  }

  /** Draw a textured quad. Tint color is multiplied with texture color. */
  def draw(region: TextureRegion, x: Float, y: Float, w: Float, h: Float,
           r: Float = 1f, g: Float = 1f, b: Float = 1f, a: Float = 1f): Unit = {
    stream.useTexture(region.texture)
    ensureCapacity(4)
    val u = region.u
    val v = region.v
    val u2 = region.u2
    val v2 = region.v2

    vertex(x, y, u, v, r, g, b, a)
    vertex(x + w, y, u2, v, r, g, b, a)
    vertex(x + w, y + h, u2, v2, r, g, b, a)
    vertex(x, y + h, u, v2, r, g, b, a)
  }

  /**
   * A textured quad with its four corners and their texture coordinates given, in order round it,
   * as two triangles (0, 1, 2) and (0, 2, 3). Neighbouring quads whose shared corners are the
   * same floats meet without a gap or an overlap. The colour tints it, or, for a batch with a shader
   * of its own, is four numbers the quad carries to it.
   */
  def drawQuad(texture: GLTexture,
               x0: Float, y0: Float, u0: Float, v0: Float,
               x1: Float, y1: Float, u1: Float, v1: Float,
               x2: Float, y2: Float, u2: Float, v2: Float,
               x3: Float, y3: Float, u3: Float, v3: Float,
               r: Float = 1f, g: Float = 1f, b: Float = 1f, a: Float = 1f): Unit = {
    stream.useTexture(texture)
    ensureCapacity(4)
    vertex(x0, y0, u0, v0, r, g, b, a)
    vertex(x1, y1, u1, v1, r, g, b, a)
    vertex(x2, y2, u2, v2, r, g, b, a)
    vertex(x3, y3, u3, v3, r, g, b, a)
  }

  /** Draw a textured quad with rotation around its center. Angle in radians. */
  def drawRotated(region: TextureRegion, x: Float, y: Float, w: Float, h: Float,
                  angle: Float, r: Float = 1f, g: Float = 1f, b: Float = 1f, a: Float = 1f): Unit = {
    stream.useTexture(region.texture)
    ensureCapacity(4)
    val cx = x + w * 0.5f
    val cy = y + h * 0.5f
    val cos = Math.cos(angle).toFloat
    val sin = Math.sin(angle).toFloat
    val hw = w * 0.5f
    val hh = h * 0.5f

    val x0 = cx + (-hw) * cos - (-hh) * sin; val y0 = cy + (-hw) * sin + (-hh) * cos
    val x1 = cx + hw * cos - (-hh) * sin;   val y1 = cy + hw * sin + (-hh) * cos
    val x2 = cx + hw * cos - hh * sin;      val y2 = cy + hw * sin + hh * cos
    val x3 = cx + (-hw) * cos - hh * sin;   val y3 = cy + (-hw) * sin + hh * cos

    val u = region.u; val v = region.v; val u2 = region.u2; val v2 = region.v2

    vertex(x0, y0, u, v, r, g, b, a)
    vertex(x1, y1, u2, v, r, g, b, a)
    vertex(x2, y2, u2, v2, r, g, b, a)
    vertex(x3, y3, u, v2, r, g, b, a)
  }

  def flush(): Unit = stream.flush()

  def dispose(): Unit = stream.dispose()

  /** Every quad reserves its four vertices with [[ensureCapacity]] first. */
  @inline private def vertex(x: Float, y: Float, u: Float, v: Float, r: Float, g: Float, b: Float, a: Float): Unit =
    stream.vertex(x, y, u, v, r, g, b, a)

  /** Room for a quad, its four vertices and its two triangles, (0, 1, 2) and (0, 2, 3) of its
    * corners in the order they are written. */
  @inline private def ensureCapacity(vertices: Int): Unit = {
    stream.reserve(vertices, vertices / 4 * 6)
    val c = stream.vertexCount
    stream.tri(c, c + 1, c + 2)
    stream.tri(c, c + 2, c + 3)
  }
}
