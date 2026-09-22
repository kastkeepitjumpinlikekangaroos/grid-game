package com.gridgame.server

import com.gridgame.common.model.Barrier
import com.gridgame.common.model.Player
import com.gridgame.common.model.Projectile
import com.gridgame.common.model.ProjectileDef
import com.gridgame.common.model.WorldData
import com.gridgame.common.protocol.ProjectileAction
import com.gridgame.common.protocol.ProjectilePacket

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
// Stopped by barrierOwner's raised barrier, where it met it (the projectile's position)
case class ProjectileBlocked(projectile: Projectile, barrierOwner: UUID) extends ProjectileEvent

/** Where a holder's barrier was on the tick it was last snapshotted. One per holder, reused. */
private final class BarrierPose {
  var x = 0f
  var y = 0f
  var cos = 0f
  var sin = 0f
  var tick = 0L
}

class ProjectileManager(registry: ClientRegistry, isTeammate: (UUID, UUID) => Boolean = (_, _) => false) {
  private val projectiles = new ConcurrentHashMap[Int, Projectile]()
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

  /** Pre-filtered snapshot of alive, hittable players rebuilt once per tick.
   *  Uses a pre-allocated array to avoid per-tick allocation. */
  private var hittablePlayers: Array[Player] = new Array[Player](64)
  private var hittableCount: Int = 0
  /** Spatial grid for efficient nearby-player lookups during collision detection.
   *  Uses a pre-allocated HashMap that is cleared each tick instead of reallocated. */
  private val gridCellSize = 4
  private val gridCells = new java.util.HashMap[Long, ArrayBuffer[Player]]()
  private val activeKeys = new ArrayBuffer[Long]()

  private def gridKey(cx: Int, cy: Int): Long = (cx.toLong << 32) | (cy.toLong & 0xFFFFFFFFL)

  /** Raised barriers, snapshotted once per tick into preallocated arrays: whose, where the holder
   *  stands, which way it faces, and where it was on the tick before if it was up then, so a
   *  barrier carried or swung onto a projectile stops it as surely as one the projectile flies
   *  into. A set of barriers is a bit mask over these slots. */
  private val MAX_BARRIERS = 64
  private val barrierOwner = new Array[UUID](MAX_BARRIERS)
  private val barrierX = new Array[Float](MAX_BARRIERS)
  private val barrierY = new Array[Float](MAX_BARRIERS)
  private val barrierCos = new Array[Float](MAX_BARRIERS)
  private val barrierSin = new Array[Float](MAX_BARRIERS)
  private val barrierMoved = new Array[Boolean](MAX_BARRIERS)
  private val barrierPrevX = new Array[Float](MAX_BARRIERS)
  private val barrierPrevY = new Array[Float](MAX_BARRIERS)
  private val barrierPrevCos = new Array[Float](MAX_BARRIERS)
  private val barrierPrevSin = new Array[Float](MAX_BARRIERS)
  private var barrierCount = 0
  private var tickCount = 0L
  private val barrierPoses = new java.util.HashMap[UUID, BarrierPose]()
  // Where the last crossing found by firstBarrierCrossing lay along its path (0-1)
  private var crossingT = 0f
  // A stopped projectile is left this far short of the barrier, on the side it came from: its
  // blast, if it has one, is then clearly in front of the barrier rather than on the line
  private val BARRIER_STANDOFF = 0.05f

  private def rebuildBarriers(allPlayers: java.util.Collection[Player]): Unit = {
    tickCount += 1
    var count = 0
    val iter = allPlayers.iterator()
    while (iter.hasNext && count < MAX_BARRIERS) {
      val player = iter.next()
      // A phased holder's barrier is as insubstantial as they are
      if (player.hasBarrier && !player.isDead && !player.isPhased) {
        val id = player.getId
        val pos = player.getPosition
        val x = pos.getX.toFloat
        val y = pos.getY.toFloat
        val angle = player.getBarrierAngle
        val cos = Math.cos(angle).toFloat
        val sin = Math.sin(angle).toFloat
        barrierOwner(count) = id
        barrierX(count) = x; barrierY(count) = y
        barrierCos(count) = cos; barrierSin(count) = sin
        var pose = barrierPoses.get(id)
        if (pose == null) { pose = new BarrierPose; barrierPoses.put(id, pose) }
        val moved = pose.tick == tickCount - 1 && (pose.x != x || pose.y != y || pose.cos != cos || pose.sin != sin)
        barrierMoved(count) = moved
        if (moved) {
          barrierPrevX(count) = pose.x; barrierPrevY(count) = pose.y
          barrierPrevCos(count) = pose.cos; barrierPrevSin(count) = pose.sin
        }
        pose.x = x; pose.y = y; pose.cos = cos; pose.sin = sin; pose.tick = tickCount
        count += 1
      }
    }
    var i = count
    while (i < barrierCount) { barrierOwner(i) = null; i += 1 }
    barrierCount = count
  }

