package com.gridgame.common.protocol;

/**
 * Types of packets in the game protocol.
 * Each type has a unique byte identifier used in the fixed-length packet format.
 */
public enum PacketType {
    PLAYER_JOIN((byte) 0x01),
    PLAYER_UPDATE((byte) 0x02),
    PLAYER_LEAVE((byte) 0x03),
    HEARTBEAT((byte) 0x05);

    private final byte id;

    PacketType(byte id) {
        this.id = id;
    }

    public byte getId() {
        return id;
    }

    /**
     * Get PacketType from byte ID.
     */
    public static PacketType fromId(byte id) {
        for (PacketType type : values()) {
            if (type.id == id) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown packet type ID: " + id);
    }
}
