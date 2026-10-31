package com.gridgame.e2e

import com.gridgame.client.game.{ClientState, E2EClient, GameClient}
import com.gridgame.client.input.GLKeyboardHandler
import com.gridgame.client.net.{ClientTcpHandler, ClientUdpHandler, NetworkThread}
import com.gridgame.common.Constants
import com.gridgame.common.model._
import com.gridgame.common.protocol._
import com.gridgame.common.world.WorldLoader
import com.gridgame.server.GameServer
import com.gridgame.server.game.{E2EServer, GameInstance}
import com.gridgame.server.lobby.{Lobby, LobbyStatus}
import io.netty.buffer.{ByteBuf, Unpooled}
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.channel.socket.DatagramPacket
import io.netty.channel.{Channel, ChannelFuture}

import java.net.{InetSocketAddress, SocketAddress}
import java.util.UUID
import scala.collection.mutable
import scala.jdk.CollectionConverters._

/**
 * The network between the clients and the server: how long a packet takes each way, by how much
 * that varies, and what share of the datagrams are lost or arrive twice. TCP is never lost and
 * keeps its order; datagrams overtake each other whenever the jitter has them do so.
 */
final case class NetConditions(latencyMs: Int = 0, jitterMs: Int = 0, udpLoss: Double = 0.0,
                               udpDuplicates: Double = 0.0) {
  def isPerfect: Boolean = latencyMs == 0 && jitterMs == 0 && udpLoss == 0.0 && udpDuplicates == 0.0
  override def toString: String =
    if (isPerfect) "a perfect network" else s"${latencyMs}ms +${jitterMs}ms, ${(udpLoss * 100).toInt}% lost, ${(udpDuplicates * 100).toInt}% twice"
}

object NetConditions {
  val Perfect: NetConditions = NetConditions()
  /** Wi-Fi on a bad evening: 30ms each way and up to 60 more, a tenth of all datagrams lost, a few twice. */
  val Lossy: NetConditions = NetConditions(latencyMs = 30, jitterMs = 60, udpLoss = 0.10, udpDuplicates = 0.03)
  /** A phone on a train: 80ms each way and up to 250 more, a fifth of all datagrams lost, some twice. */
  val Awful: NetConditions = NetConditions(latencyMs = 80, jitterMs = 250, udpLoss = 0.20, udpDuplicates = 0.05)
}

/**
 * One end of a connection: an EmbeddedChannel whose peer is the address given, safe to write to from
 * several threads at once (a match on its own clocks writes from its ticks: see
 * ServerTestKit.PeerChannel).
 */
class Wire(remote: InetSocketAddress) extends EmbeddedChannel() {
  override protected def remoteAddress0(): SocketAddress = remote
  override def writeAndFlush(msg: AnyRef): ChannelFuture = synchronized(super.writeAndFlush(msg))
  override def write(msg: AnyRef): ChannelFuture = synchronized(super.write(msg))
  override def flush(): Channel = synchronized(super.flush())
  override def writeInbound(msgs: AnyRef*): Boolean = synchronized(super.writeInbound(msgs: _*))
  override def readOutbound[T](): T = synchronized(super.readOutbound[T]())
  override def runPendingTasks(): Unit = synchronized(super.runPendingTasks())

  /** Everything written to it since the last call. */
  def written[T](): Seq[T] = synchronized {
    super.runPendingTasks()
    Iterator.continually(super.readOutbound[T]()).takeWhile(_ != null).toList
  }
}

/**
 * The whole game in one process: a real GameServer and real GameClients, each end running its own
 * network handlers — the server's TcpHandler and UdpHandler (signatures, replay windows, rate limits,
 * who a packet may name), the client's ClientTcpHandler and ClientUdpHandler (signatures, the session
 * token taken up as it arrives) — with the sockets replaced by a network of the test's own
 * ([[NetConditions]]). Every client is driven as its player would drive it, through the real
 * keyboard handler and the calls the mouse handler makes, and the app's glue (ClientMain, MatchWindow)
 * is done here as the app does it.
 *
 * With `byHand`, matches the lobbies launch are begun but not started (MatchLauncher.startMatch), and
 * their clocks are the harness's: the projectile tick every 30ms, the player tick every 200ms, the
 * bots' every 100ms, respawns, item batches. Everything then happens on the test's thread, one thing
 * at a time, and on a perfect network the harness is **strict**: every packet is delivered at once,
 * so a client acts knowing all the server has said, and then
 *
 *  - every request an honest client makes must be taken: each step, shot, cast, trap, item and R
 *    ([[Judge]]) — a refusal is the client and the server disagreeing about the rules, but for a race
 *    it couldn't have seen and the test's own stalls, which the judge counts instead;
 *  - after every step every client must agree with the server about everyone in its match: where
 *    they are, their health, who is dead, their character and team, the items on the ground and in
 *    its own inventory, the traps down, its own kills and deaths ([[checkAgreement]]).
 *
 * On any network, nothing may throw, on either end, nothing may be printed to stderr (the server
 * reports there what it refuses or catches), and the server's own rules hold ([[checkServer]]).
 * Every client and server packet is noted in a trace, and a failure prints the last of it.
 */
