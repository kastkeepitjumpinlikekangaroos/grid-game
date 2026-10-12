package com.gridgame.server

import com.gridgame.common.Constants
import com.gridgame.common.WorldRegistry
import com.gridgame.common.model._
import com.gridgame.common.protocol._
import com.gridgame.server.lobby.LobbyStatus
import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.channel.socket.DatagramPacket
import org.junit.After
import org.junit.Assert._
import org.junit.Test

import java.net.InetSocketAddress
import java.util.UUID
import scala.collection.mutable
import scala.jdk.CollectionConverters._

/**
 * Logged-in clients sending the server whatever they like: every packet type, its fields at random
 * but its layout right, mostly in their own name and sometimes in someone else's, at any point —
 * lobbies made, joined, left and started, practice, the ranked queue and the matches it makes, chat,
 * profiles, and every request a match takes, real matches running on their own threads meanwhile.
 * Most go straight to the server's router (GameServer.handleIncomingPacket), so anything one throws
 * reaches the test; the rest come in through the real TCP and UDP handlers, signed and numbered.
 *
 * After every packet: nothing threw, and the lobbies hang together — a player is in one lobby or
 * none, and that lobby counts them; a waiting lobby has its host and no more seats taken than it
 * has; nobody waits for a ranked match from inside a lobby; there are no more lobbies than the
 * server allows, and every match under way belongs to a lobby in a game. At the end, nothing the
 * server caught and only printed (a match's tick, the bots', matchmaking) went wrong either.
 */
class HostileClientFuzzTest {
  import ServerTestKit._

  @After def endMatches(): Unit = {
    for (id <- server.gameInstances.keySet().asScala.toSeq) server.endGame(id)
    server.gameInstances.values().asScala.foreach(_.stop())
    server.gameInstances.clear()
  }

  @Test def loggedInClientsSendingAnythingLeaveTheServerWhole(): Unit =
    for (seed <- 1 to 6) new Run(seed).play(2500)

  private final class Run(seed: Long) {
    private val rnd = new scala.util.Random(seed)
    private val trace = mutable.Queue[String]()
    private var step = 0
    private val tally = mutable.Map[String, Int]().withDefaultValue(0)

    private def note(s: String): Unit = { trace.enqueue(s"$step: $s"); if (trace.size > 40) trace.dequeue() }

    // What the server printed to System.err, kept for the run, and the lines of it that say something
    // went wrong rather than that a packet was refused (what it caught and carried on from)
    private val printed = new Printed

    private def troubles: Seq[String] = printed.troubles()

    private def fail(why: String): Nothing =
      throw new AssertionError(s"seed $seed, step $step: $why\nThe last packets:\n  ${trace.mkString("\n  ")}" +
        s"\nWhat the server printed of trouble:\n  ${troubles.take(20).mkString("\n  ")}")

    private def check(ok: Boolean, why: => String): Unit = if (!ok) fail(why)

    private val sessions = (0 until 5).map { i =>
      val s = new TestSession()
      s.send(n => new PlayerJoinPacket(n, s.playerId, new Position(1, 1), 0xFF00FF00, s"p$seed-$i"))
      s
    }
    private val ids = sessions.map(_.playerId)
    private val udpChannel = new EmbeddedChannel(server.udpHandler())
    private val serverAddress = new InetSocketAddress("127.0.0.1", Constants.SERVER_PORT)
    // Someone who never logged in, sending logins
    private val stranger = new PeerChannel("127.0.0.9")
    stranger.pipeline().addLast(server.tcpHandler())
    private var seq = 1000

