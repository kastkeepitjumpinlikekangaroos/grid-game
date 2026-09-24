package com.gridgame.server

import com.gridgame.common.Constants
import com.gridgame.common.model._
import com.gridgame.common.observability.Attrs
import com.gridgame.common.observability.Metrics
import com.gridgame.common.protocol._

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import scala.jdk.CollectionConverters._

class BotController(instance: GameInstance, isPractice: Boolean = false) {
  private val botIds = new java.util.concurrent.CopyOnWriteArrayList[UUID]()
  private val lastShotTime = new ConcurrentHashMap[UUID, Long]()
  private val lastQAbilityTime = new ConcurrentHashMap[UUID, Long]()
  private val lastEAbilityTime = new ConcurrentHashMap[UUID, Long]()
  private val lastMoveTime = new ConcurrentHashMap[UUID, Long]()
  private val botShootCooldown = new ConcurrentHashMap[UUID, Long]()
  private val currentTarget = new ConcurrentHashMap[UUID, UUID]()
  private val targetSwitchTime = new ConcurrentHashMap[UUID, Long]()
  private val strafeDirection = new ConcurrentHashMap[UUID, Int]() // 1 = clockwise, -1 = counter
  // Bots whose raised barrier the clients have been told about, so its end can be announced too
  private val barrierShown = ConcurrentHashMap.newKeySet[UUID]()
  // How far each bot's target was on the tick before, so a trap is laid for someone coming in
  private val lastTargetDist = new ConcurrentHashMap[UUID, java.lang.Float]()
  private var executor: ScheduledExecutorService = _

  private val TICK_INTERVAL_MS = 100L
  // Bots take a step every other one a player of the same character would (Movement), so their
  // 100ms at speed 1.0 is the player's 50ms doubled, and a melee bot's 106ms its 53ms.
  private val BOT_MOVE_INTERVAL_MS = 100L
  private val SHOOT_COOLDOWN_MIN_MS = 700L
  private val SHOOT_COOLDOWN_MAX_MS = 1100L
  private val TARGET_HYSTERESIS_MS = 2000L // stick to a target for at least 2s
  private val AIM_INACCURACY_RAD = 0.12f // ~7 degrees max aim wobble
  // A trap is laid for a target no further off than this, and only while they are closing in
  private val TRAP_TARGET_CELLS = 8f
  // A barrier goes up against a shot coming at the bot from this close
  private val INCOMING_SHOT_CELLS = 6f
  // ...that would pass within this of it
  private val INCOMING_SHOT_MISS_CELLS = 2f

  // 8 cardinal + diagonal directions for obstacle avoidance
  private val ALL_DIRS = Array(
    (1, 0), (-1, 0), (0, 1), (0, -1),
    (1, 1), (1, -1), (-1, 1), (-1, -1)
  )

  // Occupancy set rebuilt once per tick — O(1) tile occupancy checks instead of O(n)
  private val occupiedTiles = new java.util.HashSet[Long]()
  // Maps packed coords to player UUID for excludeId handling
  private val occupiedTileOwners = new java.util.HashMap[Long, UUID]()

  // Pre-allocated BFS structures reused between calls (bot executor is single-threaded)
  private val bfsVisited = new java.util.HashSet[Long]()
  private val bfsQueue = new java.util.ArrayDeque[(Int, Int, Int, Int)]()

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

  def stop(): Unit = {
    if (executor != null) {
      executor.shutdown()
      executor.shutdownNow()
    }
    // Unregister the gauge callback and drop per-bot tables; otherwise the gauge keeps
    // reporting the final bot count after the match ends, and across matches the
    // callbacks accumulate.
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
    occupiedTiles.clear()
    occupiedTileOwners.clear()
    bfsVisited.clear()
    bfsQueue.clear()
    println("BotController: Stopped")
  }

  private def packCoord(x: Int, y: Int): Long = (x.toLong << 32) | (y.toLong & 0xFFFFFFFFL)

  /** One pass over every bot. Runs every TICK_INTERVAL_MS once started; tests call it themselves. */
  private[server] def tick(): Unit = tick(System.currentTimeMillis())

