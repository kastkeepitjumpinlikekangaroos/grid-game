package com.gridgame.client.render

import com.gridgame.client.gl.RenderQuality
import org.junit.Assert._
import org.junit.{After, Before, Test}

import scala.collection.mutable

/**
 * The menu Esc brings up in a match (MatchMenu): laid out inside its card on any window, a click on
 * each control doing what it says and keeping it (the quality, the resolution, the camera's zoom, as
 * the settings' card keeps them), the zoom's track dragged, leaving only on a second press within
 * three seconds, and the arrow keys (or a controller's pad) moving about it and changing what they
 * are on. Never the player's own preferences: a store of the test's own.
 */
class MatchMenuTest {
  private val saved = mutable.Map[String, String]()
  private var before: RenderQuality.Store = _
  private var now = 1000000L
  private val menu = new MatchMenu(() => now)
  private var left = 0

  @Before def setUp(): Unit = {
    before = RenderQuality.store
    RenderQuality.store = new RenderQuality.Store {
      def get(key: String): Option[String] = saved.get(key)
      def put(key: String, value: String): Unit = saved(key) = value
    }
    RenderQuality.setTier(RenderQuality.HIGH)
    RenderQuality.configure(Array("--resolution=native", "--zoom=100"))
    menu.onLeave = () => left += 1
    menu.open()
    // A Retina laptop's window: 1440x900 units, 1800 lines, which offers its own and 1440, 1080, 720
    menu.layout(1440f, 900f, 1800)
  }

  @After def putBack(): Unit = {
    RenderQuality.store = before
    RenderQuality.setTier(RenderQuality.HIGH)
    RenderQuality.configure(Array("--resolution=native", "--zoom=100"))
  }

  private def centre(id: Int): (Float, Float) = (menu.x(id) + menu.w(id) / 2, menu.y(id) + menu.h(id) / 2)

  private def click(id: Int): Unit = {
    val (x, y) = centre(id)
    menu.pointerMoved(x, y)
    menu.pointerPressed(x, y)
    menu.pointerReleased(x, y)
  }

  private def shown(id: Int): Boolean = menu.w(id) > 0f

  // --- Laid out ---

  @Test def itsControlsLieInsideItsCardAndApartOnEveryWindow(): Unit = {
    for ((w, h, lines) <- Seq((1280, 720, 1440), (1440, 900, 1800), (2240, 1260, 2520), (1920, 1080, 1080),
                              (800, 600, 600), (3440, 1440, 1440))) {
      menu.layout(w.toFloat, h.toFloat, lines)
      val where = s"at ${w}x$h"
      assertTrue(s"the card on the window $where", menu.panelX >= 0f && menu.panelY >= 0f &&
        menu.panelX + menu.panelW <= w && menu.panelY + menu.panelH <= h)
      val ids = (0 until MatchMenu.ControlCount).filter(shown)
      for (id <- ids) {
        assertTrue(s"control $id in the card $where", menu.x(id) >= menu.panelX && menu.y(id) >= menu.panelY &&
          menu.x(id) + menu.w(id) <= menu.panelX + menu.panelW + 0.01f && menu.y(id) + menu.h(id) <= menu.panelY + menu.panelH)
        val (cx, cy) = centre(id)
        assertEquals(s"control $id is what is under its middle $where", id, menu.controlAt(cx, cy))
      }
      for (a <- ids; b <- ids if a < b) {
        val apart = menu.x(a) + menu.w(a) <= menu.x(b) + 0.01f || menu.x(b) + menu.w(b) <= menu.x(a) + 0.01f ||
          menu.y(a) + menu.h(a) <= menu.y(b) || menu.y(b) + menu.h(b) <= menu.y(a)
        assertTrue(s"controls $a and $b apart $where", apart)
      }
      assertEquals("nothing outside it", MatchMenu.NoControl, menu.controlAt(menu.panelX - 5f, menu.panelY - 5f))
    }
  }

