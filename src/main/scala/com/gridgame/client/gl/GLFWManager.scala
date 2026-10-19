package com.gridgame.client.gl

import org.lwjgl.glfw.GLFW._
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.system.Platform

/**
 * Singleton to manage GLFW initialization. Shared between ControllerHandler and GLWindow
 * to avoid double-init issues.
 */
object GLFWManager {
  private var initialized = false

  def ensureInitialized(): Unit = {
    if (initialized) return
    GLFWErrorCallback.createPrint(System.err).set()
    glfwInitHint(GLFW_COCOA_MENUBAR, GLFW_FALSE)
    // Linux: X11 wherever there is an X display, as JavaFX's windows always are (on a Wayland
    // desktop, XWayland's). Given the choice, GLFW takes Wayland.
    if (Platform.get() == Platform.LINUX && sys.env.get("DISPLAY").exists(_.nonEmpty) &&
        glfwPlatformSupported(GLFW_PLATFORM_X11))
      glfwInitHint(GLFW_PLATFORM, GLFW_PLATFORM_X11)
    if (!glfwInit()) {
      throw new RuntimeException("Failed to initialize GLFW")
    }
    initialized = true
  }

  def isInitialized: Boolean = initialized

  def terminate(): Unit = {
    if (!initialized) return
    // glfwTerminate destroys every window still open, and would free one macOS hasn't finished
    // with under AppKit (GLWindow.retire): the process's exit takes those, and GLFW, instead
    if (!GLWindow.destroySettled()) return
    glfwTerminate()
    initialized = false
  }
}
