package com.gridgame.common.protocol

import java.util.Objects
import java.util.UUID

abstract class Packet(
    val packetType: PacketType,
    val sequenceNumber: Int,
    val playerId: UUID,
    val timestamp: Int
) {
  Objects.requireNonNull(packetType, "Packet type cannot be null")
  Objects.requireNonNull(playerId, "Player ID cannot be null")

  def getType: PacketType = packetType

  def getSequenceNumber: Int = sequenceNumber

  def getPlayerId: UUID = playerId

  def getTimestamp: Int = timestamp

  def serialize(): Array[Byte]
}

object Packet {
  def getCurrentTimestamp: Int = (System.currentTimeMillis() / 1000).toInt
}
