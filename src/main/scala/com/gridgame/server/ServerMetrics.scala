package com.gridgame.server

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import scala.jdk.CollectionConverters._

class ServerMetrics {
  private val startTime = System.currentTimeMillis()

  // Counters (monotonically increasing)
  private val counters = new ConcurrentHashMap[String, AtomicLong]()

  // Gauges (current values)
  private val gauges = new ConcurrentHashMap[String, AtomicLong]()

  // Time-series ring buffer: last 300 seconds of data per metric
  private val HISTORY_SIZE = 300
  private val counterHistory = new ConcurrentHashMap[String, Array[Long]]()
  private val gaugeHistory = new ConcurrentHashMap[String, Array[Long]]()
  private val counterPrevValues = new ConcurrentHashMap[String, AtomicLong]()
  @volatile private var historyIndex = 0

  // Snapshot executor
  private val snapshotExecutor = Executors.newSingleThreadScheduledExecutor()

  def start(): Unit = {
    snapshotExecutor.scheduleAtFixedRate(
      new Runnable { def run(): Unit = snapshot() },
      1, 1, TimeUnit.SECONDS
    )
  }

  def stop(): Unit = {
    snapshotExecutor.shutdown()
  }

  // Counter operations
  def increment(name: String): Unit = {
    counters.computeIfAbsent(name, _ => { counterHistory.putIfAbsent(name, new Array[Long](HISTORY_SIZE)); new AtomicLong(0) }).incrementAndGet()
  }

  def add(name: String, delta: Long): Unit = {
    counters.computeIfAbsent(name, _ => { counterHistory.putIfAbsent(name, new Array[Long](HISTORY_SIZE)); new AtomicLong(0) }).addAndGet(delta)
  }

  def getCounter(name: String): Long = {
    val c = counters.get(name)
    if (c != null) c.get() else 0L
  }

  // Gauge operations
  def setGauge(name: String, value: Long): Unit = {
    gauges.computeIfAbsent(name, _ => { gaugeHistory.putIfAbsent(name, new Array[Long](HISTORY_SIZE)); new AtomicLong(0) }).set(value)
  }

  def getGauge(name: String): Long = {
    val g = gauges.get(name)
    if (g != null) g.get() else 0L
  }

  private def snapshot(): Unit = {
    val idx = historyIndex % HISTORY_SIZE

    // Snapshot counter deltas (rate per second)
    counters.forEach { (name, counter) =>
      val current = counter.get()
      val prev = counterPrevValues.computeIfAbsent(name, _ => new AtomicLong(0))
      val delta = current - prev.get()
      prev.set(current)
      val hist = counterHistory.get(name)
      if (hist != null) hist(idx) = delta
    }

    // Snapshot gauge values
    gauges.forEach { (name, gauge) =>
      val hist = gaugeHistory.get(name)
      if (hist != null) hist(idx) = gauge.get()
    }

    historyIndex += 1
  }

  def getUptimeSeconds: Long = (System.currentTimeMillis() - startTime) / 1000

  def toJson: String = {
    val sb = new StringBuilder
    sb.append("{")

    // Uptime
    sb.append("\"uptime\":").append(getUptimeSeconds)

    // Counters
    sb.append(",\"counters\":{")
    var first = true
    counters.forEach { (name, value) =>
      if (!first) sb.append(",")
      sb.append("\"").append(name).append("\":").append(value.get())
      first = false
    }
    sb.append("}")

    // Gauges
    sb.append(",\"gauges\":{")
    first = true
    gauges.forEach { (name, value) =>
      if (!first) sb.append(",")
      sb.append("\"").append(name).append("\":").append(value.get())
      first = false
    }
    sb.append("}")

    // Counter history (rates per second)
    sb.append(",\"counterHistory\":{")
    first = true
    val currentIdx = historyIndex
    counterHistory.forEach { (name, hist) =>
      if (!first) sb.append(",")
      sb.append("\"").append(name).append("\":[")
      val len = Math.min(currentIdx, HISTORY_SIZE)
      for (i <- 0 until len) {
        if (i > 0) sb.append(",")
        val idx = (currentIdx - len + i) % HISTORY_SIZE
        sb.append(hist(idx))
      }
      sb.append("]")
      first = false
    }
    sb.append("}")

    // Gauge history
    sb.append(",\"gaugeHistory\":{")
    first = true
    gaugeHistory.forEach { (name, hist) =>
      if (!first) sb.append(",")
      sb.append("\"").append(name).append("\":[")
      val len = Math.min(currentIdx, HISTORY_SIZE)
      for (i <- 0 until len) {
        if (i > 0) sb.append(",")
        val idx = (currentIdx - len + i) % HISTORY_SIZE
        sb.append(hist(idx))
      }
      sb.append("]")
      first = false
    }
    sb.append("}")

    sb.append("}")
    sb.toString()
  }
}
