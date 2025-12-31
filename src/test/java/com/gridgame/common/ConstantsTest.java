package com.gridgame.common;

import org.junit.Test;
import static org.junit.Assert.*;

public class ConstantsTest {

    @Test
    public void testGridSizeIsPositive() {
        assertTrue(Constants.GRID_SIZE > 0);
    }

    @Test
    public void testCellSizeIsPositive() {
        assertTrue(Constants.CELL_SIZE_PX > 0);
    }

    @Test
    public void testViewportCellsIsPositive() {
        assertTrue(Constants.VIEWPORT_CELLS > 0);
    }

    @Test
    public void testViewportSizeCalculation() {
        assertEquals(Constants.VIEWPORT_CELLS * Constants.CELL_SIZE_PX,
                     Constants.VIEWPORT_SIZE_PX);
    }

    @Test
    public void testPacketSizeIsPositive() {
        assertTrue(Constants.PACKET_SIZE > 0);
    }

    @Test
    public void testServerPortIsValid() {
        assertTrue(Constants.SERVER_PORT > 0);
        assertTrue(Constants.SERVER_PORT < 65536);
    }

    @Test
    public void testHeartbeatIntervalIsPositive() {
        assertTrue(Constants.HEARTBEAT_INTERVAL_MS > 0);
    }

    @Test
    public void testClientTimeoutIsPositive() {
        assertTrue(Constants.CLIENT_TIMEOUT_MS > 0);
    }

    @Test
    public void testTimeoutGreaterThanHeartbeat() {
        assertTrue(Constants.CLIENT_TIMEOUT_MS > Constants.HEARTBEAT_INTERVAL_MS);
    }

    @Test
    public void testMoveRateLimitIsPositive() {
        assertTrue(Constants.MOVE_RATE_LIMIT_MS > 0);
    }
}
