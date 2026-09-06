package com.gridgame.client.audio

import java.io.{ByteArrayInputStream, File, FileInputStream, InputStream}
import java.util.concurrent.ConcurrentHashMap
import javax.sound.sampled._

/** Loads and plays the procedurally generated WAV assets under sounds/
  * (see scripts/generate_sounds.py).
  *
  * Mixing is done in software onto a single `SourceDataLine` rather than by
  * opening a `javax.sound.sampled.Clip` per playback. That matters for frame
  * rate: a Clip per sound measured at ~6.4ms of CPU each (native line
  * acquisition + buffer copy + a thread per Clip), which is 40-60% of a core
  * during a firefight and left ~32 audio threads competing with the render
  * thread — and `getClip()/open()` was observed stalling for up to 96ms, which
  * is a visible hitch when an ability fires it from the input handler on the
  * render thread. Triggering a sound here is just claiming a voice slot: no
  * allocation, no native call, no thread.
  *
  * Every call is best-effort: audio failures (no mixer, unsupported format,
  * etc.) are swallowed so the game runs identically with sound hardware
  * unavailable, matching the telemetry no-op fallback philosophy used elsewhere
  * in the client. */
object AudioManager {
  private val MUSIC_VOLUME = 0.35f
  private val SFX_VOLUME = 0.8f
  private val MAX_VOICES = 24
  private val ATTACK_FALLOFF_CELLS = 35f
  // +/- 7% playback-rate jitter so repeated shots don't sound machine-gun identical
  private val PITCH_SPREAD = 0.07f

  private val SAMPLE_RATE = 44100f
  private val CHANNELS = 2
  private val CHUNK_FRAMES = 512                       // ~11.6ms per mix pass
  private val LINE_BUFFER_BYTES = CHUNK_FRAMES * 4 * 4 // ~46ms of slack

