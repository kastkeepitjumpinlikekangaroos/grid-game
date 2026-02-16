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

  def dx: Float = _dx
  def dy: Float = _dy

  def getX: Float = x

  def getY: Float = y

  def getCellX: Int = x.toInt

  def getCellY: Int = y.toInt

  def getDistanceTraveled: Float = distanceTraveled

  def hitPlayers: scala.collection.mutable.Set[UUID] = _hitPlayers

  def isReturning: Boolean = _returning

  def setReturning(r: Boolean): Unit = { _returning = r }

  def remainingBounces: Int = _remainingBounces

  def resetDistanceTraveled(): Unit = { distanceTraveled = 0f }

  /** Reverse direction (for boomerang). */
  def reverseDirection(): Unit = {
    _dx = -_dx
    _dy = -_dy
  }

  /** Reflect off a wall. Returns true if reflection was applied. */
  def ricochet(world: WorldData): Boolean = {
    if (_remainingBounces <= 0) return false
    _remainingBounces -= 1

    val nextXonly = (x + _dx * speedMultiplier).toInt
    val nextYonly = (y + _dy * speedMultiplier).toInt
    val curX = getCellX
    val curY = getCellY

    val hitX = nextXonly != curX && !world.isWalkable(nextXonly, curY)
    val hitY = nextYonly != curY && !world.isWalkable(curX, nextYonly)

    if (hitX && hitY) {
      _dx = -_dx
      _dy = -_dy
    } else if (hitX) {
      _dx = -_dx
    } else if (hitY) {
      _dy = -_dy
    } else {
      // Both axes hit at once — just reverse both
      _dx = -_dx
      _dy = -_dy
    }
    true
  }

  /** Move one sub-step (fractional tick). */
  def moveStep(fraction: Float): Unit = {
    x += _dx * speedMultiplier * fraction
    y += _dy * speedMultiplier * fraction
    distanceTraveled += math.sqrt(_dx * _dx + _dy * _dy).toFloat * speedMultiplier * fraction
  }

  def move(): Unit = {
    x += _dx * speedMultiplier
    y += _dy * speedMultiplier
    distanceTraveled += math.sqrt(_dx * _dx + _dy * _dy).toFloat * speedMultiplier
  }

  def isOutOfBounds(world: WorldData): Boolean = {
    val cellX = getCellX
    val cellY = getCellY
    cellX < 0 || cellX >= world.width || cellY < 0 || cellY >= world.height
  }

  def hitsNonWalkable(world: WorldData): Boolean = {
    !world.isWalkable(getCellX, getCellY)
  }

  def hitsFence(world: WorldData): Boolean = {
    world.getTile(getCellX, getCellY) == Tile.Fence
  }

  def hitsPlayer(player: Player): Boolean = {
    if (player.getId.equals(ownerId)) return false
    if (_hitPlayers.contains(player.getId)) return false
    val pDef = ProjectileDef.get(projectileType)
    if (pDef.passesThroughPlayers) return false
    val pos = player.getPosition
    val ddx = x - (pos.getX + 0.5f)
    val ddy = y - (pos.getY + 0.5f)
    ddx * ddx + ddy * ddy <= pDef.hitRadius * pDef.hitRadius
  }

  override def toString: String = {
    s"Projectile{id=$id, owner=${ownerId.toString.substring(0, 8)}, pos=($x, $y), vel=(${_dx}, ${_dy})}"
  }
}
