package com.gridgame.e2e

import com.gridgame.client.game.ClientState
import com.gridgame.common.WorldRegistry
import com.gridgame.common.model._
import com.gridgame.server.game.E2EServer
import org.junit.Assert._

import scala.jdk.CollectionConverters._

/**
 * Honest players (Persona) at several tables at once, round after round: each table plays a match of
 * its own every round — a free-for-all, Teams, either with bots, or practice — on the maps of the
 * rotation, with characters further round the roster every round, and goes back to the lobby browser
 * between them. On a strict loopback the harness holds every rule at every step (Loopback); on a
 * lossy network, once it has quietened at the end of a round, what came by TCP must agree: who is
 * dead, each player's score, the items on the ground and carried, the traps down, and then the
 * standings.
 */
final class FuzzRun(loop: Loopback, tableCount: Int = 3) {
  import FuzzRun._

  private val rnd = loop.rnd
  private val roster = CharacterDef.all.map(_.id.id)
  private var nextCharacter = rnd.nextInt(roster.size)
  private val tables: Seq[Seq[LoopClient]] = Seq.tabulate(tableCount) { t =>
    Seq.tabulate(4 + rnd.nextInt(3))(i => loop.connect(s"t${t}p$i${loop.seed}"))
  }
  loop.logIn(tables.flatten)

  loop.itemEveryMs = 1500
  // A lossy network loses what only a datagram says, and the server tells a client of a refusal it
  // reports on stderr when it comes of a race the network made (a trap thrown where another has just
  // landed, an item used as its player died): the judge's business on a perfect network, an everyday
  // thing on this one. Counted. Nothing the network does to an honest player's steps, shots or casts
  // is refused any more (Constants.NETWORK_TOLERANCE_MS): the validator's word fails the run
  if (!loop.net.isPerfect)
    loop.stderrAllowed = line => Loopback.FenceHandedBack(line) || line.startsWith("ClientHandler:")

  private def plan(round: Int): Plan = rnd.nextInt(20) match {
    // The first round on the smallest map, where the fighting starts soonest
    case _ if round == 1 && rnd.nextBoolean() => Plan("free-for-all with bots", 0, 2 + rnd.nextInt(2))
    case r if r < 6 => Plan("free-for-all", Maps(rnd.nextInt(Maps.size)), 0)
    case r if r < 11 => Plan("teams", Maps(rnd.nextInt(Maps.size)), 0)
    case r if r < 17 => Plan("free-for-all with bots", Maps(rnd.nextInt(Maps.size)), 1 + rnd.nextInt(3))
    case _ => Plan("practice", 0, 0)
  }

  private def character(): Byte = { nextCharacter = (nextCharacter + 1) % roster.size; roster(nextCharacter) }

  /**
   * Every table's players into a lobby (or one of them into practice) and its match started, all the
   * tables at once and each step as the lobby screens take it: the lobby, or None for practice.
   */
  private def begin(plans: Seq[(Seq[LoopClient], Plan)]): Seq[Option[Short]] = {
    for ((table, p) <- plans) {
      loop.note(s"${table.head.name}'s table: ${p.mode} on ${WorldRegistry.getFilename(p.map)}")
      table.foreach(_.pick(character()))
    }
    val (practices, lobbies) = plans.partition(_._2.mode == "practice")
    practices.foreach { case (table, _) => table.head.client.startPractice() }
    for ((table, p) <- lobbies) table.head.client.createLobby(s"${table.head.name}'s", p.map, 1)
    loop.await("the lobbies")(lobbies.forall(_._1.head.client.currentLobbyId != 0))
    val teams = lobbies.filter(_._2.mode == "teams")
    for ((table, p) <- teams) table.head.client.updateLobbyConfig(p.map, 1, 1.toByte, (table.size + 1) / 2)
    loop.await("the Teams lobbies")(teams.forall(_._1.head.client.currentLobbyGameMode == 1))
    val guests = lobbies.flatMap { case (table, _) => table.tail.map(_ -> table.head.client.currentLobbyId) }
    val refused = guests.map { case (c, _) => c -> c.lobbyRefusals.size }.toMap
    for ((c, id) <- guests) c.client.joinLobby(id)
    loop.await("the guests")(guests.forall { case (c, id) => c.client.currentLobbyId == id || c.lobbyRefusals.size > refused(c) })
    for ((c, id) <- guests) loop.check(c.client.currentLobbyId == id, s"${c.name} was refused lobby $id")
    for ((table, p) <- lobbies; _ <- 0 until p.bots) table.head.client.addBot()
    loop.await("the bots")(lobbies.forall { case (table, p) =>
      loop.server.lobbyManager.getLobby(table.head.client.currentLobbyId).botManager.botCount == p.bots
    })
    val ids = lobbies.map { case (table, _) => table -> table.head.client.currentLobbyId }.toMap
    lobbies.foreach(_._1.head.client.startGame())
    loop.await("the matches")(practices.forall(_._1.head.playing) && lobbies.forall { case (table, p) =>
      table.forall(c => c.playing && c.client.getPlayers.size == table.size - 1 + p.bots)
    })
    plans.map { case (table, p) => if (p.mode == "practice") None else Some(ids(table)) }
  }

