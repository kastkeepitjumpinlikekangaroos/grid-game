package com.gridgame.server.bots

import com.gridgame.common.model._
import com.gridgame.common.protocol._
import java.util.UUID

/**
 * Where a bot steps: toward its target, away from it or round it at its role's range, or a wander
 * with nobody to chase, round walls, water, other players and armed enemy traps. A bot steps at
 * twice a player's interval for the same character and state (Movement).
 *
 * Closing in, a bot walks straight at its goal while the straight way there is open, and otherwise
 * along a way round whatever is in it (BotPaths), kept until the goal moves off its end or the bot
 * is moved off it. A bot that fights from range and has no clear shot at its target goes to the
 * nearest cell with one, at its range, rather than circling behind a wall. Backing off with a wall
 * at its back, it goes whichever way takes it furthest, and cornered, it circles out. Circling, it
 * turns back at a wall, or where its shot would be blocked.
 */
trait BotNavigation { this: BotController =>
  /** How far round a ranged bot or a skirmisher looks for a cell with a clear shot. */
  private val SHOT_SEARCH_STEPS = 10
  /** How far round a bot backing off with a wall at its back looks for the way out. */
  private val BACK_OFF_STEPS = 8
  /** Cells of open ground behind a bot that let it back straight off. */
  private val BACK_OFF_ROOM = 3
  /** Steps in a row a bot waits for someone in its way before it goes round them. */
  private val WAITS_BEFORE_GOING_ROUND = 3
  /** After a search that found no way to its goal, how long before another, unless the goal has
    * moved further than [[RETRY_DRIFT]]: someone fenced in can't be reached any more often by
    * searching the whole map for them on every step. */
  private val RETRY_MS = 1000L
  private val RETRY_DRIFT = 4

  private[bots] def moveSmart(bot: Player, target: Player): Unit = {
    val botPos = bot.getPosition
    val targetPos = target.getPosition
    val dist = distanceBetween(botPos, targetPos)
    val preferredRange = getPreferredRange(bot.getCharacterId)
    val role = roleOf(bot.getCharacterId)
    val ranged = isRanged(bot.getCharacterId)

    if (dist < 0.01f) {
      moveRandom(bot)
    } else if (role != CombatRole.Melee && dist <= preferredRange * 1.3f + SHOT_SEARCH_STEPS && !clearShot(bot, target)) {
      // Nothing to hit from here: out from behind the wall, to where there is, at its range
      seekShot(bot, target, preferredRange, ranged)
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
      if (applyStep(bot, ddx, ddy)) return
      i += 1
    }
  }

  /** Move bot one step, a diagonal one only between open cells (WorldData.cornersOpen), as a
    * player steps. Returns true if moved. */
  private def applyStep(bot: Player, dx: Int, dy: Int): Boolean = {
    val botPos = bot.getPosition
    val nx = botPos.getX + dx
    val ny = botPos.getY + dy
    if (canMoveTo(nx, ny, bot.getId) && instance.world.cornersOpen(botPos.getX, botPos.getY, dx, dy)) {
      bot.setPosition(new Position(nx, ny))
      bot.setDirection(Direction.fromMovement(dx, dy))
      stepped(botPos.getX, botPos.getY, nx, ny)
      broadcastBotPosition(bot)
      true
    } else false
  }

  /** A step onto `cell`, one of the eight round the bot. */
  private def stepTo(bot: Player, cell: Int): Boolean =
    applyStep(bot, cell % gridW - bot.getPosition.getX, cell / gridW - bot.getPosition.getY)

  /** Straight at (dx, dy) away: the diagonal, else the longer axis, else the shorter. */
  private def stepStraight(bot: Player, dx: Int, dy: Int): Boolean = {
    val sdx = Integer.signum(dx)
    val sdy = Integer.signum(dy)
    if (sdx != 0 && sdy != 0 && applyStep(bot, sdx, sdy)) return true
    if (Math.abs(dx) >= Math.abs(dy)) {
      (sdx != 0 && applyStep(bot, sdx, 0)) || (sdy != 0 && applyStep(bot, 0, sdy))
    } else {
      (sdy != 0 && applyStep(bot, 0, sdy)) || (sdx != 0 && applyStep(bot, sdx, 0))
    }
  }

