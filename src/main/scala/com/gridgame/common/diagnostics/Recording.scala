package com.gridgame.common.diagnostics

import java.io.{BufferedInputStream, DataInputStream, EOFException, InputStream}
import java.nio.charset.StandardCharsets
import java.util.zip.{Inflater, InflaterInputStream, ZipException}

/**
 * A match's recording, as the client keeps it for a problem report (client/diagnostics/MatchRecorder)
 * and the report tool reads it (tools/ReportTool): everything the client received and sent during the
 * match, every frame it drew, every key and button, a snapshot of its own state four times a second,
 * its log, and what went wrong (design/diagnostics.md, *The recording*).
 *
 * It is a run of segment files, each a header and then records. Byte order: BIG_ENDIAN, as the wire's.
 *
 * {{{
 * segment header (16 bytes):  "GGREC"  [5] version  [6-7] segment number  [8-15] the recording's start (epoch ms)
 * record header (8 bytes):    [0] kind  [1] flags  [2-3] payload length  [4-7] time since the start, in 10us
 * }}}
 *
 * Version 2 (what the client writes): after the header, the records are one raw DEFLATE stream,
 * flushed (SYNC_FLUSH) each time the recorder writes, four times a second. A match's packets squeeze
 * to about a quarter, so a segment's 4MB holds four times the match, and a file cut short (a crash)
 * still reads up to its last flush. Version 1, the records as they are, is still read.
 *
 * A record cut short (the client stopped halfway through writing it) ends its segment: a reader takes
 * every whole record before it.
 */
object Recording {
  /** The version the client writes: compressed records. */
  val Version: Int = 2
  /** The versions a reader takes. */
  val Versions: Set[Int] = Set(1, 2)
  val Magic: Array[Byte] = "GGREC".getBytes(StandardCharsets.US_ASCII)
  val SegmentHeaderSize = 16
  val RecordHeaderSize = 8
  /** The most a record's payload can hold (its length is two bytes). */
  val MaxPayload = 65535
  /** What a record's time counts in: tens of microseconds, so four bytes last eleven hours. */
  val TimeUnitNanos = 10000L

  // ── Kinds of record ────────────────────────────────────────────────────

  /** UTF-8 JSON: what the recording is of, and anything learnt about the match or the machine later. */
  final val Meta = 1
  /** A packet from the server, its 64-byte payload as it arrived (FlagTcp: over TCP). */
  final val PacketIn = 2
  /** A packet to the server, its 64-byte payload as it went (FlagTcp: over TCP). */
  final val PacketOut = 3
  /** A frame the match's window drew: FrameRecord. */
  final val Frame = 4
  /** A key, a mouse button or a controller button going down or up: InputRecord. */
  final val Input = 5
  /** The client's own idea of us, four times a second: StateRecord. */
  final val State = 6
  /** A line of the client's log, as it was written. */
  final val Log = 7
  /** Something the client did or saw, in a line of UTF-8: "window: 2880x1800", "quality: low". */
  final val Event = 8
  /** The player marked this moment (F8): [0-1] its number, then its screenshot's file name, UTF-8 ("" none). */
  final val Mark = 9
  /** Something went wrong: [0] what (Incident), then what is known of it, UTF-8. */
  final val Incident = 10
  /** Records were lost here, the recorder having had nowhere to put them: [0-3] how many. */
  final val Gap = 11
  /** Something the client noticed for itself that a player may mean when they report a problem
    * without having marked it: NoticedRecord. */
  final val Noticed = 12

  /** A packet record's flags. */
  final val FlagTcp = 1
  /** Everything past the packet's header (its type, number and player) was zeroed: what it carried is
    * the player's to keep (a password, a token, a ticket, a chat line). */
  final val FlagRedacted = 2

  def kindName(kind: Int): String = kind match {
    case Meta => "meta"
    case PacketIn => "in"
    case PacketOut => "out"
    case Frame => "frame"
    case Input => "input"
    case State => "state"
    case Log => "log"
    case Event => "event"
    case Mark => "mark"
    case Incident => "incident"
    case Gap => "gap"
    case Noticed => "noticed"
    case k => s"kind$k"
  }

  /** The keys and buttons a frame found held (FrameRecord.Keys). */
  object Keys {
    final val W = 1 << 0
    final val A = 1 << 1
    final val S = 1 << 2
    final val D = 1 << 3
    final val Space = 1 << 4
    final val Shift = 1 << 5
    final val Q = 1 << 6
    final val E = 1 << 7
    final val R = 1 << 8
    final val MouseLeft = 1 << 9
    final val MouseRight = 1 << 10

    val names: Seq[(Int, String)] = Seq(W -> "W", A -> "A", S -> "S", D -> "D", Space -> "Space",
      Shift -> "Shift", Q -> "Q", E -> "E", R -> "R", MouseLeft -> "LMB", MouseRight -> "RMB")
  }

