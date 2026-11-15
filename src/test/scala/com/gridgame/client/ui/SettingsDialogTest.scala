package com.gridgame.client.ui

import com.gridgame.client.Fx
import com.gridgame.client.gl.RenderQuality
import com.gridgame.client.ui.screens.SettingsDialog
import javafx.scene.Node
import javafx.scene.control.{Button, ComboBox}
import javafx.scene.layout.StackPane
import javafx.stage.Stage
import org.junit.Assert._
import org.junit.{After, Before, Test}

import scala.collection.mutable
import scala.jdk.CollectionConverters._

/** The settings card (SettingsDialog): the quality, the resolution and the camera's zoom picked there
  * are taken and kept. */
class SettingsDialogTest {
  private val saved = mutable.Map[String, String]()
  private var before: RenderQuality.Store = _

  @Before def ownStore(): Unit = {
    // Never the player's own preferences
    before = RenderQuality.store
    RenderQuality.store = new RenderQuality.Store {
      def get(key: String): Option[String] = saved.get(key)
      def put(key: String, value: String): Unit = saved(key) = value
    }
    RenderQuality.setTier(RenderQuality.HIGH)
    RenderQuality.configure(Array("--resolution=native", "--zoom=100"))
  }

  @After def putBack(): Unit = {
    RenderQuality.store = before
    RenderQuality.setTier(RenderQuality.HIGH)
    RenderQuality.configure(Array("--resolution=native", "--zoom=100"))
  }

  private def all(n: Node): Seq[Node] = n match {
    case p: javafx.scene.Parent => n +: p.getChildrenUnmodifiable.asScala.toSeq.flatMap(all)
    case _ => Seq(n)
  }

  /** A page with the card open over it: its root, and the card's three drop-downs. */
  private def openAll(): (StackPane, ComboBox[String], ComboBox[String], ComboBox[String]) = Fx {
    val root = new StackPane()
    val stage = new Stage()
    stage.setScene(Theme.newScene(root))
    SettingsDialog.show(stage)
    val combos = all(root).collect { case c: ComboBox[_] => c.asInstanceOf[ComboBox[String]] }
    assertEquals("the quality's, the resolution's and the zoom's", 3, combos.size)
    (root, combos(0), combos(1), combos(2))
  }

  private def open(): (StackPane, ComboBox[String], ComboBox[String]) = {
    val (root, quality, resolution, _) = openAll()
    (root, quality, resolution)
  }

  @Test def theZoomPickedIsTakenAndKept(): Unit = {
    val (_, _, _, zoom) = openAll()
    assertEquals("the widest, as every match was drawn before", "100% (widest)", Fx(zoom.getValue))
    assertEquals(11, Fx(zoom.getItems.size))
    Fx(zoom.setValue("150%"))
    assertEquals(150, RenderQuality.zoomPercent)
    assertEquals("150%", saved("zoom"))
    // and it opens on what was chosen
    val (_, _, _, again) = openAll()
    assertEquals("150%", Fx(again.getValue))
  }

  @Test def whatIsPickedIsTakenAndKept(): Unit = {
    val (_, quality, resolution) = open()
    assertEquals("High", Fx(quality.getValue))
    assertTrue(Fx(resolution.getValue).startsWith("Native"))
    Fx(quality.setValue("Medium"))
    assertEquals(RenderQuality.MEDIUM, RenderQuality.tier)
    assertEquals("medium", saved("quality"))
    // The lowest resolution there is, which any display taller than it offers
    val offered = Fx(resolution.getItems.asScala.toSeq)
    Fx(resolution.setValue(offered.last))
    val lines = offered.last.stripSuffix("p")
    if (lines.forall(_.isDigit)) {
      assertEquals(lines.toInt, RenderQuality.resolution)
      assertEquals(s"${lines}p", saved("resolution"))
    }
  }

  @Test def itOpensOnWhatWasChosenAndDoneClosesIt(): Unit = {
    RenderQuality.choose(RenderQuality.LOW)
    val (root, quality, _) = open()
    assertEquals("Low", Fx(quality.getValue))
    val done = Fx(all(root).collect { case b: Button if b.getText == "Done" => b }.head)
    Fx(done.fire())
    assertEquals("the card gone", 0, Fx(all(root).count(_.isInstanceOf[ComboBox[_]])))
  }

  @Test def aDisplayIsOfferedItsOwnResolutionAndTheOnesBelowIt(): Unit = {
    assertEquals(Seq(0, 1440, 1080, 720), SettingsDialog.resolutionsFor(2404))
    assertEquals(Seq(0, 720), SettingsDialog.resolutionsFor(1080))
    assertEquals(Seq(0), SettingsDialog.resolutionsFor(720))
  }
}
