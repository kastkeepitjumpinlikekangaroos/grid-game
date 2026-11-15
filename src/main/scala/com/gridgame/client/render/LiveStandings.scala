package com.gridgame.client.render

import com.gridgame.client.game.GameClient
import com.gridgame.common.model.{MatchResults, Player}

import java.util.UUID

/**
 * Where everyone stands in the match being played, for the scoreboard Tab holds up
 * (hud/ScoreboardPainter): us, everyone else, and anyone who has left (the match's end still
 * scores them), each with their kills and deaths so far as the client has heard them
 * (MatchProgress.killsOf / deathsOf), placed as the match's end will place them (MatchResults): on
 * kills less deaths, then kills, identical lines sharing a place; in Teams by the team's total,
 * every member carrying their team's place, grouped by team, best team first.
 *
 * The rows are kept in arrays and rebuilt in place ([[gather]] then [[place]]), allocating nothing
 * but the walk over the client's maps; [[ScoreboardPainter]] rebuilds them only when a kill, a death,
 * someone joining or leaving changes them, and once a second for the rest. Whether each is dead, and
 * their name, are read from their Player as the board is drawn. Render thread only.
 */
final class LiveStandings(val capacity: Int = 64) {
  import LiveStandings._

  /** The rows, in the order shown. */
  var count = 0
  val ids = new Array[UUID](capacity)
  /** Their Player, or null for us (the client keeps us apart). */
  val players = new Array[Player](capacity)
  val characters = new Array[Byte](capacity)
  val teams = new Array[Byte](capacity)
  val kills = new Array[Int](capacity)
  val deaths = new Array[Int](capacity)
  val isLocal = new Array[Boolean](capacity)
  val hasLeft = new Array[Boolean](capacity)
  /** The place each finishes in as things stand: their own, or in Teams their team's. */
  val ranks = new Array[Int](capacity)

  /** Whether anyone is on a team: the board is then a section a team. */
  var teamMode = false
  /** The teams, best first: each one's id, its members' kills, its score (their scores added up),
    * its place, and how many of the rows are its. */
  var teamCount = 0
  val teamIds = new Array[Byte](MaxTeams)
  val teamKills = new Array[Int](MaxTeams)
  val teamScores = new Array[Int](MaxTeams)
  val teamRanks = new Array[Int](MaxTeams)
  val teamSizes = new Array[Int](MaxTeams)

  def score(i: Int): Int = MatchResults.score(kills(i), deaths(i))

  // ── Gathered from the client ───────────────────────────────────────────

  /** Everyone in the client's match, unplaced: us first, then everyone else, then anyone who left.
    * Then [[place]] puts them in order. */
  def gather(client: GameClient): Unit = {
    count = 0
    val me = client.getLocalPlayerId
    if (me != null) add(me, null, client.selectedCharacterId, client.localTeamId, client.killCount, client.deathCount,
      local = true, left = false)
    val it = client.getPlayers.values().iterator()
    while (it.hasNext) {
      val p = it.next()
      add(p.getId, p, p.getCharacterId, p.getTeamId, client.killsOf(p.getId), client.deathsOf(p.getId), local = false, left = false)
    }
    val gone = client.getDepartedPlayers.values().iterator()
    while (gone.hasNext) {
      val p = gone.next()
      add(p.getId, p, p.getCharacterId, p.getTeamId, client.killsOf(p.getId), client.deathsOf(p.getId), local = false, left = true)
    }
  }

  /** One row more, unplaced (a test's, or [[gather]]'s). Rows past the capacity are left off. */
  def add(id: UUID, player: Player, character: Byte, team: Byte, k: Int, d: Int, local: Boolean, left: Boolean): Unit = {
    if (count >= capacity) return
    ids(count) = id; players(count) = player; characters(count) = character; teams(count) = team
    kills(count) = k; deaths(count) = d; isLocal(count) = local; hasLeft(count) = left
    count += 1
  }

  def clear(): Unit = {
    var i = 0
    while (i < count) { ids(i) = null; players(i) = null; i += 1 }
    count = 0
    teamCount = 0
    teamMode = false
  }

  // ── Placed ─────────────────────────────────────────────────────────────

  // The order, as row indices, and the rows' fields copied out to be put back in it
  private val order = new Array[Int](capacity)
  private val scratchIds = new Array[UUID](capacity)
  private val scratchPlayers = new Array[Player](capacity)
  private val scratchBytes = new Array[Byte](capacity)
  private val scratchInts = new Array[Int](capacity)
  private val scratchFlags = new Array[Boolean](capacity)
  // Each row's team's place while sorting
  private val rowTeamRank = new Array[Int](capacity)

