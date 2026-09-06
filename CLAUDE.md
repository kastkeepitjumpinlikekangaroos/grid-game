# Grid Game - Multiplayer 2D Arena Game

A multiplayer 2D isometric arena game built with Scala, using LWJGL/OpenGL for GPU-accelerated game rendering and JavaFX for UI screens. Networking via TLS-encrypted TCP + HMAC-signed UDP with Netty.

## Build & Run

```bash
# Build everything
bazel build //...

# Run server (lobby-based, maps selected per-lobby)
bazel run //src/main/scala/com/gridgame/server:server

# Run server on custom port
bazel run //src/main/scala/com/gridgame/server:server -- 25566

# Run client (login UI prompts for host/port)
bazel run //src/main/scala/com/gridgame/client:client

# Run map editor
bazel run //src/main/scala/com/gridgame/mapeditor

# Bring up the observability stack (Grafana at http://localhost:3000)
cd ops/observability && docker compose up -d

# Disable telemetry on the server (uses no-op OTel SDK)
OTEL_SDK_DISABLED=true bazel run //src/main/scala/com/gridgame/server:server

# Run the client with telemetry opted in
GRIDGAME_TELEMETRY=1 bazel run //src/main/scala/com/gridgame/client:client
# or
bazel run //src/main/scala/com/gridgame/client:client -- --telemetry
```

### macOS: proper app name/icon in Dock & Cmd+Tab

`bazel run` launches the JVM directly, so macOS shows "java" as the app name/icon
in the Dock, Cmd+Tab switcher, and Force Quit dialog — there is no supported Java
API to override that for an unbundled process (see `ClientMain.setDockIcon` /
`-Xdock:name` in `client/BUILD.bazel`, which only cover the title bar and best-effort
Dock icon). To get "Grid Game" / "Grid Game Map Editor" with the wizard icon
everywhere, build a real `.app` bundle via `jpackage`:

```bash
# Requires a JDK 14+ with jpackage on PATH (e.g. brew install openjdk)
scripts/build_macos_app.sh client       # -> dist/macos/Grid Game.app
scripts/build_macos_app.sh mapeditor    # -> dist/macos/Grid Game Map Editor.app

open "dist/macos/Grid Game.app"
```

The script builds the target's `_deploy.jar` (already carries `sprites/`, `worlds/`,
`fonts/`, `i18n/` on its classpath) and wraps it with `sprites/icon_wizard.icns` via
`jpackage --type app-image`. `dist/` is gitignored — regenerate locally as needed.

### Windows: standalone .exe with a bundled runtime

The `_windows` Bazel targets (`client_windows`, `mapeditor_windows`) cross-build fine
from any OS — Bazel just packages the Windows-native LWJGL/JavaFX jars onto the
classpath. But turning that into a real `.exe` (bundled JRE, no separate Java install
needed, our icon baked into the executable, optional Start Menu installer) requires
`jpackage`, and **jpackage does not cross-compile** — it must run ON Windows, since it
links a native Windows launcher against the local JDK's runtime image. This can't be
produced from this (macOS) checkout; it needs to run on a Windows machine or a
`windows-latest` CI runner.

```powershell
# On Windows, with a JDK 14+ (jpackage) and Bazel installed:
python3 scripts\generate_icon.py                # -> sprites\icon_wizard.ico (cross-platform via Pillow)
.\scripts\build_windows_exe.ps1                  # -> dist\windows\Grid Game\Grid Game.exe
.\scripts\build_windows_exe.ps1 -Target mapeditor
.\scripts\build_windows_exe.ps1 -Installer       # WiX Toolset v3 installer (Start Menu/desktop shortcut, uninstaller)
```

`-Installer` requires the WiX Toolset v3 (`candle.exe`/`light.exe`) on `PATH`; without
it, the default `--type app-image` build still produces a fully standalone, icon'd,
double-click-able `.exe` folder — just without an installer wizard. `sprites/icon_wizard.ico`
is already committed (Pillow can write `.ico` cross-platform, unlike `.icns`), so only
the jpackage step itself needs a Windows box.

## Architecture Overview

