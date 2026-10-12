package com.gridgame.client.net

import org.junit.After
import org.junit.Assert._
import org.junit.Assume.assumeTrue
import org.junit.Test

import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import scala.jdk.CollectionConverters._

/** The servers a client has met and the identity each showed, in a folder of the test's own. */
class KnownServersTest {
  private val root = Files.createTempDirectory("gridgame-known")
  private val file = root.resolve("Grid Game").resolve("known_servers.properties")
  private val a = "SHA256:uNiVztksCsDhcc0u9e8BujQXVUpKZIDTMczCvj3tD2s"
  private val b = "SHA256:q7Lw0bE3fYxN2kTzR9cVmH4sJpA6gD1uWo8iKyF5tCe"

  @After def cleanUp(): Unit = Files.walk(root).iterator.asScala.toSeq.reverse.foreach(p => Files.deleteIfExists(p))

  @Test def aServerIsUnknownUntilMet(): Unit = {
    val known = KnownServers.inMemory()
    assertEquals(None, known.identityOf("play.example.com", 25565))
    known.trust("play.example.com", 25565, a)
    assertEquals(Some(a), known.identityOf("play.example.com", 25565))
  }

  @Test def eachHostAndPortIsAServerOfItsOwn(): Unit = {
    val known = KnownServers.inMemory()
    known.trust("Play.Example.com", 25565, a)
    assertEquals("a host is a host whatever its case", Some(a), known.identityOf("play.example.com", 25565))
    assertEquals(None, known.identityOf("play.example.com", 25566))
    assertEquals("another name for it is another server, as to SSH", None, known.identityOf("127.0.0.1", 25565))
  }

  @Test def trustingAnotherIdentityReplacesTheOld(): Unit = {
    val known = KnownServers.inMemory()
    known.trust("h", 1, a)
    known.trust("h", 1, b)
    assertEquals(Some(b), known.identityOf("h", 1))
  }

  @Test def thePlayersAreKeptForTheNextRunAndForThemAlone(): Unit = {
    KnownServers.at(file).trust("play.example.com", 25565, a)
    assertEquals(Some(a), KnownServers.at(file).identityOf("play.example.com", 25565))
    assumeTrue("a file system with POSIX permissions", Files.getFileStore(root).supportsFileAttributeView("posix"))
    assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(file))
  }

  @Test def aClientSeesWhatAnotherOnTheComputerMetSince(): Unit = {
    val one = KnownServers.at(file)
    val other = KnownServers.at(file)
    assertEquals(None, other.identityOf("h", 1))
    one.trust("h", 1, a)
    assertEquals(Some(a), other.identityOf("h", 1))
    other.trust("g", 2, b)
    assertEquals("neither loses the other's", (Some(a), Some(b)), (one.identityOf("h", 1), one.identityOf("g", 2)))
  }
}
