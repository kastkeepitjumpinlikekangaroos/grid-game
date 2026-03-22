package com.gridgame.server

import java.util.concurrent.locks.ReentrantLock

object EventCategory {
  val AUTH = "AUTH"
  val NETWORK = "NETWORK"
  val GAME = "GAME"
  val LOBBY = "LOBBY"
  val SECURITY = "SECURITY"
  val VALIDATION = "VALIDATION"
}

object EventLevel {
  val INFO = "INFO"
  val WARN = "WARN"
  val ERROR = "ERROR"
}

case class ServerEvent(
  timestamp: Long,
  category: String,
  level: String,
  message: String,
  details: Map[String, String] = Map.empty
)

class ServerEventLog(capacity: Int = 10000) {
  private val buffer = new Array[ServerEvent](capacity)
  private var writeIndex = 0L
  private val lock = new ReentrantLock()

  def log(category: String, level: String, message: String, details: Map[String, String] = Map.empty): Unit = {
    val event = ServerEvent(System.currentTimeMillis(), category, level, message, details)
    lock.lock()
    try {
      buffer((writeIndex % capacity).toInt) = event
      writeIndex += 1
    } finally {
      lock.unlock()
    }
  }

  def info(category: String, message: String, details: Map[String, String] = Map.empty): Unit =
    log(category, EventLevel.INFO, message, details)

  def warn(category: String, message: String, details: Map[String, String] = Map.empty): Unit =
    log(category, EventLevel.WARN, message, details)

  def error(category: String, message: String, details: Map[String, String] = Map.empty): Unit =
    log(category, EventLevel.ERROR, message, details)

  def query(
    category: Option[String] = None,
    level: Option[String] = None,
    player: Option[String] = None,
    search: Option[String] = None,
    since: Option[Long] = None,
    limit: Int = 100
  ): Seq[ServerEvent] = {
    lock.lock()
    try {
      val count = Math.min(writeIndex, capacity.toLong).toInt
      val startIdx = writeIndex - count
      val results = new scala.collection.mutable.ArrayBuffer[ServerEvent](Math.min(limit, count))

      // Iterate newest first
      var i = writeIndex - 1
      while (i >= startIdx && results.size < limit) {
        val event = buffer((i % capacity).toInt)
        if (event != null) {
          val matchesCategory = category.forall(_ == event.category)
          val matchesLevel = level.forall(_ == event.level)
          val matchesPlayer = player.forall(p =>
            event.details.get("playerId").exists(_.contains(p)) ||
            event.details.get("playerName").exists(_.toLowerCase.contains(p.toLowerCase))
          )
          val matchesSince = since.forall(event.timestamp >= _)
          val matchesSearch = search.forall(s =>
            event.message.toLowerCase.contains(s.toLowerCase) ||
            event.details.values.exists(_.toLowerCase.contains(s.toLowerCase))
          )
          if (matchesCategory && matchesLevel && matchesPlayer && matchesSince && matchesSearch) {
            results += event
          }
        }
        i -= 1
      }
      results.toSeq
    } finally {
      lock.unlock()
    }
  }

  def toJson(events: Seq[ServerEvent]): String = {
    val sb = new StringBuilder
    sb.append("[")
    var first = true
    events.foreach { event =>
      if (!first) sb.append(",")
      sb.append("{")
      sb.append("\"timestamp\":").append(event.timestamp)
      sb.append(",\"category\":\"").append(event.category).append("\"")
      sb.append(",\"level\":\"").append(event.level).append("\"")
      sb.append(",\"message\":\"").append(escapeJson(event.message)).append("\"")
      sb.append(",\"details\":{")
      var dfirst = true
      event.details.foreach { case (k, v) =>
        if (!dfirst) sb.append(",")
        sb.append("\"").append(escapeJson(k)).append("\":\"").append(escapeJson(v)).append("\"")
        dfirst = false
      }
      sb.append("}}")
      first = false
    }
    sb.append("]")
    sb.toString()
  }

  private def escapeJson(s: String): String = {
    s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")
  }
}
