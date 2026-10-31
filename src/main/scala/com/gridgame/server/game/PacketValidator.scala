package com.gridgame.server.game

import com.gridgame.server.net.ReplayGuard

import com.gridgame.common.Constants
import com.gridgame.common.model.AbilityDef
import com.gridgame.common.model.Cast
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

/**
 * How far a player may still move (PacketValidator.validateMovement): credit that builds up over time
 * at about twice their pace and is spent by every step. A bad network bunches steps up, holds one back
 * and hurries the next, or loses the ones between two that arrive; taken on the credit the time since
 * has built up, each is judged by how far the player walked over that time, not by how close
 * together two packets happened to land.
 */
private final class MoveCredit(var cells: Double, var at: Long)

/** Fire-rate state for one of a player's attacks. */
private final class AttackClock {
  /** When its latest cast started (0: never). Groups the projectiles one press sends. */
  var castAt = 0L
  /** When the cast before it started (0: none): what a charged shot is timed from. */
  var prevCastAt = 0L
  /** Projectiles that cast has fired. */
  var fired = 0
  /** Where its cooldown counts from: castAt, moved back by on-hit reductions. Kept apart from
    * castAt so a hit landing mid-cast doesn't cut the rest of that cast off. */
  var cooldownFrom = 0L
  /** The projectiles that cast has spawned, as one Cast: made with its first. */
  var cast: Cast = _
}

object PacketValidator {
  /** How long after a cast's first projectile the rest of it may arrive: the network's tolerance,
    * since a bad link delays each datagram of a burst its own way, but never more than half the
    * attack's cooldown, or the next press would be taken as more of the last (and its charge judged
    * from the cast before). At 100ms a burst that came in over a stall lost the rest of its shots. */
  def castWindowMs(cooldownMs: Long): Long = Math.min(Constants.NETWORK_TOLERANCE_MS.toLong, cooldownMs / 2)

  /** Projectiles one primary shot sends: three with a gem boost. Allowed whether or not the server
    * has seen the boost yet, since the item packet can arrive after the shots it boosted. */
  private val PRIMARY_PER_CAST = 3

  /** One clock per AttackSlot: primary, Q, E and the burst shot. */
  private val SLOT_COUNT = 4

  /** Slack on how long a charged shot can have been held, for the two packets it is timed between
    * taking different times to arrive. */
  private val CHARGE_SLACK_MS: Long = Constants.NETWORK_TOLERANCE_MS

  /**
   * The quickest a player can be moving, in cells a ms, Manhattan (a diagonal step is two cells):
   * twice the pace they step at now — phased, sped up, or neither (Movement) — and a quarter more,
   * for frames that hitched. A quicker character is allowed to be quicker.
   */
  def creditRate(moveSpeed: Float, phased: Boolean = false, speedBoost: Boolean = false): Double =
    2.0 / Movement.stepIntervalMs(moveSpeed, charging = false, 0, phased, speedBoost, slowed = false, 1f) * 1.25

  /** How long a player's movement credit builds up for at most: the network's tolerance and a second
    * more. A stall that long, and everything walked in it arriving at once, is taken; standing still
    * any longer banks nothing more. */
  val CreditWindowMs: Long = Constants.NETWORK_TOLERANCE_MS + 1000L

  /** What a step may overdraw a player's credit by: a diagonal step's two cells. */
  val CreditOverdraft = 2.0

  /**
   * How much sooner than its cooldown a cast may come: as much as the network can take off the time
   * between two (NETWORK_TOLERANCE_MS), or the fifth of a long cooldown every attack has always had,
   * and never more than the cooldown. The primary's 500ms had only 100ms of it, and a shot held up on
   * the way had the next one refused.
   */
  def castToleranceMs(cooldownMs: Long): Long =
    Math.min(cooldownMs, Math.max((cooldownMs * 0.2).toLong, Constants.NETWORK_TOLERANCE_MS.toLong))

