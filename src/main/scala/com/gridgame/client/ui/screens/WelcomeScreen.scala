package com.gridgame.client.ui.screens

import com.gridgame.client.audio.AudioManager
import com.gridgame.client.ui.{Icons, LoginForm, SkyBackdrop, SpriteGenerator}
import com.gridgame.client.i18n.Messages
import com.gridgame.client.net.{SavedLogin, ServerIdentityChangedException}
import com.gridgame.common.Constants
import com.gridgame.common.model.{CharacterId, Direction}

import javafx.geometry.{Insets, Pos}
import javafx.scene.control.{CheckBox, ComboBox, Label}
import javafx.scene.layout.{HBox, Priority, VBox}
import javafx.scene.paint.{Color, CycleMethod, LinearGradient, Stop}
import javafx.stage.Stage

import com.gridgame.client.ui.Theme._
import com.gridgame.client.ui.Widgets._

/**
 * The login screen: the server to connect to, the account to log in with or create, whether to be
 * remembered, the language, and a notice (why we are back here). With a saved login (a refresh
 * token, ClientMain) it is that login's card instead: continue as that player, or use another
 * account. `resume` presses its button for the player: as the client starts with a login it
 * remembered, and once the connection has dropped (`reconnecting`). And a server that isn't the one
 * met at its address before gets a card of its own (`showServerChanged`).
 */
