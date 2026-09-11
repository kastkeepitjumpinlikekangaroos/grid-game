package com.gridgame.client.gl

import org.lwjgl.opengl.GL11._
import org.lwjgl.opengl.GL15._
import org.lwjgl.opengl.GL20._
import org.lwjgl.opengl.GL30._
import org.lwjgl.BufferUtils
import org.lwjgl.system.MemoryUtil

import java.nio.FloatBuffer

/**
 * Batched renderer for colored 2D primitives: filled rectangles, ovals, polygons, and lines.
 * Vertices are position (vec2) + color (vec4) = 6 floats per vertex.
 * Supports standard alpha blending and additive blending for glow effects.
 */
object ShapeBatch {
  /** How many staging-buffer-fulls the GPU ring holds before it wraps and orphans. */
  private[gl] val RING_FRAMES = 8

  /**
   * Streaming upload flags. Measured on this machine, per flush: plain glBufferSubData
   * 70us (it waits for every pending draw that reads the buffer, whatever offset you
   * write), orphan+glBufferSubData 3.7us, and an unsynchronized mapped range 0.4us.
   * The ring plus orphan-on-wrap is what makes UNSYNCHRONIZED safe: no byte is ever
   * rewritten while a draw that reads it is still in flight.
   */
  private[gl] val MAP_FLAGS = GL_MAP_WRITE_BIT | GL_MAP_UNSYNCHRONIZED_BIT | GL_MAP_INVALIDATE_RANGE_BIT

  // Pre-computed sin/cos lookup tables for common segment counts used in oval rendering.
  // Uses direct array indexing (max segment count 32) to avoid Map.get Option allocation.
  private val MAX_SEGMENTS = 32
  // cosLUT(n) and sinLUT(n) are non-null for supported segment counts; null otherwise
  private val cosLUT = new Array[Array[Float]](MAX_SEGMENTS + 1)
  private val sinLUT = new Array[Array[Float]](MAX_SEGMENTS + 1)
  locally {
    val segmentCounts = Array(4, 6, 8, 10, 12, 14, 16, 20, 24, 32)
    var si = 0
    while (si < segmentCounts.length) {
      val n = segmentCounts(si)
      val step = 2.0 * Math.PI / n
      val cos = new Array[Float](n + 1)
      val sin = new Array[Float](n + 1)
      var i = 0
      while (i <= n) {
        cos(i) = Math.cos(i * step).toFloat
        sin(i) = Math.sin(i * step).toFloat
        i += 1
      }
      cosLUT(n) = cos
      sinLUT(n) = sin
      si += 1
    }
  }

  @inline def getCos(segments: Int, index: Int): Float = {
    if (segments <= MAX_SEGMENTS) {
      val arr = cosLUT(segments)
      if (arr != null) return arr(index)
    }
    Math.cos(index.toDouble * 2.0 * Math.PI / segments).toFloat
  }
  @inline def getSin(segments: Int, index: Int): Float = {
    if (segments <= MAX_SEGMENTS) {
      val arr = sinLUT(segments)
      if (arr != null) return arr(index)
    }
    Math.sin(index.toDouble * 2.0 * Math.PI / segments).toFloat
  }
}

class ShapeBatch(val shader: ShaderProgram) {
  private val VERTEX_SIZE = 6 // x, y, r, g, b, a
  private val VERTEX_BYTES = VERTEX_SIZE * 4L
  private val INITIAL_CAPACITY = 4096 // vertices
  private var capacity = INITIAL_CAPACITY
  // Staging memory, written through its raw address: a FloatBuffer.put per float (a limit
  // check and a position store each, six per vertex) was the single largest cost of
  // building a busy frame.
  private var buffer: FloatBuffer = BufferUtils.createFloatBuffer(capacity * VERTEX_SIZE)
  private var bufferAddr: Long = MemoryUtil.memAddress(buffer)
  private var vertexCount = 0
  private var drawing = false
  private var additive = false

  // Whole-batch modifiers applied in `vertex`: an alpha multiplier and a uniform scale about
  // a pivot. They let any shape renderer be faded or shrunk without knowing it — which is
  // how a projectile the terrain has stopped is drawn sinking into the surface. `begin`
  // resets them, so a caller that forgets `resetModifiers` cannot leak them into a frame.
  private var alphaMul = 1f
  private var xfOn = false
  private var xfPivX = 0f
  private var xfPivY = 0f
  private var xfScale = 1f