    private def someone: TestSession = sessions(rnd.nextInt(sessions.size))
    private def anyId(own: UUID): UUID = rnd.nextInt(10) match {
      case 0 => ids(rnd.nextInt(ids.size))
      case 1 => UUID.randomUUID()
      case _ => own
    }
    private def b(n: Int = 256): Byte = (rnd.nextInt(n) - (if (n == 256) 128 else 0)).toByte
    private def short(): Short = (rnd.nextInt(65536) - 32768).toShort
    private def cell(): Int = rnd.nextInt(10) match {
      case 0 => rnd.nextInt()
      case 1 => -1 - rnd.nextInt(5)
      case _ => rnd.nextInt(Constants.GRID_SIZE)
    }
    private def text(max: Int): String =
      Seq("", "lobby", "  ", "\u0000x", "名前" * (1 + rnd.nextInt(10)), 0x202E.toChar.toString + "evil", "a" * max, rnd.nextString(rnd.nextInt(max + 1)))(rnd.nextInt(8))
    private def lobbyId(): Short = {
      val live = server.lobbyManager.getActiveLobbies.map(_.id)
      if (live.nonEmpty && rnd.nextInt(4) != 0) live(rnd.nextInt(live.size)) else short()
    }
    private def charId(): Byte = if (rnd.nextInt(5) == 0) b() else CharacterDef.all(rnd.nextInt(CharacterDef.all.size)).id.id
    private def pos(): Position = new Position(rnd.nextInt(Constants.GRID_SIZE), rnd.nextInt(Constants.GRID_SIZE))

    /** A packet a client could send: any type, the layout right, the fields anything. */
    private def packet(s: TestSession): Packet = {
      seq += 1
      val id = anyId(s.playerId)
      rnd.nextInt(20) match {
        case 0 | 1 | 2 | 3 =>
          val action = if (rnd.nextInt(8) == 0) b() else
            Seq(LobbyAction.CREATE, LobbyAction.JOIN, LobbyAction.LEAVE, LobbyAction.START, LobbyAction.LIST_REQUEST,
              LobbyAction.CONFIG_UPDATE, LobbyAction.CHARACTER_SELECT, LobbyAction.ADD_BOT, LobbyAction.REMOVE_BOT,
              LobbyAction.PRACTICE_START)(rnd.nextInt(10))
          new LobbyActionPacket(seq, id, Packet.getCurrentTimestamp, action, lobbyId(), b(), b(), b(), b(), b(),
            text(Constants.MAX_LOBBY_NAME_LEN), charId(), b(3), b(6))
        case 4 =>
          val action = if (rnd.nextInt(6) == 0) b() else
            Seq(RankedQueueAction.QUEUE_JOIN, RankedQueueAction.QUEUE_LEAVE, RankedQueueAction.CHARACTER_CHANGE)(rnd.nextInt(3))
          new RankedQueuePacket(seq, id, action, charId(), b(4))
        case 5 => new ChatMessagePacket(seq, id, Packet.getCurrentTimestamp, b(4), text(Constants.MAX_CHAT_MESSAGE_LEN))
        case 6 => new MatchHistoryPacket(seq, id, if (rnd.nextBoolean()) MatchHistoryAction.QUERY else b())
        case 7 => new LeaderboardPacket(seq, id, if (rnd.nextBoolean()) LeaderboardAction.QUERY else b())
        case 8 => new PlayerJoinPacket(seq, id, Packet.getCurrentTimestamp, pos(), rnd.nextInt(), text(21), rnd.nextInt(), charId(), b(3))
        case 9 => new PlayerLeavePacket(seq, id)
        case 10 | 11 | 12 =>
          new PlayerUpdatePacket(seq, id, Packet.getCurrentTimestamp, pos(), rnd.nextInt(), rnd.nextInt(), rnd.nextInt(101),
            rnd.nextInt(256), charId(), b(3), rnd.nextInt(4) - 1, rnd.nextInt(256), rnd.nextInt(65536), rnd.nextInt(101))
        case 13 | 14 =>
          val a = rnd.nextDouble() * 2 * Math.PI
          ProjectilePacket.spawnRequest(seq, id, cell().toFloat, cell().toFloat, rnd.nextInt(), Math.cos(a).toFloat,
            Math.sin(a).toFloat, b(), b(), rnd.nextInt(6) - 1)
        case 15 => new ItemPacket(seq, id, cell(), cell(), b(7), rnd.nextInt(), if (rnd.nextBoolean()) ItemAction.USE else b())
        case 16 => new TrapPacket(seq, id, cell(), cell(), rnd.nextInt(), if (rnd.nextBoolean()) TrapAction.PLACE else b(),
          b(8), b(3), rnd.nextInt(5), null)
        case 17 => new HeartbeatPacket(seq, id)
        case _ =>
          // What only the server sends, sent to it
          rnd.nextInt(6) match {
            case 0 => new GameEventPacket(seq, id, Packet.getCurrentTimestamp, b(), short(), rnd.nextInt(), short(), short(),
              null, b(), short(), short(), b())
            case 1 => new TileUpdatePacket(seq, id, cell(), cell(), rnd.nextInt())
            case 2 => new WorldInfoPacket(seq, text(27))
            case 3 => new SessionTokenPacket(seq, id, Array.fill(32)(b()))
            case 4 => new SessionEndPacket(seq, id, b())
            case _ => new AuthResponsePacket(seq, rnd.nextBoolean(), id, text(23))
          }
      }
    }

