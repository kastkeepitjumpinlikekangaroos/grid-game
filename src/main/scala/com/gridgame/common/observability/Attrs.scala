package com.gridgame.common.observability

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import com.gridgame.common.protocol.PacketType

import java.util.concurrent.ConcurrentHashMap

/**
 * Pre-built AttributeKey constants and small caches for combos we hit in hot paths.
 *
 * Why this exists:
 *   - Allocating a fresh `Attributes` per packet/event would dominate CPU in tick loops.
 *   - `Attributes` is immutable and hash-equal — we cache one per known label combination
 *     and reuse it forever.
 *   - Keys are interned by OTel as well, but creating them once still avoids string ops.
 */
object Attrs {

  // Common keys
  val Type: AttributeKey[String] = AttributeKey.stringKey("type")
  val Transport: AttributeKey[String] = AttributeKey.stringKey("transport")
  val Direction: AttributeKey[String] = AttributeKey.stringKey("direction")
  val Reason: AttributeKey[String] = AttributeKey.stringKey("reason")
  val Outcome: AttributeKey[String] = AttributeKey.stringKey("outcome")
  val Action: AttributeKey[String] = AttributeKey.stringKey("action")
  val Mode: AttributeKey[String] = AttributeKey.stringKey("mode")
  val MatchType: AttributeKey[String] = AttributeKey.stringKey("match_type")
  val Kind: AttributeKey[String] = AttributeKey.stringKey("kind")
  val Op: AttributeKey[String] = AttributeKey.stringKey("op")
  val Scope: AttributeKey[String] = AttributeKey.stringKey("scope")
  val Phase: AttributeKey[String] = AttributeKey.stringKey("phase")
  val Cause: AttributeKey[String] = AttributeKey.stringKey("cause")
  val Status: AttributeKey[String] = AttributeKey.stringKey("status")
  val Character: AttributeKey[String] = AttributeKey.stringKey("character")
  val KillerCharacter: AttributeKey[String] = AttributeKey.stringKey("killer_character")
  val VictimCharacter: AttributeKey[String] = AttributeKey.stringKey("victim_character")
  val ProjectileType: AttributeKey[String] = AttributeKey.stringKey("projectile_type")
  val ItemType: AttributeKey[String] = AttributeKey.stringKey("item_type")
  val LobbyId: AttributeKey[java.lang.Long] = AttributeKey.longKey("lobby.id")
  val InstanceId: AttributeKey[java.lang.Long] = AttributeKey.longKey("instance.id")
  val PlayerId: AttributeKey[String] = AttributeKey.stringKey("player.id")
  val Map: AttributeKey[String] = AttributeKey.stringKey("map")

  // ---------- Pre-built attribute sets ----------

  /** Attributes(type=<packetType>, transport=tcp|udp) per packet type. */
  private val packetAttrCache = new ConcurrentHashMap[(Byte, Boolean), Attributes]()

  /** Returns cached Attributes with packet type + transport label. */
  def packet(pt: PacketType): Attributes = {
    packetAttrCache.computeIfAbsent((pt.id, pt.tcp), _ =>
      Attributes.of(
        Type, pt.toString,
        Transport, if (pt.tcp) "tcp" else "udp"
      )
    )
  }

  // Special "unknown" sentinel for cases where the type byte didn't deserialize
  val UnknownPacket: Attributes = Attributes.of(Type, "UNKNOWN", Transport, "tcp")
  val UnknownPacketUdp: Attributes = Attributes.of(Type, "UNKNOWN", Transport, "udp")

  // ---------- Drop reason attrs (no transport) ----------
  private def reason(label: String): Attributes = Attributes.of(Reason, label)
  val ReasonHmacFail: Attributes = reason("hmac_fail")
  val ReasonReplay: Attributes = reason("replay")
  val ReasonRateLimit: Attributes = reason("rate_limit")
  val ReasonUdpSpoof: Attributes = reason("udp_spoof")
  val ReasonExpired: Attributes = reason("expired")
  val ReasonMalformed: Attributes = reason("malformed")
  val ReasonNoToken: Attributes = reason("no_token")
  val ReasonUnauthenticated: Attributes = reason("unauthenticated")
  val ReasonTooShort: Attributes = reason("too_short")
  val ReasonUuidMismatch: Attributes = reason("uuid_mismatch")
  val ReasonTcpOnlyOverUdp: Attributes = reason("tcp_only_over_udp")

