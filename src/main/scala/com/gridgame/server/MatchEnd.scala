package com.gridgame.server

import com.gridgame.common.model.MatchResults
import com.gridgame.common.model.Player
import com.gridgame.common.model.PlayerResult
import com.gridgame.common.observability.Attrs
import com.gridgame.common.observability.Metrics
import com.gridgame.common.protocol._
import com.gridgame.server.account.{AuthDatabase, MatchRow}
import com.gridgame.server.bots.BotManager
import com.gridgame.server.game.{MatchOutcome, MatchScoring}
import com.gridgame.server.lobby.{Lobby, LobbyManager, LobbyStatus}
import com.gridgame.server.net.Outbox

import java.util.UUID
import scala.util.control.NonFatal

/**
 * The end of a match, whether its time ran out or its practice session was left: the match
 * stopped, everyone sent the final standings (GAME_OVER, a SCORE_ENTRY each, SCORE_END), the
 * results saved with everyone's character, a ranked match's ratings updated, and the lobby taken
 * down.
 *
 * A match played here is stopped and scored here (MatchScoring, `endGame`). One played on a match
 * server is stopped and scored there, and its outcome comes back to be kept (`recordRemote`); the
 * match server sends SCORE_END once it has been.
 */
final class MatchEnd(lobbyManager: LobbyManager, authDatabase: AuthDatabase, outbox: Outbox,
                     connectedPlayer: UUID => Player, unregisterMatch: Short => Unit) {
  def endGame(lobbyId: Short): Unit = {
    val lobby = lobbyManager.getLobby(lobbyId)
    if (lobby == null) return

    val instance = lobby.gameInstance
    if (instance == null) return

    if (!finishing(lobby)) return

    try {
      val outcome = MatchScoring.finish(instance, lobbyId)
      record(lobby, outcome)
    } catch {
      case NonFatal(e) =>
        System.err.println(s"MatchEnd: Lobby $lobbyId's results weren't all recorded: $e")
    } finally {
      // The match comes down whatever happened above. A database error there (another connection
      // holding its lock, say) used to stop it here: the lobby and its match stayed for good, its
      // players on a frozen match with no scoreboard and refused "already in a lobby", and the
      // lobby counted against MAX_LOBBIES until the server restarted.
      unregisterMatch(lobbyId)
      lobbyManager.removeLobby(lobbyId)
      MatchScoring.sendScoreEnd(instance, lobbyId)
      println(s"Game ended for lobby $lobbyId")
    }
  }

  /** A match a match server played has ended there, scored: keep its results. The lobby comes down
    * whatever happens to them. False if the lobby had ended already. */
  def recordRemote(lobby: Lobby, outcome: MatchOutcome): Boolean = {
    if (!finishing(lobby)) return false
    try record(lobby, outcome)
    catch {
      case NonFatal(e) =>
        System.err.println(s"MatchEnd: Lobby ${lobby.id}'s results weren't all recorded: $e")
    } finally {
      lobbyManager.removeLobby(lobby.id)
      println(s"Game ended for lobby ${lobby.id} (on a match server)")
    }
    true
  }

  /** The match timer and a player ending their practice session can both get here; only the first
    * may end the match, or it is scored and saved twice. */
  private def finishing(lobby: Lobby): Boolean = lobby.synchronized {
    if (lobby.status == LobbyStatus.FINISHED) false
    else { lobby.status = LobbyStatus.FINISHED; true }
  }

  /** Save a finished match's results and update a ranked one's ratings. */
  private def record(lobby: Lobby, outcome: MatchOutcome): Unit = {
    val modeAttrs = io.opentelemetry.api.common.Attributes.builder()
      .putAll(Attrs.modeOf(lobby.gameMode))
      .putAll(Attrs.matchTypeOf(lobby.matchType))
      .build()
    Metrics.matchesFinished.add(1L, modeAttrs)
    Metrics.matchDuration.record(outcome.playedSeconds.toDouble, modeAttrs)

    val results = outcome.results
    // Persist match results: everyone's row with the character they played, the bots' flagged as
    // bots, so which characters win can be read from the table. A practice session's bots are
    // passive targets and left out. The player count includes the bots, since ranks were placed
    // among them: "#3 of 1" is what counting only humans produced.
    val humanResults = results.filterNot(r => BotManager.isBotUUID(r.playerId))
    val recorded = if (lobby.isPractice) humanResults else results
    val playedMinutes = Math.max(1, Math.round(outcome.playedSeconds / 60.0).toInt)
    authDatabase.saveMatch(lobby.mapIndex, playedMinutes,
      recorded.map(r => MatchRow(r.playerId, r.kills, r.deaths, r.rank, outcome.characters.get(r.playerId),
        BotManager.isBotUUID(r.playerId))), lobby.matchType, results.size)

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
