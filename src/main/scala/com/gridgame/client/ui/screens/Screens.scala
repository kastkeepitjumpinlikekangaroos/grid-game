package com.gridgame.client.ui.screens

import com.gridgame.client.game.GameClient
import com.gridgame.client.net.{SavedLogin, ServerIdentityChangedException}

import javafx.scene.control.{Button, Label}
import javafx.stage.Stage

/** The server and the name of the last login, for the login form to start from. */
private[client] final case class LastLogin(host: String, port: Int, name: String)

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

  /** The login screen, with `notice` (why we are back here) on it. With a saved login it is the
    * saved login's card; `resume` logs in with it straight away (`reconnecting`: after the
    * connection dropped). */
  private[client] def showWelcomeScreen(stage: Stage, notice: String = "", resume: Boolean = false,
                                        reconnecting: Boolean = false): Unit
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
    * when it succeeds, `actionButton` enabled again when it doesn't. `remember` keeps the login on
    * disk, so the next launch logs in with it. */
  private[client] def startConnection(stage: Stage, serverHost: String, serverPort: Int,
                                      username: String, password: String, isSignup: Boolean, remember: Boolean,
                                      statusLabel: Label, actionButton: Button): Unit

  /** The login the client can log back in with without the password, or null. */
  private[client] def savedLogin: SavedLogin

  /** The server and name of the last login, or null. */
  private[client] def lastLogin: LastLogin

  /** Log in with the saved login, reporting as startConnection does. */
  private[client] def resumeLogin(stage: Stage, statusLabel: Label, actionButton: Button, reconnecting: Boolean): Unit

  /** Forget the saved login (the player wants another account), and end it on the server. */
  private[client] def forgetSavedLogin(): Unit

  /** Log out: the login ended on the server and forgotten here, and the login screen shown. */
  private[client] def logOut(stage: Stage): Unit

  /** The server isn't the one met at its host and port before (its identity changed): the login
    * screen says so, and nothing is sent until the player trusts it. */
  private[client] def showServerChanged(stage: Stage, change: ServerIdentityChangedException): Unit

  /** The player trusts the server's new identity: pinned, and the login goes on. */
  private[client] def trustServer(stage: Stage, change: ServerIdentityChangedException): Unit
}
