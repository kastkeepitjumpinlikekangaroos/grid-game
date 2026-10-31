package com.gridgame.common.diagnostics

import com.gridgame.common.diagnostics.Recording._
import org.junit.Assert._
import org.junit.Test

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.nio.charset.StandardCharsets
import java.util.zip.Deflater

/** A recording's segment is read back record for record as the client wrote it, plain (version 1) or
  * compressed (version 2, what it writes now), and one cut short (the client stopped mid-write: a
  * crash) keeps every whole record before the cut and never throws. */
class RecordingTest {
  /** A version 1 segment: the records as they are. */
  private def segment(records: Seq[(Int, Int, Long, Array[Byte])], segmentNo: Int = 3, startedAt: Long = 1793000000123L): Array[Byte] = {
    val size = SegmentHeaderSize + records.map(r => RecordHeaderSize + r._4.length).sum
    val buf = new Array[Byte](size)
    writeSegmentHeader(buf, 0, segmentNo, startedAt, version = 1)
    var off = SegmentHeaderSize
    for ((kind, flags, time, payload) <- records) {
      writeRecordHeader(buf, off, kind, flags, payload.length, time)
      System.arraycopy(payload, 0, buf, off + RecordHeaderSize, payload.length)
      off += RecordHeaderSize + payload.length
    }
    buf
  }

  private val packet = Array.tabulate[Byte](64)(i => (i * 3).toByte)
  private val records = Seq(
    (Meta, 0, 0L, """{"map":"meadow"}""".getBytes(StandardCharsets.UTF_8)),
    (PacketIn, FlagTcp, 5L, packet),
    (PacketOut, FlagRedacted, 100000L, packet),
    (Log, 0, 0xFFFFFFFFL, "the last ten-microsecond tick four bytes hold".getBytes(StandardCharsets.UTF_8)),
    (Gap, 0, 7L, new Array[Byte](0)))

