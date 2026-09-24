# Grid Game - Multiplayer 2D Arena Game

A multiplayer 2D isometric arena game built with Scala, using LWJGL/OpenGL for GPU-accelerated game rendering and JavaFX for UI screens. Networking via TLS-encrypted TCP + HMAC-signed UDP with Netty.

## Build & Run

```bash
# Build everything
bazel build //...

# Run every test (see Testing)
bazel test //src/test/...

# Run server (lobby-based, maps selected per-lobby)
bazel run //src/main/scala/com/gridgame/server:server

# Run server on custom port
bazel run //src/main/scala/com/gridgame/server:server -- 25566

# Run client (login UI prompts for host/port)
bazel run //src/main/scala/com/gridgame/client:client

# Run map editor
bazel run //src/main/scala/com/gridgame/mapeditor

# Render the projectile gallery contact sheets (dev tool, see Projectile Rendering System)
bazel run //src/main/scala/com/gridgame/client:projectile_gallery -- /tmp/gallery

# Bring up the observability stack (Grafana at http://localhost:3000)
cd ops/observability && docker compose up -d

# Disable telemetry on the server (uses no-op OTel SDK)
OTEL_SDK_DISABLED=true bazel run //src/main/scala/com/gridgame/server:server

# Run the client with telemetry opted in
GRIDGAME_TELEMETRY=1 bazel run //src/main/scala/com/gridgame/client:client
# or
bazel run //src/main/scala/com/gridgame/client:client -- --telemetry

# Graphics quality (default auto — starts high, steps down if frames stay slow).
# F7 cycles it in game.
bazel run //src/main/scala/com/gridgame/client:client -- --quality=low
GRIDGAME_QUALITY=medium bazel run //src/main/scala/com/gridgame/client:client

# Run the client with no sound at all (automated runs, a misbehaving audio device)
GRIDGAME_AUDIO=off bazel run //src/main/scala/com/gridgame/client:client

# Performance dev tools (see Client Memory & Performance)
bazel run //src/main/scala/com/gridgame/client:render_bench   # a busy match, no server
bazel run //src/main/scala/com/gridgame/client:render_bench -- --effects  # ... with status effects on
bazel run //src/main/scala/com/gridgame/client:render_bench -- --barriers # ... with barriers raised
bazel run //src/main/scala/com/gridgame/client:render_bench -- --traps    # ... with traps on the ground
bazel run //src/main/scala/com/gridgame/client:render_bench -- --divider  # ... with a Teams match's opening wall
bazel run //src/main/scala/com/gridgame/client:render_bench -- --ceasefire # ... with a free-for-all's opening ceasefire
bazel run //src/main/scala/com/gridgame/client:render_bench -- --map=the_meadow.json --at=60,17 # another map, from one cell of it
bazel run //src/main/scala/com/gridgame/client:render_bench -- --types=LIGHTNING,SOUL_BOLT  # only these projectile types in flight
GRIDGAME_GPU_PROFILE=1 bazel run //src/main/scala/com/gridgame/client:render_bench  # ... with fragments and GPU time per phase
bazel run //src/main/scala/com/gridgame/client:ui_bench       # the JavaFX menus

# Can everything be seen on every map? (see Rendering Architecture: Readability audit)
bazel run //src/main/scala/com/gridgame/client:render_audit -- --out=/tmp/audit
python3 scripts/render_audit.py /tmp/audit                  # needs numpy and Pillow

# Terrain: the tileset, the gallery to judge it in, and the generated maps (see Asset Generation)
python3 scripts/generate_tiles.py                  # -> sprites/tiles.png (needs Pillow)
python3 scripts/tile_gallery.py /tmp/tilegallery   # contact sheet, tiled fields, little scenes
python3 scripts/generate_maps.py                   # -> worlds/the_meadow.json, the_lagoon.json, the_snowglobe.json
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
│   │                           # 112 characters, 42 tiles (4 forms: ground, pool, block, prop),
│   │                           # 8 cast behaviors, projectile defs
│   │                           # ProjectileDef (pierce, boomerang, ricochet, AoE, explosions)
│   │                           # 10 on-hit effects, charge/distance damage scaling
│   │                           # 5 item types (Gem, Heart, Star, Shield, Fence)
│   │                           # Trap, TrapDef (5 kinds), TrapPlacement
│   │                           # MatchOpening (what a match's first 30s does), TeamDivider
│   │                           # (the halves of a Teams map, and the wall between them)
│   ├── protocol/               # Network packets (18 packet types), PacketSigner (HMAC-SHA256)
│   ├── observability/          # OpenTelemetry facade (Telemetry, Metrics, Attrs, Tracing, Log)
│   │                           # Pre-built instruments + cached Attributes for hot paths
│   └── world/                  # WorldLoader (JSON map parsing, 7 layer types)
├── server/                     # GameServer, GameInstance, Lobby, LobbyManager, LobbyHandler
│   │                           # AuthDatabase, BotManager, BotController, ProjectileManager
│   │                           # ItemManager, TrapManager, RankedQueue, KillTracker,
│   │                           # ClientHandler, ClientRegistry
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

src/test/scala/com/gridgame/   # Tests (see Testing): common/ (model, protocol, world),
                                # server/ (matches driven by hand), client/ (GameClient, screens),
                                # tools/ (the website's character cards)
worlds/                         # World definition files (7 JSON maps; 3 from scripts/generate_maps.py)
sprites/                        # Sprite assets (tiles.png + 112 character PNGs)
scripts/                        # Asset generation scripts (31 Python scripts)
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
| `GLGameRenderer.scala` | ~7200 | Main renderer: tiles, players, projectiles, items, status effects, barriers, the team divider, the overlay over the finished world (name plates, health bars, damage numbers), HUD, aim arrow, backgrounds, death/teleport/explosion animations |
| `GLProjectileRenderers.scala` | ~6860 | All 176 projectile type renderers (15 pattern factories + 32 specialized renderers + the local-frame silhouette system), the orbs' shared anatomy, the tethers that tie a pull to its thrower, and the electricity |
| `ShapeBatch.scala` | ~800 | Batched colored 2D primitives: fillRect, fillOval, fillOvalSoft, fillPolygon (**convex only**), fillFan (star-shaped outline, fanned from an explicit centre — stars, sunbursts, faceted hulls), fillRibbon (a band given as an outer run plus the inner run reversed — crescent blades), fillArcBand (ring segment with an alpha ramp — gauges, crescents, shockwaves), fillStarFlare (4-point glint), fillRoundedRect(Gradient), strokeLine, strokeLineSoft, and the joined strokes — strokeArc, strokeOval, strokePolygon, strokeRect, strokeRoundedRect, strokePolyline, strokePolylineTapered, strokePolylineVar (a width and an alpha of its own at every point — a limb, a comet tail, a bolt's channel) (see *Strokes are one band*). Oval segment counts follow the oval's size on screen (`pixelsPerUnit`, set per pass). Supports additive blend mode toggle, plus an alpha multiplier and a scale-about-pivot applied to every vertex (`setAlphaMultiplier` / `setScaleAbout`; reset by `begin`). |
| `SpriteBatch.scala` | ~250 | Batched textured quads with per-vertex tint/alpha, and `drawQuad` for any four corners (the ground's diamonds). Flushes on texture change. `setBlending(false)` for opaque geometry. |
| `ShaderProgram.scala` | ~190 | GLSL shader compilation + embedded shader source: ColorShader (pos+color), TextureShader (pos+texcoord+color), BloomExtract, GaussianBlur, Composite (bloom+vignette+overlay) |
| `PostProcessor.scala` | ~240 | Post-processing FBO pipeline: Scene FBO → Bloom extract (half-res) → Blur H → Blur V → quarter-res pair → Composite. `GRIDGAME_HOLECHECK=1` clears the scene to magenta, so any pixel the terrain leaves uncovered shows. |
| `GLTexture.scala` | ~280 | PNG loading via STB image → GL texture (`loadInspected` lets the caller measure or amend the pixels before upload). FBO creation for render-to-texture. `padTransparent` (colour into transparent texels, cell by cell) and mipmapped atlases. |
| `GLFontRenderer.scala` | ~280 | AWT-based font rasterization → GL texture atlas. Rasterized at the display's pixel density (`pixelScale`) so text is sharp on HiDPI screens; metrics and sizes in units; every draw takes a `scale`. Outlined text with drop shadows, and a heavy eight-copy outline for text over the world. Three sizes (14/22/44). |
| `GLWindow.scala` | ~100 | GLFW window create/show/destroy/resize |
| `GLFWManager.scala` | ~25 | Singleton `ensureInitialized()` shared by ControllerHandler and GLWindow |
| `GLTileRenderer.scala` | ~170 | Loads `sprites/tiles.png` as GL texture (transparent texels padded with their neighbours' colour); returns full or transparent-margin-trimmed TextureRegion per tile ID + frame, and `drawDiamond` draws a flat tile as exactly its diamond |
| `GLSpriteGenerator.scala` | ~190 | Packs character sprite sheets into one growable, mipmapped GL atlas |
| `LightSystem.scala` | ~170 | The dynamic light map (quarter-res soft blobs, multiplied in by the composite) and `LightPool`, which keeps a busy frame's strongest lights |
| `GpuProfiler.scala` | ~110 | Dev tool: fragments shaded and GPU time per frame phase, with `GRIDGAME_GPU_PROFILE=1` |
| `RenderAudit.scala` | ~250 | Dev tool: the whole roster and every projectile over every map's ground, for `scripts/render_audit.py` to measure |
| `Matrix4.scala` | ~30 | Orthographic projection matrix |
| `TextureRegion.scala` | ~10 | Case class for (texture, u, v, u2, v2) sub-regions |

### Shared Render Utilities (`client/render/`)

| File | Purpose |
|------|---------|
| `GameCamera.scala` | Holds visualX/Y, smooth lerp, screen shake, zoom. Provides camera offsets, on the render target's pixel grid when it is told the grid. |
| `IsometricTransform.scala` | `worldToScreen(wx,wy,cam)`, `screenToWorld(sx,sy,cam,zoom)`, `viewInsideWorld` (is the whole screen over the map?) |
| `EntityCollector.scala` | Collects items/projectiles/players by grid cell for depth-sorted rendering. Cells are a flat grid over the visible window, each a linked list of pooled entries (`takeCell` / `takeRemaining`) — no map, no boxed keys, no allocation per frame |

### Rendering Pipeline
```
PostProcessor.beginScene()        -- bind scene FBO (sized by the quality tier)
GLGameRenderer.render()           -- all game drawing into scene FBO
  Background (only if the screen runs off the map) → Ground (opaque diamonds) →
  Items → Players → Projectiles → Status Effects → Aim Arrow → Animations
PostProcessor.endScene()          -- bloom extract → blur H → blur V →
                                     composite (scene + bloom + vignette + overlay)
                                     upscales to the real framebuffer
World overlay                     -- name plates, health bars, damage numbers: world
                                     positions, drawn after the composite at full resolution
