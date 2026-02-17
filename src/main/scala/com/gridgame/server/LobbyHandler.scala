package com.gridgame.server

import com.gridgame.common.Constants
import com.gridgame.common.WorldRegistry
import com.gridgame.common.model.Player
import com.gridgame.common.model.Position
import com.gridgame.common.protocol._

import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import scala.jdk.CollectionConverters._

class LobbyHandler(server: GameServer, lobbyManager: LobbyManager) {
  private val lastListRequestTime = new ConcurrentHashMap[UUID, java.lang.Long]()
  private val LIST_REQUEST_COOLDOWN_MS = 2000L
  private val lastCreateTime = new ConcurrentHashMap[UUID, java.lang.Long]()
  private val CREATE_COOLDOWN_MS = 5000L

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

      case LobbyAction.CHARACTER_SELECT =>
        handleCharacterSelect(playerId, packet)

      case LobbyAction.ADD_BOT =>
        handleAddBot(playerId, player)

      case LobbyAction.REMOVE_BOT =>
        handleRemoveBot(playerId, player)

      case _ =>
        println(s"LobbyHandler: Unknown action ${packet.getAction} from ${playerId.toString.substring(0, 8)}")
    }
  }

  private def sanitizeName(raw: String): String = {
    // Strip control characters, zero-width chars, RTL marks, and non-BMP characters
    raw.filter(c => !c.isControl && c >= 0x20 && c < 0xD800 &&
      c != '\u200B' && c != '\u200C' && c != '\u200D' && c != '\uFEFF' && // zero-width
      c != '\u200E' && c != '\u200F' && c != '\u202A' && c != '\u202B' && c != '\u202C' // RTL/LTR
    ).trim
  }

  private def handleCreate(playerId: UUID, player: Player, packet: LobbyActionPacket): Unit = {
    // Prevent creating a lobby while already in one
    if (lobbyManager.getPlayerLobby(playerId) != null) return

    // Rate limit lobby creation (5s cooldown)
    val now = System.currentTimeMillis()
    val lastCreate = lastCreateTime.get(playerId)
    if (lastCreate != null && now - lastCreate < CREATE_COOLDOWN_MS) return
    lastCreateTime.put(playerId, now)

    val rawName = if (packet.getLobbyName.isEmpty) s"${player.getName}'s Game" else packet.getLobbyName
    val name = sanitizeName(rawName)
    if (name.isEmpty) return
    val rawMapIndex = packet.getMapIndex.toInt & 0xFF
    val mapIndex = if (rawMapIndex >= 0 && rawMapIndex < com.gridgame.common.WorldRegistry.size) rawMapIndex else 0
    val duration = Math.max(1, Math.min(30, if (packet.getDurationMinutes <= 0) Constants.DEFAULT_GAME_DURATION_MIN else packet.getDurationMinutes.toInt))
    val maxPlayers = Math.max(2, Math.min(Constants.MAX_LOBBY_PLAYERS, if (packet.getMaxPlayers <= 0) Constants.MAX_LOBBY_PLAYERS else packet.getMaxPlayers.toInt))

    val lobby = lobbyManager.createLobby(playerId, name, mapIndex, duration, maxPlayers)

    // Send JOINED response to creator
    val response = new LobbyActionPacket(
      server.getNextSequenceNumber, playerId, Packet.getCurrentTimestamp,
      LobbyAction.JOINED, lobby.id,
      lobby.mapIndex.toByte, lobby.durationMinutes.toByte,
      lobby.playerCount.toByte, lobby.maxPlayers.toByte,
      lobby.status, lobby.name, 0.toByte, lobby.gameMode, lobby.teamSize.toByte
    )
    server.sendPacketToPlayer(response, player)
  }

  private def handleJoin(playerId: UUID, player: Player, packet: LobbyActionPacket): Unit = {
    // Prevent joining if already in a lobby
    if (lobbyManager.getPlayerLobby(playerId) != null) return

    val lobby = lobbyManager.joinLobby(playerId, packet.getLobbyId)
    if (lobby == null) {
      println(s"LobbyHandler: Player ${playerId.toString.substring(0, 8)} failed to join lobby ${packet.getLobbyId}")
      return
    }

    // Send JOINED to the new player
    val response = new LobbyActionPacket(
      server.getNextSequenceNumber, playerId, Packet.getCurrentTimestamp,
      LobbyAction.JOINED, lobby.id,
      lobby.mapIndex.toByte, lobby.durationMinutes.toByte,
      lobby.playerCount.toByte, lobby.maxPlayers.toByte,
      lobby.status, lobby.name, 0.toByte, lobby.gameMode, lobby.teamSize.toByte
    )
    server.sendPacketToPlayer(response, player)

    // Broadcast PLAYER_JOINED to others in lobby (lobbyName field carries the member name)
    val joinerName = player.getName
    val broadcast = new LobbyActionPacket(
      server.getNextSequenceNumber, playerId, LobbyAction.PLAYER_JOINED, lobby.id,
      lobby.mapIndex.toByte, lobby.durationMinutes.toByte,
      lobby.playerCount.toByte, lobby.maxPlayers.toByte,
      lobby.status, joinerName
    )
    broadcastToLobby(lobby, broadcast, playerId)

    // Send existing players to the new joiner
    lobby.players.asScala.foreach { existingPid =>
      if (!existingPid.equals(playerId)) {
        val ep = server.getConnectedPlayer(existingPid)
        if (ep != null) {
          val memberPacket = new LobbyActionPacket(
            server.getNextSequenceNumber, existingPid, LobbyAction.PLAYER_JOINED, lobby.id,
            lobby.mapIndex.toByte, lobby.durationMinutes.toByte,
            lobby.playerCount.toByte, lobby.maxPlayers.toByte,
            lobby.status, ep.getName
          )
          server.sendPacketToPlayer(memberPacket, player)
        }
      }
    }
    // Send existing bots to the new joiner
    lobby.botManager.getBots.foreach { botSlot =>
      val botPacket = new LobbyActionPacket(
        server.getNextSequenceNumber, botSlot.id, LobbyAction.PLAYER_JOINED, lobby.id,
        lobby.mapIndex.toByte, lobby.durationMinutes.toByte,
        lobby.playerCount.toByte, lobby.maxPlayers.toByte,
        lobby.status, botSlot.name
      )
      server.sendPacketToPlayer(botPacket, player)
    }
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
      // Notify remaining players (lobbyName field carries the leaver's name)
      val leaverPlayer = server.getConnectedPlayer(playerId)
      val leaverName = if (leaverPlayer != null) leaverPlayer.getName else "Player"
      val leftPacket = new LobbyActionPacket(
        server.getNextSequenceNumber, playerId, LobbyAction.PLAYER_LEFT, lobby.id,
        lobby.mapIndex.toByte, lobby.durationMinutes.toByte,
        lobby.playerCount.toByte, lobby.maxPlayers.toByte,
        lobby.status, leaverName
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

    // Verify at least 1 connected human player before starting
    val connectedCount = lobby.players.asScala.count(pid => server.getConnectedPlayer(pid) != null)
    if (connectedCount == 0) {
      println(s"LobbyHandler: Cannot start game with 0 connected players")
      return
    }

    // Atomic check-and-set to prevent double start from rapid packets
    lobby.synchronized {
      if (lobby.status != LobbyStatus.WAITING) return
      lobby.status = LobbyStatus.IN_GAME
    }

    // Set match type for casual games: 0=Casual FFA, 1=Casual Teams
    lobby.matchType = lobby.gameMode

    // Resolve world file path
    val worldFileName = WorldRegistry.getFilename(lobby.mapIndex)
    val worldPath = resolveWorldPath("worlds/" + worldFileName)

    // Create GameInstance and load world (needed for spawn points)
    val instance = new GameInstance(lobby.id, worldPath, lobby.durationMinutes, server)
    instance.gameMode = lobby.gameMode
    instance.loadWorld()
    lobby.gameInstance = instance
    server.registerGameInstance(lobby.id, instance)

    // Assign teams if in Teams mode (deterministic order matches lobby roster preview)
    if (lobby.gameMode == 1) {
      val allPlayerIds = lobby.players.asScala.toSeq ++ lobby.botManager.getBots.map(_.id)
      allPlayerIds.zipWithIndex.foreach { case (pid, index) =>
        val teamId = (index % 2 + 1).toByte
        instance.teamAssignments.put(pid, teamId)
      }
    }

    // Register all lobby players in the instance's registry and kill tracker
    // (must happen before instance.start() so initial item spawns reach players)
    var occupiedSpawns = Set.empty[(Int, Int)]
    lobby.players.asScala.foreach { pid =>
      val p = server.getConnectedPlayer(pid)
      if (p != null) {
        val spawnPoint = instance.world.getValidSpawnPoint(occupiedSpawns)
        occupiedSpawns += ((spawnPoint.getX, spawnPoint.getY))
        val charId = lobby.getCharacter(pid)
        val charDef = com.gridgame.common.model.CharacterDef.get(charId)
        val instancePlayer = new Player(pid, p.getName, spawnPoint, p.getColorRGB, charDef.maxHealth, charDef.maxHealth)
        instancePlayer.setCharacterId(charId)
        instancePlayer.setTcpChannel(p.getTcpChannel)
        if (p.getUdpAddress != null) {
          instancePlayer.setUdpAddress(p.getUdpAddress)
        }
        val playerTeamId = instance.teamAssignments.getOrDefault(pid, 0.toByte)
        instancePlayer.setTeamId(playerTeamId)
        instance.registry.add(instancePlayer)
        instance.killTracker.registerPlayer(pid)
      }
    }

    // Register bots from the lobby's BotManager
    val botController = new BotController(instance)
    lobby.botManager.getBots.foreach { botSlot =>
      val spawnPoint = instance.world.getValidSpawnPoint(occupiedSpawns)
      occupiedSpawns += ((spawnPoint.getX, spawnPoint.getY))
      val charDef = com.gridgame.common.model.CharacterDef.get(botSlot.characterId)
      val colorRGB = Player.generateColorFromUUID(botSlot.id)
      val botPlayer = new Player(botSlot.id, botSlot.name, spawnPoint, colorRGB, charDef.maxHealth, charDef.maxHealth)
      botPlayer.setCharacterId(botSlot.characterId)
      val botTeamId = instance.teamAssignments.getOrDefault(botSlot.id, 0.toByte)
      botPlayer.setTeamId(botTeamId)
      instance.registry.add(botPlayer)
      instance.killTracker.registerPlayer(botSlot.id)
      botController.addBotId(botSlot.id)
    }
    instance.botController = botController

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

    // Start the instance (spawns initial items, begins projectile/timer ticks)
    // Done after players are registered and notified so item spawns reach everyone
    instance.start()

    // Broadcast join packets for all players (human + bot) so each client
    // knows every player's server-assigned spawn position
    lobby.players.asScala.foreach { pid =>
      val instancePlayer = instance.registry.get(pid)
      if (instancePlayer != null) {
        val joinPacket = new PlayerJoinPacket(
          server.getNextSequenceNumber,
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
          server.getNextSequenceNumber,
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

    println(s"LobbyHandler: Game started for lobby ${lobby.id} on map $worldFileName ($worldPath)")
  }

  private def handleListRequest(player: Player): Unit = {
    val now = System.currentTimeMillis()
    val last = lastListRequestTime.get(player.getId)
    if (last != null && now - last < LIST_REQUEST_COOLDOWN_MS) return
    lastListRequestTime.put(player.getId, now)

    val lobbies = lobbyManager.getActiveLobbies

    lobbies.foreach { lobby =>
      val entry = new LobbyActionPacket(
        server.getNextSequenceNumber, player.getId, Packet.getCurrentTimestamp,
        LobbyAction.LIST_ENTRY, lobby.id,
        lobby.mapIndex.toByte, lobby.durationMinutes.toByte,
        lobby.playerCount.toByte, lobby.maxPlayers.toByte,
        lobby.status, lobby.name, 0.toByte, lobby.gameMode, lobby.teamSize.toByte
      )
      server.sendPacketToPlayer(entry, player)
    }

    val end = new LobbyActionPacket(
      server.getNextSequenceNumber, player.getId, LobbyAction.LIST_END
    )
    server.sendPacketToPlayer(end, player)
  }

  private def handleCharacterSelect(playerId: UUID, packet: LobbyActionPacket): Unit = {
    val lobby = lobbyManager.getPlayerLobby(playerId)
    if (lobby == null) return
    if (lobby.status != LobbyStatus.WAITING) return

    // Validate character ID
    val charId = packet.getCharacterId
    if (com.gridgame.common.model.CharacterDef.get(charId) == null) return

    lobby.setCharacter(playerId, charId)

    // Broadcast CHARACTER_SELECT to other lobby members
    val broadcast = new LobbyActionPacket(
      server.getNextSequenceNumber, playerId, Packet.getCurrentTimestamp,
      LobbyAction.CHARACTER_SELECT, lobby.id,
      0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, "", packet.getCharacterId
    )
    broadcastToLobby(lobby, broadcast, playerId)
  }

  private def handleConfigUpdate(playerId: UUID, packet: LobbyActionPacket): Unit = {
    val lobby = lobbyManager.getPlayerLobby(playerId)
    if (lobby == null || !lobby.isHost(playerId)) return
    if (lobby.status != LobbyStatus.WAITING) return

    val rawMapIndex = packet.getMapIndex.toInt & 0xFF
    lobby.mapIndex = if (rawMapIndex >= 0 && rawMapIndex < com.gridgame.common.WorldRegistry.size) rawMapIndex else 0
    lobby.durationMinutes = Math.max(1, Math.min(30, if (packet.getDurationMinutes <= 0) Constants.DEFAULT_GAME_DURATION_MIN else packet.getDurationMinutes.toInt))
    val rawGameMode = packet.getGameMode
    lobby.gameMode = if (rawGameMode == 0 || rawGameMode == 1) rawGameMode else 0
    lobby.teamSize = Math.max(2, Math.min(4, packet.getTeamSize.toInt))

    // Auto-adjust maxPlayers for Teams mode
    if (lobby.gameMode == 1) {
      lobby.maxPlayers = lobby.teamSize * 2
      // Remove excess bots and notify clients
      while (lobby.playerCount > lobby.maxPlayers && lobby.botManager.botCount > 0) {
        val removed = lobby.botManager.removeLastBot()
        removed.foreach { botSlot =>
          val leftPacket = new LobbyActionPacket(
            server.getNextSequenceNumber, botSlot.id, LobbyAction.PLAYER_LEFT, lobby.id,
            lobby.mapIndex.toByte, lobby.durationMinutes.toByte,
            lobby.playerCount.toByte, lobby.maxPlayers.toByte,
            lobby.status, botSlot.name
          )
          broadcastToLobby(lobby, leftPacket, null)
        }
      }
    } else {
      lobby.maxPlayers = Constants.MAX_LOBBY_PLAYERS
    }

    // Broadcast config update to all lobby members
    val update = new LobbyActionPacket(
      server.getNextSequenceNumber, playerId, Packet.getCurrentTimestamp,
      LobbyAction.CONFIG_UPDATE, lobby.id,
      lobby.mapIndex.toByte, lobby.durationMinutes.toByte,
      lobby.playerCount.toByte, lobby.maxPlayers.toByte,
      lobby.status, lobby.name, 0.toByte, lobby.gameMode, lobby.teamSize.toByte
    )
    broadcastToLobby(lobby, update, null)
  }

  private def handleAddBot(playerId: UUID, player: Player): Unit = {
    val lobby = lobbyManager.getPlayerLobby(playerId)
    if (lobby == null || !lobby.isHost(playerId)) return
    if (lobby.status != LobbyStatus.WAITING) return

    if (lobby.playerCount >= lobby.maxPlayers) return

    val botSlot = lobby.botManager.addBot()
    println(s"LobbyHandler: Bot '${botSlot.name}' added to lobby ${lobby.id}")

    // Broadcast PLAYER_JOINED with updated player count (lobbyName carries bot name)
    val broadcast = new LobbyActionPacket(
      server.getNextSequenceNumber, botSlot.id, LobbyAction.PLAYER_JOINED, lobby.id,
      lobby.mapIndex.toByte, lobby.durationMinutes.toByte,
      lobby.playerCount.toByte, lobby.maxPlayers.toByte,
      lobby.status, botSlot.name
    )
    broadcastToLobby(lobby, broadcast, null)
  }

  private def handleRemoveBot(playerId: UUID, player: Player): Unit = {
    val lobby = lobbyManager.getPlayerLobby(playerId)
    if (lobby == null || !lobby.isHost(playerId)) return
    if (lobby.status != LobbyStatus.WAITING) return

    val removed = lobby.botManager.removeLastBot()
    if (removed.isEmpty) return

    val botSlot = removed.get
    println(s"LobbyHandler: Bot '${botSlot.name}' removed from lobby ${lobby.id}")

    // Broadcast PLAYER_LEFT with updated player count (lobbyName carries bot name)
    val broadcast = new LobbyActionPacket(
      server.getNextSequenceNumber, botSlot.id, LobbyAction.PLAYER_LEFT, lobby.id,
      lobby.mapIndex.toByte, lobby.durationMinutes.toByte,
      lobby.playerCount.toByte, lobby.maxPlayers.toByte,
      lobby.status, botSlot.name
    )
    broadcastToLobby(lobby, broadcast, null)
  }

  /** Clean up per-player rate-limit state on disconnect. */
  def cleanupPlayer(playerId: UUID): Unit = {
    lastListRequestTime.remove(playerId)
    lastCreateTime.remove(playerId)
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
