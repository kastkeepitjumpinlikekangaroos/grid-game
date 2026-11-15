package com.gridgame.client

import com.gridgame.common.WorldRegistry
import com.gridgame.common.model.CharacterId
import com.gridgame.common.protocol._
import javafx.event.Event
import javafx.scene.Node
import javafx.scene.control.Button
import javafx.scene.control.ComboBox
import javafx.scene.control.Label
import javafx.scene.input.{KeyCode, KeyEvent, MouseButton, MouseEvent}
import javafx.scene.layout.Region
import javafx.scene.layout.StackPane
import javafx.stage.Stage
import org.junit.Assert._
import org.junit.Test

import java.util.UUID
import com.gridgame.client.game.ClientState

/** The lobby room screen, built from a GameClient in a lobby and clicked like a player would. */
class LobbyRoomScreenTest {
  private val t = new TestClient(name = "me")
  private val host = UUID.randomUUID()
  private val other = UUID.randomUUID()
  private val bot = new UUID(0, 777)

  private def inLobby(asHost: Boolean, gameMode: Byte = 0, teamSize: Int = 2, maxPlayers: Int = 8): Unit = {
    t.lobby(LobbyAction.JOINED, lobbyId = 7, name = "Friday Night", players = 4, maxPlayers = maxPlayers,
      gameMode = gameMode, teamSize = teamSize)
    val order = if (asHost) Seq(t.id -> "me", host -> "host") else Seq(host -> "host", t.id -> "me")
    (order :+ (other -> "other") :+ (bot -> "Bot 777")).foreach { case (id, n) => t.lobby(LobbyAction.MEMBER, who = id, name = n, players = 4) }
    t.client.isLobbyHost = asHost
  }

  private def room(): Stage = Fx {
    val app = new ClientMain()
    app.client = t.client
    val stage = new Stage()
    app.showLobbyRoom(stage)
    stage
  }

  private def root(stage: Stage): Node = Fx(stage.getScene.getRoot)
  private def texts(stage: Stage): Seq[String] = Fx(Fx.labels(stage.getScene.getRoot))
  private def button(stage: Stage, text: String): Option[Button] =
    Fx(Fx.all(stage.getScene.getRoot).collectFirst { case b: Button if b.getText == text => b })

  @Test def theRoomShowsItsNameAndEveryoneInIt(): Unit = {
    inLobby(asHost = false)
    val shown = texts(room())
    assertTrue(shown.contains("Friday Night"))
    for (n <- Seq("host", "me (you)", "other", "Bot 777")) assertTrue(s"'$n' in $shown", shown.contains(n))
  }

  @Test def pickingACharacterInTheRoomTellsTheServer(): Unit = {
    inLobby(asHost = false)
    val stage = room()
    t.clearSent()
    Fx {
      val cell = Fx.all(stage.getScene.getRoot).collectFirst {
        case l: Label if l.getText == "Wizard" && l.getParent.getParent.isInstanceOf[StackPane] => l.getParent.getParent
      }.get
      Fx.click(cell)
    }
    val pick = t.sentLobbyActions.last
    assertEquals((LobbyAction.CHARACTER_SELECT, 7.toShort, CharacterId.Wizard.id),
      (pick.getAction, pick.getLobbyId, pick.getCharacterId))
    assertEquals(CharacterId.Wizard.id, t.client.selectedCharacterId)
  }

  @Test def teamsAreShownAsTheMatchWillDealThem(): Unit = {
    inLobby(asHost = false, gameMode = 1)
    val stage = room()
    // Each team is a block headed by its name, holding its members
    val columns = Fx {
      Fx.all(stage.getScene.getRoot).collect {
        case name: Label if name.getStyleClass.contains("team-name") =>
          name.getText -> Fx.labelsOf(name.getParent.getParent, "member-name")
      }.toMap
    }
    // Humans in join order, then bots, dealt round-robin
    assertEquals(Seq("host", "other"), columns("Team 1 (Blue)"))
    assertEquals(Seq("me (you)", "Bot 777"), columns("Team 2 (Red)"))
  }

