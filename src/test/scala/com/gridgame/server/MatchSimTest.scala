package com.gridgame.server

import com.gridgame.common.Constants
import com.gridgame.common.WorldRegistry
import com.gridgame.common.model._
import com.gridgame.server.bots.BotSlot
import com.gridgame.server.game.GameInstance
import com.gridgame.server.lobby.{LobbyStatus, MatchLauncher}
import org.junit.Assert._
import org.junit.Test

import java.util.UUID
import scala.collection.mutable
import scala.jdk.CollectionConverters._

/**
 * Whole matches, start to finish, on a clock of the test's own (SimKit): eight bots on a map,
 * launched by the real launcher, through the opening, the minute R waits, the items' second batch
 * and on to the end and its scoring — two minutes of match in the second its ticks take to run,
 * where the soak on the system's clock watches eight seconds. Between them the matches play every
 * map, both modes and every character in the roster.
 *
 * After everything a match's clocks run (each projectile tick, player tick and bots' tick, each
 * respawn), the match is looked over: the rules every match keeps (MatchRules), what its opening
 * holds back, nobody moved while held or dead, every death scored once and every kill somebody's
 * death, no projectile outliving its range, the clock every client is told counting down to the
 * second. And the match is played twice: with the same seed it is the same match, state for state
 * after every tick, which is what makes a failure here one that can be played again.
 */
class MatchSimTest {
  import ServerTestKit.{captureStderr, quietly, server}

  private val Minutes = 2
  private val Bots = 8

  /** What a match came to. */
  private final class Played(val states: Array[Int], val tally: Map[String, Int], val standings: Seq[(Long, Int, Int)],
                             val lastState: String)

  /** The characters of match `n`'s bots: the roster eight at a time, so fourteen matches play it all. */
  private def cast(n: Int): Seq[CharacterDef] =
    (0 until Bots).map(i => CharacterDef.all((n * Bots + i) % CharacterDef.all.size))

  private def fail(seed: Long, map: String, at: Long, why: String): Nothing =
    throw new AssertionError(s"seed $seed on $map, ${at}ms in: $why")

  /** Everything about the match that the next tick is played from, as text. */
  private def stateOf(gi: GameInstance, now: Long): String = {
    val sb = new StringBuilder
    for (p <- gi.registry.getAll.asScala.toSeq.sortBy(_.getId.getLeastSignificantBits)) {
      val pos = p.getPosition
      sb.append(p.getId.getLeastSignificantBits).append(':').append(pos.getX).append(',').append(pos.getY)
        .append(" hp").append(p.getHealth).append(" m").append(p.getServerMoves).append(" k").append(p.getKnockbacks)
        .append(" f").append(Math.max(0L, p.getFrozenUntil - now)).append(" r").append(Math.max(0L, p.getRootedUntil - now))
        .append(" s").append(Math.max(0L, p.getSlowedUntil - now)).append(" ph").append(Math.max(0L, p.getPhasedUntil - now))
        .append(" b").append(Math.max(0L, p.getBarrierUntil - now)).append(" sh").append(Math.max(0L, p.getShieldUntil - now))
        .append(" g").append(Math.max(0L, p.getGemBoostUntil - now)).append(" sp").append(Math.max(0L, p.getSpeedBoostUntil - now))
        .append(if (p.isBurning) " burn" else "").append(if (p.isPoisoned) " poison" else "")
        .append(" K").append(gi.killTracker.getKills(p.getId)).append(" D").append(gi.killTracker.getDeaths(p.getId))
        .append(" i").append(gi.itemManager.getInventory(p.getId).map(_.id).toSeq.sorted.mkString("."))
        .append('\n')
    }
    for (s <- gi.projectileManager.getAll.sortBy(_.id))
      sb.append('p').append(s.id).append(' ').append(s.projectileType).append(' ').append(s.getX).append(',').append(s.getY)
        .append(' ').append(s.dx).append(',').append(s.dy).append(' ').append(s.getDistanceTraveled).append('\n')
    for (i <- gi.itemManager.getAll.sortBy(_.id)) sb.append('i').append(i.id).append(' ').append(i.itemType.id).append(' ').append(i.getCellX).append(',').append(i.getCellY).append('\n')
    for (t <- gi.trapManager.getAll.sortBy(_.id)) sb.append('t').append(t.id).append(' ').append(t.trapType).append(' ').append(t.x).append(',').append(t.y).append('\n')
    sb.append("tiles ").append(gi.modifiedTiles.size)
    sb.toString
  }

