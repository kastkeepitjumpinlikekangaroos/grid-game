package com.gridgame.client.devtools

import com.gridgame.client.game.GameClient
import com.gridgame.common.Constants
import com.gridgame.common.diagnostics.Recording
import com.gridgame.common.diagnostics.Recording._
import com.gridgame.common.model.WorldData
import com.gridgame.common.protocol.{GameEvent, GameEventPacket, ItemAction, ItemPacket, LobbyAction, LobbyActionPacket, PacketSerializer}
import com.gridgame.common.world.WorldLoader
import com.google.gson.{JsonObject, JsonParser}

import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.UUID
import java.util.zip.ZipFile
import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal

/**
 * A problem report's recording, read for a replay: every record of every segment kept, in time order,
 * and what the report says of itself (whose match it was, its marks, what went wrong in it and what the
 * client noticed). Times are microseconds since the recording began, which is when the match did.
 *
 * @param gaps  where segments are missing (a long match keeps its first and its newest): from the end
 *              of the one before to the start of the one after, in microseconds
 */
final class ReplayRecording(val code: String, val records: IndexedSeq[Record], val report: JsonObject,
                            val segments: Seq[Int], val gaps: Seq[(Long, Long)], val folder: Path) {
  /** How long it runs. */
  val durationMicros: Long = if (records.isEmpty) 0L else records.last.timeMicros

  private def obj(o: JsonObject, key: String): Option[JsonObject] =
    if (o != null && o.has(key) && o.get(key).isJsonObject) Some(o.getAsJsonObject(key)) else None
  private def str(o: JsonObject, key: String): Option[String] =
    if (o != null && o.has(key) && o.get(key).isJsonPrimitive) Some(o.get(key).getAsString).filter(_.nonEmpty) else None
  private def long(o: JsonObject, key: String): Option[Long] =
    try str(o, key).map(_.toLong) catch { case _: NumberFormatException => None }

  /** Whose match it was: the report's player, or the one its client's packets name. */
  val playerId: UUID = obj(report, "connection").flatMap(str(_, "playerId"))
    .flatMap(s => try Some(UUID.fromString(s)) catch { case _: IllegalArgumentException => None })
    .orElse(records.collectFirst { case r if r.kind == PacketOut && r.payload.length >= 21 && idIn(r.payload) != null => idIn(r.payload) })
    .getOrElse(new UUID(0L, 0L))

  val playerName: String = obj(report, "connection").flatMap(str(_, "player")).getOrElse("replay")

  private def idIn(p: Array[Byte]): UUID = {
    val id = new UUID(Recording.long(p, 5), Recording.long(p, 13))
    if (id.getMostSignificantBits == 0L && id.getLeastSignificantBits == 0L) null else id
  }

  /** The moments the player marked (F8): when, and a few words for them. */
  val marks: Seq[(Long, String)] = {
    val fromReport = arrayOf("marks").flatMap(m => long(m, "atMicros").map(t => t -> s"mark ${str(m, "number").getOrElse("?")}"))
    if (fromReport.nonEmpty) fromReport
    else records.collect { case r if r.kind == Mark && r.payload.length >= 2 => r.timeMicros -> s"mark ${ushort(r.payload, 0)}" }
  }.sortBy(_._1)

  /** What the client noticed by itself, and what went wrong: when, and what. */
  val moments: Seq[(Long, String)] = {
    val noticed = arrayOf("noticed").flatMap(n => long(n, "atMicros").map(t => t -> str(n, "text").orElse(str(n, "kind")).getOrElse("noticed")))
    val incidents = arrayOf("incidents").flatMap(n => long(n, "atMicros").filter(_ > 0).map(t => t -> s"${str(n, "kind").getOrElse("incident")}"))
    val fromReport = noticed ++ incidents
    if (fromReport.nonEmpty) fromReport
    else records.collect {
      case r if r.kind == Noticed && r.payload.length >= NoticedRecord.Size =>
        val detail = new String(r.payload, NoticedRecord.Size, r.payload.length - NoticedRecord.Size, StandardCharsets.UTF_8)
        r.timeMicros -> NoticedKind.describe(r.payload(NoticedRecord.Kind) & 0xFF, int(r.payload, NoticedRecord.A), int(r.payload, NoticedRecord.B), detail)
      case r if r.kind == Incident && r.payload.length >= 1 => r.timeMicros -> IncidentKind.name(r.payload(0) & 0xFF)
    }
  }.sortBy(_._1)

  private def arrayOf(key: String): Seq[JsonObject] =
    if (report != null && report.has(key) && report.get(key).isJsonArray)
      report.getAsJsonArray(key).asScala.toSeq.collect { case e if e.isJsonObject => e.getAsJsonObject }
    else Nil
}

