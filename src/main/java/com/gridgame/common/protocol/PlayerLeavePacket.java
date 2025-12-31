package com.gridgame.common.protocol;

import com.gridgame.common.Constants;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.UUID;

/**
 * Packet sent when a player leaves the game.
 * Minimal packet with only the player ID.
 */
public class PlayerLeavePacket extends Packet {

    public PlayerLeavePacket(int sequenceNumber, UUID playerId) {
        super(PacketType.PLAYER_LEAVE, sequenceNumber, playerId, getCurrentTimestamp());
    }

    public PlayerLeavePacket(int sequenceNumber, UUID playerId, int timestamp) {
        super(PacketType.PLAYER_LEAVE, sequenceNumber, playerId, timestamp);
    }

    @Override
    public byte[] serialize() {
        ByteBuffer buffer = ByteBuffer.allocate(Constants.PACKET_SIZE);
        buffer.order(ByteOrder.BIG_ENDIAN);

        // [0] Packet Type
        buffer.put(type.getId());

        // [1-4] Sequence Number
        buffer.putInt(sequenceNumber);

        // [5-20] Player ID (UUID = 2 longs)
        buffer.putLong(playerId.getMostSignificantBits());
        buffer.putLong(playerId.getLeastSignificantBits());

        // [21-36] Unused (position, color, timestamp) - fill with zeros
        buffer.putInt(0); // X
        buffer.putInt(0); // Y
        buffer.putInt(0); // Color
        buffer.putInt(timestamp); // Timestamp

        // [37-63] Payload/Reserved (27 bytes) - fill with zeros
        buffer.put(new byte[27]);

        return buffer.array();
    }

    @Override
    public String toString() {
        return String.format("PlayerLeavePacket{seq=%d, playerId=%s}",
            sequenceNumber, playerId.toString().substring(0, 8));
    }
}
