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

  private def ensureLoaded(): Unit = {
    if (texture != null) return
    try {
      texture = GLTexture.load("sprites/tiles.png", nearest = true)
      numTiles = (texture.width / cellW).max(1)
      numFrames = (texture.height / cellH).max(1)
      regions = Array.tabulate(numTiles) { id =>
        Array.tabulate(numFrames) { frame =>
          texture.region(id * cellW, frame * cellH, cellW, cellH)
        }
      }
    } catch {
      case e: Exception =>
        System.err.println(s"GLTileRenderer: Failed to load tiles.png: ${e.getMessage}")
        numTiles = 0
        numFrames = 1
        regions = Array.empty
    }
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

  def getTexture: GLTexture = {
    ensureLoaded()
    texture
  }

  def dispose(): Unit = {
    if (texture != null) {
      texture.dispose()
      texture = null
      regions = null
    }
  }
}
