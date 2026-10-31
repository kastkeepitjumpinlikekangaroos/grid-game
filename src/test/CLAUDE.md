# Testing

```bash
bazel test //src/test/...                                        # everything
bazel test //src/test/scala/com/gridgame/server:combat_test      # one target
```

Each test file is its own `scala_junit_test` target, so each runs in a JVM of its own: a new test
file is a line in the list in its directory's BUILD.bazel (its target name, its file, and any
`data` it reads). The suites mirror the source tree:

- `common/` — the model, the wire protocol (every packet round-trips in `PacketRoundTripTest`; a
  server's identity is written as SSH writes a host key's in `ServerIdentityTest`),
  telemetry looking for the collector once at startup (`CollectorProbeTest`, and
  `TelemetryWithoutCollectorTest` / `TelemetryWithCollectorTest`, a JVM each since `init` takes
  effect once) and its exporters giving up for good on one that goes away (`ExportGiveUpGateTest`),
  the roster's data (`CharacterRosterTest`, every sprite sheet within its tile too), and the maps
  (`WorldMapsTest`: spawns on open ground, all open ground one region). `ProjectileDefRegistryTest`
  must be the first thing in its JVM to look a projectile up, which is why it is a target, and a
  single test, of its own.
- `server/` — matches driven by hand through `ServerTestKit.scala`: `TestMatch` builds a
  `GameInstance` that is begun but never started (`GameInstance.begin`), so the test calls
  `tickProjectiles` / `tickPlayers` / `respawn` itself; every player gets an `EmbeddedChannel` for
  TCP and a UDP address on a channel attached with `Outbox.attachUdpChannel`, and
  `tcpSent` / `udpSent` decode what each was sent. A `TestMatch` is a match already under way, so
  a Teams one has no opening divider over it unless the test asks (`opening = true`).
  `LobbyFlowTest` and `SessionTest` go in through `GameServer.handleIncomingPacket` and the real
  TCP handler (signed packets) instead. `TestSession` is a logged-in client on that real handler
  (a session token, the address it logged in from, signed and numbered packets, and what it was
  sent), and `TestMatch.seat` puts a few of them in a match through an in-game lobby.
