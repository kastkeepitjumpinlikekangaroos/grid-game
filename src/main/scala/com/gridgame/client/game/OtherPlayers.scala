package com.gridgame.client.game

import com.gridgame.common.model._
import com.gridgame.common.protocol._

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Everyone else in the match, as the server tells us of them: joining, their updates (where they
 * are, their health and the effects on them, their barrier), and leaving. `players` never holds
 * us (LocalPlayer). Where each is drawn between updates is the renderer's (RemoteMotion).
 */
trait OtherPlayers { this: GameClient =>
  private[game] val players: ConcurrentHashMap[UUID, Player] = new ConcurrentHashMap()

  // Players who left mid-match, kept so the final scoreboard can still name them.
  private val departedPlayers: ConcurrentHashMap[UUID, Player] = new ConcurrentHashMap()

  /** A player of the current match, including one who has since left it. */
  def findPlayer(playerId: UUID): Player = {
    val p = players.get(playerId)
    if (p != null) p else departedPlayers.get(playerId)
  }

  def playerLeftMatch(playerId: UUID): Boolean =
    !players.containsKey(playerId) && departedPlayers.containsKey(playerId)

  /** The character a shooter is playing, or -1 if we have not seen them yet.
    * `players` does not hold the local player, so that case is answered from
    * the local selection. */
  private[game] def characterIdOf(shooterId: UUID): Byte = {
    if (shooterId == null) return -1
    if (shooterId.equals(localPlayerId)) return selectedCharacterId
    val p = players.get(shooterId)
    if (p != null) p.getCharacterId else -1
  }

  /** A player's team as this client knows it: 0 is nobody's, and nobody is a teammate of theirs. */
  private[game] def teamOf(id: UUID): Byte =
    if (id.equals(localPlayerId)) localTeamId
    else { val p = players.get(id); if (p != null) p.getTeamId else 0 }

  private[game] def handlePlayerJoin(packet: PlayerJoinPacket): Unit = {
    val player = new Player(
      packet.getPlayerId,
      packet.getPlayerName,
      packet.getPosition,
      packet.getColorRGB,
      packet.getHealth
    )
    player.setCharacterId(packet.getCharacterId)
    player.setTeamId(packet.getTeamId)
    players.put(player.getId, player)
    // In the match, whatever we last heard: their updates count again
    departedPlayers.remove(player.getId)

    println(s"GameClient: Player joined - ${player.getId.toString.substring(0, 8)} ('${player.getName}') at ${player.getPosition} with health ${player.getHealth} team=${packet.getTeamId}")
  }

