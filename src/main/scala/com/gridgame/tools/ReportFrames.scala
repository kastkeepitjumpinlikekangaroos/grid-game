package com.gridgame.tools

import com.google.gson.JsonObject

import java.awt.{Color, Font, RenderingHints}
import java.awt.image.BufferedImage
import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import javax.imageio.ImageIO
import scala.jdk.CollectionConverters._

/** A picture of the seconds before a mark or an incident: where it is in the zip, and when it was drawn. */
final case class FramePic(path: String, atMicros: Long)

/**
 * The pictures kept before one mark or one incident (a few a second for the last fifteen or so,
 * Recording.ReportFiles.markFrame / incidentFrame), in time order.
 *
 * @param label     "mark 1", "incident 2"
 * @param sheet     the contact sheet's file name: mark-1-frames.png
 * @param atMicros  the mark's or the incident's own moment, when known: each picture is said to be so
 *                  long before it
 */
final case class FrameSet(label: String, sheet: String, atMicros: Option[Long], frames: Vector[FramePic]) {
  def firstMicros: Long = frames.headOption.map(_.atMicros).getOrElse(0L)
  def lastMicros: Long = frames.lastOption.map(_.atMicros).getOrElse(0L)
}

/** A contact sheet drawn: its PNG, and the pictures that couldn't be read (missing, or not a JPEG). */
final case class ContactSheet(png: Array[Byte], width: Int, height: Int, tiles: Int, skipped: Vector[String])

/**
 * The pictures before marks and incidents: found from report.json's lists (`marks[].frames`,
 * `incidents[].frames`, each `{path, atMicros}`) and, for any it doesn't list, from their names in the
 * zip (`screenshots/mark-N/XXXXXXXX.jpg`, the eight digits the milliseconds since the recording began);
 * and each set drawn as a contact sheet, its pictures tiled in time order, each labelled with when it
 * was drawn and how long before the mark (or the incident) that was.
 */
object ReportFrames {
  private val MarkFrame = """screenshots/mark-(\d+)/(\d+)\.jpg""".r
  private val IncidentFrame = """screenshots/incident-(\d+)/(\d+)\.jpg""".r

  /** A contact sheet's tiles: this wide, in at most this many columns, a strip under each for its label. */
  val ThumbWidth = 240
  val MaxColumns = 6
  val LabelHeight = 18
  val HeaderHeight = 28
  val Gap = 6

  /**
   * Every mark's and incident's pictures. `marks` (their moments, from the recording or report.json) and
   * `incidentTimes` (an incident's index to its moment) say what each set is before.
   */
  def find(report: Option[JsonObject], entries: Seq[String], marks: Vector[MarkInfo], incidentTimes: Map[Int, Long]): Vector[FrameSet] = {
    def listed(o: JsonObject): Vector[FramePic] =
      ReportSummary.arr(o, "frames").toVector.flatMap(_.asScala).filter(_.isJsonObject).map(_.getAsJsonObject).flatMap { f =>
        ReportSummary.str(f, "path").map(p => FramePic(ReportFiles.normalise(p), ReportSummary.long(f, "atMicros").getOrElse(fromName(p))))
      }
    def jsonList(key: String): Vector[JsonObject] =
      report.flatMap(ReportSummary.arr(_, key)).toVector.flatMap(_.asScala).filter(_.isJsonObject).map(_.getAsJsonObject)

    val inZip = entries.map(ReportFiles.normalise)
    val markSets = {
      val fromJson = jsonList("marks").flatMap { m =>
        ReportSummary.long(m, "number").map(n => n.toInt -> listed(m))
      }.toMap
      val fromNames = inZip.collect { case p @ MarkFrame(n, ms) => n.toInt -> FramePic(p, ms.toLong * 1000L) }.groupMap(_._1)(_._2)
      (fromJson.keySet ++ fromNames.keySet).toVector.sorted.map { n =>
        val frames = merge(fromJson.getOrElse(n, Vector.empty), fromNames.getOrElse(n, Seq.empty))
        FrameSet(s"mark $n", s"mark-$n-frames.png", marks.find(_.number == n).flatMap(_.atMicros), frames)
      }
    }
    val incidentSets = {
      val fromJson = jsonList("incidents").zipWithIndex.flatMap { case (i, position) =>
        val index = ReportSummary.long(i, "index").map(_.toInt).getOrElse(position)
        val frames = listed(i)
        if (frames.isEmpty) None else Some(index -> (frames, ReportSummary.long(i, "atMicros")))
      }.toMap
      val fromNames = inZip.collect { case p @ IncidentFrame(n, ms) => n.toInt -> FramePic(p, ms.toLong * 1000L) }.groupMap(_._1)(_._2)
      (fromJson.keySet ++ fromNames.keySet).toVector.sorted.map { n =>
        val frames = merge(fromJson.get(n).map(_._1).getOrElse(Vector.empty), fromNames.getOrElse(n, Seq.empty))
        FrameSet(s"incident $n", s"incident-$n-frames.png", fromJson.get(n).flatMap(_._2).orElse(incidentTimes.get(n)), frames)
      }
    }
    (markSets ++ incidentSets).filter(_.frames.nonEmpty)
  }

