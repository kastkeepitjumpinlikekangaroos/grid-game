package com.gridgame.client.render

import com.gridgame.common.Constants
import com.gridgame.common.model.{Projectile, ProjectileDef, WorldData}

import java.util.UUID

/**
 * A projectile in a match, as this client draws it: flown between the positions the server sends.
 *
 * The server moves every projectile once a tick (Constants.PROJECTILE_SPEED_MS, 30ms) and sends a
 * MOVE for each. Drawn where the newest MOVE put it, a projectile stood still for a frame and then
 * jumped a whole tick's flight: at 60 fps it held still in 44% of frames and moved nearly twice as
 * far as it should in the rest, at 120 fps it held still in 72%, and packets that arrived early or
 * late moved each jump onto another frame.
 *
 * Every projectile packet says which server tick it is from (ProjectilePacket.getTick), and
 * [[ProjectileTimeline]] says which tick it is now, so where a projectile is doesn't have to wait
 * for news: it is its newest position flown on at its speed for the ticks since. A straight flight
 * is then drawn exactly as the server flies it, whenever its packets arrive — a late one only
 * confirms what was already drawn. What the server does that can't be foreseen (a bounce, a turn at
 * the end of a boomerang's range, a gem doubling its speed) arrives as a correction, and a
 * correction is smoothed out over a few frames rather than jumped to.
 *
 * It is never flown past where it will stop: the face of the terrain ahead (found along the very
 * sub-steps the server will take), the end of its range, the divider between the teams, or a raised
 * barrier that stops it ([[FlightBlockers]]). That is where its DESPAWN or BLOCKED will put it, so
 * it fades out where it was last drawn. A player it is about to hit it does fly on toward, by up to
 * a tick: a hit radius reaches well past the body, and the hit removes it.
 *
 * Threads: the packet thread says what the server said ([[heard]]) or that it has stopped
 * ([[stopAt]]); the render thread flies it once a frame ([[fly]]), writing where it is drawn into
 * the Projectile it is — getX/getY, its heading, how far it has come (getDistanceTraveled, as the
 * server counts it) and whether it is on its way back. Both under its lock.
 */
