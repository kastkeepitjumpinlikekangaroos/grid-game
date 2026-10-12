package com.gridgame.common.protocol

import org.junit.Assert._
import org.junit.Test

import java.security.{KeyPairGenerator, MessageDigest}
import java.security.spec.ECGenParameterSpec
import java.util.Base64

/** A server's identity as both ends write it: SSH's way, the SHA-256 of its key's encoding. */
class ServerIdentityTest {
  private def newKey() = {
    val g = KeyPairGenerator.getInstance("EC")
    g.initialize(new ECGenParameterSpec("secp256r1"))
    g.generateKeyPair().getPublic
  }

  @Test def itIsTheSha256OfTheKeyInBase64AsSshWritesIt(): Unit = {
    val key = newKey()
    val fp = ServerIdentity.fingerprint(key)
    assertTrue(fp, fp.matches("SHA256:[A-Za-z0-9+/]{43}"))
    val digest = Base64.getDecoder.decode(fp.stripPrefix("SHA256:") + "=")
    assertArrayEquals(MessageDigest.getInstance("SHA-256").digest(key.getEncoded), digest)
  }

  @Test def oneKeyOneIdentity(): Unit = {
    val key = newKey()
    assertEquals(ServerIdentity.fingerprint(key), ServerIdentity.fingerprint(key))
    assertNotEquals(ServerIdentity.fingerprint(key), ServerIdentity.fingerprint(newKey()))
  }
}
