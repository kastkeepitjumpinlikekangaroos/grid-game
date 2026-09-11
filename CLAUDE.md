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

# Run the client with no sound at all (automated runs, a misbehaving audio device)
GRIDGAME_AUDIO=off bazel run //src/main/scala/com/gridgame/client:client

# Performance dev tools (see Client Memory & Performance)
bazel run //src/main/scala/com/gridgame/client:render_bench   # a busy match, no server
bazel run //src/main/scala/com/gridgame/client:ui_bench       # the JavaFX menus
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
| `GLProjectileRenderers.scala` | ~5800 | All 150 projectile type renderers (15 pattern factories + 33 specialized renderers + the local-frame silhouette system) |
| `ShapeBatch.scala` | ~380 | Batched colored 2D primitives: fillRect, fillOval, fillOvalSoft, fillPolygon, fillArcBand (ring segment with an alpha ramp — gauges, crescents, shockwaves), fillStarFlare (4-point glint), strokeLine, strokeLineSoft, strokeArc, strokeOval, strokePolygon. Supports additive blend mode toggle, plus an alpha multiplier and a scale-about-pivot applied to every vertex (`setAlphaMultiplier` / `setScaleAbout`; reset by `begin`). |
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
| `EntityCollector.scala` | Collects items/projectiles/players by grid cell for depth-sorted rendering. Cells are a flat grid over the visible window, each a linked list of pooled entries (`takeCell` / `takeRemaining`) — no map, no boxed keys, no allocation per frame |

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

Both batches map with `nglMapBufferRange` (a raw address) rather than `glMapBufferRange`,
which wraps every mapping in a new `ByteBuffer` because each flush maps a different range,
and write vertices with `MemoryUtil.memPutFloat` into their staging memory rather than
`FloatBuffer.put`. That makes a primitive's cost its arithmetic: the puts' limit checks and
position stores were the largest single cost of building a busy frame. Every primitive must
reserve its vertices with `ensureCapacity` before writing them; `vertex` only has a backstop.

### Projectile Rendering System
All 150 projectile types are registered in `GLProjectileRenderers.registry` (`Map[Byte, Renderer]`, flattened into `_rendererLUT` for O(1) lookup with no `Option` allocation). Projectiles use **standard alpha blending** for solid, visible shapes — the bloom post-processor provides natural glow on bright elements.

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
a disc. That produced two problems:
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

**15 pattern factories** (configurable colour + size, most also taking a `kind`):
- `energyBolt(r, g, b, size, style)` — glowing orb. `style` picks an **outer** silhouette
  (0 plain + leading crescent, 1 fire tongues, 2 rune ring, 3 soul wisp with a tail and
  eyes, 4 nebula cloud). The outer shape is what distinguishes bolts; inner detail is
  invisible at the size a projectile is actually displayed.
- `laserBolt(kind, …)` — blaster bolt: a short capsule, round at the hitbox and drawn to a
  point behind, with a dissolving afterglow. Kinds: plain, prismatic fringes (Photon),
  rings of force pulsing off the head (Cyclops).
- `railSlug(…)` — a dense dart shedding electromagnetic coil rings that widen and fade
  behind it (Railgunner).
- `siphonVortex(kind, …)` — drain abilities as a travelling whirlpool, motes spiralling
  *into* a dark core: blood sheds drips, life drain beats a heart, soul drain stares back.
- `graspingClaw(kind, …)` — grab-and-pull as three talons curling shut around a knot:
  thorny vine with leaves (vine whip, root pull) or suckered tentacles.
- `gorgonEye(petrify)` — Medusa's gaze as a blinking almond eye with a slit pupil, ringed
  by crumbling stone.
