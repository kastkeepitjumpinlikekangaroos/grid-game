package com.gridgame.tools

import com.gridgame.common.diagnostics.Recording

import java.io.ByteArrayInputStream
import java.util.zip.{DataFormatException, Inflater}

/** One record of a report's recording, with the segment it came from. */
final case class Rec(kind: Int, flags: Int, timeMicros: Long, payload: Array[Byte], segment: Int) {
  def text: String = new String(payload, java.nio.charset.StandardCharsets.UTF_8)
}

/**
 * A segment of the recording: its number, when the recording it is part of began, what it held.
 *
 * A version 1 segment is its records as they are, so a record cut short is so many bytes at its end
 * ([[cutShortBytes]]). A version 2 segment's records are one compressed stream, which a segment the
 * client closed ends with the stream's own end mark: one without it is a segment the client stopped
 * writing (a crash), whether or not a record in it was cut in two ([[endMarked]], [[partialRecord]]).
 *
 * @param bytes          its size in the zip (compressed, for version 2)
 * @param inflatedBytes  version 2: its records' size once uncompressed
 * @param damaged        version 2: what came after the last record read wasn't a compressed stream at all
 */
final case class SegmentInfo(file: String, number: Int, startedAtMs: Long, bytes: Int, records: Int,
                             firstMicros: Long, lastMicros: Long, cutShortBytes: Int, version: Int = 1,
                             inflatedBytes: Long = 0L, endMarked: Boolean = true, partialRecord: Boolean = false,
                             damaged: Boolean = false) {
  def compressed: Boolean = version >= 2

  /** Did the client stop writing it partway (a crash), so that the end of it is missing? */
  def cutShort: Boolean = if (compressed) !endMarked || partialRecord || damaged else cutShortBytes > 0
}

/**
 * The match's recording in a report (`recording/seg-NNNN.rec`, in common/diagnostics/Recording's
 * format), every segment's records put in time order.
 *
 * Segment 0 is the match's start. A long match outgrows the client's budget for its recording, and the
 * client then keeps the first segment and the newest ones, dropping those between: the numbers skip,
 * and [[missing]] says which and what stretch of the match went with them. A segment can also end in
 * a record cut short, when the client stopped halfway through writing it (a crash, or the power going):
 * every whole record before the cut is kept, and [[SegmentInfo.cutShort]] says so (for a version 1
 * segment, how many bytes weren't kept: [[SegmentInfo.cutShortBytes]]).
 */
final class ReportRecording(
    val segments: Vector[SegmentInfo],
    /** Every record of a kind this tool knows, in time order (stable: records of the same moment stay
      * in the order they were written). */
    val records: Vector[Rec],
    /** Records of kinds this tool doesn't know (a newer client's), skipped: how many of each kind. */
    val unknownKinds: Map[Int, Int],
    /** Files that were meant to be segments and couldn't be read, and why. */
    val unreadable: Vector[(String, String)]) {

  def isEmpty: Boolean = records.isEmpty

  def firstMicros: Long = if (records.isEmpty) 0L else records.head.timeMicros
  def lastMicros: Long = if (records.isEmpty) 0L else records.last.timeMicros

  /** How many records of each kind. */
  lazy val countsByKind: Map[Int, Int] = records.groupMapReduce(_.kind)(_ => 1)(_ + _)

  /** GAP records, and how many records they say were lost in all. */
  lazy val gaps: Int = records.count(_.kind == Recording.Gap)
  lazy val lostRecords: Long = records.iterator.filter(r => r.kind == Recording.Gap && r.payload.length >= 4)
    .map(r => Recording.int(r.payload, 0) & 0xFFFFFFFFL).sum

  /** When the recording began, on the wall clock (epoch ms), from its first segment. */
  def startedAtMs: Option[Long] = segments.headOption.map(_.startedAtMs)

  /** The stretches nothing was kept of, the missing segments' (from, to): a gap in what was heard across
    * one of them is the recording's, not the connection's. */
  lazy val holes: Vector[(Long, Long)] = missing.collect { case (_, _, Some(from), Some(to)) => (from, to) }

  /** Does the stretch from `a` to `b` take in a hole? */
  def crossesHole(a: Long, b: Long): Boolean = holes.exists { case (from, to) => a <= from && b >= to }

  /** The stretch from `a` to `b` less its holes: the parts of it that were recorded. */
  def recordedPieces(a: Long, b: Long): Vector[(Long, Long)] =
    holes.sortBy(_._1).foldLeft(Vector((a, b))) { case (pieces, (from, to)) =>
      pieces.flatMap { case (x, y) =>
        if (to <= x || from >= y) Vector((x, y)) else Vector((x, from), (to, y)).filter { case (p, q) => q > p }
      }
    }

  /** How much of the match the recording holds, in microseconds: from its first record to its last,
    * less its holes. */
  def coveredMicros: Long = Math.max(0L, lastMicros - firstMicros - holes.map { case (from, to) => to - from }.sum)

  /** Runs of segment numbers that aren't here, as (first missing, last missing, end of what came before,
    * start of what came after): the client dropped them, and nothing of that stretch was kept. */
  lazy val missing: Vector[(Int, Int, Option[Long], Option[Long])] = {
    val runs = Vector.newBuilder[(Int, Int, Option[Long], Option[Long])]
    segments.headOption.foreach { first =>
      if (first.number > 0) runs += ((0, first.number - 1, None, Some(first.firstMicros)))
    }
    segments.sliding(2).foreach {
      case Seq(a, b) if b.number > a.number + 1 => runs += ((a.number + 1, b.number - 1, Some(a.lastMicros), Some(b.firstMicros)))
      case _ =>
    }
    runs.result()
  }
}

