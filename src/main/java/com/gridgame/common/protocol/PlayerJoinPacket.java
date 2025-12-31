package com.gridgame.common.protocol;

import com.gridgame.common.Constants;
import com.gridgame.common.model.Position;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Packet sent when a player joins the game.
 * Includes player name in the payload section (max 27 bytes).
 */
public class PlayerJoinPacket extends Packet {
    private final Position position;
    private final int colorRGB;
    private final String playerName;

    public PlayerJoinPacket(int sequenceNumber, UUID playerId, Position position, int colorRGB, String playerName) {
        super(PacketType.PLAYER_JOIN, sequenceNumber, playerId, getCurrentTimestamp());
        this.position = position;
        this.colorRGB = colorRGB;
        this.playerName = playerName != null ? playerName : "Player";
    }

    public PlayerJoinPacket(int sequenceNumber, UUID playerId, int timestamp, Position position, int colorRGB, String playerName) {
        super(PacketType.PLAYER_JOIN, sequenceNumber, playerId, timestamp);
        this.position = position;
        this.colorRGB = colorRGB;
        this.playerName = playerName != null ? playerName : "Player";
    }

    public Position getPosition() {
        return position;
    }

    public int getColorRGB() {
        return colorRGB;
    }

    public String getPlayerName() {
        return playerName;
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

        // [29-32] Color RGB
        buffer.putInt(colorRGB);

        // [33-36] Timestamp
        buffer.putInt(timestamp);

        // [37-63] Player name (max 27 bytes)
        byte[] nameBytes = playerName.getBytes(StandardCharsets.UTF_8);
        int nameLength = Math.min(nameBytes.length, 27);
        buffer.put(nameBytes, 0, nameLength);
        // Fill remaining bytes with zeros
        buffer.put(new byte[27 - nameLength]);

        return buffer.array();
    }

    @Override
    public String toString() {
        return String.format("PlayerJoinPacket{seq=%d, playerId=%s, name='%s', position=%s, color=0x%08X}",
            sequenceNumber, playerId.toString().substring(0, 8), playerName, position, colorRGB);
    }
}