- `bladeSpinner(kind, …)` — thrown weapon tumbling end over end (axe, bone axe, katana,
  chef's knife, sword, femur, cursed blade, playing card). Sells the rotation with a
  swept arc band and silhouette ghosts rather than by smearing the shape.
- `flyingShaft(kind, …)` — shaft flying point-first (spear, arrow, poison arrow, blowdart,
  thorn, ice spike, void lance).
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
- `chainProj(kind, …)` — thrown restraint: a grappling hook with rope paying out behind
  it only as far as the throw has travelled; a tumbling manacle trailing swinging links;
  a loop of links spinning around a padlock.
- `bulletProj`, `fistProj` — small fast round; gauntleted punch.

**33 specialized `draw*` renderers** for one-off projectiles: fireball (spiral fire arms),
lightning (`lightningBolt(r, g, b)` — colour is a parameter so a storm reads yellow and a
tesla coil reads arc-cyan), frost comet (ice beam), grab paw, bandage wad, tongue lash,
boulder (faceted tumbling hull), shark jaw, bat swarm, shadow bolt, inferno blast, geyser,
wail, raise dead, and more.

**Things to watch when editing this file:**
- **Kind constants must be defined above `registry`.** `registry` is a `val` built while
  the object initialises, in textual order, so a `private val FOO_KIND = 3` declared
  *below* it still reads `0` when `factory(FOO_KIND, …)` is evaluated — it silently
  renders the wrong kind. New factories and their constants go in the sections above the
  registry.
- `fillArcBand` ramps alpha **along the sweep**, not radially. A radial falloff has to be
  built by nesting bands at constant alpha; using the ramp for it leaves one horn of a
  crescent bright and the other invisible.
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
   terrain bands

#### Projectile gallery (dev tool)

```bash
bazel run //src/main/scala/com/gridgame/client:projectile_gallery -- out/dir
bazel run //src/main/scala/com/gridgame/client:projectile_gallery -- out/dir --bench
```

Renders every registered projectile type into contact-sheet PNGs (10 pages x 4 animation
ticks) plus an `index.txt` naming each cell. It also writes `impacts_NN.png` (projectiles sinking
into a wall block across the fade) and `flyers_00.png` (wall-passers crossing one). Each cell draws one projectile over real
isometric tiles banded dark stone / grass / sand, at `CAMERA_ZOOM`, with a 48-unit player
footprint box for scale — a projectile that reads on one ground can disappear on another,
and judging any of this at 1:1 flatters it by a third. This is the loop to use for any
projectile art change; it needs no server, no login and no match. Its target compiles only the
ten GL files it needs, so it keeps building while unrelated client code is mid-edit.

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

Choosing Low outright (`--quality=low` / `GRIDGAME_QUALITY=low`, not auto) also sets
`prism.allowhidpi=false`, so the JavaFX menus draw at 1x on a HiDPI screen and the OS scales
them up: softer text, a quarter of the pixels per repaint, and a quarter-size window surface
pool (see Client Memory & Performance). It must be set before JavaFX starts, which is why
auto — which only steps down mid-match — can't do it.

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
- **A projectile's head sits on its hitbox** — nothing is drawn ahead of `(sx, sy)`; trails,
  tethers and wakes stream out behind it and fade (`fadeLine`). Beams, tethers, lightning,
  the rocket and the jaw used to run a line several tiles ahead of the hitbox to a disc,
  which both looked like a snake and showed the head arriving 100–150px before the damage.
- **Projectile collision uses the drawn tile extents** — `Projectile.getCellX/Y` is
  `floor(x + 0.5)` because tiles are drawn centred on integer coordinates. Truncating put
  walls and map edges half a tile off their sprites, which is what made projectiles glitch
  into walls and off the map. A stopped projectile then sinks into the face it struck rather
  than blinking out, and wall-passers are drawn flying over the terrain.
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
# --projectiles=, --w= --h=, --screenshot=out.png
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
python3 scripts/generate_sounds.py   # -> sounds/*.wav (182 files, ~20s)

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
same reason `projectile_gallery` does — 182 assets cannot be judged one at a
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
  - `//src/main/scala/com/gridgame/client:render_bench`, `:ui_bench` (dev tools, not shipped)
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
