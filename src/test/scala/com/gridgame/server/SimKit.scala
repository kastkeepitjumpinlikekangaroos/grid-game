package com.gridgame.server

import com.gridgame.common.Constants
import com.gridgame.common.GameClock
import com.gridgame.common.model._
import com.gridgame.server.bots.BotController
import com.gridgame.server.game.GameInstance
import com.gridgame.server.game.MatchOutcome
import com.gridgame.server.game.MatchScoring

import java.util.UUID
import scala.collection.mutable
import scala.jdk.CollectionConverters._

/**
 * Time of the test's own. `Sim(seed) { clock => ... }` puts the match's rules on a clock that stands
 * still until the test moves it (GameClock.Manual), and seeds everything a match leaves to chance
 * (scala.util.Random: where items and spawns fall, what bots decide), so what is played inside is
 * played the same every time, however busy the machine. Both go back to the system's when the body
 * is done, whatever it threw: the clock is the whole process's.
 */
object Sim {
  def apply[T](seed: Long = 1L)(body: GameClock.Manual => T): T = {
    val clock = new GameClock.Manual()
    GameClock.use(clock)
    scala.util.Random.setSeed(seed)
    try body(clock) finally GameClock.useSystem()
  }
}

/**
 * The clocks of a match that was begun but never started (GameInstance.begin), run on a clock of
 * the test's own. What it runs is the match's own list of what `GameInstance.start` schedules
 * (GameInstance.clocked: the projectile tick every 30ms, the player tick every 200ms, a batch of
 * items as it starts and every minute after, the time told every ten seconds), so a rule about what
 * a match does when is held here to what a started match does; and beside it the bots' tick (every
 * 100ms, a BotController's own once started) and their steps between (every 10ms), a respawn its
 * delay after each death, and the end when its time is up. [[advance]] plays the match on: every one of them that falls due, each at its own
 * millisecond, in the order they fall.
 *
 * Two that fall due on the same millisecond run in a fixed order (the projectile tick, the player
 * tick, the bots, the items, the time), or with `shuffleTies` in an order picked by it: on a real
 * server they are different threads', and either may come first.
 *
 * @param items    whether items are spawned (a batch at the start, then every minute)
 * @param respawns whether the dead are brought back when their wait is over
 */
