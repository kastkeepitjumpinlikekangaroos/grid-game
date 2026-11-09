package com.gridgame.e2e

import com.gridgame.client.game.{ClientState, GameClient}
import com.gridgame.common.Constants
import com.gridgame.common.model._
import com.gridgame.common.world.WorldLoader
import com.gridgame.server.GameServer
import com.gridgame.server.cluster.{MatchDirector, MatchServer}
import com.gridgame.server.game.E2EServer
import org.junit.After
import org.junit.Assert._
import org.junit.Test

import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}
import scala.collection.mutable
import scala.jdk.CollectionConverters._

/**
 * Matches played on a match server of their own (design/networking.md, *Match servers*), over real
 * sockets on loopback: a central server that players log in to and that keeps the lobbies, match
 * servers that dial it and register, and clients connecting as the app does. A lobby's match is
 * handed to a match server, its players are sent there (MATCH_ASSIGN), log in with their tickets,
 * play it there, and see the standings the central server kept. A match server plays as many
 * matches at once as its capacity holds, each its own.
 */
class MatchServerTest {
  private val run = (System.nanoTime() % 100000).abs
  private val secret = "a test's own cluster secret".getBytes(StandardCharsets.UTF_8)
  private var server: GameServer = _
  private var director: MatchDirector = _
  private val matchServers = mutable.Buffer[MatchServer]()
  private val clients = mutable.Buffer[GameClient]()

