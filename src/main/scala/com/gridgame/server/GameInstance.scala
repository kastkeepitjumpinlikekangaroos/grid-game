package com.gridgame.server

import com.gridgame.common.Constants
import com.gridgame.common.model._
import com.gridgame.common.observability.Attrs
import com.gridgame.common.observability.Metrics
import com.gridgame.common.protocol._
import com.gridgame.common.world.WorldLoader

import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import scala.jdk.CollectionConverters._

class GameInstance(val gameId: Short, val worldFile: String, val durationMinutes: Int, val server: GameServer) {
  val registry = new ClientRegistry()
  val teamAssignments = new java.util.concurrent.ConcurrentHashMap[UUID, Byte]()
  var gameMode: Byte = 0 // 0=FFA, 1=Teams
  val projectileManager = new ProjectileManager(registry, isTeammate)
  val itemManager = new ItemManager()
  val trapManager = new TrapManager(registry, isTeammate)
  val killTracker = new KillTracker()
  val handler = new ClientHandler(registry, server, projectileManager, itemManager, this)
  var world: WorldData = _
  val modifiedTiles = new java.util.concurrent.ConcurrentHashMap[(Int, Int), Int]()

  def isTeammate(id1: UUID, id2: UUID): Boolean = {
    if (gameMode == 0) return false
    val team1: java.lang.Byte = teamAssignments.get(id1)
    val team2: java.lang.Byte = teamAssignments.get(id2)
    team1 != null && team2 != null && team1.byteValue() != 0 && team1.byteValue() == team2.byteValue()
  }

  @volatile var isPractice: Boolean = false
  var botController: BotController = _

  private var projectileExecutor: ScheduledExecutorService = _
  private var itemSpawnExecutor: ScheduledExecutorService = _
  private var timerSyncExecutor: ScheduledExecutorService = _
  private var playerTickExecutor: ScheduledExecutorService = _
  private var respawnExecutor: ScheduledExecutorService = _
  private var startTime: Long = 0L
  @volatile private var running = false
  private val spawnLock = new Object()

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

  /** Put the match in play: world loaded, clock running. start() also schedules the ticks that
    * drive it; tests call this alone and run the ticks themselves. */
  private[server] def begin(): Unit = {
    if (world == null) loadWorld()
    startTime = System.currentTimeMillis()
    running = true
  }

  def start(): Unit = {
    begin()

    // Helper: wrap a Runnable in try-catch so ScheduledExecutorService doesn't silently die
    def safeRunnable(name: String)(body: => Unit): Runnable = new Runnable {
      def run(): Unit = try { body } catch {
        case e: Exception =>
          System.err.println(s"GameInstance[$gameId] $name error: ${e.getMessage}")
      }
    }

    // Start projectile tick
    projectileExecutor = Executors.newSingleThreadScheduledExecutor()
    projectileExecutor.scheduleAtFixedRate(
      safeRunnable("tickProjectiles")(tickProjectiles()),
      Constants.PROJECTILE_SPEED_MS.toLong,
      Constants.PROJECTILE_SPEED_MS.toLong,
      TimeUnit.MILLISECONDS
    )

    // Spawn items relative to map size (1 item per 500 tiles, min 3, max 20)
    val mapArea = world.width * world.height
    val itemCount = Math.max(3, Math.min(20, mapArea / 2000))
    itemSpawnExecutor = Executors.newSingleThreadScheduledExecutor()
    spawnItemBatch(itemCount)
    itemSpawnExecutor.scheduleAtFixedRate(
      safeRunnable("spawnItem")(spawnItemBatch(itemCount)),
      Constants.ITEM_SPAWN_INTERVAL_MS.toLong,
      Constants.ITEM_SPAWN_INTERVAL_MS.toLong,
      TimeUnit.MILLISECONDS
    )

    // Start player state tick (burn DoT processing)
    playerTickExecutor = Executors.newSingleThreadScheduledExecutor()
    playerTickExecutor.scheduleAtFixedRate(
      safeRunnable("tickPlayers")(tickPlayers()),
      200L, 200L, TimeUnit.MILLISECONDS
    )

    // Shared executor for respawn scheduling
    respawnExecutor = Executors.newSingleThreadScheduledExecutor()

    // Start timer sync
    timerSyncExecutor = Executors.newSingleThreadScheduledExecutor()
    timerSyncExecutor.scheduleAtFixedRate(
      safeRunnable("syncTimer")(syncTimer()),
      Constants.TIME_SYNC_INTERVAL_S.toLong,
      Constants.TIME_SYNC_INTERVAL_S.toLong,
      TimeUnit.SECONDS
    )

    Metrics.matchesStarted.add(1L, io.opentelemetry.api.common.Attributes.builder()
      .putAll(Attrs.modeOf(gameMode))
      .putAll(if (isPractice) Attrs.MatchPractice else Attrs.MatchCasual)
      .build())
    println(s"GameInstance[$gameId]: Started ($durationMinutes min)")
  }

