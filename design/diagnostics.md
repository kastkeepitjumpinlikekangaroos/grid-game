# Diagnostics and problem reports

How a player on their own computer tells us what went wrong, with enough of what their game saw to
find it: the client keeps a log on disk and records the match being played, the player marks the
moment something looks wrong (F8), and after the match a report — their words, the match's
recording, pictures of the moments they marked, the log, what the client noticed going wrong — goes
to the server, which keeps it with its own account of that match. The code is in client/diagnostics
(the log, the recording, the report), client/game (`ProblemReports`, `ReportSender`), client/net
(`PacketTap`, `UploadConnection`), ui/screens (`ReportDialog`), server (`ProblemReports`,
server/game `MatchJournal`) and tools (`ReportTool`, which reads one).

Before this, a player's client had nothing to send: its telemetry only reaches a collector on the
same machine, and the packaged apps have no console, so every message and stack trace it printed went
nowhere.

## What the player sees

- **The whole match is recorded either way**: marking is optional. A player who reports without
  having marked anything still sends every second of it, and says when it happened on the report's
  timeline, where what the game noticed is laid out to pick from (*Moments the client noticed*).
- **F8 in a match marks the moment** (`GLKeyboardHandler`; Fn+F8 on some laptops). The frame they
  were looking at is read back and kept as a picture (`MatchWindow.markMoment`, a JPEG at most 1600px
  wide, written off the thread that draws), with the fifteen seconds before it in small pictures
  (*The seconds before*), so a key pressed a moment late still catches what it was about. The
  recording notes it, and for three seconds the HUD says "Moment 2 marked: report it when the match
  ends" under the clock (`HudPainter`; the golden scene `lagoon_moment_marked`). Ten a match; past
  that the notice says so. Practice's "How it works" card and the website's controls list it.
- **The results ask for a report when there is reason to** (`ScoreboardScreen`): a gold callout over
  the buttons — "You marked 2 moments in this match. Tell us what happened?", or "Something went wrong
  in this match: the game froze for a while" when the client noticed trouble (*Incidents*) — with
  Report it. "Report a problem" is always beside Return to Lobby, and a flag in the top bar of every
  page reports anything at all, the last match with it if the player wants (`Chrome.reportButton`).
- **The report** (`ReportDialog`) is a card over the page: what kind of problem (movement, hits &
  damage, abilities, lag or disconnects, graphics, sound, crash or freeze, something else — one, or
  none), what happened in their words (2000 characters), the moments they marked with their pictures
  and match times, each kept or left out (or, with none, "about when?" and how to mark one next
  time), **when it happened** — the match as a track from start to end with the marks (gold), what
  went wrong (berry), deaths (grey) and what the game noticed (sky) on it, and a short list of the
  last, any of which, or any point on the track, a click picks ("Around 2:31: The server moved you
  3.2 cells"; report.json's `chosenMoment`) — and **What's sent**, which lists in plain words what goes
  with it and what never does.
  "Save a copy..." writes the same zip wherever they choose, to send another way; nothing waits to be
  sent after that.
- **Sent**, it says the code the server keeps it under ("GG-7K3F9QX2"), with a button to copy it,
  and the match's recording is deleted there and then (`Diagnostics.discard`): the server has it.
  **Not sent** (no connection, the server busy or refusing for now), it says why in a sentence and that
  the report is saved and goes by itself the next time they log in; a refusal that can't change (too
  big) says so instead. A report sent later says so in a line at the foot of the page.
- **A match whose window fails** (an exception drawing a frame) no longer closes the game: the
  window goes, the player leaves the match, and the lobby browser comes back with the report open and
  the error in it (`MatchWindow`'s render loop).
- **After a crash**, the first login of the next run offers the report of the run that didn't close
  properly: when it was last seen, whether it was in a match, whether Java wrote a crash file, and
  "Don't send" to let it go (*After a crash*).

Judge the look with the ui gallery's `17_scoreboard_marked` to `22_report_after_crash` (every state
of the card, made by the real screens on a client of the gallery's own).

## The log

`client.log` in the diagnostics folder: everything the client prints, to standard output and error,
each line stamped with the time and the thread (`LogFile`; `ERR` marks standard error). It is set up
first thing in `ClientMain.main` (`Diagnostics.install`), before Scala's `Console` takes
`System.out` for good, so the ~100 `println`s and stack traces of the client reach it without being
touched. Its first lines are the build (`BuildInfo.describe`), the machine and where the folder is;
the match window adds its OpenGL (vendor, renderer, version) as it opens.

A run's log is `client.log`, the one before `client.1.log` (the crash report's), the one before that
`client.2.log`. A run's file stops at 16MB — one line says it is full, the rest are counted — so a
loop printing every frame can't fill a disk. Every line is flushed as it is written, so a crash keeps
the last of them.

