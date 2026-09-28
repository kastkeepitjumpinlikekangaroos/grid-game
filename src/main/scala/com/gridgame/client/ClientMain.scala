package com.gridgame.client

import com.gridgame.client.audio.AudioManager
import com.gridgame.client.gl.GLFWManager
import com.gridgame.client.ui.{Theme, UiActivity}
import com.gridgame.client.i18n.Messages
import com.gridgame.common.Constants
import com.gridgame.common.model.WorldData

import javafx.application.Application
import javafx.application.Platform
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.image.Image
import javafx.stage.Screen
import javafx.stage.Stage
import com.gridgame.client.game.GameClient
import com.gridgame.client.ui.screens._

/**
 * The client application: the JavaFX app whose stage shows the menus (ui/screens, one class each)
 * and, once logged in, the connection to the server (GameClient). A match is played in a window of
 * its own (MatchWindow). This class starts it all up, switches between screens, connects, and shuts
 * everything down; each screen's layout and behaviour is in its own file.
 */
class ClientMain extends Application with Screens {

  // Package-private so the screen tests can give a screen a GameClient of their own
  private[client] var client: GameClient = _

  private val matchWindow = new MatchWindow(this)

  // Stops the current screen's endless animations (character previews, pulsing dots, glows).
  // Every screen switch runs it, so a screen stops however it was left, including by a
  // disconnect or a match starting; left running, they piled up on the FX thread, which
  // is also the game's render thread.
  private[client] var stopCurrentScreen: () => Unit = () => ()

  private[client] def switchScreen(): Unit = {
    val stop = stopCurrentScreen
    stopCurrentScreen = () => ()
    stop()
    // A listener that updates a screen's controls holds on to that whole screen — its scene
    // graph, its canvases and their textures — for as long as it stays registered, and keeps
    // updating it after it's gone: the lobby room's chat was rebuilt, off screen, on every
    // chat message of the match that followed. Each screen registers the ones it needs
    // after this. (Navigation listeners, which only hold the stage, are left alone.)
    if (client != null) {
      client.lobbyListListener = null
      client.lobbyUpdatedListener = null
      client.chatMessageListener = null
      client.lobbyActionFailedListener = null
      client.rankedQueueListener = null
      client.matchHistoryListener = null
      client.leaderboardListener = null
    }
  }

  // ── The screens ─────────────────────────────────────────────────────────

  private[client] def showWelcomeScreen(stage: Stage, notice: String): Unit = new WelcomeScreen(this).show(stage, notice)
  private[client] def showLobbyBrowser(stage: Stage, notice: String): Unit = new LobbyBrowserScreen(this).show(stage, notice)
  private[client] def showLobbyRoom(stage: Stage): Unit = new LobbyRoomScreen(this).show(stage)
  private[client] def showPracticeSetup(stage: Stage): Unit = new PracticeSetupScreen(this).show(stage)
  private[client] def showRankedQueue(stage: Stage): Unit = new RankedQueueScreen(this).show(stage)
  private[client] def showLeaderboard(stage: Stage): Unit = new LeaderboardScreen(this).show(stage)
  private[client] def showAccountView(stage: Stage): Unit = new AccountScreen(this).show(stage)
  private[client] def showScoreboard(stage: Stage): Unit = new ScoreboardScreen(this).show(stage)
  private[client] def showGameScene(stage: Stage): Unit = matchWindow.open(stage)

  override def start(primaryStage: Stage): Unit = {
    Theme.loadFonts()

    // Initialize internationalization (restores the persisted language) and make
    // a language change rebuild the current (login) screen in the new language.
    Messages.init()
    Messages.onLocaleChanged = () => showWelcomeScreen(primaryStage)

    loadAppIcons(primaryStage)
    setDockIcon()
    // Menu animations pause while nobody is using the window
    UiActivity.install(primaryStage)

    primaryStage.setTitle("Grid Game - Multiplayer 2D")
    primaryStage.setResizable(true)
    val bounds = Screen.getPrimary.getVisualBounds
    primaryStage.setX(bounds.getMinX)
    primaryStage.setY(bounds.getMinY)
    primaryStage.setWidth(bounds.getWidth)
    primaryStage.setHeight(bounds.getHeight)

    showWelcomeScreen(primaryStage)
  }

