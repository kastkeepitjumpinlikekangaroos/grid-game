package com.gridgame.common.model

sealed trait Direction {
  def id: Int
}

object Direction {
  case object Down extends Direction { val id = 0 }
  case object Up extends Direction { val id = 1 }
  case object Left extends Direction { val id = 2 }
  case object Right extends Direction { val id = 3 }

  def fromId(id: Int): Direction = id match {
    case 0 => Down
    case 1 => Up
    case 2 => Left
    case 3 => Right
    case _ => Down
  }

  def fromMovement(dx: Int, dy: Int): Direction = {
    if (dy < 0) Up
    else if (dy > 0) Down
    else if (dx < 0) Left
    else if (dx > 0) Right
    else Down
  }
}
