package com.gridgame.server.game

import com.gridgame.common.observability.Attrs
import com.gridgame.common.observability.Metrics
import com.gridgame.common.protocol._

/**
 * R: a player resetting their ability cooldowns (design/characters.md, *R resets the cooldowns*).
 * Their client has both abilities ready from the press; the server takes the reset if the player
 * could cast anything now and the reset's own cooldown has run, a minute since their last (or since
 * the match began, for their first, but at once in practice: GameInstance.firstResetFrom), and
 * clears every clock it holds their abilities to: the shots' and the traps' (PacketValidator), and a
 * phase's and a barrier's, which are the player's own. A refusal is sent back, with how long the
 * reset's cooldown has left if that was why, so the client puts its cooldowns back as the server has
 * them.
 */
trait CooldownResetRequests { this: ClientHandler =>
  private[game] def handleCooldownReset(packet: CooldownResetPacket): Boolean = {
    if (packet.getAction != CooldownResetAction.REQUEST) return false
    val playerId = packet.getPlayerId
    val player = registry.get(playerId)
    if (player == null) return false

    def refuse(why: String, msLeft: Long): Boolean = {
      System.err.println(s"ClientHandler: Cooldown reset refused for ${playerId.toString.substring(0, 8)}: $why")
      Metrics.validationFailed.add(1L, Attrs.VfCooldownReset)
      journal.refused(player, s"R refused (cooldown_reset): $why${if (msLeft > 0) s", ${msLeft}ms of its minute left" else ""}")
      val reject = new CooldownResetPacket(outbox.nextSeq(), playerId, CooldownResetAction.REJECTED, msLeft.toInt)
      try outbox.sendRaw(reject.serialize(), true, player) catch { case _: Exception => }
      false
    }

    // Whoever couldn't cast now can't reset either: the dead, the held, the phased (but in a phase's
    // last moments, which the client's ends a trip across the wire before ours, as for a shot), and
    // everyone in a free-for-all's opening ceasefire
    if (player.isDead || player.isFrozen) return refuse("dead or held", 0L)
    if (player.getPhasedUntil - System.currentTimeMillis() > PHASED_SHOT_GRACE_MS) return refuse("phased", 0L)
    if (instance.attacksLocked) return refuse("the opening ceasefire", 0L)

    if (!validator.takeCooldownReset(playerId, instance.firstResetFrom)) {
      return refuse("too soon", validator.cooldownResetLeftMs(playerId, instance.firstResetFrom))
    }
    // A phase's cooldown runs from its end less its length, and a barrier's from its raise, as a
    // respawn has them (Lives.respawn). Under the player's lock, as an update's phase and barrier
    // are taken (StepRequests). A barrier already up stays up for the rest of its time.
    player.synchronized {
      player.setPhasedUntil(0L)
      player.setBarrierRaisedAt(0L)
    }
    false
  }
}
