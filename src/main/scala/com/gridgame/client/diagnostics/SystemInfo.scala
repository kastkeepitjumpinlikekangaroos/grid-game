package com.gridgame.client.diagnostics

import com.gridgame.common.BuildInfo
import com.google.gson.{JsonArray, JsonObject}

import java.util.Locale

/** The machine and the build a report comes from: what a bug that only shows on some machines is found
  * by. Nothing that says who the player is: no user name, no home folder, no address. */
object SystemInfo {
  /** How the game was started: the packaged app (jpackage's launcher), a jar, Bazel, or plain java. */
  def launch: String =
    if (System.getProperty("jpackage.app-path") != null) "app"
    else if (sys.env.contains("BUILD_WORKING_DIRECTORY")) "bazel"
    else if (System.getProperty("sun.java.command", "").split(" ").headOption.exists(_.endsWith(".jar"))) "jar"
    else "java"

  def client: JsonObject = {
    val o = new JsonObject
    o.addProperty("version", BuildInfo.version)
    o.addProperty("commit", BuildInfo.commit)
    o.addProperty("dirty", BuildInfo.dirty)
    o.addProperty("built", BuildInfo.builtAt)
    o
  }

  /** The machine: system, Java, processors, memory, the screens (`screens`, as the menus measured them),
    * the language, the options the game was started with. */
  def machine(args: Seq[String], screens: Seq[String], language: String): JsonObject = {
    val o = new JsonObject
    o.addProperty("os", s"${prop("os.name")} ${prop("os.version")} (${prop("os.arch")})")
    o.addProperty("java", s"${prop("java.version")} (${prop("java.vendor")}, ${prop("java.vm.name")})")
    o.addProperty("cpus", Runtime.getRuntime.availableProcessors())
    o.addProperty("maxHeapMb", Runtime.getRuntime.maxMemory() / (1024 * 1024))
    o.addProperty("locale", Locale.getDefault.toString)
    o.addProperty("language", language)
    o.add("screens", array(screens))
    o.add("args", array(args))
    o.addProperty("launch", launch)
    o
  }

  /** One line of it, for the log's first lines. */
  def describe(args: Seq[String]): String =
    s"${prop("os.name")} ${prop("os.version")} ${prop("os.arch")}, Java ${prop("java.version")} (${prop("java.vendor")}), " +
      s"${Runtime.getRuntime.availableProcessors()} CPUs, heap up to ${Runtime.getRuntime.maxMemory() / (1024 * 1024)}MB, " +
      s"locale ${Locale.getDefault}, launched as $launch${if (args.isEmpty) "" else args.mkString(" with ", " ", "")}"

  private def prop(key: String): String = System.getProperty(key, "?")

  private def array(items: Seq[String]): JsonArray = {
    val a = new JsonArray
    items.foreach(a.add)
    a
  }
}
