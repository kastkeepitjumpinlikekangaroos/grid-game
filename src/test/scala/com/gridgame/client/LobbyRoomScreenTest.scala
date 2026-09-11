package com.gridgame.client

import com.gridgame.common.model.CharacterId
import com.gridgame.common.protocol._
import javafx.scene.Node
import javafx.scene.control.Button
import javafx.scene.control.ComboBox
import javafx.scene.control.Label
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox
import javafx.stage.Stage
import org.junit.Assert._
import org.junit.Test

import java.util.UUID

/** The lobby room screen, built from a GameClient in a lobby and clicked like a player would. */
class LobbyRoomScreenTest {
  private val t = new TestClient(name = "me")
  private val host = UUID.randomUUID()
  private val other = UUID.randomUUID()
  private val bot = new UUID(0, 777)

  private def inLobby(asHost: Boolean, gameMode: Byte = 0): Unit = {
    t.lobby(LobbyAction.JOINED, lobbyId = 7, name = "Friday Night", players = 4, gameMode = gameMode)
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
    for (n <- Seq("  host", "  me (you)", "  other", "  Bot 777")) assertTrue(s"'$n' in $shown", shown.contains(n))
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
    val columns = Fx {
      Fx.all(stage.getScene.getRoot).collect {
        case col: VBox if !col.getChildren.isEmpty && col.getChildren.get(0).isInstanceOf[Label] &&
          col.getChildren.get(0).asInstanceOf[Label].getText.startsWith("Team ") =>
          col.getChildren.get(0).asInstanceOf[Label].getText -> Fx.labels(col).tail.map(_.trim)
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

  @Test def leavingTellsTheServerAndGoesBackToTheBrowser(): Unit = {
    inLobby(asHost = false)
    val stage = room()
    t.clearSent()
    Fx(button(stage, "Leave").get.fire())
    assertTrue(t.sentLobbyActions.exists(_.getAction == LobbyAction.LEAVE))
    assertEquals(ClientState.LOBBY_BROWSER, t.client.clientState)
    assertTrue(texts(stage).contains("Lobby Browser"))
  }

  @Test def theRoomFollowsTheServer(): Unit = {
    inLobby(asHost = false)
    val stage = room()
    val late = UUID.randomUUID()
    t.lobby(LobbyAction.PLAYER_JOINED, who = late, name = "latecomer", players = 5)
    Fx(()) // let the update the listener posted run
    Fx(())
    assertTrue(texts(stage).contains("  latecomer"))
    assertTrue(texts(stage).contains("Players: 5/8"))
  }
}
