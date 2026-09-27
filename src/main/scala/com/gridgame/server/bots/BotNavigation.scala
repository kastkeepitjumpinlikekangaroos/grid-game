package com.gridgame.server.bots

import com.gridgame.common.model._
import com.gridgame.common.protocol._
import java.util.UUID

/**
 * Where a bot steps: toward its target, away from it or round it at its role's range, or a wander
 * with nobody to chase, round walls (a short BFS), other players and armed enemy traps. A bot steps
 * at twice a player's interval for the same character and state (Movement).
 */
trait BotNavigation { this: BotController =>
  private[bots] def moveSmart(bot: Player, target: Player): Unit = {
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

  private[bots] def wander(bot: Player): Unit = {
    if (scala.util.Random.nextFloat() < 0.3f) {
      moveRandom(bot)
    }
  }

  private[bots] def moveRandom(bot: Player): Unit = {
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

  private[bots] def moveToward(bot: Player, target: Player): Unit = {
    moveTowardPoint(bot, target.getPosition.getX, target.getPosition.getY)
  }

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

  /** Would an enemy's armed trap catch a bot stepping onto (x, y)? */
  private def trapBlocks(x: Int, y: Int, botId: UUID): Boolean = {
    // The usual case, and the BFS asks this of every cell it reaches
    if (instance.trapManager.size == 0) return false
    if (x == chaseX && y == chaseY) return false
    val trap = instance.trapManager.trapAt(x, y)
    trap != null && trap.isArmed(tickNow) &&
      !trap.ownerId.equals(botId) && !instance.isTeammate(trap.ownerId, botId)
  }
}