HUD                               -- drawn after the composite, always at full resolution
```

### Batch Management
`GLGameRenderer` uses `beginShapes()` / `beginSprites()` / `endAll()` helpers to minimize state transitions. Only one batch (shape or sprite) is active at a time; calling `beginShapes()` while the sprite batch is active will end the sprite batch first, and vice versa.

**Never upload a batch with a plain `glBufferSubData` at offset 0.** `ShapeBatch.flush` /
`SpriteBatch.flush` append into a GPU ring buffer and map it with
`GL_MAP_UNSYNCHRONIZED_BIT | GL_MAP_INVALIDATE_RANGE_BIT`, orphaning the whole store when
the ring wraps. Rewriting bytes a queued draw still reads makes the driver stall until that
draw retires: measured per flush on this machine, plain `glBufferSubData` costs **70us**
whatever the offset, orphan+`glBufferSubData` 3.7us, and the unsynchronized mapped range
**0.4us**. At ~110 flushes a frame that difference was the whole frame budget — a single
330-vertex HUD flush was stalling for 1.7ms. The ring plus orphan-on-wrap is what makes
`UNSYNCHRONIZED` safe: no byte is ever rewritten while a draw that reads it is in flight.

Both batches map with `nglMapBufferRange` (a raw address) rather than `glMapBufferRange`,
which wraps every mapping in a new `ByteBuffer` because each flush maps a different range,
and write vertices with `MemoryUtil.memPutFloat` into their staging memory rather than
`FloatBuffer.put`. That makes a primitive's cost its arithmetic: the puts' limit checks and
position stores were the largest single cost of building a busy frame. Every primitive must
reserve its vertices with `ensureCapacity` before writing them; `vertex` only has a backstop.

### Projectile Rendering System
All 176 projectile types are registered in `GLProjectileRenderers.registry` (`Map[Byte, Renderer]`, flattened into `_rendererLUT` for O(1) lookup with no `Option` allocation). Projectiles use **standard alpha blending** for solid, visible shapes — the bloom post-processor provides natural glow on bright elements.

`Renderer` is a single-method trait, `apply(proj, sx, sy, sb, tick)`, not a
`(Projectile, Float, Float, ShapeBatch, Int) => Unit`: `scala.Function5` isn't specialized,
so calling one boxed both coordinates and the tick — three allocations per projectile per
frame. Factory lambdas (`(proj, sx, sy, sb, tick) => …` returned as a `Renderer`) convert to
it directly; a method in the registry goes through `asRenderer(drawX)`, not `(drawX _)`.

#### Silhouettes: how a projectile says whose ability it is

Anything with a recognisable real-world shape — an axe, a katana, a femur, a playing
card, a shovel, a grenade, a spear, an arrow — is authored **once in a local frame**
(+x along the object, +y across it, both roughly within [-1.3, 1.3]) as an array of
convex `Part`s, then stamped through one of two transforms:

| Transform | Used by | What it does |
|---|---|---|
| `drawParts` / `blitPart` | tumbling objects (`bladeSpinner`, `lobbed`) | rotate by the spin angle, squash y by `ISO_Y` into the ground plane, scale |
| `drawPartsDir` / `blitPartDir` | objects flying point-first (`flyingShaft`, `fistProj`) | pure screen rotation onto the travel vector, narrowing only the cross-axis by `FLAT_Y` |

The direction-aligned transform deliberately does **not** apply `ISO_Y`: the travel
vector handed to a renderer is already in screen space, and squashing it again shortens a
spear thrown "north" to two thirds of one thrown "east".

`Part`s must be convex — `ShapeBatch.fillPolygon` fan-triangulates from vertex 0 — so a
curved blade is split into two convex spans rather than described in one loop. Each part
carries a material colour plus a `tint` weight toward the projectile's registered colour,
so steel stays steel while the energy parts take the character's palette. Round details a
polygon list cannot express (bone knobs, card pips, a cursed blade's aura) live in
`weaponDetail`.

This exists because the previous `spinner` built its outline from a polar radius per
vertex, which can only ever describe a star: an axe, a katana, a femur and a playing card
all came out as the same spinning lens. A local-frame silhouette can carry a haft at one
end and a head at the other, so it still reads as an axe at every spin angle.

#### Heads sit on the hitbox

`(sx, sy)` is where the projectile's hitbox is. Draw the thing that hits **there**, and let
anything elongated — a trail, a tether, a wake, the bolt a lightning strike just drew —
trail *behind* it through `fadeLine`, which narrows and fades to nothing instead of ending
in a hard edge.

Never draw a body out *ahead* of `(sx, sy)`. Seven renderers used to (via a `beamTip`
helper, since removed): beams, the charge shot, the tentacle, the tethers, lightning, the
rocket and the shark jaw each ran a line `worldLen` world units forward and capped it with
a disc. The talon and the blood fang were the same bug in another shape — claws and fangs
running 24-36px forward from a knuckle or gum at the hitbox, so the points that read as the
projectile arrived a third of a tile before the bite did. They now close *on* the hitbox:
the foot/jaw sits a claw's length behind and the points land at `(sx, sy)`. That produced
two problems:
- **It looked wrong.** A stroked line with a ball on the end is the silhouette of a snake,
  not of an ability, and the whip and vine styles wiggled along their length so they
  literally slithered.
- **It lied about the hitbox.** The disc that read as the projectile's head arrived 100–150px
  before the damage did.

#### Terrain: stopped by it, or flying over it

**Collision cells match the drawn tiles.** The renderer draws tile `(c, r)` centred on world
`(c, r)`, so it covers `[c − 0.5, c + 0.5)`; `Projectile.getCellX/Y` is `floor(x + 0.5)` to
match. It used to truncate (`x.toInt`), which put every wall half a tile down-screen of its
sprite: shots heading toward the camera sank halfway into wall blocks (and, bucketed into the
wall's own draw slot, were drawn *over* the block face) before vanishing, shots heading away
stopped short, and projectiles flew half a tile off the bottom edges of the map.
`Projectile.ricochet` snaps to the faces at `c ± 0.5` for the same reason.
`ProjectileTerrainTest` pins all of this.

**A stopped projectile sinks into what it hit.** The server still removes a projectile on the
first half-cell sub-step that lands in a blocking cell, so the DESPAWN position can be up to
half a cell *inside* the wall. On DESPAWN the client runs `TerrainImpact.resolve`, which walks
back along the heading to the wall face, stops the projectile there, and keeps it in
`GameClient.fadingProjectiles` for `TerrainImpact.FADE_MS` (260 ms). `EntityCollector` puts it
in the depth pass, so walls in front still cover it, and it is dropped once expired.
`GLProjectileRenderers.drawAbsorbed` draws it shrinking toward the impact point while it fades,
with a contact flash, a ring across the face and chips kicked back in the colour of the tile it
struck (`Tile.color`: grey off stone, spray off water, dust at the map edge). Explosives still
explode, now centred on the face. A despawn at the end of range in open ground fades the same
way, without the puff.

The fade and shrink come from `ShapeBatch.setAlphaMultiplier` / `setScaleAbout`, applied in
`vertex`, so any renderer can be faded or shrunk without knowing it. `begin` resets them.

**Wall-passers fly.** Types with `passesThroughWalls` are drawn `flyLift` above their ground
point (20 px with a slow bob). Their bodies go in `drawFlyingProjectiles`, after the depth pass,
so no wall block can slice through them. Their shadow goes down *in* the depth pass, on the
surface below: `surfaceLift` raises it onto the top face of an elevated tile, so the shadow
climbs over the wall the projectile clears. Their particle trails spawn at the same height.

#### Orbs: a lit sphere, a comet tail, and a shape outside both

Nearly thirty characters' primaries are an `energyBolt`, so the orb is the thing seen most in
a match. Every style shares one anatomy: `cometTail` (a band of light as wide as the orb where it
leaves it, narrowing to a wisp — and straight, since a tail swung side to side behind a round
head is a tadpole), a modest glow, `orbBody` (an inked rim, the colour at full strength at the
edge lightening to a hot core that breathes: it glows from inside), and `shedMotes` off its back.
A style is whatever it adds *outside* that sphere.

What it replaced, and why, since each was tried: the whole orb pulsed between 100% and 30% alpha
three times a second (a strobe, and on pale ground its dim frames weren't there); a dozen white
specks orbited *inside* the body and read as dirt on a flat disc; `orbBody`'s first pass, lit
from outside with a hard specular spot and a rim light, made every orb a glass marble (and put a
smile under the soul bolt's eye sockets); and a halo 3.2 times its size tinted the ground for no
read, about 0.7 million fragments an orb at 4K. Measured back to back against the old renderer
(`render_bench --types=`, 150 of them at 4480x2404), the orbs' depth pass went from 106 to 73
million fragments and frame building from 8.5 to 7.0 ms; the tethered grabs cost what the
free-flying ones did, and the whole roster's mix is 5% fewer fragments at the same CPU.

#### Pulls are tied to whoever is pulling (tethers)

A grab that flies free reads as a glove somebody threw — the Bear's and Gorilla's was a brown
mitten on an empty box-shaped cuff, and seven characters shared a knot with three sausage fingers.
Every pull is drawn tied back to its thrower: the Kraken's and the Spaceman's tentacle (suckers down its underside,
a tip curling round what it caught), the Druid's, Thornweaver's and Treant's vine (thorns raked
back, leaves, a tendril), a leash of spirit to the Bear's and Gorilla's paw print and the
Griffin's talon, a spectral chain to the Death Knight's sickle hook, a rope to the Gladiator's
grapple and the Chef's meat hook. `GLGameRenderer.anchorToThrower` says where the thrower stands
(`setAnchor`, looked up only for `wantsAnchor` types: our camera position, or anyone else's
smoothed one), and without one (they died, or a dev tool has no players) the tether runs back to
`Projectile.originX/Y`. `layTether` lays it out — bowed, swaying slowly, and cut off at the
projectile's range so a thrower who has blinked away doesn't drag it across the screen —
`shapeTether` gives it a width per point, `strokeTether` strokes it in layers with
`strokePolylineVar`. The head is still at the hitbox; the tether only trails it. The Grasping
Dead isn't a pull and isn't tethered: skeletal hands claw up out of graves along its path, on
points fixed to the ground, the one at the head reaching and the ones behind sinking back.

#### Electricity is fixed to the ground

A bolt's channel stays where it was drawn. Its kinks are keyed on points fixed to the ground
along the flight line (`along = x·ux + y·uy`), so as the head flies on it carves the channel
and leaves it standing behind it, fading. A kink every other node alternates sides by an uneven
amount (`kink`: the lightning-bolt zigzag, never more than ~55° off the line, so no mitre
folds), the nodes between are knocked aside by a crackle re-struck twenty times a second, forks
flash off it forward and out, and the head is a ball of light with arcs crackling all round it.
Thunder Strike is a storm cloud rolling over the target spot and striking it fifteen times a
second, onto the hitbox.

The bolt it replaced was a zigzag built relative to the head and re-rolled whole every three
frames, with kinks up to 23px either side of segments 7px long: a sawtooth stick carried along
by the head, jumping to a new shape twenty times a second, every hairpin mitred into a needle,
three prongs out ahead of the head like the legs of a bug. Two tries on the way that don't work:
a smooth meander is a worm, and kinks small beside the stroke's width are a noodle.

**15 pattern factories** (configurable colour + size, most also taking a `kind`):
- `energyBolt(r, g, b, size, style)` — glowing orb (see *Orbs* above). `style` picks what
  stands outside the sphere, named by the `ORB_*` constants: `ORB_PLAIN` (the bare orb),
  `ORB_FIRE` (a flame teardrop with tongues peeling off its edges), `ORB_RUNE` (a magic circle
  in the ground plane, the orb inside it), `ORB_ORBIT` (a tilted, precessing ring the orb passes
  through, with motes riding it), `ORB_HEART` (a heart beating lub-dub — charms),
  `ORB_ASTRAL` (a five-point star in an orbit of stars — the Astronomer), `ORB_SPIRIT` (a thin
  ghostly orb with wisps peeling off it), `ORB_TOXIC` (bubbles bursting on it, drops falling),
  `ORB_SWARM` (nanites on orbits of their own round a small core), `ORB_SAND` (arms of grit
  whirling out past its rim, grains shed), `ORB_MUD` (a lumpy wet glob flinging drops, with no
  light at all) and `ORB_SHOCK` (arcs crackling off it). The outer shape is what distinguishes
  bolts; inner detail is invisible at the size a projectile is actually displayed. **Whatever
  the style draws has to stand outside the body** — a feature inside the 0.90 x 0.68 sz ellipse
  is painted over and the bolt comes out plain. That is what happened to the old rune ring (at
  0.88 x 0.64 sz) and cloud lobes (the body's own colour at half alpha, behind an inked
  ellipse): four bolts each, all reading as featureless eggs.
- `laserBolt(kind, …)` — blaster bolt: a short capsule, round at the hitbox and drawn to a
  point behind, with a dissolving afterglow. Kinds: plain, prismatic fringes (Photon),
  rings of force pulsing off the head (Cyclops).
- `railSlug(…)` — a dense dart shedding electromagnetic coil rings that widen and fade
  behind it (Railgunner).
- `siphonVortex(kind, …)` — drain abilities as a travelling whirlpool, motes spiralling
  *into* a dark core: blood sheds drips, life drain beats a heart, soul drain stares back.
- `tether(kind, …)` — a pull tied back to its thrower (see *Pulls are tied to whoever is
  pulling*): tentacle, vine, spirit paw, spirit talon, death hook on a chain.
- `gorgonEye(petrify)` — Medusa's gaze as a blinking almond eye with a slit pupil, ringed
  by crumbling stone.
- `bladeSpinner(kind, …)` — thrown weapon tumbling end over end (axe, bone axe, katana,
  chef's knife, sword, femur, cursed blade, playing card). Sells the rotation with a
  swept arc band and silhouette ghosts rather than by smearing the shape.
- `flyingShaft(kind, …)` — shaft flying point-first (spear, arrow, poison arrow, blowdart,
  thorn, ice spike, void lance).
- `shuriken(r, g, b, size)` — four-bladed throwing star, one swept blade stamped at exact
  quarter turns, with a rimmed centre hole. Spin reads from a band swept across the blade
  tips; nothing is drawn on a radius out from the hub.
- `lobbed(kind, …)` — object on an arc (bomb, flask, shovel, hammer, horn, spiked mine,
  ice chunk, mud glob) with landing shadow, target ring and bounce.
- `aoeRing(kind, …)` — ground blast as filled shockwave bands over a darkened scorch.
  `kind` picks what it throws off: quake rubble, water, spores, flame, pressure rings,
  inward-falling motes, roots.
- `wave(kind, …)` — crescent sweep built from a real arc band whose centre is solved in
  the ellipse's own parameter space so it stays square to the travel direction at every
  heading. Kinds: wind, sand, sonic, flame, acid, impact, water.
- `chainProj(kind, …)` — thrown restraint: a grappling hook (or, `CHN_HOOK`, a single meat
  hook) on a twisted rope running all the way back to the thrower's hand; a tumbling manacle
  trailing swinging links; a loop of links spinning around a padlock.
- `bulletProj`, `fistProj` — small fast round; gauntleted punch.

**32 specialized `draw*` renderers** for one-off projectiles: lightning (`lightningBolt(r, g,
b, heavy)` — colour is a parameter so a storm reads yellow and a tesla coil reads arc-cyan;
`heavy` is chain lightning's thicker bolt), thunder strike, grasping dead, frost comet (ice
beam), bandage wad, tongue lash, boulder (faceted tumbling hull), shark jaw, bat swarm, shadow
bolt, inferno blast, geyser, wail, raise dead, and more. The fireball is an `ORB_FIRE` orb.

**Things to watch when editing this file:**
- **Kind constants must be defined above `registry`.** `registry` is a `val` built while
  the object initialises, in textual order, so a `private val FOO_KIND = 3` declared
  *below* it still reads `0` when `factory(FOO_KIND, …)` is evaluated — it silently
  renders the wrong kind. New factories and their constants go in the sections above the
  registry.
- **`fillPolygon` fan-triangulates from vertex 0, so it fills convex outlines only** — and
  the failure is silent, because the `strokePolygon` beside it still traces the true shape.
  A star fills as a lopsided blob with its notches bridged; a crescent fills its own hollow
  and reads as a slab. Anything built as a radius per vertex (star, sunburst, faceted hull)
  goes through `fillFan` with the centre those radii were measured from; a band built as an
  outer run plus the reversed inner run goes through `fillRibbon`. Run any client binary
  with **`GRIDGAME_POLYCHECK=1`** and every non-convex call site prints once — the
  projectile gallery covers the whole roster in a single pass. This caught eight renderers
  at once (the shuriken, both crescent blades, the holy star, two faceted hulls, the soul
  wisp's tail) plus three authored `Part` silhouettes.
- **A stroke of uniform width is a bar, whatever colour it is.** `strokeLineSoft` at
  `sz * 0.34` gave `ORB_FIRE` eight flat yellow paddles stuck round a disc — a cog, not a
  flame. Anything meant to taper is either a triangle (`fillPolygon`; a triangle is always
  convex) or a `fadeLine`, which narrows *and* fades to nothing.
- `fillArcBand` ramps alpha **along the sweep**, not radially. A radial falloff has to be
  built by nesting bands at constant alpha; using the ramp for it leaves one horn of a
  crescent bright and the other invisible.
- **A bend in a translucent line needs a joined stroke.** A zigzag or a curve built from
  `strokeLine` quads overlaps itself on the outside of every bend, and with any alpha below 1
  each overlap blends twice: the lightning bolts came out as chains of translucent rectangles.
  `strokePolyline` / `strokePolylineTapered` mitre the joins (see *Strokes are one band*).
- **Ink the whole silhouette, not only its leading edge.** A pale shape reads on sand and snow
  only by its outline. The wave crescents were inked along the front alone, with a body at
  35-45% alpha, and all eleven waves faded into the ground behind their front; the audit had
  Acid Spray at 4 strong pixels per 1000 on sand, and 32 once inked round and filled denser.
- **Never flicker a whole projectile toward zero.** `sin(phase * 8)` lands on an unrelated value
  every frame, so a flicker between 0 and 1 is a strobe, and on a dim frame on pale ground the
  shot isn't there. Flicker the core; keep the body up. A slow pulse is no better: every orb,
  the fireball and the inferno blast throbbed down to 20-30% three times a second.
- **The client never flies a projectile.** It only moves one to where the server says
  (`updatePosition`), so `getDistanceTraveled` is 0 in a match. The gallery, the bench and the
  audit fly theirs with `moveStep`, so none of them shows it: the rope measured itself by it,
  and in a real match no rope was ever drawn. Measure from `Projectile.originX/Y`, or key
  things to the ground as the electricity does.
- A block literal on the line after an expression is parsed as an *argument* to it
  (`val n = 9` followed by `{ … }` becomes `9 { … }`). Use a plain `var`/`while` at
  statement level rather than a `{ … }` wrapper.

To add a new projectile renderer:
1. Add an entry to the `registry` map in `GLProjectileRenderers` (`asRenderer(drawX)` for a
   `draw*` method, the factory call as it is for a pattern)
2. Either use a pattern factory (`energyBolt(r, g, b, size, style)`, `bladeSpinner(kind, …)`,
   …), add a `kind`/`Part` array if the object has its own silhouette, or write a
   specialized `draw*` method
3. The renderer receives screen-space coordinates (sx, sy) already transformed from world space.
   That point is the hitbox: draw the head there and trail anything elongated behind it
4. Check it in the gallery (below) — judge at the size the player sees, over all three
   terrain bands — and then on every map's real ground with the readability audit (below the
   gallery), which is where a pale shape on snow or sand shows up

#### Projectile gallery (dev tool)

```bash
bazel run //src/main/scala/com/gridgame/client:projectile_gallery -- out/dir
bazel run //src/main/scala/com/gridgame/client:projectile_gallery -- out/dir --bench
bazel run //src/main/scala/com/gridgame/client:projectile_gallery -- out/dir --focus=LIGHTNING,TENTACLE
```

`--focus=` writes one `focus_<NAME>.png` per type instead: four headings across (right, left,
up, down on screen) and four moments down, the projectile further along each row. It is the
loop for anything drawn along or behind the flight path — a tail, a tether, a bolt's channel —
which the contact sheets only ever show flying one way.

Renders every registered projectile type into contact-sheet PNGs (10 pages x 4 animation
ticks) plus an `index.txt` naming each cell. It also writes `impacts_NN.png` (projectiles sinking
into a wall block across the fade) and `flyers_00.png` (wall-passers crossing one). Each cell draws one projectile over real
isometric tiles banded dark stone / grass / sand, at `CAMERA_ZOOM`, with a 48-unit player
footprint box for scale — a projectile that reads on one ground can disappear on another,
and judging any of this at 1:1 flatters it by a third. Each cell advances the projectile to
**35% of its own effective range**, not a fixed number of steps: at six steps every
short-range type (a talon, a fang, a punch are all `maxRange` 3-4) was past its end of range
and drawn in its dissipation, at 30% alpha and 130% scale, which is not a state a player
ever aims at. This is the loop to use for any
projectile art change; it needs no server, no login and no match. Its target compiles only the
ten GL files it needs, so it keeps building while unrelated client code is mid-edit.

`--bench` times a screenful of projectiles against a ground-only baseline, so an art
change can be checked against the frame budget instead of guessed at. Measured on this
machine after the orbital-bolt pass: 16 projectiles cost **0.11 ms/frame** over a 0.68 ms
ground+post baseline — about 7us each, against a 16.7 ms budget. (The same 16 cost 0.157 ms
before that pass: an orbit ring drawn as two arcs is cheaper than the swinging tail and the
soft-stroke cloud it replaced.)

#### Readability audit (dev tool)

```bash
bazel run //src/main/scala/com/gridgame/client:render_audit -- --out=/tmp/audit
bazel run //src/main/scala/com/gridgame/client:render_audit -- --out=/tmp/audit --maps=the_snowglobe --what=projectiles
python3 scripts/render_audit.py /tmp/audit     # report.txt and worst_*.png (needs numpy and Pillow)
```

The gallery judges a projectile over three flat bands under the dark maps' grade. This judges
the whole roster and every projectile over every map's real ground — each walkable tile
covering 2% of its open ground, and each pool covering 2% of it — through the real
`GLGameRenderer`: the map's own background, grade, lighting, name plates, at the player's size.
A map whose ground and sky an earlier one already had (the four space arenas) is shot once.
Every picture is taken twice, as rendered and with what is judged taken out (a character keeps
its shadow, light and name plate and loses only its sprite; a projectile goes altogether), so
the difference between the two is exactly what it adds to the frame. The script measures that
difference as CIE76 colour difference in Lab: for characters, `edge` (the upper quartile over
the silhouette's outer 6px, which is what separates a figure from the ground); for
projectiles, `strong` (pixels per 1000 of its box differing by more than 20, what reads at a
glance). It writes the worst of each, and the worst on each map, as contact sheets beside the
same ground without them.

Measured at High (2026-09-23): every character's edge is at least 23 on the space maps'
circuit (Windwalker, Mudslinger, Serpent and Nanoswarm, teal and green on a teal floor, are the
lowest, still readable by their contour) and at least 39 on every bright ground. The weakest
projectiles on bright ground were down to 0.6-4 strong: the lightning family and the eleven
wave crescents, pale, translucent, and in lightning's case strobing. After inking the waves
round and joining the lightning's strokes, the lowest on any ground is 8 (the Sniper Beam, a
small inked dart on snow) and the 10th percentile on snow went from 18 to 30.

### Post-Processing
Settings in `PostProcessor`: `bloomThreshold`, `bloomStrength`, `vignetteStrength`, `toneMap`.
Bloom FBOs run at half resolution, plus a quarter-res pair for the wide glow. The blur is the
nine-tap Gaussian in five fetches (each pair of taps is one bilinear fetch between them). The
light map runs at quarter resolution: every light is a soft blob fading to nothing, which
bilinear upsampling reproduces exactly. Composite shader
uses screen blending for bloom and smoothstep vignette, and gates its optional work on
`uSharpen` / `uGrain` / `uWideBloom` so the quality tiers can drop it without a second
shader. The four-tap unsharp mask is the composite's most expensive part — four extra
full-resolution texture fetches per pixel. The film grain is hashed from the pixel and a frame
index that wraps: it used `sin()` of a value that grew every frame, and a GPU's `sin()` loses
its precision on numbers that size within minutes, turning the grain into bands.

**A bright map is graded differently from a dark one.** The composite's ACES curve lifts
mid-tones and pulls highlights down. That is what keeps the space maps legible, and it turned a
meadow pastel: authored grass (0.50, 0.81, 0.31) came out (0.62, 0.76, 0.45). So `GLGameRenderer`
sets the grade whenever the world's background changes, and for `sky`, `sea` and `snow`:
- `toneMap` 0: the art's own colours, only rolled off above 0.8 (`softClip`) instead of clipped.
  The shader branches on the uniform rather than mixing both curves for every pixel.
- bloom only above 0.97 luma, at 0.16 strength. At the dark maps' 0.8, sunlit sand and snow
  bloomed and screen-blended a haze over the whole frame.
- vignette 0.12, since 0.25 greys a snowfield's edges.
- `LightSystem.gain` 0.35 and an ambient of 0.625, which the composite's x1.6 makes exactly 1. In
  daylight the warm pool of light round every player reads as a spotlight on the grass.
  Explosions still flash.
The dark backgrounds (`space`, `cityscape`, `desert`, `ocean`) keep the old settings.

### Graphics quality tiers (`RenderQuality`)

The renderer is fill-rate bound, so `RenderQuality` exists to keep it playable on a weak
GPU. Set with `--quality=low|medium|high|auto`, `GRIDGAME_QUALITY`, or **F7** in game.
Default is `auto`: starts at High and steps down (never back up) after ~2s of frames slower
than 20ms, so an underpowered machine settles on its own; the first explicit choice — flag
or F7 — turns auto off.

`sceneScale` is the main lever. It sizes the scene/bloom/light targets, while the composite
still upscales to the display's real framebuffer and the HUD — and the name plates, health bars
and damage numbers over the world — are drawn on top at full resolution, so lowering it costs
sharpness in the world only, never in text. It lowers the CPU's work too: ovals take their
segment counts from their size on screen, so a world drawn at a third of the pixels is built
from a fraction of the vertices. It is
expressed against both the framebuffer and the logical window because the worst case is a
cheap machine driving a HiDPI screen: there the framebuffer is 2x the window, so the world
is being drawn at 4x the pixels the art carries (tiles are 40x56 magnified 1.6x). Medium
gives up that free 2x first.

Tiers also gate: bloom, the quarter-res wide bloom, composite sharpen and grain, the
dynamic light map, water reflections, the animated tile-overlay budget, background cache
interval, and particle emission rate.

Choosing Low outright (`--quality=low` / `GRIDGAME_QUALITY=low`, not auto) also sets
`prism.allowhidpi=false`, so the JavaFX menus draw at 1x on a HiDPI screen and the OS scales
them up: softer text, a quarter of the pixels per repaint, and a quarter-size window surface
pool (see Client Memory & Performance). It must be set before JavaFX starts, which is why
auto — which only steps down mid-match — can't do it.

### Key Design Decisions
- **A tile's form decides how it is drawn** (`TileForm` in `Tile.scala`). Walkable *ground* and
  flat *pools* (water, lava) are drawn in the ground pass. *Blocks* (walls, cliffs, the void) and
  *props* (trees, rocks, bushes, snowmen) are drawn in depth order with the players. A prop's
  sprite leaves the ground round it showing, and the ground pass lays under it whatever it is
  standing in (`Tile.groundUnder`: its own `ground` if a neighbour is that, else the first
  walkable neighbour, else its own). So one palm serves a beach and a meadow, and a tree deep in
  a forest still has grass under it. Only blocks cast the edge shadows; a prop carries its own
  round shadow. Water used to be a raised 14px block, which read as an aquarium, not a pond.
  Frames mean different things per form: ground and props have four variants picked by
  position, pools and blocks four animation frames. The map editor draws the same passes.
- **Tiles are culled against the visible diamond, not its bounding box** — the screen rect
  maps to a diamond in world space, whose AABB holds ~2.7x as many cells as are on screen.
  `render` computes bounds in the projection's own axes (`u = wx - wy`, `v = wx + wy`) and
  clips each row exactly; verified against brute force over 400 camera positions with zero
  tiles missed and 6% overdraw. Entity cells walk a few rows further (`entPad`) because a
  player sprite and its name plate hang above their own cell.
- **The ground is drawn as opaque diamonds** (`GLTileRenderer.drawDiamond`) — every ground and
  pool tile is exactly its diamond, so the ground pass draws each tile as a quad through the four
  corners, with blending off, and covers every pixel once. Drawn as the cell's bounding rect it
  shaded and blended the whole screen twice, since each tile's transparent corners lie under its
  neighbours (7.4 → 3.7 million fragments a frame at 2560x1440). Neighbours meet exactly because
  their shared corners are computed from the integer lattice (`u = wx - wy`, `v = wx + wy`), so
  they are the same floats. The pixel-art edge is a staircase either side of the true edge, so
  the pixels just inside it sample texels just outside, which are transparent — and black in the
  PNG: `GLTexture.padTransparent` gives those texels their neighbours' colour at load. `TileTest`
  checks every flat tile is its whole diamond and nothing outside it; `GRIDGAME_HOLECHECK=1`
  shows any gap in magenta. Blocks and props are still trimmed quads (`getTrimmedRegion` /
  `getTrimTopPx`), measured per cell at load, since they rise above their diamond.
- **The background is only drawn where the map ends** — every cell's ground or block covers its
  own diamond (`TileTest` checks a block covers its footprint), so mid-map, which is most of a
  match, the background can't be seen at all. It used to be redrawn every few frames and blitted
  every frame anyway: a fifth of the frame's pixels, all painted over.
  `IsometricTransform.viewInsideWorld` decides it from the screen's four corners.
- **The cached background is flipped on the way out** (`flippedRegion`). It is drawn into its
  target with the world's projection, which puts the top of the screen at v = 1, and it was
  blitted with the plain full region, top edge v = 0: every background came out upside down —
  hills and trees hanging from the top of the screen, clouds along the bottom — wherever the edge
  of a map let it show.
- **The camera sits on the pixel grid** (`GameCamera.update`'s `pixelsPerUnit`, the scene
  target's pixels per unit). The terrain is pixel art sampled nearest-neighbour at a scale that
  isn't a whole number (80 texels onto 64 or 128 pixels), so at a sub-pixel offset which texels
  get doubled or dropped depends on the offset, and with the camera gliding between pixels every
  tile's detail crawled whenever anyone moved. Entities still move smoothly: only the offset is
  rounded, never their positions. The mouse reads the same offsets. `CameraTest` pins it.
- **Name plates, health bars and damage numbers are drawn over the finished frame** — after
  the composite, at the display's full resolution, with the world's projection so each lands
  where it did. In the scene they were only as sharp as the scene (a third of the display's
  pixels at Low), dimmed by the light map and tinted by the grade: a white damage number on a
  dark map was muddy grey, a pale yellow one on snow all but invisible. Damage numbers now have
  a heavy eight-copy outline; a name on its dark plate needs no outline at all, which was four
  more copies of every glyph. Name text is 12 world units high (it was 14 with a wider plate,
  and a crowd's plates covered the heads of whoever stood a row behind). The overlay fades in
  with the match, as the world does.
- **Fonts are rasterized at the display's pixel density** (`GLFontRenderer`'s `pixelScale`,
  made again if the window moves to another screen): rasterized at 1x and magnified, all text
  on a HiDPI screen — the HUD included — was soft.
- **Strokes are one band** — `strokeOval`, `strokeArc`, `strokePolygon`, `strokeRect`,
  `strokeRoundedRect` and `strokePolyline(Tapered)` build one strip whose neighbouring segments
  share their edges (ovals as a band between an inner and an outer ellipse, polygons and
  polylines mitred at every corner). They used to be a separate quad per segment, which overlap
  on the outside of every bend and leave a notch inside it: with any alpha below 1 every joint
  blended twice, so rings, auras and shields came out beaded, the wave crescents' rims dashed,
  and lightning a chain of translucent rectangles. Anything drawn as a chain of `strokeLine`s
  that bends and isn't opaque has the same fault — use `strokePolyline`.
- **Oval segment counts follow their size on screen** (`ShapeBatch.curveSegments`): enough that
  no chord strays a pixel from the curve, and no more, from `pixelsPerUnit`, which the renderer
  sets for each pass. A ring 30 units across was a visible sixteen-sided polygon on a HiDPI
  screen, and a spark a few pixels wide was given twenty segments. Now big ovals get more and
  small ones fewer, and at Low the whole world is drawn smaller, so a weak machine builds a
  fraction of the vertices. A request under 10 is a shape (a hexagon drawn as a 6-segment oval)
  and is left alone.
- **A busy frame keeps its strongest lights** (`LightPool`) — it holds 96, and when full a new
  light replaces the weakest if it is stronger. It used to drop whatever came after the 96th, in
  depth order: an explosion's flash, added last, went missing in exactly the fights that have
  explosions, and lights blinked as entities moved between cells. `LightPoolTest` pins it.
- **In daylight a shot carries no light** (`_projectileLights`, off for `sky`, `sea` and
  `snow`): its pool only brightened the sand or snow round it toward white, washing out the pale
  projectiles in exactly the place they were hardest to see.
- **The character atlas is mipmapped** (two levels, 128px frames down to 32) and its sheets are
  padded like the tiles. A frame is drawn at 77px at High on an ordinary screen and 42px at Low;
  minified that far without a mip chain, bilinear filtering skips texels, and the one-texel
  contour that keeps a character readable broke up and shimmered as it moved.
- **The character atlas grows instead of reserving every slot** — sized for all 112
  characters it would hold 128MB of texture memory for the whole session however few
  characters a match uses. It starts at 16 slots (16MB) and doubles to at most 64. A grow
  re-uploads the sheets already in it, and retires the old texture for `disposeRetired()`
  to free at the top of the next frame — never mid-frame, where a batch may still hold
  queued vertices naming it.
- **A filled outline is only as good as its triangulation** — `ShapeBatch.fillPolygon` fans
  from vertex 0, which is right for a convex part and wrong, silently, for everything else:
  the stroked outline beside it keeps tracing the true shape, so the result reads as a
  rendering glitch rather than as geometry the renderer cannot express. That is why a
  throwing star built as a polar radius per vertex came out as a crumpled dart, and why
  both crescent blades filled their own hollow and read as grey slabs. `fillFan` (fan from
  the centre the radii were measured from) and `fillRibbon` (quad per segment across a
  band) cover the two shapes that kept being asked for, and `GRIDGAME_POLYCHECK=1` makes
  the failure loud instead of silent.
- **A projectile's silhouette carries its identity, not its palette** — the pattern
  factories used to describe shapes in polar form (a radius per vertex) or as a stroked
  line from the caster, so a whole family came out identical: every melee weapon was the
  same spinning lens, every thrown object the same grey disc, every wave the same
  triangle, and a spear or arrow was a ~190px hairline drawn `worldLen` world units long.
  Recognisable objects are now authored as convex `Part` silhouettes in a local frame and
  stamped per frame (see *Projectile Rendering System*), and the family factories take a
  `kind`. Colour differentiates within a family; shape differentiates between them.
- **A pull is tied to whoever is pulling** — tentacles, vines, leashes, chains and ropes run from
  the hitbox back to the thrower (see *Pulls are tied to whoever is pulling*). A grab flying free
  was a glove somebody threw.
- **A bolt's channel is fixed to the ground** — its kinks stay where they were drawn as the head
  flies on (see *Electricity is fixed to the ground*); built relative to the head and re-rolled
  every few frames, it was a sawtooth stick jumping about.
- **An orb glows from inside and streams a comet tail** — the colour strongest at its rim,
  lightening to a hot core, and a tail as wide as it is (see *Orbs*). Lit from outside it was a
  glass marble; pulsing its alpha, a strobe.
- **A projectile's head sits on its hitbox** — nothing is drawn ahead of `(sx, sy)`; trails,
  tethers and wakes stream out behind it and fade (`fadeLine`). Beams, tethers, lightning,
  the rocket and the jaw used to run a line several tiles ahead of the hitbox to a disc,
  which both looked like a snake and showed the head arriving 100–150px before the damage.
  The talon and the blood fang ran their claws and fangs forward the same way; they now
  close on the hitbox with the foot and the gum trailing behind.
- **An energy bolt's identity is whatever stands outside the orb** — every `energyBolt`
  draws the same 0.90 x 0.68 sz ellipse, so a style's shape only exists where it clears
  that ellipse, and only if it is not a uniform-width stroke. Three of the five styles
  failed one of those tests at once: the rune ring was drawn *inside* the body, the cloud's
  lobes were the body's own colour behind an inked ellipse, and the fire tongues were
  9px-wide soft strokes. Eleven bolts across nine characters came out as plain orbs or as
  a disc with paddles. The fourth, the soul wisp, failed differently — a tapering tail two
  and a half diameters long, swung side to side by a sine, which with a round head in front
  of it is a tadpole swimming, and which five characters' primaries all shared. Those bolts
  now sit inside a tilted ring they pass through (`ORB_ORBIT`), which reads as an orbit
  precisely *because* the orb occludes half of it.
- **Projectile collision uses the drawn tile extents** — `Projectile.getCellX/Y` is
  `floor(x + 0.5)` because tiles are drawn centred on integer coordinates. Truncating put
  walls and map edges half a tile off their sprites, which is what made projectiles glitch
  into walls and off the map. A stopped projectile then sinks into the face it struck rather
  than blinking out, and wall-passers are drawn flying over the terrain.
- **Players are hit at the centre of their cell** — for the same reason: a player is drawn,
  and fires from, world `(x, y)`, so hits, splash, blasts and slams measure to there
  (`Projectile.withinPlayer` / `distanceToPlayer`). Measured from `(x + 0.5, y + 0.5)`, a shot
  from one side connected a cell sooner than from the other, and a ground slam reached a cell
  further south-east of its caster than north-west. `CombatTest` pins it.
- **Standard alpha blending for projectiles** — additive blending (`GL_SRC_ALPHA, GL_ONE`) makes projectiles invisible on bright terrain and removes all visual distinction. Standard blending with high alpha (0.7-0.95) produces solid, visible, distinct shapes. Bloom post-processor handles glow naturally.
- **GLFW window swap** — hiding JavaFX Stage and creating a GLFW window avoids FBO→WritableImage pixel-copy overhead. Both use Cocoa NSWindows on macOS and coexist safely.
- **AnimationTimer game loop** — fires on the FX Application Thread (main thread on macOS), which is required for both GLFW and OpenGL calls. No threading complexity.
- **JavaFX UI retained** — Login, lobby, character selection, and scoreboard remain in JavaFX. Only in-game rendering uses OpenGL.

## Client Memory & Performance

The target is an old machine with little RAM. Two dev tools measure it, and any change that
could move these numbers should be checked with them rather than guessed at:

```bash
# A busy match (16 players, 150 projectiles of every type, deaths, explosions) through the
# real GLGameRenderer in a real window, no server: frame CPU/GPU time, bytes allocated per
# frame on the render thread, GC count, process footprint. --quality=, --players=,
# --projectiles=, --w= --h=, --screenshot=out.png. --effects puts a status effect on every
# player (frozen, stunned, poisoned, burning, rooted, slowed, boosted), which the default
# scene has none of — off by default so the numbers below stay comparable. --barriers has
# every fourth player hold a barrier up, turning and being struck, half of them allies, and
# --traps scatters 30 traps of every kind, half ours and half an enemy's, some arming, some
# going off. --divider raises the wall a Teams match opens with, beside the local player, and
# --ceasefire holds every attack the way a free-for-all's opening does (=<seconds> on either to
# watch it end).
bazel run //src/main/scala/com/gridgame/client:render_bench -- --quality=low

