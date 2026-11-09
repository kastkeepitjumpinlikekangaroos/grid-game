package com.gridgame.server.cluster

import com.gridgame.common.observability.Telemetry

/**
 * A match server's process (design/networking.md, *Match servers*; ops/kubernetes):
 *
 * {{{
 * match_server --central=host:port [--port=7777] [--public-host=addr] [--public-port=n] [--matches=n]
 *              [--drain-seconds=n] [--status-seconds=30] [--central-identity=SHA256:...] [--no-show-ms=n]
 * }}}
 *
 * Each flag can be given by the environment instead: GRIDGAME_CENTRAL, GRIDGAME_MATCH_PORT,
 * GRIDGAME_PUBLIC_HOST, GRIDGAME_PUBLIC_PORT, GRIDGAME_MATCHES, GRIDGAME_DRAIN_SECONDS,
 * GRIDGAME_STATUS_SECONDS, GRIDGAME_CENTRAL_IDENTITY, GRIDGAME_NO_SHOW_MS. The cluster's secret
 * only by the environment (GRIDGAME_CLUSTER_SECRET), never the command line, which anyone on the
 * machine can read.
 *
 * `--matches` is its capacity, in slots (an eight-player match is one: `MatchSpec.slotsFor`); beside
 * Agones' sidecar the capacity of the GameServer's `matches` counter is, unless the flag says
 * otherwise. Told to stop (SIGTERM, as Kubernetes deletes its pod), it takes no new match and plays
 * the ones it has to their end, for `--drain-seconds` at most (default 0: it stops at once), then
 * stops. `--status-seconds` is how often it logs its status line while it plays anything.
 */
object MatchServerMain {
  def main(args: Array[String]): Unit = {
    val flags = args.collect { case a if a.startsWith("--") =>
      val kv = a.drop(2).split("=", 2)
      kv(0) -> (if (kv.length > 1) kv(1) else "true")
    }.toMap
    def opt(flag: String, env: String): Option[String] =
      flags.get(flag).orElse(Option(System.getenv(env))).filter(_.nonEmpty)
    def int(flag: String, env: String): Option[Int] = opt(flag, env).map { v =>
      try v.toInt catch { case _: NumberFormatException => fail(s"--$flag=$v isn't a number") }
    }

    val secret = ControlLink.secretFromEnv()
    if (secret == null) fail("GRIDGAME_CLUSTER_SECRET isn't set: the match server and the central server must share one")
    val central = opt("central", "GRIDGAME_CENTRAL").getOrElse(fail("--central=host:port is missing (the central server's control port)"))
    val (centralHost, controlPort) = central.lastIndexOf(':') match {
      case i if i > 0 => (central.substring(0, i), central.substring(i + 1).toInt)
      case _ => fail(s"--central=$central isn't host:port")
    }
    val capacity = int("matches", "GRIDGAME_MATCHES")
    if (capacity.exists(_ < 1)) fail("--matches must be at least 1")
    val drainMs = int("drain-seconds", "GRIDGAME_DRAIN_SECONDS").getOrElse(0).max(0) * 1000L
    val config = MatchServer.Config(
      gamePort = int("port", "GRIDGAME_MATCH_PORT").getOrElse(7777),
      centralHost = centralHost,
      controlPort = controlPort,
      secret = secret,
      publicHost = opt("public-host", "GRIDGAME_PUBLIC_HOST"),
      publicPort = int("public-port", "GRIDGAME_PUBLIC_PORT"),
      capacity = capacity,
      centralIdentity = opt("central-identity", "GRIDGAME_CENTRAL_IDENTITY"),
      agones = Agones.fromEnv(),
      noShowMs = opt("no-show-ms", "GRIDGAME_NO_SHOW_MS").map(_.toLong).getOrElse(30000L),
      statusEveryMs = int("status-seconds", "GRIDGAME_STATUS_SECONDS").getOrElse(30).max(1) * 1000L
    )

    Telemetry.init("grid-game-match-server")
    val server = new MatchServer(config)
    Runtime.getRuntime.addShutdownHook(new Thread(() => {
      server.drainThenStop(drainMs)
      Telemetry.shutdown()
    }))
    server.start()
    Thread.currentThread().join()
  }

  private def fail(why: String): Nothing = {
    System.err.println(s"Match server: $why")
    System.exit(2)
    throw new IllegalStateException(why)
  }
}
