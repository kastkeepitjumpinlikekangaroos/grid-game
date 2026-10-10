package com.gridgame.server.bots

import com.gridgame.common.Constants
import com.gridgame.common.model._
import com.gridgame.common.observability.Attrs
import com.gridgame.common.observability.Metrics
import com.gridgame.common.protocol._
import com.gridgame.server.game.{GameInstance, MatchBots}

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import scala.jdk.CollectionConverters._

/**
 * The bots of one match, on a tick of their own (every 100ms): each picks a target, steps, uses
 * items and fights as the character it is playing, through the same match a player's client talks
 * to. Its parts, a file each: BotTargeting, BotNavigation (and BotPaths, the searches it steps by),
 * BotItems, BotCombat. Practice bots only wander.
 */
class BotController(private[bots] val instance: GameInstance, isPractice: Boolean = false)
    extends MatchBots with BotTargeting with BotNavigation with BotPaths with BotItems with BotCombat {
  private val botIds = new java.util.concurrent.CopyOnWriteArrayList[UUID]()
  private[bots] val lastShotTime = new ConcurrentHashMap[UUID, Long]()
  private[bots] val lastQAbilityTime = new ConcurrentHashMap[UUID, Long]()
  private[bots] val lastEAbilityTime = new ConcurrentHashMap[UUID, Long]()
  private val lastMoveTime = new ConcurrentHashMap[UUID, Long]()
  private[bots] val botShootCooldown = new ConcurrentHashMap[UUID, Long]()
  private[bots] val currentTarget = new ConcurrentHashMap[UUID, UUID]()
  private[bots] val targetSwitchTime = new ConcurrentHashMap[UUID, Long]()
  private[bots] val strafeDirection = new ConcurrentHashMap[UUID, Int]() // 1 = clockwise, -1 = counter
  // Bots whose raised barrier the clients have been told about, so its end can be announced too
  private[bots] val barrierShown = ConcurrentHashMap.newKeySet[UUID]()
  // How far each bot's target was on the tick before, so a trap is laid for someone coming in
  private[bots] val lastTargetDist = new ConcurrentHashMap[UUID, java.lang.Float]()
  // The way each bot is following round whatever is between it and its target (BotNavigation)
  private[bots] val routes = new ConcurrentHashMap[UUID, BotRoute]()
  private var executor: ScheduledExecutorService = _

  private val TICK_INTERVAL_MS = 100L
  // Bots take a step every other one a player of the same character would (Movement), so their
  // 100ms at speed 1.0 is the player's 50ms doubled, and a melee bot's 106ms its 53ms.
  private val BOT_MOVE_INTERVAL_MS = 100L
  private[bots] val SHOOT_COOLDOWN_MIN_MS = 700L
  private[bots] val SHOOT_COOLDOWN_MAX_MS = 1100L
  private[bots] val TARGET_HYSTERESIS_MS = 2000L // stick to a target for at least 2s
  private[bots] val AIM_INACCURACY_RAD = 0.12f // ~7 degrees max aim wobble
  // A trap is laid for a target no further off than this, and only while they are closing in
  private[bots] val TRAP_TARGET_CELLS = 8f
  // A barrier goes up against a shot coming at the bot from this close
  private[bots] val INCOMING_SHOT_CELLS = 6f
  // ...that would pass within this of it
  private[bots] val INCOMING_SHOT_MISS_CELLS = 2f

  // 8 cardinal + diagonal directions for obstacle avoidance
  private[bots] val ALL_DIRS = Array(
    (1, 0), (-1, 0), (0, 1), (0, -1),
    (1, 1), (1, -1), (-1, 1), (-1, -1)
  )

  def addBotId(id: UUID): Unit = {
    botIds.add(id)
    lastShotTime.put(id, 0L)
    lastQAbilityTime.put(id, 0L)
    lastEAbilityTime.put(id, 0L)
    // When its last step fell due, staggered so bots don't all step on the same ticks
    lastMoveTime.put(id, System.currentTimeMillis() - scala.util.Random.nextInt(BOT_MOVE_INTERVAL_MS.toInt))
    lastTargetDist.put(id, java.lang.Float.valueOf(Float.MaxValue))
    botShootCooldown.put(id, SHOOT_COOLDOWN_MIN_MS + scala.util.Random.nextLong(SHOOT_COOLDOWN_MAX_MS - SHOOT_COOLDOWN_MIN_MS))
    strafeDirection.put(id, if (scala.util.Random.nextBoolean()) 1 else -1)
    routes.put(id, new BotRoute)
  }

  // Async gauge for active bots
  private val botsActiveGauge = com.gridgame.common.observability.Telemetry.meter("com.gridgame.bots")
    .gaugeBuilder("gridgame.bots.active")
    .setDescription("Bots active in this instance")
    .setUnit("{bot}")
    .ofLongs()
    .buildWithCallback { obs =>
      obs.record(botIds.size().toLong, io.opentelemetry.api.common.Attributes.empty())
    }

  private val botTickAttrs = Attrs.tickPhase("bot")

  def start(): Unit = {
    executor = Executors.newSingleThreadScheduledExecutor()
    executor.scheduleAtFixedRate(
      new Runnable { def run(): Unit = tick() },
      TICK_INTERVAL_MS,
      TICK_INTERVAL_MS,
      TimeUnit.MILLISECONDS
    )
    println(s"BotController: Started with ${botIds.size()} bots")
  }

  override def stop(): Unit = {
    if (executor != null) {
      executor.shutdown()
      executor.shutdownNow()
      // A tick under way finishes first: it reads the tables and the scratch arrays let go below
      try executor.awaitTermination(1, TimeUnit.SECONDS)
      catch { case _: InterruptedException => Thread.currentThread().interrupt() }
    }
    // Unregister the gauge callback and drop per-bot tables; otherwise the gauge keeps
    // reporting the final bot count after the match ends, and across matches the
    // callbacks accumulate. The lobby keeps its last match until the next, so the search
    // arrays (BotPaths) go too.
    try botsActiveGauge.close() catch { case _: Throwable => () }
    botIds.clear()
    lastShotTime.clear()
    lastQAbilityTime.clear()
    lastEAbilityTime.clear()
    lastMoveTime.clear()
    botShootCooldown.clear()
    currentTarget.clear()
    targetSwitchTime.clear()
    strafeDirection.clear()
    barrierShown.clear()
    lastTargetDist.clear()
    routes.clear()
    releaseGrid()
    println("BotController: Stopped")
  }

  /** One pass over every bot. Runs every TICK_INTERVAL_MS once started; tests call it themselves. */
  private[server] def tick(): Unit = tick(System.currentTimeMillis())

  /** One pass on the caller's clock, which is what the bots step by: a test can walk them at
    * their pace a tick at a time without waiting for it. */
  private[server] def tick(now: Long): Unit = {
    val tickStart = System.nanoTime()
    try {
      if (!instance.isRunning || instance.world == null) return

      // Where everyone stands, once a tick (each bot's step keeps it up to date after)
      gridFor(instance.world)
      markStanding()

      tickNow = now
      botIds.asScala.foreach { botId =>
        val bot = instance.registry.get(botId)
        if (bot != null && !bot.isDead) {
          bot.updateHeartbeat()
          if (!bot.isFrozen) {
            tickBot(bot, now)
          }
        }
      }
    } catch {
      // An Error too: one escaping the periodic task cancels it, and the bots stood still
      case e: Throwable =>
        System.err.println(s"BotController: Error in tick: $e")
    } finally {
      Metrics.tickDuration.record((System.nanoTime() - tickStart) / 1e6, botTickAttrs)
    }
  }

  private def tickBot(bot: Player, now: Long): Unit = {
    if (isPractice) {
      // Passive practice bots: wander randomly ~15% of ticks, no shooting/abilities
      if (scala.util.Random.nextFloat() < 0.15f) {
        moveRandom(bot)
      }
      return
    }

    val target = pickTarget(bot, now)

    tryPickupItems(bot)
    tryUseItems(bot, target)

    // A barrier faces the target every tick it is up, and its end is announced when it runs out
    if (bot.hasBarrier) {
      if (target != null) faceBarrier(bot, target)
    } else if (barrierShown.remove(bot.getId)) {
      broadcastBotPosition(bot)
    }

    val posBefore = bot.getPosition
    if (!bot.isRooted) {
      // Twice a player's interval for the same character and state. Bots never charge; a phase
      // doubles their pace as it does a player's, which is what makes a melee bot's phase a way in.
      val moveInterval = 2L * Movement.stepIntervalMs(
        CharacterDef.get(bot.getCharacterId).moveSpeed,
        charging = false, chargeLevel = 0, phased = bot.isPhased,
        speedBoost = bot.hasSpeedBoost, slowed = bot.isSlowed, slowMultiplier = bot.getSlowMultiplier)
      // When the bot's last step fell due. Counted from there rather than from this tick: counted
      // from the tick, as it was, a 104ms or 106ms step waited for the second tick after the last
      // and walked at half its pace. Up to two a tick, for the steps a speed boost brings under
      // the tick's 100ms
      var due = lastMoveTime.getOrDefault(bot.getId, 0L)
      var steps = 0
      while (steps < 2 && now - due >= moveInterval) {
        stepBot(bot, target)
        due = Movement.nextStepFrom(due + moveInterval, now, TICK_INTERVAL_MS)
        steps += 1
      }
      lastMoveTime.put(bot.getId, due)
    }
    // A step tells the clients where the barrier faces. Standing still it has to be said anyway:
    // so they see it turn, and because their copy of it lapses when updates stop coming
    if (bot.hasBarrier && (bot.getPosition eq posBefore)) broadcastBotPosition(bot)

    // A bot holds its fire through a free-for-all's opening ceasefire as a player does
    // (MatchOpening): it walks and it looks for a target, and it shoots at nothing.
    if (target != null && !instance.attacksLocked) {
      tryUseAbilities(bot, target)
      tryShoot(bot, target, now)
    }
  }

  /** One step: after the target, or a wander with nobody to chase. */
  private def stepBot(bot: Player, target: Player): Unit = {
    // The cell the target stands on: a trap under them is no reason to break off the chase
    if (target != null) {
      val tp = target.getPosition
      chaseX = tp.getX; chaseY = tp.getY
    } else {
      chaseX = Int.MinValue; chaseY = Int.MinValue
    }
    // Behind a barrier, a bot closes in whatever its range: that is what the barrier is for
    if (target != null && bot.hasBarrier) {
      moveToward(bot, target)
    } else if (target != null) {
      moveSmart(bot, target)
    } else {
      wander(bot)
    }
  }

  // --- Target selection with hysteresis ---

  // --- Range-based movement ---

  // --- Item pickup and usage ---

  // --- Targeting ---

  // --- Collision checks ---

  // The cell the bot currently being moved is chasing. A trap under the target is not a reason to
  // stand off — the whole point of one laid there is that the target is standing on it.
  private[bots] var chaseX = Int.MinValue
  private[bots] var chaseY = Int.MinValue
  // This tick's clock, so the hundreds of cells a BFS looks at don't each read it
  private[bots] var tickNow = 0L

  // --- Barriers ---

  // --- Abilities ---

  // --- Shooting ---

  // --- Broadcasting ---

  private[bots] def broadcastBotPosition(bot: Player): Unit = {
    instance.broadcastToInstance(instance.stateUpdate(bot))
  }
}