  private val OUTPUT_FORMAT =
    new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, SAMPLE_RATE, 16, CHANNELS, CHANNELS * 2, SAMPLE_RATE, false)

  /** Decoded, ready-to-mix interleaved stereo samples. */
  private final class Sound(val samples: Array[Short]) {
    val frames: Int = samples.length / CHANNELS
  }

  /** One playing sound. Mutated only under `voiceLock`. */
  private final class Voice {
    var sound: Sound = null
    var pos: Double = 0.0
    var rate: Double = 1.0
    var gainL: Float = 0f
    var gainR: Float = 0f
    var loop: Boolean = false
    var active: Boolean = false
  }

  private val voiceLock = new Object
  private val voices: Array[Voice] = Array.fill(MAX_VOICES)(new Voice)
  private val musicVoice = new Voice

  private val soundCache = new ConcurrentHashMap[String, Sound]()
  private val rng = new java.util.Random()

  @volatile private var line: SourceDataLine = null
  @volatile private var running = false
  @volatile private var initFailed = false
  @volatile private var currentMusicName: String = null

  // ── public API ──────────────────────────────────────────────────────────

  def playAttack(projectileType: Byte, distanceInCells: Float = 0f, pan: Float = 0f): Unit =
    playSfx(AbilitySounds.forProjectileType(projectileType), volumeAtDistance(distanceInCells), PITCH_SPREAD, pan)

  def playSpawn(): Unit = playSfx("spawn", SFX_VOLUME)
  def playDeath(distanceInCells: Float = 0f, pan: Float = 0f): Unit =
    playSfx("death", volumeAtDistance(distanceInCells), 0f, pan)
  def playDash(): Unit = playSfx("dash", SFX_VOLUME, PITCH_SPREAD)
  def playTeleport(): Unit = playSfx("teleport", SFX_VOLUME, PITCH_SPREAD)
  def playPhaseShift(): Unit = playSfx("phase_shift", SFX_VOLUME)

  /** Local player took damage — deliberately centred, it is about you. */
  def playHitTaken(): Unit = playSfx("hit_taken", SFX_VOLUME, PITCH_SPREAD)

  /** Local player's projectile connected — the hitmarker, also centred. */
  def playHitDealt(): Unit = playSfx("hit_dealt", SFX_VOLUME, PITCH_SPREAD)

  /** Two other players traded a hit nearby. */
  def playHitOther(distanceInCells: Float, pan: Float): Unit =
    playSfx("hit_other", volumeAtDistance(distanceInCells), PITCH_SPREAD, pan)

  def playExplosion(distanceInCells: Float, pan: Float): Unit =
    playSfx("explosion", volumeAtDistance(distanceInCells), PITCH_SPREAD, pan)

  def playMenuMusic(): Unit = playMusic("music_menu")
  def playBattleMusic(): Unit = playMusic("music_battle")

  def stopMusic(): Unit = voiceLock.synchronized {
    musicVoice.active = false
    musicVoice.sound = null
    currentMusicName = null
  }

  /** Stops the mixer and releases the output line. */
  def shutdown(): Unit = {
    running = false
    stopMusic()
    val l = line
    if (l != null) {
      try {
        l.stop()
        l.close()
      } catch { case _: Exception => () }
    }
    line = null
  }

  /** Decodes every sound off the game threads so no disk I/O or WAV parsing
    * ever happens mid-match. Safe to call more than once. */
  def preload(): Unit = {
    if (!ensureStarted()) return
    val t = new Thread(new Runnable {
      def run(): Unit = {
        AbilitySounds.allSoundNames.foreach(loadSound)
        EVENT_SOUNDS.foreach(loadSound)
      }
    }, "AudioPreload")
    t.setDaemon(true)
    t.setPriority(Thread.MIN_PRIORITY)
    t.start()
  }

  private val EVENT_SOUNDS = Seq(
    "spawn", "death", "hit_taken", "hit_dealt", "hit_other",
    "explosion", "dash", "teleport", "phase_shift", "music_menu", "music_battle"
  )

  // ── playback ────────────────────────────────────────────────────────────

  private def volumeAtDistance(distanceInCells: Float): Float = {
    if (distanceInCells <= 0f) SFX_VOLUME
    else SFX_VOLUME * Math.max(0f, 1f - distanceInCells / ATTACK_FALLOFF_CELLS)
  }

  private def playSfx(name: String, volume: Float, pitchSpread: Float = 0f, pan: Float = 0f): Unit = {
    if (volume <= 0.01f) return
    if (!ensureStarted()) return
    val sound = loadSound(name)
    if (sound == null) return

    val rate = if (pitchSpread > 0f) 1.0 + (rng.nextDouble() * 2.0 - 1.0) * pitchSpread else 1.0
    // constant-power pan keeps perceived loudness steady across the field
    val p = Math.max(-1f, Math.min(1f, pan))
    val angle = (p + 1f) * 0.25 * Math.PI
    val gl = (Math.cos(angle) * volume * 1.35).toFloat
    val gr = (Math.sin(angle) * volume * 1.35).toFloat

    voiceLock.synchronized {
      var i = 0
      while (i < MAX_VOICES) {
        val v = voices(i)
        if (!v.active) {
          v.sound = sound
          v.pos = 0.0
          v.rate = rate
          v.gainL = gl
          v.gainR = gr
          v.loop = false
          v.active = true
          return
        }
        i += 1
      }
      // All voices busy — dropping is correct here, the mix is already saturated.
    }
  }

  private def playMusic(name: String): Unit = {
    if (name == currentMusicName) return
    if (!ensureStarted()) return
    val sound = loadSound(name)
    if (sound == null) return
    voiceLock.synchronized {
      musicVoice.sound = sound
      musicVoice.pos = 0.0
      musicVoice.rate = 1.0
      musicVoice.gainL = MUSIC_VOLUME
      musicVoice.gainR = MUSIC_VOLUME
      musicVoice.loop = true
      musicVoice.active = true
      currentMusicName = name
    }
  }

  // ── mixer ───────────────────────────────────────────────────────────────

  private def ensureStarted(): Boolean = {
    if (running && line != null) return true
    if (initFailed) return false // no audio device: never retry, opening a line is costly
    synchronized {
      if (running && line != null) return true
      if (initFailed) return false
      try {
        val info = new DataLine.Info(classOf[SourceDataLine], OUTPUT_FORMAT)
        val l = AudioSystem.getLine(info).asInstanceOf[SourceDataLine]
        l.open(OUTPUT_FORMAT, LINE_BUFFER_BYTES)
        l.start()
        line = l
        running = true
        val t = new Thread(new Runnable { def run(): Unit = mixLoop() }, "AudioMixer")
        t.setDaemon(true)
        t.start()
        true
      } catch {
        case _: Exception =>
          running = false
          initFailed = true
          false
      }
    }
  }

  private def mixLoop(): Unit = {
    val mix = new Array[Float](CHUNK_FRAMES * CHANNELS)
    val bytes = new Array[Byte](CHUNK_FRAMES * CHANNELS * 2)
    while (running) {
      try {
        java.util.Arrays.fill(mix, 0f)
        voiceLock.synchronized {
          var i = 0
          while (i < MAX_VOICES) {
            mixVoice(voices(i), mix)
            i += 1
          }
          mixVoice(musicVoice, mix)
        }
        var i = 0
        while (i < mix.length) {
          // soft clip: summed voices can exceed unity, and tanh-ish saturation
          // is far less objectionable than hard clipping
          val x = mix(i)
          val y = if (x > 1f || x < -1f) (x / (1f + Math.abs(x))) * 1.6f else x * (1f - 0.18f * x * x)
          val s = Math.max(-32767f, Math.min(32767f, y * 32767f)).toInt
          bytes(i * 2) = (s & 0xFF).toByte
          bytes(i * 2 + 1) = ((s >> 8) & 0xFF).toByte
          i += 1
        }
        val l = line
        if (l == null) return
        l.write(bytes, 0, bytes.length) // blocks until the device drains: paces the loop
      } catch {
        case _: InterruptedException => return
        case _: Exception => return
      }
    }
  }

  /** Mixes one voice into the buffer with linear-interpolated resampling. */
  private def mixVoice(v: Voice, mix: Array[Float]): Unit = {
    if (!v.active || v.sound == null) return
    val s = v.sound.samples
    val frames = v.sound.frames
    var pos = v.pos
    val rate = v.rate
    val gl = v.gainL
    val gr = v.gainR
    var i = 0
    while (i < CHUNK_FRAMES) {
      if (pos >= frames - 1) {
        if (v.loop) pos -= (frames - 1)
        else {
          v.active = false
          v.sound = null
          v.pos = 0.0
          return
        }
      }
      val idx = pos.toInt
      val frac = (pos - idx).toFloat
      val j = idx * CHANNELS
      val l0 = s(j).toFloat
      val r0 = s(j + 1).toFloat
      val l1 = s(j + CHANNELS).toFloat
      val r1 = s(j + CHANNELS + 1).toFloat
      val ls = (l0 + (l1 - l0) * frac) / 32768f
      val rs = (r0 + (r1 - r0) * frac) / 32768f
      mix(i * CHANNELS) += ls * gl
      mix(i * CHANNELS + 1) += rs * gr
      pos += rate
      i += 1
    }
    v.pos = pos
  }

  // ── loading ─────────────────────────────────────────────────────────────

  private def loadSound(name: String): Sound = {
    val cached = soundCache.get(name)
    if (cached != null) return cached
    val decoded = decode(name)
    if (decoded == null) return null
    val existing = soundCache.putIfAbsent(name, decoded)
    if (existing != null) existing else decoded
  }

  private def decode(name: String): Sound = {
    try {
      val bytes = loadResourceBytes(s"sounds/$name.wav")
      if (bytes == null) return null
      var ais = AudioSystem.getAudioInputStream(new ByteArrayInputStream(bytes))
      if (!ais.getFormat.matches(OUTPUT_FORMAT)) {
        ais = AudioSystem.getAudioInputStream(OUTPUT_FORMAT, ais)
      }
      val pcm =
        try ais.readAllBytes()
        finally ais.close()
      val samples = new Array[Short](pcm.length / 2)
      var i = 0
      while (i < samples.length) {
        samples(i) = (((pcm(i * 2 + 1) & 0xFF) << 8) | (pcm(i * 2) & 0xFF)).toShort
        i += 1
      }
      if (samples.length < CHANNELS * 2) null else new Sound(samples)
    } catch {
      case _: Exception => null
    }
  }

  private def loadResourceBytes(relativePath: String): Array[Byte] = {
    val stream = resolveResourceStream(relativePath)
    if (stream == null) return null
    try {
      stream.readAllBytes()
    } finally {
      stream.close()
    }
  }

  private def resolveResourceStream(relativePath: String): InputStream = {
    val direct = new File(relativePath)
    if (direct.exists()) return new FileInputStream(direct)

    val buildWorkDir = System.getenv("BUILD_WORKING_DIRECTORY")
    if (buildWorkDir != null) {
      val fromWorkDir = new File(buildWorkDir, relativePath)
      if (fromWorkDir.exists()) return new FileInputStream(fromWorkDir)
    }

    getClass.getClassLoader.getResourceAsStream(relativePath)
  }
}
