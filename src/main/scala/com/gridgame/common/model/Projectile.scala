package com.gridgame.common.model

import java.util.UUID

class Projectile(
    val id: Int,
    val ownerId: UUID,
    private var x: Int,
    private var y: Int,
    val direction: Direction,
    val colorRGB: Int
) {

  def getX: Int = x

  def getY: Int = y

  def move(): Unit = {
    direction match {
      case Direction.Up    => y -= 1
      case Direction.Down  => y += 1
      case Direction.Left  => x -= 1
      case Direction.Right => x += 1
    }
  }

  def isOutOfBounds(world: WorldData): Boolean = {
    x < 0 || x >= world.width || y < 0 || y >= world.height
  }

  def hitsNonWalkable(world: WorldData): Boolean = {
    !world.isWalkable(x, y)
  }

  def hitsPlayer(player: Player): Boolean = {
    val pos = player.getPosition
    pos.getX == x && pos.getY == y && !player.getId.equals(ownerId)
  }

  override def toString: String = {
    s"Projectile{id=$id, owner=${ownerId.toString.substring(0, 8)}, pos=($x, $y), dir=$direction}"
  }
}
