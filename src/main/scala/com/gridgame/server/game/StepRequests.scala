package com.gridgame.server.game

import com.gridgame.common.model.BarrierCast
import com.gridgame.common.model.CharacterDef
import com.gridgame.common.model.DashBuff
import com.gridgame.common.model.Direction
import com.gridgame.common.model.PhaseShiftBuff
import com.gridgame.common.model.Player
import com.gridgame.common.observability.Attrs
import com.gridgame.common.observability.Metrics
import com.gridgame.common.protocol._
import java.net.InetSocketAddress
import java.util.UUID

/**
 * A player's updates: where they are (a step, a blink, a dash), and with them whether they are
 * phased and whether their barrier is up and which way it faces. A position is taken only from an
 * update sent after the last server move the client had seen and after the last position applied,
 * and only if the character could have got there (PacketValidator); refusals that keep coming get
 * the client told where the server has it.
 */
trait StepRequests { this: ClientHandler =>
  private[game] def handlePlayerUpdate(packet: PlayerUpdatePacket, udpSender: InetSocketAddress): Boolean = {
    val playerId = packet.getPlayerId
    val player = registry.get(playerId)
    // Reject updates from unknown players — they must join via PLAYER_JOIN first
    if (player == null) return false

    val world = instance.world
    if (world == null) {
      // World should be loaded for active game instances — reject if missing
      Metrics.validationFailed.add(1L, Attrs.VfWorldMissing)
      return false
    }

    // Check and apply as one step under the player's lock. A star moves the player on the TCP
    // thread while this runs on the UDP one, and a move checked against the position from before
    // the star must not be written after it.
    var refused = false
    // Where an accepted step started, so the traps it passed over can be sprung (-1: it moved
    // nobody, so there is nothing to walk)
    var steppedFromX = -1
    var steppedFromY = -1
    val accepted = player.synchronized {
      if (player.isDead) {
        // A client goes on sending steps until it hears it has died. Taken, the body walked on
        // across everyone else's screens, picking up whatever it passed — which the respawn then
        // threw away, gone from the ground for everyone. The respawn is a server move, so nothing
        // sent from this life is taken after it either.
        false
      } else if (packet.getServerMoves != player.getServerMoves) {
        // Sent before the client knew the server had moved it (a pull, a knockback, a respawn):
        // it would only drag the player back to where they were
        false
      } else if (validator.isStaleMovement(playerId, packet.getSequenceNumber)) {
        // Sent before a position already applied: it would only put the player back
        false
      } else {
        // A phase lets the player through walls, so it takes effect before this very update is
        // checked: the first one sent with the flag can already be inside one
        if ((packet.getEffectFlags & 0x08) != 0) activatePhase(player)
        updateBarrier(player, packet)
        if (!validator.validateMovement(packet, player, world, instance.attacksLocked)) {
          Metrics.validationFailed.add(1L, Attrs.VfMovementSpeed)
          refused = true
          false
        } else {
          validator.movementApplied(playerId, packet.getSequenceNumber)
          val oldPos = player.getPosition
          val newPos = packet.getPosition
          val dx = newPos.getX - oldPos.getX
          val dy = newPos.getY - oldPos.getY
          if (dx != 0 || dy != 0) {
            player.setDirection(Direction.fromMovement(dx, dy))
          }
          // Don't allow movement if player is frozen or rooted
          if (!player.isFrozen && !player.isRooted) {
            if (dx != 0 || dy != 0) {
              steppedFromX = oldPos.getX
              steppedFromY = oldPos.getY
            }
            player.setPosition(newPos)
          }
          player.setColorRGB(packet.getColorRGB)
          // Kept so the server's own updates about this player don't show their charge dropping
          player.setChargeLevel(packet.getChargeLevel)
          // Character ID is set on join only — ignore mid-game character changes
          if (udpSender != null) {
            player.setUdpAddress(udpSender)
          }
          true
        }
      }
    }
    if (refused) noteRefused(player) else if (accepted) refusedSince.remove(playerId)
    if (!accepted) return false

    // Check for item pickup using server-authoritative position
    val pickupPos = player.getPosition
    itemManager.checkPickup(playerId, pickupPos.getX, pickupPos.getY).foreach { event =>
      instance.broadcastItemPickup(event.item, event.playerId)
    }

    // And for traps, over every cell the step crossed. An accepted update can carry a player
    // more than one cell — a datagram was lost, or they dashed — and a trap hopped clean over
    // has still been stepped on. The projectile tick catches whoever is standing on one; this
    // is the only thing that catches one they went past.
    if (steppedFromX >= 0) {
      instance.applyTrapEvents(instance.trapManager.triggerAlong(player, steppedFromX, steppedFromY,
        pickupPos.getX, pickupPos.getY, System.currentTimeMillis()))
    }

    true
  }

