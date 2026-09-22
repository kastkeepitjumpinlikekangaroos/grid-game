package com.gridgame.server

import com.gridgame.common.model.Player
import com.gridgame.common.model.Trap
import com.gridgame.common.model.TrapDef
import com.gridgame.common.model.TrapPlacement

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import scala.collection.mutable.ArrayBuffer
import scala.jdk.CollectionConverters._

sealed trait TrapEvent
/** `victimId` stepped on the trap: it is already consumed, and its damage and effects are the
  * caller's to apply. */
case class TrapSprung(trap: Trap, victimId: UUID) extends TrapEvent
/** Gone without going off: it ran out, its owner left, or a newer one of theirs pushed it off. */
case class TrapGone(trap: Trap) extends TrapEvent

/** A trap put on the ground, and whatever of the placer's had to go to make room for it. */
case class TrapPlaced(trap: Trap, removed: Seq[Trap])

/**
 * The traps lying on the ground in one match.
 *
 * Two threads reach it: placements arrive on a network thread while triggers and expiries are
 * found on the projectile tick. Every trap leaves through [[claim]], which takes it with
 * `traps.remove(id, trap)` — so however many threads find the same trap at once, exactly one of
 * them takes it, and a trap is only ever sprung, expired or removed once.
 */
class TrapManager(registry: ClientRegistry, isTeammate: (UUID, UUID) => Boolean = (_, _) => false) {
  private val traps = new ConcurrentHashMap[Int, Trap]()
  // One trap to a cell, so a placement onto an occupied cell is an O(1) refusal
  private val byCell = new ConcurrentHashMap[Long, Trap]()
  // Each player's traps, oldest first: a further one past the limit takes the oldest away
  private val byOwner = new ConcurrentHashMap[UUID, java.util.ArrayDeque[Integer]]()
  private val nextId = new AtomicInteger(1)
  // Held for the whole of a placement: the cell check, the eviction and the insert are one step
  private val lock = new Object

  // How far out a player has to be looked at for traps, in whole cells
  private val scanCells = Math.ceil(TrapDef.maxTriggerRadius).toInt

  // Async gauge: traps lying on the ground. Closed with the manager so it stops reporting a
  // finished match's traps, and so the callbacks don't pile up across matches.
  private val trapsActiveGauge = com.gridgame.common.observability.Telemetry.meter("com.gridgame.traps")
    .gaugeBuilder("gridgame.traps.active")
    .setDescription("Traps currently armed or arming on the ground")
    .setUnit("{trap}")
    .ofLongs()
    .buildWithCallback { obs =>
      obs.record(traps.size().toLong, io.opentelemetry.api.common.Attributes.empty())
    }

  private def cellKey(x: Int, y: Int): Long = (x.toLong << 32) | (y.toLong & 0xFFFFFFFFL)

  /**
   * Put a trap of `trapType` on (x, y). Null if the type is unknown or a trap is already there;
   * otherwise the new trap and whatever of this player's had to make room for it.
   */
  def place(ownerId: UUID, teamId: Byte, x: Int, y: Int, trapType: Byte, now: Long): TrapPlaced = {
    val d = TrapDef.get(trapType)
    if (d == null) return null
    lock.synchronized {
      val key = cellKey(x, y)
      if (byCell.containsKey(key)) return null

      val mine = byOwner.computeIfAbsent(ownerId, _ => new java.util.ArrayDeque[Integer]())
      // Traps of theirs that have already gone don't count against the limit
      val stale = mine.iterator()
      while (stale.hasNext) if (traps.get(stale.next()) == null) stale.remove()

      val removed = ArrayBuffer[Trap]()
      while (mine.size() >= d.maxActive) {
        val oldest = traps.get(mine.pollFirst())
        if (oldest != null && claim(oldest)) removed += oldest
      }

      val id = nextId.getAndIncrement() & 0x7FFFFFFF
      val trap = new Trap(id, ownerId, teamId, x, y, trapType, now, now + d.armDelayMs,
        now + d.lifetimeMs)
      traps.put(id, trap)
      byCell.put(key, trap)
      mine.addLast(Integer.valueOf(id))
      TrapPlaced(trap, removed.toSeq)
    }
  }

