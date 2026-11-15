package com.gridgame.server

import com.gridgame.common.WorldRegistry
import com.gridgame.common.model._
import com.gridgame.server.bots.{BotController, BotSlot}
import com.gridgame.server.lobby.{LobbyStatus, MatchLauncher}

import java.io.PrintWriter
import java.util.UUID
import scala.collection.mutable
import scala.jdk.CollectionConverters._

/**
 * The balance simulator (scripts/balance; design/characters.md, *Balance in free-for-alls against
 * bots*): mixed free-for-alls of bots, launched as a lobby's are and played to their end on a clock
 * of their own (SimKit), a match of fourteen in a few seconds and the same match again from the
 * same seed. Each has `fighters` melee and skirmisher characters and `ranged` ranged ones, dealt
 * evenly from the roster by the seed, on every map in turn.
 *
 * args: out=<csv> from=<n> to=<n> seed=<s> minutes=5 fighters=5 ranged=9 maps=0,1,2... (all)
 *       pace=700,1100  the wait between a bot's primaries, in ms (500,550 is a player's)
 *       reach=0|1      melee bots swing at all their swing will strike, as a player does
 * One line a player: match,seed,map,slot,character,role,kills,deaths,place,how=count;... where
 * `how` is what dealt each of its kills, read from the match's journal. Match n is the same
 * whichever slice of the matches (from, to) a process plays, so they are played side by side.
 *
 * It opens game_accounts.db and server_identity.p12 where it is run, as a server does: run each
 * process in a folder of its own, with `worlds` beside it.
 */
object BalanceSim {
  def main(args: Array[String]): Unit = {
    val a = args.map { s => val i = s.indexOf('='); s.substring(0, i) -> s.substring(i + 1) }.toMap
    val from = a.getOrElse("from", "0").toInt
    val to = a.getOrElse("to", "10").toInt
    val seed = a.getOrElse("seed", "101").toLong
    val minutes = a.getOrElse("minutes", "5").toInt
    val nF = a.getOrElse("fighters", "5").toInt
    val nR = a.getOrElse("ranged", "9").toInt
    val pace = a.getOrElse("pace", "700,1100").split(',').map(_.toLong)
    val reach = a.getOrElse("reach", "0") == "1"
    val maps = a.get("maps").map(_.split(',').map(_.toInt).toSeq).getOrElse(0 until WorldRegistry.size)
    val out = new PrintWriter(a("out"))

    val fighters = CharacterDef.all.filter(_.role != CombatRole.Ranged)
    val rangedAll = CharacterDef.all.filter(_.role == CombatRole.Ranged)
    // The line-ups of every match up to `to`, dealt from one generator so that match n is the same
    // whichever slice of them a process plays
    val deal = new scala.util.Random(seed * 7919L + 13L)
    def dealer(pool: Seq[CharacterDef]): () => CharacterDef = {
      var hand = List.empty[CharacterDef]
      () => { if (hand.isEmpty) hand = deal.shuffle(pool).toList; val c = hand.head; hand = hand.tail; c }
    }
    val nextF = dealer(fighters); val nextR = dealer(rangedAll)
    val lineups = (0 until to).map { _ =>
      // no character twice in a match
      def take(next: () => CharacterDef, n: Int): Seq[CharacterDef] = {
        val got = mutable.LinkedHashSet[CharacterDef]()
        var guard = 0
        val back = mutable.ArrayBuffer[CharacterDef]()
        while (got.size < n && guard < 1000) { val c = next(); if (!got.add(c)) back += c; guard += 1 }
        got.toSeq
      }
      deal.shuffle(take(nextF, nF) ++ take(nextR, nR))
    }

    val server = ServerTestKit.server
    val t0 = System.nanoTime()
    for (n <- from until to) {
      val mapIndex = maps(n % maps.size)
      val matchSeed = seed * 1000003L + n
      val characters = lineups(n)
      val oldOut = System.out; val oldErr = System.err
      val sink = new java.io.PrintStream(new java.io.OutputStream { override def write(b: Int): Unit = () })
      var rows: Seq[String] = Nil
      try {
        System.setOut(sink); System.setErr(sink)
        Console.withOut(sink) { Console.withErr(sink) {
          rows = Sim(matchSeed) { clock =>
            val lobby = server.lobbyManager.createLobby(new UUID(7L, matchSeed), s"sim $n", mapIndex, minutes, characters.size)
            for ((c, i) <- characters.zipWithIndex) lobby.botManager.seat(BotSlot(new UUID(0L, 100L + i), s"Bot${i + 1}", c.id.id))
            lobby.status = LobbyStatus.IN_GAME
            val launcher = new MatchLauncher(server)
            launcher.startMatch = _.begin()
            launcher.startBots = _ => ()
            val gi = launcher.launch(lobby)
            gi.botController match {
              case bots: BotController =>
                bots.shootCooldownMs = (pace(0), pace(1))
                bots.swingsAtFullReach = reach
              case _ =>
            }
            val clockwork = new MatchClockwork(gi, clock, items = true)
            var result: Seq[String] = Nil
            clockwork.onTimeUp = () => {
              val board = gi.killTracker.getScoreboard.map { case (id, k, d) => ((id.getLeastSignificantBits - 100L).toInt, (k, d)) }.toMap
              // kills by what dealt the blow, from the journal
              val how = Array.fill(characters.size)(mutable.Map[String, Int]().withDefaultValue(0))
              val Killed = """.*killed by (Bot\d+) \([0-9a-f]+\) with (.*), at \(.*""".r
              for (line <- gi.journal.lines) line match {
                case Killed(who, what) => how(who.substring(3).toInt - 1)(what) += 1
                case _ =>
              }
              // placed on kills less deaths, then kills
              val order = characters.indices.sortBy { i => val (k, d) = board.getOrElse(i, (0, 0)); (-(k - d), -k) }
              val place = order.zipWithIndex.map { case (slot, p) =>
                // ties share the better place
                val (k, d) = board.getOrElse(slot, (0, 0))
                val first = order.indexWhere { o => val (k2, d2) = board.getOrElse(o, (0, 0)); k2 - d2 == k - d && k2 == k }
                slot -> (first + 1)
              }.toMap
              result = characters.indices.map { i =>
                val (k, d) = board.getOrElse(i, (0, 0))
                val c = characters(i)
                s"$n,$seed,$mapIndex,$i,${c.id.id},${c.role.name},$k,$d,${place(i)},${how(i).toSeq.sortBy(-_._2).map { case (w, x) => s"$w=$x" }.mkString(";")}"
              }
              server.endGame(lobby.id)
            }
            clockwork.advance(minutes * 60000L + 1000L)
            result
          }
        } }
      } finally { System.setOut(oldOut); System.setErr(oldErr) }
      rows.foreach(out.println)
      out.flush()
      if ((n - from) % 20 == 19) System.err.println(s"${a("out")}: ${n - from + 1}/${to - from} in ${(System.nanoTime() - t0) / 1000000000L}s")
    }
    out.close()
    System.exit(0)
  }
}