final class Loopback private (val seed: Long, val net: NetConditions, val byHand: Boolean,
                              stderr: java.io.ByteArrayOutputStream, report: java.io.PrintStream) {
  import Loopback._

  val rnd = new scala.util.Random(seed)
  /** Every request taken and everyone agreeing at every step: see the class comment. */
  val strict: Boolean = byHand && net.isPerfect

  /** How long the opening of each match launched from now on lasts, if it has one. */
  var openingMs: Long = 0L
  /** How often a match's items come, after the first batch (the server's minute is longer than a test). */
  var itemEveryMs: Long = Constants.ITEM_SPAWN_INTERVAL_MS.toLong
  /** How long the dead wait for their respawn: the server's own by default. */
  var respawnDelayMs: Option[Long] = None

  val server: GameServer = new GameServer(0)
  if (byHand) E2EServer.runMatchesByHand(server, () => openingMs) else E2EServer.runMatchesOnTheirOwn(server, () => openingMs)

  private val serverUdpAddress = new InetSocketAddress("127.0.0.1", 7000)
  private val serverUdp: Wire = {
    val w = new Wire(serverUdpAddress)
    w.pipeline().addLast(E2EServer.udpHandler(server))
    server.outbox.attachUdpChannel(w)
    w
  }

  private val started = System.currentTimeMillis()
  private def now(): Long = System.currentTimeMillis()

  // ── What happened, for a failure to show ─────────────────────────────────

  private val trace = mutable.Queue[String]()
  /** What the run got up to: a run in which nothing was ever taken shows as one. */
  val tally: mutable.Map[String, Int] = mutable.Map[String, Int]().withDefaultValue(0)
  def count(what: String, n: Int = 1): Unit = tally(what) += n

  def note(s: String): Unit = {
    trace.enqueue(f"${now() - started}%6dms $s")
    if (trace.size > 160) trace.dequeue()
  }

  def fail(why: String): Nothing = {
    val err = stderrSoFar
    throw new AssertionError(
      s"seed $seed on $net${if (strict) ", strict" else ""}, ${now() - started}ms in: $why\n" +
        (if (err.trim.nonEmpty) s"stderr:\n  ${err.linesIterator.take(20).mkString("\n  ")}\n" else "") +
        s"The last of what happened:\n  ${trace.mkString("\n  ")}")
  }

  def check(ok: Boolean, why: => String): Unit = if (!ok) fail(why)

  // Problems found inside a Netty handler, where throwing would only close a channel: raised by the
  // harness as soon as the handler has returned
  private val problems = mutable.Buffer[String]()
  private[e2e] def problem(why: String): Unit = { problems += why; note(s"PROBLEM: $why") }

  /** Lines on stderr that are part of what a test does on purpose (counted): by default the server's
    * word on a fence it hands back. The client lets the server judge where a fence may go (unlike a
    * star or a trap, which it lands where the server will take them), so an honest one aimed at a wall
    * or out of reach is refused, and given back. */
  var stderrAllowed: String => Boolean = Loopback.FenceHandedBack
  private var stderrSeen = 0
  private def stderrSoFar: String = stderr.synchronized(stderr.toString)

  // What the server says of a refusal the judge has let pass (a race, a stall): taken once each
  private val forgiven = mutable.Buffer[String]()
  private[e2e] def forgive(fragment: String): Unit = forgiven += fragment

  private def checkStderr(): Unit = {
    val all = stderrSoFar
    if (all.length > stderrSeen) {
      val fresh = all.substring(stderrSeen)
      stderrSeen = all.length
      // SLF4J says once, as the database driver first logs, that it has nowhere to log to
      for (line <- fresh.linesIterator if line.trim.nonEmpty && !line.startsWith("SLF4J:")) {
        val pardon = forgiven.indexWhere(line.contains)
        if (pardon >= 0) forgiven.remove(pardon)
        else if (stderrAllowed(line)) count(s"stderr from ${line.takeWhile(_ != ':')}")
        else {
          // The server names a player by the first 8 characters of their id: theirs is the trace to show
          val named = clients.find(c => c.id != null && line.contains(c.id.toString.substring(0, 8)))
          fail(s"stderr: $line${named.map(_.recent).getOrElse("")}")
        }
      }
    }
  }

  /** Print a line to the test's own output, which the run otherwise keeps quiet. */
  def say(line: String): Unit = report.println(line)

  // ── The network ──────────────────────────────────────────────────────────

  private final class Delivery(val at: Long, val order: Long, val tcp: Boolean, val run: () => Unit)
  private val pending = mutable.PriorityQueue.empty[Delivery](Ordering.by((d: Delivery) => (-d.at, -d.order)))
  private var order = 0L
  private def schedule(at: Long, tcp: Boolean = false)(run: => Unit): Unit = {
    order += 1
    pending.enqueue(new Delivery(at, order, tcp, () => run))
  }

  private def delay(): Long = net.latencyMs + (if (net.jitterMs > 0) rnd.nextInt(net.jitterMs + 1) else 0)
  private def lost(): Boolean = net.udpLoss > 0 && rnd.nextDouble() < net.udpLoss
  private def twice(): Boolean = net.udpDuplicates > 0 && rnd.nextDouble() < net.udpDuplicates

  private var nextPort = 20000
  private[e2e] def freshPort(): Int = { nextPort += 1; nextPort }
  private var nextHost = 0
  private def freshHost(): String = { nextHost += 1; s"127.0.${1 + nextHost / 250}.${1 + nextHost % 250}" }

  private val clients = mutable.ArrayBuffer[LoopClient]()
  def everyone: Seq[LoopClient] = clients.toSeq

  /** A client, connected (its TCP connection open on the server) but not logged in. */
  def connect(name: String): LoopClient = {
    val c = new LoopClient(this, name, freshHost())
    clients += c
    c
  }

  /** What a client sent: signed as its network thread signs it, on its way to the server. */
  private[e2e] def fromClient(c: LoopClient, p: Packet): Unit = {
    val payload = p.serialize()
    val token = c.nt.sessionToken
    val data = if (token != null) PacketSigner.sign(payload, token) else padded(payload)
    note(s"${c.name} > ${describe(p)}")
    c.noteOwn(f"${now() - started}%6dms > ${describe(p)}")
    count(s"sent ${p.getType}")
    p match {
      case pp: ProjectilePacket if pp.getAction == ProjectileAction.SPAWN => c.shotsSent(pp.getAttackSlot) += 1
      case _ =>
    }
    val t = now()
    if (p.getType.tcp) {
      val at = Math.max(c.upTcpAt, t + delay())
      c.upTcpAt = at
      schedule(at, tcp = true)(toServer(c, p, data, tcp = true, t))
    } else if (lost()) {
      count("datagrams lost")
    } else {
      schedule(t + delay())(toServer(c, p, data, tcp = false, t))
      if (twice()) schedule(t + delay())(toServer(c, p, data, tcp = false, t))
    }
  }

  // The request being judged, which sees what the server says while it handles it
  private var watching: Judge = _

  private def toServer(c: LoopClient, p: Packet, data: Array[Byte], tcp: Boolean, sentAt: Long): Unit = {
    // A connection that has gone takes nothing more
    if (!c.serverEnd.isOpen) return
    val judge = if (strict) Judge.of(this, c, p, sentAt) else null
    watching = judge
    try {
      if (tcp) c.serverEnd.writeInbound(Unpooled.wrappedBuffer(data))
      else serverUdp.writeInbound(new DatagramPacket(Unpooled.wrappedBuffer(data), serverUdpAddress, c.udpAddress))
      if (p.isInstanceOf[HeartbeatPacket]) {
        val cp = E2EServer.connectedPlayer(server, c.id)
        if (cp != null && cp.getUdpAddress == null) note(s"${c.name}'s heartbeat #${p.getSequenceNumber} arrived and left it no UDP address")
      }
      drainServer()
    } finally watching = null
    if (judge != null) judge.verdict()
  }

  /** What the server has written to anyone, on its way to them. */
  private def drainServer(): Unit = {
    val t = now()
    val byAddress = mutable.Map[SocketAddress, mutable.Buffer[Array[Byte]]]()
    for (d <- serverUdp.written[DatagramPacket]()) {
      byAddress.getOrElseUpdate(d.recipient(), mutable.Buffer()) += bytesOf(d.content())
      d.release()
    }
    for (c <- clients) {
      val tcp = c.serverEnd.written[ByteBuf]().map { b => val a = bytesOf(b); b.release(); a }
      val udp = byAddress.remove(c.udpAddress).map(_.toSeq).getOrElse(Nil)
      if (tcp.nonEmpty || udp.nonEmpty) {
        val heard = (tcp ++ udp).map(decode)
        if (watching != null) heard.foreach(watching.saw(c, _))
        heard.foreach { p =>
          if (interesting(p)) note(s"${c.name} < ${describe(p)}")
          if (interesting(p) || (p.isInstanceOf[PlayerUpdatePacket] && c.id != null && c.id.equals(p.getPlayerId)))
            c.noteOwn(f"${now() - started}%6dms < ${describe(p)}")
        }
        if (net.isPerfect) {
          // Two sockets: what comes by TCP and by UDP keeps its own order, not each other's
          for ((data, isTcp) <- riffle(tcp.map((_, true)), udp.map((_, false))))
            schedule(t, isTcp && data(0) != PacketType.HEARTBEAT.id)(toClient(c, data, isTcp))
        } else {
          for (data <- tcp) {
            val at = Math.max(c.downTcpAt, t + delay())
            c.downTcpAt = at
            // A heartbeat's echo says nothing about the match
            schedule(at, tcp = data(0) != PacketType.HEARTBEAT.id)(toClient(c, data, tcp = true))
          }
          for (data <- udp) {
            if (lost()) count("datagrams lost")
            else {
              schedule(t + delay())(toClient(c, data, tcp = false))
              if (twice()) schedule(t + delay())(toClient(c, data, tcp = false))
            }
          }
        }
      }
    }
    if (byAddress.nonEmpty) count("datagrams to nobody", byAddress.values.map(_.size).sum)
  }

  private def toClient(c: LoopClient, data: Array[Byte], tcp: Boolean): Unit =
    if (c.closed) ()
    else if (tcp) c.tcpIn.writeInbound(Unpooled.wrappedBuffer(data))
    else c.udpIn.writeInbound(new DatagramPacket(Unpooled.wrappedBuffer(data), c.udpAddress, serverUdpAddress))

  private def riffle[A](a: Seq[A], b: Seq[A]): Seq[A] = {
    val out = mutable.ArrayBuffer[A]()
    var i = 0
    var j = 0
    while (i < a.size || j < b.size) {
      if (j >= b.size || (i < a.size && rnd.nextBoolean())) { out += a(i); i += 1 } else { out += b(j); j += 1 }
    }
    out.toSeq
  }

  /** Deliver everything that is due, and whatever that sets off, until nothing more is. */
  def pump(): Unit = {
    var rounds = 0
    var more = true
    while (more) {
      drainServer()
      more = false
      val t = now()
      while (pending.nonEmpty && pending.head.at <= t) {
        pending.dequeue().run()
        more = true
      }
      rounds += 1
      if (rounds > 100000) fail("the network never settled")
    }
    if (problems.nonEmpty) fail(problems.head)
  }

  /** Is anything still on its way? */
  def quiet: Boolean = pending.isEmpty

  /** Has everything sent by TCP arrived (a heartbeat's echo aside)? What the server and the clients say
    * by TCP then agrees, however much of what goes by UDP is still on its way or lost. */
  def tcpQuiet: Boolean = !pending.exists(_.tcp)

  // ── The server's clocks (byHand) ─────────────────────────────────────────

  private final class Clocks(val gi: GameInstance, at: Long) {
    var projectiles: Long = at + Constants.PROJECTILE_SPEED_MS
    var players: Long = at + 200
    var bots: Long = at + 100
    var items: Long = at
    val deadSince: mutable.Map[UUID, Long] = mutable.Map()
  }
  private val clocks = mutable.Map[GameInstance, Clocks]()
  private var nextCleanup = now() + 5000

  /** Every match's ticks that are due, as its executors would run them. */
  private def runClocks(t: Long): Unit = {
    val live = E2EServer.instances(server).filter(E2EServer.isRunning)
    clocks.keys.filterNot(live.contains).toList.foreach(clocks.remove)
    for (gi <- live) {
      val k = clocks.getOrElseUpdate(gi, new Clocks(gi, t))
      var ticks = 0
      while (t >= k.projectiles && ticks < 4) {
        E2EServer.tickProjectiles(gi); drainServer()
        k.projectiles += Constants.PROJECTILE_SPEED_MS
        ticks += 1
      }
      // Fallen behind (a slow step): the next tick from now, as a fixed-rate executor would catch up
      if (t >= k.projectiles) k.projectiles = t + Constants.PROJECTILE_SPEED_MS
      if (t >= k.players) { E2EServer.tickPlayers(gi); drainServer(); k.players = t + 200 }
      if (t >= k.bots) { E2EServer.tickBots(gi, t); drainServer(); k.bots = t + 100 }
      if (t >= k.items) { E2EServer.spawnItems(gi, E2EServer.itemBatch(gi)); drainServer(); k.items = t + itemEveryMs }
      // Respawns, as Lives.scheduleRespawn has them
      val delayMs = respawnDelayMs.getOrElse(if (gi.isPractice) 1000L else Constants.RESPAWN_DELAY_MS.toLong)
      gi.registry.getAll.asScala.foreach { p =>
        if (p.isDead) {
          val since = k.deadSince.getOrElseUpdate(p.getId, t)
          if (t - since >= delayMs) {
            E2EServer.respawn(gi, p.getId); drainServer()
            k.deadSince.remove(p.getId)
            count("respawns")
          }
        } else k.deadSince.remove(p.getId)
      }
    }
    if (t >= nextCleanup) { E2EServer.cleanup(server, t); drainServer(); nextCleanup = t + 5000 }
  }

  // ── Turning the world over ───────────────────────────────────────────────

  /**
   * One turn of everything: the matches' clocks (byHand), the network, and every client's frame —
   * its player's hands first (`act`), then what its game loop does every frame — and the rules
   * checked after.
   */
  def step(act: LoopClient => Unit = _ => ()): Unit = {
    val t = now()
    if (byHand) runClocks(t)
    pump()
    for (c <- clients if !c.closed) {
      if (c.loggedIn && t >= c.nextHeartbeat) { c.client.sendHeartbeat(); c.nextHeartbeat = t + Constants.HEARTBEAT_INTERVAL_MS }
      if (t >= c.nextFrame) {
        try {
          act(c)
          c.frame(t)
        } catch {
          case e: AssertionError => throw e
          case e: Throwable => fail(s"${c.name}'s frame threw ${stack(e)}")
        }
        c.nextFrame = t + FrameMs
      }
    }
    pump()
    checkStderr()
    checkServer()
    clients.foreach(checkClient)
    if (strict && quiet) checkAgreement()
    if (pending.isEmpty || pending.head.at > now()) Thread.sleep(1)
  }

  /** Turn the world over until `cond` holds, every client's player doing `act` meanwhile, or fail
    * after `timeoutMs`. */
  def await(what: String, timeoutMs: Long = 3000, act: LoopClient => Unit = _ => ())(cond: => Boolean): Unit = {
    val end = now() + timeoutMs
    while (!cond) {
      if (now() > end) fail(s"timed out waiting for $what")
      step(act)
    }
  }

  /** Turn the world over for `ms`, every client's player doing `act` at each of its frames. */
  def run(ms: Long)(act: LoopClient => Unit = _ => ()): Unit = {
    val end = now() + ms
    while (now() < end) step(act)
  }

  /** Hands off the keyboard until everything on its way has arrived and every match has had a tick. */
  def settle(ms: Long = 200): Unit = run(ms)()

  def close(): Unit = {
    try server.stop() catch { case _: Throwable => }
    clients.foreach(_.close())
  }

  // ── Logging in, and lobbies ──────────────────────────────────────────────

  /**
   * Log these clients in at once, each as a good password would but without its hash
   * (E2EServer.logInWithoutPassword), and wait until the server has every one connected, with the UDP
   * address its heartbeats give it (sent each second from the login on, as its network thread does).
   */
  def logIn(cs: Seq[LoopClient]): Seq[LoopClient] = {
    cs.foreach(c => E2EServer.logInWithoutPassword(server, c.serverEnd, UUID.randomUUID(), c.name))
    await("the logins", 10000)(cs.forall(c => c.loggedIn || c.authRefusal != null))
    cs.foreach(c => check(c.loggedIn, s"${c.name}'s login was refused: ${c.authRefusal}"))
    awaitConnected(cs)
    cs
  }

  private[e2e] def awaitConnected(cs: Seq[LoopClient]): Unit = {
    await("everyone connected", 10000)(cs.forall(c => E2EServer.connectedPlayer(server, c.id) != null))
    await("everyone's UDP address", 10000)(cs.forall(c => E2EServer.connectedPlayer(server, c.id).getUdpAddress != null))
  }

  /** These clients join the lobby at once, as the lobby browser does, and are in it. */
  def joinLobby(cs: Seq[LoopClient], lobbyId: Short): Unit = {
    val refused = cs.map(c => c -> c.lobbyRefusals.size).toMap
    cs.foreach(_.client.joinLobby(lobbyId))
    await(s"lobby $lobbyId's guests")(cs.forall(c => c.client.currentLobbyId == lobbyId || c.lobbyRefusals.size > refused(c)))
    for (c <- cs) check(c.client.currentLobbyId == lobbyId,
      s"${c.name} was refused lobby $lobbyId (LobbyActionFailure ${c.lobbyRefusals.lastOption.getOrElse("?")})")
  }

  // ── Matches ──────────────────────────────────────────────────────────────

  def lobbyOf(c: LoopClient): Lobby = if (c.id == null) null else E2EServer.lobbyOf(server, c.id)

  /** The match the client's player is in on the server, if one is running. */
  def matchOf(c: LoopClient): GameInstance = {
    val lobby = lobbyOf(c)
    if (lobby == null || lobby.status != LobbyStatus.IN_GAME || lobby.gameInstance == null) null
    else if (!E2EServer.isRunning(lobby.gameInstance)) null
    else lobby.gameInstance
  }


  /**
   * End a lobby's match as its clock running out would (MatchEnd), and see its standings arrive: every
   * client still in it is sent exactly what the server ranked, and goes to the scoreboard. Returns them.
   */
  def endMatch(lobbyId: Short): Seq[PlayerResult] = {
    val lobby = server.lobbyManager.getLobby(lobbyId)
    check(lobby != null && lobby.gameInstance != null, s"lobby $lobbyId has no match to end")
    val gi = lobby.gameInstance
    val inIt = clients.filter(c => !c.closed && gi.registry.contains(c.id) && c.client.clientState == ClientState.PLAYING)
    // The teams before the match comes down (stop clears them), the scores after (a match on its own
    // clocks scores until it stops)
    val teams = gi.teamAssignments.asScala.toMap
    note(s"the match of lobby $lobbyId ends")
    server.endGame(lobbyId)
    drainServer()
    val expected =
      if (gi.gameMode == 1) MatchResults.rankTeams(gi.killTracker.getScoreboard, id => teams.getOrElse(id, 0.toByte))
      else MatchResults.rankFfa(gi.killTracker.getScoreboard)
    await(s"everyone in lobby $lobbyId's match on the scoreboard")(inIt.forall(_.client.clientState == ClientState.SCOREBOARD))
    for (c <- inIt) {
      val board = c.client.scoreboard.asScala.toSeq.map(e => (e.playerId, e.kills, e.deaths, e.rank, e.teamId))
      val want = expected.map(r => (r.playerId, r.kills, r.deaths, r.rank, r.teamId.toInt))
      check(board.sortBy(_._1) == want.sortBy(_._1),
        s"${c.name}'s scoreboard ${board.mkString(", ")} is not the server's ${want.mkString(", ")}")
    }
    count("matches ended")
    count("kills", expected.map(_.kills).sum)
    expected
  }

  // ── The rules ────────────────────────────────────────────────────────────

  private val stranded = mutable.Set[(UUID, Position)]()

  /** What every match on the server must keep to, whatever anyone sends. */
  private def checkServer(): Unit =
    for (gi <- E2EServer.instances(server) if E2EServer.isRunning(gi) && gi.world != null) {
      val world = gi.world
      gi.registry.getAll.asScala.foreach { p =>
        val pos = p.getPosition
        check(p.getHealth >= 0 && p.getHealth <= p.getMaxHealth, s"${p.getName} has ${p.getHealth} of ${p.getMaxHealth} health")
        check(pos.getX >= 0 && pos.getY >= 0 && pos.getX < world.width && pos.getY < world.height, s"${p.getName} is off the map at $pos")
        if (!world.isWalkable(pos.getX, pos.getY) && !dashing(p) && !gi.modifiedTiles.containsKey((pos.getX, pos.getY))) {
          // A dash's landing is sent twice, and lost twice now and then: the server keeps its player
          // where the dash had got to, over whatever it was crossing, until their client next says
          // where it is (POSITION_RESEND_MS)
          if (net.isPerfect) fail(s"${p.getName} stands at $pos on ${world.getTile(pos.getX, pos.getY)}")
          else if (stranded.add(p.getId -> pos)) count("left standing where nobody can (a dash's landing lost)")
        }
        check(gi.itemManager.getInventory(p.getId).size <= Constants.MAX_INVENTORY_SIZE, s"${p.getName} carries too much")
      }
      val ground = gi.itemManager.getAll.map(_.id)
      check(ground.distinct.size == ground.size, "an item is on the ground twice")
      for (t <- gi.trapManager.getAll) check(gi.trapManager.trapAt(t.x, t.y) eq t, s"trap ${t.id} shares its cell")
      for (shot <- gi.projectileManager.getAll)
        check(java.lang.Float.isFinite(shot.getX) && java.lang.Float.isFinite(shot.getY), s"a projectile at (${shot.getX}, ${shot.getY})")
    }

  /** A phase from a kit with a dash in it: dashing, over walls if need be (KitTest: one of either). Or
    * a dash whose phase is over on the server but whose client is still on its way to the landing:
    * that comes as the dash ends on the client, at its next frame, a moment after the server's phase. */
  private def dashing(p: Player): Boolean = {
    val c = CharacterDef.get(p.getCharacterId)
    Seq(c.qAbility, c.eAbility).exists(_.castBehavior.isInstanceOf[DashBuff]) &&
      (System.currentTimeMillis() - p.getPhasedUntil < Constants.NETWORK_TOLERANCE_MS ||
        clients.exists(lc => !lc.closed && p.getId.equals(lc.id) && lc.client.isSwooping))
  }

  /** What every client must keep to, whatever it hears. */
  private def checkClient(c: LoopClient): Unit = if (!c.closed && c.client.clientState == ClientState.PLAYING) {
    val client = c.client
    val world = client.getWorld
    val pos = client.getLocalPosition
    check(client.getLocalHealth >= 0 && client.getLocalHealth <= client.getSelectedCharacterMaxHealth,
      s"${c.name} has ${client.getLocalHealth} of ${client.getSelectedCharacterMaxHealth} health")
    check(pos.getX >= 0 && pos.getY >= 0 && pos.getX < world.width && pos.getY < world.height, s"${c.name} is off its map at $pos")
    client.getProjectiles.values().forEach { p =>
      check(java.lang.Float.isFinite(p.getX) && java.lang.Float.isFinite(p.getY), s"${c.name} draws projectile ${p.id} at (${p.getX}, ${p.getY})")
    }
  }

  /**
   * Strict: with nothing on its way, every client in a match sees it as the server has it — itself
   * and everyone else, the items, its inventory, the traps and its own score.
   */
  private def checkAgreement(): Unit =
    for (c <- clients if !c.closed && c.client.clientState == ClientState.PLAYING) {
      val gi = matchOf(c)
      check(gi != null, s"${c.name} is playing a match the server has it in none of")
      val me = gi.registry.get(c.id)
      check(me != null, s"${c.name} is playing a match the server has taken it out of")
      val client = c.client
      def differ(what: String, mine: Any, theirs: Any): Nothing =
        fail(s"${c.name} (${CharacterDef.get(me.getCharacterId).displayName}) has $what as $mine, the server as $theirs${c.recent}")

      if (client.getLocalPosition != me.getPosition && now() >= c.racedUntil)
        differ("itself at", client.getLocalPosition, me.getPosition)
      if (client.getLocalHealth != me.getHealth) differ("its own health", client.getLocalHealth, me.getHealth)
      if (client.getIsDead != me.isDead) differ("itself dead", client.getIsDead, me.isDead)
      if (client.selectedCharacterId != me.getCharacterId) differ("its character", client.selectedCharacterId, me.getCharacterId)
      if (client.localTeamId != me.getTeamId) differ("its team", client.localTeamId, me.getTeamId)

      val others = client.getPlayers
      gi.registry.getAll.asScala.foreach { p =>
        if (p.getId != c.id) {
          val seen = others.get(p.getId)
          if (seen == null) differ(s"${p.getName}", "nowhere", p.getPosition)
          if (seen.getPosition != p.getPosition) differ(s"${p.getName} at", seen.getPosition, p.getPosition)
          if (seen.getHealth != p.getHealth) differ(s"${p.getName}'s health", seen.getHealth, p.getHealth)
          if (seen.isDead != p.isDead) differ(s"${p.getName} dead", seen.isDead, p.isDead)
          if (seen.getCharacterId != p.getCharacterId) differ(s"${p.getName}'s character", seen.getCharacterId, p.getCharacterId)
          if (seen.getTeamId != p.getTeamId) differ(s"${p.getName}'s team", seen.getTeamId, p.getTeamId)
        }
      }
      others.keySet().asScala.foreach(id => if (!gi.registry.contains(id)) differ(s"player $id in the match", "there", "gone"))

      val ground = gi.itemManager.getAll.map(i => i.id -> (i.getCellX, i.getCellY)).toMap
      val seenItems = client.getItems.asScala.map { case (id, i) => id.intValue -> (i.getCellX, i.getCellY) }.toMap
      if (seenItems != ground) differ("the items on the ground", seenItems, ground)
      val carried = gi.itemManager.getInventory(c.id).groupBy(_.itemType.id).map { case (k, v) => k -> v.size }
      for (t <- ItemType.all) {
        val mine = client.getItemCount(t.id)
        val theirs = carried.getOrElse(t.id, 0)
        if (mine != theirs) differ(s"${t.name}s in its inventory", mine, theirs)
      }
      val traps = gi.trapManager.getAll.map(_.id).toSet
      val seenTraps = client.getTraps.keySet().asScala.map(_.intValue).toSet
      if (seenTraps != traps) differ("the traps down", seenTraps.toSeq.sorted, traps.toSeq.sorted)
      if (client.killCount != gi.killTracker.getKills(c.id)) differ("its kills", client.killCount, gi.killTracker.getKills(c.id))
      if (client.deathCount != gi.killTracker.getDeaths(c.id)) differ("its deaths", client.deathCount, gi.killTracker.getDeaths(c.id))
    }
}

