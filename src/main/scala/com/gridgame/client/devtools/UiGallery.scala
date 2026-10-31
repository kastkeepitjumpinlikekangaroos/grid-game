package com.gridgame.client.devtools

import com.gridgame.client.ClientMain
import com.gridgame.client.diagnostics.{Diagnostics, PastFrame}
import com.gridgame.client.game.GameClient
import com.gridgame.client.i18n.Messages
import com.gridgame.client.net.{SavedLogin, ServerIdentityChangedException}
import com.gridgame.client.ui.{MapPreview, Theme, Widgets, WorldBackdrop}
import com.gridgame.client.ui.screens.{LastLogin, ReportAbout}
import com.gridgame.common.model.{CharacterId, MatchResults, Position, WorldData}
import com.gridgame.common.protocol._

import javafx.application.{Application, Platform}
import javafx.scene.{Group, Node, Parent, Scene, SnapshotParameters}
import javafx.scene.control.{Button, Labeled, ScrollPane}
import javafx.scene.paint.Color
import javafx.scene.transform.Transform
import javafx.stage.Stage

import java.io.File
import java.util.UUID
import java.util.concurrent.{CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.AtomicReference
import scala.jdk.CollectionConverters._

/**
 * Dev tool: every menu screen, in the states a player sees it in, drawn to a PNG each. Not
 * shipped — run with
 *
 *   bazel run //src/main/scala/com/gridgame/client:ui_gallery -- /tmp/ui
 *
 * Each screen is built by the real code (ClientMain's screens) on a GameClient that has no
 * network and is handed the server's packets by hand — a lobby list, a lobby's roster and chat,
 * a leaderboard, a profile, the end of a match — and is drawn at a window's size without ever
 * being shown. What the menus look like is judged here, as the projectile gallery is for
 * projectiles: at the size a player sees them, and every screen side by side.
 *
 *   --w=1440 --h=900   the window's size in logical pixels (default 1440x900)
 *   --scale=2          draw at 2x, as on a Retina display (canvases stay 1x and look soft)
 *   --only=a,b         only the named shots (see `shots`)
 *   --lang=ko          in another language (en, zh_CN, ko; English by default, whatever the
 *                      player has chosen)
 */
object UiGallery {
  def main(args: Array[String]): Unit = Application.launch(classOf[UiGalleryApp], args: _*)
}

class UiGalleryApp extends Application {
  private var width = 1440
  private var height = 900
  private var scale = 1.0

  override def start(stage: Stage): Unit = {
    val raw = getParameters.getRaw.asScala.toSeq
    def flag(name: String): Option[String] = raw.find(_.startsWith(name + "=")).map(_.drop(name.length + 1))
    val outDir = new File(raw.find(!_.startsWith("--")).getOrElse("ui_gallery"))
    width = flag("--w").map(_.toInt).getOrElse(1440)
    height = flag("--h").map(_.toInt).getOrElse(900)
    scale = flag("--scale").map(_.toDouble).getOrElse(1.0)
    val only = flag("--only").map(_.split(",").toSet)
    outDir.mkdirs()

    Theme.loadFonts()
    Messages.init()
    Messages.useForThisRun(flag("--lang").getOrElse("en"))
    Platform.setImplicitExit(false)

    // The shots run on a thread of their own and step onto the FX thread for each thing they do,
    // so the time they wait for (a fade, sprite sheets loading) passes with the FX thread free.
    new Thread(() => {
      try {
        // The world behind every screen, drawn before the first shot is taken
        fx(WorldBackdrop.prepare())
        MapPreview.settle()
        for ((name, shot) <- shots if only.forall(_.contains(name))) {
          val file = new File(outDir, s"$name.png")
          shot(file)
          println(s"wrote ${file.getAbsolutePath}")
        }
      } catch {
        case t: Throwable => t.printStackTrace()
      } finally Platform.exit()
    }, "ui-gallery").start()
  }

  // ── The shots ──────────────────────────────────────────────────────────

  private val me = "MapleMage"
  // A login kept from the last time, on a server of its own (it is never logged in with here)
  private def savedLogin = SavedLogin("play.gridgame.gg", 25565, me, new Array[Byte](32), Long.MaxValue)

  private def shots: Seq[(String, File => Unit)] = Seq(
    "01_login" -> (f => shoot(f, (app, s) => app.showWelcomeScreen(s, ""))),
    "02_signup" -> (f => shoot(f, (app, s) => app.showWelcomeScreen(s, ""),
      click = root => buttons(root).find(_.getText == Messages.t("Create an account")).foreach(_.fire()))),
    "03_login_notice" -> (f => shoot(f, (app, s) => app.showWelcomeScreen(s, Messages.t("Disconnected from the server")))),
    // A login kept from the last time: its card, as the client starts or once it has been taken over
    "03b_login_saved" -> (f => shoot(f, (app, s) => {
      app.savedLogin = savedLogin
      app.showWelcomeScreen(s, "")
    })),
    "03c_login_saved_elsewhere" -> (f => shoot(f, (app, s) => {
      app.savedLogin = savedLogin
      app.showWelcomeScreen(s, Messages.t("Your account logged in somewhere else"))
    })),
    // The server isn't the one met at its address before: nothing sent, both identities shown
    "03e_login_server_changed" -> (f => shoot(f, (app, s) => app.showServerChanged(s,
      new ServerIdentityChangedException("play.gridgame.gg", 25565,
        "SHA256:uNiVztksCsDhcc0u9e8BujQXVUpKZIDTMczCvj3tD2s", "SHA256:q7Lw0bE3fYxN2kTzR9cVmH4sJpA6gD1uWo8iKyF5tCe")))),
    // The form after a saved login ran out: where it was, with "Remember me" ticked
    "03d_login_expired" -> (f => shoot(f, (app, s) => {
      app.lastLogin = LastLogin(savedLogin.host, savedLogin.port, me)
      app.showWelcomeScreen(s, Messages.t("Your login has expired. Please log in again."))
    }, click = root => all(root).collect { case b: javafx.scene.control.CheckBox => b }.foreach(_.setSelected(true)))),
    "04_lobbies" -> (f => shoot(f, (app, s) => {
      app.showLobbyBrowser(s, "")
      lobbyList(app.client)
    })),
    "05_lobbies_empty" -> (f => shoot(f, (app, s) => app.showLobbyBrowser(s, Messages.t("Lobby was closed by the host")))),
    "06_room_guest" -> (f => shoot(f, (app, s) => {
      joinLobby(app.client, host = false, teams = false)
      app.showLobbyRoom(s)
      chat(app.client)
    })),
    "07_room_host_teams" -> (f => shoot(f, (app, s) => {
      joinLobby(app.client, host = true, teams = true)
      app.showLobbyRoom(s)
      chat(app.client)
    })),
    "08_practice" -> (f => shoot(f, (app, s) => app.showPracticeSetup(s))),
    "09_ranked" -> (f => shoot(f, (app, s) => {
      app.client.rankedElo = 1284
      app.showRankedQueue(s)
    })),
    "10_ranked_searching" -> (f => shoot(f, (app, s) => {
      app.client.rankedElo = 1284
      app.showRankedQueue(s)
    }, click = root => buttons(root).find(_.getText == Messages.t("Find Match")).foreach(_.fire()),
      after = app => app.client.processPacket(new RankedQueuePacket(seq(), app.client.getLocalPlayerId, 0,
        RankedQueueAction.QUEUE_STATUS, queueSize = 3, elo = 1284, waitTimeSeconds = 17)))),
    "11_leaderboard" -> (f => shoot(f, (app, s) => {
      app.showLeaderboard(s)
      leaderboard(app.client)
    })),
    "12_profile" -> (f => shoot(f, (app, s) => {
      app.showAccountView(s)
      history(app.client)
    })),
    "13_scoreboard_ffa_win" -> (f => shoot(f, (app, s) => {
      endMatch(app.client, teams = false, won = true, ranked = true)
      app.showScoreboard(s)
    })),
    "14_scoreboard_teams_loss" -> (f => shoot(f, (app, s) => {
      endMatch(app.client, teams = true, won = false, ranked = false)
      app.showScoreboard(s)
    })),
    "16_map_previews" -> (f => shoot(f, (_, s) => mapPreviews(s))),
    "15_scoreboard_practice" -> (f => shoot(f, (app, s) => {
      app.client.isPracticeMode = true
      endMatch(app.client, teams = false, won = true, ranked = false)
      app.client.practiceShots = 140
      app.client.practiceHits = 97
      app.client.practiceBestCombo = 12
      app.client.killCount = 9
      app.showScoreboard(s)
    })),
    // A problem report: asked for after a match with two moments marked (their pictures from a map's
    // preview), the same with what is sent opened and the form filled, sent, not sent, and after a crash
    "17_scoreboard_marked" -> (f => shoot(f, (app, s) => {
      reportableMatch(app.client)
      endMatch(app.client, teams = false, won = false, ranked = false)
      app.showScoreboard(s)
    })),
    "18_report_after_match" -> (f => shoot(f, (app, s) => {
      reportableMatch(app.client)
      endMatch(app.client, teams = false, won = false, ranked = false)
      app.showScoreboard(s)
      app.showProblemReport(s, ReportAbout.LastMatch)
    })),
    "19_report_filled" -> (f => shoot(f, (app, s) => {
      reportableMatch(app.client)
      endMatch(app.client, teams = false, won = false, ranked = false)
      app.showScoreboard(s)
      app.showProblemReport(s, ReportAbout.LastMatch)
    }, click = root => {
      buttons(root).find(_.getText == Messages.t("Movement")).foreach(_.fire())
      all(root).collectFirst { case a: javafx.scene.control.TextArea => a }.foreach(_.setText(
        "My dash went straight through the wall by the pond, and then the server pulled me back."))
      buttons(root).find(_.getText == Messages.t("What's sent")).foreach(_.fire())
    })),
    "20_report_sent" -> (f => shoot(f, (app, s) => {
      reportableMatch(app.client)
      answerReports(app.client, p => ProblemReportPacket.accepted(seq(), app.client.getLocalPlayerId, p.getReportId, "GG-7K3F9QX2"))
      endMatch(app.client, teams = false, won = false, ranked = false)
      app.showScoreboard(s)
      app.showProblemReport(s, ReportAbout.LastMatch)
    }, click = root => buttons(root).find(_.getText == Messages.t("Send report")).foreach(_.fire()))),
    "21_report_not_sent" -> (f => shoot(f, (app, s) => {
      reportableMatch(app.client)
      answerReports(app.client, p => ProblemReportPacket.refused(seq(), app.client.getLocalPlayerId, p.getReportId, ProblemReportRefusal.TOO_MANY, 900))
      endMatch(app.client, teams = false, won = false, ranked = false)
      app.showScoreboard(s)
      app.showProblemReport(s, ReportAbout.LastMatch)
    }, click = root => buttons(root).find(_.getText == Messages.t("Send report")).foreach(_.fire()))),
    "22_report_after_crash" -> (f => shoot(f, (app, s) => {
      crashedRun()
      lobbyList(app.client)
      app.showLobbyBrowser(s)
      app.showProblemReport(s, ReportAbout.PreviousRun)
    }))
  )

  // ── A problem report's makings ─────────────────────────────────────────

  private lazy val diagnosticsDir = java.nio.file.Files.createTempDirectory("ui-gallery-diagnostics")

  /** Diagnostics of the gallery's own (never the player's), set up afresh: with a left-over marker, it
    * finds a run that never closed. */
  private def freshDiagnostics(leftOver: Boolean = false): Unit = {
    Diagnostics.resetForTest()
    val dir = java.nio.file.Files.createTempDirectory(diagnosticsDir, "run")
    if (leftOver) java.nio.file.Files.writeString(dir.resolve("running.properties"),
      s"pid=4242\nstartedAt=${System.currentTimeMillis() - 3600000}\nlastSeenAt=${System.currentTimeMillis() - 600000}\ninMatch=true\n")
    if (leftOver) {
      val m = java.nio.file.Files.createDirectories(dir.resolve("match").resolve("20261030-120000-000"))
      java.nio.file.Files.write(m.resolve("seg-0000.rec"), new Array[Byte](300000))
    }
    Diagnostics.install(Nil, Some(dir), teeConsole = false)
  }

  /** The match the report is about: recorded, two moments marked with a picture each, a freeze in it. */
  private def reportableMatch(c: GameClient): Unit = fx {
    freshDiagnostics()
    Diagnostics.packetTap.received(new LobbyActionPacket(seq(), c.getLocalPlayerId, Packet.getCurrentTimestamp,
      LobbyAction.GAME_STARTING, 7.toShort, 5.toByte, 5.toByte, 4.toByte, 8.toByte, 0.toByte, "Chill FFA", 0.toByte, 0.toByte, 2.toByte).serialize(), tcp = true)
    Diagnostics.noteMatch("map", "the_lagoon.json")
    Diagnostics.noteMatch("character", "Wizard")
    for (piece <- Seq(0, 1)) {
      val (px, w, h) = picture(piece)
      // With the seconds before it, as the match's window keeps them
      val now = System.nanoTime()
      Diagnostics.markMoment(px, w, h, (0 until 8).map(i => PastFrame(now - (8 - i) * 250000000L, Array[Byte](-1, -40))))
    }
    Diagnostics.incident(com.gridgame.common.diagnostics.Recording.IncidentKind.Freeze, "not answering for 4200ms")
    // What the game noticed along the way
    import com.gridgame.common.diagnostics.Recording.NoticedKind
    Diagnostics.noticed(NoticedKind.SnapBack, 320)
    Diagnostics.noticed(NoticedKind.ShotUnanswered, 1, 1200, msAgo = 1200)
    Diagnostics.noticed(NoticedKind.Died, CharacterId.Wizard.id.toInt)
    Diagnostics.noticed(NoticedKind.ServerQuiet, 2100, msAgo = 2100)
    Diagnostics.noticed(NoticedKind.PingSpike, 420)
    Diagnostics.matchEnded("finished")
    // Spread over a match of four minutes, as a real one's would be (made in a moment here)
    Diagnostics.lastMatch.foreach { r =>
      def at(m: Int, sec: Int): Long = (m * 60 + sec) * 1000000L
      r.durationMicros = at(4, 12)
      val marks = r.marks.toArray.map(_.asInstanceOf[com.gridgame.client.diagnostics.MarkedMoment])
      r.marks.clear()
      marks.zip(Seq(at(1, 12), at(3, 5))).foreach { case (m, t) =>
        r.marks.add(m.copy(atMicros = t, frames = m.frames.zipWithIndex.map { case (f, i) => f.copy(atMicros = t - (8 - i) * 250000L) }))
      }
      val incidents = r.incidents.toArray.map(_.asInstanceOf[com.gridgame.client.diagnostics.IncidentNote])
      r.incidents.clear()
      incidents.foreach(n => r.incidents.add(n.copy(atMicros = at(2, 40))))
      val noticed = r.noticed.toArray.map(_.asInstanceOf[com.gridgame.client.diagnostics.NoticedMoment])
      r.noticed.clear()
      noticed.zip(Seq(at(0, 48), at(1, 55), at(2, 31), at(3, 20), at(3, 58))).foreach { case (n, t) => r.noticed.add(n.copy(atMicros = t)) }
    }
  }

  /** A piece of the Meadow (the world behind the menus) as a frame read back from the window would be:
    * RGB, the bottom row first. */
  private def picture(piece: Int): (java.nio.ByteBuffer, Int, Int) = {
    val w = 640; val h = 360
    var img: javafx.scene.image.Image = null
    WorldBackdrop.picture(i => img = i)
    val px = org.lwjgl.system.MemoryUtil.memAlloc(w * h * 3)
    if (img != null) {
      val reader = img.getPixelReader
      val ox = Math.min(img.getWidth.toInt - w, 120 + piece * 380)
      val oy = Math.min(img.getHeight.toInt - h, 160 + piece * 120)
      for (y <- 0 until h; x <- 0 until w) {
        val argb = reader.getArgb(ox + x, oy + y)
        val i = ((h - 1 - y) * w + x) * 3
        px.put(i, (argb >> 16).toByte); px.put(i + 1, (argb >> 8).toByte); px.put(i + 2, argb.toByte)
      }
    }
    (px, w, h)
  }

  /** The server's answer to a BEGIN, as the session would hear it. */
  private def answerReports(c: GameClient, say: ProblemReportPacket => Packet): Unit =
    c.packetSink = {
      case p: ProblemReportPacket if p.getAction == ProblemReportAction.BEGIN => c.processPacket(say(p))
      case _ =>
    }

  /** The run before this one never closed: a left-over marker, and the match it was in. */
  private def crashedRun(): Unit = freshDiagnostics(leftOver = true)

  /** Every map's preview at each size the menus draw one: the lobby form's, a lobby row's, the
    * lobby room's. A new map's has to read as the whole map at all three. */
  private def mapPreviews(stage: Stage): Unit = {
    import javafx.scene.layout.{FlowPane, VBox}
    val all = new FlowPane(24, 24)
    for (i <- 0 until com.gridgame.common.WorldRegistry.size) {
      val sizes = new javafx.scene.layout.HBox(12, MapPreview.view(i, 320, 180, 14), MapPreview.view(i, 104, 58, 10),
        MapPreview.view(i, 136, 76, 12))
      sizes.setAlignment(javafx.geometry.Pos.TOP_LEFT)
      all.getChildren.add(new VBox(8, Widgets.h3(com.gridgame.common.WorldRegistry.getDisplayName(i)), sizes))
    }
    val (root, _) = Widgets.page(Widgets.topBar(Seq(Widgets.brand(null)), Nil), new VBox(all), 1400)
    stage.setScene(Theme.newScene(root))
  }

  // ── What the server would have sent ────────────────────────────────────

  private var serverSeq = 5000
  private def seq(): Int = { serverSeq += 1; serverSeq }

  private val names = Seq("Aria", "Bramble", "Cinder", "Dewdrop", "Ember", "Fable", "Gale", "Hollow",
    "Ivy", "Juniper", "Kestrel", "Lumen", "Moss", "Nimbus", "Orchid", "Pebble")

  private def lobbyPacket(c: GameClient, action: Byte, who: UUID, lobbyId: Short = 7, name: String = "",
                          map: Int = 0, minutes: Int = 5, players: Int = 1, maxPlayers: Int = 8, status: Int = 0,
                          charId: Byte = 0, gameMode: Int = 0, teamSize: Int = 2): Unit =
    c.processPacket(new LobbyActionPacket(seq(), who, Packet.getCurrentTimestamp, action, lobbyId, map.toByte,
      minutes.toByte, players.toByte, maxPlayers.toByte, status.toByte, name, charId, gameMode.toByte, teamSize.toByte))

  private def lobbyList(c: GameClient): Unit = {
    val lobbies = Seq(
      ("Friday Night Brawl", 4, 5, 3, 8, 0, 0, 2), ("Chill FFA", 5, 10, 6, 8, 0, 0, 2),
      ("Teams! 3v3", 6, 5, 4, 6, 0, 1, 3), ("Snowball fight", 6, 3, 8, 8, 0, 0, 2),
      ("Ranked warmup", 1, 15, 5, 8, 1, 0, 2), ("Lagoon 2v2", 5, 5, 2, 4, 0, 1, 2))
    lobbies.zipWithIndex.foreach { case ((name, map, mins, players, max, status, mode, size), i) =>
      lobbyPacket(c, LobbyAction.LIST_ENTRY, UUID.randomUUID(), lobbyId = (10 + i).toShort, name = name, map = map,
        minutes = mins, players = players, maxPlayers = max, status = status, gameMode = mode, teamSize = size)
    }
    lobbyPacket(c, LobbyAction.LIST_END, UUID.randomUUID())
  }

  private def joinLobby(c: GameClient, host: Boolean, teams: Boolean): Unit = {
    val mode = if (teams) 1 else 0
    lobbyPacket(c, LobbyAction.JOINED, c.getLocalPlayerId, name = "Friday Night Brawl", map = 4, minutes = 5,
      players = 5, maxPlayers = if (teams) 6 else 8, gameMode = mode, teamSize = 3)
    val others = Seq(UUID.randomUUID() -> "Bramble", UUID.randomUUID() -> "Cinder", UUID.randomUUID() -> "Dewdrop")
    val bots = Seq(new UUID(0, 101) -> "Bot 101")
    val roster = (if (host) (c.getLocalPlayerId -> me) +: others else others.head +: (c.getLocalPlayerId -> me) +: others.tail) ++ bots
    roster.foreach { case (id, n) => lobbyPacket(c, LobbyAction.MEMBER, id, name = n, players = roster.size, gameMode = mode, teamSize = 3) }
    c.isLobbyHost = host
    c.selectedCharacterId = CharacterId.Wizard.id
  }

  private def chat(c: GameClient): Unit = {
    val members = c.lobbyMembers.asScala.toSeq
    def say(i: Int, text: String): Unit =
      c.processPacket(new ChatMessagePacket(seq(), members(i % members.size).id, 0, ChatScope.LOBBY, text))
    say(1, "hi all!")
    say(2, "anyone want to try the lagoon after this?")
    say(0, "sure, one more here first")
    say(3, "gl hf")
  }

  private def leaderboard(c: GameClient): Unit = {
    val rows = (names.take(14).zipWithIndex.map { case (n, i) => (n, 1720 - i * 37 - (i % 3) * 5) } :+ (me -> 1480))
      .sortBy(-_._2)
    rows.zipWithIndex.foreach { case ((n, elo), i) =>
      c.processPacket(new LeaderboardPacket(seq(), c.getLocalPlayerId, 0, LeaderboardAction.ENTRY, (i + 1).toByte,
        elo.toShort, 40 - i * 2, 90 - i * 3, n))
    }
    c.processPacket(new LeaderboardPacket(seq(), c.getLocalPlayerId, 0, LeaderboardAction.END))
  }

  private def history(c: GameClient): Unit = {
    val id = c.getLocalPlayerId
    c.processPacket(new MatchHistoryPacket(seq(), id, 0, MatchHistoryAction.STATS, totalKills = 342, totalDeaths = 208,
      matchesPlayed = 57, wins = 19, elo = 1284, oldElo = 1284))
    val now = (System.currentTimeMillis() / 1000).toInt
    val rows = Seq((2, 4, 5, 7, 2, 1, 8), (0, 5, 5, 3, 4, 3, 8), (4, 6, 5, 5, 3, 1, 6), (3, 1, 3, 2, 1, 1, 2),
      (1, 5, 10, 4, 6, 2, 8), (0, 0, 5, 1, 5, 7, 8), (2, 4, 5, 6, 1, 2, 8), (5, 4, 5, 11, 0, 1, 1))
    rows.zipWithIndex.foreach { case ((matchType, map, mins, kills, deaths, rank, total), i) =>
      c.processPacket(new MatchHistoryPacket(seq(), id, 0, MatchHistoryAction.ENTRY, matchId = 100 + i,
        mapIndex = map.toByte, duration = mins.toByte, playedAt = now - i * 5400 - 600, kills = kills.toShort,
        deaths = deaths.toShort, rank = rank.toByte, totalPlayers = total.toByte, matchType = matchType.toByte))
    }
    c.processPacket(new MatchHistoryPacket(seq(), id, 0, MatchHistoryAction.END))
  }

  private def endMatch(c: GameClient, teams: Boolean, won: Boolean, ranked: Boolean): Unit = {
    val id = c.getLocalPlayerId
    c.selectedCharacterId = CharacterId.Wizard.id
    lobbyPacket(c, LobbyAction.GAME_STARTING, id)
    c.setWorld(WorldData.createEmpty(60, 60))
    val chars = Seq(CharacterId.Gladiator, CharacterId.Assassin, CharacterId.Tidecaller, CharacterId.Wraith, CharacterId.Samurai)
    val others = names.take(5).zip(chars).map { case (n, ch) => (UUID.randomUUID(), n, ch.id) }
    def join(who: UUID, name: String, charId: Byte, team: Byte): Unit =
      c.processPacket(new PlayerJoinPacket(seq(), who, Packet.getCurrentTimestamp, new Position(5, 5), 0xFF00AA00,
        name, 100, charId, team))
    val myTeam: Byte = if (teams) 2 else 0
    val teamOf: Map[UUID, Byte] = ((id -> myTeam) +: others.zipWithIndex.map { case ((who, _, _), i) =>
      who -> (if (teams) (1 + i % 2).toByte else 0.toByte) }).toMap
    join(id, me, c.selectedCharacterId, myTeam)
    others.foreach { case (who, n, ch) => join(who, n, ch, teamOf(who)) }
    def event(e: Byte, who: UUID = new UUID(0, 0), kills: Int = 0, deaths: Int = 0, rank: Int = 0, team: Int = 0): Unit =
      c.processPacket(new GameEventPacket(seq(), who, Packet.getCurrentTimestamp, e, 7.toShort, 0, kills.toShort,
        deaths.toShort, null, rank.toByte, 0.toShort, 0.toShort, team.toByte))
    event(GameEvent.GAME_OVER)
    // Kills and deaths a match could end on (every kill somebody's death; practice's targets never
    // shoot back), ours first, placed and sent as the server places and sends them
    val lines =
      if (teams) Seq((4, 5), (7, 6), (6, 6), (5, 2), (1, 4), (3, 3))
      else if (c.isPracticeMode) Seq((9, 0), (0, 2), (0, 2), (0, 2), (0, 2), (0, 1))
      else if (won) Seq((7, 2), (9, 6), (5, 3), (5, 5), (2, 6), (1, 7))
      else Seq((6, 6), (9, 3), (7, 5), (4, 4), (2, 6), (1, 5))
    val scores = (id +: others.map(_._1)).zip(lines).map { case (who, (k, d)) => (who, k, d) }
    val results = if (teams) MatchResults.rankTeams(scores, teamOf) else MatchResults.rankFfa(scores)
    results.foreach(r => event(GameEvent.SCORE_ENTRY, r.playerId, r.kills, r.deaths, r.rank, r.teamId))
    if (ranked) c.processPacket(new MatchHistoryPacket(seq(), id, 0, MatchHistoryAction.STATS, elo = 1284, oldElo = 1261))
    event(GameEvent.SCORE_END)
  }

  // ── Drawing a screen without showing it ────────────────────────────────

  private def fx[T](body: => T): T = {
    if (Platform.isFxApplicationThread) return body
    val result = new AtomicReference[Either[Throwable, T]]()
    val done = new CountDownLatch(1)
    Platform.runLater(() => { result.set(try Right(body) catch { case t: Throwable => Left(t) }); done.countDown() })
    if (!done.await(30, TimeUnit.SECONDS)) throw new IllegalStateException("the FX thread didn't run the task")
    result.get match { case Right(v) => v; case Left(t) => throw t }
  }

  private def all(n: Node): Seq[Node] = n match {
    case sp: ScrollPane => sp +: Option(sp.getContent).toSeq.flatMap(all)
    case p: Parent => p +: p.getChildrenUnmodifiable.asScala.toSeq.flatMap(all)
    case other => Seq(other)
  }
  private def buttons(root: Node): Seq[Button] = all(root).collect { case b: Button => b }

  /**
   * Build a screen on a fresh app and client, then draw it at the window's size: `build` shows it,
   * `click` then does something on it (on its root), and `after` hands the client more of what the
   * server says. The screen's root is moved into a scene of the window's size to be drawn, so the
   * stage is never shown.
   */
  private def shoot(file: File, build: (ClientMain, Stage) => Unit, click: Parent => Unit = _ => (),
                    after: ClientMain => Unit = _ => ()): Unit = {
    val (app, scene) = fx {
      val app = new ClientMain()
      val c = new GameClient("localhost", 0, WorldData.createEmpty(60, 60), me)
      c.packetSink = _ => ()
      c.completeAuthAndJoin(UUID.randomUUID(), me)
      app.client = c
      val stage = new Stage()
      build(app, stage)
      val shown = stage.getScene
      val root = shown.getRoot
      shown.setRoot(new Group())
      val scene = new Scene(root, width.toDouble, height.toDouble)
      scene.getStylesheets.addAll(shown.getStylesheets)
      scene.setFill(Option(shown.getFill).getOrElse(Color.WHITE))
      (app, scene)
    }
    Thread.sleep(200)
    fx(click(scene.getRoot))
    fx(after(app))
    // The fade-in, and the character sheets, which load in the background. A scene with no window
    // is only laid out when it is drawn, and a scroll pane settles its content over a second
    // layout, so it is drawn once to lay it out, then again for the picture.
    Thread.sleep(800)
    fx(scene.getRoot.snapshot(null, null))
    Thread.sleep(800)
    fx(scene.getRoot.snapshot(null, null))
    // The maps' pictures, drawn on a thread of their own
    MapPreview.settle()
    Thread.sleep(400)
    fx {
      val params = new SnapshotParameters()
      params.setFill(scene.getFill)
      params.setTransform(Transform.scale(scale, scale))
      val img = scene.getRoot.snapshot(params, null)
      val w = img.getWidth.toInt; val h = img.getHeight.toInt
      val out = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB)
      val reader = img.getPixelReader
      var y = 0
      while (y < h) { var x = 0; while (x < w) { out.setRGB(x, y, reader.getArgb(x, y)); x += 1 }; y += 1 }
      javax.imageio.ImageIO.write(out, "png", file)
      app.switchScreen() // stop the screen's animations
    }
  }
}
