package com.gridgame.tools

import com.gridgame.common.diagnostics.Recording
import com.gridgame.common.protocol.PacketType

import java.io.Writer

/** A stretch of the recording to look at: from and to, in microseconds since it began, and what it is around. */
final case class Window(fromMicros: Long, toMicros: Long, label: String) {
  def contains(t: Long): Boolean = t >= fromMicros && t <= toMicros
}

object Window {
  /** `halfSeconds` either side of `centreMicros`. */
  def around(centreMicros: Long, halfSeconds: Double, label: String): Window = {
    val half = (halfSeconds * 1e6).toLong
    Window(Math.max(0L, centreMicros - half), centreMicros + half, label)
  }
}

/** When the player said on the report card that the problem happened, and what they picked it by. */
final case class ChosenMoment(atMicros: Long, text: String, source: String)

/**
 * The recording as a timeline: a line a record, in time order, "mm:ss.mmm" first. Packets are decoded
 * field by field, inputs named, the player's marks and what went wrong shouted (`>>> MARK 1`,
 * `!!! FREEZE`), and what the client noticed for itself said (`~~~ NOTICED`). Frames are a line only
 * when they were a hitch the player saw, and states only when something in them changed (the first a
 * window shows is given whole). Where segments are missing, a line says so and what stretch of the
 * match went with them; where the player said it happened, a line says that (`*** CHOSEN`).
 *
 * A window still reads every record before it, to know every player by name when it starts.
 */
