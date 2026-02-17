package com.gridgame.server

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

  private val MAX_UDP_PER_SECOND = 60
  private val MAX_TCP_PER_SECOND = 20
  private val MAX_CONNECTIONS_PER_MINUTE = 5
  private val MAX_AUTH_FAILURES = 5
  private val AUTH_COOLDOWN_MS = 30000L

  def allowPacket(clientId: UUID, isUdp: Boolean): Boolean = {
    if (isUdp) {
      val counter = udpCounts.computeIfAbsent(clientId, _ => new WindowCounter())
      counter.allowAndIncrement(MAX_UDP_PER_SECOND, 1000L)
    } else {
      val counter = tcpCounts.computeIfAbsent(clientId, _ => new WindowCounter())
      counter.allowAndIncrement(MAX_TCP_PER_SECOND, 1000L)
    }
  }

  def allowConnection(address: InetAddress): Boolean = {
    val counter = connectionCounts.computeIfAbsent(address, _ => new WindowCounter())
    counter.allowAndIncrement(MAX_CONNECTIONS_PER_MINUTE, 60000L)
  }

  def allowAuthAttempt(address: InetAddress): Boolean = {
    val tracker = authFailures.computeIfAbsent(address, _ => new AuthTracker())
    val now = System.currentTimeMillis()
    if (now - tracker.lastFailureTime.get() > AUTH_COOLDOWN_MS) {
      tracker.failures.set(0)
      return true
    }
    tracker.failures.get() < MAX_AUTH_FAILURES
  }

  def recordAuthFailure(address: InetAddress): Unit = {
    val tracker = authFailures.computeIfAbsent(address, _ => new AuthTracker())
    tracker.failures.incrementAndGet()
    tracker.lastFailureTime.set(System.currentTimeMillis())
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
      if (now - entry.getValue.lastFailureTime.get() > AUTH_COOLDOWN_MS * 2) authIter.remove()
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

  def allowAndIncrement(maxCount: Int, windowMs: Long): Boolean = {
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
