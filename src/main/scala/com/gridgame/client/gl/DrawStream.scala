package com.gridgame.client.gl

import org.lwjgl.opengl.GL11._
import org.lwjgl.opengl.GL15._
import org.lwjgl.opengl.GL20._
import org.lwjgl.opengl.GL30._
import org.lwjgl.opengl.GL32.glDrawElementsBaseVertex
import org.lwjgl.BufferUtils
import org.lwjgl.system.MemoryUtil

import java.nio.{FloatBuffer, ShortBuffer}

object DrawStream {
  /** How many staging-buffer-fulls the GPU rings hold before they wrap and orphan. */
  private[gl] val RING_FRAMES = 8

  private[gl] val MAP_FLAGS = GL_MAP_WRITE_BIT | GL_MAP_UNSYNCHRONIZED_BIT | GL_MAP_INVALIDATE_RANGE_BIT

  /** x, y, u, v, r, g, b, a */
  private[gl] final val VERTEX_FLOATS = 8
  private[gl] final val VERTEX_BYTES = VERTEX_FLOATS * 4L

  /** The texture coordinate a shape's vertices carry: the stream's shader draws a vertex with a u
    * below -1 in its colour alone, as the shapes' own shader did, and samples nothing. */
  private[gl] final val UNTEXTURED = -2f

  /** The main stream's shader: a shape's fragment is its colour, a sprite's its texel tinted by its
    * colour, the same expressions the two batches' shaders had. Which one is decided per triangle
    * (every vertex of a triangle is a shape's or a sprite's), so a 2x2 block of fragments always
    * takes one branch and the sprite's texture is sampled with its derivatives as before. */
  val VERT: String =
    """#version 330 core
      |layout(location = 0) in vec2 aPos;
      |layout(location = 1) in vec2 aTexCoord;
      |layout(location = 2) in vec4 aColor;
      |uniform mat4 uProjection;
      |out vec2 vTexCoord;
      |out vec4 vColor;
      |void main() {
      |  gl_Position = uProjection * vec4(aPos, 0.0, 1.0);
      |  vTexCoord = aTexCoord;
      |  vColor = aColor;
      |}""".stripMargin

  val FRAG: String =
    """#version 330 core
      |in vec2 vTexCoord;
      |in vec4 vColor;
      |uniform sampler2D uTexture;
      |out vec4 FragColor;
      |void main() {
      |  if (vTexCoord.x < -1.0) FragColor = vColor;
      |  else FragColor = texture(uTexture, vTexCoord) * vColor;
      |}""".stripMargin

  // GL's blend state as the streams last set it: bit 0 blending on, bit 1 additive; -1 not known
  private var blendState = -1
  // The stream with geometry waiting to be drawn, if any: at most one, so that what is drawn goes
  // to the GPU in the order it was asked for
  private var pending: DrawStream = null

  /** Draw whatever any stream has waiting. */
  def flushPending(): Unit = if (pending != null) pending.flush()

  private def setBlend(state: Int): Unit = {
    if (state != blendState) {
      // what is waiting was asked for under the old state, and is drawn under it
      flushPending()
      if (((state ^ blendState) & 1) != 0 || blendState < 0) {
        if ((state & 1) != 0) glEnable(GL_BLEND) else glDisable(GL_BLEND)
      }
      if (((state ^ blendState) & 2) != 0 || blendState < 0) {
        if ((state & 2) != 0) glBlendFunc(GL_SRC_ALPHA, GL_ONE)
        else glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
      }
      blendState = state
    }
  }

  /** glBlendFunc for alpha blending (false) or additive (true), as the batches set it. */
  private[gl] def blendAdditive(additive: Boolean): Unit =
    setBlend(if (blendState < 0) (if (additive) 3 else 1) else (blendState & 1) | (if (additive) 2 else 0))

  /** glEnable / glDisable of GL_BLEND, as the batches set it. */
  private[gl] def blendEnabled(on: Boolean): Unit =
    setBlend(if (blendState < 0) (if (on) 1 else 0) else (blendState & 2) | (if (on) 1 else 0))