  /**
   * Where a cast taken at `now` starts its attack's cooldown: on the schedule the attack keeps — a
   * cooldown after the last cast — rather than when this one arrived. A cast the network held up
   * doesn't push the next one's out, and one it hurried doesn't let a client fire faster than the
   * cooldown on the whole: a cast can come early (castToleranceMs), but not every cast can.
   */
  def scheduledCastAt(cooldownFrom: Long, now: Long, cooldownMs: Long): Long =
    if (cooldownFrom <= 0L) now else Math.max(now - castToleranceMs(cooldownMs), cooldownFrom + cooldownMs)

  /** Projectiles one cast of the ability sends. Dashes, blinks and buffs send none. */
  private def projectilesPerCast(ability: AbilityDef): Int = ability.castBehavior match {
    case FanProjectile(count, _) => count
    case StandardProjectile | GroundSlam(_) => 1
    case _ => 0
  }

  /** Is this phased player dashing? A dash is phased while it lasts, as a phase shift is, and goes
    * over walls, as a phase shift doesn't. A kit has one or the other at most (KitTest), and the
    * phased flag is honoured by it (StepRequests.activatePhase), so a phased player whose kit has
    * a dash is dashing — and for the network's tolerance after: the server's phase began when the
    * dash's first update arrived, and on a bad link a step sent near its end can arrive after the
    * server's is over, still crossing the wall the dash goes over. */
  private def isDashing(player: Player, charDef: CharacterDef): Boolean =
    System.currentTimeMillis() - player.getPhasedUntil < Constants.NETWORK_TOLERANCE_MS && hasDash(charDef)

  private def hasDash(charDef: CharacterDef): Boolean = charDef != null &&
    (charDef.qAbility.castBehavior.isInstanceOf[DashBuff] || charDef.eAbility.castBehavior.isInstanceOf[DashBuff])
}

/**
 * @param characterOf where the checks look a player's character up: the roster, unless a test
 *                    needs one the roster doesn't have yet (a character quicker than any in it).
 */
class PacketValidator(characterOf: Byte => CharacterDef = id => CharacterDef.get(id)) {
  import PacketValidator._

  // How far each player may still move (MoveCredit)
  private val moveCredit = new ConcurrentHashMap[UUID, MoveCredit]()

  // The sequence number of the last update each player sent that reached the movement check, taken or
  // not: whether the next one follows it with nothing lost between (followsTheLastSeen)
  private val lastSeenUpdate = new ConcurrentHashMap[UUID, java.lang.Integer]()
  // Fire rate: a clock for each of a player's attacks, indexed by AttackSlot
  private val attackClocks = new ConcurrentHashMap[UUID, Array[AttackClock]]()
  // Sequence number of the newest position applied for each player: an accepted update, or a
  // star's item packet. Positions are absolute, so an update sent before that one can only drag
  // the player back to where they were (a reordered datagram, or a step sent just before a star).
  private val movementFence = new ConcurrentHashMap[UUID, java.lang.Integer]()
  // Sequence number of the newest update whose barrier word (up or not, and which way it faces)
  // was taken for each player. The movement fence can't stand in for it: that one moves only on a
  // position the server accepted, so an update sent before a raise and held up on the way got past
  // it whenever the raise's own position had been refused, and dropped the barrier — which the
  // cooldown then kept down for the rest of its life, while its holder's client showed it up.
  private val barrierFence = new ConcurrentHashMap[UUID, java.lang.Integer]()

  // When each player's current life began (a respawn), for the charge of its first shot
  private val lifeStartedAt = new ConcurrentHashMap[UUID, java.lang.Long]()

  // When each player last reset their cooldowns (R), which its own cooldown runs from. A new life
  // leaves it as it is: nobody has R back for dying
  private val lastCooldownReset = new ConcurrentHashMap[UUID, java.lang.Long]()

  // Where each player's last step in a dash left them: the first update after the dash, from that
  // cell, is its landing (dashLanding)
  private val dashedTo = new ConcurrentHashMap[UUID, (Int, Int)]()

