package com.gridgame.client

import com.gridgame.client.input.KeyboardHandler
import com.gridgame.client.input.MouseHandler
import com.gridgame.client.ui.GameCanvas
import com.gridgame.common.Constants
import com.gridgame.common.WorldRegistry
import com.gridgame.common.model.WorldData
import com.gridgame.common.world.WorldLoader

import javafx.animation.AnimationTimer
import javafx.application.Application
import javafx.application.Platform
import javafx.collections.FXCollections
import javafx.event.EventHandler
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Scene
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
import javafx.stage.Stage

class ClientMain extends Application {

  private var client: GameClient = _
  private var canvas: GameCanvas = _
  private var renderLoop: AnimationTimer = _

  private val darkBg = "-fx-background-color: #1e1e2e;"
  private val fieldStyle = "-fx-background-color: #2a2a3e; -fx-text-fill: white; -fx-font-size: 15; -fx-prompt-text-fill: #999; -fx-padding: 8; -fx-background-radius: 4;"
  private val buttonStyle = "-fx-background-color: #4a9eff; -fx-text-fill: white; -fx-font-size: 15; -fx-font-weight: bold; -fx-padding: 10 28; -fx-background-radius: 4; -fx-cursor: hand;"
  private val buttonRedStyle = "-fx-background-color: #ff4a4a; -fx-text-fill: white; -fx-font-size: 15; -fx-font-weight: bold; -fx-padding: 10 28; -fx-background-radius: 4; -fx-cursor: hand;"
  private val buttonGreenStyle = "-fx-background-color: #4aff4a; -fx-text-fill: #111; -fx-font-size: 15; -fx-font-weight: bold; -fx-padding: 10 28; -fx-background-radius: 4; -fx-cursor: hand;"
  private val labelStyle = "-fx-text-fill: #ddd; -fx-font-size: 15;"
  private val comboStyle = "-fx-background-color: #2a2a3e; -fx-text-fill: white; -fx-font-size: 15; -fx-padding: 6; -fx-background-radius: 4;"
  private val listViewCss = "-fx-background-color: #2a2a3e; -fx-control-inner-background: #2a2a3e; -fx-text-fill: white; -fx-font-size: 14;"

  private def styleCombo(combo: ComboBox[String]): Unit = {
    combo.setStyle(comboStyle)
    val cellFactory = new Callback[ListView[String], ListCell[String]] {
      override def call(param: ListView[String]): ListCell[String] = new ListCell[String] {
        override def updateItem(item: String, empty: Boolean): Unit = {
          super.updateItem(item, empty)
          if (empty || item == null) {
            setText(null)
            setStyle("-fx-background-color: #2a2a3e;")
          } else {
            setText(item)
            setStyle("-fx-background-color: #2a2a3e; -fx-text-fill: white; -fx-font-size: 14; -fx-padding: 6;")
          }
          // Highlight on hover/selection
          if (isSelected) setStyle("-fx-background-color: #4a9eff; -fx-text-fill: white; -fx-font-size: 14; -fx-padding: 6;")
        }
      }
    }
    combo.setCellFactory(cellFactory)
    combo.setButtonCell(cellFactory.call(null))
  }

  override def start(primaryStage: Stage): Unit = {
    primaryStage.setTitle("Grid Game - Multiplayer 2D")
    primaryStage.setResizable(true)

    showWelcomeScreen(primaryStage)
  }

