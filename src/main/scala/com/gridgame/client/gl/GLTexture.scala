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
    fromBytes(bytes, nearest)
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

  private def fromBytes(data: Array[Byte], nearest: Boolean): GLTexture = {
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
