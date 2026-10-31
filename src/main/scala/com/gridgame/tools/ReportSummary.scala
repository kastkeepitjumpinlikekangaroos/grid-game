package com.gridgame.tools

import com.google.gson.{JsonArray, JsonElement, JsonObject, JsonParser}
import com.gridgame.common.diagnostics.Recording

import scala.jdk.CollectionConverters._

/** A moment the player marked: from the recording's MARK record, or report.json's list where it has none. */
final case class MarkInfo(number: Int, atMicros: Option[Long], matchTime: String, screenshot: String)

/** A set of pictures before a mark or an incident, and what became of its contact sheet. */
final case class FramesRead(set: FrameSet, sheetTiles: Int, skipped: Vector[String]) {
  def sheetWritten: Boolean = sheetTiles > 0
}

/**
 * The report in human terms (summary.txt): who sent it and about what, what they said, their machine,
 * the match and who was in it, the moments they marked and what went wrong, the server's note, the
 * files, and what the recording holds. Every field of report.json is optional, and one this tool
 * doesn't know is still shown, under its own name.
 */
object ReportSummary {
  import ReportPackets.{num, time}

  def parseJson(text: String): Either[String, JsonObject] =
    try {
      val e = JsonParser.parseString(text)
      if (e.isJsonObject) Right(e.getAsJsonObject) else Left("not a JSON object")
    } catch { case e: Exception => Left(Option(e.getMessage).getOrElse(e.getClass.getSimpleName)) }

  def str(o: JsonObject, k: String): Option[String] =
    Option(o.get(k)).filter(e => !e.isJsonNull).map(e => if (e.isJsonPrimitive) e.getAsString else e.toString).map(Clean.line)

  def obj(o: JsonObject, k: String): Option[JsonObject] = Option(o.get(k)).filter(_.isJsonObject).map(_.getAsJsonObject)

  def arr(o: JsonObject, k: String): Option[JsonArray] = Option(o.get(k)).filter(_.isJsonArray).map(_.getAsJsonArray)

  def long(o: JsonObject, k: String): Option[Long] =
    Option(o.get(k)).filter(e => e.isJsonPrimitive && e.getAsJsonPrimitive.isNumber).map(_.getAsLong)

  /** Any JSON value on one line: text as it is, a list joined, an object as {key: value, ...}. */
  def show(e: JsonElement): String =
    if (e == null || e.isJsonNull) "-"
    else if (e.isJsonPrimitive) Clean.line(e.getAsString)
    else if (e.isJsonArray) e.getAsJsonArray.asScala.map(show).mkString(", ")
    else e.getAsJsonObject.entrySet().asScala.map(en => s"${Clean.line(en.getKey)}: ${show(en.getValue)}").mkString("{", ", ", "}")

  val Categories: Map[String, String] = Map(
    "movement" -> "Movement", "hits" -> "Hits & damage", "abilities" -> "Abilities", "lag" -> "Lag or disconnects",
    "graphics" -> "Graphics", "sound" -> "Sound", "crash" -> "Crash or freeze", "other" -> "Something else")

  private val TopLevel = Set("format", "reportId", "kind", "createdAt", "category", "description", "whenInMatch",
    "client", "system", "graphics", "connection", "match", "marks", "incidents", "previousRun", "files", "noticed",
    "chosenMoment")

  /** What the client noticed: the recording's NOTICED records, then any of report.json's list the recording
    * lost (a missing segment), in time order. */
  def noticed(report: Option[JsonObject], recording: ReportRecording): Vector[NoticedView] = {
    val fromRecording = recording.records.filter(_.kind == Recording.Noticed)
      .flatMap(r => ReportPackets.noticed(r.timeMicros, r.payload))
    def kindOf(name: String): Int = (1 to 64).find(k => Recording.NoticedKind.name(k) == name).getOrElse(0)
    val fromJson = report.flatMap(arr(_, "noticed")).toVector.flatMap(_.asScala).filter(_.isJsonObject).map(_.getAsJsonObject).flatMap { n =>
      long(n, "atMicros").map { at =>
        val kind = str(n, "kind").map(kindOf).getOrElse(0)
        NoticedView(at, kind, long(n, "a").map(_.toInt).getOrElse(0), long(n, "b").map(_.toInt).getOrElse(0),
          str(n, "detail").getOrElse(""), if (kind == 0) str(n, "text").orElse(str(n, "kind")).getOrElse("") else "")
      }
    }
    // One the recording holds is the same one report.json lists: within a millisecond, of the same kind
    val missing = fromJson.filterNot(j => fromRecording.exists(r => r.kind == j.kind && Math.abs(r.atMicros - j.atMicros) <= 1000L))
    (fromRecording ++ missing).sortBy(_.atMicros)
  }

