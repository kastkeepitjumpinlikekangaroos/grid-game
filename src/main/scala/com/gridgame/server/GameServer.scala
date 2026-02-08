package com.gridgame.server

import com.gridgame.common.Constants
import com.gridgame.common.model.Item
import com.gridgame.common.model.Player
import com.gridgame.common.model.Projectile
import com.gridgame.common.model.WorldData
import com.gridgame.common.protocol._
import com.gridgame.common.world.WorldLoader

import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketException
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import scala.jdk.CollectionConverters._

class GameServer(port: Int, val worldFile: String = "") {
  private var socket: DatagramSocket = _
  private val registry = new ClientRegistry()
  private val projectileManager = new ProjectileManager(registry)
  private val itemManager = new ItemManager()
  private val handler = new ClientHandler(registry, this, projectileManager, itemManager)
  private val broadcastExecutor: ExecutorService = Executors.newFixedThreadPool(4)
  private val cleanupExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
  private val projectileExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
  private val itemSpawnExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
  private val sequenceNumber = new AtomicInteger(0)
  @volatile private var running = false
  private var world: WorldData = _

  def start(): Unit = {
    socket = new DatagramSocket(port)
    running = true

    println(s"Game server started on port $port")
    println(s"World file configured: '${worldFile}' (empty=${worldFile.isEmpty})")

    // Load world for collision detection
    if (worldFile.nonEmpty) {
      try {
        world = WorldLoader.loadFromFile(worldFile)
        println(s"Loaded world '${world.name}' (${world.width}x${world.height})")
      } catch {
        case e: Exception =>
          println(s"Warning: Could not load world file: ${e.getMessage}")
          world = WorldData.createEmpty(200, 200)
      }
    } else {
      world = WorldData.createEmpty(200, 200)
    }

    cleanupExecutor.scheduleAtFixedRate(
      new Runnable { def run(): Unit = cleanup() },
      5, 5, TimeUnit.SECONDS
    )

    // Schedule projectile tick
    projectileExecutor.scheduleAtFixedRate(
      new Runnable { def run(): Unit = tickProjectiles() },
      Constants.PROJECTILE_SPEED_MS.toLong,
      Constants.PROJECTILE_SPEED_MS.toLong,
      TimeUnit.MILLISECONDS
    )

    val spawnItems = () => {
      for (i <- 1 to 50) {
        spawnItem()
      }
    }

    spawnItems()

    // Schedule item spawning
    itemSpawnExecutor.scheduleAtFixedRate(
      new Runnable { def run(): Unit = spawnItems() },
      Constants.ITEM_SPAWN_INTERVAL_MS.toLong,
      Constants.ITEM_SPAWN_INTERVAL_MS.toLong,
      TimeUnit.MILLISECONDS
    )

    receiveLoop()
  }

  private def receiveLoop(): Unit = {
    val buffer = new Array[Byte](Constants.PACKET_SIZE)

    while (running) {
      try {
        val dgram = new DatagramPacket(buffer, buffer.length)
        socket.receive(dgram)
        handlePacket(dgram)
      } catch {
        case e: IOException =>
          if (running) {
            System.err.println(s"Error receiving packet: ${e.getMessage}")
          }
      }
    }
  }

  private def handlePacket(dgram: DatagramPacket): Unit = {
    try {
      val data = dgram.getData
      val packet = PacketSerializer.deserialize(data)

      val address = dgram.getAddress
      val port = dgram.getPort

      val shouldBroadcast = handler.processPacket(packet, address, port)

      if (shouldBroadcast) {
        // Inject the server's authoritative health for player packets
        val packetToBroadcast = packet.getType match {
          case PacketType.PLAYER_UPDATE =>
            val updatePacket = packet.asInstanceOf[PlayerUpdatePacket]
            val player = registry.get(updatePacket.getPlayerId)
            if (player != null) {
              new PlayerUpdatePacket(
                updatePacket.getSequenceNumber,
                updatePacket.getPlayerId,
                updatePacket.getTimestamp,
                updatePacket.getPosition,
                updatePacket.getColorRGB,
                player.getHealth
              )
            } else {
              packet
            }
          case PacketType.PLAYER_JOIN =>
            val joinPacket = packet.asInstanceOf[PlayerJoinPacket]
            val player = registry.get(joinPacket.getPlayerId)
            if (player != null) {
              new PlayerJoinPacket(
                joinPacket.getSequenceNumber,
                joinPacket.getPlayerId,
                joinPacket.getTimestamp,
                joinPacket.getPosition,
                joinPacket.getColorRGB,
                joinPacket.getPlayerName,
                player.getHealth
              )
            } else {
              packet
            }
          case _ => packet
        }
        broadcast(packetToBroadcast, packet.getPlayerId)
      }
    } catch {
      case e: IllegalArgumentException =>
        System.err.println(s"Invalid packet received: ${e.getMessage}")
    }
  }

  private def broadcast(packet: Packet, excludePlayerId: UUID): Unit = {
    val data = packet.serialize()
    val players = registry.getAll

    broadcastExecutor.submit(new Runnable {
      def run(): Unit = {
        players.asScala.foreach { player =>
          if (!player.getId.equals(excludePlayerId)) {
            sendTo(data, player.getAddress, player.getPort)
          }
        }
      }
    })
  }

  private def sendTo(data: Array[Byte], address: InetAddress, port: Int): Unit = {
    try {
      val dgram = new DatagramPacket(data, data.length, address, port)
      socket.send(dgram)
    } catch {
      case e: IOException =>
        System.err.println(s"Error sending to ${address.getHostAddress}:$port - ${e.getMessage}")
    }
  }