  def validateChargeLevel(chargeLevel: Int): Boolean = {
    chargeLevel >= 0 && chargeLevel <= 100
  }

  /**
   * @param attacksLocked the opening of a free-for-all holds everyone's fire (MatchOpening), so a
   *                       jump no walk could have made isn't a blink or a dash either
   * @param changedLately whether a cell changed (a fence went up) too lately for the client to have
   *                       heard of it (GameInstance.changedLately): a step onto it, or past its corner,
   *                       was sent knowing nothing of it, and is taken. On a bad network a fence going
   *                       up beside someone walking had their step refused, and they snapped back.
   */
  def validateMovement(packet: PlayerUpdatePacket, player: Player, world: WorldData,
                       attacksLocked: Boolean = false,
                       changedLately: (Int, Int) => Boolean = (_, _) => false): Boolean = {
    val pos = packet.getPosition
    val x = pos.getX
    val y = pos.getY
    val previous = lastSeenUpdate.put(packet.getPlayerId, Integer.valueOf(packet.getSequenceNumber))
    val followsTheLastSeen = previous != null && packet.getSequenceNumber == ((previous.intValue + 1) & 0x7FFFFFFF)

    // World bounds check
    if (x < 0 || x >= world.width || y < 0 || y >= world.height) {
      System.err.println(s"PacketValidator: Player ${packet.getPlayerId.toString.substring(0, 8)} out of bounds ($x, $y)")
      Metrics.validationFailed.add(1L, Attrs.VfMovementBounds)
      return false
    }

    // Nobody stands in a wall but someone dashing: a dash goes over whatever lies between. A phase
    // shift walked through walls too; untouchable and quick, it is stopped by them now as anyone
    // is (design/characters.md, *A phase stops at walls*). An update that doesn't move the player
    // isn't a step onto anything: a fence put up where they stand (nothing stops one going up on a
    // player) had every update of theirs refused while they stood there, a barrier's or a charge's,
    // each one corrected as though they had wandered off (found by ThreadedMatchFuzzTest)
    val charDef = characterOf(player.getCharacterId)
    val dashing = isDashing(player, charDef)
    val standing = player.getPosition
    if (!dashing && !world.isWalkable(x, y) && !(x == standing.getX && y == standing.getY) && !changedLately(x, y)) {
      System.err.println(s"PacketValidator: Player ${packet.getPlayerId.toString.substring(0, 8)} moved to non-walkable tile ($x, $y)")
      Metrics.validationFailed.add(1L, Attrs.VfMovementWalkable)
      return false
    }

    // A diagonal step goes between open cells only (WorldData.cornersOpen), as the client and the
    // bots take one, phased or not; a dash goes over corners as it goes over walls. Past a wall's
    // corner, or between two blocks touching at one, is through the wall. Only a single step can be
    // judged so, and only one that comes straight after the last update the server had from them:
    // when a datagram between them was lost, the server knows the ends and not the way, and two
    // steps round a corner, the first of them lost, are no diagonal past it. A dash's landing is a dash's too,
    // however late it comes (dashLanding): it lands on open ground, but from wherever the dash had
    // got to.
    val from = player.getPosition
    val sdx = x - from.getX
    val sdy = y - from.getY
    if (!dashing && !dashLanding(player, charDef) && Math.abs(sdx) == 1 && Math.abs(sdy) == 1 &&
        followsTheLastSeen && !world.cornersOpen(from.getX, from.getY, sdx, sdy) &&
        !changedLately(from.getX + sdx, from.getY) && !changedLately(from.getX, from.getY + sdy)) {
      Metrics.validationFailed.add(1L, Attrs.VfMovementWalkable)
      return false
    }

    // The opening divider between the teams. Checked against where the player is rather than
    // against the target cell alone, so a dash or a blink can't jump the wall the way a step can't
    // walk through it.
    val divider = world.divider
    if (divider != null && divider.stops(from.getX, from.getY, x, y)) {
      Metrics.validationFailed.add(1L, Attrs.VfMovementWalkable)
      return false
    }

    // Pace, on the player's movement credit (MoveCredit): built up at about twice the pace they
    // step at now (creditRate) since it was last topped up, spent by every step, and capped. It
    // used to be a step's worth between one update's arrival and the next, plus two cells: steps a
    // bad network bunched together, or the one that arrived after the steps between were lost, were
    // refused as a speed hack, and the player snapped back. A player's first update in a match is
    // judged on the credit of the network's tolerance (NETWORK_TOLERANCE_MS): a step or a few,
    // never the other side of the map. A phased player (a phase shift, or a dash) builds it at
    // their phased pace; a dash, much quicker than that, has its reach below.
    val now = System.currentTimeMillis()
    val oldPos = player.getPosition
    val dx = x - oldPos.getX
    val dy = y - oldPos.getY
    val distance = Math.abs(dx.toLong) + Math.abs(dy.toLong) // Long arithmetic to prevent overflow
    val rate = creditRate(if (charDef != null) charDef.moveSpeed else 1.0f, player.isPhased, player.hasSpeedBoost)
    val credit = moveCredit.computeIfAbsent(packet.getPlayerId, _ => new MoveCredit(rate * Constants.NETWORK_TOLERANCE_MS, now))
    val available = Math.min(rate * CreditWindowMs, credit.cells + Math.max(0L, now - credit.at) * rate)
    credit.at = now
    credit.cells = available
    if (distance <= available + CreditOverdraft) credit.cells = available - distance
    else {
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
        System.err.println(s"PacketValidator: Player ${packet.getPlayerId.toString.substring(0, 8)} moved $distance cells " +
          f"on ${available}%.1f of credit")
        Metrics.validationFailed.add(1L, Attrs.VfMovementSpeed)
        return false
      }
    }

