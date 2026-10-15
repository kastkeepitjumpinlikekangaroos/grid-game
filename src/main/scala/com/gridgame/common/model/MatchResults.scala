package com.gridgame.common.model

import java.util.UUID

/** One player's final line in a finished match. `rank` is the finishing place; in a Teams
  * match it is the team's place, shared by every member of the team. */
final case class PlayerResult(playerId: UUID, kills: Int, deaths: Int, rank: Int, teamId: Byte = 0) {
  def score: Int = MatchResults.score(kills, deaths)
}

/** Final standings and rating changes for a finished match. Pure, so the server's
  * end-of-match path and the tests run exactly the same code. */
object MatchResults {

  /** What every mode of match is won on: kills less deaths, so a death costs as much as a kill
    * earns. Ranked on kills alone, dying cost nothing but the seconds before the respawn. */
  def score(kills: Int, deaths: Int): Int = kills - deaths

  /** Better line first: the higher score, then more kills. */
  private val byPerformance: Ordering[PlayerResult] =
    Ordering.by((r: PlayerResult) => (-r.score, -r.kills))

  /** FFA places from `(playerId, kills, deaths)`. Identical lines share a place and the
    * next place is skipped (1, 2, 2, 4): which of two tied players the scoreboard lists
    * first is not a result, and it decides wins and rating. */
  def rankFfa(scores: Seq[(UUID, Int, Int)]): Seq[PlayerResult] = {
    val sorted = scores.map { case (id, k, d) => PlayerResult(id, k, d, 0) }.sorted(byPerformance)
    var rank = 0
    sorted.zipWithIndex.map { case (r, i) =>
      if (i == 0 || byPerformance.compare(sorted(i - 1), r) != 0) rank = i + 1
      r.copy(rank = rank)
    }
  }

  /** Teams places. A team places by its members' scores added up, and teams with equal totals
    * share the place, so a drawn match is rank 1 for everyone. Nobody can hurt a teammate or
    * themselves, so every death on one side is a kill for the other, and a team's score is its
    * kills less the other team's: the same order its kills alone give. Rows come back grouped by
    * team, best team first, each team's members best first. */
  def rankTeams(scores: Seq[(UUID, Int, Int)], teamOf: UUID => Byte): Seq[PlayerResult] = {
    val rows = scores.map { case (id, k, d) => PlayerResult(id, k, d, 0, teamOf(id)) }
    val totals = rows.groupBy(_.teamId).map { case (team, members) => team -> members.map(_.score).sum }
    def teamRank(team: Byte): Int = 1 + totals.count(_._2 > totals(team))
    rows.map(r => r.copy(rank = teamRank(r.teamId)))
      .sortBy(r => (r.rank, r.teamId, -r.score, -r.kills))
  }

  /** Rating changes for the rated players of a match, as `playerId -> (oldElo, newElo)`.
    *
    * Each player is scored against every opponent: a better place is a win, the same place
    * a draw, a worse one a loss, against the usual logistic expectation. Teammates are not
    * opponents; scoring them as losses (they share your place) would cost the winning side
    * rating for winning. K is split across the opponents faced, so a full lobby moves a
    * rating no faster than a duel does. */
  def eloChanges(players: Seq[(PlayerResult, Int)], k: Double = 32.0): Map[UUID, (Int, Int)] = {
    players.map { case (me, myElo) =>
      val opponents = players.filter { case (other, _) =>
        other.playerId != me.playerId && (me.teamId == 0 || other.teamId != me.teamId)
      }
      val delta =
        if (opponents.isEmpty) 0.0
        else {
          val kEach = k / opponents.size
          opponents.map { case (other, otherElo) =>
            val expected = 1.0 / (1.0 + Math.pow(10, (otherElo - myElo) / 400.0))
            val actual = if (me.rank < other.rank) 1.0 else if (me.rank == other.rank) 0.5 else 0.0
            kEach * (actual - expected)
          }.sum
        }
      me.playerId -> ((myElo, Math.max(0, myElo + Math.round(delta).toInt)))
    }.toMap
  }
}
