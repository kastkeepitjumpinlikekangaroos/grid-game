package com.gridgame.server

import com.gridgame.common.Constants
import com.gridgame.common.WorldRegistry
import com.gridgame.common.model.Player
import com.gridgame.common.model.Position
import com.gridgame.common.protocol._

import java.io.File
import java.util.UUID
import scala.jdk.CollectionConverters._

class LobbyHandler(server: GameServer, lobbyManager: LobbyManager) {

  def processLobbyAction(packet: LobbyActionPacket, player: Player): Unit = {
    val playerId = packet.getPlayerId

    packet.getAction match {
      case LobbyAction.CREATE =>
        handleCreate(playerId, player, packet)

      case LobbyAction.JOIN =>
        handleJoin(playerId, player, packet)

      case LobbyAction.LEAVE =>
        handleLeave(playerId, player)

      case LobbyAction.START =>
        handleStart(playerId, player)

      case LobbyAction.LIST_REQUEST =>
        handleListRequest(player)

      case LobbyAction.CONFIG_UPDATE =>
        handleConfigUpdate(playerId, packet)

      case _ =>
        println(s"LobbyHandler: Unknown action ${packet.getAction} from ${playerId.toString.substring(0, 8)}")
    }
  }

  private def handleCreate(playerId: UUID, player: Player, packet: LobbyActionPacket): Unit = {
    val name = if (packet.getLobbyName.isEmpty) s"${player.getName}'s Game" else packet.getLobbyName
    val mapIndex = packet.getMapIndex.toInt & 0xFF
    val duration = if (packet.getDurationMinutes <= 0) Constants.DEFAULT_GAME_DURATION_MIN else packet.getDurationMinutes.toInt
    val maxPlayers = if (packet.getMaxPlayers <= 0) Constants.MAX_LOBBY_PLAYERS else packet.getMaxPlayers.toInt

    val lobby = lobbyManager.createLobby(playerId, name, mapIndex, duration, maxPlayers)

    // Send JOINED response to creator
    val response = new LobbyActionPacket(
      server.getNextSequenceNumber, playerId, LobbyAction.JOINED, lobby.id,
      lobby.mapIndex.toByte, lobby.durationMinutes.toByte,
      lobby.playerCount.toByte, lobby.maxPlayers.toByte,
      lobby.status, lobby.name
    )
    server.sendPacketToPlayer(response, player)
  }

  private def handleJoin(playerId: UUID, player: Player, packet: LobbyActionPacket): Unit = {
    val lobby = lobbyManager.joinLobby(playerId, packet.getLobbyId)
    if (lobby == null) {
      println(s"LobbyHandler: Player ${playerId.toString.substring(0, 8)} failed to join lobby ${packet.getLobbyId}")
      return
    }

    // Send JOINED to the new player
    val response = new LobbyActionPacket(
      server.getNextSequenceNumber, playerId, LobbyAction.JOINED, lobby.id,
      lobby.mapIndex.toByte, lobby.durationMinutes.toByte,
      lobby.playerCount.toByte, lobby.maxPlayers.toByte,
      lobby.status, lobby.name
    )
    server.sendPacketToPlayer(response, player)

    // Broadcast PLAYER_JOINED to others in lobby
    val broadcast = new LobbyActionPacket(
      server.getNextSequenceNumber, playerId, LobbyAction.PLAYER_JOINED, lobby.id,
      lobby.mapIndex.toByte, lobby.durationMinutes.toByte,
      lobby.playerCount.toByte, lobby.maxPlayers.toByte,
      lobby.status, lobby.name
    )
    broadcastToLobby(lobby, broadcast, playerId)
  }

  private def handleLeave(playerId: UUID, player: Player): Unit = {
    val lobby = lobbyManager.leaveLobby(playerId)
    if (lobby == null) return

    if (lobby.isHost(playerId)) {
      // Host left - close lobby
      val closePacket = new LobbyActionPacket(
        server.getNextSequenceNumber, playerId, LobbyAction.LOBBY_CLOSED, lobby.id
      )
      broadcastToLobby(lobby, closePacket, null)
      lobbyManager.removeLobby(lobby.id)
    } else {
      // Notify remaining players
      val leftPacket = new LobbyActionPacket(
        server.getNextSequenceNumber, playerId, LobbyAction.PLAYER_LEFT, lobby.id,
        lobby.mapIndex.toByte, lobby.durationMinutes.toByte,
        lobby.playerCount.toByte, lobby.maxPlayers.toByte,
        lobby.status, lobby.name
      )
      broadcastToLobby(lobby, leftPacket, null)
    }
  }

