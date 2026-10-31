package com.gridgame.client.game

import com.gridgame.common.Constants
import com.gridgame.common.model._

import java.util.concurrent.atomic.AtomicLong

/**
 * How we move: a step at a time as the input handlers ask ([[movePlayer]], at the pace
 * [[moveStepIntervalMs]] sets), a dash along a line, a blink, a star's jump, and where we are drawn
 * between two steps ([[updateVisualPosition]]). Every move we make is ours to make and the
 * server's to check: each goes to it at once (LocalPlayer.sendPositionUpdate).
 */
trait LocalMovement { this: GameClient =>
  private[game] val movementBlockedUntil: AtomicLong = new AtomicLong(0)
  private val lastMoveTime: AtomicLong = new AtomicLong(0)
  @volatile private var movementInputActive = false

  // A dash in progress (DashBuff, the Raptor's Swoop first among them): from where to where, and
  // over which moments
  private[game] val swoopingUntil: AtomicLong = new AtomicLong(0)
  @volatile private var swoopTargetX: Float = 0f
  @volatile private var swoopTargetY: Float = 0f
  @volatile private var swoopStartX: Float = 0f
  @volatile private var swoopStartY: Float = 0f
  private val swoopStartTime: AtomicLong = new AtomicLong(0)

  // Movement interpolation for smooth camera following
  @volatile private var moveInterpFromX: Double = 0.0
  @volatile private var moveInterpFromY: Double = 0.0
  @volatile private var moveInterpToX: Double = 0.0
  @volatile private var moveInterpToY: Double = 0.0
  @volatile private var moveInterpStartTime: Long = 0L
  @volatile private var moveInterpDurationMs: Long = Constants.MOVE_RATE_LIMIT_MS
  @volatile private var prevMoveTimestamp: Long = 0L

  def movePlayer(dx: Int, dy: Int): Unit = {
    // Check if frozen — still send position so server echoes frozen state back
    if (isFrozen || isRooted) {
      localDirection.set(Direction.fromMovement(dx, dy))
      sendPositionUpdate(localPosition.get())
      return
    }

    // Check if movement is blocked from burst shot
    if (System.currentTimeMillis() < movementBlockedUntil.get()) {
      return
    }

    val world = currentWorld.get()
    val current = localPosition.get()

    // Update direction based on movement attempt (even if blocked)
    localDirection.set(Direction.fromMovement(dx, dy))

    var finalX = current.getX
    var finalY = current.getY

    val targetX = Math.max(0, Math.min(world.width - 1, current.getX + dx))
    val targetY = Math.max(0, Math.min(world.height - 1, current.getY + dy))

    // A wall stops a walk, a phased one too, and its corner: a diagonal step goes between open cells
    // only (WorldData.canStep), as the server and the bots step. The opening divider is a hole in
    // the world's walkability while it is up (TeamDivider), so it stops a step as a wall does
    def canStep(tx: Int, ty: Int): Boolean =
      world.canStep(current.getX, current.getY, tx - current.getX, ty - current.getY)

    if (canStep(targetX, targetY)) {
      finalX = targetX
      finalY = targetY
    } else if (dx != 0 && dy != 0) {
      // Diagonal blocked, or past a wall's corner — slide along whichever axis is open
      if (canStep(targetX, current.getY)) {
        finalX = targetX
      } else if (canStep(current.getX, targetY)) {
        finalY = targetY
      }
    }

    val newPos = new Position(finalX, finalY)

    if (!newPos.equals(current)) {
      // Set up movement interpolation for smooth rendering
      val now = System.currentTimeMillis()
      moveInterpFromX = current.getX.toDouble
      moveInterpFromY = current.getY.toDouble
      moveInterpToX = finalX.toDouble
      moveInterpToY = finalY.toDouble
      // We glide to the new cell over the time until the next step: the gap since the last one,
      // which is the pace while a key is held. A step after standing still has no such gap, and
      // glides over the step's own interval (twice it for a pure left or right, as the input
      // handlers wait twice as long for one). It used to glide over the whole time stood still: a
      // tap after a few seconds slid one cell over those seconds, the camera with it.
      val interval = moveStepIntervalMs.toLong * (if (dx != 0 && dy != 0 && dx != dy) 2 else 1)
      val gap = now - prevMoveTimestamp
      // A gap of two intervals is a fresh start, as it is to the input handlers (Movement.nextStepFrom)
      moveInterpDurationMs = if (prevMoveTimestamp == 0L || gap >= 2 * interval) interval else Math.max(16L, gap)
      prevMoveTimestamp = now
      moveInterpStartTime = now

      localPosition.set(newPos)
      lastMoveTime.set(now)
      sendPositionUpdate(newPos)
    }
  }

