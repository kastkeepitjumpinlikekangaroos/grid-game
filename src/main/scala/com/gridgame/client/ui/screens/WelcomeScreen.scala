package com.gridgame.client.ui.screens

import com.gridgame.client.audio.AudioManager
import com.gridgame.client.ui.LoginForm
import com.gridgame.client.i18n.Messages
import com.gridgame.common.Constants

import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.Button
import javafx.scene.control.ComboBox
import javafx.scene.control.Label
import javafx.scene.control.PasswordField
import javafx.scene.control.TextField
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.scene.text.Font
import javafx.scene.text.FontWeight
import javafx.stage.Stage

import com.gridgame.client.ui.Theme._

/** The login screen: the server to connect to, the account to log in with or create, the language, and a notice (why we are back here). */
private[client] final class WelcomeScreen(app: Screens) {
  import app._

  def show(stage: Stage, notice: String = ""): Unit = {
    switchScreen()
    val root = new VBox(0)
    root.setAlignment(Pos.CENTER)
    root.setStyle(darkBg)

    // Title section with dramatic presentation and animated glow
    val titleBox = new VBox(8)
    titleBox.setAlignment(Pos.CENTER)
    titleBox.setPadding(new Insets(48, 0, 28, 0))

    val title = new Label("Grid Game")
    title.setFont(Font.font("Exo 2", FontWeight.BOLD, 56))
    title.setTextFill(Color.WHITE)
    title.setStyle("-fx-effect: dropshadow(gaussian, rgba(74, 158, 255, 0.6), 32, 0, 0, 0);")
    // Subtle breathing glow animation on title: 0.92 -> 1.0 -> 0.92 opacity over 4s
    val titleGlow = steppedLoop(4.0, t => title.setOpacity(0.92 + 0.08 * triangle(t)))

    // Wider accent line with gradient
    val accentLine = new Region()
    accentLine.setMinHeight(2)
    accentLine.setMaxHeight(2)
    accentLine.setMaxWidth(120)
    accentLine.setStyle("-fx-background-color: linear-gradient(to right, transparent, #4a9eff, #7b61ff, #4a9eff, transparent); -fx-background-radius: 1;")

    val subtitle = new Label(Messages.t("Multiplayer Arena"))
    subtitle.setFont(Font.font("Exo 2", FontWeight.NORMAL, 15))
    subtitle.setTextFill(Color.web("#8899aa"))

    val versionLabel = new Label("v1.0")
    versionLabel.setFont(Font.font("Exo 2", FontWeight.NORMAL, 11))
    versionLabel.setTextFill(Color.web("#556677"))

    titleBox.getChildren.addAll(title, accentLine, subtitle, versionLabel)

    // Card container for the form
    val card = new VBox(16)
    card.setAlignment(Pos.CENTER)
    card.setPadding(new Insets(28, 36, 28, 36))
    card.setMaxWidth(440)
    card.setStyle(cardBg)

    val modeLabel = new Label(Messages.t("Login"))
    modeLabel.setFont(Font.font("Exo 2", FontWeight.BOLD, 20))
    modeLabel.setTextFill(Color.web("#4a9eff"))

    val usernameLabel = new Label(Messages.t("USERNAME"))
    usernameLabel.setStyle(sectionHeaderStyle)

    val usernameField = new TextField()
    usernameField.setPromptText(Messages.t("Enter username"))
    usernameField.setMaxWidth(Double.MaxValue)
    addFieldFocusEffect(usernameField)

    val passwordLabel = new Label(Messages.t("PASSWORD"))
    passwordLabel.setStyle(sectionHeaderStyle)

    val passwordField = new PasswordField()
    passwordField.setPromptText(Messages.t("Enter password"))
    passwordField.setMaxWidth(Double.MaxValue)
    passwordField.setStyle(fieldStyle)
    passwordField.focusedProperty().addListener((_, _, focused) => {
      passwordField.setStyle(if (focused) fieldFocusStyle else fieldStyle)
    })

    val confirmLabel = new Label(Messages.t("CONFIRM PASSWORD"))
    confirmLabel.setStyle(sectionHeaderStyle)

    val confirmField = new PasswordField()
    confirmField.setPromptText(Messages.t("Confirm password"))
    confirmField.setMaxWidth(Double.MaxValue)
    confirmField.setStyle(fieldStyle)
    confirmField.focusedProperty().addListener((_, _, focused) => {
      confirmField.setStyle(if (focused) fieldFocusStyle else fieldStyle)
    })

    val confirmBox = new VBox(4, confirmLabel, confirmField)
    confirmBox.setVisible(false)
    confirmBox.setManaged(false)

    // Server section with gradient separator
    val serverSep = createSeparator()

    val serverLabel = new Label(Messages.t("SERVER"))
    serverLabel.setStyle(sectionHeaderStyle)

    val hostField = new TextField()
    hostField.setPromptText("localhost")
    hostField.setMaxWidth(Double.MaxValue)
    addFieldFocusEffect(hostField)

    val portField = new TextField()
    portField.setPromptText("25565")
    portField.setMaxWidth(Double.MaxValue)
    addFieldFocusEffect(portField)

    val serverRow = new HBox(8)
    serverRow.setMaxWidth(Double.MaxValue)
    HBox.setHgrow(hostField, Priority.ALWAYS)
    portField.setMaxWidth(90)
    portField.setPrefWidth(90)
    serverRow.getChildren.addAll(hostField, portField)

    val actionButton = new Button(Messages.t("Login"))
    addHoverEffect(actionButton, buttonStyle, buttonHoverStyle)
    actionButton.setDefaultButton(true)
    actionButton.setMaxWidth(Double.MaxValue)

    val toggleLink = new Button(Messages.t("Don't have an account? Sign Up"))
    toggleLink.setStyle("-fx-background-color: transparent; -fx-text-fill: #7788aa; -fx-cursor: hand; -fx-font-size: 13; -fx-padding: 4 0 0 0;")
    toggleLink.setOnMouseEntered(_ => toggleLink.setStyle("-fx-background-color: transparent; -fx-text-fill: #4a9eff; -fx-cursor: hand; -fx-font-size: 13; -fx-padding: 4 0 0 0;"))
    toggleLink.setOnMouseExited(_ => toggleLink.setStyle("-fx-background-color: transparent; -fx-text-fill: #7788aa; -fx-cursor: hand; -fx-font-size: 13; -fx-padding: 4 0 0 0;"))

    var isSignupMode = false

    toggleLink.setOnAction(_ => {
      isSignupMode = !isSignupMode
      if (isSignupMode) {
        modeLabel.setText(Messages.t("Sign Up"))
        actionButton.setText(Messages.t("Create Account"))
        toggleLink.setText(Messages.t("Already have an account? Login"))
        confirmBox.setVisible(true)
        confirmBox.setManaged(true)
      } else {
        modeLabel.setText(Messages.t("Login"))
        actionButton.setText(Messages.t("Login"))
        toggleLink.setText(Messages.t("Don't have an account? Sign Up"))
        confirmBox.setVisible(false)
        confirmBox.setManaged(false)
      }
    })

    val statusLabel = new Label(notice)
    statusLabel.setTextFill(Color.web("#e84057"))
    statusLabel.setFont(Font.font("Exo 2", FontWeight.BOLD, 13))
    statusLabel.setWrapText(true)
    statusLabel.setMaxWidth(Double.MaxValue)

    val doAction = () => {
      val username = usernameField.getText.trim
      val password = passwordField.getText

      val problem = LoginForm.problem(username, password, confirmField.getText, isSignupMode)
      if (problem.isDefined) {
        statusLabel.setTextFill(Color.web("#e84057"))
        statusLabel.setText(problem.get)
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
              statusLabel.setText(Messages.t("Invalid port number"))
              -1
          }
        }
        if (port > 0) {
          actionButton.setDisable(true)
          statusLabel.setTextFill(Color.web("#8899bb"))
          statusLabel.setText(Messages.t("Connecting to {0}...", s"$host:$port"))
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

    // Subtle animated border glow on the card
    val cardGlow = new javafx.animation.Timeline(
      new javafx.animation.KeyFrame(javafx.util.Duration.ZERO,
        new javafx.animation.KeyValue(card.effectProperty(),
          new javafx.scene.effect.DropShadow(javafx.scene.effect.BlurType.GAUSSIAN, Color.web("rgba(74, 158, 255, 0.15)"), 24, 0, 0, 0))),
      new javafx.animation.KeyFrame(javafx.util.Duration.millis(3000),
        new javafx.animation.KeyValue(card.effectProperty(),
          new javafx.scene.effect.DropShadow(javafx.scene.effect.BlurType.GAUSSIAN, Color.web("rgba(74, 158, 255, 0.35)"), 32, 0, 0, 0)))
    )
    cardGlow.setCycleCount(javafx.animation.Animation.INDEFINITE)
    cardGlow.setAutoReverse(true)
    cardGlow.play()
    stopCurrentScreen = () => { titleGlow.stop(); cardGlow.stop() }

    // Language selector — changing it applies the choice globally, persists it,
    // and rebuilds this screen (via Messages.onLocaleChanged) in the new language.
    val langLabel = new Label(Messages.t("Language"))
    langLabel.setStyle(sectionHeaderStyle)
    val langCombo = new ComboBox[Messages.Lang]()
    Messages.supported.foreach(l => langCombo.getItems.add(l))
    langCombo.setConverter(new javafx.util.StringConverter[Messages.Lang] {
      override def toString(l: Messages.Lang): String = if (l == null) "" else l.nativeName
      override def fromString(s: String): Messages.Lang = null
    })
    langCombo.setValue(Messages.currentLang) // set before handler so it doesn't fire
    langCombo.setOnAction(_ => {
      val l = langCombo.getValue
      if (l != null) Messages.setLocale(l.tag)
    })
    val langRow = new HBox(8, langLabel, langCombo, createSoundToggleButton())
    langRow.setAlignment(Pos.CENTER)
    langRow.setPadding(new Insets(12, 0, 0, 0))

    root.getChildren.addAll(langRow, titleBox, card, new Region() { setMinHeight(16) }, toggleLink)

    fadeInScene(stage, root)
    stage.show()
    AudioManager.playMenuMusic()
    // Decode every sound now, on a background thread, so no WAV parsing or disk
    // I/O ever lands mid-match on the render or packet threads.
    AudioManager.preload()
  }
}
