package com.gridgame.server

import com.gridgame.common.Constants
import com.gridgame.common.WorldRegistry
import com.gridgame.common.model.Player
import com.gridgame.common.protocol._

import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import scala.jdk.CollectionConverters._

case class QueueEntry(playerId: UUID, var characterId: Byte, elo: Int, joinTime: Long, mode: Byte = RankedQueueMode.FFA)

class RankedQueue(server: GameServer) {
  // FFA queue
  private val queue: CopyOnWriteArrayList[QueueEntry] = new CopyOnWriteArrayList[QueueEntry]()
  private val playerInQueue: ConcurrentHashMap[UUID, QueueEntry] = new ConcurrentHashMap[UUID, QueueEntry]()

  // Duel queue
  private val duelQueue: CopyOnWriteArrayList[QueueEntry] = new CopyOnWriteArrayList[QueueEntry]()
  private val duelPlayerInQueue: ConcurrentHashMap[UUID, QueueEntry] = new ConcurrentHashMap[UUID, QueueEntry]()

  // Teams queue
  private val teamsQueue: CopyOnWriteArrayList[QueueEntry] = new CopyOnWriteArrayList[QueueEntry]()
  private val teamsPlayerInQueue: ConcurrentHashMap[UUID, QueueEntry] = new ConcurrentHashMap[UUID, QueueEntry]()

  private var matchmakingExecutor: ScheduledExecutorService = _

  def start(): Unit = {
    matchmakingExecutor = Executors.newSingleThreadScheduledExecutor()
    matchmakingExecutor.scheduleAtFixedRate(
      new Runnable { def run(): Unit = checkQueue() },
      5, 5, TimeUnit.SECONDS
    )
    println("RankedQueue: Started matchmaking service")
  }

  def stop(): Unit = {
    if (matchmakingExecutor != null) {
      matchmakingExecutor.shutdown()
      try {
        if (!matchmakingExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
          matchmakingExecutor.shutdownNow()
        }
      } catch {
        case _: InterruptedException =>
          matchmakingExecutor.shutdownNow()
      }
    }
  }

  def addPlayer(playerId: UUID, characterId: Byte, elo: Int, mode: Byte = RankedQueueMode.FFA): Unit = {
    if (playerInQueue.containsKey(playerId) || duelPlayerInQueue.containsKey(playerId) || teamsPlayerInQueue.containsKey(playerId)) return

    val entry = QueueEntry(playerId, characterId, elo, System.currentTimeMillis(), mode)
    if (mode == RankedQueueMode.DUEL) {
      duelQueue.add(entry)
      duelPlayerInQueue.put(playerId, entry)
      println(s"RankedQueue: Player ${playerId.toString.substring(0, 8)} joined duel queue (elo=$elo, queue size=${duelQueue.size()})")
      sendDuelQueueStatus(playerId)
    } else if (mode == RankedQueueMode.TEAMS) {
      teamsQueue.add(entry)
      teamsPlayerInQueue.put(playerId, entry)
      println(s"RankedQueue: Player ${playerId.toString.substring(0, 8)} joined teams queue (elo=$elo, queue size=${teamsQueue.size()})")
      sendTeamsQueueStatus(playerId)
    } else {
      queue.add(entry)
      playerInQueue.put(playerId, entry)
      println(s"RankedQueue: Player ${playerId.toString.substring(0, 8)} joined FFA queue (elo=$elo, queue size=${queue.size()})")
      sendQueueStatus(playerId)
    }
  }

  def removePlayer(playerId: UUID): Unit = {
    val entry = playerInQueue.remove(playerId)
    if (entry != null) {
      queue.remove(entry)
      println(s"RankedQueue: Player ${playerId.toString.substring(0, 8)} left FFA queue (queue size=${queue.size()})")
    }
    val duelEntry = duelPlayerInQueue.remove(playerId)
    if (duelEntry != null) {
      duelQueue.remove(duelEntry)
      println(s"RankedQueue: Player ${playerId.toString.substring(0, 8)} left duel queue (queue size=${duelQueue.size()})")
    }
    val teamsEntry = teamsPlayerInQueue.remove(playerId)
    if (teamsEntry != null) {
      teamsQueue.remove(teamsEntry)
      println(s"RankedQueue: Player ${playerId.toString.substring(0, 8)} left teams queue (queue size=${teamsQueue.size()})")
    }
  }

