package com.gridgame.common.protocol

import com.gridgame.common.model.TrapDef

import java.util.UUID

object TrapAction {
  /** Client -> server: put one of my traps on (x, y). Carries the [[AttackSlot]] that cast it. */
  val PLACE: Byte = 0
  /** Server -> everyone: this trap is on the ground here. */
  val SPAWN: Byte = 1
  /** Server -> everyone: it went off under the victim, and is gone. */
  val TRIGGER: Byte = 2
  /** Server -> everyone: it is gone without going off (it ran out, its owner left, or a newer
    * one of theirs pushed it off). */
  val REMOVE: Byte = 3
  /** Server -> the player whose PLACE it refused, so their client gives the cooldown back. */
  val REJECTED: Byte = 4
}

/**
 * A trap being placed, spawned, sprung or taken away.
 *
 * Laid out like the other position-carrying packets (x, y, colour and timestamp at [21-36]), so
 * it decodes through [[PacketSerializer]]'s common path:
 *
 * {{{
 * [21-24] x        [37-40] trap id     [43] team id
 * [25-28] y        [41] action         [44] attack slot (PLACE, echoed on REJECTED)
 * [29-32] colour   [42] trap type      [45-60] victim id (TRIGGER)
 * [33-36] timestamp                    [61-63] reserved
 * }}}
 */
class TrapPacket(
    sequenceNumber: Int,
    playerId: UUID,
    timestamp: Int,
    val x: Int,
    val y: Int,
    val trapId: Int,
    val action: Byte,
    val trapType: Byte,
    val teamId: Byte = 0,
    val attackSlot: Int = 0,
    val victimId: UUID = null
) extends Packet(PacketType.TRAP_UPDATE, sequenceNumber, playerId, timestamp) {

  def this(sequenceNumber: Int, playerId: UUID, x: Int, y: Int, trapId: Int, action: Byte,
           trapType: Byte, teamId: Byte, attackSlot: Int, victimId: UUID) =
    this(sequenceNumber, playerId, Packet.getCurrentTimestamp, x, y, trapId, action, trapType,
         teamId, attackSlot, victimId)

  def getX: Int = x
  def getY: Int = y
  def getTrapId: Int = trapId
  def getAction: Byte = action
  def getTrapType: Byte = trapType
  def getTeamId: Byte = teamId

  /** On a PLACE, which of the caster's attacks threw it ([[AttackSlot]]). */
  def getAttackSlot: Int = attackSlot

  /** On a TRIGGER, who set it off. */
  def getVictimId: UUID = victimId

  def getColorRGB: Int = {
    val d = TrapDef.get(trapType)
    if (d != null) d.colorRGB else 0xFFFFFFFF
  }

  override def serialize(): Array[Byte] = {
    val buffer = SerializeUtil.acquireBuffer()

    // [0] Packet Type
    buffer.put(packetType.id)

    // [1-4] Sequence Number
    buffer.putInt(sequenceNumber)

    // [5-20] Player UUID (the trap's owner; on a PLACE, the caster)
    buffer.putLong(playerId.getMostSignificantBits)
    buffer.putLong(playerId.getLeastSignificantBits)

    // [21-24] X, [25-28] Y
    buffer.putInt(x)
    buffer.putInt(y)

    // [29-32] Colour (from the trap type, as an item packet takes its own)
    buffer.putInt(getColorRGB)

    // [33-36] Timestamp
    buffer.putInt(timestamp)

    // [37-40] Trap ID
    buffer.putInt(trapId)

    // [41] Action, [42] Trap type, [43] Team, [44] Attack slot
    buffer.put(action)
    buffer.put(trapType)
    buffer.put(teamId)
    buffer.put(attackSlot.toByte)

    // [45-60] Victim UUID
    if (victimId != null) {
      buffer.putLong(victimId.getMostSignificantBits)
      buffer.putLong(victimId.getLeastSignificantBits)
    } else {
      buffer.putLong(0L)
      buffer.putLong(0L)
    }

    // [61-63] Reserved
    buffer.put(new Array[Byte](3))

    buffer.array().clone()
  }

  override def toString: String = {
    val actionStr = action match {
      case TrapAction.PLACE => "PLACE"
      case TrapAction.SPAWN => "SPAWN"
      case TrapAction.TRIGGER => "TRIGGER"
      case TrapAction.REMOVE => "REMOVE"
      case TrapAction.REJECTED => "REJECTED"
      case _ => "UNKNOWN"
    }
    s"TrapPacket{seq=$sequenceNumber, owner=${playerId.toString.substring(0, 8)}, " +
      s"type=$trapType, pos=($x, $y), trapId=$trapId, action=$actionStr}"
  }
}
