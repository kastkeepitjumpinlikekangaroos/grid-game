package com.gridgame.client;

import com.gridgame.common.Constants;
import com.gridgame.common.protocol.Packet;
import com.gridgame.common.protocol.PacketSerializer;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Background thread for handling UDP network communication.
 * Sends packets to the server and receives broadcasts from the server.
 */
public class NetworkThread extends Thread {
    private final GameClient client;
    private final InetAddress serverAddress;
    private final int serverPort;
    private DatagramSocket socket;
    private final ScheduledExecutorService heartbeatExecutor;
    private volatile boolean running;

    public NetworkThread(GameClient client, InetAddress serverAddress, int serverPort) {
        super("NetworkThread");
        this.client = client;
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
        this.heartbeatExecutor = Executors.newSingleThreadScheduledExecutor();
        this.running = false;
        setDaemon(true);
    }

    @Override
    public void run() {
        try {
            socket = new DatagramSocket();
            running = true;

            System.out.printf("NetworkThread: Connected to server %s:%d%n",
                serverAddress.getHostAddress(), serverPort);

            // Schedule heartbeat task (every 3 seconds)
            heartbeatExecutor.scheduleAtFixedRate(
                this::sendHeartbeat,
                Constants.HEARTBEAT_INTERVAL_MS,
                Constants.HEARTBEAT_INTERVAL_MS,
                TimeUnit.MILLISECONDS
            );

            // Start receive loop
            receiveLoop();

        } catch (SocketException e) {
            System.err.println("NetworkThread: Failed to create socket - " + e.getMessage());
        } finally {
            shutdown();
        }
    }

    /**
     * Main receive loop.
     */
    private void receiveLoop() {
        byte[] buffer = new byte[Constants.PACKET_SIZE];

        while (running && !isInterrupted()) {
            try {
                DatagramPacket dgram = new DatagramPacket(buffer, buffer.length);
                socket.receive(dgram);

                // Deserialize packet
                Packet packet = PacketSerializer.deserialize(dgram.getData());

                // Enqueue for processing by the client
                client.enqueuePacket(packet);

            } catch (IOException e) {
                if (running) {
                    System.err.println("NetworkThread: Error receiving packet - " + e.getMessage());
                }
            } catch (IllegalArgumentException e) {
                System.err.println("NetworkThread: Invalid packet received - " + e.getMessage());
            }
        }
    }

    /**
     * Send a packet to the server.
     */
    public void send(Packet packet) {
        if (socket == null || socket.isClosed()) {
            System.err.println("NetworkThread: Cannot send packet, socket is closed");
            return;
        }

        try {
            byte[] data = packet.serialize();
            DatagramPacket dgram = new DatagramPacket(data, data.length, serverAddress, serverPort);
            socket.send(dgram);
        } catch (IOException e) {
            System.err.println("NetworkThread: Error sending packet - " + e.getMessage());
        }
    }

    /**
     * Send a heartbeat packet to keep the connection alive.
     */
    private void sendHeartbeat() {
        client.sendHeartbeat();
    }

    /**
     * Shutdown the network thread.
     */
    public void shutdown() {
        running = false;

        heartbeatExecutor.shutdown();
        try {
            if (!heartbeatExecutor.awaitTermination(1, TimeUnit.SECONDS)) {
                heartbeatExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            heartbeatExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        if (socket != null && !socket.isClosed()) {
            socket.close();
        }

        System.out.println("NetworkThread: Shutdown complete");
    }
}
