package com.gridgame.client.gl

import org.lwjgl.opengl.GL11._
import org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE
import org.lwjgl.opengl.GL13.GL_TEXTURE0
import org.lwjgl.opengl.GL13.glActiveTexture
import org.lwjgl.opengl.GL30._
import org.lwjgl.stb.STBImage._
import org.lwjgl.BufferUtils
import org.lwjgl.system.MemoryUtil

import java.io.{File, FileInputStream, InputStream}
import java.nio.ByteBuffer

/** OpenGL texture wrapper. Supports loading from PNG files and FBO render targets. */
class GLTexture private[gl] (val id: Int, val width: Int, val height: Int, val isFBO: Boolean) {
  private var fboId: Int = 0

  def bind(unit: Int = 0): Unit = {
    glActiveTexture(GL_TEXTURE0 + unit)
    glBindTexture(GL_TEXTURE_2D, id)
  }

  def unbind(): Unit = glBindTexture(GL_TEXTURE_2D, 0)

  /** Bind this texture as a framebuffer render target. Only valid for FBO textures. */
  def bindAsTarget(): Unit = {
    if (!isFBO) throw new IllegalStateException("Not an FBO texture")
    glBindFramebuffer(GL_FRAMEBUFFER, fboId)
    glViewport(0, 0, width, height)
  }

  /** Unbind framebuffer (return to default). */
  def unbindTarget(): Unit = {
    glBindFramebuffer(GL_FRAMEBUFFER, 0)
  }

  def dispose(): Unit = {
    glDeleteTextures(id)
    if (isFBO) glDeleteFramebuffers(fboId)
  }

  /** Get a TextureRegion covering the entire texture. */
  def fullRegion: TextureRegion = TextureRegion(this, 0f, 0f, 1f, 1f)

  /** Get a sub-region given pixel coordinates. */
  def region(x: Int, y: Int, w: Int, h: Int): TextureRegion = {
    TextureRegion(
      this,
      x.toFloat / width,
      y.toFloat / height,
      (x + w).toFloat / width,
      (y + h).toFloat / height
    )
  }
}

object GLTexture {
  /** Load a texture from a PNG file, trying filesystem paths then classpath. */
  def load(relativePath: String, nearest: Boolean = true): GLTexture = {
    val bytes = loadBytes(relativePath)
    if (bytes == null) throw new RuntimeException(s"Texture not found: $relativePath")
    fromBytes(bytes, nearest, null)
  }

  /**
   * Load a texture, handing the decoded RGBA pixels to `inspect` before they are uploaded.
   * Lets a caller measure the image (e.g. where a tile's transparent margin ends) without
   * keeping a second copy of it around, or amend it in place before the GPU gets it.
   */
  def loadInspected(relativePath: String, nearest: Boolean,
                    inspect: (ByteBuffer, Int, Int) => Unit): GLTexture = {
    val bytes = loadBytes(relativePath)
    if (bytes == null) throw new RuntimeException(s"Texture not found: $relativePath")
    fromBytes(bytes, nearest, inspect)
  }

  /** A PNG's pixels, RGBA row by row from the top, handed with its size to `build` and freed after
    * it: for a texture made out of them rather than from them as they are. */
  def decode[A](relativePath: String)(build: (ByteBuffer, Int, Int) => A): A = {
    val bytes = loadBytes(relativePath)
    if (bytes == null) throw new RuntimeException(s"Texture not found: $relativePath")
    val buf = BufferUtils.createByteBuffer(bytes.length)
    buf.put(bytes)
    buf.flip()
    val w = BufferUtils.createIntBuffer(1)
    val h = BufferUtils.createIntBuffer(1)
    val channels = BufferUtils.createIntBuffer(1)
    stbi_set_flip_vertically_on_load(false)
    val pixels = stbi_load_from_memory(buf, w, h, channels, 4)
    if (pixels == null) throw new RuntimeException(s"STB image load failed: ${stbi_failure_reason()}")
    try build(pixels, w.get(0), h.get(0)) finally stbi_image_free(pixels)
  }

