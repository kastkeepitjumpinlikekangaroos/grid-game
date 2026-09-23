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
    val barrierMs: Int = 0
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

    // [55-63] Reserved (9 bytes) - fill with zeros
    buffer.put(new Array[Byte](9))

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
}
