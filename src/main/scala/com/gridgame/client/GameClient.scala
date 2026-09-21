package com.gridgame.client

import com.gridgame.client.render.{FadingProjectile, TerrainImpact}
import com.gridgame.client.audio.AudioManager
import com.gridgame.client.i18n.{I18n, Messages}
import com.gridgame.common.Constants
import com.gridgame.common.model._
import com.gridgame.common.observability.Attrs
import com.gridgame.common.observability.Metrics
import com.gridgame.common.protocol._

import java.util.UUID
import java.util.concurrent.BlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

object ClientState {
  val CONNECTING = 0
  val LOBBY_BROWSER = 1
  val IN_LOBBY = 2
  val PLAYING = 3
  val SCOREBOARD = 4
}

/** Someone in the current lobby, human or bot. */
final case class LobbyMember(id: UUID, name: String)

/** One row of the ranked leaderboard. */
final case class LeaderboardEntry(rank: Int, username: String, elo: Int, wins: Int, matchesPlayed: Int)

/** One of the profile's recent matches. `playedAt` is epoch seconds; `rank` is the team's
  * place in a Teams match; `matchType` is the server's (0 casual FFA, 1 casual Teams,
  * 2 ranked FFA, 3 ranked duel, 4 ranked Teams, 5 practice). */
final case class MatchHistoryEntry(matchId: Int, mapIndex: Int, durationMinutes: Int, playedAt: Long,
                                   kills: Int, deaths: Int, rank: Int, totalPlayers: Int, matchType: Int)

class GameClient(serverHost: String, serverPort: Int, initialWorld: WorldData, var playerName: String = "Player") {
  private var networkThread: NetworkThread = _

  private var localPlayerId: UUID = UUID.randomUUID()
  private var localColorRGB: Int = Player.generateColorFromUUID(localPlayerId)
  private val currentWorld: AtomicReference[WorldData] = new AtomicReference(initialWorld)
  private val localPosition: AtomicReference[Position] = new AtomicReference(
    initialWorld.getValidSpawnPoint()
  )
  private val localDirection: AtomicReference[Direction] = new AtomicReference(Direction.Down)
  private val localHealth: AtomicInteger = new AtomicInteger(100)
  private val players: ConcurrentHashMap[UUID, Player] = new ConcurrentHashMap()
  private val projectiles: ConcurrentHashMap[Int, Projectile] = new ConcurrentHashMap()
  private val items: ConcurrentHashMap[Int, Item] = new ConcurrentHashMap()
  private val inventory: ConcurrentHashMap[Byte, ConcurrentLinkedDeque[Item]] = new ConcurrentHashMap()
  private val incomingPackets: BlockingQueue[Packet] = new LinkedBlockingQueue(Constants.INCOMING_QUEUE_CAPACITY)
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
  private val phasedUntil: AtomicLong = new AtomicLong(0)
  private val burningUntil: AtomicLong = new AtomicLong(0)
  private val speedBoostUntil: AtomicLong = new AtomicLong(0)
  private val rootedUntil: AtomicLong = new AtomicLong(0)
  private val slowedUntil: AtomicLong = new AtomicLong(0)
  // A stun is a freeze the client draws differently, so it comes with the frozen flag set too
  private val stunnedUntil: AtomicLong = new AtomicLong(0)
  private val poisonedUntil: AtomicLong = new AtomicLong(0)
  // How strong the slow we are under is (packet byte [52]). A Slow(_, 0.3f) really is 30% of
  // our pace; every slow used to be a flat half whatever its def said, which is what we assume
  // until the server tells us.
  private val DEFAULT_SLOW_MULTIPLIER = 0.5f
  @volatile private var slowMultiplier: Float = DEFAULT_SLOW_MULTIPLIER

  // Server moves we have taken (Player.getServerMoves): pulls, knockbacks, respawns, freezes,
  // corrections. Sent with every position; the server drops positions sent before a move we
  // hadn't seen yet. Reset each match, since each match counts its own.
  @volatile private var serverMovesSeen: Int = 0

  // Ability cast flash state
  private val lastCastTime: AtomicLong = new AtomicLong(0)
  @volatile private var lastCastDirX: Float = 0f
  @volatile private var lastCastDirY: Float = 0f

  // Swoop state (Raptor Q)
  private val swoopingUntil: AtomicLong = new AtomicLong(0)
  @volatile private var swoopTargetX: Float = 0f
  @volatile private var swoopTargetY: Float = 0f
  @volatile private var swoopStartX: Float = 0f
  @volatile private var swoopStartY: Float = 0f
  private val swoopStartTime: AtomicLong = new AtomicLong(0)

  // Charge shot state
  @volatile var isCharging: Boolean = false
  private val chargingStartTime: AtomicLong = new AtomicLong(0)
  private val lastChargingUpdateTime: AtomicLong = new AtomicLong(0)

  // Hit animation tracking (entries expire after HIT_EXPIRE_MS)
  // Stored as parallel maps so renderer can read each component without allocation per frame.
  private val playerHitTimes: ConcurrentHashMap[UUID, Long] = new ConcurrentHashMap()
  // Projectile colorRGB and screen-space direction at hit moment — used for themed/directional hit FX.
  private val playerHitColors: ConcurrentHashMap[UUID, Integer] = new ConcurrentHashMap()
  private val playerHitDx: ConcurrentHashMap[UUID, java.lang.Float] = new ConcurrentHashMap()
  private val playerHitDy: ConcurrentHashMap[UUID, java.lang.Float] = new ConcurrentHashMap()
  private val playerHitProjType: ConcurrentHashMap[UUID, java.lang.Byte] = new ConcurrentHashMap()
  private val HIT_EXPIRE_MS = 600L
  private var lastHitCleanupTime: Long = 0L

  // Death animation tracking: (timestamp, worldX, worldY, colorRGB)
  private val deathAnimations: ConcurrentHashMap[UUID, Array[Long]] = new ConcurrentHashMap()
  private val localDeathTime: AtomicLong = new AtomicLong(0)

  // Teleport animation tracking: (timestamp, oldX, oldY, newX, newY, colorRGB)
  private val teleportAnimations: ConcurrentHashMap[UUID, Array[Long]] = new ConcurrentHashMap()

  // Explosion animation tracking: projectileId -> (timestamp, worldX*1000, worldY*1000, colorRGB, blastRadius*1000)
  private val explosionAnimations: ConcurrentHashMap[Int, Array[Long]] = new ConcurrentHashMap()

  // Projectiles stopped by terrain or the end of their range: kept for TerrainImpact.FADE_MS
  // where they stopped, so they read as absorbed rather than blinking out. The renderer drops
  // expired entries.
  private val fadingProjectiles: ConcurrentHashMap[Int, FadingProjectile] = new ConcurrentHashMap()

  // Movement interpolation for smooth camera following
  @volatile private var moveInterpFromX: Double = 0.0
  @volatile private var moveInterpFromY: Double = 0.0
  @volatile private var moveInterpToX: Double = 0.0
  @volatile private var moveInterpToY: Double = 0.0
  @volatile private var moveInterpStartTime: Long = 0L
  @volatile private var moveInterpDurationMs: Long = Constants.MOVE_RATE_LIMIT_MS
  @volatile private var prevMoveTimestamp: Long = 0L

  // AoE splash animation tracking: projectileId -> (timestamp, worldX*1000, worldY*1000, colorRGB, aoeRadius*1000)
  private val aoeSplashAnimations: ConcurrentHashMap[Int, Array[Long]] = new ConcurrentHashMap()

  // Track recently removed projectile IDs to prevent UDP MOVE packets from resurrecting them
  private val recentlyRemovedProjectiles: java.util.Set[Int] =
    java.util.Collections.newSetFromMap(new ConcurrentHashMap[Int, java.lang.Boolean]())

  // Where outgoing packets go: the network thread, or a test capturing them
  @volatile private[client] var packetSink: Packet => Unit = _

  private def send(packet: Packet): Unit = {
    val sink = packetSink
    if (sink != null) sink(packet) else networkThread.send(packet)
  }

  @volatile private var running = false
  @volatile private var isDead = false
  private val disconnected = new AtomicBoolean(false)
  private var packetProcessor: Thread = _
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
  @volatile var currentLobbyGameMode: Byte = 0  // 0=FFA, 1=Teams
  @volatile var currentLobbyTeamSize: Int = 2
  @volatile var localTeamId: Byte = 0

  // Server listings (lobbies, leaderboard, match history) arrive as ENTRY... END. Entries
  // collect in a pending buffer (packet-processor thread only) and replace the published
  // list at END. A request the server rate-limits gets no reply, so it leaves the last
  // listing up; clearing the list on request used to leave blank rows or "Loading..." forever.
  @volatile var lobbyList: Vector[LobbyInfo] = Vector.empty
  private val pendingLobbyList = scala.collection.mutable.ArrayBuffer.empty[LobbyInfo]
  // In server order: humans as they joined, bots as they were added (see previewTeams).
  val lobbyMembers: CopyOnWriteArrayList[LobbyMember] = new CopyOnWriteArrayList[LobbyMember]()
  @volatile var lobbyActionFailedListener: Byte => Unit = _

  // Players who left mid-match, kept so the final scoreboard can still name them.
  private val departedPlayers: ConcurrentHashMap[UUID, Player] = new ConcurrentHashMap()

  // Esc arms leaving the match until this time; a second Esc before then leaves.
  @volatile var leaveConfirmUntil: Long = 0L

  // Game stats
  @volatile var killCount: Int = 0
  @volatile var deathCount: Int = 0
  private var gameTimeSyncRemaining: Int = 0
  private var gameTimeSyncTimestamp: Long = 0L
  val scoreboard: CopyOnWriteArrayList[ScoreEntry] = new CopyOnWriteArrayList[ScoreEntry]()
  @volatile var isRespawning: Boolean = false
  @volatile var lastKillerCharacterName: String = ""

  // Practice mode state
  @volatile var isPracticeMode: Boolean = false
  @volatile var practiceCombo: Int = 0
  @volatile var practiceBestCombo: Int = 0
  @volatile var practiceHits: Int = 0
  @volatile var practiceShots: Int = 0
  @volatile var practiceLastHitTime: Long = 0L

