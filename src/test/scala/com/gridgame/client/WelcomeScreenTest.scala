package com.gridgame.client

import com.gridgame.client.net.{KnownServers, LoginStore, SavedLogin, ServerIdentityChangedException}
import com.gridgame.client.ui.screens.LastLogin
import com.gridgame.common.Constants
import javafx.scene.control.{Button, CheckBox, Label, PasswordField, TextField}
import javafx.stage.Stage
import org.junit.Assert._
import org.junit.Test

import scala.collection.mutable

/**
 * The login screen: the form, and whether to be remembered; the saved login's card, which logs in
 * with it at a press or by itself; and another account. Built by the real screen on an app whose
 * logging in is only noted down: nothing connects.
 */
class WelcomeScreenTest {
  /** The app, minus the network: what the screen asks of it is kept in `asked`. */
  private class App extends ClientMain {
    knownServers = KnownServers.inMemory()
    val asked = mutable.Buffer[String]()
    override private[client] def startConnection(stage: Stage, serverHost: String, serverPort: Int, username: String,
                                                 password: String, isSignup: Boolean, remember: Boolean,
                                                 statusLabel: Label, actionButton: Button): Unit =
      asked += s"log in $username at $serverHost:$serverPort, remember=$remember"
    override private[client] def resumeLogin(stage: Stage, statusLabel: Label, actionButton: Button,
                                             reconnecting: Boolean): Unit =
      asked += s"log in as ${savedLogin.name}, reconnecting=$reconnecting"
    override private[client] def forgetSavedLogin(): Unit = {
      asked += "forget"
      savedLogin = null
    }
    override private[client] def trustServer(stage: Stage, change: ServerIdentityChangedException): Unit =
      asked += s"trust ${change.seen} for ${change.host}:${change.port}"
  }

  private val saved = SavedLogin("play.example.com", 4000, "Owen", new Array[Byte](32), Long.MaxValue)

  private def screen(app: App, notice: String = "", resume: Boolean = false, reconnecting: Boolean = false): Stage = Fx {
    val stage = new Stage()
    app.showWelcomeScreen(stage, notice, resume, reconnecting)
    stage
  }

  private def find[T](stage: Stage)(pick: PartialFunction[javafx.scene.Node, T]): Option[T] =
    Fx(Fx.all(stage.getScene.getRoot).collectFirst(pick))

  private def button(stage: Stage, text: String): Button =
    find(stage) { case b: Button if b.getText == text => b }.getOrElse(throw new AssertionError(s"no '$text' button"))

  private def remember(stage: Stage): CheckBox =
    find(stage) { case c: CheckBox => c }.getOrElse(throw new AssertionError("no Remember me"))

  private def logIn(stage: Stage, name: String): Unit = Fx {
    Fx.all(stage.getScene.getRoot).foreach {
      case p: PasswordField if p.getPromptText == "Enter password" => p.setText("secret1")
      case f: TextField if f.getPromptText == "Enter username" => f.setText(name)
      case _ =>
    }
    button(stage, "Log in").fire()
  }

  @Test def theFormAsksWhetherToBeRemembered(): Unit = {
    val app = new App
    val stage = screen(app)
    val box = remember(stage)
    assertEquals("Remember me", Fx(box.getText))
    assertFalse("not unless the player asks", Fx(box.isSelected))
    logIn(stage, "bob")
    val again = screen(app)
    Fx(remember(again).setSelected(true))
    logIn(again, "bob")
    assertEquals(Seq(s"log in bob at localhost:${Constants.SERVER_PORT}, remember=false",
      s"log in bob at localhost:${Constants.SERVER_PORT}, remember=true"), app.asked)
  }

  @Test def aSavedLoginIsItsCard(): Unit = {
    val app = new App
    app.savedLogin = saved
    val stage = screen(app)
    assertTrue("no password to type", find(stage) { case p: PasswordField => p }.isEmpty)
    assertTrue(Fx(Fx.labels(stage.getScene.getRoot)).contains("play.example.com:4000"))
    Fx(button(stage, "Continue as Owen").fire())
    assertEquals(Seq("log in as Owen, reconnecting=false"), app.asked)
  }