object Loopback {
  /** A frame every 16ms, as a 60 fps display has. */
  val FrameMs = 16L

  /** The server refusing an honest client's fence (ItemRequests.placeFence), which it gives back. */
  val FenceHandedBack: String => Boolean = _.startsWith("ClientHandler: Fence ")

  /**
   * Run `body` on a loopback of its own, keeping what everything prints from the test's output:
   * stdout thrown away (the server and the clients say something about nearly everything they do),
   * stderr kept for the harness to read after every step. Scala's println goes by Console.out, which
   * is taken from System.out once, so both are swapped (ServerTestKit.quietly).
   */
  def apply[T](seed: Long, net: NetConditions = NetConditions.Perfect, byHand: Boolean = true)(body: Loopback => T): T = {
    val err = new java.io.ByteArrayOutputStream()
    val errStream = new java.io.PrintStream(new java.io.OutputStream {
      override def write(b: Int): Unit = err.synchronized(err.write(b))
      override def write(b: Array[Byte], off: Int, len: Int): Unit = err.synchronized(err.write(b, off, len))
    }, true)
    val nothing = new java.io.PrintStream(java.io.OutputStream.nullOutputStream())
    val (out, oldErr) = (System.out, System.err)
    System.setOut(nothing)
    System.setErr(errStream)
    try Console.withOut(nothing)(Console.withErr(errStream) {
      val loop = new Loopback(seed, net, byHand, err, out)
      try body(loop) finally loop.close()
    }) finally {
      System.setOut(out)
      System.setErr(oldErr)
    }
  }

