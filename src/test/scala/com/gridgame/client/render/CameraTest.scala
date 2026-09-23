package com.gridgame.client.render

import com.gridgame.common.Constants
import org.junit.Assert._
import org.junit.Test

/**
 * The isometric mapping between the world and the screen, and the camera that follows the
 * player. The mouse aim is the screen-to-world half of it: an error here fires every shot and
 * places every star and fence somewhere other than the cursor.
 */
class CameraTest {
  private val zoom = Constants.CAMERA_ZOOM

  @Test def worldToScreenAndBackIsTheSamePoint(): Unit = {
    for ((wx, wy) <- Seq((0.0, 0.0), (10.5, 3.25), (99.0, 120.0), (-4.0, 7.0))) {
      val sx = IsometricTransform.worldToScreenX(wx, wy, 300.0)
      val sy = IsometricTransform.worldToScreenY(wx, wy, -120.0)
      assertEquals(wx, IsometricTransform.screenToWorldX(sx, sy, 300.0, -120.0), 1e-9)
      assertEquals(wy, IsometricTransform.screenToWorldY(sx, sy, 300.0, -120.0), 1e-9)
    }
  }

  @Test def theAxesRunDiagonallyAcrossTheScreen(): Unit = {
    // +x is down-right on screen, +y is down-left, both by half a tile
    assertEquals(Constants.ISO_HALF_W.toDouble, IsometricTransform.worldToScreenX(1, 0, 0), 0)
    assertEquals(Constants.ISO_HALF_H.toDouble, IsometricTransform.worldToScreenY(1, 0, 0), 0)
    assertEquals(-Constants.ISO_HALF_W.toDouble, IsometricTransform.worldToScreenX(0, 1, 0), 0)
    assertEquals(Constants.ISO_HALF_H.toDouble, IsometricTransform.worldToScreenY(0, 1, 0), 0)
  }

  @Test def theCameraCentresThePlayer(): Unit = {
    val cam = new GameCamera()
    val (w, h) = (1280.0 / zoom, 800.0 / zoom)
    cam.update(40, 25, 0.016, w, h)
    assertEquals(w / 2, IsometricTransform.worldToScreenX(40, 25, cam.camOffX), 1e-9)
    assertEquals(h / 2, IsometricTransform.worldToScreenY(40, 25, cam.camOffY), 1e-9)
  }

  @Test def theCursorAtTheCentreOfTheWindowIsOnThePlayer(): Unit = {
    val cam = new GameCamera()
    cam.update(40, 25, 0.016, 1280.0 / zoom, 800.0 / zoom)
    // Cursor positions are in window pixels; the view is zoomed
    val (wx, wy) = cam.screenToWorld(640.0, 400.0)
    assertEquals(40.0, wx, 1e-9)
    assertEquals(25.0, wy, 1e-9)
  }

  @Test def theCameraEasesToANewPositionAndSettles(): Unit = {
    val cam = new GameCamera()
    cam.update(10, 10, 0.016, 800, 600)
    cam.update(20, 10, 0.016, 800, 600)
    assertTrue("not all the way at once", cam.visualX > 10 && cam.visualX < 20)
    for (_ <- 0 until 200) cam.update(20, 10, 0.016, 800, 600)
    assertEquals(20.0, cam.visualX, 0)
  }

  @Test def aShakeDiesAway(): Unit = {
    val cam = new GameCamera()
    cam.update(10, 10, 0.016, 800, 600)
    val (x0, y0) = (cam.camOffX, cam.camOffY)
    cam.addShake(12)
    cam.update(10, 10, 0.016, 800, 600)
    for (_ <- 0 until 200) cam.update(10, 10, 0.016, 800, 600)
    assertEquals(x0, cam.camOffX, 0)
    assertEquals(y0, cam.camOffY, 0)
  }

  // -- on the pixel grid -----------------------------------------------------------------------

  @Test def givenItsPixelsTheViewSitsOnWholePixelsEvenMidGlide(): Unit = {
    // The terrain is nearest-sampled pixel art: at an offset between pixels, which of its texels
    // are doubled or dropped depends on the offset, and the detail crawled as the camera moved
    for (ppu <- Seq(1.6, 3.2, 0.88, 1.12)) {
      val cam = new GameCamera()
      cam.update(10, 10, 0.016, 800, 450, ppu)
      var step = 0
      while (step < 40) {
        cam.update(10.37 + step * 0.13, 11.91 - step * 0.07, 0.016, 800, 450, ppu)
        for (off <- Seq(cam.camOffX, cam.camOffY)) {
          val px = off * ppu
          assertEquals(s"at $ppu px a unit, step $step", Math.rint(px), px, 1e-6)
        }
        step += 1
      }
    }
  }