  private case class Seen(pos: Position, dead: Boolean, frozenUntil: Long, rootedUntil: Long, moves: Int)

  /** One match of bots, launched as a lobby's is and played to its end on a clock of its own. */
  private def play(seed: Long, mapIndex: Int, teams: Boolean, characters: Seq[CharacterDef]): Played = Sim(seed) { clock =>
    val map = WorldRegistry.getFilename(mapIndex) + (if (teams) " (Teams)" else "")
    val tally = mutable.Map[String, Int]().withDefaultValue(0)
    val states = mutable.ArrayBuffer[Int]()
    var lastState = ""
    var standings: Seq[(Long, Int, Int)] = Nil

    val errors = captureStderr(quietly {
      // A host who never connected isn't seated: a match of bots alone, each with an id of its own
      // that is the same at every run
      val lobby = server.lobbyManager.createLobby(new UUID(7L, seed), s"sim $seed", mapIndex, Minutes, Bots)
      if (teams) lobby.configure(1.toByte, Bots / 2)
      for ((c, i) <- characters.zipWithIndex) {
        lobby.botManager.seat(BotSlot(new UUID(0L, 100L + i), s"Bot ${i + 1}", c.id.id))
      }
      lobby.status = LobbyStatus.IN_GAME
      val launcher = new MatchLauncher(server)
      launcher.startMatch = _.begin()
      launcher.startBots = _ => ()
      val gi = launcher.launch(lobby)
      val world = gi.world
      // On the map its lobby chose, as its file reads: a file the match can't load is an empty
      // world to it (GameInstance.loadWorld), and its players' clients would be on another
      val asItsFileReads = com.gridgame.common.world.WorldLoader.load(
        MatchLauncher.resolveWorldPath("worlds/" + WorldRegistry.getFilename(mapIndex)))
      assertEquals(s"$map: the map the match is played on", (asItsFileReads.name, asItsFileReads.width, asItsFileReads.height),
        (world.name, world.width, world.height))
      assertTrue(s"$map: every cell of it", (0 until world.height).forall(y => (0 until world.width).forall(x =>
        world.getTile(x, y) == asItsFileReads.getTile(x, y))))
      assertEquals(s"$map: and its spawn points", asItsFileReads.spawnPoints, world.spawnPoints)
      // Everyone seated is in it as the character they were seated as, and nobody starts on top of
      // anybody else: each start is chosen away from the ones already given out
      val seated = lobby.botManager.getBots
      assertEquals(s"$map: everyone seated", seated.map(_.id).toSet, gi.registry.getAll.asScala.map(_.getId).toSet)
      for (slot <- seated)
        assertEquals(s"$map: ${slot.name}'s character", slot.characterId, gi.registry.get(slot.id).getCharacterId)
      val starts = gi.registry.getAll.asScala.toSeq.map(p => (p.getPosition.getX, p.getPosition.getY))
      for (i <- starts.indices; j <- starts.indices if i < j)
        assertTrue(s"$map: two start within three cells of each other, at ${starts(i)} and ${starts(j)}",
          Math.hypot(starts(i)._1 - starts(j)._1, starts(i)._2 - starts(j)._2) >= 3)
      val clockwork = new MatchClockwork(gi, clock, items = true)
      clockwork.onTimeUp = () => {
        standings = gi.killTracker.getScoreboard.map { case (id, k, d) => (id.getLeastSignificantBits, k, d) }.sortBy(_._1)
        server.endGame(lobby.id)
      }

      val seen = mutable.Map[UUID, Seen]()
      val deathsSeen = mutable.Map[UUID, Int]().withDefaultValue(0)
      val firstSeen = mutable.Map[Int, Long]()
      for (p <- gi.registry.getAll.asScala)
        seen(p.getId) = Seen(p.getPosition, p.isDead, p.getFrozenUntil, p.getRootedUntil, p.getServerMoves)
      val opening = Constants.MATCH_OPENING_MS.toLong

      clockwork.afterEach = { what =>
        val at = clockwork.at
        val now = clock.millis()
        def check(ok: Boolean, why: => String): Unit = if (!ok) fail(seed, map, at, s"after $what: $why")
        if (what != "end") {
          val broken = MatchRules.broken(gi, now)
          check(broken == null, broken)
          val everyone = gi.registry.getAll.asScala.toSeq

          for (p <- everyone) {
            val pos = p.getPosition
            val who = s"${p.getName} (${CharacterDef.get(p.getCharacterId).displayName})"
            val was = seen(p.getId)
            // The living stand where someone can: open ground, or a fence put up on them
            if (!p.isDead && !p.isPhased)
              check(world.isWalkable(pos.getX, pos.getY) || gi.modifiedTiles.containsKey((pos.getX, pos.getY)),
                s"$who stands on ${world.getTile(pos.getX, pos.getY)} at $pos")
            if (pos != was.pos && p.getServerMoves == was.moves) {
              check(!(was.dead && p.isDead), s"$who, dead, moved from ${was.pos} to $pos")
              check(was.frozenUntil <= now, s"$who, held for ${was.frozenUntil - now}ms more, moved from ${was.pos} to $pos")
              check(was.rootedUntil <= now, s"$who, rooted for ${was.rootedUntil - now}ms more, moved from ${was.pos} to $pos")
            }
            if (!was.dead && p.isDead) { deathsSeen(p.getId) += 1; tally("deaths") += 1 }
            check(gi.killTracker.getDeaths(p.getId) == deathsSeen(p.getId),
              s"$who has died ${deathsSeen(p.getId)} times and been scored ${gi.killTracker.getDeaths(p.getId)}")
            if (pos != was.pos) tally("steps") += 1
            seen(p.getId) = Seen(pos, p.isDead, p.getFrozenUntil, p.getRootedUntil, p.getServerMoves)
          }
          // Every kill is somebody's death: nobody dies of nothing, and nobody is killed twice
          val board = gi.killTracker.getScoreboard
          check(board.map(_._2).sum == board.map(_._3).sum, s"${board.map(_._2).sum} kills and ${board.map(_._3).sum} deaths")
          if (teams) {
            def of(team: Int)(pick: ((UUID, Int, Int)) => Int): Int =
              board.filter(r => gi.teamAssignments.get(r._1) == team.toByte).map(pick).sum
            check(of(1)(_._2) == of(2)(_._3) && of(2)(_._2) == of(1)(_._3), "a team's kills aren't the other's deaths")
          }

          // The opening: a free-for-all holds everyone's fire, Teams keeps each team to its half
          val shots = gi.projectileManager.getAll
          if (at < opening) {
            for (p <- everyone) check(p.getHealth == p.getMaxHealth, s"${p.getName} was hurt in the opening (${p.getHealth} of ${p.getMaxHealth})")
            if (teams) {
              check(gi.divider != null && gi.divider.up, "the wall is down before the opening is over")
              for (p <- everyone) check(TeamDivider.sideOf(world, p.getPosition.getX, p.getPosition.getY) == TeamDivider.sideOfTeam(p.getTeamId),
                s"${p.getName} of team ${p.getTeamId} is at ${p.getPosition}, not in their half")
              for (s <- shots) check(gi.divider.side(s.getCellX, s.getCellY) == TeamDivider.sideOfTeam(gi.teamAssignments.get(s.ownerId)) ||
                gi.divider.side(s.getCellX, s.getCellY) == 0, s"a shot of team ${gi.teamAssignments.get(s.ownerId)}'s is across the wall at (${s.getX}, ${s.getY})")
            } else {
              check(gi.attacksLocked, "fire isn't held in a free-for-all's opening")
              check(shots.isEmpty, s"${shots.size} shots in flight in the ceasefire")
              check(gi.trapManager.size == 0, "a trap down in the ceasefire")
              for (p <- everyone) check(!p.hasBarrier && !p.isPhased, s"${p.getName} cast in the ceasefire")
            }
          } else if (what == "projectiles") {
            // (The projectile tick is what ends it: by the first one past its time it is over)
            check(!gi.attacksLocked && !gi.inOpening, "the opening outlasted its thirty seconds")
            check(gi.divider == null, "the wall outlasted the opening")
          }

          // No shot outlives the flight its range allows (one fired with no heading sat where it was
          // for the rest of the match, hitting whoever walked into it)
          for (s <- shots) {
            val since = firstSeen.getOrElseUpdate(s.id, at)
            val d = ProjectileDef.get(s.projectileType)
            val speed = d.effectiveSpeed(s.chargeLevel)
            val ticks = if (speed <= 0f) 1.0 else Math.ceil(d.effectiveMaxRange(s.chargeLevel) / speed) + 2
            val longest = (ticks * (if (d.boomerang) 2 else 1) * Constants.PROJECTILE_SPEED_MS).toLong + 2 * Constants.PROJECTILE_SPEED_MS
            check(at - since <= longest, s"projectile ${s.id} (${d.name}) is still in flight after ${at - since}ms, of $longest at most")
            tally("most shots in flight") = Math.max(tally("most shots in flight"), shots.size)
          }
          if (firstSeen.size > 4096) firstSeen.filterInPlace((id, _) => shots.exists(_.id == id))
          tally("shots") = Math.max(tally("shots"), shots.map(_.id).maxOption.getOrElse(0))
          tally("traps") = Math.max(tally("traps"), gi.trapManager.getAll.map(_.id).maxOption.getOrElse(0))
          tally("fence cells") = gi.modifiedTiles.size

          // The clock everyone is told, to the second: counted down from the match's length
          check(gi.getRemainingSeconds == Math.ceil((Minutes * 60000L - at) / 1000.0).toInt,
            s"${gi.getRemainingSeconds}s left on the match's clock")
          lastState = stateOf(gi, now)
          states += lastState.hashCode
        } else {
          check(at == Minutes * 60000L, "the match ended at another time than its own")
          check(!gi.isRunning, "the match is still running after its end")
          check(server.lobbyManager.getLobby(lobby.id) == null, "the lobby outlived its match")
        }
      }

      clockwork.advance(Minutes * 60000L + 5000)
      if (!clockwork.isOver) fail(seed, map, clockwork.at, "the match never ended")
      for (k <- Seq("projectiles", "players", "bots", "items", "respawn")) tally(s"ticks: $k") = clockwork.ran(k)
    })
    for (line <- errors.linesIterator)
      if (line.contains(" error:") || line.contains("Error in") || line.contains("Exception") || line.contains("weren't all recorded"))
        throw new AssertionError(s"seed $seed on $map: the match printed: $line")
    new Played(states.toArray, tally.toMap, standings, lastState)
  }

