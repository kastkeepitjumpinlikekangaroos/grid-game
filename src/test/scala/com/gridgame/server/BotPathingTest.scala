package com.gridgame.server

import com.gridgame.server.bots.BotController
import com.gridgame.common.WorldRegistry
import com.gridgame.common.model._
import com.gridgame.common.world.WorldLoader
import org.junit.Assert._
import org.junit.Test

import java.util.UUID

/**
 * How bots find their way round walls: into no pocket they can't get out of, round anything in the
 * way however far off the target is, out from behind a wall to a clear shot, and out of a corner.
 * Bots used to walk straight at their target and search only when a step was blocked: the next
 * step undid the search's way round and they paced two cells for ever, and a target further off
 * than its 600 cells left them standing at the wall.
 *
 * Every match here is in a free-for-all's opening, so the bots walk and hold their fire, and the
 * bots are walked on a clock of the test's own (BotController.tick(now)).
 */
class BotPathingTest {
  /** A minute on from now: the clock a bot is added with is long past by then. */
  private def later: Long = System.currentTimeMillis() + 60000

  private val STEPS_X = Array(1, -1, 0, 0, 1, 1, -1, -1)
  private val STEPS_Y = Array(0, 0, 1, -1, 1, -1, 1, -1)

  /** The fewest steps a bot can take from `from` to every cell, onto any of the eight round it at
    * each step (-1 where it can't get to). */
  private def fewestSteps(w: WorldData, from: (Int, Int)): Array[Int] = {
    val dist = Array.fill(w.width * w.height)(-1)
    val queue = new java.util.ArrayDeque[Int]()
    dist(from._2 * w.width + from._1) = 0
    queue.add(from._2 * w.width + from._1)
    while (!queue.isEmpty) {
      val c = queue.poll()
      val x = c % w.width
      val y = c / w.width
      for (d <- 0 until 8) {
        val nx = x + STEPS_X(d)
        val ny = y + STEPS_Y(d)
        if (w.isWalkable(nx, ny) && dist(ny * w.width + nx) < 0) {
          dist(ny * w.width + nx) = dist(c) + 1
          queue.add(ny * w.width + nx)
        }
      }
    }
    dist
  }

  /** The fewest steps from `from` to beside `to`. */
  private def stepsBeside(w: WorldData, from: (Int, Int), to: (Int, Int)): Int =
    fewestSteps(w, from)(to._2 * w.width + to._1) - 1

  private def walls(w: WorldData, cells: Seq[(Int, Int)]): WorldData = {
    cells.foreach { case (x, y) => w.setTile(x, y, Tile.Wall) }
    w
  }

  /** Ticks until a bot of `character` at `from` stands beside someone standing still at `to`, or
    * -1 if it doesn't within `maxTicks`. */
  private def ticksToReach(world: WorldData, character: CharacterId, from: (Int, Int), to: (Int, Int),
                           maxTicks: Int, botId: UUID = new UUID(0L, 901)): Int = {
    val m = new TestMatch(world, opening = true)
    val bot = m.join(character, from._1, from._2, id = botId)
    m.join(CharacterId.Spaceman, to._1, to._2)
    val bots = new BotController(m.instance)
    try {
      bots.addBotId(bot.getId)
      val start = later
      var k = 0
      while (k < maxTicks) {
        val p = bot.getPosition
        if (Math.max(Math.abs(p.getX - to._1), Math.abs(p.getY - to._2)) <= 1) return k
        bots.tick(start + 100L * k)
        k += 1
        if (k % 50 == 0) m.clearSent()
      }
      -1
    } finally bots.stop()
  }

  /** As many ticks as a melee bot takes for `steps` steps (106ms a step on a 100ms tick), and a
    * tenth more for the way round not being the very shortest. */
  private def meleeTicksFor(steps: Int): Int = (steps * 1.06 * 1.1).toInt + 3

  @Test def aMeleeBotGetsOutOfAPocketInItsWay(): Unit = {
    // A cup open toward the bot, the target behind its back wall. Walking straight in, the bot used
    // to reach the back wall and pace two cells there: the search's step out, then a straight step
    // back in
    val w = walls(WorldData.createEmpty(50, 40),
      (14 to 26).map(y => (20, y)) ++ (13 to 20).flatMap(x => Seq((x, 14), (x, 26))))
    val steps = stepsBeside(w, (8, 20), (30, 20))
    val ticks = ticksToReach(w, CharacterId.Barbarian, (8, 20), (30, 20), 300)
    assertTrue(s"reached in $ticks ticks, $steps steps away", ticks >= 0 && ticks <= meleeTicksFor(steps))
  }

  @Test def aBotFindsItsWayRoundAWallHoweverFarOffItsTargetIs(): Unit = {
    // A wall across most of the map, the target 60 cells off beyond it: further than the 600 cells
    // the old search looked at, which left the bot standing at the wall
    val w = walls(WorldData.createEmpty(120, 40), (0 to 33).map(y => (60, y)))
    val steps = stepsBeside(w, (30, 5), (90, 5))
    val ticks = ticksToReach(w, CharacterId.Barbarian, (30, 5), (90, 5), 600)
    assertTrue(s"reached in $ticks ticks, $steps steps away", ticks >= 0 && ticks <= meleeTicksFor(steps))
  }