  @Test def aDisplayIsOfferedItsOwnResolutionAndTheOnesBelowIt(): Unit = {
    assertEquals(Seq(0, 1440, 1080, 720), (0 until menu.resolutionCount).map(menu.resolutionAt))
    menu.layout(1920f, 1080f, 1080)
    assertEquals(Seq(0, 720), (0 until menu.resolutionCount).map(menu.resolutionAt))
    menu.layout(1280f, 720f, 720)
    assertEquals(Seq(0), (0 until menu.resolutionCount).map(menu.resolutionAt))
    // and the options it doesn't offer take no room and no click
    for (i <- 1 until MatchMenu.MaxResolutions) assertFalse(shown(MatchMenu.ResolutionBase + i))
  }

  @Test def whatIsPickedIsWhatTheWorldIsDrawnAt(): Unit = {
    RenderQuality.configure(Array("--resolution=1440"))
    assertEquals(1, menu.pickedResolution)
    // A display no taller than the resolution chosen draws at its own: the display's own is picked
    menu.layout(1920f, 1080f, 1080)
    assertEquals(0, menu.pickedResolution)
  }

  // --- The mouse ---

  @Test def aClickPicksTheQualityAndTheResolutionAndBothAreKept(): Unit = {
    click(MatchMenu.QualityBase + 2)
    assertEquals(RenderQuality.LOW, RenderQuality.tier)
    assertEquals("low", saved("quality"))
    click(MatchMenu.ResolutionBase + 2)
    assertEquals(1080, RenderQuality.resolution)
    assertEquals("1080p", saved("resolution"))
    assertTrue("the menu stays up", menu.isOpen)
  }

  @Test def theZoomStepsInAndOutAndIsKept(): Unit = {
    click(MatchMenu.ZoomIn)
    assertEquals(110, RenderQuality.zoomPercent)
    assertEquals("110%", saved("zoom"))
    click(MatchMenu.ZoomIn)
    click(MatchMenu.ZoomOut)
    assertEquals(110, RenderQuality.zoomPercent)
    click(MatchMenu.ZoomOut)
    click(MatchMenu.ZoomOut)
    assertEquals("never wider than the widest", RenderQuality.MinZoom, RenderQuality.zoomPercent)
    RenderQuality.configure(Array("--zoom=200"))
    click(MatchMenu.ZoomIn)
    assertEquals("nor closer than the closest", RenderQuality.MaxZoom, RenderQuality.zoomPercent)
  }

  @Test def theZoomsTrackIsDragged(): Unit = {
    val y = menu.trackMidY
    menu.pointerPressed(menu.trackX1 + 3f, y)
    assertEquals("pressed at its end: the closest", RenderQuality.MaxZoom, RenderQuality.zoomPercent)
    menu.pointerMoved((menu.trackX0 + menu.trackX1) / 2, y + 30f)
    assertEquals("dragged to its middle, off it as a drag may go", 150, RenderQuality.zoomPercent)
    menu.pointerMoved(menu.trackX0 - 100f, y)
    assertEquals(RenderQuality.MinZoom, RenderQuality.zoomPercent)
    menu.pointerReleased(menu.trackX0 - 100f, y)
    menu.pointerMoved(menu.trackX1, y)
    assertEquals("let go of, it stays", RenderQuality.MinZoom, RenderQuality.zoomPercent)
    assertEquals("100%", saved("zoom"))
    assertEquals(0f, menu.zoomFraction, 0f)
  }

  @Test def aPressOnOneControlAndAReleaseOnAnotherDoNothing(): Unit = {
    val (rx, ry) = centre(MatchMenu.Resume)
    val (lx, ly) = centre(MatchMenu.Leave)
    menu.pointerPressed(rx, ry)
    assertEquals(MatchMenu.Resume, menu.pressed)
    menu.pointerReleased(lx, ly)
    assertTrue(menu.isOpen)
    assertFalse(menu.leaveArmed)
    assertEquals(MatchMenu.NoControl, menu.pressed)
  }

