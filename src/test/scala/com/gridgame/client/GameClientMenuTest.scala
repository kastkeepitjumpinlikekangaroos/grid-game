package com.gridgame.client

import com.gridgame.client.game.{Attacks, ClientState}
import com.gridgame.client.gl.RenderQuality
import com.gridgame.client.input.{GLKeyboardHandler, GLMouseHandler}
import com.gridgame.client.render.{GameCamera, MatchMenu}
import com.gridgame.common.model._
import com.gridgame.common.protocol._
import org.junit.Assert._
import org.junit.{After, Before, Test}
import org.lwjgl.glfw.GLFW._

import scala.collection.mutable

/**
 * The menu Esc brings up in a match and the scoreboard Tab holds up, through the real keyboard and
 * mouse handlers (headless, as GameClientAimTest drives them): Esc brings the menu up and puts it
 * away, and while it is up the keys and the mouse are its own — nothing walks, aims, fires or casts,
 * what was aimed is put away, and a click it took fires nothing even let go of after it has gone —
 * the arrows and Enter work it, F7 still changes the quality, leaving takes Leave pressed twice, and
 * Esc in the chat only closes the chat. Tab holds the scoreboard up while it is down, never stuck up
 * by the chat, the menu or the window losing the focus.
 */
class GameClientMenuTest {
  private val t = new TestClient()
  private val c = t.client
  private val menu = new MatchMenu()
  private val keys = new GLKeyboardHandler(c, menu)
  private val mouse = new GLMouseHandler(c, new GameCamera(), menu)
  private val saved = mutable.Map[String, String]()
  private var before: RenderQuality.Store = _

  @Before def setUp(): Unit = {
    before = RenderQuality.store
    RenderQuality.store = new RenderQuality.Store {
      def get(key: String): Option[String] = saved.get(key)
      def put(key: String, value: String): Unit = saved(key) = value
    }
    RenderQuality.setTier(RenderQuality.HIGH)
    RenderQuality.configure(Array("--resolution=native", "--zoom=100"))
    // A character whose primary neither charges nor is a melee swing, so a click that went to the
    // match would show as a shot
    val d = CharacterDef.all.find(d => ProjectileDef.get(d.primaryProjectileType).chargeSpeedScaling.isEmpty).get
    c.selectedCharacterId = d.id.id
    t.startMatch(spawn = (30, 30))
    c.setMouseWorldPosition(38.0, 30.0)
    menu.onLeave = () => c.leaveMatch()
    t.clearSent()
  }

  @After def putBack(): Unit = {
    RenderQuality.store = before
    RenderQuality.setTier(RenderQuality.HIGH)
    RenderQuality.configure(Array("--resolution=native", "--zoom=100"))
  }

  private def press(key: Int): Unit = keys.invoke(0L, key, 0, GLFW_PRESS, 0)
  private def release(key: Int): Unit = keys.invoke(0L, key, 0, GLFW_RELEASE, 0)
  private def leaves: Int = t.sentLobbyActions.count(_.getAction == LobbyAction.LEAVE)

  // --- Esc ---

  @Test def escBringsTheMenuUpAndPutsItAwayAndLeavesNothing(): Unit = {
    press(GLFW_KEY_ESCAPE)
    assertTrue(menu.isOpen)
    press(GLFW_KEY_ESCAPE)
    assertFalse(menu.isOpen)
    press(GLFW_KEY_ESCAPE)
    press(GLFW_KEY_ESCAPE)
    assertEquals("Esc twice no longer leaves", 0, leaves)
    assertEquals(ClientState.PLAYING, c.clientState)
  }

  @Test def escInTheChatOnlyClosesTheChat(): Unit = {
    press(GLFW_KEY_T)
    assertTrue(keys.isChatMode)
    press(GLFW_KEY_ESCAPE)
    assertFalse(keys.isChatMode)
    assertFalse(menu.isOpen)
  }

  @Test def leavingIsLeavePressedTwice(): Unit = {
    press(GLFW_KEY_ESCAPE)
    press(GLFW_KEY_LEFT) // from Resume to Leave
    press(GLFW_KEY_ENTER)
    assertEquals("the first press only arms it", 0, leaves)
    assertTrue(menu.leaveArmed)
    press(GLFW_KEY_ENTER)
    assertEquals(1, leaves)
    assertFalse(menu.isOpen)
  }