  def setAlphaMultiplier(m: Float): Unit = { alphaMul = m }
  def setScaleAbout(pivotX: Float, pivotY: Float, scale: Float): Unit = {
    xfPivX = pivotX; xfPivY = pivotY; xfScale = scale; xfOn = scale != 1f
  }
  def resetModifiers(): Unit = { alphaMul = 1f; xfOn = false }

  // GPU ring buffer. Each flush appends at a fresh offset and draws from there, so no
  // upload ever lands on bytes the GPU may still be reading — overwriting a range that is
  // still in flight is what makes the driver stall (a single 330-vertex flush was measured
  // at 1.7ms). When the ring wraps we orphan the whole store, which hands us fresh storage
  // instead of waiting on the old one.
  private var ringVerts = INITIAL_CAPACITY * ShapeBatch.RING_FRAMES
  private var ringOffset = 0

  private val vao = glGenVertexArrays()
  private val vbo = glGenBuffers()

  // Set up VAO
  glBindVertexArray(vao)
  glBindBuffer(GL_ARRAY_BUFFER, vbo)
  glBufferData(GL_ARRAY_BUFFER, ringVerts * VERTEX_SIZE * 4L, GL_STREAM_DRAW)
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
    resetModifiers()
    shader.use()
    shader.setUniformMat4("uProjection", projection)
    glEnable(GL_BLEND)
    // Set the func directly rather than via setAdditiveBlend(false): that call is a no-op
    // when `additive` is already false, which would leave the batch drawing under whatever
    // blend func the previous GL state happened to have (GL_ONE/GL_ZERO on a fresh context,
    // i.e. every alpha ignored).
    additive = false
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
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

  /** Fast 6-vertex rect for sub-4px elements where oval detail is imperceptible. */
  @inline def fillDot(cx: Float, cy: Float, r: Float, cr: Float, cg: Float, cb: Float, ca: Float): Unit = {
    fillRect(cx - r, cy - r, r * 2, r * 2, cr, cg, cb, ca)
  }

  /** Fill a rounded rectangle with uniform color. Corner radius is clamped to half the smallest dimension. */
  def fillRoundedRect(x: Float, y: Float, w: Float, h: Float, radius: Float, r: Float, g: Float, b: Float, a: Float): Unit = {
    val rad = Math.min(radius, Math.min(w, h) * 0.5f)
    if (rad < 1f) { fillRect(x, y, w, h, r, g, b, a); return }
    val segs = 6 // segments per corner quarter
    // Center cross (two rects)
    fillRect(x + rad, y, w - rad * 2, h, r, g, b, a)
    fillRect(x, y + rad, rad, h - rad * 2, r, g, b, a)
    fillRect(x + w - rad, y + rad, rad, h - rad * 2, r, g, b, a)
    // Four corners as triangle fans
    roundedCorner(x + rad, y + rad, rad, Math.PI.toFloat, segs, r, g, b, a)         // TL
    roundedCorner(x + w - rad, y + rad, rad, Math.PI.toFloat * 1.5f, segs, r, g, b, a) // TR
    roundedCorner(x + w - rad, y + h - rad, rad, 0f, segs, r, g, b, a)               // BR
    roundedCorner(x + rad, y + h - rad, rad, Math.PI.toFloat * 0.5f, segs, r, g, b, a) // BL
  }

  /** Fill a rounded rectangle with a vertical gradient (top to bottom). */
  def fillRoundedRectGradient(x: Float, y: Float, w: Float, h: Float, radius: Float,
                               r0: Float, g0: Float, b0: Float, a0: Float,
                               r1: Float, g1: Float, b1: Float, a1: Float): Unit = {
    val rad = Math.min(radius, Math.min(w, h) * 0.5f)
    if (rad < 1f) {
      fillRectGradient(x, y, w, h, r0, g0, b0, a0, r0, g0, b0, a0, r1, g1, b1, a1, r1, g1, b1, a1)
      return
    }
    // Approximate with 3 horizontal bands (top corners, middle, bottom corners)
    val topFrac = rad / h; val botFrac = 1f - topFrac
    val mr = r0 + (r1 - r0) * topFrac; val mg = g0 + (g1 - g0) * topFrac; val mb = b0 + (b1 - b0) * topFrac; val ma = a0 + (a1 - a0) * topFrac
    val mr2 = r0 + (r1 - r0) * botFrac; val mg2 = g0 + (g1 - g0) * botFrac; val mb2 = b0 + (b1 - b0) * botFrac; val ma2 = a0 + (a1 - a0) * botFrac
    // Top band with rounded corners
    fillRoundedRect(x, y, w, rad, rad, r0, g0, b0, a0)
    // Middle band (straight rect)
    fillRectGradient(x, y + rad, w, h - rad * 2, mr, mg, mb, ma, mr, mg, mb, ma, mr2, mg2, mb2, ma2, mr2, mg2, mb2, ma2)
    // Bottom band with rounded corners
    fillRoundedRect(x, y + h - rad, w, rad, rad, r1, g1, b1, a1)
  }