# The JavaFX menus: the character select screen, then a match start (stage hidden), a second
# visit, and the same screen idle — live/committed heap, CPU, footprint for each phase.
bazel run //src/main/scala/com/gridgame/client:ui_bench
```

On macOS both print the footprint as Activity Monitor counts it, split into `graphics`
(GPU memory), `IOSurface` (window surfaces), and `VM_ALLOCATE` (mostly the Java heap).

### In game: the render thread allocates next to nothing
A busy frame allocated 167KB (10MB/s) and now allocates ~3.5KB, with no GC during a
15-second bench run where there were nine. What it took, and what to keep that way:
- `EntityCollector` is a flat grid of linked lists, not a `Map[Long, ArrayBuffer]` — the
  boxed cell key looked up for every visible cell was two thirds of all allocation, and
  cells the renderer removed from the map never returned their buffers to the pool.
- `GLProjectileRenderers.Renderer` is a trait, not a `Function5` (which boxes its floats).
- Batches map and write through raw addresses (see Batch Management).
- `GLFontRenderer` looks glyphs up in a flat Latin-1 table / `LongMap` — a
  `getOrElseUpdate` builds a closure per character drawn.
- Per-frame state lives in primitive arrays updated in place: no `java.lang.Float` map
  values, no destructured tuples in a loop.

The same bench measured frame-build CPU ~20% lower (2.9 vs 3.6ms at Low).

### In game: where the GPU's pixels go
`GRIDGAME_GPU_PROFILE=1` makes the render bench count, per phase of the frame, the fragments the
GPU shaded (occlusion queries) and the time it took. **Compare fragments, not time**: the weak
GPUs this targets are fill-rate bound, so fragments are their cost, and a fragment count is the
same on every machine. Time is only meaningful on a desktop GPU; Apple's GPUs are tile-based
and run a whole render pass at once, so time inside a pass lands on whichever phase closed it,
and can come out negative. (Their GL answers timestamp queries with zeros, which is why it
uses elapsed-time queries.)

Mid-map on the Meadow, 16 players, at High on a 2560x1440 framebuffer, the fixed costs went:
background redraw + blit 6.7 → 0 million fragments a frame (it can't be seen mid-map), ground
7.4 → 3.7 (opaque diamonds, once per pixel), light map 1.5 → 0.4-0.6 (quarter res) — about
30 → 19-23 million in all, depending on what is in view (props and projectiles are what
varies). What is left is the post chain (6.9: the full-resolution composite and the bloom
passes) and whatever the scene holds; 150 projectiles are ~30 million on their own. The name
plates, health bars and damage numbers are ~1.3 million at full resolution whatever the tier,
the price of their staying sharp.

Measured back to back on the Hive (this machine, High, 2560x1440), render-thread CPU per frame
went 5.6 → 4.9 ms with 150 projectiles and 3.4 → 2.7-3.1 ms with 30, and GPU frame time
13.6 → 12.3 ms and 11.9 → 9.5-10.2 ms; allocation stayed at 3.6KB a frame with no GC. The CPU
came from oval segments sized to the screen, arcs rotated incrementally instead of a sin/cos per
vertex, and a four-times larger staging buffer (a busy frame flushed every few projectiles).
Bench numbers drift 20% or more between runs on this machine as it warms: compare runs made
back to back, and repeat them.

### Menus: every changed frame repaints the whole window
JavaFX on macOS presents the whole window for any change, however small, so the menus' cost
is set by how often something on them moves, not by what moves: a 10px square animating at
60 fps on an otherwise empty full-screen window burns ~60% of a core on a 5K display. Rules
the screens follow:
- **Animations are stepped and pause when nobody's looking.** `steppedLoop` (ClientMain)
  runs looping decoration at 12 fps; the character panel's ability previews run at 30 fps
  with the sprite steps on the same pulses. All of it holds still while the window is in
  the background or has had no input for 30s (`UiActivity`). Measured interleaved on a 5K
  display, character select went from 50% of a core to 40% in use and 1% idle (it used to
  cost the same idle as in use), and the idle login screen from 35-90% to ~2%.
- **No per-frame images.** `SpriteGenerator` keeps whole sheets — thumbnails decoded at the
  size the grid draws them, on JavaFX's background loader — and draws frames with a source
  rectangle. It used to keep 16 `WritableImage`s per character forever: 117MB of heap and
  1792 GPU textures after browsing the grid.
- **Only on-screen grid cells are drawn**, when their frame changes.
- **`ViewportCache.disable` on any `ScrollPane` around animated content.** ScrollPane's skin
  caches its viewport as a bitmap; around canvases that animate, every frame re-renders the
  whole viewport into a texture as big as the viewport and draws it again.
- **Screens don't outlive themselves.** `switchScreen` unregisters the listeners that update
  a screen's controls (they held the whole scene graph, and the lobby chat kept rebuilding
  off screen through the next match), and `showGameScene` swaps the hidden stage's scene
  for an empty one and drops the sprite cache, so none of the menus stay resident in a match.

What none of this fixes: a visible JavaFX window on macOS builds up a pool of about 15
window-sized IOSurfaces as it keeps presenting — ~650MB on a 5K display, ~120MB at 1080p —
and only gives it back when the window is hidden (a match start does). It is Core
Animation's pool behind the `CAOpenGLLayer` JavaFX draws into; nothing in JavaFX's API sizes
it, a resize doesn't release it, and JavaFX 21.0.12 and 23.0.2 behave exactly like 21.0.1
(measured interleaved). Fewer presents only delay it; Low quality's 1x menus quarter it. The
GLFW game window's surfaces don't grow. When measuring the menus, keep the window visible
and unobstructed: an occluded window presents less and flatters every number.

### JVM heap
The heap is capped and started small (`-Xms64m -Xmx768m` in the client's `jvm_flags`, and
passed through `--java-options` by `scripts/build_macos_app.sh` / `build_windows_exe.ps1`);
the live set is well under 100MB. `ClientMain.tuneHeap` sets, at runtime so it applies to
`java -jar` too, `G1PeriodicGCInterval=30000` and `Min/MaxHeapFreeRatio=10/30`: HotSpot only
returns memory after a concurrent cycle or full GC, which a game this light on allocation
rarely triggers, so without them the heap stayed at its high-water mark in the menus.

## Asset Generation

Sprites are pre-rendered images loaded at runtime.

### Tile Sprites
```bash
# Requires Pillow: pip install Pillow
python3 scripts/generate_tiles.py                  # -> sprites/tiles.png
python3 scripts/tile_gallery.py /tmp/tilegallery   # look at it
python3 scripts/tile_gallery.py /tmp/tilegallery --map worlds/the_meadow.json --at 60,52
```

- **Output**: `sprites/tiles.png`, one 80x112 cell per tile id across (2x the 40x56 display
  cell) and four rows down, 42 tiles. The diamond a tile stands on is centred at (40, 92).
- **The rows depend on the tile's form** (`TileForm`, mirrored in the script's `TILES`): four
  variants for ground and props, four animation frames for pools and moving blocks, one frame
  four times for a block that doesn't move.
- **Drawn in MapleStory's style with the characters' own kit**: `sprite_base`'s `cel`, `blob`,
  `ink`, `shade` and `lit`, supersampled 4x and resampled down. Flat cel fills, one hard shadow
  tone away from the upper-left light, outlines in a dark version of each part's hue. Round trees,
  fat spotted mushrooms, a scalloped grass lip over brown soil on a cliff.
- Three rules specific to terrain, all from how the renderer tiles it:
  - **Ground and pools are seamless.** They are cut to the same hard-edged diamond the tileset
    has always used, so neighbours meet exactly. Nothing on them is outlined or shaded toward an
    edge, because any mark that touches the edge of one tile shows up as a grid across a field
    of them. Details sit in the middle.
  - **A block only inks its bottom edge.** Its top meets its neighbours' and its side faces are
    covered by the block in front, so a line anywhere else draws a seam between every pair of
    blocks in a wall. What is on a side face shows only on the outside of a mass of blocks, which
    is exactly where a cliff's grass lip belongs.
  - **Walkable ground stays quiet.** It covers most of the screen and everything has to read on
    it. The first pass sprinkled little flowers and light flecks on every grass tile, and in game
    the meadow read as confetti.
- Props can be drawn bigger than they are authored (`PROP_SCALE`, through `PropDraw`), which scales
  about the prop's foot but leaves line weights alone.
- Water and ice have no tile overlay in `GLGameRenderer.drawTileOverlays`: the tiles animate and
  shine on their own. The old glints, ripples and frost needles had never actually been drawn,
  because they were collected only for walkable tiles. Once pools were drawn with the ground they
  turned a sea into static and a frozen pond into flocks of white birds. Lava keeps its overlay.
- **Judge a change in the gallery, then in the game.** `tile_gallery.py` writes a contact sheet,
  each tile as a patch in a field (seams and grids show up here), little scenes with characters
  in them for scale, and optionally a window of a real map. The engine's grade still changes
  everything, so finish with `render_bench -- --map=... --at=x,y --screenshot=out.png`.
- `TileTest` checks the atlas has a column for every tile and draws each as its form says (a
  ground tile covers its diamond, a prop leaves its ground showing).

### Maps
Seven maps in `WorldRegistry` (display names come from the file names): four space arenas,
**The Cell, The Hive, The Nexus, The Colony** (circuit floor, starfield void, obsidian border,
slime blocks for cover), and three bright ones, **The Meadow, The Lagoon, The Snowglobe**.

Every map has the same shape, because it plays well: a round arena of about 0.39x the world's
width in radius, in a square world, closed off by terrain nobody can cross, with cover in rings,
arcs and blocks over 3-8% of the arena. The bright three are generated by
`scripts/generate_maps.py` and differ in what they are made of:

| Map | Size, arena | Ground | Cover | Beyond the arena |
|---|---|---|---|---|
| The Meadow | 120, r 46 | grass, flower patches, dirt paths (a wheel round a pond) | fairy rings of trees with mushrooms inside, giant mushrooms, grass-topped cliff outcrops, hedges, rocks | a forest behind a flowering hedge; `sky` |
| The Lagoon | 130, r 50 | sandy beach round grassy middle, boardwalks out from a bridged pool | palm groves, rock pools rimmed with coral, a ring of broken columns, rocks | shallows, then deep sea; `sea` |
| The Snowglobe | 100, r 38 | snow, a frozen pond with a fir in the middle, icy patches | snowmen round the pond, snow forts (short ice walls facing the middle), L-shaped ice blocks, pine groves | a pine forest; `snow` |

- **Symmetric under all eight of the square's symmetries.** Every decision about a cell is made
  from its offset to the centre with the signs dropped and the axes sorted. That makes a Teams
  match the same match from either half (the divider is the centre column), and
  `WorldMapsTest` checks the three are mirror images across it. The four older maps are centred
  half a cell off the divider's line, so they are only nearly symmetric.
- **Checked before writing**, as `WorldMapsTest` checks every registered map: spawn points on open
  ground with open ground on all four sides, none on the divider's column, as many in each half,
  and all open ground one region. Spawns come from slots in one eighth of the arena, mirrored
  into the other seven. `--preview <dir>` draws each map top-down.
- **Backgrounds.** `sky` is MapleStory's: blue paling to the horizon, fat outlined clouds, rolling
  hills with round trees on them. `sea` is open water to a horizon with islands on it: the
  Lagoon's sea runs to the edge of the world, and the sky's green hills behind it read as the
  sea stopping at a field. `snow` is winter hills with firs, with snow falling in front of the
  world. That is the only weather drawn: `spawnWeatherParticles` had never been called.
  `space`, `cityscape`, `desert` and `ocean` are as they were. See Post-Processing for how the
  bright backgrounds are graded.

### Application Icon
```bash
python3 scripts/generate_icon.py   # -> sprites/icon_wizard_{128,256,1024}.png + sprites/icon_wizard.ico
scripts/generate_icns.sh           # macOS-only: -> sprites/icon_wizard.icns (from the 1024 master)
```
Renders the wizard's front-facing frame (via `generate_wizard.py`'s `draw_wizard`) standalone,
crops/centers it, and exports at icon sizes. It draws through `sprite_base.ScaledDraw` at
`RENDER_SCALE` and applies the same `finish_frame` detail pass at each output size, so the
1024px icon is rendered at native resolution rather than a 64px sprite blown up 16x.
Used for the JavaFX Stage/Taskbar icon
(`ClientMain.loadAppIcons`/`setDockIcon`, `MapEditorApp`), the GLFW in-game window icon
(`GLWindow.setIcon`), the `.icns` consumed by `scripts/build_macos_app.sh`, and the
`.ico` consumed by `scripts/build_windows_exe.ps1`. Pillow writes `.ico` directly
(cross-platform); `.icns` needs macOS's `sips`/`iconutil`, hence the separate script.

### Character Sprites
The original 12 characters each have a dedicated generator script (`scripts/generate_<name>.py`;
the Spaceman's is `generate_spaceman.py` -> `sprites/character.png`). The other 100 live in seven
category files (`generate_{elementals,undead,medieval,scifi,beasts,mythological,specialists}.py`)
and are generated in batch.

```bash
# Individual character (original 12)
python3 scripts/generate_gladiator.py   # -> sprites/gladiator.png
python3 scripts/generate_wizard.py      # -> sprites/wizard.png
# etc.