  /** How long this player waits between steps in their current state — the rule both input
    * handlers move by, and the server checks against (Movement). */
  def moveStepIntervalMs: Int = Movement.stepIntervalMs(
    getSelectedCharacterDef.moveSpeed, isCharging, getChargeLevel, isPhased, hasSpeedBoost,
    isSlowed, slowMultiplier)

  def isMovementBlocked: Boolean = {
    System.currentTimeMillis() < movementBlockedUntil.get()
  }

  /** A new life stands where it was put: no dash under way (one the last match ended in finished
    * on the first frame of the next, and put us at its end), no burst's standstill, and no step or
    * knockback of the last life's to be drawn moving along. */
  private[game] def stopMoving(): Unit = {
    swoopingUntil.set(0)
    movementBlockedUntil.set(0)
    moveInterpStartTime = 0L
    moveInterpDurationMs = Constants.MOVE_RATE_LIMIT_MS
    prevMoveTimestamp = 0L
    knockSliding = false
    drawnYet = false
  }

  /** The server has put us at `pos` (a server move we hadn't taken: LocalPlayer.handleOwnUpdate),
    * by a knockback or otherwise. Told together, so no frame sees the one without the other. */
  private[game] def placedByServer(pos: Position, knockedBack: Boolean): Unit = placedLock.synchronized {
    localPosition.set(pos)
    if (knockedBack) knocksHeard += 1
  }

  def setMovementInputActive(active: Boolean): Unit = { movementInputActive = active }

  def getIsMoving: Boolean = movementInputActive || System.currentTimeMillis() - lastMoveTime.get() < 200

  // ── Dashes ──────────────────────────────────────────────────────────────

  /** A dash toward the cursor, phased while it lasts: to where [[dashLanding]] says, over
    * `durationMs`. [[tickSwoop]] carries us along it. */
  private[game] def dash(maxDistance: Int, durationMs: Int, now: Long): Unit = {
    val pos = localPosition.get()
    val landing = dashLanding(pos, maxDistance)
    if (landing != null) {
      swoopStartX = pos.getX.toFloat
      swoopStartY = pos.getY.toFloat
      swoopTargetX = landing.getX.toFloat
      swoopTargetY = landing.getY.toFloat
      swoopStartTime.set(now)
      swoopingUntil.set(now + durationMs)
      phasedUntil.set(now + durationMs)
      sendPositionUpdate(localPosition.get())
    }
  }

  /**
   * Where a dash toward the cursor would end: the farthest open cell along the line to it, at most
   * `maxDistance` cells, passing over whatever lies between (a dash goes over walls, as a phase
   * shift no longer does: design/characters.md, *A phase stops at walls*); where we are if
   * nothing along it is open. Null when the cursor is within half a cell of us, which goes nowhere.
   * The dash and its aim (AimArrowPainter) both ask this, so the aim shows where it ends.
   */
  private[client] def dashLanding(maxDistance: Int): Position = dashLanding(localPosition.get(), maxDistance)

  private def dashLanding(pos: Position, maxDistance: Int): Position = {
    val dx = (mouseWorldX - pos.getX).toFloat
    val dy = (mouseWorldY - pos.getY).toFloat
    val dist = Math.sqrt(dx * dx + dy * dy).toFloat
    val clampedDist = Math.min(dist, maxDistance.toFloat)
    if (clampedDist <= 0.5f) return null
    val ndx = dx / dist
    val ndy = dy / dist
    val startX = pos.getX.toFloat
    val startY = pos.getY.toFloat
    val world = currentWorld.get()
    // Not over the opening divider: the dash takes the farthest open cell along its line,
    // the far side of the wall is open ground, and the server refuses every step there
    val divider = world.divider
    var bestX = pos.getX
    var bestY = pos.getY
    var step = 1
    while (step <= clampedDist.toInt) {
      val testX = Math.max(0, Math.min(world.width - 1, (startX + ndx * step).toInt))
      val testY = Math.max(0, Math.min(world.height - 1, (startY + ndy * step).toInt))
      if (world.isWalkable(testX, testY) &&
          (divider == null || !divider.stops(pos.getX, pos.getY, testX, testY))) {
        bestX = testX
        bestY = testY
      }
      step += 1
    }
    new Position(bestX, bestY)
  }

