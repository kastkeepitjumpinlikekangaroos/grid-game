package com.gridgame.common.world

import com.google.gson.JsonParser
import com.google.gson.JsonObject
import com.google.gson.JsonArray
import com.gridgame.common.model.{Position, Tile, WorldData}

import java.io.{File, FileReader, BufferedReader, InputStreamReader}
import scala.collection.mutable.ArrayBuffer

object WorldLoader {

  def load(path: String): WorldData = {
    // Try filesystem first
    val file = new File(path)
    if (file.exists()) return loadFromFile(file.getAbsolutePath)

    // Try relative to BUILD_WORKING_DIRECTORY (set by Bazel)
    val buildWorkDir = System.getenv("BUILD_WORKING_DIRECTORY")
    if (buildWorkDir != null) {
      val fromWorkDir = new File(buildWorkDir, path)
      if (fromWorkDir.exists()) return loadFromFile(fromWorkDir.getAbsolutePath)
    }

    // Try classpath (bundled in JAR)
    loadFromResource(path)
  }

  def loadFromFile(filePath: String): WorldData = {
    val file = new File(filePath)
    if (!file.exists()) {
      throw new IllegalArgumentException(s"World file not found: $filePath")
    }
    val reader = new BufferedReader(new FileReader(file))
    try {
      parseJson(reader)
    } finally {
      reader.close()
    }
  }

  def loadFromResource(resourcePath: String): WorldData = {
    val stream = getClass.getClassLoader.getResourceAsStream(resourcePath)
    if (stream == null) {
      throw new IllegalArgumentException(s"World resource not found: $resourcePath")
    }
    val reader = new BufferedReader(new InputStreamReader(stream))
    try {
      parseJson(reader)
    } finally {
      reader.close()
    }
  }

  private def parseJson(reader: BufferedReader): WorldData = {
    val jsonElement = JsonParser.parseReader(reader)
    val root = jsonElement.getAsJsonObject

    val name = root.get("name").getAsString
    val width = root.get("width").getAsInt
    val height = root.get("height").getAsInt
    val background = if (root.has("background")) root.get("background").getAsString else "sky"

    // Initialize tiles with default (grass)
    val tiles: Array[Array[Tile]] = Array.fill(height, width)(Tile.Grass)

    // Parse terrain layers
    if (root.has("layers")) {
      val layers = root.getAsJsonArray("layers")
      for (i <- 0 until layers.size()) {
        val layer = layers.get(i).getAsJsonObject
        parseLayer(layer, tiles, width, height)
      }
    }

    // Parse spawn points
    val spawnPoints = ArrayBuffer[Position]()
    if (root.has("spawnPoints")) {
      val spawns = root.getAsJsonArray("spawnPoints")
      for (i <- 0 until spawns.size()) {
        val spawn = spawns.get(i).getAsJsonObject
        val x = spawn.get("x").getAsInt
        val y = spawn.get("y").getAsInt
        if (x >= 0 && x < width && y >= 0 && y < height) {
          spawnPoints += new Position(x, y)
        }
      }
    }

    // Default spawn point if none defined
    if (spawnPoints.isEmpty) {
      spawnPoints += new Position(width / 2, height / 2)
    }

    new WorldData(name, width, height, tiles, spawnPoints.toSeq, background)
  }