  @Test def resumePutsItAway(): Unit = {
    click(MatchMenu.Resume)
    assertFalse(menu.isOpen)
    assertEquals(0, left)
  }

  // --- Leaving ---

  @Test def leavingTakesASecondPressWithinThreeSeconds(): Unit = {
    click(MatchMenu.Leave)
    assertTrue("armed", menu.leaveArmed)
    assertTrue(menu.isOpen)
    assertEquals(0, left)
    now += MatchMenu.LeaveConfirmMs + 1
    assertFalse("three seconds on, it isn't any more", menu.leaveArmed)
    click(MatchMenu.Leave)
    assertEquals("a press after it stopped waiting only arms it again", 0, left)
    now += 1000
    click(MatchMenu.Leave)
    assertEquals(1, left)
    assertFalse("and the menu goes with the match", menu.isOpen)
  }

  @Test def anythingElsePressedTakesTheLeaveBack(): Unit = {
    click(MatchMenu.Leave)
    click(MatchMenu.QualityBase + 1)
    assertFalse(menu.leaveArmed)
    click(MatchMenu.Leave)
    click(MatchMenu.ZoomIn)
    click(MatchMenu.Leave)
    assertEquals("each press after another control arms it afresh", 0, left)
  }

  @Test def itComesUpAfreshEachTime(): Unit = {
    menu.navigate(0, -1)
    click(MatchMenu.Leave)
    menu.close()
    menu.open()
    assertFalse(menu.leaveArmed)
    assertFalse(menu.focusVisible)
    assertEquals(MatchMenu.RowActions, menu.focusRow)
    assertEquals("on Resume, so Enter goes back to the match", MatchMenu.Resume, menu.focusAction)
    assertEquals(MatchMenu.NoControl, menu.pressed)
  }

  // --- The keyboard and a controller ---

  @Test def theKeysMoveAboutItAndChangeWhatTheyAreOn(): Unit = {
    menu.navigate(0, -1)
    assertTrue("shown once used", menu.focusVisible)
    assertEquals(MatchMenu.RowZoom, menu.focusRow)
    menu.navigate(1, 0)
    assertEquals(110, RenderQuality.zoomPercent)
    menu.navigate(0, -1)
    menu.navigate(1, 0)
    assertEquals(1440, RenderQuality.resolution)
    menu.navigate(0, -1)
    menu.navigate(1, 0)
    menu.navigate(1, 0)
    menu.navigate(1, 0)
    assertEquals("Low, and no further", RenderQuality.LOW, RenderQuality.tier)
    menu.navigate(-1, 0)
    assertEquals(RenderQuality.MEDIUM, RenderQuality.tier)
    menu.navigate(0, -1)
    assertEquals("the top row is as far up as it goes", MatchMenu.RowQuality, menu.focusRow)
    menu.activateFocused()
    assertTrue("Enter on a setting's row presses nothing", menu.isOpen)
    (1 to 5).foreach(_ => menu.navigate(0, 1))
    assertEquals(MatchMenu.RowActions, menu.focusRow)
    menu.navigate(-1, 0)
    assertEquals(MatchMenu.Leave, menu.focusAction)
    menu.activateFocused()
    assertTrue(menu.leaveArmed)
    menu.activateFocused()
    assertEquals(1, left)
  }

  @Test def theMouseTakesTheKeyboardsRingAway(): Unit = {
    menu.navigate(0, -1)
    val (x, y) = centre(MatchMenu.QualityBase)
    menu.pointerPressed(x, y)
    assertFalse(menu.focusVisible)
  }

  @Test def nothingIsUnderThePointerBeforeItIsLaidOut(): Unit = {
    val fresh = new MatchMenu(() => now)
    fresh.open()
    assertEquals(MatchMenu.NoControl, fresh.controlAt(720f, 450f))
    fresh.pointerPressed(720f, 450f)
    fresh.pointerReleased(720f, 450f)
    assertTrue(fresh.isOpen)
  }
}
