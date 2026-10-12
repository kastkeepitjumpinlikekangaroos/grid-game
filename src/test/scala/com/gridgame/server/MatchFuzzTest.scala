package com.gridgame.server

import com.gridgame.common.Constants
import com.gridgame.common.model._
import com.gridgame.common.protocol._
import com.gridgame.server.bots.BotController
import org.junit.Assert._
import org.junit.Test

import java.util.UUID
import scala.collection.mutable
import scala.jdk.CollectionConverters._

/**
 * A match driven by thousands of random requests from its players — steps, jumps, shots from every
 * attack, traps, items, rejoins, leaving — some as a client sends them and some as no honest one
 * would, between the match's ticks, its respawns, its bots and time passing, with the rules every
 * match must keep checked after every one of them:
 *
 *  - nothing it is sent, and none of its ticks, throws (nor do its bots, whose tick swallows it)
 *  - health stays between 0 and the character's maximum, and everyone stays on the map
 *  - nobody is moved onto a cell they can't stand on, unless phased (a phase walks through walls)
 *  - the dead, the frozen and the rooted are not moved by their own client
 *  - an item is on the ground or in one inventory, never both, never two; an inventory holds ten
 *  - a player has at most thirty projectiles in flight, each somewhere, and every shot the server
 *    takes leaves from within reach of its shooter
 *  - a trap to a cell, and no more on the ground than each player may keep
 *  - every death is scored, once
 *
 * Every run is seeded, and a failure says which seed and step, with the actions that led to it.
 */
class MatchFuzzTest {
  import MatchFuzzTest._
  import ServerTestKit.{captureStderr, quietly}

  @Test def aFreeForAllKeepsItsRulesWhateverItsPlayersSend(): Unit =
    for (seed <- 1 to 10) new Run(seed, gameMode = 0, opening = false, bots = seed % 2 == 0).play(2500)

  @Test def aTeamsMatchKeepsItsRulesWhateverItsPlayersSend(): Unit =
    for (seed <- 101 to 106) new Run(seed, gameMode = 1, opening = false, bots = seed % 2 == 1).play(2500)

  @Test def anOpeningKeepsItsRulesWhateverItsPlayersSend(): Unit = {
    // The Teams wall up, and a free-for-all's ceasefire
    for (seed <- 201 to 203) new Run(seed, gameMode = 1, opening = true, bots = true).play(2000)
    for (seed <- 301 to 303) new Run(seed, gameMode = 0, opening = true, bots = true).play(2000)
  }

  @Test def practiceKeepsItsRulesWhateverItsPlayersSend(): Unit =
    for (seed <- 401 to 403) new Run(seed, gameMode = 0, opening = false, bots = true, practice = true).play(2000)
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

  /**
   * One match, played `steps` actions long. Its players are humans (requests through the match's
   * ClientHandler, serialized and read back as the wire carries them) and, with `bots`, two bots on
   * a BotController ticked by hand.
   */
  final class Run(seed: Long, gameMode: Byte, opening: Boolean, bots: Boolean, practice: Boolean = false) {
    private val rnd = new scala.util.Random(seed)
    private val world = clutteredWorld(rnd)
    private val m = new TestMatch(world, gameMode, opening, practice)
    private val instance = m.instance
    private val trace = mutable.Queue[String]()
    private var step = 0
    // What the run got up to, so a run that only ever had its requests refused shows as one
    private val tally = mutable.Map[String, Int]().withDefaultValue(0)
    private def count(what: String, n: Int = 1): Unit = tally(what) += n

    private def note(s: String): Unit = {
      trace.enqueue(s"$step: $s")
      if (trace.size > 40) trace.dequeue()
    }

    private def fail(why: String): Nothing =
      throw new AssertionError(s"seed $seed (mode $gameMode, opening $opening, bots $bots, practice $practice), " +
        s"step $step: $why\nThe last actions:\n  ${trace.mkString("\n  ")}")

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
      humans += m.join(characters(rnd.nextInt(characters.size)).id, x, y, team)
    }

    private val botController: BotController =
      if (!bots) null
      else {
        val bc = new BotController(instance, practice)
        instance.botController = bc
        for (i <- 0 until 2) {
          val team: Byte = if (gameMode == 1) (i % 2 + 1).toByte else 0
          val (x, y) = openCell(if (gameMode == 1) TeamDivider.sideOfTeam(team) else 0)
          val bot = m.join(characters(rnd.nextInt(characters.size)).id, x, y, team,
            id = new UUID(0L, 900000L + seed * 10 + i))
          bc.addBotId(bot.getId)
        }
        bc
      }

