package com.gridgame.client.game

import com.gridgame.common.protocol._

/** One row of the ranked leaderboard. */
final case class LeaderboardEntry(rank: Int, username: String, elo: Int, wins: Int, matchesPlayed: Int)

/** One of the profile's recent matches. `playedAt` is epoch seconds; `rank` is the team's
  * place in a Teams match; `matchType` is the server's (0 casual FFA, 1 casual Teams,
  * 2 ranked FFA, 3 ranked duel, 4 ranked Teams, 5 practice). */
final case class MatchHistoryEntry(matchId: Int, mapIndex: Int, durationMinutes: Int, playedAt: Long,
                                   kills: Int, deaths: Int, rank: Int, totalPlayers: Int, matchType: Int)

/** The server's records, for the menus: the ranked leaderboard, and our profile's lifetime stats
  * and recent matches. Each arrives as a listing (ENTRY... END) and is published whole at its END,
  * as the lobby list is. */
trait Records { this: GameClient =>
  @volatile var matchHistory: Vector[MatchHistoryEntry] = Vector.empty
  @volatile var matchHistoryLoaded: Boolean = false
  private val pendingMatchHistory = scala.collection.mutable.ArrayBuffer.empty[MatchHistoryEntry]
  @volatile var totalKillsStat: Int = 0
  @volatile var totalDeathsStat: Int = 0
  @volatile var matchesPlayedStat: Int = 0
  @volatile var winsStat: Int = 0

  @volatile var leaderboard: Vector[LeaderboardEntry] = Vector.empty
  @volatile var leaderboardLoaded: Boolean = false
  private val pendingLeaderboard = scala.collection.mutable.ArrayBuffer.empty[LeaderboardEntry]

  @volatile var leaderboardListener: () => Unit = _
  @volatile var matchHistoryListener: () => Unit = _

  private[game] def handleLeaderboard(packet: LeaderboardPacket): Unit = {
    packet.getAction match {
      case LeaderboardAction.ENTRY =>
        pendingLeaderboard += LeaderboardEntry(
          packet.getRank & 0xFF, packet.getUsername, packet.getElo.toInt,
          packet.getWins, packet.getMatchesPlayed
        )

      case LeaderboardAction.END =>
        leaderboard = pendingLeaderboard.toVector
        pendingLeaderboard.clear()
        leaderboardLoaded = true
        fire(leaderboardListener)

      case _ =>
    }
  }

  private[game] def handleMatchHistory(packet: MatchHistoryPacket): Unit = {
    packet.getAction match {
      case MatchHistoryAction.STATS =>
        totalKillsStat = packet.getTotalKills
        totalDeathsStat = packet.getTotalDeaths
        matchesPlayedStat = packet.getMatchesPlayed
        winsStat = packet.getWins
        rankedElo = packet.getElo.toInt
        // Server sets oldElo == elo for profile queries (no change). It only
        // differs when this is a post-match push, which is our signal that
        // the just-ended match was ranked and the scoreboard should render
        // the ELO delta.
        if (packet.getOldElo != packet.getElo) {
          pendingEloChange = Some((packet.getOldElo.toInt, packet.getElo.toInt))
        }

      case MatchHistoryAction.ENTRY =>
        pendingMatchHistory += MatchHistoryEntry(
          packet.getMatchId, packet.getMapIndex & 0xFF, packet.getDuration & 0xFF,
          packet.getPlayedAt & 0xFFFFFFFFL, packet.getKills.toInt, packet.getDeaths.toInt,
          packet.getRank & 0xFF, packet.getTotalPlayers & 0xFF,
          packet.getMatchType & 0xFF
        )

      case MatchHistoryAction.END =>
        matchHistory = pendingMatchHistory.toVector
        pendingMatchHistory.clear()
        matchHistoryLoaded = true
        fire(matchHistoryListener)

      case _ =>
    }
  }

  def requestLeaderboard(): Unit = {
    val packet = new LeaderboardPacket(
      sequenceNumber.getAndIncrement(), localPlayerId, LeaderboardAction.QUERY
    )
    send(packet)
  }

  def requestMatchHistory(): Unit = {
    val packet = new MatchHistoryPacket(
      sequenceNumber.getAndIncrement(), localPlayerId, MatchHistoryAction.QUERY
    )
    send(packet)
  }
}
