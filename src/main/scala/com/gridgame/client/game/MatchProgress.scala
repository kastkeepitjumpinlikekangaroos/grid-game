package com.gridgame.client.game

import com.gridgame.client.diagnostics.Diagnostics
import com.gridgame.common.diagnostics.Recording.NoticedKind
import com.gridgame.client.audio.AudioManager
import com.gridgame.client.i18n.{I18n, Messages}
import com.gridgame.common.model._
import com.gridgame.common.protocol._

import java.util.UUID
import java.util.concurrent.{ConcurrentHashMap, CopyOnWriteArrayList}

/**
 * The match as a whole, as the server reports it (GameEvent): kills and deaths — the death itself
 * and the respawn after it, everyone's, which come by TCP and so always reach us (OtherPlayers) —
 * everyone's kills and deaths so far (for the scoreboard Tab holds up), the kill feed, our kills in
 * a row, the match clock, the scoreboard at the end, practice's tallies, and leaving the match.
 */
trait MatchProgress { this: GameClient =>
  @volatile var killCount: Int = 0
  @volatile var deathCount: Int = 0

  // Everyone's kills and deaths in this match, us included, as the KILLs have told them: each carries
  // the killer's own count, the server's (KillTracker), and whoever they killed has a death more. A
  // death nobody scored isn't a KILL, and isn't counted by the server either. Kills in the high half
  // of the value, deaths in the low. The match's whole run of KILLs comes by TCP from its start, so
  // this is the count the match's end will score (render/LiveStandings reads it).
  private val tallies = new ConcurrentHashMap[UUID, java.lang.Long]()
  @volatile private var _talliesChanged = 0

  /** A player's kills in this match so far, as the KILLs have told them. */
  def killsOf(playerId: UUID): Int = {
    val t = tallies.get(playerId)
    if (t == null) 0 else (t.longValue >>> 32).toInt
  }

  /** A player's deaths in this match so far, as the KILLs have told them. */
  def deathsOf(playerId: UUID): Int = {
    val t = tallies.get(playerId)
    if (t == null) 0 else t.longValue.toInt
  }

  /** Counts every change to the kills and deaths: whoever shows them knows when to look again. */
  def talliesChanged: Int = _talliesChanged

  private def tally(playerId: UUID, kills: Int, deaths: Int): Unit =
    tallies.put(playerId, java.lang.Long.valueOf((kills.toLong << 32) | (deaths & 0xFFFFFFFFL)))
  private var gameTimeSyncRemaining: Int = 0
  private var gameTimeSyncTimestamp: Long = 0L
  val scoreboard: CopyOnWriteArrayList[ScoreEntry] = new CopyOnWriteArrayList[ScoreEntry]()
  @volatile var lastKillerCharacterName: String = ""
  @volatile var lastKillerCharacter: Byte = -1
  // The kill feed: the last five kills anyone made, oldest first
  val killFeed: CopyOnWriteArrayList[KillFeedEntry] = new CopyOnWriteArrayList[KillFeedEntry]()

  // Our kills in a row, which the HUD announces and keeps counting (render/CLAUDE.md, *A kill is
  // announced*): since our last death, and in practice, where nothing kills us, for as long as each
  // comes within PracticeComboMs of the one before. Written by the packet thread; the renderer reads
  // the newest kill as one object, so a frame never sees a count from one kill and a name from another.
  @volatile var killStreak: Int = 0
  /** Our newest kill: when, the run it made and whom. Null before our first of the match. */
  @volatile var lastKill: KillMoment = null
  // How many kills anyone has made in this match, as the KILLs have told them: the first is first blood
  private var killsHeard = 0

  /** Our run of kills as it stands at `nowMs`: in practice, none once the window after the last has
    * passed, though no kill has come to start a new run. */
  def streakAt(nowMs: Long): Int = {
    val run = killStreak
    if (run == 0 || !isPracticeMode) run
    else {
      val last = lastKill
      if (last == null || nowMs - last.atMs > MatchProgress.PracticeComboMs) 0 else run
    }
  }

