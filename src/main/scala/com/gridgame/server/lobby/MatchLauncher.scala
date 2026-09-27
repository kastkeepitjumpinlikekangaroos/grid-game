package com.gridgame.server.lobby

import com.gridgame.common.WorldRegistry
import com.gridgame.common.model.CharacterDef
import com.gridgame.common.model.Player
import com.gridgame.common.model.TeamAssignment
import com.gridgame.common.protocol._
import com.gridgame.server.bots.BotController
import com.gridgame.server.game.GameInstance

import java.io.File
import scala.jdk.CollectionConverters._

/**
 * Starts a lobby's match, the same way for a casual lobby, practice and every ranked queue: the
 * world loaded, the teams dealt (Teams only), everyone placed — each team in its own half — with
 * the bots seated after the humans, every client told (the start, the world, then where everyone
 * is), and the match's clocks and the bots started.
 *
 * The caller has the lobby ready first: its players and their characters, its bots, its mode, and
 * its status set to IN_GAME.
 */
final class MatchLauncher(host: LobbyHost) {

  /** @param withBots whether the match gets a BotController (a duel has no seats for bots) */
  def launch(lobby: Lobby, withBots: Boolean = true): GameInstance = {
    // Resolve world file path
    val worldFileName = WorldRegistry.getFilename(lobby.mapIndex)
    val worldPath = MatchLauncher.resolveWorldPath("worlds/" + worldFileName)

    // Create GameInstance and load world (needed for spawn points)
    val instance = new GameInstance(lobby.id, worldPath, lobby.durationMinutes, host)
    instance.gameMode = lobby.gameMode
    instance.isPractice = lobby.isPractice
    instance.loadWorld()
    lobby.gameInstance = instance
    host.registerGameInstance(lobby.id, instance)

    // Assign teams if in Teams mode, in the order the lobby room previewed them
    if (lobby.gameMode == 1) {
      val humans = lobby.players.asScala.toSeq.filter(pid => host.getConnectedPlayer(pid) != null)
      TeamAssignment.assign(humans, lobby.botManager.getBots.map(_.id)).foreach { case (pid, teamId) =>
        instance.teamAssignments.put(pid, teamId)
      }
    }

    // Register all lobby players in the instance's registry and kill tracker
    // (must happen before instance.start() so initial item spawns reach players)
    var occupiedSpawns = Set.empty[(Int, Int)]
    lobby.players.asScala.foreach { pid =>
      val p = host.getConnectedPlayer(pid)
      if (p != null) {
        // In Teams, each team starts in its own half of the map (TeamDivider)
        val playerTeamId = instance.teamAssignments.getOrDefault(pid, 0.toByte)
        val spawnPoint = instance.spawnFor(playerTeamId, occupiedSpawns)
        occupiedSpawns += ((spawnPoint.getX, spawnPoint.getY))
        val charId = lobby.getCharacter(pid)
        val charDef = CharacterDef.get(charId)
        val instancePlayer = new Player(pid, p.getName, spawnPoint, p.getColorRGB, charDef.maxHealth, charDef.maxHealth)
        instancePlayer.setCharacterId(charId)
        instancePlayer.setTcpChannel(p.getTcpChannel)
        if (p.getUdpAddress != null) {
          instancePlayer.setUdpAddress(p.getUdpAddress)
        }
        instancePlayer.setTeamId(playerTeamId)
        instance.registry.add(instancePlayer)
        instance.killTracker.registerPlayer(pid)
      }
    }

    // Register bots from the lobby's BotManager
    require(withBots || lobby.botManager.botCount == 0, "a match without a BotController can't seat bots")
    val botController = if (withBots) new BotController(instance, lobby.isPractice) else null
    lobby.botManager.getBots.foreach { botSlot =>
      val botTeamId = instance.teamAssignments.getOrDefault(botSlot.id, 0.toByte)
      val spawnPoint = instance.spawnFor(botTeamId, occupiedSpawns)
      occupiedSpawns += ((spawnPoint.getX, spawnPoint.getY))
      val charDef = CharacterDef.get(botSlot.characterId)
      val colorRGB = Player.generateColorFromUUID(botSlot.id)
      val botPlayer = new Player(botSlot.id, botSlot.name, spawnPoint, colorRGB, charDef.maxHealth, charDef.maxHealth)
      botPlayer.setCharacterId(botSlot.characterId)
      botPlayer.setTeamId(botTeamId)
      instance.registry.add(botPlayer)
      instance.killTracker.registerPlayer(botSlot.id)
      botController.addBotId(botSlot.id)
    }
    instance.botController = botController

    // Send GAME_STARTING to all lobby members
    val startingPacket = new LobbyActionPacket(
      host.outbox.nextSeq(), lobby.hostId, LobbyAction.GAME_STARTING, lobby.id,
      lobby.mapIndex.toByte, lobby.durationMinutes.toByte,
      lobby.playerCount.toByte, lobby.maxPlayers.toByte,
      lobby.status, lobby.name
    )
    lobby.players.asScala.foreach { pid =>
      val p = host.getConnectedPlayer(pid)
      if (p != null) host.outbox.send(startingPacket, p)
    }

    // Send WorldInfo to each player
    lobby.players.asScala.foreach { pid =>
      val p = host.getConnectedPlayer(pid)
      if (p != null) {
        val worldInfoPacket = new WorldInfoPacket(host.outbox.nextSeq(), worldFileName)
        host.outbox.send(worldInfoPacket, p)
      }
    }

    // Start the instance (spawns initial items, begins projectile/timer ticks)
    // Done after players are registered and notified so item spawns reach everyone
    instance.start()

    // Broadcast join packets for all players (human + bot) so each client
    // knows every player's server-assigned spawn position
    lobby.players.asScala.foreach { pid =>
      val instancePlayer = instance.registry.get(pid)
      if (instancePlayer != null) {
        val joinPacket = new PlayerJoinPacket(
          host.outbox.nextSeq(),
          pid,
          instancePlayer.getPosition,
          instancePlayer.getColorRGB,
          instancePlayer.getName,
          instancePlayer.getHealth,
          instancePlayer.getCharacterId,
          instancePlayer.getTeamId
        )
        instance.broadcastToInstance(joinPacket)
      }
    }
    lobby.botManager.getBots.foreach { botSlot =>
      val botPlayer = instance.registry.get(botSlot.id)
      if (botPlayer != null) {
        val joinPacket = new PlayerJoinPacket(
          host.outbox.nextSeq(),
          botSlot.id,
          botPlayer.getPosition,
          botPlayer.getColorRGB,
          botSlot.name,
          botPlayer.getHealth,
          botSlot.characterId,
          botPlayer.getTeamId
        )
        instance.broadcastToInstance(joinPacket)
      }
    }

    // Start bot controller if there are bots
    if (lobby.botManager.botCount > 0) {
      botController.start()
    }
    instance
  }
}

object MatchLauncher {
  /** A world file, relative to the server's working directory or, under `bazel run`, the directory
    * it was run from. */
  def resolveWorldPath(worldFile: String): String = {
    val direct = new File(worldFile)
    if (direct.exists()) return direct.getAbsolutePath

    val buildWorkDir = System.getenv("BUILD_WORKING_DIRECTORY")
    if (buildWorkDir != null) {
      val fromWorkDir = new File(buildWorkDir, worldFile)
      if (fromWorkDir.exists()) return fromWorkDir.getAbsolutePath
    }

    worldFile
  }
}
