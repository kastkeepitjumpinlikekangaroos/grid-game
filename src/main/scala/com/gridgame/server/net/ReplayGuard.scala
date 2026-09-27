package com.gridgame.server.net

import com.gridgame.common.Constants

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Replay protection: every packet a logged-in client sends is numbered, and a number seen before —
 * or, over TCP, one that isn't ahead of the last — is dropped before anything reads the packet.
 * TCP and UDP share the client's one counter but arrive by different routes, so UDP can race
 * ahead of TCP: each is tracked on its own, UDP with a sliding window for out-of-order datagrams.
 */
final class ReplayGuard {
  private val lastTcpSequence = new ConcurrentHashMap[UUID, AtomicInteger]()
  private val lastUdpSequence = new ConcurrentHashMap[UUID, AtomicInteger]()
  // Sliding window bitmap for UDP out-of-order tolerance
  private val sequenceWindow = new ConcurrentHashMap[UUID, Array[Long]]()

  import ReplayGuard.isNewer

  def validateSequence(playerId: UUID, seqNum: Int, isUdp: Boolean): Boolean = {
    if (!isUdp) {
      // TCP is ordered: reject if seqNum is not ahead of lastSeen (lock-free CAS loop)
      // Uses circular comparison to handle 31-bit wrap-around
      val lastSeq = lastTcpSequence.computeIfAbsent(playerId, _ => new AtomicInteger(-1))
      var last = lastSeq.get()
      while (isNewer(seqNum, last)) {
        if (lastSeq.compareAndSet(last, seqNum)) return true
        last = lastSeq.get()
      }
      return false
    }

    // UDP: use sliding window for out-of-order tolerance
    val lastSeq = lastUdpSequence.computeIfAbsent(playerId, _ => new AtomicInteger(-1))
    val windowSize = Constants.SEQUENCE_WINDOW_SIZE
    val bitmapLongs = windowSize / 64 // 1024/64 = 16 longs
    // Window is stored as Array[Long] where index 0 = windowBase, rest = bitmap
    val window = sequenceWindow.computeIfAbsent(playerId, _ => new Array[Long](1 + bitmapLongs))

    window.synchronized {
      val windowBase = window(0).toInt

      // Use circular arithmetic to handle 31-bit sequence wraparound
      val delta = (seqNum - windowBase) & 0x7FFFFFFF
      if (delta >= 0x40000000) {
        // Behind window in circular space
        return false
      }

      if (delta >= windowSize) {
        // Ahead of window — advance window
        val shift = delta - windowSize + 1
        if (shift >= windowSize) {
          // Complete reset
          for (i <- 1 to bitmapLongs) window(i) = 0L
        } else {
          // Shift the bitmap
          shiftBitmap(window, shift)
        }
        window(0) = (windowBase + shift).toLong
      }

      // Check and set bit for this seqNum (use circular delta for correct offset)
      val offset = (seqNum - window(0).toInt) & 0x7FFFFFFF
      val longIdx = 1 + (offset / 64)
      val bitIdx = offset % 64

      if ((window(longIdx) & (1L << bitIdx)) != 0) {
        // Already seen this sequence number (replay)
        return false
      }

      window(longIdx) |= (1L << bitIdx)
      lastSeq.set(Math.max(lastSeq.get(), seqNum))
    }

    true
  }

  private def shiftBitmap(window: Array[Long], shift: Int): Unit = {
    val bitmapLongs = window.length - 1 // index 0 is base, rest is bitmap
    val totalBits = bitmapLongs * 64
    if (shift >= totalBits) {
      for (i <- 1 to bitmapLongs) window(i) = 0L
      return
    }
    val longShift = shift / 64
    val bitShift = shift % 64
    for (i <- 1 to bitmapLongs) {
      val srcIdx = i + longShift
      if (srcIdx > bitmapLongs) {
        window(i) = 0L
      } else if (bitShift == 0) {
        window(i) = window(srcIdx)
      } else {
        val lo = window(srcIdx) >>> bitShift
        val hi = if (srcIdx + 1 <= bitmapLongs) window(srcIdx + 1) << (64 - bitShift) else 0L
        window(i) = lo | hi
      }
    }
  }

  /** A new session: its client counts packets from zero again. Packets from the old session
    * can't be replayed into it, since they are signed with the old session's token. */
  def resetSequences(playerId: UUID): Unit = {
    lastTcpSequence.remove(playerId)
    lastUdpSequence.remove(playerId)
    sequenceWindow.remove(playerId)
  }

  def removePlayer(playerId: UUID): Unit = resetSequences(playerId)

  /** Remove entries for players no longer in the connected set (safety net for leaked state). */
  def cleanupStale(connectedPlayerIds: java.util.Set[UUID]): Unit = {
    val maps: Seq[ConcurrentHashMap[UUID, _]] = Seq(lastTcpSequence, lastUdpSequence, sequenceWindow)
    maps.foreach { map =>
      val iter = map.keySet().iterator()
      while (iter.hasNext) {
        if (!connectedPlayerIds.contains(iter.next())) iter.remove()
      }
    }
  }
}

object ReplayGuard {
  /** Circular comparison in 31-bit sequence space. Returns true if seqNum is ahead of last. */
  def isNewer(seqNum: Int, last: Int): Boolean = {
    if (last == -1) return true // First packet
    if (seqNum == last) return false // Duplicate
    ((seqNum - last) & 0x7FFFFFFF) < 0x40000000
  }
}
