package com.gridgame.client;

import com.gridgame.common.Constants;
import com.gridgame.common.model.Player;
import com.gridgame.common.model.Position;
import com.gridgame.common.protocol.*;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Main client orchestrator that manages local state and network communication.
 */
public class GameClient {
    private final String serverHost;
    private final int serverPort;
    private NetworkThread networkThread;

    private final UUID localPlayerId;
    private final int localColorRGB;
    private final AtomicReference<Position> localPosition;
    private final ConcurrentHashMap<UUID, Player> players;
    private final ConcurrentHashMap<UUID, Integer> lastSequence;
    private final BlockingQueue<Packet> incomingPackets;
    private final AtomicInteger sequenceNumber;

    private volatile boolean running;

    public GameClient(String serverHost, int serverPort) {
        this.serverHost = serverHost;
        this.serverPort = serverPort;
        this.localPlayerId = UUID.randomUUID();
        this.localColorRGB = Player.generateColorFromUUID(localPlayerId);
        this.localPosition = new AtomicReference<>(new Position(
            Constants.GRID_SIZE / 2, Constants.GRID_SIZE / 2)); // Start in center
        this.players = new ConcurrentHashMap<>();
        this.lastSequence = new ConcurrentHashMap<>();
        this.incomingPackets = new LinkedBlockingQueue<>();
        this.sequenceNumber = new AtomicInteger(0);
        this.running = false;
    }

    /**
     * Connect to the server and start network communication.
     */
    public void connect() {
        try {
            InetAddress serverAddress = InetAddress.getByName(serverHost);
            networkThread = new NetworkThread(this, serverAddress, serverPort);
            running = true;

            // Start network thread
            networkThread.start();

            // Send JOIN packet
            sendJoinPacket();

            // Start packet processor thread
            startPacketProcessor();

            System.out.printf("GameClient: Connected to %s:%d as player %s%n",
                serverHost, serverPort, localPlayerId.toString().substring(0, 8));

        } catch (UnknownHostException e) {
            System.err.println("GameClient: Unknown host - " + e.getMessage());
        }
    }

    /**
     * Send a JOIN packet to the server.
     */
    private void sendJoinPacket() {
        Position pos = localPosition.get();
        PlayerJoinPacket packet = new PlayerJoinPacket(
            sequenceNumber.getAndIncrement(),
            localPlayerId,
            pos,
            localColorRGB,
            "Player"
        );
        networkThread.send(packet);
    }

    /**
     * Send a heartbeat packet to the server.
     */
    public void sendHeartbeat() {
        sendPositionUpdate(localPosition.get());
    }

    /**
     * Move the local player by a delta.
     */
    public void movePlayer(int dx, int dy) {
        Position current = localPosition.get();
        int newX = Math.max(0, Math.min(Constants.GRID_SIZE - 1, current.getX() + dx));
        int newY = Math.max(0, Math.min(Constants.GRID_SIZE - 1, current.getY() + dy));

        Position newPos = new Position(newX, newY);

        if (!newPos.equals(current)) {
            localPosition.set(newPos);
            sendPositionUpdate(newPos);
        }
    }

    /**
     * Send a position update to the server.
     */
    private void sendPositionUpdate(Position position) {
        PlayerUpdatePacket packet = new PlayerUpdatePacket(
            sequenceNumber.getAndIncrement(),
            localPlayerId,
            position,
            localColorRGB
        );
        networkThread.send(packet);
    }

    /**
     * Enqueue a packet received from the network for processing.
     */
    public void enqueuePacket(Packet packet) {
        incomingPackets.offer(packet);
    }

    /**
     * Start the packet processor thread that processes incoming packets.
     */
    private void startPacketProcessor() {
        Thread processor = new Thread(() -> {
            while (running) {
                try {
                    Packet packet = incomingPackets.take();
                    processPacket(packet);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }, "PacketProcessor");
        processor.setDaemon(true);
        processor.start();
    }

    /**
     * Process an incoming packet.
     */
    private void processPacket(Packet packet) {
        UUID playerId = packet.getPlayerId();

        // Ignore packets from ourselves
        if (playerId.equals(localPlayerId)) {
            return;
        }

        // Check sequence number (ignore out-of-order packets)
        int lastSeq = lastSequence.getOrDefault(playerId, -1);
        if (packet.getSequenceNumber() <= lastSeq) {
            return; // Old packet, ignore
        }
        lastSequence.put(playerId, packet.getSequenceNumber());

        // Process based on type
        switch (packet.getType()) {
            case PLAYER_JOIN:
                handlePlayerJoin((PlayerJoinPacket) packet);
                break;

            case PLAYER_UPDATE:
                handlePlayerUpdate((PlayerUpdatePacket) packet);
                break;

            case PLAYER_LEAVE:
                handlePlayerLeave((PlayerLeavePacket) packet);
                break;

            default:
                // Ignore other packet types
                break;
        }
    }

    private void handlePlayerJoin(PlayerJoinPacket packet) {
        Player player = new Player(
            packet.getPlayerId(),
            packet.getPlayerName(),
            packet.getPosition(),
            packet.getColorRGB()
        );
        players.put(player.getId(), player);

        System.out.printf("GameClient: Player joined - %s ('%s') at %s%n",
            player.getId().toString().substring(0, 8),
            player.getName(),
            player.getPosition());
    }

    private void handlePlayerUpdate(PlayerUpdatePacket packet) {
        UUID playerId = packet.getPlayerId();
        Player player = players.get(playerId);

        if (player != null) {
            player.setPosition(packet.getPosition());
            player.setColorRGB(packet.getColorRGB());
        } else {
            // Player not known, create them
            player = new Player(playerId, "Player", packet.getPosition(), packet.getColorRGB());
            players.put(playerId, player);
        }
    }

    private void handlePlayerLeave(PlayerLeavePacket packet) {
        UUID playerId = packet.getPlayerId();
        Player player = players.remove(playerId);

        if (player != null) {
            System.out.printf("GameClient: Player left - %s ('%s')%n",
                playerId.toString().substring(0, 8),
                player.getName());
        }
    }

    /**
     * Get the local player's position.
     */
    public Position getLocalPosition() {
        return localPosition.get();
    }

    /**
     * Get the local player's ID.
     */
    public UUID getLocalPlayerId() {
        return localPlayerId;
    }

    /**
     * Get all players (including remote players, but not including local player in the map).
     */
    public ConcurrentHashMap<UUID, Player> getPlayers() {
        return players;
    }

    /**
     * Get the local player's color.
     */
    public int getLocalColorRGB() {
        return localColorRGB;
    }

    /**
     * Disconnect from the server.
     */
    public void disconnect() {
        if (!running) {
            return;
        }

        running = false;

        // Send LEAVE packet
        PlayerLeavePacket leavePacket = new PlayerLeavePacket(
            sequenceNumber.getAndIncrement(),
            localPlayerId
        );
        networkThread.send(leavePacket);

        // Shutdown network thread
        networkThread.shutdown();

        System.out.println("GameClient: Disconnected");
    }
}
