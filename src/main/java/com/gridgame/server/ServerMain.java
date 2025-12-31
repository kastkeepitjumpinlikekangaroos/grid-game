package com.gridgame.server;

import com.gridgame.common.Constants;

import java.net.SocketException;

/**
 * Entry point for the game server application.
 */
public class ServerMain {

    public static void main(String[] args) {
        // Parse port from command line arguments
        int port = Constants.SERVER_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number: " + args[0]);
                System.err.println("Usage: ServerMain [port]");
                System.exit(1);
            }
        }

        // Create and start server
        GameServer server = new GameServer(port);

        // Add shutdown hook for graceful termination
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nShutdown signal received...");
            server.stop();
        }));

        try {
            server.start();
        } catch (SocketException e) {
            System.err.println("Failed to start server: " + e.getMessage());
            System.exit(1);
        }
    }
}
