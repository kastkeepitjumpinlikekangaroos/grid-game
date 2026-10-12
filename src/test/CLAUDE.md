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
  and reads what the server wrote still signed, to see which token signed it;
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
  map's edge, over water but not lava, at only a fence for a shot that flies over walls), and the
  mouse's pointer (`CursorTest`: its picture whole at every pixel scale, the dot on the hotspot, put
  down on whole pixels, and only over the window's content). `TileTest`, in
  `common/model`, also holds the tileset to what the renderer assumes: a flat tile is exactly
  its diamond, a block covers its whole footprint. `BlastStyleTest` holds the blasts to the roster:
  every attack that blasts has a look of its own, and no two characters share one.
  `ProjectileLookTest` does the same for projectiles drawn as their thrower's: every attack in the
  roster is drawn, everyone who threw the old crescent has a look no one else has, and a type's own
  look (for a thrower nobody knows) is one of its throwers'. `ProjectileThrowerTest`, in `client/`,
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
  `GameClientKnockbackTest` is how a knockback is shown: ours slid on the test's clock
  (`TestClient.nanos`), everyone else's neither a teleport nor a turn.
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
  every closer.
- The opening of a match: `TeamDividerTest` in `common/model` (the halves, and what the world
  itself then refuses — a step, a blink, a star, a trap throw) and in `server` (spawns, shots,
  blasts and steps against the wall, and the two announcements); `CeasefireTest` for a
  free-for-all's opening, one case per attack and cast behaviour, players and bots alike;
  `GameClientOpeningTest` for the client's copy of either; and `LobbyFlowTest` for a real Teams
  match starting with each team in its own half.
- `tools/` — `DocsRosterTest` does the same for the website: every character card's role and
  health must be what the roster says (see *Website*).

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
  wrong type, or with no heading), traps, items, rejoins, leaving — between its ticks, respawns and
  time passing (cooldowns cleared and effects worn off by hand: every rule runs on the wall clock),
  in a free-for-all, Teams, both openings and practice. Nothing throws; health stays in bounds;
  everyone stays on the map; nobody is moved onto a cell they can't stand on unless phased; the
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
  its player acts and frames are flown, a new match starting whenever one ends. Nothing it hears
  may throw (`GameClient.processAll` loses only the packet that does).
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

When a test fixes a bug, check it fails with the bug put back.
