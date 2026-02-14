package com.gridgame.server

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import scala.jdk.CollectionConverters._

class LobbyManager {
  private val lobbies = new ConcurrentHashMap[Short, Lobby]()
  private val playerLobby = new ConcurrentHashMap[UUID, Short]()
  private val nextId = new AtomicInteger(1)

  def createLobby(hostId: UUID, name: String, mapIndex: Int, durationMinutes: Int, maxPlayers: Int): Lobby = {
    val id = nextId.getAndIncrement().toShort
    val lobby = new Lobby(id, hostId, name, mapIndex, durationMinutes, maxPlayers)
    lobby.addPlayer(hostId)
    lobbies.put(id, lobby)
    playerLobby.put(hostId, id)
    println(s"LobbyManager: Created lobby $id '$name' by ${hostId.toString.substring(0, 8)}")
    lobby
  }

  def joinLobby(playerId: UUID, lobbyId: Short): Lobby = {
    val lobby = lobbies.get(lobbyId)
    if (lobby == null) return null
    if (lobby.status != LobbyStatus.WAITING) return null
    if (!lobby.addPlayer(playerId)) return null
    playerLobby.put(playerId, lobbyId)
    println(s"LobbyManager: Player ${playerId.toString.substring(0, 8)} joined lobby $lobbyId")
    lobby
  }

  def leaveLobby(playerId: UUID): Lobby = {
    val lobbyId: java.lang.Short = playerLobby.remove(playerId)
    if (lobbyId == null) return null
    val lobby = lobbies.get(lobbyId.shortValue())
    if (lobby != null) {
      lobby.removePlayer(playerId)
      println(s"LobbyManager: Player ${playerId.toString.substring(0, 8)} left lobby $lobbyId")
    }
    lobby
  }

  def getLobby(lobbyId: Short): Lobby = lobbies.get(lobbyId)

  def getPlayerLobby(playerId: UUID): Lobby = {
    val lobbyId: java.lang.Short = playerLobby.get(playerId)
    if (lobbyId != null) lobbies.get(lobbyId.shortValue()) else null
  }

  def getActiveLobbies: Seq[Lobby] = {
    lobbies.values().asScala.filter(_.status != LobbyStatus.FINISHED).toSeq
  }

  def removeLobby(lobbyId: Short): Unit = {
    val lobby = lobbies.remove(lobbyId)
    if (lobby != null) {
      import scala.jdk.CollectionConverters._
      lobby.players.asScala.foreach(playerLobby.remove)
      println(s"LobbyManager: Removed lobby $lobbyId")
    }
  }

  def setPlayerLobby(playerId: UUID, lobbyId: Short): Unit = {
    playerLobby.put(playerId, lobbyId)
  }
}
