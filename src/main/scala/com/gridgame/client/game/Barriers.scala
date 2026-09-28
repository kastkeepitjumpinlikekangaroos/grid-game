package com.gridgame.client.game

import com.gridgame.common.model._
import com.gridgame.common.protocol._

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Barriers (BarrierCast; see Barriers in the gameplay docs). Ours is raised by an ability, faces
 * wherever we aim, and is streamed to the server while it is up ([[streamBarrier]]); firing or
 * casting anything drops it. Everyone else's runs on its own timer from what their updates say.
 * Shots stopped on one ripple where they struck, and a shot in flight is never flown through one
 * that would stop it ([[barriersInFlight]]).
 */
trait Barriers { this: GameClient =>
  // Our barrier: up until barrierUntil. Dropping it early moves barrierUntil to the moment it
  // dropped, which is what the renderer fades it out from.
  private val barrierUntil: AtomicLong = new AtomicLong(0)
  private val barrierRaisedAt: AtomicLong = new AtomicLong(0)
  // When we last told the server where it faces, and what we said; and whether the last update we
  // sent had it up, so that it running out gets said too
  private[game] val lastBarrierSentAt: AtomicLong = new AtomicLong(0)
  @volatile private[game] var lastBarrierSentAngle: Float = 0f
  @volatile private[game] var barrierAnnounced: Boolean = false
  private val BARRIER_STREAM_MS = 100L
  private val BARRIER_TURN_STREAM_MS = 50L
  private val BARRIER_TURN_RAD = 0.035f

  // Someone else's barrier runs on its own timer, as ours does: every update that has one up says
  // how long it has left (PlayerUpdatePacket byte [53-54]), so losing, delaying or refusing a run
  // of their updates doesn't take it off our screen while it is still up. Only used when an update
  // says a barrier is up without saying how long for.
  private val REMOTE_BARRIER_FALLBACK_MS = 600L
  // The newest update we have taken a barrier from, per player. A datagram that overtakes another
  // — one sent before the barrier went up, or before it was turned — must not undo what a newer
  // one said: remote updates are otherwise applied in the order they arrive.
  private val barrierUpdateSeq = new ConcurrentHashMap[UUID, Integer]()

  // Shots stopped on barriers, for the ripple where each struck: a ring of slots, overwritten
  // oldest first. `across` is where along the barrier it struck (Barrier.acrossOf), so the
  // ripple moves with a barrier that is carried on.
  val BARRIER_IMPACT_SLOTS = 16
  private val barrierImpactOwner = new Array[UUID](BARRIER_IMPACT_SLOTS)
  private val barrierImpactAcross = new Array[Float](BARRIER_IMPACT_SLOTS)
  private val barrierImpactTime = new Array[Long](BARRIER_IMPACT_SLOTS)
  private var barrierImpactNext = 0

  // ── Ours ────────────────────────────────────────────────────────────────

  def hasBarrier: Boolean = System.currentTimeMillis() < barrierUntil.get()

  /** When our barrier went up, and when it comes (or came) down: the renderer fades it by these. */
  def getBarrierRaisedAt: Long = barrierRaisedAt.get()
  def getBarrierUntil: Long = barrierUntil.get()

  /** Up now, for `durationMs`, facing the cursor; the update carries both (streamBarrier keeps it
    * turning). */
  private[game] def raiseBarrier(now: Long, durationMs: Int): Unit = {
    barrierRaisedAt.set(now)
    barrierUntil.set(now + durationMs)
    sendPositionUpdate(localPosition.get())
  }

  /**
   * While our barrier is up, keep the server told which way it faces: every 100ms, and every 50ms
   * while it is turning, so everyone else sees it swing as we aim. When it runs out, say that too,
   * twice, since nothing else would: we may well be standing still. Called every frame by the
   * input handlers; it decides for itself whether anything needs sending.
   */
  def streamBarrier(): Unit = {
    if (hasBarrier) {
      val since = System.currentTimeMillis() - lastBarrierSentAt.get()
      if (since >= BARRIER_STREAM_MS ||
          (since >= BARRIER_TURN_STREAM_MS && angleBetween(getAimAngle, lastBarrierSentAngle) > BARRIER_TURN_RAD)) {
        sendPositionUpdate(localPosition.get())
      }
    } else if (barrierAnnounced) {
      val pos = localPosition.get()
      sendPositionUpdate(pos)
      sendPositionUpdate(pos)
    }
  }