  /** When the player said on the report card it happened, if they did. */
  def chosen(report: Option[JsonObject]): Option[ChosenMoment] =
    report.flatMap(obj(_, "chosenMoment")).flatMap { c =>
      val at = long(c, "atMicros").orElse(str(c, "matchTime").flatMap(t => try Some(ReportTool.parseMoment(t)) catch { case _: Exception => None }))
      at.map(a => ChosenMoment(a, str(c, "text").getOrElse(""), str(c, "source").getOrElse("?")))
    }

  /** Each incident report.json lists, by its index (or its place in the list), to its moment. */
  def incidentTimes(report: Option[JsonObject]): Map[Int, Long] =
    report.flatMap(arr(_, "incidents")).toVector.flatMap(_.asScala).filter(_.isJsonObject).map(_.getAsJsonObject).zipWithIndex
      .flatMap { case (i, position) => long(i, "atMicros").map(at => long(i, "index").map(_.toInt).getOrElse(position) -> at) }.toMap

  /** "a moment the client noticed" and so on: where the player picked the moment from. */
  def sourceName(source: String): String = source match {
    case "mark" => "a moment they marked"
    case "noticed" => "a moment the client noticed"
    case "incident" => "something that went wrong"
    case "timeline" => "the match's timeline"
    case other => other
  }

  /** The marks of a report: the recording's, which say when they were made to the microsecond, then any
    * report.json lists that the recording lost (a missing segment). */
  def marks(report: Option[JsonObject], recording: ReportRecording): Vector[MarkInfo] = {
    val fromJson = report.flatMap(arr(_, "marks")).toVector.flatMap(_.asScala).filter(_.isJsonObject).map(_.getAsJsonObject).map { m =>
      MarkInfo(long(m, "number").map(_.toInt).getOrElse(0), long(m, "atMicros"), str(m, "matchTime").getOrElse(""),
        str(m, "screenshot").getOrElse(""))
    }
    val fromRecording = recording.records.filter(r => r.kind == Recording.Mark && r.payload.length >= 2).map { r =>
      val n = Recording.ushort(r.payload, 0)
      val file = new String(r.payload, 2, r.payload.length - 2, java.nio.charset.StandardCharsets.UTF_8)
      val json = fromJson.find(_.number == n)
      MarkInfo(n, Some(r.timeMicros), json.map(_.matchTime).getOrElse(""), Clean.line(if (file.nonEmpty) file else json.map(_.screenshot).getOrElse("")))
    }
    (fromRecording ++ fromJson.filterNot(j => fromRecording.exists(_.number == j.number))).sortBy(_.number)
  }