final class NetProjectile(pid: Int, owner: UUID, x0: Float, y0: Float, dirX0: Float, dirY0: Float,
                          color: Int, charge: Int, pType: Byte, tick0: Int, spawned: Boolean, steps0: Int,
                          world0: WorldData, heardAt: Long)
  extends Projectile(pid, owner, x0, y0, dirX0, dirY0, color, charge, pType) {

  import NetProjectile._

  private val pDef = ProjectileDef.get(projectileType)
  private val maxRange = pDef.effectiveMaxRange(chargeLevel).toFloat
  // Seen fired (its SPAWN), so first drawn where it was fired (originX/Y), not somewhere on its way
  private val seenFired = spawned
  // The server flies it in sub-steps of at most half a cell and judges each where it ends: the
  // end of its range, a boomerang's turn and a wall all fall at the end of one
  private val sub = speedMultiplier / Math.max(1, Math.ceil(speedMultiplier / 0.5f).toInt)

  // What the server has said (packet thread, under the lock): its newest position, the tick it is
  // from, and how it is moving
  private var nTick = 0
  private var nX = 0f
  private var nY = 0f
  private var nUx = 0f
  private var nUy = 0f
  // Steps a tick: 2 while its owner has a gem, which has the server move it twice a tick
  private var nSteps = 1
  // How far it had flown by then, as the server counts it (range, and a boomerang's turn)
  private var nDist = 0f
  // Ticks past nTick it can be flown before it would stop
  private var nLimit = 0f
  private var nReturning = false
  // The newest is where it was fired: no tick has moved it yet
  private var nSpawn = false
  private var nVersion = 0
  private var heardNanos = 0L
  private var stopped = false

  // What the render thread last flew it from, and the correction still being smoothed out
  private var rVersion = 0
  private var rTick = 0
  private var rX = 0f
  private var rY = 0f
  private var rUx = 0f
  private var rUy = 0f
  private var rSpeed = 0f
  private var rDist = 0f
  private var rLimit = 0f
  private var rReturning = false
  private var errX = 0f
  private var errY = 0f

  heard(tick0, x0, y0, dirX0, dirY0, spawned, steps0, world0, heardAt)

  /**
   * The server's word on it: at the end of server tick `tick` it was at (x, y), heading (dirX,
   * dirY). A SPAWN's is where it was fired, which holds until the tick after the one it is stamped
   * with. `steps` is how many times a tick the server moves it (2 under a gem), which is needed only
   * until two moves in a straight line show it. Returns false for news older than what it has:
   * datagrams overtake each other.
   */
  def heard(tick: Int, x: Float, y: Float, dirX: Float, dirY: Float, spawn: Boolean, steps: Int,
            world: WorldData, nowNanos: Long): Boolean = synchronized {
    if (stopped) return false
    val first = nVersion == 0
    // A MOVE from the tick its SPAWN is stamped with is newer: it was fired while that tick ran,
    // and that tick moved it
    if (!first && (tick < nTick || (tick == nTick && !nSpawn))) return false
    val len = Math.sqrt(dirX * dirX + dirY * dirY).toFloat
    val ux = if (len > 1e-6f) dirX / len else nUx
    val uy = if (len > 1e-6f) dirY / len else nUy
    if (first) {
      nSteps = Math.max(1, steps)
      nDist = 0f
    } else if (nSpawn) {
      // Its first move, which is straight: whether that was the tick after its SPAWN's or the
      // same one can't be told from the ticks, so how far it went is measured
      nDist = hypot(x - nX, y - nY)
    } else {
      val gap = tick - nTick
      val turn = ux * nUx + uy * nUy
      if (turn > STRAIGHT && speedMultiplier > 1e-4f) {
        // Two moves in a straight line: how far it went says whether it moves once a tick or twice
        nSteps = if (hypot(x - nX, y - nY) / (gap * speedMultiplier) > 1.5f) 2 else 1
      }
      val flown = speedMultiplier * nSteps * gap
      if (turn < -STRAIGHT && pDef.boomerang && !nReturning) {
        // Turned back at the end of its range, where the server starts counting again
        nDist = Math.max(0f, flown - toEndOfRange)
        nReturning = true
      } else nDist += flown // a bounce doesn't restart the count
    }
    nTick = tick; nX = x; nY = y; nUx = ux; nUy = uy; nSpawn = spawn
    nLimit = stopsAfter(world)
    nVersion += 1
    heardNanos = nowNanos
    true
  }

  /**
   * How many ticks past its newest position it can be flown before it would stop: never beyond
   * MAX_AHEAD, the end of its range, the face of the terrain it will strike, or the divider.
   */
  private def stopsAfter(world: WorldData): Float = {
    val speed = speedMultiplier * nSteps
    if (speed < 1e-4f) return MAX_AHEAD
    // Its sub-steps from here: the end of its range comes first, then the terrain
    var reach = Math.min(MAX_AHEAD * speed, toEndOfRange)
    if (world != null) {
      val passes = pDef.passesThroughWalls
      val n = Math.ceil(reach / sub).toInt
      var i = 1
      var struck = false
      while (i <= n && !struck) {
        var d = sub * i
        struck = TerrainImpact.blocks(world, TerrainImpact.cellOf(nX + nUx * d), TerrainImpact.cellOf(nY + nUy * d), passes)
        if (struck) {
          // Stopped at the end of this sub-step, and drawn on the face it met, which is where its
          // DESPAWN will put it (TerrainImpact.resolve walks back the same way)
          var back = 0
          while (back < 40 && d > 0f &&
                 TerrainImpact.blocks(world, TerrainImpact.cellOf(nX + nUx * d), TerrainImpact.cellOf(nY + nUy * d), passes)) {
            d -= 0.05f
            back += 1
          }
          reach = Math.min(reach, Math.max(0f, d))
        }
        i += 1
      }
      val divider = world.divider
      if (divider != null && divider.up && reach > 0f) {
        val c = divider.crossing(nX, nY, nX + nUx * reach, nY + nUy * reach)
        if (c >= 0f) reach = Math.max(0f, c * reach - STANDOFF)
      }
    }
    reach / speed
  }

  /** How much further it goes from its newest position before its range runs out: to the end of
    * the sub-step that reaches it, which is where the server stops it (or turns a boomerang). */
  private def toEndOfRange: Float =
    Math.max(0f, Math.ceil(Math.max(0f, maxRange - nDist) / sub).toFloat * sub)

  /**
   * Put it where it is at server tick `t` (with a fraction: [[ProjectileTimeline.tickAt]]): its
   * newest position flown on for the ticks since, no further than it can go, plus what is left of
   * the last correction once `decay` (this frame's share of it that remains) has taken its share.
   * Returns [[FLYING]]; [[STOPPED]] once stopAt has put it down; or [[EXPIRED]] if nothing has been
   * heard of it for EXPIRE_NS, which means the packet that ended it was lost — it is then stopped
   * where it is, for the caller to fade out.
   */
  def fly(t: Double, decay: Float, nowNanos: Long, blockers: FlightBlockers = null): Int = synchronized {
    if (stopped) return STOPPED
    if (nowNanos - heardNanos > EXPIRE_NS) { stopped = true; return EXPIRED }
    if (java.lang.Double.isNaN(t)) return FLYING
    errX *= decay; errY *= decay
    if (rVersion != nVersion) {
      val first = rVersion == 0
      // Where it was about to be drawn, flown on from what was known before
      var wasX = 0f; var wasY = 0f
      if (!first) {
        val a = flown(t, blockers)
        wasX = rX + rUx * rSpeed * a + errX; wasY = rY + rUy * rSpeed * a + errY
      }
      rTick = nTick; rX = nX; rY = nY; rUx = nUx; rUy = nUy
      rSpeed = speedMultiplier * nSteps
      rDist = nDist; rLimit = nLimit; rReturning = nReturning
      rVersion = nVersion
      val a = flown(t, blockers)
      val isX = rX + rUx * rSpeed * a; val isY = rY + rUy * rSpeed * a
      if (!first) { errX = wasX - isX; errY = wasY - isY }
      // Just fired: it leaves from where it was fired, and catches up with where it is by now —
      // whether or not its first move has arrived by the first frame it is drawn in
      else if (seenFired) { errX = originX - isX; errY = originY - isY }
      else { errX = 0f; errY = 0f }
      if (errX * errX + errY * errY > SNAP_CELLS * SNAP_CELLS) { errX = 0f; errY = 0f }
    }
    val a = flown(t, blockers)
    updatePosition(rX + rUx * rSpeed * a + errX, rY + rUy * rSpeed * a + errY, rUx, rUy)
    setDistanceTraveled(rDist + rSpeed * a)
    setReturning(rReturning)
    FLYING
  }

  /** It has stopped at (x, y) — struck terrain, a barrier or the divider — and is drawn there from
    * now on. Packet thread. */
  def stopAt(x: Float, y: Float): Unit = synchronized {
    stopped = true
    updatePosition(x, y, dx, dy)
  }

  /** How many ticks past the news the render thread last took it has flown by tick `t`: the ticks
    * since, no further than where it stops, and short of a barrier standing in its way. */
  private def flown(t: Double, blockers: FlightBlockers): Float = {
    var a = ahead(t, rTick, rLimit)
    if (blockers != null && a > 0f && rSpeed > 1e-4f && !pDef.passesThroughWalls) {
      val reach = rSpeed * a
      val c = blockers.blockedAt(ownerId, rX, rY, rX + rUx * reach, rY + rUy * reach)
      if (c >= 0f) a = Math.max(0f, c * reach - STANDOFF) / rSpeed
    }
    a
  }

  @inline private def ahead(t: Double, tick: Int, limit: Float): Float = {
    val a = (t - tick).toFloat
    if (a <= 0f) 0f else if (a > limit) limit else a
  }

  @inline private def hypot(x: Float, y: Float): Float = Math.sqrt(x * x + y * y).toFloat

  // What the server last said, for tests and for anyone debugging a projectile
  def heardTick: Int = synchronized(nTick)
  def heardX: Float = synchronized(nX)
  def heardY: Float = synchronized(nY)
  def heardSteps: Int = synchronized(nSteps)
  def isStopped: Boolean = synchronized(stopped)
}

