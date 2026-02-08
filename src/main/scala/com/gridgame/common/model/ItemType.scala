package com.gridgame.common.model

sealed trait ItemType {
  def id: Byte
  def name: String
  def colorRGB: Int // ARGB color for rendering
}

object ItemType {
  case object Coin extends ItemType {
    val id: Byte = 0
    val name = "Coin"
    val colorRGB = 0xFFFFD700 // Gold
  }

  case object Gem extends ItemType {
    val id: Byte = 1
    val name = "Gem"
    val colorRGB = 0xFF00BCD4 // Cyan
  }

  case object Heart extends ItemType {
    val id: Byte = 2
    val name = "Heart"
    val colorRGB = 0xFFE91E63 // Pink
  }

  case object Star extends ItemType {
    val id: Byte = 3
    val name = "Star"
    val colorRGB = 0xFFFFEB3B // Yellow
  }

  case object Shield extends ItemType {
    val id: Byte = 4
    val name = "Shield"
    val colorRGB = 0xFF9C27B0 // Purple
  }

  val all: Seq[ItemType] = Seq(Coin, Gem, Heart, Star, Shield)

  def fromId(id: Byte): ItemType = all.find(_.id == id).getOrElse(Coin)
}
