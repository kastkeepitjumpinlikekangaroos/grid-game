package com.gridgame.client.game

import com.gridgame.client.diagnostics.{OutboxEntry, ReportOutbox}
import com.gridgame.client.net.UploadConnection
import com.gridgame.common.protocol.{ProblemReportAction, ProblemReportRefusal}

import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.{ConcurrentHashMap, TimeUnit, TimeoutException}

/**
 * A report in the outbox on its way to the server (design/diagnostics.md, *Sending a report*): the session
 * asks the server to take it (BEGIN) and is given a ticket (READY), and the report goes down a connection
 * of its own with it (UploadConnection). The outbox is told what became of it: sent, with the server's
 * code, or kept to try again (after the wait the server asked for).
 *
 * Blocking: run it on a thread of its own, never the FX thread.
 */
object ReportSender {
  sealed trait Result
  /** The server keeps it, under `code`. */
  final case class Sent(code: String) extends Result
  /**
   * It didn't go, and is kept to try again: why (a ProblemReportRefusal, or one of NoAnswer, Broken,
   * Missing), how long the server asked us to wait, and whatever else is known.
   */
  final case class NotSent(reason: Int, retryAfterSeconds: Int, detail: String) extends Result

  /** The server said nothing to the BEGIN in time (an older server that doesn't take reports, say). */
  val NoAnswer: Int = -1
  /** The upload's own connection failed: couldn't connect, the server not the one met before, dropped. */
  val Broken: Int = -2
  /** The report's zip is gone from the outbox. */
  val Missing: Int = -3

  /** How long the server has to answer a BEGIN (a test's is shorter). */
  @volatile private[client] var answerTimeoutMs = 10000L

  // The reports on their way now: one report goes once at a time, whoever sends it (the report's card,
  // or the outbox emptied after a login)
  private val inFlight = ConcurrentHashMap.newKeySet[UUID]()

  /** Whether trying `reason` again later can work at all. */
  def worthRetrying(reason: Int): Boolean =
    reason != ProblemReportRefusal.TOO_BIG && reason != Missing

  /** Send `entry` over `client`'s session; `progress` is told the bytes gone and the report's size. */
  def send(client: GameClient, outbox: ReportOutbox, entry: OutboxEntry,
           progress: (Long, Long) => Unit = (_, _) => ()): Result = {
    val zip = outbox.zip(entry.id)
    if (!Files.isRegularFile(zip)) return NotSent(Missing, 0, "the report's file is gone")

    def notSent(reason: Int, retryAfter: Int, detail: String): Result = {
      val waitMs = if (retryAfter > 0) retryAfter * 1000L else if (worthRetrying(reason)) 60000L else 0L
      outbox.markFailed(entry.id, s"$reason: $detail", System.currentTimeMillis() + waitMs)
      System.err.println(s"Diagnostics: report ${entry.id} not sent ($reason, $detail)")
      NotSent(reason, retryAfter, detail)
    }
    def sent(code: String): Result = {
      outbox.markSent(entry.id, code)
      println(s"Diagnostics: report ${entry.id} sent; the server keeps it as $code")
      Sent(code)
    }

    val answer = try client.beginReport(entry.id, entry.size.toInt, entry.crc, entry.matchId, entry.kind)
      .get(answerTimeoutMs, TimeUnit.MILLISECONDS)
    catch {
      case _: TimeoutException => return notSent(NoAnswer, 0, "the server didn't answer")
      case e: Exception => return notSent(Broken, 0, String.valueOf(e.getMessage))
    }
    answer.getAction match {
      case ProblemReportAction.ACCEPTED => sent(answer.getCode)
      case ProblemReportAction.REFUSED => notSent(answer.getReason, answer.getRetryAfterSeconds, "refused")
      case ProblemReportAction.READY =>
        progress(0L, entry.size)
        UploadConnection.send(client.reportServerHost, client.reportServerPort, client.knownServers, entry.id,
          answer.getTicket, zip, n => progress(n, entry.size)) match {
          case UploadConnection.Accepted(code) => sent(code)
          case UploadConnection.Refused(reason, retry) => notSent(reason, retry, "refused on upload")
          case UploadConnection.Failed(reason) => notSent(reason, 0, "the upload arrived wrong")
          case UploadConnection.Broken(message) => notSent(Broken, 0, message)
        }
      case other => notSent(Broken, 0, s"the server answered with action $other")
    }
  }

  /** `send`, unless the report is already on its way (then BUSY, and nothing is sent twice). */
  def sendAlone(client: GameClient, outbox: ReportOutbox, entry: OutboxEntry,
                progress: (Long, Long) => Unit = (_, _) => ()): Result =
    if (!inFlight.add(entry.id)) NotSent(ProblemReportRefusal.BUSY, 30, "already on its way")
    else try send(client, outbox, entry, progress) finally inFlight.remove(entry.id)

  /**
   * Every report waiting in the outbox that is due, one after the other, over `client`'s session: what is
   * left from an earlier run, or from a moment the server couldn't take it. `onSent` hears of each that
   * went. Blocking.
   */
  def sendPending(client: GameClient, outbox: ReportOutbox, onSent: (OutboxEntry, String) => Unit): Unit = {
    outbox.tidy()
    val now = System.currentTimeMillis()
    for (entry <- outbox.pending if entry.notBeforeMs <= now && !inFlight.contains(entry.id)) sendAlone(client, outbox, entry) match {
      case Sent(code) => onSent(entry, code)
      case NotSent(_, _, _) =>
    }
  }
}
