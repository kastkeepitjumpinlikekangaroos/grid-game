package com.gridgame.tools

import com.gridgame.common.WorldRegistry
import com.gridgame.common.diagnostics.Recording
import com.gridgame.common.diagnostics.Recording.{FrameRecord, InputDevice, InputRecord, NoticedKind, NoticedRecord, StateRecord}
import com.gridgame.common.model.{CharacterDef, ItemType, MatchOpening, ProjectileDef, TrapDef}
import com.gridgame.common.protocol._

import java.util.{Locale, UUID}
import scala.collection.mutable

/** Who's who in a recording: players by id, named from report.json's list and every PLAYER_JOIN seen. */
final class PlayerNames(val local: UUID) {
  private val names = mutable.HashMap.empty[UUID, String]

  def learn(id: UUID, name: String): Unit =
    if (id != null && name != null && name.trim.nonEmpty) names(id) = Clean.line(name.trim)

  def nameOf(id: UUID): Option[String] = names.get(id)

  /** An id as its first eight characters, with the name it goes by once known: "a1b2c3d4/Bob". */
  def apply(id: UUID): String =
    if (id == null || (id.getMostSignificantBits == 0L && id.getLeastSignificantBits == 0L)) "-"
    else {
      val n = names.get(id).map("/" + _).getOrElse("")
      val us = if (local != null && id == local) " (us)" else ""
      id.toString.take(8) + n + us
    }

  def copy(): PlayerNames = {
    val c = new PlayerNames(local)
    c.names ++= names
    c
  }
}

/** A packet of the recording, decoded for a line of the timeline or a row of packets.csv. */
final case class PacketView(typeName: String, seq: Int, player: String, action: String, x: String, y: String,
                            detail: String, redacted: Boolean, decoded: Boolean)

/** A FRAME record's fields (Recording.FrameRecord). */
final case class FrameView(deltaMs: Float, workMs: Float, cellX: Float, cellY: Float, drawnX: Float, drawnY: Float,
                           aimX: Float, aimY: Float, keys: Int, flags: Int, quality: Int, aiming: Int) {
  def isHitch: Boolean = deltaMs > ReportPackets.HitchDeltaMs || workMs > ReportPackets.HitchWorkMs
}

/** A STATE record's fields (Recording.StateRecord). */
final case class StateView(health: Int, maxHealth: Int, effects: Int, qLeftMs: Int, eLeftMs: Int, rLeftMs: Int,
                           charge: Int, items: Int, kills: Int, deaths: Int, secondsLeft: Int, roundTripMs: Int,
                           projectiles: Int, players: Int, serverMoves: Int, knockbacks: Int, queued: Int)

/** An INPUT record's fields (Recording.InputRecord). */
final case class InputView(device: Int, action: Int, code: Int, mods: Int)

/** A NOTICED record's fields (Recording.NoticedRecord): what the client noticed for itself, and when (for a
  * silence, `atMicros` is when it ended). `givenText` is report.json's words for one of a kind this tool
  * doesn't know. */
final case class NoticedView(atMicros: Long, kind: Int, a: Int, b: Int, detail: String, givenText: String = "") {
  def name: String = NoticedKind.name(kind)

  private def known: Boolean = !name.startsWith("noticed")

  /** When it began: a silence's start, the record's time for anything else. */
  def fromMicros: Long = if (kind == NoticedKind.ServerQuiet) Math.max(0L, atMicros - a * 1000L) else atMicros

  /** In English: NoticedKind's words, with a killer's character named where the record gives one. */
  def text: String =
    if (!known && givenText.nonEmpty) givenText
    else {
      val words = NoticedKind.describe(kind, a, b, detail)
      if (kind == NoticedKind.Died && a >= 0 && a < 256) s"$words; the killer played ${ReportPackets.character(a.toByte)}" else words
    }
}

/** Text as it is safe to write: what a player's machine sent can hold anything, a terminal's escape
  * codes among it. */
object Clean {
  /** One line: control characters (newlines too) shown as a dot, nothing else touched. */
  def line(s: String): String = if (s == null) "" else s.map(c => if (Character.isISOControl(c)) '·' else c)

  /** Several lines: kept apart, every other control character shown as a dot. */
  def lines(s: String): String =
    if (s == null) "" else s.replace("\r\n", "\n").map(c => if (c != '\n' && c != '\t' && Character.isISOControl(c)) '·' else c)
}

/**
 * Every packet type, input, frame and state of a recording in words: names for the actions and the
 * events, players by their names, the projectile, item, trap, character and map each id stands for.
 */
