package com.gridgame.common.protocol;

import com.gridgame.common.Constants;
import com.gridgame.common.model.Position;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Handles serialization and deserialization of network packets.
 * All packets are fixed 64-byte arrays with BIG_ENDIAN byte order.
 */
public class PacketSerializer {

    private PacketSerializer() {
        // Utility class
    }

    /**
     * Deserialize a 64-byte array into a Packet object.
     *
     * @param data The 64-byte packet data
     * @return The deserialized packet
     * @throws IllegalArgumentException if data is invalid
     */
    public static Packet deserialize(byte[] data) {
        if (data == null || data.length != Constants.PACKET_SIZE) {
            throw new IllegalArgumentException(
                String.format("Invalid packet size: expected %d bytes, got %d",
                    Constants.PACKET_SIZE, data != null ? data.length : 0));
        }

        ByteBuffer buffer = ByteBuffer.wrap(data);
        buffer.order(ByteOrder.BIG_ENDIAN);

        // [0] Packet Type
        byte typeId = buffer.get();
        PacketType type = PacketType.fromId(typeId);

        // [1-4] Sequence Number
        int sequenceNumber = buffer.getInt();

        // [5-20] Player ID (UUID)
        long mostSigBits = buffer.getLong();
        long leastSigBits = buffer.getLong();
        UUID playerId = new UUID(mostSigBits, leastSigBits);

        // [21-24] X Position
        int x = buffer.getInt();

        // [25-28] Y Position
        int y = buffer.getInt();

        // [29-32] Color RGB
        int colorRGB = buffer.getInt();

        // [33-36] Timestamp
        int timestamp = buffer.getInt();

        // [37-63] Payload (27 bytes)
        byte[] payload = new byte[27];
        buffer.get(payload);

        // Deserialize based on packet type
        switch (type) {
            case PLAYER_JOIN:
                // Extract player name from payload
                String playerName = extractString(payload);
                try {
                    Position joinPosition = new Position(x, y);
                    return new PlayerJoinPacket(sequenceNumber, playerId, timestamp,
                        joinPosition, colorRGB, playerName);
                } catch (IllegalArgumentException e) {
                    // Invalid position, use default
                    Position defaultPosition = new Position(0, 0);
                    return new PlayerJoinPacket(sequenceNumber, playerId, timestamp,
                        defaultPosition, colorRGB, playerName);
                }

            case PLAYER_UPDATE:
                try {
                    Position updatePosition = new Position(x, y);
                    return new PlayerUpdatePacket(sequenceNumber, playerId, timestamp,
                        updatePosition, colorRGB);
                } catch (IllegalArgumentException e) {
                    // Invalid position, use default
                    Position defaultPosition = new Position(0, 0);
                    return new PlayerUpdatePacket(sequenceNumber, playerId, timestamp,
                        defaultPosition, colorRGB);
                }

            case PLAYER_LEAVE:
                return new PlayerLeavePacket(sequenceNumber, playerId, timestamp);

            case HEARTBEAT:
                // For heartbeat, we can reuse PlayerLeavePacket structure
                // or create a dedicated HeartbeatPacket if needed
                return new PlayerLeavePacket(sequenceNumber, playerId, timestamp);

            default:
                throw new IllegalArgumentException("Unknown packet type: " + type);
        }
    }

    /**
     * Extract a null-terminated or zero-padded string from a byte array.
     */
    private static String extractString(byte[] bytes) {
        // Find the end of the string (first zero byte)
        int length = 0;
        for (int i = 0; i < bytes.length; i++) {
            if (bytes[i] == 0) {
                length = i;
                break;
            }
            length = i + 1;
        }

        if (length == 0) {
            return "";
        }

        return new String(bytes, 0, length, StandardCharsets.UTF_8);
    }

    /**
     * Validate that a packet's serialized form is exactly 64 bytes.
     */
    public static boolean validate(byte[] data) {
        return data != null && data.length == Constants.PACKET_SIZE;
    }
}