  private def padded(payload: Array[Byte]): Array[Byte] = {
    val data = new Array[Byte](Constants.PACKET_SIZE)
    System.arraycopy(payload, 0, data, 0, Math.min(payload.length, Constants.PACKET_PAYLOAD_SIZE))
    data
  }

  private def bytesOf(buf: ByteBuf): Array[Byte] = {
    val a = new Array[Byte](buf.readableBytes())
    buf.getBytes(buf.readerIndex(), a)
    a
  }

  /** The payload of what went over the wire, signed or not: its first 64 bytes. */
  private def decode(data: Array[Byte]): Packet = PacketSerializer.deserialize(java.util.Arrays.copyOf(data, Constants.PACKET_PAYLOAD_SIZE))

  def stack(e: Throwable): String = {
    val sw = new java.io.StringWriter
    e.printStackTrace(new java.io.PrintWriter(sw))
    sw.toString
  }

  /** The packets worth noting in a trace when the server sends them (not every projectile's MOVE). */
  private def interesting(p: Packet): Boolean = p match {
    case pp: ProjectilePacket => pp.getAction != ProjectileAction.MOVE
    case _: HeartbeatPacket => false
    case _ => true
  }

  def describe(p: Packet): String = {
    def id(u: UUID) = if (u == null) "-" else u.toString.substring(0, 6)
    p match {
      case u: PlayerUpdatePacket =>
        f"UPDATE ${id(u.getPlayerId)} at ${u.getPosition} hp ${u.getHealth} moves ${u.getServerMoves} flags ${u.getEffectFlags}%x/${u.getEffectFlags2}%x #${u.getSequenceNumber}"
      case pp: ProjectilePacket =>
        val action = Seq("SPAWN", "MOVE", "HIT", "DESPAWN", "PIERCE", "BLOCKED").lift(pp.getAction).getOrElse(pp.getAction.toString)
        f"PROJECTILE $action ${pp.getProjectileId} type ${pp.getProjectileType} by ${id(pp.getPlayerId)} at (${pp.getX}%.1f,${pp.getY}%.1f) dir (${pp.getDx}%.2f,${pp.getDy}%.2f)${if (pp.getTargetId != null) " on " + id(pp.getTargetId) else ""}"
      case g: GameEventPacket =>
        s"EVENT ${g.getEventType} ${id(g.getPlayerId)} -> ${id(g.getTargetId)} k${g.getKills} d${g.getDeaths} at (${g.getSpawnX},${g.getSpawnY})"
      case i: ItemPacket => s"ITEM action ${i.getAction} ${i.getItemId} type ${i.getItemType} by ${id(i.getPlayerId)} at (${i.getX},${i.getY})"
      case t: TrapPacket => s"TRAP action ${t.getAction} ${t.getTrapId} type ${t.getTrapType} by ${id(t.getPlayerId)} at (${t.getX},${t.getY}) slot ${t.getAttackSlot}"
      case l: LobbyActionPacket => s"LOBBY action ${l.getAction} lobby ${l.getLobbyId} by ${id(l.getPlayerId)} mode ${l.getGameMode} char ${l.getCharacterId}"
      case j: PlayerJoinPacket => s"JOIN ${id(j.getPlayerId)} at ${j.getPosition} char ${j.getCharacterId} team ${j.getTeamId}"
      case r: CooldownResetPacket => s"R action ${r.getAction} ${id(r.getPlayerId)} ${r.getMsLeft}ms"
      case other => s"${other.getType} ${id(other.getPlayerId)}"
    }
  }

