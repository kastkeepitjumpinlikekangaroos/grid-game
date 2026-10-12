package com.gridgame.client.net

import java.io.IOException
import java.nio.file.Path
import java.util.{Locale, Properties}

/**
 * The servers this client has met, and each one's identity (ServerIdentity.fingerprint of its TLS
 * key): pinned the first time the client connects to a host and port, as SSH pins a host key, and
 * checked every time after, before anything is sent (PinnedTrustManager; design/networking.md,
 * layer 1). The player's are kept in their settings folder beside the remembered login
 * (`known_servers.properties`, SettingsFiles), and read afresh each time, as another client on the
 * computer may have met a server since. A dev tool's and a test's are kept in memory, so they never
 * add to the player's.
 */
final class KnownServers private (file: Option[Path]) {
  private val held = new Properties()

  /** The identity pinned for the server at host:port, if this client has met one there. */
  def identityOf(host: String, port: Int): Option[String] = synchronized {
    Option(current().getProperty(KnownServers.key(host, port)))
  }

  /** Pin `fingerprint` as the identity of the server at host:port, in place of any pinned before:
    * the first meeting, or the player trusting a server that changed. */
  def trust(host: String, port: Int, fingerprint: String): Unit = synchronized {
    val pins = current()
    pins.setProperty(KnownServers.key(host, port), fingerprint)
    file.foreach { f =>
      try SettingsFiles.write(f, pins, "Grid Game: the servers this computer has met, and the identity each showed. " +
        "A server whose identity isn't the one here is refused.")
      catch { case e: IOException => System.err.println(s"KnownServers: Could not keep the server's identity: ${e.getMessage}") }
    }
  }

  private def current(): Properties = file match {
    case None => held
    case Some(f) =>
      try SettingsFiles.read(f).getOrElse(new Properties())
      catch {
        case e: IOException =>
          System.err.println(s"KnownServers: Could not read the servers met before: ${e.getMessage}")
          new Properties()
      }
  }
}

object KnownServers {
  private val FileName = "known_servers.properties"

  /** The player's own, in their settings folder. */
  def forPlayer(): KnownServers = new KnownServers(Some(SettingsFiles.folder().resolve(FileName)))

  /** Kept in `file`. */
  def at(file: Path): KnownServers = new KnownServers(Some(file))

  /** Kept in memory alone: a dev tool's, or a test's. */
  def inMemory(): KnownServers = new KnownServers(None)

  /** A host and port as pins are kept by: the host as typed, in lower case ("localhost" and
    * "127.0.0.1" are two servers, as they are to SSH). */
  private[net] def key(host: String, port: Int): String = s"${host.trim.toLowerCase(Locale.ROOT)}:$port"
}
