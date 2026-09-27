package com.gridgame.client.audio

import org.junit.Assert._
import org.junit.Test

/**
 * A fan fires all its projectiles in one tick, and every one plays its sound: what a burst of
 * triggers of one sound does in the mixer.
 */
class VoicePoolTest {
  private val Ms = 1000L * 1000L

  /** A second of a constant level, so what the mix adds up to is easy to read. */
  private def tone(level: Short = 8000): Sound = new Sound(Array.fill(44100 * VoicePool.Channels)(level))

  private def onsetPeak(pool: VoicePool, frames: Int): Float = {
    val mix = new Array[Float](frames * VoicePool.Channels)
    pool.mixInto(mix, frames)
    mix.map(Math.abs).max
  }

  @Test def anEightWayFanStartsTwoVoicesNotEight(): Unit = {
    val pool = new VoicePool(24)
    val s = tone()
    for (k <- 0 until 8) pool.trigger(s, 1.0, 0.5f, 0.5f, k * Ms / 4, lateFrames = 400)
    assertEquals(2, pool.playing(s))
    val Seq(first, second) = pool.voicesOf(s)
    // the other six lift the first, but no further than MaxLift
    assertEquals(VoicePool.MaxLift, first.lift, 1e-4f)
    assertEquals(0.5f * VoicePool.MaxLift, first.gainL, 1e-4f)
    // and the second comes in late, so the two onsets don't land together
    assertTrue(second.pos < 0.0)
  }

  @Test def theOnsetOfAFanIsNoLongerEveryCopyAtOnce(): Unit = {
    val single = new VoicePool(24)
    single.trigger(tone(), 1.0, 0.5f, 0.5f, 0L, lateFrames = 400)
    val one = onsetPeak(single, 64)

    val pool = new VoicePool(24)
    val s = tone()
    for (k <- 0 until 8) pool.trigger(s, 1.0, 0.5f, 0.5f, 0L, lateFrames = 400)
    // eight copies on one sample were eight times the level; now it is the lifted first alone
    assertEquals(one * VoicePool.MaxLift, onsetPeak(pool, 64), one * 1e-3f)
  }

  @Test def theLateCopyIsSilentUntilItStarts(): Unit = {
    val pool = new VoicePool(4)
    val s = tone()
    pool.trigger(s, 1.0, 0.5f, 0.5f, 0L, lateFrames = 100)
    pool.trigger(s, 1.0, 0.5f, 0.5f, 1 * Ms, lateFrames = 100)
    val late = pool.voicesOf(s)(1)
    val mix = new Array[Float](200 * VoicePool.Channels)
    VoicePool.mixVoice(late, mix, 200)
    assertEquals(0f, mix(99 * VoicePool.Channels), 0f)
    assertTrue(mix(101 * VoicePool.Channels) > 0f)
  }

  @Test def triggersSpreadOutInTimeAreEachTheirOwnSound(): Unit = {
    val pool = new VoicePool(24)
    val s = tone()
    for (k <- 0 until 4) pool.trigger(s, 1.0, 0.5f, 0.5f, k * (VoicePool.CoalesceNs + Ms), lateFrames = 400)
    assertEquals(4, pool.playing(s))
    assertTrue(pool.voicesOf(s).forall(_.pos == 0.0))
  }

  @Test def theSameAttackFromTwoPlacesIsTwoSounds(): Unit = {
    // two players on opposite sides firing the same primary at once: both are heard, each where
    // it was fired, and neither lifts the other
    val pool = new VoicePool(24)
    val s = tone()
    for (_ <- 0 until 3) {
      pool.trigger(s, 1.0, 0.9f, 0.2f, 0L, 400)
      pool.trigger(s, 1.0, 0.2f, 0.9f, 0L, 400)
    }
    assertEquals(4, pool.playing(s))
    assertEquals(2, pool.voicesOf(s).count(v => v.gainL > v.gainR))
  }

  @Test def differentSoundsNeverCoalesce(): Unit = {
    val pool = new VoicePool(24)
    val (a, b) = (tone(), tone())
    for (_ <- 0 until 3) { pool.trigger(a, 1.0, 0.5f, 0.5f, 0L, 400); pool.trigger(b, 1.0, 0.5f, 0.5f, 0L, 400) }
    assertEquals(2, pool.playing(a))
    assertEquals(2, pool.playing(b))
  }
}
