package com.gridgame.client.ui.screens

import com.gridgame.client.ui.{CharacterSelectionPanel, Icons, MapPreview}
import com.gridgame.client.i18n.Messages
import com.gridgame.client.game.LobbyMember
import com.gridgame.common.WorldRegistry
import com.gridgame.common.model.TeamAssignment

import javafx.application.Platform
import javafx.collections.FXCollections
import javafx.geometry.{Insets, Pos}
import javafx.scene.Node
import javafx.scene.control.{ComboBox, ScrollPane}
import javafx.scene.layout.{FlowPane, HBox, Priority, Region, VBox}
import javafx.scene.shape.Circle
import javafx.scene.text.{Text, TextFlow}
import javafx.stage.Stage

import com.gridgame.client.ui.Theme._
import com.gridgame.client.ui.Widgets._

/** A lobby's room: its members and the characters they have picked, the map, the time and the mode (the host's to set), the teams, chat, and starting the match. */
private[client] final class LobbyRoomScreen(app: Screens) {
  import app._

  def show(stage: Stage): Unit = {
    switchScreen()
    import scala.jdk.CollectionConverters._

    def modeText: String =
      if (client.currentLobbyGameMode == 1) Messages.t("Teams ({0}v{0})", client.currentLobbyTeamSize.toString)
      else Messages.t("Free-For-All")
    def mapName: String = WorldRegistry.getDisplayName(client.currentLobbyMapIndex)
    def timeText: String = Messages.t("{0} min", client.currentLobbyDuration.toString)

    // ── The heading: the lobby and its map, what kind of match it is, and the way out (and, for
    // the host, in) ──
    // Loading a map for its preview parses the world file, so it is redrawn only when the map
    // changes, not on every join, leave or character pick.
    val preview = new VBox()
    var previewedMap = -1
    def showPreview(): Unit = if (client.currentLobbyMapIndex != previewedMap) {
      previewedMap = client.currentLobbyMapIndex
      preview.getChildren.setAll(MapPreview.view(previewedMap, 80, 80, 14))
    }
    showPreview()

    val modeChip = chip(modeText, "violet")
    val mapChip = chip(mapName, "sky", Icons.MapPin, Palette.Sky)
    val timeChip = chip(timeText, "grey", Icons.Clock, Palette.Muted)
    val chips = row(8, modeChip, mapChip, timeChip)
    val title = h1(client.currentLobbyName)
    val leaveBtn = button(Messages.t("Leave"), "plain")
    val headingRight = new HBox(12, leaveBtn)
    headingRight.setAlignment(Pos.CENTER_RIGHT)
    val heading = row(18, preview, new VBox(8, title, chips), grow(), headingRight)

    /** A setting the host can change, drawn as the chip it is for everyone else, with a drop-down's arrow. */
    def settingChip[T](combo: ComboBox[T], tone: String, tip: String): ComboBox[T] = {
      combo.getStyleClass.addAll("chip-select", s"chip-select-$tone")
      combo.setTooltip(new javafx.scene.control.Tooltip(tip))
      combo
    }

    // Host-only controls, refreshed from the server's view of the lobby on every update
    var refreshHostControls: () => Unit = () => ()

    val playersChip = chip("", "sky")
    val botButtons = new HBox(6)
    botButtons.setAlignment(Pos.CENTER_RIGHT)
    val leftPanel = new VBox(16)

    if (!client.isLobbyHost) {
      // Everything a guest could read of the settings is in the heading; what is left is to wait
      val waitingLabel = text(Messages.t("Waiting for host to start..."), "lead-on-sky")
      headingRight.getChildren.add(0, row(8, Icons.node(Icons.Clock, 18, Palette.Ink), waitingLabel))
    } else {
      // The host sets the match up in the heading itself: its chips are drop-downs
      val mapCombo = settingChip(new ComboBox[String](FXCollections.observableArrayList(WorldRegistry.displayNames: _*)),
        "sky", Messages.t("Map"))
      mapCombo.getSelectionModel.select(client.currentLobbyMapIndex)

      val durationCombo = settingChip(makeDurationCombo(client.currentLobbyDuration), "grey", Messages.t("Time"))
      durationCombo.setMaxWidth(Region.USE_PREF_SIZE)

      val gameModeCombo = settingChip(new ComboBox[String](FXCollections.observableArrayList(Messages.t("Free-For-All"), Messages.t("Teams"))),
        "violet", Messages.t("Mode"))
      gameModeCombo.getSelectionModel.select(if (client.currentLobbyGameMode == 1) 1 else 0)

      val teamSizes = Seq(2, 3, 4)
      val teamSizeCombo = settingChip(new ComboBox[String](FXCollections.observableArrayList(teamSizes.map(n => s"${n}v$n"): _*)),
        "violet", Messages.t("Size"))
      teamSizeCombo.getSelectionModel.select(Math.max(0, teamSizes.indexOf(client.currentLobbyTeamSize)))

      chips.getChildren.setAll(gameModeCombo, teamSizeCombo, mapCombo, durationCombo,
        chip(Messages.t("You are the host"), "leaf", Icons.Crown, Palette.Leaf))

      // Set while showing the server's values, so selecting them doesn't send another update
      var syncing = false
      val sendConfigUpdate = () => {
        if (!syncing) {
          val ts = teamSizes(Math.max(0, teamSizeCombo.getSelectionModel.getSelectedIndex))
          client.updateLobbyConfig(mapCombo.getSelectionModel.getSelectedIndex, selectedDuration(durationCombo),
            gameModeCombo.getSelectionModel.getSelectedIndex.toByte, ts)
        }
      }

      val startBtn = button(Messages.t("Start Game"), "sun", "lg")
      startBtn.setGraphic(Icons.node(Icons.Play, 16, javafx.scene.paint.Color.web("#5a3300")))
      startBtn.setOnAction(_ => client.startGame())
      headingRight.getChildren.add(startBtn)

      val addBotBtn = button(Messages.t("Add Bot"), "plain", "sm")
      addBotBtn.setGraphic(Icons.node(Icons.Plus, 14, Palette.Ink))
      addBotBtn.setOnAction(_ => client.addBot())
      val removeBotBtn = button(Messages.t("Remove Bot"), "plain", "sm")
      removeBotBtn.setGraphic(Icons.node(Icons.Minus, 14, Palette.Ink))
      removeBotBtn.setOnAction(_ => client.removeBot())
      botButtons.getChildren.addAll(addBotBtn, removeBotBtn)

      def showTeamSize(isTeams: Boolean): Unit = { teamSizeCombo.setVisible(isTeams); teamSizeCombo.setManaged(isTeams) }

      mapCombo.setOnAction(_ => {
        sendConfigUpdate()
        previewedMap = mapCombo.getSelectionModel.getSelectedIndex
        preview.getChildren.setAll(MapPreview.view(previewedMap, 80, 80, 14))
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
    }

    // ── Who is here: split into the teams the match will deal in Teams mode ──
    val roster = new VBox(10)

    def member(m: LobbyMember): HBox = {
      val isLocal = m.id == client.getLocalPlayerId
      val name = text(if (isLocal) s"${m.name} ${Messages.t("(you)")}" else m.name, "member-name")
      val pill = row(8, avatar(m.name, 26, TeamAssignment.isBot(m.id)), name)
      pill.getStyleClass.add("member")
      if (isLocal) pill.getStyleClass.add("member-self")
      pill
    }

    def pills(members: Seq[LobbyMember]): FlowPane = {
      val f = new FlowPane(6, 6)
      members.foreach(m => f.getChildren.add(member(m)))
      f
    }

    def rebuildRoster(): Unit = {
      playersChip.setText(s"${client.currentLobbyPlayerCount}/${client.currentLobbyMaxPlayers}")
      roster.getChildren.clear()
      if (client.currentLobbyGameMode == 1) {
        val teams = client.previewTeams
        def team(team: Byte, header: String): VBox = {
          val colour = Palette.team(team)
          val name = text(header, "team-name")
          name.setStyle(s"-fx-text-fill: ${hex(colour)};")
          new VBox(6, row(7, new Circle(5, colour), name), pills(teams.filter(_._2 == team).map(_._1)))
        }
        roster.getChildren.addAll(team(1, Messages.t("Team 1 (Blue)")), team(2, Messages.t("Team 2 (Red)")))
      } else {
        roster.getChildren.add(pills(client.lobbyMembers.asScala.toSeq))
      }
    }
    rebuildRoster()
    val playersCard = card(cardHeader(Messages.t("Players"), playersChip), roster)
    if (client.isLobbyHost) playersCard.getChildren.add(botButtons)
    botButtons.setAlignment(Pos.CENTER_LEFT)
    playersCard.setSpacing(12)

    // ── Chat ──
    val chatLines = new VBox(4)
    chatLines.setPadding(new Insets(10, 12, 10, 12))
    val chatScroll = new ScrollPane(chatLines)
    chatScroll.setFitToWidth(true)
    chatScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER)
    chatScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED)
    chatScroll.getStyleClass.add("chat-log")
    chatScroll.setPrefHeight(170)
    chatScroll.setMinHeight(96)
    VBox.setVgrow(chatScroll, Priority.ALWAYS)

    val chatInput = field(Messages.t("Type a message..."))
    val sendBtn = iconButton(Icons.Send, Messages.t("Send"), Palette.Sky)
    HBox.setHgrow(chatInput, Priority.ALWAYS)
    def send(): Unit = {
      val text = chatInput.getText.trim
      if (text.nonEmpty) {
        client.sendChatMessage(text, com.gridgame.common.protocol.ChatScope.LOBBY)
        chatInput.clear()
      }
    }
    chatInput.setOnAction(_ => send())
    sendBtn.setOnAction(_ => send())
    val chatCard = card(cardHeader(Messages.t("Chat")), chatScroll, row(6, chatInput, sendBtn))
    chatCard.setSpacing(10)
    VBox.setVgrow(chatCard, Priority.ALWAYS)

    leftPanel.getChildren.addAll(playersCard, chatCard)
    leftPanel.setMinWidth(340)
    leftPanel.setPrefWidth(340)
    leftPanel.setMaxWidth(340)

    // ── The character to play ──
    val charPanel = new CharacterSelectionPanel(
      () => client.selectedCharacterId,
      id => client.selectCharacter(id)
    )
    val charSection = charPanel.createPanel()
    HBox.setHgrow(charSection, Priority.ALWAYS)
    stopCurrentScreen = () => charPanel.stop()

    val leave = () => client.leaveLobby()
    leaveBtn.setOnAction(_ => {
      leave()
      showLobbyBrowser(stage)
    })

    val columns = new HBox(24, leftPanel, charSection)
    columns.setAlignment(Pos.TOP_LEFT)
    val body = new VBox(20, heading, columns)

    // Wire up listeners
    def refreshRoom(): Unit = {
      modeChip.setText(modeText)
      mapChip.setText(mapName)
      timeChip.setText(timeText)
      showPreview()
      rebuildRoster()
      refreshHostControls()
    }
    client.lobbyUpdatedListener = () => Platform.runLater(() => refreshRoom())

    def renderChat(): Unit = {
      chatLines.getChildren.clear()
      client.chatMessages.asScala.foreach { entry =>
        val sender = entry(1).asInstanceOf[String]
        val msg = entry(2).asInstanceOf[String]
        val line =
          if (sender.isEmpty) {
            val t = new Text(msg)
            t.getStyleClass.add("chat-system")
            new TextFlow(t)
          } else {
            val who = new Text(sender + "  ")
            who.getStyleClass.add("chat-name")
            who.setStyle(s"-fx-fill: ${hex(if (sender == client.playerName) Palette.Sky else Palette.forName(sender))};")
            val said = new Text(msg)
            said.getStyleClass.add("chat-text")
            new TextFlow(who, said)
          }
        chatLines.getChildren.add(line)
      }
      chatScroll.layout()
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

    Chrome.page(app, stage, Chrome.Tab.Lobbies, body, leave, maxWidth = 1560, subPage = true).show(stage)
  }
}
