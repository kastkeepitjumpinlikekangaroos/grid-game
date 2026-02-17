package com.gridgame.server

import com.gridgame.common.Constants
import com.gridgame.common.model.CharacterDef
import com.gridgame.common.model.Player
import com.gridgame.common.model.TeleportCast
import com.gridgame.common.model.WorldData
import com.gridgame.common.protocol.PlayerUpdatePacket
import com.gridgame.common.protocol.ProjectilePacket

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class PacketValidator {
  // Track last position update time per player for speed checking
  private val lastUpdateTime = new ConcurrentHashMap[UUID, java.lang.Long]()
  // Track last projectile spawn time per player for fire rate enforcement
  private val lastProjectileTime = new ConcurrentHashMap[UUID, java.lang.Long]()

  def validateMovement(packet: PlayerUpdatePacket, player: Player, world: WorldData): Boolean = {
    val pos = packet.getPosition
    val x = pos.getX
    val y = pos.getY

    // World bounds check
    if (x < 0 || x >= world.width || y < 0 || y >= world.height) {
      System.err.println(s"PacketValidator: Player ${packet.getPlayerId.toString.substring(0, 8)} out of bounds ($x, $y)")
      return false
    }

    // Walkability check (skip for phased players)
    if (!player.isPhased && !world.isWalkable(x, y)) {
      System.err.println(s"PacketValidator: Player ${packet.getPlayerId.toString.substring(0, 8)} moved to non-walkable tile ($x, $y)")
      return false
    }

    // Speed check
    val now = System.currentTimeMillis()
    val lastTime: java.lang.Long = lastUpdateTime.put(packet.getPlayerId, now)
    if (lastTime != null) {
      val deltaMs = now - lastTime.longValue()
      if (deltaMs > 0) {
        val oldPos = player.getPosition
        val dx = Math.abs(x - oldPos.getX)
        val dy = Math.abs(y - oldPos.getY)
        val distance = dx + dy // Manhattan distance

        // Skip speed check for phased/dashing players
        if (!player.isPhased) {
          // Max expected speed: 1 cell per MOVE_RATE_LIMIT_MS (50ms)
          // With tolerance: allow 2x expected max + 2 cells grace
          val expectedMaxCells = (deltaMs.toDouble / Constants.MOVE_RATE_LIMIT_MS) * 2 + 2
          if (distance > expectedMaxCells) {
            // Allow teleport abilities: check if this player's character has TeleportCast
            // and the distance is within the ability's max range
            val charDef = CharacterDef.get(player.getCharacterId)
            val isTeleport = charDef != null && (charDef.eAbility.castBehavior match {
              case TeleportCast(maxDistance) => distance <= maxDistance + 2
              case _ => false
            })
            if (!isTeleport) {
              System.err.println(s"PacketValidator: Player ${packet.getPlayerId.toString.substring(0, 8)} speed hack detected: moved $distance cells in ${deltaMs}ms")
              return false
            }
          }
        }
      }
    }

    true
  }

  def validateProjectileSpawn(packet: ProjectilePacket, player: Player): Boolean = {
    val pos = player.getPosition

    // Spawn position within 3 cells of player position
    val dx = Math.abs(packet.getX - pos.getX)
    val dy = Math.abs(packet.getY - pos.getY)
    if (dx > 3 || dy > 3) {
      System.err.println(s"PacketValidator: Player ${packet.getPlayerId.toString.substring(0, 8)} projectile spawn too far from player")
      return false
    }

    // Fire rate enforcement: 80% of SHOOT_COOLDOWN_MS as minimum gap
    val now = System.currentTimeMillis()
    val lastTime: java.lang.Long = lastProjectileTime.put(packet.getPlayerId, now)
    if (lastTime != null) {
      val gap = now - lastTime.longValue()
      val minGap = (Constants.SHOOT_COOLDOWN_MS * 0.8).toLong
      if (gap < minGap) {
        // Allow abilities which have their own cooldowns — only enforce for primary fire
        val charDef = CharacterDef.get(player.getCharacterId)
        if (packet.getProjectileType == charDef.primaryProjectileType) {
          System.err.println(s"PacketValidator: Player ${packet.getPlayerId.toString.substring(0, 8)} fire rate too fast (${gap}ms)")
          return false
        }
      }
    }

    true
  }

  def removePlayer(playerId: UUID): Unit = {
    lastUpdateTime.remove(playerId)
    lastProjectileTime.remove(playerId)
  }
}
