package com.gridgame.server.bots

import com.gridgame.common.model._
import java.util.UUID

/** The way a bot is following round something in its way (BotNavigation): the cells from its next
  * step to the end, and what they were found for. One per bot, kept from step to step. */
private[bots] final class BotRoute {
  private[bots] var cells = new Array[Int](64)
  private[bots] var length = 0
  private[bots] var next = 0
  /** The goal the way was found for. Its end is that cell, or the nearest to it the search got. */
  private[bots] var goalX = 0
  private[bots] var goalY = 0
  /** The cell the next step follows from. A bot standing anywhere else has been moved (a
    * knockback, a dash, a blink, a respawn), and the way is found again. */
  private[bots] var from = -1
  /** Steps in a row it couldn't take for someone standing in the way. */
  private[bots] var waits = 0
  /** Did the search get all the way to the goal, and when was it (the bots' clock)? A way that
    * can't be found isn't looked for again on every step. */
  private[bots] var reachesGoal = false
  private[bots] var searchedAt = 0L

  def remaining: Int = length - next
  def clear(): Unit = { length = 0; next = 0; from = -1 }
}

/**
 * The searches behind a bot's steps (BotNavigation), on the match's grid: the way to a cell round
 * whatever stands between (A*), and a look round the bot a few steps out (breadth first).
 *
 * A bot steps to any of the eight cells round it, and a step takes as long whichever it is, so a
 * way is measured in steps; a diagonal costs a hair more than a straight step, so that of the
 * shortest ways the straightest is taken. In the way: every cell nobody can stand on (the opening's
 * divider too), an enemy's armed trap, and anyone standing near the bot (further off, they will
 * have moved by the time it gets there).
 *
 * The scratch arrays are the world's size and are reused from one search to the next: a match's
 * bots have a thread of their own. A cell is marked as seen by a search with that search's number,
 * so nothing has to be cleared between two.
 */
trait BotPaths { this: BotController =>
  private val STEP = 1000
  private val DIAGONAL = 1001
  /** How many cells a search for a way may close before it settles for the nearest to the goal it
    * got to. Enough to cross the Colony, the biggest map (240 x 240), round anything on it. */
  private val MAX_CLOSED = 20000
  /** ...and one round everybody: a crowd is got round near by, or not at all (a target with
    * someone on every side of them can't be got to, and searching the whole map won't change it). */
  private val MAX_CLOSED_CROWDED = 2000
  /** Players standing this near the bot are in its way; further off they are not. */
  private[bots] val CROWD_CELLS = 2
  /** How far round a cell nobody can stand on (someone phased, in a wall) its nearest open one is
    * looked for. */
  private val OPEN_GROUND_CELLS = 20

  // The eight steps, the four straight ones first
  private[bots] val STEP_X = Array(1, -1, 0, 0, 1, 1, -1, -1)
  private[bots] val STEP_Y = Array(0, 0, 1, -1, 1, -1, 1, -1)

  private[bots] var gridW = 0
  private var gridH = 0
  private var cost: Array[Int] = _
  private var cameFrom: Array[Int] = _
  private var seenIn: Array[Int] = _
  private var closedIn: Array[Int] = _
  /** The cells the last look round reached, nearest first, the bot's own at 0. */
  private[bots] var reached: Array[Int] = _
  /** Room for a figure per cell, for whoever reads the last look round. */
  private[bots] var perCell: Array[Float] = _
  private var search = 0
  private var open = new Array[Long](1024)
  private var openSize = 0
  // Who stands where: a cell is taken while its mark is this tick's
  private var standing: Array[Int] = _
  private var standingMark = 0

  /** The scratch arrays, the size of this world. */
  private[bots] def gridFor(world: WorldData): Unit = {
    if (cost == null || world.width != gridW || world.height != gridH) {
      gridW = world.width
      gridH = world.height
      val n = gridW * gridH
      cost = new Array[Int](n)
      cameFrom = new Array[Int](n)
      seenIn = new Array[Int](n)
      closedIn = new Array[Int](n)
      reached = new Array[Int](n)
      perCell = new Array[Float](n)
      standing = new Array[Int](n)
      search = 0
      standingMark = 0
    }
  }

  private[bots] def releaseGrid(): Unit = {
    cost = null; cameFrom = null; seenIn = null; closedIn = null; reached = null; perCell = null; standing = null
    gridW = 0; gridH = 0
  }

  /** Mark where every living player stands, at the start of a tick. */
  private[bots] def markStanding(): Unit = {
    standingMark += 1
    instance.registry.forEachPlayer { p =>
      if (!p.isDead) {
        val x = p.getPosition.getX
        val y = p.getPosition.getY
        if (x >= 0 && y >= 0 && x < gridW && y < gridH) standing(y * gridW + x) = standingMark
      }
    }
  }

