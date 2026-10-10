package com.gridgame.client.render

import com.gridgame.client.gl.GLWindow
import com.gridgame.client.render.hud.CursorPainter
import org.junit.Assert._
import org.junit.Test

/**
 * The mouse's pointer in a match (CursorPainter): a picture made once at the display's pixel density
 * and put down a texel to a pixel, with its hotspot where the mouse is. Cut off at its edge, off the
 * hotspot by a pixel, or put down between pixels (every line smeared across two), it would be the
 * worst-drawn thing on the screen, and the one the eye is always on.
 */
class CursorTest {
  private val Scales = Seq(1f, 1.5f, 2f, 3f)

  private def alpha(px: Array[Int], size: Int, x: Int, y: Int): Int = px(y * size + x) >>> 24

  @Test def thePictureIsWholeAtEveryScale(): Unit = {
    Scales.foreach { s =>
      val size = CursorPainter.artSize(s)
      val px = CursorPainter.art(size, s)
      // Its shadow has faded out before the picture's edge: nothing of it is cut off
      var i = 0
      while (i < size) {
        Seq((i, 0), (i, size - 1), (0, i), (size - 1, i)).foreach { case (x, y) =>
          assertTrue(s"edge pixel ($x, $y) at ${s}x has alpha ${alpha(px, size, x, y)}", alpha(px, size, x, y) <= 2)
        }
        i += 1
      }
      // and it reaches as far as it was drawn to: the ticks' ink out to where they end
      val c = size / 2
      val tickEnd = ((CursorPainter.TickTo + CursorPainter.TickWidth / 2) * s).toInt - 1
      assertEquals(s"the right tick's end at ${s}x", 255, alpha(px, size, c + tickEnd, c))
    }
  }

  @Test def theHotspotIsTheMiddleOfTheDot(): Unit = {
    // An odd picture (at 1x) has its hotspot on a pixel's centre, an even one (at 2x) on a corner
    assertEquals(1, CursorPainter.artSize(1f) % 2)
    assertEquals(0, CursorPainter.artSize(2f) % 2)
    Seq(1f, 2f).foreach { s =>
      val size = CursorPainter.artSize(s)
      val px = CursorPainter.art(size, s)
      val middle = if (size % 2 == 1) Seq((size / 2, size / 2))
                   else for (x <- Seq(size / 2 - 1, size / 2); y <- Seq(size / 2 - 1, size / 2)) yield (x, y)
      middle.foreach { case (x, y) => assertEquals(s"the dot at ($x, $y), ${s}x", 0xFFFFFFFF, px(y * size + x)) }
      // and the picture is the same turned a quarter or mirrored, so the hotspot is its middle
      var y = 0
      while (y < size) {
        var x = 0
        while (x < size) {
          val a = alpha(px, size, x, y)
          assertEquals(s"mirrored ($x, $y) at ${s}x", a.toDouble, alpha(px, size, size - 1 - x, y).toDouble, 3.0)
          assertEquals(s"turned ($x, $y) at ${s}x", a.toDouble, alpha(px, size, y, x).toDouble, 3.0)
          x += 1
        }
        y += 1
      }
    }
  }

  @Test def itIsPutDownOnWholePixelsCentredOnTheHotspot(): Unit = {
    Scales.foreach { s =>
      val size = CursorPainter.artSize(s)
      Seq(0f, 0.25f, 0.5f, 0.75f, 0.999f, 101.5f, 640.2f, 1279.9f).foreach { h =>
        val middle = CursorPainter.cornerPx(h, size) + size / 2.0
        assertEquals(s"the middle of a ${size}px picture for a hotspot at $h", h.toDouble, middle, 0.5 + 1e-4)
      }
    }
    // An odd picture's middle texel is the pixel the hotspot is in, not the next one along
    assertEquals(640 - 26, CursorPainter.cornerPx(640.9f, 53))
    assertEquals(641 - 52, CursorPainter.cornerPx(640.6f, 104))
  }

  @Test def itIsOnlyOursOverTheWindowsContent(): Unit = {
    // GLFW reports the cursor's position past the content too: over the title bar, beyond an edge
    assertTrue(GLWindow.overContent(0, 0, 1280, 720))
    assertTrue(GLWindow.overContent(1279.5, 719.5, 1280, 720))
    assertFalse(GLWindow.overContent(640, -3, 1280, 720))
    assertFalse(GLWindow.overContent(-0.5, 300, 1280, 720))
    assertFalse(GLWindow.overContent(1280, 300, 1280, 720))
    assertFalse(GLWindow.overContent(300, 720, 1280, 720))
  }
}