  def isSwooping: Boolean = swoopingUntil.get() > 0

  def getSwoopProgress: Double = {
    val now = System.currentTimeMillis()
    val start = swoopStartTime.get()
    val end = swoopingUntil.get()
    if (now >= end || end <= start) return 1.0
    (now - start).toDouble / (end - start).toDouble
  }

  def getSwoopStartX: Float = swoopStartX
  def getSwoopStartY: Float = swoopStartY
  def getSwoopTargetX: Float = swoopTargetX
  def getSwoopTargetY: Float = swoopTargetY

  /** Carry a dash on to where it has got to by now, and end it when it is over. Called every frame
    * by the input handlers while one lasts. */
  def tickSwoop(): Unit = {
    val now = System.currentTimeMillis()
    val swoopEnd = swoopingUntil.get()
    if (swoopEnd == 0) return

    if (now >= swoopEnd) {
      // Dash has ended — snap to exact target position and send final update
      swoopingUntil.set(0)
      val finalPos = new Position(Math.round(swoopTargetX).toInt, Math.round(swoopTargetY).toInt)
      localPosition.set(finalPos)
      // Send twice for UDP redundancy (dash end position is critical)
      sendPositionUpdate(finalPos)
      sendPositionUpdate(finalPos)
      return
    }

    val elapsed = now - swoopStartTime.get()
    val duration = (swoopEnd - swoopStartTime.get()).toFloat
    val t = Math.min(1.0f, elapsed / duration)

    val newX = swoopStartX + (swoopTargetX - swoopStartX) * t
    val newY = swoopStartY + (swoopTargetY - swoopStartY) * t

    val posX = Math.round(newX).toInt
    val posY = Math.round(newY).toInt
    val newPos = new Position(posX, posY)
    localPosition.set(newPos)
    sendPositionUpdate(newPos)

    // Update direction based on swoop direction
    val dx = (swoopTargetX - swoopStartX).toInt
    val dy = (swoopTargetY - swoopStartY).toInt
    if (dx != 0 || dy != 0) {
      localDirection.set(Direction.fromMovement(
        Math.max(-1, Math.min(1, dx)),
        Math.max(-1, Math.min(1, dy))
      ))
    }
  }

  // ── Blinks and stars ────────────────────────────────────────────────────

  /** Star: show the teleport now. The item packet already sent is what moves us on the server,
    * which tells everyone else, so there's no position update to send. */
  private[game] def teleportTo(target: Position): Unit = {
    val oldPos = localPosition.get()
    localPosition.set(target)
    knockSliding = false
    val now = System.currentTimeMillis()
    lastMoveTime.set(now)

    // Record teleport animation
    teleportAnimations.put(localPlayerId, Array(
      now,
      oldPos.getX.toLong, oldPos.getY.toLong,
      target.getX.toLong, target.getY.toLong,
      localColorRGB.toLong
    ))

    println(s"GameClient: Teleported to (${target.getX}, ${target.getY})")
  }

  private[game] def performBlink(maxDistance: Int): Unit = {
    val oldPos = localPosition.get()
    val dest = blinkLanding(oldPos, maxDistance)
    blinkTo(oldPos, dest.getX, dest.getY)
  }

  /**
   * Where a blink would land: its whole range toward the aim however near the cursor is (the way we
   * face with the cursor on us), walked a cell at a time and stopping before the first cell that
   * can't be stood on (Teleport.blinkTarget); where we are if the first one can't. The blink and its
   * aim (AimArrowPainter) both ask this, so the aim shows where it lands.
   */
  private[client] def blinkLanding(maxDistance: Int): Position = blinkLanding(localPosition.get(), maxDistance)

  private def blinkLanding(pos: Position, maxDistance: Int): Position = {
    val dx = (mouseWorldX - pos.getX).toFloat
    val dy = (mouseWorldY - pos.getY).toFloat
    val len = Math.sqrt(dx * dx + dy * dy).toFloat
    var ndx = 0f
    var ndy = 0f
    if (len > 0.01f) { ndx = dx / len; ndy = dy / len }
    else localDirection.get() match {
      case Direction.Up    => ndy = -1f
      case Direction.Down  => ndy = 1f
      case Direction.Left  => ndx = -1f
      case Direction.Right => ndx = 1f
    }
    Teleport.blinkTarget(currentWorld.get(), pos.getX, pos.getY, ndx, ndy, maxDistance)
  }

