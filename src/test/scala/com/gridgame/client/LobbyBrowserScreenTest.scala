package com.gridgame.client

import com.gridgame.common.WorldRegistry
import com.gridgame.common.protocol._
import javafx.scene.Node
import javafx.scene.control.Button
import javafx.stage.Stage
import org.junit.Assert._
import org.junit.Test

/** The lobby browser's form for a new lobby, built from a GameClient and clicked like a player would:
  * the map it is made on is the one picked in its carousel. */
class LobbyBrowserScreenTest {
  private val t = new TestClient(name = "me")

  private def browser(): Stage = Fx {
    val app = new ClientMain()
    app.client = t.client
    val stage = new Stage()
    app.showLobbyBrowser(stage)
    stage
  }

  private def button(stage: Stage, text: String): Button =
    Fx(Fx.all(stage.getScene.getRoot).collectFirst { case b: Button if b.getText == text => b }.get)
  private def arrow(stage: Stage, tip: String): Button =
    Fx(Fx.all(stage.getScene.getRoot).collectFirst { case b: Button if b.getTooltip != null && b.getTooltip.getText == tip => b }.get)
  private def maps(stage: Stage): Seq[Node] =
    Fx(Fx.all(stage.getScene.getRoot).filter(_.getStyleClass.contains("carousel-item")))

  /** Create Lobby clicked: the map the server is asked for. */
  private def created(stage: Stage): Int = {
    t.clearSent()
    Fx(button(stage, "Create Lobby").fire())
    val create = t.sentLobbyActions.last
    assertEquals(LobbyAction.CREATE, create.getAction)
    create.getMapIndex & 0xFF
  }

  @Test def withNoMapPickedALobbyIsMadeOnTheFirst(): Unit = {
    val stage = browser()
    assertEquals(WorldRegistry.size, maps(stage).size)
    assertTrue(Fx(Fx.labels(stage.getScene.getRoot)).contains(WorldRegistry.getDisplayName(0)))
    assertEquals(0, created(stage))
  }

  @Test def aLobbyIsMadeOnTheMapPickedInTheStrip(): Unit = {
    val stage = browser()
    Fx(Fx.click(maps(stage)(7)))
    assertTrue(Fx(Fx.labels(stage.getScene.getRoot)).contains(WorldRegistry.getDisplayName(7)))
    assertEquals(7, created(stage))
  }

  @Test def aLobbyIsMadeOnTheMapTheArrowsSteppedTo(): Unit = {
    val stage = browser()
    Fx(arrow(stage, "Previous map").fire())
    assertEquals(WorldRegistry.size - 1, created(stage))
  }
}
