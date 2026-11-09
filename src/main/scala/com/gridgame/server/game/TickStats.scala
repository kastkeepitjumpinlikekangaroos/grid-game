package com.gridgame.server.game

import java.util.concurrent.atomic.{AtomicLong, AtomicLongArray}

/**
 * How late this process's matches run their projectile ticks, against the moment each was due
 * (`GameInstance.tickProjectiles`, every 30ms from the match's start), every match's ticks counted
 * together. A tick runs late when its thread isn't run in time (every core busy, a CPU limit's
 * throttling, a GC pause) or the one before it overran, and a late tick is a hitch in every
 * projectile its players see. A match server says how its window went in its status line
 * (cluster/MatchServer), which is what its capacity was measured by (ops/kubernetes/CLAUDE.md).
 *
 * Recording is two atomic adds and a compare: it runs on every tick of every match.
 */
object TickStats {
  /** The buckets' upper bounds, in ms: a tick 0.4ms late counts in the first, one 7ms late in 10's. */
  val BoundsMs: Array[Int] = Array(1, 2, 5, 10, 20, 50, 100)

  private val counts = new AtomicLongArray(BoundsMs.length + 1)
  private val worstNs = new AtomicLong()
  private val busyNs = new AtomicLong()

  /** A tick that started `lateNs` after it was due and took `tookNs`. */
  def record(lateNs: Long, tookNs: Long): Unit = {
    val lateMs = lateNs / 1e6
    var i = 0
    while (i < BoundsMs.length && lateMs > BoundsMs(i)) i += 1
    counts.incrementAndGet(i)
    busyNs.addAndGet(tookNs)
    var w = worstNs.get()
    while (lateNs > w && !worstNs.compareAndSet(w, lateNs)) w = worstNs.get()
  }

  /** The ticks since the last `take`, and a new window begun. */
  def take(): Window = {
    val c = Array.tabulate(BoundsMs.length + 1)(i => counts.getAndSet(i, 0L))
    Window(c, worstNs.getAndSet(0L) / 1e6, busyNs.getAndSet(0L) / 1e6)
  }

  /** @param counts how many ticks fell in each bucket (the last: over the last bound)
    * @param worstMs the latest of them
    * @param busyMs the ticks' own running time, all together */
  final case class Window(counts: Array[Long], worstMs: Double, busyMs: Double) {
    def ticks: Long = counts.sum

    /** The share of ticks no more than `ms` late (`ms` one of the bounds). */
    def within(ms: Int): Double = {
      val i = BoundsMs.indexOf(ms)
      require(i >= 0, s"$ms isn't one of the bounds")
      if (ticks == 0) 1.0 else counts.take(i + 1).sum.toDouble / ticks
    }

    /** The bound the `p`th percentile of ticks were within (Int.MaxValue: later than every bound). */
    def percentileMs(p: Double): Int = {
      val n = ticks
      if (n == 0) return 0
      val want = math.ceil(p / 100.0 * n).toLong
      var seen = 0L
      var i = 0
      while (i < counts.length) {
        seen += counts(i)
        if (seen >= want) return if (i < BoundsMs.length) BoundsMs(i) else Int.MaxValue
        i += 1
      }
      Int.MaxValue
    }

    /** A line for a log: "p50 <1ms, p99 <5ms, worst 7.2ms, 99.1% within 2ms". */
    def describe: String = {
      def bound(ms: Int) = if (ms == Int.MaxValue) s">${BoundsMs.last}ms" else s"<=${ms}ms"
      if (ticks == 0) "no ticks"
      else f"p50 ${bound(percentileMs(50))}, p99 ${bound(percentileMs(99))}, p99.9 ${bound(percentileMs(99.9))}, " +
        f"worst $worstMs%.1fms late"
    }
  }
}
