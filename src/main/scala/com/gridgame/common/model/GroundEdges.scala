package com.gridgame.common.model

/**
 * Where two kinds of ground meet, the game draws one soft, inked edge instead of the tiles' staircase
 * of diamonds: the match in a shader (the client's GroundEdgePainter), the menus' map pictures on the
 * CPU ([[shade]], which the shader follows line for line).
 *
 * Every ground that meets its neighbours this way has a layer, low to high: open space (the space
 * maps' void), the space maps' deck, deep water, water, ice, dirt, path, sand, snow, grass (and the
 * flowers, which are grass). Where layers meet, the higher spills over the lower. The edge runs where
 * a bilinear blend of the cells' "this layer or higher", one value at each cell's centre, is one
 * half: a straight run of cells keeps its edge on the cells' own edge, a corner is rounded, and a
 * staircase of cells, which is how a map draws a curve or a slope, becomes a curve. The higher ground
 * is inked along its edge, as the characters are; where that edge faces the camera it stands on a
 * riser of its own (grass on soil, snow on a bank, the deck on its hull, over space); water carries a
 * line of foam wherever anything meets it, and space a glow, the edge of the field that keeps the
 * arena in.
 *
 * Ground that is built in pieces (boardwalks, flagstones, metal), lava and blocks take no part (layer
 * 0). A cell reads a neighbour like that as more of its own ground, so they keep the tiles' straight
 * edges, nothing spills over them and they spill over nothing.
 */
object GroundEdges {
  /** Layers run 1 to Count - 1; 0 takes no part. The shader packs a layer into four bits. */
  val Count = 11

  private val layerById: Array[Int] = {
    val a = new Array[Int](Tile.all.map(_.id).max + 1)
    a(Tile.Void.id) = 1
    a(Tile.Circuit.id) = 2
    a(Tile.DeepWater.id) = 3
    a(Tile.Water.id) = 4
    a(Tile.Ice.id) = 5
    a(Tile.Dirt.id) = 6
    a(Tile.Path.id) = 7
    a(Tile.Sand.id) = 8
    a(Tile.Snow.id) = 9
    a(Tile.Grass.id) = 10
    a(Tile.Flowers.id) = 10
    a
  }

  def layerOf(t: Tile): Int = if (t.id >= 0 && t.id < layerById.length) layerById(t.id) else 0

  /** The layer of the ground cell (x, y) shows: a prop's is that of the ground it stands in. */
  def layerAt(world: WorldData, x: Int, y: Int): Int = {
    val t = world.getTile(x, y)
    if (t.form eq TileForm.Prop) layerOf(Tile.groundUnder(world, x, y)) else layerOf(t)
  }

  /** Every cell's layer, a row at a time. */
  def layers(world: WorldData): Array[Byte] = {
    val out = new Array[Byte](world.width * world.height)
    var y = 0
    while (y < world.height) {
      var x = 0
      while (x < world.width) { out(y * world.width + x) = layerAt(world, x, y).toByte; x += 1 }
      y += 1
    }
    out
  }

  /** Cell (x, y)'s layer in a grid of them, read as `own` where it takes no part; off the grid, the
    * nearest cell on it. */
  @inline private def read(layers: Array[Byte], w: Int, h: Int, x: Int, y: Int, own: Int): Int = {
    val cx = if (x < 0) 0 else if (x >= w) w - 1 else x
    val cy = if (y < 0) 0 else if (y >= h) h - 1 else y
    val l = layers(cy * w + cx) & 0xFF
    if (l == 0) own else l
  }

  /** Whether an edge passes through cell (x, y): it takes part, and one of the eight round it is other
    * ground that does. Nothing else in the cell's diamond can be touched by one, riser included. */
  def hasEdge(layers: Array[Byte], w: Int, h: Int, x: Int, y: Int): Boolean = {
    val own = layers(y * w + x) & 0xFF
    if (own == 0) return false
    var dy = -1
    while (dy <= 1) {
      var dx = -1
      while (dx <= 1) {
        if (read(layers, w, h, x + dx, y + dy, own) != own) return true
        dx += 1
      }
      dy += 1
    }
    false
  }

  // ── How an edge looks ──
  // Lines, risers and shores in display units, as the art is: a cell's diamond is 40 across.
  val InkWidth = 1.1
  /** The heights a riser can be, in display units: a step of soil or snow, and the deck's hull. A
    * riser reads the ground that far above each pixel, so two heights are two more looks round it;
    * neither may pass half a cell (10), or the look would leave the nine cells round it. */
  val RiserHeights: Array[Double] = Array(3.0, 8.0)