  def stop(): Unit = {
    running = false
    if (botController != null) botController.stop()
    if (projectileExecutor != null) { projectileExecutor.shutdown(); projectileExecutor.shutdownNow() }
    if (playerTickExecutor != null) { playerTickExecutor.shutdown(); playerTickExecutor.shutdownNow() }
    if (itemSpawnExecutor != null) { itemSpawnExecutor.shutdown(); itemSpawnExecutor.shutdownNow() }
    if (respawnExecutor != null) { respawnExecutor.shutdown(); respawnExecutor.shutdownNow() }
    if (timerSyncExecutor != null) { timerSyncExecutor.shutdown(); timerSyncExecutor.shutdownNow() }
    // Release manager state and unregister their async gauges. Without this, the
    // gauge callbacks remain registered against OTel's meter and keep reporting the
    // post-mortem sizes of projectiles/items, and across matches the callbacks
    // accumulate (memory leak + metric corruption).
    projectileManager.close()
    itemManager.close()
    trapManager.close()
    modifiedTiles.clear()
    teamAssignments.clear()
    println(s"GameInstance[$gameId]: Stopped")
  }

  def getRemainingSeconds: Int = {
    val elapsed = (System.currentTimeMillis() - startTime) / 1000
    val total = durationMinutes * 60
    Math.max(0, (total - elapsed).toInt)
  }

  def getElapsedSeconds: Int = ((System.currentTimeMillis() - startTime) / 1000).toInt

  def isTimeUp: Boolean = getRemainingSeconds <= 0

  def isRunning: Boolean = running

  private val projectileTickAttrs = Attrs.tickPhase("projectile")
  private val playerTickAttrs = Attrs.tickPhase("player")
  private val timerTickAttrs = Attrs.tickPhase("timer")

  private def projectilePacket(projectile: Projectile, action: Byte, targetId: UUID = null): ProjectilePacket =
    new ProjectilePacket(
      server.getNextSequenceNumber,
      projectile.ownerId,
      projectile.getX, projectile.getY,
      projectile.colorRGB,
      projectile.id,
      projectile.dx, projectile.dy,
      action,
      targetId,
      projectile.chargeLevel.toByte,
      projectile.projectileType
    )

  /** A hit on targetId: HIT if it used the projectile up, PIERCE if the projectile flies on. */
  private def hitPacket(projectile: Projectile, targetId: UUID, flyingOn: Boolean): ProjectilePacket =
    projectilePacket(projectile, if (flyingOn) ProjectileAction.PIERCE else ProjectileAction.HIT, targetId)

