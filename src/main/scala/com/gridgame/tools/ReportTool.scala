package com.gridgame.tools

import com.google.gson.JsonObject

import java.io.{BufferedWriter, OutputStreamWriter, Writer}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import java.util.UUID
import scala.jdk.CollectionConverters._

/**
 * Dev tool: a player's problem report, read for the developer who has to fix what it is about
 * (design/diagnostics.md). It takes the server's folder for a report (`problem_reports/<day>/<code>/`),
 * or the report's zip on its own, and writes beside it:
 *
 *  - summary.txt     who sent it and about what, what they said (and when they picked it happened), their
 *                    machine, the match, the moments they marked, what went wrong, what the client
 *                    noticed for itself, the pictures before marks and incidents, the server's note,
 *                    what the recording holds
 *  - timeline.txt    every record of the recording in time order, a line each (`--around`/`--mark`:
 *                    only that stretch), timeline-mark-N.txt ten seconds either side of each mark, and
 *                    timeline-chosen.txt of the moment the player picked
 *  - frames.csv, state.csv, packets.csv, noticed.csv    the recording as tables
 *  - net.txt         packets a second, heartbeat round trips, the server's silences, frame times
 *  - mark-N-frames.png, incident-I-frames.png    each mark's and incident's pictures, tiled
 *  - the screenshots, logs and crash files, and the server's journal of the match
 *
 * Run:
 *   bazel run //src/main/scala/com/gridgame/tools:report -- <report folder or zip> [--out=<dir>]
 *       [--around=m:ss[,seconds]] [--mark=N[,seconds]]
 */
object ReportTool {
  /** Seconds either side of a mark (or of `--around`'s moment) a window shows unless told otherwise. */
  val DefaultHalfSeconds = 10.0

  sealed trait Focus
  final case class Around(centreMicros: Long, halfSeconds: Double) extends Focus
  final case class AtMark(number: Int, halfSeconds: Double) extends Focus

  final class ReportToolException(message: String) extends RuntimeException(message)

  /** What a run wrote: where, the summary it printed, and the files. */
  final case class Result(out: Path, summary: String, written: Seq[String])

  def main(args: Array[String]): Unit = {
    // The contact sheets are drawn with Java2D, which needs no display
    System.setProperty("java.awt.headless", "true")
    try {
      val (input, out, focus) = parseArgs(args, workingDirectory)
      val result = run(input, out, focus)
      print(result.summary)
      println(s"\nRead into ${result.out}")
    } catch {
      case e: ReportToolException if e.getMessage.isEmpty => println(Usage) // --help
      case e: ReportToolException =>
        System.err.println(e.getMessage)
        System.err.println(Usage)
        sys.exit(1)
      case e: IllegalArgumentException =>
        System.err.println(e.getMessage)
        sys.exit(1)
    }
  }

  val Usage: String =
    """Usage: bazel run //src/main/scala/com/gridgame/tools:report -- <report folder or zip> [options]
      |  --out=<dir>                where to write (default: <input>-read beside the input)
      |  --around=m:ss[,seconds]    timeline.txt of only that moment, 10 seconds either side unless told
      |  --mark=N[,seconds]         timeline.txt of only the player's mark N, the same way""".stripMargin

  /** Under `bazel run` the working directory is the runfiles': a path given is the caller's. */
  private def workingDirectory: Path =
    Option(System.getenv("BUILD_WORKING_DIRECTORY")).filter(_.nonEmpty).map(Paths.get(_)).getOrElse(Paths.get("").toAbsolutePath)

