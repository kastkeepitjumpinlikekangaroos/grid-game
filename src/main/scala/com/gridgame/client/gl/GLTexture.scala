package com.gridgame.client.gl

import org.lwjgl.opengl.GL11._
import org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE
import org.lwjgl.opengl.GL13.GL_TEXTURE0
import org.lwjgl.opengl.GL13.glActiveTexture
import org.lwjgl.opengl.GL30._
import org.lwjgl.stb.STBImage._
import org.lwjgl.BufferUtils

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
