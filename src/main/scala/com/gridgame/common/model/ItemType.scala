package com.gridgame.common.model

sealed trait ItemType {
  def id: Byte
  def name: String
  def colorRGB: Int // ARGB color for rendering
}

object ItemType {
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

  case object Fence extends ItemType {
    val id: Byte = 5
    val name = "Fence"
    val colorRGB = 0xFFA1887F // Light brown
  }

  val all: Seq[ItemType] = Seq(Gem, Heart, Star, Shield, Fence)
  val spawnable: Seq[ItemType] = Seq(Heart, Star, Gem, Shield, Fence)

  def fromId(id: Byte): ItemType = all.find(_.id == id).getOrElse(Heart)
}
