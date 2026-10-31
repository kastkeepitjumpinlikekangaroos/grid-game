package com.gridgame.client.diagnostics

import com.gridgame.common.diagnostics.Recording
import com.gridgame.common.diagnostics.Recording._
import com.gridgame.common.protocol.PacketType

import java.io.{FileOutputStream, IOException}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.zip.Deflater

/**
 * What the client keeps of the match it is playing, for a problem report about it (design/diagnostics.md,
 * *The recording*): every packet in and out, every frame drawn, every key and button, its own state four
 * times a second, its log, the moments the player marked and whatever went wrong — in Recording's format.
 *
 * Everything is written to disk as it goes, so a crash leaves the match up to its last quarter second
 * for the next run to offer. Callers on any thread (the network's, the packet thread, the one that draws)
 * append whole records to a buffer under a lock, which allocates nothing; a thread of its own swaps the
 * buffer for its twin four times a second, compresses it (Recording's version 2: one DEFLATE stream a
 * segment, flushed each time, so a file cut short still reads to its last flush) and writes it out. A
 * buffer that fills before then (the disk stalled) drops what doesn't fit and says how much in a Gap
 * record.
 *
 * The files are segments of `segmentBytes` on disk, compressed: a match's records squeeze to about a
 * quarter, so 16 of 4MB hold about 256MB of it, the whole of any match a lobby can set (a busy one runs
 * several times the measured 9KB a second). A match that outgrows `maxSegments` of them keeps its
 * first (the start: who is in it, the map) and its newest, the oldest of the rest deleted as it goes.
 *
 * What the player keeps to themselves is never written: a login's password, a token or a ticket, and chat.
 * Those packets keep their header (type, number, player) and lose the rest (FlagRedacted).
 */
