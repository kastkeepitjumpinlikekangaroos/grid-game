package com.gridgame.client.render

import com.gridgame.client.TestClient
import com.gridgame.common.model.{MatchResults, PlayerResult}
import com.gridgame.common.protocol.GameEvent
import org.junit.Assert._
import org.junit.Test

import java.lang.management.ManagementFactory
import java.util.UUID

/**
 * The scoreboard Tab holds up in a match (LiveStandings): everyone's kills and deaths as the match's
 * KILLs have told them (MatchProgress.killsOf / deathsOf: the killer's own count, a death more for
 * whoever they killed), everyone in it gathered, us and anyone who left too, and placed exactly as the
 * match's end will place them (MatchResults.rankFfa and rankTeams, which the server scores with),
 * putting them in order allocating nothing.
 */
class LiveStandingsTest {

  // --- What the KILLs tell ---

  @Test def everyonesKillsAndDeathsAreWhatTheKillsTold(): Unit = {
    val t = new TestClient()
    val c = t.client
    val (a, b) = (UUID.randomUUID(), UUID.randomUUID())
    t.startMatch(spawn = (20, 20), others = Seq(a -> (25, 20), b -> (30, 20)))
    val before = c.talliesChanged
    // A killed B: A's own count, as the server keeps it, and a death for B
    t.gameEvent(GameEvent.KILL, who = a, target = b, kills = 1, deaths = 0)
    assertEquals((1, 0), (c.killsOf(a), c.deathsOf(a)))
    assertEquals((0, 1), (c.killsOf(b), c.deathsOf(b)))
    assertNotEquals("whoever shows them is told", before, c.talliesChanged)
    // B, back, killed A: B's count says B died once
    t.gameEvent(GameEvent.KILL, who = b, target = a, kills = 1, deaths = 1)
    assertEquals((1, 1), (c.killsOf(b), c.deathsOf(b)))
    assertEquals((1, 1), (c.killsOf(a), c.deathsOf(a)))
    // We killed A: ours, as the HUD has them too, and A's second death
    t.gameEvent(GameEvent.KILL, who = t.id, target = a, kills = 1, deaths = 0)
    assertEquals((1, 0), (c.killsOf(t.id), c.deathsOf(t.id)))
    assertEquals((c.killCount, c.deathCount), (c.killsOf(t.id), c.deathsOf(t.id)))
    assertEquals(2, c.deathsOf(a))
    // A killer's count is the server's, whatever we missed of it
    t.gameEvent(GameEvent.KILL, who = a, target = t.id, kills = 7, deaths = 4)
    assertEquals((7, 4), (c.killsOf(a), c.deathsOf(a)))
    assertEquals((1, 1), (c.killsOf(t.id), c.deathsOf(t.id)))
    // A new match forgets them all
    t.startMatch(spawn = (20, 20))
    assertEquals((0, 0), (c.killsOf(a), c.deathsOf(a)))
    assertEquals(0, c.deathsOf(t.id))
  }

  @Test def everyoneIsGatheredUsAndAnyoneWhoLeftToo(): Unit = {
    val t = new TestClient()
    val c = t.client
    val (a, b) = (UUID.randomUUID(), UUID.randomUUID())
    t.startMatch(spawn = (20, 20), others = Seq(a -> (25, 20), b -> (30, 20)))
    t.gameEvent(GameEvent.KILL, who = b, target = a, kills = 2, deaths = 0)
    t.leave(b)
    val s = new LiveStandings()
    s.gather(c)
    s.place()
    assertEquals(3, s.count)
    val row = (0 until s.count).map(i => s.ids(i) -> i).toMap
    assertTrue("us", s.isLocal(row(t.id)))
    assertNull("we are no Player of the client's", s.players(row(t.id)))
    assertTrue("B left, and is still on it, as the match's end still scores them", s.hasLeft(row(b)))
    assertEquals((2, 0), (s.kills(row(b)), s.deaths(row(b))))
    assertEquals("and first, on +2", 0, row(b))
    assertEquals(Seq(1, 2, 3), (0 until s.count).map(s.ranks))
    assertFalse("nobody is on a team", s.teamMode)
  }

  // --- Placed as the match's end will place them ---

  private def standingsOf(lines: Seq[(UUID, Int, Int, Byte)]): LiveStandings = {
    val s = new LiveStandings()
    lines.foreach { case (id, k, d, team) => s.add(id, null, 0.toByte, team, k, d, local = false, left = false) }
    s.place()
    s
  }

