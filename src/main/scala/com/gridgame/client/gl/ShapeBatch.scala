package com.gridgame.client.gl

import org.lwjgl.opengl.GL11._
import org.lwjgl.opengl.GL15._
import org.lwjgl.opengl.GL20._
import org.lwjgl.opengl.GL30._
import org.lwjgl.opengl.GL32.glDrawElementsBaseVertex
import org.lwjgl.BufferUtils
import org.lwjgl.system.MemoryUtil

import java.nio.{FloatBuffer, ShortBuffer}

/**
 * Batched renderer for colored 2D primitives: filled rectangles, ovals, polygons, and lines.
 * Vertices are position (vec2) + color (vec4) = 6 floats per vertex, drawn as indexed triangles:
 * a fan shares its centre and a band its edges (see *A shape's vertices are shared* in
 * gl/CLAUDE.md). Supports standard alpha blending and additive blending for glow effects.
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

  /** Segments in each quarter turn of a rounded rectangle's corner. */
  private val ROUND_SEGS = 6

  // A rounded rectangle's corners, turned once rather than with a Math.cos and a Math.sin for every
  // point of every name plate and health bar every frame: each the same float the per-point
  // arithmetic gave, so every corner comes out where it did. fillRoundedRect's (by corner: top left,
  // top right, bottom right, bottom left, each a quarter turn from its start in ROUND_SEGS float
  // steps), and the outline's that fillRoundedRectGradient and strokeRoundedRect walk clockwise from
  // the top left (another step, worked out in double)
  private val cornerCos = Array.ofDim[Float](4, ROUND_SEGS + 1)
  private val cornerSin = Array.ofDim[Float](4, ROUND_SEGS + 1)
  private val outlineCos = new Array[Float]((ROUND_SEGS + 1) * 4)
  private val outlineSin = new Array[Float]((ROUND_SEGS + 1) * 4)
  locally {
    val starts = Array(Math.PI.toFloat, Math.PI.toFloat * 1.5f, 0f, Math.PI.toFloat * 0.5f)
    val cornerStep = (Math.PI.toFloat * 0.5f) / ROUND_SEGS
    val outlineStep = (Math.PI * 0.5 / ROUND_SEGS).toFloat
    var corner = 0
    while (corner < 4) {
      var j = 0
      while (j <= ROUND_SEGS) {
        val a: Float = starts(corner) + j * cornerStep
        cornerCos(corner)(j) = Math.cos(a).toFloat
        cornerSin(corner)(j) = Math.sin(a).toFloat
        val ang: Float = starts(corner) + outlineStep * j
        outlineCos(corner * (ROUND_SEGS + 1) + j) = Math.cos(ang).toFloat
        outlineSin(corner * (ROUND_SEGS + 1) + j) = Math.sin(ang).toFloat
        j += 1
      }
      corner += 1
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

  /** The table of cosines (sines) for an oval of `segments` segments, n + 1 of them, or null when it
    * has none (getCos works those out one at a time): for a loop round an oval to look up once. */
  @inline private[gl] def cosTable(segments: Int): Array[Float] =
    if (segments >= 0 && segments <= MAX_SEGMENTS) cosLUT(segments) else null
  @inline private[gl] def sinTable(segments: Int): Array[Float] =
    if (segments >= 0 && segments <= MAX_SEGMENTS) sinLUT(segments) else null

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
  // A flush's indices are 16 bits, counted from its first vertex (glDrawElementsBaseVertex), so
  // it holds at most this many vertices
  private val MAX_CAPACITY = 65536
  private var capacity = INITIAL_CAPACITY
  // Staging memory, written through its raw address: a FloatBuffer.put per float (a limit
  // check and a position store each, six per vertex) was the single largest cost of
  // building a busy frame.
  private var buffer: FloatBuffer = BufferUtils.createFloatBuffer(capacity * VERTEX_SIZE)
  private var bufferAddr: Long = MemoryUtil.memAddress(buffer)
  private var vertexCount = 0
  // Three indices a triangle, into this flush's vertices. A fan of n triangles is n + 2 vertices
  // and a band of n quads 2n + 2, against 3n and 6n drawn as plain triangles, so a flush has room
  // for about three indices a vertex.
  private var indexCapacity = INITIAL_CAPACITY * 3
  private var indices: ShortBuffer = BufferUtils.createShortBuffer(indexCapacity)
  private var indexAddr: Long = MemoryUtil.memAddress(indices)
  private var indexCount = 0
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

  // GPU ring buffers, one for the vertices and one for the indices. Each flush appends at a fresh
  // offset in both and draws from there, so no upload ever lands on bytes the GPU may still be
  // reading — overwriting a range that is still in flight is what makes the driver stall (a
  // single 330-vertex flush was measured at 1.7ms). When a ring wraps we orphan its whole store,
  // which hands us fresh storage instead of waiting on the old one.
  private var ringVerts = INITIAL_CAPACITY * ShapeBatch.RING_FRAMES
  private var ringOffset = 0
  private var ringIndices = indexCapacity * ShapeBatch.RING_FRAMES
  private var ringIndexOffset = 0

  private val vao = glGenVertexArrays()
  private val vbo = glGenBuffers()
  private val ibo = glGenBuffers()

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
  // The index ring is the VAO's own (the element buffer binding is VAO state)
  glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ibo)
  glBufferData(GL_ELEMENT_ARRAY_BUFFER, ringIndices * 2L, GL_STREAM_DRAW)
  glBindVertexArray(0)

  def begin(projection: FloatBuffer): Unit = {
    if (drawing) throw new IllegalStateException("Already drawing")
    drawing = true
    vertexCount = 0
    indexCount = 0
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

  /** A fast rect for sub-4px elements where oval detail is imperceptible. */
  @inline def fillDot(cx: Float, cy: Float, r: Float, cr: Float, cg: Float, cb: Float, ca: Float): Unit = {
    fillRect(cx - r, cy - r, r * 2, r * 2, cr, cg, cb, ca)
  }

  /** Fill a rounded rectangle with uniform color. Corner radius is clamped to half the smallest dimension. */
  def fillRoundedRect(x: Float, y: Float, w: Float, h: Float, radius: Float, r: Float, g: Float, b: Float, a: Float): Unit = {
    val rad = Math.min(radius, Math.min(w, h) * 0.5f)
    if (rad < 1f) { fillRect(x, y, w, h, r, g, b, a); return }
    // Center cross (two rects)
    fillRect(x + rad, y, w - rad * 2, h, r, g, b, a)
    fillRect(x, y + rad, rad, h - rad * 2, r, g, b, a)
    fillRect(x + w - rad, y + rad, rad, h - rad * 2, r, g, b, a)
    // Four corners as triangle fans
    roundedCorner(x + rad, y + rad, rad, 0, r, g, b, a)         // TL
    roundedCorner(x + w - rad, y + rad, rad, 1, r, g, b, a)     // TR
    roundedCorner(x + w - rad, y + h - rad, rad, 2, r, g, b, a) // BR
    roundedCorner(x + rad, y + h - rad, rad, 3, r, g, b, a)     // BL
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
    val segs = ShapeBatch.ROUND_SEGS // per corner
    val pts = (segs + 1) * 4
    ensureCapacity(pts + 1, pts * 3)
    val cx = x + w * 0.5f; val cy = y + h * 0.5f
    val invH = 1f / h
    val c = vertexCount
    val tc = 0.5f
    vertex(cx, cy, r0 + (r1 - r0) * tc, g0 + (g1 - g0) * tc, b0 + (b1 - b0) * tc, a0 + (a1 - a0) * tc)
    // Corners clockwise from top left, each swept a quarter turn, each point coloured by its height
    var idx = 0
    while (idx < pts) {
      val corner = idx / (segs + 1)
      val ox = if (corner == 0 || corner == 3) x + rad else x + w - rad
      val oy = if (corner < 2) y + rad else y + h - rad
      val px = ox + ShapeBatch.outlineCos(idx) * rad
      val py = oy + ShapeBatch.outlineSin(idx) * rad
      val t = (py - y) * invH
      vertex(px, py, r0 + (r1 - r0) * t, g0 + (g1 - g0) * t, b0 + (b1 - b0) * t, a0 + (a1 - a0) * t)
      idx += 1
    }
    var k = 1
    while (k <= pts) {
      tri(c, c + k, c + 1 + k % pts)
      k += 1
    }
  }

  /** Stroke a rounded rectangle's outline as one band, mitred all the way round. */
  def strokeRoundedRect(x: Float, y: Float, w: Float, h: Float, radius: Float, lineWidth: Float,
                        r: Float, g: Float, b: Float, a: Float): Unit = {
    val rad = Math.min(radius, Math.min(w, h) * 0.5f)
    if (rad < 1f) { strokeRect(x, y, w, h, lineWidth, r, g, b, a); return }
    val segs = ShapeBatch.ROUND_SEGS
    val pts = (segs + 1) * 4
    if (_rrXs.length < pts) { _rrXs = new Array[Float](pts); _rrYs = new Array[Float](pts) }
    var idx = 0
    while (idx < pts) {
      val corner = idx / (segs + 1)
      val ox = if (corner == 0 || corner == 3) x + rad else x + w - rad
      val oy = if (corner < 2) y + rad else y + h - rad
      _rrXs(idx) = ox + ShapeBatch.outlineCos(idx) * rad
      _rrYs(idx) = oy + ShapeBatch.outlineSin(idx) * rad
      idx += 1
    }
    polyStroke(_rrXs, _rrYs, pts, closed = true, lineWidth, lineWidth, r, g, b, a, a)
  }
  private var _rrXs = new Array[Float](28)
  private var _rrYs = new Array[Float](28)

  /** One corner of [[fillRoundedRect]] (0 top left, 1 top right, 2 bottom right, 3 bottom left),
    * fanned from its centre. */
  private def roundedCorner(cx: Float, cy: Float, radius: Float, corner: Int,
                            r: Float, g: Float, b: Float, a: Float): Unit = {
    val segs = ShapeBatch.ROUND_SEGS
    val cos = ShapeBatch.cornerCos(corner); val sin = ShapeBatch.cornerSin(corner)
    ensureCapacity(segs + 2, segs * 3)
    val c = vertexCount
    vertex(cx, cy, r, g, b, a)
    var i = 0
    while (i <= segs) {
      vertex(cx + cos(i) * radius, cy + sin(i) * radius, r, g, b, a)
      i += 1
    }
    i = 0
    while (i < segs) {
      tri(c, c + 1 + i, c + 2 + i)
      i += 1
    }
  }

  def fillRect(x: Float, y: Float, w: Float, h: Float, r: Float, g: Float, b: Float, a: Float): Unit = {
    ensureCapacity(4, 6)
    // Two triangles: TL, TR, BR and TL, BR, BL
    val v = vertexCount
    vertex(x, y, r, g, b, a)
    vertex(x + w, y, r, g, b, a)
    vertex(x + w, y + h, r, g, b, a)
    vertex(x, y + h, r, g, b, a)
    quadTris(v, v + 1, v + 2, v + 3)
  }

  /** Fill a rectangle with per-corner colors for smooth gradients. Order: TL, TR, BR, BL. */
  def fillRectGradient(x: Float, y: Float, w: Float, h: Float,
                       r0: Float, g0: Float, b0: Float, a0: Float,
                       r1: Float, g1: Float, b1: Float, a1: Float,
                       r2: Float, g2: Float, b2: Float, a2: Float,
                       r3: Float, g3: Float, b3: Float, a3: Float): Unit = {
    ensureCapacity(4, 6)
    val v = vertexCount
    vertex(x, y, r0, g0, b0, a0)         // TL
    vertex(x + w, y, r1, g1, b1, a1)     // TR
    vertex(x + w, y + h, r2, g2, b2, a2) // BR
    vertex(x, y + h, r3, g3, b3, a3)     // BL
    quadTris(v, v + 1, v + 2, v + 3)
  }

  /** Approximate an oval as a triangle fan. segments = 16 for small, 32 for large. */
  def fillOval(cx: Float, cy: Float, rx: Float, ry: Float, r: Float, g: Float, b: Float, a: Float, segments0: Int = 20): Unit = {
    val segments = curveSegments(segments0, rx, ry)
    ensureCapacity(segments + 2, segments * 3)
    val cos = ShapeBatch.cosTable(segments); val sin = ShapeBatch.sinTable(segments)
    val c = vertexCount
    vertex(cx, cy, r, g, b, a)
    var i = 0
    while (i <= segments) {
      val co = if (cos != null) cos(i) else ShapeBatch.getCos(segments, i)
      val si = if (sin != null) sin(i) else ShapeBatch.getSin(segments, i)
      vertex(cx + rx * co, cy + ry * si, r, g, b, a)
      i += 1
    }
    fanTris(c, segments)
  }

  /** Fill an oval with edge alpha falloff for soft glow effect. */
  def fillOvalSoft(cx: Float, cy: Float, rx: Float, ry: Float, r: Float, g: Float, b: Float, centerA: Float, edgeA: Float, segments0: Int = 20): Unit = {
    // An edge that fades to nothing hides its facets; one that stays visible doesn't
    val segments = if (edgeA > 0.05f) curveSegments(segments0, rx, ry) else softSegments(segments0, rx, ry)
    ensureCapacity(segments + 2, segments * 3)
    val cos = ShapeBatch.cosTable(segments); val sin = ShapeBatch.sinTable(segments)
    val c = vertexCount
    vertex(cx, cy, r, g, b, centerA)
    var i = 0
    while (i <= segments) {
      val co = if (cos != null) cos(i) else ShapeBatch.getCos(segments, i)
      val si = if (sin != null) sin(i) else ShapeBatch.getSin(segments, i)
      vertex(cx + rx * co, cy + ry * si, r, g, b, edgeA)
      i += 1
    }
    fanTris(c, segments)
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
    ensureCapacity(n, (n - 2) * 3)
    val v = vertexCount
    var i = 0
    while (i < n) {
      vertex(xs(i), ys(i), r, g, b, a)
      i += 1
    }
    i = 1
    while (i < n - 1) {
      tri(v, v + i, v + i + 1)
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
    ensureCapacity(n + 1, n * 3)
    val c = vertexCount
    vertex(cx, cy, r, g, b, a)
    var i = 0
    while (i < n) {
      vertex(xs(i), ys(i), r, g, b, a)
      i += 1
    }
    i = 0
    while (i < n) {
      val j = if (i == n - 1) 0 else i + 1
      tri(c, c + 1 + i, c + 1 + j)
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
    ensureCapacity(n, (half - 1) * 6)
    val v = vertexCount
    var k = 0
    while (k < n) {
      vertex(xs(k), ys(k), r, g, b, a)
      k += 1
    }
    var i = 0
    while (i < half - 1) {
      val oa = v + i;         val ob = v + i + 1
      val ia = v + n - 1 - i; val ib = v + n - 2 - i
      quadTris(oa, ob, ib, ia)
      i += 1
    }
  }

  /** Draw a line as a thin quad with optional width. */
  def strokeLine(x1: Float, y1: Float, x2: Float, y2: Float, lineWidth: Float, r: Float, g: Float, b: Float, a: Float): Unit = {
    ensureCapacity(4, 6)
    val dx = x2 - x1
    val dy = y2 - y1
    val len = Math.sqrt(dx * dx + dy * dy).toFloat
    if (len < 0.001f) return
    val hw = lineWidth * 0.5f
    // Normal perpendicular to line direction
    val nx = -dy / len * hw
    val ny = dx / len * hw

    val v = vertexCount
    vertex(x1 + nx, y1 + ny, r, g, b, a)
    vertex(x1 - nx, y1 - ny, r, g, b, a)
    vertex(x2 - nx, y2 - ny, r, g, b, a)
    vertex(x2 + nx, y2 + ny, r, g, b, a)
    quadTris(v, v + 1, v + 2, v + 3)
  }

  /** Draw a line with alpha falloff on edges for anti-aliased appearance. */
  def strokeLineSoft(x1: Float, y1: Float, x2: Float, y2: Float, lineWidth: Float,
                     r: Float, g: Float, b: Float, a: Float): Unit = {
    ensureCapacity(8, 18) // 6 triangles for 3-strip soft line
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

    val v = vertexCount
    vertex(x1 + ox1, y1 + oy1, r, g, b, 0f) // v:     left outer, start
    vertex(x1 + cx1, y1 + cy1, r, g, b, a)  // v + 1: left core, start
    vertex(x2 + cx1, y2 + cy1, r, g, b, a)  // v + 2: left core, end
    vertex(x2 + ox1, y2 + oy1, r, g, b, 0f) // v + 3: left outer, end
    vertex(x1 - cx1, y1 - cy1, r, g, b, a)  // v + 4: right core, start
    vertex(x2 - cx1, y2 - cy1, r, g, b, a)  // v + 5: right core, end
    vertex(x1 - ox1, y1 - oy1, r, g, b, 0f) // v + 6: right outer, start
    vertex(x2 - ox1, y2 - oy1, r, g, b, 0f) // v + 7: right outer, end
    // Left outer to left core
    quadTris(v, v + 1, v + 2, v + 3)
    // Left core to right core (opaque center)
    quadTris(v + 1, v + 4, v + 5, v + 2)
    // Right core to right outer
    quadTris(v + 4, v + 6, v + 7, v + 5)
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
    ensureCapacity(2 * (n + 1), n * 6)
    val cos = ShapeBatch.cosTable(n); val sin = ShapeBatch.sinTable(n)
    val v = vertexCount
    var i = 0
    while (i <= n) {
      val c = if (cos != null) cos(i) else ShapeBatch.getCos(n, i)
      val s = if (sin != null) sin(i) else ShapeBatch.getSin(n, i)
      vertex(cx + ixr * c, cy + iyr * s, r, g, b, a)
      vertex(cx + oxr * c, cy + oyr * s, r, g, b, a)
      i += 1
    }
    bandTris(v, n)
  }

  /** Stroke a partial oval outline. startAngle/sweep in radians, CCW. One band, as [[strokeOval]]. */
  def strokeArc(cx: Float, cy: Float, rx: Float, ry: Float,
                startAngle: Float, sweep: Float, lineWidth: Float,
                r: Float, g: Float, b: Float, a: Float, segments: Int = 12): Unit = {
    if (segments < 1) return
    val hw = lineWidth * 0.5f
    val ixr = Math.max(0f, rx - hw); val iyr = Math.max(0f, ry - hw)
    val oxr = rx + hw; val oyr = ry + hw
    ensureCapacity(2 * (segments + 1), segments * 6)
    val step = sweep / segments
    val cs = Math.cos(step).toFloat; val ss = Math.sin(step).toFloat
    var c0 = Math.cos(startAngle).toFloat; var s0 = Math.sin(startAngle).toFloat
    val v = vertexCount
    var i = 0
    while (i <= segments) {
      vertex(cx + ixr * c0, cy + iyr * s0, r, g, b, a)
      vertex(cx + oxr * c0, cy + oyr * s0, r, g, b, a)
      val c1 = c0 * cs - s0 * ss; val s1 = s0 * cs + c0 * ss
      c0 = c1; s0 = s1
      i += 1
    }
    bandTris(v, segments)
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
    ensureCapacity(2 * (segments + 1), segments * 6)
    val step = sweep / segments
    val invSegs = 1f / segments
    // One sin/cos for the whole band: each boundary is the last one turned by `step`
    val cs = Math.cos(step).toFloat; val ss = Math.sin(step).toFloat
    var c0 = Math.cos(startAngle).toFloat; var s0 = Math.sin(startAngle).toFloat
    val v = vertexCount
    var i = 0
    while (i <= segments) {
      val al = aStart + (aEnd - aStart) * (i * invSegs)
      vertex(cx + rxIn * c0, cy + ryIn * s0, r, g, b, al)
      vertex(cx + rxOut * c0, cy + ryOut * s0, r, g, b, al)
      val c1 = c0 * cs - s0 * ss; val s1 = s0 * cs + c0 * ss
      c0 = c1; s0 = s1
      i += 1
    }
    bandTris(v, segments)
  }

  /**
   * Four-point star flare: two crossed tapered spikes with a hot core. `angle` rotates the
   * long axis, `ratio` is the short spike length relative to the long one.
   */
  def fillStarFlare(cx: Float, cy: Float, len: Float, thickness: Float, angle: Float, ratio: Float,
                    r: Float, g: Float, b: Float, a: Float): Unit = {
    ensureCapacity(12, 12)
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
    val v = vertexCount
    vertex(cx + px, cy + py, r, g, b, a)
    vertex(cx - px, cy - py, r, g, b, a)
    vertex(cx + dx * len, cy + dy * len, r, g, b, 0f)
    tri(v, v + 1, v + 2)
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

  /** An open polyline with its own width `ws(i)` and alpha `as(i)` at every point — a limb
    * that swells and narrows, a rope that fades in from the hand that holds it — still one
    * mitred band. A linear taper can only describe a cone. */
  def strokePolylineVar(xs: Array[Float], ys: Array[Float], ws: Array[Float], as: Array[Float], n: Int,
                        r: Float, g: Float, b: Float): Unit = {
    if (n < 2) return
    polyStroke(xs, ys, n, closed = false, 0f, 0f, r, g, b, 0f, 0f, ws, as)
  }

  // Per-point mitre offsets for polyStroke, grown as needed
  private var _mitX = new Array[Float](32)
  private var _mitY = new Array[Float](32)

  // Per-edge unit normals for polyStroke and smoothMitres, and whether the edge is long enough to
  // have one, grown as needed
  private var _edgeX = new Array[Float](32)
  private var _edgeY = new Array[Float](32)
  private var _edgeOk = new Array[Boolean](32)

  /** The unit normal (−dy, dx) / length of each edge between the first `n` points into _edgeX/_edgeY,
    * edge e from point e to the next (and, closed, from the last back to the first); one shorter than
    * 1e-4 has none. Each is worked out once and read by the points at both its ends, which each
    * worked it out for themselves, the same way, to the same floats. */
  private def edgeNormals(xs: Array[Float], ys: Array[Float], n: Int, closed: Boolean): Unit = {
    if (_edgeX.length < n) { _edgeX = new Array[Float](n * 2); _edgeY = new Array[Float](n * 2); _edgeOk = new Array[Boolean](n * 2) }
    val last = n - 1
    val edges = if (closed) n else last
    var e = 0
    while (e < edges) {
      val j = if (e == last) 0 else e + 1
      val dx = xs(j) - xs(e); val dy = ys(j) - ys(e)
      val len = Math.sqrt(dx * dx + dy * dy).toFloat
      if (len > 1e-4f) { _edgeX(e) = -dy / len; _edgeY(e) = dx / len; _edgeOk(e) = true }
      else _edgeOk(e) = false
      e += 1
    }
  }

  private def polyStroke(xs: Array[Float], ys: Array[Float], n: Int, closed: Boolean,
                         w0: Float, w1: Float, r: Float, g: Float, b: Float, a0: Float, a1: Float,
                         ws: Array[Float] = null, as: Array[Float] = null): Unit = {
    if (_mitX.length < n) { _mitX = new Array[Float](n * 2); _mitY = new Array[Float](n * 2) }
    edgeNormals(xs, ys, n, closed)
    val last = n - 1
    val invLast = 1f / last
    var i = 0
    while (i < n) {
      // Unit normals of the edge coming in and the edge going out; an end of an open line, or a
      // zero-length edge, borrows the other one
      var inX = 0f; var inY = 0f; var outX = 0f; var outY = 0f
      var hasIn = false; var hasOut = false
      if ((closed || i < last) && _edgeOk(i)) { outX = _edgeX(i); outY = _edgeY(i); hasOut = true }
      if (closed || i > 0) {
        val p = if (i == 0) last else i - 1
        if (_edgeOk(p)) { inX = _edgeX(p); inY = _edgeY(p); hasIn = true }
      }
      if (!hasIn) { inX = outX; inY = outY }
      if (!hasOut) { outX = inX; outY = inY }
      val hw = 0.5f * (if (ws != null) ws(i) else if (closed) w0 else w0 + (w1 - w0) * i * invLast)
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
    ensureCapacity(2 * n, segs * 6)
    // Each point's two edges once, at its alpha: inner (along minus the mitre), then outer
    val v = vertexCount
    i = 0
    while (i < n) {
      val ai = if (as != null) as(i) else if (closed) a0 else a0 + (a1 - a0) * i * invLast
      vertex(xs(i) - _mitX(i), ys(i) - _mitY(i), r, g, b, ai)
      vertex(xs(i) + _mitX(i), ys(i) + _mitY(i), r, g, b, ai)
      i += 1
    }
    i = 0
    while (i < segs) {
      val j = if (i == last) 0 else i + 1
      quadTris(v + 2 * i, v + 2 * i + 1, v + 2 * j + 1, v + 2 * j)
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

  // ── Smooth-edged shapes ─────────────────────────────────────────────────
  // Every shape above has a hard edge, which a pixel either is inside of or isn't: a curve or a
  // slanted line comes out a staircase, at Low (under a pixel a unit) a coarse one. These fade
  // each edge out over one pixel of the target (pixelsPerUnit), centred on the edge, so a pixel the
  // edge half covers is half covered: a ring, a slanted rim or a disc on the ground reads as drawn
  // with a pen rather than cut out of the pixel grid (gl/CLAUDE.md, *Smooth edges*).

  /** How far a smooth edge fades, in units: one pixel of the target. */
  @inline private def featherWidth: Float = 1f / Math.max(0.25f, pixelsPerUnit)

  /** Segments for a smooth oval of these radii: no chord strays a quarter of a pixel from the curve.
    * A smooth edge shows a polygon's corners that a hard one's staircase hid: a dot drawn with
    * curveSegments' eight was an octagon. Past the sine table's 64 they are turned round step by
    * step ([[smoothOval]]). */
  @inline private def smoothSegments(rx: Float, ry: Float): Int = {
    val rPx = Math.max(Math.abs(rx), Math.abs(ry)) * pixelsPerUnit
    // A chord's sagitta is r pi^2 / 2n^2, a quarter of a pixel for n = pi sqrt(2r)
    Math.max(12, Math.min(MAX_SMOOTH_SEGMENTS, (Math.PI * Math.sqrt(2.0 * rPx)).toInt + 1))
  }
  private val MAX_SMOOTH_SEGMENTS = 256
  // The directions round a smooth oval, worked out once a shape (smoothOval)
  private val _ovC = new Array[Float](MAX_SMOOTH_SEGMENTS + 1)
  private val _ovS = new Array[Float](MAX_SMOOTH_SEGMENTS + 1)

  /** The n + 1 directions round a smooth oval of n segments into _ovC/_ovS, the last the first again
    * so it closes exactly. */
  private def smoothOval(n: Int): Unit = {
    val step = 2.0 * Math.PI / n
    val cs = Math.cos(step); val ss = Math.sin(step)
    var c = 1.0; var s = 0.0
    var i = 0
    while (i < n) {
      _ovC(i) = c.toFloat; _ovS(i) = s.toFloat
      val c1 = c * cs - s * ss
      s = s * cs + c * ss; c = c1
      i += 1
    }
    _ovC(n) = 1f; _ovS(n) = 0f
  }

  /** A filled oval whose edge fades out over a pixel. */
  def fillOvalSmooth(cx: Float, cy: Float, rx: Float, ry: Float, r: Float, g: Float, b: Float, a: Float): Unit = {
    val h = featherWidth * 0.5f
    val n = smoothSegments(rx + h, ry + h)
    smoothOval(n)
    val irx = Math.max(0f, rx - h); val iry = Math.max(0f, ry - h)
    val orx = rx + h; val ory = ry + h
    ensureCapacity(1 + 2 * (n + 1), n * 9)
    // The centre, then each direction's point on the inner oval (at a) and on the outer (at none)
    val c = vertexCount
    vertex(cx, cy, r, g, b, a)
    var i = 0
    while (i <= n) {
      vertex(cx + irx * _ovC(i), cy + iry * _ovS(i), r, g, b, a)
      vertex(cx + orx * _ovC(i), cy + ory * _ovS(i), r, g, b, 0f)
      i += 1
    }
    i = 0
    while (i < n) {
      val in0 = c + 1 + 2 * i
      tri(c, in0, in0 + 2)
      quadTris(in0, in0 + 1, in0 + 3, in0 + 2)
      i += 1
    }
  }

  /** An oval's outline `lineWidth` across whose edges fade out over a pixel, inside and out. A line
    * thinner than a pixel is drawn a pixel wide and as much fainter. */
  def strokeOvalSmooth(cx: Float, cy: Float, rx: Float, ry: Float, lineWidth: Float,
                       r: Float, g: Float, b: Float, a: Float): Unit = {
    val f = featherWidth
    val w = Math.max(lineWidth, f)
    val ae = a * Math.min(1f, lineWidth / f)
    val core = (w - f) * 0.5f
    val out = (w + f) * 0.5f
    val n = smoothSegments(rx + out, ry + out)
    smoothOval(n)
    // A band `core` either side of the ring at ae, fading to nothing `out` either side: four
    // ovals, from the inside out
    val ax0 = Math.max(0f, rx - out); val ay0 = Math.max(0f, ry - out)
    val bx0 = Math.max(0f, rx - core); val by0 = Math.max(0f, ry - core)
    val bx1 = rx + core; val by1 = ry + core
    val ax1 = rx + out; val ay1 = ry + out
    ensureCapacity(4 * (n + 1), n * 18)
    val v = vertexCount
    var i = 0
    while (i <= n) {
      val c = _ovC(i); val s = _ovS(i)
      vertex(cx + ax0 * c, cy + ay0 * s, r, g, b, 0f)
      vertex(cx + bx0 * c, cy + by0 * s, r, g, b, ae)
      vertex(cx + bx1 * c, cy + by1 * s, r, g, b, ae)
      vertex(cx + ax1 * c, cy + ay1 * s, r, g, b, 0f)
      i += 1
    }
    smoothBands(v, n, core > 0f)
  }

  // Per-point unit mitres for the smooth shapes (a normal's length is 1 / cos of half the turn
  // there), and their alphas, grown as needed
  private var _smX = new Array[Float](64)
  private var _smY = new Array[Float](64)

  /**
   * The unit mitre at each of the first `n` points of a path into _smX/_smY: pointing out of a
   * closed one (whichever way round it runs), and to the left of an open one (screen y down: the
   * side (−dy, dx) of its way). Joins sharper than MITER_LIMIT are cut back, as polyStroke's are.
   */
  private def smoothMitres(xs: Array[Float], ys: Array[Float], n: Int, closed: Boolean): Unit = {
    if (_smX.length < n) { _smX = new Array[Float](n * 2); _smY = new Array[Float](n * 2) }
    // A closed path's normals point out of it: the left of its way is inside it when it runs
    // with a positive area
    var side = 1f
    if (closed) {
      var area = 0f
      var i = 0
      while (i < n) {
        val j = if (i == n - 1) 0 else i + 1
        area += xs(i) * ys(j) - xs(j) * ys(i)
        i += 1
      }
      if (area > 0f) side = -1f
    }
    edgeNormals(xs, ys, n, closed)
    val last = n - 1
    var i = 0
    while (i < n) {
      var inX = 0f; var inY = 0f; var outX = 0f; var outY = 0f
      var hasIn = false; var hasOut = false
      if ((closed || i < last) && _edgeOk(i)) { outX = _edgeX(i); outY = _edgeY(i); hasOut = true }
      if (closed || i > 0) {
        val p = if (i == 0) last else i - 1
        if (_edgeOk(p)) { inX = _edgeX(p); inY = _edgeY(p); hasIn = true }
      }
      if (!hasIn) { inX = outX; inY = outY }
      if (!hasOut) { outX = inX; outY = inY }
      var mx = inX + outX; var my = inY + outY
      val ml = Math.sqrt(mx * mx + my * my).toFloat
      if (ml < 1e-4f) { mx = outX; my = outY }
      else {
        mx /= ml; my /= ml
        val cosHalf = mx * outX + my * outY
        val k = if (cosHalf > 1f / ShapeBatch.MITER_LIMIT) 1f / cosHalf else ShapeBatch.MITER_LIMIT
        mx *= k; my *= k
      }
      _smX(i) = mx * side; _smY(i) = my * side
      i += 1
    }
  }

  /**
   * A path through the first `n` points stroked `lineWidth` across, its edges fading out over a pixel
   * either side, mitred at every bend; closed back to its first point, or open. `as` is an alpha for
   * each point (null for none), times `a`; a line thinner than a pixel is drawn a pixel wide and as
   * much fainter. `offset` moves the line off the path: out of a closed one, to the left of an open
   * one — an outline just outside a rim, which a stroke on the rim itself would half cover.
   */
  def strokePathSmooth(xs: Array[Float], ys: Array[Float], as: Array[Float], n: Int, closed: Boolean,
                       lineWidth: Float, offset: Float, r: Float, g: Float, b: Float, a: Float): Unit = {
    if (n < 2) return
    smoothMitres(xs, ys, n, closed)
    val f = featherWidth
    val w = Math.max(lineWidth, f)
    val ae = a * Math.min(1f, lineWidth / f)
    val c0 = offset - (w - f) * 0.5f; val c1 = offset + (w - f) * 0.5f
    val o0 = offset - (w + f) * 0.5f; val o1 = offset + (w + f) * 0.5f
    val segs = if (closed) n else n - 1
    ensureCapacity(4 * n, segs * 18)
    // Each point's four offsets along its mitre once: the band's edges (at none) and its core's
    // (at the point's alpha)
    val v = vertexCount
    var i = 0
    while (i < n) {
      val ai = if (as != null) as(i) * ae else ae
      val xi = xs(i); val yi = ys(i); val mxi = _smX(i); val myi = _smY(i)
      vertex(xi + mxi * o0, yi + myi * o0, r, g, b, 0f)
      vertex(xi + mxi * c0, yi + myi * c0, r, g, b, ai)
      vertex(xi + mxi * c1, yi + myi * c1, r, g, b, ai)
      vertex(xi + mxi * o1, yi + myi * o1, r, g, b, 0f)
      i += 1
    }
    val core = c1 > c0
    i = 0
    while (i < segs) {
      val j = if (i == n - 1) 0 else i + 1
      smoothBand(v + 4 * i, v + 4 * j, core)
      i += 1
    }
  }

  /**
   * A band filled, laid out as [[fillRibbon]]'s is (an outer run of points, then the inner run
   * reversed, so point i pairs with point n-1-i across it), its outline fading out over a pixel. `as`
   * is an alpha for each point, times `a`, so a band can fade along its length: the fill is a quad
   * across it between each pair and the next, never a fan, which would smear one point's alpha over
   * the whole shape. Its outline is the closed path through all n points, which must not cross
   * itself.
   */
  def fillRibbonSmooth(xs: Array[Float], ys: Array[Float], as: Array[Float], n: Int,
                       r: Float, g: Float, b: Float, a: Float): Unit = {
    if (n < 4) return
    smoothMitres(xs, ys, n, closed = true)
    val h = featherWidth * 0.5f
    val half = n / 2
    ensureCapacity(2 * n, (half - 1) * 6 + n * 6)
    // Each point inside the outline by half the fade (at its alpha) and outside it by as much (at
    // none)
    val v = vertexCount
    var k = 0
    while (k < n) {
      vertex(xs(k) - _smX(k) * h, ys(k) - _smY(k) * h, r, g, b, as(k) * a)
      vertex(xs(k) + _smX(k) * h, ys(k) + _smY(k) * h, r, g, b, 0f)
      k += 1
    }
    // Across the band, inside its outline by half the fade
    var i = 0
    while (i < half - 1) {
      val oa = i; val ob = i + 1
      val ia = n - 1 - i; val ib = n - 2 - i
      quadTris(v + 2 * oa, v + 2 * ia, v + 2 * ib, v + 2 * ob)
      i += 1
    }
    // and round its outline, from there out to half the fade outside it
    i = 0
    while (i < n) {
      val j = if (i == n - 1) 0 else i + 1
      quadTris(v + 2 * i, v + 2 * i + 1, v + 2 * j + 1, v + 2 * j)
      i += 1
    }
  }

  // ── Triangles ──
  // Every shape writes each of its vertices once and then its triangles as indices into them,
  // vertex by vertex in the order the triangles had when each was three vertices of its own: the
  // same triangles, in the same order, from the same floats.

  /** One triangle, by its vertices' indices in this flush. */
  @inline private def tri(a: Int, b: Int, c: Int): Unit = {
    if (indexCount + 3 > indexCapacity) growIndices(indexCapacity * 2)
    val p = indexAddr + indexCount * 2L
    MemoryUtil.memPutShort(p, a.toShort)
    MemoryUtil.memPutShort(p + 2, b.toShort)
    MemoryUtil.memPutShort(p + 4, c.toShort)
    indexCount += 3
  }

  /** A quad from i0 across to o0 at one end and from i1 across to o1 at the other, as two
    * triangles: (i0, o0, o1) and (i0, o1, i1). */
  @inline private def quadTris(i0: Int, o0: Int, o1: Int, i1: Int): Unit = {
    tri(i0, o0, o1)
    tri(i0, o1, i1)
  }

  /** A fan of `n` triangles from centre `c` round the rim points after it, c + 1 to c + n + 1. */
  @inline private def fanTris(c: Int, n: Int): Unit = {
    var i = 0
    while (i < n) {
      tri(c, c + 1 + i, c + 2 + i)
      i += 1
    }
  }

  /** A band of `n` quads over the n + 1 inner/outer pairs from `v` (inner at v + 2k, outer after it). */
  @inline private def bandTris(v: Int, n: Int): Unit = {
    var i = 0
    while (i < n) {
      val i0 = v + 2 * i
      quadTris(i0, i0 + 1, i0 + 3, i0 + 2)
      i += 1
    }
  }

  /** A smooth stroke between two runs of four (its outer edge, its core's two edges, its other outer
    * edge) from `p` and `q`: fading in, the core if it has one, fading out. */
  @inline private def smoothBand(p: Int, q: Int, core: Boolean): Unit = {
    quadTris(p, p + 1, q + 1, q)
    if (core) quadTris(p + 1, p + 2, q + 2, q + 1)
    quadTris(p + 2, p + 3, q + 3, q + 2)
  }

  /** [[smoothBand]] round the `n` segments of a smooth oval's n + 1 runs of four from `v`. */
  @inline private def smoothBands(v: Int, n: Int, core: Boolean): Unit = {
    var i = 0
    while (i < n) {
      smoothBand(v + 4 * i, v + 4 * i + 4, core)
      i += 1
    }
  }

  def flush(): Unit = {
    if (indexCount == 0) { vertexCount = 0; return }

    glBindVertexArray(vao)
    glBindBuffer(GL_ARRAY_BUFFER, vbo)

    if (ringOffset + vertexCount > ringVerts) {
      // Wrapped: discard the old store. The driver hands back untouched memory instead of
      // waiting on draws that still reference the old one, and every offset in the new
      // generation is safe to write unsynchronized again.
      glBufferData(GL_ARRAY_BUFFER, ringVerts.toLong * VERTEX_BYTES, GL_STREAM_DRAW)
      ringOffset = 0
    }
    // The raw-address form: glMapBufferRange wraps each mapping in a new ByteBuffer, since
    // every flush maps a different range — one allocation per flush, ~100 a frame.
    val byteLen = vertexCount.toLong * VERTEX_BYTES
    val mapped = nglMapBufferRange(GL_ARRAY_BUFFER, ringOffset.toLong * VERTEX_BYTES, byteLen, ShapeBatch.MAP_FLAGS)
    if (mapped != 0L) {
      MemoryUtil.memCopy(bufferAddr, mapped, byteLen)
      glUnmapBuffer(GL_ARRAY_BUFFER)
    } else {
      // Driver refused the mapping — fall back to the (much slower) copy path.
      buffer.limit(vertexCount * VERTEX_SIZE).position(0)
      glBufferSubData(GL_ARRAY_BUFFER, ringOffset.toLong * VERTEX_BYTES, buffer)
      buffer.clear()
    }

    // The indices the same way, into the VAO's element buffer
    if (ringIndexOffset + indexCount > ringIndices) {
      glBufferData(GL_ELEMENT_ARRAY_BUFFER, ringIndices * 2L, GL_STREAM_DRAW)
      ringIndexOffset = 0
    }
    val indexBytes = indexCount * 2L
    val mappedIdx = nglMapBufferRange(GL_ELEMENT_ARRAY_BUFFER, ringIndexOffset * 2L, indexBytes, ShapeBatch.MAP_FLAGS)
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
  }

  def dispose(): Unit = {
    glDeleteVertexArrays(vao)
    glDeleteBuffers(vbo)
    glDeleteBuffers(ibo)
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
  private def growStaging(newCapacity0: Int): Unit = {
    val newCapacity = Math.min(MAX_CAPACITY, newCapacity0)
    if (newCapacity <= vertexCount) throw new IllegalStateException(s"a flush holds at most $MAX_CAPACITY vertices")
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

  /** Replace the index staging buffer with a larger one, keeping the indices already in it. */
  private def growIndices(newCapacity: Int): Unit = {
    val grown = BufferUtils.createShortBuffer(newCapacity)
    val grownAddr = MemoryUtil.memAddress(grown)
    MemoryUtil.memCopy(indexAddr, grownAddr, indexCount * 2L)
    indices = grown
    indexAddr = grownAddr
    indexCapacity = newCapacity
    if (ringIndices < indexCapacity * ShapeBatch.RING_FRAMES) {
      ringIndices = indexCapacity * ShapeBatch.RING_FRAMES
      ringIndexOffset = 0
      // The element buffer binding is the VAO's
      glBindVertexArray(vao)
      glBufferData(GL_ELEMENT_ARRAY_BUFFER, ringIndices * 2L, GL_STREAM_DRAW)
      glBindVertexArray(0)
    }
  }

  /** Room for `vertices` more vertices and `idx` more indices in this flush: flushed first if
    * either won't fit. */
  private def ensureCapacity(vertices: Int, idx: Int): Unit = {
    if (vertexCount + vertices > capacity || indexCount + idx > indexCapacity) {
      flush()
      // Grow the staging buffers, and the GPU rings with them
      if (vertices > capacity) growStaging(vertices * 2)
      if (idx > indexCapacity) growIndices(idx * 2)
    }
  }
}
