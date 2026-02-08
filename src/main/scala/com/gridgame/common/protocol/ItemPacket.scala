package com.gridgame.common.protocol

import com.gridgame.common.Constants
import com.gridgame.common.model.ItemType

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

object ItemAction {
  val SPAWN: Byte = 0
  val PICKUP: Byte = 1
  val INVENTORY: Byte = 2
}

class ItemPacket(
    sequenceNumber: Int,
    playerId: UUID,
    timestamp: Int,
    val x: Int,
    val y: Int,
    val itemTypeId: Byte,
    val itemId: Int,
    val action: Byte
) extends Packet(PacketType.ITEM_UPDATE, sequenceNumber, playerId, timestamp) {

  def this(sequenceNumber: Int, playerId: UUID, x: Int, y: Int, itemTypeId: Byte,
           itemId: Int, action: Byte) = {
    this(sequenceNumber, playerId, Packet.getCurrentTimestamp, x, y, itemTypeId,
         itemId, action)
  }

  def getX: Int = x

  def getY: Int = y

  def getItemTypeId: Byte = itemTypeId

  def getItemType: ItemType = ItemType.fromId(itemTypeId)

  def getItemId: Int = itemId

  def getAction: Byte = action

  override def serialize(): Array[Byte] = {
    val buffer = ByteBuffer.allocate(Constants.PACKET_SIZE)
    buffer.order(ByteOrder.BIG_ENDIAN)

    // [0] Packet Type
    buffer.put(packetType.id)

    // [1-4] Sequence Number
    buffer.putInt(sequenceNumber)

    // [5-20] Player UUID
    buffer.putLong(playerId.getMostSignificantBits)
    buffer.putLong(playerId.getLeastSignificantBits)

    // [21-24] X (int)
    buffer.putInt(x)

    // [25-28] Y (int)
    buffer.putInt(y)

    // [29-32] Color RGB (derived from item type, for protocol compatibility)
    buffer.putInt(getItemType.colorRGB)

    // [33-36] Timestamp
    buffer.putInt(timestamp)

    // [37-40] Item ID
    buffer.putInt(itemId)

    // [41] Action
    buffer.put(action)

    // [42] Item Type ID
    buffer.put(itemTypeId)

    // [43-63] Reserved (21 bytes)
    buffer.put(new Array[Byte](21))

    buffer.array()
  }

  override def toString: String = {
    val actionStr = action match {
      case ItemAction.SPAWN => "SPAWN"
      case ItemAction.PICKUP => "PICKUP"
      case ItemAction.INVENTORY => "INVENTORY"
      case _ => "UNKNOWN"
    }
    s"ItemPacket{seq=$sequenceNumber, player=${playerId.toString.substring(0, 8)}, type=${getItemType.name}, pos=($x, $y), itemId=$itemId, action=$actionStr}"
  }
}
