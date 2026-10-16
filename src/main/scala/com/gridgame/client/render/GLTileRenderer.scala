package com.gridgame.client.render

import com.gridgame.common.Constants
import com.gridgame.client.gl.{GLTexture, SpriteBatch, TextureRegion}


/**
 * The tile atlas on the GPU: sprites/tiles.png as one texture, a TextureRegion per tile and frame
 * (whole, or with the empty rows above its art trimmed off), and [[drawDiamond]] for the ground.
 *
 * It is filtered as the characters are, linearly and through two halvings, so the terrain's lines
 * and outlines come out as smooth as theirs at any scale. Picked a texel per pixel, at the 80 texels
 * to 64 or 128 pixels a cell is drawn at, every line was a staircase of doubled and dropped texels.
 * Filtered, a pixel reads a little round its texel, so each cell is laid in a slot of its own with a
 * gutter of its own border texels round it, and its transparent texels take the colour of the art
 * beside them: a sample at the edge of a cell, or a halving of one, never reads the cell next to it in
 * the atlas or the black a PNG leaves in a clear texel. Without them every corner of the ground's
 * diamonds carried a dark dash.
 */
object GLTileRenderer {
  private val cellW = Constants.TILE_ATLAS_CELL_W  // 80
  private val cellH = Constants.TILE_ATLAS_CELL_H  // 112
  // Each cell's slot in the texture: the cell, and a gutter round it as wide as the halvings need
  // (a texel of the last one)
  private val MipLevels = 2
  private val Gutter = 1 << MipLevels
  private val slotW = cellW + 2 * Gutter
  private val slotH = cellH + 2 * Gutter

  private var texture: GLTexture = _
  private var numTiles: Int = 0
  private var numFrames: Int = 1
  // regions(tileId)(frame) = TextureRegion
  private var regions: Array[Array[TextureRegion]] = _
  // Same cells with the empty rows above the artwork cut off, plus how much was cut.
  // A flat ground tile only paints the bottom 40 of its 112 atlas rows, so drawing the
  // whole cell rasterizes (and alpha-blends) nearly 3x the pixels it needs to — and ground
  // covers the entire screen. Trimming is pixel-identical and is measured per frame at
  // load time, so it follows whatever generate_tiles.py produces.
  private var trimmed: Array[Array[TextureRegion]] = _
  private var trimTopPx: Array[Array[Float]] = _

  // One texel in texture coordinates, for drawing a flat tile as exactly its diamond
  private var texelU = 0f
  private var texelV = 0f
  // The diamond a tile stands on, in a cell's texels: centred on column 40, from row 72 to row 112
  private val centerCol = cellW / 2
  private val bottomRow = cellH
  private val midRow = cellH - Constants.ISO_HALF_H * 2
  private val topRow = cellH - Constants.ISO_HALF_H * 4

  private def ensureLoaded(): Unit = {
    if (texture != null) return
    try {
      // Row of the first non-transparent pixel in each (tile, frame) cell.
      var contentTop: Array[Array[Int]] = null
      texture = GLTexture.decode("sprites/tiles.png") { (px, w, h) =>
        val nt = (w / cellW).max(1)
        val nf = (h / cellH).max(1)
        contentTop = Array.ofDim[Int](nt, nf)
        var id = 0
        while (id < nt) {
          var f = 0
          while (f < nf) {
            var row = 0
            var found = -1
            while (row < cellH && found < 0) {
              val y = f * cellH + row
              var col = 0
              while (col < cellW && found < 0) {
                // RGBA8: alpha is the 4th byte of each pixel
                if (px.get(((y.toLong * w + id * cellW + col) * 4 + 3).toInt) != 0) found = row
                col += 1
              }
              row += 1
            }
            contentTop(id)(f) = if (found < 0) 0 else found
            f += 1
          }
          id += 1
        }
        // Clear texels take the colour of the art beside them, as far as a halving reaches: the
        // ground is drawn as exact diamonds with blending off, and a flat tile's edge is a staircase
        // crossing the diamond's true edge, so the pixels just inside it read texels just outside
        GLTexture.padTransparentRings(px, w, h, cellW, cellH, 2 * Gutter)
        // Each cell into its slot, the gutter round it its own border texels again, and its halvings,
        // a slot at a time: the slots line up with every halving, so a slot's are the atlas's there
        val tex = GLTexture.allocate(nt * slotW, nf * slotH, MipLevels)
        val levels = Array.tabulate(MipLevels + 1)(l => GLTexture.allocPixels((slotW >> l) * (slotH >> l) * 4))
        try {
          // in the decoded pixels' byte order, so a texel is copied as an int and comes out as it went in
          levels(0).order(px.order())
          var f = 0
          while (f < nf) {
            var t = 0
            while (t < nt) {
              val slot = levels(0)
              var sy = 0
              while (sy < slotH) {
                val cy = Math.max(0, Math.min(cellH - 1, sy - Gutter))
                var sx = 0
                while (sx < slotW) {
                  val cx = Math.max(0, Math.min(cellW - 1, sx - Gutter))
                  slot.putInt((sy * slotW + sx) * 4, px.getInt(((f * cellH + cy) * w + t * cellW + cx) * 4))
                  sx += 1
                }
                sy += 1
              }
              var l = 0
              while (l <= MipLevels) {
                if (l > 0) GLTexture.halve(levels(l - 1), slotW >> (l - 1), slotH >> (l - 1), levels(l))
                GLTexture.upload(tex, l, (t * slotW) >> l, (f * slotH) >> l, slotW >> l, slotH >> l, levels(l))
                l += 1
              }
              t += 1
            }
            f += 1
          }
        } finally levels.foreach(GLTexture.freePixels)
        tex
      }
      texelU = 1f / texture.width
      texelV = 1f / texture.height
      numTiles = (texture.width / slotW).max(1)
      numFrames = (texture.height / slotH).max(1)
      regions = Array.tabulate(numTiles) { id =>
        Array.tabulate(numFrames) { frame =>
          texture.region(id * slotW + Gutter, frame * slotH + Gutter, cellW, cellH)
        }
      }
      // Atlas cells are 2x the display cell, so an atlas row is half a display pixel.
      val atlasToDisplay = Constants.TILE_CELL_HEIGHT.toFloat / cellH
      trimmed = Array.tabulate(numTiles) { id =>
        Array.tabulate(numFrames) { frame =>
          val top = if (contentTop == null) 0 else contentTop(id)(frame)
          texture.region(id * slotW + Gutter, frame * slotH + Gutter + top, cellW, cellH - top)
        }
      }
      trimTopPx = Array.tabulate(numTiles) { id =>
        Array.tabulate(numFrames) { frame =>
          val top = if (contentTop == null) 0 else contentTop(id)(frame)
          top * atlasToDisplay
        }
      }
    } catch {
      case e: Exception =>
        System.err.println(s"GLTileRenderer: Failed to load tiles.png: ${e.getMessage}")
        numTiles = 0
        numFrames = 1
        regions = Array.empty
        trimmed = Array.empty
        trimTopPx = Array.empty
    }
  }

