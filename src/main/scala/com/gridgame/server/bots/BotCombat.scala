package com.gridgame.server.bots

import com.gridgame.common.model._
import com.gridgame.common.observability.Attrs
import com.gridgame.common.observability.Metrics
import com.gridgame.common.protocol._
import java.util.UUID

/** A bot fighting: its primary at its target (with a little aim wobble), its Q and E when they fit —
  * a slam in reach, a closer from further off, a trap for someone coming in, a barrier against
  * shots it would stop — and its barrier held up facing the target until it can strike. */
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

  /**
   * Would a shot of `pType` fired from (fromX, fromY) fly straight to (toX, toY) — nothing on the
   * way that stops a projectile, walked in the half-cell steps a projectile flies in and measured by
   * the cells it would be in? A type that passes through walls needs no clear line.
   */
  private[bots] def lineOfFire(fromX: Int, fromY: Int, toX: Int, toY: Int, pType: Byte): Boolean = {
    if (ProjectileDef.get(pType).passesThroughWalls) return true
    val world = instance.world
    val dx = (toX - fromX).toDouble
    val dy = (toY - fromY).toDouble
    val steps = Math.ceil(Math.sqrt(dx * dx + dy * dy) * 2).toInt
    var i = 1
    while (i < steps) {
      val t = i.toDouble / steps
      if (world.stopsProjectile(Math.floor(fromX + dx * t + 0.5).toInt, Math.floor(fromY + dy * t + 0.5).toInt)) return false
      i += 1
    }
    true
  }

  /** Can the bot strike its target from where it stands: in its primary's reach, with nothing solid
    * between them? What it lowers a raised barrier for, and nothing short of it. */
  private[bots] def canStrike(bot: Player, target: Player, dist: Float): Boolean = {
    val primary = CharacterDef.get(bot.getCharacterId).primaryProjectileType
    val bp = bot.getPosition
    val tp = target.getPosition
    dist <= getMaxRange(bot.getCharacterId) && lineOfFire(bp.getX, bp.getY, tp.getX, tp.getY, primary)
  }

  /** Can `shooter` hit the bot from where they stand with shots a barrier stops: someone who shoots
    * from further off than a blade (a skirmisher's boulders as well as a ranged character's shots),
    * in reach, with nothing solid between them? One whose shots fly through walls flies through a
    * barrier too, so it is no reason to raise one. */
  private def firesStoppableShotsAt(shooter: Player, bot: Player, dist: Float): Boolean = {
    val primary = CharacterDef.get(shooter.getCharacterId).primaryProjectileType
    val sp = shooter.getPosition
    val bp = bot.getPosition
    roleOf(shooter.getCharacterId) != CombatRole.Melee && !ProjectileDef.get(primary).passesThroughWalls &&
      dist <= getMaxRange(shooter.getCharacterId) && lineOfFire(sp.getX, sp.getY, bp.getX, bp.getY, primary)
  }

  /**
   * Is an enemy's shot on its way that a barrier raised facing (cos, sin) would stop: close by,
   * heading this way near enough to hit, with nothing solid in its way, and still out in front of
   * where the barrier would stand? One already inside that line would be past it, and one coming
   * from the side would go round it.
   */
  private def incomingShot(bot: Player, cos: Float, sin: Float): Boolean = {
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
          // Heading this way, and would pass close enough to hit...
          if (along > 0f && Math.abs(rx * p.dy - ry * p.dx) / speed <= INCOMING_SHOT_MISS_CELLS) {
            // ...into the front of the barrier, before anything else stops it
            val ex = p.getX + p.dx / speed * along
            val ey = p.getY + p.dy / speed * along
            if (Barrier.crossing(bx, by, cos, sin, p.getX, p.getY, ex, ey) >= 0f &&
                lineOfFire(Math.round(p.getX), Math.round(p.getY), bp.getX, bp.getY, p.projectileType)) found = true
          }
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

    // Behind a raised barrier a bot casts nothing until it can strike (holdsFire). Asked once a
    // tick, and only if a barrier is up: a Q that raises one holds the E after it
    lazy val strike = canStrike(bot, target, dist)
    def holdsFire(ability: AbilityDef): Boolean =
      bot.hasBarrier && !ability.castBehavior.isInstanceOf[BarrierCast] && !strike

    val lastQ = lastQAbilityTime.getOrDefault(bot.getId, 0L)
    if (now - lastQ >= charDef.qAbility.cooldownMs && !holdsFire(charDef.qAbility)) {
      if (tryFireAbility(bot, target, charDef.qAbility, dist, closing)) {
        lastQAbilityTime.put(bot.getId, now)
      }
    }

    val lastE = lastEAbilityTime.getOrDefault(bot.getId, 0L)
    if (now - lastE >= charDef.eAbility.cooldownMs && !holdsFire(charDef.eAbility)) {
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
          targetPos.getX.toDouble, targetPos.getY.toDouble, maxRange,
          (x, y) => instance.barrierAcross(bot.getId, botPos2.getX.toFloat, botPos2.getY.toFloat, x.toFloat, y.toFloat))
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
        // Up only while the bot can't strike its target — out of its reach, or behind terrain.
        // Able to, it attacks, and that drops a barrier: raised then, it came straight down again.
        if (bot.hasBarrier || canStrike(bot, target, dist)) return false
        // And only against what it will stop: a target who can shoot the bot from where they
        // stand with shots that don't fly through walls, or such a shot coming at its front
        val facing = Math.atan2(ndy, ndx).toFloat
        val underFire = firesStoppableShotsAt(target, bot, dist) ||
          incomingShot(bot, Math.cos(facing).toFloat, Math.sin(facing).toFloat)
        if (!underFire) return false
        bot.raiseBarrier(System.currentTimeMillis(), durationMs, facing)
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

    // Behind a barrier, it holds its fire until the shot can land. It used to throw at a target
    // behind a wall as readily as at one in the open, dropping the barrier to hit the wall.
    if (bot.hasBarrier && !canStrike(bot, target, dist)) return

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
