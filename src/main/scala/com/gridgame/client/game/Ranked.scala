package com.gridgame.client.game

import com.gridgame.common.Constants
import com.gridgame.common.protocol._

/** The ranked queue: joining and leaving it, the character we queue as, how long the wait is, and
  * the match it finds us, which is entered as a lobby is. */
trait Ranked { this: GameClient =>
  @volatile var isInRankedQueue: Boolean = false
  @volatile var rankedElo: Int = 1000
  // Set by the server's unsolicited post-ranked-match STATS push. Holds
  // (oldElo, newElo) so the scoreboard can show the change. Cleared on
  // GAME_OVER, so it never leaks into a casual match's post-game screen.
  @volatile var pendingEloChange: Option[(Int, Int)] = None
  @volatile var rankedQueueSize: Int = 0
  @volatile var rankedQueueWaitTime: Int = 0
  @volatile var rankedQueueListener: () => Unit = _
  @volatile var rankedMatchFoundListener: () => Unit = _

  private[game] def handleRankedQueue(packet: RankedQueuePacket): Unit = {
    packet.getAction match {
      case RankedQueueAction.QUEUE_STATUS =>
        rankedQueueSize = packet.getQueueSize & 0xFF
        rankedElo = packet.getElo.toInt
        rankedQueueWaitTime = packet.getWaitTimeSeconds
        fire(rankedQueueListener)

      case RankedQueueAction.MATCH_FOUND =>
        isInRankedQueue = false
        currentLobbyId = packet.getLobbyId
        currentLobbyName = packet.getLobbyName
        currentLobbyMapIndex = packet.getMapIndex & 0xFF
        currentLobbyDuration = packet.getDurationMinutes & 0xFF
        currentLobbyPlayerCount = packet.getPlayerCount & 0xFF
        currentLobbyMaxPlayers = packet.getMaxPlayers & 0xFF
        // Set both ways: a Teams value left over from an earlier lobby made an FFA or duel
        // scoreboard group everyone under one team.
        if (packet.getMode == RankedQueueMode.TEAMS) {
          currentLobbyGameMode = 1
          currentLobbyTeamSize = Constants.TEAMS_TEAM_SIZE
        } else {
          currentLobbyGameMode = 0
          currentLobbyTeamSize = 2
        }
        clientState = ClientState.IN_LOBBY
        if (rankedMatchFoundListener != null) rankedMatchFoundListener()

      case _ =>
    }
  }

  def queueRanked(mode: Byte = RankedQueueMode.FFA): Unit = {
    isInRankedQueue = true
    val packet = new RankedQueuePacket(
      sequenceNumber.getAndIncrement(), localPlayerId,
      RankedQueueAction.QUEUE_JOIN, selectedCharacterId, mode
    )
    send(packet)
  }

  def leaveRankedQueue(): Unit = {
    isInRankedQueue = false
    val packet = new RankedQueuePacket(
      sequenceNumber.getAndIncrement(), localPlayerId, RankedQueueAction.QUEUE_LEAVE
    )
    send(packet)
  }

  def changeRankedCharacter(id: Byte): Unit = {
    selectedCharacterId = id
    if (isInRankedQueue) {
      val packet = new RankedQueuePacket(
        sequenceNumber.getAndIncrement(), localPlayerId,
        RankedQueueAction.CHARACTER_CHANGE, id
      )
      send(packet)
    }
  }
}
