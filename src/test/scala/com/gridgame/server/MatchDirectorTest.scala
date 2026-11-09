package com.gridgame.server.cluster

import com.gridgame.common.model.{Player, Position}
import com.gridgame.common.protocol.{MatchAssignPacket, MatchAssignPart, Packet}
import com.gridgame.server.ServerTestKit
import com.gridgame.server.game.{MatchOutcome, TickStats}
import com.gridgame.server.lobby.Lobby
import com.gridgame.server.net.{Outbox, RateLimiter, ReplayGuard, Sessions, TlsProvider}
import com.google.gson.JsonObject
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelInitializer
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import org.junit.After
import org.junit.Assert._
import org.junit.Test

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.{ConcurrentHashMap, CountDownLatch, LinkedBlockingQueue, TimeUnit}
import scala.collection.mutable

/**
 * The central server's side of the match servers (`MatchDirector`, design/networking.md, *Match
 * servers*), against match servers scripted by the test over real control links: which one each
 * match is handed to (the fullest with room), a match given back going to another first in line, a
 * match waiting for room and taking it as a match is released, a draining one handed nothing, a
 * match bigger than a whole match server played on an empty one, and every match of a match server
 * that goes away lost. And what a match costs in slots, what a match server reads of Agones'
 * answers about its GameServer, and how late ticks ran (TickStats).
 */
class MatchDirectorTest {
  private val secret = "a test's own cluster secret".getBytes(StandardCharsets.UTF_8)
  private val group = new NioEventLoopGroup(1)
  private val scripted = mutable.Buffer[Scripted]()
  private var director: MatchDirector = _
  private val host = new FakeHost

  @After def shutDown(): Unit = {
    scripted.foreach(s => try s.link.close() catch { case _: Throwable => })
    if (director != null) director.stop()
    group.shutdownGracefully()
  }

  private def waitUntil(what: String, ms: Long = 5000)(cond: => Boolean): Unit = {
    val end = System.currentTimeMillis() + ms
    while (!cond) {
      if (System.currentTimeMillis() > end) throw new AssertionError(s"timed out waiting for $what")
      Thread.sleep(5)
    }
  }

  private def freePort(): Int = { val s = new java.net.ServerSocket(0); val p = s.getLocalPort; s.close(); p }

  private def startDirector(): Int = {
    val port = freePort()
    val dir = Files.createTempDirectory("director-test")
    director = new MatchDirector(port, secret, TlsProvider.load(dir.resolve(TlsProvider.IdentityFileName)), host, waitMs = 30000L)
    director.bind()
    port
  }

  /** What the central server needs of its lobbies and players, and what it was told of each match. */
  private final class FakeHost extends DirectorHost {
    val outbox = new Outbox(new Sessions(new RateLimiter(), new ReplayGuard()))
    val players = new ConcurrentHashMap[UUID, Player]()
    val channels = new ConcurrentHashMap[UUID, ServerTestKit.PeerChannel]()
    val lobbies = new ConcurrentHashMap[java.lang.Short, Lobby]()
    val failed = new LinkedBlockingQueue[Short]()
    val ended = new LinkedBlockingQueue[Short]()
    val lost = new LinkedBlockingQueue[Short]()
    def getConnectedPlayer(playerId: UUID): Player = players.get(playerId)
    def lobbyOfMatch(lobbyId: Short, matchId: UUID): Lobby = {
      val l = lobbies.get(lobbyId)
      if (l != null && l.remoteMatch != null && l.remoteMatch.matchId == matchId) l else null
    }
    def matchFailed(lobby: Lobby, why: String): Unit = failed.add(lobby.id)
    def playerLeftMatch(lobby: Lobby, playerId: UUID): Unit = ()
    def matchEnded(lobby: Lobby, outcome: MatchOutcome, journal: String): Unit = ended.add(lobby.id)
    def matchLost(lobby: Lobby): Unit = lost.add(lobby.id)
  }

  private var nextLobby: Short = 1