  /** The world the server names, read as MatchWindow.loadWorld reads it. */
  def loadWorld(file: String): WorldData = WorldLoader.load("worlds/" + file)
}

/**
 * One player's client: a real GameClient, its network handlers (on a NetworkThread that is never
 * started, which only holds the session's token as the real one does), its keyboard handler, and the
 * glue the app gives it — the world loaded when the server names it, the answer to its login, the
 * scoreboard and back to the lobby browser.
 */
final class LoopClient private[e2e] (val loop: Loopback, val name: String, val ip: String) {
  val client: GameClient = new GameClient("loopback", 0, WorldData.createEmpty(Constants.GRID_SIZE, Constants.GRID_SIZE), name)
  private[e2e] val nt = new NetworkThread("loopback", 0, _ => (), () => ())
  val udpAddress = new InetSocketAddress(ip, loop.freshPort())
  /** The server's end of its TCP connection: the server's real handler. */
  private[e2e] val serverEnd: Wire = {
    val w = new Wire(new InetSocketAddress(ip, loop.freshPort()))
    w.pipeline().addLast(E2EServer.tcpHandler(loop.server))
    w
  }
  private[e2e] val tcpIn = new EmbeddedChannel(new ClientTcpHandler(p => heard(p), nt))
  private[e2e] val udpIn = new EmbeddedChannel(new ClientUdpHandler(p => heard(p), nt))
  val keys = new GLKeyboardHandler(client)

