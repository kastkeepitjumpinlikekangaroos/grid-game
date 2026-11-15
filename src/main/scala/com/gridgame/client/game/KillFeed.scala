package com.gridgame.client.game

/**
 * A kill as the HUD's feed shows it (MatchProgress.killFeed): when it was heard, on the system's
 * clock (a match's frames are timed on it too), who killed whom by their characters' names ("You"
 * for us) and the characters themselves (-1 for a player we never heard of), their teams, and whether
 * it was ours or on us.
 */
final class KillFeedEntry(val atMs: Long, val killerName: String, val victimName: String,
                          val killerCharacter: Byte, val victimCharacter: Byte,
                          val killerTeam: Byte, val victimTeam: Byte,
                          val byUs: Boolean, val onUs: Boolean)

/**
 * One of our kills, as the HUD announces it (render/CLAUDE.md, *A kill is announced*): when it was
 * heard, the run of kills in a row it made (MatchProgress.killStreak), whom it was, by their
 * character's name and id, and whether it was the match's first blood (the first kill anyone made in
 * it; never in practice, whose targets don't fight back).
 */
final class KillMoment(val atMs: Long, val streak: Int, val victimName: String, val victimCharacter: Byte,
                       val firstBlood: Boolean)