```
┌──────────────────────────────────────────────────────────────────┐
│                            CLIENT                                │
│  ┌─────────────┐  ┌─────────────┐  ┌────────────────────────┐   │
│  │ ClientMain  │  │ GameClient  │  │ NetworkThread          │   │
│  │ (JavaFX App)│──│ (State)     │──│ (TLS+HMAC via Netty)   │   │
│  └─────────────┘  └─────────────┘  └────────────────────────┘   │
│         │                │                                       │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │                 OpenGL Renderer (GLFW Window)               │ │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐  │ │
│  │  │GLGameRenderer│  │ ShapeBatch   │  │ SpriteBatch      │  │ │
│  │  │(All game     │  │ (2D prims)   │  │ (Textured quads) │  │ │
│  │  │ rendering)   │  │              │  │                  │  │ │
│  │  └──────────────┘  └──────────────┘  └──────────────────┘  │ │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐  │ │
│  │  │GLProjectile  │  │PostProcessor │  │ GLFontRenderer   │  │ │
│  │  │Renderers(112)│  │(Bloom+Vign.) │  │ (AWT→GL atlas)   │  │ │
│  │  └──────────────┘  └──────────────┘  └──────────────────┘  │ │
│  └─────────────────────────────────────────────────────────────┘ │
│  ┌──────────────┐  ┌──────────────┐  ┌────────────────────────┐  │
│  │GLKeyboard    │  │GLMouse       │  │ ControllerHandler      │  │
│  │Handler(GLFW) │  │Handler(GLFW) │  │ (GLFW gamepad)         │  │
│  └──────────────┘  └──────────────┘  └────────────────────────┘  │
│  ┌──────────────────────────────────────────────────────────────┐ │
│  │              JavaFX UI (Login, Lobby, Scoreboard)            │ │
│  │  ┌──────────────────────┐  ┌──────────────────────────────┐ │ │
│  │  │CharacterSelectionPanel│  │AbilityPreviewRenderer       │ │ │
│  │  │(Categorized grid)    │  │(Animated ability previews)   │ │ │
│  │  └──────────────────────┘  └──────────────────────────────┘ │ │
│  └──────────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────────┘
                           │ TLS 1.3 (TCP) + HMAC-signed (UDP)
                           ▼
┌──────────────────────────────────────────────────────────────────┐
│                            SERVER                                │
│  ┌─────────────┐  ┌──────────────┐  ┌────────────────────────┐  │
│  │ ServerMain  │──│ GameServer   │──│ ClientHandler          │  │
│  │ (Entry)     │  │ (Netty TCP/  │  │ (Packet Processing)    │  │
│  │             │  │  UDP Loops)  │  │                        │  │
│  └─────────────┘  └──────────────┘  └────────────────────────┘  │
│                          │                                       │
│  ┌──────────────┐  ┌──────────────┐  ┌────────────────────────┐ │
│  │ LobbyManager │  │ GameInstance │  │ ClientRegistry         │ │
│  │ (Lobby CRUD) │  │ (Match State)│  │ (Player Map)           │ │
│  └──────────────┘  └──────────────┘  └────────────────────────┘ │
│                          │                                       │
│  ┌──────────────┐  ┌──────────────┐  ┌────────────────────────┐ │
│  │ AuthDatabase │  │ Projectile   │  │ ItemManager            │ │
│  │ (SQLite)     │  │ Manager      │  │ (Spawns/Pickups)       │ │
│  └──────────────┘  └──────────────┘  └────────────────────────┘ │
│                                                                  │
│  ┌──────────────┐  ┌──────────────┐  ┌────────────────────────┐ │
│  │ RankedQueue  │  │ BotManager   │  │ KillTracker            │ │
│  │ (Matchmaking)│  │ (AI Players) │  │ (Scoring)              │ │
│  └──────────────┘  └──────────────┘  └────────────────────────┘ │
│                                                                  │
│  ┌──────────────┐  ┌──────────────┐  ┌────────────────────────┐ │
│  │ TlsProvider  │  │ RateLimiter  │  │ PacketValidator        │ │
│  │ (TLS certs)  │  │ (Throttling) │  │ (Anti-cheat)           │ │
│  └──────────────┘  └──────────────┘  └────────────────────────┘ │
└──────────────────────────────────────────────────────────────────┘
```

## Project Structure

```
src/main/scala/com/gridgame/
├── common/                     # Shared code between client and server
│   ├── model/                  # Data models (Player, Tile, CharacterDef, Item, Projectile, etc.)
│   │                           # 112 characters, 34 tiles, 6 cast behaviors, projectile defs
│   │                           # ProjectileDef (pierce, boomerang, ricochet, AoE, explosions)
│   │                           # 10 on-hit effects, charge/distance damage scaling
│   │                           # 5 item types (Gem, Heart, Star, Shield, Fence)
│   ├── protocol/               # Network packets (16 packet types), PacketSigner (HMAC-SHA256)
│   ├── observability/          # OpenTelemetry facade (Telemetry, Metrics, Attrs, Tracing, Log)
│   │                           # Pre-built instruments + cached Attributes for hot paths
│   └── world/                  # WorldLoader (JSON map parsing, 7 layer types)
├── server/                     # GameServer, GameInstance, Lobby, LobbyManager, LobbyHandler
│   │                           # AuthDatabase, BotManager, BotController, ProjectileManager
│   │                           # ItemManager, RankedQueue, KillTracker, ClientHandler, ClientRegistry
│   │                           # TlsProvider, RateLimiter, PacketValidator
├── client/                     # Client (GameClient, ClientMain, NetworkThread)
│   ├── gl/                     # OpenGL renderer (GLGameRenderer, GLProjectileRenderers,
│   │                           # ShapeBatch, SpriteBatch, ShaderProgram, PostProcessor,
│   │                           # GLTexture, GLFontRenderer, GLWindow, GLFWManager,
│   │                           # GLTileRenderer, GLSpriteGenerator, Matrix4, TextureRegion)
│   ├── render/                 # Shared render utilities (GameCamera, IsometricTransform, EntityCollector)
│   ├── ui/                     # JavaFX UI screens (TileRenderer, BackgroundRenderer, SpriteGenerator,
│   │                           # CharacterSelectionPanel, AbilityPreviewRenderer)
│   └── input/                  # GLKeyboardHandler, GLMouseHandler, ControllerHandler
└── mapeditor/                  # Standalone map editor (12 source files)
    │                           # MapEditorApp, EditorCanvas, EditorState, TilePalette,
    │                           # DrawingTools, ToolBar, PropertiesPanel, MenuBarBuilder,
    │                           # UndoManager, WorldSaver, NewMapDialog, StatusBar,
    │                           # EditorTileRenderer

src/test/scala/com/gridgame/   # Tests (ConstantsTest, PositionTest)
worlds/                         # World definition files (16 JSON maps)
sprites/                        # Sprite assets (tiles.png + 112 character PNGs)
scripts/                        # Asset generation scripts (14 Python scripts)
docs/                           # GitHub Pages landing site
ops/observability/              # Local docker-compose stack
                                # OTel Collector, Prometheus, Tempo, Loki, Grafana
                                # Auto-provisioned datasources + dashboards (JSON)
```