  @Test def aRangedBotStepsOutFromBehindAWallToAClearShot(): Unit = {
    // Inside a cup whose back wall stands between it and the target, at its range: it used to
    // circle in there, its every shot into the wall
    val w = walls(WorldData.createEmpty(60, 60),
      (25 to 35).map(y => (26, y)) ++ (18 to 26).flatMap(x => Seq((x, 25), (x, 35))))
    val m = new TestMatch(w, opening = true)
    val bot = m.join(CharacterId.Soldier, 24, 30, id = new UUID(0L, 902))
    val target = m.join(CharacterId.Spaceman, 34, 30)
    val bots = new BotController(m.instance)
    try {
      bots.addBotId(bot.getId)
      val bullet = CharacterDef.get(CharacterId.Soldier.id).primaryProjectileType
      val reach = ProjectileDef.get(bullet).maxRange
      def shot: Boolean = {
        val p = bot.getPosition
        val dx = p.getX - 34
        val dy = p.getY - 30
        Math.sqrt(dx * dx + dy * dy) <= reach && clearLine(w, p.getX, p.getY, 34, 30)
      }
      val start = later
      var k = 0
      while (k < 80 && !shot) { bots.tick(start + 100L * k); k += 1 }
      assertTrue(s"a clear shot from ${m.at(bot)} after $k ticks", shot)
      // And it keeps one, circling the target where its shots get through
      var kept = 0
      for (j <- 0 until 50) { bots.tick(start + 100L * (k + j)); if (shot) kept += 1 }
      assertTrue(s"kept its shot $kept ticks of 50", kept >= 45)
      assertFalse(target.isDead)
    } finally bots.stop()
  }

  /** Nothing that stops a shot on the line from one cell to another, measured as a shot flies. */
  private def clearLine(w: WorldData, fx: Int, fy: Int, tx: Int, ty: Int): Boolean = {
    val dx = (tx - fx).toDouble
    val dy = (ty - fy).toDouble
    val steps = Math.ceil(Math.sqrt(dx * dx + dy * dy) * 2).toInt
    (1 until steps).forall { i =>
      val t = i.toDouble / steps
      !w.stopsProjectile(Math.floor(fx + dx * t + 0.5).toInt, Math.floor(fy + dy * t + 0.5).toInt)
    }
  }

  @Test def aRangedBotBacksOutOfACornerAlongTheWall(): Unit = {
    // In the map's corner with someone coming in on it: there was no straight way back, the old
    // search looked for a way to a cell off the map, and the bot stood there to be hit
    val m = new TestMatch(WorldData.createEmpty(30, 30), opening = true)
    val bot = m.join(CharacterId.Soldier, 0, 0, id = new UUID(0L, 903))
    m.join(CharacterId.Barbarian, 2, 2)
    val bots = new BotController(m.instance)
    try {
      bots.addBotId(bot.getId)
      val start = later
      for (k <- 0 until 30) bots.tick(start + 100L * k)
      val (x, y) = m.at(bot)
      val dist = Math.sqrt((x - 2) * (x - 2) + (y - 2) * (y - 2))
      // Out to half its range or more, where it backs off no further
      assertTrue(s"at ($x, $y), $dist from the Barbarian", dist >= 5.0)
    } finally bots.stop()
  }

  @Test def twoBotsNeverStepOntoOneCell(): Unit = {
    // After each other with a cell between them, both stepped onto it on the same tick: who stood
    // where was noted once a tick, so the second didn't know the first had just stepped there
    val m = new TestMatch(WorldData.createEmpty(30, 30), opening = true)
    val a = m.join(CharacterId.Barbarian, 10, 10, id = new UUID(0L, 904))
    val b = m.join(CharacterId.Barbarian, 12, 10, id = new UUID(0L, 905))
    val bots = new BotController(m.instance)
    try {
      bots.addBotId(a.getId)
      bots.addBotId(b.getId)
      val start = later
      for (k <- 0 until 12) {
        bots.tick(start + 100L * k)
        assertNotEquals(s"tick $k", m.at(a), m.at(b))
      }
    } finally bots.stop()
  }

  @Test def onEveryMapABotReachesATargetStandingAnywhere(): Unit = {
    // Bot and target dropped on open ground across every map, 8 to 70 steps apart. Before, a fifth
    // to almost half of these never got there
    val rng = new scala.util.Random(20261009)
    var botNo = 1000L
    for (i <- 0 until WorldRegistry.size) {
      val file = WorldRegistry.getFilename(i)
      val w = WorldLoader.load("worlds/" + file)
      val open = (for (y <- 0 until w.height; x <- 0 until w.width if w.isWalkable(x, y)) yield (x, y)).toArray
      for (_ <- 0 until 25) {
        val from = open(rng.nextInt(open.length))
        val dist = fewestSteps(w, from)
        val far = open.filter { case (x, y) => dist(y * w.width + x) >= 9 && dist(y * w.width + x) <= 71 }
        val to = far(rng.nextInt(far.length))
        val steps = dist(to._2 * w.width + to._1) - 1
        botNo += 1
        val ticks = ticksToReach(w, CharacterId.Barbarian, from, to, meleeTicksFor(steps) * 2, new UUID(0L, botNo))
        assertTrue(s"$file: $from to $to, $steps steps, took $ticks ticks", ticks >= 0 && ticks <= meleeTicksFor(steps))
      }
    }
  }
}