  /** We died: our run of kills is over. */
  private[game] def endStreak(): Unit = killStreak = 0
  @volatile var gameOverListener: () => Unit = _

  // Practice mode state
  @volatile var isPracticeMode: Boolean = false
  // Practice's tallies: its longest run of kills (killStreak), the kills, the shots fired
  @volatile var practiceBestCombo: Int = 0
  @volatile var practiceHits: Int = 0
  @volatile var practiceShots: Int = 0

  def gameTimeRemaining: Int = {
    if (gameTimeSyncTimestamp == 0L) return 0
    val elapsed = ((System.currentTimeMillis() - gameTimeSyncTimestamp) / 1000).toInt
    Math.max(0, gameTimeSyncRemaining - elapsed)
  }

  /** No match: no score, nobody's kills or deaths, no clock, no scoreboard. */
  private[game] def forgetScores(): Unit = {
    killCount = 0
    deathCount = 0
    tallies.clear()
    _talliesChanged += 1
    gameTimeSyncRemaining = 0
    gameTimeSyncTimestamp = 0L
    scoreboard.clear()
    killFeed.clear()
    killStreak = 0
    lastKill = null
    lastKillerCharacter = -1
    killsHeard = 0
  }

  /** A match begins: practice's tallies start again (it is still practice if it was), and the
    * clock runs from the lobby's duration until the server's first TIME_SYNC. */
  private[game] def beginScoring(): Unit = {
    killStreak = 0
    lastKill = null
    killsHeard = 0
    practiceBestCombo = 0
    practiceHits = 0
    practiceShots = 0
    gameTimeSyncRemaining = currentLobbyDuration * 60
    gameTimeSyncTimestamp = System.currentTimeMillis()
  }

  /** A kill of ours: the run it makes, practice's best, and the kill the HUD announces. */
  private def ourKill(now: Long, victimName: String, victimCharacter: Byte): Unit = {
    val run = streakAt(now) + 1
    killStreak = run
    if (isPracticeMode && run > practiceBestCombo) practiceBestCombo = run
    lastKill = new KillMoment(now, run, victimName, victimCharacter, firstBlood = killsHeard == 1 && !isPracticeMode)
  }