  // ---------- Rate-limit kinds ----------
  private def kind(label: String): Attributes = Attributes.of(Kind, label)
  val RlUdp: Attributes = kind("udp")
  val RlTcp: Attributes = kind("tcp")
  val RlChat: Attributes = kind("chat")
  val RlConnection: Attributes = kind("connection")
  val RlAuth: Attributes = kind("auth")
  val RlPreAuth: Attributes = kind("pre_auth")
  val RlMalformed: Attributes = kind("malformed_packets")

  // ---------- Validation failure kinds ----------
  val VfMovementSpeed: Attributes = kind("movement_speed")
  val VfMovementBounds: Attributes = kind("movement_bounds")
  val VfMovementWalkable: Attributes = kind("movement_walkable")
  val VfProjectileVelocity: Attributes = kind("projectile_velocity")
  val VfProjectilePosition: Attributes = kind("projectile_position")
  val VfProjectileFireRate: Attributes = kind("projectile_fire_rate")
  val VfProjectileCharge: Attributes = kind("projectile_charge")
  val VfCharacter: Attributes = kind("character")
  val VfWorldMissing: Attributes = kind("world_missing")

  // ---------- Auth outcomes ----------
  val AuthLoginSuccess: Attributes = Attributes.of(Action, "login", Outcome, "success")
  val AuthLoginFail: Attributes = Attributes.of(Action, "login", Outcome, "fail")
  val AuthLoginRateLimited: Attributes = Attributes.of(Action, "login", Outcome, "rate_limited")
  val AuthSignupSuccess: Attributes = Attributes.of(Action, "signup", Outcome, "success")
  val AuthSignupFail: Attributes = Attributes.of(Action, "signup", Outcome, "fail")
  val AuthSignupRateLimited: Attributes = Attributes.of(Action, "signup", Outcome, "rate_limited")

  // ---------- Connection events ----------
  val ConnTcpOpen: Attributes = Attributes.of(Transport, "tcp", Action, "open")
  val ConnTcpClose: Attributes = Attributes.of(Transport, "tcp", Action, "close")
  val ConnUdpAccept: Attributes = Attributes.of(Transport, "udp", Action, "accept")
  val ConnTcpRejectedRateLimit: Attributes = Attributes.of(Transport, "tcp", Reason, "rate_limit")

  // ---------- Chat scopes ----------
  val ChatLobby: Attributes = Attributes.of(Scope, "lobby")
  val ChatGame: Attributes = Attributes.of(Scope, "game")
  val ChatTeam: Attributes = Attributes.of(Scope, "team")
  val ChatRateLimited: Attributes = Attributes.of(Scope, "rate_limited")

  // ---------- Direction / bandwidth ----------
  val DirIn: Attributes = Attributes.of(Direction, "in")
  val DirOut: Attributes = Attributes.of(Direction, "out")

  // ---------- Lobby actions ----------
  private val lobbyActionCache = new ConcurrentHashMap[Byte, Attributes]()
  def lobbyAction(action: Byte): Attributes =
    lobbyActionCache.computeIfAbsent(action, a => Attributes.of(Action, lobbyActionName(a)))

  private def lobbyActionName(b: Byte): String = b match {
    case 0 => "create" case 1 => "join" case 2 => "leave" case 3 => "start"
    case 4 => "list_request" case 5 => "list_entry" case 6 => "list_end"
    case 7 => "joined" case 8 => "player_joined" case 9 => "player_left"
    case 10 => "game_starting" case 11 => "lobby_closed" case 12 => "config_update"
    case 13 => "character_select" case 14 => "add_bot" case 15 => "remove_bot"
    case 16 => "practice_start" case _ => s"unknown_$b"
  }

  // ---------- Modes ----------
  val ModeFfa: Attributes = Attributes.of(Mode, "ffa")
  val ModeTeams: Attributes = Attributes.of(Mode, "teams")
  val ModeDuel: Attributes = Attributes.of(Mode, "duel")
  def modeOf(byte: Byte): Attributes = byte match {
    case 0 => ModeFfa
    case 1 => ModeTeams
    case _ => Attributes.of(Mode, s"mode_$byte")
  }

