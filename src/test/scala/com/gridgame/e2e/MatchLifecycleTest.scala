package com.gridgame.e2e

import com.gridgame.client.game.ClientState
import com.gridgame.common.model._
import org.junit.Assert._
import org.junit.Test


/**
 * Matches from the login screen to the scoreboard and back, through the real client and the real
 * server, with every rule of a strict loopback held at every step (Loopback): signing up, lobbies
 * made and joined, characters picked, a free-for-all and a Teams match with their openings, played by
 * the players' own hands, ended, and their standings sent to everyone, saved and shown, and a match
 * after a match. SessionLifecycleTest has ranked play, leaving, and losing the connection.
 */
class MatchLifecycleTest {
  // Accounts outlive a run in the test's database: names of this run's own
  private val run = (System.nanoTime() % 100000).abs

  /** Everyone's hands on the keyboard for `ms`, then off, and everything said arrived. */
  private def play(loop: Loopback, players: Seq[LoopClient], ms: Long): Unit = {
    val hands = players.map(c => c -> new Persona(c, loop.rnd)).toMap
    loop.run(ms)(c => hands.get(c).foreach(_.act()))
    hands.values.foreach(_.letGo())
    loop.settle()
  }

  private def report(loop: Loopback, what: String): Unit =
    loop.say(s"MatchLifecycleTest $what: ${loop.tally.toSeq.filterNot(_._1.startsWith("sent ")).sortBy(_._1)
      .map { case (k, v) => s"$k $v" }.mkString(", ")}")

  @Test def aFreeForAllFromSignUpToTheScoreboard(): Unit = Loopback(seed = 1) { loop =>
    loop.openingMs = 400
    val alice = loop.connect(s"alice$run").signUp()
    val bob = loop.connect(s"bob$run").signUp()
    alice.pick(CharacterId.Gladiator)
    val lobbyId = alice.createLobby(mapIndex = 0)
    bob.joinLobby(lobbyId)
    bob.pick(CharacterId.Wizard)

    alice.client.startGame()
    loop.await("the match to start")(alice.playing && bob.playing &&
      alice.client.getPlayers.containsKey(bob.id) && bob.client.getPlayers.containsKey(alice.id))
    assertEquals("the character the lobby has it play", CharacterId.Wizard.id, bob.client.selectedCharacterId)
    // The ceasefire is heard with the start, before anyone can act
    assertTrue("the opening's ceasefire", alice.client.attacksLocked && bob.client.attacksLocked)
    loop.await("the opening to end")(!alice.client.attacksLocked && !bob.client.attacksLocked)
    play(loop, Seq(alice, bob), 2500)

    val results = loop.endMatch(lobbyId)
    assertEquals(Set(alice.id, bob.id), results.map(_.playerId).toSet)
    // Saved for each of them, as their profile shows it
    for (c <- Seq(alice, bob)) {
      val mine = results.find(_.playerId == c.id).get
      val (_, _, _, _, kills, deaths, rank, _, _) = loop.server.authDatabase.getMatchHistory(c.id).head
      assertEquals(s"${c.name}'s saved match", (mine.kills, mine.deaths, mine.rank), (kills, deaths, rank))
    }
    // And back to the lobby browser, as the scoreboard's button does, for another match
    Seq(alice, bob).foreach(_.client.returnToLobbyBrowser())
    loop.settle()
    assertEquals(ClientState.LOBBY_BROWSER, alice.client.clientState)
    val again = bob.createLobby(mapIndex = 6)
    alice.joinLobby(again)
    bob.client.startGame()
    loop.await("the second match")(alice.playing && bob.playing && alice.client.getPlayers.containsKey(bob.id))
    // Nothing of the first match carried over: everyone whole, nothing on the ground they had heard of
    assertEquals(0, alice.client.killCount + alice.client.deathCount)
    play(loop, Seq(alice, bob), 1000)
    loop.endMatch(again)
    report(loop, "free-for-all")
  }

  @Test def aTeamsMatchOpensWithTheWallUpAndEachTeamInItsHalf(): Unit = Loopback(seed = 2) { loop =>
    loop.openingMs = 1200
    val players = loop.logIn(Seq.tabulate(4)(i => loop.connect(s"team$i$run")))
    players.zipWithIndex.foreach { case (c, i) => c.pick(CharacterDef.all((i * 29 + 5) % CharacterDef.all.size).id) }
    val host = players.head
    val lobbyId = host.createLobby(mapIndex = 6, mode = 1, teamSize = 2)
    loop.joinLobby(players.tail, lobbyId)
    host.client.startGame()
    loop.await("the match")(players.forall(c => c.playing && c.client.getPlayers.size == 3))
    val gi = loop.matchOf(host)

    // The wall stands on the server and on every client, and each team starts on its own side of it
    assertNotNull("the server's wall", gi.divider)
    for (c <- players) {
      assertTrue(s"${c.name}'s wall", c.client.divider != null && c.client.divider.up)
      val at = c.client.getLocalPosition
      assertEquals(s"${c.name}'s half", TeamDivider.sideOfTeam(c.client.localTeamId), TeamDivider.sideOf(gi.world, at.getX, at.getY))
    }
    assertEquals("two to a team", Seq(2, 2), players.groupBy(_.client.localTeamId).values.map(_.size).toSeq)

    // Played at the wall (everyone walks at the nearest foe, across it): no step of theirs into it, and
    // nothing the server refuses
    play(loop, players, 700)
    loop.await("the wall to come down")(gi.divider == null && players.forall(c => c.client.divider == null || !c.client.divider.up))
    play(loop, players, 1800)

    val results = loop.endMatch(lobbyId)
    for ((team, members) <- results.groupBy(_.teamId))
      assertEquals(s"team $team's members share its place", 1, members.map(_.rank).distinct.size)
    report(loop, "teams")
  }
}