  /** Something other than a stream changed GL's blend state: forget what it was, so the next set
    * is made for certain. Nothing may be waiting when it is called. */
  def blendChangedElsewhere(): Unit = { flushPending(); blendState = -1 }

  /** A new GL context is current (a match's window made): nothing of the last one's state holds. */
  def contextChanged(): Unit = { pending = null; blendState = -1 }
}

/**
 * The vertices and triangles a [[ShapeBatch]] and a [[SpriteBatch]] build, in the order they are
 * asked for, drawn with one draw call until something the GPU must know about changes: the
 * projection, the blend state, or the texture a sprite samples. The two batches used to have a
 * stream each, so a frame that went from a wall's sprite to a shot's shapes and back to the next
 * wall's sprite — the depth pass, most of all — was a draw call at every change, about half the
 * 200 or so a busy frame made (gl/CLAUDE.md, *One stream for shapes and sprites*). A shape's vertex
 * carries [[DrawStream.UNTEXTURED]] for its texture coordinate and is drawn in its colour alone.
 *
 * Every vertex is written once and every triangle as three 16-bit indices counted from the flush's
 * first vertex (glDrawElementsBaseVertex), into GPU rings mapped unsynchronized: see gl/CLAUDE.md,
 * *Batch Management*.
 */
final class DrawStream(val shader: ShaderProgram) {
  import DrawStream._

  // Vertices staged before a flush. At 4096 a busy frame flushed every few projectiles, and each
  // flush maps, copies and draws
  private val INITIAL_CAPACITY = 16384
  // A flush's indices are 16 bits, counted from its first vertex, so it holds at most this many
  private val MAX_CAPACITY = 65536
  private var capacity = INITIAL_CAPACITY
  // Staging memory, written through its raw address: a FloatBuffer.put per float (a limit check and
  // a position store each) was the single largest cost of building a busy frame
  private var buffer: FloatBuffer = BufferUtils.createFloatBuffer(capacity * VERTEX_FLOATS)
  private[gl] var bufferAddr: Long = MemoryUtil.memAddress(buffer)
  private[gl] var vertexCount = 0
  // Three indices a triangle. A fan of n triangles is n + 2 vertices and a band of n quads 2n + 2,
  // so a flush has room for about three indices a vertex
  private var indexCapacity = INITIAL_CAPACITY * 3
  private var indices: ShortBuffer = BufferUtils.createShortBuffer(indexCapacity)
  private[gl] var indexAddr: Long = MemoryUtil.memAddress(indices)
  private[gl] var indexCount = 0

  // The texture the sprites waiting sample (null: none of what is waiting samples one)
  private var texture: GLTexture = _

  // The projection as last given, and whether the shader has it yet
  private val projection = new Array[Float](16)
  private var projectionKnown = false

  // GPU rings, one for the vertices and one for the indices. Each flush appends at a fresh offset in
  // both and draws from there, so no upload lands on bytes the GPU may still be reading; when a ring
  // wraps its whole store is orphaned
  private var ringVerts = INITIAL_CAPACITY * RING_FRAMES
  private var ringOffset = 0
  private var ringIndices = indexCapacity * RING_FRAMES
  private var ringIndexOffset = 0

  private val vao = glGenVertexArrays()
  private val vbo = glGenBuffers()
  private val ibo = glGenBuffers()