  private def blinkTo(oldPos: Position, x: Int, y: Int): Unit = {
    if (x == oldPos.getX && y == oldPos.getY) return

    val newPos = new Position(x, y)
    localPosition.set(newPos)
    // Gone from wherever a knockback was still sliding us
    knockSliding = false
    val now = System.currentTimeMillis()
    lastMoveTime.set(now)
    // Send twice for UDP redundancy (blink is a single critical event)
    sendPositionUpdate(newPos)
    sendPositionUpdate(newPos)

    // Record teleport animation
    teleportAnimations.put(localPlayerId, Array(
      now,
      oldPos.getX.toLong, oldPos.getY.toLong,
      x.toLong, y.toLong,
      localColorRGB.toLong
    ))

    println(s"GameClient: Blinked to ($x, $y)")
  }

  // ── Where we are drawn ──────────────────────────────────────────────────

  /** Smoothly interpolated position for rendering. Results stored in visualPosX/visualPosY to avoid tuple allocation. */
  private var _visualPosX: Double = 0.0
  private var _visualPosY: Double = 0.0
  def visualPosX: Double = _visualPosX
  def visualPosY: Double = _visualPosY

  // Knockbacks the server has put us somewhere by (placedByServer, the packet thread), and how
  // many of them the render thread has started sliding us for
  private val placedLock = new Object
  private var knocksHeard = 0
  private var knocksDrawn = 0
  // The slide under way (render thread): when it began on the client's clock (nanoClock), how
  // long it takes, and how far from where the knockback put us it started (KnockbackSlide)
  private var knockSliding = false
  private var knockAtNanos = 0L
  private var knockMs = 0.0
  private var knockFromX = 0.0
  private var knockFromY = 0.0
  // Whether visualPosX/Y says where we were drawn yet this life, for a slide to start from
  private var drawnYet = false
  // A replay of a match's recording (devtools ReportReplay) says where we stood and were drawn, frame
  // by frame, rather than the client working it out from steps it never takes
  @volatile private var replayed = false

  /** Where a recording says we stood and were drawn this frame: a replay's, never the game's. A new cell
    * turns us the way we stepped and has us walking, as a step of our own would (movePlayer). */
  private[client] def replayLocal(cellX: Int, cellY: Int, drawnX: Double, drawnY: Double): Unit = {
    val was = localPosition.get()
    if (was != null && (was.getX != cellX || was.getY != cellY)) {
      localDirection.set(Direction.fromMovement(Integer.signum(cellX - was.getX), Integer.signum(cellY - was.getY)))
      lastMoveTime.set(System.currentTimeMillis())
    }
    localPosition.set(new Position(cellX, cellY))
    _visualPosX = drawnX
    _visualPosY = drawnY
    replayed = true
  }

  /** Call once per frame to update visualPosX/visualPosY: where we are, or between it and the cell
    * we are stepping from, and sliding in from where a knockback caught us. */
  def updateVisualPosition(): Unit = {
    if (replayed) return
    var pos: Position = null
    var knocks = 0
    placedLock.synchronized {
      pos = localPosition.get()
      knocks = knocksHeard
    }
    val nowNanos = nanoClock()
    if (knocks != knocksDrawn) {
      knocksDrawn = knocks
      // From where we were drawn to where it put us, slid there rather than jumped, and the step
      // we were drawn taking is over
      if (drawnYet) {
        knockFromX = _visualPosX - pos.getX
        knockFromY = _visualPosY - pos.getY
        knockMs = KnockbackSlide.durationMs(Math.hypot(knockFromX, knockFromY))
        knockAtNanos = nowNanos
        knockSliding = true
      }
      moveInterpStartTime = 0L
    }
    drawnYet = true

    val elapsed = System.currentTimeMillis() - moveInterpStartTime
    if (moveInterpStartTime == 0L || elapsed >= moveInterpDurationMs) {
      _visualPosX = pos.getX.toDouble
      _visualPosY = pos.getY.toDouble
    } else {
      val t = elapsed.toDouble / moveInterpDurationMs
      _visualPosX = moveInterpFromX + (moveInterpToX - moveInterpFromX) * t
      _visualPosY = moveInterpFromY + (moveInterpToY - moveInterpFromY) * t
    }

    if (knockSliding) {
      val left = KnockbackSlide.remaining((nowNanos - knockAtNanos) / 1e6, knockMs)
      if (left <= 0.0) knockSliding = false
      else {
        _visualPosX += knockFromX * left
        _visualPosY += knockFromY * left
      }
    }
  }
}
