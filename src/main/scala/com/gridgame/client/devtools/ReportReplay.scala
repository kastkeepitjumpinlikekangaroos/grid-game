package com.gridgame.client.devtools

import com.gridgame.client.gl.{GLFWManager, GLWindow, RenderQuality}
import com.gridgame.client.render.{FrameClock, GLGameRenderer, IsometricTransform}
import com.gridgame.common.Constants
import com.gridgame.common.diagnostics.Recording.{FrameFlags, FrameRecord, float, ushort}
import com.gridgame.common.model.CharacterDef

import org.lwjgl.glfw.GLFW._

import java.awt.{Color, Font, RenderingHints}
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.{Path, Paths}
import javax.imageio.ImageIO

/**
 * Dev tool: a player's match redrawn from their problem report's recording, through the real GameClient
 * and GLGameRenderer, to watch any moment of it — one nobody marked among them. Not shipped — run with
 *
 *   bazel run //src/main/scala/com/gridgame/client:report_replay -- <report folder or zip> [options]
 *
 * In its window: Space pause and play, Left/Right five seconds back and on, comma/period a frame back
 * and on (paused), [ and ] half and twice the speed (0.25x to 4x), M the next mark, N the next moment
 * the client noticed or thing that went wrong, Home the start, S a picture of the frame beside the
 * report. The title says where it is.
 *
 *   --at=m:ss[.mmm] --out=frame.png         that moment to a PNG, and done (no window shown)
 *   --film=m:ss-m:ss --fps=N --out=dir      a run of PNGs over that stretch, and a contact sheet of them
 *   --size=WxH                              the window's size (default 1280x720)
 *   --quality=low|medium|high               the renderer's tier (default high)
 *   --sound                                 the game's sounds (it is silent otherwise)
 *
 * The client keeps time on the wall clock for some effects (a hold, a burn, the match clock), so they are
 * exact played at 1x and approximate after a seek or at another speed; a picture of a moment is taken
 * after the three seconds before it are played at 1x, so it is drawn as the player saw it.
 */
object ReportReplay {
  private val WarmMicros = 3000000L

  final case class Options(input: String = null, at: Option[Long] = None, film: Option[(Long, Long)] = None,
                           fps: Int = 10, out: Option[String] = None, w: Int = 1280, h: Int = 720, sound: Boolean = false)

  def main(args: Array[String]): Unit = {
    val rest = RenderQuality.configure(if (args.exists(_.startsWith("--quality="))) args else args :+ "--quality=high")
    val opts = parse(rest.toSeq)
    if (opts.input == null) {
      System.err.println("usage: report_replay <report folder or zip> [--at=m:ss[.mmm] --out=frame.png] " +
        "[--film=m:ss-m:ss --fps=N --out=dir] [--size=WxH] [--quality=low|medium|high] [--sound]")
      System.exit(1)
    }
    if (!opts.sound) silence()
    CharacterDef.all // registers every ProjectileDef

    val recording = ReplayRecording.load(resolve(opts.input))
    describe(recording)
    if (recording.records.isEmpty) { System.err.println("replay: there is no recording in it"); System.exit(1) }

    val window = new GLWindow(s"Replay ${recording.code}", opts.w, opts.h)
    window.create()
    val replay = new Replay(recording, window)
    try {
      (opts.at, opts.film) match {
        case (Some(t), _) =>
          val out = resolveOut(opts.out.getOrElse(s"replay-${stamp(t)}.png"), recording)
          replay.picture(t, out)
          println(s"replay: wrote ${out.getAbsolutePath}")
        case (None, Some((from, to))) =>
          val dir = resolveOut(opts.out.getOrElse(s"replay-${stamp(from)}-${stamp(to)}"), recording)
          replay.film(from, to, opts.fps, dir)
          println(s"replay: wrote ${dir.getAbsolutePath}")
        case _ =>
          replay.interactive()
      }
    } finally {
      replay.dispose()
      window.destroy()
      GLFWManager.terminate()
    }
    System.exit(0)
  }

  /** One recording on one window: its session, the renderer drawing it, and the ways to play it. */
  private final class Replay(recording: ReplayRecording, window: GLWindow) {
    private val session = new ReplaySession(recording)
    private var renderer = newRenderer()

    private def newRenderer(): GLGameRenderer = {
      val r = new GLGameRenderer(session.client, FrameClock.System)
      // The pointer is the recording's: the aim follows it, as the match's window has it follow the mouse
      r.onCamera = () => true
      r
    }

