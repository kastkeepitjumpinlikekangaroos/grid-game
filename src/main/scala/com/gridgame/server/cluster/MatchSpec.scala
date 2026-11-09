package com.gridgame.server.cluster

import com.gridgame.common.model.PlayerResult
import com.gridgame.server.bots.BotSlot
import com.gridgame.server.game.MatchOutcome
import com.gridgame.server.lobby.{Lobby, LobbyStatus}
import com.google.gson.{JsonArray, JsonObject}

import java.util.{Base64, UUID}
import scala.jdk.CollectionConverters._

/** A player as a match server is told of them: who, as which character, and the ticket their client
  * logs in to the match with. */
final case class SpecPlayer(id: UUID, name: String, colorRGB: Int, characterId: Byte, ticket: Array[Byte])

/**
 * A match as the central server hands it to a match server ("start"): everything its lobby says of
 * it, its players in the lobby's order (Teams deals them by it), its bots, its id, and the slots of
 * a match server's capacity it takes (`MatchSpec.slotsFor`). The match server makes a lobby of it of
 * its own and launches it as the central server would have (MatchLauncher).
 */
final case class MatchSpec(matchId: UUID, lobbyId: Short, name: String, mapIndex: Int, durationMinutes: Int,
                           maxPlayers: Int, gameMode: Byte, teamSize: Int, matchType: Byte, ranked: Boolean,
                           withBots: Boolean, players: Seq[SpecPlayer], bots: Seq[BotSlot], slots: Int = 1) {

  def toJson: JsonObject = {
    val m = ControlLink.message("start")
    m.addProperty("matchId", matchId.toString)
    m.addProperty("lobbyId", lobbyId)
    m.addProperty("name", name)
    m.addProperty("mapIndex", mapIndex)
    m.addProperty("durationMinutes", durationMinutes)
    m.addProperty("maxPlayers", maxPlayers)
    m.addProperty("gameMode", gameMode)
    m.addProperty("teamSize", teamSize)
    m.addProperty("matchType", matchType)
    m.addProperty("ranked", ranked)
    m.addProperty("withBots", withBots)
    val ps = new JsonArray
    players.foreach { p =>
      val o = new JsonObject
      o.addProperty("id", p.id.toString)
      o.addProperty("name", p.name)
      o.addProperty("color", p.colorRGB)
      o.addProperty("character", p.characterId)
      o.addProperty("ticket", Base64.getEncoder.encodeToString(p.ticket))
      ps.add(o)
    }
    m.add("players", ps)
    val bs = new JsonArray
    bots.foreach { b =>
      val o = new JsonObject
      o.addProperty("id", b.id.toString)
      o.addProperty("name", b.name)
      o.addProperty("character", b.characterId)
      bs.add(o)
    }
    m.add("bots", bs)
    m.addProperty("slots", slots)
    m
  }

  /** A lobby of the match server's own for it, as the central server's was when it started. */
  def toLobby: Lobby = {
    val host = players.headOption.map(_.id).getOrElse(new UUID(0L, 0L))
    val lobby = new Lobby(lobbyId, host, name, mapIndex, durationMinutes, maxPlayers)
    lobby.gameMode = gameMode
    lobby.teamSize = teamSize
    lobby.matchType = matchType
    lobby.isRanked = ranked
    lobby.matchId = matchId
    players.foreach { p =>
      lobby.players.add(p.id)
      lobby.setCharacter(p.id, p.characterId)
    }
    bots.foreach(lobby.botManager.seat)
    lobby.status = LobbyStatus.IN_GAME
    lobby
  }
}

object MatchSpec {
  /**
   * The slots of a match server's capacity a match takes: what it costs the server, in matches of
   * eight humans. A match costs for everyone in it (each one's moves and shots taken, checked and
   * passed on, a bot's thinking) and more for its humans times everyone (each human is sent
   * everyone's moves and shots): `players × (8 + humans) / 128`, rounded up, and never less than
   * one. Fitted to what was measured (ops/kubernetes/CLAUDE.md, *How many matches a match server
   * plays*): sixteen humans cost three matches of eight, one human with seven bots less than one;
   * thirty-two humans, a lobby's most, are ten.
   */
  def slotsFor(humans: Int, bots: Int): Int = {
    val players = humans + bots
    math.max(1, math.ceil((players * (8 + humans)).toDouble / 128.0).toInt)
  }