    // Where a dash's step leaves the player, its landing will come from
    if (dashing) dashedTo.put(packet.getPlayerId, (x, y)) else dashedTo.remove(packet.getPlayerId)
    true
  }

  /**
   * Is this update a dash's landing: the first after the dash, from the very cell the dash's last
   * step put the player on? The client sends where its dash ends as the dash's phase ends — later,
   * after a frame that hitched — and the server's phase began a trip across the wire after the
   * client's, so the landing arrives after the server's phase is over. Judged as an ordinary step, a
   * landing a diagonal cell past a wall's corner, or out of the wall the dash crossed, was refused,
   * and the player held where the dash had got to: in the wall or the pool it was going over (found by
   * RosterSweepTest and LossyNetworkFuzzTest). One step, once a dash: any update after the dash's is
   * a step's, and a server move in between (a knockback cutting the dash short) puts the player
   * somewhere else. Not tied to the dash's phase, which an R in its last moments ends early.
   */
  private def dashLanding(player: Player, charDef: CharacterDef): Boolean = {
    val dashed = dashedTo.get(player.getId)
    val at = player.getPosition
    dashed != null && hasDash(charDef) && dashed._1 == at.getX && dashed._2 == at.getY
  }

  /** True if this update was sent before a position already applied for the player: drop it. */
  def isStaleMovement(playerId: UUID, seqNum: Int): Boolean = {
    val fence = movementFence.get(playerId)
    fence != null && !ReplayGuard.isNewer(seqNum, fence.intValue())
  }

  /** The position carried by packet `seqNum` has been applied. Never moves the fence back.
    * Callers hold the player's lock, which is what makes the check-and-apply atomic. */
  def movementApplied(playerId: UUID, seqNum: Int): Unit = {
    val fence = movementFence.get(playerId)
    if (fence == null || ReplayGuard.isNewer(seqNum, fence.intValue())) {
      movementFence.put(playerId, Integer.valueOf(seqNum))
    }
  }

  /** A (re)join places the player afresh, and a new session restarts its sequence numbers. */
  def resetMovementFence(playerId: UUID): Unit = {
    movementFence.remove(playerId)
    barrierFence.remove(playerId)
  }

  /** Is update `seqNum` newer than every one whose barrier word was taken for the player? Records
    * it if so. Callers hold the player's lock, which is what makes the check-and-take atomic. */
  def takeBarrierWord(playerId: UUID, seqNum: Int): Boolean = {
    val fence = barrierFence.get(playerId)
    if (fence != null && !ReplayGuard.isNewer(seqNum, fence.intValue())) false
    else {
      barrierFence.put(playerId, Integer.valueOf(seqNum))
      true
    }
  }

  /**
   * A new life starts with every attack ready. The client already clears its own cooldowns on
   * respawn (GameClient's RESPAWN handler), so without this the server spent the rest of the
   * last life's cooldown refusing abilities the player could see were ready — silently for a
   * projectile, and as a refused placement for a trap.
   */
  def resetAttackClocks(playerId: UUID): Unit = {
    attackClocks.remove(playerId)
    lifeStartedAt.put(playerId, java.lang.Long.valueOf(System.currentTimeMillis()))
  }

  def validateProjectileSpawn(packet: ProjectilePacket, player: Player): Boolean = {
    val pos = player.getPosition

    // Fired from within three cells of where the server has the shooter, and as far again as they
    // could have walked while the steps that took them there are on their way (Movement.lagCells):
    // a shot fired on the move behind a held-up step was refused as fired from too far away
    val reach = 3 + Movement.lagCells(Constants.NETWORK_TOLERANCE_MS, characterSpeed(player))
    val dx = Math.abs(packet.getX - pos.getX)
    val dy = Math.abs(packet.getY - pos.getY)
    if (dx > reach || dy > reach) {
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
    // primary shot or an ability's whole fan. A new cast may come as early as the network can hurry
    // one (castToleranceMs), on the schedule its casts keep (scheduledCastAt).
    val clock = attackClocks.computeIfAbsent(packet.getPlayerId, _ => Array.fill(SLOT_COUNT)(new AttackClock))(slot)
    val accepted = clock.synchronized {
      val now = System.currentTimeMillis()
      if (now - clock.castAt <= castWindowMs(cooldownMs) && clock.fired < perCast) {
        clock.fired += 1
        true
      } else if (now - clock.cooldownFrom >= cooldownMs - castToleranceMs(cooldownMs)) {
        clock.prevCastAt = clock.castAt
        clock.castAt = now
        clock.cooldownFrom = scheduledCastAt(clock.cooldownFrom, now, cooldownMs)
        clock.fired = 1
        clock.cast = null
        true
      } else false
    }
    if (!accepted) {
      System.err.println(s"PacketValidator: Player ${packet.getPlayerId.toString.substring(0, 8)} attack $slot fired too fast")
    }
    accepted
  }

  /**
   * The cast a projectile the attack in `slot` just fired belongs to: the one its cast's first
   * projectile opened, so the rest of a fan joins it (see Cast), fired from (fromX, fromY). Call it
   * after [[validateProjectileSpawn]] accepted the shot.
   */
  def castFor(playerId: UUID, slot: Int, fromX: Float, fromY: Float): Cast = {
    val clocks = attackClocks.get(playerId)
    if (clocks == null || slot < 0 || slot >= SLOT_COUNT) return new Cast(fromX, fromY)
    val clock = clocks(slot)
    clock.synchronized {
      if (clock.cast == null) clock.cast = new Cast(fromX, fromY)
      clock.cast
    }
  }

  /**
   * How charged a projectile the attack in `slot` just fired can be: what the client asked for, up
   * to what it has had time to charge. Only the primary charges, and only from when it could:
   * since the attack's last cast (a charge begins once its cooldown is over) or since this life
   * began. The charge was taken as the client said, 0 to 100, so a client could fire full charges
   * at the primary's fire rate — five to ten times its damage, twice a second — without the four
   * seconds of holding a charge takes. Call it after [[validateProjectileSpawn]] accepted the shot.
   */
  def chargeAllowed(playerId: UUID, slot: Int, requested: Int): Int = {
    if (slot != AttackSlot.PRIMARY) return 0
    val clocks = attackClocks.get(playerId)
    val lastCast = if (clocks != null) clocks(slot).synchronized(clocks(slot).prevCastAt) else 0L
    val lifeStart = lifeStartedAt.get(playerId)
    val since = Math.max(lastCast, if (lifeStart != null) lifeStart.longValue() else 0L)
    // Nothing to time it from: the first shot of the match, which an opening always holds off
    // for far longer than a charge takes
    if (since <= 0L) return requested
    val held = System.currentTimeMillis() - since + CHARGE_SLACK_MS
    Math.max(0, Math.min(requested, (held * 100 / Constants.CHARGE_MAX_MS).toInt))
  }

  /**
   * A cast that spawns no projectile, held to the same clock a shot is (castToleranceMs,
   * scheduledCastAt). Traps come in on a packet of their own rather than as a spawn request, so they
   * have nowhere else to be counted.
   */
  def validateCast(playerId: UUID, slot: Int, cooldownMs: Int): Boolean = {
    if (slot < 0 || slot >= SLOT_COUNT) return false
    val clock = attackClocks.computeIfAbsent(playerId, _ => Array.fill(SLOT_COUNT)(new AttackClock))(slot)
    clock.synchronized {
      val now = System.currentTimeMillis()
      if (now - clock.cooldownFrom < cooldownMs - castToleranceMs(cooldownMs)) false
      else {
        clock.castAt = now
        clock.cooldownFrom = scheduledCastAt(clock.cooldownFrom, now, cooldownMs)
        clock.fired = 1
        true
      }
    }
  }

  private def characterSpeed(player: Player): Float = {
    val charDef = characterOf(player.getCharacterId)
    if (charDef != null) charDef.moveSpeed else 1.0f
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

  /**
   * R (design/characters.md, *R resets the cooldowns*): has the player's reset its own cooldown
   * behind it, but for the tolerance every attack gets (castToleranceMs), since their last reset, or since
   * `firstFrom` for the first (GameInstance.firstResetFrom: the match's start, or never in
   * practice)? If so it is taken, and the Q and E clocks are cleared, so the next cast of either, a
   * shot or a trap, is judged as though neither had been cast. A phase's and a barrier's cooldowns
   * are the player's own (CooldownResetRequests clears those).
   */
  def takeCooldownReset(playerId: UUID, firstFrom: Long): Boolean = {
    val now = System.currentTimeMillis()
    var taken = false
    lastCooldownReset.compute(playerId, (_, last) => {
      val cooldown = Constants.COOLDOWN_RESET_MS.toLong
      if (now - resetFrom(last, firstFrom) < cooldown - castToleranceMs(cooldown)) last
      else { taken = true; java.lang.Long.valueOf(now) }
    })
    if (taken) {
      val clocks = attackClocks.get(playerId)
      if (clocks != null) {
        clearCooldown(clocks(AttackSlot.Q))
        clearCooldown(clocks(AttackSlot.E))
      }
    }
    taken
  }

  /** How long before the player's next reset would be taken in full, as their client waits for it
    * (ms; 0 if it could go now). */
  def cooldownResetLeftMs(playerId: UUID, firstFrom: Long): Long =
    Math.max(0L, resetFrom(lastCooldownReset.get(playerId), firstFrom) + Constants.COOLDOWN_RESET_MS -
      System.currentTimeMillis())

  private def resetFrom(last: java.lang.Long, firstFrom: Long): Long =
    if (last == null) firstFrom else Math.max(firstFrom, last.longValue())

  private def clearCooldown(clock: AttackClock): Unit = clock.synchronized { clock.cooldownFrom = 0L }

  def removePlayer(playerId: UUID): Unit = {
    moveCredit.remove(playerId)
    lastSeenUpdate.remove(playerId)
    dashedTo.remove(playerId)
    attackClocks.remove(playerId)
    movementFence.remove(playerId)
    barrierFence.remove(playerId)
    lifeStartedAt.remove(playerId)
    lastCooldownReset.remove(playerId)
  }

}