object ReportPackets {
  /** A frame this much after the one before it, or this long in the making, is a hitch the player saw. */
  val HitchDeltaMs = 50f
  val HitchWorkMs = 33f

  // ── Times and numbers ─────────────────────────────────────────────────

  /** A time into the recording: "mm:ss.mmm", the minutes going on past an hour. */
  def time(micros: Long): String = {
    val ms = Math.max(0L, micros) / 1000L
    "%02d:%02d.%03d".formatLocal(Locale.ROOT, ms / 60000L, (ms / 1000L) % 60L, ms % 1000L)
  }

  def seconds(micros: Long): String = "%.6f".formatLocal(Locale.ROOT, micros / 1e6)

  def num(v: Double, decimals: Int): String = s"%.${decimals}f".formatLocal(Locale.ROOT, v)

  def hex(b: Array[Byte]): String = b.map(x => "%02x".formatLocal(Locale.ROOT, x & 0xFF)).mkString

  def colour(argb: Int): String = "#%08X".formatLocal(Locale.ROOT, argb)

  // ── Names for ids ─────────────────────────────────────────────────────

  def character(id: Byte): String =
    if (CharacterDef.isValid(id)) s"${CharacterDef.get(id).displayName}(${id & 0xFF})" else s"character ${id & 0xFF}"

  def projectile(id: Byte): String = s"${ProjectileDef.get(id).name}(${id & 0xFF})"

  def item(id: Byte): String = ItemType.all.find(_.id == id).map(t => s"${t.name}(${id & 0xFF})").getOrElse(s"item ${id & 0xFF}")

  def trap(id: Byte): String = Option(TrapDef.get(id)).map(d => s"${d.name}(${id & 0xFF})").getOrElse(s"trap ${id & 0xFF}")

  def map(index: Int): String =
    if (index >= 0 && index < WorldRegistry.size) s"${WorldRegistry.getDisplayName(index)} (${WorldRegistry.getFilename(index)})"
    else s"map $index"

  def mode(m: Byte): String = m match { case 0 => "free-for-all"; case 1 => "Teams"; case x => s"mode $x" }

  def slot(s: Int): String = s match {
    case AttackSlot.PRIMARY => "primary"; case AttackSlot.Q => "Q"; case AttackSlot.E => "E"; case AttackSlot.BURST => "burst"
    case x => s"slot $x"
  }

  def quality(tier: Int): String = tier match { case 0 => "high"; case 1 => "medium"; case 2 => "low"; case x => s"tier $x" }

  private def named(v: Int, names: (Int, String)*): String = names.collectFirst { case (k, n) if k == v => n }.getOrElse(s"$v")

  def projectileAction(a: Byte): String = named(a, ProjectileAction.SPAWN.toInt -> "SPAWN", ProjectileAction.MOVE.toInt -> "MOVE",
    ProjectileAction.HIT.toInt -> "HIT", ProjectileAction.DESPAWN.toInt -> "DESPAWN", ProjectileAction.PIERCE.toInt -> "PIERCE",
    ProjectileAction.BLOCKED.toInt -> "BLOCKED")

  def itemAction(a: Byte): String = named(a, ItemAction.SPAWN.toInt -> "SPAWN", ItemAction.PICKUP.toInt -> "PICKUP",
    ItemAction.INVENTORY.toInt -> "INVENTORY", ItemAction.USE.toInt -> "USE", ItemAction.USE_REJECTED.toInt -> "USE_REJECTED")

  def trapAction(a: Byte): String = named(a, TrapAction.PLACE.toInt -> "PLACE", TrapAction.SPAWN.toInt -> "SPAWN",
    TrapAction.TRIGGER.toInt -> "TRIGGER", TrapAction.REMOVE.toInt -> "REMOVE", TrapAction.REJECTED.toInt -> "REJECTED")

