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

  def getValidSpawnPoint(): Position = getValidSpawnPoint(Set.empty)

  def getValidSpawnPoint(occupied: Set[(Int, Int)]): Position = {
    // Shuffle spawn points so each caller gets a random one
    if (spawnPoints.nonEmpty) {
      val shuffled = scala.util.Random.shuffle(spawnPoints)
      for (spawn <- shuffled) {
        if (isWalkable(spawn) && !occupied.contains((spawn.getX, spawn.getY))) {
          return spawn
        }
      }
      // All spawn points occupied — try nearby walkable tiles around each spawn
      for (spawn <- shuffled) {
        for (radius <- 1 to 5) {
          val offsets = scala.util.Random.shuffle(
            (for (dx <- -radius to radius; dy <- -radius to radius if Math.abs(dx) == radius || Math.abs(dy) == radius) yield (dx, dy)).toList
          )
          for ((dx, dy) <- offsets) {
            val x = spawn.getX + dx
            val y = spawn.getY + dy
            if (x >= 0 && x < width && y >= 0 && y < height && isWalkable(x, y) && !occupied.contains((x, y))) {
              return new Position(x, y)
            }
          }
        }
      }
    }
    // Fall back to finding a random walkable tile
    val walkable = scala.collection.mutable.ArrayBuffer[Position]()
    val centerX = width / 2
    val centerY = height / 2
    val searchRadius = Math.min(30, Math.max(width, height))
    for (dx <- -searchRadius to searchRadius) {
      for (dy <- -searchRadius to searchRadius) {
        val x = centerX + dx
        val y = centerY + dy
        if (x >= 0 && x < width && y >= 0 && y < height && isWalkable(x, y) && !occupied.contains((x, y))) {
          walkable += new Position(x, y)
        }
      }
    }
    if (walkable.nonEmpty) {
      return walkable(scala.util.Random.nextInt(walkable.size))
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