## Rendering Architecture

The client uses a dual-window approach: JavaFX for UI screens (login, lobby, character selection, scoreboard) and a GLFW window with OpenGL 3.3 core profile for in-game rendering.

### Window Lifecycle
When a match starts, `ClientMain.showGameScene()` hides the JavaFX Stage and creates a GLFW window with an OpenGL context. The game loop runs via JavaFX's `AnimationTimer` (fires on the main thread, required for both GLFW and OpenGL on macOS). On game over, the GLFW window is destroyed and the JavaFX Stage is shown again.

### OpenGL Renderer (`client/gl/`)

| File | Lines | Purpose |
|------|-------|---------|
| `GLGameRenderer.scala` | ~1530 | Main renderer: tiles, players, projectiles, items, status effects, HUD, aim arrow, backgrounds, death/teleport/explosion animations |
| `GLProjectileRenderers.scala` | ~1150 | All 112 projectile type renderers (8 pattern factories + 19 specialized renderers) |
| `ShapeBatch.scala` | ~300 | Batched colored 2D primitives: fillRect, fillOval, fillOvalSoft, fillPolygon, strokeLine, strokeLineSoft, strokeOval, strokePolygon. Supports additive blend mode toggle. |
| `SpriteBatch.scala` | ~200 | Batched textured quads with per-vertex tint/alpha. Flushes on texture change. |
| `ShaderProgram.scala` | ~190 | GLSL shader compilation + embedded shader source: ColorShader (pos+color), TextureShader (pos+texcoord+color), BloomExtract, GaussianBlur, Composite (bloom+vignette+overlay) |
| `PostProcessor.scala` | ~150 | Post-processing FBO pipeline: Scene FBO → Bloom extract (half-res) → Blur H → Blur V → Composite |
| `GLTexture.scala` | ~120 | PNG loading via STB image → GL texture. FBO creation for render-to-texture. |
| `GLFontRenderer.scala` | ~160 | AWT-based font rasterization → GL texture atlas. Supports outlined text with drop shadows. Three sizes (16/24/48px). |
| `GLWindow.scala` | ~100 | GLFW window create/show/destroy/resize |
| `GLFWManager.scala` | ~25 | Singleton `ensureInitialized()` shared by ControllerHandler and GLWindow |
| `GLTileRenderer.scala` | ~50 | Loads `sprites/tiles.png` as GL texture, returns TextureRegion per tile ID + frame |
| `GLSpriteGenerator.scala` | ~70 | Loads character sprite sheets as GL textures |
| `Matrix4.scala` | ~30 | Orthographic projection matrix |
| `TextureRegion.scala` | ~10 | Case class for (texture, u, v, u2, v2) sub-regions |

### Shared Render Utilities (`client/render/`)

| File | Purpose |
|------|---------|
| `GameCamera.scala` | Holds visualX/Y, smooth lerp, screen shake, zoom. Provides camera offsets. |
| `IsometricTransform.scala` | `worldToScreen(wx,wy,cam)`, `screenToWorld(sx,sy,cam,zoom)` |
| `EntityCollector.scala` | Collects items/projectiles/players by grid cell for depth-sorted rendering |

### Rendering Pipeline
```
PostProcessor.beginScene()        -- bind scene FBO
GLGameRenderer.render()           -- all game drawing into scene FBO
  Background → Tiles → Items → Players → Projectiles →
  Status Effects → Aim Arrow → Animations → HUD
PostProcessor.endScene()          -- bloom extract → blur H → blur V →
                                     composite (scene + bloom + vignette + overlay)
```