  def lobbyAction(a: Byte): String = named(a, LobbyAction.CREATE.toInt -> "CREATE", LobbyAction.JOIN.toInt -> "JOIN",
    LobbyAction.LEAVE.toInt -> "LEAVE", LobbyAction.START.toInt -> "START", LobbyAction.LIST_REQUEST.toInt -> "LIST_REQUEST",
    LobbyAction.LIST_ENTRY.toInt -> "LIST_ENTRY", LobbyAction.LIST_END.toInt -> "LIST_END", LobbyAction.JOINED.toInt -> "JOINED",
    LobbyAction.PLAYER_JOINED.toInt -> "PLAYER_JOINED", LobbyAction.PLAYER_LEFT.toInt -> "PLAYER_LEFT",
    LobbyAction.GAME_STARTING.toInt -> "GAME_STARTING", LobbyAction.LOBBY_CLOSED.toInt -> "LOBBY_CLOSED",
    LobbyAction.CONFIG_UPDATE.toInt -> "CONFIG_UPDATE", LobbyAction.CHARACTER_SELECT.toInt -> "CHARACTER_SELECT",
    LobbyAction.ADD_BOT.toInt -> "ADD_BOT", LobbyAction.REMOVE_BOT.toInt -> "REMOVE_BOT",
    LobbyAction.PRACTICE_START.toInt -> "PRACTICE_START", LobbyAction.ACTION_FAILED.toInt -> "ACTION_FAILED",
    LobbyAction.MEMBER.toInt -> "MEMBER")

  def gameEvent(e: Byte): String = named(e, GameEvent.KILL.toInt -> "KILL", GameEvent.TIME_SYNC.toInt -> "TIME_SYNC",
    GameEvent.GAME_OVER.toInt -> "GAME_OVER", GameEvent.SCORE_ENTRY.toInt -> "SCORE_ENTRY", GameEvent.SCORE_END.toInt -> "SCORE_END",
    GameEvent.RESPAWN.toInt -> "RESPAWN", GameEvent.MATCH_OPENING.toInt -> "MATCH_OPENING", GameEvent.MATCH_INFO.toInt -> "MATCH_INFO")

  def authAction(a: Byte): String = named(a, AuthAction.LOGIN.toInt -> "LOGIN", AuthAction.SIGNUP.toInt -> "SIGNUP",
    AuthAction.REFRESH.toInt -> "REFRESH", AuthAction.LOGOUT.toInt -> "LOGOUT", AuthAction.UPLOAD.toInt -> "UPLOAD")

  def reportAction(a: Byte): String = named(a, ProblemReportAction.BEGIN.toInt -> "BEGIN", ProblemReportAction.READY.toInt -> "READY",
    ProblemReportAction.REFUSED.toInt -> "REFUSED", ProblemReportAction.ACCEPTED.toInt -> "ACCEPTED",
    ProblemReportAction.FAILED.toInt -> "FAILED")

  def reportReason(r: Byte): String = named(r, ProblemReportRefusal.TOO_MANY.toInt -> "too many",
    ProblemReportRefusal.TOO_BIG.toInt -> "too big", ProblemReportRefusal.STORAGE_FULL.toInt -> "storage full",
    ProblemReportRefusal.BUSY.toInt -> "busy", ProblemReportRefusal.BAD_TICKET.toInt -> "bad ticket",
    ProblemReportRefusal.TRUNCATED.toInt -> "truncated", ProblemReportRefusal.CORRUPT.toInt -> "corrupt",
    ProblemReportRefusal.UNAVAILABLE.toInt -> "unavailable")

  def reportKind(k: Byte): String = named(k, ProblemReportKind.MATCH.toInt -> "match", ProblemReportKind.CRASH.toInt -> "crash",
    ProblemReportKind.GENERAL.toInt -> "general")

  def cooldownAction(a: Byte): String = named(a, CooldownResetAction.REQUEST.toInt -> "REQUEST", CooldownResetAction.REJECTED.toInt -> "REJECTED")

  def chatScope(s: Byte): String = named(s, ChatScope.LOBBY.toInt -> "lobby", ChatScope.GAME.toInt -> "game", ChatScope.TEAM.toInt -> "team")

  def rankedAction(a: Byte): String = named(a, RankedQueueAction.QUEUE_JOIN.toInt -> "QUEUE_JOIN",
    RankedQueueAction.QUEUE_LEAVE.toInt -> "QUEUE_LEAVE", RankedQueueAction.QUEUE_STATUS.toInt -> "QUEUE_STATUS",
    RankedQueueAction.MATCH_FOUND.toInt -> "MATCH_FOUND", RankedQueueAction.CHARACTER_CHANGE.toInt -> "CHARACTER_CHANGE")

  def rankedMode(m: Byte): String = named(m, RankedQueueMode.FFA.toInt -> "ffa", RankedQueueMode.DUEL.toInt -> "duel", RankedQueueMode.TEAMS.toInt -> "teams")

  def historyAction(a: Byte): String = named(a, MatchHistoryAction.QUERY.toInt -> "QUERY", MatchHistoryAction.ENTRY.toInt -> "ENTRY",
    MatchHistoryAction.END.toInt -> "END", MatchHistoryAction.STATS.toInt -> "STATS")

