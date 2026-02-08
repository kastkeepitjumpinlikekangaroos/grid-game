package com.gridgame.server

import com.gridgame.common.Constants
import com.gridgame.common.model.Player
import com.gridgame.common.model.Projectile
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
case class ProjectileDespawned(projectile: Projectile) extends ProjectileEvent

class ProjectileManager(registry: ClientRegistry) {
  private val projectiles = new ConcurrentHashMap[Int, Projectile]()
  private val nextId = new AtomicInteger(1)

  def spawnProjectile(ownerId: UUID, x: Int, y: Int, dx: Float, dy: Float, colorRGB: Int): Projectile = {
    val id = nextId.getAndIncrement()
    // Spawn the projectile one cell ahead in the velocity direction
    val spawnX = x.toFloat + dx
    val spawnY = y.toFloat + dy
    val projectile = new Projectile(id, ownerId, spawnX, spawnY, dx, dy, colorRGB)
    projectiles.put(id, projectile)
    println(s"ProjectileManager: Spawned $projectile")
    projectile
  }

  def tick(world: WorldData): Seq[ProjectileEvent] = {
    val events = ArrayBuffer[ProjectileEvent]()
    val toRemove = ArrayBuffer[Int]()

    projectiles.values().asScala.foreach { projectile =>
      // Move the projectile
      projectile.move()

      // Check if out of bounds
      if (projectile.isOutOfBounds(world)) {
        toRemove += projectile.id
        events += ProjectileDespawned(projectile)
      }
      // Check if hit non-walkable tile
      else if (projectile.hitsNonWalkable(world)) {
        toRemove += projectile.id
        events += ProjectileDespawned(projectile)
      }
      else {
        // Check for player hits
        var hitPlayer: Player = null
        registry.getAll.asScala.foreach { player =>
          if (projectile.hitsPlayer(player) && !player.isDead) {
            hitPlayer = player
          }
        }

        if (hitPlayer != null) {
          // Apply damage
          val newHealth = hitPlayer.getHealth - Constants.PROJECTILE_DAMAGE
          hitPlayer.setHealth(newHealth)
          println(s"ProjectileManager: ${projectile} hit player ${hitPlayer.getId.toString.substring(0, 8)}, health now $newHealth")

          toRemove += projectile.id
          events += ProjectileHit(projectile, hitPlayer.getId)
        } else {
          events += ProjectileMoved(projectile)
        }
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