  private[server] def tickProjectiles(): Unit = {
    if (!running || world == null) return
    val tickStart = System.nanoTime()

    val events = projectileManager.tick(world)
    events.foreach {
      case ProjectileMoved(projectile) =>
        broadcastBuffered(projectilePacket(projectile, ProjectileAction.MOVE))

      case ProjectileKill(projectile, targetId, flyingOn) =>
        // ProjectileManager reports only the blow that killed the target, so this is the one
        // place the death is scored
        Metrics.projectilesHit.add(1L, Attrs.projectileType(projectile.projectileType))
        broadcastBuffered(hitPacket(projectile, targetId, flyingOn))
        notifyAbilityHitForOwner(projectile)

        // Send player update with 0 health
        val target = registry.get(targetId)
        if (target != null) broadcastBuffered(stateUpdate(target))

        // Life-steal on killing blow
        ProjectileDef.get(projectile.projectileType).onHitEffect.foreach {
          case LifeSteal(healPercent) => lifeSteal(projectile, healPercent)
          case _ => // no life-steal
        }

        recordKill(projectile.ownerId, targetId, projectile.projectileType, Attrs.CauseProjectile)

      case ProjectileHit(projectile, targetId, flyingOn) =>
        Metrics.projectilesHit.add(1L, Attrs.projectileType(projectile.projectileType))
        broadcastBuffered(hitPacket(projectile, targetId, flyingOn))
        notifyAbilityHitForOwner(projectile)

        val target = registry.get(targetId)
        if (target != null && !target.isDead) {
          // Apply type-specific on-hit effects from ProjectileDef (skip dead targets)
          ProjectileDef.get(projectile.projectileType).onHitEffect.foreach {
            case PullToOwner => pullToOwner(projectile, target)

            case Freeze(durationMs) =>
              if (target.tryFreeze(durationMs)) holdByServer(target)

            case Stun(durationMs) =>
              if (target.tryStun(durationMs)) holdByServer(target)

            case TeleportOwnerBehind(distance, freezeDurationMs) =>
              if (target.tryFreeze(freezeDurationMs)) holdByServer(target)
              val owner = registry.get(projectile.ownerId)
              if (owner != null) {
                val targetPos = target.getPosition
                val (bdx, bdy) = target.getDirection match {
                  case Direction.Up    => (0, 1)
                  case Direction.Down  => (0, -1)
                  case Direction.Left  => (1, 0)
                  case Direction.Right => (-1, 0)
                }
                val destX = Math.max(0, Math.min(world.width - 1, targetPos.getX + bdx * distance))
                val destY = Math.max(0, Math.min(world.height - 1, targetPos.getY + bdy * distance))
                if (world.isWalkable(destX, destY)) {
                  moveByServer(owner, new Position(destX, destY))
                  broadcastBuffered(stateUpdate(owner))
                }
              }

            case Push(pushDistance) =>
              // Straight back from whoever fired it
              val pushOwner = registry.get(projectile.ownerId)
              if (pushOwner != null) {
                val from = pushOwner.getPosition
                pushFrom(target, from.getX.toFloat, from.getY.toFloat, pushDistance)
              }

            case LifeSteal(healPercent) =>
              lifeSteal(projectile, healPercent)
              // Bat Swarm also applies a brief freeze
              if (projectile.projectileType == ProjectileType.BAT_SWARM && target.tryFreeze(600)) {
                holdByServer(target)
              }

            case Burn(totalDamage, durationMs, tickMs) =>
              target.applyBurn(totalDamage, durationMs, tickMs, projectile.ownerId)

            case Poison(totalDamage, durationMs, tickMs) =>
              target.applyPoison(totalDamage, durationMs, tickMs, projectile.ownerId)

            case VortexPull(radius, pullStrength) =>
              // Pull all nearby enemies toward the hit location
              val vx = projectile.getX
              val vy = projectile.getY
              projectileManager.forEachNearbyPlayer(vx, vy, radius) { nearby =>
                if (!nearby.isDead && !nearby.isPhased && !nearby.getId.equals(projectile.ownerId) && !isTeammate(projectile.ownerId, nearby.getId) &&
                    !projectileManager.shelteredFromBlast(projectile.ownerId, vx, vy, nearby)) {
                  if (pullToward(nearby, vx, vy, radius, pullStrength) && (nearby ne target)) {
                    broadcastBuffered(stateUpdate(nearby))
                  }
                }
              }

            case Root(durationMs) =>
              if (target.tryRoot(durationMs)) holdByServer(target)

            case Slow(durationMs, multiplier) =>
              target.trySlow(durationMs, multiplier)

            case SpeedBoost(durationMs) => boostOwner(projectile, durationMs)
          }

          broadcastBuffered(stateUpdate(target))
        }

      case ProjectileAoE(projectile) =>
        // Broadcast despawn (client renders explosion for GRENADE/ROCKET)
        broadcastBuffered(projectilePacket(projectile, ProjectileAction.DESPAWN))

        // Determine blast parameters from ProjectileDef
        val pDef = ProjectileDef.get(projectile.projectileType)
        val explosion = pDef.explosionConfig.getOrElse(ExplosionConfig(40, 10, 3.0f))
        val (centerDmg, edgeDmg, blastRadius) = (explosion.centerDamage, explosion.edgeDamage, explosion.blastRadius)

        // Find all players in blast radius (skip if explosion deals no damage, e.g. visual-only snare mine)
        val explosionX = projectile.getX
        val explosionY = projectile.getY
        if (centerDmg > 0 || edgeDmg > 0)
        projectileManager.forEachNearbyPlayer(explosionX, explosionY, blastRadius) { player =>
          // Nor through a barrier: one it went off in front of shelters whoever is behind it
          if (!player.isDead && !player.hasShield && !player.isPhased && !player.getId.equals(projectile.ownerId) && !isTeammate(projectile.ownerId, player.getId) &&
              !projectileManager.shelteredFromBlast(projectile.ownerId, explosionX, explosionY, player)) {
            val distance = Projectile.distanceToPlayer(explosionX, explosionY, player)
            if (distance <= blastRadius) {
              val damage = (centerDmg - (distance / blastRadius) * (centerDmg - edgeDmg)).toInt
              val killed = player.damage(damage)
              broadcastBuffered(projectilePacket(projectile, ProjectileAction.HIT, player.getId))
              notifyAbilityHitForOwner(projectile)
              broadcastBuffered(stateUpdate(player))
              if (killed) recordKill(projectile.ownerId, player.getId, projectile.projectileType, Attrs.CauseAoe)
            }
          }
        }

      case ProjectileAoEHit(projectile, targetId, damage, held) =>
        Metrics.projectilesHit.add(1L, Attrs.projectileType(projectile.projectileType))
        broadcastBuffered(projectilePacket(projectile, ProjectileAction.HIT, targetId))
        notifyAbilityHitForOwner(projectile)

        val aoeTarget = registry.get(targetId)
        if (aoeTarget != null) {
          // A blast carries the same on-hit effect a direct hit would. All but the holds used to
          // be dropped here, so a slam could neither push nor pull, the Necromancer's Soul
          // Harvest never healed him and the Cyborg's Overclock boosted nobody.
          val pDef = ProjectileDef.get(projectile.projectileType)
          var heldHere = held
          pDef.onHitEffect.foreach {
            case Freeze(durationMs) => if (aoeTarget.tryFreeze(durationMs)) heldHere = true
            case Stun(durationMs) => if (aoeTarget.tryStun(durationMs)) heldHere = true
            case Root(durationMs) => if (aoeTarget.tryRoot(durationMs)) heldHere = true
            case Slow(durationMs, multiplier) => aoeTarget.trySlow(durationMs, multiplier)
            case Burn(totalDamage, durationMs, tickMs) => aoeTarget.applyBurn(totalDamage, durationMs, tickMs, projectile.ownerId)
            case Poison(totalDamage, durationMs, tickMs) => aoeTarget.applyPoison(totalDamage, durationMs, tickMs, projectile.ownerId)
            case VortexPull(radius, pullStrength) =>
              // A vortex that bursts where it lands (Vortex Bomb) pulls in what its blast caught.
              // It passes through players, so it never lands a direct hit: skipped here like the
              // other positional effects, its pull never happened at all.
              pullToward(aoeTarget, projectile.getX, projectile.getY, radius, pullStrength)
            case Push(pushDistance) => pushFrom(aoeTarget, projectile.getX, projectile.getY, pushDistance)
            case PullToOwner => pullToOwner(projectile, aoeTarget)
            case LifeSteal(healPercent) =>
              // Once per victim, off what the blast took from them: the projectile's own damage
              // is 0 for a slam, so healing by that healed nothing
              lifeSteal(projectile, healPercent, damage)
            case SpeedBoost(durationMs) => boostOwner(projectile, durationMs)
            case TeleportOwnerBehind(_, _) => // one target to land behind, not a crowd: direct hits only
          }
          if (heldHere) holdByServer(aoeTarget)
          broadcastBuffered(stateUpdate(aoeTarget))
        }

      case ProjectileAoEKill(projectile, targetId, damage) =>
        Metrics.projectilesHit.add(1L, Attrs.projectileType(projectile.projectileType))
        broadcastBuffered(projectilePacket(projectile, ProjectileAction.HIT, targetId))
        notifyAbilityHitForOwner(projectile)

        val aoeKillTarget = registry.get(targetId)
        if (aoeKillTarget != null) broadcastBuffered(stateUpdate(aoeKillTarget))

        ProjectileDef.get(projectile.projectileType).onHitEffect.foreach {
          case LifeSteal(healPercent) => lifeSteal(projectile, healPercent, damage)
          case _ => // no life-steal
        }

        recordKill(projectile.ownerId, targetId, projectile.projectileType, Attrs.CauseAoe)

      case ProjectileDespawned(projectile) =>
        broadcastBuffered(projectilePacket(projectile, ProjectileAction.DESPAWN))
        Metrics.projectilesExpired.add(1L, Attrs.projectileType(projectile.projectileType))

      case ProjectileBlocked(projectile, barrierOwner) =>
        // Where it met the barrier, and whose it was
        broadcastBuffered(projectilePacket(projectile, ProjectileAction.BLOCKED, barrierOwner))
    }
    // Traps on the ground: the ones that have run out, and the ones somebody is standing on.
    // A player who walked onto one between ticks has already sprung it (ClientHandler); this
    // catches everyone standing on one, bots and players the server itself moved included.
    applyTrapEvents(trapManager.tick(System.currentTimeMillis()))
    // Flush all buffered writes at end of tick
    flushAllInstancePlayers()
    Metrics.tickDuration.record((System.nanoTime() - tickStart) / 1e6, projectileTickAttrs)
  }

