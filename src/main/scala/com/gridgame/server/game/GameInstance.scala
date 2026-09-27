package com.gridgame.server.game

import com.gridgame.common.Constants
import com.gridgame.common.model._
import com.gridgame.common.observability.Attrs
import com.gridgame.common.observability.Metrics
import com.gridgame.common.protocol._
import com.gridgame.common.world.WorldLoader
import com.gridgame.server.net.Outbox

import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * One running match: its players (registry), projectiles, items and traps, its world, its teams, its
 * clock and its ticks. What happens in it is spread over the traits it is made of, a file each:
 *
 *  - Opening            its first thirty seconds: the Teams wall, the free-for-all ceasefire
 *  - Broadcasts         telling everyone in the match
 *  - PlayerSync         what everyone is told about a player, and the server moving one
 *  - ProjectileEffects  what projectiles do: moves, hits, blasts and their effects
 *  - TrapEffects        traps going off
 *  - Vitals             burns, poisons and regeneration
 *  - Lives              deaths scored, and respawns
 *
 * What its players send it is ClientHandler's to judge. `start` schedules its ticks; a test calls
 * `begin` instead and runs them itself.
 */
class GameInstance(val gameId: Short, val worldFile: String, val durationMinutes: Int, host: MatchHost)
    extends Opening with Broadcasts with PlayerSync with ProjectileEffects with TrapEffects with Vitals with Lives {
  /** Where the match's packets go out. */
  def outbox: Outbox = host.outbox

  val registry = new ClientRegistry()
  val teamAssignments = new java.util.concurrent.ConcurrentHashMap[UUID, Byte]()
  var gameMode: Byte = 0 // 0=FFA, 1=Teams
  val projectileManager = new ProjectileManager(registry, isTeammate, () => divider)
  val itemManager = new ItemManager()
  val trapManager = new TrapManager(registry, isTeammate)
  val killTracker = new KillTracker()
  val handler = new ClientHandler(this)
  var world: WorldData = _
  val modifiedTiles = new java.util.concurrent.ConcurrentHashMap[(Int, Int), Int]()

  def isTeammate(id1: UUID, id2: UUID): Boolean = {
    if (gameMode == 0) return false
    val team1: java.lang.Byte = teamAssignments.get(id1)
    val team2: java.lang.Byte = teamAssignments.get(id2)
    team1 != null && team2 != null && team1.byteValue() != 0 && team1.byteValue() == team2.byteValue()
  }

  @volatile var isPractice: Boolean = false
  /** The bots playing in this match, if any: they stop with it. */
  var botController: MatchBots = _
  private var projectileExecutor: ScheduledExecutorService = _
  private var itemSpawnExecutor: ScheduledExecutorService = _
  private var timerSyncExecutor: ScheduledExecutorService = _
  private var playerTickExecutor: ScheduledExecutorService = _
  private[game] var respawnExecutor: ScheduledExecutorService = _
  /**
   * The match clock, on System.nanoTime: the clock the executor that ends the match counts on, so
   * the end comes round exactly when the time is up. The wall clock is no good for it. The system
   * sets it by a few tens of milliseconds, back as often as forward, every 25 minutes or so (timed,
   * on macOS), and the executor never sees it.
   */
  private var startNanos: Long = 0L
  private var endsAtNanos: Long = 0L
  /** How long the match lasts: its minutes, unless a test wants a shorter one. */
  private[server] var durationMs: Long = durationMinutes * 60000L
  @volatile private[game] var running = false
  private[game] val spawnLock = new Object()

  def loadWorld(): Unit = {
    if (worldFile.nonEmpty) {
      try {
        world = WorldLoader.load(worldFile)
        println(s"GameInstance[$gameId]: Loaded world '${world.name}' (${world.width}x${world.height})")
      } catch {
        case e: Exception =>
          println(s"GameInstance[$gameId]: Failed to load world: ${e.getMessage}")
          world = WorldData.createEmpty(200, 200)
      }
    } else {
      world = WorldData.createEmpty(200, 200)
    }
  }

  /** Put the match in play: world loaded, clock running. start() also schedules the ticks that
    * drive it; tests call this alone and run the ticks themselves. */
  private[server] def begin(): Unit = {
    if (world == null) loadWorld()
    startNanos = System.nanoTime()
    endsAtNanos = startNanos + durationMs * 1000000L
    beginOpening()
    running = true
  }

  def start(): Unit = {
    begin()

    // Helper: wrap a Runnable in try-catch so ScheduledExecutorService doesn't silently die
    def safeRunnable(name: String)(body: => Unit): Runnable = new Runnable {
      def run(): Unit = try { body } catch {
        case e: Exception =>
          System.err.println(s"GameInstance[$gameId] $name error: ${e.getMessage}")
      }
    }

    // Start projectile tick
    projectileExecutor = Executors.newSingleThreadScheduledExecutor()
    projectileExecutor.scheduleAtFixedRate(
      safeRunnable("tickProjectiles")(tickProjectiles()),
      Constants.PROJECTILE_SPEED_MS.toLong,
      Constants.PROJECTILE_SPEED_MS.toLong,
      TimeUnit.MILLISECONDS
    )

    // Spawn items relative to map size (1 item per 500 tiles, min 3, max 20)
    val mapArea = world.width * world.height
    val itemCount = Math.max(3, Math.min(20, mapArea / 2000))
    itemSpawnExecutor = Executors.newSingleThreadScheduledExecutor()
    spawnItemBatch(itemCount)
    itemSpawnExecutor.scheduleAtFixedRate(
      safeRunnable("spawnItem")(spawnItemBatch(itemCount)),
      Constants.ITEM_SPAWN_INTERVAL_MS.toLong,
      Constants.ITEM_SPAWN_INTERVAL_MS.toLong,
      TimeUnit.MILLISECONDS
    )

    // Start player state tick (burn DoT processing)
    playerTickExecutor = Executors.newSingleThreadScheduledExecutor()
    playerTickExecutor.scheduleAtFixedRate(
      safeRunnable("tickPlayers")(tickPlayers()),
      200L, 200L, TimeUnit.MILLISECONDS
    )

    // Shared executor for respawn scheduling
    respawnExecutor = Executors.newSingleThreadScheduledExecutor()

    // Start timer sync
    timerSyncExecutor = Executors.newSingleThreadScheduledExecutor()
    timerSyncExecutor.scheduleAtFixedRate(
      safeRunnable("syncTimer")(syncTimer()),
      Constants.TIME_SYNC_INTERVAL_S.toLong,
      Constants.TIME_SYNC_INTERVAL_S.toLong,
      TimeUnit.SECONDS
    )
    timerSyncExecutor.schedule(
      safeRunnable("endMatch")(endMatch()),
      endsAtNanos - System.nanoTime(),
      TimeUnit.NANOSECONDS
    )

    Metrics.matchesStarted.add(1L, io.opentelemetry.api.common.Attributes.builder()
      .putAll(Attrs.modeOf(gameMode))
      .putAll(if (isPractice) Attrs.MatchPractice else Attrs.MatchCasual)
      .build())
    println(s"GameInstance[$gameId]: Started ($durationMinutes min)")
  }

  def stop(): Unit = {
    running = false
    if (botController != null) botController.stop()
    if (projectileExecutor != null) { projectileExecutor.shutdown(); projectileExecutor.shutdownNow() }
    if (playerTickExecutor != null) { playerTickExecutor.shutdown(); playerTickExecutor.shutdownNow() }
    if (itemSpawnExecutor != null) { itemSpawnExecutor.shutdown(); itemSpawnExecutor.shutdownNow() }
    if (respawnExecutor != null) { respawnExecutor.shutdown(); respawnExecutor.shutdownNow() }
    if (timerSyncExecutor != null) { timerSyncExecutor.shutdown(); timerSyncExecutor.shutdownNow() }
    // Release manager state and unregister their async gauges. Without this, the
    // gauge callbacks remain registered against OTel's meter and keep reporting the
    // post-mortem sizes of projectiles/items, and across matches the callbacks
    // accumulate (memory leak + metric corruption).
    projectileManager.close()
    itemManager.close()
    trapManager.close()
    modifiedTiles.clear()
    teamAssignments.clear()
    println(s"GameInstance[$gameId]: Stopped")
  }

  /** Rounded up: a countdown shows the second that is running out, and reads 0 once the time is up. */
  def getRemainingSeconds: Int = {
    val left = endsAtNanos - System.nanoTime()
    if (left <= 0L) 0 else ((left + 999999999L) / 1000000000L).toInt
  }

  def getElapsedSeconds: Int = ((System.nanoTime() - startNanos) / 1000000000L).toInt

  def isTimeUp: Boolean = System.nanoTime() - endsAtNanos >= 0L

  def isRunning: Boolean = running

  private val projectileTickAttrs = Attrs.tickPhase("projectile")
  private val timerTickAttrs = Attrs.tickPhase("timer")

  /**
   * The projectile tick running now, or last run: 1 for the first. Every projectile packet carries
   * it (ProjectilePacket.getTick), so a client can put each position on the server's own timeline
   * and fly the projectile between them (NetProjectile). A MOVE is sent at the end of its tick; a
   * SPAWN, whenever the shot was fired, carries the tick before the one that first moves it.
   * Written by the projectile tick only.
   */
  @volatile private[game] var projectileTick = 0

  private[server] def getProjectileTick: Int = projectileTick

  private[server] def tickProjectiles(): Unit = {
    if (!running || world == null) return
    val tickStart = System.nanoTime()
    projectileTick += 1

    syncOpening()
    projectileManager.tick(world).foreach(applyProjectileEvent)
    // Traps on the ground: the ones that have run out, and the ones somebody is standing on.
    // A player who walked onto one between ticks has already sprung it (ClientHandler); this
    // catches everyone standing on one, bots and players the server itself moved included.
    applyTrapEvents(trapManager.tick(System.currentTimeMillis()))
    // Flush all buffered writes at end of tick
    flushAllInstancePlayers()
    Metrics.tickDuration.record((System.nanoTime() - tickStart) / 1e6, projectileTickAttrs)
  }

  private def spawnItemBatch(count: Int): Unit = {
    if (!running || world == null) return
    val zeroUUID = new UUID(0L, 0L)
    var spawned = false
    var i = 0
    while (i < count) {
      itemManager.spawnRandomItem(world).foreach { event =>
        val packet = new ItemPacket(
          outbox.nextSeq(),
          zeroUUID,
          event.item.x, event.item.y,
          event.item.itemType.id,
          event.item.id,
          ItemAction.SPAWN
        )
        broadcastBuffered(packet)
        spawned = true
        Metrics.itemsSpawned.add(1L, Attrs.itemTypeAttrs(event.item.itemType.id))
      }
      i += 1
    }
    if (spawned) flushAllInstancePlayers()
  }

  /**
   * The end of the match, scheduled for the moment its time is up. The syncs below used to end it,
   * the first to find the time up, and the one due at the deadline comes round only a few
   * milliseconds after it: with the wall clock set back further than that during the match, it
   * found a second left, and the match ran on for another ten with every countdown at 0:00.
   */
  private def endMatch(): Unit = if (running) host.endGame(gameId)

  private def syncTimer(): Unit = {
    if (!running || isTimeUp) return
    val tickStart = System.nanoTime()

    val zeroUUID = new UUID(0L, 0L)
    val packet = new GameEventPacket(
      outbox.nextSeq(),
      zeroUUID,
      GameEvent.TIME_SYNC,
      gameId,
      getRemainingSeconds,
      0.toShort, 0.toShort, null, 0.toByte, 0.toShort, 0.toShort
    )
    broadcastToInstance(packet)
    Metrics.tickDuration.record((System.nanoTime() - tickStart) / 1e6, timerTickAttrs)
  }

}
