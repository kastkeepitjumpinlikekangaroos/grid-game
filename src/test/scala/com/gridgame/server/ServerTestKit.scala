package com.gridgame.server

import com.gridgame.common.Constants
import com.gridgame.common.model._
import com.gridgame.common.protocol._
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.channel.socket.DatagramPacket

import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import scala.collection.mutable

object ServerTestKit {
  // Building a GameServer generates a TLS certificate and opens the auth database, so a test
  // class shares one. It is never started, so it holds no sockets.
  lazy val server = new GameServer(0)

  private val nextGameId = new AtomicInteger(100)
  private val nextPort = new AtomicInteger(40000)

  def freshPort(): Int = nextPort.incrementAndGet()
  def freshGameId(): Short = nextGameId.incrementAndGet().toShort

  /** Decode what the server wrote: 80 bytes, the payload first (unsigned, as no test player has
    * a session token). */
  def decode(buf: ByteBuf): Packet = {
    val bytes = new Array[Byte](Constants.PACKET_PAYLOAD_SIZE)
    buf.getBytes(buf.readerIndex(), bytes)
    buf.release()
    PacketSerializer.deserialize(bytes)
  }

  /** An EmbeddedChannel whose far end looks like a real TCP peer, for code that asks the channel
    * for the client's address. */
  class PeerChannel(host: String = "127.0.0.1") extends EmbeddedChannel() {
    private val peer = new InetSocketAddress(host, freshPort())
    override protected def remoteAddress0(): SocketAddress = peer
  }

  /**
   * A client that has logged in: its TCP channel runs the server's real handler, and it holds a
   * session token and the address it logged in from, as a login leaves them. What it sends goes
   * in through that handler, signed and numbered as a real client's is.
   */
  class TestSession(val playerId: UUID = UUID.randomUUID()) {
    val channel = new PeerChannel()
    channel.pipeline().addLast(new GameServerTcpHandler(server))
    val token: Array[Byte] = server.generateSessionToken(playerId, channel)
    server.playerTcpAddresses.put(playerId, InetAddress.getByName("127.0.0.1"))
    private var seq = 0

    def nextSeq(): Int = { seq += 1; seq }

    /** Send over TCP as the client does: its own count, signed with its token. */
    def send(make: Int => Packet): Unit = sendSigned(make(nextSeq()), token)

    def sendSigned(p: Packet, key: Array[Byte]): Unit =
      channel.writeInbound(Unpooled.wrappedBuffer(PacketSigner.sign(p.serialize(), key)))

    /** The join every client sends as soon as it has logged in. */
    def join(name: String = "p"): Unit =
      send(n => new PlayerJoinPacket(n, playerId, new Position(1, 1), 0xFF00FF00, name))

    def lobbyAction(action: Byte, lobbyId: Short = 0): Unit =
      send(n => new LobbyActionPacket(n, playerId, Packet.getCurrentTimestamp, action, lobbyId, 0.toByte,
        5.toByte, 0.toByte, 8.toByte, 0.toByte, "mine"))

    def createLobby(): Unit = lobbyAction(LobbyAction.CREATE)

    def queueRanked(mode: Byte, charId: Byte = 0): Unit =
      send(n => new RankedQueuePacket(n, playerId, RankedQueueAction.QUEUE_JOIN, charId, mode))

    /** What the server wrote to this client since the last call. */
    def received(): Seq[Packet] = {
      channel.runPendingTasks()
      Iterator.continually(channel.readOutbound[ByteBuf]()).takeWhile(_ != null).map(decode).toSeq
    }

    def lobby: Lobby = server.lobbyManager.getPlayerLobby(playerId)
  }
}

/**
 * One match, driven by hand: nothing runs on its own, the test calls the ticks. Players get a TCP
 * channel and a UDP address of their own, and everything the server sends them is kept, per
 * player, to be read back with [[tcpSent]] and [[udpSent]].
 *
 * The match is under way: its opening — a wall down the middle of the map in Teams, holstered
 * abilities in a free-for-all ([[com.gridgame.common.model.MatchOpening]]) — is over unless the
 * test asks for it with `opening = true`.
 */