/** The roster as it is built, a line a character (C), a projectile it throws (P) and a trap (T),
  * with every number a balance pass moves: what scripts/balance reads the roster from. args: the
  * file to write. */
object RosterDump {
  def main(args: Array[String]): Unit = {
    val out = new PrintWriter(args(0))
    def cast(a: AbilityDef): String = a.castBehavior match {
      case StandardProjectile => "shot"
      case FanProjectile(n, _) => s"fan$n"
      case GroundSlam(r) => s"slam$r"
      case PhaseShiftBuff(ms) => s"phase$ms"
      case DashBuff(d, ms, _) => s"dash$d"
      case TeleportCast(d) => s"blink$d"
      case TrapCast(t, _) => s"trap$t"
      case BarrierCast(ms) => s"barrier$ms"
      case other => other.toString
    }
    def typeOf(a: AbilityDef): String = a.castBehavior match {
      case StandardProjectile | FanProjectile(_, _) | GroundSlam(_) => a.projectileType.toString
      case TrapCast(t, _) => s"trap$t"
      case _ => "-"
    }
    for (c <- CharacterDef.all)
      out.println(Seq("C", c.id.id, c.displayName, c.role.name, c.maxHealth, c.primaryProjectileType,
        typeOf(c.qAbility), cast(c.qAbility), c.qAbility.cooldownMs, c.qAbility.name.replace('\t', ' '),
        typeOf(c.eAbility), cast(c.eAbility), c.eAbility.cooldownMs, c.eAbility.name.replace('\t', ' ')).mkString("\t"))
    val used = CharacterDef.all.flatMap(c => Seq(c.primaryProjectileType.toString, typeOf(c.qAbility), typeOf(c.eAbility)))
      .filter(s => s != "-" && !s.startsWith("trap")).map(_.toByte).distinct
    for (t <- used) {
      val d = ProjectileDef.get(t)
      val dots = d.onHitEffects.collect { case Burn(x, ms, tick) => s"burn$x"; case Poison(x, ms, tick) => s"poison$x" }.mkString("+")
      val fx = d.onHitEffects.map(_.toString).mkString("+")
      out.println(Seq("P", t, d.name, d.damage,
        d.chargeDamageScaling.map(s => s"${s.min}-${s.max}").getOrElse("-"),
        d.distanceDamageScaling.map(s => s"${s.baseDamage}-${s.maxDamage}").getOrElse("-"),
        d.aoeOnHit.map(_.damage.toString).getOrElse("-"), d.aoeOnMaxRange.map(_.damage.toString).getOrElse("-"),
        d.explosionConfig.map(e => s"${e.centerDamage}-${e.edgeDamage}").getOrElse("-"),
        if (dots.isEmpty) "-" else dots, d.maxRange, d.speedMultiplier, d.hitRadius, d.pierceCount, if (fx.isEmpty) "-" else fx).mkString("\t"))
    }
    for (t <- TrapDef.all)
      out.println(Seq("T", t.id, t.name, t.damage, t.explosion.map(e => s"${e.centerDamage}-${e.edgeDamage}").getOrElse("-"),
        t.effects.map(_.toString).mkString("+")).mkString("\t"))
    out.close()
  }
}
