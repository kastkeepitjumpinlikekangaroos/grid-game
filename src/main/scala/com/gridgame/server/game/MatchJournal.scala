package com.gridgame.server.game

import com.gridgame.common.model.CharacterDef
import com.gridgame.common.model.Player
import com.gridgame.common.model.ProjectileDef
import com.gridgame.common.protocol.AttackSlot

import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * What the server saw and decided in one match, a line at a time, for a problem report about that
 * match to be kept beside (ProblemReports; design/diagnostics.md): who played what, who joined,
 * rejoined, left or lost their connection, every request it refused and why, the positions it
 * corrected, the kills and the respawns, what went wrong in its ticks, and its end. A player's
 * client records what it saw of the match; this is what the server made of the same moments, which
 * no client can know (a refused shot says nothing back).
 *
 * A line is `mm:ss.mmm  <the player's id, 8 hex digits>  <their name>  <what>`, timed from the
 * match's start ([[begin]]). Bounded: the first [[MatchJournal.HeadLines]] (the start and its
 * roster) and the last [[MatchJournal.TailLines]] are kept, and a player's refusals are written at
 * most [[MatchJournal.RefusalsPerSecond]] a second, the rest counted in one line, so a client
 * flooding the server can't push the start out of it. A line is made only when something happens:
 * nothing here runs per tick, and a refusal's text is built only when it is written.
 *
 * @param enabled false for a journal that keeps nothing (a validator a test makes on its own)
 */
final class MatchJournal(val matchId: UUID, enabled: Boolean = true,
                         nanoClock: () => Long = () => System.nanoTime()) {
  import MatchJournal._

  @volatile private var origin: Long = nanoClock()
  @volatile private var began: Instant = Instant.now()

  private val head = new Array[String](if (enabled) HeadLines else 0)
  private var headCount = 0
  private val tail = new Array[String](if (enabled) TailLines else 0)
  private var tailNext = 0
  private var tailCount = 0
  private var dropped = 0L

  // A window of refusals per player: how many were written in its second, and how many weren't
  private val windows = new ConcurrentHashMap[UUID, RefusalWindow]()

  /** The match is under way: the journal's clock runs from here. */
  def begin(): Unit = { origin = nanoClock(); began = Instant.now() }

  /** When the match began, on the server's clock. */
  def beganAt: Instant = began

  /** Something about the match as a whole. */
  def note(what: String): Unit = if (enabled) add(line(elapsed(), null, null, what))

  /** Something about a player. */
  def note(player: Player, what: String): Unit = if (enabled) add(line(elapsed(), player.getId, player.getName, what))

  def note(playerId: UUID, name: String, what: String): Unit = if (enabled) add(line(elapsed(), playerId, name, what))

  /** A request of the player's the server refused: written unless RefusalsPerSecond of theirs have
    * been this second already; those that weren't are counted in a line of their own. */
  def refused(player: Player, what: => String): Unit = if (enabled) refused(player.getId, player.getName, what)

  def refused(playerId: UUID, name: String, what: => String): Unit = if (enabled) {
    val t = elapsed()
    val second = t / 1000000000L
    var w = windows.get(playerId)
    if (w == null) {
      val made = new RefusalWindow
      val had = windows.putIfAbsent(playerId, made)
      w = if (had != null) had else made
    }
    var unwritten = 0
    var unwrittenAt = 0L
    val write = w.synchronized {
      if (w.second != second) {
        unwritten = w.suppressed
        unwrittenAt = w.lastSuppressedAt
        w.second = second
        w.count = 0
        w.suppressed = 0
      }
      if (w.count < RefusalsPerSecond) { w.count += 1; true }
      else { w.suppressed += 1; w.lastSuppressedAt = t; false }
    }
    if (unwritten > 0) add(line(unwrittenAt, playerId, name, unwrittenLine(unwritten)))
    if (write) add(line(t, playerId, name, what))
  }

  /** Every line kept, oldest first: the head, a line saying how many went from between it and the
    * tail if any did, and the tail. Refusals not yet written up are counted at the end. */
  def lines: Vector[String] = {
    flushRefusals()
    synchronized {
      val out = Vector.newBuilder[String]
      var i = 0
      while (i < headCount) { out += head(i); i += 1 }
      if (dropped > 0) out += s"... $dropped lines dropped here: the journal keeps its first $HeadLines and its last $TailLines ..."
      val start = if (tailCount < TailLines) 0 else tailNext
      i = 0
      while (i < tailCount) { out += tail((start + i) % TailLines); i += 1 }
      out.result()
    }
  }

  /** The journal as the text file kept beside a report: what it is, then its lines. */
  def render: String = {
    val body = lines
    val sb = new StringBuilder
    sb.append(s"Match $matchId: what the server saw and decided in it, as it happened.\n")
    sb.append(s"It began $began (the server's clock). Each line is the time since then, the player's id and\n")
    sb.append("name (or ---- for the match as a whole) and what happened.\n\n")
    body.foreach(l => sb.append(l).append('\n'))
    sb.toString
  }

  /** Lines kept so far. */
  def size: Int = synchronized(headCount + tailCount)

  private def flushRefusals(): Unit = if (enabled) {
    windows.forEach { (id: UUID, w: RefusalWindow) =>
      var unwritten = 0
      var at = 0L
      w.synchronized {
        if (w.suppressed > 0) {
          unwritten = w.suppressed
          at = w.lastSuppressedAt
          w.suppressed = 0
        }
      }
      if (unwritten > 0) add(line(at, id, null, unwrittenLine(unwritten)))
    }
  }

  private def unwrittenLine(n: Int): String = s"... and $n more refusals that second, not written one by one"

  private def elapsed(): Long = nanoClock() - origin

  private def add(l: String): Unit = synchronized {
    if (headCount < HeadLines) {
      head(headCount) = l
      headCount += 1
    } else {
      if (tailCount == TailLines) dropped += 1 else tailCount += 1
      tail(tailNext) = l
      tailNext = (tailNext + 1) % TailLines
    }
  }
}

