package com.gridgame.tools

import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.zip.{ZipException, ZipInputStream}
import scala.jdk.CollectionConverters._

/**
 * A problem report as the report tool is given it: the files of the zip the client sent (report.json,
 * the recording's segments, the screenshots, the logs, a JVM's crash file), read into memory, and what
 * the server kept beside it (server.json, match-journal.txt). It reads the server's folder for the
 * report, the zip on its own (a player's "Save a copy" is the same zip), or a folder the zip was
 * unpacked into.
 *
 * Everything in a report came from a player's machine. A name in the zip is never taken as a path
 * ([[ReportFiles.safeRelative]]), and nothing is read past sizes no real report comes near: a zip that
 * unpacks to gigabytes is cut short, with a warning, instead of filling the developer's memory.
 */
final class ReportFiles(
    /** What the tool was pointed at. */
    val source: Path,
    /** The folder the report was found in, when there was one (the server's folder for it). */
    val folder: Option[Path],
    /** The zip's files, by their names in it ('/' between folders), in the order they came. */
    val entries: Vector[(String, Array[Byte])],
    /** The server's note on the report (server.json), as it is. */
    val serverNote: Option[String],
    /** What the server saw of the match the report is about (match-journal.txt), as it is. */
    val journal: Option[String],
    /** What wasn't as it should be in what was read. */
    val warnings: Vector[String]) {

  def entry(name: String): Option[Array[Byte]] = entries.collectFirst { case (n, b) if n == name => b }

  def text(name: String): Option[String] = entry(name).map(b => new String(b, StandardCharsets.UTF_8))
}

object ReportFiles {
  /** No file of a real report comes near this: a segment of the recording is a few megabytes. */
  val MaxEntryBytes: Int = 256 << 20
  /** Nor does a whole report: the server takes at most tens of megabytes. */
  val MaxTotalBytes: Long = 1L << 30

  val ZipName = "report.zip"
  val ReportJson = "report.json"
  val ServerNoteName = "server.json"
  val JournalName = "match-journal.txt"

  /** The report at `input`: a folder holding report.zip (the server's), a folder holding report.json
    * (an unpacked zip), or the zip itself. Throws an IllegalArgumentException when it is none of them. */
  def load(input: Path): ReportFiles = {
    val warnings = Vector.newBuilder[String]
    if (Files.isDirectory(input)) {
      val zip = input.resolve(ZipName)
      if (Files.isRegularFile(zip)) fromZip(input, zip, Some(input), warnings)
      else if (Files.isRegularFile(input.resolve(ReportJson))) fromUnpacked(input, warnings)
      else throw new IllegalArgumentException(s"$input holds neither a $ZipName nor a $ReportJson")
    } else if (Files.isRegularFile(input)) {
      // A zip beside the server's note on it is in the server's folder for the report
      val parent = Option(input.toAbsolutePath.getParent)
      val beside = parent.filter(p => Files.isRegularFile(p.resolve(ServerNoteName)) || Files.isRegularFile(p.resolve(JournalName)))
      fromZip(input, input, beside, warnings)
    } else throw new IllegalArgumentException(s"$input: no such file or folder")
  }

  private def fromZip(source: Path, zip: Path, folder: Option[Path], warnings: scala.collection.mutable.Builder[String, Vector[String]]): ReportFiles = {
    val entries = Vector.newBuilder[(String, Array[Byte])]
    var total = 0L
    val in = new ZipInputStream(Files.newInputStream(zip))
    try {
      var e = in.getNextEntry
      var stop = false
      while (e != null && !stop) {
        if (!e.isDirectory) {
          val name = normalise(e.getName)
          val bytes = readCapped(in, name, warnings)
          total += bytes.length
          if (total > MaxTotalBytes) {
            warnings += s"the zip unpacks to more than ${MaxTotalBytes >> 20}MB: read no further than $name"
            stop = true
          } else entries += name -> bytes
        }
        if (!stop) e = in.getNextEntry
      }
    } catch {
      case z: ZipException => warnings += s"$zip is damaged (${z.getMessage}): kept what came before the damage"
      case _: java.io.EOFException => warnings += s"$zip ends early: kept what came before its end"
    } finally in.close()
    new ReportFiles(source, folder, entries.result(), folder.flatMap(read(_, ServerNoteName)),
      folder.flatMap(read(_, JournalName)), warnings.result())
  }

  private def fromUnpacked(dir: Path, warnings: scala.collection.mutable.Builder[String, Vector[String]]): ReportFiles = {
    val stream = Files.walk(dir)
    val files = try stream.iterator().asScala.filter(Files.isRegularFile(_)).toVector.sortBy(_.toString) finally stream.close()
    val entries = files.flatMap { f =>
      val name = dir.relativize(f).iterator().asScala.map(_.toString).mkString("/")
      if (name == ServerNoteName || name == JournalName) None
      else {
        val in = Files.newInputStream(f)
        try Some(name -> readCapped(in, name, warnings)) finally in.close()
      }
    }
    new ReportFiles(dir, Some(dir), entries, read(dir, ServerNoteName), read(dir, JournalName), warnings.result())
  }

  private def read(dir: Path, name: String): Option[String] = {
    val f = dir.resolve(name)
    if (!Files.isRegularFile(f)) None
    else {
      val in = Files.newInputStream(f)
      try Some(new String(readCapped(in, name, Vector.newBuilder[String]), StandardCharsets.UTF_8)) finally in.close()
    }
  }

  /** `in`'s bytes, up to MaxEntryBytes; past that it is cut there, and said so. */
  private def readCapped(in: InputStream, name: String, warnings: scala.collection.mutable.Builder[String, Vector[String]]): Array[Byte] = {
    val out = new java.io.ByteArrayOutputStream()
    val buf = new Array[Byte](64 * 1024)
    var n = in.read(buf)
    var cut = false
    while (n >= 0 && !cut) {
      val room = MaxEntryBytes - out.size()
      if (n > room) { out.write(buf, 0, room); cut = true }
      else { out.write(buf, 0, n); n = in.read(buf) }
    }
    if (cut) warnings += s"$name is bigger than ${MaxEntryBytes >> 20}MB: kept its first ${MaxEntryBytes >> 20}MB"
    out.toByteArray
  }

  /** A name in the zip as '/'-separated, without a leading slash. */
  def normalise(name: String): String = {
    var n = name.replace('\\', '/')
    while (n.startsWith("/") || n.startsWith("./")) n = if (n.startsWith("/")) n.drop(1) else n.drop(2)
    n
  }

  /**
   * A name in the zip as a path that stays inside the folder it is written to, or None for a name that
   * would climb out of it (a "..", which a hostile zip uses to write anywhere it likes). Every character
   * past letters, digits and `._-` and spaces becomes `_`.
   */
  def safeRelative(name: String): Option[String] = {
    val parts = normalise(name).split('/').filter(p => p.nonEmpty && p != ".")
    if (parts.isEmpty || parts.contains("..")) None
    else Some(parts.map(_.map(c => if (c.isLetterOrDigit && c < 128 || c == '.' || c == '_' || c == '-' || c == ' ') c else '_')).mkString("/"))
  }

  /** `name` written to `outDir`, if it can be without leaving it; where it went. */
  def writeInside(outDir: Path, name: String, bytes: Array[Byte]): Option[Path] =
    safeRelative(name).flatMap { rel =>
      val target = outDir.resolve(rel).normalize()
      if (!target.startsWith(outDir.normalize())) None
      else {
        Files.createDirectories(target.getParent)
        Files.write(target, bytes)
        Some(target)
      }
    }
}