  /** The match over: by its clock for a lobby's, by its player leaving for practice's (MatchEnd either way). */
  private def end(table: Seq[LoopClient], lobby: Option[Short]): Unit = {
    lobby match {
      case Some(id) => loop.endMatch(id)
      case None =>
        val player = table.head
        val gi = loop.matchOf(player)
        player.client.leaveMatch()
        loop.await(s"${player.name}'s practice to end")(player.client.clientState == ClientState.SCOREBOARD)
        assertTrue(s"${player.name}'s practice scoreboard has it", player.client.scoreboard.asScala.exists(_.playerId == player.id))
        if (gi != null) assertFalse("the practice is over on the server", E2EServer.isRunning(gi))
    }
    table.foreach(_.client.returnToLobbyBrowser())
  }

  /** What came by TCP, agreed on once a lossy network has quietened (see the class comment). */
  private def agreeOnWhatCameByTcp(): Unit =
    for (c <- loop.everyone if c.playing) {
      val gi = loop.matchOf(c)
      loop.check(gi != null, s"${c.name} is playing a match the server has it in none of")
      val client = c.client
      val me = gi.registry.get(c.id)
      def check(ok: Boolean, why: => String): Unit = loop.check(ok, s"$why${c.recent}")
      check(client.getIsDead == me.isDead, s"${c.name} has itself dead ${client.getIsDead}, the server ${me.isDead}")
      gi.registry.getAll.asScala.filter(_.getId != c.id).foreach { p =>
        val seen = client.getPlayers.get(p.getId)
        check(seen != null && seen.isDead == p.isDead,
          s"${c.name} has ${p.getName} dead ${seen != null && seen.isDead}, the server ${p.isDead}")
      }
      check(client.killCount == gi.killTracker.getKills(c.id) && client.deathCount == gi.killTracker.getDeaths(c.id),
        s"${c.name} has its score at ${client.killCount}/${client.deathCount}, the server " +
          s"${gi.killTracker.getKills(c.id)}/${gi.killTracker.getDeaths(c.id)}")
      val ground = gi.itemManager.getAll.map(_.id).toSet
      val seenItems = client.getItems.keySet().asScala.map(_.intValue).toSet
      check(seenItems == ground, s"${c.name} has the items on the ground as $seenItems, the server $ground")
      // The dead's are left out: an item used as its player died, before the client heard, the server
      // doesn't take and says nothing of, and the respawn takes everything both ends have anyway
      if (!me.isDead && !client.getIsDead) {
        val carried = gi.itemManager.getInventory(c.id).groupBy(_.itemType.id).map { case (k, v) => k -> v.size }
        for (t <- ItemType.all)
          check(client.getItemCount(t.id) == carried.getOrElse(t.id, 0),
            s"${c.name} carries ${client.getItemCount(t.id)} ${t.name}s, the server ${carried.getOrElse(t.id, 0)}")
      }
      val traps = gi.trapManager.getAll.map(_.id).toSet
      val seenTraps = client.getTraps.keySet().asScala.map(_.intValue).toSet
      check(seenTraps == traps, s"${c.name} has the traps down as $seenTraps, the server $traps")
    }

  /** `check`, which a match ticking on its own threads may catch mid-change, passing within `tries`
    * looks 50ms apart. */
  private def eventually(tries: Int = 5)(check: => Unit): Unit = {
    var tried = 0
    var passed = false
    while (!passed) {
      try { check; passed = true }
      catch {
        case e: AssertionError =>
          tried += 1
          if (tried >= tries) throw e
          loop.settle(50)
      }
    }
  }

