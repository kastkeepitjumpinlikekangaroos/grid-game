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
| `GLGameRenderer.scala` | ~5850 | Main renderer: tiles, players, projectiles, items, status effects, HUD, aim arrow, backgrounds, death/teleport/explosion animations |
| `GLProjectileRenderers.scala` | ~5560 | All 150 projectile type renderers (11 pattern factories + 30 specialized renderers + the local-frame silhouette system) |
| `ShapeBatch.scala` | ~380 | Batched colored 2D primitives: fillRect, fillOval, fillOvalSoft, fillPolygon, fillArcBand (ring segment with an alpha ramp — gauges, crescents, shockwaves), fillStarFlare (4-point glint), strokeLine, strokeLineSoft, strokeArc, strokeOval, strokePolygon. Supports additive blend mode toggle. |
| `SpriteBatch.scala` | ~200 | Batched textured quads with per-vertex tint/alpha. Flushes on texture change. |
| `ShaderProgram.scala` | ~190 | GLSL shader compilation + embedded shader source: ColorShader (pos+color), TextureShader (pos+texcoord+color), BloomExtract, GaussianBlur, Composite (bloom+vignette+overlay) |
| `PostProcessor.scala` | ~230 | Post-processing FBO pipeline: Scene FBO → Bloom extract (half-res) → Blur H → Blur V → quarter-res pair → Composite |
| `GLTexture.scala` | ~195 | PNG loading via STB image → GL texture. FBO creation for render-to-texture. |
| `GLFontRenderer.scala` | ~160 | AWT-based font rasterization → GL texture atlas. Supports outlined text with drop shadows. Three sizes (16/24/48px). |
| `GLWindow.scala` | ~100 | GLFW window create/show/destroy/resize |
| `GLFWManager.scala` | ~25 | Singleton `ensureInitialized()` shared by ControllerHandler and GLWindow |
| `GLTileRenderer.scala` | ~130 | Loads `sprites/tiles.png` as GL texture; returns full or transparent-margin-trimmed TextureRegion per tile ID + frame |
| `GLSpriteGenerator.scala` | ~160 | Packs character sprite sheets into one growable GL atlas |
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
PostProcessor.beginScene()        -- bind scene FBO (sized by the quality tier)
GLGameRenderer.render()           -- all game drawing into scene FBO
  Background → Tiles → Items → Players → Projectiles →
  Status Effects → Aim Arrow → Animations
PostProcessor.endScene()          -- bloom extract → blur H → blur V →
                                     composite (scene + bloom + vignette + overlay)
                                     upscales to the real framebuffer
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

### Projectile Rendering System
All 150 projectile types are registered in `GLProjectileRenderers.registry` (`Map[Byte, Renderer]`, flattened into `_rendererLUT` for O(1) lookup with no `Option` allocation). Projectiles use **standard alpha blending** for solid, visible shapes — the bloom post-processor provides natural glow on bright elements.

Type alias: `type Renderer = (Projectile, Float, Float, ShapeBatch, Int) => Unit`

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

**11 pattern factories** (configurable colour + size, most also taking a `kind`):
- `energyBolt(r, g, b, size, style)` — glowing orb. `style` picks an **outer** silhouette
  (0 plain + leading crescent, 1 fire tongues, 2 rune ring, 3 soul wisp with a tail and
  eyes, 4 nebula cloud). The outer shape is what distinguishes bolts; inner detail is
  invisible at the size a projectile is actually displayed.
- `beamProj(r, g, b, worldLen, width, style)` — directional beam. 8 styles: laser, drain
  (back-flowing siphon), whip, ice, vine, stone, railgun, gravity.
