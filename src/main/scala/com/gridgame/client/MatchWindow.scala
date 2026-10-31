package com.gridgame.client

import com.gridgame.client.audio.AudioManager
import com.gridgame.client.diagnostics.{Diagnostics, PastFrame, StateSample}
import com.gridgame.client.gl.{FrameHistory, GLWindow, RenderQuality}
import com.gridgame.client.input.{ControllerHandler, GLKeyboardHandler, GLMouseHandler}
import com.gridgame.client.i18n.Messages
import com.gridgame.common.Constants
import com.gridgame.common.model.WorldData
import com.gridgame.common.world.WorldLoader

import javafx.animation.AnimationTimer
import javafx.application.Platform
import javafx.scene.Scene
import javafx.scene.layout.StackPane
import javafx.scene.paint.Color
import javafx.stage.Screen
import javafx.stage.Stage
import com.gridgame.client.game.ClientState
import com.gridgame.client.render.GLGameRenderer
import com.gridgame.client.ui.screens.{ReportAbout, Screens}
import com.gridgame.common.diagnostics.Recording.{FrameFlags, IncidentKind, NoticedKind}
import org.lwjgl.opengl.{GL11, GL20, GL30}
import org.lwjgl.system.MemoryUtil

import java.util.concurrent.{CompletableFuture, TimeUnit}

/**
 * The match's window. When a match starts the JavaFX stage is hidden and a GLFW window takes over:
 * the renderer draws into it from a loop on the FX thread (which on macOS is also the thread GLFW
 * and OpenGL must run on), and its keyboard, mouse and controller drive the client. When the match
 * ends it is hidden and the stage comes back, on the scoreboard or the lobby browser; the window is
 * destroyed once macOS has finished with it (GLWindow.retire).
 *
 * Every frame also goes into the match's recording for a problem report (design/diagnostics.md): how
 * long it took, where we were and aimed, what was held, and four times a second what the client thinks
 * of us. F8 marks the moment, with a picture of the frame and the last fifteen seconds of small ones
 * (FrameHistory, which keeps them as it goes). A frame that comes long after the last is noticed. A
 * frame that throws closes the match and brings the menus back with the report offered, rather than
 * closing the game.
 */