### Batch Management
`GLGameRenderer` uses `beginShapes()` / `beginSprites()` / `endAll()` helpers to minimize state transitions. Only one batch (shape or sprite) is active at a time; calling `beginShapes()` while the sprite batch is active will end the sprite batch first, and vice versa.

### Projectile Rendering System
All 112 projectile types are registered in `GLProjectileRenderers.registry` (`Map[Byte, Renderer]`). Projectiles use **standard alpha blending** for solid, visible shapes — the bloom post-processor provides natural glow on bright elements.

**8 pattern factories** (configurable color + size):
- `energyBolt` — round glowing orb with orbiting sparkles and trail
- `beamProj` — thick directional beam with bright core
- `spinner` — rotating multi-armed star (axes, shurikens, katanas)
- `physProj` — arrow/dart with prominent head and fletching
- `lobbed` — arcing sphere with ground shadow
- `aoeRing` — expanding concentric rings with pulsing glow
- `chainProj` — zigzag lightning bolt segments
- `wave` — wide crescent sweep

**19 specialized renderers** for unique projectiles: fireball (spiral fire arms), lightning (forking bolts), tidal wave (cresting water), boulder (tumbling rock), shark jaw (animated chomping teeth), bat swarm, shadow bolt (void tendrils with purple eyes), inferno blast (fire vortex), and more.

Type alias: `type Renderer = (Projectile, Float, Float, ShapeBatch, Int) => Unit`

To add a new projectile renderer:
1. Add an entry to the `registry` map in `GLProjectileRenderers`
2. Either use a pattern factory (`energyBolt(r, g, b, size)`) or write a specialized `draw*` method
3. The renderer receives screen-space coordinates (sx, sy) already transformed from world space

### Post-Processing
Settings in `PostProcessor`: `bloomThreshold=0.88`, `bloomStrength=0.12`, `vignetteStrength=0.08`. Bloom FBOs run at half resolution. Composite shader uses screen blending for bloom and smoothstep vignette.

### Key Design Decisions
- **Standard alpha blending for projectiles** — additive blending (`GL_SRC_ALPHA, GL_ONE`) makes projectiles invisible on bright terrain and removes all visual distinction. Standard blending with high alpha (0.7-0.95) produces solid, visible, distinct shapes. Bloom post-processor handles glow naturally.
- **GLFW window swap** — hiding JavaFX Stage and creating a GLFW window avoids FBO→WritableImage pixel-copy overhead. Both use Cocoa NSWindows on macOS and coexist safely.
- **AnimationTimer game loop** — fires on the FX Application Thread (main thread on macOS), which is required for both GLFW and OpenGL calls. No threading complexity.
- **JavaFX UI retained** — Login, lobby, character selection, and scoreboard remain in JavaFX. Only in-game rendering uses OpenGL.

## Asset Generation

Sprites are pre-rendered images loaded at runtime.

### Tile Sprites
```bash
# Requires Pillow: pip install Pillow
python3 scripts/generate_tiles.py
```

- **Input**: Tile definitions hardcoded in the script (colors from `Tile.scala`, elevations per tile)
- **Output**: `sprites/tiles.png` (40x56px per cell, 34 tiles total)
- Flat (walkable) tiles: diamond at bottom 20px, upper area transparent
- Elevated (non-walkable) tiles: top diamond + left/right side faces, bottom-aligned
- If you add a new tile type to `Tile.scala`, also add its entry to the `TILES` list in this script and regenerate

### Application Icon
```bash
python3 scripts/generate_icon.py   # -> sprites/icon_wizard_{128,256,1024}.png + sprites/icon_wizard.ico
scripts/generate_icns.sh           # macOS-only: -> sprites/icon_wizard.icns (from the 1024 master)
```
Renders the wizard's front-facing frame (via `generate_wizard.py`'s `draw_wizard`) standalone,
crops/centers it, and exports at icon sizes. Used for the JavaFX Stage/Taskbar icon
(`ClientMain.loadAppIcons`/`setDockIcon`, `MapEditorApp`), the GLFW in-game window icon
(`GLWindow.setIcon`), the `.icns` consumed by `scripts/build_macos_app.sh`, and the
`.ico` consumed by `scripts/build_windows_exe.ps1`. Pillow writes `.ico` directly
(cross-platform); `.icns` needs macOS's `sips`/`iconutil`, hence the separate script.

### Character Sprites
The original 11 characters each have a dedicated generator script (`scripts/generate_<name>.py`). The remaining 100 characters are generated in batch using shared utilities.

```bash
# Individual character (original 11)
python3 scripts/generate_gladiator.py   # -> sprites/gladiator.png
python3 scripts/generate_wizard.py      # -> sprites/wizard.png
# etc.

# Batch generate newer characters (uses sprite_base.py utilities)
python3 scripts/generate_all_new_characters.py
```

Each sprite sheet contains 4 directions x 4 animation frames.

## Characters (112 total)

Characters are defined across 8 categories in `CharacterId.scala` and `CharacterDef.scala`:

