package com.gridgame.common.model

import java.util.UUID

class Projectile(
    val id: Int,
    val ownerId: UUID,
    private var x: Float,
    private var y: Float,
    val dx: Float,
    val dy: Float,
    val colorRGB: Int
) {

  private var distanceTraveled: Float = 0f

  def getX: Float = x

  def getY: Float = y

  def getCellX: Int = x.toInt

  def getCellY: Int = y.toInt

  def getDistanceTraveled: Float = distanceTraveled

  def move(): Unit = {
    x += dx
    y += dy
    distanceTraveled += math.sqrt(dx * dx + dy * dy).toFloat
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
    val pos = player.getPosition
    pos.getX == getCellX && pos.getY == getCellY && !player.getId.equals(ownerId)
  }

  override def toString: String = {
    s"Projectile{id=$id, owner=${ownerId.toString.substring(0, 8)}, pos=($x, $y), vel=($dx, $dy)}"
  }
}
