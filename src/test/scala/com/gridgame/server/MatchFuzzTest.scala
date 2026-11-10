package com.gridgame.server

import com.gridgame.common.Constants
import com.gridgame.common.GameClock
import com.gridgame.common.model._
import com.gridgame.common.protocol._
import org.junit.Assert._
import org.junit.Test

import java.util.UUID
import scala.collection.mutable
import scala.jdk.CollectionConverters._

/**
 * A match driven by thousands of random requests from its players — steps, jumps, shots from every
 * attack, traps, items, R, rejoins, leaving — some as a client sends them and some as no honest one
 * would, while the match goes on: its ticks, its bots and its respawns fall due as time passes, on
 * a clock of the run's own (SimKit), a few milliseconds or a few seconds between one request and
 * the next. The rules every match must keep are checked after every request and every stretch of
 * time:
 *
 *  - nothing it is sent, and none of its ticks, throws (nor do its bots, whose tick swallows it)
 *  - what every match keeps (MatchRules): health in bounds, everyone on the map, an item on the
 *    ground or in one inventory and ten at most, thirty shots in flight a player and none past its
 *    range, a trap to a cell, no shield, gem, phase or barrier longer than it lasts
 *  - nobody is moved onto a cell they can't stand on unless dashing (a dash goes over walls; a
 *    phase shift doesn't, and walked through them until 2026-10-14), or onto a fence too new for
 *    their client to have heard of
 *  - the dead, the frozen and the rooted are not moved by their own client
 *  - every shot the server takes leaves from within reach of its shooter, and none outlives the
 *    flight its range allows
 *  - every death is scored, once, and every kill is somebody's death
 *  - while a match's opening lasts nobody is hurt, whatever anyone sends: in a free-for-all
 *    nothing is fired, thrown, raised or phased, and behind the Teams wall everyone stays in their
 *    own half
 *
 * Every run is seeded and plays the same every time: a failure says which seed and step, with the
 * actions that led to it, and that seed plays it again.
 *
 * It was played on the system's clock, with cooldowns and effects cleared by hand to stand for
 * time passing: under load, when its steps took longer, a dash's phase ran out between two of them
 * and the step after was taken by the server (which gives a dash the network's tolerance) and held
 * against it here.
 */
class MatchFuzzTest {
  import MatchFuzzTest._

  @Test def aFreeForAllKeepsItsRulesWhateverItsPlayersSend(): Unit =
    for (seed <- 1 to 10) play(seed, gameMode = 0, bots = seed % 2 == 0)

  @Test def aTeamsMatchKeepsItsRulesWhateverItsPlayersSend(): Unit =
    for (seed <- 101 to 106) play(seed, gameMode = 1, bots = seed % 2 == 1)

  @Test def anOpeningKeepsItsRulesWhateverItsPlayersSend(): Unit = {
    // The Teams wall up, and a free-for-all's ceasefire: two runs inside the thirty seconds, and
    // one that plays on through their end
    for (mode <- Seq(1, 0); (seed, pace) <- Seq((201, 0.1), (202, 0.1), (203, 1.0)))
      play(seed + mode * 100, gameMode = mode.toByte, bots = true, opening = true, steps = 2000, pace = pace)
  }

  @Test def practiceKeepsItsRulesWhateverItsPlayersSend(): Unit =
    for (seed <- 401 to 403) play(seed, gameMode = 0, bots = true, practice = true, steps = 2000)

  @Test def aSeedPlaysTheSameRunEveryTime(): Unit = {
    val first = play(7, gameMode = 0, bots = true, steps = 1500)
    val again = play(7, gameMode = 0, bots = true, steps = 1500)
    assertEquals(first, again)
    assertNotEquals("and another seed another", first, play(8, gameMode = 0, bots = true, steps = 1500))
  }
}

object MatchFuzzTest {
  import ServerTestKit.{captureStderr, quietly}

