package com.gridgame.common.model

import java.util.UUID

/** How a Teams lobby is split into teams 1 and 2. The server assigns teams when the match
  * starts and the lobby room previews them before it does; both go through here so the
  * preview can't disagree with the match. */
object TeamAssignment {

  /** Bots are minted as `UUID(0, n)` with a growing n (see the server's `BotManager`). */
  def isBot(id: UUID): Boolean = id.getMostSignificantBits == 0L && id.getLeastSignificantBits > 0L

  /** Deals the two teams round-robin over the humans in the order they joined, then the
    * bots in the order they were added. */
  def assign(humans: Seq[UUID], bots: Seq[UUID]): Seq[(UUID, Byte)] =
    (humans ++ bots).zipWithIndex.map { case (id, i) => id -> (i % 2 + 1).toByte }
}