  /** The barriers that stop what `ownerId` fires: everyone's but their own and their allies'.
   *  With `bodies`, what stops the projectile itself, which nothing does for one that flies over
   *  walls; without, what shelters a player from its blast. */
  private def barriersAgainst(ownerId: UUID, passesThroughWalls: Boolean, bodies: Boolean): Long = {
    if (barrierCount == 0 || (bodies && passesThroughWalls)) return 0L
    var mask = 0L
    var i = 0
    while (i < barrierCount) {
      val holder = barrierOwner(i)
      if (!holder.equals(ownerId) && !isTeammate(ownerId, holder)) mask |= 1L << i
      i += 1
    }
    mask
  }

  /** The first of `mask`'s barriers the path from (ax, ay) to (bx, by) meets from the front, or
   *  -1. Where along the path it met it is left in crossingT. */
  private def firstBarrierCrossing(mask: Long, ax: Float, ay: Float, bx: Float, by: Float): Int = {
    var best = -1
    var bestT = 2f
    // Nothing further than this from a holder can be anywhere near their barrier
    val reach = Barrier.REACH + Math.abs(bx - ax) + Math.abs(by - ay)
    var i = 0
    while (i < barrierCount) {
      if ((mask & (1L << i)) != 0L) {
        val hx = barrierX(i); val hy = barrierY(i)
        if (Math.abs(ax - hx) <= reach && Math.abs(ay - hy) <= reach) {
          val t = Barrier.crossing(hx, hy, barrierCos(i), barrierSin(i), ax, ay, bx, by)
          if (t >= 0f && t < bestT) { best = i; bestT = t }
        }
      }
      i += 1
    }
    crossingT = bestT
    best
  }

  /** Is the straight line from (x, y) to the player cut by one of `mask`'s barriers? */
  private def shelteredBy(mask: Long, x: Float, y: Float, player: Player): Boolean = {
    val pos = player.getPosition
    firstBarrierCrossing(mask, x, y, pos.getX.toFloat, pos.getY.toFloat) >= 0
  }

  /**
   * Does a barrier shelter `player` from a blast `ownerId` set off at (x, y)? Blasts, splashes,
   * slams and vortices centred in front of a barrier don't reach anyone it stands between them
   * and. Like forEachNearbyPlayer, this reads the tick's snapshot: call it only from the
   * projectile tick's thread (GameInstance's event handling).
   */
  def shelteredFromBlast(ownerId: UUID, x: Float, y: Float, player: Player): Boolean = {
    val mask = barriersAgainst(ownerId, passesThroughWalls = false, bodies = false)
    mask != 0L && shelteredBy(mask, x, y, player)
  }

  /** Put a stopped projectile at (x, y) and resolve it: an explosive goes off there, as it would
   *  against a wall; anything else is stopped. Pierce, ricochet and boomerang included. */
  private def stopAtBarrier(projectile: Projectile, pDef: ProjectileDef, barrier: Int, x: Float, y: Float,
                            events: ArrayBuffer[ProjectileEvent], toRemove: ArrayBuffer[Int]): Unit = {
    projectile.updatePosition(x, y, projectile.dx, projectile.dy)
    toRemove += projectile.id
    if (pDef.isExplosive) events += ProjectileAoE(projectile)
    else events += ProjectileBlocked(projectile, barrierOwner(barrier))
  }

  /** Stop the projectile where the path from (ax, ay) to (bx, by) first met one of `mask`'s
   *  barriers, a hair short of it. Returns whether it did. */
  private def stopOnPath(projectile: Projectile, pDef: ProjectileDef, mask: Long, ax: Float, ay: Float,
                         bx: Float, by: Float, events: ArrayBuffer[ProjectileEvent],
                         toRemove: ArrayBuffer[Int]): Boolean = {
    val hit = firstBarrierCrossing(mask, ax, ay, bx, by)
    if (hit < 0) return false
    val px = bx - ax
    val py = by - ay
    val len = Math.sqrt(px * px + py * py).toFloat
    val back = if (len > 1e-6f) BARRIER_STANDOFF / len else 0f
    val t = Math.max(0f, crossingT - back)
    stopAtBarrier(projectile, pDef, hit, ax + px * t, ay + py * t, events, toRemove)
    true
  }

