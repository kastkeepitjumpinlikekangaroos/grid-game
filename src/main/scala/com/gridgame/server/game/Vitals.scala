package com.gridgame.server.game

import com.gridgame.common.Constants
import com.gridgame.common.model._
import com.gridgame.common.observability.Attrs
import com.gridgame.common.observability.Metrics
import com.gridgame.common.protocol._

/** Health over time: burns and poisons ticking (each in its own slot, so both can run at once) and
  * regeneration, which neither lets through. Runs every 200ms. */
trait Vitals { this: GameInstance =>
  private val playerTickAttrs = Attrs.tickPhase("player")

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
        } else if (player.isBurning || player.isPoisoned) {
          // No regen while a burn or a poison lasts, between its ticks as much as on them. A burn
          // used to hold it off only on the ticks it bit, and regen healed most of it back between.
          player.resetRegenAccumulator()
        } else if (player.getHealth < player.getMaxHealth) {
          // --- Health Regen (only when no DoT is running, and not at full health) ---
          // A share of the character's own max health, so a 150 HP bruiser and a 60 HP caster
          // both take 50s to come back from nothing. It used to be 3.0 - (maxHp - 70) * 0.04 a
          // second, which healed the frailest fastest and stopped altogether from 145 HP.
          val maxHp = player.getMaxHealth
          val regenPerSec = maxHp * Constants.REGEN_SHARE_PER_SEC
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
}