  // ── Traps ────────────────────────────────────────────────────────────────

  private def trapPacket(trap: Trap, action: Byte, victimId: UUID = null): TrapPacket =
    new TrapPacket(server.getNextSequenceNumber, trap.ownerId, Packet.getCurrentTimestamp,
      trap.x, trap.y, trap.id, action, trap.trapType, trap.teamId, 0, victimId)

  /** Tell everyone about a trap: it is on the ground, or it is gone. */
  private[server] def broadcastTrap(trap: Trap, action: Byte): Unit =
    broadcastToInstance(trapPacket(trap, action))

  /** Every client is sent every trap; their own and their allies' are drawn plainly, everyone
    * else's faintly, so a trap is findable if you look for it. */
  private[server] def sendTrapsTo(player: Player): Unit = {
    var sent = false
    trapManager.forEachTrap { trap =>
      server.sendRawToPlayerBuffered(trapPacket(trap, TrapAction.SPAWN).serialize(), true, player)
      sent = true
    }
    if (sent) server.flushPlayer(player)
  }

  /**
   * What a pass over the traps turned up. Called from the projectile tick and, for a player who
   * stepped onto one, from the network thread that took their update: the trap itself was
   * already consumed by whichever of them got to it (TrapManager.claim), so the two can't both
   * spring one.
   */
  private[server] def applyTrapEvents(events: Seq[TrapEvent]): Unit = events.foreach {
    case TrapGone(trap) => broadcastTrap(trap, TrapAction.REMOVE)
    case TrapSprung(trap, victimId) => springTrap(trap, victimId)
  }

  private def springTrap(trap: Trap, victimId: UUID): Unit = {
    broadcastToInstance(trapPacket(trap, TrapAction.TRIGGER, victimId))
    Metrics.trapsSprung.add(1L, Attrs.trapType(trap.trapType))
    val tDef = trap.defn
    if (tDef == null) return
    tDef.explosion match {
      case Some(blast) => trapBlast(trap, blast)
      case None =>
        val victim = registry.get(victimId)
        if (victim != null && !victim.isDead) {
          val killed = tDef.damage > 0 && victim.damage(tDef.damage)
          // Its hold obeys CC immunity like any other, and it is spent either way
          if (!killed && !victim.isDead) applyTrapEffects(trap, victim, tDef)
          broadcastToInstance(stateUpdate(victim))
          if (killed) recordKill(trap.ownerId, victimId, 0.toByte, Attrs.CauseTrap)
        }
    }
  }

