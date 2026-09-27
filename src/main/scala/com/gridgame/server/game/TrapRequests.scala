package com.gridgame.server.game

import com.gridgame.common.model.CharacterDef
import com.gridgame.common.model.TrapCast
import com.gridgame.common.model.TrapPlacement
import com.gridgame.common.observability.Attrs
import com.gridgame.common.observability.Metrics
import com.gridgame.common.protocol._
import java.util.UUID

/** A player throwing a trap onto the ground (TrapCast), and their traps going with them. */
trait TrapRequests { this: ClientHandler =>
  /**
   * A client throwing one of its traps onto the ground. Everything the placement rests on is
   * checked here — that the player can cast at all, that the attack they name really throws this
   * trap and is off cooldown, and that the cell is one [[TrapPlacement]] would have reached from
   * where the server has them — because a trap the client shows and the server refuses is a long
   * cooldown spent on nothing. A refusal is sent back so the client gives that cooldown up again.
   *
   * The order matters: everything that can be judged without touching the clock is judged first,
   * so only a genuine race (someone else's trap landing on the cell in between) can spend a cast
   * and still be refused.
   */
  private[game] def handleTrapUpdate(packet: TrapPacket): Boolean = {
    if (packet.getAction != TrapAction.PLACE) return false
    val playerId = packet.getPlayerId
    val player = registry.get(playerId)
    if (player == null) return false
    val world = instance.world
    if (world == null) return false

    def refuse(why: String): Boolean = {
      System.err.println(s"ClientHandler: Trap refused for ${playerId.toString.substring(0, 8)}: $why")
      Metrics.validationFailed.add(1L, Attrs.VfTrapPlacement)
      val reject = new TrapPacket(outbox.nextSeq(), playerId, packet.getX, packet.getY,
        0, TrapAction.REJECTED, packet.getTrapType, player.getTeamId, packet.getAttackSlot, null)
      try outbox.sendRaw(reject.serialize(), true, player) catch { case _: Exception => }
      false
    }

    // The dead, the held and the phased cast nothing
    if (player.isDead || player.isFrozen || player.isPhased) return refuse("dead, held or phased")
    // Nor does anyone during a free-for-all's opening ceasefire (MatchOpening)
    if (instance.attacksLocked) return refuse("the opening ceasefire")

    val charDef = CharacterDef.get(player.getCharacterId)
    val slot = packet.getAttackSlot
    val ability = if (charDef == null) null else slot match {
      case AttackSlot.Q => charDef.qAbility
      case AttackSlot.E => charDef.eAbility
      case _ => null
    }
    val cast = if (ability == null) null else ability.castBehavior match {
      case t: TrapCast => t
      case _ => null
    }
    if (cast == null || cast.trapType != packet.getTrapType) {
      return refuse(s"attack $slot throws no trap of type ${packet.getTrapType}")
    }

    val pos = player.getPosition
    if (!TrapPlacement.isValidTarget(world, pos.getX, pos.getY, packet.getX, packet.getY, cast.maxRange)) {
      return refuse(s"(${packet.getX},${packet.getY}) is out of reach of (${pos.getX},${pos.getY})")
    }
    if (instance.trapManager.trapAt(packet.getX, packet.getY) != null) return refuse("a trap is already there")
    if (!validator.validateCast(playerId, slot, ability.cooldownMs)) return refuse("cast too soon")

    val placed = instance.trapManager.place(playerId, player.getTeamId, packet.getX, packet.getY,
      cast.trapType, System.currentTimeMillis())
    if (placed == null) return refuse("a trap landed there first")

    // Casting anything drops a raised barrier, as firing does
    if (player.hasBarrier) {
      player.dropBarrier()
      broadcastState(player)
    }

    // A fourth trap takes their oldest away
    placed.removed.foreach(instance.broadcastTrap(_, TrapAction.REMOVE))
    instance.broadcastTrap(placed.trap, TrapAction.SPAWN)
    Metrics.trapsPlaced.add(1L, Attrs.trapType(cast.trapType))
    false
  }

  /** Their traps go with them when they leave the match. */
  private[game] def clearTrapsOf(playerId: UUID): Unit = {
    instance.trapManager.removeAllOf(playerId).foreach(instance.broadcastTrap(_, TrapAction.REMOVE))
  }
}