  /** A lobby of `humans` logged-in players (and `bots`), its match launched on the match servers. */
  private def launch(humans: Int, bots: Int = 0): Lobby = {
    val ids = Seq.fill(humans)(UUID.randomUUID())
    val lobby = new Lobby(nextLobby, ids.head, s"lobby $nextLobby", 0, 5, 32)
    nextLobby = (nextLobby + 1).toShort
    ids.foreach { id =>
      val p = new Player(id, s"p${id.toString.take(4)}", new Position(0, 0), 0xff0000, 100)
      val ch = new ServerTestKit.PeerChannel()
      p.setTcpChannel(ch)
      host.players.put(id, p)
      host.channels.put(id, ch)
      lobby.players.add(id)
    }
    (1 to bots).foreach(_ => lobby.botManager.addBot())
    host.lobbies.put(lobby.id, lobby)
    director.launch(lobby, withBots = bots > 0)
    lobby
  }

  /** A match server the test speaks for: it registers with `capacity` and says what the test says. */
  private final class Scripted(controlPort: Int, val capacity: Int) {
    val heard = new LinkedBlockingQueue[JsonObject]()
    @volatile var link: ControlLink = _
    private val opened = new CountDownLatch(1)
    new Bootstrap().group(group).channel(classOf[NioSocketChannel])
      .handler(new ChannelInitializer[SocketChannel] {
        override def initChannel(ch: SocketChannel): Unit =
          ControlLink.pipeline(ch, ControlLink.clientTls(ch, null), central = false, secret, l => {
            link = l
            opened.countDown()
            new ControlListener {
              def onMessage(l: ControlLink, msg: JsonObject): Unit = heard.add(msg)
              def onClosed(l: ControlLink): Unit = ()
            }
          })
      })
      .connect("127.0.0.1", controlPort).sync()
    assertTrue("the control link opened", opened.await(5, TimeUnit.SECONDS))
    scripted += this

    def register(): Unit = {
      val m = ControlLink.message("register")
      m.addProperty("host", "127.0.0.1")
      m.addProperty("port", 26000 + scripted.indexOf(this))
      val key = new Array[Byte](MatchAssignPacket.KeyHashSize)
      new java.util.Random(capacity).nextBytes(key)
      m.addProperty("fingerprint", MatchAssignPacket.fingerprintOf(key))
      m.addProperty("build", "test")
      m.addProperty("capacity", capacity)
      link.send(m)
    }

    /** The next message of `kind` it was sent (the others before it are passed over). */
    def next(kind: String): JsonObject = {
      val end = System.currentTimeMillis() + 5000
      while (System.currentTimeMillis() < end) {
        val m = heard.poll(50, TimeUnit.MILLISECONDS)
        if (m != null && ControlLink.kindOf(m) == kind) return m
      }
      throw new AssertionError(s"no '$kind' came")
    }

    /** Nothing of `kind` comes within `ms`. */
    def nothing(kind: String, ms: Long = 300): Unit = {
      val end = System.currentTimeMillis() + ms
      while (System.currentTimeMillis() < end) {
        val m = heard.poll(20, TimeUnit.MILLISECONDS)
        if (m != null && ControlLink.kindOf(m) == kind) throw new AssertionError(s"an unexpected '$kind': $m")
      }
    }

    def start(): MatchSpec = MatchSpec.fromJson(next("start"))

    def say(kind: String, matchId: UUID)(more: JsonObject => Unit = _ => ()): Unit = {
      val m = MatchSpec.about(kind, matchId)
      more(m)
      link.send(m)
    }
  }

  private def registered(controlPort: Int, capacity: Int): Scripted = {
    val before = director.registered
    val s = new Scripted(controlPort, capacity)
    s.register()
    waitUntil("it to register")(director.registered == before + 1)
    s
  }

  private def matchOf(lobby: Lobby): UUID = lobby.remoteMatch.matchId

  /** What a player was sent over TCP so far. */
  private def sent(playerId: UUID): Seq[Packet] = {
    val ch = host.channels.get(playerId)
    ch.runPendingTasks()
    Iterator.continually(ch.readOutbound[ByteBuf]()).takeWhile(_ != null).map(ServerTestKit.decode).toSeq
  }