  /**
   * The client says the player is phased (its phase ability, or a dash). Honour it if that
   * ability is off cooldown: 80% of it since the last phase started, the tolerance every other
   * attack gets. This used to count from when the last phase ended, at 90%, which a phase recast
   * as soon as it was ready never passed (Wraith: 5s phase, 12s cooldown, allowed after 15.8s),
   * so every second phase showed on the client and wasn't honoured: the player took hits, and
   * walking through walls was refused.
   */
  private def activatePhase(player: Player): Unit = {
    if (player.isPhased) return
    // A phase and a dash are abilities like any other: held by a free-for-all's opening ceasefire
    if (instance.attacksLocked) return
    val charDef = CharacterDef.get(player.getCharacterId)
    val grants = Seq(charDef.qAbility, charDef.eAbility).flatMap { ability =>
      ability.castBehavior match {
        case PhaseShiftBuff(durationMs) => Some((durationMs, ability.cooldownMs))
        case DashBuff(_, durationMs, _) => Some((durationMs, ability.cooldownMs))
        case _ => None
      }
    }
    grants.headOption.foreach { case (durationMs, cooldownMs) =>
      val now = System.currentTimeMillis()
      val lastEnd = player.getPhasedUntil
      val lastStart = lastEnd - durationMs
      if (lastEnd == 0L || now - lastStart >= (cooldownMs * 0.8).toLong) {
        player.setPhasedUntil(now + durationMs)
      }
    }
  }

  /**
   * The client says whether the player's barrier is up (effect flags 2, bit 2) and which way it
   * faces (the aim angle). A raise is honoured if the character has a barrier and it is off
   * cooldown: 80% of it since the last raise, the tolerance every other attack gets, counted from
   * the raise because a barrier can drop early. While it is up every update turns it, and one
   * without the bit drops it.
   *
   * Only the newest word counts ([[PacketValidator.takeBarrierWord]]). The movement fence that
   * keeps stale positions out moves only on an accepted position, so without this an update sent
   * just before a raise, arriving after the raise's own refused update, dropped the barrier on the
   * server — and the cooldown kept it from going up again — while its holder saw it up.
   */
  private def updateBarrier(player: Player, packet: PlayerUpdatePacket): Unit = {
    if (!validator.takeBarrierWord(player.getId, packet.getSequenceNumber)) return
    val raised = (packet.getEffectFlags2 & 0x04) != 0
    if (player.hasBarrier) {
      if (raised) player.setBarrierAngle(packet.aimAngleRadians.toFloat)
      else player.dropBarrier()
    } else if (raised && !player.isDead && !player.isFrozen && !player.isPhased && !instance.attacksLocked) {
      val charDef = CharacterDef.get(player.getCharacterId)
      Seq(charDef.qAbility, charDef.eAbility).collectFirst {
        case a if a.castBehavior.isInstanceOf[BarrierCast] => (a.castBehavior.asInstanceOf[BarrierCast].durationMs, a.cooldownMs)
      }.foreach { case (durationMs, cooldownMs) =>
        val now = System.currentTimeMillis()
        val last = player.getBarrierRaisedAt
        if (last == 0L || now - last >= (cooldownMs * 0.8).toLong) {
          player.raiseBarrier(now, durationMs, packet.aimAngleRadians.toFloat)
        }
      }
    }
  }

  // When the current run of refused positions began, per player; cleared by an accepted one
  private[game] val refusedSince = new java.util.concurrent.ConcurrentHashMap[UUID, java.lang.Long]()

  // When each player was last corrected, so a client that keeps sending refused positions is
  // told where it is at most every CORRECTION_INTERVAL_MS
  private[game] val lastCorrectionAt = new java.util.concurrent.ConcurrentHashMap[UUID, java.lang.Long]()
  private val CORRECTION_AFTER_MS = 250L
  private val CORRECTION_INTERVAL_MS = 500L

  /**
   * One of the player's positions was refused. A lone refusal is usually a race the server is
   * about to catch up with (a step that overtook the star it was taken from), so nothing is said.
   * Refusals that keep coming mean the client and server disagree about where the player is, and
   * every step it takes from where it thinks it is will be refused too: the client only takes a
   * position from the server when it is a server move, so tell it where it is with one.
   */
  private def noteRefused(player: Player): Unit = {
    val now = System.currentTimeMillis()
    val since = refusedSince.putIfAbsent(player.getId, now)
    if (since != null && now - since.longValue() >= CORRECTION_AFTER_MS) correctPosition(player)
  }

  /** Send the player the position the server has for them, as a server move: their client takes
    * it, and the steps it sent from where it wrongly thought it was are dropped. */
  private def correctPosition(player: Player): Unit = {
    val now = System.currentTimeMillis()
    val last = lastCorrectionAt.get(player.getId)
    if (last != null && now - last.longValue() < CORRECTION_INTERVAL_MS) return
    lastCorrectionAt.put(player.getId, now)
    instance.moveByServer(player, player.getPosition)
  }
}
