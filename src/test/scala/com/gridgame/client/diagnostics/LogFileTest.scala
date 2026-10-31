package com.gridgame.client.diagnostics

import org.junit.Assert._
import org.junit.Test

import java.io.{ByteArrayOutputStream, PrintStream}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

/** The client's log on disk (LogFile): three runs kept, every line stamped with its time and thread, a
  * cap on a run's file, and what is printed still going where it went before. */
class LogFileTest {
  private def dir(): Path = Files.createTempDirectory("logs")
  private def lines(p: Path): Seq[String] = Files.readAllLines(p, StandardCharsets.UTF_8).toArray.map(_.toString).toSeq

  @Test def eachRunMovesTheOnesBeforeAlongAndKeepsThree(): Unit = {
    val d = dir()
    for (run <- 1 to 4) {
      val log = new LogFile(d)
      log.open()
      log.line("out", s"run $run")
      log.close()
    }
    def run(name: String): String = lines(d.resolve(name)).head
    assertTrue(run("client.log").endsWith("run 4"))
    assertTrue(run("client.1.log").endsWith("run 3"))
    assertTrue(run("client.2.log").endsWith("run 2"))
    assertFalse("the oldest goes", Files.exists(d.resolve("client.3.log")))
    val log = new LogFile(d)
    log.open()
    assertTrue("the run before is the one a crash report takes", lines(log.previous.get).head.endsWith("run 4"))
    log.close()
  }

  @Test def aLineSaysWhenAndWhichThreadWroteIt(): Unit = {
    val d = dir()
    val log = new LogFile(d)
    log.open()
    val t = new Thread(() => { log.line("out", "hello"); log.line("err", "it broke") }, "PacketProcessor")
    t.start(); t.join()
    log.close()
    val Seq(out, err) = lines(d.resolve("client.log"))
    assertTrue(out, out.matches("""\d{4}-\d\d-\d\d \d\d:\d\d:\d\d\.\d{3} \[PacketProcessor\] hello"""))
    assertTrue(err, err.matches("""\d{4}-\d\d-\d\d \d\d:\d\d:\d\d\.\d{3} \[PacketProcessor\] ERR it broke"""))
  }

  @Test def aFullLogSaysSoOnceAndCountsWhatItLeavesOut(): Unit = {
    val d = dir()
    val log = new LogFile(d, capBytes = 300)
    log.open()
    for (n <- 0 until 50) log.line("out", s"line $n of a loop printing every frame")
    log.close()
    val kept = lines(d.resolve("client.log"))
    assertTrue(Files.size(d.resolve("client.log")) < 300 + 200)
    assertEquals("one note that it is full", 1, kept.count(_.contains("log is full")))
    assertEquals(kept.size - 1 + log.linesLeftOut, 50L)
  }

  @Test def whatIsPrintedGoesToTheFileAndStillToTheConsole(): Unit = {
    val d = dir()
    val oldOut = System.out
    val oldErr = System.err
    val console = new ByteArrayOutputStream()
    System.setOut(new PrintStream(console, true, StandardCharsets.UTF_8))
    val log = new LogFile(d)
    val heard = new java.util.concurrent.CopyOnWriteArrayList[String]()
    try {
      log.open()
      log.onLine = l => heard.add(l)
      log.tee()
      System.out.println("printed")
      System.out.print("in two ")
      System.out.println("parts")
      System.err.println("an error")
    } finally {
      System.setOut(oldOut)
      System.setErr(oldErr)
      log.close()
    }
    assertEquals("printed\nin two parts\n", console.toString(StandardCharsets.UTF_8))
    val inFile = lines(d.resolve("client.log"))
    assertEquals(3, inFile.size)
    assertTrue(inFile(0).endsWith("] printed"))
    assertTrue("a line printed in two parts is one line", inFile(1).endsWith("] in two parts"))
    assertTrue(inFile(2).endsWith("] ERR an error"))
    assertEquals("and each is handed on (to the match's recording)", 3, heard.size)
  }
}