object MatchJournal {
  /** The first lines, always kept: the start of the match and who played in it. */
  val HeadLines = 500
  /** The last lines kept after those. */
  val TailLines = 19500
  /** A player's refusals written a second at most. */
  val RefusalsPerSecond = 20

  /** A journal that keeps nothing: a validator's until its match hands it its own. */
  val Off: MatchJournal = new MatchJournal(new UUID(0L, 0L), enabled = false)

  /** A character, as a line names it. */
  def characterName(id: Byte): String =
    if (CharacterDef.isValid(id)) CharacterDef.get(id).displayName else s"character $id"

  /** A projectile type, as a line names it. */
  def projectileName(t: Byte): String =
    try ProjectileDef.get(t).name catch { case _: Exception => s"projectile type $t" }

  /** An attack slot (AttackSlot), as a line names it. */
  def slotName(slot: Int): String = slot match {
    case AttackSlot.PRIMARY => "the primary"
    case AttackSlot.Q => "Q"
    case AttackSlot.E => "E"
    case AttackSlot.BURST => "the burst"
    case s => s"attack slot $s"
  }

  /** An exception and where it was thrown, in one line. */
  def describe(e: Throwable): String = {
    val where = e.getStackTrace.take(3).map(f => s"${f.getClassName.split('.').last}.${f.getMethodName}:${f.getLineNumber}")
    if (where.isEmpty) e.toString else s"$e at ${where.mkString(" < ")}"
  }

  /** `mm:ss.mmm  <id>  <name>  <what>`, `t` nanoseconds into the match. */
  private[game] def line(t: Long, id: UUID, name: String, what: String): String = {
    val ms = Math.max(0L, t / 1000000L)
    val who = if (id == null) "--------" else id.toString.substring(0, 8)
    val n = if (name == null) "" else name
    f"${ms / 60000}%02d:${(ms / 1000) % 60}%02d.${ms % 1000}%03d  $who  $n%-16s  $what"
  }

  private final class RefusalWindow {
    var second = -1L
    var count = 0
    var suppressed = 0
    var lastSuppressedAt = 0L
  }
}

/**
 * The journals of matches that have ended, kept a while (KeepMs, at most MaxKept, the oldest going
 * first) so a report about a match its player has just finished can still be kept with it. The
 * server keeps a running match's journal on the match itself.
 */
final class MatchJournals(keepMs: Long = MatchJournals.KeepMs, maxKept: Int = MatchJournals.MaxKept) {
  // Insertion order is the order they ended in
  private val kept = new java.util.LinkedHashMap[UUID, (MatchJournal, Long)]()

  /** A match has ended: its journal is kept from `now`. */
  def keep(journal: MatchJournal, now: Long = System.currentTimeMillis()): Unit = synchronized {
    kept.remove(journal.matchId)
    kept.put(journal.matchId, (journal, now))
    evict(now)
  }

  def get(matchId: UUID, now: Long = System.currentTimeMillis()): Option[MatchJournal] = synchronized {
    evict(now)
    Option(kept.get(matchId)).map(_._1)
  }

  /** Forget those kept long enough, and the oldest of too many. */
  def evict(now: Long = System.currentTimeMillis()): Unit = synchronized {
    val it = kept.values().iterator()
    var n = kept.size()
    while (it.hasNext) {
      val (_, endedAt) = it.next()
      if (n > maxKept || now - endedAt > keepMs) { it.remove(); n -= 1 }
    }
  }

  def size: Int = synchronized(kept.size())
}

object MatchJournals {
  /** How long a finished match's journal is kept: long enough to play out its scoreboard and send a report. */
  val KeepMs: Long = 30 * 60 * 1000L
  /** The most kept at once. */
  val MaxKept = 50
}
