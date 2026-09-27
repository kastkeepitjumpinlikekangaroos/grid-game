package com.gridgame.server.game

import com.gridgame.common.model._
import com.gridgame.common.observability.Attrs
import com.gridgame.common.observability.Metrics
import com.gridgame.common.protocol._

import java.util.UUID

/** Traps going off and running out, as the trap tick and players' steps find them (TrapManager): a
  * trap's damage and effects on whoever stepped on it, or its blast, and everyone told. */
trait TrapEffects { this: GameInstance =>
  private def trapPacket(trap: Trap, action: Byte, victimId: UUID = null): TrapPacket =
    new TrapPacket(outbox.nextSeq(), trap.ownerId, Packet.getCurrentTimestamp,
      trap.x, trap.y, trap.id, action, trap.trapType, trap.teamId, 0, victimId)

  /** Tell everyone about a trap: it is on the ground, or it is gone. */
  private[server] def broadcastTrap(trap: Trap, action: Byte): Unit =
    broadcastToInstance(trapPacket(trap, action))

  /** Every client is sent every trap; their own and their allies' are drawn plainly, everyone
    * else's faintly, so a trap is findable if you look for it. */
  private[server] def sendTrapsTo(player: Player): Unit = {
    var sent = false
    trapManager.forEachTrap { trap =>
      outbox.sendRawBuffered(trapPacket(trap, TrapAction.SPAWN).serialize(), true, player)
      sent = true
    }
    if (sent) outbox.flush(player)
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
}
