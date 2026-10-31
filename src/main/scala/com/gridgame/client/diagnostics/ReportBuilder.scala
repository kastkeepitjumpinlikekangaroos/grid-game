package com.gridgame.client.diagnostics

import com.gridgame.common.diagnostics.Recording.{NoticedKind, ReportFiles}
import com.gridgame.common.protocol.ProblemReportKind
import com.google.gson.{GsonBuilder, JsonArray, JsonObject}

import java.io.{BufferedOutputStream, FileOutputStream, InputStream}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.UUID
import java.util.zip.{CRC32, CheckedOutputStream, Deflater, ZipEntry, ZipOutputStream}
import scala.jdk.CollectionConverters._

/**
 * A problem report put together (design/diagnostics.md, *What a report holds*): one zip in the outbox
 * with what the player said and everything that helps read it.
 *
 * {{{
 * report.json              what the player said (kind, category, description, when), the client's build,
 *                          the machine, its graphics, the connection, the match, the marks, what went wrong
 * recording/seg-NNNN.rec   the match's recording (Recording's format), if it is about one
 * screenshots/mark-N.jpg   the marked moments' pictures the player kept
 * screenshots/mark-N/      the seconds before each of them, a few frames a second (and incident-N/ for
 *                          a freeze's or a crash's)
 * logs/client.log          this run's log so far
 * logs/previous-client.log the run that crashed's log (a crash report)
 * crash/hs_err_pidN.log    the JVM's own account of that crash, if it wrote one
 * }}}
 */
object ReportBuilder {
  /**
   * What goes in a report.
   *
   * @param matchRecord       the match it is about (its recording goes in if `includeRecording`)
   * @param marks             the marked moments whose pictures go in
   * @param previousRun       the run it is about, for a crash report
   * @param connection        the server and the player, as report.json's "connection"
   * @param matchNow          what the client knows of the match as the report is made, over what was noted
   *                          as it went (the players, the score)
   */
  final case class Request(kind: Byte, category: String, description: String, whenInMatch: String,
                           matchRecord: Option[MatchRecord], includeRecording: Boolean, marks: Seq[MarkedMoment],
                           previousRun: Option[PreviousRun], connection: JsonObject, matchNow: JsonObject,
                           includeLog: Boolean = true, chosen: Option[ChosenMoment] = None)

  /** The most a report may be: what the server takes. */
  val MaxBytes: Long = 64L * 1024 * 1024

  private val gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

  def kindName(kind: Byte): String = kind match {
    case ProblemReportKind.MATCH => "match"
    case ProblemReportKind.CRASH => "crash"
    case _ => "general"
  }

  /** "2:31": a time in a match, from microseconds since its recording began. */
  def matchTime(micros: Long): String = {
    val s = Math.max(0L, micros / 1000000L)
    f"${s / 60}%d:${s % 60}%02d"
  }

  /** The files of a recording folder: its segments in order. */
  def segmentsIn(dir: Path): Seq[Path] =
    if (!Files.isDirectory(dir)) Nil
    else {
      val s = Files.list(dir)
      try s.iterator().asScala.filter(_.getFileName.toString.matches("seg-\\d+\\.rec")).toVector.sortBy(_.getFileName.toString)
      finally s.close()
    }

