package com.gridgame.server.game

import com.gridgame.server.net.Outbox

/** What a match needs from the server it runs in. */
trait MatchHost {
  /** Where the match's packets go out. */
  def outbox: Outbox

  /** The match's time is up: score it, tell everyone, and tear it down. GameInstance calls this
    * from its own timer. */
  def endGame(lobbyId: Short): Unit
}
