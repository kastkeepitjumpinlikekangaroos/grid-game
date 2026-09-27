package com.gridgame.client.ui.screens

import com.gridgame.client.game.GameClient

import javafx.scene.control.{Button, Label}
import javafx.stage.Stage

/**
 * The app as a screen sees it (ClientMain is the one implementation): the connection's client, the
 * way to every other screen, and the hook that stops the screen being left. Every screen's `show`
 * starts with [[switchScreen]], which stops the last screen's endless animations
 * ([[stopCurrentScreen]]) and drops the client listeners it registered, and then sets its own.
 */
private[client] trait Screens {
  private[client] def client: GameClient

  /** Stop the screen being left: its animations, and the client listeners that update it. */
  private[client] def switchScreen(): Unit

  /** What stops the current screen's endless animations when the next one comes up. */
  private[client] var stopCurrentScreen: () => Unit

  private[client] def showWelcomeScreen(stage: Stage, notice: String = ""): Unit
  private[client] def showLobbyBrowser(stage: Stage, notice: String = ""): Unit
  private[client] def showLobbyRoom(stage: Stage): Unit
  private[client] def showPracticeSetup(stage: Stage): Unit
  private[client] def showRankedQueue(stage: Stage): Unit
  private[client] def showLeaderboard(stage: Stage): Unit
  private[client] def showAccountView(stage: Stage): Unit
  private[client] def showScoreboard(stage: Stage): Unit
  /** The match: the stage hides and the match's own window takes over (MatchWindow). */
  private[client] def showGameScene(stage: Stage): Unit

  /** Connect to a server and log in (or sign up), reporting on `statusLabel`: the lobby browser
    * when it succeeds, `actionButton` enabled again when it doesn't. */
  private[client] def startConnection(stage: Stage, serverHost: String, serverPort: Int,
                                      username: String, password: String, isSignup: Boolean,
                                      statusLabel: Label, actionButton: Button): Unit
}