  /**
   * An RGBA texture `w` x `h`, filtered linearly, trilinearly through `mipLevels` halvings if it has
   * any, every level of it allocated and none of it filled: for a texture built a piece at a time with
   * [[upload]], its halvings made with [[halve]]. Halved by the driver (glGenerateMipmap), the tile
   * atlas's two halvings cost 11MB of graphics memory on macOS rather than the 2MB they hold.
   */
  def allocate(w: Int, h: Int, mipLevels: Int): GLTexture = {
    val texId = glGenTextures()
    glBindTexture(GL_TEXTURE_2D, texId)
    var level = 0
    while (level <= mipLevels) {
      glTexImage2D(GL_TEXTURE_2D, level, GL_RGBA, Math.max(1, w >> level), Math.max(1, h >> level), 0, GL_RGBA,
        GL_UNSIGNED_BYTE, 0: Long)
      level += 1
    }
    if (mipLevels > 0) {
      glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR)
      glTexParameteri(GL_TEXTURE_2D, org.lwjgl.opengl.GL12.GL_TEXTURE_MAX_LEVEL, mipLevels)
    } else glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
    new GLTexture(texId, w, h, false)
  }

  /** RGBA `pixels`, `w` x `h`, into level `level` of `target` at (x, y) in that level's texels. */
  def upload(target: GLTexture, level: Int, x: Int, y: Int, w: Int, h: Int, pixels: ByteBuffer): Unit = {
    glBindTexture(GL_TEXTURE_2D, target.id)
    glTexSubImage2D(GL_TEXTURE_2D, level, x, y, w, h, GL_RGBA, GL_UNSIGNED_BYTE, pixels)
  }

  /** The halving of RGBA `src`, `w` x `h` (both even), into `dst`: each texel the average of the four
    * under it, channel by channel. Clear texels count with the rest, so they want the colour of the art
    * beside them first ([[padTransparentRings]]). */
  def halve(src: ByteBuffer, w: Int, h: Int, dst: ByteBuffer): Unit = {
    val dw = w / 2; val dh = h / 2
    var y = 0
    while (y < dh) {
      var x = 0
      while (x < dw) {
        val a = ((y * 2) * w + x * 2) * 4; val b = a + w * 4
        var c = 0
        while (c < 4) {
          val sum = (src.get(a + c) & 0xFF) + (src.get(a + 4 + c) & 0xFF) + (src.get(b + c) & 0xFF) +
            (src.get(b + 4 + c) & 0xFF)
          dst.put((y * dw + x) * 4 + c, ((sum + 2) >> 2).toByte)
          c += 1
        }
        x += 1
      }
      y += 1
    }
  }

  /** Scratch pixels to build a texture in, given back with [[freePixels]] once it is uploaded: a
    * direct buffer of the JVM's own lives until its next garbage collection, which a match can go
    * minutes without. Keep them small: macOS keeps a large block it has freed dirty, in the process's
    * footprint (a 7MB copy of the tile atlas stayed there all match). */
  def allocPixels(bytes: Int): ByteBuffer = MemoryUtil.memAlloc(bytes)

  def freePixels(buf: ByteBuffer): Unit = MemoryUtil.memFree(buf)

  /** A texture of `w` x `h` pixels given as ARGB ints, row by row from the top (an AWT image's
    * `getRGB`), with straight alpha, as the batches blend. */
  def fromArgb(argb: Array[Int], w: Int, h: Int, nearest: Boolean = false): GLTexture = {
    val buf = BufferUtils.createByteBuffer(w * h * 4)
    var i = 0
    while (i < w * h) {
      val p = argb(i)
      buf.put(((p >> 16) & 0xFF).toByte).put(((p >> 8) & 0xFF).toByte).put((p & 0xFF).toByte).put(((p >>> 24) & 0xFF).toByte)
      i += 1
    }
    buf.flip()
    val tex = createEmpty(w, h, nearest)
    glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, w, h, GL_RGBA, GL_UNSIGNED_BYTE, buf)
    tex
  }

  /** Create a 1x1 solid white pixel texture (useful for tinted sprite drawing). */
  def createWhitePixel(): GLTexture = {
    val texId = glGenTextures()
    glBindTexture(GL_TEXTURE_2D, texId)
    val buf = org.lwjgl.BufferUtils.createByteBuffer(4)
    buf.put(0xFF.toByte).put(0xFF.toByte).put(0xFF.toByte).put(0xFF.toByte).flip()
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, 1, 1, 0, GL_RGBA, GL_UNSIGNED_BYTE, buf)
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST)
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST)
    new GLTexture(texId, 1, 1, false)
  }

  /** Create an empty texture for use as an FBO render target. */
  def createFBO(width: Int, height: Int): GLTexture = {
    val texId = glGenTextures()
    glBindTexture(GL_TEXTURE_2D, texId)
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, 0: Long)
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)

    val fboId = glGenFramebuffers()
    glBindFramebuffer(GL_FRAMEBUFFER, fboId)
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texId, 0)

    val status = glCheckFramebufferStatus(GL_FRAMEBUFFER)
    if (status != GL_FRAMEBUFFER_COMPLETE) {
      throw new RuntimeException(s"FBO incomplete: $status")
    }
    glBindFramebuffer(GL_FRAMEBUFFER, 0)

    val tex = new GLTexture(texId, width, height, true)
    tex.fboId = fboId
    tex
  }

  /** Resize an FBO texture. Creates a new one and disposes the old. */
  def resizeFBO(old: GLTexture, width: Int, height: Int): GLTexture = {
    if (old != null) old.dispose()
    createFBO(width, height)
  }

  /**
   * Create an empty RGBA texture for use as a texture atlas. With `mipLevels` > 0 it is
   * trilinear-filtered through that many halvings, which the caller builds with
   * [[generateMipmaps]] after filling it.
   */
  def createEmpty(width: Int, height: Int, nearest: Boolean = false, mipLevels: Int = 0): GLTexture = {
    val texId = glGenTextures()
    glBindTexture(GL_TEXTURE_2D, texId)
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, 0: Long)
    val filter = if (nearest) GL_NEAREST else GL_LINEAR
    if (mipLevels > 0) {
      glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR)
      glTexParameteri(GL_TEXTURE_2D, org.lwjgl.opengl.GL12.GL_TEXTURE_MAX_LEVEL, mipLevels)
      glGenerateMipmap(GL_TEXTURE_2D) // allocate the chain, so the texture is complete before it is filled
    } else glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, filter)
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, filter)
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
    new GLTexture(texId, width, height, false)
  }

  /** Rebuild a texture's mip chain from its top level, after its pixels changed. */
  def generateMipmaps(target: GLTexture): Unit = {
    glBindTexture(GL_TEXTURE_2D, target.id)
    glGenerateMipmap(GL_TEXTURE_2D)
  }

  /**
   * Give every transparent texel within `passes` of opaque ones the average colour of its opaque
   * neighbours, keeping its alpha at 0, reading only within the same `cellW` x `cellH` cell of an
   * atlas so no cell takes its neighbour's colour. A texel with alpha 0 contributes nothing to a
   * blended draw, but it does to anything that samples between texels — filtering, a mip level,
   * geometry drawn without blending — and what a PNG leaves in transparent texels is black.
   */
  def padTransparent(px: ByteBuffer, w: Int, h: Int, cellW: Int, cellH: Int, passes: Int): Unit = {
    val n = w * h
    val rgb = new Array[Int](n)
    val filled = new Array[Boolean](n)
    var i = 0
    while (i < n) {
      val o = i * 4
      rgb(i) = ((px.get(o) & 0xFF) << 16) | ((px.get(o + 1) & 0xFF) << 8) | (px.get(o + 2) & 0xFF)
      filled(i) = px.get(o + 3) != 0
      i += 1
    }
    var pass = 0
    while (pass < passes) {
      val next = filled.clone()
      var y = 0
      while (y < h) {
        val cy0 = (y / cellH) * cellH
        var x = 0
        while (x < w) {
          val idx = y * w + x
          if (!filled(idx)) {
            val cx0 = (x / cellW) * cellW
            var sr = 0; var sg = 0; var sb = 0; var k = 0
            var dy = -1
            while (dy <= 1) {
              val ny = y + dy
              if (ny >= cy0 && ny < cy0 + cellH && ny < h) {
                var dx = -1
                while (dx <= 1) {
                  val nx = x + dx
                  if (nx >= cx0 && nx < cx0 + cellW && nx < w && filled(ny * w + nx)) {
                    val c = rgb(ny * w + nx)
                    sr += (c >> 16) & 0xFF; sg += (c >> 8) & 0xFF; sb += c & 0xFF; k += 1
                  }
                  dx += 1
                }
              }
              dy += 1
            }
            if (k > 0) {
              rgb(idx) = ((sr / k) << 16) | ((sg / k) << 8) | (sb / k)
              next(idx) = true
              val o = idx * 4
              px.put(o, (sr / k).toByte); px.put(o + 1, (sg / k).toByte); px.put(o + 2, (sb / k).toByte)
            }
          }
          x += 1
        }
        y += 1
      }
      System.arraycopy(next, 0, filled, 0, n)
      pass += 1
    }
  }

  /**
   * [[padTransparent]] for many rings at once: every transparent texel up to `rings` from an opaque
   * one in its `cellW` x `cellH` cell is given the average colour of its neighbours of the ring
   * before, its alpha kept at 0. Each texel is visited once, where a pass over the whole atlas per
   * ring would visit every texel each time.
   */
  def padTransparentRings(px: ByteBuffer, w: Int, h: Int, cellW: Int, cellH: Int, rings: Int): Unit = {
    val n = w * h
    // 0: transparent, not yet in a ring; 1: in the ring being filled; 2: filled (opaque, or padded).
    // Colours are read from and written to the pixels themselves.
    val state = new Array[Byte](n)
    var i = 0
    while (i < n) {
      if (px.get(i * 4 + 3) != 0) state(i) = 2
      i += 1
    }
    var ring = new Array[Int](4096)
    var ringLen = 0
    var next = new Array[Int](4096)
    var nextLen = 0
    // The first ring: transparent texels touching an opaque one in their own cell
    i = 0
    while (i < n) {
      if (state(i) == 0 && touchesFilled(state, i, w, h, cellW, cellH)) {
        if (ringLen == ring.length) ring = java.util.Arrays.copyOf(ring, ringLen * 2)
        ring(ringLen) = i; ringLen += 1; state(i) = 1
      }
      i += 1
    }
    var colours = new Array[Int](4096)
    var r = 0
    while (r < rings && ringLen > 0) {
      if (colours.length < ringLen) colours = new Array[Int](ringLen)
      // The ring's colours from the texels filled before it...
      var k = 0
      while (k < ringLen) {
        val idx = ring(k)
        val x = idx % w; val y = idx / w
        val cx0 = (x / cellW) * cellW; val cy0 = (y / cellH) * cellH
        var sr = 0; var sg = 0; var sb = 0; var c = 0
        var ny = Math.max(cy0, y - 1)
        while (ny <= Math.min(Math.min(cy0 + cellH, h) - 1, y + 1)) {
          var nx = Math.max(cx0, x - 1)
          while (nx <= Math.min(Math.min(cx0 + cellW, w) - 1, x + 1)) {
            val j = ny * w + nx
            if (state(j) == 2) {
              val o = j * 4
              sr += px.get(o) & 0xFF; sg += px.get(o + 1) & 0xFF; sb += px.get(o + 2) & 0xFF; c += 1
            }
            nx += 1
          }
          ny += 1
        }
        colours(k) = ((sr / c) << 16) | ((sg / c) << 8) | (sb / c)
        k += 1
      }
      // ...then all of it filled at once, and the texels round it make the next ring
      k = 0
      while (k < ringLen) {
        val idx = ring(k); val v = colours(k)
        state(idx) = 2
        val o = idx * 4
        px.put(o, ((v >> 16) & 0xFF).toByte); px.put(o + 1, ((v >> 8) & 0xFF).toByte); px.put(o + 2, (v & 0xFF).toByte)
        k += 1
      }
      nextLen = 0
      k = 0
      while (k < ringLen) {
        val idx = ring(k)
        val x = idx % w; val y = idx / w
        val cx0 = (x / cellW) * cellW; val cy0 = (y / cellH) * cellH
        var ny = Math.max(cy0, y - 1)
        while (ny <= Math.min(Math.min(cy0 + cellH, h) - 1, y + 1)) {
          var nx = Math.max(cx0, x - 1)
          while (nx <= Math.min(Math.min(cx0 + cellW, w) - 1, x + 1)) {
            val j = ny * w + nx
            if (state(j) == 0) {
              if (nextLen == next.length) next = java.util.Arrays.copyOf(next, nextLen * 2)
              next(nextLen) = j; nextLen += 1; state(j) = 1
            }
            nx += 1
          }
          ny += 1
        }
        k += 1
      }
      val t = ring; ring = next; next = t
      ringLen = nextLen
      r += 1
    }
  }

  private def touchesFilled(state: Array[Byte], idx: Int, w: Int, h: Int, cellW: Int, cellH: Int): Boolean = {
    val x = idx % w; val y = idx / w
    val cx0 = (x / cellW) * cellW; val cy0 = (y / cellH) * cellH
    var ny = Math.max(cy0, y - 1)
    while (ny <= Math.min(Math.min(cy0 + cellH, h) - 1, y + 1)) {
      var nx = Math.max(cx0, x - 1)
      while (nx <= Math.min(Math.min(cx0 + cellW, w) - 1, x + 1)) {
        if (state(ny * w + nx) == 2) return true
        nx += 1
      }
      ny += 1
    }
    false
  }

  /** Load image pixels from a file and upload as a sub-image into an existing texture, padding
    * its transparent texels first ([[padTransparent]]) if `padCell` > 0. */
  def uploadSubImage(target: GLTexture, destX: Int, destY: Int, relativePath: String, padCell: Int = 0): Boolean = {
    val bytes = loadBytes(relativePath)
    if (bytes == null) return false
    val buf = BufferUtils.createByteBuffer(bytes.length)
    buf.put(bytes)
    buf.flip()
    val w = BufferUtils.createIntBuffer(1)
    val h = BufferUtils.createIntBuffer(1)
    val channels = BufferUtils.createIntBuffer(1)
    stbi_set_flip_vertically_on_load(false)
    val pixels = stbi_load_from_memory(buf, w, h, channels, 4)
    if (pixels == null) return false
    if (padCell > 0) padTransparent(pixels, w.get(0), h.get(0), padCell, padCell, 2)
    glBindTexture(GL_TEXTURE_2D, target.id)
    glTexSubImage2D(GL_TEXTURE_2D, 0, destX, destY, w.get(0), h.get(0), GL_RGBA, GL_UNSIGNED_BYTE, pixels)
    stbi_image_free(pixels)
    true
  }

  private def fromBytes(data: Array[Byte], nearest: Boolean,
                        inspect: (ByteBuffer, Int, Int) => Unit): GLTexture = {
    val buf = BufferUtils.createByteBuffer(data.length)
    buf.put(data)
    buf.flip()

    val w = BufferUtils.createIntBuffer(1)
    val h = BufferUtils.createIntBuffer(1)
    val channels = BufferUtils.createIntBuffer(1)

    stbi_set_flip_vertically_on_load(false)
    val pixels = stbi_load_from_memory(buf, w, h, channels, 4)
    if (pixels == null) {
      throw new RuntimeException(s"STB image load failed: ${stbi_failure_reason()}")
    }

    if (inspect != null) inspect(pixels, w.get(0), h.get(0))

    val texId = glGenTextures()
    glBindTexture(GL_TEXTURE_2D, texId)
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w.get(0), h.get(0), 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels)

    val filter = if (nearest) GL_NEAREST else GL_LINEAR
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, filter)
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, filter)
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)

    stbi_image_free(pixels)

    new GLTexture(texId, w.get(0), h.get(0), false)
  }

  private def loadBytes(relativePath: String): Array[Byte] = {
    val stream = resolveResourceStream(relativePath)
    if (stream == null) return null
    try {
      stream.readAllBytes()
    } finally {
      stream.close()
    }
  }

  private def resolveResourceStream(relativePath: String): InputStream = {
    val direct = new File(relativePath)
    if (direct.exists()) return new FileInputStream(direct)

    val buildWorkDir = System.getenv("BUILD_WORKING_DIRECTORY")
    if (buildWorkDir != null) {
      val fromWorkDir = new File(buildWorkDir, relativePath)
      if (fromWorkDir.exists()) return new FileInputStream(fromWorkDir)
    }

    getClass.getClassLoader.getResourceAsStream(relativePath)
  }
}
