package com.gridgame.client

import com.gridgame.client.game.{ClientState, GameClient}
import com.gridgame.client.net.{KnownServers, LoginStore, SavedLogin, ServerIdentityChangedException}
import com.gridgame.common.model.WorldData
import com.gridgame.common.protocol._
import javafx.scene.control.{Button, Label, Labeled, PasswordField}
import javafx.stage.Stage
import org.junit.After
import org.junit.Assert._
import org.junit.Test

import java.nio.file.Files
import java.util.UUID
import scala.collection.mutable

/**
 * The app logging in and staying logged in (ClientMain; design/networking.md, *Staying logged in*):
 * what it keeps, when it logs back in by itself and when it doesn't, and what it ends. Its clients
 * connect nowhere: the test hands each the server's word, and drops its connection, itself.
 */
class ClientLoginFlowTest {
  private val dir = Files.createTempDirectory("gridgame-flow")
  private val store = new LoginStore(dir.resolve("login.properties"))
  private val me = UUID.randomUUID()
  private val token1 = Array.fill[Byte](32)(1)
  private val token2 = Array.fill[Byte](32)(2)
  private val monthOn = System.currentTimeMillis() + 30L * 86400000L

  @After def cleanUp(): Unit = {
    store.clear()
    Files.deleteIfExists(dir)
  }

  /** A client with no network: it connects nowhere (or can't, while `unreachable`, or finds the server
    * isn't the one met there before, while there is a `changed`), and keeps what it sends. */
  private class FakeClient(name: String, unreachable: Boolean, changed: ServerIdentityChangedException)
      extends GameClient("localhost", 0, WorldData.createEmpty(10, 10), name) {
    private val out = mutable.Buffer[Packet]()
    packetSink = p => out.synchronized(out += p)
    @volatile private var endedBecause: Byte = SessionEndReason.NONE

    override def connect(): Unit = {
      if (changed != null) throw changed
      if (unreachable) throw new java.io.IOException("Connection refused")
    }
    override def sessionEndReason: Byte = endedBecause

    def asked: Seq[AuthRequestPacket] = out.synchronized(out.collect { case a: AuthRequestPacket => a }.toList)

    /** The server's answer to a login: its refresh token, then yes. */
    def letIn(token: Array[Byte]): Unit = {
      processPacket(new RefreshTokenPacket(1, me, token, monthOn))
      processPacket(new AuthResponsePacket(2, true, me, AuthRules.LoginSuccessful))
    }
    def refuse(message: String): Unit = processPacket(new AuthResponsePacket(3, false, null, message))

    /** The connection drops, as the network thread's disconnect tells the app: on the FX thread. */
    def drop(reason: Byte = SessionEndReason.NONE): Unit = {
      endedBecause = reason
      val listener = disconnectListener
      Fx(listener())
    }
  }

  /** The app, its clients the fakes (every one it made, in order), its tries quick, the servers it
    * has met its own. */
  private class App extends ClientMain {
    loginStore = store
    knownServers = KnownServers.inMemory()
    val clients = mutable.Buffer[FakeClient]()
    @volatile var unreachable = false
    @volatile var changed: ServerIdentityChangedException = _
    override private[client] def newGameClient(host: String, port: Int, name: String): GameClient = {
      val c = new FakeClient(name, unreachable, changed)
      clients.synchronized(clients += c)
      c
    }
    override private[client] def retryWaitMs(n: Int, reconnecting: Boolean): Long = 5L
    def made: Int = clients.synchronized(clients.size)
    def client(i: Int): FakeClient = clients.synchronized(clients(i))
  }

  private def waitUntil(what: String)(cond: => Boolean): Unit = {
    val end = System.currentTimeMillis() + 5000
    while (!cond) {
      if (System.currentTimeMillis() > end) throw new AssertionError(s"timed out waiting for $what")
      Thread.sleep(10)
    }
  }

  private def newStage(): Stage = Fx(new Stage())

  /** Long enough for anything the app was going to do by itself to have been done. */
  private def settle(): Unit = { Thread.sleep(150); Fx(()) }

  // Nothing until a screen is on the stage (a test that logs in from no screen at all waits for one)
  private def nodes(stage: Stage): Seq[javafx.scene.Node] =
    Fx(if (stage.getScene == null) Nil else Fx.all(stage.getScene.getRoot))
  private def texts(stage: Stage): Seq[String] = nodes(stage).collect { case l: Labeled if l.getText != null => l.getText }
  private def onForm(stage: Stage): Boolean = nodes(stage).exists(_.isInstanceOf[PasswordField])
  private def onLobbyBrowser(app: App, stage: Stage): Boolean =
    app.client != null && app.client.clientState == ClientState.LOBBY_BROWSER && texts(stage).contains("Create Lobby")