# Batch generate the other 100 (all ~10s, pure Pillow, no numpy)
python3 scripts/generate_all_new_characters.py
```

Each sprite sheet contains 4 directions x 4 animation frames. Frame 0 of each row is also the idle
pose (the renderer shows it whenever a player stands still), so the walk is stand / stride / stand /
stride.

**Every script goes through `sprite_base.generate_character`** — the individual
ones only own their `draw_<name>` function, they do not run their own render
loop. That single chokepoint is what lets a rendering change apply to all 112
characters at once, so keep it that way rather than re-adding a per-script loop.

#### Judging a change: the sprite gallery

```bash
python3 scripts/sprite_gallery.py /tmp/spritegallery                  # contact sheets
python3 scripts/sprite_gallery.py /tmp/spritegallery --chars wolf,bear
python3 scripts/sprite_gallery.py /tmp/spritegallery --detail         # 4x4 per character
```

The review loop for character art, in the same spirit as `sound_gallery.py` and
the projectile gallery: 112 characters cannot be judged one at a time, and the
faults that matter are the ones that only show up when they sit side by side —
a roster where forty characters share one bald head, or where every silhouette
is the same rounded slab. Each cell draws the character at the size the player
sees (`PLAYER_DISPLAY_SIZE_PX` 48 x `CAMERA_ZOOM` 1.6 ~= 77px) and again at 2x,
over the three terrain bands it has to read against. **Run it after any change
here**; judging a sprite at its 128px sheet resolution flatters it, and judging
it on one ground hides contrast faults. `sprite_base.render_sheet(draw_func)`
returns a sheet as an image without writing it, for a scratch preview while
iterating on one character.

#### Style: MapleStory

The roster is drawn in MapleStory's style, and each rule of it lives in one place in
`sprite_base`, so a new character gets it by using the kit rather than by imitation:

| Rule | Where it lives |
|---|---|
| **About 2 heads tall**: a huge round head (12.4 x 11.2 units, crown at y ≈ 9) over a small slim body 22 units from chin to sole, short legs, small round shoes. With the feet at y = 54 of a 64-unit frame, a taller figure is only possible by shrinking the skull | `rig` |
| **Eyes are the face**: a tall white nearly filled by the iris, the iris darkening toward the top, a heavy upper lash flicking out at the outer corner, a big catchlight upper-left and a small one lower-right. No nose; a tiny mouth; thin high brows | `ms_eye`, `ms_brow`, `ms_mouth`, `head_face` |
| **Hair is silhouette**: one mass of crown, fringe and side locks with a gloss band across the crown | `rig_hair` + `HAIR_STYLES` |
| **Flat cel shading**: every shape is a flat fill, one hard-edged shadow tone on the side away from the light (upper left), an optional hard highlight. No gradients, no noise | `cel` |
| **Outlines dark but never black**: each part is lined in a dark, slightly richer version of its own hue | `ink`, applied automatically by `ScaledDraw` |
| **Gear is oversized**: staffs taller than the wearer, hats wider than the shoulders, shields half the body | by convention, and the gear helpers (`blade`, `spear`, `bow`, `round_shield`) |

**The proportions are deliberately chibi, and that is not a fault to fix.** An earlier pass
moved the rig to realistic thirds and it was a straight downgrade: the sprites came out small,
spindly and interchangeable, and the roster lost its charm. Do not shrink the head.

#### The kit (`sprite_base.py`)

Everything is drawn as polygons (`Poly`, `Ell`, `RRect`, `Limb`), because a polygon maps exactly
through `ScaledDraw`, can be shifted (which is how `cel` finds a shape's shadow side), and has one
continuous outline — a limb built as a polygon plus a separate round cap had a line drawn across
its tip.

| Helper | What it is for |
|---|---|
| `cel(draw, shape, color, sh=, hi=, regions=)` | fill flat, lay the shadow band (`sh` is how far the lit face shifts toward the light), optional highlight rim, extra `(shape, colour)` regions clipped to the shape, then the ink line on top |
| `blob(draw, shapes, color)` | the union of several shapes as **one** cel-shaded mass with one outline — beards, clouds, fur ruffs, foliage, smoke. Drawing overlapping ellipses one by one inks a line across every seam |
| `draw.sub()` / `draw.merge(layer, clip=)` | a transparent layer in the same coordinate space; drawing `fill=CLEAR` on it erases. How hoods cut their face opening and how `cel` cuts a shadow band |
| `shade(c)`, `lit(c)`, `ink(c)`, `mix(a, b, t)` | one cel step darker (hue-shifted toward violet, not toward mud), one step lighter, the line colour, a blend |
| `Fixed(r, g, b)` | an outline colour used exactly as given, never re-inked from the fill |
| `rig` + `rig_legs` / `rig_torso` / `rig_robe` / `rig_arms` / `rig_hand` / `rig_head` / `rig_hair` / `rig_hood` / `rig_cape` / `rig_belt` | the humanoid, see below |
| `head_skull`, `head_face`, `face_anchor` | a head without the standard face, a face without the skull, and where the eyes sit — for masks, visors, beards and eyepatches |
| `blade`, `spear`, `bow`, `round_shield`, `gem`, `xform` | gear authored once in a local frame (+x along the object from the grip) and placed with `xform(pts, x, y, angle, scale, flip)` |
| `flame`, `cloud`, `crystal`, `bolt`, `leaf`, `sparkle`, `star` | effects in flat cel tones |
| `creature_setup`, `eye_pair`, `quad_legs`, `draw_paw`, `rot_pts` | the non-humanoids: frame layout, a pair of eyes (far one narrowed in profile), a quadruped's trot |

A humanoid is `rig(...)` plus the rig calls, with its costume hung off the anchors
(`shoulder(side)`, `hand(side)`, `hand_at(r, side, reach, lift, out)`, `foot(side)`, `hip(side)`,
`sh_y`, `waist_y`, `hip_y`, `head_cy`, `hx`) in between. `build` scales the body's width — 1.3 is a
bruiser, 0.9 a waif — and `head` the skull, for the few whose hat needs headroom. `r.d` is the facing
(-1/0/+1), `r.back` the up view, `r.near(side)` whether a limb is on the camera side, `r.ph` the
stride phase. Draw order changes with the facing, which is what the `layer` arguments are for:

```
rig_hair(layer="back") -> rig_cape(layer="under") -> rig_legs -> rig_arms(layer="far")
  -> rig_torso / rig_robe -> rig_arms(layer="near") -> rig_head(hair=, style=) -> hat
  -> rig_cape(layer="over")