  private def angleBetween(a: Float, b: Float): Float = {
    val d = Math.abs(a - b) % (2 * Math.PI).toFloat
    if (d > Math.PI) (2 * Math.PI).toFloat - d else d
  }

  /** Firing or casting anything drops our barrier: it goes down, and the server hears so, before
    * the shot is sent. */
  private[game] def dropBarrier(): Unit = {
    val now = System.currentTimeMillis()
    if (now < barrierUntil.get()) {
      barrierUntil.set(now)
      // Twice, as running out is announced twice: everyone else now holds it until the time the
      // server gave it, so a single lost datagram would leave them a barrier that isn't there.
      val pos = localPosition.get()
      sendPositionUpdate(pos)
      sendPositionUpdate(pos)
    }
  }

  /** A new life or a new match: no barrier, and nothing left to announce about the last one. */
  private[game] def clearBarrier(): Unit = {
    barrierUntil.set(0)
    barrierRaisedAt.set(0)
    barrierAnnounced = false
    barrierUpdateSeq.clear()
  }

  // ── Everyone else's ─────────────────────────────────────────────────────

  /** An update about `player` (someone else): their barrier as it says. A barrier lasts as long as
    * the update that carries it says it has left, and is turned by every update that has it up; one
    * without it drops it, and it fades out from then. Only the newest update about it counts, so a
    * datagram that overtakes another can't undo it. */
  private[game] def heardBarrierOf(player: Player, packet: PlayerUpdatePacket, now: Long): Unit =
    if (barrierWordIsNew(player.getId, packet.getSequenceNumber)) {
      if ((packet.getEffectFlags2 & 0x04) != 0) {
        if (!player.hasBarrier) player.setBarrierRaisedAt(now)
        player.setBarrierUntil(now + remoteBarrierMs(packet))
        player.setBarrierAngle(packet.aimAngleRadians.toFloat)
      } else {
        player.dropBarrier()
      }
    }

  /** They left the match: nothing more to hear of their barrier. */
  private[game] def forgetBarrierOf(playerId: UUID): Unit = barrierUpdateSeq.remove(playerId)

  /** How long a barrier this update says is up has left. An update that doesn't say falls back to
    * a short lease, so one is never taken to be already over. */
  private[game] def remoteBarrierMs(packet: PlayerUpdatePacket): Long = {
    val left = packet.getBarrierMs
    if (left > 0) left.toLong else REMOTE_BARRIER_FALLBACK_MS
  }

  /** Is this the newest word we have had on that player's barrier? Records it if so. */
  private[game] def barrierWordIsNew(playerId: UUID, seq: Int): Boolean = {
    val last = barrierUpdateSeq.get(playerId)
    // Circular comparison, as the server's own sequence checks are: the numbers wrap at 2^31
    if (last != null && ((seq - last.intValue()) & 0x7FFFFFFF) >= 0x40000000) return false
    barrierUpdateSeq.put(playerId, Integer.valueOf(seq))
    true
  }

  // ── Shots stopped on them ───────────────────────────────────────────────

  /** A shot stopped on `holder`'s barrier at (x, y): kept for the ripple, as where along the
    * barrier it struck. */
  private[client] def recordBarrierImpact(holder: UUID, x: Float, y: Float, now: Long): Unit = {
    if (holder == null) return
    val (hx, hy, angle) =
      if (holder.equals(localPlayerId)) {
        val pos = localPosition.get()
        (pos.getX.toFloat, pos.getY.toFloat, getAimAngle)
      } else {
        val p = players.get(holder)
        if (p == null) return
        (p.getPosition.getX.toFloat, p.getPosition.getY.toFloat, p.getBarrierAngle)
      }
    val across = Barrier.acrossOf(hx, hy, Math.cos(angle).toFloat, Math.sin(angle).toFloat, x, y)
    barrierImpactOwner.synchronized {
      val i = barrierImpactNext
      barrierImpactOwner(i) = holder
      barrierImpactAcross(i) = Math.max(-Barrier.WIDTH / 2, Math.min(Barrier.WIDTH / 2, across))
      barrierImpactTime(i) = now
      barrierImpactNext = (i + 1) % BARRIER_IMPACT_SLOTS
    }
  }

  /** Slot `i` of the barrier impacts: whose barrier it struck, where along it, and when (0 if the
    * slot has never been used). Read by the renderer; a slot being overwritten mid-read only
    * misplaces one ripple for a frame. */
  def getBarrierImpactOwner(i: Int): UUID = barrierImpactOwner(i)
  def getBarrierImpactAcross(i: Int): Float = barrierImpactAcross(i)
  def getBarrierImpactTime(i: Int): Long = barrierImpactTime(i)

