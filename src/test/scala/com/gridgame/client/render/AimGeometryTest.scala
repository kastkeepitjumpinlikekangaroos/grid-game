package com.gridgame.client.render

import com.gridgame.client.TestClient
import com.gridgame.client.render.world.AimGeometry
import com.gridgame.client.render.world.AimGeometry._
import com.gridgame.common.model._
import org.junit.Assert._
import org.junit.Test

/**
 * Where our aim lies as we walk (AimGeometry; render/CLAUDE.md, *An aim moves with us*). A step puts
 * us on the next cell at once, the sprite glides there over the step and the camera follows the
 * sprite, so our cell runs up to a cell and a half ahead of where we are drawn. Worked out from our
 * cell and drawn from the sprite, the aim swung with every step and pointed off the pointer, and a
 * landing hopped a cell at a time. Here we walk east a step every three frames at 60 a second (a
 * 50ms step, a ranged character's pace), the camera following as the renderer's does, the pointer
 * held still on the screen and read into the world for each frame's camera (MatchWindow's
 * GLGameRenderer.onCamera), as the match's window does.
 */
class AimGeometryTest {
  private val t = new TestClient()
  private val c = t.client
  private val camera = new GameCamera()
  private val aim = new AimGeometry(c, camera)
  private val HW = 20.0; private val HH = 10.0
  // The window, in pixels, and where on it the pointer rests: down and to the left of us
  private val WinW = 1280.0; private val WinH = 720.0
  private val Pointer = (WinW / 2 - 150, WinH / 2 + 120)

  private var frameNo = 0

  /** One frame: a step every third (if walking), the sprite where its glide has it, the camera after
    * it, and the pointer read into the world for the camera. */
  private def frame(walking: Boolean): Unit = {
    if (walking && frameNo % 3 == 0) c.movePlayer(1, 0)
    val cell = c.getLocalPosition
    val glide = if (walking) (frameNo % 3) / 3.0 else 1.0
    camera.update(cell.getX - 1 + glide, cell.getY, 1.0 / 60, WinW / 1.6, WinH / 1.6)
    camera.screenToWorldInto(Pointer._1, Pointer._2)
    c.setMouseWorldPosition(IsometricTransform.lastWorldX, IsometricTransform.lastWorldY)
    frameNo += 1
  }

  /** The way (ux, uy) points on the screen, in degrees. */
  private def screenAngle(ux: Double, uy: Double): Double =
    Math.toDegrees(Math.atan2((ux + uy) * HH, (ux - uy) * HW))

  private def start(): Unit = {
    t.startMatch(spawn = (20, 30))
    camera.resetVisualPosition()
    (0 until 10).foreach(_ => frame(walking = false))
  }

  @Test def theAimStaysPutOnTheScreenAsWeWalk(): Unit = {
    start()
    aim.aimFromHere(atPointer = true, faceWhenOnUs = true)
    val standing = screenAngle(aim.ux, aim.uy)
    var drawnMin = Double.MaxValue; var drawnMax = Double.MinValue
    var cellMin = Double.MaxValue; var cellMax = Double.MinValue
    (0 until 60).foreach { _ =>
      frame(walking = true)
      aim.aimFromHere(atPointer = true, faceWhenOnUs = true)
      val a = screenAngle(aim.ux, aim.uy)
      drawnMin = Math.min(drawnMin, a); drawnMax = Math.max(drawnMax, a)
      // what it was, worked out from our cell
      val pos = c.getLocalPosition
      val b = screenAngle(c.getMouseWorldX - pos.getX, c.getMouseWorldY - pos.getY)
      cellMin = Math.min(cellMin, b); cellMax = Math.max(cellMax, b)
    }
    assertEquals("from where we are drawn toward the pointer, as standing", standing, (drawnMin + drawnMax) / 2, 0.01)
    assertEquals("and never turning", 0.0, drawnMax - drawnMin, 0.01)
    // From our cell it swung degrees with each step (143.8 to 154.2, as written), up to 13 off the pointer
    assertTrue(s"from our cell it swung ${cellMax - cellMin} degrees", cellMax - cellMin > 5.0)
    assertTrue(s"and pointed ${cellMax - standing} degrees off", cellMax - standing > 10.0)
  }

  @Test def aSticksAimIsTheWayItPoints(): Unit = {
    // A controller's stick sets the aim ten cells out from our cell the way it points
    // (ControllerHandler): it is that way, from wherever we are drawn
    start()
    val way = Math.toRadians(70.0)
    (0 until 30).foreach { _ =>
      frame(walking = true)
      val pos = c.getLocalPosition
      c.setMouseWorldPosition(pos.getX + 10 * Math.cos(way), pos.getY + 10 * Math.sin(way))
      aim.aimFromHere(atPointer = false, faceWhenOnUs = true)
      assertEquals(Math.cos(way), aim.ux, 1e-9)
      assertEquals(Math.sin(way), aim.uy, 1e-9)
      assertEquals(camera.visualX, aim.ox, 0.0)
    }
  }

