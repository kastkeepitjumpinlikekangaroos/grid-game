package com.gridgame.server.game

import com.gridgame.common.Constants
import com.gridgame.common.model._
import com.gridgame.common.protocol._

import java.util.UUID

/**
 * The opening of a match (MatchOpening): its first thirty seconds, which a Teams match spends with a
 * wall down the middle of the map (TeamDivider) and a free-for-all with everyone's fire held. The
 * server announces it when the match begins and when it is over, and tells anyone joining during it;
 * each client runs it on its own clock from there.
 */
trait Opening { this: GameInstance =>
  private var openingAnnounced = false
  private var openingEndAnnounced = false
  private var openingRules: Byte = 0

  /** When the opening ends, on the server's clock. */
  private[server] var openingEndsAt: Long = 0L

  /**
   * The opening starts with the match. Teams spends it with the two halves walled off from each
   * other, a free-for-all with everyone's abilities holstered. The wall hangs off the world, so every
   * walkability check in the game refuses its cells while it stands. Practice is for trying a
   * character out against passive bots, so it opens with nothing held back: holding its fire for
   * thirty seconds is the one thing an opening must not do there.
   */
  private[game] def beginOpening(): Unit = {
    openingRules = if (isPractice) 0.toByte else MatchOpening.rulesFor(gameMode)
    openingEndsAt = if (openingRules == 0) 0L else System.currentTimeMillis() + Constants.MATCH_OPENING_MS
    openingAnnounced = false
    openingEndAnnounced = false
    if (MatchOpening.has(openingRules, MatchOpening.DIVIDER) && world != null) {
      world.divider = TeamDivider.raise(world, openingEndsAt)
    }
  }

  /** The wall between the two halves of the map, while it stands. Null in a free-for-all, and
    * once it has come down. */
  def divider: TeamDivider = if (world == null) null else world.divider

  def inOpening: Boolean = System.currentTimeMillis() < openingEndsAt

  /** Is nobody allowed to attack? The opening of a free-for-all is a ceasefire: no shot, no
    * charge, no burst and no ability, so everyone gets the same half minute to find their feet
    * and pick their ground. */
  def attacksLocked: Boolean =
    MatchOpening.has(openingRules, MatchOpening.NO_ATTACKS) && inOpening

  /** Put the opening behind us with nothing left to say about it: the matches the test kit drives
    * are already under way. */
  private[server] def skipOpening(): Unit = {
    openingEndsAt = 0L
    openingAnnounced = true
    openingEndAnnounced = true
    if (world != null) world.divider = null
  }

  private def openingPacket(msLeft: Int): GameEventPacket =
    new GameEventPacket(outbox.nextSeq(), new UUID(0L, 0L), Packet.getCurrentTimestamp,
      GameEvent.MATCH_OPENING, gameId, 0, 0.toShort, 0.toShort, null, 0.toByte, 0.toShort, 0.toShort,
      0.toByte, msLeft, openingRules)

  /**
   * Every client runs the opening on its own clock, from the time left it is told: it is announced
   * once when the match begins and once when it is over, both over TCP. The wall is taken off the
   * world here as well as announced, so the rest of the match pays nothing for it.
   */
  private[server] def syncOpening(): Unit = {
    if (openingRules == 0) return // practice: there is no opening to tell anyone about
    val now = System.currentTimeMillis()
    if (now < openingEndsAt) {
      if (!openingAnnounced) {
        openingAnnounced = true
        broadcastToInstance(openingPacket((openingEndsAt - now).toInt))
      }
    } else if (!openingEndAnnounced) {
      openingEndAnnounced = true
      if (world != null) world.divider = null
      broadcastToInstance(openingPacket(0))
    }
  }

  /** A client joining during the opening hears about it too. */
  private[server] def sendOpeningTo(player: Player): Unit = {
    val left = openingEndsAt - System.currentTimeMillis()
    if (left > 0L) outbox.send(openingPacket(left.toInt), player)
  }
}
