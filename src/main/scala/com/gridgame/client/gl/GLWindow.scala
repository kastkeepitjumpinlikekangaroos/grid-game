package com.gridgame.client.gl

import org.lwjgl.glfw.GLFW._
import org.lwjgl.glfw.{GLFWErrorCallback, GLFWImage, GLFWNativeCocoa}
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11._
import org.lwjgl.stb.STBImage._
import org.lwjgl.system.{JNI, MemoryStack, Platform}
import org.lwjgl.system.MemoryUtil.NULL
import org.lwjgl.system.macosx.ObjCRuntime
import org.lwjgl.BufferUtils

import java.io.{File, FileInputStream, InputStream}
import scala.collection.mutable

/**
 * Manages a GLFW window with an OpenGL 3.3 core context for game rendering.
 * Created when a match starts; when it ends, retired (off the screen at once) and destroyed once
 * macOS has finished with it (see [[retire]]).
 */
class GLWindow(title: String, initialWidth: Int, initialHeight: Int) {
  private var window: Long = NULL
  // When retire took the window off the screen for good (System.nanoTime), or 0
  private var retiredNs: Long = 0L
  // The NSWindow retire closed ahead of GLFW, which destroy has to release (macOS only)
  private var closedNsWindow: Long = NULL
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

  // The system's pointer while a game draws its own (hideSystemCursor), and where the mouse was
  // when readPointer last looked
  private var blankCursor: Long = NULL
  private val pointerXs = new Array[Double](1)
  private val pointerYs = new Array[Double](1)
  def pointerX: Double = pointerXs(0)
  def pointerY: Double = pointerYs(0)

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

  /**
   * No system pointer over this window's content, for a game that draws its own: a cursor with
   * nothing in it, which the system shows only while the mouse is over the content of this window,
   * and puts its own back anywhere else (the title bar, another window). Not GLFW_CURSOR_HIDDEN:
   * on macOS that hides the pointer for the whole app (`[NSCursor hide]`) until the mouse leaves the
   * window, so a window closed under the mouse could take the pointer from the menus too.
   */
  def hideSystemCursor(): Unit = {
    if (window == NULL || blankCursor != NULL) return
    val stack = MemoryStack.stackPush()
    try {
      // Every pixel transparent but the hotspot's, whose alpha is 1/255: Windows takes a cursor with
      // no alpha anywhere as one without an alpha channel and draws its mask instead, a black square
      val pixels = stack.calloc(16 * 16 * 4)
      pixels.put(3, 1.toByte)
      val image = GLFWImage.malloc(stack)
      image.set(16, 16, pixels)
      blankCursor = glfwCreateCursor(image, 0, 0)
    } finally {
      stack.pop()
    }
    if (blankCursor != NULL) glfwSetCursor(window, blankCursor)
  }

