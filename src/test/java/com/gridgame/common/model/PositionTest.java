package com.gridgame.common.model;

import org.junit.Test;
import static org.junit.Assert.*;

public class PositionTest {

    @Test
    public void testValidPosition() {
        Position pos = new Position(5, 10);
        assertEquals(5, pos.getX());
        assertEquals(10, pos.getY());
    }

    @Test
    public void testPositionAtZero() {
        Position pos = new Position(0, 0);
        assertEquals(0, pos.getX());
        assertEquals(0, pos.getY());
    }

    @Test
    public void testPositionAtMaxBounds() {
        Position pos = new Position(999, 999);
        assertEquals(999, pos.getX());
        assertEquals(999, pos.getY());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNegativeXThrowsException() {
        new Position(-1, 5);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNegativeYThrowsException() {
        new Position(5, -1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testXOutOfBoundsThrowsException() {
        new Position(1000, 5);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testYOutOfBoundsThrowsException() {
        new Position(5, 1000);
    }

    @Test
    public void testEqualsWithSamePosition() {
        Position pos1 = new Position(10, 20);
        Position pos2 = new Position(10, 20);
        assertEquals(pos1, pos2);
    }

    @Test
    public void testEqualsWithDifferentPosition() {
        Position pos1 = new Position(10, 20);
        Position pos2 = new Position(10, 21);
        assertNotEquals(pos1, pos2);
    }

    @Test
    public void testEqualsWithNull() {
        Position pos = new Position(10, 20);
        assertNotEquals(pos, null);
    }

    @Test
    public void testEqualsWithDifferentClass() {
        Position pos = new Position(10, 20);
        assertNotEquals(pos, "not a position");
    }

    @Test
    public void testHashCodeConsistency() {
        Position pos1 = new Position(10, 20);
        Position pos2 = new Position(10, 20);
        assertEquals(pos1.hashCode(), pos2.hashCode());
    }

    @Test
    public void testToString() {
        Position pos = new Position(10, 20);
        String result = pos.toString();
        assertTrue(result.contains("10"));
        assertTrue(result.contains("20"));
    }
}
