package com.gridgame.common.protocol

import com.gridgame.common.Constants

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
    val x: Float,
    val y: Float,
    val colorRGB: Int,
    val projectileId: Int,
    val dx: Float,
    val dy: Float,
    val action: Byte,
    val targetId: UUID = null,
    val chargeLevel: Byte = 0,
    val projectileType: Byte = 0
) extends Packet(PacketType.PROJECTILE_UPDATE, sequenceNumber, ownerId, timestamp) {

  def this(sequenceNumber: Int, ownerId: UUID, x: Float, y: Float, colorRGB: Int,
           projectileId: Int, dx: Float, dy: Float, action: Byte) = {
    this(sequenceNumber, ownerId, Packet.getCurrentTimestamp, x, y, colorRGB,
         projectileId, dx, dy, action, null, 0.toByte, 0.toByte)
  }

  def this(sequenceNumber: Int, ownerId: UUID, x: Float, y: Float, colorRGB: Int,
           projectileId: Int, dx: Float, dy: Float, action: Byte, targetId: UUID) = {
    this(sequenceNumber, ownerId, Packet.getCurrentTimestamp, x, y, colorRGB,
         projectileId, dx, dy, action, targetId, 0.toByte, 0.toByte)
  }

  def this(sequenceNumber: Int, ownerId: UUID, x: Float, y: Float, colorRGB: Int,
           projectileId: Int, dx: Float, dy: Float, action: Byte, targetId: UUID, chargeLevel: Byte) = {
    this(sequenceNumber, ownerId, Packet.getCurrentTimestamp, x, y, colorRGB,
         projectileId, dx, dy, action, targetId, chargeLevel, 0.toByte)
  }

  def this(sequenceNumber: Int, ownerId: UUID, x: Float, y: Float, colorRGB: Int,
           projectileId: Int, dx: Float, dy: Float, action: Byte, targetId: UUID, chargeLevel: Byte, projectileType: Byte) = {
    this(sequenceNumber, ownerId, Packet.getCurrentTimestamp, x, y, colorRGB,
         projectileId, dx, dy, action, targetId, chargeLevel, projectileType)
  }

  def getProjectileId: Int = projectileId

  def getDx: Float = dx

  def getDy: Float = dy

  def getAction: Byte = action

  def getTargetId: UUID = targetId

  def getX: Float = x

  def getY: Float = y

  def getColorRGB: Int = colorRGB

  def getChargeLevel: Int = chargeLevel.toInt & 0xFF

  def getProjectileType: Byte = projectileType

  override def serialize(): Array[Byte] = {
    val buffer = ByteBuffer.allocate(Constants.PACKET_PAYLOAD_SIZE)
    buffer.order(ByteOrder.BIG_ENDIAN)

    // [0] Packet Type
    buffer.put(packetType.id)

    // [1-4] Sequence Number
    buffer.putInt(sequenceNumber)

    // [5-20] Owner ID (UUID = 2 longs)
    buffer.putLong(ownerId.getMostSignificantBits)
    buffer.putLong(ownerId.getLeastSignificantBits)

    // [21-24] X Position (as float bits)
    buffer.putFloat(x)

    // [25-28] Y Position (as float bits)
    buffer.putFloat(y)

    // [29-32] Color RGB
    buffer.putInt(colorRGB)

    // [33-36] Timestamp
    buffer.putInt(timestamp)

    // Payload [37-63] (27 bytes)
    // [37-40] Projectile ID
    buffer.putInt(projectileId)

    // [41-42] DX (scaled short: dx * 32767)
    buffer.putShort((dx * 32767).toShort)

    // [43-44] DY (scaled short: dy * 32767)
    buffer.putShort((dy * 32767).toShort)

    // [45] Action
    buffer.put(action)

    // [46-61] Target UUID (for hit action)
    if (targetId != null) {
      buffer.putLong(targetId.getMostSignificantBits)
      buffer.putLong(targetId.getLeastSignificantBits)
    } else {
      buffer.putLong(0L)
      buffer.putLong(0L)
    }

    // [62] Charge level (0-100)
    buffer.put(chargeLevel)

    // [63] Projectile type (0=normal, 1=tentacle, 2=ice)
    buffer.put(projectileType)

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
    s"ProjectilePacket{seq=$sequenceNumber, owner=${playerId.toString.substring(0, 8)}, pos=($x, $y), projId=$projectileId, vel=($dx, $dy), action=$actionStr}"
  }
}
