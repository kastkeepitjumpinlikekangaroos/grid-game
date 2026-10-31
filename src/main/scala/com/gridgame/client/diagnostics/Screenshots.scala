package com.gridgame.client.diagnostics

import org.lwjgl.stb.STBImageWrite
import org.lwjgl.system.MemoryUtil

import java.nio.ByteBuffer
import java.nio.file.Path

/**
 * A marked moment's picture (design/diagnostics.md, *Marking a moment*): the frame the player saw, read
 * back from the window as it was drawn (bottom row first, as OpenGL reads it, three bytes a pixel), made
 * no wider than `maxWidth` and written as a JPEG with stb, off the thread that draws.
 */
object Screenshots {
  val MaxWidth = 1600
  val Quality = 85

  /**
   * `pixels` (w x h, RGB, rows bottom-up, as glReadPixels gives them with a pack alignment of 1) to a
   * JPEG at `file`, the right way up. Halved, or more, until it is at most `maxWidth` wide. Frees
   * `pixels` (MemoryUtil.memAlloc'd) either way. True if it was written.
   */
  def writeJpeg(pixels: ByteBuffer, w: Int, h: Int, file: Path, maxWidth: Int = MaxWidth, quality: Int = Quality): Boolean = {
    var step = 1
    while (w / step > maxWidth) step += 1
    val ow = w / step
    val oh = h / step
    val out = MemoryUtil.memAlloc(ow * oh * 3)
    try {
      // Each output pixel the average of a step x step block, the rows turned the right way up
      var oy = 0
      while (oy < oh) {
        val dst = oy * ow * 3
        val srcRow0 = h - 1 - oy * step // the image's top row is OpenGL's last
        var ox = 0
        while (ox < ow) {
          var r = 0; var g = 0; var b = 0
          var dy = 0
          while (dy < step) {
            val row = srcRow0 - dy
            var dx = 0
            while (dx < step) {
              val i = (row * w + ox * step + dx) * 3
              r += pixels.get(i) & 0xFF
              g += pixels.get(i + 1) & 0xFF
              b += pixels.get(i + 2) & 0xFF
              dx += 1
            }
            dy += 1
          }
          val n = step * step
          out.put(dst + ox * 3, (r / n).toByte)
          out.put(dst + ox * 3 + 1, (g / n).toByte)
          out.put(dst + ox * 3 + 2, (b / n).toByte)
          ox += 1
        }
        oy += 1
      }
      STBImageWrite.stbi_write_jpg(file.toString, ow, oh, 3, out, quality)
    } catch {
      case e: Throwable =>
        System.err.println(s"Screenshots: couldn't write $file ($e)")
        false
    } finally {
      MemoryUtil.memFree(out)
      MemoryUtil.memFree(pixels)
    }
  }
}
