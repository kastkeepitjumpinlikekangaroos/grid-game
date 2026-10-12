package com.gridgame.client

import com.gridgame.client.audio.AudioManager
import com.gridgame.client.gl.GLFWManager
import com.gridgame.client.net.{KnownServers, LoginStore, SavedLogin, ServerIdentityChangedException}
import com.gridgame.client.ui.{Theme, UiActivity}
import com.gridgame.client.i18n.Messages
import com.gridgame.common.Constants
import com.gridgame.common.model.WorldData
import com.gridgame.common.protocol.{AuthRules, SessionEndReason}

import javafx.application.Application
import javafx.application.Platform
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.image.Image
import javafx.stage.Screen
import javafx.stage.Stage
import com.gridgame.client.game.GameClient
import com.gridgame.client.ui.screens._

import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger}

/**
 * The client application: the JavaFX app whose stage shows the menus (ui/screens, one class each)
 * and, once logged in, the connection to the server (GameClient). A match is played in a window of
 * its own (MatchWindow). This class starts it all up, switches between screens, connects, logs in
 * and stays logged in, and shuts everything down; each screen's layout and behaviour is in its own
 * file.
 *
 * Staying logged in (design/networking.md, *Staying logged in*): every login is given a refresh
 * token, which logs the client in again without the password. It is held for as long as the client
 * runs ([[savedLogin]]), so a dropped connection logs back in by itself, and kept on disk too when
 * the player ticked "Remember me" ([[loginStore]]), so the next launch does. A login the player
 * didn't ask to keep is ended on the server when the client closes.
 */
class ClientMain extends Application with Screens {

  // Package-private so the screen tests can give a screen a GameClient of their own
  @volatile private[client] var client: GameClient = _

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

  private[client] def showWelcomeScreen(stage: Stage, notice: String, resume: Boolean, reconnecting: Boolean): Unit =
    new WelcomeScreen(this).show(stage, notice, resume, reconnecting)
  private[client] def showServerChanged(stage: Stage, change: ServerIdentityChangedException): Unit =
    new WelcomeScreen(this).showServerChanged(stage, change)
  private[client] def showLobbyBrowser(stage: Stage, notice: String): Unit = new LobbyBrowserScreen(this).show(stage, notice)
  private[client] def showLobbyRoom(stage: Stage): Unit = new LobbyRoomScreen(this).show(stage)
  private[client] def showPracticeSetup(stage: Stage): Unit = new PracticeSetupScreen(this).show(stage)
  private[client] def showRankedQueue(stage: Stage): Unit = new RankedQueueScreen(this).show(stage)
  private[client] def showLeaderboard(stage: Stage): Unit = new LeaderboardScreen(this).show(stage)
  private[client] def showAccountView(stage: Stage): Unit = new AccountScreen(this).show(stage)
  private[client] def showScoreboard(stage: Stage): Unit = new ScoreboardScreen(this).show(stage)
  private[client] def showGameScene(stage: Stage): Unit = matchWindow.open(stage)

  // ── Staying logged in ──────────────────────────────────────────────────

  /** Where "Remember me" keeps the login between runs (the player's settings folder); a test gives
    * one of its own. */
  private[client] var loginStore: LoginStore = LoginStore.forPlayer()

  /** The servers this computer has met and the identity each showed, which every connection holds
    * its server to before it sends anything (design/networking.md, layer 1); a test gives its own. */
  private[client] var knownServers: KnownServers = KnownServers.forPlayer()

  /** The login the client logs back in with, without the password (its refresh token): from a login
    * until the player logs out, the server refuses it, or the client closes. Null when there is none. */
  @volatile private[client] var savedLogin: SavedLogin = _

  /** Whether the player asked for the login to be remembered: kept on disk as well. */
  @volatile private[client] var rememberLogin = false

  /** The server and name of the last login, for the login form to start from. */
  @volatile private[client] var lastLogin: LastLogin = _

  // Every login attempt's number. What an attempt hears back is acted on only while it is the latest:
  // a newer attempt, a logout, or the player choosing another account leave an older one to die.
  private val loginAttempt = new AtomicInteger()
  // Held to keep or forget the saved login, so a token arriving as the player logs out can't put it
  // back on disk after the logout took it off
  private val loginLock = new Object

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

