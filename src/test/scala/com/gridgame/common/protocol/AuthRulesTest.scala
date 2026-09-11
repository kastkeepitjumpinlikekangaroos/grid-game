package com.gridgame.common.protocol

import org.junit.Assert._
import org.junit.Test

/** The account rules the server applies to a signup, and the login screen checks before it. */
class AuthRulesTest {
  @Test def usernamesAreLettersDigitsUnderscoresAndDashes(): Unit = {
    for (ok <- Seq("a", "Bob", "x_y-z", "player123", "A" * 20)) assertTrue(ok, AuthRules.isValidUsername(ok))
    for (bad <- Seq("", "John Doe", "bob!", "émile", "a" * 21, "tab\there", "semi;colon", null))
      assertFalse(String.valueOf(bad), AuthRules.isValidUsername(bad))
  }

  @Test def limitsMatchTheLoginPacket(): Unit = {
    assertEquals(20, AuthRules.MaxUsernameLength)
    assertEquals(20, AuthRules.MaxPasswordLength)
    assertEquals(6, AuthRules.MinPasswordLength)
  }
}
