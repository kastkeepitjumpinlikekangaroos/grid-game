package com.gridgame.client

import com.gridgame.common.Constants
import com.gridgame.common.model.Direction
import com.gridgame.common.model.Item
import com.gridgame.common.model.Player
import com.gridgame.common.model.Position
import com.gridgame.common.model.Projectile
import com.gridgame.common.model.WorldData
import com.gridgame.common.protocol._

import java.net.InetAddress
import java.net.UnknownHostException
import java.util.UUID
import java.util.concurrent.BlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
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
  private val projectiles: ConcurrentHashMap[Int, Projectile] = new ConcurrentHashMap()
  private val items: ConcurrentHashMap[Int, Item] = new ConcurrentHashMap()
  private val inventory: CopyOnWriteArrayList[Item] = new CopyOnWriteArrayList()
  private val incomingPackets: BlockingQueue[Packet] = new LinkedBlockingQueue()
  private val sequenceNumber: AtomicInteger = new AtomicInteger(0)
  private val movementBlockedUntil: AtomicLong = new AtomicLong(0)

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

  def rejoin(): Unit = {
    if (!isDead) return

    // Reset local state
    isDead = false
    localHealth.set(Constants.MAX_HEALTH)

    // Get a new spawn point
    val world = currentWorld.get()
    val newSpawn = world.getValidSpawnPoint()
    localPosition.set(newSpawn)
    localDirection.set(Direction.Down)

    // Clear local projectiles and inventory
    projectiles.clear()
    inventory.clear()

    // Send join packet to server
    sendJoinPacket()

    println(s"GameClient: Rejoined at $newSpawn")
  }

  def movePlayer(dx: Int, dy: Int): Unit = {
    // Check if movement is blocked from burst shot
    if (System.currentTimeMillis() < movementBlockedUntil.get()) {
      return
    }

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

  def shoot(): Unit = {
    if (isDead) return

    // Shoot in the direction the player is facing
    val direction = localDirection.get()
    val (dx, dy) = direction match {
      case Direction.Up    => (0.0f, -1.0f)
      case Direction.Down  => (0.0f, 1.0f)
      case Direction.Left  => (-1.0f, 0.0f)
      case Direction.Right => (1.0f, 0.0f)
    }
    shootToward(dx, dy)
  }

  def shootToward(dx: Float, dy: Float): Unit = {
    if (isDead) return

    val pos = localPosition.get()
    val packet = new ProjectilePacket(
      sequenceNumber.getAndIncrement(),
      localPlayerId,
      pos.getX.toFloat,
      pos.getY.toFloat,
      localColorRGB,
      0, // Server assigns real ID
      dx, dy,
      ProjectileAction.SPAWN
    )
    networkThread.send(packet)
  }

  def shootAllDirections(): Unit = {
    if (isDead) return

    val pos = localPosition.get()

    // Block movement for 500ms
    movementBlockedUntil.set(System.currentTimeMillis() + Constants.BURST_SHOT_MOVEMENT_BLOCK_MS)

    // Shoot in all 4 cardinal directions using dx/dy
    val velocities = Seq(
      (0.0f, -1.0f),  // Up
      (0.0f, 1.0f),   // Down
      (-1.0f, 0.0f),  // Left
      (1.0f, 0.0f)    // Right
    )
    velocities.foreach { case (dx, dy) =>
      val packet = new ProjectilePacket(
        sequenceNumber.getAndIncrement(),
        localPlayerId,
        pos.getX.toFloat,
        pos.getY.toFloat,
        localColorRGB,
        0, // Server assigns real ID
        dx, dy,
        ProjectileAction.SPAWN
      )
      networkThread.send(packet)
    }
  }

  def isMovementBlocked: Boolean = {
    System.currentTimeMillis() < movementBlockedUntil.get()
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

    // Handle projectile updates for all players (including local player's own projectiles)
    if (packet.getType == PacketType.PROJECTILE_UPDATE) {
      handleProjectileUpdate(packet.asInstanceOf[ProjectilePacket])
      return
    }

    // Handle item updates
    if (packet.getType == PacketType.ITEM_UPDATE) {
      handleItemUpdate(packet.asInstanceOf[ItemPacket])
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

  private def handleProjectileUpdate(packet: ProjectilePacket): Unit = {
    val projectileId = packet.getProjectileId

    packet.getAction match {
      case ProjectileAction.SPAWN =>
        val projectile = new Projectile(
          projectileId,
          packet.getPlayerId,
          packet.getX,
          packet.getY,
          packet.getDx,
          packet.getDy,
          packet.getColorRGB
        )
        projectiles.put(projectileId, projectile)

      case ProjectileAction.MOVE =>
        val projectile = projectiles.get(projectileId)
        if (projectile == null) {
          // Create projectile if we missed the spawn
          val newProjectile = new Projectile(
            projectileId,
            packet.getPlayerId,
            packet.getX,
            packet.getY,
            packet.getDx,
            packet.getDy,
            packet.getColorRGB
          )
          projectiles.put(projectileId, newProjectile)
        }
        // Note: Server already moved it, we get updated position in packet
        // For now just ensure it exists for rendering at the new position
        val existingProjectile = projectiles.get(projectileId)
        if (existingProjectile != null) {
          // Update position by replacing with new projectile at packet position
          val updatedProjectile = new Projectile(
            projectileId,
            packet.getPlayerId,
            packet.getX,
            packet.getY,
            packet.getDx,
            packet.getDy,
            packet.getColorRGB
          )
          projectiles.put(projectileId, updatedProjectile)
        }

      case ProjectileAction.HIT =>
        projectiles.remove(projectileId)
        // Hit effect could be added here

      case ProjectileAction.DESPAWN =>
        projectiles.remove(projectileId)

      case _ =>
        // Unknown action
    }
  }

  private def handleItemUpdate(packet: ItemPacket): Unit = {
    packet.getAction match {
      case ItemAction.SPAWN =>
        val item = new Item(packet.getItemId, packet.getX, packet.getY, packet.getItemType)
        items.put(packet.getItemId, item)

      case ItemAction.PICKUP =>
        items.remove(packet.getItemId)
        if (packet.getPlayerId.equals(localPlayerId)) {
          val item = new Item(packet.getItemId, packet.getX, packet.getY, packet.getItemType)
          inventory.add(item)
        }

      case ItemAction.INVENTORY =>
        if (packet.getPlayerId.equals(localPlayerId)) {
          val item = new Item(packet.getItemId, packet.getX, packet.getY, packet.getItemType)
          inventory.add(item)
        }

      case _ =>
        // Unknown action
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

  def getProjectiles: ConcurrentHashMap[Int, Projectile] = projectiles

  def getItems: ConcurrentHashMap[Int, Item] = items

  def getInventory: CopyOnWriteArrayList[Item] = inventory

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
