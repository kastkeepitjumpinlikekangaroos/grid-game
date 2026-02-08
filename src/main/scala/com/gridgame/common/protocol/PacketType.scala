package com.gridgame.common.protocol

sealed abstract class PacketType(val id: Byte, val tcp: Boolean)

object PacketType {
  case object PLAYER_JOIN extends PacketType(0x01.toByte, true)
  case object PLAYER_UPDATE extends PacketType(0x02.toByte, false)
  case object PLAYER_LEAVE extends PacketType(0x03.toByte, true)
  case object WORLD_INFO extends PacketType(0x04.toByte, true)
  case object HEARTBEAT extends PacketType(0x05.toByte, false)
  case object PROJECTILE_UPDATE extends PacketType(0x06.toByte, false)
  case object ITEM_UPDATE extends PacketType(0x07.toByte, true)
  case object TILE_UPDATE extends PacketType(0x08.toByte, true)

  private val values: Array[PacketType] = Array(PLAYER_JOIN, PLAYER_UPDATE, PLAYER_LEAVE, WORLD_INFO, HEARTBEAT, PROJECTILE_UPDATE, ITEM_UPDATE, TILE_UPDATE)

  def fromId(id: Byte): PacketType = {
    values.find(_.id == id).getOrElse(
      throw new IllegalArgumentException(s"Unknown packet type ID: $id")
    )
  }
}
