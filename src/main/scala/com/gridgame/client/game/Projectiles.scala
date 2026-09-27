package com.gridgame.client.game

import com.gridgame.client.audio.AudioManager
import com.gridgame.common.model._
import com.gridgame.common.protocol._
import GameClient._

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * The projectiles in flight, as the server tells us of them (PROJECTILE_UPDATE: spawned, moved,
 * hit, stopped, blocked), each flown between the server's ticks so it moves smoothly
 * ([[flyProjectiles]], NetProjectile), and each stopped one kept a moment longer to fade where it
 * stopped. A hit, a splash and an explosion are handed on as what the renderer shows of them
 * (EventAnimations) and the sound they make.
 */
trait Projectiles { this: GameClient =>
  private val projectiles: ConcurrentHashMap[Int, Projectile] = new ConcurrentHashMap()

  // Projectiles stopped by terrain or the end of their range: kept for TerrainImpact.FADE_MS
  // where they stopped, so they read as absorbed rather than blinking out. The renderer drops
  // expired entries.
  private val fadingProjectiles: ConcurrentHashMap[Int, FadingProjectile] = new ConcurrentHashMap()

  // Which of the server's projectile ticks it is now: projectiles are flown between their packets
  // on it (NetProjectile, flyProjectiles)
  private val projectileTimeline = new ProjectileTimeline

  // The clock projectile packets are stamped with as they arrive, and the renderer flies them by.
  // A test's own, in a test
  @volatile private[client] var nanoClock: () => Long = () => System.nanoTime()

  // Track recently removed projectile IDs to prevent UDP MOVE packets from resurrecting them
  private val recentlyRemovedProjectiles: java.util.Set[Int] =
    java.util.Collections.newSetFromMap(new ConcurrentHashMap[Int, java.lang.Boolean]())

  /** A projectile as the server first told us of it: fired (its SPAWN), or already in flight (a
    * MOVE that overtook the SPAWN, or came instead of a lost one). */
  private def netProjectile(packet: ProjectilePacket, spawn: Boolean, now: Long): NetProjectile =
    new NetProjectile(packet.getProjectileId, packet.getPlayerId, packet.getX, packet.getY,
      packet.getDx, packet.getDy, packet.getColorRGB, packet.getChargeLevel, packet.getProjectileType,
      packet.getTick, spawn, projectileStepsOf(packet.getPlayerId), getWorld, now)

  /** How many times a tick the server moves this player's projectiles: twice while they have a gem. */
  private def projectileStepsOf(ownerId: UUID): Int = {
    val boosted =
      if (ownerId.equals(localPlayerId)) hasGemBoost
      else { val p = players.get(ownerId); p != null && p.hasGemBoost }
    if (boosted) 2 else 1
  }

  /** The server's news of one of the projectiles it is flying. */
  private def heardOf(projectile: Projectile, packet: ProjectilePacket, now: Long): Unit = projectile match {
    case np: NetProjectile =>
      np.heard(packet.getTick, packet.getX, packet.getY, packet.getDx, packet.getDy, spawn = false,
        projectileStepsOf(packet.getPlayerId), getWorld, now)
    case p => p.updatePosition(packet.getX, packet.getY, packet.getDx, packet.getDy)
  }

  /** It stopped at (x, y), and is drawn there while it fades. */
  private def stopProjectile(projectile: Projectile, x: Float, y: Float): Unit = projectile match {
    case np: NetProjectile => np.stopAt(x, y)
    case p => p.updatePosition(x, y, p.dx, p.dy)
  }

