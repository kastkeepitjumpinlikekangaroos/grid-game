package com.gridgame.common.model

import com.gridgame.common.Constants
import java.util.Objects

final class Position(private val x: Int, private val y: Int) {
  if (x < 0 || x >= Constants.GRID_SIZE) {
    throw new IllegalArgumentException(
      s"X coordinate $x out of bounds [0, ${Constants.GRID_SIZE})"
    )
  }
  if (y < 0 || y >= Constants.GRID_SIZE) {
    throw new IllegalArgumentException(
      s"Y coordinate $y out of bounds [0, ${Constants.GRID_SIZE})"
    )
  }

  def getX: Int = x

  def getY: Int = y

  override def equals(obj: Any): Boolean = {
    obj match {
      case that: Position => this.x == that.x && this.y == that.y
      case _ => false
    }
  }

  override def hashCode(): Int = Objects.hash(Int.box(x), Int.box(y))

  override def toString: String = s"Position($x, $y)"
}
