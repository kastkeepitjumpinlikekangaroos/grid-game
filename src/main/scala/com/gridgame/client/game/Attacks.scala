package com.gridgame.client.game

import com.gridgame.client.audio.AudioManager
import com.gridgame.common.Constants
import com.gridgame.common.model._
import com.gridgame.common.protocol._

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicLongArray

/**
 * Our attacks: where we aim, charging the primary, firing it (and the burst along all eight
 * directions), and casting the Q and E abilities, each on its own cooldown. A shot is only asked
 * of the server (ProjectilePacket.spawnRequest): the projectile is drawn when the server's SPAWN
 * comes back (Projectiles). Nothing attacks while we are held, phased or in a ceasefire.
 *
 * Q and E are aimed, then cast with a click (design/characters.md, *Casting mode*): the key puts
 * the ability in our hands, the renderer lays where it will go on the ground (AimArrowPainter) while
 * we walk and aim, and the primary button casts it toward the aim of that moment. An ability with
 * nothing to aim, a slam round us among them, goes off as its key goes down
 * ([[Attacks.goesOffOnPress]]). The primary is held to aim, and goes off as its button comes up. The
 * right mouse button puts away whatever is aimed or held without it going off ([[cancelAims]]). R has
 * both abilities ready again, once a minute ([[pressReset]]).
 */
trait Attacks { this: GameClient =>
  import Attacks._

  // Where the cursor is, in world coordinates: what we aim at
  @volatile private[game] var mouseWorldX: Double = 0.0
  @volatile private[game] var mouseWorldY: Double = 0.0

  // When each ability was last cast (its cooldown runs from then)
  private[game] val lastQAbilityTime: AtomicLong = new AtomicLong(0)
  private[game] val lastEAbilityTime: AtomicLong = new AtomicLong(0)

  // R: when it was last pressed, or when the match began, on the client's clock (nanoClock); its own
  // cooldown runs from then. NOT_HELD (ready) before any match, and as practice begins. A new life
  // leaves it as it is
  private val lastCooldownReset: AtomicLong = new AtomicLong(NOT_HELD)
  // Q's and E's casts as they were before the last R, until the server has had its say: a refusal
  // puts them back (handleCooldownReset). NOT_HELD: nothing waiting on it
  private val qBeforeReset: AtomicLong = new AtomicLong(NOT_HELD)
  private val eBeforeReset: AtomicLong = new AtomicLong(NOT_HELD)
  // An R of ours this match the server may yet refuse (it says nothing when it takes one)
  private val resetAsked: AtomicBoolean = new AtomicBoolean(false)

  // The attacks being aimed, by AttackSlot: the primary held down, and Q or E since its key went
  // down (only one of the two at a time): since when on the client's clock (nanoClock), or NOT_HELD.
  // The render thread presses, casts and draws them; a new life puts them away from the packet thread
  private val heldSince: AtomicLongArray = {
    val a = new AtomicLongArray(HELD_SLOTS)
    var s = 0
    while (s < HELD_SLOTS) { a.set(s, NOT_HELD); s += 1 }
    a
  }

  // When the primary last went off, on the client's clock: the next waits SHOOT_COOLDOWN_MS. The
  // mouse and the controller each kept their own, on the wall clock
  private val lastPrimaryShot: AtomicLong = new AtomicLong(NOT_HELD)

  // The flash of the last cast, for the renderer: when, and which way
  private val lastCastTime: AtomicLong = new AtomicLong(0)
  @volatile private var lastCastDirX: Float = 0f
  @volatile private var lastCastDirY: Float = 0f

  // Charge shot state. The charge is timed on the client's clock (nanoClock), which only runs
  // forward: on the wall clock, which the OS slews and now and then steps, a charge could jump or
  // read below nothing, and the golden frames couldn't hold one still to draw it
  @volatile var isCharging: Boolean = false
  private val chargingSince: AtomicLong = new AtomicLong(0)
  private val lastChargingUpdateTime: AtomicLong = new AtomicLong(0)

  // Every projectile we ask the server for, until it answers (telemetry)
  private[client] val pendingSpawns = new PendingSpawns()

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

  // ── Casting mode, and the primary held ──────────────────────────────────