  /** A cell on ground with walls, fences and water about it, and a wall round the edge. */
  def clutteredWorld(rnd: scala.util.Random, size: Int = 44): WorldData = {
    val w = WorldData.createEmpty(size, size)
    for (x <- 0 until size; y <- 0 until size) {
      val edge = x == 0 || y == 0 || x == size - 1 || y == size - 1
      val r = rnd.nextDouble()
      if (edge || r < 0.07) w.setTile(x, y, Tile.Wall)
      else if (r < 0.10) w.setTile(x, y, Tile.Fence)
      else if (r < 0.13) w.setTile(x, y, Tile.Water)
    }
    w
  }

  /** One run on a clock of its own: what the match came to, as text (who stands where, with what). */
  def play(seed: Long, gameMode: Byte, bots: Boolean, opening: Boolean = false, practice: Boolean = false,
           steps: Int = 2500, pace: Double = 1.0): String =
    Sim(seed) { clock => new Run(clock, seed, gameMode, opening, bots, practice, pace).play(steps) }

  /**
   * One match, played `steps` actions long. Its players are humans (requests through the match's
   * ClientHandler, serialized and read back as the wire carries them) and, with `bots`, two bots on
   * the match's own bot tick. `pace` stretches or shrinks the time between requests.
   */
  final class Run(clock: GameClock.Manual, seed: Long, gameMode: Byte, opening: Boolean, bots: Boolean,
                  practice: Boolean, pace: Double) {
    private val rnd = new scala.util.Random(seed)
    private val world = clutteredWorld(rnd)
    // Longer than any run: a match's end is MatchSimTest's
    private val m = new SimMatch(clock, world, gameMode, opening, practice, minutes = 60)
    private val instance = m.instance
    private val trace = mutable.Queue[String]()
    private var step = 0
    // What the run got up to, so a run that only ever had its requests refused shows as one
    private val tally = mutable.Map[String, Int]().withDefaultValue(0)
    private def count(what: String, n: Int = 1): Unit = tally(what) += n

    private def note(s: String): Unit = {
      trace.enqueue(s"$step (${m.at}ms): $s")
      if (trace.size > 40) trace.dequeue()
    }

    private def fail(why: String): Nothing =
      throw new AssertionError(s"seed $seed (mode $gameMode, opening $opening, bots $bots, practice $practice, pace $pace), " +
        s"step $step, ${m.at}ms in: $why\nThe last actions:\n  ${trace.mkString("\n  ")}")

    private def check(ok: Boolean, why: => String): Unit = if (!ok) fail(why)

    // --- Who is playing ---

    private def openCell(side: Int = 0): (Int, Int) = {
      var tries = 0
      while (tries < 10000) {
        val (x, y) = (1 + rnd.nextInt(world.width - 2), 1 + rnd.nextInt(world.height - 2))
        if (world.isWalkable(x, y) && (side == 0 || TeamDivider.sideOf(world, x, y) == side)) return (x, y)
        tries += 1
      }
      fail("no open ground")
    }

    private val characters = CharacterDef.all
    private val humans = mutable.ArrayBuffer[Player]()
    private val seqs = mutable.Map[UUID, Int]()

    for (i <- 0 until 6) {
      val team: Byte = if (gameMode == 1) (i % 2 + 1).toByte else 0
      val (x, y) = openCell(if (gameMode == 1) TeamDivider.sideOfTeam(team) else 0)
      humans += m.join(characters(rnd.nextInt(characters.size)).id, x, y, team, id = new UUID(seed, 1L + i))
    }

    if (bots) for (i <- 0 until 2) {
      val team: Byte = if (gameMode == 1) (i % 2 + 1).toByte else 0
      val (x, y) = openCell(if (gameMode == 1) TeamDivider.sideOfTeam(team) else 0)
      m.addBot(characters(rnd.nextInt(characters.size)).id, x, y, team)
    }

    private def everyone: Seq[Player] = instance.registry.getAll.asScala.toSeq.sortBy(p => (p.getId.getMostSignificantBits, p.getId.getLeastSignificantBits))

    private def nextSeq(p: Player): Int = {
      val n = seqs.getOrElse(p.getId, 0) + 1
      seqs(p.getId) = n
      n
    }

    /** What the server makes of a packet off the wire: its bytes, read back. */
    private def wire(p: Packet): Packet = PacketSerializer.deserialize(p.serialize())

    private def send(p: Player, packet: Packet): Unit =
      instance.handler.processPacket(wire(packet), null, p.getUdpAddress)

    // --- What a player's client sends ---

    private def near(c: Int, spread: Int, limit: Int): Int = Math.max(0, Math.min(limit - 1, c + rnd.nextInt(2 * spread + 1) - spread))

    private def update(p: Player, x: Int, y: Int): Unit = {
      val honest = rnd.nextDouble() < 0.9
      val moves = if (honest) p.getServerMoves else p.getServerMoves - 1 - rnd.nextInt(2)
      val seq = if (rnd.nextDouble() < 0.93) nextSeq(p) else Math.max(1, seqs.getOrElse(p.getId, 1) - rnd.nextInt(5))
      val flags = (if (rnd.nextDouble() < 0.05) 0x08 else 0) | (if (rnd.nextDouble() < 0.02) rnd.nextInt(256) else 0)
      val flags2 = if (rnd.nextDouble() < 0.06) 0x04 else 0
      val aim = rnd.nextDouble() * 2 * Math.PI
      note(f"${p.getName} update to ($x,$y) moves=$moves seq=$seq flags=$flags%x flags2=$flags2%x")
      send(p, new PlayerUpdatePacket(seq, p.getId, Packet.getCurrentTimestamp, new Position(x, y), p.getColorRGB,
        rnd.nextInt(300), rnd.nextInt(101), flags, p.getCharacterId, p.getTeamId, moves, flags2,
        PlayerUpdatePacket.encodeAimAngle(aim), rnd.nextInt(101)))
    }

    private def walk(p: Player): Unit = {
      val pos = p.getPosition
      update(p, near(pos.getX, 1, world.width), near(pos.getY, 1, world.height))
    }

    private def jump(p: Player): Unit = {
      val pos = p.getPosition
      val reach = if (rnd.nextBoolean()) 12 else world.width
      update(p, near(pos.getX, reach, world.width), near(pos.getY, reach, world.height))
    }

    private def attackType(c: CharacterDef, slot: Int): Byte = slot match {
      case AttackSlot.Q => c.qAbility.projectileType
      case AttackSlot.E => c.eAbility.projectileType
      case _ => c.primaryProjectileType
    }

    private def headings(c: CharacterDef, slot: Int): Seq[(Float, Float)] = {
      val a = rnd.nextDouble() * 2 * Math.PI
      val one = (Math.cos(a).toFloat, Math.sin(a).toFloat)
      if (slot == AttackSlot.BURST) AttackSlot.BurstDirections
      else {
        val ability = if (slot == AttackSlot.Q) c.qAbility else if (slot == AttackSlot.E) c.eAbility else null
        val count = if (ability == null) (if (rnd.nextDouble() < 0.2) 3 else 1) else ability.castBehavior match {
          case FanProjectile(n, _) => n
          case _ => 1
        }
        Seq.fill(count) {
          val b = a + rnd.nextGaussian() * 0.3
          (Math.cos(b).toFloat, Math.sin(b).toFloat)
        } match {
          case hs if rnd.nextDouble() < 0.03 => (0f, 0f) +: hs.tail // no heading at all
          case hs if rnd.nextDouble() < 0.03 => (3f, -2f) +: hs.tail // not a unit
          case hs => if (hs.isEmpty) Seq(one) else hs
        }
      }
    }

    private def fire(p: Player): Unit = {
      val c = CharacterDef.get(p.getCharacterId)
      val slot = rnd.nextDouble() match {
        case r if r < 0.5 => AttackSlot.PRIMARY
        case r if r < 0.7 => AttackSlot.Q
        case r if r < 0.9 => AttackSlot.E
        case r if r < 0.98 => AttackSlot.BURST
        case _ => 4 + rnd.nextInt(250) // no attack at all
      }
      val pType = if (rnd.nextDouble() < 0.9) attackType(c, slot) else (rnd.nextInt(256) - 128).toByte
      val pos = p.getPosition
      val (fx, fy) = rnd.nextDouble() match {
        case r if r < 0.85 => (pos.getX.toFloat, pos.getY.toFloat)
        case r if r < 0.92 => (pos.getX + rnd.nextInt(7) - 3f, pos.getY + rnd.nextInt(7) - 3f)
        case r if r < 0.96 => (rnd.nextFloat() * world.width, rnd.nextFloat() * world.height)
        case _ => Seq((Float.NaN, Float.NaN), (Float.PositiveInfinity, 1f), (-1e30f, 5f))(rnd.nextInt(3))
      }
      val charge = if (rnd.nextDouble() < 0.9) rnd.nextInt(101) else rnd.nextInt(256) - 128
      val before = instance.projectileManager.getAll.map(_.id).toSet
      val hs = headings(c, slot)
      note(s"${p.getName} fires slot $slot type $pType from ($fx,$fy) charge $charge, ${hs.size} heading(s)")
      for ((dx, dy) <- hs)
        send(p, ProjectilePacket.spawnRequest(nextSeq(p), p.getId, fx, fy, p.getColorRGB, dx, dy, charge.toByte, pType, slot))
      for (shot <- instance.projectileManager.getAll if !before.contains(shot.id)) {
        count("shots taken")
        // The server put it a cell out from where it was fired, which is within three cells of
        // where it has the shooter and what a bad network can leave it behind by (a slam is put
        // down on its caster)
        val ox = shot.getX - shot.dx
        val oy = shot.getY - shot.dy
        val reach = 3.01f + Movement.lagCells(Constants.NETWORK_TOLERANCE_MS, c.moveSpeed)
        check(!ox.isNaN && !oy.isNaN && Math.abs(ox - pos.getX) <= reach && Math.abs(oy - pos.getY) <= reach,
          s"a shot fired from ($ox, $oy) by ${p.getName} at (${pos.getX}, ${pos.getY})")
      }
    }

    private def throwTrap(p: Player): Unit = {
      val c = CharacterDef.get(p.getCharacterId)
      val slot = if (rnd.nextBoolean()) AttackSlot.Q else AttackSlot.E
      val ability = if (slot == AttackSlot.Q) c.qAbility else c.eAbility
      val trapType: Byte = ability.castBehavior match {
        case TrapCast(t, _) if rnd.nextDouble() < 0.9 => t
        case _ => rnd.nextInt(7).toByte
      }
      val pos = p.getPosition
      val (x, y) = (pos.getX + rnd.nextInt(17) - 8, pos.getY + rnd.nextInt(17) - 8)
      note(s"${p.getName} throws trap $trapType from slot $slot to ($x,$y)")
      val before = instance.trapManager.getAll.map(_.id).toSet
      send(p, new TrapPacket(nextSeq(p), p.getId, x, y, 0, TrapAction.PLACE, trapType, p.getTeamId, slot, null))
      if (instance.trapManager.getAll.exists(t => !before.contains(t.id))) count("traps thrown")
    }

    private var handedOut = 5000000

    private def useItem(p: Player): Unit = {
      val inv = instance.itemManager.getInventory(p.getId).toSeq
      if (inv.size < Constants.MAX_INVENTORY_SIZE && rnd.nextDouble() < 0.4) {
        // Picked up a moment ago, as far as this is concerned
        val kind = ItemType.all(rnd.nextInt(ItemType.all.size))
        handedOut += 1
        instance.itemManager.addToInventory(p.getId, new Item(handedOut, 0, 0, kind))
      }
      val held = instance.itemManager.getInventory(p.getId).toSeq.sortBy(_.id)
      val (itemId, kind) =
        if (held.nonEmpty && rnd.nextDouble() < 0.85) { val i = held(rnd.nextInt(held.size)); (i.id, i.itemType.id) }
        else (rnd.nextInt(), rnd.nextInt(8).toByte)
      val pos = p.getPosition
      val (x, y) = (pos.getX + rnd.nextInt(41) - 20, pos.getY + rnd.nextInt(41) - 20)
      note(s"${p.getName} uses item $itemId (kind $kind) at ($x,$y)")
      val had = instance.itemManager.getInventory(p.getId).size
      send(p, new ItemPacket(nextSeq(p), p.getId, x, y, kind, itemId, ItemAction.USE))
      if (instance.itemManager.getInventory(p.getId).size < had) count("items used")
    }

    private def rejoin(p: Player): Unit = {
      note(s"${p.getName} rejoins")
      m.rejoin(p)
    }

    /** R: taken once its minute has run, which few runs are long enough for more than once, so now
      * and then the match is made a minute older (each player's first R goes; later ones wait on
      * their own minute). */
    private def pressR(p: Player): Unit = {
      if (rnd.nextInt(3) == 0) { note("the match is a minute older"); instance.startedAtMs -= Constants.COOLDOWN_RESET_MS }
      note(s"${p.getName} presses R")
      val action = if (rnd.nextDouble() < 0.9) CooldownResetAction.REQUEST else (rnd.nextInt(256) - 128).toByte
      send(p, new CooldownResetPacket(nextSeq(p), p.getId, action, rnd.nextInt()))
      count("R pressed")
    }

    private def leave(p: Player): Unit = {
      note(s"${p.getName} leaves")
      send(p, new PlayerLeavePacket(nextSeq(p), p.getId))
      humans -= p
    }

    // --- The match going on ---

    /** How long until the next request: none at all, a frame or two, a moment, or seconds. */
    private def gap(): Long = {
      val ms = rnd.nextDouble() match {
        case r if r < 0.35 => 0
        case r if r < 0.85 => rnd.nextInt(40)
        case r if r < 0.97 => 40 + rnd.nextInt(260)
        case _ => 1000 + rnd.nextInt(3000)
      }
      (ms * pace).toLong
    }

    private def armedTrapNear(p: Player): Unit = {
      // Someone's trap, already armed, where this player is about to walk. Not in an opening: it
      // is the run's own doing, not a player's, and nobody is to be hurt there
      if (instance.inOpening) return
      val owner = everyone(rnd.nextInt(everyone.size))
      val pos = p.getPosition
      val (x, y) = (near(pos.getX, 2, world.width), near(pos.getY, 2, world.height))
      val trapType = TrapDef.all(rnd.nextInt(TrapDef.all.size)).id
      if (world.isWalkable(x, y)) {
        note(s"${owner.getName}'s armed trap $trapType at ($x,$y)")
        m.armedTrap(owner, x, y, trapType)
      }
    }

    // --- The rules ---

    private case class Seen(pos: Position, alive: Boolean, held: Boolean, moves: Int)

    private def snapshot(): Map[UUID, Seen] = everyone.map { p =>
      val now = GameClock.now()
      p.getId -> Seen(p.getPosition, !p.isDead, p.getFrozenUntil > now || p.getRootedUntil > now, p.getServerMoves)
    }.toMap

    // Everyone who has played in it, the ones who have left too: their deaths stay scored
    private val everyoneEver: Seq[UUID] = everyone.map(_.getId)

    private def scored(pick: UUID => Int): Int = everyoneEver.map(pick).sum

    // Deaths as they happen: looked for after everything the match's clocks run and every request
    private val wasAlive = mutable.Map[UUID, Boolean]()
    private var died = 0
    private def lookForDeaths(): Unit = for (p <- everyone) {
      if (wasAlive.getOrElse(p.getId, true) && p.isDead) died += 1
      wasAlive(p.getId) = !p.isDead
    }
    lookForDeaths()
    m.clockwork.afterEach = _ => lookForDeaths()

    private val firstSeen = mutable.Map[Int, Long]()

    /** Dashing, as the server judges it (PacketValidator.isDashing): phased, or just out of a phase,
      * with a dash in the kit. Over walls if need be (a kit has one phase or dash at most, KitTest). */
    private def dashing(p: Player): Boolean = GameClock.now() - p.getPhasedUntil < Constants.NETWORK_TOLERANCE_MS && {
      val c = CharacterDef.get(p.getCharacterId)
      Seq(c.qAbility, c.eAbility).exists(_.castBehavior.isInstanceOf[DashBuff])
    }

    private def checkRules(before: Map[UUID, Seen], fencedBefore: Set[(Int, Int)], wasOpening: Boolean,
                           actor: Player, ownMove: Boolean): Unit = {
      lookForDeaths()
      val broken = MatchRules.broken(instance)
      check(broken == null, broken)

      val fenced = instance.modifiedTiles.keySet().asScala.toSet -- fencedBefore
      for (p <- everyone) {
        val pos = p.getPosition
        val name = p.getName
        before.get(p.getId).foreach { was =>
          check(p.getServerMoves >= was.moves, s"$name's server moves went from ${was.moves} to ${p.getServerMoves}")
          if (pos != was.pos) {
            // A fence too new for their client to have heard of may be stepped onto (changedLately)
            check(world.isWalkable(pos.getX, pos.getY) || dashing(p) || fenced.contains((pos.getX, pos.getY)) ||
              instance.changedLately(pos.getX, pos.getY),
              s"$name moved from ${was.pos} onto $pos, where nobody can stand (${world.getTile(pos.getX, pos.getY)})")
            if (ownMove && (actor eq p)) {
              check(was.alive, s"$name, dead, moved from ${was.pos} to $pos by their own client")
              check(!was.held, s"$name, held, moved from ${was.pos} to $pos by their own client")
            }
          }
          if (pos != was.pos) count(if (ownMove && (actor eq p)) "own moves" else "other moves")
          if (p.getServerMoves > was.moves) count("server moves")
        }
      }
      // Every death scored once, and every kill somebody's death
      val deaths = scored(instance.killTracker.getDeaths)
      check(deaths == died, s"$died have died, and $deaths deaths are scored")
      check(scored(instance.killTracker.getKills) == deaths, s"${scored(instance.killTracker.getKills)} kills and $deaths deaths")
      count("fence cells", fenced.size)

      // No shot outlives the flight its range allows
      val shots = instance.projectileManager.getAll
      for (s <- shots) {
        val since = firstSeen.getOrElseUpdate(s.id, m.at)
        val d = ProjectileDef.get(s.projectileType)
        val speed = d.effectiveSpeed(s.chargeLevel)
        val ticks = if (speed <= 0f) 1.0 else Math.ceil(d.effectiveMaxRange(s.chargeLevel) / speed) + 2
        val longest = (ticks * (if (d.boomerang) 2 else 1) * Constants.PROJECTILE_SPEED_MS).toLong + 2 * Constants.PROJECTILE_SPEED_MS
        check(m.at - since <= longest, s"projectile ${s.id} (${d.name}) is still in flight after ${m.at - since}ms, of $longest at most")
      }
      if (firstSeen.size > 4096) firstSeen.filterInPlace((id, _) => shots.exists(_.id == id))

      // The opening, for as long as it has lasted all through this step: nobody is hurt, whatever
      // anyone sent. A free-for-all's holds every attack; the Teams wall keeps each team to its half
      if (wasOpening && instance.inOpening) {
        for (p <- everyone) check(p.getHealth == p.getMaxHealth, s"${p.getName} was hurt in the opening (${p.getHealth} of ${p.getMaxHealth})")
        if (gameMode == 1) {
          check(instance.divider != null && instance.divider.up, "the wall is down before the opening is over")
          for (p <- everyone) check(TeamDivider.sideOf(world, p.getPosition.getX, p.getPosition.getY) == TeamDivider.sideOfTeam(p.getTeamId),
            s"${p.getName} of team ${p.getTeamId} is at ${p.getPosition}, across the wall")
          for (s <- shots; owner = instance.registry.get(s.ownerId) if owner != null) {
            val side = instance.divider.side(s.getCellX, s.getCellY)
            check(side == 0 || side == TeamDivider.sideOfTeam(owner.getTeamId), s"${owner.getName}'s shot is across the wall at (${s.getX}, ${s.getY})")
          }
          for (t <- instance.trapManager.getAll)
            check(instance.divider.side(t.x, t.y) == TeamDivider.sideOfTeam(t.teamId), s"a trap of team ${t.teamId}'s lies across the wall at (${t.x}, ${t.y})")
        } else {
          check(instance.attacksLocked, "fire isn't held in a free-for-all's opening")
          check(shots.isEmpty, s"${shots.size} shots in flight in the ceasefire")
          check(instance.trapManager.size == 0, "a trap down in the ceasefire")
          for (p <- everyone) check(!p.hasBarrier && !p.isPhased, s"${p.getName} cast in the ceasefire")
        }
        count("steps in the opening")
      }
    }

    /** What the match has come to, for one run to be held against another of the same seed. */
    private def state: String = everyone.map { p =>
      s"${p.getName} ${p.getPosition} hp${p.getHealth} m${p.getServerMoves} K${instance.killTracker.getKills(p.getId)} " +
        s"D${instance.killTracker.getDeaths(p.getId)} i${instance.itemManager.getInventory(p.getId).size}"
    }.mkString("; ") + s" | shots ${instance.projectileManager.getAll.map(_.id).sorted.mkString(",")} | traps ${instance.trapManager.size}" +
      s" | items ${instance.itemManager.size} | ${m.at}ms"

    def play(steps: Int): String = {
      var result = ""
      val errors = captureStderr(quietly {
        while (step < steps) {
          step += 1
          try {
            // Time passes, and the match goes on: its ticks, its bots, its respawns
            val wait = gap()
            if (wait > 0) {
              val before = snapshot()
              val fencedBefore = instance.modifiedTiles.keySet().asScala.toSet
              val wasOpening = instance.inOpening
              note(s"${wait}ms pass")
              m.advance(wait)
              checkRules(before, fencedBefore, wasOpening, null, ownMove = false)
            }
            // Then somebody's client sends something
            val before = snapshot()
            val fencedBefore = instance.modifiedTiles.keySet().asScala.toSet
            val wasOpening = instance.inOpening
            val actor = if (humans.isEmpty) null else humans(rnd.nextInt(humans.size))
            var ownMove = false
            val r = rnd.nextDouble()
            if (actor == null) ()
            else if (r < 0.40) { ownMove = true; walk(actor) }
            else if (r < 0.44) { ownMove = true; jump(actor) }
            else if (r < 0.70) fire(actor)
            else if (r < 0.76) throwTrap(actor)
            else if (r < 0.84) { ownMove = true; useItem(actor) }
            else if (r < 0.86) rejoin(actor)
            else if (r < 0.864 && humans.size > 2) leave(actor)
            else if (r < 0.88) { note("items spawn"); instance.spawnItemBatch(4) }
            else if (r < 0.92) armedTrapNear(actor)
            else if (r < 0.94) pressR(actor)
            else { note("a tick"); m.tick() }
            checkRules(before, fencedBefore, wasOpening, actor, ownMove)
          } catch {
            case e: AssertionError => throw e
            case e: Throwable =>
              val sw = new java.io.StringWriter
              e.printStackTrace(new java.io.PrintWriter(sw))
              fail(s"threw $sw")
          }
          if (step % 64 == 0) humans.foreach(p => m.sent(p)) // everything it sent reads back
        }
        result = state
      })
      tally("traps on the ground at the end") = instance.trapManager.size
      tally("items carried at the end") = everyone.map(p => instance.itemManager.getInventory(p.getId).size).sum
      tally("deaths") = died
      for (k <- Seq("projectiles", "players", "bots", "respawn")) tally(s"ticks: $k") = m.clockwork.ran(k)
      println(s"MatchFuzzTest seed $seed (${m.at / 1000}s of match): ${tally.toSeq.sortBy(_._1).map { case (k, v) => s"$k $v" }.mkString(", ")}")
      // Anything the match caught and only printed is a bug too
      for (line <- errors.linesIterator if line.contains("Exception") || line.contains("Error in"))
        fail(s"the match printed: $line")
      instance.stop()
      result
    }
  }

}