  /** The same match again: with its seed it is the same match, state for state after every tick. */
  private def playsTheSameAgain(first: Played, seed: Long, mapIndex: Int, teams: Boolean, characters: Seq[CharacterDef]): Unit = {
    val again = play(seed, mapIndex, teams, characters)
    val map = WorldRegistry.getFilename(mapIndex)
    assertEquals(s"seed $seed on $map: as many ticks, played again", first.states.length, again.states.length)
    val differs = first.states.indices.find(i => first.states(i) != again.states(i))
    if (differs.isDefined)
      throw new AssertionError(s"seed $seed on $map: played again, the match differs from its tick ${differs.get} of ${first.states.length} on")
    assertEquals(s"seed $seed on $map: the same standings", first.standings, again.standings)
    assertEquals(first.lastState, again.lastState)
  }

  private def report(what: String, played: Seq[Played]): Unit = {
    val keys = played.flatMap(_.tally.keys).distinct.sorted
    println(s"MatchSimTest $what: " + keys.map(k => s"$k ${played.map(_.tally.getOrElse(k, 0)).sum}").mkString(", "))
  }

  /** A match on every map, the first and the last of them played twice. */
  private def onEveryMap(teams: Boolean, firstSeed: Long, firstCast: Int): Seq[Played] =
    (0 until WorldRegistry.size).map { i =>
      val first = play(firstSeed + i, i, teams, cast(firstCast + i))
      if (i == 0 || i == WorldRegistry.size - 1) playsTheSameAgain(first, firstSeed + i, i, teams, cast(firstCast + i))
      first
    }

