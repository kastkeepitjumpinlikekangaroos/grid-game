package com.gridgame.e2e

import com.gridgame.client.game.{ClientState, GameClient}
import com.gridgame.common.Constants
import com.gridgame.common.model._
import com.gridgame.common.world.WorldLoader
import com.gridgame.server.GameServer
import com.gridgame.server.game.E2EServer
import org.junit.After
import org.junit.Assert._
import org.junit.Test

import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import scala.collection.mutable
import scala.jdk.CollectionConverters._

/**
 * The game over real sockets, which is all the loopback (Loopback) leaves out: a GameServer listening
 * on loopback (TLS over TCP and signed UDP on one port, its matches and the matchmaker on clocks of
 * their own), and GameClients connecting as the app does (NetworkThread: the TLS handshake, the
 * server's identity pinned, a heartbeat every second; the packet thread). Two players sign up, meet in
 * a match, see each other move and fire, and see its standings when it ends.
 */
class RealNetworkTest {
  private val run = (System.nanoTime() % 100000).abs
  private var server: GameServer = _
  private val clients = mutable.Buffer[GameClient]()

  @After def shutDown(): Unit = {
    clients.foreach(c => try c.disconnect() catch { case _: Throwable => })
    if (server != null) server.stop()
  }

  private def die(why: String): Nothing = throw new AssertionError(why)

  private def waitUntil(what: String, ms: Long = 10000)(cond: => Boolean): Unit = {
    val end = System.currentTimeMillis() + ms
    while (!cond) {
      if (System.currentTimeMillis() > end) die(s"timed out waiting for $what")
      Thread.sleep(10)
    }
  }

  /** A port free for both TCP and UDP, as the server binds both to one. */
  private def freePort(): Int = {
    val tcp = new java.net.ServerSocket(0)
    val port = tcp.getLocalPort
    tcp.close()
    new java.net.DatagramSocket(port).close()
    port
  }

  /** A client as the app makes one and wires it (ClientMain.newClient): connected, signed up, joined. */
  private def player(name: String, port: Int): (GameClient, UUID) = {
    val c = new GameClient("127.0.0.1", port, WorldData.createEmpty(Constants.GRID_SIZE, Constants.GRID_SIZE), name)
    clients += c
    c.setWorldFileListener(file => c.setWorld(WorldLoader.load("worlds/" + file)))
    val answer = new AtomicReference[Either[String, UUID]]()
    c.authResponseListener = (ok: Boolean, id: UUID, message: String) => {
      if (ok) c.completeAuthAndJoin(id, name)
      answer.set(if (ok) Right(id) else Left(message))
    }
    // The server is up once it takes the connection
    waitUntil("the server to take a connection") {
      try { c.connect(); true } catch { case _: java.io.IOException => Thread.sleep(100); false }
    }
    c.sendAuthRequest(name, "hunter22", isSignup = true)
    waitUntil(s"$name's sign-up")(answer.get != null)
    val id = answer.get.fold(m => die(s"$name's sign-up was refused: $m"), identity)
    // Its heartbeats, every second, give the server the address to send its datagrams to
    waitUntil(s"$name's UDP address") {
      val p = server.getConnectedPlayer(id)
      p != null && p.getUdpAddress != null
    }
    (c, id)
  }

  @Test def twoPlayersSignUpMeetInAMatchAndSeeEachOtherMoveAndFire(): Unit = {
    val port = freePort()
    server = new GameServer(port)
    val serving = new Thread(() => server.start(), "GameServer")
    serving.setDaemon(true)
    serving.start()

    val (alice, aliceId) = player(s"net_a$run", port)
    val (bob, bobId) = player(s"net_b$run", port)
    alice.selectCharacter(CharacterId.Ranger.id)
    alice.createLobby("over the wire", 0, 1)
    waitUntil("alice's lobby")(alice.currentLobbyId != 0)
    val lobbyId = alice.currentLobbyId
    bob.joinLobby(lobbyId)
    waitUntil("bob in the lobby")(bob.currentLobbyId == lobbyId)
    alice.startGame()
    waitUntil("the match") {
      alice.clientState == ClientState.PLAYING && bob.clientState == ClientState.PLAYING &&
        alice.getPlayers.containsKey(bobId) && bob.getPlayers.containsKey(aliceId)
    }
    val gi = server.lobbyManager.getLobby(lobbyId).gameInstance
    // The free-for-all's half minute of ceasefire, over now: its end is heard at the next tick
    E2EServer.shortenOpening(gi, 0)
    waitUntil("the ceasefire's end")(!alice.attacksLocked && !bob.attacksLocked)

    // Alice walks: every step goes by datagram, and bob sees her where the server has her
    val start = alice.getLocalPosition
    val world = alice.getWorld
    val (dx, dy) = Seq((1, 0), (-1, 0), (0, 1), (0, -1)).find { case (x, y) =>
      (1 to 3).forall(k => world.isWalkable(start.getX + x * k, start.getY + y * k))
    }.getOrElse(die(s"nowhere to walk from $start"))
    for (_ <- 1 to 3) {
      alice.movePlayer(dx, dy)
      Thread.sleep(alice.moveStepIntervalMs + 30L)
    }
    val walked = alice.getLocalPosition
    assertNotEquals("alice walked", start, walked)
    waitUntil("the server to have her where she walked")(gi.registry.get(aliceId).getPosition == walked)
    waitUntil("bob to see her there")(bob.getPlayers.get(aliceId).getPosition == walked)

    // And fires at bob: the server spawns it, and bob sees it in flight
    val target = bob.getLocalPosition
    alice.setMouseWorldPosition(target.getX, target.getY)
    alice.pressPrimary()
    alice.releasePrimary()
    waitUntil("bob to see alice's shot") {
      bob.getProjectiles.values().asScala.exists(_.ownerId == aliceId) ||
        bob.getFadingProjectiles.values().asScala.nonEmpty
    }

    // The match ends: everyone on the scoreboard, with the server's standings
    server.endGame(lobbyId)
    waitUntil("the scoreboards")(alice.clientState == ClientState.SCOREBOARD && bob.clientState == ClientState.SCOREBOARD)
    for (c <- Seq(alice, bob))
      assertEquals(Set(aliceId, bobId), c.scoreboard.asScala.map(_.playerId).toSet)

    // Leaving: the server lets them go
    alice.disconnect()
    bob.disconnect()
    waitUntil("the server to see them go")(server.getConnectedPlayer(aliceId) == null && server.getConnectedPlayer(bobId) == null)
  }
}