  /** Is the straight way from (x, y) to (tx, ty) open — every cell of it one a bot can stand on,
    * and no wall's corner cut on the way, the diagonal steps first, as [[stepStraight]] takes it?
    * Someone standing on it is no matter: a step onto them goes round. */
  private def straightWayOpen(botId: UUID, x: Int, y: Int, tx: Int, ty: Int): Boolean = {
    val world = instance.world
    var cx = x
    var cy = y
    while (cx != tx || cy != ty) {
      val sx = Integer.signum(tx - cx)
      val sy = Integer.signum(ty - cy)
      if (!world.canStep(cx, cy, sx, sy) || trapBlocks(cx + sx, cy + sy, botId)) return false
      cx += sx
      cy += sy
    }
    true
  }

  /**
   * A step toward (tx, ty): straight while the straight way is open, else along the way round. The
   * way is found again when the goal has moved off its end by more than a sixth of what is left of
   * it, when the bot is not where it left it, and when it runs into something new (a fence, a trap
   * thrown down). Someone standing on it is gone round, onto a cell beside the one after; after a
   * few steps waiting for someone who doesn't move, on the straight way or the way round, a way is
   * found round everybody. A goal nobody could stand on (someone phased in a wall) is the open cell
   * nearest it; one with no way to it is looked for again only now and then, and meanwhile the bot
   * goes as near as it can.
   */
  private def moveTowardPoint(bot: Player, tx: Int, ty: Int): Unit = {
    val botPos = bot.getPosition
    val x = botPos.getX
    val y = botPos.getY
    if (x == tx && y == ty) return
    val route = routes.get(bot.getId)
    if (route.waits < WAITS_BEFORE_GOING_ROUND && straightWayOpen(bot.getId, x, y, tx, ty)) {
      route.clear()
      // A way there after all, whatever the last search found
      route.reachesGoal = true
      if (stepStraight(bot, tx - x, ty - y)) route.waits = 0
      else if (Math.max(Math.abs(tx - x), Math.abs(ty - y)) > 1) route.waits += 1
      return
    }

    val goal = nearestOpen(tx, ty)
    if (goal < 0) return
    val gx = goal % gridW
    val gy = goal / gridW
    val here = y * gridW + x
    if (goal == here) return
    // Someone on every side of the goal, and the bot one of the crowd: as near as it can get. A
    // search would find no way through them, and look over the whole map to be sure of it
    if (Math.max(Math.abs(gx - x), Math.abs(gy - y)) <= CROWD_CELLS + 1 && surrounded(gx, gy)) return
    val drift = Math.max(Math.abs(route.goalX - gx), Math.abs(route.goalY - gy))
    if (route.from != here || route.remaining == 0 || drift > Math.max(1, route.remaining / 6)) {
      if (!route.reachesGoal && drift <= RETRY_DRIFT && tickNow - route.searchedAt < RETRY_MS) {
        // No way there a moment ago: on along what it found, if the bot is still on it
        if (route.from != here || route.remaining == 0) return
      } else if (!findWay(bot.getId, x, y, gx, gy, route, crowded = route.waits >= WAITS_BEFORE_GOING_ROUND)) return
    }
    val next = route.cells(route.next)
    val nx = next % gridW
    val ny = next / gridW
    if (stepTo(bot, next)) {
      route.next += 1
      route.from = next
      route.waits = 0
    } else if (next == goal) {
      // Beside the goal, which someone stands on: there
    } else if (standsAt(nx, ny)) {
      if (!goRound(bot, route)) {
        route.waits += 1
        if (route.waits >= WAITS_BEFORE_GOING_ROUND) route.clear()
      }
    } else {
      // Something new in the way: find it again next step
      route.clear()
    }
  }

  /** Someone stands on the route's next cell: a step onto a cell beside the one after it, if one
    * is free, and on along the route from there. */
  private def goRound(bot: Player, route: BotRoute): Boolean = {
    if (route.remaining < 2) return false
    val after = route.cells(route.next + 1)
    val ax = after % gridW
    val ay = after / gridW
    val bx = bot.getPosition.getX
    val by = bot.getPosition.getY
    var d = 0
    while (d < 8) {
      val cx = bx + STEP_X(d)
      val cy = by + STEP_Y(d)
      if (Math.max(Math.abs(cx - ax), Math.abs(cy - ay)) <= 1 && applyStep(bot, STEP_X(d), STEP_Y(d))) {
        val cell = cy * gridW + cx
        route.next += 1
        if (cell == after) route.next += 1
        route.from = cell
        route.waits = 0
        return true
      }
      d += 1
    }
    false
  }

