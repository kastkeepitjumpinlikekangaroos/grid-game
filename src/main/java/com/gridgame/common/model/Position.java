package com.gridgame.common.model;

import com.gridgame.common.Constants;
import java.util.Objects;

/**
 * Immutable position on the game grid.
 * Coordinates are validated to be within bounds [0, GRID_SIZE).
 */
public final class Position {
    private final int x;
    private final int y;

    public Position(int x, int y) {
        if (x < 0 || x >= Constants.GRID_SIZE) {
            throw new IllegalArgumentException(
                String.format("X coordinate %d out of bounds [0, %d)", x, Constants.GRID_SIZE));
        }
        if (y < 0 || y >= Constants.GRID_SIZE) {
            throw new IllegalArgumentException(
                String.format("Y coordinate %d out of bounds [0, %d)", y, Constants.GRID_SIZE));
        }
        this.x = x;
        this.y = y;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Position)) return false;
        Position other = (Position) obj;
        return x == other.x && y == other.y;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }

    @Override
    public String toString() {
        return String.format("Position(%d, %d)", x, y);
    }
}
