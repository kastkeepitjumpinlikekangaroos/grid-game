package com.gridgame.server

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
case class ProjectileHit(projectile: Projectile, targetId: UUID) extends ProjectileEvent
case class ProjectileKill(projectile: Projectile, targetId: UUID) extends ProjectileEvent
case class ProjectileAoEHit(projectile: Projectile, targetId: UUID) extends ProjectileEvent
case class ProjectileAoEKill(projectile: Projectile, targetId: UUID) extends ProjectileEvent
case class ProjectileDespawned(projectile: Projectile) extends ProjectileEvent
case class ProjectileAoE(projectile: Projectile) extends ProjectileEvent

class ProjectileManager(registry: ClientRegistry, isTeammate: (UUID, UUID) => Boolean = (_, _) => false) {
  private val projectiles = new ConcurrentHashMap[Int, Projectile]()
  private val nextId = new AtomicInteger(1)

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

  def spawnProjectile(ownerId: UUID, x: Int, y: Int, dx: Float, dy: Float, colorRGB: Int, chargeLevel: Int = 0, projectileType: Byte = com.gridgame.common.model.ProjectileType.NORMAL): Projectile = {
    val id = nextId.getAndIncrement() & 0x7FFFFFFF // Ensure positive IDs after overflow
    // Spawn the projectile one cell ahead in the velocity direction
    val spawnX = x.toFloat + dx
    val spawnY = y.toFloat + dy
    val projectile = new Projectile(id, ownerId, spawnX, spawnY, dx, dy, colorRGB, chargeLevel, projectileType)
    projectiles.put(id, projectile)
    projectile
  }

  def tick(world: WorldData): Seq[ProjectileEvent] = {
    val events = ArrayBuffer[ProjectileEvent]()
    val toRemove = ArrayBuffer[Int]()

    rebuildGrid(registry.getAll)

    projectiles.values().asScala.foreach { projectile =>
      val owner = registry.get(projectile.ownerId)
      val steps = if (owner != null && owner.hasGemBoost) 2 else 1
      var resolved = false
      val pDef = ProjectileDef.get(projectile.projectileType)

      // Sub-step movement so projectiles can't skip over non-walkable tiles.
      val movePerTick = math.sqrt(projectile.dx * projectile.dx + projectile.dy * projectile.dy).toFloat * projectile.speedMultiplier
      val subSteps = math.ceil(movePerTick / 0.5f).toInt.max(1)
      val fraction = 1.0f / subSteps

      var step = 0
      while (step < steps && !resolved) {
        var sub = 0
        while (sub < subSteps && !resolved) {
          projectile.moveStep(fraction)

          val maxRange = pDef.effectiveMaxRange(projectile.chargeLevel)
          if (projectile.getDistanceTraveled >= maxRange) {
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
                events ++= applyAoEDamage(projectile, aoe.radius, aoe.damage, null, aoe.freezeDurationMs, aoe.rootDurationMs)
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
              if (projectile.hitsPlayer(player) && !isTeammate(projectile.ownerId, player.getId)) {
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
                val newHealth = hitPlayer.synchronized {
                  val h = hitPlayer.getHealth - damage
                  hitPlayer.setHealth(h)
                  h
                }
                // Pierce: track hit player and continue if pierce count not exhausted
                projectile.hitPlayers += hitPlayer.getId
                val canPierce = pDef.pierceCount > 0 && projectile.hitPlayers.size < pDef.pierceCount

                if (!canPierce) {
                  toRemove += projectile.id
                }
                if (newHealth <= 0) {
                  events += ProjectileKill(projectile, hitPlayer.getId)
                } else {
                  events += ProjectileHit(projectile, hitPlayer.getId)
                }

                // AoE splash damage to nearby players (excluding the direct hit target)
                pDef.aoeOnHit.foreach { aoe =>
                  events ++= applyAoEDamage(projectile, aoe.radius, aoe.damage, hitPlayer.getId, aoe.freezeDurationMs, aoe.rootDurationMs)
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

    // Remove despawned/hit projectiles
    toRemove.foreach(id => projectiles.remove(id))

    events.toSeq
  }

  def getProjectile(id: Int): Projectile = projectiles.get(id)

  def removeProjectile(id: Int): Unit = projectiles.remove(id)

  def getAll: Seq[Projectile] = projectiles.values().asScala.toSeq

  def size: Int = projectiles.size()

  /** Deal AoE damage to all players within radius of the projectile, excluding excludeId (the direct-hit target). */
  private def applyAoEDamage(projectile: Projectile, radius: Float, damage: Int, excludeId: UUID, freezeDurationMs: Int = 0, rootDurationMs: Int = 0): Seq[ProjectileEvent] = {
    val events = ArrayBuffer[ProjectileEvent]()
    val px = projectile.getX
    val py = projectile.getY
    // Use pre-filtered hittable array (already excludes dead/shielded/phased)
    val players = hittablePlayers
    val len = hittableCount
    var i = 0
    while (i < len) {
      val player = players(i)
      i += 1
      if (!player.getId.equals(projectile.ownerId) &&
          (excludeId == null || !player.getId.equals(excludeId)) &&
          !isTeammate(projectile.ownerId, player.getId)) {
        val pos = player.getPosition
        val dx = px - (pos.getX + 0.5f)
        val dy = py - (pos.getY + 0.5f)
        if (dx * dx + dy * dy <= radius * radius) {
          val newHealth = player.synchronized {
            val h = player.getHealth - damage
            player.setHealth(h)
            h
          }
          if (freezeDurationMs > 0) {
            player.tryFreeze(freezeDurationMs)
          }
          if (rootDurationMs > 0) {
            player.tryRoot(rootDurationMs)
          }
          if (newHealth <= 0) {
            events += ProjectileAoEKill(projectile, player.getId)
          } else {
            events += ProjectileAoEHit(projectile, player.getId)
          }
        }
      }
    }
    events.toSeq
  }
}