  /** report.json's list and what the zip holds that it doesn't list, once each, in time order. */
  private def merge(listed: Vector[FramePic], named: Seq[FramePic]): Vector[FramePic] = {
    val paths = listed.map(_.path).toSet
    (listed ++ named.filterNot(f => paths(f.path))).sortBy(f => (f.atMicros, f.path))
  }

  /** The moment a picture's name says it was drawn ("00151250.jpg": 151.25s in), or 0. */
  private def fromName(path: String): Long =
    """(\d+)\.jpg$""".r.findFirstMatchIn(path).flatMap(_.group(1).toLongOption).map(_ * 1000L).getOrElse(0L)

  /** How big a sheet of `count` tiles of `thumbH` pictures is. */
  def sheetSize(count: Int, thumbH: Int): (Int, Int) = {
    val columns = Math.max(1, Math.min(MaxColumns, count))
    val rows = Math.max(1, (count + columns - 1) / columns)
    (Gap + columns * (ThumbWidth + Gap), HeaderHeight + rows * (thumbH + LabelHeight + Gap) + Gap)
  }

  /** "-2.750s": how long before the mark (or incident) a picture was drawn. */
  private def before(at: Long, moment: Option[Long]): String =
    moment.map(m => s"-${ReportPackets.num((m - at) / 1e6, 3)}s").getOrElse("")

  /**
   * The set's contact sheet: every picture it can read, tiled in time order, labelled. None when not one of
   * them can be read (then every one of them is a skip the summary reports).
   */
  def contactSheet(set: FrameSet, entry: String => Option[Array[Byte]]): Either[Vector[String], ContactSheet] = {
    System.setProperty("java.awt.headless", "true")
    val skipped = Vector.newBuilder[String]
    val pictures = set.frames.flatMap { f =>
      val image = entry(f.path).flatMap(b => try Option(ImageIO.read(new ByteArrayInputStream(b))) catch { case _: Exception => None })
      if (image.isEmpty) skipped += f.path
      image.map(f -> _)
    }
    if (pictures.isEmpty) return Left(skipped.result())
    val first = pictures.head._2
    val thumbH = Math.max(1, Math.round(ThumbWidth.toDouble * first.getHeight / Math.max(1, first.getWidth)).toInt)
    val (width, height) = sheetSize(pictures.size, thumbH)
    val columns = Math.max(1, Math.min(MaxColumns, pictures.size))
    val sheet = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    val g = sheet.createGraphics()
    try {
      g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
      g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
      g.setColor(new Color(0x10, 0x16, 0x26))
      g.fillRect(0, 0, width, height)
      g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13))
      g.setColor(new Color(0xFF, 0xD4, 0x66))
      val heading = s"${set.label}" + set.atMicros.map(m => s" at ${ReportPackets.time(m)}").getOrElse("") +
        s": ${pictures.size} pictures from ${ReportPackets.time(pictures.head._1.atMicros)} to ${ReportPackets.time(pictures.last._1.atMicros)}"
      g.drawString(heading, Gap + 2, HeaderHeight - 10)
      g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12))
      pictures.zipWithIndex.foreach { case ((f, image), i) =>
        val x = Gap + (i % columns) * (ThumbWidth + Gap)
        val y = HeaderHeight + (i / columns) * (thumbH + LabelHeight + Gap)
        // Each kept to its own shape inside its tile
        val scale = Math.min(ThumbWidth.toDouble / image.getWidth, thumbH.toDouble / image.getHeight)
        val w = Math.max(1, (image.getWidth * scale).toInt)
        val h = Math.max(1, (image.getHeight * scale).toInt)
        g.drawImage(image, x + (ThumbWidth - w) / 2, y + (thumbH - h) / 2, w, h, null)
        g.setColor(new Color(0xC2, 0xCA, 0xE4))
        g.drawString(s"${ReportPackets.time(f.atMicros)}  ${before(f.atMicros, set.atMicros)}".trim, x + 2, y + thumbH + LabelHeight - 5)
      }
    } finally g.dispose()
    val out = new ByteArrayOutputStream()
    ImageIO.write(sheet, "png", out)
    Right(ContactSheet(out.toByteArray, width, height, pictures.size, skipped.result()))
  }
}