  private def applyTrapEffects(trap: Trap, victim: Player, tDef: TrapDef): Unit = {
    var held = false
    tDef.effects.foreach {
      case Stun(durationMs) => if (victim.tryStun(durationMs)) held = true
      case Freeze(durationMs) => if (victim.tryFreeze(durationMs)) held = true
      case Root(durationMs) => if (victim.tryRoot(durationMs)) held = true
      case Slow(durationMs, multiplier) => victim.trySlow(durationMs, multiplier)
      case Burn(totalDamage, durationMs, tickMs) => victim.applyBurn(totalDamage, durationMs, tickMs, trap.ownerId)
      case Poison(totalDamage, durationMs, tickMs) => victim.applyPoison(totalDamage, durationMs, tickMs, trap.ownerId)
      case Push(distance) => pushFrom(victim, trap.x.toFloat, trap.y.toFloat, distance)
      case _ => // nothing to pull a victim to, and nobody holding it to heal
    }
    if (held) holdByServer(victim)
  }

  /**
   * A trap that explodes, with the same falloff a projectile's blast has and the same shelter
   * behind a barrier. It walks the registry rather than the projectile tick's spatial grid and
   * checks barriers against the players themselves rather than that tick's snapshot of them,
   * because a trap goes off on whichever thread stepped on it — and those two are the
   * projectile tick's own, rebuilt in place every 30ms.
   */
  private def trapBlast(trap: Trap, blast: ExplosionConfig): Unit = {
    val bx = trap.x.toFloat
    val by = trap.y.toFloat
    registry.forEachPlayer { player =>
      if (!player.isDead && !player.hasShield && !player.isPhased &&
          !player.getId.equals(trap.ownerId) && !isTeammate(trap.ownerId, player.getId)) {
        val distance = Projectile.distanceToPlayer(bx, by, player)
        if (distance <= blast.blastRadius && !shelteredFromTrap(trap.ownerId, bx, by, player)) {
          val damage = (blast.centerDamage -
            (distance / blast.blastRadius) * (blast.centerDamage - blast.edgeDamage)).toInt
          val killed = player.damage(damage)
          broadcastToInstance(stateUpdate(player))
          if (killed) recordKill(trap.ownerId, player.getId, 0.toByte, Attrs.CauseTrap)
        }
      }
    }
  }

  /** Does a barrier stand between a trap's blast at (x, y) and this player? The same rule
    * ProjectileManager applies to a projectile's blast, read off the holders themselves. */
  private def shelteredFromTrap(ownerId: UUID, x: Float, y: Float, player: Player): Boolean = {
    val pos = player.getPosition
    val px = pos.getX.toFloat
    val py = pos.getY.toFloat
    var sheltered = false
    registry.forEachPlayer { holder =>
      if (!sheltered && holder.hasBarrier && !holder.isDead && !holder.isPhased &&
          !holder.getId.equals(ownerId) && !isTeammate(ownerId, holder.getId)) {
        val hp = holder.getPosition
        if (Barrier.crosses(hp.getX.toFloat, hp.getY.toFloat, holder.getBarrierAngle, x, y, px, py)) {
          sheltered = true
        }
      }
    }
    sheltered
  }

  /** Knock a player `cells` straight back from (fromX, fromY), stopping at the first wall rather
    * than coming out on its far side. */
  private def pushFrom(p: Player, fromX: Float, fromY: Float, cells: Float): Unit = {
    val pos = p.getPosition
    val pdx = pos.getX - fromX
    val pdy = pos.getY - fromY
    val dist = Math.sqrt(pdx * pdx + pdy * pdy)
    if (dist <= 0.01) return
    val dest = Teleport.slide(world, pos.getX, pos.getY, pdx / dist, pdy / dist, cells.toInt)
    if (dest != pos) moveByServer(p, dest)
  }

  /** Drag a player onto the cell the projectile's owner is standing on. */
  private def pullToOwner(projectile: Projectile, p: Player): Unit = {
    val owner = registry.get(projectile.ownerId)
    if (owner == null) return
    val ownerPos = owner.getPosition
    if (world.isWalkable(ownerPos.getX, ownerPos.getY) && ownerPos != p.getPosition) moveByServer(p, ownerPos)
  }

  /** Speed the projectile's owner up — the effect lands on whoever fired it, not on what it hit. */
  private def boostOwner(projectile: Projectile, durationMs: Int): Unit = {
    val owner = registry.get(projectile.ownerId)
    if (owner != null && !owner.isDead) {
      owner.setSpeedBoostUntil(System.currentTimeMillis() + durationMs)
      broadcastBuffered(stateUpdate(owner))
    }
  }

  /** Pull a player up to `strength` cells toward (vx, vy), if they are within `radius` of it,
    * stopping at walls. Returns whether they moved. */
  private def pullToward(p: Player, vx: Float, vy: Float, radius: Float, strength: Float): Boolean = {
    val pos = p.getPosition
    val dist = Projectile.distanceToPlayer(vx, vy, p)
    if (dist <= 0.1f || dist > radius) return false
    val dest = Teleport.slide(world, pos.getX, pos.getY, (vx - pos.getX) / dist, (vy - pos.getY) / dist,
      Math.min(strength, dist).toInt)
    if (dest == pos) return false
    moveByServer(p, dest)
    true
  }

