package com.gridgame.client.game

import com.gridgame.common.observability.Attrs
import com.gridgame.common.observability.Metrics

/**
 * The projectiles we have asked the server for, until it answers: telemetry, which nothing in the
 * game reads. A spawn request is one datagram, never sent again, and the server refuses one without
 * a word, so a request lost on the way or refused just comes to nothing. This counts how often
 * (`gridgame.client.spawn_requests`, by attack slot and outcome).
 *
 * The answer is the first packet we hear about a projectile of ours we hadn't heard of: its SPAWN,
 * or, when that was lost, its first MOVE or its end, since the shot flew all the same. It answers
 * the oldest request for its type still waiting. A request with no answer within a second is
 * `unanswered`: lost on the way there, refused, or answered only by packets that were all lost.
 * What we asked for as we died, or as the match ended, is dropped uncounted: the server refuses it
 * for that, not for anything the network did.
 *
 * Requested on the render thread and answered on the packet thread, so every method takes the
 * lock. Nothing here allocates once it is built, and nothing runs per frame.
 */
final class PendingSpawns(timeoutNs: Long = PendingSpawns.TIMEOUT_NS) {
  import PendingSpawns._

  // The requests waiting, oldest first, in a ring from `head`: the projectile type asked for, the
  // attack slot (CLOSED once counted: a hole until the front moves past it) and when it was sent
  private val types = new Array[Byte](CAPACITY)
  private val slots = new Array[Int](CAPACITY)
  private val sentAt = new Array[Long](CAPACITY)
  private var head = 0
  private var size = 0

  // The ids of our projectiles already heard of, newest last: nothing more about them answers
  // anything. A player has at most 30 in flight (the server's cap), so these outlast them.
  private val heardIds = new Array[Int](HEARD_IDS)
  private var heardNext = 0
  private var heardCount = 0

  // Counted so far, by attack slot: what the metric says, kept for tests to read
  private val answeredBySlot = new Array[Long](SLOTS)
  private val unansweredBySlot = new Array[Long](SLOTS)

  /** We asked the server for a projectile of `projectileType`, fired from `slot` (AttackSlot). */
  def requested(projectileType: Byte, slot: Int, now: Long): Unit = synchronized {
    expire(now)
    // Only if a second's requests overran the ring, which a player's cooldowns don't allow
    if (size == CAPACITY) close(head, answered = false)
    val i = (head + size) % CAPACITY
    types(i) = projectileType
    slots(i) = slot
    sentAt(i) = now
    size += 1
  }

  /** A packet about one of our own projectiles, whatever it says. */
  def heard(projectileId: Int, projectileType: Byte, now: Long): Unit = synchronized {
    if (known(projectileId)) return
    heardIds(heardNext) = projectileId
    heardNext = (heardNext + 1) % HEARD_IDS
    heardCount = Math.min(heardCount + 1, HEARD_IDS)
    expire(now)
    var k = 0
    while (k < size) {
      val i = (head + k) % CAPACITY
      if (slots(i) != CLOSED && types(i) == projectileType) {
        close(i, answered = true)
        return
      }
      k += 1
    }
    // Nothing waiting for it: an answer that came after its request had been given up on
  }

  /** Count every request past its time as unanswered. The game does this when it next asks or
    * hears, so a request is counted a little after its second is up, never before. */
  def expire(now: Long): Unit = synchronized {
    while (size > 0 && now - sentAt(head) >= timeoutNs) close(head, answered = false)
  }

  /** We died: what is past its time was unanswered; the rest is dropped uncounted. */
  def dropWaiting(now: Long): Unit = synchronized {
    expire(now)
    head = 0
    size = 0
  }

  /** The match is over: nothing is waiting, and the next one numbers its projectiles afresh. */
  def forgetMatch(now: Long): Unit = synchronized {
    dropWaiting(now)
    heardNext = 0
    heardCount = 0
  }

  /** Requests counted each way, from one attack slot (AttackSlot) or all of them. */
  def answered(slot: Int): Long = synchronized(answeredBySlot(slotIndex(slot)))
  def unanswered(slot: Int): Long = synchronized(unansweredBySlot(slotIndex(slot)))
  def answered: Long = synchronized(answeredBySlot.sum)
  def unanswered: Long = synchronized(unansweredBySlot.sum)

  private def known(projectileId: Int): Boolean = {
    var k = 1
    while (k <= heardCount) {
      if (heardIds((heardNext - k + HEARD_IDS) % HEARD_IDS) == projectileId) return true
      k += 1
    }
    false
  }

  /** Count request `i` and leave a hole where it was; the front moves past any holes. */
  private def close(i: Int, answered: Boolean): Unit = {
    val slot = slotIndex(slots(i))
    if (answered) answeredBySlot(slot) += 1 else unansweredBySlot(slot) += 1
    Metrics.clientSpawnRequests.add(1L, Attrs.spawnRequest(slot, answered))
    slots(i) = CLOSED
    while (size > 0 && slots(head) == CLOSED) {
      head = (head + 1) % CAPACITY
      size -= 1
    }
  }
}

object PendingSpawns {
  /** How long the server has to answer: far longer than any round trip a match is playable on. */
  val TIMEOUT_NS: Long = 1000000000L

  /** Requests that can be waiting at once: a second of the most a player can fire (a burst, a
    * gem-boosted shot, two fans of eight) is under half of it. */
  private val CAPACITY = 64

  /** Our projectiles remembered as heard of: over four times the 30 a player can have in flight. */
  private val HEARD_IDS = 128

  private val CLOSED = -1

  /** AttackSlot's four: primary, Q, E, burst. */
  private val SLOTS = 4
  private def slotIndex(slot: Int): Int = if (slot >= 0 && slot < SLOTS) slot else 0
}
