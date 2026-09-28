package com.gridgame.client.ui.screens

import com.gridgame.client.audio.AudioManager
import com.gridgame.client.i18n.Messages
import com.gridgame.client.ui.{Icons, SkyBackdrop, Theme, Widgets}
import com.gridgame.client.ui.Theme.Palette

import javafx.scene.Node
import javafx.scene.control.{Button, Label}
import javafx.scene.layout.{HBox, Region, StackPane}
import javafx.stage.Stage

/**
 * What frames every screen once the player is logged in, as a web site's pages share their header:
 * the sky, and a bar across the top with the game's name, a tab for each place to go (the lobbies,
 * practice, ranked, the leaderboard), the sound switch, and the player's own chip, which opens
 * their profile. A screen hands it its body and says which tab it is.
 */
private[client] object Chrome {
  sealed trait Tab
  object Tab {
    case object Lobbies extends Tab
    case object Practice extends Tab
    case object Ranked extends Tab
    case object Leaderboard extends Tab
    case object Profile extends Tab
    case object Nowhere extends Tab
  }

  /** A screen's page: its root, the body that fades in, and a way to show the player's rating
    * once the server has said it. */
  final class Page(val root: StackPane, val body: Node, refresh: () => Unit) {
    def refreshUser(): Unit = refresh()
    def show(stage: Stage): Unit = Theme.showPage(stage, root, body)
  }

  /**
   * The page for a screen with `active` lit in the top bar. `leave` runs before the bar takes the
   * player anywhere else: out of the lobby they are in, or the ranked queue. A `subPage` (the
   * lobby room, under Lobbies) goes to its tab's page when the tab is clicked; any other page's
   * own tab does nothing.
   */
  def page(app: Screens, stage: Stage, active: Tab, body: Region, leave: () => Unit = () => (),
           maxWidth: Double = 1200, subPage: Boolean = false): Page = {
    import app._
    def go(tab: Tab, to: () => Unit): () => Unit = () =>
      if (tab != active || subPage) { leave(); to() }

    def navTab(label: String, tab: Tab, to: () => Unit): Button = {
      val b = new Button(label)
      b.getStyleClass.add("nav-tab")
      if (tab == active) b.getStyleClass.add("nav-tab-active")
      b.setOnAction(_ => go(tab, to)())
      b
    }

    val tabs = Seq(
      navTab(Messages.t("Lobbies"), Tab.Lobbies, () => showLobbyBrowser(stage)),
      navTab(Messages.t("Practice"), Tab.Practice, () => showPracticeSetup(stage)),
      navTab(Messages.t("Ranked"), Tab.Ranked, () => showRankedQueue(stage)),
      navTab(Messages.t("Leaderboard"), Tab.Leaderboard, () => showLeaderboard(stage)))

    // The player: their avatar, name and, once the server has said it, their rating
    val rating = Widgets.chip("", "gold", Icons.Star, Palette.Gold)
    def refresh(): Unit = {
      rating.setText(client.rankedElo.toString)
      rating.setVisible(client.matchHistoryLoaded)
      rating.setManaged(client.matchHistoryLoaded)
    }
    refresh()
    val user = new HBox(Widgets.avatar(client.playerName, 32), Widgets.text(client.playerName, "user-name"), rating)
    user.getStyleClass.add("user-chip")
    if (active == Tab.Profile) user.getStyleClass.add("user-chip-active")
    user.setOnMouseClicked(_ => go(Tab.Profile, () => showAccountView(stage))())
    javafx.scene.control.Tooltip.install(user, new javafx.scene.control.Tooltip(Messages.t("Profile")))

    val bar = Widgets.topBar(
      Widgets.brand(go(Tab.Lobbies, () => showLobbyBrowser(stage))) +: tabs,
      Seq(soundToggle(), user))
    val (root, column) = Widgets.page(bar, body, maxWidth)
    new Page(root, column, () => refresh())
  }

  /** The page before login: the sky and a bar with the game's name, and whatever `right` holds. */
  def loggedOut(body: Region, right: Seq[Node], sky: SkyBackdrop): Page = {
    val bar = Widgets.topBar(Seq(Widgets.brand(null)), right)
    val (root, column) = Widgets.page(bar, body, 1200, sky)
    new Page(root, column, () => ())
  }

  /** Sound on or off. `AudioManager` is a global, so one switch covers the menus and the match
    * alike, and the choice is saved so it survives a restart. */
  def soundToggle(): Button = {
    def tip: String = if (AudioManager.isMuted) Messages.t("Sound: Off") else Messages.t("Sound: On")
    def icon: Node = Icons.node(if (AudioManager.isMuted) Icons.SoundOff else Icons.SoundOn, 20, Palette.Muted)
    val b = Widgets.iconButton(Icons.SoundOn, tip)
    b.setGraphic(icon)
    b.setOnAction(_ => {
      AudioManager.toggleMuted()
      b.setGraphic(icon)
      b.getTooltip.setText(tip)
    })
    b
  }

  /** A page's heading: its title, a line under it, and anything that belongs at its right end. */
  def heading(title: String, subtitle: String, right: Node*): HBox = {
    val words = new javafx.scene.layout.VBox(2, Widgets.h1(title))
    if (subtitle.nonEmpty) words.getChildren.add(Widgets.text(subtitle, "lead-on-sky"))
    val h = new HBox(16, words, Widgets.grow())
    h.getChildren.addAll(right: _*)
    h.setAlignment(javafx.geometry.Pos.BOTTOM_LEFT)
    h
  }

  /** A notice across the top of a page: why the player is here, or what just went wrong. */
  def notice(): Label = {
    val l = Widgets.text("", "notice")
    l.setWrapText(true)
    l.setMaxWidth(Double.MaxValue)
    l.setVisible(false)
    l.setManaged(false)
    l
  }

  def showNotice(l: Label, message: String, error: Boolean): Unit = {
    l.getStyleClass.removeAll("notice-error")
    if (error) l.getStyleClass.add("notice-error")
    l.setText(message)
    l.setVisible(message.nonEmpty)
    l.setManaged(message.nonEmpty)
  }
}