final class ReportTimeline(recording: ReportRecording, seedNames: PlayerNames, screenshots: Map[Int, String],
                           chosen: Option[ChosenMoment] = None, pictures: Vector[FrameSet] = Vector.empty) {
  /** "  (5 pictures before it: mark-1-frames.png)" for the set `label` names, or one at that moment. */
  private def picturesOf(label: String, at: Long): String =
    pictures.find(p => p.label == label || (label.isEmpty && p.label.startsWith("incident") && p.atMicros.exists(m => Math.abs(m - at) <= 1000L)))
      .map(p => s"  (${p.frames.size} pictures before it: ${p.sheet})").getOrElse("")

  import ReportPackets._

  /** Write the timeline (all of it, or `window` of it) to `out`; how many lines it came to. */
  def write(out: Writer, window: Option[Window]): Int = {
    val names = seedNames.copy()
    var lines = 0
    def emit(t: Long, tag: String, text: String): Unit = {
      out.write(time(t))
      out.write("  ")
      out.write(tag)
      var pad = tag.length
      while (pad < 9) { out.write(' '); pad += 1 }
      out.write(' ')
      out.write(text)
      out.write('\n')
      lines += 1
    }
    window.foreach(w => { out.write(s"# ${w.label}: ${time(w.fromMicros)} to ${time(w.toMicros)}\n"); lines += 1 })

    val missingAfter = recording.missing.map { case (a, b, before, after) => (b + 1, (a, b, before, after)) }.toMap
    val firstSegment = recording.segments.headOption.map(_.number).getOrElse(0)
    var segment = Int.MinValue
    var lastState: StateView = null
    var stateShown = false
    var chosenShown = chosen.isEmpty
    def showChosen(): Unit = chosen.foreach { c =>
      chosenShown = true
      if (window.forall(_.contains(c.atMicros)))
        emit(c.atMicros, "*** CHOSEN", s"the player says it was around here: ${Clean.line(c.text)} (from ${ReportSummary.sourceName(c.source)})")
    }

    recording.records.foreach { r =>
      if (!chosenShown && chosen.exists(_.atMicros <= r.timeMicros)) showChosen()
      val inside = window.forall(_.contains(r.timeMicros))
      if (r.segment != segment) {
        segment = r.segment
        if (inside) {
          missingAfter.get(segment).foreach { case (a, b, before, after) =>
            val which = if (a == b) s"segment $a is" else s"segments $a-$b are"
            val span = (before, after) match {
              case (Some(x), Some(y)) => s"nothing was kept from ${time(x)} to ${time(y)}"
              case (None, Some(y)) => s"nothing was kept before ${time(y)}, the match's start among it"
              case _ => "nothing was kept of that stretch"
            }
            emit(r.timeMicros, "===", s"$which missing (the client keeps a long match's start and its newest part): $span")
          }
          val started = recording.segments.find(_.number == segment).map(s => s" (the recording began ${java.time.Instant.ofEpochMilli(s.startedAtMs)})").getOrElse("")
          emit(r.timeMicros, "===", s"segment $segment" + (if (segment == firstSegment) started else ""))
        }
      }
      r.kind match {
        case Recording.PacketIn | Recording.PacketOut =>
          val outgoing = r.kind == Recording.PacketOut
          if (inside) {
            val v = describe(r.payload, r.flags, outgoing, names)
            val dir = if (outgoing) "out" else "in"
            val transport = if ((r.flags & Recording.FlagTcp) != 0) "tcp" else "udp"
            val head = s"${v.typeName} #${v.seq} ${v.player}"
            val action = if (v.action.isEmpty) "" else s" ${v.action}"
            val at = if (v.x.isEmpty) "" else s" at (${v.x},${v.y})"
            val detail = if (v.detail.isEmpty) "" else s" ${v.detail}"
            emit(r.timeMicros, s"$dir $transport", s"$head$action$at$detail")
          } else if (r.payload.length == 64 && r.payload(0) == PacketType.PLAYER_JOIN.id && (r.flags & Recording.FlagRedacted) == 0) {
            describe(r.payload, r.flags, outgoing, names) // only to learn who it names
          }

        case Recording.Frame =>
          if (inside) frame(r.payload) match {
            case Some(f) if f.isHitch =>
              emit(r.timeMicros, "frame", s"HITCH ${num(f.deltaMs, 1)}ms since the last (this one took ${num(f.workMs, 1)}ms) " +
                s"at (${num(f.cellX, 0)},${num(f.cellY, 0)}) quality ${quality(f.quality)}" +
                (if (f.keys != 0) s" holding ${keysHeld(f.keys)}" else ""))
            case Some(_) =>
            case None => emit(r.timeMicros, "frame", s"(${r.payload.length} bytes: not a frame this tool reads)")
          }

        case Recording.State =>
          state(r.payload) match {
            case Some(s) =>
              if (inside) {
                if (!stateShown) { emit(r.timeMicros, "state", describeState(s)); stateShown = true }
                else if (lastState != null) {
                  val changes = stateChanges(lastState, s)
                  if (changes.nonEmpty) emit(r.timeMicros, "state", changes)
                }
              }
              lastState = s
            case None => if (inside) emit(r.timeMicros, "state", s"(${r.payload.length} bytes: not a state this tool reads)")
          }

        case Recording.Input =>
          if (inside) input(r.payload) match {
            case Some(v) => val (tag, text) = describeInput(v); emit(r.timeMicros, tag, text)
            case None => emit(r.timeMicros, "input", s"(${r.payload.length} bytes: not an input this tool reads)")
          }

        case Recording.Mark =>
          if (inside) {
            val n = if (r.payload.length >= 2) Recording.ushort(r.payload, 0) else 0
            val file = if (r.payload.length > 2) Clean.line(new String(r.payload, 2, r.payload.length - 2, java.nio.charset.StandardCharsets.UTF_8)) else ""
            val shot = screenshots.get(n).orElse(Option(file).filter(_.nonEmpty)).map(f => s"  screenshot $f").getOrElse("  (no screenshot)")
            emit(r.timeMicros, s">>> MARK $n", shot.trim + picturesOf(s"mark $n", r.timeMicros))
          }

        case Recording.Incident =>
          if (inside) {
            val kind = if (r.payload.isEmpty) 0 else r.payload(0) & 0xFF
            val detail = if (r.payload.length > 1) Clean.line(new String(r.payload, 1, r.payload.length - 1, java.nio.charset.StandardCharsets.UTF_8)) else ""
            emit(r.timeMicros, s"!!! ${Recording.IncidentKind.name(kind).toUpperCase(java.util.Locale.ROOT)}", detail + picturesOf("", r.timeMicros))
          }

        case Recording.Gap =>
          if (inside) {
            val lost = if (r.payload.length >= 4) Recording.int(r.payload, 0) & 0xFFFFFFFFL else 0L
            emit(r.timeMicros, "--- GAP", s"$lost records lost here: the recorder had nowhere to put them")
          }

        case Recording.Noticed =>
          if (inside) noticed(r.timeMicros, r.payload) match {
            case Some(v) => emit(r.timeMicros, "~~~ NOTICED", describeNoticed(v))
            case None => emit(r.timeMicros, "~~~ NOTICED", s"(${r.payload.length} bytes: not a noticed moment this tool reads)")
          }

        case Recording.Log => if (inside) emit(r.timeMicros, "log", Clean.line(r.text))
        case Recording.Event => if (inside) emit(r.timeMicros, "event", Clean.line(r.text))
        case Recording.Meta => if (inside) emit(r.timeMicros, "meta", Clean.line(r.text))
        case _ =>
      }
    }
    if (!chosenShown) showChosen()
    lines
  }
}
