package com.gridgame.server

import com.gridgame.common.Constants
import com.gridgame.common.model.Direction
import com.gridgame.common.model.Item
import com.gridgame.common.model.Player
import com.gridgame.common.model.Position
import com.gridgame.common.model.Projectile
import com.gridgame.common.model.ProjectileType
import com.gridgame.common.model.WorldData
import com.gridgame.common.protocol._
import com.gridgame.common.world.WorldLoader

import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import scala.jdk.CollectionConverters._

class GameInstance(val gameId: Short, val worldFile: String, val durationMinutes: Int, val server: GameServer) {
  val registry = new ClientRegistry()
  val projectileManager = new ProjectileManager(registry)
  val itemManager = new ItemManager()
  val killTracker = new KillTracker()
  val handler = new ClientHandler(registry, server, projectileManager, itemManager, this)
  var world: WorldData = _
  val modifiedTiles = new java.util.concurrent.ConcurrentHashMap[(Int, Int), Int]()

  private var projectileExecutor: ScheduledExecutorService = _
  private var itemSpawnExecutor: ScheduledExecutorService = _
  private var timerSyncExecutor: ScheduledExecutorService = _
  private var startTime: Long = 0L
  @volatile private var running = false

  def loadWorld(): Unit = {
    if (worldFile.nonEmpty) {
      try {
        world = WorldLoader.load(worldFile)
        println(s"GameInstance[$gameId]: Loaded world '${world.name}' (${world.width}x${world.height})")
      } catch {
        case e: Exception =>
          println(s"GameInstance[$gameId]: Failed to load world: ${e.getMessage}")
          world = WorldData.createEmpty(200, 200)
      }
    } else {
      world = WorldData.createEmpty(200, 200)
    }
  }

  def start(): Unit = {
    if (world == null) loadWorld()

    startTime = System.currentTimeMillis()
    running = true

    // Start projectile tick
    projectileExecutor = Executors.newSingleThreadScheduledExecutor()
    projectileExecutor.scheduleAtFixedRate(
      new Runnable { def run(): Unit = tickProjectiles() },
      Constants.PROJECTILE_SPEED_MS.toLong,
      Constants.PROJECTILE_SPEED_MS.toLong,
      TimeUnit.MILLISECONDS
    )

    // Spawn initial items and start item spawning
    itemSpawnExecutor = Executors.newSingleThreadScheduledExecutor()
    for (_ <- 1 to 50) spawnItem()
    itemSpawnExecutor.scheduleAtFixedRate(
      new Runnable { def run(): Unit = for (_ <- 1 to 50) spawnItem() },
      Constants.ITEM_SPAWN_INTERVAL_MS.toLong,
      Constants.ITEM_SPAWN_INTERVAL_MS.toLong,
      TimeUnit.MILLISECONDS
    )

    // Start timer sync
    timerSyncExecutor = Executors.newSingleThreadScheduledExecutor()
    timerSyncExecutor.scheduleAtFixedRate(
      new Runnable { def run(): Unit = syncTimer() },
      Constants.TIME_SYNC_INTERVAL_S.toLong,
      Constants.TIME_SYNC_INTERVAL_S.toLong,
      TimeUnit.SECONDS
    )

    println(s"GameInstance[$gameId]: Started ($durationMinutes min)")
  }

  def stop(): Unit = {
    running = false
    if (projectileExecutor != null) { projectileExecutor.shutdown(); projectileExecutor.shutdownNow() }
    if (itemSpawnExecutor != null) { itemSpawnExecutor.shutdown(); itemSpawnExecutor.shutdownNow() }
    if (timerSyncExecutor != null) { timerSyncExecutor.shutdown(); timerSyncExecutor.shutdownNow() }
    println(s"GameInstance[$gameId]: Stopped")
  }

  def getRemainingSeconds: Int = {
    val elapsed = (System.currentTimeMillis() - startTime) / 1000
    val total = durationMinutes * 60
    Math.max(0, (total - elapsed).toInt)
  }

  def isTimeUp: Boolean = getRemainingSeconds <= 0

  def isRunning: Boolean = running

