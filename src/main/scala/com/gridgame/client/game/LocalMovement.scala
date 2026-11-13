package com.gridgame.client.game

import com.gridgame.common.model._

import java.util.concurrent.atomic.AtomicLong

/**
 * How we move: a step at a time as the input handlers ask ([[movePlayer]], at the pace
 * [[moveStepIntervalMs]] sets), a dash along a line, a blink, a star's jump, and where we are drawn
 * between two steps ([[updateVisualPosition]]). Every move we make is ours to make and the
 * server's to check: each goes to it at once (LocalPlayer.sendPositionUpdate). Steps, their glide
 * and a dash are timed on the frames' clock ([[frameNanos]]), the moment each frame is shown.
 */
trait LocalMovement { this: GameClient =>
  private[game] val movementBlockedUntil: AtomicLong = new AtomicLong(0)
  private val lastMoveTime: AtomicLong = new AtomicLong(0)
  @volatile private var movementInputActive = false

  // A dash in progress (DashBuff, the Raptor's Swoop first among them): from where to where, and
  // over which moments of the frames' clock (frameNanos). Ended by its own end, a server move, a death
  @volatile private[game] var dashing = false
  @volatile private var swoopTargetX: Float = 0f
  @volatile private var swoopTargetY: Float = 0f
  @volatile private var swoopStartX: Float = 0f
  @volatile private var swoopStartY: Float = 0f
  @volatile private var dashFromNanos = 0L
  @volatile private var dashNanos = 1L

  // The step we are drawn taking: from where to which cell, from the moment it fell due and over
  // its own interval, on the frames' clock (movePlayer); glideNanos 0 when none is under way
  @volatile private var glideFromX: Double = 0.0
  @volatile private var glideFromY: Double = 0.0
  @volatile private var glideToX: Double = 0.0
  @volatile private var glideToY: Double = 0.0
  @volatile private var glideAtNanos: Long = 0L
  @volatile private var glideNanos: Long = 0L
  // How many steps' worth the glide covers: one, or more when it took up the rest of the last
  @volatile private var glideSteps: Double = 1.0

  // The time of the frame being drawn, on the display's cadence (MatchWindow's FramePacer), once a
  // window has said so (beginFrame)
  @volatile private var pacedNanos = 0L
  @volatile private var paced = false

  /** A frame is begun, to be shown at `nanos` (the match window's FramePacer, on the display's
    * cadence): what our steps, their glide and a dash are timed on, and everything the renderer draws
    * moving (render/CLAUDE.md, *Everything moving is placed at the frame's time*). */
  def beginFrame(nanos: Long): Unit = { pacedNanos = nanos; paced = true }

  /** The time of the frame being drawn: the one beginFrame gave, or the client's own clock
    * (nanoClock) for a client no window paces (a test, a dev tool). */
  def frameNanos: Long = if (paced) pacedNanos else nanoClock()

  /** A step as a player takes one, now: a test's, or the controller's or keyboard's without a pace of
    * their own (theirs say when it fell due: the other movePlayer). It glides over the step's own
    * interval (twice it for a pure left or right, as the input handlers wait twice as long for one). */
  def movePlayer(dx: Int, dy: Int): Unit = {
    val horizontal = dx != 0 && dy != 0 && dx != dy
    movePlayer(dx, dy, frameNanos, moveStepIntervalMs * 1000000L * (if (horizontal) 2 else 1))
  }