  def updateCharacter(playerId: UUID, characterId: Byte): Unit = {
    val entry = playerInQueue.get(playerId)
    if (entry != null) {
      entry.characterId = characterId
      println(s"RankedQueue: Player ${playerId.toString.substring(0, 8)} changed character to $characterId")
    }
    val duelEntry = duelPlayerInQueue.get(playerId)
    if (duelEntry != null) {
      duelEntry.characterId = characterId
      println(s"RankedQueue: Player ${playerId.toString.substring(0, 8)} changed character to $characterId (duel)")
    }
    val teamsEntry = teamsPlayerInQueue.get(playerId)
    if (teamsEntry != null) {
      teamsEntry.characterId = characterId
      println(s"RankedQueue: Player ${playerId.toString.substring(0, 8)} changed character to $characterId (teams)")
    }
  }

  def isInQueue(playerId: UUID): Boolean = playerInQueue.containsKey(playerId) || duelPlayerInQueue.containsKey(playerId) || teamsPlayerInQueue.containsKey(playerId)

  private def sendQueueStatus(playerId: UUID): Unit = {
    val player = server.getConnectedPlayer(playerId)
    val entry = playerInQueue.get(playerId)
    if (player == null || entry == null) return

    val waitSeconds = ((System.currentTimeMillis() - entry.joinTime) / 1000).toInt
    val packet = new RankedQueuePacket(
      server.getNextSequenceNumber, playerId, Packet.getCurrentTimestamp,
      RankedQueueAction.QUEUE_STATUS,
      entry.characterId,
      queue.size().toByte,
      entry.elo.toShort,
      waitSeconds
    )
    server.sendPacketToPlayer(packet, player)
  }

  private def sendDuelQueueStatus(playerId: UUID): Unit = {
    val player = server.getConnectedPlayer(playerId)
    val entry = duelPlayerInQueue.get(playerId)
    if (player == null || entry == null) return

    val waitSeconds = ((System.currentTimeMillis() - entry.joinTime) / 1000).toInt
    val packet = new RankedQueuePacket(
      server.getNextSequenceNumber, playerId, Packet.getCurrentTimestamp,
      RankedQueueAction.QUEUE_STATUS,
      entry.characterId,
      duelQueue.size().toByte,
      entry.elo.toShort,
      waitSeconds,
      mode = RankedQueueMode.DUEL
    )
    server.sendPacketToPlayer(packet, player)
  }

  private def sendTeamsQueueStatus(playerId: UUID): Unit = {
    val player = server.getConnectedPlayer(playerId)
    val entry = teamsPlayerInQueue.get(playerId)
    if (player == null || entry == null) return

    val waitSeconds = ((System.currentTimeMillis() - entry.joinTime) / 1000).toInt
    val packet = new RankedQueuePacket(
      server.getNextSequenceNumber, playerId, Packet.getCurrentTimestamp,
      RankedQueueAction.QUEUE_STATUS,
      entry.characterId,
      teamsQueue.size().toByte,
      entry.elo.toShort,
      waitSeconds,
      mode = RankedQueueMode.TEAMS
    )
    server.sendPacketToPlayer(packet, player)
  }

  private def checkQueue(): Unit = {
    try {
      checkFfaQueue()
      checkDuelQueue()
      checkTeamsQueue()
    } catch {
      case e: Exception =>
        System.err.println(s"RankedQueue: Error in checkQueue: ${e.getMessage}")
    }
  }