private[client] final class MatchWindow(app: Screens) {
  import app._

  private var renderLoop: AnimationTimer = _
  private var controllerHandler: ControllerHandler = _
  private var glWindow: GLWindow = _
  private var glRenderer: GLGameRenderer = _
  // The player pressed F8: the frame being drawn is the moment, picture and all
  private var markRequested = false
  // What the recording is told four times a second, one reused
  private val stateSample = new StateSample
  // The last seconds of what the window showed, small, for a mark made late (or a freeze, a crash)
  private var frameHistory: FrameHistory = _
  // A long frame before this (System.nanoTime) is expected, not noticed: the window opening, a full
  // screen toggling, the focus coming and going
  private var hitchesFrom = 0L

  def open(stage: Stage): Unit = {
    switchScreen()

    // Create GL renderer
    glRenderer = new GLGameRenderer(client)
    client.setRejoinListener(() => glRenderer.resetVisualPosition())

    // Prevent JavaFX from shutting down when we hide the last stage
    Platform.setImplicitExit(false)

    // Hide JavaFX stage — GLFW window takes over rendering. Its screen goes with it: a hidden
    // stage still holds its scene, so the character grid's canvases, sprite sheets and the
    // textures behind them stayed allocated for the whole match. Every way out of a match
    // shows a new screen, so nothing needs this one again.
    stage.setScene(new Scene(new StackPane(), Color.BLACK))
    com.gridgame.client.ui.SpriteGenerator.clearCache()
    com.gridgame.client.ui.MapPreview.clearCache()
    com.gridgame.client.ui.WorldBackdrop.clearCache()
    stage.hide()

    // Create GLFW window
    val bounds = Screen.getPrimary.getVisualBounds
    // On Linux one app with the menus' windows, which JavaFX gives the Application's class name, so
    // the desktop (a Chromebook's shelf among them) shows them under the one launcher and icon; and
    // as many of the screen's pixels a unit as the menus are, which X11 leaves to the app (a desktop
    // at 200%: the window filled a quarter of the screen, and everything in it was half the size)
    val unitScale = if (ClientMain.isLinux) Screen.getPrimary.getOutputScaleX else 1.0
    glWindow = new GLWindow("Grid Game", bounds.getWidth.toInt, bounds.getHeight.toInt,
      windowClass = classOf[ClientMain].getName, unitScale = unitScale)
    glWindow.create()
    // The renderer draws the pointer: the system's was lost in a fight (CursorPainter)
    glWindow.hideSystemCursor()
    noteGraphics()
    val history = new FrameHistory()
    frameHistory = history
    Diagnostics.pastFrames = () => history.frames.map(f => PastFrame(f.nanos, f.jpeg))
    hitchesFrom = System.nanoTime() + 3000000000L

    // Set up GLFW input handlers
    val glKeyHandler = new GLKeyboardHandler(client)
    val glMouseHandler = new GLMouseHandler(client, glRenderer.camera)
    // Esc twice leaves. It fires inside this frame's event poll, so tearing down the window
    // it is drawing to waits for the frame to finish.
    glKeyHandler.onLeaveMatch = () => Platform.runLater(() => leaveMatch(stage))
    // F8: inside this frame's event poll too, so the picture is taken once the frame is drawn
    glKeyHandler.onMarkMoment = () => markRequested = true

    org.lwjgl.glfw.GLFW.glfwSetKeyCallback(glWindow.handle, glKeyHandler)
    org.lwjgl.glfw.GLFW.glfwSetCharCallback(glWindow.handle, (_, codepoint: Int) => {
      if (glKeyHandler.isChatMode && codepoint >= 32 && codepoint < 127) {
        if (glKeyHandler.chatInputBuffer.length < Constants.MAX_CHAT_MESSAGE_LEN) {
          glKeyHandler.chatInputBuffer.append(codepoint.toChar)
          client.chatInputText = glKeyHandler.chatInputBuffer.toString
        }
      }
    })
    glWindow.onPointerMoved((x, y) => glMouseHandler.onCursorPos(x, y))
    org.lwjgl.glfw.GLFW.glfwSetMouseButtonCallback(glWindow.handle, (_, button, action, mods) => glMouseHandler.onMouseButton(button, action, mods))

    // Focus callback: clear keys when window loses focus
    var windowFocused = true
    org.lwjgl.glfw.GLFW.glfwSetWindowFocusCallback(glWindow.handle, (_, focused) => {
      windowFocused = focused
      hitchesFrom = System.nanoTime() + 2000000000L
      Diagnostics.event(if (focused) "focus: back" else "focus: lost")
      if (!focused) glKeyHandler.clearAllKeys()
    })

    controllerHandler = new ControllerHandler(client)
    controllerHandler.init()

    // The pointer read into the world as each frame's camera moves, before anything is drawn, so our
    // aim is drawn where the pointer is on that frame's screen (render/CLAUDE.md, *An aim moves with
    // us*); not while the controller's stick aims, which sets the aim itself
    glRenderer.onCamera = () => {
      val pointer = !controllerHandler.stickAiming
      if (pointer) glMouseHandler.refreshWorldPosition()
      pointer
    }

    glWindow.show()
    AudioManager.playBattleMusic()

    // Game loop via AnimationTimer (fires on FX/main thread — required for GLFW on macOS)
    var lastFrameTime = 0L
    var fullscreen = false
    var frameDeltaMs = 0f
    var lastStateNs = 0L
    var lastTier = RenderQuality.tier
    var lastFbWidth = glWindow.fbWidth
    var lastFbHeight = glWindow.fbHeight
    renderLoop = new AnimationTimer() {
      override def handle(now: Long): Unit = {
        val frameStartNs = System.nanoTime()
        try {
          if (glWindow == null || !glWindow.isValid) return
          val deltaNs = if (lastFrameTime == 0L) 16_666_667L else now - lastFrameTime
          lastFrameTime = now
          frameDeltaMs = deltaNs / 1e6f
          val deltaSec = Math.min(deltaNs / 1_000_000_000.0, 0.05)

          org.lwjgl.glfw.GLFW.glfwPollEvents()
          glKeyHandler.update()
          controllerHandler.update()

          // F11 fullscreen toggle (edge-triggered, fires once per press)
          if (glKeyHandler.consumeF11Press()) {
            fullscreen = !fullscreen
            glWindow.setFullscreen(fullscreen)
            hitchesFrom = System.nanoTime() + 2000000000L
            Diagnostics.event(if (fullscreen) "full screen: on" else "full screen: off")
          }

          // Check if GLFW window was closed
          if (glWindow.shouldClose) {
            Diagnostics.matchEnded("closed")
            renderLoop.stop()
            disposeHistory()
            glRenderer.dispose()
            glRenderer = null
            retire(glWindow)
            glWindow = null
            Platform.runLater(() => {
              Platform.setImplicitExit(true)
              AudioManager.stopMusic()
              client.disconnect()
              Platform.exit()
            })
            return
          }

          // The frame, which reads the pointer into the world once it has moved the camera (onCamera)
          glRenderer.render(deltaSec, glWindow.fbWidth, glWindow.fbHeight, glWindow.width, glWindow.height)
          // The pointer, over everything, where the mouse is now: only over this window while it has
          // the focus (elsewhere the system's shows), and not while a controller's stick is aiming,
          // when where the mouse rests means nothing
          if (glWindow.readPointer() && !controllerHandler.stickAiming)
            glRenderer.drawCursor(glWindow.pointerX, glWindow.pointerY, glWindow.fbWidth, glWindow.width, glWindow.height)
          // A moment marked: the frame as the player sees it, pointer and all, before it goes to the screen
          if (markRequested) { markRequested = false; markMoment() }
          // And a small one now and then for the history, read back without waiting (nothing most frames)
          if (Diagnostics.recorder != null) frameHistory.capture(glWindow.fbWidth, glWindow.fbHeight, frameStartNs)
          glWindow.swapBuffers()

          // The frame into the match's recording, and now and then what the client thinks of us
          val recorder = Diagnostics.recorder
          if (recorder != null) {
            // A frame long after the last, when nothing explains it: a stall the player saw
            if (frameDeltaMs > 300f && frameStartNs - hitchesFrom > 0L)
              Diagnostics.noticed(NoticedKind.Hitch, frameDeltaMs.toInt, msAgo = frameDeltaMs.toInt)
            val pos = client.getLocalPosition
            var flags = 0
            if (windowFocused) flags |= FrameFlags.Focused
            if (glKeyHandler.isChatMode) flags |= FrameFlags.ChatOpen
            if (client.getIsDead) flags |= FrameFlags.Dead
            if (fullscreen) flags |= FrameFlags.Fullscreen
            if (controllerHandler.stickAiming) flags |= FrameFlags.StickAiming
            if (client.isCharging) flags |= FrameFlags.Charging
            if (client.getIsMoving) flags |= FrameFlags.Moving
            recorder.frame(frameDeltaMs, ((System.nanoTime() - frameStartNs) / 1e6).toFloat,
              pos.getX.toFloat, pos.getY.toFloat, client.visualPosX.toFloat, client.visualPosY.toFloat,
              client.getMouseWorldX.toFloat, client.getMouseWorldY.toFloat,
              glKeyHandler.heldMask | glMouseHandler.heldMask, flags, RenderQuality.tier, client.aimedAbility)
            if (frameStartNs - lastStateNs >= 250_000_000L) {
              lastStateNs = frameStartNs
              client.sampleState(stateSample)
              recorder.state(stateSample)
            }
            if (RenderQuality.tier != lastTier) {
              lastTier = RenderQuality.tier
              Diagnostics.event(s"quality: ${RenderQuality.tierName}")
            }
            if (glWindow.fbWidth != lastFbWidth || glWindow.fbHeight != lastFbHeight) {
              lastFbWidth = glWindow.fbWidth
              lastFbHeight = glWindow.fbHeight
              Diagnostics.event(s"window: ${lastFbWidth}x$lastFbHeight")
            }
          }
        } catch {
          case e: Exception =>
            System.err.println("=== RENDER LOOP EXCEPTION ===")
            e.printStackTrace()
            com.gridgame.common.observability.Metrics.clientErrors.add(1L,
              io.opentelemetry.api.common.Attributes.of(com.gridgame.common.observability.Attrs.Kind, "render_loop"))
            Diagnostics.incident(IncidentKind.RenderCrash, Diagnostics.stackTrace(e),
              if (frameHistory != null) frameHistory.frames.map(f => PastFrame(f.nanos, f.jpeg)) else Nil)
            renderLoop.stop()
            disposeHistory()
            if (glRenderer != null) { glRenderer.dispose(); glRenderer = null }
            if (glWindow != null) { retire(glWindow); glWindow = null }
            // Out of the match and back to the menus, where the player can tell us what they saw, rather
            // than the game vanishing: the menus don't draw with OpenGL, so they are still fine
            Platform.runLater(() => {
              close(stage, "error")
              client.leaveMatch()
              client.returnToLobbyBrowser()
              showLobbyBrowser(stage, Messages.t("The match closed because of an error"))
              showProblemReport(stage, ReportAbout.MatchError(e.toString))
            })
        } finally {
          val frameMs = (System.nanoTime() - frameStartNs) / 1e6
          com.gridgame.common.observability.Metrics.clientFrameDuration.record(
            frameMs, io.opentelemetry.api.common.Attributes.empty()
          )
          // In `auto` quality this steps the tier down if frames stay slow.
          com.gridgame.client.gl.RenderQuality.noteFrame(frameMs)
        }
      }
    }
    renderLoop.start()

    // Wire game over listener
    client.gameOverListener = () => {
      Platform.runLater(() => {
        close(stage, "finished")
        showScoreboard(stage)
      })
    }

    // The server no longer closes a lobby mid-match, but if one closes anyway don't leave
    // the player in a match nothing is driving.
    client.lobbyClosedListener = () => {
      Platform.runLater(() => {
        close(stage, "closed by the server")
        client.returnToLobbyBrowser()
        showLobbyBrowser(stage, Messages.t("The match was closed"))
      })
    }

    println("Game started!")
  }

  /** Take down the in-game window and bring the JavaFX stage back; a no-op when no match is
    * showing. Run it between frames (from a runLater), never inside one: it destroys the
    * window the frame is drawing to. `how` the match ended, for its recording: finished, left,
    * disconnected, error. */
  def close(stage: Stage, how: String): Unit = {
    if (renderLoop == null) return
    Diagnostics.matchEnded(how)
    renderLoop.stop()
    renderLoop = null
    client.gameOverListener = null
    disposeHistory()
    if (glRenderer != null) { glRenderer.dispose(); glRenderer = null }
    if (glWindow != null) { retire(glWindow); glWindow = null }
    Platform.setImplicitExit(true)
    AudioManager.playMenuMusic()
    stage.show()
  }

  /** The history let go, while the window's context is still current (its GL objects with it). */
  private def disposeHistory(): Unit = {
    Diagnostics.pastFrames = () => Nil
    if (frameHistory != null) { frameHistory.dispose(); frameHistory = null }
  }

  /** The window off the screen now, and destroyed once macOS has finished with it (GLWindow.retire),
    * which is looked at every quarter second, on the FX thread GLFW must be called from. */
  private def retire(window: GLWindow): Unit = {
    window.retire()
    destroyRetiredWhenSettled()
  }

  private def destroyRetiredWhenSettled(): Unit =
    CompletableFuture.delayedExecutor(250, TimeUnit.MILLISECONDS).execute(() =>
      Platform.runLater(() => if (!GLWindow.destroySettled()) destroyRetiredWhenSettled()))

  /** Esc-Esc in a match. */
  private def leaveMatch(stage: Stage): Unit = {
    if (renderLoop == null) return
    if (client.isPracticeMode) {
      // The server ends the session and sends its results like any finished match. Should
      // they never come, don't leave the player standing in a session nothing is running.
      val sessionLobby = client.currentLobbyId
      client.leaveMatch()
      val fallback = new javafx.animation.PauseTransition(javafx.util.Duration.seconds(4))
      fallback.setOnFinished(_ => {
        if (client.clientState == ClientState.PLAYING && client.currentLobbyId == sessionLobby) {
          close(stage, "left")
          client.returnToLobbyBrowser()
          showLobbyBrowser(stage)
        }
      })
      fallback.play()
    } else {
      client.leaveMatch()
      close(stage, "left")
      showLobbyBrowser(stage)
    }
  }

  /** What the match window draws with, for a report: the OpenGL the driver gave it, its size and scale. */
  private def noteGraphics(): Unit =
    try {
      Diagnostics.noteGraphics(GL11.glGetString(GL11.GL_VENDOR), GL11.glGetString(GL11.GL_RENDERER),
        GL11.glGetString(GL11.GL_VERSION), GL11.glGetString(GL20.GL_SHADING_LANGUAGE_VERSION))
      Diagnostics.noteWindow(glWindow.fbWidth, glWindow.fbHeight,
        if (glWindow.width > 0) glWindow.fbWidth.toDouble / glWindow.width else 1.0, RenderQuality.tierName, RenderQuality.isAuto)
      Diagnostics.event(s"window: ${glWindow.fbWidth}x${glWindow.fbHeight}")
    } catch { case e: Exception => System.err.println(s"MatchWindow: couldn't read the graphics' names ($e)") }

  /** The player marked this moment (F8): the frame read back as drawn, for its picture, and the HUD
    * says so. Read before the swap, from the back buffer the frame was drawn into. */
  private def markMoment(): Unit = {
    val w = glWindow.fbWidth
    val h = glWindow.fbHeight
    val pixels = if (Diagnostics.recorder == null || w <= 0 || h <= 0) null else {
      val buf = MemoryUtil.memAlloc(w * h * 3)
      try {
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, 0)
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1)
        GL11.glReadPixels(0, 0, w, h, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, buf)
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 4)
        buf
      } catch {
        case e: Exception =>
          MemoryUtil.memFree(buf)
          System.err.println(s"MatchWindow: couldn't read the frame back ($e)")
          null
      }
    }
    // With the seconds before it: the key is often pressed a moment after what it is about
    Diagnostics.markMoment(pixels, w, h, frameHistory.frames.map(f => PastFrame(f.nanos, f.jpeg)))
  }

  def loadWorld(worldFileName: String): Unit = {
    if (worldFileName.isEmpty) {
      println("Server did not specify a world, using default")
      return
    }
    // Sanitize: reject path traversal attempts
    if (worldFileName.contains("..") || worldFileName.contains("/") || worldFileName.contains("\\")) {
      System.err.println(s"Rejected suspicious world filename from server: $worldFileName")
      client.setWorld(WorldData.createEmpty(Constants.GRID_SIZE, Constants.GRID_SIZE))
      if (glRenderer != null) glRenderer.resetVisualPosition()
      return
    }
    println(s"Server requested world: $worldFileName")
    val worldPath = "worlds/" + worldFileName
    try {
      val world = WorldLoader.load(worldPath)
      client.setWorld(world)
      if (glRenderer != null) glRenderer.resetVisualPosition()
      println(s"Loaded world: ${world.name} (${world.width}x${world.height})")
    } catch {
      case e: Exception =>
        println(s"Failed to load world $worldPath: ${e.getMessage}, using default")
        client.setWorld(WorldData.createEmpty(Constants.GRID_SIZE, Constants.GRID_SIZE))
        if (glRenderer != null) glRenderer.resetVisualPosition()
    }
  }

  /** The app is shutting down: whatever of a match is still up goes with it. Its window is only
    * retired: GLFWManager.terminate destroys it if macOS has finished with it, and otherwise leaves
    * it to the process's exit. */
  def dispose(): Unit = {
    Diagnostics.matchEnded("closed")
    if (renderLoop != null) renderLoop.stop()
    if (glWindow != null && glWindow.isValid) disposeHistory()
    if (glRenderer != null) glRenderer.dispose()
    if (glWindow != null) glWindow.retire()
    if (controllerHandler != null) controllerHandler.cleanup()
  }
}