  /** The form's login, as its button starts it. */
  private def logIn(app: App, stage: Stage, remember: Boolean): Unit = Fx {
    app.startConnection(stage, "play.example.com", 4000, "bob", "secret1", isSignup = false, remember,
      new Label(), new Button())
  }

  /** Logged in with the password, the server's answer handed over. */
  private def loggedIn(app: App, stage: Stage, remember: Boolean): FakeClient = {
    logIn(app, stage, remember)
    waitUntil("the login asked")(app.made == 1 && app.client(0).asked.nonEmpty)
    val c = app.client(0)
    c.letIn(token1)
    waitUntil("the lobby browser")(onLobbyBrowser(app, stage))
    c
  }

  @Test def aLoginIsKeptOnDiskOnlyIfThePlayerAsked(): Unit = {
    val app = new App
    loggedIn(app, newStage(), remember = true)
    assertArrayEquals(token1, app.savedLogin.token)
    assertArrayEquals(token1, store.load().get.token)
    assertEquals(("play.example.com", 4000, "bob"), store.load().map(l => (l.host, l.port, l.name)).get)

    val unasked = new App
    loggedIn(unasked, newStage(), remember = false)
    assertArrayEquals("held while the client runs", token1, unasked.savedLogin.token)
    assertEquals("but not on disk: what an earlier run kept is gone too", None, store.load())
  }

  @Test def aRefusedPasswordIsNotSentAgainWhenTheServerHangsUp(): Unit = {
    // The server closes a connection that hasn't logged in after LOGIN_DEADLINE_MS: that drop is
    // no reason to try the password it refused again, and again
    val app = new App
    val stage = newStage()
    logIn(app, stage, remember = false)
    waitUntil("the login asked")(app.made == 1 && app.client(0).asked.nonEmpty)
    app.client(0).refuse(AuthRules.InvalidCredentials)
    app.client(0).drop()
    settle()
    assertEquals("no new connection", 1, app.made)
    assertEquals(1, app.client(0).asked.size)
  }

  @Test def aConnectionCutBeforeTheAnswerIsTriedAgain(): Unit = {
    // The server went down as we asked: ask again, on a new connection
    val app = new App
    val stage = newStage()
    logIn(app, stage, remember = false)
    waitUntil("the login asked")(app.made == 1 && app.client(0).asked.nonEmpty)
    app.client(0).drop()
    waitUntil("a second try")(app.made == 2 && app.client(1).asked.nonEmpty)
    assertEquals(AuthAction.LOGIN, app.client(1).asked.head.getAction)
  }

  @Test def aDroppedConnectionLogsBackInWithTheSavedLogin(): Unit = {
    val app = new App
    val stage = newStage()
    loggedIn(app, stage, remember = true).drop()
    waitUntil("a refresh login")(app.made == 2 && app.client(1).asked.nonEmpty)
    val asked = app.client(1).asked.head
    assertEquals(AuthAction.REFRESH, asked.getAction)
    assertArrayEquals(token1, asked.getRefreshToken)
    app.client(1).letIn(token2)
    waitUntil("the lobby browser again")(onLobbyBrowser(app, stage))
    assertTrue(texts(stage).contains("Reconnected to the server"))
    assertArrayEquals("the token it was exchanged for, on disk", token2, store.load().get.token)
  }

  @Test def loggedInElsewhereItWaitsForThePlayer(): Unit = {
    // Logging back in by itself, it would throw the other one out, and that one it, for ever
    val app = new App
    val stage = newStage()
    loggedIn(app, stage, remember = true).drop(SessionEndReason.REPLACED)
    settle()
    assertEquals("no new connection", 1, app.made)
    assertTrue(texts(stage).contains("Your account logged in somewhere else"))
    assertTrue("the card, to take it back", texts(stage).contains("Continue as bob"))
    assertTrue("still logged in, so to speak", store.load().isDefined)
  }

  @Test def loggedOutItForgetsTheLogin(): Unit = {
    val app = new App
    val stage = newStage()
    loggedIn(app, stage, remember = true).drop(SessionEndReason.REVOKED)
    settle()
    assertEquals(1, app.made)
    assertTrue(onForm(stage))
    assertTrue(texts(stage).contains("You were logged out. Please log in again."))
    assertNull(app.savedLogin)
    assertEquals(None, store.load())
  }

  @Test def aRefusedRefreshTokenIsForgotten(): Unit = {
    store.save(SavedLogin("play.example.com", 4000, "bob", token1, monthOn))
    val app = new App
    val stage = newStage()
    Fx(app.showFirstScreen(stage))
    waitUntil("a refresh login")(app.made == 1 && app.client(0).asked.nonEmpty)
    app.client(0).refuse(AuthRules.LoginExpired)
    waitUntil("the form")(onForm(stage))
    assertTrue(texts(stage).contains("Your login has expired. Please log in again."))
    assertEquals(None, store.load())
  }

