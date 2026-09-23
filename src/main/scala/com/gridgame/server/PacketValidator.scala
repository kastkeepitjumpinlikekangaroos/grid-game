package com.gridgame.server

import com.gridgame.common.Constants
import com.gridgame.common.model.AbilityDef
import com.gridgame.common.model.CharacterDef
import com.gridgame.common.model.Player
import com.gridgame.common.model.DashBuff
import com.gridgame.common.model.FanProjectile
import com.gridgame.common.model.GroundSlam
import com.gridgame.common.model.Movement
import com.gridgame.common.model.StandardProjectile
import com.gridgame.common.model.Teleport
import com.gridgame.common.model.TeleportCast
import com.gridgame.common.model.WorldData
import com.gridgame.common.observability.Attrs
import com.gridgame.common.observability.Metrics
import com.gridgame.common.protocol.AttackSlot
import com.gridgame.common.protocol.PlayerUpdatePacket
import com.gridgame.common.protocol.ProjectilePacket

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** Fire-rate state for one of a player's attacks. */
private final class AttackClock {
  /** When its latest cast started (0: never). Groups the projectiles one press sends. */
  var castAt = 0L
  /** Projectiles that cast has fired. */
  var fired = 0
  /** Where its cooldown counts from: castAt, moved back by on-hit reductions. Kept apart from
    * castAt so a hit landing mid-cast doesn't cut the rest of that cast off. */
  var cooldownFrom = 0L
}

object PacketValidator {
  /** How long after a cast's first projectile the rest of it may arrive. The client sends them all
    * in the same frame. */
  private val CAST_WINDOW_MS = 100L

  /** Projectiles one primary shot sends: three with a gem boost. Allowed whether or not the server
    * has seen the boost yet, since the item packet can arrive after the shots it boosted. */
  private val PRIMARY_PER_CAST = 3

  /** One clock per AttackSlot: primary, Q, E and the burst shot. */
  private val SLOT_COUNT = 4

  /**
   * The furthest a character of this speed could honestly have walked in `deltaMs`: twice what
   * their own step interval allows (Movement), plus two cells of grace for a client whose
   * updates bunched up. A quicker character is allowed to be quicker; the tolerance is not a
   * flat number of cells that a fast character would eat into and a slow one would never reach.
   */
  def maxCellsIn(deltaMs: Long, moveSpeed: Float): Long =
    ((deltaMs.toDouble / Movement.baseStepIntervalMs(moveSpeed)) * 2 + 2).toLong

  /** Projectiles one cast of the ability sends. Dashes, blinks and buffs send none. */
  private def projectilesPerCast(ability: AbilityDef): Int = ability.castBehavior match {
    case FanProjectile(count, _) => count
    case StandardProjectile | GroundSlam(_) => 1
    case _ => 0
  }
}

/**
 * @param characterOf where the checks look a player's character up: the roster, unless a test
 *                    needs one the roster doesn't have yet (a character quicker than any in it).
 */
class PacketValidator(characterOf: Byte => CharacterDef = id => CharacterDef.get(id)) {
  import PacketValidator._

  // Track last position update time per player for speed checking
  private val lastUpdateTime = new ConcurrentHashMap[UUID, java.lang.Long]()
  // Fire rate: a clock for each of a player's attacks, indexed by AttackSlot
  private val attackClocks = new ConcurrentHashMap[UUID, Array[AttackClock]]()
  // Sequence number of the newest position applied for each player: an accepted update, or a
  // star's item packet. Positions are absolute, so an update sent before that one can only drag
  // the player back to where they were (a reordered datagram, or a step sent just before a star).
  private val movementFence = new ConcurrentHashMap[UUID, java.lang.Integer]()

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

  def validateChargeLevel(chargeLevel: Int): Boolean = {
    chargeLevel >= 0 && chargeLevel <= 100
  }

