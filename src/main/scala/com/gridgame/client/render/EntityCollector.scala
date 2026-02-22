package com.gridgame.client.render

import com.gridgame.client.GameClient
import com.gridgame.common.model.{Item, Player, Projectile}

import java.util.UUID
import scala.collection.mutable

/**
 * Collects game entities (items, projectiles, players) by grid cell for depth-sorted rendering.
 * Uses typed entity entries instead of closures to avoid per-entity lambda allocation.
 */
class EntityCollector {
  import EntityCollector._

  // Remote player visual interpolation state — uses java.util.HashMap to avoid
  // Scala Option wrapping on get(), and reusable double[2] arrays to avoid Tuple2 allocation.
  val remoteVisualPositions: java.util.HashMap[UUID, Array[Double]] = new java.util.HashMap()

  // Mutable output fields for getRemoteVisualPos — avoids tuple allocation on lookup
  private var _rvx: Double = 0.0
  private var _rvy: Double = 0.0
  def lastRVX: Double = _rvx
  def lastRVY: Double = _rvy

  /** Look up remote visual position. Returns true if found, result in lastRVX/lastRVY. */
  def getRemoteVisualPos(pid: UUID): Boolean = {
    val arr = remoteVisualPositions.get(pid)
    if (arr != null) {
      _rvx = arr(0)
      _rvy = arr(1)
      true
    } else false
  }

  // Pre-allocated map reused each frame (cleared instead of recreated)
  private val entitiesByCell = mutable.Map.empty[Long, mutable.ArrayBuffer[CellEntry]]
  // Buffer pool for ArrayBuffer reuse
  private val bufferPool = mutable.ArrayBuffer.empty[mutable.ArrayBuffer[CellEntry]]

