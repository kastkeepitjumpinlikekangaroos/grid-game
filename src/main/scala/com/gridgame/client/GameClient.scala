package com.gridgame.client

import com.gridgame.common.Constants
import com.gridgame.common.model.CharacterDef
import com.gridgame.common.model.CharacterId
import com.gridgame.common.model.Direction
import com.gridgame.common.model.Item
import com.gridgame.common.model.ItemType
import com.gridgame.common.model.LobbyInfo
import com.gridgame.common.model.Player
import com.gridgame.common.model.Position
import com.gridgame.common.model.Projectile
import com.gridgame.common.model.ProjectileType
import com.gridgame.common.model.ScoreEntry
import com.gridgame.common.model.Tile
import com.gridgame.common.model.WorldData
import com.gridgame.common.protocol._

import java.util.UUID
import java.util.concurrent.BlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

object ClientState {
  val CONNECTING = 0
  val LOBBY_BROWSER = 1
  val IN_LOBBY = 2
  val PLAYING = 3
  val SCOREBOARD = 4
}

class GameClient(serverHost: String, serverPort: Int, initialWorld: WorldData, var playerName: String = "Player") {
  private var networkThread: NetworkThread = _

  private var localPlayerId: UUID = UUID.randomUUID()
  private var localColorRGB: Int = Player.generateColorFromUUID(localPlayerId)
  private val currentWorld: AtomicReference[WorldData] = new AtomicReference(initialWorld)
  private val localPosition: AtomicReference[Position] = new AtomicReference(
    initialWorld.getValidSpawnPoint()
  )
  private val localDirection: AtomicReference[Direction] = new AtomicReference(Direction.Down)
  private val localHealth: AtomicInteger = new AtomicInteger(Constants.MAX_HEALTH)
  private val players: ConcurrentHashMap[UUID, Player] = new ConcurrentHashMap()
  private val projectiles: ConcurrentHashMap[Int, Projectile] = new ConcurrentHashMap()
  private val items: ConcurrentHashMap[Int, Item] = new ConcurrentHashMap()
  private val inventory: ConcurrentHashMap[Byte, ConcurrentLinkedDeque[Item]] = new ConcurrentHashMap()
  private val incomingPackets: BlockingQueue[Packet] = new LinkedBlockingQueue()
  private val sequenceNumber: AtomicInteger = new AtomicInteger(0)
  private val movementBlockedUntil: AtomicLong = new AtomicLong(0)
  private val lastMoveTime: AtomicLong = new AtomicLong(0)
  private val fastProjectilesUntil: AtomicLong = new AtomicLong(0)
  private val shieldUntil: AtomicLong = new AtomicLong(0)
  @volatile private var mouseWorldX: Double = 0.0
  @volatile private var mouseWorldY: Double = 0.0

  // Character selection state
  @volatile var selectedCharacterId: Byte = CharacterId.DEFAULT.id
  val lobbyCharacterSelections: ConcurrentHashMap[UUID, Byte] = new ConcurrentHashMap[UUID, Byte]()

  // Ability cooldown state
  private val lastQAbilityTime: AtomicLong = new AtomicLong(0)
  private val lastEAbilityTime: AtomicLong = new AtomicLong(0)
  private val frozenUntil: AtomicLong = new AtomicLong(0)

  // Charge shot state
  @volatile var isCharging: Boolean = false
  private val chargingStartTime: AtomicLong = new AtomicLong(0)
  private val lastChargingUpdateTime: AtomicLong = new AtomicLong(0)

  // Hit animation tracking
  private val playerHitTimes: ConcurrentHashMap[UUID, Long] = new ConcurrentHashMap()

  // Death animation tracking: (timestamp, worldX, worldY, colorRGB)
  private val deathAnimations: ConcurrentHashMap[UUID, Array[Long]] = new ConcurrentHashMap()
  private val localDeathTime: AtomicLong = new AtomicLong(0)

  // Teleport animation tracking: (timestamp, oldX, oldY, newX, newY, colorRGB)
  private val teleportAnimations: ConcurrentHashMap[UUID, Array[Long]] = new ConcurrentHashMap()

  @volatile private var running = false
  @volatile private var isDead = false
  @volatile private var movementInputActive = false
  @volatile private var worldFileListener: String => Unit = _
  @volatile private var rejoinListener: () => Unit = _
  @volatile var authResponseListener: (Boolean, UUID, String) => Unit = _

  // Lobby state
  @volatile var clientState: Int = ClientState.CONNECTING
  @volatile var currentLobbyId: Short = 0
  @volatile var currentLobbyName: String = ""
  @volatile var currentLobbyMapIndex: Int = 0
  @volatile var currentLobbyDuration: Int = Constants.DEFAULT_GAME_DURATION_MIN
  @volatile var currentLobbyPlayerCount: Int = 0
  @volatile var currentLobbyMaxPlayers: Int = Constants.MAX_LOBBY_PLAYERS
  @volatile var isLobbyHost: Boolean = false
  val lobbyList: CopyOnWriteArrayList[LobbyInfo] = new CopyOnWriteArrayList[LobbyInfo]()

