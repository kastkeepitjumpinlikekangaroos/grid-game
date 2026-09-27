package com.gridgame.server.game

/** The bots playing in a match (server.bots): they act on a timer of their own, and stop with it. */
trait MatchBots {
  def stop(): Unit
}