    showFirstScreen(primaryStage)
    primaryStage.show()
  }

  /** The first screen: logging in with the login remembered from the last run, if there is one (its
    * card), or the login form. */
  private[client] def showFirstScreen(stage: Stage): Unit =
    loginStore.load() match {
      case Some(saved) =>
        savedLogin = saved
        rememberLogin = true
        lastLogin = LastLogin(saved.host, saved.port, saved.name)
        showWelcomeScreen(stage, resume = true)
      case None =>
        showWelcomeScreen(stage)
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

  /** Log in with a password, or sign up: the login form. `remember` keeps the login on disk. */
  private[client] def startConnection(stage: Stage, serverHost: String, serverPort: Int,
                                      username: String, password: String, isSignup: Boolean, remember: Boolean,
                                      statusLabel: Label, actionButton: Button): Unit = {
    // Not to be remembered: whatever an earlier run kept goes now, whatever becomes of this one
    if (!remember) loginStore.clear()
    rememberLogin = remember
    // The form starts from this one next time, whatever becomes of it
    lastLogin = LastLogin(serverHost, serverPort, username)
    logIn(stage, serverHost, serverPort, username, statusLabel, actionButton, resuming = false, reconnecting = false,
      if (isSignup) Messages.t("Creating account...") else Messages.t("Logging in..."),
      c => c.sendAuthRequest(username, password, isSignup))
  }

  /** Log in with the saved login's refresh token: its card's button, pressed by the player, or for
    * them as the client starts or once the connection has dropped (`reconnecting`). */
  private[client] def resumeLogin(stage: Stage, statusLabel: Label, actionButton: Button, reconnecting: Boolean): Unit = {
    val saved = savedLogin
    if (saved == null) { showWelcomeScreen(stage); return }
    logIn(stage, saved.host, saved.port, saved.name, statusLabel, actionButton, resuming = true, reconnecting,
      if (reconnecting) Messages.t("Lost the connection. Reconnecting...") else Messages.t("Logging in as {0}...", saved.name),
      // The newest token: an attempt cut off after the server answered it may have been given the next
      c => { val now = savedLogin; if (now != null) c.sendRefreshLogin(now.token) })
  }

  /**
   * Connect and log in, `ask`ing the server on each connection, and report on `statusLabel`: the
   * lobby browser when it works, `actionButton` enabled again when it doesn't. A connection that
   * fails, or drops before the server answers (it went down), is tried again, a few times; a
   * reconnect tries for longer, with longer waits between.
   */
  private def logIn(stage: Stage, host: String, port: Int, name: String, statusLabel: Label, actionButton: Button,
                    resuming: Boolean, reconnecting: Boolean, asking: String, ask: GameClient => Unit): Unit = {
    val attempt = loginAttempt.incrementAndGet()
    def latest: Boolean = loginAttempt.get == attempt
    val tries = if (reconnecting) ClientMain.ReconnectTries else ClientMain.LoginTries
    def status(text: String, tone: Theme.Tone): Unit =
      Platform.runLater(() => if (latest) Theme.showStatus(statusLabel, text, tone))
    def fail(text: String): Unit = Platform.runLater(() => if (latest) {
      Theme.showStatus(statusLabel, text, Theme.Tone.Error)
      actionButton.setDisable(false)
    })
    // The login form has said it is connecting; the saved login's card has said nothing yet
    if (resuming) status(asking, if (reconnecting) Theme.Tone.Warn else Theme.Tone.Info)

    // An earlier attempt's connection (a wrong password, say) is still open. Close it rather
    // than leak one per click toward the server's per-IP connection limit.
    if (client != null) client.disconnect()

    // One try on a background thread: connect, then ask. What the server says comes back to the
    // listeners newClient sets, which try again if the connection drops before it answers.
    def tryOnce(n: Int): Unit = daemon("Login") {
      try {
        if (n > 1) {
          status(if (reconnecting) Messages.t("Reconnecting ({0}/{1})...", n.toString, tries.toString)
                 else Messages.t("Retrying connection ({0}/{1})...", n.toString, tries.toString), Theme.Tone.Warn)
          Thread.sleep(retryWaitMs(n, reconnecting))
        }
        if (latest) {
          val c = newClient(stage, host, port, name, attempt, resuming, reconnecting, statusLabel, actionButton,
            onCutOff = () => if (n < tries) tryOnce(n + 1)
                             else fail(Messages.t("Disconnected from the server")))
          client = c
          try {
            c.connect()
            // Posted before the request goes out, so a fast rejection can't be overwritten
            // by "Logging in..." and leave it showing next to a re-enabled button.
            status(asking, if (reconnecting) Theme.Tone.Warn else Theme.Tone.Info)
            ask(c)
          } catch {
            case changed: ServerIdentityChangedException =>
              // Not the server met there before: nothing of the login was sent, and trying again
              // would only find the same one. The player decides.
              Platform.runLater(() => if (latest) showServerChanged(stage, changed))
            case e: Exception =>
              if (n < tries) tryOnce(n + 1)
              else fail(if (reconnecting) Messages.t("Could not reconnect ({0})", e.getMessage)
                        else Messages.t("Could not connect to the server ({0})", e.getMessage))
          }
        }
      } catch {
        case _: InterruptedException =>
      }
    }

    tryOnce(1)
  }

  /** How long to wait before try `n` (2 and on): 2s for a login, as it always was; a reconnect
    * starts at 1s and doubles, to 16s. A test's own is quicker. */
  private[client] def retryWaitMs(n: Int, reconnecting: Boolean): Long =
    if (reconnecting) 1000L << Math.min(n - 2, 4) else 2000L

  /** A client for one connection, as `name`, holding its server to the identity met there before: a
    * test's own connects nowhere. */
  private[client] def newGameClient(host: String, port: Int, name: String): GameClient = {
    val c = new GameClient(host, port, WorldData.createEmpty(Constants.GRID_SIZE, Constants.GRID_SIZE), name)
    c.knownServers = knownServers
    c
  }

  /** The player trusts the server's new identity (it was set up again): it is pinned in place of the
    * old one, and the login goes on — with the saved login, if it is that server's, or the form. */
  private[client] def trustServer(stage: Stage, change: ServerIdentityChangedException): Unit = {
    knownServers.trust(change.host, change.port, change.seen)
    val saved = savedLogin
    if (saved != null && saved.host == change.host && saved.port == change.port) showWelcomeScreen(stage, resume = true)
    else showWelcomeScreen(stage)
  }

  /** `body` on a thread of its own, which doesn't keep the client running once it has closed. */
  private def daemon(name: String)(body: => Unit): Unit = {
    val t = new Thread(() => body, name)
    t.setDaemon(true)
    t.start()
  }

  /** A client for one try of login attempt `attempt`, whose listeners act only while the attempt is
    * the latest. `onCutOff` is the connection dropping before the server answered. Once it has
    * answered, a drop is the end of the session if the answer let us in, and nothing otherwise:
    * the server closes a connection that hasn't logged in after LOGIN_DEADLINE_MS, which isn't
    * a reason to send a refused password again. */
  private def newClient(stage: Stage, host: String, port: Int, name: String, attempt: Int, resuming: Boolean,
                        reconnecting: Boolean, statusLabel: Label, actionButton: Button,
                        onCutOff: () => Unit): GameClient = {
    def latest: Boolean = loginAttempt.get == attempt
    val c = newGameClient(host, port, name)
    val answered = new AtomicBoolean(false)
    val loggedIn = new AtomicBoolean(false)
    c.setWorldFileListener(worldFileName => {
      println(s"ClientMain: World file listener triggered with: '$worldFileName'")
      matchWindow.loadWorld(worldFileName)
    })
    // On the packet thread, before the answer behind it: on disk (if it is to be) before the client
    // is first heard on the session, which gives up the token it had for good
    c.refreshTokenListener = (token: Array[Byte], expiresAt: Long) =>
      keepLogin(attempt, SavedLogin(host, port, name, token, expiresAt))
    c.authResponseListener = (success: Boolean, assignedUUID: java.util.UUID, message: String) => {
      answered.set(true)
      // An attempt given up on while it was out: whatever it got, it is let go
      Platform.runLater(() => if (!latest) c.disconnect() else {
        if (success) {
          loggedIn.set(true)
          lastLogin = LastLogin(host, port, name)
          c.completeAuthAndJoin(assignedUUID, name)
          // Stats now, so the ranked screen shows the real rating rather than the default 1000
          c.requestMatchHistory()
          showLobbyBrowser(stage, if (reconnecting) Messages.t("Reconnected to the server") else "")
        } else if (resuming && message == AuthRules.LoginExpired) {
          // Run out, logged out, or used elsewhere: only the password will do now
          forgetLogin()
          showWelcomeScreen(stage, Messages.t("Your login has expired. Please log in again."))
        } else {
          Theme.showStatus(statusLabel, message, Theme.Tone.Error)
          actionButton.setDisable(false)
        }
      })
    }
    // Already on the FX thread (GameClient posts it)
    c.disconnectListener = () => if (latest) {
      if (loggedIn.get) connectionLost(stage, c) else if (!answered.get) onCutOff()
    }
    c
  }

  /** A new refresh token for login attempt `attempt`'s login, if it is still the latest: held, and on
    * disk if the player asked to be remembered. */
  private def keepLogin(attempt: Int, login: SavedLogin): Unit = loginLock.synchronized {
    if (loginAttempt.get == attempt) {
      savedLogin = login
      if (rememberLogin) loginStore.save(login)
    }
  }

  /** No more logging back in without the password: the saved login goes, from memory and disk, and
    * no login attempt under way keeps another. */
  private def forgetLogin(): Unit = loginLock.synchronized {
    loginAttempt.incrementAndGet()
    savedLogin = null
    rememberLogin = false
    loginStore.clear()
  }

  /**
   * The connection dropped after logging in: out of the match, if we were in one, and back in by
   * ourselves with the saved login (its card on the login screen), unless the server said why it
   * ended the session. Logged in somewhere else, it waits for the player: logging back in by itself,
   * it would throw the other one out, and that one us, for ever. Logged out, only the password will do.
   */
  private def connectionLost(stage: Stage, c: GameClient): Unit = {
    // Out of the match, if we were in one, which shows the menus' stage again
    matchWindow.close(stage)
    c.sessionEndReason match {
      case SessionEndReason.REVOKED =>
        forgetLogin()
        showWelcomeScreen(stage, Messages.t("You were logged out. Please log in again."))
      case SessionEndReason.REPLACED =>
        showWelcomeScreen(stage, Messages.t("Your account logged in somewhere else"))
      case _ =>
        if (savedLogin != null) showWelcomeScreen(stage, resume = true, reconnecting = true)
        else showWelcomeScreen(stage, Messages.t("Disconnected from the server"))
    }
  }

  /** Log out: the login is ended on the server (its refresh token logs nobody in again) and
    * forgotten here, and the login screen shown. */
  private[client] def logOut(stage: Stage): Unit = {
    val saved = savedLogin
    forgetLogin()
    val c = client
    if (c != null) {
      if (saved != null) c.sendLogout(saved.token)
      c.disconnect()
    }
    showWelcomeScreen(stage)
  }

  /** The player wants another account than the saved login's: it is forgotten, and ended on the
    * server as well if it can be reached, so no copy of it is any good either. */
  private[client] def forgetSavedLogin(): Unit = {
    val saved = savedLogin
    forgetLogin()
    if (client != null) client.disconnect()
    if (saved != null) daemon("Logout") {
      // Held to the server's identity as any connection is: the token goes to no other
      val c = newGameClient(saved.host, saved.port, saved.name)
      try {
        c.connect()
        c.sendLogout(saved.token)
      } catch {
        case _: Exception => // Not reachable: it runs out by itself
      } finally c.disconnect()
    }
  }

  override def stop(): Unit = {
    matchWindow.dispose()
    loginAttempt.incrementAndGet()
    if (client != null) {
      // A login the player didn't ask to keep ends with the client: nothing will log in with it again
      val saved = savedLogin
      if (saved != null && !rememberLogin) client.sendLogout(saved.token)
      client.disconnect()
    }
    AudioManager.shutdown()
    GLFWManager.terminate()
  }
}

object ClientMain {
  /** Connections a login from the login screen tries before it gives up. */
  private val LoginTries = 3
  /** Connections a reconnect tries before it gives up: about half a minute, for a server restarting. */
  private val ReconnectTries = 6

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
