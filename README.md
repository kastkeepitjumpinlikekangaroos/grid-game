# Grid Game

A multiplayer 2D isometric arena game built with Scala, using LWJGL/OpenGL for GPU-accelerated game rendering and JavaFX for UI screens.

Players connect to a server, join or create lobbies, pick from 112 unique characters across 8 themed categories, and battle in timed free-for-all matches across 16 maps. Features include ranked matchmaking with ELO, account-based authentication, bot players, gamepad support, and a built-in map editor.

## Quick Start

```bash
# Build
bazel build //...

# Run server (default port 25565)
bazel run //src/main/scala/com/gridgame/server:server

# Run server on custom port
bazel run //src/main/scala/com/gridgame/server:server -- 25566

# Run client (login UI handles connection)
bazel run //src/main/scala/com/gridgame/client:client

# Run map editor
bazel run //src/main/scala/com/gridgame/mapeditor

# Run the local observability stack (Grafana + Prometheus + Tempo + Loki + OTel Collector)
cd ops/observability && docker compose up -d
# Then open http://localhost:3000 — pre-provisioned dashboards under "Grid Game"
```

## Features

- **112 Characters** - Across 8 categories (Original, Elemental, Undead/Dark, Medieval/Fantasy, Sci-Fi/Tech, Nature/Beast, Mythological, Specialist), each with unique primary attack, Q ability, and E ability
- **16 Maps** - From small arenas to large battlegrounds with varied terrain (34 tile types)
- **Advanced Projectile System** - Pierce, boomerang, ricochet, AoE splash, explosions, charge scaling, distance-based damage, and 10 on-hit effects (freeze, burn, root, slow, pull, push, life steal, vortex, speed boost, teleport)
- **5 Item Types** - Heart (heal), Star (speed), Gem (score), Shield (defense), Fence (placeable barrier)
- **Lobby System** - Create/join lobbies, configure map and game duration, add bots
- **Ranked Queue** - ELO-based matchmaking
- **Accounts** - Login/register with persistent stats, match history, and leaderboards
- **Network Security** - TLS 1.3 encrypted TCP (explicit cipher suites), HMAC-signed packets, session tokens with expiration, rate limiting, per-channel auth failure tracking, UDP replay protection (sliding window), UDP source IP validation, projectile velocity validation, and server-side anti-cheat validation
- **Observability** - OpenTelemetry SDK with metrics, traces, and logs over OTLP. ~40 server-side instruments cover packets, latency, kills/deaths, validation, ranked queue, DB, and JVM runtime. Self-hosted stack in `ops/observability/` (Collector + Prometheus + Tempo + Loki + Grafana, all docker-compose). Opt-in client telemetry via `--telemetry`.
- **GPU-Accelerated Rendering** - OpenGL 3.3 via LWJGL with batched draw calls, post-processing (bloom, vignette), and 112 distinct projectile visual effects
- **Isometric Rendering** - 2.5D tile-based world with parallax backgrounds
- **Gamepad Support** - Controller input via LWJGL/GLFW
- **Character Selection** - Filterable, categorized grid with ability previews and animated sprite previews
- **Map Editor** - Standalone tool with tile palette, drawing tools, undo/redo, properties panel, and world file export

## Controls

| Key | Action |
|-----|--------|
| WASD | Move |
| Mouse | Aim direction |
| Left Click / Space | Shoot |
| Shift+Space | Burst shot (charged) |
| Q | Ability 1 |
| E | Ability 2 |
| Hold Space | Charge shot |
| F11 | Toggle fullscreen |

## Characters

112 characters organized into 8 categories:

