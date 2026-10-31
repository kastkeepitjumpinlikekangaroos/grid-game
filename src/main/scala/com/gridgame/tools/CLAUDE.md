# tools

Dev tools, not shipped: the generated text kept in step with the roster, and the reader of the
problem reports players send (`report`, below).

- `gendocs` (`GenDocsRoster`, on `DocsRoster`) — rewrites each character card's role and health
  pill in `docs/index.html` from the roster: `bazel run //src/main/scala/com/gridgame/tools:gendocs`.
  `DocsRosterTest` fails until it has been run after a change to a role or a health.
- `gencontent` (`GenContentCatalog`) — regenerates the English game-content entries of
  `i18n/messages_en.json` (every character's and ability's name and description):
  `bazel run //src/main/scala/com/gridgame/tools:gencontent 2>/dev/null`. The catalog wins over the
  code (`I18n.tOr`), so renaming an ability in the roster alone leaves the old name on every screen;
  `ContentCatalogTest` catches it.

## `report`: reading a player's problem report

`ReportTool` and its `Report*.scala` files read a problem report (design/diagnostics.md) for whoever
has to fix what it is about. It depends on `common` alone: the recording's format is
`common/diagnostics/Recording.scala`, and its packets are read with `PacketSerializer`.

```bash
bazel run //src/main/scala/com/gridgame/tools:report -- problem_reports/2026-10-31/GG-7K3F9QX2
bazel run //src/main/scala/com/gridgame/tools:report -- ~/Desktop/grid-game-report.zip     # a saved copy
bazel run //src/main/scala/com/gridgame/tools:report -- <report> --mark=2          # timeline.txt: 10s either side of mark 2
bazel run //src/main/scala/com/gridgame/tools:report -- <report> --around=2:31,5   # timeline.txt: 2:26 to 2:36
bazel run //src/main/scala/com/gridgame/tools:report -- <report> --out=/tmp/read   # elsewhere than <report>-read
```

It reads the server's folder for a report (`report.zip`, `server.json`, `match-journal.txt`), the zip on
its own (what "Save a copy" gives a player), or a folder the zip was unpacked into; paths are the
caller's (`BUILD_WORKING_DIRECTORY`). It writes `<report>-read/` beside it (a zip's name without
`.zip`), prints the summary, and says where it wrote:

| File | What it is for |
|---|---|
| `summary.txt` | the report in human terms: who sent it, what kind and category, and first of all **when the player picked it happened** (report.json's `chosenMoment`: from a mark, a moment the client noticed, an incident, or a click on the match's timeline); what they said; the client's build, the machine, the graphics, the connection; the match and its players; the marks (each with its match time, screenshot, window file, and the pictures before it), what went wrong; **what the client noticed** for itself (snap-backs, shots the server never answered, hitches, silences, deaths, refusals, ping spikes, with counts by kind; the recording's, then any report.json lists that a missing segment lost); the pictures before marks and incidents; the run before a crash; the server's note; the files; what the recording holds (each segment's version, its size compressed and uncompressed, whether it was cut short; records by kind, missing segments, records lost, kinds skipped). Every field of report.json is optional, and one the tool doesn't know is still shown, under its own name |
| `timeline.txt` | every record in time order, a line each, `mm:ss.mmm` first: packets field by field (actions and events by name, players as `a1b2c3d4/Bob`, us `(us)`, named from report.json and each PLAYER_JOIN), keys and buttons, `>>> MARK n` (and its pictures' sheet), `!!! FREEZE` and the other incidents, `~~~ NOTICED kind: what` (a silence with where it began: its record is at its end), `*** CHOSEN` where the player said it happened, log lines, events; frames only when one was a hitch (over 50ms since the last, or over 33ms in the making) and states only when something in them changed. `--mark`/`--around` narrow it to a window, whose first state is given whole |
| `timeline-mark-N.txt`, `timeline-chosen.txt` | ten seconds either side of each mark, always, and of the moment the player picked, when they did |
| `frames.csv`, `state.csv`, `packets.csv`, `noticed.csv` | the recording as tables, a row a record, time in seconds: for a spreadsheet or a plot |
| `mark-N-frames.png`, `incident-I-frames.png` | the pictures kept before a mark or an incident (a few a second for the last fifteen or so: report.json's `frames`, and any `screenshots/mark-N/XXXXXXXX.jpg` in the zip it doesn't list), tiled in time order, each labelled with its match time and how long before the mark it was; a picture that won't read is left out and named in the summary |
| `net.txt` | packets a second by type each way (average and busiest second), heartbeat round trips (a HEARTBEAT sent to its TCP echo), the longest the server went quiet on TCP, UDP and either, frame-time percentiles and the worst frames |
| the rest | the screenshots, logs, crash files and report.json as the zip had them, and server.json and match-journal.txt |

**How to look at one:** summary.txt first: what they said, and when, which they may have picked from
what the client noticed (timeline-chosen.txt is that moment). Then each mark's window beside its
contact sheet: a mark is pressed a moment after what it is about, and the sheet is the seconds before
it, as the player saw them. Then the incidents' moments (`--around`) and the noticed list (a snap-back
or a silence near where they point is a strong lead), and for "lag" net.txt and the hitches. The
server's side of the same moments is in match-journal.txt (what it refused, and why), on the match's
clock, which starts as the recording does (GAME_STARTING) give or take a trip down the wire.

