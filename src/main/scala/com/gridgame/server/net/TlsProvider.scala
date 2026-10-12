package com.gridgame.server.net

import com.gridgame.common.protocol.ServerIdentity
import io.netty.handler.ssl.SslContext
import io.netty.handler.ssl.SslContextBuilder

import java.io.File
import java.nio.file.{AtomicMoveNotSupportedException, Files, Path, Paths, StandardCopyOption}
import java.nio.file.attribute.PosixFilePermissions
import java.security.KeyStore
import javax.net.ssl.KeyManagerFactory

/**
 * The server's TLS identity: a key pair and a certificate for it, made the first time the server
 * starts and kept beside its account database (`server_identity.p12`, readable by its owner
 * alone), so that it is the same server to its players' clients from one start to the next. A
 * client pins the key the first time it connects, and won't log in to a server that shows it
 * another (design/networking.md, layer 1). The certificate was made anew at every start, as it
 * was, so there was nothing for a client to check, and it took any certificate at all.
 *
 * Deleting the file makes the server a new identity at its next start, and every client that met
 * it before warns its player that the server changed. A file that can't be read stops the server
 * rather than being replaced: a key that silently changed is what a client is there to catch.
 */
object TlsProvider {
  val IdentityFileName = "server_identity.p12"

  // Not a secret: the file's permissions are what keep the key, as an SSH host key's are, and a
  // password kept beside the file would add nothing. A PKCS12 file just has to have one.
  private val StorePassword = "gridgame-server-identity".toCharArray
  private val Alias = "gridgame"

  /** The server's identity: its TLS context, and the fingerprint of its key, which clients pin. */
  final class Identity(val context: SslContext, val fingerprint: String, val file: Path)

  /** Where the identity is kept: beside the account database (AuthDatabase.resolveDbPath), in the
    * directory `bazel run` was run from, or the working directory. */
  def resolveIdentityPath(): Path = {
    val buildWorkDir = System.getenv("BUILD_WORKING_DIRECTORY")
    if (buildWorkDir != null) new File(buildWorkDir, IdentityFileName).toPath.toAbsolutePath
    else Paths.get(IdentityFileName).toAbsolutePath
  }

  /** The identity kept at `file`, made there first if there is none. */
  def load(file: Path = resolveIdentityPath()): Identity = {
    if (!Files.exists(file)) create(file)
    else if (posix(file)) {
      val perms = Files.getPosixFilePermissions(file)
      if (perms != PosixFilePermissions.fromString("rw-------")) {
        System.err.println(s"TLS: $file was readable by others (${PosixFilePermissions.toString(perms)}); made the owner's alone")
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"))
      }
    }

    val ks = KeyStore.getInstance("PKCS12")
    try {
      val in = Files.newInputStream(file)
      try ks.load(in, StorePassword) finally in.close()
    } catch {
      case e: Exception =>
        throw new IllegalStateException(s"The server's identity ($file) can't be read: ${e.getMessage}. " +
          "Delete it to make a new one; every client that met this server will warn its player that it changed.", e)
    }
    val cert = ks.getCertificate(Alias)
    if (cert == null) throw new IllegalStateException(s"The server's identity ($file) has no key '$Alias' in it")

    val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
    kmf.init(ks, StorePassword)
    val context = SslContextBuilder.forServer(kmf)
      .protocols("TLSv1.3")
      .ciphers(java.util.Arrays.asList("TLS_AES_256_GCM_SHA384", "TLS_CHACHA20_POLY1305_SHA256"))
      .build()
    new Identity(context, ServerIdentity.fingerprint(cert.getPublicKey), file)
  }

  /**
   * A new identity at `file`: an EC P-256 key and a self-signed certificate for it, made by the JDK's
   * keytool in a folder of the owner's alone beside `file` and moved into place whole. The
   * certificate is good for a hundred years: what a client checks is the key, not the dates.
   */
  private def create(file: Path): Unit = {
    val dir = file.toAbsolutePath.getParent
    Files.createDirectories(dir)
    val tmpDir =
      if (posix(dir)) Files.createTempDirectory(dir, ".identity", PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")))
      else Files.createTempDirectory(dir, ".identity")
    val made = tmpDir.resolve(IdentityFileName)
    try {
      val keytool = Paths.get(System.getProperty("java.home"), "bin", "keytool").toString
      val process = new ProcessBuilder(
        keytool, "-genkeypair",
        "-alias", Alias,
        "-keyalg", "EC",
        "-groupname", "secp256r1",
        "-sigalg", "SHA256withECDSA",
        "-validity", "36500",
        "-storetype", "PKCS12",
        "-keystore", made.toString,
        "-storepass", new String(StorePassword),
        "-dname", "CN=Grid Game server"
      ).redirectErrorStream(true).start()
      val output = new String(process.getInputStream.readAllBytes())
      val exitCode = process.waitFor()
      if (exitCode != 0) throw new IllegalStateException(s"keytool failed (exit $exitCode): $output")
      if (posix(made)) Files.setPosixFilePermissions(made, PosixFilePermissions.fromString("rw-------"))
      try Files.move(made, file, StandardCopyOption.ATOMIC_MOVE)
      catch { case _: AtomicMoveNotSupportedException => Files.move(made, file) }
      println(s"TLS: Made the server's identity ($file)")
    } finally {
      Files.deleteIfExists(made)
      Files.deleteIfExists(tmpDir)
    }
  }

  private def posix(path: Path): Boolean = Files.getFileStore(path).supportsFileAttributeView("posix")
}