    /** What the router makes of it: anything thrown on the way out is a failure. */
    private def route(s: TestSession, p: Packet): Unit = {
      val wire = PacketSerializer.deserialize(p.serialize())
      note(s"${s.playerId.toString.take(8)} sends $wire")
      if (wire.getType.tcp) server.handleIncomingPacket(wire, s.channel, null)
      else server.handleIncomingPacket(wire, null, new InetSocketAddress("127.0.0.1", 50000 + rnd.nextInt(1000)))
    }

    /** As a real client sends it: signed with its session's token and numbered, through the handlers. */
    private def signed(s: TestSession, p: Packet): Unit = {
      note(s"${s.playerId.toString.take(8)} sends, signed, ${p.getType}")
      check(s.channel.isOpen, s"the server closed ${s.playerId.toString.take(8)}'s connection")
      if (p.getType.tcp) s.send(n => PacketSerializer.deserialize(renumber(p, n)))
      else udpChannel.writeInbound(new DatagramPacket(Unpooled.wrappedBuffer(PacketSigner.sign(renumber(p, s.nextSeq()), s.token)),
        serverAddress, new InetSocketAddress("127.0.0.1", 51000)))
      check(udpChannel.isOpen, "the UDP socket closed")
    }

    private def renumber(p: Packet, n: Int): Array[Byte] = {
      val bytes = p.serialize()
      java.nio.ByteBuffer.wrap(bytes).putInt(1, n)
      bytes
    }

    private def login(): Unit = {
      val action = Seq(AuthAction.LOGIN, AuthAction.SIGNUP, AuthAction.REFRESH, AuthAction.LOGOUT)(rnd.nextInt(4))
      val p =
        if (AuthAction.carriesToken(action)) new AuthRequestPacket(0, Packet.getCurrentTimestamp, action, "", "", Array.fill(32)(b()))
        else new AuthRequestPacket(0, Packet.getCurrentTimestamp, action, text(20), text(20))
      note(s"a stranger sends $p")
      if (stranger.isOpen)
        stranger.writeInbound(Unpooled.wrappedBuffer(PacketSigner.sign(p.serialize(), new Array[Byte](32))))
    }