  private def acquireBuffer(): mutable.ArrayBuffer[CellEntry] = {
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

  @inline def cellKey(cx: Int, cy: Int): Long =
    EntityCollector.cellKey(cx, cy)

  private def addEntity(cx: Int, cy: Int, entry: CellEntry): Unit = {
    val key = cellKey(cx, cy)
    val buf = entitiesByCell.getOrElse(key, null)
    if (buf != null) {
      buf += entry
    } else {
      val newBuf = acquireBuffer()
      newBuf += entry
      entitiesByCell(key) = newBuf
    }
  }

  def collect(
    client: GameClient,
    deltaSec: Double,
    localVisualX: Double,
    localVisualY: Double,
    localDeathAnimActive: Boolean,
    minVisX: Int = -1000000,
    maxVisX: Int = 1000000,
    minVisY: Int = -1000000,
    maxVisY: Int = 1000000
  ): mutable.Map[Long, mutable.ArrayBuffer[CellEntry]] = {
    // Return old buffers to pool and clear the map
    releaseBuffers()

    // Bounds for off-screen culling (pre-expanded by 2 cells for margin)
    val cullingMinX = minVisX - 2
    val cullingMaxX = maxVisX + 2
    val cullingMinY = minVisY - 2
    val cullingMaxY = maxVisY + 2

    // Items — use Java iterator directly, skip off-screen
    val itemIter = client.getItems.values().iterator()
    while (itemIter.hasNext) {
      val item = itemIter.next()
      val ix = item.getCellX; val iy = item.getCellY
      if (ix >= cullingMinX && ix <= cullingMaxX && iy >= cullingMinY && iy <= cullingMaxY) {
        addEntity(ix, iy, ItemEntry(item))
      }
    }

    // Projectiles — use Java iterator directly, skip off-screen
    val projIter = client.getProjectiles.values().iterator()
    while (projIter.hasNext) {
      val proj = projIter.next()
      val cx = Math.round(proj.getX).toInt
      val cy = Math.round(proj.getY).toInt
      if (cx >= cullingMinX && cx <= cullingMaxX && cy >= cullingMinY && cy <= cullingMaxY) {
        addEntity(cx, cy, ProjectileEntry(proj))
      }
    }

    // Remote players (with visual interpolation + velocity extrapolation) — use Java iterator directly
    // Per-player array layout: [visualX, visualY, lastDiscreteX, lastDiscreteY, velX, velY, lastUpdateTimeMs]
    val remoteLerp = 1.0 - Math.exp(-18.0 * deltaSec)
    val nowMs = System.currentTimeMillis().toDouble
    val playerIter = client.getPlayers.values().iterator()
    while (playerIter.hasNext) {
      val player = playerIter.next()
      if (!player.isDead) {
        val pos = player.getPosition
        val pid = player.getId
        val posX = pos.getX.toDouble
        val posY = pos.getY.toDouble
        val existing = remoteVisualPositions.get(pid)

        var rvx: Double = posX
        var rvy: Double = posY

        if (existing != null) {
          val lastDiscX = existing(2)
          val lastDiscY = existing(3)

          // Detect position change (new server update arrived)
          if (posX != lastDiscX || posY != lastDiscY) {
            val dtSec = (nowMs - existing(6)) / 1000.0
            val dx = posX - lastDiscX
            val dy = posY - lastDiscY

            if (Math.abs(dx) + Math.abs(dy) > 3 || dtSec <= 0.001) {
              // Teleport: snap visual position, zero velocity
              existing(0) = posX
              existing(1) = posY
              existing(4) = 0.0
              existing(5) = 0.0
            } else {
              // Compute velocity from consecutive position updates
              existing(4) = dx / dtSec
              existing(5) = dy / dtSec
            }
            existing(2) = posX
            existing(3) = posY
            existing(6) = nowMs
          }

          // Extrapolate target position to bridge packet gaps, then fade back when idle.
          // This predicts where the player likely is during UDP packet loss,
          // preventing the visual snap that occurs when the next packet arrives.
          val msSinceUpdate = nowMs - existing(6)
          val MaxExtrapMs = 75.0 // ~1.5 packet intervals at 50ms move rate
          val extrapSec = if (msSinceUpdate <= MaxExtrapMs) {
            msSinceUpdate / 1000.0
          } else {
            // Fade extrapolation back to zero over the next interval to avoid overshoot when player stops
            val fade = Math.max(0.0, 1.0 - (msSinceUpdate - MaxExtrapMs) / MaxExtrapMs)
            (MaxExtrapMs / 1000.0) * fade
          }
          val targetX = existing(2) + existing(4) * extrapSec
          val targetY = existing(3) + existing(5) * extrapSec

          // Exponential smooth toward extrapolated target
          val prevX = existing(0)
          val prevY = existing(1)
          val nx = prevX + (targetX - prevX) * remoteLerp
          val ny = prevY + (targetY - prevY) * remoteLerp

          // Snap to discrete position when close and extrapolation has faded
          rvx = if (extrapSec < 0.001 && Math.abs(nx - posX) < 0.01) posX else nx
          rvy = if (extrapSec < 0.001 && Math.abs(ny - posY) < 0.01) posY else ny

          existing(0) = rvx
          existing(1) = rvy
        } else {
          remoteVisualPositions.put(pid, Array(posX, posY, posX, posY, 0.0, 0.0, nowMs))
        }
        addEntity(pos.getX, pos.getY, PlayerEntry(player, rvx, rvy))
      }
    }

    // Clean up disconnected players — iterate and remove entries not in current players
    val cleanIter = remoteVisualPositions.entrySet().iterator()
    while (cleanIter.hasNext) {
      val entry = cleanIter.next()
      if (!client.getPlayers.containsKey(entry.getKey)) {
        cleanIter.remove()
      }
    }

    // Local player
    val localPos = client.getLocalPosition
    if (localDeathAnimActive) {
      addEntity(localPos.getX, localPos.getY, LocalDeathEntry)
    } else if (!client.getIsDead) {
      addEntity(localPos.getX, localPos.getY, LocalPlayerEntry)
    }

    entitiesByCell
  }
}

object EntityCollector {
  @inline def cellKey(cx: Int, cy: Int): Long =
    (cx.toLong << 32) | (cy & 0xFFFFFFFFL)

  /** Typed entity entries — avoids per-entity closure allocation */
  sealed trait CellEntry
  final case class ItemEntry(item: Item) extends CellEntry
  final case class ProjectileEntry(proj: Projectile) extends CellEntry
  final case class PlayerEntry(player: Player, vx: Double, vy: Double) extends CellEntry
  case object LocalPlayerEntry extends CellEntry
  case object LocalDeathEntry extends CellEntry
}