```

The far arm is behind the torso in profile; long hair and capes are behind the body head-on but
over it from behind. Anything a hand holds is drawn after the arm and before `rig_hand`, so the
fingers close over it. Pass `hat=True` to `rig_head`/`rig_hair` when something is worn on the
crown: the gloss band is left off and the brows are drawn so the hat can sit on them.

Things learned the hard way, all of which the gallery caught:

- **Everything around the head is sized to the head.** `rig_head`, `rig_hair` and `rig_hood`
  derive from `r.head_rx`/`head_ry`; anything hand-placed (a helm, a crown, a mask) has to be
  checked against it, or the headgear ends up too small for its own skull.
- **There is no headroom.** The crown sits near y = 9 of the 64-unit frame, so a hat, crest or
  flame drawn above it has about 9 units before the top clips it. A character whose silhouette
  lives above the skull needs a smaller skull (`rig(..., head=0.9)`), not a taller hat.
- **Streaming hair must fall.** Hair locks that rise away from the head read as horns or wings at
  77px (the banshee came back horned twice). Start every lock at the head and let it fall, curling
  at the tip.
- **Draw order is load-bearing.** A glow painted *after* the thing it lights swallows it; auras,
  wings, tails and back hair go behind.
- **Value contrast, not just hue.** Three near-identical darks merge into one blob at 77px (the
  gorilla, the first death knight and magma knight were both "dark ball with horns"). Give every
  character one shape nobody else in its category has — the magma knight's helm is a volcano.
- **Claws and fangs authored at 3.5 units land at four screen pixels** and read as a row of shark
  teeth. Keep them ~2.
- **A quadruped seen dead-on has no silhouette.** The DOWN/UP rows of the animals are the animal
  coming at (or leaving) the camera, head biggest, body falling away behind.
- **Similar creatures need different species, not different colours.** The Raptor is a bald eagle
  (white head, brown body), the Hawk a slate-blue falcon with a barred chest, the Griffin a gold
  eagle-lion, the Phoenix a bird made of fire.
- **`rig(bob=)` takes the bob for this frame**, a number, not the per-frame list.

#### Rendering pipeline (`sprite_base.py`)

Draw functions author in a 64x64 space with the feet at y = 54. They never draw at that size:

```
ScaledDraw onto a 256px canvas  →  Lanczos down to 128  →  finish_frame  →  quantize
   (supersampling)                                          (contour ring)    (255-colour palette)
