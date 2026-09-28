package com.gridgame.client.ui.screens

import com.gridgame.client.i18n.Messages
import com.gridgame.client.ui.Icons
import com.gridgame.client.game.MatchHistoryEntry
import com.gridgame.common.WorldRegistry

import javafx.application.Platform
import javafx.geometry.Pos
import javafx.scene.layout.{HBox, VBox}
import javafx.stage.Stage

import com.gridgame.client.ui.Theme._
import com.gridgame.client.ui.Widgets._

/** Our account: lifetime stats, the ranked rating and the recent matches. */
private[client] final class AccountScreen(app: Screens) {
  import app._

  def show(stage: Stage): Unit = {
    switchScreen()

    // Who: the avatar the top bar shows, big, and the rating
    val rating = chip("", "gold", Icons.Star, Palette.Gold)
    val who = row(18, avatar(client.playerName, 68),
      new VBox(6, h1(client.playerName), row(10, rating, text(Messages.t("Practice sessions don't count toward stats."), "lead-on-sky"))))

    val (killsBox, killsValue) = statTile(Messages.t("Kills"), "-", "leaf")
    val (deathsBox, deathsValue) = statTile(Messages.t("Deaths"), "-", "berry")
    val (matchesBox, matchesValue) = statTile(Messages.t("Matches"), "-", "sky")
    val (winsBox, winsValue) = statTile(Messages.t("Wins"), "-", "gold")
    val (eloBox, eloValue) = statTile(Messages.t("Rating"), "-")
    val tiles = new HBox(12, killsBox, deathsBox, matchesBox, winsBox, eloBox)
    val statsCard = card(cardHeader(Messages.t("All-time stats")), tiles)

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

    val cols = Seq(Col(Messages.t("Result"), 110), Col(Messages.t("Mode"), 150), Col(Messages.t("Map"), 0),
      Col(Messages.t("K / D"), 90, Pos.CENTER_RIGHT), Col(Messages.t("Length"), 90, Pos.CENTER_RIGHT),
      Col(Messages.t("Played"), 130, Pos.CENTER_RIGHT))

    def historyRow(e: MatchHistoryEntry) = {
      // A Teams rank is the team's place, so it reads as a result, not "#1/6"
      val result =
        if (isTeamMatch(e)) {
          if (e.rank == 1) chip(Messages.t("Victory"), "leaf") else chip(Messages.t("Defeat"), "berry")
        } else {
          val place = chip(s"#${e.rank}/${e.totalPlayers}", if (e.rank == 1 && e.matchType != 5) "gold" else "grey")
          if (e.rank == 1 && e.matchType != 5) place.setGraphic(Icons.node(Icons.Crown, 12, Palette.Gold))
          place
        }
      val mode = text(matchTypeLabel(e.matchType), "strong")
      val kd = new HBox(text(e.kills.toString, "number", "text-leaf"), text(" / ", "muted"), text(e.deaths.toString, "number", "text-berry"))
      kd.setAlignment(Pos.BASELINE_RIGHT)
      tableRow(cols, Seq(result, mode, text(WorldRegistry.getDisplayName(e.mapIndex), "body"), kd,
        text(Messages.t("{0} min", e.durationMinutes.toString), "body"),
        text(dateFormat.format(new java.util.Date(e.playedAt * 1000L)), "muted")))
    }

    val rows = new VBox(4)
    val historyCard = card(cardHeader(Messages.t("Recent matches")), tableHead(cols), rows)
    historyCard.setSpacing(10)

    val body = new VBox(20, who, statsCard, historyCard)
    val page = Chrome.page(app, stage, Chrome.Tab.Profile, body, maxWidth = 1100)
    page.show(stage)

    // Show what we have now and replace it when the response lands. The server rate-limits
    // this query, so reopening the screen within a few seconds gets the cached profile.
    def render(): Unit = {
      if (client.matchHistoryLoaded) {
        killsValue.setText(client.totalKillsStat.toString)
        deathsValue.setText(client.totalDeathsStat.toString)
        matchesValue.setText(client.matchesPlayedStat.toString)
        winsValue.setText(client.winsStat.toString)
        eloValue.setText(client.rankedElo.toString)
      }
      rating.setText(client.rankedElo.toString)
      rating.setVisible(client.matchHistoryLoaded)
      if (client.matchHistory.nonEmpty) rows.getChildren.setAll(client.matchHistory.map(historyRow): _*)
      else if (!client.matchHistoryLoaded) rows.getChildren.setAll(emptyState(Messages.t("Loading..."), Icons.Clock))
      else rows.getChildren.setAll(emptyState(Messages.t("No matches played yet"), Icons.Sword))
    }
    render()
    client.matchHistoryListener = () => Platform.runLater(() => { render(); page.refreshUser() })
    client.requestMatchHistory()
  }
}