  /** Could the bot hit the target from where it stands, with nothing solid in the way of its
    * primary? */
  private def clearShot(bot: Player, target: Player): Boolean = {
    val bp = bot.getPosition
    val tp = target.getPosition
    lineOfFire(bp.getX, bp.getY, tp.getX, tp.getY, CharacterDef.get(bot.getCharacterId).primaryProjectileType)
  }

  /** A ranged bot or a skirmisher with no clear shot: a step toward the nearest cell it could hit
    * the target from, no nearer than it lets anyone come (a skirmisher lets them close) and no
    * further than it fights from. None near: a step toward the target. */
  private def seekShot(bot: Player, target: Player, preferredRange: Float, ranged: Boolean): Unit = {
    val nearest = if (ranged) preferredRange * 0.5f else 0f
    val furthest = Math.min(preferredRange * 1.3f, getMaxRange(bot.getCharacterId).toFloat)
    val bp = bot.getPosition
    val tp = target.getPosition
    val tx = tp.getX
    val ty = tp.getY
    val primary = CharacterDef.get(bot.getCharacterId).primaryProjectileType
    val n = lookAround(bot.getId, bp.getX, bp.getY, SHOT_SEARCH_STEPS, (2 * SHOT_SEARCH_STEPS + 1) * (2 * SHOT_SEARCH_STEPS + 1))
    var i = 1
    while (i < n) {
      val c = reached(i)
      val cx = c % gridW
      val cy = c / gridW
      val dx = (cx - tx).toFloat
      val dy = (cy - ty).toFloat
      val d = Math.sqrt(dx * dx + dy * dy).toFloat
      if (d >= nearest && d <= furthest && lineOfFire(cx, cy, tx, ty, primary)) {
        stepTo(bot, firstStepTo(c))
        return
      }
      i += 1
    }
    moveToward(bot, target)
  }

  /** Strafe perpendicular to the target (orbit around them), turning back at a wall or where the
    * shot would be blocked. */
  private def strafe(bot: Player, target: Player): Unit = {
    val dir = strafeDirection.getOrDefault(bot.getId, 1)
    if (strafeStep(bot, target, dir)) return
    // That way is shut: round the other way, from this step on
    strafeDirection.put(bot.getId, -dir)
    strafeStep(bot, target, -dir)
  }

  private def strafeStep(bot: Player, target: Player, dir: Int): Boolean = {
    val botPos = bot.getPosition
    val targetPos = target.getPosition
    val dx = (targetPos.getX - botPos.getX).toFloat
    val dy = (targetPos.getY - botPos.getY).toFloat

    val perpX = -dy * dir
    val perpY = dx * dir
    val sdx = Integer.signum(Math.round(perpX))
    val sdy = Integer.signum(Math.round(perpY))

    if (sdx == 0 && sdy == 0) {
      moveRandom(bot)
      return true
    }

    // The strafe direction, then its components
    strafeTo(bot, target, sdx, sdy) || (sdx != 0 && strafeTo(bot, target, sdx, 0)) ||
      (sdy != 0 && strafeTo(bot, target, 0, sdy))
  }

  /** A step, if it doesn't take the bot out of its shot. */
  private def strafeTo(bot: Player, target: Player, dx: Int, dy: Int): Boolean = {
    val nx = bot.getPosition.getX + dx
    val ny = bot.getPosition.getY + dy
    val tp = target.getPosition
    lineOfFire(nx, ny, tp.getX, tp.getY, CharacterDef.get(bot.getCharacterId).primaryProjectileType) &&
      applyStep(bot, dx, dy)
  }

  /** Back off from the target: straight away while there is room behind and the shot stays clear,
    * else whichever way takes the bot furthest from it, round the wall at its back, with a shot from
    * there if any has one; cornered, it circles out. */
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

