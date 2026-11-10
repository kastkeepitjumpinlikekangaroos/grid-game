package com.gridgame.common

import java.util.concurrent.atomic.AtomicLong

/**
 * The clock a match's rules run on: how long a cooldown, a hold, a burn, a barrier, a trap, the
 * opening and a respawn last, and the match's own length. It is the system's clocks, unless a test
 * has put one of its own in their place ([[use]]).
 *
 * On the system's clocks a rule can only be judged by waiting for it: a test slept through a
 * shortened effect and hoped the machine kept up, nothing could be looked at on the millisecond it
 * changes, a burn's bites over five seconds or R's minute were never watched to the end, and a
 * seeded run played the same hands but not the same match, since how long its steps took was the
 * machine's to say. On a clock of the test's own ([[Manual]]) time passes only when the test says,
 * so a whole match is played in the time its ticks take to run, and plays the same every time.
 *
 * `now` is the wall clock, in ms, which the rules time nearly everything on; `nanos` is the
 * monotonic one the match's own length counts on (GameInstance: the wall clock is set back and
 * forth by the system now and then). What measures the machine rather than the game (how long a
 * tick took, how late it ran) reads the system's clocks itself.
 */
object GameClock {
  /** Where the time comes from. */
  trait Source {
    /** The wall clock: ms since the epoch. */
    def millis(): Long
    /** A clock that only runs forward: ns since some moment of its own. */
    def nanos(): Long
  }

  private object SystemClocks extends Source {
    def millis(): Long = System.currentTimeMillis()
    def nanos(): Long = System.nanoTime()
  }

  @volatile private var source: Source = SystemClocks

  def now(): Long = source.millis()

  def nanos(): Long = source.nanos()

  /** Run the rules on this clock from now on: a test's own. Every match in the process is on it,
    * so a test that takes it gives it back ([[useSystem]]) when it is done. */
  def use(clock: Source): Unit = source = clock

  /** Back on the system's clocks. */
  def useSystem(): Unit = source = SystemClocks

  /** Is this the system's clock (and not a test's)? */
  def isSystem: Boolean = source eq SystemClocks

  /**
   * A clock that stands still until it is told to move: both of its hands are one count of ms, so
   * the match's length and its cooldowns pass together. It starts at a time of day like any other
   * (never 0: "never" is 0 on many of the rules' own clocks), and only runs forward.
   */
  final class Manual(startMillis: Long = Manual.Start) extends Source {
    private val ms = new AtomicLong(startMillis)

    def millis(): Long = ms.get()
    def nanos(): Long = ms.get() * 1000000L

    /** Let this much time pass, at once. */
    def advance(byMs: Long): Unit = {
      require(byMs >= 0L, s"a clock runs forward, not $byMs ms back")
      ms.addAndGet(byMs)
    }

    /** How long since it started. */
    def elapsed: Long = ms.get() - startMillis
  }

  object Manual {
    /** 2026-01-01 00:00:00 UTC. */
    val Start: Long = 1767225600000L
  }
}
