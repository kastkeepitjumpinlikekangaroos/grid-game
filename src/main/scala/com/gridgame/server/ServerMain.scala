package com.gridgame.server

import com.gridgame.common.Constants

import java.net.SocketException

object ServerMain {

  def main(args: Array[String]): Unit = {
    var port = Constants.SERVER_PORT

    if (args.length > 0) {
      try {
        port = args(0).toInt
      } catch {
        case _: NumberFormatException =>
          System.err.println(s"Invalid port number: ${args(0)}")
          System.err.println("Usage: ServerMain [port]")
          System.exit(1)
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
      case e: SocketException =>
        System.err.println(s"Failed to start server: ${e.getMessage}")
        System.exit(1)
    }
  }
}