  def sendPacketTo(packet: Packet, address: InetAddress, port: Int): Unit = {
    sendTo(packet.serialize(), address, port)
  }

  def getNextSequenceNumber: Int = sequenceNumber.getAndIncrement()

  private def tickProjectiles(): Unit = {
    if (!running || world == null) return

    val events = projectileManager.tick(world)
    events.foreach {
      case ProjectileMoved(projectile) =>
        val packet = new ProjectilePacket(
          sequenceNumber.getAndIncrement(),
          projectile.ownerId,
          projectile.getX, projectile.getY,
          projectile.colorRGB,
          projectile.id,
          projectile.dx, projectile.dy,
          ProjectileAction.MOVE
        )
        broadcastToAll(packet)

      case ProjectileHit(projectile, targetId) =>
        // Send projectile hit packet
        val hitPacket = new ProjectilePacket(
          sequenceNumber.getAndIncrement(),
          projectile.ownerId,
          projectile.getX, projectile.getY,
          projectile.colorRGB,
          projectile.id,
          projectile.dx, projectile.dy,
          ProjectileAction.HIT,
          targetId
        )
        broadcastToAll(hitPacket)

        // Send player update with new health
        val target = registry.get(targetId)
        if (target != null) {
          val updatePacket = new PlayerUpdatePacket(
            sequenceNumber.getAndIncrement(),
            targetId,
            target.getPosition,
            target.getColorRGB,
            target.getHealth
          )
          broadcastToAll(updatePacket)
        }

      case ProjectileDespawned(projectile) =>
        val packet = new ProjectilePacket(
          sequenceNumber.getAndIncrement(),
          projectile.ownerId,
          projectile.getX, projectile.getY,
          projectile.colorRGB,
          projectile.id,
          projectile.dx, projectile.dy,
          ProjectileAction.DESPAWN
        )
        broadcastToAll(packet)
    }
  }

  private def broadcastToAll(packet: Packet): Unit = {
    val data = packet.serialize()
    val players = registry.getAll

    broadcastExecutor.submit(new Runnable {
      def run(): Unit = {
        players.asScala.foreach { player =>
          sendTo(data, player.getAddress, player.getPort)
        }
      }
    })
  }

  def broadcastPlayerUpdate(packet: PlayerUpdatePacket): Unit = {
    broadcastToAll(packet)
  }

  def getWorld: WorldData = world

  def broadcastTileUpdate(playerId: UUID, x: Int, y: Int, tileId: Int): Unit = {
    val packet = new TileUpdatePacket(
      sequenceNumber.getAndIncrement(),
      playerId,
      x, y,
      tileId
    )
    broadcastToAll(packet)
  }

  def broadcastProjectileSpawn(projectile: Projectile): Unit = {
    val packet = new ProjectilePacket(
      sequenceNumber.getAndIncrement(),
      projectile.ownerId,
      projectile.getX, projectile.getY,
      projectile.colorRGB,
      projectile.id,
      projectile.dx, projectile.dy,
      ProjectileAction.SPAWN
    )
    broadcastToAll(packet)
  }

  private def spawnItem(): Unit = {
    if (!running || world == null) return
    itemManager.spawnRandomItem(world).foreach { event =>
      broadcastItemSpawn(event.item)
    }
  }

  def broadcastItemSpawn(item: Item): Unit = {
    val zeroUUID = new UUID(0L, 0L)
    val packet = new ItemPacket(
      sequenceNumber.getAndIncrement(),
      zeroUUID,
      item.x, item.y,
      item.itemType.id,
      item.id,
      ItemAction.SPAWN
    )
    broadcastToAll(packet)
  }

  def broadcastItemPickup(item: Item, playerId: UUID): Unit = {
    val packet = new ItemPacket(
      sequenceNumber.getAndIncrement(),
      playerId,
      item.x, item.y,
      item.itemType.id,
      item.id,
      ItemAction.PICKUP
    )
    broadcastToAll(packet)
  }

  private def cleanup(): Unit = {
    val timedOut = registry.getTimedOutClients

    timedOut.asScala.foreach { playerId =>
      val leavePacket = handler.handleTimeout(playerId, sequenceNumber.getAndIncrement())
      if (leavePacket != null) {
        broadcast(leavePacket, playerId)
      }
    }

    if (!timedOut.isEmpty || registry.size > 0) {
      println(s"Server stats: ${registry.size} players connected")
    }
  }

  def stop(): Unit = {
    println("Stopping server...")
    running = false

    if (socket != null && !socket.isClosed) {
      socket.close()
    }

    broadcastExecutor.shutdown()
    cleanupExecutor.shutdown()
    projectileExecutor.shutdown()
    itemSpawnExecutor.shutdown()

    try {
      if (!broadcastExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
        broadcastExecutor.shutdownNow()
      }
      if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
        cleanupExecutor.shutdownNow()
      }
      if (!projectileExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
        projectileExecutor.shutdownNow()
      }
      if (!itemSpawnExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
        itemSpawnExecutor.shutdownNow()
      }
    } catch {
      case _: InterruptedException =>
        broadcastExecutor.shutdownNow()
        cleanupExecutor.shutdownNow()
        projectileExecutor.shutdownNow()
        itemSpawnExecutor.shutdownNow()
        Thread.currentThread().interrupt()
    }

    println("Server stopped.")
  }

  def getPlayerCount: Int = registry.size
}
