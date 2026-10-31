package com.gridgame.client.diagnostics

import com.gridgame.client.net.{PacketTap, SettingsFiles}
import com.gridgame.common.BuildInfo
import com.gridgame.common.diagnostics.Recording
import com.gridgame.common.diagnostics.Recording.{IncidentKind, NoticedKind}
import com.gridgame.common.protocol.{GameEvent, LobbyAction, PacketType}
import com.google.gson.{GsonBuilder, JsonArray, JsonElement, JsonObject}

import java.io.{PrintWriter, StringWriter}
import java.nio.channels.{FileChannel, FileLock, OverlappingFileLockException}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths, StandardOpenOption}
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.ZoneOffset
import java.util.concurrent.{ConcurrentHashMap, CopyOnWriteArrayList, Executors}
import scala.jdk.CollectionConverters._

/** A picture of a frame the match's window drew, kept a moment before a mark or an incident: where it
  * is on disk, and when it was drawn (microseconds since the recording began). */
final case class FramePicture(path: Path, atMicros: Long)

/** A frame as the window's history kept it (gl/FrameHistory): when it was drawn (System.nanoTime), and
  * the JPEG. */
final case class PastFrame(nanos: Long, jpeg: Array[Byte])

/** A moment the player marked in a match (F8): when, its picture once it is written, and the pictures
  * of the seconds before it. */
final case class MarkedMoment(number: Int, atMicros: Long, screenshot: Path, frames: Seq[FramePicture] = Nil)

/** Something that went wrong, as a report tells of it (IncidentKind), and the pictures of the seconds
  * before it, for one the player would have seen (a freeze, the match's window failing). */
final case class IncidentNote(kind: Int, atMicros: Long, detail: String, frames: Seq[FramePicture] = Nil)

/** Something the client noticed for itself (Recording.NoticedKind) that a player who didn't mark the
  * moment may mean: when it began, its numbers, and a few words (the killer's name). */
final case class NoticedMoment(kind: Int, atMicros: Long, a: Int, b: Int, detail: String)

/**
 * One match's recording and what is known of it: where its files are, the moments marked in it, what
 * went wrong in it, and what the client learnt of it as it went (the map, the mode, who it played).
 */
final class MatchRecord private[diagnostics] (val dir: Path, val startedAtMs: Long, private[diagnostics] val recorder: MatchRecorder) {
  val marks = new CopyOnWriteArrayList[MarkedMoment]()
  val incidents = new CopyOnWriteArrayList[IncidentNote]()
  val noticed = new CopyOnWriteArrayList[NoticedMoment]()
  /** How long the match went on, in the recording's microseconds (once it has ended). */
  @volatile var durationMicros: Long = 0L
  private val info = new JsonObject
  @volatile var endedAtMs: Long = 0L
  /** How it ended: "playing" until it does, then "finished", "left", "error", "disconnected", "closed". */
  @volatile var ended: String = "playing"
  /** Taken for a report being written: kept when the next match starts, until it is let go. */
  @volatile private[diagnostics] var pinned = false

  def segments: Seq[Path] = recorder.segments
  /** Bytes on disk, compressed; and the records' own bytes before that. */
  def bytes: Long = recorder.bytesWritten
  def bytesRecorded: Long = recorder.bytesRecorded
  def isRecording: Boolean = ended == "playing"

  /** What has been learnt of the match, as report.json's "match" has it. */
  def matchInfo: JsonObject = info.synchronized(info.deepCopy())

  private[diagnostics] def note(key: String, value: JsonElement): Unit = info.synchronized(info.add(key, value))
}

