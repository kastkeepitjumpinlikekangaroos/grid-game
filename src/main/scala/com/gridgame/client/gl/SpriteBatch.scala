package com.gridgame.client.gl

import org.lwjgl.opengl.GL11._
import org.lwjgl.opengl.GL15._
import org.lwjgl.opengl.GL20._
import org.lwjgl.opengl.GL30._
import org.lwjgl.BufferUtils

import java.nio.FloatBuffer

/**
 * Batched renderer for textured quads with per-vertex tint/alpha.
 * Vertices are position (vec2) + texcoord (vec2) + color (vec4) = 8 floats per vertex.
 * Flushes automatically on texture change. Supports additive blending for glow effects.
 */
class SpriteBatch(val shader: ShaderProgram) {
  private val VERTEX_SIZE = 8 // x, y, u, v, r, g, b, a
  private val INITIAL_CAPACITY = 4096
  private var capacity = INITIAL_CAPACITY
  private var buffer: FloatBuffer = BufferUtils.createFloatBuffer(capacity * VERTEX_SIZE)
  private var vertexCount = 0
  private var drawing = false
  private var additive = false
  private var currentTexture: GLTexture = _

  private val vao = glGenVertexArrays()
  private val vbo = glGenBuffers()

  glBindVertexArray(vao)
  glBindBuffer(GL_ARRAY_BUFFER, vbo)
  glBufferData(GL_ARRAY_BUFFER, capacity * VERTEX_SIZE * 4L, GL_DYNAMIC_DRAW)
  // position
  glVertexAttribPointer(0, 2, GL_FLOAT, false, VERTEX_SIZE * 4, 0)
  glEnableVertexAttribArray(0)
  // texcoord
  glVertexAttribPointer(1, 2, GL_FLOAT, false, VERTEX_SIZE * 4, 2 * 4L)
  glEnableVertexAttribArray(1)
  // color
  glVertexAttribPointer(2, 4, GL_FLOAT, false, VERTEX_SIZE * 4, 4 * 4L)
  glEnableVertexAttribArray(2)
  glBindVertexArray(0)

  def begin(projection: FloatBuffer): Unit = {
    if (drawing) throw new IllegalStateException("Already drawing")
    drawing = true
    vertexCount = 0
    buffer.clear()
    currentTexture = null
    shader.use()
    shader.setUniformMat4("uProjection", projection)
    shader.setUniform1i("uTexture", 0)
    glEnable(GL_BLEND)
    setAdditiveBlend(false)
  }

  def end(): Unit = {
    if (!drawing) throw new IllegalStateException("Not drawing")
    flush()
    drawing = false
  }

  def setAdditiveBlend(enabled: Boolean): Unit = {
    if (enabled != additive) {
      flush()
      additive = enabled
      if (additive) {
        glBlendFunc(GL_SRC_ALPHA, GL_ONE)
      } else {
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
      }
    }
  }

  /** Draw a textured quad. Tint color is multiplied with texture color. */
  def draw(region: TextureRegion, x: Float, y: Float, w: Float, h: Float,
           r: Float = 1f, g: Float = 1f, b: Float = 1f, a: Float = 1f): Unit = {
    if (region.texture != currentTexture) {
      flush()
      currentTexture = region.texture
      currentTexture.bind(0)
    }
    ensureCapacity(6)
    val u = region.u
    val v = region.v
    val u2 = region.u2
    val v2 = region.v2

    vertex(x, y, u, v, r, g, b, a)
    vertex(x + w, y, u2, v, r, g, b, a)
    vertex(x + w, y + h, u2, v2, r, g, b, a)

    vertex(x, y, u, v, r, g, b, a)
    vertex(x + w, y + h, u2, v2, r, g, b, a)
    vertex(x, y + h, u, v2, r, g, b, a)
  }

  /** Draw a textured quad with rotation around its center. Angle in radians. */
  def drawRotated(region: TextureRegion, x: Float, y: Float, w: Float, h: Float,
                  angle: Float, r: Float = 1f, g: Float = 1f, b: Float = 1f, a: Float = 1f): Unit = {
    if (region.texture != currentTexture) {
      flush()
      currentTexture = region.texture
      currentTexture.bind(0)
    }
    ensureCapacity(6)
    val cx = x + w * 0.5f
    val cy = y + h * 0.5f
    val cos = Math.cos(angle).toFloat
    val sin = Math.sin(angle).toFloat
    val hw = w * 0.5f
    val hh = h * 0.5f

    def rot(lx: Float, ly: Float): (Float, Float) = {
      (cx + lx * cos - ly * sin, cy + lx * sin + ly * cos)
    }

    val (x0, y0) = rot(-hw, -hh)
    val (x1, y1) = rot(hw, -hh)
    val (x2, y2) = rot(hw, hh)
    val (x3, y3) = rot(-hw, hh)

    val u = region.u; val v = region.v; val u2 = region.u2; val v2 = region.v2

    vertex(x0, y0, u, v, r, g, b, a)
    vertex(x1, y1, u2, v, r, g, b, a)
    vertex(x2, y2, u2, v2, r, g, b, a)

    vertex(x0, y0, u, v, r, g, b, a)
    vertex(x2, y2, u2, v2, r, g, b, a)
    vertex(x3, y3, u, v2, r, g, b, a)
  }

  def flush(): Unit = {
    if (vertexCount == 0) return
    buffer.flip()

    glBindVertexArray(vao)
    glBindBuffer(GL_ARRAY_BUFFER, vbo)

    if (vertexCount * VERTEX_SIZE > capacity * VERTEX_SIZE) {
      glBufferData(GL_ARRAY_BUFFER, buffer, GL_DYNAMIC_DRAW)
    } else {
      glBufferSubData(GL_ARRAY_BUFFER, 0, buffer)
    }

    glDrawArrays(GL_TRIANGLES, 0, vertexCount)
    glBindVertexArray(0)

    vertexCount = 0
    buffer.clear()
  }

  def dispose(): Unit = {
    glDeleteVertexArrays(vao)
    glDeleteBuffers(vbo)
    shader.dispose()
  }

  private def vertex(x: Float, y: Float, u: Float, v: Float, r: Float, g: Float, b: Float, a: Float): Unit = {
    buffer.put(x).put(y).put(u).put(v).put(r).put(g).put(b).put(a)
    vertexCount += 1
  }

  private def ensureCapacity(additionalVertices: Int): Unit = {
    val needed = (vertexCount + additionalVertices) * VERTEX_SIZE
    if (needed > capacity * VERTEX_SIZE) {
      flush()
      if (additionalVertices * VERTEX_SIZE > capacity * VERTEX_SIZE) {
        capacity = additionalVertices * 2
        buffer = BufferUtils.createFloatBuffer(capacity * VERTEX_SIZE)
        glBindBuffer(GL_ARRAY_BUFFER, vbo)
        glBufferData(GL_ARRAY_BUFFER, capacity * VERTEX_SIZE * 4L, GL_DYNAMIC_DRAW)
      }
    }
  }
}
