package com.gridgame.server

import com.gridgame.common.Constants
import com.gridgame.common.observability.Telemetry

object ServerMain {

  def main(args: Array[String]): Unit = {
    Telemetry.init("grid-game-server")

    var port = Constants.SERVER_PORT
    // Matches on match servers (design/networking.md, *Match servers*): their control port
    var controlPort = Option(System.getenv("GRIDGAME_CONTROL_PORT")).filter(_.nonEmpty).map(_.toInt).getOrElse(0)

    // Parse arguments: [port]
    println(s"Arguments received: ${args.mkString(", ")}")
    for (arg <- args) {
      println(s"Processing arg: '$arg'")
      if (arg.startsWith("--control-port=")) {
        controlPort = arg.stripPrefix("--control-port=").toInt
      } else if (arg.startsWith("--world=")) {
        println(s"Note: --world argument is ignored in lobby mode. Maps are selected per-lobby.")
      } else {
        try {
          port = arg.toInt
        } catch {
          case _: NumberFormatException =>
            System.err.println(s"Invalid argument: $arg")
            System.err.println("Usage: ServerMain [port] [--control-port=N]")
            System.exit(1)
        }
      }
    }

    val server = new GameServer(port)
    if (controlPort > 0) {
      val secret = com.gridgame.server.cluster.ControlLink.secretFromEnv()
      if (secret == null) {
        System.err.println("--control-port needs GRIDGAME_CLUSTER_SECRET: the secret the match servers prove they hold")
        System.exit(1)
      }
      server.enableMatchServers(controlPort, secret)
      println(s"Matches are played on match servers (control port $controlPort)")
    }

    Runtime.getRuntime.addShutdownHook(new Thread(new Runnable {
      def run(): Unit = {
        println("\nShutdown signal received...")
        server.stop()
        Telemetry.shutdown()
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
