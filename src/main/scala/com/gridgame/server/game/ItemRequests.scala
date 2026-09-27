package com.gridgame.server.game

import com.gridgame.common.Constants
import com.gridgame.common.model.Direction
import com.gridgame.common.model.ItemType
import com.gridgame.common.model.Player
import com.gridgame.common.model.Position
import com.gridgame.common.model.Teleport
import com.gridgame.common.model.Tile
import com.gridgame.common.observability.Attrs
import com.gridgame.common.observability.Metrics
import com.gridgame.common.protocol._

/** A player using an item from their inventory: a heart, a shield, a gem, a star's teleport or a
  * fence. A star or a fence the server refuses is given back. */
trait ItemRequests { this: ClientHandler =>
  private[game] def handleItemUpdate(packet: ItemPacket): Boolean = {
    if (packet.getAction == ItemAction.USE) {
      val playerId = packet.getPlayerId
      val player = registry.get(playerId)
      // The dead can't use items. The client doesn't offer it, but a use sent just before the
      // death reached it arrived after: a heart then brought the player back where they fell,
      // with a respawn still due to move them three seconds later.
      if (player != null && player.isDead) return false
      val removedItem = itemManager.removeFromInventory(playerId, packet.getItemId)
      if (removedItem == null) {
        System.err.println(s"ClientHandler: Item USE failed — item ${packet.getItemId} not in server inventory for ${playerId.toString.substring(0, 8)}")
        return false
      }
      Metrics.itemsUsed.add(1L, Attrs.itemTypeAttrs(removedItem.itemType.id))

      if (player != null) {
        removedItem.itemType match {
          case ItemType.Heart =>
            player.synchronized { player.setHealth(player.getMaxHealth) }
            broadcastState(player)
            println(s"ClientHandler: Player ${playerId.toString.substring(0, 8)} healed to full")

          case ItemType.Shield =>
            player.setShieldUntil(System.currentTimeMillis() + Constants.SHIELD_DURATION_MS)
            broadcastState(player)
            println(s"ClientHandler: Player ${playerId.toString.substring(0, 8)} shield activated")

          case ItemType.Gem =>
            player.setGemBoostUntil(System.currentTimeMillis() + Constants.GEM_DURATION_MS)
            broadcastState(player)
            println(s"ClientHandler: Player ${playerId.toString.substring(0, 8)} gem boost activated")

          case ItemType.Fence =>
            if (!placeFence(player, packet.getX, packet.getY)) {
              // Placement failed — restore item to inventory so player can retry
              itemManager.addToInventory(playerId, removedItem)
              // Notify client to re-add item to their local inventory
              val restorePacket = new ItemPacket(
                outbox.nextSeq(),
                playerId,
                removedItem.x, removedItem.y,
                removedItem.itemType.id,
                removedItem.id,
                ItemAction.INVENTORY
              )
              try {
                outbox.sendRaw(restorePacket.serialize(), true, player)
              } catch { case _: Exception => }
            }

          case ItemType.Star =>
            if (!teleportWithStar(player, packet.getX, packet.getY, packet.getSequenceNumber)) {
              // The client has already moved itself there, so give the star back and tell it
              // where the server has it
              itemManager.addToInventory(playerId, removedItem)
              val pos = player.getPosition
              System.err.println(s"ClientHandler: Star refused for ${playerId.toString.substring(0, 8)}: (${packet.getX},${packet.getY}) from (${pos.getX},${pos.getY})")
              val rejectPacket = new ItemPacket(
                outbox.nextSeq(),
                playerId,
                pos.getX, pos.getY,
                removedItem.itemType.id,
                removedItem.id,
                ItemAction.USE_REJECTED
              )
              try {
                outbox.sendRaw(rejectPacket.serialize(), true, player)
              } catch { case _: Exception => }
              // Anything the client sent from the landing cell is stale
              instance.moveByServer(player, pos)
            }
          case _ =>
        }
      }
    }
    false
  }

  /**
   * Star: move the player to the cell they aimed at. This item packet is the teleport; the
   * client picked the cell with the same Teleport rule, so the server refuses only when its world
   * or its copy of the position has moved on since. Returns false if refused.
   */
  private def teleportWithStar(player: Player, targetX: Int, targetY: Int, seqNum: Int): Boolean = {
    val w = instance.world
    if (w == null) return false
    val moved = player.synchronized {
      val pos = player.getPosition
      if (player.isFrozen || player.isRooted) {
        // A freeze or a root holds the player where the server has them — a step, a blink and a
        // dash are all held by it, and a star was the one way out
        false
      } else if (validator.isStaleMovement(player.getId, seqNum)) {
        // An update the client sent after using the star is already applied, so it's past here
        true
      } else if (!Teleport.isValidStarTarget(w, pos.getX, pos.getY, targetX, targetY)) {
        false
      } else {
        player.setPosition(new Position(targetX, targetY))
        // Updates the client sent before using the star still describe where it was
        validator.movementApplied(player.getId, seqNum)
        true
      }
    }
    // Everyone else sees the teleport now rather than on the player's next step
    if (moved) broadcastState(player)
    moved
  }

  /** Try to place a fence. Returns true if at least one tile was placed. */
  private def placeFence(player: Player, targetX: Int, targetY: Int): Boolean = {
    val w = instance.world
    if (w == null) return false

    // Bounds check
    if (targetX < 0 || targetX >= w.width || targetY < 0 || targetY >= w.height) {
      System.err.println(s"ClientHandler: Fence out of bounds ($targetX,$targetY)")
      return false
    }

    // Center tile must be walkable
    if (!w.isWalkable(targetX, targetY)) {
      System.err.println(s"ClientHandler: Fence center tile not walkable ($targetX,$targetY)")
      return false
    }

    // Distance check: fence must be placed near the player
    val from = player.getPosition
    val fdx = Math.abs(targetX - from.getX)
    val fdy = Math.abs(targetY - from.getY)
    if (fdx + fdy > Constants.FENCE_MAX_DISTANCE) return false

    // Nor over the opening divider: for those thirty seconds nothing of yours reaches the other
    // half, a fence included — and this one would still be standing there when the wall drops
    val divider = w.divider
    if (divider != null && divider.stops(from.getX, from.getY, targetX, targetY)) return false

    // Determine the perpendicular offsets based on player facing direction
    val (perpDx, perpDy) = player.getDirection match {
      case Direction.Up | Direction.Down    => (1, 0)
      case Direction.Left | Direction.Right => (0, 1)
    }

    // Place 3 tiles centered on the target position: center and ±1 perpendicular
    val positions = Seq(
      (targetX - perpDx, targetY - perpDy),
      (targetX, targetY),
      (targetX + perpDx, targetY + perpDy)
    )

    var placed = 0
    positions.foreach { case (tx, ty) =>
      if (tx >= 0 && tx < w.width && ty >= 0 && ty < w.height && w.isWalkable(tx, ty)) {
        if (w.setTile(tx, ty, Tile.Fence)) {
          instance.broadcastTileUpdate(player.getId, tx, ty, Tile.Fence.id)
          placed += 1
        }
      }
    }

    if (placed > 0) {
      println(s"ClientHandler: Player ${player.getId.toString.substring(0, 8)} placed $placed fence tiles at ($targetX, $targetY)")
    } else {
      System.err.println(s"ClientHandler: Fence placement failed at ($targetX,$targetY) — no walkable tiles")
    }
    placed > 0
  }
}
