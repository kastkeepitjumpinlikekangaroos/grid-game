package com.gridgame.server.lobby

import com.gridgame.common.Constants
import com.gridgame.server.bots.{BotManager, BotSlot}
import com.gridgame.server.game.GameInstance

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
  val botManager: BotManager = new BotManager()
  @volatile var status: Byte = LobbyStatus.WAITING
  @volatile var gameInstance: GameInstance = _
  @volatile var isRanked: Boolean = false
  @volatile var gameMode: Byte = 0  // 0=FFA, 1=Teams
  @volatile var teamSize: Int = 2   // 2, 3, or 4
  @volatile var matchType: Byte = 0 // 0=Casual FFA, 1=Casual Teams, 2=Ranked FFA, 3=Ranked Duel, 4=Ranked Teams

  // Seats are taken and resized under the lobby's lock (addPlayer, addBot, configure, and the
  // match starting in LobbyHandler.handleStart), each looking at the seats and changing them in one
  // step: a join and a bot the host added at the same moment each saw the last seat free, and both
  // took it. Giving one up needs no look first (removePlayer, BotManager.removeLastBot).

  def addPlayer(playerId: UUID): Boolean = this.synchronized {
    // A lobby whose match has started takes nobody: its players have been dealt their places
    if (status != LobbyStatus.WAITING) return false
    // Bots hold seats too: counting only humans let a 2v2 lobby of host + 3 bots take a fifth player.
    if (playerCount >= maxPlayers) return false
    if (players.contains(playerId)) return false
    players.add(playerId)
    true
  }

  /** A bot in the next free seat, or none when there isn't one. */
  def addBot(): Option[BotSlot] = this.synchronized {
    if (playerCount >= maxPlayers) None else Some(botManager.addBot())
  }

  /**
   * The host's mode: a free-for-all, or Teams of the size asked for (2 to 4 a side) fitted to the
   * humans here. A size too small for them grows to fit, and more humans than 4v4 holds can't play
   * Teams. Teams has seats for both teams and no more, so the bots that no longer fit go, the last
   * added first; they are returned.
   */
  def configure(requestedMode: Byte, requestedTeamSize: Int): Seq[BotSlot] = this.synchronized {
    val requestedSize = Math.max(2, Math.min(4, requestedTeamSize))
    val minTeamSize = (players.size + 1) / 2
    val teamsFit = minTeamSize <= 4
    gameMode = if (requestedMode == 1 && teamsFit) 1 else 0
    teamSize = if (teamsFit) Math.max(requestedSize, minTeamSize) else requestedSize
    if (gameMode == 1) {
      maxPlayers = teamSize * 2
      val dropped = Seq.newBuilder[BotSlot]
      while (playerCount > maxPlayers && botManager.botCount > 0) botManager.removeLastBot().foreach(dropped += _)
      dropped.result()
    } else {
      maxPlayers = Constants.MAX_LOBBY_PLAYERS
      Nil
    }
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

  def isPractice: Boolean = matchType == 5

  def playerCount: Int = players.size() + botManager.botCount
}
