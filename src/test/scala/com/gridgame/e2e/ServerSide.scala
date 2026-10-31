package com.gridgame.server.game

import com.gridgame.common.model.Player
import com.gridgame.common.model.TeamDivider
import com.gridgame.common.protocol._
import com.gridgame.server.GameServer
import com.gridgame.server.bots.BotController
import com.gridgame.server.lobby.Lobby
import io.netty.channel.Channel
import io.netty.channel.ChannelHandler

import java.net.InetSocketAddress
import java.util.UUID
import scala.jdk.CollectionConverters._

/**
 * What the end-to-end harness (com.gridgame.e2e) needs of the server's insides, which are
 * package-private: its network handlers, its matches' clocks, and a login that skips the password's
 * hash (bcrypt at cost 12 is a quarter of a second a login, and the roster sweep logs in a hundred).
 */
object E2EServer {
  def tcpHandler(s: GameServer): ChannelHandler = s.tcpHandler()
  def udpHandler(s: GameServer): ChannelHandler = s.udpHandler()

  /**
   * Every match this server launches from now on is begun but not started, its bots never started:
   * the harness runs its clocks (`tick`). `openingMs` says how long each one's opening lasts, if it
   * has one (the real thirty seconds are longer than a test), read as each match is launched.
   */
  def runMatchesByHand(s: GameServer, openingMs: () => Long): Unit = {
    s.launcher.startMatch = { gi =>
      gi.begin()
      shortenOpening(gi, openingMs())
    }
    s.launcher.startBots = _ => ()
  }

  /** Matches launched from now on run on their own clocks, as the real server's do, but for their
    * opening, which lasts `openingMs` (read as each is launched). */
  def runMatchesOnTheirOwn(s: GameServer, openingMs: () => Long): Unit = {
    s.launcher.startMatch = { gi =>
      gi.start()
      shortenOpening(gi, openingMs())
    }
    s.launcher.startBots = _.start()
  }

  /** The opening, if the match has one, ending `ms` from now: its wall too. Before the first tick,
    * which announces it. */
  def shortenOpening(gi: GameInstance, ms: Long): Unit =
    if (gi.openingEndsAt > 0L) {
      gi.openingEndsAt = System.currentTimeMillis() + ms
      val world = gi.world
      if (world != null && world.divider != null) world.divider = TeamDivider.raise(world, gi.openingEndsAt)
    }

  def instances(s: GameServer): Seq[GameInstance] = s.gameInstances.values().asScala.toSeq

  // A match's clocks, as GameInstance.start schedules them
  def tickProjectiles(gi: GameInstance): Unit = gi.tickProjectiles()
  def tickPlayers(gi: GameInstance): Unit = gi.tickPlayers()
  def respawn(gi: GameInstance, id: UUID): Unit = gi.respawn(id)
  def spawnItems(gi: GameInstance, count: Int): Unit = gi.spawnItemBatch(count)
  def tickBots(gi: GameInstance, now: Long): Unit = gi.botController match {
    case bc: BotController => bc.tick(now)
    case _ =>
  }
  def isRunning(gi: GameInstance): Boolean = gi.running
  /** How many items `start` would spawn in a batch on this match's map. */
  def itemBatch(gi: GameInstance): Int = Math.max(3, Math.min(20, gi.world.width * gi.world.height / 2000))

  def checkRankedQueue(s: GameServer, now: Long): Unit = s.rankedQueue.checkQueue(now)
  def cleanup(s: GameServer, now: Long): Unit = s.cleanup(now)

  def lobbyOf(s: GameServer, playerId: UUID): Lobby = s.lobbyManager.getPlayerLobby(playerId)

  /**
   * Log the player on this connection in as AuthService does for a good password: their address and
   * name kept, a session, its token sent first and the answer after it. No refresh token: logging back
   * in by itself is no part of what this is for.
   */
  def logInWithoutPassword(s: GameServer, ch: Channel, id: UUID, name: String): Unit = {
    s.sessions.playerTcpAddresses.put(id, ch.remoteAddress().asInstanceOf[InetSocketAddress].getAddress)
    s.sessions.accountNames.put(id, name)
    val token = s.sessions.generateSessionToken(id, ch)
    s.outbox.sendVia(new SessionTokenPacket(s.outbox.nextSeq(), id, token), ch)
    s.outbox.sendVia(new AuthResponsePacket(s.outbox.nextSeq(), true, id, AuthRules.LoginSuccessful), ch)
  }

  def connectedPlayer(s: GameServer, id: UUID): Player = s.getConnectedPlayer(id)
}
