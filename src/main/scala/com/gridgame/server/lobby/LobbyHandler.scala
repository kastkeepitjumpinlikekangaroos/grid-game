package com.gridgame.server.lobby

import com.gridgame.common.Constants
import com.gridgame.common.WorldRegistry
import com.gridgame.common.model.Player
import com.gridgame.common.observability.Attrs
import com.gridgame.common.observability.Metrics
import com.gridgame.common.protocol._

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import scala.jdk.CollectionConverters._

/** What a player does in the lobby browser and in a lobby room: make, join, leave and configure a
  * lobby, pick a character, add bots, start the match — and practice, a lobby of one with bots. */
class LobbyHandler(host: LobbyHost, lobbyManager: LobbyManager, rankedQueue: RankedQueue, launcher: MatchLauncher) {
  private val lastListRequestTime = new ConcurrentHashMap[UUID, java.lang.Long]()
  private val LIST_REQUEST_COOLDOWN_MS = 2000L
  private val lastCreateTime = new ConcurrentHashMap[UUID, java.lang.Long]()
  private val CREATE_COOLDOWN_MS = 3000L

  def processLobbyAction(packet: LobbyActionPacket, player: Player): Unit = {
    val playerId = packet.getPlayerId
    Metrics.lobbyAction.add(1L, Attrs.lobbyAction(packet.getAction))

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

      case LobbyAction.PRACTICE_START =>
        handlePracticeStart(playerId, player, packet)

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
    if (lobbyManager.getPlayerLobby(playerId) != null) { sendFailure(player, LobbyFailure.ALREADY_IN_LOBBY); return }

    // Rate limit lobby creation; only a lobby actually created spends the cooldown
    val now = System.currentTimeMillis()
    val lastCreate = lastCreateTime.get(playerId)
    if (lastCreate != null && now - lastCreate < CREATE_COOLDOWN_MS) { sendFailure(player, LobbyFailure.RATE_LIMITED); return }

    val rawName = if (packet.getLobbyName.isEmpty) s"${player.getName}'s Game" else packet.getLobbyName
    val name = sanitizeName(rawName)
    if (name.isEmpty) { sendFailure(player, LobbyFailure.INVALID_NAME); return }
    val rawMapIndex = packet.getMapIndex.toInt & 0xFF
    val mapIndex = if (rawMapIndex >= 0 && rawMapIndex < com.gridgame.common.WorldRegistry.size) rawMapIndex else 0
    val duration = Math.max(1, Math.min(30, if (packet.getDurationMinutes <= 0) Constants.DEFAULT_GAME_DURATION_MIN else packet.getDurationMinutes.toInt))
    val maxPlayers = Math.max(2, Math.min(Constants.MAX_LOBBY_PLAYERS, if (packet.getMaxPlayers <= 0) Constants.MAX_LOBBY_PLAYERS else packet.getMaxPlayers.toInt))

    val lobby = lobbyManager.createLobby(playerId, name, mapIndex, duration, maxPlayers)
    if (lobby == null) { sendFailure(player, LobbyFailure.SERVER_FULL); return }
    lastCreateTime.put(playerId, now)
    leftForALobby(playerId)
    takeCharacter(lobby, playerId, packet.getCharacterId)

    // Send JOINED response to creator
    val response = new LobbyActionPacket(
      host.outbox.nextSeq(), playerId, Packet.getCurrentTimestamp,
      LobbyAction.JOINED, lobby.id,
      lobby.mapIndex.toByte, lobby.durationMinutes.toByte,
      lobby.playerCount.toByte, lobby.maxPlayers.toByte,
      lobby.status, lobby.name, 0.toByte, lobby.gameMode, lobby.teamSize.toByte
    )
    host.outbox.send(response, player)
  }