  private[game] def handleGameEvent(packet: GameEventPacket): Unit = {
    packet.getEventType match {
      case GameEvent.KILL =>
        val killerId = packet.getPlayerId
        val victimId = packet.getTargetId
        // The death itself, on our screen whatever became of the update that also says so
        if (victimId != null) heardKilled(victimId, packet.getSequenceNumber)

        if (killerId.equals(localPlayerId)) {
          killCount = packet.getKills.toInt
          deathCount = packet.getDeaths.toInt

          if (isPracticeMode) practiceHits += 1
        }
        if (victimId != null && victimId.equals(localPlayerId) && !killerId.equals(localPlayerId)) {
          deathCount += 1
        }
        // Everyone's, for the scoreboard: the killer's as the server counts them, a death more for
        // whoever they killed (nobody can kill themselves, but should they, their own count says so)
        tally(killerId, packet.getKills.toInt, packet.getDeaths.toInt)
        if (victimId != null && !victimId.equals(killerId)) tally(victimId, killsOf(victimId), deathsOf(victimId) + 1)
        _talliesChanged += 1

        // Add to kill feed (using character names)
        val killerName = if (killerId.equals(localPlayerId)) Messages.t("You") else {
          val p = players.get(killerId)
          if (p != null) I18n.characterName(CharacterDef.get(p.getCharacterId)) else killerId.toString.substring(0, 8)
        }
        val victimName = if (victimId != null && victimId.equals(localPlayerId)) Messages.t("You") else {
          if (victimId != null) {
            val p = players.get(victimId)
            if (p != null) I18n.characterName(CharacterDef.get(p.getCharacterId)) else victimId.toString.substring(0, 8)
          } else "?"
        }

        // Track who killed the local player (for death screen)
        if (victimId != null && victimId.equals(localPlayerId)) {
          val killerP = players.get(killerId)
          lastKillerCharacterName = if (killerP != null) I18n.characterName(CharacterDef.get(killerP.getCharacterId)) else "?"
          lastKillerCharacter = if (killerP != null) killerP.getCharacterId else -1
          // A moment a player may mean ("right before I died"), for a problem report's timeline
          Diagnostics.noticed(NoticedKind.Died, if (killerP != null) killerP.getCharacterId.toInt else -1,
            detail = if (killerP != null) CharacterDef.get(killerP.getCharacterId).displayName else "")
        }
        if (victimId != null) {
          val victimPlayer = players.get(victimId)
          val (distance, pan) =
            if (victimPlayer != null) {
              val vx = victimPlayer.getPosition.getX.toFloat
              val vy = victimPlayer.getPosition.getY.toFloat
              (distanceFromLocal(vx, vy), panFromLocal(vx, vy))
            } else (0f, 0f)
          AudioManager.playDeath(distance, pan)
        }

        val now = System.currentTimeMillis()
        killsHeard += 1
        val byUs = killerId.equals(localPlayerId)
        val onUs = victimId != null && victimId.equals(localPlayerId)
        killFeed.add(new KillFeedEntry(now, killerName, victimName, characterIdOf(killerId), characterIdOf(victimId),
          teamOf(killerId), if (victimId != null) teamOf(victimId) else 0, byUs, onUs))
        // Keep only last 5
        while (killFeed.size() > 5) killFeed.remove(0)
        if (byUs && !onUs) ourKill(now, victimName, characterIdOf(victimId))

      case GameEvent.TIME_SYNC =>
        gameTimeSyncRemaining = packet.getRemainingSeconds
        gameTimeSyncTimestamp = System.currentTimeMillis()

      case GameEvent.MATCH_OPENING =>
        setOpening(packet.getOpeningMs, packet.getOpeningRules)

      case GameEvent.MATCH_INFO =>
        heardMatchId(packet.getTargetId)

      case GameEvent.GAME_OVER =>
        scoreboard.clear()
        // Reset any leftover ELO delta from a previous match so a casual
        // game's scoreboard doesn't inherit ranked info.
        pendingEloChange = None

      case GameEvent.SCORE_ENTRY =>
        val entry = new ScoreEntry(packet.getPlayerId, packet.getKills.toInt, packet.getDeaths.toInt, packet.getRank.toInt, packet.getTeamId.toInt)
        scoreboard.add(entry)

      case GameEvent.SCORE_END =>
        // Only for the match we are in: one we just left can still end before the server
        // has taken us out of it, and must not pull us out of the lobby browser.
        if (clientState == ClientState.PLAYING) {
          clientState = ClientState.SCOREBOARD
          if (gameOverListener != null) gameOverListener()
        }

      case GameEvent.RESPAWN =>
        heardRespawned(packet.getPlayerId, packet.getSpawnX.toInt, packet.getSpawnY.toInt, packet.getSequenceNumber)

      case _ =>
        println(s"GameClient: Unknown game event ${packet.getEventType}")
    }
  }

  /** Leave the match in progress. Practice ends on the server and its results screen still
    * follows; any other match carries on without us, so we are straight back in the browser. */
  def leaveMatch(): Unit = {
    val packet = new LobbyActionPacket(
      sequenceNumber.getAndIncrement(), localPlayerId, LobbyAction.LEAVE
    )
    send(packet)
    if (!isPracticeMode) {
      // Practice's results come from where it was played: anything else we are done with
      closeMatchConnection()
      returnToLobbyBrowser()
    }
  }
}

object MatchProgress {
  /** How long after a kill in practice the next keeps the run going (MatchProgress.killStreak). */
  val PracticeComboMs = 4000L
}
