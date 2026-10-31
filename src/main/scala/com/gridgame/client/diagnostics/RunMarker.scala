package com.gridgame.client.diagnostics

import com.gridgame.common.BuildInfo

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import java.util.Properties

/**
 * The run before this one, which never closed properly: crashed (the JVM itself, its `hs_err_pid` file
 * says how), froze and was forced to quit, or lost its power. What the next run can offer to send of it.
 *
 * @param log        its log (`client.1.log` once this run has moved it aside)
 * @param hsErr      the JVM's own account of a crash, if it wrote one
 * @param recording  the match it was playing, as far as it got, if it was in one
 */
final case class PreviousRun(pid: Long, startedAtMs: Long, lastSeenAtMs: Long, version: String, inMatch: Boolean,
                             log: Option[Path], hsErr: Option[Path], recording: Option[Path])

/**
 * A file that says a run is under way (`running.properties`, in the diagnostics folder): written as it
 * starts, its time brought up to date every few seconds, and taken down as it closes. One still there as
 * the next run starts is a run that never closed (design/diagnostics.md, *After a crash*).
 *
 * The JVM writes `hs_err_pid<pid>.log` where the crashed run was started from, or failing that its
 * temporary folder: looked for there by the pid the marker kept, which no launch option has to set up,
 * so a run from the packaged app, a jar or Bazel are all found the same way.
 */
final class RunMarker(dir: Path) {
  val file: Path = dir.resolve("running.properties")

  /** What the marker an earlier run left says, if one is there. Read before this run's own is written. */
  def leftOver(): Option[Properties] =
    if (!Files.isRegularFile(file)) None
    else try {
      val p = new Properties()
      val in = Files.newBufferedReader(file, StandardCharsets.UTF_8)
      try p.load(in) finally in.close()
      Some(p)
    } catch { case _: Exception => Some(new Properties()) }

  /** This run is under way: its pid, when it started, where from. */
  def start(): Unit = write(inMatch = false)

  /** Still running, as of now (and whether in a match). */
  def touch(inMatch: Boolean): Unit = write(inMatch)

  private val started = System.currentTimeMillis()

  private def write(inMatch: Boolean): Unit = {
    val p = new Properties()
    p.setProperty("pid", ProcessHandle.current().pid().toString)
    p.setProperty("startedAt", started.toString)
    p.setProperty("lastSeenAt", System.currentTimeMillis().toString)
    p.setProperty("cwd", Paths.get("").toAbsolutePath.toString)
    p.setProperty("version", BuildInfo.describe)
    p.setProperty("inMatch", inMatch.toString)
    try {
      val tmp = dir.resolve("running.properties.tmp")
      val out = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)
      try p.store(out, "A Grid Game run under way: one still here at the next start never closed properly")
      finally out.close()
      Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
    } catch { case _: Exception => }
  }

  /** The run closed properly. */
  def clear(): Unit = try Files.deleteIfExists(file) catch { case _: Exception => }
}

object RunMarker {
  /** The run a left-over marker describes, with the files it left: its log, the JVM's crash file, the
    * match it was recording. */
  def previousRun(marker: Properties, previousLog: Option[Path], recording: Option[Path], extraDirs: Seq[Path]): PreviousRun = {
    def long(key: String): Long = try marker.getProperty(key, "0").trim.toLong catch { case _: NumberFormatException => 0L }
    val pid = long("pid")
    val cwd = Option(marker.getProperty("cwd")).map(Paths.get(_))
    PreviousRun(pid, long("startedAt"), long("lastSeenAt"), marker.getProperty("version", "?"),
      marker.getProperty("inMatch", "false") == "true", previousLog, findCrashFile(pid, cwd.toSeq ++ extraDirs),
      recording)
  }

  /** The JVM's crash file for `pid`: where the run was started from, its temporary folder, the home folder. */
  def findCrashFile(pid: Long, dirs: Seq[Path]): Option[Path] = {
    if (pid <= 0) return None
    val name = s"hs_err_pid$pid.log"
    val places = dirs ++ Seq(Paths.get(System.getProperty("java.io.tmpdir", "/tmp")), Paths.get("/tmp"),
      Paths.get(System.getProperty("user.home", ".")))
    places.distinct.map(_.resolve(name)).find(p => try Files.isRegularFile(p) catch { case _: Exception => false })
  }
}
