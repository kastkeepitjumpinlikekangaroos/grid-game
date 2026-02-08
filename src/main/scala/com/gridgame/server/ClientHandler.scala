package com.gridgame.server

import com.gridgame.common.Constants
import com.gridgame.common.model.Player
import com.gridgame.common.model.Position
import com.gridgame.common.protocol._

import java.net.InetAddress
import java.util.UUID

class ClientHandler(registry: ClientRegistry, server: GameServer, projectileManager: ProjectileManager) {

  def processPacket(packet: Packet, address: InetAddress, port: Int): Boolean = {
    val playerId = packet.getPlayerId

    packet.getType match {
      case PacketType.PLAYER_JOIN =>
        handlePlayerJoin(packet.asInstanceOf[PlayerJoinPacket], address, port)

      case PacketType.PLAYER_UPDATE =>
        handlePlayerUpdate(packet.asInstanceOf[PlayerUpdatePacket], address, port)

      case PacketType.PLAYER_LEAVE =>
        handlePlayerLeave(packet.asInstanceOf[PlayerLeavePacket])

      case PacketType.HEARTBEAT =>
        handleHeartbeat(playerId, address, port)

      case PacketType.PROJECTILE_UPDATE =>
        handleProjectileUpdate(packet.asInstanceOf[ProjectilePacket], address, port)

      case _ =>
        System.err.println(s"Unknown packet type: ${packet.getType}")
        false
    }
  }

  private def handleProjectileUpdate(packet: ProjectilePacket, address: InetAddress, port: Int): Boolean = {
    // Only handle spawn requests from clients
    if (packet.getAction != ProjectileAction.SPAWN) {
      return false // Ignore non-spawn packets from clients
    }

    val playerId = packet.getPlayerId
    val player = registry.get(playerId)

    if (player == null) {
      System.err.println(s"Received projectile spawn from unknown player: $playerId")
      return false
    }

    // Spawn projectile at player's position with velocity from packet
    val projectile = projectileManager.spawnProjectile(
      playerId,
      packet.getX.toInt,
      packet.getY.toInt,
      packet.getDx,
      packet.getDy,
      packet.getColorRGB
    )

    // Broadcast spawn to all clients
    server.broadcastProjectileSpawn(projectile)

    false // Don't auto-broadcast; we handled it
  }

  private def handlePlayerJoin(packet: PlayerJoinPacket, address: InetAddress, port: Int): Boolean = {
    val playerId = packet.getPlayerId

    // Send world info to the joining player
    if (server.worldFile.nonEmpty) {
      val worldInfoPacket = new WorldInfoPacket(server.getNextSequenceNumber, server.worldFile)
      println(s"Sending world info to client: ${server.worldFile}")
      server.sendPacketTo(worldInfoPacket, address, port)
    } else {
      println("No world file configured on server")
    }

    if (registry.contains(playerId)) {
      println(s"Player rejoining: $playerId")
      val existing = registry.get(playerId)
      existing.setPosition(packet.getPosition)
      existing.setColorRGB(packet.getColorRGB)
      existing.setName(packet.getPlayerName)
      existing.setHealth(Constants.MAX_HEALTH)
      existing.setAddress(address)
      existing.setPort(port)
      existing.updateHeartbeat()
      true
    } else {
      val player = new Player(playerId, packet.getPlayerName, packet.getPosition, packet.getColorRGB, Constants.MAX_HEALTH)
      player.setAddress(address)
      player.setPort(port)

      registry.add(player)

      println(s"Player joined: ${playerId.toString.substring(0, 8)} ('${packet.getPlayerName}') at ${packet.getPosition} from ${address.getHostAddress}:$port with health ${player.getHealth}")

      true
    }
  }

  private def handlePlayerUpdate(packet: PlayerUpdatePacket, address: InetAddress, port: Int): Boolean = {
    val playerId = packet.getPlayerId
    var player = registry.get(playerId)

    if (player == null) {
      System.err.println(s"Received update for unknown player: $playerId")
      player = new Player(playerId, "Player", packet.getPosition, packet.getColorRGB)
      player.setAddress(address)
      player.setPort(port)
      registry.add(player)
    } else {
      player.setPosition(packet.getPosition)
      player.setColorRGB(packet.getColorRGB)
      player.setAddress(address)
      player.setPort(port)
    }

    true
  }

  private def handlePlayerLeave(packet: PlayerLeavePacket): Boolean = {
    val playerId = packet.getPlayerId
    val player = registry.get(playerId)

    if (player != null) {
      registry.remove(playerId)
      println(s"Player left: ${playerId.toString.substring(0, 8)} ('${player.getName}')")
    }

    true
  }

  private def handleHeartbeat(playerId: UUID, address: InetAddress, port: Int): Boolean = {
    val player = registry.get(playerId)

    if (player != null) {
      player.updateHeartbeat()
      player.setAddress(address)
      player.setPort(port)
    } else {
      System.err.println(s"Received heartbeat from unknown player: $playerId")
    }

    false
  }

  def handleTimeout(playerId: UUID, sequenceNumber: Int): PlayerLeavePacket = {
    val player = registry.get(playerId)
    if (player != null) {
      registry.remove(playerId)
      println(s"Player timed out: ${playerId.toString.substring(0, 8)} ('${player.getName}')")
      new PlayerLeavePacket(sequenceNumber, playerId)
    } else {
      null
    }
  }
}