    private def everyone: Seq[Player] = instance.registry.getAll.asScala.toSeq

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

    // Shots the server took, with where their shooter stood when they were fired
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
        // where it has the shooter (a slam is put down on its caster)
        val ox = shot.getX - shot.dx
        val oy = shot.getY - shot.dy
        check(!ox.isNaN && !oy.isNaN && Math.abs(ox - pos.getX) <= 3.01f && Math.abs(oy - pos.getY) <= 3.01f,
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
      val held = instance.itemManager.getInventory(p.getId).toSeq
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

    private def leave(p: Player): Unit = {
      note(s"${p.getName} leaves")
      send(p, new PlayerLeavePacket(nextSeq(p), p.getId))
      humans -= p
    }

    // --- The match going on ---

    private def passTime(): Unit = {
      // Cooldowns run out and effects wear off: what a few seconds would do, without the wait
      val p = everyone(rnd.nextInt(everyone.size))
      note(s"time passes for ${p.getName}")
      instance.handler.resetAttacks(p.getId)
      if (rnd.nextBoolean()) { p.setFrozenUntil(0); p.setStunnedUntil(0) }
      if (rnd.nextBoolean()) p.setRootedUntil(0)
      if (rnd.nextBoolean()) p.setPhasedUntil(0)
      if (rnd.nextBoolean()) p.setShieldUntil(0)
      if (rnd.nextBoolean()) p.setGemBoostUntil(0)
      if (rnd.nextBoolean()) p.clearSlow()
      if (rnd.nextBoolean()) p.setBarrierUntil(0)
      if (rnd.nextBoolean()) p.setBarrierRaisedAt(0)
    }

    private def armedTrapNear(p: Player): Unit = {
      // Someone's trap, already armed, where this player is about to walk
      val owner = everyone(rnd.nextInt(everyone.size))
      val pos = p.getPosition
      val (x, y) = (near(pos.getX, 2, world.width), near(pos.getY, 2, world.height))
      val trapType = TrapDef.all(rnd.nextInt(TrapDef.all.size)).id
      if (world.isWalkable(x, y)) {
        note(s"${owner.getName}'s armed trap $trapType at ($x,$y)")
        m.armedTrap(owner, x, y, trapType)
      }
    }

    private def tickBots(): Unit = if (botController != null) {
      note("bots tick")
      val errors = captureStderr(botController.tick(System.currentTimeMillis()))
      check(!errors.contains("Error in tick"), s"a bot's tick threw:\n$errors")
    }

    // --- The rules ---

    private case class Seen(pos: Position, alive: Boolean, held: Boolean, moves: Int)

    private def snapshot(): Map[UUID, Seen] = everyone.map { p =>
      val now = System.currentTimeMillis()
      p.getId -> Seen(p.getPosition, !p.isDead, p.getFrozenUntil > now + 100 || p.getRootedUntil > now + 100, p.getServerMoves)
    }.toMap

    // Everyone who has played in it, the ones who have left too: their deaths stay scored
    private lazy val everyoneEver: Seq[UUID] = everyone.map(_.getId)

    private def deaths: Int = everyoneEver.map(id => instance.killTracker.getDeaths(id)).sum

    private def checkRules(before: Map[UUID, Seen], deathsBefore: Int, fencedBefore: Set[(Int, Int)],
                           actor: Player, ownMove: Boolean): Unit = {
      val fenced = instance.modifiedTiles.keySet().asScala.toSet -- fencedBefore
      var died = 0
      for (p <- everyone) {
        val pos = p.getPosition
        val name = p.getName
        check(p.getHealth >= 0 && p.getHealth <= p.getMaxHealth, s"$name has ${p.getHealth} of ${p.getMaxHealth} health")
        check(pos.getX >= 0 && pos.getY >= 0 && pos.getX < world.width && pos.getY < world.height, s"$name is off the map at $pos")
        before.get(p.getId).foreach { was =>
          check(p.getServerMoves >= was.moves, s"$name's server moves went from ${was.moves} to ${p.getServerMoves}")
          if (pos != was.pos) {
            check(world.isWalkable(pos.getX, pos.getY) || p.isPhased || fenced.contains((pos.getX, pos.getY)),
              s"$name moved from ${was.pos} onto $pos, where nobody can stand (${world.getTile(pos.getX, pos.getY)})")
            if (ownMove && (actor eq p)) {
              check(was.alive, s"$name, dead, moved from ${was.pos} to $pos by their own client")
              check(!was.held, s"$name, held, moved from ${was.pos} to $pos by their own client")
            }
          }
          if (was.alive && p.isDead) died += 1
          if (pos != was.pos) count(if (ownMove && (actor eq p)) "own moves" else "other moves")
          if (p.getServerMoves > was.moves) count("server moves")
        }
      }
      check(deaths - deathsBefore == died, s"$died died, but ${deaths - deathsBefore} deaths were scored")
      count("deaths", died)
      count("fence cells", fenced.size)

      // Items: on the ground or in one inventory
      val ground = instance.itemManager.getAll.map(_.id)
      check(ground.distinct.size == ground.size, "an item is on the ground twice")
      val carried = mutable.Map[Int, UUID]()
      for (p <- everyone) {
        val inv = instance.itemManager.getInventory(p.getId).toSeq
        check(inv.size <= Constants.MAX_INVENTORY_SIZE, s"${p.getName} carries ${inv.size} items")
        for (item <- inv) {
          check(!ground.contains(item.id), s"item ${item.id} is in ${p.getName}'s inventory and on the ground")
          carried.get(item.id).foreach(other => fail(s"item ${item.id} is carried by ${p.getName} and $other"))
          carried(item.id) = p.getId
        }
      }

      // Projectiles: thirty a player, each somewhere
      val inFlight = instance.projectileManager.getAll
      for ((owner, shots) <- inFlight.groupBy(_.ownerId))
        check(shots.size <= 30, s"$owner has ${shots.size} projectiles in flight")
      for (shot <- inFlight)
        check(java.lang.Float.isFinite(shot.getX) && java.lang.Float.isFinite(shot.getY), s"a projectile at (${shot.getX}, ${shot.getY})")

      // Traps: one to a cell, and no more than each player may keep
      val traps = instance.trapManager.getAll
      for (t <- traps) check(instance.trapManager.trapAt(t.x, t.y) eq t, s"trap ${t.id} at (${t.x}, ${t.y}) shares its cell")
      for ((owner, mine) <- traps.groupBy(_.ownerId))
        check(mine.size <= mine.map(t => TrapDef.get(t.trapType).maxActive).max, s"$owner has ${mine.size} traps down")
    }

    def play(steps: Int): Unit = {
      everyoneEver
      val errors = captureStderr(quietly {
        while (step < steps) {
          step += 1
          val before = snapshot()
          val deathsBefore = deaths
          val fencedBefore = instance.modifiedTiles.keySet().asScala.toSet
          val actor = if (humans.isEmpty) null else humans(rnd.nextInt(humans.size))
          var ownMove = false
          try {
            val r = rnd.nextDouble()
            if (actor == null || r < 0.18) { note("tick"); m.tick() }
            else if (r < 0.48) { ownMove = true; walk(actor) }
            else if (r < 0.51) { ownMove = true; jump(actor) }
            else if (r < 0.70) fire(actor)
            else if (r < 0.74) throwTrap(actor)
            else if (r < 0.79) { ownMove = true; useItem(actor) }
            else if (r < 0.80) rejoin(actor)
            else if (r < 0.803 && humans.size > 2) leave(actor)
            else if (r < 0.84) { note("players tick"); instance.tickPlayers() }
            else if (r < 0.85) { note("items spawn"); instance.spawnItemBatch(4) }
            else if (r < 0.88) passTime()
            else if (r < 0.90) armedTrapNear(actor)
            else if (r < 0.95) tickBots()
            else everyone.find(_.isDead).foreach { d => note(s"${d.getName} respawns"); instance.respawn(d.getId); count("respawns") }
          } catch {
            case e: AssertionError => throw e
            case e: Throwable =>
              val sw = new java.io.StringWriter
              e.printStackTrace(new java.io.PrintWriter(sw))
              fail(s"threw $sw")
          }
          checkRules(before, deathsBefore, fencedBefore, actor, ownMove)
          if (step % 64 == 0) everyone.foreach(p => m.sent(p)) // everything it sent reads back
        }
      })
      tally("traps on the ground at the end") = instance.trapManager.size
      tally("items carried at the end") = everyone.map(p => instance.itemManager.getInventory(p.getId).size).sum
      println(s"MatchFuzzTest seed $seed: ${tally.toSeq.sortBy(_._1).map { case (k, v) => s"$k $v" }.mkString(", ")}")
      // Anything the match caught and only printed is a bug too
      for (line <- errors.linesIterator if line.contains("Exception") || line.contains("Error in"))
        fail(s"the match printed: $line")
      instance.stop()
    }
  }

}
