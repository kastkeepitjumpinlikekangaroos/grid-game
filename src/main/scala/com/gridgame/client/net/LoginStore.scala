package com.gridgame.client.net

import com.gridgame.common.Constants

import java.io.IOException
import java.nio.file.{Files, Path, Paths}
import java.util.{Base64, Properties}

/**
 * A login the client can log back in with, without the password: the server it is on, the name it
 * logged in by, its refresh token, and when its login runs out (epoch ms; the server's word, so a
 * client whose clock is off may try one the server then refuses).
 */
final case class SavedLogin(host: String, port: Int, name: String, token: Array[Byte], expiresAt: Long) {
  def expired(now: Long): Boolean = expiresAt <= now

  // Never the token: this is what a log line would print
  override def toString: String = s"SavedLogin($name at $host:$port)"
}

/**
 * Where "Remember me" keeps the login between runs (design/networking.md, *Staying logged in*): one
 * small file in the player's own settings folder (SettingsFiles: readable and writable by them
 * alone, written whole or not at all). A login the player didn't ask to keep is never written: it
 * lives and dies with the client. Anything it can't read is deleted and not kept.
 */
final class LoginStore(val file: Path) {
  import LoginStore._

  /** The login kept on disk, if there is one it can use: one that has run out goes. */
  def load(now: Long = System.currentTimeMillis()): Option[SavedLogin] = {
    if (!Files.isRegularFile(file)) return None
    val login =
      try SettingsFiles.read(file).flatMap(parse)
      catch {
        case _: IOException | _: IllegalArgumentException => None
      }
    login match {
      case Some(l) if !l.expired(now) => login
      case _ =>
        clear()
        None
    }
  }

  /** Keep the login, in place of the one kept before. False if it couldn't be written. */
  def save(login: SavedLogin): Boolean =
    try {
      val props = new Properties()
      props.setProperty("version", "1")
      props.setProperty("host", login.host)
      props.setProperty("port", login.port.toString)
      props.setProperty("name", login.name)
      props.setProperty("token", Base64.getEncoder.encodeToString(login.token))
      props.setProperty("expiresAt", login.expiresAt.toString)
      SettingsFiles.write(file, props, "Grid Game: the login \"Remember me\" keeps. Delete this file to log out on this computer.")
      true
    } catch {
      case e: IOException =>
        System.err.println(s"LoginStore: Could not keep the login: ${e.getMessage}")
        false
    }

  /** Forget the kept login, if there is one. */
  def clear(): Unit =
    try Files.deleteIfExists(file)
    catch { case e: IOException => System.err.println(s"LoginStore: Could not delete the kept login: ${e.getMessage}") }
}

object LoginStore {
  private val FileName = "login.properties"

  /** The player's own: the file `GRIDGAME_LOGIN_FILE` names, if set, or one in the settings folder. */
  def forPlayer(): LoginStore =
    new LoginStore(sys.env.get("GRIDGAME_LOGIN_FILE").filter(_.nonEmpty).map(Paths.get(_))
      .getOrElse(SettingsFiles.folder().resolve(FileName)))

  /** A kept login as written by `save`, or None if anything in it is missing or wrong. */
  private def parse(props: Properties): Option[SavedLogin] = {
    def get(key: String): Option[String] = Option(props.getProperty(key)).map(_.trim).filter(_.nonEmpty)
    for {
      version <- get("version") if version == "1"
      host <- get("host")
      port <- get("port").flatMap(_.toIntOption) if port > 0 && port <= 65535
      name <- get("name")
      token <- get("token").map(t => Base64.getDecoder.decode(t)) if token.length == Constants.REFRESH_TOKEN_SIZE
      expiresAt <- get("expiresAt").flatMap(_.toLongOption)
    } yield SavedLogin(host, port, name, token, expiresAt)
  }
}
