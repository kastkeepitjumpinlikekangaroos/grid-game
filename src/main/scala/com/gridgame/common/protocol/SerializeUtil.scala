package com.gridgame.common.protocol

import com.gridgame.common.Constants

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Provides a ThreadLocal reusable ByteBuffer for packet serialization,
 *  avoiding ByteBuffer.allocate() per packet. */
object SerializeUtil {
  private val threadLocalBuffer: ThreadLocal[ByteBuffer] = ThreadLocal.withInitial(() =>
    ByteBuffer.allocate(Constants.PACKET_PAYLOAD_SIZE).order(ByteOrder.BIG_ENDIAN)
  )

  /** Acquire a cleared, BIG_ENDIAN ByteBuffer of PACKET_PAYLOAD_SIZE.
   *  The buffer is reused per-thread; caller must clone .array() if the data
   *  needs to outlive the next acquireBuffer() call. */
  def acquireBuffer(): ByteBuffer = {
    val buf = threadLocalBuffer.get()
    buf.clear()
    buf
  }

  /**
   * Put `text` into a field of `field` bytes: as much of it as fits in whole characters, as UTF-8,
   * then zeros. Cut at a byte count, as every text field was, a character of two or three bytes
   * could be cut in half, and the far end read the half as "�" — a lobby's name in Korean or
   * Chinese, say, a byte too long. Not for the login's name and password, whose cut the account
   * database already holds them by.
   */
  def putText(buffer: ByteBuffer, text: String, field: Int): Unit = {
    val bytes = text.getBytes(java.nio.charset.StandardCharsets.UTF_8)
    var n = Math.min(bytes.length, field)
    // Back off to the start of the character the field ends inside: its other bytes are 10xxxxxx
    if (n < bytes.length) while (n > 0 && (bytes(n) & 0xC0) == 0x80) n -= 1
    buffer.put(bytes, 0, n)
    while (n < field) { buffer.put(0.toByte); n += 1 }
  }
}
