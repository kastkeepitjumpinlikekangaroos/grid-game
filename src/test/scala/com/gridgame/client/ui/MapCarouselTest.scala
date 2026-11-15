package com.gridgame.client.ui

import com.gridgame.client.Fx
import com.gridgame.common.WorldRegistry
import javafx.event.Event
import javafx.scene.{Node, Scene}
import javafx.scene.control.Button
import javafx.scene.input.{KeyCode, KeyEvent, MouseButton, MouseEvent}
import javafx.scene.layout.StackPane
import org.junit.Assert._
import org.junit.Test

import scala.collection.mutable

/** The maps as a carousel (MapCarousel), clicked and keyed as a player would, on no window. */
class MapCarouselTest {
  private val count = WorldRegistry.size
  private val picks = mutable.Buffer[Int]()
  private val chosen = mutable.Buffer[Int]()

  private def carousel(initial: Int = 0): MapCarousel = Fx {
    val c = new MapCarousel(initial)
    c.onPick = picks += _
    c.onChoose = chosen += _
    c
  }

  private def classed(c: MapCarousel, styleClass: String): Seq[Node] = Fx(Fx.all(c.node).filter(_.getStyleClass.contains(styleClass)))
  private def arrow(c: MapCarousel, tip: String): Button =
    Fx(Fx.all(c.node).collectFirst { case b: Button if b.getTooltip != null && b.getTooltip.getText == tip => b }.get)
  private def shown(c: MapCarousel, styleClass: String): String = Fx(Fx.labelsOf(c.node, styleClass).head)
  private def strip(c: MapCarousel): Seq[Node] = classed(c, "carousel-item")
  private def ringed(c: MapCarousel): Seq[Int] = Fx(strip(c).zipWithIndex.collect {
    case (n, i) if n.getStyleClass.contains("carousel-item-picked") => i
  })

