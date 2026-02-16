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

  def spawnProjectile(ownerId: UUID, x: Int, y: Int, dx: Float, dy: Float, colorRGB: Int, chargeLevel: Int = 0, projectileType: Byte = com.gridgame.common.model.ProjectileType.NORMAL): Projectile = {
    val id = nextId.getAndIncrement()
    // Spawn the projectile one cell ahead in the velocity direction
    val spawnX = x.toFloat + dx
    val spawnY = y.toFloat + dy
    val projectile = new Projectile(id, ownerId, spawnX, spawnY, dx, dy, colorRGB, chargeLevel, projectileType)
    projectiles.put(id, projectile)
    println(s"ProjectileManager: Spawned $projectile (charge=$chargeLevel%, type=$projectileType)")
    projectile
  }

  def tick(world: WorldData): Seq[ProjectileEvent] = {
    val events = ArrayBuffer[ProjectileEvent]()
    val toRemove = ArrayBuffer[Int]()

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
            registry.getAll.asScala.foreach { player =>
              if (projectile.hitsPlayer(player) && !player.isDead && !player.hasShield && !player.isPhased && !isTeammate(projectile.ownerId, player.getId)) {
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
                println(s"ProjectileManager: ${projectile} hit player ${hitPlayer.getId.toString.substring(0, 8)}, damage=$damage (charge=${projectile.chargeLevel}%), health now $newHealth")

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
    registry.getAll.asScala.foreach { player =>
      if (!player.getId.equals(projectile.ownerId) &&
          (excludeId == null || !player.getId.equals(excludeId)) &&
          !player.isDead && !player.hasShield &&
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
          println(s"ProjectileManager: AoE hit player ${player.getId.toString.substring(0, 8)}, damage=$damage, health now $newHealth" +
            (if (freezeDurationMs > 0) s", frozen ${freezeDurationMs}ms" else "") +
            (if (rootDurationMs > 0) s", rooted ${rootDurationMs}ms" else ""))
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