  @After def shutDown(): Unit = {
    clients.foreach(c => try c.disconnect() catch { case _: Throwable => })
    matchServers.foreach(m => try m.stop() catch { case _: Throwable => })
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

  private def freePort(): Int = {
    val tcp = new java.net.ServerSocket(0)
    val port = tcp.getLocalPort
    tcp.close()
    new java.net.DatagramSocket(port).close()
    port
  }

  /** The central server, playing its matches on match servers. */
  private def central(waitMs: Long = 30000L): (Int, Int) = {
    val port = freePort()
    val controlPort = freePort()
    server = new GameServer(port)
    director = server.enableMatchServers(controlPort, secret, waitMs)
    val serving = new Thread(() => server.start(), "GameServer")
    serving.setDaemon(true)
    serving.start()
    (port, controlPort)
  }

  private def matchServer(controlPort: Int, key: Array[Byte] = secret, noShowMs: Long = 30000L,
                          capacity: Int = 1): MatchServer = {
    val m = new MatchServer(MatchServer.Config(freePort(), "127.0.0.1", controlPort, key,
      publicHost = Some("127.0.0.1"), capacity = Some(capacity), noShowMs = noShowMs))
    matchServers += m
    m.start()
    m
  }

  private val closedLobbies = new AtomicInteger()

  private def player(name: String, port: Int): (GameClient, UUID) = {
    val c = new GameClient("127.0.0.1", port, WorldData.createEmpty(Constants.GRID_SIZE, Constants.GRID_SIZE), name)
    clients += c
    c.setWorldFileListener(file => c.setWorld(WorldLoader.load("worlds/" + file)))
    c.lobbyClosedListener = () => closedLobbies.incrementAndGet()
    val answer = new AtomicReference[Either[String, UUID]]()
    c.authResponseListener = (ok: Boolean, id: UUID, message: String) => {
      if (ok) c.completeAuthAndJoin(id, name)
      answer.set(if (ok) Right(id) else Left(message))
    }
    waitUntil("the server to take a connection") {
      try { c.connect(); true } catch { case _: java.io.IOException => Thread.sleep(100); false }
    }
    c.sendAuthRequest(name, "hunter22", isSignup = true)
    waitUntil(s"$name's sign-up")(answer.get != null)
    val id = answer.get.fold(m => die(s"$name's sign-up was refused: $m"), identity)
    waitUntil(s"$name's UDP address") {
      val p = server.getConnectedPlayer(id)
      p != null && p.getUdpAddress != null
    }
    (c, id)
  }

  /** Everyone in a lobby the first makes, and its match started. */
  private def meet(players: Seq[GameClient], ids: Seq[UUID]): Short = {
    players.head.selectCharacter(CharacterId.Ranger.id)
    players.head.createLobby("on a match server", 0, 1)
    waitUntil("the lobby")(players.head.currentLobbyId != 0)
    val lobbyId = players.head.currentLobbyId
    players.tail.foreach(_.joinLobby(lobbyId))
    waitUntil("everyone in the lobby")(players.forall(_.currentLobbyId == lobbyId))
    players.head.startGame()
    lobbyId
  }

  private def everyonePlaying(players: Seq[GameClient], ids: Seq[UUID]): Boolean =
    players.forall(c => c.clientState == ClientState.PLAYING && c.onMatchServer &&
      ids.count(id => c.getPlayers.containsKey(id)) == ids.size - 1)

  /** `walker` takes three steps, by datagram to its match server, which has it there, and `watcher`
    * sees it there. */
  private def walkAndBeSeen(walker: GameClient, walkerId: UUID, watcher: GameClient, gi: com.gridgame.server.game.GameInstance): Unit = {
    val start = walker.getLocalPosition
    val world = walker.getWorld
    val (dx, dy) = Seq((1, 0), (-1, 0), (0, 1), (0, -1)).find { case (x, y) =>
      (1 to 3).forall(k => world.isWalkable(start.getX + x * k, start.getY + y * k))
    }.getOrElse(die(s"nowhere to walk from $start"))
    for (_ <- 1 to 3) {
      walker.movePlayer(dx, dy)
      Thread.sleep(walker.moveStepIntervalMs + 30L)
    }
    val walked = walker.getLocalPosition
    assertNotEquals("it walked", start, walked)
    waitUntil("the match server to have it where it walked")(gi.registry.get(walkerId).getPosition == walked)
    waitUntil("the other to see it there")(watcher.getPlayers.get(walkerId).getPosition == walked)
  }

  @Test def aMatchIsPlayedOnTheMatchServerAndItsResultsKeptByTheCentralServer(): Unit = {
    val (port, controlPort) = central()
    val ms = matchServer(controlPort)
    waitUntil("the match server to register")(director.registered == 1 && director.free == 1)

    val (alice, aliceId) = player(s"ms_a$run", port)
    val (bob, bobId) = player(s"ms_b$run", port)
    val udpIn = new AtomicInteger(); val udpDropped = new AtomicReference[String]("")
    bob.packetTap = new com.gridgame.client.net.PacketTap {
      def received(payload: Array[Byte], tcp: Boolean): Unit = if (!tcp && payload(0) == 2) udpIn.incrementAndGet()
      def sent(payload: Array[Byte], tcp: Boolean): Unit = ()
      def dropped(what: String, typeId: Byte): Unit = udpDropped.set(udpDropped.get + " " + what)
    }
    val lobbyId = meet(Seq(alice, bob), Seq(aliceId, bobId))
    waitUntil("both playing on the match server")(everyonePlaying(Seq(alice, bob), Seq(aliceId, bobId)))

    // Played there, not here
    assertTrue("the central server plays none of it", E2EServer.instances(server).isEmpty)
    val lobby = E2EServer.lobbyOf(server, aliceId)
    assertNotNull("the lobby is still the central server's", lobby)
    assertNotNull("its match is on a match server", lobby.remoteMatch)
    val gi = ms.currentInstance
    assertNotNull(gi)
    assertEquals("the match has the id the central server gave it", lobby.remoteMatch.matchId, gi.matchId)
    assertTrue(gi.registry.contains(aliceId) && gi.registry.contains(bobId))
    assertEquals("nobody else is free to take another", 0, director.free)

    E2EServer.shortenOpening(gi, 0)
    waitUntil("the ceasefire's end")(!alice.attacksLocked && !bob.attacksLocked)

    // Alice walks, by datagram to the match server, and bob sees her where it has her
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
    waitUntil("the match server to have her where she walked")(gi.registry.get(aliceId).getPosition == walked)
    try waitUntil("bob to see her there")(bob.getPlayers.get(aliceId).getPosition == walked)
    catch { case e: AssertionError =>
      die(s"bob sees her at ${bob.getPlayers.get(aliceId).getPosition}, not $walked; the match server has bob's UDP at " +
        s"${gi.registry.get(bobId).getUdpAddress} and alice's at ${gi.registry.get(aliceId).getUdpAddress}; bob got ${udpIn.get} updates, dropped ${udpDropped.get}") }

    // And fires at bob
    val target = bob.getLocalPosition
    alice.setMouseWorldPosition(target.getX, target.getY)
    alice.pressPrimary()
    alice.releasePrimary()
    waitUntil("bob to see alice's shot") {
      bob.getProjectiles.values().asScala.exists(_.ownerId == aliceId) || bob.getFadingProjectiles.values().asScala.nonEmpty
    }

    // A match chat line goes to the match server and reaches the other player
    val heard = bob.chatMessages.size
    alice.sendChatMessage("gg", com.gridgame.common.protocol.ChatScope.GAME)
    waitUntil("bob to hear alice")(bob.chatMessages.size > heard)

    // Its time is up: scored there, kept here, and the scoreboard on both
    ms.endGame(lobbyId)
    waitUntil("the scoreboards")(alice.clientState == ClientState.SCOREBOARD && bob.clientState == ClientState.SCOREBOARD)
    for (c <- Seq(alice, bob))
      assertEquals(Set(aliceId, bobId), c.scoreboard.asScala.map(_.playerId).toSet)
    val (_, _, played, _, _) = server.authDatabase.getPlayerStats(aliceId)
    assertEquals("the central server saved the match", 1, played)
    assertNull("its lobby is gone", E2EServer.lobbyOf(server, aliceId))

    // The match server lets its players go, and is free for the next
    waitUntil("the match server to be free again")(director.free == 1 && ms.isFree)
    waitUntil("the clients to let the match server go")(!alice.onMatchServer && !bob.onMatchServer)
    assertTrue("the session on the central server goes on", server.getConnectedPlayer(aliceId) != null)

    // A second match on the same match server (bob hosts: alice made a lobby too lately to make another)
    alice.returnToLobbyBrowser()
    bob.returnToLobbyBrowser()
    meet(Seq(bob, alice), Seq(bobId, aliceId))
    waitUntil("both playing the second match")(everyonePlaying(Seq(alice, bob), Seq(aliceId, bobId)))
  }

  @Test def practiceIsPlayedWithItsBotsOnTheMatchServerAndLeavingItShowsItsResults(): Unit = {
    val (port, controlPort) = central()
    val ms = matchServer(controlPort)
    waitUntil("the match server to register")(director.registered == 1)
    val (alice, aliceId) = player(s"ms_n$run", port)
    alice.startPractice()
    waitUntil("practice on the match server, its bots there") {
      alice.clientState == ClientState.PLAYING && alice.onMatchServer && alice.getPlayers.size == 5
    }
    val gi = ms.currentInstance
    assertNotNull("its bots run there", gi.botController)
    assertTrue("none here", E2EServer.instances(server).isEmpty)

    // Leaving practice ends it, scored, and its results screen follows
    alice.leaveMatch()
    waitUntil("the results")(alice.clientState == ClientState.SCOREBOARD)
    assertTrue(alice.scoreboard.asScala.exists(_.playerId == aliceId))
    assertEquals("the bots are on it too", 6, alice.scoreboard.size)
    assertEquals("kept by the central server (in her history, never her stats)", 1,
      server.authDatabase.getMatchHistory(aliceId).size)
    waitUntil("the match server free again")(director.free == 1)
  }

  @Test def aRankedDuelIsFoundPlayedOnTheMatchServerAndRatedByTheCentralServer(): Unit = {
    val (port, controlPort) = central()
    val ms = matchServer(controlPort)
    waitUntil("the match server to register")(director.registered == 1)
    val (alice, aliceId) = player(s"ms_o$run", port)
    val (bob, bobId) = player(s"ms_p$run", port)
    alice.selectCharacter(CharacterId.Samurai.id)
    bob.selectCharacter(CharacterId.Ranger.id)
    alice.queueRanked(com.gridgame.common.protocol.RankedQueueMode.DUEL)
    bob.queueRanked(com.gridgame.common.protocol.RankedQueueMode.DUEL)
    Thread.sleep(300)
    E2EServer.checkRankedQueue(server, System.currentTimeMillis())
    waitUntil("the duel, on the match server")(everyonePlaying(Seq(alice, bob), Seq(aliceId, bobId)))
    assertEquals("as the character it queued as", CharacterId.Ranger.id, ms.currentInstance.registry.get(bobId).getCharacterId)

    // Alice wins it: bob dies once
    val gi = ms.currentInstance
    gi.killTracker.recordKill(aliceId, bobId)
    ms.endGame(gi.gameId)
    waitUntil("the scoreboards")(alice.clientState == ClientState.SCOREBOARD && bob.clientState == ClientState.SCOREBOARD)
    val aliceElo = server.authDatabase.getEloByUUID(aliceId)
    val bobElo = server.authDatabase.getEloByUUID(bobId)
    assertTrue(s"the winner up ($aliceElo), the loser down ($bobElo)", aliceElo > 1000 && bobElo < 1000)
    waitUntil("each told their new rating")(alice.rankedElo == aliceElo && bob.rankedElo == bobElo)
    assertEquals("alice's change on her scoreboard", Some((1000, aliceElo)), alice.pendingEloChange)
  }

  @Test def aPlayerLeavingIsOutOfTheMatchWhichGoesOnForTheRest(): Unit = {
    val (port, controlPort) = central()
    val ms = matchServer(controlPort)
    waitUntil("the match server to register")(director.registered == 1)
    val (alice, aliceId) = player(s"ms_c$run", port)
    val (bob, bobId) = player(s"ms_d$run", port)
    val (carol, carolId) = player(s"ms_e$run", port)
    meet(Seq(alice, bob, carol), Seq(aliceId, bobId, carolId))
    waitUntil("all three playing")(everyonePlaying(Seq(alice, bob, carol), Seq(aliceId, bobId, carolId)))
    val gi = ms.currentInstance

    carol.leaveMatch()
    assertEquals(ClientState.LOBBY_BROWSER, carol.clientState)
    waitUntil("the match server to take her out")(!gi.registry.contains(carolId))
    waitUntil("the others to see her go")(!alice.getPlayers.containsKey(carolId) && !bob.getPlayers.containsKey(carolId))
    waitUntil("the central server's lobby to let her go")(E2EServer.lobbyOf(server, carolId) == null)
    assertFalse("she let the match server go", carol.onMatchServer)
    assertEquals("the match goes on", ClientState.PLAYING, alice.clientState)

    // Bob's client dies (its connection to the match server drops): out of it too
    bob.closeMatchConnection()
    waitUntil("the match server to take bob out")(!gi.registry.contains(bobId))
    waitUntil("alice to see him go")(!alice.getPlayers.containsKey(bobId))
    waitUntil("the central server's lobby to let him go")(E2EServer.lobbyOf(server, bobId) == null)
  }

  @Test def aMatchServerGoingAwayEndsItsMatchAndSendsItsPlayersBack(): Unit = {
    val (port, controlPort) = central()
    val ms = matchServer(controlPort)
    waitUntil("the match server to register")(director.registered == 1)
    val (alice, aliceId) = player(s"ms_f$run", port)
    val (bob, bobId) = player(s"ms_g$run", port)
    meet(Seq(alice, bob), Seq(aliceId, bobId))
    waitUntil("both playing")(everyonePlaying(Seq(alice, bob), Seq(aliceId, bobId)))

    ms.stop()
    waitUntil("the central server to know")(director.registered == 0)
    waitUntil("its lobby gone")(E2EServer.lobbyOf(server, aliceId) == null && E2EServer.lobbyOf(server, bobId) == null)
    waitUntil("both back in the browser")(alice.clientState == ClientState.LOBBY_BROWSER && bob.clientState == ClientState.LOBBY_BROWSER)
    assertTrue("the screens were told", closedLobbies.get() >= 2)
    assertTrue("still logged in", server.getConnectedPlayer(aliceId) != null && server.getConnectedPlayer(bobId) != null)
  }

  @Test def aMatchWaitsForAMatchServerToComeFree(): Unit = {
    val (port, controlPort) = central()
    val (alice, aliceId) = player(s"ms_l$run", port)
    val (bob, bobId) = player(s"ms_m$run", port)
    meet(Seq(alice, bob), Seq(aliceId, bobId))
    waitUntil("the match to wait for a match server")(director.queued == 1)
    assertNotNull("its lobby waits with it", E2EServer.lobbyOf(server, aliceId))
    // One registers (an autoscaler's): the match is its
    matchServer(controlPort)
    waitUntil("both playing on it")(everyonePlaying(Seq(alice, bob), Seq(aliceId, bobId)))
    assertEquals(0, director.queued)
  }

  @Test def withNoMatchServerComingFreeALobbysMatchDoesntStartAndTheLobbyCloses(): Unit = {
    val (port, _) = central(waitMs = 1500L)
    val (alice, aliceId) = player(s"ms_h$run", port)
    val (bob, bobId) = player(s"ms_i$run", port)
    meet(Seq(alice, bob), Seq(aliceId, bobId))
    waitUntil("the lobby to close")(E2EServer.lobbyOf(server, aliceId) == null)
    waitUntil("both told")(closedLobbies.get() >= 2)
    assertNotEquals(ClientState.PLAYING, alice.clientState)
    assertEquals(0, director.queued)
  }

  @Test def aMatchServerWithoutTheSecretIsNeverRegistered(): Unit = {
    val (_, controlPort) = central()
    val ms = matchServer(controlPort, key = "not the secret".getBytes(StandardCharsets.UTF_8))
    Thread.sleep(1500)
    assertEquals(0, director.registered)
    assertFalse(ms.isFree)
  }

  @Test def aTicketForNobodyIsRefused(): Unit = {
    val (port, controlPort) = central()
    val ms = matchServer(controlPort, noShowMs = 60000L)
    waitUntil("the match server to register")(director.registered == 1)
    val (alice, aliceId) = player(s"ms_j$run", port)
    val (bob, bobId) = player(s"ms_k$run", port)
    meet(Seq(alice, bob), Seq(aliceId, bobId))
    waitUntil("both playing")(everyonePlaying(Seq(alice, bob), Seq(aliceId, bobId)))

    // A client with a made-up ticket: refused, and the match never hears of it
    val known = com.gridgame.client.net.KnownServers.inMemory()
    known.trust("127.0.0.1", E2EMatch.portOf(ms), ms.identity.fingerprint)
    val answers = new java.util.concurrent.LinkedBlockingQueue[com.gridgame.common.protocol.Packet]()
    val t = new com.gridgame.client.net.NetworkThread("127.0.0.1", E2EMatch.portOf(ms), p => answers.add(p), () => (),
      knownServers = known)
    t.start()
    t.waitForReady()
    t.send(new com.gridgame.common.protocol.AuthRequestPacket(1, com.gridgame.common.protocol.AuthAction.MATCH_JOIN,
      new Array[Byte](Constants.REFRESH_TOKEN_SIZE)))
    val answer = answers.poll(5, java.util.concurrent.TimeUnit.SECONDS)
    t.shutdown()
    assertNotNull("an answer", answer)
    assertFalse("refused", answer.asInstanceOf[com.gridgame.common.protocol.AuthResponsePacket].getSuccess)
    assertEquals(2, ms.currentInstance.registry.getAll.size)
  }

  @Test def aMatchServerPlaysTwoMatchesAtOnceEachItsOwn(): Unit = {
    val (port, controlPort) = central()
    val ms = matchServer(controlPort, capacity = 2)
    waitUntil("the match server to register")(director.registered == 1 && director.free == 2)
    val (alice, aliceId) = player(s"ms_q$run", port)
    val (bob, bobId) = player(s"ms_r$run", port)
    val (carol, carolId) = player(s"ms_s$run", port)
    val (dave, daveId) = player(s"ms_t$run", port)
    val first = meet(Seq(alice, bob), Seq(aliceId, bobId))
    waitUntil("the first match played there")(everyonePlaying(Seq(alice, bob), Seq(aliceId, bobId)))
    meet(Seq(carol, dave), Seq(carolId, daveId))
    waitUntil("the second played there too")(everyonePlaying(Seq(carol, dave), Seq(carolId, daveId)))
    assertEquals("both on the one match server", 2, ms.instances.size)
    assertEquals(0, director.free)

    // Each its own: its players, its ticks, what they see
    val g1 = ms.instanceOf(aliceId)
    val g2 = ms.instanceOf(carolId)
    assertNotSame(g1, g2)
    assertSame(g1, ms.instanceOf(bobId))
    assertSame(g2, ms.instanceOf(daveId))
    assertTrue(!g1.registry.contains(carolId) && !g2.registry.contains(aliceId))
    assertFalse("nobody sees the other match's players",
      alice.getPlayers.containsKey(carolId) || carol.getPlayers.containsKey(aliceId))
    walkAndBeSeen(alice, aliceId, bob, g1)
    walkAndBeSeen(carol, carolId, dave, g2)
    assertFalse(dave.getPlayers.containsKey(aliceId))

    // One ends: its players see the scoreboard, the other plays on, and its slot is free again
    ms.endGame(first)
    waitUntil("the first's scoreboards")(alice.clientState == ClientState.SCOREBOARD && bob.clientState == ClientState.SCOREBOARD)
    assertEquals(ClientState.PLAYING, carol.clientState)
    waitUntil("its slot free again")(director.free == 1 && ms.instances.size == 1)
    waitUntil("its players let go")(!alice.onMatchServer && !bob.onMatchServer)
    assertTrue("the other's still there", carol.onMatchServer && dave.onMatchServer)
    walkAndBeSeen(dave, daveId, carol, g2)
  }

  @Test def aMatchWaitsForRoomOnAFullMatchServerAndTakesItWhenTheMatchBeforeIsOver(): Unit = {
    val (port, controlPort) = central()
    val ms = matchServer(controlPort, capacity = 1)
    waitUntil("the match server to register")(director.registered == 1)
    val (alice, aliceId) = player(s"ms_u$run", port)
    val (bob, bobId) = player(s"ms_v$run", port)
    val (carol, carolId) = player(s"ms_w$run", port)
    val (dave, daveId) = player(s"ms_x$run", port)
    val first = meet(Seq(alice, bob), Seq(aliceId, bobId))
    waitUntil("the first played")(everyonePlaying(Seq(alice, bob), Seq(aliceId, bobId)))
    meet(Seq(carol, dave), Seq(carolId, daveId))
    waitUntil("the second to wait for room")(director.queued == 1)
    assertNotEquals(ClientState.PLAYING, carol.clientState)

    ms.endGame(first)
    waitUntil("the second played there, once the first let its players go", 15000) {
      everyonePlaying(Seq(carol, dave), Seq(carolId, daveId))
    }
    assertEquals(0, director.queued)
    assertSame(ms.instanceOf(carolId), ms.currentInstance)
  }

  @Test def aPlayerWhoLeftForAnotherMatchOnTheSameMatchServerPlaysOnAsTheFirstIsLetGo(): Unit = {
    val (port, controlPort) = central()
    val ms = matchServer(controlPort, capacity = 2)
    waitUntil("the match server to register")(director.registered == 1)
    val (alice, aliceId) = player(s"ms_y$run", port)
    val (bob, bobId) = player(s"ms_z$run", port)
    val (carol, carolId) = player(s"ms_0$run", port)
    val first = meet(Seq(bob, alice), Seq(bobId, aliceId))
    waitUntil("the first played")(everyonePlaying(Seq(alice, bob), Seq(aliceId, bobId)))
    val g1 = ms.instanceOf(aliceId)

    // Alice leaves it, and plays another with carol: on the same match server, which has room
    alice.leaveMatch()
    waitUntil("alice out of the first")(!g1.registry.contains(aliceId) && E2EServer.lobbyOf(server, aliceId) == null)
    meet(Seq(carol, alice), Seq(carolId, aliceId))
    waitUntil("alice and carol playing the second")(everyonePlaying(Seq(carol, alice), Seq(carolId, aliceId)))
    val g2 = ms.instanceOf(aliceId)
    assertNotSame(g1, g2)

    // The first ends and its players are let go, alice among them as it has her: her session is
    // the second's, and goes on
    ms.endGame(first)
    waitUntil("bob's scoreboard")(bob.clientState == ClientState.SCOREBOARD)
    waitUntil("the first let go")(!bob.onMatchServer && ms.instances.size == 1)
    assertTrue("alice still plays the second", alice.onMatchServer && alice.clientState == ClientState.PLAYING)
    walkAndBeSeen(alice, aliceId, carol, g2)
  }

  @Test def aDrainingMatchServerTakesNoNewMatchButPlaysItsOwnToTheirEnd(): Unit = {
    val (port, controlPort) = central()
    val draining = matchServer(controlPort, capacity = 2)
    waitUntil("the match server to register")(director.registered == 1)
    val (alice, aliceId) = player(s"ms_1$run", port)
    val (bob, bobId) = player(s"ms_2$run", port)
    val (carol, carolId) = player(s"ms_3$run", port)
    val (dave, daveId) = player(s"ms_4$run", port)
    val first = meet(Seq(alice, bob), Seq(aliceId, bobId))
    waitUntil("the first played")(everyonePlaying(Seq(alice, bob), Seq(aliceId, bobId)))

    // Told to stop (as Kubernetes deletes its pod): it drains
    val stopped = new java.util.concurrent.CountDownLatch(1)
    val drainer = new Thread(() => { draining.drainThenStop(60000L); stopped.countDown() }, "drainer")
    drainer.setDaemon(true)
    drainer.start()
    waitUntil("the central server to count it out")(director.free == 0)

    // The next match is handed to another, the first played on where it is
    meet(Seq(carol, dave), Seq(carolId, daveId))
    waitUntil("the next to wait")(director.queued == 1)
    val other = matchServer(controlPort, capacity = 2)
    waitUntil("it played on the other")(everyonePlaying(Seq(carol, dave), Seq(carolId, daveId)))
    assertNotNull(other.instanceOf(carolId))
    assertNull(draining.instanceOf(carolId))
    assertEquals("the first plays on", ClientState.PLAYING, alice.clientState)
    assertEquals("it hasn't stopped", 1L, stopped.getCount)

    // Its last match over, it stops
    draining.endGame(first)
    waitUntil("the first's scoreboards")(alice.clientState == ClientState.SCOREBOARD && bob.clientState == ClientState.SCOREBOARD)
    assertTrue("drained, it stopped", stopped.await(15, java.util.concurrent.TimeUnit.SECONDS))
    waitUntil("the central server to let it go")(director.registered == 1)
    assertEquals("the other plays on", ClientState.PLAYING, carol.clientState)
  }
}

/** The match server's own port, for a test that connects to it as a client. */
object E2EMatch {
  def portOf(ms: MatchServer): Int = ms.gamePort
}
