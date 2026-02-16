package com.gridgame.client.render

import com.gridgame.client.GameClient
import com.gridgame.common.model.{Item, Player, Projectile}

import java.util.UUID
import scala.collection.mutable
import scala.jdk.CollectionConverters._

/**
 * Collects game entities (items, projectiles, players) by grid cell for depth-sorted rendering.
 * Used by both the JavaFX and OpenGL renderers to ensure correct isometric depth ordering.
 */
class EntityCollector {
  // Remote player visual interpolation state
  val remoteVisualPositions: mutable.Map[UUID, (Double, Double)] = mutable.Map.empty

  /** Represents a drawable entity at a grid cell position. */
  case class CellEntity(cellX: Int, cellY: Int, drawOrder: Int, draw: () => Unit)

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
    val entitiesByCell = mutable.Map.empty[(Int, Int), mutable.ArrayBuffer[() => Unit]]

    def addEntity(cx: Int, cy: Int, drawFn: () => Unit): Unit = {
      entitiesByCell.getOrElseUpdate((cx, cy), mutable.ArrayBuffer.empty) += drawFn
    }

    // Bounds for off-screen culling (pre-expanded by 2 cells for margin)
    val cullingMinX = minVisX - 2
    val cullingMaxX = maxVisX + 2
    val cullingMinY = minVisY - 2
    val cullingMaxY = maxVisY + 2

    // Items
    client.getItems.values().asScala.foreach { item =>
      addEntity(item.getCellX, item.getCellY, () => drawItem(item))
    }

    // Projectiles (skip off-screen ones)
    client.getProjectiles.values().asScala.foreach { proj =>
      val cx = Math.round(proj.getX).toInt
      val cy = Math.round(proj.getY).toInt
      if (cx >= cullingMinX && cx <= cullingMaxX && cy >= cullingMinY && cy <= cullingMaxY) {
        addEntity(cx, cy, () => drawProjectile(proj))
      }
    }

    // Remote players (with visual interpolation)
    val remoteLerp = 1.0 - Math.exp(-18.0 * deltaSec)
    client.getPlayers.values().asScala.filter(!_.isDead).foreach { player =>
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

    // Clean up disconnected players
    remoteVisualPositions.keys
      .filterNot(id => client.getPlayers.containsKey(id))
      .toSeq.foreach(remoteVisualPositions.remove)

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
