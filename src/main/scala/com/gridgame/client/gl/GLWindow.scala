package com.gridgame.client.gl

import org.lwjgl.glfw.GLFW._
import org.lwjgl.glfw.{GLFWErrorCallback, GLFWImage}
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11._
import org.lwjgl.stb.STBImage._
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil.NULL
import org.lwjgl.BufferUtils

import java.io.{File, FileInputStream, InputStream}

/**
 * Manages a GLFW window with an OpenGL 3.3 core context for game rendering.
 * Created when entering the game scene, destroyed when leaving.
 */
class GLWindow(title: String, initialWidth: Int, initialHeight: Int) {
  private var window: Long = NULL
  private var _width: Int = initialWidth
  private var _height: Int = initialHeight
  private var _fbWidth: Int = initialWidth
  private var _fbHeight: Int = initialHeight

  def width: Int = _width
  def height: Int = _height
  /** Framebuffer dimensions (may differ from window on Retina/HiDPI). */
  def fbWidth: Int = _fbWidth
  def fbHeight: Int = _fbHeight
  def handle: Long = window
  def isValid: Boolean = window != NULL

  def create(): Unit = {
    GLFWManager.ensureInitialized()

    glfwDefaultWindowHints()
    glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3)
    glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3)
    glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE)
    glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE) // Required on macOS
    glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE) // Show after setup
    glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE)

    window = glfwCreateWindow(initialWidth, initialHeight, title, NULL, NULL)
    if (window == NULL) throw new RuntimeException("Failed to create GLFW window")

    setIcon(Seq("sprites/icon_wizard_256.png", "sprites/icon_wizard_128.png"))

    // Set up resize callback
    glfwSetWindowSizeCallback(window, (_, w, h) => {
      _width = w
      _height = h
    })
    glfwSetFramebufferSizeCallback(window, (_, w, h) => {
      _fbWidth = w
      _fbHeight = h
    })

    // Get actual framebuffer size (for Retina)
    val fbw = Array(0)
    val fbh = Array(0)
    glfwGetFramebufferSize(window, fbw, fbh)
    _fbWidth = fbw(0)
    _fbHeight = fbh(0)

    // Center on primary monitor (GLFW reports none until AppKit has finished launching,
    // which JavaFX has always done by now but a standalone tool may not have)
    val monitor = glfwGetPrimaryMonitor()
    val vidMode = if (monitor != NULL) glfwGetVideoMode(monitor) else null
    if (vidMode != null) {
      glfwSetWindowPos(window,
        (vidMode.width() - initialWidth) / 2,
        (vidMode.height() - initialHeight) / 2)
    }

    makeContextCurrent()
    GL.createCapabilities()

    // VSync enabled — prevents screen tearing
    glfwSwapInterval(1)

    // OpenGL defaults for 2D rendering
    glEnable(GL_BLEND)
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
    glDisable(GL_DEPTH_TEST)
  }

  /** Set the window/taskbar icon (no-op on macOS, where the dock icon comes from the app process instead). */
  def setIcon(relativePaths: Seq[String]): Unit = {
    if (window == NULL) return
    val loaded = relativePaths.flatMap(loadIconPixels)
    if (loaded.isEmpty) return
    val stack = MemoryStack.stackPush()
    try {
      val images = GLFWImage.malloc(loaded.size, stack)
      loaded.zipWithIndex.foreach { case ((w, h, pixels), i) =>
        images.get(i).set(w, h, pixels)
      }
      glfwSetWindowIcon(window, images)
    } finally {
      stack.pop()
    }
    loaded.foreach { case (_, _, pixels) => stbi_image_free(pixels) }
  }

  private def loadIconPixels(relativePath: String): Option[(Int, Int, java.nio.ByteBuffer)] = {
    val stream = resolveIconStream(relativePath)
    if (stream == null) return None
    val bytes = try stream.readAllBytes() finally stream.close()
    val buf = BufferUtils.createByteBuffer(bytes.length)
    buf.put(bytes)
    buf.flip()
    val w = BufferUtils.createIntBuffer(1)
    val h = BufferUtils.createIntBuffer(1)
    val channels = BufferUtils.createIntBuffer(1)
    stbi_set_flip_vertically_on_load(false)
    val pixels = stbi_load_from_memory(buf, w, h, channels, 4)
    if (pixels == null) None else Some((w.get(0), h.get(0), pixels))
  }

  private def resolveIconStream(relativePath: String): InputStream = {
    val direct = new File(relativePath)
    if (direct.exists()) return new FileInputStream(direct)

    val buildWorkDir = System.getenv("BUILD_WORKING_DIRECTORY")
    if (buildWorkDir != null) {
      val fromWorkDir = new File(buildWorkDir, relativePath)
      if (fromWorkDir.exists()) return new FileInputStream(fromWorkDir)
    }

    getClass.getClassLoader.getResourceAsStream(relativePath)
  }

  def show(): Unit = {
    if (window != NULL) glfwShowWindow(window)
  }

  def hide(): Unit = {
    if (window != NULL) glfwHideWindow(window)
  }

  def makeContextCurrent(): Unit = {
    if (window != NULL) glfwMakeContextCurrent(window)
  }

  def swapBuffers(): Unit = {
    if (window != NULL) glfwSwapBuffers(window)
  }

  def shouldClose: Boolean = {
    if (window == NULL) true else glfwWindowShouldClose(window)
  }

  def setFullscreen(fullscreen: Boolean): Unit = {
    if (window == NULL) return
    val monitor = glfwGetPrimaryMonitor()
    val vidMode = glfwGetVideoMode(monitor)
    if (fullscreen) {
      glfwSetWindowMonitor(window, monitor, 0, 0, vidMode.width(), vidMode.height(), vidMode.refreshRate())
    } else {
      val w = initialWidth
      val h = initialHeight
      val x = (vidMode.width() - w) / 2
      val y = (vidMode.height() - h) / 2
      glfwSetWindowMonitor(window, NULL, x, y, w, h, GLFW_DONT_CARE)
    }
  }

  def destroy(): Unit = {
    if (window != NULL) {
      glfwDestroyWindow(window)
      window = NULL
    }
  }
}