  private[e2e] var upTcpAt = 0L
  private[e2e] var downTcpAt = 0L
  private[e2e] var nextHeartbeat = 0L
  private[e2e] var nextFrame = 0L
  private var lastFrameNs = System.nanoTime()
  private[e2e] var closed = false
  private val ownTrace = mutable.Queue[String]()
  private[e2e] def noteOwn(s: String): Unit = { ownTrace.enqueue(s); if (ownTrace.size > 30) ownTrace.dequeue() }
  /** What it last sent and was told, for a failure about it to show. */
  def recent: String = s"\n$name's last packets:\n  ${ownTrace.mkString("\n  ")}"

  /** Who the server logged it in as (null before). */
  @volatile var id: UUID = _
  def loggedIn: Boolean = id != null
  @volatile var authRefusal: String = _
  /** Lobby actions the server refused it, by reason (LobbyAction.ACTION_FAILED). */
  val lobbyRefusals: mutable.Buffer[Byte] = mutable.Buffer()
  /** The spawn requests it has sent, by AttackSlot. */
  val shotsSent: mutable.Map[Int, Int] = mutable.Map[Int, Int]().withDefaultValue(0)
  /** By AttackSlot, when its latest cast was sent and when that cast's first request reached the
    * server (Judge.Shot). */
  private[e2e] val castDelivery = mutable.Map[Int, (Long, Long)]()
  /** Until when a race the judge let pass may keep it a step off the server (Judge.RaceSettleMs). */
  private[e2e] var racedUntil = 0L
  // What it sent last about itself, for the judge: whether it said it was phased, and its barrier up
  private[e2e] var saidPhased = false
  private[e2e] var saidBarrier = false

