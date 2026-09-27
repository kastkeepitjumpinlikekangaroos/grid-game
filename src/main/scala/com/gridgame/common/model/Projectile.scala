package com.gridgame.common.model

import java.util.UUID

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

  /** Where this copy of it started: on the client, where it was fired from (its SPAWN) or first
    * seen (a MOVE that overtook the SPAWN). A rope with nobody left to run back to runs back to
    * here. The client flies its copies between the server's positions (NetProjectile), which also
    * keeps `distanceTraveled` as the server counts it. */
  val originX: Float = x
  val originY: Float = y

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

  /** The client's copy is flown by the client (NetProjectile), which sets how far it has come. */
  def setDistanceTraveled(d: Float): Unit = { distanceTraveled = d }

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

  /** Update position and heading in place. The client's copy is put where it is drawn this way,
    * every frame (NetProjectile.fly), and where it stopped once it has. */
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
