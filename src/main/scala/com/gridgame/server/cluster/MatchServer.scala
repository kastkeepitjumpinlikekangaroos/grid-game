package com.gridgame.server.cluster

import com.gridgame.common.{BuildInfo, Constants}
import com.gridgame.common.model.{Player, Position}
import com.gridgame.common.observability.{Attrs, Metrics}
import com.gridgame.common.protocol._
import com.gridgame.server.game.{GameInstance, MatchScoring, TickStats}
import com.gridgame.server.lobby.{Lobby, LobbyHost, MatchLauncher}
import com.gridgame.server.net._
import com.google.gson.JsonObject
import io.netty.bootstrap.Bootstrap
import io.netty.channel.{Channel, ChannelFuture, ChannelFutureListener, ChannelInitializer, ChannelOption}
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel

import java.lang.management.ManagementFactory
import java.net.InetSocketAddress
import java.nio.file.{Files, Path}
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.{ConcurrentHashMap, Executors, TimeUnit}
import scala.jdk.CollectionConverters._

/**
 * A match server (design/networking.md, *Match servers*): it plays the matches the central server
 * hands it, as many at once as its capacity holds, for the players the central server sends to it.
 *
 * It dials the central server's control port and registers: where its players reach it (the
 * address and port, which Agones gives it in Kubernetes), the fingerprint of the TLS key it shows
 * them, made afresh at every start, and its capacity, in slots (a match takes `MatchSpec.slots`: an
 * eight-player match one, a bigger one more). Given a match ("start"), it makes a lobby of its own of
 * it and launches it as the central server would (MatchLauncher), its players seated but not yet
 * connected, and says "started"; one it has no room for, or that comes as it drains, it gives back
 * ("failed", to be tried elsewhere). Each player's client then connects here, logs in with its ticket
 * (AUTH_REQUEST MATCH_JOIN), which says which of its matches it is in, and is given a session of its
 * own (a token, signed packets, as on the central server) and sent the match (ClientHandler.admit).
 * From there it is a match as any other: the same handlers, validator, ticks and bots, each match on
 * its own executors; what a player sends goes to the match their session was admitted to.
 *
 * A player who leaves (or whose connection drops, or who never comes within `noShowMs`) is taken
 * out of their match and the central server told ("left"). At the end a match is scored here
 * (MatchScoring), its players sent the standings, and the outcome sent to the central server with
 * the match's journal ("ended"), which keeps the results and the ratings and says "recorded"; then
 * the players get SCORE_END, their connections are closed and its slots are free ("released").
 *
 * If the link to the central server goes, so does every match it was playing (the central server
 * takes them for lost): their players are let go, and it dials again. Draining (`drain`, as its pod
 * is deleted), it takes no new match but plays the ones it has to their end.
 *
 * Every 30s while it plays anything it logs a status line: its matches and players, how late their
 * ticks ran (TickStats), the CPU and heap it used.
 */
final class MatchServer(config: MatchServer.Config) extends PacketRouter with LobbyHost {
  import MatchServer._

  val rateLimiter = new RateLimiter()
  val replayGuard = new ReplayGuard()
  val sessions = new Sessions(rateLimiter, replayGuard)
  val outbox = new Outbox(sessions)

  // A key of its own for each start: its players are told it by the central server, so nothing has
  // to be kept, and no two match servers share one
  private val identityDir: Path = Files.createTempDirectory("gridgame-match")
  val identity: TlsProvider.Identity = TlsProvider.load(identityDir.resolve(TlsProvider.IdentityFileName))

  private val network = new ServerNetwork(config.gamePort, identity.context,
    () => new TcpHandler(sessions, rateLimiter, replayGuard, this),
    () => new UdpHandler(sessions, rateLimiter, replayGuard, this), outbox)
  private[cluster] val launcher = new MatchLauncher(this)
  // Starting, ending and letting go of matches, one at a time, off the network's threads
  private val lifecycle = Executors.newSingleThreadExecutor(r => daemon(r, "MatchServer-lifecycle"))
  private val timers = Executors.newSingleThreadScheduledExecutor(r => daemon(r, "MatchServer-timers"))
  // Agones' sidecar, by HTTP: never on the lifecycle's thread, where a slow answer would hold up a start
  private val agonesCalls = Executors.newSingleThreadExecutor(r => daemon(r, "MatchServer-agones"))
  private val controlGroup = new NioEventLoopGroup(1)

