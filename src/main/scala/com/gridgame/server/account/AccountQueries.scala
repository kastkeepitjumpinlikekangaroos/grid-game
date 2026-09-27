package com.gridgame.server.account

import com.gridgame.common.protocol._
import com.gridgame.server.net.Outbox
import io.netty.channel.Channel

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * A player's profile (their stats and recent matches) and the ranked leaderboard, both read from
 * the database and sent back as ENTRY... END. Each is an expensive query, so each has its own
 * budget per player.
 */
final class AccountQueries(authDatabase: AuthDatabase, outbox: Outbox, isConnected: UUID => Boolean) {
  // Per-player rate limiting for expensive queries, one budget per kind
  private val lastHistoryQueryTime = new ConcurrentHashMap[UUID, java.lang.Long]()
  private val lastLeaderboardQueryTime = new ConcurrentHashMap[UUID, java.lang.Long]()

  def handleMatchHistoryRequest(packet: MatchHistoryPacket, tcpCh: Channel): Unit = {
    if (packet.getAction != MatchHistoryAction.QUERY) return

    val playerId = packet.getPlayerId
    if (!isConnected(playerId)) return

    if (!allowExpensiveQuery(lastHistoryQueryTime, playerId)) return

    // Send stats. Use oldElo = elo here so the client knows there's no ELO
    // change to display (this is a profile query, not a post-match push).
    val (totalKills, totalDeaths, matchesPlayed, wins, elo) = authDatabase.getPlayerStats(playerId)
    val statsPacket = new MatchHistoryPacket(
      outbox.nextSeq(), playerId, Packet.getCurrentTimestamp, MatchHistoryAction.STATS,
      totalKills = totalKills, totalDeaths = totalDeaths,
      matchesPlayed = matchesPlayed, wins = wins, elo = elo.toShort, oldElo = elo.toShort
    )
    outbox.sendVia(statsPacket, tcpCh)

    // Send history entries
    val history = authDatabase.getMatchHistory(playerId)
    history.foreach { case (matchId, mapIndex, durationMin, playedAt, kills, deaths, rank, playerCount, matchType) =>
      val entryPacket = new MatchHistoryPacket(
        outbox.nextSeq(), playerId, Packet.getCurrentTimestamp, MatchHistoryAction.ENTRY,
        matchId.toInt, mapIndex.toByte, durationMin.toByte, (playedAt / 1000).toInt,
        kills.toShort, deaths.toShort, rank.toByte, playerCount.toByte,
        matchType = matchType.toByte
      )
      outbox.sendVia(entryPacket, tcpCh)
    }

    // Send end marker
    val endPacket = new MatchHistoryPacket(outbox.nextSeq(), playerId, MatchHistoryAction.END)
    outbox.sendVia(endPacket, tcpCh)
  }

  private val QUERY_RATE_LIMIT_MS = 10000L // 10 seconds between expensive queries

  /** Each query kind has its own budget, and only an answered query spends it. Sharing one
    * timestamp (and restamping it on every rejected request) meant opening the leaderboard
    * within 10s of the profile, or clicking either twice, got no reply at all. */
  private def allowExpensiveQuery(lastTimes: ConcurrentHashMap[UUID, java.lang.Long], playerId: UUID): Boolean = {
    val now = System.currentTimeMillis()
    val lastTime = lastTimes.get(playerId)
    if (lastTime != null && now - lastTime < QUERY_RATE_LIMIT_MS) return false
    lastTimes.put(playerId, now)
    true
  }

  def handleLeaderboardRequest(packet: LeaderboardPacket, tcpCh: Channel): Unit = {
    if (packet.getAction != LeaderboardAction.QUERY) return

    val playerId = packet.getPlayerId
    if (!isConnected(playerId)) return

    if (!allowExpensiveQuery(lastLeaderboardQueryTime, playerId)) return

    val leaderboard = authDatabase.getLeaderboard()
    var rank: Int = 1
    leaderboard.foreach { case (username, elo, wins, matchesPlayed) =>
      val entryPacket = new LeaderboardPacket(
        outbox.nextSeq(), playerId, Packet.getCurrentTimestamp, LeaderboardAction.ENTRY,
        rank.toByte, elo.toShort, wins, matchesPlayed, username
      )
      outbox.sendVia(entryPacket, tcpCh)
      rank += 1
    }

    val endPacket = new LeaderboardPacket(outbox.nextSeq(), playerId, LeaderboardAction.END)
    outbox.sendVia(endPacket, tcpCh)
  }

  /** The player is gone: their budgets with them. */
  def forget(playerId: UUID): Unit = {
    lastHistoryQueryTime.remove(playerId)
    lastLeaderboardQueryTime.remove(playerId)
  }
}