  /** Heal the owner of a life-stealing projectile by its share of the damage the hit dealt. */
  private def lifeSteal(projectile: Projectile, healPercent: Int): Unit = {
    val pDef = ProjectileDef.get(projectile.projectileType)
    lifeSteal(projectile, healPercent, pDef.effectiveDamage(projectile.chargeLevel, projectile.getDistanceTraveled))
  }

  /** The same, for a blast: a slam's own damage is 0, so it has to be told what its splash took. */
  private def lifeSteal(projectile: Projectile, healPercent: Int, damage: Int): Unit = {
    val lsOwner = registry.get(projectile.ownerId)
    if (lsOwner != null && !lsOwner.isDead) {
      val healAmount = damage * healPercent / 100
      lsOwner.synchronized {
        lsOwner.setHealth(Math.min(lsOwner.getMaxHealth, lsOwner.getHealth + healAmount))
      }
      broadcastBuffered(stateUpdate(lsOwner))
    }
  }

  /**
   * Score a death: the kill (when the killer is known), the kill feed, and the victim's respawn.
   * Callers only get here for the blow that took the victim from alive to dead
   * (Player.damage), which is what keeps two hits, or a hit and a burn tick on another thread,
   * from scoring one death twice and scheduling two respawns.
   */
  private def recordKill(killerId: UUID, victimId: UUID, projectileType: Byte,
                         cause: io.opentelemetry.api.common.Attributes): Unit = {
    if (killerId != null) {
      killTracker.recordKill(killerId, victimId)
      val killer = registry.get(killerId)
      val victim = registry.get(victimId)
      Metrics.kills.add(1L, Attrs.killCombo(
        if (killer != null) killer.getCharacterId else 0,
        if (victim != null) victim.getCharacterId else 0,
        projectileType
      ))
      Metrics.deaths.add(1L, cause)
      broadcastToInstance(new GameEventPacket(
        server.getNextSequenceNumber,
        killerId,
        GameEvent.KILL,
        gameId,
        getRemainingSeconds,
        killTracker.getKills(killerId).toShort,
        killTracker.getDeaths(killerId).toShort,
        victimId,
        0.toByte, 0.toShort, 0.toShort
      ))
    }
    scheduleRespawn(victimId)
  }

  /** Mirror client-side on-hit ability cooldown reduction so server fire-rate validation stays in sync. */
  private def notifyAbilityHitForOwner(projectile: Projectile): Unit = {
    val owner = registry.get(projectile.ownerId)
    if (owner != null) {
      handler.notifyAbilityHit(projectile.ownerId, projectile.projectileType, owner.getCharacterId)
    }
  }

  private[server] def playerFlags(p: Player): Int =
    (if (p.hasShield) 0x01 else 0) |
    (if (p.hasGemBoost) 0x02 else 0) |
    (if (p.isFrozen) 0x04 else 0) |
    (if (p.isPhased) 0x08 else 0) |
    (if (p.isBurning) 0x10 else 0) |
    (if (p.hasSpeedBoost) 0x20 else 0) |
    (if (p.isRooted) 0x40 else 0) |
    (if (p.isSlowed) 0x80 else 0)

  /** The status flags that didn't fit in the first byte. */
  private[server] def playerFlags2(p: Player): Int =
    (if (p.isStunned) 0x01 else 0) |
    (if (p.isPoisoned) 0x02 else 0) |
    (if (p.hasBarrier) 0x04 else 0)

  /** How slow a slow has this player, as a percentage, so their client steps at the real rate. */
  private def slowPercent(p: Player): Int =
    if (!p.isSlowed) 0 else Math.max(1, Math.min(100, Math.round(p.getSlowMultiplier * 100f)))

  /**
   * A player as the server has them, for everyone to be told: position, health, every status
   * flag, character, team, and how many times the server has moved them. Every update the
   * server sends about a player goes through here; each used to be built by hand, and several
   * carried a few of the flags, so a burning, rooted or slowed player's effects blinked off on
   * other screens whenever one of those went out.
   */
  private[server] def stateUpdate(p: Player): PlayerUpdatePacket =
    new PlayerUpdatePacket(
      server.getNextSequenceNumber,
      p.getId,
      Packet.getCurrentTimestamp,
      p.getPosition,
      p.getColorRGB,
      p.getHealth,
      p.getChargeLevel,
      playerFlags(p),
      p.getCharacterId,
      p.getTeamId,
      p.getServerMoves,
      playerFlags2(p),
      // Which way a raised barrier faces, so every client turns it as its holder does
      if (p.hasBarrier) PlayerUpdatePacket.encodeAimAngle(p.getBarrierAngle) else 0,
      slowPercent(p)
    )

  /**
   * Put a player somewhere their client didn't: a pull, a knockback, a respawn. The move is
   * counted and the player told over TCP, so it can't be lost; their client takes the position
   * from the first update carrying the new count, and every step it sent before seeing it is
   * dropped here (ClientHandler), instead of dragging the player back — which a push used to be.
   */
  private[server] def moveByServer(p: Player, pos: Position): Unit = {
    placeByServer(p, pos)
    sendStateToPlayer(p)
  }

  private def placeByServer(p: Player, pos: Position): Unit = p.synchronized {
    p.setPosition(pos)
    p.recordServerMove()
  }

