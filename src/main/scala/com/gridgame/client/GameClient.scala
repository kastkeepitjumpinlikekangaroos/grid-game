package com.gridgame.client

import com.gridgame.common.Constants
import com.gridgame.common.model.Direction
import com.gridgame.common.model.Player
import com.gridgame.common.model.Position
import com.gridgame.common.model.WorldData
import com.gridgame.common.protocol._

import java.net.InetAddress
import java.net.UnknownHostException
import java.util.UUID
import java.util.concurrent.BlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class GameClient(serverHost: String, serverPort: Int, initialWorld: WorldData) {
  private var networkThread: NetworkThread = _

  private val localPlayerId: UUID = UUID.randomUUID()
  private val localColorRGB: Int = Player.generateColorFromUUID(localPlayerId)
  private val currentWorld: AtomicReference[WorldData] = new AtomicReference(initialWorld)
  private val localPosition: AtomicReference[Position] = new AtomicReference(
    initialWorld.getValidSpawnPoint()
  )
  private val localDirection: AtomicReference[Direction] = new AtomicReference(Direction.Down)
  private val localHealth: AtomicInteger = new AtomicInteger(Constants.MAX_HEALTH)
  private val players: ConcurrentHashMap[UUID, Player] = new ConcurrentHashMap()
  private val lastSequence: ConcurrentHashMap[UUID, Integer] = new ConcurrentHashMap()
  private val incomingPackets: BlockingQueue[Packet] = new LinkedBlockingQueue()
  private val sequenceNumber: AtomicInteger = new AtomicInteger(0)

  @volatile private var running = false
  @volatile private var isDead = false
  @volatile private var worldFileListener: String => Unit = _

  def connect(): Unit = {
    try {
      val serverAddress = InetAddress.getByName(serverHost)
      networkThread = new NetworkThread(this, serverAddress, serverPort)
      running = true

      networkThread.start()
      networkThread.waitForReady() // Wait for socket to be created

      startPacketProcessor()

      sendJoinPacket()

      println(s"GameClient: Connected to $serverHost:$serverPort as player ${localPlayerId.toString.substring(0, 8)}")
    } catch {
      case e: UnknownHostException =>
        System.err.println(s"GameClient: Unknown host - ${e.getMessage}")
    }
  }

  private def sendJoinPacket(): Unit = {
    val pos = localPosition.get()
    val packet = new PlayerJoinPacket(
      sequenceNumber.getAndIncrement(),
      localPlayerId,
      pos,
      localColorRGB,
      "Player"
    )
    networkThread.send(packet)
  }

  def sendHeartbeat(): Unit = {
    sendPositionUpdate(localPosition.get())
  }

  def movePlayer(dx: Int, dy: Int): Unit = {
    val world = currentWorld.get()
    val current = localPosition.get()
    val newX = Math.max(0, Math.min(world.width - 1, current.getX + dx))
    val newY = Math.max(0, Math.min(world.height - 1, current.getY + dy))

    // Update direction based on movement attempt (even if blocked)
    localDirection.set(Direction.fromMovement(dx, dy))

    // Check if destination is walkable
    if (!world.isWalkable(newX, newY)) {
      return
    }

    val newPos = new Position(newX, newY)

    if (!newPos.equals(current)) {
      localPosition.set(newPos)
      sendPositionUpdate(newPos)
    }
  }

  private def sendPositionUpdate(position: Position): Unit = {
    val packet = new PlayerUpdatePacket(
      sequenceNumber.getAndIncrement(),
      localPlayerId,
      position,
      localColorRGB,
      localHealth.get()
    )
    networkThread.send(packet)
  }

  def enqueuePacket(packet: Packet): Unit = {
    incomingPackets.offer(packet)
  }

  private def startPacketProcessor(): Unit = {
    val processor = new Thread(new Runnable {
      def run(): Unit = {
        while (running) {
          try {
            val packet = incomingPackets.take()
            processPacket(packet)
          } catch {
            case _: InterruptedException =>
              return
          }
        }
      }
    }, "PacketProcessor")
    processor.setDaemon(true)
    processor.start()
  }

  private def processPacket(packet: Packet): Unit = {
    println(s"GameClient: Processing packet type: ${packet.getType}")

    // Handle world info separately (doesn't have a real player ID)
    if (packet.getType == PacketType.WORLD_INFO) {
      println("GameClient: Received WORLD_INFO packet")
      handleWorldInfo(packet.asInstanceOf[WorldInfoPacket])
      return
    }

    val playerId = packet.getPlayerId

    // Handle updates for local player (health from server)
    if (playerId.equals(localPlayerId)) {
      packet.getType match {
        case PacketType.PLAYER_UPDATE =>
          val updatePacket = packet.asInstanceOf[PlayerUpdatePacket]
          val serverHealth = updatePacket.getHealth
          localHealth.set(serverHealth)
          if (serverHealth <= 0 && !isDead) {
            isDead = true
            println("GameClient: You have died!")
          }
        case _ =>
      }
      return
    }

    val lastSeq = lastSequence.getOrDefault(playerId, -1)
    if (packet.getSequenceNumber <= lastSeq) {
      return
    }
    lastSequence.put(playerId, packet.getSequenceNumber)

    packet.getType match {
      case PacketType.PLAYER_JOIN =>
        handlePlayerJoin(packet.asInstanceOf[PlayerJoinPacket])

      case PacketType.PLAYER_UPDATE =>
        handlePlayerUpdate(packet.asInstanceOf[PlayerUpdatePacket])

      case PacketType.PLAYER_LEAVE =>
        handlePlayerLeave(packet.asInstanceOf[PlayerLeavePacket])

      case _ =>
      // Ignore other packet types
    }
  }

  private def handleWorldInfo(packet: WorldInfoPacket): Unit = {
    val worldFile = packet.getWorldFile
    println(s"GameClient: Received world info from server: $worldFile")

    if (worldFileListener != null) {
      worldFileListener(worldFile)
    }
  }

  private def handlePlayerJoin(packet: PlayerJoinPacket): Unit = {
    val player = new Player(
      packet.getPlayerId,
      packet.getPlayerName,
      packet.getPosition,
      packet.getColorRGB,
      packet.getHealth
    )
    players.put(player.getId, player)

    println(s"GameClient: Player joined - ${player.getId.toString.substring(0, 8)} ('${player.getName}') at ${player.getPosition} with health ${player.getHealth}")
  }

  private def handlePlayerUpdate(packet: PlayerUpdatePacket): Unit = {
    val playerId = packet.getPlayerId
    var player = players.get(playerId)

    if (player != null) {
      val oldPos = player.getPosition
      val newPos = packet.getPosition
      val dx = newPos.getX - oldPos.getX
      val dy = newPos.getY - oldPos.getY
      if (dx != 0 || dy != 0) {
        player.setDirection(Direction.fromMovement(dx, dy))
      }
      player.setPosition(newPos)
      player.setColorRGB(packet.getColorRGB)
      player.setHealth(packet.getHealth)
    } else {
      player = new Player(playerId, "Player", packet.getPosition, packet.getColorRGB, packet.getHealth)
      players.put(playerId, player)
    }
  }

  private def handlePlayerLeave(packet: PlayerLeavePacket): Unit = {
    val playerId = packet.getPlayerId
    val player = players.remove(playerId)

    if (player != null) {
      println(s"GameClient: Player left - ${playerId.toString.substring(0, 8)} ('${player.getName}')")
    }
  }

  def getLocalPosition: Position = localPosition.get()

  def getLocalPlayerId: UUID = localPlayerId

  def getPlayers: ConcurrentHashMap[UUID, Player] = players

  def getLocalColorRGB: Int = localColorRGB

  def getLocalDirection: Direction = localDirection.get()

  def getLocalHealth: Int = localHealth.get()

  def getIsDead: Boolean = isDead

  def getWorld: WorldData = currentWorld.get()

  def setWorld(world: WorldData): Unit = {
    currentWorld.set(world)
    // Respawn at a valid position in the new world
    val newSpawn = world.getValidSpawnPoint()
    localPosition.set(newSpawn)
    sendPositionUpdate(newSpawn)
    println(s"GameClient: World changed to '${world.name}', respawned at $newSpawn")
  }

  def setWorldFileListener(listener: String => Unit): Unit = {
    worldFileListener = listener
  }

  def disconnect(): Unit = {
    if (!running) {
      return
    }

    running = false

    val leavePacket = new PlayerLeavePacket(
      sequenceNumber.getAndIncrement(),
      localPlayerId
    )
    networkThread.send(leavePacket)

    networkThread.shutdown()

    println("GameClient: Disconnected")
  }
}
