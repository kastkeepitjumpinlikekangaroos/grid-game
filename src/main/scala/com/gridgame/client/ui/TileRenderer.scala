package com.gridgame.client.ui

import com.gridgame.common.Constants

import javafx.scene.image.{Image, WritableImage}

import java.io.{File, FileInputStream, InputStream}

object TileRenderer {
  private val cellW = Constants.TILE_ATLAS_CELL_W   // 80
  private val cellH = Constants.TILE_ATLAS_CELL_H  // 112

  // tiles(tileId)(frame) = Image
  private var tiles: Array[Array[Image]] = _
  private var numTiles: Int = 0
  private var numFrames: Int = 1

  private def ensureLoaded(): Unit = {
    if (tiles != null) return

    val stream = resolveResourceStream("sprites/tiles.png")
    if (stream == null) {
      System.err.println("Tileset not found: sprites/tiles.png")
      numTiles = 0
      numFrames = 1
      tiles = Array.empty
      return
    }

    val sheet = new Image(stream)
    val reader = sheet.getPixelReader
    numTiles = (sheet.getWidth.toInt / cellW).max(1)
    numFrames = (sheet.getHeight.toInt / cellH).max(1)
    tiles = Array.tabulate(numTiles) { id =>
      Array.tabulate(numFrames) { frame =>
        new WritableImage(reader, id * cellW, frame * cellH, cellW, cellH)
      }
    }
  }

  private def resolveResourceStream(relativePath: String): InputStream = {
    val direct = new File(relativePath)
    if (direct.exists()) return new FileInputStream(direct)

    val buildWorkDir = System.getenv("BUILD_WORKING_DIRECTORY")
    if (buildWorkDir != null) {
      val fromWorkDir = new File(buildWorkDir, relativePath)
      if (fromWorkDir.exists()) return new FileInputStream(fromWorkDir)
    }

    // Try classpath (bundled in JAR)
    getClass.getClassLoader.getResourceAsStream(relativePath)
  }

  def getNumFrames: Int = {
    ensureLoaded()
    numFrames
  }

  def getTileImage(tileId: Int, frame: Int): Image = {
    ensureLoaded()
    val id = if (tileId >= 0 && tileId < numTiles) tileId else 0
    val f = frame % numFrames
    tiles(id)(f)
  }

  def getTileImage(tileId: Int): Image = getTileImage(tileId, 0)
}
