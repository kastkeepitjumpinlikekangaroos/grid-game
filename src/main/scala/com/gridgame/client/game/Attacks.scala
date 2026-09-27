package com.gridgame.client.game

import com.gridgame.client.audio.AudioManager
import com.gridgame.common.Constants
import com.gridgame.common.model._
import com.gridgame.common.protocol._

import java.util.concurrent.atomic.AtomicLong

/**
 * Our attacks: where we aim, charging the primary, firing it (and the burst along all eight
 * directions), and casting the Q and E abilities, each on its own cooldown. A shot is only asked
 * of the server (ProjectilePacket.spawnRequest): the projectile is drawn when the server's SPAWN
 * comes back (Projectiles). Nothing attacks while we are held, phased or in a ceasefire.
 */
trait Attacks { this: GameClient =>
  // Where the cursor is, in world coordinates: what we aim at
  @volatile private[game] var mouseWorldX: Double = 0.0
  @volatile private[game] var mouseWorldY: Double = 0.0

  // When each ability was last cast (its cooldown runs from then)
  private[game] val lastQAbilityTime: AtomicLong = new AtomicLong(0)
  private[game] val lastEAbilityTime: AtomicLong = new AtomicLong(0)

  // The flash of the last cast, for the renderer: when, and which way
  private val lastCastTime: AtomicLong = new AtomicLong(0)
  @volatile private var lastCastDirX: Float = 0f
  @volatile private var lastCastDirY: Float = 0f

  // Charge shot state
  @volatile var isCharging: Boolean = false
  private val chargingStartTime: AtomicLong = new AtomicLong(0)
  private val lastChargingUpdateTime: AtomicLong = new AtomicLong(0)

  // ── Aim ─────────────────────────────────────────────────────────────────

  def setMouseWorldPosition(x: Double, y: Double): Unit = {
    mouseWorldX = x
    mouseWorldY = y
  }

  def getMouseWorldX: Double = mouseWorldX

  def getMouseWorldY: Double = mouseWorldY

  /** Which way we aim, in world radians: at the cursor, or the way we face when it is on us. A
    * raised barrier faces this way. */
  def getAimAngle: Float = {
    val pos = localPosition.get()
    val dx = mouseWorldX - pos.getX
    val dy = mouseWorldY - pos.getY
    if (dx * dx + dy * dy > 1e-4) Math.atan2(dy, dx).toFloat
    else localDirection.get() match {
      case Direction.Up    => (-Math.PI / 2).toFloat
      case Direction.Down  => (Math.PI / 2).toFloat
      case Direction.Left  => Math.PI.toFloat
      case Direction.Right => 0f
    }
  }

  private def getAimDirection: (Float, Float) = {
    val pos = localPosition.get()
    val dx = (mouseWorldX - pos.getX).toFloat
    val dy = (mouseWorldY - pos.getY).toFloat
    val len = Math.sqrt(dx * dx + dy * dy).toFloat
    if (len > 0.01f) (dx / len, dy / len) else {
      val dir = localDirection.get()
      dir match {
        case Direction.Up    => (0.0f, -1.0f)
        case Direction.Down  => (0.0f, 1.0f)
        case Direction.Left  => (-1.0f, 0.0f)
        case Direction.Right => (1.0f, 0.0f)
      }
    }
  }

  // ── Charging, firing, casting ───────────────────────────────────────────

  def sendChargingUpdate(): Unit = {
    val now = System.currentTimeMillis()
    if (now - lastChargingUpdateTime.get() < 100) return // Rate limit to every 100ms
    lastChargingUpdateTime.set(now)

    sendPositionUpdate(localPosition.get())
  }

  def startCharging(): Unit = {
    if (isFrozen) return
    // No charging up through the ceasefire either: a bar that fills and then fires nothing is
    // worse than a button that does nothing
    if (attacksLocked) return
    isCharging = true
    chargingStartTime.set(System.currentTimeMillis())
  }

  def cancelCharging(): Unit = {
    isCharging = false
    // Send update so remote clients see charge drop to 0
    sendPositionUpdate(localPosition.get())
  }

  def getChargeLevel: Int = {
    if (!isCharging) return 0
    val elapsed = System.currentTimeMillis() - chargingStartTime.get()
    Math.min(100, (elapsed * 100 / Constants.CHARGE_MAX_MS).toInt)
  }

  def shoot(): Unit = {
    if (isDead || isPhased || isFrozen) return

    // Shoot in the direction the player is facing
    val direction = localDirection.get()
    val (dx, dy) = direction match {
      case Direction.Up    => (0.0f, -1.0f)
      case Direction.Down  => (0.0f, 1.0f)
      case Direction.Left  => (-1.0f, 0.0f)
      case Direction.Right => (1.0f, 0.0f)
    }
    shootToward(dx, dy, 0)
  }

