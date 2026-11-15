package com.gridgame.common.model

sealed trait Tile {
  def id: Int
  def name: String
  def walkable: Boolean
  def color: Int // ARGB color for rendering

  /** Whether a projectile is stopped by the tile: everything nobody can walk on stops one, but
    * water and lava, which a shot flies over as it does over the ground. */
  def stopsProjectiles: Boolean = !walkable

  /** How the tile stands on the map, which is how it is drawn — see [[TileForm]]. */
  def form: TileForm = if (walkable) TileForm.Ground else TileForm.Block

  /** For a [[TileForm.Prop]], the ground it grows out of when it isn't standing in any other
    * (see [[Tile.groundUnder]]). Meaningless for the other forms. */
  def ground: Tile = Tile.Grass

  /** Whether its four atlas frames are an animation, cycled cell by cell (a pool's ripples, a moving
    * block's), rather than four variants of it picked by position ([[variantAt]]). */
  def animates: Boolean = form eq TileForm.Pool

  /** Which of its four variants cell (x, y) shows, by where it is, so a field of it isn't one tile
    * stamped over and over. */
  def variantAt(x: Int, y: Int): Int = ((x * 7 + y * 13) & 0x7FFFFFFF) % 4
}

/**
 * How a tile stands on the map. The server only cares whether a tile can be walked on and whether
 * it stops a projectile; this is for everything that draws the world (the game, the map editor),
 * and it decides the pass a tile is drawn in and what is drawn under it.
 */
sealed abstract class TileForm

object TileForm {
  /** Walkable ground: a flat diamond, drawn before everything else. */
  case object Ground extends TileForm

  /** A flat surface nobody can walk on — water, lava. It lies level with the ground, so it is
    * drawn with the ground, animates, and casts no shadow. */
  case object Pool extends TileForm

  /** A solid block rising off the ground — a wall, a cliff, the void. Its sprite covers its whole
    * cell, and it is drawn in depth order with everything standing on the map. */
  case object Block extends TileForm

  /** Something standing on the ground — a tree, a rock, a bush. Its sprite leaves the ground
    * around it showing, so whatever ground it stands in is laid under it first
    * ([[Tile.groundUnder]]), and the same tree can stand in a meadow or on a beach. */
  case object Prop extends TileForm
}

object Tile {
  case object Grass extends Tile {
    val id = 0
    val name = "grass"
    val walkable = true
    val color = 0xFF80CE50 // Meadow green
  }

  case object Water extends Tile {
    val id = 1
    val name = "water"
    val walkable = false
    override val form: TileForm = TileForm.Pool
    override val stopsProjectiles: Boolean = false
    val color = 0xFF4ABAF0 // Bright blue
  }

  case object Sand extends Tile {
    val id = 2
    val name = "sand"
    val walkable = true
    val color = 0xFFFAE7B0 // Warm sand
  }

  case object Stone extends Tile {
    val id = 3
    val name = "stone"
    val walkable = true
    val color = 0xFFD6D0C4 // Pale flagstone
  }

  case object Wall extends Tile {
    val id = 4
    val name = "wall"
    val walkable = false
    val color = 0xFFCEBC9C // Sandstone bricks
  }

  case object Tree extends Tile {
    val id = 5
    val name = "tree"
    val walkable = false
    override val form: TileForm = TileForm.Prop
    override def ground: Tile = Grass
    val color = 0xFF4FA64A // Canopy green, darker than the grass it stands in
  }

  case object Path extends Tile {
    val id = 6
    val name = "path"
    val walkable = true
    val color = 0xFFE6C68C // Dirt path
  }

  case object DeepWater extends Tile {
    val id = 7
    val name = "deep_water"
    val walkable = false
    override val form: TileForm = TileForm.Pool
    override val stopsProjectiles: Boolean = false
    val color = 0xFF2E80D6 // Deep blue
  }

  case object Snow extends Tile {
    val id = 8
    val name = "snow"
    val walkable = true
    val color = 0xFFF2F7FF // Snow white
  }

  case object Ice extends Tile {
    val id = 9
    val name = "ice"
    val walkable = true
    val color = 0xFFC0EAFC // Pale ice
  }

