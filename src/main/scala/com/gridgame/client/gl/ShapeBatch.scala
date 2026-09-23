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

  // Pre-computed sin/cos lookup tables for every segment count an oval may be drawn with, so no
  // oval ever calls Math.cos. Direct array indexing, to avoid Map.get Option allocation.
  private[gl] val MAX_SEGMENTS = 64
  // cosLUT(n) and sinLUT(n) are non-null for 3 <= n <= MAX_SEGMENTS
  private val cosLUT = new Array[Array[Float]](MAX_SEGMENTS + 1)
  private val sinLUT = new Array[Array[Float]](MAX_SEGMENTS + 1)
  locally {
    var n = 3
    while (n <= MAX_SEGMENTS) {
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
      n += 1
    }
  }

  /** Mitre joins longer than this many half-widths are cut back to it, so a hairpin turn in a
    * polyline doesn't throw a spike across the screen. */
  private val MITER_LIMIT = 3f

  /**
   * Dev check for the convexity `fillPolygon` requires. A non-convex outline fans into
   * something that is not its own shape — a throwing star fills as a lopsided blob with
   * its notches bridged, a crescent blade as a solid slab — and because the stroked
   * outline still traces the true shape, it reads as a rendering glitch rather than as
   * wrong geometry. Run any client binary with `GRIDGAME_POLYCHECK=1` and every offending
   * call site prints once; the projectile gallery covers the whole roster in one pass.
   * Free when unset: the flag is a static val, so the branch folds away.
   */
  private[gl] val POLY_CHECK = System.getenv("GRIDGAME_POLYCHECK") != null
  private val polySeen = new java.util.HashSet[String]()
  private[gl] def checkConvex(xs: Array[Float], ys: Array[Float], n: Int): Unit = {
    var pos = false; var neg = false
    var i = 0
    while (i < n) {
      val ax = xs((i + 1) % n) - xs(i); val ay = ys((i + 1) % n) - ys(i)
      val bx = xs((i + 2) % n) - xs((i + 1) % n); val by = ys((i + 2) % n) - ys((i + 1) % n)
      val cr = ax * by - ay * bx
      if (cr > 1e-3f) pos = true else if (cr < -1e-3f) neg = true
      i += 1
    }
    if (pos && neg) {
      val st = Thread.currentThread.getStackTrace
      var f = 2
      while (f < st.length && st(f).getClassName.endsWith("ShapeBatch")) f += 1
      val key = s"n=$n ${st(f).getClassName}.${st(f).getMethodName}:${st(f).getLineNumber}"
      if (polySeen.add(key)) System.err.println(s"[NONCONVEX] $key")
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
  // Vertices staged before a flush. At 4096 a busy frame flushed every few projectiles, and each
  // flush maps, copies and draws
  private val INITIAL_CAPACITY = 16384
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

  /**
   * Screen pixels per unit of the projection being drawn with, set by the renderer for each pass.
   * Ovals take their segment count from it ([[curveSegments]]). A ring 30 units across is 96
   * pixels wide on a HiDPI screen at the game's zoom, and at the 16 segments its call site asked
   * for it came out a visible sixteen-sided polygon; a spark 3 units across is a few pixels wide
   * however many segments it is given, and at Low quality the whole world is drawn smaller.
   */
  var pixelsPerUnit: Float = 1f

  /**
   * Segments for an oval of these radii: enough that no chord strays a pixel from the curve, and
   * no more — so a big ring gets more than its call site asked for and a small one fewer, and a
   * weak machine rendering the world at a third of the pixels builds a fraction of the vertices.
   * A small request (a hexagon drawn as a six-segment oval) is a shape, and is left alone.
   */
  @inline private def curveSegments(requested: Int, rx: Float, ry: Float): Int = {
    if (requested < 10) return requested
    val rPx = Math.max(Math.abs(rx), Math.abs(ry)) * pixelsPerUnit
    // A chord's sagitta is r(1 - cos(pi/n)) ~ r pi^2 / 2n^2, which is within 1px for n >= 2.22 sqrt(r)
    val need = (2.22f * Math.sqrt(rPx).toFloat).toInt + 1
    Math.max(8, Math.min(ShapeBatch.MAX_SEGMENTS, need))
  }

  /** As [[curveSegments]], for an oval whose edge fades to nothing, which hides its facets: within
    * 2px of the curve (n >= 1.57 sqrt(r)). */
  @inline private def softSegments(requested: Int, rx: Float, ry: Float): Int = {
    if (requested < 10) return requested
    val rPx = Math.max(Math.abs(rx), Math.abs(ry)) * pixelsPerUnit
    val need = (1.57f * Math.sqrt(rPx).toFloat).toInt + 1
    Math.max(8, Math.min(ShapeBatch.MAX_SEGMENTS, need))
  }

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

  /**
   * Fill a rounded rectangle with a vertical gradient (top to bottom), as one convex outline fanned
   * from its centre with each vertex coloured by its height. It used to be three rounded rects
   * stacked — a top band, a middle and a bottom band — whose inner corners were rounded too, so
   * every name plate had a notch in each side where the bands met.
   */
  def fillRoundedRectGradient(x: Float, y: Float, w: Float, h: Float, radius: Float,
                               r0: Float, g0: Float, b0: Float, a0: Float,
                               r1: Float, g1: Float, b1: Float, a1: Float): Unit = {
    val rad = Math.min(radius, Math.min(w, h) * 0.5f)
    if (rad < 1f) {
      fillRectGradient(x, y, w, h, r0, g0, b0, a0, r0, g0, b0, a0, r1, g1, b1, a1, r1, g1, b1, a1)
      return
    }
    val segs = 6 // per corner
    val pts = (segs + 1) * 4
    ensureCapacity(pts * 3)
    val cx = x + w * 0.5f; val cy = y + h * 0.5f
    val invH = 1f / h
    val step = (Math.PI * 0.5 / segs).toFloat
    var prevX = 0f; var prevY = 0f
    var k = 0
    while (k <= pts) {
      val idx = k % pts
      val corner = idx / (segs + 1)
      val j = idx % (segs + 1)
      // Corners clockwise from top left, each swept a quarter turn (no tuple: this runs for
      // every name plate every frame)
      val ox = if (corner == 0 || corner == 3) x + rad else x + w - rad
      val oy = if (corner < 2) y + rad else y + h - rad
      val base = (corner: @scala.annotation.switch) match {
        case 0 => Math.PI.toFloat; case 1 => Math.PI.toFloat * 1.5f; case 2 => 0f; case _ => Math.PI.toFloat * 0.5f
      }
      val ang = base + step * j
      val px = ox + Math.cos(ang).toFloat * rad
      val py = oy + Math.sin(ang).toFloat * rad
      if (k > 0) {
        val tc = 0.5f
        val tp = (prevY - y) * invH; val tn = (py - y) * invH
        vertex(cx, cy, r0 + (r1 - r0) * tc, g0 + (g1 - g0) * tc, b0 + (b1 - b0) * tc, a0 + (a1 - a0) * tc)
        vertex(prevX, prevY, r0 + (r1 - r0) * tp, g0 + (g1 - g0) * tp, b0 + (b1 - b0) * tp, a0 + (a1 - a0) * tp)
        vertex(px, py, r0 + (r1 - r0) * tn, g0 + (g1 - g0) * tn, b0 + (b1 - b0) * tn, a0 + (a1 - a0) * tn)
      }
      prevX = px; prevY = py
      k += 1
    }
  }

  /** Stroke a rounded rectangle's outline as one band, mitred all the way round. */
  def strokeRoundedRect(x: Float, y: Float, w: Float, h: Float, radius: Float, lineWidth: Float,
                        r: Float, g: Float, b: Float, a: Float): Unit = {
    val rad = Math.min(radius, Math.min(w, h) * 0.5f)
    if (rad < 1f) { strokeRect(x, y, w, h, lineWidth, r, g, b, a); return }
    val segs = 6
    val pts = (segs + 1) * 4
    if (_rrXs.length < pts) { _rrXs = new Array[Float](pts); _rrYs = new Array[Float](pts) }
    val step = (Math.PI * 0.5 / segs).toFloat
    var idx = 0
    while (idx < pts) {
      val corner = idx / (segs + 1)
      val j = idx % (segs + 1)
      val ox = if (corner == 0 || corner == 3) x + rad else x + w - rad
      val oy = if (corner < 2) y + rad else y + h - rad
      val base = (corner: @scala.annotation.switch) match {
        case 0 => Math.PI.toFloat; case 1 => Math.PI.toFloat * 1.5f; case 2 => 0f; case _ => Math.PI.toFloat * 0.5f
      }
      val ang = base + step * j
      _rrXs(idx) = ox + Math.cos(ang).toFloat * rad
      _rrYs(idx) = oy + Math.sin(ang).toFloat * rad
      idx += 1
    }
    polyStroke(_rrXs, _rrYs, pts, closed = true, lineWidth, lineWidth, r, g, b, a, a)
  }
  private var _rrXs = new Array[Float](28)
  private var _rrYs = new Array[Float](28)

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
  def fillOval(cx: Float, cy: Float, rx: Float, ry: Float, r: Float, g: Float, b: Float, a: Float, segments0: Int = 20): Unit = {
    val segments = curveSegments(segments0, rx, ry)
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
  def fillOvalSoft(cx: Float, cy: Float, rx: Float, ry: Float, r: Float, g: Float, b: Float, centerA: Float, edgeA: Float, segments0: Int = 20): Unit = {
    // An edge that fades to nothing hides its facets; one that stays visible doesn't
    val segments = if (edgeA > 0.05f) curveSegments(segments0, rx, ry) else softSegments(segments0, rx, ry)
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
    if (ShapeBatch.POLY_CHECK) ShapeBatch.checkConvex(xs, ys, n)
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

  /**
   * Fill a polygon as a fan from an explicit centre: `n` triangles (centre, vᵢ, vᵢ₊₁),
   * closing back to v₀. Correct for any outline that is star-shaped about that centre —
   * anything built as a radius per vertex, so throwing stars, sunbursts and faceted
   * hulls. `fillPolygon` cannot draw these: fanning from vertex 0 bridges the notches.
   */
  def fillFan(cx: Float, cy: Float, xs: Array[Float], ys: Array[Float], n: Int,
              r: Float, g: Float, b: Float, a: Float): Unit = {
    if (n < 3) return
    ensureCapacity(n * 3)
    var i = 0
    while (i < n) {
      val j = if (i == n - 1) 0 else i + 1
      vertex(cx, cy, r, g, b, a)
      vertex(xs(i), ys(i), r, g, b, a)
      vertex(xs(j), ys(j), r, g, b, a)
      i += 1
    }
  }

  /**
   * Fill a band given as an outer run followed by the inner run reversed — the layout the
   * crescent renderers build, where vertex i pairs with vertex n-1-i. One quad per
   * segment, so the concave side stays open and the inner and outer edges may have
   * different radii or squash. `fillPolygon` fans a crescent into a filled disc, which
   * turns a blade into a slab.
   */
  def fillRibbon(xs: Array[Float], ys: Array[Float], n: Int,
                 r: Float, g: Float, b: Float, a: Float): Unit = {
    if (n < 4) return
    val half = n / 2
    ensureCapacity((half - 1) * 6)
    var i = 0
    while (i < half - 1) {
      val oa = i;         val ob = i + 1
      val ia = n - 1 - i; val ib = n - 2 - i
      vertex(xs(oa), ys(oa), r, g, b, a)
      vertex(xs(ob), ys(ob), r, g, b, a)
      vertex(xs(ib), ys(ib), r, g, b, a)
      vertex(xs(oa), ys(oa), r, g, b, a)
      vertex(xs(ib), ys(ib), r, g, b, a)
      vertex(xs(ia), ys(ia), r, g, b, a)
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

  /**
   * Stroke an oval outline: one band between the ellipses half a line width inside and outside it.
   * Neighbouring segments share their edges. Drawn as a separate quad per segment, as it used to
   * be, a translucent ring double-blended a bead into every joint and left a notch inside each:
   * rings, auras and shields all came out beaded, and the wave crescents' rims dashed.
   */
  def strokeOval(cx: Float, cy: Float, rx: Float, ry: Float, lineWidth: Float,
                 r: Float, g: Float, b: Float, a: Float, segments0: Int = 20): Unit = {
    val n = curveSegments(segments0, rx, ry)
    val hw = lineWidth * 0.5f
    val ixr = Math.max(0f, rx - hw); val iyr = Math.max(0f, ry - hw)
    val oxr = rx + hw; val oyr = ry + hw
    ensureCapacity(n * 6)
    var i = 0
    while (i < n) {
      val c0 = ShapeBatch.getCos(n, i); val s0 = ShapeBatch.getSin(n, i)
      val c1 = ShapeBatch.getCos(n, i + 1); val s1 = ShapeBatch.getSin(n, i + 1)
      bandQuad(cx + ixr * c0, cy + iyr * s0, cx + oxr * c0, cy + oyr * s0,
        cx + ixr * c1, cy + iyr * s1, cx + oxr * c1, cy + oyr * s1, r, g, b, a, a)
      i += 1
    }
  }

  /** Stroke a partial oval outline. startAngle/sweep in radians, CCW. One band, as [[strokeOval]]. */
  def strokeArc(cx: Float, cy: Float, rx: Float, ry: Float,
                startAngle: Float, sweep: Float, lineWidth: Float,
                r: Float, g: Float, b: Float, a: Float, segments: Int = 12): Unit = {
    if (segments < 1) return
    val hw = lineWidth * 0.5f
    val ixr = Math.max(0f, rx - hw); val iyr = Math.max(0f, ry - hw)
    val oxr = rx + hw; val oyr = ry + hw
    ensureCapacity(segments * 6)
    val step = sweep / segments
    val cs = Math.cos(step).toFloat; val ss = Math.sin(step).toFloat
    var c0 = Math.cos(startAngle).toFloat; var s0 = Math.sin(startAngle).toFloat
    var i = 0
    while (i < segments) {
      val c1 = c0 * cs - s0 * ss; val s1 = s0 * cs + c0 * ss
      bandQuad(cx + ixr * c0, cy + iyr * s0, cx + oxr * c0, cy + oyr * s0,
        cx + ixr * c1, cy + iyr * s1, cx + oxr * c1, cy + oyr * s1, r, g, b, a, a)
      c0 = c1; s0 = s1
      i += 1
    }
  }

  /** Two triangles across a band segment: inner/outer at its start, inner/outer at its end. */
  @inline private def bandQuad(ix0: Float, iy0: Float, ox0: Float, oy0: Float,
                               ix1: Float, iy1: Float, ox1: Float, oy1: Float,
                               r: Float, g: Float, b: Float, a0: Float, a1: Float): Unit = {
    vertex(ix0, iy0, r, g, b, a0); vertex(ox0, oy0, r, g, b, a0); vertex(ox1, oy1, r, g, b, a1)
    vertex(ix0, iy0, r, g, b, a0); vertex(ox1, oy1, r, g, b, a1); vertex(ix1, iy1, r, g, b, a1)
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
    // One sin/cos for the whole band: each boundary is the last one turned by `step`
    val cs = Math.cos(step).toFloat; val ss = Math.sin(step).toFloat
    var c0 = Math.cos(startAngle).toFloat; var s0 = Math.sin(startAngle).toFloat
    var i = 0
    while (i < segments) {
      val al0 = aStart + (aEnd - aStart) * (i * invSegs)
      val al1 = aStart + (aEnd - aStart) * ((i + 1) * invSegs)
      val c1 = c0 * cs - s0 * ss; val s1 = s0 * cs + c0 * ss
      val ix0 = cx + rxIn * c0;  val iy0 = cy + ryIn * s0
      val ox0 = cx + rxOut * c0; val oy0 = cy + ryOut * s0
      val ix1 = cx + rxIn * c1;  val iy1 = cy + ryIn * s1
      val ox1 = cx + rxOut * c1; val oy1 = cy + ryOut * s1
      vertex(ix0, iy0, r, g, b, al0); vertex(ox0, oy0, r, g, b, al0); vertex(ox1, oy1, r, g, b, al1)
      vertex(ix0, iy0, r, g, b, al0); vertex(ox1, oy1, r, g, b, al1); vertex(ix1, iy1, r, g, b, al1)
      c0 = c1; s0 = s1
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

  /** Stroke a closed polygon outline using the first `n` elements, mitred at every corner so the
    * sides meet without overlapping (see [[strokeOval]] for why that matters). */
  def strokePolygon(xs: Array[Float], ys: Array[Float], n: Int, lineWidth: Float,
                    r: Float, g: Float, b: Float, a: Float): Unit = {
    if (n < 2) return
    polyStroke(xs, ys, n, closed = true, lineWidth, lineWidth, r, g, b, a, a)
  }

  /** Stroke an open polyline through the first `n` points, mitred at every bend. */
  def strokePolyline(xs: Array[Float], ys: Array[Float], n: Int, lineWidth: Float,
                     r: Float, g: Float, b: Float, a: Float): Unit = {
    if (n < 2) return
    polyStroke(xs, ys, n, closed = false, lineWidth, lineWidth, r, g, b, a, a)
  }

  /** An open polyline whose width and alpha run evenly from (w0, a0) at its first point to
    * (w1, a1) at its last — a bolt or a trail that tapers and fades along its length in one
    * piece, instead of a chain of quads overlapping at every bend. */
  def strokePolylineTapered(xs: Array[Float], ys: Array[Float], n: Int, w0: Float, w1: Float,
                            r: Float, g: Float, b: Float, a0: Float, a1: Float): Unit = {
    if (n < 2) return
    polyStroke(xs, ys, n, closed = false, w0, w1, r, g, b, a0, a1)
  }

  // Per-point mitre offsets for polyStroke, grown as needed
  private var _mitX = new Array[Float](32)
  private var _mitY = new Array[Float](32)

  private def polyStroke(xs: Array[Float], ys: Array[Float], n: Int, closed: Boolean,
                         w0: Float, w1: Float, r: Float, g: Float, b: Float, a0: Float, a1: Float): Unit = {
    if (_mitX.length < n) { _mitX = new Array[Float](n * 2); _mitY = new Array[Float](n * 2) }
    val last = n - 1
    val invLast = 1f / last
    var i = 0
    while (i < n) {
      // Unit normals of the edge coming in and the edge going out; an end of an open line, or a
      // zero-length edge, borrows the other one
      var inX = 0f; var inY = 0f; var outX = 0f; var outY = 0f
      var hasIn = false; var hasOut = false
      if (closed || i < last) {
        val j = if (i == last) 0 else i + 1
        val dx = xs(j) - xs(i); val dy = ys(j) - ys(i)
        val len = Math.sqrt(dx * dx + dy * dy).toFloat
        if (len > 1e-4f) { outX = -dy / len; outY = dx / len; hasOut = true }
      }
      if (closed || i > 0) {
        val p = if (i == 0) last else i - 1
        val dx = xs(i) - xs(p); val dy = ys(i) - ys(p)
        val len = Math.sqrt(dx * dx + dy * dy).toFloat
        if (len > 1e-4f) { inX = -dy / len; inY = dx / len; hasIn = true }
      }
      if (!hasIn) { inX = outX; inY = outY }
      if (!hasOut) { outX = inX; outY = inY }
      val hw = 0.5f * (if (closed) w0 else w0 + (w1 - w0) * i * invLast)
      var mx = inX + outX; var my = inY + outY
      val ml = Math.sqrt(mx * mx + my * my).toFloat
      if (ml < 1e-4f) { mx = outX; my = outY } // a full turn back: square it off
      else {
        mx /= ml; my /= ml
        val cosHalf = mx * outX + my * outY
        val k = if (cosHalf > 1f / ShapeBatch.MITER_LIMIT) 1f / cosHalf else ShapeBatch.MITER_LIMIT
        mx *= k; my *= k
      }
      _mitX(i) = mx * hw; _mitY(i) = my * hw
      i += 1
    }
    val segs = if (closed) n else last
    ensureCapacity(segs * 6)
    i = 0
    while (i < segs) {
      val j = if (i == last) 0 else i + 1
      val ai = if (closed) a0 else a0 + (a1 - a0) * i * invLast
      val aj = if (closed) a0 else a0 + (a1 - a0) * j * invLast
      bandQuad(xs(i) - _mitX(i), ys(i) - _mitY(i), xs(i) + _mitX(i), ys(i) + _mitY(i),
        xs(j) - _mitX(j), ys(j) - _mitY(j), xs(j) + _mitX(j), ys(j) + _mitY(j), r, g, b, ai, aj)
      i += 1
    }
  }

  /** Stroke a rectangle outline: a frame of four rects that meet at the corners without
    * overlapping, so a translucent border doesn't darken its corners. */
  def strokeRect(x: Float, y: Float, w: Float, h: Float, lineWidth: Float,
                 r: Float, g: Float, b: Float, a: Float): Unit = {
    val hw = lineWidth * 0.5f
    fillRect(x - hw, y - hw, w + lineWidth, lineWidth, r, g, b, a)
    fillRect(x - hw, y + h - hw, w + lineWidth, lineWidth, r, g, b, a)
    if (h > lineWidth) {
      fillRect(x - hw, y + hw, lineWidth, h - lineWidth, r, g, b, a)
      fillRect(x + w - hw, y + hw, lineWidth, h - lineWidth, r, g, b, a)
    }
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
