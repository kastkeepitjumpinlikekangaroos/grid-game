package com.gridgame.common.protocol

import com.gridgame.common.Constants

import java.util.UUID

object ProjectileAction {
  val SPAWN: Byte = 0
  val MOVE: Byte = 1
  // A hit that used the projectile up
  val HIT: Byte = 2
  val DESPAWN: Byte = 3
  // A hit it flies on from (pierce): the client shows the hit and keeps the projectile. Sent as a
  // HIT, the client dropped it, and then ignored the MOVEs of a projectile it had seen hit.
  val PIERCE: Byte = 4
  // Stopped by a raised barrier: x, y are where it met the barrier, targetId the barrier's holder.
  // An explosive the barrier stops goes off there instead, and is sent as a DESPAWN like any other.
  val BLOCKED: Byte = 5
}

/** Which of a character's attacks fired a projectile. A client's spawn request has to say, because
  * the projectile type can't: many characters fire one type from two of their attacks (Bear mauls
  * with the claw it swipes with), and the server holds each attack to its own cooldown and
  * projectile count. PRIMARY is 0, so a request that doesn't say is taken as the primary attack. */
object AttackSlot {
  val PRIMARY = 0
  val Q = 1
  val E = 2
  // Shift+Space: the primary projectile in all eight directions at once
  val BURST = 3

  /** Headings a burst shot fires along, as (dx, dy) unit vectors: the eight compass points. */
  val BurstDirections: Seq[(Float, Float)] = (0 until 8).map { i =>
    val a = Math.PI * i / 4
    (Math.cos(a).toFloat, Math.sin(a).toFloat)
  }
}

object ProjectilePacket {
  /** A client asking the server to fire one projectile from one of its attacks ([[AttackSlot]]).
    * The server assigns the projectile its ID, so the request carries the slot in that field. */
  def spawnRequest(sequenceNumber: Int, ownerId: UUID, x: Float, y: Float, colorRGB: Int,
                   dx: Float, dy: Float, chargeLevel: Byte, projectileType: Byte, slot: Int): ProjectilePacket =
    new ProjectilePacket(sequenceNumber, ownerId, x, y, colorRGB, slot, dx, dy,
      ProjectileAction.SPAWN, null, chargeLevel, projectileType)
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

  /** From the server: the projectile tick it was sent on, carried in the timestamp field. A MOVE
    * is the projectile's position at the end of that tick; a SPAWN's position holds until the
    * tick after it, which is the first to move it. Ticks are Constants.PROJECTILE_SPEED_MS apart
    * and count from 1 in each match. */
  def getTick: Int = timestamp

  /** In a client's SPAWN request, the [[AttackSlot]] that fired it. */
  def getAttackSlot: Int = projectileId

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
    val buffer = SerializeUtil.acquireBuffer()

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

    // [33-36] Timestamp: from the server, the projectile tick it was sent on (getTick)
    buffer.putInt(timestamp)

    // Payload [37-63] (27 bytes)
    // [37-40] Projectile ID (a client's SPAWN request carries its AttackSlot here instead)
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

    buffer.array().clone()
  }

  override def toString: String = {
    val actionStr = action match {
      case ProjectileAction.SPAWN => "SPAWN"
      case ProjectileAction.MOVE => "MOVE"
      case ProjectileAction.HIT => "HIT"
      case ProjectileAction.DESPAWN => "DESPAWN"
      case ProjectileAction.PIERCE => "PIERCE"
      case ProjectileAction.BLOCKED => "BLOCKED"
      case _ => "UNKNOWN"
    }
    s"ProjectilePacket{seq=$sequenceNumber, owner=${playerId.toString.substring(0, 8)}, pos=($x, $y), projId=$projectileId, vel=($dx, $dy), action=$actionStr}"
  }
}