  private def checkFfaQueue(): Unit = {
    val now = System.currentTimeMillis()
    val snapshot = queue.asScala.toSeq
    val queueSize = snapshot.size

    if (queueSize >= Constants.MAX_LOBBY_PLAYERS) {
      val sorted = snapshot.sortBy(_.elo)
      val matchEntries = sorted.take(Constants.MAX_LOBBY_PLAYERS)
      startRankedMatch(matchEntries)
    } else if (queueSize >= 2) {
      val oldest = snapshot.minBy(_.joinTime)
      val waitTime = now - oldest.joinTime
      if (waitTime > 60000) {
        startRankedMatch(snapshot)
      }
    }

    // Send status updates to remaining queued players
    queue.asScala.foreach { entry =>
      sendQueueStatus(entry.playerId)
    }
  }

  private def checkDuelQueue(): Unit = {
    val now = System.currentTimeMillis()
    val snapshot = duelQueue.asScala.toSeq

    if (snapshot.size >= 2) {
      // Sort by ELO to find closest pair
      val sorted = snapshot.sortBy(_.elo)
      var matched = false

      // Try to find a pair within 200 ELO
      var i = 0
      while (i < sorted.size - 1 && !matched) {
        val a = sorted(i)
        val b = sorted(i + 1)
        val eloDiff = Math.abs(a.elo - b.elo)
        val oldestWait = now - Math.min(a.joinTime, b.joinTime)

        if (eloDiff <= 200 || oldestWait > 30000) {
          startDuelMatch(Seq(a, b))
          matched = true
        }
        i += 1
      }

      // If no close pair found but someone waited > 30s, match any pair
      if (!matched) {
        val oldest = sorted.minBy(_.joinTime)
        val oldestWait = now - oldest.joinTime
        if (oldestWait > 30000 && sorted.size >= 2) {
          startDuelMatch(Seq(sorted(0), sorted(1)))
        }
      }
    }

    // Send status updates to remaining queued players
    duelQueue.asScala.foreach { entry =>
      sendDuelQueueStatus(entry.playerId)
    }
  }

  private def checkTeamsQueue(): Unit = {
    val now = System.currentTimeMillis()
    val snapshot = teamsQueue.asScala.toSeq
    val queueSize = snapshot.size

    // Start immediately when full lobby (teamSize * 2) is ready
    if (queueSize >= Constants.TEAMS_MAX_PLAYERS) {
      val sorted = snapshot.sortBy(_.elo)
      val matchEntries = sorted.take(Constants.TEAMS_MAX_PLAYERS)
      startTeamsMatch(matchEntries)
    } else if (queueSize >= 1) {
      // Start after 60 seconds even with a single player, fill rest with bots
      val oldest = snapshot.minBy(_.joinTime)
      val waitTime = now - oldest.joinTime
      if (waitTime > 60000) {
        startTeamsMatch(snapshot)
      }
    }

    // Send status updates to remaining queued players
    teamsQueue.asScala.foreach { entry =>
      sendTeamsQueueStatus(entry.playerId)
    }
  }