  @volatile private var link: ControlLink = _
  @volatile private var stopped = false
  @volatile private var draining = false
  // What Agones was last told: nothing yet, Ready or Allocated
  @volatile private var agonesState = "Scheduled"
  @volatile private var publicAddress: (String, Int) = _
  @volatile private var slotCapacity: Int = config.capacity.getOrElse(DefaultCapacity)
  @volatile private var agonesCounter = false

  sessions.tellSessionEnded = (channel, playerId, reason) =>
    outbox.sendVia(new SessionEndPacket(outbox.nextSeq(), playerId, reason), channel)

  /** A match it plays: what the central server sent, and who of its players has come. */
  private final class Hosted(val spec: MatchSpec, val lobby: Lobby, val startedAt: Long) {
    @volatile var instance: GameInstance = _
    // The players as the launcher seats them: no connection until their client comes
    val stubs: Map[UUID, Player] = spec.players.map { p =>
      p.id -> new Player(p.id, p.name, new Position(0, 0), p.colorRGB, Constants.MAX_HEALTH)
    }.toMap
    val admitted = ConcurrentHashMap.newKeySet[UUID]()
    val gone = ConcurrentHashMap.newKeySet[UUID]()
    // Each player's connection, as they were admitted on it
    val channels = new ConcurrentHashMap[UUID, Channel]()
    @volatile var finishing = false
    // The scoreboard follows "recorded", or the wait for it running out: whichever comes first
    val shownScoreboard = new AtomicBoolean(false)
    def id: UUID = spec.matchId
  }

  // Every match it holds, from its start until its players are let go, by its id
  private val matches = new ConcurrentHashMap[UUID, Hosted]()
  // The ones being played (not yet ending), by their lobby's id: what a match calls its host by.
  // The central server's lobby ids are unique among its live lobbies, and a lobby's id is used again
  // only after its match has ended
  private val playing = new ConcurrentHashMap[java.lang.Short, Hosted]()
  // Each player's newest match here, from its start: who the launcher seats
  private val seatedIn = new ConcurrentHashMap[UUID, Hosted]()
  // The match each player's session was admitted to: where what they send goes
  private val sessionIn = new ConcurrentHashMap[UUID, Hosted]()

  /** Listen for players, and dial the central server. */
  def start(): Unit = {
    network.bind()
    println(s"Match server ${BuildInfo.describe} on port ${config.gamePort}, identity ${identity.fingerprint}")
    // Healthy from the start: Agones restarts a GameServer that stays quiet past its health check
    config.agones.foreach(a => timers.scheduleAtFixedRate(() => guarded("health")(a.health()), 0, 2, TimeUnit.SECONDS))
    resolveFromAgones()
    println(s"Match server: players reach it at ${publicAddress._1}:${publicAddress._2}; room for $slotCapacity slots of matches")
    timers.scheduleAtFixedRate(() => guarded("cleanup")(cleanup()), 1, 1, TimeUnit.SECONDS)
    timers.scheduleAtFixedRate(() => guarded("status")(logStatus()), config.statusEveryMs, config.statusEveryMs, TimeUnit.MILLISECONDS)
    TickStats.take()
    dial()
  }

  def stop(): Unit = {
    stopped = true
    matches.values().forEach(h => if (h.instance != null) h.instance.stop())
    val l = link
    if (l != null) l.close()
    timers.shutdownNow()
    lifecycle.shutdownNow()
    agonesCalls.shutdownNow()
    controlGroup.shutdownGracefully()
    network.stop()
    try Files.walk(identityDir).sorted(java.util.Comparator.reverseOrder()).forEach(p => Files.deleteIfExists(p))
    catch { case _: Exception => }
  }

  /**
   * Take no new match (the central server is told) and play the ones it has to their end, for
   * `maxWaitMs` at most; then stop. What its process does as Kubernetes deletes its pod.
   */
  def drainThenStop(maxWaitMs: Long): Unit = {
    drain()
    val end = System.currentTimeMillis() + maxWaitMs
    if (!matches.isEmpty) println(s"Match server: draining: ${matches.size()} match(es) to finish, ${maxWaitMs / 1000}s at most")
    while (!matches.isEmpty && System.currentTimeMillis() < end) Thread.sleep(200)
    if (!matches.isEmpty) println(s"Match server: ${matches.size()} match(es) still playing; stopped")
    stop()
  }

