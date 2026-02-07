package com.gridgame.server

import com.gridgame.common.Constants
import com.gridgame.common.model.Player

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import scala.collection.mutable.ArrayBuffer
import scala.jdk.CollectionConverters._

class ClientRegistry {
  private val players = new ConcurrentHashMap[UUID, Player]()

  def add(player: Player): Unit = {
    players.put(player.getId, player)
  }

  def remove(playerId: UUID): Unit = {
    players.remove(playerId)
  }

  def get(playerId: UUID): Player = {
    players.get(playerId)
  }

  def getAll: java.util.List[Player] = {
    new java.util.ArrayList[Player](players.values())
  }

  def contains(playerId: UUID): Boolean = {
    players.containsKey(playerId)
  }

  def updateHeartbeat(playerId: UUID): Unit = {
    val player = players.get(playerId)
    if (player != null) {
      player.updateHeartbeat()
    }
  }

  def getTimedOutClients: java.util.List[UUID] = {
    val now = System.currentTimeMillis()
    val timeout = Constants.CLIENT_TIMEOUT_MS
    val timedOut = new java.util.ArrayList[UUID]()

    players.values().asScala.foreach { player =>
      if (now - player.getLastUpdateTime > timeout) {
        timedOut.add(player.getId)
      }
    }

    timedOut
  }

  def size: Int = players.size()

  def clear(): Unit = {
    players.clear()
  }
}
