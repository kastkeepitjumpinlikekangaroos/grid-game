package com.gridgame.client.diagnostics

import java.lang.management.{ManagementFactory, ThreadInfo}

/**
 * Notices when the thread that draws the match and runs the menus (the JavaFX thread) stops answering:
 * a task is posted to it twice a second, and one that hasn't run after `stallMs` is a freeze. Then every
 * thread's stack goes into the log and the match's recording, once a freeze (design/diagnostics.md,
 * *Freezes*): what a player can only describe as "the game froze" is a deadlock or a stall someone can
 * read. It also keeps the run's marker up to date (`tick`), which says when the run was last alive.
 *
 * @param post      run a task on the watched thread (Platform.runLater)
 * @param onFreeze  how long it has been stuck, and every thread's stack
 * @param onThaw    it answered again, after this long
 * @param tick      every `tickMs` or so, from the watchdog's own thread
 */
final class Watchdog(post: Runnable => Unit, onFreeze: (Long, String) => Unit, onThaw: Long => Unit,
                     tick: () => Unit, stallMs: Long = 4000L, everyMs: Long = 500L, tickMs: Long = 10000L) {
  @volatile private var sentAtNs = 0L
  @volatile private var pending = false
  @volatile private var frozen = false
  @volatile private var running = true

  private val thread = new Thread(() => loop(), "Watchdog")
  thread.setDaemon(true)

  def start(): Unit = thread.start()
  def stop(): Unit = { running = false; thread.interrupt() }

  private def loop(): Unit = {
    var lastTickNs = System.nanoTime()
    try {
      while (running) {
        val now = System.nanoTime()
        if (!pending) {
          pending = true
          sentAtNs = now
          try post(() => answered()) catch { case _: Throwable => pending = false }
        } else {
          val stuckMs = (now - sentAtNs) / 1000000L
          if (!frozen && stuckMs >= stallMs) {
            frozen = true
            try onFreeze(stuckMs, Watchdog.allThreads()) catch { case _: Throwable => }
          }
        }
        if (now - lastTickNs >= tickMs * 1000000L) {
          lastTickNs = now
          try tick() catch { case _: Throwable => }
        }
        Thread.sleep(everyMs)
      }
    } catch { case _: InterruptedException => }
  }

  private def answered(): Unit = {
    if (frozen) {
      frozen = false
      try onThaw((System.nanoTime() - sentAtNs) / 1000000L) catch { case _: Throwable => }
    }
    pending = false
  }
}

object Watchdog {
  /** Every thread's stack, whole, with the locks each holds and waits on, and any deadlock found. */
  def allThreads(): String = {
    val mx = ManagementFactory.getThreadMXBean
    val sb = new StringBuilder
    val deadlocked = try Option(mx.findDeadlockedThreads()).map(_.toSet).getOrElse(Set.empty[Long]) catch { case _: Throwable => Set.empty[Long] }
    if (deadlocked.nonEmpty) sb.append(s"DEADLOCK among threads ${deadlocked.mkString(", ")}\n")
    val infos: Array[ThreadInfo] = try mx.dumpAllThreads(true, true) catch { case _: Throwable => Array.empty }
    for (info <- infos if info != null) {
      sb.append('"').append(info.getThreadName).append("\" #").append(info.getThreadId).append(' ').append(info.getThreadState)
      if (info.getLockName != null) sb.append(" on ").append(info.getLockName)
      if (info.getLockOwnerName != null) sb.append(" held by \"").append(info.getLockOwnerName).append('"')
      sb.append('\n')
      val locked = info.getLockedMonitors
      val stack = info.getStackTrace
      var i = 0
      while (i < stack.length) {
        sb.append("    at ").append(stack(i)).append('\n')
        locked.filter(_.getLockedStackDepth == i).foreach(m => sb.append("    - locked ").append(m).append('\n'))
        i += 1
      }
      sb.append('\n')
    }
    sb.toString
  }
}