  /** A version 2 segment as the client writes it: the records one raw DEFLATE stream, flushed after each
    * `every` of them, as the recorder flushes each time it writes. */
  private def compressed(records: Seq[(Int, Int, Long, Array[Byte])], every: Int = 1): Array[Byte] = {
    val out = new ByteArrayOutputStream()
    val head = new Array[Byte](SegmentHeaderSize)
    writeSegmentHeader(head, 0, 0, 1793000000123L)
    out.write(head)
    val deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true)
    val chunk = new Array[Byte](1 << 16)
    def flush(): Unit = { var n = deflater.deflate(chunk, 0, chunk.length, Deflater.SYNC_FLUSH); while (n > 0) { out.write(chunk, 0, n); n = if (n == chunk.length) deflater.deflate(chunk, 0, chunk.length, Deflater.SYNC_FLUSH) else 0 } }
    records.grouped(every).foreach { group =>
      for ((kind, flags, time, payload) <- group) {
        val r = new Array[Byte](RecordHeaderSize + payload.length)
        writeRecordHeader(r, 0, kind, flags, payload.length, time)
        System.arraycopy(payload, 0, r, RecordHeaderSize, payload.length)
        deflater.setInput(r)
        while (!deflater.needsInput()) { val n = deflater.deflate(chunk, 0, chunk.length, Deflater.NO_FLUSH); out.write(chunk, 0, n) }
      }
      flush()
    }
    out.toByteArray
  }

  @Test def everyRecordComesBackAsItWasWritten(): Unit = {
    val reader = new SegmentReader(new ByteArrayInputStream(segment(records)))
    assertEquals(SegmentHeader(1, 3, 1793000000123L), reader.header)
    assertFalse(reader.compressed)
    val back = reader.records.toVector
    assertEquals(records.size, back.size)
    for (((kind, flags, time, payload), r) <- records.zip(back)) {
      assertEquals(kind, r.kind)
      assertEquals(flags, r.flags)
      assertEquals("times count in tens of microseconds", time * 10, r.timeMicros)
      assertArrayEquals(payload, r.payload)
    }
    assertEquals("""{"map":"meadow"}""", back.head.text)
  }

  @Test def aSegmentCutShortKeepsEveryWholeRecordBeforeTheCut(): Unit = {
    val whole = segment(records)
    val ends = Iterator.iterate(SegmentHeaderSize)(o => o + RecordHeaderSize + ushort(whole, o + 2)).take(records.size + 1).toVector
    // Cut inside every record, and just after each
    for (cut <- SegmentHeaderSize until whole.length) {
      val kept = new SegmentReader(new ByteArrayInputStream(java.util.Arrays.copyOf(whole, cut))).records.toVector
      assertEquals(s"cut at $cut", ends.count(e => e > SegmentHeaderSize && e <= cut), kept.size)
    }
  }

  @Test def compressedRecordsComeBackAsTheyWereWritten(): Unit = {
    // Many alike, as a match's are: they squeeze
    val many = (0 until 400).map(i => (PacketIn, 0, i * 3L, Array.tabulate[Byte](64)(j => (if (j < 21) j else i % 7).toByte)))
    val bytes = compressed(records ++ many, every = 25)
    val reader = new SegmentReader(new ByteArrayInputStream(bytes))
    assertEquals(Version, reader.header.version)
    assertTrue(reader.compressed)
    val back = reader.records.toVector
    assertEquals((records ++ many).size, back.size)
    for (((kind, flags, time, payload), r) <- (records ++ many).zip(back)) {
      assertEquals((kind, flags, time * 10), (r.kind, r.flags, r.timeMicros))
      assertArrayEquals(payload, r.payload)
    }
    val plain = segment(records ++ many).length
    assertTrue(s"compressed to ${bytes.length} of $plain bytes", bytes.length < plain / 3)
  }

  @Test def aCompressedSegmentCutShortKeepsWhatCameBeforeTheCutAndNeverThrows(): Unit = {
    // Flushed after every record, as a crash would leave a file the recorder had been flushing
    val bytes = compressed(records)
    var last = 0
    for (cut <- SegmentHeaderSize to bytes.length) {
      val kept = new SegmentReader(new ByteArrayInputStream(java.util.Arrays.copyOf(bytes, cut))).records.toVector
      assertTrue(s"cut at $cut kept ${kept.size}, fewer than an earlier cut", kept.size >= last)
      last = kept.size
      for ((r, (kind, _, _, payload)) <- kept.zip(records)) {
        assertEquals(kind, r.kind)
        assertArrayEquals(payload, r.payload)
      }
    }
    assertEquals("the whole of it, uncut", records.size, last)
    // And garbage where the stream should be is the end of it, not an exception
    val broken = bytes.clone()
    for (i <- SegmentHeaderSize + 4 until broken.length) broken(i) = (broken(i) ^ 0x5A).toByte
    new SegmentReader(new ByteArrayInputStream(broken)).records.toVector
  }

  @Test def whatIsntARecordingIsSaidToBeNone(): Unit = {
    for (bytes <- Seq(new Array[Byte](0), "GGREC".getBytes(StandardCharsets.US_ASCII), new Array[Byte](64))) {
      try { new SegmentReader(new ByteArrayInputStream(bytes)); fail(s"${bytes.length} bytes read as a recording") }
      catch { case _: IllegalArgumentException => }
    }
    val otherVersion = segment(records)
    otherVersion(5) = (Versions.max + 1).toByte
    try { new SegmentReader(new ByteArrayInputStream(otherVersion)); fail("another version read as this one") }
    catch { case e: IllegalArgumentException => assertTrue(e.getMessage, e.getMessage.contains("version")) }
  }

  @Test def numbersAreWrittenAsTheWireWritesThem(): Unit = {
    val b = new Array[Byte](18)
    putShort(b, 0, -2); putInt(b, 2, 0x81020304); putFloat(b, 6, 1.5f); putLong(b, 10, -5L)
    assertEquals(-2, short(b, 0))
    assertEquals(0xFFFE, ushort(b, 0))
    assertEquals(0x81020304, int(b, 2))
    assertEquals(1.5f, float(b, 6), 0f)
    assertEquals(-5L, long(b, 10))
    val bb = java.nio.ByteBuffer.wrap(b) // big-endian, like the packets
    assertEquals(0x81020304, bb.getInt(2))
  }
}