  /** Each layer's ground, the colour its tile is painted (so what spills over meets its own tiles
    * without a seam), by layer. */
  val base: Array[Int] = Array(0, Tile.Void.color, Tile.Circuit.color, Tile.DeepWater.color, Tile.Water.color,
    Tile.Ice.color, Tile.Dirt.color, Tile.Path.color, Tile.Sand.color, Tile.Snow.color, Tile.Grass.color)
  /** The line round each layer's edge, a dark version of its own colour as the characters' are; 0 for
    * none (water meets deep water softly, and nothing is above space). */
  val ink: Array[Int] = Array(0, 0, 0xFF0E3A40, 0, 0, 0, 0xFF74472E, 0xFF8C6843, 0xFF91744D, 0xFF96ACD6,
    0xFF1E5B16)
  /** The face of the step a layer stands on where its edge faces the camera, which of the
    * [[RiserHeights]] it is (1 or 2; 0 for a layer that lies flat), and the line at its foot. */
  val riser: Array[Int] = Array(0, 0, 0xFF3E4876, 0, 0, 0, 0, 0, 0xFFCAA97E, 0xFFCEDEF6, 0xFFB0784A)
  val riserKind: Array[Int] = Array(0, 0, 2, 0, 0, 0, 0, 0, 1, 1, 1)
  val riserInk: Array[Int] = Array(0, 0, 0xFF161634, 0, 0, 0, 0, 0, 0xFF91744D, 0xFF96ACD6, 0xFF4D2214)
  /** What a layer carries round anything higher that meets it, how wide, and whether it fades away
    * from the edge (a glow) rather than ending in a line (foam); 0 for nothing. */
  val shore: Array[Int] = Array(0, 0xFF6EE8FF, 0, 0, 0xFFE2F6FF, 0, 0, 0, 0, 0, 0)
  val shoreWidth: Array[Double] = Array(0, 2.6, 0, 0, 1.25, 0, 0, 0, 0, 0, 0)
  val shoreSoft: Array[Boolean] = Array(false, true, false, false, false, false, false, false, false, false, false)

  /** How far above a point, in world units along each axis, is the point a riser's height above it
    * on the screen: a display unit up is half a cell's height less of wx + wy. */
  def riserLift(kind: Int): Double = RiserHeights(kind - 1) / (2.0 * com.gridgame.common.Constants.ISO_HALF_H)

  private val HW = com.gridgame.common.Constants.ISO_HALF_W.toDouble
  private val HH = com.gridgame.common.Constants.ISO_HALF_H.toDouble
  private val LiftA = riserLift(1)
  private val LiftB = riserLift(2)