  /** Read a "start" message; throws IllegalArgumentException if it isn't one. */
  def fromJson(m: JsonObject): MatchSpec =
    try MatchSpec(
      UUID.fromString(m.get("matchId").getAsString),
      m.get("lobbyId").getAsShort,
      m.get("name").getAsString,
      m.get("mapIndex").getAsInt,
      m.get("durationMinutes").getAsInt,
      m.get("maxPlayers").getAsInt,
      m.get("gameMode").getAsByte,
      m.get("teamSize").getAsInt,
      m.get("matchType").getAsByte,
      m.get("ranked").getAsBoolean,
      m.get("withBots").getAsBoolean,
      m.getAsJsonArray("players").asScala.map(_.getAsJsonObject).map { o =>
        SpecPlayer(UUID.fromString(o.get("id").getAsString), o.get("name").getAsString, o.get("color").getAsInt,
          o.get("character").getAsByte, Base64.getDecoder.decode(o.get("ticket").getAsString))
      }.toSeq,
      m.getAsJsonArray("bots").asScala.map(_.getAsJsonObject).map { o =>
        BotSlot(UUID.fromString(o.get("id").getAsString), o.get("name").getAsString, o.get("character").getAsByte)
      }.toSeq,
      if (m.has("slots")) math.max(1, m.get("slots").getAsInt) else 1
    ) catch {
      case e: IllegalArgumentException => throw e
      case e: Exception => throw new IllegalArgumentException(s"not a match: $e", e)
    }

  /** A match's end as a match server reports it ("ended"): its outcome and its journal. */
  def endedJson(outcome: MatchOutcome, journal: String): JsonObject = {
    val m = ControlLink.message("ended")
    m.addProperty("matchId", outcome.matchId.toString)
    m.addProperty("playedSeconds", outcome.playedSeconds)
    val rs = new JsonArray
    outcome.results.foreach { r =>
      val o = new JsonObject
      o.addProperty("id", r.playerId.toString)
      o.addProperty("kills", r.kills)
      o.addProperty("deaths", r.deaths)
      o.addProperty("rank", r.rank)
      o.addProperty("team", r.teamId)
      outcome.characters.get(r.playerId).foreach(c => o.addProperty("character", c))
      rs.add(o)
    }
    m.add("results", rs)
    m.addProperty("journal", journal)
    m
  }

  def outcomeFromJson(m: JsonObject): MatchOutcome =
    try {
      val rows = m.getAsJsonArray("results").asScala.map(_.getAsJsonObject).toSeq
      MatchOutcome(
        UUID.fromString(m.get("matchId").getAsString),
        m.get("playedSeconds").getAsInt,
        rows.map(o => PlayerResult(UUID.fromString(o.get("id").getAsString), o.get("kills").getAsInt,
          o.get("deaths").getAsInt, o.get("rank").getAsInt, o.get("team").getAsByte)),
        rows.filter(_.has("character")).map(o => UUID.fromString(o.get("id").getAsString) -> o.get("character").getAsByte).toMap
      )
    } catch { case e: Exception => throw new IllegalArgumentException(s"not a match's outcome: $e", e) }

  /** A message about one match: `kind`, its id, and a player's if it is about one. */
  def about(kind: String, matchId: UUID, playerId: UUID = null): JsonObject = {
    val m = ControlLink.message(kind)
    m.addProperty("matchId", matchId.toString)
    if (playerId != null) m.addProperty("playerId", playerId.toString)
    m
  }

  def matchIdOf(m: JsonObject): UUID = uuid(m, "matchId")
  def playerIdOf(m: JsonObject): UUID = uuid(m, "playerId")

  private def uuid(m: JsonObject, k: String): UUID =
    try if (m.has(k)) UUID.fromString(m.get(k).getAsString) else null
    catch { case _: Exception => null }
}