  // Game stats
  @volatile var killCount: Int = 0
  @volatile var deathCount: Int = 0
  private var gameTimeSyncRemaining: Int = 0
  private var gameTimeSyncTimestamp: Long = 0L
  val scoreboard: CopyOnWriteArrayList[ScoreEntry] = new CopyOnWriteArrayList[ScoreEntry]()
  @volatile var isRespawning: Boolean = false

  def gameTimeRemaining: Int = {
    if (gameTimeSyncTimestamp == 0L) return 0
    val elapsed = ((System.currentTimeMillis() - gameTimeSyncTimestamp) / 1000).toInt
    Math.max(0, gameTimeSyncRemaining - elapsed)
  }

  // Kill feed: (timestamp, killerName, victimName)
  val killFeed: CopyOnWriteArrayList[Array[AnyRef]] = new CopyOnWriteArrayList[Array[AnyRef]]()

  // Match history state: (matchId, mapIndex, durationMin, playedAt, kills, deaths, rank, totalPlayers)
  val matchHistory: CopyOnWriteArrayList[Array[Int]] = new CopyOnWriteArrayList[Array[Int]]()
  @volatile var totalKillsStat: Int = 0
  @volatile var totalDeathsStat: Int = 0
  @volatile var matchesPlayedStat: Int = 0
  @volatile var winsStat: Int = 0

  // Listeners
  // Ranked queue state
  @volatile var isInRankedQueue: Boolean = false
  @volatile var rankedElo: Int = 1000
  @volatile var rankedQueueSize: Int = 0
  @volatile var rankedQueueWaitTime: Int = 0
  @volatile var rankedQueueListener: () => Unit = _
  @volatile var rankedMatchFoundListener: () => Unit = _

  @volatile var matchHistoryListener: () => Unit = _
  @volatile var lobbyListListener: () => Unit = _
  @volatile var lobbyJoinedListener: () => Unit = _
  @volatile var lobbyUpdatedListener: () => Unit = _
  @volatile var gameStartingListener: () => Unit = _
  @volatile var gameOverListener: () => Unit = _
  @volatile var lobbyClosedListener: () => Unit = _

  def connect(): Unit = {
    try {
      networkThread = new NetworkThread(this, serverHost, serverPort)
      running = true

      networkThread.start()
      networkThread.waitForReady() // Wait for TCP + UDP channels to be ready

      startPacketProcessor()

      println(s"GameClient: Connected to $serverHost:$serverPort (awaiting auth)")
    } catch {
      case e: Exception =>
        System.err.println(s"GameClient: Connection error - ${e.getMessage}")
        throw e
    }
  }

  def sendAuthRequest(username: String, password: String, isSignup: Boolean): Unit = {
    val action = if (isSignup) AuthAction.SIGNUP else AuthAction.LOGIN
    val packet = new AuthRequestPacket(
      sequenceNumber.getAndIncrement(),
      action,
      username,
      password
    )
    networkThread.send(packet)
  }

  def completeAuthAndJoin(assignedUUID: UUID, username: String): Unit = {
    localPlayerId = assignedUUID
    localColorRGB = Player.generateColorFromUUID(assignedUUID)
    playerName = username

    sendJoinPacket()

    clientState = ClientState.LOBBY_BROWSER

    println(s"GameClient: Authenticated as '$username' (${assignedUUID.toString.substring(0, 8)})")
  }

  private def sendJoinPacket(): Unit = {
    val pos = localPosition.get()
    val packet = new PlayerJoinPacket(
      sequenceNumber.getAndIncrement(),
      localPlayerId,
      pos,
      localColorRGB,
      playerName
    )
    networkThread.send(packet)
  }

  def sendHeartbeat(): Unit = {
    val packet = new HeartbeatPacket(
      sequenceNumber.getAndIncrement(),
      localPlayerId
    )
    networkThread.send(packet)
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

    // Reset effect timers
    fastProjectilesUntil.set(0)
    shieldUntil.set(0)
    localDeathTime.set(0)
    lastQAbilityTime.set(0)
    lastEAbilityTime.set(0)
    frozenUntil.set(0)

    // Send join packet to server
    sendJoinPacket()

    if (rejoinListener != null) rejoinListener()

    println(s"GameClient: Rejoined at $newSpawn")
  }