  @Test def aFreeForAllOnEveryMapKeepsItsRulesFromStartToEnd(): Unit = {
    val played = onEveryMap(teams = false, firstSeed = 100, firstCast = 0)
    report("free-for-alls", played)
    assertTrue("the bots fought", played.map(_.tally.getOrElse("deaths", 0)).sum > 0)
    assertTrue("and walked", played.forall(_.tally.getOrElse("steps", 0) > 100))
  }

  @Test def aTeamsMatchOnEveryMapKeepsItsRulesFromStartToEnd(): Unit = {
    val played = onEveryMap(teams = true, firstSeed = 200, firstCast = WorldRegistry.size)
    report("Teams matches", played)
    assertTrue("the bots fought", played.map(_.tally.getOrElse("deaths", 0)).sum > 0)
  }

  @Test def betweenThemTheMatchesPlayTheWholeRoster(): Unit =
    // Eight a match: a roster that has grown wants another match here
    assertEquals(CharacterDef.all.map(_.id.id).toSet, (0 until 2 * WorldRegistry.size).flatMap(cast).map(_.id.id).toSet)

  @Test def anotherSeedIsAnotherMatch(): Unit = {
    // The seed is what the match is played from: were it not, playing one twice would prove nothing
    val a = play(1, 4, teams = false, cast(0))
    val b = play(2, 4, teams = false, cast(0))
    assertFalse(a.states.sameElements(b.states))
  }