  glBindVertexArray(vao)
  glBindBuffer(GL_ARRAY_BUFFER, vbo)
  glBufferData(GL_ARRAY_BUFFER, ringVerts * VERTEX_BYTES, GL_STREAM_DRAW)
  glVertexAttribPointer(0, 2, GL_FLOAT, false, VERTEX_BYTES.toInt, 0)       // position
  glEnableVertexAttribArray(0)
  glVertexAttribPointer(1, 2, GL_FLOAT, false, VERTEX_BYTES.toInt, 2 * 4L)  // texture coordinate
  glEnableVertexAttribArray(1)
  glVertexAttribPointer(2, 4, GL_FLOAT, false, VERTEX_BYTES.toInt, 4 * 4L)  // colour
  glEnableVertexAttribArray(2)
  // The index ring is the VAO's own (the element buffer binding is VAO state)
  glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ibo)
  glBufferData(GL_ELEMENT_ARRAY_BUFFER, ringIndices * 2L, GL_STREAM_DRAW)
  glBindVertexArray(0)

  shader.use()
  shader.setUniform1i("uTexture", 0)

  /** A batch begins on this stream: whatever any stream has waiting is drawn first, as a batch's
    * `begin` always did, and the shader is in use with this projection from here on (the caller may
    * set its other uniforms now). */
  def begin(proj: FloatBuffer): Unit = {
    flushPending()
    shader.use()
    var i = 0
    while (i < 16) { projection(i) = proj.get(i); i += 1 }
    shader.setUniformMat4("uProjection", proj)
    projectionKnown = true
  }

  /** The other batch takes over this stream without anything being drawn: only a new projection
    * draws what is waiting first. */
  def resume(proj: FloatBuffer): Unit = {
    var same = projectionKnown
    var i = 0
    while (same && i < 16) { same = projection(i) == proj.get(i); i += 1 }
    if (!same) begin(proj)
  }

  /** The texture the next sprite samples: what is waiting is drawn first if it samples another. */
  def useTexture(t: GLTexture): Unit = {
    if (t ne texture) {
      if (texture != null && vertexCount > 0) flush()
      texture = t
    }
  }

  /** Room for `vertices` more vertices and `idx` more indices in this flush, drawn first if either
    * won't fit; and this stream the one with geometry waiting. */
  def reserve(vertices: Int, idx: Int): Unit = {
    if (pending ne this) {
      flushPending()
      pending = this
    }
    if (vertexCount + vertices > capacity || indexCount + idx > indexCapacity) {
      val t = texture
      flush()
      texture = t
      pending = this
      if (vertexCount + vertices > capacity) growStaging(vertices * 2)
      if (indexCount + idx > indexCapacity) growIndices(idx * 2)
    }
  }

  /** One vertex, at the next place in the staging memory (reserved first with [[reserve]]). */
  @inline def vertex(x: Float, y: Float, u: Float, v: Float, r: Float, g: Float, b: Float, a: Float): Unit = {
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

  /** One triangle, by its vertices' numbers in this flush. */
  @inline def tri(a: Int, b: Int, c: Int): Unit = {
    if (indexCount + 3 > indexCapacity) growIndices(indexCapacity * 2)
    val p = indexAddr + indexCount * 2L
    MemoryUtil.memPutShort(p, a.toShort)
    MemoryUtil.memPutShort(p + 2, b.toShort)
    MemoryUtil.memPutShort(p + 4, c.toShort)
    indexCount += 3
  }

  /** Draw everything waiting, in one draw call. */
  def flush(): Unit = {
    if (pending eq this) pending = null
    if (indexCount == 0) { vertexCount = 0; texture = null; return }

    shader.use()
    if (texture != null) texture.bind(0)
    glBindVertexArray(vao)
    glBindBuffer(GL_ARRAY_BUFFER, vbo)

    if (ringOffset + vertexCount > ringVerts) {
      // Wrapped: discard the old store. The driver hands back untouched memory instead of waiting
      // on draws that still read the old one, and every offset in the new generation is safe to
      // write unsynchronized again.
      glBufferData(GL_ARRAY_BUFFER, ringVerts.toLong * VERTEX_BYTES, GL_STREAM_DRAW)
      ringOffset = 0
    }
    // The raw-address form: glMapBufferRange wraps each mapping in a new ByteBuffer
    val byteLen = vertexCount.toLong * VERTEX_BYTES
    val mapped = nglMapBufferRange(GL_ARRAY_BUFFER, ringOffset.toLong * VERTEX_BYTES, byteLen, MAP_FLAGS)
    if (mapped != 0L) {
      MemoryUtil.memCopy(bufferAddr, mapped, byteLen)
      glUnmapBuffer(GL_ARRAY_BUFFER)
    } else {
      // Driver refused the mapping: the (much slower) copy
      buffer.limit(vertexCount * VERTEX_FLOATS).position(0)
      glBufferSubData(GL_ARRAY_BUFFER, ringOffset.toLong * VERTEX_BYTES, buffer)
      buffer.clear()
    }

    if (ringIndexOffset + indexCount > ringIndices) {
      glBufferData(GL_ELEMENT_ARRAY_BUFFER, ringIndices * 2L, GL_STREAM_DRAW)
      ringIndexOffset = 0
    }
    val indexBytes = indexCount * 2L
    val mappedIdx = nglMapBufferRange(GL_ELEMENT_ARRAY_BUFFER, ringIndexOffset * 2L, indexBytes, MAP_FLAGS)
    if (mappedIdx != 0L) {
      MemoryUtil.memCopy(indexAddr, mappedIdx, indexBytes)
      glUnmapBuffer(GL_ELEMENT_ARRAY_BUFFER)
    } else {
      indices.limit(indexCount).position(0)
      glBufferSubData(GL_ELEMENT_ARRAY_BUFFER, ringIndexOffset * 2L, indices)
      indices.clear()
    }

    glDrawElementsBaseVertex(GL_TRIANGLES, indexCount, GL_UNSIGNED_SHORT, ringIndexOffset * 2L, ringOffset)
    ringOffset += vertexCount
    ringIndexOffset += indexCount
    glBindVertexArray(0)

    vertexCount = 0
    indexCount = 0
    texture = null
  }

  /** Replace the staging buffer with a larger one, keeping the vertices already in it. */
  private def growStaging(newCapacity0: Int): Unit = {
    val newCapacity = Math.min(MAX_CAPACITY, newCapacity0)
    if (newCapacity <= vertexCount) throw new IllegalStateException(s"a flush holds at most $MAX_CAPACITY vertices")
    val grown = BufferUtils.createFloatBuffer(newCapacity * VERTEX_FLOATS)
    val grownAddr = MemoryUtil.memAddress(grown)
    MemoryUtil.memCopy(bufferAddr, grownAddr, vertexCount * VERTEX_BYTES)
    buffer = grown
    bufferAddr = grownAddr
    capacity = newCapacity
    // The GPU ring holds RING_FRAMES staging-buffer-fulls; a flush larger than the ring would never
    // fit, so it grows with the staging buffer
    if (ringVerts < capacity * RING_FRAMES) {
      ringVerts = capacity * RING_FRAMES
      ringOffset = 0
      glBindBuffer(GL_ARRAY_BUFFER, vbo)
      glBufferData(GL_ARRAY_BUFFER, ringVerts * VERTEX_BYTES, GL_STREAM_DRAW)
    }
  }

  /** Replace the index staging buffer with a larger one, keeping the indices already in it. */
  private def growIndices(newCapacity: Int): Unit = {
    val grown = BufferUtils.createShortBuffer(newCapacity)
    val grownAddr = MemoryUtil.memAddress(grown)
    MemoryUtil.memCopy(indexAddr, grownAddr, indexCount * 2L)
    indices = grown
    indexAddr = grownAddr
    indexCapacity = newCapacity
    if (ringIndices < indexCapacity * RING_FRAMES) {
      ringIndices = indexCapacity * RING_FRAMES
      ringIndexOffset = 0
      glBindVertexArray(vao) // the element buffer binding is the VAO's
      glBufferData(GL_ELEMENT_ARRAY_BUFFER, ringIndices * 2L, GL_STREAM_DRAW)
      glBindVertexArray(0)
    }
  }

  private var disposed = false

  /** Free its GL objects and its shader (once, though both batches on it may ask). */
  def dispose(): Unit = {
    if (disposed) return
    disposed = true
    if (pending eq this) pending = null
    glDeleteVertexArrays(vao)
    glDeleteBuffers(vbo)
    glDeleteBuffers(ibo)
    shader.dispose()
  }
}
