package com.gridgame.client.gl

import org.lwjgl.opengl.GL11._
import org.lwjgl.opengl.GL15._
import org.lwjgl.opengl.GL20._
import org.lwjgl.opengl.GL30._
import org.lwjgl.BufferUtils
import org.lwjgl.system.MemoryUtil

import java.nio.FloatBuffer

object SpriteBatch {
  /** How many staging-buffer-fulls the GPU ring holds before it wraps and orphans. */
  private[gl] val RING_FRAMES = 8
}

/**
 * Batched renderer for textured quads with per-vertex tint/alpha.
 * Vertices are position (vec2) + texcoord (vec2) + color (vec4) = 8 floats per vertex.
 * Flushes automatically on texture change. Supports additive blending for glow effects.
 */
class SpriteBatch(val shader: ShaderProgram) {
  private val VERTEX_SIZE = 8 // x, y, u, v, r, g, b, a
  private val VERTEX_BYTES = VERTEX_SIZE * 4L
  private val INITIAL_CAPACITY = 4096
  private var capacity = INITIAL_CAPACITY
  // Staging memory, written through its raw address — see ShapeBatch.
  private var buffer: FloatBuffer = BufferUtils.createFloatBuffer(capacity * VERTEX_SIZE)
  private var bufferAddr: Long = MemoryUtil.memAddress(buffer)
  private var vertexCount = 0
  private var drawing = false
  private var additive = false
  private var currentTexture: GLTexture = _

  // GPU ring buffer — see ShapeBatch for why each flush must land on fresh bytes.
  private var ringVerts = INITIAL_CAPACITY * SpriteBatch.RING_FRAMES
  private var ringOffset = 0

  private val vao = glGenVertexArrays()
  private val vbo = glGenBuffers()

  glBindVertexArray(vao)
  glBindBuffer(GL_ARRAY_BUFFER, vbo)
  glBufferData(GL_ARRAY_BUFFER, ringVerts * VERTEX_SIZE * 4L, GL_STREAM_DRAW)
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

  // Set texture sampler uniform once (it never changes)
  shader.use()
  shader.setUniform1i("uTexture", 0)

  def begin(projection: FloatBuffer): Unit = {
    if (drawing) throw new IllegalStateException("Already drawing")
    drawing = true
    vertexCount = 0
    currentTexture = null
    shader.use()
    shader.setUniformMat4("uProjection", projection)
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

    val x0 = cx + (-hw) * cos - (-hh) * sin; val y0 = cy + (-hw) * sin + (-hh) * cos
    val x1 = cx + hw * cos - (-hh) * sin;   val y1 = cy + hw * sin + (-hh) * cos
    val x2 = cx + hw * cos - hh * sin;      val y2 = cy + hw * sin + hh * cos
    val x3 = cx + (-hw) * cos - hh * sin;   val y3 = cy + (-hw) * sin + hh * cos

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

    glBindVertexArray(vao)
    glBindBuffer(GL_ARRAY_BUFFER, vbo)

    if (ringOffset + vertexCount > ringVerts) {
      // Wrapped: discard the old store. The driver hands back untouched memory instead of
      // waiting on draws that still reference the old one, and every offset in the new
      // generation is safe to write unsynchronized again.
      glBufferData(GL_ARRAY_BUFFER, ringVerts.toLong * VERTEX_BYTES, GL_STREAM_DRAW)
      ringOffset = 0
    }
    val byteOffset = ringOffset.toLong * VERTEX_BYTES
    val byteLen = vertexCount.toLong * VERTEX_BYTES
    // Raw-address mapping: the ByteBuffer form allocates a wrapper per flush (see ShapeBatch)
    val mapped = nglMapBufferRange(GL_ARRAY_BUFFER, byteOffset, byteLen, ShapeBatch.MAP_FLAGS)
    if (mapped != 0L) {
      MemoryUtil.memCopy(bufferAddr, mapped, byteLen)
      glUnmapBuffer(GL_ARRAY_BUFFER)
    } else {
      // Driver refused the mapping — fall back to the (much slower) copy path.
      buffer.limit(vertexCount * VERTEX_SIZE).position(0)
      glBufferSubData(GL_ARRAY_BUFFER, byteOffset, buffer)
      buffer.clear()
    }
    glDrawArrays(GL_TRIANGLES, ringOffset, vertexCount)
    ringOffset += vertexCount

    glBindVertexArray(0)

    vertexCount = 0
  }

  def dispose(): Unit = {
    glDeleteVertexArrays(vao)
    glDeleteBuffers(vbo)
    shader.dispose()
  }

  /** Every quad reserves its vertices with [[ensureCapacity]] first; the check here is only
    * a backstop, since a raw write past the end would corrupt native memory. */
  private def vertex(x: Float, y: Float, u: Float, v: Float, r: Float, g: Float, b: Float, a: Float): Unit = {
    if (vertexCount >= capacity) growStaging(capacity * 2)
    val p = bufferAddr + vertexCount * VERTEX_BYTES
    MemoryUtil.memPutFloat(p, x)
    MemoryUtil.memPutFloat(p + 4, y)
    MemoryUtil.memPutFloat(p + 8, u)
    MemoryUtil.memPutFloat(p + 12, v)
    MemoryUtil.memPutFloat(p + 16, r)
    MemoryUtil.memPutFloat(p + 20, g)
    MemoryUtil.memPutFloat(p + 24, b)
    MemoryUtil.memPutFloat(p + 28, a)
    vertexCount += 1
  }

  /** Replace the staging buffer with a larger one, keeping the vertices already in it. */
  private def growStaging(newCapacity: Int): Unit = {
    val grown = BufferUtils.createFloatBuffer(newCapacity * VERTEX_SIZE)
    val grownAddr = MemoryUtil.memAddress(grown)
    MemoryUtil.memCopy(bufferAddr, grownAddr, vertexCount * VERTEX_BYTES)
    buffer = grown
    bufferAddr = grownAddr
    capacity = newCapacity
    if (ringVerts < capacity * SpriteBatch.RING_FRAMES) {
      ringVerts = capacity * SpriteBatch.RING_FRAMES
      ringOffset = 0
      glBindBuffer(GL_ARRAY_BUFFER, vbo)
      glBufferData(GL_ARRAY_BUFFER, ringVerts * VERTEX_BYTES, GL_STREAM_DRAW)
    }
  }

  private def ensureCapacity(additionalVertices: Int): Unit = {
    if (vertexCount + additionalVertices > capacity) {
      flush()
      if (additionalVertices > capacity) growStaging(additionalVertices * 2)
    }
  }
}
