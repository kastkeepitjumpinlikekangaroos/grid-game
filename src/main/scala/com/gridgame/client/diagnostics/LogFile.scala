package com.gridgame.client.diagnostics

import java.io.{ByteArrayOutputStream, FileOutputStream, IOException, OutputStream, PrintStream}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, StandardCopyOption}
import java.time.{Instant, ZoneId}
import java.time.format.DateTimeFormatter

/**
 * The client's log on disk: everything it prints, to standard output and to standard error, each line
 * stamped with the time and the thread that wrote it (design/diagnostics.md, *The log*). The packaged
 * apps have no console, so without it a stack trace went nowhere a player could find it.
 *
 * One file a run, `client.log`, the run before it `client.1.log` and the one before that `client.2.log`:
 * a run that crashed is still there when the next one starts. A run's file stops at `capBytes` (a line
 * says so, and how many it left out), so a loop printing an error every frame can't fill the disk.
 */
final class LogFile(dir: Path, name: String = "client", keep: Int = 3, capBytes: Long = 16L * 1024 * 1024) {
  /** This run's file. */
  val path: Path = dir.resolve(s"$name.log")

  /** The run before this one's file, once `open` has moved it aside, if there was one. */
  def previous: Option[Path] = Some(dir.resolve(s"$name.1.log")).filter(Files.isRegularFile(_))

  /** Each line as it is written, after the file has it: the match being recorded keeps it too. */
  @volatile var onLine: String => Unit = _ => ()

  private var out: OutputStream = _
  private var written = 0L
  private var leftOut = 0L
  private val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault())

  /** Move the earlier runs' files along and start this run's. */
  def open(): Unit = synchronized {
    rotate()
    out = new FileOutputStream(path.toFile)
    written = 0L
    leftOut = 0L
  }

  private def rotate(): Unit = {
    def file(n: Int): Path = if (n == 0) path else dir.resolve(s"$name.$n.log")
    try {
      Files.deleteIfExists(file(keep - 1))
      for (n <- (keep - 2) to 0 by -1)
        if (Files.exists(file(n))) Files.move(file(n), file(n + 1), StandardCopyOption.REPLACE_EXISTING)
    } catch { case _: IOException => } // an earlier run's file in the way costs only its history
  }

  /** A line, from `stream` ("out" or "err"), by the thread that is writing it. */
  def line(stream: String, text: String): Unit = {
    val stamped = s"${stamp.format(Instant.now())} [${Thread.currentThread().getName}]${if (stream == "err") " ERR" else ""} $text"
    synchronized {
      if (out != null) {
        val bytes = (stamped + "\n").getBytes(StandardCharsets.UTF_8)
        if (written + bytes.length <= capBytes) {
          try {
            out.write(bytes)
            out.flush() // a crash keeps every line written before it
            written += bytes.length
          } catch { case _: IOException => }
        } else {
          if (leftOut == 0L) {
            val note = s"${stamp.format(Instant.now())} [log] this run's log is full (${capBytes / (1024 * 1024)}MB); what follows is left out\n"
            try { out.write(note.getBytes(StandardCharsets.UTF_8)); out.flush() } catch { case _: IOException => }
          }
          leftOut += 1
        }
      }
    }
    try onLine(stamped) catch { case _: Throwable => }
  }

  /** How many lines were left out once the file was full. */
  def linesLeftOut: Long = synchronized(leftOut)

  /** Everything printed from now on goes to the file as well as where it went before. Scala's println
    * goes by Console.out, which takes System.out once, as it starts: this runs before it does. */
  def tee(): Unit = {
    System.setOut(new PrintStream(new Tee(System.out, "out"), true, StandardCharsets.UTF_8))
    System.setErr(new PrintStream(new Tee(System.err, "err"), true, StandardCharsets.UTF_8))
  }

  def close(): Unit = synchronized {
    if (out != null) try out.close() catch { case _: IOException => }
    out = null
  }

  /** A stream that writes on to `original` and hands each whole line to the file. A println holds its
    * PrintStream's lock for the whole line, so the lines of two threads printing at once never mix. */
  private final class Tee(original: PrintStream, stream: String) extends OutputStream {
    private val pending = new ByteArrayOutputStream(256)

    override def write(b: Int): Unit = synchronized {
      original.write(b)
      take(b.toByte)
    }

    override def write(b: Array[Byte], off: Int, len: Int): Unit = synchronized {
      original.write(b, off, len)
      var i = off
      while (i < off + len) { take(b(i)); i += 1 }
    }

    override def flush(): Unit = original.flush()

    private def take(b: Byte): Unit =
      if (b == '\n') {
        var text = pending.toString(StandardCharsets.UTF_8)
        if (text.endsWith("\r")) text = text.dropRight(1)
        pending.reset()
        line(stream, text)
      } else pending.write(b)
  }
}