final class MatchRecorder(val dir: Path, val startedAtMs: Long, val startNanos: Long = System.nanoTime(),
                          segmentBytes: Int = MatchRecorder.SegmentBytes, maxSegments: Int = MatchRecorder.MaxSegments,
                          bufferBytes: Int = MatchRecorder.BufferBytes, writeEveryMs: Long = 250L,
                          startWriter: Boolean = true) {
  private var active = new Array[Byte](bufferBytes)
  private var spare = new Array[Byte](bufferBytes)
  private var used = 0
  private var lost = 0
  private var closed = false
  @volatile private var failed = false

  // The writer's, and only the writer's (or finish's, once it has stopped)
  private var segment = -1
  private var segmentOut: FileOutputStream = _
  private var segmentWritten = 0L
  @volatile private var totalWritten = 0L
  @volatile private var recordBytes = 0L
  private var segmentFiles = Vector.empty[Path]
  // The segment's DEFLATE stream, and what it squeezes a buffer into
  private var deflater: Deflater = _
  private val squeezed = new Array[Byte](1 << 16)

  private val writer = new Thread(() => writeLoop(), "MatchRecorder")
  writer.setDaemon(true)

  Files.createDirectories(dir)
  openSegment()
  // A test that wants the buffer to fill holds the writer back
  if (startWriter) writer.start()

  /** Times count from here, in Recording's units. */
  private def now(): Long = (System.nanoTime() - startNanos) / TimeUnitNanos

  /** Microseconds since the recording began: what a mark or an incident is placed by. */
  def elapsedMicros: Long = (System.nanoTime() - startNanos) / 1000L

  /** Where a moment on System.nanoTime falls in the recording, in microseconds. */
  def microsAt(nanos: Long): Long = (nanos - startNanos) / 1000L

  /** Bytes on disk so far (compressed), what is kept of the segments let go not counted. */
  def bytesWritten: Long = totalWritten

  /** The match's records so far, before compression. */
  def bytesRecorded: Long = recordBytes

  /** Whether the disk refused it, so nothing more is being kept. */
  def hasFailed: Boolean = failed

  /**
   * Room for a record of `length` bytes of payload, its header written: the payload's offset in `active`,
   * or -1 (nothing written, counted as lost). Called holding the lock.
   */
  private def reserve(kind: Int, flags: Int, length: Int): Int = {
    if (closed || failed) return -1
    if (used + RecordHeaderSize + length > active.length) {
      lost += 1
      notifyAll()
      return -1
    }
    writeRecordHeader(active, used, kind, flags, length, now())
    val at = used + RecordHeaderSize
    used = at + length
    // Half full before its time: wake the writer
    if (used > active.length / 2) notifyAll()
    at
  }

  /** A packet's 64-byte payload, in or out, as it went over the wire; what is the player's own is zeroed. */
  def packet(kind: Int, tcp: Boolean, payload: Array[Byte]): Unit = synchronized {
    val secret = MatchRecorder.isPrivate(payload(0))
    val flags = (if (tcp) FlagTcp else 0) | (if (secret) FlagRedacted else 0)
    val at = reserve(kind, flags, payload.length)
    if (at >= 0) {
      if (secret) {
        val keep = Math.min(MatchRecorder.HeaderBytes, payload.length)
        System.arraycopy(payload, 0, active, at, keep)
        java.util.Arrays.fill(active, at + keep, at + payload.length, 0.toByte)
      } else System.arraycopy(payload, 0, active, at, payload.length)
    }
  }

  /** A frame (FrameRecord). */
  def frame(deltaMs: Float, workMs: Float, cellX: Float, cellY: Float, drawnX: Float, drawnY: Float,
            aimX: Float, aimY: Float, keys: Int, flags: Int, quality: Int, aiming: Int): Unit = synchronized {
    val at = reserve(Frame, 0, FrameRecord.Size)
    if (at >= 0) {
      val b = active
      putFloat(b, at + FrameRecord.DeltaMs, deltaMs)
      putFloat(b, at + FrameRecord.WorkMs, workMs)
      putFloat(b, at + FrameRecord.CellX, cellX)
      putFloat(b, at + FrameRecord.CellY, cellY)
      putFloat(b, at + FrameRecord.DrawnX, drawnX)
      putFloat(b, at + FrameRecord.DrawnY, drawnY)
      putFloat(b, at + FrameRecord.AimX, aimX)
      putFloat(b, at + FrameRecord.AimY, aimY)
      putInt(b, at + FrameRecord.HeldKeys, keys)
      putShort(b, at + FrameRecord.Flags, flags)
      b(at + FrameRecord.Quality) = quality.toByte
      b(at + FrameRecord.Aiming) = aiming.toByte
    }
  }

  /** A key or button going down or up (InputRecord). */
  def input(device: Int, action: Int, code: Int, mods: Int): Unit = synchronized {
    val at = reserve(Input, 0, InputRecord.Size)
    if (at >= 0) {
      val b = active
      b(at + InputRecord.Device) = device.toByte
      b(at + InputRecord.Action) = action.toByte
      putShort(b, at + InputRecord.Code, code)
      b(at + InputRecord.Mods) = mods.toByte
      b(at + 5) = 0; b(at + 6) = 0; b(at + 7) = 0
    }
  }

  /** The client's idea of us (StateRecord). */
  def state(s: StateSample): Unit = synchronized {
    val at = reserve(State, 0, StateRecord.Size)
    if (at >= 0) {
      val b = active
      java.util.Arrays.fill(b, at, at + StateRecord.Size, 0.toByte)
      putShort(b, at + StateRecord.Health, s.health)
      putShort(b, at + StateRecord.MaxHealth, s.maxHealth)
      putInt(b, at + StateRecord.Effects, s.effects)
      putInt(b, at + StateRecord.QLeftMs, s.qLeftMs)
      putInt(b, at + StateRecord.ELeftMs, s.eLeftMs)
      putInt(b, at + StateRecord.RLeftMs, s.rLeftMs)
      b(at + StateRecord.Charge) = s.charge.toByte
      b(at + StateRecord.Items) = s.items.toByte
      putShort(b, at + StateRecord.Kills, s.kills)
      putShort(b, at + StateRecord.Deaths, s.deaths)
      putShort(b, at + StateRecord.SecondsLeft, s.secondsLeft)
      putShort(b, at + StateRecord.RoundTripMs, s.roundTripMs)
      putShort(b, at + StateRecord.Projectiles, Math.min(s.projectiles, 0xFFFF))
      putShort(b, at + StateRecord.Players, Math.min(s.players, 0xFFFF))
      putInt(b, at + StateRecord.ServerMoves, s.serverMoves)
      putInt(b, at + StateRecord.Knockbacks, s.knockbacks)
      putShort(b, at + StateRecord.Queued, Math.min(s.queued, 0xFFFF))
    }
  }

  /** A line of text: Meta (JSON), Log or Event. */
  def text(kind: Int, s: String): Unit = {
    val bytes = utf8(s, MaxPayload)
    synchronized {
      val at = reserve(kind, 0, bytes.length)
      if (at >= 0) System.arraycopy(bytes, 0, active, at, bytes.length)
    }
  }

  /** The player marked this moment: its number, and its screenshot's file name ("" none). */
  def mark(number: Int, screenshot: String): Unit = {
    val name = utf8(screenshot, 1024)
    synchronized {
      val at = reserve(Mark, 0, 2 + name.length)
      if (at >= 0) {
        putShort(active, at, number)
        System.arraycopy(name, 0, active, at + 2, name.length)
      }
    }
  }

  /** Something went wrong (IncidentKind), and what is known of it. */
  def incident(kind: Int, detail: String): Unit = {
    val bytes = utf8(detail, MaxPayload - 1)
    synchronized {
      val at = reserve(Incident, 0, 1 + bytes.length)
      if (at >= 0) {
        active(at) = kind.toByte
        System.arraycopy(bytes, 0, active, at + 1, bytes.length)
      }
    }
  }

  /** Something the client noticed for itself (Recording.NoticedKind), its numbers and a few words. */
  def noticed(kind: Int, a: Int, b: Int, detail: String): Unit = {
    val bytes = utf8(detail, 1024)
    synchronized {
      val at = reserve(Noticed, 0, NoticedRecord.Size + bytes.length)
      if (at >= 0) {
        active(at + NoticedRecord.Kind) = kind.toByte
        putInt(active, at + NoticedRecord.A, a)
        putInt(active, at + NoticedRecord.B, b)
        System.arraycopy(bytes, 0, active, at + NoticedRecord.Size, bytes.length)
      }
    }
  }

  /** Stop: whatever is buffered written out, the files closed. Returns the segments kept, in order. */
  def finish(): Seq[Path] = {
    synchronized {
      if (closed) return segmentFiles
      closed = true
      notifyAll()
    }
    if (writer.isAlive) writer.join(5000)
    drain()
    drain() // the Gap the last one may have left
    closeSegment()
    segmentFiles
  }

  /** The segments on disk so far (the writer's view; safe to read once finished). */
  def segments: Seq[Path] = segmentFiles

  // ── The writer ─────────────────────────────────────────────────────────

  private def writeLoop(): Unit = {
    try {
      while (synchronized { if (!closed) wait(writeEveryMs); !closed }) drain()
    } catch { case _: InterruptedException => }
  }

  /**
   * Swap the buffers and write out what was in the one that was filling. What was lost since (there was
   * no room for it) is the first thing in the new one: a Gap, where in time it was lost.
   */
  private def drain(): Unit = {
    val (buf, n) = synchronized {
      val full = active
      val n = used
      active = spare
      spare = full
      used = 0
      if (lost > 0) {
        writeRecordHeader(active, 0, Gap, 0, 4, now())
        putInt(active, RecordHeaderSize, lost)
        used = RecordHeaderSize + 4
        lost = 0
      }
      (full, n)
    }
    if (n > 0 && !failed) {
      try {
        if (segmentWritten > segmentBytes) openSegment()
        deflater.setInput(buf, 0, n)
        while (!deflater.needsInput()) write(deflater.deflate(squeezed, 0, squeezed.length, Deflater.NO_FLUSH))
        // Flushed to a byte boundary: everything so far reads back, if the client goes no further
        var k = deflater.deflate(squeezed, 0, squeezed.length, Deflater.SYNC_FLUSH)
        write(k)
        while (k == squeezed.length) { k = deflater.deflate(squeezed, 0, squeezed.length, Deflater.SYNC_FLUSH); write(k) }
        segmentOut.flush()
        recordBytes += n
      } catch {
        case e: IOException =>
          failed = true
          System.err.println(s"MatchRecorder: stopped keeping the match ($e)")
      }
    }
  }

  /** `k` bytes of `squeezed` onto the segment. */
  private def write(k: Int): Unit = if (k > 0) {
    segmentOut.write(squeezed, 0, k)
    segmentWritten += k
    totalWritten += k
  }

  /** The segment's stream ended and its file closed. */
  private def closeSegment(): Unit = {
    if (segmentOut != null) {
      try {
        if (deflater != null && !failed) {
          deflater.finish()
          while (!deflater.finished()) write(deflater.deflate(squeezed))
        }
        segmentOut.close()
      } catch { case _: IOException => }
    }
    if (deflater != null) deflater.end()
    deflater = null
    segmentOut = null
  }

  /** The next segment, the oldest but the first deleted once there are too many. */
  private def openSegment(): Unit = {
    try {
      closeSegment()
      segment += 1
      val file = dir.resolve(MatchRecorder.segmentName(segment))
      val out = new FileOutputStream(file.toFile)
      val head = new Array[Byte](SegmentHeaderSize)
      writeSegmentHeader(head, 0, segment, startedAtMs)
      out.write(head)
      out.flush()
      segmentOut = out
      deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true)
      segmentWritten = SegmentHeaderSize
      totalWritten += SegmentHeaderSize
      segmentFiles :+= file
      while (segmentFiles.size > maxSegments) {
        val oldest = segmentFiles(1)
        totalWritten -= Files.size(oldest)
        Files.deleteIfExists(oldest)
        segmentFiles = segmentFiles.patch(1, Nil, 1)
      }
    } catch {
      case e: IOException =>
        failed = true
        System.err.println(s"MatchRecorder: can't keep the match ($e)")
    }
  }

  private def utf8(s: String, max: Int): Array[Byte] = {
    val bytes = (if (s == null) "" else s).getBytes(StandardCharsets.UTF_8)
    if (bytes.length <= max) bytes else java.util.Arrays.copyOf(bytes, max)
  }
}

