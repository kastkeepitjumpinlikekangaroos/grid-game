package com.gridgame.server;

import com.gridgame.common.Constants;
import com.gridgame.common.model.Player;
import com.gridgame.common.protocol.Packet;
import com.gridgame.common.protocol.PacketSerializer;
import com.gridgame.common.protocol.PlayerLeavePacket;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Main game server that handles UDP networking, client management, and broadcasting.
 */
public class GameServer {
    private final int port;
    private DatagramSocket socket;
    private final ClientRegistry registry;
    private final ClientHandler handler;
    private final ExecutorService broadcastExecutor;
    private final ScheduledExecutorService cleanupExecutor;
    private final AtomicInteger sequenceNumber;
    private volatile boolean running;

    public GameServer(int port) {
        this.port = port;
        this.registry = new ClientRegistry();
        this.handler = new ClientHandler(registry);
        this.broadcastExecutor = Executors.newFixedThreadPool(4);
        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor();
        this.sequenceNumber = new AtomicInteger(0);
        this.running = false;
    }

    /**
     * Start the server.
     */
    public void start() throws SocketException {
        socket = new DatagramSocket(port);
        running = true;

        System.out.printf("Game server started on port %d%n", port);

        // Schedule cleanup task to run every 5 seconds
        cleanupExecutor.scheduleAtFixedRate(this::cleanup, 5, 5, TimeUnit.SECONDS);

        // Start receiving packets
        receiveLoop();
    }

    /**
     * Main receive loop - runs on the main thread.
     */
    private void receiveLoop() {
        byte[] buffer = new byte[Constants.PACKET_SIZE];

        while (running) {
            try {
                DatagramPacket dgram = new DatagramPacket(buffer, buffer.length);
                socket.receive(dgram);

                // Process packet in current thread
                handlePacket(dgram);

            } catch (IOException e) {
                if (running) {
                    System.err.println("Error receiving packet: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Handle an incoming datagram packet.
     */
    private void handlePacket(DatagramPacket dgram) {
        try {
            // Deserialize packet
            byte[] data = dgram.getData();
            Packet packet = PacketSerializer.deserialize(data);

            InetAddress address = dgram.getAddress();
            int port = dgram.getPort();

            // Process packet
            boolean shouldBroadcast = handler.processPacket(packet, address, port);

            // Broadcast to all other clients
            if (shouldBroadcast) {
                broadcast(packet, packet.getPlayerId());
            }

        } catch (IllegalArgumentException e) {
            System.err.println("Invalid packet received: " + e.getMessage());
        }
    }

    /**
     * Broadcast a packet to all clients except the excluded one.
     *
     * @param packet The packet to broadcast
     * @param excludePlayerId The player ID to exclude from broadcast (usually the sender)
     */
    private void broadcast(Packet packet, UUID excludePlayerId) {
        byte[] data = packet.serialize();
        List<Player> players = registry.getAll();

        broadcastExecutor.submit(() -> {
            for (Player player : players) {
                if (!player.getId().equals(excludePlayerId)) {
                    sendTo(data, player.getAddress(), player.getPort());
                }
            }
        });
    }

    /**
     * Send raw packet data to a specific address and port.
     */
    private void sendTo(byte[] data, InetAddress address, int port) {
        try {
            DatagramPacket dgram = new DatagramPacket(data, data.length, address, port);
            socket.send(dgram);
        } catch (IOException e) {
            System.err.printf("Error sending to %s:%d - %s%n",
                address.getHostAddress(), port, e.getMessage());
        }
    }

    /**
     * Cleanup task that removes timed-out clients.
     */
    private void cleanup() {
        List<UUID> timedOut = registry.getTimedOutClients();

        for (UUID playerId : timedOut) {
            PlayerLeavePacket leavePacket = handler.handleTimeout(playerId, sequenceNumber.getAndIncrement());
            if (leavePacket != null) {
                // Broadcast the leave packet to all remaining clients
                broadcast(leavePacket, playerId);
            }
        }

        // Print server stats
        if (!timedOut.isEmpty() || registry.size() > 0) {
            System.out.printf("Server stats: %d players connected%n", registry.size());
        }
    }

    /**
     * Stop the server gracefully.
     */
    public void stop() {
        System.out.println("Stopping server...");
        running = false;

        if (socket != null && !socket.isClosed()) {
            socket.close();
        }

        broadcastExecutor.shutdown();
        cleanupExecutor.shutdown();

        try {
            if (!broadcastExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                broadcastExecutor.shutdownNow();
            }
            if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            broadcastExecutor.shutdownNow();
            cleanupExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        System.out.println("Server stopped.");
    }

    /**
     * Get the number of connected players.
     */
    public int getPlayerCount() {
        return registry.size();
    }
}