  /**
   * Draw a flat tile — ground or a pool — as exactly its diamond: corners left (xl, ym), top
   * (xc, yt), right (xr, ym) and bottom (xc, yb) on screen, each on the matching corner of the
   * tile's diamond in the atlas. Drawn as the cell's bounding rectangle instead, as the ground
   * used to be, every pixel of the ground was shaded and blended twice, since each tile's
   * transparent corners lie under its neighbours. Neighbours built from the same corner floats
   * meet exactly, so the ground can be drawn with blending off.
   */
  def drawDiamond(batch: SpriteBatch, tileId: Int, frame: Int,
                  xl: Float, xc: Float, xr: Float, yt: Float, ym: Float, yb: Float): Unit = {
    ensureLoaded()
    if (numTiles == 0) return
    val id = if (tileId >= 0 && tileId < numTiles) tileId else 0
    val f = frame % numFrames
    val u0 = (id * slotW + Gutter) * texelU
    val v0 = (f * slotH + Gutter) * texelV
    batch.drawQuad(texture,
      xl, ym, u0, v0 + midRow * texelV,
      xc, yt, u0 + centerCol * texelU, v0 + topRow * texelV,
      xr, ym, u0 + cellW * texelU, v0 + midRow * texelV,
      xc, yb, u0 + centerCol * texelU, v0 + bottomRow * texelV)
  }

  def getNumFrames: Int = {
    ensureLoaded()
    numFrames
  }

  def getTileRegion(tileId: Int, frame: Int): TextureRegion = {
    ensureLoaded()
    if (regions.isEmpty) return null
    val id = if (tileId >= 0 && tileId < numTiles) tileId else 0
    val f = frame % numFrames
    regions(id)(f)
  }

  def getTileRegion(tileId: Int): TextureRegion = getTileRegion(tileId, 0)

  /**
   * Cell region with the transparent rows above the artwork removed. Pair with
   * [[getTrimTopPx]], which says how far down the display cell the returned region starts.
   */
  def getTrimmedRegion(tileId: Int, frame: Int): TextureRegion = {
    ensureLoaded()
    if (trimmed == null || trimmed.isEmpty) return null
    val id = if (tileId >= 0 && tileId < numTiles) tileId else 0
    trimmed(id)(frame % numFrames)
  }

  /** Display-space pixels of empty space trimmed off the top of a cell. */
  def getTrimTopPx(tileId: Int, frame: Int): Float = {
    ensureLoaded()
    if (trimTopPx == null || trimTopPx.isEmpty) return 0f
    val id = if (tileId >= 0 && tileId < numTiles) tileId else 0
    trimTopPx(id)(frame % numFrames)
  }

  def getTexture: GLTexture = {
    ensureLoaded()
    texture
  }

  def dispose(): Unit = {
    if (texture != null) {
      texture.dispose()
      texture = null
      regions = null
      trimmed = null
      trimTopPx = null
    }
  }
}