  case object Lava extends Tile {
    val id = 10
    val name = "lava"
    val walkable = false
    override val form: TileForm = TileForm.Pool
    // A shot flies over it, as over water: it lies level with the ground
    override val stopsProjectiles: Boolean = false
    val color = 0xFFFF8028 // Molten orange
  }

  case object Mountain extends Tile {
    val id = 11
    val name = "mountain"
    val walkable = false
    val color = 0xFFA0968C // Grey crag
  }

  case object Fence extends Tile {
    val id = 12
    val name = "fence"
    val walkable = false
    val color = 0xFFD6A062 // Plank wood
  }

  case object Metal extends Tile {
    val id = 13
    val name = "metal"
    val walkable = true
    val color = 0xFFB2C0D0 // Steel plate
  }

  case object Glass extends Tile {
    val id = 14
    val name = "glass"
    val walkable = true
    val color = 0xFFAAE4F4 // Glass cyan
  }

  case object EnergyField extends Tile {
    val id = 15
    val name = "energy_field"
    val walkable = false
    val color = 0xFFB274F0 // Violet force field
  }

  case object Circuit extends Tile {
    val id = 16
    val name = "circuit"
    val walkable = true
    val color = 0xFF24726E // Deck teal
    /** Deck plating, plates two cells across: its variants are a plate's four quarters. */
    override def variantAt(x: Int, y: Int): Int = (x & 1) + 2 * (y & 1)
  }

  /** Open space below the edge of the space maps' arena: flat, nobody can stand there, and it stops
    * a shot as a wall does. Its frames are four skies, picked by position, not an animation. */
  case object Void extends Tile {
    val id = 17
    val name = "void"
    val walkable = false
    override val form: TileForm = TileForm.Pool
    override def animates: Boolean = false
    val color = 0xFF18143E // Deep space
  }

  case object Toxic extends Tile {
    val id = 18
    val name = "toxic"
    val walkable = false
    override val form: TileForm = TileForm.Prop
    override def ground: Tile = Circuit
    val color = 0xFF92E848 // Slime green
  }

  case object Plasma extends Tile {
    val id = 19
    val name = "plasma"
    val walkable = false
    override val form: TileForm = TileForm.Prop
    override def ground: Tile = Circuit
    val color = 0xFFFF68B6 // Hot pink
  }

  case object Flowers extends Tile {
    val id = 20
    val name = "flowers"
    val walkable = true
    val color = 0xFF9CD46A // Meadow in flower
  }

  case object Dirt extends Tile {
    val id = 21
    val name = "dirt"
    val walkable = true
    val color = 0xFFC08E5E // Brown earth
  }

  case object Cobblestone extends Tile {
    val id = 22
    val name = "cobblestone"
    val walkable = true
    val color = 0xFFB6B4C0 // Cobbles
  }

  case object Marsh extends Tile {
    val id = 23
    val name = "marsh"
    val walkable = true
    val color = 0xFF829658 // Murky olive
  }

  case object Crystal extends Tile {
    val id = 24
    val name = "crystal"
    val walkable = false
    override val form: TileForm = TileForm.Prop
    override def ground: Tile = Stone
    val color = 0xFFB280F0 // Amethyst
  }

  case object Coral extends Tile {
    val id = 25
    val name = "coral"
    val walkable = false
    override val form: TileForm = TileForm.Prop
    override def ground: Tile = Sand
    val color = 0xFFFF7896 // Coral pink
  }

  case object Ruins extends Tile {
    val id = 26
    val name = "ruins"
    val walkable = false
    override val form: TileForm = TileForm.Prop
    override def ground: Tile = Grass
    val color = 0xFFE8E2D6 // Weathered marble
  }

  case object Moss extends Tile {
    val id = 27
    val name = "moss"
    val walkable = true
    val color = 0xFF68AC50 // Mossy green
  }

  case object Obsidian extends Tile {
    val id = 28
    val name = "obsidian"
    val walkable = false
    override val form: TileForm = TileForm.Prop
    override def ground: Tile = Circuit
    val color = 0xFF423868 // Volcanic glass
  }

  case object Cliff extends Tile {
    val id = 29
    val name = "cliff"
    val walkable = false
    val color = 0xFFB67C4C // Earth under grass
  }

  case object Ash extends Tile {
    val id = 30
    val name = "ash"
    val walkable = true
    val color = 0xFF8C8686 // Grey ash
  }

