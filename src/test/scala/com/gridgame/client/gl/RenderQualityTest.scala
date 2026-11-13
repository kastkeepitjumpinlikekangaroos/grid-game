package com.gridgame.client.gl

import org.junit.Assert._
import org.junit.{After, Before, Test}

import scala.collection.mutable

/**
 * The graphics quality and resolution (RenderQuality): both the player's choices, kept between runs,
 * and nothing changes either by itself. The resolution caps the lines the world is drawn at, so High's
 * effects can be had at 1080p on a 4.5K screen, where High at its own 2404 lines missed refreshes in a
 * fight (gl/CLAUDE.md, *Graphics quality tiers*).
 */
class RenderQualityTest {
  private val saved = mutable.Map[String, String]()
  private var before: RenderQuality.Store = _

  @Before def ownStore(): Unit = {
    before = RenderQuality.store
    RenderQuality.store = new RenderQuality.Store {
      def get(key: String): Option[String] = saved.get(key)
      def put(key: String, value: String): Unit = saved(key) = value
    }
    RenderQuality.setTier(RenderQuality.HIGH)
    RenderQuality.configure(Array("--resolution=native"))
  }

  @After def putBack(): Unit = {
    RenderQuality.store = before
    RenderQuality.setTier(RenderQuality.HIGH)
    RenderQuality.configure(Array("--resolution=native"))
  }

  // The iMac's window: 2240x1202 points, 4480x2404 pixels
  private def scale: Float = RenderQuality.sceneScale(4480, 2404, 2240)

  @Test def theResolutionCapsTheLinesTheWorldIsDrawnAt(): Unit = {
    assertEquals("High, native", 1.0f, scale, 1e-6f)
    RenderQuality.configure(Array("--resolution=1080"))
    assertEquals("High at 1080p", 1080f / 2404, scale, 1e-6f)
    RenderQuality.configure(Array("--resolution=1440p"))
    assertEquals("High at 1440p", 1440f / 2404, scale, 1e-6f)
    // A tier that draws fewer lines still does
    RenderQuality.setTier(RenderQuality.MEDIUM)
    assertEquals("Medium's own half, under 1440p", 0.5f, scale, 1e-6f)
    RenderQuality.configure(Array("--resolution=720"))
    assertEquals("Medium at 720p", 720f / 2404, scale, 1e-6f)
  }

  @Test def aDisplayNoTallerThanTheResolutionIsDrawnAtItsOwn(): Unit = {
    RenderQuality.configure(Array("--resolution=1080"))
    assertEquals(1.0f, RenderQuality.sceneScale(1920, 1080, 1920), 1e-6f)
    assertEquals(1.0f, RenderQuality.sceneScale(1280, 720, 1280), 1e-6f)
  }

  @Test def theFlagsAreTakenAndPassedOver(): Unit = {
    val rest = RenderQuality.configure(Array("--quality=medium", "--resolution=720p", "--other"))
    assertEquals(RenderQuality.MEDIUM, RenderQuality.tier)
    assertEquals(720, RenderQuality.resolution)
    assertEquals(Seq("--other"), rest.toSeq)
    // auto is High now that nothing steps down; something unknown leaves both as they were
    RenderQuality.configure(Array("--quality=auto"))
    assertEquals(RenderQuality.HIGH, RenderQuality.tier)
    RenderQuality.configure(Array("--quality=ultra", "--resolution=900"))
    assertEquals(RenderQuality.HIGH, RenderQuality.tier)
    assertEquals(720, RenderQuality.resolution)
  }

  @Test def thePlayersChoicesAreKeptForTheNextRun(): Unit = {
    RenderQuality.choose(RenderQuality.LOW)
    RenderQuality.chooseResolution(1080)
    assertEquals(Map("quality" -> "low", "resolution" -> "1080p"), saved.toMap)
    // The next run: back to what a run starts with, then what was saved
    RenderQuality.setTier(RenderQuality.HIGH)
    RenderQuality.configure(Array("--resolution=native"))
    RenderQuality.loadSaved()
    assertEquals(RenderQuality.LOW, RenderQuality.tier)
    assertEquals(1080, RenderQuality.resolution)
    // F7 in a match is a choice too
    RenderQuality.cycleTier()
    assertEquals(RenderQuality.HIGH, RenderQuality.tier)
    assertEquals("high", saved("quality"))
  }

  @Test def aFlagIsForTheRunAndIsNotSaved(): Unit = {
    RenderQuality.chooseResolution(720)
    RenderQuality.loadSaved()
    RenderQuality.configure(Array("--resolution=native", "--quality=medium"))
    assertEquals(0, RenderQuality.resolution)
    assertEquals(Map("resolution" -> "720p"), saved.toMap)
  }

  @Test def onlyTheResolutionsOfferedAreKept(): Unit = {
    RenderQuality.chooseResolution(900)
    assertEquals("not one of them: the display's own", 0, RenderQuality.resolution)
    assertEquals("native", saved("resolution"))
  }
}
