package com.gridgame.server

import com.gridgame.common.protocol._
import org.junit.After
import org.junit.Assert._
import org.junit.Test

import java.util.UUID
import scala.collection.mutable

/**
 * Chat as the server passes it on: a lobby's chat to that lobby while it waits, a match's to
 * everyone in the match, team chat to the sender's team alone. Nothing unprintable, nothing empty,
 * and no more than five lines a second from anyone.
 */
class ChatTest {
  import ServerTestKit._

  private val lobbies = mutable.Buffer[Short]()

  @After def tidyUp(): Unit = lobbies.foreach(server.lobbyManager.removeLobby)

  private def player(): TestSession = { val s = new TestSession(); s.join(); s }

  private def say(s: TestSession, scope: Byte, text: String): Unit =
    s.send(n => new ChatMessagePacket(n, s.playerId, Packet.getCurrentTimestamp, scope, text))

  /** The lines a client has been sent since the last call: who said them, and what. */
  private def heard(s: TestSession): Seq[(UUID, String)] =
    s.received().collect { case c: ChatMessagePacket => (c.getPlayerId, c.getMessage) }

  /** A waiting lobby of a host and a guest. */
  private def lobbyOfTwo(): (TestSession, TestSession) = {
    val host = player()
    host.createLobby()
    lobbies += host.lobby.id
    val guest = player()
    guest.lobbyAction(LobbyAction.JOIN, host.lobby.id)
    assertEquals(host.lobby, guest.lobby)
    host.received()
    guest.received()
    (host, guest)
  }

  // --- A lobby ---

  @Test def lobbyChatReachesEveryoneInTheLobbyAndNobodyElse(): Unit = {
    val (host, guest) = lobbyOfTwo()
    val outsider = player()
    outsider.received()
    say(guest, ChatScope.LOBBY, "hello")
    assertEquals(Seq((guest.playerId, "hello")), heard(host))
    assertEquals("the sender sees it too", Seq((guest.playerId, "hello")), heard(guest))
    assertTrue(heard(outsider).isEmpty)
  }

  @Test def whatCannotBePrintedIsTakenOut(): Unit = {
    val (host, guest) = lobbyOfTwo()
    say(guest, ChatScope.LOBBY, "  hi\u0007 there\u001b ")
    assertEquals(Seq((guest.playerId, "hi there")), heard(host))
  }

  @Test def aLineWithNothingLeftIsNotSent(): Unit = {
    val (host, guest) = lobbyOfTwo()
    say(guest, ChatScope.LOBBY, "\u0001\u0002   ")
    assertTrue(heard(host).isEmpty)
  }

  @Test def noMoreThanFiveLinesASecondFromAnyone(): Unit = {
    val (host, guest) = lobbyOfTwo()
    for (i <- 1 to 8) say(guest, ChatScope.LOBBY, s"line $i")
    assertEquals((1 to 5).map(i => (guest.playerId, s"line $i")), heard(host))
  }

  @Test def outsideALobbyNobodyHears(): Unit = {
    val a = player()
    val b = player()
    b.received()
    say(a, ChatScope.LOBBY, "anyone?")
    say(a, ChatScope.GAME, "anyone?")
    assertTrue(heard(a).isEmpty)
    assertTrue(heard(b).isEmpty)
  }

  @Test def aLobbyThatHasNotStartedHasNoMatchChat(): Unit = {
    val (host, guest) = lobbyOfTwo()
    say(guest, ChatScope.GAME, "early")
    say(guest, ChatScope.TEAM, "early")
    assertTrue(heard(host).isEmpty)
  }

  // --- A match ---

  @Test def matchChatReachesEveryoneInTheMatch(): Unit = {
    val a = player()
    val b = player()
    val c = player()
    val lobby = new TestMatch().seat(Seq(a, b, c))
    lobbies += lobby.id
    Seq(a, b, c).foreach(_.received())
    say(b, ChatScope.GAME, "gg")
    Seq(a, b, c).foreach(s => assertEquals(Seq((b.playerId, "gg")), heard(s)))
  }

  @Test def lobbyChatIsNotPassedOnOnceTheMatchIsUnderWay(): Unit = {
    val a = player()
    val b = player()
    val lobby = new TestMatch().seat(Seq(a, b))
    lobbies += lobby.id
    Seq(a, b).foreach(_.received())
    say(a, ChatScope.LOBBY, "wrong channel")
    assertTrue(heard(b).isEmpty)
  }

  @Test def teamChatReachesTheSendersTeamAlone(): Unit = {
    val a = player()
    val ally = player()
    val enemy = player()
    val lobby = new TestMatch(gameMode = 1).seat(Seq(a, ally, enemy), teams = Seq(1, 1, 2))
    lobbies += lobby.id
    Seq(a, ally, enemy).foreach(_.received())
    say(a, ChatScope.TEAM, "push left")
    assertEquals(Seq((a.playerId, "push left")), heard(ally))
    assertEquals(Seq((a.playerId, "push left")), heard(a))
    assertTrue("the other team hears nothing", heard(enemy).isEmpty)
  }

  @Test def aFreeForAllHasNoTeamChat(): Unit = {
    val a = player()
    val b = player()
    val lobby = new TestMatch().seat(Seq(a, b))
    lobbies += lobby.id
    Seq(a, b).foreach(_.received())
    say(a, ChatScope.TEAM, "team?")
    assertTrue(heard(b).isEmpty)
  }
}