final class MatchClockwork(val instance: GameInstance, val clock: GameClock.Manual, items: Boolean = true,
                           respawns: Boolean = true, shuffleTies: scala.util.Random = null) {
  private val startedAt = clock.millis()

  /** How long the match has been played, in ms. */
  def at: Long = clock.millis() - startedAt

  /** One of the match's clocks: what it is called in `ran`, when it next falls due, how often. */
  private final class Task(val name: String, var due: Long, val every: Long, val body: () => Unit)

  // What `ran` and `afterEach` call each of the match's own clocks
  private val named = Map("tickProjectiles" -> "projectiles", "tickPlayers" -> "players", "spawnItem" -> "items", "syncTimer" -> "time")

  private val tasks: Array[Task] = {
    val ofTheMatch = instance.clocked.filter(c => items || c.name != "spawnItem")
      .map(c => new Task(named(c.name), startedAt + c.firstAfterMs, c.everyMs, c.run))
    val bots = new Task("bots", startedAt + BotController.TickIntervalMs, BotController.TickIntervalMs, () =>
      instance.botController match {
        case bc: BotController => bc.tick(clock.millis())
        case _ =>
      })
    // and the steps that fall due between their ticks, as a started match's bots take them
    val botSteps = new Task("botSteps", startedAt + BotController.StepIntervalMs, BotController.StepIntervalMs, () =>
      instance.botController match {
        case bc: BotController => bc.stepTick(clock.millis())
        case _ =>
      })
    val (ticks, rest) = ofTheMatch.partition(t => t.name == "projectiles" || t.name == "players")
    (ticks ++ Seq(bots, botSteps) ++ rest).toArray
  }
  private val Respawn = tasks.length
  private val End = tasks.length + 1
  private val endsAt = startedAt + instance.durationMs
  private val respawnAt = mutable.LinkedHashMap[UUID, Long]()

  /** How many times each has run: "projectiles", "players", "bots", "items", "time", "respawn". */
  val ran: mutable.Map[String, Int] = mutable.Map[String, Int]().withDefaultValue(0)

  /** Called after everything the clockwork runs, with what it was (as in `ran`): where a test
    * looks the match over. */
  var afterEach: String => Unit = _ => ()

  /** What the end of the match does: by default what happens where a match is played
    * (MatchScoring.finish: stopped, scored, its players told). */
  var onTimeUp: () => Unit = () => outcome = Some(MatchScoring.finish(instance, instance.gameId))

  /** How the match ended, once its time was up. */
  var outcome: Option[MatchOutcome] = None

  private var over = false
  def isOver: Boolean = over

  /** The dead are owed a respawn from the moment they are seen dead; the living none. */
  private def noteDeaths(): Unit = if (respawns) {
    val now = clock.millis()
    instance.registry.forEachPlayer { p =>
      if (p.isDead) { if (!respawnAt.contains(p.getId)) respawnAt(p.getId) = now + instance.respawnDelayMs }
      else respawnAt.remove(p.getId)
    }
    respawnAt.keys.filterNot(instance.registry.contains).toList.foreach(respawnAt.remove)
  }

  private def run(what: Int, respawning: UUID): Unit = {
    val name =
      if (what == Respawn) {
        respawnAt.remove(respawning)
        instance.respawn(respawning)
        "respawn"
      } else if (what == End) {
        over = true
        onTimeUp()
        "end"
      } else {
        tasks(what).body()
        tasks(what).name
      }
    ran(name) += 1
    if (!over) noteDeaths()
    afterEach(name)
  }

  /** Play the match on for `ms`: everything that falls due in that time, each at its own moment. */
  def advance(ms: Long): Unit = {
    val until = clock.millis() + ms
    noteDeaths()
    var more = !over
    while (more) {
      // The earliest of everything due; among those due at once, the first in the fixed order, or
      // one picked by `shuffleTies`
      var at = Math.min(endsAt, if (respawnAt.isEmpty) Long.MaxValue else respawnAt.values.min)
      var k = 0
      while (k < tasks.length) { if (tasks(k).due < at) at = tasks(k).due; k += 1 }
      if (at > until) more = false
      else {
        val ties = mutable.ArrayBuffer[(Int, UUID)]()
        // The end comes first of all it ties with: nothing more of the match is played once its time is up
        if (endsAt == at) ties += ((End, null))
        else {
          k = 0
          while (k < tasks.length) { if (tasks(k).due == at) ties += ((k, null)); k += 1 }
          for ((id, t) <- respawnAt if t == at) ties += ((Respawn, id))
        }
        val (what, who) = if (shuffleTies != null && ties.size > 1) ties(shuffleTies.nextInt(ties.size)) else ties.head
        if (at > clock.millis()) clock.advance(at - clock.millis())
        if (what < tasks.length) tasks(what).due += tasks(what).every
        run(what, who)
        if (over) more = false
      }
    }
    if (until > clock.millis()) clock.advance(until - clock.millis())
  }

  /** Play the match on until it has been played for `atMs` in all. */
  def advanceTo(atMs: Long): Unit = if (atMs > at) advance(atMs - at)
}

/**
 * A [[TestMatch]] on a clock of the test's own (inside `Sim { clock => ... }`): nothing happens in
 * it but what the test sends it, and what falls due as the test lets time pass ([[advance]]) — its
 * ticks, its bots, its respawns and its end, as a started match has them ([[MatchClockwork]]). A
 * rule that turns on the clock can be looked at on the millisecond it turns, and minutes of a
 * match take as long as their ticks take to run.
 *
 * No items fall unless asked for (`items`), so a test's players carry what it gives them.
 */
