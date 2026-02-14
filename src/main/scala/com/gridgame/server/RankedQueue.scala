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

case class QueueEntry(playerId: UUID, var characterId: Byte, elo: Int, joinTime: Long)

class RankedQueue(server: GameServer) {
  private val queue: CopyOnWriteArrayList[QueueEntry] = new CopyOnWriteArrayList[QueueEntry]()
  private val playerInQueue: ConcurrentHashMap[UUID, QueueEntry] = new ConcurrentHashMap[UUID, QueueEntry]()
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

  def addPlayer(playerId: UUID, characterId: Byte, elo: Int): Unit = {
    if (playerInQueue.containsKey(playerId)) return

    val entry = QueueEntry(playerId, characterId, elo, System.currentTimeMillis())
    queue.add(entry)
    playerInQueue.put(playerId, entry)
    println(s"RankedQueue: Player ${playerId.toString.substring(0, 8)} joined queue (elo=$elo, queue size=${queue.size()})")

    sendQueueStatus(playerId)
  }

  def removePlayer(playerId: UUID): Unit = {
    val entry = playerInQueue.remove(playerId)
    if (entry != null) {
      queue.remove(entry)
      println(s"RankedQueue: Player ${playerId.toString.substring(0, 8)} left queue (queue size=${queue.size()})")
    }
  }

  def updateCharacter(playerId: UUID, characterId: Byte): Unit = {
    val entry = playerInQueue.get(playerId)
    if (entry != null) {
      entry.characterId = characterId
      println(s"RankedQueue: Player ${playerId.toString.substring(0, 8)} changed character to $characterId")
    }
  }

  def isInQueue(playerId: UUID): Boolean = playerInQueue.containsKey(playerId)

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

  private def checkQueue(): Unit = {
    try {
      val now = System.currentTimeMillis()
      val queueSize = queue.size()

      if (queueSize >= Constants.MAX_LOBBY_PLAYERS) {
        // Full match: sort by ELO, take closest 8
        val sorted = queue.asScala.toSeq.sortBy(_.elo)
        val matchEntries = sorted.take(Constants.MAX_LOBBY_PLAYERS)
        startRankedMatch(matchEntries)
      } else if (queueSize >= 2) {
        // Check if oldest entry has waited > 60 seconds
        val oldest = queue.asScala.minBy(_.joinTime)
        val waitTime = now - oldest.joinTime
        if (waitTime > 60000) {
          val matchEntries = queue.asScala.toSeq
          startRankedMatch(matchEntries)
        }
      }

      // Send status updates to remaining queued players
      queue.asScala.foreach { entry =>
        sendQueueStatus(entry.playerId)
      }
    } catch {
      case e: Exception =>
        System.err.println(s"RankedQueue: Error in checkQueue: ${e.getMessage}")
    }
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
    entries.foreach { entry =>
      val p = server.getConnectedPlayer(entry.playerId)
      if (p != null) {
        val spawnPoint = instance.world.getValidSpawnPoint()
        val instancePlayer = new Player(entry.playerId, p.getName, spawnPoint, p.getColorRGB, Constants.MAX_HEALTH)
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
      entries.size.toByte, lobby.maxPlayers.toByte,
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

    println(s"RankedQueue: Started ranked match (lobby ${lobby.id}) with ${entries.size} players on $worldFileName")
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
