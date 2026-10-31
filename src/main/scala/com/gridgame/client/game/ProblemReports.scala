package com.gridgame.client.game

import com.gridgame.client.diagnostics.{Diagnostics, StateSample}
import com.gridgame.common.WorldRegistry
import com.gridgame.common.diagnostics.Recording.StateEffects
import com.gridgame.common.model.CharacterDef
import com.gridgame.common.protocol._
import com.google.gson.{JsonArray, JsonObject}

import java.util.UUID
import java.util.concurrent.{CompletableFuture, ConcurrentHashMap}

/**
 * The client's part in problem reports (design/diagnostics.md): which match this is (the server names it,
 * GameEvent.MATCH_INFO), what the recording is told of the match as it learns it (the map, the mode, who
 * we play), a snapshot of what it thinks of us for the recording four times a second, what a report says
 * of the match and the connection, and asking the server to take a report (BEGIN) and hearing its answer.
 */
trait ProblemReports { this: GameClient =>
  /** The match's own id, as the server named it; null until it has. */
  @volatile var matchId: UUID = _

  // The answer each report asked about is waiting for, by its id
  private val reportAnswers = new ConcurrentHashMap[UUID, CompletableFuture[ProblemReportPacket]]()

  /**
   * Ask the server to take report `reportId` (`size` bytes, CRC-32 `crc`, about `aboutMatch`): the future
   * completes with what it says — READY with a ticket for the upload, REFUSED, or ACCEPTED when it has the
   * report already.
   */
  def beginReport(reportId: UUID, size: Int, crc: Int, aboutMatch: UUID, kind: Byte): CompletableFuture[ProblemReportPacket] = {
    val answer = new CompletableFuture[ProblemReportPacket]()
    reportAnswers.put(reportId, answer)
    send(ProblemReportPacket.begin(sequenceNumber.getAndIncrement(), localPlayerId, reportId, size, crc, aboutMatch, kind))
    answer
  }

  /** The server's answer to a BEGIN. */
  private[game] def handleProblemReport(packet: ProblemReportPacket): Unit = {
    val answer = reportAnswers.remove(packet.getReportId)
    if (answer != null) answer.complete(packet)
  }

  /** The server named the match (MATCH_INFO). */
  private[game] def heardMatchId(id: UUID): Unit = {
    matchId = id
    if (id != null) Diagnostics.noteMatch("matchId", id.toString)
  }

  /** A match begins: what the lobby said of it, for its recording. */
  private[game] def noteMatchStarting(): Unit = {
    matchId = null
    Diagnostics.noteMatch("map", WorldRegistry.getFilename(currentLobbyMapIndex))
    Diagnostics.noteMatch("mode", if (isPracticeMode) "practice" else if (currentLobbyGameMode == 1) "teams" else "ffa")
    Diagnostics.noteMatch("practice", isPracticeMode)
    Diagnostics.noteMatch("durationMinutes", Int.box(currentLobbyDuration))
    Diagnostics.noteMatch("lobby", Int.box(currentLobbyId.toInt))
  }

  /** The server named the map (WORLD_INFO) and the character it has us play (our join echo). */
  private[game] def noteWorldFile(file: String): Unit = Diagnostics.noteMatch("map", file)
  private[game] def noteOwnCharacter(id: Byte): Unit =
    Diagnostics.noteMatch("character", CharacterDef.get(id).displayName)

  /** The round trip of the last heartbeat, ms; -1 before one has come back. */
  @volatile var lastRoundTripMs: Int = -1

  /** What we think of ourselves now, into `s`, for the recording: allocates nothing. */
  def sampleState(s: StateSample): Unit = {
    s.health = getLocalHealth
    s.maxHealth = getSelectedCharacterMaxHealth
    var e = 0
    if (isFrozen) e |= StateEffects.Frozen
    if (isRooted) e |= StateEffects.Rooted
    if (isSlowed) e |= StateEffects.Slowed
    if (isStunned) e |= StateEffects.Stunned
    if (isPhased) e |= StateEffects.Phased
    if (isBurning) e |= StateEffects.Burning
    if (isPoisoned) e |= StateEffects.Poisoned
    if (hasSpeedBoost) e |= StateEffects.SpeedBoost
    if (hasShield) e |= StateEffects.Shield
    if (hasGemBoost) e |= StateEffects.Gem
    if (getIsDead) e |= StateEffects.Dead
    if (isRespawning) e |= StateEffects.Respawning
    if (isCharging) e |= StateEffects.Charging
    if (hasBarrier) e |= StateEffects.Barrier
    if (attacksLocked) e |= StateEffects.AttacksLocked
    if (isSwooping) e |= StateEffects.Swooping
    if (isMovementBlocked) e |= StateEffects.MovementBlocked
    s.effects = e
    s.qLeftMs = (getQCooldownRemaining * 1000).toInt
    s.eLeftMs = (getECooldownRemaining * 1000).toInt
    s.rLeftMs = (getResetCooldownRemaining * 1000).toInt
    s.charge = getChargeLevel
    s.items = getInventoryCount
    s.kills = killCount
    s.deaths = deathCount
    s.secondsLeft = gameTimeRemaining
    s.roundTripMs = lastRoundTripMs
    s.projectiles = getProjectiles.size()
    s.players = getPlayers.size()
    s.serverMoves = serverMovesSeen
    s.knockbacks = knockbacksSeen
    s.queued = queuedPackets
  }

  /** What the client knows of the match now, for a report about it: who is in it, the score. */
  def matchSummary: JsonObject = {
    val o = new JsonObject
    if (matchId != null) o.addProperty("matchId", matchId.toString)
    o.addProperty("character", getSelectedCharacterDef.displayName)
    o.addProperty("practice", isPracticeMode)
    o.addProperty("kills", killCount)
    o.addProperty("deaths", deathCount)
    val players = new JsonArray
    getPlayers.forEach { (id, p) =>
      val j = new JsonObject
      // Whole: bots' ids share their first digits
      j.addProperty("id", id.toString)
      j.addProperty("name", p.getName)
      j.addProperty("character", CharacterDef.get(p.getCharacterId).displayName)
      j.addProperty("team", p.getTeamId)
      j.addProperty("left", playerLeftMatch(id))
      players.add(j)
    }
    o.add("players", players)
    o
  }

  /** The server and who we are on it, for a report's "connection". */
  def connectionSummary: JsonObject = {
    val o = new JsonObject
    o.addProperty("server", s"$serverHost:$serverPort")
    o.addProperty("player", playerName)
    if (localPlayerId != null) o.addProperty("playerId", localPlayerId.toString)
    o
  }

  /** Where the server is, for the report's own connection to it. */
  def reportServerHost: String = serverHost
  def reportServerPort: Int = serverPort
}
