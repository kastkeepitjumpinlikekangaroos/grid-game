package com.gridgame.client

import com.gridgame.client.game.GameClient
import com.gridgame.common.model.WorldData
import com.gridgame.common.protocol._
import org.junit.Assert._
import org.junit.Test

import scala.collection.mutable

/** Logging in with a refresh token, and logging out, as the client asks for them and hears back. */
class GameClientLoginTest {
  private val token = Array.tabulate[Byte](32)(i => (i * 3 + 1).toByte)

  @Test def aRefreshTokenIsHandedOnBeforeTheAnswerBehindIt(): Unit = {
    // So it is kept (on disk, if it is to be) before the client acts on the answer and is first
    // heard on the session, which is when the server gives up the token it replaced for good
    val t = new TestClient()
    val heard = mutable.Buffer[String]()
    t.client.refreshTokenListener = (got, expiresAt) => heard += s"token ${got.sameElements(token)} $expiresAt"
    t.client.authResponseListener = (ok, _, _) => heard += s"answer $ok"
    t.receive(new RefreshTokenPacket(1, t.id, token, 1234L))
    t.receive(new AuthResponsePacket(2, true, t.id, AuthRules.LoginSuccessful))
    assertEquals(Seq("token true 1234", "answer true"), heard)
  }

  @Test def aRefreshLoginAndALogoutCarryTheToken(): Unit = {
    val t = new TestClient()
    t.clearSent()
    t.client.sendRefreshLogin(token)
    t.client.sendLogout(token)
    val asked = t.sent.collect { case a: AuthRequestPacket => a }
    assertEquals(Seq(AuthAction.REFRESH, AuthAction.LOGOUT), asked.map(_.getAction))
    asked.foreach(a => assertArrayEquals(token, a.getRefreshToken))
  }

  @Test def aClientThatNeverConnectedHasNothingToSayOrHear(): Unit = {
    // Closing the game with a login it couldn't reconnect ends that login, if it can: no connection, no word
    val c = new GameClient("localhost", 0, WorldData.createEmpty(10, 10))
    c.sendLogout(token)
    assertEquals(SessionEndReason.NONE, c.sessionEndReason)
  }
}
