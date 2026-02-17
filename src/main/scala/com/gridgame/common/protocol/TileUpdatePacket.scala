package com.gridgame.common.protocol

import com.gridgame.common.Constants

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

class TileUpdatePacket(
    sequenceNumber: Int,
    playerId: UUID,
    timestamp: Int,
    val tileX: Int,
    val tileY: Int,
    val tileId: Int
) extends Packet(PacketType.TILE_UPDATE, sequenceNumber, playerId, timestamp) {

  def this(sequenceNumber: Int, playerId: UUID, tileX: Int, tileY: Int, tileId: Int) = {
    this(sequenceNumber, playerId, Packet.getCurrentTimestamp, tileX, tileY, tileId)
  }

  def getTileX: Int = tileX

  def getTileY: Int = tileY

  def getTileId: Int = tileId

  override def serialize(): Array[Byte] = {
    val buffer = ByteBuffer.allocate(Constants.PACKET_PAYLOAD_SIZE)
    buffer.order(ByteOrder.BIG_ENDIAN)

    // [0] Packet Type
    buffer.put(packetType.id)

    // [1-4] Sequence Number
    buffer.putInt(sequenceNumber)

    // [5-20] Player UUID
    buffer.putLong(playerId.getMostSignificantBits)
    buffer.putLong(playerId.getLeastSignificantBits)

    // [21-24] X Position (reuse standard x slot for tile x)
    buffer.putInt(tileX)

    // [25-28] Y Position
    buffer.putInt(tileY)

    // [29-32] Color RGB (unused, zero)
    buffer.putInt(0)

    // [33-36] Timestamp
    buffer.putInt(timestamp)

    // [37-40] Tile ID
    buffer.putInt(tileId)

    // [41-63] Reserved (23 bytes)
    buffer.put(new Array[Byte](23))

    buffer.array()
  }

  override def toString: String = {
    s"TileUpdatePacket{seq=$sequenceNumber, pos=($tileX, $tileY), tileId=$tileId}"
  }
}