  /** Take no new match: the central server hands it none, and one already on its way is given back. */
  def drain(): Unit = if (!draining) {
    draining = true
    tell(ControlLink.message("draining"))
  }

  /** Where its players connect. */
  def gamePort: Int = config.gamePort

  /** How many slots of matches it plays at once. */
  def capacity: Int = slotCapacity

  /** The slots its matches take now (until each is let go). */
  def slotsUsed: Int = matches.values().asScala.iterator.map(_.spec.slots).sum

  /** Whether it is registered and could take a match now. */
  def isFree: Boolean = link != null && !draining && slotsUsed < slotCapacity

  /** The matches it plays (not yet ending). */
  def instances: Seq[GameInstance] = playing.values().asScala.toSeq.flatMap(h => Option(h.instance))

  /** The match it plays, if it plays one; the first if more (for a test of one). */
  def currentInstance: GameInstance = instances.headOption.orNull

  /** The match `playerId` was admitted to, or seated in, here. */
  def instanceOf(playerId: UUID): GameInstance = {
    val h = Option(sessionIn.get(playerId)).getOrElse(seatedIn.get(playerId))
    if (h == null) null else h.instance
  }

  // ── The central server ─────────────────────────────────────────────────────

  /** Where players reach it and how many slots it has: what the config says, or Agones (its
    * address and port, set as the pod is scheduled, and the capacity of its `matches` counter). */
  private def resolveFromAgones(): Unit = {
    val status = config.agones.flatMap { a =>
      // Its address and port come as the pod is scheduled; wait a while for them
      (1 to 30).iterator.map { _ =>
        val got = a.status().filter(s => Agones.addressOf(s).isDefined)
        if (got.isEmpty) Thread.sleep(1000)
        got
      }.collectFirst { case Some(s) => s }
    }
    val fromAgones = status.flatMap(Agones.addressOf)
    val host = config.publicHost.orElse(fromAgones.map(_._1)).getOrElse("127.0.0.1")
    val port = config.publicPort.orElse(fromAgones.map(_._2)).getOrElse(config.gamePort)
    publicAddress = (host, port)
    val counted = status.flatMap(s => Agones.capacityOf(s, Agones.MatchesCounter))
    agonesCounter = counted.isDefined
    slotCapacity = math.max(1, config.capacity.orElse(counted.map(_.toInt)).getOrElse(DefaultCapacity))
    // Told otherwise by its own flag, it puts its counter right: the autoscaler counts by it
    for (a <- config.agones; c <- counted if c != slotCapacity) a.setCapacity(Agones.MatchesCounter, slotCapacity)
  }