  /** Sets the title bar / taskbar icon (Windows/Linux; macOS uses the dock icon instead, see setDockIcon). */
  private def loadAppIcons(stage: Stage): Unit = {
    val images = Seq("sprites/icon_wizard_256.png", "sprites/icon_wizard_128.png").flatMap { path =>
      val stream = resolveIconStream(path)
      if (stream == null) None else Some(new Image(stream))
    }
    stage.getIcons.addAll(images: _*)
  }

  /** Sets the macOS dock icon via the AWT Taskbar API (no-op on platforms without a taskbar/dock). */
  private def setDockIcon(): Unit = {
    try {
      if (java.awt.Taskbar.isTaskbarSupported) {
        val taskbar = java.awt.Taskbar.getTaskbar
        if (taskbar.isSupported(java.awt.Taskbar.Feature.ICON_IMAGE)) {
          val stream = resolveIconStream("sprites/icon_wizard_256.png")
          if (stream != null) {
            val img = try javax.imageio.ImageIO.read(stream) finally stream.close()
            if (img != null) taskbar.setIconImage(img)
          }
        }
      }
    } catch {
      case _: Throwable => // Taskbar API unavailable on this platform; ignore
    }
  }

  private def resolveIconStream(relativePath: String): java.io.InputStream = {
    val direct = new java.io.File(relativePath)
    if (direct.exists()) return new java.io.FileInputStream(direct)

    val buildWorkDir = System.getenv("BUILD_WORKING_DIRECTORY")
    if (buildWorkDir != null) {
      val fromWorkDir = new java.io.File(buildWorkDir, relativePath)
      if (fromWorkDir.exists()) return new java.io.FileInputStream(fromWorkDir)
    }

    getClass.getClassLoader.getResourceAsStream(relativePath)
  }

  private[client] def startConnection(stage: Stage, serverHost: String, serverPort: Int,
                                      username: String, password: String, isSignup: Boolean,
                                      statusLabel: Label, actionButton: Button): Unit = {
    val initialWorld = WorldData.createEmpty(Constants.GRID_SIZE, Constants.GRID_SIZE)

    def newClient(): GameClient = {
      val c = new GameClient(serverHost, serverPort, initialWorld, username)
      c.setWorldFileListener(worldFileName => {
        println(s"ClientMain: World file listener triggered with: '$worldFileName'")
        matchWindow.loadWorld(worldFileName)
      })
      c.authResponseListener = (success: Boolean, assignedUUID: java.util.UUID, message: String) => {
        Platform.runLater(() => {
          if (success) {
            c.completeAuthAndJoin(assignedUUID, username)
            // Stats now, so the ranked screen shows the real rating rather than the default 1000
            c.requestMatchHistory()
            showLobbyBrowser(stage)
          } else {
            Theme.showStatus(statusLabel, message, Theme.Tone.Error)
            actionButton.setDisable(false)
          }
        })
      }
      c.disconnectListener = () => handleServerDisconnect(stage)
      c
    }

    // An earlier attempt's connection (a wrong password, say) is still open. Close it rather
    // than leak one per click toward the server's per-IP connection limit.
    if (client != null) client.disconnect()
    client = newClient()

    // Connect on a background thread with retry logic
    val maxRetries = 3
    val retryDelayMs = 2000L
    new Thread(() => {
      var attempt = 0
      var connected = false
      while (attempt < maxRetries && !connected) {
        attempt += 1
        try {
          if (attempt > 1) {
            val shownAttempt = attempt
            Platform.runLater(() => {
              Theme.showStatus(statusLabel, Messages.t("Retrying connection ({0}/{1})...", shownAttempt.toString, maxRetries.toString),
                Theme.Tone.Warn)
            })
            Thread.sleep(retryDelayMs)
            // Create a fresh client for the retry
            client = newClient()
          }
          client.connect()
          connected = true
          // Posted before the request goes out, so a fast rejection can't be overwritten
          // by "Logging in..." and leave it showing next to a re-enabled button.
          Platform.runLater(() => {
            Theme.showStatus(statusLabel, if (isSignup) Messages.t("Creating account...") else Messages.t("Logging in..."),
              Theme.Tone.Info)
          })
          client.sendAuthRequest(username, password, isSignup)
        } catch {
          case _: InterruptedException => return
          case e: Exception =>
            if (attempt >= maxRetries) {
              Platform.runLater(() => {
                Theme.showStatus(statusLabel, Messages.t("Could not connect to the server ({0})", e.getMessage), Theme.Tone.Error)
                actionButton.setDisable(false)
              })
            }
        }
      }
    }).start()
  }

