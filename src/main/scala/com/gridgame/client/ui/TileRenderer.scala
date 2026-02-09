package com.gridgame.client.ui

import com.gridgame.common.Constants

import javafx.scene.image.{Image, WritableImage}

import java.io.{File, FileInputStream}

object TileRenderer {
  private val cellW = Constants.TILE_CELL_WIDTH   // 40
  private val cellH = Constants.TILE_CELL_HEIGHT  // 56
  private val numTiles = 20

  private var tiles: Array[Image] = _

  private def ensureLoaded(): Unit = {
    if (tiles != null) return

    val path = resolveSpritePath("sprites/tiles.png")
    val file = new File(path)
    if (!file.exists()) {
      System.err.println(s"Tileset not found: ${file.getAbsolutePath}")
      tiles = Array.fill(numTiles)(new WritableImage(cellW, cellH))
      return
    }

    val sheet = new Image(new FileInputStream(file))
    val reader = sheet.getPixelReader
    tiles = Array.tabulate(numTiles) { id =>
      new WritableImage(reader, id * cellW, 0, cellW, cellH)
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

  def getTileImage(tileId: Int): Image = {
    ensureLoaded()
    if (tileId >= 0 && tileId < numTiles) tiles(tileId)
    else tiles(0) // fallback to grass
  }
}