    /** Where the playback is, and moving it: back means a new client, so a new renderer for it. */
    private def seek(t: Long): Unit = {
      if (session.seek(Math.max(0L, Math.min(t, recording.durationMicros)))) {
        renderer.dispose()
        renderer = newRenderer()
      }
    }

    /** One frame, `dt` after the last, with the recording's pointer over it where the player had it. */
    private def draw(dtSec: Double): Unit = {
      renderer.render(Math.min(dtSec, 0.05), window.fbWidth, window.fbHeight, window.width, window.height)
      val f = session.lastFrame
      if (f != null && (ushort(f, FrameRecord.Flags) & FrameFlags.Focused) != 0 && (ushort(f, FrameRecord.Flags) & FrameFlags.StickAiming) == 0) {
        val cam = renderer.camera
        val zoom = Constants.CAMERA_ZOOM
        val ax = float(f, FrameRecord.AimX).toDouble
        val ay = float(f, FrameRecord.AimY).toDouble
        renderer.drawCursor(IsometricTransform.worldToScreenX(ax, ay, cam.camOffX) * zoom,
          IsometricTransform.worldToScreenY(ax, ay, cam.camOffY) * zoom, window.fbWidth, window.width, window.height)
      }
    }

    /** Play from where it is to `to` at 1x on the wall clock, a frame every 60th of a second, calling
      * `atEach` with the playback time before each frame is swapped (to take a picture). */
    private def playTo(to: Long, atEach: Long => Unit = _ => ()): Unit = {
      var last = System.nanoTime()
      var t = session.timeMicros
      while (t < to) {
        val frameStart = System.nanoTime()
        glfwPollEvents()
        val dt = Math.min(frameStart - last, 50000000L)
        last = frameStart
        t = Math.min(to, t + dt / 1000L)
        session.advanceTo(t)
        draw(dt / 1e9)
        atEach(t)
        window.swapBuffers()
        val spare = 16666667L - (System.nanoTime() - frameStart)
        if (spare > 0) Thread.sleep(spare / 1000000L, (spare % 1000000L).toInt)
      }
    }

    /** The moment at `t`, as the player saw it: the three seconds before it played at 1x first. */
    def picture(t: Long, out: File): Unit = {
      val at = Math.max(0L, Math.min(t, recording.durationMicros))
      seek(Math.max(0L, at - WarmMicros))
      playTo(at)
      glfwPollEvents()
      session.advanceTo(at)
      draw(1.0 / 60)
      Option(out.getParentFile).foreach(_.mkdirs())
      savePng(out, window.fbWidth, window.fbHeight)
      window.swapBuffers()
    }

    /** `fps` pictures a second from `from` to `to`, numbered, and a contact sheet of them. */
    def film(from: Long, to: Long, fps: Int, dir: File): Unit = {
      dir.mkdirs()
      val start = Math.max(0L, Math.min(from, recording.durationMicros))
      val end = Math.max(start, Math.min(to, recording.durationMicros))
      seek(Math.max(0L, start - WarmMicros))
      playTo(start)
      val every = 1000000L / Math.max(1, fps)
      var nextShot = start
      val shots = scala.collection.mutable.ArrayBuffer[(Long, File)]()
      def shoot(t: Long): Unit = if (t >= nextShot) {
        val f = new File(dir, f"frame-${shots.size}%04d.png")
        savePng(f, window.fbWidth, window.fbHeight)
        shots += t -> f
        nextShot += every
      }
      glfwPollEvents()
      session.advanceTo(start)
      draw(1.0 / 60)
      shoot(start)
      window.swapBuffers()
      playTo(end, shoot)
      contactSheet(shots.toSeq, new File(dir, "contact.png"))
    }