  /** The server connection dropped: leave whatever screen, or match, we were on for login. */
  private def handleServerDisconnect(stage: Stage): Unit = {
    // GameClient already posts this onto the FX thread.
    matchWindow.close(stage)
    stage.show()
    showWelcomeScreen(stage, Messages.t("Disconnected from the server"))
  }

  override def stop(): Unit = {
    matchWindow.dispose()
    if (client != null) client.disconnect()
    AudioManager.shutdown()
    GLFWManager.terminate()
  }
}

object ClientMain {
  /**
   * Let the heap give memory back. The JVM's defaults suit a server: the heap grows to
   * whatever the busiest moment needed and keeps it, since HotSpot only shrinks after a
   * full GC or a concurrent cycle and G1 rarely runs either in a game this light on
   * allocation. So a client that had been through a match sat in the menus holding
   * several hundred MB of heap it wasn't using. These are manageable flags, set here so
   * they apply however the game was launched (bazel run, the packaged app, java -jar);
   * a value given on the command line wins.
   *
   *  - G1PeriodicGCInterval: when no GC has run for 30s, run a concurrent cycle, which is
   *    when G1 returns memory (JEP 346). It never fires while a match is allocating.
   *  - Min/MaxHeapFreeRatio: shrink to at most 30% free rather than 70%.
   */
  private def tuneHeap(): Unit = {
    try {
      val hotspot = java.lang.management.ManagementFactory
        .getPlatformMXBean(classOf[com.sun.management.HotSpotDiagnosticMXBean])
      def setIfDefault(name: String, value: String): Unit =
        if (hotspot.getVMOption(name).getOrigin == com.sun.management.VMOption.Origin.DEFAULT)
          hotspot.setVMOption(name, value)
      setIfDefault("MinHeapFreeRatio", "10") // first: Min may not exceed Max
      setIfDefault("MaxHeapFreeRatio", "30")
      setIfDefault("G1PeriodicGCInterval", "30000")
    } catch {
      case _: Throwable => // not HotSpot: nothing to tune
    }
  }

  def main(args: Array[String]): Unit = {
    tuneHeap()
    // Client telemetry is opt-in. Enable with `--telemetry` flag or `GRIDGAME_TELEMETRY=1`.
    val telemetryEnabled = args.contains("--telemetry") ||
      sys.env.get("GRIDGAME_TELEMETRY").exists(v => v == "1" || v.equalsIgnoreCase("true"))
    if (telemetryEnabled) {
      com.gridgame.common.observability.Telemetry.init("grid-game-client")
      Runtime.getRuntime.addShutdownHook(new Thread(new Runnable {
        def run(): Unit = com.gridgame.common.observability.Telemetry.shutdown()
      }))
    }
    // Graphics quality: `--quality=low|medium|high|auto` or `GRIDGAME_QUALITY`.
    // Defaults to auto, which starts high and steps down if frames stay slow.
    val passThrough = com.gridgame.client.gl.RenderQuality.configure(args.filterNot(_ == "--telemetry"))
    // Asked for low quality outright: also draw the menus at 1x on a HiDPI screen and let the
    // OS scale them up. Text is softer, but every menu repaint fills a quarter of the pixels,
    // and on macOS the pool of window surfaces JavaFX's layer builds up is a quarter the size
    // (at most ~165MB instead of ~650MB on a 5K display). It has to be decided before JavaFX
    // starts, which is why auto — which only steps down once a match is running — can't.
    if (!com.gridgame.client.gl.RenderQuality.isAuto &&
        com.gridgame.client.gl.RenderQuality.tier == com.gridgame.client.gl.RenderQuality.LOW &&
        System.getProperty("prism.allowhidpi") == null) {
      System.setProperty("prism.allowhidpi", "false")
    }
    Application.launch(classOf[ClientMain], passThrough: _*)
  }
}