  def leaderboardAction(a: Byte): String = named(a, LeaderboardAction.QUERY.toInt -> "QUERY", LeaderboardAction.ENTRY.toInt -> "ENTRY",
    LeaderboardAction.END.toInt -> "END")

  def sessionEnd(r: Byte): String = named(r, SessionEndReason.NONE.toInt -> "none", SessionEndReason.REPLACED.toInt -> "REPLACED",
    SessionEndReason.REVOKED.toInt -> "REVOKED")

  /** A PLAYER_UPDATE's effect flags ([42], and [49]'s bits) by name. */
  def updateEffects(flags1: Int, flags2: Int): Seq[String] = {
    val one = Seq(0x01 -> "shield", 0x02 -> "gem", 0x04 -> "frozen", 0x08 -> "phased", 0x10 -> "burning", 0x20 -> "speed",
      0x40 -> "rooted", 0x80 -> "slowed").collect { case (bit, n) if (flags1 & bit) != 0 => n }
    val two = Seq(0x01 -> "stunned", 0x02 -> "poisoned", 0x04 -> "barrier").collect { case (bit, n) if (flags2 & bit) != 0 => n }
    one ++ two
  }

  def flagNames(flags: Int, names: Seq[(Int, String)]): Seq[String] = names.collect { case (bit, n) if (flags & bit) != 0 => n }

  // ── Packets ───────────────────────────────────────────────────────────

  private def pos(v: Float): String = num(v, 2)

  /** The packet in a PacketIn/PacketOut record. A redacted one (Recording.FlagRedacted) shows its header
    * alone; one that won't decode, its bytes. Its PLAYER_JOINs teach `names` who is who. */
  def describe(payload: Array[Byte], flags: Int, outgoing: Boolean, names: PlayerNames): PacketView = {
    if (payload.length != 64)
      return PacketView("?", 0, "-", "", "", "", s"not a packet (${payload.length} bytes): ${hex(payload)}", redacted = false, decoded = false)
    val typeId = payload(0)
    val typeName = try PacketType.fromId(typeId).toString catch { case _: IllegalArgumentException => "type 0x%02x".formatLocal(Locale.ROOT, typeId & 0xFF) }
    val seq = Recording.int(payload, 1)
    val playerId = new UUID(Recording.long(payload, 5), Recording.long(payload, 13))
    if ((flags & Recording.FlagRedacted) != 0)
      return PacketView(typeName, seq, names(playerId), "", "", "", "(redacted: what it carried is the player's own)", redacted = true, decoded = true)
    val packet = try PacketSerializer.deserialize(payload) catch {
      case e: IllegalArgumentException =>
        return PacketView(typeName, seq, names(playerId), "", "", "", s"undecodable (${Clean.line(e.getMessage)}): ${hex(payload)}", redacted = false, decoded = false)
    }
    val (action, x, y, detail) = fields(packet, outgoing, names)
    PacketView(typeName, seq, names(packet.getPlayerId), action, x, y, detail, redacted = false, decoded = true)
  }