    /** The window, played and steered by the keys, until it is closed. */
    def interactive(): Unit = {
      var playing = true
      var speed = 1.0
      var t = 0L
      val actions = new java.util.concurrent.ConcurrentLinkedQueue[java.lang.Integer]()
      glfwSetKeyCallback(window.handle, (_, key, _, action, _) =>
        if (action == GLFW_PRESS || action == GLFW_REPEAT) actions.add(Integer.valueOf(key)))
      window.show()
      seek(0L)
      var last = System.nanoTime()
      var titledAt = 0L
      var saveNext = false
      while (!window.shouldClose) {
        val frameStart = System.nanoTime()
        glfwPollEvents()
        val dt = Math.min(frameStart - last, 50000000L)
        last = frameStart
        var key = actions.poll()
        while (key != null) {
          key.intValue match {
            case GLFW_KEY_SPACE => playing = !playing; if (playing && t >= recording.durationMicros) { t = 0L; seek(0L) }
            case GLFW_KEY_LEFT => t = Math.max(0L, t - 5000000L); seek(t)
            case GLFW_KEY_RIGHT => t = Math.min(recording.durationMicros, t + 5000000L); seek(t)
            case GLFW_KEY_COMMA => playing = false; t = Math.max(0L, t - 16667L); seek(t)
            case GLFW_KEY_PERIOD => playing = false; t = Math.min(recording.durationMicros, t + 16667L); seek(t)
            case GLFW_KEY_LEFT_BRACKET => speed = Math.max(0.25, speed / 2)
            case GLFW_KEY_RIGHT_BRACKET => speed = Math.min(4.0, speed * 2)
            case GLFW_KEY_HOME => t = 0L; seek(0L)
            case GLFW_KEY_M => recording.marks.find(_._1 > t + 1000L).foreach { case (at, what) => t = at; seek(t); println(s"replay: ${clock(at)} $what") }
            case GLFW_KEY_N => recording.moments.find(_._1 > t + 1000L).foreach { case (at, what) => t = at; seek(t); println(s"replay: ${clock(at)} $what") }
            case GLFW_KEY_S => saveNext = true
            case _ =>
          }
          key = actions.poll()
        }
        if (playing) {
          t = Math.min(recording.durationMicros, t + (dt / 1000L * speed).toLong)
          session.advanceTo(t)
          if (t >= recording.durationMicros) playing = false
        }
        draw(if (playing) dt / 1e9 * speed else 0.0)
        if (saveNext) {
          saveNext = false
          val f = new File(recording.folder.toFile, s"replay-${stamp(t)}.png")
          savePng(f, window.fbWidth, window.fbHeight)
          println(s"replay: wrote ${f.getAbsolutePath}")
        }
        window.swapBuffers()
        if (frameStart - titledAt > 200000000L) {
          titledAt = frameStart
          glfwSetWindowTitle(window.handle, s"Replay ${recording.code}  ${clock(t)} / ${clock(recording.durationMicros)}  " +
            s"${if (speed == speed.toInt) speed.toInt.toString else speed.toString}x${if (playing) "" else "  paused"}" +
            "    Space play  Left/Right 5s  , . frame  [ ] speed  M mark  N moment  Home  S save")
        }
        val spare = 16666667L - (System.nanoTime() - frameStart)
        if (spare > 0) Thread.sleep(spare / 1000000L, (spare % 1000000L).toInt)
      }
    }

    def dispose(): Unit = renderer.dispose()
  }

  // ── Options ────────────────────────────────────────────────────────────

  private def parse(args: Seq[String]): Options = args.foldLeft(Options()) { (o, a) =>
    if (a.startsWith("--at=")) o.copy(at = Some(time(a.drop(5))))
    else if (a.startsWith("--film=")) {
      val Array(from, to) = a.drop(7).split("-", 2)
      o.copy(film = Some(time(from) -> time(to)))
    }
    else if (a.startsWith("--fps=")) o.copy(fps = a.drop(6).toInt)
    else if (a.startsWith("--out=")) o.copy(out = Some(a.drop(6)))
    else if (a.startsWith("--size=")) {
      val Array(w, h) = a.drop(7).toLowerCase.split("x", 2)
      o.copy(w = w.toInt, h = h.toInt)
    }
    else if (a == "--sound") o.copy(sound = true)
    else if (a.startsWith("--")) { System.err.println(s"replay: $a is no option"); o }
    else o.copy(input = a)
  }

  /** "2:31", "2:31.250", "1:02:31" or plain seconds, to microseconds. */
  def time(s: String): Long = {
    val parts = s.trim.split(":")
    val seconds = parts.foldLeft(0.0)((acc, p) => acc * 60 + p.toDouble)
    (seconds * 1000000L).round
  }

  /** "2:31.250" */
  def clock(micros: Long): String = {
    val ms = Math.max(0L, micros / 1000L)
    f"${ms / 60000}%d:${ms / 1000 % 60}%02d.${ms % 1000}%03d"
  }