  def parseArgs(args: Array[String], cwd: Path): (Path, Path, Option[Focus]) = {
    var input: Path = null
    var out: Path = null
    var focus: Option[Focus] = None
    args.foreach {
      case a if a.startsWith("--out=") => out = cwd.resolve(a.drop("--out=".length))
      case a if a.startsWith("--around=") =>
        val (at, half) = withSeconds(a.drop("--around=".length))
        focus = Some(Around(parseMoment(at), half))
      case a if a.startsWith("--mark=") =>
        val (n, half) = withSeconds(a.drop("--mark=".length))
        focus = Some(AtMark(n.toIntOption.getOrElse(throw new ReportToolException(s"--mark=$n: a mark's number, as 1")), half))
      case "--help" | "-h" => throw new ReportToolException("")
      case a if a.startsWith("--") => throw new ReportToolException(s"No such option: $a")
      case a =>
        if (input != null) throw new ReportToolException(s"One report at a time: $input and $a")
        input = cwd.resolve(a)
    }
    if (input == null) throw new ReportToolException("Which report? Give its folder or its zip.")
    (input, if (out != null) out else defaultOut(input), focus)
  }

  /** "<input>-read" beside it, a zip's name without its ".zip". */
  def defaultOut(input: Path): Path = {
    val abs = input.toAbsolutePath.normalize()
    val name = Option(abs.getFileName).map(_.toString).getOrElse("report")
    val base = if (name.toLowerCase(java.util.Locale.ROOT).endsWith(".zip")) name.dropRight(4) else name
    Option(abs.getParent).getOrElse(abs).resolve(s"$base-read")
  }

  private def withSeconds(s: String): (String, Double) = s.split(',') match {
    case Array(a) => (a, DefaultHalfSeconds)
    case Array(a, b) => (a, b.toDoubleOption.filter(_ > 0).getOrElse(throw new ReportToolException(s"$b: seconds, as 10")))
    case _ => throw new ReportToolException(s"$s: a moment and, after a comma, how many seconds either side")
  }

  /** "2:31", "2:31.500", "1:02:31" or "151": a moment of the match, in microseconds. */
  def parseMoment(s: String): Long = {
    val parts = s.trim.split(':')
    val numbers = parts.map(p => p.toDoubleOption.filter(_ >= 0).getOrElse(throw new ReportToolException(s"$s: a moment, as 2:31")))
    if (numbers.isEmpty || numbers.length > 3) throw new ReportToolException(s"$s: a moment, as 2:31")
    (numbers.foldLeft(0.0)((acc, n) => acc * 60 + n) * 1e6).toLong
  }

