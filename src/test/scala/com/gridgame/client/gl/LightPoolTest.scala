package com.gridgame.client.gl

import org.junit.Assert._
import org.junit.Test

/**
 * The frame's lights (LightPool). A busy fight asks for more than the pool holds, and what it keeps
 * then decides whether an explosion flashes and whether lights blink as the fight moves.
 */
class LightPoolTest {
  private def radii(p: LightPool): Seq[Float] = (0 until p.count).map(p.radius(_)).sorted

  @Test def underCapacityEveryLightIsKept(): Unit = {
    val p = new LightPool(4)
    Seq(10f, 20f, 30f).foreach(r => p.add(0f, 0f, r, 1f, 1f, 1f, 0.2f))
    assertEquals(Seq(10f, 20f, 30f), radii(p))
  }

  @Test def whenFullTheStrongestAreKeptWhateverOrderTheyCameIn(): Unit = {
    for (order <- Seq((1 to 12).map(_ * 10f), (1 to 12).reverse.map(_ * 10f), Seq(50f, 120f, 10f, 90f, 30f, 110f,
      70f, 20f, 100f, 40f, 80f, 60f))) {
      val p = new LightPool(5)
      order.foreach(r => p.add(0f, 0f, r, 1f, 1f, 1f, 0.2f))
      assertEquals(s"from $order", Seq(80f, 90f, 100f, 110f, 120f), radii(p))
    }
  }

  @Test def anExplosionAddedLastStillFlashes(): Unit = {
    // It used to be dropped: the pool took lights first come, and explosions are added after
    // every player and projectile
    val p = new LightPool(96)
    (0 until 150).foreach(i => p.add(i.toFloat, 0f, 55f, 1f, 1f, 1f, 0.15f))
    p.add(500f, 500f, 180f, 1f, 0.6f, 0.2f, 0.6f)
    assertEquals(96, p.count)
    assertTrue((0 until p.count).exists(i => p.radius(i) == 180f && p.x(i) == 500f))
  }

  @Test def aLightThatWouldNotShowTakesNoSlot(): Unit = {
    val p = new LightPool(2)
    p.add(0f, 0f, 50f, 1f, 1f, 1f, 0f)
    p.add(0f, 0f, 0f, 1f, 1f, 1f, 0.5f)
    assertEquals(0, p.count)
  }

  @Test def clearingEmptiesItForTheNextFrame(): Unit = {
    val p = new LightPool(2)
    Seq(10f, 20f, 30f).foreach(r => p.add(0f, 0f, r, 1f, 1f, 1f, 0.2f))
    p.clear()
    p.add(0f, 0f, 5f, 1f, 1f, 1f, 0.2f)
    assertEquals(Seq(5f), radii(p))
  }
}