  /** A freeze or root holds the player where the server has them: their client, which may have
    * stepped on before it heard, goes back to that cell. Counted like a move. */
  private[server] def holdByServer(p: Player): Unit = {
    p.recordServerMove()
    sendStateToPlayer(p)
  }

  private[server] def sendStateToPlayer(p: Player): Unit = {
    try server.sendRawToPlayer(stateUpdate(p).serialize(), true, p)
    catch { case _: Exception => }
  }

  // What one damage-over-time tick did, without allocating an Option per player per tick
  private val DOT_NOT_DUE = 0
  private val DOT_TICKED = 1
  private val DOT_KILLED = 2

  /**
   * One tick of a player's burn, if it is due. The state is read and written under the player's
   * lock so two ticks can't both take the same one.
   */
  private def tickBurn(player: Player, now: Long): Int = player.synchronized {
    if (!player.isBurning || now < player.getLastBurnTick + player.getBurnTickMs) DOT_NOT_DUE
    else {
      player.setLastBurnTick(now)
      if (player.damage(player.getBurnDamagePerTick)) DOT_KILLED else DOT_TICKED
    }
  }

  /** One tick of a player's poison, if it is due, under the same lock. */
  private def tickPoison(player: Player, now: Long): Int = player.synchronized {
    val dmg = player.takePoisonTick(now)
    if (dmg < 0) DOT_NOT_DUE
    else if (player.damage(dmg)) DOT_KILLED
    else DOT_TICKED
  }

  /** Tick player state (burn and poison DoTs + health regen). Runs every 200ms. */
  private[server] def tickPlayers(): Unit = {
    if (!running) return
    val tickStart = System.nanoTime()
    val now = System.currentTimeMillis()
    registry.forEachPlayer { player =>
      if (!player.isDead) {
        // --- Damage over time: a burn and a poison run side by side ---
        val burn = tickBurn(player, now)
        if (burn == DOT_KILLED) recordKill(player.getBurnOwnerId, player.getId, 0.toByte, Attrs.CauseBurn)
        val poison = if (player.isDead) DOT_NOT_DUE else tickPoison(player, now)
        if (poison == DOT_KILLED) recordKill(player.getPoisonOwnerId, player.getId, 0.toByte, Attrs.CausePoison)
        if (burn != DOT_NOT_DUE || poison != DOT_NOT_DUE) {
          // Broadcast updated health. No regen on a tick a DoT took a bite out of them. The update
          // for a poison's last tick goes out without the flag: that is how clients hear it is over.
          broadcastToInstance(stateUpdate(player))
        } else if (player.isPoisoned) {
          // No regen while a poison lasts, between its ticks as much as on them
          player.resetRegenAccumulator()
        } else if (player.getHealth < player.getMaxHealth) {
          // --- Health Regen (only when no DoT ticked, no poison, and not at full health) ---
          val maxHp = player.getMaxHealth
          val regenPerSec = 3.0 - (maxHp - 70) * (2.0 / 50.0)
          player.addRegenAccumulator(regenPerSec * 0.2) // 200ms tick
          val accum = player.getRegenAccumulator
          if (accum >= 1.0) {
            val healAmount = accum.toInt
            val healed = player.synchronized {
              val oldHealth = player.getHealth
              // A blow may have landed since the check above: the dead don't regenerate
              if (oldHealth <= 0) false
              else {
                player.setHealth(Math.min(maxHp, oldHealth + healAmount))
                player.getHealth != oldHealth
              }
            }
            player.subtractRegenAccumulator(healAmount.toDouble)
            if (healed) broadcastToInstance(stateUpdate(player))
          }
        } else {
          // At full health, reset accumulator
          player.resetRegenAccumulator()
        }
      }
    }
    Metrics.tickDuration.record((System.nanoTime() - tickStart) / 1e6, playerTickAttrs)
  }

  private def scheduleRespawn(playerId: UUID): Unit = {
    if (respawnExecutor == null || respawnExecutor.isShutdown) return
    respawnExecutor.schedule(new Runnable {
      def run(): Unit = respawn(playerId)
    }, (if (isPractice) 1000L else Constants.RESPAWN_DELAY_MS.toLong), TimeUnit.MILLISECONDS)
  }

