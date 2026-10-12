package com.gridgame.common.protocol

import java.security.MessageDigest
import java.security.PublicKey
import java.util.Base64

/**
 * How a client knows the server it connected to is the one it met before (design/networking.md,
 * layer 1): by the fingerprint of the server's TLS key, written as SSH writes a host key's —
 * "SHA256:" and the SHA-256 of the key (its X.509 SubjectPublicKeyInfo) in base64. The key, not the
 * certificate, so a certificate made again for the same key is the same server. The server prints
 * its own as it starts; a client pins the one it first meets at each host and port.
 */
object ServerIdentity {
  def fingerprint(key: PublicKey): String =
    "SHA256:" + Base64.getEncoder.withoutPadding.encodeToString(MessageDigest.getInstance("SHA-256").digest(key.getEncoded))
}
