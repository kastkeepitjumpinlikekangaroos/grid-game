package com.gridgame.client

import com.gridgame.client.audio.AudioManager
import com.gridgame.client.gl.{GLFWManager, GLGameRenderer, GLWindow}
import com.gridgame.client.input.{ControllerHandler, GLKeyboardHandler, GLMouseHandler}
import com.gridgame.client.ui.{CharacterSelectionPanel, UiActivity, ViewportCache}
import com.gridgame.client.i18n.{I18n, Messages}
import com.gridgame.common.Constants
import com.gridgame.common.WorldRegistry
import com.gridgame.common.model.CharacterDef
import com.gridgame.common.model.LobbyInfo
import com.gridgame.common.model.ScoreEntry
import com.gridgame.common.model.TeamAssignment
import com.gridgame.common.model.WorldData
import com.gridgame.common.protocol.LobbyFailure
import com.gridgame.common.protocol.RankedQueueMode
import com.gridgame.common.world.WorldLoader

import javafx.animation.AnimationTimer
import javafx.application.Application
import javafx.application.Platform
import javafx.collections.FXCollections
import javafx.event.EventHandler
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.canvas.Canvas
import javafx.scene.control.Button
import javafx.scene.control.ComboBox
import javafx.scene.control.ScrollPane
import javafx.scene.control.Label
import javafx.scene.control.ListCell
import javafx.scene.control.ListView
import javafx.scene.control.PasswordField
import javafx.scene.control.TextField
import javafx.scene.image.Image
import javafx.util.Callback
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.scene.text.Font
import javafx.scene.text.FontWeight
import javafx.stage.Screen
import javafx.stage.Stage

class ClientMain extends Application {

  private var client: GameClient = _
  private var renderLoop: AnimationTimer = _
  private var controllerHandler: ControllerHandler = _
  private var glWindow: GLWindow = _
  private var glRenderer: GLGameRenderer = _
  private var primaryStageRef: Stage = _

  // Stops the current screen's endless animations (character previews, pulsing dots, glows).
  // Every screen switch runs it, so a screen stops however it was left, including by a
  // disconnect or a match starting; left running, they piled up on the FX thread, which
  // is also the game's render thread.
  private var stopCurrentScreen: () => Unit = () => ()