  @Test def enterOrSpaceOnTheMenuAsItComesUpGoesBackToTheMatch(): Unit = {
    press(GLFW_KEY_ESCAPE)
    press(GLFW_KEY_ENTER)
    assertFalse(menu.isOpen)
    press(GLFW_KEY_ESCAPE)
    press(GLFW_KEY_SPACE)
    assertFalse(menu.isOpen)
    assertEquals(0, leaves)
    assertTrue("and fires nothing", t.sentSpawns.isEmpty)
  }

  // --- The menu has the keys and the mouse ---

  @Test def whileTheMenuIsUpNothingWalksOrCasts(): Unit = {
    press(GLFW_KEY_D)
    keys.update()
    val at = t.at
    assertNotEquals("D walks", (30, 30), at)
    press(GLFW_KEY_ESCAPE)
    t.clearSent()
    for (i <- 1 to 20) {
      t.nanos += 50000000L
      if (i == 3) { press(GLFW_KEY_W); press(GLFW_KEY_Q); press(GLFW_KEY_1) }
      keys.update()
    }
    assertEquals("D held from before stops, and nothing pressed after moves us", at, t.at)
    assertEquals(-1, c.aimedAbility)
    mouse.onCursorPos(10, 10)
    mouse.onMouseButton(GLFW_MOUSE_BUTTON_LEFT, GLFW_PRESS, 0)
    mouse.onMouseButton(GLFW_MOUSE_BUTTON_LEFT, GLFW_RELEASE, 0)
    mouse.onMouseButton(GLFW_MOUSE_BUTTON_RIGHT, GLFW_PRESS, 0)
    assertTrue(s"no shot: ${t.sentSpawns}", t.sentSpawns.isEmpty)
    assertEquals("no step", Nil, t.sentUpdates.map(u => (u.getPosition.getX, u.getPosition.getY)).filter(_ != at))
    assertTrue(menu.isOpen)
  }

  @Test def whatWasAimedIsPutAwayAsItComesUp(): Unit = {
    val d = c.getSelectedCharacterDef
    val slot = if (!Attacks.goesOffOnPress(d.qAbility)) 0 else if (!Attacks.goesOffOnPress(d.eAbility)) 1 else -1
    if (slot >= 0) {
      press(if (slot == 0) GLFW_KEY_Q else GLFW_KEY_E)
      assertEquals(slot, c.aimedAbility)
      press(GLFW_KEY_ESCAPE)
      assertEquals("an ability taken up is put away", -1, c.aimedAbility)
      mouse.onMouseButton(GLFW_MOUSE_BUTTON_LEFT, GLFW_PRESS, 0)
      mouse.onMouseButton(GLFW_MOUSE_BUTTON_LEFT, GLFW_RELEASE, 0)
      press(GLFW_KEY_ESCAPE)
      assertEquals(-1, c.aimedAbility)
    }
    // The primary held as the menu comes up: let go of over it, it goes nowhere
    mouse.onMouseButton(GLFW_MOUSE_BUTTON_LEFT, GLFW_PRESS, 0)
    press(GLFW_KEY_ESCAPE)
    mouse.onMouseButton(GLFW_MOUSE_BUTTON_LEFT, GLFW_RELEASE, 0)
    press(GLFW_KEY_ESCAPE)
    assertTrue("nothing goes off", t.sentSpawns.isEmpty)
  }

  @Test def theAimStaysPutWhileThePointerIsOnTheMenu(): Unit = {
    press(GLFW_KEY_ESCAPE)
    mouse.onCursorPos(5, 5)
    mouse.refreshWorldPosition()
    assertEquals((38.0, 30.0), (c.getMouseWorldX, c.getMouseWorldY))
  }