  case object Thorns extends Tile {
    val id = 31
    val name = "thorns"
    val walkable = false
    override val form: TileForm = TileForm.Prop
    override def ground: Tile = Dirt
    val color = 0xFF528446 // Bramble green
  }

  case object Basalt extends Tile {
    val id = 32
    val name = "basalt"
    val walkable = false
    val color = 0xFF56526C // Dark columns
  }

  case object Gravel extends Tile {
    val id = 33
    val name = "gravel"
    val walkable = true
    val color = 0xFFCEC4B2 // Loose rocky ground
  }

  // Props and ground for the bright maps (the Meadow, the Lagoon, the Snowglobe)

  case object Bush extends Tile {
    val id = 34
    val name = "bush"
    val walkable = false
    override val form: TileForm = TileForm.Prop
    val color = 0xFF5EB456 // Leafy green
  }

  case object Rock extends Tile {
    val id = 35
    val name = "rock"
    val walkable = false
    override val form: TileForm = TileForm.Prop
    val color = 0xFFA8A29A // Weathered grey
  }

  case object Mushroom extends Tile {
    val id = 36
    val name = "mushroom"
    val walkable = false
    override val form: TileForm = TileForm.Prop
    val color = 0xFFFF9234 // Orange cap
  }

  case object Palm extends Tile {
    val id = 37
    val name = "palm"
    val walkable = false
    override val form: TileForm = TileForm.Prop
    override def ground: Tile = Sand
    val color = 0xFF62BE46 // Frond green
  }

  case object Pine extends Tile {
    val id = 38
    val name = "pine"
    val walkable = false
    override val form: TileForm = TileForm.Prop
    override def ground: Tile = Snow
    val color = 0xFF3E9068 // Evergreen
  }

  case object Snowman extends Tile {
    val id = 39
    val name = "snowman"
    val walkable = false
    override val form: TileForm = TileForm.Prop
    override def ground: Tile = Snow
    val color = 0xFFF4F8FF // Snow white
  }

  case object Planks extends Tile {
    val id = 40
    val name = "planks"
    val walkable = true
    val color = 0xFFC8955C // Boardwalk wood
  }

  case object IceBlock extends Tile {
    val id = 41
    val name = "ice_block"
    val walkable = false
    val color = 0xFFA6DCF6 // Glacier blue
  }

  // The volcano maps (the Crucible, the Caldera, the Cinderlands): scorched rock, with ash lying on
  // it and lava (above) running through it

  case object Cinder extends Tile {
    val id = 42
    val name = "cinder"
    val walkable = true
    val color = 0xFF96706C // Scorched rock
  }

  case object CharredTree extends Tile {
    val id = 43
    val name = "charred_tree"
    val walkable = false
    override val form: TileForm = TileForm.Prop
    override def ground: Tile = Cinder
    val color = 0xFF3E3038 // Burnt wood
  }

  case object MagmaRock extends Tile {
    val id = 44
    val name = "magma_rock"
    val walkable = false
    override val form: TileForm = TileForm.Prop
    override def ground: Tile = Cinder
    val color = 0xFF54404A // Dark rock, cracked and glowing
  }

  // The graveyard maps (the Crypt, the Graveyard, the Necropolis), under the moon

  case object DeadGrass extends Tile {
    val id = 45
    val name = "dead_grass"
    val walkable = true
    val color = 0xFF68867A // Grass by moonlight
  }

  case object Tombstone extends Tile {
    val id = 46
    val name = "tombstone"
    val walkable = false
    override val form: TileForm = TileForm.Prop
    override def ground: Tile = DeadGrass
    val color = 0xFFA9A8BC // Headstone grey
    // Graves stand in rows, every fourth cell: by the usual rule a whole plot of them was one stone
    override def variantAt(x: Int, y: Int): Int = Tile.scattered(x, y)
  }

  case object DeadTree extends Tile {
    val id = 47
    val name = "dead_tree"
    val walkable = false
    override val form: TileForm = TileForm.Prop
    override def ground: Tile = DeadGrass
    val color = 0xFF6E6478 // Bare grey wood
  }

  case object Crypt extends Tile {
    val id = 48
    val name = "crypt"
    val walkable = false
    val color = 0xFF6E6C84 // Mausoleum stone
  }