object ReplayRecording {
  private val SegmentName = """(?:.*/)?seg-(\d+)\.rec""".r

  /**
   * A report as the server keeps it (a folder with its report.zip), a bare report.zip (the client's "Save
   * a copy"), an unpacked report (report.json and recording/), or a client's own match folder (its
   * seg-NNNN.rec files and match.json, in diagnostics/match/).
   */
  def load(input: Path): ReplayRecording =
    if (Files.isDirectory(input)) {
      val zip = input.resolve("report.zip")
      if (Files.isRegularFile(zip)) fromZip(zip, input) else fromFolder(input)
    } else fromZip(input, input.toAbsolutePath.getParent)

  private def fromZip(file: Path, folder: Path): ReplayRecording = {
    val zip = new ZipFile(file.toFile)
    try {
      val report = Option(zip.getEntry(ReportFiles.ReportJson)).map(e => json(zip.getInputStream(e))).orNull
      val segs = zip.entries().asScala.toVector.flatMap(e => e.getName match {
        case SegmentName(n) if !e.isDirectory => Some(n.toInt -> e)
        case _ => None
      }).sortBy(_._1)
      val read = segs.map { case (n, e) => n -> records(zip.getInputStream(e)) }
      build(codeOf(folder, file), read, report, folder)
    } finally zip.close()
  }

  private def fromFolder(dir: Path): ReplayRecording = {
    val report = Seq(dir.resolve(ReportFiles.ReportJson), dir.resolve("match.json")).find(Files.isRegularFile(_))
      .map(p => json(Files.newInputStream(p))).orNull
    val places = Seq(dir.resolve("recording"), dir).filter(Files.isDirectory(_))
    val segs = places.flatMap { d =>
      val s = Files.list(d)
      try s.iterator().asScala.toVector.flatMap(p => p.getFileName.toString match {
        case SegmentName(n) => Some(n.toInt -> p)
        case _ => None
      }) finally s.close()
    }.distinctBy(_._1).sortBy(_._1)
    val read = segs.map { case (n, p) => n -> records(Files.newInputStream(p)) }
    build(codeOf(dir, dir), read, report, dir)
  }

  private def build(code: String, read: Seq[(Int, Vector[Record])], report: JsonObject, folder: Path): ReplayRecording = {
    val gaps = read.sliding(2).collect {
      case Seq((a, ra), (b, rb)) if b != a + 1 && ra.nonEmpty && rb.nonEmpty => (ra.last.timeMicros, rb.head.timeMicros)
    }.toVector
    new ReplayRecording(code, read.flatMap(_._2).toVector, report, read.map(_._1), gaps, folder)
  }

  /** The server's code for the report (its server.json), or the name of what was opened. */
  private def codeOf(folder: Path, opened: Path): String = {
    val serverJson = if (folder != null) folder.resolve("server.json") else null
    val fromServer = if (serverJson != null && Files.isRegularFile(serverJson)) {
      val o = json(Files.newInputStream(serverJson))
      if (o != null && o.has("code")) Some(o.get("code").getAsString) else None
    } else None
    fromServer.getOrElse(Option(opened.getFileName).map(_.toString.stripSuffix(".zip")).getOrElse("replay"))
  }

  private def records(in: InputStream): Vector[Record] =
    try new SegmentReader(in).records.toVector
    catch { case _: IllegalArgumentException => Vector.empty }
    finally in.close()

  private def json(in: InputStream): JsonObject =
    try {
      val e = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8))
      if (e.isJsonObject) e.getAsJsonObject else null
    } catch { case NonFatal(_) => null } finally in.close()
}

/**
 * A recording played into a GameClient that connects nowhere, as the player's own game heard it:
 * forward to any time, as a clock passes (`advanceTo`), and back by starting again from the start and
 * playing to the time in a burst (`seek`). What a replay draws, and what its test drives, with no
 * window (devtools ReportReplay).
 *
 * The server's packets go in as they arrived (`processPacket`), stamped on the client's clock
 * (`nanoClock`) with the time the recording kept, so projectiles fly as they did. Where we stood, where
 * we were drawn and where we aimed come from the recording's frames (`replayLocal`): the client never
 * takes a step of its own here. A packet the recording cut to its header (a password, a token, a chat
 * line: the player's own) is left out, and what we sent the server is only looked at for who we were.
 *
 * The client times some things on the wall clock from when it hears of them, so packets fed in a burst
 * (a seek) start them all at once. The two the HUD shows as numbers are put right after a burst: the
 * match clock (the last TIME_SYNC, less the time since it) and the opening's countdown (the last
 * MATCH_OPENING, the same way), each told to the client again as the server would have at that moment.
 * The match's length comes from GAME_STARTING, as the lobby that the recording starts after had said.
 * Effects (a hold, a burn) are left as they come: exact played at 1x, a little long after a burst.
 *
 * Two things of ours only our own game knew, never the server's packets, are put in from the recording:
 * how long Q, E and R had left to cool down (its state samples, at the time played to: `cooldownsAt`),
 * and each item we used (the USE we sent: the server says nothing of a use it takes).
 */
