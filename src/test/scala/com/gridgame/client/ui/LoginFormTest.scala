package com.gridgame.client.ui

import org.junit.Assert._
import org.junit.Test

/**
 * What the login screen says before it connects. A signup the server would refuse is explained
 * here, in the player's language: the server answered every refused name "Username taken", and a
 * short password with a message cut off at 23 bytes.
 */
class LoginFormTest {
  private def signup(user: String, pass: String, confirm: String = null): Option[String] =
    LoginForm.problem(user, pass, if (confirm == null) pass else confirm, isSignup = true)
  private def login(user: String, pass: String): Option[String] = LoginForm.problem(user, pass, "", isSignup = false)

  @Test def aGoodSignupPasses(): Unit = {
    assertEquals(None, signup("new_player-1", "secret1"))
    assertEquals(None, login("anyone", "whatever"))
  }

  @Test def emptyFieldsAreAskedFor(): Unit = {
    assertEquals(Some("Username is required"), login("", "secret1"))
    assertEquals(Some("Password is required"), login("bob", ""))
  }

  @Test def aSignupNeedsTheSamePasswordTwice(): Unit =
    assertEquals(Some("Passwords do not match"), signup("bob", "secret1", "secret2"))

  @Test def aSignupNameMustBeLettersDigitsUnderscoresOrDashes(): Unit = {
    for (bad <- Seq("John Doe", "bob!", "émile")) {
      assertEquals(bad, Some("Usernames can only use letters, numbers, _ and -"), signup(bad, "secret1"))
    }
  }

  @Test def aSignupPasswordNeedsSixCharacters(): Unit =
    assertEquals(Some("Password must be at least 6 characters"), signup("bob", "abc"))

  @Test def lengthLimitsMatchThePacket(): Unit = {
    assertEquals(Some("Username max 20 characters"), login("x" * 21, "secret1"))
    assertEquals(Some("Password max 20 characters"), login("bob", "p" * 21))
  }

  @Test def aLoginIsNotHeldToTheSignupRules(): Unit = {
    // Existing accounts all fit them anyway; a wrong one is "Invalid credentials" from the server
    assertEquals(None, login("John Doe", "abc"))
  }
}