  private def tickProjectiles(): Unit = {
    if (!running || world == null) return

    val events = projectileManager.tick(world)
    events.foreach {
      case ProjectileMoved(projectile) =>
        val packet = new ProjectilePacket(
          server.getNextSequenceNumber,
          projectile.ownerId,
          projectile.getX, projectile.getY,
          projectile.colorRGB,
          projectile.id,
          projectile.dx, projectile.dy,
          ProjectileAction.MOVE,
          null,
          projectile.chargeLevel.toByte,
          projectile.projectileType
        )
        broadcastToInstance(packet)

      case ProjectileKill(projectile, targetId) =>
        // Send hit packet
        val hitPacket = new ProjectilePacket(
          server.getNextSequenceNumber,
          projectile.ownerId,
          projectile.getX, projectile.getY,
          projectile.colorRGB,
          projectile.id,
          projectile.dx, projectile.dy,
          ProjectileAction.HIT,
          targetId,
          projectile.chargeLevel.toByte,
          projectile.projectileType
        )
        broadcastToInstance(hitPacket)

        // Send player update with 0 health
        val target = registry.get(targetId)
        if (target != null) {
          val flags = (if (target.hasShield) 0x01 else 0) |
                      (if (target.hasGemBoost) 0x02 else 0) |
                      (if (target.isFrozen) 0x04 else 0) |
                      (if (target.isPhased) 0x08 else 0)
          val updatePacket = new PlayerUpdatePacket(
            server.getNextSequenceNumber,
            targetId,
            target.getPosition,
            target.getColorRGB,
            target.getHealth,
            0,
            flags
          )
          broadcastToInstance(updatePacket)
        }

        // Record kill
        killTracker.recordKill(projectile.ownerId, targetId)

        // Broadcast kill event
        val killPacket = new GameEventPacket(
          server.getNextSequenceNumber,
          projectile.ownerId,
          GameEvent.KILL,
          gameId,
          getRemainingSeconds,
          killTracker.getKills(projectile.ownerId).toShort,
          killTracker.getDeaths(projectile.ownerId).toShort,
          targetId,
          0.toByte, 0.toShort, 0.toShort
        )
        broadcastToInstance(killPacket)

        // Schedule auto-respawn
        scheduleRespawn(targetId)

      case ProjectileHit(projectile, targetId) =>
        val hitPacket = new ProjectilePacket(
          server.getNextSequenceNumber,
          projectile.ownerId,
          projectile.getX, projectile.getY,
          projectile.colorRGB,
          projectile.id,
          projectile.dx, projectile.dy,
          ProjectileAction.HIT,
          targetId,
          projectile.chargeLevel.toByte,
          projectile.projectileType
        )
        broadcastToInstance(hitPacket)

        val target = registry.get(targetId)
        if (target != null) {
          // Apply type-specific effects
          projectile.projectileType match {
            case ProjectileType.TENTACLE | ProjectileType.ROPE =>
              // Pull target directly in front of the shooter
              val owner = registry.get(projectile.ownerId)
              if (owner != null) {
                val ownerPos = owner.getPosition
                val (fdx, fdy) = owner.getDirection match {
                  case Direction.Up    => (0, -1)
                  case Direction.Down  => (0, 1)
                  case Direction.Left  => (-1, 0)
                  case Direction.Right => (1, 0)
                }
                val destX = ownerPos.getX + fdx
                val destY = ownerPos.getY + fdy
                val clampedX = Math.max(0, Math.min(world.width - 1, destX))
                val clampedY = Math.max(0, Math.min(world.height - 1, destY))
                if (world.isWalkable(clampedX, clampedY)) {
                  target.setPosition(new Position(clampedX, clampedY))
                }
              }

            case ProjectileType.ICE_BEAM =>
              target.setFrozenUntil(System.currentTimeMillis() + Constants.ICE_BEAM_FREEZE_DURATION_MS)

            case ProjectileType.HAUNT =>
              // Freeze the target for 2 seconds
              target.setFrozenUntil(System.currentTimeMillis() + Constants.HAUNT_FREEZE_DURATION_MS)
              // Teleport the projectile owner behind the target
              val owner = registry.get(projectile.ownerId)
              if (owner != null) {
                val targetPos = target.getPosition
                val (bdx, bdy) = target.getDirection match {
                  case Direction.Up    => (0, 1)   // behind Up-facing = below
                  case Direction.Down  => (0, -1)  // behind Down-facing = above
                  case Direction.Left  => (1, 0)   // behind Left-facing = right
                  case Direction.Right => (-1, 0)  // behind Right-facing = left
                }
                val destX = Math.max(0, Math.min(world.width - 1, targetPos.getX + bdx * Constants.HAUNT_TELEPORT_DISTANCE))
                val destY = Math.max(0, Math.min(world.height - 1, targetPos.getY + bdy * Constants.HAUNT_TELEPORT_DISTANCE))
                if (world.isWalkable(destX, destY)) {
                  owner.setPosition(new Position(destX, destY))
                  // Protect against client position updates overwriting the teleport
                  owner.setServerTeleportedUntil(System.currentTimeMillis() + 500)
                  // Broadcast position update for the Wraith
                  val ownerFlags = (if (owner.hasShield) 0x01 else 0) |
                                   (if (owner.hasGemBoost) 0x02 else 0) |
                                   (if (owner.isFrozen) 0x04 else 0) |
                                   (if (owner.isPhased) 0x08 else 0)
                  val ownerUpdate = new PlayerUpdatePacket(
                    server.getNextSequenceNumber,
                    projectile.ownerId,
                    owner.getPosition,
                    owner.getColorRGB,
                    owner.getHealth,
                    0,
                    ownerFlags
                  )
                  broadcastToInstance(ownerUpdate)
                }
              }

            case ProjectileType.TIDAL_WAVE =>
              // Push target away from shooter
              val twOwner = registry.get(projectile.ownerId)
              if (twOwner != null) {
                val twOwnerPos = twOwner.getPosition
                val twTargetPos = target.getPosition
                val pdx = twTargetPos.getX - twOwnerPos.getX
                val pdy = twTargetPos.getY - twOwnerPos.getY
                val dist = Math.sqrt(pdx * pdx + pdy * pdy).toFloat
                if (dist > 0.01f) {
                  val pushDist = Constants.TIDAL_WAVE_PUSHBACK_DISTANCE
                  val destX = twTargetPos.getX + Math.round(pdx / dist * pushDist)
                  val destY = twTargetPos.getY + Math.round(pdy / dist * pushDist)
                  val clampedX = Math.max(0, Math.min(world.width - 1, destX))
                  val clampedY = Math.max(0, Math.min(world.height - 1, destY))
                  if (world.isWalkable(clampedX, clampedY)) {
                    target.setPosition(new Position(clampedX, clampedY))
                  }
                }
              }

            case ProjectileType.GUST =>
              // Push target away from the shooter
              val gustOwner = registry.get(projectile.ownerId)
              if (gustOwner != null) {
                val gustOwnerPos = gustOwner.getPosition
                val gustTargetPos = target.getPosition
                val pushDx = gustTargetPos.getX - gustOwnerPos.getX
                val pushDy = gustTargetPos.getY - gustOwnerPos.getY
                val pushLen = Math.sqrt(pushDx * pushDx + pushDy * pushDy)
                if (pushLen > 0.01) {
                  val ndx = pushDx / pushLen
                  val ndy = pushDy / pushLen
                  var destX = gustTargetPos.getX
                  var destY = gustTargetPos.getY
                  for (step <- 1 to Constants.GUST_PUSH_DISTANCE) {
                    val nextX = Math.max(0, Math.min(world.width - 1, (gustTargetPos.getX + ndx * step).toInt))
                    val nextY = Math.max(0, Math.min(world.height - 1, (gustTargetPos.getY + ndy * step).toInt))
                    if (world.isWalkable(nextX, nextY)) {
                      destX = nextX
                      destY = nextY
                    }
                  }
                  target.setPosition(new Position(destX, destY))
                }
              }

            case _ => // No special effect (AXE, SPEAR, NORMAL, SOUL_BOLT, SPLASH, GEYSER, TALON)
          }

          val flags = (if (target.hasShield) 0x01 else 0) |
                      (if (target.hasGemBoost) 0x02 else 0) |
                      (if (target.isFrozen) 0x04 else 0) |
                      (if (target.isPhased) 0x08 else 0)
          val updatePacket = new PlayerUpdatePacket(
            server.getNextSequenceNumber,
            targetId,
            target.getPosition,
            target.getColorRGB,
            target.getHealth,
            0,
            flags
          )
          broadcastToInstance(updatePacket)
        }

      case ProjectileAoEHit(projectile, targetId) =>
        val hitPacket = new ProjectilePacket(
          server.getNextSequenceNumber,
          projectile.ownerId,
          projectile.getX, projectile.getY,
          projectile.colorRGB,
          projectile.id,
          projectile.dx, projectile.dy,
          ProjectileAction.HIT,
          targetId,
          projectile.chargeLevel.toByte,
          projectile.projectileType
        )
        broadcastToInstance(hitPacket)

        val aoeTarget = registry.get(targetId)
        if (aoeTarget != null) {
          val flags = (if (aoeTarget.hasShield) 0x01 else 0) |
                      (if (aoeTarget.hasGemBoost) 0x02 else 0) |
                      (if (aoeTarget.isFrozen) 0x04 else 0) |
                      (if (aoeTarget.isPhased) 0x08 else 0)
          val updatePacket = new PlayerUpdatePacket(
            server.getNextSequenceNumber,
            targetId,
            aoeTarget.getPosition,
            aoeTarget.getColorRGB,
            aoeTarget.getHealth,
            0,
            flags
          )
          broadcastToInstance(updatePacket)
        }

      case ProjectileAoEKill(projectile, targetId) =>
        val hitPacket = new ProjectilePacket(
          server.getNextSequenceNumber,
          projectile.ownerId,
          projectile.getX, projectile.getY,
          projectile.colorRGB,
          projectile.id,
          projectile.dx, projectile.dy,
          ProjectileAction.HIT,
          targetId,
          projectile.chargeLevel.toByte,
          projectile.projectileType
        )
        broadcastToInstance(hitPacket)

        val aoeKillTarget = registry.get(targetId)
        if (aoeKillTarget != null) {
          val flags = (if (aoeKillTarget.hasShield) 0x01 else 0) |
                      (if (aoeKillTarget.hasGemBoost) 0x02 else 0) |
                      (if (aoeKillTarget.isFrozen) 0x04 else 0) |
                      (if (aoeKillTarget.isPhased) 0x08 else 0)
          val updatePacket = new PlayerUpdatePacket(
            server.getNextSequenceNumber,
            targetId,
            aoeKillTarget.getPosition,
            aoeKillTarget.getColorRGB,
            aoeKillTarget.getHealth,
            0,
            flags
          )
          broadcastToInstance(updatePacket)
        }

        killTracker.recordKill(projectile.ownerId, targetId)

        val aoeKillPacket = new GameEventPacket(
          server.getNextSequenceNumber,
          projectile.ownerId,
          GameEvent.KILL,
          gameId,
          getRemainingSeconds,
          killTracker.getKills(projectile.ownerId).toShort,
          killTracker.getDeaths(projectile.ownerId).toShort,
          targetId,
          0.toByte, 0.toShort, 0.toShort
        )
        broadcastToInstance(aoeKillPacket)

        scheduleRespawn(targetId)

      case ProjectileDespawned(projectile) =>
        val packet = new ProjectilePacket(
          server.getNextSequenceNumber,
          projectile.ownerId,
          projectile.getX, projectile.getY,
          projectile.colorRGB,
          projectile.id,
          projectile.dx, projectile.dy,
          ProjectileAction.DESPAWN,
          null,
          projectile.chargeLevel.toByte,
          projectile.projectileType
        )
        broadcastToInstance(packet)
    }
  }

