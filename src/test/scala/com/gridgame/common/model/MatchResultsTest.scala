package com.gridgame.common.model

import org.junit.Assert._
import org.junit.Test

import java.util.UUID

class MatchResultsTest {

  private def id(n: Int): UUID = new UUID(42L, n.toLong)

  private def ranks(results: Seq[PlayerResult]): Map[UUID, Int] =
    results.map(r => r.playerId -> r.rank).toMap

  @Test
  def ffaRanksByKillsThenDeaths(): Unit = {
    val results = MatchResults.rankFfa(Seq((id(1), 5, 3), (id(2), 9, 1), (id(3), 5, 1)))
    assertEquals(Seq(id(2), id(3), id(1)), results.map(_.playerId))
    assertEquals(Seq(1, 2, 3), results.map(_.rank))
  }

  @Test
  def ffaIdenticalLinesSharePlaceAndSkipNext(): Unit = {
    val results = MatchResults.rankFfa(Seq((id(1), 9, 11), (id(2), 19, 12), (id(3), 9, 11), (id(4), 4, 18)))
    assertEquals(Map(id(2) -> 1, id(1) -> 2, id(3) -> 2, id(4) -> 4), ranks(results))
  }

  @Test
  def teamsRankByTeamTotalKills(): Unit = {
    // The reported screenshot: 6 players split 3v3. Team 2 has 19+16+4 = 39, team 1 has 17+9+9 = 35.
    val scores = Seq((id(1), 19, 12), (id(2), 17, 9), (id(3), 16, 7), (id(4), 9, 11), (id(5), 9, 17), (id(6), 4, 18))
    val teamOf = Map(id(1) -> 2.toByte, id(2) -> 1.toByte, id(3) -> 2.toByte, id(4) -> 1.toByte, id(5) -> 1.toByte, id(6) -> 2.toByte)
    val results = MatchResults.rankTeams(scores, teamOf)

    assertEquals(Seq(id(1), id(3), id(6), id(2), id(4), id(5)), results.map(_.playerId))
    assertEquals(Seq(1, 1, 1, 2, 2, 2), results.map(_.rank))
    assertEquals(Seq(2, 2, 2, 1, 1, 1).map(_.toByte), results.map(_.teamId))
  }

  @Test
  def teamsDrawIsFirstPlaceForEveryone(): Unit = {
    val teamOf = Map(id(1) -> 1.toByte, id(2) -> 2.toByte)
    val results = MatchResults.rankTeams(Seq((id(1), 7, 2), (id(2), 7, 5)), teamOf)
    assertEquals(Seq(1, 1), results.map(_.rank))
  }

  @Test
  def eloWinnerGainsWhatLoserLosesInDuel(): Unit = {
    val results = MatchResults.rankFfa(Seq((id(1), 5, 1), (id(2), 1, 5)))
    val changes = MatchResults.eloChanges(results.map(r => (r, 1000)))
    assertEquals((1000, 1016), changes(id(1)))
    assertEquals((1000, 984), changes(id(2)))
  }

  @Test
  def eloDrawBetweenEqualRatingsChangesNothing(): Unit = {
    val results = MatchResults.rankFfa(Seq((id(1), 3, 3), (id(2), 3, 3)))
    val changes = MatchResults.eloChanges(results.map(r => (r, 1200)))
    assertEquals((1200, 1200), changes(id(1)))
    assertEquals((1200, 1200), changes(id(2)))
  }

  @Test
  def eloIgnoresTeammates(): Unit = {
    // 2v2, equal ratings: every winner beats both opponents, every loser loses to both.
    // Scoring teammates as opponents used to cost the winners rating against each other.
    val teamOf = Map(id(1) -> 1.toByte, id(2) -> 1.toByte, id(3) -> 2.toByte, id(4) -> 2.toByte)
    val results = MatchResults.rankTeams(Seq((id(1), 6, 1), (id(2), 2, 3), (id(3), 3, 4), (id(4), 1, 4)), teamOf)
    val changes = MatchResults.eloChanges(results.map(r => (r, 1000)))
    assertEquals((1000, 1016), changes(id(1)))
    assertEquals((1000, 1016), changes(id(2)))
    assertEquals((1000, 984), changes(id(3)))
    assertEquals((1000, 984), changes(id(4)))
  }

  @Test
  def eloWithOnlyTeammatesRatedChangesNothing(): Unit = {
    val teamOf = Map(id(1) -> 1.toByte, id(2) -> 1.toByte)
    val results = MatchResults.rankTeams(Seq((id(1), 6, 1), (id(2), 2, 3)), teamOf)
    val changes = MatchResults.eloChanges(results.map(r => (r, 1000)))
    assertEquals((1000, 1000), changes(id(1)))
    assertEquals((1000, 1000), changes(id(2)))
  }

  @Test
  def eloNeverGoesNegative(): Unit = {
    val results = MatchResults.rankFfa(Seq((id(1), 5, 1), (id(2), 1, 5)))
    val changes = MatchResults.eloChanges(results.map(r => (r, 3)))
    assertEquals((3, 0), changes(id(2)))
  }
}