/**
 * The client's diagnostics (design/diagnostics.md): its log on disk, the match it is recording, the
 * moments the player marks, what went wrong, and the run before this one if it never closed. One for
 * the app, as there is one log and one player; `install` sets it up as the client starts, before
 * anything prints, and every part of the client reaches it from here.
 *
 * Its files are the player's own, in their settings folder (`diagnostics/`, readable by them alone):
 *
 * {{{
 * client.log, client.1.log, client.2.log   this run's log and the two before
 * running.properties                       this run is under way (RunMarker)
 * match/<start>/                           a match's recording (MatchRecorder), match.json, its marks' pictures
 * outbox/<report>.zip, .json               reports waiting to go, and what became of the ones sent
 * }}}
 *
 * A second client on the same machine (GRIDGAME_LOGIN_FILE) finds the folder taken and keeps its own
 * under `instances/<pid>/`. GRIDGAME_DIAGNOSTICS_DIR puts it all somewhere else.
 */
object Diagnostics {
  /** Moments a match takes marks of: plenty to show a bug, and a ceiling on a report's pictures. */
  val MaxMarks = 10
  /** Moments the client notices for itself, at most, in a match: a link that goes bad notices a lot. */
  val MaxNoticed = 300
  /** The same kind noticed again within this long is left out (a death never is). */
  val NoticedQuietMs = 2000L
  /** The same kind of incident counted rather than noted again within this long (a test's is shorter). */
  @volatile private[client] var incidentQuietMs = 5000L

  @volatile private var installed = false
  @volatile private var _folder: Path = _
  @volatile private var _log: LogFile = _
  @volatile private var marker: RunMarker = _
  @volatile private var _previousRun: Option[PreviousRun] = None
  @volatile private var args: Seq[String] = Nil
  private var heldLock: (FileChannel, FileLock) = _
  private var watchdog: Watchdog = _

  /** What the menus measured of the screens, and the UI's language, for a report's "system". */
  @volatile var screens: Seq[String] = Nil
  @volatile var language: String = ""

  @volatile private var _graphics: JsonObject = _

  /** The match being recorded, or null. */
  @volatile private var current: MatchRecord = _
  /** The last match recorded, kept for a report until the next starts or the client closes. */
  @volatile private var last: MatchRecord = _

  /** Where the files are (null before `install`). */
  def folder: Path = _folder
  def log: LogFile = _log
  def isInstalled: Boolean = installed

  /** The run before this one, if it never closed properly; until a report of it is sent or turned down. */
  def previousRun: Option[PreviousRun] = _previousRun

  private lazy val gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

  // ── Starting up ────────────────────────────────────────────────────────

  /**
   * Set it all up: the folder, the log (and everything printed from now on into it, if `teeConsole`), the
   * run before this one, the marker that says this one is running, and a handler for the exceptions
   * nothing catches. Done once; a failure leaves the client running without it.
   */
  def install(args: Seq[String], dir: Option[Path] = None, teeConsole: Boolean = true): Unit = synchronized {
    if (installed) return
    try {
      val base = dir.orElse(sys.env.get("GRIDGAME_DIAGNOSTICS_DIR").filter(_.nonEmpty).map(Paths.get(_)))
        .getOrElse(SettingsFiles.folder().resolve("diagnostics"))
      SettingsFiles.createPrivateDirectory(base)
      _folder = claim(base)
      _outbox = new ReportOutbox(_folder.resolve("outbox"))
      this.args = args
      val logFile = new LogFile(_folder)
      logFile.open()
      if (teeConsole) logFile.tee()
      logFile.onLine = line => { val c = current; if (c != null) c.recorder.text(Recording.Log, line) }
      _log = logFile

      marker = new RunMarker(_folder)
      val leftOver = marker.leftOver()
      val matches = matchFolders()
      _previousRun = leftOver.map(m => RunMarker.previousRun(m, logFile.previous, matches.lastOption, Seq(_folder)))
      // Recordings a run closed properly didn't take down (it was killed as it closed); the crashed
      // run's match is kept for its report
      matches.filterNot(d => _previousRun.exists(_.recording.contains(d))).foreach(deleteTree)
      marker.start()
      installed = true

      println(s"Grid Game ${BuildInfo.describe}")
      println(s"System: ${SystemInfo.describe(args)}")
      println(s"Diagnostics: ${_folder}")
      _previousRun.foreach { r =>
        println(s"Diagnostics: the last run (pid ${r.pid}, started ${iso(r.startedAtMs)}, last seen ${iso(r.lastSeenAtMs)}) " +
          s"didn't close properly${if (r.inMatch) ", in a match" else ""}" +
          r.hsErr.map(f => s"; the JVM's crash file is $f").getOrElse(""))
      }

      Thread.setDefaultUncaughtExceptionHandler((t: Thread, e: Throwable) => {
        System.err.println(s"Uncaught exception in thread ${t.getName}:")
        e.printStackTrace()
        incident(IncidentKind.Error, s"${t.getName}: ${stackTrace(e)}")
      })
      Runtime.getRuntime.addShutdownHook(new Thread(() => shutdown(), "DiagnosticsShutdown"))
    } catch {
      case e: Exception => System.err.println(s"Diagnostics: not available ($e)")
    }
  }

