package com.gridgame.client.ui

import javafx.animation.{AnimationTimer, KeyFrame, Timeline}
import javafx.application.{Application, Platform}
import javafx.scene.Scene
import javafx.scene.control.{Label, ScrollPane}
import javafx.scene.layout.{StackPane, VBox}
import javafx.scene.paint.Color
import javafx.scene.shape.Rectangle
import javafx.stage.{Screen, Stage}
import javafx.util.Duration

import java.lang.management.ManagementFactory

/**
 * Dev tool: what the menus cost while the player is sitting in them. Not shipped — run with
 *
 *   bazel run //src/main/scala/com/gridgame/client:ui_bench
 *
 * Opens the character select screen at the client's window size and browses a few
 * characters, does to the JavaFX side what a match start does (stop the screen, swap the
 * scene out, hide the stage) and then what a match end does (a fresh screen), and finally
 * lets that screen go idle. For each phase it prints the live heap after a GC, the
 * committed heap, CPU used, and — on macOS — the process footprint as Activity Monitor
 * reports it, split into GPU memory, window surfaces, heap and malloc.
 *
 *   --keep-scene     hide the stage with its scene still attached, as before showGameScene swapped it out
 *   --small          a 1000x700 window instead of the screen's size
 *   --snapshot=PATH  save the second character select screen as a PNG
 *   --empty-anim     no menus, just a 10px square moving on an empty window: the minimal
 *                    repro of what one repaint costs (--every=N moves it every Nth pulse)
 */
object UiMemoryBench {
  def main(args: Array[String]): Unit = Application.launch(classOf[UiMemoryBenchApp], args: _*)
}

class UiMemoryBenchApp extends Application {
  private var selected: Byte = 0
  private var panel: CharacterSelectionPanel = _
  private var lastCpuNs = 0L
  private var lastWallNs = 0L

  private def lobbyScene(): Scene = {
    panel = new CharacterSelectionPanel(() => selected, id => selected = id)
    val root = new VBox(panel.createPanel())
    root.setStyle("-fx-background-color: #1a1a2e;")
    val scroll = new ScrollPane(root)
    scroll.setFitToWidth(true)
    scroll.setStyle("-fx-background-color: #1a1a2e; -fx-border-color: transparent;")
    ViewportCache.disable(scroll) // as the lobby screens do
    new Scene(scroll)
  }

  private def emptyAnimScene(every: Int): Scene = {
    val dot = new Rectangle(10, 10, Color.WHITE)
    val timer = new AnimationTimer {
      private var n = 0
      override def handle(now: Long): Unit = {
        n += 1
        if (n % every == 0) dot.setTranslateX((n / every % 100).toDouble)
      }
    }
    timer.start()
    new Scene(new StackPane(new Label("empty"), dot), Color.web("#1a1a2e"))
  }

  private def saveSnapshot(scene: Scene, path: String): Unit = {
    val img = scene.snapshot(null)
    val w = img.getWidth.toInt; val h = img.getHeight.toInt
    val out = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB)
    val reader = img.getPixelReader
    var y = 0
    while (y < h) { var x = 0; while (x < w) { out.setRGB(x, y, reader.getArgb(x, y)); x += 1 }; y += 1 }
    javax.imageio.ImageIO.write(out, "png", new java.io.File(path))
  }

  private def processCpuNs: Long = ManagementFactory.getOperatingSystemMXBean match {
    case os: com.sun.management.OperatingSystemMXBean => os.getProcessCpuTime
    case _ => 0L
  }

  private def footprint(): String = {
    if (!System.getProperty("os.name", "").toLowerCase.contains("mac")) return ""
    try {
      val pid = ProcessHandle.current().pid()
      val p = new ProcessBuilder("footprint", pid.toString).redirectErrorStream(true).start()
      val out = new String(p.getInputStream.readAllBytes())
      p.waitFor()
      def line(tag: String): String =
        out.linesIterator.find(_.contains(tag)).map(_.trim.split("\\s+").take(2).mkString(" ")).getOrElse("?")
      val total = out.linesIterator.find(_.contains("phys_footprint:")).map(_.split(":")(1).trim).getOrElse("?")
      s"footprint $total (graphics ${line("IOAccelerator (graphics)")}, " +
        s"VM_ALLOCATE ${line("untagged (VM_ALLOCATE)")}, IOSurface ${line("IOSurface")}, " +
        s"malloc ${line("MALLOC_SMALL")})"
    } catch { case e: Exception => s"footprint unavailable: ${e.getMessage}" }
  }

  private def startMeasuring(): Unit = { lastCpuNs = processCpuNs; lastWallNs = System.nanoTime() }

  private def report(phase: String): Unit = {
    val now = System.nanoTime()
    val cpuPct = (processCpuNs - lastCpuNs) * 100.0 / (now - lastWallNs)
    System.gc(); System.gc()
    val heap = ManagementFactory.getMemoryMXBean.getHeapMemoryUsage
    println(f"[$phase%-22s] live heap ${heap.getUsed / 1048576.0}%6.1f MB, committed ${heap.getCommitted / 1048576.0}%6.1f MB, " +
      f"cpu $cpuPct%5.1f%% of a core | ${footprint()}")
    startMeasuring()
  }

  override def start(stage: Stage): Unit = {
    val raw = getParameters.getRaw
    def flag(name: String): Option[String] =
      raw.toArray.map(_.toString).find(_.startsWith(name + "=")).map(_.drop(name.length + 1))
    val bounds = Screen.getPrimary.getVisualBounds
    stage.setX(bounds.getMinX); stage.setY(bounds.getMinY)
    if (raw.contains("--small")) { stage.setWidth(1000); stage.setHeight(700) }
    else { stage.setWidth(bounds.getWidth); stage.setHeight(bounds.getHeight) }

    if (raw.contains("--empty-anim")) {
      stage.setScene(emptyAnimScene(flag("--every").map(_.toInt).getOrElse(1)))
      stage.show()
      startMeasuring()
      new Timeline(new KeyFrame(Duration.seconds(8), _ => { report("empty window, animating"); Platform.exit(); System.exit(0) })).play()
      return
    }

    stage.setScene(lobbyScene())
    stage.show()
    startMeasuring()

    // Browse: the detail preview follows the selection, as a click would
    val browse = new Timeline(new KeyFrame(Duration.millis(400), _ => selected = ((selected + 9) % 112).toByte))
    browse.setCycleCount(15)

    val steps = Seq[(Double, () => Unit)](
      (1.0, () => browse.play()),
      (8.0, () => report("character select")),
      (8.1, () => {
        panel.stop()
        // What showGameScene does to the JavaFX side when the GLFW window takes over
        if (!raw.contains("--keep-scene")) {
          stage.setScene(new Scene(new StackPane(), Color.BLACK))
          SpriteGenerator.clearCache()
        }
        Platform.setImplicitExit(false)
        stage.hide()
        startMeasuring()
      }),
      (16.0, () => report("stage hidden (match)")),
      (16.1, () => { stage.setScene(lobbyScene()); stage.show() }),
      (20.0, () => startMeasuring()), // past the sheets loading
      (29.9, () => flag("--snapshot").foreach(path => saveSnapshot(stage.getScene, path))),
      (30.0, () => report("character select again")),
      (30.1, () => UiActivity.markIdle()),
      (32.0, () => startMeasuring()),
      (40.0, () => report("same screen, idle")),
      (40.5, () => { Platform.exit(); System.exit(0) })
    )
    steps.foreach { case (t, f) => new Timeline(new KeyFrame(Duration.seconds(t), _ => f())).play() }
  }
}
