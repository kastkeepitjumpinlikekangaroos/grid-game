package com.gridgame.common.model

import org.junit.Test
import org.junit.Assert._

class PositionTest {

  @Test
  def testValidPosition(): Unit = {
    val pos = new Position(5, 10)
    assertEquals(5, pos.getX)
    assertEquals(10, pos.getY)
  }

  @Test
  def testPositionAtZero(): Unit = {
    val pos = new Position(0, 0)
    assertEquals(0, pos.getX)
    assertEquals(0, pos.getY)
  }

  @Test
  def testPositionAtMaxBounds(): Unit = {
    val pos = new Position(999, 999)
    assertEquals(999, pos.getX)
    assertEquals(999, pos.getY)
  }

  @Test(expected = classOf[IllegalArgumentException])
  def testNegativeXThrowsException(): Unit = {
    new Position(-1, 5)
  }

  @Test(expected = classOf[IllegalArgumentException])
  def testNegativeYThrowsException(): Unit = {
    new Position(5, -1)
  }

  @Test(expected = classOf[IllegalArgumentException])
  def testXOutOfBoundsThrowsException(): Unit = {
    new Position(1000, 5)
  }

  @Test(expected = classOf[IllegalArgumentException])
  def testYOutOfBoundsThrowsException(): Unit = {
    new Position(5, 1000)
  }

  @Test
  def testEqualsWithSamePosition(): Unit = {
    val pos1 = new Position(10, 20)
    val pos2 = new Position(10, 20)
    assertEquals(pos1, pos2)
  }

  @Test
  def testEqualsWithDifferentPosition(): Unit = {
    val pos1 = new Position(10, 20)
    val pos2 = new Position(10, 21)
    assertNotEquals(pos1, pos2)
  }

  @Test
  def testEqualsWithNull(): Unit = {
    val pos = new Position(10, 20)
    assertNotEquals(pos, null)
  }

  @Test
  def testEqualsWithDifferentClass(): Unit = {
    val pos = new Position(10, 20)
    assertNotEquals(pos, "not a position")
  }

  @Test
  def testHashCodeConsistency(): Unit = {
    val pos1 = new Position(10, 20)
    val pos2 = new Position(10, 20)
    assertEquals(pos1.hashCode(), pos2.hashCode())
  }

  @Test
  def testToString(): Unit = {
    val pos = new Position(10, 20)
    val result = pos.toString
    assertTrue(result.contains("10"))
    assertTrue(result.contains("20"))
  }
}