  def movePlayer(dx: Int, dy: Int): Unit = {
    // Check if frozen
    if (isFrozen) return

    // Check if movement is blocked from burst shot
    if (System.currentTimeMillis() < movementBlockedUntil.get()) {
      return
    }

    val world = currentWorld.get()
    val current = localPosition.get()

    // Update direction based on movement attempt (even if blocked)
    localDirection.set(Direction.fromMovement(dx, dy))

    var finalX = current.getX
    var finalY = current.getY

    val targetX = Math.max(0, Math.min(world.width - 1, current.getX + dx))
    val targetY = Math.max(0, Math.min(world.height - 1, current.getY + dy))

    if (world.isWalkable(targetX, targetY)) {
      finalX = targetX
      finalY = targetY
    } else if (dx != 0 && dy != 0) {
      // Diagonal blocked — try sliding along each axis
      if (world.isWalkable(targetX, current.getY)) {
        finalX = targetX
      } else if (world.isWalkable(current.getX, targetY)) {
        finalY = targetY
      }
    }

    val newPos = new Position(finalX, finalY)

    if (!newPos.equals(current)) {
      localPosition.set(newPos)
      lastMoveTime.set(System.currentTimeMillis())
      sendPositionUpdate(newPos)
    }
  }

  private def getEffectFlags: Int = {
    (if (hasShield) 0x01 else 0) | (if (hasGemBoost) 0x02 else 0) | (if (isFrozen) 0x04 else 0)
  }

  private def sendPositionUpdate(position: Position): Unit = {
    val packet = new PlayerUpdatePacket(
      sequenceNumber.getAndIncrement(),
      localPlayerId,
      position,
      localColorRGB,
      localHealth.get(),
      getChargeLevel,
      getEffectFlags
    )
    networkThread.send(packet)
  }

  def sendChargingUpdate(): Unit = {
    val now = System.currentTimeMillis()
    if (now - lastChargingUpdateTime.get() < 100) return // Rate limit to every 100ms
    lastChargingUpdateTime.set(now)

    val pos = localPosition.get()
    val packet = new PlayerUpdatePacket(
      sequenceNumber.getAndIncrement(),
      localPlayerId,
      pos,
      localColorRGB,
      localHealth.get(),
      getChargeLevel,
      getEffectFlags
    )
    networkThread.send(packet)
  }

  def startCharging(): Unit = {
    isCharging = true
    chargingStartTime.set(System.currentTimeMillis())
  }

  def cancelCharging(): Unit = {
    isCharging = false
    // Send update so remote clients see charge drop to 0
    sendPositionUpdate(localPosition.get())
  }

  def getChargeLevel: Int = {
    if (!isCharging) return 0
    val elapsed = System.currentTimeMillis() - chargingStartTime.get()
    Math.min(100, (elapsed * 100 / Constants.CHARGE_MAX_MS).toInt)
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
    shootToward(dx, dy, 0)
  }

