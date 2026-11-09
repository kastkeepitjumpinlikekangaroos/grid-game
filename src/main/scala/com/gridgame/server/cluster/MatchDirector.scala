package com.gridgame.server.cluster

import com.gridgame.common.model.Player
import com.gridgame.common.protocol.MatchAssignPacket
import com.gridgame.server.game.MatchOutcome
import com.gridgame.server.lobby.{Lobby, RemoteMatch, RemoteMatches}
import com.gridgame.server.net.{Outbox, TlsProvider}
import com.google.gson.JsonObject
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.{Channel, ChannelInitializer, ChannelOption}
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel

import java.net.InetAddress
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import scala.jdk.CollectionConverters._

/** What the match servers' director needs of the central server: its players, and what to do as each
  * match it handed out goes. */
trait DirectorHost {
  def outbox: Outbox
  def getConnectedPlayer(playerId: UUID): Player
  /** The match's lobby, if it is still that match's. */
  def lobbyOfMatch(lobbyId: Short, matchId: UUID): Lobby
  /** The match server couldn't start it: its players are told the lobby closed. */
  def matchFailed(lobby: Lobby, why: String): Unit
  /** A player is out of it (left, lost their connection to the match server, never came). */
  def playerLeftMatch(lobby: Lobby, playerId: UUID): Unit
  /** It ended there, scored, with its journal. */
  def matchEnded(lobby: Lobby, outcome: MatchOutcome, journal: String): Unit
  /** Its match server went away with it unfinished: it is over, without a result. */
  def matchLost(lobby: Lobby): Unit
}

/**
 * The central server's side of the match servers (design/networking.md, *Match servers*): it
 * listens for them on the control port, keeps those that have registered with their capacity (in
 * slots: `MatchSpec.slotsFor`), and hands each lobby's match to one with room for it (`launch`). It
 * packs them: of the servers with room, the one with the most slots taken, so that the rest empty
 * and an autoscaler can take them away. With none that has room, the match waits, the oldest first,
 * for one to make room or register within `waitMs` (an autoscaler adds them as matches fill them: a
 * lobby that starts in the moment between waits for it, rather than closing). The match server
 * starts it and says so ("started"); only then are the match's players told where to play it
 * (MATCH_ASSIGN: the server's address, a ticket each, and the key it will show them), so none arrives
 * before it has the match. A match server that gives one back ("failed" to be tried again: full, or
 * draining) has it handed to another, first in line. What happens to the match after comes back over
 * the link: a player leaving it, its end with its outcome (kept here, then "recorded" back so its
 * players see the scoreboard), its slots free again ("released"), or the link going with it.
 *
 * Messages from a match server: register, started, failed, left, ended, released, draining. To one:
 * start, remove, end, abandon, recorded.
 */