  /** Take this trap off the ground. True for whoever got there first, false for everyone else. */
  private def claim(trap: Trap): Boolean = {
    if (!traps.remove(trap.id, trap)) return false
    byCell.remove(cellKey(trap.x, trap.y), trap)
    true
  }

  /** One pass: traps that have run out, and traps an enemy is standing on. */
  def tick(now: Long): Seq[TrapEvent] = {
    if (traps.isEmpty) return Nil
    val events = ArrayBuffer[TrapEvent]()

    val iter = traps.values().iterator()
    while (iter.hasNext) {
      val trap = iter.next()
      if (trap.isExpired(now) && claim(trap)) events += TrapGone(trap)
    }

    registry.forEachPlayer { p =>
      if (canTrigger(p)) {
        val pos = p.getPosition
        springAt(p, pos.getX, pos.getY, now, events)
      }
    }
    events.toSeq
  }

  /**
   * Every trap a player walking from (fromX, fromY) to (toX, toY) set off. An update the server
   * accepts can carry a player several cells (a lost datagram, a dash), and a trap stepped clean
   * over has still been stepped on.
   */
  def triggerAlong(player: Player, fromX: Int, fromY: Int, toX: Int, toY: Int, now: Long): Seq[TrapEvent] = {
    if (traps.isEmpty || !canTrigger(player)) return Nil
    val events = ArrayBuffer[TrapEvent]()
    TrapPlacement.walkCells(fromX, fromY, toX, toY)((cx, cy) => springAt(player, cx, cy, now, events))
    events.toSeq
  }

  /** Phased players (a phase shift, a dash) pass over traps, and so does anyone a shield item has
    * made invulnerable — the same players a projectile can't touch. */
  private def canTrigger(p: Player): Boolean = !p.isDead && !p.isPhased && !p.hasShield

  /** Spring every armed trap of an enemy's that reaches (x, y). */
  private def springAt(player: Player, x: Int, y: Int, now: Long, out: ArrayBuffer[TrapEvent]): Unit = {
    var dy = -scanCells
    while (dy <= scanCells) {
      var dx = -scanCells
      while (dx <= scanCells) {
        val trap = byCell.get(cellKey(x + dx, y + dy))
        if (trap != null && trap.isArmed(now) && trap.catches(x, y) &&
            !trap.ownerId.equals(player.getId) && !isTeammate(trap.ownerId, player.getId) &&
            claim(trap)) {
          out += TrapSprung(trap, player.getId)
        }
        dx += 1
      }
      dy += 1
    }
  }

  /** The trap on this cell, or null. */
  def trapAt(x: Int, y: Int): Trap = byCell.get(cellKey(x, y))

  /** A player's traps go with them when they leave the match. */
  def removeAllOf(ownerId: UUID): Seq[Trap] = lock.synchronized {
    byOwner.remove(ownerId)
    val gone = ArrayBuffer[Trap]()
    val iter = traps.values().iterator()
    while (iter.hasNext) {
      val trap = iter.next()
      if (trap.ownerId.equals(ownerId) && claim(trap)) gone += trap
    }
    gone.toSeq
  }

  def getAll: Seq[Trap] = traps.values().asScala.toSeq

  /** Visit every trap without building a collection. */
  def forEachTrap(fn: Trap => Unit): Unit = {
    val iter = traps.values().iterator()
    while (iter.hasNext) fn(iter.next())
  }

  def countOf(ownerId: UUID): Int = {
    var n = 0
    forEachTrap(t => if (t.ownerId.equals(ownerId)) n += 1)
    n
  }

  def size: Int = traps.size()

  /** Release all state and unregister the active-traps gauge callback. */
  def close(): Unit = {
    try trapsActiveGauge.close() catch { case _: Throwable => () }
    traps.clear()
    byCell.clear()
    byOwner.clear()
  }
}