  def shootToward(dx: Float, dy: Float, chargeLevel: Int = 0): Unit = {
    if (isDead || isPhased || isFrozen) return
    if (attacksLocked) return // the opening ceasefire of a free-for-all (MatchOpening)
    if (isPracticeMode) practiceShots += 1
    dropBarrier()

    val pos = localPosition.get()
    val chargeByte = Math.min(100, Math.max(0, chargeLevel)).toByte

    val primaryType = getSelectedCharacterDef.primaryProjectileType

    if (hasGemBoost) {
      // Shoot 3 projectiles in a narrow cone (center ± 15 degrees)
      val angle = Math.PI / 36.0 // 5 degrees
      val offsets = Seq(0.0, -angle, angle)
      offsets.foreach { theta =>
        val cos = Math.cos(theta).toFloat
        val sin = Math.sin(theta).toFloat
        val rdx = dx * cos - dy * sin
        val rdy = dx * sin + dy * cos
        send(ProjectilePacket.spawnRequest(
          sequenceNumber.getAndIncrement(), localPlayerId,
          pos.getX.toFloat, pos.getY.toFloat, localColorRGB, rdx, rdy,
          chargeByte, primaryType, AttackSlot.PRIMARY))
      }
    } else {
      send(ProjectilePacket.spawnRequest(
        sequenceNumber.getAndIncrement(), localPlayerId,
        pos.getX.toFloat, pos.getY.toFloat, localColorRGB, dx, dy,
        chargeByte, primaryType, AttackSlot.PRIMARY))
    }
  }

  /** Burst shot (Shift+Space): the character's primary projectile along all eight compass points
    * (AttackSlot.BURST), at the cost of standing still for a moment. It used to send four of the
    * default bolt, which the server refused from every character but Spaceman, whose primary it
    * is: pressing it rooted the player and fired nothing. */
  def shootAllDirections(): Unit = {
    if (isDead || isPhased || isFrozen) return
    if (attacksLocked) return // it is the primary in eight directions, so the ceasefire holds it too
    dropBarrier()

    val pos = localPosition.get()

    // Block movement for 500ms
    movementBlockedUntil.set(System.currentTimeMillis() + Constants.BURST_SHOT_MOVEMENT_BLOCK_MS)

    val primaryType = getSelectedCharacterDef.primaryProjectileType
    AttackSlot.BurstDirections.foreach { case (dx, dy) =>
      send(ProjectilePacket.spawnRequest(
        sequenceNumber.getAndIncrement(), localPlayerId,
        pos.getX.toFloat, pos.getY.toFloat, localColorRGB, dx, dy,
        0.toByte, primaryType, AttackSlot.BURST))
    }
  }

  def shootAbility(slot: Int): Unit = {
    if (isDead || isFrozen) return
    // Held by a free-for-all's opening ceasefire (MatchOpening). Refused here rather than sent
    // and refused, so the cooldown isn't spent on a cast the server was never going to allow.
    if (attacksLocked) return

    val charDef = getSelectedCharacterDef
    val (abilityDef, lastAbilityTime, attackSlot) = slot match {
      case 0 => (charDef.qAbility, lastQAbilityTime, AttackSlot.Q)
      case 1 => (charDef.eAbility, lastEAbilityTime, AttackSlot.E)
      case _ => return
    }

    // Can't use abilities while phased (except Phase Shift itself is already activated)
    if (isPhased) return

    val now = System.currentTimeMillis()
    if (now - lastAbilityTime.get() < abilityDef.cooldownMs) return

    // Rooted, we attack but go nowhere: the server applies no step, blink or dash while a root
    // lasts, and says nothing when a rooted client blinks anyway — so ours showed the blink, and
    // the server had us where we were. Refused here, the cooldown isn't spent on it.
    abilityDef.castBehavior match {
      case _: DashBuff | _: TeleportCast if isRooted => return
      case _ =>
    }

    // A trap has to have somewhere to land. With nowhere — aimed into a wall from inside one —
    // nothing is cast and the cooldown is not spent, so the cell is picked before anything else
    // happens. The server picks it the same way (TrapPlacement), from its own copy of where we
    // are, so a placement we show is one it takes.
    val trapCell = abilityDef.castBehavior match {
      case TrapCast(_, range) =>
        val pos = localPosition.get()
        val cell = TrapPlacement.target(currentWorld.get(), pos.getX, pos.getY, mouseWorldX, mouseWorldY, range)
        if (cell.isEmpty) return
        cell
      case _ => None
    }

    lastAbilityTime.set(now)
    // Casting anything drops a raised barrier. (Raising one can't: it is on cooldown while up.)
    dropBarrier()

    // Track cast flash direction
    val (castDx, castDy) = getAimDirection
    lastCastDirX = castDx
    lastCastDirY = castDy
    lastCastTime.set(now)

    abilityDef.castBehavior match {
      case DashBuff(maxDistance, durationMs, _) =>
        AudioManager.playDash()
        // Dash toward cursor, phased during dash (e.g. Raptor Swoop)
        dash(maxDistance, durationMs, now)

      case PhaseShiftBuff(durationMs) =>
        AudioManager.playPhaseShift()
        phasedUntil.set(now + durationMs)
        sendPositionUpdate(localPosition.get())

      case BarrierCast(durationMs) =>
        AudioManager.playBarrierUp()
        raiseBarrier(now, durationMs)

      case TeleportCast(maxDistance) =>
        AudioManager.playTeleport()
        // This ability's own range: a blink on Q used to take E's (Glitcher blinked 16 of its 6)
        performBlink(maxDistance)

      case fan @ FanProjectile(count, _) =>
        val pos = localPosition.get()
        val (ndx, ndy) = getAimDirection
        for (i <- 0 until count) {
          val theta = fan.angleOf(i)
          val cos = Math.cos(theta).toFloat
          val sin = Math.sin(theta).toFloat
          val rdx = ndx * cos - ndy * sin
          val rdy = ndx * sin + ndy * cos
          send(ProjectilePacket.spawnRequest(
            sequenceNumber.getAndIncrement(), localPlayerId,
            pos.getX.toFloat, pos.getY.toFloat, localColorRGB, rdx, rdy,
            0.toByte, abilityDef.projectileType, attackSlot))
        }

      case GroundSlam(_) =>
        val pos = localPosition.get()
        send(ProjectilePacket.spawnRequest(
          sequenceNumber.getAndIncrement(), localPlayerId,
          pos.getX.toFloat, pos.getY.toFloat, localColorRGB, 0.0f, 0.0f,
          0.toByte, abilityDef.projectileType, attackSlot))

      case TrapCast(trapType, _) =>
        val cell = trapCell.get
        AudioManager.playTrapPlace()
        send(new TrapPacket(sequenceNumber.getAndIncrement(), localPlayerId,
          cell.getX, cell.getY, 0, TrapAction.PLACE, trapType, localTeamId, attackSlot, null))

      case StandardProjectile =>
        val pos = localPosition.get()
        val (ndx, ndy) = getAimDirection
        send(ProjectilePacket.spawnRequest(
          sequenceNumber.getAndIncrement(), localPlayerId,
          pos.getX.toFloat, pos.getY.toFloat, localColorRGB, ndx, ndy,
          0.toByte, abilityDef.projectileType, attackSlot))
    }
  }

