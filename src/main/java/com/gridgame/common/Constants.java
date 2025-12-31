package com.gridgame.common;

/**
 * Constants used throughout the game for grid dimensions, networking, and timing.
 */
public final class Constants {
    private Constants() {
        // Prevent instantiation
    }

    // Grid configuration
    public static final int GRID_SIZE = 1000;
    public static final int CELL_SIZE_PX = 20;
    public static final int VIEWPORT_CELLS = 50;
    public static final int VIEWPORT_SIZE_PX = VIEWPORT_CELLS * CELL_SIZE_PX; // 500px

    // Network configuration
    public static final int PACKET_SIZE = 64;
    public static final int SERVER_PORT = 25565;
    public static final int HEARTBEAT_INTERVAL_MS = 3000; // 3 seconds
    public static final int CLIENT_TIMEOUT_MS = 10000; // 10 seconds

    // Input configuration
    public static final int MOVE_RATE_LIMIT_MS = 100; // Max 10 moves per second
}
