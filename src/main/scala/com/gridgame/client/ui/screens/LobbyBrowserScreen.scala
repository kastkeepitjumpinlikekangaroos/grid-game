package com.gridgame.client.ui.screens

import com.gridgame.client.i18n.Messages
import com.gridgame.client.ui.{Icons, MapPreview}
import com.gridgame.common.Constants
import com.gridgame.common.WorldRegistry
import com.gridgame.common.model.LobbyInfo
import com.gridgame.common.protocol.LobbyFailure

import javafx.application.Platform
import javafx.collections.FXCollections
import javafx.geometry.Pos
import javafx.scene.control.{Button, ComboBox, TextField}
import javafx.scene.layout.{HBox, Priority, VBox}
import javafx.stage.Stage

import com.gridgame.client.ui.Theme._
import com.gridgame.client.ui.Widgets._

/** The lobby browser: the open lobbies to join, a new lobby to create, and the way to practice, the ranked queue, the leaderboard and the account. */
private[client] final class LobbyBrowserScreen(app: Screens) {
  import app._

  def show(stage: Stage, notice: String = ""): Unit = {
    switchScreen()

    val refreshBtn = button(Messages.t("Refresh"), "plain", Icons.Refresh, Palette.Ink)
    val status = Chrome.notice()
    if (notice.nonEmpty) Chrome.showNotice(status, notice, error = false)

    // ── The open lobbies ──
    val countChip = chip("0", "sky")
    val rows = new VBox(6)
    val listCard = card(cardHeader(Messages.t("Open lobbies"), countChip), rows)
    HBox.setHgrow(listCard, Priority.ALWAYS)
    listCard.setMinWidth(480)

    // ── A new lobby ──
    val nameField: TextField = field(Messages.t("Lobby name"))
    // The name travels in a fixed-size field; stop at its limit rather than let the packet
    // cut a character in half.
    nameField.setTextFormatter(new javafx.scene.control.TextFormatter[String]((change: javafx.scene.control.TextFormatter.Change) =>
      if (change.getControlNewText.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= Constants.MAX_LOBBY_NAME_LEN) change else null
    ))
    val mapCombo = new ComboBox[String](FXCollections.observableArrayList(WorldRegistry.displayNames: _*))
    mapCombo.getSelectionModel.select(0)
    mapCombo.setMaxWidth(Double.MaxValue)
    val durationCombo = makeDurationCombo(Constants.DEFAULT_GAME_DURATION_MIN)
    val preview = new VBox()
    def showPreview(): Unit = preview.getChildren.setAll(MapPreview.view(mapCombo.getSelectionModel.getSelectedIndex, 320, 200, 14))
    showPreview()
    mapCombo.setOnAction(_ => showPreview())

    val createBtn = button(Messages.t("Create Lobby"), "sun", "lg")
    createBtn.setMaxWidth(Double.MaxValue)

    val createCard = card(
      cardHeader(Messages.t("Create a lobby")),
      labeled(Messages.t("Name"), nameField),
      labeled(Messages.t("Map"), mapCombo),
      preview,
      labeled(Messages.t("Time"), durationCombo),
      createBtn)
    createCard.setMinWidth(364)
    createCard.setPrefWidth(364)
    createCard.setMaxWidth(364)

    // A create or join is in flight until the server answers with JOINED or ACTION_FAILED.
    // The buttons stay disabled meanwhile so a second click can't send a second request.
    var busy = false
    var joinButtons = Seq.empty[(Button, LobbyInfo)]
    def joinable(info: LobbyInfo): Boolean = info.status == 0 && info.playerCount < info.maxPlayers
    val busyTimeout = new javafx.animation.PauseTransition(javafx.util.Duration.seconds(6))
    def setBusy(b: Boolean): Unit = {
      busy = b
      createBtn.setDisable(b)
      joinButtons.foreach { case (btn, info) => btn.setDisable(b || !joinable(info)) }
      if (b) busyTimeout.playFromStart() else busyTimeout.stop()
    }
    busyTimeout.setOnFinished(_ => if (busy) {
      setBusy(false)
      Chrome.showNotice(status, Messages.t("No response from the server, please try again"), error = true)
    })

    def join(info: LobbyInfo): Unit =
      if (!busy) {
        if (info.status != 0) {
          Chrome.showNotice(status, Messages.t("Can't join - game already in progress"), error = true)
        } else if (info.playerCount >= info.maxPlayers) {
          Chrome.showNotice(status, Messages.t("That lobby is full"), error = true)
        } else {
          Chrome.showNotice(status, Messages.t("Joining lobby..."), error = false)
          setBusy(true)
          client.joinLobby(info.lobbyId)
        }
      }

    /** A lobby: its map, its name, what kind of match and how full it is, and the way in. */
    def lobbyRow(info: LobbyInfo): HBox = {
      val waiting = info.status == 0
      val full = info.playerCount >= info.maxPlayers
      val meta = muted(s"${WorldRegistry.getDisplayName(info.mapIndex)}  ·  ${Messages.t("{0} min", info.durationMinutes.toString)}")
      val words = new VBox(2, h3(info.name), meta)
      val mode =
        if (info.gameMode == 1) chip(Messages.t("Teams {0}v{0}", info.teamSize.toString), "violet")
        else chip(Messages.t("FFA"), "sky")
      val state = if (waiting) chip(Messages.t("Waiting"), "leaf") else chip(Messages.t("In Game"), "grey")
      val count = text(s"${info.playerCount}/${info.maxPlayers}", "strong")
      if (full) count.getStyleClass.add("text-berry")
      val players = row(6, Icons.node(Icons.Users, 16, if (full) Palette.Berry else Palette.Muted), count)
      players.setMinWidth(64)
      val joinBtn = button(Messages.t("Join"), "sky", "sm")
      joinBtn.setMinWidth(72)
      joinBtn.setDisable(busy || !joinable(info))
      joinBtn.setOnAction(_ => join(info))
      joinButtons :+= (joinBtn -> info)
      val r = row(14, MapPreview.view(info.mapIndex, 58, 58, 10), words, grow(), mode, state, players, joinBtn)
      r.getStyleClass.addAll("row", "row-hover")
      r.setOnMouseClicked(e => if (e.getClickCount == 2) join(info))
      r
    }

    def updateList(): Unit = {
      joinButtons = Seq.empty
      val lobbies = client.lobbyList
      countChip.setText(lobbies.size.toString)
      if (lobbies.isEmpty) rows.getChildren.setAll(emptyState(Messages.t("No open lobbies yet. Create one!"), Icons.Users))
      else rows.getChildren.setAll(lobbies.map(lobbyRow): _*)
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
      Chrome.showNotice(status, lobbyFailureMessage(reason), error = true)
      // Our listing was wrong about that lobby
      if (reason == LobbyFailure.LOBBY_FULL || reason == LobbyFailure.NOT_JOINABLE) client.requestLobbyList()
    })

    refreshBtn.setOnAction(_ => client.requestLobbyList())

    createBtn.setOnAction(_ => {
      val name = if (nameField.getText.trim.isEmpty) s"${client.playerName}'s Lobby" else nameField.getText.trim
      Chrome.showNotice(status, Messages.t("Creating lobby..."), error = false)
      setBusy(true)
      client.createLobby(name, mapCombo.getSelectionModel.getSelectedIndex, selectedDuration(durationCombo))
    })

    val columns = new HBox(24, listCard, createCard)
    columns.setAlignment(Pos.TOP_LEFT)
    columns.setFillHeight(false) // each card as tall as what is in it
    val body = new VBox(20,
      Chrome.heading(Messages.t("Lobbies"), Messages.t("Pick a lobby to join, or start your own."), refreshBtn),
      status, columns)

    val page = Chrome.page(app, stage, Chrome.Tab.Lobbies, body)
    // The rating in the top bar, once the stats asked for at login land
    client.matchHistoryListener = () => Platform.runLater(() => page.refreshUser())

    stopCurrentScreen = () => busyTimeout.stop()

    page.show(stage)

    // Auto-refresh on show
    client.requestLobbyList()
  }
}