  /**
   * @param attacksLocked the opening of a free-for-all holds everyone's fire (MatchOpening), so a
   *                       jump no walk could have made isn't a blink or a dash either
   */
  def validateMovement(packet: PlayerUpdatePacket, player: Player, world: WorldData,
                       attacksLocked: Boolean = false): Boolean = {
    val pos = packet.getPosition
    val x = pos.getX
    val y = pos.getY

    // World bounds check
    if (x < 0 || x >= world.width || y < 0 || y >= world.height) {
      System.err.println(s"PacketValidator: Player ${packet.getPlayerId.toString.substring(0, 8)} out of bounds ($x, $y)")
      Metrics.validationFailed.add(1L, Attrs.VfMovementBounds)
      return false
    }

    // Walkability check (skip for phased players)
    if (!player.isPhased && !world.isWalkable(x, y)) {
      System.err.println(s"PacketValidator: Player ${packet.getPlayerId.toString.substring(0, 8)} moved to non-walkable tile ($x, $y)")
      Metrics.validationFailed.add(1L, Attrs.VfMovementWalkable)
      return false
    }

    // The opening divider between the teams, which a phase doesn't pass either. Checked against
    // where the player is rather than against the target cell alone, so a dash or a blink can't
    // jump the wall the way a step can't walk through it.
    val divider = world.divider
    if (divider != null) {
      val from = player.getPosition
      if (divider.stops(from.getX, from.getY, x, y)) {
        Metrics.validationFailed.add(1L, Attrs.VfMovementWalkable)
        return false
      }
    }

    // Speed check
    val now = System.currentTimeMillis()
    val lastTime: java.lang.Long = lastUpdateTime.put(packet.getPlayerId, now)
    if (lastTime != null) {
      // Updates handled in the same millisecond are checked too. Skipping them let the second of
      // any pair go anywhere: it quietly waved through refused teleports (the client sends those
      // twice), and any jump a modified client cared to send twice.
      val deltaMs = Math.max(0L, now - lastTime.longValue())
      val oldPos = player.getPosition
      val dx = x - oldPos.getX
      val dy = y - oldPos.getY
      val distance = Math.abs(dx.toLong) + Math.abs(dy.toLong) // Long arithmetic to prevent overflow

      // Skip speed check for phased/dashing players
      if (!player.isPhased) {
        val charDef = characterOf(player.getCharacterId)
        val expectedMaxCells = maxCellsIn(deltaMs, if (charDef != null) charDef.moveSpeed else 1.0f)
        if (distance > expectedMaxCells) {
          // Allow teleport/dash abilities: check if this player's character has TeleportCast or DashBuff
          // on either Q or E ability, and the jump is within the ability's reach
          val abilities = if (charDef != null) Seq(charDef.qAbility, charDef.eAbility) else Seq.empty
          val isAbilityMovement = !attacksLocked && abilities.exists { ability =>
            ability.castBehavior match {
              case TeleportCast(maxDistance) => Teleport.withinReach(dx, dy, maxDistance)
              case DashBuff(maxDistance, _, _) => Teleport.withinReach(dx, dy, maxDistance)
              case _ => false
            }
          }
          // A star needs no exception here: its item packet moves the player (ClientHandler),
          // so the client's next update starts from the new cell
          if (!isAbilityMovement) {
            System.err.println(s"PacketValidator: Player ${packet.getPlayerId.toString.substring(0, 8)} speed hack detected: moved $distance cells in ${deltaMs}ms")
            Metrics.validationFailed.add(1L, Attrs.VfMovementSpeed)
            return false
          }
        }
      }
    }

    true
  }

  /** True if this update was sent before a position already applied for the player: drop it. */
  def isStaleMovement(playerId: UUID, seqNum: Int): Boolean = {
    val fence = movementFence.get(playerId)
    fence != null && !isNewerSequence(seqNum, fence.intValue())
  }

  /** The position carried by packet `seqNum` has been applied. Never moves the fence back.
    * Callers hold the player's lock, which is what makes the check-and-apply atomic. */
  def movementApplied(playerId: UUID, seqNum: Int): Unit = {
    val fence = movementFence.get(playerId)
    if (fence == null || isNewerSequence(seqNum, fence.intValue())) {
      movementFence.put(playerId, Integer.valueOf(seqNum))
    }
  }

  /** A (re)join places the player afresh, and a new session restarts its sequence numbers. */
  def resetMovementFence(playerId: UUID): Unit = movementFence.remove(playerId)

  /**
   * A new life starts with every attack ready. The client already clears its own cooldowns on
   * respawn (GameClient's RESPAWN handler), so without this the server spent the rest of the
   * last life's cooldown refusing abilities the player could see were ready — silently for a
   * projectile, and as a refused placement for a trap.
   */
  def resetAttackClocks(playerId: UUID): Unit = attackClocks.remove(playerId)

  /** A new session: its client counts packets from zero again. Packets from the old session
    * can't be replayed into it, since they are signed with the old session's token. */
  def resetSequences(playerId: UUID): Unit = {
    lastTcpSequence.remove(playerId)
    lastUdpSequence.remove(playerId)
    sequenceWindow.remove(playerId)
  }

