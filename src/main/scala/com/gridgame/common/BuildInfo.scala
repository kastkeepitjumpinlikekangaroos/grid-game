package com.gridgame.common

import java.util.Properties

/**
 * Which build of the game this is: the commit it was built from and when, stamped into the jar by
 * Bazel (`//src/main/scala/com/gridgame/common:build_info`, from `scripts/workspace_status.sh`). A
 * problem report and the logs say it, so a report can be read against the code that sent it. A jar
 * built without the stamp (a build on Windows, which has no status script, or a test) says "dev".
 */
object BuildInfo {
  /** The resource the stamp is written to. */
  val ResourcePath = "com/gridgame/common/build_info.properties"

  private val props: Properties = {
    val p = new Properties()
    val in = getClass.getClassLoader.getResourceAsStream(ResourcePath)
    if (in != null) try p.load(in) catch { case _: Exception => } finally in.close()
    p
  }

  private def prop(key: String, default: String): String = {
    val v = props.getProperty(key)
    if (v == null || v.trim.isEmpty || v.trim == "unknown") default else v.trim
  }

  /** The game's version, as the packages carry it. */
  val version: String = prop("version", "1.0")

  /** The commit it was built from (a short hash), or "dev". */
  val commit: String = prop("commit", "dev")

  /** Whether the tree it was built from had changes not yet committed. */
  val dirty: Boolean = prop("dirty", "0") == "1"

  /** When it was built (UTC, ISO 8601), or "" when nobody said. */
  val builtAt: String = prop("built", "")

  /** One line for a log or a report: "1.0 (3f2a9c1, built 2026-10-30T14:02:11Z)". */
  def describe: String = {
    val c = if (dirty) s"$commit+changes" else commit
    if (builtAt.isEmpty) s"$version ($c)" else s"$version ($c, built $builtAt)"
  }
}