final class ReplaySession(val recording: ReplayRecording, worlds: String => WorldData = ReplaySession.loadWorld) {
  import ReplaySession._

  private val records = recording.records
  // The state samples (four a second), for the cooldowns: when, and Q's, E's and R's ms left
  private val states: Array[Array[Long]] = records.iterator
    .filter(r => r.kind == State && r.payload.length >= StateRecord.Size)
    .map(r => Array(r.timeMicros, int(r.payload, StateRecord.QLeftMs).toLong, int(r.payload, StateRecord.ELeftMs).toLong,
      int(r.payload, StateRecord.RLeftMs).toLong))
    .toArray
  // The client's clock (nanoClock): the recording's time, from a base that keeps it positive
  @volatile private var nowNanos: Long = BaseNanos
  private var next = 0
  private var fedTo = -1L
  private var _client: GameClient = fresh()
  private var _frame: Array[Byte] = _
  private var _errors = 0
  // The last TIME_SYNC (seconds left, when) and MATCH_OPENING (ms left, rules, when) heard; before the
  // first TIME_SYNC, the match's start counts as one (its length, when)
  private var lastSync: (Int, Long) = _
  private var lastOpening: (Int, Byte, Long) = _

  /** The client being played into: a new one after a `seek` back. */
  def client: GameClient = _client

  /** Where the playback is: everything up to here has gone in. */
  def timeMicros: Long = Math.max(0L, fedTo)

  /** The last frame the recording had at or before now (FrameRecord), or null. */
  def lastFrame: Array[Byte] = _frame

  /** Packets that threw as the client took them in. */
  def packetErrors: Int = _errors

  /** Feed every record up to `t`, and hold the client's clock there. Forward only. */
  def advanceTo(t: Long): Unit = {
    val from = fedTo
    while (next < records.length && records(next).timeMicros <= t) {
      feed(records(next), t)
      next += 1
    }
    if (t > fedTo) fedTo = t
    nowNanos = nanosAt(fedTo)
    if (fedTo - from > BurstMicros) settleClocks(fedTo)
    // Every call, paused too: the client counts Q and E down on the wall clock
    val c = _client
    val q = cooldownsAt(fedTo, c.getSelectedCharacterDef.qAbility.cooldownMs, 1)
    val e = cooldownsAt(fedTo, c.getSelectedCharacterDef.eAbility.cooldownMs, 2)
    val r = cooldownsAt(fedTo, Constants.COOLDOWN_RESET_MS, 3)
    c.replayCooldowns(q, e, r)
  }

  /**
   * How long the cooldown in this column of `states` (1 Q, 2 E, 3 R) had left at `t`, ms. The sample
   * before counted down to `t`; but a cast between two samples shows only in the one after, which says
   * when it was (its time less what had run of the cooldown), so a cast is on the HUD as it was cast,
   * not up to a quarter of a second later. Only a cast dated between the two samples is one: a cooldown
   * a hit cut short (reduceAbilityCooldownOnHit) dates to before the sample before.
   */
  private[devtools] def cooldownsAt(t: Long, cooldownMs: Int, column: Int): Long = {
    // The last sample at or before t
    var lo = 0
    var hi = states.length
    while (lo < hi) { val mid = (lo + hi) >>> 1; if (states(mid)(0) <= t) lo = mid + 1 else hi = mid }
    val after = if (lo < states.length) states(lo) else null
    if (after != null) {
      val left = after(column)
      val castAt = after(0) - (cooldownMs - left) * 1000L
      if (left > 0 && left <= cooldownMs && castAt <= t && (lo == 0 || castAt > states(lo - 1)(0)))
        return left + (after(0) - t) / 1000L
    }
    if (lo == 0) 0L
    else { val before = states(lo - 1); Math.max(0L, before(column) - (t - before(0)) / 1000L) }
  }

  /** After a burst, the match clock and the opening's countdown told again as they stood at `t`. */
  private def settleClocks(t: Long): Unit = {
    val c = _client
    def tell(p: GameEventPacket): Unit = try c.processPacket(p) catch { case NonFatal(_) => _errors += 1 }
    if (lastSync != null) {
      val (left, at) = lastSync
      val now = Math.max(0, left - ((t - at) / 1000000L).toInt)
      tell(new GameEventPacket(0, new UUID(0L, 0L), 0, GameEvent.TIME_SYNC, remainingSeconds = now))
    }
    if (lastOpening != null) {
      val (ms, rules, at) = lastOpening
      if (ms > 0) {
        val now = Math.max(0L, ms - (t - at) / 1000L).toInt
        tell(new GameEventPacket(0, new UUID(0L, 0L), 0, GameEvent.MATCH_OPENING, openingMs = now, openingRules = rules))
      }
    }
  }