| Category | IDs | Count | Characters |
|----------|-----|-------|------------|
| Original | 0-11 | 12 | Spaceman, Gladiator, Wraith, Wizard, Tidecaller, Soldier, Raptor, Assassin, Warden, Samurai, PlagueDoctor, Vampire |
| Elemental | 12-26 | 15 | Pyromancer, Cryomancer, Stormcaller, Earthshaker, Windwalker, MagmaKnight, Frostbite, Sandstorm, Thornweaver, Cloudrunner, Inferno, Glacier, Mudslinger, Ember, Avalanche |
| Undead/Dark | 27-41 | 15 | Necromancer, SkeletonKing, Banshee, Lich, Ghoul, Reaper, Shade, Revenant, Gravedigger, Dullahan, Phantom, Mummy, Deathknight, Shadowfiend, Poltergeist |
| Medieval/Fantasy | 42-56 | 15 | Paladin, Ranger, Berserker, Crusader, Druid, Bard, Monk, Cleric, Rogue, Barbarian, Enchantress, Jester, Valkyrie, Warlock, Inquisitor |
| Sci-Fi/Tech | 57-71 | 15 | Cyborg, Hacker, MechPilot, Android, Chronomancer, Graviton, Tesla, Nanoswarm, Voidwalker, Photon, Railgunner, Bombardier, Sentinel, Pilot, Glitcher |
| Nature/Beast | 72-86 | 15 | Wolf, Serpent, Spider, Bear, Scorpion, Hawk, Shark, Beetle, Treant, Phoenix, Hydra, Mantis, Jellyfish, Gorilla, Chameleon |
| Mythological | 87-101 | 15 | Minotaur, Medusa, Cerberus, Centaur, Kraken, Sphinx, Cyclops, Harpy, Griffin, Anubis, Yokai, Golem, Djinn, Fenrir, Chimera |
| Specialist | 102-111 | 10 | Alchemist, Puppeteer, Gambler, Blacksmith, Pirate, Chef, Musician, Astronomer, Runesmith, Shapeshifter |

### Cast Behaviors
Each ability uses one of these cast behaviors (defined in `CharacterDef.scala`):
- `StandardProjectile` — fires a projectile toward the cursor
- `PhaseShiftBuff(durationMs)` — grants a temporary buff (e.g., ethereal form)
- `DashBuff(maxDistance, durationMs, moveRateMs)` — dash movement ability
- `TeleportCast(maxDistance)` — instant teleport to cursor position
- `FanProjectile(count, fanAngle)` — fires multiple projectiles in a fan pattern
- `GroundSlam(radius)` — AoE ground slam around the caster

### Projectile System
Projectiles are defined in `ProjectileDef.scala` with extensive customization:
- **Charge scaling** — speed, damage, and range scale with charge level
- **Distance damage scaling** — damage increases over distance (e.g., spears)
- **Pierce** — passes through multiple players (`pierceCount`)
- **Boomerang** — returns to owner after max range
- **Ricochet** — bounces off walls (`ricochetCount`)
- **AoE splash** — area damage on hit or at max range, with optional freeze/root
- **Explosions** — center/edge damage with blast radius
- **Pass-through** — can ignore players or walls

### On-Hit Effects
10 effect types applied when projectiles hit players:
- `Freeze(durationMs)`, `Root(durationMs)`, `Slow(durationMs, multiplier)`
- `Burn(totalDamage, durationMs, tickMs)`, `Push(distance)`, `PullToOwner`
- `VortexPull(radius, pullStrength)`, `LifeSteal(healPercent)`
- `SpeedBoost(durationMs)`, `TeleportOwnerBehind(distance, freezeDurationMs)`

### Item Types
5 item types (defined in `ItemType.scala`): Gem, Heart, Star, Shield, Fence

## Network Protocol

80-byte packets (64-byte payload + 16-byte HMAC-SHA256) over TLS-encrypted TCP (reliable) and HMAC-signed UDP (fast updates), using Netty. Byte order: BIG_ENDIAN.

### Network Security

9 layers of security protect the networking stack:

