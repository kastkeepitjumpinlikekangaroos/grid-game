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

  private val MIN_SPAWN_DISTANCE = 50

  private def isFarEnough(x: Int, y: Int, occupied: Set[(Int, Int)]): Boolean = {
    occupied.forall { case (ox, oy) =>
      val dx = x - ox
      val dy = y - oy
      Math.sqrt(dx.toDouble * dx + dy.toDouble * dy) >= MIN_SPAWN_DISTANCE
    }
  }

  def getValidSpawnPoint(): Position = getValidSpawnPoint(Set.empty)

  def getValidSpawnPoint(occupied: Set[(Int, Int)]): Position = {
    // Try spawn points with full minimum distance
    if (spawnPoints.nonEmpty) {
      val shuffled = scala.util.Random.shuffle(spawnPoints)
      for (spawn <- shuffled) {
        if (isWalkable(spawn) && isFarEnough(spawn.getX, spawn.getY, occupied)) {
          return spawn
        }
      }
    }

    // No predefined spawn point works — find a random walkable tile far from others
    val candidates = scala.collection.mutable.ArrayBuffer[Position]()
    for (y <- 0 until height; x <- 0 until width) {
      if (isWalkable(x, y) && isFarEnough(x, y, occupied)) {
        candidates += new Position(x, y)
      }
    }
    if (candidates.nonEmpty) {
      return candidates(scala.util.Random.nextInt(candidates.size))
    }

    // Map too small for full distance — relax to half, then quarter, then any
    for (relaxed <- Seq(MIN_SPAWN_DISTANCE / 2, MIN_SPAWN_DISTANCE / 4, 5, 1)) {
      val relaxedCandidates = scala.collection.mutable.ArrayBuffer[Position]()
      for (y <- 0 until height; x <- 0 until width) {
        if (isWalkable(x, y) && occupied.forall { case (ox, oy) =>
          val dx = x - ox; val dy = y - oy
          Math.sqrt(dx.toDouble * dx + dy.toDouble * dy) >= relaxed
        }) {
          relaxedCandidates += new Position(x, y)
        }
      }
      if (relaxedCandidates.nonEmpty) {
        return relaxedCandidates(scala.util.Random.nextInt(relaxedCandidates.size))
      }
    }

    // Last resort
    new Position(width / 2, height / 2)
  }
}

object WorldData {
  def createEmpty(width: Int, height: Int): WorldData = {
    val tiles: Array[Array[Tile]] = Array.fill(height, width)(Tile.Grass)
    new WorldData("Empty World", width, height, tiles, Seq(new Position(width / 2, height / 2)))
  }
}
