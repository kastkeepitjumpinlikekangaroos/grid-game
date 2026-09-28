package com.gridgame.client.ui.screens

import com.gridgame.client.i18n.{I18n, Messages}
import com.gridgame.client.ui.{Icons, SpriteGenerator}
import com.gridgame.common.model.{CharacterDef, Direction, ScoreEntry}

import javafx.geometry.{Insets, Pos}
import javafx.scene.layout.{HBox, VBox}
import javafx.scene.paint.{Color, CycleMethod, LinearGradient, Stop}
import javafx.scene.shape.Circle
import javafx.stage.Stage

import com.gridgame.client.ui.Theme._
import com.gridgame.client.ui.Widgets._

/** A finished match's scoreboard (practice's results, or a ranked match's rating change), and the way back. */
private[client] final class ScoreboardScreen(app: Screens) {
  import app._

  def show(stage: Stage): Unit = {
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

    def gradient(top: String, mid: String, bottom: String) = new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
      new Stop(0, Color.web(top)), new Stop(0.5, Color.web(mid)), new Stop(1, Color.web(bottom)))
    val gold = gradient("#fff6b8", "#ffd84a", "#ffb31f")
    val sky = gradient("#ffffff", "#e3f1ff", "#b9dcff")
    val leaf = gradient("#effff0", "#b8f0a8", "#72d35c")
    val dusk = gradient("#fdfbff", "#dfe6f2", "#aab8cf")
    val ink = Color.web("#1c3558")

    // Headline: the player's own result, which is what they came to this screen for
    val title =
      if (isPractice) displayTitle(Messages.t("Practice Complete"), 58, leaf, Color.web("#1d5a2a"))
      else localEntry match {
        case Some(_) if isDraw => displayTitle(Messages.t("Draw"), 64, sky, ink)
        case Some(e) if e.rank == 1 => displayTitle(Messages.t("Victory!"), 72, gold, Color.web("#6b3d00"))
        case Some(_) if teamMode => displayTitle(Messages.t("Defeat"), 64, dusk, ink)
        case _ => displayTitle(Messages.t("Game Over"), 64, sky, ink)
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
    val subtitle = text(subtitleText, "hero-pill")

    val header = new VBox(6, title, subtitle)
    header.setAlignment(Pos.CENTER)
    val body = new VBox(18, header)
    body.setAlignment(Pos.TOP_CENTER)

    // Ranked rating change — only shown when the server pushed a post-match STATS
    // (i.e. the just-ended match was ranked with >=2 humans).
    client.pendingEloChange.foreach { case (oldElo, newElo) =>
      val delta = newElo - oldElo
      val (tone, deltaText) =
        if (delta > 0) ("leaf", s"+$delta")
        else if (delta < 0) ("berry", delta.toString)
        else ("grey", "±0")
      val change = row(14,
        Icons.node(Icons.Trophy, 26, Palette.Gold),
        new VBox(0, eyebrow(Messages.t("RANKED ELO")),
          row(10, text(oldElo.toString, "number", "text-faint"), text("→", "muted"), text(newElo.toString, "stat-value", "text-gold"))),
        grow(), chip(deltaText, tone))
      val eloCard = card(change)
      eloCard.getStyleClass.add("card-tight")
      eloCard.setPadding(new Insets(12, 20, 16, 18))
      eloCard.setMaxWidth(420)
      body.getChildren.add(eloCard)
    }

    val cols = Seq(Col(Messages.t("RANK"), 70), Col(Messages.t("PLAYER"), 0),
      Col(Messages.t("KILLS"), 90, Pos.CENTER_RIGHT), Col(Messages.t("DEATHS"), 90, Pos.CENTER_RIGHT))
    val table = new VBox(4, tableHead(cols))

    var lastTeamId = -1
    var placeInTeam = 0
    rows.foreach { entry =>
      // Team header: name, total kills, and the result, above each team's rows
      if (teamMode && entry.teamId != lastTeamId) {
        lastTeamId = entry.teamId
        placeInTeam = 0
        val colour = Palette.team(entry.teamId)
        val teamLabel = text(teamName(entry.teamId), "team-name")
        teamLabel.setStyle(s"-fx-text-fill: ${hex(colour)}; -fx-font-size: 16px;")
        val killsTotal = chip(Messages.t("{0} kills", teamKills(entry.teamId).toString), "grey", Icons.Sword, Palette.Muted)
        val teamHeader = row(10, new Circle(6, colour), teamLabel, killsTotal, grow())
        if (!isDraw && teamRanks(entry.teamId) == 1)
          teamHeader.getChildren.add(chip(Messages.t("WINNER"), "gold", Icons.Crown, Palette.Gold))
        teamHeader.setPadding(new Insets(if (table.getChildren.size > 1) 14 else 4, 14, 4, 14))
        table.getChildren.add(teamHeader)
      }
      placeInTeam += 1

      val isLocal = entry.playerId.equals(localId)
      // FFA: the finishing place, medal-coloured, shared by exact ties. Teams: the place is
      // the team's (in its header), so rows show each player's standing within their team.
      val place = if (teamMode) text(placeInTeam.toString, "medal") else medal(entry.rank)

      // Character first, as the match showed everyone; then who was playing it
      val player = if (isLocal) null else client.findPlayer(entry.playerId)
      val charDef =
        if (isLocal) Some(client.getSelectedCharacterDef)
        else if (player != null) Some(CharacterDef.get(player.getCharacterId))
        else None
      val characterName = charDef.map(I18n.characterName).getOrElse("?")
      val playerName =
        if (isLocal) client.playerName
        else if (player != null) player.getName
        else entry.playerId.toString.substring(0, 8)
      val portrait = charDef.map(d => SpriteGenerator.portraitView(d.id.id, Direction.Down, 36))
        .getOrElse(avatar(playerName, 30))
      val charLabel = text(if (isLocal) s"$characterName ${Messages.t("(you)")}" else characterName, "h3")
      val left = !isLocal && client.playerLeftMatch(entry.playerId)
      val playerLabel = text(if (left) s"$playerName  ${Messages.t("(left)")}" else playerName, "muted")
      val names = new HBox(10, charLabel, playerLabel)
      names.setAlignment(Pos.BASELINE_LEFT)
      val who = row(12, portrait, names)

      val r = tableRow(cols, Seq(place, who, text(entry.kills.toString, "number", "text-leaf"),
        text(entry.deaths.toString, "number", "text-berry")))
      if (isLocal) r.getStyleClass.add("row-self")
      table.getChildren.add(r)
    }
    val scoreCard = card(table)
    scoreCard.setMaxWidth(820)
    body.getChildren.add(scoreCard)

    // Practice stats row (only shown in practice mode)
    if (isPractice) {
      val accuracy = if (client.practiceShots > 0) (client.practiceHits * 100.0 / client.practiceShots).toInt else 0
      val (bestComboBox, _) = statTile(Messages.t("Best Combo"), client.practiceBestCombo.toString, "gold")
      val (accuracyBox, _) = statTile(Messages.t("Accuracy"), s"$accuracy%", "sky")
      val (totalKillsBox, _) = statTile(Messages.t("Total Kills"), client.killCount.toString, "leaf")
      val statsCard = card(new HBox(12, bestComboBox, accuracyBox, totalKillsBox))
      statsCard.setMaxWidth(820)
      body.getChildren.add(statsCard)
    }

    val backToLobbies = () => {
      client.pendingEloChange = None
      client.returnToLobbyBrowser()
    }
    val returnBtn = button(Messages.t("Return to Lobby"), "sun", "lg")
    returnBtn.setOnAction(_ => {
      backToLobbies()
      showLobbyBrowser(stage)
    })

    // Buttons sit right under the results; pinned to the bottom edge they were cut off
    val btnBox = new HBox(12)
    btnBox.setAlignment(Pos.CENTER)
    btnBox.setPadding(new Insets(6, 0, 0, 0))
    if (isPractice) {
      val practiceAgainBtn = button(Messages.t("Practice Again"), "leaf", "lg")
      practiceAgainBtn.setOnAction(_ => {
        client.returnToLobbyBrowser()
        showPracticeSetup(stage)
      })
      btnBox.getChildren.add(practiceAgainBtn)
    }
    btnBox.getChildren.add(returnBtn)
    body.getChildren.add(btnBox)

    // The whole page scrolls when a big lobby outgrows the window
    Chrome.page(app, stage, Chrome.Tab.Nowhere, body, backToLobbies, maxWidth = 900).show(stage)
  }
}
