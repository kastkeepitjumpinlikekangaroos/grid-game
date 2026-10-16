package com.gridgame.client.game

import com.gridgame.common.model._
import com.gridgame.common.protocol._

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Everyone else in the match, as the server tells us of them: joining, their updates (where they
 * are, their health and the effects on them, their barrier), dying and coming back, and leaving.
 * `players` never holds us (LocalPlayer). Where each is drawn between updates is the renderer's
 * (RemoteMotion).
 *
 * Only the newest word on a player counts ([[newWordOn]]), us included: their updates are
 * datagrams, which overtake each other, and their death and their respawn come by TCP, which an
 * update sent before either can arrive after.
 */
trait OtherPlayers { this: GameClient =>
  private[game] val players: ConcurrentHashMap[UUID, Player] = new ConcurrentHashMap()

  // Players who left mid-match, kept so the final scoreboard can still name them.
  private val departedPlayers: ConcurrentHashMap[UUID, Player] = new ConcurrentHashMap()

  // The sequence number of the newest word we have taken on each player, us included: the server's
  // last update about them we applied, or their death or respawn if that came after it. The server
  // numbers everything it sends from one count, so a packet numbered before it was built before it.
  private val newestWord: ConcurrentHashMap[UUID, Integer] = new ConcurrentHashMap()

  /**
   * Is the server's packet `seq` about `playerId` newer than everything we have taken about them?
   * Records it if so. Updates were applied in the order they arrived, and an older one put a player
   * back where they had been, with the health and the effects they had then: a datagram sent just
   * before a kill and delivered after it stood the dead player up again on our screen, alive and
   * where they fell, until they respawned; one sent before a freeze landed lifted it.
   */
  private[game] def newWordOn(playerId: UUID, seq: Int): Boolean = {
    val last = newestWord.get(playerId)
    if (last != null && !GameClient.isNewer(seq, last.intValue())) false
    else {
      newestWord.put(playerId, Integer.valueOf(seq))
      true
    }
  }

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
    // Someone who has left the match. Updates come over UDP and the leave over TCP, so one sent
    // before the leave can arrive after it; taken, it brought them back as a player called
    // "Player" standing where they left, for good, since no second leave would come for them.
    if (player == null && departedPlayers.containsKey(playerId)) return
    if (!newWordOn(playerId, packet.getSequenceNumber)) return

    if (player == null) {
      // The first we hear of them: as they are now, so nothing below takes it for a move, a
      // knockback or a death, and the rest of it is read as any update is
      player = new Player(playerId, "Player", packet.getPosition, packet.getColorRGB, packet.getHealth)
      player.setCharacterId(packet.getCharacterId)
      player.setKnockbacks(packet.getKnockbacks)
      players.put(playerId, player)
    }

    val wasAlive = !player.isDead
    val oldPos = player.getPosition
    val newPos = packet.getPosition
    val dx = newPos.getX - oldPos.getX
    val dy = newPos.getY - oldPos.getY

    // Knocked back since the last word on them: the renderer slides them to where the server
    // put them (RemoteMotion, by this count), so it is neither a teleport nor a turn to walk
    // off the way they were thrown. A long push used to show as both.
    val knockedBack = packet.getKnockbacks > player.getKnockbacks
    if (knockedBack) player.setKnockbacks(packet.getKnockbacks)

    // Detect teleport: large position jump (Manhattan distance > 3).
    // Skip if the player was dead — a respawn also resets position to an
    // unrelated point on the map and would otherwise be misread as a cast.
    if (wasAlive && !knockedBack && Math.abs(dx) + Math.abs(dy) > 3) {
      teleportAnimations.put(playerId, Array(
        System.currentTimeMillis(),
        oldPos.getX.toLong, oldPos.getY.toLong,
        newPos.getX.toLong, newPos.getY.toLong,
        packet.getColorRGB.toLong
      ))
    }

