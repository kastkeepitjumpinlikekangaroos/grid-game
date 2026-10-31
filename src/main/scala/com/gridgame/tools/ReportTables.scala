package com.gridgame.tools

import com.gridgame.common.diagnostics.Recording

import java.io.Writer
import scala.collection.mutable

/**
 * The recording as tables, for a spreadsheet or a plot: every frame (frames.csv), every state
 * (state.csv) and every packet (packets.csv), a row each, the time in seconds since the recording began.
 */
object ReportTables {
  import ReportPackets._

  def row(out: Writer, fields: String*): Unit = {
    var first = true
    fields.foreach { f =>
      if (!first) out.write(',')
      first = false
      out.write(if (f.exists(c => c == ',' || c == '"' || c == '\n' || c == '\r')) "\"" + f.replace("\"", "\"\"") + "\"" else f)
    }
    out.write('\n')
  }

  /** frames.csv: how long each frame took, where we were and aimed, what was held. */
  def writeFrames(recording: ReportRecording, out: Writer): Int = {
    row(out, "time_s", "delta_ms", "work_ms", "cell_x", "cell_y", "drawn_x", "drawn_y", "aim_x", "aim_y", "keys", "flags", "quality", "aiming")
    var n = 0
    recording.records.foreach { r =>
      if (r.kind == Recording.Frame) frame(r.payload).foreach { f =>
        row(out, seconds(r.timeMicros), num(f.deltaMs, 3), num(f.workMs, 3), num(f.cellX, 0), num(f.cellY, 0),
          num(f.drawnX, 3), num(f.drawnY, 3), num(f.aimX, 3), num(f.aimY, 3), keysHeld(f.keys), frameFlags(f.flags),
          quality(f.quality), if (f.aiming < 0) "" else slot(f.aiming + 1))
        n += 1
      }
    }
    n
  }

  /** state.csv: the client's idea of us, four times a second. */
  def writeStates(recording: ReportRecording, out: Writer): Int = {
    row(out, "time_s", "health", "max_health", "effects", "q_left_ms", "e_left_ms", "r_left_ms", "charge", "items",
      "kills", "deaths", "seconds_left", "round_trip_ms", "projectiles", "players", "server_moves", "knockbacks", "queued")
    var n = 0
    recording.records.foreach { r =>
      if (r.kind == Recording.State) state(r.payload).foreach { s =>
        row(out, seconds(r.timeMicros), s.health.toString, s.maxHealth.toString, effects(s.effects).mkString("+"),
          s.qLeftMs.toString, s.eLeftMs.toString, s.rLeftMs.toString, s.charge.toString, s.items.toString, s.kills.toString,
          s.deaths.toString, s.secondsLeft.toString, s.roundTripMs.toString, s.projectiles.toString, s.players.toString,
          s.serverMoves.toString, s.knockbacks.toString, s.queued.toString)
        n += 1
      }
    }
    n
  }

  /** noticed.csv: what the client noticed for itself, a row each (a silence's row at its end). */
  def writeNoticed(noticed: Vector[NoticedView], out: Writer): Int = {
    row(out, "time_s", "kind", "a", "b", "detail", "text")
    noticed.foreach(n => row(out, seconds(n.atMicros), n.name, n.a.toString, n.b.toString, Clean.line(n.detail), Clean.line(n.text)))
    noticed.size
  }

  /** packets.csv: every packet in and out, decoded as the timeline decodes it. */
  def writePackets(recording: ReportRecording, seedNames: PlayerNames, out: Writer): Int = {
    val names = seedNames.copy()
    row(out, "time_s", "dir", "transport", "type", "seq", "player", "action", "x", "y", "detail")
    var n = 0
    recording.records.foreach { r =>
      if (r.kind == Recording.PacketIn || r.kind == Recording.PacketOut) {
        val outgoing = r.kind == Recording.PacketOut
        val v = describe(r.payload, r.flags, outgoing, names)
        row(out, seconds(r.timeMicros), if (outgoing) "out" else "in", if ((r.flags & Recording.FlagTcp) != 0) "tcp" else "udp",
          v.typeName, v.seq.toString, v.player, v.action, v.x, v.y, v.detail)
        n += 1
      }
    }
    n
  }
}

