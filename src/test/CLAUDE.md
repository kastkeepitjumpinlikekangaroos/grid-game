# Testing

```bash
bazel test //src/test/...                                        # everything
bazel test //src/test/scala/com/gridgame/server:combat_test      # one target
```

Each test file is its own `scala_junit_test` target, so each runs in a JVM of its own: a new test
file is a line in the list in its directory's BUILD.bazel (its target name, its file, and any
`data` it reads). The suites mirror the source tree:

- `common/` — the model, the wire protocol (every packet round-trips in `PacketRoundTripTest`),
  the roster's data (`CharacterRosterTest`), and the maps (`WorldMapsTest`: spawns on open
  ground, all open ground one region). `ProjectileDefRegistryTest` must be the first thing in its
  JVM to look a projectile up, which is why it is a target, and a single test, of its own.
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
  could send (another player's name, the wrong key, garbage, forged datagrams, replays);
  `RateLimiterTest`; `ReconnectTest` for rejoins, logins over a live connection and disconnects;
  `ChatTest` for who hears what; `RankedQueueTest` for matchmaking, run by hand on a clock of the
  test's own (`RankedQueue.checkQueue(now)`), so a minute's wait takes no time; and
  `SpawnRequestTest` for what the server takes from a spawn request (heading, charge, who may
  fire). On the client, `ClientNetworkTest` feeds its handlers through an `EmbeddedChannel`.
- Projectiles in flight: `ProjectileTicksTest` (server) pins the tick every projectile packet
  carries. `ProjectileFlightTest` (client) sends shots as the server flies and stamps them, over a
  network with latency, jitter and loss, and draws frames at 60 fps on the test's own clock
  (`TestClient.nanos`, which projectile packets are stamped with as they arrive): every frame's
  step, a late packet changing nothing, the stops (a wall's face, the end of range, the divider, a
  barrier — and none at water's edge), and the fade of a shot whose end was lost.
- `client/render`, `client/gl` — the camera (`CameraTest`: the isometric mapping, the pixel grid,
  whether the view has run off the map), the light pool (`LightPoolTest`), and how other players
  are drawn (`RemoteMotionTest`: an even pace under jitter, never past where they stopped or a dash
  ended, corners turned where they were heard turning, jumps drawn at once). `TileTest`, in
  `common/model`, also holds the tileset to what the renderer assumes: a flat tile is exactly
  its diamond, a block covers its whole footprint. `BlastStyleTest` holds the blasts to the roster:
  every attack that blasts has a look of its own, and no two characters share one.
- `client/` — `ClientTestKit.scala`: `TestClient` is a `GameClient` whose packets go to a
  capture (`GameClient.packetSink`) and which is handed the server's with `processPacket`.
  The screen tests (`LobbyRoomScreenTest`, `ScoreboardScreenTest`, `CharacterSelectionPanelTest`)
  build the real JavaFX screens, never shown, on the FX thread via `Fx { ... }`, and click
  them. They set `GRIDGAME_AUDIO=off`, and they must not call `Messages.setLocale`, which would
  save a language to the player's own settings. `GameClientBlastTest` is what the client records
  of a blast: one per explosion or splash (a slam is one, not an explosion and a splash; the HITs of
  everyone a splash caught are the same one), who threw it, and none where the terrain stopped a
  splash that goes off only at the end of its range. `GameClientSpawnRequestsTest` is which of our
  spawn requests the server answered (`PendingSpawns`): by the first packet about the projectile,
  whatever it says, for the attack that asked for its type, and none counted as lost for dying.
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

**Effects nothing in the roster has yet** (a stun, a poison, a slam that pushes or pulls) are
pinned with `ProjectileDef`s registered by the test itself, on ids the game doesn't use — see
`TestEffects` at the foot of `OnHitEffectsTest`. Each test file gets its own JVM, so a test-only
registration can't leak into another suite. Bots are walked on a clock of the test's own
(`BotController.tick(now)`), so `BotMovementTest` covers ten seconds of their pace in no time. `TrapDef.register` is the same door for a trap the
roster hasn't got — a poison short enough for a test to sit through (`TestTraps` in `TrapTest`).
A character the roster doesn't have (one quicker
than any in it) goes through `PacketValidator`'s `characterOf` parameter instead — see
`PacketValidatorTest.aFasterCharacterIsJudgedByItsOwnPace`.

When a test fixes a bug, check it fails with the bug put back.
