package com.gridgame.server.game

import com.gridgame.common.model.CharacterDef
import com.gridgame.common.model.GroundSlam
import com.gridgame.common.model.Player
import com.gridgame.common.observability.Attrs
import com.gridgame.common.observability.Metrics
import com.gridgame.common.protocol._

/** A player firing: the primary, its burst, or an ability that throws a projectile. Judged against
  * who they are (dead, held or phased fire nothing), the opening's ceasefire and the attack's own
  * clock (PacketValidator) before the server spawns it. */
trait ShotRequests { this: ClientHandler =>
  private[game] def handleProjectileUpdate(packet: ProjectilePacket): Boolean = {
    // Only handle spawn requests from clients
    if (packet.getAction != ProjectileAction.SPAWN) {
      return false // Ignore non-spawn packets from clients
    }

    val playerId = packet.getPlayerId
    val player = registry.get(playerId)

    if (player == null) {
      System.err.println(s"Received projectile spawn from unknown player: $playerId")
      return false
    }

    if (player.isFrozen || player.isDead) {
      return false
    }

    // Phased, a player is untouchable and fires nothing: their client fires nothing while phased,
    // and the server already takes no trap and no barrier from them. A phase's last moments are
    // let through, since the client's ends a trip across the wire before the server's does and
    // its first shot after it can land here in them.
    if (player.getPhasedUntil - System.currentTimeMillis() > PHASED_SHOT_GRACE_MS) {
      Metrics.validationFailed.add(1L, Attrs.VfProjectileFireRate)
      return false
    }

    // Validate projectile velocity: reject NaN or Infinite
    val pdx = packet.getDx
    val pdy = packet.getDy
    if (java.lang.Float.isNaN(pdx) || java.lang.Float.isNaN(pdy) ||
        java.lang.Float.isInfinite(pdx) || java.lang.Float.isInfinite(pdy)) {
      System.err.println(s"ClientHandler: Player ${playerId.toString.substring(0, 8)} projectile velocity NaN/Inf")
      Metrics.validationFailed.add(1L, Attrs.VfProjectileVelocity)
      return false
    }
    // The heading only says which way: a projectile flies at its own def's speed. Taken as a
    // velocity, (1, 1) flew 41% faster than it should, and (0, 0) never moved at all — it never
    // reached the end of its range either, so it sat where it was fired for the rest of the match,
    // hitting whoever walked into it. A slam is thrown down on its caster, whatever it is sent with.
    val (headingX, headingY) =
      if (castsSlam(player, packet.getAttackSlot)) (0f, 0f)
      else {
        val len = Math.sqrt(pdx * pdx + pdy * pdy).toFloat
        if (len < MIN_HEADING) {
          System.err.println(s"ClientHandler: Player ${playerId.toString.substring(0, 8)} projectile has no heading")
          Metrics.validationFailed.add(1L, Attrs.VfProjectileVelocity)
          return false
        }
        (pdx / len, pdy / len)
      }

    // Nobody attacks during a free-for-all's opening ceasefire (MatchOpening) — the primary and
    // its burst as much as an ability. Judged before the fire-rate clock, so a refused attack
    // costs no cooldown.
    if (instance.attacksLocked) {
      Metrics.validationFailed.add(1L, Attrs.VfOpening)
      return false
    }

    if (!validator.validateProjectileSpawn(packet, player)) {
      Metrics.validationFailed.add(1L, Attrs.VfProjectileFireRate)
      return false
    }

    // Firing anything drops a raised barrier. The client drops it first and says so, but that
    // update and this spawn race each other over UDP, so the spawn drops it too.
    if (player.hasBarrier) {
      player.dropBarrier()
      broadcastState(player)
    }

    // Spawn projectile at player's position along the heading from the packet, charged as far as
    // the time the player has had allows
    val projectile = projectileManager.spawnProjectile(
      playerId,
      packet.getX.toInt,
      packet.getY.toInt,
      headingX,
      headingY,
      packet.getColorRGB,
      validator.chargeAllowed(playerId, packet.getAttackSlot, packet.getChargeLevel),
      packet.getProjectileType
    )

    if (projectile == null) return false // Per-player projectile cap reached

    instance.broadcastProjectileSpawn(projectile)

    false // Don't auto-broadcast; we handled it
  }

  /** How long before a phase ends a shot is let through anyway (see handleProjectileUpdate). */
  private val PHASED_SHOT_GRACE_MS = 250L

  /** A heading shorter than this says no direction at all. The client sends unit headings. */
  private val MIN_HEADING = 0.5f

  /** Does the attack in this slot throw its projectile down on the caster as a ground slam? */
  private def castsSlam(player: Player, slot: Int): Boolean = {
    val charDef = CharacterDef.get(player.getCharacterId)
    val ability = slot match {
      case AttackSlot.Q => charDef.qAbility
      case AttackSlot.E => charDef.eAbility
      case _ => null
    }
    ability != null && ability.castBehavior.isInstanceOf[GroundSlam]
  }
}
