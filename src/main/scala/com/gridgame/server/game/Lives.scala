package com.gridgame.server.game

import com.gridgame.common.Constants
import com.gridgame.common.model._
import com.gridgame.common.observability.Attrs
import com.gridgame.common.observability.Metrics
import com.gridgame.common.protocol._

import java.util.UUID
import java.util.concurrent.TimeUnit

/** A death scored, and the next life: where each team starts one, and a respawn with nothing carried
  * over from the last. */
trait Lives { this: GameInstance =>
  /**
   * Score a death: the kill (when the killer is known), the kill feed, and the victim's respawn.
   * Callers only get here for the blow that took the victim from alive to dead
   * (Player.damage), which is what keeps two hits, or a hit and a burn tick on another thread,
   * from scoring one death twice and scheduling two respawns.
   */
  private[game] def recordKill(killerId: UUID, victimId: UUID, projectileType: Byte,
                         cause: io.opentelemetry.api.common.Attributes): Unit = {
    if (killerId != null) {
      killTracker.recordKill(killerId, victimId)
      val killer = registry.get(killerId)
      val victim = registry.get(victimId)
      Metrics.kills.add(1L, Attrs.killCombo(
        if (killer != null) killer.getCharacterId else 0,
        if (victim != null) victim.getCharacterId else 0,
        projectileType
      ))
      Metrics.deaths.add(1L, cause)
      broadcastToInstance(new GameEventPacket(
        outbox.nextSeq(),
        killerId,
        GameEvent.KILL,
        gameId,
        getRemainingSeconds,
        killTracker.getKills(killerId).toShort,
        killTracker.getDeaths(killerId).toShort,
        victimId,
        0.toByte, 0.toShort, 0.toShort
      ))
    }
    scheduleRespawn(victimId)
  }

  private def scheduleRespawn(playerId: UUID): Unit = {
    if (respawnExecutor == null || respawnExecutor.isShutdown) return
    respawnExecutor.schedule(new Runnable {
      def run(): Unit = respawn(playerId)
    }, (if (isPractice) 1000L else Constants.RESPAWN_DELAY_MS.toLong), TimeUnit.MILLISECONDS)
  }

  /** Bring a dead player back at a spawn point, with nothing carried over from their last life. */
  private[server] def respawn(playerId: UUID): Unit = {
    if (!running) return
    val player = registry.get(playerId)
    if (player == null) return
    val spawnPoint = spawnLock.synchronized {
      player.setHealth(player.getMaxHealth)
      player.setDirection(Direction.Down)
      player.clearBurn()
      player.clearPoison()
      player.clearSlow()
      player.setRootedUntil(0)
      player.setFrozenUntil(0)
      player.setStunnedUntil(0)
      player.setSpeedBoostUntil(0)
      // The client drops these on respawn too. Kept here, a shield or phase still running from
      // the last life made the new one briefly unhittable, and a gem boost doubled the speed
      // of shots the client fired as unboosted.
      player.setShieldUntil(0)
      player.setGemBoostUntil(0)
      player.setPhasedUntil(0)
      // Down, and ready: the client starts every life with its abilities off cooldown, and the
      // clocks the server holds them to are cleared with it (handler.resetAttacks below)
      player.setBarrierUntil(0)
      player.setBarrierRaisedAt(0)
      player.resetRegenAccumulator()
      // Clear inventory on respawn
      itemManager.clearInventory(playerId)
      val occupied = {
        val set = scala.collection.mutable.HashSet[(Int, Int)]()
        registry.forEachPlayer { p =>
          if (!p.isDead && !p.getId.equals(playerId)) {
            set += ((p.getPosition.getX, p.getPosition.getY))
          }
        }
        set.toSet
      }
      handler.resetAttacks(playerId)
      val sp = spawnFor(player.getTeamId, occupied)
      // Placed inside the lock, so a respawn at the same moment sees this spawn as taken. It is
      // a server move: anything the client sent from its last life is stale.
      placeByServer(player, sp)
      sp
    }

    Metrics.respawns.add(1L, io.opentelemetry.api.common.Attributes.empty())
    // Send respawn event
    val respawnPacket = new GameEventPacket(
      outbox.nextSeq(),
      playerId,
      GameEvent.RESPAWN,
      gameId,
      getRemainingSeconds,
      killTracker.getKills(playerId).toShort,
      killTracker.getDeaths(playerId).toShort,
      null,
      0.toByte,
      spawnPoint.getX.toShort,
      spawnPoint.getY.toShort
    )
    broadcastToInstance(respawnPacket)
    sendStateToPlayer(player)

    // Broadcast updated player state
    broadcastToInstance(stateUpdate(player))
  }

  /**
   * Where a player of this team starts a life. Each team keeps one half of the map for the whole
   * of a team match (TeamDivider), so a death puts them back with their team rather than wherever
   * there happened to be room.
   */
  private[server] def spawnFor(teamId: Byte, occupied: Set[(Int, Int)]): Position = {
    val side = if (gameMode == 1) TeamDivider.sideOfTeam(teamId) else 0
    if (side == 0) world.getValidSpawnPoint(occupied)
    else world.getValidSpawnPoint(occupied, (x, y) => TeamDivider.sideOf(world, x, y) == side)
  }
}