  /**
   * The edge drawn over cell (cx, cy) at the world point (px, py) (cells are centred on whole
   * numbers), as premultiplied RGBA from 0 to 1 in out(0..3), all 0 where the cell's own ground
   * shows. `pxPerUnit` is how many pixels (or samples) a display unit is: the lines are as wide in
   * units at any size, and every edge is smoothed over one pixel.
   */
  def shade(layers: Array[Byte], w: Int, h: Int, cx: Int, cy: Int, px: Double, py: Double,
            pxPerUnit: Double, out: Array[Double]): Unit = {
    out(0) = 0; out(1) = 0; out(2) = 0; out(3) = 0
    val own = layers(cy * w + cx) & 0xFF
    if (own == 0) return
    // The four cells round the point, and where it is between their centres; the same for the points
    // a riser's height above it, of either height
    val x0 = Math.floor(px).toInt; val y0 = Math.floor(py).toInt
    val s = px - x0; val t = py - y0
    val l00 = read(layers, w, h, x0, y0, own); val l10 = read(layers, w, h, x0 + 1, y0, own)
    val l01 = read(layers, w, h, x0, y0 + 1, own); val l11 = read(layers, w, h, x0 + 1, y0 + 1, own)
    val ax = px - LiftA; val ay = py - LiftA
    val ax0 = Math.floor(ax).toInt; val ay0 = Math.floor(ay).toInt
    val as = ax - ax0; val at = ay - ay0
    val a00 = read(layers, w, h, ax0, ay0, own); val a10 = read(layers, w, h, ax0 + 1, ay0, own)
    val a01 = read(layers, w, h, ax0, ay0 + 1, own); val a11 = read(layers, w, h, ax0 + 1, ay0 + 1, own)
    val bx = px - LiftB; val by = py - LiftB
    val bx0 = Math.floor(bx).toInt; val by0 = Math.floor(by).toInt
    val bs = bx - bx0; val bt = by - by0
    val b00 = read(layers, w, h, bx0, by0, own); val b10 = read(layers, w, h, bx0 + 1, by0, own)
    val b01 = read(layers, w, h, bx0, by0 + 1, own); val b11 = read(layers, w, h, bx0 + 1, by0 + 1, own)
    // Nothing but the cell's own ground round the point, or round the points a riser above it: no edge
    // can reach it. Most of a cell an edge passes through is like that.
    if (l00 == own && l10 == own && l01 == own && l11 == own && a00 == own && a10 == own && a01 == own &&
        a11 == own && b00 == own && b10 == own && b01 == own && b11 == own) return

    val inkPx = InkWidth * pxPerUnit
    var cur = 0 // the ground painted at the point so far
    var k = 1
    while (k < Count) {
      if (l00 == k || l10 == k || l01 == k || l11 == k || a00 == k || a10 == k || a01 == k || a11 == k ||
          b00 == k || b10 == k || b01 == k || b11 == k) {
        val d = distance(l00, l10, l01, l11, s, t, k, pxPerUnit)
        val kind = riserKind(k)
        val da =
          if (kind == 1) distance(a00, a10, a01, a11, as, at, k, pxPerUnit)
          else if (kind == 2) distance(b00, b10, b01, b11, bs, bt, k, pxPerUnit)
          else d
        val cover = clamp01(d + 0.5)
        // what lies below carries round this layer and its riser
        if (cur > 0 && shore(cur) != 0) {
          val dd = if (kind != 0) Math.max(d, da) else d
          val wPx = shoreWidth(cur) * pxPerUnit
          val reach =
            if (shoreSoft(cur)) { val f = clamp01((dd + wPx) / wPx); f * f }
            else clamp01(dd + wPx + 0.5)
          over(out, shore(cur), reach * (1 - clamp01(dd + 0.5)))
        }
        // this layer: over what is below, or, if it is the cell's own, cleared to show its tile
        if (k == own) {
          out(0) *= 1 - cover; out(1) *= 1 - cover; out(2) *= 1 - cover; out(3) *= 1 - cover
        } else over(out, base(k), cover)
        if (cover > 0.5) cur = k
        // the step it stands on, below its edge, inked along its foot
        if (kind != 0) {
          val r = clamp01(da + 0.5) * (1 - cover)
          val foot = clamp01(inkPx - da + 0.5)
          over(out, riser(k), r * (1 - foot))
          over(out, riserInk(k), r * foot)
          if (r > 0.5) cur = k
        }
        // and its line, inside its edge
        if (ink(k) != 0) over(out, ink(k), cover * clamp01(inkPx - d + 0.5))
      }
      k += 1
    }
  }

  /** The signed distance, in pixels, from a point to the edge of the ground of layer k or above
    * (positive inside), from the four cells round it and where it is between them. */
  private def distance(l00: Int, l10: Int, l01: Int, l11: Int, s: Double, t: Double, k: Int,
                       pxPerUnit: Double): Double = {
    val c00 = if (l00 >= k) 1.0 else 0.0; val c10 = if (l10 >= k) 1.0 else 0.0
    val c01 = if (l01 >= k) 1.0 else 0.0; val c11 = if (l11 >= k) 1.0 else 0.0
    val f = (1 - t) * ((1 - s) * c00 + s * c10) + t * ((1 - s) * c01 + s * c11)
    val fs = (1 - t) * (c10 - c00) + t * (c11 - c01)
    val ft = (1 - s) * (c01 - c00) + s * (c11 - c10)
    // On the screen x = (wx - wy) HW and y = (wx + wy) HH display units
    val gx = (fs - ft) / (2 * HW) / pxPerUnit
    val gy = (fs + ft) / (2 * HH) / pxPerUnit
    val g = Math.sqrt(gx * gx + gy * gy)
    if (g < 1e-9) { if (f >= 0.5) 1e4 else -1e4 } else (f - 0.5) / g
  }

  @inline private def clamp01(v: Double): Double = if (v < 0) 0 else if (v > 1) 1 else v

  /** Premultiplied `colour` at coverage `a` over out. */
  @inline private def over(out: Array[Double], colour: Int, a: Double): Unit = {
    if (a <= 0) return
    val k = 1 - a
    out(0) = ((colour >> 16) & 0xFF) / 255.0 * a + out(0) * k
    out(1) = ((colour >> 8) & 0xFF) / 255.0 * a + out(1) * k
    out(2) = (colour & 0xFF) / 255.0 * a + out(2) * k
    out(3) = a + out(3) * k
  }
}