  /** Is someone standing on (x, y)? A bot asking about the cells round it: its own is never one. */
  private[bots] def standsAt(x: Int, y: Int): Boolean =
    x >= 0 && y >= 0 && x < gridW && y < gridH && standing(y * gridW + x) == standingMark

  /** Is every cell round (x, y) one nobody can stand on, or one somebody stands on? */
  private[bots] def surrounded(x: Int, y: Int): Boolean = {
    val world = instance.world
    var d = 0
    while (d < 8) {
      val nx = x + STEP_X(d)
      val ny = y + STEP_Y(d)
      if (world.isWalkable(nx, ny) && !standsAt(nx, ny)) return false
      d += 1
    }
    true
  }

  /** A bot's step, kept up to date for the bots after it this tick. */
  private[bots] def stepped(fromX: Int, fromY: Int, toX: Int, toY: Int): Unit = {
    if (fromX >= 0 && fromY >= 0 && fromX < gridW && fromY < gridH) standing(fromY * gridW + fromX) = 0
    if (toX >= 0 && toY >= 0 && toX < gridW && toY < gridH) standing(toY * gridW + toX) = standingMark
  }

  private def nextSearch(): Int = {
    if (search == Int.MaxValue) {
      java.util.Arrays.fill(seenIn, 0)
      java.util.Arrays.fill(closedIn, 0)
      search = 0
    }
    search += 1
    search
  }

  /** A lower bound on the steps from one cell to another, in the search's units: the steps of the
    * way across open ground, the diagonals' hair included. */
  private def estimate(x: Int, y: Int, gx: Int, gy: Int): Int = {
    val ax = Math.abs(gx - x)
    val ay = Math.abs(gy - y)
    if (ax > ay) STEP * ax + (DIAGONAL - STEP) * ay else STEP * ay + (DIAGONAL - STEP) * ax
  }

  /**
   * The way from (sx, sy) to (gx, gy), put into `route`: to the goal, or as near it as the search
   * got if it can't be reached or is too far round to find. The goal itself is always open, as the
   * target stands there. With `crowded`, everyone standing anywhere is in the way, not just those
   * near the bot. False if there is no step that gets the bot any nearer.
   */
  private[bots] def findWay(botId: UUID, sx: Int, sy: Int, gx: Int, gy: Int, route: BotRoute,
                            crowded: Boolean): Boolean = {
    val world = instance.world
    val w = gridW
    val id = nextSearch()
    val start = sy * w + sx
    val goal = gy * w + gx
    openSize = 0
    cost(start) = 0
    cameFrom(start) = -1
    seenIn(start) = id
    val startEstimate = estimate(sx, sy, gx, gy)
    push(key(startEstimate, startEstimate, start))
    var best = start
    var bestEstimate = startEstimate
    var closed = 0
    var found = false
    val limit = if (crowded) MAX_CLOSED_CROWDED else MAX_CLOSED
    while (openSize > 0 && !found && closed < limit) {
      val c = (pop() & 0xFFFFF).toInt
      if (closedIn(c) != id) {
        closedIn(c) = id
        if (c == goal) found = true
        else {
          closed += 1
          val cx = c % w
          val cy = c / w
          val here = cost(c)
          var d = 0
          while (d < 8) {
            val nx = cx + STEP_X(d)
            val ny = cy + STEP_Y(d)
            if (nx >= 0 && ny >= 0 && nx < w && ny < gridH) {
              val n = ny * w + nx
              if (closedIn(n) != id && (n == goal || (world.isWalkable(nx, ny) && !trapBlocks(nx, ny, botId) &&
                  !(standsAt(nx, ny) && (crowded || Math.max(Math.abs(nx - sx), Math.abs(ny - sy)) <= CROWD_CELLS))))) {
                val g = here + (if (d < 4) STEP else DIAGONAL)
                if (seenIn(n) != id || g < cost(n)) {
                  seenIn(n) = id
                  cost(n) = g
                  cameFrom(n) = c
                  val h = estimate(nx, ny, gx, gy)
                  push(key(g + h, h, n))
                  if (h < bestEstimate) { bestEstimate = h; best = n }
                }
              }
            }
            d += 1
          }
        }
      }
    }
    val end = if (found) goal else best
    route.goalX = gx
    route.goalY = gy
    route.reachesGoal = found
    route.searchedAt = tickNow
    route.next = 0
    route.from = start
    if (end == start) { route.length = 0; return false }
    var steps = 0
    var c = end
    while (c != start) { steps += 1; c = cameFrom(c) }
    if (route.cells.length < steps) route.cells = new Array[Int](Math.max(steps, route.cells.length * 2))
    c = end
    var i = steps - 1
    while (i >= 0) { route.cells(i) = c; c = cameFrom(c); i -= 1 }
    route.length = steps
    true
  }