object NetProjectile {
  /** How many ticks past its newest position a projectile is flown before it waits for news. A
    * packet late by more than three ticks, or three lost in a row, hold it still until the next. */
  val MAX_AHEAD = 4f
  /** Nothing heard of it for this long (ten ticks): whatever ended it was lost on the way. Every
    * projectile the server still has is sent every tick. */
  val EXPIRE_NS: Long = 10L * Constants.PROJECTILE_SPEED_MS * 1000000L
  /** A correction bigger than this is not an error to smooth out but a jump. */
  val SNAP_CELLS = 3f
  /** How long a correction takes to fall to a third of itself. */
  val SMOOTH_MS = 40f
  /** How far short of a barrier or the divider the server stops a shot (ProjectileManager's). */
  private val STANDOFF = 0.05f
  /** Headings closer than this (the cosine between them) are the same heading. */
  private val STRAIGHT = 0.9999f

  val FLYING = 0
  val STOPPED = 1
  val EXPIRED = 2
}

/**
 * What stands in a projectile's way this frame besides the terrain: the barriers raised in front of
 * their holders, which move and turn from frame to frame (GameClient gathers them).
 */
trait FlightBlockers {
  /** Where the path from (ax, ay) to (bx, by) of a projectile `owner` fired first meets something
    * that stops it: the fraction of the way along it, in [0, 1], or -1. */
  def blockedAt(owner: UUID, ax: Float, ay: Float, bx: Float, by: Float): Float
}