/**
 * The connection and the frames in numbers (net.txt): packets a second by type each way, the heartbeats'
 * round trips, the longest the server went quiet on each transport, and how long frames took.
 */
final class ReportNet(recording: ReportRecording) {
  import ReportPackets._

  private def typeOf(p: Array[Byte]): String =
    if (p.length != 64) "?" else try com.gridgame.common.protocol.PacketType.fromId(p(0)).toString
    catch { case _: IllegalArgumentException => "type 0x%02x".formatLocal(java.util.Locale.ROOT, p(0) & 0xFF) }

  private def percentile(sorted: Array[Double], q: Double): Double =
    if (sorted.isEmpty) 0.0 else sorted(Math.min(sorted.length - 1, Math.max(0, Math.ceil(q * sorted.length).toInt - 1)))

  private def spread(values: Array[Double]): String = {
    val s = values.sorted
    if (s.isEmpty) "none"
    else s"p50 ${num(percentile(s, 0.5), 1)}ms  p90 ${num(percentile(s, 0.9), 1)}ms  p99 ${num(percentile(s, 0.99), 1)}ms  " +
      s"p99.9 ${num(percentile(s, 0.999), 1)}ms  max ${num(s.last, 1)}ms"
  }

  def write(out: Writer): Unit = {
    def line(s: String = ""): Unit = { out.write(s); out.write('\n') }
    val first = recording.firstMicros
    val last = recording.lastMicros
    // Rates are over what was recorded: a missing segment's stretch holds nothing, heard or not
    val span = Math.max(1e-6, recording.coveredMicros / 1e6)
    line("The connection and the frames, as the client saw them")
    line(s"The recording: ${time(first)} to ${time(last)} (${num(span, 1)}s of it recorded" +
      (if (recording.holes.isEmpty) ")" else s"; nothing of ${recording.holes.map { case (a, b) => s"${time(a)}-${time(b)}" }.mkString(", ")})"))
    line()

    // ── Packets a second, by type each way ──
    val totals = mutable.LinkedHashMap.empty[(String, String), Int].withDefaultValue(0)
    val perSecond = mutable.HashMap.empty[(String, String), mutable.HashMap[Long, Int]]
    val transports = mutable.HashMap.empty[String, Int].withDefaultValue(0)
    // ── Heartbeats: one sent (UDP) to the next echo (TCP) ──
    var pendingHeartbeat = -1L
    val roundTrips = mutable.ArrayBuffer.empty[(Long, Double)]
    var unanswered = 0
    var unasked = 0
    // ── Silences from the server, per transport ──
    val lastIn = mutable.HashMap.empty[String, Long]
    val silences = mutable.HashMap.empty[String, mutable.ArrayBuffer[(Long, Long)]]
    // A silence across a missing segment is only the parts of it either side that were recorded
    def silence(transport: String, from: Long, to: Long): Unit =
      silences.getOrElseUpdate(transport, mutable.ArrayBuffer.empty) ++= recording.recordedPieces(from, to)
    def quiet(transport: String, t: Long): Unit = {
      lastIn.get(transport).foreach(prev => silence(transport, prev, t))
      lastIn(transport) = t
    }
    // ── Frames ──
    val deltas = mutable.ArrayBuffer.empty[(Long, FrameView)]
    val clientTrips = mutable.ArrayBuffer.empty[Double]

    recording.records.foreach { r =>
      r.kind match {
        case Recording.PacketIn | Recording.PacketOut =>
          val dir = if (r.kind == Recording.PacketOut) "out" else "in"
          val transport = if ((r.flags & Recording.FlagTcp) != 0) "tcp" else "udp"
          val tpe = typeOf(r.payload)
          totals((dir, tpe)) += 1
          transports(s"$dir $transport") += 1
          val second = (r.timeMicros - first) / 1000000L
          val buckets = perSecond.getOrElseUpdate((dir, tpe), mutable.HashMap.empty)
          buckets(second) = buckets.getOrElse(second, 0) + 1
          if (tpe == "HEARTBEAT") {
            if (dir == "out") { if (pendingHeartbeat >= 0) unanswered += 1; pendingHeartbeat = r.timeMicros }
            else if (pendingHeartbeat >= 0) {
              // One sent before a missing segment and an echo after it are no round trip
              if (!recording.crossesHole(pendingHeartbeat, r.timeMicros))
                roundTrips += ((pendingHeartbeat, (r.timeMicros - pendingHeartbeat) / 1000.0))
              pendingHeartbeat = -1L
            }
            else unasked += 1
          }
          if (dir == "in") { quiet(transport, r.timeMicros); quiet("either", r.timeMicros) }
        case Recording.Frame => frame(r.payload).foreach(f => deltas += ((r.timeMicros, f)))
        case Recording.State => state(r.payload).foreach(s => if (s.roundTripMs >= 0) clientTrips += s.roundTripMs.toDouble)
        case _ =>
      }
    }
    if (pendingHeartbeat >= 0) unanswered += 1
    // The server quiet at the end, until the recording stopped, counts too: a dropped connection looks so
    lastIn.foreach { case (transport, prev) => if (last > prev) silence(transport, prev, last) }

    line("Packets by type (a second: the average over the recording, and the busiest second)")
    line("  %-4s %-20s %8s %8s %8s".formatLocal(java.util.Locale.ROOT, "dir", "type", "total", "avg/s", "peak/s"))
    totals.toSeq.sortBy { case ((dir, tpe), n) => (dir, -n, tpe) }.foreach { case ((dir, tpe), n) =>
      val peak = perSecond.get((dir, tpe)).map(_.values.max).getOrElse(0)
      line("  %-4s %-20s %8d %8s %8d".formatLocal(java.util.Locale.ROOT, dir, tpe, n, num(n / span, 1), peak))
    }
    if (totals.isEmpty) line("  no packets")
    line(s"  in: ${transports("in tcp")} by TCP, ${transports("in udp")} by UDP; out: ${transports("out tcp")} by TCP, ${transports("out udp")} by UDP")
    line()

    line("Heartbeat round trips (a HEARTBEAT sent, to the server's echo of it)")
    line(s"  ${roundTrips.size} answered, $unanswered not answered before the next went, $unasked echoes with none waiting")
    if (roundTrips.nonEmpty) {
      line(s"  ${spread(roundTrips.map(_._2).toArray)}")
      line("  slowest: " + roundTrips.sortBy(-_._2).take(10).map { case (t, ms) => s"${time(t)} ${num(ms, 1)}ms" }.mkString(", "))
    }
    if (clientTrips.nonEmpty) line(s"  the client's own reckoning (its states): ${spread(clientTrips.toArray)}")
    line()

    line("The longest the server went quiet")
    Seq("tcp", "udp", "either").foreach { transport =>
      val worst = silences.getOrElse(transport, mutable.ArrayBuffer.empty).sortBy { case (a, b) => -(b - a) }.take(5)
      val text = if (worst.isEmpty) "nothing heard"
                 else worst.map { case (a, b) => s"${num((b - a) / 1e6, 3)}s from ${time(a)}" }.mkString(", ")
      line(s"  $transport: $text")
    }
    line()

    line("Frames")
    if (deltas.isEmpty) line("  no frames")
    else {
      val hitches = deltas.count(_._2.isHitch)
      line(s"  ${deltas.size} frames, ${num(deltas.size / span, 1)} a second on average; $hitches hitches " +
        s"(over ${num(HitchDeltaMs, 0)}ms since the last, or over ${num(HitchWorkMs, 0)}ms in the making)")
      line(s"  between frames: ${spread(deltas.map(_._2.deltaMs.toDouble).toArray)}")
      line(s"  making a frame: ${spread(deltas.map(_._2.workMs.toDouble).toArray)}")
      line("  worst: " + deltas.sortBy(-_._2.deltaMs).take(10).map { case (t, f) =>
        s"${time(t)} ${num(f.deltaMs, 1)}ms (made in ${num(f.workMs, 1)}ms)" }.mkString(", "))
    }
  }
}