  private def dial(): Unit = if (!stopped) {
    val future: ChannelFuture = new Bootstrap()
      .group(controlGroup)
      .channel(classOf[NioSocketChannel])
      .option[java.lang.Boolean](ChannelOption.TCP_NODELAY, true)
      .option[java.lang.Integer](ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
      .handler(new ChannelInitializer[SocketChannel] {
        override def initChannel(ch: SocketChannel): Unit =
          ControlLink.pipeline(ch, ControlLink.clientTls(ch, config.centralIdentity.orNull), central = false,
            config.secret, l => { opened(l); listener })
      })
      .connect(config.centralHost, config.controlPort)
    future.addListener(new ChannelFutureListener {
      def operationComplete(f: ChannelFuture): Unit = if (!f.isSuccess) {
        System.err.println(s"Match server: can't reach the central server at ${config.centralHost}:${config.controlPort} " +
          s"(${f.cause().getMessage}); trying again in ${RedialMs}ms")
        redial()
      }
    })
  }

  private def redial(): Unit =
    if (!stopped) try timers.schedule(new Runnable { def run(): Unit = dial() }, RedialMs, TimeUnit.MILLISECONDS)
    catch { case _: java.util.concurrent.RejectedExecutionException => }

  private def opened(l: ControlLink): Unit = {
    link = l
    val m = ControlLink.message("register")
    m.addProperty("host", publicAddress._1)
    m.addProperty("port", publicAddress._2)
    m.addProperty("fingerprint", identity.fingerprint)
    m.addProperty("build", BuildInfo.describe)
    m.addProperty("capacity", slotCapacity)
    m.addProperty("draining", draining)
    l.send(m)
    println(s"Match server: registered with the central server at ${config.centralHost}:${config.controlPort}")
    syncAgones()
  }

  private val listener: ControlListener = new ControlListener {
    def onMessage(l: ControlLink, msg: JsonObject): Unit = guarded(ControlLink.kindOf(msg)) {
      val h = { val id = MatchSpec.matchIdOf(msg); if (id == null) null else matches.get(id) }
      ControlLink.kindOf(msg) match {
        case "start" =>
          val spec = MatchSpec.fromJson(msg)
          lifecycle.execute(() => guarded("start")(startMatch(spec)))
        case "remove" if h != null =>
          val pid = MatchSpec.playerIdOf(msg)
          if (pid != null) {
            val p = if (h.instance != null) h.instance.registry.get(pid) else null
            playerGone(h, pid, "left the lobby on the central server", tell = false)
            if (p != null && p.getTcpChannel != null) p.getTcpChannel.asInstanceOf[Channel].close()
          }
        case "end" if h != null => lifecycle.execute(() => guarded("end")(finish(h)))
        case "abandon" if h != null =>
          lifecycle.execute(() => guarded("abandon")(abandon(h, "everyone has left: the match is stopped without a result")))
        case "recorded" if h != null => lifecycle.execute(() => guarded("scoreboard")(showScoreboard(h, recorded = true)))
        case kind => System.err.println(s"Match server: '$kind' from the central server, about no match it plays; ignored")
      }
    }

    def onClosed(l: ControlLink): Unit = {
      if (link eq l) link = null
      if (stopped) return
      System.err.println(s"Match server: lost the central server (${playing.size()} match(es) playing)")
      playing.values().forEach { h =>
        lifecycle.execute(() => guarded("abandon")(abandon(h, "the central server went away: the match is stopped without a result")))
      }
      redial()
    }
  }

  private def failed(matchId: UUID, why: String, retry: Boolean): JsonObject = {
    val m = MatchSpec.about("failed", matchId)
    m.addProperty("why", why)
    m.addProperty("retry", retry)
    m
  }

  private def tell(msg: JsonObject): Unit = { val l = link; if (l != null) l.send(msg) }

  // ── A match's life (on the lifecycle's thread) ─────────────────────────────

  private def startMatch(spec: MatchSpec): Unit = {
    if (stopped) return
    val used = slotsUsed
    // A match bigger than the whole capacity is played on its own rather than nowhere
    if (matches.containsKey(spec.matchId)) {
      System.err.println(s"Match server: match ${spec.matchId} handed over again; it plays it already")
      return
    }
    if (draining || (used > 0 && used + spec.slots > slotCapacity)) {
      val why = if (draining) "draining" else s"full ($used of $slotCapacity slots taken, it needs ${spec.slots})"
      System.err.println(s"Match server: gave match ${spec.matchId} back: $why")
      tell(failed(spec.matchId, why, retry = true))
      return
    }
    val h = new Hosted(spec, spec.toLobby, System.currentTimeMillis())
    matches.put(spec.matchId, h)
    playing.put(spec.lobbyId, h)
    spec.players.foreach(p => seatedIn.put(p.id, h))
    syncAgones()
    try {
      h.instance = launcher.launch(h.lobby, spec.withBots)
      println(s"Match server: match ${spec.matchId} started (lobby ${spec.lobbyId}, ${spec.players.size} players, " +
        s"${spec.bots.size} bots, ${spec.slots} slot(s); ${matches.size()} match(es), ${slotsUsed} of $slotCapacity slots)")
      tell(MatchSpec.about("started", spec.matchId))
    } catch {
      case e: Exception =>
        System.err.println(s"Match server: match ${spec.matchId} failed to start: $e")
        forget(h)
        tell(failed(spec.matchId, e.toString, retry = false))
    }
  }

  /** The match is over: scored here, its outcome to the central server, then (once it has kept
    * it) the scoreboard. */
  private def finish(h: Hosted): Unit = {
    if (h.finishing || h.instance == null) return
    h.finishing = true
    playing.remove(h.spec.lobbyId, h)
    val instance = h.instance
    val outcome = MatchScoring.finish(instance, h.spec.lobbyId)
    tell(MatchSpec.endedJson(outcome, instance.journal.render))
    println(s"Match server: match ${h.id} ended after ${outcome.playedSeconds}s")
    timers.schedule(new Runnable {
      def run(): Unit = lifecycle.execute(() => guarded("scoreboard")(showScoreboard(h, recorded = false)))
    }, config.recordWaitMs, TimeUnit.MILLISECONDS)
  }

  /** SCORE_END once the central server has kept the results (or the wait for it ran out). */
  private def showScoreboard(h: Hosted, recorded: Boolean): Unit =
    if (h.finishing && h.instance != null && h.shownScoreboard.compareAndSet(false, true)) {
      if (!recorded) System.err.println(s"Match server: the central server didn't say it had kept match ${h.id}'s results")
      MatchScoring.sendScoreEnd(h.instance, h.spec.lobbyId)
      release(h)
    }

  private def abandon(h: Hosted, why: String): Unit = {
    if (h.finishing) return
    h.finishing = true
    playing.remove(h.spec.lobbyId, h)
    if (h.instance != null) {
      h.instance.journal.note(why)
      h.instance.stop()
    }
    println(s"Match server: match ${h.id}: $why")
    release(h)
  }

  /** Let the match's players go (a moment later, for what was just sent to reach them), and its
    * slots with them. */
  private def release(h: Hosted): Unit =
    try timers.schedule(new Runnable {
      def run(): Unit = guarded("release") {
        h.stubs.keys.foreach { pid =>
          val ch = h.channels.get(pid)
          // A player whose session is another match's by now (they left this one, and the central
          // server sent them to another here) keeps it: only this match's is ended
          if (sessionIn.remove(pid, h)) {
            if (ch != null) sessions.channelClosed(ch)
            sessions.endSession(pid)
          }
          if (ch != null) ch.close()
        }
        forget(h)
        tell(MatchSpec.about("released", h.id))
        if (draining && matches.isEmpty) println("Match server: drained")
      }
    }, ReleaseDelayMs, TimeUnit.MILLISECONDS)
    catch { case _: java.util.concurrent.RejectedExecutionException => }

  /** The match is gone from here, its slots free. */
  private def forget(h: Hosted): Unit = {
    matches.remove(h.id, h)
    playing.remove(h.spec.lobbyId, h)
    h.stubs.keys.foreach(pid => seatedIn.remove(pid, h))
    syncAgones()
  }

  /** Agones told what it plays: Allocated while it plays anything, so a fleet scaling down leaves
    * it be; Ready while it plays nothing and the central server has it (registered, not draining);
    * its `matches` counter the slots taken (never over the counter's capacity, which a match bigger
    * than the whole of it would take it past). */
  private def syncAgones(): Unit = config.agones.foreach { a =>
    try agonesCalls.execute(() => guarded("agones") {
      val used = slotsUsed
      if (agonesCounter) a.setCount(Agones.MatchesCounter, math.min(used, slotCapacity).toLong)
      if (used > 0) { if (agonesState != "Allocated" && a.allocate()) agonesState = "Allocated" }
      else if (agonesState != "Ready" && link != null && !draining) { if (a.ready()) agonesState = "Ready" }
    }) catch { case _: java.util.concurrent.RejectedExecutionException => }
  }

  /** A player is out of the match, and (unless the central server said so) the central server told. */
  private def playerGone(h: Hosted, pid: UUID, why: String, tell: Boolean = true): Unit =
    if (!h.finishing && h.gone.add(pid)) {
      if (h.instance != null) h.instance.handler.removePlayer(pid, why)
      if (tell) this.tell(MatchSpec.about("left", h.id, pid))
    }

  private def cleanup(): Unit = {
    val now = System.currentTimeMillis()
    rateLimiter.cleanup()
    replayGuard.cleanupStale(sessionIn.keySet())
    matches.values().forEach { h =>
      if (h.instance != null && !h.finishing) h.spec.players.foreach { sp =>
        if (!h.gone.contains(sp.id)) {
          if (!h.admitted.contains(sp.id)) {
            if (now - h.startedAt > config.noShowMs) playerGone(h, sp.id, "never connected to the match server")
          } else {
            val p = h.instance.registry.get(sp.id)
            // Gone quiet: its connection is closed, and the disconnect takes it out of the match
            if (p != null && now - p.getLastUpdateTime > Constants.CLIENT_TIMEOUT_MS && p.getTcpChannel != null)
              p.getTcpChannel.asInstanceOf[Channel].close()
          }
        }
      }
    }
  }

  // ── Its status, in its log ─────────────────────────────────────────────────

  private val os = ManagementFactory.getOperatingSystemMXBean.asInstanceOf[com.sun.management.OperatingSystemMXBean]
  private val gcs = ManagementFactory.getGarbageCollectorMXBeans.asScala.toSeq
  @volatile private var lastCpuNs = os.getProcessCpuTime
  @volatile private var lastAt = System.nanoTime()
  @volatile private var lastGcs = 0L
  @volatile private var lastGcMs = 0L
  @volatile private var saidIdle = true

  /** Its matches, players, ticks, CPU, garbage collections and heap since the last: every
    * `statusEveryMs` while it plays anything, and once as it stops. */
  private def logStatus(): Unit = {
    val cpu = os.getProcessCpuTime
    val now = System.nanoTime()
    val cores = (cpu - lastCpuNs).toDouble / math.max(1L, now - lastAt)
    lastCpuNs = cpu
    lastAt = now
    val gcCount = gcs.map(_.getCollectionCount.max(0L)).sum
    val gcMs = gcs.map(_.getCollectionTime.max(0L)).sum
    val (collections, collectedMs) = (gcCount - lastGcs, gcMs - lastGcMs)
    lastGcs = gcCount
    lastGcMs = gcMs
    val window = TickStats.take()
    if (matches.isEmpty && saidIdle) return
    saidIdle = matches.isEmpty
    val heap = ManagementFactory.getMemoryMXBean.getHeapMemoryUsage
    val players = playing.values().asScala.iterator.map(_.admitted.size).sum
    println(f"Match server: status: ${playing.size()} match(es) playing, $slotsUsed of $slotCapacity slots, " +
      f"$players players; ticks ${window.describe}; CPU $cores%.2f of ${Runtime.getRuntime.availableProcessors} " +
      f"core(s); GC $collections collection(s), ${collectedMs}ms; heap ${heap.getUsed / 1048576}MB of " +
      f"${heap.getMax / 1048576}MB; ${ManagementFactory.getThreadMXBean.getThreadCount} threads")
  }

  // ── The players ────────────────────────────────────────────────────────────

  def handleIncomingPacket(packet: Packet, tcpCh: Channel, udpSender: InetSocketAddress): Unit = {
    Metrics.packetsReceived.add(1L, Attrs.packet(packet.getType))
    try {
      if (udpSender != null && packet.getType.tcp) {
        Metrics.packetsDropped.add(1L, Attrs.ReasonTcpOnlyOverUdp)
        return
      }
      // A TCP packet names the player its channel logged in as, or none at all
      if (packet.getType != PacketType.AUTH_REQUEST && tcpCh != null) {
        val authenticatedId = sessions.channelToPlayer.get(tcpCh)
        if (authenticatedId == null) { Metrics.packetsDropped.add(1L, Attrs.ReasonUnauthenticated); return }
        if (packet.getPlayerId != null && !authenticatedId.equals(packet.getPlayerId)) {
          Metrics.packetsDropped.add(1L, Attrs.ReasonUuidMismatch)
          return
        }
      }
      if (packet.getType == PacketType.AUTH_REQUEST) {
        handleMatchJoin(packet.asInstanceOf[AuthRequestPacket], tcpCh)
        return
      }

      // The match the sender's session was admitted to (the handlers checked the session is theirs)
      val h = if (packet.getPlayerId == null) null else sessionIn.get(packet.getPlayerId)
      val instance = if (h == null) null else h.instance
      if (instance == null) return
      val p = instance.registry.get(packet.getPlayerId)
      if (p != null) p.updateHeartbeat()

      packet.getType match {
        case PacketType.HEARTBEAT =>
          if (p != null) {
            if (udpSender != null) {
              val expected = sessions.playerTcpAddresses.get(p.getId)
              if (expected != null && udpSender.getAddress.equals(expected)) p.setUdpAddress(udpSender)
            }
            outbox.sendOverTcp(p)(new HeartbeatPacket(outbox.nextSeq(), p.getId))
          }

        case PacketType.CHAT_MESSAGE => chat(packet.asInstanceOf[ChatMessagePacket], h, instance)

        case PacketType.PLAYER_LEAVE => playerGone(h, packet.getPlayerId, "left the match: its client said so")

        // The central server's: a client that sent one here is mistaken
        case PacketType.LOBBY_ACTION | PacketType.PLAYER_JOIN | PacketType.RANKED_QUEUE | PacketType.MATCH_HISTORY |
             PacketType.LEADERBOARD | PacketType.PROBLEM_REPORT =>

        case _ =>
          if (!h.finishing && p != null) {
            if (instance.handler.processPacket(packet, tcpCh, udpSender))
              instance.broadcastToInstanceExcluding(instance.relayed(packet), packet.getPlayerId)
          }
      }
    } catch {
      case e: IllegalArgumentException =>
        System.err.println(s"Match server: invalid packet: ${e.getMessage}")
        Metrics.packetsDropped.add(1L, Attrs.ReasonMalformed)
    }
  }

  /** A client logging in with its match ticket: given a session, and sent its match. */
  private def handleMatchJoin(request: AuthRequestPacket, ch: Channel): Unit = {
    if (ch == null) return
    val address = ch.remoteAddress() match {
      case a: InetSocketAddress => a.getAddress
      case _ => null
    }
    def refuse(why: String, failure: Boolean): Unit = {
      if (failure && address != null) rateLimiter.recordAuthFailure(address)
      outbox.sendVia(new AuthResponsePacket(outbox.nextSeq(), false, null, why), ch)
      ch.close()
    }
    if (request.getAction != AuthAction.MATCH_JOIN) return refuse("Not a central server", failure = false)
    if (address != null && !rateLimiter.allowAuthAttempt(address)) return refuse(AuthRules.TooManyAttempts, failure = false)

    val token = request.getRefreshToken
    val ticket = if (token == null) null else java.util.Arrays.copyOfRange(token, 0, MatchAssignPacket.TicketSize)
    // Every ticket of every match compared, in constant time: which one matched, if any, says
    // nothing by its timing
    var found: (Hosted, SpecPlayer) = null
    if (ticket != null) matches.values().forEach { h =>
      h.spec.players.foreach(sp => if (MessageDigest.isEqual(sp.ticket, ticket)) found = (h, sp))
    }
    if (found == null) return refuse("Not in this match", failure = true)
    val (h, sp) = found
    val instance = h.instance
    val player = if (instance == null) null else instance.registry.get(sp.id)
    if (player == null || h.finishing || h.gone.contains(sp.id)) return refuse("Not in this match", failure = false)

    val p = player
    if (address != null) {
      sessions.playerTcpAddresses.put(p.getId, address)
      rateLimiter.clearAuthFailures(address)
    }
    sessions.accountNames.put(p.getId, p.getName)
    // Theirs from now: what they send goes to this match. A session of theirs here for another match
    // (one they left, if the close of its connection hasn't come through yet) is replaced by this one
    // and out of that match
    val before = sessionIn.put(p.getId, h)
    val sessionToken = sessions.generateSessionToken(p.getId, ch)
    if (before != null && (before ne h)) playerGone(before, p.getId, "logged in to another match on this server")
    outbox.sendVia(new SessionTokenPacket(outbox.nextSeq(), p.getId, sessionToken), ch)
    outbox.sendVia(new AuthResponsePacket(outbox.nextSeq(), true, p.getId, "Joined the match"), ch)
    h.channels.put(p.getId, ch)
    h.admitted.add(p.getId)

    // The match is starting, as far as this client knows: then everything of it
    val lobby = h.lobby
    outbox.sendVia(new LobbyActionPacket(
      outbox.nextSeq(), lobby.hostId, LobbyAction.GAME_STARTING, lobby.id,
      lobby.mapIndex.toByte, lobby.durationMinutes.toByte,
      lobby.playerCount.toByte, lobby.maxPlayers.toByte,
      lobby.status, lobby.name
    ), ch)
    instance.handler.admit(p, ch)
    println(s"Match server: ${p.getId.toString.substring(0, 8)} ('${p.getName}') joined match ${h.id}")
  }

  /** A match's chat: to everyone in it, or to the sender's team. */
  private def chat(packet: ChatMessagePacket, h: Hosted, instance: GameInstance): Unit = {
    val pid = packet.getPlayerId
    if (!rateLimiter.allowChat(pid)) { Metrics.rateLimitTriggered.add(1L, Attrs.RlChat); return }
    val text = packet.getMessage.filter(c => c >= 32 && c < 127).trim
    if (text.isEmpty) return
    val out = new ChatMessagePacket(outbox.nextSeq(), pid, Packet.getCurrentTimestamp, packet.getScope, text)
    packet.getScope match {
      case ChatScope.GAME =>
        Metrics.chatMessages.add(1L, Attrs.ChatGame)
        instance.broadcastToInstance(out)
      case ChatScope.TEAM if h.lobby.gameMode == 1 =>
        Metrics.chatMessages.add(1L, Attrs.ChatTeam)
        val team: java.lang.Byte = instance.teamAssignments.get(pid)
        if (team != null) {
          val data = out.serialize()
          instance.registry.getAll.asScala.foreach { p =>
            val t: java.lang.Byte = instance.teamAssignments.get(p.getId)
            if (t != null && t.byteValue() == team.byteValue()) outbox.sendRaw(data, true, p)
          }
        }
      case _ =>
    }
  }

  def handleDisconnect(channel: Channel): Unit = {
    // A channel that was the player's session's (one replaced or let go is forgotten before it closes)
    val pid = sessions.channelClosed(channel)
    if (pid == null) return
    sessions.endSession(pid)
    val h = sessionIn.remove(pid)
    if (h != null && h.instance != null && h.instance.registry.contains(pid))
      playerGone(h, pid, "lost its connection to the match server, and is out of the match")
  }

  // ── What the launcher needs (LobbyHost) ────────────────────────────────────

  def getConnectedPlayer(playerId: UUID): Player = {
    val h = seatedIn.get(playerId)
    if (h == null) null else h.stubs.getOrElse(playerId, null)
  }

  def registerGameInstance(lobbyId: Short, instance: GameInstance): Unit = {
    val h = playing.get(lobbyId)
    if (h != null) h.instance = instance
  }

  def unregisterGameInstance(lobbyId: Short): Unit = ()

  /** A match's time is up (its own timer), or practice was ended. */
  def endGame(lobbyId: Short): Unit = {
    val h = playing.get(lobbyId)
    if (h != null) lifecycle.execute(() => guarded("end")(finish(h)))
  }

  private def guarded(what: String)(body: => Unit): Unit =
    try body catch { case e: Throwable => System.err.println(s"Match server: $what failed: $e"); e.printStackTrace() }
}

object MatchServer {
  private val RedialMs = 2000L
  private val ReleaseDelayMs = 2000L