final class MatchDirector(port: Int, secret: Array[Byte], identity: TlsProvider.Identity, host: DirectorHost,
                          waitMs: Long = MatchDirector.WaitMs)
    extends RemoteMatches {

  /** A registered match server: where its players connect, the key it shows them, its capacity, and
    * the matches it holds (until it releases them). */
  private final class Server(val link: ControlLink, val address: InetAddress, val port: Int,
                             val keyHash: Array[Byte], val build: String, val capacity: Int, val order: Long) {
    val matches = new ConcurrentHashMap[UUID, Handle]()
    @volatile var draining = false
    // A match it gave back with room by our count: none until then (its count and ours differ for a moment)
    @volatile var refusingUntil = 0L
    def used: Int = matches.values().asScala.iterator.map(_.slots).sum
    /** Whether it can take a match of `slots`: one bigger than its whole capacity only when empty. */
    def hasRoomFor(slots: Int, now: Long): Boolean =
      !draining && link.isOpen && now >= refusingUntil && { val u = used; u == 0 || u + slots <= capacity }
    override def toString: String = s"${address.getHostAddress}:$port"
  }

  /** A lobby's match on a match server, or waiting for one to have room. */
  private final class Handle(val lobby: Lobby, val withBots: Boolean, val matchId: UUID) extends RemoteMatch {
    val queuedAt: Long = System.currentTimeMillis()
    @volatile var server: Server = null
    @volatile var tickets: Map[UUID, Array[Byte]] = Map.empty
    @volatile var slots: Int = 1
    @volatile var over = false
    def lobbyId: Short = lobby.id

    // Waiting, there is nothing to tell: its players are read from the lobby as it is handed out
    def removePlayer(playerId: UUID): Unit = {
      val s = server
      if (!over && s != null) s.link.send(MatchSpec.about("remove", matchId, playerId))
    }

    def endNow(): Unit = if (!over) {
      val s = server
      if (s != null) s.link.send(MatchSpec.about("end", matchId))
      else if (waiting.remove(this)) {
        over = true
        host.matchFailed(lobby, "ended before a match server had room")
      }
    }

    def abandon(): Unit = if (!over) {
      over = true
      val s = server
      if (s != null) s.link.send(MatchSpec.about("abandon", matchId)) else waiting.remove(this)
    }
  }

  // Matches waiting for a match server, oldest first (one given back goes first again)
  private val waiting = new java.util.concurrent.ConcurrentLinkedDeque[Handle]()
  private val timer = java.util.concurrent.Executors.newSingleThreadScheduledExecutor { r =>
    val t = new Thread(r, "MatchDirector")
    t.setDaemon(true)
    t
  }

  private val servers = new ConcurrentHashMap[ControlLink, Server]()
  private val registrations = new AtomicLong()
  private val random = new SecureRandom()
  private var group: NioEventLoopGroup = _
  private var listening: Channel = _

  /** Listen for match servers. */
  def bind(): Unit = {
    group = new NioEventLoopGroup(2)
    listening = new ServerBootstrap()
      .group(group)
      .channel(classOf[NioServerSocketChannel])
      .childHandler(new ChannelInitializer[SocketChannel] {
        override def initChannel(ch: SocketChannel): Unit =
          ControlLink.pipeline(ch, ControlLink.serverTls(identity.context, ch), central = true, secret, _ => listener)
      })
      .childOption[java.lang.Boolean](ChannelOption.TCP_NODELAY, true)
      .bind(port).sync().channel()
    println(s"Match servers: listening for them on port $port")
    timer.scheduleAtFixedRate(() => try { giveUpWaiting(); dispatch() } catch { case e: Throwable => System.err.println(s"Match servers: $e") },
      1, 1, java.util.concurrent.TimeUnit.SECONDS)
  }

  /** A match that has waited `waitMs` for a match server doesn't start: its lobby closes. */
  private def giveUpWaiting(now: Long = System.currentTimeMillis()): Unit =
    waiting.asScala.foreach { h =>
      if (now - h.queuedAt > waitMs && waiting.remove(h)) {
        h.over = true
        host.matchFailed(h.lobby, s"no match server had room in ${waitMs / 1000}s (${servers.size()} registered, $free slots free)")
      }
    }

  def stop(): Unit = {
    timer.shutdownNow()
    servers.keySet().asScala.foreach(_.close())
    if (listening != null) listening.close().sync()
    if (group != null) group.shutdownGracefully()
  }

  /** Match servers registered. */
  def registered: Int = servers.size()

  /** The slots free on the match servers taking matches. */
  def free: Int = servers.values().asScala.iterator.filter(s => !s.draining && s.link.isOpen)
    .map(s => math.max(0, s.capacity - s.used)).sum

  /** Matches handed out and not yet released. */
  def playing: Int = servers.values().asScala.iterator.map(_.matches.size()).sum

  /** Matches waiting for a match server. */
  def queued: Int = waiting.size()

  def launch(lobby: Lobby, withBots: Boolean): RemoteMatch = {
    val handle = new Handle(lobby, withBots, UUID.randomUUID())
    lobby.matchId = handle.matchId
    // Before it can be handed out: what comes back about it finds its lobby by it
    lobby.remoteMatch = handle
    waiting.add(handle)
    dispatch()
    if (handle.server == null)
      println(s"Match servers: lobby ${lobby.id}'s match waits for room on a match server " +
        s"(${servers.size()} registered, $free slots free)")
    handle
  }

  /** Each match waiting, oldest first, to the fullest match server with room for it, while there are both. */
  private def dispatch(): Unit = servers.synchronized {
    val now = System.currentTimeMillis()
    var going = true
    while (going) {
      val h = waiting.peekFirst()
      if (h == null) going = false
      else if (h.over) waiting.remove(h)
      else {
        h.slots = slotsOf(h)
        val fits = servers.values().asScala.filter(_.hasRoomFor(h.slots, now))
        if (fits.isEmpty) going = false
        else {
          waiting.remove(h)
          assign(h, fits.maxBy(s => (s.used, -s.order)))
        }
      }
    }
  }

  private def humansOf(lobby: Lobby): Seq[Player] =
    lobby.players.asScala.toSeq.flatMap(pid => Option(host.getConnectedPlayer(pid)))

  private def slotsOf(h: Handle): Int =
    MatchSpec.slotsFor(humansOf(h.lobby).size, if (h.withBots) h.lobby.botManager.botCount else 0)

  /** Hand a match to a match server: its players as its lobby has them now, a ticket each. */
  private def assign(h: Handle, server: Server): Unit = {
    val lobby = h.lobby
    val humans = humansOf(lobby)
    h.tickets = humans.map { p =>
      val t = new Array[Byte](MatchAssignPacket.TicketSize)
      random.nextBytes(t)
      p.getId -> t
    }.toMap
    val spec = MatchSpec(h.matchId, lobby.id, lobby.name, lobby.mapIndex, lobby.durationMinutes, lobby.maxPlayers,
      lobby.gameMode, lobby.teamSize, lobby.matchType, lobby.isRanked, h.withBots,
      humans.map(p => SpecPlayer(p.getId, p.getName, p.getColorRGB, lobby.getCharacter(p.getId), h.tickets(p.getId))),
      if (h.withBots) lobby.botManager.getBots else Nil, h.slots)
    server.matches.put(h.matchId, h)
    h.server = server
    server.link.send(spec.toJson)
    val waited = System.currentTimeMillis() - h.queuedAt
    println(s"Match servers: lobby ${lobby.id}'s match ${h.matchId} to $server (${humans.size} players, ${spec.bots.size} bots, " +
      s"${h.slots} slot(s); ${server.used} of ${server.capacity} taken there${if (waited > 100) s"; it waited ${waited}ms" else ""})")
  }

  private val listener: ControlListener = new ControlListener {
    def onMessage(link: ControlLink, msg: JsonObject): Unit =
      try handle(link, msg)
      catch { case e: Exception => System.err.println(s"Match servers: '${ControlLink.kindOf(msg)}' from $link: $e") }

    def onClosed(link: ControlLink): Unit = {
      val server = servers.remove(link)
      if (server == null) return
      println(s"Match servers: $server went away with ${server.matches.size()} match(es) (${servers.size()} left)")
      server.matches.values().forEach { h =>
        if (!h.over) {
          h.over = true
          val lobby = host.lobbyOfMatch(h.lobbyId, h.matchId)
          if (lobby != null) host.matchLost(lobby)
        }
      }
      server.matches.clear()
    }
  }

  private def str(m: JsonObject, k: String): String = if (m.has(k)) m.get(k).getAsString else null
  private def bool(m: JsonObject, k: String): Boolean = m.has(k) && m.get(k).getAsBoolean

  private def handle(link: ControlLink, msg: JsonObject): Unit = ControlLink.kindOf(msg) match {
    case "register" =>
      val keyHash = MatchAssignPacket.keyHashOf(str(msg, "fingerprint"))
      val address = try InetAddress.getByName(str(msg, "host")) catch { case _: Exception => null }
      val port = if (msg.has("port")) msg.get("port").getAsInt else 0
      val capacity = if (msg.has("capacity")) msg.get("capacity").getAsInt else 1
      if (keyHash == null || address == null || port <= 0 || port > 65535 || capacity < 1) {
        System.err.println(s"Match servers: $link registered without a usable address, key or capacity; closed")
        link.close()
      } else {
        val s = new Server(link, address, port, keyHash, str(msg, "build"), capacity, registrations.incrementAndGet())
        s.draining = bool(msg, "draining")
        servers.put(link, s)
        println(s"Match servers: $s registered (${s.build}, room for $capacity slots; ${servers.size()} in all)")
        dispatch()
      }

    case kind =>
      val server = servers.get(link)
      if (server == null) { link.close(); return }
      kind match {
        case "started" => forMatch(server, msg) { (h, lobby) =>
          println(s"Match servers: ${h.matchId} under way on $server")
          tellPlayers(server, h, lobby)
        }

        case "failed" =>
          val h = handleOf(server, msg)
          if (h != null) {
            server.matches.remove(h.matchId)
            h.server = null
            if (bool(msg, "retry") && !h.over) {
              // Its count of its slots and ours differ (or it drains): another, if any, first in line
              server.refusingUntil = System.currentTimeMillis() + MatchDirector.RefusedForMs
              println(s"Match servers: $server gave ${h.matchId} back (${str(msg, "why")}); handing it to another")
              waiting.addFirst(h)
              dispatch()
            } else if (!h.over) {
              h.over = true
              val lobby = host.lobbyOfMatch(h.lobbyId, h.matchId)
              if (lobby != null) host.matchFailed(lobby, str(msg, "why"))
            }
          }

        case "left" => forMatch(server, msg) { (_, lobby) =>
          val pid = MatchSpec.playerIdOf(msg)
          if (pid != null) host.playerLeftMatch(lobby, pid)
        }

        case "ended" =>
          val outcome = MatchSpec.outcomeFromJson(msg)
          val h = server.matches.get(outcome.matchId)
          if (h != null && !h.over) {
            h.over = true
            val lobby = host.lobbyOfMatch(h.lobbyId, h.matchId)
            if (lobby != null) host.matchEnded(lobby, outcome, str(msg, "journal"))
          }
          // Recorded or not (a lobby gone already), the match server may now show the scoreboard
          link.send(MatchSpec.about("recorded", outcome.matchId))

        case "released" =>
          val h = handleOf(server, msg)
          if (h != null) {
            server.matches.remove(h.matchId)
            // Let go before it ended here: over, without a result (the match server stopped it)
            if (!h.over) {
              h.over = true
              val lobby = host.lobbyOfMatch(h.lobbyId, h.matchId)
              if (lobby != null) host.matchLost(lobby)
            }
          }
          dispatch()

        case "draining" =>
          server.draining = true
          println(s"Match servers: $server is draining: no new matches for it (${server.matches.size()} to finish)")

        case other => System.err.println(s"Match servers: '$other' from $server, ignored")
      }
  }

  private def handleOf(server: Server, msg: JsonObject): Handle = {
    val id = MatchSpec.matchIdOf(msg)
    if (id == null) null else server.matches.get(id)
  }

  /** A message about one of the server's matches, with its lobby if that is still the match's. */
  private def forMatch(server: Server, msg: JsonObject)(f: (Handle, Lobby) => Unit): Unit = {
    val h = handleOf(server, msg)
    if (h == null || h.over) return
    val lobby = host.lobbyOfMatch(h.lobbyId, h.matchId)
    if (lobby != null) f(h, lobby)
  }

  /** Where each of the match's players plays it: the address and their ticket, then the key. */
  private def tellPlayers(server: Server, h: Handle, lobby: Lobby): Unit =
    h.tickets.foreach { case (pid, ticket) =>
      val p = host.getConnectedPlayer(pid)
      if (p != null && lobby.players.contains(pid)) {
        host.outbox.send(MatchAssignPacket.address(host.outbox.nextSeq(), pid, server.address, server.port, ticket), p)
        host.outbox.send(MatchAssignPacket.identity(host.outbox.nextSeq(), pid, server.keyHash), p)
      }
    }
}

object MatchDirector {
  /** How long a match waits for room on a match server before its lobby closes. */
  val WaitMs = 30000L
  /** How long a match server that gave a match back is handed no other. */
  private val RefusedForMs = 1000L
}
