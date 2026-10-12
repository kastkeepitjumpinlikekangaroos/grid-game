package com.gridgame.client.net

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.{AtomicMoveNotSupportedException, FileAlreadyExistsException, Files, Path, Paths, StandardCopyOption}
import java.nio.file.attribute.PosixFilePermissions
import java.util.Properties

/**
 * The files the client keeps in the player's settings folder — the login "Remember me" keeps
 * (LoginStore), the servers it has met (KnownServers): where that folder is, and reading and writing
 * one, readable and writable by the player alone on macOS and Linux (on Windows the folder is
 * already theirs alone), written whole beside it and moved into place, so a crash halfway leaves
 * the last one.
 */
object SettingsFiles {
  private val OwnerOnlyFile = PosixFilePermissions.fromString("rw-------")
  private val OwnerOnlyDirectory = PosixFilePermissions.fromString("rwx------")

  /** Where the game keeps the player's settings: Application Support on macOS, %APPDATA% on
    * Windows, the XDG config folder elsewhere. */
  def folder(): Path = {
    val home = Paths.get(System.getProperty("user.home"))
    val os = System.getProperty("os.name", "").toLowerCase
    if (os.contains("mac")) home.resolve("Library").resolve("Application Support").resolve("Grid Game")
    else if (os.contains("win")) {
      val appData = sys.env.get("APPDATA").filter(_.nonEmpty).map(Paths.get(_))
      appData.getOrElse(home.resolve("AppData").resolve("Roaming")).resolve("Grid Game")
    } else {
      val config = sys.env.get("XDG_CONFIG_HOME").filter(_.nonEmpty).map(Paths.get(_))
      config.getOrElse(home.resolve(".config")).resolve("grid-game")
    }
  }

  /** What `file` holds, or None if there is no such file. Throws if it can't be read. */
  def read(file: Path): Option[Properties] =
    if (!Files.isRegularFile(file)) None
    else {
      val props = new Properties()
      val in = Files.newBufferedReader(file, StandardCharsets.UTF_8)
      try props.load(in) finally in.close()
      Some(props)
    }

  /** Write `props` to `file`, in place of what it held: readable by the player alone from the moment
    * it exists, not made so after. Its folder is made if it isn't there. */
  def write(file: Path, props: Properties, comment: String): Unit = {
    val dir = file.toAbsolutePath.getParent
    createPrivateDirectory(dir)
    val tmp = if (posix(dir)) Files.createTempFile(dir, ".settings", ".tmp", PosixFilePermissions.asFileAttribute(OwnerOnlyFile))
              else Files.createTempFile(dir, ".settings", ".tmp")
    try {
      val out = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)
      try props.store(out, comment) finally out.close()
      try Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
      catch { case _: AtomicMoveNotSupportedException => Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING) }
    } finally Files.deleteIfExists(tmp)
  }

  private def posix(dir: Path): Boolean = Files.getFileStore(dir).supportsFileAttributeView("posix")

  /** The folder, made if it isn't there, and readable by the player alone when it is the game's own. */
  private def createPrivateDirectory(dir: Path): Unit =
    if (!Files.isDirectory(dir)) {
      val parent = dir.getParent
      if (parent != null) Files.createDirectories(parent)
      try {
        if (posix(parent)) Files.createDirectory(dir, PosixFilePermissions.asFileAttribute(OwnerOnlyDirectory))
        else Files.createDirectory(dir)
      } catch { case _: FileAlreadyExistsException => }
    }
}
