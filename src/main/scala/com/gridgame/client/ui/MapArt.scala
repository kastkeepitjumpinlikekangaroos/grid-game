package com.gridgame.client.ui

import com.gridgame.common.Constants
import com.gridgame.common.model.{GroundEdges, Tile, TileForm, WorldData}

import javafx.scene.image.{Image, PixelFormat, WritableImage}

/**
 * The maps drawn as a match draws them, for the menus: every cell from the game's own tile art
 * (sprites/tiles.png) in the match's isometric projection, TerrainPainter's passes done on the CPU
 * into an image — the ground (each prop with the ground it stands in under it, the soft edge where two
 * kinds of it meet, GroundEdges.shade, and the shadows blocks cast beside them), then the blocks and
 * props a diagonal at a time, far to near. Seen from high
 * above, the whole map is a diamond; [[MapPreview]] stands it on a little base, and the page behind
 * every screen ([[WorldBackdrop]]) is a stretch of one seen closer.
 *
 * Drawn small, the art is filtered rather than picked: the atlas is halved four times over
 * (box-filtered, premultiplied), each sample reads the halving whose texels are about its own size,
 * and each pixel is the average of a grid of samples. The atlas is read for the pictures and let go
 * after them ([[release]]). Picked a texel per pixel, a forest drawn at
 * three pixels a tree was noise. It is drawn a band of rows at a time, so a big picture never needs
 * more than a strip of samples at once.
 */
