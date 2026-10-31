package com.gridgame.client.diagnostics

import com.google.gson.{GsonBuilder, JsonObject, JsonParser}

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.UUID
import scala.jdk.CollectionConverters._

/**
 * A report, as the outbox keeps it: what it is (its zip beside it), and what has become of it.
 *
 * @param status       "pending" until the server keeps it, then "sent"
 * @param code         the server's code for it, once sent
 * @param notBeforeMs  when trying again is worth it (the server said wait), epoch ms
 */
final case class OutboxEntry(id: UUID, kind: Byte, createdAtMs: Long, size: Long, crc: Int, matchId: UUID,
                             summary: String, status: String = OutboxEntry.Pending, code: String = "",
                             attempts: Int = 0, lastError: String = "", notBeforeMs: Long = 0L, sentAtMs: Long = 0L) {
  def isPending: Boolean = status == OutboxEntry.Pending
}

object OutboxEntry {
  val Pending = "pending"
  val Sent = "sent"
}

/**
 * Reports on their way to the server (design/diagnostics.md, *Sending a report*): each one's zip and a
 * note of it (`<id>.zip`, `<id>.json`), kept until the server has it, so a report made while it can't be
 * sent (no connection, the server busy) goes the next time it can — after the next login, if need be.
 * Once sent, the zip goes and the note stays a month with the server's code for it.
 *
 * Never more than MaxPending waiting (the oldest goes first), nor any of them longer than KeepMs.
 */
final class ReportOutbox(val dir: Path) {
  import ReportOutbox._

  private val gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

  def zip(id: UUID): Path = dir.resolve(s"$id.zip")
  private def note(id: UUID): Path = dir.resolve(s"$id.json")

  def save(e: OutboxEntry): Unit = synchronized {
    Files.createDirectories(dir)
    val o = new JsonObject
    o.addProperty("id", e.id.toString)
    o.addProperty("kind", e.kind)
    o.addProperty("createdAt", e.createdAtMs)
    o.addProperty("size", e.size)
    o.addProperty("crc", e.crc)
    if (e.matchId != null) o.addProperty("matchId", e.matchId.toString)
    o.addProperty("summary", e.summary)
    o.addProperty("status", e.status)
    o.addProperty("code", e.code)
    o.addProperty("attempts", e.attempts)
    o.addProperty("lastError", e.lastError)
    o.addProperty("notBefore", e.notBeforeMs)
    o.addProperty("sentAt", e.sentAtMs)
    val tmp = dir.resolve(s"${e.id}.json.tmp")
    Files.writeString(tmp, gson.toJson(o), StandardCharsets.UTF_8)
    Files.move(tmp, note(e.id), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
  }

  def get(id: UUID): Option[OutboxEntry] = synchronized(read(note(id)))

  /** Every report the outbox knows of, oldest first. */
  def entries: Seq[OutboxEntry] = synchronized {
    if (!Files.isDirectory(dir)) Nil
    else {
      val s = Files.list(dir)
      try s.iterator().asScala.filter(_.getFileName.toString.endsWith(".json")).toVector.flatMap(read).sortBy(_.createdAtMs)
      finally s.close()
    }
  }

  /** The reports waiting to go whose zips are still there, oldest first. */
  def pending: Seq[OutboxEntry] = entries.filter(e => e.isPending && Files.isRegularFile(zip(e.id)))

  /** The server has it, under `code`: the zip goes, the note stays. */
  def markSent(id: UUID, code: String, now: Long = System.currentTimeMillis()): Unit = synchronized {
    get(id).foreach(e => save(e.copy(status = OutboxEntry.Sent, code = code, sentAtMs = now, lastError = "")))
    Files.deleteIfExists(zip(id))
  }

  /** It didn't go: why, and when it is worth trying again. */
  def markFailed(id: UUID, error: String, notBeforeMs: Long): Unit = synchronized {
    get(id).foreach(e => save(e.copy(attempts = e.attempts + 1, lastError = error, notBeforeMs = notBeforeMs)))
  }

  /** Gone, zip and note. */
  def discard(id: UUID): Unit = synchronized {
    Files.deleteIfExists(zip(id))
    Files.deleteIfExists(note(id))
  }

  /** The old let go: a pending report older than KeepMs, a sent one's note older than that, the oldest
    * pending past MaxPending, and a zip without a note (one being written when the client stopped). */
  def tidy(now: Long = System.currentTimeMillis()): Unit = synchronized {
    val all = entries
    all.filter(e => now - (if (e.isPending) e.createdAtMs else e.sentAtMs) > KeepMs).foreach(e => discard(e.id))
    val waiting = all.filter(e => e.isPending && now - e.createdAtMs <= KeepMs)
    waiting.dropRight(MaxPending).foreach(e => discard(e.id))
    if (Files.isDirectory(dir)) {
      val known = all.map(_.id.toString).toSet
      val s = Files.list(dir)
      try s.iterator().asScala.toVector.foreach { p =>
        val name = p.getFileName.toString
        if ((name.endsWith(".zip") && !known(name.stripSuffix(".zip"))) || name.endsWith(".tmp")) Files.deleteIfExists(p)
      } finally s.close()
    }
  }

  private def read(file: Path): Option[OutboxEntry] =
    try {
      if (!Files.isRegularFile(file)) None
      else {
        val o = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject
        def str(k: String): String = if (o.has(k) && !o.get(k).isJsonNull) o.get(k).getAsString else ""
        def long(k: String): Long = if (o.has(k)) o.get(k).getAsLong else 0L
        Some(OutboxEntry(UUID.fromString(str("id")), o.get("kind").getAsByte, long("createdAt"), long("size"),
          o.get("crc").getAsInt, if (str("matchId").isEmpty) null else UUID.fromString(str("matchId")), str("summary"),
          str("status"), str("code"), long("attempts").toInt, str("lastError"), long("notBefore"), long("sentAt")))
      }
    } catch { case _: Exception => None }
}

object ReportOutbox {
  val MaxPending = 5
  val KeepMs: Long = 30L * 24 * 3600 * 1000
}
