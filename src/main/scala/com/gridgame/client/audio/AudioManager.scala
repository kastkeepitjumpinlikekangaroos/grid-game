package com.gridgame.client.audio

import com.gridgame.common.model.{CharacterDef => CharacterDefs, TrapKind}

import java.io.{ByteArrayInputStream, File, FileInputStream, InputStream}
import java.util.concurrent.ConcurrentHashMap
import java.util.prefs.Preferences
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
  // ...and +/- 1% for a sound whose pitch is the point of it (AbilitySounds.isTuned): 7% is more
  // than a semitone, and a lute, a choir or a chime that far off its note and the music's key is
  // out of tune, not varied
  private val TUNED_SPREAD = 0.01f

  private val SAMPLE_RATE = 44100f
  private val CHANNELS = 2
  private val CHUNK_FRAMES = 512                       // ~11.6ms per mix pass
  private val LINE_BUFFER_BYTES = CHUNK_FRAMES * 4 * 4 // ~46ms of slack

  private val OUTPUT_FORMAT =
    new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, SAMPLE_RATE, 16, CHANNELS, CHANNELS * 2, SAMPLE_RATE, false)

  private val voiceLock = new Object
  private val voices = new VoicePool(MAX_VOICES) // sound effects; see VoicePool.trigger
  private val musicVoice = new Voice

  private val soundCache = new ConcurrentHashMap[String, Sound]()
  private val rng = new java.util.Random()

  @volatile private var line: SourceDataLine = null
  @volatile private var running = false
  // GRIDGAME_AUDIO=off runs without sound entirely (no output line, nothing decoded) —
  // for automated runs of the client, or a machine whose audio device misbehaves.
  @volatile private var initFailed = sys.env.get("GRIDGAME_AUDIO").exists(_.equalsIgnoreCase("off"))
  @volatile private var currentMusicName: String = null

  // Best-effort like the rest of this object: a backing store this JVM cannot
  // open must not take audio down with it, so the whole lookup is guarded and a
  // null `prefs` simply means the mute state lives for this run only.
  private val MutedPrefKey = "muted"
  private val prefs: Preferences =
    try Preferences.userRoot().node("com/gridgame/client/audio")
    catch { case _: Throwable => null }
  // Read once at class-init so the very first playMenuMusic() already honours it.
  @volatile private var muted: Boolean =
    try prefs != null && prefs.getBoolean(MutedPrefKey, false)
    catch { case _: Throwable => false }

  // ── public API ──────────────────────────────────────────────────────────

  def isMuted: Boolean = muted

  /** Global mute for music and SFX, persisted across runs.
    *
    * Muting silences every voice immediately but keeps `currentMusicName`, so
    * unmuting resumes whichever track the current screen asked for instead of
    * leaving the game silent until the next screen transition. The mixer thread
    * and the decoded-sound cache are deliberately left alone — closing the line
    * would put the ~96ms `open()` stall back on the first sound after unmuting. */
  def setMuted(m: Boolean): Unit = {
    if (m == muted) return
    muted = m
    try if (prefs != null) prefs.putBoolean(MutedPrefKey, m)
    catch { case _: Throwable => () }
    if (m) {
      voiceLock.synchronized {
        voices.stopAll()
        musicVoice.active = false
        musicVoice.sound = null
      }
    } else {
      // playMusic() short-circuits on an unchanged name, so clear it first.
      val resume = currentMusicName
      currentMusicName = null
      if (resume != null) playMusic(resume)
    }
  }

  /** Flips the mute state and returns the new one, for a UI toggle button. */
  def toggleMuted(): Boolean = {
    setMuted(!muted)
    muted
  }

  /** @param characterId the shooter's character — a few projectiles are shared
    *                     by characters who should not sound alike, so this
    *                     selects between them. Pass -1 when it isn't known. */
  def playAttack(projectileType: Byte, characterId: Byte = -1,
                 distanceInCells: Float = 0f, pan: Float = 0f): Unit = {
    val name = AbilitySounds.forAttack(projectileType, characterId)
    playSfx(name, volumeAtDistance(distanceInCells), spreadOf(name), pan)
  }

  /** We cast an ability that throws nothing — a dash, a blink, a phase, a barrier, a trap — from
    * `slot` (0 is Q, 1 is E), as `characterId`: that character's own sound for it. `kind` is what
    * it is ([[AbilitySounds.DASH]]...), for a character without one. Centred: it is ours. */
  def playCast(characterId: Byte, slot: Int, kind: Int): Unit = {
    val name = AbilitySounds.forCast(characterId, slot, kind)
    playSfx(name, SFX_VOLUME, spreadOf(name))
  }

  /** Somebody else was seen to cast one of `kind`, over there: a blink out of nowhere, a phase, a
    * barrier going up. Theirs used to make no sound at all — only our own did. */
  def playCastOf(characterId: Byte, kind: Int, distanceInCells: Float, pan: Float): Unit = {
    val name = AbilitySounds.forCastOf(characterId, kind)
    playSfx(name, volumeAtDistance(distanceInCells), spreadOf(name), pan)
  }

  def playSpawn(): Unit = playSfx("spawn", SFX_VOLUME)
  def playDeath(distanceInCells: Float = 0f, pan: Float = 0f): Unit =
    playSfx("death", volumeAtDistance(distanceInCells), 0f, pan)
  /** The wall a Teams match opens with going up (a character's own barrier is a cast). */
  def playBarrierUp(): Unit = playSfx("barrier_up", SFX_VOLUME, TUNED_SPREAD)
  /** R: both abilities ready again. */
  def playCooldownReset(): Unit = playSfx("cooldown_reset", SFX_VOLUME)

  /** Somebody else setting a trap of `trapType` down over there, as `characterId` — an enemy
    * setting one nearby is a thing worth hearing — in that character's own way of setting it. (Ours
    * is a cast: [[playCast]].) */
  def playTrapPlace(characterId: Byte, trapType: Byte, distanceInCells: Float, pan: Float): Unit = {
    val name = AbilitySounds.forTrap(characterId, trapType)
    playSfx(name, volumeAtDistance(distanceInCells), spreadOf(name), pan)
  }

  private def spreadOf(name: String): Float = if (AbilitySounds.isTuned(name)) TUNED_SPREAD else PITCH_SPREAD

  /** A trap going off, by what kind it is ([[com.gridgame.common.model.TrapKind]]). A mine is not
    * here: its blast is the explosion everything else's is. */
  def playTrapSprung(kind: Byte, distanceInCells: Float, pan: Float): Unit = {
    val name = kind match {
      case TrapKind.POD => "trap_poison"
      case TrapKind.RUNE => "trap_ignite"
      case _ => "trap_snap" // jaws, and a web closing
    }
    playSfx(name, volumeAtDistance(distanceInCells), PITCH_SPREAD, pan)
  }

  /** A shot stopped on someone's barrier, over there. */
  def playBarrierBlock(distanceInCells: Float, pan: Float): Unit =
    playSfx("barrier_block", volumeAtDistance(distanceInCells), PITCH_SPREAD, pan)

  /** Local player took damage — deliberately centred, it is about you. */
  def playHitTaken(): Unit = playSfx("hit_taken", SFX_VOLUME, PITCH_SPREAD)

  /** Local player's projectile connected — the hitmarker, also centred. */
  def playHitDealt(): Unit = playSfx("hit_dealt", SFX_VOLUME, PITCH_SPREAD)

  /** Two other players traded a hit nearby. */
  def playHitOther(distanceInCells: Float, pan: Float): Unit =
    playSfx("hit_other", volumeAtDistance(distanceInCells), PITCH_SPREAD, pan)

  /** An explosive going off: its own detonation (a fire's whoomph, a mud bomb's splat), or the
    * frag's bang for anything without one (see `AbilitySounds.forBlast`). */
  def playBlast(projectileType: Byte, characterId: Byte, distanceInCells: Float, pan: Float): Unit =
    playSfx(AbilitySounds.forBlast(projectileType, characterId), volumeAtDistance(distanceInCells),
      PITCH_SPREAD, pan)

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

  /** Decodes, off the game threads, the sounds any match plays whoever is in it: the hits, the
    * deaths, the traps, the music. Safe to call more than once.
    *
    * Not the characters' own. There are over three hundred of those, 40MB decoded, and a match
    * has a dozen characters in it: they are loaded as each is seen to be in the match
    * ([[preloadCharacter]]) and let go when it is over ([[forgetCharacters]]), so the client holds
    * a match's worth, not the roster's. */
  def preload(): Unit = {
    if (!ensureStarted()) return
    loader.execute(() => EVENT_SOUNDS.foreach(loadSound))
  }

  /** `characterId` is in the match: its three attacks' sounds, and what its explosives go off as,
    * decoded on the loader's thread before any of them is needed. Asking again costs a flag read
    * (the client asks at every update about a player). A sound that is needed before the loader
    * has reached it is decoded where it is played: a few milliseconds, once. */
  def preloadCharacter(characterId: Byte): Unit = {
    val i = characterId & 0xFF
    // Only once the mixer is running (the app starts it with `preload`): a client that has played
    // nothing yet — a test's, a dev tool's — opens no audio line and decodes nothing for this
    if (wanted.get(i) != 0 || !running || !CharacterDefs.isValid(characterId)) return
    if (!wanted.compareAndSet(i, 0, 1)) return
    loader.execute(() => if (wanted.get(i) == 1) AbilitySounds.soundsOf(characterId).foreach(loadSound))
  }

  /** The match is over: its characters' sounds are let go (a voice still playing one keeps it
    * until it ends), and the next match loads its own. */
  def forgetCharacters(): Unit = {
    var i = 0
    while (i < wanted.length) { wanted.set(i, 0); i += 1 }
    soundCache.keySet().removeIf(name => !EVENT_SOUND_SET.contains(name))
  }

  /** How many sounds are held decoded, and their size in bytes: for the benches and the tests. */
  def cached: (Int, Long) = {
    var bytes = 0L
    soundCache.values().forEach(s => bytes += s.samples.length * 2L)
    (soundCache.size(), bytes)
  }

  private val wanted = new java.util.concurrent.atomic.AtomicIntegerArray(256)
  private lazy val loader: java.util.concurrent.ExecutorService =
    java.util.concurrent.Executors.newSingleThreadExecutor((r: Runnable) => {
      val t = new Thread(r, "AudioPreload")
      t.setDaemon(true)
      t.setPriority(Thread.MIN_PRIORITY)
      t
    })

  // Everything that isn't a character's own (AbilitySoundsTest checks each has a file): what
  // happens to a player, a trap going off, the plain cast of each kind, R, the music
  private[audio] val EVENT_SOUNDS = Seq(
    "spawn", "death", "hit_taken", "hit_dealt", "hit_other",
    "explosion", "dash", "teleport", "phase_shift", "barrier_up", "barrier_block",
    "trap_place", "trap_snap", "trap_poison", "trap_ignite", "cooldown_reset",
    "music_menu", "music_battle"
  )
  private val EVENT_SOUND_SET: java.util.Set[String] = new java.util.HashSet[String](java.util.Arrays.asList(EVENT_SOUNDS: _*))

  // ── playback ────────────────────────────────────────────────────────────

  private def volumeAtDistance(distanceInCells: Float): Float = {
    if (distanceInCells <= 0f) SFX_VOLUME
    else SFX_VOLUME * Math.max(0f, 1f - distanceInCells / ATTACK_FALLOFF_CELLS)
  }

  /** Told the name of every sound effect as it is asked for and near enough to be heard — before
    * the mute, the mixer or the device have any say — so a test can hear what the game would play.
    * Nothing in the game sets it. */
  @volatile private[client] var heardBy: String => Unit = null

  private def playSfx(name: String, volume: Float, pitchSpread: Float = 0f, pan: Float = 0f): Unit = {
    if (volume <= 0.01f) return
    val ear = heardBy
    if (ear != null) ear(name)
    if (muted) return
    if (!ensureStarted()) return
    val sound = loadSound(name)
    if (sound == null) return

    val rate = if (pitchSpread > 0f) 1.0 + (rng.nextDouble() * 2.0 - 1.0) * pitchSpread else 1.0
    // constant-power pan keeps perceived loudness steady across the field
    val p = Math.max(-1f, Math.min(1f, pan))
    val angle = (p + 1f) * 0.25 * Math.PI
    val gl = (Math.cos(angle) * volume * 1.35).toFloat
    val gr = (Math.sin(angle) * volume * 1.35).toFloat

    // a second copy of a sound just started comes in 6-18ms behind it (see VoicePool.trigger)
    val late = ((6 + rng.nextInt(13)) * SAMPLE_RATE / 1000f).toInt
    voiceLock.synchronized {
      voices.trigger(sound, rate, gl, gr, System.nanoTime(), late)
    }
  }

  private def playMusic(name: String): Unit = {
    if (name == currentMusicName) return
    // Remember the request even while muted, so unmuting can resume this track.
    if (muted) { currentMusicName = name; return }
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
          voices.mixInto(mix, CHUNK_FRAMES)
          VoicePool.mixVoice(musicVoice, mix, CHUNK_FRAMES)
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
