package com.gridgame.server.bots

import com.gridgame.common.model._
import com.gridgame.common.observability.Attrs
import com.gridgame.common.observability.Metrics
import com.gridgame.common.protocol._
import java.util.UUID

/** A bot fighting: its primary at its target (with a little aim wobble), its Q and E when they fit —
  * a slam in reach, a closer from further off, a trap for someone coming in, a barrier against a
  * shot coming at it — and its barrier held up facing the target. */
trait BotCombat { this: BotController =>
  /** Turn the bot's raised barrier to face the target. */
  private[bots] def faceBarrier(bot: Player, target: Player): Unit = {
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

  private[bots] def tryUseAbilities(bot: Player, target: Player): Unit = {
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

  private[bots] def tryShoot(bot: Player, target: Player, now: Long): Unit = {
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
}