  private def scheduleRespawn(playerId: UUID): Unit = {
    val executor = Executors.newSingleThreadScheduledExecutor()
    executor.schedule(new Runnable {
      def run(): Unit = {
        if (!running) return
        val player = registry.get(playerId)
        if (player != null) {
          player.setHealth(player.getMaxHealth)
          val spawnPoint = world.getValidSpawnPoint()
          player.setPosition(spawnPoint)

          // Send respawn event
          val respawnPacket = new GameEventPacket(
            server.getNextSequenceNumber,
            playerId,
            GameEvent.RESPAWN,
            gameId,
            getRemainingSeconds,
            killTracker.getKills(playerId).toShort,
            killTracker.getDeaths(playerId).toShort,
            null,
            0.toByte,
            spawnPoint.getX.toShort,
            spawnPoint.getY.toShort
          )
          broadcastToInstance(respawnPacket)

          // Broadcast updated player state
          val updatePacket = new PlayerUpdatePacket(
            server.getNextSequenceNumber,
            playerId,
            spawnPoint,
            player.getColorRGB,
            player.getHealth,
            0, 0
          )
          broadcastToInstance(updatePacket)

          println(s"GameInstance[$gameId]: Player ${playerId.toString.substring(0, 8)} respawned at $spawnPoint")
        }
        executor.shutdown()
      }
    }, Constants.RESPAWN_DELAY_MS.toLong, TimeUnit.MILLISECONDS)
  }

