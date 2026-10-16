package com.gridgame.common.protocol

import com.gridgame.common.Constants
import com.gridgame.common.model.Position

import java.util.UUID

class PlayerUpdatePacket(
    sequenceNumber: Int,
    playerId: UUID,
    timestamp: Int,
    val position: Position,
    val colorRGB: Int,
    val health: Int = 100,
    val chargeLevel: Int = 0,
    val effectFlags: Int = 0,
    val characterId: Byte = 0,
    val teamId: Byte = 0,
    // Server -> the player: how many times the server has moved them (Player.getServerMoves).
    // Client -> server: the count the client had seen when it sent this position.
    val serverMoves: Int = 0,
    // Status flags that didn't fit in the first byte: bit 0 stunned, bit 1 poisoned,
    // bit 2 barrier up. Defaulted so the call sites that predate them still compile.
    val effectFlags2: Int = 0,
    // Where the player is aiming, as angle / 2pi * 65536 (see aimAngleRadians).
    val aimAngle: Int = 0,
    // The speed multiplier while slowed, 0-100, so the client steps at the rate the slow that
    // landed actually calls for instead of a flat half.
    val slowPercent: Int = 0,
    // Server -> the rest of the match: how long this player's raised barrier has left, in ms.
    // Everyone draws it on that timer rather than on a lease renewed by whichever updates
    // happen to arrive, so a hitch in the holder's client can't take it off other screens.
    // 0 when none is up, and on the client's own updates: how long one lasts is the server's.
    val barrierMs: Int = 0,
    // Server -> everyone: how many of the server's moves of this player were knockbacks
    // (Player.getKnockbacks), so a client can draw the move a new one made as a shove rather than
    // a teleport. 0 on the client's own updates.
    val knockbacks: Int = 0,
    // Server -> everyone: how long this player's speed boost has left, in ms (0 when none, and on
    // the client's own updates). The boosted player's own client walks by it: with the flag alone
    // it gave itself a second of boost per update that carried it, and a player at full health,
    // who hears nothing between casts, walked a 4s boost for one
    val speedBoostMs: Int = 0,
    // Server -> everyone: how long this player's freeze or stun (the hold), root and slow have
    // left, in ms (0 when none, and on the client's own updates), carried in 20ms steps
    // (PlayerUpdatePacket.encodeEffectMs). The held player's own client is held for that long: with
    // the flags alone it held itself until whichever update next came without them, which at full
    // health nothing sends before its fallback (a 2s snare held for 3), and below it the next
    // regeneration tick, up to most of a second after a 400ms stun had ended.
    val heldMs: Int = 0,
    val rootedMs: Int = 0,
    val slowedMs: Int = 0
) extends Packet(PacketType.PLAYER_UPDATE, sequenceNumber, playerId, timestamp) {

  def this(sequenceNumber: Int, playerId: UUID, position: Position, colorRGB: Int) = {
    this(sequenceNumber, playerId, Packet.getCurrentTimestamp, position, colorRGB, 100, 0, 0, 0.toByte, 0.toByte)
  }

  def this(sequenceNumber: Int, playerId: UUID, position: Position, colorRGB: Int, health: Int) = {
    this(sequenceNumber, playerId, Packet.getCurrentTimestamp, position, colorRGB, health, 0, 0, 0.toByte, 0.toByte)
  }

  def this(sequenceNumber: Int, playerId: UUID, position: Position, colorRGB: Int, health: Int, chargeLevel: Int) = {
    this(sequenceNumber, playerId, Packet.getCurrentTimestamp, position, colorRGB, health, chargeLevel, 0, 0.toByte, 0.toByte)
  }

  def this(sequenceNumber: Int, playerId: UUID, position: Position, colorRGB: Int, health: Int, chargeLevel: Int, effectFlags: Int) = {
    this(sequenceNumber, playerId, Packet.getCurrentTimestamp, position, colorRGB, health, chargeLevel, effectFlags, 0.toByte, 0.toByte)
  }

  def this(sequenceNumber: Int, playerId: UUID, position: Position, colorRGB: Int, health: Int, chargeLevel: Int, effectFlags: Int, characterId: Byte) = {
    this(sequenceNumber, playerId, Packet.getCurrentTimestamp, position, colorRGB, health, chargeLevel, effectFlags, characterId, 0.toByte)
  }

  def getPosition: Position = position

  def getColorRGB: Int = colorRGB

  def getHealth: Int = health

  def getChargeLevel: Int = chargeLevel

  def getEffectFlags: Int = effectFlags

  def getCharacterId: Byte = characterId

  def getTeamId: Byte = teamId

  def getServerMoves: Int = serverMoves

  def getEffectFlags2: Int = effectFlags2

  def getAimAngle: Int = aimAngle

  /** The aim angle back in radians, in [0, 2pi). */
  def aimAngleRadians: Double = PlayerUpdatePacket.decodeAimAngle(aimAngle)

  def getSlowPercent: Int = slowPercent

  /** How long the raised barrier has left, in ms (0: none, or the sender doesn't say). */
  def getBarrierMs: Int = barrierMs

  /** How many times the server has knocked the player back (0 from a client). */
  def getKnockbacks: Int = knockbacks

  /** How long the player's speed boost has left, in ms (0: none, or the sender doesn't say). */
  def getSpeedBoostMs: Int = speedBoostMs

  /** How long the player's freeze or stun has left, in ms (0: none, or the sender doesn't say). */
  def getHeldMs: Int = heldMs

  /** How long the player's root has left, in ms (0: none, or the sender doesn't say). */
  def getRootedMs: Int = rootedMs

  /** How long the player's slow has left, in ms (0: none, or the sender doesn't say). */
  def getSlowedMs: Int = slowedMs

  override def serialize(): Array[Byte] = {
    val buffer = SerializeUtil.acquireBuffer()

    // [0] Packet Type
    buffer.put(packetType.id)

    // [1-4] Sequence Number
    buffer.putInt(sequenceNumber)

    // [5-20] Player ID (UUID = 2 longs)
    buffer.putLong(playerId.getMostSignificantBits)
    buffer.putLong(playerId.getLeastSignificantBits)

    // [21-24] X Position
    buffer.putInt(position.getX)

    // [25-28] Y Position
    buffer.putInt(position.getY)

    // [29-32] Color RGB (4 bytes for ARGB)
    buffer.putInt(colorRGB)

    // [33-36] Timestamp
    buffer.putInt(timestamp)

    // [37-40] Health
    buffer.putInt(health)

    // [41] Charge level (0-100)
    buffer.put(chargeLevel.toByte)

    // [42] Effect flags bitfield (bit 0: shield, bit 1: gem boost)
    buffer.put(effectFlags.toByte)

    // [43] Character ID
    buffer.put(characterId)

    // [44] Team ID
    buffer.put(teamId)

    // [45-48] Server moves
    buffer.putInt(serverMoves)

    // [49] Second effect flags byte (bit 0: stunned, bit 1: poisoned, bit 2: barrier up)
    buffer.put(effectFlags2.toByte)

    // [50-51] Aim angle, angle / 2pi * 65536
    buffer.putShort((aimAngle & 0xFFFF).toShort)

    // [52] Slow multiplier as a percentage (0-100)
    buffer.put(slowPercent.toByte)

    // [53-54] How long a raised barrier has left, in ms (capped at 65535)
    buffer.putShort((Math.max(0, Math.min(65535, barrierMs)) & 0xFFFF).toShort)

    // [55-58] Knockbacks
    buffer.putInt(knockbacks)

    // [59-60] How long a speed boost has left, in ms (capped at 65535)
    buffer.putShort((Math.max(0, Math.min(65535, speedBoostMs)) & 0xFFFF).toShort)

    // [61] How long a freeze or stun has left, [62] a root, [63] a slow: 20ms steps, rounded up
    buffer.put(PlayerUpdatePacket.encodeEffectMs(heldMs).toByte)
    buffer.put(PlayerUpdatePacket.encodeEffectMs(rootedMs).toByte)
    buffer.put(PlayerUpdatePacket.encodeEffectMs(slowedMs).toByte)

    buffer.array().clone()
  }

  override def toString: String = {
    s"PlayerUpdatePacket{seq=$sequenceNumber, playerId=${playerId.toString.substring(0, 8)}, position=$position, color=0x${colorRGB.toHexString.toUpperCase}, health=$health, charge=$chargeLevel, effects=$effectFlags/$effectFlags2}"
  }
}

