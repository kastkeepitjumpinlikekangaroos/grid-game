package com.gridgame.e2e

import com.gridgame.client.game.ClientState
import com.gridgame.common.model._
import com.gridgame.common.protocol.RankedQueueMode
import com.gridgame.server.game.E2EServer
import org.junit.Assert._
import org.junit.Test

/**
 * A player's session around its matches, through the real client and the real server on a strict
 * loopback (Loopback): queueing for ranked and being found a match, rated by it and told so; leaving
 * a match and losing the connection mid-match, with the match going on without them; and logging
 * back in with the password afterwards.
 */
class SessionLifecycleTest {
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
    loop.say(s"SessionLifecycleTest $what: ${loop.tally.toSeq.filterNot(_._1.startsWith("sent ")).sortBy(_._1)
      .map { case (k, v) => s"$k $v" }.mkString(", ")}")

  @Test def aRankedDuelIsFoundPlayedAndRated(): Unit = Loopback(seed = 3) { loop =>
    loop.openingMs = 300
    val alice = loop.connect(s"rka$run").signUp()
    val bob = loop.connect(s"rkb$run").signUp()
    alice.pick(CharacterId.Samurai)
    bob.pick(CharacterId.Ranger)
    alice.client.queueRanked(RankedQueueMode.DUEL)
    bob.client.queueRanked(RankedQueueMode.DUEL)
    // Heard by the server, and the matchmaker's pass (every five seconds on the real server)
    loop.settle(50)
    E2EServer.checkRankedQueue(loop.server, System.currentTimeMillis())
    loop.await("the duel")(alice.playing && bob.playing && alice.client.getPlayers.containsKey(bob.id))
    assertEquals("the character it queued as", CharacterId.Ranger.id, bob.client.selectedCharacterId)
    val lobby = loop.lobbyOf(alice)
    assertTrue("ranked", lobby.isRanked)

    play(loop, Seq(alice, bob), 2500)
    val results = loop.endMatch(lobby.id)
    // Each rated as the server rated them, and told so before the scoreboard showed
    for (c <- Seq(alice, bob)) {
      val elo = loop.server.authDatabase.getEloByUUID(c.id)
      assertEquals(s"${c.name}'s rating as its client has it", elo, c.client.rankedElo)
      val mine = results.find(_.playerId == c.id).get
      val other = results.find(_.playerId != c.id).get
      if (mine.rank == other.rank) assertEquals(s"${c.name}: a draw moves nobody", 1000, elo)
      else {
        assertEquals(s"${c.name}: the winner up, the loser down", mine.rank < other.rank, elo > 1000)
        assertEquals(s"${c.name}'s change on its scoreboard", Some((1000, elo)), c.client.pendingEloChange)
      }
    }
    report(loop, "ranked duel")
  }

  @Test def aPlayerWhoLeavesOrIsCutOffIsGoneAndTheMatchGoesOn(): Unit = Loopback(seed = 4) { loop =>
    val players = Seq("stay", "quit", "drop", "also").map(n => loop.connect(s"$n$run").signUp())
    val Seq(stay, quit, drop, also) = players
    val lobbyId = stay.createLobby(mapIndex = 0)
    loop.joinLobby(players.tail, lobbyId)
    stay.client.startGame()
    loop.await("the match")(players.forall(c => c.playing && c.client.getPlayers.size == 3))
    play(loop, players, 800)

    // Esc, twice: out of the match and back in the lobby browser, free to play elsewhere
    quit.press(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE)
    quit.press(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE)
    loop.await("the leaver gone")(Seq(stay, drop, also).forall(c => !c.client.getPlayers.containsKey(quit.id)))
    assertEquals(ClientState.LOBBY_BROWSER, quit.client.clientState)
    assertNull("out of the lobby", loop.lobbyOf(quit))
    val elsewhere = quit.createLobby(mapIndex = 1)
    assertTrue(elsewhere != lobbyId)

    // The connection lost: the server takes the player out of everything, and the others see them go
    drop.serverEnd.close()
    drop.close()
    loop.await("the dropped player gone")(Seq(stay, also).forall(c => !c.client.getPlayers.containsKey(drop.id)))
    assertNull("not connected", E2EServer.connectedPlayer(loop.server, drop.id))
    play(loop, Seq(stay, also), 800)

    // Back with its password, on a connection of its own: in the lobby browser, its match gone
    val back = loop.connect(drop.name)
    back.client.sendAuthRequest(drop.name, "hunter22", isSignup = false)
    loop.await("the login")(back.loggedIn)
    assertEquals("the same account", drop.id, back.id)
    assertEquals(ClientState.LOBBY_BROWSER, back.client.clientState)
    assertNull("in no lobby", loop.lobbyOf(back))

    // The leavers are still scored
    val results = loop.endMatch(lobbyId)
    assertEquals(Set(stay.id, quit.id, drop.id, also.id), results.map(_.playerId).toSet)
    report(loop, "leaving")
  }
}