  /**
   * A barrier that moved or turned since the last tick can have been carried onto the projectile,
   * which hasn't moved yet this tick. Seen from the barrier, the projectile went from where it was
   * relative to the old pose to where it is relative to the new one; if that crosses the front,
   * the barrier met it, and it stops on the barrier's face. Returns whether it stopped.
   */
  private def sweptByBarrier(projectile: Projectile, pDef: ProjectileDef, mask: Long,
                             events: ArrayBuffer[ProjectileEvent], toRemove: ArrayBuffer[Int]): Boolean = {
    val x = projectile.getX
    val y = projectile.getY
    var i = 0
    while (i < barrierCount) {
      if ((mask & (1L << i)) != 0L && barrierMoved(i)) {
        val hx = barrierX(i); val hy = barrierY(i); val c = barrierCos(i); val s = barrierSin(i)
        val px = barrierPrevX(i); val py = barrierPrevY(i); val pc = barrierPrevCos(i); val ps = barrierPrevSin(i)
        val reach = Barrier.REACH + Math.abs(hx - px) + Math.abs(hy - py)
        if (Math.abs(x - hx) <= reach && Math.abs(y - hy) <= reach) {
          val across = Barrier.acrossOf(hx, hy, c, s, x, y)
          val t = Barrier.crossingLocal(Barrier.forwardOf(px, py, pc, ps, x, y), Barrier.acrossOf(px, py, pc, ps, x, y),
            Barrier.forwardOf(hx, hy, c, s, x, y), across)
          if (t >= 0f) {
            // On the barrier's face where the projectile is, just in front of it
            val f = Barrier.forwardAt(across) + BARRIER_STANDOFF
            stopAtBarrier(projectile, pDef, i, hx + f * c - across * s, hy + f * s + across * c, events, toRemove)
            return true
          }
        }
      }
      i += 1
    }
    false
  }

  private def rebuildGrid(allPlayers: java.util.Collection[Player]): Unit = {
    // Clear previous tick's data without reallocating the HashMap
    var i = 0
    while (i < activeKeys.length) {
      val buf = gridCells.get(activeKeys(i))
      if (buf != null) buf.clear()
      i += 1
    }
    activeKeys.clear()

    // Also build the flat hittable array (reuse pre-allocated buffer)
    var count = 0
    val iter = allPlayers.iterator()
    while (iter.hasNext) {
      val player = iter.next()
      if (!player.isDead && !player.hasShield && !player.isPhased) {
        // Grow array if needed
        if (count >= hittablePlayers.length) {
          val newArr = new Array[Player](hittablePlayers.length * 2)
          System.arraycopy(hittablePlayers, 0, newArr, 0, count)
          hittablePlayers = newArr
        }
        hittablePlayers(count) = player
        count += 1
        val pos = player.getPosition
        val cx = pos.getX / gridCellSize
        val cy = pos.getY / gridCellSize
        val k = gridKey(cx, cy)
        var cell = gridCells.get(k)
        if (cell == null) {
          cell = new ArrayBuffer[Player](4)
          gridCells.put(k, cell)
        }
        if (cell.isEmpty) activeKeys += k
        cell += player
      }
    }
    hittableCount = count
  }

  /** Iterate players in the 3x3 neighborhood of the given world position.
   *  Calls `fn` for each nearby player. No allocation per call. */
  @inline private def forEachNearby(x: Float, y: Float)(fn: Player => Unit): Unit = {
    val cx = (x / gridCellSize).toInt
    val cy = (y / gridCellSize).toInt
    var dy = -1
    while (dy <= 1) {
      var dx = -1
      while (dx <= 1) {
        val cell = gridCells.get(gridKey(cx + dx, cy + dy))
        if (cell != null) {
          var i = 0
          while (i < cell.length) {
            fn(cell(i))
            i += 1
          }
        }
        dx += 1
      }
      dy += 1
    }
  }

  /** Iterate players within a configurable radius using the spatial grid.
   *  Expands the grid cell search to cover the given radius. The grid belongs to the projectile
   *  tick, which rebuilds it in place: call this only from that thread (GameInstance's event
   *  handling). The bots used to call it from theirs and read cells mid-rebuild. */
  def forEachNearbyPlayer(x: Float, y: Float, radius: Float)(fn: Player => Unit): Unit = {
    val cellRadius = (radius / gridCellSize).toInt + 1
    val cx = (x / gridCellSize).toInt
    val cy = (y / gridCellSize).toInt
    var dy = -cellRadius
    while (dy <= cellRadius) {
      var dx = -cellRadius
      while (dx <= cellRadius) {
        val cell = gridCells.get(gridKey(cx + dx, cy + dy))
        if (cell != null) {
          var i = 0
          while (i < cell.length) {
            fn(cell(i))
            i += 1
          }
        }
        dx += 1
      }
      dy += 1
    }
  }

  private val MAX_PROJECTILES_PER_PLAYER = 30