  @Test def aClickTheMenuTookFiresNothingLetGoOfAfterItHasGone(): Unit = {
    menu.layout(1440f, 900f, 1800)
    press(GLFW_KEY_ESCAPE)
    // Down on the menu, Esc while it is held, up over the match
    mouse.onCursorPos(menu.x(MatchMenu.QualityBase) + 5, menu.y(MatchMenu.QualityBase) + 5)
    mouse.onMouseButton(GLFW_MOUSE_BUTTON_LEFT, GLFW_PRESS, 0)
    press(GLFW_KEY_ESCAPE)
    assertFalse(menu.isOpen)
    mouse.onMouseButton(GLFW_MOUSE_BUTTON_LEFT, GLFW_RELEASE, 0)
    assertTrue(t.sentSpawns.isEmpty)
    assertEquals("and the quality wasn't picked: it takes a press and a release", RenderQuality.HIGH, RenderQuality.tier)
    // Resume, clicked: the menu goes, and the click with it
    press(GLFW_KEY_ESCAPE)
    menu.layout(1440f, 900f, 1800)
    mouse.onCursorPos(menu.x(MatchMenu.Resume) + 10, menu.y(MatchMenu.Resume) + 10)
    mouse.onMouseButton(GLFW_MOUSE_BUTTON_LEFT, GLFW_PRESS, 0)
    mouse.onMouseButton(GLFW_MOUSE_BUTTON_LEFT, GLFW_RELEASE, 0)
    assertFalse(menu.isOpen)
    assertTrue(t.sentSpawns.isEmpty)
    // and the next click is the match's again
    mouse.onMouseButton(GLFW_MOUSE_BUTTON_LEFT, GLFW_PRESS, 0)
    mouse.onMouseButton(GLFW_MOUSE_BUTTON_LEFT, GLFW_RELEASE, 0)
    assertEquals(Seq(AttackSlot.PRIMARY), t.sentSpawns.map(_.getAttackSlot))
  }

  @Test def theArrowsWorkTheMenuAndF7StillChangesTheQuality(): Unit = {
    press(GLFW_KEY_ESCAPE)
    press(GLFW_KEY_UP) // to the zoom
    press(GLFW_KEY_RIGHT)
    keys.invoke(0L, GLFW_KEY_RIGHT, 0, GLFW_REPEAT, 0) // held, it goes on
    assertEquals(120, RenderQuality.zoomPercent)
    assertEquals("120%", saved("zoom"))
    press(GLFW_KEY_F7)
    assertEquals(RenderQuality.MEDIUM, RenderQuality.tier)
    assertTrue(menu.isOpen)
  }

  // --- Tab ---

  @Test def tabHoldsTheScoreboardUpWhileItIsDown(): Unit = {
    press(GLFW_KEY_TAB)
    assertTrue(keys.tabHeld)
    keys.invoke(0L, GLFW_KEY_TAB, 0, GLFW_REPEAT, 0)
    assertTrue(keys.tabHeld)
    release(GLFW_KEY_TAB)
    assertFalse(keys.tabHeld)
    // and the game goes on under it: D still walks
    press(GLFW_KEY_TAB)
    press(GLFW_KEY_D)
    keys.update()
    assertNotEquals((30, 30), t.at)
    assertTrue(keys.tabHeld)
  }

  @Test def theScoreboardIsNeverLeftUpWithNoTabHeld(): Unit = {
    // The chat opening takes it down, and Tab does nothing while typing
    press(GLFW_KEY_TAB)
    press(GLFW_KEY_T)
    assertFalse(keys.tabHeld)
    press(GLFW_KEY_TAB)
    assertFalse("not while typing", keys.tabHeld)
    press(GLFW_KEY_ESCAPE) // the chat closed
    release(GLFW_KEY_TAB)
    // The menu coming up takes it down, and Tab does nothing while it is up
    press(GLFW_KEY_TAB)
    press(GLFW_KEY_ESCAPE)
    assertTrue(menu.isOpen)
    assertFalse(keys.tabHeld)
    release(GLFW_KEY_TAB)
    press(GLFW_KEY_TAB)
    assertFalse("not while the menu is up", keys.tabHeld)
    press(GLFW_KEY_ESCAPE)
    release(GLFW_KEY_TAB)
    assertFalse(menu.isOpen)
    // The window losing the focus takes it down: its release would come to another window
    press(GLFW_KEY_TAB)
    keys.clearAllKeys()
    assertFalse(keys.tabHeld)
  }
}