  /** A frame: how long it took, where we were and were drawn, where we aimed, what was held. */
  object FrameRecord {
    final val Size = 40
    /** f32: how long since the frame before began, ms. */
    final val DeltaMs = 0
    /** f32: how long this frame took on the thread that draws it, from its events to its swap, ms. */
    final val WorkMs = 4
    /** f32 x2: the cell we stand on. */
    final val CellX = 8
    final val CellY = 12
    /** f32 x2: where we are drawn (between steps, sliding from a push). */
    final val DrawnX = 16
    final val DrawnY = 20
    /** f32 x2: the world point the pointer (or the stick) aims at. */
    final val AimX = 24
    final val AimY = 28
    /** i32: the keys and buttons held (Keys). */
    final val HeldKeys = 32
    /** u16: FrameFlags. */
    final val Flags = 36
    /** u8: the graphics quality tier drawn at (RenderQuality). */
    final val Quality = 38
    /** i8: the ability taken up to aim (0 Q, 1 E), -1 none. */
    final val Aiming = 39
  }

  object FrameFlags {
    final val Focused = 1 << 0
    final val ChatOpen = 1 << 1
    final val Dead = 1 << 2
    final val Fullscreen = 1 << 3
    final val StickAiming = 1 << 4
    final val Charging = 1 << 5
    final val Moving = 1 << 6

    val names: Seq[(Int, String)] = Seq(Focused -> "focused", ChatOpen -> "chat", Dead -> "dead",
      Fullscreen -> "fullscreen", StickAiming -> "stick", Charging -> "charging", Moving -> "moving")
  }

  /** A key, a mouse button or a controller button. */
  object InputRecord {
    final val Size = 8
    /** u8: InputDevice. */
    final val Device = 0
    /** u8: 0 up, 1 down, 2 held down (the key repeating). */
    final val Action = 1
    /** i16: the GLFW key or button; -1 for a key typed into chat, which isn't kept. */
    final val Code = 2
    /** u8: the GLFW modifier keys held with it. */
    final val Mods = 4
  }

  object InputDevice {
    final val Keyboard = 0
    final val Mouse = 1
    final val Controller = 2
  }

  /** The client's own idea of us: health, effects, cooldowns, the score, the connection. */
  object StateRecord {
    final val Size = 48
    /** i16 x2 */
    final val Health = 0
    final val MaxHealth = 2
    /** i32: StateEffects. */
    final val Effects = 4
    /** i32 x3: how long Q, E and R have left to cool down, ms. */
    final val QLeftMs = 8
    final val ELeftMs = 12
    final val RLeftMs = 16
    /** u8: the charge, 0-100. */
    final val Charge = 20
    /** u8: items carried. */
    final val Items = 21
    /** i16 x2: our kills and deaths. */
    final val Kills = 22
    final val Deaths = 24
    /** i16: the match's seconds left, as our clock has them. */
    final val SecondsLeft = 26
    /** i16: the last heartbeat's round trip, ms; -1 not known. */
    final val RoundTripMs = 28
    /** u16 x2: projectiles in flight, and the other players in the match. */
    final val Projectiles = 30
    final val Players = 32
    /** i32 x2: the server moves and the knockbacks seen of us (design/networking.md, *Position authority*). */
    final val ServerMoves = 34
    final val Knockbacks = 38
    /** u16: the server's packets waiting for the packet thread. */
    final val Queued = 42
  }

  object StateEffects {
    final val Frozen = 1 << 0
    final val Rooted = 1 << 1
    final val Slowed = 1 << 2
    final val Stunned = 1 << 3
    final val Phased = 1 << 4
    final val Burning = 1 << 5
    final val Poisoned = 1 << 6
    final val SpeedBoost = 1 << 7
    final val Shield = 1 << 8
    final val Gem = 1 << 9
    final val Dead = 1 << 10
    final val Respawning = 1 << 11
    final val Charging = 1 << 12
    final val Barrier = 1 << 13
    final val AttacksLocked = 1 << 14
    final val Swooping = 1 << 15
    final val MovementBlocked = 1 << 16

    val names: Seq[(Int, String)] = Seq(Frozen -> "frozen", Rooted -> "rooted", Slowed -> "slowed",
      Stunned -> "stunned", Phased -> "phased", Burning -> "burning", Poisoned -> "poisoned",
      SpeedBoost -> "speed", Shield -> "shield", Gem -> "gem", Dead -> "dead", Respawning -> "respawning",
      Charging -> "charging", Barrier -> "barrier", AttacksLocked -> "ceasefire", Swooping -> "swooping",
      MovementBlocked -> "blocked")
  }

