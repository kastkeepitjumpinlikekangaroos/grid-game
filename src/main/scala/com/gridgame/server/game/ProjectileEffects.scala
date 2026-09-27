package com.gridgame.server.game

import com.gridgame.common.model._
import com.gridgame.common.observability.Attrs
import com.gridgame.common.observability.Metrics
import com.gridgame.common.protocol._

import java.util.UUID

/**
 * What projectiles do, as the projectile tick reports it (ProjectileManager): every move, hit, blast
 * and stop told to everyone, and a hit's effects applied — the holds, pulls, pushes, burns, poisons,
 * life steal and boosts its def names (ProjectileDef.onHitEffects), to a direct hit and to everyone
 * a blast catches alike.
 */
trait ProjectileEffects { this: GameInstance =>
  private def projectilePacket(projectile: Projectile, action: Byte, targetId: UUID = null): ProjectilePacket =
    new ProjectilePacket(
      outbox.nextSeq(),
      projectile.ownerId,
      projectileTick,
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

  def broadcastProjectileSpawn(projectile: Projectile): Unit = {
    broadcastToInstance(projectilePacket(projectile, ProjectileAction.SPAWN))
    Metrics.projectilesSpawned.add(1L, Attrs.projectileType(projectile.projectileType))
    applyCastSelfBuff(projectile)
  }

  /** What one thing a projectile did this tick means: a packet for everyone, and for a hit, its damage
    * already dealt (ProjectileManager), its effects, and a kill scored. */
  private[game] def applyProjectileEvent(event: ProjectileEvent): Unit = event match {
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
      ProjectileDef.get(projectile.projectileType).onHitEffects.foreach {
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
        ProjectileDef.get(projectile.projectileType).onHitEffects.foreach {
          case PullToOwner => pullToOwner(projectile, target)

          case Freeze(durationMs) =>
            if (target.tryFreeze(durationMs)) holdByServer(target)

          case Stun(durationMs) =>
            if (target.tryStun(durationMs)) holdByServer(target)

          case TeleportOwnerBehind(distance, freezeDurationMs) =>
            if (target.tryFreeze(freezeDurationMs)) holdByServer(target)
            val owner = registry.get(projectile.ownerId)
            // The shot can outlive its owner, and the dead go nowhere
            if (owner != null && !owner.isDead) {
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
        pDef.onHitEffects.foreach {
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

      ProjectileDef.get(projectile.projectileType).onHitEffects.foreach {
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

  /**
   * A slam that buffs its caster does it on the cast, not on a hit. The Cyborg's Overclock is a
   * 0-damage ring whose on-hit effect is a speed boost, so it only sped him up when an enemy
   * happened to be standing inside it — which, for an ability that is entirely a self-buff, is
   * never when it matters.
   */
  private def applyCastSelfBuff(projectile: Projectile): Unit =
    ProjectileDef.get(projectile.projectileType).onHitEffects.collectFirst { case b: SpeedBoost => b } match {
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

  /** Speed the projectile's owner up — the effect lands on whoever fired it, not on what it hit. */
  private def boostOwner(projectile: Projectile, durationMs: Int): Unit = {
    val owner = registry.get(projectile.ownerId)
    if (owner != null && !owner.isDead) {
      owner.setSpeedBoostUntil(System.currentTimeMillis() + durationMs)
      broadcastBuffered(stateUpdate(owner))
    }
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

  /** Mirror client-side on-hit ability cooldown reduction so server fire-rate validation stays in sync. */
  private def notifyAbilityHitForOwner(projectile: Projectile): Unit = {
    val owner = registry.get(projectile.ownerId)
    if (owner != null) {
      handler.notifyAbilityHit(projectile.ownerId, projectile.projectileType, owner.getCharacterId)
    }
  }
}
