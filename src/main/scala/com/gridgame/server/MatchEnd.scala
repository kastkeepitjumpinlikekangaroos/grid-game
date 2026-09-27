package com.gridgame.server

import com.gridgame.common.model.MatchResults
import com.gridgame.common.model.Player
import com.gridgame.common.model.PlayerResult
import com.gridgame.common.observability.Attrs
import com.gridgame.common.observability.Metrics
import com.gridgame.common.protocol._
import com.gridgame.server.account.AuthDatabase
import com.gridgame.server.bots.BotManager
import com.gridgame.server.lobby.{LobbyManager, LobbyStatus}
import com.gridgame.server.net.Outbox

import java.util.UUID
import scala.jdk.CollectionConverters._

/**
 * The end of a match, whether its time ran out or its practice session was left: the match
 * stopped, everyone sent the final standings (GAME_OVER, a SCORE_ENTRY each, SCORE_END), the
 * humans' results saved, a ranked match's ratings updated, and the lobby taken down.
 */
final class MatchEnd(lobbyManager: LobbyManager, authDatabase: AuthDatabase, outbox: Outbox,
                     connectedPlayer: UUID => Player, unregisterMatch: Short => Unit) {
  def endGame(lobbyId: Short): Unit = {
    val lobby = lobbyManager.getLobby(lobbyId)
    if (lobby == null) return

    val instance = lobby.gameInstance
    if (instance == null) return

    // The match timer and a player ending their practice session can both get here; only
    // the first may end the match, or it is scored and saved twice.
    lobby.synchronized {
      if (lobby.status == LobbyStatus.FINISHED) return
      lobby.status = LobbyStatus.FINISHED
    }

    // Practice can end early, so time the match rather than trusting its configured length.
    val playedSeconds = instance.getElapsedSeconds
    val modeAttrs = io.opentelemetry.api.common.Attributes.builder()
      .putAll(Attrs.modeOf(instance.gameMode))
      .putAll(Attrs.matchTypeOf(lobby.matchType))
      .build()
    Metrics.matchesFinished.add(1L, modeAttrs)
    Metrics.matchDuration.record(playedSeconds.toDouble, modeAttrs)

    // Snapshot the teams before stop() clears them. Reading them afterwards put every
    // player on "team 0", one team, so a Teams scoreboard ranked everybody #1.
    val teams: Map[UUID, Byte] = instance.teamAssignments.asScala.toMap

    // Stop the instance (shutdownNow() interrupts worker threads including the
    // current thread when called from the match's own timer, endMatch). Clear the
    // interrupt flag so subsequent JDBC operations in saveMatch aren't disrupted.
    instance.stop()
    Thread.interrupted()

    // Broadcast GAME_OVER
    val zeroUUID = new UUID(0L, 0L)
    val gameOverPacket = new GameEventPacket(
      outbox.nextSeq(), zeroUUID, GameEvent.GAME_OVER, lobbyId,
      0, 0.toShort, 0.toShort, null, 0.toByte, 0.toShort, 0.toShort
    )
    instance.broadcastToInstance(gameOverPacket)

    // Send SCORE_ENTRY for each player. In Teams mode every member carries their team's
    // place; players who left mid-match are still scored.
    val scoreboard = instance.killTracker.getScoreboard
    val results =
      if (instance.gameMode == 1) MatchResults.rankTeams(scoreboard, pid => teams.getOrElse(pid, 0.toByte))
      else MatchResults.rankFfa(scoreboard)
    results.foreach { r =>
      val scorePacket = new GameEventPacket(
        outbox.nextSeq(), r.playerId, GameEvent.SCORE_ENTRY, lobbyId,
        0, r.kills.toShort, r.deaths.toShort, null, r.rank.toByte, 0.toShort, 0.toShort, r.teamId
      )
      instance.broadcastToInstance(scorePacket)
    }

    // Persist match results (exclude bots). The player count includes the bots, since
    // ranks were placed among them: "#3 of 1" is what counting only humans produced.
    val humanResults = results.filterNot(r => BotManager.isBotUUID(r.playerId))
    val playedMinutes = Math.max(1, Math.round(playedSeconds / 60.0).toInt)
    authDatabase.saveMatch(lobby.mapIndex, playedMinutes,
      humanResults.map(r => (r.playerId, r.kills, r.deaths, r.rank.toByte)), lobby.matchType, results.size)

    // Update ELO for ranked matches (exclude bots) and push fresh stats to each
    // human so the post-match scoreboard can show the ELO delta without forcing
    // the client to manually re-query match history.
    if (lobby.isRanked && humanResults.size >= 2) {
      val eloDeltas = updateRankedElo(humanResults)
      eloDeltas.foreach { case (uuid, (oldElo, newElo)) =>
        val player = connectedPlayer(uuid)
        if (player != null) {
          val (totalKills, totalDeaths, matchesPlayed, wins, _) = authDatabase.getPlayerStats(uuid)
          val statsPacket = new MatchHistoryPacket(
            outbox.nextSeq(), uuid, Packet.getCurrentTimestamp,
            MatchHistoryAction.STATS,
            totalKills = totalKills, totalDeaths = totalDeaths,
            matchesPlayed = matchesPlayed, wins = wins,
            elo = newElo.toShort, oldElo = oldElo.toShort
          )
          outbox.send(statsPacket, player)
        }
      }
    }

    // Send SCORE_END
    val scoreEndPacket = new GameEventPacket(
      outbox.nextSeq(), zeroUUID, GameEvent.SCORE_END, lobbyId,
      0, 0.toShort, 0.toShort, null, 0.toByte, 0.toShort, 0.toShort
    )
    instance.broadcastToInstance(scoreEndPacket)

    // Clean up
    unregisterMatch(lobbyId)
    lobbyManager.removeLobby(lobbyId)

    println(s"Game ended for lobby $lobbyId")
  }

  /**
   * Apply ELO updates for a finished ranked match.
   * Returns a map of playerId -> (oldElo, newElo) so callers can notify clients.
   * Returns an empty map if the match had fewer than 2 humans.
   */
  private def updateRankedElo(results: Seq[PlayerResult]): Map[UUID, (Int, Int)] = {
    if (results.size < 2) return Map.empty

    val changes = MatchResults.eloChanges(results.map(r => (r, authDatabase.getEloByUUID(r.playerId))))
    changes.foreach { case (uuid, (oldElo, newElo)) =>
      val username = authDatabase.getUsernameByUUID(uuid)
      if (username != null) {
        authDatabase.updateElo(username, newElo)
        println(s"RankedELO: ${username} $oldElo -> $newElo (delta=${newElo - oldElo})")
      }
      Metrics.eloDelta.record((newElo - oldElo).toDouble, io.opentelemetry.api.common.Attributes.empty())
    }
    changes
  }
}
