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

class BotController(instance: GameInstance) {
  private val botIds = new java.util.concurrent.CopyOnWriteArrayList[UUID]()
  private val lastShotTime = new ConcurrentHashMap[UUID, Long]()
  private val lastQAbilityTime = new ConcurrentHashMap[UUID, Long]()
  private val lastEAbilityTime = new ConcurrentHashMap[UUID, Long]()
  private var executor: ScheduledExecutorService = _

  private val TICK_INTERVAL_MS = 200L
  private val SHOOT_COOLDOWN_MS = 1500L

  def addBotId(id: UUID): Unit = {
    botIds.add(id)
    lastShotTime.put(id, 0L)
    lastQAbilityTime.put(id, 0L)
    lastEAbilityTime.put(id, 0L)
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

      botIds.asScala.foreach { botId =>
        val bot = instance.registry.get(botId)
        if (bot != null && !bot.isDead) {
          bot.updateHeartbeat()
          if (!bot.isFrozen) {
            tickBot(bot)
          }
        }
      }
    } catch {
      case e: Exception =>
        System.err.println(s"BotController: Error in tick: ${e.getMessage}")
    }
  }

  private def tickBot(bot: Player): Unit = {
    val target = findNearestPlayer(bot)

    // Try to pick up nearby items
    tryPickupItems(bot)

    // Try to use items from inventory
    tryUseItems(bot, target)

    if (target == null) return

    // Rooted bots can't move but can still shoot/use abilities
    if (!bot.isRooted) {
      // Slowed bots move every other tick
      if (!bot.isSlowed || (System.currentTimeMillis() / TICK_INTERVAL_MS) % 2 == 0) {
        moveSmart(bot, target)
      }
    }
    tryUseAbilities(bot, target)
    tryShoot(bot, target)
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
    if (isRanged(charId)) {
      // Ranged characters prefer to stay at ~65% of their max range
      maxRange * 0.65f
    } else {
      // Melee characters want to be adjacent
      1.5f
    }
  }

  private def moveSmart(bot: Player, target: Player): Unit = {
    val botPos = bot.getPosition
    val targetPos = target.getPosition
    val dist = distanceBetween(botPos, targetPos)
    val preferredRange = getPreferredRange(bot.getCharacterId)
    val ranged = isRanged(bot.getCharacterId)

    val dx = targetPos.getX - botPos.getX
    val dy = targetPos.getY - botPos.getY

    if (dist < 0.01f) {
      // On top of another entity - move to a random adjacent cell
      moveRandom(bot)
    } else if (ranged && dist < preferredRange * 0.6f) {
      // Too close for a ranged character - kite away
      moveAway(bot, target)
    } else if (dist > preferredRange + 2) {
      // Too far - move closer
      moveToward(bot, target)
    }
    // else: at preferred range, stay put
  }

  private def moveRandom(bot: Player): Unit = {
    val botPos = bot.getPosition
    val directions = scala.util.Random.shuffle(Seq((1, 0), (-1, 0), (0, 1), (0, -1)))
    directions.find { case (ddx, ddy) =>
      canMoveTo(botPos.getX + ddx, botPos.getY + ddy, bot.getId)
    }.foreach { case (ddx, ddy) =>
      bot.setPosition(new Position(botPos.getX + ddx, botPos.getY + ddy))
      bot.setDirection(Direction.fromMovement(ddx, ddy))
      broadcastBotPosition(bot)
    }
  }

  private def moveAway(bot: Player, target: Player): Unit = {
    val botPos = bot.getPosition
    val targetPos = target.getPosition
    val dx = botPos.getX - targetPos.getX
    val dy = botPos.getY - targetPos.getY

    // Move away from target (opposite direction)
    val sdx = Integer.signum(dx)
    val sdy = Integer.signum(dy)

    // If both are zero (on top of each other), pick a random direction
    val (fdx, fdy) = if (sdx == 0 && sdy == 0) {
      val r = scala.util.Random.nextInt(4)
      r match {
        case 0 => (1, 0)
        case 1 => (-1, 0)
        case 2 => (0, 1)
        case _ => (0, -1)
      }
    } else (sdx, sdy)

    var moved = false

    if (Math.abs(dx) >= Math.abs(dy)) {
      if (fdx != 0 && canMoveTo(botPos.getX + fdx, botPos.getY, bot.getId)) {
        bot.setPosition(new Position(botPos.getX + fdx, botPos.getY))
        bot.setDirection(Direction.fromMovement(fdx, 0))
        moved = true
      } else if (fdy != 0 && canMoveTo(botPos.getX, botPos.getY + fdy, bot.getId)) {
        bot.setPosition(new Position(botPos.getX, botPos.getY + fdy))
        bot.setDirection(Direction.fromMovement(0, fdy))
        moved = true
      }
    } else {
      if (fdy != 0 && canMoveTo(botPos.getX, botPos.getY + fdy, bot.getId)) {
        bot.setPosition(new Position(botPos.getX, botPos.getY + fdy))
        bot.setDirection(Direction.fromMovement(0, fdy))
        moved = true
      } else if (fdx != 0 && canMoveTo(botPos.getX + fdx, botPos.getY, bot.getId)) {
        bot.setPosition(new Position(botPos.getX + fdx, botPos.getY))
        bot.setDirection(Direction.fromMovement(fdx, 0))
        moved = true
      }
    }

    if (moved) broadcastBotPosition(bot)
  }

  private def moveToward(bot: Player, target: Player): Unit = {
    val botPos = bot.getPosition
    val targetPos = target.getPosition
    val dx = targetPos.getX - botPos.getX
    val dy = targetPos.getY - botPos.getY

    // Don't move if already adjacent or on top of target
    if (Math.abs(dx) <= 1 && Math.abs(dy) <= 1) return

    val sdx = Integer.signum(dx)
    val sdy = Integer.signum(dy)

    var moved = false

    // Prefer moving on the axis with greater distance
    if (Math.abs(dx) >= Math.abs(dy)) {
      val newX = botPos.getX + sdx
      if (sdx != 0 && canMoveTo(newX, botPos.getY, bot.getId)) {
        bot.setPosition(new Position(newX, botPos.getY))
        bot.setDirection(Direction.fromMovement(sdx, 0))
        moved = true
      } else if (sdy != 0 && canMoveTo(botPos.getX, botPos.getY + sdy, bot.getId)) {
        bot.setPosition(new Position(botPos.getX, botPos.getY + sdy))
        bot.setDirection(Direction.fromMovement(0, sdy))
        moved = true
      }
    } else {
      val newY = botPos.getY + sdy
      if (sdy != 0 && canMoveTo(botPos.getX, newY, bot.getId)) {
        bot.setPosition(new Position(botPos.getX, newY))
        bot.setDirection(Direction.fromMovement(0, sdy))
        moved = true
      } else if (sdx != 0 && canMoveTo(botPos.getX + sdx, botPos.getY, bot.getId)) {
        bot.setPosition(new Position(botPos.getX + sdx, botPos.getY))
        bot.setDirection(Direction.fromMovement(sdx, 0))
        moved = true
      }
    }

    if (moved) broadcastBotPosition(bot)
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

    val healthPercent = bot.getHealth.toFloat / bot.getMaxHealth.toFloat

    inventory.foreach { item =>
      item.itemType match {
        case ItemType.Heart =>
          // Use heart when health below 50%
          bot.synchronized {
            if (bot.getHealth.toFloat / bot.getMaxHealth.toFloat < 0.5f) {
              useItem(bot, item)
              bot.setHealth(bot.getMaxHealth)
              broadcastBotPosition(bot)
            }
          }

        case ItemType.Shield =>
          // Use shield immediately if not already shielded
          bot.synchronized {
            if (!bot.hasShield) {
              useItem(bot, item)
              bot.setShieldUntil(System.currentTimeMillis() + Constants.SHIELD_DURATION_MS)
              broadcastBotPosition(bot)
            }
          }

        case ItemType.Gem =>
          // Use gem immediately if not already boosted
          bot.synchronized {
            if (!bot.hasGemBoost) {
              useItem(bot, item)
              bot.setGemBoostUntil(System.currentTimeMillis() + Constants.GEM_DURATION_MS)
              broadcastBotPosition(bot)
            }
          }

        case ItemType.Fence =>
          // Place fence between bot and nearest target
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
          // Skip Star (teleport is client-side) and unknown items
      }
    }
  }

  private def useItem(bot: Player, item: com.gridgame.common.model.Item): Unit = {
    instance.itemManager.removeFromInventory(bot.getId, item.id)
    // Broadcast USE to all clients so they see the effect
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

    // Try Q ability
    val lastQ = lastQAbilityTime.getOrDefault(bot.getId, 0L)
    if (now - lastQ >= charDef.qAbility.cooldownMs) {
      if (tryFireAbility(bot, target, charDef.qAbility, dist)) {
        lastQAbilityTime.put(bot.getId, now)
      }
    }

    // Try E ability
    val lastE = lastEAbilityTime.getOrDefault(bot.getId, 0L)
    if (now - lastE >= charDef.eAbility.cooldownMs) {
      if (tryFireAbility(bot, target, charDef.eAbility, dist)) {
        lastEAbilityTime.put(bot.getId, now)
      }
    }
  }

  /** Attempt to fire an ability. Returns true if used. */
  private def tryFireAbility(bot: Player, target: Player, ability: AbilityDef, dist: Float): Boolean = {
    val botPos = bot.getPosition
    val targetPos = target.getPosition
    val dx = (targetPos.getX - botPos.getX).toFloat
    val dy = (targetPos.getY - botPos.getY).toFloat
    val len = Math.sqrt(dx * dx + dy * dy).toFloat

    val (ndx, ndy) = if (len < 0.01f) {
      // Overlapping - pick a random direction
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
        // Use phase shift when enemy is close (defensive)
        if (dist > 5) return false
        bot.setPhasedUntil(System.currentTimeMillis() + durationMs)
        broadcastBotPosition(bot)
        true

      case DashBuff(maxDistance, durationMs, _) =>
        // Dash toward target
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
        // Blink toward target when at mid range
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

  private def tryShoot(bot: Player, target: Player): Unit = {
    if (bot.isPhased) return
    val dist = distanceBetween(bot.getPosition, target.getPosition)
    val maxRange = getMaxRange(bot.getCharacterId)
    if (dist > maxRange) return

    val now = System.currentTimeMillis()
    val lastShot = lastShotTime.getOrDefault(bot.getId, 0L)
    if (now - lastShot < SHOOT_COOLDOWN_MS) return

    lastShotTime.put(bot.getId, now)

    val botPos = bot.getPosition
    val targetPos = target.getPosition
    val dx = (targetPos.getX - botPos.getX).toFloat
    val dy = (targetPos.getY - botPos.getY).toFloat
    val len = Math.sqrt(dx * dx + dy * dy).toFloat

    val (ndx, ndy) = if (len < 0.01f) {
      // Overlapping - pick a random direction
      val angle = scala.util.Random.nextFloat() * 2f * Math.PI.toFloat
      (Math.cos(angle).toFloat, Math.sin(angle).toFloat)
    } else {
      (dx / len, dy / len)
    }

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