  // ── In a throw's way ────────────────────────────────────────────────────

  /** Does an enemy's raised barrier stand across the straight way from (ax, ay) to (bx, by), met
    * from its front? What stops a trap we throw, as a wall does; the server asks the same of its
    * own players (GameInstance.barrierAcross). Ours and our allies' stop nothing of ours. */
  private[game] def enemyBarrierAcross(ax: Float, ay: Float, bx: Float, by: Float): Boolean = {
    val it = players.values().iterator()
    while (it.hasNext) {
      val p = it.next()
      if (p.hasBarrier && !p.isDead && !p.isPhased && !(localTeamId != 0 && p.getTeamId == localTeamId)) {
        val pos = p.getPosition
        if (Barrier.crosses(pos.getX.toFloat, pos.getY.toFloat, p.getBarrierAngle, ax, ay, bx, by)) return true
      }
    }
    false
  }

  // ── In a projectile's way ───────────────────────────────────────────────

  // The barriers up this frame, as the server stops shots on them: whose, where they stand, which
  // way it faces, and their team. A shot isn't flown through one that stops it, any more than into
  // a wall: it waits on its face for the BLOCKED that puts it there. Render thread only.
  private val MAX_FLY_BARRIERS = 32
  private val flyBarrierHolder = new Array[UUID](MAX_FLY_BARRIERS)
  private val flyBarrierX = new Array[Float](MAX_FLY_BARRIERS)
  private val flyBarrierY = new Array[Float](MAX_FLY_BARRIERS)
  private val flyBarrierCos = new Array[Float](MAX_FLY_BARRIERS)
  private val flyBarrierSin = new Array[Float](MAX_FLY_BARRIERS)
  private val flyBarrierTeam = new Array[Byte](MAX_FLY_BARRIERS)
  private var flyBarrierCount = 0

  /** The barriers up this frame, as what stops a projectile in flight, or null if none is up. */
  private[game] def barriersInFlight(): FlightBlockers = {
    gatherBarriers()
    if (flyBarrierCount > 0) barrierBlockers else null
  }

  private def gatherBarriers(): Unit = {
    var n = 0
    // Ours faces where we aim, as it is drawn; a phased holder's is as insubstantial as they are
    if (hasBarrier && !isDead && !isPhased) {
      val pos = localPosition.get()
      n = noteFlyBarrier(n, localPlayerId, pos.getX, pos.getY, getAimAngle, localTeamId)
    }
    val it = players.values().iterator()
    while (it.hasNext && n < MAX_FLY_BARRIERS) {
      val p = it.next()
      if (p.hasBarrier && !p.isDead && !p.isPhased) {
        val pos = p.getPosition
        n = noteFlyBarrier(n, p.getId, pos.getX, pos.getY, p.getBarrierAngle, p.getTeamId)
      }
    }
    var i = n
    while (i < flyBarrierCount) { flyBarrierHolder(i) = null; i += 1 }
    flyBarrierCount = n
  }

  private def noteFlyBarrier(n: Int, holder: UUID, x: Int, y: Int, angle: Float, team: Byte): Int = {
    if (n >= MAX_FLY_BARRIERS) return n
    flyBarrierHolder(n) = holder
    flyBarrierX(n) = x.toFloat; flyBarrierY(n) = y.toFloat
    flyBarrierCos(n) = Math.cos(angle).toFloat; flyBarrierSin(n) = Math.sin(angle).toFloat
    flyBarrierTeam(n) = team
    n + 1
  }

  // What the server stops on a barrier: an enemy's shot — not the holder's own, not a teammate's
  private val barrierBlockers = new FlightBlockers {
    def blockedAt(owner: UUID, ax: Float, ay: Float, bx: Float, by: Float): Float = {
      val ownerTeam = teamOf(owner)
      var best = -1f
      var i = 0
      while (i < flyBarrierCount) {
        if (!flyBarrierHolder(i).equals(owner) && !(ownerTeam != 0 && ownerTeam == flyBarrierTeam(i))) {
          val c = Barrier.crossing(flyBarrierX(i), flyBarrierY(i), flyBarrierCos(i), flyBarrierSin(i), ax, ay, bx, by)
          if (c >= 0f && (best < 0f || c < best)) best = c
        }
        i += 1
      }
      best
    }
  }
}