1. **TLS 1.3 for TCP** — All TCP traffic encrypted via Netty `SslHandler`. Server generates a self-signed certificate at startup using `keytool` with a random password and restrictive temp directory permissions (`rwx------`). Explicit cipher suites: `TLS_AES_256_GCM_SHA384`, `TLS_CHACHA20_POLY1305_SHA256`. Client trusts all certs (game server, not web).
2. **HMAC Packet Signing** — After auth, server issues a 32-byte session token. All subsequent packets (TCP and UDP) carry a 16-byte truncated HMAC-SHA256. Packets with invalid HMAC are dropped silently. UDP packets without a valid session token are dropped entirely (no unsigned UDP fallback).
3. **Rate Limiting** — Per-client: 60 UDP/s, 20 TCP/s. Per-IP: 5 connections/min, 5 auth failures before 30s cooldown. Per-channel: connection closed after 5 auth failures (`MAX_AUTH_FAILURES_PER_CHANNEL`). Race-free auth tracking via `computeIfAbsent`. Stale entries cleaned up every 5s.
4. **Server-Side Validation** — Movement validated against world bounds, walkability, and speed limits (2x expected + 2 cells tolerance, Long arithmetic to prevent overflow). Projectile spawn validated against player position (max 3 cells), fire rate (80% of `SHOOT_COOLDOWN_MS`), velocity (NaN/Inf rejection, magnitude <= sqrt(2)), and charge level (0-100). Health values validated against `MAX_HEALTH`.
5. **Auth Hardening** — Constant-time hash comparison (`MessageDigest.isEqual`), dummy hash on username-not-found (prevents timing enumeration), password minimum 6 characters.
6. **Replay Protection** — `PacketValidator` tracks sequence numbers per player with a sliding window bitmap (`SEQUENCE_WINDOW_SIZE = 256`) for UDP out-of-order tolerance. TCP enforces strictly increasing sequence numbers. Duplicate/replayed packets are rejected.
7. **UDP Source Validation** — Server records each player's TCP connection IP (`playerTcpAddresses`). UDP packets are only accepted if the sender IP matches the player's TCP IP, preventing UDP source spoofing.
8. **Session Token Expiration** — Tokens expire after `SESSION_TOKEN_LIFETIME_MS` (1 hour). The cleanup loop removes expired tokens and closes the player's TCP channel, forcing re-authentication.
9. **Client Disconnect Recovery** — `NetworkThread` uses Netty `IdleStateHandler` for read timeout detection (`CLIENT_TIMEOUT_MS`). On disconnect, a callback notifies `GameClient` which clears game state and can transition the UI back to the login screen. Incoming packet queue is bounded (`INCOMING_QUEUE_CAPACITY = 2048`) to prevent memory exhaustion. Sequence numbers reset on reconnect.

### Packet Format

```
┌─────────────────────────────────────────────────────────────┐
│                     80 bytes on wire                        │
├──────────────────────────────────────────┬──────────────────┤
│  64-byte payload (PACKET_PAYLOAD_SIZE)   │  16-byte HMAC    │
│  [0]     Packet type ID                  │  Truncated       │
│  [1-4]   Sequence number                 │  HMAC-SHA256     │
│  [5-20]  Player UUID                     │  (or zeroed      │
│  [21-63] Type-specific data              │   if pre-auth)   │
└──────────────────────────────────────────┴──────────────────┘
```

Serialization uses `Constants.PACKET_PAYLOAD_SIZE` (64 bytes). Transport uses `Constants.PACKET_SIZE` (80 bytes). The HMAC is an outer layer — `PacketSigner.sign()` wraps a 64-byte payload into an 80-byte signed packet, and `PacketSigner.verify()` unwraps it back.

### Packet Types (16 total)

| ID   | Name              | Transport | Description                    |
|------|-------------------|-----------|--------------------------------|
| 0x01 | PLAYER_JOIN       | TCP       | Player enters game             |
| 0x02 | PLAYER_UPDATE     | UDP       | Position/health updates        |
| 0x03 | PLAYER_LEAVE      | TCP       | Player disconnects             |
| 0x04 | WORLD_INFO        | TCP       | World filename                 |
| 0x05 | HEARTBEAT         | UDP       | Keep-alive signal              |
| 0x06 | PROJECTILE_UPDATE | UDP       | Projectile movement            |
| 0x07 | ITEM_UPDATE       | TCP       | Item spawns/pickups            |
| 0x08 | TILE_UPDATE       | TCP       | Tile changes                   |
| 0x09 | LOBBY_ACTION      | TCP       | Lobby operations               |
| 0x0A | GAME_EVENT        | TCP       | Kill/death events              |
| 0x0B | AUTH_REQUEST      | TCP       | Login/register                 |
| 0x0C | AUTH_RESPONSE     | TCP       | Auth result                    |
| 0x0D | MATCH_HISTORY     | TCP       | Game statistics                |
| 0x0E | RANKED_QUEUE      | TCP       | Ranked matchmaking             |
| 0x0F | LEADERBOARD       | TCP       | Rankings                       |
| 0x10 | SESSION_TOKEN     | TCP       | Session token delivery (post-auth) |

### Connection Flow

```
Client                              Server
   │                                   │
   │═══ TLS 1.3 Handshake ═══════════│  (encrypted TCP channel)
   │                                   │
   │──── AUTH_REQUEST ────────────────>│  (login/register, no HMAC yet)
   │<─── AUTH_RESPONSE ───────────────│
   │<─── SESSION_TOKEN ───────────────│  (32-byte token for HMAC signing)
   │                                   │
   │  ── all packets HMAC-signed ──   │
   │                                   │
   │──── LOBBY_ACTION (list) ────────>│  (browse/create/join lobbies)
   │<─── LOBBY_ACTION (lobby data) ───│
   │                                   │
   │──── LOBBY_ACTION (start) ───────>│  (host starts game)
   │<─── WORLD_INFO (filename) ───────│
   │<─── PLAYER_JOIN (broadcast) ─────│
   │                                   │
   │──── PLAYER_UPDATE ──────────────>│  (gameplay loop, validated)
   │<─── PLAYER_UPDATE (broadcast) ───│
   │<─── PROJECTILE_UPDATE ───────────│
   │<─── ITEM_UPDATE ─────────────────│
   │<─── GAME_EVENT ──────────────────│
   │                                   │
   │──── HEARTBEAT (every 3s) ───────>│  (rate limited)
   │                                   │
```