  @Test def onlyTheHostCanStart(): Unit = {
    inLobby(asHost = false)
    val guest = room()
    assertTrue(button(guest, "Start Game").isEmpty)
    assertTrue(texts(guest).contains("Waiting for host to start..."))

    val h = new TestClient(name = "boss")
    h.lobby(LobbyAction.JOINED, lobbyId = 9, name = "Boss Room")
    h.client.isLobbyHost = true
    val stage = Fx { val app = new ClientMain(); app.client = h.client; val s = new Stage(); app.showLobbyRoom(s); s }
    h.clearSent()
    Fx(button(stage, "Start Game").get.fire())
    assertEquals(LobbyAction.START, h.sentLobbyActions.last.getAction)
    assertTrue(texts(stage).contains("You are the host"))
  }

  @Test def theHostsSettingsGoToTheServer(): Unit = {
    inLobby(asHost = true)
    val stage = room()
    t.clearSent()
    Fx {
      val mode = Fx.all(stage.getScene.getRoot).collectFirst {
        case c: ComboBox[_] if c.getItems.contains("Teams") => c.asInstanceOf[ComboBox[String]]
      }.get
      mode.getSelectionModel.select("Teams")
      mode.getOnAction.handle(null)
    }
    val update = t.sentLobbyActions.last
    assertEquals((LobbyAction.CONFIG_UPDATE, 1.toByte), (update.getAction, update.getGameMode))
  }

  // ── The map: the host picks it in a carousel over the room ──

  private def mapChip(stage: Stage): Option[Button] =
    Fx(Fx.all(stage.getScene.getRoot).collectFirst { case b: Button if b.getStyleClass.contains("chip-button") => b })
  private def picker(stage: Stage): Option[Node] =
    Fx(Fx.all(stage.getScene.getRoot).find(_.getStyleClass.contains("dialog")))
  private def mapsIn(stage: Stage): Seq[Node] =
    Fx(Fx.all(picker(stage).get).filter(_.getStyleClass.contains("carousel-item")))
  private def nextMap(stage: Stage): Button =
    Fx(Fx.all(picker(stage).get).collectFirst { case b: Button if b.getTooltip != null && b.getTooltip.getText == "Next map" => b }.get)
  private def doubleClick(n: Node): Unit = Fx {
    Fx.click(n)
    Event.fireEvent(n, new MouseEvent(MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0, MouseButton.PRIMARY, 2,
      false, false, false, false, true, false, false, true, false, false, null))
  }

  @Test def theHostPicksTheMapInACarouselOverTheRoom(): Unit = {
    inLobby(asHost = true)
    val stage = room()
    assertEquals(WorldRegistry.getDisplayName(0), Fx(mapChip(stage).get.getText))
    t.clearSent()
    Fx(mapChip(stage).get.fire())
    assertTrue("the picker is open", picker(stage).nonEmpty)
    assertTrue(texts(stage).contains("Choose a map"))
    Fx(Fx.click(mapsIn(stage)(5)))
    assertTrue("browsing the maps sends nothing", t.sentLobbyActions.isEmpty)

    Fx(button(stage, "Use this map").get.fire())
    assertTrue("the picker is closed", picker(stage).isEmpty)
    val update = t.sentLobbyActions.last
    assertEquals((LobbyAction.CONFIG_UPDATE, 5), (update.getAction, update.getMapIndex & 0xFF))
    assertEquals(WorldRegistry.getDisplayName(5), Fx(mapChip(stage).get.getText))

    // Another setting changed before the server has answered keeps the map just picked
    t.clearSent()
    Fx {
      val mode = Fx.all(stage.getScene.getRoot).collectFirst {
        case c: ComboBox[_] if c.getItems.contains("Teams") => c.asInstanceOf[ComboBox[String]]
      }.get
      mode.getSelectionModel.select("Teams")
      mode.getOnAction.handle(null)
    }
    val next = t.sentLobbyActions.last
    assertEquals((LobbyAction.CONFIG_UPDATE, 5, 1.toByte), (next.getAction, next.getMapIndex & 0xFF, next.getGameMode))

    // The server's word on it, and the picker opens on it next time
    t.lobby(LobbyAction.CONFIG_UPDATE, map = 5, players = 4, gameMode = 1)
    Fx(()); Fx(())
    assertEquals(WorldRegistry.getDisplayName(5), Fx(mapChip(stage).get.getText))
    Fx(mapChip(stage).get.fire())
    assertEquals(Seq(5), Fx(mapsIn(stage).zipWithIndex.collect { case (n, i) if n.getStyleClass.contains("carousel-item-picked") => i }))
  }

