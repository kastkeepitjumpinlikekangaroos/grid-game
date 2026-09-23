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
  val SHURIKEN: Byte = 18
  val POISON_DART: Byte = 19
  val CHAIN_BOLT: Byte = 20
  val LOCKDOWN_CHAIN: Byte = 21
  val SNARE_MINE: Byte = 22
  val KATANA: Byte = 23
  val SWORD_WAVE: Byte = 24
  val PLAGUE_BOLT: Byte = 25
  val MIASMA: Byte = 26
  val BLIGHT_BOMB: Byte = 27
  val BLOOD_FANG: Byte = 28
  val BLOOD_SIPHON: Byte = 29
  val BAT_SWARM: Byte = 30

  // Batch 1: Elemental
  val FLAME_BOLT: Byte = 31
  val FROST_SHARD: Byte = 32
  val LIGHTNING: Byte = 33
  val CHAIN_LIGHTNING: Byte = 34
  val THUNDER_STRIKE: Byte = 35
  val BOULDER: Byte = 36
  val SEISMIC_SLAM: Byte = 37
  val WIND_BLADE: Byte = 38
  val MAGMA_BALL: Byte = 39
  val ERUPTION: Byte = 40
  val FROST_TRAP: Byte = 41
  val SAND_SHOT: Byte = 42
  val SAND_BLAST: Byte = 43
  val THORN: Byte = 44
  val VINE_WHIP: Byte = 45
  val THORN_WALL: Byte = 46
  val INFERNO_BLAST: Byte = 47
  val GLACIER_SPIKE: Byte = 48
  val MUD_GLOB: Byte = 49
  val MUD_BOMB: Byte = 50
  val EMBER_SHOT: Byte = 51
  val AVALANCHE_CRUSH: Byte = 52

  // Batch 2: Undead/Dark
  val DEATH_BOLT: Byte = 53
  val RAISE_DEAD: Byte = 54
  val BONE_AXE: Byte = 55
  val BONE_THROW: Byte = 56
  val WAIL: Byte = 57
  val SOUL_DRAIN: Byte = 58
  val CLAW_SWIPE: Byte = 59
  val DEVOUR: Byte = 60
  val SCYTHE: Byte = 61
  val REAP: Byte = 62
  val SHADOW_BOLT: Byte = 63
  val CURSED_BLADE: Byte = 64
  val LIFE_DRAIN: Byte = 65
  val SHOVEL: Byte = 66
  val HEAD_THROW: Byte = 67
  val BANDAGE_WHIP: Byte = 68
  val CURSE: Byte = 69

  // Batch 3: Medieval/Fantasy
  val HOLY_BLADE: Byte = 70
  val HOLY_BOLT: Byte = 71
  val ARROW: Byte = 72
  val POISON_ARROW: Byte = 73
  val SONIC_WAVE: Byte = 74
  val SONIC_BOOM: Byte = 75
  val FIST: Byte = 76
  val SMITE: Byte = 77
  val CHARM: Byte = 78
  val CARD: Byte = 79

  // Batch 4: Sci-Fi/Tech
  val DATA_BOLT: Byte = 80
  val VIRUS: Byte = 81
  val LASER: Byte = 82
  val GRAVITY_BALL: Byte = 83
  val GRAVITY_WELL: Byte = 84
  val TESLA_COIL: Byte = 85
  val NANO_BOLT: Byte = 86
  val VOID_BOLT: Byte = 87
  val RAILGUN: Byte = 88
  val CLUSTER_BOMB: Byte = 89

  // Batch 5: Nature/Beast
  val VENOM_BOLT: Byte = 90
  val WEB_SHOT: Byte = 91
  val STINGER: Byte = 92
  val ACID_BOMB: Byte = 93

  // AoE Root projectiles
  val SEISMIC_ROOT: Byte = 94
  val ROOT_GROWTH: Byte = 95
  val WEB_TRAP: Byte = 96
  val TREMOR_SLAM: Byte = 97
  val ENTANGLE: Byte = 98
  val STONE_GAZE: Byte = 99
  val INK_SNARE: Byte = 100
  val GRAVITY_LOCK: Byte = 101

  // Character-specific
  val KNIFE: Byte = 102
  val STING: Byte = 103
  val HAMMER: Byte = 104
  val HORN: Byte = 105
  val MYSTIC_BOLT: Byte = 106
  val PETRIFY: Byte = 107
  val GRAB: Byte = 108
  val JAW: Byte = 109
  val TONGUE: Byte = 110
  val ACID_FLASK: Byte = 111

  // Roster audit: new differentiation projectiles
  val BOOMERANG_BLADE: Byte = 112
  val VORTEX_BOMB: Byte = 113
  val CHAIN_LIGHTNING_FORK: Byte = 114
  val SNIPER_BEAM: Byte = 115
  val MOMENTUM_STRIKE: Byte = 116
  val LEECH_BOLT: Byte = 117
  val RICOCHET_SHARD: Byte = 118
  val FLAME_WAVE: Byte = 119
  val POISON_CLOUD: Byte = 120
  val BONE_BOOMERANG: Byte = 121
  val GRAVITY_LANCE: Byte = 122
  val SHADOW_HAUNT: Byte = 123
  val CHARGE_FIST: Byte = 124
  val ACID_SPRAY: Byte = 125
  val ECHO_BOLT: Byte = 126
  val FLAME_TRAIL: Byte = 127
  // Use Byte range carefully — these are signed (-128 to 127)
  // IDs 128+ must use negative bytes but & 0xFF handles lookup
  val STAR_BOLT: Byte = -128    // 128
  val RUNE_BOLT: Byte = -127    // 129
  val SOUL_HARVEST: Byte = -126 // 130
  val OVERCLOCK_BEAM: Byte = -125 // 131
  val NAPALM_STRIKE: Byte = -124  // 132
  val THROWN_BOULDER: Byte = -123  // 133
  val EYE_BEAM: Byte = -122       // 134

  // DPS balance variants (same visual, different damage)
  val SOUL_BOLT_HEAVY: Byte = -121   // 135
  val FROST_SHARD_LIGHT: Byte = -120 // 136
  val FLAME_BOLT_HEAVY: Byte = -119  // 137
  val FLAME_BOLT_LIGHT: Byte = -118  // 138
  val BULLET_HEAVY: Byte = -117      // 139
  val BULLET_LIGHT: Byte = -116      // 140
  val SONIC_WAVE_HEAVY: Byte = -115  // 141
  val SONIC_WAVE_MED: Byte = -114    // 142
  val THORN_LIGHT: Byte = -113       // 143
  val LASER_HEAVY: Byte = -112       // 144
  val LASER_LIGHT: Byte = -111       // 145
  val ARROW_HEAVY: Byte = -110       // 146
  val ARROW_LIGHT: Byte = -109       // 147
  val HOLY_BOLT_HEAVY: Byte = -108   // 148
  val VENOM_BOLT_LIGHT: Byte = -107  // 149

  // Plan 5a: the melee and skirmisher kits (variants reuse their base type's renderer)
  val SHOCKWAVE: Byte = -106          // 150: Crusader's Shield Bash: a knockback slam
  val ICE_QUAKE: Byte = -105          // 151: Avalanche's Ice Quake: a freezing slam
  val HOWL: Byte = -104               // 152: Wolf's Howl: a wide slowing slam
  val FERAL_ROAR: Byte = -103         // 153: Shapeshifter's Feral Roar: a slowing slam
  val EARTHSPLITTER: Byte = -102      // 154: Barbarian's travelling fissure: roots
  val GRASPING_DEAD: Byte = -101      // 155: Gravedigger's grasping hands: roots
  val DEATH_GRIP: Byte = -100         // 156: Death Knight's pull
  val TALON_GRAB: Byte = -99          // 157: Griffin's pull
  val PARALYTIC_STING: Byte = -98     // 158: Scorpion's stun
  val HAMMER_THROW: Byte = -97        // 159: Blacksmith's one heavy hammer: stuns
  val MEAT_HOOK: Byte = -96           // 160: Chef's pull
  val FLAMBE: Byte = -95              // 161: Chef's short-range exploding burn
  val HOLY_NOVA: Byte = -94           // 162: Paladin's Holy Nova bolts: HOLY_BOLT that stuns
  val AXE_SPIN: Byte = -93            // 163: Berserker's Axe Spin: AXE that slows
  val VENOM_DART: Byte = -92          // 164: Assassin's Poison Dart: poisons and slows
  val TOXIC_SHURIKEN: Byte = -91      // 165: Rogue's Knife Spray: poisons and slows
  val BLOOD_FRENZY: Byte = -90        // 166: Fenrir's Blood Frenzy: drains and slows
  val FLURRY: Byte = -89              // 167: Monk's Flurry: FIST that stuns
  val ROOTING_BOULDER: Byte = -88     // 168: Cyclops' Boulder Fan: BOULDER that roots
  val EMBER_FAN: Byte = -87           // 169: Ember's Ember Fan: burns and slows
  // Plan 5a: primaries given an identity of their own
  val FENRIR_CLAW: Byte = -86         // 170: CLAW_SWIPE that drains
  val GHOUL_CLAW: Byte = -85          // 171: CLAW_SWIPE that slows
  val SHARK_CLAW: Byte = -84          // 172: CLAW_SWIPE that bleeds (a poison)
  val ICE_BOULDER: Byte = -83         // 173: Avalanche's BOULDER: slows
  val DRAIN_BLADE: Byte = -82         // 174: Revenant's CURSED_BLADE: drains
  val CHILL_BLADE: Byte = -81         // 175: Death Knight's CURSED_BLADE: slows
}