  def shootToward(dx: Float, dy: Float, chargeLevel: Int = 0): Unit = {
    if (isDead) return

    val pos = localPosition.get()
    val chargeByte = Math.min(100, Math.max(0, chargeLevel)).toByte

    if (hasGemBoost) {
      // Shoot 3 projectiles in a narrow cone (center ± 15 degrees)
      val angle = Math.PI / 36.0 // 5 degrees
      val offsets = Seq(0.0, -angle, angle)
      offsets.foreach { theta =>
        val cos = Math.cos(theta).toFloat
        val sin = Math.sin(theta).toFloat
        val rdx = dx * cos - dy * sin
        val rdy = dx * sin + dy * cos
        val packet = new ProjectilePacket(
          sequenceNumber.getAndIncrement(),
          localPlayerId,
          pos.getX.toFloat,
          pos.getY.toFloat,
          localColorRGB,
          0,
          rdx, rdy,
          ProjectileAction.SPAWN,
          null,
          chargeByte
        )
        networkThread.send(packet)
      }
    } else {
      val packet = new ProjectilePacket(
        sequenceNumber.getAndIncrement(),
        localPlayerId,
        pos.getX.toFloat,
        pos.getY.toFloat,
        localColorRGB,
        0,
        dx, dy,
        ProjectileAction.SPAWN,
        null,
        chargeByte
      )
      networkThread.send(packet)
    }
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

  def shootAbility(slot: Int): Unit = {
    if (isDead || isFrozen) return

    val charDef = getSelectedCharacterDef
    val (abilityDef, lastAbilityTime) = slot match {
      case 0 => (charDef.qAbility, lastQAbilityTime)
      case 1 => (charDef.eAbility, lastEAbilityTime)
      case _ => return
    }

    val now = System.currentTimeMillis()
    if (now - lastAbilityTime.get() < abilityDef.cooldownMs) return

    lastAbilityTime.set(now)

    val pos = localPosition.get()
    val dx = (mouseWorldX - pos.getX).toFloat
    val dy = (mouseWorldY - pos.getY).toFloat
    val len = Math.sqrt(dx * dx + dy * dy).toFloat
    val (ndx, ndy) = if (len > 0.01f) (dx / len, dy / len) else {
      val dir = localDirection.get()
      dir match {
        case Direction.Up    => (0.0f, -1.0f)
        case Direction.Down  => (0.0f, 1.0f)
        case Direction.Left  => (-1.0f, 0.0f)
        case Direction.Right => (1.0f, 0.0f)
      }
    }

    val packet = new ProjectilePacket(
      sequenceNumber.getAndIncrement(),
      localPlayerId,
      pos.getX.toFloat,
      pos.getY.toFloat,
      localColorRGB,
      0,
      ndx, ndy,
      ProjectileAction.SPAWN,
      null,
      0.toByte,
      abilityDef.projectileType
    )
    networkThread.send(packet)
  }

  def isFrozen: Boolean = System.currentTimeMillis() < frozenUntil.get()

  def getQCooldownFraction: Float = {
    val cooldownMs = getSelectedCharacterDef.qAbility.cooldownMs
    val elapsed = System.currentTimeMillis() - lastQAbilityTime.get()
    if (elapsed >= cooldownMs) 0.0f
    else 1.0f - (elapsed.toFloat / cooldownMs)
  }

  def getECooldownFraction: Float = {
    val cooldownMs = getSelectedCharacterDef.eAbility.cooldownMs
    val elapsed = System.currentTimeMillis() - lastEAbilityTime.get()
    if (elapsed >= cooldownMs) 0.0f
    else 1.0f - (elapsed.toFloat / cooldownMs)
  }

  def getQCooldownRemaining: Float = {
    val cooldownMs = getSelectedCharacterDef.qAbility.cooldownMs
    val remaining = lastQAbilityTime.get() + cooldownMs - System.currentTimeMillis()
    if (remaining <= 0) 0.0f else remaining / 1000.0f
  }

  def getECooldownRemaining: Float = {
    val cooldownMs = getSelectedCharacterDef.eAbility.cooldownMs
    val remaining = lastEAbilityTime.get() + cooldownMs - System.currentTimeMillis()
    if (remaining <= 0) 0.0f else remaining / 1000.0f
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
    // Handle auth response
    if (packet.getType == PacketType.AUTH_RESPONSE) {
      val authPacket = packet.asInstanceOf[AuthResponsePacket]
      if (authResponseListener != null) {
        authResponseListener(authPacket.getSuccess, authPacket.getAssignedUUID, authPacket.getMessage)
      }
      return
    }

    // Handle match history
    if (packet.getType == PacketType.MATCH_HISTORY) {
      handleMatchHistory(packet.asInstanceOf[MatchHistoryPacket])
      return
    }

    // Handle ranked queue
    if (packet.getType == PacketType.RANKED_QUEUE) {
      handleRankedQueue(packet.asInstanceOf[RankedQueuePacket])
      return
    }

    // Handle lobby actions
    if (packet.getType == PacketType.LOBBY_ACTION) {
      handleLobbyAction(packet.asInstanceOf[LobbyActionPacket])
      return
    }

    // Handle game events
    if (packet.getType == PacketType.GAME_EVENT) {
      handleGameEvent(packet.asInstanceOf[GameEventPacket])
      return
    }

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

    // Handle tile updates
    if (packet.getType == PacketType.TILE_UPDATE) {
      handleTileUpdate(packet.asInstanceOf[TileUpdatePacket])
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

          // Handle frozen flag (bit 2)
          val flags = updatePacket.getEffectFlags
          if ((flags & 0x04) != 0) {
            frozenUntil.set(System.currentTimeMillis() + 1000)
          }

          // Handle position correction (e.g. tentacle pull)
          val serverPos = updatePacket.getPosition
          val localPos = localPosition.get()
          if (serverPos.getX != localPos.getX || serverPos.getY != localPos.getY) {
            localPosition.set(serverPos)
          }

          if (serverHealth <= 0 && !isDead) {
            isDead = true
            isRespawning = true
            localDeathTime.set(System.currentTimeMillis())
            println("GameClient: You have died! Auto-respawning in 3s...")
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

  private def handleLobbyAction(packet: LobbyActionPacket): Unit = {
    packet.getAction match {
      case LobbyAction.LIST_ENTRY =>
        val info = new LobbyInfo(
          packet.getLobbyId, packet.getLobbyName, packet.getMapIndex & 0xFF,
          packet.getDurationMinutes & 0xFF, packet.getPlayerCount & 0xFF,
          packet.getMaxPlayers & 0xFF, packet.getLobbyStatus & 0xFF
        )
        lobbyList.add(info)

      case LobbyAction.LIST_END =>
        if (lobbyListListener != null) lobbyListListener()

      case LobbyAction.JOINED =>
        currentLobbyId = packet.getLobbyId
        currentLobbyName = packet.getLobbyName
        currentLobbyMapIndex = packet.getMapIndex & 0xFF
        currentLobbyDuration = packet.getDurationMinutes & 0xFF
        currentLobbyPlayerCount = packet.getPlayerCount & 0xFF
        currentLobbyMaxPlayers = packet.getMaxPlayers & 0xFF
        clientState = ClientState.IN_LOBBY
        if (lobbyJoinedListener != null) lobbyJoinedListener()

      case LobbyAction.PLAYER_JOINED | LobbyAction.PLAYER_LEFT =>
        currentLobbyPlayerCount = packet.getPlayerCount & 0xFF
        if (lobbyUpdatedListener != null) lobbyUpdatedListener()

      case LobbyAction.CONFIG_UPDATE =>
        currentLobbyMapIndex = packet.getMapIndex & 0xFF
        currentLobbyDuration = packet.getDurationMinutes & 0xFF
        if (lobbyUpdatedListener != null) lobbyUpdatedListener()

      case LobbyAction.GAME_STARTING =>
        clientState = ClientState.PLAYING
        killCount = 0
        deathCount = 0
        gameTimeSyncRemaining = currentLobbyDuration * 60
        gameTimeSyncTimestamp = System.currentTimeMillis()
        scoreboard.clear()
        killFeed.clear()
        isDead = false
        isRespawning = false
        localHealth.set(Constants.MAX_HEALTH)
        players.clear()
        projectiles.clear()
        items.clear()
        inventory.clear()
        if (gameStartingListener != null) gameStartingListener()

      case LobbyAction.CHARACTER_SELECT =>
        lobbyCharacterSelections.put(packet.getPlayerId, packet.getCharacterId)
        if (lobbyUpdatedListener != null) lobbyUpdatedListener()

      case LobbyAction.LOBBY_CLOSED =>
        clientState = ClientState.LOBBY_BROWSER
        currentLobbyId = 0
        if (lobbyClosedListener != null) lobbyClosedListener()

      case _ =>
        println(s"GameClient: Unknown lobby action ${packet.getAction}")
    }
  }

  private def handleGameEvent(packet: GameEventPacket): Unit = {
    packet.getEventType match {
      case GameEvent.KILL =>
        val killerId = packet.getPlayerId
        val victimId = packet.getTargetId

        if (killerId.equals(localPlayerId)) {
          killCount = packet.getKills.toInt
          deathCount = packet.getDeaths.toInt
        }
        if (victimId != null && victimId.equals(localPlayerId)) {
          deathCount += 1
        }

        // Add to kill feed
        val killerName = if (killerId.equals(localPlayerId)) "You" else {
          val p = players.get(killerId)
          if (p != null) p.getName else killerId.toString.substring(0, 8)
        }
        val victimName = if (victimId != null && victimId.equals(localPlayerId)) "You" else {
          if (victimId != null) {
            val p = players.get(victimId)
            if (p != null) p.getName else victimId.toString.substring(0, 8)
          } else "?"
        }
        killFeed.add(Array(System.currentTimeMillis().asInstanceOf[AnyRef], killerName.asInstanceOf[AnyRef], victimName.asInstanceOf[AnyRef]))
        // Keep only last 5
        while (killFeed.size() > 5) killFeed.remove(0)

      case GameEvent.TIME_SYNC =>
        gameTimeSyncRemaining = packet.getRemainingSeconds
        gameTimeSyncTimestamp = System.currentTimeMillis()

      case GameEvent.GAME_OVER =>
        scoreboard.clear()

      case GameEvent.SCORE_ENTRY =>
        val entry = new ScoreEntry(packet.getPlayerId, packet.getKills.toInt, packet.getDeaths.toInt, packet.getRank.toInt)
        scoreboard.add(entry)

      case GameEvent.SCORE_END =>
        clientState = ClientState.SCOREBOARD
        if (gameOverListener != null) gameOverListener()

      case GameEvent.RESPAWN =>
        if (packet.getPlayerId.equals(localPlayerId)) {
          isDead = false
          isRespawning = false
          localHealth.set(Constants.MAX_HEALTH)
          val spawnX = packet.getSpawnX.toInt
          val spawnY = packet.getSpawnY.toInt
          localPosition.set(new Position(spawnX, spawnY))
          localDeathTime.set(0)
          fastProjectilesUntil.set(0)
          shieldUntil.set(0)
          if (rejoinListener != null) rejoinListener()
          println(s"GameClient: Auto-respawned at ($spawnX, $spawnY)")
        }

      case _ =>
        println(s"GameClient: Unknown game event ${packet.getEventType}")
    }
  }

  def getSelectedCharacterDef: CharacterDef = CharacterDef.get(selectedCharacterId)

  def selectCharacter(id: Byte): Unit = {
    selectedCharacterId = id
    if (currentLobbyId != 0) {
      val packet = new LobbyActionPacket(
        sequenceNumber.getAndIncrement(), localPlayerId, Packet.getCurrentTimestamp,
        LobbyAction.CHARACTER_SELECT, currentLobbyId,
        0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, "", id
      )
      networkThread.send(packet)
    }
  }

  private def handleMatchHistory(packet: MatchHistoryPacket): Unit = {
    packet.getAction match {
      case MatchHistoryAction.STATS =>
        totalKillsStat = packet.getTotalKills
        totalDeathsStat = packet.getTotalDeaths
        matchesPlayedStat = packet.getMatchesPlayed
        winsStat = packet.getWins
        rankedElo = packet.getElo.toInt

      case MatchHistoryAction.ENTRY =>
        matchHistory.add(Array(
          packet.getMatchId, packet.getMapIndex & 0xFF, packet.getDuration & 0xFF,
          packet.getPlayedAt, packet.getKills.toInt, packet.getDeaths.toInt,
          packet.getRank & 0xFF, packet.getTotalPlayers & 0xFF
        ))

      case MatchHistoryAction.END =>
        if (matchHistoryListener != null) matchHistoryListener()

      case _ =>
    }
  }

  private def handleRankedQueue(packet: RankedQueuePacket): Unit = {
    packet.getAction match {
      case RankedQueueAction.QUEUE_STATUS =>
        rankedQueueSize = packet.getQueueSize & 0xFF
        rankedElo = packet.getElo.toInt
        rankedQueueWaitTime = packet.getWaitTimeSeconds
        if (rankedQueueListener != null) rankedQueueListener()

      case RankedQueueAction.MATCH_FOUND =>
        isInRankedQueue = false
        currentLobbyId = packet.getLobbyId
        currentLobbyName = packet.getLobbyName
        currentLobbyMapIndex = packet.getMapIndex & 0xFF
        currentLobbyDuration = packet.getDurationMinutes & 0xFF
        currentLobbyPlayerCount = packet.getPlayerCount & 0xFF
        currentLobbyMaxPlayers = packet.getMaxPlayers & 0xFF
        clientState = ClientState.IN_LOBBY
        if (rankedMatchFoundListener != null) rankedMatchFoundListener()

      case _ =>
    }
  }

  // Lobby actions
  def requestLobbyList(): Unit = {
    lobbyList.clear()
    val packet = new LobbyActionPacket(
      sequenceNumber.getAndIncrement(), localPlayerId, LobbyAction.LIST_REQUEST
    )
    networkThread.send(packet)
  }

  def createLobby(name: String, mapIndex: Int, durationMinutes: Int): Unit = {
    isLobbyHost = true
    val packet = new LobbyActionPacket(
      sequenceNumber.getAndIncrement(), localPlayerId, LobbyAction.CREATE, 0.toShort,
      mapIndex.toByte, durationMinutes.toByte, 0.toByte, Constants.MAX_LOBBY_PLAYERS.toByte,
      0.toByte, name
    )
    networkThread.send(packet)
  }

  def joinLobby(lobbyId: Short): Unit = {
    isLobbyHost = false
    val packet = new LobbyActionPacket(
      sequenceNumber.getAndIncrement(), localPlayerId, LobbyAction.JOIN, lobbyId
    )
    networkThread.send(packet)
  }

  def leaveLobby(): Unit = {
    val packet = new LobbyActionPacket(
      sequenceNumber.getAndIncrement(), localPlayerId, LobbyAction.LEAVE
    )
    networkThread.send(packet)
    clientState = ClientState.LOBBY_BROWSER
    currentLobbyId = 0
    isLobbyHost = false
  }

  def startGame(): Unit = {
    val packet = new LobbyActionPacket(
      sequenceNumber.getAndIncrement(), localPlayerId, LobbyAction.START
    )
    networkThread.send(packet)
  }

  def requestMatchHistory(): Unit = {
    matchHistory.clear()
    val packet = new MatchHistoryPacket(
      sequenceNumber.getAndIncrement(), localPlayerId, MatchHistoryAction.QUERY
    )
    networkThread.send(packet)
  }

  def updateLobbyConfig(mapIndex: Int, durationMinutes: Int): Unit = {
    val packet = new LobbyActionPacket(
      sequenceNumber.getAndIncrement(), localPlayerId, LobbyAction.CONFIG_UPDATE, currentLobbyId,
      mapIndex.toByte, durationMinutes.toByte, 0.toByte, 0.toByte, 0.toByte, ""
    )
    networkThread.send(packet)
  }

  def returnToLobbyBrowser(): Unit = {
    clientState = ClientState.LOBBY_BROWSER
    currentLobbyId = 0
    isLobbyHost = false
    killCount = 0
    deathCount = 0
    gameTimeSyncRemaining = 0
    gameTimeSyncTimestamp = 0L
    scoreboard.clear()
    killFeed.clear()
    players.clear()
    projectiles.clear()
    items.clear()
    isDead = false
    isRespawning = false
  }

  // Ranked queue actions
  def queueRanked(): Unit = {
    isInRankedQueue = true
    val packet = new RankedQueuePacket(
      sequenceNumber.getAndIncrement(), localPlayerId,
      RankedQueueAction.QUEUE_JOIN, selectedCharacterId
    )
    networkThread.send(packet)
  }

  def leaveRankedQueue(): Unit = {
    isInRankedQueue = false
    val packet = new RankedQueuePacket(
      sequenceNumber.getAndIncrement(), localPlayerId, RankedQueueAction.QUEUE_LEAVE
    )
    networkThread.send(packet)
  }

  def changeRankedCharacter(id: Byte): Unit = {
    selectedCharacterId = id
    if (isInRankedQueue) {
      val packet = new RankedQueuePacket(
        sequenceNumber.getAndIncrement(), localPlayerId,
        RankedQueueAction.CHARACTER_CHANGE, id
      )
      networkThread.send(packet)
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
          packet.getColorRGB,
          packet.getChargeLevel,
          packet.getProjectileType
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
            packet.getColorRGB,
            packet.getChargeLevel,
            packet.getProjectileType
          )
          projectiles.put(projectileId, newProjectile)
        }
        val existingProjectile = projectiles.get(projectileId)
        if (existingProjectile != null) {
          val updatedProjectile = new Projectile(
            projectileId,
            packet.getPlayerId,
            packet.getX,
            packet.getY,
            packet.getDx,
            packet.getDy,
            packet.getColorRGB,
            existingProjectile.chargeLevel,
            existingProjectile.projectileType
          )
          projectiles.put(projectileId, updatedProjectile)
        }

      case ProjectileAction.HIT =>
        projectiles.remove(projectileId)
        val targetId = packet.getTargetId
        if (targetId != null) {
          playerHitTimes.put(targetId, System.currentTimeMillis())
        }

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
          addToInventory(item)
        }

      case ItemAction.INVENTORY =>
        if (packet.getPlayerId.equals(localPlayerId)) {
          val item = new Item(packet.getItemId, packet.getX, packet.getY, packet.getItemType)
          addToInventory(item)
        }

      case _ =>
        // Unknown action
    }
  }

  private def addToInventory(item: Item): Boolean = {
    val deque = inventory.computeIfAbsent(item.itemType.id, _ => new ConcurrentLinkedDeque[Item]())
    deque.add(item)
    true
  }

  private def handleTileUpdate(packet: TileUpdatePacket): Unit = {
    val world = currentWorld.get()
    val tile = Tile.fromId(packet.getTileId)
    world.setTile(packet.getTileX, packet.getTileY, tile)
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
    player.setCharacterId(packet.getCharacterId)
    players.put(player.getId, player)

    println(s"GameClient: Player joined - ${player.getId.toString.substring(0, 8)} ('${player.getName}') at ${player.getPosition} with health ${player.getHealth}")
  }

  private def handlePlayerUpdate(packet: PlayerUpdatePacket): Unit = {
    val playerId = packet.getPlayerId
    var player = players.get(playerId)

    if (player != null) {
      val wasAlive = !player.isDead
      val oldPos = player.getPosition
      val newPos = packet.getPosition
      val dx = newPos.getX - oldPos.getX
      val dy = newPos.getY - oldPos.getY

      // Detect teleport: large position jump (Manhattan distance > 3)
      if (Math.abs(dx) + Math.abs(dy) > 3) {
        teleportAnimations.put(playerId, Array(
          System.currentTimeMillis(),
          oldPos.getX.toLong, oldPos.getY.toLong,
          newPos.getX.toLong, newPos.getY.toLong,
          packet.getColorRGB.toLong
        ))
      }

      if (dx != 0 || dy != 0) {
        player.setDirection(Direction.fromMovement(dx, dy))
      }
      player.setPosition(newPos)
      player.setColorRGB(packet.getColorRGB)
      player.setHealth(packet.getHealth)
      player.setChargeLevel(packet.getChargeLevel)
      if (packet.getCharacterId != 0 || player.getCharacterId == 0) {
        player.setCharacterId(packet.getCharacterId)
      }

      // Apply effect flags from server
      val flags = packet.getEffectFlags
      if ((flags & 0x01) != 0) {
        player.setShieldUntil(System.currentTimeMillis() + 1000)
      } else {
        player.setShieldUntil(0)
      }
      if ((flags & 0x02) != 0) {
        player.setGemBoostUntil(System.currentTimeMillis() + 1000)
      } else {
        player.setGemBoostUntil(0)
      }
      if ((flags & 0x04) != 0) {
        player.setFrozenUntil(System.currentTimeMillis() + 1000)
      } else {
        player.setFrozenUntil(0)
      }

      // Record death animation when player newly dies
      if (wasAlive && player.isDead) {
        deathAnimations.put(playerId, Array(
          System.currentTimeMillis(),
          newPos.getX.toLong,
          newPos.getY.toLong,
          packet.getColorRGB.toLong
        ))
      }
    } else {
      player = new Player(playerId, "Player", packet.getPosition, packet.getColorRGB, packet.getHealth)
      player.setCharacterId(packet.getCharacterId)
      player.setChargeLevel(packet.getChargeLevel)
      val flags = packet.getEffectFlags
      if ((flags & 0x01) != 0) player.setShieldUntil(System.currentTimeMillis() + 1000)
      if ((flags & 0x02) != 0) player.setGemBoostUntil(System.currentTimeMillis() + 1000)
      if ((flags & 0x04) != 0) player.setFrozenUntil(System.currentTimeMillis() + 1000)
      players.put(playerId, player)
    }
  }

  private def handlePlayerLeave(packet: PlayerLeavePacket): Unit = {
    val playerId = packet.getPlayerId
    val player = players.remove(playerId)
    playerHitTimes.remove(playerId)

    if (player != null) {
      println(s"GameClient: Player left - ${playerId.toString.substring(0, 8)} ('${player.getName}')")
    }
  }

  def useItem(itemTypeId: Byte): Unit = {
    val deque = inventory.get(itemTypeId)
    if (deque == null || deque.isEmpty) {
      return
    }

    val item = deque.poll()
    if (item == null) return

    // Notify server that item was used
    val packet = new ItemPacket(
      sequenceNumber.getAndIncrement(),
      localPlayerId,
      item.getCellX, item.getCellY,
      item.itemType.id,
      item.id,
      ItemAction.USE
    )
    networkThread.send(packet)

    println(s"GameClient: Used item '${item.itemType.name}' (${deque.size()} remaining)")

    val now = System.currentTimeMillis()
    item.itemType match {
      case ItemType.Star =>
        teleportToMouse()
      case ItemType.Gem =>
        fastProjectilesUntil.set(now + Constants.GEM_DURATION_MS)
      case ItemType.Shield =>
        shieldUntil.set(now + Constants.SHIELD_DURATION_MS)
      case ItemType.Heart =>
        // Server heals
      case ItemType.Fence =>
        // Server handles
      case _ =>
    }
  }

  def getLocalPosition: Position = localPosition.get()

  def getLocalPlayerId: UUID = localPlayerId

  def getPlayers: ConcurrentHashMap[UUID, Player] = players

  def getProjectiles: ConcurrentHashMap[Int, Projectile] = projectiles

  def getItems: ConcurrentHashMap[Int, Item] = items

  def getItemCount(itemTypeId: Byte): Int = {
    val deque = inventory.get(itemTypeId)
    if (deque == null) 0 else deque.size()
  }

  def getInventoryCount: Int = {
    import scala.jdk.CollectionConverters._
    inventory.values().asScala.map(_.size()).sum
  }

  def getLocalColorRGB: Int = localColorRGB

  def getLocalDirection: Direction = localDirection.get()

  def getLocalHealth: Int = localHealth.get()

  def setMovementInputActive(active: Boolean): Unit = { movementInputActive = active }

  def getIsMoving: Boolean = movementInputActive || System.currentTimeMillis() - lastMoveTime.get() < 200

  def getIsDead: Boolean = isDead

  def getPlayerHitTime(playerId: UUID): Long = playerHitTimes.getOrDefault(playerId, 0L)

  def getLocalDeathTime: Long = localDeathTime.get()

  def getDeathAnimations: ConcurrentHashMap[UUID, Array[Long]] = deathAnimations

  def getTeleportAnimations: ConcurrentHashMap[UUID, Array[Long]] = teleportAnimations

  def getWorld: WorldData = currentWorld.get()

  def setWorld(world: WorldData): Unit = {
    currentWorld.set(world)
    // Respawn at a valid position in the new world
    val newSpawn = world.getValidSpawnPoint()
    localPosition.set(newSpawn)
    sendPositionUpdate(newSpawn)
    println(s"GameClient: World changed to '${world.name}', respawned at $newSpawn")
  }

  def setMouseWorldPosition(x: Double, y: Double): Unit = {
    mouseWorldX = x
    mouseWorldY = y
  }

  def getMouseWorldX: Double = mouseWorldX
  def getMouseWorldY: Double = mouseWorldY

  private def teleportToMouse(): Unit = {
    val world = currentWorld.get()
    val oldPos = localPosition.get()
    val targetX = Math.round(mouseWorldX).toInt
    val targetY = Math.round(mouseWorldY).toInt

    // Clamp to world bounds
    val clampedX = Math.max(0, Math.min(world.width - 1, targetX))
    val clampedY = Math.max(0, Math.min(world.height - 1, targetY))

    if (!world.isWalkable(clampedX, clampedY)) {
      println("GameClient: Can't teleport to non-walkable tile!")
      return
    }

    val newPos = new Position(clampedX, clampedY)
    localPosition.set(newPos)
    lastMoveTime.set(System.currentTimeMillis())
    sendPositionUpdate(newPos)

    // Record teleport animation
    teleportAnimations.put(localPlayerId, Array(
      System.currentTimeMillis(),
      oldPos.getX.toLong, oldPos.getY.toLong,
      clampedX.toLong, clampedY.toLong,
      localColorRGB.toLong
    ))

    println(s"GameClient: Teleported to ($clampedX, $clampedY)")
  }

  def hasGemBoost: Boolean = System.currentTimeMillis() < fastProjectilesUntil.get()

  def hasShield: Boolean = System.currentTimeMillis() < shieldUntil.get()

  def setWorldFileListener(listener: String => Unit): Unit = {
    worldFileListener = listener
  }

  def setRejoinListener(listener: () => Unit): Unit = {
    rejoinListener = listener
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
