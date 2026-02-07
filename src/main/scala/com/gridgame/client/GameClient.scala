package com.gridgame.client

import com.gridgame.common.Constants
import com.gridgame.common.model.Player
import com.gridgame.common.model.Position
import com.gridgame.common.protocol._

import java.net.InetAddress
import java.net.UnknownHostException
import java.util.UUID
import java.util.concurrent.BlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class GameClient(serverHost: String, serverPort: Int) {
  private var networkThread: NetworkThread = _

  private val localPlayerId: UUID = UUID.randomUUID()
  private val localColorRGB: Int = Player.generateColorFromUUID(localPlayerId)
  private val localPosition: AtomicReference[Position] = new AtomicReference(
    new Position(Constants.GRID_SIZE / 2, Constants.GRID_SIZE / 2)
  )
  private val players: ConcurrentHashMap[UUID, Player] = new ConcurrentHashMap()
  private val lastSequence: ConcurrentHashMap[UUID, Integer] = new ConcurrentHashMap()
  private val incomingPackets: BlockingQueue[Packet] = new LinkedBlockingQueue()
  private val sequenceNumber: AtomicInteger = new AtomicInteger(0)

  @volatile private var running = false

  def connect(): Unit = {
    try {
      val serverAddress = InetAddress.getByName(serverHost)
      networkThread = new NetworkThread(this, serverAddress, serverPort)
      running = true

      networkThread.start()

      sendJoinPacket()

      startPacketProcessor()

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
    val current = localPosition.get()
    val newX = Math.max(0, Math.min(Constants.GRID_SIZE - 1, current.getX + dx))
    val newY = Math.max(0, Math.min(Constants.GRID_SIZE - 1, current.getY + dy))

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
      localColorRGB
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
    val playerId = packet.getPlayerId

    if (playerId.equals(localPlayerId)) {
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

  private def handlePlayerJoin(packet: PlayerJoinPacket): Unit = {
    val player = new Player(
      packet.getPlayerId,
      packet.getPlayerName,
      packet.getPosition,
      packet.getColorRGB
    )
    players.put(player.getId, player)

    println(s"GameClient: Player joined - ${player.getId.toString.substring(0, 8)} ('${player.getName}') at ${player.getPosition}")
  }

  private def handlePlayerUpdate(packet: PlayerUpdatePacket): Unit = {
    val playerId = packet.getPlayerId
    var player = players.get(playerId)

    if (player != null) {
      player.setPosition(packet.getPosition)
      player.setColorRGB(packet.getColorRGB)
    } else {
      player = new Player(playerId, "Player", packet.getPosition, packet.getColorRGB)
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