  /** The folder `base`, if no other client on this machine has it; else one of this client's own. */
  private def claim(base: Path): Path = {
    val ch = FileChannel.open(base.resolve(".lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE)
    val lock = try ch.tryLock() catch { case _: OverlappingFileLockException => null }
    if (lock != null) {
      heldLock = (ch, lock)
      // Folders of other clients that have gone: nothing can send them now
      val instances = base.resolve("instances")
      if (Files.isDirectory(instances)) list(instances).foreach { d =>
        val alive = try ProcessHandle.of(d.getFileName.toString.toLong).isPresent catch { case _: NumberFormatException => false }
        if (!alive) deleteTree(d)
      }
      base
    } else {
      ch.close()
      val own = base.resolve("instances").resolve(ProcessHandle.current().pid().toString)
      Files.createDirectories(own)
      own
    }
  }

  /** Keep an eye on the JavaFX thread (Watchdog): `post` is Platform.runLater. */
  def startWatchdog(post: Runnable => Unit): Unit = synchronized {
    if (!installed || watchdog != null) return
    watchdog = new Watchdog(post,
      onFreeze = (ms, threads) => {
        System.err.println(s"Diagnostics: the game has not answered for ${ms}ms. Every thread:\n$threads")
        // What was on the screen as it froze: the window's history, kept off the frozen thread
        incident(IncidentKind.Freeze, s"not answering for ${ms}ms\n$threads", pastFrames())
      },
      onThaw = ms => println(s"Diagnostics: the game answered again after ${ms}ms"),
      tick = () => { val m = marker; if (m != null) m.touch(current != null) })
    watchdog.start()
  }

  // ── The machine ────────────────────────────────────────────────────────

  /** The match window's OpenGL, once it has made its context. */
  def noteGraphics(vendor: String, renderer: String, version: String, glsl: String): Unit = {
    val g = new JsonObject
    g.addProperty("vendor", vendor)
    g.addProperty("renderer", renderer)
    g.addProperty("version", version)
    g.addProperty("glsl", glsl)
    val old = _graphics
    if (old != null) for (k <- Seq("quality", "auto", "window", "contentScale") if old.has(k)) g.add(k, old.get(k))
    _graphics = g
    println(s"Graphics: $renderer ($vendor), OpenGL $version, GLSL $glsl")
    event(s"graphics: $renderer ($vendor), OpenGL $version")
  }

  /** The match window's size and scale, and the quality it draws at. */
  def noteWindow(width: Int, height: Int, contentScale: Double, quality: String, auto: Boolean): Unit = {
    val g = Option(_graphics).map(_.deepCopy()).getOrElse(new JsonObject)
    g.addProperty("window", s"${width}x$height")
    g.addProperty("contentScale", contentScale)
    g.addProperty("quality", quality)
    g.addProperty("auto", auto)
    _graphics = g
  }

  def graphics: JsonObject = Option(_graphics).map(_.deepCopy()).orNull

  def system: JsonObject = SystemInfo.machine(args, screens, language)

  // ── The match ──────────────────────────────────────────────────────────

  /** The match being recorded's recorder, or null: what the window hands its frames to. */
  def recorder: MatchRecorder = { val c = current; if (c == null) null else c.recorder }

  /** The match being recorded, or else the last one recorded (kept for its report), if any. */
  def lastMatch: Option[MatchRecord] = Option(current).orElse(Option(last))

  /** A match is starting (the server's GAME_STARTING): its recording begins, and the one before it is let go. */
  def matchStarting(): Unit = synchronized {
    if (!installed) return
    // One still recording never heard its end (a drop, a missed SCORE_END): it ends here
    val c = current
    if (c != null) { current = null; end(c, "replaced") }
    // The last match goes, unless a report is being made of it
    Seq(last, c).filter(r => r != null && !r.pinned).foreach(r => deleteTree(r.dir))
    last = null
    try {
      val started = System.currentTimeMillis()
      val dir = _folder.resolve("match").resolve(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").withZone(ZoneOffset.UTC).format(Instant.ofEpochMilli(started)))
      val record = new MatchRecord(dir, started, new MatchRecorder(dir, started))
      record.note("startedAt", new com.google.gson.JsonPrimitive(iso(started)))
      lastHeardNanos = 0L
      noticedLast.clear()
      current = record
      val meta = new JsonObject
      meta.add("client", SystemInfo.client)
      meta.add("system", system)
      Option(_graphics).foreach(g => meta.add("graphics", g))
      record.recorder.text(Recording.Meta, gson.toJson(meta))
      writeMatchInfo(record)
      val m = marker
      if (m != null) m.touch(inMatch = true)
      println(s"Diagnostics: recording the match into $dir")
    } catch {
      case e: Exception => System.err.println(s"Diagnostics: can't record the match ($e)")
    }
  }

  /** The match is over for us (`how`: finished, left, error, disconnected, closed): its recording stops,
    * and it is kept for a report until the next match starts or the client closes. */
  def matchEnded(how: String): Unit = synchronized {
    val c = current
    if (c == null) return
    current = null
    end(c, how)
    val l = last
    if (l != null && (l ne c) && !l.pinned) deleteTree(l.dir)
    last = c
    val m = marker
    if (m != null) m.touch(inMatch = false)
  }

  private def end(c: MatchRecord, how: String): Unit = {
    c.ended = how
    c.endedAtMs = System.currentTimeMillis()
    c.durationMicros = c.recorder.elapsedMicros
    c.note("ended", new com.google.gson.JsonPrimitive(how))
    c.note("endedAt", new com.google.gson.JsonPrimitive(iso(c.endedAtMs)))
    c.recorder.text(Recording.Event, s"match ended: $how")
    c.recorder.finish()
    writeMatchInfo(c)
    println(s"Diagnostics: the match's recording stopped ($how): ${c.segments.size} segment(s), ${c.bytes / 1024}KB")
  }

  /** Something learnt about the match being recorded: kept for its report, and in the recording. */
  def noteMatch(key: String, value: JsonElement): Unit = {
    val c = current
    if (c == null) return
    c.note(key, value)
    c.recorder.text(Recording.Meta, s"""{"$key": ${gson.toJson(value)}}""")
    writeMatchInfo(c)
  }

  def noteMatch(key: String, value: String): Unit = noteMatch(key, new com.google.gson.JsonPrimitive(value))
  def noteMatch(key: String, value: Number): Unit = noteMatch(key, new com.google.gson.JsonPrimitive(value))
  def noteMatch(key: String, value: Boolean): Unit = noteMatch(key, new com.google.gson.JsonPrimitive(value))

  /** The match's info beside its recording, so a run after a crash can say what the match was. */
  private def writeMatchInfo(c: MatchRecord): Unit =
    try Files.writeString(c.dir.resolve("match.json"), gson.toJson(c.matchInfo), StandardCharsets.UTF_8)
    catch { case _: Exception => }

  /** What a crashed run's recording said of its match. */
  def matchInfoIn(dir: Path): JsonObject =
    try com.google.gson.JsonParser.parseString(Files.readString(dir.resolve("match.json"), StandardCharsets.UTF_8)).getAsJsonObject
    catch { case _: Exception => new JsonObject }

  /** Something the client did or saw, for the recording ("window: 2880x1800"). */
  def event(text: String): Unit = { val c = current; if (c != null) c.recorder.text(Recording.Event, text) }

  private val incidentLast = new ConcurrentHashMap[Int, java.lang.Long]()
  private val incidentQuiet = new ConcurrentHashMap[Int, Integer]()

  /**
   * Something went wrong (IncidentKind): noted for the match's report (and in its recording), whether or
   * not a match is under way. The same kind again within incidentQuietMs is only counted, and the count
   * goes with the next one noted.
   */
  def incident(kind: Int, detail: String, frames: Seq[PastFrame] = Nil): Unit = {
    val now = System.currentTimeMillis()
    val lastAt = incidentLast.get(kind)
    if (lastAt != null && now - lastAt < incidentQuietMs) {
      incidentQuiet.merge(kind, 1, (a: Integer, b: Integer) => a + b)
      return
    }
    incidentLast.put(kind, now)
    val quiet = Option(incidentQuiet.remove(kind)).map(_.intValue).getOrElse(0)
    val text = if (quiet > 0) s"$detail\n(and $quiet more like it before this)" else detail
    val c = current
    if (c != null) {
      val index = c.incidents.size + 1
      c.incidents.add(IncidentNote(kind, c.recorder.elapsedMicros, text, keepFrames(c, s"incident-$index", frames)))
      c.recorder.incident(kind, text)
    } else sessionIncidents.add(IncidentNote(kind, 0L, s"${iso(now)} $text"))
  }

  /** The frames of the window's history, written beside the match's recording in `folder` (off the
    * calling thread), as the pictures a mark or an incident has of the seconds before it. */
  private def keepFrames(c: MatchRecord, folder: String, frames: Seq[PastFrame]): Seq[FramePicture] =
    if (frames.isEmpty) Nil
    else {
      val dir = c.dir.resolve(folder)
      val pictures = frames.map { f =>
        val micros = c.recorder.microsAt(f.nanos)
        (FramePicture(dir.resolve(f"${Math.max(0L, micros / 1000L)}%08d.jpg"), micros), f.jpeg)
      }
      screenshotWriter.execute(() => {
        try {
          Files.createDirectories(dir)
          pictures.foreach { case (p, jpeg) => Files.write(p.path, jpeg) }
        } catch { case e: Exception => System.err.println(s"Diagnostics: couldn't keep the frames before $folder ($e)") }
      })
      pictures.map(_._1)
    }

  /** The window's history of the last seconds, while a match's window is open (MatchWindow sets it). */
  @volatile var pastFrames: () => Seq[PastFrame] = () => Nil

  private val noticedLast = new ConcurrentHashMap[Int, java.lang.Long]()

  /**
   * The client noticed something for itself (Recording.NoticedKind), with its numbers (`a`, `b`) and a
   * few words: kept for the report's timeline, and in the recording. It is placed `msAgo` back, where it
   * began (a hitch, a silence, a shot that went unanswered). The same kind within NoticedQuietMs is left
   * out (a death never is), and a match keeps MaxNoticed at most. Allocates only when it notes one.
   */
  def noticed(kind: Int, a: Int, b: Int = 0, detail: String = "", msAgo: Int = 0): Unit = {
    val c = current
    if (c == null || c.noticed.size >= MaxNoticed) return
    val now = System.currentTimeMillis()
    if (kind != NoticedKind.Died) {
      val lastAt = noticedLast.get(kind)
      if (lastAt != null && now - lastAt < NoticedQuietMs) return
      noticedLast.put(kind, now)
    }
    val at = Math.max(0L, c.recorder.elapsedMicros - msAgo * 1000L)
    c.noticed.add(NoticedMoment(kind, at, a, b, detail))
    c.recorder.noticed(kind, a, b, detail)
  }

  /** What went wrong outside a match this run, for a report about the game in general. */
  val sessionIncidents = new CopyOnWriteArrayList[IncidentNote]()

  // ── Marking a moment ───────────────────────────────────────────────────

  /** The number of the last moment marked, and until when the HUD says so (epoch ms). */
  @volatile var lastMarkNumber = 0
  @volatile var markNoticeUntilMs = 0L
  /** The mark the HUD says the match has had its fill of, rather than a new one. */
  @volatile var marksFull = false

  private lazy val screenshotWriter = {
    val e = Executors.newSingleThreadExecutor((r: Runnable) => { val t = new Thread(r, "Screenshots"); t.setDaemon(true); t })
    e
  }

  /**
   * The player marked this moment (F8). Its number, or 0 when no match is being recorded or it has had
   * MaxMarks already. `pixels` is the frame as the window read it back (RGB, bottom row first,
   * MemoryUtil.memAlloc'd), or null: written as a JPEG beside the recording, off this thread, and freed.
   */
  def markMoment(pixels: java.nio.ByteBuffer, w: Int, h: Int, before: Seq[PastFrame] = Nil): Int = {
    val c = current
    val now = System.currentTimeMillis()
    if (c == null || c.marks.size >= MaxMarks) {
      if (pixels != null) org.lwjgl.system.MemoryUtil.memFree(pixels)
      if (c != null) { marksFull = true; markNoticeUntilMs = now + 3000L }
      return 0
    }
    val number = c.marks.size + 1
    val shot = c.dir.resolve(s"mark-$number.jpg")
    c.recorder.mark(number, if (pixels != null) shot.getFileName.toString else "")
    c.marks.add(MarkedMoment(number, c.recorder.elapsedMicros, if (pixels != null) shot else null,
      keepFrames(c, s"mark-$number", before)))
    if (pixels != null) screenshotWriter.execute(() => Screenshots.writeJpeg(pixels, w, h, shot))
    println(s"Diagnostics: the player marked moment $number")
    lastMarkNumber = number
    marksFull = false
    markNoticeUntilMs = now + 3000L
    number
  }

  /** Every marked moment's picture written: what a report waits for before it takes them. */
  def screenshotsWritten(): Unit =
    try screenshotWriter.submit(new Runnable { def run(): Unit = () }).get(10, java.util.concurrent.TimeUnit.SECONDS)
    catch { case _: Exception => }

  // ── The connection ─────────────────────────────────────────────────────

  /**
   * What the connection tells of every packet: kept in the match being recorded. GAME_STARTING begins a
   * recording here, on the network's thread, ahead of the packets that follow it (the world, everyone's
   * joins), which would be missed if it waited for the packet thread to get to it.
   */
  // When the match's last packet came from the server (the network's threads)
  @volatile private var lastHeardNanos = 0L
  private val QuietNanos = 1500L * 1000000L

  val packetTap: PacketTap = new PacketTap {
    def received(payload: Array[Byte], tcp: Boolean): Unit = {
      if (payload(0) == PacketType.LOBBY_ACTION.id && payload(21) == LobbyAction.GAME_STARTING) matchStarting()
      val c = current
      if (c != null) {
        // The server quiet for a second and a half or more in a match: something a player would feel
        val now = System.nanoTime()
        val since = lastHeardNanos
        lastHeardNanos = now
        if (since != 0L && now - since > QuietNanos) {
          val ms = ((now - since) / 1000000L).toInt
          noticed(NoticedKind.ServerQuiet, ms, msAgo = ms)
        }
        c.recorder.packet(Recording.PacketIn, tcp, payload)
        if (payload(0) == PacketType.GAME_EVENT.id && payload(21) == GameEvent.SCORE_END)
          c.recorder.text(Recording.Event, "the server ended the match")
      }
    }

    def sent(payload: Array[Byte], tcp: Boolean): Unit = {
      val c = current
      if (c != null) c.recorder.packet(Recording.PacketOut, tcp, payload)
    }

    def dropped(what: String, typeId: Byte): Unit = {
      val kind =
        if (what.contains("signature")) IncidentKind.BadSignature
        else if (what.contains("buffer")) IncidentKind.SendDropped
        else IncidentKind.PacketError
      incident(kind, s"$what (packet type $typeId)")
    }
  }

  // ── Reports ────────────────────────────────────────────────────────────

  /** Reports waiting to go, and what became of those sent (null before `install`). */
  def outbox: ReportOutbox = _outbox
  @volatile private var _outbox: ReportOutbox = _

  /** Keep `record` while a report is made of it, though the next match start. */
  def pin(record: MatchRecord): Unit = record.pinned = true

  /** A report of it is done with: deleted now if it isn't the last match's any more. */
  def unpin(record: MatchRecord): Unit = synchronized {
    record.pinned = false
    if ((record ne last) && (record ne current)) deleteTree(record.dir)
  }

  /** A report of it has gone to the server: its recording goes now, not at the next match. */
  def discard(record: MatchRecord): Unit = synchronized {
    record.pinned = false
    if (record eq last) last = null
    if (record ne current) deleteTree(record.dir)
  }

  /** The crashed run's report is sent or turned down: its recording goes, and it isn't offered again. */
  def forgetPreviousRun(): Unit = synchronized {
    _previousRun.foreach(_.recording.foreach(deleteTree))
    _previousRun = None
  }

  // ── Closing ────────────────────────────────────────────────────────────

  /** The client is closing properly: the match's recording stopped and discarded with the last one (a
    * report of them would have been sent by now), and the marker taken down. */
  def shutdown(): Unit = synchronized {
    if (!installed) return
    val c = current
    if (c != null) { current = null; end(c, "closed") }
    Seq(c, last).filter(_ != null).foreach(r => deleteTree(r.dir))
    last = null
    if (watchdog != null) watchdog.stop()
    val m = marker
    if (m != null) m.clear()
    println("Diagnostics: closed properly")
  }

  // ── Helpers ────────────────────────────────────────────────────────────

  def iso(ms: Long): String = if (ms <= 0) "" else DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(ms))

  def stackTrace(e: Throwable): String = {
    val sw = new StringWriter()
    e.printStackTrace(new PrintWriter(sw))
    sw.toString
  }

  private def matchFolders(): Seq[Path] = {
    val dir = _folder.resolve("match")
    if (!Files.isDirectory(dir)) Nil else list(dir).filter(Files.isDirectory(_)).sortBy(_.getFileName.toString)
  }

  private def list(dir: Path): Seq[Path] = {
    val s = Files.list(dir)
    try s.iterator().asScala.toVector finally s.close()
  }

  private[diagnostics] def deleteTree(dir: Path): Unit =
    try {
      if (Files.isDirectory(dir)) {
        val walk = Files.walk(dir)
        try walk.iterator().asScala.toVector.reverse.foreach(p => Files.deleteIfExists(p)) finally walk.close()
      } else Files.deleteIfExists(dir)
    } catch { case _: Exception => }

  /** For a test: everything as it was before `install`, and nothing of this one left behind. */
  private[client] def resetForTest(): Unit = synchronized {
    val c = current
    if (c != null) { current = null; c.recorder.finish() }
    last = null
    if (watchdog != null) { watchdog.stop(); watchdog = null }
    if (_log != null) _log.close()
    if (heldLock != null) { try { heldLock._2.release(); heldLock._1.close() } catch { case _: Exception => }; heldLock = null }
    _previousRun = None
    _outbox = null
    _graphics = null
    lastMarkNumber = 0
    markNoticeUntilMs = 0L
    sessionIncidents.clear()
    incidentLast.clear()
    incidentQuiet.clear()
    noticedLast.clear()
    pastFrames = () => Nil
    lastHeardNanos = 0L
    installed = false
  }
}
