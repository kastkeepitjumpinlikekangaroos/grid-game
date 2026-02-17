package com.gridgame.client.render

import com.gridgame.client.GameClient
import com.gridgame.common.model.{Item, Player, Projectile}

import java.util.UUID
import scala.collection.mutable

/**
 * Collects game entities (items, projectiles, players) by grid cell for depth-sorted rendering.
 * Used by both the JavaFX and OpenGL renderers to ensure correct isometric depth ordering.
 */
class EntityCollector {
  // Remote player visual interpolation state
  val remoteVisualPositions: mutable.Map[UUID, (Double, Double)] = mutable.Map.empty

  /** Represents a drawable entity at a grid cell position. */
  case class CellEntity(cellX: Int, cellY: Int, drawOrder: Int, draw: () => Unit)

  // Pre-allocated map reused each frame (cleared instead of recreated)
  private val entitiesByCell = mutable.Map.empty[(Int, Int), mutable.ArrayBuffer[() => Unit]]
  // Buffer pool for ArrayBuffer reuse
  private val bufferPool = mutable.ArrayBuffer.empty[mutable.ArrayBuffer[() => Unit]]

  private def acquireBuffer(): mutable.ArrayBuffer[() => Unit] = {
    if (bufferPool.nonEmpty) bufferPool.remove(bufferPool.size - 1) else mutable.ArrayBuffer.empty
  }

  private def releaseBuffers(): Unit = {
    val iter = entitiesByCell.valuesIterator
    while (iter.hasNext) {
      val buf = iter.next()
      buf.clear()
      bufferPool += buf
    }
    entitiesByCell.clear()
  }

  /**
   * Collect all entities from the client state, grouped by cell.
   * Returns a map of (cellX, cellY) → sequence of draw functions.
   *
   * @param client The game client
   * @param deltaSec Frame delta time
   * @param drawItem Function to draw an item
   * @param drawProjectile Function to draw a projectile
   * @param drawPlayer Function to draw a remote player at interpolated position
   * @param drawLocalPlayer Function to draw the local player
   * @param drawLocalDeath Function to draw local player death effect
   * @param localVisualX Local player visual X
   * @param localVisualY Local player visual Y
   */
  def collect(
    client: GameClient,
    deltaSec: Double,
    drawItem: Item => Unit,
    drawProjectile: Projectile => Unit,
    drawPlayer: (Player, Double, Double) => Unit,
    drawLocalPlayer: () => Unit,
    drawLocalDeath: () => Unit,
    localVisualX: Double,
    localVisualY: Double,
    localDeathAnimActive: Boolean,
    minVisX: Int = -1000000,
    maxVisX: Int = 1000000,
    minVisY: Int = -1000000,
    maxVisY: Int = 1000000
  ): mutable.Map[(Int, Int), mutable.ArrayBuffer[() => Unit]] = {
    // Return old buffers to pool and clear the map
    releaseBuffers()

    def addEntity(cx: Int, cy: Int, drawFn: () => Unit): Unit = {
      val key = (cx, cy)
      val buf = entitiesByCell.getOrElse(key, null)
      if (buf != null) {
        buf += drawFn
      } else {
        val newBuf = acquireBuffer()
        newBuf += drawFn
        entitiesByCell(key) = newBuf
      }
    }

    // Bounds for off-screen culling (pre-expanded by 2 cells for margin)
    val cullingMinX = minVisX - 2
    val cullingMaxX = maxVisX + 2
    val cullingMinY = minVisY - 2
    val cullingMaxY = maxVisY + 2

    // Items — use Java iterator directly
    val itemIter = client.getItems.values().iterator()
    while (itemIter.hasNext) {
      val item = itemIter.next()
      addEntity(item.getCellX, item.getCellY, () => drawItem(item))
    }

    // Projectiles — use Java iterator directly, skip off-screen
    val projIter = client.getProjectiles.values().iterator()
    while (projIter.hasNext) {
      val proj = projIter.next()
      val cx = Math.round(proj.getX).toInt
      val cy = Math.round(proj.getY).toInt
      if (cx >= cullingMinX && cx <= cullingMaxX && cy >= cullingMinY && cy <= cullingMaxY) {
        addEntity(cx, cy, () => drawProjectile(proj))
      }
    }

    // Remote players (with visual interpolation) — use Java iterator directly
    val remoteLerp = 1.0 - Math.exp(-18.0 * deltaSec)
    val playerIter = client.getPlayers.values().iterator()
    while (playerIter.hasNext) {
      val player = playerIter.next()
      if (!player.isDead) {
        val pos = player.getPosition
        val pid = player.getId
        val (rvx, rvy) = remoteVisualPositions.get(pid) match {
          case Some((prevX, prevY)) =>
            val nx = prevX + (pos.getX - prevX) * remoteLerp
            val ny = prevY + (pos.getY - prevY) * remoteLerp
            val sx = if (Math.abs(nx - pos.getX) < 0.01) pos.getX.toDouble else nx
            val sy = if (Math.abs(ny - pos.getY) < 0.01) pos.getY.toDouble else ny
            (sx, sy)
          case None =>
            (pos.getX.toDouble, pos.getY.toDouble)
        }
        remoteVisualPositions.put(pid, (rvx, rvy))
        addEntity(pos.getX, pos.getY, () => drawPlayer(player, rvx, rvy))
      }
    }

    // Clean up disconnected players — filterInPlace avoids intermediate collections
    remoteVisualPositions.filterInPlace((id, _) => client.getPlayers.containsKey(id))

    // Local player
    val localPos = client.getLocalPosition
    if (localDeathAnimActive) {
      addEntity(localPos.getX, localPos.getY, () => drawLocalDeath())
    } else if (!client.getIsDead) {
      addEntity(localPos.getX, localPos.getY, () => drawLocalPlayer())
    }

    entitiesByCell
  }
}
