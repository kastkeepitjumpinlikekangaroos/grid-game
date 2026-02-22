package com.gridgame.server

import io.netty.channel.Channel

import java.net.InetAddress
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class RateLimiter {
  // Per-client packet rate limiting (sliding window)
  private val udpCounts = new ConcurrentHashMap[UUID, WindowCounter]()
  private val tcpCounts = new ConcurrentHashMap[UUID, WindowCounter]()

  // Per-IP connection rate limiting
  private val connectionCounts = new ConcurrentHashMap[InetAddress, WindowCounter]()

  // Per-IP auth failure tracking
  private val authFailures = new ConcurrentHashMap[InetAddress, AuthTracker]()

  // Per-channel rate limiting for pre-auth packets (before player ID is known)
  private val channelCounts = new ConcurrentHashMap[Channel, WindowCounter]()
  private val MAX_PRE_AUTH_PER_SECOND = 5

  private val MAX_UDP_PER_SECOND = 120
  private val MAX_TCP_PER_SECOND = 40
  private val MAX_CONNECTIONS_PER_MINUTE = 5
  private val MAX_AUTH_FAILURES = 5
  private val BASE_COOLDOWN_MS = 30000L  // 30s base, doubles each batch of failures
  private val MAX_COOLDOWN_MS = 3600000L // 1 hour cap

  def allowPacket(clientId: UUID, isUdp: Boolean): Boolean = {
    if (isUdp) {
      val counter = udpCounts.computeIfAbsent(clientId, _ => new WindowCounter())
      counter.allowAndIncrement(MAX_UDP_PER_SECOND, 1000L)
    } else {
      val counter = tcpCounts.computeIfAbsent(clientId, _ => new WindowCounter())
      counter.allowAndIncrement(MAX_TCP_PER_SECOND, 1000L)
    }
  }

  /** Rate limit packets on a channel before player ID is known (pre-auth). */
  def allowPreAuthPacket(channel: Channel): Boolean = {
    val counter = channelCounts.computeIfAbsent(channel, _ => new WindowCounter())
    counter.allowAndIncrement(MAX_PRE_AUTH_PER_SECOND, 1000L)
  }

  def removeChannel(channel: Channel): Unit = {
    channelCounts.remove(channel)
  }

  def allowConnection(address: InetAddress): Boolean = {
    val counter = connectionCounts.computeIfAbsent(address, _ => new WindowCounter())
    counter.allowAndIncrement(MAX_CONNECTIONS_PER_MINUTE, 60000L)
  }

  def allowAuthAttempt(address: InetAddress): Boolean = {
    val tracker = authFailures.computeIfAbsent(address, _ => new AuthTracker())
    tracker.synchronized {
      val now = System.currentTimeMillis()
      val failures = tracker.failures.get()
      if (failures >= MAX_AUTH_FAILURES) {
        // Exponential backoff: 30s, 60s, 120s, 240s... capped at 1 hour
        val cooldownBatch = (failures / MAX_AUTH_FAILURES) - 1
        val cooldown = Math.min(BASE_COOLDOWN_MS << Math.min(cooldownBatch, 6), MAX_COOLDOWN_MS)
        if (now - tracker.lastFailureTime.get() > cooldown) {
          // Cooldown expired — allow one more batch but keep progressive penalty
          true
        } else {
          // Still in cooldown — block entirely
          false
        }
      } else {
        // Under the failure threshold — allow
        true
      }
    }
  }

  def recordAuthFailure(address: InetAddress): Unit = {
    val tracker = authFailures.computeIfAbsent(address, _ => new AuthTracker())
    tracker.synchronized {
      tracker.failures.incrementAndGet()
      tracker.lastFailureTime.set(System.currentTimeMillis())
    }
  }

  def clearAuthFailures(address: InetAddress): Unit = {
    authFailures.remove(address)
  }

  def cleanup(): Unit = {
    val now = System.currentTimeMillis()
    val staleThreshold = 60000L

    cleanupMap(udpCounts, now, staleThreshold)
    cleanupMap(tcpCounts, now, staleThreshold)

    val connIter = connectionCounts.entrySet().iterator()
    while (connIter.hasNext) {
      if (now - connIter.next().getValue.windowStart.get() > staleThreshold) connIter.remove()
    }

    val authIter = authFailures.entrySet().iterator()
    while (authIter.hasNext) {
      val entry = authIter.next()
      if (now - entry.getValue.lastFailureTime.get() > MAX_COOLDOWN_MS * 2) authIter.remove()
    }
  }

  private def cleanupMap[K](map: ConcurrentHashMap[K, WindowCounter], now: Long, threshold: Long): Unit = {
    val iter = map.entrySet().iterator()
    while (iter.hasNext) {
      if (now - iter.next().getValue.windowStart.get() > threshold) iter.remove()
    }
  }
}

private class WindowCounter {
  val windowStart = new AtomicLong(System.currentTimeMillis())
  val count = new AtomicInteger(0)

  def allowAndIncrement(maxCount: Int, windowMs: Long): Boolean = synchronized {
    val now = System.currentTimeMillis()
    if (now - windowStart.get() > windowMs) {
      windowStart.set(now)
      count.set(1)
      true
    } else {
      count.incrementAndGet() <= maxCount
    }
  }
}

private class AuthTracker {
  val failures = new AtomicInteger(0)
  val lastFailureTime = new AtomicLong(0)
}
