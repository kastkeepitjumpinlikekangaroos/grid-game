package com.gridgame.server

import com.gridgame.common.Constants
import com.gridgame.common.model.Player
import com.gridgame.common.model.Projectile
import com.gridgame.common.model.ProjectileType
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
case class ProjectileDespawned(projectile: Projectile) extends ProjectileEvent

class ProjectileManager(registry: ClientRegistry) {
  private val projectiles = new ConcurrentHashMap[Int, Projectile]()
  private val nextId = new AtomicInteger(1)

  def spawnProjectile(ownerId: UUID, x: Int, y: Int, dx: Float, dy: Float, colorRGB: Int, chargeLevel: Int = 0, projectileType: Byte = ProjectileType.NORMAL): Projectile = {
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

      // Sub-step movement so projectiles can't skip over non-walkable tiles.
      // Each sub-step moves at most ~0.5 cells, then we check collisions.
      val movePerTick = math.sqrt(projectile.dx * projectile.dx + projectile.dy * projectile.dy).toFloat * projectile.speedMultiplier
      val subSteps = math.ceil(movePerTick / 0.5f).toInt.max(1)
      val fraction = 1.0f / subSteps

      var step = 0
      while (step < steps && !resolved) {
        var sub = 0
        while (sub < subSteps && !resolved) {
          projectile.moveStep(fraction)

          val maxRange = projectile.projectileType match {
            case ProjectileType.TENTACLE => Constants.TENTACLE_MAX_RANGE.toDouble
            case ProjectileType.ICE_BEAM => Constants.ICE_BEAM_MAX_RANGE.toDouble
            case ProjectileType.AXE => Constants.AXE_MAX_RANGE.toDouble
            case ProjectileType.ROPE => Constants.ROPE_MAX_RANGE.toDouble
            case ProjectileType.SPEAR => Constants.SPEAR_MAX_RANGE.toDouble
            case ProjectileType.SOUL_BOLT => Constants.SOUL_BOLT_MAX_RANGE.toDouble
            case ProjectileType.HAUNT => Constants.HAUNT_MAX_RANGE.toDouble
            case ProjectileType.ARCANE_BOLT => Constants.ARCANE_BOLT_MAX_RANGE.toDouble
            case ProjectileType.FIREBALL => Constants.FIREBALL_MAX_RANGE.toDouble
            case _ => Constants.CHARGE_MIN_RANGE + (projectile.chargeLevel / 100.0 * (Constants.CHARGE_MAX_RANGE - Constants.CHARGE_MIN_RANGE))
          }
          if (projectile.getDistanceTraveled >= maxRange) {
            toRemove += projectile.id
            events += ProjectileDespawned(projectile)
            resolved = true
          } else if (projectile.isOutOfBounds(world)) {
            toRemove += projectile.id
            events += ProjectileDespawned(projectile)
            resolved = true
          } else if (projectile.hitsNonWalkable(world) &&
                     projectile.projectileType != ProjectileType.SOUL_BOLT &&
                     projectile.projectileType != ProjectileType.HAUNT &&
                     projectile.projectileType != ProjectileType.ARCANE_BOLT) {
            toRemove += projectile.id
            events += ProjectileDespawned(projectile)
            resolved = true
          } else {
            var hitPlayer: Player = null
            registry.getAll.asScala.foreach { player =>
              if (projectile.hitsPlayer(player) && !player.isDead && !player.hasShield && !player.isPhased) {
                hitPlayer = player
              }
            }

            if (hitPlayer != null) {
              val damage = projectile.projectileType match {
                case ProjectileType.TENTACLE => Constants.TENTACLE_DAMAGE
                case ProjectileType.ICE_BEAM => Constants.ICE_BEAM_DAMAGE
                case ProjectileType.AXE => Constants.AXE_DAMAGE
                case ProjectileType.ROPE => Constants.ROPE_DAMAGE
                case ProjectileType.SPEAR =>
                  val distanceFraction = Math.min(1.0f, projectile.getDistanceTraveled / Constants.SPEAR_MAX_RANGE.toFloat)
                  (Constants.SPEAR_BASE_DAMAGE + (distanceFraction * (Constants.SPEAR_MAX_DAMAGE - Constants.SPEAR_BASE_DAMAGE))).toInt
                case ProjectileType.SOUL_BOLT => Constants.SOUL_BOLT_DAMAGE
                case ProjectileType.HAUNT => Constants.HAUNT_DAMAGE
                case ProjectileType.ARCANE_BOLT => Constants.ARCANE_BOLT_DAMAGE
                case ProjectileType.FIREBALL => Constants.FIREBALL_DAMAGE
                case _ => (Constants.CHARGE_MIN_DAMAGE + (projectile.chargeLevel / 100.0 * (Constants.CHARGE_MAX_DAMAGE - Constants.CHARGE_MIN_DAMAGE))).toInt
              }
              val newHealth = hitPlayer.getHealth - damage
              hitPlayer.setHealth(newHealth)
              println(s"ProjectileManager: ${projectile} hit player ${hitPlayer.getId.toString.substring(0, 8)}, damage=$damage (charge=${projectile.chargeLevel}%), health now $newHealth")

              toRemove += projectile.id
              if (newHealth <= 0) {
                events += ProjectileKill(projectile, hitPlayer.getId)
              } else {
                events += ProjectileHit(projectile, hitPlayer.getId)
              }
              resolved = true
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
}