  private def spawnItem(): Unit = {
    if (!running || world == null) return
    itemManager.spawnRandomItem(world).foreach { event =>
      val zeroUUID = new UUID(0L, 0L)
      val packet = new ItemPacket(
        server.getNextSequenceNumber,
        zeroUUID,
        event.item.x, event.item.y,
        event.item.itemType.id,
        event.item.id,
        ItemAction.SPAWN
      )
      broadcastToInstance(packet)
    }
  }

  private def syncTimer(): Unit = {
    if (!running) return

    if (isTimeUp) {
      server.endGame(gameId)
      return
    }

    val zeroUUID = new UUID(0L, 0L)
    val packet = new GameEventPacket(
      server.getNextSequenceNumber,
      zeroUUID,
      GameEvent.TIME_SYNC,
      gameId,
      getRemainingSeconds,
      0.toShort, 0.toShort, null, 0.toByte, 0.toShort, 0.toShort
    )
    broadcastToInstance(packet)
  }

  def broadcastToInstance(packet: Packet): Unit = {
    registry.getAll.asScala.foreach { player =>
      server.sendPacketToPlayer(packet, player)
    }
  }

  def broadcastProjectileSpawn(projectile: Projectile): Unit = {
    val packet = new ProjectilePacket(
      server.getNextSequenceNumber,
      projectile.ownerId,
      projectile.getX, projectile.getY,
      projectile.colorRGB,
      projectile.id,
      projectile.dx, projectile.dy,
      ProjectileAction.SPAWN,
      null,
      projectile.chargeLevel.toByte,
      projectile.projectileType
    )
    broadcastToInstance(packet)
  }

  def broadcastItemPickup(item: Item, playerId: UUID): Unit = {
    val packet = new ItemPacket(
      server.getNextSequenceNumber,
      playerId,
      item.x, item.y,
      item.itemType.id,
      item.id,
      ItemAction.PICKUP
    )
    broadcastToInstance(packet)
  }

  def broadcastTileUpdate(playerId: UUID, x: Int, y: Int, tileId: Int): Unit = {
    modifiedTiles.put((x, y), tileId)
    val packet = new TileUpdatePacket(
      server.getNextSequenceNumber,
      playerId,
      x, y,
      tileId
    )
    broadcastToInstance(packet)
  }

  def sendModifiedTiles(player: Player): Unit = {
    val zeroUUID = new UUID(0L, 0L)
    modifiedTiles.forEach { (pos, tileId) =>
      val packet = new TileUpdatePacket(
        server.getNextSequenceNumber,
        zeroUUID,
        pos._1, pos._2,
        tileId
      )
      server.sendPacketToPlayer(packet, player)
    }
  }

  def broadcastPlayerUpdate(packet: PlayerUpdatePacket): Unit = {
    broadcastToInstance(packet)
  }
}