  /** Each frame's landing of a `kind` reaching `max`, as the painter asks for it: the cast's own
    * cell, then where it is shown. */
  private def landings(kind: Int, max: Int, frames: Int, walking: Boolean): Seq[(Double, Double)] =
    (0 until frames).map { _ =>
      frame(walking)
      aim.aimFromHere(atPointer = true, faceWhenOnUs = true)
      val cast = castOf(kind, max)
      assertTrue(aim.landing(kind, max, cast.getX, cast.getY, 1, atPointer = true, 1f / 60))
      (aim.lx - aim.ox, aim.ly - aim.oy)
    }

  private def castOf(kind: Int, max: Int): Position =
    if (kind == DASH) c.dashLanding(max) else if (kind == BLINK) c.blinkLanding(max) else c.trapLanding(max).get

  @Test def aLandingMovesWithUsOnOpenGround(): Unit = {
    for ((kind, max) <- Seq((DASH, 6), (BLINK, 8), (THROW, 7))) {
      aim.forgetLanding()
      start()
      val walked = landings(kind, max, 60, walking = true)
      val moved = walked.map { case (x, y) => Math.hypot(x - walked.head._1, y - walked.head._2) }.max
      assertEquals(s"kind $kind: where it lands, from where we are drawn, never moves as we walk", 0.0, moved, 1e-9)
      // whole cells from where we are drawn, snapped as the cast snaps them
      assertEquals(Math.rint(walked.head._1), walked.head._1, 1e-9)
      assertEquals(Math.rint(walked.head._2), walked.head._2, 1e-9)
      // and standing again, the cast's own cell
      (0 until 40).foreach(_ => frame(walking = false))
      landings(kind, max, 1, walking = false)
      val cast = castOf(kind, max)
      assertEquals(s"kind $kind", cast.getX.toDouble, aim.lx, 1e-9)
      assertEquals(s"kind $kind", cast.getY.toDouble, aim.ly, 1e-9)
    }
  }

  @Test def whatTheTerrainTakesOffALandingIsShown(): Unit = {
    // A wall across the way the pointer is, three cells down from where we stand: a blink stops
    // before it, a dash goes over it, and both are shown where the cast puts us
    (0 until 60).foreach(x => t.world.setTile(x, 34, Tile.Wall))
    for ((kind, max) <- Seq((BLINK, 8), (DASH, 6))) {
      aim.forgetLanding()
      start()
      landings(kind, max, 1, walking = false)
      val cast = castOf(kind, max)
      if (kind == BLINK) assertTrue("the blink is cut short", cast.getY < 34)
      assertEquals(cast.getX.toDouble, aim.lx, 1e-9)
      assertEquals(cast.getY.toDouble, aim.ly, 1e-9)
    }
  }

  @Test def aLaneIsCutShortAtOnceAndGrowsBackOutEased(): Unit = {
    // A shot's lane at its whole reach, then a tree in the way: cut back to it at once, never running
    // on through it
    val frame = 1f / 60
    assertEquals(15f, aim.reach(0, 15f, frame), 0f)
    assertEquals(4f, aim.reach(0, 4f, frame), 0f)
    // and past the tree, grown back out a little each frame to its whole reach, not flicked out to it
    val grown = (0 until 30).map(_ => aim.reach(0, 15f, frame))
    assertTrue(s"the first frame grows it to ${grown.head}", grown.head > 4f && grown.head < 8f)
    assertTrue(grown.zip(grown.tail).forall { case (a, b) => b >= a })
    assertEquals(15f, grown.last, 0.05f)
    // Each lane grows on its own (a fan's spokes), and a new aim starts at its whole reach
    assertEquals(9f, aim.reach(1, 9f, frame), 0f)
    assertEquals(3f, aim.reach(0, 3f, frame), 0f)
    aim.forget()
    assertEquals(15f, aim.reach(0, 15f, frame), 0f)
  }

  @Test def openLandingIsWhatEachCastGivesWithNothingInTheWay(): Unit = {
    // From (30, 30) on open ground, aimed every way at every distance, short of the range and past it
    t.startMatch(spawn = (30, 30))
    for (max <- Seq(1, 3, 5, 6, 8); deg <- 0 until 360 by 7; dist <- Seq(0.3, 0.6, 1.2, 2.5, 4.0, 6.7, 9.5, 12.0)) {
      val dx = dist * Math.cos(Math.toRadians(deg)); val dy = dist * Math.sin(Math.toRadians(deg))
      c.setMouseWorldPosition(30 + dx, 30 + dy)
      def offset(p: Position): (Int, Int) = (p.getX - 30, p.getY - 30)
      def open(kind: Int): (Int, Int) = {
        val o = openLanding(kind, 30, 30, c.getMouseWorldX, c.getMouseWorldY, max)
        (unpackX(o), unpackY(o))
      }
      val dash = c.dashLanding(max)
      if (dash == null)
        assertEquals(s"dash $max at $deg, $dist", NOWHERE, openLanding(DASH, 30, 30, c.getMouseWorldX, c.getMouseWorldY, max))
      else assertEquals(s"dash $max at $deg, $dist", offset(dash), open(DASH))
      assertEquals(s"blink $max at $deg, $dist", offset(c.blinkLanding(max)), open(BLINK))
      assertEquals(s"throw $max at $deg, $dist", offset(c.trapLanding(max).get), open(THROW))
    }
  }
}
