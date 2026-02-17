package com.gridgame.client.gl

import org.lwjgl.opengl.GL11._
import org.lwjgl.opengl.GL15._
import org.lwjgl.opengl.GL20._
import org.lwjgl.opengl.GL30._
import org.lwjgl.BufferUtils

import java.nio.FloatBuffer

/**
 * Batched renderer for colored 2D primitives: filled rectangles, ovals, polygons, and lines.
 * Vertices are position (vec2) + color (vec4) = 6 floats per vertex.
 * Supports standard alpha blending and additive blending for glow effects.
 */
class ShapeBatch(val shader: ShaderProgram) {
  private val VERTEX_SIZE = 6 // x, y, r, g, b, a
  private val INITIAL_CAPACITY = 4096 // vertices
  private var capacity = INITIAL_CAPACITY
  private var buffer: FloatBuffer = BufferUtils.createFloatBuffer(capacity * VERTEX_SIZE)
  private var vertexCount = 0
  private var drawing = false
  private var additive = false

  private val vao = glGenVertexArrays()
  private val vbo = glGenBuffers()

  // Set up VAO
  glBindVertexArray(vao)
  glBindBuffer(GL_ARRAY_BUFFER, vbo)
  glBufferData(GL_ARRAY_BUFFER, capacity * VERTEX_SIZE * 4L, GL_DYNAMIC_DRAW)
  // position
  glVertexAttribPointer(0, 2, GL_FLOAT, false, VERTEX_SIZE * 4, 0)
  glEnableVertexAttribArray(0)
  // color
  glVertexAttribPointer(1, 4, GL_FLOAT, false, VERTEX_SIZE * 4, 2 * 4L)
  glEnableVertexAttribArray(1)
  glBindVertexArray(0)

