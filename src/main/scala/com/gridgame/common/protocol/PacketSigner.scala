package com.gridgame.common.protocol

import com.gridgame.common.Constants

import java.security.MessageDigest
import java.util.Arrays
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object PacketSigner {
  private val ALGORITHM = "HmacSHA256"

  // ThreadLocal cached Mac instances to avoid Mac.getInstance() + new SecretKeySpec per packet
  private val threadLocalMac: ThreadLocal[Mac] = ThreadLocal.withInitial(() => Mac.getInstance(ALGORITHM))
  private val threadLocalKey: ThreadLocal[Array[Byte]] = ThreadLocal.withInitial(() => null)

  /** Sign a 64-byte payload, returning an 80-byte packet with 16-byte truncated HMAC appended. */
  def sign(payload: Array[Byte], sessionToken: Array[Byte]): Array[Byte] = {
    val result = new Array[Byte](Constants.PACKET_SIZE)
    System.arraycopy(payload, 0, result, 0, Constants.PACKET_PAYLOAD_SIZE)
    computeHmacInto(payload, sessionToken, result, Constants.PACKET_PAYLOAD_SIZE)
    result
  }

  /** Verify an 80-byte signed packet. Returns the 64-byte payload if valid, null otherwise. */
  def verify(signedPacket: Array[Byte], sessionToken: Array[Byte]): Array[Byte] = {
    if (signedPacket == null || signedPacket.length != Constants.PACKET_SIZE) return null

    val payload = new Array[Byte](Constants.PACKET_PAYLOAD_SIZE)
    System.arraycopy(signedPacket, 0, payload, 0, Constants.PACKET_PAYLOAD_SIZE)

    val receivedHmac = new Array[Byte](Constants.HMAC_SIZE)
    System.arraycopy(signedPacket, Constants.PACKET_PAYLOAD_SIZE, receivedHmac, 0, Constants.HMAC_SIZE)

    val expectedHmac = computeHmac(payload, sessionToken)

    if (MessageDigest.isEqual(receivedHmac, expectedHmac)) payload else null
  }

  /** Compute truncated HMAC directly into a destination array, avoiding intermediate allocations. */
  private def computeHmacInto(data: Array[Byte], key: Array[Byte], dest: Array[Byte], destOffset: Int): Unit = {
    val mac = threadLocalMac.get()
    val cachedKey = threadLocalKey.get()
    if (cachedKey == null || !Arrays.equals(cachedKey, key)) {
      mac.init(new SecretKeySpec(key, ALGORITHM))
      threadLocalKey.set(key.clone())
    }
    val fullHmac = mac.doFinal(data)
    System.arraycopy(fullHmac, 0, dest, destOffset, Constants.HMAC_SIZE)
  }

  private def computeHmac(data: Array[Byte], key: Array[Byte]): Array[Byte] = {
    val mac = threadLocalMac.get()
    val cachedKey = threadLocalKey.get()
    if (cachedKey == null || !Arrays.equals(cachedKey, key)) {
      mac.init(new SecretKeySpec(key, ALGORITHM))
      threadLocalKey.set(key.clone())
    }
    val fullHmac = mac.doFinal(data)
    val truncated = new Array[Byte](Constants.HMAC_SIZE)
    System.arraycopy(fullHmac, 0, truncated, 0, Constants.HMAC_SIZE)
    truncated
  }
}
