package com.gridgame.server.game

import com.gridgame.common.model.CharacterDef
import com.gridgame.common.model.Player
import com.gridgame.common.observability.Attrs
import com.gridgame.common.observability.Metrics
import com.gridgame.common.protocol._
import io.netty.channel.Channel
import java.io.File
import java.util.UUID

/**
 * A player joining the match — the first time, when it starts, or again after a reconnect, which
 * takes nothing from the join but the connection — and leaving it. A joiner is sent the match as it
 * stands: everyone in it, the items, traps and changed tiles, their inventory and the opening.
 */
trait JoinRequests { this: ClientHandler =>
  private[game] def handlePlayerJoin(packet: PlayerJoinPacket, tcpChannel: Channel): Boolean = {
    val playerId = packet.getPlayerId

    // Send world info to the joining player via TCP
    val wf = instance.worldFile
    if (wf.nonEmpty) {
      val worldFileName = new File(wf).getName
      val worldInfoPacket = new WorldInfoPacket(outbox.nextSeq(), worldFileName)
      println(s"Sending world info to client: $worldFileName")
      outbox.sendVia(worldInfoPacket, tcpChannel)
    } else {
      println("No world file configured on server")
    }

    // The join places the player, and a reconnected client counts its packets from zero again
    validator.resetMovementFence(playerId)

    if (registry.contains(playerId)) {
      println(s"Player rejoining: $playerId")
      val existing = registry.get(playerId)
      // A client that has just (re)connected knows nothing of the match, so nothing it says about
      // itself is taken: where the player is and how much health they have stay the server's, and
      // it is told both below. It used to be put wherever it said and healed to full — a teleport
      // and a heal, a revival for the dead, whenever a client cared to send a join.
      registry.rebind(existing, tcpChannel)
      existing.updateHeartbeat()

      // Send existing players, items, and tile modifications to rejoining player
      sendExistingPlayers(playerId, existing)
      sendExistingItems(existing)
      instance.sendTrapsTo(existing)
      sendInventoryContents(playerId, existing)
      instance.sendModifiedTiles(existing)
      // Its state, with the count of server moves: a client that has lost count of them (a
      // restarted one starts from zero) picks it up, or all its updates would be taken as stale
      instance.sendStateToPlayer(existing)
      instance.sendOpeningTo(existing)

      true
    } else {
      val charDef = com.gridgame.common.model.CharacterDef.get(packet.getCharacterId)
      if (charDef == null) {
        System.err.println(s"ClientHandler: Player ${playerId.toString.substring(0, 8)} invalid character ID: ${packet.getCharacterId}")
        Metrics.validationFailed.add(1L, Attrs.VfCharacter)
        return false
      }
      // gridgame.character.played is incremented from ClientRegistry.add to cover all join paths
      // Validate new player join position against world bounds and walkability
      val joinPos = packet.getPosition
      val world = instance.world
      // A joiner claiming a cell in the other team's half is put in their own (the divider), as
      // one claiming a wall is put on a spawn point
      val sideOk = world == null || world.divider == null ||
        world.divider.side(joinPos.getX, joinPos.getY) == com.gridgame.common.model.TeamDivider.sideOfTeam(packet.getTeamId)
      val validPos = if (world != null && joinPos.getX >= 0 && joinPos.getX < world.width &&
          joinPos.getY >= 0 && joinPos.getY < world.height && world.isWalkable(joinPos.getX, joinPos.getY) && sideOk) {
        joinPos
      } else if (world != null) {
        instance.spawnFor(packet.getTeamId, Set.empty)
      } else {
        joinPos // No world loaded yet — accept client position
      }
      val player = new Player(playerId, packet.getPlayerName, validPos, packet.getColorRGB, charDef.maxHealth, charDef.maxHealth)
      player.setCharacterId(packet.getCharacterId)
      player.setTcpChannel(tcpChannel)

      registry.add(player)

      println(s"Player joined: ${playerId.toString.substring(0, 8)} ('${packet.getPlayerName}') at ${packet.getPosition} with health ${player.getHealth}")

      // Send existing players, items, and tile modifications to new player
      sendExistingPlayers(playerId, player)
      sendExistingItems(player)
      instance.sendTrapsTo(player)
      sendInventoryContents(playerId, player)
      instance.sendModifiedTiles(player)
      instance.sendOpeningTo(player)

      true
    }
  }

  private[game] def handlePlayerLeave(packet: PlayerLeavePacket): Boolean = {
    val playerId = packet.getPlayerId
    val player = registry.get(playerId)

    if (player != null) {
      registry.remove(playerId)
      itemManager.clearInventory(playerId)
      clearTrapsOf(playerId)
      validator.removePlayer(playerId)
      lastCorrectionAt.remove(playerId)
      refusedSince.remove(playerId)
      println(s"Player left: ${playerId.toString.substring(0, 8)} ('${player.getName}')")
    }

    true
  }

  private def sendExistingPlayers(joiningPlayerId: UUID, joiningPlayer: Player): Unit = {
    import scala.jdk.CollectionConverters._
    registry.getAll.asScala.foreach { existing =>
      if (!existing.getId.equals(joiningPlayerId)) {
        // With their team: sent without it, every other player came back to a rejoining client
        // as team 0, allies drawn as enemies, until each of them next moved
        val packet = new PlayerJoinPacket(
          outbox.nextSeq(),
          existing.getId,
          existing.getPosition,
          existing.getColorRGB,
          existing.getName,
          existing.getHealth,
          existing.getCharacterId,
          existing.getTeamId
        )
        outbox.send(packet, joiningPlayer)
      }
    }
  }

  private def sendExistingItems(player: Player): Unit = {
    val zeroUUID = new UUID(0L, 0L)
    var sent = false
    itemManager.getAll.foreach { item =>
      val packet = new ItemPacket(
        outbox.nextSeq(),
        zeroUUID,
        item.x, item.y,
        item.itemType.id,
        item.id,
        ItemAction.SPAWN
      )
      val data = packet.serialize()
      outbox.sendRawBuffered(data, true, player)
      sent = true
    }
    if (sent) outbox.flush(player)
  }

  private def sendInventoryContents(playerId: UUID, player: Player): Unit = {
    itemManager.getInventory(playerId).foreach { item =>
      val packet = new ItemPacket(
        outbox.nextSeq(),
        playerId,
        item.x, item.y,
        item.itemType.id,
        item.id,
        ItemAction.INVENTORY
      )
      outbox.send(packet, player)
    }
  }
}
