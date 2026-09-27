package com.gridgame.server.game

import com.gridgame.common.model.Player

import scala.collection.mutable.ArrayBuffer

/**
 * The players a projectile can hit this tick — alive, not shielded, not phased — in a flat array
 * ([[hittable]]) and in a grid of 4-cell squares for finding those near a point. Rebuilt in place
 * once a tick, so it allocates nothing per tick; it belongs to the projectile tick's thread.
 */
private[game] final class PlayerGrid {
  /** Pre-filtered snapshot of alive, hittable players rebuilt once per tick.
   *  Uses a pre-allocated array to avoid per-tick allocation. */
  private var hittablePlayers: Array[Player] = new Array[Player](64)
  private var hittableCount: Int = 0
  /** Spatial grid for efficient nearby-player lookups during collision detection.
   *  Uses a pre-allocated HashMap that is cleared each tick instead of reallocated. */
  private val gridCellSize = 4
  private val gridCells = new java.util.HashMap[Long, ArrayBuffer[Player]]()
  private val activeKeys = new ArrayBuffer[Long]()

  private def gridKey(cx: Int, cy: Int): Long = (cx.toLong << 32) | (cy.toLong & 0xFFFFFFFFL)

  /** The hittable players, in `hittable(0)` to `hittable(hittableSize - 1)`. */
  def hittable: Array[Player] = hittablePlayers
  def hittableSize: Int = hittableCount

  def rebuild(allPlayers: java.util.Collection[Player]): Unit = {
    // Clear previous tick's data without reallocating the HashMap
    var i = 0
    while (i < activeKeys.length) {
      val buf = gridCells.get(activeKeys(i))
      if (buf != null) buf.clear()
      i += 1
    }
    activeKeys.clear()

    // Also build the flat hittable array (reuse pre-allocated buffer)
    var count = 0
    val iter = allPlayers.iterator()
    while (iter.hasNext) {
      val player = iter.next()
      if (!player.isDead && !player.hasShield && !player.isPhased) {
        // Grow array if needed
        if (count >= hittablePlayers.length) {
          val newArr = new Array[Player](hittablePlayers.length * 2)
          System.arraycopy(hittablePlayers, 0, newArr, 0, count)
          hittablePlayers = newArr
        }
        hittablePlayers(count) = player
        count += 1
        val pos = player.getPosition
        val cx = pos.getX / gridCellSize
        val cy = pos.getY / gridCellSize
        val k = gridKey(cx, cy)
        var cell = gridCells.get(k)
        if (cell == null) {
          cell = new ArrayBuffer[Player](4)
          gridCells.put(k, cell)
        }
        if (cell.isEmpty) activeKeys += k
        cell += player
      }
    }
    hittableCount = count
  }

  /** Iterate players in the 3x3 neighborhood of the given world position.
   *  Calls `fn` for each nearby player. No allocation per call. */
  @inline def forEachNearby(x: Float, y: Float)(fn: Player => Unit): Unit = {
    val cx = (x / gridCellSize).toInt
    val cy = (y / gridCellSize).toInt
    var dy = -1
    while (dy <= 1) {
      var dx = -1
      while (dx <= 1) {
        val cell = gridCells.get(gridKey(cx + dx, cy + dy))
        if (cell != null) {
          var i = 0
          while (i < cell.length) {
            fn(cell(i))
            i += 1
          }
        }
        dx += 1
      }
      dy += 1
    }
  }

  /** Iterate players within a configurable radius using the spatial grid.
   *  Expands the grid cell search to cover the given radius. */
  def forEachNearbyPlayer(x: Float, y: Float, radius: Float)(fn: Player => Unit): Unit = {
    val cellRadius = (radius / gridCellSize).toInt + 1
    val cx = (x / gridCellSize).toInt
    val cy = (y / gridCellSize).toInt
    var dy = -cellRadius
    while (dy <= cellRadius) {
      var dx = -cellRadius
      while (dx <= cellRadius) {
        val cell = gridCells.get(gridKey(cx + dx, cy + dy))
        if (cell != null) {
          var i = 0
          while (i < cell.length) {
            fn(cell(i))
            i += 1
          }
        }
        dx += 1
      }
      dy += 1
    }
  }

  def clear(): Unit = {
    gridCells.clear()
    activeKeys.clear()
    hittableCount = 0
    java.util.Arrays.fill(hittablePlayers.asInstanceOf[Array[AnyRef]], null)
  }
}
