package com.gridgame.common.model

class WorldData(
  val name: String,
  val width: Int,
  val height: Int,
  val tiles: Array[Array[Tile]],
  val spawnPoints: Seq[Position],
  val background: String = "sky"
) {

  def getTile(x: Int, y: Int): Tile = {
    if (x >= 0 && x < width && y >= 0 && y < height) {
      tiles(y)(x)
    } else {
      Tile.Wall // Out of bounds is treated as wall
    }
  }

  def isWalkable(x: Int, y: Int): Boolean = {
    getTile(x, y).walkable
  }

  def isWalkable(pos: Position): Boolean = {
    isWalkable(pos.getX, pos.getY)
  }

  def setTile(x: Int, y: Int, tile: Tile): Boolean = {
    if (x >= 0 && x < width && y >= 0 && y < height) {
      tiles(y)(x) = tile
      true
    } else {
      false
    }
  }

  def getRandomSpawnPoint(): Position = {
    if (spawnPoints.nonEmpty) {
      val idx = (Math.random() * spawnPoints.length).toInt
      spawnPoints(idx)
    } else {
      // Default to center if no spawn points defined
      new Position(width / 2, height / 2)
    }
  }

  def getValidSpawnPoint(): Position = {
    // Try spawn points first
    for (spawn <- spawnPoints) {
      if (isWalkable(spawn)) {
        return spawn
      }
    }
    // Fall back to finding any walkable tile near center
    val centerX = width / 2
    val centerY = height / 2
    for (radius <- 0 until Math.max(width, height)) {
      for (dx <- -radius to radius) {
        for (dy <- -radius to radius) {
          val x = centerX + dx
          val y = centerY + dy
          if (x >= 0 && x < width && y >= 0 && y < height && isWalkable(x, y)) {
            return new Position(x, y)
          }
        }
      }
    }
    // Last resort
    new Position(0, 0)
  }
}

object WorldData {
  def createEmpty(width: Int, height: Int): WorldData = {
    val tiles: Array[Array[Tile]] = Array.fill(height, width)(Tile.Grass)
    new WorldData("Empty World", width, height, tiles, Seq(new Position(width / 2, height / 2)))
  }
}
