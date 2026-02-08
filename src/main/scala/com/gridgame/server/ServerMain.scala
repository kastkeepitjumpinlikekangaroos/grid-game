package com.gridgame.server

import com.gridgame.common.Constants

import java.io.File
import java.net.SocketException

object ServerMain {

  def main(args: Array[String]): Unit = {
    var port = Constants.SERVER_PORT
    var worldFile = ""

    // Parse arguments: [port] [--world=<file>]
    println(s"Arguments received: ${args.mkString(", ")}")
    for (arg <- args) {
      println(s"Processing arg: '$arg'")
      if (arg.startsWith("--world=")) {
        val rawPath = arg.substring(8)
        println(s"Raw world path: '$rawPath'")
        worldFile = resolveWorldPath(rawPath)
        println(s"Resolved world file: '$worldFile'")
      } else {
        try {
          port = arg.toInt
        } catch {
          case _: NumberFormatException =>
            System.err.println(s"Invalid argument: $arg")
            System.err.println("Usage: ServerMain [port] [--world=<file>]")
            System.exit(1)
        }
      }
    }

    if (worldFile.nonEmpty) {
      println(s"Using world: $worldFile")
    } else {
      println("No world file specified, clients will use default world")
    }

    val server = new GameServer(port, worldFile)

    Runtime.getRuntime.addShutdownHook(new Thread(new Runnable {
      def run(): Unit = {
        println("\nShutdown signal received...")
        server.stop()
      }
    }))

    try {
      server.start()
    } catch {
      case e: SocketException =>
        System.err.println(s"Failed to start server: ${e.getMessage}")
        System.exit(1)
    }
  }

  private def resolveWorldPath(worldFile: String): String = {
    // Check if file exists directly
    val direct = new File(worldFile)
    if (direct.exists()) {
      println(s"Found world file: ${direct.getAbsolutePath}")
      return direct.getAbsolutePath
    }

    // Try relative to BUILD_WORKING_DIRECTORY (set by Bazel)
    val buildWorkDir = System.getenv("BUILD_WORKING_DIRECTORY")
    if (buildWorkDir != null) {
      val fromWorkDir = new File(buildWorkDir, worldFile)
      if (fromWorkDir.exists()) {
        println(s"Found world file: ${fromWorkDir.getAbsolutePath}")
        return fromWorkDir.getAbsolutePath
      }
    }

    // File not found, return as-is and let GameServer handle the error
    println(s"Warning: World file not found locally: $worldFile")
    worldFile
  }
}
