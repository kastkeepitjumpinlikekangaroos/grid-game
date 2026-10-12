package com.gridgame.server.game

import com.gridgame.common.model.Cast
import com.gridgame.common.model.Player
import com.gridgame.common.model.Projectile
import com.gridgame.common.model.ProjectileDef
import com.gridgame.common.model.TeamDivider
import com.gridgame.common.model.Tile
import com.gridgame.common.model.WorldData

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import scala.collection.mutable.ArrayBuffer
import scala.jdk.CollectionConverters._

sealed trait ProjectileEvent
case class ProjectileMoved(projectile: Projectile) extends ProjectileEvent
// flyingOn: the projectile pierced this player and keeps going
case class ProjectileHit(projectile: Projectile, targetId: UUID, flyingOn: Boolean = false) extends ProjectileEvent
// Only the blow that killed the target: a player already dead is never hit, so never killed twice
case class ProjectileKill(projectile: Projectile, targetId: UUID, flyingOn: Boolean = false) extends ProjectileEvent
// damage: what the splash took off this victim, which is not the projectile's own damage (a
// ground slam's is 0). held: the splash froze, stunned or rooted the target
case class ProjectileAoEHit(projectile: Projectile, targetId: UUID, damage: Int, held: Boolean = false) extends ProjectileEvent
case class ProjectileAoEKill(projectile: Projectile, targetId: UUID, damage: Int) extends ProjectileEvent
case class ProjectileDespawned(projectile: Projectile) extends ProjectileEvent
case class ProjectileAoE(projectile: Projectile) extends ProjectileEvent
// Stopped by barrierOwner's raised barrier, where it met it (the projectile's position).
// A null owner is the opening divider between the teams, which belongs to nobody.
case class ProjectileBlocked(projectile: Projectile, barrierOwner: UUID) extends ProjectileEvent

/**
 * Every projectile in flight in one match, and the projectile tick ([[tick]], every
 * PROJECTILE_SPEED_MS): each moves in half-cell sub-steps and is stopped by the first thing it
 * meets — the opening divider, a raised barrier (BarrierSnapshot), the end of its range, the edge
 * of the map or terrain — or hits a player (PlayerGrid). What happened comes back as
 * ProjectileEvents, for GameInstance to act on and tell everyone.
 */
