# client

The game client. Its menus are JavaFX (login, lobbies, character select, scoreboard); a match is
drawn with OpenGL 3.3 core into a GLFW window of its own. Every package is a Bazel library and
depends only on the ones above it (BUILD.bazel here):

```
gl                   OpenGL and GLFW primitives, knowing nothing of the game        (gl/CLAUDE.md)
audio, i18n, net     sound (audio/CLAUDE.md), the UI's text, the connection to the server
game                 GameClient: everything the client knows and does in a session  (game/CLAUDE.md)
render/projectiles   how every projectile type is drawn                    (render/projectiles/CLAUDE.md)
render/blasts        how every explosion and splash is drawn               (render/blasts/CLAUDE.md)
render               the renderer: core, the painters (world/, hud/), GLGameRenderer (render/CLAUDE.md)
input                keyboard, mouse, controller
ui, ui/screens       the menus' theme and widgets, and a class per screen   (ui/CLAUDE.md)
devtools             the benches, the render audit, the golden frames, the projectile gallery
.                    ClientMain (the JavaFX app) and MatchWindow (the match's window)
```

The binaries (`client`, `client_windows`, the dev tools) sit on the libraries and add each
platform's JavaFX and native jars, which the libraries only compile against; `client_lib` is every
library, with the build machine's jars, for the tests.

## The two windows

When a match starts, `MatchWindow.open` hides the JavaFX stage and creates a GLFW window with an
OpenGL context. The game loop runs as a JavaFX `AnimationTimer` (it fires on the main thread, which
on macOS both GLFW and OpenGL require): it polls GLFW's events, updates the input handlers and the
controller, and has the renderer draw a frame. On game over the GLFW window is destroyed and the
JavaFX stage shown again, on the scoreboard (or the lobby browser if the match was left).
Everything the renderer reads of the game is the `GameClient`'s, which the packet thread writes
(game/CLAUDE.md).

- **GLFW window swap** — hiding JavaFX Stage and creating a GLFW window avoids FBO→WritableImage pixel-copy overhead. Both use Cocoa NSWindows on macOS and coexist safely.
- **AnimationTimer game loop** — fires on the FX Application Thread (main thread on macOS), which is required for both GLFW and OpenGL calls. No threading complexity.
- **JavaFX UI retained** — Login, lobby, character selection, and scoreboard remain in JavaFX. Only in-game rendering uses OpenGL.

## Memory and performance

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

What keeps a frame's CPU and GPU cost down, and what to measure: render/CLAUDE.md (*Performance*);
the menus: ui/CLAUDE.md.

### JVM heap
The heap is capped and started small (`-Xms64m -Xmx768m` in the client's `jvm_flags`, and
passed through `--java-options` by `scripts/build_macos_app.sh` / `build_windows_exe.ps1`);
the live set is well under 100MB. `ClientMain.tuneHeap` sets, at runtime so it applies to
`java -jar` too, `G1PeriodicGCInterval=30000` and `Min/MaxHeapFreeRatio=10/30`: HotSpot only
returns memory after a concurrent cycle or full GC, which a game this light on allocation
rarely triggers, so without them the heap stayed at its high-water mark in the menus.

## Packaging the apps

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
classpath, and only those (see *Bazel* in the root CLAUDE.md). But turning that into a real `.exe` (bundled JRE, no separate Java install
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
