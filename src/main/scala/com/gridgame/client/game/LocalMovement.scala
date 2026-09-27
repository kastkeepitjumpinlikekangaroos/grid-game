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

    // The opening divider stops a phase as surely as a wall stops a walk, and neither can be
    // crossed: the server refuses a step over it, so taking one here would only rubber-band us
    val divider = world.divider
    def canStand(tx: Int, ty: Int): Boolean =
      (isPhased || world.isWalkable(tx, ty)) &&
        (divider == null || !divider.stops(current.getX, current.getY, tx, ty))

    if (canStand(targetX, targetY)) {
      finalX = targetX
      finalY = targetY
    } else if (dx != 0 && dy != 0) {
      // Diagonal blocked — try sliding along each axis
      if (canStand(targetX, current.getY)) {
        finalX = targetX
      } else if (canStand(current.getX, targetY)) {
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
      if (prevMoveTimestamp > 0) {
        moveInterpDurationMs = Math.max(16, now - prevMoveTimestamp)
      }
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
    * on the first frame of the next, and put us at its end), no burst's standstill, and no step of
    * the last life's to be drawn walking along. */
  private[game] def stopMoving(): Unit = {
    swoopingUntil.set(0)
    movementBlockedUntil.set(0)
    moveInterpStartTime = 0L
    moveInterpDurationMs = Constants.MOVE_RATE_LIMIT_MS
    prevMoveTimestamp = 0L
  }

  def setMovementInputActive(active: Boolean): Unit = { movementInputActive = active }

  def getIsMoving: Boolean = movementInputActive || System.currentTimeMillis() - lastMoveTime.get() < 200

  // ── Dashes ──────────────────────────────────────────────────────────────

  /** A dash toward the cursor, phased while it lasts: to the farthest open cell along the line,
    * at most `maxDistance` cells, over `durationMs`. [[tickSwoop]] carries us along it. */
  private[game] def dash(maxDistance: Int, durationMs: Int, now: Long): Unit = {
    val pos = localPosition.get()
    val dx = (mouseWorldX - pos.getX).toFloat
    val dy = (mouseWorldY - pos.getY).toFloat
    val dist = Math.sqrt(dx * dx + dy * dy).toFloat
    val clampedDist = Math.min(dist, maxDistance.toFloat)
    if (clampedDist > 0.5f) {
      val ndx = dx / dist
      val ndy = dy / dist
      swoopStartX = pos.getX.toFloat
      swoopStartY = pos.getY.toFloat
      val world = currentWorld.get()
      // Not over the opening divider: the dash takes the farthest open cell along its line,
      // the far side of the wall is open ground, and the server refuses every step there
      val divider = world.divider
      var bestX = swoopStartX
      var bestY = swoopStartY
      for (step <- 1 to clampedDist.toInt) {
        val testX = Math.max(0, Math.min(world.width - 1, (swoopStartX + ndx * step).toInt))
        val testY = Math.max(0, Math.min(world.height - 1, (swoopStartY + ndy * step).toInt))
        if (world.isWalkable(testX, testY) &&
            (divider == null || !divider.stops(pos.getX, pos.getY, testX, testY))) {
          bestX = testX.toFloat
          bestY = testY.toFloat
        }
      }
      swoopTargetX = bestX
      swoopTargetY = bestY
      swoopStartTime.set(now)
      swoopingUntil.set(now + durationMs)
      phasedUntil.set(now + durationMs)
      sendPositionUpdate(localPosition.get())
    }
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
    val dx = (mouseWorldX - oldPos.getX).toFloat
    val dy = (mouseWorldY - oldPos.getY).toFloat
    val len = Math.sqrt(dx * dx + dy * dy).toFloat
    val (ndx, ndy) = if (len > 0.01f) (dx / len, dy / len) else {
      val dir = localDirection.get()
      dir match {
        case Direction.Up    => (0.0f, -1.0f)
        case Direction.Down  => (0.0f, 1.0f)
        case Direction.Left  => (-1.0f, 0.0f)
        case Direction.Right => (1.0f, 0.0f)
      }
    }

    // Walk cell-by-cell up to the blink's range, stopping before the first non-walkable cell
    val dest = Teleport.blinkTarget(currentWorld.get(), oldPos.getX, oldPos.getY, ndx, ndy, maxDistance)
    blinkTo(oldPos, dest.getX, dest.getY)
  }

  private def blinkTo(oldPos: Position, x: Int, y: Int): Unit = {
    if (x == oldPos.getX && y == oldPos.getY) return

    val newPos = new Position(x, y)
    localPosition.set(newPos)
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

  /** Call once per frame to update visualPosX/visualPosY. */
  def updateVisualPosition(): Unit = {
    val pos = localPosition.get()
    if (moveInterpStartTime == 0L) {
      _visualPosX = pos.getX.toDouble
      _visualPosY = pos.getY.toDouble
      return
    }
    val now = System.currentTimeMillis()
    val elapsed = now - moveInterpStartTime
    if (elapsed >= moveInterpDurationMs) {
      _visualPosX = pos.getX.toDouble
      _visualPosY = pos.getY.toDouble
    } else {
      val t = elapsed.toDouble / moveInterpDurationMs
      _visualPosX = moveInterpFromX + (moveInterpToX - moveInterpFromX) * t
      _visualPosY = moveInterpFromY + (moveInterpToY - moveInterpFromY) * t
    }
  }
}
