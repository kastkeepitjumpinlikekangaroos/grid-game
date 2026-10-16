package com.gridgame.common.protocol

import java.util.UUID

object CooldownResetAction {
  /** Client -> server: R was pressed. Every one of the player's ability cooldowns is over, if the
    * reset's own has run (design/characters.md, *R resets the cooldowns*). */
  val REQUEST: Byte = 0
  /** Server -> the player whose REQUEST it refused, so their client puts its cooldowns back. */
  val REJECTED: Byte = 1
}

/**
 * R, the reset of a player's ability cooldowns: asked for by their client and refused, if it is, by
 * the server. A reset the server takes says nothing back: the client had its abilities ready from
 * the press.
 *
 * {{{
 * [21]     action (CooldownResetAction)
 * [22-25]  REJECTED: how long the reset's own cooldown still has to run on the server, in ms; 0 when
 *          it was refused for anything else (dead, held, phased, the opening's ceasefire)
 * [26-63]  zero
 * }}}
 */
class CooldownResetPacket(
    sequenceNumber: Int,
    playerId: UUID,
    timestamp: Int,
    val action: Byte,
    val msLeft: Int = 0
) extends Packet(PacketType.COOLDOWN_RESET, sequenceNumber, playerId, timestamp) {

  def this(sequenceNumber: Int, playerId: UUID, action: Byte, msLeft: Int) =
    this(sequenceNumber, playerId, Packet.getCurrentTimestamp, action, msLeft)

  def getAction: Byte = action

  /** On a REJECTED, how long the server's own reset cooldown still has to run (ms), or 0. */
  def getMsLeft: Int = msLeft

  override def serialize(): Array[Byte] = {
    val buffer = SerializeUtil.acquireBuffer()

    // [0] Packet Type
    buffer.put(packetType.id)

    // [1-4] Sequence Number
    buffer.putInt(sequenceNumber)

    // [5-20] Player ID (UUID)
    buffer.putLong(playerId.getMostSignificantBits)
    buffer.putLong(playerId.getLeastSignificantBits)

    // [21] Action
    buffer.put(action)

    // [22-25] The reset's own cooldown left on the server (REJECTED)
    buffer.putInt(msLeft)

    // [26-63] Reserved
    buffer.put(new Array[Byte](38))

    buffer.array().clone()
  }
}