  /**
   * Once hands are off: where everyone is, which only datagrams say, agrees too, mended by what every
   * client says of itself every POSITION_RESEND_MS (and the server of every bot): each client where
   * the server has it, and where every other human is. The bots fight on, so where they are is left
   * out, and so is health.
   */
  private def agreeOnWhereEveryoneIs(): Unit =
    for (c <- loop.everyone if c.playing) {
      val gi = loop.matchOf(c)
      loop.check(gi != null, s"${c.name} is playing a match the server has it in none of")
      val me = gi.registry.get(c.id)
      loop.check(c.client.getLocalPosition == me.getPosition,
        s"${c.name} has itself at ${c.client.getLocalPosition}, the server at ${me.getPosition}${c.recent}")
      for (other <- loop.everyone if other.playing && !other.id.equals(c.id) && gi.registry.contains(other.id)) {
        val seen = c.client.getPlayers.get(other.id)
        val there = gi.registry.get(other.id).getPosition
        loop.check(seen != null && seen.getPosition == there,
          s"${c.name} has ${other.name} at ${if (seen == null) "nowhere" else seen.getPosition}, the server at $there${c.recent}")
      }
    }

  def play(rounds: Int, roundMs: Int = 4000): Unit = {
    for (round <- 1 to rounds) {
      loop.openingMs = if (rnd.nextBoolean()) 0L else 300L + rnd.nextInt(900)
      val plans = tables.map(_ => plan(round))
      val lobbies = begin(tables.zip(plans))
      // Anyone but a lobby's host may leave its match (Esc, twice): the host stays, so the match goes on
      val hands = tables.zip(plans).flatMap { case (table, p) =>
        table.map(c => c -> new Persona(c, rnd, leaves = p.mode != "practice" && (c ne table.head), chats = rnd.nextBoolean()))
      }.toMap
      loop.run(roundMs + rnd.nextInt(1000))(c => hands.get(c).foreach(_.act()))
      hands.values.foreach(_.letGo())
      // Hands off, and everything said has arrived (on a lossy network, everything said by TCP: the
      // bots fight on and heartbeats come every second, so datagrams are always on their way). A
      // match on its own clocks goes on changing as it is looked at: what disagrees is looked at again
      if (loop.strict) loop.settle(100)
      else {
        loop.settle(if (loop.net.isPerfect) 200 else 600)
        eventually() {
          loop.await("TCP to quieten", 5000)(loop.tcpQuiet)
          agreeOnWhatCameByTcp()
        }
        // A position resent can be lost too, on the way to the server or on the way on from it: with
        // a fifth of datagrams lost each way, a dozen resends (six seconds) mend every pair of players
        // in a run but for a few in ten thousand
        eventually(tries = 120)(agreeOnWhereEveryoneIs())
      }
      tables.zip(lobbies).foreach { case (t, l) => end(t, l) }
      loop.count("rounds")
      plans.foreach(p => loop.count(s"matches: ${p.mode}"))
    }
    loop.say(s"${getClass.getSimpleName} seed ${loop.seed} on ${loop.net}: " +
      loop.tally.toSeq.filterNot(_._1.startsWith("sent ")).sortBy(_._1).map { case (k, v) => s"$k $v" }.mkString(", "))
    if (loop.strict) {
      assertTrue("players took steps", loop.tally("steps taken") > 0)
      assertTrue("players' shots were taken", loop.tally("primary shots taken") > 0)
    }
  }
}

object FuzzRun {
  /** The seeds to play: these, or the one asked for (`GRIDGAME_E2E_SEED`), or new ones for as long as
    * a soak was asked to last (`GRIDGAME_E2E_SOAK_SECONDS`). */
  def seeds(default: Seq[Long]): Iterator[Long] = {
    val one = sys.env.get("GRIDGAME_E2E_SEED").map(_.toLong)
    val soakMs = sys.env.get("GRIDGAME_E2E_SOAK_SECONDS").map(_.toLong * 1000)
    (one, soakMs) match {
      case (Some(seed), _) => Iterator(seed)
      case (None, Some(ms)) =>
        val end = System.currentTimeMillis() + ms
        Iterator.from(1).map(i => default.head * 1000 + i).takeWhile(_ => System.currentTimeMillis() < end)
      case _ => default.iterator
    }
  }

  // Mostly the smaller maps, where the players find each other in a round's few seconds
  private val Maps = Seq(0, 0, 6, 6, 4, 1, 5, 2, 3).filter(_ < WorldRegistry.size)

  /** One table's match in a round. */
  private final case class Plan(mode: String, map: Int, bots: Int)
}