  /** The primary button went down. With Q or E being aimed ([[pressAbility]]) the click casts it,
    * toward the aim of this moment, and is nothing more: the primary doesn't go off as the button
    * comes up. A cast that couldn't go off now ([[abilityReady]], or a trap with nowhere to land)
    * casts nothing and spends nothing, and the ability stays aimed for the next click. With nothing
    * aimed it is the primary: one that charges begins its charge (pressed inside its cooldown, as
    * before, nothing happens); any other is held to aim, whatever its cooldown, and goes off when the
    * button comes up ([[releasePrimary]]) if the cooldown has run by then. */
  def pressPrimary(): Unit = {
    if (isDead) return
    val aimed = aimedAbility
    if (aimed >= 0) {
      if (shootAbility(aimed)) letGo(attackSlotOf(aimed))
    } else if (primaryCharges) {
      // Held as it charges, so the renderer can tell it from a Q or E pressed before or after it
      if (primaryCooledDown) { startCharging(); if (isCharging) hold(AttackSlot.PRIMARY) }
    } else hold(AttackSlot.PRIMARY)
  }

  /** The primary button came up: the shot held goes off toward the aim, charged as far as it was. A
    * click that cast an ability held nothing, so nothing goes off. */
  def releasePrimary(): Unit = {
    if (isCharging) {
      letGo(AttackSlot.PRIMARY)
      if (isDead) { cancelCharging(); return }
      val level = getChargeLevel
      cancelCharging()
      lastPrimaryShot.set(nanoClock())
      fireAtAim(level)
    } else if (letGo(AttackSlot.PRIMARY) && !primaryCharges && !isDead && primaryCooledDown) {
      // (A primary that charges only ever goes off charged: let go of without a charge, nothing)
      lastPrimaryShot.set(nanoClock())
      fireAtAim(0)
    }
  }

  /** Q's or E's key went down (slot 0 or 1). An ability with nothing to aim
    * ([[Attacks.goesOffOnPress]]) goes off now. Any other is taken up to be aimed if its cooldown has
    * run (on its cooldown the key does nothing, and a click is the primary's): we walk and aim as
    * ever, the renderer shows where it will go, and the next press of the primary button casts it
    * ([[pressPrimary]]). One ability is aimed at a time, so the other's key takes over from it; the
    * same key again leaves it as it is. The key coming up does nothing. */
  def pressAbility(slot: Int): Unit = {
    if (isDead) return
    val ability = abilityIn(slot)
    if (ability == null) return
    if (goesOffOnPress(ability)) shootAbility(slot)
    else if (abilityCooledDown(slot, ability)) {
      letGo(attackSlotOf(1 - slot))
      if (heldSince.get(attackSlotOf(slot)) == NOT_HELD) hold(attackSlotOf(slot))
    }
  }

  /** Put away everything aimed or held without any of it going off, a charge included: the right
    * mouse button, the chat opening, the window losing the focus. Nothing is cast, so no cooldown is
    * spent. */
  def cancelAims(): Unit = {
    letGoOfAll()
    if (isCharging) cancelCharging()
  }

  /** The ability being aimed, waiting for the click that casts it: 0 (Q), 1 (E), or -1. */
  def aimedAbility: Int =
    if (heldSince.get(AttackSlot.Q) != NOT_HELD) 0 else if (heldSince.get(AttackSlot.E) != NOT_HELD) 1 else -1

  /** The attack the renderer shows the aim of (an AttackSlot): the last pressed of those being aimed,
    * or -1. */
  def aimingSlot: Int = {
    var best = -1
    var bestAt = 0L
    var s = 0
    while (s < HELD_SLOTS) {
      val at = heldSince.get(s)
      if (at != NOT_HELD && (best < 0 || at - bestAt > 0)) { best = s; bestAt = at }
      s += 1
    }
    best
  }

  /** How long the attack in this AttackSlot has been aimed (the primary held, Q or E since its key went
    * down), in ms on the client's clock; -1 if it isn't. */
  def aimedForMs(slot: Int): Long = {
    val at = if (slot >= 0 && slot < HELD_SLOTS) heldSince.get(slot) else NOT_HELD
    if (at == NOT_HELD) -1L else Math.max(0L, (nanoClock() - at) / 1000000L)
  }

