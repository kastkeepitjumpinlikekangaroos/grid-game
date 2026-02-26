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
}
