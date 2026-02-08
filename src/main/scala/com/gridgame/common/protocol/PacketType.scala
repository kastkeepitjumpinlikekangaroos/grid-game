package com.gridgame.common.protocol

sealed abstract class PacketType(val id: Byte)

object PacketType {
  case object PLAYER_JOIN extends PacketType(0x01.toByte)
  case object PLAYER_UPDATE extends PacketType(0x02.toByte)
  case object PLAYER_LEAVE extends PacketType(0x03.toByte)
  case object WORLD_INFO extends PacketType(0x04.toByte)
  case object HEARTBEAT extends PacketType(0x05.toByte)
  case object PROJECTILE_UPDATE extends PacketType(0x06.toByte)
  case object ITEM_UPDATE extends PacketType(0x07.toByte)

  private val values: Array[PacketType] = Array(PLAYER_JOIN, PLAYER_UPDATE, PLAYER_LEAVE, WORLD_INFO, HEARTBEAT, PROJECTILE_UPDATE, ITEM_UPDATE)

  def fromId(id: Byte): PacketType = {
    values.find(_.id == id).getOrElse(
      throw new IllegalArgumentException(s"Unknown packet type ID: $id")
    )
  }
}
