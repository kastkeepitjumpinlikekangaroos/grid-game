package com.gridgame.client

import com.gridgame.client.audio.AudioManager
import com.gridgame.client.gl.GLWindow
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
import com.gridgame.client.ui.screens.Screens

/**
 * The match's window. When a match starts the JavaFX stage is hidden and a GLFW window takes over:
 * the renderer draws into it from a loop on the FX thread (which on macOS is also the thread GLFW
 * and OpenGL must run on), and its keyboard, mouse and controller drive the client. When the match
 * ends it is destroyed and the stage comes back, on the scoreboard or the lobby browser.
 */
private[client] final class MatchWindow(app: Screens) {
  import app._

  private var renderLoop: AnimationTimer = _
  private var controllerHandler: ControllerHandler = _
  private var glWindow: GLWindow = _
  private var glRenderer: GLGameRenderer = _

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
    stage.hide()

    // Create GLFW window
    val bounds = Screen.getPrimary.getVisualBounds
    glWindow = new GLWindow("Grid Game", bounds.getWidth.toInt, bounds.getHeight.toInt)
    glWindow.create()

    // Set up GLFW input handlers
    val glKeyHandler = new GLKeyboardHandler(client)
    val glMouseHandler = new GLMouseHandler(client, glRenderer.camera)
    // Esc twice leaves. It fires inside this frame's event poll, so tearing down the window
    // it is drawing to waits for the frame to finish.
    glKeyHandler.onLeaveMatch = () => Platform.runLater(() => leaveMatch(stage))

    org.lwjgl.glfw.GLFW.glfwSetKeyCallback(glWindow.handle, glKeyHandler)
    org.lwjgl.glfw.GLFW.glfwSetCharCallback(glWindow.handle, (_, codepoint: Int) => {
      if (glKeyHandler.isChatMode && codepoint >= 32 && codepoint < 127) {
        if (glKeyHandler.chatInputBuffer.length < Constants.MAX_CHAT_MESSAGE_LEN) {
          glKeyHandler.chatInputBuffer.append(codepoint.toChar)
          client.chatInputText = glKeyHandler.chatInputBuffer.toString
        }
      }
    })
    org.lwjgl.glfw.GLFW.glfwSetCursorPosCallback(glWindow.handle, (_, x, y) => glMouseHandler.onCursorPos(x, y))
    org.lwjgl.glfw.GLFW.glfwSetMouseButtonCallback(glWindow.handle, (_, button, action, mods) => glMouseHandler.onMouseButton(button, action, mods))

    // Focus callback: clear keys when window loses focus
    org.lwjgl.glfw.GLFW.glfwSetWindowFocusCallback(glWindow.handle, (_, focused) => {
      if (!focused) glKeyHandler.clearAllKeys()
    })

    controllerHandler = new ControllerHandler(client)
    controllerHandler.init()

    glWindow.show()
    AudioManager.playBattleMusic()

    // Game loop via AnimationTimer (fires on FX/main thread — required for GLFW on macOS)
    var lastFrameTime = 0L
    var fullscreen = false
    renderLoop = new AnimationTimer() {
      override def handle(now: Long): Unit = {
        val frameStartNs = System.nanoTime()
        try {
          if (glWindow == null || !glWindow.isValid) return
          val deltaNs = if (lastFrameTime == 0L) 16_666_667L else now - lastFrameTime
          lastFrameTime = now
          val deltaSec = Math.min(deltaNs / 1_000_000_000.0, 0.05)

          org.lwjgl.glfw.GLFW.glfwPollEvents()
          glKeyHandler.update()
          controllerHandler.update()

          // F11 fullscreen toggle (edge-triggered, fires once per press)
          if (glKeyHandler.consumeF11Press()) {
            fullscreen = !fullscreen
            glWindow.setFullscreen(fullscreen)
          }

          // Check if GLFW window was closed
          if (glWindow.shouldClose) {
            renderLoop.stop()
            glRenderer.dispose()
            glRenderer = null
            glWindow.destroy()
            glWindow = null
            Platform.runLater(() => {
              Platform.setImplicitExit(true)
              AudioManager.stopMusic()
              client.disconnect()
              Platform.exit()
            })
            return
          }

          glRenderer.render(deltaSec, glWindow.fbWidth, glWindow.fbHeight, glWindow.width, glWindow.height)
          // Refresh mouse world position after camera update so next frame's
          // input callbacks use accurate coordinates even if the mouse is stationary
          glMouseHandler.refreshWorldPosition()
          glWindow.swapBuffers()
        } catch {
          case e: Exception =>
            System.err.println("=== RENDER LOOP EXCEPTION ===")
            e.printStackTrace()
            com.gridgame.common.observability.Metrics.clientErrors.add(1L,
              io.opentelemetry.api.common.Attributes.of(com.gridgame.common.observability.Attrs.Kind, "render_loop"))
            renderLoop.stop()
            if (glRenderer != null) { glRenderer.dispose(); glRenderer = null }
            if (glWindow != null) { glWindow.destroy(); glWindow = null }
            Platform.runLater(() => {
              Platform.setImplicitExit(true)
              AudioManager.stopMusic()
              client.disconnect()
              Platform.exit()
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
        close(stage)
        showScoreboard(stage)
      })
    }

    // The server no longer closes a lobby mid-match, but if one closes anyway don't leave
    // the player in a match nothing is driving.
    client.lobbyClosedListener = () => {
      Platform.runLater(() => {
        close(stage)
        client.returnToLobbyBrowser()
        showLobbyBrowser(stage, Messages.t("The match was closed"))
      })
    }

    println("Game started!")
  }

  /** Take down the in-game window and bring the JavaFX stage back; a no-op when no match is
    * showing. Run it between frames (from a runLater), never inside one: it destroys the
    * window the frame is drawing to. */
  def close(stage: Stage): Unit = {
    if (renderLoop == null) return
    renderLoop.stop()
    renderLoop = null
    client.gameOverListener = null
    if (glRenderer != null) { glRenderer.dispose(); glRenderer = null }
    if (glWindow != null) { glWindow.destroy(); glWindow = null }
    Platform.setImplicitExit(true)
    AudioManager.playMenuMusic()
    stage.show()
  }

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
          close(stage)
          client.returnToLobbyBrowser()
          showLobbyBrowser(stage)
        }
      })
      fallback.play()
    } else {
      client.leaveMatch()
      close(stage)
      showLobbyBrowser(stage)
    }
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

  /** The app is shutting down: whatever of a match is still up goes with it. */
  def dispose(): Unit = {
    if (renderLoop != null) renderLoop.stop()
    if (glRenderer != null) glRenderer.dispose()
    if (glWindow != null) glWindow.destroy()
    if (controllerHandler != null) controllerHandler.cleanup()
  }
}
