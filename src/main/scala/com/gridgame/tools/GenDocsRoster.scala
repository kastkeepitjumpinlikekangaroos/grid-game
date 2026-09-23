package com.gridgame.tools

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Dev tool: rewrites the role and health on every character card of the website from the roster
 * ([[DocsRoster]]), in place.
 *
 * Run:  bazel run //src/main/scala/com/gridgame/tools:gendocs
 */
object GenDocsRoster {
  def main(args: Array[String]): Unit = {
    // bazel run starts in the runfiles tree and says where the workspace is
    val root = Option(System.getenv("BUILD_WORKSPACE_DIRECTORY")).getOrElse(".")
    val path = Paths.get(root, "docs", "index.html")
    val before = new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
    val after = DocsRoster.rewrite(before)
    if (after != before) Files.write(path, after.getBytes(StandardCharsets.UTF_8))
    println(s"${DocsRoster.cards(after).size} cards in $path${if (after == before) ", unchanged" else ""}")
  }
}
