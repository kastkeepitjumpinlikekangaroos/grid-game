package com.gridgame.server

import com.gridgame.common.Constants
import com.gridgame.common.model.CharacterDef
import com.gridgame.common.model.Player
import com.gridgame.common.model.DashBuff
import com.gridgame.common.model.TeleportCast
import com.gridgame.common.model.WorldData
import com.gridgame.common.protocol.PlayerUpdatePacket
import com.gridgame.common.protocol.ProjectilePacket

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class PacketValidator {
  // Track last position update time per player for speed checking
  private val lastUpdateTime = new ConcurrentHashMap[UUID, java.lang.Long]()
  // Track last projectile spawn time per player for fire rate enforcement
  private val lastProjectileTime = new ConcurrentHashMap[UUID, java.lang.Long]()
  // Track burst start time and count per player (gem boost fires 3 in same frame)
  private val burstStartTime = new ConcurrentHashMap[UUID, java.lang.Long]()
  private val burstCount = new ConcurrentHashMap[UUID, AtomicInteger]()
  // Track last Q/E ability projectile times for per-ability cooldown enforcement
  private val lastQProjectileTime = new ConcurrentHashMap[UUID, java.lang.Long]()
  private val lastEProjectileTime = new ConcurrentHashMap[UUID, java.lang.Long]()
  // Allow one large movement (e.g. Star item teleport) within a time window
  private val itemTeleportAllowed = new ConcurrentHashMap[UUID, java.lang.Long]()

  // Replay protection: separate sequence tracking for TCP and UDP per player
  // TCP and UDP share a single client-side counter but arrive via different transports,
  // so UDP packets can race ahead of TCP — they must be validated independently.
  private val lastTcpSequence = new ConcurrentHashMap[UUID, AtomicInteger]()
  private val lastUdpSequence = new ConcurrentHashMap[UUID, AtomicInteger]()
  // Sliding window bitmap for UDP out-of-order tolerance
  private val sequenceWindow = new ConcurrentHashMap[UUID, Array[Long]]()

  /** Circular comparison in 31-bit sequence space. Returns true if seqNum is ahead of last. */
  private def isNewerSequence(seqNum: Int, last: Int): Boolean = {
    if (last == -1) return true // First packet
    if (seqNum == last) return false // Duplicate
    ((seqNum - last) & 0x7FFFFFFF) < 0x40000000
  }

  def validateSequence(playerId: UUID, seqNum: Int, isUdp: Boolean): Boolean = {
    if (!isUdp) {
      // TCP is ordered: reject if seqNum is not ahead of lastSeen (lock-free CAS loop)
      // Uses circular comparison to handle 31-bit wrap-around
      val lastSeq = lastTcpSequence.computeIfAbsent(playerId, _ => new AtomicInteger(-1))
      var last = lastSeq.get()
      while (isNewerSequence(seqNum, last)) {
        if (lastSeq.compareAndSet(last, seqNum)) return true
        last = lastSeq.get()
      }
      return false
    }

    // UDP: use sliding window for out-of-order tolerance
    val lastSeq = lastUdpSequence.computeIfAbsent(playerId, _ => new AtomicInteger(-1))
    val windowSize = Constants.SEQUENCE_WINDOW_SIZE
    val bitmapLongs = windowSize / 64 // 1024/64 = 16 longs
    // Window is stored as Array[Long] where index 0 = windowBase, rest = bitmap
    val window = sequenceWindow.computeIfAbsent(playerId, _ => new Array[Long](1 + bitmapLongs))

    window.synchronized {
      val windowBase = window(0).toInt

      // Use circular arithmetic to handle 31-bit sequence wraparound
      val delta = (seqNum - windowBase) & 0x7FFFFFFF
      if (delta >= 0x40000000) {
        // Behind window in circular space
        return false
      }

      if (delta >= windowSize) {
        // Ahead of window — advance window
        val shift = delta - windowSize + 1
        if (shift >= windowSize) {
          // Complete reset
          for (i <- 1 to bitmapLongs) window(i) = 0L
        } else {
          // Shift the bitmap
          shiftBitmap(window, shift)
        }
        window(0) = (windowBase + shift).toLong
      }

      // Check and set bit for this seqNum (use circular delta for correct offset)
      val offset = (seqNum - window(0).toInt) & 0x7FFFFFFF
      val longIdx = 1 + (offset / 64)
      val bitIdx = offset % 64

      if ((window(longIdx) & (1L << bitIdx)) != 0) {
        // Already seen this sequence number (replay)
        return false
      }

      window(longIdx) |= (1L << bitIdx)
      lastSeq.set(Math.max(lastSeq.get(), seqNum))
    }

    true
  }

  private def shiftBitmap(window: Array[Long], shift: Int): Unit = {
    val bitmapLongs = window.length - 1 // index 0 is base, rest is bitmap
    val totalBits = bitmapLongs * 64
    if (shift >= totalBits) {
      for (i <- 1 to bitmapLongs) window(i) = 0L
      return
    }
    val longShift = shift / 64
    val bitShift = shift % 64
    for (i <- 1 to bitmapLongs) {
      val srcIdx = i + longShift
      if (srcIdx > bitmapLongs) {
        window(i) = 0L
      } else if (bitShift == 0) {
        window(i) = window(srcIdx)
      } else {
        val lo = window(srcIdx) >>> bitShift
        val hi = if (srcIdx + 1 <= bitmapLongs) window(srcIdx + 1) << (64 - bitShift) else 0L
        window(i) = lo | hi
      }
    }
  }

  def validateHealth(health: Int): Boolean = {
    health >= 0 && health <= Constants.MAX_HEALTH
  }

  def validateChargeLevel(chargeLevel: Int): Boolean = {
    chargeLevel >= 0 && chargeLevel <= 100
  }

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
        val distance = dx.toLong + dy.toLong // Long arithmetic to prevent overflow

        // Skip speed check for phased/dashing players
        if (!player.isPhased) {
          // Max expected speed: 1 cell per MOVE_RATE_LIMIT_MS (50ms)
          // With tolerance: allow 2x expected max + 2 cells grace
          val expectedMaxCells = (deltaMs.toDouble / Constants.MOVE_RATE_LIMIT_MS) * 2 + 2
          if (distance > expectedMaxCells.toLong) {
            // Allow teleport/dash abilities: check if this player's character has TeleportCast or DashBuff
            // on either Q or E ability, and the distance is within the ability's max range
            val charDef = CharacterDef.get(player.getCharacterId)
            val abilities = if (charDef != null) Seq(charDef.qAbility, charDef.eAbility) else Seq.empty
            val isAbilityMovement = abilities.exists { ability =>
              ability.castBehavior match {
                case TeleportCast(maxDistance) => distance <= maxDistance + 2
                case DashBuff(maxDistance, _, _) => distance <= maxDistance + 2
                case _ => false
              }
            }
            if (!isAbilityMovement) {
              // Check for recent item teleport (Star item) — allow within 2s window
              val allowedTime = itemTeleportAllowed.remove(packet.getPlayerId)
              val isItemTeleport = allowedTime != null && (now - allowedTime) < 500
              if (!isItemTeleport) {
                System.err.println(s"PacketValidator: Player ${packet.getPlayerId.toString.substring(0, 8)} speed hack detected: moved $distance cells in ${deltaMs}ms")
                return false
              }
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

    // Validate charge level
    if (!validateChargeLevel(packet.getChargeLevel)) {
      System.err.println(s"PacketValidator: Player ${packet.getPlayerId.toString.substring(0, 8)} invalid charge level ${packet.getChargeLevel}")
      return false
    }

    // Validate projectile type is valid for this character
    val charDef = CharacterDef.get(player.getCharacterId)
    if (charDef == null) return false
    val pType = packet.getProjectileType
    if (pType != charDef.primaryProjectileType &&
        pType != charDef.qAbility.projectileType &&
        pType != charDef.eAbility.projectileType) {
      System.err.println(s"PacketValidator: Player ${packet.getPlayerId.toString.substring(0, 8)} spoofed projectile type $pType")
      return false
    }

    // Fire rate enforcement: 80% of SHOOT_COOLDOWN_MS as minimum gap between shots.
    // Gem boost fires 3 projectiles in a single burst (same frame). Due to TCP/UDP race,
    // player.hasGemBoost may be false when the burst arrives, so we allow bursts of up to 3
    // rapid primary projectiles and enforce cooldown from the burst start time.
    val now = System.currentTimeMillis()
    val lastTime: java.lang.Long = lastProjectileTime.put(packet.getPlayerId, now)
    if (lastTime != null) {
      val gap = now - lastTime.longValue()
      if (pType == charDef.primaryProjectileType) {
        val burst = burstCount.computeIfAbsent(packet.getPlayerId, _ => new AtomicInteger(0))
        if (gap <= 100) {
          // Rapid fire — part of a burst (gem boost). Allow up to 3 per burst.
          if (burst.incrementAndGet() > 3) {
            System.err.println(s"PacketValidator: Player ${packet.getPlayerId.toString.substring(0, 8)} burst too large")
            return false
          }
        } else {
          // New shot — check cooldown from burst start time (not last projectile)
          val burstStart = burstStartTime.getOrDefault(packet.getPlayerId, lastTime)
          val gapFromBurst = now - burstStart.longValue()
          val minGap = (Constants.SHOOT_COOLDOWN_MS * 0.8).toLong
          if (gapFromBurst < minGap) {
            System.err.println(s"PacketValidator: Player ${packet.getPlayerId.toString.substring(0, 8)} fire rate too fast (${gapFromBurst}ms)")
            return false
          }
          burst.set(1)
          burstStartTime.put(packet.getPlayerId, now)
        }
      } else {
        // Ability projectile — enforce per-ability cooldown
        val (tracker, cooldownMs) =
          if (pType == charDef.qAbility.projectileType)
            (lastQProjectileTime, charDef.qAbility.cooldownMs)
          else
            (lastEProjectileTime, charDef.eAbility.cooldownMs)
        val lastFire: java.lang.Long = tracker.put(packet.getPlayerId, now)
        if (lastFire != null) {
          val gap = now - lastFire.longValue()
          if (gap > 100 && gap < (cooldownMs * 0.8).toLong) return false
        }
      }
    } else {
      // First projectile ever — initialize burst tracking
      burstCount.computeIfAbsent(packet.getPlayerId, _ => new AtomicInteger(1))
      burstStartTime.put(packet.getPlayerId, now)
    }

    true
  }

  /** Reduce per-ability fire rate cooldown for the given player (mirrors client-side on-hit reduction). */
  def reduceAbilityCooldown(playerId: UUID, projectileType: Byte, characterId: Byte): Unit = {
    val charDef = CharacterDef.get(characterId)
    if (charDef == null) return

    val (tracker, cooldownMs) =
      if (projectileType == charDef.qAbility.projectileType)
        (lastQProjectileTime, charDef.qAbility.cooldownMs.toLong)
      else if (projectileType == charDef.eAbility.projectileType)
        (lastEProjectileTime, charDef.eAbility.cooldownMs.toLong)
      else return

    val lastFire: java.lang.Long = tracker.get(playerId)
    if (lastFire == null) return

    val now = System.currentTimeMillis()
    val remaining = lastFire + cooldownMs - now
    if (remaining > 0) {
      tracker.put(playerId, java.lang.Long.valueOf(lastFire - remaining / 2))
    }
  }

  /** Allow one large movement for this player (e.g. Star item teleport). */
  def allowItemTeleport(playerId: UUID): Unit = {
    itemTeleportAllowed.put(playerId, System.currentTimeMillis())
  }

  def removePlayer(playerId: UUID): Unit = {
    lastUpdateTime.remove(playerId)
    lastProjectileTime.remove(playerId)
    burstStartTime.remove(playerId)
    burstCount.remove(playerId)
    lastQProjectileTime.remove(playerId)
    lastEProjectileTime.remove(playerId)
    lastTcpSequence.remove(playerId)
    lastUdpSequence.remove(playerId)
    sequenceWindow.remove(playerId)
    itemTeleportAllowed.remove(playerId)
  }

  /** Remove entries for players no longer in the connected set (safety net for leaked state). */
  def cleanupStale(connectedPlayerIds: java.util.Set[UUID]): Unit = {
    val maps: Seq[ConcurrentHashMap[UUID, _]] = Seq(lastUpdateTime, lastProjectileTime, lastQProjectileTime, lastEProjectileTime, lastTcpSequence, lastUdpSequence, sequenceWindow, itemTeleportAllowed)
    maps.foreach { map =>
      val iter = map.keySet().iterator()
      while (iter.hasNext) {
        if (!connectedPlayerIds.contains(iter.next())) iter.remove()
      }
    }
  }
}