  // ── Cooldowns ───────────────────────────────────────────────────────────

  def getLastCastTime: Long = lastCastTime.get()
  def getLastCastDirX: Float = lastCastDirX
  def getLastCastDirY: Float = lastCastDirY

  /** A new life: every ability ready. */
  private[game] def resetCooldowns(): Unit = {
    lastQAbilityTime.set(0)
    lastEAbilityTime.set(0)
  }

  def getQCooldownFraction: Float = {
    val cooldownMs = getSelectedCharacterDef.qAbility.cooldownMs
    val elapsed = System.currentTimeMillis() - lastQAbilityTime.get()
    if (elapsed >= cooldownMs) 0.0f
    else 1.0f - (elapsed.toFloat / cooldownMs)
  }

  def getECooldownFraction: Float = {
    val cooldownMs = getSelectedCharacterDef.eAbility.cooldownMs
    val elapsed = System.currentTimeMillis() - lastEAbilityTime.get()
    if (elapsed >= cooldownMs) 0.0f
    else 1.0f - (elapsed.toFloat / cooldownMs)
  }

  def getQCooldownRemaining: Float = {
    val cooldownMs = getSelectedCharacterDef.qAbility.cooldownMs
    val remaining = lastQAbilityTime.get() + cooldownMs - System.currentTimeMillis()
    if (remaining <= 0) 0.0f else remaining / 1000.0f
  }

  def getECooldownRemaining: Float = {
    val cooldownMs = getSelectedCharacterDef.eAbility.cooldownMs
    val remaining = lastEAbilityTime.get() + cooldownMs - System.currentTimeMillis()
    if (remaining <= 0) 0.0f else remaining / 1000.0f
  }

  private[game] def reduceAbilityCooldownOnHit(projectileType: Byte): Unit = {
    val charDef = getSelectedCharacterDef
    val now = System.currentTimeMillis()

    val (abilityDef, lastAbilityTime) =
      if (charDef.qAbility.projectileType == projectileType) (charDef.qAbility, lastQAbilityTime)
      else if (charDef.eAbility.projectileType == projectileType) (charDef.eAbility, lastEAbilityTime)
      else return

    val remaining = lastAbilityTime.get() + abilityDef.cooldownMs - now
    if (remaining > 0) {
      lastAbilityTime.addAndGet(-remaining / 2)
    }
  }

  /** How long after a refused cast the ability may be tried again. */
  private val REFUND_RETRY_MS = 400

  private[game] def refundAbility(lastCast: AtomicLong, cooldownMs: Int): Unit =
    lastCast.set(System.currentTimeMillis() - Math.max(0, cooldownMs - REFUND_RETRY_MS))
}