  private def fields(packet: Packet, outgoing: Boolean, names: PlayerNames): (String, String, String, String) = packet match {
    case j: PlayerJoinPacket =>
      names.learn(j.getPlayerId, j.getPlayerName)
      ("", j.getPosition.getX.toString, j.getPosition.getY.toString,
        s"""name "${Clean.line(j.getPlayerName)}" ${character(j.getCharacterId)} team ${j.getTeamId} health ${j.getHealth} colour ${colour(j.getColorRGB)}""")

    case u: PlayerUpdatePacket =>
      val parts = Vector.newBuilder[String]
      parts += s"hp ${u.getHealth}"
      if (u.getChargeLevel > 0) parts += s"charge ${u.getChargeLevel}"
      val effects = updateEffects(u.getEffectFlags, u.getEffectFlags2)
      if (effects.nonEmpty) parts += effects.mkString("[", ",", "]")
      parts += character(u.getCharacterId)
      if (u.getTeamId != 0) parts += s"team ${u.getTeamId}"
      parts += s"moves ${u.getServerMoves}"
      if (u.getKnockbacks != 0) parts += s"knockbacks ${u.getKnockbacks}"
      if (u.getAimAngle != 0) parts += s"aim ${num(Math.toDegrees(PlayerUpdatePacket.decodeAimAngle(u.getAimAngle)), 0)}deg"
      if (u.getSlowPercent != 0) parts += s"slow ${u.getSlowPercent}%"
      if (u.getBarrierMs != 0) parts += s"barrier ${u.getBarrierMs}ms"
      if (u.getSpeedBoostMs != 0) parts += s"boost ${u.getSpeedBoostMs}ms"
      if (u.getHeldMs != 0) parts += s"held ${u.getHeldMs}ms"
      if (u.getRootedMs != 0) parts += s"rooted ${u.getRootedMs}ms"
      if (u.getSlowedMs != 0) parts += s"slowed ${u.getSlowedMs}ms"
      ("", u.getPosition.getX.toString, u.getPosition.getY.toString, parts.result().mkString(" "))

    case _: PlayerLeavePacket => ("", "", "", "left")

    case w: WorldInfoPacket => ("", "", "", s"world ${Clean.line(w.getWorldFile)}")

    case _: HeartbeatPacket => ("", "", "", "")

    case p: ProjectilePacket =>
      val action = projectileAction(p.getAction)
      val heading = s"heading (${num(p.getDx, 3)},${num(p.getDy, 3)})"
      val charge = if (p.getChargeLevel > 0) s" charge ${p.getChargeLevel}" else ""
      val target = if (p.getTargetId != null) s" target ${names(p.getTargetId)}" else ""
      val detail =
        if (outgoing && p.getAction == ProjectileAction.SPAWN) s"asks for ${projectile(p.getProjectileType)} from the ${slot(p.getAttackSlot)} $heading$charge"
        else s"#${p.getProjectileId} ${projectile(p.getProjectileType)} tick ${p.getTick} $heading$charge$target"
      (action, pos(p.getX), pos(p.getY), detail)

    case i: ItemPacket => (itemAction(i.getAction), i.getX.toString, i.getY.toString, s"#${i.getItemId} ${item(i.getItemTypeId)}")

    case t: TileUpdatePacket => ("", t.getTileX.toString, t.getTileY.toString, s"tile ${t.getTileId}")

    case l: LobbyActionPacket =>
      val failure = if (l.getAction == LobbyAction.ACTION_FAILED) s" failure ${l.getLobbyStatus}" else ""
      (lobbyAction(l.getAction), "", "",
        s"""lobby ${l.getLobbyId} ${map(l.getMapIndex)} ${l.getDurationMinutes} min ${l.getPlayerCount}/${l.getMaxPlayers} """ +
          s"""status ${l.getLobbyStatus} ${mode(l.getGameMode)} team size ${l.getTeamSize} ${character(l.getCharacterId)} """ +
          s"""name "${Clean.line(l.getLobbyName)}"$failure""")

    case g: GameEventPacket =>
      val subject = names(g.getPlayerId)
      val detail = g.getEventType match {
        case GameEvent.KILL => s"$subject killed ${names(g.getTargetId)} (the killer: ${g.getKills} kills, ${g.getDeaths} deaths)"
        case GameEvent.TIME_SYNC => s"${g.getRemainingSeconds}s left"
        case GameEvent.GAME_OVER => "the match is over"
        case GameEvent.SCORE_ENTRY => s"$subject ${g.getKills} kills ${g.getDeaths} deaths, place ${g.getRank}, team ${g.getTeamId}"
        case GameEvent.SCORE_END => "the scores are all in"
        case GameEvent.RESPAWN => s"$subject respawns at (${g.getSpawnX},${g.getSpawnY})"
        case GameEvent.MATCH_OPENING =>
          val rules = Seq(MatchOpening.DIVIDER.toInt -> "the wall", MatchOpening.NO_ATTACKS.toInt -> "the ceasefire")
            .collect { case (bit, n) if (g.getOpeningRules & bit) != 0 => n }
          s"${g.getOpeningMs}ms of the opening left (${if (rules.isEmpty) "nothing held" else rules.mkString(", ")})"
        case GameEvent.MATCH_INFO => s"match ${Option(g.getTargetId).map(_.toString).getOrElse("-")} (game ${g.getGameId})"
        case e => s"event $e: game ${g.getGameId} ${g.getRemainingSeconds}s ${g.getKills}/${g.getDeaths} ${names(g.getTargetId)}"
      }
      (gameEvent(g.getEventType), "", "", detail)

    case a: AuthRequestPacket => (authAction(a.getAction), "", "", "(what logs in is never shown)")

    case a: AuthResponsePacket =>
      ("", "", "", s"""${if (a.getSuccess) "logged in" else "refused"} "${Clean.line(a.getMessage)}" as ${names(a.getAssignedUUID)}""")

    case m: MatchHistoryPacket =>
      val detail = m.getAction match {
        case MatchHistoryAction.ENTRY => s"match ${m.getMatchId} ${map(m.getMapIndex)} ${m.getKills}/${m.getDeaths} place ${m.getRank} of ${m.getTotalPlayers}"
        case MatchHistoryAction.STATS => s"${m.getTotalKills} kills ${m.getTotalDeaths} deaths ${m.getMatchesPlayed} matches ${m.getWins} wins elo ${m.getOldElo} -> ${m.getElo}"
        case _ => ""
      }
      (historyAction(m.getAction), "", "", detail)

    case r: RankedQueuePacket =>
      (rankedAction(r.getAction), "", "", s"${rankedMode(r.getMode)} ${character(r.getCharacterId)} queue ${r.getQueueSize} elo ${r.getElo} " +
        s"""waited ${r.getWaitTimeSeconds}s lobby ${r.getLobbyId} "${Clean.line(r.getLobbyName)}"""")

    case l: LeaderboardPacket =>
      (leaderboardAction(l.getAction), "", "", if (l.getAction == LeaderboardAction.ENTRY) s"""#${l.getRank} "${Clean.line(l.getUsername)}" elo ${l.getElo}""" else "")

    case _: SessionTokenPacket => ("", "", "", "(the token is never shown)")

    case c: ChatMessagePacket =>
      (chatScope(c.getScope), "", "", if (c.getMessage.isEmpty) "" else s""""${Clean.line(c.getMessage)}"""")

    case t: TrapPacket =>
      val victim = if (t.getVictimId != null) s" under ${names(t.getVictimId)}" else ""
      val slotText = if (t.getAction == TrapAction.PLACE || t.getAction == TrapAction.REJECTED) s" from the ${slot(t.getAttackSlot)}" else ""
      (trapAction(t.getAction), t.getX.toString, t.getY.toString, s"#${t.getTrapId} ${trap(t.getTrapType)} team ${t.getTeamId}$slotText$victim")

    case r: RefreshTokenPacket => ("", "", "", s"(the token is never shown) its login runs out ${java.time.Instant.ofEpochMilli(r.getExpiresAt)}")

    case s: SessionEndPacket => (sessionEnd(s.getReason), "", "", "")

    case c: CooldownResetPacket =>
      (cooldownAction(c.getAction), "", "", if (c.getAction == CooldownResetAction.REJECTED) s"R has ${c.getMsLeft}ms of its own left" else "")

    case r: ProblemReportPacket =>
      val id = Option(r.getReportId).map(_.toString).getOrElse("-")
      val detail = r.getAction match {
        case ProblemReportAction.BEGIN =>
          s"report $id: ${r.getSize} bytes crc ${"%08x".formatLocal(Locale.ROOT, r.getCrc)} ${reportKind(r.getKind)} match ${Option(r.getMatchId).getOrElse("-")}"
        case ProblemReportAction.READY => s"report $id (its ticket is never shown)"
        case ProblemReportAction.REFUSED => s"report $id: ${reportReason(r.getReason)}, try again in ${r.getRetryAfterSeconds}s"
        case ProblemReportAction.ACCEPTED => s"report $id kept as ${Clean.line(r.getCode)}"
        case ProblemReportAction.FAILED => s"report $id: ${reportReason(r.getReason)}"
        case _ => s"report $id"
      }
      (reportAction(r.getAction), "", "", detail)

    case other => ("", "", "", Clean.line(other.toString))
  }

  // ── Inputs ────────────────────────────────────────────────────────────

  def input(p: Array[Byte]): Option[InputView] =
    if (p.length < InputRecord.Size) None
    else Some(InputView(p(InputRecord.Device) & 0xFF, p(InputRecord.Action) & 0xFF, Recording.short(p, InputRecord.Code), p(InputRecord.Mods) & 0xFF))

  /** A GLFW key code as it is labelled on the keyboard. */
  def keyName(code: Int): String = code match {
    case -1 => "(typed into chat: not kept)"
    case 32 => "Space"
    case c if c >= 39 && c <= 96 => c.toChar.toString
    case 161 => "World1"
    case 162 => "World2"
    case 256 => "Esc"
    case 257 => "Enter"
    case 258 => "Tab"
    case 259 => "Backspace"
    case 260 => "Insert"
    case 261 => "Delete"
    case 262 => "Right"
    case 263 => "Left"
    case 264 => "Down"
    case 265 => "Up"
    case 266 => "PageUp"
    case 267 => "PageDown"
    case 268 => "Home"
    case 269 => "End"
    case 280 => "CapsLock"
    case 281 => "ScrollLock"
    case 282 => "NumLock"
    case 283 => "PrintScreen"
    case 284 => "Pause"
    case c if c >= 290 && c <= 314 => s"F${c - 289}"
    case c if c >= 320 && c <= 329 => s"Keypad${c - 320}"
    case 330 => "Keypad."
    case 331 => "Keypad/"
    case 332 => "Keypad*"
    case 333 => "Keypad-"
    case 334 => "Keypad+"
    case 335 => "KeypadEnter"
    case 336 => "Keypad="
    case 340 => "LeftShift"
    case 341 => "LeftCtrl"
    case 342 => "LeftAlt"
    case 343 => "LeftSuper"
    case 344 => "RightShift"
    case 345 => "RightCtrl"
    case 346 => "RightAlt"
    case 347 => "RightSuper"
    case 348 => "Menu"
    case c => s"key $c"
  }

  def mouseName(code: Int): String = code match {
    case 0 => "left"; case 1 => "right"; case 2 => "middle"; case c => s"button ${c + 1}"
  }

  /** A GLFW gamepad button. */
  def padName(code: Int): String = code match {
    case 0 => "A"; case 1 => "B"; case 2 => "X"; case 3 => "Y"; case 4 => "LB"; case 5 => "RB"; case 6 => "Back"
    case 7 => "Start"; case 8 => "Guide"; case 9 => "LeftStick"; case 10 => "RightStick"; case 11 => "DpadUp"
    case 12 => "DpadRight"; case 13 => "DpadDown"; case 14 => "DpadLeft"; case c => s"button $c"
  }

  def modNames(mods: Int): Seq[String] =
    Seq(0x01 -> "Shift", 0x02 -> "Ctrl", 0x04 -> "Alt", 0x08 -> "Super", 0x10 -> "CapsLock", 0x20 -> "NumLock")
      .collect { case (bit, n) if (mods & bit) != 0 => n }

  def inputAction(a: Int): String = a match { case 0 => "up"; case 1 => "down"; case 2 => "repeat"; case x => s"action $x" }

  /** ("key", "Q down (Shift)") and the like: the timeline's tag and text for an input. */
  def describeInput(v: InputView): (String, String) = {
    val mods = modNames(v.mods)
    val withMods = if (mods.isEmpty) "" else mods.mkString(" (", "+", ")")
    v.device match {
      case InputDevice.Keyboard =>
        if (v.code == -1) ("key", keyName(-1)) else ("key", s"${keyName(v.code)} ${inputAction(v.action)}$withMods")
      case InputDevice.Mouse => ("mouse", s"${mouseName(v.code)} ${inputAction(v.action)}$withMods")
      case InputDevice.Controller => ("pad", s"${padName(v.code)} ${inputAction(v.action)}")
      case d => ("input", s"device $d code ${v.code} ${inputAction(v.action)}$withMods")
    }
  }

  // ── What the client noticed ───────────────────────────────────────────

  def noticed(atMicros: Long, p: Array[Byte]): Option[NoticedView] =
    if (p.length < NoticedRecord.Size) None
    else Some(NoticedView(atMicros, p(NoticedRecord.Kind) & 0xFF, Recording.int(p, NoticedRecord.A), Recording.int(p, NoticedRecord.B),
      if (p.length > NoticedRecord.Size) Clean.line(new String(p, NoticedRecord.Size, p.length - NoticedRecord.Size,
        java.nio.charset.StandardCharsets.UTF_8)) else ""))

  /** "server-quiet: the server said nothing for 2.1s (from 02:29.400)": the timeline's words for one. */
  def describeNoticed(v: NoticedView): String =
    s"${v.name}: ${v.text}" + (if (v.kind == NoticedKind.ServerQuiet) s" (from ${time(v.fromMicros)})" else "")

  // ── Frames and states ─────────────────────────────────────────────────

  def frame(p: Array[Byte]): Option[FrameView] =
    if (p.length < FrameRecord.Size) None
    else Some(FrameView(Recording.float(p, FrameRecord.DeltaMs), Recording.float(p, FrameRecord.WorkMs),
      Recording.float(p, FrameRecord.CellX), Recording.float(p, FrameRecord.CellY),
      Recording.float(p, FrameRecord.DrawnX), Recording.float(p, FrameRecord.DrawnY),
      Recording.float(p, FrameRecord.AimX), Recording.float(p, FrameRecord.AimY),
      Recording.int(p, FrameRecord.HeldKeys), Recording.ushort(p, FrameRecord.Flags),
      p(FrameRecord.Quality) & 0xFF, p(FrameRecord.Aiming).toInt))

  def state(p: Array[Byte]): Option[StateView] =
    if (p.length < StateRecord.Size) None
    else Some(StateView(Recording.short(p, StateRecord.Health), Recording.short(p, StateRecord.MaxHealth),
      Recording.int(p, StateRecord.Effects), Recording.int(p, StateRecord.QLeftMs), Recording.int(p, StateRecord.ELeftMs),
      Recording.int(p, StateRecord.RLeftMs), p(StateRecord.Charge) & 0xFF, p(StateRecord.Items) & 0xFF,
      Recording.short(p, StateRecord.Kills), Recording.short(p, StateRecord.Deaths), Recording.short(p, StateRecord.SecondsLeft),
      Recording.short(p, StateRecord.RoundTripMs), Recording.ushort(p, StateRecord.Projectiles), Recording.ushort(p, StateRecord.Players),
      Recording.int(p, StateRecord.ServerMoves), Recording.int(p, StateRecord.Knockbacks), Recording.ushort(p, StateRecord.Queued)))

  def keysHeld(keys: Int): String = flagNames(keys, Recording.Keys.names).mkString("+")

  def frameFlags(flags: Int): String = flagNames(flags, Recording.FrameFlags.names).mkString("+")

  def effects(effects: Int): Seq[String] = flagNames(effects, Recording.StateEffects.names)

  /** A whole state, for the first one a timeline shows. */
  def describeState(s: StateView): String = {
    val fx = effects(s.effects)
    s"health ${s.health}/${s.maxHealth}${if (fx.isEmpty) "" else fx.mkString(" [", ",", "]")} Q ${cooldown(s.qLeftMs)} " +
      s"E ${cooldown(s.eLeftMs)} R ${cooldown(s.rLeftMs)} kills ${s.kills} deaths ${s.deaths} items ${s.items} " +
      s"round trip ${if (s.roundTripMs < 0) "?" else s"${s.roundTripMs}ms"} ${s.players} others ${s.projectiles} projectiles " +
      s"server moves ${s.serverMoves} knockbacks ${s.knockbacks} ${s.secondsLeft}s left"
  }

  private def cooldown(ms: Int): String = if (ms <= 0) "ready" else s"${num(ms / 1000.0, 1)}s"

  /** What changed from one state to the next, or "" when nothing worth a line did. */
  def stateChanges(a: StateView, b: StateView): String = {
    val parts = Vector.newBuilder[String]
    if (a.health != b.health || a.maxHealth != b.maxHealth) parts += s"health ${a.health} -> ${b.health}" + (if (a.maxHealth != b.maxHealth) s" (of ${b.maxHealth})" else "")
    val was = effects(a.effects).toSet
    val now = effects(b.effects).toSet
    effects(b.effects).filterNot(was).foreach(e => parts += s"+$e")
    effects(a.effects).filterNot(now).foreach(e => parts += s"-$e")
    if (a.kills != b.kills) parts += s"kills ${a.kills} -> ${b.kills}"
    if (a.deaths != b.deaths) parts += s"deaths ${a.deaths} -> ${b.deaths}"
    if (a.qLeftMs > 0 && b.qLeftMs <= 0) parts += "Q ready"
    if (a.eLeftMs > 0 && b.eLeftMs <= 0) parts += "E ready"
    if (a.rLeftMs > 0 && b.rLeftMs <= 0) parts += "R ready"
    if (a.qLeftMs <= 0 && b.qLeftMs > 0) parts += s"Q cooling ${cooldown(b.qLeftMs)}"
    if (a.eLeftMs <= 0 && b.eLeftMs > 0) parts += s"E cooling ${cooldown(b.eLeftMs)}"
    if (a.rLeftMs <= 0 && b.rLeftMs > 0) parts += s"R cooling ${cooldown(b.rLeftMs)}"
    if (a.items != b.items) parts += s"items ${a.items} -> ${b.items}"
    if (a.serverMoves != b.serverMoves) parts += s"server moved us (${b.serverMoves - a.serverMoves})"
    if (a.knockbacks != b.knockbacks) parts += s"knocked back (${b.knockbacks - a.knockbacks})"
    if (a.players != b.players) parts += s"others ${a.players} -> ${b.players}"
    parts.result().mkString(", ")
  }
}
