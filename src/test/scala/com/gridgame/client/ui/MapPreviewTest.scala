package com.gridgame.client.ui

import com.gridgame.client.Fx
import org.junit.Assert._
import org.junit.Test

import java.util.concurrent.{CountDownLatch, TimeUnit}
import scala.collection.mutable

/**
 * The maps' pictures for the menus (MapPreview): drawn on a thread of their own, the soonest wanted
 * first, and not at all once nobody wants them. Each test asks for its own sizes of picture, so what
 * another drew isn't there already.
 */
class MapPreviewTest {
  /** Keep the painter's thread busy until the latch returned is let go: what is asked meanwhile
    * waits its turn in the queue. */
  private def holdThePainter(): CountDownLatch = {
    Fx(())
    val started = new CountDownLatch(1)
    val release = new CountDownLatch(1)
    MapPreview.paint(() => { started.countDown(); release.await(30, TimeUnit.SECONDS) })
    assertTrue("the painter took up the hold", started.await(30, TimeUnit.SECONDS))
    release
  }

  /** Every picture asked for drawn, and handed over on the FX thread. */
  private def settled(): Unit = { MapPreview.settle(); Fx(()) }

  @Test def aPictureGivenUpOnBeforeItIsDrawnIsNeverDrawn(): Unit = {
    val release = holdThePainter()
    val got = mutable.Buffer[Int]()
    Fx {
      val first = MapPreview.picture(1, 31, 17)(_ => got += 1)
      MapPreview.picture(2, 31, 17)(_ => got += 2)
      first.cancel()
    }
    release.countDown()
    settled()
    assertEquals(Seq(2), Fx(got.toSeq))
    assertNull("not drawn at all", Fx(MapPreview.drawn(1, 31, 17)))
    assertNotNull(Fx(MapPreview.drawn(2, 31, 17)))
  }

  @Test def aPictureOneOfTwoGivesUpOnIsStillDrawnForTheOther(): Unit = {
    val release = holdThePainter()
    val got = mutable.Buffer[String]()
    Fx {
      val one = MapPreview.picture(3, 33, 19)(_ => got += "one")
      MapPreview.picture(3, 33, 19)(_ => got += "other")
      one.cancel()
    }
    release.countDown()
    settled()
    assertEquals(Seq("other"), Fx(got.toSeq))
  }

  @Test def givenUpOnByWhoeverWantedItSoonestItWaitsItsTurn(): Unit = {
    // Map 4 wanted at once by one and ahead by another; 5 and 6 ahead. The one who wanted 4 at once
    // gives up: 4 is drawn when the maps ahead are, after 5 and 6, which were asked before it was
    val release = holdThePainter()
    val order = mutable.Buffer[Int]()
    Fx {
      val now = MapPreview.picture(4, 35, 21)(_ => order += 4)
      MapPreview.picture(4, 35, 21, MapPreview.Ahead)(_ => order += 4)
      MapPreview.picture(5, 35, 21, MapPreview.Ahead)(_ => order += 5)
      MapPreview.picture(6, 35, 21, MapPreview.Ahead)(_ => order += 6)
      now.cancel()
    }
    release.countDown()
    settled()
    assertEquals(Seq(5, 6, 4), Fx(order.toSeq))
  }

  @Test def aPictureAlreadyDrawnIsHandedOverAtOnceAndGivingUpOnItChangesNothing(): Unit = {
    val got = mutable.Buffer[Int]()
    Fx(MapPreview.picture(7, 37, 23)(_ => got += 1))
    settled()
    Fx {
      val again = MapPreview.picture(7, 37, 23)(_ => got += 2)
      assertEquals("there at once", Seq(1, 2), got.toSeq)
      again.cancel()
    }
    assertNotNull(Fx(MapPreview.drawn(7, 37, 23)))
  }
}
