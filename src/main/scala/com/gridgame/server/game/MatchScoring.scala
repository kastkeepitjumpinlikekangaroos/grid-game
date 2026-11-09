package com.gridgame.server.game

import com.gridgame.common.model.{MatchResults, PlayerResult}
import com.gridgame.common.protocol._

import java.util.UUID
import scala.jdk.CollectionConverters._

/** How a match ended: its standings and how long it was played, with the character each player
  * played, for the server that keeps the results (MatchEnd). */
final case class MatchOutcome(matchId: UUID, playedSeconds: Int, results: Seq[PlayerResult],
                              characters: Map[UUID, Byte])

/**
 * The part of a match's end that happens where the match runs (in the server, or on a match server
 * of its own): the match stopped, scored on kills less deaths (by team in Teams), and its players
 * sent the standings (GAME_OVER, a SCORE_ENTRY each). Saving them and the ratings are the server's
 * that keeps the accounts (MatchEnd); SCORE_END, the scoreboard's last line, follows once it has.
 */
object MatchScoring {
  private val ZeroId = new UUID(0L, 0L)

  def finish(instance: GameInstance, lobbyId: Short): MatchOutcome = {
    // Practice can end early, so time the match rather than trusting its configured length.
    val playedSeconds = instance.getElapsedSeconds

    // Snapshot the teams before stop() clears them. Reading them afterwards put every
    // player on "team 0", one team, so a Teams scoreboard ranked everybody #1.
    val teams: Map[UUID, Byte] = instance.teamAssignments.asScala.toMap

    // Stop the instance (shutdownNow() interrupts worker threads including the
    // current thread when called from the match's own timer, endMatch). Clear the
    // interrupt flag so what follows (the database, a match server's report) isn't disrupted.
    instance.stop()
    Thread.interrupted()

    instance.broadcastToInstance(new GameEventPacket(
      instance.outbox.nextSeq(), ZeroId, GameEvent.GAME_OVER, lobbyId,
      0, 0.toShort, 0.toShort, null, 0.toByte, 0.toShort, 0.toShort
    ))

    // In Teams mode every member carries their team's place; players who left mid-match are still scored.
    val scoreboard = instance.killTracker.getScoreboard
    val results =
      if (instance.gameMode == 1) MatchResults.rankTeams(scoreboard, pid => teams.getOrElse(pid, 0.toByte))
      else MatchResults.rankFfa(scoreboard)
    instance.journal.note(s"ended after ${playedSeconds}s: " + results.map { r =>
      s"#${r.rank} ${r.playerId.toString.substring(0, 8)} ${r.kills}-${r.deaths}${if (r.teamId != 0) s" (team ${r.teamId})" else ""}"
    }.mkString(", "))
    results.foreach { r =>
      instance.broadcastToInstance(new GameEventPacket(
        instance.outbox.nextSeq(), r.playerId, GameEvent.SCORE_ENTRY, lobbyId,
        0, r.kills.toShort, r.deaths.toShort, null, r.rank.toByte, 0.toShort, 0.toShort, r.teamId
      ))
    }

    val characters = results.flatMap(r => instance.registry.characterOf(r.playerId).map(r.playerId -> _)).toMap
    MatchOutcome(instance.matchId, playedSeconds, results, characters)
  }

  /** The standings are all there: the scoreboard can be shown. */
  def sendScoreEnd(instance: GameInstance, lobbyId: Short): Unit =
    instance.broadcastToInstance(new GameEventPacket(
      instance.outbox.nextSeq(), ZeroId, GameEvent.SCORE_END, lobbyId,
      0, 0.toShort, 0.toShort, null, 0.toByte, 0.toShort, 0.toShort
    ))
}
