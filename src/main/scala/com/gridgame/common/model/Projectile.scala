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
    val projectileType: Byte = ProjectileType.NORMAL,
    // The character its owner is playing, as far as whoever made this copy knows (-1: not known).
    // The client draws a projectile as its thrower's: a Musician's sonic wave and a Bard's are not
    // the same thing (GLProjectileRenderers). The server has no use for it and leaves it unset.
    val ownerCharacterId: Byte = -1
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
  // The cast it was fired in, with the rest of a fan (server side; see Cast)
  @volatile private var _cast: Cast = _

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
  //
  // On the line between two cells it is in the one it is heading into (Projectile.cellEntered):
  // counted in the further one whichever way it flew, a shot heading east or south met a wall on
  // its face, and one heading west or north half a cell inside it, a sub-step later.
  def getCellX: Int = Projectile.cellEntered(x, _dx)

  def getCellY: Int = Projectile.cellEntered(y, _dy)

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

  /** The cast it was fired in (server side): null on the client, and on the server only for a
    * projectile made without ProjectileManager.spawnProjectile. */
  def cast: Cast = _cast

  def setCast(c: Cast): Unit = { _cast = c }

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
    // (behind us on each axis) is open to it — ground, or water it flew over
    val fromX = if (_dx > 0) curX - 1 else if (_dx < 0) curX + 1 else curX
    val fromY = if (_dy > 0) curY - 1 else if (_dy < 0) curY + 1 else curY
    val hitX = fromX != curX && !world.stopsProjectile(fromX, curY)
    val hitY = fromY != curY && !world.stopsProjectile(curX, fromY)

    if (hitX && hitY) {
      // Corner: reverse both
      _dx = -_dx
      _dy = -_dy
    } else if (hitX) {
      // Snap back to the open side of the vertical wall (its faces are at curX +/- 0.5)
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
      // Snap back to the open side of the horizontal wall (its faces are at curY +/- 0.5)
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

  /** Is it in terrain that stops a shot — anything nobody can walk on but water, which it flies
    * over ([[Tile.stopsProjectiles]])? Terrain only: the opening divider between the teams is not
    * a wall, and is judged on its own line by ProjectileManager so that a type flying over walls
    * is stopped by it too. */
  def hitsTerrain(world: WorldData): Boolean = {
    world.stopsProjectile(getCellX, getCellY)
  }

  def hitsFence(world: WorldData): Boolean = {
    world.getTile(getCellX, getCellY) == Tile.Fence
  }

  def hitsPlayer(player: Player): Boolean = {
    if (player.getId.equals(ownerId)) return false
    if (_hitPlayers.contains(player.getId)) return false
    // Killed earlier this tick: a body doesn't stop the next shot
    if (player.isDead) return false
    // Pushed by this cast already: the rest of a fan flies through them, rather than catching
    // them up where the push left them and hitting them again
    val c = _cast
    if (c != null && c.hasPushed(player.getId)) return false
    val pDef = ProjectileDef.get(projectileType)
    if (pDef.passesThroughPlayers) return false
    val behind = hitCentreBehind(pDef)
    if (behind != 0f && _speed > 0f)
      Projectile.withinPlayer(x - _dx / _speed * behind, y - _dy / _speed * behind, player, pDef.hitRadius)
    else Projectile.withinPlayer(x, y, player, pDef.hitRadius)
  }

  /**
   * How far behind it, along its flight, its hit circle is centred just now. A wave's circle trails
   * its leading edge and a melee swing's hangs back toward whoever swung it
   * (ProjectileDef.hitCentreBehind), though never back past where it is first checked, a sub-step out
   * from its spawn: round the spawn itself a wave caught players standing beside its caster, whom a
   * circle round its edge never reached. A boomerang coming back keeps its circle on its thrower's
   * side of it, which is ahead of it now (a negative answer), and brings it back the same way: let
   * go of at the turn, the circle would sit on the far end of the flight and reach its whole radius
   * past it.
   */
  private def hitCentreBehind(pDef: ProjectileDef): Float = {
    val want = pDef.hitCentreBehind
    if (want <= 0f) 0f
    else if (_returning)
      -Math.min(want, Math.max(0f, pDef.effectiveMaxRange(chargeLevel).toFloat - distanceTraveled - Projectile.MAX_SUB_STEP))
    else Math.min(want, Math.max(0f, distanceTraveled - Projectile.MAX_SUB_STEP))
  }

  /** The centre of its hit circle ([[hitsPlayer]]): where it is, or as far behind that as its type
    * trails it. What the projectile tick looks round for whom it strikes. */
  def hitCentreX: Float = {
    val behind = hitCentreBehind(ProjectileDef.get(projectileType))
    if (behind != 0f && _speed > 0f) x - _dx / _speed * behind else x
  }

  def hitCentreY: Float = {
    val behind = hitCentreBehind(ProjectileDef.get(projectileType))
    if (behind != 0f && _speed > 0f) y - _dy / _speed * behind else y
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

/**
 * One press of an attack and every projectile it fired: one for most attacks, five for a fan, eight
 * for a ring. What its projectiles do to a player is counted per cast, not per projectile, where a
 * fan is one wave: **a cast pushes a player once**, however many of its projectiles reach them, and
 * the rest of it then flies through them. Each projectile used to push on its own, straight on from
 * wherever the last push had left the player, so a fan of waves fired from close in threw its
 * victim anything from three cells to fifteen in the same tick, depending on how many of them
 * overlapped where they stood, and a fan of gusts up to twenty-five.
 *
 * `fromX`/`fromY` is where the cast was fired from, which a push drives its victims straight away
 * from: from wherever its caster had walked to by the time it landed, the same wave pushed its
 * victims one way or another. Server side, like the rest of a match's rules.
 */
final class Cast(val fromX: Float, val fromY: Float) {
  // Whom it has pushed, made at its first push: most casts never push anyone
  private var pushed: java.util.HashSet[UUID] = _

  /** Has this cast pushed the player? */
  def hasPushed(playerId: UUID): Boolean = synchronized {
    pushed != null && pushed.contains(playerId)
  }

  /** The cast pushes this player: true the first time, false if it already has. */
  def push(playerId: UUID): Boolean = synchronized {
    if (pushed == null) pushed = new java.util.HashSet[UUID]()
    pushed.add(playerId)
  }
}

object Projectile {
  /** The furthest a projectile moves between two looks at what it has hit: the server flies each
    * tick in sub-steps no longer (ProjectileManager), and the client finds walls along the same
    * ones (NetProjectile). */
  val MAX_SUB_STEP = 0.5f

  /** How near the line between two cells counts as on it: far more than the rounding of a
    * position added up a sub-step at a time, far less than anyone could see. */
  val ON_THE_LINE = 1e-3f

  /**
   * The cell a point moving along `heading` on this axis is in: tiles are centred on whole
   * coordinates, so cell c is [c - 0.5, c + 0.5], and a point on the line between two cells is in
   * the one it is heading into. A sub-step of half a cell puts a shot fired along a row or a column
   * on those lines every other step. Counted in the further cell whatever its heading
   * (floor(v + 0.5)), a shot heading east or south stopped on the face of a wall and one heading
   * west or north half a cell inside it, a sub-step later, where a blast then went off (found by
   * SymmetryTest). The server's collision and the client's flight (TerrainImpact) both ask this.
   */
  def cellEntered(v: Float, heading: Float): Int =
    if (heading < 0f) Math.ceil(v - 0.5f - ON_THE_LINE).toInt else Math.floor(v + 0.5f + ON_THE_LINE).toInt

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