private[client] object MapArt {
  private val HW = Constants.ISO_HALF_W.toDouble // 20 units: half a cell's diamond across
  private val HH = Constants.ISO_HALF_H.toDouble // 10 units: half of it down
  private val CellW = Constants.TILE_ATLAS_CELL_W // 80 texels: a cell of the atlas across
  private val CellH = Constants.TILE_ATLAS_CELL_H // 112 texels down
  private val Tpu = CellW / (2 * HW) // 2 texels to a unit
  /** How far above its diamond's centre a cell's art begins: blocks and props rise this far less
    * half a diamond above the ground. */
  private val ArtTop = Constants.TILE_CELL_HEIGHT - HH // 46
  /** How far a block or prop can rise above the top of its diamond. */
  val Rise: Double = ArtTop - HH // 36
  private val Levels = 5 // the atlas and four halvings: a cell is 5 x 7 texels at the last

  // ── The tile art ─────────────────────────────────────────────────────────

  /** The atlas, premultiplied ARGB, at each level; and for each tile's frame, its diamond's
    * average colour (opaque), for a sample at a diamond's edge that lands on a clear texel. */
  private final class Atlas(val px: Array[Array[Int]], val w: Array[Int], val tiles: Int, val frames: Int,
                            val groundColour: Array[Int])

  // Read when a picture is drawn and kept until release: 8MB, the atlas and its halvings
  private var loaded: Atlas = _
  private def atlas: Atlas = synchronized {
    if (loaded == null) loaded = load()
    loaded
  }

  /** Let the tile art go until the next picture: MapPreview's thread does when it runs out of
    * pictures to draw, so the menus don't hold it while they are only being looked at. */
  def release(): Unit = synchronized { loaded = null }

  private def load(): Atlas = {
    val url = SpriteGenerator.resolveResourceUrl("sprites/tiles.png")
    val img = if (url == null) null else new Image(url, false)
    if (img == null || img.isError) return new Atlas(Array.fill(Levels)(new Array[Int](1)), Array.fill(Levels)(1), 0, 1, Array(0))
    val w0 = img.getWidth.toInt; val h0 = img.getHeight.toInt
    val px = new Array[Array[Int]](Levels)
    val ws = new Array[Int](Levels)
    px(0) = new Array[Int](w0 * h0)
    ws(0) = w0
    img.getPixelReader.getPixels(0, 0, w0, h0, PixelFormat.getIntArgbPreInstance, px(0), 0, w0)
    var hs = h0
    for (l <- 1 until Levels) {
      val pw = ws(l - 1); val nw = pw / 2; val nh = hs / 2
      val src = px(l - 1); val dst = new Array[Int](nw * nh)
      var y = 0
      while (y < nh) {
        var x = 0
        while (x < nw) {
          val i = (y * 2) * pw + x * 2
          dst(y * nw + x) = average4(src(i), src(i + 1), src(i + pw), src(i + pw + 1))
          x += 1
        }
        y += 1
      }
      px(l) = dst; ws(l) = nw; hs = nh
    }
    val tiles = w0 / CellW
    val frames = Math.max(1, h0 / CellH)
    // A diamond's average: its lower 40 rows of texels, where they are opaque
    val ground = new Array[Int](tiles * frames)
    for (id <- 0 until tiles; f <- 0 until frames) {
      var r, g, b, n = 0L
      var ty = CellH - 4 * HH.toInt
      while (ty < CellH) {
        var tx = 0
        while (tx < CellW) {
          val c = px(0)((f * CellH + ty) * w0 + id * CellW + tx)
          if ((c >>> 24) == 255) { r += (c >> 16) & 0xFF; g += (c >> 8) & 0xFF; b += c & 0xFF; n += 1 }
          tx += 1
        }
        ty += 1
      }
      ground(id * frames + f) =
        if (n == 0) 0xFF000000 else 0xFF000000 | ((r / n).toInt << 16) | ((g / n).toInt << 8) | (b / n).toInt
    }
    new Atlas(px, ws, tiles, frames, ground)
  }

  private def average4(a: Int, b: Int, c: Int, d: Int): Int = {
    def ch(s: Int) = ((((a >>> s) & 0xFF) + ((b >>> s) & 0xFF) + ((c >>> s) & 0xFF) + ((d >>> s) & 0xFF) + 2) >> 2) << s
    ch(24) | ch(16) | ch(8) | ch(0)
  }

  /** Tile `id`'s frame `f` at (tx, ty), in the full-size atlas's texels within its cell, read at
    * level `l`: premultiplied, or clear outside the cell. */
  @inline private def texel(a: Atlas, l: Int, id: Int, f: Int, tx: Double, ty: Double): Int = {
    if (tx < 0 || ty < 0 || tx >= CellW || ty >= CellH) return 0
    val x = (id * CellW + tx).toInt >> l
    val y = (f * CellH + ty).toInt >> l
    a.px(l)(y * a.w(l) + x)
  }

  /** A premultiplied colour made opaque: its own colour where it has any, else `fallback`. */
  @inline private def opaque(c: Int, fallback: Int): Int = {
    val al = c >>> 24
    if (al == 255) c
    else if (al < 8) fallback
    else {
      val r = Math.min(255, ((c >> 16) & 0xFF) * 255 / al)
      val g = Math.min(255, ((c >> 8) & 0xFF) * 255 / al)
      val b = Math.min(255, (c & 0xFF) * 255 / al)
      0xFF000000 | (r << 16) | (g << 8) | b
    }
  }

  /** `src` over `dst`, both premultiplied. */
  @inline private def over(src: Int, dst: Int): Int = {
    val sa = src >>> 24
    if (sa == 255 || dst == 0) return src
    if (sa == 0) return dst
    val k = 255 - sa
    val a = sa + ((dst >>> 24) * k + 127) / 255
    val r = ((src >> 16) & 0xFF) + (((dst >> 16) & 0xFF) * k + 127) / 255
    val g = ((src >> 8) & 0xFF) + (((dst >> 8) & 0xFF) * k + 127) / 255
    val b = (src & 0xFF) + ((dst & 0xFF) * k + 127) / 255
    (Math.min(255, a) << 24) | (Math.min(255, r) << 16) | (Math.min(255, g) << 8) | Math.min(255, b)
  }

  /** A ground edge (GroundEdges.shade: premultiplied, 0 to 1) over an opaque colour. */
  @inline private def overEdge(e: Array[Double], c: Int): Int = {
    val k = 1 - e(3)
    val r = Math.min(255, (e(0) * 255 + ((c >> 16) & 0xFF) * k + 0.5).toInt)
    val g = Math.min(255, (e(1) * 255 + ((c >> 8) & 0xFF) * k + 0.5).toInt)
    val b = Math.min(255, (e(2) * 255 + (c & 0xFF) * k + 0.5).toInt)
    0xFF000000 | (r << 16) | (g << 8) | b
  }

  /** An opaque colour darkened by `k` (0 to 1) toward the near-black the match's shadows are. */
  @inline private def shade(c: Int, k: Double): Int = {
    val keep = 1 - k
    val r = (((c >> 16) & 0xFF) * keep).toInt
    val g = (((c >> 8) & 0xFF) * keep).toInt
    val b = ((c & 0xFF) * keep + 8 * k).toInt
    (c & 0xFF000000) | (r << 16) | (g << 8) | b
  }

  // ── The map ──────────────────────────────────────────────────────────────

  private final val Flat = 1
  private final val Raised = 2 // a block or a prop: drawn in the depth pass

  /** Where a picture of the map is: the projection's origin (cell (0, 0)'s centre) at (ox, oy)
    * pixels, `scale` pixels to a unit. */
  final case class View(scale: Double, ox: Double, oy: Double)

  /** The cells a picture shows: x0 to x1 across, y0 to y1 down, inclusive. */
  final case class Window(x0: Int, y0: Int, x1: Int, y1: Int)

  def whole(world: WorldData): Window = Window(0, 0, world.width - 1, world.height - 1)

  /**
   * Where a match on the map is played, and a little of what is round it: every cell that can be
   * walked to from a spawn point, and `rim` of that again on each side, within the map. A map's
   * border (the forest, the sea, the void) is scenery, a quarter or more of the map on most, and
   * shown whole it left the arena small in a little picture. The whole map if nobody can walk.
   */
  def playArea(world: WorldData, rim: Double): Window = {
    val W = world.width; val H = world.height
    val seen = new Array[Boolean](W * H)
    val queue = new Array[Int](W * H)
    var head = 0; var tail = 0
    world.spawnPoints.foreach { p =>
      val x = p.getX; val y = p.getY
      if (x >= 0 && y >= 0 && x < W && y < H && !seen(y * W + x) && world.isTileWalkable(x, y)) {
        seen(y * W + x) = true; queue(tail) = y * W + x; tail += 1
      }
    }
    var x0 = W; var y0 = H; var x1 = -1; var y1 = -1
    while (head < tail) {
      val c = queue(head); head += 1
      val x = c % W; val y = c / W
      x0 = Math.min(x0, x); x1 = Math.max(x1, x); y0 = Math.min(y0, y); y1 = Math.max(y1, y)
      def visit(nx: Int, ny: Int): Unit =
        if (nx >= 0 && ny >= 0 && nx < W && ny < H && !seen(ny * W + nx) && world.isTileWalkable(nx, ny)) {
          seen(ny * W + nx) = true; queue(tail) = ny * W + nx; tail += 1
        }
      visit(x + 1, y); visit(x - 1, y); visit(x, y + 1); visit(x, y - 1)
    }
    if (x1 < 0) return whole(world)
    val m = Math.max(2, Math.round(Math.max(x1 - x0, y1 - y0) * rim).toInt)
    Window(Math.max(0, x0 - m), Math.max(0, y0 - m), Math.min(W - 1, x1 + m), Math.min(H - 1, y1 + m))
  }

  /** What `win` covers in the projection's units, from cell (0, 0)'s centre: (left, top, right,
    * bottom), the top including `rise` above its far corner for what stands there. */
  def bounds(win: Window, rise: Double = Rise): (Double, Double, Double, Double) =
    ((win.x0 - win.y1 - 1) * HW, (win.x0 + win.y0 - 1) * HH - rise, (win.x1 - win.y0 + 1) * HW, (win.x1 + win.y1 + 1) * HH)

  /** The view that fits all of `win`, and `below` units under it, into `w` x `h` pixels with
    * `margin` of either side to spare, centred; `rise` as [[bounds]]. */
  def fit(win: Window, w: Int, h: Int, margin: Double, below: Double = 0, rise: Double = Rise): View = {
    val (l, t, r, b) = bounds(win, rise)
    val bb = b + below
    val scale = Math.min(w * (1 - 2 * margin) / (r - l), h * (1 - 2 * margin) / (bb - t))
    View(scale, w / 2.0 - (l + r) / 2 * scale, h / 2.0 - (t + bb) / 2 * scale)
  }

  /** The view that has the middle of `win` in the middle of `w` x `h` pixels, at `scale`. */
  def centred(win: Window, w: Int, h: Int, scale: Double): View = {
    val cx = (win.x0 + win.x1) / 2.0; val cy = (win.y0 + win.y1) / 2.0
    View(scale, w / 2.0 - (cx - cy) * HW * scale, h / 2.0 - (cx + cy) * HH * scale)
  }

  /**
   * The cells of `win` drawn into `w` x `h` pixels as `view` places them, premultiplied ARGB a row
   * at a time, each pixel the average of `ss` x `ss` samples; what is outside them is left clear.
   * With a `base`, they stand on a slab that many units deep, as a model of the map would: below
   * their two near edges, the side of each edge cell's ground in shadow, darker toward the bottom.
   */
  def draw(world: WorldData, win: Window, w: Int, h: Int, view: View, ss: Int, base: Double = 0): Array[Int] = {
    val a = atlas
    val out = new Array[Int](w * h)
    if (a.tiles == 0) return out
    val W = world.width; val H = world.height
    // Each cell's tile as drawn: what lies flat (a prop's own ground under it), and what stands
    val form = new Array[Byte](W * H)
    val flatId = new Array[Int](W * H)
    val flatFrame = new Array[Int](W * H)
    val raisedId = new Array[Int](W * H)
    val raisedFrame = new Array[Int](W * H)
    val isBlock = new Array[Boolean](W * H)
    for (y <- 0 until H; x <- 0 until W) {
      val i = y * W + x
      val t = world.getTile(x, y)
      // a picture is a still: a tile that animates as it is at its own frame of the cycle
      val variant = (if (t.animates) (x * 7 + y * 13) & 0x7FFFFFFF else t.variantAt(x, y)) % a.frames
      t.form match {
        case TileForm.Ground | TileForm.Pool =>
          form(i) = Flat; flatId(i) = t.id; flatFrame(i) = variant
        case TileForm.Prop =>
          val g = Tile.groundUnder(world, x, y)
          form(i) = Raised; flatId(i) = g.id; flatFrame(i) = g.variantAt(x, y) % a.frames
          raisedId(i) = t.id; raisedFrame(i) = variant
        case _ =>
          // A block covers its own diamond; under it, its own colour, for where its edge is soft
          form(i) = Raised; flatId(i) = t.id; flatFrame(i) = 0
          raisedId(i) = t.id; raisedFrame(i) = variant; isBlock(i) = true
      }
      if (flatId(i) >= a.tiles) flatId(i) = 0
      if (raisedId(i) >= a.tiles) raisedId(i) = 0
    }
    // Where two kinds of ground meet, as the match's GroundEdgePainter draws it
    val layers = GroundEdges.layers(world)
    val edged = new Array[Boolean](W * H)
    for (y <- win.y0 to win.y1; x <- win.x0 to win.x1) edged(y * W + x) = GroundEdges.hasEdge(layers, W, H, x, y)
    val edge = new Array[Double](4)

    val scale = view.scale
    val unitsPerSample = 1.0 / (scale * ss)
    val samplesPerUnit = scale * ss
    val texelsPerSample = Tpu * unitsPerSample
    val level = Math.max(0, Math.min(Levels - 1, Math.round(Math.log(texelsPerSample) / Math.log(2)).toInt))
    val bw = w * ss
    val colX = Array.tabulate(bw)(i => ((i + 0.5) / ss - view.ox) / scale)
    val Band = 16
    val buf = new Array[Int](bw * Band * ss)
    val shadowDepth = 5.0
    val shadowK = 0.22

    var by = 0
    while (by < h) {
      val rows = Math.min(Band, h - by)
      val bh = rows * ss
      java.util.Arrays.fill(buf, 0, bw * bh, 0)
      def rowY(j: Int): Double = ((by + (j + 0.5) / ss) - view.oy) / scale

      // ── The ground ──
      var j = 0
      while (j < bh) {
        val Y = rowY(j)
        val v = Y / HH
        var i = 0
        while (i < bw) {
          val X = colX(i)
          val u = X / HW
          val cx = Math.floor((u + v) * 0.5 + 0.5).toInt
          val cy = Math.floor((v - u) * 0.5 + 0.5).toInt
          if (cx >= win.x0 && cy >= win.y0 && cx <= win.x1 && cy <= win.y1) {
            val c = cy * W + cx
            val id = flatId(c); val f = flatFrame(c)
            val dx = X - (cx - cy) * HW
            val dy = Y - (cx + cy) * HH
            var col = opaque(texel(a, level, id, f, (dx + HW) * Tpu, (dy + ArtTop) * Tpu), a.groundColour(id * a.frames + f))
            if (edged(c)) {
              GroundEdges.shade(layers, W, H, cx, cy, (u + v) * 0.5, (v - u) * 0.5, samplesPerUnit, edge)
              if (edge(3) > 0) col = overEdge(edge, col)
            }
            // The shadow a block casts on the ground beside it, below its two near edges
            if (!isBlock(c)) {
              if (dx <= 0 && cx > win.x0 && isBlock(c - 1) && dy + HH * (1 + dx / HW) < shadowDepth) col = shade(col, shadowK)
              else if (dx >= 0 && cy > win.y0 && isBlock(c - W) && dy + HH * (1 - dx / HW) < shadowDepth) col = shade(col, shadowK)
            }
            buf(j * bw + i) = col
          } else if (base > 0) {
            // Below the near edges, the slab's two sides: the right one under the cells x = x1,
            // the left one under y = y1, meeting under the near corner (u = x1 - y1)
            val right = u >= win.x1 - win.y1
            val t = if (right) Y - HH * (2 * (win.x1 + 0.5) - u) else Y - HH * (2 * (win.y1 + 0.5) + u)
            if (t >= 0 && t <= base) {
              // How far along its edge: y on the right side, x on the left
              val along = if (right) (win.x1 + 0.5) - u else (win.y1 + 0.5) + u
              val lo = if (right) win.y0 else win.x0
              val hi = if (right) win.y1 else win.x1
              if (along >= lo - 0.5 && along <= hi + 0.5) {
                val k = Math.max(lo, Math.min(hi, Math.round(along).toInt))
                val c = if (right) k * W + win.x1 else win.y1 * W + k
                val edge = a.groundColour(flatId(c) * a.frames + flatFrame(c))
                val depth = t / base
                buf(j * bw + i) = shade(edge, (if (right) 0.52 else 0.36) + 0.3 * depth)
              }
            }
          }
          i += 1
        }
        j += 1
      }

      // ── Blocks and props, a diagonal at a time, far to near ──
      val top = rowY(0) - unitsPerSample; val bottom = rowY(bh - 1) + unitsPerSample
      val vLo = Math.max(win.x0 + win.y0, Math.ceil((top - HH) / HH).toInt)
      val vHi = Math.min(win.x1 + win.y1, Math.floor((bottom + ArtTop) / HH).toInt)
      var d = vLo
      while (d <= vHi) {
        var x = Math.max(win.x0, d - win.y1)
        val xEnd = Math.min(win.x1, d - win.y0)
        while (x <= xEnd) {
          val y = d - x
          val c = y * W + x
          if (form(c) == Raised) {
            val xc = (x - y) * HW; val yc = d * HH
            val left = xc - HW; val artTop = yc - ArtTop
            val i0 = Math.max(0, Math.floor(((left * scale) + view.ox) * ss).toInt)
            val i1 = Math.min(bw - 1, Math.ceil((((xc + HW) * scale) + view.ox) * ss).toInt)
            val id = raisedId(c); val f = raisedFrame(c)
            var jj = 0
            while (jj < bh) {
              val Y = rowY(jj)
              val ty = (Y - artTop) * Tpu
              if (ty >= 0 && ty < CellH) {
                var ii = i0
                while (ii <= i1) {
                  val t = texel(a, level, id, f, (colX(ii) - left) * Tpu, ty)
                  if (t != 0) buf(jj * bw + ii) = over(t, buf(jj * bw + ii))
                  ii += 1
                }
              }
              jj += 1
            }
          }
          x += 1
        }
        d += 1
      }

      // ── Each pixel the average of its samples ──
      val n = ss * ss
      var py = 0
      while (py < rows) {
        var px = 0
        while (px < w) {
          var sa, sr, sg, sb = 0
          var q = 0
          while (q < ss) {
            var p = 0
            val base = (py * ss + q) * bw + px * ss
            while (p < ss) {
              val s = buf(base + p)
              sa += s >>> 24; sr += (s >> 16) & 0xFF; sg += (s >> 8) & 0xFF; sb += s & 0xFF
              p += 1
            }
            q += 1
          }
          out((by + py) * w + px) = ((sa / n) << 24) | ((sr / n) << 16) | ((sg / n) << 8) | (sb / n)
          px += 1
        }
        py += 1
      }
      by += rows
    }
    out
  }

  /** Light a model of a map from its far left, in place: brighter toward the top left of the
    * picture, a little darker toward the bottom right, as a lamp over a table lights one. */
  def light(px: Array[Int], w: Int, h: Int): Unit = {
    var y = 0
    while (y < h) {
      var x = 0
      while (x < w) {
        val i = y * w + x
        val c = px(i)
        val a = c >>> 24
        if (a != 0) {
          val f = 1.08 - 0.16 * y / h - 0.06 * x / w
          val r = Math.min(a, (((c >> 16) & 0xFF) * f).toInt)
          val g = Math.min(a, (((c >> 8) & 0xFF) * f).toInt)
          val b = Math.min(a, ((c & 0xFF) * f).toInt)
          px(i) = (a << 24) | (r << 16) | (g << 8) | b
        }
        x += 1
      }
      y += 1
    }
  }

  /** Blur premultiplied pixels in place: a box `r` pixels either side, across and then down. */
  def soften(px: Array[Int], w: Int, h: Int, r: Int): Unit = {
    val tmp = new Array[Int](Math.max(w, h))
    def pass(start: Int, step: Int, n: Int): Unit = {
      var i = 0
      while (i < n) { tmp(i) = px(start + i * step); i += 1 }
      val span = 2 * r + 1
      i = 0
      while (i < n) {
        var sa, sr, sg, sb = 0
        var k = -r
        while (k <= r) {
          val c = tmp(Math.max(0, Math.min(n - 1, i + k)))
          sa += c >>> 24; sr += (c >> 16) & 0xFF; sg += (c >> 8) & 0xFF; sb += c & 0xFF
          k += 1
        }
        px(start + i * step) = ((sa / span) << 24) | ((sr / span) << 16) | ((sg / span) << 8) | (sb / span)
        i += 1
      }
    }
    var y = 0
    while (y < h) { pass(y * w, 1, w); y += 1 }
    var x = 0
    while (x < w) { pass(x, w, h); x += 1 }
  }

  /** Premultiplied pixels as an image. */
  def image(px: Array[Int], w: Int, h: Int): WritableImage = {
    val img = new WritableImage(w, h)
    img.getPixelWriter.setPixels(0, 0, w, h, PixelFormat.getIntArgbPreInstance, px, 0, w)
    img
  }
}