  /** Bring a dead player back at a spawn point, with nothing carried over from their last life. */
  private[server] def respawn(playerId: UUID): Unit = {
    if (!running) return
    val player = registry.get(playerId)
    if (player == null) return
    val spawnPoint = spawnLock.synchronized {
      player.setHealth(player.getMaxHealth)
      player.setDirection(Direction.Down)
      player.clearBurn()
      player.clearPoison()
      player.clearSlow()
      player.setRootedUntil(0)
      player.setFrozenUntil(0)
      player.setStunnedUntil(0)
      player.setSpeedBoostUntil(0)
      // The client drops these on respawn too. Kept here, a shield or phase still running from
      // the last life made the new one briefly unhittable, and a gem boost doubled the speed
      // of shots the client fired as unboosted.
      player.setShieldUntil(0)
      player.setGemBoostUntil(0)
      player.setPhasedUntil(0)
      // Down, and ready: the client starts every life with its abilities off cooldown, and the
      // clocks the server holds them to are cleared with it (handler.resetAttacks below)
      player.setBarrierUntil(0)
      player.setBarrierRaisedAt(0)
      player.resetRegenAccumulator()
      // Clear inventory on respawn
      itemManager.clearInventory(playerId)
      val occupied = {
        val set = scala.collection.mutable.HashSet[(Int, Int)]()
        registry.forEachPlayer { p =>
          if (!p.isDead && !p.getId.equals(playerId)) {
            set += ((p.getPosition.getX, p.getPosition.getY))
          }
        }
        set.toSet
      }
      handler.resetAttacks(playerId)
      val sp = world.getValidSpawnPoint(occupied)
      // Placed inside the lock, so a respawn at the same moment sees this spawn as taken. It is
      // a server move: anything the client sent from its last life is stale.
      placeByServer(player, sp)
      sp
    }

    Metrics.respawns.add(1L, io.opentelemetry.api.common.Attributes.empty())
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
    sendStateToPlayer(player)

    // Broadcast updated player state
    broadcastToInstance(stateUpdate(player))
  }

  private def spawnItemBatch(count: Int): Unit = {
    if (!running || world == null) return
    val zeroUUID = new UUID(0L, 0L)
    var spawned = false
    var i = 0
    while (i < count) {
      itemManager.spawnRandomItem(world).foreach { event =>
        val packet = new ItemPacket(
          server.getNextSequenceNumber,
          zeroUUID,
          event.item.x, event.item.y,
          event.item.itemType.id,
          event.item.id,
          ItemAction.SPAWN
        )
        broadcastBuffered(packet)
        spawned = true
        Metrics.itemsSpawned.add(1L, Attrs.itemTypeAttrs(event.item.itemType.id))
      }
      i += 1
    }
    if (spawned) flushAllInstancePlayers()
  }

  private def syncTimer(): Unit = {
    if (!running) return
    val tickStart = System.nanoTime()

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
    Metrics.tickDuration.record((System.nanoTime() - tickStart) / 1e6, timerTickAttrs)
  }

  def broadcastToInstance(packet: Packet): Unit = {
    broadcastToInstanceExcluding(packet, null)
  }

  def broadcastToInstanceExcluding(packet: Packet, excludePlayerId: UUID): Unit = {
    val data = packet.serialize()
    val isTcp = packet.getType.tcp
    registry.forEachPlayer { player =>
      if (excludePlayerId == null || !player.getId.equals(excludePlayerId)) {
        try {
          server.sendRawToPlayer(data, isTcp, player)
        } catch {
          case _: Exception => // Skip disconnected player; don't crash broadcast loop
        }
      }
    }
  }

  /** Buffered broadcast: write without flushing. Call flushAllInstancePlayers() after the batch. */
  private def broadcastBuffered(packet: Packet): Unit = {
    val data = packet.serialize()
    val isTcp = packet.getType.tcp
    registry.forEachPlayer { player =>
      try {
        server.sendRawToPlayerBuffered(data, isTcp, player)
      } catch {
        case _: Exception =>
      }
    }
  }

  /** Flush all channels after a batch of buffered broadcasts. */
  private def flushAllInstancePlayers(): Unit = {
    registry.forEachPlayer { player =>
      try { server.flushPlayer(player) } catch { case _: Exception => }
    }
    server.flushUdpChannel()
  }

  def broadcastProjectileSpawn(projectile: Projectile): Unit = {
    broadcastToInstance(projectilePacket(projectile, ProjectileAction.SPAWN))
    Metrics.projectilesSpawned.add(1L, Attrs.projectileType(projectile.projectileType))
    applyCastSelfBuff(projectile)
  }

  /**
   * A slam that buffs its caster does it on the cast, not on a hit. The Cyborg's Overclock is a
   * 0-damage ring whose on-hit effect is a speed boost, so it only sped him up when an enemy
   * happened to be standing inside it — which, for an ability that is entirely a self-buff, is
   * never when it matters.
   */
  private def applyCastSelfBuff(projectile: Projectile): Unit =
    ProjectileDef.get(projectile.projectileType).onHitEffect match {
      case Some(SpeedBoost(durationMs)) =>
        val owner = registry.get(projectile.ownerId)
        if (owner != null && !owner.isDead && castsAsSlam(owner, projectile.projectileType)) {
          owner.setSpeedBoostUntil(System.currentTimeMillis() + durationMs)
          broadcastToInstance(stateUpdate(owner))
        }
      case _ => // a travelling projectile buffs on the hit, as before
    }

  /** Does this player's Q or E throw this projectile down as a ground slam? */
  private def castsAsSlam(p: Player, projectileType: Byte): Boolean = {
    def isSlam(a: AbilityDef): Boolean =
      a.projectileType == projectileType && (a.castBehavior match {
        case GroundSlam(_) => true
        case _ => false
      })
    val charDef = CharacterDef.get(p.getCharacterId)
    charDef != null && (isSlam(charDef.qAbility) || isSlam(charDef.eAbility))
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
    Metrics.itemsPickedUp.add(1L, Attrs.itemTypeAttrs(item.itemType.id))
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
    Metrics.tilesModified.add(1L, io.opentelemetry.api.common.Attributes.of(Attrs.ItemType, "tile_" + tileId))
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