- The network: `NetworkHandlerTest` feeds the TCP and UDP handlers what a broken or hostile client
  could send (another player's name, the wrong key, garbage, forged datagrams, replays, a connection
  that never logs in, on the channel's own clock: `EmbeddedChannel.advanceTimeBy`);
  `SessionTest` renews a session an hour in (its token aged by hand, then `GameServer.renewSessions`)
  and reads what the server wrote still signed, to see which token signed it, and keeps a player
  connected on anything they send, not only their heartbeat (`GameServer.cleanup(now)`);
  `RateLimiterTest`; `ReconnectTest` for rejoins, logins over a live connection and disconnects;
  `RefreshTokensTest` for refresh tokens in a database of its own (exchanged once, a copy that comes
  back ending its family, a lost reply's retry, a month from the password, hashes only) and
  `StayLoggedInTest` for staying logged in through the real TCP handler, each test's connections
  from addresses of their own (`127.0.x.y`) so the failed logins one counts don't hold up another;
  `TlsProviderTest` for the server's identity (made once, kept, the same at every start);
  `ChatTest` for who hears what; `RankedQueueTest` for matchmaking, run by hand on a clock of the
  test's own (`RankedQueue.checkQueue(now)`), so a minute's wait takes no time; and
  `SpawnRequestTest` for what the server takes from a spawn request (heading, charge, who may
  fire). On the client, `ClientNetworkTest` feeds its handlers through an `EmbeddedChannel`,
  `GameClientLoginTest` is a refresh login and a logout as the client sends and hears them,
  `LoginStoreTest` and `KnownServersTest` (client/net) are "Remember me"'s file and the servers met,
  in a folder of the test's own, `ServerPinningTest` holds real TLS handshakes over loopback to the
  key met first (keys made by keytool; a refused server gets no byte of the login, however eager
  the client), and `ClientLoginFlowTest` is the app logging in and staying logged in, on clients
  that connect nowhere (`ClientMain.newGameClient` and `retryWaitMs` are its seams).
- Projectiles in flight: `ProjectileTicksTest` (server) pins the tick every projectile packet
  carries. `ProjectileFlightTest` (client) sends shots as the server flies and stamps them, over a
  network with latency, jitter and loss, and draws frames at 60 fps on the test's own clock
  (`TestClient.nanos`, which projectile packets are stamped with as they arrive): every frame's
  step, a late packet changing nothing, the stops (a wall's face, the end of range, the divider, a
  barrier — and none at water's edge), and the fade of a shot whose end was lost.
- `client/render`, `client/gl` — the camera (`CameraTest`: the isometric mapping, the pixel grid,
  whether the view has run off the map), the light pool (`LightPoolTest`), and how other players
  are drawn (`RemoteMotionTest`: an even pace under jitter, never past where they stopped or a dash
  ended, corners turned where they were heard turning, jumps drawn at once, knockbacks slid), the
  depth pass's order (`DepthOrderTest`: us sorted where we are drawn, not on the cell we stand on;
  a diagonal's entities back to front; every wall a sprite overlaps on the right side of it all
  along every step; the diagonal walk visiting exactly the cells on screen), and
  where a charged shot's aim lane stops (`AimLaneTest`: where the shot would, at a wall's face or the
  map's edge, over water but not lava, at only a fence for a shot that flies over walls), where our
  aim lies as we walk (`AimGeometryTest`, walking a step every three frames at 60 fps with the camera
  following as the renderer's does and the pointer still: the aim from where we are drawn toward the
  pointer, not turning as we step, where from our cell it swung degrees; a landing moving exactly
  with us on open ground and on the cast's own cell standing, what a wall takes off it shown; a
  controller's stick keeping its heading; and `AimGeometry.openLanding` the dash's, blink's and
  throw's own answer aimed every way at every distance), and the
  mouse's pointer (`CursorTest`: its picture whole at every pixel scale, the dot on the hotspot, put
  down on whole pixels, and only over the window's content). `TileTest`, in
  `common/model`, also holds the tileset to what the renderer assumes: a flat tile is exactly
  its diamond, a block covers its whole footprint. `BlastStyleTest` holds the blasts to the roster:
  every attack that blasts has a look of its own, and no two characters share one.
  `ProjectileLookTest` does the same for projectiles drawn as their thrower's: every attack in the
  roster is drawn, everyone who threw the old crescent has a look no one else has, and a type's own
  look (for a thrower nobody knows) is one of its throwers'; the fifty-one attacks that were orbs,
  rings and blobs each have a look and a light colour of their own (`glowOf`), no one who shares
  their type draws it the same way, and the tongue and the bandage are tied to their throwers; every
  pull to the thrower is tied to them (but the gravity ball and the scythe's swing), and a pull or a
  strike several characters share (the tentacle, the grab, the vine, the rope, the talon, the claws)
  is drawn as each one's own. `ProjectileThrowerTest`, in `client/`,
  is the other end: a projectile the server tells us of knows who threw it.
- `client/` — `ClientTestKit.scala`: `TestClient` is a `GameClient` whose packets go to a
  capture (`GameClient.packetSink`) and which is handed the server's with `processPacket`.
  The screen tests (`LobbyRoomScreenTest`, `ScoreboardScreenTest`, `CharacterSelectionPanelTest`,
  `WelcomeScreenTest`) build the real JavaFX screens, never shown, on the FX thread via `Fx { ... }`,
  and click them. They set `GRIDGAME_AUDIO=off`, and they must not call `Messages.setLocale`, which
  would save a language to the player's own settings, nor log in through the app, which would keep
  a login in the player's own "Remember me" file: `WelcomeScreenTest`'s app only notes down what the
  screen asks of it. `GameClientBlastTest` is what the client records
  of a blast: one per explosion or splash (a slam is one, not an explosion and a splash; the HITs of
  everyone a splash caught are the same one), who threw it, and none where the terrain stopped a
  splash that goes off only at the end of its range. `GameClientSpawnRequestsTest` is which of our
  spawn requests the server answered (`PendingSpawns`): by the first packet about the projectile,
  whatever it says, for the attack that asked for its type, and none counted as lost for dying.
  `ui/DropDownsTest` is a click on a closed drop-down opening it after its list closed by itself
  under the mouse, and a click on an open one still closing it. `ui/MapArtTest` is the maps drawn
  for the menus: where a map's matches are played (what its spawns can walk to), the whole map
  drawn and nothing off it, the base under its near edges only, a window drawing its own cells.
  `GameClientCombatTest` is what the client keeps of a projectile the server ends: a shot gone at its
  hit, one stopped by the terrain fading where it struck, and a pull kept to be reeled in, holding
  whoever it caught. `GameClientKnockbackTest` is how a knockback is shown: ours slid on the test's clock
  (`TestClient.nanos`), everyone else's neither a teleport nor a turn. `GameClientAimTest` is
  casting mode (design/characters.md, *Casting mode*): Q or E taken up by its key and cast by the
  next click toward the aim of that moment, which fires no primary as it comes up; nothing taken up
  on its cooldown; a click that can't cast it (a ceasefire, a root under a dash, a trap with nowhere
  to land) spending nothing and leaving it aimed; the other key taking over; every slam, a phase and
  a barrier going off as their key goes down; the right button, the chat and the focus putting it
  away; click, Q, click, E, click casting all three; the primary held and fired as its button comes
  up, charges and all; and what the renderer is shown of an aimed dash, blink and throw is where the
  cast then puts us or the trap. It drives the real `GLKeyboardHandler` and `GLMouseHandler` too,
  headless. `SlamReachTest` (server) holds every slam in the roster to what the model says it
  reaches: an enemy whose cell is within `AbilityDef.slamRadius` is hit, and nobody past it.
  `GameClientResetTest` is R (design/characters.md, *R resets the cooldowns*): both abilities ready
  from the press and the server asked once; nothing done or spent with nothing cooling down, nor by
  the held, the phased, the dead or a ceasefire; what is aimed left aimed; R's minute from the
  match's start (but none in practice, and a match after practice has it again), kept through a
  death, begun again by a new match; one barrier at a time; and a refusal putting Q and E back but
  for a cast the server would have taken anyway, R back when the server says or in a moment.
  `CooldownResetTest` (server) is the other end: every clock a cast is held to cleared (a shot's, a
  trap's, a phase's, a barrier's), R's own minute from the match's start (practice's taken at once)
  and after each, 80% of it taken, kept through a death and a rejoin, refused to the held,
  the phased (but in a phase's last moments), the dead and a ceasefire and said so with what is left,
  and a bot pressing it once both its abilities are spent in a fight.
  `ContentCatalogTest` ties `i18n/messages_en.json`
  to `CharacterDef`: the catalog wins over the code (`I18n.tOr`), so renaming an ability in
  `CharacterDef` alone leaves the old name on every screen, silently. Regenerate the entries
  with `bazel run //src/main/scala/com/gridgame/tools:gencontent 2>/dev/null`.
- `client/audio` — `AbilitySoundsTest` (every attack, and every explosive that deals its blast,
  sounds from a file that exists; a fire, a mud bomb and a flask don't go off as the frag) and
  `VoicePoolTest` (what a fan's burst of one sound does in the mixer). The sounds themselves are
  judged with `scripts/sound_audit.py --check` and the gallery (see *Sound Effects & Music*).
- The kits: `MeleeKitTest` (common) holds the melee and skirmisher kits to crowd control, a way
  in on a runner or a place on the list of anchors, and primaries that never hold;
  `MeleeKitsTest` (server) lands each new effect and combination, and walks a runner away from
  every closer. `KitTest` (common) holds every kit to no volley of more than three, a volley only
  on the characters listed with why, no two kits alike, one phase or dash a kit and which slams
  haste their casters; `KitReworkTest` (server) lands each of the volleys' replacements in a match
  (slams, splashes, shots through and off walls, the hastes and how long they are told to last,
  the two new traps, the ice wall), and `BotMovementTest` the bots' getaways and hastes.
- The opening of a match: `TeamDividerTest` in `common/model` (the halves, and what the world
  itself then refuses — a step, a blink, a star, a trap throw) and in `server` (spawns, shots,
  blasts and steps against the wall, and the two announcements); `CeasefireTest` for a
  free-for-all's opening, one case per attack and cast behaviour, players and bots alike;
  `GameClientOpeningTest` for the client's copy of either; and `LobbyFlowTest` for a real Teams
  match starting with each team in its own half.
- `tools/` — `DocsRosterTest` does the same for the website: every character card's role and
  health must be what the roster says (see *Website*). `ReportToolTest` reads a problem report made
  for it (segments 0 and 3, every kind of record, a record cut short, a redacted packet) into its
  summary, timeline and tables, its windows round a mark, and a note of the segments left out.
- Problem reports (design/diagnostics.md). `RecordingTest` (common/diagnostics) is a recording's
  segment read back as written, and what is kept of one cut short. In `client/diagnostics`, each with
  a `Diagnostics` of its own in a folder of the test's own (`install(Nil, Some(dir), teeConsole =
  false)`, `resetForTest` after): `MatchRecorderTest` (every kind of record back as written, nothing
  of the player's own on disk, a long match's first segment and its newest kept, a full buffer's Gap,
  and 100,000 frames recorded allocating nothing beyond what an empty loop does, measured with
  `ThreadMXBean.getThreadAllocatedBytes` once the measuring itself is warm), `LogFileTest`,
  `DiagnosticsTest` (the run that never closed found with its log, crash file and match; a match
  recorded from GAME_STARTING to its end; the last kept until the next unless pinned; the same trouble
  counted; ten marks; a second client's folder) and `ReportBuilderTest` (the zip and report.json for a
  match and a crash; the outbox). `UploadConnectionTest` (client/net) sends a report to a TLS server
  of the test's own that speaks the upload's side, keys made by keytool: the ticket first, the bytes
  only after the go-ahead, and nothing at all to a server that isn't the one pinned.
  `GameClientReportsTest` is the session's part (BEGIN, the answer, the match named, the snapshot,
  the outbox told), with the server's answers handed straight back through `packetSink`;
  `ReportDialogTest` the report's card over a real page (what it asks and shows, sending, sent, not
  sent, no connection, cancelled, a moment the game noticed picked and going with it, a sent report
  letting its match go) and the scoreboard asking for one; `GameClientReportsTest` also has each moment
  the client notices for itself (a server move that wasn't a push, our death, a shot unanswered, a
  refused R, a slow heartbeat). `ReplaySessionTest` (in `client/`, on the devtools' `replay` library)
  plays a synthetic report into a client and seeks in it. `ProblemReportsTest` (server)
  takes reports through the real TCP handler, an upload over real TLS among them, and
  `MatchJournalTest` is what the server keeps of a match for one; `TestSession` takes a `server` of
  the test's own for them.
- `mapeditor/` — `MapEditorTest`: what the editor saves is the map it has, read back by the game's
  own `WorldLoader` (every map in worlds/, maps edited at random with every tool, a new map); undo
  and redo walk its edits back and forth exactly, and keep the last fifty; a flood fill takes exactly
  its region, a rectangle and a brush their cells, a circle its round, and nothing is drawn off the map.
- `e2e/` — the whole game at once: real clients against a real server (*End to end*, below). It
  mirrors no one directory, since it is both ends.

## Fuzz and property suites

Seeded random runs, with the rules checked after every step. A failure names its seed and step
and lists the actions that led to it; each run prints a tally of what it got up to (shots taken,
deaths, matches started, packets from inside a match), so a run whose every request was refused
shows as one. A server fuzz run throws stdout away and keeps stderr (`ServerTestKit.quietly`,
`captureStderr`; Scala's `println` goes by `Console.out`, not `System.out`, so both are swapped):
the server says something about nearly everything it is sent, and stderr is where it reports what
it caught and carried on from (a tick's error, a bot's), which fails the run.

- `MatchFuzzTest` (server): a `TestMatch` of six players, and two bots in some runs, on cluttered
  ground: thousands of requests — steps, jumps, shots from every attack (some from nowhere, of the
  wrong type, or with no heading), traps, items, R, rejoins, leaving — between its ticks, respawns and
  time passing (cooldowns cleared and effects worn off by hand: every rule runs on the wall clock),
  in a free-for-all, Teams, both openings and practice. Nothing throws; health stays in bounds;
  everyone stays on the map; nobody is moved onto a cell they can't stand on unless dashing; the
  dead and the held aren't moved by their own client; an item is on the ground or in one inventory,
  ten at most; thirty shots in flight at most, every one taken leaving from within reach of its
  shooter; a trap to a cell; every death scored once. It found the NaN spawn on its own.
- `HostileClientFuzzTest` (server): logged-in `TestSession`s sending every packet type with its
  fields at random, mostly straight into `GameServer.handleIncomingPacket` (whatever throws reaches
  the test) and the rest signed through the real TCP and UDP handlers; lobbies, practice and ranked
  matches start along the way and run on their own threads. The lobbies hang together after every
  packet: one lobby or none, which counts them; a waiting lobby keeps its host and no more seats
  taken than it has; nobody queued for ranked from inside one; every match a lobby in a game's.
- `BotMatchSoakTest` (server): a real match of eight bots on every map at once, free-for-all and
  Teams, started by `MatchLauncher` with the opening skipped, sampled every 25ms for eight seconds,
  then ended and scored through `MatchEnd`.
- `ClientPacketFuzzTest` (client): server packets at random into a `TestClient` in a match, while
  its player acts (abilities taken up, clicked off and put away among it, the primary held and let
  go, and what the renderer reads of where an aimed one would go) and frames are flown, a new match
  starting whenever one ends. Nothing it hears may throw (`GameClient.processAll` loses only the
  packet that does).
- `PacketFuzzTest` (common/protocol): any 64 bytes are read as a packet or refused as malformed,
  and a packet read is the same written and read again; signatures over random and tampered bytes.
- `ReplayGuardModelTest` (server): long streams of packet numbers (late, twice, lost, gaps, across
  the 31-bit wrap) against a model of what the window promises.
- `PlacementAgreementTest` (common/model): what the client picks for a trap, a blink or a shove,
  the server takes — on cluttered ground and every map, from where the client is and a step behind.
- `PlayerGridTest` (server, in package `server.game` for `PlayerGrid`): the projectile tick's grid
  shows a shot everyone the roster's furthest reach covers, and a blast everyone in its radius.

To extend one, give its run another action and say what rule it must keep; keep a target under
about ten seconds.

**Effects nothing in the roster has yet** (a stun, a poison, a slam that pushes or pulls) are
pinned with `ProjectileDef`s registered by the test itself, on ids the game doesn't use — see
`TestEffects` at the foot of `OnHitEffectsTest`. Each test file gets its own JVM, so a test-only
registration can't leak into another suite. Bots are walked on a clock of the test's own
(`BotController.tick(now)`), so `BotMovementTest` covers ten seconds of their pace in no time, and
`BotPathingTest` walks them round walls on every map (the maps are its `data`), never past a wall's
corner, in a free-for-all's opening so they hold their fire. `TrapDef.register` is the same door for a trap the
roster hasn't got — a poison short enough for a test to sit through (`TestTraps` in `TrapTest`).
A character the roster doesn't have (one quicker
than any in it) goes through `PacketValidator`'s `characterOf` parameter instead — see
`PacketValidatorTest.aFasterCharacterIsJudgedByItsOwnPace`.

## End to end

`e2e/` plays the game with both ends real: `GameServer` and `GameClient`s, each end on its own
network handlers — the server's `TcpHandler` and `UdpHandler` (signatures, replay windows, rate
limits, who a packet may name) and the client's `ClientTcpHandler` and `ClientUdpHandler` (a session
token taken up as it arrives) — with only the sockets swapped for a network of the test's own. Every
other suite is one end, with the other end's packets made by hand; this is where the two have to
agree.

- **`Loopback`** is that harness. `Loopback(seed, net, byHand) { loop => ... }` keeps what everything
  prints off the test's output and reads stderr after every step. `NetConditions` is the network:
  `Perfect`, or `Lossy` (latency, jitter that reorders datagrams, a tenth of them lost, a few twice;
  TCP is never lost and keeps its order). `LoopClient` is one player's client with the app's glue
  (ClientMain's, MatchWindow's: the world loaded when the server names it, the answer to a login, the
  lobby closing, Esc twice), driven through the real `GLKeyboardHandler` and the calls
  `GLMouseHandler` makes; `signUp` goes through `AuthService` (bcrypt), `Loopback.logIn` logs any
  number in at once without the hash (`E2EServer.logInWithoutPassword`, as `AuthService.startSession`
  does). `ServerSide.scala` and `ClientSide.scala` are the two packages' insides it needs
  (`E2EServer` in `server.game`, `E2EClient` in `client.game`).
- **`byHand`** (the default): the matches the lobbies launch are begun, not started
  (`MatchLauncher.startMatch`, `startBots`), and the harness runs their clocks — the projectile
  tick every 30ms, the player tick every 200ms, the bots' every 100ms, respawns, item batches, the
  cleanup loop — on the test's thread, between the network's deliveries. `openingMs`,
  `itemEveryMs` and `respawnDelayMs` shorten what a test can't sit through. Without it the matches
  run on their own executors, as the real server's do.
- **Strict** (byHand on a perfect network): every packet is delivered at once (TCP's and UDP's each in
  order, interleaved at random, as two sockets are), so a client acts knowing everything the server
  has said. Then every request an honest client makes must be taken (`Judge`: each step, a dash's
  and a blink's, a phase and a barrier going up, each shot of every attack, a trap, an item, R), and
  after every step every client must agree with the server about its whole match: where everyone is,
  their health, who is dead, characters and teams, the items on the ground and its own inventory, the
  traps down, its own kills and deaths (`checkAgreement`). On any network nothing may throw on either
  end, nothing may be printed to stderr, the server's own rules hold (health, the map, walkable
  ground, items, traps) and every client's (`checkServer`, `checkClient`). A failure names the seed
  and prints the last of the trace, and the client concerned's own last packets.
- **`Persona`** is an honest player at the keyboard: it walks (mostly at the nearest foe), aims,
  fires and charges, takes Q and E up and casts them with a click or puts them away, bursts, uses
  items, presses R, chats, and with `leaves` leaves. `FuzzRun` seats personas at three tables and
  plays rounds: each table a match of its own every round (a free-for-all, Teams, either with bots,
  or practice) on the maps of the rotation, with characters further round the roster each round, and
  back to the lobby browser between them; on a lossy network it checks what came by TCP once that
  has quietened (`tcpQuiet`: who is dead, the scores, the items, the traps), and the standings at the
  end.

The suites:

- `MatchLifecycleTest` — sign-up to the scoreboard and back: a free-for-all (its ceasefire heard with
  the start), its results saved as the profile shows them, a second match with nothing carried over;
  a Teams match with its wall up on both ends, each team in its half, played at the wall until it
  comes down, scored by team.
- `SessionLifecycleTest` — ranked: a duel found by the matchmaker, played as the characters queued,
  rated, and the rating on each client's scoreboard; leaving a match (Esc twice) and losing the
  connection mid-match, the match going on and still scoring them, and logging back in with the
  password into the lobby browser.
- `EndToEndFuzzTest` (strict), `LossyNetworkFuzzTest` and `AwfulNetworkFuzzTest` — `FuzzRun`. The
  last two play over bad networks (`NetConditions.Lossy` and `Awful`) and fail on anything the validator refuses an
  honest player, and on any position (theirs, every other human's) not mended within six seconds of
  hands coming off (design/networking.md, *Bad networks*). A failure names its seed:
  `--test_env=GRIDGAME_E2E_SEED=<seed>` plays it again, and `--test_env=GRIDGAME_E2E_SOAK_SECONDS=600`
  keeps playing new seeds for ten minutes. The rules run on the wall clock, so a seed plays the same
  hands but not always quite the same match.
- `ThreadedMatchFuzzTest` — `FuzzRun` against matches on executors of their own, as the real server
  runs them (not `byHand`): ticks racing the players' requests, the TCP checks looked at again
  until a match caught mid-change has settled, the standings read once it has stopped.
- `RosterSweepTest` (strict) — the whole roster at once, a match on every map: every character's
  primary (charged if it charges), burst, Q and E, cast as a player casts them, each one taken by the
  server. A character whose two ends disagree about an attack fails here by name.
- `RealNetworkTest` — what the loopback leaves out: a server listening on loopback (TLS, length
  framing, UDP on the same port, its matches on their own clocks) and clients connecting as the app
  does (`NetworkThread`, the identity pinned, heartbeats). Two sign up, meet, see each other walk and
  fire, and see the standings.

What it has found, each fixed and pinned by a test of its own: the opening was announced at the
match's first tick, so a shot or a cast in its first 30ms was refused and its cooldown spent
(`LobbyFlowTest.theOpeningIsAnnouncedBefore…`); a blink that reached one diagonal cell past a wall's
corner landed there, which the server refuses as a step (`TeleportTest`, `PlacementAgreementTest`); a
dash's landing, which comes after its phase (long after, when a frame hitched), was judged as a step
past a corner and refused, holding the player where the dash had got to (`PacketValidatorTest`);
a trap thrown onto a cell another trap lay on was sent, refused and its cast spent
(`GameClientTrapTest`); and a player a fence went up on had every update from that cell refused as a
step onto the fence (`PacketValidatorTest`).

What the judge doesn't hold against the rules: a trap refused because someone else's landed there
after it was thrown, and a step refused for a fence someone put up in the same moment (races the
client couldn't have seen; the stepper has `Judge.RaceSettleMs` to be put back in line), a cast whose projectiles the test's own
process stalled past the server's window for a cast (`PacketValidator.castWindowMs`), as a GC pause on
a real server would, and a fence the server hands back (the client lets the server judge
where a fence may go, unlike a star or a trap, so one aimed at a wall or out of reach is refused and
given back: `Loopback.FenceHandedBack`). Each is counted in the tally.

What it showed and was then made tolerant of (design/networking.md, *Bad networks*): steps bunched by
jitter refused as a speed hack, positions that went only by datagram and were never said again (a
stopped player a step back on the server, a dash's lost landing leaving its player over what it
crossed), and a client sending where it was before the server had said where it put it. What it
leaves as it is: an item used as its player died, before their client heard, is ignored by the server
without a word, so the two inventories differ until the respawn empties both.

To extend it: a new request a client can make gets a `Judge` (what the server says when it takes it
and when it doesn't); a new part of the match a client keeps gets a line in `checkAgreement`; a new
thing a player can do gets a branch in `Persona.attack`. Keep a target under about ten seconds:
`RosterSweepTest` and the fuzz tests are the longest, and Bazel runs them side by side. Check a change
to them with `bazel test //src/test/scala/com/gridgame/e2e:all --runs_per_test=6`: the rules run on
the wall clock, and copies running side by side stall one another, which is how a dash's late
landing was found — a test that only passes on an idle machine will fail on a busy one.

When a test fixes a bug, check it fails with the bug put back.