  /** How many slots of matches a match server plays at once when neither its flag nor Agones says:
    * what the load test found a pod of the fleet's size plays without its ticks running late
    * (ops/kubernetes/CLAUDE.md, *How many matches a match server plays*). */
  val DefaultCapacity = 16

  /**
   * @param gamePort        where players connect (TCP and UDP)
   * @param centralHost     the central server's control port: where to dial
   * @param secret          the cluster's secret (GRIDGAME_CLUSTER_SECRET)
   * @param publicHost      the address players are told, if not Agones' (or 127.0.0.1)
   * @param publicPort      the port players are told, if not Agones' (or gamePort)
   * @param capacity        how many slots of matches it plays at once, if not Agones' counter's
   *                        capacity (or DefaultCapacity)
   * @param centralIdentity the central server's key, if it is to be held to one
   * @param noShowMs        how long a player has to connect before they are taken out
   * @param recordWaitMs    how long the scoreboard waits for the central server to keep the results
   * @param statusEveryMs   how often it logs its status while it plays anything
   */
  final case class Config(gamePort: Int, centralHost: String, controlPort: Int, secret: Array[Byte],
                          publicHost: Option[String] = None, publicPort: Option[Int] = None,
                          capacity: Option[Int] = None, centralIdentity: Option[String] = None,
                          agones: Option[Agones] = None, noShowMs: Long = 30000L, recordWaitMs: Long = 10000L,
                          statusEveryMs: Long = 30000L)

  private def daemon(r: Runnable, name: String): Thread = {
    val t = new Thread(r, name)
    t.setDaemon(true)
    t
  }
}
