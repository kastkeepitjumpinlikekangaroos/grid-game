package com.gridgame.server.game

import com.gridgame.common.Constants
import com.gridgame.common.model.BarrierCast
import com.gridgame.common.model.CharacterDef
import com.gridgame.common.model.DashBuff
import com.gridgame.common.model.Direction
import com.gridgame.common.model.GroundSlam
import com.gridgame.common.model.ItemType
import com.gridgame.common.model.PhaseShiftBuff
import com.gridgame.common.model.Player
import com.gridgame.common.model.Position
import com.gridgame.common.model.Teleport
import com.gridgame.common.model.Tile
import com.gridgame.common.model.TrapCast
import com.gridgame.common.model.TrapPlacement
import com.gridgame.common.observability.Attrs
import com.gridgame.common.observability.Metrics
import com.gridgame.common.protocol._
import io.netty.channel.Channel

import java.io.File
import java.net.InetSocketAddress
import java.util.UUID

/**
 * What the players of one match send it, judged against the server's own view of the match
 * (PacketValidator) before any of it is taken. Each kind of request has a file of its own:
 *
 *  - JoinRequests   joining, rejoining and leaving
 *  - StepRequests   positions, phases and barriers
 *  - ShotRequests   firing
 *  - TrapRequests   throwing traps
 *  - ItemRequests   using items
 */
class ClientHandler(private[game] val instance: GameInstance, private[game] val validator: PacketValidator = new PacketValidator())
    extends JoinRequests with StepRequests with ShotRequests with TrapRequests with ItemRequests {
  private[game] val registry = instance.registry
  private[game] val projectileManager = instance.projectileManager
  private[game] val itemManager = instance.itemManager
  private[game] def outbox = instance.outbox

  /**
   * Process a packet received from a client.
   * @param packet the decoded packet
   * @param tcpChannel non-null if this packet arrived over TCP
   * @param udpSender non-null if this packet arrived over UDP (the sender's UDP address)
   * @return true if the packet should be broadcast to other players
   */
  def processPacket(packet: Packet, tcpChannel: Channel, udpSender: InetSocketAddress): Boolean = {
    val playerId = packet.getPlayerId

    packet.getType match {
      case PacketType.PLAYER_JOIN =>
        handlePlayerJoin(packet.asInstanceOf[PlayerJoinPacket], tcpChannel)

      case PacketType.PLAYER_UPDATE =>
        handlePlayerUpdate(packet.asInstanceOf[PlayerUpdatePacket], udpSender)

      case PacketType.PLAYER_LEAVE =>
        handlePlayerLeave(packet.asInstanceOf[PlayerLeavePacket])

      case PacketType.HEARTBEAT =>
        handleHeartbeat(playerId, udpSender)

      case PacketType.PROJECTILE_UPDATE =>
        handleProjectileUpdate(packet.asInstanceOf[ProjectilePacket])

      case PacketType.ITEM_UPDATE =>
        handleItemUpdate(packet.asInstanceOf[ItemPacket])

      case PacketType.TRAP_UPDATE =>
        handleTrapUpdate(packet.asInstanceOf[TrapPacket])

      case _ =>
        System.err.println(s"Unknown packet type: ${packet.getType}")
        false
    }
  }

  private def handleHeartbeat(playerId: UUID, udpSender: InetSocketAddress): Boolean = {
    val player = registry.get(playerId)

    if (player != null) {
      player.updateHeartbeat()
      if (udpSender != null) {
        player.setUdpAddress(udpSender)
      }
    } else {
      System.err.println(s"Received heartbeat from unknown player: $playerId")
    }

    false
  }

  /** Tell everyone in the match the player's state as the server has it. */
  private[game] def broadcastState(player: Player): Unit = {
    instance.broadcastPlayerUpdate(instance.stateUpdate(player))
  }

  /** A new life: every attack off cooldown, as the player's own client has it. */
  def resetAttacks(playerId: UUID): Unit = validator.resetAttackClocks(playerId)

  /** Notify that a player's ability projectile hit a target. Reduces server-side fire-rate cooldown tracking to mirror client-side on-hit cooldown reduction. */
  def notifyAbilityHit(playerId: UUID, projectileType: Byte, characterId: Byte): Unit = {
    validator.reduceAbilityCooldown(playerId, projectileType, characterId)
  }

  def handleDisconnect(channel: Channel): Unit = {
    val player = registry.getByChannel(channel)
    if (player != null) {
      println(s"Player disconnected (TCP): ${player.getId.toString.substring(0, 8)} ('${player.getName}')")
      removePlayer(player.getId)
    }
  }

  /** Take a player out of the match (they disconnected or left it) and tell the others.
    * Their kills and deaths stay with the kill tracker, so they are still scored. */
  def removePlayer(playerId: UUID): Unit = {
    if (registry.get(playerId) == null) return
    registry.remove(playerId)
    itemManager.clearInventory(playerId)
    clearTrapsOf(playerId)
    validator.removePlayer(playerId)
    lastCorrectionAt.remove(playerId)
    refusedSince.remove(playerId)

    val leavePacket = new PlayerLeavePacket(outbox.nextSeq(), playerId)
    instance.broadcastToInstance(leavePacket)
  }
}