| Category | Count | Examples |
|----------|-------|----------|
| Original | 12 | Spaceman, Gladiator, Wraith, Wizard, Tidecaller, Soldier, Raptor, Assassin, Warden, Samurai, Plague Doctor, Vampire |
| Elemental | 15 | Pyromancer, Cryomancer, Stormcaller, Earthshaker, Inferno, Glacier, Avalanche |
| Undead/Dark | 15 | Necromancer, Skeleton King, Banshee, Lich, Reaper, Deathknight, Shadowfiend |
| Medieval/Fantasy | 15 | Paladin, Ranger, Berserker, Druid, Bard, Monk, Valkyrie, Warlock |
| Sci-Fi/Tech | 15 | Cyborg, Hacker, MechPilot, Android, Chronomancer, Graviton, Railgunner |
| Nature/Beast | 15 | Wolf, Serpent, Bear, Hawk, Phoenix, Hydra, Gorilla, Chameleon |
| Mythological | 15 | Minotaur, Medusa, Cerberus, Kraken, Sphinx, Griffin, Fenrir, Chimera |
| Specialist | 10 | Alchemist, Puppeteer, Gambler, Pirate, Chef, Musician, Shapeshifter |

Each character has unique abilities using cast behaviors: StandardProjectile, PhaseShiftBuff, DashBuff, TeleportCast, FanProjectile, and GroundSlam.

## Tech Stack

- **Language**: Scala 2.13
- **Rendering**: LWJGL 3.3.4 / OpenGL 3.3 core profile (game), JavaFX 21 (UI screens)
- **Networking**: Netty (TLS 1.3 TCP + HMAC-signed UDP, 80-byte packets with 64-byte payload + 16-byte HMAC, replay protection, UDP source validation, session expiration)
- **Database**: SQLite (accounts, match history, ELO)
- **Build**: Bazel with rules_scala
- **Input**: GLFW (keyboard, mouse, gamepad)
- **Assets**: Python (Pillow) sprite generators
- **Observability**: OpenTelemetry Java SDK 1.42 + autoconfigure, JVM runtime metrics, OTLP gRPC export. Local stack: OTel Collector, Prometheus, Tempo, Loki, Grafana (docker-compose)

## Project Structure

```
src/main/scala/com/gridgame/
  common/            # Shared by server, client and tools
    model/           # The rules as data: characters (roster/), projectiles, effects, tiles, the world
    protocol/        # 18 packet types, serialization, HMAC signing (PacketSigner)
    world/           # WorldLoader (JSON maps)
    observability/   # OpenTelemetry facade (Telemetry, Metrics, Attrs, Tracing, Log)
  server/            # net/ (sockets, TLS, sessions), account/, game/ (a match), bots/, lobby/
  client/            # ClientMain (JavaFX app) and MatchWindow (the match's GLFW window)
    game/            # GameClient: the client's side of the game
    gl/              # OpenGL primitives (batches, shaders, fonts, post-processing)
    render/          # The renderer: GLGameRenderer and its painters (world/, hud/),
                     # projectile renderers (projectiles/) and explosions (blasts/)
    ui/              # JavaFX theme and widgets; ui/screens/ has a class per screen
    input/, audio/, net/, i18n/, devtools/
  mapeditor/         # Standalone map editor
  tools/             # Website roster cards, i18n content catalog
worlds/              # JSON maps
sprites/             # Tile sheet + 112 character sprite sheets
scripts/             # Python asset generators (tiles, maps, sprites, sounds, icons)
design/              # Feature design docs
docs/                # GitHub Pages landing site
ops/observability/   # docker-compose stack (Collector, Prometheus, Tempo, Loki, Grafana)
```

Each package is its own Bazel library and depends only on the layers below it. How each part works,
and why, is documented next to the code: start at [CLAUDE.md](CLAUDE.md), which indexes every
module doc and the feature docs in `design/`.

## Rendering Architecture

The game uses a dual-window approach: JavaFX for UI screens (login, lobby, character selection, scoreboard) and a GLFW window with OpenGL 3.3 for in-game rendering. When a match starts, the JavaFX stage hides and a GLFW window opens; when the match ends, the GLFW window is destroyed and JavaFX resumes.

`GLGameRenderer` draws each frame pass by pass — the background where the map ends, the ground as
opaque diamonds, traps and blasts on the ground, then blocks, items, projectiles and players
interleaved by depth, then barriers, glows, animations and particles — into a scene target, which
the post-processor composites (bloom, light map, grade, vignette). Name plates, health bars, damage
numbers and the HUD are drawn over the finished frame at full resolution. Each part of the frame is a
painter of its own (`client/render/world`, `client/render/hud`); every projectile type and every
explosion has its own look (`client/render/projectiles`, `client/render/blasts`). See
[client/render/CLAUDE.md](src/main/scala/com/gridgame/client/render/CLAUDE.md).

