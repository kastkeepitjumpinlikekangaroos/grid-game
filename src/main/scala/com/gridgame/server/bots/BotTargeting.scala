package com.gridgame.server.bots

import com.gridgame.common.model._
import com.gridgame.common.protocol._

/** Who a bot is after: the nearest enemy it can reach (none across the opening's wall), kept for at
  * least two seconds unless someone much nearer turns up, and the range its role fights from. */
trait BotTargeting { this: BotController =>
  private[bots] def pickTarget(bot: Player, now: Long): Player = {
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

  private[bots] def distanceBetween(a: Position, b: Position): Float = {
    val dx = a.getX - b.getX
    val dy = a.getY - b.getY
    Math.sqrt(dx * dx + dy * dy).toFloat
  }

  private[bots] def getMaxRange(charId: Byte): Int = {
    val charDef = CharacterDef.get(charId)
    ProjectileDef.get(charDef.primaryProjectileType).maxRange
  }

  private[bots] def roleOf(charId: Byte): CombatRole = CharacterDef.get(charId).role

  /** Keeps its distance, backing off from anyone who closes in. Only the ranged do: melee has to
    * close in, and a skirmisher's bruiser health is there so it can stand its ground. */
  private[bots] def isRanged(charId: Byte): Boolean = roleOf(charId) == CombatRole.Ranged

  private[bots] def getPreferredRange(charId: Byte): Float = roleOf(charId) match {
    case CombatRole.Ranged => getMaxRange(charId) * 0.6f
    // Halfway into its throw, where a boulder is hard to sidestep
    case CombatRole.Skirmisher => getMaxRange(charId) * 0.5f
    case CombatRole.Melee => 1.5f
  }
}