```

- **`ScaledDraw`** proxies `ImageDraw` and multiplies every coordinate, radius and stroke width.
  It also re-inks outlines: any dark outline on a filled shape is replaced with `ink(fill)`, so
  even an old call site passing `OUTLINE` gets a coloured line. Box primitives map `x1` to
  `(x1 + 1) * scale - 1` so a scaled rectangle exactly covers its source pixels; `point` fills a
  whole `scale x scale` block.
- **`finish_frame`** is just `add_contour`: a one-pixel ring outside the silhouette in a darker
  version of the edge it borders, laid *behind* the frame so the anti-aliased edge survives. It
  is what keeps a character readable over both pale sand and dark water. The shading itself is
  authored in the art by `cel`: the previous image-space pass (bevel, rim light, occlusion ramp
  and a fixed grain field) was painterly by design and fought flat colour everywhere.
- Sheets are saved as a **255-colour palette PNG** with alpha in `tRNS`. Palette alphas ≥250 are
  snapped to 255 (the octree splits on alpha too, and would otherwise leave every body pixel at
  252-254). Both loaders expand it back to RGBA: `stbi_load_from_memory(..., 4)` on the GL side,
  `javafx.scene.image.Image` on the UI side.

A sprite is displayed at `PLAYER_DISPLAY_SIZE_PX` (48) times `CAMERA_ZOOM` (1.6) ≈ 77px, where
one authoring unit is about 1.2 screen pixels. **Judge any change at that size**, not at the
128px sheet resolution.

### Sound Effects & Music
All audio is procedurally synthesized (numpy oscillators/noise, no samples or external
audio libraries — zero licensing concerns) into `sounds/*.wav`, mirroring how sprites are
generated.

```bash
# Requires numpy: pip install numpy
python3 scripts/generate_sounds.py   # -> sounds/*.wav (188 files, ~23s)

# Look at what you just made — the contact sheet is the review loop (see below).
# Needs Pillow as well as numpy: pip install numpy Pillow
python3 scripts/sound_gallery.py /tmp/sndgallery
```

`scripts/generate_sounds.py` is a sound-design toolkit, not tone+noise bursts. Alongside
FM (`fm`), a time-varying resonant state-variable filter (`svf`), fast static resonant
filters (`res_lp`/`res_bp`), causal RBJ biquads (`eq`, used wherever a transient or the
master EQ is involved — the zero-phase FFT filters pre-ring, which puts a ghost tick
before a click), `saturate`, `bitcrush`, convolution `reverb`, `delay_fx` and `chorus`,
three engines carry most of the character:

- **`modal` + `MATERIALS`** — a struck object rings at frequency ratios fixed by its
  shape, each partial decaying at its own rate. Those two tables *are* the difference
  between iron, wood, bone, stone, ice, glass, chitin and flesh; no amount of filtering
  noise gets there. Every impact goes through `strike` (= `modal` + the bright edge of
  the contact).
- **`cry`/`glottal`/`tract`** — source-filter voice synthesis: a glottal pulse train with
  jitter and shimmer through formants that *move*. Every howl, screech, wail, bellow,
  roar and death exhale in the game comes out of this one throat and differs by pitch
  contour, vowel path and roughness.
- **`air`** — a whoosh is pink noise through a resonance tracing a doppler arc, with slow
  turbulence, an optional edge tone, and a `body` layer an octave and a half below
  standing in for the mass of air displaced. Without that body a swing is 100% 2-5kHz
  hiss.

Attacks are then assembled by thirteen family engines (`bolt`, `beam`, `swing`, `thrown`,
`shaft`, `lobbed`, `slam`, `burst`, `wavefront`, `gun`, `chain`, `chime`, `elec`,
`machine`) plus ~40 one-offs. Keeping families as engines rather than 120 bespoke
functions is what lets one quality change reach the whole roster, the same reason every
sprite goes through `sprite_base.generate_character`.

Generators output mono; every sound then goes through the shared `master()` chain —
`transient_shape` → `compress` → `sub_boost` → `presence_dip` → `air_tame` → `stereoize`
(decorrelated width) → `stereo_reverb` (separate impulse response per channel) → optional
`auto_pan`/`ping_pong` → `stereo_glue` (music only) → limiting. Per-sound `level`s give
the mix real dynamics (a thrown card is quiet, thunder is loud) — the set spans ~12dB.
Output is 16-bit **stereo** 44.1kHz. `sounds/` is ~33MB, the largest asset directory; the
two music loops are ~8.5MB of it.

Three rules the earlier version of this file broke, and why they matter:

- **Length is bounded by `SHOOT_COOLDOWN_MS` (500).** `MAX_DUR` caps each sound by class
  (`bolt` 0.55s, `melee` 0.6s, `ability` 0.9s, `heavy` 1.5s). An attack with more than
  ~0.5s of audible energy is still sounding when its own next shot fires; stacked across
  a firefight that is the difference between distinct attacks and mush.
- **`presence_dip` (a wide −3.5dB bell at 3.2kHz) is applied to every SFX.** The ear's
  sensitivity peaks at 3-4kHz, so 150 attacks all piling energy there is physically
  tiring inside a minute — more than any individual sound, that was what made the old
  set shrill. Median 2-5kHz energy fraction is now 0.03 (it was 0.17, with a long tail
  up to 0.76).
- **Identity goes in the body, not on top.** A bolt's fundamental sits at 90-350Hz with a
  short sub under it, and the timbre that names the school of magic rides on top at about
  −12dB. Built the other way round — all identity, no body — bolts read as UI beeps and
  vanish the moment anything else plays.

#### Judging a change: the spectrogram gallery

`scripts/sound_gallery.py` renders every sound as a log-frequency spectrogram
with a dB envelope strip underneath, 24 to a contact sheet. It exists for the
same reason `projectile_gallery` does — 188 assets cannot be judged one at a
time, and the faults that matter are the ones visible when they sit side by
side. **Run it after any change here.** What to look for:

| in the picture | in the sound |
|---|---|
| parallel lines sloping down | a pitch glide. One is a cartoon "boing"; a whole family doing it is why a set reads as generic |
| horizontal lines after the onset | a struck object ringing. A few, inharmonic, is the sound of hitting a metal bucket |
| a flat-topped envelope strip | no shape. The ear reads shape first, so this is heard as generic texture however good the texture is |
| energy filling 60Hz-16kHz | no focus. Once every sound occupies the whole range, none of them is distinguishable |
| no vertical stripe at the onset | no transient, so no impact |

Three faults found exactly this way, after the numbers said the set was fine:

- **every bolt was a slide whistle.** `bolt()`'s pitch envelope slid a harmonic
  stack down most of an octave across the whole sound. It is now a fast, small
  settle inside the first 35ms — a launch, not a swoop.
- **nothing decayed.** Generator-stage reverb and the master send were both
  near 60% wet, in series, which turned a bolt that reaches -44dB by 250ms dry
  into -20dB. Hence `GEN_REVERB` and, as a backstop, `enforce_shape()`: a
  per-class amplitude *ceiling* (flat for `hold`, then down to -60dB over
  `t60`) that ducks anything refusing to fall away. Deliberately held sounds —
  beams, howls, gas clouds — are listed in `SUSTAINED` and get the `drone`
  contour instead.
- **everything was full-spectrum.** Summing five layers and saturating the
  result fills the whole band on every sound. `air_tame` is now a real -7dB
  shelf at 9kHz, with `BRIGHT`/`SPARK` for the sounds that have earned their
  top end (ice, glass, sparkle, electricity, the hitmarker).

**The clang rule.** A modal bank is a *colour under* an impact, never the impact itself.
Four ways to turn the whole game into someone hitting a metal bucket, all of which this
file has done at some point:

- letting the partials lead — `MATERIALS["ring"]` is 0.14-0.55 for everything except
  `bell` for exactly this reason, and the decay rates are weapon rates, not the
  instrument rates a physical-modelling paper gives you;
- ringing a material once per rotation in `thrown()`, which is *literally* banging on
  metal at 7Hz. The tumble is air being chopped; the weapon rings once, on release;
- building a debris scatter out of `strike()` — a dozen tuned resonators inside half a
  second. `debris()` exists for this: each grain is its own short filtered noise burst;
- a high-Q resonance riding on noise (`air(q=…)` is capped at 3.0). Narrow resonance on
  noise is the sound of blowing across a bottle, and it is hollow in the same way on
  every sound that uses it.

`thud()` covers the other half: a body impact is one damped sine with no overtone series.
A drum-membrane mode set at 80Hz is a tom, and a tom under every hit is a bucket.

Avoid also: sustained pure tones (the old horn was a 2.2s sine stack — a foghorn),
melodic arpeggios as attacks (the old data bolt was a five-note chiptune riff fired twice
a second), tremolo rates in the 20-60Hz roughness band (the old stinger was a kazoo), and
a global `tanh` on the master bus (it costs several dB of crest on *every* sound —
`_soft_knee` rounds off only what is above the knee).

- `sounds/atk_*.wav` — one sound per attack archetype. `AbilitySounds.scala` maps all 150
  `ProjectileType` ids onto them, mirroring the many-to-one grouping already used by
  `GLProjectileRenderers.registry` (types that share a renderer usually share a sound),
  **plus a per-character override table**. A handful of projectile types are shared by
  characters with nothing in common — `TREMOR_SLAM` is a barbarian splitting the earth
  *and* a wolf's howl *and* a banshee's wail; `FIREBALL` is a wizard's fireball *and* a
  chef's flambé *and* an alchemist's thrown potion — so `forAttack(projectileType,
  characterId)` checks `(character, projectile)` first. `GameClient.characterIdOf`
  resolves the shooter at the call site.
- **A projectile type names the object, not who is holding it.** Six characters "throw a
  boulder" and five "swipe a claw"; the base sound can only be one of them, so the rest
  get a character voice (`gen_ape_throw`, `gen_stone_fist`, `gen_mantis_scythe`, …) built
  as the base plus what that creature adds — an effort grunt, wingbeats, chitin scrape,
  a snarl. The primary attack is what a player hears for a whole match, so it is worth a
  file of its own; abilities on long cooldowns can share.
- `python3 scripts/sound_roster.py` prints what every character actually hears with both
  tables resolved, and `--shared` lists the sounds more than one character's primary
  still shares — the audit to run after touching either table.
- `sounds/spawn.wav`, `sounds/death.wav`, `sounds/dash.wav`, `sounds/teleport.wav`,
  `sounds/phase_shift.wav`, `sounds/barrier_up.wav` — non-projectile events/cast behaviors.
  `sounds/barrier_block.wav` is a shot stopped on a barrier: a dull thud of energy with a
  short fizz and nothing that rings, since it plays for every shot anyone fires into one.
- `sounds/trap_place.wav` (one set down — quiet and short, since everyone nearby hears it),
  and one per trap kind going off: `trap_snap.wav` (jaws, and a web closing), `trap_poison.wav`
  (a pod bursting), `trap_ignite.wav` (a rune catching). A mine's blast is `explosion.wav`.
- `sounds/hit_taken.wav` (you were hit), `sounds/hit_dealt.wav` (hitmarker — bright and
  high-mid so it cuts through), `sounds/hit_other.wav` (someone else was hit, duller and
  distance-attenuated), `sounds/explosion.wav` (explosive projectile despawn).
- `sounds/music_menu.wav` (calm, loops through JavaFX UI screens) and
  `sounds/music_battle.wav` (faster/more intense, loops during a match).
- `client/audio/AudioManager.scala` **mixes in software onto a single
  `SourceDataLine`** (no LWJGL/OpenAL dependency needed for 2D game audio). All
  playback is best-effort — a missing device sets `initFailed` and every call
  no-ops, so the game runs identically without sound hardware.

  **Do not go back to a `Clip` per playback.** It was measured at ~6.4ms of CPU per
  sound (native line acquisition + buffer copy + a thread per Clip) — 40-60% of a
  core in a firefight, ~35 audio threads competing with the render thread, and
  `getClip()/open()` stalling up to **96ms**, which lands directly on the render
  thread because dash/teleport/phase-shift are triggered from the input handler.
  Measured at 120 sounds/sec, Clip-per-play gave a 58ms worst frame; the mixer gives
  4.4ms with 4 threads. Triggering a sound is now just claiming a voice slot
  (~0.05ms median, 0.29ms worst) — no allocation, no native call, no thread.

  Voices are capped at `MAX_VOICES` (24); when they are all busy the trigger is
  dropped, which is correct since the mix is already saturated. Idle mixer cost is
  ~1% of a core. `AudioManager.preload()` decodes every sound on a background thread
  at startup so no WAV parsing or disk I/O lands mid-match.
- **Pitch and pan are applied per voice in the mixer**, not via `AudioFormat` tricks
  or `BALANCE`/`PAN` controls: `±7%` playback-rate jitter (`PITCH_SPREAD`, via
  linear-interpolated resampling) so rapid fire isn't machine-gun identical, and
  constant-power panning. Attack/hit/death sounds also attenuate by distance.
- **Positional stereo**: sounds are panned by where they happened. Screen X in an
  isometric projection is `(wx - wy)`, so `GameClient.panFromLocal` pans on that axis
  rather than world X, scaled by `Constants.AUDIO_PAN_RANGE_CELLS`.
  `hit_taken`/`hit_dealt` are deliberately left centred — they are about you, not
  about a location in the arena.
- If you add a new `ProjectileType`, add a matching entry to `AbilitySounds.scala`
  (falls back to `atk_normal_bolt` if omitted). If you add a wholly new sound
  archetype, add an entry to the `_sounds()` table in `scripts/generate_sounds.py`
  and rerun it — the script cross-checks itself against `AbilitySounds.scala` and
  fails if that file names a sound it does not generate, so the two cannot drift.
- If a character's ability would read as somebody else's — because it shares a
  projectile type with a character of a different fantasy — add a line to the
  `only(...)` override block in `AbilitySounds.scala` rather than splitting the
  projectile type.

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

### Roles, health and pace
Every character names a `role: CombatRole` (`common/model/CombatRole.scala`), and the role sets
the two numbers that trade range for staying power. Whoever has to get into range gets the
health to survive getting there. Whoever can hit from range walks a little quicker, so a ranged
character can kite a melee one (walk away, shooting back) for as long as both only walk. A melee
character closes the gap with its abilities or an item, not its feet.

| Role | Primary reach | Characters | Health | `moveSpeed` | Step |
|---|---|---|---|---|---|
| `Ranged` | 12 or more | 75 | 60-80 | 1.0 | 50ms, 20 cells/s |
| `Skirmisher` | 7-10 | 7 | 105-150 | 0.97 | 52ms, 19.2 cells/s |
| `Melee` | 6 cells or less | 30 | 105-150 | 0.94 | 53ms, 18.9 cells/s |

- **The role is named on each character, not worked out.** `CombatRole.forRange` is the rule
  (reach measured fully charged), with one exception: the Monk, whose 6-cell punch reaches 10
  charged, is melee. `RosterBalanceTest` checks every character against the rule and its list of
  exceptions.
- **Skirmishers** are the boulder throwers (Earthshaker, Avalanche, Beetle, Gorilla, Golem,
  Cyclops) plus Ember: melee health, a pace between the two.
- **Health keeps each character's place within its class.** It was mapped from the old values
  (ranged `60 + (old - 65) * 20/60`, melee and skirmishers `105 + (old - 65) * 45/65`, rounded to
  5), then the glass cannons (Wizard, Railgunner, Astronomer, Cleric, Pilot) were set at the
  ranged floor and the Minotaur at the melee ceiling. The toughest ranged character (80) stays at
  least 20 below the frailest fighter (105). `RosterBalanceTest` pins the bands, that gap and the
  order of the paces.
- **Only just quicker:** walking straight away, a ranged character opens about a cell a second on
  a melee one. Played over the network: a Soldier walking away from a Berserker and shooting back
  went 20.1 cells a second to its 19.1, opened the gap from 8 cells to 12 and killed it in 3.9s
  without being swung at. From 8-9 cells, the Gladiator's rope pulled the Soldier in and the
  Hawk's Dive Bomb teleport landed beside it; each killed it inside 1.4s. `RosterBalanceTest` keeps
  the gap between half a cell and two cells a second.
- **Every melee and skirmisher kit has a way in, or is an anchor** (Plan 5a). A way in is a
  teleport; a phase, which walks at twice the pace; a pull, stun, root or slow on a projectile fast
  enough to land on someone walking away from 8 cells; or a dash that gains 6 cells over walking.
  The Crusader, Golem, Beetle and Avalanche are **anchors** instead: a barrier or a big hold, with
  a team to do the chasing. `MeleeKitTest` checks both, and that every kit has crowd control.
  - A player's `DashBuff` moves `maxDistance` cells over `durationMs` while the runner keeps
    walking, so it gains only `maxDistance - 18.9 * durationMs / 1000` cells over walking. Melee
    dashes were 300-500ms and gained 2-5; they are now 150ms (the Minotaur's 14 cells 200ms), and
    the 8-cell ones 10 cells: 7 to 10 cells each. A shorter dash is a shorter invulnerable phase
    too. Bots dash at once (`BotController` sets their position), so it never short-changed them.
    Ranged dashes are escapes and were left as they were.
  - A projectile flies `33 x speedMultiplier` cells a second, so on a runner walking straight
    away it lands from at most `maxRange x (1 - 0.6 / speedMultiplier) + hitRadius + 1` cells: a
    0.85 rope from 10, a 0.9 closer of 18 cells from 8, a 0.7 bolt from 5, and a 0.6 axe (a
    runner's own speed) only inside its hit radius. That formula is for a runner moving smoothly,
    and one moves in one-cell steps: a 16-cell closer at 0.9, which it put at 8.1, missed from 8
    at 3 phases of the step in 20, and the play test's Hammer Throw missed from 8 over the network.
    The 16-cell pulls and holds are 0.95. `MeleeKitsTest` walks a runner away from every
    projectile way in at 20 phases of its step and checks each lands from 8, and that the Death
    Bolt the Death Grip replaced never could.
  - Played over the network (Plan 5a), a Soldier walking away from 8 cells and shooting back was
    caught by the Berserker's Rage Charge, the Chef's Meat Hook, the Vampire's Mist Form, and the
    Blacksmith's, Death Knight's, Paladin's and Scorpion's throws, and was dead 1.2-3.2s after
    each. A dash stops at the cursor, so it is aimed past the runner: aimed at them, the Rage
    Charge covered 8 of its 10 cells and left the Berserker 4 cells back, out of its axe's reach.
  - A primary never holds (no stun, freeze or root): it fires twice a second, and each hold grants
    1.5s of CC immunity, which turns the kit's own holds away. A slow grants none.
- **Regen doesn't work against it:** 2% of the character's own max health a second
  (`Constants.REGEN_SHARE_PER_SEC`), so everyone takes 50s to come back from nothing, and none
  while a burn or a poison lasts. It used to be `3.0 - (max - 70) * 0.04` a second, which healed
  the frailest fastest and nobody at all from 145 HP.
- **Bots play their role** (`BotController.moveSmart`): a ranged bot keeps its distance and backs
  off, a melee bot closes in, and a skirmisher closes to half its throw and circles there without
  backing off. A ranged bot strafes at mid range rather than walking straight away, so a melee
  bot still catches one now and then. A phase doubles a bot's pace as it does a player's; a melee
  or skirmisher bot casts one to close on a target out of its reach (the Vampire's Mist Form), a
  ranged bot only to get away from someone within 5 cells.
- **Shown** under the name on the character select screen ("Melee · 145 HP · Slow",
  `CharacterSelectionPanel.roleLine`) and on the website's cards, which a tool writes from the
  roster (see *Website*). The grid's "Melee" and "Ranged" tabs are the older playstyle
  categories, not the role.
- A few roles read against their character's fantasy. The Magma Knight and the Valkyrie are
  ranged, and so kite. Ember, a tiny fire sprite, is a skirmisher with bruiser health. They follow
  the rule until their kits change.

### Cast Behaviors
Each ability uses one of these cast behaviors (defined in `CharacterDef.scala`):
- `StandardProjectile` — fires a projectile toward the cursor
- `PhaseShiftBuff(durationMs)` — grants a temporary buff (e.g., ethereal form)
- `DashBuff(maxDistance, durationMs, moveRateMs)` — dash movement ability
- `TeleportCast(maxDistance)` — instant teleport to cursor position
- `FanProjectile(count, fanAngle)` — fires multiple projectiles in a fan pattern
- `GroundSlam(radius)` — AoE ground slam around the caster
- `BarrierCast(durationMs)` — raises a barrier in front of the caster (see *Barriers*); fires
  nothing, so its ability's projectile type is -3 (buffs and dashes carry -1, teleports -2)
- `TrapCast(trapType, maxRange)` — throws a trap onto the ground (see *Traps*); fires nothing
  either, so its ability's projectile type is -4

### Movement speed
`CharacterDef.moveSpeed` multiplies the base walking rate of 20 cells a second
(`Constants.MOVE_RATE_LIMIT_MS`, 50ms a cell). Each character's is its role's pace
(`CombatRole.speed`, see *Roles, health and pace*): 1.0 ranged, 0.97 skirmisher, 0.94 melee.

`common/model/Movement.scala` holds the one rule that turns it into a step interval, and
everything that moves a player or checks a move reads it: `GLKeyboardHandler` and
`ControllerHandler` (through `GameClient.moveStepIntervalMs`; the isometric "pure left/right
takes twice the delay" rule stays in the handlers), `BotController` (bots step at twice a
player's interval for the same character and state), and `PacketValidator.maxCellsIn`, whose
tolerance is twice that character's own rate plus two cells of grace. The two input handlers
each used to carry their own copy of the numbers.

Everything acting on a player is a factor on their interval, not an absolute: charging drags a
step out to ten times its length at full, a phase halves it, a speed boost takes it to 60%, and
**a slow divides by its own multiplier** — a `Slow(_, 0.3f)` really is 30% of their pace. Only
the "slowed" bit used to cross the wire, so every slow was a flat half whatever its def said;
`PlayerUpdatePacket` byte [52] now carries the strength with it. `MovementTest` pins the
arithmetic, and that at a speed of 1.0 it is exactly what the handlers computed before.

**A step's wait counts from when the last step fell due, not from the frame that took it**
(`Movement.nextStepFrom`). The input handlers step on 60 fps frames and the bots on a 100ms tick,
and counting from the frame rounded every interval up to whole frames. That turned the roles'
few-percent gap into a quarter: a melee character's 53ms step waited for the fourth frame and
walked at 67ms, to a ranged character's 50ms. A 25ms phase walked at 33ms. A 50ms step lost a
whole frame whenever the third came a millisecond early, about one step in eight at ±3ms of frame
jitter. And a bot's 104ms or 106ms step waited for the second tick after its last, at half its
pace. Now the handlers carry the remainder (a step a whole interval late is a fresh start, so
nothing builds up while the keys are up), and a bot takes up to two steps a tick (a speed boost
brings a step under the tick). `MovementTest` and `BotMovementTest` pin it.

### Projectile System
Projectiles are defined in `ProjectileDef.scala` with extensive customization:
- **Charge scaling** — speed, damage, and range scale with charge level
- **Distance damage scaling** — damage increases over distance (e.g., spears)
- **Pierce** — passes through `pierceCount` players and is used up by the next
  (`ProjectileDef.piercesAfter`). It used to stop at hit number `pierceCount`, so the fourteen
  types with a pierce of 1 never pierced. The server tells clients about a hit it flies on from
  with `ProjectileAction.PIERCE`, so they keep drawing it
- **Boomerang** — returns to owner after max range
- **Ricochet** — bounces off walls (`ricochetCount`)
- **AoE splash** — area damage on hit or at max range, with an optional freeze, stun or root
- **Explosions** — center/edge damage with blast radius
- **Pass-through** — can ignore players or walls

### On-Hit Effects
12 effect types applied when projectiles hit players. A def carries one in `onHitEffect` and any
more in `alsoOnHit` (a dart that poisons and slows, a horn that knocks back and stuns): everything
reads `ProjectileDef.onHitEffects`, both in order, direct hits and blasts alike. The Vampire's Bat
Swarm freeze used to be a special case in `GameInstance`; it is an effect in its def now.

**An explosion deals its blast and nothing else.** A def that `explodesOnPlayerHit` goes straight to
its `explosionConfig` blast, which applies no on-hit effect, so the Inferno's Inferno Blast and the
Pilot's Napalm Strike have never burned anyone. A splash (`aoeOnHit`, `aoeOnMaxRange`) does apply
them to everyone it catches, which is why the Chef's Flambé is a splash.
- `Freeze(durationMs)`, `Stun(durationMs)`, `Root(durationMs)`, `Slow(durationMs, multiplier)`
- `Burn(totalDamage, durationMs, tickMs)`, `Poison(totalDamage, durationMs, tickMs)`
- `Push(distance)`, `PullToOwner`, `VortexPull(radius, pullStrength)`
- `LifeSteal(healPercent)`, `SpeedBoost(durationMs)`, `TeleportOwnerBehind(distance, freezeDurationMs)`

**A stun is a freeze wearing different clothes.** `Player.tryStun` sets the same `frozenUntil`
timer with the same rules (refused while frozen, CC-immune or phased) and the same CC immunity
after it, so every "is frozen" gate in the server, the client and the bots holds a stunned
player without knowing stuns exist. All `stunnedUntil` adds is which of the two the client
draws — stars round the head instead of frost (`GLGameRenderer.drawStunnedEffect`). Splashes
carry one through `AoESplashConfig.stunDurationMs`, beside `freezeDurationMs`/`rootDurationMs`.

**A poison is a second damage-over-time slot, not a second kind of burn.** It has its own
ticks and owner on `Player`, so a poison and a burn run at once — sharing burn's slot,
whichever landed second wiped the first out. `GameInstance.tickPlayers` ticks both every 200ms
(`tickBurn`, `tickPoison`), and a kill is credited to whoever cast it (`Attrs.CausePoison`).

A poison counts its ticks rather than timing out (`Player.takePoisonTick`): each falls due a
whole `tickMs` after the one before was *due*, it lasts until the last one has landed, and the
damage left is spread over the ticks left, so it deals exactly its total. The update for its
last tick goes out without the flag, which is how clients hear it is over. Nobody regenerates
while poisoned, between its ticks as well as on them.

**A burn does not deal its listed total**, and is left that way here so burns play as they did.
It times out on the clock (`isBurning` is `now < burnUntil`) and schedules each tick from when
the last one actually landed, so on a 200ms server tick every bite is late, the last falls after
the deadline, and the per-tick split truncates: the roster's burns deal 60-80% of their totals
(`Burn(15, 3000, 750)` does 9, `Burn(12, 3000, 750)` 9, `Burn(20, 4000, 800)` 16). Tune burns
by what they deal, or move them onto the poison's rules and retune. Regen is off for as long as
a burn lasts, as for a poison. It used to be off only on the ticks the burn bit, and at a melee
character's 3 HP a second regen healed most of a burn back between bites.

**A blast carries the same on-hit effect a direct hit would.** The `ProjectileAoEHit` case used
to apply only the holds and the burn and drop the rest, so no slam could push or pull, the
Necromancer's Soul Harvest ("healing from damage dealt") healed nothing, and the Cyborg's
Overclock boosted nobody. Life steal off a blast heals by the splash damage the event carries,
because a slam's own `effectiveDamage` is 0. `TeleportOwnerBehind` stays direct-hit only: it
needs one target to land behind, not a crowd. A slam whose on-hit effect is a `SpeedBoost` is a
self-buff, so it lands on the caster at cast time (`GameInstance.applyCastSelfBuff`) whether or
not anyone is standing in it.

### Barriers
`BarrierCast` raises a shield wall carried in front of its caster and turned to face their aim:
the Crusader's Bulwark (3.5s, a 12s cooldown counted from the raise), the Gladiator's Scutum, the
Paladin's Aegis and the Beetle's Carapace (3s each) and the Golem's Stone Wall (3.5s, 14s). It is
called a barrier in
code because the Shield *item* (key 4, five seconds of invulnerability, `Player.hasShield`,
flag 0x01) already has the name; the player-facing ability can still say shield.

- **One shape.** `common/model/Barrier.scala` is a polyline of three segments, 2.0 cells out
  along the aim, 6.0 wide, its ends bent 0.6 back. The server stops shots on it and the client
  draws it from it. It is one-sided: only a path that crosses its front (heading in, toward the
  holder) meets it, so the holder's own shots go out through it and an enemy who gets inside the
  arc is past it.
- **What it stops**: enemy projectile bodies. Not the holder's own, not a teammate's
  (`GameInstance.isTeammate`; in FFA everyone else is an enemy), and never a
  `passesThroughWalls` type. A stopped projectile halts where it met the barrier and is sent as
  `ProjectileAction.BLOCKED`; an explosive goes off there instead, as against a wall, and pierce,
  ricochet and boomerang types simply stop.
- **Who it shelters**: nobody can be hit through it, because the line from the projectile to
  them has to be clear. That is required, not a nicety: hit radii (1.8 to 3.5 cells) reach past
  a barrier standing 2 cells out, so without it a shot hit the holder, or whoever was beside
  them, before it got to the barrier. Blasts, splashes, slams and vortices centred in front of it
  don't reach anyone it stands between (`ProjectileManager.shelteredFromBlast`).
- **It moves.** `ProjectileManager` snapshots the raised barriers once a tick, with where each
  was on the tick before. A projectile the barrier was carried or swung onto, in front of it last
  tick and behind it now, is stopped too: without that, a holder walking into fire, which is what
  the Crusader is for, let the shots through. A shot's first tick also checks the one-cell hop
  from where it was fired to where `spawnProjectile` put it, so an enemy pressed up against the
  barrier can't start a shot on its far side.
- **Up and down.** The client raises it by sending effect flags 2 bit 2 with the aim angle,
  keeps its facing streamed while it is up (`GameClient.streamBarrier`: every 100ms, every 50ms
  while it turns, from both input handlers) and says when it has run out. The server
  (`ClientHandler.updateBarrier`) honours a raise at 80% of the cooldown since the last raise,
  turns the barrier with every update, and drops it on an update without the bit and on any spawn
  it accepts from the holder. Firing the primary, the burst shot or any ability drops it; the
  client drops it first and says so before the shot is sent. Death drops it (`Player.damage`),
  and a respawn resets it. Every client runs the barrier on its own timer: ours from the cast,
  as a phase does, and everyone else's from the time left that each update carrying it reports
  (bytes [53-54]), dropping it on an update that doesn't. An update older than the newest one it
  has taken a barrier from is ignored, since datagrams overtake each other. A remote barrier used
  to be a 600ms lease renewed by those updates, which its holder's client streams **from its
  render loop** — so a hitch there longer than the lease (a GC pause, a resize, an alt-tab on the
  weak machine this targets), a burst of lost datagrams, or a run of updates the server refused
  after a knockback took a barrier that was still up, and still stopping shots, off every other
  screen for the rest of its life. Dropping one early is announced twice, as running out is,
  because nobody else's copy expires on its own any more.
- **Drawn** by `GLGameRenderer.drawBarriers`, after the flying projectiles: a translucent sheet
  22px tall along the polyline and a glowing strip on the ground under it, blue for ours and our
  allies', red for everyone else's. It grows out of the ground as it goes up, sinks and fades as
  it drops, and ripples for 300ms where a shot struck it. A ripple is kept as how far along the
  barrier it struck (`GameClient.getBarrierImpact*`), so it moves with a barrier that is carried
  on. The strip is what shows a barrier aimed straight left or right, whose sheet this projection
  sees edge-on.
- **Bots** raise it against a target who shoots from further off than a blade (a ranged
  character or a skirmisher), who can reach them and whom they can't yet reach, or against a
  shot coming in; face the target and close in while it is up; and drop it to fire or
  cast.

`BarrierTest` (in `common/model` for the shape, and in `server`), `GameClientBarrierTest` and
`PacketRoundTripTest` pin all of this.

### Traps
`TrapCast` throws a trap onto the ground, where it waits for an enemy to walk into it. Five
abilities are one: the Warden's Snare Mine, the Blacksmith's Anvil Trap and the Gravedigger's Open
Grave (bear traps), the Sentinel's Deploy Mine (a mine) and the Runesmith's Rune Trap (a fire
rune).

- **One registry.** `common/model/Trap.scala` holds `TrapDef` — what a trap does — and `TrapKind`
  — what it looks like, kept apart so two traps that do different things can share a look. Five
  are defined: a bear trap (10 damage and a 2s stun), a mine (a 45/15 blast over 3 cells), a
  poison pod (48 over 6s, and a slow), a fire rune (a 40 burn over 5s) and a snare (a 2s root;
  Plan 5b gives it to the Spider). Unlike `ProjectileDef`, the registry is filled by its own
  initializer, so nothing can look a trap up before it is there.
- **Where it lands.** `TrapPlacement.target` walks from the caster toward the cursor, at most the
  ability's range (6 cells), and stops before the first cell a trap can't lie on: the client picks
  the cell with it and the server checks with `isValidTarget`, the same reason `Teleport` exists
  for blinks and stars. A throw can't be dropped past a wall. No cell at all means no cast and no
  cooldown spent.
- **Arming and lasting.** It arms 800ms after it lands and lies there 25s. A player keeps three;
  a fourth takes their oldest away. One trap to a cell.
- **What sets it off.** An enemy — not the owner, not a teammate — within `triggerRadius` (1.0
  cell: the trap's own cell and its four neighbours). Phased players walk over it, and so does
  anyone a Shield item has made invulnerable, exactly as a projectile can't touch them. A trigger
  consumes it whatever happens next: its stun obeys CC immunity like any other hold, and is spent
  either way.
- **What it then does** (`GameInstance.springTrap`): its damage and effects to the one who stepped
  on it, or its explosion with the same falloff and the same shelter behind a barrier a
  projectile's blast has. A burn or a poison is owned by whoever laid the trap, so the kill is
  theirs (`Attrs.CauseTrap`). The blast walks the registry rather than the projectile tick's
  spatial grid, because a trap goes off on whichever thread stepped on it and that grid belongs to
  the tick.
- **When it is checked.** `GameInstance.tickProjectiles` calls `trapManager.tick` every 30ms,
  which expires old traps and catches everyone standing on one. `ClientHandler.handlePlayerUpdate`
  also walks the cells an accepted step crossed: an update can carry a player several cells (a
  lost datagram), and a trap hopped clean over has still been stepped on. Every trap leaves
  through `TrapManager.claim`, which takes it with `traps.remove(id, trap)`, so however many
  threads find the same one, exactly one springs it.
- **Placing one** goes over TCP as a `TRAP_UPDATE` carrying its `AttackSlot`. `ClientHandler`
  checks the player can cast, that the attack really throws that trap, that the cell is in reach
  with a clear path, that the cell is free, and the cooldown — through
  `PacketValidator.validateCast`, on the same per-slot clock a shot uses. Anything judged without
  the clock is judged first, so only a genuine race can spend a cast and still be refused. A
  refusal comes back as `REJECTED` and the client gives the cooldown back, ready again in 400ms
  rather than instantly: the reason a placement was refused often hasn't gone away, and a cooldown
  handed back whole turns a held key into a request every frame.
- **A new life starts with every attack ready.** `GameInstance.respawn` clears the server's attack
  clocks (`PacketValidator.resetAttackClocks`), which the client has always done for its own.
  Without it the server spent the rest of the last life's cooldown refusing abilities the player
  could see were ready — silently for a projectile, and as a refused placement for a trap.
- **Everyone is sent every trap**, joiners and rejoiners included. The owner and their allies see
  it plainly; everyone else sees it at 30% alpha — findable if you look, which is the point of
  looking. A player leaving takes their traps with them.
- **Drawn** by `GLGameRenderer.drawTraps` in the ground pass, so a wall in front of one covers it
  in the depth pass that follows, with nothing asked of `EntityCollector`. Per kind: a bear trap's
  jaws, a mine with a blinking diode, a spore pod, a burning rune, a web. A ring closes on one
  that is still arming, and it fades over its last 800ms. A trap going off is a flash, a ring and
  something of its own (jaws snapping, spores rising, flame standing up, strands whipping back); a
  mine's blast goes through `explosionAnimations`, keyed past `GameClient.TRAP_EXPLOSION_IDS` so a
  trap and a projectile can never collide. The ability slot carries the count ("2/3").
- **Bots** lay one for a target within 8 cells that is closing in, and treat armed enemy traps as
  cells to walk round — in `canMoveTo` and in the BFS — unless their target is standing on one.
- **Sounds**: `trap_place`, `trap_snap`, `trap_poison`, `trap_ignite`; a mine reuses `explosion`.

`TrapTest`, `GameClientTrapTest`, `PacketRoundTripTest` and `CharacterRosterTest` pin all of this.

### Item Types
5 item types (defined in `ItemType.scala`): Gem, Heart, Star, Shield, Fence

## The opening of a match

A match's first `Constants.MATCH_OPENING_MS` (30s) is not played quite the way the rest of it is,
and each mode spends it its own way: a Teams match walls the two halves off from each other, a
free-for-all holds everyone's fire. `common/model/MatchOpening.scala` names the two rules
(`DIVIDER`, `NO_ATTACKS`) and which mode gets which; the server sends the set, and each side acts
on the rules it recognises rather than working them out from the mode.

**Both sides run it on their own clocks.** The server announces it once when the match begins and
once when it is over — `GameEvent.MATCH_OPENING`, carrying the milliseconds left in
`GameEventPacket` bytes [48-51] and the rules in [52] — and tells anyone joining during it
(`GameInstance.sendOpeningTo`). A client's copy always outlives the server's by the trip down the
wire, so it never lets through something the server would refuse and rubber-band.
`GameInstance.syncOpening` drives both announcements from the projectile tick, and takes the wall
off the world as it announces the end, so the rest of the match pays nothing for it. Under the
match clock a countdown runs and a beat of FIGHT! marks the end
(`GLGameRenderer.drawOpeningCountdown`) — the same words either way, since behind a wall or
holding your fire, the battle starts when the countdown does.

### Teams: a half of the map each, and a wall between them

A Teams match is played in two halves of the map, one per team, and opens with a wall between
them: nothing at all crosses the line, so each side can gather, pick its ground and say something
to each other before anyone can shoot anyone.
- **One line, worked out from the map.** `common/model/TeamDivider.scala` splits the world down
  the middle of its longer axis (x for a square map, which every map in `worlds/` is), so both
  sides derive the same geometry from the world they loaded and no shape is ever sent. Team 1
  takes the low half, team 2 the high one (`sideOfTeam`), and every spawn — the match's first and
  every respawn after it — comes out of that team's own half (`GameInstance.spawnFor`, through the
  cell filter on `WorldData.getValidSpawnPoint`). A half with nowhere to put anyone falls back to
  the whole map rather than to the middle of it.
- **The wall is a hole in the world's walkability.** A raised divider hangs off
  `WorldData.divider`, and `isWalkable` refuses the cells it stands on while it is up. That is what
  makes a step, a dash, a blink, a knockback, a trap throw, a bot's path and a spawn point all
  refuse it without any of them knowing it is there. It is not terrain — the tiles are untouched,
  and `isTileWalkable`, which is what a projectile is stopped by, ignores it.
- **What ignores walls is held by hand.** A star jumps over whatever lies between
  (`Teleport.starTarget` / `isValidStarTarget`), a phase walks through walls
  (`PacketValidator.validateMovement`), and a `passesThroughWalls` projectile flies over them.
  Each is turned back by the side rule — `allowsMove`: not into the wall, and not onto its far
  side — or by the crossing test, so nothing gets over what a walk cannot get through.
- **Nothing crosses it in flight either.** `ProjectileManager` reads the divider once a tick and
  stops any projectile whose sub-step crosses the line, a hair short of it, the way a barrier does:
  `ProjectileAction.BLOCKED` with **no** target, since this wall is nobody's. An explosive goes off
  there instead. A hit radius reaches a cell and a half past the line and a blast several, so a
  direct hit or a blast whose line to its victim crosses the divider is cut too (`dividerBetween`),
  exactly as a held barrier shelters whoever stands behind it.
- **It is the same wall for everyone**, allies included: for thirty seconds the two halves cannot
  touch each other at all.
- **Bots** ignore anyone on the far side (`findNearestPlayer`) — they can neither reach nor hit
  them, and would spend the opening walking into the wall shooting it. Their paths already avoid
  the cells it stands on.
- **Drawn** by `GLGameRenderer.drawTeamDivider`, after the flying projectiles and the held
  barriers: a sheet of amber light standing on the ground along the line — amber because blue and
  red are somebody's barrier — clipped to the stretch the screen can see, in the projection's own
  axes. A hard line along its top and a glow where it meets the ground, with ribs standing in it:
  two bright parallel edges with an even fill between them read as a road, not a wall. It beats
  through its last three seconds, flashes where a shot strikes it, and sinks into the ground as it
  drops.

### Free-for-all: a ceasefire

There are no sides to keep apart, so a free-for-all spends its opening holding everyone's fire:
for those thirty seconds nobody can attack at all — no shot, no charge, no burst, no ability — and
everyone gets the same half minute to find their feet and pick their ground.

- **Every attack is held**, whatever it is: a projectile of any kind
  (`ClientHandler.handleProjectileUpdate` refuses every spawn, before the fire-rate clock, so a
  refused attack costs no cooldown), a trap (`handleTrapUpdate` refuses it and says so, so the
  client gives the cooldown back), a phase or a dash (`activatePhase`), a barrier
  (`updateBarrier`), and a blink's jump — `PacketValidator.validateMovement` drops its
  ability-movement exception, so a jump no walk could have made is refused like any other.
- **What isn't held**: walking, and items. A star is an item, not an attack.
- **The client holds it first**, at the four doors an attack leaves by — `shootToward`,
  `shootAllDirections`, `shootAbility` and `startCharging`, the last because a charge bar that
  fills and then fires nothing is worse than a button that does nothing. Refusing here spends no
  cooldown, and no burst-shot root, on an attack the server was never going to allow.
- **The two ability slots are shuttered** with a padlock and the seconds left, the icon behind
  dimmed: a held slot is not a cooldown, and nothing it does will start one.
- **Bots hold their fire too** (`BotController.tickBot`): a bot spends the opening walking and
  looking for a target, and shooting at nothing.
- **Practice has no opening at all** (`GameInstance.begin` reads `isPractice`): it is where a
  player goes to try a character out, and holding its fire for thirty seconds is the one thing an
  opening must not do there. A ranked duel is a free-for-all of two, and holds fire like any other.

### Both

- **Look at either** with `bazel run //src/main/scala/com/gridgame/client:render_bench -- --divider`
  or `-- --ceasefire` (`=<seconds>` on either to watch it end), which is the loop for the HUD and
  the wall, since a real opening needs a match and lasts thirty seconds of it. The wall is stood
  beside the local player rather than down the middle of the map.
- `TeamDividerTest` (in `common/model` for the geometry and what the world refuses it, and in
  `server` for the match), `CeasefireTest`, `GameClientOpeningTest`, `LobbyFlowTest` and
  `PacketRoundTripTest` pin all of this. `TestMatch` opens with the opening only when a test asks
  for it (`opening = true`): a match driven by hand is one already under way.

## The end of a match

A match ends the moment its time is up: `GameInstance.start` schedules `endMatch` for its deadline.
The 10-second `TIME_SYNC`s only report the time left, and every client counts down from the last
one. The match clock (`getRemainingSeconds`, `isTimeUp`) is `System.nanoTime`, the clock that
executor counts on, never the wall clock.

It used to be the wall clock, and the syncs used to end the match, the first to find the time up.
The sync due at the deadline comes round only a few milliseconds after it, and macOS's `timed`
corrects the wall clock by 5-80 ms, backwards as often as forwards, about every 25 minutes. When the
clock went back further than that margin during a match, the sync found a second left, and the match
ran on for another ten seconds with every countdown at 0:00. That was about one match in ten on the
dev machine. `EndGameTest` pins both halves, and a match can be made shorter for a test with
`durationMs`.

## Network Protocol

80-byte packets (64-byte payload + 16-byte HMAC-SHA256) over TLS-encrypted TCP (reliable) and HMAC-signed UDP (fast updates), using Netty. Byte order: BIG_ENDIAN.

### Network Security

9 layers of security protect the networking stack:

1. **TLS 1.3 for TCP** — All TCP traffic encrypted via Netty `SslHandler`. Server generates a self-signed certificate at startup using `keytool` with a random password and restrictive temp directory permissions (`rwx------`). Explicit cipher suites: `TLS_AES_256_GCM_SHA384`, `TLS_CHACHA20_POLY1305_SHA256`. Client trusts all certs (game server, not web).
2. **HMAC Packet Signing** — After auth, server issues a 32-byte session token. All subsequent packets (TCP and UDP) carry a 16-byte truncated HMAC-SHA256. Packets with invalid HMAC are dropped silently. UDP packets without a valid session token are dropped entirely (no unsigned UDP fallback). **A signature proves who sent a packet, not who it names**: a TCP packet naming anyone but the player its channel logged in as is dropped in `GameServerTcpHandler` before that player's rate budget or replay window is touched. Checked later, as it was, one logged-in client could spend another's budget, or send one packet numbered far ahead in their count and make every packet they sent afterwards look like a replay. The client's handlers take nothing unsigned once it holds a token (its UDP handler doesn't look at the sender, so the signature is all there is). `NetworkHandlerTest` and `ClientNetworkTest` pin both ends.
3. **Rate Limiting** — Per-client: 240 UDP/s, 40 TCP/s, 5 chat lines/s (`RateLimiter`'s companion object holds every limit). Per-IP: 5 connections/min, 5 auth failures before 30s cooldown. Per-channel: 5 auth requests/s before login, and the connection closed after 5 auth failures (`MAX_AUTH_FAILURES_PER_CHANNEL`). A datagram counts against a player's budget only once its signature checks out, since its source address is whatever its sender writes into it: charged first, a flood of forged datagrams in a player's name spent their budget and their real updates were dropped. Race-free auth tracking via `computeIfAbsent`. Stale entries cleaned up every 5s. `RateLimiterTest`.
4. **Server-Side Validation** — Movement validated against world bounds, walkability, and speed limits (`PacketValidator.maxCellsIn`: 2x the character's own rate, from `CharacterDef.moveSpeed` through `Movement`, + 2 cells tolerance, Long arithmetic to prevent overflow; updates in the same millisecond are checked too). A phased player walks through walls but is checked at their phased pace (twice their own, `Movement.phasedStepIntervalMs`), with a dash's reach on top: the check used to be skipped for them, and for as long as a phase lasted a client could put its player anywhere. Position updates older (by sequence number) than the newest position already applied are dropped, since positions are absolute, and so is every update from a player who is dead (see *Position authority*). Teleports go through `common/model/Teleport.scala` on both sides: the client picks a star's or blink's landing cell with it and the server checks with it, because a teleport the client shows and the server refuses snaps the player back. A star is applied by its TCP item packet, not by letting a UDP jump through; a refused one comes back as `ItemAction.USE_REJECTED`. Projectile spawn validated against player position (max 3 cells); the shooter (nobody dead, held or phased fires — a phase's last 250ms excepted, since the client's ends a trip across the wire before the server's); the heading, which is only a direction (NaN/Inf rejected, anything shorter than half a unit refused, the rest normalised, and a ground slam's zeroed so it lands on its caster — taken as a velocity, (1, 1) flew 41% faster and (0, 0) never moved, so it never reached its range and lay where it was fired for the rest of the match); the charge level (0-100, and for the primary no more than the time since that attack's last cast, or since the respawn, allows: `PacketValidator.chargeAllowed`; every other attack is uncharged. Taken as the client said, a client could fire full charges at the primary's fire rate); and the attack that fired it: a spawn request names its `AttackSlot` (primary, Q, E, or the burst shot — the primary along `AttackSlot.BurstDirections`, 8 a cast on `BURST_SHOT_COOLDOWN_MS` — carried in the unused projectile-ID field), its type must be that attack's, and each attack has its own clock — a new cast after 80% of that attack's cooldown (`SHOOT_COOLDOWN_MS` for the primary), at most its own projectile count per cast (3 for a gem-boosted primary, a fan's count). **Don't go back to inferring the attack from the projectile type**: many characters fire one type from two attacks (Bear's Maul is eight of its primary's claws), and judged as a primary burst the ring was cut to the three projectiles the fan sends first, straight behind the caster. `AbilityCastValidationTest` pins this across the roster. Health is the server's own: the health a client reports is never read.
5. **Auth Hardening** — Constant-time hash comparison (`MessageDigest.isEqual`), dummy hash on username-not-found (prevents timing enumeration), password minimum 6 characters.
6. **Replay Protection** — `PacketValidator` tracks sequence numbers per player with a sliding window bitmap (`SEQUENCE_WINDOW_SIZE = 1024`) for UDP out-of-order tolerance. TCP enforces strictly increasing sequence numbers. Duplicate/replayed packets are rejected. Issuing a session token resets the player's sequence tracking (`resetSequences`): the new session's client counts from zero, and when a re-login closed a channel the server still had open, the old session's numbers used to stay and every packet of the new one was dropped as a replay. `SessionTest` pins it.
7. **UDP Source Validation** — Server records each player's TCP connection IP (`playerTcpAddresses`). UDP packets are only accepted if the sender IP matches the player's TCP IP. It is a cheap first filter, not proof: a source address can be forged, which is why the rate limit waits for the signature (3).
8. **Session Token Expiration** — Tokens expire after `SESSION_TOKEN_LIFETIME_MS` (1 hour). The cleanup loop removes expired tokens and closes the player's TCP channel, forcing re-authentication.
9. **Client Disconnect Recovery** — `NetworkThread` uses Netty `IdleStateHandler` for read timeout detection (`CLIENT_TIMEOUT_MS`). On disconnect, a callback notifies `GameClient` which clears game state and can transition the UI back to the login screen. Incoming packet queue is bounded (`INCOMING_QUEUE_CAPACITY = 8192`) to prevent memory exhaustion. Sequence numbers reset on reconnect.

### Position authority

The client moves its own player and the server checks every step (item 4 above). The server
moves a player itself only through a **server move** — a pull, a knockback, a vortex, a
teleport-behind, a respawn, a freeze or root holding them where it has them, a refused star, or
a correction — made with `GameInstance.moveByServer` / `holdByServer`, which count it
(`Player.recordServerMove`) and send the player their state over TCP, so a move can't be lost.

- `PlayerUpdatePacket` bytes [45-48] carry the count: the server's updates about a player say how
  many server moves it has made them, and the client's updates echo the count it has seen.
- **The client takes a position from the server only from an update with a count it hasn't
  seen** (`GameClient.processPacket`). Every other update about it — regen and burn ticks, hits,
  lifesteal — carries wherever the server last heard it was, a step or two behind a player who
  is walking, and snapping to those rubber-banded players back all through a match.
- **The server drops a step whose count is behind the player's** (`ClientHandler`): it was sent
  before the client knew of the move and would drag the player back. Pushes used to be undone
  that way, and the old half-second "server teleported" window, which swallowed the owner's
  every step after a teleport-behind and then refused them all as too far, is gone.
- A lone refused step says nothing (it is usually a race the server catches up with, like a step
  overtaking its star), but refusals that go on for 250ms get the client corrected, at most every
  500ms: it no longer takes positions from ordinary updates, so nothing else would tell it.
- What the rest of the match is told about a player is the server's view (`GameInstance.
  stateUpdate`): position where the server holds it, all eight status flags, character, team.
  Relaying the client's claim with four flags showed rooted players walking and blinked a
  burning, rooted, slowed or sped-up player's effects off on every step.
- A match starts where the server placed each player: the client takes its spawn from its own
  `PLAYER_JOIN` echo and sends nothing from `setWorld`. Picking one itself used to put players
  on the same spawn and show everyone teleporting at the start.
- **The dead don't move.** A client goes on sending steps until it hears it has died; the server
  ignores every update from a dead player (`ClientHandler.handlePlayerUpdate`). Taken, the body
  walked on across everyone's screens and picked up whatever it passed, which the respawn then
  threw away. The respawn is a server move, so nothing sent from the last life counts after it.
- **A hold holds against every way of moving.** While a root or freeze lasts the server applies no
  step, blink or dash, and refuses a star; the client doesn't blink or dash while rooted, or use a
  star while held, and neither do bots. The client used to blink anyway: it showed the jump, the
  server kept the player where they were and said nothing, and the two disagreed until its later
  steps were refused.

`PositionAuthorityTest`, `OnHitEffectsTest`, `PhaseShiftTest`, `TeleportValidationTest` and
`GameClientPositionTest` pin all of this.

### Sessions, reconnects and names

- **A join from a player the match already has is a rejoin, and takes nothing from the join**
  (`ClientHandler.handlePlayerJoin`): the player's connection is rebound, and the client is sent
  the match — everyone in it with their teams, items, traps, its own state, the opening — but
  where the player is and how much health they have stay the server's. It used to put them
  wherever the join said and heal them to full: a teleport and a heal, a revival for the dead,
  whenever a client sent a join. **A join never puts anyone into a running match**
  (`GameServer.handleGlobalConnect` only routes one to a match that has the player): a client that
  sent `PLAYER_LEAVE` and then `PLAYER_JOIN` came back fresh, wherever it said, as any character.
- **One session an account.** A login while the server still holds another connection open for
  that account closes it and ends that session as a disconnect would (`GameServer.
  leaveEverything`): out of the ranked queue, its lobby, and its match. It used to close the
  channel alone, and the player stayed in the match — a target standing still — and in the lobby,
  so every lobby the new session tried was refused as "already in a lobby" until the match ended.
- **A player goes by the name they logged in with** (`GameServer.accountNames`), not by the name
  in their join, which let anyone appear in a lobby or in chat as anyone else.
- **Lobby 0 is no lobby** — on the wire and in the client — so `LobbyManager` never hands it out
  when its numbers wrap at 32768.
- **Entering a lobby leaves the ranked queue**, and the matchmaker leaves out anyone it finds in
  a lobby (it works from a snapshot): a player queued while in a lobby was taken into the ranked
  match and left behind in the other as a member who would never come back.
- The client ignores an update about a player who has left the match (`GameClient.
  departedPlayers`): updates come over UDP and the leave over TCP, and one arriving after the
  leave brought them back as a player called "Player" who never went away.

`ReconnectTest`, `AuthFlowTest`, `LobbyManagerTest`, `RankedQueueTest` and `GameClientMatchTest`
pin these.

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

`PLAYER_UPDATE` is the packet with the most in it, and the one new fields keep being found for.
Its payload:

| Bytes | Field |
|---|---|
| [21-28] | position x, y |
| [29-32] | colour (ARGB) |
| [33-36] | timestamp |
| [37-40] | health |
| [41] | charge level (0-100) |
| [42] | effect flags: 0x01 shield, 0x02 gem, 0x04 frozen, 0x08 phased, 0x10 burning, 0x20 speed boost, 0x40 rooted, 0x80 slowed |
| [43] | character id |
| [44] | team id |
| [45-48] | server moves (see *Position authority*) |
| [49] | effect flags 2: bit 0 stunned, bit 1 poisoned, bit 2 barrier up, bits 3-7 free |
| [50-51] | aim angle, `angle / 2pi * 65536` (`PlayerUpdatePacket.encodeAimAngle`) |
| [52] | slow strength as a percentage (0-100): how fast the player moves while slowed |
| [53-54] | how long a raised barrier has left, in ms (server -> everyone; 0 otherwise) |
| [55-63] | reserved, zero |

A stunned player has both 0x04 and flags2 bit 0 set: the freeze bit is what holds them, the
stun bit only says what to draw. Byte [49] bit 2 is a raised barrier, bytes [50-51] the way
it faces and [53-54] how long it has left (see *Barriers*): a client sends its aim there while
its barrier is up, and the server writes the barrier's facing and its remaining time. All are 0
otherwise.

### Packet Types (18 total)

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
| 0x0A | GAME_EVENT        | TCP       | Kills, the clock, the match's opening |
| 0x0B | AUTH_REQUEST      | TCP       | Login/register                 |
| 0x0C | AUTH_RESPONSE     | TCP       | Auth result                    |
| 0x0D | MATCH_HISTORY     | TCP       | Game statistics                |
| 0x0E | RANKED_QUEUE      | TCP       | Ranked matchmaking             |
| 0x0F | LEADERBOARD       | TCP       | Rankings                       |
| 0x10 | SESSION_TOKEN     | TCP       | Session token delivery (post-auth) |
| 0x11 | CHAT_MESSAGE      | TCP       | Lobby and match chat           |
| 0x12 | TRAP_UPDATE       | TCP       | Traps placed, sprung, removed  |

A `PROJECTILE_UPDATE` carries one of these `ProjectileAction`s:

| Action | Meaning |
|---|---|
| 0 SPAWN | fired (a client's request carries its `AttackSlot` in the projectile-id field) |
| 1 MOVE | where it is now |
| 2 HIT | hit targetId and was used up |
| 3 DESPAWN | stopped by terrain or its range, or an explosive going off |
| 4 PIERCE | hit targetId and flies on |
| 5 BLOCKED | stopped on targetId's barrier, at x, y |

A `TRAP_UPDATE` carries one of these `TrapAction`s. The packet's own player id is the trap's
owner; its layout is in `TrapPacket`.

| Action | Meaning |
|---|---|
| 0 PLACE | a client asking to put one down (its `AttackSlot` is in byte [44]) |
| 1 SPAWN | it is on the ground here |
| 2 TRIGGER | it went off under the victim in bytes [45-60], and is gone |
| 3 REMOVE | gone without going off: it ran out, its owner left, or a newer one pushed it off |
| 4 REJECTED | the placement was refused; the placer's client gives the cooldown back |

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
   │<─── PLAYER_JOIN (broadcast) ─────│  (each client takes its spawn from its own)
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
1. Add case object to `Tile.scala` with the next id (an id is its atlas column, so no gaps),
   name, walkable, color. A non-walkable tile that isn't a block overrides `form` (`Pool` or
   `Prop`), and a prop names the `ground` it grows out of.
2. Add to `Tile.all` sequence, and bump the count in `WorldDataTest`
3. Write its draw function in `scripts/generate_tiles.py` and add it to `TILES` with its form. A
   block also needs `BLOCK_HEIGHTS` (and `ANIMATED_BLOCKS` if its frames move). A small prop can
   be enlarged with `PROP_SCALE`. A prop whose ground isn't grass goes in `tile_gallery.py`'s
   `PROP_GROUND`.
4. Run `python3 scripts/generate_tiles.py`, then look at it with `scripts/tile_gallery.py` and in
   the render bench (`TileTest` fails until the atlas has the new column)
5. Use in world JSON files (the map editor's palette lists every tile)

### Adding a New Packet Type
1. Add to `PacketType.scala` (new case object with unique ID, specify `tcp = true/false`)
2. Add to `PacketType.values` array
3. Create packet class extending `Packet` (use `Constants.PACKET_PAYLOAD_SIZE` for `ByteBuffer.allocate` in `serialize()`)
4. Add deserialization case in `PacketSerializer.deserialize()`
5. Handle in `GameClient.processPacket()` or `ClientHandler.processPacket()`

### Adding a New Trap
1. Add a `TrapType` id and a `TrapDef` to `common/model/Trap.scala`, and put it in `TrapDef.all`
   (the registry is built from that list by the object's own initializer). Pick an existing
   `TrapKind` or add one.
2. If it is a new kind, draw it: a `case` in `GLGameRenderer.drawTrap` and one in
   `drawTrapEffect` for it going off, plus `AbilityPreviewRenderer.drawTrapTop` for the character
   panel. Convex polygons only (`GRIDGAME_POLYCHECK=1`), and no per-frame allocation.
3. Give an ability `castBehavior = TrapCast(TrapType.X, range)` with `projectileType = -4` and
   `maxRange` equal to the cast's range (`CharacterRosterTest` checks both).
4. Update `i18n/messages_en.json` (`char.<id>.e.name`/`.desc` — `ContentCatalogTest` enforces it)
   and `docs/index.html`.
5. A new kind that should not sound like the others gets an entry in `AudioManager.playTrapSprung`
   and a generator in `scripts/generate_sounds.py` (see *Sound Effects & Music*).

### Adding a New Character
1. Add `CharacterId` entry in `CharacterId.scala` (next available ID byte)
2. Define `ProjectileDef` entries for the character's projectiles. `CharacterDef`'s initializer
   builds its defs and all 112 characters in one JVM method, and it is at the 64KB a method may
   be ("Method too large: CharacterDef$.<clinit>"), so new defs go in a builder beside it, as
   Plan 5a's do in `MeleeKitProjectiles.build(base)`, which copies a variant from `base(type)`
3. Register them: `CharacterDef` calls `ProjectileDef.register(...)` on its own and then on each
   builder's, in that order, so a builder finds every type it copies
4. Create `CharacterDef` val with abilities, stats, and sprite sheet path. Give it the `role` its
   primary's reach reads as (`CombatRole.forRange`), a `maxHealth` in that role's band and a
   `moveSpeed` of the role's pace (`Melee.speed`, `Skirmisher.speed`; ranged keeps the default
   1.0). `RosterBalanceTest` checks all three.
5. Add to `byId` map and `all` sequence in `CharacterDef`
6. Generate sprite sheet — either:
   - Create dedicated script: `scripts/generate_<name>.py` (uses `sprite_base.py`)
   - Or add to `scripts/generate_all_new_characters.py` batch generator
7. Run the script to produce `sprites/<name>.png`
8. Add its card to `docs/index.html` and run
   `bazel run //src/main/scala/com/gridgame/tools:gendocs` to write its role and health

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
  - `//src/main/scala/com/gridgame/client:projectile_gallery` (dev tool, not shipped)
  - `//src/main/scala/com/gridgame/client:render_bench`, `:ui_bench`, `:render_audit` (dev tools, not shipped)
  - `//src/main/scala/com/gridgame/common:common`
  - `//src/main/scala/com/gridgame/mapeditor`
  - `//src/main/scala/com/gridgame/mapeditor:mapeditor_windows`
  - `//docs:website`
  - `//src/main/scala/com/gridgame/server:server_lib`, `//src/main/scala/com/gridgame/client:client_lib`
    (the same sources as libraries, visible to the tests)

## Testing

```bash
bazel test //src/test/...                                        # everything
bazel test //src/test/scala/com/gridgame/server:combat_test      # one target
```

Each test file is its own `scala_junit_test` target, so each runs in a JVM of its own. The
suites mirror the source tree:

- `common/` — the model, the wire protocol (every packet round-trips in `PacketRoundTripTest`),
  the roster's data (`CharacterRosterTest`), and the maps (`WorldMapsTest`: spawns on open
  ground, all open ground one region). `ProjectileDefRegistryTest` must be the first thing in its
  JVM to look a projectile up, which is why it is a target, and a single test, of its own.
- `server/` — matches driven by hand through `ServerTestKit.scala`: `TestMatch` builds a
  `GameInstance` that is begun but never started (`GameInstance.begin`), so the test calls
  `tickProjectiles` / `tickPlayers` / `respawn` itself; every player gets an `EmbeddedChannel` for
  TCP and a UDP address on a channel attached with `GameServer.attachUdpChannel`, and
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
- `client/render`, `client/gl` — the camera (`CameraTest`: the isometric mapping, the pixel grid,
  whether the view has run off the map) and the light pool (`LightPoolTest`). `TileTest`, in
  `common/model`, also holds the tileset to what the renderer assumes: a flat tile is exactly
  its diamond, a block covers its whole footprint.
- `client/` — `ClientTestKit.scala`: `TestClient` is a `GameClient` whose packets go to a
  capture (`GameClient.packetSink`) and which is handed the server's with `processPacket`.
  The screen tests (`LobbyRoomScreenTest`, `ScoreboardScreenTest`, `CharacterSelectionPanelTest`)
  build the real JavaFX screens, never shown, on the FX thread via `Fx { ... }`, and click
  them. They set `GRIDGAME_AUDIO=off`, and they must not call `Messages.setLocale`, which would
  save a language to the player's own settings. `ContentCatalogTest` ties `i18n/messages_en.json`
  to `CharacterDef`: the catalog wins over the code (`I18n.tOr`), so renaming an ability in
  `CharacterDef` alone leaves the old name on every screen, silently. Regenerate the entries
  with `bazel run //src/main/scala/com/gridgame/tools:gencontent 2>/dev/null`.
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
# Build fat JARs
bazel build //src/main/scala/com/gridgame/client:client_deploy.jar
bazel build //src/main/scala/com/gridgame/client:client_windows_deploy.jar

# Create a GitHub release with both JARs
gh release create v1.0.0 \
  bazel-bin/src/main/scala/com/gridgame/client/client_deploy.jar#"Grid Game (macOS)" \
  bazel-bin/src/main/scala/com/gridgame/client/client_windows_deploy.jar#"Grid Game (Windows)"
```