  def spawnProjectile(ownerId: UUID, x: Int, y: Int, dx: Float, dy: Float, colorRGB: Int, chargeLevel: Int = 0, projectileType: Byte = com.gridgame.common.model.ProjectileType.NORMAL): Projectile = {
    // Enforce per-player active projectile cap to prevent memory exhaustion (O(1) check)
    val counter = playerProjectileCount.computeIfAbsent(ownerId, _ => new AtomicInteger(0))
    if (counter.get() >= MAX_PROJECTILES_PER_PLAYER) return null

    val id = nextId.getAndIncrement() & 0x7FFFFFFF // Ensure positive IDs after overflow
    // Spawn the projectile one cell ahead in the velocity direction
    val spawnX = x.toFloat + dx
    val spawnY = y.toFloat + dy
    val projectile = new Projectile(id, ownerId, spawnX, spawnY, dx, dy, colorRGB, chargeLevel, projectileType)
    projectiles.put(id, projectile)
    counter.incrementAndGet()
    projectile
  }

  def tick(world: WorldData): Seq[ProjectileEvent] = {
    val events = ArrayBuffer[ProjectileEvent]()
    val toRemove = ArrayBuffer[Int]()

    rebuildGrid(registry.getPlayerValues)
    rebuildBarriers(registry.getPlayerValues)

    projectiles.values().asScala.foreach { projectile =>
      val owner = registry.get(projectile.ownerId)
      val steps = if (owner != null && owner.hasGemBoost) 2 else 1
      var resolved = false
      val pDef = ProjectileDef.get(projectile.projectileType)
      // The raised barriers this projectile can't pass
      val barriers = barriersAgainst(projectile.ownerId, pDef.passesThroughWalls, bodies = true)

      val fresh = !projectile.hasFlown
      projectile.markFlown()
      if (barriers != 0L) {
        resolved =
          if (fresh) {
            // It was spawned a cell out from where it was fired: an enemy pressed up against a
            // barrier would otherwise start their shot on the far side of it
            stopOnPath(projectile, pDef, barriers, projectile.getX - projectile.dx, projectile.getY - projectile.dy,
              projectile.getX, projectile.getY, events, toRemove)
          } else sweptByBarrier(projectile, pDef, barriers, events, toRemove)
      }

      // Sub-step movement so projectiles can't skip over non-walkable tiles.
      val movePerTick = math.sqrt(projectile.dx * projectile.dx + projectile.dy * projectile.dy).toFloat * projectile.speedMultiplier
      val subSteps = math.ceil(movePerTick / 0.5f).toInt.max(1)
      val fraction = 1.0f / subSteps

      var step = 0
      while (step < steps && !resolved) {
        var sub = 0
        while (sub < subSteps && !resolved) {
          val fromX = projectile.getX
          val fromY = projectile.getY
          projectile.moveStep(fraction)

          val maxRange = pDef.effectiveMaxRange(projectile.chargeLevel)
          if (barriers != 0L && stopOnPath(projectile, pDef, barriers, fromX, fromY, projectile.getX, projectile.getY,
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
          } else if (projectile.isOutOfBounds(world)) {
            toRemove += projectile.id
            if (pDef.isExplosive) {
              events += ProjectileAoE(projectile)
            } else {
              events += ProjectileDespawned(projectile)
            }
            resolved = true
          } else if (projectile.hitsNonWalkable(world) && (!pDef.passesThroughWalls || projectile.hitsFence(world))) {
            // Ricochet: bounce off walls instead of despawning
            if (projectile.remainingBounces > 0 && !projectile.hitsFence(world)) {
              projectile.ricochet(world)
              // Don't resolve — projectile continues
            } else {
              toRemove += projectile.id
              if (pDef.isExplosive) {
                events += ProjectileAoE(projectile)
              } else {
                events += ProjectileDespawned(projectile)
              }
              resolved = true
            }
          } else {
            var hitPlayer: Player = null
            forEachNearby(projectile.getX, projectile.getY) { player =>
              // Not through a barrier: a hit radius reaches past one two cells out, so without this
              // a shot hit its holder, or whoever sheltered beside them, before it reached it
              if (projectile.hitsPlayer(player) && !isTeammate(projectile.ownerId, player.getId) &&
                  (barriers == 0L || !shelteredBy(barriers, projectile.getX, projectile.getY, player))) {
                hitPlayer = player
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
    gridCells.clear()
    activeKeys.clear()
    hittableCount = 0
    java.util.Arrays.fill(hittablePlayers.asInstanceOf[Array[AnyRef]], null)
    barrierCount = 0
    java.util.Arrays.fill(barrierOwner.asInstanceOf[Array[AnyRef]], null)
    barrierPoses.clear()
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
    val shelter = barriersAgainst(projectile.ownerId, passesThroughWalls = false, bodies = false)
    // Use pre-filtered hittable array (already excludes dead/shielded/phased)
    val players = hittablePlayers
    val len = hittableCount
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
          (shelter == 0L || !shelteredBy(shelter, px, py, player))) {
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
