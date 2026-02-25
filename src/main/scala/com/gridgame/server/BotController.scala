package com.gridgame.server

import com.gridgame.common.Constants
import com.gridgame.common.model._
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
  private var executor: ScheduledExecutorService = _

  private val TICK_INTERVAL_MS = 100L
  private val BOT_MOVE_INTERVAL_MS = 100L // bots move every 100ms (10 moves/sec, close to player's ~20)
  private val SHOOT_COOLDOWN_MIN_MS = 700L
  private val SHOOT_COOLDOWN_MAX_MS = 1100L
  private val TARGET_HYSTERESIS_MS = 2000L // stick to a target for at least 2s
  private val AIM_INACCURACY_RAD = 0.12f // ~7 degrees max aim wobble

  // 8 cardinal + diagonal directions for obstacle avoidance
  private val ALL_DIRS = Array(
    (1, 0), (-1, 0), (0, 1), (0, -1),
    (1, 1), (1, -1), (-1, 1), (-1, -1)
  )

  def addBotId(id: UUID): Unit = {
    botIds.add(id)
    lastShotTime.put(id, 0L)
    lastQAbilityTime.put(id, 0L)
    lastEAbilityTime.put(id, 0L)
    // Stagger initial move times so bots don't all move on the same tick
    lastMoveTime.put(id, System.currentTimeMillis() - scala.util.Random.nextInt(BOT_MOVE_INTERVAL_MS.toInt))
    botShootCooldown.put(id, SHOOT_COOLDOWN_MIN_MS + scala.util.Random.nextLong(SHOOT_COOLDOWN_MAX_MS - SHOOT_COOLDOWN_MIN_MS))
    strafeDirection.put(id, if (scala.util.Random.nextBoolean()) 1 else -1)
  }

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
    println("BotController: Stopped")
  }

  private def tick(): Unit = {
    try {
      if (!instance.isRunning || instance.world == null) return

      val now = System.currentTimeMillis()
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

    // Movement gated by per-bot move timer
    val canMove = !bot.isRooted && {
      val lastMove = lastMoveTime.getOrDefault(bot.getId, 0L)
      val moveInterval = if (bot.isSlowed) BOT_MOVE_INTERVAL_MS * 2
                         else if (bot.hasSpeedBoost) (BOT_MOVE_INTERVAL_MS * 0.6).toLong
                         else BOT_MOVE_INTERVAL_MS
      now - lastMove >= moveInterval
    }

    if (canMove) {
      if (target != null) {
        moveSmart(bot, target)
      } else {
        wander(bot)
      }
      lastMoveTime.put(bot.getId, now)
    }

    if (target != null) {
      tryUseAbilities(bot, target)
      tryShoot(bot, target, now)
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

  private def isRanged(charId: Byte): Boolean = {
    getMaxRange(charId) >= 10
  }

  private def getPreferredRange(charId: Byte): Float = {
    val maxRange = getMaxRange(charId)
    if (isRanged(charId)) maxRange * 0.6f
    else 1.5f
  }

  private def moveSmart(bot: Player, target: Player): Unit = {
    val botPos = bot.getPosition
    val targetPos = target.getPosition
    val dist = distanceBetween(botPos, targetPos)
    val preferredRange = getPreferredRange(bot.getCharacterId)
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
    } else if (ranged) {
      strafe(bot, target)
    } else {
      moveToward(bot, target)
    }
  }

  private def wander(bot: Player): Unit = {
    if (scala.util.Random.nextFloat() < 0.3f) {
      moveRandom(bot)
    }
  }

  private def moveRandom(bot: Player): Unit = {
    val botPos = bot.getPosition
    val directions = scala.util.Random.shuffle(ALL_DIRS.toSeq)
    directions.find { case (ddx, ddy) =>
      canMoveTo(botPos.getX + ddx, botPos.getY + ddy, bot.getId)
    }.foreach { case (ddx, ddy) =>
      bot.setPosition(new Position(botPos.getX + ddx, botPos.getY + ddy))
      bot.setDirection(Direction.fromMovement(ddx, ddy))
      broadcastBotPosition(bot)
    }
  }

  // --- BFS pathfinding (bounded to avoid expensive searches) ---

  private val BFS_MAX_CELLS = 600 // max cells to explore (~25 cell radius)
  private val BFS_DIRS = Array((1, 0), (-1, 0), (0, 1), (0, -1))

  /** BFS from (fromX,fromY) toward (toX,toY). Returns the first step, or None if unreachable. */
  private def bfsNextStep(fromX: Int, fromY: Int, toX: Int, toY: Int, botId: UUID): Option[(Int, Int)] = {
    if (fromX == toX && fromY == toY) return None

    val world = instance.world
    // Pack coordinates into a single Long for the visited set: (x << 32) | (y & 0xFFFFFFFFL)
    val visited = new java.util.HashSet[Long]()
    // Queue entries: (x, y, firstStepX, firstStepY)
    val queue = new java.util.ArrayDeque[(Int, Int, Int, Int)]()

    val startKey = (fromX.toLong << 32) | (fromY.toLong & 0xFFFFFFFFL)
    visited.add(startKey)

    // Seed with walkable neighbors
    for ((ddx, ddy) <- BFS_DIRS) {
      val nx = fromX + ddx
      val ny = fromY + ddy
      val key = (nx.toLong << 32) | (ny.toLong & 0xFFFFFFFFL)
      if (!visited.contains(key) && nx >= 0 && nx < world.width && ny >= 0 && ny < world.height &&
          world.isWalkable(nx, ny)) {
        visited.add(key)
        if (nx == toX && ny == toY) return Some((ddx, ddy))
        // Only enqueue if not occupied (but target cell itself is fine to path toward)
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
        val key = (nx.toLong << 32) | (ny.toLong & 0xFFFFFFFFL)
        if (!visited.contains(key) && nx >= 0 && nx < world.width && ny >= 0 && ny < world.height &&
            world.isWalkable(nx, ny)) {
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

  private def placeFence(bot: Player, targetX: Int, targetY: Int): Unit = {
    val (perpDx, perpDy) = bot.getDirection match {
      case Direction.Up | Direction.Down    => (1, 0)
      case Direction.Left | Direction.Right => (0, 1)
    }

    val positions = Seq(
      (targetX - perpDx, targetY - perpDy),
      (targetX, targetY),
      (targetX + perpDx, targetY + perpDy)
    )

    val w = instance.world
    positions.foreach { case (tx, ty) =>
      if (w.setTile(tx, ty, com.gridgame.common.model.Tile.Fence)) {
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

    instance.registry.getAll.asScala.foreach { player =>
      if (!player.isDead && !player.getId.equals(bot.getId) && !instance.isTeammate(bot.getId, player.getId)) {
        val dist = distanceBetween(bot.getPosition, player.getPosition)
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
    instance.registry.getAll.asScala.exists { player =>
      !player.isDead && !player.getId.equals(excludeId) &&
        player.getPosition.getX == x && player.getPosition.getY == y
    }
  }

  private def canMoveTo(x: Int, y: Int, botId: UUID): Boolean = {
    val world = instance.world
    x >= 0 && x < world.width && y >= 0 && y < world.height &&
      world.isWalkable(x, y) && !isTileOccupied(x, y, botId)
  }

  // --- Abilities ---

  private def tryUseAbilities(bot: Player, target: Player): Unit = {
    if (bot.isPhased) return

    val charDef = CharacterDef.get(bot.getCharacterId)
    val dist = distanceBetween(bot.getPosition, target.getPosition)
    val now = System.currentTimeMillis()

    val lastQ = lastQAbilityTime.getOrDefault(bot.getId, 0L)
    if (now - lastQ >= charDef.qAbility.cooldownMs) {
      if (tryFireAbility(bot, target, charDef.qAbility, dist)) {
        lastQAbilityTime.put(bot.getId, now)
      }
    }

    val lastE = lastEAbilityTime.getOrDefault(bot.getId, 0L)
    if (now - lastE >= charDef.eAbility.cooldownMs) {
      if (tryFireAbility(bot, target, charDef.eAbility, dist)) {
        lastEAbilityTime.put(bot.getId, now)
      }
    }
  }

  private def tryFireAbility(bot: Player, target: Player, ability: AbilityDef, dist: Float): Boolean = {
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
        val projectile = instance.projectileManager.spawnProjectile(
          bot.getId, botPos.getX, botPos.getY,
          ndx, ndy, bot.getColorRGB, 0, ability.projectileType
        )
        if (projectile != null) instance.broadcastProjectileSpawn(projectile)
        true

      case FanProjectile(count, fanAngle) =>
        if (dist > ability.maxRange) return false
        val halfAngle = fanAngle / 2.0
        for (i <- 0 until count) {
          val theta = -halfAngle + (fanAngle * i / (count - 1).toDouble)
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
        if (dist > 5) return false
        bot.setPhasedUntil(System.currentTimeMillis() + durationMs)
        broadcastBotPosition(bot)
        true

      case DashBuff(maxDistance, durationMs, _) =>
        if (dist < 3 || dist > maxDistance + 5) return false
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
        val projectile = instance.projectileManager.spawnProjectile(
          bot.getId, botPos.getX, botPos.getY,
          0.0f, 0.0f, bot.getColorRGB, 0, ability.projectileType
        )
        if (projectile != null) instance.broadcastProjectileSpawn(projectile)
        true

      case TeleportCast(maxDistance) =>
        if (dist < 4 || dist > maxDistance + 8) return false
        val clampedDist = Math.min(dist, maxDistance.toFloat).toInt
        val world = instance.world
        var bestX = botPos.getX
        var bestY = botPos.getY
        for (step <- 1 to clampedDist) {
          val testX = Math.max(0, Math.min(world.width - 1, (botPos.getX + ndx * step).toInt))
          val testY = Math.max(0, Math.min(world.height - 1, (botPos.getY + ndy * step).toInt))
          if (world.isWalkable(testX, testY)) {
            bestX = testX
            bestY = testY
          }
        }
        bot.setPosition(new Position(bestX, bestY))
        bot.setServerTeleportedUntil(System.currentTimeMillis() + 500)
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
    val flags = (if (bot.hasShield) 0x01 else 0) |
                (if (bot.hasGemBoost) 0x02 else 0) |
                (if (bot.isFrozen) 0x04 else 0) |
                (if (bot.isPhased) 0x08 else 0) |
                (if (bot.isBurning) 0x10 else 0) |
                (if (bot.hasSpeedBoost) 0x20 else 0) |
                (if (bot.isRooted) 0x40 else 0) |
                (if (bot.isSlowed) 0x80 else 0)
    val packet = new PlayerUpdatePacket(
      instance.server.getNextSequenceNumber,
      bot.getId,
      bot.getPosition,
      bot.getColorRGB,
      bot.getHealth,
      0,
      flags,
      bot.getCharacterId
    )
    instance.broadcastToInstance(packet)
  }
}
