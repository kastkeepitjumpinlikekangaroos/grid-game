package com.gridgame.common.model

import com.gridgame.common.Constants
import java.util.UUID

object ProjectileType {
  val NORMAL: Byte = 0
  val TENTACLE: Byte = 1
  val ICE_BEAM: Byte = 2
}

class Projectile(
    val id: Int,
    val ownerId: UUID,
    private var x: Float,
    private var y: Float,
    val dx: Float,
    val dy: Float,
    val colorRGB: Int,
    val chargeLevel: Int = 0,
    val projectileType: Byte = ProjectileType.NORMAL
) {

  private var distanceTraveled: Float = 0f

  val speedMultiplier: Float = projectileType match {
    case ProjectileType.ICE_BEAM => 0.3f
    case ProjectileType.TENTACLE => 0.9f
    case _ =>
      val c = Constants
      c.CHARGE_MIN_SPEED + (chargeLevel / 100.0f) * (c.CHARGE_MAX_SPEED - c.CHARGE_MIN_SPEED)
  }

  def getX: Float = x

  def getY: Float = y

  def getCellX: Int = x.toInt

  def getCellY: Int = y.toInt

  def getDistanceTraveled: Float = distanceTraveled

  /** Move one sub-step (fractional tick). */
  def moveStep(fraction: Float): Unit = {
    x += dx * speedMultiplier * fraction
    y += dy * speedMultiplier * fraction
    distanceTraveled += math.sqrt(dx * dx + dy * dy).toFloat * speedMultiplier * fraction
  }

  def move(): Unit = {
    x += dx * speedMultiplier
    y += dy * speedMultiplier
    distanceTraveled += math.sqrt(dx * dx + dy * dy).toFloat * speedMultiplier
  }

  def isOutOfBounds(world: WorldData): Boolean = {
    val cellX = getCellX
    val cellY = getCellY
    cellX < 0 || cellX >= world.width || cellY < 0 || cellY >= world.height
  }

  def hitsNonWalkable(world: WorldData): Boolean = {
    !world.isWalkable(getCellX, getCellY)
  }

  def hitsPlayer(player: Player): Boolean = {
    if (player.getId.equals(ownerId)) return false
    val pos = player.getPosition
    val dx = x - (pos.getX + 0.5f)
    val dy = y - (pos.getY + 0.5f)
    val radius = com.gridgame.common.Constants.PROJECTILE_HIT_RADIUS
    dx * dx + dy * dy <= radius * radius
  }

  override def toString: String = {
    s"Projectile{id=$id, owner=${ownerId.toString.substring(0, 8)}, pos=($x, $y), vel=($dx, $dy)}"
  }
}
