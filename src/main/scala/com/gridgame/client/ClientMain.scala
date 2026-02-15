package com.gridgame.client

import com.gridgame.client.input.ControllerHandler
import com.gridgame.client.input.KeyboardHandler
import com.gridgame.client.input.MouseHandler
import com.gridgame.client.ui.GameCanvas
import com.gridgame.client.ui.SpriteGenerator
import com.gridgame.common.Constants
import com.gridgame.common.WorldRegistry
import com.gridgame.common.model.CharacterDef
import com.gridgame.common.model.CharacterId
import com.gridgame.common.model.Direction
import com.gridgame.common.model.WorldData
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
import javafx.scene.control.Label
import javafx.scene.control.ListCell
import javafx.scene.control.ListView
import javafx.scene.control.PasswordField
import javafx.scene.control.TextField
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
  private var canvas: GameCanvas = _
  private var renderLoop: AnimationTimer = _
  private var controllerHandler: ControllerHandler = _

  // -- Enhanced color palette & styles --
  private val darkBg = "-fx-background-color: linear-gradient(to bottom, #1a1a2e 0%, #151528 50%, #111124 100%);"
  private val cardBg = "-fx-background-color: #20203a; -fx-background-radius: 16; -fx-border-color: rgba(255,255,255,0.07); -fx-border-radius: 16; -fx-border-width: 1; -fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.5), 24, 0, 0, 8);"
  private val cardBgSubtle = "-fx-background-color: #1c1c34; -fx-background-radius: 12; -fx-border-color: rgba(255,255,255,0.05); -fx-border-radius: 12; -fx-border-width: 1; -fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.3), 12, 0, 0, 4);"
  private val fieldStyle = "-fx-background-color: #181830; -fx-text-fill: #eef; -fx-font-size: 14; -fx-prompt-text-fill: #556; -fx-padding: 12 14; -fx-background-radius: 8; -fx-border-color: rgba(255,255,255,0.08); -fx-border-radius: 8; -fx-border-width: 1;"
  private val fieldFocusStyle = "-fx-background-color: #181830; -fx-text-fill: #eef; -fx-font-size: 14; -fx-prompt-text-fill: #556; -fx-padding: 12 14; -fx-background-radius: 8; -fx-border-color: #4a9eff; -fx-border-radius: 8; -fx-border-width: 2; -fx-effect: dropshadow(gaussian, rgba(74, 158, 255, 0.3), 16, 0, 0, 0);"
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
  private val listViewCss = "-fx-background-color: #1a1a32; -fx-control-inner-background: #1a1a32; -fx-text-fill: white; -fx-font-size: 14; -fx-background-radius: 12; -fx-border-color: rgba(255,255,255,0.05); -fx-border-radius: 12; -fx-border-width: 1;"
  private val sectionHeaderStyle = "-fx-text-fill: #7788aa; -fx-font-size: 11; -fx-font-weight: bold;"

  private def addHoverEffect(btn: Button, normalStyle: String, hoverStyle: String): Unit = {
    btn.setStyle(normalStyle)
    btn.setOnMouseEntered(_ => btn.setStyle(hoverStyle))
    btn.setOnMouseExited(_ => btn.setStyle(normalStyle))
  }

  private def addFieldFocusEffect(field: TextField): Unit = {
    field.setStyle(fieldStyle)
    field.focusedProperty().addListener((_, _, focused) => {
      field.setStyle(if (focused) fieldFocusStyle else fieldStyle)
    })
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
            setStyle("-fx-background-color: #181830; -fx-text-fill: #dde; -fx-font-size: 13; -fx-padding: 8 12;")
          }
          if (isSelected) setStyle("-fx-background-color: #3a6eaf; -fx-text-fill: white; -fx-font-size: 13; -fx-padding: 8 12; -fx-background-radius: 6;")
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
      gc.setFont(Font.font("System", 10))
      gc.fillText(s"${world.width}x${world.height}", 4, canvasH - 4)
    } catch {
      case _: Exception =>
        gc.setFill(Color.web("#556677"))
        gc.setFont(Font.font("System", 12))
        gc.fillText("Preview unavailable", canvasW / 2 - 50, canvasH / 2)
    }
  }

  override def start(primaryStage: Stage): Unit = {
    primaryStage.setTitle("Grid Game - Multiplayer 2D")
    primaryStage.setResizable(true)
    val bounds = Screen.getPrimary.getVisualBounds
    primaryStage.setX(bounds.getMinX)
    primaryStage.setY(bounds.getMinY)
    primaryStage.setWidth(bounds.getWidth)
    primaryStage.setHeight(bounds.getHeight)

    showWelcomeScreen(primaryStage)
  }

  private def showWelcomeScreen(stage: Stage): Unit = {
    val root = new VBox(0)
    root.setAlignment(Pos.CENTER)
    root.setStyle(darkBg)

    // Title section with glow
    val titleBox = new VBox(6)
    titleBox.setAlignment(Pos.CENTER)
    titleBox.setPadding(new Insets(48, 0, 28, 0))

    val title = new Label("Grid Game")
    title.setFont(Font.font("System", FontWeight.BOLD, 48))
    title.setTextFill(Color.WHITE)
    title.setStyle("-fx-effect: dropshadow(gaussian, rgba(74, 158, 255, 0.5), 24, 0, 0, 0);")

    val accentLine = createAccentLine()

    val subtitle = new Label("Multiplayer Arena")
    subtitle.setFont(Font.font("System", FontWeight.NORMAL, 14))
    subtitle.setTextFill(Color.web("#556677"))

    titleBox.getChildren.addAll(title, accentLine, subtitle)

    // Card container for the form
    val card = new VBox(16)
    card.setAlignment(Pos.CENTER)
    card.setPadding(new Insets(28, 36, 28, 36))
    card.setMaxWidth(360)
    card.setStyle(cardBg)

    val modeLabel = new Label("Login")
    modeLabel.setFont(Font.font("System", FontWeight.BOLD, 20))
    modeLabel.setTextFill(Color.web("#4a9eff"))

    val usernameLabel = new Label("USERNAME")
    usernameLabel.setStyle(sectionHeaderStyle)

    val usernameField = new TextField()
    usernameField.setPromptText("Enter username")
    usernameField.setMaxWidth(290)
    addFieldFocusEffect(usernameField)

    val passwordLabel = new Label("PASSWORD")
    passwordLabel.setStyle(sectionHeaderStyle)

    val passwordField = new PasswordField()
    passwordField.setPromptText("Enter password")
    passwordField.setMaxWidth(290)
    passwordField.setStyle(fieldStyle)
    passwordField.focusedProperty().addListener((_, _, focused) => {
      passwordField.setStyle(if (focused) fieldFocusStyle else fieldStyle)
    })

    val confirmLabel = new Label("CONFIRM PASSWORD")
    confirmLabel.setStyle(sectionHeaderStyle)

    val confirmField = new PasswordField()
    confirmField.setPromptText("Confirm password")
    confirmField.setMaxWidth(290)
    confirmField.setStyle(fieldStyle)
    confirmField.focusedProperty().addListener((_, _, focused) => {
      confirmField.setStyle(if (focused) fieldFocusStyle else fieldStyle)
    })

    val confirmBox = new VBox(4, confirmLabel, confirmField)
    confirmBox.setVisible(false)
    confirmBox.setManaged(false)

    // Server section with gradient separator
    val serverSep = createSeparator()

    val serverLabel = new Label("SERVER")
    serverLabel.setStyle(sectionHeaderStyle)

    val hostField = new TextField()
    hostField.setPromptText("localhost")
    hostField.setMaxWidth(290)
    addFieldFocusEffect(hostField)

    val portField = new TextField()
    portField.setPromptText("25565")
    portField.setMaxWidth(290)
    addFieldFocusEffect(portField)

    val serverRow = new HBox(8)
    serverRow.setMaxWidth(290)
    HBox.setHgrow(hostField, Priority.ALWAYS)
    portField.setMaxWidth(90)
    portField.setPrefWidth(90)
    serverRow.getChildren.addAll(hostField, portField)

    val actionButton = new Button("Login")
    addHoverEffect(actionButton, buttonStyle, buttonHoverStyle)
    actionButton.setDefaultButton(true)
    actionButton.setMaxWidth(290)

    val toggleLink = new Button("Don't have an account? Sign Up")
    toggleLink.setStyle("-fx-background-color: transparent; -fx-text-fill: #556688; -fx-cursor: hand; -fx-font-size: 12; -fx-padding: 4 0 0 0;")
    toggleLink.setOnMouseEntered(_ => toggleLink.setStyle("-fx-background-color: transparent; -fx-text-fill: #4a9eff; -fx-cursor: hand; -fx-font-size: 12; -fx-padding: 4 0 0 0;"))
    toggleLink.setOnMouseExited(_ => toggleLink.setStyle("-fx-background-color: transparent; -fx-text-fill: #556688; -fx-cursor: hand; -fx-font-size: 12; -fx-padding: 4 0 0 0;"))

    var isSignupMode = false

    toggleLink.setOnAction(_ => {
      isSignupMode = !isSignupMode
      if (isSignupMode) {
        modeLabel.setText("Sign Up")
        actionButton.setText("Create Account")
        toggleLink.setText("Already have an account? Login")
        confirmBox.setVisible(true)
        confirmBox.setManaged(true)
      } else {
        modeLabel.setText("Login")
        actionButton.setText("Login")
        toggleLink.setText("Don't have an account? Sign Up")
        confirmBox.setVisible(false)
        confirmBox.setManaged(false)
      }
    })

    val statusLabel = new Label("")
    statusLabel.setTextFill(Color.web("#e84057"))
    statusLabel.setFont(Font.font("System", FontWeight.BOLD, 13))
    statusLabel.setWrapText(true)
    statusLabel.setMaxWidth(290)

    val doAction = () => {
      val username = usernameField.getText.trim
      val password = passwordField.getText

      if (username.isEmpty) {
        statusLabel.setTextFill(Color.web("#e84057"))
        statusLabel.setText("Username is required")
      } else if (password.isEmpty) {
        statusLabel.setTextFill(Color.web("#e84057"))
        statusLabel.setText("Password is required")
      } else if (isSignupMode && password != confirmField.getText) {
        statusLabel.setTextFill(Color.web("#e84057"))
        statusLabel.setText("Passwords do not match")
      } else if (username.length > 20) {
        statusLabel.setTextFill(Color.web("#e84057"))
        statusLabel.setText("Username max 20 characters")
      } else if (password.length > 20) {
        statusLabel.setTextFill(Color.web("#e84057"))
        statusLabel.setText("Password max 20 characters")
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
              statusLabel.setText("Invalid port number")
              -1
          }
        }
        if (port > 0) {
          actionButton.setDisable(true)
          statusLabel.setTextFill(Color.web("#8899bb"))
          statusLabel.setText(s"Connecting to $host:$port...")
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

    root.getChildren.addAll(titleBox, card, new Region() { setMinHeight(16) }, toggleLink)

    val scene = new Scene(root)
    stage.setScene(scene)
    stage.show()
  }

  private def startConnection(stage: Stage, serverHost: String, serverPort: Int,
                              username: String, password: String, isSignup: Boolean,
                              statusLabel: Label, actionButton: Button): Unit = {
    val initialWorld = WorldData.createEmpty(Constants.GRID_SIZE, Constants.GRID_SIZE)

    client = new GameClient(serverHost, serverPort, initialWorld, username)

    client.setWorldFileListener(worldFileName => {
      println(s"ClientMain: World file listener triggered with: '$worldFileName'")
      handleWorldFileFromServer(worldFileName)
    })

    // Set up auth response listener
    client.authResponseListener = (success: Boolean, assignedUUID: java.util.UUID, message: String) => {
      Platform.runLater(() => {
        if (success) {
          client.completeAuthAndJoin(assignedUUID, username)
          client.requestLobbyList()
          showLobbyBrowser(stage)
        } else {
          statusLabel.setTextFill(Color.web("#e84057"))
          statusLabel.setText(message)
          actionButton.setDisable(false)
        }
      })
    }

    // Connect on a background thread so the UI stays responsive
    new Thread(() => {
      try {
        client.connect()
        // Send auth request after connection is established
        client.sendAuthRequest(username, password, isSignup)
        Platform.runLater(() => {
          statusLabel.setTextFill(Color.web("#8899bb"))
          statusLabel.setText(if (isSignup) "Creating account..." else "Logging in...")
        })
      } catch {
        case e: Exception =>
          Platform.runLater(() => {
            statusLabel.setTextFill(Color.web("#e84057"))
            statusLabel.setText(s"Connection failed: ${e.getMessage}")
            actionButton.setDisable(false)
          })
      }
    }).start()
  }

  private def showLobbyBrowser(stage: Stage): Unit = {
    val root = new VBox(0)
    root.setStyle(darkBg)

    // Header area
    val headerArea = new VBox(12)
    headerArea.setPadding(new Insets(28, 28, 20, 28))

    val titleBar = new HBox(10)
    titleBar.setAlignment(Pos.CENTER_LEFT)
    val title = new Label("Lobby Browser")
    title.setFont(Font.font("System", FontWeight.BOLD, 30))
    title.setTextFill(Color.WHITE)
    title.setStyle("-fx-effect: dropshadow(gaussian, rgba(74, 158, 255, 0.3), 12, 0, 0, 0);")
    val spacer = new Region()
    HBox.setHgrow(spacer, Priority.ALWAYS)
    val profileBtn = new Button("Profile")
    addHoverEffect(profileBtn, buttonGhostStyle, buttonGhostHoverStyle)
    val leaderboardBtn = new Button("Leaderboard")
    addHoverEffect(leaderboardBtn, buttonGhostStyle, buttonGhostHoverStyle)
    val rankedBtn = new Button("Ranked")
    addHoverEffect(rankedBtn, buttonGreenStyle, buttonGreenHoverStyle)
    val refreshBtn = new Button("Refresh")
    addHoverEffect(refreshBtn, buttonGhostStyle, buttonGhostHoverStyle)
    titleBar.getChildren.addAll(title, spacer, profileBtn, leaderboardBtn, rankedBtn, refreshBtn)

    val headerSep = createAccentLine()
    headerSep.setMaxWidth(Double.MaxValue)
    headerSep.setStyle("-fx-background-color: linear-gradient(to right, #4a9eff, rgba(74, 158, 255, 0.1)); -fx-background-radius: 1;")

    headerArea.getChildren.addAll(titleBar, headerSep)

    // Content area
    val contentArea = new VBox(16)
    contentArea.setPadding(new Insets(0, 28, 24, 28))
    VBox.setVgrow(contentArea, Priority.ALWAYS)

    // Lobby section label
    val lobbyHeader = new Label("AVAILABLE LOBBIES")
    lobbyHeader.setStyle(sectionHeaderStyle)

    // Lobby list with custom cell factory
    val lobbyListView = new ListView[String]()
    lobbyListView.setStyle(listViewCss)
    lobbyListView.setPrefHeight(220)
    VBox.setVgrow(lobbyListView, Priority.ALWAYS)

    val lobbyCellFactory = new Callback[ListView[String], ListCell[String]] {
      override def call(param: ListView[String]): ListCell[String] = new ListCell[String] {
        override def updateItem(item: String, empty: Boolean): Unit = {
          super.updateItem(item, empty)
          if (empty || item == null) {
            setText(null)
            setStyle("-fx-background-color: transparent;")
          } else {
            setText(item)
            val base = "-fx-text-fill: #ccdde8; -fx-font-size: 13; -fx-padding: 12 16; -fx-background-radius: 8;"
            if (isSelected) {
              setStyle(s"-fx-background-color: rgba(74, 158, 255, 0.15); $base -fx-border-color: #4a9eff; -fx-border-width: 0 0 0 3; -fx-border-radius: 8; -fx-effect: dropshadow(gaussian, rgba(74, 158, 255, 0.15), 8, 0, 0, 0);")
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

    val joinBtn = new Button("Join Selected")
    addHoverEffect(joinBtn, buttonStyle, buttonHoverStyle)
    joinBtn.setDisable(true)
    joinBtn.setMaxWidth(Double.MaxValue)

    lobbyListView.getSelectionModel.selectedIndexProperty().addListener((_, _, newVal) => {
      joinBtn.setDisable(newVal.intValue() < 0)
    })

    // Create lobby form in a card
    val createCard = new VBox(14)
    createCard.setPadding(new Insets(20, 24, 20, 24))
    createCard.setStyle(cardBg)

    val createLabel = new Label("CREATE NEW LOBBY")
    createLabel.setStyle(sectionHeaderStyle)

    val nameField = new TextField()
    nameField.setPromptText("Lobby name")
    addFieldFocusEffect(nameField)

    val mapCombo = new ComboBox[String](FXCollections.observableArrayList(WorldRegistry.displayNames: _*))
    mapCombo.getSelectionModel.select(0)
    mapCombo.setMaxWidth(Double.MaxValue)
    styleCombo(mapCombo)

    val durationCombo = new ComboBox[String](FXCollections.observableArrayList("1 min", "3 min", "5 min", "10 min", "15 min", "20 min"))
    durationCombo.getSelectionModel.select(2) // Default 5 min
    durationCombo.setMaxWidth(Double.MaxValue)
    styleCombo(durationCombo)

    val createBtn = new Button("Create Lobby")
    addHoverEffect(createBtn, buttonGreenStyle, buttonGreenHoverStyle)
    createBtn.setMaxWidth(Double.MaxValue)

    val statusLabel = new Label("")
    statusLabel.setTextFill(Color.web("#8899bb"))
    statusLabel.setFont(Font.font("System", 12))

    // Wire up lobby list listener
    val updateList = () => {
      Platform.runLater(() => {
        val items = new java.util.ArrayList[String]()
        import scala.jdk.CollectionConverters._
        client.lobbyList.asScala.foreach { info =>
          val mapName = WorldRegistry.getDisplayName(info.mapIndex)
          val statusStr = info.status match {
            case 0 => "Waiting"
            case 1 => "In Game"
            case _ => "?"
          }
          val modeStr = if (info.gameMode == 1) s"Teams(${info.teamSize}v${info.teamSize})" else "FFA"
          items.add(s"${info.name}  |  $mapName  |  ${info.playerCount}/${info.maxPlayers}  |  $statusStr  |  ${info.durationMinutes}min  |  $modeStr")
        }
        lobbyListView.setItems(FXCollections.observableArrayList(items))
      })
    }
    client.lobbyListListener = () => updateList()

    // Wire up lobby joined listener
    client.lobbyJoinedListener = () => {
      Platform.runLater(() => showLobbyRoom(stage))
    }

    client.lobbyClosedListener = () => {
      Platform.runLater(() => {
        showLobbyBrowser(stage)
        statusLabel.setText("Lobby was closed by the host")
      })
    }

    // Button actions
    profileBtn.setOnAction(_ => showAccountView(stage))

    leaderboardBtn.setOnAction(_ => showLeaderboard(stage))

    rankedBtn.setOnAction(_ => {
      showRankedQueue(stage)
    })

    refreshBtn.setOnAction(_ => {
      client.requestLobbyList()
    })

    joinBtn.setOnAction(_ => {
      val idx = lobbyListView.getSelectionModel.getSelectedIndex
      if (idx >= 0 && idx < client.lobbyList.size()) {
        val info = client.lobbyList.get(idx)
        if (info.status == 0) {
          client.joinLobby(info.lobbyId)
        } else {
          statusLabel.setText("Can't join - game already in progress")
        }
      }
    })

    createBtn.setOnAction(_ => {
      val name = if (nameField.getText.trim.isEmpty) s"${client.playerName}'s Lobby" else nameField.getText.trim
      val mapIdx = mapCombo.getSelectionModel.getSelectedIndex
      val durStr = durationCombo.getSelectionModel.getSelectedItem
      val duration = durStr.split(" ")(0).toInt
      client.createLobby(name, mapIdx, duration)
    })

    val formRow1 = new HBox(10, new Label("Name") { setStyle(sectionHeaderStyle); setMinWidth(44) }, nameField)
    formRow1.setAlignment(Pos.CENTER_LEFT)
    HBox.setHgrow(nameField, Priority.ALWAYS)
    val formRow2 = new HBox(10, new Label("Map") { setStyle(sectionHeaderStyle); setMinWidth(44) }, mapCombo)
    formRow2.setAlignment(Pos.CENTER_LEFT)
    HBox.setHgrow(mapCombo, Priority.ALWAYS)

    val mapPreviewCanvas = new Canvas(200, 200)
    val mapPreviewWrapper = new StackPane(mapPreviewCanvas)
    mapPreviewWrapper.setMaxWidth(208)
    mapPreviewWrapper.setMaxHeight(208)
    mapPreviewWrapper.setPadding(new Insets(4))
    mapPreviewWrapper.setStyle("-fx-background-color: #111124; -fx-background-radius: 8; -fx-border-color: rgba(255,255,255,0.08); -fx-border-radius: 8; -fx-border-width: 1;")
    val mapPreviewBox = new VBox(0, mapPreviewWrapper)
    mapPreviewBox.setAlignment(Pos.CENTER)
    renderMapPreview(mapPreviewCanvas, mapCombo.getSelectionModel.getSelectedIndex)
    mapCombo.setOnAction(_ => renderMapPreview(mapPreviewCanvas, mapCombo.getSelectionModel.getSelectedIndex))

    val formRow3 = new HBox(10, new Label("Time") { setStyle(sectionHeaderStyle); setMinWidth(44) }, durationCombo)
    formRow3.setAlignment(Pos.CENTER_LEFT)
    HBox.setHgrow(durationCombo, Priority.ALWAYS)

    createCard.getChildren.addAll(createLabel, formRow1, formRow2, mapPreviewBox, formRow3, createBtn)

    contentArea.getChildren.addAll(lobbyHeader, lobbyListView, joinBtn, createSeparator(), createCard, statusLabel)

    root.getChildren.addAll(headerArea, contentArea)

    val scene = new Scene(root)
    stage.setScene(scene)

    // Auto-refresh on show
    client.requestLobbyList()
  }

  private def showLobbyRoom(stage: Stage): Unit = {
    val root = new VBox(0)
    root.setAlignment(Pos.TOP_CENTER)
    root.setStyle(darkBg)

    // Header with glow
    val headerBox = new VBox(6)
    headerBox.setAlignment(Pos.CENTER)
    headerBox.setPadding(new Insets(32, 24, 20, 24))

    val lobbyTitle = new Label(client.currentLobbyName)
    lobbyTitle.setFont(Font.font("System", FontWeight.BOLD, 30))
    lobbyTitle.setTextFill(Color.WHITE)
    lobbyTitle.setStyle("-fx-effect: dropshadow(gaussian, rgba(74, 158, 255, 0.3), 12, 0, 0, 0);")

    val playersLabel = new Label(s"Players: ${client.currentLobbyPlayerCount}/${client.currentLobbyMaxPlayers}")
    playersLabel.setFont(Font.font("System", FontWeight.BOLD, 16))
    playersLabel.setTextFill(Color.web("#4a9eff"))

    val headerLine = createAccentLine()

    headerBox.getChildren.addAll(lobbyTitle, playersLabel, headerLine)

    // Info card
    val infoCard = new VBox(14)
    infoCard.setPadding(new Insets(20, 28, 20, 28))
    infoCard.setMaxWidth(420)
    infoCard.setStyle(cardBg)

    val mapLabel = new Label(s"Map: ${WorldRegistry.getDisplayName(client.currentLobbyMapIndex)}")
    mapLabel.setFont(Font.font("System", 14))
    mapLabel.setTextFill(Color.web("#aabbcc"))

    val durationLabel = new Label(s"Duration: ${client.currentLobbyDuration} min")
    durationLabel.setFont(Font.font("System", 14))
    durationLabel.setTextFill(Color.web("#aabbcc"))

    val waitingLabel = new Label("Waiting for host to start...")
    waitingLabel.setFont(Font.font("System", 13))
    waitingLabel.setTextFill(Color.web("#556677"))

    // Character selection section
    val charSectionLabel = new Label("SELECT CHARACTER")
    charSectionLabel.setStyle(sectionHeaderStyle)

    // Character sprite preview - larger with ring
    val previewSize = 96.0
    val previewCanvas = new Canvas(previewSize, previewSize)
    val previewGc = previewCanvas.getGraphicsContext2D
    var previewAnimTick = 0
    var previewDirIndex = 0
    val previewDirs = Array(Direction.Down, Direction.Left, Direction.Up, Direction.Right)

    val charNameLabel = new Label("")
    charNameLabel.setFont(Font.font("System", FontWeight.BOLD, 17))
    charNameLabel.setTextFill(Color.web("#4a9eff"))

    val charInfoLabel = new Label("")
    charInfoLabel.setFont(Font.font("System", 12))
    charInfoLabel.setTextFill(Color.web("#8899aa"))
    charInfoLabel.setWrapText(true)
    charInfoLabel.setMaxWidth(400)
    charInfoLabel.setStyle("-fx-line-spacing: 2;")

    def drawPreview(): Unit = {
      previewGc.clearRect(0, 0, previewSize, previewSize)
      val charDef = CharacterDef.get(client.selectedCharacterId)
      val dir = previewDirs(previewDirIndex)
      val frame = (previewAnimTick / 10) % 4
      val sprite = SpriteGenerator.getSprite(0, dir, frame, charDef.id.id)
      // Draw ring background
      previewGc.setStroke(Color.web("#3a3a5e"))
      previewGc.setLineWidth(2)
      previewGc.strokeOval(4, 4, previewSize - 8, previewSize - 8)
      previewGc.setFill(Color.web("#1a1a30"))
      previewGc.fillOval(6, 6, previewSize - 12, previewSize - 12)
      // Draw sprite centered, scaled up
      val spriteDisplaySize = 68.0
      val offset = (previewSize - spriteDisplaySize) / 2.0
      previewGc.drawImage(sprite, offset, offset, spriteDisplaySize, spriteDisplaySize)
    }

    val previewTimer = new AnimationTimer {
      override def handle(now: Long): Unit = {
        previewAnimTick += 1
        // Rotate direction every 40 frames
        if (previewAnimTick % 40 == 0) {
          previewDirIndex = (previewDirIndex + 1) % previewDirs.length
        }
        drawPreview()
      }
    }
    previewTimer.start()

    def updateCharacterInfo(): Unit = {
      val charDef = CharacterDef.get(client.selectedCharacterId)
      charNameLabel.setText(charDef.displayName)
      charInfoLabel.setText(s"${charDef.description}\nQ: ${charDef.qAbility.name} - ${charDef.qAbility.description}\nE: ${charDef.eAbility.name} - ${charDef.eAbility.description}")
      previewAnimTick = 0
      previewDirIndex = 0
      drawPreview()
    }

    // Character carousel with left/right arrows
    val charCountLabel = new Label("")
    charCountLabel.setFont(Font.font("System", 11))
    charCountLabel.setTextFill(Color.web("#556677"))

    val arrowButtonStyle = "-fx-background-color: rgba(255,255,255,0.06); -fx-text-fill: #8899aa; -fx-font-size: 18; -fx-font-weight: bold; -fx-padding: 8 16; -fx-background-radius: 10; -fx-cursor: hand; -fx-border-color: rgba(255,255,255,0.08); -fx-border-width: 1; -fx-border-radius: 10;"
    val arrowButtonHoverStyle = "-fx-background-color: rgba(74, 158, 255, 0.15); -fx-text-fill: #4a9eff; -fx-font-size: 18; -fx-font-weight: bold; -fx-padding: 8 16; -fx-background-radius: 10; -fx-cursor: hand; -fx-border-color: rgba(74, 158, 255, 0.3); -fx-border-width: 1; -fx-border-radius: 10;"

    def currentCharIndex: Int = {
      val idx = CharacterDef.all.indexWhere(_.id.id == client.selectedCharacterId)
      if (idx < 0) 0 else idx
    }

    def selectByIndex(idx: Int): Unit = {
      val wrapped = ((idx % CharacterDef.all.size) + CharacterDef.all.size) % CharacterDef.all.size
      val charDef = CharacterDef.all(wrapped)
      client.selectCharacter(charDef.id.id)
      updateCharacterInfo()
      charCountLabel.setText(s"${wrapped + 1} / ${CharacterDef.all.size}")
    }

    val prevBtn = new Button("\u25C0")
    prevBtn.setStyle(arrowButtonStyle)
    prevBtn.setOnMouseEntered(_ => prevBtn.setStyle(arrowButtonHoverStyle))
    prevBtn.setOnMouseExited(_ => prevBtn.setStyle(arrowButtonStyle))
    prevBtn.setOnAction(_ => selectByIndex(currentCharIndex - 1))

    val nextBtn = new Button("\u25B6")
    nextBtn.setStyle(arrowButtonStyle)
    nextBtn.setOnMouseEntered(_ => nextBtn.setStyle(arrowButtonHoverStyle))
    nextBtn.setOnMouseExited(_ => nextBtn.setStyle(arrowButtonStyle))
    nextBtn.setOnAction(_ => selectByIndex(currentCharIndex + 1))

    val carouselBox = new HBox(16, prevBtn, charCountLabel, nextBtn)
    carouselBox.setAlignment(Pos.CENTER)

    updateCharacterInfo()
    charCountLabel.setText(s"${currentCharIndex + 1} / ${CharacterDef.all.size}")

    val previewBox = new VBox(6, previewCanvas, charNameLabel)
    previewBox.setAlignment(Pos.CENTER)

    // Character info in a subtle card
    val charInfoCard = new VBox(8, charInfoLabel)
    charInfoCard.setPadding(new Insets(12, 16, 12, 16))
    charInfoCard.setMaxWidth(420)
    charInfoCard.setStyle(cardBgSubtle)

    val charSection = new VBox(12, charSectionLabel, previewBox, carouselBox, charInfoCard)
    charSection.setAlignment(Pos.CENTER)
    charSection.setPadding(new Insets(0, 24, 0, 24))

    val buttonBox = new HBox(12)
    buttonBox.setAlignment(Pos.CENTER)
    buttonBox.setPadding(new Insets(12, 0, 24, 0))

    val leaveBtn = new Button("Leave")
    addHoverEffect(leaveBtn, buttonRedStyle, buttonRedHoverStyle)

    leaveBtn.setOnAction(_ => {
      previewTimer.stop()
      client.leaveLobby()
      showLobbyBrowser(stage)
    })

    buttonBox.getChildren.add(leaveBtn)

    // Map preview
    val lobbyMapPreviewCanvas = new Canvas(180, 180)
    val lobbyMapPreviewWrapper = new StackPane(lobbyMapPreviewCanvas)
    lobbyMapPreviewWrapper.setMaxWidth(188)
    lobbyMapPreviewWrapper.setMaxHeight(188)
    lobbyMapPreviewWrapper.setPadding(new Insets(4))
    lobbyMapPreviewWrapper.setStyle("-fx-background-color: #111124; -fx-background-radius: 8; -fx-border-color: rgba(255,255,255,0.08); -fx-border-radius: 8; -fx-border-width: 1;")
    val lobbyMapPreviewBox = new VBox(0, lobbyMapPreviewWrapper)
    lobbyMapPreviewBox.setAlignment(Pos.CENTER)
    renderMapPreview(lobbyMapPreviewCanvas, client.currentLobbyMapIndex)

    // Team roster helper (builds/rebuilds team roster content in place)
    val teamRosterBox = new VBox(8)
    teamRosterBox.setId("teamRosterBox")
    teamRosterBox.setPadding(new Insets(8, 12, 8, 12))
    teamRosterBox.setStyle(cardBgSubtle)

    def rebuildTeamRoster(): Unit = {
      teamRosterBox.getChildren.clear()
      if (client.currentLobbyGameMode != 1) {
        teamRosterBox.setVisible(false)
        teamRosterBox.setManaged(false)
        return
      }
      teamRosterBox.setVisible(true)
      teamRosterBox.setManaged(true)

      import scala.jdk.CollectionConverters._
      val members = client.lobbyMembers.asScala.toSeq

      val team1Header = new Label("Team 1 (Blue)")
      team1Header.setFont(Font.font("System", FontWeight.BOLD, 13))
      team1Header.setTextFill(Color.web("#4a82ff"))

      val team2Header = new Label("Team 2 (Red)")
      team2Header.setFont(Font.font("System", FontWeight.BOLD, 13))
      team2Header.setTextFill(Color.web("#e84057"))

      val team1List = new VBox(3)
      val team2List = new VBox(3)

      members.zipWithIndex.foreach { case (arr, idx) =>
        val name = arr(1).asInstanceOf[String]
        val isLocal = arr(0).asInstanceOf[java.util.UUID].equals(client.getLocalPlayerId)
        val displayName = if (isLocal) s"$name (You)" else name
        val lbl = new Label(s"  $displayName")
        lbl.setFont(Font.font("System", 12))
        if (idx % 2 == 0) {
          lbl.setTextFill(Color.web("#8899cc"))
          team1List.getChildren.add(lbl)
        } else {
          lbl.setTextFill(Color.web("#cc8899"))
          team2List.getChildren.add(lbl)
        }
      }

      val col1 = new VBox(4, team1Header, team1List)
      HBox.setHgrow(col1, Priority.ALWAYS)
      val col2 = new VBox(4, team2Header, team2List)
      HBox.setHgrow(col2, Priority.ALWAYS)

      val rosterRow = new HBox(16, col1, col2)
      rosterRow.setAlignment(Pos.CENTER_LEFT)

      teamRosterBox.getChildren.add(rosterRow)
    }
    rebuildTeamRoster()

    // Host-only controls
    if (client.isLobbyHost) {
      waitingLabel.setText("You are the host")
      waitingLabel.setTextFill(Color.web("#2ecc71"))
      waitingLabel.setFont(Font.font("System", FontWeight.BOLD, 13))

      val configLabel = new Label("GAME SETTINGS")
      configLabel.setStyle(sectionHeaderStyle)

      val mapCombo = new ComboBox[String](FXCollections.observableArrayList(WorldRegistry.displayNames: _*))
      mapCombo.getSelectionModel.select(client.currentLobbyMapIndex)
      mapCombo.setMaxWidth(Double.MaxValue)
      styleCombo(mapCombo)

      val durationCombo = new ComboBox[String](FXCollections.observableArrayList("1 min", "3 min", "5 min", "10 min", "15 min", "20 min"))
      val durIdx = client.currentLobbyDuration match {
        case 1 => 0; case 3 => 1; case 5 => 2; case 10 => 3; case 15 => 4; case 20 => 5; case _ => 2
      }
      durationCombo.getSelectionModel.select(durIdx)
      durationCombo.setMaxWidth(Double.MaxValue)
      styleCombo(durationCombo)

      val gameModeCombo = new ComboBox[String](FXCollections.observableArrayList("Free-For-All", "Teams"))
      gameModeCombo.getSelectionModel.select(client.currentLobbyGameMode.toInt)
      gameModeCombo.setMaxWidth(Double.MaxValue)
      styleCombo(gameModeCombo)

      val teamSizeCombo = new ComboBox[String](FXCollections.observableArrayList("2v2", "3v3", "4v4"))
      val tsIdx = client.currentLobbyTeamSize match {
        case 3 => 1; case 4 => 2; case _ => 0
      }
      teamSizeCombo.getSelectionModel.select(tsIdx)
      teamSizeCombo.setMaxWidth(Double.MaxValue)
      styleCombo(teamSizeCombo)
      teamSizeCombo.setVisible(client.currentLobbyGameMode == 1)
      teamSizeCombo.setManaged(client.currentLobbyGameMode == 1)

      val sendConfigUpdate = () => {
        val dur = durationCombo.getSelectionModel.getSelectedItem.split(" ")(0).toInt
        val gm: Byte = gameModeCombo.getSelectionModel.getSelectedIndex.toByte
        val ts = teamSizeCombo.getSelectionModel.getSelectedIndex match {
          case 1 => 3; case 2 => 4; case _ => 2
        }
        client.updateLobbyConfig(mapCombo.getSelectionModel.getSelectedIndex, dur, gm, ts)
      }

      mapCombo.setOnAction(_ => {
        sendConfigUpdate()
        renderMapPreview(lobbyMapPreviewCanvas, mapCombo.getSelectionModel.getSelectedIndex)
      })
      durationCombo.setOnAction(_ => sendConfigUpdate())
      gameModeCombo.setOnAction(_ => {
        val isTeams = gameModeCombo.getSelectionModel.getSelectedIndex == 1
        teamSizeCombo.setVisible(isTeams)
        teamSizeCombo.setManaged(isTeams)
        sendConfigUpdate()
      })
      teamSizeCombo.setOnAction(_ => sendConfigUpdate())

      val startBtn = new Button("Start Game")
      addHoverEffect(startBtn, buttonGreenStyle, buttonGreenHoverStyle)
      startBtn.setFont(Font.font("System", FontWeight.BOLD, 16))
      startBtn.setMaxWidth(Double.MaxValue)
      startBtn.setOnAction(_ => {
        client.startGame()
      })

      val addBotBtn = new Button("Add Bot")
      addHoverEffect(addBotBtn, buttonStyle, buttonHoverStyle)
      addBotBtn.setMaxWidth(Double.MaxValue)
      addBotBtn.setOnAction(_ => client.addBot())

      val removeBotBtn = new Button("Remove Bot")
      addHoverEffect(removeBotBtn, buttonRedStyle, buttonRedHoverStyle)
      removeBotBtn.setMaxWidth(Double.MaxValue)
      removeBotBtn.setOnAction(_ => client.removeBot())

      val botRow = new HBox(10, addBotBtn, removeBotBtn)
      botRow.setAlignment(Pos.CENTER)
      HBox.setHgrow(addBotBtn, Priority.ALWAYS)
      HBox.setHgrow(removeBotBtn, Priority.ALWAYS)

      val row1 = new HBox(10, new Label("Map") { setStyle(sectionHeaderStyle); setMinWidth(44) }, mapCombo)
      row1.setAlignment(Pos.CENTER_LEFT)
      HBox.setHgrow(mapCombo, Priority.ALWAYS)
      val row2 = new HBox(10, new Label("Time") { setStyle(sectionHeaderStyle); setMinWidth(44) }, durationCombo)
      row2.setAlignment(Pos.CENTER_LEFT)
      HBox.setHgrow(durationCombo, Priority.ALWAYS)

      val row3 = new HBox(10, new Label("Mode") { setStyle(sectionHeaderStyle); setMinWidth(44) }, gameModeCombo)
      row3.setAlignment(Pos.CENTER_LEFT)
      HBox.setHgrow(gameModeCombo, Priority.ALWAYS)

      val row4 = new HBox(10, new Label("Size") { setStyle(sectionHeaderStyle); setMinWidth(44) }, teamSizeCombo)
      row4.setAlignment(Pos.CENTER_LEFT)
      HBox.setHgrow(teamSizeCombo, Priority.ALWAYS)

      infoCard.getChildren.addAll(waitingLabel, createSeparator(), configLabel, row1, lobbyMapPreviewBox, row2, row3, row4, botRow, teamRosterBox, startBtn)
      buttonBox.getChildren.add(new Region()) // spacing
    } else {
      val gameModeStr = if (client.currentLobbyGameMode == 1) s"Teams (${client.currentLobbyTeamSize}v${client.currentLobbyTeamSize})" else "Free-For-All"
      val nonHostGameModeLabel = new Label(s"Mode: $gameModeStr")
      nonHostGameModeLabel.setTextFill(Color.web("#ccdde8"))
      nonHostGameModeLabel.setFont(Font.font("System", FontWeight.NORMAL, 14))
      nonHostGameModeLabel.setId("gameModeLabel")
      infoCard.getChildren.addAll(mapLabel, durationLabel, nonHostGameModeLabel, teamRosterBox, lobbyMapPreviewBox, createSeparator(), waitingLabel)
    }

    root.getChildren.addAll(headerBox, infoCard, new Region() { setMinHeight(16) }, charSection, new Region() { setMinHeight(4) }, buttonBox)

    // Wire up listeners
    client.lobbyUpdatedListener = () => {
      Platform.runLater(() => {
        playersLabel.setText(s"Players: ${client.currentLobbyPlayerCount}/${client.currentLobbyMaxPlayers}")
        mapLabel.setText(s"Map: ${WorldRegistry.getDisplayName(client.currentLobbyMapIndex)}")
        durationLabel.setText(s"Duration: ${client.currentLobbyDuration} min")
        renderMapPreview(lobbyMapPreviewCanvas, client.currentLobbyMapIndex)
        // Update game mode label for non-hosts
        val gml = infoCard.lookup("#gameModeLabel")
        if (gml != null && gml.isInstanceOf[Label]) {
          val gmStr = if (client.currentLobbyGameMode == 1) s"Teams (${client.currentLobbyTeamSize}v${client.currentLobbyTeamSize})" else "Free-For-All"
          gml.asInstanceOf[Label].setText(s"Mode: $gmStr")
        }
        // Rebuild team roster
        rebuildTeamRoster()
      })
    }

    client.gameStartingListener = () => {
      Platform.runLater(() => {
        previewTimer.stop()
        showGameScene(stage)
      })
    }

    client.lobbyClosedListener = () => {
      Platform.runLater(() => {
        previewTimer.stop()
        showLobbyBrowser(stage)
      })
    }

    val scene = new Scene(root)
    stage.setScene(scene)
  }

  private def showRankedQueue(stage: Stage): Unit = {
    val root = new VBox(0)
    root.setAlignment(Pos.TOP_CENTER)
    root.setStyle(darkBg)

    // Header with glow
    val headerBox = new VBox(8)
    headerBox.setAlignment(Pos.CENTER)
    headerBox.setPadding(new Insets(32, 24, 20, 24))

    val queueTitle = new Label("Ranked Queue")
    queueTitle.setFont(Font.font("System", FontWeight.BOLD, 30))
    queueTitle.setTextFill(Color.WHITE)
    queueTitle.setStyle("-fx-effect: dropshadow(gaussian, rgba(255, 215, 0, 0.3), 12, 0, 0, 0);")

    // ELO badge
    val eloLabel = new Label(s"ELO: ${client.rankedElo}")
    eloLabel.setFont(Font.font("System", FontWeight.BOLD, 18))
    eloLabel.setTextFill(Color.web("#ffd700"))
    eloLabel.setStyle("-fx-background-color: rgba(255, 215, 0, 0.08); -fx-padding: 6 20; -fx-background-radius: 20; -fx-border-color: rgba(255, 215, 0, 0.2); -fx-border-radius: 20; -fx-border-width: 1;")

    val headerLine = new Region()
    headerLine.setMinHeight(2)
    headerLine.setMaxHeight(2)
    headerLine.setMaxWidth(60)
    headerLine.setStyle("-fx-background-color: linear-gradient(to right, transparent, #ffd700, transparent); -fx-background-radius: 1;")

    headerBox.getChildren.addAll(queueTitle, eloLabel, headerLine)

    // Mode selection card
    val modeCard = new VBox(14)
    modeCard.setPadding(new Insets(20, 28, 20, 28))
    modeCard.setMaxWidth(420)
    modeCard.setStyle(cardBg)
    modeCard.setAlignment(Pos.CENTER)

    val modeLabel = new Label("SELECT MODE")
    modeLabel.setStyle(sectionHeaderStyle)

    var selectedMode: Byte = RankedQueueMode.FFA

    val modeButtonActiveStyle = "-fx-background-color: linear-gradient(to bottom, #5aadff, #3a8eef); -fx-text-fill: white; -fx-font-size: 14; -fx-font-weight: bold; -fx-padding: 14 28; -fx-background-radius: 10; -fx-cursor: hand; -fx-effect: dropshadow(gaussian, rgba(74, 158, 255, 0.5), 16, 0, 0, 3); -fx-border-color: #6db8ff; -fx-border-radius: 10; -fx-border-width: 2;"
    val modeButtonInactiveStyle = "-fx-background-color: rgba(255,255,255,0.06); -fx-text-fill: #8899aa; -fx-font-size: 14; -fx-font-weight: bold; -fx-padding: 14 28; -fx-background-radius: 10; -fx-cursor: hand; -fx-border-color: rgba(255,255,255,0.1); -fx-border-radius: 10; -fx-border-width: 1;"
    val modeButtonInactiveHoverStyle = "-fx-background-color: rgba(255,255,255,0.12); -fx-text-fill: #ccdde8; -fx-font-size: 14; -fx-font-weight: bold; -fx-padding: 14 28; -fx-background-radius: 10; -fx-cursor: hand; -fx-border-color: rgba(255,255,255,0.2); -fx-border-radius: 10; -fx-border-width: 1;"

    val ffaBtn = new Button("FFA (8 Players)")
    ffaBtn.setStyle(modeButtonActiveStyle)
    ffaBtn.setMaxWidth(Double.MaxValue)
    HBox.setHgrow(ffaBtn, Priority.ALWAYS)

    val duelBtn = new Button("1v1 Duel")
    duelBtn.setStyle(modeButtonInactiveStyle)
    duelBtn.setMaxWidth(Double.MaxValue)
    HBox.setHgrow(duelBtn, Priority.ALWAYS)

    val teamsBtn = new Button("Teams (3v3)")
    teamsBtn.setStyle(modeButtonInactiveStyle)
    teamsBtn.setMaxWidth(Double.MaxValue)
    HBox.setHgrow(teamsBtn, Priority.ALWAYS)

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

    val modeRow = new HBox(12, ffaBtn, duelBtn, teamsBtn)
    modeRow.setAlignment(Pos.CENTER)

    // Queue status elements (initially hidden)
    val queueSizeLabel = new Label("Players in queue: 1")
    queueSizeLabel.setFont(Font.font("System", 14))
    queueSizeLabel.setTextFill(Color.web("#aabbcc"))
    queueSizeLabel.setVisible(false)
    queueSizeLabel.setManaged(false)

    val waitTimeLabel = new Label("Wait time: 0s")
    waitTimeLabel.setFont(Font.font("System", 14))
    waitTimeLabel.setTextFill(Color.web("#aabbcc"))
    waitTimeLabel.setVisible(false)
    waitTimeLabel.setManaged(false)

    val searchingLabel = new Label("")
    searchingLabel.setFont(Font.font("System", FontWeight.BOLD, 14))
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
          val modeText = if (selectedMode == RankedQueueMode.DUEL) "Searching for opponent"
            else if (selectedMode == RankedQueueMode.TEAMS) "Searching for teammates"
            else "Searching for match"
          searchingLabel.setText(s"$modeText$dots")
        }
      }
    }
    dotTimer.start()

    // Find Match button
    val findMatchBtn = new Button("Find Match")
    addHoverEffect(findMatchBtn, buttonGreenStyle, buttonGreenHoverStyle)
    findMatchBtn.setFont(Font.font("System", FontWeight.BOLD, 15))
    findMatchBtn.setMaxWidth(Double.MaxValue)

    modeCard.getChildren.addAll(modeLabel, modeRow, findMatchBtn, searchingLabel, searchSeparator, queueSizeLabel, waitTimeLabel)

    // Character selection section
    val charSectionLabel = new Label("SELECT CHARACTER")
    charSectionLabel.setStyle(sectionHeaderStyle)

    val previewSize = 96.0
    val previewCanvas = new Canvas(previewSize, previewSize)
    val previewGc = previewCanvas.getGraphicsContext2D
    var previewAnimTick = 0
    var previewDirIndex = 0
    val previewDirs = Array(Direction.Down, Direction.Left, Direction.Up, Direction.Right)

    val charNameLabel = new Label("")
    charNameLabel.setFont(Font.font("System", FontWeight.BOLD, 17))
    charNameLabel.setTextFill(Color.web("#4a9eff"))

    val charInfoLabel = new Label("")
    charInfoLabel.setFont(Font.font("System", 12))
    charInfoLabel.setTextFill(Color.web("#8899aa"))
    charInfoLabel.setWrapText(true)
    charInfoLabel.setMaxWidth(400)
    charInfoLabel.setStyle("-fx-line-spacing: 2;")

    def drawPreview(): Unit = {
      previewGc.clearRect(0, 0, previewSize, previewSize)
      val charDef = CharacterDef.get(client.selectedCharacterId)
      val dir = previewDirs(previewDirIndex)
      val frame = (previewAnimTick / 10) % 4
      val sprite = SpriteGenerator.getSprite(0, dir, frame, charDef.id.id)
      previewGc.setStroke(Color.web("#3a3a5e"))
      previewGc.setLineWidth(2)
      previewGc.strokeOval(4, 4, previewSize - 8, previewSize - 8)
      previewGc.setFill(Color.web("#1a1a30"))
      previewGc.fillOval(6, 6, previewSize - 12, previewSize - 12)
      val spriteDisplaySize = 68.0
      val offset = (previewSize - spriteDisplaySize) / 2.0
      previewGc.drawImage(sprite, offset, offset, spriteDisplaySize, spriteDisplaySize)
    }

    val previewTimer = new AnimationTimer {
      override def handle(now: Long): Unit = {
        previewAnimTick += 1
        if (previewAnimTick % 40 == 0) {
          previewDirIndex = (previewDirIndex + 1) % previewDirs.length
        }
        drawPreview()
      }
    }
    previewTimer.start()

    def updateCharacterInfo(): Unit = {
      val charDef = CharacterDef.get(client.selectedCharacterId)
      charNameLabel.setText(charDef.displayName)
      charInfoLabel.setText(s"${charDef.description}\nQ: ${charDef.qAbility.name} - ${charDef.qAbility.description}\nE: ${charDef.eAbility.name} - ${charDef.eAbility.description}")
      previewAnimTick = 0
      previewDirIndex = 0
      drawPreview()
    }

    val charCountLabel = new Label("")
    charCountLabel.setFont(Font.font("System", 11))
    charCountLabel.setTextFill(Color.web("#556677"))

    val arrowButtonStyle = "-fx-background-color: rgba(255,255,255,0.06); -fx-text-fill: #8899aa; -fx-font-size: 18; -fx-font-weight: bold; -fx-padding: 8 16; -fx-background-radius: 10; -fx-cursor: hand; -fx-border-color: rgba(255,255,255,0.08); -fx-border-width: 1; -fx-border-radius: 10;"
    val arrowButtonHoverStyle = "-fx-background-color: rgba(74, 158, 255, 0.15); -fx-text-fill: #4a9eff; -fx-font-size: 18; -fx-font-weight: bold; -fx-padding: 8 16; -fx-background-radius: 10; -fx-cursor: hand; -fx-border-color: rgba(74, 158, 255, 0.3); -fx-border-width: 1; -fx-border-radius: 10;"

    def currentCharIndex: Int = {
      val idx = CharacterDef.all.indexWhere(_.id.id == client.selectedCharacterId)
      if (idx < 0) 0 else idx
    }

    def selectByIndex(idx: Int): Unit = {
      val wrapped = ((idx % CharacterDef.all.size) + CharacterDef.all.size) % CharacterDef.all.size
      val charDef = CharacterDef.all(wrapped)
      client.changeRankedCharacter(charDef.id.id)
      updateCharacterInfo()
      charCountLabel.setText(s"${wrapped + 1} / ${CharacterDef.all.size}")
    }

    val prevBtn = new Button("\u25C0")
    prevBtn.setStyle(arrowButtonStyle)
    prevBtn.setOnMouseEntered(_ => prevBtn.setStyle(arrowButtonHoverStyle))
    prevBtn.setOnMouseExited(_ => prevBtn.setStyle(arrowButtonStyle))
    prevBtn.setOnAction(_ => selectByIndex(currentCharIndex - 1))

    val nextBtn = new Button("\u25B6")
    nextBtn.setStyle(arrowButtonStyle)
    nextBtn.setOnMouseEntered(_ => nextBtn.setStyle(arrowButtonHoverStyle))
    nextBtn.setOnMouseExited(_ => nextBtn.setStyle(arrowButtonStyle))
    nextBtn.setOnAction(_ => selectByIndex(currentCharIndex + 1))

    val carouselBox = new HBox(16, prevBtn, charCountLabel, nextBtn)
    carouselBox.setAlignment(Pos.CENTER)

    updateCharacterInfo()
    charCountLabel.setText(s"${currentCharIndex + 1} / ${CharacterDef.all.size}")

    val previewBox = new VBox(6, previewCanvas, charNameLabel)
    previewBox.setAlignment(Pos.CENTER)

    val charInfoCard = new VBox(8, charInfoLabel)
    charInfoCard.setPadding(new Insets(12, 16, 12, 16))
    charInfoCard.setMaxWidth(420)
    charInfoCard.setStyle(cardBgSubtle)

    val charSection = new VBox(12, charSectionLabel, previewBox, carouselBox, charInfoCard)
    charSection.setAlignment(Pos.CENTER)
    charSection.setPadding(new Insets(0, 24, 0, 24))

    // Back / Leave queue button
    val leaveBtn = new Button("Back")
    addHoverEffect(leaveBtn, buttonRedStyle, buttonRedHoverStyle)
    leaveBtn.setOnAction(_ => {
      previewTimer.stop()
      dotTimer.stop()
      if (isSearching) {
        client.leaveRankedQueue()
      }
      client.requestLobbyList()
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

      leaveBtn.setText("Leave Queue")
    })

    val buttonBox = new HBox(12)
    buttonBox.setAlignment(Pos.CENTER)
    buttonBox.setPadding(new Insets(12, 0, 24, 0))
    buttonBox.getChildren.add(leaveBtn)

    root.getChildren.addAll(headerBox, modeCard, new Region() { setMinHeight(16) }, charSection, new Region() { setMinHeight(4) }, buttonBox)

    // Wire up queue status listener
    client.rankedQueueListener = () => {
      Platform.runLater(() => {
        queueSizeLabel.setText(s"Players in queue: ${client.rankedQueueSize}")
        waitTimeLabel.setText(s"Wait time: ${client.rankedQueueWaitTime}s")
        eloLabel.setText(s"ELO: ${client.rankedElo}")
      })
    }

    // Wire up match found listener - transition to game
    client.rankedMatchFoundListener = () => {
      Platform.runLater(() => {
        // Match found - game starting packets will follow
      })
    }

    // Wire up game starting listener to transition to game scene
    client.gameStartingListener = () => {
      Platform.runLater(() => {
        previewTimer.stop()
        dotTimer.stop()
        showGameScene(stage)
      })
    }

    client.lobbyClosedListener = () => {
      Platform.runLater(() => {
        previewTimer.stop()
        dotTimer.stop()
        client.requestLobbyList()
        showLobbyBrowser(stage)
      })
    }

    val scene = new Scene(root)
    stage.setScene(scene)
  }

  private def showLeaderboard(stage: Stage): Unit = {
    val root = new VBox(16)
    root.setPadding(new Insets(24))
    root.setStyle(darkBg)

    val titleBar = new HBox(12)
    titleBar.setAlignment(Pos.CENTER_LEFT)
    val title = new Label("Leaderboard")
    title.setFont(Font.font("System", FontWeight.BOLD, 28))
    title.setTextFill(Color.WHITE)
    val spacer = new Region()
    HBox.setHgrow(spacer, Priority.ALWAYS)
    val backBtn = new Button("Back")
    addHoverEffect(backBtn, buttonStyle, buttonHoverStyle)
    backBtn.setOnAction(_ => {
      client.requestLobbyList()
      showLobbyBrowser(stage)
    })
    titleBar.getChildren.addAll(title, spacer, backBtn)

    val leaderboardListView = new ListView[String]()
    leaderboardListView.setStyle(listViewCss)
    leaderboardListView.setPrefHeight(500)
    VBox.setVgrow(leaderboardListView, Priority.ALWAYS)

    val leaderboardCellFactory = new Callback[ListView[String], ListCell[String]] {
      override def call(param: ListView[String]): ListCell[String] = new ListCell[String] {
        override def updateItem(item: String, empty: Boolean): Unit = {
          super.updateItem(item, empty)
          if (empty || item == null) {
            setText(null)
            setStyle("-fx-background-color: #242440;")
          } else {
            setText(item)
            val base = "-fx-font-size: 15; -fx-padding: 10 12; -fx-font-weight: bold;"
            val idx = getIndex
            // Check if this row is the local player
            val isLocal = idx >= 0 && idx < client.leaderboard.size() && {
              val entry = client.leaderboard.get(idx)
              entry(1).asInstanceOf[String].equalsIgnoreCase(client.playerName)
            }
            if (isLocal) {
              setStyle(s"-fx-background-color: rgba(74, 158, 255, 0.15); -fx-text-fill: #4a9eff; $base")
            } else if (idx == 0) {
              setStyle(s"-fx-background-color: #242440; -fx-text-fill: #ffd700; $base")
            } else if (idx == 1) {
              setStyle(s"-fx-background-color: #2a2a48; -fx-text-fill: #c0c0c0; $base")
            } else if (idx == 2) {
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

    val loadingLabel = new Label("Loading...")
    loadingLabel.setTextFill(Color.web("#8899bb"))
    loadingLabel.setFont(Font.font("System", 13))

    root.getChildren.addAll(titleBar, leaderboardListView, loadingLabel)

    val scene = new Scene(root)
    stage.setScene(scene)

    // Set up listener and request data
    client.leaderboardListener = () => {
      Platform.runLater(() => {
        val items = new java.util.ArrayList[String]()
        import scala.jdk.CollectionConverters._
        client.leaderboard.asScala.foreach { entry =>
          val rank = entry(0).asInstanceOf[Int]
          val username = entry(1).asInstanceOf[String]
          val elo = entry(2).asInstanceOf[Int]
          val wins = entry(3).asInstanceOf[Int]
          val matches = entry(4).asInstanceOf[Int]
          items.add(s"#$rank  |  $username  |  ELO: $elo  |  ${wins}W  |  $matches Matches")
        }
        leaderboardListView.setItems(FXCollections.observableArrayList(items))
        loadingLabel.setText(if (items.isEmpty) "No players found" else "")
      })
    }
    client.requestLeaderboard()
  }

  private def showAccountView(stage: Stage): Unit = {
    val root = new VBox(0)
    root.setStyle(darkBg)

    // Header
    val headerArea = new VBox(12)
    headerArea.setPadding(new Insets(28, 28, 20, 28))

    val titleBar = new HBox(12)
    titleBar.setAlignment(Pos.CENTER_LEFT)
    val title = new Label("Profile")
    title.setFont(Font.font("System", FontWeight.BOLD, 30))
    title.setTextFill(Color.WHITE)
    title.setStyle("-fx-effect: dropshadow(gaussian, rgba(74, 158, 255, 0.3), 12, 0, 0, 0);")

    val playerTag = new Label(client.playerName)
    playerTag.setFont(Font.font("System", FontWeight.BOLD, 14))
    playerTag.setTextFill(Color.web("#4a9eff"))
    playerTag.setStyle("-fx-background-color: rgba(74, 158, 255, 0.1); -fx-padding: 4 12; -fx-background-radius: 12; -fx-border-color: rgba(74, 158, 255, 0.2); -fx-border-radius: 12; -fx-border-width: 1;")

    val spacer = new Region()
    HBox.setHgrow(spacer, Priority.ALWAYS)
    val backBtn = new Button("Back")
    addHoverEffect(backBtn, buttonGhostStyle, buttonGhostHoverStyle)
    backBtn.setOnAction(_ => {
      client.requestLobbyList()
      showLobbyBrowser(stage)
    })
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

    val statsTitle = new Label("ALL-TIME STATS")
    statsTitle.setStyle(sectionHeaderStyle)

    val statsRow = new HBox(12)
    statsRow.setAlignment(Pos.CENTER)
    statsRow.setPadding(new Insets(8, 0, 4, 0))

    val killsBox = createStatBox("Kills", "0", "#2ecc71")
    val deathsBox = createStatBox("Deaths", "0", "#e84057")
    val matchesBox = createStatBox("Matches", "0", "#4a9eff")
    val winsBox = createStatBox("Wins", "0", "#ffd700")
    val eloBox = createStatBox("ELO", "1000", "#e88d3f")
    HBox.setHgrow(killsBox, Priority.ALWAYS)
    HBox.setHgrow(deathsBox, Priority.ALWAYS)
    HBox.setHgrow(matchesBox, Priority.ALWAYS)
    HBox.setHgrow(winsBox, Priority.ALWAYS)
    HBox.setHgrow(eloBox, Priority.ALWAYS)
    statsRow.getChildren.addAll(killsBox, deathsBox, matchesBox, winsBox, eloBox)

    statsCard.getChildren.addAll(statsTitle, statsRow)

    // Match history label
    val historyTitle = new Label("RECENT MATCHES")
    historyTitle.setStyle(sectionHeaderStyle)
    historyTitle.setPadding(new Insets(4, 0, 0, 0))

    // Match history list
    val historyListView = new ListView[String]()
    historyListView.setStyle(listViewCss)
    historyListView.setPrefHeight(280)
    VBox.setVgrow(historyListView, Priority.ALWAYS)

    val historyCellFactory = new Callback[ListView[String], ListCell[String]] {
      override def call(param: ListView[String]): ListCell[String] = new ListCell[String] {
        override def updateItem(item: String, empty: Boolean): Unit = {
          super.updateItem(item, empty)
          if (empty || item == null) {
            setText(null)
            setStyle("-fx-background-color: transparent;")
          } else {
            setText(item)
            val base = "-fx-text-fill: #ccdde8; -fx-font-size: 13; -fx-padding: 12 16; -fx-background-radius: 6;"
            // Color-code by rank
            val isWin = item.startsWith("#1/")
            if (isWin) {
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

    val loadingLabel = new Label("Loading...")
    loadingLabel.setTextFill(Color.web("#556677"))
    loadingLabel.setFont(Font.font("System", 12))

    contentArea.getChildren.addAll(statsCard, historyTitle, historyListView, loadingLabel)

    root.getChildren.addAll(headerArea, contentArea)

    val scene = new Scene(root)
    stage.setScene(scene)

    // Set up listener and request data
    client.matchHistoryListener = () => {
      Platform.runLater(() => {
        // Update stats
        val killsLabel = killsBox.getChildren.get(0).asInstanceOf[VBox].getChildren.get(1).asInstanceOf[Label]
        killsLabel.setText(client.totalKillsStat.toString)
        val deathsLabel = deathsBox.getChildren.get(0).asInstanceOf[VBox].getChildren.get(1).asInstanceOf[Label]
        deathsLabel.setText(client.totalDeathsStat.toString)
        val matchesLabel = matchesBox.getChildren.get(0).asInstanceOf[VBox].getChildren.get(1).asInstanceOf[Label]
        matchesLabel.setText(client.matchesPlayedStat.toString)
        val winsLabel = winsBox.getChildren.get(0).asInstanceOf[VBox].getChildren.get(1).asInstanceOf[Label]
        winsLabel.setText(client.winsStat.toString)
        val eloLabel = eloBox.getChildren.get(0).asInstanceOf[VBox].getChildren.get(1).asInstanceOf[Label]
        eloLabel.setText(client.rankedElo.toString)

        // Update match list
        val items = new java.util.ArrayList[String]()
        import scala.jdk.CollectionConverters._
        client.matchHistory.asScala.foreach { entry =>
          val mapName = WorldRegistry.getDisplayName(entry(1))
          val playedAt = entry(3).toLong * 1000L
          val date = new java.text.SimpleDateFormat("MMM d, HH:mm").format(new java.util.Date(playedAt))
          val kills = entry(4)
          val deaths = entry(5)
          val rank = entry(6)
          val totalPlayers = entry(7)
          val duration = entry(2)
          items.add(s"#${rank}/${totalPlayers}  |  $mapName  |  ${kills}K/${deaths}D  |  ${duration}min  |  $date")
        }
        historyListView.setItems(FXCollections.observableArrayList(items))
        loadingLabel.setText(if (items.isEmpty) "No matches played yet" else "")
      })
    }
    client.requestMatchHistory()
  }

  private def createStatBox(label: String, value: String, accentColor: String): StackPane = {
    val container = new StackPane()
    container.setStyle(s"-fx-background-color: rgba(255,255,255,0.03); -fx-background-radius: 12; -fx-border-color: rgba(255,255,255,0.05); -fx-border-radius: 12; -fx-border-width: 1;")
    container.setPadding(new Insets(12, 8, 12, 8))
    container.setMinWidth(80)

    val inner = new VBox(4)
    inner.setAlignment(Pos.CENTER)
    val nameLabel = new Label(label)
    nameLabel.setStyle(sectionHeaderStyle)
    val valueLabel = new Label(value)
    valueLabel.setFont(Font.font("System", FontWeight.BOLD, 22))
    valueLabel.setTextFill(Color.web(accentColor))
    inner.getChildren.addAll(nameLabel, valueLabel)

    container.getChildren.add(inner)
    container
  }

  private def showGameScene(stage: Stage): Unit = {
    canvas = new GameCanvas(client)
    client.setRejoinListener(() => canvas.resetVisualPosition())

    val root = new StackPane(canvas)
    val scene = new Scene(root)

    canvas.widthProperty().bind(scene.widthProperty())
    canvas.heightProperty().bind(scene.heightProperty())

    val keyHandler = new KeyboardHandler(client)
    scene.setOnKeyPressed(keyHandler)
    scene.setOnKeyReleased(keyHandler)

    controllerHandler = new ControllerHandler(client)
    controllerHandler.init()

    val mouseHandler = new MouseHandler(client, canvas)
    scene.setOnMousePressed(mouseHandler)
    scene.setOnMouseReleased(mouseHandler)
    scene.setOnMouseDragged(mouseHandler)
    scene.setOnMouseMoved(mouseHandler)

    scene.addEventFilter(KeyEvent.KEY_PRESSED, new EventHandler[KeyEvent] {
      override def handle(event: KeyEvent): Unit = {
        if (event.getCode == KeyCode.F11) {
          stage.setFullScreen(!stage.isFullScreen)
        }
      }
    })

    stage.setScene(scene)

    // Clear all pressed keys when the window loses focus to prevent stuck keys
    stage.focusedProperty().addListener((_: javafx.beans.value.ObservableValue[_ <: java.lang.Boolean], _: java.lang.Boolean, focused: java.lang.Boolean) => {
      if (!focused) {
        keyHandler.clearAllKeys()
      }
    })

    val frameIntervalNs = 1_000_000_000L / 60
    var lastFrameTime = 0L
    renderLoop = new AnimationTimer() {
      override def handle(now: Long): Unit = {
        if (now - lastFrameTime >= frameIntervalNs) {
          lastFrameTime = now
          keyHandler.update()
          controllerHandler.update()
          canvas.render()
        }
      }
    }
    renderLoop.start()

    // Wire game over listener
    client.gameOverListener = () => {
      Platform.runLater(() => {
        renderLoop.stop()
        showScoreboard(stage)
      })
    }

    stage.setOnCloseRequest(_ => {
      println("Closing application...")
      client.disconnect()
      renderLoop.stop()
      if (controllerHandler != null) controllerHandler.cleanup()
    })

    println("Game started!")
  }

  private def showScoreboard(stage: Stage): Unit = {
    val root = new VBox(0)
    root.setAlignment(Pos.TOP_CENTER)
    root.setStyle(darkBg)

    // Title section with glow
    val titleBox = new VBox(6)
    titleBox.setAlignment(Pos.CENTER)
    titleBox.setPadding(new Insets(36, 0, 24, 0))

    val title = new Label("Game Over")
    title.setFont(Font.font("System", FontWeight.BOLD, 40))
    title.setTextFill(Color.WHITE)
    title.setStyle("-fx-effect: dropshadow(gaussian, rgba(74, 158, 255, 0.4), 20, 0, 0, 0);")

    val subtitle = new Label("Final Scoreboard")
    subtitle.setFont(Font.font("System", 14))
    subtitle.setTextFill(Color.web("#556677"))

    val accentLine = createAccentLine()

    titleBox.getChildren.addAll(title, subtitle, accentLine)

    // Scoreboard card
    val scoreCard = new VBox(0)
    scoreCard.setMaxWidth(500)
    scoreCard.setStyle(cardBg)

    // Header row
    val header = new HBox(0)
    header.setAlignment(Pos.CENTER_LEFT)
    header.setPadding(new Insets(14, 20, 14, 20))
    header.setStyle("-fx-background-color: rgba(255,255,255,0.03); -fx-background-radius: 16 16 0 0; -fx-border-color: transparent transparent rgba(255,255,255,0.06) transparent; -fx-border-width: 0 0 1 0;")
    val hRank = new Label("RANK")
    hRank.setMinWidth(60); hRank.setStyle(sectionHeaderStyle)
    val hPlayer = new Label("PLAYER")
    hPlayer.setMinWidth(160); hPlayer.setStyle(sectionHeaderStyle)
    val hKills = new Label("KILLS")
    hKills.setMinWidth(70); hKills.setStyle(sectionHeaderStyle)
    val hDeaths = new Label("DEATHS")
    hDeaths.setMinWidth(70); hDeaths.setStyle(sectionHeaderStyle)
    header.getChildren.addAll(hRank, hPlayer, hKills, hDeaths)
    scoreCard.getChildren.add(header)

    import scala.jdk.CollectionConverters._

    // Team color map for team-colored rows
    val teamColors = Map(1 -> "rgba(74, 130, 255, 0.12)", 2 -> "rgba(232, 64, 87, 0.12)", 3 -> "rgba(46, 204, 113, 0.12)", 4 -> "rgba(241, 196, 15, 0.12)")

    // Sort by team then rank when in teams mode
    val sortedEntries = if (client.currentLobbyGameMode == 1) {
      client.scoreboard.asScala.toSeq.sortBy(e => (e.rank, e.teamId))
    } else {
      client.scoreboard.asScala.toSeq
    }

    var rowIndex = 0
    var lastTeamId = -1
    sortedEntries.foreach { entry =>
      // Add team separator header in Teams mode
      if (client.currentLobbyGameMode == 1 && entry.teamId != lastTeamId) {
        lastTeamId = entry.teamId
        val teamLabel = new Label(s"Team $lastTeamId")
        teamLabel.setFont(Font.font("System", FontWeight.BOLD, 13))
        val teamColor = lastTeamId match {
          case 1 => Color.web("#4a82ff")
          case 2 => Color.web("#e84057")
          case 3 => Color.web("#2ecc71")
          case 4 => Color.web("#f1c40f")
          case _ => Color.web("#8899aa")
        }
        teamLabel.setTextFill(teamColor)
        val teamHeaderBox = new HBox(teamLabel)
        teamHeaderBox.setPadding(new Insets(10, 20, 4, 20))
        teamHeaderBox.setStyle(s"-fx-background-color: ${teamColors.getOrElse(lastTeamId, "transparent")};")
        scoreCard.getChildren.add(teamHeaderBox)
      }

      val row = new HBox(0)
      row.setAlignment(Pos.CENTER_LEFT)
      row.setPadding(new Insets(12, 20, 12, 20))

      val isLocal = entry.playerId.equals(client.getLocalPlayerId)
      val isLast = rowIndex == sortedEntries.size - 1
      val bottomRadius = if (isLast) "-fx-background-radius: 0 0 16 16;" else ""

      val teamBg = if (client.currentLobbyGameMode == 1) {
        teamColors.getOrElse(entry.teamId, "transparent")
      } else "transparent"

      val rowBg = if (isLocal) {
        s"-fx-background-color: rgba(74, 158, 255, 0.1); -fx-border-color: transparent transparent rgba(255,255,255,0.04) transparent; -fx-border-width: 0 0 1 0; $bottomRadius"
      } else if (client.currentLobbyGameMode == 1) {
        s"-fx-background-color: $teamBg; -fx-border-color: transparent transparent rgba(255,255,255,0.03) transparent; -fx-border-width: 0 0 1 0; $bottomRadius"
      } else if (rowIndex % 2 == 0) {
        s"-fx-background-color: transparent; -fx-border-color: transparent transparent rgba(255,255,255,0.03) transparent; -fx-border-width: 0 0 1 0; $bottomRadius"
      } else {
        s"-fx-background-color: rgba(255,255,255,0.02); -fx-border-color: transparent transparent rgba(255,255,255,0.03) transparent; -fx-border-width: 0 0 1 0; $bottomRadius"
      }
      row.setStyle(rowBg)

      // Medal styling for top 3
      val rankText = entry.rank match {
        case 1 => "#1"
        case 2 => "#2"
        case 3 => "#3"
        case n => s"#$n"
      }
      val rankLabel = new Label(rankText)
      rankLabel.setMinWidth(60)
      val rankColor = entry.rank match {
        case 1 => Color.web("#ffd700")
        case 2 => Color.web("#c0c0c0")
        case 3 => Color.web("#cd7f32")
        case _ => Color.web("#556677")
      }
      rankLabel.setTextFill(rankColor)
      rankLabel.setFont(Font.font("System", FontWeight.BOLD, if (entry.rank <= 3) 20 else 16))
      if (entry.rank <= 3) {
        rankLabel.setStyle(s"-fx-effect: dropshadow(gaussian, ${if (entry.rank == 1) "rgba(255,215,0,0.4)" else if (entry.rank == 2) "rgba(192,192,192,0.3)" else "rgba(205,127,50,0.3)"}, 8, 0, 0, 0);")
      }

      val nameStr = if (isLocal) s"${client.playerName} (you)" else {
        val p = client.getPlayers.get(entry.playerId)
        if (p != null) p.getName else entry.playerId.toString.substring(0, 8)
      }
      val nameLabel = new Label(nameStr)
      nameLabel.setMinWidth(160)
      nameLabel.setTextFill(if (isLocal) Color.web("#4a9eff") else Color.web("#ccdde8"))
      nameLabel.setFont(Font.font("System", FontWeight.BOLD, 15))

      val killsLabel = new Label(entry.kills.toString)
      killsLabel.setMinWidth(70)
      killsLabel.setTextFill(Color.web("#2ecc71"))
      killsLabel.setFont(Font.font("System", FontWeight.BOLD, 16))

      val deathsLabel = new Label(entry.deaths.toString)
      deathsLabel.setMinWidth(70)
      deathsLabel.setTextFill(Color.web("#e84057"))
      deathsLabel.setFont(Font.font("System", FontWeight.BOLD, 15))

      row.getChildren.addAll(rankLabel, nameLabel, killsLabel, deathsLabel)
      scoreCard.getChildren.add(row)
      rowIndex += 1
    }

    val returnBtn = new Button("Return to Lobby")
    addHoverEffect(returnBtn, buttonStyle, buttonHoverStyle)
    returnBtn.setFont(Font.font("System", FontWeight.BOLD, 15))
    returnBtn.setOnAction(_ => {
      client.returnToLobbyBrowser()
      client.requestLobbyList()
      showLobbyBrowser(stage)
    })

    val btnBox = new VBox(0)
    btnBox.setAlignment(Pos.CENTER)
    btnBox.setPadding(new Insets(24, 0, 0, 0))
    btnBox.getChildren.add(returnBtn)

    root.getChildren.addAll(titleBox, scoreCard, btnBox)

    val scene = new Scene(root)
    stage.setScene(scene)
  }

  private def handleWorldFileFromServer(worldFileName: String): Unit = {
    if (worldFileName.isEmpty) {
      println("Server did not specify a world, using default")
      return
    }
    println(s"Server requested world: $worldFileName")
    val worldPath = "worlds/" + worldFileName
    try {
      val world = WorldLoader.load(worldPath)
      client.setWorld(world)
      if (canvas != null) canvas.resetVisualPosition()
      println(s"Loaded world: ${world.name} (${world.width}x${world.height})")
    } catch {
      case e: Exception =>
        println(s"Failed to load world $worldPath: ${e.getMessage}, using default")
        client.setWorld(WorldData.createEmpty(Constants.GRID_SIZE, Constants.GRID_SIZE))
        if (canvas != null) canvas.resetVisualPosition()
    }
  }

  override def stop(): Unit = {
    if (controllerHandler != null) controllerHandler.cleanup()
    if (client != null) {
      client.disconnect()
    }
  }
}

object ClientMain {
  def main(args: Array[String]): Unit = {
    Application.launch(classOf[ClientMain], args: _*)
  }
}