  def begin(projection: FloatBuffer): Unit = {
    if (drawing) throw new IllegalStateException("Already drawing")
    drawing = true
    vertexCount = 0
    buffer.clear()
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

  /** Switch between standard alpha blend and additive blend. Flushes current batch. */
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

  def fillRect(x: Float, y: Float, w: Float, h: Float, r: Float, g: Float, b: Float, a: Float): Unit = {
    ensureCapacity(6)
    // Two triangles
    vertex(x, y, r, g, b, a)
    vertex(x + w, y, r, g, b, a)
    vertex(x + w, y + h, r, g, b, a)

    vertex(x, y, r, g, b, a)
    vertex(x + w, y + h, r, g, b, a)
    vertex(x, y + h, r, g, b, a)
  }

  /** Fill a rectangle with per-corner colors for smooth gradients. Order: TL, TR, BR, BL. */
  def fillRectGradient(x: Float, y: Float, w: Float, h: Float,
                       r0: Float, g0: Float, b0: Float, a0: Float,
                       r1: Float, g1: Float, b1: Float, a1: Float,
                       r2: Float, g2: Float, b2: Float, a2: Float,
                       r3: Float, g3: Float, b3: Float, a3: Float): Unit = {
    ensureCapacity(6)
    vertex(x, y, r0, g0, b0, a0)         // TL
    vertex(x + w, y, r1, g1, b1, a1)     // TR
    vertex(x + w, y + h, r2, g2, b2, a2) // BR

    vertex(x, y, r0, g0, b0, a0)         // TL
    vertex(x + w, y + h, r2, g2, b2, a2) // BR
    vertex(x, y + h, r3, g3, b3, a3)     // BL
  }

  /** Approximate an oval as a triangle fan. segments = 16 for small, 32 for large. */
  def fillOval(cx: Float, cy: Float, rx: Float, ry: Float, r: Float, g: Float, b: Float, a: Float, segments: Int = 20): Unit = {
    ensureCapacity(segments * 3)
    val step = (2.0 * Math.PI / segments).toFloat
    var angle = 0f
    var i = 0
    while (i < segments) {
      val a1 = angle
      val a2 = angle + step
      vertex(cx, cy, r, g, b, a)
      vertex(cx + rx * Math.cos(a1).toFloat, cy + ry * Math.sin(a1).toFloat, r, g, b, a)
      vertex(cx + rx * Math.cos(a2).toFloat, cy + ry * Math.sin(a2).toFloat, r, g, b, a)
      angle += step
      i += 1
    }
  }

  /** Fill an oval with edge alpha falloff for soft glow effect. */
  def fillOvalSoft(cx: Float, cy: Float, rx: Float, ry: Float, r: Float, g: Float, b: Float, centerA: Float, edgeA: Float, segments: Int = 20): Unit = {
    ensureCapacity(segments * 3)
    val step = (2.0 * Math.PI / segments).toFloat
    var angle = 0f
    var i = 0
    while (i < segments) {
      val a1 = angle
      val a2 = angle + step
      vertex(cx, cy, r, g, b, centerA)
      vertex(cx + rx * Math.cos(a1).toFloat, cy + ry * Math.sin(a1).toFloat, r, g, b, edgeA)
      vertex(cx + rx * Math.cos(a2).toFloat, cy + ry * Math.sin(a2).toFloat, r, g, b, edgeA)
      angle += step
      i += 1
    }
  }

  /** Fill a convex polygon given arrays of x and y coordinates. */
  def fillPolygon(xs: Array[Float], ys: Array[Float], r: Float, g: Float, b: Float, a: Float): Unit = {
    fillPolygon(xs, ys, Math.min(xs.length, ys.length), r, g, b, a)
  }

  /** Fill a convex polygon using the first `n` elements of the given arrays. */
  def fillPolygon(xs: Array[Float], ys: Array[Float], n: Int, r: Float, g: Float, b: Float, a: Float): Unit = {
    if (n < 3) return
    // Fan triangulation from first vertex (works for convex polygons)
    val numTris = n - 2
    ensureCapacity(numTris * 3)
    var i = 1
    while (i < n - 1) {
      vertex(xs(0), ys(0), r, g, b, a)
      vertex(xs(i), ys(i), r, g, b, a)
      vertex(xs(i + 1), ys(i + 1), r, g, b, a)
      i += 1
    }
  }

  /** Draw a line as a thin quad with optional width. */
  def strokeLine(x1: Float, y1: Float, x2: Float, y2: Float, lineWidth: Float, r: Float, g: Float, b: Float, a: Float): Unit = {
    ensureCapacity(6)
    val dx = x2 - x1
    val dy = y2 - y1
    val len = Math.sqrt(dx * dx + dy * dy).toFloat
    if (len < 0.001f) return
    val hw = lineWidth * 0.5f
    // Normal perpendicular to line direction
    val nx = -dy / len * hw
    val ny = dx / len * hw

    vertex(x1 + nx, y1 + ny, r, g, b, a)
    vertex(x1 - nx, y1 - ny, r, g, b, a)
    vertex(x2 - nx, y2 - ny, r, g, b, a)

    vertex(x1 + nx, y1 + ny, r, g, b, a)
    vertex(x2 - nx, y2 - ny, r, g, b, a)
    vertex(x2 + nx, y2 + ny, r, g, b, a)
  }

  /** Draw a line with alpha falloff on edges for anti-aliased appearance. */
  def strokeLineSoft(x1: Float, y1: Float, x2: Float, y2: Float, lineWidth: Float,
                     r: Float, g: Float, b: Float, a: Float): Unit = {
    ensureCapacity(18) // 6 triangles for 3-strip soft line
    val dx = x2 - x1
    val dy = y2 - y1
    val len = Math.sqrt(dx * dx + dy * dy).toFloat
    if (len < 0.001f) return

    val coreHW = lineWidth * 0.35f
    val edgeHW = lineWidth * 0.65f
    val nx = -dy / len
    val ny = dx / len

    // Outer edge (transparent) → Core edge (opaque) → Center → Core edge → Outer edge
    val ox1 = nx * edgeHW; val oy1 = ny * edgeHW
    val cx1 = nx * coreHW; val cy1 = ny * coreHW

    // Left outer to left core
    vertex(x1 + ox1, y1 + oy1, r, g, b, 0f)
    vertex(x1 + cx1, y1 + cy1, r, g, b, a)
    vertex(x2 + cx1, y2 + cy1, r, g, b, a)
    vertex(x1 + ox1, y1 + oy1, r, g, b, 0f)
    vertex(x2 + cx1, y2 + cy1, r, g, b, a)
    vertex(x2 + ox1, y2 + oy1, r, g, b, 0f)

    // Left core to right core (opaque center)
    vertex(x1 + cx1, y1 + cy1, r, g, b, a)
    vertex(x1 - cx1, y1 - cy1, r, g, b, a)
    vertex(x2 - cx1, y2 - cy1, r, g, b, a)
    vertex(x1 + cx1, y1 + cy1, r, g, b, a)
    vertex(x2 - cx1, y2 - cy1, r, g, b, a)
    vertex(x2 + cx1, y2 + cy1, r, g, b, a)

    // Right core to right outer
    vertex(x1 - cx1, y1 - cy1, r, g, b, a)
    vertex(x1 - ox1, y1 - oy1, r, g, b, 0f)
    vertex(x2 - ox1, y2 - oy1, r, g, b, 0f)
    vertex(x1 - cx1, y1 - cy1, r, g, b, a)
    vertex(x2 - ox1, y2 - oy1, r, g, b, 0f)
    vertex(x2 - cx1, y2 - cy1, r, g, b, a)
  }

  /** Stroke an oval outline as line segments. */
  def strokeOval(cx: Float, cy: Float, rx: Float, ry: Float, lineWidth: Float,
                 r: Float, g: Float, b: Float, a: Float, segments: Int = 20): Unit = {
    val step = (2.0 * Math.PI / segments).toFloat
    var angle = 0f
    var i = 0
    while (i < segments) {
      val a1 = angle
      val a2 = angle + step
      val x1 = cx + rx * Math.cos(a1).toFloat
      val y1 = cy + ry * Math.sin(a1).toFloat
      val x2 = cx + rx * Math.cos(a2).toFloat
      val y2 = cy + ry * Math.sin(a2).toFloat
      strokeLine(x1, y1, x2, y2, lineWidth, r, g, b, a)
      angle += step
      i += 1
    }
  }

  /** Stroke a polygon outline. */
  def strokePolygon(xs: Array[Float], ys: Array[Float], lineWidth: Float,
                    r: Float, g: Float, b: Float, a: Float): Unit = {
    strokePolygon(xs, ys, Math.min(xs.length, ys.length), lineWidth, r, g, b, a)
  }

  /** Stroke a polygon outline using the first `n` elements. */
  def strokePolygon(xs: Array[Float], ys: Array[Float], n: Int, lineWidth: Float,
                    r: Float, g: Float, b: Float, a: Float): Unit = {
    if (n < 2) return
    var i = 0
    while (i < n) {
      val j = (i + 1) % n
      strokeLine(xs(i), ys(i), xs(j), ys(j), lineWidth, r, g, b, a)
      i += 1
    }
  }

  /** Stroke a rectangle outline. */
  def strokeRect(x: Float, y: Float, w: Float, h: Float, lineWidth: Float,
                 r: Float, g: Float, b: Float, a: Float): Unit = {
    strokeLine(x, y, x + w, y, lineWidth, r, g, b, a)
    strokeLine(x + w, y, x + w, y + h, lineWidth, r, g, b, a)
    strokeLine(x + w, y + h, x, y + h, lineWidth, r, g, b, a)
    strokeLine(x, y + h, x, y, lineWidth, r, g, b, a)
  }

  def flush(): Unit = {
    if (vertexCount == 0) return
    buffer.flip()

    glBindVertexArray(vao)
    glBindBuffer(GL_ARRAY_BUFFER, vbo)

    // Upload vertex data
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

  private def vertex(x: Float, y: Float, r: Float, g: Float, b: Float, a: Float): Unit = {
    buffer.put(x).put(y).put(r).put(g).put(b).put(a)
    vertexCount += 1
  }

  private def ensureCapacity(additionalVertices: Int): Unit = {
    val needed = (vertexCount + additionalVertices) * VERTEX_SIZE
    if (needed > capacity * VERTEX_SIZE) {
      flush()
      if (additionalVertices * VERTEX_SIZE > capacity * VERTEX_SIZE) {
        // Grow buffer
        capacity = additionalVertices * 2
        buffer = BufferUtils.createFloatBuffer(capacity * VERTEX_SIZE)
        glBindBuffer(GL_ARRAY_BUFFER, vbo)
        glBufferData(GL_ARRAY_BUFFER, capacity * VERTEX_SIZE * 4L, GL_DYNAMIC_DRAW)
      }
    }
  }
}