  @Test def aServerOutOfReachKeepsTheSavedLogin(): Unit = {
    // It may be back in a minute: the card stays, to try again
    store.save(SavedLogin("play.example.com", 4000, "bob", token1, monthOn))
    val app = new App
    app.unreachable = true
    val stage = newStage()
    Fx(app.showFirstScreen(stage))
    waitUntil("it gives up")(texts(stage).exists(_.startsWith("Could not connect to the server")))
    assertEquals("three tries", 3, app.made)
    assertTrue(store.load().isDefined)
    assertTrue(nodes(stage).collectFirst { case b: Button if b.getText == "Continue as bob" => !b.isDisabled }.get)
  }

  @Test def logOutEndsTheLoginOnTheServer(): Unit = {
    val app = new App
    val stage = newStage()
    val c = loggedIn(app, stage, remember = true)
    Fx(app.logOut(stage))
    val logout = c.asked.last
    assertEquals(AuthAction.LOGOUT, logout.getAction)
    assertArrayEquals(token1, logout.getRefreshToken)
    assertEquals(None, store.load())
    assertTrue(onForm(stage))
  }

  // --- A server that isn't the one met at its address before ---

  private val change = new ServerIdentityChangedException("play.example.com", 4000,
    "SHA256:uNiVztksCsDhcc0u9e8BujQXVUpKZIDTMczCvj3tD2s", "SHA256:q7Lw0bE3fYxN2kTzR9cVmH4sJpA6gD1uWo8iKyF5tCe")

  private def button(stage: Stage, text: String): Button =
    nodes(stage).collectFirst { case b: Button if b.getText == text => b }.get

  @Test def aServerThatChangedIsAskedNothingAndNotTriedAgain(): Unit = {
    // Trying again would only find the same server: the player decides
    val app = new App
    app.changed = change
    val stage = newStage()
    logIn(app, stage, remember = false)
    waitUntil("the warning")(texts(stage).contains("This server's identity has changed"))
    settle()
    assertEquals("one try", 1, app.made)
    assertTrue("nothing asked of it", app.client(0).asked.isEmpty)
    assertTrue("both identities, to compare", texts(stage).contains(change.known) && texts(stage).contains(change.seen))
  }

  @Test def trustedTheNewIdentityIsPinnedAndTheSavedLoginGoesOn(): Unit = {
    store.save(SavedLogin("play.example.com", 4000, "bob", token1, monthOn))
    val app = new App
    app.knownServers.trust("play.example.com", 4000, change.known)
    app.changed = change
    val stage = newStage()
    Fx(app.showFirstScreen(stage))
    waitUntil("the warning")(texts(stage).contains("This server's identity has changed"))
    app.changed = null // the server the player now trusts
    Fx(button(stage, "Trust the new identity").fire())
    assertEquals(Some(change.seen), app.knownServers.identityOf("play.example.com", 4000))
    waitUntil("a refresh login")(app.made == 2 && app.client(1).asked.nonEmpty)
    assertEquals(AuthAction.REFRESH, app.client(1).asked.head.getAction)
  }

  @Test def backLeavesTheServerUntrusted(): Unit = {
    store.save(SavedLogin("play.example.com", 4000, "bob", token1, monthOn))
    val app = new App
    app.knownServers.trust("play.example.com", 4000, change.known)
    app.changed = change
    val stage = newStage()
    Fx(app.showFirstScreen(stage))
    waitUntil("the warning")(texts(stage).contains("This server's identity has changed"))
    Fx(button(stage, "Back").fire())
    settle()
    assertEquals(Some(change.known), app.knownServers.identityOf("play.example.com", 4000))
    assertTrue("the card, to try again when the player chooses", texts(stage).contains("Continue as bob"))
    assertEquals("and nothing tried by itself", 1, app.made)
    assertTrue("the saved login kept", store.load().isDefined)
  }

  @Test def theAppsClientsAreHeldToThePlayersKnownServers(): Unit = {
    val app = new ClientMain()
    app.knownServers = KnownServers.inMemory()
    assertSame(app.knownServers, app.newGameClient("play.example.com", 4000, "bob").knownServers)
    assertNotSame("any other client keeps its own", app.knownServers,
      new GameClient("play.example.com", 4000, WorldData.createEmpty(10, 10)).knownServers)
  }

  @Test def closingEndsALoginThePlayerDidNotAskToKeep(): Unit = {
    val unasked = new App
    val c = loggedIn(unasked, newStage(), remember = false)
    Fx(unasked.stop())
    assertEquals(AuthAction.LOGOUT, c.asked.last.getAction)

    val remembered = new App
    val r = loggedIn(remembered, newStage(), remember = true)
    Fx(remembered.stop())
    assertFalse("kept for the next launch", r.asked.exists(_.getAction == AuthAction.LOGOUT))
    assertTrue(store.load().isDefined)
  }
}
