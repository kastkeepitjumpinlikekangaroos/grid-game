package com.gridgame.client.ui.screens

import com.gridgame.client.i18n.Messages
import com.gridgame.common.Constants
import com.gridgame.common.WorldRegistry
import com.gridgame.common.model.LobbyInfo
import com.gridgame.common.protocol.LobbyFailure

import javafx.application.Platform
import javafx.collections.FXCollections
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.canvas.Canvas
import javafx.scene.control.Button
import javafx.scene.control.ComboBox
import javafx.scene.control.Label
import javafx.scene.control.ListCell
import javafx.scene.control.ListView
import javafx.scene.control.TextField
import javafx.util.Callback
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.scene.text.Font
import javafx.scene.text.FontWeight
import javafx.stage.Stage

import com.gridgame.client.ui.Theme._

/** The lobby browser: the open lobbies to join, a new lobby to create, and the way to practice, the ranked queue, the leaderboard and the account. */
private[client] final class LobbyBrowserScreen(app: Screens) {
  import app._

  def show(stage: Stage, notice: String = ""): Unit = {
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
}
