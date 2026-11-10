package com.gridgame.server.game

import com.gridgame.common.model.Player
import com.gridgame.common.protocol._
import io.netty.channel.Channel
import java.io.File
import java.util.UUID

/**
 * A player of the match coming back to it after a reconnect — which takes nothing from the join
 * but the connection — or connecting to the match server playing it, and leaving it. They are sent
 * the match as it stands: everyone in it, the items, traps and changed tiles, their inventory and
 * the opening. Nobody joins a match in progress who was not seated in it when it started.
 */
trait JoinRequests { this: ClientHandler =>
  private[game] def handlePlayerJoin(packet: PlayerJoinPacket, tcpChannel: Channel): Boolean = {
    val playerId = packet.getPlayerId

    // Nobody is put into a match by asking: everyone in one was seated when it started
    // (MatchLauncher), and is sent it again here when their client comes back, or admitted by the
    // match server playing it (admit). A join from anyone else is no rejoin. It used to make them
    // a player of it as the join described them — wherever it said, at full health, as whichever
    // character it named — which let a player who had left the match (PLAYER_LEAVE) come back as
    // new; the server stopped sending such joins here, and the match itself has no such door now.
    val existing = registry.get(playerId)
    if (existing == null) {
      journal.refused(playerId, packet.getPlayerName, "join refused: not a player of this match")
      return false
    }

    // Which match this is, for a problem report the player sends about it, ahead of the world as a
    // match's start sends it
    outbox.sendVia(instance.matchInfoPacket(), tcpChannel)

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

    // The join places nobody, and a reconnected client counts its packets from zero again
    validator.resetMovementFence(playerId)

    println(s"Player rejoining: $playerId")
    // A client that has just (re)connected knows nothing of the match, so nothing it says about
    // itself is taken: where the player is and how much health they have stay the server's, and
    // it is told both below. It used to be put wherever it said and healed to full — a teleport
    // and a heal, a revival for the dead, whenever a client cared to send a join.
    registry.rebind(existing, tcpChannel)
    existing.updateHeartbeat()
    journal.note(existing, "rejoined: its client joined again and was sent the match")

    sendMatchTo(existing)
    true
  }

  /**
   * A player of the match connecting to the match server playing it (design/networking.md, *Match
   * servers*): seated when the match was sent there, they have had nothing of it yet. Sent it as a
   * rejoiner is — which match, the map, then their own join (where the server put them, as which
   * character, on which team) and everyone else's, the items, traps and tiles, their state, the
   * opening, and the clock — on `tcpChannel`, now theirs.
   */
  def admit(player: Player, tcpChannel: Channel): Unit = {
    val playerId = player.getId
    outbox.sendVia(instance.matchInfoPacket(), tcpChannel)
    val wf = instance.worldFile
    if (wf.nonEmpty) outbox.sendVia(new WorldInfoPacket(outbox.nextSeq(), new File(wf).getName), tcpChannel)
    validator.resetMovementFence(playerId)
    registry.rebind(player, tcpChannel)
    player.updateHeartbeat()
    journal.note(player, "connected to the match server and was sent the match")
    outbox.send(new PlayerJoinPacket(outbox.nextSeq(), playerId, player.getPosition, player.getColorRGB,
      player.getName, player.getHealth, player.getCharacterId, player.getTeamId), player)
    sendMatchTo(player)
    outbox.send(instance.timeSyncPacket(), player)
  }

  /** The match as it stands, to a player (re)joining it. */
  private def sendMatchTo(player: Player): Unit = {
    // Everyone else, the items, and tile modifications
    sendExistingPlayers(player.getId, player)
    sendExistingItems(player)
    instance.sendTrapsTo(player)
    sendInventoryContents(player.getId, player)
    instance.sendModifiedTiles(player)
    // Its state, with the count of server moves: a client that has lost count of them (a
    // restarted one starts from zero) picks it up, or all its updates would be taken as stale
    instance.sendStateToPlayer(player)
    instance.sendOpeningTo(player)
  }

  private[game] def handlePlayerLeave(packet: PlayerLeavePacket): Boolean = {
    val playerId = packet.getPlayerId
    val player = registry.get(playerId)

    if (player != null) {
      journal.note(player, "left the match: its client said so")
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