  private def handleJoin(playerId: UUID, player: Player, packet: LobbyActionPacket): Unit = {
    // Prevent joining if already in a lobby
    if (lobbyManager.getPlayerLobby(playerId) != null) { sendFailure(player, LobbyFailure.ALREADY_IN_LOBBY); return }

    val target = lobbyManager.getLobby(packet.getLobbyId)
    if (target == null || target.status != LobbyStatus.WAITING || target.isPractice || target.isRanked) {
      sendFailure(player, LobbyFailure.NOT_JOINABLE)
      return
    }
    val lobby = lobbyManager.joinLobby(playerId, packet.getLobbyId)
    if (lobby == null) {
      println(s"LobbyHandler: Player ${playerId.toString.substring(0, 8)} failed to join lobby ${packet.getLobbyId}")
      sendFailure(player, if (target.status == LobbyStatus.WAITING) LobbyFailure.LOBBY_FULL else LobbyFailure.NOT_JOINABLE)
      return
    }
    leftForALobby(playerId)
    val picked = takeCharacter(lobby, playerId, packet.getCharacterId)

    // Send JOINED to the new player
    val response = new LobbyActionPacket(
      host.outbox.nextSeq(), playerId, Packet.getCurrentTimestamp,
      LobbyAction.JOINED, lobby.id,
      lobby.mapIndex.toByte, lobby.durationMinutes.toByte,
      lobby.playerCount.toByte, lobby.maxPlayers.toByte,
      lobby.status, lobby.name, 0.toByte, lobby.gameMode, lobby.teamSize.toByte
    )
    host.outbox.send(response, player)

    // Broadcast PLAYER_JOINED to others in lobby (lobbyName field carries the member name)
    val joinerName = player.getName
    val broadcast = new LobbyActionPacket(
      host.outbox.nextSeq(), playerId, LobbyAction.PLAYER_JOINED, lobby.id,
      lobby.mapIndex.toByte, lobby.durationMinutes.toByte,
      lobby.playerCount.toByte, lobby.maxPlayers.toByte,
      lobby.status, joinerName
    )
    broadcastToLobby(lobby, broadcast, playerId)

    // Send the roster to the new joiner in server order, themselves included, so their
    // lobby room lists everyone (and previews teams) in the order the match will deal them.
    lobby.players.asScala.foreach { memberId =>
      val member = if (memberId.equals(playerId)) player else host.getConnectedPlayer(memberId)
      if (member != null) {
        val memberPacket = new LobbyActionPacket(
          host.outbox.nextSeq(), memberId, LobbyAction.MEMBER, lobby.id,
          lobby.mapIndex.toByte, lobby.durationMinutes.toByte,
          lobby.playerCount.toByte, lobby.maxPlayers.toByte,
          lobby.status, member.getName
        )
        host.outbox.send(memberPacket, player)
      }
    }
    lobby.botManager.getBots.foreach { botSlot =>
      val botPacket = new LobbyActionPacket(
        host.outbox.nextSeq(), botSlot.id, LobbyAction.MEMBER, lobby.id,
        lobby.mapIndex.toByte, lobby.durationMinutes.toByte,
        lobby.playerCount.toByte, lobby.maxPlayers.toByte,
        lobby.status, botSlot.name
      )
      host.outbox.send(botPacket, player)
    }

    // The others see the joiner's pick, as they would had it been picked in the room
    if (picked) broadcastCharacterSelect(lobby, playerId, packet.getCharacterId)
  }

  /**
   * A player who has gone into a lobby is no longer waiting for a ranked match. Queueing is refused
   * to anyone in a lobby, but a lobby could be made or joined from the queue, and the matchmaker
   * then took the player into its match's lobby and left them behind in the other as a member who
   * would never come back.
   */
  private def leftForALobby(playerId: UUID): Unit = rankedQueue.removePlayer(playerId)

  /**
   * The character a player arrives with. CREATE and JOIN carry the one the client has selected,
   * which it shows as picked in the lobby room; only a click there used to reach the server, so
   * a player who had picked before joining (in practice, ranked, or the last lobby) was entered
   * as Spaceman and every shot they fired was refused as another character's. Returns whether
   * one was taken.
   */
  private def takeCharacter(lobby: Lobby, playerId: UUID, charId: Byte): Boolean = {
    if (!com.gridgame.common.model.CharacterDef.isValid(charId)) return false
    lobby.setCharacter(playerId, charId)
    true
  }

  private def broadcastCharacterSelect(lobby: Lobby, playerId: UUID, charId: Byte): Unit = {
    val broadcast = new LobbyActionPacket(
      host.outbox.nextSeq(), playerId, Packet.getCurrentTimestamp,
      LobbyAction.CHARACTER_SELECT, lobby.id,
      0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, "", charId
    )
    broadcastToLobby(lobby, broadcast, playerId)
  }

