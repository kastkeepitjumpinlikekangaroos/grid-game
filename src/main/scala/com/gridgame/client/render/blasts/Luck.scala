package com.gridgame.client.render.blasts

import GLBlastRenderers._
import BlastKit._
import Pieces._

/** Luck: the Gambler's Lucky Streak, a hand of cards and a fistful of coins thrown up round him
  * on a gold ring, and the streaks of the haste it gives him. */
private[blasts] object Luck {
  private[blasts] def luck(): Unit = {
    shockRing(0f, 0.3f, 4f, 1f, 0.84f, 0.3f, 0.9f)
    edgeRing(0.05f, 0.6f, 2f, 1f, 0.9f, 0.5f, 0.6f)
    streaks(10, 0.02f, 0.35f, 0.2f, 1f, 0.3f, 2.6f, 1f, 0.84f, 0.3f, 0.85f, 1)
    if (layer != AIR) return
    flash(0.07f, 0.45f, 1f, 0.86f, 0.42f)
    // The cards: red and black pips, whirled up and round as they go out
    swirl(D_CARD, 5, 0f, 0.9f, 0.85f, 42f, 0.6f, 4.4f, 0.86f, 0.12f, 0.16f, 1)
    swirl(D_CARD, 4, 0f, 0.9f, 0.8f, 46f, 0.55f, 4.4f, 0.12f, 0.1f, 0.16f, 2)
    debris(D_COIN, 8, 0f, 0.8f, 0.8f, 44f, 3f, 1f, 0.8f, 0.24f, 3, shadow = true)
    glints(5, 0.05f, 0.7f, 0.7f, 30f, 9f, 1f, 0.9f, 0.5f, 1f)
  }
}