  /** Read the report at `input` into `out` (made if it isn't there); `focus` narrows timeline.txt. */
  def run(input: Path, out: Path, focus: Option[Focus]): Result = {
    val files = ReportFiles.load(input)
    val report: Either[String, JsonObject] =
      files.text(ReportFiles.ReportJson).map(ReportSummary.parseJson).getOrElse(Left("the report has no report.json"))
    val serverNote = files.serverNote.map(ReportSummary.parseJson)
    val recording = ReportRecording.read(files)
    // A mark's screenshot by its name in the zip ("mark-1.jpg" is screenshots/mark-1.jpg there)
    val marks = ReportSummary.marks(report.toOption, recording).map { m =>
      val inZip = files.entries.map(_._1).find(n => m.screenshot.nonEmpty && (n == m.screenshot || n.endsWith("/" + m.screenshot)))
      m.copy(screenshot = inZip.getOrElse(m.screenshot))
    }
    val names = seedNames(report.toOption)
    val screenshots = marks.filter(_.screenshot.nonEmpty).map(m => m.number -> m.screenshot).toMap
    val noticed = ReportSummary.noticed(report.toOption, recording)
    val chosen = ReportSummary.chosen(report.toOption)
    val frameSets = ReportFrames.find(report.toOption, files.entries.map(_._1), marks, ReportSummary.incidentTimes(report.toOption))

    Files.createDirectories(out)
    val written = Vector.newBuilder[String]
    def writeText(name: String)(body: Writer => Unit): Unit = {
      val w = new BufferedWriter(new OutputStreamWriter(Files.newOutputStream(out.resolve(name)), StandardCharsets.UTF_8), 1 << 16)
      try body(w) finally w.close()
      written += name
    }

    val timeline = new ReportTimeline(recording, names, screenshots, chosen, frameSets)
    val window = focus.map {
      case Around(centre, half) => Window.around(centre, half, s"around ${ReportPackets.time(centre)}")
      case AtMark(n, half) =>
        val mark = marks.find(_.number == n).getOrElse(throw new ReportToolException(
          s"No mark $n in this report" + (if (marks.isEmpty) ": it has none" else s": it has ${marks.map(_.number).mkString(", ")}")))
        val at = mark.atMicros.getOrElse(throw new ReportToolException(s"Mark $n isn't in the recording, so its moment isn't known"))
        Window.around(at, half, s"mark $n")
    }
    if (!recording.isEmpty || recording.segments.nonEmpty) {
      writeText("timeline.txt")(w => timeline.write(w, window))
      marks.foreach(m => m.atMicros.foreach { at =>
        writeText(s"timeline-mark-${m.number}.txt")(w => timeline.write(w, Some(Window.around(at, DefaultHalfSeconds, s"mark ${m.number}"))))
      })
      chosen.foreach(c =>
        writeText("timeline-chosen.txt")(w => timeline.write(w, Some(Window.around(c.atMicros, DefaultHalfSeconds, "the moment the player picked")))))
      writeText("frames.csv")(w => ReportTables.writeFrames(recording, w))
      writeText("state.csv")(w => ReportTables.writeStates(recording, w))
      writeText("packets.csv")(w => ReportTables.writePackets(recording, names, w))
      writeText("net.txt")(w => new ReportNet(recording).write(w))
    }
    if (noticed.nonEmpty) writeText("noticed.csv")(w => ReportTables.writeNoticed(noticed, w))

    // Each mark's and incident's pictures, tiled in time order (the pictures themselves are copied below)
    val framesRead = frameSets.map { set =>
      ReportFrames.contactSheet(set, files.entry) match {
        case Right(sheet) =>
          Files.write(out.resolve(set.sheet), sheet.png)
          written += set.sheet
          FramesRead(set, sheet.tiles, sheet.skipped)
        case Left(skipped) => FramesRead(set, 0, skipped)
      }
    }

    // What the report carried besides its recording, copied out, and what the server kept beside it
    files.entries.foreach { case (name, bytes) =>
      if (!ReportRecording.isSegment(name)) ReportFiles.writeInside(out, name, bytes) match {
        case Some(p) => written += out.relativize(p).iterator().asScala.mkString("/")
        case None => // a name that would climb out of the folder: not written anywhere
      }
    }
    files.serverNote.foreach(n => { Files.write(out.resolve(ReportFiles.ServerNoteName), n.getBytes(StandardCharsets.UTF_8)); written += ReportFiles.ServerNoteName })
    files.journal.foreach(j => { Files.write(out.resolve(ReportFiles.JournalName), j.getBytes(StandardCharsets.UTF_8)); written += ReportFiles.JournalName })

    val summary = ReportSummary.text(files, report, serverNote, recording, marks,
      files.journal.map(_.linesIterator.size), ("summary.txt" +: written.result()).distinct, noticed, chosen, framesRead)
    Files.write(out.resolve("summary.txt"), summary.getBytes(StandardCharsets.UTF_8))
    Result(out, summary, ("summary.txt" +: written.result()).distinct)
  }

  /** Every player report.json knows of, by name, and which of them sent it. */
  private def seedNames(report: Option[JsonObject]): PlayerNames = {
    def uuid(s: String): UUID = try UUID.fromString(s) catch { case _: IllegalArgumentException => null }
    val local = report.flatMap(ReportSummary.obj(_, "connection")).flatMap(ReportSummary.str(_, "playerId")).map(uuid).orNull
    val names = new PlayerNames(local)
    for {
      r <- report
      m <- ReportSummary.obj(r, "match")
      players <- ReportSummary.arr(m, "players")
      p <- players.asScala if p.isJsonObject
      id <- ReportSummary.str(p.getAsJsonObject, "id")
      name <- ReportSummary.str(p.getAsJsonObject, "name")
    } names.learn(uuid(id), name)
    for (r <- report; c <- ReportSummary.obj(r, "connection"); name <- ReportSummary.str(c, "player")) names.learn(local, name)
    names
  }
}