    // Straight away (diagonal, then axes), where there is room behind
    if (fdx != 0 && fdy != 0 && backOff(bot, target, fdx, fdy)) return
    if (Math.abs(dx) >= Math.abs(dy)) {
      if (fdx != 0 && backOff(bot, target, fdx, 0)) return
      if (fdy != 0 && backOff(bot, target, 0, fdy)) return
    } else {
      if (fdy != 0 && backOff(bot, target, 0, fdy)) return
      if (fdx != 0 && backOff(bot, target, fdx, 0)) return
    }

    if (!stepFurthestFrom(bot, target)) strafe(bot, target)
  }

  /** A step back along (dx, dy), if there is open ground behind it to go on backing off over and
    * the target can still be shot from it. */
  private def backOff(bot: Player, target: Player, dx: Int, dy: Int): Boolean = {
    val world = instance.world
    val x = bot.getPosition.getX
    val y = bot.getPosition.getY
    var k = 2
    while (k <= BACK_OFF_ROOM) {
      if (!world.canStep(x + dx * (k - 1), y + dy * (k - 1), dx, dy) || trapBlocks(x + dx * k, y + dy * k, bot.getId)) return false
      k += 1
    }
    strafeTo(bot, target, dx, dy)
  }

  /** A step toward the cell a few steps round that is furthest from the target, if it is a cell
    * further off than the bot stands now, on a way that never takes it nearer the target than it
    * is: backing off round a wall, not past whoever it is backing off from. Of those, the furthest
    * the bot could shoot the target from, if any. */
  private def stepFurthestFrom(bot: Player, target: Player): Boolean = {
    val bp = bot.getPosition
    val tx = target.getPosition.getX
    val ty = target.getPosition.getY
    val now = distanceBetween(bp, target.getPosition)
    val n = lookAround(bot.getId, bp.getX, bp.getY, BACK_OFF_STEPS, (2 * BACK_OFF_STEPS + 1) * (2 * BACK_OFF_STEPS + 1))
    // The nearest each cell's way comes to the target; a cell's way is its parent's and one step,
    // and the parent comes first in the order the look round reached them
    val nearestOnWay = perCell
    nearestOnWay(reached(0)) = now
    val primary = CharacterDef.get(bot.getCharacterId).primaryProjectileType
    var best = -1
    var bestDist = now + 1f
    var bestShot = -1
    var bestShotDist = now + 1f
    var i = 1
    while (i < n) {
      val c = reached(i)
      val cx = c % gridW
      val cy = c / gridW
      val dx = (cx - tx).toFloat
      val dy = (cy - ty).toFloat
      val d = Math.sqrt(dx * dx + dy * dy).toFloat
      val onWay = Math.min(nearestOnWay(cellBefore(c)), d)
      nearestOnWay(c) = onWay
      if (onWay >= now - 0.5f) {
        if (d > bestDist) { bestDist = d; best = c }
        if (d > bestShotDist && lineOfFire(cx, cy, tx, ty, primary)) { bestShotDist = d; bestShot = c }
      }
      i += 1
    }
    val to = if (bestShot >= 0) bestShot else best
    to >= 0 && stepTo(bot, firstStepTo(to))
  }

  private[bots] def moveToward(bot: Player, target: Player): Unit = {
    moveTowardPoint(bot, target.getPosition.getX, target.getPosition.getY)
  }

  private def canMoveTo(x: Int, y: Int, botId: UUID): Boolean = {
    val world = instance.world
    x >= 0 && x < world.width && y >= 0 && y < world.height &&
      world.isWalkable(x, y) && !standsAt(x, y) && !trapBlocks(x, y, botId)
  }

  /** Would an enemy's armed trap catch a bot stepping onto (x, y)? */
  private[bots] def trapBlocks(x: Int, y: Int, botId: UUID): Boolean = {
    // The usual case, and the searches ask this of every cell they reach
    if (instance.trapManager.size == 0) return false
    if (x == chaseX && y == chaseY) return false
    val trap = instance.trapManager.trapAt(x, y)
    trap != null && trap.isArmed(tickNow) &&
      !trap.ownerId.equals(botId) && !instance.isTeammate(trap.ownerId, botId)
  }
}