  /** Would the primary go off if its button came up now? The renderer draws its aim brighter when it
    * would. What [[shootToward]] refuses, and the primary's cooldown. */
  def primaryReady: Boolean =
    !isDead && !isPhased && !isFrozen && !attacksLocked && primaryCooledDown

  /** Would the ability in slot 0 (Q) or 1 (E) go off now? Not while we are dead, held, phased or in a
    * ceasefire, nor before its cooldown has run, nor a dash or a blink while rooted, nor a barrier
    * while ours is still up. A trap needs somewhere to land besides ([[trapLanding]]), which this
    * doesn't look for. The renderer draws the aim of the ability being aimed brighter when it would. */
  def abilityReady(slot: Int): Boolean = {
    // Held by a free-for-all's opening ceasefire (MatchOpening): refused here rather than sent and
    // refused, so the cooldown isn't spent on a cast the server was never going to allow
    if (isDead || isFrozen || attacksLocked) return false
    val ability = abilityIn(slot)
    if (ability == null) return false
    // Can't use abilities while phased (except Phase Shift itself is already activated)
    if (isPhased) return false
    if (!abilityCooledDown(slot, ability)) return false
    // Rooted, we attack but go nowhere: the server applies no step, blink or dash while a root
    // lasts, and says nothing when a rooted client blinks anyway — so ours showed the blink, and
    // the server had us where we were. Refused here, the cooldown isn't spent on it.
    ability.castBehavior match {
      case _: DashBuff | _: TeleportCast if isRooted => false
      // One barrier at a time. Its cooldown always outlasted it, but R (pressReset) can have it ready
      // again while it is still up: raised again, ours would have run on past the server's
      case _: BarrierCast if hasBarrier => false
      case _ => true
    }
  }

  /**
   * Where a trap thrown now, at most `range` cells toward the aim, would land; None with nowhere to
   * put it (aimed into a wall from inside one). The server picks the cell the same way
   * (TrapPlacement), from its own copy of where we are, so a placement we show is one it takes. An
   * enemy's barrier stops the throw as a wall does (Barriers.enemyBarrierAcross). The throw and its
   * aim both ask this, so the aim shows where it lands.
   */
  def trapLanding(range: Int): Option[Position] = {
    val pos = localPosition.get()
    val cell = TrapPlacement.target(currentWorld.get(), pos.getX, pos.getY, mouseWorldX, mouseWorldY, range,
      (x, y) => enemyBarrierAcross(pos.getX.toFloat, pos.getY.toFloat, x.toFloat, y.toFloat))
    // Nor onto a trap already there, anyone's: one to a cell, and the server refused the throw, after
    // the cast had been spent on it (found by RosterSweepTest)
    if (cell.isDefined && trapAt(cell.get.getX, cell.get.getY)) None else cell
  }

  private def abilityCooledDown(slot: Int, ability: AbilityDef): Boolean = {
    val last = if (slot == 0) lastQAbilityTime else lastEAbilityTime
    System.currentTimeMillis() - last.get() >= ability.cooldownMs
  }

  private def primaryCharges: Boolean =
    ProjectileDef.get(getSelectedCharacterDef.primaryProjectileType).chargeSpeedScaling.isDefined

  private def primaryCooledDown: Boolean = {
    val last = lastPrimaryShot.get()
    last == NOT_HELD || nanoClock() - last >= Constants.SHOOT_COOLDOWN_MS * 1000000L
  }

  /** The primary toward the aim; nothing with the aim on us. */
  private def fireAtAim(chargeLevel: Int): Unit = {
    val pos = localPosition.get()
    val deltaX = mouseWorldX - pos.getX
    val deltaY = mouseWorldY - pos.getY
    val magnitude = Math.sqrt(deltaX * deltaX + deltaY * deltaY)
    if (magnitude < 0.001) return
    shootToward((deltaX / magnitude).toFloat, (deltaY / magnitude).toFloat, chargeLevel)
  }