  case object Lantern extends Tile {
    val id = 49
    val name = "lantern"
    val walkable = false
    override val form: TileForm = TileForm.Prop
    override def ground: Tile = DeadGrass
    val color = 0xFFFFC65A // Candlelight
    // Lamps stand at even steps along a path, as the graves do
    override def variantAt(x: Int, y: Int): Int = Tile.scattered(x, y)
  }

  case object Cypress extends Tile {
    val id = 55
    val name = "cypress"
    val walkable = false
    override val form: TileForm = TileForm.Prop
    override def ground: Tile = DeadGrass
    val color = 0xFF3E6E68 // Dark evergreen
  }

  // The desert maps (the Tomb, the Oasis, the Dunes)

  case object Dune extends Tile {
    val id = 50
    val name = "dune"
    val walkable = true
    val color = 0xFFEEC478 // Deep golden sand
  }

  case object Cactus extends Tile {
    val id = 51
    val name = "cactus"
    val walkable = false
    override val form: TileForm = TileForm.Prop
    override def ground: Tile = Dune
    val color = 0xFF5FAE5A // Cactus green
  }

  case object Obelisk extends Tile {
    val id = 52
    val name = "obelisk"
    val walkable = false
    override val form: TileForm = TileForm.Prop
    override def ground: Tile = Dune
    val color = 0xFFD9B77C // Carved sandstone
  }

  case object Mesa extends Tile {
    val id = 53
    val name = "mesa"
    val walkable = false
    val color = 0xFFC8794A // Red rock
  }

  case object Bones extends Tile {
    val id = 54
    val name = "bones"
    val walkable = false
    override val form: TileForm = TileForm.Prop
    override def ground: Tile = Dune
    val color = 0xFFEFE8D6 // Bleached bone
  }

  val all: Seq[Tile] = Seq(
    Grass, Water, Sand, Stone, Wall, Tree, Path, DeepWater, Snow, Ice, Lava, Mountain, Fence,
    Metal, Glass, EnergyField, Circuit, Void, Toxic, Plasma,
    Flowers, Dirt, Cobblestone, Marsh, Crystal, Coral, Ruins, Moss,
    Obsidian, Cliff, Ash, Thorns, Basalt, Gravel,
    Bush, Rock, Mushroom, Palm, Pine, Snowman, Planks, IceBlock,
    Cinder, CharredTree, MagmaRock, DeadGrass, Tombstone, DeadTree, Crypt, Lantern,
    Dune, Cactus, Obelisk, Mesa, Bones, Cypress
  )

  /** A variant for cell (x, y) that doesn't repeat along a row, a column or any even step of
    * either, for tiles a map sets out at even steps. */
  private def scattered(x: Int, y: Int): Int = {
    var h = x * 73856093 ^ y * 19349663
    h ^= h >>> 13; h *= 0x5bd1e995; h ^= h >>> 15
    (h & 0x7FFFFFFF) % 4
  }

  def fromId(id: Int): Tile = all.find(_.id == id).getOrElse(Grass)

  def fromName(name: String): Tile = all.find(_.name == name).getOrElse(Grass)

  /**
   * The ground a prop at (x, y) is drawn standing in: its own [[Tile.ground]] if any of the four
   * cells around it is that, otherwise the first walkable ground around it, otherwise its own.
   * So a tree at the edge of a meadow stands on grass even beside a path, a rock on a beach
   * stands in sand, and a tree deep in a forest — nothing but trees around it — still has grass
   * under it. Allocates nothing: the renderer asks it for every prop on screen, every frame.
   */
  def groundUnder(world: WorldData, x: Int, y: Int): Tile = {
    val own = world.getTile(x, y).ground
    var first: Tile = null
    var i = 0
    while (i < 4) {
      val n = (i: @scala.annotation.switch) match {
        case 0 => world.getTile(x, y + 1)
        case 1 => world.getTile(x + 1, y)
        case 2 => world.getTile(x - 1, y)
        case _ => world.getTile(x, y - 1)
      }
      if (n.form eq TileForm.Ground) {
        if (n eq own) return own
        if (first == null) first = n
      }
      i += 1
    }
    if (first != null) first else own
  }
}
