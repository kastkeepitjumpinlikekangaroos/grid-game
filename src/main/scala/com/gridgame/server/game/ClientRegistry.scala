package com.gridgame.server.game

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
  // The character each player who has left was playing: a leaver is still scored at the end of
  // the match (MatchEnd), and saved with the character they played
  private val departed = new ConcurrentHashMap[UUID, java.lang.Byte]()

  def add(player: Player): Unit = {
    departed.remove(player.getId)
    val isNew = players.put(player.getId, player) == null
    val ch = player.getTcpChannel
    if (ch != null) channelToPlayer.put(ch.asInstanceOf[Channel], player.getId)
    // Count each fresh entry into an instance registry — covers human joins through
    // LobbyHandler/RankedQueue, bot setup, and (re)connects through ClientHandler.
    if (isNew) Metrics.characterPlayed.add(1L, Attrs.character(player.getCharacterId))
  }

  /** The player's client has come back on another connection: it is theirs now, so a disconnect of
    * it takes them out, and the old one is forgotten. */
  def rebind(player: Player, channel: Channel): Unit = {
    if (channel == null) return
    val old = player.getTcpChannel
    if (old != null) channelToPlayer.remove(old.asInstanceOf[Channel], player.getId)
    player.setTcpChannel(channel)
    channelToPlayer.put(channel, player.getId)
  }

  def remove(playerId: UUID): Unit = {
    val player = players.remove(playerId)
    if (player != null) {
      departed.put(playerId, player.getCharacterId)
      val ch = player.getTcpChannel
      if (ch != null) channelToPlayer.remove(ch.asInstanceOf[Channel])
    }
  }

  def get(playerId: UUID): Player = {
    players.get(playerId)
  }

  /** The character the player is playing, or was when they left; None for someone never here. */
  def characterOf(playerId: UUID): Option[Byte] = {
    val player = players.get(playerId)
    if (player != null) Some(player.getCharacterId) else Option(departed.get(playerId)).map(_.byteValue)
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

  def getByChannel(channel: Channel): Player = {
    val playerId = channelToPlayer.get(channel)
    if (playerId != null) players.get(playerId) else null
  }

  def size: Int = players.size()

  def clear(): Unit = {
    players.clear()
    departed.clear()
  }
}