  def gameTimeRemaining: Int = {
    if (gameTimeSyncTimestamp == 0L) return 0
    val elapsed = ((System.currentTimeMillis() - gameTimeSyncTimestamp) / 1000).toInt
    Math.max(0, gameTimeSyncRemaining - elapsed)
  }

  // Kill feed: (timestamp, killerName, victimName)
  val killFeed: CopyOnWriteArrayList[Array[AnyRef]] = new CopyOnWriteArrayList[Array[AnyRef]]()

  // Chat state: each entry is Array(timestamp: Long, senderName: String, message: String, scope: Byte)
  val chatMessages: CopyOnWriteArrayList[Array[AnyRef]] = new CopyOnWriteArrayList[Array[AnyRef]]()
  @volatile var chatMessageListener: () => Unit = _
  @volatile var isChatOpen: Boolean = false
  @volatile var chatInputText: String = ""

  @volatile var matchHistory: Vector[MatchHistoryEntry] = Vector.empty
  @volatile var matchHistoryLoaded: Boolean = false
  private val pendingMatchHistory = scala.collection.mutable.ArrayBuffer.empty[MatchHistoryEntry]
  @volatile var totalKillsStat: Int = 0
  @volatile var totalDeathsStat: Int = 0
  @volatile var matchesPlayedStat: Int = 0
  @volatile var winsStat: Int = 0

  // Listeners
  // Ranked queue state
  @volatile var isInRankedQueue: Boolean = false
  @volatile var rankedElo: Int = 1000
  // Set by the server's unsolicited post-ranked-match STATS push. Holds
  // (oldElo, newElo) so the scoreboard can show the change. Cleared on
  // GAME_OVER, so it never leaks into a casual match's post-game screen.
  @volatile var pendingEloChange: Option[(Int, Int)] = None
  @volatile var rankedQueueSize: Int = 0
  @volatile var rankedQueueWaitTime: Int = 0
  @volatile var rankedQueueListener: () => Unit = _
  @volatile var rankedMatchFoundListener: () => Unit = _

  @volatile var leaderboard: Vector[LeaderboardEntry] = Vector.empty
  @volatile var leaderboardLoaded: Boolean = false
  private val pendingLeaderboard = scala.collection.mutable.ArrayBuffer.empty[LeaderboardEntry]
  @volatile var leaderboardListener: () => Unit = _

  @volatile var matchHistoryListener: () => Unit = _
  @volatile var lobbyListListener: () => Unit = _
  @volatile var lobbyJoinedListener: () => Unit = _
  @volatile var lobbyUpdatedListener: () => Unit = _
  @volatile var gameStartingListener: () => Unit = _
  @volatile var gameOverListener: () => Unit = _
  @volatile var lobbyClosedListener: () => Unit = _

  // Disconnect listener (e.g. for UI to show reconnection message)
  @volatile var disconnectListener: () => Unit = _

