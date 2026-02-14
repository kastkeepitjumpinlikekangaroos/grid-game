package com.gridgame.server

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import scala.jdk.CollectionConverters._

class KillTracker {
  private val kills = new ConcurrentHashMap[UUID, AtomicInteger]()
  private val deaths = new ConcurrentHashMap[UUID, AtomicInteger]()

  def registerPlayer(playerId: UUID): Unit = {
    kills.putIfAbsent(playerId, new AtomicInteger(0))
    deaths.putIfAbsent(playerId, new AtomicInteger(0))
  }

  def recordKill(killerId: UUID, victimId: UUID): Unit = {
    kills.computeIfAbsent(killerId, _ => new AtomicInteger(0)).incrementAndGet()
    deaths.computeIfAbsent(victimId, _ => new AtomicInteger(0)).incrementAndGet()
  }

  def getKills(playerId: UUID): Int = {
    val counter = kills.get(playerId)
    if (counter != null) counter.get() else 0
  }

  def getDeaths(playerId: UUID): Int = {
    val counter = deaths.get(playerId)
    if (counter != null) counter.get() else 0
  }

  /** Returns (playerId, kills, deaths) sorted by kills descending. */
  def getScoreboard: Seq[(UUID, Int, Int)] = {
    kills.keySet().asScala.toSeq.map { playerId =>
      (playerId, getKills(playerId), getDeaths(playerId))
    }.sortBy(-_._2)
  }
}