## The recording

From the packet that starts a match (GAME_STARTING) to its end, the client keeps everything it would
take to see the match as the player's game saw it (`MatchRecorder`, in `common/diagnostics/Recording`'s
format, which `ReportTool` reads):

| Record | What | When |
|---|---|---|
| packet in / out | the 64-byte payload, TCP or UDP | every packet, from `NetworkThread`'s handlers and `send` (`PacketTap`) |
| frame | how long since the last and how long it took, our cell and where we were drawn, where we aimed, the keys and buttons held, focus, chat, dead, full screen, the stick, charging, moving, the quality tier, the ability being aimed | every frame (`MatchWindow`) |
| input | a key, a mouse button: down or up, its code and modifiers | as GLFW reports them |
| state | the client's idea of us: health, every effect, Q/E/R cooldowns left, charge, items, kills and deaths, the clock, the last round trip, projectiles and players it knows, server moves and knockbacks seen, packets waiting | four times a second (`GameClient.sampleState`) |
| log, event, meta | each log line as it is written; what the client did or saw ("focus: lost", "quality: low", "window: 2880x1800", "full screen: on"); what it learns of the match (map, mode, our character, the match's id) | as they happen |
| mark, incident, gap | a marked moment; something that went wrong; records lost when the buffer was full | as they happen |

- **The start begins it on the network's thread.** The tap sees GAME_STARTING as it arrives and opens
  the recording there, ahead of the packets right behind it (the world, everyone's joins), which the
  packet thread would only get to after. It ends where every way out of a match passes,
  `MatchWindow.close` (finished, left, disconnected, error, closed by the server) or the app closing.
- **What is the player's own is never written.** A login's password, a session's or a login's token,
  an upload's ticket, a chat line: those packets keep their 21-byte header (type, number, player) and
  lose the rest (`MatchRecorder.isPrivate`; the record says it was cut). Keys pressed while typing in
  chat are kept without their code.
- **It allocates nothing as it records.** Every caller appends a whole record to a 1MB buffer under a
  lock; a thread of its own swaps it for its twin four times a second, compresses it and writes it
  out. 100,000
  frames, inputs, states and packets allocate nothing on the recording thread (`MatchRecorderTest`),
  as the render thread must (client/CLAUDE.md). A buffer that fills before the writer gets to it (the
  disk stalled) drops what doesn't fit and writes how many in a Gap.
- **It is on disk as it goes, compressed** (Recording's version 2): each segment one raw DEFLATE
  stream, flushed (SYNC_FLUSH) every time the writer writes, so a crash leaves the match readable up to
  its last quarter second (`MatchRecorderTest` reads a file copied mid-match), and a match's records
  take about a quarter of their size or less. Compressing is the writer thread's; the threads that
  record still allocate nothing.
- **Segments of 4MB on disk, sixteen kept**: about 256MB of a match's records, the whole of any match
  a lobby can set even at several times the measured rate. Measured over a real match (six players,
  five of them bots, a minute): 9.3KB a second of records — 43 player updates and 36 projectile
  updates in a second, and the frames. A match that outgrows them keeps its first segment (who was in
  it, the map) and its newest, the oldest of the rest deleted as it goes. A report stores the
  segments as they are (they squeeze no further).
- **Kept until it is sent, the next match or the client closing.** The last match stays
  (`Diagnostics.lastMatch`) for a report about it; a report about it reaching the server deletes it
  then (`discard`), the next match's start deletes it unless a report is being made of it then (`pin`),
  and a clean exit deletes it. It is kept past leaving the results on purpose: players often decide to
  report from the lobby a minute later. A recording the next run finds is a crash's (*After a crash*).

## Moments the client noticed

What the client can see for itself that a player who didn't mark the moment may mean, noted as it
happens (`Diagnostics.noticed`, `Recording.NoticedKind`, a Noticed record in the recording) and laid
out on the report's timeline to pick from:

| Kind | Noticed where | When |
|---|---|---|
| the server moved us (snap-back) | `LocalPlayer.handleOwnUpdate` | a server move we hadn't seen put us 1.5 cells or more from where we were, and wasn't a push or a pull (those count as knockbacks) nor our death: a correction, a teleport, a refused star |
| a shot unanswered | `PendingSpawns` (`onUnanswered`) | nothing about a projectile we asked for came within a second: lost or refused; placed where it was fired |
| a hitch | `MatchWindow` | a frame came more than 300ms after the last, but not in the three seconds after the window opens nor two after a full screen toggles or the focus moves; placed where it began |
| the server quiet | `Diagnostics.packetTap` | no packet from the server for 1.5s in a match (the heartbeat's echo alone is one a second); placed where it went quiet |
| we died | `MatchProgress` (KILL) | with who killed us: an anchor ("right before I died") |
| refused | `Attacks` (R), `Traps`, `Items` | the server refused an R (with what is left of its minute), a trap or an item |
| a ping spike | `Connection` | a heartbeat took more than 300ms to come back |

The same kind within 2s is left out (a death never is), and a match keeps 300 at most. Each allocates
only when it is noted, so the render thread's hitch check costs a comparison a frame. None of it is
shown in the match or nags the player: it waits on the report's card.

## The seconds before

A marked moment, a freeze and a failed frame carry the last fifteen seconds of what the window showed,
small (`gl/FrameHistory`): a frame every quarter second scaled to 480 pixels across and kept as a JPEG
of 20-40KB, about sixty of them, 1.5MB, held in memory and written beside the recording only when one
of them asks (`mark-N/`, `incident-N/`, each picture named by its millisecond in the recording;
`ReportFiles.markFrame`). They cover what the recording can't: what the player's own graphics card
drew (the replay draws with ours).

Nothing waits on the GPU and nothing is allocated on the thread that draws. Most frames `capture` is a
comparison. When one is due it blits the window's framebuffer into a small one and starts reading that
into a pixel buffer object without waiting, and maps the buffer it started a capture ago, long done,
and copies it into one from a pool (`nglMapBufferRange`, no buffer object made); a thread of its own
flips, encodes (stb) and keeps it, dropping what is older than fifteen seconds. If that thread is behind
a capture is skipped, never waited for. Measured with the render bench (`--history`, `--history-out=`
to look at the frames), High at 4480x2404 with sixteen players and 150 projectiles, alternated with
runs without it: frame CPU, GPU time and bytes allocated a frame moved no more than the machine's own
drift between runs; `capture` itself took 22-40us a frame on average and at most about 1ms on the
frame a capture falls on; 58 frames kept, none skipped. The freeze watchdog takes the frames from its
own thread (`Diagnostics.pastFrames`), so a frozen window still has them.

## Incidents

What the client notices going wrong (`Diagnostics.incident`, `IncidentKind`), noted in the match's
recording and its report, or this run's for a report outside a match:

- an exception nothing caught, on any thread (`Thread.setDefaultUncaughtExceptionHandler`; JavaFX
  hands its own to it), with its stack;
- the match window failing a frame (`RENDER_CRASH`);
- **a freeze**: the JavaFX thread — which draws the match and runs the menus — not answering for 4s
  (`Watchdog`: a task posted to it twice a second). Every thread's whole stack goes into the log and
  the recording, with the locks each holds and waits on and any deadlock, so "the game froze" comes
  with the reason; it is noted once a freeze, and the log says when it answered again;
- a packet that threw as it was handled (`GameClient.processAll`), the packet thread falling 8192
  packets behind, a packet that couldn't be sent, packets whose signature didn't check out, and the
  connection dropping.

The same kind again within 5s is counted, not noted, and the count goes with the next one: a stream of
bad signatures is one line, not thousands.

## After a crash

`running.properties` in the folder says a run is under way: its pid, when it started, where from, the
build, whether it is in a match, and when it was last alive (written every 10s). A clean exit (the
window closed, Cmd+Q, the system asking it to quit) takes it down in a shutdown hook. One still there
as the next run starts is a run that never closed — a JVM crash, a freeze the player had to force
quit, the power — and is offered after the first login (`RunMarker`, `PreviousRun`):

- its log, moved aside to `client.1.log`;
- the JVM's own `hs_err_pid<pid>.log`, looked for by the pid the marker kept where that run started
  from, the temporary folder and the home folder — where HotSpot writes it, whatever launched the
  game (the packaged app, a jar, Bazel), so no launch option has to say where;
- the match it was recording, if it was in one, and what was known of it (`match.json` beside it).

Sent or turned down, it isn't offered again. The packaged apps need nothing of their own for any of
this.

## What a report holds

One zip (`ReportBuilder`), at most 64MB (the server's limit; a report is far smaller):

| File | What |
|---|---|
| `report.json` | `format`, `reportId`, `kind` (match, crash, general), `createdAt`, `category`, `description`, `whenInMatch`, `client` (version, commit, dirty, built), `system` (os, java, cpus, maxHeapMb, locale, language, screens, args, launch), `graphics` (vendor, renderer, version, glsl, quality, auto, window, contentScale), `connection` (server, player, playerId), `match` (matchId, map, mode, practice, character, durationMinutes, startedAt, endedAt, ended, kills, deaths, players), `marks` (number, atMicros, matchTime, screenshot, frames), `incidents` (index, kind, atMicros, matchTime, detail, frames), `noticed` (kind, atMicros, matchTime, a, b, detail, text), `chosenMoment` (atMicros, matchTime, source, text), `previousRun` (a crash report's), `files` |
| `recording/seg-NNNN.rec` | the match's recording, compressed (left out if the player left the match out of a general report) |
| `screenshots/mark-N.jpg` | the pictures of the moments the player kept |
| `screenshots/mark-N/XXXXXXXX.jpg`, `screenshots/incident-N/XXXXXXXX.jpg` | the seconds before each, small, named by their millisecond in the recording |
| `logs/client.log`, `logs/previous-client.log` | this run's log; the crashed run's |
| `crash/hs_err_pidN.log` | the JVM's account of the crash (it lists the computer's settings, which the report card says) |

Nothing that says who the player is beyond their name on the server: no user name, home folder or
address. A time in a match is the recording's, which starts with the match.

## Sending a report

The report goes into the outbox (`diagnostics/outbox/<id>.zip`, and a note of it) before anything is
sent, so it survives whatever happens next (`ReportOutbox`). Then (`ReportSender`, on a thread of its
own):

1. **The session asks** (PROBLEM_REPORT BEGIN: the report's id, size, CRC-32, kind and the match's id,
   which the server told the client as the match began: GAME_EVENT MATCH_INFO). The server answers
   READY with a ticket, REFUSED with why and how long to wait, or ACCEPTED with the code of one it has
   already (a resend after a lost answer: kept once). No answer in 10s is an older server.
2. **The report goes down a connection of its own** (`UploadConnection`): TLS to the same server, held
   to the identity pinned for it, logged in with the ticket (AUTH_REQUEST UPLOAD), then the zip, raw,
   once the server says go; ACCEPTED with the code, or FAILED. Its own connection so megabytes never
   stand in a match's way on the session's (design/networking.md, *A problem report's upload*, for
   why it is safe).
3. **The outbox is told**: sent, with the code (the zip goes, the note stays a month), or kept, with
   when trying again is worth it. Whatever is waiting goes after the next login (`ClientMain.afterLogin`,
   `ReportSender.sendPending`), one at a time, never the same report twice at once; at most five wait,
   none longer than a month.

The server takes 6 reports an hour and 20 a day from an account, 4 uploads at once (one a player),
and 2GB in all, and keeps each in `problem_reports/<date>/<code>/` beside its account database with
`server.json` and, if it still has it, `match-journal.txt` — the server's own account of that match:
everyone in it, every request it refused with why and the numbers (a step's cells and the credit it had, a
shot's attack), corrections, deaths, errors in its ticks (server/CLAUDE.md, *Problem reports*).

## Reading a report

```bash
bazel run //src/main/scala/com/gridgame/tools:report -- problem_reports/2026-10-31/GG-C3P7PVEW
bazel run //src/main/scala/com/gridgame/tools:report -- report.zip --mark=2      # ±10s round the second mark
bazel run //src/main/scala/com/gridgame/tools:report -- report.zip --around=2:31,20
```

It writes `summary.txt` (start here: it opens with the moment the player picked), `timeline.txt`
(every record by time, packets decoded field by field, marks, incidents and noticed moments loud,
frames only when they hitch), `timeline-mark-N.txt` and `timeline-chosen.txt` (twenty seconds round
each), `noticed.csv`, `frames.csv`, `state.csv`, `packets.csv`, `net.txt` (rates, round trips, the
server's silences, frame-time percentiles), a contact sheet of each mark's and incident's seconds
before (`mark-N-frames.png`), and copies of the pictures, logs and the server's journal
(tools/CLAUDE.md, *report*). Read a mark's window beside the journal's lines at the same time: what
the player's game saw and what the server decided.

To watch any moment, marked or not, replay the match through the real client and renderer:

```bash
bazel run //src/main/scala/com/gridgame/client:report_replay -- <report folder or zip>
bazel run //src/main/scala/com/gridgame/client:report_replay -- report.zip --at=2:31 --out=frame.png
bazel run //src/main/scala/com/gridgame/client:report_replay -- report.zip --film=2:25-2:35 --fps=6 --out=film
```

It feeds the server's packets into a client that connects nowhere at the times the recording kept,
stamped on its clock (`nanoClock`) so projectiles fly as they did, and puts the player where the
recording's frames say they stood, were drawn and aimed (`GameClient.replayLocal`). What only the
player's own game knew is put in from the recording too: Q's, E's and R's cooldowns from the state
samples, a cast between two of them dated by the one after (`replayCooldowns`), and each item used, by
the USE it sent, since the server says nothing of a use it takes (`replayItemUsed`). In the window:
Space play/pause, Left/Right 5s back/on, comma/period a frame back/on (paused), [ and ] half/twice the
speed, M the next mark, N the next noticed moment or incident, Home the start, S a PNG. Headless,
`--at` writes one moment and `--film` a run of frames with a contact sheet (`--size=`, `--quality=`;
silent unless `--sound`). Limits: seeking back starts a fresh client and plays to the moment in a
burst, and the client times some effects on the wall clock, so a hold or a burn is exact at 1x and a
little long after a seek or at another speed (the match clock and the opening's countdown are put
right); the player's own charge, aiming and casts aren't replayed (where they were and aimed is);
after a gap in a long match's recording players reappear as packets resume; and it draws with this
machine's GPU, which is what *The seconds before* is for. `devtools/ReplaySession` is the part without
a window, which `ReplaySessionTest` drives.

## Which build sent it

`common/BuildInfo` reads `build_info.properties`, which `//src/main/scala/com/gridgame/common:build_info`
stamps into every binary from `scripts/workspace_status.sh` (`.bazelrc`'s
`--workspace_status_command`, on macOS and Linux): the commit, whether the tree had changes, and
when. A runtime dependency of the binaries only, so a new commit rebuilds no library. Windows has no
bash to run the script, so a build made there says "dev"; the release jars are made on the Mac.

## Where the files are

The client's, readable by the player alone (`diagnostics/` in their settings folder:
Application Support on macOS, `%APPDATA%` on Windows, `~/.config/grid-game` elsewhere;
`GRIDGAME_DIAGNOSTICS_DIR` anywhere else): the logs, `running.properties`, `match/<start>/` (a
recording, its `match.json`, its pictures), `outbox/`. A second client on the machine finds the
folder locked and keeps its own under `instances/<pid>/`, which the first deletes once that client
has gone. Dev tools and tests never install it, so they write nothing there.

## Tests

`MatchRecorderTest`, `LogFileTest`, `DiagnosticsTest`, `ReportBuilderTest` (client/diagnostics),
`UploadConnectionTest` (client/net, against a TLS server of the test's own), `GameClientReportsTest`
(the session's part, and each moment the client notices), `ReportDialogTest` (the card, its timeline,
a sent report letting its match go), `ReplaySessionTest`, `RecordingTest` (common/diagnostics: plain
and compressed segments, cut short anywhere), the packet in `PacketRoundTripTest` and
`PacketFuzzTest`, `ProblemReportsTest` and `MatchJournalTest` (server), `ReportToolTest` (tools). The
render bench's `--history` measures the seconds before (it needs a window, so no test does).
