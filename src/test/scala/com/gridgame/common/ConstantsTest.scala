package com.gridgame.common

import org.junit.Test
import org.junit.Assert._

class ConstantsTest {

  @Test
  def testGridSizeIsPositive(): Unit = {
    assertTrue(Constants.GRID_SIZE > 0)
  }

  @Test
  def testCellSizeIsPositive(): Unit = {
    assertTrue(Constants.CELL_SIZE_PX > 0)
  }

  @Test
  def testViewportCellsIsPositive(): Unit = {
    assertTrue(Constants.VIEWPORT_CELLS > 0)
  }

  @Test
  def testViewportSizeCalculation(): Unit = {
    assertEquals(Constants.VIEWPORT_CELLS * Constants.CELL_SIZE_PX, Constants.VIEWPORT_SIZE_PX)
  }

  @Test
  def testPacketSizeIsPositive(): Unit = {
    assertTrue(Constants.PACKET_SIZE > 0)
  }

  @Test
  def testServerPortIsValid(): Unit = {
    assertTrue(Constants.SERVER_PORT > 0)
    assertTrue(Constants.SERVER_PORT < 65536)
  }

  @Test
  def testHeartbeatIntervalIsPositive(): Unit = {
    assertTrue(Constants.HEARTBEAT_INTERVAL_MS > 0)
  }

  @Test
  def testClientTimeoutIsPositive(): Unit = {
    assertTrue(Constants.CLIENT_TIMEOUT_MS > 0)
  }

  @Test
  def testTimeoutGreaterThanHeartbeat(): Unit = {
    assertTrue(Constants.CLIENT_TIMEOUT_MS > Constants.HEARTBEAT_INTERVAL_MS)
  }

  @Test
  def testMoveRateLimitIsPositive(): Unit = {
    assertTrue(Constants.MOVE_RATE_LIMIT_MS > 0)
  }
}