  private[game] def handlePlayerUpdate(packet: PlayerUpdatePacket): Unit = {
    val playerId = packet.getPlayerId
    var player = players.get(playerId)

    if (player != null) {
      val wasAlive = !player.isDead
      val oldPos = player.getPosition
      val newPos = packet.getPosition
      val dx = newPos.getX - oldPos.getX
      val dy = newPos.getY - oldPos.getY

      // Detect teleport: large position jump (Manhattan distance > 3).
      // Skip if the player was dead — a respawn also resets position to an
      // unrelated point on the map and would otherwise be misread as a cast.
      if (wasAlive && Math.abs(dx) + Math.abs(dy) > 3) {
        teleportAnimations.put(playerId, Array(
          System.currentTimeMillis(),
          oldPos.getX.toLong, oldPos.getY.toLong,
          newPos.getX.toLong, newPos.getY.toLong,
          packet.getColorRGB.toLong
        ))
      }

      if (dx != 0 || dy != 0) {
        player.setDirection(Direction.fromMovement(dx, dy))
      }
      player.setPosition(newPos)
      player.setColorRGB(packet.getColorRGB)
      player.setHealth(packet.getHealth)
      player.setChargeLevel(packet.getChargeLevel)
      if (packet.getCharacterId != 0 || player.getCharacterId == 0) {
        player.setCharacterId(packet.getCharacterId)
      }
      if (packet.getTeamId != 0) {
        player.setTeamId(packet.getTeamId)
      }

      // Apply effect flags from server — only set timer on OFF→ON transition
      val flags = packet.getEffectFlags
      val now = System.currentTimeMillis()
      if ((flags & 0x01) != 0) {
        if (now >= player.getShieldUntil) player.setShieldUntil(now + 1000)
      } else {
        player.setShieldUntil(0)
      }
      if ((flags & 0x02) != 0) {
        if (now >= player.getGemBoostUntil) player.setGemBoostUntil(now + 1000)
      } else {
        player.setGemBoostUntil(0)
      }
      if ((flags & 0x04) != 0) {
        if (now >= player.getFrozenUntil) player.setFrozenUntil(now + 5000)
      } else {
        player.setFrozenUntil(0)
      }
      if ((flags & 0x08) != 0) {
        if (now >= player.getPhasedUntil) player.setPhasedUntil(now + 1000)
      } else {
        player.setPhasedUntil(0)
      }
      if ((flags & 0x10) != 0) {
        if (!player.isBurning) player.applyBurn(0, 1000, 1000, null) // Visual-only on client; server handles actual DoT
      } else {
        player.clearBurn()
      }
      if ((flags & 0x20) != 0) {
        if (now >= player.getSpeedBoostUntil) player.setSpeedBoostUntil(now + 1000)
      } else {
        player.setSpeedBoostUntil(0)
      }
      if ((flags & 0x40) != 0) {
        if (now >= player.getRootedUntil) player.setRootedUntil(now + 3000)
      } else {
        player.setRootedUntil(0)
      }
      if ((flags & 0x80) != 0) {
        if (now >= player.getSlowedUntil) player.setSlowedUntil(now + 3000)
        if (packet.getSlowPercent > 0) player.setSlowMultiplier(packet.getSlowPercent / 100f)
      } else {
        player.clearSlow()
      }
      val flags2 = packet.getEffectFlags2
      if ((flags2 & 0x01) != 0) {
        if (now >= player.getStunnedUntil) player.setStunnedUntil(now + 5000)
      } else {
        player.setStunnedUntil(0)
      }
      // Visual only: the server does the damage. Nothing ticks it here, so it shows until an
      // update without the flag, which the poison's last tick always sends.
      if ((flags2 & 0x02) != 0) {
        if (!player.isPoisoned) player.applyPoison(0, 1000, 1000, null)
      } else {
        player.clearPoison()
      }
      heardBarrierOf(player, packet, now)

      // Record death animation when player newly dies
      if (wasAlive && player.isDead) {
        deathAnimations.put(playerId, Array(
          System.currentTimeMillis(),
          newPos.getX.toLong,
          newPos.getY.toLong,
          packet.getColorRGB.toLong,
          player.getCharacterId.toLong
        ))
      }
    } else {
      // Someone who has left the match. Updates come over UDP and the leave over TCP, so one sent
      // before the leave can arrive after it; taken, it brought them back as a player called
      // "Player" standing where they left, for good, since no second leave would come for them.
      if (departedPlayers.containsKey(playerId)) return
      player = new Player(playerId, "Player", packet.getPosition, packet.getColorRGB, packet.getHealth)
      player.setCharacterId(packet.getCharacterId)
      player.setChargeLevel(packet.getChargeLevel)
      val flags = packet.getEffectFlags
      if ((flags & 0x01) != 0) player.setShieldUntil(System.currentTimeMillis() + 1000)
      if ((flags & 0x02) != 0) player.setGemBoostUntil(System.currentTimeMillis() + 1000)
      if ((flags & 0x04) != 0) player.setFrozenUntil(System.currentTimeMillis() + 5000)
      if ((flags & 0x08) != 0) player.setPhasedUntil(System.currentTimeMillis() + 1000)
      if ((flags & 0x10) != 0) player.applyBurn(0, 1000, 1000, null)
      if ((flags & 0x20) != 0) player.setSpeedBoostUntil(System.currentTimeMillis() + 1000)
      if ((flags & 0x40) != 0) player.setRootedUntil(System.currentTimeMillis() + 3000)
      if ((flags & 0x80) != 0) player.setSlowedUntil(System.currentTimeMillis() + 3000)
      if (packet.getSlowPercent > 0) player.setSlowMultiplier(packet.getSlowPercent / 100f)
      val flags2 = packet.getEffectFlags2
      if ((flags2 & 0x01) != 0) player.setStunnedUntil(System.currentTimeMillis() + 5000)
      if ((flags2 & 0x02) != 0) player.applyPoison(0, 1000, 1000, null)
      if ((flags2 & 0x04) != 0) {
        barrierWordIsNew(playerId, packet.getSequenceNumber)
        player.raiseBarrier(System.currentTimeMillis(), remoteBarrierMs(packet).toInt,
          packet.aimAngleRadians.toFloat)
      }
      players.put(playerId, player)
    }
  }

  private[game] def handlePlayerLeave(packet: PlayerLeavePacket): Unit = {
    val playerId = packet.getPlayerId
    val player = players.remove(playerId)
    forgetBarrierOf(playerId)
    forgetHitOn(playerId)

    if (player != null) {
      departedPlayers.put(playerId, player)
      println(s"GameClient: Player left - ${playerId.toString.substring(0, 8)} ('${player.getName}')")
    }
  }

  /** No match: nobody in it, and nobody who has left it. */
  private[game] def forgetPlayers(): Unit = {
    players.clear()
    departedPlayers.clear()
  }

  def getPlayers: ConcurrentHashMap[UUID, Player] = players
}