  def connect(): Unit = {
    try {
      sequenceNumber.set(0)
      disconnected.set(false)
      networkThread = new NetworkThread(this, serverHost, serverPort)
      running = true

      // Wire disconnect callback from network thread
      networkThread.disconnectCallback = new Runnable {
        def run(): Unit = handleDisconnect()
      }

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

  private def handleDisconnect(): Unit = {
    if (!disconnected.compareAndSet(false, true)) return

    Metrics.clientReconnects.add(1L, io.opentelemetry.api.common.Attributes.empty())
    running = false
    clientState = ClientState.CONNECTING
    networkThread.sessionToken = null

    // Clear game state
    players.clear()
    projectiles.clear()
    fadingProjectiles.clear()
    items.clear()
    inventory.clear()
    killFeed.clear()
    chatMessages.clear()
    scoreboard.clear()
    deathAnimations.clear()
    teleportAnimations.clear()
    explosionAnimations.clear()
    aoeSplashAnimations.clear()
    playerHitTimes.clear()
    playerHitColors.clear()
    playerHitDx.clear()
    playerHitDy.clear()
    playerHitProjType.clear()
    recentlyRemovedProjectiles.clear()
    isDead = false
    isRespawning = false
    fastProjectilesUntil.set(0)
    shieldUntil.set(0)
    frozenUntil.set(0)
    burningUntil.set(0)
    speedBoostUntil.set(0)
    rootedUntil.set(0)
    slowedUntil.set(0)
    stunnedUntil.set(0)
    poisonedUntil.set(0)
    slowMultiplier = DEFAULT_SLOW_MULTIPLIER
    phasedUntil.set(0)
    localDeathTime.set(0)
    lastQAbilityTime.set(0)
    lastEAbilityTime.set(0)

    println("GameClient: Disconnected from server")

    val listener = disconnectListener
    if (listener != null) {
      javafx.application.Platform.runLater(new Runnable {
        def run(): Unit = listener()
      })
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
    send(packet)
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
      playerName,
      getSelectedCharacterMaxHealth,
      selectedCharacterId
    )
    send(packet)
  }

  // Tracks the time the most recent heartbeat was sent — set in sendHeartbeat, read when the
  // server echoes it back. Used to derive `gridgame.client.latency`.
  private val heartbeatSentAtNs = new AtomicLong(0L)

  def sendHeartbeat(): Unit = {
    val packet = new HeartbeatPacket(
      sequenceNumber.getAndIncrement(),
      localPlayerId
    )
    heartbeatSentAtNs.set(System.nanoTime())
    send(packet)
  }

  def rejoin(): Unit = {
    if (!isDead) return

    // Reset local state
    isDead = false
    localHealth.set(getSelectedCharacterMaxHealth)

    // Get a new spawn point
    val world = currentWorld.get()
    val newSpawn = world.getValidSpawnPoint()
    localPosition.set(newSpawn)
    localDirection.set(Direction.Down)

    // Clear local projectiles and inventory
    projectiles.clear()
    fadingProjectiles.clear()
    inventory.clear()

    // Reset effect timers
    fastProjectilesUntil.set(0)
    shieldUntil.set(0)
    localDeathTime.set(0)
    lastQAbilityTime.set(0)
    lastEAbilityTime.set(0)
    frozenUntil.set(0)
    phasedUntil.set(0)
    burningUntil.set(0)
    speedBoostUntil.set(0)
    rootedUntil.set(0)
    slowedUntil.set(0)
    stunnedUntil.set(0)
    poisonedUntil.set(0)
    slowMultiplier = DEFAULT_SLOW_MULTIPLIER

    // Send join packet to server
    sendJoinPacket()

    if (rejoinListener != null) rejoinListener()

    println(s"GameClient: Rejoined at $newSpawn")
  }

  def movePlayer(dx: Int, dy: Int): Unit = {
    // Check if frozen — still send position so server echoes frozen state back
    if (isFrozen || isRooted) {
      localDirection.set(Direction.fromMovement(dx, dy))
      sendPositionUpdate(localPosition.get())
      return
    }

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

    if (isPhased || world.isWalkable(targetX, targetY)) {
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
      // Set up movement interpolation for smooth rendering
      val now = System.currentTimeMillis()
      moveInterpFromX = current.getX.toDouble
      moveInterpFromY = current.getY.toDouble
      moveInterpToX = finalX.toDouble
      moveInterpToY = finalY.toDouble
      if (prevMoveTimestamp > 0) {
        moveInterpDurationMs = Math.max(16, now - prevMoveTimestamp)
      }
      prevMoveTimestamp = now
      moveInterpStartTime = now

      localPosition.set(newPos)
      lastMoveTime.set(now)
      sendPositionUpdate(newPos)
    }
  }

  def isPhased: Boolean = System.currentTimeMillis() < phasedUntil.get()

  def isSwooping: Boolean = swoopingUntil.get() > 0

  def getSwoopProgress: Double = {
    val now = System.currentTimeMillis()
    val start = swoopStartTime.get()
    val end = swoopingUntil.get()
    if (now >= end || end <= start) return 1.0
    (now - start).toDouble / (end - start).toDouble
  }

  def getSwoopStartX: Float = swoopStartX
  def getSwoopStartY: Float = swoopStartY
  def getSwoopTargetX: Float = swoopTargetX
  def getSwoopTargetY: Float = swoopTargetY

  def getLastCastTime: Long = lastCastTime.get()
  def getLastCastDirX: Float = lastCastDirX
  def getLastCastDirY: Float = lastCastDirY

  def tickSwoop(): Unit = {
    val now = System.currentTimeMillis()
    val swoopEnd = swoopingUntil.get()
    if (swoopEnd == 0) return

    if (now >= swoopEnd) {
      // Dash has ended — snap to exact target position and send final update
      swoopingUntil.set(0)
      val finalPos = new Position(Math.round(swoopTargetX).toInt, Math.round(swoopTargetY).toInt)
      localPosition.set(finalPos)
      // Send twice for UDP redundancy (dash end position is critical)
      sendPositionUpdate(finalPos)
      sendPositionUpdate(finalPos)
      return
    }

    val elapsed = now - swoopStartTime.get()
    val duration = (swoopEnd - swoopStartTime.get()).toFloat
    val t = Math.min(1.0f, elapsed / duration)

    val newX = swoopStartX + (swoopTargetX - swoopStartX) * t
    val newY = swoopStartY + (swoopTargetY - swoopStartY) * t

    val posX = Math.round(newX).toInt
    val posY = Math.round(newY).toInt
    val newPos = new Position(posX, posY)
    localPosition.set(newPos)
    sendPositionUpdate(newPos)

    // Update direction based on swoop direction
    val dx = (swoopTargetX - swoopStartX).toInt
    val dy = (swoopTargetY - swoopStartY).toInt
    if (dx != 0 || dy != 0) {
      localDirection.set(Direction.fromMovement(
        Math.max(-1, Math.min(1, dx)),
        Math.max(-1, Math.min(1, dy))
      ))
    }
  }

  private def getEffectFlags: Int = {
    (if (hasShield) 0x01 else 0) | (if (hasGemBoost) 0x02 else 0) | (if (isFrozen) 0x04 else 0) | (if (isPhased) 0x08 else 0) | (if (isBurning) 0x10 else 0) | (if (hasSpeedBoost) 0x20 else 0) | (if (isRooted) 0x40 else 0) | (if (isSlowed) 0x80 else 0)
  }

  private def getEffectFlags2: Int = (if (isStunned) 0x01 else 0) | (if (isPoisoned) 0x02 else 0)

  private def getSlowPercent: Int = if (isSlowed) Math.round(slowMultiplier * 100f) else 0

  private def sendPositionUpdate(position: Position): Unit = {
    send(new PlayerUpdatePacket(
      sequenceNumber.getAndIncrement(),
      localPlayerId,
      Packet.getCurrentTimestamp,
      position,
      localColorRGB,
      localHealth.get(),
      getChargeLevel,
      getEffectFlags,
      selectedCharacterId,
      localTeamId,
      serverMovesSeen,
      getEffectFlags2,
      0, // aim angle: nothing aims by it yet
      getSlowPercent
    ))
  }

  def sendChargingUpdate(): Unit = {
    val now = System.currentTimeMillis()
    if (now - lastChargingUpdateTime.get() < 100) return // Rate limit to every 100ms
    lastChargingUpdateTime.set(now)

    sendPositionUpdate(localPosition.get())
  }

  def startCharging(): Unit = {
    if (isFrozen) return
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
    if (isDead || isPhased || isFrozen) return

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
    if (isDead || isPhased || isFrozen) return
    if (isPracticeMode) practiceShots += 1

    val pos = localPosition.get()
    val chargeByte = Math.min(100, Math.max(0, chargeLevel)).toByte

    val primaryType = getSelectedCharacterDef.primaryProjectileType

    if (hasGemBoost) {
      // Shoot 3 projectiles in a narrow cone (center ± 15 degrees)
      val angle = Math.PI / 36.0 // 5 degrees
      val offsets = Seq(0.0, -angle, angle)
      offsets.foreach { theta =>
        val cos = Math.cos(theta).toFloat
        val sin = Math.sin(theta).toFloat
        val rdx = dx * cos - dy * sin
        val rdy = dx * sin + dy * cos
        send(ProjectilePacket.spawnRequest(
          sequenceNumber.getAndIncrement(), localPlayerId,
          pos.getX.toFloat, pos.getY.toFloat, localColorRGB, rdx, rdy,
          chargeByte, primaryType, AttackSlot.PRIMARY))
      }
    } else {
      send(ProjectilePacket.spawnRequest(
        sequenceNumber.getAndIncrement(), localPlayerId,
        pos.getX.toFloat, pos.getY.toFloat, localColorRGB, dx, dy,
        chargeByte, primaryType, AttackSlot.PRIMARY))
    }
  }

  /** Burst shot (Shift+Space): the character's primary projectile along all eight compass points
    * (AttackSlot.BURST), at the cost of standing still for a moment. It used to send four of the
    * default bolt, which the server refused from every character but Spaceman, whose primary it
    * is: pressing it rooted the player and fired nothing. */
  def shootAllDirections(): Unit = {
    if (isDead || isPhased || isFrozen) return

    val pos = localPosition.get()

    // Block movement for 500ms
    movementBlockedUntil.set(System.currentTimeMillis() + Constants.BURST_SHOT_MOVEMENT_BLOCK_MS)

    val primaryType = getSelectedCharacterDef.primaryProjectileType
    AttackSlot.BurstDirections.foreach { case (dx, dy) =>
      send(ProjectilePacket.spawnRequest(
        sequenceNumber.getAndIncrement(), localPlayerId,
        pos.getX.toFloat, pos.getY.toFloat, localColorRGB, dx, dy,
        0.toByte, primaryType, AttackSlot.BURST))
    }
  }

  def shootAbility(slot: Int): Unit = {
    if (isDead || isFrozen) return

    val charDef = getSelectedCharacterDef
    val (abilityDef, lastAbilityTime, attackSlot) = slot match {
      case 0 => (charDef.qAbility, lastQAbilityTime, AttackSlot.Q)
      case 1 => (charDef.eAbility, lastEAbilityTime, AttackSlot.E)
      case _ => return
    }

    // Can't use abilities while phased (except Phase Shift itself is already activated)
    if (isPhased) return

    val now = System.currentTimeMillis()
    if (now - lastAbilityTime.get() < abilityDef.cooldownMs) return

    lastAbilityTime.set(now)

    // Track cast flash direction
    val (castDx, castDy) = getAimDirection
    lastCastDirX = castDx
    lastCastDirY = castDy
    lastCastTime.set(now)

    abilityDef.castBehavior match {
      case DashBuff(maxDistance, durationMs, _) =>
        AudioManager.playDash()
        // Dash toward cursor, phased during dash (e.g. Raptor Swoop)
        val pos = localPosition.get()
        val dx = (mouseWorldX - pos.getX).toFloat
        val dy = (mouseWorldY - pos.getY).toFloat
        val dist = Math.sqrt(dx * dx + dy * dy).toFloat
        val clampedDist = Math.min(dist, maxDistance.toFloat)
        if (clampedDist > 0.5f) {
          val ndx = dx / dist
          val ndy = dy / dist
          swoopStartX = pos.getX.toFloat
          swoopStartY = pos.getY.toFloat
          val world = currentWorld.get()
          var bestX = swoopStartX
          var bestY = swoopStartY
          for (step <- 1 to clampedDist.toInt) {
            val testX = Math.max(0, Math.min(world.width - 1, (swoopStartX + ndx * step).toInt))
            val testY = Math.max(0, Math.min(world.height - 1, (swoopStartY + ndy * step).toInt))
            if (world.isWalkable(testX, testY)) {
              bestX = testX.toFloat
              bestY = testY.toFloat
            }
          }
          swoopTargetX = bestX
          swoopTargetY = bestY
          swoopStartTime.set(now)
          swoopingUntil.set(now + durationMs)
          phasedUntil.set(now + durationMs)
          sendPositionUpdate(localPosition.get())
        }

      case PhaseShiftBuff(durationMs) =>
        AudioManager.playPhaseShift()
        phasedUntil.set(now + durationMs)
        sendPositionUpdate(localPosition.get())

      case TeleportCast(maxDistance) =>
        AudioManager.playTeleport()
        // This ability's own range: a blink on Q used to take E's (Glitcher blinked 16 of its 6)
        performBlink(maxDistance)

      case fan @ FanProjectile(count, _) =>
        val pos = localPosition.get()
        val (ndx, ndy) = getAimDirection
        for (i <- 0 until count) {
          val theta = fan.angleOf(i)
          val cos = Math.cos(theta).toFloat
          val sin = Math.sin(theta).toFloat
          val rdx = ndx * cos - ndy * sin
          val rdy = ndx * sin + ndy * cos
          send(ProjectilePacket.spawnRequest(
            sequenceNumber.getAndIncrement(), localPlayerId,
            pos.getX.toFloat, pos.getY.toFloat, localColorRGB, rdx, rdy,
            0.toByte, abilityDef.projectileType, attackSlot))
        }

      case GroundSlam(_) =>
        val pos = localPosition.get()
        send(ProjectilePacket.spawnRequest(
          sequenceNumber.getAndIncrement(), localPlayerId,
          pos.getX.toFloat, pos.getY.toFloat, localColorRGB, 0.0f, 0.0f,
          0.toByte, abilityDef.projectileType, attackSlot))

      case StandardProjectile =>
        val pos = localPosition.get()
        val (ndx, ndy) = getAimDirection
        send(ProjectilePacket.spawnRequest(
          sequenceNumber.getAndIncrement(), localPlayerId,
          pos.getX.toFloat, pos.getY.toFloat, localColorRGB, ndx, ndy,
          0.toByte, abilityDef.projectileType, attackSlot))
    }
  }

  private def getAimDirection: (Float, Float) = {
    val pos = localPosition.get()
    val dx = (mouseWorldX - pos.getX).toFloat
    val dy = (mouseWorldY - pos.getY).toFloat
    val len = Math.sqrt(dx * dx + dy * dy).toFloat
    if (len > 0.01f) (dx / len, dy / len) else {
      val dir = localDirection.get()
      dir match {
        case Direction.Up    => (0.0f, -1.0f)
        case Direction.Down  => (0.0f, 1.0f)
        case Direction.Left  => (-1.0f, 0.0f)
        case Direction.Right => (1.0f, 0.0f)
      }
    }
  }

  def isFrozen: Boolean = System.currentTimeMillis() < frozenUntil.get()

  def isBurning: Boolean = System.currentTimeMillis() < burningUntil.get()

  def hasSpeedBoost: Boolean = System.currentTimeMillis() < speedBoostUntil.get()

  def isRooted: Boolean = System.currentTimeMillis() < rootedUntil.get()

  def isSlowed: Boolean = System.currentTimeMillis() < slowedUntil.get()

  def isStunned: Boolean = System.currentTimeMillis() < stunnedUntil.get()

  def isPoisoned: Boolean = System.currentTimeMillis() < poisonedUntil.get()

  def getSlowMultiplier: Float = slowMultiplier

  /** How long this player waits between steps in their current state — the rule both input
    * handlers move by, and the server checks against (Movement). */
  def moveStepIntervalMs: Int = Movement.stepIntervalMs(
    getSelectedCharacterDef.moveSpeed, isCharging, getChargeLevel, isPhased, hasSpeedBoost,
    isSlowed, slowMultiplier)

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

  /** Run a screen's listener if one is registered. The listener is read once: the FX thread
    * clears these as screens go away (ClientMain.switchScreen), so a check-then-call on the
    * field could race to a null. */
  private def fire(listener: () => Unit): Unit = if (listener != null) listener()

  def enqueuePacket(packet: Packet): Unit = {
    if (!incomingPackets.offer(packet)) {
      System.err.println(s"GameClient: Packet queue full, dropping ${packet.getType}")
    }
  }

  private def startPacketProcessor(): Unit = {
    val processor = new Thread(new Runnable {
      private val drainBuffer = new java.util.ArrayList[Packet](64)
      def run(): Unit = {
        while (running) {
          try {
            // Block for first packet, then drain all available
            val first = incomingPackets.take()
            processPacket(first)
            drainBuffer.clear()
            incomingPackets.drainTo(drainBuffer)
            var i = 0
            while (i < drainBuffer.size()) {
              processPacket(drainBuffer.get(i))
              i += 1
            }
          } catch {
            case _: InterruptedException =>
              return
            case scala.util.control.NonFatal(e) =>
              // One bad packet or listener must not kill this thread; every screen after it
              // would stop responding to the server.
              System.err.println(s"GameClient: Error processing packet - $e")
              e.printStackTrace()
          }
        }
      }
    }, "PacketProcessor")
    processor.setDaemon(true)
    packetProcessor = processor
    processor.start()
  }

  private[client] def processPacket(packet: Packet): Unit = {
    cleanupHitTimes()
    // Handle session token
    if (packet.getType == PacketType.SESSION_TOKEN) {
      val tokenPacket = packet.asInstanceOf[SessionTokenPacket]
      networkThread.sessionToken = tokenPacket.getSessionToken
      println("GameClient: Session token received")
      return
    }

    // Handle auth response
    if (packet.getType == PacketType.AUTH_RESPONSE) {
      val authPacket = packet.asInstanceOf[AuthResponsePacket]
      if (authResponseListener != null) {
        authResponseListener(authPacket.getSuccess, authPacket.getAssignedUUID, authPacket.getMessage)
      }
      return
    }

    // Handle leaderboard
    if (packet.getType == PacketType.LEADERBOARD) {
      handleLeaderboard(packet.asInstanceOf[LeaderboardPacket])
      return
    }

    // Handle match history
    if (packet.getType == PacketType.MATCH_HISTORY) {
      handleMatchHistory(packet.asInstanceOf[MatchHistoryPacket])
      return
    }

    // Handle chat message
    if (packet.getType == PacketType.CHAT_MESSAGE) {
      handleChatMessage(packet.asInstanceOf[ChatMessagePacket])
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

          // Handle effect flags from server — only set timer on OFF→ON transition
          val flags = updatePacket.getEffectFlags
          val now = System.currentTimeMillis()
          if ((flags & 0x04) != 0) {
            if (now >= frozenUntil.get()) frozenUntil.set(now + 5000)
          } else {
            frozenUntil.set(0)
          }
          if ((flags & 0x10) != 0) {
            if (now >= burningUntil.get()) burningUntil.set(now + 1000)
          } else {
            burningUntil.set(0)
          }
          if ((flags & 0x20) != 0) {
            if (now >= speedBoostUntil.get()) speedBoostUntil.set(now + 1000)
          } else {
            speedBoostUntil.set(0)
          }
          if ((flags & 0x40) != 0) {
            if (now >= rootedUntil.get()) rootedUntil.set(now + 3000)
          } else {
            rootedUntil.set(0)
          }
          if ((flags & 0x80) != 0) {
            if (now >= slowedUntil.get()) slowedUntil.set(now + 3000)
            // How hard: a slow only makes us step at its own rate if we know what that rate is
            if (updatePacket.getSlowPercent > 0) slowMultiplier = updatePacket.getSlowPercent / 100f
          } else {
            slowedUntil.set(0)
            slowMultiplier = DEFAULT_SLOW_MULTIPLIER
          }
          // A stun comes with the frozen flag as well: this only says which of the two to draw
          val flags2 = updatePacket.getEffectFlags2
          if ((flags2 & 0x01) != 0) {
            if (now >= stunnedUntil.get()) stunnedUntil.set(now + 5000)
          } else {
            stunnedUntil.set(0)
          }
          if ((flags2 & 0x02) != 0) {
            if (now >= poisonedUntil.get()) poisonedUntil.set(now + 1000)
          } else {
            poisonedUntil.set(0)
          }
          // Don't overwrite local phased state from server echo — local timer is authoritative
          // Server echoes phased flag to confirm it, but we don't reset the timer

          // The position counts only when it is a server move we haven't taken yet (a pull, a
          // knockback, a freeze, a respawn, a correction). Any other update carries wherever the
          // server last heard we were, a step or two behind a player who is walking, and
          // snapping to that on every regen tick, burn tick or lifesteal rubber-banded us back.
          if (updatePacket.getServerMoves - serverMovesSeen > 0) {
            serverMovesSeen = updatePacket.getServerMoves
            swoopingUntil.set(0) // the server's move ends a dash
            localPosition.set(updatePacket.getPosition)
          }

          if (serverHealth <= 0 && !isDead) {
            isDead = true
            isRespawning = true
            localDeathTime.set(System.currentTimeMillis())
            println("GameClient: You have died! Auto-respawning in 3s...")
          }
        case PacketType.PLAYER_JOIN =>
          // Our own join echo, at the start of a match: our team (it colours our health bar)
          // and where the server placed us, which it picked to keep players apart. We used to
          // keep a spawn setWorld had picked at random and send that instead, which the server
          // took: two players could start on one spawn, and everyone saw each other teleport.
          val join = packet.asInstanceOf[PlayerJoinPacket]
          localTeamId = join.getTeamId
          localPosition.set(join.getPosition)
          localHealth.set(join.getHealth)
          AudioManager.playSpawn()
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

      case PacketType.HEARTBEAT =>
        // Server echoes heartbeats back — derive RTT
        val sentNs = heartbeatSentAtNs.getAndSet(0L)
        if (sentNs > 0) {
          Metrics.clientLatency.record((System.nanoTime() - sentNs) / 1e6, io.opentelemetry.api.common.Attributes.empty())
        }

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
          packet.getMaxPlayers & 0xFF, packet.getLobbyStatus & 0xFF,
          packet.getGameMode & 0xFF, packet.getTeamSize & 0xFF
        )
        pendingLobbyList += info

      case LobbyAction.LIST_END =>
        lobbyList = pendingLobbyList.toVector
        pendingLobbyList.clear()
        fire(lobbyListListener)

      case LobbyAction.JOINED =>
        currentLobbyId = packet.getLobbyId
        currentLobbyName = packet.getLobbyName
        currentLobbyMapIndex = packet.getMapIndex & 0xFF
        currentLobbyDuration = packet.getDurationMinutes & 0xFF
        currentLobbyPlayerCount = packet.getPlayerCount & 0xFF
        currentLobbyMaxPlayers = packet.getMaxPlayers & 0xFF
        currentLobbyGameMode = packet.getGameMode
        currentLobbyTeamSize = packet.getTeamSize & 0xFF
        clientState = ClientState.IN_LOBBY
        lobbyMembers.clear()
        lobbyMembers.add(LobbyMember(localPlayerId, playerName))
        chatMessages.clear() // the last lobby's chat isn't this one's
        if (lobbyJoinedListener != null) lobbyJoinedListener()

      case LobbyAction.MEMBER =>
        // The roster a joiner is sent, in server order and including themselves: re-adding
        // at the end moves us from the front, where JOINED put us, to our real place.
        currentLobbyPlayerCount = packet.getPlayerCount & 0xFF
        val memberId = packet.getPlayerId
        lobbyMembers.removeIf(_.id == memberId)
        lobbyMembers.add(LobbyMember(memberId, packet.getLobbyName))
        fire(lobbyUpdatedListener)

      case LobbyAction.PLAYER_JOINED =>
        currentLobbyPlayerCount = packet.getPlayerCount & 0xFF
        currentLobbyMaxPlayers = packet.getMaxPlayers & 0xFF
        val memberName = packet.getLobbyName
        val memberId = packet.getPlayerId
        lobbyMembers.removeIf(_.id == memberId)
        lobbyMembers.add(LobbyMember(memberId, memberName))
        addLobbySystemMessage(Messages.t("{0} joined the lobby", memberName))
        fire(lobbyUpdatedListener)

      case LobbyAction.PLAYER_LEFT =>
        currentLobbyPlayerCount = packet.getPlayerCount & 0xFF
        currentLobbyMaxPlayers = packet.getMaxPlayers & 0xFF
        val leftId = packet.getPlayerId
        import scala.jdk.CollectionConverters._
        val leftName = Option(packet.getLobbyName).filter(_.nonEmpty)
          .orElse(lobbyMembers.asScala.find(_.id == leftId).map(_.name))
          .getOrElse("?")
        lobbyMembers.removeIf(_.id == leftId)
        addLobbySystemMessage(Messages.t("{0} left the lobby", leftName))
        fire(lobbyUpdatedListener)

      case LobbyAction.CONFIG_UPDATE =>
        currentLobbyMapIndex = packet.getMapIndex & 0xFF
        currentLobbyDuration = packet.getDurationMinutes & 0xFF
        currentLobbyGameMode = packet.getGameMode
        currentLobbyTeamSize = packet.getTeamSize & 0xFF
        // Switching to Teams caps the lobby (and may drop bots), so the counts change too.
        currentLobbyPlayerCount = packet.getPlayerCount & 0xFF
        currentLobbyMaxPlayers = packet.getMaxPlayers & 0xFF
        fire(lobbyUpdatedListener)

      case LobbyAction.ACTION_FAILED =>
        val listener = lobbyActionFailedListener
        if (listener != null) listener(packet.getLobbyStatus)

      case LobbyAction.GAME_STARTING =>
        clientState = ClientState.PLAYING
        leaveConfirmUntil = 0L
        serverMovesSeen = 0
        killCount = 0
        deathCount = 0
        // Reset practice stats but keep isPracticeMode flag
        practiceCombo = 0
        practiceBestCombo = 0
        practiceHits = 0
        practiceShots = 0
        practiceLastHitTime = 0L
        gameTimeSyncRemaining = currentLobbyDuration * 60
        gameTimeSyncTimestamp = System.currentTimeMillis()
        scoreboard.clear()
        killFeed.clear()
        chatMessages.clear()
        isDead = false
        isRespawning = false
        localHealth.set(getSelectedCharacterMaxHealth)
        players.clear()
        departedPlayers.clear()
        projectiles.clear()
        fadingProjectiles.clear()
        items.clear()
        inventory.clear()
        deathAnimations.clear()
        teleportAnimations.clear()
        explosionAnimations.clear()
        aoeSplashAnimations.clear()
        playerHitTimes.clear()
        playerHitColors.clear()
        playerHitDx.clear()
        playerHitDy.clear()
        playerHitProjType.clear()
        recentlyRemovedProjectiles.clear()
        if (gameStartingListener != null) gameStartingListener()

      case LobbyAction.CHARACTER_SELECT =>
        lobbyCharacterSelections.put(packet.getPlayerId, packet.getCharacterId)
        fire(lobbyUpdatedListener)

      case LobbyAction.LOBBY_CLOSED =>
        clientState = ClientState.LOBBY_BROWSER
        currentLobbyId = 0
        if (lobbyClosedListener != null) lobbyClosedListener()

      case _ =>
        println(s"GameClient: Unknown lobby action ${packet.getAction}")
    }
  }

  private def addLobbySystemMessage(text: String): Unit = {
    chatMessages.add(Array(
      System.currentTimeMillis().asInstanceOf[AnyRef],
      "".asInstanceOf[AnyRef],
      text.asInstanceOf[AnyRef],
      ChatScope.LOBBY.asInstanceOf[AnyRef]
    ))
    while (chatMessages.size() > 50) chatMessages.remove(0)
    val listener = chatMessageListener
    if (listener != null) listener()
  }

  /** The lobby roster with the team each member will play on, as `TeamAssignment` deals them
    * when the match starts: humans in join order, then bots in the order they were added. */
  def previewTeams: Seq[(LobbyMember, Byte)] = {
    import scala.jdk.CollectionConverters._
    val members = lobbyMembers.asScala.toVector
    val (bots, humans) = members.partition(m => TeamAssignment.isBot(m.id))
    val byId = members.map(m => m.id -> m).toMap
    TeamAssignment.assign(humans.map(_.id), bots.sortBy(_.id.getLeastSignificantBits).map(_.id))
      .map { case (id, team) => (byId(id), team) }
  }

  /** A player of the current match, including one who has since left it. */
  def findPlayer(playerId: UUID): Player = {
    val p = players.get(playerId)
    if (p != null) p else departedPlayers.get(playerId)
  }

  def playerLeftMatch(playerId: UUID): Boolean =
    !players.containsKey(playerId) && departedPlayers.containsKey(playerId)

  private def handleGameEvent(packet: GameEventPacket): Unit = {
    packet.getEventType match {
      case GameEvent.KILL =>
        val killerId = packet.getPlayerId
        val victimId = packet.getTargetId

        if (killerId.equals(localPlayerId)) {
          killCount = packet.getKills.toInt
          deathCount = packet.getDeaths.toInt

          // Track practice mode stats
          if (isPracticeMode) {
            practiceHits += 1
            practiceCombo += 1
            if (practiceCombo > practiceBestCombo) practiceBestCombo = practiceCombo
            practiceLastHitTime = System.currentTimeMillis()
          }
        }
        if (victimId != null && victimId.equals(localPlayerId) && !killerId.equals(localPlayerId)) {
          deathCount += 1
        }

        // Add to kill feed (using character names)
        val killerName = if (killerId.equals(localPlayerId)) Messages.t("You") else {
          val p = players.get(killerId)
          if (p != null) I18n.characterName(CharacterDef.get(p.getCharacterId)) else killerId.toString.substring(0, 8)
        }
        val victimName = if (victimId != null && victimId.equals(localPlayerId)) Messages.t("You") else {
          if (victimId != null) {
            val p = players.get(victimId)
            if (p != null) I18n.characterName(CharacterDef.get(p.getCharacterId)) else victimId.toString.substring(0, 8)
          } else "?"
        }

        // Track who killed the local player (for death screen)
        if (victimId != null && victimId.equals(localPlayerId)) {
          val killerP = players.get(killerId)
          lastKillerCharacterName = if (killerP != null) I18n.characterName(CharacterDef.get(killerP.getCharacterId)) else "?"
        }
        if (victimId != null) {
          val victimPlayer = players.get(victimId)
          val (distance, pan) =
            if (victimPlayer != null) {
              val vx = victimPlayer.getPosition.getX.toFloat
              val vy = victimPlayer.getPosition.getY.toFloat
              (distanceFromLocal(vx, vy), panFromLocal(vx, vy))
            } else (0f, 0f)
          AudioManager.playDeath(distance, pan)
        }

        val feedText = Messages.t("{0} killed {1}", killerName, victimName)
        killFeed.add(Array(System.currentTimeMillis().asInstanceOf[AnyRef], killerName.asInstanceOf[AnyRef], victimName.asInstanceOf[AnyRef], feedText.asInstanceOf[AnyRef]))
        // Keep only last 5
        while (killFeed.size() > 5) killFeed.remove(0)

      case GameEvent.TIME_SYNC =>
        gameTimeSyncRemaining = packet.getRemainingSeconds
        gameTimeSyncTimestamp = System.currentTimeMillis()

      case GameEvent.GAME_OVER =>
        scoreboard.clear()
        // Reset any leftover ELO delta from a previous match so a casual
        // game's scoreboard doesn't inherit ranked info.
        pendingEloChange = None

      case GameEvent.SCORE_ENTRY =>
        val entry = new ScoreEntry(packet.getPlayerId, packet.getKills.toInt, packet.getDeaths.toInt, packet.getRank.toInt, packet.getTeamId.toInt)
        scoreboard.add(entry)

      case GameEvent.SCORE_END =>
        // Only for the match we are in: one we just left can still end before the server
        // has taken us out of it, and must not pull us out of the lobby browser.
        if (clientState == ClientState.PLAYING) {
          clientState = ClientState.SCOREBOARD
          if (gameOverListener != null) gameOverListener()
        }

      case GameEvent.RESPAWN =>
        if (packet.getPlayerId.equals(localPlayerId)) {
          AudioManager.playSpawn()
          isDead = false
          isRespawning = false
          localHealth.set(getSelectedCharacterMaxHealth)
          val spawnX = packet.getSpawnX.toInt
          val spawnY = packet.getSpawnY.toInt
          localPosition.set(new Position(spawnX, spawnY))
          localDeathTime.set(0)
          fastProjectilesUntil.set(0)
          shieldUntil.set(0)
          frozenUntil.set(0)
          burningUntil.set(0)
          speedBoostUntil.set(0)
          rootedUntil.set(0)
          slowedUntil.set(0)
          stunnedUntil.set(0)
          poisonedUntil.set(0)
          slowMultiplier = DEFAULT_SLOW_MULTIPLIER
          phasedUntil.set(0)
          inventory.clear()
          lastQAbilityTime.set(0)
          lastEAbilityTime.set(0)
          if (rejoinListener != null) rejoinListener()
          println(s"GameClient: Auto-respawned at ($spawnX, $spawnY)")
        }

      case _ =>
        println(s"GameClient: Unknown game event ${packet.getEventType}")
    }
  }

  def getSelectedCharacterDef: CharacterDef = CharacterDef.get(selectedCharacterId)

  def getSelectedCharacterMaxHealth: Int = getSelectedCharacterDef.maxHealth

  def selectCharacter(id: Byte): Unit = {
    selectedCharacterId = id
    if (currentLobbyId != 0) {
      val packet = new LobbyActionPacket(
        sequenceNumber.getAndIncrement(), localPlayerId, Packet.getCurrentTimestamp,
        LobbyAction.CHARACTER_SELECT, currentLobbyId,
        0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, "", id
      )
      send(packet)
    }
  }

  private def handleLeaderboard(packet: LeaderboardPacket): Unit = {
    packet.getAction match {
      case LeaderboardAction.ENTRY =>
        pendingLeaderboard += LeaderboardEntry(
          packet.getRank & 0xFF, packet.getUsername, packet.getElo.toInt,
          packet.getWins, packet.getMatchesPlayed
        )

      case LeaderboardAction.END =>
        leaderboard = pendingLeaderboard.toVector
        pendingLeaderboard.clear()
        leaderboardLoaded = true
        fire(leaderboardListener)

      case _ =>
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
        // Server sets oldElo == elo for profile queries (no change). It only
        // differs when this is a post-match push, which is our signal that
        // the just-ended match was ranked and the scoreboard should render
        // the ELO delta.
        if (packet.getOldElo != packet.getElo) {
          pendingEloChange = Some((packet.getOldElo.toInt, packet.getElo.toInt))
        }

      case MatchHistoryAction.ENTRY =>
        pendingMatchHistory += MatchHistoryEntry(
          packet.getMatchId, packet.getMapIndex & 0xFF, packet.getDuration & 0xFF,
          packet.getPlayedAt & 0xFFFFFFFFL, packet.getKills.toInt, packet.getDeaths.toInt,
          packet.getRank & 0xFF, packet.getTotalPlayers & 0xFF,
          packet.getMatchType & 0xFF
        )

      case MatchHistoryAction.END =>
        matchHistory = pendingMatchHistory.toVector
        pendingMatchHistory.clear()
        matchHistoryLoaded = true
        fire(matchHistoryListener)

      case _ =>
    }
  }

  private def handleRankedQueue(packet: RankedQueuePacket): Unit = {
    packet.getAction match {
      case RankedQueueAction.QUEUE_STATUS =>
        rankedQueueSize = packet.getQueueSize & 0xFF
        rankedElo = packet.getElo.toInt
        rankedQueueWaitTime = packet.getWaitTimeSeconds
        fire(rankedQueueListener)

      case RankedQueueAction.MATCH_FOUND =>
        isInRankedQueue = false
        currentLobbyId = packet.getLobbyId
        currentLobbyName = packet.getLobbyName
        currentLobbyMapIndex = packet.getMapIndex & 0xFF
        currentLobbyDuration = packet.getDurationMinutes & 0xFF
        currentLobbyPlayerCount = packet.getPlayerCount & 0xFF
        currentLobbyMaxPlayers = packet.getMaxPlayers & 0xFF
        // Set both ways: a Teams value left over from an earlier lobby made an FFA or duel
        // scoreboard group everyone under one team.
        if (packet.getMode == RankedQueueMode.TEAMS) {
          currentLobbyGameMode = 1
          currentLobbyTeamSize = Constants.TEAMS_TEAM_SIZE
        } else {
          currentLobbyGameMode = 0
          currentLobbyTeamSize = 2
        }
        clientState = ClientState.IN_LOBBY
        if (rankedMatchFoundListener != null) rankedMatchFoundListener()

      case _ =>
    }
  }

  private def handleChatMessage(packet: ChatMessagePacket): Unit = {
    val senderId = packet.getPlayerId
    val senderName = if (senderId.equals(localPlayerId)) playerName else {
      // Try lobby members first, then players map, then truncated UUID
      import scala.jdk.CollectionConverters._
      val memberName = lobbyMembers.asScala.find(_.id == senderId).map(_.name)
      memberName.getOrElse {
        val p = players.get(senderId)
        if (p != null) p.getName else senderId.toString.substring(0, 8)
      }
    }
    chatMessages.add(Array(
      System.currentTimeMillis().asInstanceOf[AnyRef],
      senderName.asInstanceOf[AnyRef],
      packet.getMessage.asInstanceOf[AnyRef],
      packet.getScope.asInstanceOf[AnyRef]
    ))
    while (chatMessages.size() > 50) chatMessages.remove(0)
    val listener = chatMessageListener
    if (listener != null) listener()
  }

  def sendChatMessage(message: String, scope: Byte): Unit = {
    val msgBytes = message.getBytes(java.nio.charset.StandardCharsets.UTF_8)
    val truncated = if (msgBytes.length > Constants.MAX_CHAT_MESSAGE_LEN) {
      new String(msgBytes, 0, Constants.MAX_CHAT_MESSAGE_LEN, java.nio.charset.StandardCharsets.UTF_8)
    } else message
    val packet = new ChatMessagePacket(
      sequenceNumber.getAndIncrement(), localPlayerId, Packet.getCurrentTimestamp,
      scope, truncated
    )
    send(packet)
  }

  // Lobby actions
  def requestLobbyList(): Unit = {
    val packet = new LobbyActionPacket(
      sequenceNumber.getAndIncrement(), localPlayerId, LobbyAction.LIST_REQUEST
    )
    send(packet)
  }

  // Both carry the character already selected (in practice, ranked or the last lobby), which
  // the lobby room shows as picked: the server enters us as it, not as whatever it last heard.
  def createLobby(name: String, mapIndex: Int, durationMinutes: Int): Unit = {
    isLobbyHost = true
    val packet = new LobbyActionPacket(
      sequenceNumber.getAndIncrement(), localPlayerId, Packet.getCurrentTimestamp, LobbyAction.CREATE, 0.toShort,
      mapIndex.toByte, durationMinutes.toByte, 0.toByte, Constants.MAX_LOBBY_PLAYERS.toByte,
      0.toByte, name, selectedCharacterId
    )
    send(packet)
  }

  def joinLobby(lobbyId: Short): Unit = {
    isLobbyHost = false
    val packet = new LobbyActionPacket(
      sequenceNumber.getAndIncrement(), localPlayerId, Packet.getCurrentTimestamp, LobbyAction.JOIN, lobbyId,
      characterId = selectedCharacterId
    )
    send(packet)
  }

  def leaveLobby(): Unit = {
    val packet = new LobbyActionPacket(
      sequenceNumber.getAndIncrement(), localPlayerId, LobbyAction.LEAVE
    )
    send(packet)
    clientState = ClientState.LOBBY_BROWSER
    currentLobbyId = 0
    isLobbyHost = false
    currentLobbyGameMode = 0
    currentLobbyTeamSize = 2
    lobbyMembers.clear()
  }

  /** Leave the match in progress. Practice ends on the server and its results screen still
    * follows; any other match carries on without us, so we are straight back in the browser. */
  def leaveMatch(): Unit = {
    leaveConfirmUntil = 0L
    val packet = new LobbyActionPacket(
      sequenceNumber.getAndIncrement(), localPlayerId, LobbyAction.LEAVE
    )
    send(packet)
    if (!isPracticeMode) returnToLobbyBrowser()
  }

  def startGame(): Unit = {
    val packet = new LobbyActionPacket(
      sequenceNumber.getAndIncrement(), localPlayerId, LobbyAction.START
    )
    send(packet)
  }

  def requestLeaderboard(): Unit = {
    val packet = new LeaderboardPacket(
      sequenceNumber.getAndIncrement(), localPlayerId, LeaderboardAction.QUERY
    )
    send(packet)
  }

  def requestMatchHistory(): Unit = {
    val packet = new MatchHistoryPacket(
      sequenceNumber.getAndIncrement(), localPlayerId, MatchHistoryAction.QUERY
    )
    send(packet)
  }

  def addBot(): Unit = {
    val packet = new LobbyActionPacket(
      sequenceNumber.getAndIncrement(), localPlayerId, LobbyAction.ADD_BOT
    )
    send(packet)
  }

  def removeBot(): Unit = {
    val packet = new LobbyActionPacket(
      sequenceNumber.getAndIncrement(), localPlayerId, LobbyAction.REMOVE_BOT
    )
    send(packet)
  }

  def startPractice(): Unit = {
    isPracticeMode = true
    val packet = new LobbyActionPacket(
      sequenceNumber.getAndIncrement(), localPlayerId, Packet.getCurrentTimestamp,
      LobbyAction.PRACTICE_START, 0.toShort,
      0.toByte, 0.toByte, 0.toByte, 0.toByte, 0.toByte, "", selectedCharacterId
    )
    send(packet)
  }

  def updateLobbyConfig(mapIndex: Int, durationMinutes: Int, gameMode: Byte = -1, teamSize: Int = -1): Unit = {
    val gm = if (gameMode >= 0) gameMode else currentLobbyGameMode
    val ts = if (teamSize > 0) teamSize else currentLobbyTeamSize
    val packet = new LobbyActionPacket(
      sequenceNumber.getAndIncrement(), localPlayerId, Packet.getCurrentTimestamp,
      LobbyAction.CONFIG_UPDATE, currentLobbyId,
      mapIndex.toByte, durationMinutes.toByte, 0.toByte, 0.toByte, 0.toByte, "",
      0.toByte, gm, ts.toByte
    )
    send(packet)
  }

  def returnToLobbyBrowser(): Unit = {
    clientState = ClientState.LOBBY_BROWSER
    currentLobbyId = 0
    isLobbyHost = false
    isPracticeMode = false
    leaveConfirmUntil = 0L
    departedPlayers.clear()
    killCount = 0
    deathCount = 0
    gameTimeSyncRemaining = 0
    gameTimeSyncTimestamp = 0L
    scoreboard.clear()
    killFeed.clear()
    chatMessages.clear()
    players.clear()
    projectiles.clear()
    fadingProjectiles.clear()
    items.clear()
    isDead = false
    isRespawning = false
    localTeamId = 0
    currentLobbyGameMode = 0
    currentLobbyTeamSize = 2
    lobbyMembers.clear()
    deathAnimations.clear()
    teleportAnimations.clear()
    explosionAnimations.clear()
    aoeSplashAnimations.clear()
    playerHitTimes.clear()
    playerHitColors.clear()
    playerHitDx.clear()
    playerHitDy.clear()
    playerHitProjType.clear()
    recentlyRemovedProjectiles.clear()
  }

  // Ranked queue actions
  @volatile var rankedMode: Byte = RankedQueueMode.FFA

  def queueRanked(mode: Byte = RankedQueueMode.FFA): Unit = {
    isInRankedQueue = true
    rankedMode = mode
    val packet = new RankedQueuePacket(
      sequenceNumber.getAndIncrement(), localPlayerId,
      RankedQueueAction.QUEUE_JOIN, selectedCharacterId, mode
    )
    send(packet)
  }

  def leaveRankedQueue(): Unit = {
    isInRankedQueue = false
    val packet = new RankedQueuePacket(
      sequenceNumber.getAndIncrement(), localPlayerId, RankedQueueAction.QUEUE_LEAVE
    )
    send(packet)
  }

  def changeRankedCharacter(id: Byte): Unit = {
    selectedCharacterId = id
    if (isInRankedQueue) {
      val packet = new RankedQueuePacket(
        sequenceNumber.getAndIncrement(), localPlayerId,
        RankedQueueAction.CHARACTER_CHANGE, id
      )
      send(packet)
    }
  }

  /** Distance in grid cells from the local player — drives sound attenuation. */
  private def distanceFromLocal(x: Float, y: Float): Float = {
    val pos = localPosition.get()
    val dx = x - pos.getX
    val dy = y - pos.getY
    Math.sqrt((dx * dx + dy * dy).toDouble).toFloat
  }

  /** Stereo pan (-1 left .. +1 right) for a world position. Screen X in an
    * isometric projection is (wx - wy), so that — not world X — is the axis
    * that maps to left/right for the listener. */
  private def panFromLocal(x: Float, y: Float): Float = {
    val pos = localPosition.get()
    val screenDx = (x - pos.getX) - (y - pos.getY)
    Math.max(-1f, Math.min(1f, screenDx / Constants.AUDIO_PAN_RANGE_CELLS))
  }

  /** The character a shooter is playing, or -1 if we have not seen them yet.
    * `players` does not hold the local player, so that case is answered from
    * the local selection. */
  private def characterIdOf(shooterId: UUID): Byte = {
    if (shooterId == null) return -1
    if (shooterId.equals(localPlayerId)) return selectedCharacterId
    val p = players.get(shooterId)
    if (p != null) p.getCharacterId else -1
  }

  private def handleProjectileUpdate(packet: ProjectilePacket): Unit = {
    val projectileId = packet.getProjectileId

    packet.getAction match {
      case ProjectileAction.SPAWN =>
        recentlyRemovedProjectiles.remove(projectileId)
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
        AudioManager.playAttack(packet.getProjectileType, characterIdOf(packet.getPlayerId),
          distanceFromLocal(packet.getX, packet.getY), panFromLocal(packet.getX, packet.getY))
        // Periodic cleanup: evict oldest entries to prevent unbounded growth
        // (incremental eviction avoids clearing all entries which could resurrect projectiles)
        if (recentlyRemovedProjectiles.size() > 500) {
          val iter = recentlyRemovedProjectiles.iterator()
          var removed = 0
          while (iter.hasNext && removed < 250) {
            iter.next()
            iter.remove()
            removed += 1
          }
        }

      case ProjectileAction.MOVE =>
        val projectile = projectiles.get(projectileId)
        if (projectile == null) {
          // Only create if we haven't seen this projectile despawn/hit already
          // (UDP MOVE packets can arrive after TCP HIT/DESPAWN)
          if (!recentlyRemovedProjectiles.contains(projectileId)) {
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
        } else {
          // Update in-place to avoid allocation
          projectile.updatePosition(packet.getX, packet.getY, packet.getDx, packet.getDy)
        }

      case ProjectileAction.HIT | ProjectileAction.PIERCE =>
        // A pierce passed through the target and keeps flying, so the projectile stays (its
        // MOVEs keep coming). Dropped here, it vanished at its first hit.
        if (packet.getAction == ProjectileAction.HIT) {
          projectiles.remove(projectileId)
          recentlyRemovedProjectiles.add(projectileId)
        }
        val hitPType = packet.getProjectileType
        val targetId = packet.getTargetId
        if (targetId != null) {
          playerHitTimes.put(targetId, System.currentTimeMillis())
          playerHitColors.put(targetId, Integer.valueOf(packet.getColorRGB))
          playerHitDx.put(targetId, java.lang.Float.valueOf(packet.getDx))
          playerHitDy.put(targetId, java.lang.Float.valueOf(packet.getDy))
          playerHitProjType.put(targetId, java.lang.Byte.valueOf(hitPType))

          // If our projectile hit another player, reduce ability cooldown by 50%
          if (packet.getPlayerId.equals(localPlayerId)) {
            reduceAbilityCooldownOnHit(packet.getProjectileType)
          }

          if (targetId.equals(localPlayerId)) AudioManager.playHitTaken()
          else if (packet.getPlayerId.equals(localPlayerId)) AudioManager.playHitDealt()
          else AudioManager.playHitOther(
            distanceFromLocal(packet.getX, packet.getY), panFromLocal(packet.getX, packet.getY))
        }

        // AoE splash visual on hit
        val hitPDef = ProjectileDef.get(hitPType)
        hitPDef.aoeOnHit.foreach { aoe =>
          aoeSplashAnimations.put(projectileId, Array(
            System.currentTimeMillis(),
            (packet.getX * 1000).toLong,
            (packet.getY * 1000).toLong,
            packet.getColorRGB.toLong,
            (aoe.radius * 1000).toLong
          ))
        }

      case ProjectileAction.DESPAWN =>
        val despawned = projectiles.remove(projectileId)
        recentlyRemovedProjectiles.add(projectileId)
        val pType = if (despawned != null) despawned.projectileType else packet.getProjectileType
        val pDef = ProjectileDef.get(pType)
        val colorRGB = if (despawned != null) despawned.colorRGB else packet.getColorRGB
        // Where it actually met the terrain: the server removes it up to half a cell inside
        // the wall, so walk back to the face before showing anything there.
        val impact = TerrainImpact.resolve(getWorld, packet.getX, packet.getY,
          packet.getDx, packet.getDy, pDef.passesThroughWalls)
        if (pDef.isExplosive) {
          AudioManager.playExplosion(
            distanceFromLocal(impact.x, impact.y), panFromLocal(impact.x, impact.y))
          val blastRadius = pDef.explosionConfig.map(_.blastRadius).getOrElse(3f)
          explosionAnimations.put(projectileId, Array(
            System.currentTimeMillis(),
            (impact.x * 1000).toLong,
            (impact.y * 1000).toLong,
            colorRGB.toLong,
            (blastRadius * 1000).toLong
          ))
        } else if (despawned != null) {
          // Stop it where it struck and let it sink into the surface there
          despawned.updatePosition(impact.x, impact.y, despawned.dx, despawned.dy)
          fadingProjectiles.put(projectileId,
            new FadingProjectile(despawned, System.currentTimeMillis(), impact.hitTerrain, impact.tileColor))
        }
        // AoE splash visual on max range
        pDef.aoeOnMaxRange.foreach { aoe =>
          aoeSplashAnimations.put(projectileId + 1000000, Array(
            System.currentTimeMillis(),
            (packet.getX * 1000).toLong,
            (packet.getY * 1000).toLong,
            colorRGB.toLong,
            (aoe.radius * 1000).toLong
          ))
        }

      case _ =>
        // Unknown action
    }
  }

  private def reduceAbilityCooldownOnHit(projectileType: Byte): Unit = {
    val charDef = getSelectedCharacterDef
    val now = System.currentTimeMillis()

    val (abilityDef, lastAbilityTime) =
      if (charDef.qAbility.projectileType == projectileType) (charDef.qAbility, lastQAbilityTime)
      else if (charDef.eAbility.projectileType == projectileType) (charDef.eAbility, lastEAbilityTime)
      else return

    val remaining = lastAbilityTime.get() + abilityDef.cooldownMs - now
    if (remaining > 0) {
      lastAbilityTime.addAndGet(-remaining / 2)
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

      case ItemAction.USE_REJECTED =>
        if (packet.getPlayerId.equals(localPlayerId)) {
          addToInventory(new Item(packet.getItemId, packet.getX, packet.getY, packet.getItemType))
          if (packet.getItemType == ItemType.Star) {
            // The server didn't take the teleport: back to where it has us
            localPosition.set(new Position(packet.getX, packet.getY))
            println(s"GameClient: Teleport refused by server, back at (${packet.getX}, ${packet.getY})")
          }
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
    player.setTeamId(packet.getTeamId)
    players.put(player.getId, player)

    println(s"GameClient: Player joined - ${player.getId.toString.substring(0, 8)} ('${player.getName}') at ${player.getPosition} with health ${player.getHealth} team=${packet.getTeamId}")
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

      // Detect teleport: large position jump (Manhattan distance > 3).
      // Skip if the player was dead — a respawn also resets position to an
      // unrelated point on the map and would otherwise be misread as a cast.
      if (wasAlive && Math.abs(dx) + Math.abs(dy) > 3) {
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
      if (packet.getTeamId != 0) {
        player.setTeamId(packet.getTeamId)
      }

      // Apply effect flags from server — only set timer on OFF→ON transition
      val flags = packet.getEffectFlags
      val now = System.currentTimeMillis()
      if ((flags & 0x01) != 0) {
        if (now >= player.getShieldUntil) player.setShieldUntil(now + 1000)
      } else {
        player.setShieldUntil(0)
      }
      if ((flags & 0x02) != 0) {
        if (now >= player.getGemBoostUntil) player.setGemBoostUntil(now + 1000)
      } else {
        player.setGemBoostUntil(0)
      }
      if ((flags & 0x04) != 0) {
        if (now >= player.getFrozenUntil) player.setFrozenUntil(now + 5000)
      } else {
        player.setFrozenUntil(0)
      }
      if ((flags & 0x08) != 0) {
        if (now >= player.getPhasedUntil) player.setPhasedUntil(now + 1000)
      } else {
        player.setPhasedUntil(0)
      }
      if ((flags & 0x10) != 0) {
        if (!player.isBurning) player.applyBurn(0, 1000, 1000, null) // Visual-only on client; server handles actual DoT
      } else {
        player.clearBurn()
      }
      if ((flags & 0x20) != 0) {
        if (now >= player.getSpeedBoostUntil) player.setSpeedBoostUntil(now + 1000)
      } else {
        player.setSpeedBoostUntil(0)
      }
      if ((flags & 0x40) != 0) {
        if (now >= player.getRootedUntil) player.setRootedUntil(now + 3000)
      } else {
        player.setRootedUntil(0)
      }
      if ((flags & 0x80) != 0) {
        if (now >= player.getSlowedUntil) player.setSlowedUntil(now + 3000)
        if (packet.getSlowPercent > 0) player.setSlowMultiplier(packet.getSlowPercent / 100f)
      } else {
        player.clearSlow()
      }
      val flags2 = packet.getEffectFlags2
      if ((flags2 & 0x01) != 0) {
        if (now >= player.getStunnedUntil) player.setStunnedUntil(now + 5000)
      } else {
        player.setStunnedUntil(0)
      }
      if ((flags2 & 0x02) != 0) {
        if (!player.isPoisoned) player.applyPoison(0, 1000, 1000, null) // visual only: the server does the damage
      } else {
        player.clearPoison()
      }

      // Record death animation when player newly dies
      if (wasAlive && player.isDead) {
        deathAnimations.put(playerId, Array(
          System.currentTimeMillis(),
          newPos.getX.toLong,
          newPos.getY.toLong,
          packet.getColorRGB.toLong,
          player.getCharacterId.toLong
        ))
      }
    } else {
      player = new Player(playerId, "Player", packet.getPosition, packet.getColorRGB, packet.getHealth)
      player.setCharacterId(packet.getCharacterId)
      player.setChargeLevel(packet.getChargeLevel)
      val flags = packet.getEffectFlags
      if ((flags & 0x01) != 0) player.setShieldUntil(System.currentTimeMillis() + 1000)
      if ((flags & 0x02) != 0) player.setGemBoostUntil(System.currentTimeMillis() + 1000)
      if ((flags & 0x04) != 0) player.setFrozenUntil(System.currentTimeMillis() + 5000)
      if ((flags & 0x08) != 0) player.setPhasedUntil(System.currentTimeMillis() + 1000)
      if ((flags & 0x10) != 0) player.applyBurn(0, 1000, 1000, null)
      if ((flags & 0x20) != 0) player.setSpeedBoostUntil(System.currentTimeMillis() + 1000)
      if ((flags & 0x40) != 0) player.setRootedUntil(System.currentTimeMillis() + 3000)
      if ((flags & 0x80) != 0) player.setSlowedUntil(System.currentTimeMillis() + 3000)
      if (packet.getSlowPercent > 0) player.setSlowMultiplier(packet.getSlowPercent / 100f)
      val flags2 = packet.getEffectFlags2
      if ((flags2 & 0x01) != 0) player.setStunnedUntil(System.currentTimeMillis() + 5000)
      if ((flags2 & 0x02) != 0) player.applyPoison(0, 1000, 1000, null)
      players.put(playerId, player)
    }
  }

  private def handlePlayerLeave(packet: PlayerLeavePacket): Unit = {
    val playerId = packet.getPlayerId
    val player = players.remove(playerId)
    playerHitTimes.remove(playerId)
    playerHitColors.remove(playerId)
    playerHitDx.remove(playerId)
    playerHitDy.remove(playerId)
    playerHitProjType.remove(playerId)

    if (player != null) {
      departedPlayers.put(playerId, player)
      println(s"GameClient: Player left - ${playerId.toString.substring(0, 8)} ('${player.getName}')")
    }
  }

  def useItem(itemTypeId: Byte): Unit = {
    val deque = inventory.get(itemTypeId)
    if (deque == null || deque.isEmpty) {
      return
    }

    // A star lands on the cell the server will check it against (Teleport), so it can't show a
    // teleport the server then undoes. Nowhere to go keeps the star.
    var starTarget: Position = null
    if (itemTypeId == ItemType.Star.id) {
      val pos = localPosition.get()
      starTarget = Teleport.starTarget(currentWorld.get(), pos.getX, pos.getY, mouseWorldX, mouseWorldY).orNull
      if (starTarget == null) {
        println("GameClient: Nowhere to teleport to!")
        return
      }
    }

    val item = deque.poll()
    if (item == null) return

    // Notify server that item was used
    // For fence and star, send the target cell as coordinates
    val (packetX, packetY) = if (item.itemType == ItemType.Star) {
      (starTarget.getX, starTarget.getY)
    } else if (item.itemType == ItemType.Fence) {
      (Math.round(mouseWorldX).toInt, Math.round(mouseWorldY).toInt)
    } else {
      (item.getCellX, item.getCellY)
    }
    val packet = new ItemPacket(
      sequenceNumber.getAndIncrement(),
      localPlayerId,
      packetX, packetY,
      item.itemType.id,
      item.id,
      ItemAction.USE
    )
    send(packet)

    println(s"GameClient: Used item '${item.itemType.name}' (${deque.size()} remaining)")

    val now = System.currentTimeMillis()
    item.itemType match {
      case ItemType.Star =>
        teleportTo(starTarget)
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

  /** Smoothly interpolated position for rendering. Results stored in visualPosX/visualPosY to avoid tuple allocation. */
  private var _visualPosX: Double = 0.0
  private var _visualPosY: Double = 0.0
  def visualPosX: Double = _visualPosX
  def visualPosY: Double = _visualPosY

  /** Call once per frame to update visualPosX/visualPosY. */
  def updateVisualPosition(): Unit = {
    val pos = localPosition.get()
    if (moveInterpStartTime == 0L) {
      _visualPosX = pos.getX.toDouble
      _visualPosY = pos.getY.toDouble
      return
    }
    val now = System.currentTimeMillis()
    val elapsed = now - moveInterpStartTime
    if (elapsed >= moveInterpDurationMs) {
      _visualPosX = pos.getX.toDouble
      _visualPosY = pos.getY.toDouble
    } else {
      val t = elapsed.toDouble / moveInterpDurationMs
      _visualPosX = moveInterpFromX + (moveInterpToX - moveInterpFromX) * t
      _visualPosY = moveInterpFromY + (moveInterpToY - moveInterpFromY) * t
    }
  }

  def getLocalPlayerId: UUID = localPlayerId

  def getPlayers: ConcurrentHashMap[UUID, Player] = players

  def getProjectiles: ConcurrentHashMap[Int, Projectile] = projectiles

  def getItems: ConcurrentHashMap[Int, Item] = items

  def getItemCount(itemTypeId: Byte): Int = {
    val deque = inventory.get(itemTypeId)
    if (deque == null) 0 else deque.size()
  }

  def getInventoryCount: Int = {
    var sum = 0
    val iter = inventory.values().iterator()
    while (iter.hasNext) sum += iter.next().size()
    sum
  }

  def getLocalColorRGB: Int = localColorRGB

  def getLocalDirection: Direction = localDirection.get()

  def getLocalHealth: Int = localHealth.get()

  def setMovementInputActive(active: Boolean): Unit = { movementInputActive = active }

  def getIsMoving: Boolean = movementInputActive || System.currentTimeMillis() - lastMoveTime.get() < 200

  def getIsDead: Boolean = isDead

  def getPlayerHitTime(playerId: UUID): Long =
    playerHitTimes.getOrDefault(playerId, 0L)

  /** Color of the projectile that last hit this player (0 if none). */
  def getPlayerHitColorRGB(playerId: UUID): Int = {
    val v = playerHitColors.get(playerId)
    if (v == null) 0 else v.intValue()
  }

  /** Direction (world space) the projectile that last hit this player was traveling. */
  def getPlayerHitDx(playerId: UUID): Float = {
    val v = playerHitDx.get(playerId)
    if (v == null) 0f else v.floatValue()
  }
  def getPlayerHitDy(playerId: UUID): Float = {
    val v = playerHitDy.get(playerId)
    if (v == null) 0f else v.floatValue()
  }
  def getPlayerHitProjectileType(playerId: UUID): Byte = {
    val v = playerHitProjType.get(playerId)
    if (v == null) 0.toByte else v.byteValue()
  }

  /** Periodic cleanup of expired hit entries to prevent unbounded growth. Call from packet processing, not render. */
  private def cleanupHitTimes(): Unit = {
    val now = System.currentTimeMillis()
    if (now - lastHitCleanupTime > 2000) {
      lastHitCleanupTime = now
      val iter = playerHitTimes.entrySet().iterator()
      while (iter.hasNext) {
        val entry = iter.next()
        if (now - entry.getValue > HIT_EXPIRE_MS) {
          val k = entry.getKey
          iter.remove()
          playerHitColors.remove(k)
          playerHitDx.remove(k)
          playerHitDy.remove(k)
          playerHitProjType.remove(k)
        }
      }
    }
  }

  def getLocalDeathTime: Long = localDeathTime.get()

  def getDeathAnimations: ConcurrentHashMap[UUID, Array[Long]] = deathAnimations

  def getTeleportAnimations: ConcurrentHashMap[UUID, Array[Long]] = teleportAnimations

  def getExplosionAnimations: ConcurrentHashMap[Int, Array[Long]] = explosionAnimations
  def getAoeSplashAnimations: ConcurrentHashMap[Int, Array[Long]] = aoeSplashAnimations
  def getFadingProjectiles: ConcurrentHashMap[Int, FadingProjectile] = fadingProjectiles

  def getWorld: WorldData = currentWorld.get()

  def setWorld(world: WorldData): Unit = {
    currentWorld.set(world)
    // Somewhere in the new world until the server's join echo, which follows the world on the
    // same connection, says where it placed us. Kept to ourselves: sent, the server took it as
    // our position (see the PLAYER_JOIN case in processPacket).
    val tempSpawn = world.getValidSpawnPoint()
    localPosition.set(tempSpawn)
    println(s"GameClient: World changed to '${world.name}', temp spawn at $tempSpawn")
  }

  def setMouseWorldPosition(x: Double, y: Double): Unit = {
    mouseWorldX = x
    mouseWorldY = y
  }

  def getMouseWorldX: Double = mouseWorldX
  def getMouseWorldY: Double = mouseWorldY

  /** Star: show the teleport now. The item packet already sent is what moves us on the server,
    * which tells everyone else, so there's no position update to send. */
  private def teleportTo(target: Position): Unit = {
    val oldPos = localPosition.get()
    localPosition.set(target)
    val now = System.currentTimeMillis()
    lastMoveTime.set(now)

    // Record teleport animation
    teleportAnimations.put(localPlayerId, Array(
      now,
      oldPos.getX.toLong, oldPos.getY.toLong,
      target.getX.toLong, target.getY.toLong,
      localColorRGB.toLong
    ))

    println(s"GameClient: Teleported to (${target.getX}, ${target.getY})")
  }

  private def performBlink(maxDistance: Int): Unit = {
    val oldPos = localPosition.get()
    val dx = (mouseWorldX - oldPos.getX).toFloat
    val dy = (mouseWorldY - oldPos.getY).toFloat
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

    // Walk cell-by-cell up to the blink's range, stopping before the first non-walkable cell
    val dest = Teleport.blinkTarget(currentWorld.get(), oldPos.getX, oldPos.getY, ndx, ndy, maxDistance)
    blinkTo(oldPos, dest.getX, dest.getY)
  }

  private def blinkTo(oldPos: Position, x: Int, y: Int): Unit = {
    if (x == oldPos.getX && y == oldPos.getY) return

    val newPos = new Position(x, y)
    localPosition.set(newPos)
    val now = System.currentTimeMillis()
    lastMoveTime.set(now)
    // Send twice for UDP redundancy (blink is a single critical event)
    sendPositionUpdate(newPos)
    sendPositionUpdate(newPos)

    // Record teleport animation
    teleportAnimations.put(localPlayerId, Array(
      now,
      oldPos.getX.toLong, oldPos.getY.toLong,
      x.toLong, y.toLong,
      localColorRGB.toLong
    ))

    println(s"GameClient: Blinked to ($x, $y)")
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
    if (!running && disconnected.get()) {
      return
    }

    running = false
    disconnected.set(true)

    // Interrupt packet processor thread so it doesn't block on take()
    if (packetProcessor != null) packetProcessor.interrupt()

    // Null if connect() was never reached
    if (networkThread == null) return

    val leavePacket = new PlayerLeavePacket(
      sequenceNumber.getAndIncrement(),
      localPlayerId
    )
    networkThread.send(leavePacket)
    networkThread.sessionToken = null

    networkThread.shutdown()

    println("GameClient: Disconnected")
  }
}
