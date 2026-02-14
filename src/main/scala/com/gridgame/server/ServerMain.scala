package com.gridgame.server

import com.gridgame.common.Constants

object ServerMain {

  def main(args: Array[String]): Unit = {
    var port = Constants.SERVER_PORT

    // Parse arguments: [port]
    println(s"Arguments received: ${args.mkString(", ")}")
    for (arg <- args) {
      println(s"Processing arg: '$arg'")
      if (arg.startsWith("--world=")) {
        println(s"Note: --world argument is ignored in lobby mode. Maps are selected per-lobby.")
      } else {
        try {
          port = arg.toInt
        } catch {
          case _: NumberFormatException =>
            System.err.println(s"Invalid argument: $arg")
            System.err.println("Usage: ServerMain [port]")
            System.exit(1)
        }
      }
    }

    val server = new GameServer(port)

    Runtime.getRuntime.addShutdownHook(new Thread(new Runnable {
      def run(): Unit = {
        println("\nShutdown signal received...")
        server.stop()
      }
    }))

    try {
      server.start()
    } catch {
      case e: Exception =>
        System.err.println(s"Failed to start server: ${e.getMessage}")
        System.exit(1)
    }
  }
}
