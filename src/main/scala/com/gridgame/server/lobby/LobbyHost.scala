package com.gridgame.server.lobby

import com.gridgame.common.model.Player
import com.gridgame.server.game.{GameInstance, MatchHost}

import java.util.UUID

/** What the lobbies need from the server they run in: a match's needs, and the server's players and
  * running matches. */
trait LobbyHost extends MatchHost {
  /** A logged-in player, or null. */
  def getConnectedPlayer(playerId: UUID): Player

  /** A lobby's match has started... */
  def registerGameInstance(lobbyId: Short, instance: GameInstance): Unit

  /** ...or been stopped. */
  def unregisterGameInstance(lobbyId: Short): Unit
}