  val MatchCasual: Attributes = Attributes.of(MatchType, "casual")
  val MatchRanked: Attributes = Attributes.of(MatchType, "ranked")
  val MatchPractice: Attributes = Attributes.of(MatchType, "practice")
  def matchTypeOf(byte: Byte): Attributes = byte match {
    case 0 => MatchCasual
    case 1 => MatchRanked
    case 2 => MatchPractice
    case _ => Attributes.of(MatchType, s"type_$byte")
  }

  // ---------- DB ops ----------
  private val dbCache = new ConcurrentHashMap[String, Attributes]()
  def dbOp(op: String): Attributes =
    dbCache.computeIfAbsent(op, o => Attributes.of(Op, o))

  // ---------- Tick phases ----------
  def tickPhase(label: String): Attributes = Attributes.of(Phase, label)

  // ---------- Character / projectile / item caches ----------
  private val characterCache = new ConcurrentHashMap[Byte, Attributes]()
  def character(id: Byte): Attributes =
    characterCache.computeIfAbsent(id, b => Attributes.of(Character, characterName(b)))

  private val projectileCache = new ConcurrentHashMap[Byte, Attributes]()
  def projectileType(id: Byte): Attributes =
    projectileCache.computeIfAbsent(id, b => Attributes.of(ProjectileType, projectileTypeName(b)))

  private val itemCache = new ConcurrentHashMap[Byte, Attributes]()
  def itemTypeAttrs(id: Byte): Attributes =
    itemCache.computeIfAbsent(id, b => Attributes.of(ItemType, itemTypeName(b)))

  private val killCache = new ConcurrentHashMap[(Byte, Byte, Byte), Attributes]()
  def killCombo(killerCharId: Byte, victimCharId: Byte, projType: Byte): Attributes = {
    killCache.computeIfAbsent((killerCharId, victimCharId, projType), _ =>
      Attributes.of(
        KillerCharacter, characterName(killerCharId),
        VictimCharacter, characterName(victimCharId),
        ProjectileType, projectileTypeName(projType)
      )
    )
  }

  // ---------- Item action breakdown ----------
  val ItemSpawn: Attributes = Attributes.of(Action, "spawn")
  val ItemPickup: Attributes = Attributes.of(Action, "pickup")
  val ItemUse: Attributes = Attributes.of(Action, "use")

  // ---------- Cause of death ----------
  val CauseProjectile: Attributes = Attributes.of(Cause, "projectile")
  val CauseAoe: Attributes = Attributes.of(Cause, "aoe")
  val CauseBurn: Attributes = Attributes.of(Cause, "burn")

  // ---------- Bot action kinds ----------
  val BotMove: Attributes = Attributes.of(Action, "move")
  val BotStrafe: Attributes = Attributes.of(Action, "strafe")
  val BotWander: Attributes = Attributes.of(Action, "wander")
  val BotPickup: Attributes = Attributes.of(Action, "pickup_item")
  val BotUseItem: Attributes = Attributes.of(Action, "use_item")
  val BotAbility: Attributes = Attributes.of(Action, "use_ability")
  val BotPlaceFence: Attributes = Attributes.of(Action, "place_fence")

  // ---------- Empty ----------
  val Empty: Attributes = Attributes.empty()

  // ----- name lookups (cheap; called once per (key) and cached) -----

  private def characterName(id: Byte): String = {
    // Best-effort lookup; falls back to numeric id if CharacterDef registry isn't ready.
    try {
      val cd = com.gridgame.common.model.CharacterDef.get(id)
      if (cd != null && cd.displayName != null) cd.displayName else s"char_$id"
    } catch { case _: Throwable => s"char_$id" }
  }

  private def projectileTypeName(id: Byte): String = {
    try {
      val pd = com.gridgame.common.model.ProjectileDef.get(id)
      if (pd != null && pd.name != null) pd.name else s"proj_$id"
    } catch { case _: Throwable => s"proj_$id" }
  }

  private def itemTypeName(id: Byte): String = id match {
    case 0 => "gem"
    case 1 => "heart"
    case 2 => "star"
    case 3 => "shield"
    case 4 => "fence"
    case _ => s"item_$id"
  }
}
