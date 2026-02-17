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
  case object LOBBY_ACTION extends PacketType(0x09.toByte, true)
  case object GAME_EVENT extends PacketType(0x0A.toByte, true)
  case object AUTH_REQUEST extends PacketType(0x0B.toByte, true)
  case object AUTH_RESPONSE extends PacketType(0x0C.toByte, true)
  case object MATCH_HISTORY extends PacketType(0x0D.toByte, true)
  case object RANKED_QUEUE extends PacketType(0x0E.toByte, true)
  case object LEADERBOARD extends PacketType(0x0F.toByte, true)
  case object SESSION_TOKEN extends PacketType(0x10.toByte, true)

  private val values: Array[PacketType] = Array(PLAYER_JOIN, PLAYER_UPDATE, PLAYER_LEAVE, WORLD_INFO, HEARTBEAT, PROJECTILE_UPDATE, ITEM_UPDATE, TILE_UPDATE, LOBBY_ACTION, GAME_EVENT, AUTH_REQUEST, AUTH_RESPONSE, MATCH_HISTORY, RANKED_QUEUE, LEADERBOARD, SESSION_TOKEN)

  def fromId(id: Byte): PacketType = {
    values.find(_.id == id).getOrElse(
      throw new IllegalArgumentException(s"Unknown packet type ID: $id")
    )
  }
}
