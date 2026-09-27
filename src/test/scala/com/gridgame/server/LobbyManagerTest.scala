package com.gridgame.server

import com.gridgame.server.lobby.{Lobby, LobbyManager, LobbyStatus}
import com.gridgame.common.Constants
import org.junit.Assert._
import org.junit.Test

import java.util.UUID

/** The lobbies' bookkeeping: their numbers, their seats, and who is in which. */
class LobbyManagerTest {
  private val lobbies = new LobbyManager()

  private def someone(): UUID = UUID.randomUUID()

  private def create(host: UUID = someone(), maxPlayers: Int = 8): Lobby =
    lobbies.createLobby(host, "lobby", 0, 5, maxPlayers)

  @Test def noLobbyIsNumberedZero(): Unit = {
    // Numbers wrap at 32768, and lobby 0 is no lobby at all to the client and on the wire: the
    // players of a lobby numbered 0 had their character picks go nowhere
    lobbies.nextId.set(0x7FFF)
    val last = create()
    val next = create()
    assertEquals(0x7FFF.toShort, last.id)
    assertNotEquals(0.toShort, next.id)
  }

  @Test def aNumberStillInUseIsNotHandedOutAgain(): Unit = {
    val first = create()
    lobbies.nextId.set(first.id)
    assertNotEquals(first.id, create().id)
  }

  @Test def atMostAHundredLobbiesAtOnce(): Unit = {
    val made = (1 to Constants.MAX_LOBBIES).map(_ => create())
    assertTrue(made.forall(_ != null))
    assertNull(create())
    lobbies.removeLobby(made.head.id)
    assertNotNull("room again once one closes", create())
  }

  @Test def theHostIsInTheLobbyTheyMade(): Unit = {
    val host = someone()
    val lobby = create(host)
    assertEquals(lobby, lobbies.getPlayerLobby(host))
    assertTrue(lobby.isHost(host))
    assertEquals(1, lobby.playerCount)
  }

  @Test def aFullLobbyTakesNobodyElse(): Unit = {
    val lobby = create(maxPlayers = 2)
    assertEquals(lobby, lobbies.joinLobby(someone(), lobby.id))
    val late = someone()
    assertNull(lobbies.joinLobby(late, lobby.id))
    assertNull(lobbies.getPlayerLobby(late))
  }

  @Test def aBotTakesASeat(): Unit = {
    val lobby = create(maxPlayers = 2)
    lobby.botManager.addBot()
    assertNull(lobbies.joinLobby(someone(), lobby.id))
  }

  @Test def aLobbyThatHasStartedTakesNobody(): Unit = {
    val lobby = create()
    lobby.status = LobbyStatus.IN_GAME
    assertNull(lobbies.joinLobby(someone(), lobby.id))
  }

  @Test def leavingFreesTheSeatAndThePlayer(): Unit = {
    val lobby = create(maxPlayers = 2)
    val guest = someone()
    lobbies.joinLobby(guest, lobby.id)
    assertEquals(lobby, lobbies.leaveLobby(guest))
    assertNull(lobbies.getPlayerLobby(guest))
    assertFalse(lobby.players.contains(guest))
    assertEquals("the seat is free again", lobby, lobbies.joinLobby(someone(), lobby.id))
    assertNull("leaving twice is leaving nothing", lobbies.leaveLobby(guest))
  }

  @Test def closingALobbyLetsEveryoneInItGo(): Unit = {
    val host = someone()
    val lobby = create(host)
    val guest = someone()
    lobbies.joinLobby(guest, lobby.id)
    lobbies.removeLobby(lobby.id)
    assertNull(lobbies.getLobby(lobby.id))
    assertNull(lobbies.getPlayerLobby(host))
    assertNull(lobbies.getPlayerLobby(guest))
  }

  @Test def aFinishedLobbyIsNotListedAsActive(): Unit = {
    val open = create()
    val finished = create()
    finished.status = LobbyStatus.FINISHED
    val active = lobbies.getActiveLobbies
    assertTrue(active.contains(open))
    assertFalse(active.contains(finished))
  }

  // --- Two at once: each of these holds the lobby as a join or a START under way does, and lets
  // the other request in while it does ---

  /** Run `request` on a thread of its own while the lobby is held, as it is while `meanwhile` —
    * someone else's request — is under way, and wait for it to finish. */
  private def whileHeld[A](lobby: Lobby)(request: => A)(meanwhile: => Unit): A = {
    var result: Option[A] = None
    val other = new Thread(() => result = Some(request))
    lobby.synchronized {
      other.start()
      // Until it waits for the lobby (or, not waiting, has done what it came to do)
      val deadline = System.nanoTime() + 5_000_000_000L
      while (other.getState != Thread.State.BLOCKED && other.getState != Thread.State.TERMINATED &&
             System.nanoTime() < deadline) Thread.onSpinWait()
      meanwhile
    }
    other.join(5000)
    result.getOrElse(throw new AssertionError("the request never finished"))
  }

  @Test def aJoinAndABotAtOnceDoNotBothTakeTheLastSeat(): Unit = {
    // The host added a bot as someone joined: each saw the last seat free, and took it
    val lobby = create(maxPlayers = 2)
    val bot = whileHeld(lobby)(lobby.addBot()) {
      lobby.players.add(someone()) // the join takes the last seat
    }
    assertEquals(None, bot)
    assertEquals(2, lobby.playerCount)
  }

  @Test def aSwitchToTeamsAsSomeoneJoinsHasSeatsForThemToo(): Unit = {
    // Four humans, and 2v2 asked for as a fifth joins: sized for the four, the switch left five in
    // a lobby of four seats
    val lobby = create()
    (1 to 3).foreach(_ => lobbies.joinLobby(someone(), lobby.id))
    whileHeld(lobby)(lobby.configure(1, 2)) {
      lobby.players.add(someone())
    }
    assertEquals((1, 3, 6), (lobby.gameMode.toInt, lobby.teamSize, lobby.maxPlayers))
  }

  @Test def aJoinAsTheMatchStartsIsRefused(): Unit = {
    // Its players are dealt their places once it has started: one let in after that was left in
    // the room, and never in the match
    val lobby = create()
    val late = someone()
    val joined = whileHeld(lobby)(lobbies.joinLobby(late, lobby.id)) {
      lobby.status = LobbyStatus.IN_GAME // LobbyHandler.handleStart
    }
    assertNull(joined)
    assertFalse(lobby.players.contains(late))
    assertNull(lobbies.getPlayerLobby(late))
  }

  @Test def switchingToTeamsDropsTheBotsThatNoLongerFit(): Unit = {
    val lobby = create()
    lobbies.joinLobby(someone(), lobby.id)
    val bots = (1 to 4).flatMap(_ => lobby.addBot())
    val dropped = lobby.configure(1, 2)
    assertEquals("the last added go first", bots.reverse.take(2), dropped)
    assertEquals((4, 4), (lobby.playerCount, lobby.maxPlayers))
    assertEquals("and back to a free-for-all's seats", Nil, lobby.configure(0, 2))
    assertEquals(Constants.MAX_LOBBY_PLAYERS, lobby.maxPlayers)
  }
}