    private def checkLobbies(): Unit = {
      val lm = server.lobbyManager
      val lobbies = lm.getActiveLobbies
      check(lobbies.size <= Constants.MAX_LOBBIES, s"${lobbies.size} lobbies")
      for (id <- ids) {
        val l = lm.getPlayerLobby(id)
        if (l != null) {
          check(l.players.contains(id), s"${id.toString.take(8)} is in lobby ${l.id}, which doesn't list them")
          if (l.status != LobbyStatus.FINISHED)
            check(!server.rankedQueue.isInQueue(id), s"${id.toString.take(8)} is queued for ranked from inside lobby ${l.id}")
        }
      }
      for (l <- lobbies) {
        for (member <- l.players.asScala)
          check(lm.getPlayerLobby(member) eq l, s"lobby ${l.id} lists ${member.toString.take(8)}, who is in ${lm.getPlayerLobby(member)}")
        if (l.status == LobbyStatus.WAITING) {
          check(l.players.contains(l.hostId), s"waiting lobby ${l.id} has lost its host")
          check(l.playerCount <= l.maxPlayers, s"lobby ${l.id} seats ${l.playerCount} of ${l.maxPlayers}")
          check(l.mapIndex >= 0 && l.mapIndex < WorldRegistry.size, s"lobby ${l.id} is on map ${l.mapIndex}")
        }
        if (l.status == LobbyStatus.IN_GAME)
          check(l.gameInstance != null, s"lobby ${l.id} is in a game it has no match for")
      }
      for ((id, gi) <- server.gameInstances.asScala) {
        val l = lm.getLobby(id)
        check(l != null && (l.gameInstance eq gi), s"match $id belongs to no lobby in a game")
      }
    }

    def play(steps: Int): Unit = {
      val oldErr = Console.err
      val kept = new java.io.PrintStream(printed, true)
      System.setErr(kept)
      try Console.withErr(kept)(quietly {
        while (step < steps) {
          step += 1
          val s = someone
          val inLobby = server.lobbyManager.getPlayerLobby(s.playerId)
          if (inLobby != null && inLobby.status == LobbyStatus.IN_GAME && inLobby.gameInstance != null &&
              inLobby.gameInstance.registry.contains(s.playerId)) tally("packets from inside a match") += 1
          try {
            rnd.nextInt(100) match {
              case r if r < 75 => route(s, packet(s))
              case r if r < 90 => signed(s, packet(s))
              case r if r < 92 => login()
              case r if r < 95 =>
                // A lobby a minute on: its maker may make another, and the matchmaker takes whoever has waited
                note("a minute passes")
                server.lobbyHandler.lastCreateTime.clear()
                server.lobbyHandler.lastListRequestTime.clear()
                server.rankedQueue.checkQueue(System.currentTimeMillis() + 120000)
              case _ =>
                // Too many matches at once is a lot of threads for a test: the oldest ends
                if (server.gameInstances.size > 4) {
                  val oldest = server.gameInstances.keySet().asScala.min
                  note(s"match $oldest's time is up")
                  server.endGame(oldest)
                }
            }
          } catch {
            case e: AssertionError => throw e
            case e: Throwable =>
              val sw = new java.io.StringWriter
              e.printStackTrace(new java.io.PrintWriter(sw))
              fail(s"threw $sw")
          }
          sessions.foreach(_.received()) // everything sent to them reads back
          checkLobbies()
          tally("most matches at once") = Math.max(tally("most matches at once"), server.gameInstances.size)
          tally("most lobbies at once") = Math.max(tally("most lobbies at once"), server.lobbyManager.getActiveLobbies.size)
          if (troubles.nonEmpty) fail("the server caught something and carried on")
        }
      }) finally System.setErr(oldErr)
      println(s"HostileClientFuzzTest seed $seed: ${tally.toSeq.sortBy(_._1).map { case (k, v) => s"$k $v" }.mkString(", ")}")
      for (s <- sessions) s.channel.close()
      stranger.close()
    }
  }
}

/** System.err, kept: read a line at a time as it grows, for the lines that say something went wrong. */
private final class Printed extends java.io.ByteArrayOutputStream {
  private var scanned = 0
  private val found = scala.collection.mutable.Buffer[String]()

  private def isTrouble(line: String): Boolean =
    line.contains("Invalid packet received") || line.contains("Exception") || line.contains(" error:") ||
      line.contains("Error in") || line.contains("Too many malformed")

  def troubles(): Seq[String] = synchronized {
    var end = count
    while (end > scanned && buf(end - 1) != '\n') end -= 1 // whole lines only
    if (end > scanned) {
      new String(buf, scanned, end - scanned, java.nio.charset.StandardCharsets.UTF_8).linesIterator
        .filter(isTrouble).foreach(found += _)
      scanned = end
    }
    found.toSeq
  }
}