  /** Put the report together in `outbox`, ready to send: its note says what it is, its size and its CRC. */
  def build(req: Request, outbox: ReportOutbox, now: Long = System.currentTimeMillis()): OutboxEntry = {
    val id = UUID.randomUUID()
    // A marked moment's pictures, and the frames before it or an incident, are written off the thread
    // that draws: wait for the last of them
    if (req.marks.nonEmpty || req.matchRecord.exists(!_.incidents.isEmpty)) Diagnostics.screenshotsWritten()

    // What goes in, as (name in the zip, file)
    val segments: Seq[Path] =
      if (req.includeRecording) req.matchRecord.map(_.segments).orElse(req.previousRun.flatMap(_.recording).map(segmentsIn)).getOrElse(Nil)
      else Nil
    val shots: Seq[(MarkedMoment, Path)] = req.marks.flatMap(m => Option(m.screenshot).filter(Files.isRegularFile(_)).map(m -> _))
    val crashShots: Seq[Path] =
      if (req.kind == ProblemReportKind.CRASH && req.includeRecording)
        req.previousRun.flatMap(_.recording).toSeq.flatMap(d => list(d).filter(_.getFileName.toString.matches("mark-\\d+\\.jpg")))
      else Nil
    // The seconds before each mark kept and each incident the player would have seen
    val markFrames: Seq[(String, Path)] = if (!req.includeRecording) Nil else
      req.marks.flatMap(m => m.frames.map(f => ReportFiles.markFrame(m.number, f.atMicros / 1000L) -> f.path))
    val incidentFrames: Seq[(String, Path)] = if (!req.includeRecording) Nil else
      req.matchRecord.toSeq.flatMap(_.incidents.asScala.zipWithIndex).flatMap { case (n, i) =>
        n.frames.map(f => ReportFiles.incidentFrame(i + 1, f.atMicros / 1000L) -> f.path)
      }
    val files: Seq[(String, Path)] =
      segments.map(p => s"recording/${p.getFileName}" -> p) ++
        shots.map { case (m, p) => ReportFiles.markShot(m.number) -> p } ++
        markFrames ++ incidentFrames ++
        crashShots.map(p => s"screenshots/${p.getFileName}" -> p) ++
        (if (req.includeLog) Option(Diagnostics.log).map(l => "logs/client.log" -> l.path).toSeq else Nil) ++
        req.previousRun.flatMap(_.log).map("logs/previous-client.log" -> _) ++
        req.previousRun.flatMap(_.hsErr).map(p => s"crash/${p.getFileName}" -> p)
    val present = files.filter { case (_, p) => Files.isRegularFile(p) }

    val json = reportJson(id, req, now, shots, crashShots, present)
    val zipFile = outbox.zip(id)
    Files.createDirectories(outbox.dir)
    val crc = new CRC32
    val out = new ZipOutputStream(new CheckedOutputStream(new BufferedOutputStream(new FileOutputStream(zipFile.toFile), 1 << 16), crc))
    try {
      out.setLevel(Deflater.DEFAULT_COMPRESSION)
      out.putNextEntry(new ZipEntry("report.json"))
      out.write(gson.toJson(json).getBytes(StandardCharsets.UTF_8))
      out.closeEntry()
      for ((name, file) <- present) {
        // Pictures are JPEG and the recording's segments compressed already: stored as they are
        // rather than squeezed again
        out.setLevel(if (name.endsWith(".jpg") || name.endsWith(".rec")) Deflater.NO_COMPRESSION else Deflater.DEFAULT_COMPRESSION)
        out.putNextEntry(new ZipEntry(name))
        copy(file, out)
        out.closeEntry()
      }
    } finally out.close()

    val size = Files.size(zipFile)
    val matchId = req.matchRecord.flatMap(r => str(r.matchInfo, "matchId"))
      .orElse(str(req.matchNow, "matchId"))
      .orElse(req.previousRun.flatMap(_.recording).flatMap(d => str(Diagnostics.matchInfoIn(d), "matchId")))
      .flatMap(s => try Some(UUID.fromString(s)) catch { case _: IllegalArgumentException => None }).orNull
    val summary = Seq(req.category, req.description.linesIterator.find(_.trim.nonEmpty).getOrElse("").take(80)).filter(_.nonEmpty).mkString(": ")
    val entry = OutboxEntry(id, req.kind, now, size, crc.getValue.toInt, matchId, summary)
    outbox.save(entry)
    println(s"Diagnostics: report $id (${kindName(req.kind)}) made: ${size / 1024}KB, ${present.size + 1} files")
    entry
  }