  private def handleStart(playerId: UUID, player: Player): Unit = {
    val lobby = lobbyManager.getPlayerLobby(playerId)
    if (lobby == null || !lobby.isHost(playerId)) {
      println(s"LobbyHandler: Non-host ${playerId.toString.substring(0, 8)} tried to start game")
      return
    }

    if (lobby.status != LobbyStatus.WAITING) return

    lobby.status = LobbyStatus.IN_GAME

    // Resolve world file path
    val worldFileName = WorldRegistry.getFilename(lobby.mapIndex)
    val worldPath = resolveWorldPath("worlds/" + worldFileName)

    // Create GameInstance
    val instance = new GameInstance(lobby.id, worldPath, lobby.durationMinutes, server)
    lobby.gameInstance = instance
    server.registerGameInstance(lobby.id, instance)

    instance.start()

    // Register all lobby players in the instance's registry and kill tracker
    lobby.players.asScala.foreach { pid =>
      val p = server.getConnectedPlayer(pid)
      if (p != null) {
        val spawnPoint = instance.world.getValidSpawnPoint()
        val instancePlayer = new Player(pid, p.getName, spawnPoint, p.getColorRGB, Constants.MAX_HEALTH)
        instancePlayer.setTcpChannel(p.getTcpChannel)
        if (p.getUdpAddress != null) {
          instancePlayer.setUdpAddress(p.getUdpAddress)
        }
        instance.registry.add(instancePlayer)
        instance.killTracker.registerPlayer(pid)
      }
    }

    // Send GAME_STARTING to all lobby members
    val startingPacket = new LobbyActionPacket(
      server.getNextSequenceNumber, playerId, LobbyAction.GAME_STARTING, lobby.id,
      lobby.mapIndex.toByte, lobby.durationMinutes.toByte,
      lobby.playerCount.toByte, lobby.maxPlayers.toByte,
      lobby.status, lobby.name
    )
    broadcastToLobby(lobby, startingPacket, null)

    // Send WorldInfo to each player
    lobby.players.asScala.foreach { pid =>
      val p = server.getConnectedPlayer(pid)
      if (p != null) {
        val worldInfoPacket = new WorldInfoPacket(server.getNextSequenceNumber, worldFileName)
        server.sendPacketToPlayer(worldInfoPacket, p)
      }
    }

    println(s"LobbyHandler: Game started for lobby ${lobby.id} on map $worldFileName ($worldPath)")
  }

  private def handleListRequest(player: Player): Unit = {
    val lobbies = lobbyManager.getActiveLobbies

    lobbies.foreach { lobby =>
      val entry = new LobbyActionPacket(
        server.getNextSequenceNumber, player.getId, LobbyAction.LIST_ENTRY, lobby.id,
        lobby.mapIndex.toByte, lobby.durationMinutes.toByte,
        lobby.playerCount.toByte, lobby.maxPlayers.toByte,
        lobby.status, lobby.name
      )
      server.sendPacketToPlayer(entry, player)
    }

    val end = new LobbyActionPacket(
      server.getNextSequenceNumber, player.getId, LobbyAction.LIST_END
    )
    server.sendPacketToPlayer(end, player)
  }

  private def handleConfigUpdate(playerId: UUID, packet: LobbyActionPacket): Unit = {
    val lobby = lobbyManager.getPlayerLobby(playerId)
    if (lobby == null || !lobby.isHost(playerId)) return
    if (lobby.status != LobbyStatus.WAITING) return

    lobby.mapIndex = packet.getMapIndex.toInt & 0xFF
    lobby.durationMinutes = if (packet.getDurationMinutes <= 0) Constants.DEFAULT_GAME_DURATION_MIN else packet.getDurationMinutes.toInt

    // Broadcast config update to all lobby members
    val update = new LobbyActionPacket(
      server.getNextSequenceNumber, playerId, LobbyAction.CONFIG_UPDATE, lobby.id,
      lobby.mapIndex.toByte, lobby.durationMinutes.toByte,
      lobby.playerCount.toByte, lobby.maxPlayers.toByte,
      lobby.status, lobby.name
    )
    broadcastToLobby(lobby, update, null)
  }

  private def broadcastToLobby(lobby: Lobby, packet: Packet, excludePlayerId: UUID): Unit = {
    lobby.players.asScala.foreach { pid =>
      if (excludePlayerId == null || !pid.equals(excludePlayerId)) {
        val p = server.getConnectedPlayer(pid)
        if (p != null) {
          server.sendPacketToPlayer(packet, p)
        }
      }
    }
  }

  private def resolveWorldPath(worldFile: String): String = {
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
