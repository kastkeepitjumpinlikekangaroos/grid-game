package com.gridgame.server;

import com.gridgame.common.model.Player;
import com.gridgame.common.model.Position;
import com.gridgame.common.protocol.*;

import java.net.InetAddress;
import java.util.UUID;

/**
 * Handles processing of incoming packets and updates the client registry.
 */
public class ClientHandler {
    private final ClientRegistry registry;

    public ClientHandler(ClientRegistry registry) {
        this.registry = registry;
    }

    /**
     * Process an incoming packet and update the registry accordingly.
     *
     * @param packet The received packet
     * @param address The sender's address
     * @param port The sender's port
     * @return true if the packet was processed successfully and should be broadcast
     */
    public boolean processPacket(Packet packet, InetAddress address, int port) {
        UUID playerId = packet.getPlayerId();

        switch (packet.getType()) {
            case PLAYER_JOIN:
                return handlePlayerJoin((PlayerJoinPacket) packet, address, port);

            case PLAYER_UPDATE:
                return handlePlayerUpdate((PlayerUpdatePacket) packet, address, port);

            case PLAYER_LEAVE:
                return handlePlayerLeave((PlayerLeavePacket) packet);

            case HEARTBEAT:
                return handleHeartbeat(playerId, address, port);

            default:
                System.err.println("Unknown packet type: " + packet.getType());
                return false;
        }
    }

    private boolean handlePlayerJoin(PlayerJoinPacket packet, InetAddress address, int port) {
        UUID playerId = packet.getPlayerId();

        // Check if player already exists
        if (registry.contains(playerId)) {
            System.out.println("Player already joined: " + playerId);
            // Update their info
            Player existing = registry.get(playerId);
            existing.setPosition(packet.getPosition());
            existing.setColorRGB(packet.getColorRGB());
            existing.setName(packet.getPlayerName());
            existing.setAddress(address);
            existing.setPort(port);
            existing.updateHeartbeat();
            return true; // Broadcast the update
        }

        // Create new player
        Player player = new Player(playerId, packet.getPlayerName(),
            packet.getPosition(), packet.getColorRGB());
        player.setAddress(address);
        player.setPort(port);

        registry.add(player);

        System.out.printf("Player joined: %s ('%s') at %s from %s:%d%n",
            playerId.toString().substring(0, 8),
            packet.getPlayerName(),
            packet.getPosition(),
            address.getHostAddress(),
            port);

        return true; // Broadcast to all clients
    }

    private boolean handlePlayerUpdate(PlayerUpdatePacket packet, InetAddress address, int port) {
        UUID playerId = packet.getPlayerId();
        Player player = registry.get(playerId);

        if (player == null) {
            System.err.println("Received update for unknown player: " + playerId);
            // Create a default player entry
            player = new Player(playerId, "Player", packet.getPosition(), packet.getColorRGB());
            player.setAddress(address);
            player.setPort(port);
            registry.add(player);
        } else {
            // Update player position and color
            player.setPosition(packet.getPosition());
            player.setColorRGB(packet.getColorRGB());
            player.setAddress(address);
            player.setPort(port);
        }

        return true; // Broadcast to all clients
    }

    private boolean handlePlayerLeave(PlayerLeavePacket packet) {
        UUID playerId = packet.getPlayerId();
        Player player = registry.get(playerId);

        if (player != null) {
            registry.remove(playerId);
            System.out.printf("Player left: %s ('%s')%n",
                playerId.toString().substring(0, 8),
                player.getName());
        }

        return true; // Broadcast to all clients
    }

    private boolean handleHeartbeat(UUID playerId, InetAddress address, int port) {
        Player player = registry.get(playerId);

        if (player != null) {
            player.updateHeartbeat();
            player.setAddress(address);
            player.setPort(port);
        } else {
            System.err.println("Received heartbeat from unknown player: " + playerId);
        }

        return false; // Don't broadcast heartbeats
    }

    /**
     * Handle a timeout by removing the player and creating a LEAVE packet.
     */
    public PlayerLeavePacket handleTimeout(UUID playerId, int sequenceNumber) {
        Player player = registry.get(playerId);
        if (player != null) {
            registry.remove(playerId);
            System.out.printf("Player timed out: %s ('%s')%n",
                playerId.toString().substring(0, 8),
                player.getName());

            return new PlayerLeavePacket(sequenceNumber, playerId);
        }
        return null;
    }
}
