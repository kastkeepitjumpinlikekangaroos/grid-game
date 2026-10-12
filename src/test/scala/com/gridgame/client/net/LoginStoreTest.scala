package com.gridgame.client.net

import org.junit.After
import org.junit.Assert._
import org.junit.Assume.assumeTrue
import org.junit.Test

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.nio.file.attribute.PosixFilePermissions
import scala.jdk.CollectionConverters._

/** Where "Remember me" keeps a login: in a folder of the test's own, never the player's. */
class LoginStoreTest {
  private val root = Files.createTempDirectory("gridgame-login")
  private val file = root.resolve("Grid Game").resolve("login.properties")
  private val store = new LoginStore(file)
  private val token = Array.tabulate[Byte](32)(i => (i * 5 + 3).toByte)
  private val now = 1_800_000_000_000L
  private val login = SavedLogin("play.example.com", 25566, "Owen_2", token, now + 86400000L)

  @After def cleanUp(): Unit =
    Files.walk(root).iterator.asScala.toSeq.reverse.foreach(p => Files.deleteIfExists(p))

  private def posix: Boolean = Files.getFileStore(root).supportsFileAttributeView("posix")

  private def listed(dir: Path): Seq[String] = Files.list(dir).iterator.asScala.map(_.getFileName.toString).toSeq.sorted

  @Test def aKeptLoginComesBackWhole(): Unit = {
    assertTrue(store.save(login))
    val back = store.load(now).get
    assertEquals((login.host, login.port, login.name, login.expiresAt), (back.host, back.port, back.name, back.expiresAt))
    assertArrayEquals(token, back.token)
  }

  @Test def onlyThePlayerCanReadIt(): Unit = {
    assumeTrue("a file system with POSIX permissions", posix)
    store.save(login)
    assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(file))
    assertEquals("the folder made for it, too", PosixFilePermissions.fromString("rwx------"),
      Files.getPosixFilePermissions(file.getParent))
  }

  @Test def keepingAnotherReplacesItAndLeavesNothingElseBehind(): Unit = {
    store.save(login)
    val next = login.copy(token = Array.fill[Byte](32)(9))
    store.save(next)
    assertArrayEquals(next.token, store.load(now).get.token)
    assertEquals(Seq("login.properties"), listed(file.getParent))
  }

  @Test def oneThatHasRunOutIsDeleted(): Unit = {
    store.save(login.copy(expiresAt = now))
    assertEquals(None, store.load(now))
    assertFalse(Files.exists(file))
  }

  @Test def oneItCannotReadIsDeleted(): Unit = {
    Files.createDirectories(file.getParent)
    val wellFormed = "version=1\nhost=h\nport=1\nname=n\nexpiresAt=9999999999999\n"
    for (damaged <- Seq("garbage", wellFormed + "token=AAAA\n", wellFormed + "token=!!not base64!!\n",
                         wellFormed.replace("version=1", "version=2") + "token=" + java.util.Base64.getEncoder.encodeToString(token))) {
      Files.write(file, damaged.getBytes(StandardCharsets.UTF_8))
      assertEquals(damaged, None, store.load(now))
      assertFalse(Files.exists(file))
    }
  }

  @Test def clearForgetsIt(): Unit = {
    store.save(login)
    store.clear()
    assertEquals(None, store.load(now))
    store.clear() // nothing there now: nothing to do
  }

  @Test def withNothingKeptNothingIsMade(): Unit = {
    assertEquals(None, store.load(now))
    assertFalse(Files.exists(file.getParent))
  }

  @Test def theTokenIsNotInALogLine(): Unit = {
    val line = login.toString
    assertTrue(line.contains("Owen_2"))
    assertFalse(line.contains(java.util.Base64.getEncoder.encodeToString(token).take(8)))
  }

}
