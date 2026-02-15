package com.gridgame.common.model

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
  val ARCANE_BOLT: Byte = 8
  val FIREBALL: Byte = 9
  val SPLASH: Byte = 10
  val TIDAL_WAVE: Byte = 11
  val GEYSER: Byte = 12
  val BULLET: Byte = 13
  val GRENADE: Byte = 14
  val ROCKET: Byte = 15
  val TALON: Byte = 16
  val GUST: Byte = 17
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

  val speedMultiplier: Float = ProjectileDef.get(projectileType).effectiveSpeed(chargeLevel)

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
    val pDef = ProjectileDef.get(projectileType)
    if (pDef.passesThroughPlayers) return false
    val pos = player.getPosition
    val dx = x - (pos.getX + 0.5f)
    val dy = y - (pos.getY + 0.5f)
    dx * dx + dy * dy <= pDef.hitRadius * pDef.hitRadius
  }

  override def toString: String = {
    s"Projectile{id=$id, owner=${ownerId.toString.substring(0, 8)}, pos=($x, $y), vel=($dx, $dy)}"
  }
}
