package com.gridgame.server.game

import com.gridgame.common.model._
import com.gridgame.common.observability.Attrs
import com.gridgame.common.observability.Metrics
import com.gridgame.common.protocol._

import java.util.UUID

/** Telling everyone in the match: each packet serialized once and sent to every player, over the
  * transport its type names — at once, or buffered and flushed at the end of a tick. */
trait Broadcasts { this: GameInstance =>
  def broadcastToInstance(packet: Packet): Unit = {
    broadcastToInstanceExcluding(packet, null)
  }

  def broadcastToInstanceExcluding(packet: Packet, excludePlayerId: UUID): Unit = {
    val data = packet.serialize()
    val isTcp = packet.getType.tcp
    registry.forEachPlayer { player =>
      if (excludePlayerId == null || !player.getId.equals(excludePlayerId)) {
        try {
          outbox.sendRaw(data, isTcp, player)
        } catch {
          case _: Exception => // Skip disconnected player; don't crash broadcast loop
        }
      }
    }
  }

  /** Buffered broadcast: write without flushing. Call flushAllInstancePlayers() after the batch. */
  private[game] def broadcastBuffered(packet: Packet): Unit = {
    val data = packet.serialize()
    val isTcp = packet.getType.tcp
    registry.forEachPlayer { player =>
      try {
        outbox.sendRawBuffered(data, isTcp, player)
      } catch {
        case _: Exception =>
      }
    }
  }

  /** Flush all channels after a batch of buffered broadcasts. */
  private[game] def flushAllInstancePlayers(): Unit = {
    registry.forEachPlayer { player =>
      try { outbox.flush(player) } catch { case _: Exception => }
    }
    outbox.flushUdp()
  }

  def broadcastPlayerUpdate(packet: PlayerUpdatePacket): Unit = {
    broadcastToInstance(packet)
  }

  def broadcastItemPickup(item: Item, playerId: UUID): Unit = {
    val packet = new ItemPacket(
      outbox.nextSeq(),
      playerId,
      item.x, item.y,
      item.itemType.id,
      item.id,
      ItemAction.PICKUP
    )
    broadcastToInstance(packet)
    Metrics.itemsPickedUp.add(1L, Attrs.itemTypeAttrs(item.itemType.id))
  }

  def broadcastTileUpdate(playerId: UUID, x: Int, y: Int, tileId: Int): Unit = {
    modifiedTiles.put((x, y), tileId)
    val packet = new TileUpdatePacket(
      outbox.nextSeq(),
      playerId,
      x, y,
      tileId
    )
    broadcastToInstance(packet)
    Metrics.tilesModified.add(1L, io.opentelemetry.api.common.Attributes.of(Attrs.ItemType, "tile_" + tileId))
  }

  def sendModifiedTiles(player: Player): Unit = {
    val zeroUUID = new UUID(0L, 0L)
    modifiedTiles.forEach { (pos, tileId) =>
      val packet = new TileUpdatePacket(
        outbox.nextSeq(),
        zeroUUID,
        pos._1, pos._2,
        tileId
      )
      outbox.send(packet, player)
    }
  }
}
