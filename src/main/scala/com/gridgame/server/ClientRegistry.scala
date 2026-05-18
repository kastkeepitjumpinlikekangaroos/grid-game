package com.gridgame.server

import com.gridgame.common.Constants
import com.gridgame.common.model.Player
import com.gridgame.common.observability.Attrs
import com.gridgame.common.observability.Metrics
import io.netty.channel.Channel

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import scala.jdk.CollectionConverters._

class ClientRegistry {
  private val players = new ConcurrentHashMap[UUID, Player]()
  private val channelToPlayer = new ConcurrentHashMap[Channel, UUID]()

  def add(player: Player): Unit = {
    val isNew = players.put(player.getId, player) == null
    val ch = player.getTcpChannel
    if (ch != null) channelToPlayer.put(ch.asInstanceOf[Channel], player.getId)
    // Count each fresh entry into an instance registry — covers human joins through
    // LobbyHandler/RankedQueue, bot setup, and (re)connects through ClientHandler.
    if (isNew) Metrics.characterPlayed.add(1L, Attrs.character(player.getCharacterId))
  }

  def remove(playerId: UUID): Unit = {
    val player = players.remove(playerId)
    if (player != null) {
      val ch = player.getTcpChannel
      if (ch != null) channelToPlayer.remove(ch.asInstanceOf[Channel])
    }
  }

  def get(playerId: UUID): Player = {
    players.get(playerId)
  }

  /** Returns a snapshot copy of all players. Use only when a genuine snapshot is needed. */
  def getAll: java.util.List[Player] = {
    new java.util.ArrayList[Player](players.values())
  }

  /** Returns the live values collection — no copy. Safe for iteration but reflects concurrent changes. */
  def getPlayerValues: java.util.Collection[Player] = players.values()

  /** Iterate all players without allocating a collection. */
  def forEachPlayer(fn: Player => Unit): Unit = {
    val iter = players.values().iterator()
    while (iter.hasNext) {
      fn(iter.next())
    }
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

  def getByChannel(channel: Channel): Player = {
    val playerId = channelToPlayer.get(channel)
    if (playerId != null) players.get(playerId) else null
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