- `bladeSpinner(kind, …)` — thrown weapon tumbling end over end (axe, bone axe, katana,
  chef's knife, sword, femur, cursed blade, playing card). Sells the rotation with a
  swept arc band and silhouette ghosts rather than by smearing the shape.
- `flyingShaft(kind, …)` — shaft flying point-first (spear, arrow, poison arrow, blowdart,
  thorn, ice spike).
- `spinner(r, g, b, size, pts)` — polar star; correct for the one thing that *is* a star
  (shuriken).
- `lobbed(kind, …)` — object on an arc (bomb, flask, shovel, hammer, horn, spiked mine,
  ice chunk, mud glob) with landing shadow, target ring and bounce.
- `aoeRing(kind, …)` — ground blast as filled shockwave bands over a darkened scorch.
  `kind` picks what it throws off: quake rubble, water, spores, flame, pressure rings,
  inward-falling motes, roots.
- `wave(kind, …)` — crescent sweep built from a real arc band whose centre is solved in
  the ellipse's own parameter space so it stays square to the travel direction at every
  heading. Kinds: wind, sand, sonic, flame, acid, impact, water.
- `chainProj(kind, …)` — tether: interlocking metal links with an anchor hook, or a rope
  of two braided strands with a grappling hook. Both sag between caster and head.
- `bulletProj`, `fistProj` — small fast round; gauntleted punch.

**30 specialized `draw*` renderers** for one-off projectiles: fireball (spiral fire arms),
lightning (`lightningBolt(r, g, b)` — colour is a parameter so a storm reads yellow and a
tesla coil reads arc-cyan), boulder (faceted tumbling hull), shark jaw, bat swarm, shadow
bolt, inferno blast, geyser, wail, raise dead, and more.

**Two things to watch when editing this file:**
- `fillArcBand` ramps alpha **along the sweep**, not radially. A radial falloff has to be
  built by nesting bands at constant alpha; using the ramp for it leaves one horn of a
  crescent bright and the other invisible.
- A block literal on the line after an expression is parsed as an *argument* to it
  (`val n = 9` followed by `{ … }` becomes `9 { … }`). Use a plain `var`/`while` at
  statement level rather than a `{ … }` wrapper.

To add a new projectile renderer:
1. Add an entry to the `registry` map in `GLProjectileRenderers`
2. Either use a pattern factory (`energyBolt(r, g, b, size, style)`, `bladeSpinner(kind, …)`,
   …), add a `kind`/`Part` array if the object has its own silhouette, or write a
   specialized `draw*` method
3. The renderer receives screen-space coordinates (sx, sy) already transformed from world space
4. Check it in the gallery (below) — judge at the size the player sees, over all three
   terrain bands

#### Projectile gallery (dev tool)

```bash
bazel run //src/main/scala/com/gridgame/client:projectile_gallery -- out/dir
bazel run //src/main/scala/com/gridgame/client:projectile_gallery -- out/dir --bench
```

Renders every registered projectile type into contact-sheet PNGs (10 pages x 4 animation
ticks) plus an `index.txt` naming each cell. Each cell draws one projectile over real
isometric tiles banded dark stone / grass / sand, at `CAMERA_ZOOM`, with a 48-unit player
footprint box for scale — a projectile that reads on one ground can disappear on another,
and judging any of this at 1:1 flatters it by a third. This is the loop to use for any
projectile art change; it needs no server, no login and no match.

`--bench` times a screenful of projectiles against a ground-only baseline, so an art
change can be checked against the frame budget instead of guessed at. Measured on this
machine after the silhouette pass: 16 projectiles cost **0.18 ms/frame** over a 0.72 ms
ground+post baseline — about 11us each, against a 16.7 ms budget.

### Post-Processing
Settings in `PostProcessor`: `bloomThreshold`, `bloomStrength`, `vignetteStrength`. Bloom
FBOs run at half resolution, plus a quarter-res pair for the wide glow. Composite shader
uses screen blending for bloom and smoothstep vignette, and gates its optional work on
`uSharpen` / `uGrain` / `uWideBloom` so the quality tiers can drop it without a second
shader. The four-tap unsharp mask is the composite's most expensive part — four extra
full-resolution texture fetches per pixel.

### Graphics quality tiers (`RenderQuality`)

The renderer is fill-rate bound, so `RenderQuality` exists to keep it playable on a weak
GPU. Set with `--quality=low|medium|high|auto`, `GRIDGAME_QUALITY`, or **F7** in game.
Default is `auto`: starts at High and steps down (never back up) after ~2s of frames slower
than 20ms, so an underpowered machine settles on its own; the first explicit choice — flag
or F7 — turns auto off.

`sceneScale` is the main lever. It sizes the scene/bloom/light targets, while the composite
still upscales to the display's real framebuffer and the HUD is drawn on top at full
resolution — so lowering it costs sharpness in the world only, never in text. It is
expressed against both the framebuffer and the logical window because the worst case is a
cheap machine driving a HiDPI screen: there the framebuffer is 2x the window, so the world
is being drawn at 4x the pixels the art carries (tiles are 40x56 magnified 1.6x). Medium
gives up that free 2x first.

Tiers also gate: bloom, the quarter-res wide bloom, composite sharpen and grain, the
dynamic light map, water reflections, the animated tile-overlay budget, background cache
interval, and particle emission rate.

### Key Design Decisions
- **Tiles are culled against the visible diamond, not its bounding box** — the screen rect
  maps to a diamond in world space, whose AABB holds ~2.7x as many cells as are on screen.
  `render` computes bounds in the projection's own axes (`u = wx - wy`, `v = wx + wy`) and
  clips each row exactly; verified against brute force over 400 camera positions with zero
  tiles missed and 6% overdraw. Entity cells walk a few rows further (`entPad`) because a
  player sprite and its name plate hang above their own cell.
- **Ground tiles draw a trimmed quad** — a flat tile only paints the bottom 40 of its 112
  atlas rows, so drawing the full cell rasterized ~2.8x the pixels it needed, over the whole
  screen. `GLTileRenderer` measures each cell's first non-transparent row at load time
  (so it follows whatever `generate_tiles.py` emits) and exposes `getTrimmedRegion` /
  `getTrimTopPx`. The UV mapping is exact — the trimmed quad samples the identical texel.
- **The character atlas grows instead of reserving every slot** — sized for all 112
  characters it would hold 128MB of texture memory for the whole session however few
  characters a match uses. It starts at 16 slots (16MB) and doubles to at most 64. A grow
  re-uploads the sheets already in it, and retires the old texture for `disposeRetired()`
  to free at the top of the next frame — never mid-frame, where a batch may still hold
  queued vertices naming it.
- **A projectile's silhouette carries its identity, not its palette** — the pattern
  factories used to describe shapes in polar form (a radius per vertex) or as a stroked
  line from the caster, so a whole family came out identical: every melee weapon was the
  same spinning lens, every thrown object the same grey disc, every wave the same
  triangle, and a spear or arrow was a ~190px hairline drawn `worldLen` world units long.
  Recognisable objects are now authored as convex `Part` silhouettes in a local frame and
  stamped per frame (see *Projectile Rendering System*), and the family factories take a
  `kind`. Colour differentiates within a family; shape differentiates between them.
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
crops/centers it, and exports at icon sizes. It draws through `sprite_base.ScaledDraw` at
`RENDER_SCALE` and applies the same `finish_frame` detail pass at each output size, so the
1024px icon is rendered at native resolution rather than a 64px sprite blown up 16x.
Used for the JavaFX Stage/Taskbar icon
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

**Every script goes through `sprite_base.generate_character`** — the individual
ones only own their `draw_<name>` function, they do not run their own render
loop. That single chokepoint is what lets a rendering change apply to all 112
characters at once, so keep it that way rather than re-adding a per-script loop.

#### Rendering pipeline (`sprite_base.py`)

Draw functions author in a 64x64 space (or 128x128, for `draw_generic_character`).
They never draw at that size:

```
ScaledDraw onto a 256px canvas  →  Lanczos down to 128  →  finish_frame  →  quantize
   (supersampling)                                          (detail pass)     (255-colour palette)
```

- **`ScaledDraw`** proxies `ImageDraw` and multiplies every coordinate, radius
  and stroke width. Box primitives map `x1` to `(x1 + 1) * scale - 1` so a
  scaled rectangle still exactly covers its source pixels and leaves no seam
  against an adjoining polygon; `point` fills a whole `scale x scale` block, or
  one-pixel highlights would render sub-pixel and vanish in the downsample.
  Stroke widths scale but are **not** boosted beyond that — bumping them merges
  adjacent thin strokes (a banshee's hair strands, medusa's snakes) into a blob.
- **`finish_frame`** = `add_contour` → `add_shading` → `add_grain`. All three
  derive from the frame's own pixels, so no character is hand-shaded:
  - `add_contour` dilates the silhouette by a pixel and lays a dark ring
    *behind* the frame (so the sprite's anti-aliased edge survives). This is
    what keeps characters readable over both pale sand and dark water.
  - `add_shading` bevels interior colour boundaries, rim-lights the fill just
    inside the upper-left silhouette, and occludes the lower-right, plus a
    top-to-bottom ambient ramp. Light comes from the upper left to match the
    ground shadow `GLGameRenderer.drawShadow` offsets to (+2, +1).
  - `add_grain` adds a fixed low-amplitude field — fixed, not per-frame, or it
    would crawl visibly across the four walk frames.
- Two masks protect the art from the shading. Dark **outline** pixels are never
  lightened (brightening them turns the rim light into a halo), and **emissive**
  pixels — bright *and* saturated, i.e. flame, plasma, glowing eyes — are never
  darkened, since those are exactly what the renderer's bloom keys off.
- Sheets are saved as a **255-colour palette PNG** with alpha in `tRNS`. The
  shading fills what used to be flat colour with gradients, which triples an
  RGBA PNG; quantizing is visually indistinguishable at the size a sprite is
  displayed and lands well under the original size. Palette alphas ≥250 are
  snapped to 255 (the octree splits on alpha too, and would otherwise leave
  every body pixel at 252-254). Both loaders expand it back to RGBA:
  `stbi_load_from_memory(..., 4)` on the GL side, `javafx.scene.image.Image`
  on the UI side.

Tuning constants (`BEVEL_LIGHT`, `RIM_LIGHT`, `GRAIN`, …) sit at the top of the
detail-pass section. They are deliberately restrained: a sprite is displayed at
`PLAYER_DISPLAY_SIZE_PX` (48) times `CAMERA_ZOOM` (1.6) ≈ 77px, and anything
heavier reads as noise rather than detail. **Judge any change at that size**,
not at the 128px sheet resolution.

### Sound Effects & Music
All audio is procedurally synthesized (numpy oscillators/noise, no samples or external
audio libraries — zero licensing concerns) into `sounds/*.wav`, mirroring how sprites are
generated.

```bash
# Requires numpy: pip install numpy
python3 scripts/generate_sounds.py   # -> sounds/*.wav (80 files, ~5s)
```

`scripts/generate_sounds.py` is a small sound-design toolkit, not just tone+noise
bursts — that distinction is what keeps sounds from reading as cheap beeps. It provides
FM synthesis (`fm`), a time-varying resonant state-variable filter (`svf`, for sweeps)
plus fast static resonant filters (`res_lp`/`res_bp`), `saturate`, `bitcrush`,
convolution `reverb`, `delay_fx`, `chorus`, and vowel `formant` filters (what makes the
wail/moan/growl sounds read as a creature rather than filtered noise). Every sound is
layered as **transient → body → texture → tail**.

Generators output mono; every sound then goes through the shared `master()` chain —
`transient_shape` → `compress` → `sub_boost` → `stereoize` (decorrelated width) →
`stereo_reverb` (separate impulse response per channel) → optional `auto_pan`/`ping_pong`
→ `limit`. Per-sound `level`s give the mix real dynamics (a stinger is quiet, thunder is
loud) instead of normalizing everything to the same loudness. Output is 16-bit **stereo**
44.1kHz; SFX tails are trimmed at -52dB and capped at 2.2s, since long quiet tails cost
file size but are inaudible under gameplay.

Pitched attacks are tuned to **A minor**, the key of both music tracks, so a firefight
stays harmonically coherent. `sounds/` is ~26MB, the largest asset directory — if that
becomes a problem, the two music loops are ~8.5MB of it.

- `sounds/atk_*.wav` — one sound per projectile-visual archetype. `AbilitySounds.scala`
  maps each of the 150 `ProjectileType` ids to one of these files, mirroring the
  many-to-one grouping already used by `GLProjectileRenderers.registry` (types that
  share a renderer share a sound).
- `sounds/spawn.wav`, `sounds/death.wav`, `sounds/dash.wav`, `sounds/teleport.wav`,
  `sounds/phase_shift.wav` — non-projectile events/cast behaviors.
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
  archetype, add a generator function + entry in `scripts/generate_sounds.py` and
  rerun it.

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
  - `//src/main/scala/com/gridgame/client:projectile_gallery` (dev tool, not shipped)
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