  private def startTeamsMatch(entries: Seq[QueueEntry]): Unit = {
    // Remove matched players from teams queue
    entries.foreach { entry =>
      teamsQueue.remove(entry)
      teamsPlayerInQueue.remove(entry.playerId)
    }

    // Pick random map
    val random = new java.util.Random()
    val mapIndex = random.nextInt(WorldRegistry.size)
    val worldFileName = WorldRegistry.getFilename(mapIndex)

    // Create lobby
    val hostId = entries.head.playerId
    val lobbyName = "Ranked Teams"
    val lobby = server.lobbyManager.createLobby(hostId, lobbyName, mapIndex, Constants.TEAMS_GAME_DURATION_MIN, Constants.TEAMS_MAX_PLAYERS)
    lobby.isRanked = true
    lobby.gameMode = 1 // Teams mode
    lobby.teamSize = Constants.TEAMS_TEAM_SIZE
    lobby.matchType = 4 // Ranked Teams

    // Add remaining players to lobby
    entries.tail.foreach { entry =>
      lobby.addPlayer(entry.playerId)
      server.lobbyManager.setPlayerLobby(entry.playerId, lobby.id)
    }

    // Set character selections
    entries.foreach { entry =>
      lobby.setCharacter(entry.playerId, entry.characterId)
    }

    // Send MATCH_FOUND to each player
    entries.foreach { entry =>
      val player = server.getConnectedPlayer(entry.playerId)
      if (player != null) {
        val matchFoundPacket = new RankedQueuePacket(
          server.getNextSequenceNumber, entry.playerId, Packet.getCurrentTimestamp,
          RankedQueueAction.MATCH_FOUND,
          entry.characterId,
          entries.size.toByte,
          entry.elo.toShort,
          0,
          lobby.id,
          mapIndex.toByte,
          Constants.TEAMS_GAME_DURATION_MIN.toByte,
          entries.size.toByte,
          Constants.TEAMS_MAX_PLAYERS.toByte,
          lobbyName,
          RankedQueueMode.TEAMS
        )
        server.sendPacketToPlayer(matchFoundPacket, player)
      }
    }

    // Start the game
    lobby.status = LobbyStatus.IN_GAME

    val worldPath = resolveWorldPath("worlds/" + worldFileName)
    val instance = new GameInstance(lobby.id, worldPath, lobby.durationMinutes, server)
    instance.gameMode = 1 // Teams mode
    instance.loadWorld()
    lobby.gameInstance = instance
    server.registerGameInstance(lobby.id, instance)

    // Fill remaining slots with bots first so team assignment includes them
    val botController = new BotController(instance)
    val botsNeeded = Constants.TEAMS_MAX_PLAYERS - entries.size
    for (_ <- 1 to botsNeeded) {
      lobby.botManager.addBot()
    }

    // Assign teams: round-robin (players first, then bots)
    val allIds = entries.map(_.playerId) ++ lobby.botManager.getBots.map(_.id)
    allIds.zipWithIndex.foreach { case (pid, index) =>
      val teamId = (index % 2 + 1).toByte
      instance.teamAssignments.put(pid, teamId)
    }

    // Register all human players in the instance
    var occupiedSpawns = Set.empty[(Int, Int)]
    entries.foreach { entry =>
      val p = server.getConnectedPlayer(entry.playerId)
      if (p != null) {
        val spawnPoint = instance.world.getValidSpawnPoint(occupiedSpawns)
        occupiedSpawns += ((spawnPoint.getX, spawnPoint.getY))
        val charDef = com.gridgame.common.model.CharacterDef.get(entry.characterId)
        val instancePlayer = new Player(entry.playerId, p.getName, spawnPoint, p.getColorRGB, charDef.maxHealth, charDef.maxHealth)
        instancePlayer.setCharacterId(entry.characterId)
        instancePlayer.setTcpChannel(p.getTcpChannel)
        if (p.getUdpAddress != null) {
          instancePlayer.setUdpAddress(p.getUdpAddress)
        }
        val playerTeamId = instance.teamAssignments.getOrDefault(entry.playerId, 0.toByte)
        instancePlayer.setTeamId(playerTeamId)
        instance.registry.add(instancePlayer)
        instance.killTracker.registerPlayer(entry.playerId)
      }
    }

    // Register bots in the instance
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

    // Send GAME_STARTING to all players
    val startingPacket = new LobbyActionPacket(
      server.getNextSequenceNumber, hostId, LobbyAction.GAME_STARTING, lobby.id,
      lobby.mapIndex.toByte, lobby.durationMinutes.toByte,
      lobby.playerCount.toByte, lobby.maxPlayers.toByte,
      lobby.status, lobby.name
    )
    entries.foreach { entry =>
      val p = server.getConnectedPlayer(entry.playerId)
      if (p != null) {
        server.sendPacketToPlayer(startingPacket, p)
      }
    }

    // Send WorldInfo to each player
    entries.foreach { entry =>
      val p = server.getConnectedPlayer(entry.playerId)
      if (p != null) {
        val worldInfoPacket = new WorldInfoPacket(server.getNextSequenceNumber, worldFileName)
        server.sendPacketToPlayer(worldInfoPacket, p)
      }
    }

    // Start the instance
    instance.start()

    // Broadcast join packets for all players (human + bot) so each client
    // knows every player's server-assigned spawn position
    entries.foreach { entry =>
      val instancePlayer = instance.registry.get(entry.playerId)
      if (instancePlayer != null) {
        val joinPacket = new PlayerJoinPacket(
          server.getNextSequenceNumber,
          entry.playerId,
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

    // Start bot AI
    if (lobby.botManager.botCount > 0) {
      botController.start()
    }

    println(s"RankedQueue: Started ranked teams match (lobby ${lobby.id}) with ${entries.size} players + $botsNeeded bots on $worldFileName")
  }

  private def startRankedMatch(entries: Seq[QueueEntry]): Unit = {
    // Remove matched players from queue
    entries.foreach { entry =>
      queue.remove(entry)
      playerInQueue.remove(entry.playerId)
    }

    // Pick random map
    val random = new java.util.Random()
    val mapIndex = random.nextInt(WorldRegistry.size)
    val worldFileName = WorldRegistry.getFilename(mapIndex)

    // Create lobby
    val hostId = entries.head.playerId
    val lobbyName = "Ranked Match"
    val lobby = server.lobbyManager.createLobby(hostId, lobbyName, mapIndex, Constants.DEFAULT_GAME_DURATION_MIN, Constants.MAX_LOBBY_PLAYERS)
    lobby.isRanked = true
    lobby.matchType = 2 // Ranked FFA

    // Add remaining players to lobby
    entries.tail.foreach { entry =>
      lobby.addPlayer(entry.playerId)
      server.lobbyManager.setPlayerLobby(entry.playerId, lobby.id)
    }

    // Set character selections
    entries.foreach { entry =>
      lobby.setCharacter(entry.playerId, entry.characterId)
    }

    // Send MATCH_FOUND to each player
    entries.foreach { entry =>
      val player = server.getConnectedPlayer(entry.playerId)
      if (player != null) {
        val matchFoundPacket = new RankedQueuePacket(
          server.getNextSequenceNumber, entry.playerId, Packet.getCurrentTimestamp,
          RankedQueueAction.MATCH_FOUND,
          entry.characterId,
          entries.size.toByte,
          entry.elo.toShort,
          0,
          lobby.id,
          mapIndex.toByte,
          Constants.DEFAULT_GAME_DURATION_MIN.toByte,
          entries.size.toByte,
          Constants.MAX_LOBBY_PLAYERS.toByte,
          lobbyName
        )
        server.sendPacketToPlayer(matchFoundPacket, player)
      }
    }

    // Start the game (similar to LobbyHandler.handleStart)
    lobby.status = LobbyStatus.IN_GAME

    val worldPath = resolveWorldPath("worlds/" + worldFileName)
    val instance = new GameInstance(lobby.id, worldPath, lobby.durationMinutes, server)
    instance.loadWorld()
    lobby.gameInstance = instance
    server.registerGameInstance(lobby.id, instance)

    // Register all players in the instance
    var occupiedSpawns = Set.empty[(Int, Int)]
    entries.foreach { entry =>
      val p = server.getConnectedPlayer(entry.playerId)
      if (p != null) {
        val spawnPoint = instance.world.getValidSpawnPoint(occupiedSpawns)
        occupiedSpawns += ((spawnPoint.getX, spawnPoint.getY))
        val charDef = com.gridgame.common.model.CharacterDef.get(entry.characterId)
        val instancePlayer = new Player(entry.playerId, p.getName, spawnPoint, p.getColorRGB, charDef.maxHealth, charDef.maxHealth)
        instancePlayer.setCharacterId(entry.characterId)
        instancePlayer.setTcpChannel(p.getTcpChannel)
        if (p.getUdpAddress != null) {
          instancePlayer.setUdpAddress(p.getUdpAddress)
        }
        instance.registry.add(instancePlayer)
        instance.killTracker.registerPlayer(entry.playerId)
      }
    }

    // Fill remaining slots with bots
    val botController = new BotController(instance)
    val botsNeeded = Constants.MAX_LOBBY_PLAYERS - entries.size
    for (_ <- 1 to botsNeeded) {
      val botSlot = lobby.botManager.addBot()
      val spawnPoint = instance.world.getValidSpawnPoint(occupiedSpawns)
      occupiedSpawns += ((spawnPoint.getX, spawnPoint.getY))
      val charDef = com.gridgame.common.model.CharacterDef.get(botSlot.characterId)
      val colorRGB = Player.generateColorFromUUID(botSlot.id)
      val botPlayer = new Player(botSlot.id, botSlot.name, spawnPoint, colorRGB, charDef.maxHealth, charDef.maxHealth)
      botPlayer.setCharacterId(botSlot.characterId)
      instance.registry.add(botPlayer)
      instance.killTracker.registerPlayer(botSlot.id)
      botController.addBotId(botSlot.id)
    }
    instance.botController = botController

    // Send GAME_STARTING to all players
    val startingPacket = new LobbyActionPacket(
      server.getNextSequenceNumber, hostId, LobbyAction.GAME_STARTING, lobby.id,
      lobby.mapIndex.toByte, lobby.durationMinutes.toByte,
      lobby.playerCount.toByte, lobby.maxPlayers.toByte,
      lobby.status, lobby.name
    )
    entries.foreach { entry =>
      val p = server.getConnectedPlayer(entry.playerId)
      if (p != null) {
        server.sendPacketToPlayer(startingPacket, p)
      }
    }

    // Send WorldInfo to each player
    entries.foreach { entry =>
      val p = server.getConnectedPlayer(entry.playerId)
      if (p != null) {
        val worldInfoPacket = new WorldInfoPacket(server.getNextSequenceNumber, worldFileName)
        server.sendPacketToPlayer(worldInfoPacket, p)
      }
    }

    // Start the instance
    instance.start()

    // Broadcast join packets for all players (human + bot) so each client
    // knows every player's server-assigned spawn position
    entries.foreach { entry =>
      val instancePlayer = instance.registry.get(entry.playerId)
      if (instancePlayer != null) {
        val joinPacket = new PlayerJoinPacket(
          server.getNextSequenceNumber,
          entry.playerId,
          instancePlayer.getPosition,
          instancePlayer.getColorRGB,
          instancePlayer.getName,
          instancePlayer.getHealth,
          instancePlayer.getCharacterId
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
          botSlot.characterId
        )
        instance.broadcastToInstance(joinPacket)
      }
    }

    // Start bot AI
    if (lobby.botManager.botCount > 0) {
      botController.start()
    }

    println(s"RankedQueue: Started ranked match (lobby ${lobby.id}) with ${entries.size} players + ${botsNeeded} bots on $worldFileName")
  }

  private def startDuelMatch(entries: Seq[QueueEntry]): Unit = {
    // Remove matched players from duel queue
    entries.foreach { entry =>
      duelQueue.remove(entry)
      duelPlayerInQueue.remove(entry.playerId)
    }

    // Pick random map
    val random = new java.util.Random()
    val mapIndex = random.nextInt(WorldRegistry.size)
    val worldFileName = WorldRegistry.getFilename(mapIndex)

    // Create lobby
    val hostId = entries.head.playerId
    val lobbyName = "Ranked Duel"
    val lobby = server.lobbyManager.createLobby(hostId, lobbyName, mapIndex, Constants.DUEL_GAME_DURATION_MIN, Constants.DUEL_MAX_PLAYERS)
    lobby.isRanked = true
    lobby.matchType = 3 // Ranked Duel

    // Add remaining players to lobby
    entries.tail.foreach { entry =>
      lobby.addPlayer(entry.playerId)
      server.lobbyManager.setPlayerLobby(entry.playerId, lobby.id)
    }

    // Set character selections
    entries.foreach { entry =>
      lobby.setCharacter(entry.playerId, entry.characterId)
    }

    // Send MATCH_FOUND to each player
    entries.foreach { entry =>
      val player = server.getConnectedPlayer(entry.playerId)
      if (player != null) {
        val matchFoundPacket = new RankedQueuePacket(
          server.getNextSequenceNumber, entry.playerId, Packet.getCurrentTimestamp,
          RankedQueueAction.MATCH_FOUND,
          entry.characterId,
          entries.size.toByte,
          entry.elo.toShort,
          0,
          lobby.id,
          mapIndex.toByte,
          Constants.DUEL_GAME_DURATION_MIN.toByte,
          entries.size.toByte,
          Constants.DUEL_MAX_PLAYERS.toByte,
          lobbyName,
          RankedQueueMode.DUEL
        )
        server.sendPacketToPlayer(matchFoundPacket, player)
      }
    }

    // Start the game
    lobby.status = LobbyStatus.IN_GAME

    val worldPath = resolveWorldPath("worlds/" + worldFileName)
    val instance = new GameInstance(lobby.id, worldPath, lobby.durationMinutes, server)
    instance.loadWorld()
    lobby.gameInstance = instance
    server.registerGameInstance(lobby.id, instance)

    // Register all players in the instance (no bots for duels)
    var occupiedSpawns = Set.empty[(Int, Int)]
    entries.foreach { entry =>
      val p = server.getConnectedPlayer(entry.playerId)
      if (p != null) {
        val spawnPoint = instance.world.getValidSpawnPoint(occupiedSpawns)
        occupiedSpawns += ((spawnPoint.getX, spawnPoint.getY))
        val charDef = com.gridgame.common.model.CharacterDef.get(entry.characterId)
        val instancePlayer = new Player(entry.playerId, p.getName, spawnPoint, p.getColorRGB, charDef.maxHealth, charDef.maxHealth)
        instancePlayer.setCharacterId(entry.characterId)
        instancePlayer.setTcpChannel(p.getTcpChannel)
        if (p.getUdpAddress != null) {
          instancePlayer.setUdpAddress(p.getUdpAddress)
        }
        instance.registry.add(instancePlayer)
        instance.killTracker.registerPlayer(entry.playerId)
      }
    }

    // Send GAME_STARTING to all players
    val startingPacket = new LobbyActionPacket(
      server.getNextSequenceNumber, hostId, LobbyAction.GAME_STARTING, lobby.id,
      lobby.mapIndex.toByte, lobby.durationMinutes.toByte,
      lobby.playerCount.toByte, lobby.maxPlayers.toByte,
      lobby.status, lobby.name
    )
    entries.foreach { entry =>
      val p = server.getConnectedPlayer(entry.playerId)
      if (p != null) {
        server.sendPacketToPlayer(startingPacket, p)
      }
    }

    // Send WorldInfo to each player
    entries.foreach { entry =>
      val p = server.getConnectedPlayer(entry.playerId)
      if (p != null) {
        val worldInfoPacket = new WorldInfoPacket(server.getNextSequenceNumber, worldFileName)
        server.sendPacketToPlayer(worldInfoPacket, p)
      }
    }

    // Start the instance
    instance.start()

    // Broadcast join packets for all players so each client
    // knows every player's server-assigned spawn position
    entries.foreach { entry =>
      val instancePlayer = instance.registry.get(entry.playerId)
      if (instancePlayer != null) {
        val joinPacket = new PlayerJoinPacket(
          server.getNextSequenceNumber,
          entry.playerId,
          instancePlayer.getPosition,
          instancePlayer.getColorRGB,
          instancePlayer.getName,
          instancePlayer.getHealth,
          instancePlayer.getCharacterId
        )
        instance.broadcastToInstance(joinPacket)
      }
    }

    println(s"RankedQueue: Started ranked duel (lobby ${lobby.id}) with ${entries.size} players on $worldFileName")
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