object ReportRecording {
  private val SegmentFile = """(?:.*/)?seg-(\d+)\.rec""".r

  /** The kinds of record this tool reads. */
  val KnownKinds: Set[Int] = Set(Recording.Meta, Recording.PacketIn, Recording.PacketOut, Recording.Frame,
    Recording.Input, Recording.State, Recording.Log, Recording.Event, Recording.Mark, Recording.Incident, Recording.Gap,
    Recording.Noticed)

  /**
   * What a version 2 segment's compressed stream comes to, read to its end: whether it ends with the
   * stream's end mark (the client closed the segment), how many bytes it uncompresses to, and whether
   * it broke off into something that isn't a compressed stream.
   */
  final case class StreamEnd(endMarked: Boolean, inflatedBytes: Long, damaged: Boolean)

  def streamEnd(segment: Array[Byte]): StreamEnd = {
    val inflater = new Inflater(true)
    try {
      // Raw DEFLATE wants a byte past the stream to see its end: zlib's "dummy" byte
      val input = java.util.Arrays.copyOfRange(segment, Recording.SegmentHeaderSize, segment.length + 1)
      inflater.setInput(input)
      val out = new Array[Byte](1 << 16)
      var total = 0L
      var damaged = false
      var stuck = false
      try {
        while (!inflater.finished() && !stuck) {
          val n = inflater.inflate(out)
          total += n
          if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) stuck = true
        }
      } catch { case _: DataFormatException => damaged = true }
      StreamEnd(inflater.finished(), total, damaged)
    } finally inflater.end()
  }

  /** Is this name in a report's zip a segment of its recording? */
  def isSegment(name: String): Boolean = name.startsWith("recording/") && SegmentFile.matches(name)

  def read(files: ReportFiles): ReportRecording = {
    val unreadable = Vector.newBuilder[(String, String)]
    val read = files.entries.filter { case (name, _) => isSegment(name) }.flatMap { case (name, bytes) =>
      try {
        val reader = new Recording.SegmentReader(new ByteArrayInputStream(bytes))
        val recs = reader.records.toVector
        Some((name, reader.header, recs, bytes))
      } catch {
        case e: IllegalArgumentException => unreadable += name -> e.getMessage; None
      }
    }
    // In segment order: the header's number, which the file's name says too
    val ordered = read.sortBy { case (name, header, _, _) => (header.segment, name) }
    val unknown = scala.collection.mutable.Map.empty[Int, Int].withDefaultValue(0)
    val infos = ordered.map { case (name, header, recs, bytes) =>
      val recordBytes = recs.iterator.map(r => (Recording.RecordHeaderSize + r.payload.length).toLong).sum
      // Its earliest and latest: records written from several threads at once can be a little out of order
      val first = if (recs.isEmpty) 0L else recs.iterator.map(_.timeMicros).min
      val last = if (recs.isEmpty) 0L else recs.iterator.map(_.timeMicros).max
      if (header.version >= 2) {
        val end = streamEnd(bytes)
        SegmentInfo(name, header.segment, header.startedAtMs, bytes.length, recs.size, first, last, 0, header.version,
          end.inflatedBytes, end.endMarked, end.inflatedBytes > recordBytes, end.damaged)
      } else SegmentInfo(name, header.segment, header.startedAtMs, bytes.length, recs.size, first, last,
        (bytes.length - Recording.SegmentHeaderSize - recordBytes).toInt, header.version)
    }
    val all = ordered.iterator.flatMap { case (_, header, recs, _) =>
      recs.iterator.flatMap { r =>
        if (KnownKinds(r.kind)) Some(Rec(r.kind, r.flags, r.timeMicros, r.payload, header.segment))
        else { unknown(r.kind) += 1; None }
      }
    }.toVector
    // Stable: what was written at the same moment stays in the order it was written
    new ReportRecording(infos, all.sortBy(_.timeMicros), unknown.toMap, unreadable.result())
  }
}