  def text(files: ReportFiles, report: Either[String, JsonObject], serverNote: Option[Either[String, JsonObject]],
           recording: ReportRecording, markList: Vector[MarkInfo], journalLines: Option[Int], written: Seq[String],
           noticedList: Vector[NoticedView] = Vector.empty, chosenMoment: Option[ChosenMoment] = None,
           frames: Vector[FramesRead] = Vector.empty): String = {
    val sb = new StringBuilder
    def line(s: String = ""): Unit = { sb.append(s); sb.append('\n') }
    def field(label: String, value: Option[String]): Unit = value.filter(_.nonEmpty).foreach(v => line(f"  $label%-14s $v"))
    /** Every field of `o` past `known`, under its own name. */
    def rest(o: JsonObject, known: Set[String]): Unit =
      o.entrySet().asScala.filterNot(e => known(e.getKey)).foreach(e => field(Clean.line(e.getKey) + ":", Some(show(e.getValue))))
    def section(title: String, o: Option[JsonObject], known: Seq[(String, String)]): Unit = o.foreach { j =>
      line(title)
      known.foreach { case (k, label) => field(label, Option(j.get(k)).map(show)) }
      rest(j, known.map(_._1).toSet)
      line()
    }

    val r = report.toOption
    val note = serverNote.flatMap(_.toOption)
    val code = note.flatMap(str(_, "code"))
      .orElse(files.folder.map(_.getFileName.toString).filter(_.matches("GG-[A-Z0-9]+")))
    val kind = r.flatMap(str(_, "kind")).getOrElse("?")
    val who = r.flatMap(obj(_, "connection")).flatMap(str(_, "player"))
      .orElse(note.flatMap(n => str(n, "account").orElse(str(n, "name")).orElse(str(n, "username"))))
    line(s"Problem report ${code.getOrElse("(no code: the server never had it)")}: a $kind report" + who.map(w => s" from $w").getOrElse(""))
    r.flatMap(str(_, "createdAt")).foreach(t => line(s"Made $t" + note.flatMap(str(_, "receivedAt")).map(rt => s"; the server had it $rt").getOrElse("")))
    report match { case Left(e) => line(s"report.json can't be read: $e"); case _ => }
    chosenMoment.foreach { c =>
      val shown = r.flatMap(obj(_, "chosenMoment")).flatMap(str(_, "matchTime")).getOrElse(matchClock(c.atMicros))
      line(s"The player says it was around $shown" + (if (c.text.nonEmpty) s": ${c.text}" else "") +
        s", from ${sourceName(c.source)} (timeline-chosen.txt)")
    }
    line()

    r.foreach { j =>
      line("What the player said")
      val category = str(j, "category").getOrElse("")
      field("Category:", Some(if (category.isEmpty) "(not given)" else Categories.getOrElse(category, category)))
      str(j, "whenInMatch").filter(_.nonEmpty).foreach(w => field("When:", Some(s"$w into the match")))
      val description = Option(j.get("description")).filter(e => e.isJsonPrimitive).map(e => Clean.lines(e.getAsString).trim).getOrElse("")
      if (description.isEmpty) field("Description:", Some("(none)"))
      else { line("  Description:"); description.split('\n').foreach(l => line(s"    | $l")) }
      line()

      section("The client", obj(j, "client"), Seq("version" -> "Version:", "commit" -> "Commit:", "dirty" -> "Changes:", "built" -> "Built:"))
      section("The machine", obj(j, "system"), Seq("os" -> "System:", "java" -> "Java:", "cpus" -> "Processors:",
        "maxHeapMb" -> "Max heap (MB):", "locale" -> "Locale:", "language" -> "Language:", "screens" -> "Screens:",
        "args" -> "Arguments:", "launch" -> "Launched as:"))
      section("Graphics", obj(j, "graphics"), Seq("vendor" -> "Vendor:", "renderer" -> "Renderer:", "version" -> "OpenGL:",
        "glsl" -> "GLSL:", "quality" -> "Quality:", "auto" -> "Auto quality:", "window" -> "Window:", "contentScale" -> "Content scale:"))
      section("The connection", obj(j, "connection"), Seq("server" -> "Server:", "player" -> "Player:", "playerId" -> "Player id:"))

      obj(j, "match").foreach { m =>
        line("The match")
        val known = Seq("matchId" -> "Match id:", "map" -> "Map:", "mode" -> "Mode:", "practice" -> "Practice:",
          "character" -> "Played as:", "durationMinutes" -> "Minutes:", "startedAt" -> "Started:", "endedAt" -> "Ended:",
          "ended" -> "How it ended:", "kills" -> "Kills:", "deaths" -> "Deaths:")
        known.foreach { case (k, label) => field(label, Option(m.get(k)).map(show)) }
        rest(m, known.map(_._1).toSet + "players")
        arr(m, "players").foreach { ps =>
          line(s"  Players (${ps.size}):")
          ps.asScala.filter(_.isJsonObject).map(_.getAsJsonObject).foreach { p =>
            val bot = Option(p.get("bot")).exists(e => e.isJsonPrimitive && e.getAsString == "true")
            val others = p.entrySet().asScala.filterNot(e => Set("id", "name", "character", "team", "bot")(e.getKey))
              .map(e => s"${Clean.line(e.getKey)} ${show(e.getValue)}")
            line(f"    ${str(p, "id").map(_.take(8)).getOrElse("-")}%-8s  ${str(p, "name").getOrElse("?")}%-20s " +
              f"${str(p, "character").getOrElse("?")}%-16s team ${str(p, "team").getOrElse("0")}" +
              (if (bot) "  (bot)" else "") + (if (others.isEmpty) "" else others.mkString("  ", "  ", "")))
          }
        }
        line()
      }
    }

    line(s"Marks (${markList.size})")
    if (markList.isEmpty) line("  none: the player marked no moment (F8 in a match)")
    markList.foreach { m =>
      val at = m.atMicros.map(t => s"at ${time(t)} of the recording").getOrElse("(not in the recording)")
      val shown = if (m.matchTime.nonEmpty) s", ${m.matchTime} by the match's clock" else ""
      val shot = if (m.screenshot.nonEmpty) s"; screenshot ${m.screenshot}" else "; no screenshot"
      line(s"  #${m.number} $at$shown$shot   (timeline-mark-${m.number}.txt)")
      frames.find(_.set.label == s"mark ${m.number}").foreach(f => line(s"     ${framesLine(f)}"))
    }
    line()

    val incidentsJson = r.flatMap(arr(_, "incidents")).toVector.flatMap(_.asScala).filter(_.isJsonObject).map(_.getAsJsonObject)
    val incidentsRec = recording.records.filter(_.kind == Recording.Incident)
    line(s"What went wrong (${Math.max(incidentsJson.size, incidentsRec.size)})")
    if (incidentsRec.nonEmpty) incidentsRec.foreach { rec =>
      val kind = if (rec.payload.isEmpty) 0 else rec.payload(0) & 0xFF
      val detail = if (rec.payload.length > 1) Clean.line(new String(rec.payload, 1, rec.payload.length - 1, java.nio.charset.StandardCharsets.UTF_8)) else ""
      line(s"  ${time(rec.timeMicros)}  ${Recording.IncidentKind.name(kind)}  $detail")
    } else if (incidentsJson.nonEmpty) incidentsJson.foreach { i =>
      line(s"  ${long(i, "atMicros").map(time).getOrElse("--:--.---")}  ${str(i, "kind").getOrElse("?")}  ${str(i, "detail").getOrElse("")}")
    } else line("  nothing the client noticed")
    line()

    line(s"What the client noticed (${noticedList.size})")
    if (noticedList.isEmpty) line("  nothing: no snap-back, unanswered shot, hitch, silence, death, refusal or ping spike")
    noticedList.foreach(n => line(s"  ${time(n.atMicros)}  ${ReportPackets.describeNoticed(n)}"))
    if (noticedList.nonEmpty)
      line("  By kind: " + noticedList.groupMapReduce(_.name)(_ => 1)(_ + _).toSeq.sortBy { case (k, c) => (-c, k) }
        .map { case (k, c) => s"$k $c" }.mkString(", ") + "   (noticed.csv)")
    line()

    if (frames.nonEmpty) {
      line(s"Pictures before marks and incidents (${frames.size})")
      frames.foreach(f => line(s"  ${f.set.label}: ${framesLine(f)}"))
      line()
    }

    r.foreach(j => section("The run before (it didn't close properly)", obj(j, "previousRun"),
      Seq("pid" -> "Process:", "startedAt" -> "Started:", "lastSeenAt" -> "Last seen:", "hsErr" -> "JVM crash file:")))

    line("The server's note (server.json)")
    serverNote match {
      case None => line("  none: not a folder the server kept (a saved copy, or a report that was never sent)")
      case Some(Left(e)) => line(s"  can't be read: $e")
      case Some(Right(n)) => n.entrySet().asScala.foreach(e => field(Clean.line(e.getKey) + ":", Some(show(e.getValue))))
    }
    journalLines.foreach(n => line(s"  The server's journal of the match: match-journal.txt ($n lines)"))
    line()

    line("Files in the report")
    files.entries.foreach { case (name, bytes) =>
      val unsafe = if (ReportFiles.safeRelative(name).isEmpty) "  (not copied out: its name would climb out of the folder)" else ""
      line(f"  ${Clean.line(name)}%-40s ${bytes.length}%10d bytes$unsafe")
    }
    if (files.entries.isEmpty) line("  none")
    line()

    line("The recording")
    if (recording.segments.isEmpty && recording.unreadable.isEmpty) line("  none (a report about no match)")
    else {
      line(s"  Segments: ${recording.segments.map(_.number).mkString(", ")}")
      recording.segments.foreach { s =>
        if (s.compressed) {
          val ratio = if (s.bytes > 0 && s.inflatedBytes > 0) s", ${num(s.inflatedBytes.toDouble / s.bytes, 1)}x" else ""
          val cut =
            if (s.damaged) "; its compressed stream breaks off into something else: cut short there"
            else if (!s.endMarked && s.partialRecord) "; cut short: the client stopped writing it partway through a record, before closing it"
            else if (!s.endMarked) "; cut short: the client stopped writing it before closing it (a crash?)"
            else if (s.partialRecord) "; a record at its end was cut in two"
            else ""
          line(s"    ${s.number}: ${time(s.firstMicros)} to ${time(s.lastMicros)}, ${s.records} records, version ${s.version}, " +
            s"${s.bytes} bytes compressed (${s.inflatedBytes} uncompressed$ratio) (${s.file})$cut")
        } else {
          val cut = if (s.cutShortBytes > 0) s"; its last ${s.cutShortBytes} bytes were a record cut short" else ""
          line(s"    ${s.number}: ${time(s.firstMicros)} to ${time(s.lastMicros)}, ${s.records} records, version ${s.version}, " +
            s"${s.bytes} bytes (${s.file})$cut")
        }
      }
      recording.missing.foreach { case (a, b, before, after) =>
        val which = if (a == b) s"segment $a" else s"segments $a-$b"
        val span = (before, after) match {
          case (Some(x), Some(y)) => s"nothing kept from ${time(x)} to ${time(y)}"
          case (None, Some(y)) => s"nothing kept before ${time(y)}: the match's start is missing"
          case _ => "nothing kept of that stretch"
        }
        line(s"  Missing: $which ($span): the client keeps a long match's start and its newest part")
      }
      val byKind = recording.countsByKind.toSeq.sortBy(-_._2).map { case (k, n) => s"${Recording.kindName(k)} $n" }
      line(s"  Records: ${recording.records.size}${if (byKind.isEmpty) "" else byKind.mkString(" (", ", ", ")")}")
      if (recording.unknownKinds.nonEmpty)
        line(s"  Skipped: ${recording.unknownKinds.values.sum} records of kinds this tool doesn't know (" +
          recording.unknownKinds.toSeq.sorted.map { case (k, n) => s"kind $k: $n" }.mkString(", ") + ")")
      if (recording.gaps > 0) line(s"  Lost: ${recording.lostRecords} records, in ${recording.gaps} gaps (the recorder had nowhere to put them)")
      recording.unreadable.foreach { case (name, why) => line(s"  Unreadable: ${Clean.line(name)}: ${Clean.line(why)}") }
      if (!recording.isEmpty) {
        val span = (recording.lastMicros - recording.firstMicros) / 1e6
        val recorded = if (recording.holes.isEmpty) "" else s", ${num(recording.coveredMicros / 1e6, 1)}s of it recorded"
        line(s"  Covers ${time(recording.firstMicros)} to ${time(recording.lastMicros)} (${num(span, 1)}s$recorded)" +
          recording.startedAtMs.map(ms => s"; it began ${java.time.Instant.ofEpochMilli(ms)}").getOrElse(""))
      }
    }
    line()

    r.foreach { j =>
      val others = j.entrySet().asScala.filterNot(e => TopLevel(e.getKey))
      if (others.nonEmpty) {
        line("Other fields of report.json")
        others.foreach(e => field(Clean.line(e.getKey) + ":", Some(show(e.getValue))))
        line()
      }
    }

    if (files.warnings.nonEmpty) {
      line("Warnings")
      files.warnings.foreach(w => line(s"  ${Clean.line(w)}"))
      line()
    }

    if (written.nonEmpty) line(s"Written: ${written.mkString(", ")}")
    sb.toString
  }

  /** "2:31": a moment in the match's terms, as the report card shows them. */
  private def matchClock(micros: Long): String = {
    val secs = Math.max(0L, micros / 1000000L)
    f"${secs / 60}%d:${secs % 60}%02d"
  }

  /** "60 pictures from 02:15.250 to 02:29.750 (15.0s to 0.25s before it): mark-1-frames.png" */
  private def framesLine(f: FramesRead): String = {
    val set = f.set
    val span = set.atMicros.map(m => s" (${num((m - set.firstMicros) / 1e6, 2)}s to ${num((m - set.lastMicros) / 1e6, 2)}s before it)").getOrElse("")
    val sheet = if (f.sheetWritten) s": ${set.sheet}" + (if (f.sheetTiles < set.frames.size) s" (${f.sheetTiles} of them)" else "")
                else ": no contact sheet (not one of them could be read)"
    val skipped = if (f.skipped.isEmpty) "" else s"; couldn't read ${f.skipped.size}: ${f.skipped.map(Clean.line).mkString(", ")}"
    s"${set.frames.size} pictures from ${time(set.firstMicros)} to ${time(set.lastMicros)}$span$sheet$skipped"
  }
}