  /**
   * Breadth first round (sx, sy), out to `steps` steps or `maxCells` cells, over the cells a bot
   * can step onto now (nobody standing there either). [[reached]] holds the cells it got to, nearest
   * first, (sx, sy) itself at 0; [[firstStepTo]] is the step toward any of them. Returns how many.
   */
  private[bots] def lookAround(botId: UUID, sx: Int, sy: Int, steps: Int, maxCells: Int): Int = {
    val world = instance.world
    val w = gridW
    val id = nextSearch()
    val start = sy * w + sx
    seenIn(start) = id
    cost(start) = 0
    cameFrom(start) = -1
    reached(0) = start
    var head = 0
    var tail = 1
    while (head < tail && tail < maxCells) {
      val c = reached(head)
      head += 1
      val depth = cost(c)
      if (depth < steps) {
        val cx = c % w
        val cy = c / w
        var d = 0
        while (d < 8 && tail < maxCells) {
          val nx = cx + STEP_X(d)
          val ny = cy + STEP_Y(d)
          if (nx >= 0 && ny >= 0 && nx < w && ny < gridH) {
            val n = ny * w + nx
            if (seenIn(n) != id && world.isWalkable(nx, ny) && !trapBlocks(nx, ny, botId) && !standsAt(nx, ny)) {
              seenIn(n) = id
              cost(n) = depth + 1
              cameFrom(n) = c
              reached(tail) = n
              tail += 1
            }
          }
          d += 1
        }
      }
    }
    tail
  }

  /** The open cell nearest (x, y): (x, y) itself if a bot could stand on it, else the nearest in the
    * first ring round it that has one (someone phased can stand in a wall or on water), as far as
    * [[OPEN_GROUND_CELLS]] out; -1 if none. */
  private[bots] def nearestOpen(x: Int, y: Int): Int = {
    val world = instance.world
    if (world.isWalkable(x, y)) return y * gridW + x
    var r = 1
    while (r <= OPEN_GROUND_CELLS) {
      var best = -1
      var bestSq = Int.MaxValue
      var dy = -r
      while (dy <= r) {
        // The ring's top and bottom rows whole, its sides only at their ends
        val stride = if (dy == -r || dy == r) 1 else 2 * r
        var dx = -r
        while (dx <= r) {
          if (world.isWalkable(x + dx, y + dy) && dx * dx + dy * dy < bestSq) {
            bestSq = dx * dx + dy * dy
            best = (y + dy) * gridW + x + dx
          }
          dx += stride
        }
        dy += 1
      }
      if (best >= 0) return best
      r += 1
    }
    -1
  }

  /** The cell before `cell` on the way the last look round found to it, one of [[reached]] (not 0). */
  private[bots] def cellBefore(cell: Int): Int = cameFrom(cell)

  /** The first step on the way the last look round found to `cell`, one of [[reached]] (not 0). */
  private[bots] def firstStepTo(cell: Int): Int = {
    var c = cell
    while (cameFrom(c) >= 0 && cameFrom(cameFrom(c)) >= 0) c = cameFrom(c)
    c
  }

  /** A cell's place in the open set: the lowest whole cost first, of those the nearest the goal.
    * Twenty bits each for the cell and the estimate hold any map the grid can address. */
  private def key(f: Int, h: Int, cell: Int): Long =
    Math.min(f, 0x7FFFFF).toLong << 40 | h.toLong << 20 | cell

  private def push(key: Long): Unit = {
    if (openSize == open.length) open = java.util.Arrays.copyOf(open, open.length * 2)
    var i = openSize
    openSize += 1
    while (i > 0) {
      val parent = (i - 1) >> 1
      if (open(parent) <= key) { open(i) = key; return }
      open(i) = open(parent)
      i = parent
    }
    open(0) = key
  }

  private def pop(): Long = {
    val top = open(0)
    openSize -= 1
    if (openSize > 0) {
      val last = open(openSize)
      var i = 0
      var done = false
      while (!done) {
        val l = 2 * i + 1
        if (l >= openSize) done = true
        else {
          val r = l + 1
          val child = if (r < openSize && open(r) < open(l)) r else l
          if (open(child) < last) { open(i) = open(child); i = child }
          else done = true
        }
      }
      open(i) = last
    }
    top
  }
}
