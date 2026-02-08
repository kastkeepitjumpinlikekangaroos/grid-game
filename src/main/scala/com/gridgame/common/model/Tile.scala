package com.gridgame.common.model

sealed trait Tile {
  def id: Int
  def name: String
  def walkable: Boolean
  def color: Int // ARGB color for rendering
}

object Tile {
  case object Grass extends Tile {
    val id = 0
    val name = "grass"
    val walkable = true
    val color = 0xFF4CAF50 // Green
  }

  case object Water extends Tile {
    val id = 1
    val name = "water"
    val walkable = false
    val color = 0xFF2196F3 // Blue
  }

  case object Sand extends Tile {
    val id = 2
    val name = "sand"
    val walkable = true
    val color = 0xFFF5DEB3 // Wheat/tan
  }

  case object Stone extends Tile {
    val id = 3
    val name = "stone"
    val walkable = true
    val color = 0xFF9E9E9E // Gray
  }

  case object Wall extends Tile {
    val id = 4
    val name = "wall"
    val walkable = false
    val color = 0xFF5D4037 // Brown
  }

  case object Tree extends Tile {
    val id = 5
    val name = "tree"
    val walkable = false
    val color = 0xFF2E7D32 // Dark green
  }

  case object Path extends Tile {
    val id = 6
    val name = "path"
    val walkable = true
    val color = 0xFFD7CCC8 // Light brown
  }

  case object DeepWater extends Tile {
    val id = 7
    val name = "deep_water"
    val walkable = false
    val color = 0xFF1565C0 // Dark blue
  }

  case object Snow extends Tile {
    val id = 8
    val name = "snow"
    val walkable = true
    val color = 0xFFFAFAFA // White
  }

  case object Ice extends Tile {
    val id = 9
    val name = "ice"
    val walkable = true
    val color = 0xFFB3E5FC // Light blue
  }

  case object Lava extends Tile {
    val id = 10
    val name = "lava"
    val walkable = false
    val color = 0xFFFF5722 // Orange-red
  }

  case object Mountain extends Tile {
    val id = 11
    val name = "mountain"
    val walkable = false
    val color = 0xFF757575 // Dark gray
  }

  case object Fence extends Tile {
    val id = 12
    val name = "fence"
    val walkable = false
    val color = 0xFFA1887F // Light brown
  }

  case object Metal extends Tile {
    val id = 13
    val name = "metal"
    val walkable = true
    val color = 0xFF78909C // Blue-gray steel
  }

  case object Glass extends Tile {
    val id = 14
    val name = "glass"
    val walkable = true
    val color = 0xFF80DEEA // Translucent cyan
  }

  case object EnergyField extends Tile {
    val id = 15
    val name = "energy_field"
    val walkable = false
    val color = 0xFFAB47BC // Purple force field
  }

  case object Circuit extends Tile {
    val id = 16
    val name = "circuit"
    val walkable = true
    val color = 0xFF004D40 // Dark teal
  }

  case object Void extends Tile {
    val id = 17
    val name = "void"
    val walkable = false
    val color = 0xFF0A0A12 // Near-black space
  }

  case object Toxic extends Tile {
    val id = 18
    val name = "toxic"
    val walkable = false
    val color = 0xFF76FF03 // Bright acid green
  }

  case object Plasma extends Tile {
    val id = 19
    val name = "plasma"
    val walkable = false
    val color = 0xFFFF4081 // Hot pink
  }

  val all: Seq[Tile] = Seq(
    Grass, Water, Sand, Stone, Wall, Tree, Path, DeepWater, Snow, Ice, Lava, Mountain, Fence,
    Metal, Glass, EnergyField, Circuit, Void, Toxic, Plasma
  )

  def fromId(id: Int): Tile = all.find(_.id == id).getOrElse(Grass)

  def fromName(name: String): Tile = all.find(_.name == name).getOrElse(Grass)
}
