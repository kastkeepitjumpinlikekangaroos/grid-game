# Grid Game - Multiplayer 2D Arena Game

A multiplayer 2D isometric arena game built with Scala, using LWJGL/OpenGL for GPU-accelerated game rendering and JavaFX for UI screens. Networking via TCP+UDP with Netty.

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
```

## Architecture Overview

```
┌──────────────────────────────────────────────────────────────────┐
│                            CLIENT                                │
│  ┌─────────────┐  ┌─────────────┐  ┌────────────────────────┐   │
│  │ ClientMain  │  │ GameClient  │  │ NetworkThread          │   │
│  │ (JavaFX App)│──│ (State)     │──│ (TCP+UDP via Netty)    │   │
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
                           │ TCP + UDP
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
│   ├── protocol/               # Network packets (15 packet types)
│   └── world/                  # WorldLoader (JSON map parsing, 7 layer types)
├── server/                     # GameServer, GameInstance, Lobby, LobbyManager, LobbyHandler
│   │                           # AuthDatabase, BotManager, BotController, ProjectileManager
│   │                           # ItemManager, RankedQueue, KillTracker, ClientHandler, ClientRegistry
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

Fixed 64-byte packets over TCP (reliable) and UDP (fast updates), using Netty. Byte order: BIG_ENDIAN.

### Packet Types (15 total)

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

### Connection Flow

```
Client                              Server
   │                                   │
   │──── AUTH_REQUEST ────────────────>│  (login/register)
   │<─── AUTH_RESPONSE ───────────────│
   │                                   │
   │──── LOBBY_ACTION (list) ────────>│  (browse/create/join lobbies)
   │<─── LOBBY_ACTION (lobby data) ───│
   │                                   │
   │──── LOBBY_ACTION (start) ───────>│  (host starts game)
   │<─── WORLD_INFO (filename) ───────│
   │<─── PLAYER_JOIN (broadcast) ─────│
   │                                   │
   │──── PLAYER_UPDATE ──────────────>│  (gameplay loop)
   │<─── PLAYER_UPDATE (broadcast) ───│
   │<─── PROJECTILE_UPDATE ───────────│
   │<─── ITEM_UPDATE ─────────────────│
   │<─── GAME_EVENT ──────────────────│
   │                                   │
   │──── HEARTBEAT (every 3s) ───────>│
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
3. Create packet class extending `Packet`
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

## Bazel Build Notes

- Uses `rules_scala` with Scala 2.13.16
- Dependencies: JavaFX 21.0.1, Netty 4.1.104, Gson 2.10.1, SQLite JDBC 3.44.0, Guava 32.1.3, LWJGL 3.3.4 (glfw, opengl, stb + macOS ARM64 and Windows natives)
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
