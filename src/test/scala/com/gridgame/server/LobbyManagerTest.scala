package com.gridgame.server

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
}