  private def stamp(micros: Long): String = clock(micros).replace(':', 'm').replace('.', 's')

  /** A path as given under `bazel run`: from where it was run. */
  private def resolve(p: String): Path = {
    val path = Paths.get(p)
    if (path.isAbsolute) path
    else Option(System.getenv("BUILD_WORKING_DIRECTORY")).map(Paths.get(_).resolve(path)).getOrElse(path)
  }

  /** Where output goes: as given (from where it was run), or a bare name beside the report. */
  private def resolveOut(p: String, recording: ReplayRecording): File = {
    val path = Paths.get(p)
    if (path.isAbsolute) path.toFile
    else if (path.getParent != null || System.getenv("BUILD_WORKING_DIRECTORY") == null) resolve(p).toFile
    else recording.folder.resolve(path).toFile
  }

  private def describe(r: ReplayRecording): Unit = {
    println(s"replay: ${r.code}, ${r.playerName}'s match, ${clock(r.durationMicros)} long, ${r.records.size} records " +
      s"in segment(s) ${r.segments.mkString(", ")}")
    r.gaps.foreach { case (a, b) => println(s"replay: nothing kept from ${clock(a)} to ${clock(b)} (the client keeps a long match's start and its newest part)") }
    r.marks.foreach { case (t, what) => println(s"replay:   ${clock(t)}  $what") }
    r.moments.foreach { case (t, what) => println(s"replay:   ${clock(t)}  $what") }
  }

  /** The game's sounds off for this run only, the way GRIDGAME_AUDIO=off has them, without saving
    * anything to the player's settings: a replay plays packets in bursts, and a seek would be a din. */
  private def silence(): Unit =
    try {
      // By name: the game's library brings it, and the dev tools don't otherwise depend on sound
      val cls = Class.forName("com.gridgame.client.audio.AudioManager$")
      val audio = cls.getField("MODULE$").get(null)
      val f = cls.getDeclaredField("initFailed")
      f.setAccessible(true)
      f.setBoolean(audio, true)
    } catch { case _: Throwable => System.err.println("replay: couldn't silence the game; run it with GRIDGAME_AUDIO=off") }

  // ── Pictures ───────────────────────────────────────────────────────────

  /** The default framebuffer, as it is about to be shown, to a PNG. */
  private def savePng(file: File, w: Int, h: Int): Unit = ImageIO.write(readBack(w, h), "png", file)

  private def readBack(w: Int, h: Int): BufferedImage = {
    val buf = org.lwjgl.BufferUtils.createByteBuffer(w * h * 4)
    org.lwjgl.opengl.GL30.glBindFramebuffer(org.lwjgl.opengl.GL30.GL_FRAMEBUFFER, 0)
    org.lwjgl.opengl.GL11.glReadPixels(0, 0, w, h, org.lwjgl.opengl.GL11.GL_RGBA, org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE, buf)
    val img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
    var y = 0
    while (y < h) {
      var x = 0
      while (x < w) {
        val o = ((h - 1 - y) * w + x) * 4
        img.setRGB(x, y, ((buf.get(o) & 0xFF) << 16) | ((buf.get(o + 1) & 0xFF) << 8) | (buf.get(o + 2) & 0xFF))
        x += 1
      }
      y += 1
    }
    img
  }

  /** The film's pictures side by side, small, each with its time under it. */
  private def contactSheet(shots: Seq[(Long, File)], out: File): Unit = {
    if (shots.isEmpty) return
    val first = ImageIO.read(shots.head._2)
    val tw = 320
    val th = Math.max(1, first.getHeight * tw / first.getWidth)
    val label = 22
    val cols = Math.min(6, shots.size)
    val rows = (shots.size + cols - 1) / cols
    val sheet = new BufferedImage(cols * tw, rows * (th + label), BufferedImage.TYPE_INT_RGB)
    val g = sheet.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    g.setColor(new Color(8, 12, 30))
    g.fillRect(0, 0, sheet.getWidth, sheet.getHeight)
    g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14))
    shots.zipWithIndex.foreach { case ((t, f), i) =>
      val x = i % cols * tw
      val y = i / cols * (th + label)
      g.drawImage(ImageIO.read(f), x, y, tw, th, null)
      g.setColor(new Color(238, 242, 255))
      g.drawString(clock(t), x + 6, y + th + 16)
    }
    g.dispose()
    ImageIO.write(sheet, "png", out)
  }
}