/**
 * Which of the server's projectile ticks it is now, on this client's clock.
 *
 * Every MOVE is sent at the end of its tick and stamped with it, so `arrival - tick * TICK` is the
 * same for all of them but for how long each spent on the way. The least of those over the last
 * couple of seconds is when a tick's news gets here by the quickest route, and that is where the
 * timeline puts the tick. A packet that took longer brings news the projectile has already been
 * flown to. The least, not the average: a hit or a stop is then never drawn later than the network
 * allows.
 *
 * The estimate moves when the route does — a quicker packet, or the window forgetting one — and the
 * renderer follows it at a tenth of real time rather than jumping (a correction speeds up or slows
 * every projectile by a tenth for a moment, instead of moving them all at once). Over a quarter of a
 * second off, or a new match, it jumps.
 *
 * Packet thread: [[heard]], [[heardSpawn]], [[reset]]. Render thread: [[tickAt]].
 */
final class ProjectileTimeline {
  import ProjectileTimeline._

  // The least `arrival - tick * TICK` in each of the last BUCKETS slots of BUCKET_NS of arrivals
  private val slotIdx = Array.fill(BUCKETS)(Long.MinValue)
  private val slotLeast = new Array[Long](BUCKETS)
  @volatile private var base = 0L
  @volatile private var hasBase = false
  @volatile private var generation = 0

  // What the renderer follows (render thread)
  private var applied = 0L
  private var appliedGen = -1
  private var lastNanos = 0L

  /** A MOVE from `tick` arrived at `arrivalNanos`. */
  def heard(tick: Int, arrivalNanos: Long): Unit = synchronized {
    val sample = arrivalNanos - tick.toLong * TICK_NS
    val idx = Math.floorDiv(arrivalNanos, BUCKET_NS)
    val slot = Math.floorMod(idx, BUCKETS.toLong).toInt
    if (slotIdx(slot) != idx) { slotIdx(slot) = idx; slotLeast(slot) = sample }
    else if (sample < slotLeast(slot)) slotLeast(slot) = sample
    var least = Long.MaxValue
    var i = 0
    while (i < BUCKETS) {
      if (slotIdx(i) > idx - BUCKETS && slotLeast(i) < least) least = slotLeast(i)
      i += 1
    }
    base = least
    hasBase = true
  }

  /** A SPAWN goes out when the shot is fired, not at the end of a tick, so it says little about the
    * route; it only starts a timeline that has nothing else to go on. */
  def heardSpawn(tick: Int, arrivalNanos: Long): Unit = synchronized {
    if (!hasBase) heard(tick, arrivalNanos)
  }

  /** A new match counts its ticks from the start. */
  def reset(): Unit = synchronized {
    java.util.Arrays.fill(slotIdx, Long.MinValue)
    hasBase = false
    generation += 1
  }

  /** The server tick it is at `nowNanos`, with a fraction; NaN until anything has been heard. */
  def tickAt(nowNanos: Long): Double = {
    if (!hasBase) return Double.NaN
    val b = base
    val g = generation
    if (g != appliedGen || Math.abs(b - applied) > SNAP_NS) { applied = b; appliedGen = g }
    else if (b != applied) {
      val dt = Math.max(0L, Math.min(nowNanos - lastNanos, MAX_FRAME_NS))
      val step = Math.max(1L, (dt * FOLLOW).toLong)
      applied += Math.max(-step, Math.min(step, b - applied))
    }
    lastNanos = nowNanos
    (nowNanos - applied).toDouble / TICK_NS
  }
}

object ProjectileTimeline {
  val TICK_NS: Long = Constants.PROJECTILE_SPEED_MS * 1000000L
  private val BUCKET_NS = 250000000L
  private val BUCKETS = 8
  private val SNAP_NS = 250000000L
  private val FOLLOW = 0.1
  private val MAX_FRAME_NS = 100000000L
}
