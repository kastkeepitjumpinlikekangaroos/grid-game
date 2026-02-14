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

  private val darkBg = "-fx-background-color: #1a1a2e;"
  private val cardBg = "-fx-background-color: #242440; -fx-background-radius: 12; -fx-border-color: #3a3a5e; -fx-border-radius: 12; -fx-border-width: 1;"
  private val fieldStyle = "-fx-background-color: #2e2e4a; -fx-text-fill: white; -fx-font-size: 15; -fx-prompt-text-fill: #777; -fx-padding: 10 12; -fx-background-radius: 6; -fx-border-color: #3a3a5e; -fx-border-radius: 6; -fx-border-width: 1;"
  private val fieldFocusStyle = "-fx-background-color: #2e2e4a; -fx-text-fill: white; -fx-font-size: 15; -fx-prompt-text-fill: #777; -fx-padding: 10 12; -fx-background-radius: 6; -fx-border-color: #4a9eff; -fx-border-radius: 6; -fx-border-width: 1.5;"
  private val buttonStyle = "-fx-background-color: #4a9eff; -fx-text-fill: white; -fx-font-size: 15; -fx-font-weight: bold; -fx-padding: 10 32; -fx-background-radius: 6; -fx-cursor: hand; -fx-effect: dropshadow(gaussian, rgba(74, 158, 255, 0.3), 8, 0, 0, 2);"
  private val buttonHoverStyle = "-fx-background-color: #5aadff; -fx-text-fill: white; -fx-font-size: 15; -fx-font-weight: bold; -fx-padding: 10 32; -fx-background-radius: 6; -fx-cursor: hand; -fx-effect: dropshadow(gaussian, rgba(74, 158, 255, 0.5), 12, 0, 0, 3);"
  private val buttonRedStyle = "-fx-background-color: #e84057; -fx-text-fill: white; -fx-font-size: 15; -fx-font-weight: bold; -fx-padding: 10 32; -fx-background-radius: 6; -fx-cursor: hand; -fx-effect: dropshadow(gaussian, rgba(232, 64, 87, 0.3), 8, 0, 0, 2);"
  private val buttonRedHoverStyle = "-fx-background-color: #f05068; -fx-text-fill: white; -fx-font-size: 15; -fx-font-weight: bold; -fx-padding: 10 32; -fx-background-radius: 6; -fx-cursor: hand; -fx-effect: dropshadow(gaussian, rgba(232, 64, 87, 0.5), 12, 0, 0, 3);"
  private val buttonGreenStyle = "-fx-background-color: #2ecc71; -fx-text-fill: white; -fx-font-size: 15; -fx-font-weight: bold; -fx-padding: 10 32; -fx-background-radius: 6; -fx-cursor: hand; -fx-effect: dropshadow(gaussian, rgba(46, 204, 113, 0.3), 8, 0, 0, 2);"
  private val buttonGreenHoverStyle = "-fx-background-color: #3ddb80; -fx-text-fill: white; -fx-font-size: 15; -fx-font-weight: bold; -fx-padding: 10 32; -fx-background-radius: 6; -fx-cursor: hand; -fx-effect: dropshadow(gaussian, rgba(46, 204, 113, 0.5), 12, 0, 0, 3);"
  private val labelStyle = "-fx-text-fill: #ccd; -fx-font-size: 15;"
  private val comboStyle = "-fx-background-color: #2e2e4a; -fx-text-fill: white; -fx-font-size: 15; -fx-padding: 6; -fx-background-radius: 6; -fx-border-color: #3a3a5e; -fx-border-radius: 6; -fx-border-width: 1;"
  private val listViewCss = "-fx-background-color: #242440; -fx-control-inner-background: #242440; -fx-text-fill: white; -fx-font-size: 14; -fx-background-radius: 8;"
  private val sectionHeaderStyle = "-fx-text-fill: #8899bb; -fx-font-size: 11; -fx-font-weight: bold;"

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
            setStyle("-fx-background-color: #2e2e4a;")
          } else {
            setText(item)
            setStyle("-fx-background-color: #2e2e4a; -fx-text-fill: white; -fx-font-size: 14; -fx-padding: 8 10;")
          }
          if (isSelected) setStyle("-fx-background-color: #4a9eff; -fx-text-fill: white; -fx-font-size: 14; -fx-padding: 8 10; -fx-background-radius: 4;")
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
    val root = new VBox(0)
    root.setAlignment(Pos.CENTER)
    root.setStyle(darkBg)

    // Title section
    val titleBox = new VBox(4)
    titleBox.setAlignment(Pos.CENTER)
    titleBox.setPadding(new Insets(36, 0, 20, 0))

    val title = new Label("Grid Game")
    title.setFont(Font.font("System", FontWeight.BOLD, 44))
    title.setTextFill(Color.WHITE)
    title.setStyle("-fx-effect: dropshadow(gaussian, rgba(74, 158, 255, 0.4), 16, 0, 0, 0);")

    val subtitle = new Label("Multiplayer 2D")
    subtitle.setFont(Font.font("System", FontWeight.NORMAL, 15))
    subtitle.setTextFill(Color.web("#667"))

    titleBox.getChildren.addAll(title, subtitle)

    // Card container for the form
    val card = new VBox(14)
    card.setAlignment(Pos.CENTER)
    card.setPadding(new Insets(24, 32, 24, 32))
    card.setMaxWidth(340)
    card.setStyle(cardBg)

    val modeLabel = new Label("Login")
    modeLabel.setFont(Font.font("System", FontWeight.BOLD, 18))
    modeLabel.setTextFill(Color.web("#4a9eff"))

    val usernameLabel = new Label("USERNAME")
    usernameLabel.setStyle(sectionHeaderStyle)

    val usernameField = new TextField()
    usernameField.setPromptText("Enter username")
    usernameField.setMaxWidth(280)
    addFieldFocusEffect(usernameField)

    val passwordLabel = new Label("PASSWORD")
    passwordLabel.setStyle(sectionHeaderStyle)

    val passwordField = new PasswordField()
    passwordField.setPromptText("Enter password")
    passwordField.setMaxWidth(280)
    passwordField.setStyle(fieldStyle)
    passwordField.focusedProperty().addListener((_, _, focused) => {
      passwordField.setStyle(if (focused) fieldFocusStyle else fieldStyle)
    })

    val confirmLabel = new Label("CONFIRM PASSWORD")
    confirmLabel.setStyle(sectionHeaderStyle)

    val confirmField = new PasswordField()
    confirmField.setPromptText("Confirm password")
    confirmField.setMaxWidth(280)
    confirmField.setStyle(fieldStyle)
    confirmField.focusedProperty().addListener((_, _, focused) => {
      confirmField.setStyle(if (focused) fieldFocusStyle else fieldStyle)
    })

    val confirmBox = new VBox(4, confirmLabel, confirmField)
    confirmBox.setVisible(false)
    confirmBox.setManaged(false)

    // Server section with separator
    val serverSep = new Region()
    serverSep.setMinHeight(1)
    serverSep.setMaxHeight(1)
    serverSep.setStyle("-fx-background-color: #3a3a5e;")

    val serverLabel = new Label("SERVER")
    serverLabel.setStyle(sectionHeaderStyle)

    val hostField = new TextField()
    hostField.setPromptText("localhost")
    hostField.setMaxWidth(280)
    addFieldFocusEffect(hostField)

    val portField = new TextField()
    portField.setPromptText("25565")
    portField.setMaxWidth(280)
    addFieldFocusEffect(portField)

    val serverRow = new HBox(8)
    serverRow.setMaxWidth(280)
    HBox.setHgrow(hostField, Priority.ALWAYS)
    portField.setMaxWidth(90)
    portField.setPrefWidth(90)
    serverRow.getChildren.addAll(hostField, portField)

    val actionButton = new Button("Login")
    addHoverEffect(actionButton, buttonStyle, buttonHoverStyle)
    actionButton.setDefaultButton(true)
    actionButton.setMaxWidth(280)

    val toggleLink = new Button("Don't have an account? Sign Up")
    toggleLink.setStyle("-fx-background-color: transparent; -fx-text-fill: #6688bb; -fx-cursor: hand; -fx-font-size: 12; -fx-padding: 0;")
    toggleLink.setOnMouseEntered(_ => toggleLink.setStyle("-fx-background-color: transparent; -fx-text-fill: #4a9eff; -fx-cursor: hand; -fx-font-size: 12; -fx-padding: 0;"))
    toggleLink.setOnMouseExited(_ => toggleLink.setStyle("-fx-background-color: transparent; -fx-text-fill: #6688bb; -fx-cursor: hand; -fx-font-size: 12; -fx-padding: 0;"))

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
    statusLabel.setMaxWidth(280)

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

    root.getChildren.addAll(titleBox, card, new Region() { setMinHeight(12) }, toggleLink)

    val scene = new Scene(root, 420, 580)
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
    val root = new VBox(16)
    root.setPadding(new Insets(24))
    root.setStyle(darkBg)

    val titleBar = new HBox(12)
    titleBar.setAlignment(Pos.CENTER_LEFT)
    val title = new Label("Lobby Browser")
    title.setFont(Font.font("System", FontWeight.BOLD, 28))
    title.setTextFill(Color.WHITE)
    val spacer = new Region()
    HBox.setHgrow(spacer, Priority.ALWAYS)
    val refreshBtn = new Button("Refresh")
    addHoverEffect(refreshBtn, buttonStyle, buttonHoverStyle)
    titleBar.getChildren.addAll(title, spacer, refreshBtn)

    // Lobby list with custom cell factory
    val lobbyListView = new ListView[String]()
    lobbyListView.setStyle(listViewCss)
    lobbyListView.setPrefHeight(250)
    VBox.setVgrow(lobbyListView, Priority.ALWAYS)

    val lobbyCellFactory = new Callback[ListView[String], ListCell[String]] {
      override def call(param: ListView[String]): ListCell[String] = new ListCell[String] {
        override def updateItem(item: String, empty: Boolean): Unit = {
          super.updateItem(item, empty)
          if (empty || item == null) {
            setText(null)
            setStyle("-fx-background-color: #242440;")
          } else {
            setText(item)
            val base = "-fx-text-fill: #dde; -fx-font-size: 14; -fx-padding: 10 12;"
            if (isSelected) {
              setStyle(s"-fx-background-color: #3a3a6e; $base -fx-border-color: #4a9eff; -fx-border-width: 0 0 0 3;")
            } else if (getIndex % 2 == 0) {
              setStyle(s"-fx-background-color: #242440; $base")
            } else {
              setStyle(s"-fx-background-color: #2a2a48; $base")
            }
          }
        }
      }
    }
    lobbyListView.setCellFactory(lobbyCellFactory)

    val joinBtn = new Button("Join Selected")
    addHoverEffect(joinBtn, buttonStyle, buttonHoverStyle)
    joinBtn.setDisable(true)

    lobbyListView.getSelectionModel.selectedIndexProperty().addListener((_, _, newVal) => {
      joinBtn.setDisable(newVal.intValue() < 0)
    })

    // Separator
    val sep = new Region()
    sep.setMinHeight(1)
    sep.setMaxHeight(1)
    sep.setStyle("-fx-background-color: #3a3a5e;")

    // Create lobby form in a card
    val createCard = new VBox(12)
    createCard.setPadding(new Insets(16, 20, 16, 20))
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

    val statusLabel = new Label("")
    statusLabel.setTextFill(Color.web("#8899bb"))
    statusLabel.setFont(Font.font("System", 13))

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

    val formRow1 = new HBox(8, new Label("Name") { setStyle(sectionHeaderStyle); setMinWidth(50) }, nameField)
    formRow1.setAlignment(Pos.CENTER_LEFT)
    HBox.setHgrow(nameField, Priority.ALWAYS)
    val formRow2 = new HBox(8, new Label("Map") { setStyle(sectionHeaderStyle); setMinWidth(50) }, mapCombo)
    formRow2.setAlignment(Pos.CENTER_LEFT)
    HBox.setHgrow(mapCombo, Priority.ALWAYS)
    val formRow3 = new HBox(8, new Label("Time") { setStyle(sectionHeaderStyle); setMinWidth(50) }, durationCombo)
    formRow3.setAlignment(Pos.CENTER_LEFT)
    HBox.setHgrow(durationCombo, Priority.ALWAYS)

    createCard.getChildren.addAll(createLabel, formRow1, formRow2, formRow3, createBtn)

    root.getChildren.addAll(titleBar, lobbyListView, joinBtn, sep, createCard, statusLabel)

    val scene = new Scene(root, 560, 640)
    stage.setScene(scene)

    // Auto-refresh on show
    client.requestLobbyList()
  }

  private def showLobbyRoom(stage: Stage): Unit = {
    val root = new VBox(0)
    root.setAlignment(Pos.TOP_CENTER)
    root.setStyle(darkBg)

    // Header
    val headerBox = new VBox(4)
    headerBox.setAlignment(Pos.CENTER)
    headerBox.setPadding(new Insets(28, 24, 16, 24))

    val lobbyTitle = new Label(client.currentLobbyName)
    lobbyTitle.setFont(Font.font("System", FontWeight.BOLD, 28))
    lobbyTitle.setTextFill(Color.WHITE)

    val playersLabel = new Label(s"Players: ${client.currentLobbyPlayerCount}/${client.currentLobbyMaxPlayers}")
    playersLabel.setFont(Font.font("System", FontWeight.BOLD, 18))
    playersLabel.setTextFill(Color.web("#4a9eff"))

    headerBox.getChildren.addAll(lobbyTitle, playersLabel)

    // Info card
    val infoCard = new VBox(12)
    infoCard.setPadding(new Insets(20, 24, 20, 24))
    infoCard.setMaxWidth(380)
    infoCard.setStyle(cardBg)

    val mapLabel = new Label(s"Map: ${WorldRegistry.getDisplayName(client.currentLobbyMapIndex)}")
    mapLabel.setFont(Font.font("System", 15))
    mapLabel.setTextFill(Color.web("#ccd"))

    val durationLabel = new Label(s"Duration: ${client.currentLobbyDuration} min")
    durationLabel.setFont(Font.font("System", 15))
    durationLabel.setTextFill(Color.web("#ccd"))

    val waitingLabel = new Label("Waiting for host to start...")
    waitingLabel.setFont(Font.font("System", 14))
    waitingLabel.setTextFill(Color.web("#667"))

    val buttonBox = new HBox(12)
    buttonBox.setAlignment(Pos.CENTER)
    buttonBox.setPadding(new Insets(8, 0, 0, 0))

    val leaveBtn = new Button("Leave")
    addHoverEffect(leaveBtn, buttonRedStyle, buttonRedHoverStyle)

    leaveBtn.setOnAction(_ => {
      client.leaveLobby()
      showLobbyBrowser(stage)
    })

    buttonBox.getChildren.add(leaveBtn)

    // Host-only controls
    if (client.isLobbyHost) {
      waitingLabel.setText("You are the host")
      waitingLabel.setTextFill(Color.web("#2ecc71"))

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

      mapCombo.setOnAction(_ => {
        val dur = durationCombo.getSelectionModel.getSelectedItem.split(" ")(0).toInt
        client.updateLobbyConfig(mapCombo.getSelectionModel.getSelectedIndex, dur)
      })
      durationCombo.setOnAction(_ => {
        val dur = durationCombo.getSelectionModel.getSelectedItem.split(" ")(0).toInt
        client.updateLobbyConfig(mapCombo.getSelectionModel.getSelectedIndex, dur)
      })

      val startBtn = new Button("Start Game")
      addHoverEffect(startBtn, buttonGreenStyle, buttonGreenHoverStyle)
      startBtn.setFont(Font.font("System", FontWeight.BOLD, 16))
      startBtn.setMaxWidth(Double.MaxValue)
      startBtn.setOnAction(_ => {
        client.startGame()
      })

      val row1 = new HBox(8, new Label("Map") { setStyle(sectionHeaderStyle); setMinWidth(50) }, mapCombo)
      row1.setAlignment(Pos.CENTER_LEFT)
      HBox.setHgrow(mapCombo, Priority.ALWAYS)
      val row2 = new HBox(8, new Label("Time") { setStyle(sectionHeaderStyle); setMinWidth(50) }, durationCombo)
      row2.setAlignment(Pos.CENTER_LEFT)
      HBox.setHgrow(durationCombo, Priority.ALWAYS)

      infoCard.getChildren.addAll(waitingLabel, configLabel, row1, row2, startBtn)
      buttonBox.getChildren.add(new Region()) // spacing
    } else {
      infoCard.getChildren.addAll(mapLabel, durationLabel, waitingLabel)
    }

    root.getChildren.addAll(headerBox, infoCard, new Region() { setMinHeight(16) }, buttonBox)

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

    val scene = new Scene(root, 460, 420)
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
    val root = new VBox(0)
    root.setAlignment(Pos.TOP_CENTER)
    root.setStyle(darkBg)

    // Title section
    val titleBox = new VBox(4)
    titleBox.setAlignment(Pos.CENTER)
    titleBox.setPadding(new Insets(32, 0, 20, 0))

    val title = new Label("Game Over")
    title.setFont(Font.font("System", FontWeight.BOLD, 36))
    title.setTextFill(Color.WHITE)
    title.setStyle("-fx-effect: dropshadow(gaussian, rgba(74, 158, 255, 0.3), 12, 0, 0, 0);")

    val subtitle = new Label("Final Scoreboard")
    subtitle.setFont(Font.font("System", 15))
    subtitle.setTextFill(Color.web("#667"))

    titleBox.getChildren.addAll(title, subtitle)

    // Scoreboard card
    val scoreCard = new VBox(0)
    scoreCard.setMaxWidth(440)
    scoreCard.setStyle(cardBg)

    // Header row
    val header = new HBox(0)
    header.setAlignment(Pos.CENTER_LEFT)
    header.setPadding(new Insets(10, 16, 10, 16))
    header.setStyle("-fx-border-color: transparent transparent #3a3a5e transparent; -fx-border-width: 0 0 1 0;")
    val hRank = new Label("RANK")
    hRank.setMinWidth(50); hRank.setStyle(sectionHeaderStyle)
    val hPlayer = new Label("PLAYER")
    hPlayer.setMinWidth(140); hPlayer.setStyle(sectionHeaderStyle)
    val hKills = new Label("KILLS")
    hKills.setMinWidth(60); hKills.setStyle(sectionHeaderStyle)
    val hDeaths = new Label("DEATHS")
    hDeaths.setMinWidth(60); hDeaths.setStyle(sectionHeaderStyle)
    header.getChildren.addAll(hRank, hPlayer, hKills, hDeaths)
    scoreCard.getChildren.add(header)

    import scala.jdk.CollectionConverters._
    var rowIndex = 0
    client.scoreboard.asScala.foreach { entry =>
      val row = new HBox(0)
      row.setAlignment(Pos.CENTER_LEFT)
      row.setPadding(new Insets(10, 16, 10, 16))

      val isLocal = entry.playerId.equals(client.getLocalPlayerId)
      val rowBg = if (isLocal) {
        "-fx-background-color: rgba(74, 158, 255, 0.12); -fx-border-color: transparent transparent #3a3a5e transparent; -fx-border-width: 0 0 1 0;"
      } else if (rowIndex % 2 == 0) {
        "-fx-background-color: transparent; -fx-border-color: transparent transparent #2a2a45 transparent; -fx-border-width: 0 0 1 0;"
      } else {
        "-fx-background-color: rgba(255,255,255,0.02); -fx-border-color: transparent transparent #2a2a45 transparent; -fx-border-width: 0 0 1 0;"
      }
      row.setStyle(rowBg)

      val rankText = if (entry.rank == 1) "#1" else s"#${entry.rank}"
      val rankLabel = new Label(rankText)
      rankLabel.setMinWidth(50)
      rankLabel.setTextFill(entry.rank match {
        case 1 => Color.web("#ffd700")
        case 2 => Color.web("#c0c0c0")
        case 3 => Color.web("#cd7f32")
        case _ => Color.web("#889")
      })
      rankLabel.setFont(Font.font("System", FontWeight.BOLD, 18))

      val nameStr = if (isLocal) s"${client.playerName} (you)" else {
        val p = client.getPlayers.get(entry.playerId)
        if (p != null) p.getName else entry.playerId.toString.substring(0, 8)
      }
      val nameLabel = new Label(nameStr)
      nameLabel.setMinWidth(140)
      nameLabel.setTextFill(if (isLocal) Color.web("#4a9eff") else Color.web("#dde"))
      nameLabel.setFont(Font.font("System", FontWeight.BOLD, 15))

      val killsLabel = new Label(entry.kills.toString)
      killsLabel.setMinWidth(60)
      killsLabel.setTextFill(Color.web("#2ecc71"))
      killsLabel.setFont(Font.font("System", FontWeight.BOLD, 16))

      val deathsLabel = new Label(entry.deaths.toString)
      deathsLabel.setMinWidth(60)
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
    btnBox.setPadding(new Insets(20, 0, 0, 0))
    btnBox.getChildren.add(returnBtn)

    root.getChildren.addAll(titleBox, scoreCard, btnBox)

    val scene = new Scene(root, 520, 520)
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