## Observability

The server is instrumented with OpenTelemetry (metrics, traces, logs). Telemetry is enabled by default on the server and falls back to a no-op SDK if the OTLP endpoint is unreachable, so the game runs identically with or without the stack running. The client opts in via `--telemetry` or `GRIDGAME_TELEMETRY=1`.

```bash
# Bring up the local stack (Collector + Prometheus + Tempo + Loki + Grafana)
cd ops/observability && docker compose up -d
# Grafana is at http://localhost:3000 with anonymous admin access.
# Four dashboards under the "Grid Game" folder:
#   Overview      — connections, packets, latency, anti-cheat, gameplay activity
#   Gameplay      — kills, characters, projectiles, ranked queue, ELO
#   Client        — frame duration, RTT, reconnects (when --telemetry enabled)
#   JVM Runtime   — heap, GC, threads
```

Configuration is via standard `OTEL_*` env vars (see `ops/observability/.env.example`). Set `OTEL_SDK_DISABLED=true` to turn off entirely.

### What's instrumented (server)

~40 instruments. Highlights:

- **Network**: `gridgame.packets.received|sent|dropped`, `gridgame.bandwidth.bytes`, `gridgame.packet.process.duration`, `gridgame.hmac.failures`, `gridgame.replay.rejected`
- **Connections / sessions**: async gauges for `gridgame.connections.active`, `gridgame.sessions.active`, `gridgame.lobbies.active`, `gridgame.instances.active`, `gridgame.projectiles.active`, `gridgame.items.active`, `gridgame.bots.active`
- **Gameplay** (server-authoritative — includes bot activity): `gridgame.kills` (killer × victim × projectile), `gridgame.deaths` (by cause), `gridgame.respawns`, `gridgame.projectiles.spawned|hit|expired`, `gridgame.items.spawned|picked_up|used`, `gridgame.character.played` (incremented from `ClientRegistry.add`, covers all join paths), `gridgame.tiles.modified`, `gridgame.chat.messages`
- **Performance**: `gridgame.tick.duration` (phase = projectile|player|timer|bot), `gridgame.db.duration` (per op), `gridgame.auth.duration`
- **Anti-cheat**: `gridgame.rate_limit.triggered` (kind), `gridgame.validation.failed` (kind = movement_speed|movement_bounds|projectile_velocity|fire_rate|character|…)
- **Ranked**: `gridgame.queue.players` (gauge per mode), `gridgame.queue.matches_made`, `gridgame.queue.wait_time`, `gridgame.elo.delta`
- **JVM**: standard `jvm.memory.*`, `jvm.gc.*`, `jvm.threads`, `jvm.cpu.*` via the OTel runtime metrics module

The instrumentation lives in `common/observability/`. Hot paths use pre-built `Attributes` instances (`Attrs.scala`) so per-call allocation is zero.

### Key gotchas discovered during integration

- **Wire-packet counts ≠ activity counts.** `gridgame.packets.received{type="PROJECTILE_UPDATE"}` only counts client TCP/UDP packets — bot projectiles bypass the network. Use `gridgame.projectiles.spawned` for total activity (humans + bots).
- **Don't set `const_labels` on the Prometheus exporter** when also using `resource_to_telemetry_conversion: enabled: true` — same label name twice triggers a "duplicate label" error in the Go Prometheus client and silently drops every metric. The `resource` processor's `cluster` attribute already becomes a label.
- **Grafana provisioned datasources need an explicit `uid:`** — otherwise a random UID is assigned and dashboards that reference `uid: prometheus` show "datasource not found" with no error in the UI.
- **Histogram unit suffixes.** OTel's Prometheus exporter renames `gridgame.tick.duration` (unit `ms`) to `gridgame_tick_duration_milliseconds_bucket` and `gridgame.match.duration` (unit `s`) to `gridgame_match_duration_seconds_bucket`. Match-duration queries need `_seconds`, not `_milliseconds`.