  /** Something the client noticed: [0] NoticedKind, [1-4] a, [5-8] b (what each says is the kind's),
    * then, for some kinds, a few words of UTF-8. */
  object NoticedRecord {
    final val Size = 9
    final val Kind = 0
    final val A = 1
    final val B = 5
  }

  /**
   * What the client notices for itself during a match, without the player pressing anything: the
   * moments a player who didn't mark one may mean, offered on the report's timeline to pick from
   * (design/diagnostics.md, *Moments the client noticed*).
   */
  object NoticedKind {
    /** The server put us somewhere we didn't walk to (a correction or a teleport, not a push): a = how
      * far, in hundredths of a cell. */
    final val SnapBack = 1
    /** Something we fired the server never answered within a second (lost, or refused): a = the
      * AttackSlot (0 primary, 1 Q, 2 E, 3 the burst shot). */
    final val ShotUnanswered = 2
    /** A frame came this long after the last: a = ms. */
    final val Hitch = 3
    /** Nothing came from the server for this long: a = ms (the record is at the silence's end). */
    final val ServerQuiet = 4
    /** We died: a = the killer's character id (-1 not known); the killer's name follows. */
    final val Died = 5
    /** The server refused what we did: a = 0 R, 1 a trap, 2 an item; b = R's ms left, if that was why. */
    final val Refused = 6
    /** A heartbeat took this long to come back: a = ms. */
    final val PingSpike = 7

    def name(kind: Int): String = kind match {
      case SnapBack => "snap-back"
      case ShotUnanswered => "shot-unanswered"
      case Hitch => "hitch"
      case ServerQuiet => "server-quiet"
      case Died => "died"
      case Refused => "refused"
      case PingSpike => "ping-spike"
      case k => s"noticed$k"
    }

    /** In a few words, in English (the report's own text; the game's card says it in the player's language). */
    def describe(kind: Int, a: Int, b: Int, detail: String): String = kind match {
      case SnapBack => f"the server moved us ${a / 100.0}%.1f cells, not by a push"
      case ShotUnanswered => s"our ${slotName(a)} got no answer from the server"
      case Hitch => s"a frame came ${a}ms after the last"
      case ServerQuiet => f"the server said nothing for ${a / 1000.0}%.1fs"
      case Died => if (detail.nonEmpty) s"we died (killed by $detail)" else "we died"
      case Refused => a match {
        case 0 => if (b > 0) f"the server refused our R (${b / 1000.0}%.1fs of its minute left)" else "the server refused our R"
        case 1 => "the server refused our trap"
        case _ => "the server refused an item"
      }
      case PingSpike => s"a heartbeat took ${a}ms to come back"
      case k => s"noticed $k ($a, $b)"
    }

    /** An AttackSlot's name. */
    def slotName(slot: Int): String = slot match {
      case 0 => "primary"
      case 1 => "Q"
      case 2 => "E"
      case 3 => "burst shot"
      case s => s"attack $s"
    }
  }

  /** What went wrong (an Incident record's [0]). */
  object IncidentKind {
    /** An exception nothing caught, on any thread. */
    final val Error = 1
    /** The match's window failed drawing a frame, and the match was closed. */
    final val RenderCrash = 2
    /** The thread that draws and runs the menus stopped answering for seconds. */
    final val Freeze = 3
    /** A packet from the server threw as it was handled. */
    final val PacketError = 4
    /** The server's packets came faster than they were handled, and some were thrown away. */
    final val QueueFull = 5
    /** The connection to the server dropped. */
    final val Disconnect = 6
    /** Packets from the server whose signature didn't check out. */
    final val BadSignature = 7
    /** A packet to the server dropped, the connection's buffer being full. */
    final val SendDropped = 8

    def name(kind: Int): String = kind match {
      case Error => "error"
      case RenderCrash => "render-crash"
      case Freeze => "freeze"
      case PacketError => "packet-error"
      case QueueFull => "queue-full"
      case Disconnect => "disconnect"
      case BadSignature => "bad-signature"
      case SendDropped => "send-dropped"
      case k => s"incident$k"
    }
  }

  // ── Writing ────────────────────────────────────────────────────────────

  /** A segment's header into `buf` at `off`: `version` 2 says compressed records follow, 1 plain ones. */
  def writeSegmentHeader(buf: Array[Byte], off: Int, segment: Int, startedAtMs: Long, version: Int = Version): Unit = {
    System.arraycopy(Magic, 0, buf, off, Magic.length)
    buf(off + 5) = version.toByte
    putShort(buf, off + 6, segment)
    putLong(buf, off + 8, startedAtMs)
  }

  /** A record's header into `buf` at `off`; its payload follows it. */
  def writeRecordHeader(buf: Array[Byte], off: Int, kind: Int, flags: Int, length: Int, timeUnits: Long): Unit = {
    buf(off) = kind.toByte
    buf(off + 1) = flags.toByte
    putShort(buf, off + 2, length)
    putInt(buf, off + 4, timeUnits.toInt)
  }

