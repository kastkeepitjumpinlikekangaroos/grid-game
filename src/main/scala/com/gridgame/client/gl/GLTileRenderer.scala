package com.gridgame.client.gl

import com.gridgame.common.Constants

/**
 * OpenGL tile renderer. Loads sprites/tiles.png as a GL texture
 * and provides TextureRegion per tile ID + animation frame.
 * Mirrors the functionality of ui/TileRenderer.scala for the GL pipeline.
 */
object GLTileRenderer {
  private val cellW = Constants.TILE_ATLAS_CELL_W  // 80
  private val cellH = Constants.TILE_ATLAS_CELL_H  // 112

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
      texture = GLTexture.loadInspected("sprites/tiles.png", nearest = true, (px, w, h) => {
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
        // The ground is drawn as exact diamonds with blending off (drawDiamond), and a flat tile's
        // pixel-art edge is a staircase crossing the diamond's true edge: the pixels just inside
        // the edge sample texels just outside the staircase. Transparent, and black in the PNG
        GLTexture.padTransparent(px, w, h, cellW, cellH, 2)
      })
      texelU = 1f / texture.width
      texelV = 1f / texture.height
      numTiles = (texture.width / cellW).max(1)
      numFrames = (texture.height / cellH).max(1)
      regions = Array.tabulate(numTiles) { id =>
        Array.tabulate(numFrames) { frame =>
          texture.region(id * cellW, frame * cellH, cellW, cellH)
        }
      }
      // Atlas cells are 2x the display cell, so an atlas row is half a display pixel.
      val atlasToDisplay = Constants.TILE_CELL_HEIGHT.toFloat / cellH
      trimmed = Array.tabulate(numTiles) { id =>
        Array.tabulate(numFrames) { frame =>
          val top = if (contentTop == null) 0 else contentTop(id)(frame)
          texture.region(id * cellW, frame * cellH + top, cellW, cellH - top)
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
    val u0 = id * cellW * texelU
    val v0 = f * cellH * texelV
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