  private def switchScreen(): Unit = {
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

  private val lobbyDurations = Seq(1, 3, 5, 10, 15, 20)

  private def makeDurationCombo(selectedMinutes: Int): ComboBox[String] = {
    val combo = new ComboBox[String](FXCollections.observableArrayList(lobbyDurations.map(d => Messages.t("{0} min", d.toString)): _*))
    combo.getSelectionModel.select(Math.max(0, lobbyDurations.indexOf(selectedMinutes)))
    combo.setMaxWidth(Double.MaxValue)
    styleCombo(combo)
    combo
  }

  private def selectedDuration(combo: ComboBox[String]): Int =
    lobbyDurations(Math.max(0, combo.getSelectionModel.getSelectedIndex))

  private def showStatus(label: Label, text: String, error: Boolean): Unit = {
    label.setTextFill(Color.web(if (error) "#e84057" else "#8899bb"))
    label.setText(text)
  }

  private def lobbyFailureMessage(reason: Byte): String = reason match {
    case LobbyFailure.RATE_LIMITED => Messages.t("Please wait a moment and try again")
    case LobbyFailure.LOBBY_FULL => Messages.t("That lobby is full")
    case LobbyFailure.NOT_JOINABLE => Messages.t("That lobby is no longer open")
    case LobbyFailure.SERVER_FULL => Messages.t("The server has no room for another lobby")
    case LobbyFailure.ALREADY_IN_LOBBY => Messages.t("You are already in a lobby")
    case LobbyFailure.INVALID_NAME => Messages.t("Invalid lobby name")
    case _ => Messages.t("Something went wrong, please try again")
  }

  // Lets a scroll pane show its page's background. The other way, defining -fx-background on
  // the pane, recolours every label inside: Modena derives label text from that looked-up
  // colour, and taken from an inline style it outranks setTextFill, so all the coloured text
  // in a scroll pane (scoreboard ranks and teams, roster, chat, the ELO badge) came out white.
  private val appStylesheet = "data:text/css;base64," + java.util.Base64.getEncoder.encodeToString(
    ".scroll-pane > .viewport { -fx-background-color: transparent; }".getBytes(java.nio.charset.StandardCharsets.UTF_8))

  private def newScene(root: javafx.scene.Parent): Scene = {
    val scene = new Scene(root)
    scene.getStylesheets.add(appStylesheet)
    scene
  }

  private def placeholderLabel(text: String): Label = {
    val label = new Label(text)
    label.setTextFill(Color.web("#667788"))
    label.setFont(Font.font("Exo 2", 14))
    label
  }

  // -- Enhanced color palette & styles --
  private val darkBg = "-fx-background-color: linear-gradient(to bottom, #1a1a2e 0%, #151528 50%, #111124 100%);"
  private val cardBg = "-fx-background-color: #20203a; -fx-background-radius: 16; -fx-border-color: rgba(255,255,255,0.07); -fx-border-radius: 16; -fx-border-width: 1; -fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.5), 24, 0, 0, 8);"
  private val cardBgSubtle = "-fx-background-color: #1c1c34; -fx-background-radius: 12; -fx-border-color: rgba(255,255,255,0.05); -fx-border-radius: 12; -fx-border-width: 1; -fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.3), 12, 0, 0, 4);"
  private val fieldStyle = "-fx-background-color: #181830; -fx-text-fill: #eef; -fx-font-size: 14; -fx-prompt-text-fill: #778; -fx-padding: 12 14; -fx-background-radius: 8; -fx-border-color: rgba(255,255,255,0.08); -fx-border-radius: 8; -fx-border-width: 1;"
  private val fieldFocusStyle = "-fx-background-color: #181830; -fx-text-fill: #eef; -fx-font-size: 14; -fx-prompt-text-fill: #778; -fx-padding: 12 14; -fx-background-radius: 8; -fx-border-color: #4a9eff; -fx-border-radius: 8; -fx-border-width: 2; -fx-effect: dropshadow(gaussian, rgba(74, 158, 255, 0.3), 16, 0, 0, 0);"
  private val buttonStyle = "-fx-background-color: linear-gradient(to bottom, #5aadff, #3a8eef); -fx-text-fill: white; -fx-font-size: 14; -fx-font-weight: bold; -fx-padding: 11 28; -fx-background-radius: 8; -fx-cursor: hand; -fx-effect: dropshadow(gaussian, rgba(74, 158, 255, 0.35), 12, 0, 0, 3);"
  private val buttonHoverStyle = "-fx-background-color: linear-gradient(to bottom, #6db8ff, #4a9eff); -fx-text-fill: white; -fx-font-size: 14; -fx-font-weight: bold; -fx-padding: 11 28; -fx-background-radius: 8; -fx-cursor: hand; -fx-effect: dropshadow(gaussian, rgba(74, 158, 255, 0.55), 18, 0, 0, 4);"
  private val buttonRedStyle = "-fx-background-color: linear-gradient(to bottom, #f05068, #d83850); -fx-text-fill: white; -fx-font-size: 14; -fx-font-weight: bold; -fx-padding: 11 28; -fx-background-radius: 8; -fx-cursor: hand; -fx-effect: dropshadow(gaussian, rgba(232, 64, 87, 0.35), 12, 0, 0, 3);"
  private val buttonRedHoverStyle = "-fx-background-color: linear-gradient(to bottom, #ff6078, #e84860); -fx-text-fill: white; -fx-font-size: 14; -fx-font-weight: bold; -fx-padding: 11 28; -fx-background-radius: 8; -fx-cursor: hand; -fx-effect: dropshadow(gaussian, rgba(232, 64, 87, 0.55), 18, 0, 0, 4);"
  private val buttonGreenStyle = "-fx-background-color: linear-gradient(to bottom, #3ddb80, #28b865); -fx-text-fill: white; -fx-font-size: 14; -fx-font-weight: bold; -fx-padding: 11 28; -fx-background-radius: 8; -fx-cursor: hand; -fx-effect: dropshadow(gaussian, rgba(46, 204, 113, 0.35), 12, 0, 0, 3);"
  private val buttonGreenHoverStyle = "-fx-background-color: linear-gradient(to bottom, #4deb90, #38c875); -fx-text-fill: white; -fx-font-size: 14; -fx-font-weight: bold; -fx-padding: 11 28; -fx-background-radius: 8; -fx-cursor: hand; -fx-effect: dropshadow(gaussian, rgba(46, 204, 113, 0.55), 18, 0, 0, 4);"
  private val buttonGhostStyle = "-fx-background-color: rgba(255,255,255,0.06); -fx-text-fill: #99aabb; -fx-font-size: 13; -fx-font-weight: bold; -fx-padding: 9 20; -fx-background-radius: 8; -fx-cursor: hand; -fx-border-color: rgba(255,255,255,0.1); -fx-border-radius: 8; -fx-border-width: 1;"
  private val buttonGhostHoverStyle = "-fx-background-color: rgba(255,255,255,0.12); -fx-text-fill: #ccdde8; -fx-font-size: 13; -fx-font-weight: bold; -fx-padding: 9 20; -fx-background-radius: 8; -fx-cursor: hand; -fx-border-color: rgba(255,255,255,0.2); -fx-border-radius: 8; -fx-border-width: 1;"
  private val labelStyle = "-fx-text-fill: #bbc; -fx-font-size: 14;"
  private val comboStyle = "-fx-background-color: #181830; -fx-text-fill: white; -fx-font-size: 14; -fx-padding: 8; -fx-background-radius: 8; -fx-border-color: rgba(255,255,255,0.08); -fx-border-radius: 8; -fx-border-width: 1;"
  // No -fx-control-inner-background here: list cells derive their labels' text colour from it,
  // so defining it inline turned every coloured label in a lobby card white (see appStylesheet).
  // The cell factories paint each cell's background themselves.
  private val listViewCss = "-fx-background-color: #1a1a32; -fx-font-size: 14; -fx-background-radius: 12; -fx-border-color: rgba(255,255,255,0.05); -fx-border-radius: 12; -fx-border-width: 1;"
  private val sectionHeaderStyle = "-fx-text-fill: #99aabb; -fx-font-size: 13; -fx-font-weight: bold;"

  private def addHoverEffect(btn: Button, normalStyle: String, hoverStyle: String): Unit = {
    btn.setStyle(normalStyle)
    btn.setOnMouseEntered(_ => btn.setStyle(hoverStyle))
    btn.setOnMouseExited(_ => {
      btn.setStyle(normalStyle)
      btn.setScaleX(1.0); btn.setScaleY(1.0)
    })
    btn.setOnMousePressed(_ => {
      btn.setScaleX(0.95); btn.setScaleY(0.95)
    })
    btn.setOnMouseReleased(_ => {
      btn.setScaleX(1.0); btn.setScaleY(1.0)
    })
  }

  /** Sound on/off toggle. `AudioManager` is a global, so one button covers the
    * JavaFX screens and the in-game GLFW window alike, and the choice is
    * persisted so it survives a restart. */
  private def createSoundToggleButton(): Button = {
    val btn = new Button()
    def soundLabel: String =
      if (AudioManager.isMuted) Messages.t("Sound: Off") else Messages.t("Sound: On")
    btn.setText(soundLabel)
    addHoverEffect(btn, buttonGhostStyle, buttonGhostHoverStyle)
    btn.setOnAction(_ => {
      AudioManager.toggleMuted()
      btn.setText(soundLabel)
    })
    btn
  }

  private def addFieldFocusEffect(field: TextField): Unit = {
    field.setStyle(fieldStyle)
    field.focusedProperty().addListener((_, _, focused) => {
      field.setStyle(if (focused) fieldFocusStyle else fieldStyle)
    })
  }

  /**
   * A looping menu animation stepped at 12 fps, calling `apply` with the loop's phase
   * (0 to 1). Any change on screen makes JavaFX present the whole window, and on macOS a
   * full-screen window doing that every pulse holds a CPU core at ~60% on a 5K display,
   * just for a gently pulsing label. The slow swings these animations make look the same
   * stepped, and they hold still while nobody is using the window (see UiActivity).
   */
  private def steppedLoop(periodSec: Double, apply: Double => Unit): AnimationTimer = {
    UiActivity.touch()
    val timer = new AnimationTimer {
      private var last = 0L
      override def handle(now: Long): Unit = {
        if (now - last < 83_000_000L || UiActivity.idle) return
        last = now
        apply((now / 1e9 % periodSec) / periodSec)
      }
    }
    timer.start()
    timer
  }

  /** 0 -> 1 -> 0 as `t` runs 0 -> 1: what a Timeline with auto-reverse traces. */
  private def triangle(t: Double): Double = if (t < 0.5) t * 2 else 2 - t * 2

  private def fadeInScene(stage: Stage, root: javafx.scene.Parent): Unit = {
    val overlay = new javafx.scene.shape.Rectangle()
    overlay.setFill(Color.BLACK)
    overlay.setMouseTransparent(true)
    val stack = new StackPane(root, overlay)
    overlay.widthProperty().bind(stack.widthProperty())
    overlay.heightProperty().bind(stack.heightProperty())
    val scene = newScene(stack)
    scene.setFill(Color.BLACK)
    stage.setScene(scene)
    val fade = new javafx.animation.FadeTransition(javafx.util.Duration.millis(300), overlay)
    fade.setFromValue(1.0)
    fade.setToValue(0.0)
    fade.setOnFinished(_ => stack.getChildren.remove(overlay))
    fade.play()
  }

  private def styleCombo(combo: ComboBox[String]): Unit = {
    combo.setStyle(comboStyle)
    val cellFactory = new Callback[ListView[String], ListCell[String]] {
      override def call(param: ListView[String]): ListCell[String] = new ListCell[String] {
        override def updateItem(item: String, empty: Boolean): Unit = {
          super.updateItem(item, empty)
          if (empty || item == null) {
            setText(null)
            setStyle("-fx-background-color: #181830;")
          } else {
            setText(item)
            setStyle("-fx-background-color: #181830; -fx-text-fill: #dde; -fx-font-size: 14; -fx-padding: 8 12;")
          }
          if (isSelected) setStyle("-fx-background-color: #3a6eaf; -fx-text-fill: white; -fx-font-size: 14; -fx-padding: 8 12; -fx-background-radius: 6;")
        }
      }
    }
    combo.setCellFactory(cellFactory)
    combo.setButtonCell(cellFactory.call(null))
  }

  private def createAccentLine(): Region = {
    val line = new Region()
    line.setMinHeight(2)
    line.setMaxHeight(2)
    line.setMaxWidth(60)
    line.setStyle("-fx-background-color: linear-gradient(to right, transparent, #4a9eff, transparent); -fx-background-radius: 1;")
    line
  }

  private def createSeparator(): Region = {
    val sep = new Region()
    sep.setMinHeight(1)
    sep.setMaxHeight(1)
    sep.setStyle("-fx-background-color: linear-gradient(to right, transparent, rgba(255,255,255,0.08), transparent);")
    sep
  }

  private def renderMapPreview(canvas: Canvas, mapIndex: Int): Unit = {
    val gc = canvas.getGraphicsContext2D
    val canvasW = canvas.getWidth
    val canvasH = canvas.getHeight
    gc.setFill(Color.web("#111124"))
    gc.fillRect(0, 0, canvasW, canvasH)
    try {
      val filename = WorldRegistry.getFilename(mapIndex)
      val world = WorldLoader.load("worlds/" + filename)
      val scale = Math.min(canvasW / world.width, canvasH / world.height)
      val offsetX = (canvasW - world.width * scale) / 2
      val offsetY = (canvasH - world.height * scale) / 2
      for (y <- 0 until world.height) {
        for (x <- 0 until world.width) {
          val tile = world.getTile(x, y)
          val argb = tile.color
          val r = ((argb >> 16) & 0xFF) / 255.0
          val g = ((argb >> 8) & 0xFF) / 255.0
          val b = (argb & 0xFF) / 255.0
          gc.setFill(Color.color(r, g, b))
          gc.fillRect(offsetX + x * scale, offsetY + y * scale, Math.ceil(scale), Math.ceil(scale))
        }
      }
      // Draw spawn points as small white markers
      gc.setFill(Color.color(1, 1, 1, 0.6))
      for (spawn <- world.spawnPoints) {
        val sx = offsetX + spawn.getX * scale
        val sy = offsetY + spawn.getY * scale
        gc.fillOval(sx - 2, sy - 2, 4, 4)
      }
      // Draw map name and size
      gc.setFill(Color.color(1, 1, 1, 0.4))
      gc.setFont(Font.font("Exo 2", 10))
      gc.fillText(s"${world.width}x${world.height}", 4, canvasH - 4)
    } catch {
      case _: Exception =>
        gc.setFill(Color.web("#556677"))
        gc.setFont(Font.font("Exo 2", 12))
        gc.fillText("Preview unavailable", canvasW / 2 - 50, canvasH / 2)
    }
  }

  override def start(primaryStage: Stage): Unit = {
    // Load bundled fonts for JavaFX: Exo 2 for Latin UI, Noto Sans SC/KR so CJK
    // UI text renders (JavaFX resolves the family names registered here).
    Seq("/fonts/Exo2-Bold.ttf", "/fonts/NotoSansSC-i18n.ttf", "/fonts/NotoSansKR-i18n.ttf").foreach { p =>
      val s = getClass.getResourceAsStream(p)
      if (s != null) { Font.loadFont(s, 16); s.close() }
    }

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

  private def showWelcomeScreen(stage: Stage, notice: String = ""): Unit = {
    switchScreen()
    val root = new VBox(0)
    root.setAlignment(Pos.CENTER)
    root.setStyle(darkBg)

    // Title section with dramatic presentation and animated glow
    val titleBox = new VBox(8)
    titleBox.setAlignment(Pos.CENTER)
    titleBox.setPadding(new Insets(48, 0, 28, 0))

    val title = new Label("Grid Game")
    title.setFont(Font.font("Exo 2", FontWeight.BOLD, 56))
    title.setTextFill(Color.WHITE)
    title.setStyle("-fx-effect: dropshadow(gaussian, rgba(74, 158, 255, 0.6), 32, 0, 0, 0);")
    // Subtle breathing glow animation on title: 0.92 -> 1.0 -> 0.92 opacity over 4s
    val titleGlow = steppedLoop(4.0, t => title.setOpacity(0.92 + 0.08 * triangle(t)))

    // Wider accent line with gradient
    val accentLine = new Region()
    accentLine.setMinHeight(2)
    accentLine.setMaxHeight(2)
    accentLine.setMaxWidth(120)
    accentLine.setStyle("-fx-background-color: linear-gradient(to right, transparent, #4a9eff, #7b61ff, #4a9eff, transparent); -fx-background-radius: 1;")

    val subtitle = new Label(Messages.t("Multiplayer Arena"))
    subtitle.setFont(Font.font("Exo 2", FontWeight.NORMAL, 15))
    subtitle.setTextFill(Color.web("#8899aa"))

    val versionLabel = new Label("v1.0")
    versionLabel.setFont(Font.font("Exo 2", FontWeight.NORMAL, 11))
    versionLabel.setTextFill(Color.web("#556677"))

    titleBox.getChildren.addAll(title, accentLine, subtitle, versionLabel)

    // Card container for the form
    val card = new VBox(16)
    card.setAlignment(Pos.CENTER)
    card.setPadding(new Insets(28, 36, 28, 36))
    card.setMaxWidth(440)
    card.setStyle(cardBg)

    val modeLabel = new Label(Messages.t("Login"))
    modeLabel.setFont(Font.font("Exo 2", FontWeight.BOLD, 20))
    modeLabel.setTextFill(Color.web("#4a9eff"))

    val usernameLabel = new Label(Messages.t("USERNAME"))
    usernameLabel.setStyle(sectionHeaderStyle)

    val usernameField = new TextField()
    usernameField.setPromptText(Messages.t("Enter username"))
    usernameField.setMaxWidth(Double.MaxValue)
    addFieldFocusEffect(usernameField)

    val passwordLabel = new Label(Messages.t("PASSWORD"))
    passwordLabel.setStyle(sectionHeaderStyle)

    val passwordField = new PasswordField()
    passwordField.setPromptText(Messages.t("Enter password"))
    passwordField.setMaxWidth(Double.MaxValue)
    passwordField.setStyle(fieldStyle)
    passwordField.focusedProperty().addListener((_, _, focused) => {
      passwordField.setStyle(if (focused) fieldFocusStyle else fieldStyle)
    })

    val confirmLabel = new Label(Messages.t("CONFIRM PASSWORD"))
    confirmLabel.setStyle(sectionHeaderStyle)

    val confirmField = new PasswordField()
    confirmField.setPromptText(Messages.t("Confirm password"))
    confirmField.setMaxWidth(Double.MaxValue)
    confirmField.setStyle(fieldStyle)
    confirmField.focusedProperty().addListener((_, _, focused) => {
      confirmField.setStyle(if (focused) fieldFocusStyle else fieldStyle)
    })

    val confirmBox = new VBox(4, confirmLabel, confirmField)
    confirmBox.setVisible(false)
    confirmBox.setManaged(false)

    // Server section with gradient separator
    val serverSep = createSeparator()

    val serverLabel = new Label(Messages.t("SERVER"))
    serverLabel.setStyle(sectionHeaderStyle)

    val hostField = new TextField()
    hostField.setPromptText("localhost")
    hostField.setMaxWidth(Double.MaxValue)
    addFieldFocusEffect(hostField)

    val portField = new TextField()
    portField.setPromptText("25565")
    portField.setMaxWidth(Double.MaxValue)
    addFieldFocusEffect(portField)

    val serverRow = new HBox(8)
    serverRow.setMaxWidth(Double.MaxValue)
    HBox.setHgrow(hostField, Priority.ALWAYS)
    portField.setMaxWidth(90)
    portField.setPrefWidth(90)
    serverRow.getChildren.addAll(hostField, portField)

    val actionButton = new Button(Messages.t("Login"))
    addHoverEffect(actionButton, buttonStyle, buttonHoverStyle)
    actionButton.setDefaultButton(true)
    actionButton.setMaxWidth(Double.MaxValue)

    val toggleLink = new Button(Messages.t("Don't have an account? Sign Up"))
    toggleLink.setStyle("-fx-background-color: transparent; -fx-text-fill: #7788aa; -fx-cursor: hand; -fx-font-size: 13; -fx-padding: 4 0 0 0;")
    toggleLink.setOnMouseEntered(_ => toggleLink.setStyle("-fx-background-color: transparent; -fx-text-fill: #4a9eff; -fx-cursor: hand; -fx-font-size: 13; -fx-padding: 4 0 0 0;"))
    toggleLink.setOnMouseExited(_ => toggleLink.setStyle("-fx-background-color: transparent; -fx-text-fill: #7788aa; -fx-cursor: hand; -fx-font-size: 13; -fx-padding: 4 0 0 0;"))

    var isSignupMode = false

    toggleLink.setOnAction(_ => {
      isSignupMode = !isSignupMode
      if (isSignupMode) {
        modeLabel.setText(Messages.t("Sign Up"))
        actionButton.setText(Messages.t("Create Account"))
        toggleLink.setText(Messages.t("Already have an account? Login"))
        confirmBox.setVisible(true)
        confirmBox.setManaged(true)
      } else {
        modeLabel.setText(Messages.t("Login"))
        actionButton.setText(Messages.t("Login"))
        toggleLink.setText(Messages.t("Don't have an account? Sign Up"))
        confirmBox.setVisible(false)
        confirmBox.setManaged(false)
      }
    })

    val statusLabel = new Label(notice)
    statusLabel.setTextFill(Color.web("#e84057"))
    statusLabel.setFont(Font.font("Exo 2", FontWeight.BOLD, 13))
    statusLabel.setWrapText(true)
    statusLabel.setMaxWidth(Double.MaxValue)

    val doAction = () => {
      val username = usernameField.getText.trim
      val password = passwordField.getText

      if (username.isEmpty) {
        statusLabel.setTextFill(Color.web("#e84057"))
        statusLabel.setText(Messages.t("Username is required"))
      } else if (password.isEmpty) {
        statusLabel.setTextFill(Color.web("#e84057"))
        statusLabel.setText(Messages.t("Password is required"))
      } else if (isSignupMode && password != confirmField.getText) {
        statusLabel.setTextFill(Color.web("#e84057"))
        statusLabel.setText(Messages.t("Passwords do not match"))
      } else if (username.length > 20) {
        statusLabel.setTextFill(Color.web("#e84057"))
        statusLabel.setText(Messages.t("Username max 20 characters"))
      } else if (password.length > 20) {
        statusLabel.setTextFill(Color.web("#e84057"))
        statusLabel.setText(Messages.t("Password max 20 characters"))
      } else {
        val host = if (hostField.getText.trim.isEmpty) "localhost" else hostField.getText.trim
        val portText = portField.getText.trim
        val port = if (portText.isEmpty) {
          Constants.SERVER_PORT
        } else {
          try {
            Integer.parseInt(portText)
          } catch {
            case _: NumberFormatException =>
              statusLabel.setTextFill(Color.web("#e84057"))
              statusLabel.setText(Messages.t("Invalid port number"))
              -1
          }
        }
        if (port > 0) {
          actionButton.setDisable(true)
          statusLabel.setTextFill(Color.web("#8899bb"))
          statusLabel.setText(Messages.t("Connecting to {0}...", s"$host:$port"))
          startConnection(stage, host, port, username, password, isSignupMode, statusLabel, actionButton)
        }
      }
    }

    actionButton.setOnAction(_ => doAction())
    passwordField.setOnAction(_ => doAction())

    card.getChildren.addAll(modeLabel,
      new VBox(4, usernameLabel, usernameField),
      new VBox(4, passwordLabel, passwordField),
      confirmBox,
      serverSep, serverLabel, serverRow,
      actionButton, statusLabel)

    // Subtle animated border glow on the card
    val cardGlow = new javafx.animation.Timeline(
      new javafx.animation.KeyFrame(javafx.util.Duration.ZERO,
        new javafx.animation.KeyValue(card.effectProperty(),
          new javafx.scene.effect.DropShadow(javafx.scene.effect.BlurType.GAUSSIAN, Color.web("rgba(74, 158, 255, 0.15)"), 24, 0, 0, 0))),
      new javafx.animation.KeyFrame(javafx.util.Duration.millis(3000),
        new javafx.animation.KeyValue(card.effectProperty(),
          new javafx.scene.effect.DropShadow(javafx.scene.effect.BlurType.GAUSSIAN, Color.web("rgba(74, 158, 255, 0.35)"), 32, 0, 0, 0)))
    )
    cardGlow.setCycleCount(javafx.animation.Animation.INDEFINITE)
    cardGlow.setAutoReverse(true)
    cardGlow.play()
    stopCurrentScreen = () => { titleGlow.stop(); cardGlow.stop() }

    // Language selector — changing it applies the choice globally, persists it,
    // and rebuilds this screen (via Messages.onLocaleChanged) in the new language.
    val langLabel = new Label(Messages.t("Language"))
    langLabel.setStyle(sectionHeaderStyle)
    val langCombo = new ComboBox[Messages.Lang]()
    Messages.supported.foreach(l => langCombo.getItems.add(l))
    langCombo.setConverter(new javafx.util.StringConverter[Messages.Lang] {
      override def toString(l: Messages.Lang): String = if (l == null) "" else l.nativeName
      override def fromString(s: String): Messages.Lang = null
    })
    langCombo.setValue(Messages.currentLang) // set before handler so it doesn't fire
    langCombo.setOnAction(_ => {
      val l = langCombo.getValue
      if (l != null) Messages.setLocale(l.tag)
    })
    val langRow = new HBox(8, langLabel, langCombo, createSoundToggleButton())
    langRow.setAlignment(Pos.CENTER)
    langRow.setPadding(new Insets(12, 0, 0, 0))

    root.getChildren.addAll(langRow, titleBox, card, new Region() { setMinHeight(16) }, toggleLink)

    fadeInScene(stage, root)
    stage.show()
    AudioManager.playMenuMusic()
    // Decode every sound now, on a background thread, so no WAV parsing or disk
    // I/O ever lands mid-match on the render or packet threads.
    AudioManager.preload()
  }

  private def startConnection(stage: Stage, serverHost: String, serverPort: Int,
                              username: String, password: String, isSignup: Boolean,
                              statusLabel: Label, actionButton: Button): Unit = {
    val initialWorld = WorldData.createEmpty(Constants.GRID_SIZE, Constants.GRID_SIZE)

    def newClient(): GameClient = {
      val c = new GameClient(serverHost, serverPort, initialWorld, username)
      c.setWorldFileListener(worldFileName => {
        println(s"ClientMain: World file listener triggered with: '$worldFileName'")
        handleWorldFileFromServer(worldFileName)
      })
      c.authResponseListener = (success: Boolean, assignedUUID: java.util.UUID, message: String) => {
        Platform.runLater(() => {
          if (success) {
            c.completeAuthAndJoin(assignedUUID, username)
            // Stats now, so the ranked screen shows the real rating rather than the default 1000
            c.requestMatchHistory()
            showLobbyBrowser(stage)
          } else {
            statusLabel.setTextFill(Color.web("#e84057"))
            statusLabel.setText(message)
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
              statusLabel.setTextFill(Color.web("#c8aa6e"))
              statusLabel.setText(Messages.t("Retrying connection ({0}/{1})...", shownAttempt.toString, maxRetries.toString))
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
            statusLabel.setTextFill(Color.web("#8899bb"))
            statusLabel.setText(if (isSignup) Messages.t("Creating account...") else Messages.t("Logging in..."))
          })
          client.sendAuthRequest(username, password, isSignup)
        } catch {
          case _: InterruptedException => return
          case e: Exception =>
            if (attempt >= maxRetries) {
              Platform.runLater(() => {
                statusLabel.setTextFill(Color.web("#e84057"))
                statusLabel.setText(Messages.t("Could not connect to the server ({0})", e.getMessage))
                actionButton.setDisable(false)
              })
            }
        }
      }
    }).start()
  }

  private def showLobbyBrowser(stage: Stage, notice: String = ""): Unit = {
    switchScreen()
    val root = new VBox(0)
    root.setStyle(darkBg)

    // Header area
    val headerArea = new VBox(12)
    headerArea.setPadding(new Insets(28, 28, 20, 28))

    val titleBar = new HBox(10)
    titleBar.setAlignment(Pos.CENTER_LEFT)
    val title = new Label(Messages.t("Lobby Browser"))
    title.setFont(Font.font("Exo 2", FontWeight.BOLD, 30))
    title.setTextFill(Color.WHITE)
    title.setStyle("-fx-effect: dropshadow(gaussian, rgba(74, 158, 255, 0.3), 12, 0, 0, 0);")
    val spacer = new Region()
    HBox.setHgrow(spacer, Priority.ALWAYS)
    val profileBtn = new Button(Messages.t("Profile"))
    addHoverEffect(profileBtn, buttonGhostStyle, buttonGhostHoverStyle)
    val leaderboardBtn = new Button(Messages.t("Leaderboard"))
    addHoverEffect(leaderboardBtn, buttonGhostStyle, buttonGhostHoverStyle)
    val practiceBtn = new Button(Messages.t("Practice"))
    addHoverEffect(practiceBtn, buttonGreenStyle, buttonGreenHoverStyle)
    val rankedBtn = new Button(Messages.t("Ranked"))
    addHoverEffect(rankedBtn, buttonGreenStyle, buttonGreenHoverStyle)
    val refreshBtn = new Button(Messages.t("Refresh"))
    addHoverEffect(refreshBtn, buttonGhostStyle, buttonGhostHoverStyle)
    val soundBtn = createSoundToggleButton()
    titleBar.getChildren.addAll(title, spacer, soundBtn, profileBtn, leaderboardBtn, practiceBtn, rankedBtn, refreshBtn)

    val headerSep = createAccentLine()
    headerSep.setMaxWidth(Double.MaxValue)
    headerSep.setStyle("-fx-background-color: linear-gradient(to right, #4a9eff, rgba(74, 158, 255, 0.1)); -fx-background-radius: 1;")

    headerArea.getChildren.addAll(titleBar, headerSep)

    // Two-column content area
    val contentArea = new HBox(20)
    contentArea.setPadding(new Insets(0, 28, 24, 28))
    VBox.setVgrow(contentArea, Priority.ALWAYS)

    // Left column: lobby list + join button (60%)
    val leftColumn = new VBox(12)
    HBox.setHgrow(leftColumn, Priority.ALWAYS)

    val lobbyHeader = new Label(Messages.t("AVAILABLE LOBBIES"))
    lobbyHeader.setStyle(sectionHeaderStyle)

    // Cells render the LobbyInfo they hold. Reading the client's list at the cell's index
    // instead drew blank rows once a refresh had changed that list under the ListView.
    val lobbyListView = new ListView[LobbyInfo]()
    lobbyListView.setStyle(listViewCss)
    lobbyListView.setPrefHeight(400)
    lobbyListView.setPlaceholder(placeholderLabel(Messages.t("No open lobbies yet. Create one!")))
    VBox.setVgrow(lobbyListView, Priority.ALWAYS)

    // The "Waiting" dots pulse forever, so they are tracked to be stopped with the screen.
    // "Waiting" status dots pulse 1.0 -> 0.3 -> 1.0 opacity, together, from one stepped timer
    val pulsingDots = new java.util.HashSet[Label]()
    val dotPulse = steppedLoop(1.6, t => {
      val opacity = 1.0 - 0.7 * triangle(t)
      pulsingDots.forEach(_.setOpacity(opacity))
    })

    val lobbyCellFactory = new Callback[ListView[LobbyInfo], ListCell[LobbyInfo]] {
      override def call(param: ListView[LobbyInfo]): ListCell[LobbyInfo] = new ListCell[LobbyInfo] {
        private var pulseDot: Label = _

        setOnMouseEntered(_ => {
          if (!isEmpty && !isSelected) {
            setStyle(getStyle.replace("-fx-background-color: rgba(255,255,255,0.02)", "-fx-background-color: rgba(74, 158, 255, 0.06)")
              .replace("-fx-background-color: transparent", "-fx-background-color: rgba(74, 158, 255, 0.06)") +
              " -fx-effect: dropshadow(gaussian, rgba(74, 158, 255, 0.12), 12, 0, 0, 0);")
          }
        })
        setOnMouseExited(_ => {
          if (!isEmpty && !isSelected) {
            updateItem(getItem, false)
          }
        })

        override def updateItem(info: LobbyInfo, empty: Boolean): Unit = {
          super.updateItem(info, empty)
          if (pulseDot != null) {
            pulsingDots.remove(pulseDot)
            pulseDot = null
          }
          setText(null)
          if (empty || info == null) {
            setGraphic(null)
            setStyle("-fx-background-color: transparent; -fx-padding: 0;")
          } else {
            val mapName = WorldRegistry.getDisplayName(info.mapIndex)
            val waiting = info.status == 0
            val statusStr = if (waiting) Messages.t("Waiting") else Messages.t("In Game")
            val statusColor = if (waiting) "#2ecc71" else "#e84057"
            val modeStr = if (info.gameMode == 1) Messages.t("Teams {0}v{0}", info.teamSize.toString) else Messages.t("FFA")

            // Lobby name + status
            val nameLabel = new Label(info.name)
            nameLabel.setFont(Font.font("Exo 2", FontWeight.BOLD, 15))
            nameLabel.setTextFill(Color.web("#ccdde8"))
            val statusDot = new Label("●")
            statusDot.setTextFill(Color.web(statusColor))
            statusDot.setFont(Font.font("Exo 2", 10))

            // Animated pulse for "Waiting" status dot
            if (waiting) {
              pulseDot = statusDot
              pulsingDots.add(statusDot)
            }

            val statusText = new Label(s"$statusStr  •  ${Messages.t("{0} min", info.durationMinutes.toString)}")
            statusText.setFont(Font.font("Exo 2", 12))
            statusText.setTextFill(Color.web("#778899"))
            val nameRow = new HBox(6, statusDot, nameLabel)
            nameRow.setAlignment(Pos.CENTER_LEFT)

            val nameCol = new VBox(2, nameRow, statusText)

            val cardSpacer = new Region()
            HBox.setHgrow(cardSpacer, Priority.ALWAYS)

            // Mode badge
            val modeBadge = new Label(modeStr)
            modeBadge.setFont(Font.font("Exo 2", FontWeight.BOLD, 11))
            if (info.gameMode == 1) {
              modeBadge.setTextFill(Color.web("#e84057"))
              modeBadge.setStyle("-fx-background-color: rgba(232, 64, 87, 0.15); -fx-padding: 4 12; -fx-background-radius: 12;")
            } else {
              modeBadge.setTextFill(Color.web("#4a9eff"))
              modeBadge.setStyle("-fx-background-color: rgba(74, 158, 255, 0.15); -fx-padding: 4 12; -fx-background-radius: 12;")
            }

            // Player count badge, red when there's no seat left
            val full = info.playerCount >= info.maxPlayers
            val countBadge = new Label(s"${info.playerCount}/${info.maxPlayers}")
            countBadge.setFont(Font.font("Exo 2", FontWeight.BOLD, 11))
            if (full) {
              countBadge.setTextFill(Color.web("#e84057"))
              countBadge.setStyle("-fx-background-color: rgba(232, 64, 87, 0.15); -fx-padding: 4 12; -fx-background-radius: 12;")
            } else {
              countBadge.setTextFill(Color.web("#2ecc71"))
              countBadge.setStyle("-fx-background-color: rgba(46, 204, 113, 0.15); -fx-padding: 4 12; -fx-background-radius: 12;")
            }

            // Larger mini map preview
            val miniCanvas = new Canvas(56, 56)
            renderMapPreview(miniCanvas, info.mapIndex)
            miniCanvas.setStyle("-fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.4), 6, 0, 0, 2);")
            val mapNameLabel = new Label(mapName)
            mapNameLabel.setFont(Font.font("Exo 2", 10))
            mapNameLabel.setTextFill(Color.web("#778899"))
            mapNameLabel.setAlignment(Pos.CENTER)
            mapNameLabel.setMaxWidth(64)
            val mapCol = new VBox(3, miniCanvas, mapNameLabel)
            mapCol.setAlignment(Pos.CENTER)

            val badgeCol = new VBox(5, modeBadge, countBadge)
            badgeCol.setAlignment(Pos.CENTER_RIGHT)

            val card = new HBox(14, nameCol, cardSpacer, badgeCol, mapCol)
            card.setAlignment(Pos.CENTER_LEFT)
            card.setPadding(new Insets(10, 16, 10, 16))

            setGraphic(card)
            val base = "-fx-padding: 3 4; -fx-background-radius: 12;"
            if (isSelected) {
              setStyle(s"-fx-background-color: rgba(74, 158, 255, 0.12); $base -fx-border-color: #4a9eff; -fx-border-width: 0 0 0 3; -fx-border-radius: 12; -fx-effect: dropshadow(gaussian, rgba(74, 158, 255, 0.2), 12, 0, 0, 0);")
            } else if (getIndex % 2 == 0) {
              setStyle(s"-fx-background-color: rgba(255,255,255,0.02); $base")
            } else {
              setStyle(s"-fx-background-color: transparent; $base")
            }
          }
        }
      }
    }
    lobbyListView.setCellFactory(lobbyCellFactory)

    val joinBtn = new Button(Messages.t("Join Selected"))
    addHoverEffect(joinBtn, buttonStyle, buttonHoverStyle)
    joinBtn.setDisable(true)
    joinBtn.setMaxWidth(Double.MaxValue)

    leftColumn.getChildren.addAll(lobbyHeader, lobbyListView, joinBtn)

    // Right column: create lobby form (40%)
    val rightColumn = new VBox(14)
    rightColumn.setMinWidth(360)
    rightColumn.setPrefWidth(440)

    val createCard = new VBox(14)
    createCard.setPadding(new Insets(20, 24, 20, 24))
    createCard.setStyle(cardBg)

    val createLabel = new Label(Messages.t("CREATE NEW LOBBY"))
    createLabel.setStyle(sectionHeaderStyle)

    val nameField = new TextField()
    nameField.setPromptText(Messages.t("Lobby name"))
    addFieldFocusEffect(nameField)
    // The name travels in a fixed-size field; stop at its limit rather than let the packet
    // cut a character in half.
    nameField.setTextFormatter(new javafx.scene.control.TextFormatter[String]((change: javafx.scene.control.TextFormatter.Change) =>
      if (change.getControlNewText.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= Constants.MAX_LOBBY_NAME_LEN) change else null
    ))

    val mapCombo = new ComboBox[String](FXCollections.observableArrayList(WorldRegistry.displayNames: _*))
    mapCombo.getSelectionModel.select(0)
    mapCombo.setMaxWidth(Double.MaxValue)
    styleCombo(mapCombo)

    val durationCombo = makeDurationCombo(Constants.DEFAULT_GAME_DURATION_MIN)

    val createBtn = new Button(Messages.t("Create Lobby"))
    addHoverEffect(createBtn, buttonGreenStyle, buttonGreenHoverStyle)
    createBtn.setMaxWidth(Double.MaxValue)

    val statusLabel = new Label("")
    statusLabel.setFont(Font.font("Exo 2", 12))
    statusLabel.setWrapText(true)
    statusLabel.setMaxWidth(Double.MaxValue)
    if (notice.nonEmpty) showStatus(statusLabel, notice, error = false)

    // A create or join is in flight until the server answers with JOINED or ACTION_FAILED.
    // The buttons stay disabled meanwhile so a second click can't send a second request.
    var busy = false
    val busyTimeout = new javafx.animation.PauseTransition(javafx.util.Duration.seconds(6))
    def setBusy(b: Boolean): Unit = {
      busy = b
      createBtn.setDisable(b)
      joinBtn.setDisable(b || lobbyListView.getSelectionModel.getSelectedItem == null)
      if (b) busyTimeout.playFromStart() else busyTimeout.stop()
    }
    busyTimeout.setOnFinished(_ => if (busy) {
      setBusy(false)
      showStatus(statusLabel, Messages.t("No response from the server, please try again"), error = true)
    })

    lobbyListView.getSelectionModel.selectedItemProperty().addListener((_, _, selected) => {
      joinBtn.setDisable(busy || selected == null)
    })
    lobbyListView.setOnMouseClicked(e => if (e.getClickCount == 2) joinBtn.fire())

    import scala.jdk.CollectionConverters._
    val updateList = () => {
      val selectedId = Option(lobbyListView.getSelectionModel.getSelectedItem).map(_.lobbyId)
      lobbyListView.getItems.setAll(client.lobbyList.asJava)
      selectedId.flatMap(id => client.lobbyList.find(_.lobbyId == id)).foreach(info => lobbyListView.getSelectionModel.select(info))
    }
    // Show the last listing right away; the refresh below replaces it when it lands.
    updateList()
    client.lobbyListListener = () => Platform.runLater(() => updateList())

    client.lobbyJoinedListener = () => {
      Platform.runLater(() => showLobbyRoom(stage))
    }

    client.lobbyClosedListener = () => {
      Platform.runLater(() => showLobbyBrowser(stage, Messages.t("Lobby was closed by the host")))
    }

    client.lobbyActionFailedListener = reason => Platform.runLater(() => {
      setBusy(false)
      showStatus(statusLabel, lobbyFailureMessage(reason), error = true)
      // Our listing was wrong about that lobby
      if (reason == LobbyFailure.LOBBY_FULL || reason == LobbyFailure.NOT_JOINABLE) client.requestLobbyList()
    })

    // Button actions
    profileBtn.setOnAction(_ => showAccountView(stage))

    leaderboardBtn.setOnAction(_ => showLeaderboard(stage))

    practiceBtn.setOnAction(_ => {
      showPracticeSetup(stage)
    })

    rankedBtn.setOnAction(_ => {
      showRankedQueue(stage)
    })

    refreshBtn.setOnAction(_ => {
      client.requestLobbyList()
    })

    joinBtn.setOnAction(_ => {
      val info = lobbyListView.getSelectionModel.getSelectedItem
      if (info != null) {
        if (info.status != 0) {
          showStatus(statusLabel, Messages.t("Can't join - game already in progress"), error = true)
        } else if (info.playerCount >= info.maxPlayers) {
          showStatus(statusLabel, Messages.t("That lobby is full"), error = true)
        } else {
          showStatus(statusLabel, Messages.t("Joining lobby..."), error = false)
          setBusy(true)
          client.joinLobby(info.lobbyId)
        }
      }
    })

    createBtn.setOnAction(_ => {
      val name = if (nameField.getText.trim.isEmpty) s"${client.playerName}'s Lobby" else nameField.getText.trim
      showStatus(statusLabel, Messages.t("Creating lobby..."), error = false)
      setBusy(true)
      client.createLobby(name, mapCombo.getSelectionModel.getSelectedIndex, selectedDuration(durationCombo))
    })

    val formRow1 = new HBox(10, new Label(Messages.t("Name")) { setStyle(sectionHeaderStyle); setMinWidth(44) }, nameField)
    formRow1.setAlignment(Pos.CENTER_LEFT)
    HBox.setHgrow(nameField, Priority.ALWAYS)
    val formRow2 = new HBox(10, new Label(Messages.t("Map")) { setStyle(sectionHeaderStyle); setMinWidth(44) }, mapCombo)
    formRow2.setAlignment(Pos.CENTER_LEFT)
    HBox.setHgrow(mapCombo, Priority.ALWAYS)

    val mapPreviewCanvas = new Canvas(280, 200)
    val mapPreviewWrapper = new StackPane(mapPreviewCanvas)
    mapPreviewWrapper.setMaxWidth(288)
    mapPreviewWrapper.setMaxHeight(208)
    mapPreviewWrapper.setPadding(new Insets(4))
    mapPreviewWrapper.setStyle("-fx-background-color: #111124; -fx-background-radius: 8; -fx-border-color: rgba(255,255,255,0.08); -fx-border-radius: 8; -fx-border-width: 1;")
    val mapPreviewBox = new VBox(0, mapPreviewWrapper)
    mapPreviewBox.setAlignment(Pos.CENTER)
    renderMapPreview(mapPreviewCanvas, mapCombo.getSelectionModel.getSelectedIndex)
    mapCombo.setOnAction(_ => renderMapPreview(mapPreviewCanvas, mapCombo.getSelectionModel.getSelectedIndex))

    val formRow3 = new HBox(10, new Label(Messages.t("Time")) { setStyle(sectionHeaderStyle); setMinWidth(44) }, durationCombo)
    formRow3.setAlignment(Pos.CENTER_LEFT)
    HBox.setHgrow(durationCombo, Priority.ALWAYS)

    createCard.getChildren.addAll(createLabel, formRow1, formRow2, mapPreviewBox, formRow3, createBtn)

    rightColumn.getChildren.addAll(createCard, statusLabel)

    contentArea.getChildren.addAll(leftColumn, rightColumn)

    root.getChildren.addAll(headerArea, contentArea)

    stopCurrentScreen = () => {
      busyTimeout.stop()
      dotPulse.stop()
      pulsingDots.clear()
    }

    fadeInScene(stage, root)

    // Auto-refresh on show
    client.requestLobbyList()
  }

  private def showLobbyRoom(stage: Stage): Unit = {
    switchScreen()
    import scala.jdk.CollectionConverters._
    val root = new VBox(0)
    root.setAlignment(Pos.TOP_CENTER)
    root.setStyle(darkBg)

    def playersText: String =
      Messages.t("Players: {0}/{1}", client.currentLobbyPlayerCount.toString, client.currentLobbyMaxPlayers.toString)
    def modeText: String =
      if (client.currentLobbyGameMode == 1) Messages.t("Teams ({0}v{0})", client.currentLobbyTeamSize.toString)
      else Messages.t("Free-For-All")

    // Header
    val headerBox = new VBox(6)
    headerBox.setAlignment(Pos.CENTER)
    headerBox.setPadding(new Insets(28, 24, 16, 24))

    val lobbyTitle = new Label(client.currentLobbyName)
    lobbyTitle.setFont(Font.font("Exo 2", FontWeight.BOLD, 28))
    lobbyTitle.setTextFill(Color.WHITE)
    lobbyTitle.setStyle("-fx-effect: dropshadow(gaussian, rgba(74, 158, 255, 0.3), 12, 0, 0, 0);")

    val playersLabel = new Label(playersText)
    playersLabel.setFont(Font.font("Exo 2", FontWeight.BOLD, 16))
    playersLabel.setTextFill(Color.web("#4a9eff"))

    val headerLine = createAccentLine()

    headerBox.getChildren.addAll(lobbyTitle, playersLabel, headerLine)

    // Two-panel content
    val mainContent = new HBox(24)
    mainContent.setPadding(new Insets(0, 28, 24, 28))
    VBox.setVgrow(mainContent, Priority.ALWAYS)

    // Left panel (~40%): info card, map preview, host settings, leave button
    val leftPanel = new VBox(14)
    leftPanel.setMinWidth(360)
    leftPanel.setPrefWidth(420)

    val infoCard = new VBox(14)
    infoCard.setPadding(new Insets(20, 28, 20, 28))
    infoCard.setStyle(cardBg)

    val mapLabel = new Label(Messages.t("Map: {0}", WorldRegistry.getDisplayName(client.currentLobbyMapIndex)))
    mapLabel.setFont(Font.font("Exo 2", 14))
    mapLabel.setTextFill(Color.web("#aabbcc"))

    val durationLabel = new Label(Messages.t("Duration: {0} min", client.currentLobbyDuration.toString))
    durationLabel.setFont(Font.font("Exo 2", 14))
    durationLabel.setTextFill(Color.web("#aabbcc"))

    val waitingLabel = new Label(Messages.t("Waiting for host to start..."))
    waitingLabel.setFont(Font.font("Exo 2", 14))
    waitingLabel.setTextFill(Color.web("#8899aa"))

    // Map preview (enlarged). Loading a map for its preview parses the world file, so it is
    // redrawn only when the map changes, not on every join, leave or character pick.
    val lobbyMapPreviewCanvas = new Canvas(320, 240)
    val lobbyMapPreviewWrapper = new StackPane(lobbyMapPreviewCanvas)
    lobbyMapPreviewWrapper.setMaxWidth(328)
    lobbyMapPreviewWrapper.setMaxHeight(248)
    lobbyMapPreviewWrapper.setPadding(new Insets(4))
    lobbyMapPreviewWrapper.setStyle("-fx-background-color: #111124; -fx-background-radius: 8; -fx-border-color: rgba(255,255,255,0.08); -fx-border-radius: 8; -fx-border-width: 1;")
    val lobbyMapPreviewBox = new VBox(0, lobbyMapPreviewWrapper)
    lobbyMapPreviewBox.setAlignment(Pos.CENTER)
    var previewedMap = client.currentLobbyMapIndex
    renderMapPreview(lobbyMapPreviewCanvas, previewedMap)

    // Roster: who is here, split into the teams the match will deal in Teams mode
    val rosterBox = new VBox(6)
    rosterBox.setPadding(new Insets(8, 12, 8, 12))
    rosterBox.setStyle(cardBgSubtle)

    def memberLabel(member: LobbyMember, color: String): Label = {
      val isLocal = member.id == client.getLocalPlayerId
      val lbl = new Label("  " + (if (isLocal) s"${member.name} ${Messages.t("(you)")}" else member.name))
      lbl.setFont(Font.font("Exo 2", if (isLocal) FontWeight.BOLD else FontWeight.NORMAL, 13))
      lbl.setTextFill(Color.web(color))
      lbl
    }

    def rebuildRoster(): Unit = {
      rosterBox.getChildren.clear()
      if (client.currentLobbyGameMode == 1) {
        val teams = client.previewTeams
        def teamColumn(team: Byte, header: String, headerColor: String, memberColor: String): VBox = {
          val headerLabel = new Label(header)
          headerLabel.setFont(Font.font("Exo 2", FontWeight.BOLD, 14))
          headerLabel.setTextFill(Color.web(headerColor))
          val column = new VBox(3, headerLabel)
          teams.filter(_._2 == team).foreach { case (member, _) => column.getChildren.add(memberLabel(member, memberColor)) }
          HBox.setHgrow(column, Priority.ALWAYS)
          column
        }
        val rosterRow = new HBox(16,
          teamColumn(1, Messages.t("Team 1 (Blue)"), "#4a82ff", "#8899cc"),
          teamColumn(2, Messages.t("Team 2 (Red)"), "#e84057", "#cc8899"))
        rosterRow.setAlignment(Pos.TOP_LEFT)
        rosterBox.getChildren.add(rosterRow)
      } else {
        val header = new Label(Messages.t("PLAYERS"))
        header.setStyle(sectionHeaderStyle)
        rosterBox.getChildren.add(header)
        client.lobbyMembers.asScala.foreach(m => rosterBox.getChildren.add(memberLabel(m, "#ccdde8")))
      }
    }
    rebuildRoster()

    val leaveBtn = new Button(Messages.t("Leave"))
    addHoverEffect(leaveBtn, buttonRedStyle, buttonRedHoverStyle)
    leaveBtn.setMaxWidth(Double.MaxValue)

    val nonHostGameModeLabel = new Label(Messages.t("Mode: {0}", modeText))
    nonHostGameModeLabel.setTextFill(Color.web("#ccdde8"))
    nonHostGameModeLabel.setFont(Font.font("Exo 2", FontWeight.NORMAL, 14))

    // Host-only controls, refreshed from the server's view of the lobby on every update
    var refreshHostControls: () => Unit = () => ()

    if (client.isLobbyHost) {
      waitingLabel.setText(Messages.t("You are the host"))
      waitingLabel.setTextFill(Color.web("#2ecc71"))
      waitingLabel.setFont(Font.font("Exo 2", FontWeight.BOLD, 13))

      val configLabel = new Label(Messages.t("GAME SETTINGS"))
      configLabel.setStyle(sectionHeaderStyle)

      val mapCombo = new ComboBox[String](FXCollections.observableArrayList(WorldRegistry.displayNames: _*))
      mapCombo.getSelectionModel.select(client.currentLobbyMapIndex)
      mapCombo.setMaxWidth(Double.MaxValue)
      styleCombo(mapCombo)

      val durationCombo = makeDurationCombo(client.currentLobbyDuration)

      val gameModeCombo = new ComboBox[String](FXCollections.observableArrayList(Messages.t("Free-For-All"), Messages.t("Teams")))
      gameModeCombo.getSelectionModel.select(if (client.currentLobbyGameMode == 1) 1 else 0)
      gameModeCombo.setMaxWidth(Double.MaxValue)
      styleCombo(gameModeCombo)

      val teamSizes = Seq(2, 3, 4)
      val teamSizeCombo = new ComboBox[String](FXCollections.observableArrayList(teamSizes.map(n => s"${n}v$n"): _*))
      teamSizeCombo.getSelectionModel.select(Math.max(0, teamSizes.indexOf(client.currentLobbyTeamSize)))
      teamSizeCombo.setMaxWidth(Double.MaxValue)
      styleCombo(teamSizeCombo)

      // Set while showing the server's values, so selecting them doesn't send another update
      var syncing = false
      val sendConfigUpdate = () => {
        if (!syncing) {
          val ts = teamSizes(Math.max(0, teamSizeCombo.getSelectionModel.getSelectedIndex))
          client.updateLobbyConfig(mapCombo.getSelectionModel.getSelectedIndex, selectedDuration(durationCombo),
            gameModeCombo.getSelectionModel.getSelectedIndex.toByte, ts)
        }
      }

      val startBtn = new Button(Messages.t("Start Game"))
      addHoverEffect(startBtn, buttonGreenStyle, buttonGreenHoverStyle)
      startBtn.setFont(Font.font("Exo 2", FontWeight.BOLD, 16))
      startBtn.setMaxWidth(Double.MaxValue)
      startBtn.setOnAction(_ => {
        client.startGame()
      })

      val addBotBtn = new Button(Messages.t("Add Bot"))
      addHoverEffect(addBotBtn, buttonStyle, buttonHoverStyle)
      addBotBtn.setMaxWidth(Double.MaxValue)
      addBotBtn.setOnAction(_ => client.addBot())

      val removeBotBtn = new Button(Messages.t("Remove Bot"))
      addHoverEffect(removeBotBtn, buttonRedStyle, buttonRedHoverStyle)
      removeBotBtn.setMaxWidth(Double.MaxValue)
      removeBotBtn.setOnAction(_ => client.removeBot())

      val botRow = new HBox(10, addBotBtn, removeBotBtn)
      botRow.setAlignment(Pos.CENTER)
      HBox.setHgrow(addBotBtn, Priority.ALWAYS)
      HBox.setHgrow(removeBotBtn, Priority.ALWAYS)

      val row1 = new HBox(10, new Label(Messages.t("Map")) { setStyle(sectionHeaderStyle); setMinWidth(44) }, mapCombo)
      row1.setAlignment(Pos.CENTER_LEFT)
      HBox.setHgrow(mapCombo, Priority.ALWAYS)
      val row2 = new HBox(10, new Label(Messages.t("Time")) { setStyle(sectionHeaderStyle); setMinWidth(44) }, durationCombo)
      row2.setAlignment(Pos.CENTER_LEFT)
      HBox.setHgrow(durationCombo, Priority.ALWAYS)

      val row3 = new HBox(10, new Label(Messages.t("Mode")) { setStyle(sectionHeaderStyle); setMinWidth(44) }, gameModeCombo)
      row3.setAlignment(Pos.CENTER_LEFT)
      HBox.setHgrow(gameModeCombo, Priority.ALWAYS)

      val row4 = new HBox(10, new Label(Messages.t("Size")) { setStyle(sectionHeaderStyle); setMinWidth(44) }, teamSizeCombo)
      row4.setAlignment(Pos.CENTER_LEFT)
      HBox.setHgrow(teamSizeCombo, Priority.ALWAYS)
      def showTeamSize(isTeams: Boolean): Unit = { row4.setVisible(isTeams); row4.setManaged(isTeams) }

      mapCombo.setOnAction(_ => {
        sendConfigUpdate()
        previewedMap = mapCombo.getSelectionModel.getSelectedIndex
        renderMapPreview(lobbyMapPreviewCanvas, previewedMap)
      })
      durationCombo.setOnAction(_ => sendConfigUpdate())
      gameModeCombo.setOnAction(_ => {
        showTeamSize(gameModeCombo.getSelectionModel.getSelectedIndex == 1)
        sendConfigUpdate()
      })
      teamSizeCombo.setOnAction(_ => sendConfigUpdate())

      refreshHostControls = () => {
        syncing = true
        try {
          // The server can settle on something other than what was asked (a team size that
          // fits everyone here, or FFA when there are more humans than Teams holds).
          gameModeCombo.getSelectionModel.select(if (client.currentLobbyGameMode == 1) 1 else 0)
          teamSizeCombo.getSelectionModel.select(Math.max(0, teamSizes.indexOf(client.currentLobbyTeamSize)))
          showTeamSize(client.currentLobbyGameMode == 1)
        } finally syncing = false
        addBotBtn.setDisable(client.currentLobbyPlayerCount >= client.currentLobbyMaxPlayers)
        removeBotBtn.setDisable(!client.lobbyMembers.asScala.exists(m => TeamAssignment.isBot(m.id)))
      }
      refreshHostControls()

      infoCard.getChildren.addAll(waitingLabel, createSeparator(), configLabel, row1, lobbyMapPreviewBox, row2, row3, row4, botRow, rosterBox, startBtn)
    } else {
      infoCard.getChildren.addAll(mapLabel, durationLabel, nonHostGameModeLabel, rosterBox, lobbyMapPreviewBox, createSeparator(), waitingLabel)
    }

    // Chat panel
    val chatBox = new VBox(6)
    chatBox.setPadding(new Insets(12, 14, 12, 14))
    chatBox.setStyle(cardBg)
    VBox.setVgrow(chatBox, Priority.ALWAYS)

    val chatHeader = new Label(Messages.t("CHAT"))
    chatHeader.setStyle(sectionHeaderStyle)

    val chatMessagesBox = new VBox(3)
    chatMessagesBox.setPadding(new Insets(4))

    val chatScroll = new ScrollPane(chatMessagesBox)
    chatScroll.setFitToWidth(true)
    chatScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER)
    chatScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED)
    chatScroll.setStyle("-fx-background-color: #181830; -fx-border-color: rgba(255,255,255,0.05); -fx-border-radius: 6; -fx-border-width: 1;")
    chatScroll.setPrefHeight(180)
    VBox.setVgrow(chatScroll, Priority.ALWAYS)