  private def click(n: Node, times: Int = 1): Unit = Fx {
    Event.fireEvent(n, new MouseEvent(MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0, MouseButton.PRIMARY, times,
      false, false, false, false, true, false, false, true, false, false, null))
  }
  private def key(c: MapCarousel, code: KeyCode): Unit = Fx {
    Event.fireEvent(classed(c, "carousel-hero").head, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false))
  }

  @Test def itOpensOnTheMapItIsGivenNamedAndCounted(): Unit = {
    val c = carousel(4)
    assertEquals(4, Fx(c.picked))
    assertEquals(WorldRegistry.getDisplayName(4), shown(c, "carousel-title"))
    assertEquals(s"5 / $count", shown(c, "carousel-count"))
    assertEquals("every map is in the strip", count, strip(c).size)
    assertEquals("the picked one, and only it, ringed", Seq(4), ringed(c))
    assertTrue("nobody picked anything yet", picks.isEmpty)
  }

  @Test def aMapThereIsNoneOfOpensOnTheFirst(): Unit = {
    assertEquals(0, Fx(carousel(count).picked))
    assertEquals(0, Fx(carousel(-1).picked))
  }

  @Test def theArrowsStepThroughEveryMapAndRoundTheEnds(): Unit = {
    val c = carousel(0)
    Fx(arrow(c, "Previous map").fire())
    assertEquals("back from the first is the last", count - 1, Fx(c.picked))
    Fx(arrow(c, "Next map").fire())
    assertEquals("on from the last is the first", 0, Fx(c.picked))
    for (_ <- 0 until count) Fx(arrow(c, "Next map").fire())
    assertEquals(0, Fx(c.picked))
    assertEquals((count - 1) +: (0 until count) :+ 0, picks.toSeq)
    assertEquals(WorldRegistry.getDisplayName(0), shown(c, "carousel-title"))
    assertEquals(Seq(0), ringed(c))
    assertTrue("stepping settles on nothing", chosen.isEmpty)
  }

  @Test def aClickInTheStripPicksThatMapAndADoubleClickSettlesOnIt(): Unit = {
    val c = carousel(0)
    click(strip(c)(6))
    assertEquals(6, Fx(c.picked))
    assertEquals(Seq(6), picks.toSeq)
    assertEquals(Seq(6), ringed(c))
    assertEquals(WorldRegistry.getDisplayName(6), shown(c, "carousel-title"))
    assertEquals(s"7 / $count", shown(c, "carousel-count"))
    assertTrue(chosen.isEmpty)

    click(strip(c)(6)) // the one picked already: nothing new
    assertEquals(Seq(6), picks.toSeq)

    // A double click is a click, then a second one counted as the second
    click(strip(c)(2)); click(strip(c)(2), times = 2)
    assertEquals(2, Fx(c.picked))
    assertEquals(Seq(6, 2), picks.toSeq)
    assertEquals(Seq(2), chosen.toSeq)
  }

  @Test def theKeysStepGoToEitherEndAndEnterSettles(): Unit = {
    val c = carousel(0)
    key(c, KeyCode.RIGHT); key(c, KeyCode.RIGHT)
    assertEquals(2, Fx(c.picked))
    key(c, KeyCode.LEFT)
    assertEquals(1, Fx(c.picked))
    key(c, KeyCode.END)
    assertEquals(count - 1, Fx(c.picked))
    key(c, KeyCode.HOME)
    assertEquals(0, Fx(c.picked))
    key(c, KeyCode.LEFT)
    assertEquals(count - 1, Fx(c.picked))
    assertEquals(Seq(1, 2, 1, count - 1, 0, count - 1), picks.toSeq)
    key(c, KeyCode.ENTER)
    assertEquals(Seq(count - 1), chosen.toSeq)
  }

  @Test def aMapPickedFromCodeIsShownWithoutTellingOnPick(): Unit = {
    val c = carousel(0)
    Fx(c.pick(3))
    assertEquals(3, Fx(c.picked))
    assertEquals(WorldRegistry.getDisplayName(3), shown(c, "carousel-title"))
    assertEquals(Seq(3), ringed(c))
    assertTrue(picks.isEmpty)
    Fx(c.pick(count)) // no such map: nothing changes
    assertEquals(3, Fx(c.picked))
  }

  /** Where each of the strip's maps is, from the strip's left edge, laid out as on a page. */
  private def laidOut(c: MapCarousel): (Double, Seq[(Double, Double)]) = Fx {
    val root = new StackPane(c.node)
    new Scene(root, 600, 500)
    root.applyCss()
    root.layout()
    val strip = c.node.getChildren.get(2)
    val left = strip.localToScene(0, 0).getX
    (strip.getLayoutBounds.getWidth, Fx.all(c.node).filter(_.getStyleClass.contains("carousel-item")).map { n =>
      val b = n.localToScene(n.getLayoutBounds)
      (b.getMinX - left, b.getMaxX - left)
    })
  }

  @Test def theStripSlidesToKeepThePickedMapInSight(): Unit = {
    val c = carousel(0)
    val (width, atFirst) = laidOut(c)
    assertEquals(MapCarousel.Form.width, width, 0.01)
    assertEquals("the first map at the strip's left edge", 0.0, atFirst.head._1, 0.01)

    for (i <- Seq(9, count - 1, 4, 0)) {
      Fx(c.pick(i))
      val (_, at) = laidOut(c)
      val (l, r) = at(i)
      assertTrue(s"map $i in sight: $l to $r of $width", l >= -0.01 && r <= width + 0.01)
      if (i == 0) assertEquals(0.0, l, 0.01)
      else if (i == count - 1) assertEquals("the strip's end at its right edge", width, r, 0.51)
      else assertEquals(s"map $i in the middle", width / 2, (l + r) / 2, 0.51)
    }
  }

  @Test def theWheelSlidesTheStripWithinItsEnds(): Unit = {
    val c = carousel(0)
    laidOut(c)
    def wheel(dy: Double): Unit = Fx {
      val strip = c.node.getChildren.get(2)
      Event.fireEvent(strip, new javafx.scene.input.ScrollEvent(javafx.scene.input.ScrollEvent.SCROLL, 0, 0, 0, 0,
        false, false, false, false, false, false, 0, dy, 0, dy, javafx.scene.input.ScrollEvent.HorizontalTextScrollUnits.NONE, 0,
        javafx.scene.input.ScrollEvent.VerticalTextScrollUnits.NONE, 0, 0, null))
    }
    wheel(-40) // the wheel turned down: on along the strip
    val (_, along) = laidOut(c)
    assertEquals(-40.0, along.head._1, 0.51)
    wheel(100000)
    assertEquals("not past the start", 0.0, laidOut(c)._2.head._1, 0.51)
    wheel(-100000)
    val (width, atEnd) = laidOut(c)
    assertEquals("not past the end", width, atEnd.last._2, 0.51)
    assertEquals("sliding picks nothing", 0, Fx(c.picked))
  }

  private def pictureIn(item: Node): javafx.scene.image.Image =
    Fx.all(item).collectFirst { case v: javafx.scene.image.ImageView => v.getImage }.get

  @Test def onlyTheMapsInSightAreShownAndOnlyTheyAreGivenTheirPictures(): Unit = {
    val c = carousel(0)
    val (width, at) = laidOut(c)
    MapPreview.settle(); Fx(()) // every little picture drawn and handed over
    def inSight(spots: Seq[(Double, Double)], i: Int) = spots(i)._2 > 0 && spots(i)._1 < width
    Fx {
      val items = strip(c)
      for (i <- 0 until count) {
        assertEquals(s"map $i shown if and only if in sight", inSight(at, i), items(i).isVisible)
        // A picture landing out of sight is a repaint of the whole window (ui/CLAUDE.md)
        if (inSight(at, i)) assertNotNull(s"map $i, in sight, has its picture", pictureIn(items(i)))
        else assertNull(s"map $i, out of sight, is given none yet", pictureIn(items(i)))
      }
    }
    // Slid along to the last: the maps come into sight with their pictures, those it left go
    Fx(c.pick(count - 1))
    val (_, there) = laidOut(c)
    Fx {
      val items = strip(c)
      for (i <- 0 until count) {
        assertEquals(s"map $i shown if and only if in sight", inSight(there, i), items(i).isVisible)
        if (inSight(there, i)) assertNotNull(s"map $i, now in sight, has its picture", pictureIn(items(i)))
      }
      assertFalse("the first map is out of sight", items.head.isVisible)
    }
  }

  @Test def aCarouselClosedBeforeItsPicturesAreDrawnHasNoneDrawn(): Unit = {
    // The painter held, a carousel (the map picker's, its sizes no other test here asks for) opened
    // and closed, then let go: it draws nothing for it
    Fx(())
    val started = new java.util.concurrent.CountDownLatch(1)
    val release = new java.util.concurrent.CountDownLatch(1)
    MapPreview.paint(() => { started.countDown(); release.await(30, java.util.concurrent.TimeUnit.SECONDS) })
    started.await(30, java.util.concurrent.TimeUnit.SECONDS)
    val look = MapCarousel.Wide
    Fx {
      val c = new MapCarousel(3, look)
      Fx(c.pick(4))
      c.close()
    }
    release.countDown()
    MapPreview.settle(); Fx(())
    Fx {
      for (i <- 0 until count) assertNull(s"map $i's little picture", MapPreview.drawn(i, look.thumbWidth, look.thumbHeight))
      for (i <- Seq(2, 3, 4, 5)) assertNull(s"map $i's big picture", MapPreview.drawn(i, look.width, look.height))
    }
  }

  @Test def howBigAMapIsReadsAsAWordAndItsCells(): Unit = {
    assertEquals("Small map  ·  90 × 90", MapCarousel.sizeText(90, 90))
    assertEquals("Medium map  ·  140 × 140", MapCarousel.sizeText(140, 140))
    assertEquals("Large map  ·  240 × 240", MapCarousel.sizeText(240, 240))
  }
}
