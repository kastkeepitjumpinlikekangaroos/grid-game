package com.gridgame.server

import com.gridgame.common.Constants
import com.gridgame.common.model.CharacterDef
import com.gridgame.common.model.Direction
import com.gridgame.common.model.ItemType
import com.gridgame.common.model.Player
import com.gridgame.common.model.Position
import com.gridgame.common.model.ProjectileType
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
  private var executor: ScheduledExecutorService = _

  private val TICK_INTERVAL_MS = 200L
  private val SHOOT_COOLDOWN_MS = 1500L

  def addBotId(id: UUID): Unit = {
    botIds.add(id)
    lastShotTime.put(id, 0L)
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
          tickBot(bot)
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

    moveSmart(bot, target)
    tryShoot(bot, target)
  }

  // --- Range-based movement ---

  private def getMaxRange(charId: Byte): Int = {
    val charDef = CharacterDef.get(charId)
    charDef.primaryProjectileType match {
      case ProjectileType.AXE         => Constants.AXE_MAX_RANGE
      case ProjectileType.TALON       => Constants.TALON_MAX_RANGE
      case ProjectileType.SOUL_BOLT   => Constants.SOUL_BOLT_MAX_RANGE
      case ProjectileType.SPLASH      => Constants.SPLASH_MAX_RANGE
      case ProjectileType.BULLET      => Constants.BULLET_MAX_RANGE
      case ProjectileType.ARCANE_BOLT => Constants.ARCANE_BOLT_MAX_RANGE
      case _                          => Constants.CHARGE_MIN_RANGE // NORMAL default
    }
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

    if (ranged && dist < preferredRange * 0.6f && dist > 0) {
      // Too close for a ranged character - kite away
      moveAway(bot, target)
    } else if (dist > preferredRange + 2) {
      // Too far - move closer
      moveToward(bot, target)
    }
    // else: at preferred range, stay put
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
          if (healthPercent < 0.5f) {
            useItem(bot, item)
            bot.setHealth(bot.getMaxHealth)
            broadcastBotPosition(bot)
          }

        case ItemType.Shield =>
          // Use shield immediately if not already shielded
          if (!bot.hasShield) {
            useItem(bot, item)
            bot.setShieldUntil(System.currentTimeMillis() + Constants.SHIELD_DURATION_MS)
            broadcastBotPosition(bot)
          }

        case ItemType.Gem =>
          // Use gem immediately if not already boosted
          if (!bot.hasGemBoost) {
            useItem(bot, item)
            bot.setGemBoostUntil(System.currentTimeMillis() + Constants.GEM_DURATION_MS)
            broadcastBotPosition(bot)
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
      if (!player.isDead && !player.getId.equals(bot.getId)) {
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

  // --- Shooting ---

  private def tryShoot(bot: Player, target: Player): Unit = {
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
    if (len < 0.01f) return

    val ndx = dx / len
    val ndy = dy / len

    val charDef = CharacterDef.get(bot.getCharacterId)
    val projectile = instance.projectileManager.spawnProjectile(
      bot.getId,
      botPos.getX, botPos.getY,
      ndx, ndy,
      bot.getColorRGB,
      0,
      charDef.primaryProjectileType
    )
    instance.broadcastProjectileSpawn(projectile)
  }

  // --- Broadcasting ---

  private def broadcastBotPosition(bot: Player): Unit = {
    val flags = (if (bot.hasShield) 0x01 else 0) |
                (if (bot.hasGemBoost) 0x02 else 0) |
                (if (bot.isFrozen) 0x04 else 0) |
                (if (bot.isPhased) 0x08 else 0)
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
