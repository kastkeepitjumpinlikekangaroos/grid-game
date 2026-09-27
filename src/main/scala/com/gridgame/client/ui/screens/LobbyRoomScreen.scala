package com.gridgame.client.ui.screens

import com.gridgame.client.ui.{CharacterSelectionPanel, ViewportCache}
import com.gridgame.client.i18n.Messages
import com.gridgame.common.WorldRegistry
import com.gridgame.common.model.TeamAssignment

import javafx.application.Platform
import javafx.collections.FXCollections
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.canvas.Canvas
import javafx.scene.control.Button
import javafx.scene.control.ComboBox
import javafx.scene.control.ScrollPane
import javafx.scene.control.Label
import javafx.scene.control.TextField
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.scene.text.Font
import javafx.scene.text.FontWeight
import javafx.stage.Stage
import com.gridgame.client.game.LobbyMember

import com.gridgame.client.ui.Theme._

/** A lobby's room: its members and the characters they have picked, the map, the time and the mode (the host's to set), the teams, chat, and starting the match. */
private[client] final class LobbyRoomScreen(app: Screens) {
  import app._

  def show(stage: Stage): Unit = {
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
}