  def putShort(buf: Array[Byte], off: Int, v: Int): Unit = {
    buf(off) = (v >>> 8).toByte
    buf(off + 1) = v.toByte
  }

  def putInt(buf: Array[Byte], off: Int, v: Int): Unit = {
    buf(off) = (v >>> 24).toByte
    buf(off + 1) = (v >>> 16).toByte
    buf(off + 2) = (v >>> 8).toByte
    buf(off + 3) = v.toByte
  }

  def putLong(buf: Array[Byte], off: Int, v: Long): Unit = {
    putInt(buf, off, (v >>> 32).toInt)
    putInt(buf, off + 4, v.toInt)
  }

  def putFloat(buf: Array[Byte], off: Int, v: Float): Unit = putInt(buf, off, java.lang.Float.floatToRawIntBits(v))

  // ── Reading ────────────────────────────────────────────────────────────

  def short(b: Array[Byte], off: Int): Int = ((b(off) << 8) | (b(off + 1) & 0xFF)).toShort.toInt
  def ushort(b: Array[Byte], off: Int): Int = ((b(off) & 0xFF) << 8) | (b(off + 1) & 0xFF)
  def int(b: Array[Byte], off: Int): Int =
    ((b(off) & 0xFF) << 24) | ((b(off + 1) & 0xFF) << 16) | ((b(off + 2) & 0xFF) << 8) | (b(off + 3) & 0xFF)
  def long(b: Array[Byte], off: Int): Long = (int(b, off).toLong << 32) | (int(b, off + 4) & 0xFFFFFFFFL)
  def float(b: Array[Byte], off: Int): Float = java.lang.Float.intBitsToFloat(int(b, off))

  /** What a segment's header says. */
  final case class SegmentHeader(version: Int, segment: Int, startedAtMs: Long)

  /** A record: its kind, its flags, when (microseconds since the recording began) and what it holds. */
  final case class Record(kind: Int, flags: Int, timeMicros: Long, payload: Array[Byte]) {
    def text: String = new String(payload, StandardCharsets.UTF_8)
  }

  /**
   * One segment's records, in the order they were written, whichever version wrote it. Throws if `in`
   * isn't a segment at all (no header, or a version it doesn't know); a record cut short, or a
   * compressed stream cut short or broken, ends it quietly after the last whole record.
   */
  final class SegmentReader(in: InputStream) {
    val header: SegmentHeader = {
      val head = new Array[Byte](SegmentHeaderSize)
      try new DataInputStream(in).readFully(head)
      catch { case _: EOFException => throw new IllegalArgumentException("not a recording: too short") }
      if (!java.util.Arrays.equals(java.util.Arrays.copyOf(head, Magic.length), Magic))
        throw new IllegalArgumentException("not a recording")
      val version = head(5) & 0xFF
      if (!Versions(version)) throw new IllegalArgumentException(s"a recording of version $version; this reads ${Versions.toSeq.sorted.mkString(" and ")}")
      SegmentHeader(version, ushort(head, 6), long(head, 8))
    }

    /** Whether its records were compressed (version 2). */
    def compressed: Boolean = header.version >= 2

    private val data = new DataInputStream(
      if (compressed) new InflaterInputStream(new BufferedInputStream(in, 1 << 16), new Inflater(true), 1 << 16) else in)

    private val recordHead = new Array[Byte](RecordHeaderSize)

    /** The next record, or null at the end (or where it was cut short). */
    def next(): Record = {
      try {
        data.readFully(recordHead)
        val length = ushort(recordHead, 2)
        val payload = new Array[Byte](length)
        data.readFully(payload)
        Record(recordHead(0) & 0xFF, recordHead(1) & 0xFF, (int(recordHead, 4) & 0xFFFFFFFFL) * (TimeUnitNanos / 1000), payload)
      } catch {
        case _: EOFException => null
        case _: ZipException => null // the compressed stream broken where a crash cut it
      }
    }

    /** Every record left. */
    def records: Iterator[Record] = Iterator.continually(next()).takeWhile(_ != null)
  }

  /** Where things are in a report's zip, as the client writes them and the report tool reads them. */
  object ReportFiles {
    val ReportJson = "report.json"
    val RecordingDir = "recording/"
    /** The picture of the frame the player marked. */
    def markShot(number: Int): String = s"screenshots/mark-$number.jpg"
    /** The frames before a marked moment (or an incident), kept a few a second for the last few seconds,
      * each named by when it was drawn: milliseconds since the recording began, eight digits. */
    def markFrame(number: Int, ms: Long): String = f"screenshots/mark-$number/$ms%08d.jpg"
    def incidentFrame(index: Int, ms: Long): String = f"screenshots/incident-$index/$ms%08d.jpg"
  }
}
