package com.gridgame.client.audio

/** Decoded, ready-to-mix interleaved stereo samples. */
private[audio] final class Sound(val samples: Array[Short]) {
  val frames: Int = samples.length / VoicePool.Channels
}

/** One playing sound. Mutated only under AudioManager's voice lock. */
private[audio] final class Voice {
  var sound: Sound = null
  var pos: Double = 0.0
  var rate: Double = 1.0
  var gainL: Float = 0f
  var gainR: Float = 0f
  var loop: Boolean = false
  var active: Boolean = false
  var startedNs: Long = 0L
  var lift: Float = 1f
}

/** The mixer's sound-effect voices: which are playing, and mixing them into a buffer.
  *
  * Kept apart from AudioManager, which owns the output line, so what a burst of triggers does can
  * be tested without a sound device. Not thread-safe: AudioManager calls it under its lock. */
private[audio] final class VoicePool(val size: Int) {
  import VoicePool._

  private val voices: Array[Voice] = Array.fill(size)(new Voice)

  /** Starts `sound`, unless copies of it have only just started from the same place.
    *
    * A fan fires all its projectiles in one tick and each arrives as its own spawn, so an eight-way
    * fan used to start eight copies of one sound on the same sample: at the onset they summed to
    * eight times its level, past what the output's soft clip can round off, and took a third of
    * the voices. Now, counting the copies started within `CoalesceNs` and panned the same way
    * (a fan comes from one caster; two players firing the same attack at once are two sounds):
    *  - none: it plays;
    *  - one: it plays `lateFrames` late, so the two onsets do not land together;
    *  - two or more: nothing more starts, and the first copy is lifted by `Lift`, up to `MaxLift`
    *    in all — a bigger fan is louder, but not eight times louder.
    *
    * @return whether a voice was started */
  def trigger(sound: Sound, rate: Double, gainL: Float, gainR: Float, nowNs: Long, lateFrames: Int): Boolean = {
    val balance = balanceOf(gainL, gainR)
    var first: Voice = null
    var copies = 0
    var i = 0
    while (i < size) {
      val v = voices(i)
      if (v.active && (v.sound eq sound) && nowNs - v.startedNs < CoalesceNs &&
          Math.abs(balanceOf(v.gainL, v.gainR) - balance) < SamePlace) {
        copies += 1
        if (first == null || v.startedNs < first.startedNs) first = v
      }
      i += 1
    }
    if (copies >= MaxCopies) {
      val g = Math.min(Lift, MaxLift / first.lift)
      if (g > 1f) {
        first.gainL *= g
        first.gainR *= g
        first.lift *= g
      }
      return false
    }
    i = 0
    while (i < size) {
      val v = voices(i)
      if (!v.active) {
        v.sound = sound
        v.pos = if (copies > 0) -lateFrames * rate else 0.0
        v.rate = rate
        v.gainL = gainL
        v.gainR = gainR
        v.loop = false
        v.active = true
        v.startedNs = nowNs
        v.lift = 1f
        return true
      }
      i += 1
    }
    false // all voices busy — dropping is correct here, the mix is already saturated
  }

  def mixInto(mix: Array[Float], frames: Int): Unit = {
    var i = 0
    while (i < size) {
      mixVoice(voices(i), mix, frames)
      i += 1
    }
  }

  def stopAll(): Unit = {
    var i = 0
    while (i < size) {
      val v = voices(i)
      v.active = false
      v.sound = null
      v.pos = 0.0
      i += 1
    }
  }

  /** How many voices are playing `sound`, for tests. */
  def playing(sound: Sound): Int = voices.count(v => v.active && (v.sound eq sound))

  /** The voices playing `sound`, oldest first, for tests. */
  def voicesOf(sound: Sound): Seq[Voice] =
    voices.filter(v => v.active && (v.sound eq sound)).sortBy(_.startedNs).toSeq
}

private[audio] object VoicePool {
  val Channels = 2
  /** How close together two triggers of one sound are one event (a fan's spawns arrive in one tick). */
  val CoalesceNs: Long = 40L * 1000 * 1000
  val MaxCopies = 2
  val Lift = 1.15f
  val MaxLift = 1.8f
  /** How close two copies' left/right balance is to count as one place (a lift keeps it). */
  val SamePlace = 0.06f

  private def balanceOf(gainL: Float, gainR: Float): Float = gainR / (gainL + gainR + 1e-9f)

  /** Mixes one voice into the buffer with linear-interpolated resampling. */
  def mixVoice(v: Voice, mix: Array[Float], frames: Int): Unit = {
    if (!v.active || v.sound == null) return
    val s = v.sound.samples
    val soundFrames = v.sound.frames
    var pos = v.pos
    val rate = v.rate
    val gl = v.gainL
    val gr = v.gainR
    var i = 0
    while (i < frames) {
      if (pos < 0.0) {
        pos += rate // a copy started late (see `trigger`): silent until it begins
      } else {
        if (pos >= soundFrames - 1) {
          if (v.loop) pos -= (soundFrames - 1)
          else {
            v.active = false
            v.sound = null
            v.pos = 0.0
            return
          }
        }
        val idx = pos.toInt
        val frac = (pos - idx).toFloat
        val j = idx * Channels
        val l0 = s(j).toFloat
        val r0 = s(j + 1).toFloat
        val l1 = s(j + Channels).toFloat
        val r1 = s(j + Channels + 1).toFloat
        val ls = (l0 + (l1 - l0) * frac) / 32768f
        val rs = (r0 + (r1 - r0) * frac) / 32768f
        mix(i * Channels) += ls * gl
        mix(i * Channels + 1) += rs * gr
        pos += rate
      }
      i += 1
    }
    v.pos = pos
  }
}
