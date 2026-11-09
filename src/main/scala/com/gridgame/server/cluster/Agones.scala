package com.gridgame.server.cluster

import com.google.gson.{JsonObject, JsonParser}

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.time.Duration

/**
 * A match server's word with Agones, when Kubernetes runs it as an Agones GameServer
 * (ops/kubernetes): through the SDK's sidecar, over HTTP on localhost (AGONES_SDK_HTTP_PORT). It
 * says it is healthy every couple of seconds; Ready once the central server has it and whenever it
 * plays no match, Allocated while it plays any (a fleet scaling down deletes only Ready
 * GameServers); and how many it plays on its `matches` counter, which the fleet's autoscaler keeps
 * enough room on. It learns from it the address and port its players reach it on, and how many
 * matches it may play at once (the counter's capacity).
 */
final class Agones(port: Int) {
  private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()
  private val base = s"http://localhost:$port"

  private def send(method: String, path: String, body: String): Boolean =
    try {
      val req = HttpRequest.newBuilder(URI.create(base + path))
        .timeout(Duration.ofSeconds(5))
        .header("Content-Type", "application/json")
        .method(method, HttpRequest.BodyPublishers.ofString(body)).build()
      val res = http.send(req, HttpResponse.BodyHandlers.ofString())
      val ok = res.statusCode() / 100 == 2
      if (!ok) System.err.println(s"Agones: $method $path: ${res.statusCode()} ${res.body()}")
      ok
    } catch { case e: Exception => System.err.println(s"Agones: $method $path failed: $e"); false }

  def ready(): Boolean = send("POST", "/ready", "{}")
  def health(): Boolean = send("POST", "/health", "{}")
  def allocate(): Boolean = send("POST", "/allocate", "{}")
  def shutdown(): Boolean = send("POST", "/shutdown", "{}")

  /** Set a counter's count (one the GameServer's spec declares). */
  def setCount(counter: String, count: Long): Boolean =
    send("PATCH", s"/v1beta1/counters/$counter", s"""{"count": $count}""")

  /** Set a counter's capacity. */
  def setCapacity(counter: String, capacity: Long): Boolean =
    send("PATCH", s"/v1beta1/counters/$counter", s"""{"capacity": $capacity}""")

  /** What Agones says of this GameServer: its `status`, or None if the sidecar didn't answer. */
  def status(): Option[JsonObject] =
    try {
      val req = HttpRequest.newBuilder(URI.create(base + "/gameserver")).timeout(Duration.ofSeconds(5)).GET().build()
      val body = http.send(req, HttpResponse.BodyHandlers.ofString()).body()
      Option(JsonParser.parseString(body).getAsJsonObject.getAsJsonObject("status"))
    } catch { case e: Exception => System.err.println(s"Agones: /gameserver failed: $e"); None }
}

object Agones {
  /** The counter a match server keeps its matches on (the Fleet declares it, with its capacity). */
  val MatchesCounter = "matches"

  /** Agones' sidecar, if this process runs beside one. */
  def fromEnv(): Option[Agones] =
    Option(System.getenv("AGONES_SDK_HTTP_PORT")).filter(_.nonEmpty).map(p => new Agones(p.toInt))

  /** The address and port a GameServer's `status` gives it, once it has them. */
  def addressOf(status: JsonObject): Option[(String, Int)] = {
    val address = if (status.has("address") && !status.get("address").isJsonNull) status.get("address").getAsString else ""
    val ports = if (status.has("ports") && status.get("ports").isJsonArray) status.getAsJsonArray("ports") else null
    if (address.isEmpty || ports == null || ports.size() == 0) None
    else Some(address -> ports.get(0).getAsJsonObject.get("port").getAsInt)
  }

  /** A counter's capacity in a GameServer's `status`, if it declares the counter (its int64s come
    * as strings or numbers). */
  def capacityOf(status: JsonObject, counter: String): Option[Long] =
    try {
      val counters = if (status.has("counters") && status.get("counters").isJsonObject) status.getAsJsonObject("counters") else null
      val c = if (counters != null && counters.has(counter)) counters.getAsJsonObject(counter) else null
      if (c == null || !c.has("capacity")) None else Some(c.get("capacity").getAsLong)
    } catch { case _: Exception => None }
}