class SimMatch(val clock: GameClock.Manual, world: WorldData = WorldData.createEmpty(60, 60), gameMode: Byte = 0,
               opening: Boolean = false, practice: Boolean = false, minutes: Int = 5, items: Boolean = false,
               respawns: Boolean = true, shuffleTies: scala.util.Random = null)
    extends TestMatch(world, gameMode, opening, practice, minutes) {
  val clockwork = new MatchClockwork(instance, clock, items, respawns, shuffleTies)

  /** How long the match has been played, in ms. */
  def at: Long = clockwork.at

  /** Play the match on for `ms`. */
  def advance(ms: Long): Unit = clockwork.advance(ms)

  def advanceTo(atMs: Long): Unit = clockwork.advanceTo(atMs)

  // A step's gap is that much of the match played, not a wait
  override protected def pause(ms: Long): Unit = advance(ms)

  private var botCount = 0

  /** The match's bots, once it has any ([[addBot]]). */
  def bots: BotController = instance.botController.asInstanceOf[BotController]

  /** A bot at (x, y), played by the match's BotController on the clockwork's bot tick. It has no
    * connection, as a real match's bots have none. */
  def addBot(character: CharacterId, x: Int, y: Int, team: Byte = 0): Player = {
    if (instance.botController == null) instance.botController = new BotController(instance, practice)
    botCount += 1
    val bot = join(character, x, y, team, id = new UUID(0L, 7000L + botCount), connected = false)
    bots.addBotId(bot.getId)
    bot
  }
}

/** One of the projectiles the roster throws, and someone who throws it: thrown down as a slam, or
  * sent flying. */
final case class RosterAttack(character: CharacterDef, projectileType: Byte, slam: Boolean) {
  def defn: ProjectileDef = ProjectileDef.get(projectileType)
  def name: String = s"${defn.name} (${character.displayName})"
}

object RosterAttack {
  /** Every projectile type the roster throws, once, with the first character who throws it. */
  val all: Seq[RosterAttack] = {
    val seen = mutable.LinkedHashMap[Byte, RosterAttack]()
    for (c <- CharacterDef.all) {
      seen.getOrElseUpdate(c.primaryProjectileType, RosterAttack(c, c.primaryProjectileType, slam = false))
      for (a <- Seq(c.qAbility, c.eAbility)) a.castBehavior match {
        case StandardProjectile | FanProjectile(_, _) => seen.getOrElseUpdate(a.projectileType, RosterAttack(c, a.projectileType, slam = false))
        case GroundSlam(_) => seen.getOrElseUpdate(a.projectileType, RosterAttack(c, a.projectileType, slam = true))
        case _ =>
      }
    }
    seen.values.toSeq
  }
}

/** What a match must keep to at every moment, whoever is playing it and whatever they send: looked
  * over by the suites that play whole matches on a clock of their own. */
