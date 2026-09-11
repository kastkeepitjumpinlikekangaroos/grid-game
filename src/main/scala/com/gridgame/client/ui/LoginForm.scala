package com.gridgame.client.ui

import com.gridgame.client.i18n.Messages
import com.gridgame.common.protocol.AuthRules

/** What the login screen checks before it connects. */
object LoginForm {

  /**
   * Why the form can't be sent as filled in, if it can't. A signup is held to the rules the server
   * applies (AuthRules), so a name it would refuse is explained here: the server used to answer
   * every refused name with "Username taken", and a short password with a message cut off at 23
   * bytes, "Password must be 6+ cha".
   */
  def problem(username: String, password: String, confirm: String, isSignup: Boolean): Option[String] = {
    if (username.isEmpty) Some(Messages.t("Username is required"))
    else if (password.isEmpty) Some(Messages.t("Password is required"))
    else if (isSignup && password != confirm) Some(Messages.t("Passwords do not match"))
    else if (username.length > AuthRules.MaxUsernameLength) Some(Messages.t("Username max 20 characters"))
    else if (password.length > AuthRules.MaxPasswordLength) Some(Messages.t("Password max 20 characters"))
    else if (isSignup && !AuthRules.isValidUsername(username))
      Some(Messages.t("Usernames can only use letters, numbers, _ and -"))
    else if (isSignup && password.length < AuthRules.MinPasswordLength)
      Some(Messages.t("Password must be at least {0} characters", AuthRules.MinPasswordLength.toString))
    else None
  }
}
