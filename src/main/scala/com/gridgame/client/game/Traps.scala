package com.gridgame.client.game

import com.gridgame.client.audio.AudioManager
import com.gridgame.common.model._
import com.gridgame.common.protocol._
import GameClient._

import java.util.concurrent.ConcurrentHashMap

/**
 * Traps (TrapCast; see Traps in the gameplay docs): every trap on the ground, ours and everyone
 * else's, as the server tells us of them, and each going off. Placing one is an ability
 * (Attacks.shootAbility); a placement the server refuses gives the ability back.
 */
trait Traps { this: GameClient =>
  // Traps on the ground, ours and everyone else's: the server sends every client every trap, and
  // the renderer draws an enemy's faintly rather than not at all
  private val traps: ConcurrentHashMap[Int, Trap] = new ConcurrentHashMap()

  // A trap going off: trapId -> (timestamp, worldX*1000, worldY*1000, trapType). A mine's blast
  // goes through `blasts` instead, keyed past every projectile's (TRAP_BLAST_KEYS).
  private val trapEffects: ConcurrentHashMap[Int, Array[Long]] = new ConcurrentHashMap()

  /**
   * Traps as the server tells us about them. Their timers are taken from when we heard, not from
   * the server's clock: the arming ring only says "not yet", and a trap whose lifetime we had
   * wrong would fade off the ground while it was still there (the server's REMOVE is what takes
   * it away).
   */
  private[game] def handleTrapUpdate(packet: TrapPacket): Unit = {
    val now = System.currentTimeMillis()
    val trapId = packet.getTrapId
    packet.getAction match {
      case TrapAction.SPAWN =>
        val tDef = TrapDef.get(packet.getTrapType)
        if (tDef != null) {
          traps.put(trapId, new Trap(trapId, packet.getPlayerId, packet.getTeamId, packet.getX,
            packet.getY, packet.getTrapType, now, now + tDef.armDelayMs, now + tDef.lifetimeMs))
          // Ours was heard when we threw it; this is someone else setting one down over there
          if (!packet.getPlayerId.equals(localPlayerId)) {
            AudioManager.playTrapPlace(distanceFromLocal(packet.getX.toFloat, packet.getY.toFloat),
              panFromLocal(packet.getX.toFloat, packet.getY.toFloat))
          }
        }

      case TrapAction.TRIGGER =>
        val sprung = traps.remove(trapId)
        val trapType = if (sprung != null) sprung.trapType else packet.getTrapType
        val tDef = TrapDef.get(trapType)
        if (tDef != null) {
          val wx = packet.getX.toFloat
          val wy = packet.getY.toFloat
          val dist = distanceFromLocal(wx, wy)
          val pan = panFromLocal(wx, wy)
          tDef.explosion match {
            case Some(boom) =>
              blast(TRAP_BLAST_KEYS | (trapId & 0xFFFFFFFFL), wx, wy, tDef.colorRGB, boom.blastRadius,
                trapType, characterIdOf(packet.getPlayerId), trap = true)
              AudioManager.playExplosion(dist, pan)
            case None =>
              trapEffects.put(trapId, Array(now, (wx * 1000).toLong, (wy * 1000).toLong, trapType.toLong))
              AudioManager.playTrapSprung(tDef.kind, dist, pan)
          }
        }

      case TrapAction.REMOVE =>
        traps.remove(trapId)

      case TrapAction.REJECTED =>
        // The server wouldn't take it, so the ability was spent on nothing: have it back — but
        // ready in a moment rather than this instant. Refused placements have a reason that
        // often hasn't gone away (a trap is already on that cell), and a cooldown handed back
        // whole turns a held key into a placement request every frame.
        if (packet.getPlayerId.equals(localPlayerId)) {
          val charDef = getSelectedCharacterDef
          packet.getAttackSlot match {
            case AttackSlot.Q => refundAbility(lastQAbilityTime, charDef.qAbility.cooldownMs)
            case AttackSlot.E => refundAbility(lastEAbilityTime, charDef.eAbility.cooldownMs)
            case _ =>
          }
          println(s"GameClient: Trap placement refused at (${packet.getX}, ${packet.getY})")
        }

      case _ =>
      // A PLACE echoed back, or an action we don't know
    }
  }

  /** No match: no traps, and none going off. */
  private[game] def forgetTraps(): Unit = {
    traps.clear()
    trapEffects.clear()
  }

  def getTraps: ConcurrentHashMap[Int, Trap] = traps

  /** Traps going off, for the renderer: trapId -> (when, x*1000, y*1000, trap type). */
  def getTrapEffects: ConcurrentHashMap[Int, Array[Long]] = trapEffects

  /** Ours or an ally's — drawn plainly. Everyone else's is drawn faintly, findable if looked for. */
  def isFriendlyTrap(trap: Trap): Boolean =
    trap.ownerId.equals(localPlayerId) || (localTeamId != 0 && trap.teamId == localTeamId)

  /** How many of our own traps are on the ground, for the ability slot's count. */
  def myTrapCount: Int = {
    if (traps.isEmpty) return 0
    var n = 0
    val iter = traps.values().iterator()
    while (iter.hasNext) if (iter.next().ownerId.equals(localPlayerId)) n += 1
    n
  }
}
