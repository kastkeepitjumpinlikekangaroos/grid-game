package com.gridgame.common.model

/**
 * The first [[com.gridgame.common.Constants.MATCH_OPENING_MS]] of a match, when it is not played
 * quite the way the rest of it is.
 *
 * Both modes open with a moment to get your bearings, and each spends it differently. A Teams
 * match walls the two halves off from each other ([[TeamDivider]]), so a team can gather, pick its
 * ground and say something to each other before anyone can shoot anyone. A free-for-all has no
 * sides to keep apart, so it holds everyone's fire instead: for those thirty seconds nobody can
 * attack at all — no shot, no charge, no burst, no ability — and everyone gets the same half
 * minute to find their feet and pick their ground.
 *
 * The rules are a bit set rather than a mode, because what a mode does with its opening is one
 * thing and what the opening then means is another: the server sends the set, and the client and
 * the server each act on the rules they recognise.
 */
object MatchOpening {
  /** A wall down the middle of the map, one team on each side (see [[TeamDivider]]). */
  val DIVIDER: Byte = 0x01
  /** A ceasefire: nobody may attack — not the primary, not its burst, not an ability. */
  val NO_ATTACKS: Byte = 0x02

  /** What the opening of a match in this mode does. */
  def rulesFor(gameMode: Byte): Byte = if (gameMode == 1) DIVIDER else NO_ATTACKS

  def has(rules: Byte, rule: Byte): Boolean = (rules & rule) != 0
}