  E2EClient.sendTo(client, p => loop.fromClient(this, p))
  // MatchWindow.loadWorld, as the app wires it (ClientMain.newClient)
  client.setWorldFileListener { file =>
    if (file.isEmpty) ()
    else if (file.contains("..") || file.contains("/") || file.contains("\\")) loop.problem(s"$name was sent the world '$file'")
    else client.setWorld(try Loopback.loadWorld(file) catch {
      case e: Exception => loop.problem(s"$name couldn't load the world '$file': $e"); WorldData.createEmpty(Constants.GRID_SIZE, Constants.GRID_SIZE)
    })
  }
  // The answer to its login: in, as the app takes it in (ClientMain.newClient), or refused
  client.authResponseListener = (ok: Boolean, assigned: UUID, message: String) =>
    if (ok) {
      id = assigned
      client.completeAuthAndJoin(assigned, name)
      client.requestMatchHistory()
    } else authRefusal = message
  client.lobbyActionFailedListener = reason => lobbyRefusals += reason
  // MatchWindow: a lobby closing under a match goes back to the browser
  client.lobbyClosedListener = () => if (client.clientState == ClientState.PLAYING) client.returnToLobbyBrowser()
  // Esc twice: MatchWindow.leaveMatch (practice's results still come)
  keys.onLeaveMatch = () => { loop.note(s"$name leaves the match"); client.leaveMatch() }