  private def handleLeave(playerId: UUID, player: Player): Unit = {
    val lobby = lobbyManager.leaveLobby(playerId)
    if (lobby == null) return

    val instance = lobby.gameInstance
    if (lobby.status == LobbyStatus.IN_GAME && instance != null) {
      if (lobby.isPractice) {
        // Leaving practice ends the session, and the player still gets its results screen.
        host.endGame(lobby.id)
      } else {
        // Leaving takes you out of the match but it carries on for everyone else, host or
        // not. Closing the lobby here used to strand the rest in a frozen match with no
        // scoreboard, and let a losing host void a ranked match by quitting.
        instance.handler.removePlayer(playerId)
        if (lobby.players.isEmpty) {
          // No humans left to finish it. Stop it, or its executors and OTel gauge callbacks
          // keep running and reporting forever.
          instance.stop()
          host.unregisterGameInstance(lobby.id)
          lobbyManager.removeLobby(lobby.id)
        }
      }
      return
    }
    // Finished: endGame is already tearing the lobby down.
    if (lobby.status != LobbyStatus.WAITING) return

    if (lobby.isHost(playerId)) {
      val closePacket = new LobbyActionPacket(
        host.outbox.nextSeq(), playerId, LobbyAction.LOBBY_CLOSED, lobby.id
      )
      broadcastToLobby(lobby, closePacket, null)
      lobbyManager.removeLobby(lobby.id)
    } else {
      // Notify remaining players (lobbyName field carries the leaver's name)
      val leftPacket = new LobbyActionPacket(
        host.outbox.nextSeq(), playerId, LobbyAction.PLAYER_LEFT, lobby.id,
        lobby.mapIndex.toByte, lobby.durationMinutes.toByte,
        lobby.playerCount.toByte, lobby.maxPlayers.toByte,
        lobby.status, player.getName
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
    val connectedCount = lobby.players.asScala.count(pid => host.getConnectedPlayer(pid) != null)
    if (connectedCount == 0) {
      println(s"LobbyHandler: Cannot start game with 0 connected players")
      return
    }

    // Atomic check-and-set to prevent double start from rapid packets
    lobby.synchronized {
      if (lobby.status != LobbyStatus.WAITING) return
      // Set match type for casual games: 0=Casual FFA, 1=Casual Teams
      // Preserve practice matchType (5) if already set
      if (lobby.matchType != 5) lobby.matchType = lobby.gameMode
      lobby.status = LobbyStatus.IN_GAME
    }

    val instance = launcher.launch(lobby)

    println(s"LobbyHandler: Game started for lobby ${lobby.id} on map ${WorldRegistry.getFilename(lobby.mapIndex)} (${instance.worldFile})")
  }

  private def handleListRequest(player: Player): Unit = {
    val now = System.currentTimeMillis()
    val last = lastListRequestTime.get(player.getId)
    if (last != null && now - last < LIST_REQUEST_COOLDOWN_MS) return
    lastListRequestTime.put(player.getId, now)

    // Practice sessions are private; listing them only clutters the browser with games no one can join.
    val lobbies = lobbyManager.getActiveLobbies.filterNot(_.isPractice)

    lobbies.foreach { lobby =>
      val entry = new LobbyActionPacket(
        host.outbox.nextSeq(), player.getId, Packet.getCurrentTimestamp,
        LobbyAction.LIST_ENTRY, lobby.id,
        lobby.mapIndex.toByte, lobby.durationMinutes.toByte,
        lobby.playerCount.toByte, lobby.maxPlayers.toByte,
        lobby.status, lobby.name, 0.toByte, lobby.gameMode, lobby.teamSize.toByte
      )
      host.outbox.send(entry, player)
    }

    val end = new LobbyActionPacket(
      host.outbox.nextSeq(), player.getId, LobbyAction.LIST_END
    )
    host.outbox.send(end, player)
  }

  private def handleCharacterSelect(playerId: UUID, packet: LobbyActionPacket): Unit = {
    val lobby = lobbyManager.getPlayerLobby(playerId)
    if (lobby == null) return
    if (lobby.status != LobbyStatus.WAITING) return

    // Validate character ID (CharacterDef.get never returns null: it falls back to Spaceman)
    if (!takeCharacter(lobby, playerId, packet.getCharacterId)) return

    // Broadcast CHARACTER_SELECT to other lobby members
    broadcastCharacterSelect(lobby, playerId, packet.getCharacterId)
  }

  private def handleConfigUpdate(playerId: UUID, packet: LobbyActionPacket): Unit = {
    val lobby = lobbyManager.getPlayerLobby(playerId)
    if (lobby == null || !lobby.isHost(playerId)) return
    if (lobby.status != LobbyStatus.WAITING) return

    val rawMapIndex = packet.getMapIndex.toInt & 0xFF
    lobby.mapIndex = if (rawMapIndex >= 0 && rawMapIndex < com.gridgame.common.WorldRegistry.size) rawMapIndex else 0
    lobby.durationMinutes = Math.max(1, Math.min(30, if (packet.getDurationMinutes <= 0) Constants.DEFAULT_GAME_DURATION_MIN else packet.getDurationMinutes.toInt))
    // Teams go up to 4v4. A team size too small for the humans already here would leave a
    // lopsided match, so it grows to fit them; more humans than 4v4 holds can't play Teams.
    val requestedSize = Math.max(2, Math.min(4, packet.getTeamSize.toInt))
    val minTeamSize = (lobby.players.size + 1) / 2
    val teamsFit = minTeamSize <= 4
    lobby.gameMode = if (packet.getGameMode == 1 && teamsFit) 1 else 0
    lobby.teamSize = if (teamsFit) Math.max(requestedSize, minTeamSize) else requestedSize

    // Auto-adjust maxPlayers for Teams mode
    if (lobby.gameMode == 1) {
      lobby.maxPlayers = lobby.teamSize * 2
      // Remove excess bots and notify clients
      while (lobby.playerCount > lobby.maxPlayers && lobby.botManager.botCount > 0) {
        val removed = lobby.botManager.removeLastBot()
        removed.foreach { botSlot =>
          val leftPacket = new LobbyActionPacket(
            host.outbox.nextSeq(), botSlot.id, LobbyAction.PLAYER_LEFT, lobby.id,
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
      host.outbox.nextSeq(), playerId, Packet.getCurrentTimestamp,
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
    Metrics.botsAdded.add(1L, io.opentelemetry.api.common.Attributes.empty())
    println(s"LobbyHandler: Bot '${botSlot.name}' added to lobby ${lobby.id}")

    // Broadcast PLAYER_JOINED with updated player count (lobbyName carries bot name)
    val broadcast = new LobbyActionPacket(
      host.outbox.nextSeq(), botSlot.id, LobbyAction.PLAYER_JOINED, lobby.id,
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
    Metrics.botsRemoved.add(1L, io.opentelemetry.api.common.Attributes.empty())
    println(s"LobbyHandler: Bot '${botSlot.name}' removed from lobby ${lobby.id}")

    // Broadcast PLAYER_LEFT with updated player count (lobbyName carries bot name)
    val broadcast = new LobbyActionPacket(
      host.outbox.nextSeq(), botSlot.id, LobbyAction.PLAYER_LEFT, lobby.id,
      lobby.mapIndex.toByte, lobby.durationMinutes.toByte,
      lobby.playerCount.toByte, lobby.maxPlayers.toByte,
      lobby.status, botSlot.name
    )
    broadcastToLobby(lobby, broadcast, null)
  }

  private def handlePracticeStart(playerId: UUID, player: Player, packet: LobbyActionPacket): Unit = {
    // Prevent starting practice while already in a lobby
    if (lobbyManager.getPlayerLobby(playerId) != null) { sendFailure(player, LobbyFailure.ALREADY_IN_LOBBY); return }

    // Rate limit (reuse create cooldown)
    val now = System.currentTimeMillis()
    val lastCreate = lastCreateTime.get(playerId)
    if (lastCreate != null && now - lastCreate < CREATE_COOLDOWN_MS) { sendFailure(player, LobbyFailure.RATE_LIMITED); return }

    // Create a practice lobby with a random map
    val mapIndex = scala.util.Random.nextInt(com.gridgame.common.WorldRegistry.size)
    val lobby = lobbyManager.createLobby(playerId, "Practice", mapIndex, 30, 32)
    if (lobby == null) { sendFailure(player, LobbyFailure.SERVER_FULL); return }
    lastCreateTime.put(playerId, now)
    leftForALobby(playerId)
    lobby.matchType = 5

    // Set the player's selected character
    takeCharacter(lobby, playerId, packet.getCharacterId)

    // Add 5 bots
    for (_ <- 1 to 5) lobby.botManager.addBot()

    // Send JOINED response
    val response = new LobbyActionPacket(
      host.outbox.nextSeq(), playerId, Packet.getCurrentTimestamp,
      LobbyAction.JOINED, lobby.id,
      lobby.mapIndex.toByte, lobby.durationMinutes.toByte,
      lobby.playerCount.toByte, lobby.maxPlayers.toByte,
      lobby.status, lobby.name, 0.toByte, lobby.gameMode, lobby.teamSize.toByte
    )
    host.outbox.send(response, player)

    // Auto-start the game immediately
    handleStart(playerId, player)
  }

  /** Tell a player why their create / join / practice request did nothing, rather than
    * leaving their button looking broken. */
  private def sendFailure(player: Player, reason: Byte): Unit = {
    val packet = new LobbyActionPacket(
      host.outbox.nextSeq(), player.getId, Packet.getCurrentTimestamp,
      LobbyAction.ACTION_FAILED, 0.toShort, 0.toByte, 0.toByte, 0.toByte, 0.toByte, reason, ""
    )
    host.outbox.send(packet, player)
  }

  /** Clean up per-player rate-limit state on disconnect. */
  def cleanupPlayer(playerId: UUID): Unit = {
    lastListRequestTime.remove(playerId)
    lastCreateTime.remove(playerId)
  }

  private def broadcastToLobby(lobby: Lobby, packet: Packet, excludePlayerId: UUID): Unit = {
    lobby.players.asScala.foreach { pid =>
      if (excludePlayerId == null || !pid.equals(excludePlayerId)) {
        val p = host.getConnectedPlayer(pid)
        if (p != null) {
          host.outbox.send(packet, p)
        }
      }
    }
  }

}
