package com.gridgame.common.protocol;

import java.util.Objects;
import java.util.UUID;

/**
 * Abstract base class for all network packets.
 * All packets have a fixed size of 64 bytes and contain common fields.
 */
public abstract class Packet {
    protected final PacketType type;
    protected final int sequenceNumber;
    protected final UUID playerId;
    protected final int timestamp;

    protected Packet(PacketType type, int sequenceNumber, UUID playerId, int timestamp) {
        this.type = Objects.requireNonNull(type, "Packet type cannot be null");
        this.sequenceNumber = sequenceNumber;
        this.playerId = Objects.requireNonNull(playerId, "Player ID cannot be null");
        this.timestamp = timestamp;
    }

    public PacketType getType() {
        return type;
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public int getTimestamp() {
        return timestamp;
    }

    /**
     * Serialize this packet to a 64-byte array.
     */
    public abstract byte[] serialize();

    /**
     * Get the current Unix timestamp as an int (seconds since epoch mod 2^32).
     */
    protected static int getCurrentTimestamp() {
        return (int) (System.currentTimeMillis() / 1000);
    }
}
