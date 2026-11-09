package com.gridgame.server.lobby

import java.util.UUID

/**
 * A lobby's match played on a match server rather than in this server (design/networking.md,
 * *Match servers*): what the lobbies can ask of it. It is under way from the moment it is handed
 * there; its end comes back as its outcome (MatchEnd.recordRemote).
 */
trait RemoteMatch {
  def matchId: UUID

  /** A player left the lobby, or lost their connection to this server: out of the match too. */
  def removePlayer(playerId: UUID): Unit

  /** End it now, scored (a practice session left). */
  def endNow(): Unit

  /** Everyone has left: stop it without a result. */
  def abandon(): Unit
}

/** Where a lobby's match is played when it isn't played here: the match servers (server/cluster). */
trait RemoteMatches {
  /** Start the lobby's match on a match server, and tell its players where to play it. Throws if
    * none can take it. */
  def launch(lobby: Lobby, withBots: Boolean): RemoteMatch
}