  /** A match all of whose choices come from its seed: the map, the mode and who plays. */
  private def matchOf(seed: Long): Played = {
    val rnd = new scala.util.Random(seed)
    val mapIndex = rnd.nextInt(WorldRegistry.size)
    val teams = rnd.nextBoolean()
    play(seed, mapIndex, teams, Seq.fill(Bots)(CharacterDef.all(rnd.nextInt(CharacterDef.all.size))))
  }

  /**
   * More matches, for as long as asked: `--test_env=GRIDGAME_SIM_SOAK_SECONDS=600` plays new seeds
   * for ten minutes (about a thousand matches), and `--test_env=GRIDGAME_SIM_SEED=<seed>` plays the
   * one a soak's failure names, again and the same.
   */
  @Test def moreMatchesForAsLongAsAsked(): Unit = {
    Option(System.getenv("GRIDGAME_SIM_SEED")).foreach(s => matchOf(s.toLong))
    val seconds = Option(System.getenv("GRIDGAME_SIM_SOAK_SECONDS")).map(_.toInt).getOrElse(0)
    if (seconds > 0) {
      val until = System.nanoTime() + seconds * 1000000000L
      var seed = System.currentTimeMillis()
      val played = mutable.Buffer[Played]()
      while (System.nanoTime() < until) { played += matchOf(seed); seed += 1 }
      report(s"soak of ${played.size} matches", played.toSeq)
    }
  }
}
