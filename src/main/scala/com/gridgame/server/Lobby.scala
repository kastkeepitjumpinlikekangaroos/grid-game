package com.gridgame.server

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

object LobbyStatus {
  val WAITING: Byte = 0
  val IN_GAME: Byte = 1
  val FINISHED: Byte = 2
}

class Lobby(
    val id: Short,
    val hostId: UUID,
    var name: String,
    var mapIndex: Int,
    var durationMinutes: Int,
    var maxPlayers: Int
) {
  val players: CopyOnWriteArrayList[UUID] = new CopyOnWriteArrayList[UUID]()
  val characterSelections: ConcurrentHashMap[UUID, Byte] = new ConcurrentHashMap[UUID, Byte]()
  @volatile var status: Byte = LobbyStatus.WAITING
  @volatile var gameInstance: GameInstance = _
  @volatile var isRanked: Boolean = false

  def addPlayer(playerId: UUID): Boolean = {
    if (players.size() >= maxPlayers) return false
    if (players.contains(playerId)) return false
    players.add(playerId)
    true
  }

  def removePlayer(playerId: UUID): Boolean = {
    players.remove(playerId)
  }

  def isHost(playerId: UUID): Boolean = hostId.equals(playerId)

  def setCharacter(playerId: UUID, charId: Byte): Unit = {
    characterSelections.put(playerId, charId)
  }

  def getCharacter(playerId: UUID): Byte = {
    characterSelections.getOrDefault(playerId, 0.toByte)
  }

  def playerCount: Int = players.size()
}