  @Test def eachMatchGoesToTheFullestMatchServerWithRoomForIt(): Unit = {
    val port = startDirector()
    val a = registered(port, capacity = 2)
    val b = registered(port, capacity = 2)
    assertEquals(4, director.free)

    // Both empty: the first to register takes the first
    val first = launch(2)
    assertEquals(matchOf(first), a.start().matchId)
    // Its players are told where only once it has started there
    val alice = first.players.get(0)
    assertTrue("nothing before it has started", sent(alice).isEmpty)
    a.say("started", matchOf(first))()
    val got = mutable.Buffer[Packet]()
    waitUntil("alice told where to play it") { got ++= sent(alice); got.size >= 2 }
    got.toSeq match {
      case Seq(address: MatchAssignPacket, identity: MatchAssignPacket) =>
        assertEquals(MatchAssignPart.ADDRESS, address.part)
        assertEquals(26000, address.port)
        assertEquals(MatchAssignPart.IDENTITY, identity.part)
      case other => fail(s"not the match's address and key: $other")
    }

    // The next goes where the first is: the fullest with room
    val second = launch(2)
    assertEquals(matchOf(second), a.start().matchId)
    a.say("started", matchOf(second))()
    // That one is full: the next goes to the other
    val third = launch(2)
    assertEquals(matchOf(third), b.start().matchId)
    b.nothing("start")
    a.nothing("start")
    assertEquals(1, director.free)
    assertEquals(3, director.playing)
  }

  @Test def aMatchGivenBackGoesToAnotherFirstInLine(): Unit = {
    val port = startDirector()
    val a = registered(port, capacity = 1)
    val b = registered(port, capacity = 1)
    val lobby = launch(2)
    val spec = a.start()
    // It has no room after all (or it began to drain): to try elsewhere
    a.say("failed", spec.matchId) { m => m.addProperty("why", "full"); m.addProperty("retry", true) }
    assertEquals("handed to the other", spec.matchId, b.start().matchId)
    assertTrue("its lobby didn't close", host.failed.isEmpty)
    assertSame(lobby, host.lobbyOfMatch(lobby.id, spec.matchId))

    // A match that couldn't start at all, though, is over: its lobby closes
    val other = launch(2)
    val otherSpec = a.start()
    a.say("failed", otherSpec.matchId) { m => m.addProperty("why", "the map wouldn't load"); m.addProperty("retry", false) }
    assertEquals(other.id, host.failed.poll(5, TimeUnit.SECONDS))
  }

  @Test def aMatchWaitsForRoomAndTakesItAsAnotherIsReleased(): Unit = {
    val port = startDirector()
    val a = registered(port, capacity = 1)
    val first = launch(2)
    assertEquals(matchOf(first), a.start().matchId)
    a.say("started", matchOf(first))()

    val second = launch(2)
    a.nothing("start")
    assertEquals("it waits", 1, director.queued)

    // The first ends: kept, then its slots released, and the second takes them
    val outcome = MatchSpec.endedJson(MatchOutcome(matchOf(first), 60, Nil, Map.empty), "a journal")
    a.link.send(outcome)
    assertEquals(first.id, host.ended.poll(5, TimeUnit.SECONDS))
    assertEquals(matchOf(first), MatchSpec.matchIdOf(a.next("recorded")))
    a.nothing("start", 200)
    a.say("released", matchOf(first))()
    assertEquals(matchOf(second), a.start().matchId)
    assertEquals(0, director.queued)
  }

  @Test def aDrainingMatchServerIsHandedNothingNew(): Unit = {
    val port = startDirector()
    val a = registered(port, capacity = 4)
    a.link.send(ControlLink.message("draining"))
    waitUntil("it to count for nothing")(director.free == 0)
    val lobby = launch(2)
    a.nothing("start")
    assertEquals(1, director.queued)
    // One that isn't draining registers: the match is its
    val b = registered(port, capacity = 4)
    assertEquals(matchOf(lobby), b.start().matchId)
  }

  @Test def aMatchBiggerThanAWholeMatchServerIsPlayedOnAnEmptyOne(): Unit = {
    val port = startDirector()
    val a = registered(port, capacity = 2)
    val small = launch(2)
    assertEquals(matchOf(small), a.start().matchId)
    // Sixteen humans: three slots, more than the whole of a match server of two
    val big = launch(16)
    a.nothing("start")
    assertEquals(1, director.queued)
    val b = registered(port, capacity = 2)
    val spec = b.start()
    assertEquals(matchOf(big), spec.matchId)
    assertEquals(3, spec.slots)
    assertEquals(16, spec.players.size)
  }