  @Test def aFreeForAllIsPlacedAsTheMatchsEndPlacesIt(): Unit = {
    val rnd = new scala.util.Random(11)
    for (trial <- 0 until 400) {
      val lines = (0 until 1 + rnd.nextInt(12)).map(_ => (UUID.randomUUID(), rnd.nextInt(7), rnd.nextInt(7), 0.toByte))
      val s = standingsOf(lines)
      val expected: Map[UUID, PlayerResult] = MatchResults.rankFfa(lines.map(l => (l._1, l._2, l._3))).map(r => r.playerId -> r).toMap
      assertEquals(lines.size, s.count)
      for (i <- 0 until s.count) {
        assertEquals(s"trial $trial, row $i: the place", expected(s.ids(i)).rank, s.ranks(i))
        assertEquals(expected(s.ids(i)).kills, s.kills(i))
        assertEquals(expected(s.ids(i)).deaths, s.deaths(i))
        if (i > 0) assertTrue(s"trial $trial: better lines first",
          s.score(i - 1) > s.score(i) || (s.score(i - 1) == s.score(i) && s.kills(i - 1) >= s.kills(i)))
      }
    }
  }

  @Test def aTeamsMatchIsPlacedAsTheMatchsEndPlacesIt(): Unit = {
    val rnd = new scala.util.Random(12)
    for (trial <- 0 until 400) {
      val lines = (0 until 2 + rnd.nextInt(10)).map(_ =>
        (UUID.randomUUID(), rnd.nextInt(6), rnd.nextInt(6), (1 + rnd.nextInt(2)).toByte))
      val teamOf = lines.map(l => l._1 -> l._4).toMap
      val s = standingsOf(lines)
      val expected = MatchResults.rankTeams(lines.map(l => (l._1, l._2, l._3)), teamOf)
      val byId = expected.map(r => r.playerId -> r).toMap
      assertTrue(s.teamMode)
      for (i <- 0 until s.count) assertEquals(s"trial $trial, row $i: the team's place", byId(s.ids(i)).rank, s.ranks(i))
      // The same order but for exact ties within a team, which neither side orders by anything
      def key(r: PlayerResult) = (r.rank, r.teamId, -r.score, -r.kills)
      assertEquals(s"trial $trial", expected.map(key), (0 until s.count).map(i => byId(s.ids(i))).map(key))
      // and each team's totals, best first
      for (t <- 0 until s.teamCount) {
        val members = lines.filter(_._4 == s.teamIds(t))
        assertEquals(members.map(_._2).sum, s.teamKills(t))
        assertEquals(members.map(l => l._2 - l._3).sum, s.teamScores(t))
        assertEquals(members.size, s.teamSizes(t))
        if (t > 0) assertTrue(s.teamRanks(t - 1) <= s.teamRanks(t))
      }
    }
  }

  @Test def aTeamLeadsOnItsKillsLessItsDeathsOrTheTeamsAreLevel(): Unit = {
    val (a1, a2, b1, b2) = (UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())
    val s = standingsOf(Seq((b1, 2, 3, 2.toByte), (a1, 3, 1, 1.toByte), (b2, 1, 1, 2.toByte), (a2, 2, 2, 1.toByte)))
    assertEquals(Seq(1, 2), (0 until s.teamCount).map(s.teamIds(_).toInt))
    assertEquals(Seq(5, 3), (0 until s.teamCount).map(s.teamKills))
    assertEquals(Seq(2, -1), (0 until s.teamCount).map(s.teamScores))
    assertEquals(Seq(1, 2), (0 until s.teamCount).map(s.teamRanks))
    assertFalse(s.teamsLevel)
    assertEquals("the leading team's rows first, its best first", Seq(a1, a2, b2, b1), (0 until s.count).map(s.ids))
    val level = standingsOf(Seq((a1, 2, 1, 1.toByte), (b1, 1, 0, 2.toByte)))
    assertTrue(level.teamsLevel)
    assertEquals(Seq(1, 1), (0 until level.count).map(level.ranks))
  }

  @Test def placingThemAllocatesNothing(): Unit = {
    val s = new LiveStandings()
    val rnd = new scala.util.Random(3)
    val ids = (0 until 16).map(_ => UUID.randomUUID())
    def fill(): Unit = {
      s.clear()
      var i = 0
      while (i < 16) {
        s.add(ids(i), null, 0.toByte, (1 + (i & 1)).toByte, rnd.nextInt(9), rnd.nextInt(9), local = i == 0, left = false)
        i += 1
      }
    }
    val bean = ManagementFactory.getThreadMXBean.asInstanceOf[com.sun.management.ThreadMXBean]
    val me = Thread.currentThread().getId
    // Warm, then measured: an empty loop's allocation is the floor
    for (_ <- 0 until 20000) { fill(); s.place() }
    val empty0 = bean.getThreadAllocatedBytes(me)
    for (_ <- 0 until 20000) fill()
    val empty = bean.getThreadAllocatedBytes(me) - empty0
    val placed0 = bean.getThreadAllocatedBytes(me)
    for (_ <- 0 until 20000) { fill(); s.place() }
    val placed = bean.getThreadAllocatedBytes(me) - placed0
    assertTrue(s"placing 16 rows 20,000 times allocated ${placed - empty} bytes", placed - empty < 64 * 1024)
  }
}
