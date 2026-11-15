package com.gridgame.client.render

import com.gridgame.client.TestClient
import com.gridgame.client.render.world.ItemPainter
import com.gridgame.common.model._
import org.junit.Assert._
import org.junit.Test

/**
 * What the item painter makes of the items on the client's ground from frame to frame (ItemPainter's
 * collect; render/CLAUDE.md, *An item is drawn as the characters are*): an item pops into being when
 * it appears and goes with a puff when it is taken, and walking away from one, or back to it, is
 * neither.
 */
class ItemPainterTest {
  /** A client in a match and a painter on a renderer's context, looking where the test says at the
    * time it says. Nothing is drawn: `collect` is the frame's look at the items. */
  private class Scene {
    val T = 1900000000000L
    val t = new TestClient(WorldData.createEmpty(120, 120))
    val c = t.client
    t.startMatch(spawn = (30, 30))
    val ctx = new RenderContext(c, FrameClock.System)
    val painter = new ItemPainter(ctx)
    ctx.canvasW = 800; ctx.canvasH = 450
    lookAt(30, 30)

    /** The camera on cell (x, y): it in the middle of the screen. */
    def lookAt(x: Int, y: Int): Unit = {
      ctx.camOffX = 400 - (x - y) * 20
      ctx.camOffY = 225 - (x + y) * 10
    }

    def put(id: Int, at: (Int, Int), kind: ItemType = ItemType.Gem): Unit =
      c.getItems.put(id, new Item(id, at._1, at._2, kind))

    /** A frame `ms` after the scene's time. */
    def frame(ms: Long): Unit = {
      ctx.frameTimeMs = T + ms
      painter.collect()
    }

    /** Frames 16ms apart from `from` to `to`. */
    def frames(from: Long, to: Long): Unit = {
      var ms = from
      while (ms <= to) { frame(ms); ms += 16 }
    }

    def sparks: Int = ctx.combatParticles.activeCount
  }

  @Test def anItemPopsIntoBeingAsItAppears(): Unit = {
    val sc = new Scene
    sc.put(1, (32, 30))
    sc.frame(0)
    assertEquals(1, sc.painter.marks)
    assertEquals("nothing of it yet in the frame it appears", 0f, sc.painter.markPop(0), 0f)
    sc.frame(ItemPainter.POP_MS / 2)
    assertTrue(s"most of the way there half way through, not ${sc.painter.markPop(0)}", sc.painter.markPop(0) > 0.6f)
    var most = 0f
    var ms = 0L
    while (ms <= ItemPainter.POP_MS) { sc.frame(ms); most = Math.max(most, sc.painter.markPop(0)); ms += 8 }
    assertTrue(s"a little past its size on the way ($most)", most > 1.02f && most < 1.3f)
    sc.frame(ItemPainter.POP_MS)
    assertEquals("and at its size from then on", 1f, sc.painter.markPop(0), 0f)
    sc.frame(60000)
    assertEquals(1f, sc.painter.markPop(0), 0f)
  }

  @Test def walkingAwayFromAnItemAndBackIsNeitherAPickupNorANewItem(): Unit = {
    val sc = new Scene
    sc.put(1, (32, 30))
    sc.frames(0, 1000)
    // off across the map: the item is still on the ground, only no longer on the screen
    sc.lookAt(90, 90)
    sc.frames(1016, 2000)
    assertEquals(0, sc.painter.marks)
    assertEquals("nobody took it", 0, sc.painter.going)
    assertEquals(0, sc.sparks)
    // and back
    sc.lookAt(30, 30)
    sc.frame(2016)
    assertEquals(1, sc.painter.marks)
    assertEquals("it has been there all along: it doesn't pop into being again", 1f, sc.painter.markPop(0), 0f)
    assertEquals(0, sc.painter.going)
  }

  @Test def anItemThatAppearedOffScreenIsWholeWhenWeGetToIt(): Unit = {
    val sc = new Scene
    sc.put(1, (90, 90))
    sc.frames(0, 3000)
    assertEquals(0, sc.painter.marks)
    sc.lookAt(90, 90)
    sc.frame(3016)
    assertEquals(1, sc.painter.marks)
    assertEquals(1f, sc.painter.markPop(0), 0f)
  }

  @Test def anItemTakenInFrontOfUsGoesWithAPuff(): Unit = {
    val sc = new Scene
    sc.put(1, (32, 30), ItemType.Heart)
    sc.put(2, (28, 31), ItemType.Star)
    sc.frames(0, 1000)
    assertEquals(2, sc.painter.marks)
    sc.c.getItems.remove(1)
    sc.frame(1016)
    assertEquals(1, sc.painter.marks)
    assertEquals("the one taken is shown going", 1, sc.painter.going)
    assertEquals("in a burst of sparks", ItemPainter.SPARKS, sc.sparks)
    sc.frame(1032)
    assertEquals("once", 1, sc.painter.going)
    assertEquals(ItemPainter.SPARKS, sc.sparks)
  }

  @Test def anItemTakenOffScreenGoesUnseen(): Unit = {
    val sc = new Scene
    sc.put(1, (90, 90))
    sc.frames(0, 1000)
    sc.c.getItems.remove(1)
    sc.frames(1016, 1100)
    assertEquals(0, sc.painter.going)
    assertEquals(0, sc.sparks)
  }

  @Test def whatWentWhileNothingWasDrawnIsNotSeenGoing(): Unit = {
    // the game over screen draws no world: an item taken under it is simply not there afterwards
    val sc = new Scene
    sc.put(1, (32, 30))
    sc.put(2, (28, 31))
    sc.frames(0, 1000)
    sc.c.getItems.remove(1)
    sc.frame(4000)
    assertEquals(1, sc.painter.marks)
    assertEquals(0, sc.painter.going)
    assertEquals(0, sc.sparks)
    // nor after a new life (GLGameRenderer.resetVisualPosition), however soon the next frame comes
    sc.frames(4016, 4100)
    sc.c.getItems.remove(2)
    sc.painter.reset()
    sc.frame(4116)
    assertEquals(0, sc.painter.going)
    assertEquals(0, sc.sparks)
  }

  @Test def anItemsSpawnSaidAgainIsTheSameItem(): Unit = {
    // a joiner's catch-up says SPAWN for an item we already have, and the client makes a new Item of it
    val sc = new Scene
    sc.put(1, (32, 30))
    sc.frames(0, 1000)
    sc.put(1, (32, 30))
    sc.frame(1016)
    assertEquals(1, sc.painter.marks)
    assertEquals("not a new item", 1f, sc.painter.markPop(0), 0f)
    assertEquals("and nobody took the old one", 0, sc.painter.going)
    assertEquals(0, sc.sparks)
  }

  @Test def everyItemOnScreenIsKeptForTheGroundAndNoOther(): Unit = {
    val sc = new Scene
    val kinds = ItemType.all
    kinds.zipWithIndex.foreach { case (k, i) => sc.put(10 + i, (28 + i * 2, 30), k) }
    sc.put(99, (100, 20))
    sc.frames(0, 1000)
    assertEquals(kinds.size, sc.painter.marks)
  }
}
