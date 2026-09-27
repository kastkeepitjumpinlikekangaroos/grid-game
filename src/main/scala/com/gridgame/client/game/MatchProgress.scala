package com.gridgame.client.game

import com.gridgame.client.audio.AudioManager
import com.gridgame.client.i18n.{I18n, Messages}
import com.gridgame.common.model._
import com.gridgame.common.protocol._

import java.util.concurrent.CopyOnWriteArrayList

/**
 * The match as a whole, as the server reports it (GameEvent): kills and deaths, the kill feed, the
 * match clock, the scoreboard at the end, practice's tallies, and leaving the match.
 */
trait MatchProgress { this: GameClient =>
  // Esc arms leaving the match until this time; a second Esc before then leaves.
  @volatile var leaveConfirmUntil: Long = 0L

  @volatile var killCount: Int = 0
  @volatile var deathCount: Int = 0
  private var gameTimeSyncRemaining: Int = 0
  private var gameTimeSyncTimestamp: Long = 0L
  val scoreboard: CopyOnWriteArrayList[ScoreEntry] = new CopyOnWriteArrayList[ScoreEntry]()
  @volatile var lastKillerCharacterName: String = ""
  // Kill feed: (timestamp, killerName, victimName)
  val killFeed: CopyOnWriteArrayList[Array[AnyRef]] = new CopyOnWriteArrayList[Array[AnyRef]]()
  @volatile var gameOverListener: () => Unit = _

  // Practice mode state
  @volatile var isPracticeMode: Boolean = false
  @volatile var practiceCombo: Int = 0
  @volatile var practiceBestCombo: Int = 0
  @volatile var practiceHits: Int = 0
  @volatile var practiceShots: Int = 0
  @volatile var practiceLastHitTime: Long = 0L

  def gameTimeRemaining: Int = {
    if (gameTimeSyncTimestamp == 0L) return 0
    val elapsed = ((System.currentTimeMillis() - gameTimeSyncTimestamp) / 1000).toInt
    Math.max(0, gameTimeSyncRemaining - elapsed)
  }

  /** No match: no score, no clock, no scoreboard, and nothing armed to leave one. */
  private[game] def forgetScores(): Unit = {
    leaveConfirmUntil = 0L
    killCount = 0
    deathCount = 0
    gameTimeSyncRemaining = 0
    gameTimeSyncTimestamp = 0L
    scoreboard.clear()
    killFeed.clear()
  }

  /** A match begins: practice's tallies start again (it is still practice if it was), and the
    * clock runs from the lobby's duration until the server's first TIME_SYNC. */
  private[game] def beginScoring(): Unit = {
    practiceCombo = 0
    practiceBestCombo = 0
    practiceHits = 0
    practiceShots = 0
    practiceLastHitTime = 0L
    gameTimeSyncRemaining = currentLobbyDuration * 60
    gameTimeSyncTimestamp = System.currentTimeMillis()
  }

  private[game] def handleGameEvent(packet: GameEventPacket): Unit = {
    packet.getEventType match {
      case GameEvent.KILL =>
        val killerId = packet.getPlayerId
        val victimId = packet.getTargetId

        if (killerId.equals(localPlayerId)) {
          killCount = packet.getKills.toInt
          deathCount = packet.getDeaths.toInt

          // Track practice mode stats
          if (isPracticeMode) {
            practiceHits += 1
            practiceCombo += 1
            if (practiceCombo > practiceBestCombo) practiceBestCombo = practiceCombo
            practiceLastHitTime = System.currentTimeMillis()
          }
        }
        if (victimId != null && victimId.equals(localPlayerId) && !killerId.equals(localPlayerId)) {
          deathCount += 1
        }

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

        val feedText = Messages.t("{0} killed {1}", killerName, victimName)
        killFeed.add(Array(System.currentTimeMillis().asInstanceOf[AnyRef], killerName.asInstanceOf[AnyRef], victimName.asInstanceOf[AnyRef], feedText.asInstanceOf[AnyRef]))
        // Keep only last 5
        while (killFeed.size() > 5) killFeed.remove(0)

      case GameEvent.TIME_SYNC =>
        gameTimeSyncRemaining = packet.getRemainingSeconds
        gameTimeSyncTimestamp = System.currentTimeMillis()

      case GameEvent.MATCH_OPENING =>
        setOpening(packet.getOpeningMs, packet.getOpeningRules)

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
        if (packet.getPlayerId.equals(localPlayerId)) respawned(packet.getSpawnX.toInt, packet.getSpawnY.toInt)

      case _ =>
        println(s"GameClient: Unknown game event ${packet.getEventType}")
    }
  }

  /** Leave the match in progress. Practice ends on the server and its results screen still
    * follows; any other match carries on without us, so we are straight back in the browser. */
  def leaveMatch(): Unit = {
    leaveConfirmUntil = 0L
    val packet = new LobbyActionPacket(
      sequenceNumber.getAndIncrement(), localPlayerId, LobbyAction.LEAVE
    )
    send(packet)
    if (!isPracticeMode) returnToLobbyBrowser()
  }
}