class ProjectileManager(registry: ClientRegistry, isTeammate: (UUID, UUID) => Boolean = (_, _) => false,
                       dividerOf: () => TeamDivider = () => null) {
  private val projectiles = new ConcurrentHashMap[Int, Projectile]()
  // What the tick's projectiles can hit, and the barriers raised against them: both rebuilt at
  // the start of every tick
  private val grid = new PlayerGrid
  private val raisedBarriers = new BarrierSnapshot(isTeammate)
  private val nextId = new AtomicInteger(1)
  // Per-player active projectile count (avoids O(n) iteration on every spawn)
  private val playerProjectileCount = new ConcurrentHashMap[UUID, AtomicInteger]()

  // Async gauge: projectiles in flight. Held as AutoCloseable so the callback can be
  // unregistered when the instance ends — otherwise the gauge keeps reporting stale
  // sizes against this manager (and across instances, callbacks accumulate).
  private val projectilesActiveGauge = com.gridgame.common.observability.Telemetry.meter("com.gridgame.projectiles")
    .gaugeBuilder("gridgame.projectiles.active")
    .setDescription("Projectiles currently in flight")
    .setUnit("{projectile}")
    .ofLongs()
    .buildWithCallback { obs =>
      obs.record(projectiles.size().toLong, io.opentelemetry.api.common.Attributes.empty())
    }

  // The opening divider as of this tick, or null: read once, since it is the same wall for every
  // projectile and every blast in the tick (ProjectileManager's own thread, like the grid).
  private var tickDivider: TeamDivider = null

  /** Is the opening divider between (x, y) and the player? Nothing reaches through it — a hit
   *  radius reaches a cell and a half past the wall, and a blast several. */
  private def dividerBetween(x: Float, y: Float, player: Player): Boolean = {
    val d = tickDivider
    if (d == null) return false
    val pos = player.getPosition
    d.crosses(x, y, pos.getX.toFloat, pos.getY.toFloat)
  }

  /**
   * Stop the projectile where the path from (ax, ay) to (bx, by) met the opening divider, a hair
   * short of it, and say so. Returns whether it did. Everything is stopped by it — an ally's shot,
   * the caster's own and a type that flies over walls — because for these thirty seconds the two
   * halves of the map simply cannot touch each other.
   */
  private def stopOnDivider(projectile: Projectile, pDef: ProjectileDef, ax: Float, ay: Float,
                            bx: Float, by: Float, events: ArrayBuffer[ProjectileEvent],
                            toRemove: ArrayBuffer[Int]): Boolean = {
    val d = tickDivider
    if (d == null) return false
    val hit = d.crossing(ax, ay, bx, by)
    if (hit < 0f) return false
    val px = bx - ax
    val py = by - ay
    val len = Math.sqrt(px * px + py * py).toFloat
    val back = if (len > 1e-6f) BarrierSnapshot.STANDOFF / len else 0f
    val t = Math.max(0f, hit - back)
    projectile.updatePosition(ax + px * t, ay + py * t, projectile.dx, projectile.dy)
    toRemove += projectile.id
    if (pDef.isExplosive) events += ProjectileAoE(projectile)
    else events += ProjectileBlocked(projectile, null)
    true
  }

  /**
   * Does a barrier shelter `player` from a blast `ownerId` set off at (x, y)? Blasts, splashes,
   * slams and vortices centred in front of a barrier don't reach anyone it stands between them
   * and. Like forEachNearbyPlayer, this reads the tick's snapshot: call it only from the
   * projectile tick's thread (GameInstance's event handling).
   */
  def shelteredFromBlast(ownerId: UUID, x: Float, y: Float, player: Player): Boolean = {
    if (dividerBetween(x, y, player)) return true
    val mask = raisedBarriers.against(ownerId, passesThroughWalls = false, bodies = false)
    mask != 0L && raisedBarriers.shelters(mask, x, y, player)
  }

  /** Put a stopped projectile at (x, y) and resolve it: an explosive goes off there, as it would
   *  against a wall; anything else is stopped. Pierce, ricochet and boomerang included. */
  private def stopAtBarrier(projectile: Projectile, pDef: ProjectileDef, barrier: Int, x: Float, y: Float,
                            events: ArrayBuffer[ProjectileEvent], toRemove: ArrayBuffer[Int]): Unit = {
    projectile.updatePosition(x, y, projectile.dx, projectile.dy)
    toRemove += projectile.id
    if (pDef.isExplosive) events += ProjectileAoE(projectile)
    else events += ProjectileBlocked(projectile, raisedBarriers.owner(barrier))
  }

  /** Stop the projectile where the path from (ax, ay) to (bx, by) first met one of `mask`'s
   *  barriers, a hair short of it. Returns whether it did. */
  private def stopOnPath(projectile: Projectile, pDef: ProjectileDef, mask: Long, ax: Float, ay: Float,
                         bx: Float, by: Float, events: ArrayBuffer[ProjectileEvent],
                         toRemove: ArrayBuffer[Int]): Boolean = {
    val hit = raisedBarriers.firstCrossing(mask, ax, ay, bx, by)
    if (hit < 0) return false
    val px = bx - ax
    val py = by - ay
    val len = Math.sqrt(px * px + py * py).toFloat
    val back = if (len > 1e-6f) BarrierSnapshot.STANDOFF / len else 0f
    val t = Math.max(0f, raisedBarriers.crossingT - back)
    stopAtBarrier(projectile, pDef, hit, ax + px * t, ay + py * t, events, toRemove)
    true
  }

  /**
   * Does terrain that would stop this projectile stand between it at (x, y) and the player? A hit
   * radius reaches 1.8 to 3.5 cells, past a wall right in front of a player, so a shot that stopped
   * at a fence — drawn stopping there — hit whoever stood behind it: from a cell behind, 26 of the
   * roster's 48 single-target shots did. The same rule a barrier's shelter keeps
   * (BarrierSnapshot.shelters): the straight way from the shot to the player has to be clear. It is
   * looked along in quarter cells, every cell but the two ends': the projectile's is open or it
   * would have stopped, and someone standing in a fence put down on them is still hit from outside
   * it.
   */
  private def terrainBetween(world: WorldData, pDef: ProjectileDef, x: Float, y: Float, player: Player): Boolean = {
    val pos = player.getPosition
    val dx = pos.getX - x
    val dy = pos.getY - y
    val n = Math.ceil(Math.sqrt(dx * dx + dy * dy) / ProjectileManager.SIGHT_STEP).toInt
    val fromCx = Math.floor(x + 0.5f).toInt
    val fromCy = Math.floor(y + 0.5f).toInt
    var i = 1
    while (i < n) {
      val t = i.toFloat / n
      val cx = Math.floor(x + dx * t + 0.5f).toInt
      val cy = Math.floor(y + dy * t + 0.5f).toInt
      if ((cx != fromCx || cy != fromCy) && (cx != pos.getX || cy != pos.getY) &&
          world.stopsProjectile(cx, cy) && (!pDef.passesThroughWalls || world.getTile(cx, cy) == Tile.Fence)) return true
      i += 1
    }
    false
  }

  /** Is the projectile off the map, or in terrain that stops it: anything nobody can walk on but
    * water, and for a type that flies over walls only a fence? */
  private def stoppedHere(projectile: Projectile, pDef: ProjectileDef, world: WorldData): Boolean =
    projectile.isOutOfBounds(world) ||
      (projectile.hitsTerrain(world) && (!pDef.passesThroughWalls || projectile.hitsFence(world)))

  /** Resolve a projectile where it is, if that is off the map or in terrain that stops it: it ends
    * there, an explosive going off, unless it ricochets off a wall and flies on. Returns whether it
    * ended. */
  private def stopsWhereItIs(projectile: Projectile, pDef: ProjectileDef, world: WorldData,
                             events: ArrayBuffer[ProjectileEvent], toRemove: ArrayBuffer[Int]): Boolean = {
    if (!stoppedHere(projectile, pDef, world)) return false
    if (!projectile.isOutOfBounds(world) && projectile.remainingBounces > 0 && !projectile.hitsFence(world)) {
      // Ricochet: bounce off walls instead of despawning
      projectile.ricochet(world)
      false
    } else {
      toRemove += projectile.id
      events += (if (pDef.isExplosive) ProjectileAoE(projectile) else ProjectileDespawned(projectile))
      true
    }
  }

  /** A barrier that moved or turned since the last tick can have been carried onto the projectile,
    * which hasn't moved yet this tick: if one was, it stops on the barrier's face. Returns whether
    * it stopped. */
  private def sweptByBarrier(projectile: Projectile, pDef: ProjectileDef, mask: Long,
                             events: ArrayBuffer[ProjectileEvent], toRemove: ArrayBuffer[Int]): Boolean = {
    val i = raisedBarriers.sweptOver(mask, projectile.getX, projectile.getY)
    if (i < 0) return false
    stopAtBarrier(projectile, pDef, i, raisedBarriers.faceX, raisedBarriers.faceY, events, toRemove)
    true
  }

  /** Iterate players within `radius` of (x, y), by the tick's grid (PlayerGrid). The grid belongs to
   *  the projectile tick, which rebuilds it in place: call this only from that thread
   *  (GameInstance's event handling). The bots used to call it from theirs and read cells mid-rebuild. */
  def forEachNearbyPlayer(x: Float, y: Float, radius: Float)(fn: Player => Unit): Unit =
    grid.forEachNearbyPlayer(x, y, radius)(fn)

  private val MAX_PROJECTILES_PER_PLAYER = 30

  /** A projectile fired from (x, y) along (dx, dy). `cast` is the cast it is one of, which a fan's
    * projectiles share (see Cast); without one it is a cast of its own. */
  def spawnProjectile(ownerId: UUID, x: Int, y: Int, dx: Float, dy: Float, colorRGB: Int, chargeLevel: Int = 0,
                      projectileType: Byte = com.gridgame.common.model.ProjectileType.NORMAL,
                      cast: Cast = null): Projectile = {
    // Enforce per-player active projectile cap to prevent memory exhaustion (O(1) check)
    val counter = playerProjectileCount.computeIfAbsent(ownerId, _ => new AtomicInteger(0))
    if (counter.get() >= MAX_PROJECTILES_PER_PLAYER) return null

    val id = nextId.getAndIncrement() & 0x7FFFFFFF // Ensure positive IDs after overflow
    // Spawn the projectile one cell ahead in the velocity direction
    val spawnX = x.toFloat + dx
    val spawnY = y.toFloat + dy
    val projectile = new Projectile(id, ownerId, spawnX, spawnY, dx, dy, colorRGB, chargeLevel, projectileType)
    projectile.setCast(if (cast != null) cast else new Cast(x.toFloat, y.toFloat))
    projectiles.put(id, projectile)
    counter.incrementAndGet()
    projectile
  }

  def tick(world: WorldData): Seq[ProjectileEvent] = {
    val events = ArrayBuffer[ProjectileEvent]()
    val toRemove = ArrayBuffer[Int]()

    grid.rebuild(registry.getPlayerValues)
    raisedBarriers.rebuild(registry.getPlayerValues)
    tickDivider = { val d = dividerOf(); if (d != null && d.up) d else null }

    projectiles.values().asScala.foreach { projectile =>
      val owner = registry.get(projectile.ownerId)
      val steps = if (owner != null && owner.hasGemBoost) 2 else 1
      var resolved = false
      val pDef = ProjectileDef.get(projectile.projectileType)
      // The raised barriers this projectile can't pass
      val barriers = raisedBarriers.against(projectile.ownerId, pDef.passesThroughWalls, bodies = true)

      // Sub-step movement so projectiles can't skip over terrain that stops them.
      val movePerTick = math.sqrt(projectile.dx * projectile.dx + projectile.dy * projectile.dy).toFloat * projectile.speedMultiplier
      val subSteps = math.ceil(movePerTick / Projectile.MAX_SUB_STEP).toInt.max(1)
      val fraction = 1.0f / subSteps

      val fresh = !projectile.hasFlown
      projectile.markFlown()
      if (fresh) {
        // It was spawned a cell out from where it was fired: an enemy pressed up against a
        // barrier (or the divider) would otherwise start their shot on the far side of it
        val fromX = projectile.getX - projectile.dx
        val fromY = projectile.getY - projectile.dy
        resolved = stopOnDivider(projectile, pDef, fromX, fromY, projectile.getX, projectile.getY, events, toRemove) ||
          (barriers != 0L && stopOnPath(projectile, pDef, barriers, fromX, fromY,
            projectile.getX, projectile.getY, events, toRemove))
        // ...and the cell it was put in, which no sub-step below looks at. A sub-step of exactly
        // half a cell carried it from there straight into the next, so a shot of 0.5 or 1.0 a
        // tick fired east or south flew through a one-cell wall (a fence) its shooter stood
        // against, for its whole range — while the aim lane and the bots' line of fire stopped it.
        // Not one that doesn't fly: a slam is put down on its caster and goes off where it lies
        if (!resolved && movePerTick > 0f) resolved = stopsWhereItIs(projectile, pDef, world, events, toRemove)
      } else if (barriers != 0L) {
        resolved = sweptByBarrier(projectile, pDef, barriers, events, toRemove)
      }

      var step = 0
      while (step < steps && !resolved) {
        var sub = 0
        while (sub < subSteps && !resolved) {
          val fromX = projectile.getX
          val fromY = projectile.getY
          projectile.moveStep(fraction)

          val maxRange = pDef.effectiveMaxRange(projectile.chargeLevel)
          if (stopOnDivider(projectile, pDef, fromX, fromY, projectile.getX, projectile.getY, events, toRemove)) {
            // The wall between the two halves: nothing at all crosses it while it is up
            resolved = true
          } else if (barriers != 0L && stopOnPath(projectile, pDef, barriers, fromX, fromY, projectile.getX, projectile.getY,
              events, toRemove)) {
            // Met a barrier on the way: checked first, since it stood somewhere along this step and
            // everything below is judged where the step ended
            resolved = true
          } else if (projectile.getDistanceTraveled >= maxRange) {
            // Boomerang: reverse direction at max range instead of despawning
            if (pDef.boomerang && !projectile.isReturning) {
              projectile.reverseDirection()
              projectile.setReturning(true)
              projectile.resetDistanceTraveled()
              projectile.hitPlayers.clear() // can hit players again on return
            } else {
              toRemove += projectile.id
              // AoE on max range (e.g. geyser, snare mine)
              pDef.aoeOnMaxRange.foreach { aoe =>
                events ++= applyAoEDamage(projectile, aoe.radius, aoe.damage, null, aoe.freezeDurationMs,
                  aoe.rootDurationMs, aoe.stunDurationMs)
              }
              if (pDef.isExplosive) {
                events += ProjectileAoE(projectile)
              } else {
                events += ProjectileDespawned(projectile)
              }
              resolved = true
            }
          } else if (stoppedHere(projectile, pDef, world)) {
            resolved = stopsWhereItIs(projectile, pDef, world, events, toRemove)
          } else {
            // The nearest of those it reaches. It was whichever the grid happened to list last: fired
            // east at someone a cell or two away, with someone else just behind them, the shot went
            // through the first untouched and struck the second
            var hitPlayer: Player = null
            var hitDistSq = Float.MaxValue
            grid.forEachNearby(projectile.getX, projectile.getY) { player =>
              // Not through a barrier: a hit radius reaches past one two cells out, so without this
              // a shot hit its holder, or whoever sheltered beside them, before it reached it.
              // Nor through a wall, for the same reason (terrainBetween)
              if (projectile.hitsPlayer(player) && !isTeammate(projectile.ownerId, player.getId) &&
                  (barriers == 0L || !raisedBarriers.shelters(barriers, projectile.getX, projectile.getY, player)) &&
                  !dividerBetween(projectile.getX, projectile.getY, player) &&
                  !terrainBetween(world, pDef, projectile.getX, projectile.getY, player)) {
                val pos = player.getPosition
                val ddx = pos.getX - projectile.getX
                val ddy = pos.getY - projectile.getY
                val distSq = ddx * ddx + ddy * ddy
                if (distSq < hitDistSq) {
                  hitDistSq = distSq
                  hitPlayer = player
                }
              }
            }

            if (hitPlayer != null) {
              // Explosive projectiles that explode on player hit (e.g. rocket)
              if (pDef.explodesOnPlayerHit) {
                toRemove += projectile.id
                events += ProjectileAoE(projectile)
                resolved = true
              } else {
                val damage = pDef.effectiveDamage(projectile.chargeLevel, projectile.getDistanceTraveled)
                val killed = hitPlayer.damage(damage)
                // Pierce: track hit player and continue if pierce count not exhausted. It used to
                // stop at hit number pierceCount, so the fourteen types with a pierce of 1 (Arrow,
                // Soul Bolt, Boomerang Blade...) never passed through anyone.
                projectile.hitPlayers += hitPlayer.getId
                val canPierce = pDef.piercesAfter(projectile.hitPlayers.size)

                if (!canPierce) {
                  toRemove += projectile.id
                }
                if (killed) {
                  events += ProjectileKill(projectile, hitPlayer.getId, flyingOn = canPierce)
                } else {
                  events += ProjectileHit(projectile, hitPlayer.getId, flyingOn = canPierce)
                }

                // AoE splash damage to nearby players (excluding the direct hit target)
                pDef.aoeOnHit.foreach { aoe =>
                  events ++= applyAoEDamage(projectile, aoe.radius, aoe.damage, hitPlayer.getId, aoe.freezeDurationMs,
                    aoe.rootDurationMs, aoe.stunDurationMs)
                }

                if (!canPierce) {
                  resolved = true
                }
              }
            }
          }
          sub += 1
        }
        step += 1
      }

      if (!resolved) {
        events += ProjectileMoved(projectile)
      }
    }

    // Remove despawned/hit projectiles and decrement per-player counters
    toRemove.foreach { id =>
      val removed = projectiles.remove(id)
      if (removed != null) {
        val cnt = playerProjectileCount.get(removed.ownerId)
        if (cnt != null) cnt.decrementAndGet()
      }
    }

    events.toSeq
  }

  def getProjectile(id: Int): Projectile = projectiles.get(id)

  def removeProjectile(id: Int): Unit = projectiles.remove(id)

  def getAll: Seq[Projectile] = projectiles.values().asScala.toSeq

  def size: Int = projectiles.size()

  /** Release all state and unregister the active-projectiles gauge callback. */
  def close(): Unit = {
    try projectilesActiveGauge.close() catch { case _: Throwable => () }
    projectiles.clear()
    playerProjectileCount.clear()
    grid.clear()
    raisedBarriers.clear()
    tickDivider = null
  }

  /** Visit every projectile in flight without building a collection. Safe from any thread; a
   *  projectile's position may be a tick old. */
  def forEachProjectile(fn: Projectile => Unit): Unit = {
    val it = projectiles.values().iterator()
    while (it.hasNext) fn(it.next())
  }

  /** Deal AoE damage to all players within radius of the projectile, excluding excludeId (the direct-hit target). */
  private def applyAoEDamage(projectile: Projectile, radius: Float, damage: Int, excludeId: UUID,
                             freezeDurationMs: Int = 0, rootDurationMs: Int = 0,
                             stunDurationMs: Int = 0): Seq[ProjectileEvent] = {
    val events = ArrayBuffer[ProjectileEvent]()
    val px = projectile.getX
    val py = projectile.getY
    // A barrier between the splash and a player shelters them from it
    val shelter = raisedBarriers.against(projectile.ownerId, passesThroughWalls = false, bodies = false)
    // Use pre-filtered hittable array (already excludes dead/shielded/phased)
    val players = grid.hittable
    val len = grid.hittableSize
    var i = 0
    while (i < len) {
      val player = players(i)
      i += 1
      // The snapshot is from the start of the tick: skip anyone killed since
      if (!player.isDead &&
          !player.getId.equals(projectile.ownerId) &&
          (excludeId == null || !player.getId.equals(excludeId)) &&
          !isTeammate(projectile.ownerId, player.getId) &&
          Projectile.withinPlayer(px, py, player, radius) &&
          (shelter == 0L || !raisedBarriers.shelters(shelter, px, py, player)) &&
          !dividerBetween(px, py, player)) {
        val killed = player.damage(damage)
        if (killed) {
          events += ProjectileAoEKill(projectile, player.getId, damage)
        } else if (!player.isDead) {
          val frozen = freezeDurationMs > 0 && player.tryFreeze(freezeDurationMs)
          val stunned = stunDurationMs > 0 && player.tryStun(stunDurationMs)
          val rooted = rootDurationMs > 0 && player.tryRoot(rootDurationMs)
          events += ProjectileAoEHit(projectile, player.getId, damage, held = frozen || stunned || rooted)
        }
      }
    }
    events.toSeq
  }
}

object ProjectileManager {
  /** How far apart the way from a projectile to a player it reaches is looked along for a wall
    * between them (terrainBetween): a quarter cell, so the corner of one isn't stepped over. */
  private val SIGHT_STEP = 0.25
}