## Common Modifications

### Adding a New Tile Type
1. Add case object to `Tile.scala` with id, name, walkable, color
2. Add to `Tile.all` sequence
3. Add entry to `TILES` list in `scripts/generate_tiles.py` (with color and elevation)
4. Run `python3 scripts/generate_tiles.py` to regenerate `sprites/tiles.png`
5. Use in world JSON files

### Adding a New Packet Type
1. Add to `PacketType.scala` (new case object with unique ID, specify `tcp = true/false`)
2. Add to `PacketType.values` array
3. Create packet class extending `Packet` (use `Constants.PACKET_PAYLOAD_SIZE` for `ByteBuffer.allocate` in `serialize()`)
4. Add deserialization case in `PacketSerializer.deserialize()`
5. Handle in `GameClient.processPacket()` or `ClientHandler.processPacket()`

### Adding a New Character
1. Add `CharacterId` entry in `CharacterId.scala` (next available ID byte)
2. Define `ProjectileDef` entries for the character's projectiles in `CharacterDef.scala`
3. Register projectile defs in `ProjectileDef.register()`
4. Create `CharacterDef` val with abilities, stats, and sprite sheet path
5. Add to `byId` map and `all` sequence in `CharacterDef`
6. Generate sprite sheet — either:
   - Create dedicated script: `scripts/generate_<name>.py` (uses `sprite_base.py`)
   - Or add to `scripts/generate_all_new_characters.py` batch generator
7. Run the script to produce `sprites/<name>.png`

### Adding New World Layer Type
1. Add case in `WorldLoader.parseLayer()` match statement
2. Implement tile placement logic
3. Supported layer types: `fill`, `rect`, `border`, `circle`, `line`, `points`, `grid`

### Adding a New Metric
1. Add the instrument to `common/observability/Metrics.scala` — counter / histogram / async gauge as appropriate. Set a descriptive `setDescription` and an OTel unit (`ms`, `s`, `By`, `{event}`, etc.).
2. If the metric carries labels that recur in hot paths, pre-build the `Attributes` in `common/observability/Attrs.scala` (cache by key — never allocate per call inside a tick loop).
3. Call `Metrics.<instrument>.add(...)` / `.record(...)` at the call site. For async gauges, register a callback in the file that owns the underlying state (see how `ProjectileManager`, `ItemManager`, `BotController` wire their gauges in their constructor).
4. Add a Grafana panel to the relevant dashboard JSON in `ops/observability/grafana/dashboards/`. The OTel Prometheus exporter normalizes names: dots → underscores, counters get `_total`, histograms get `_bucket`/`_sum`/`_count`, and the unit becomes a suffix (`ms` → `_milliseconds`, `s` → `_seconds`, `By` → `_bytes`). Grafana auto-reloads dashboards from the mounted volume every 10s — no restart needed.

## Observability

OpenTelemetry is wired in at server startup via `Telemetry.init("grid-game-server")` in `ServerMain.main`. The client opt-in path lives in `ClientMain.main` (`--telemetry` flag or `GRIDGAME_TELEMETRY=1`).

### Pipeline

```
Server  ──OTLP gRPC──►  OTel Collector  ──►  Prometheus  ──►  Grafana
(SDK 1.42)  :4317                       ──►  Tempo (traces)
                                        ──►  Loki (logs)
```

All four backends + Grafana are docker-composed in `ops/observability/`. Defaults target `localhost:4317`; falls back to a no-op SDK if init fails so the game runs identically with or without the stack up.

### Code layout (`common/observability/`)

| File | Purpose |
|------|---------|
| `Telemetry.scala` | SDK init via `AutoConfiguredOpenTelemetrySdk`; reads `OTEL_*` env vars. Idempotent `init()` + `shutdown()`. Registers JVM runtime metrics. |
| `Metrics.scala` | All ~40 instruments declared once as `val`s on a single `Meter("com.gridgame")`. Use `Metrics.foo.add(1L, attrs)` from call sites — no need to look up by name. |
| `Attrs.scala` | Cached `Attributes` instances keyed by packet type, character id, projectile type, drop reason, etc. Hot loops must NOT allocate via `Attributes.of(...)` per call. |
| `Tracing.scala` | `Tracing.span("name", attrs) { body }` helper. Used sparingly — counters/histograms are the primary instrumentation. |
| `Log.scala` | OTel logs bridge with `Log.info/warn/error(msg, kv...)`. Currently most code still uses `println` / `System.err.println`. |

### Key wiring decisions