    val chatInput = new TextField()
    chatInput.setPromptText(Messages.t("Type a message..."))
    chatInput.setStyle(fieldStyle)
    chatInput.setOnAction(_ => {
      val text = chatInput.getText.trim
      if (text.nonEmpty) {
        client.sendChatMessage(text, com.gridgame.common.protocol.ChatScope.LOBBY)
        chatInput.clear()
      }
    })

    chatBox.getChildren.addAll(chatHeader, chatScroll, chatInput)

    leftPanel.getChildren.addAll(infoCard, chatBox, leaveBtn)

    // Right panel (~60%): character selection grid
    val rightPanel = new VBox(0)
    HBox.setHgrow(rightPanel, Priority.ALWAYS)

    val charPanel = new CharacterSelectionPanel(
      () => client.selectedCharacterId,
      id => client.selectCharacter(id)
    )
    val charSection = charPanel.createPanel()
    rightPanel.getChildren.add(charSection)
    stopCurrentScreen = () => charPanel.stop()

    leaveBtn.setOnAction(_ => {
      client.leaveLobby()
      showLobbyBrowser(stage)
    })

    mainContent.getChildren.addAll(leftPanel, rightPanel)

    root.getChildren.addAll(headerBox, mainContent)

    val scrollPane = new ScrollPane(root)
    scrollPane.setFitToWidth(true)
    scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER)
    scrollPane.setStyle("-fx-background-color: #1a1a2e; -fx-border-color: transparent;")
    ViewportCache.disable(scrollPane) // the character panel inside animates

    // Wire up listeners
    def refreshRoom(): Unit = {
      playersLabel.setText(playersText)
      mapLabel.setText(Messages.t("Map: {0}", WorldRegistry.getDisplayName(client.currentLobbyMapIndex)))
      durationLabel.setText(Messages.t("Duration: {0} min", client.currentLobbyDuration.toString))
      if (client.currentLobbyMapIndex != previewedMap) {
        previewedMap = client.currentLobbyMapIndex
        renderMapPreview(lobbyMapPreviewCanvas, previewedMap)
      }
      nonHostGameModeLabel.setText(Messages.t("Mode: {0}", modeText))
      rebuildRoster()
      refreshHostControls()
    }
    client.lobbyUpdatedListener = () => Platform.runLater(() => refreshRoom())

    def renderChat(): Unit = {
      chatMessagesBox.getChildren.clear()
      client.chatMessages.asScala.foreach { entry =>
        val sender = entry(1).asInstanceOf[String]
        val msg = entry(2).asInstanceOf[String]
        val lbl = new Label()
        if (sender.isEmpty) {
          lbl.setText(msg)
          lbl.setTextFill(Color.web("#778899"))
          lbl.setFont(Font.font("Exo 2", javafx.scene.text.FontPosture.ITALIC, 12))
        } else {
          lbl.setText(sender + ": " + msg)
          if (sender == client.playerName) {
            lbl.setTextFill(Color.web("#4a9eff"))
          } else {
            lbl.setTextFill(Color.web("#ccdde8"))
          }
          lbl.setFont(Font.font("Exo 2", 12))
        }
        lbl.setWrapText(true)
        chatMessagesBox.getChildren.add(lbl)
      }
      chatScroll.setVvalue(1.0)
    }
    client.chatMessageListener = () => Platform.runLater(() => renderChat())

    client.gameStartingListener = () => {
      Platform.runLater(() => showGameScene(stage))
    }

    client.lobbyClosedListener = () => {
      Platform.runLater(() => showLobbyBrowser(stage, Messages.t("Lobby was closed by the host")))
    }

    // The roster a joiner is sent lands right behind JOINED, before these listeners were
    // set, so draw once from the current state now that they are.
    refreshRoom()
    renderChat()

    val scene = newScene(scrollPane)
    stage.setScene(scene)
  }

  private def showPracticeSetup(stage: Stage): Unit = {
    switchScreen()
    val root = new VBox(0)
    root.setAlignment(Pos.TOP_CENTER)
    root.setStyle(darkBg)

    // Header
    val headerBox = new VBox(8)
    headerBox.setAlignment(Pos.CENTER)
    headerBox.setPadding(new Insets(28, 24, 16, 24))

    val titleLabel = new Label(Messages.t("Target Practice"))
    titleLabel.setFont(Font.font("Exo 2", FontWeight.BOLD, 28))
    titleLabel.setTextFill(Color.WHITE)
    titleLabel.setStyle("-fx-effect: dropshadow(gaussian, rgba(61, 219, 128, 0.3), 12, 0, 0, 0);")

    val headerLine = new Region()
    headerLine.setMinHeight(2)
    headerLine.setMaxHeight(2)
    headerLine.setMaxWidth(60)
    headerLine.setStyle("-fx-background-color: linear-gradient(to right, transparent, #3ddb80, transparent); -fx-background-radius: 1;")

    headerBox.getChildren.addAll(titleLabel, headerLine)

    // Two-panel content
    val mainContent = new HBox(24)
    mainContent.setPadding(new Insets(0, 28, 24, 28))
    VBox.setVgrow(mainContent, Priority.ALWAYS)

    // Left panel: info + buttons
    val leftPanel = new VBox(14)
    leftPanel.setMinWidth(300)
    leftPanel.setPrefWidth(340)

    val infoCard = new VBox(14)
    infoCard.setPadding(new Insets(20, 28, 20, 28))
    infoCard.setStyle(cardBg)
    infoCard.setAlignment(Pos.CENTER)

    val descLabel = new Label(Messages.t("HOW IT WORKS"))
    descLabel.setStyle(sectionHeaderStyle)

    val descText = new Label(Messages.t("Shoot passive bots with\nsatisfying feedback. Bots\nrespawn quickly so you can\npractice non-stop."))
    descText.setTextFill(Color.web("#8899aa"))
    descText.setFont(Font.font("Exo 2", 14))
    descText.setWrapText(true)

    val exitHint = new Label(Messages.t("Press Esc twice in game to end the session."))
    exitHint.setTextFill(Color.web("#667788"))
    exitHint.setFont(Font.font("Exo 2", 12))
    exitHint.setWrapText(true)

    val sep = createSeparator()

    val startBtn = new Button(Messages.t("Start Practice"))
    addHoverEffect(startBtn, buttonGreenStyle, buttonGreenHoverStyle)
    startBtn.setFont(Font.font("Exo 2", FontWeight.BOLD, 16))
    startBtn.setMaxWidth(Double.MaxValue)

    val statusLabel = new Label("")
    statusLabel.setFont(Font.font("Exo 2", 12))
    statusLabel.setWrapText(true)

    val backBtn = new Button(Messages.t("Back"))
    addHoverEffect(backBtn, buttonGhostStyle, buttonGhostHoverStyle)
    backBtn.setMaxWidth(Double.MaxValue)

    infoCard.getChildren.addAll(descLabel, descText, exitHint, sep, startBtn, statusLabel)
    leftPanel.getChildren.addAll(infoCard, backBtn)

    // Right panel: character selection
    val rightPanel = new VBox(0)
    HBox.setHgrow(rightPanel, Priority.ALWAYS)

    val charPanel = new CharacterSelectionPanel(
      () => client.selectedCharacterId,
      id => { client.selectedCharacterId = id }
    )
    val charSection = charPanel.createPanel()
    rightPanel.getChildren.add(charSection)
    stopCurrentScreen = () => charPanel.stop()

    mainContent.getChildren.addAll(leftPanel, rightPanel)
    root.getChildren.addAll(headerBox, mainContent)

    val scrollPane = new ScrollPane(root)
    scrollPane.setFitToWidth(true)
    scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER)
    scrollPane.setStyle("-fx-background-color: #1a1a2e; -fx-border-color: transparent;")
    ViewportCache.disable(scrollPane) // the character panel inside animates

    backBtn.setOnAction(_ => {
      client.isPracticeMode = false
      showLobbyBrowser(stage)
    })

    startBtn.setOnAction(_ => {
      startBtn.setDisable(true)
      showStatus(statusLabel, Messages.t("Starting practice..."), error = false)
      // Set before sending: the reply can beat the next line on a local server. The session
      // starts straight away, so the JOINED it opens with must not flash up the lobby room.
      client.lobbyJoinedListener = () => ()
      client.gameStartingListener = () => {
        Platform.runLater(() => showGameScene(stage))
      }
      client.lobbyActionFailedListener = reason => Platform.runLater(() => {
        client.isPracticeMode = false
        startBtn.setDisable(false)
        showStatus(statusLabel, lobbyFailureMessage(reason), error = true)
      })
      client.startPractice()
    })

    val scene = newScene(scrollPane)
    stage.setScene(scene)
  }

  private def showRankedQueue(stage: Stage): Unit = {
    switchScreen()
    val root = new VBox(0)
    root.setAlignment(Pos.TOP_CENTER)
    root.setStyle(darkBg)

    // Header
    val headerBox = new VBox(8)
    headerBox.setAlignment(Pos.CENTER)
    headerBox.setPadding(new Insets(28, 24, 16, 24))

    val titleRow = new HBox(16)
    titleRow.setAlignment(Pos.CENTER)

    val queueTitle = new Label(Messages.t("Ranked Queue"))
    queueTitle.setFont(Font.font("Exo 2", FontWeight.BOLD, 28))
    queueTitle.setTextFill(Color.WHITE)
    queueTitle.setStyle("-fx-effect: dropshadow(gaussian, rgba(255, 215, 0, 0.3), 12, 0, 0, 0);")

    // ELO badge
    val eloLabel = new Label(s"ELO: ${client.rankedElo}")
    eloLabel.setFont(Font.font("Exo 2", FontWeight.BOLD, 18))
    eloLabel.setTextFill(Color.web("#ffd700"))
    eloLabel.setStyle("-fx-background-color: rgba(255, 215, 0, 0.08); -fx-padding: 6 20; -fx-background-radius: 20; -fx-border-color: rgba(255, 215, 0, 0.2); -fx-border-radius: 20; -fx-border-width: 1;")

    titleRow.getChildren.addAll(queueTitle, eloLabel)

    val headerLine = new Region()
    headerLine.setMinHeight(2)
    headerLine.setMaxHeight(2)
    headerLine.setMaxWidth(60)
    headerLine.setStyle("-fx-background-color: linear-gradient(to right, transparent, #ffd700, transparent); -fx-background-radius: 1;")

    headerBox.getChildren.addAll(titleRow, headerLine)

    // Two-panel content
    val mainContent = new HBox(24)
    mainContent.setPadding(new Insets(0, 28, 24, 28))
    VBox.setVgrow(mainContent, Priority.ALWAYS)

    // Left panel (~40%): mode selection, find match, queue status, back button
    val leftPanel = new VBox(14)
    leftPanel.setMinWidth(360)
    leftPanel.setPrefWidth(420)

    val modeCard = new VBox(14)
    modeCard.setPadding(new Insets(20, 28, 20, 28))
    modeCard.setStyle(cardBg)
    modeCard.setAlignment(Pos.CENTER)

    val modeLabel = new Label(Messages.t("SELECT MODE"))
    modeLabel.setStyle(sectionHeaderStyle)

    var selectedMode: Byte = RankedQueueMode.FFA

    val modeButtonActiveStyle = "-fx-background-color: linear-gradient(to bottom, #5aadff, #3a8eef); -fx-text-fill: white; -fx-font-size: 14; -fx-font-weight: bold; -fx-padding: 14 28; -fx-background-radius: 10; -fx-cursor: hand; -fx-effect: dropshadow(gaussian, rgba(74, 158, 255, 0.5), 16, 0, 0, 3); -fx-border-color: #6db8ff; -fx-border-radius: 10; -fx-border-width: 2;"
    val modeButtonInactiveStyle = "-fx-background-color: rgba(255,255,255,0.06); -fx-text-fill: #8899aa; -fx-font-size: 14; -fx-font-weight: bold; -fx-padding: 14 28; -fx-background-radius: 10; -fx-cursor: hand; -fx-border-color: rgba(255,255,255,0.1); -fx-border-radius: 10; -fx-border-width: 1;"
    val modeButtonInactiveHoverStyle = "-fx-background-color: rgba(255,255,255,0.12); -fx-text-fill: #ccdde8; -fx-font-size: 14; -fx-font-weight: bold; -fx-padding: 14 28; -fx-background-radius: 10; -fx-cursor: hand; -fx-border-color: rgba(255,255,255,0.2); -fx-border-radius: 10; -fx-border-width: 1;"

    val ffaBtn = new Button(Messages.t("FFA (8 Players)"))
    ffaBtn.setStyle(modeButtonActiveStyle)
    ffaBtn.setMaxWidth(Double.MaxValue)

    val duelBtn = new Button(Messages.t("1v1 Duel"))
    duelBtn.setStyle(modeButtonInactiveStyle)
    duelBtn.setMaxWidth(Double.MaxValue)

    val teamsBtn = new Button(Messages.t("Teams (3v3)"))
    teamsBtn.setStyle(modeButtonInactiveStyle)
    teamsBtn.setMaxWidth(Double.MaxValue)

    val allModeButtons = Seq(ffaBtn, duelBtn, teamsBtn)

    def updateModeButtons(): Unit = {
      allModeButtons.foreach { btn =>
        val isActive = (btn == ffaBtn && selectedMode == RankedQueueMode.FFA) ||
          (btn == duelBtn && selectedMode == RankedQueueMode.DUEL) ||
          (btn == teamsBtn && selectedMode == RankedQueueMode.TEAMS)
        if (isActive) {
          btn.setStyle(modeButtonActiveStyle)
          btn.setOnMouseEntered(null)
          btn.setOnMouseExited(null)
        } else {
          btn.setStyle(modeButtonInactiveStyle)
          btn.setOnMouseEntered(_ => btn.setStyle(modeButtonInactiveHoverStyle))
          btn.setOnMouseExited(_ => btn.setStyle(modeButtonInactiveStyle))
        }
      }
    }
    updateModeButtons()

    ffaBtn.setOnAction(_ => {
      selectedMode = RankedQueueMode.FFA
      updateModeButtons()
    })

    duelBtn.setOnAction(_ => {
      selectedMode = RankedQueueMode.DUEL
      updateModeButtons()
    })

    teamsBtn.setOnAction(_ => {
      selectedMode = RankedQueueMode.TEAMS
      updateModeButtons()
    })

    // Stack mode buttons vertically for left panel
    val modeButtonsCol = new VBox(10, ffaBtn, duelBtn, teamsBtn)

    // Queue status elements (initially hidden)
    val queueSizeLabel = new Label(Messages.t("Players in queue: {0}", "1"))
    queueSizeLabel.setFont(Font.font("Exo 2", 14))
    queueSizeLabel.setTextFill(Color.web("#aabbcc"))
    queueSizeLabel.setVisible(false)
    queueSizeLabel.setManaged(false)

    val waitTimeLabel = new Label(Messages.t("Wait time: {0}s", "0"))
    waitTimeLabel.setFont(Font.font("Exo 2", 14))
    waitTimeLabel.setTextFill(Color.web("#aabbcc"))
    waitTimeLabel.setVisible(false)
    waitTimeLabel.setManaged(false)

    val searchingLabel = new Label("")
    searchingLabel.setFont(Font.font("Exo 2", FontWeight.BOLD, 14))
    searchingLabel.setTextFill(Color.web("#4a9eff"))
    searchingLabel.setVisible(false)
    searchingLabel.setManaged(false)

    val searchSeparator = createSeparator()
    searchSeparator.setVisible(false)
    searchSeparator.setManaged(false)

    // Animated dots for searching
    var dotTick = 0
    var isSearching = false
    val dotTimer = new AnimationTimer {
      private var lastUpdate = 0L
      override def handle(now: Long): Unit = {
        if (isSearching && now - lastUpdate > 500_000_000L) {
          lastUpdate = now
          dotTick = (dotTick + 1) % 4
          val dots = "." * dotTick
          val modeText = if (selectedMode == RankedQueueMode.DUEL) Messages.t("Searching for opponent")
            else if (selectedMode == RankedQueueMode.TEAMS) Messages.t("Searching for teammates")
            else Messages.t("Searching for match")
          searchingLabel.setText(s"$modeText$dots")
        }
      }
    }
    dotTimer.start()

    // Find Match button
    val findMatchBtn = new Button(Messages.t("Find Match"))
    addHoverEffect(findMatchBtn, buttonGreenStyle, buttonGreenHoverStyle)
    findMatchBtn.setFont(Font.font("Exo 2", FontWeight.BOLD, 15))
    findMatchBtn.setMaxWidth(Double.MaxValue)

    modeCard.getChildren.addAll(modeLabel, modeButtonsCol, findMatchBtn, searchingLabel, searchSeparator, queueSizeLabel, waitTimeLabel)

    // Character selection panel (declared before leaveBtn so it can reference it)
    val charPanel = new CharacterSelectionPanel(
      () => client.selectedCharacterId,
      id => client.changeRankedCharacter(id)
    )
    val charSection = charPanel.createPanel()
    stopCurrentScreen = () => { charPanel.stop(); dotTimer.stop() }

    // Back / Leave queue button
    val leaveBtn = new Button(Messages.t("Back"))
    addHoverEffect(leaveBtn, buttonRedStyle, buttonRedHoverStyle)
    leaveBtn.setMaxWidth(Double.MaxValue)
    leaveBtn.setOnAction(_ => {
      if (isSearching) {
        client.leaveRankedQueue()
      }
      showLobbyBrowser(stage)
    })

    // Find Match button action
    findMatchBtn.setOnAction(_ => {
      isSearching = true
      client.queueRanked(selectedMode)

      // Disable mode buttons and Find Match
      ffaBtn.setDisable(true)
      duelBtn.setDisable(true)
      teamsBtn.setDisable(true)
      findMatchBtn.setVisible(false)
      findMatchBtn.setManaged(false)

      // Show searching UI
      searchingLabel.setVisible(true)
      searchingLabel.setManaged(true)
      searchSeparator.setVisible(true)
      searchSeparator.setManaged(true)
      queueSizeLabel.setVisible(true)
      queueSizeLabel.setManaged(true)
      waitTimeLabel.setVisible(true)
      waitTimeLabel.setManaged(true)

      leaveBtn.setText(Messages.t("Leave Queue"))
    })

    leftPanel.getChildren.addAll(modeCard, leaveBtn)

    // Right panel (~60%): character selection grid
    val rightPanel = new VBox(0)
    HBox.setHgrow(rightPanel, Priority.ALWAYS)
    rightPanel.getChildren.add(charSection)

    mainContent.getChildren.addAll(leftPanel, rightPanel)

    root.getChildren.addAll(headerBox, mainContent)

    val scrollPane = new ScrollPane(root)
    scrollPane.setFitToWidth(true)
    scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER)
    scrollPane.setStyle("-fx-background-color: #1a1a2e; -fx-border-color: transparent;")
    ViewportCache.disable(scrollPane) // the character panel inside animates

    // Wire up queue status listener
    client.rankedQueueListener = () => {
      Platform.runLater(() => {
        queueSizeLabel.setText(Messages.t("Players in queue: {0}", client.rankedQueueSize.toString))
        waitTimeLabel.setText(Messages.t("Wait time: {0}s", client.rankedQueueWaitTime.toString))
        eloLabel.setText(s"ELO: ${client.rankedElo}")
      })
    }

    // The rating is only known once stats arrive; until then the badge showed the default 1000.
    client.matchHistoryListener = () => {
      Platform.runLater(() => eloLabel.setText(s"ELO: ${client.rankedElo}"))
    }
    client.requestMatchHistory()

    // Wire up match found listener - transition to game
    client.rankedMatchFoundListener = () => {
      Platform.runLater(() => {
        // Match found - game starting packets will follow
      })
    }

    // Wire up game starting listener to transition to game scene
    client.gameStartingListener = () => {
      Platform.runLater(() => showGameScene(stage))
    }

    client.lobbyClosedListener = () => {
      Platform.runLater(() => showLobbyBrowser(stage))
    }

    val scene = newScene(scrollPane)
    stage.setScene(scene)
  }

  private def showLeaderboard(stage: Stage): Unit = {
    switchScreen()
    val root = new VBox(16)
    root.setPadding(new Insets(24))
    root.setStyle(darkBg)

    val titleBar = new HBox(12)
    titleBar.setAlignment(Pos.CENTER_LEFT)
    val title = new Label(Messages.t("Leaderboard"))
    title.setFont(Font.font("Exo 2", FontWeight.BOLD, 28))
    title.setTextFill(Color.WHITE)
    val spacer = new Region()
    HBox.setHgrow(spacer, Priority.ALWAYS)
    val backBtn = new Button(Messages.t("Back"))
    addHoverEffect(backBtn, buttonStyle, buttonHoverStyle)
    backBtn.setOnAction(_ => showLobbyBrowser(stage))
    titleBar.getChildren.addAll(title, spacer, backBtn)

    val leaderboardListView = new ListView[LeaderboardEntry]()
    leaderboardListView.setStyle(listViewCss)
    VBox.setVgrow(leaderboardListView, Priority.ALWAYS)

    val leaderboardCellFactory = new Callback[ListView[LeaderboardEntry], ListCell[LeaderboardEntry]] {
      override def call(param: ListView[LeaderboardEntry]): ListCell[LeaderboardEntry] = new ListCell[LeaderboardEntry] {
        override def updateItem(entry: LeaderboardEntry, empty: Boolean): Unit = {
          super.updateItem(entry, empty)
          if (empty || entry == null) {
            setText(null)
            setStyle("-fx-background-color: #242440;")
          } else {
            setText(Messages.t("#{0}  |  {1}  |  ELO: {2}  |  {3}W  |  {4} Matches",
              entry.rank.toString, entry.username, entry.elo.toString, entry.wins.toString, entry.matchesPlayed.toString))
            val base = "-fx-font-size: 15; -fx-padding: 10 12; -fx-font-weight: bold;"
            val idx = getIndex
            if (entry.username.equalsIgnoreCase(client.playerName)) {
              setStyle(s"-fx-background-color: rgba(74, 158, 255, 0.15); -fx-text-fill: #4a9eff; $base")
            } else if (entry.rank == 1) {
              setStyle(s"-fx-background-color: #242440; -fx-text-fill: #ffd700; $base")
            } else if (entry.rank == 2) {
              setStyle(s"-fx-background-color: #2a2a48; -fx-text-fill: #c0c0c0; $base")
            } else if (entry.rank == 3) {
              setStyle(s"-fx-background-color: #242440; -fx-text-fill: #cd7f32; $base")
            } else if (idx % 2 == 0) {
              setStyle(s"-fx-background-color: #242440; -fx-text-fill: #dde; $base")
            } else {
              setStyle(s"-fx-background-color: #2a2a48; -fx-text-fill: #dde; $base")
            }
          }
        }
      }
    }
    leaderboardListView.setCellFactory(leaderboardCellFactory)

    val loadingLabel = new Label("")
    loadingLabel.setTextFill(Color.web("#8899bb"))
    loadingLabel.setFont(Font.font("Exo 2", 13))

    root.getChildren.addAll(titleBar, leaderboardListView, loadingLabel)

    val scene = newScene(root)
    stage.setScene(scene)

    // Show what we have now and replace it when the response lands. The server rate-limits
    // this query, so reopening the screen within a few seconds gets the cached board.
    import scala.jdk.CollectionConverters._
    def render(): Unit = {
      leaderboardListView.getItems.setAll(client.leaderboard.asJava)
      loadingLabel.setText(
        if (!client.leaderboardLoaded) Messages.t("Loading...")
        else if (client.leaderboard.isEmpty) Messages.t("No players found")
        else "")
    }
    render()
    client.leaderboardListener = () => Platform.runLater(() => render())
    client.requestLeaderboard()
  }

  private def showAccountView(stage: Stage): Unit = {
    switchScreen()
    val root = new VBox(0)
    root.setStyle(darkBg)

    // Header
    val headerArea = new VBox(12)
    headerArea.setPadding(new Insets(28, 28, 20, 28))

    val titleBar = new HBox(12)
    titleBar.setAlignment(Pos.CENTER_LEFT)
    val title = new Label(Messages.t("Profile"))
    title.setFont(Font.font("Exo 2", FontWeight.BOLD, 30))
    title.setTextFill(Color.WHITE)
    title.setStyle("-fx-effect: dropshadow(gaussian, rgba(74, 158, 255, 0.3), 12, 0, 0, 0);")

    val playerTag = new Label(client.playerName)
    playerTag.setFont(Font.font("Exo 2", FontWeight.BOLD, 14))
    playerTag.setTextFill(Color.web("#4a9eff"))
    playerTag.setStyle("-fx-background-color: rgba(74, 158, 255, 0.1); -fx-padding: 4 12; -fx-background-radius: 12; -fx-border-color: rgba(74, 158, 255, 0.2); -fx-border-radius: 12; -fx-border-width: 1;")

    val spacer = new Region()
    HBox.setHgrow(spacer, Priority.ALWAYS)
    val backBtn = new Button(Messages.t("Back"))
    addHoverEffect(backBtn, buttonGhostStyle, buttonGhostHoverStyle)
    backBtn.setOnAction(_ => showLobbyBrowser(stage))
    titleBar.getChildren.addAll(title, playerTag, spacer, backBtn)

    val headerSep = createAccentLine()
    headerSep.setMaxWidth(Double.MaxValue)
    headerSep.setStyle("-fx-background-color: linear-gradient(to right, #4a9eff, rgba(74, 158, 255, 0.1)); -fx-background-radius: 1;")

    headerArea.getChildren.addAll(titleBar, headerSep)

    // Content
    val contentArea = new VBox(16)
    contentArea.setPadding(new Insets(0, 28, 24, 28))
    VBox.setVgrow(contentArea, Priority.ALWAYS)

    // Stats card
    val statsCard = new VBox(12)
    statsCard.setPadding(new Insets(20, 24, 20, 24))
    statsCard.setStyle(cardBg)

    val statsTitle = new Label(Messages.t("ALL-TIME STATS"))
    statsTitle.setStyle(sectionHeaderStyle)

    val statsRow = new HBox(12)
    statsRow.setAlignment(Pos.CENTER)
    statsRow.setPadding(new Insets(8, 0, 4, 0))

    val (killsBox, killsValue) = createStatBox(Messages.t("Kills"), "-", "#2ecc71")
    val (deathsBox, deathsValue) = createStatBox(Messages.t("Deaths"), "-", "#e84057")
    val (matchesBox, matchesValue) = createStatBox(Messages.t("Matches"), "-", "#4a9eff")
    val (winsBox, winsValue) = createStatBox(Messages.t("Wins"), "-", "#ffd700")
    val (eloBox, eloValue) = createStatBox("ELO", "-", "#e88d3f")
    Seq(killsBox, deathsBox, matchesBox, winsBox, eloBox).foreach(b => HBox.setHgrow(b, Priority.ALWAYS))
    statsRow.getChildren.addAll(killsBox, deathsBox, matchesBox, winsBox, eloBox)

    val practiceNote = new Label(Messages.t("Practice sessions don't count toward stats."))
    practiceNote.setTextFill(Color.web("#667788"))
    practiceNote.setFont(Font.font("Exo 2", 11))

    statsCard.getChildren.addAll(statsTitle, statsRow, practiceNote)

    // Match history label
    val historyTitle = new Label(Messages.t("RECENT MATCHES"))
    historyTitle.setStyle(sectionHeaderStyle)
    historyTitle.setPadding(new Insets(4, 0, 0, 0))

    // Match history list
    val historyListView = new ListView[MatchHistoryEntry]()
    historyListView.setStyle(listViewCss)
    VBox.setVgrow(historyListView, Priority.ALWAYS)

    val dateFormat = new java.text.SimpleDateFormat("MMM d, HH:mm", Messages.currentLocale)
    def matchTypeLabel(matchType: Int): String = matchType match {
      case 1 => Messages.t("Casual Teams")
      case 2 => Messages.t("Ranked FFA")
      case 3 => Messages.t("Ranked Duel")
      case 4 => Messages.t("Ranked Teams")
      case 5 => Messages.t("Practice")
      case _ => Messages.t("Casual FFA")
    }
    def isTeamMatch(e: MatchHistoryEntry): Boolean = e.matchType == 1 || e.matchType == 4

    val historyCellFactory = new Callback[ListView[MatchHistoryEntry], ListCell[MatchHistoryEntry]] {
      override def call(param: ListView[MatchHistoryEntry]): ListCell[MatchHistoryEntry] = new ListCell[MatchHistoryEntry] {
        override def updateItem(e: MatchHistoryEntry, empty: Boolean): Unit = {
          super.updateItem(e, empty)
          if (empty || e == null) {
            setText(null)
            setStyle("-fx-background-color: transparent;")
          } else {
            // A Teams rank is the team's place, so it reads as a result, not "#1/6"
            val result =
              if (isTeamMatch(e)) { if (e.rank == 1) Messages.t("Victory") else Messages.t("Defeat") }
              else s"#${e.rank}/${e.totalPlayers}"
            val date = dateFormat.format(new java.util.Date(e.playedAt * 1000L))
            setText(Messages.t("{0}  |  {1}  |  {2}  |  {3}K/{4}D  |  {5}min  |  {6}",
              matchTypeLabel(e.matchType), result, WorldRegistry.getDisplayName(e.mapIndex),
              e.kills.toString, e.deaths.toString, e.durationMinutes.toString, date))
            val base = "-fx-text-fill: #ccdde8; -fx-font-size: 14; -fx-padding: 12 16; -fx-background-radius: 6;"
            if (e.rank == 1 && e.matchType != 5) {
              setStyle(s"-fx-background-color: rgba(255, 215, 0, 0.06); $base -fx-border-color: rgba(255, 215, 0, 0.15); -fx-border-width: 0 0 0 3; -fx-border-radius: 6;")
            } else if (getIndex % 2 == 0) {
              setStyle(s"-fx-background-color: rgba(255,255,255,0.02); $base")
            } else {
              setStyle(s"-fx-background-color: transparent; $base")
            }
          }
        }
      }
    }
    historyListView.setCellFactory(historyCellFactory)

    val loadingLabel = new Label("")
    loadingLabel.setTextFill(Color.web("#8899aa"))
    loadingLabel.setFont(Font.font("Exo 2", 13))

    contentArea.getChildren.addAll(statsCard, historyTitle, historyListView, loadingLabel)

    root.getChildren.addAll(headerArea, contentArea)

    val scene = newScene(root)
    stage.setScene(scene)

    // Show what we have now and replace it when the response lands. The server rate-limits
    // this query, so reopening the screen within a few seconds gets the cached profile.
    import scala.jdk.CollectionConverters._
    def render(): Unit = {
      if (client.matchHistoryLoaded) {
        killsValue.setText(client.totalKillsStat.toString)
        deathsValue.setText(client.totalDeathsStat.toString)
        matchesValue.setText(client.matchesPlayedStat.toString)
        winsValue.setText(client.winsStat.toString)
        eloValue.setText(client.rankedElo.toString)
      }
      historyListView.getItems.setAll(client.matchHistory.asJava)
      loadingLabel.setText(
        if (!client.matchHistoryLoaded) Messages.t("Loading...")
        else if (client.matchHistory.isEmpty) Messages.t("No matches played yet")
        else "")
    }
    render()
    client.matchHistoryListener = () => Platform.runLater(() => render())
    client.requestMatchHistory()
  }

  /** A labelled stat tile; returns the tile and its value label, to update later. */
  private def createStatBox(label: String, value: String, accentColor: String): (StackPane, Label) = {
    val container = new StackPane()
    container.setStyle(s"-fx-background-color: rgba(255,255,255,0.03); -fx-background-radius: 12; -fx-border-color: rgba(255,255,255,0.05); -fx-border-radius: 12; -fx-border-width: 1;")
    container.setPadding(new Insets(12, 8, 12, 8))
    container.setMinWidth(80)

    val inner = new VBox(4)
    inner.setAlignment(Pos.CENTER)
    val nameLabel = new Label(label)
    nameLabel.setStyle(sectionHeaderStyle)
    val valueLabel = new Label(value)
    valueLabel.setFont(Font.font("Exo 2", FontWeight.BOLD, 22))
    valueLabel.setTextFill(Color.web(accentColor))
    inner.getChildren.addAll(nameLabel, valueLabel)

    container.getChildren.add(inner)
    (container, valueLabel)
  }

  private def showGameScene(stage: Stage): Unit = {
    switchScreen()
    primaryStageRef = stage

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
        closeGameScene(stage)
        showScoreboard(stage)
      })
    }

    // The server no longer closes a lobby mid-match, but if one closes anyway don't leave
    // the player in a match nothing is driving.
    client.lobbyClosedListener = () => {
      Platform.runLater(() => {
        closeGameScene(stage)
        client.returnToLobbyBrowser()
        showLobbyBrowser(stage, Messages.t("The match was closed"))
      })
    }

    println("Game started!")
  }

  /** Take down the in-game window and bring the JavaFX stage back; a no-op when no match is
    * showing. Run it between frames (from a runLater), never inside one: it destroys the
    * window the frame is drawing to. */
  private def closeGameScene(stage: Stage): Unit = {
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
          closeGameScene(stage)
          client.returnToLobbyBrowser()
          showLobbyBrowser(stage)
        }
      })
      fallback.play()
    } else {
      client.leaveMatch()
      closeGameScene(stage)
      showLobbyBrowser(stage)
    }
  }

  /** The server connection dropped: leave whatever screen, or match, we were on for login. */
  private def handleServerDisconnect(stage: Stage): Unit = {
    // GameClient already posts this onto the FX thread.
    closeGameScene(stage)
    stage.show()
    showWelcomeScreen(stage, Messages.t("Disconnected from the server"))
  }

  private def showScoreboard(stage: Stage): Unit = {
    switchScreen()
    import scala.jdk.CollectionConverters._

    val isPractice = client.isPracticeMode
    val localId = client.getLocalPlayerId
    val entries = client.scoreboard.asScala.toVector
    // Decided by the results rather than by lobby state that can outlive its lobby
    val teamMode = entries.exists(_.teamId != 0)

    val rows: Vector[ScoreEntry] =
      if (teamMode) entries.sortBy(e => (e.rank, e.teamId, -e.kills, e.deaths))
      else entries.sortBy(e => (e.rank, -e.kills, e.deaths))
    val localEntry = entries.find(_.playerId.equals(localId))
    val teamRanks: Map[Int, Int] = entries.groupBy(_.teamId).map { case (team, members) => team -> members.head.rank }
    val teamKills: Map[Int, Int] = entries.groupBy(_.teamId).map { case (team, members) => team -> members.map(_.kills).sum }
    val isDraw = teamMode && teamRanks.size > 1 && teamRanks.values.forall(_ == 1)

    def teamName(team: Int): String = team match {
      case 1 => Messages.t("Team 1 (Blue)")
      case 2 => Messages.t("Team 2 (Red)")
      case n => Messages.t("Team {0}", n.toString)
    }
    def teamColor(team: Int): String = team match {
      case 1 => "#4a82ff"
      case 2 => "#e84057"
      case 3 => "#2ecc71"
      case 4 => "#f1c40f"
      case _ => "#8899aa"
    }
    val teamRowColors = Map(1 -> "rgba(74, 130, 255, 0.12)", 2 -> "rgba(232, 64, 87, 0.12)", 3 -> "rgba(46, 204, 113, 0.12)", 4 -> "rgba(241, 196, 15, 0.12)")

    // Headline: the player's own result, which is what they came to this screen for
    val (titleText, titleColor, titleGlow) =
      if (isPractice) (Messages.t("Practice Complete"), "#ffffff", "rgba(61, 219, 128, 0.4)")
      else localEntry match {
        case Some(_) if isDraw => (Messages.t("Draw"), "#ffffff", "rgba(74, 158, 255, 0.4)")
        case Some(e) if e.rank == 1 => (Messages.t("Victory!"), "#ffd700", "rgba(255, 215, 0, 0.5)")
        case Some(_) if teamMode => (Messages.t("Defeat"), "#ff8090", "rgba(232, 64, 87, 0.45)")
        case _ => (Messages.t("Game Over"), "#ffffff", "rgba(74, 158, 255, 0.4)")
      }
    val subtitleText =
      if (isPractice) Messages.t("Target Practice")
      else if (teamMode) {
        val byPlace = teamKills.toSeq.sortBy { case (team, kills) => (teamRanks(team), team) }
        val score = byPlace.map(_._2.toString).mkString(" – ")
        if (isDraw) Messages.t("Tied at {0}", score)
        else byPlace.headOption.map { case (team, _) => Messages.t("{0} wins {1}", teamName(team), score) }.getOrElse(Messages.t("Final Scoreboard"))
      } else localEntry match {
        case Some(e) => Messages.t("You placed #{0} of {1}", e.rank.toString, entries.size.toString)
        case None => Messages.t("Final Scoreboard")
      }

    val root = new VBox(0)
    root.setAlignment(Pos.TOP_CENTER)
    root.setStyle(darkBg)
    root.setPadding(new Insets(0, 24, 36, 24))
    root.setMinHeight(Region.USE_PREF_SIZE)

    // Title section with glow
    val titleBox = new VBox(6)
    titleBox.setAlignment(Pos.CENTER)
    titleBox.setPadding(new Insets(36, 0, 24, 0))

    val title = new Label(titleText)
    title.setFont(Font.font("Exo 2", FontWeight.BOLD, 40))
    title.setTextFill(Color.web(titleColor))
    title.setStyle(s"-fx-effect: dropshadow(gaussian, $titleGlow, 20, 0, 0, 0);")

    val subtitle = new Label(subtitleText)
    subtitle.setFont(Font.font("Exo 2", 15))
    subtitle.setTextFill(Color.web("#8899aa"))

    titleBox.getChildren.addAll(title, subtitle, createAccentLine())
    root.getChildren.add(titleBox)

    // Ranked ELO delta — only shown when the server pushed a post-match STATS
    // (i.e. the just-ended match was ranked with >=2 humans).
    client.pendingEloChange.foreach { case (oldElo, newElo) =>
      val delta = newElo - oldElo
      val (deltaColor, deltaText) =
        if (delta > 0) ("#2ecc71", s"+$delta")
        else if (delta < 0) ("#e84057", delta.toString)
        else ("#8899aa", "±0")

      val eloBox = new VBox(6)
      eloBox.setAlignment(Pos.CENTER)
      eloBox.setPadding(new Insets(0, 0, 20, 0))

      val eloHeader = new Label(Messages.t("RANKED ELO"))
      eloHeader.setFont(Font.font("Exo 2", FontWeight.BOLD, 11))
      eloHeader.setTextFill(Color.web("#8899aa"))
      eloHeader.setStyle("-fx-letter-spacing: 2px;")

      val eloRow = new HBox(14)
      eloRow.setAlignment(Pos.CENTER)
      val oldLbl = new Label(oldElo.toString)
      oldLbl.setFont(Font.font("Exo 2", FontWeight.BOLD, 24))
      oldLbl.setTextFill(Color.web("#8899aa"))
      val arrow = new Label("→")
      arrow.setFont(Font.font("Exo 2", 22))
      arrow.setTextFill(Color.web("#556677"))
      val newLbl = new Label(newElo.toString)
      newLbl.setFont(Font.font("Exo 2", FontWeight.BOLD, 28))
      newLbl.setTextFill(Color.web("#ffd700"))
      newLbl.setStyle("-fx-effect: dropshadow(gaussian, rgba(255, 215, 0, 0.4), 12, 0, 0, 0);")
      val deltaLbl = new Label(deltaText)
      deltaLbl.setFont(Font.font("Exo 2", FontWeight.BOLD, 22))
      deltaLbl.setTextFill(Color.web(deltaColor))
      eloRow.getChildren.addAll(oldLbl, arrow, newLbl, deltaLbl)

      eloBox.getChildren.addAll(eloHeader, eloRow)
      root.getChildren.add(eloBox)
    }

    // Scoreboard card
    val scoreCard = new VBox(0)
    scoreCard.setMaxWidth(760)
    scoreCard.setStyle(cardBg)

    // Header row
    val header = new HBox(0)
    header.setAlignment(Pos.CENTER_LEFT)
    header.setPadding(new Insets(14, 20, 14, 20))
    header.setStyle("-fx-background-color: rgba(255,255,255,0.03); -fx-background-radius: 16 16 0 0; -fx-border-color: transparent transparent rgba(255,255,255,0.06) transparent; -fx-border-width: 0 0 1 0;")
    val hRank = new Label(Messages.t("RANK"))
    hRank.setMinWidth(80); hRank.setStyle(sectionHeaderStyle)
    val hPlayer = new Label(Messages.t("PLAYER"))
    hPlayer.setMinWidth(240); hPlayer.setMaxWidth(Double.MaxValue); hPlayer.setStyle(sectionHeaderStyle)
    HBox.setHgrow(hPlayer, Priority.ALWAYS)
    val hKills = new Label(Messages.t("KILLS"))
    hKills.setMinWidth(90); hKills.setStyle(sectionHeaderStyle)
    val hDeaths = new Label(Messages.t("DEATHS"))
    hDeaths.setMinWidth(90); hDeaths.setStyle(sectionHeaderStyle)
    header.getChildren.addAll(hRank, hPlayer, hKills, hDeaths)
    scoreCard.getChildren.add(header)

    val rowBorder = "-fx-border-color: transparent transparent rgba(255,255,255,0.04) transparent; -fx-border-width: 0 0 1 0;"
    var lastTeamId = -1
    var placeInTeam = 0
    rows.zipWithIndex.foreach { case (entry, rowIndex) =>
      // Team header: name, total kills, and the result, above each team's rows
      if (teamMode && entry.teamId != lastTeamId) {
        lastTeamId = entry.teamId
        placeInTeam = 0
        val teamLabel = new Label(teamName(entry.teamId))
        teamLabel.setFont(Font.font("Exo 2", FontWeight.BOLD, 15))
        teamLabel.setTextFill(Color.web(teamColor(entry.teamId)))
        val killsTotal = new Label(Messages.t("{0} kills", teamKills(entry.teamId).toString))
        killsTotal.setFont(Font.font("Exo 2", FontWeight.BOLD, 13))
        killsTotal.setTextFill(Color.web("#aabbcc"))
        val teamSpacer = new Region()
        HBox.setHgrow(teamSpacer, Priority.ALWAYS)
        val teamHeaderBox = new HBox(12, teamLabel, killsTotal, teamSpacer)
        teamHeaderBox.setAlignment(Pos.CENTER_LEFT)
        if (!isDraw && teamRanks(entry.teamId) == 1) {
          val winnerBadge = new Label(Messages.t("WINNER"))
          winnerBadge.setFont(Font.font("Exo 2", FontWeight.BOLD, 11))
          winnerBadge.setTextFill(Color.web("#ffd700"))
          winnerBadge.setStyle("-fx-background-color: rgba(255, 215, 0, 0.12); -fx-padding: 3 10; -fx-background-radius: 10;")
          teamHeaderBox.getChildren.add(winnerBadge)
        }
        teamHeaderBox.setPadding(new Insets(12, 20, 6, 20))
        teamHeaderBox.setStyle(s"-fx-background-color: ${teamRowColors.getOrElse(entry.teamId, "transparent")};")
        scoreCard.getChildren.add(teamHeaderBox)
      }
      placeInTeam += 1

      val row = new HBox(0)
      row.setAlignment(Pos.CENTER_LEFT)

      val isLocal = entry.playerId.equals(localId)
      val bottomRadius = if (rowIndex == rows.size - 1) "-fx-background-radius: 0 0 16 16;" else ""
      // Our own row is marked by an accent bar, so in Teams it keeps its team's tint rather
      // than taking a blue that reads as the other team.
      val rowBg =
        if (teamMode) teamRowColors.getOrElse(entry.teamId, "transparent")
        else if (isLocal) "rgba(74, 158, 255, 0.14)"
        else if (rowIndex % 2 == 0) "transparent"
        else "rgba(255,255,255,0.02)"
      if (isLocal) {
        row.setPadding(new Insets(12, 20, 12, 17))
        row.setStyle(s"-fx-background-color: $rowBg; -fx-border-color: transparent transparent rgba(255,255,255,0.04) #4a9eff; -fx-border-width: 0 0 1 3; $bottomRadius")
      } else {
        row.setPadding(new Insets(12, 20, 12, 20))
        row.setStyle(s"-fx-background-color: $rowBg; $rowBorder $bottomRadius")
      }

      // FFA: the finishing place, medal-coloured, shared by exact ties. Teams: the place is
      // the team's (in its header), so rows show each player's standing within their team.
      val rankLabel = new Label(if (teamMode) placeInTeam.toString else s"#${entry.rank}")
      rankLabel.setMinWidth(80)
      if (teamMode) {
        rankLabel.setTextFill(Color.web("#667788"))
        rankLabel.setFont(Font.font("Exo 2", FontWeight.BOLD, 15))
      } else {
        rankLabel.setTextFill(entry.rank match {
          case 1 => Color.web("#ffd700")
          case 2 => Color.web("#c0c0c0")
          case 3 => Color.web("#cd7f32")
          case _ => Color.web("#556677")
        })
        rankLabel.setFont(Font.font("Exo 2", FontWeight.BOLD, if (entry.rank <= 3) 20 else 16))
        if (entry.rank <= 3) {
          rankLabel.setStyle(s"-fx-effect: dropshadow(gaussian, ${if (entry.rank == 1) "rgba(255,215,0,0.4)" else if (entry.rank == 2) "rgba(192,192,192,0.3)" else "rgba(205,127,50,0.3)"}, 8, 0, 0, 0);")
        }
      }

      // Character first, as the match showed everyone; then who was playing it
      val player = if (isLocal) null else client.findPlayer(entry.playerId)
      val characterName =
        if (isLocal) I18n.characterName(client.getSelectedCharacterDef)
        else if (player != null) I18n.characterName(CharacterDef.get(player.getCharacterId))
        else "?"
      val playerName =
        if (isLocal) client.playerName
        else if (player != null) player.getName
        else entry.playerId.toString.substring(0, 8)
      val charLabel = new Label(if (isLocal) s"$characterName ${Messages.t("(you)")}" else characterName)
      charLabel.setTextFill(if (isLocal) Color.web("#4a9eff") else Color.web("#ccdde8"))
      charLabel.setFont(Font.font("Exo 2", FontWeight.BOLD, 15))
      val left = !isLocal && client.playerLeftMatch(entry.playerId)
      val playerLabel = new Label(if (left) s"$playerName  ${Messages.t("(left)")}" else playerName)
      playerLabel.setTextFill(Color.web("#778899"))
      playerLabel.setFont(Font.font("Exo 2", 12))
      val nameCell = new HBox(10, charLabel, playerLabel)
      nameCell.setAlignment(Pos.BASELINE_LEFT)
      nameCell.setMinWidth(240)
      nameCell.setMaxWidth(Double.MaxValue)
      HBox.setHgrow(nameCell, Priority.ALWAYS)

      val killsLabel = new Label(entry.kills.toString)
      killsLabel.setMinWidth(90)
      killsLabel.setTextFill(Color.web("#2ecc71"))
      killsLabel.setFont(Font.font("Exo 2", FontWeight.BOLD, 16))

      val deathsLabel = new Label(entry.deaths.toString)
      deathsLabel.setMinWidth(90)
      deathsLabel.setTextFill(Color.web("#e84057"))
      deathsLabel.setFont(Font.font("Exo 2", FontWeight.BOLD, 15))

      row.getChildren.addAll(rankLabel, nameCell, killsLabel, deathsLabel)
      scoreCard.getChildren.add(row)
    }
    root.getChildren.add(scoreCard)

    // Practice stats row (only shown in practice mode)
    if (isPractice) {
      val practiceStatsRow = new HBox(12)
      practiceStatsRow.setAlignment(Pos.CENTER)
      practiceStatsRow.setPadding(new Insets(12, 20, 12, 20))
      practiceStatsRow.setMaxWidth(760)
      practiceStatsRow.setStyle(cardBgSubtle)
      VBox.setMargin(practiceStatsRow, new Insets(16, 0, 0, 0))

      val accuracy = if (client.practiceShots > 0) (client.practiceHits * 100.0 / client.practiceShots).toInt else 0
      val (bestComboBox, _) = createStatBox(Messages.t("Best Combo"), client.practiceBestCombo.toString, "#ffd700")
      val (accuracyBox, _) = createStatBox(Messages.t("Accuracy"), s"$accuracy%", "#4a9eff")
      val (totalKillsBox, _) = createStatBox(Messages.t("Total Kills"), client.killCount.toString, "#2ecc71")
      HBox.setHgrow(bestComboBox, Priority.ALWAYS)
      HBox.setHgrow(accuracyBox, Priority.ALWAYS)
      HBox.setHgrow(totalKillsBox, Priority.ALWAYS)
      practiceStatsRow.getChildren.addAll(bestComboBox, accuracyBox, totalKillsBox)
      root.getChildren.add(practiceStatsRow)
    }

    val returnBtn = new Button(Messages.t("Return to Lobby"))
    addHoverEffect(returnBtn, buttonStyle, buttonHoverStyle)
    returnBtn.setFont(Font.font("Exo 2", FontWeight.BOLD, 15))
    returnBtn.setOnAction(_ => {
      client.pendingEloChange = None
      client.returnToLobbyBrowser()
      showLobbyBrowser(stage)
    })

    // Buttons sit right under the results; pinned to the bottom edge they were cut off
    val btnBox = new HBox(12)
    btnBox.setAlignment(Pos.CENTER)
    btnBox.setPadding(new Insets(24, 0, 0, 0))

    if (isPractice) {
      val practiceAgainBtn = new Button(Messages.t("Practice Again"))
      addHoverEffect(practiceAgainBtn, buttonGreenStyle, buttonGreenHoverStyle)
      practiceAgainBtn.setFont(Font.font("Exo 2", FontWeight.BOLD, 15))
      practiceAgainBtn.setOnAction(_ => {
        client.returnToLobbyBrowser()
        showPracticeSetup(stage)
      })
      btnBox.getChildren.add(practiceAgainBtn)
    }

    btnBox.getChildren.add(returnBtn)
    root.getChildren.add(btnBox)

    // The whole page scrolls when a big lobby outgrows the window. The card used to sit in a
    // scroll pane of its own, which pinned it to the left edge and the button to the bottom.
    val scrollPane = new ScrollPane(root)
    scrollPane.setFitToWidth(true)
    scrollPane.setFitToHeight(true)
    scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER)
    scrollPane.setStyle("-fx-background-color: #151528; -fx-border-color: transparent;")

    fadeInScene(stage, scrollPane)
  }

  private def handleWorldFileFromServer(worldFileName: String): Unit = {
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

  override def stop(): Unit = {
    if (renderLoop != null) renderLoop.stop()
    if (glRenderer != null) glRenderer.dispose()
    if (glWindow != null) glWindow.destroy()
    if (controllerHandler != null) controllerHandler.cleanup()
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
