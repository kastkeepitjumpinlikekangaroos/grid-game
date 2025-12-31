package com.gridgame.common.protocol;

import com.gridgame.common.Constants;
import com.gridgame.common.model.Position;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.UUID;

/**
 * Packet for updating a player's position and color.
 * Used when a player moves on the grid.
 */
public class PlayerUpdatePacket extends Packet {
    private final Position position;
    private final int colorRGB;

    public PlayerUpdatePacket(int sequenceNumber, UUID playerId, Position position, int colorRGB) {
        super(PacketType.PLAYER_UPDATE, sequenceNumber, playerId, getCurrentTimestamp());
        this.position = position;
        this.colorRGB = colorRGB;
    }

    public PlayerUpdatePacket(int sequenceNumber, UUID playerId, int timestamp, Position position, int colorRGB) {
        super(PacketType.PLAYER_UPDATE, sequenceNumber, playerId, timestamp);
        this.position = position;
        this.colorRGB = colorRGB;
    }

    public Position getPosition() {
        return position;
    }

    public int getColorRGB() {
        return colorRGB;
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

        // [21-24] X Position
        buffer.putInt(position.getX());

        // [25-28] Y Position
        buffer.putInt(position.getY());

        // [29-32] Color RGB (4 bytes for ARGB)
        buffer.putInt(colorRGB);

        // [33-36] Timestamp
        buffer.putInt(timestamp);

        // [37-63] Payload/Reserved (27 bytes) - fill with zeros
        buffer.put(new byte[27]);

        return buffer.array();
    }

    @Override
    public String toString() {
        return String.format("PlayerUpdatePacket{seq=%d, playerId=%s, position=%s, color=0x%08X}",
            sequenceNumber, playerId.toString().substring(0, 8), position, colorRGB);
    }
}
