package com.gridgame.server

import com.gridgame.common.model.Position
import com.gridgame.common.protocol._
import io.netty.buffer.ByteBuf
import org.junit.Assert._
import org.junit.Test

/**
 * What the login screen is told when it signs up or logs in. A name the server refused used to
 * come back as "Username taken", and a short password as "Password must be 6+ cha": 25 bytes in
 * a 23-byte field.
 */
class AuthFlowTest {
  import ServerTestKit._

  private val suffix = (System.nanoTime() % 100000).toString

  /** Send one auth request on a fresh connection and return the server's reply. */
  private def reply(action: Byte, username: String, password: String): AuthResponsePacket = {
    val ch = new PeerChannel()
    server.handleIncomingPacket(new AuthRequestPacket(0, action, username, password), ch, null)
    val replies = Iterator.continually(ch.readOutbound[ByteBuf]()).takeWhile(_ != null).map(decode)
      .collect { case r: AuthResponsePacket => r }.toSeq
    assertEquals(1, replies.size)
    replies.head
  }

  @Test def aNameWithASpaceIsReportedAsInvalidNotTaken(): Unit = {
    val r = reply(AuthAction.SIGNUP, "John Doe", "secret1")
    assertFalse(r.getSuccess)
    assertEquals(AuthRules.InvalidUsername, r.getMessage)
  }

  @Test def aShortPasswordIsReportedInFull(): Unit = {
    val r = reply(AuthAction.SIGNUP, s"short$suffix", "abc")
    assertFalse(r.getSuccess)
    assertEquals(AuthRules.PasswordTooShort, r.getMessage)
  }

  @Test def aSignupThenATakenName(): Unit = {
    val name = s"taken$suffix"
    val ok = reply(AuthAction.SIGNUP, name, "secret1")
    assertTrue(ok.getSuccess)
    assertNotNull(ok.getAssignedUUID)
    val again = reply(AuthAction.SIGNUP, name, "secret1")
    assertFalse(again.getSuccess)
    assertEquals(AuthRules.UsernameTaken, again.getMessage)
  }

  @Test def playersGoByTheNameTheyLoggedInWith(): Unit = {
    // The name was whatever the client's join said, so anyone could appear in a lobby, on a name
    // plate or in chat as anyone else — another player, a bot — or with a name full of the
    // invisible and right-to-left characters a lobby's name is cleaned of
    val name = s"named$suffix"
    val ch = new PeerChannel()
    server.handleIncomingPacket(new AuthRequestPacket(0, AuthAction.SIGNUP, name, "secret1"), ch, null)
    val ok = Iterator.continually(ch.readOutbound[ByteBuf]()).takeWhile(_ != null).map(decode)
      .collect { case r: AuthResponsePacket => r }.toSeq
    assertTrue(ok.head.getSuccess)
    val id = ok.head.getAssignedUUID
    server.handleIncomingPacket(new PlayerJoinPacket(1, id, new Position(1, 1), 0, "Bot 1"), ch, null)
    assertEquals(name, server.getConnectedPlayer(id).getName)
  }

  @Test def aLoginWithTheWrongPasswordIsRefused(): Unit = {
    val name = s"login$suffix"
    assertTrue(reply(AuthAction.SIGNUP, name, "secret1").getSuccess)
    val bad = reply(AuthAction.LOGIN, name, "wrong-pass")
    assertFalse(bad.getSuccess)
    assertEquals(AuthRules.InvalidCredentials, bad.getMessage)
    val good = reply(AuthAction.LOGIN, name, "secret1")
    assertTrue(good.getSuccess)
    assertEquals(AuthRules.LoginSuccessful, good.getMessage)
  }
}
