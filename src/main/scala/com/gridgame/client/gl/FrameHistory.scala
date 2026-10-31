package com.gridgame.client.gl

import org.lwjgl.opengl.GL11._
import org.lwjgl.opengl.GL15._
import org.lwjgl.opengl.GL21._
import org.lwjgl.opengl.GL30._
import org.lwjgl.stb.{STBIWriteCallback, STBImageWrite}
import org.lwjgl.system.MemoryUtil

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.{ArrayBlockingQueue, TimeUnit}

/**
 * The last few seconds of what a window showed, small: a frame every `everyMs` scaled down to
 * `width` pixels across, kept as JPEGs for `keepMs` (design/diagnostics.md, *The seconds before*).
 * A problem report takes them when the player marks a moment late, or something goes wrong they
 * would have seen.
 *
 * Nothing waits on the GPU and nothing is allocated on the thread that draws. `capture`, called once
 * the frame is drawn and before the swap, does nothing at all most frames; when one is due it blits the
 * window into a small framebuffer and starts reading that into a pixel buffer object without waiting
 * for it, and copies out the one it started a capture ago, long since done, into a buffer from a pool.
 * A thread of its own turns those into JPEGs. The renderer never leaves the scissor test on, which a
 * blit would be clipped by.
 */
final class FrameHistory(width: Int = FrameHistory.Width, everyMs: Long = FrameHistory.EveryMs,
                         keepMs: Long = FrameHistory.KeepMs, quality: Int = FrameHistory.Quality) {
  import FrameHistory._

  // The render thread's
  private var fbo = 0
  private var color = 0
  private var targetW = 0
  private var targetH = 0
  private val pbos = new Array[Int](2)
  private var nextPbo = 0
  private var pendingPbo = -1
  private var pendingW = 0
  private var pendingH = 0
  private var pendingNanos = 0L
  private var lastNanos = 0L
  private var capturedYet = false

  // Frames read back, waiting to be squeezed into JPEGs, and the buffers they come back in
  private final class Staging(val buf: ByteBuffer) {
    var w = 0
    var h = 0
    var nanos = 0L
  }
  private val maxBytes = width * width * 4 // a frame no taller than it is wide
  private val free = new ArrayBlockingQueue[Staging](Staged)
  private val jobs = new ArrayBlockingQueue[Staging](Staged)
  @volatile private var running = true
  private var encoder: Thread = _

  // The frames kept, oldest first
  private val kept = new java.util.ArrayDeque[Frame]()

  /** How many frames a capture skipped because the JPEG thread was behind. */
  @volatile var skipped = 0

  /**
   * After a frame is drawn, before the swap, with the window's framebuffer size: a capture if one is
   * due (the window's own default framebuffer is read, as the player sees it).
   */
  def capture(fbWidth: Int, fbHeight: Int, nowNanos: Long): Unit = {
    if (fbWidth <= 0 || fbHeight <= 0 || (capturedYet && nowNanos - lastNanos < everyMs * 1000000L)) return
    lastNanos = nowNanos
    capturedYet = true
    if (encoder == null) start()
    if (pendingPbo >= 0) collect()

    // Scaled to `width` across, as tall as the window is for that, and even
    val w = width
    val h = Math.max(2, Math.min(width, (width.toLong * fbHeight / fbWidth).toInt) & ~1)
    if (w != targetW || h != targetH) target(w, h)

    glBindFramebuffer(GL_READ_FRAMEBUFFER, 0)
    glBindFramebuffer(GL_DRAW_FRAMEBUFFER, fbo)
    glBlitFramebuffer(0, 0, fbWidth, fbHeight, 0, 0, w, h, GL_COLOR_BUFFER_BIT, GL_LINEAR)
    glBindFramebuffer(GL_READ_FRAMEBUFFER, fbo)
    glBindBuffer(GL_PIXEL_PACK_BUFFER, pbos(nextPbo))
    glReadPixels(0, 0, w, h, GL_RGBA, GL_UNSIGNED_BYTE, 0L) // into the buffer, not waited for
    glBindBuffer(GL_PIXEL_PACK_BUFFER, 0)
    glBindFramebuffer(GL_FRAMEBUFFER, 0)
    pendingPbo = nextPbo
    pendingW = w
    pendingH = h
    pendingNanos = nowNanos
    nextPbo = (nextPbo + 1) % pbos.length
  }

  /** The read started a capture ago, done by now: copied out for the JPEG thread. */
  private def collect(): Unit = {
    val staging = free.poll()
    glBindBuffer(GL_PIXEL_PACK_BUFFER, pbos(pendingPbo))
    if (staging == null) skipped += 1
    else {
      val bytes = pendingW * pendingH * 4
      val at = nglMapBufferRange(GL_PIXEL_PACK_BUFFER, 0L, bytes.toLong, GL_MAP_READ_BIT)
      if (at != 0L) {
        MemoryUtil.memCopy(at, MemoryUtil.memAddress(staging.buf), bytes.toLong)
        glUnmapBuffer(GL_PIXEL_PACK_BUFFER)
        staging.w = pendingW
        staging.h = pendingH
        staging.nanos = pendingNanos
        if (!jobs.offer(staging)) free.offer(staging)
      } else free.offer(staging)
    }
    glBindBuffer(GL_PIXEL_PACK_BUFFER, 0)
    pendingPbo = -1
  }

  /** The small framebuffer the window is scaled into, and the pixel buffers it is read into. */
  private def target(w: Int, h: Int): Unit = {
    if (fbo == 0) {
      fbo = glGenFramebuffers()
      color = glGenRenderbuffers()
      for (i <- pbos.indices) {
        pbos(i) = glGenBuffers()
        glBindBuffer(GL_PIXEL_PACK_BUFFER, pbos(i))
        glBufferData(GL_PIXEL_PACK_BUFFER, maxBytes.toLong, GL_STREAM_READ)
      }
      glBindBuffer(GL_PIXEL_PACK_BUFFER, 0)
    }
    glBindRenderbuffer(GL_RENDERBUFFER, color)
    glRenderbufferStorage(GL_RENDERBUFFER, GL_RGBA8, w, h)
    glBindRenderbuffer(GL_RENDERBUFFER, 0)
    glBindFramebuffer(GL_FRAMEBUFFER, fbo)
    glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_RENDERBUFFER, color)
    glBindFramebuffer(GL_FRAMEBUFFER, 0)
    targetW = w
    targetH = h
    pendingPbo = -1 // a read of the old size is no good to anyone
  }

  private def start(): Unit = {
    for (_ <- 0 until Staged) free.offer(new Staging(MemoryUtil.memAlloc(maxBytes)))
    encoder = new Thread(() => encodeLoop(), "FrameHistory")
    encoder.setDaemon(true)
    encoder.start()
  }

  // ── The JPEG thread ────────────────────────────────────────────────────

  private def encodeLoop(): Unit = {
    val rgb = MemoryUtil.memAlloc(maxBytes / 4 * 3)
    val out = new ByteArrayOutputStream(64 * 1024)
    // stb hands the JPEG over in small pieces: each copied through one scratch array, not a new one
    val scratch = new Array[Byte](8192)
    val write = STBIWriteCallback.create((_: Long, data: Long, size: Int) => {
      var done = 0
      while (done < size) {
        val n = Math.min(scratch.length, size - done)
        MemoryUtil.memByteBuffer(data + done, n).get(scratch, 0, n)
        out.write(scratch, 0, n)
        done += n
      }
    })
    try {
      while (running) {
        val job = jobs.poll(200, TimeUnit.MILLISECONDS)
        if (job != null) {
          try {
            // RGBA, the bottom row first, to RGB the right way up
            val w = job.w
            val h = job.h
            var y = 0
            while (y < h) {
              val src = (h - 1 - y) * w * 4
              val dst = y * w * 3
              var x = 0
              while (x < w) {
                rgb.put(dst + x * 3, job.buf.get(src + x * 4))
                rgb.put(dst + x * 3 + 1, job.buf.get(src + x * 4 + 1))
                rgb.put(dst + x * 3 + 2, job.buf.get(src + x * 4 + 2))
                x += 1
              }
              y += 1
            }
            out.reset()
            if (STBImageWrite.stbi_write_jpg_to_func(write, 0L, w, h, 3, rgb, quality) != 0) {
              val frame = Frame(job.nanos, out.toByteArray)
              kept.synchronized {
                kept.addLast(frame)
                while (!kept.isEmpty && frame.nanos - kept.peekFirst().nanos > keepMs * 1000000L) kept.removeFirst()
              }
            }
          } finally free.offer(job)
        }
      }
    } catch {
      case _: InterruptedException =>
    } finally {
      write.free()
      MemoryUtil.memFree(rgb)
    }
  }

  /** The frames kept, oldest first: the last `keepMs` before now, or fewer. */
  def frames: Seq[Frame] = kept.synchronized(kept.toArray(new Array[Frame](0)).toSeq)

  /** Let it all go: the GL objects (on the thread the window's context is current on) and the thread. */
  def dispose(): Unit = {
    running = false
    if (encoder != null) { encoder.interrupt(); encoder.join(1000) }
    if (fbo != 0) {
      glDeleteFramebuffers(fbo)
      glDeleteRenderbuffers(color)
      pbos.foreach(p => if (p != 0) glDeleteBuffers(p))
      fbo = 0
    }
    var s = free.poll()
    while (s != null) { MemoryUtil.memFree(s.buf); s = free.poll() }
    s = jobs.poll()
    while (s != null) { MemoryUtil.memFree(s.buf); s = jobs.poll() }
    kept.synchronized(kept.clear())
  }
}

object FrameHistory {
  /** 480 pixels across, four a second, for fifteen seconds: about sixty JPEGs of 20-40KB. */
  val Width = 480
  val EveryMs = 250L
  val KeepMs = 15000L
  val Quality = 70
  /** Frames read back and waiting for the JPEG thread, at most: past that, a capture is skipped. */
  private val Staged = 3

  /** A frame kept: when it was drawn (System.nanoTime) and the JPEG. */
  final case class Frame(nanos: Long, jpeg: Array[Byte])
}