  /**
   * Fly every projectile to where it is now, between the server's ticks (NetProjectile): the
   * renderer calls this once a frame, before it draws any. A projectile nothing has been heard of
   * for a while was ended by a packet that was lost on the way — every packet about projectiles
   * is a datagram — and fades out where it is, where it used to stay frozen for the rest of the
   * match. Not marked as removed, so if it was only the news that stalled, its next move brings it
   * back.
   */
  def flyProjectiles(nowNanos: Long, deltaSec: Double): Unit = {
    if (projectiles.isEmpty) return
    val t = projectileTimeline.tickAt(nowNanos)
    val decay = Math.exp(-deltaSec * 1000.0 / NetProjectile.SMOOTH_MS).toFloat
    val blockers = barriersInFlight()
    val it = projectiles.values().iterator()
    while (it.hasNext) {
      it.next() match {
        case np: NetProjectile =>
          if (np.fly(t, decay, nowNanos, blockers) == NetProjectile.EXPIRED && projectiles.remove(np.id, np))
            fadingProjectiles.put(np.id, new FadingProjectile(np, System.currentTimeMillis(), false, 0))
        case _ => // one a dev tool flies itself
      }
    }
  }

  private[game] def handleProjectileUpdate(packet: ProjectilePacket): Unit = {
    val projectileId = packet.getProjectileId
    val now = nanoClock()

    packet.getAction match {
      case ProjectileAction.SPAWN =>
        recentlyRemovedProjectiles.remove(projectileId)
        projectileTimeline.heardSpawn(packet.getTick, now)
        // A MOVE that overtook it has put the projectile further on already
        if (projectiles.get(projectileId) == null)
          projectiles.put(projectileId, netProjectile(packet, spawn = true, now))
        AudioManager.playAttack(packet.getProjectileType, characterIdOf(packet.getPlayerId),
          distanceFromLocal(packet.getX, packet.getY), panFromLocal(packet.getX, packet.getY))
        // Periodic cleanup: evict oldest entries to prevent unbounded growth
        // (incremental eviction avoids clearing all entries which could resurrect projectiles)
        if (recentlyRemovedProjectiles.size() > 500) {
          val iter = recentlyRemovedProjectiles.iterator()
          var removed = 0
          while (iter.hasNext && removed < 250) {
            iter.next()
            iter.remove()
            removed += 1
          }
        }

      case ProjectileAction.MOVE =>
        projectileTimeline.heard(packet.getTick, now)
        val projectile = projectiles.get(projectileId)
        if (projectile == null) {
          // Only create if we haven't seen this projectile hit or stop already: every projectile
          // packet is a datagram, and a MOVE can arrive after the HIT or DESPAWN that ended it
          if (!recentlyRemovedProjectiles.contains(projectileId))
            projectiles.put(projectileId, netProjectile(packet, spawn = false, now))
        } else heardOf(projectile, packet, now)

      case ProjectileAction.HIT | ProjectileAction.PIERCE =>
        // A pierce passed through the target and keeps flying, so the projectile stays (its
        // MOVEs keep coming). Dropped here, it vanished at its first hit.
        if (packet.getAction == ProjectileAction.HIT) {
          projectiles.remove(projectileId)
          recentlyRemovedProjectiles.add(projectileId)
        }
        val hitPType = packet.getProjectileType
        val targetId = packet.getTargetId
        if (targetId != null) {
          recordHit(targetId, packet.getColorRGB, packet.getDx, packet.getDy, hitPType, System.currentTimeMillis())

          // If our projectile hit another player, reduce ability cooldown by 50%
          if (packet.getPlayerId.equals(localPlayerId)) {
            reduceAbilityCooldownOnHit(packet.getProjectileType)
          }

          if (targetId.equals(localPlayerId)) AudioManager.playHitTaken()
          else if (packet.getPlayerId.equals(localPlayerId)) AudioManager.playHitDealt()
          else AudioManager.playHitOther(
            distanceFromLocal(packet.getX, packet.getY), panFromLocal(packet.getX, packet.getY))
        }

        // A splash goes off where it struck. The HITs on everyone it caught come in the same tick
        // and are the same blast.
        val hitPDef = ProjectileDef.get(hitPType)
        hitPDef.aoeOnHit.foreach { aoe =>
          blast(blastKey(projectileId, packet.getTick), packet.getX, packet.getY, packet.getColorRGB,
            aoe.radius, hitPType, characterIdOf(packet.getPlayerId), trap = false)
        }

      case ProjectileAction.DESPAWN =>
        val despawned = projectiles.remove(projectileId)
        recentlyRemovedProjectiles.add(projectileId)
        val pType = if (despawned != null) despawned.projectileType else packet.getProjectileType
        val pDef = ProjectileDef.get(pType)
        val colorRGB = if (despawned != null) despawned.colorRGB else packet.getColorRGB
        // Where it actually met the terrain: the server removes it up to half a cell inside
        // the wall, so walk back to the face before showing anything there.
        val impact = TerrainImpact.resolve(getWorld, packet.getX, packet.getY,
          packet.getDx, packet.getDy, pDef.passesThroughWalls)
        val owner = if (despawned != null) despawned.ownerId else packet.getPlayerId
        if (pDef.isExplosive) {
          // An explosive always goes off, wherever it stopped: at the end of its range, on a wall
          // or at the edge of the map. One blast, the size of whichever of its blast and its
          // splash reaches further (a slam has both, the same).
          val radius = Math.max(pDef.explosionConfig.map(_.blastRadius).getOrElse(3f),
            pDef.aoeOnMaxRange.map(_.radius).getOrElse(0f))
          blast(blastKey(projectileId, packet.getTick), impact.x, impact.y, colorRGB, radius, pType,
            characterIdOf(owner), trap = false)
          // A bang only for one that deals its blast. A slam's, a web's and the ink's explosion is
          // there only to be drawn, and their own sound played when they were cast: a Wolf's howl
          // went off as a grenade. The bang is the explosive's own: napalm's is fire, a mud bomb's
          // a splat.
          if (pDef.explosionConfig.exists(e => e.centerDamage > 0 || e.edgeDamage > 0))
            AudioManager.playBlast(pType, characterIdOf(owner),
              distanceFromLocal(impact.x, impact.y), panFromLocal(impact.x, impact.y))
        } else {
          if (despawned != null) {
            // Stop it where it struck and let it sink into the surface there
            stopProjectile(despawned, impact.x, impact.y)
            fadingProjectiles.put(projectileId,
              new FadingProjectile(despawned, System.currentTimeMillis(), impact.hitTerrain, impact.tileColor))
          }
          // A splash at the end of its range. The server doesn't set one off when the terrain
          // stopped it first, so neither is one drawn there.
          if (!impact.hitTerrain) pDef.aoeOnMaxRange.foreach { aoe =>
            blast(blastKey(projectileId, packet.getTick), impact.x, impact.y, colorRGB, aoe.radius, pType,
              characterIdOf(owner), trap = false)
          }
        }

      case ProjectileAction.BLOCKED =>
        // Stopped on a barrier: it fades out where it met it (no puff, there is no terrain to kick
        // up), and the barrier ripples there
        val blocked = projectiles.remove(projectileId)
        recentlyRemovedProjectiles.add(projectileId)
        val nowMs = System.currentTimeMillis()
        if (blocked != null) {
          stopProjectile(blocked, packet.getX, packet.getY)
          fadingProjectiles.put(projectileId, new FadingProjectile(blocked, nowMs, false, 0))
        }
        // A barrier's holder, or nobody at all — the divider between the teams
        if (packet.getTargetId != null) recordBarrierImpact(packet.getTargetId, packet.getX, packet.getY, nowMs)
        else recordDividerImpact(packet.getX, packet.getY, nowMs)
        AudioManager.playBarrierBlock(distanceFromLocal(packet.getX, packet.getY), panFromLocal(packet.getX, packet.getY))

      case _ =>
        // Unknown action
    }
  }

  /** No match: nothing in flight, fading, or remembered as gone. */
  private[game] def forgetProjectiles(): Unit = {
    projectiles.clear()
    projectileTimeline.reset()
    fadingProjectiles.clear()
    recentlyRemovedProjectiles.clear()
  }

  def getProjectiles: ConcurrentHashMap[Int, Projectile] = projectiles

  def getFadingProjectiles: ConcurrentHashMap[Int, FadingProjectile] = fadingProjectiles
}