  @Test def aMatchServerGoingAwayLosesEveryMatchItHeld(): Unit = {
    val port = startDirector()
    val a = registered(port, capacity = 4)
    val one = launch(2)
    val two = launch(3)
    Seq(one, two).foreach { l => a.start(); a.say("started", matchOf(l))() }
    a.link.close()
    val lost = Set(host.lost.poll(5, TimeUnit.SECONDS), host.lost.poll(5, TimeUnit.SECONDS))
    assertEquals(Set(one.id, two.id), lost)
    waitUntil("it to be forgotten")(director.registered == 0)
  }

  @Test def aMatchTakesSlotsByWhatItSends(): Unit = {
    // Eight humans are one slot, and so is anything smaller: a duel, Teams, practice with its bots
    assertEquals(1, MatchSpec.slotsFor(8, 0))
    assertEquals(1, MatchSpec.slotsFor(2, 0))
    assertEquals(1, MatchSpec.slotsFor(6, 0))
    assertEquals(1, MatchSpec.slotsFor(1, 7))
    assertEquals(1, MatchSpec.slotsFor(0, 0))
    // Bigger: as measured, sixteen humans three, and by everyone times the humans past that
    assertEquals(2, MatchSpec.slotsFor(8, 4))
    assertEquals(2, MatchSpec.slotsFor(8, 8))
    assertEquals(2, MatchSpec.slotsFor(12, 0))
    assertEquals(3, MatchSpec.slotsFor(16, 0))
    assertEquals(10, MatchSpec.slotsFor(32, 0))

    // And they cross the link with the match
    val spec = MatchSpec(UUID.randomUUID(), 7, "a lobby", 0, 5, 16, 0, 0, 0, false, false, Nil, Nil, slots = 4)
    assertEquals(4, MatchSpec.fromJson(spec.toJson).slots)
    val old = spec.toJson
    old.remove("slots")
    assertEquals("one, from a central server that doesn't say", 1, MatchSpec.fromJson(old).slots)
  }

  @Test def agonesStatusGivesTheAddressPortAndCapacity(): Unit = {
    // As the SDK sidecar's REST answers (/gameserver): its int64s as strings
    val status = com.google.gson.JsonParser.parseString(
      """{"state":"Ready","address":"172.18.0.2","ports":[{"name":"game-tcp","port":26005},{"name":"game-udp","port":26005}],
        |"counters":{"matches":{"count":"3","capacity":"16"}},"lists":{}}""".stripMargin).getAsJsonObject
    assertEquals(Some("172.18.0.2" -> 26005), Agones.addressOf(status))
    assertEquals(Some(16L), Agones.capacityOf(status, Agones.MatchesCounter))
    assertEquals("a counter it doesn't declare", None, Agones.capacityOf(status, "rooms"))
    // Or as numbers; and before it is scheduled, no address yet
    val early = com.google.gson.JsonParser.parseString(
      """{"state":"Scheduled","address":"","ports":[],"counters":{"matches":{"count":0,"capacity":24}}}""").getAsJsonObject
    assertEquals(None, Agones.addressOf(early))
    assertEquals(Some(24L), Agones.capacityOf(early, Agones.MatchesCounter))
    assertEquals(None, Agones.capacityOf(com.google.gson.JsonParser.parseString("""{"counters":{}}""").getAsJsonObject, "matches"))
  }

  @Test def tickStatsCountHowLateTicksRan(): Unit = {
    TickStats.take()
    (1 to 98).foreach(_ => TickStats.record(300000L, 1000000L)) // 0.3ms late
    TickStats.record(4000000L, 1000000L) // 4ms
    TickStats.record(130000000L, 1000000L) // 130ms
    val w = TickStats.take()
    assertEquals(100, w.ticks)
    assertEquals(1, w.percentileMs(50))
    assertEquals(5, w.percentileMs(99))
    assertEquals(Int.MaxValue, w.percentileMs(100))
    assertEquals(0.98, w.within(1), 1e-9)
    assertEquals(0.99, w.within(5), 1e-9)
    assertEquals(130.0, w.worstMs, 1e-9)
    assertEquals(100.0, w.busyMs, 1e-9)
    assertEquals("a new window", 0, TickStats.take().ticks)
  }
}