class Projectile(
    val id: Int,
    val ownerId: UUID,
    private var x: Float,
    private var y: Float,
    private var _dx: Float,
    private var _dy: Float,
    val colorRGB: Int,
    val chargeLevel: Int = 0,
    val projectileType: Byte = ProjectileType.NORMAL
) {

  private var distanceTraveled: Float = 0f

  val speedMultiplier: Float = ProjectileDef.get(projectileType).effectiveSpeed(chargeLevel)

  // Pierce tracking: set of player IDs already hit (prevents double-hits)
  private val _hitPlayers = scala.collection.mutable.Set[UUID]()
  // Boomerang state
  private var _returning: Boolean = false
  // Ricochet state
  private var _remainingBounces: Int = ProjectileDef.get(projectileType).ricochetCount
  // Cached speed (magnitude of velocity vector) to avoid per-tick sqrt
  private var _speed: Float = math.sqrt(_dx * _dx + _dy * _dy).toFloat
  // Whether the server has flown it yet. It is spawned a cell out from where it was fired, and
  // its first tick checks that hop as well, so it can't start on the far side of a barrier.
  private var _flown: Boolean = false

  def dx: Float = _dx
  def dy: Float = _dy

  def getX: Float = x

  def getY: Float = y

  // Terrain cell. Tiles are drawn centred on integer coordinates — tile (c, r) covers
  // [c - 0.5, c + 0.5) — so that is the cell a projectile occupies as far as walls and map
  // edges are concerned. Truncating (x.toInt) put every wall half a tile down-screen of where
  // it is drawn: shots heading toward the camera sank halfway into wall blocks before
  // stopping, shots heading away stopped half a tile short, and at the bottom edges of the
  // map projectiles flew half a tile out over the void.
  def getCellX: Int = Math.floor(x + 0.5f).toInt

  def getCellY: Int = Math.floor(y + 0.5f).toInt

  def getDistanceTraveled: Float = distanceTraveled

  def hitPlayers: scala.collection.mutable.Set[UUID] = _hitPlayers

  def isReturning: Boolean = _returning

  def setReturning(r: Boolean): Unit = { _returning = r }

  def remainingBounces: Int = _remainingBounces

  def resetDistanceTraveled(): Unit = { distanceTraveled = 0f }

  def hasFlown: Boolean = _flown

  def markFlown(): Unit = { _flown = true }

  /** Reverse direction (for boomerang). */
  def reverseDirection(): Unit = {
    _dx = -_dx
    _dy = -_dy
  }

  /** Reflect off a wall. Returns true if reflection was applied. */
  def ricochet(world: WorldData): Boolean = {
    if (_remainingBounces <= 0) return false
    _remainingBounces -= 1

    val curX = getCellX
    val curY = getCellY

    // Determine which wall was hit by checking which adjacent cell
    // (behind us on each axis) is walkable
    val fromX = if (_dx > 0) curX - 1 else if (_dx < 0) curX + 1 else curX
    val fromY = if (_dy > 0) curY - 1 else if (_dy < 0) curY + 1 else curY
    val hitX = fromX != curX && world.isTileWalkable(fromX, curY)
    val hitY = fromY != curY && world.isTileWalkable(curX, fromY)

    if (hitX && hitY) {
      // Corner: reverse both
      _dx = -_dx
      _dy = -_dy
    } else if (hitX) {
      // Snap back to the walkable side of the vertical wall (its faces are at curX +/- 0.5)
      if (_dx > 0) x = curX.toFloat - 0.51f
      else x = curX.toFloat + 0.51f
      // 90° turn away from vertical wall
      val oldDx = _dx
      val oldDy = _dy
      if ((oldDx > 0) == (oldDy > 0)) {
        _dx = -oldDy; _dy = oldDx
      } else {
        _dx = oldDy; _dy = -oldDx
      }
    } else if (hitY) {
      // Snap back to the walkable side of the horizontal wall (its faces are at curY +/- 0.5)
      if (_dy > 0) y = curY.toFloat - 0.51f
      else y = curY.toFloat + 0.51f
      // 90° turn away from horizontal wall
      val oldDx = _dx
      val oldDy = _dy
      if ((oldDx > 0) == (oldDy > 0)) {
        _dx = oldDy; _dy = -oldDx
      } else {
        _dx = -oldDy; _dy = oldDx
      }
    } else {
      // Fallback: reverse both
      _dx = -_dx
      _dy = -_dy
    }
    _speed = math.sqrt(_dx * _dx + _dy * _dy).toFloat
    true
  }

  /** Move one sub-step (fractional tick). */
  def moveStep(fraction: Float): Unit = {
    x += _dx * speedMultiplier * fraction
    y += _dy * speedMultiplier * fraction
    distanceTraveled += _speed * speedMultiplier * fraction
  }

  def move(): Unit = {
    x += _dx * speedMultiplier
    y += _dy * speedMultiplier
    distanceTraveled += _speed * speedMultiplier
  }

  def isOutOfBounds(world: WorldData): Boolean = {
    val cellX = getCellX
    val cellY = getCellY
    cellX < 0 || cellX >= world.width || cellY < 0 || cellY >= world.height
  }

  /** Terrain only: the opening divider between the teams is not a wall, and is judged on its own
    * line by ProjectileManager so that a type flying over walls is stopped by it too. */
  def hitsNonWalkable(world: WorldData): Boolean = {
    !world.isTileWalkable(getCellX, getCellY)
  }

  def hitsFence(world: WorldData): Boolean = {
    world.getTile(getCellX, getCellY) == Tile.Fence
  }

  def hitsPlayer(player: Player): Boolean = {
    if (player.getId.equals(ownerId)) return false
    if (_hitPlayers.contains(player.getId)) return false
    // Killed earlier this tick: a body doesn't stop the next shot
    if (player.isDead) return false
    val pDef = ProjectileDef.get(projectileType)
    if (pDef.passesThroughPlayers) return false
    Projectile.withinPlayer(x, y, player, pDef.hitRadius)
  }

  /** Update position and velocity in-place (used by client for MOVE packets to avoid allocation). */
  def updatePosition(newX: Float, newY: Float, newDx: Float, newDy: Float): Unit = {
    x = newX
    y = newY
    _dx = newDx
    _dy = newDy
    _speed = math.sqrt(newDx * newDx + newDy * newDy).toFloat
  }

  override def toString: String = {
    s"Projectile{id=$id, owner=${ownerId.toString.substring(0, 8)}, pos=($x, $y), vel=(${_dx}, ${_dy})}"
  }
}

object Projectile {
  /**
   * Is the point (x, y) within `radius` of the player? A player stands at the centre of their
   * cell, (x, y) exactly, which is where they are drawn and where their shots start; tiles are
   * centred on integer coordinates (see getCellX). Hits used to be measured from (x + 0.5,
   * y + 0.5), the cell's far corner: shots from one side connected a cell sooner than from the
   * other, and blasts and slams were centred half a cell off their caster.
   */
  def withinPlayer(x: Float, y: Float, player: Player, radius: Float): Boolean = {
    val pos = player.getPosition
    val dx = x - pos.getX
    val dy = y - pos.getY
    dx * dx + dy * dy <= radius * radius
  }

  /** Distance from (x, y) to the centre of the player's cell. */
  def distanceToPlayer(x: Float, y: Float, player: Player): Float = {
    val pos = player.getPosition
    val dx = x - pos.getX
    val dy = y - pos.getY
    math.sqrt(dx * dx + dy * dy).toFloat
  }
}