- **`Metrics.characterPlayed` is incremented from `ClientRegistry.add`**, not from `ClientHandler.handlePlayerJoin`. Real human joins go through `LobbyHandler.handleStart` / `RankedQueue.start*Match` (which call `registry.add` directly), not through the `PLAYER_JOIN` packet path — only client *rejoins* during an active match hit `handlePlayerJoin`. Putting the counter at the registry chokepoint covers all join paths, including bots.
- **Async gauges live on the owning class**, registered in its constructor with `Meter.gaugeBuilder(...).buildWithCallback { obs => obs.record(state.size(), Attrs.Empty) }`. See `ProjectileManager`, `ItemManager`, `BotController`, `GameServer`, `RankedQueue` for examples.
- **`gridgame.kills` is labeled with `killer_character × victim_character × projectile_type`**. With 112 characters × 112 × 112 projectile types that's high theoretical cardinality but Prometheus handles it fine because most combos never occur. If this gets out of hand, drop one of the dimensions.
- **Network metrics are wire-only.** Bot-fired projectiles bypass the network entirely, so `gridgame.packets.received{type="PROJECTILE_UPDATE"}` shows just human activity. For total game-event rates use the server-authoritative counters like `gridgame.projectiles.spawned`.

### Backend stack gotchas (resolved during integration — read before changing collector config)

- **Do not set `const_labels:` on the `prometheus` exporter when also using `resource_to_telemetry_conversion: enabled: true`.** The `resource` processor's attributes get promoted to labels, and if the same name appears in `const_labels` the Go Prometheus client throws "duplicate label names in constant and variable labels" and silently drops every metric (the OTel collector still reports them as "sent" — there's no error in `otelcol_*` metrics either). The collector logs show the error, but only there.
- **Grafana provisioned datasources need an explicit `uid:`** in `provisioning/datasources/datasources.yaml`. Without it, Grafana auto-generates a UID and dashboards that reference `uid: prometheus` (or any specific UID) silently fail to render with no error message in the UI.
- **The `OTLP receiver` and `prometheusexporter` are bound to different ports**: `:4317`/`:4318` for OTLP in, `:8889` for Prometheus scrape out. Confirm by curling `http://localhost:8889/metrics` to see what the exporter is actually serving.

### Configuration

Standard `OTEL_*` env vars (see `ops/observability/.env.example`). Most useful:

- `OTEL_SDK_DISABLED=true` — turn off entirely
- `OTEL_EXPORTER_OTLP_ENDPOINT=http://remote:4317` — point at a non-local collector
- `OTEL_RESOURCE_ATTRIBUTES=service.version=...,deployment.environment=prod`
- `OTEL_METRIC_EXPORT_INTERVAL=10000` — milliseconds between metric flushes

## Bazel Build Notes

- Uses `rules_scala` with Scala 2.13.16
- Dependencies: JavaFX 21.0.1, Netty 4.1.104, Gson 2.10.1, SQLite JDBC 3.44.0, Guava 32.1.3, LWJGL 3.3.4 (glfw, opengl, stb + macOS ARM64 and Windows natives), OpenTelemetry Java SDK 1.42.1 + autoconfigure + OTLP exporter + JVM runtime metrics (2.8.0-alpha)
- OTel deps live in `common/BUILD.bazel` as `OTEL_DEPS` and are re-exported, so `server` and `client` pick them up transitively through `//src/main/scala/com/gridgame/common:common`
- Build targets:
  - `//src/main/scala/com/gridgame/server:server`
  - `//src/main/scala/com/gridgame/client:client`
  - `//src/main/scala/com/gridgame/client:client_windows`
  - `//src/main/scala/com/gridgame/common:common`
  - `//src/main/scala/com/gridgame/mapeditor`
  - `//src/main/scala/com/gridgame/mapeditor:mapeditor_windows`
  - `//docs:website`

## Website (GitHub Pages)

Static landing page served from the `docs/` directory on `main` via GitHub Pages.

### Files
- `docs/index.html` — Single-page site (hero, features, characters, controls, download)
- `docs/style.css` — Dark theme stylesheet
- `docs/script.js` — Smooth scroll for nav anchors
- `docs/BUILD.bazel` — Bazel filegroup target

### Deployment
GitHub Pages is configured to deploy from `main` branch, `/docs` directory. Any push to `main` that modifies `docs/` will auto-deploy.

### Creating Releases (Deploy JARs)
Bazel's `scala_binary` auto-supports `_deploy.jar` suffix targets, producing fat JARs with all dependencies bundled. No BUILD file changes needed.

```bash
# Build fat JARs
bazel build //src/main/scala/com/gridgame/client:client_deploy.jar
bazel build //src/main/scala/com/gridgame/client:client_windows_deploy.jar

# Create a GitHub release with both JARs
gh release create v1.0.0 \
  bazel-bin/src/main/scala/com/gridgame/client/client_deploy.jar#"Grid Game (macOS)" \
  bazel-bin/src/main/scala/com/gridgame/client/client_windows_deploy.jar#"Grid Game (Windows)"
```