  /** One pass on the caller's clock, which is what the bots step by: a test can walk them at
    * their pace a tick at a time without waiting for it. */
  private[server] def tick(now: Long): Unit = {
    val tickStart = System.nanoTime()
    try {
      if (!instance.isRunning || instance.world == null) return

      // Rebuild occupancy set once per tick
      occupiedTiles.clear()
      occupiedTileOwners.clear()
      instance.registry.forEachPlayer { p =>
        if (!p.isDead) {
          val key = packCoord(p.getPosition.getX, p.getPosition.getY)
          occupiedTiles.add(key)
          occupiedTileOwners.put(key, p.getId)
        }
      }

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
      case e: Exception =>
        System.err.println(s"BotController: Error in tick: ${e.getMessage}")
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

  private def pickTarget(bot: Player, now: Long): Player = {
    val lastSwitch = targetSwitchTime.getOrDefault(bot.getId, 0L)
    val currentId = currentTarget.get(bot.getId)

    // Check if current target is still valid
    val currentValid = if (currentId != null) {
      val p = instance.registry.get(currentId)
      p != null && !p.isDead && !instance.isTeammate(bot.getId, p.getId)
    } else false

    // If current target is valid and we haven't exceeded hysteresis, keep it
    if (currentValid && now - lastSwitch < TARGET_HYSTERESIS_MS) {
      return instance.registry.get(currentId)
    }

    // Find nearest player
    val nearest = findNearestPlayer(bot)
    if (nearest != null) {
      // Only switch if the new target is significantly closer (>30% closer) or current is invalid
      if (currentValid) {
        val currentPlayer = instance.registry.get(currentId)
        val currentDist = distanceBetween(bot.getPosition, currentPlayer.getPosition)
        val nearestDist = distanceBetween(bot.getPosition, nearest.getPosition)
        if (nearestDist < currentDist * 0.7f || !currentId.equals(nearest.getId)) {
          if (nearestDist < currentDist * 0.7f) {
            currentTarget.put(bot.getId, nearest.getId)
            targetSwitchTime.put(bot.getId, now)
          }
          // else keep current target until hysteresis expires, then switch
          if (now - lastSwitch >= TARGET_HYSTERESIS_MS) {
            currentTarget.put(bot.getId, nearest.getId)
            targetSwitchTime.put(bot.getId, now)
          }
          return if (now - lastSwitch >= TARGET_HYSTERESIS_MS || nearestDist < currentDist * 0.7f)
            nearest else currentPlayer
        }
        return currentPlayer
      } else {
        currentTarget.put(bot.getId, nearest.getId)
        targetSwitchTime.put(bot.getId, now)
      }
    }
    nearest
  }

  // --- Range-based movement ---

  private def getMaxRange(charId: Byte): Int = {
    val charDef = CharacterDef.get(charId)
    ProjectileDef.get(charDef.primaryProjectileType).maxRange
  }

  private def roleOf(charId: Byte): CombatRole = CharacterDef.get(charId).role

  /** Keeps its distance, backing off from anyone who closes in. Only the ranged do: melee has to
    * close in, and a skirmisher's bruiser health is there so it can stand its ground. */
  private def isRanged(charId: Byte): Boolean = roleOf(charId) == CombatRole.Ranged

  private def getPreferredRange(charId: Byte): Float = roleOf(charId) match {
    case CombatRole.Ranged => getMaxRange(charId) * 0.6f
    // Halfway into its throw, where a boulder is hard to sidestep
    case CombatRole.Skirmisher => getMaxRange(charId) * 0.5f
    case CombatRole.Melee => 1.5f
  }

  private def moveSmart(bot: Player, target: Player): Unit = {
    val botPos = bot.getPosition
    val targetPos = target.getPosition
    val dist = distanceBetween(botPos, targetPos)
    val preferredRange = getPreferredRange(bot.getCharacterId)
    val role = roleOf(bot.getCharacterId)
    val ranged = isRanged(bot.getCharacterId)

    if (dist < 0.01f) {
      moveRandom(bot)
    } else if (ranged && dist < preferredRange * 0.5f) {
      moveAway(bot, target)
    } else if (ranged && dist < preferredRange * 0.8f) {
      if (scala.util.Random.nextFloat() < 0.4f) strafe(bot, target)
      else moveAway(bot, target)
    } else if (dist > preferredRange * 1.3f) {
      moveToward(bot, target)
    } else if (role == CombatRole.Melee) {
      moveToward(bot, target)
    } else {
      // In range: the ranged and skirmishers circle there
      strafe(bot, target)
    }
  }

  private def wander(bot: Player): Unit = {
    if (scala.util.Random.nextFloat() < 0.3f) {
      moveRandom(bot)
    }
  }

  private def moveRandom(bot: Player): Unit = {
    val botPos = bot.getPosition
    // Start from random index and iterate circularly (avoids shuffle allocation)
    val start = scala.util.Random.nextInt(ALL_DIRS.length)
    var i = 0
    while (i < ALL_DIRS.length) {
      val (ddx, ddy) = ALL_DIRS((start + i) % ALL_DIRS.length)
      if (canMoveTo(botPos.getX + ddx, botPos.getY + ddy, bot.getId)) {
        bot.setPosition(new Position(botPos.getX + ddx, botPos.getY + ddy))
        bot.setDirection(Direction.fromMovement(ddx, ddy))
        broadcastBotPosition(bot)
        return
      }
      i += 1
    }
  }

  // --- BFS pathfinding (bounded to avoid expensive searches) ---

  private val BFS_MAX_CELLS = 600 // max cells to explore (~25 cell radius)
  private val BFS_DIRS = Array((1, 0), (-1, 0), (0, 1), (0, -1))

  /** BFS from (fromX,fromY) toward (toX,toY). Returns the first step, or None if unreachable. */
  private def bfsNextStep(fromX: Int, fromY: Int, toX: Int, toY: Int, botId: UUID): Option[(Int, Int)] = {
    if (fromX == toX && fromY == toY) return None

    val world = instance.world
    // Reuse pre-allocated structures (bot executor is single-threaded)
    val visited = bfsVisited; visited.clear()
    val queue = bfsQueue; queue.clear()

    val startKey = packCoord(fromX, fromY)
    visited.add(startKey)

    // Seed with walkable neighbors
    for ((ddx, ddy) <- BFS_DIRS) {
      val nx = fromX + ddx
      val ny = fromY + ddy
      val key = packCoord(nx, ny)
      if (!visited.contains(key) && nx >= 0 && nx < world.width && ny >= 0 && ny < world.height &&
          world.isWalkable(nx, ny) && !trapBlocks(nx, ny, botId)) {
        visited.add(key)
        if (nx == toX && ny == toY) return Some((ddx, ddy))
        if (!isTileOccupied(nx, ny, botId)) {
          queue.add((nx, ny, ddx, ddy))
        }
      }
    }

    var explored = 0
    while (!queue.isEmpty && explored < BFS_MAX_CELLS) {
      val (cx, cy, firstDx, firstDy) = queue.poll()
      explored += 1

      for ((ddx, ddy) <- BFS_DIRS) {
        val nx = cx + ddx
        val ny = cy + ddy
        val key = packCoord(nx, ny)
        if (!visited.contains(key) && nx >= 0 && nx < world.width && ny >= 0 && ny < world.height &&
            world.isWalkable(nx, ny) && !trapBlocks(nx, ny, botId)) {
          visited.add(key)
          if (nx == toX && ny == toY) return Some((firstDx, firstDy))
          if (!isTileOccupied(nx, ny, botId)) {
            queue.add((nx, ny, firstDx, firstDy))
          }
        }
      }
    }
    None
  }

  /** Move bot one step. Returns true if moved. */
  private def applyStep(bot: Player, dx: Int, dy: Int): Boolean = {
    val botPos = bot.getPosition
    val nx = botPos.getX + dx
    val ny = botPos.getY + dy
    if (canMoveTo(nx, ny, bot.getId)) {
      bot.setPosition(new Position(nx, ny))
      bot.setDirection(Direction.fromMovement(dx, dy))
      broadcastBotPosition(bot)
      true
    } else false
  }

  /** Try direct move toward (tx,ty), falling back to BFS if blocked. */
  private def moveTowardPoint(bot: Player, tx: Int, ty: Int): Unit = {
    val botPos = bot.getPosition
    val dx = tx - botPos.getX
    val dy = ty - botPos.getY
    val sdx = Integer.signum(dx)
    val sdy = Integer.signum(dy)

    if (sdx == 0 && sdy == 0) return

    // Fast path: try direct moves first
    if (sdx != 0 && sdy != 0 && applyStep(bot, sdx, sdy)) return
    if (Math.abs(dx) >= Math.abs(dy)) {
      if (sdx != 0 && applyStep(bot, sdx, 0)) return
      if (sdy != 0 && applyStep(bot, 0, sdy)) return
    } else {
      if (sdy != 0 && applyStep(bot, 0, sdy)) return
      if (sdx != 0 && applyStep(bot, sdx, 0)) return
    }

    // Direct path blocked - use BFS to navigate around walls
    bfsNextStep(botPos.getX, botPos.getY, tx, ty, bot.getId).foreach { case (bfsDx, bfsDy) =>
      applyStep(bot, bfsDx, bfsDy)
    }
  }

  /** Strafe perpendicular to the target (orbit around them). */
  private def strafe(bot: Player, target: Player): Unit = {
    val botPos = bot.getPosition
    val targetPos = target.getPosition
    val dx = (targetPos.getX - botPos.getX).toFloat
    val dy = (targetPos.getY - botPos.getY).toFloat
    val dir = strafeDirection.getOrDefault(bot.getId, 1)

    val perpX = -dy * dir
    val perpY = dx * dir
    val sdx = Integer.signum(Math.round(perpX))
    val sdy = Integer.signum(Math.round(perpY))

    if (sdx == 0 && sdy == 0) {
      moveRandom(bot)
      return
    }

    // Try strafe direction, then components, then flip
    if (applyStep(bot, sdx, sdy)) return
    if (sdx != 0 && applyStep(bot, sdx, 0)) return
    if (sdy != 0 && applyStep(bot, 0, sdy)) return

    // Blocked on all direct strafe attempts - use BFS to a strafe target point
    val strafeTargetX = botPos.getX + sdx * 3
    val strafeTargetY = botPos.getY + sdy * 3
    val bfsMoved = bfsNextStep(botPos.getX, botPos.getY, strafeTargetX, strafeTargetY, bot.getId)
      .exists { case (bfsDx, bfsDy) => applyStep(bot, bfsDx, bfsDy) }

    if (!bfsMoved) {
      // Still stuck - flip strafe direction for next time
      strafeDirection.put(bot.getId, -dir)
    }
  }

  private def moveAway(bot: Player, target: Player): Unit = {
    val botPos = bot.getPosition
    val targetPos = target.getPosition
    val dx = botPos.getX - targetPos.getX
    val dy = botPos.getY - targetPos.getY

    val sdx = Integer.signum(dx)
    val sdy = Integer.signum(dy)

    val (fdx, fdy) = if (sdx == 0 && sdy == 0) {
      val r = scala.util.Random.nextInt(4)
      r match {
        case 0 => (1, 0)
        case 1 => (-1, 0)
        case 2 => (0, 1)
        case _ => (0, -1)
      }
    } else (sdx, sdy)

    // Try direct away (diagonal, then axes)
    if (fdx != 0 && fdy != 0 && applyStep(bot, fdx, fdy)) return
    if (Math.abs(dx) >= Math.abs(dy)) {
      if (fdx != 0 && applyStep(bot, fdx, 0)) return
      if (fdy != 0 && applyStep(bot, 0, fdy)) return
    } else {
      if (fdy != 0 && applyStep(bot, 0, fdy)) return
      if (fdx != 0 && applyStep(bot, fdx, 0)) return
    }

    // Direct away blocked - BFS to a point away from target
    val awayX = botPos.getX + fdx * 8
    val awayY = botPos.getY + fdy * 8
    // Clamp the away target to world bounds
    val world = instance.world
    val clampedX = Math.max(0, Math.min(world.width - 1, awayX))
    val clampedY = Math.max(0, Math.min(world.height - 1, awayY))
    bfsNextStep(botPos.getX, botPos.getY, clampedX, clampedY, bot.getId).foreach { case (bfsDx, bfsDy) =>
      applyStep(bot, bfsDx, bfsDy)
    }
  }

  private def moveToward(bot: Player, target: Player): Unit = {
    moveTowardPoint(bot, target.getPosition.getX, target.getPosition.getY)
  }

  // --- Item pickup and usage ---

  private def tryPickupItems(bot: Player): Unit = {
    val botPos = bot.getPosition
    val pickup = instance.itemManager.checkPickup(bot.getId, botPos.getX, botPos.getY)
    pickup.foreach { event =>
      instance.broadcastItemPickup(event.item, event.playerId)
    }
  }

  private def tryUseItems(bot: Player, target: Player): Unit = {
    val inventory = instance.itemManager.getInventory(bot.getId)
    if (inventory.isEmpty) return

    inventory.foreach { item =>
      item.itemType match {
        case ItemType.Heart =>
          bot.synchronized {
            if (bot.getHealth.toFloat / bot.getMaxHealth.toFloat < 0.5f) {
              useItem(bot, item)
              bot.setHealth(bot.getMaxHealth)
              broadcastBotPosition(bot)
            }
          }

        case ItemType.Shield =>
          bot.synchronized {
            if (!bot.hasShield) {
              useItem(bot, item)
              bot.setShieldUntil(System.currentTimeMillis() + Constants.SHIELD_DURATION_MS)
              broadcastBotPosition(bot)
            }
          }

        case ItemType.Gem =>
          bot.synchronized {
            if (!bot.hasGemBoost) {
              useItem(bot, item)
              bot.setGemBoostUntil(System.currentTimeMillis() + Constants.GEM_DURATION_MS)
              broadcastBotPosition(bot)
            }
          }

        case ItemType.Fence =>
          if (target != null) {
            val botPos = bot.getPosition
            val targetPos = target.getPosition
            val fdx = Integer.signum(targetPos.getX - botPos.getX)
            val fdy = Integer.signum(targetPos.getY - botPos.getY)
            val fenceX = botPos.getX + fdx * 2
            val fenceY = botPos.getY + fdy * 2
            useItem(bot, item)
            placeFence(bot, fenceX, fenceY)
          }

        case _ =>
      }
    }
  }

  private def useItem(bot: Player, item: com.gridgame.common.model.Item): Unit = {
    instance.itemManager.removeFromInventory(bot.getId, item.id)
    val packet = new ItemPacket(
      instance.server.getNextSequenceNumber,
      bot.getId,
      bot.getPosition.getX, bot.getPosition.getY,
      item.itemType.id,
      item.id,
      ItemAction.USE
    )
    instance.broadcastToInstance(packet)
  }

  private[server] def placeFence(bot: Player, targetX: Int, targetY: Int): Unit = {
    val (perpDx, perpDy) = bot.getDirection match {
      case Direction.Up | Direction.Down    => (1, 0)
      case Direction.Left | Direction.Right => (0, 1)
    }

    val positions = Seq(
      (targetX - perpDx, targetY - perpDy),
      (targetX, targetY),
      (targetX + perpDx, targetY + perpDy)
    )

    // Only on open ground, as a player's fence (ClientHandler.placeFence): this used to turn any
    // cell into fence, walls and water included, and a fence stops the shots that fly over walls
    val w = instance.world
    positions.foreach { case (tx, ty) =>
      if (w.isWalkable(tx, ty) && w.setTile(tx, ty, com.gridgame.common.model.Tile.Fence)) {
        instance.broadcastTileUpdate(bot.getId, tx, ty, com.gridgame.common.model.Tile.Fence.id)
      }
    }
  }

  // --- Targeting ---

  private def distanceBetween(a: Position, b: Position): Float = {
    val dx = a.getX - b.getX
    val dy = a.getY - b.getY
    Math.sqrt(dx * dx + dy * dy).toFloat
  }

  private def findNearestPlayer(bot: Player): Player = {
    var nearest: Player = null
    var nearestDist = Float.MaxValue

    // Straight over the registry: a match holds at most a few dozen players. This used to search
    // the projectile manager's spatial grid, which belongs to the projectile tick's thread and is
    // rebuilt in place every 30ms, so from this thread it was read mid-rebuild.
    val botPos = bot.getPosition
    // Nobody on the far side of the opening divider is worth chasing: a bot can neither reach
    // them nor hit them, and would spend the opening walking into the wall shooting it
    val divider = instance.divider
    val side = if (divider != null && divider.up) divider.side(botPos.getX, botPos.getY) else 0
    instance.registry.forEachPlayer { player =>
      if (!player.isDead && !player.getId.equals(bot.getId) && !instance.isTeammate(bot.getId, player.getId) &&
          (side == 0 || divider.side(player.getPosition.getX, player.getPosition.getY) == side)) {
        val dist = distanceBetween(botPos, player.getPosition)
        if (dist < nearestDist) {
          nearestDist = dist
          nearest = player
        }
      }
    }

    nearest
  }

  // --- Collision checks ---

  private def isTileOccupied(x: Int, y: Int, excludeId: UUID): Boolean = {
    val key = packCoord(x, y)
    if (!occupiedTiles.contains(key)) return false
    // Check if the occupant is the excluded bot
    val owner = occupiedTileOwners.get(key)
    owner != null && !owner.equals(excludeId)
  }

  private def canMoveTo(x: Int, y: Int, botId: UUID): Boolean = {
    val world = instance.world
    x >= 0 && x < world.width && y >= 0 && y < world.height &&
      world.isWalkable(x, y) && !isTileOccupied(x, y, botId) && !trapBlocks(x, y, botId)
  }

  // The cell the bot currently being moved is chasing. A trap under the target is not a reason to
  // stand off — the whole point of one laid there is that the target is standing on it.
  private var chaseX = Int.MinValue
  private var chaseY = Int.MinValue
  // This tick's clock, so the hundreds of cells a BFS looks at don't each read it
  private var tickNow = 0L

  /** Would an enemy's armed trap catch a bot stepping onto (x, y)? */
  private def trapBlocks(x: Int, y: Int, botId: UUID): Boolean = {
    // The usual case, and the BFS asks this of every cell it reaches
    if (instance.trapManager.size == 0) return false
    if (x == chaseX && y == chaseY) return false
    val trap = instance.trapManager.trapAt(x, y)
    trap != null && trap.isArmed(tickNow) &&
      !trap.ownerId.equals(botId) && !instance.isTeammate(trap.ownerId, botId)
  }

  // --- Barriers ---

  /** Turn the bot's raised barrier to face the target. */
  private def faceBarrier(bot: Player, target: Player): Unit = {
    val bp = bot.getPosition
    val tp = target.getPosition
    val dx = tp.getX - bp.getX
    val dy = tp.getY - bp.getY
    if (dx != 0 || dy != 0) bot.setBarrierAngle(Math.atan2(dy, dx).toFloat)
  }

  /** Drop the bot's barrier before it fires or casts anything, as a player's does. */
  private def lowerBarrier(bot: Player): Unit = {
    if (bot.hasBarrier) {
      bot.dropBarrier()
      barrierShown.remove(bot.getId)
      broadcastBotPosition(bot)
    }
  }

  /** Is an enemy's shot, one a barrier would stop, coming at the bot from close by? */
  private def incomingShot(bot: Player): Boolean = {
    val bp = bot.getPosition
    val bx = bp.getX.toFloat
    val by = bp.getY.toFloat
    var found = false
    instance.projectileManager.forEachProjectile { p =>
      if (!found && !p.ownerId.equals(bot.getId) && !instance.isTeammate(bot.getId, p.ownerId) &&
          !ProjectileDef.get(p.projectileType).passesThroughWalls) {
        val rx = bx - p.getX
        val ry = by - p.getY
        val dist = Math.sqrt(rx * rx + ry * ry).toFloat
        val speed = Math.sqrt(p.dx * p.dx + p.dy * p.dy).toFloat
        if (dist <= INCOMING_SHOT_CELLS && speed > 0.01f) {
          val along = (rx * p.dx + ry * p.dy) / speed
          // Heading this way, and would pass close enough to hit
          if (along > 0f && Math.abs(rx * p.dy - ry * p.dx) / speed <= INCOMING_SHOT_MISS_CELLS) found = true
        }
      }
    }
    found
  }

  // --- Abilities ---

  private def tryUseAbilities(bot: Player, target: Player): Unit = {
    if (bot.isPhased) return

    val charDef = CharacterDef.get(bot.getCharacterId)
    val dist = distanceBetween(bot.getPosition, target.getPosition)
    val now = System.currentTimeMillis()
    // Read and rolled forward once a tick, so both abilities see the same answer
    val closing = isClosing(bot.getId, dist)

    val lastQ = lastQAbilityTime.getOrDefault(bot.getId, 0L)
    if (now - lastQ >= charDef.qAbility.cooldownMs) {
      if (tryFireAbility(bot, target, charDef.qAbility, dist, closing)) {
        lastQAbilityTime.put(bot.getId, now)
      }
    }

    val lastE = lastEAbilityTime.getOrDefault(bot.getId, 0L)
    if (now - lastE >= charDef.eAbility.cooldownMs) {
      if (tryFireAbility(bot, target, charDef.eAbility, dist, closing)) {
        lastEAbilityTime.put(bot.getId, now)
      }
    }
  }

  /** Is this bot's target nearer than it was, and does it stay noted for next time? */
  private def isClosing(botId: UUID, dist: Float): Boolean = {
    val before = lastTargetDist.put(botId, java.lang.Float.valueOf(dist))
    before != null && dist < before.floatValue()
  }

  private def tryFireAbility(bot: Player, target: Player, ability: AbilityDef, dist: Float,
                             closing: Boolean): Boolean = {
    val botPos = bot.getPosition
    val targetPos = target.getPosition
    val dx = (targetPos.getX - botPos.getX).toFloat
    val dy = (targetPos.getY - botPos.getY).toFloat
    val len = Math.sqrt(dx * dx + dy * dy).toFloat

    val (ndx, ndy) = if (len < 0.01f) {
      val angle = scala.util.Random.nextFloat() * 2f * Math.PI.toFloat
      (Math.cos(angle).toFloat, Math.sin(angle).toFloat)
    } else {
      (dx / len, dy / len)
    }

    ability.castBehavior match {
      case StandardProjectile =>
        if (dist > ability.maxRange) return false
        lowerBarrier(bot)
        val projectile = instance.projectileManager.spawnProjectile(
          bot.getId, botPos.getX, botPos.getY,
          ndx, ndy, bot.getColorRGB, 0, ability.projectileType
        )
        if (projectile != null) instance.broadcastProjectileSpawn(projectile)
        true

      case fan @ FanProjectile(count, _) =>
        if (dist > ability.maxRange) return false
        lowerBarrier(bot)
        for (i <- 0 until count) {
          val theta = fan.angleOf(i)
          val cos = Math.cos(theta).toFloat
          val sin = Math.sin(theta).toFloat
          val rdx = ndx * cos - ndy * sin
          val rdy = ndx * sin + ndy * cos
          val projectile = instance.projectileManager.spawnProjectile(
            bot.getId, botPos.getX, botPos.getY,
            rdx, rdy, bot.getColorRGB, 0, ability.projectileType
          )
          if (projectile != null) instance.broadcastProjectileSpawn(projectile)
        }
        true

      case PhaseShiftBuff(durationMs) =>
        // A ranged bot's phase gets it away from someone who has closed in. A melee or skirmisher
        // bot's is its way in: twice the pace and untouchable, toward a target out of its reach.
        val wayIn = roleOf(bot.getCharacterId) != CombatRole.Ranged
        if (wayIn && (dist <= getMaxRange(bot.getCharacterId) || dist > 14)) return false
        if (!wayIn && dist > 5) return false
        lowerBarrier(bot)
        bot.setPhasedUntil(System.currentTimeMillis() + durationMs)
        broadcastBotPosition(bot)
        true

      case DashBuff(maxDistance, durationMs, _) =>
        // A root holds a bot where it is, as it holds a player against a step, a dash and a blink
        if (bot.isRooted || dist < 3 || dist > maxDistance + 5) return false
        lowerBarrier(bot)
        val clampedDist = Math.min(dist, maxDistance.toFloat)
        val world = instance.world
        var bestX = botPos.getX
        var bestY = botPos.getY
        for (step <- 1 to clampedDist.toInt) {
          val testX = Math.max(0, Math.min(world.width - 1, (botPos.getX + ndx * step).toInt))
          val testY = Math.max(0, Math.min(world.height - 1, (botPos.getY + ndy * step).toInt))
          if (world.isWalkable(testX, testY)) {
            bestX = testX
            bestY = testY
          }
        }
        bot.setPosition(new Position(bestX, bestY))
        bot.setPhasedUntil(System.currentTimeMillis() + durationMs)
        broadcastBotPosition(bot)
        true

      case GroundSlam(radius) =>
        if (dist > radius) return false
        lowerBarrier(bot)
        val projectile = instance.projectileManager.spawnProjectile(
          bot.getId, botPos.getX, botPos.getY,
          0.0f, 0.0f, bot.getColorRGB, 0, ability.projectileType
        )
        if (projectile != null) instance.broadcastProjectileSpawn(projectile)
        true

      case TeleportCast(maxDistance) =>
        if (bot.isRooted || dist < 4 || dist > maxDistance + 8) return false
        lowerBarrier(bot)
        val clampedDist = Math.min(dist, maxDistance.toFloat).toInt
        // A player's blink, stopping before the first wall rather than coming out beyond it
        bot.setPosition(Teleport.blinkTarget(instance.world, botPos.getX, botPos.getY, ndx, ndy, clampedDist))
        broadcastBotPosition(bot)
        true

      case TrapCast(trapType, maxRange) =>
        // Laid in the path of someone coming in: further off than this they will have wandered
        // away before it arms, and stepping away from it they will never meet it
        if (dist > TRAP_TARGET_CELLS || !closing) return false
        val botPos2 = bot.getPosition
        val cell = TrapPlacement.target(instance.world, botPos2.getX, botPos2.getY,
          targetPos.getX.toDouble, targetPos.getY.toDouble, maxRange)
        cell match {
          case Some(at) if instance.trapManager.trapAt(at.getX, at.getY) == null =>
            lowerBarrier(bot)
            val placed = instance.trapManager.place(bot.getId, bot.getTeamId, at.getX, at.getY,
              trapType, System.currentTimeMillis())
            if (placed == null) false
            else {
              placed.removed.foreach(instance.broadcastTrap(_, TrapAction.REMOVE))
              instance.broadcastTrap(placed.trap, TrapAction.SPAWN)
              Metrics.trapsPlaced.add(1L, Attrs.trapType(trapType))
              true
            }
          case _ => false
        }

      case BarrierCast(durationMs) =>
        // Up against someone who shoots from further off than a blade (a skirmisher's boulders
        // as well as a ranged character's shots) and can shoot it from where they are, while it
        // still has ground to close (in its own range it is about to attack, which would drop
        // it), or against a shot on its way in
        val underFire = (roleOf(target.getCharacterId) != CombatRole.Melee &&
          dist <= getMaxRange(target.getCharacterId) && dist > getMaxRange(bot.getCharacterId)) ||
          incomingShot(bot)
        if (!underFire) return false
        bot.raiseBarrier(System.currentTimeMillis(), durationMs, Math.atan2(ndy, ndx).toFloat)
        barrierShown.add(bot.getId)
        broadcastBotPosition(bot)
        true
    }
  }

  // --- Shooting ---

  private def tryShoot(bot: Player, target: Player, now: Long): Unit = {
    if (bot.isPhased) return
    val dist = distanceBetween(bot.getPosition, target.getPosition)
    val maxRange = getMaxRange(bot.getCharacterId)
    if (dist > maxRange) return

    val lastShot = lastShotTime.getOrDefault(bot.getId, 0L)
    val cooldown = botShootCooldown.getOrDefault(bot.getId, SHOOT_COOLDOWN_MIN_MS)
    if (now - lastShot < cooldown) return

    lastShotTime.put(bot.getId, now)
    lowerBarrier(bot)
    // Randomize next cooldown slightly
    botShootCooldown.put(bot.getId, SHOOT_COOLDOWN_MIN_MS + scala.util.Random.nextLong(SHOOT_COOLDOWN_MAX_MS - SHOOT_COOLDOWN_MIN_MS))

    val botPos = bot.getPosition
    val targetPos = target.getPosition
    val dx = (targetPos.getX - botPos.getX).toFloat
    val dy = (targetPos.getY - botPos.getY).toFloat
    val len = Math.sqrt(dx * dx + dy * dy).toFloat

    val (baseDx, baseDy) = if (len < 0.01f) {
      val angle = scala.util.Random.nextFloat() * 2f * Math.PI.toFloat
      (Math.cos(angle).toFloat, Math.sin(angle).toFloat)
    } else {
      (dx / len, dy / len)
    }

    // Add slight aim inaccuracy for more natural feel
    val aimOffset = (scala.util.Random.nextFloat() - 0.5f) * 2f * AIM_INACCURACY_RAD
    val cos = Math.cos(aimOffset).toFloat
    val sin = Math.sin(aimOffset).toFloat
    val ndx = baseDx * cos - baseDy * sin
    val ndy = baseDx * sin + baseDy * cos

    val charDef = CharacterDef.get(bot.getCharacterId)
    val projectile = instance.projectileManager.spawnProjectile(
      bot.getId,
      botPos.getX, botPos.getY,
      ndx, ndy,
      bot.getColorRGB,
      0,
      charDef.primaryProjectileType
    )
    if (projectile != null) instance.broadcastProjectileSpawn(projectile)
  }

  // --- Broadcasting ---

  private def broadcastBotPosition(bot: Player): Unit = {
    instance.broadcastToInstance(instance.stateUpdate(bot))
  }
}