object PlayerUpdatePacket {
  /** An angle in radians as the 16-bit value byte [50-51] carries. */
  def encodeAimAngle(radians: Double): Int = {
    val turns = radians / (2 * Math.PI)
    val wrapped = turns - Math.floor(turns)
    (Math.round(wrapped * 65536.0).toInt) & 0xFFFF
  }

  /** The inverse of [[encodeAimAngle]]: radians in [0, 2pi). */
  def decodeAimAngle(raw: Int): Double = (raw & 0xFFFF) * (2 * Math.PI) / 65536.0

  /** The steps bytes [61-63] count a hold's, a root's and a slow's time left in, in ms. */
  val EffectStepMs = 20

  /** The longest time left those bytes can say: 255 steps, 5.1s. A longer effect is said to have
    * that long, and the updates about the player after it say what is left by then. */
  val MaxEffectMs: Int = 255 * EffectStepMs

  /** A hold's, a root's or a slow's time left as one of bytes [61-63]: in whole steps, rounded up,
    * so the client it reaches never lets its player go before the server does. 0 is none. */
  def encodeEffectMs(ms: Int): Int =
    if (ms <= 0) 0 else (Math.min(ms, MaxEffectMs) + EffectStepMs - 1) / EffectStepMs

  /** The inverse of [[encodeEffectMs]], in ms. */
  def decodeEffectMs(raw: Int): Int = (raw & 0xFF) * EffectStepMs
}