  private def showWelcomeScreen(stage: Stage): Unit = {
    val root = new VBox(16)
    root.setAlignment(Pos.CENTER)
    root.setPadding(new Insets(40))
    root.setStyle(darkBg)

    val title = new Label("Grid Game")
    title.setFont(Font.font("System", FontWeight.BOLD, 42))
    title.setTextFill(Color.WHITE)

    val subtitle = new Label("Multiplayer 2D")
    subtitle.setFont(Font.font("System", FontWeight.NORMAL, 16))
    subtitle.setTextFill(Color.web("#888"))

    val modeLabel = new Label("Login")
    modeLabel.setFont(Font.font("System", FontWeight.BOLD, 18))
    modeLabel.setTextFill(Color.web("#4a9eff"))

    val usernameLabel = new Label("Username")
    usernameLabel.setTextFill(Color.web("#ddd"))
    usernameLabel.setFont(Font.font("System", FontWeight.BOLD, 15))

    val usernameField = new TextField()
    usernameField.setPromptText("Enter username")
    usernameField.setMaxWidth(280)
    usernameField.setStyle(fieldStyle)

    val passwordLabel = new Label("Password")
    passwordLabel.setTextFill(Color.web("#ddd"))
    passwordLabel.setFont(Font.font("System", FontWeight.BOLD, 15))

    val passwordField = new PasswordField()
    passwordField.setPromptText("Enter password")
    passwordField.setMaxWidth(280)
    passwordField.setStyle(fieldStyle)

    val confirmLabel = new Label("Confirm Password")
    confirmLabel.setTextFill(Color.web("#ddd"))
    confirmLabel.setFont(Font.font("System", FontWeight.BOLD, 15))

    val confirmField = new PasswordField()
    confirmField.setPromptText("Confirm password")
    confirmField.setMaxWidth(280)
    confirmField.setStyle(fieldStyle)

    val confirmBox = new VBox(4, confirmLabel, confirmField)
    confirmBox.setVisible(false)
    confirmBox.setManaged(false)

    val hostLabel = new Label("Host")
    hostLabel.setTextFill(Color.web("#ddd"))
    hostLabel.setFont(Font.font("System", FontWeight.BOLD, 15))

    val hostField = new TextField()
    hostField.setPromptText("localhost")
    hostField.setMaxWidth(280)
    hostField.setStyle(fieldStyle)

    val portLabel = new Label("Port")
    portLabel.setTextFill(Color.web("#ddd"))
    portLabel.setFont(Font.font("System", FontWeight.BOLD, 15))

    val portField = new TextField()
    portField.setPromptText("25565")
    portField.setMaxWidth(280)
    portField.setStyle(fieldStyle)

    val actionButton = new Button("Login")
    actionButton.setStyle(buttonStyle)
    actionButton.setDefaultButton(true)

    val toggleLink = new Button("Don't have an account? Sign Up")
    toggleLink.setStyle("-fx-background-color: transparent; -fx-text-fill: #4a9eff; -fx-cursor: hand; -fx-underline: true; -fx-padding: 0;")

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
    statusLabel.setTextFill(Color.web("#ff6666"))
    statusLabel.setFont(Font.font("System", FontWeight.BOLD, 14))
    statusLabel.setWrapText(true)
    statusLabel.setMaxWidth(280)

    val doAction = () => {
      val username = usernameField.getText.trim
      val password = passwordField.getText

      if (username.isEmpty) {
        statusLabel.setTextFill(Color.web("#ff6666"))
        statusLabel.setText("Username is required")
      } else if (password.isEmpty) {
        statusLabel.setTextFill(Color.web("#ff6666"))
        statusLabel.setText("Password is required")
      } else if (isSignupMode && password != confirmField.getText) {
        statusLabel.setTextFill(Color.web("#ff6666"))
        statusLabel.setText("Passwords do not match")
      } else if (username.length > 20) {
        statusLabel.setTextFill(Color.web("#ff6666"))
        statusLabel.setText("Username max 20 characters")
      } else if (password.length > 20) {
        statusLabel.setTextFill(Color.web("#ff6666"))
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
              statusLabel.setTextFill(Color.web("#ff6666"))
              statusLabel.setText("Invalid port number")
              -1
          }
        }
        if (port > 0) {
          actionButton.setDisable(true)
          statusLabel.setTextFill(Color.web("#aaa"))
          statusLabel.setText(s"Connecting to $host:$port...")
          startConnection(stage, host, port, username, password, isSignupMode, statusLabel, actionButton)
        }
      }
    }

    actionButton.setOnAction(_ => doAction())
    passwordField.setOnAction(_ => doAction())

    root.getChildren.addAll(title, subtitle, modeLabel,
      new VBox(4, usernameLabel, usernameField),
      new VBox(4, passwordLabel, passwordField),
      confirmBox,
      new VBox(4, hostLabel, hostField),
      new VBox(4, portLabel, portField),
      actionButton, toggleLink, statusLabel)

    val scene = new Scene(root, 400, 550)
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
          statusLabel.setTextFill(Color.web("#ff6666"))
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
          statusLabel.setTextFill(Color.web("#aaa"))
          statusLabel.setText(if (isSignup) "Creating account..." else "Logging in...")
        })
      } catch {
        case e: Exception =>
          Platform.runLater(() => {
            statusLabel.setTextFill(Color.web("#ff6666"))
            statusLabel.setText(s"Connection failed: ${e.getMessage}")
            actionButton.setDisable(false)
          })
      }
    }).start()
  }

  private def showLobbyBrowser(stage: Stage): Unit = {
    val root = new VBox(12)
    root.setPadding(new Insets(20))
    root.setStyle(darkBg)

    val titleBar = new HBox(12)
    titleBar.setAlignment(Pos.CENTER_LEFT)
    val title = new Label("Lobby Browser")
    title.setFont(Font.font("System", FontWeight.BOLD, 28))
    title.setTextFill(Color.WHITE)
    val spacer = new Region()
    HBox.setHgrow(spacer, Priority.ALWAYS)
    val refreshBtn = new Button("Refresh")
    refreshBtn.setStyle(buttonStyle)
    titleBar.getChildren.addAll(title, spacer, refreshBtn)

    val lobbyListView = new ListView[String]()
    lobbyListView.setStyle(listViewCss)
    lobbyListView.setPrefHeight(250)
    VBox.setVgrow(lobbyListView, Priority.ALWAYS)

    val joinBtn = new Button("Join Selected")
    joinBtn.setStyle(buttonStyle)
    joinBtn.setDisable(true)

    lobbyListView.getSelectionModel.selectedIndexProperty().addListener((_, _, newVal) => {
      joinBtn.setDisable(newVal.intValue() < 0)
    })

    // Create lobby form
    val createLabel = new Label("Create New Lobby")
    createLabel.setFont(Font.font("System", FontWeight.BOLD, 20))
    createLabel.setTextFill(Color.WHITE)

    val nameField = new TextField()
    nameField.setPromptText("Lobby name")
    nameField.setMaxWidth(300)
    nameField.setStyle(fieldStyle)

    val mapCombo = new ComboBox[String](FXCollections.observableArrayList(WorldRegistry.displayNames: _*))
    mapCombo.getSelectionModel.select(0)
    mapCombo.setMaxWidth(300)
    styleCombo(mapCombo)

    val durationCombo = new ComboBox[String](FXCollections.observableArrayList("3 min", "5 min", "10 min", "15 min", "20 min"))
    durationCombo.getSelectionModel.select(1) // Default 5 min
    durationCombo.setMaxWidth(300)
    styleCombo(durationCombo)

    val createBtn = new Button("Create Lobby")
    createBtn.setStyle(buttonGreenStyle)

    val statusLabel = new Label("")
    statusLabel.setTextFill(Color.web("#aaa"))

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
          items.add(s"${info.name}  |  $mapName  |  ${info.playerCount}/${info.maxPlayers}  |  $statusStr  |  ${info.durationMinutes}min")
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
    refreshBtn.setOnAction(_ => {
      client.requestLobbyList()
    })

    joinBtn.setOnAction(_ => {
      val idx = lobbyListView.getSelectionModel.getSelectedIndex
      if (idx >= 0 && idx < client.lobbyList.size()) {
        val info = client.lobbyList.get(idx)
        if (info.status == 0) { // Only join waiting lobbies
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

    val createForm = new VBox(8, createLabel,
      new HBox(8, new Label("Name:") { setStyle(labelStyle); setMinWidth(60) }, nameField),
      new HBox(8, new Label("Map:") { setStyle(labelStyle); setMinWidth(60) }, mapCombo),
      new HBox(8, new Label("Time:") { setStyle(labelStyle); setMinWidth(60) }, durationCombo),
      createBtn
    )
    createForm.setPadding(new Insets(10, 0, 0, 0))

    root.getChildren.addAll(titleBar, lobbyListView, joinBtn, createForm, statusLabel)

    val scene = new Scene(root, 550, 600)
    stage.setScene(scene)

    // Auto-refresh on show
    client.requestLobbyList()
  }

  private def showLobbyRoom(stage: Stage): Unit = {
    val root = new VBox(16)
    root.setPadding(new Insets(24))
    root.setAlignment(Pos.TOP_CENTER)
    root.setStyle(darkBg)

    val lobbyTitle = new Label(client.currentLobbyName)
    lobbyTitle.setFont(Font.font("System", FontWeight.BOLD, 28))
    lobbyTitle.setTextFill(Color.WHITE)

    val mapLabel = new Label(s"Map: ${WorldRegistry.getDisplayName(client.currentLobbyMapIndex)}")
    mapLabel.setFont(Font.font("System", 17))
    mapLabel.setTextFill(Color.web("#ccc"))

    val durationLabel = new Label(s"Duration: ${client.currentLobbyDuration} min")
    durationLabel.setFont(Font.font("System", 17))
    durationLabel.setTextFill(Color.web("#ccc"))

    val playersLabel = new Label(s"Players: ${client.currentLobbyPlayerCount}/${client.currentLobbyMaxPlayers}")
    playersLabel.setFont(Font.font("System", FontWeight.BOLD, 20))
    playersLabel.setTextFill(Color.WHITE)

    val waitingLabel = new Label("Waiting for host to start...")
    waitingLabel.setFont(Font.font("System", 16))
    waitingLabel.setTextFill(Color.web("#aaa"))

    val buttonBox = new HBox(12)
    buttonBox.setAlignment(Pos.CENTER)

    val leaveBtn = new Button("Leave")
    leaveBtn.setStyle(buttonRedStyle)

    leaveBtn.setOnAction(_ => {
      client.leaveLobby()
      showLobbyBrowser(stage)
    })

    buttonBox.getChildren.add(leaveBtn)

    // Host-only controls
    if (client.isLobbyHost) {
      waitingLabel.setText("You are the host. Start when ready!")

      val mapCombo = new ComboBox[String](FXCollections.observableArrayList(WorldRegistry.displayNames: _*))
      mapCombo.getSelectionModel.select(client.currentLobbyMapIndex)
      styleCombo(mapCombo)

      val durationCombo = new ComboBox[String](FXCollections.observableArrayList("3 min", "5 min", "10 min", "15 min", "20 min"))
      val durIdx = client.currentLobbyDuration match {
        case 3 => 0; case 5 => 1; case 10 => 2; case 15 => 3; case 20 => 4; case _ => 1
      }
      durationCombo.getSelectionModel.select(durIdx)
      styleCombo(durationCombo)

      mapCombo.setOnAction(_ => {
        val dur = durationCombo.getSelectionModel.getSelectedItem.split(" ")(0).toInt
        client.updateLobbyConfig(mapCombo.getSelectionModel.getSelectedIndex, dur)
      })
      durationCombo.setOnAction(_ => {
        val dur = durationCombo.getSelectionModel.getSelectedItem.split(" ")(0).toInt
        client.updateLobbyConfig(mapCombo.getSelectionModel.getSelectedIndex, dur)
      })

      val startBtn = new Button("Start Game")
      startBtn.setStyle(buttonGreenStyle)
      startBtn.setFont(Font.font("System", FontWeight.BOLD, 16))
      startBtn.setOnAction(_ => {
        client.startGame()
      })

      val configBox = new VBox(8,
        new HBox(8, new Label("Map:") { setStyle(labelStyle); setMinWidth(60) }, mapCombo),
        new HBox(8, new Label("Time:") { setStyle(labelStyle); setMinWidth(60) }, durationCombo)
      )

      buttonBox.getChildren.add(startBtn)
      root.getChildren.addAll(lobbyTitle, playersLabel, configBox, waitingLabel, buttonBox)
    } else {
      root.getChildren.addAll(lobbyTitle, mapLabel, durationLabel, playersLabel, waitingLabel, buttonBox)
    }

    // Wire up listeners
    client.lobbyUpdatedListener = () => {
      Platform.runLater(() => {
        playersLabel.setText(s"Players: ${client.currentLobbyPlayerCount}/${client.currentLobbyMaxPlayers}")
        mapLabel.setText(s"Map: ${WorldRegistry.getDisplayName(client.currentLobbyMapIndex)}")
        durationLabel.setText(s"Duration: ${client.currentLobbyDuration} min")
      })
    }

    client.gameStartingListener = () => {
      Platform.runLater(() => showGameScene(stage))
    }

    client.lobbyClosedListener = () => {
      Platform.runLater(() => showLobbyBrowser(stage))
    }

    val scene = new Scene(root, 450, 400)
    stage.setScene(scene)
  }

  private def showGameScene(stage: Stage): Unit = {
    canvas = new GameCanvas(client)
    client.setRejoinListener(() => canvas.resetVisualPosition())

    val root = new StackPane(canvas)
    val scene = new Scene(root, Constants.VIEWPORT_SIZE_PX, Constants.VIEWPORT_SIZE_PX)

    canvas.widthProperty().bind(scene.widthProperty())
    canvas.heightProperty().bind(scene.heightProperty())

    val keyHandler = new KeyboardHandler(client)
    scene.setOnKeyPressed(keyHandler)
    scene.setOnKeyReleased(keyHandler)

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

    val frameIntervalNs = 1_000_000_000L / 60
    var lastFrameTime = 0L
    renderLoop = new AnimationTimer() {
      override def handle(now: Long): Unit = {
        if (now - lastFrameTime >= frameIntervalNs) {
          lastFrameTime = now
          keyHandler.update()
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
    })

    println("Game started!")
  }

  private def showScoreboard(stage: Stage): Unit = {
    val root = new VBox(16)
    root.setPadding(new Insets(30))
    root.setAlignment(Pos.TOP_CENTER)
    root.setStyle(darkBg)

    val title = new Label("Game Over")
    title.setFont(Font.font("System", FontWeight.BOLD, 36))
    title.setTextFill(Color.WHITE)

    val subtitle = new Label("Final Scoreboard")
    subtitle.setFont(Font.font("System", 18))
    subtitle.setTextFill(Color.web("#aaa"))

    // Header row
    val header = new HBox(20)
    header.setAlignment(Pos.CENTER)
    header.setPadding(new Insets(8))
    val hRank = new Label("Rank")
    hRank.setMinWidth(50); hRank.setTextFill(Color.web("#ccc")); hRank.setFont(Font.font("System", FontWeight.BOLD, 15))
    val hPlayer = new Label("Player")
    hPlayer.setMinWidth(120); hPlayer.setTextFill(Color.web("#ccc")); hPlayer.setFont(Font.font("System", FontWeight.BOLD, 15))
    val hKills = new Label("Kills")
    hKills.setMinWidth(60); hKills.setTextFill(Color.web("#ccc")); hKills.setFont(Font.font("System", FontWeight.BOLD, 15))
    val hDeaths = new Label("Deaths")
    hDeaths.setMinWidth(60); hDeaths.setTextFill(Color.web("#ccc")); hDeaths.setFont(Font.font("System", FontWeight.BOLD, 15))
    header.getChildren.addAll(hRank, hPlayer, hKills, hDeaths)

    val scoreRows = new VBox(4)
    import scala.jdk.CollectionConverters._
    client.scoreboard.asScala.foreach { entry =>
      val row = new HBox(20)
      row.setAlignment(Pos.CENTER)
      row.setPadding(new Insets(6))

      val isLocal = entry.playerId.equals(client.getLocalPlayerId)
      val rowBg = if (isLocal) "-fx-background-color: #3a3a5e; -fx-background-radius: 4;" else ""
      row.setStyle(rowBg)

      val rankLabel = new Label(s"#${entry.rank}")
      rankLabel.setMinWidth(50)
      rankLabel.setTextFill(if (entry.rank == 1) Color.GOLD else Color.WHITE)
      rankLabel.setFont(Font.font("System", FontWeight.BOLD, 18))

      val nameStr = if (isLocal) s"You (${client.playerName})" else {
        val p = client.getPlayers.get(entry.playerId)
        if (p != null) p.getName else entry.playerId.toString.substring(0, 8)
      }
      val nameLabel = new Label(nameStr)
      nameLabel.setMinWidth(120)
      nameLabel.setTextFill(if (isLocal) Color.web("#5ab0ff") else Color.WHITE)
      nameLabel.setFont(Font.font("System", FontWeight.BOLD, 16))

      val killsLabel = new Label(entry.kills.toString)
      killsLabel.setMinWidth(60)
      killsLabel.setTextFill(Color.web("#5aff5a"))
      killsLabel.setFont(Font.font("System", FontWeight.BOLD, 16))

      val deathsLabel = new Label(entry.deaths.toString)
      deathsLabel.setMinWidth(60)
      deathsLabel.setTextFill(Color.web("#ff5a5a"))
      deathsLabel.setFont(Font.font("System", FontWeight.BOLD, 15))

      row.getChildren.addAll(rankLabel, nameLabel, killsLabel, deathsLabel)
      scoreRows.getChildren.add(row)
    }

    val returnBtn = new Button("Return to Lobby")
    returnBtn.setStyle(buttonStyle)
    returnBtn.setFont(Font.font("System", FontWeight.BOLD, 16))
    returnBtn.setOnAction(_ => {
      client.returnToLobbyBrowser()
      client.requestLobbyList()
      showLobbyBrowser(stage)
    })

    root.getChildren.addAll(title, subtitle, header, scoreRows, returnBtn)

    val scene = new Scene(root, 500, 500)
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
