package com.gridgame.common.model;

import java.net.InetAddress;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents a player in the game.
 * Contains player identification, position, appearance, and network information.
 */
public class Player {
    private final UUID id;
    private String name;
    private Position position;
    private int colorRGB; // Packed ARGB color
    private long lastUpdateTime;

    // Network information (server-side only)
    private InetAddress address;
    private int port;

    public Player(UUID id, String name, Position position, int colorRGB) {
        this.id = Objects.requireNonNull(id, "Player ID cannot be null");
        this.name = name;
        this.position = Objects.requireNonNull(position, "Position cannot be null");
        this.colorRGB = colorRGB;
        this.lastUpdateTime = System.currentTimeMillis();
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Position getPosition() {
        return position;
    }

    public void setPosition(Position position) {
        this.position = Objects.requireNonNull(position, "Position cannot be null");
        this.lastUpdateTime = System.currentTimeMillis();
    }

    public int getColorRGB() {
        return colorRGB;
    }

    public void setColorRGB(int colorRGB) {
        this.colorRGB = colorRGB;
    }

    public long getLastUpdateTime() {
        return lastUpdateTime;
    }

    public void updateHeartbeat() {
        this.lastUpdateTime = System.currentTimeMillis();
    }

    public InetAddress getAddress() {
        return address;
    }

    public void setAddress(InetAddress address) {
        this.address = address;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Player)) return false;
        Player other = (Player) obj;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return String.format("Player{id=%s, name='%s', position=%s}",
            id.toString().substring(0, 8), name, position);
    }

    /**
     * Generates a color from a UUID by hashing it to an HSB color.
     * Returns a packed ARGB integer.
     */
    public static int generateColorFromUUID(UUID id) {
        long hash = id.getLeastSignificantBits();
        float hue = (hash % 360) / 360.0f;
        float saturation = 0.7f;
        float brightness = 0.9f;

        // Convert HSB to RGB
        int rgb = java.awt.Color.HSBtoRGB(hue, saturation, brightness);
        // Ensure full opacity (alpha = 255)
        return 0xFF000000 | (rgb & 0x00FFFFFF);
    }
}
