package com.gridgame.server

import com.gridgame.common.model.Player
import com.gridgame.common.observability.Attrs
import com.gridgame.common.observability.Metrics
import com.gridgame.common.protocol._
import com.gridgame.server.lobby.{LobbyManager, LobbyStatus}
import com.gridgame.server.net.{Outbox, RateLimiter}

import java.util.UUID
import scala.jdk.CollectionConverters._

/**
 * Chat: a line in a lobby goes to everyone in it while it waits, a line in a match to everyone in
 * the match, a team line to the sender's team. Rate limited per player, and stripped of anything
 * that isn't printable ASCII.
 */
final class ChatRelay(lobbyManager: LobbyManager, rateLimiter: RateLimiter, outbox: Outbox,
                      connectedPlayer: UUID => Player) {
  def handle(packet: ChatMessagePacket): Unit = {
    val playerId = packet.getPlayerId
    val player = connectedPlayer(playerId)
    if (player == null) return

    if (!rateLimiter.allowChat(playerId)) {
      Metrics.rateLimitTriggered.add(1L, Attrs.RlChat)
      return
    }

    // Sanitize: strip control chars, trim, reject empty
    val sanitized = packet.getMessage.filter(c => c >= 32 && c < 127).trim
    if (sanitized.isEmpty) return

    Metrics.chatMessages.add(1L, packet.getScope match {
      case ChatScope.LOBBY => Attrs.ChatLobby
      case ChatScope.GAME => Attrs.ChatGame
      case ChatScope.TEAM => Attrs.ChatTeam
      case _ => Attrs.ChatLobby
    })

    val lobby = lobbyManager.getPlayerLobby(playerId)
    if (lobby == null) return

    val broadcastPacket = new ChatMessagePacket(
      outbox.nextSeq(), playerId, Packet.getCurrentTimestamp,
      packet.getScope, sanitized
    )

    packet.getScope match {
      case ChatScope.LOBBY if lobby.status == LobbyStatus.WAITING =>
        val data = broadcastPacket.serialize()
        lobby.players.forEach { pid =>
          val p = connectedPlayer(pid)
          if (p != null) outbox.sendRaw(data, true, p)
        }

      case ChatScope.GAME if lobby.status == LobbyStatus.IN_GAME && lobby.gameInstance != null =>
        lobby.gameInstance.broadcastToInstance(broadcastPacket)

      case ChatScope.TEAM if lobby.status == LobbyStatus.IN_GAME && lobby.gameInstance != null && lobby.gameMode == 1 =>
        val instance = lobby.gameInstance
        val senderTeam: java.lang.Byte = instance.teamAssignments.get(playerId)
        if (senderTeam != null) {
          val data = broadcastPacket.serialize()
          instance.registry.getAll.asScala.foreach { p =>
            val pTeam: java.lang.Byte = instance.teamAssignments.get(p.getId)
            if (pTeam != null && pTeam.byteValue() == senderTeam.byteValue()) {
              outbox.sendRaw(data, true, p)
            }
          }
        }

      case _ => // Invalid scope for current state, ignore
    }
  }
}
