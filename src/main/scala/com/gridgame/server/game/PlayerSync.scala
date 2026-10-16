package com.gridgame.server.game

import com.gridgame.common.model._
import com.gridgame.common.protocol._

/**
 * The server's word on a player: what everyone is told about them (stateUpdate — where the server
 * has them, and every status they are under), and the server moving them itself (a pull, a push,
 * a respawn, a hold). A server move is counted and sent to the player over TCP, so their client
 * takes it and drops what it sent before it heard (see Position authority in server/CLAUDE.md).
 */
trait PlayerSync { this: GameInstance =>
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
   * How long this player's raised barrier has left, in ms. Every client draws it on that timer,
   * as its holder's own client does, rather than on a lease renewed by whichever of the holder's
   * updates reach them: a gap in those — a hitch in the holder's render loop, which is what
   * streams them, a burst of lost datagrams, a run the server refused — used to take the barrier
   * off every other screen for the rest of its life while it was still up and still stopping shots.
   */
  private def barrierMsLeft(p: Player): Int =
    if (!p.hasBarrier) 0
    else Math.max(1L, Math.min(65535L, p.getBarrierUntil - System.currentTimeMillis())).toInt

  /**
   * How long this player's speed boost has left, in ms, so their own client walks it for as long
   * as it lasts. The flag alone gave a client a second of it per update that carried one, and a
   * player at full health hears no update between a cast and the next hit: the Cyborg's 4s
   * Overclock walked for one.
   */
  private def speedBoostMsLeft(p: Player): Int =
    if (!p.hasSpeedBoost) 0
    else Math.max(1L, Math.min(65535L, p.getSpeedBoostUntil - System.currentTimeMillis())).toInt

  /**
   * How long an effect running until `until` has left, in ms, for bytes [61-63] of an update (0 if
   * it is over). Every client holds, roots and slows a player for as long as this says: with the
   * flags alone, a client held its own player until the next update without them, which nothing
   * sends when an effect ends.
   */
  private def msLeft(until: Long, now: Long): Int =
    if (until <= now) 0 else Math.min(until - now, PlayerUpdatePacket.MaxEffectMs.toLong).toInt

  /**
   * A player as the server has them, for everyone to be told: position, health, every status
   * flag and how long each hold, root and slow has left, character, team, how many times the
   * server has moved them and how many of those moves were knockbacks. Every update the server
   * sends about a player goes through here; each used to be built by hand, and several carried a
   * few of the flags, so a burning, rooted or slowed player's effects blinked off on other screens
   * whenever one of those went out.
   *
   * The count of server moves is read before the position. A server move writes the position and
   * then counts it (placeByServer), so read in that order the count is never newer than the
   * position beside it. Read the other way, an update built while another thread moved the player
   * could carry the old position with the new count, which their client took as the move, and then
   * ignored the update that really carried it, its count no longer new: it stood where the server
   * no longer had it, its steps refused, until a correction.
   */
  private[server] def stateUpdate(p: Player): PlayerUpdatePacket = {
    val seq = outbox.nextSeq()
    val serverMoves = p.getServerMoves
    val position = p.getPosition
    val now = System.currentTimeMillis()
    new PlayerUpdatePacket(
      seq,
      p.getId,
      Packet.getCurrentTimestamp,
      position,
      p.getColorRGB,
      p.getHealth,
      p.getChargeLevel,
      playerFlags(p),
      p.getCharacterId,
      p.getTeamId,
      serverMoves,
      playerFlags2(p),
      // Which way a raised barrier faces, so every client turns it as its holder does
      if (p.hasBarrier) PlayerUpdatePacket.encodeAimAngle(p.getBarrierAngle) else 0,
      slowPercent(p),
      barrierMsLeft(p),
      p.getKnockbacks,
      speedBoostMsLeft(p),
      msLeft(p.getFrozenUntil, now),
      msLeft(p.getRootedUntil, now),
      msLeft(p.getSlowedUntil, now)
    )
  }

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

  private[game] def placeByServer(p: Player, pos: Position): Unit = p.synchronized {
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
    try outbox.sendRaw(stateUpdate(p).serialize(), true, p)
    catch { case _: Exception => }
  }

  /** Knock a player `cells` straight back from (fromX, fromY), stopping at the first wall rather
    * than coming out on its far side. Counted as a knockback as well as a server move, so every
    * client draws the move as a shove (Player.getKnockbacks). */
  private[game] def pushFrom(p: Player, fromX: Float, fromY: Float, cells: Float): Unit = {
    val pos = p.getPosition
    val pdx = pos.getX - fromX
    val pdy = pos.getY - fromY
    val dist = Math.sqrt(pdx * pdx + pdy * pdy)
    if (dist <= 0.01) return
    val dest = Teleport.slide(world, pos.getX, pos.getY, pdx / dist, pdy / dist, cells.toInt)
    if (dest != pos) {
      p.recordKnockback()
      moveByServer(p, dest)
    }
  }

  /** Drag a player onto the cell the projectile's owner is standing on. Counted as a knockback as
    * well as a server move, so every client slides them in along the pull (KnockbackSlide) rather
    * than jumping them there with a teleport's flash, as a pull used to be drawn. */
  private[game] def pullToOwner(projectile: Projectile, p: Player): Unit = {
    val owner = registry.get(projectile.ownerId)
    if (owner == null) return
    val ownerPos = owner.getPosition
    if (world.isWalkable(ownerPos.getX, ownerPos.getY) && ownerPos != p.getPosition) {
      p.recordKnockback()
      moveByServer(p, ownerPos)
    }
  }

  /** Pull a player up to `strength` cells toward (vx, vy), if they are within `radius` of it,
    * stopping at walls. Returns whether they moved. */
  private[game] def pullToward(p: Player, vx: Float, vy: Float, radius: Float, strength: Float): Boolean = {
    val pos = p.getPosition
    val dist = Projectile.distanceToPlayer(vx, vy, p)
    if (dist <= 0.1f || dist > radius) return false
    val dest = Teleport.slide(world, pos.getX, pos.getY, (vx - pos.getX) / dist, (vy - pos.getY) / dist,
      Math.min(strength, dist).toInt)
    if (dest == pos) return false
    moveByServer(p, dest)
    true
  }
}