  def validateProjectileSpawn(packet: ProjectilePacket, player: Player): Boolean = {
    val pos = player.getPosition

    // Spawn position within 3 cells of player position
    val dx = Math.abs(packet.getX - pos.getX)
    val dy = Math.abs(packet.getY - pos.getY)
    if (dx > 3 || dy > 3) {
      System.err.println(s"PacketValidator: Player ${packet.getPlayerId.toString.substring(0, 8)} projectile spawn too far from player")
      Metrics.validationFailed.add(1L, Attrs.VfProjectilePosition)
      return false
    }

    // Validate charge level
    if (!validateChargeLevel(packet.getChargeLevel)) {
      System.err.println(s"PacketValidator: Player ${packet.getPlayerId.toString.substring(0, 8)} invalid charge level ${packet.getChargeLevel}")
      Metrics.validationFailed.add(1L, Attrs.VfProjectileCharge)
      return false
    }

    val charDef = characterOf(player.getCharacterId)
    if (charDef == null) {
      Metrics.validationFailed.add(1L, Attrs.VfCharacter)
      return false
    }

    // Judge it by the attack the client says fired it. The type alone can't say: many characters
    // fire one type from two of their attacks. Bear's Maul is eight of the claws it swipes with,
    // so judged by type it was a primary burst, capped at a gem boost's three: the first three
    // the client sent, which pointed behind the bear.
    val slot = packet.getAttackSlot
    val (slotType, cooldownMs, perCast) = slot match {
      case AttackSlot.PRIMARY => (charDef.primaryProjectileType, Constants.SHOOT_COOLDOWN_MS, PRIMARY_PER_CAST)
      case AttackSlot.Q => (charDef.qAbility.projectileType, charDef.qAbility.cooldownMs, projectilesPerCast(charDef.qAbility))
      case AttackSlot.E => (charDef.eAbility.projectileType, charDef.eAbility.cooldownMs, projectilesPerCast(charDef.eAbility))
      case AttackSlot.BURST => (charDef.primaryProjectileType, Constants.BURST_SHOT_COOLDOWN_MS, AttackSlot.BurstDirections.size)
      case _ => (0.toByte, 0, 0)
    }
    val pType = packet.getProjectileType
    if (perCast == 0 || pType != slotType) {
      System.err.println(s"PacketValidator: Player ${packet.getPlayerId.toString.substring(0, 8)} spoofed projectile type $pType for attack $slot")
      Metrics.validationFailed.add(1L, Attrs.VfProjectileFireRate)
      return false
    }

    // Fire rate. A cast is everything one press of the attack sends, all in the same frame: a
    // primary shot or an ability's whole fan. A new cast needs 80% of that attack's cooldown.
    val clock = attackClocks.computeIfAbsent(packet.getPlayerId, _ => Array.fill(SLOT_COUNT)(new AttackClock))(slot)
    val accepted = clock.synchronized {
      val now = System.currentTimeMillis()
      if (now - clock.castAt <= CAST_WINDOW_MS && clock.fired < perCast) {
        clock.fired += 1
        true
      } else if (now - clock.cooldownFrom >= (cooldownMs * 0.8).toLong) {
        clock.castAt = now
        clock.cooldownFrom = now
        clock.fired = 1
        true
      } else false
    }
    if (!accepted) {
      System.err.println(s"PacketValidator: Player ${packet.getPlayerId.toString.substring(0, 8)} attack $slot fired too fast")
    }
    accepted
  }

  /**
   * A cast that spawns no projectile, held to the same clock a shot is: a new one needs 80% of
   * that attack's cooldown, the tolerance every attack gets. Traps come in on a packet of their
   * own rather than as a spawn request, so they have nowhere else to be counted.
   */
  def validateCast(playerId: UUID, slot: Int, cooldownMs: Int): Boolean = {
    if (slot < 0 || slot >= SLOT_COUNT) return false
    val clock = attackClocks.computeIfAbsent(playerId, _ => Array.fill(SLOT_COUNT)(new AttackClock))(slot)
    clock.synchronized {
      val now = System.currentTimeMillis()
      if (now - clock.cooldownFrom < (cooldownMs * 0.8).toLong) false
      else {
        clock.castAt = now
        clock.cooldownFrom = now
        clock.fired = 1
        true
      }
    }
  }

  /** Reduce per-ability fire rate cooldown for the given player (mirrors client-side on-hit reduction). */
  def reduceAbilityCooldown(playerId: UUID, projectileType: Byte, characterId: Byte): Unit = {
    val charDef = characterOf(characterId)
    if (charDef == null) return

    // Picked by type, exactly as the client's reduceAbilityCooldownOnHit picks it, so the two
    // clocks stay in step
    val (slot, cooldownMs) =
      if (projectileType == charDef.qAbility.projectileType)
        (AttackSlot.Q, charDef.qAbility.cooldownMs.toLong)
      else if (projectileType == charDef.eAbility.projectileType)
        (AttackSlot.E, charDef.eAbility.cooldownMs.toLong)
      else return

    val clocks = attackClocks.get(playerId)
    if (clocks == null) return
    val clock = clocks(slot)
    clock.synchronized {
      val remaining = clock.cooldownFrom + cooldownMs - System.currentTimeMillis()
      if (remaining > 0) clock.cooldownFrom -= remaining / 2
    }
  }

  def removePlayer(playerId: UUID): Unit = {
    lastUpdateTime.remove(playerId)
    attackClocks.remove(playerId)
    lastTcpSequence.remove(playerId)
    lastUdpSequence.remove(playerId)
    sequenceWindow.remove(playerId)
    movementFence.remove(playerId)
  }

  /** Remove entries for players no longer in the connected set (safety net for leaked state). */
  def cleanupStale(connectedPlayerIds: java.util.Set[UUID]): Unit = {
    val maps: Seq[ConcurrentHashMap[UUID, _]] = Seq(lastUpdateTime, attackClocks, lastTcpSequence, lastUdpSequence, sequenceWindow, movementFence)
    maps.foreach { map =>
      val iter = map.keySet().iterator()
      while (iter.hasNext) {
        if (!connectedPlayerIds.contains(iter.next())) iter.remove()
      }
    }
  }
}