What the tool takes care of:

- **Missing segments.** The client keeps a long match's first segment and its newest ones, so the
  numbers can skip (0, 3, 4...): the summary and the timeline say which and what stretch went, and
  net.txt counts neither a silence nor a round trip across one, nor its time in a rate.
- **Compressed segments.** The client writes version 2 segments, its records one DEFLATE stream that a
  segment it closed ends with the stream's own end mark; `Recording.SegmentReader` reads them as it
  reads version 1, and `ReportRecording.streamEnd` reads the stream to its end to say what it
  uncompresses to and whether it was closed.
- **Records cut short** (the client stopped mid-write: a crash) end their segment, and every whole one
  before is read: a version 1 segment says how many bytes were left over, a version 2 one that it has
  no end mark (and whether a record in it was cut in two, or the stream broke off into something
  else); **unknown kinds** (a newer client) are counted and skipped; a packet that won't decode is
  shown as its bytes; a redacted one (`Recording.FlagRedacted`: a chat line, a token) as its header
  alone.
- **A report is a player's machine's say-so.** A name in the zip is never a path (`../evil.txt` is
  written nowhere: `ReportFiles.safeRelative`), control characters (a terminal's escape codes among
  them) come out as `·`, and nothing is read past 256MB a file or 1GB a zip.

A new record kind goes in `Recording.scala`, then `ReportRecording.KnownKinds`, `ReportTimeline` and,
if it is a table's, `ReportTables`; a new packet type in `ReportPackets.fields` (until it is, its
line is the packet's own `toString`). `ReportToolTest` reads a synthetic report with every kind of
record, two version 1 segments with the ones between them missing, a record cut short, a kind it
doesn't know, a redacted packet, one that won't decode and a name that climbs out of the folder; and
one as the client writes them now: compressed segments (one closed, one cut partway through a batch),
every kind of noticed moment, a chosen moment, and pictures before a mark and an incident (one of
them not a picture), checked down to the contact sheets' size for the pictures that could be read.

## Website (GitHub Pages)

Static landing page served from the `docs/` directory on `main` via GitHub Pages.

### Files
- `docs/index.html` — Single-page site (hero, features, characters, controls, download). Each
  character card's pill ("Melee &middot; 145 HP") is written from the roster by
  `bazel run //src/main/scala/com/gridgame/tools:gendocs` (`tools/DocsRoster`). Run it after any
  change to a role or a health; `DocsRosterTest` fails until you do.
- `docs/style.css` — Dark theme stylesheet
- `docs/script.js` — Smooth scroll for nav anchors
- `docs/BUILD.bazel` — Bazel filegroup target

### Deployment
GitHub Pages is configured to deploy from `main` branch, `/docs` directory. Any push to `main` that modifies `docs/` will auto-deploy.

### Creating Releases (Deploy JARs)
Bazel's `scala_binary` auto-supports `_deploy.jar` suffix targets, producing fat JARs with all dependencies bundled. No BUILD file changes needed.

```bash
# Build fat JARs (client is the build machine's: made on the Mac, it is the macOS one)
bazel build //src/main/scala/com/gridgame/client:client_deploy.jar
bazel build //src/main/scala/com/gridgame/client:client_windows_deploy.jar
bazel build //src/main/scala/com/gridgame/client:client_linux_deploy.jar
bazel build //src/main/scala/com/gridgame/client:client_linux_arm64_deploy.jar

# The Linux apps with their own runtime (no Java to install), and the .debs, which are how a
# Chromebook installs the game (client/CLAUDE.md, *Packaging the apps*; Docker off Linux)
scripts/build_linux_package.sh --arch x86_64 --deb
scripts/build_linux_package.sh --arch arm64 --deb

# Create a GitHub release with them all (each .deb's name carries the minute it was built)
DEB_X64=$(ls dist/linux/grid-game_*_amd64.deb); DEB_ARM64=$(ls dist/linux/grid-game_*_arm64.deb)
gh release create v1.0.0 \
  bazel-bin/src/main/scala/com/gridgame/client/client_deploy.jar#"Grid Game (macOS)" \
  bazel-bin/src/main/scala/com/gridgame/client/client_windows_deploy.jar#"Grid Game (Windows)" \
  bazel-bin/src/main/scala/com/gridgame/client/client_linux_deploy.jar#"Grid Game (Linux x86_64, jar)" \
  bazel-bin/src/main/scala/com/gridgame/client/client_linux_arm64_deploy.jar#"Grid Game (Linux arm64, jar)" \
  "$DEB_X64#Grid Game (Linux and Chromebook x86_64, .deb)" \
  "$DEB_ARM64#Grid Game (Linux and Chromebook arm64, .deb)" \
  dist/linux/grid-game-linux-x86_64.tar.gz#"Grid Game (Linux x86_64)" \
  dist/linux/grid-game-linux-arm64.tar.gz#"Grid Game (Linux arm64)"
```