private[client] final class WelcomeScreen(app: Screens) {
  import app._

  def show(stage: Stage, notice: String = "", resume: Boolean = false, reconnecting: Boolean = false): Unit = {
    switchScreen()
    val saved = savedLogin
    present(stage, if (saved != null) savedCard(stage, saved, notice, resume, reconnecting) else loginForm(stage, notice))
  }

  /**
   * The server isn't the one met at its host and port before: its TLS key isn't the one pinned
   * there (design/networking.md, layer 1). Nothing of the login was sent. Its owner may have set it
   * up again, or someone may be in the middle of the connection: both identities, to compare with
   * the one its owner sees as it starts, and the player decides. Back is the default.
   */
  def showServerChanged(stage: Stage, change: ServerIdentityChangedException): Unit = {
    switchScreen()
    val heading = row(10, Icons.node(Icons.Alert, 22, Palette.Berry), h2(Messages.t("This server's identity has changed")))
    val server = row(6, Icons.node(Icons.Server, 14, Palette.Muted), muted(s"${change.host}:${change.port}"))
    val why = para(Messages.t("It isn't the server you connected to here before, so nothing was sent to it."), "body")
    val ask = para(Messages.t("If its owner set it up again, you can trust its new identity. If not, someone may be listening in on your connection: don't log in."), "muted")
    def fingerprint(f: String): Label = {
      val l = text(f, "fingerprint")
      l.setWrapText(true)
      l.setMaxWidth(Double.MaxValue)
      l
    }
    val back = button(Messages.t("Back"), "sun", "lg")
    back.setDefaultButton(true)
    back.setMaxWidth(Double.MaxValue)
    back.setOnAction(_ => show(stage))
    val trust = button(Messages.t("Trust the new identity"), "berry")
    trust.setMaxWidth(Double.MaxValue)
    trust.setOnAction(_ => trustServer(stage, change))
    present(stage, card(new VBox(6, heading, server), why,
      labeled(Messages.t("Identity before"), fingerprint(change.known)),
      labeled(Messages.t("Identity now"), fingerprint(change.seen)),
      ask, new VBox(10, back, trust)))
  }

  /** The page: the game's name over `form`, on the sky. */
  private def present(stage: Stage, form: VBox): Unit = {
    // The game's name, lettered as MapleStory letters its own
    val gold = new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
      new Stop(0, Color.web("#fff6b8")), new Stop(0.5, Color.web("#ffd84a")), new Stop(1, Color.web("#ffb31f")))
    val title = displayTitle("Grid Game", 78, gold, Color.web("#1c3558"))
    val tagline = text(Messages.t("Multiplayer Arena") + "  ·  v1.0", "hero-pill")

    form.setMaxWidth(420)
    form.setPadding(new Insets(26, 30, 26, 30))
    form.setSpacing(16)

    // Language selector — changing it applies the choice globally, persists it,
    // and rebuilds this screen (via Messages.onLocaleChanged) in the new language.
    val langCombo = new ComboBox[Messages.Lang]()
    Messages.supported.foreach(l => langCombo.getItems.add(l))
    langCombo.setConverter(new javafx.util.StringConverter[Messages.Lang] {
      override def toString(l: Messages.Lang): String = if (l == null) "" else l.nativeName
      override def fromString(s: String): Messages.Lang = null
    })
    langCombo.setTooltip(new javafx.scene.control.Tooltip(Messages.t("Language")))
    langCombo.setValue(Messages.currentLang) // set before handler so it doesn't fire
    langCombo.setOnAction(_ => {
      val l = langCombo.getValue
      if (l != null) Messages.setLocale(l.tag)
    })
    val language = row(8, Icons.node(Icons.Globe, 18, Palette.Muted), langCombo)
    language.setPadding(new Insets(0, 6, 0, 0))

    val body = new VBox(8, title, tagline, spacer(14), form)
    body.setAlignment(Pos.TOP_CENTER)

    // A few of the roster out on the hills, turned toward the middle
    val sky = new SkyBackdrop()
    Seq((0.06, CharacterId.Gladiator, Direction.Right), (0.15, CharacterId.Wizard, Direction.Right),
      (0.85, CharacterId.Tidecaller, Direction.Left), (0.94, CharacterId.Samurai, Direction.Left)).foreach {
      case (x, who, facing) => sky.stand(SpriteGenerator.portraitView(who.id, facing, 128), x, 128)
    }

    val page = Chrome.loggedOut(body, Seq(language, Chrome.soundToggle()), sky)
    page.show(stage)
    AudioManager.playMenuMusic()
    // Decode every sound now, on a background thread, so no WAV parsing or disk
    // I/O ever lands mid-match on the render or packet threads.
    AudioManager.preload()
  }

  /** The form: a name and a password to log in with, or a new account's. */
  private def loginForm(stage: Stage, notice: String): VBox = {
    val heading = h2(Messages.t("Welcome back"))
    val blurb = muted(Messages.t("Log in to jump into a match."))

    val usernameField = field(Messages.t("Enter username"))
    val passwordInput = passwordField(Messages.t("Enter password"))
    val confirmInput = passwordField(Messages.t("Confirm password"))
    val confirmBox = labeled(Messages.t("Confirm password"), confirmInput)
    confirmBox.setVisible(false)
    confirmBox.setManaged(false)

    val hostField = field("localhost")
    val portField = field(Constants.SERVER_PORT.toString)
    portField.setPrefWidth(96)
    portField.setMaxWidth(96)
    HBox.setHgrow(hostField, Priority.ALWAYS)
    val serverRow = row(8, hostField, portField)

    // Where the last login was, to start from (after a logout, or a login that ran out)
    val last = lastLogin
    if (last != null) {
      usernameField.setText(last.name)
      hostField.setText(last.host)
      portField.setText(last.port.toString)
    }

    // Kept on disk to log in with at the next launch; held only while the client runs otherwise
    val remember = new CheckBox(Messages.t("Remember me"))

    val actionButton = button(Messages.t("Log in"), "sun", "lg")
    actionButton.setDefaultButton(true)
    actionButton.setMaxWidth(Double.MaxValue)

    val statusLabel = text("", "status")
    statusLabel.setWrapText(true)
    statusLabel.setMaxWidth(Double.MaxValue)
    statusLabel.managedProperty().bind(statusLabel.textProperty().isNotEmpty)
    if (notice.nonEmpty) showStatus(statusLabel, notice, Tone.Error)

    val switchPrompt = muted(Messages.t("New here?"))
    val switchLink = button(Messages.t("Create an account"), "ghost")
    val switchRow = row(2, switchPrompt, switchLink)
    switchRow.setAlignment(Pos.CENTER)

    var isSignupMode = false
    switchLink.setOnAction(_ => {
      isSignupMode = !isSignupMode
      heading.setText(if (isSignupMode) Messages.t("Create your account") else Messages.t("Welcome back"))
      blurb.setText(if (isSignupMode) Messages.t("Pick a name and a password to start playing.") else Messages.t("Log in to jump into a match."))
      actionButton.setText(if (isSignupMode) Messages.t("Create Account") else Messages.t("Log in"))
      switchPrompt.setText(if (isSignupMode) Messages.t("Already have an account?") else Messages.t("New here?"))
      switchLink.setText(if (isSignupMode) Messages.t("Log in") else Messages.t("Create an account"))
      confirmBox.setVisible(isSignupMode)
      confirmBox.setManaged(isSignupMode)
    })

    val doAction = () => {
      val username = usernameField.getText.trim
      val password = passwordInput.getText

      val problem = LoginForm.problem(username, password, confirmInput.getText, isSignupMode)
      if (problem.isDefined) {
        showStatus(statusLabel, problem.get, Tone.Error)
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
              showStatus(statusLabel, Messages.t("Invalid port number"), Tone.Error)
              -1
          }
        }
        if (port > 0) {
          actionButton.setDisable(true)
          showStatus(statusLabel, Messages.t("Connecting to {0}...", s"$host:$port"), Tone.Info)
          startConnection(stage, host, port, username, password, isSignupMode, remember.isSelected, statusLabel, actionButton)
        }
      }
    }

    actionButton.setOnAction(_ => doAction())
    passwordInput.setOnAction(_ => doAction())

    val form = card(
      new VBox(4, heading, blurb),
      labeled(Messages.t("Username"), usernameField),
      labeled(Messages.t("Password"), passwordInput),
      confirmBox,
      labeled(Messages.t("Server"), serverRow),
      remember,
      new VBox(10, actionButton, statusLabel),
      divider(),
      switchRow)
    javafx.application.Platform.runLater(() =>
      if (last != null) passwordInput.requestFocus() else usernameField.requestFocus())
    form
  }

  /** The saved login's card: who, on which server, and the button that logs in as them. */
  private def savedCard(stage: Stage, saved: SavedLogin, notice: String, resume: Boolean, reconnecting: Boolean): VBox = {
    val heading = h2(Messages.t("Welcome back"))
    val server = row(6, Icons.node(Icons.Server, 14, Palette.Muted), muted(s"${saved.host}:${saved.port}"))
    val who = row(14, avatar(saved.name, 52), new VBox(2, text(saved.name, "h3"), server))

    val continueButton = button(Messages.t("Continue as {0}", saved.name), "sun", "lg")
    continueButton.setDefaultButton(true)
    continueButton.setMaxWidth(Double.MaxValue)

    val statusLabel = text("", "status")
    statusLabel.setWrapText(true)
    statusLabel.setMaxWidth(Double.MaxValue)
    statusLabel.managedProperty().bind(statusLabel.textProperty().isNotEmpty)
    if (notice.nonEmpty) showStatus(statusLabel, notice, Tone.Error)

    def go(again: Boolean): Unit = {
      continueButton.setDisable(true)
      resumeLogin(stage, statusLabel, continueButton, again)
    }
    continueButton.setOnAction(_ => go(again = false))

    val otherLink = button(Messages.t("Use another account"), "ghost")
    otherLink.setOnAction(_ => {
      forgetSavedLogin()
      show(stage)
    })
    val switchRow = row(2, muted(Messages.t("Not you?")), otherLink)
    switchRow.setAlignment(Pos.CENTER)

    val form = card(heading, who, new VBox(10, continueButton, statusLabel), divider(), switchRow)
    if (resume) go(again = reconnecting)
    form
  }

  private def spacer(h: Double): javafx.scene.layout.Region = {
    val r = new javafx.scene.layout.Region()
    r.setMinHeight(h)
    r
  }
}
