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

  @Test def aRespawnSnapsTheCameraRatherThanPanningAcrossTheMap(): Unit = {
    val cam = new GameCamera()
    cam.update(10, 10, 0.016, 800, 600)
    cam.resetVisualPosition()
    cam.update(90, 70, 0.016, 800, 600)
    assertEquals((90.0, 70.0), (cam.visualX, cam.visualY))
  }
}
