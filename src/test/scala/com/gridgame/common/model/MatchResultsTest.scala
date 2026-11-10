package com.gridgame.common.model

import org.junit.Assert._
import org.junit.Test

import java.util.UUID

class MatchResultsTest {

  private def id(n: Int): UUID = new UUID(42L, n.toLong)

  private def ranks(results: Seq[PlayerResult]): Map[UUID, Int] =
    results.map(r => r.playerId -> r.rank).toMap

  @Test
  def aMatchIsWonOnKillsLessDeaths(): Unit = {
    // The most kills is not the win: 3 kills for no deaths beats 10 kills for 8 deaths
    val results = MatchResults.rankFfa(Seq((id(1), 10, 8), (id(2), 3, 0), (id(3), 6, 6)))
    assertEquals(Seq(id(2), id(1), id(3)), results.map(_.playerId))
    assertEquals(Seq(1, 2, 3), results.map(_.rank))
    assertEquals(Seq(3, 2, 0), results.map(_.score))
  }

  @Test
  def aDeathCostsWhatAKillEarns(): Unit = {
    // One more kill and one more death leave the line where it was, whatever its kills
    assertEquals(MatchResults.score(4, 1), MatchResults.score(5, 2))
    assertEquals(-3, MatchResults.score(0, 3))
  }

  @Test
  def ffaLevelOnScoreGoesToMoreKills(): Unit = {
    val results = MatchResults.rankFfa(Seq((id(1), 5, 3), (id(2), 4, 0), (id(3), 9, 5)))
    assertEquals(Seq(id(3), id(2), id(1)), results.map(_.playerId))
    assertEquals(Seq(1, 2, 3), results.map(_.rank))
  }

  @Test
  def ffaIdenticalLinesSharePlaceAndSkipNext(): Unit = {
    val results = MatchResults.rankFfa(Seq((id(1), 9, 11), (id(2), 19, 12), (id(3), 9, 11), (id(4), 4, 18)))
    assertEquals(Map(id(2) -> 1, id(1) -> 2, id(3) -> 2, id(4) -> 4), ranks(results))
  }

  @Test
  def teamsRankByTheirMembersScoresAddedUp(): Unit = {
    // The reported screenshot: 6 players split 3v3. Team 2 has 39 kills and 37 deaths (+2), team 1
    // 35 and 37 (-2). Each team's members are listed by their own score: 16-7 above 19-12.
    val scores = Seq((id(1), 19, 12), (id(2), 17, 9), (id(3), 16, 7), (id(4), 9, 11), (id(5), 9, 17), (id(6), 4, 18))
    val teamOf = Map(id(1) -> 2.toByte, id(2) -> 1.toByte, id(3) -> 2.toByte, id(4) -> 1.toByte, id(5) -> 1.toByte, id(6) -> 2.toByte)
    val results = MatchResults.rankTeams(scores, teamOf)

    assertEquals(Seq(id(3), id(1), id(6), id(2), id(4), id(5)), results.map(_.playerId))
    assertEquals(Seq(1, 1, 1, 2, 2, 2), results.map(_.rank))
    assertEquals(Seq(2, 2, 2, 1, 1, 1).map(_.toByte), results.map(_.teamId))
  }

  @Test
  def aTeamsDeathsCountAgainstIt(): Unit = {
    // More kills, and more deaths still: 10 - 9 loses to 8 - 2
    val teamOf = Map(id(1) -> 1.toByte, id(2) -> 1.toByte, id(3) -> 2.toByte)
    val results = MatchResults.rankTeams(Seq((id(1), 6, 4), (id(2), 4, 5), (id(3), 8, 2)), teamOf)
    assertEquals(Map(id(3) -> 1, id(1) -> 2, id(2) -> 2), ranks(results))
  }

  @Test
  def teamsDrawIsFirstPlaceForEveryone(): Unit = {
    val teamOf = Map(id(1) -> 1.toByte, id(2) -> 2.toByte)
    val results = MatchResults.rankTeams(Seq((id(1), 7, 5), (id(2), 5, 3)), teamOf)
    assertEquals(Seq(1, 1), results.map(_.rank))
  }

  @Test
  def eloWinnerGainsWhatLoserLosesInDuel(): Unit = {
    val results = MatchResults.rankFfa(Seq((id(1), 5, 1), (id(2), 1, 5)))
    val changes = MatchResults.eloChanges(results.map(r => (r, 1000)))
    assertEquals((1000, 1016), changes(id(1)))
    assertEquals((1000, 984), changes(id(2)))
  }

  /** What a duel between these two ratings moves each by, the first of them winning (or neither). */
  private def duel(first: Int, second: Int, drawn: Boolean = false): (Int, Int) = {
    val a = PlayerResult(id(1), 1, 0, 1)
    val b = PlayerResult(id(2), 0, 1, if (drawn) 1 else 2)
    val changes = MatchResults.eloChanges(Seq((a, first), (b, second)))
    assertEquals((first, second), (changes(id(1))._1, changes(id(2))._1))
    (changes(id(1))._2 - first, changes(id(2))._2 - second)
  }

  @Test
  def eloMovesByHowUnexpectedTheResultWas(): Unit = {
    // The usual expectation: a rating 200 better is expected to win three in four, 400 better ten
    // in eleven, and 32 points are played for. Beating whom one was expected to beat earns little,
    // an upset a lot, and what one gains the other loses
    assertEquals("level", (16, -16), duel(1000, 1000))
    assertEquals("the favourite by 200 wins", (8, -8), duel(1200, 1000))
    assertEquals("and loses", (24, -24), duel(1000, 1200))
    assertEquals("the favourite by 400 wins", (3, -3), duel(1400, 1000))
    assertEquals("and loses", (29, -29), duel(1000, 1400))
    assertEquals("by 800: next to nothing to gain", (0, 0), duel(1800, 1000))
    assertEquals("and nearly all of it to lose", (32, -32), duel(1000, 1800))
    assertEquals("a draw costs the favourite what an even match would not", (-8, 8), duel(1200, 1000, drawn = true))
  }

  @Test
  def inAFullLobbyTheSamePointsAreSharedAmongTheOpponents(): Unit = {
    // Each place against each other place, the 32 split across the three faced: first of four
    // level players gains what a duel's winner does, and the middle two a third of it
    val results = (1 to 4).map(n => (PlayerResult(id(n), 5 - n, n, n), 1000))
    val changes = MatchResults.eloChanges(results)
    assertEquals(Seq(16, 5, -5, -16), (1 to 4).map(n => changes(id(n))._2 - 1000))
    // And unequal: the lowest rated of four winning it takes more than the highest would
    val upset = MatchResults.eloChanges(Seq((PlayerResult(id(1), 3, 0, 1), 800), (PlayerResult(id(2), 2, 1, 2), 1200),
      (PlayerResult(id(3), 1, 2, 3), 1200), (PlayerResult(id(4), 0, 3, 4), 1200)))
    val favourite = MatchResults.eloChanges(Seq((PlayerResult(id(1), 3, 0, 1), 1200), (PlayerResult(id(2), 2, 1, 2), 800),
      (PlayerResult(id(3), 1, 2, 3), 800), (PlayerResult(id(4), 0, 3, 4), 800)))
    assertEquals("ten in eleven against, three times over", 29, upset(id(1))._2 - 800)
    assertEquals(3, favourite(id(1))._2 - 1200)
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
