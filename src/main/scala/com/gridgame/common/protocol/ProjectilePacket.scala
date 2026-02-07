package com.gridgame.common.protocol

import com.gridgame.common.Constants
import com.gridgame.common.model.Direction

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

object ProjectileAction {
  val SPAWN: Byte = 0
  val MOVE: Byte = 1
  val HIT: Byte = 2
  val DESPAWN: Byte = 3
}

class ProjectilePacket(
    sequenceNumber: Int,
    ownerId: UUID,
    timestamp: Int,
    val x: Int,
    val y: Int,
    val colorRGB: Int,
    val projectileId: Int,
    val direction: Direction,
    val action: Byte,
    val targetId: UUID = null
) extends Packet(PacketType.PROJECTILE_UPDATE, sequenceNumber, ownerId, timestamp) {

  def this(sequenceNumber: Int, ownerId: UUID, x: Int, y: Int, colorRGB: Int,
           projectileId: Int, direction: Direction, action: Byte) = {
    this(sequenceNumber, ownerId, Packet.getCurrentTimestamp, x, y, colorRGB,
         projectileId, direction, action, null)
  }

  def this(sequenceNumber: Int, ownerId: UUID, x: Int, y: Int, colorRGB: Int,
           projectileId: Int, direction: Direction, action: Byte, targetId: UUID) = {
    this(sequenceNumber, ownerId, Packet.getCurrentTimestamp, x, y, colorRGB,
         projectileId, direction, action, targetId)
  }

  def getProjectileId: Int = projectileId

  def getDirection: Direction = direction

  def getAction: Byte = action

  def getTargetId: UUID = targetId

  def getX: Int = x

  def getY: Int = y

  def getColorRGB: Int = colorRGB

  override def serialize(): Array[Byte] = {
    val buffer = ByteBuffer.allocate(Constants.PACKET_SIZE)
    buffer.order(ByteOrder.BIG_ENDIAN)

    // [0] Packet Type
    buffer.put(packetType.id)

    // [1-4] Sequence Number
    buffer.putInt(sequenceNumber)

    // [5-20] Owner ID (UUID = 2 longs)
    buffer.putLong(ownerId.getMostSignificantBits)
    buffer.putLong(ownerId.getLeastSignificantBits)

    // [21-24] X Position
    buffer.putInt(x)

    // [25-28] Y Position
    buffer.putInt(y)

    // [29-32] Color RGB
    buffer.putInt(colorRGB)

    // [33-36] Timestamp
    buffer.putInt(timestamp)

    // Payload [37-63] (27 bytes)
    // [37-40] Projectile ID
    buffer.putInt(projectileId)

    // [41] Direction ID
    buffer.put(direction.id.toByte)

    // [42] Action
    buffer.put(action)

    // [43-58] Target UUID (for hit action)
    if (targetId != null) {
      buffer.putLong(targetId.getMostSignificantBits)
      buffer.putLong(targetId.getLeastSignificantBits)
    } else {
      buffer.putLong(0L)
      buffer.putLong(0L)
    }

    // [59-63] Reserved (5 bytes)
    buffer.put(new Array[Byte](5))

    buffer.array()
  }

  override def toString: String = {
    val actionStr = action match {
      case ProjectileAction.SPAWN => "SPAWN"
      case ProjectileAction.MOVE => "MOVE"
      case ProjectileAction.HIT => "HIT"
      case ProjectileAction.DESPAWN => "DESPAWN"
      case _ => "UNKNOWN"
    }
    s"ProjectilePacket{seq=$sequenceNumber, owner=${playerId.toString.substring(0, 8)}, pos=($x, $y), projId=$projectileId, dir=$direction, action=$actionStr}"
  }
}