  /** Whether the mouse is over this window's content while the window has the focus, reading where it
    * is now into [[pointerX]] / [[pointerY]] (window units, as the cursor callbacks give it). Anywhere
    * else, or with another window in front, the system's pointer is the one that shows. */
  def readPointer(): Boolean = {
    if (window == NULL || glfwGetWindowAttrib(window, GLFW_FOCUSED) != GLFW_TRUE) return false
    glfwGetCursorPos(window, pointerXs, pointerYs)
    GLWindow.overContent(pointerXs(0), pointerYs(0), _width, _height)
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

  /**
   * Take the window off the screen for good, to be destroyed once macOS has finished with it
   * ([[GLWindow.destroySettled]]) rather than now. glfwDestroyWindow frees GLFW's record of the
   * window while AppKit can still hold the NSWindow and GLFW's view in it (a frame or more, longer
   * through a transition), and that view reads the record whenever AppKit asks it anything. In
   * macOS's own full screen (the green button), AppKit is still taking the window out of it when
   * glfwDestroyWindow returns, and redraws the view on the way: the game crashed at the end of a
   * match, `-[GLFWContentView isOpaque]` reading the freed record. Taken off the screen now and
   * destroyed later, the record is still there for anything AppKit asks.
   */
  def retire(): Unit = {
    if (window == NULL || retiredNs != 0L) return
    // The system's arrow back first, as destroy does, while the window is still under the mouse
    if (blankCursor != NULL) glfwSetCursor(window, NULL)
    // A window in GLFW's own full screen (F11) minimises itself when it loses the focus, which
    // hiding it would make it do
    glfwSetWindowAttrib(window, GLFW_AUTO_ICONIFY, GLFW_FALSE)
    putAway()
    GLWindow.retired += this
  }

  /** Off the screen and out of either full screen, and the time a retired window waits started. */
  private def putAway(): Unit = {
    glfwHideWindow(window)
    // GLFW's own full screen (F11) is left hidden, so a display mode GLFW changed for it is back
    // well before the window goes
    if (glfwGetWindowMonitor(window) != NULL)
      glfwSetWindowMonitor(window, NULL, 0, 0, initialWidth, initialHeight, GLFW_DONT_CARE)
    // Hidden is not enough in macOS's own full screen: a hidden window stays in it, Space and all.
    // Taken out of it (toggleFullScreen) the window is put back on screen as the animation ends,
    // over the menus. Closed, as glfwDestroyWindow does it, AppKit takes the Space down and nothing
    // comes back; it is kept alive for GLFW, which counts on closing it to release it (see destroy).
    if (Platform.get() == Platform.MACOSX) {
      closedNsWindow = GLFWNativeCocoa.glfwGetCocoaWindow(window)
      GLWindow.Cocoa.closeKeeping(closedNsWindow)
    }
    retiredNs = System.nanoTime()
  }

  /** A retired window AppKit has put back on the screen, off it again. It does that to a window
    * closed in the middle of going into its full screen: the Space goes, the window stays. */
  private def keepOffScreen(): Unit =
    if (window != NULL && glfwGetWindowAttrib(window, GLFW_VISIBLE) == GLFW_TRUE) putAway()

  /** Whether macOS has finished with a retired window, so it can be destroyed: out of macOS's own
    * full screen, and off the screen long enough for what putting it away set going in AppKit to be
    * over (its references to the window were dropped 0.2-0.7s after the close, measured). */
  def settled: Boolean =
    retiredNs != 0L && System.nanoTime() - retiredNs >= GLWindow.SettleNs && !inMacFullScreen

  /** Whether macOS has the window in its own full screen, or is still taking it out. GLFW has no
    * word for that full screen (only for its own), so AppKit is asked. */
  private def inMacFullScreen: Boolean =
    window != NULL && Platform.get() == Platform.MACOSX &&
      GLWindow.Cocoa.inFullScreen(GLFWNativeCocoa.glfwGetCocoaWindow(window))

  /** Free the window now. One macOS may still be using, a match's, is retired instead. */
  def destroy(): Unit = {
    if (window != NULL) {
      // The system's arrow back first, so a window closed under the mouse doesn't leave the blank
      // cursor showing until the mouse next moves. A retired window did that as it hid: the menus
      // may want a pointer of their own there by now.
      if (blankCursor != NULL && retiredNs == 0L) glfwSetCursor(window, NULL)
      glfwDestroyWindow(window)
      window = NULL
    }
    // Retire closed it with the release turned off, so GLFW's close released nothing: its reference
    // to the window it made is ours to drop
    if (closedNsWindow != NULL) {
      GLWindow.Cocoa.release(closedNsWindow)
      closedNsWindow = NULL
    }
    if (blankCursor != NULL) {
      glfwDestroyCursor(blankCursor)
      blankCursor = NULL
    }
    GLWindow.retired -= this
  }
}

object GLWindow {
  /** The least a retired window stays before it is destroyed, whatever else says it could go. */
  private val SettleNs = 2_000_000_000L

  // Retired windows not destroyed yet. Like every GLFW call, used on the main thread only.
  private val retired = mutable.ArrayBuffer.empty[GLWindow]

  /** Destroy every retired window macOS has finished with ([[GLWindow.retire]]), putting back off
    * the screen any AppKit has shown again, and say whether any are left: call it again later
    * until none are. */
  def destroySettled(): Boolean = {
    retired.foreach(_.keepOffScreen())
    retired.filter(_.settled).foreach(_.destroy())
    retired.isEmpty
  }

  /** AppKit, through the Objective-C runtime. macOS only. */
  private object Cocoa {
    private lazy val msgSend = ObjCRuntime.getLibrary.getFunctionAddress("objc_msgSend")
    private lazy val objcRelease = ObjCRuntime.getLibrary.getFunctionAddress("objc_release")
    private val NSWindowStyleMaskFullScreen = 1L << 14

    def inFullScreen(nsWindow: Long): Boolean =
      nsWindow != NULL &&
        (JNI.invokePPP(nsWindow, ObjCRuntime.sel_getUid("styleMask"), msgSend) & NSWindowStyleMaskFullScreen) != 0

    /** Close an NSWindow without the release closing does by default: glfwDestroyWindow still uses
      * the window GLFW made, and releases it only by closing it again, which now releases nothing,
      * so GLWindow.destroy releases it after. */
    def closeKeeping(nsWindow: Long): Unit = {
      JNI.invokePPPV(nsWindow, ObjCRuntime.sel_getUid("setReleasedWhenClosed:"), 0L, msgSend)
      JNI.invokePPV(nsWindow, ObjCRuntime.sel_getUid("close"), msgSend)
    }

    def release(o: Long): Unit = JNI.invokePV(o, objcRelease)
  }

  /** Whether a cursor position (window units, from the content's top-left) is over a content area
    * `width` x `height`: GLFW reports positions outside it too, over the title bar or past an edge. */
  def overContent(x: Double, y: Double, width: Int, height: Int): Boolean =
    x >= 0 && y >= 0 && x < width && y < height
}