  private def parseLayer(layer: JsonObject, tiles: Array[Array[Tile]], width: Int, height: Int): Unit = {
    val tileType = Tile.fromName(layer.get("tile").getAsString)
    val layerType = layer.get("type").getAsString

    layerType match {
      case "fill" =>
        // Fill entire area
        for (y <- 0 until height; x <- 0 until width) {
          tiles(y)(x) = tileType
        }

      case "rect" =>
        // Fill a rectangle
        val x1 = layer.get("x").getAsInt
        val y1 = layer.get("y").getAsInt
        val w = layer.get("width").getAsInt
        val h = layer.get("height").getAsInt
        for (y <- y1 until Math.min(y1 + h, height); x <- x1 until Math.min(x1 + w, width)) {
          if (x >= 0 && y >= 0) {
            tiles(y)(x) = tileType
          }
        }

      case "border" =>
        // Create a border around the map
        val thickness = if (layer.has("thickness")) layer.get("thickness").getAsInt else 1
        for (t <- 0 until thickness) {
          for (x <- 0 until width) {
            if (t < height) tiles(t)(x) = tileType
            if (height - 1 - t >= 0) tiles(height - 1 - t)(x) = tileType
          }
          for (y <- 0 until height) {
            if (t < width) tiles(y)(t) = tileType
            if (width - 1 - t >= 0) tiles(y)(width - 1 - t) = tileType
          }
        }

      case "circle" =>
        // Fill a circle
        val cx = layer.get("cx").getAsInt
        val cy = layer.get("cy").getAsInt
        val radius = layer.get("radius").getAsInt
        for (y <- Math.max(0, cy - radius) until Math.min(height, cy + radius + 1)) {
          for (x <- Math.max(0, cx - radius) until Math.min(width, cx + radius + 1)) {
            val dx = x - cx
            val dy = y - cy
            if (dx * dx + dy * dy <= radius * radius) {
              tiles(y)(x) = tileType
            }
          }
        }

      case "line" =>
        // Draw a line (for paths, rivers, etc.)
        val x1 = layer.get("x1").getAsInt
        val y1 = layer.get("y1").getAsInt
        val x2 = layer.get("x2").getAsInt
        val y2 = layer.get("y2").getAsInt
        val thickness = if (layer.has("thickness")) layer.get("thickness").getAsInt else 1
        drawLine(tiles, x1, y1, x2, y2, thickness, tileType, width, height)

      case "scatter" =>
        // Randomly scatter tiles
        val density = layer.get("density").getAsDouble
        val minX = if (layer.has("minX")) layer.get("minX").getAsInt else 0
        val minY = if (layer.has("minY")) layer.get("minY").getAsInt else 0
        val maxX = if (layer.has("maxX")) layer.get("maxX").getAsInt else width
        val maxY = if (layer.has("maxY")) layer.get("maxY").getAsInt else height
        val avoidTiles = if (layer.has("avoid")) {
          val avoidArr = layer.getAsJsonArray("avoid")
          (0 until avoidArr.size()).map(i => Tile.fromName(avoidArr.get(i).getAsString)).toSet
        } else {
          Set.empty[Tile]
        }

        for (y <- minY until Math.min(maxY, height); x <- minX until Math.min(maxX, width)) {
          if (x >= 0 && y >= 0 && !avoidTiles.contains(tiles(y)(x)) && Math.random() < density) {
            tiles(y)(x) = tileType
          }
        }

      case "points" =>
        // Place tiles at specific points
        val points = layer.getAsJsonArray("points")
        for (i <- 0 until points.size()) {
          val point = points.get(i).getAsJsonObject
          val x = point.get("x").getAsInt
          val y = point.get("y").getAsInt
          if (x >= 0 && x < width && y >= 0 && y < height) {
            tiles(y)(x) = tileType
          }
        }

      case _ =>
        println(s"Unknown layer type: $layerType")
    }
  }

  private def drawLine(tiles: Array[Array[Tile]], x1: Int, y1: Int, x2: Int, y2: Int,
                       thickness: Int, tile: Tile, width: Int, height: Int): Unit = {
    val dx = Math.abs(x2 - x1)
    val dy = Math.abs(y2 - y1)
    val sx = if (x1 < x2) 1 else -1
    val sy = if (y1 < y2) 1 else -1
    var err = dx - dy
    var x = x1
    var y = y1

    while (true) {
      // Draw with thickness
      for (ty <- -(thickness / 2) to (thickness / 2)) {
        for (tx <- -(thickness / 2) to (thickness / 2)) {
          val px = x + tx
          val py = y + ty
          if (px >= 0 && px < width && py >= 0 && py < height) {
            tiles(py)(px) = tile
          }
        }
      }

      if (x == x2 && y == y2) return

      val e2 = 2 * err
      if (e2 > -dy) {
        err -= dy
        x += sx
      }
      if (e2 < dx) {
        err += dx
        y += sy
      }
    }
  }
}