  /**
   * A step by (dx, dy), which fell due at `dueNanos` on the frames' clock (frameNanos), the next
   * `intervalNanos` after it. We are drawn gliding to the new cell from that moment over that
   * interval, so a walk is drawn at its pace however its steps fall on the frames.
   *
   * A step is only taken on a frame, the first after it falls due: at 60 fps a 53ms step comes three
   * frames after the last and now and then four, and a 50ms one on every third only while the
   * frames keep exact time. Glided from the frame that took it over the gap since the last, a step
   * taken a frame late cut the last glide short (the next was due sooner: Movement.nextStepFrom)
   * and one taken a frame early let it run out and stand still: walking as the Ghoul, the sprite
   * stood still in 4% of frames and moved up to 1.68 times its pace in others, the camera, and the
   * world under us with it, 0.6 to 1.2 times. From when each fell due, the glides follow on from one
   * another exactly (render/CLAUDE.md, *Everything moving is placed at the frame's time*).
   */
  def movePlayer(dx: Int, dy: Int, dueNanos: Long, intervalNanos: Long): Unit = {
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
      val now = System.currentTimeMillis()
      // Glided from where the last step's glide has us when this one fell due: the cell we step
      // from, as it ends there then when we walk at a steady pace. A step that comes sooner, the pace
      // having quickened (a slow or a charge let go, a boost begun), takes up what is left of the last
      // glide and goes on at the new pace: finished within the new step's interval, as it was, the
      // rest of a 167ms step and the next were drawn in 50ms, a lurch to three times the pace.
      var fromX = current.getX.toDouble
      var fromY = current.getY.toDouble
      var steps = 1.0
      if (glideNanos > 0L && glideToX == current.getX && glideToY == current.getY) {
        val f = (dueNanos - glideAtNanos).toDouble / glideNanos
        if (f < 1.0) {
          val g = Math.max(0.0, f)
          fromX = glideFromX + (glideToX - glideFromX) * g
          fromY = glideFromY + (glideToY - glideFromY) * g
          steps += (1.0 - g) * glideSteps
        }
      }
      glideFromX = fromX
      glideFromY = fromY
      glideToX = finalX.toDouble
      glideToY = finalY.toDouble
      glideAtNanos = dueNanos
      glideSteps = steps
      glideNanos = Math.max(1L, (intervalNanos * steps).toLong)

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
    dashing = false
    movementBlockedUntil.set(0)
    glideNanos = 0L
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
      dashFromNanos = frameNanos
      dashNanos = Math.max(1L, durationMs * 1000000L)
      dashing = true
      // Off from the cell we dash from, not from the step we were drawn taking
      glideNanos = 0L
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

  def isSwooping: Boolean = dashing

  /** How far through the dash under way the frame being drawn is, 0 to 1. */
  def getSwoopProgress: Double =
    Math.max(0.0, Math.min(1.0, (frameNanos - dashFromNanos).toDouble / dashNanos))

  def getSwoopStartX: Float = swoopStartX
  def getSwoopStartY: Float = swoopStartY
  def getSwoopTargetX: Float = swoopTargetX
  def getSwoopTargetY: Float = swoopTargetY

  /** Carry a dash on to where it has got to by the frame being drawn, and end it when it is over.
    * Called every frame by the input handlers while one lasts. We stand on the cell it has reached
    * and are drawn along its line (updateVisualPosition). */
  def tickSwoop(): Unit = {
    if (!dashing) return

    if (frameNanos - dashFromNanos >= dashNanos) {
      // Dash has ended — snap to exact target position and send final update
      dashing = false
      val finalPos = new Position(Math.round(swoopTargetX).toInt, Math.round(swoopTargetY).toInt)
      localPosition.set(finalPos)
      // Send twice for UDP redundancy (dash end position is critical)
      sendPositionUpdate(finalPos)
      sendPositionUpdate(finalPos)
      return
    }

    val t = getSwoopProgress.toFloat

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
    // Drawn there at once (the teleport's flash), not finishing a step's glide on the way
    knockSliding = false
    glideNanos = 0L
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
    // Gone from wherever a knockback was still sliding us, or a step was gliding us
    knockSliding = false
    glideNanos = 0L
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
    val nowNanos = frameNanos
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
      glideNanos = 0L
    }
    drawnYet = true

    if (dashing) {
      // Along the dash's line as far as the frame has got, not on the cell it has reached: the
      // cells come a whole one every other frame or so, which drew the dash in jumps
      val t = getSwoopProgress
      _visualPosX = swoopStartX + (swoopTargetX - swoopStartX) * t
      _visualPosY = swoopStartY + (swoopTargetY - swoopStartY) * t
    } else {
      val elapsed = nowNanos - glideAtNanos
      if (glideNanos == 0L || elapsed >= glideNanos) {
        _visualPosX = pos.getX.toDouble
        _visualPosY = pos.getY.toDouble
      } else {
        val t = Math.max(0.0, elapsed.toDouble / glideNanos)
        _visualPosX = glideFromX + (glideToX - glideFromX) * t
        _visualPosY = glideFromY + (glideToY - glideFromY) * t
      }
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
