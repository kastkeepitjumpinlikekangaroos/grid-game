package com.gridgame.server

import io.netty.handler.ssl.SslContext
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.util.InsecureTrustManagerFactory

import java.io.FileInputStream
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.security.KeyStore
import java.security.SecureRandom
import javax.net.ssl.KeyManagerFactory

object TlsProvider {
  def serverContext(): SslContext = {
    // Create temp dir with restrictive permissions (owner-only)
    val tmpDir = try {
      Files.createTempDirectory("gridgame-tls", PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")))
    } catch {
      case _: UnsupportedOperationException =>
        // Fallback for non-POSIX systems (Windows)
        Files.createTempDirectory("gridgame-tls")
    }
    val keystorePath = tmpDir.resolve("keystore.p12")

    // Generate a random password instead of using a hardcoded one
    val random = new SecureRandom()
    val passwordChars = new Array[Char](16)
    val charset = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    for (i <- passwordChars.indices) {
      passwordChars(i) = charset.charAt(random.nextInt(charset.length))
    }
    val password = new String(passwordChars)

    // Use keytool from the current JDK to generate a self-signed cert
    val javaHome = System.getProperty("java.home")
    val keytool = java.nio.file.Paths.get(javaHome, "bin", "keytool").toString

    val process = new ProcessBuilder(
      keytool, "-genkeypair",
      "-alias", "gridgame",
      "-keyalg", "RSA",
      "-keysize", "2048",
      "-validity", "365",
      "-storetype", "PKCS12",
      "-keystore", keystorePath.toString,
      "-storepass", password,
      "-dname", "CN=gridgame-server"
    ).redirectErrorStream(true).start()

    val exitCode = process.waitFor()
    if (exitCode != 0) {
      val output = new String(process.getInputStream.readAllBytes())
      throw new RuntimeException(s"keytool failed (exit $exitCode): $output")
    }

    val ks = KeyStore.getInstance("PKCS12")
    val fis = new FileInputStream(keystorePath.toFile)
    try {
      ks.load(fis, password.toCharArray)
    } finally {
      fis.close()
    }

    val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
    kmf.init(ks, password.toCharArray)

    // Clean up temp files immediately after loading
    Files.deleteIfExists(keystorePath)
    Files.deleteIfExists(tmpDir)

    SslContextBuilder.forServer(kmf)
      .protocols("TLSv1.3")
      .ciphers(java.util.Arrays.asList("TLS_AES_256_GCM_SHA384", "TLS_CHACHA20_POLY1305_SHA256"))
      .build()
  }

  def clientContext(): SslContext = {
    SslContextBuilder.forClient()
      .trustManager(InsecureTrustManagerFactory.INSTANCE)
      .protocols("TLSv1.3")
      .build()
  }
}