    if (!knockedBack && (dx != 0 || dy != 0)) {
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

    // Apply effect flags from server. A hold, a root, a slow and a boost last as long as the
    // update says they have left; the rest are leases the next update renews.
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
      player.setFrozenUntil(GameClient.effectEnds(packet.getHeldMs, player.getFrozenUntil, now, 5000))
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
      // The server's updates say how long it has left; the player's own, relayed, only that it is on
      player.setSpeedBoostUntil(GameClient.effectEnds(packet.getSpeedBoostMs, player.getSpeedBoostUntil, now, 1000))
    } else {
      player.setSpeedBoostUntil(0)
    }
    if ((flags & 0x40) != 0) {
      player.setRootedUntil(GameClient.effectEnds(packet.getRootedMs, player.getRootedUntil, now, 3000))
    } else {
      player.setRootedUntil(0)
    }
    if ((flags & 0x80) != 0) {
      player.setSlowedUntil(GameClient.effectEnds(packet.getSlowedMs, player.getSlowedUntil, now, 3000))
      if (packet.getSlowPercent > 0) player.setSlowMultiplier(packet.getSlowPercent / 100f)
    } else {
      player.clearSlow()
    }
    val flags2 = packet.getEffectFlags2
    if ((flags2 & 0x01) != 0) {
      player.setStunnedUntil(GameClient.effectEnds(packet.getHeldMs, player.getStunnedUntil, now, 5000))
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

    if (wasAlive && player.isDead) recordDeath(player)
  }

  /** What the renderer shows of `player` dying, where they are. */
  private def recordDeath(player: Player): Unit = {
    val pos = player.getPosition
    deathAnimations.put(player.getId, Array(
      System.currentTimeMillis(),
      pos.getX.toLong,
      pos.getY.toLong,
      player.getColorRGB.toLong,
      player.getCharacterId.toLong
    ))
  }

  /**
   * The server says `playerId` was killed (GameEvent.KILL, packet `seq`): dead on our screen from
   * now, us included (LocalPlayer.died). The update that says so is a datagram, and was all that
   * did: one lost, and a dead enemy stood on our screen alive where they fell until they respawned,
   * or our own death went unseen — no death, still walking and shooting, every bit of it refused —
   * until we were put back at a spawn. Unless we have heard newer of them already: their respawn's
   * update, overtaking a kill held up on the way.
   */
  private[game] def heardKilled(playerId: UUID, seq: Int): Unit = {
    if (!newWordOn(playerId, seq)) return
    if (playerId.equals(localPlayerId)) died()
    else {
      val player = players.get(playerId)
      if (player != null && !player.isDead) {
        player.setHealth(0)
        // The dead hold nothing up
        player.dropBarrier()
        recordDeath(player)
      }
    }
  }

  /**
   * The server brought `playerId` back at (spawnX, spawnY) (GameEvent.RESPAWN, packet `seq`): us
   * (LocalPlayer.respawned), or anyone else, whole and with nothing on them, as the server's respawn
   * leaves them. Only the update after it used to say so, a datagram: lost, they stayed dead and
   * unseen on our screen until they next moved or were hit, which a player who respawned and stood
   * still at full health might never be.
   */
  private[game] def heardRespawned(playerId: UUID, spawnX: Int, spawnY: Int, seq: Int): Unit = {
    if (playerId.equals(localPlayerId)) {
      // Always ours: it is all that brings us back, and an update after it may have overtaken it
      newWordOn(playerId, seq)
      respawned(spawnX, spawnY)
    } else if (newWordOn(playerId, seq)) {
      val player = players.get(playerId)
      if (player != null) {
        player.setPosition(new Position(spawnX, spawnY))
        player.setHealth(player.getMaxHealth)
        player.setShieldUntil(0)
        player.setGemBoostUntil(0)
        player.setFrozenUntil(0)
        player.setStunnedUntil(0)
        player.setPhasedUntil(0)
        player.setRootedUntil(0)
        player.setSpeedBoostUntil(0)
        player.clearSlow()
        player.clearBurn()
        player.clearPoison()
        player.dropBarrier()
      }
    }
  }

  private[game] def handlePlayerLeave(packet: PlayerLeavePacket): Unit = {
    val playerId = packet.getPlayerId
    val player = players.remove(playerId)
    newestWord.remove(playerId)
    forgetHitOn(playerId)

    if (player != null) {
      departedPlayers.put(playerId, player)
      println(s"GameClient: Player left - ${playerId.toString.substring(0, 8)} ('${player.getName}')")
    }
  }

  /** No match: nobody in it, nobody who has left it, and no word on anyone. */
  private[game] def forgetPlayers(): Unit = {
    players.clear()
    departedPlayers.clear()
    newestWord.clear()
  }

  def getPlayers: ConcurrentHashMap[UUID, Player] = players
}