  private def reportJson(id: UUID, req: Request, now: Long, shots: Seq[(MarkedMoment, Path)], crashShots: Seq[Path],
                         files: Seq[(String, Path)]): JsonObject = {
    val o = new JsonObject
    o.addProperty("format", 1)
    o.addProperty("reportId", id.toString)
    o.addProperty("kind", kindName(req.kind))
    o.addProperty("createdAt", Diagnostics.iso(now))
    o.addProperty("category", req.category)
    o.addProperty("description", req.description)
    o.addProperty("whenInMatch", req.whenInMatch)
    o.add("client", SystemInfo.client)
    o.add("system", Diagnostics.system)
    Option(Diagnostics.graphics).foreach(g => o.add("graphics", g))
    if (req.connection != null) o.add("connection", req.connection)

    // The match: what was noted as it went, and over it what the client knows now
    val m = req.matchRecord.map(_.matchInfo)
      .orElse(if (req.kind == ProblemReportKind.CRASH) req.previousRun.flatMap(_.recording).map(Diagnostics.matchInfoIn) else None)
      .getOrElse(new JsonObject)
    if (req.matchNow != null) req.matchNow.entrySet().asScala.foreach(e => m.add(e.getKey, e.getValue))
    if (m.size() > 0) o.add("match", m)

    val present = files.map(_._1).toSet
    def frames(pictures: Seq[FramePicture], path: FramePicture => String): JsonArray = {
      val a = new JsonArray
      pictures.map(p => p -> path(p)).filter { case (_, name) => present(name) }.foreach { case (p, name) =>
        val j = new JsonObject
        j.addProperty("path", name)
        j.addProperty("atMicros", p.atMicros)
        a.add(j)
      }
      a
    }

    val marks = new JsonArray
    req.marks.foreach { mark =>
      val j = new JsonObject
      j.addProperty("number", mark.number)
      j.addProperty("atMicros", mark.atMicros)
      j.addProperty("matchTime", matchTime(mark.atMicros))
      if (shots.exists(_._1 == mark)) j.addProperty("screenshot", ReportFiles.markShot(mark.number))
      val before = frames(mark.frames, f => ReportFiles.markFrame(mark.number, f.atMicros / 1000L))
      if (before.size > 0) j.add("frames", before)
      marks.add(j)
    }
    crashShots.foreach { p =>
      val j = new JsonObject
      j.addProperty("number", p.getFileName.toString.stripPrefix("mark-").stripSuffix(".jpg").toIntOption.getOrElse(0))
      j.addProperty("screenshot", s"screenshots/${p.getFileName}")
      marks.add(j)
    }
    o.add("marks", marks)

    val incidents = new JsonArray
    val notes = req.matchRecord.map(_.incidents.asScala.toSeq).getOrElse(Nil) ++
      (if (req.kind == ProblemReportKind.GENERAL) Diagnostics.sessionIncidents.asScala.toSeq else Nil)
    notes.zipWithIndex.foreach { case (n, i) =>
      val j = new JsonObject
      j.addProperty("index", i + 1)
      j.addProperty("kind", com.gridgame.common.diagnostics.Recording.IncidentKind.name(n.kind))
      j.addProperty("atMicros", n.atMicros)
      j.addProperty("matchTime", matchTime(n.atMicros))
      j.addProperty("detail", n.detail)
      val before = frames(n.frames, f => ReportFiles.incidentFrame(i + 1, f.atMicros / 1000L))
      if (before.size > 0) j.add("frames", before)
      incidents.add(j)
    }
    o.add("incidents", incidents)

    // What the client noticed for itself, in the match the report is about
    val noticed = new JsonArray
    req.matchRecord.toSeq.flatMap(_.noticed.asScala).foreach { n =>
      val j = new JsonObject
      j.addProperty("kind", NoticedKind.name(n.kind))
      j.addProperty("atMicros", n.atMicros)
      j.addProperty("matchTime", matchTime(n.atMicros))
      j.addProperty("a", n.a)
      j.addProperty("b", n.b)
      j.addProperty("detail", n.detail)
      j.addProperty("text", NoticedKind.describe(n.kind, n.a, n.b, n.detail))
      noticed.add(j)
    }
    o.add("noticed", noticed)

    // When the player says it happened, if they picked it on the report's timeline
    req.chosen.foreach { c =>
      val j = new JsonObject
      j.addProperty("atMicros", c.atMicros)
      j.addProperty("matchTime", matchTime(c.atMicros))
      j.addProperty("source", c.source)
      j.addProperty("text", c.text)
      o.add("chosenMoment", j)
    }

    req.previousRun.foreach { r =>
      val j = new JsonObject
      j.addProperty("pid", r.pid)
      j.addProperty("startedAt", Diagnostics.iso(r.startedAtMs))
      j.addProperty("lastSeenAt", Diagnostics.iso(r.lastSeenAtMs))
      j.addProperty("version", r.version)
      j.addProperty("inMatch", r.inMatch)
      r.hsErr.foreach(p => j.addProperty("hsErr", s"crash/${p.getFileName}"))
      o.add("previousRun", j)
    }

    val list = new JsonArray
    files.foreach { case (name, p) =>
      val j = new JsonObject
      j.addProperty("path", name)
      j.addProperty("bytes", try Files.size(p) catch { case _: Exception => -1L })
      list.add(j)
    }
    o.add("files", list)
    o
  }

  private def str(o: JsonObject, key: String): Option[String] =
    if (o != null && o.has(key) && o.get(key).isJsonPrimitive) Some(o.get(key).getAsString).filter(_.nonEmpty) else None

  private def list(dir: Path): Seq[Path] =
    if (!Files.isDirectory(dir)) Nil
    else {
      val s = Files.list(dir)
      try s.iterator().asScala.toVector.sortBy(_.getFileName.toString) finally s.close()
    }

  /** A file into the zip, as much of it as there is now (this run's log is still being written). */
  private def copy(file: Path, out: java.io.OutputStream): Unit = {
    val in: InputStream = Files.newInputStream(file)
    try in.transferTo(out) finally in.close()
  }
}

/** When the player says it happened, picked on the report's timeline: when (the recording's
  * microseconds), what they picked it from ("mark", "noticed", "incident", or "timeline" for a place
  * on the timeline itself), and what it said there. */
final case class ChosenMoment(atMicros: Long, source: String, text: String)