object MatchRecorder {
  /** 4MB segments on disk, compressed, sixteen kept at most: about 256MB of a match's records. */
  val SegmentBytes: Int = 4 * 1024 * 1024
  val MaxSegments = 16
  /** Each of the two buffers: a quarter second of even the busiest match fills a few percent of it. */
  val BufferBytes: Int = 1024 * 1024

  /** A packet's header: its type, its number and the player it names. */
  val HeaderBytes = 21

  def segmentName(n: Int): String = f"seg-$n%04d.rec"

  /** Packets whose fields are the player's own: a login's password, a session's or a login's token, an
    * upload's ticket, a chat line. Only their header is kept. */
  def isPrivate(typeId: Byte): Boolean =
    typeId == PacketType.AUTH_REQUEST.id || typeId == PacketType.SESSION_TOKEN.id ||
      typeId == PacketType.REFRESH_TOKEN.id || typeId == PacketType.CHAT_MESSAGE.id ||
      typeId == PacketType.PROBLEM_REPORT.id
}

/** What the client knows of us at a moment (StateRecord), filled in by whoever samples it and copied out
  * by the recorder: one, reused, so sampling allocates nothing. */
final class StateSample {
  var health = 0
  var maxHealth = 0
  var effects = 0
  var qLeftMs = 0
  var eLeftMs = 0
  var rLeftMs = 0
  var charge = 0
  var items = 0
  var kills = 0
  var deaths = 0
  var secondsLeft = 0
  var roundTripMs = -1
  var projectiles = 0
  var players = 0
  var serverMoves = 0
  var knockbacks = 0
  var queued = 0
}