class TestMatch(val world: WorldData = WorldData.createEmpty(60, 60), gameMode: Byte = 0,
                opening: Boolean = false, practice: Boolean = false) {
  import ServerTestKit._

  val server: GameServer = ServerTestKit.server
  private val udp = new EmbeddedChannel()
  server.attachUdpChannel(udp)

  val instance: GameInstance = {
    val gi = new GameInstance(freshGameId(), "", 5, server)
    gi.world = world
    gi.gameMode = gameMode
    gi.isPractice = practice
    gi.begin()
    if (!opening) gi.skipOpening()
    gi
  }

  private val tcp = mutable.Map[UUID, EmbeddedChannel]()
  private val udpInbox = mutable.Map[InetSocketAddress, mutable.Buffer[Packet]]()
  private var seq = 0

  def join(character: CharacterId, x: Int, y: Int, team: Byte = 0): Player = {
    val p = new Player(UUID.randomUUID(), character.name, new Position(x, y), 0xFF336699)
    p.setCharacterId(character.id)
    p.setHealth(p.getMaxHealth)
    p.setTeamId(team)
    if (team != 0) instance.teamAssignments.put(p.getId, team)
    val ch = new EmbeddedChannel()
    p.setTcpChannel(ch)
    tcp(p.getId) = ch
    val addr = new InetSocketAddress("127.0.0.1", freshPort())
    p.setUdpAddress(addr)
    udpInbox(addr) = mutable.Buffer.empty
    instance.registry.add(p)
    instance.killTracker.registerPlayer(p.getId)
    p
  }

  /** What the player was sent over TCP since the last call. */
  def tcpSent(p: Player): Seq[Packet] = {
    val ch = tcp(p.getId)
    ch.runPendingTasks()
    Iterator.continually(ch.readOutbound[ByteBuf]()).takeWhile(_ != null).map(decode).toSeq
  }

  /** What the player was sent over UDP since the last call. */
  def udpSent(p: Player): Seq[Packet] = {
    drainUdp()
    val inbox = udpInbox(p.getUdpAddress)
    val out = inbox.toList
    inbox.clear()
    out
  }

  private def drainUdp(): Unit = {
    udp.runPendingTasks()
    Iterator.continually(udp.readOutbound[DatagramPacket]()).takeWhile(_ != null).foreach { d =>
      udpInbox.get(d.recipient()) match {
        case Some(inbox) => inbox += decode(d.content())
        case None => d.release()
      }
    }
  }

  /** Forget everything sent so far. */
  def clearSent(): Unit = {
    tcp.keys.foreach(id => instance.registry.get(id) match { case null => case p => tcpSent(p) })
    drainUdp()
    udpInbox.values.foreach(_.clear())
  }

  /** Everything the player was sent, over either transport, since the last call. */
  def sent(p: Player): Seq[Packet] = tcpSent(p) ++ udpSent(p)

  def updatesAbout(packets: Seq[Packet], who: Player): Seq[PlayerUpdatePacket] =
    packets.collect { case u: PlayerUpdatePacket if u.getPlayerId == who.getId => u }

  def projectileEvents(packets: Seq[Packet], action: Byte): Seq[ProjectilePacket] =
    packets.collect { case pp: ProjectilePacket if pp.getAction == action => pp }

  def tick(n: Int = 1): Unit = for (_ <- 0 until n) instance.tickProjectiles()

  /** A projectile of the player's, put straight into flight (no fire-rate check). */
  def launch(owner: Player, pType: Byte, dx: Float, dy: Float, from: (Int, Int) = null): Projectile = {
    val (x, y) = if (from != null) from else (owner.getPosition.getX, owner.getPosition.getY)
    val p = instance.projectileManager.spawnProjectile(owner.getId, x, y, dx, dy, owner.getColorRGB, 0, pType)
    instance.broadcastProjectileSpawn(p)
    p
  }

  /** Fly the projectile until it is gone (hit, stopped or out of range). */
  def tickUntilGone(p: Projectile, maxTicks: Int = 400): Unit = {
    var n = 0
    while (instance.projectileManager.getProjectile(p.id) != null && n < maxTicks) { tick(); n += 1 }
  }

  /** A position update from the player's client, as it would send it: the server moves it has
    * heard of by default. `flags2` and `aimAngle` are the second flag byte and the aim, in
    * radians (a barrier's bit and its facing). Returns whether the server accepted it. */
  def move(p: Player, x: Int, y: Int, serverMoves: Int = -1, flags: Int = 0, gapMs: Long = 2,
           flags2: Int = 0, aimAngle: Double = 0.0): Boolean = {
    if (gapMs > 0) Thread.sleep(gapMs)
    seq += 1
    val moves = if (serverMoves >= 0) serverMoves else p.getServerMoves
    instance.handler.processPacket(
      new PlayerUpdatePacket(seq, p.getId, Packet.getCurrentTimestamp, new Position(x, y), p.getColorRGB,
        p.getHealth, 0, flags, p.getCharacterId, p.getTeamId, moves, flags2,
        PlayerUpdatePacket.encodeAimAngle(aimAngle)),
      null, p.getUdpAddress)
  }

  /** Spawn requests from the player's client for one press of an attack, one per heading, as the
    * wire carries them. Returns the projectiles the server spawned for them. */
  def fire(p: Player, slot: Int, pType: Byte, headings: Seq[(Float, Float)]): Seq[Projectile] = {
    val before = instance.projectileManager.getAll.map(_.id).toSet
    val pos = p.getPosition
    for ((dx, dy) <- headings) {
      seq += 1
      instance.handler.processPacket(
        PacketSerializer.deserialize(ProjectilePacket.spawnRequest(seq, p.getId, pos.getX.toFloat, pos.getY.toFloat,
          p.getColorRGB, dx, dy, 0.toByte, pType, slot).serialize()),
        null, null)
    }
    instance.projectileManager.getAll.filterNot(pr => before.contains(pr.id))
  }

  /** A trap placement from the player's client, as the wire carries it: the trap it put on the
    * ground, or null if the server refused it. */
  def placeTrap(p: Player, slot: Int, trapType: Byte, x: Int, y: Int): Trap = {
    val before = instance.trapManager.getAll.map(_.id).toSet
    seq += 1
    instance.handler.processPacket(
      PacketSerializer.deserialize(new TrapPacket(seq, p.getId, x, y, 0, TrapAction.PLACE, trapType,
        p.getTeamId, slot, null).serialize()),
      null, null)
    instance.trapManager.getAll.find(t => !before.contains(t.id)).orNull
  }

  /** A trap already on the ground and past its arming delay, without the wait or the checks. */
  def armedTrap(owner: Player, x: Int, y: Int, trapType: Byte): Trap = {
    val placed = instance.trapManager.place(owner.getId, owner.getTeamId, x, y, trapType,
      System.currentTimeMillis() - 5000)
    if (placed == null) null else placed.trap
  }

  /** A player's client rejoining a match already in progress. */
  def rejoin(p: Player): Unit = {
    seq += 1
    val pos = p.getPosition
    instance.handler.processPacket(
      new PlayerJoinPacket(seq, p.getId, Packet.getCurrentTimestamp, pos, p.getColorRGB, p.getName,
        p.getHealth, p.getCharacterId, p.getTeamId),
      tcp(p.getId), null)
  }

  /**
   * Put these logged-in players into this match, as starting a lobby does: members of an in-game
   * lobby (the first its host), each registered in the match on their own connection, at a cell of
   * their own. With `teams`, the lobby plays Teams and each is dealt the team given for them.
   */
  def seat(sessions: Seq[ServerTestKit.TestSession], teams: Seq[Byte] = Nil): Lobby = {
    val lobby = server.lobbyManager.createLobby(sessions.head.playerId, "match", 0, 5, 8)
    sessions.tail.foreach { s => lobby.addPlayer(s.playerId); server.lobbyManager.setPlayerLobby(s.playerId, lobby.id) }
    if (teams.nonEmpty) lobby.gameMode = 1
    lobby.gameInstance = instance
    lobby.status = LobbyStatus.IN_GAME
    sessions.zipWithIndex.foreach { case (s, i) =>
      val p = new Player(s.playerId, s"p$i", new Position(10 + 5 * i, 10), 0xFF00FF00)
      p.setCharacterId(CharacterId.Gladiator.id)
      p.setHealth(p.getMaxHealth)
      p.setTcpChannel(s.channel)
      if (teams.nonEmpty) {
        p.setTeamId(teams(i))
        instance.teamAssignments.put(s.playerId, teams(i))
      }
      instance.registry.add(p)
      instance.killTracker.registerPlayer(s.playerId)
    }
    lobby
  }

  def trapEvents(packets: Seq[Packet], action: Byte): Seq[TrapPacket] =
    packets.collect { case tp: TrapPacket if tp.getAction == action => tp }

  def useItem(p: Player, itemType: ItemType, x: Int = 0, y: Int = 0): Item = {
    seq += 1
    val item = new Item(10000 + seq, 0, 0, itemType)
    instance.itemManager.addToInventory(p.getId, item)
    instance.handler.processPacket(new ItemPacket(seq, p.getId, x, y, itemType.id, item.id, ItemAction.USE), null, null)
    item
  }

  def hasItem(p: Player, item: Item): Boolean = instance.itemManager.getInventory(p.getId).exists(_.id == item.id)

  def at(p: Player): (Int, Int) = (p.getPosition.getX, p.getPosition.getY)
}
