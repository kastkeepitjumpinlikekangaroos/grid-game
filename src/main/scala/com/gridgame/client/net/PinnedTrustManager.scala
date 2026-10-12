package com.gridgame.client.net

import com.gridgame.common.protocol.ServerIdentity

import java.net.Socket
import java.security.cert.{CertificateException, X509Certificate}
import javax.net.ssl.{SSLEngine, X509ExtendedTrustManager}

/**
 * The server at host:port isn't the one this client met there before: its TLS key isn't the one
 * pinned for it (KnownServers). Thrown in the TLS handshake, before a byte of the login is sent,
 * and out of the connection's `waitForReady`. `known` is the identity pinned, `seen` the one shown.
 */
final class ServerIdentityChangedException(val host: String, val port: Int, val known: String, val seen: String)
    extends CertificateException(s"The server at $host:$port isn't the one met there before: it showed $seen, not $known")

/**
 * Takes the server's certificate if its key is the one pinned for host:port (`known`), or if none is
 * pinned yet: the first meeting, which the connection pins once the handshake is through (`seen`).
 * The key is all there is to check, as for an SSH host key: not a chain of signers, not a host name,
 * not dates. That the server holds the key is proved by the handshake itself — TLS 1.3's
 * CertificateVerify, which the JDK checks whatever a trust manager says. An extended trust manager,
 * so the JDK uses it as it is rather than wrapping it in checks of its own.
 */
final class PinnedTrustManager(host: String, port: Int, known: Option[String]) extends X509ExtendedTrustManager {
  @volatile private var shown: String = _

  /** The identity the server showed, once it has (null before). */
  def seen: String = shown

  private def check(chain: Array[X509Certificate]): Unit = {
    if (chain == null || chain.isEmpty) throw new CertificateException(s"The server at $host:$port showed no certificate")
    val fingerprint = ServerIdentity.fingerprint(chain(0).getPublicKey)
    shown = fingerprint
    known.foreach(k => if (k != fingerprint) throw new ServerIdentityChangedException(host, port, k, fingerprint))
  }

  override def checkServerTrusted(chain: Array[X509Certificate], authType: String): Unit = check(chain)
  override def checkServerTrusted(chain: Array[X509Certificate], authType: String, socket: Socket): Unit = check(chain)
  override def checkServerTrusted(chain: Array[X509Certificate], authType: String, engine: SSLEngine): Unit = check(chain)

  // The client is never asked to trust a client
  override def checkClientTrusted(chain: Array[X509Certificate], authType: String): Unit =
    throw new CertificateException("Not a server")
  override def checkClientTrusted(chain: Array[X509Certificate], authType: String, socket: Socket): Unit =
    throw new CertificateException("Not a server")
  override def checkClientTrusted(chain: Array[X509Certificate], authType: String, engine: SSLEngine): Unit =
    throw new CertificateException("Not a server")

  override def getAcceptedIssuers: Array[X509Certificate] = Array.empty
}
