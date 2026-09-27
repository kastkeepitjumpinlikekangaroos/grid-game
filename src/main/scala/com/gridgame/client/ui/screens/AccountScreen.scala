package com.gridgame.client.ui.screens

import com.gridgame.client.i18n.Messages
import com.gridgame.common.WorldRegistry

import javafx.application.Platform
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.ListCell
import javafx.scene.control.ListView
import javafx.util.Callback
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.scene.text.Font
import javafx.scene.text.FontWeight
import javafx.stage.Stage
import com.gridgame.client.game.MatchHistoryEntry

import com.gridgame.client.ui.Theme._

/** Our account: lifetime stats, the ranked rating and the recent matches. */
private[client] final class AccountScreen(app: Screens) {
  import app._

  def show(stage: Stage): Unit = {
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
}