  @Test def browsingTheMapsChangesNothingUntilTheHostSettlesOnOne(): Unit = {
    inLobby(asHost = true)
    val stage = room()
    t.clearSent()
    Fx(mapChip(stage).get.fire())
    for (_ <- 0 until 3) Fx(nextMap(stage).fire())
    Fx(button(stage, "Cancel").get.fire())
    assertTrue(picker(stage).isEmpty)

    // Escape closes it as it was, too
    Fx(mapChip(stage).get.fire())
    Fx(Fx.click(mapsIn(stage)(3)))
    Fx(Event.fireEvent(mapsIn(stage)(3), new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE, false, false, false, false)))
    assertTrue(picker(stage).isEmpty)

    // And settling on the map the lobby has already is no change to send
    Fx(mapChip(stage).get.fire())
    doubleClick(mapsIn(stage)(0))
    assertTrue(picker(stage).isEmpty)

    assertTrue(s"nothing sent: ${t.sentLobbyActions.map(_.getAction)}", t.sentLobbyActions.isEmpty)
    assertEquals(WorldRegistry.getDisplayName(0), Fx(mapChip(stage).get.getText))

    // A double click on another settles on it at once
    Fx(mapChip(stage).get.fire())
    doubleClick(mapsIn(stage)(2))
    assertTrue(picker(stage).isEmpty)
    assertEquals(Seq((LobbyAction.CONFIG_UPDATE, 2)), t.sentLobbyActions.map(p => (p.getAction, p.getMapIndex & 0xFF)))
  }

  @Test def theHostsPictureOfTheMapOpensThePickerAGuestsDoesNot(): Unit = {
    inLobby(asHost = true)
    val host = room()
    Fx(Fx.click(Fx.all(host.getScene.getRoot).find(_.getStyleClass.contains("map-pick")).get))
    assertTrue(picker(host).nonEmpty)

    val g = new TestClient(name = "guest")
    g.lobby(LobbyAction.JOINED, lobbyId = 9, name = "Their Room")
    g.client.isLobbyHost = false
    val guest = Fx { val app = new ClientMain(); app.client = g.client; val s = new Stage(); app.showLobbyRoom(s); s }
    assertTrue("no map chip to click", mapChip(guest).isEmpty)
    assertTrue("its picture isn't a way in", Fx(Fx.all(guest.getScene.getRoot).forall(!_.getStyleClass.contains("map-pick"))))
    Fx(Fx.click(Fx.all(guest.getScene.getRoot).find(_.getStyleClass.contains("map-frame")).get))
    assertTrue(picker(guest).isEmpty)
    assertTrue(texts(guest).contains(WorldRegistry.getDisplayName(0)))
  }

  @Test def leavingTellsTheServerAndGoesBackToTheBrowser(): Unit = {
    inLobby(asHost = false)
    val stage = room()
    t.clearSent()
    Fx(button(stage, "Leave").get.fire())
    assertTrue(t.sentLobbyActions.exists(_.getAction == LobbyAction.LEAVE))
    assertEquals(ClientState.LOBBY_BROWSER, t.client.clientState)
    assertTrue(texts(stage).contains("Open lobbies"))
  }

  @Test def goingElsewhereFromTheTopBarLeavesTheLobby(): Unit = {
    inLobby(asHost = false)
    val stage = room()
    t.clearSent()
    Fx(button(stage, "Leaderboard").get.fire())
    assertTrue(t.sentLobbyActions.exists(_.getAction == LobbyAction.LEAVE))
    assertEquals(ClientState.LOBBY_BROWSER, t.client.clientState)
    assertTrue(texts(stage).contains("The highest-rated players on this server."))
  }

  /** Where each button's corner is on the page, laid out in a small laptop's window (ui/CLAUDE.md). */
  private def placesOf(stage: Stage, buttons: Button*): Seq[(Double, Double)] = Fx {
    val page = stage.getScene.getRoot.asInstanceOf[Region]
    page.resize(1440, 900)
    page.applyCss()
    page.layout()
    buttons.map { b => val p = b.localToScene(0, 0); (p.getX, p.getY) }
  }

  /** The host clicks Add Bot until the lobby is full and then Remove Bot until no bot is left, the
    * server answering each click as it does (a bot joining, the last one added leaving). Where the
    * two buttons are after each answer, starting from before the first click. */
  private def clickBotsInAndOut(gameMode: Byte, teamSize: Int, maxPlayers: Int): Seq[Seq[(Double, Double)]] = {
    inLobby(asHost = true, gameMode, teamSize, maxPlayers)
    val stage = room()
    val (add, remove) = (button(stage, "Add Bot").get, button(stage, "Remove Bot").get)
    val places = Seq.newBuilder[Seq[(Double, Double)]]
    places += placesOf(stage, add, remove)
    def answered(): Unit = { Fx(()); Fx(()); places += placesOf(stage, add, remove) } // the update the listener posted run

    var bots = Vector(bot)
    while (t.client.currentLobbyPlayerCount < maxPlayers) {
      t.clearSent()
      Fx(add.fire())
      assertEquals(Seq(LobbyAction.ADD_BOT), t.sentLobbyActions.map(_.getAction))
      bots :+= new UUID(0, 800 + bots.size)
      t.lobby(LobbyAction.PLAYER_JOINED, who = bots.last, name = s"Bot ${bots.last.getLeastSignificantBits}",
        players = t.client.currentLobbyPlayerCount + 1, maxPlayers = maxPlayers)
      answered()
    }
    assertEquals(maxPlayers, Fx(Fx.labelsOf(root(stage), "member-name")).size)
    assertTrue("a full lobby takes no more bots", Fx(add.isDisable))

    while (bots.nonEmpty) {
      t.clearSent()
      Fx(remove.fire())
      assertEquals(Seq(LobbyAction.REMOVE_BOT), t.sentLobbyActions.map(_.getAction))
      t.lobby(LobbyAction.PLAYER_LEFT, who = bots.last, name = s"Bot ${bots.last.getLeastSignificantBits}",
        players = t.client.currentLobbyPlayerCount - 1)
      bots = bots.init
      answered()
    }
    assertTrue("no bot left to remove", Fx(remove.isDisable))
    places.result()
  }

  // Clicked as fast as a host can, each click's bot is a pill more on the roster: the next click
  // must land on the same button. Under the roster, a bot that wrapped it onto another line pushed
  // the buttons down from under the pointer.
  @Test def theBotButtonsStayUnderThePointerAsAFreeForAllFillsWithBots(): Unit = {
    val places = clickBotsInAndOut(gameMode = 0, teamSize = 2, maxPlayers = 32)
    assertEquals(1 + 28 + 29, places.size)
    places.zipWithIndex.foreach { case (p, i) => assertEquals(s"after answer $i", places.head, p) }
  }

  @Test def theBotButtonsStayUnderThePointerAsTeamsFillWithBots(): Unit = {
    val places = clickBotsInAndOut(gameMode = 1, teamSize = 4, maxPlayers = 8)
    assertEquals(1 + 4 + 5, places.size)
    places.zipWithIndex.foreach { case (p, i) => assertEquals(s"after answer $i", places.head, p) }
  }

  @Test def theRoomFollowsTheServer(): Unit = {
    inLobby(asHost = false)
    val stage = room()
    val late = UUID.randomUUID()
    t.lobby(LobbyAction.PLAYER_JOINED, who = late, name = "latecomer", players = 5)
    Fx(()) // let the update the listener posted run
    Fx(())
    assertTrue(texts(stage).contains("latecomer"))
    assertTrue(texts(stage).contains("5/8"))
  }
}