  /** Go to `t`: forward as `advanceTo`, or, behind where it is, a new client played to `t` from the
    * start. True when the client is a new one. */
  def seek(t: Long): Boolean = {
    val target = Math.max(0L, t)
    if (target < fedTo) {
      _client = fresh()
      next = 0
      fedTo = -1L
      _frame = null
      advanceTo(target)
      true
    } else {
      advanceTo(target)
      false
    }
  }

  private def fresh(): GameClient = {
    lastSync = null
    lastOpening = null
    val c = new GameClient("replay", 0, WorldData.createEmpty(Constants.GRID_SIZE, Constants.GRID_SIZE), recording.playerName)
    c.packetSink = _ => ()
    c.completeAuthAndJoin(recording.playerId, recording.playerName)
    c.nanoClock = () => nowNanos
    c.setWorldFileListener(file => c.setWorld(worlds(file)))
    c
  }

  private def feed(r: Record, to: Long): Unit = r.kind match {
    case PacketIn if (r.flags & FlagRedacted) == 0 && r.payload.length == com.gridgame.common.Constants.PACKET_PAYLOAD_SIZE =>
      nowNanos = nanosAt(r.timeMicros)
      try {
        val p = PacketSerializer.deserialize(r.payload)
        p match {
          // The match's length, which the lobby said before the recording began
          case l: LobbyActionPacket if l.getAction == LobbyAction.GAME_STARTING && l.getDurationMinutes > 0 =>
            _client.currentLobbyDuration = l.getDurationMinutes
            lastSync = (l.getDurationMinutes * 60, r.timeMicros)
            lastOpening = null
          case g: GameEventPacket if g.getEventType == GameEvent.TIME_SYNC => lastSync = (g.getRemainingSeconds, r.timeMicros)
          case g: GameEventPacket if g.getEventType == GameEvent.MATCH_OPENING => lastOpening = (g.getOpeningMs, g.getOpeningRules, r.timeMicros)
          case _ =>
        }
        _client.processPacket(p)
      } catch { case NonFatal(_) => _errors += 1 }
    // An item we used, gone from what we carry (copies of the same USE are nothing)
    case PacketOut if (r.flags & FlagRedacted) == 0 && r.payload.length == com.gridgame.common.Constants.PACKET_PAYLOAD_SIZE =>
      try PacketSerializer.deserialize(r.payload) match {
        case i: ItemPacket if i.getAction == ItemAction.USE && i.getPlayerId.equals(recording.playerId) =>
          _client.replayItemUsed(i.getItemTypeId, i.getItemId, (to - r.timeMicros) / 1000L)
        case _ =>
      } catch { case NonFatal(_) => }
    case Frame if r.payload.length >= FrameRecord.Size =>
      val b = r.payload
      try {
        _client.replayLocal(float(b, FrameRecord.CellX).toInt, float(b, FrameRecord.CellY).toInt,
          float(b, FrameRecord.DrawnX).toDouble, float(b, FrameRecord.DrawnY).toDouble)
      } catch { case _: IllegalArgumentException => } // a cell off the grid
      _client.setMouseWorldPosition(float(b, FrameRecord.AimX).toDouble, float(b, FrameRecord.AimY).toDouble)
      _client.setMovementInputActive((ushort(b, FrameRecord.Flags) & FrameFlags.Moving) != 0)
      _frame = b
    case _ =>
  }
}

object ReplaySession {
  /** Where the client's clock starts: far from zero, so nothing reads it as unset. */
  val BaseNanos: Long = 1000000000000L

  /** An advance longer than this is a burst (a seek), after which the clocks are put right. */
  val BurstMicros: Long = 250000L

  def nanosAt(micros: Long): Long = BaseNanos + micros * 1000L

  /** A map the server named, as the game loads one (MatchWindow): one with a path in it is no map. */
  def loadWorld(file: String): WorldData =
    if (file.isEmpty || file.contains("..") || file.contains("/") || file.contains("\\"))
      WorldData.createEmpty(Constants.GRID_SIZE, Constants.GRID_SIZE)
    else try WorldLoader.load("worlds/" + file)
    catch { case NonFatal(e) =>
      System.err.println(s"replay: couldn't load $file (${e.getMessage})")
      WorldData.createEmpty(Constants.GRID_SIZE, Constants.GRID_SIZE)
    }
}
