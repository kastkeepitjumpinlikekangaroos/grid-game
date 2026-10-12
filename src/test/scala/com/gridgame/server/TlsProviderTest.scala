package com.gridgame.server

import com.gridgame.common.protocol.ServerIdentity
import com.gridgame.server.net.TlsProvider
import org.junit.After
import org.junit.Assert._
import org.junit.Assume.assumeTrue
import org.junit.Test

import java.nio.file.{Files, Path}
import java.nio.file.attribute.PosixFilePermissions
import java.security.KeyStore
import scala.jdk.CollectionConverters._

/**
 * The server's TLS identity, in a folder of the test's own: made once, kept, and the same server
 * from one start to the next — which is what lets its players' clients pin it.
 */
class TlsProviderTest {
  private val dir = Files.createTempDirectory("gridgame-identity")
  private val file = dir.resolve(TlsProvider.IdentityFileName)

  @After def cleanUp(): Unit = Files.walk(dir).iterator.asScala.toSeq.reverse.foreach(p => Files.deleteIfExists(p))

  private def posix: Boolean = Files.getFileStore(dir).supportsFileAttributeView("posix")

  /** The fingerprint of the key in the file, read as anyone with the file would. */
  private def keyIn(f: Path): String = {
    val ks = KeyStore.getInstance("PKCS12")
    val in = Files.newInputStream(f)
    try ks.load(in, "gridgame-server-identity".toCharArray) finally in.close()
    ServerIdentity.fingerprint(ks.getCertificate(ks.aliases().nextElement()).getPublicKey)
  }

  @Test def theFirstStartMakesAnIdentityAndKeepsIt(): Unit = {
    val first = TlsProvider.load(file)
    assertTrue(Files.isRegularFile(file))
    assertEquals("what it says is the key it keeps", keyIn(file), first.fingerprint)
    assertTrue(first.fingerprint, first.fingerprint.matches("SHA256:[A-Za-z0-9+/]{43}"))
    assertEquals("nothing else left behind", Seq(TlsProvider.IdentityFileName),
      Files.list(dir).iterator.asScala.map(_.getFileName.toString).toSeq)
  }

  @Test def everyStartAfterIsTheSameServer(): Unit = {
    // Made anew at every start, as it was, a client had nothing to check
    val first = TlsProvider.load(file)
    assertEquals(first.fingerprint, TlsProvider.load(file).fingerprint)
    assertNotEquals("another folder, another server", first.fingerprint,
      TlsProvider.load(dir.resolve("elsewhere").resolve(TlsProvider.IdentityFileName)).fingerprint)
  }

  @Test def onlyItsOwnerCanReadIt(): Unit = {
    assumeTrue("a file system with POSIX permissions", posix)
    TlsProvider.load(file)
    assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(file))
    // And one left readable by others is made the owner's alone as it is read
    Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-r--r--"))
    TlsProvider.load(file)
    assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(file))
  }

  @Test def aDamagedIdentityStopsTheServerRatherThanBeingReplaced(): Unit = {
    // A key that changed without a word is what a client is there to catch: the owner decides
    Files.write(file, "not a keystore".getBytes)
    try {
      TlsProvider.load(file)
      fail("a damaged identity was taken")
    } catch {
      case e: IllegalStateException => assertTrue(e.getMessage, e.getMessage.contains("Delete it"))
    }
    assertEquals("left as it was", "not a keystore", new String(Files.readAllBytes(file)))
  }
}
