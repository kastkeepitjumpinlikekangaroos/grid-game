package com.gridgame.client.ui

import com.gridgame.common.Constants

import javafx.scene.image.{Image, WritableImage}

import java.io.{File, FileInputStream}

object TileRenderer {
  private val cellW = Constants.TILE_CELL_WIDTH   // 40
  private val cellH = Constants.TILE_CELL_HEIGHT  // 56
  private val numTiles = 20

  // tiles(tileId)(frame) = Image
  private var tiles: Array[Array[Image]] = _
  private var numFrames: Int = 1

  private def ensureLoaded(): Unit = {
    if (tiles != null) return

    val path = resolveSpritePath("sprites/tiles.png")
    val file = new File(path)
    if (!file.exists()) {
      System.err.println(s"Tileset not found: ${file.getAbsolutePath}")
      numFrames = 1
      tiles = Array.fill(numTiles)(Array.fill(1)(new WritableImage(cellW, cellH)))
      return
    }

    val sheet = new Image(new FileInputStream(file))
    val reader = sheet.getPixelReader
    numFrames = (sheet.getHeight.toInt / cellH).max(1)
    tiles = Array.tabulate(numTiles) { id =>
      Array.tabulate(numFrames) { frame =>
        new WritableImage(reader, id * cellW, frame * cellH, cellW, cellH)
      }
    }
  }

  private def resolveSpritePath(relativePath: String): String = {
    val direct = new File(relativePath)
    if (direct.exists()) return direct.getAbsolutePath

    val buildWorkDir = System.getenv("BUILD_WORKING_DIRECTORY")
    if (buildWorkDir != null) {
      val fromWorkDir = new File(buildWorkDir, relativePath)
      if (fromWorkDir.exists()) return fromWorkDir.getAbsolutePath
    }

    relativePath
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