  /** Put the rows in the order the board shows them, each with its place: MatchResults.rankFfa's
    * order and places, or in Teams rankTeams'. Rows that tie exactly keep the order they were
    * gathered in. */
  def place(): Unit = {
    teamMode = false
    var i = 0
    while (i < count) { if (teams(i) != 0) teamMode = true; i += 1 }

    // The teams: each one's kills and score, then its place among them
    teamCount = 0
    if (teamMode) {
      i = 0
      while (i < count) {
        var t = 0
        while (t < teamCount && teamIds(t) != teams(i)) t += 1
        if (t == teamCount && teamCount < MaxTeams) {
          teamIds(t) = teams(i); teamKills(t) = 0; teamScores(t) = 0; teamSizes(t) = 0
          teamCount += 1
        }
        if (t < teamCount) {
          teamKills(t) += kills(i); teamScores(t) += score(i); teamSizes(t) += 1
        }
        i += 1
      }
      var t = 0
      while (t < teamCount) {
        var better = 0
        var u = 0
        while (u < teamCount) { if (teamScores(u) > teamScores(t)) better += 1; u += 1 }
        teamRanks(t) = 1 + better
        t += 1
      }
      i = 0
      while (i < count) { rowTeamRank(i) = teamRankOf(teams(i)); i += 1 }
    }

    // The order: an insertion sort (a match has a few players), which keeps ties as they came
    i = 0
    while (i < count) {
      val row = i
      var j = i - 1
      while (j >= 0 && before(row, order(j))) { order(j + 1) = order(j); j -= 1 }
      order(j + 1) = row
      i += 1
    }
    permute()

    // The places: a team's, or shared by identical lines and the next ones skipped (1, 2, 2, 4)
    i = 0
    while (i < count) {
      ranks(i) =
        if (teamMode) teamRankOf(teams(i))
        else if (i > 0 && score(i) == score(i - 1) && kills(i) == kills(i - 1)) ranks(i - 1)
        else i + 1
      i += 1
    }
    // and the teams best first, as their rows are
    if (teamMode) sortTeams()
  }

  /** Whether row `a` goes above row `b`. */
  private def before(a: Int, b: Int): Boolean = {
    if (teamMode) {
      if (rowTeamRank(a) != rowTeamRank(b)) return rowTeamRank(a) < rowTeamRank(b)
      if (teams(a) != teams(b)) return teams(a) < teams(b)
    }
    val sa = score(a); val sb = score(b)
    if (sa != sb) sa > sb else kills(a) > kills(b)
  }

  private def teamRankOf(team: Byte): Int = {
    var t = 0
    while (t < teamCount) { if (teamIds(t) == team) return teamRanks(t); t += 1 }
    0
  }

  /** Every row's fields put in `order`, through the scratch arrays. */
  private def permute(): Unit = {
    def ints(a: Array[Int]): Unit = {
      var i = 0; while (i < count) { scratchInts(i) = a(order(i)); i += 1 }
      System.arraycopy(scratchInts, 0, a, 0, count)
    }
    def bytes(a: Array[Byte]): Unit = {
      var i = 0; while (i < count) { scratchBytes(i) = a(order(i)); i += 1 }
      System.arraycopy(scratchBytes, 0, a, 0, count)
    }
    def flags(a: Array[Boolean]): Unit = {
      var i = 0; while (i < count) { scratchFlags(i) = a(order(i)); i += 1 }
      System.arraycopy(scratchFlags, 0, a, 0, count)
    }
    var i = 0
    while (i < count) { scratchIds(i) = ids(order(i)); scratchPlayers(i) = players(order(i)); i += 1 }
    System.arraycopy(scratchIds, 0, ids, 0, count)
    System.arraycopy(scratchPlayers, 0, players, 0, count)
    i = 0
    while (i < count) { scratchIds(i) = null; scratchPlayers(i) = null; i += 1 }
    bytes(characters); bytes(teams)
    ints(kills); ints(deaths); ints(rowTeamRank)
    flags(isLocal); flags(hasLeft)
  }

  /** The teams in the order their rows are in: by place, then id. */
  private def sortTeams(): Unit = {
    var i = 1
    while (i < teamCount) {
      val id = teamIds(i); val k = teamKills(i); val s = teamScores(i); val r = teamRanks(i); val n = teamSizes(i)
      var j = i - 1
      while (j >= 0 && (teamRanks(j) > r || (teamRanks(j) == r && teamIds(j) > id))) {
        teamIds(j + 1) = teamIds(j); teamKills(j + 1) = teamKills(j); teamScores(j + 1) = teamScores(j)
        teamRanks(j + 1) = teamRanks(j); teamSizes(j + 1) = teamSizes(j)
        j -= 1
      }
      teamIds(j + 1) = id; teamKills(j + 1) = k; teamScores(j + 1) = s; teamRanks(j + 1) = r; teamSizes(j + 1) = n
      i += 1
    }
  }

  /** Whether every team has the same total: a draw, as things stand. */
  def teamsLevel: Boolean = teamCount > 1 && {
    var t = 1
    var level = true
    while (t < teamCount) { if (teamScores(t) != teamScores(0)) level = false; t += 1 }
    level
  }
}

object LiveStandings {
  /** At most this many teams are told apart (a match has two). */
  final val MaxTeams = 8
}