  private def abilityIn(slot: Int): AbilityDef = slot match {
    case 0 => getSelectedCharacterDef.qAbility
    case 1 => getSelectedCharacterDef.eAbility
    case _ => null
  }

  private def attackSlotOf(slot: Int): Int = if (slot == 0) AttackSlot.Q else AttackSlot.E

  private def hold(attackSlot: Int): Unit = {
    val now = nanoClock()
    heldSince.set(attackSlot, if (now == NOT_HELD) now + 1 else now)
  }

  /** Was the attack in this AttackSlot aimed? It isn't now. */
  private def letGo(attackSlot: Int): Boolean = heldSince.getAndSet(attackSlot, NOT_HELD) != NOT_HELD

  private def letGoOfAll(): Unit = {
    var s = 0
    while (s < HELD_SLOTS) { heldSince.set(s, NOT_HELD); s += 1 }
  }

  // ── Charging, firing, casting ───────────────────────────────────────────

  /** Ask the server for a projectile: one datagram, watched until the server's word of the
    * projectile comes back (PendingSpawns). */
  private def requestSpawn(request: ProjectilePacket): Unit = {
    send(request)
    pendingSpawns.requested(request.getProjectileType, request.getAttackSlot, nanoClock())
  }

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
    chargingSince.set(nanoClock())
    isCharging = true
  }

  def cancelCharging(): Unit = {
    isCharging = false
    // Send update so remote clients see charge drop to 0
    sendPositionUpdate(localPosition.get())
  }

  def getChargeLevel: Int = {
    if (!isCharging) return 0
    val elapsedMs = (nanoClock() - chargingSince.get()) / 1000000L
    Math.max(0, Math.min(100, (elapsedMs * 100 / Constants.CHARGE_MAX_MS).toInt))
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
        requestSpawn(ProjectilePacket.spawnRequest(
          sequenceNumber.getAndIncrement(), localPlayerId,
          pos.getX.toFloat, pos.getY.toFloat, localColorRGB, rdx, rdy,
          chargeByte, primaryType, AttackSlot.PRIMARY))
      }
    } else {
      requestSpawn(ProjectilePacket.spawnRequest(
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

    val pos = localPosition.get()

    // Block movement for 500ms
    movementBlockedUntil.set(System.currentTimeMillis() + Constants.BURST_SHOT_MOVEMENT_BLOCK_MS)

    val primaryType = getSelectedCharacterDef.primaryProjectileType
    AttackSlot.BurstDirections.foreach { case (dx, dy) =>
      requestSpawn(ProjectilePacket.spawnRequest(
        sequenceNumber.getAndIncrement(), localPlayerId,
        pos.getX.toFloat, pos.getY.toFloat, localColorRGB, dx, dy,
        0.toByte, primaryType, AttackSlot.BURST))
    }
  }

  /** Cast the ability in slot 0 (Q) or 1 (E) now, toward the aim, if it is ready ([[abilityReady]]);
    * whether it was cast. The input handlers have it aimed first ([[pressAbility]], [[pressPrimary]]). */
  def shootAbility(slot: Int): Boolean = {
    if (!abilityReady(slot)) return false
    val abilityDef = abilityIn(slot)
    val (lastAbilityTime, attackSlot) =
      if (slot == 0) (lastQAbilityTime, AttackSlot.Q) else (lastEAbilityTime, AttackSlot.E)
    val now = System.currentTimeMillis()

    // A trap has to have somewhere to land. With nowhere — aimed into a wall from inside one —
    // nothing is cast and the cooldown is not spent, so the cell is picked before anything else
    // happens ([[trapLanding]]).
    val trapCell = abilityDef.castBehavior match {
      case TrapCast(_, range) =>
        val cell = trapLanding(range)
        if (cell.isEmpty) return false
        cell
      case _ => None
    }

    lastAbilityTime.set(now)

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
          requestSpawn(ProjectilePacket.spawnRequest(
            sequenceNumber.getAndIncrement(), localPlayerId,
            pos.getX.toFloat, pos.getY.toFloat, localColorRGB, rdx, rdy,
            0.toByte, abilityDef.projectileType, attackSlot))
        }

      case GroundSlam(_) =>
        val pos = localPosition.get()
        requestSpawn(ProjectilePacket.spawnRequest(
          sequenceNumber.getAndIncrement(), localPlayerId,
          pos.getX.toFloat, pos.getY.toFloat, localColorRGB, 0.0f, 0.0f,
          0.toByte, abilityDef.projectileType, attackSlot))
        // A slam that speeds its caster up does it as it is cast (the server's applyCastSelfBuff):
        // we walk at its pace from now rather than a round trip later
        ProjectileDef.get(abilityDef.projectileType).onHitEffects.foreach {
          case SpeedBoost(durationMs) => hasteCast(durationMs)
          case _ =>
        }

      case TrapCast(trapType, _) =>
        val cell = trapCell.get
        AudioManager.playTrapPlace()
        send(new TrapPacket(sequenceNumber.getAndIncrement(), localPlayerId,
          cell.getX, cell.getY, 0, TrapAction.PLACE, trapType, localTeamId, attackSlot, null))

      case StandardProjectile =>
        val pos = localPosition.get()
        val (ndx, ndy) = getAimDirection
        requestSpawn(ProjectilePacket.spawnRequest(
          sequenceNumber.getAndIncrement(), localPlayerId,
          pos.getX.toFloat, pos.getY.toFloat, localColorRGB, ndx, ndy,
          0.toByte, abilityDef.projectileType, attackSlot))
    }
    true
  }

  // ── Cooldowns ───────────────────────────────────────────────────────────

  def getLastCastTime: Long = lastCastTime.get()
  def getLastCastDirX: Float = lastCastDirX
  def getLastCastDirY: Float = lastCastDirY

  /** A new life: every attack ready, nothing charging, held or aimed, and nothing waiting on the
    * server. A charge held as the last match ended — its window gone before the button came up —
    * went on into the next at full strength, walking at a charge's pace. */
  private[game] def resetAttacks(): Unit = {
    lastQAbilityTime.set(0)
    lastEAbilityTime.set(0)
    // A refusal of an R pressed in the last life puts nothing back into this one (R itself runs on)
    qBeforeReset.set(NOT_HELD)
    eBeforeReset.set(NOT_HELD)
    lastPrimaryShot.set(NOT_HELD)
    isCharging = false
    // Nor an attack held or aimed as we died: the first click after the respawn would have set it off
    letGoOfAll()
    pendingSpawns.dropWaiting(nanoClock())
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

  // ── R: every ability ready again ────────────────────────────────────────

  /**
   * R went down (design/characters.md, *R resets the cooldowns*): both abilities are ready again at
   * once, if R's own minute has run ([[resetReady]]) and one of them is cooling down. With nothing to
   * reset it does nothing and spends nothing. Whatever is aimed stays aimed. The server is asked, and
   * judges it as it would a cast: a refusal puts the cooldowns back as they were
   * ([[handleCooldownReset]]).
   */
  def pressReset(): Unit = {
    if (!resetReady) return
    val charDef = getSelectedCharacterDef
    if (abilityCooledDown(0, charDef.qAbility) && abilityCooledDown(1, charDef.eAbility)) return
    qBeforeReset.set(lastQAbilityTime.get())
    eBeforeReset.set(lastEAbilityTime.get())
    lastQAbilityTime.set(0)
    lastEAbilityTime.set(0)
    lastCooldownReset.set(clockMark())
    resetAsked.set(true)
    send(new CooldownResetPacket(sequenceNumber.getAndIncrement(), localPlayerId, CooldownResetAction.REQUEST, 0))
    AudioManager.playCooldownReset()
  }

  /** Would R reset the cooldowns now? Not while we are dead, held, phased or in a ceasefire, when no
    * ability could be cast either, nor before its own minute has run: from the match's start, then
    * from the last press. Whether there is anything to reset is [[pressReset]]'s to see. */
  def resetReady: Boolean = !isDead && !isFrozen && !isPhased && !attacksLocked && resetCooledDown

  /** How much of R's own cooldown is still to run, 1 (just pressed, or the match just begun) to 0. */
  def getResetCooldownFraction: Float = {
    val left = resetCooldownLeftMs
    if (left <= 0L) 0f else left.toFloat / Constants.COOLDOWN_RESET_MS
  }

  /** R's own cooldown still to run, in seconds; 0 once it can be pressed. */
  def getResetCooldownRemaining: Float = resetCooldownLeftMs / 1000f

  private def resetCooledDown: Boolean = resetCooldownLeftMs <= 0L

  private def resetCooldownLeftMs: Long = {
    val last = lastCooldownReset.get()
    if (last == NOT_HELD) 0L
    else Math.min(Constants.COOLDOWN_RESET_MS.toLong,
      Math.max(0L, Constants.COOLDOWN_RESET_MS - (nanoClock() - last) / 1000000L))
  }

  /** The match has begun (or a new one has): R's first minute runs from now, so nobody has it before
    * the fighting has gone on a while. Practice, where a player goes to try a character out, has it
    * ready from the start, as the server does (GameInstance.firstResetFrom). */
  private[client] def startResetCooldown(): Unit = {
    lastCooldownReset.set(if (isPracticeMode) NOT_HELD else clockMark())
    resetAsked.set(false)
    qBeforeReset.set(NOT_HELD)
    eBeforeReset.set(NOT_HELD)
  }

  /**
   * The server refused our R (CooldownResetPacket REJECTED): the cooldowns go back as the server has
   * them. Q and E are put back as they were before it, but for a cast since that the server took
   * anyway (one its own clock would have let through without the reset). R itself is ready when the
   * server says its cooldown runs out, or, refused for anything else (we were held, or had just died),
   * in a moment, as a refused trap is. A refusal of an R from the last match is nothing to this one.
   */
  private[game] def handleCooldownReset(packet: CooldownResetPacket): Unit = {
    if (packet.getAction != CooldownResetAction.REJECTED || !packet.getPlayerId.equals(localPlayerId)) return
    if (!resetAsked.getAndSet(false)) return
    val left = if (packet.getMsLeft > 0) Math.min(packet.getMsLeft, Constants.COOLDOWN_RESET_MS) else REFUND_RETRY_MS
    lastCooldownReset.set(clockMark() - (Constants.COOLDOWN_RESET_MS - left) * 1000000L)
    val charDef = getSelectedCharacterDef
    putBack(lastQAbilityTime, qBeforeReset.getAndSet(NOT_HELD), charDef.qAbility.cooldownMs)
    putBack(lastEAbilityTime, eBeforeReset.getAndSet(NOT_HELD), charDef.eAbility.cooldownMs)
    println(s"GameClient: Cooldown reset refused (${packet.getMsLeft}ms left on it)")
  }

  /** An ability's cast as it was before a refused reset: unless it has been cast since and the server
    * would have taken that cast anyway (80% of the cooldown, as it judges one), when the new one stands. */
  private def putBack(lastCast: AtomicLong, before: Long, cooldownMs: Int): Unit = {
    if (before == NOT_HELD) return
    val now = lastCast.get()
    if (now == 0L || now - before < (cooldownMs * 0.8).toLong) lastCast.set(before)
  }

  /** Now on the client's clock, never mistaken for NOT_HELD. */
  private def clockMark(): Long = {
    val now = nanoClock()
    if (now == NOT_HELD) now + 1 else now
  }
}

object Attacks {
  /** The attacks that can be aimed, by AttackSlot: PRIMARY, Q and E. */
  private val HELD_SLOTS = 3

  /** Not aimed (heldSince), never fired (lastPrimaryShot). */
  private val NOT_HELD = Long.MinValue

  /**
   * Does this ability go off as its key goes down, rather than being aimed and cast with a click?
   * Only one with nothing to aim: a slam, which reaches all round us whichever way we aim; a phase,
   * which goes nowhere; a barrier, which turns with the aim the whole time it is up, so is aimed after
   * it goes up; and a fan that goes all the way round. Everything else goes somewhere (a shot, a fan, a
   * dash, a blink, a trap thrown), and is aimed first, so where it will go can be shown.
   */
  def goesOffOnPress(ability: AbilityDef): Boolean = ability.castBehavior match {
    case _: GroundSlam | _: PhaseShiftBuff | _: BarrierCast => true
    case fan: FanProjectile => fan.isFullCircle
    case _ => false
  }
}