  @Test def andIsNeverMoreThanHalfAPixelFromWhereItWouldBe(): Unit = {
    val ppu = 1.12
    val snapped = new GameCamera(); val free = new GameCamera()
    for (i <- 0 until 30) {
      val (x, y) = (20 + i * 0.211, 30 - i * 0.157)
      snapped.update(x, y, 0.016, 800, 450, ppu)
      free.update(x, y, 0.016, 800, 450)
      assertEquals(free.camOffX, snapped.camOffX, 0.5 / ppu + 1e-9)
      assertEquals(free.camOffY, snapped.camOffY, 0.5 / ppu + 1e-9)
    }
  }

  @Test def theCursorAimsWhereTheSnappedViewDrewTheWorld(): Unit = {
    val cam = new GameCamera()
    cam.update(40.3, 25.7, 0.016, 1280.0 / zoom, 800.0 / zoom, 3.2)
    // A world point, where it was drawn, in window pixels, and back
    val sx = IsometricTransform.worldToScreenX(41.0, 26.0, cam.camOffX) * zoom
    val sy = IsometricTransform.worldToScreenY(41.0, 26.0, cam.camOffY) * zoom
    val (wx, wy) = cam.screenToWorld(sx, sy)
    assertEquals(41.0, wx, 1e-9)
    assertEquals(26.0, wy, 1e-9)
  }

  // -- the edge of the world ------------------------------------------------------------------

  @Test def mustTheBackgroundBeDrawn(): Unit = {
    val (w, h) = (800.0, 450.0)
    val cam = new GameCamera()
    // mid-map in a big world: every corner of the view is over a cell
    cam.update(60, 60, 0.016, w, h)
    assertTrue(IsometricTransform.viewInsideWorld(cam.camOffX, cam.camOffY, w, h, 120, 120))
    // near a corner of it, the view runs off the world
    cam.resetVisualPosition(); cam.update(4, 4, 0.016, w, h)
    assertFalse(IsometricTransform.viewInsideWorld(cam.camOffX, cam.camOffY, w, h, 120, 120))
    // and a world smaller than the view never fills it
    cam.resetVisualPosition(); cam.update(7, 7, 0.016, w, h)
    assertFalse(IsometricTransform.viewInsideWorld(cam.camOffX, cam.camOffY, w, h, 14, 14))
  }

  @Test def whenTheViewIsInsideTheWorldEveryPointOfItIsOverACell(): Unit = {
    val (w, h) = (800.0, 450.0)
    val rng = new scala.util.Random(7)
    var inside = 0
    for (_ <- 0 until 400) {
      val cam = new GameCamera()
      cam.update(rng.nextDouble() * 60, rng.nextDouble() * 60, 0.016, w, h)
      if (IsometricTransform.viewInsideWorld(cam.camOffX, cam.camOffY, w, h, 60, 60)) {
        inside += 1
        for (_ <- 0 until 50) {
          val (px, py) = (rng.nextDouble() * w, rng.nextDouble() * h)
          val cx = Math.floor(IsometricTransform.screenToWorldX(px, py, cam.camOffX, cam.camOffY) + 0.5)
          val cy = Math.floor(IsometricTransform.screenToWorldY(px, py, cam.camOffX, cam.camOffY) + 0.5)
          assertTrue(s"($px, $py) is over cell ($cx, $cy)", cx >= 0 && cy >= 0 && cx < 60 && cy < 60)
        }
      }
    }
    assertTrue("some views were inside", inside > 20)
  }

  @Test def aRespawnSnapsTheCameraRatherThanPanningAcrossTheMap(): Unit = {
    val cam = new GameCamera()
    cam.update(10, 10, 0.016, 800, 600)
    cam.resetVisualPosition()
    cam.update(90, 70, 0.016, 800, 600)
    assertEquals((90.0, 70.0), (cam.visualX, cam.visualY))
  }
}