object MatchRules {
  /** The first rule the match is breaking now, or null. */
  def broken(gi: GameInstance, now: Long = GameClock.now()): String = {
    val world = gi.world
    val everyone = gi.registry.getAll.asScala.toSeq
    for (p <- everyone) {
      val pos = p.getPosition
      val who = s"${p.getName} (${CharacterDef.get(p.getCharacterId).displayName})"
      if (p.getHealth < 0 || p.getHealth > p.getMaxHealth) return s"$who has ${p.getHealth} of ${p.getMaxHealth} health"
      if (pos.getX < 0 || pos.getY < 0 || pos.getX >= world.width || pos.getY >= world.height) return s"$who is off the map at $pos"
      // A stun is a freeze drawn differently, and a launch a stun in the air: neither outlasts what holds it
      if (p.isStunned && !p.isFrozen) return s"$who is stunned but not held"
      if (p.isAirborne && !p.isStunned) return s"$who is in the air but not stunned"
      val kit = CharacterDef.get(p.getCharacterId)
      val casts = Seq(kit.qAbility.castBehavior, kit.eAbility.castBehavior)
      if (p.hasBarrier) {
        val longest = casts.collect { case BarrierCast(ms) => ms }
        if (longest.isEmpty) return s"$who holds a barrier up, and has none in the kit"
        if (p.getBarrierUntil - now > longest.max) return s"$who's barrier has ${p.getBarrierUntil - now}ms left, of ${longest.max}"
        if (p.isDead) return s"$who is dead with a barrier up"
      }
      if (p.isPhased) {
        val longest = casts.collect { case PhaseShiftBuff(ms) => ms; case DashBuff(_, ms, _) => ms }
        if (longest.isEmpty) return s"$who is phased, and has neither a phase nor a dash in the kit"
        if (p.getPhasedUntil - now > longest.max) return s"$who's phase has ${p.getPhasedUntil - now}ms left, of ${longest.max}"
      }
      if (p.getShieldUntil - now > Constants.SHIELD_DURATION_MS) return s"$who's shield has ${p.getShieldUntil - now}ms left"
      if (p.getGemBoostUntil - now > Constants.GEM_DURATION_MS) return s"$who's gem has ${p.getGemBoostUntil - now}ms left"
      val inv = gi.itemManager.getInventory(p.getId).toSeq
      if (inv.size > Constants.MAX_INVENTORY_SIZE) return s"$who carries ${inv.size} items"
    }

    // Items: on the ground or in one inventory, never both, never two
    val ground = gi.itemManager.getAll
    if (ground.map(_.id).distinct.size != ground.size) return "an item is on the ground twice"
    val carried = mutable.Map[Int, String]()
    for (p <- everyone; item <- gi.itemManager.getInventory(p.getId)) {
      if (ground.exists(_.id == item.id)) return s"item ${item.id} is in ${p.getName}'s inventory and on the ground"
      if (carried.contains(item.id)) return s"item ${item.id} is carried by ${p.getName} and ${carried(item.id)}"
      carried(item.id) = p.getName
    }
    for (item <- ground if !world.isTileWalkable(item.getCellX, item.getCellY) && !gi.modifiedTiles.containsKey((item.getCellX, item.getCellY)))
      return s"item ${item.id} lies on ${world.getTile(item.getCellX, item.getCellY)} at (${item.getCellX}, ${item.getCellY})"

    // Projectiles: thirty a player, each somewhere on or beside the map, none flown past its range
    val shots = gi.projectileManager.getAll
    for ((owner, theirs) <- shots.groupBy(_.ownerId) if theirs.size > 30) return s"$owner has ${theirs.size} projectiles in flight"
    for (shot <- shots) {
      if (!java.lang.Float.isFinite(shot.getX) || !java.lang.Float.isFinite(shot.getY)) return s"a projectile at (${shot.getX}, ${shot.getY})"
      val d = ProjectileDef.get(shot.projectileType)
      val reach = d.effectiveMaxRange(shot.chargeLevel)
      // Flown no further than its range and the tick's worth it was in when it got there (a gem doubles it)
      if (shot.getDistanceTraveled > reach + 2 * d.effectiveSpeed(shot.chargeLevel) + 0.01)
        return s"projectile ${shot.id} (${d.name}) has flown ${shot.getDistanceTraveled} of $reach"
    }

    // Traps: one to a cell, no more than each player may keep, none past its time
    val traps = gi.trapManager.getAll
    for (t <- traps) {
      if (gi.trapManager.trapAt(t.x, t.y) ne t) return s"trap ${t.id} at (${t.x}, ${t.y}) shares its cell"
      if (now - t.expiresAt > 2 * Constants.PROJECTILE_SPEED_MS) return s"trap ${t.id} is ${now - t.expiresAt}ms past its time"
    }
    for ((owner, mine) <- traps.groupBy(_.ownerId) if mine.size > mine.map(t => TrapDef.get(t.trapType).maxActive).max)
      return s"$owner has ${mine.size} traps down"
    null
  }
}