  @Test def theCardLogsInByItselfWhenAsked(): Unit = {
    // As the game starts with a login it remembered, and once the connection has dropped
    val app = new App
    app.savedLogin = saved
    val stage = screen(app, resume = true, reconnecting = true)
    assertEquals(Seq("log in as Owen, reconnecting=true"), app.asked)
    assertTrue("pressed for the player", Fx(button(stage, "Continue as Owen").isDisabled))
  }

  @Test def notLoggedBackInByItselfAfterALoginElsewhere(): Unit = {
    // The card, and why: the player decides whether to take the account back
    val app = new App
    app.savedLogin = saved
    val stage = screen(app, notice = "Your account logged in somewhere else")
    assertTrue(app.asked.isEmpty)
    assertTrue(Fx(Fx.labels(stage.getScene.getRoot)).contains("Your account logged in somewhere else"))
    assertFalse(Fx(button(stage, "Continue as Owen").isDisabled))
  }

  @Test def anotherAccountForgetsTheSavedLoginForTheForm(): Unit = {
    val app = new App
    app.savedLogin = saved
    val stage = screen(app)
    Fx(button(stage, "Use another account").fire())
    assertEquals(Seq("forget"), app.asked)
    assertTrue("the form", find(stage) { case p: PasswordField => p }.isDefined)
  }

  @Test def aRememberedLoginLogsInAsTheGameStarts(): Unit = {
    val dir = java.nio.file.Files.createTempDirectory("gridgame-welcome")
    try {
      val app = new App
      app.loginStore = new LoginStore(dir.resolve("login.properties"))
      app.loginStore.save(saved)
      Fx(app.showFirstScreen(new Stage()))
      assertEquals(Seq("log in as Owen, reconnecting=false"), app.asked)
      assertTrue("kept on disk again as it is exchanged", app.rememberLogin)

      // Nothing remembered (or nothing still good): the form, and nothing asked
      val none = new App
      none.loginStore = new LoginStore(dir.resolve("login.properties"))
      none.loginStore.save(saved.copy(expiresAt = System.currentTimeMillis() - 1))
      val stage = Fx { val s = new Stage(); none.showFirstScreen(s); s }
      assertTrue(none.asked.isEmpty)
      assertTrue(find(stage) { case p: PasswordField => p }.isDefined)
    } finally {
      java.nio.file.Files.deleteIfExists(dir.resolve("login.properties"))
      java.nio.file.Files.deleteIfExists(dir)
    }
  }

  @Test def aServerThatChangedShowsBothIdentitiesAndAsks(): Unit = {
    val change = new ServerIdentityChangedException("play.example.com", 4000,
      "SHA256:uNiVztksCsDhcc0u9e8BujQXVUpKZIDTMczCvj3tD2s", "SHA256:q7Lw0bE3fYxN2kTzR9cVmH4sJpA6gD1uWo8iKyF5tCe")
    val app = new App
    val stage = Fx { val s = new Stage(); app.showServerChanged(s, change); s }
    val shown = Fx(Fx.labels(stage.getScene.getRoot))
    for (t <- Seq("This server's identity has changed", "play.example.com:4000", change.known, change.seen))
      assertTrue(s"'$t' in $shown", shown.contains(t))
    assertTrue("leaving is the default", Fx(button(stage, "Back").isDefaultButton))
    Fx(button(stage, "Trust the new identity").fire())
    assertEquals(Seq(s"trust ${change.seen} for play.example.com:4000"), app.asked)
    Fx(button(stage, "Back").fire())
    assertTrue("back to the form", find(stage) { case p: PasswordField => p }.isDefined)
  }

  @Test def theFormStartsFromTheLastLogin(): Unit = {
    // After a logout, or a saved login that ran out: the name and the server it was on
    val app = new App
    app.lastLogin = LastLogin("example.org", 4000, "Owen")
    val stage = screen(app)
    val fields = Fx(Fx.all(stage.getScene.getRoot).collect { case f: TextField if !f.isInstanceOf[PasswordField] => f.getText })
    assertEquals(Seq("Owen", "example.org", "4000"), fields)
  }
}