  private def heard(p: Packet): Unit =
    try E2EClient.hear(client, p)
    catch { case e: Throwable => loop.problem(s"$name threw on ${Loopback.describe(p)}: ${Loopback.stack(e)}") }

  /** What the game loop does every frame (MatchWindow): the keyboard's update, the projectiles flown,
    * where we are drawn, and what the renderer reads of an aimed attack. */
  private[e2e] def frame(t: Long): Unit = {
    val ns = System.nanoTime()
    val dt = Math.min((ns - lastFrameNs) / 1e9, 0.05)
    lastFrameNs = ns
    if (client.clientState == ClientState.PLAYING) {
      keys.update()
      client.flyProjectiles(ns, dt)
      client.updateVisualPosition()
      client.aimingSlot
      client.primaryReady
      val d = client.getSelectedCharacterDef
      Seq(d.qAbility, d.eAbility).zipWithIndex.foreach { case (a, i) =>
        client.abilityReady(i)
        a.castBehavior match {
          case DashBuff(max, _, _) => E2EClient.dashLanding(client, max)
          case TeleportCast(max) => E2EClient.blinkLanding(client, max)
          case TrapCast(_, range) => client.trapLanding(range)
          case _ =>
        }
      }
    }
  }

  // ── Logging in ───────────────────────────────────────────────────────────

  /** Sign up as the login screen does, and wait to be in. */
  def signUp(password: String = "hunter22"): LoopClient = {
    client.sendAuthRequest(name, password, isSignup = true)
    loop.await(s"$name's sign-up", 10000)(loggedIn || authRefusal != null)
    loop.check(loggedIn, s"$name's sign-up was refused: $authRefusal")
    joined()
  }

  /** Log in as a good password would, without its hash (Loopback.logIn). */
  def logIn(): LoopClient = { loop.logIn(Seq(this)); this }

  private def joined(): LoopClient = { loop.awaitConnected(Seq(this)); this }

  /** Join a lobby as the lobby browser does, and wait to be in it (Loopback.joinLobby). */
  def joinLobby(lobbyId: Short): Unit = loop.joinLobby(Seq(this), lobbyId)

  // ── The lobby screens ────────────────────────────────────────────────────

  def createLobby(mapIndex: Int = 0, minutes: Int = 1, mode: Byte = 0, teamSize: Int = 2): Short = {
    client.createLobby(s"$name's", mapIndex, minutes)
    loop.await(s"$name's lobby")(client.currentLobbyId != 0)
    if (mode != 0) {
      client.updateLobbyConfig(mapIndex, minutes, mode, teamSize)
      loop.await(s"$name's lobby set to mode $mode")(client.currentLobbyGameMode == mode)
    }
    client.currentLobbyId
  }


  def pick(character: CharacterId): Unit = pick(character.id)
  def pick(character: Byte): Unit = {
    client.selectCharacter(character)
    if (client.currentLobbyId != 0) {
      val lobbyId = client.currentLobbyId
      loop.await(s"$name's pick in lobby $lobbyId") {
        val lobby = loop.server.lobbyManager.getLobby(lobbyId)
        lobby != null && lobby.getCharacter(id) == character
      }
    }
  }

  def playing: Boolean = client.clientState == ClientState.PLAYING

  // ── At the keyboard ──────────────────────────────────────────────────────

  def press(key: Int, mods: Int = 0): Unit = keys.invoke(0L, key, 0, org.lwjgl.glfw.GLFW.GLFW_PRESS, mods)
  def release(key: Int): Unit = keys.invoke(0L, key, 0, org.lwjgl.glfw.GLFW.GLFW_RELEASE, 0)

  /** The mouse over the world at (x, y), as GLMouseHandler puts it there. */
  def aimAt(x: Double, y: Double): Unit = client.setMouseWorldPosition(x, y)

  /** The left button down and up, as GLMouseHandler hands them on. */
  def mouseDown(): Unit = client.pressPrimary()
  def mouseUp(): Unit = client.releasePrimary()
  def rightClick(): Unit = client.cancelAims()

  def close(): Unit = if (!closed) {
    closed = true
    try { tcpIn.close(); udpIn.close(); serverEnd.close() } catch { case _: Throwable => }
  }
}