  private def roundedCorner(cx: Float, cy: Float, radius: Float, startAngle: Float, segs: Int,
                            r: Float, g: Float, b: Float, a: Float): Unit = {
    val step = (Math.PI.toFloat * 0.5f) / segs
    ensureCapacity(segs * 3)
    var i = 0
    while (i < segs) {
      val a1 = startAngle + i * step
      val a2 = startAngle + (i + 1) * step
      vertex(cx, cy, r, g, b, a)
      vertex(cx + Math.cos(a1).toFloat * radius, cy + Math.sin(a1).toFloat * radius, r, g, b, a)
      vertex(cx + Math.cos(a2).toFloat * radius, cy + Math.sin(a2).toFloat * radius, r, g, b, a)
      i += 1
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
    var i = 0
    while (i < segments) {
      vertex(cx, cy, r, g, b, a)
      vertex(cx + rx * ShapeBatch.getCos(segments, i), cy + ry * ShapeBatch.getSin(segments, i), r, g, b, a)
      vertex(cx + rx * ShapeBatch.getCos(segments, i + 1), cy + ry * ShapeBatch.getSin(segments, i + 1), r, g, b, a)
      i += 1
    }
  }

  /** Fill an oval with edge alpha falloff for soft glow effect. */
  def fillOvalSoft(cx: Float, cy: Float, rx: Float, ry: Float, r: Float, g: Float, b: Float, centerA: Float, edgeA: Float, segments: Int = 20): Unit = {
    ensureCapacity(segments * 3)
    var i = 0
    while (i < segments) {
      vertex(cx, cy, r, g, b, centerA)
      vertex(cx + rx * ShapeBatch.getCos(segments, i), cy + ry * ShapeBatch.getSin(segments, i), r, g, b, edgeA)
      vertex(cx + rx * ShapeBatch.getCos(segments, i + 1), cy + ry * ShapeBatch.getSin(segments, i + 1), r, g, b, edgeA)
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
    var i = 0
    while (i < segments) {
      val x1 = cx + rx * ShapeBatch.getCos(segments, i)
      val y1 = cy + ry * ShapeBatch.getSin(segments, i)
      val x2 = cx + rx * ShapeBatch.getCos(segments, i + 1)
      val y2 = cy + ry * ShapeBatch.getSin(segments, i + 1)
      strokeLine(x1, y1, x2, y2, lineWidth, r, g, b, a)
      i += 1
    }
  }

  /** Stroke a partial oval outline. startAngle/sweep in radians, CCW. */
  def strokeArc(cx: Float, cy: Float, rx: Float, ry: Float,
                startAngle: Float, sweep: Float, lineWidth: Float,
                r: Float, g: Float, b: Float, a: Float, segments: Int = 12): Unit = {
    if (segments < 1) return
    val step = sweep / segments
    var px = cx + rx * Math.cos(startAngle).toFloat
    var py = cy + ry * Math.sin(startAngle).toFloat
    var i = 0
    while (i < segments) {
      val ang = startAngle + step * (i + 1)
      val nx = cx + rx * Math.cos(ang).toFloat
      val ny = cy + ry * Math.sin(ang).toFloat
      strokeLine(px, py, nx, ny, lineWidth, r, g, b, a)
      px = nx; py = ny
      i += 1
    }
  }

  /**
   * Filled band between an inner and an outer ellipse over an angular sweep, with alpha
   * ramping from aStart at startAngle to aEnd at the far end. One primitive for gauges,
   * crescents, shockwave arcs and swept motion trails — all of which read as flat
   * geometry when built from plain strokeLine.
   */
  def fillArcBand(cx: Float, cy: Float, rxIn: Float, ryIn: Float, rxOut: Float, ryOut: Float,
                  startAngle: Float, sweep: Float, segments: Int,
                  r: Float, g: Float, b: Float, aStart: Float, aEnd: Float): Unit = {
    if (segments < 1) return
    ensureCapacity(segments * 6)
    val step = sweep / segments
    val invSegs = 1f / segments
    var i = 0
    while (i < segments) {
      val a0 = startAngle + step * i
      val a1 = a0 + step
      val al0 = aStart + (aEnd - aStart) * (i * invSegs)
      val al1 = aStart + (aEnd - aStart) * ((i + 1) * invSegs)
      val c0 = Math.cos(a0).toFloat; val s0 = Math.sin(a0).toFloat
      val c1 = Math.cos(a1).toFloat; val s1 = Math.sin(a1).toFloat
      val ix0 = cx + rxIn * c0;  val iy0 = cy + ryIn * s0
      val ox0 = cx + rxOut * c0; val oy0 = cy + ryOut * s0
      val ix1 = cx + rxIn * c1;  val iy1 = cy + ryIn * s1
      val ox1 = cx + rxOut * c1; val oy1 = cy + ryOut * s1
      vertex(ix0, iy0, r, g, b, al0); vertex(ox0, oy0, r, g, b, al0); vertex(ox1, oy1, r, g, b, al1)
      vertex(ix0, iy0, r, g, b, al0); vertex(ox1, oy1, r, g, b, al1); vertex(ix1, iy1, r, g, b, al1)
      i += 1
    }
  }

  /**
   * Four-point star flare: two crossed tapered spikes with a hot core. `angle` rotates the
   * long axis, `ratio` is the short spike length relative to the long one.
   */
  def fillStarFlare(cx: Float, cy: Float, len: Float, thickness: Float, angle: Float, ratio: Float,
                    r: Float, g: Float, b: Float, a: Float): Unit = {
    ensureCapacity(24)
    val c = Math.cos(angle).toFloat; val s = Math.sin(angle).toFloat
    // Long axis spikes (along +/- (c,s)), short axis spikes (along +/- (-s,c))
    starSpike(cx, cy, c, s, len, thickness, r, g, b, a)
    starSpike(cx, cy, -c, -s, len, thickness, r, g, b, a)
    starSpike(cx, cy, -s, c, len * ratio, thickness, r, g, b, a)
    starSpike(cx, cy, s, -c, len * ratio, thickness, r, g, b, a)
    fillOvalSoft(cx, cy, thickness * 1.4f, thickness * 1.4f, 1f, 1f, 1f, a, 0f, 6)
  }

  private def starSpike(cx: Float, cy: Float, dx: Float, dy: Float, len: Float, thickness: Float,
                        r: Float, g: Float, b: Float, a: Float): Unit = {
    val px = -dy * thickness * 0.5f
    val py = dx * thickness * 0.5f
    vertex(cx + px, cy + py, r, g, b, a)
    vertex(cx - px, cy - py, r, g, b, a)
    vertex(cx + dx * len, cy + dy * len, r, g, b, 0f)
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
    // The raw-address form: glMapBufferRange wraps each mapping in a new ByteBuffer, since
    // every flush maps a different range — one allocation per flush, ~100 a frame.
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

  /** Every primitive reserves its vertices with [[ensureCapacity]] first; the check here
    * is only a backstop, since a raw write past the end would corrupt native memory. */
  private def vertex(x: Float, y: Float, r: Float, g: Float, b: Float, a: Float): Unit = {
    if (vertexCount >= capacity) growStaging(capacity * 2)
    val p = bufferAddr + vertexCount * VERTEX_BYTES
    if (xfOn) {
      MemoryUtil.memPutFloat(p, xfPivX + (x - xfPivX) * xfScale)
      MemoryUtil.memPutFloat(p + 4, xfPivY + (y - xfPivY) * xfScale)
    } else {
      MemoryUtil.memPutFloat(p, x)
      MemoryUtil.memPutFloat(p + 4, y)
    }
    MemoryUtil.memPutFloat(p + 8, r)
    MemoryUtil.memPutFloat(p + 12, g)
    MemoryUtil.memPutFloat(p + 16, b)
    MemoryUtil.memPutFloat(p + 20, a * alphaMul)
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
    // The GPU ring holds RING_FRAMES staging-buffer-fulls; a flush larger than the ring
    // would never fit, so grow it with the staging buffer.
    if (ringVerts < capacity * ShapeBatch.RING_FRAMES) {
      ringVerts = capacity * ShapeBatch.RING_FRAMES
      ringOffset = 0
      glBindBuffer(GL_ARRAY_BUFFER, vbo)
      glBufferData(GL_ARRAY_BUFFER, ringVerts * VERTEX_BYTES, GL_STREAM_DRAW)
    }
  }

  private def ensureCapacity(additionalVertices: Int): Unit = {
    if (vertexCount + additionalVertices > capacity) {
      flush()
      // Grow the staging buffer, and the GPU ring with it
      if (additionalVertices > capacity) growStaging(additionalVertices * 2)
    }
  }
}
