package com.gridgame.common.model

import com.gridgame.common.Constants
import java.util.UUID

object ProjectileType {
  val NORMAL: Byte = 0
  val TENTACLE: Byte = 1
  val ICE_BEAM: Byte = 2
  val AXE: Byte = 3
  val ROPE: Byte = 4
  val SPEAR: Byte = 5
  val SOUL_BOLT: Byte = 6
  val HAUNT: Byte = 7
  val SPLASH: Byte = 8
  val TIDAL_WAVE: Byte = 9
  val GEYSER: Byte = 10
  val BULLET: Byte = 11
  val GRENADE: Byte = 12
  val ROCKET: Byte = 13
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
    case ProjectileType.AXE => 0.6f
    case ProjectileType.ROPE => 0.85f
    case ProjectileType.SPEAR => 0.7f
    case ProjectileType.SOUL_BOLT => 0.65f
    case ProjectileType.HAUNT => 0.5f
    case ProjectileType.SPLASH => 0.65f
    case ProjectileType.TIDAL_WAVE => 0.5f
    case ProjectileType.GEYSER => 0.6f
    case ProjectileType.BULLET => 1.0f
    case ProjectileType.GRENADE => 0.5f
    case ProjectileType.ROCKET => 0.55f
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
    // Grenades pass through players entirely
    if (projectileType == ProjectileType.GRENADE) return false
    val pos = player.getPosition
    val dx = x - (pos.getX + 0.5f)
    val dy = y - (pos.getY + 0.5f)
    val radius = projectileType match {
      case ProjectileType.AXE => Constants.AXE_HIT_RADIUS
      case ProjectileType.GEYSER => Constants.GEYSER_AOE_RADIUS
      case _ => Constants.PROJECTILE_HIT_RADIUS
    }
    dx * dx + dy * dy <= radius * radius
  }

  override def toString: String = {
    s"Projectile{id=$id, owner=${ownerId.toString.substring(0, 8)}, pos=($x, $y), vel=($dx, $dy)}"
  }
}
