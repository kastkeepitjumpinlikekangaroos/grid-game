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

  case object Flowers extends Tile {
    val id = 20
    val name = "flowers"
    val walkable = true
    val color = 0xFF66BB6A // Meadow green
  }

  case object Dirt extends Tile {
    val id = 21
    val name = "dirt"
    val walkable = true
    val color = 0xFF8D6E63 // Brown earth
  }

  case object Cobblestone extends Tile {
    val id = 22
    val name = "cobblestone"
    val walkable = true
    val color = 0xFF90A4AE // Blue-gray stone
  }

  case object Marsh extends Tile {
    val id = 23
    val name = "marsh"
    val walkable = true
    val color = 0xFF5D7A3E // Murky olive green
  }

  case object Crystal extends Tile {
    val id = 24
    val name = "crystal"
    val walkable = false
    val color = 0xFF7E57C2 // Amethyst purple
  }

  case object Coral extends Tile {
    val id = 25
    val name = "coral"
    val walkable = false
    val color = 0xFFFF7043 // Warm coral orange
  }

  case object Ruins extends Tile {
    val id = 26
    val name = "ruins"
    val walkable = false
    val color = 0xFF7E7360 // Weathered stone
  }

  case object Moss extends Tile {
    val id = 27
    val name = "moss"
    val walkable = true
    val color = 0xFF689F38 // Mossy green
  }

  case object Obsidian extends Tile {
    val id = 28
    val name = "obsidian"
    val walkable = false
    val color = 0xFF1A1A2E // Dark volcanic glass
  }

  case object Cliff extends Tile {
    val id = 29
    val name = "cliff"
    val walkable = false
    val color = 0xFF5C5040 // Steep rock face
  }

  case object Ash extends Tile {
    val id = 30
    val name = "ash"
    val walkable = true
    val color = 0xFF6B6B6B // Gray volcanic ash
  }

  case object Thorns extends Tile {
    val id = 31
    val name = "thorns"
    val walkable = false
    val color = 0xFF3B2F18 // Dark thorny brown
  }

  case object Basalt extends Tile {
    val id = 32
    val name = "basalt"
    val walkable = false
    val color = 0xFF2D2D3D // Dark columnar rock
  }

  case object Gravel extends Tile {
    val id = 33
    val name = "gravel"
    val walkable = true
    val color = 0xFFB0A899 // Loose rocky ground
  }

  val all: Seq[Tile] = Seq(
    Grass, Water, Sand, Stone, Wall, Tree, Path, DeepWater, Snow, Ice, Lava, Mountain, Fence,
    Metal, Glass, EnergyField, Circuit, Void, Toxic, Plasma,
    Flowers, Dirt, Cobblestone, Marsh, Crystal, Coral, Ruins, Moss,
    Obsidian, Cliff, Ash, Thorns, Basalt, Gravel
  )

  def fromId(id: Int): Tile = all.find(_.id == id).getOrElse(Grass)

  def fromName(name: String): Tile = all.find(_.name == name).getOrElse(Grass)
}
