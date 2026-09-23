package com.gridgame.client.gl

import org.lwjgl.opengl.GL15.{GL_QUERY_RESULT, GL_QUERY_RESULT_AVAILABLE, GL_SAMPLES_PASSED, glBeginQuery, glEndQuery, glGenQueries, glGetQueryObjecti}
import org.lwjgl.opengl.GL33.{GL_TIME_ELAPSED, glGetQueryObjecti64}

/**
 * Dev tool: where a frame's GPU work goes. Off unless `GRIDGAME_GPU_PROFILE` is set, and then
 * [[GLGameRenderer]] ends its batches at every phase boundary and each phase is measured twice:
 *
 *  - fragments: how many pixels it shaded, in any target (GL_SAMPLES_PASSED). This is the
 *    renderer's real cost on the weak GPUs the game is meant to run on, which are fill-rate
 *    bound, and it is the same number on every machine, so it is the one to compare changes by.
 *  - time (GL_TIME_ELAPSED). Meaningful on a desktop GPU; a tile-based one (every Apple GPU)
 *    runs a whole render pass at once, so time inside a pass lands on whichever phase happened
 *    to close it, and can even come out negative.
 *
 * Results are read back [[Frames]] frames later, so profiling never waits on the GPU; the render
 * bench prints the averages:
 *
 *   GRIDGAME_GPU_PROFILE=1 bazel run //src/main/scala/com/gridgame/client:render_bench
 */
object GpuProfiler {
  val enabled: Boolean = System.getenv("GRIDGAME_GPU_PROFILE") != null

  private val Frames = 4
  private val MaxMarks = 32
  private var timeQ: Array[Int] = _
  private var fragQ: Array[Int] = _
  private val labels = new Array[String](MaxMarks)
  private val marksIn = new Array[Int](Frames)
  private val totalNs = new Array[Long](MaxMarks)
  private val totalFrags = new Array[Long](MaxMarks)
  private val samples = new Array[Int](MaxMarks)
  private var frame = 0
  private var mark = 0

  /** Start a frame: collect the one [[Frames]] ago, whose queries have long since finished. */
  def beginFrame(): Unit = {
    if (!enabled) return
    if (timeQ == null) {
      timeQ = new Array[Int](Frames * MaxMarks)
      fragQ = new Array[Int](Frames * MaxMarks)
      var i = 0
      while (i < timeQ.length) { timeQ(i) = glGenQueries(); fragQ(i) = glGenQueries(); i += 1 }
    }
    val slot = frame % Frames
    val n = marksIn(slot)
    if (frame >= Frames && n > 0 && glGetQueryObjecti(fragQ(slot * MaxMarks + n - 1), GL_QUERY_RESULT_AVAILABLE) != 0) {
      var i = 0
      while (i < n) {
        totalNs(i) += glGetQueryObjecti64(timeQ(slot * MaxMarks + i), GL_QUERY_RESULT)
        totalFrags(i) += glGetQueryObjecti64(fragQ(slot * MaxMarks + i), GL_QUERY_RESULT)
        samples(i) += 1
        i += 1
      }
    }
    mark = 0
    open(slot)
  }

  private def open(slot: Int): Unit = {
    glBeginQuery(GL_TIME_ELAPSED, timeQ(slot * MaxMarks + mark))
    glBeginQuery(GL_SAMPLES_PASSED, fragQ(slot * MaxMarks + mark))
  }

  /** The GPU work submitted since the previous mark was `label`'s. */
  def timestamp(label: String): Unit = {
    if (!enabled || mark >= MaxMarks - 1) return
    labels(mark) = label
    glEndQuery(GL_TIME_ELAPSED)
    glEndQuery(GL_SAMPLES_PASSED)
    mark += 1
    open(frame % Frames)
  }

  def endFrame(): Unit = {
    if (!enabled) return
    glEndQuery(GL_TIME_ELAPSED)
    glEndQuery(GL_SAMPLES_PASSED)
    marksIn(frame % Frames) = mark // the tail after the last mark is left out
    frame += 1
  }

  def report(): String = {
    if (!enabled) return ""
    val sb = new StringBuilder("  GPU by phase (mean per frame):        Mfrag      ms")
    var frags = 0.0; var ms = 0.0
    var i = 0
    while (i < MaxMarks && labels(i) != null) {
      if (samples(i) > 0) {
        val f = totalFrags(i) / 1e6 / samples(i)
        val t = totalNs(i) / 1e6 / samples(i)
        frags += f; ms += t
        sb.append(f"\n    ${labels(i)}%-32s $f%7.2f  $t%6.2f")
      }
      i += 1
    }
    sb.append(f"\n    ${"total"}%-32s $frags%7.2f  $ms%6.2f")
    sb.toString
  }
}
