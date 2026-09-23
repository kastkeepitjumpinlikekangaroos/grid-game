package com.gridgame.common.model

class WorldData(
  val name: String,
  val width: Int,
  val height: Int,
  val tiles: Array[Array[Tile]],
  val spawnPoints: Seq[Position],
  val background: String = "sky"
) {

  /**
   * The opening divider standing over this world while a team match begins, or null (see
   * [[TeamDivider]]). It is not terrain — the tiles are untouched — but nothing may stand on the
   * cells it runs through while it is up, so it is folded into [[isWalkable]]: every movement,
   * teleport, throw, bot path and spawn check in the game already asks that question.
   */
  @volatile var divider: TeamDivider = _

  def getTile(x: Int, y: Int): Tile = {
    if (x >= 0 && x < width && y >= 0 && y < height) {
      tiles(y)(x)
    } else {
      Tile.Wall // Out of bounds is treated as wall
    }
  }

  /** The terrain alone, with no divider standing over it — what a projectile is stopped by. The
    * divider is not terrain: it stops shots on its own line, and stops the ones that fly over
    * walls as surely as the rest ([[TeamDivider]], ProjectileManager). */
  def isTileWalkable(x: Int, y: Int): Boolean = getTile(x, y).walkable

  def isWalkable(x: Int, y: Int): Boolean = {
    val d = divider
    isTileWalkable(x, y) && (d == null || !d.blocks(x, y))
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

  private val anyCell: (Int, Int) => Boolean = (_, _) => true

  def getValidSpawnPoint(): Position = getValidSpawnPoint(Set.empty)

  def getValidSpawnPoint(occupied: Set[(Int, Int)]): Position = getValidSpawnPoint(occupied, anyCell)

  /**
   * A spawn point away from the cells in `occupied`, out of those `accept` allows — which is how
   * a team match keeps each team in its own half of the map (see [[TeamDivider]]). A half that
   * has nowhere to put anyone falls back to the whole map rather than to the middle of it.
   */
  def getValidSpawnPoint(occupied: Set[(Int, Int)], accept: (Int, Int) => Boolean): Position = {
    // Try spawn points with full minimum distance
    if (spawnPoints.nonEmpty) {
      val shuffled = scala.util.Random.shuffle(spawnPoints)
      for (spawn <- shuffled) {
        if (isWalkable(spawn) && accept(spawn.getX, spawn.getY) && isFarEnough(spawn.getX, spawn.getY, occupied)) {
          return spawn
        }
      }
    }

    // No predefined spawn point works — find a random walkable tile far from others
    val candidates = scala.collection.mutable.ArrayBuffer[Position]()
    for (y <- 0 until height; x <- 0 until width) {
      if (isWalkable(x, y) && accept(x, y) && isFarEnough(x, y, occupied)) {
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
        if (isWalkable(x, y) && accept(x, y) && occupied.forall { case (ox, oy) =>
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

    // Nowhere in the half it was asked for: anywhere at all, rather than the middle of the map
    if (accept ne anyCell) return getValidSpawnPoint(occupied)

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
