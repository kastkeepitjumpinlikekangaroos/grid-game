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
  common/            # Shared models, protocol (16 packet types), world loader
    model/           # Player, Tile (34), CharacterDef (112), ProjectileDef, Item, Projectile
    protocol/        # 16 packet types, serialization, HMAC signing (PacketSigner)
    world/           # WorldLoader (JSON map parsing, 7 layer types)
    observability/   # OpenTelemetry facade (Telemetry, Metrics, Attrs, Tracing, Log)
  server/            # Game server, lobbies, auth, bots, projectiles, items, ranked queue
                     # TLS (TlsProvider), rate limiting (RateLimiter), validation (PacketValidator)
  client/            # Client entry point (ClientMain, GameClient, NetworkThread)
    gl/              # OpenGL renderer (see Rendering Architecture below)
    render/          # Shared render utilities (camera, isometric transform, entity collection)
    ui/              # JavaFX UI screens (TileRenderer, BackgroundRenderer, CharacterSelectionPanel)
    input/           # GLFW input (GLKeyboardHandler, GLMouseHandler, ControllerHandler)
  mapeditor/         # Standalone map editor (12 source files)
worlds/              # 16 JSON map definitions
sprites/             # Tile sheet + 112 character sprite sheets
scripts/             # Python sprite generators (14 scripts)
docs/                # GitHub Pages landing site
ops/observability/   # docker-compose stack (Collector, Prometheus, Tempo, Loki, Grafana)
                     # with auto-provisioned Grafana dashboards and datasources
```

## Rendering Architecture

The game uses a dual-window approach: JavaFX for UI screens (login, lobby, character selection, scoreboard) and a GLFW window with OpenGL 3.3 for in-game rendering. When a match starts, the JavaFX stage hides and a GLFW window opens; when the match ends, the GLFW window is destroyed and JavaFX resumes.

### OpenGL Renderer (`client/gl/`)

| File | Purpose |
|------|---------|
| `GLWindow.scala` | GLFW window lifecycle (create, show, destroy, resize) |
| `GLFWManager.scala` | Singleton ensuring GLFW is initialized once (shared with ControllerHandler) |
| `GLGameRenderer.scala` | Main game renderer (~1500 lines) — tiles, players, projectiles, items, status effects, HUD, aim arrow, backgrounds |
| `GLProjectileRenderers.scala` | All 112 projectile type renderers (~1150 lines) with 8 pattern factories and 19 specialized renderers |
| `ShapeBatch.scala` | Batched colored 2D primitives (fillRect, fillOval, fillOvalSoft, fillPolygon, strokeLine, etc.) |
| `SpriteBatch.scala` | Batched textured quads with per-vertex tint/alpha |
| `ShaderProgram.scala` | GLSL shader compilation/linking + embedded shader source (color, texture, bloom, blur, composite) |
| `PostProcessor.scala` | Post-processing FBO pipeline: bloom extract, Gaussian blur, composite with vignette and damage overlay |
| `GLTexture.scala` | PNG loading via STB image, FBO creation for render-to-texture |
| `GLTileRenderer.scala` | Loads tile sprite sheet as GL texture, provides texture regions per tile ID + frame |
| `GLSpriteGenerator.scala` | Loads character sprite sheets as GL textures |
| `GLFontRenderer.scala` | AWT-based font rasterization to GL texture atlas, supports outlined text with drop shadows |
| `Matrix4.scala` | Orthographic projection matrix for 2D rendering |
| `TextureRegion.scala` | UV sub-region of a texture atlas |

### Rendering Pipeline

```
1. PostProcessor.beginScene()     -- bind scene FBO
2. GLGameRenderer.render()        -- all game drawing into FBO
   a. Background (sky, cityscape, space, desert, ocean)
   b. Tiles (ground + elevated, depth-sorted)
   c. Items (bobbing, glow, sparkles)
   d. Players (sprites, shadows, health bars, names)
   e. Projectiles (112 types via GLProjectileRenderers)
   f. Status effects (shields, burns, freezes, etc.)
   g. Aim arrow + charge effects
   h. Death/teleport/explosion animations
   i. HUD overlay (abilities, inventory, kill feed)
3. PostProcessor.endScene()       -- bloom extract → blur → composite + vignette
```

### Projectile Rendering System

All 112 projectile types are mapped in `GLProjectileRenderers`. Projectiles use standard alpha blending for solid, visible shapes. The bloom post-processor provides natural glow on bright elements.

**8 pattern factories** cover common projectile shapes with per-type color and size:
- `energyBolt` — round glowing orb with orbiting sparkles and trail
- `beamProj` — thick directional beam with bright core
- `spinner` — rotating multi-armed star (axes, shurikens, katanas)
- `physProj` — arrow/dart with prominent head and fletching
- `lobbed` — arcing sphere with ground shadow and bounce feel
- `aoeRing` — expanding concentric rings with pulsing glow
- `chainProj` — zigzag lightning bolt segments
- `wave` — wide crescent sweep

**19 specialized renderers** handle unique projectiles: fireball (spiral fire arms), lightning (forking bolts), tidal wave (cresting water), boulder (tumbling rock), shark jaw (animated teeth), bat swarm, shadow bolt (void tendrils), inferno blast (fire vortex), and more.

### Post-Processing

The `PostProcessor` applies screen-wide effects after all game rendering:
- **Bloom**: Extracts bright pixels at half resolution, applies two-pass Gaussian blur, composites back with screen blending
- **Vignette**: Subtle edge darkening via smoothstep falloff
- **Damage overlay**: Red flash when the player takes a hit

Settings: `bloomThreshold=0.88`, `bloomStrength=0.12`, `vignetteStrength=0.08` (tuned for subtle enhancement).

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
