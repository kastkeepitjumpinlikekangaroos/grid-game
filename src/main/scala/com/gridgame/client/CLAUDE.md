# client

The game client. Its menus are JavaFX (login, lobbies, character select, scoreboard); a match is
drawn with OpenGL 3.3 core into a GLFW window of its own. Every package is a Bazel library and
depends only on the ones above it (BUILD.bazel here):

```
gl                   OpenGL and GLFW primitives, knowing nothing of the game        (gl/CLAUDE.md)
audio, i18n, net     sound (audio/CLAUDE.md), the UI's text, the connection to the server (held
                     to the identity met there before: KnownServers, PinnedTrustManager), where
                     "Remember me" keeps a login (LoginStore), what a match's recording is told
                     of every packet (PacketTap) and a problem report's own connection
                     (UploadConnection)
diagnostics          the client's log on disk, the match it is recording, the moments marked in
                     it, what went wrong, the run before this one if it crashed, and the problem
                     reports made of them, waiting in the outbox     (design/diagnostics.md)
game                 GameClient: everything the client knows and does in a session  (game/CLAUDE.md)
render/projectiles   how every projectile type is drawn                    (render/projectiles/CLAUDE.md)
render/blasts        how every explosion and splash is drawn               (render/blasts/CLAUDE.md)
render               the renderer: core, the painters (world/, hud/), GLGameRenderer (render/CLAUDE.md)
input                keyboard, mouse, controller
ui, ui/screens       the menus' theme and widgets, and a class per screen   (ui/CLAUDE.md)
devtools             the benches, the render audit, the golden frames, the projectile gallery
.                    ClientMain (the JavaFX app, which also logs in and stays logged in:
                     design/networking.md, *Staying logged in*) and MatchWindow (the match's window)
```

The binaries sit on the libraries and add each platform's JavaFX and native jars, which the
libraries only compile against: `client` and the dev tools the build machine's (a Mac's, Linux's on
x86_64 or arm64, Windows'), and `client_windows`, `client_linux` and `client_linux_arm64` their own
system's, from any machine; `client_lib` is every library, with the build machine's jars, for the
tests.

## The two windows

When a match starts, `MatchWindow.open` hides the JavaFX stage and creates a GLFW window with an
OpenGL context. The game loop runs as a JavaFX `AnimationTimer` (it fires on the main thread, which
on macOS both GLFW and OpenGL require): it polls GLFW's events, updates the input handlers and the
controller, and has the renderer draw a frame. On game over the GLFW window is hidden and the
JavaFX stage shown again, on the scoreboard (or the lobby browser if the match was left); the
window is destroyed a couple of seconds later, once macOS has finished with it (`GLWindow.retire`:
destroyed straight away in macOS's own full screen, it crashed the client). Everything the renderer
reads of the game is the `GameClient`'s, which the packet thread writes (game/CLAUDE.md).

- **GLFW window swap** — hiding JavaFX Stage and creating a GLFW window avoids FBO→WritableImage pixel-copy overhead. Both use Cocoa NSWindows on macOS and coexist safely, as long as the GLFW one is never destroyed while AppKit is still busy with it (gl/CLAUDE.md, *A window macOS may still be using is retired*).
- **AnimationTimer game loop** — fires on the FX Application Thread (main thread on macOS), which is required for both GLFW and OpenGL calls. No threading complexity.
- **JavaFX UI retained** — Login, lobby, character selection, and scoreboard remain in JavaFX. Only in-game rendering uses OpenGL.
- **The match draws its own pointer** — over the GLFW window the system's is hidden and the renderer draws a crosshair after each frame, where the mouse is, while the window has the focus (render/CLAUDE.md, *The pointer is ours*). The menus keep the system's.
- **The match is recorded, and a failed frame doesn't close the game** — each frame, every key and button, and four times a second what the client thinks of us go into the match's recording for a problem report, allocating nothing; F8 marks the moment with a picture of the frame, read back before the swap (`MatchWindow.markMoment`), and the last fifteen seconds of small ones the window keeps as it goes (`gl/FrameHistory`, read back through pixel buffers without waiting); a frame long after the last is noticed. A frame that throws takes the window down and brings the menus back with the report offered, where it used to quit the app (design/diagnostics.md).

## Diagnostics

`ClientMain.main` sets them up before anything else (`Diagnostics.install`): from then on everything
the client prints goes to `client.log` in the player's settings folder as well as the console, a
watch is kept on the JavaFX thread for freezes, an exception nothing catches is logged and noted, and
a run that never closed properly is found and offered as a report after the next login. The app's
clients are given `Diagnostics.packetTap` (`ClientMain.newGameClient`), which records a match from
its GAME_STARTING to its end; a dev tool's or a test's client, and any process that never calls
`install`, records nothing and writes nothing there. All of it: design/diagnostics.md.

`report_replay` (devtools `ReportReplay`, on `ReplaySession`, the part without a window) redraws any
moment of a reported match through the real GameClient and GLGameRenderer, from its recording; its
keys, headless options and limits are in design/diagnostics.md, *Reading a report*.

## On Linux

The same code runs on Linux (x86_64 and arm64, Chromebooks' Linux among them), with these things
of its own, each found running the client there:

- **Both windows are X11's.** JavaFX's always are (on a Wayland desktop, through XWayland), and
  GLFW is told to be too wherever there is an X display (gl/CLAUDE.md, *On Linux, GLFW runs on
  X11*).
- **Both are one app's.** JavaFX names its windows after the Application's class
  (`com.gridgame.client.ClientMain`, its X11 class), and `MatchWindow` gives the match's window the
  same (`GLWindow`'s `windowClass`): a desktop groups windows by it and finds their launcher by it,
  so the menus and the match share one entry and one icon in a dock or a Chromebook's shelf. The
  `.deb`'s menu entry claims that class (`StartupWMClass`). Under GLFW's own name, its title, the
  match was a second app.
- **The menus' stage is maximized as well as given the screen's size** (`ClientMain.start`, and
  `MapEditorApp`). Under openbox (LXDE's and LXQt's window manager) JavaFX's GTK window given a size
  settled on 200x200 as it was shown, whatever the size; maximized, it filled the screen under
  openbox, metacity, xfwm4, fluxbox and icewm, as sized it did under all but openbox. Without a
  window manager, sizes stuck either way. The same with JavaFX 21.0.12.
- **The match is as big as the menus on a scaled desktop.** X11 leaves an app to scale itself, and
  GLFW sizes windows in the screen's pixels: on a desktop at 200% the match's window filled a
  quarter of the screen and drew everything half size. `MatchWindow` gives it the menus' scale
  (gl/CLAUDE.md, *On Linux, GLFW runs on X11*). And `--quality=low` leaves the menus at that scale
  (`prism.allowhidpi` is left alone on Linux), where elsewhere it draws them at 1x for the system
  to scale up: on Linux nothing did, and they came out half size.
- **No AWT toolkit.** `setDockIcon` (the Mac's dock) asks AWT about a taskbar only on a Mac: on
  Linux the question started AWT's X11 toolkit, a thread (`AWT-XAWT`) and a display connection of
  its own, to answer no, and with it GTK warned `XSetErrorHandler() called with a GDK error trap
  pushed` at every start (Xlib's error handler is the whole process's, and the toolkit sets it).
  GLFW sets it for a moment around some of its calls, and the warning can still turn up now and
  then in a match (it did once, at no action in particular, in the matches played to test this):
  GTK only warns. The match's text is still drawn with AWT's fonts, which need no toolkit.

What it was checked on (2026-10-16, Docker on this Mac), all under Xvfb with Mesa's llvmpipe
(OpenGL 4.5 core) and openbox: the arm64 jar through sign-up, a practice match (moving, shooting, Q,
F11 and back, Esc twice to the scoreboard), a second match, and the window's close button quitting
the app; the x86_64 jar, emulated, through sign-up and a match; at 200% (`GDK_SCALE=2` on a
3200x1800 screen) the menus, a match, the pointer and the aim, and `--quality=low`'s menus; the
`.deb`s of both processors installed on a bare Debian 12 and played from there, the arm64 one
upgraded and removed too, and the map editor's beside it; the map editor's jar; JavaFX on XWayland
under weston (a probe for GLFW there: gl/CLAUDE.md). Not on a Chromebook, a real GPU, Wayland's own
scaling, or with sound: the containers have no sound card, and the client goes silent without one,
as anywhere.

## Memory and performance

The target is an old machine with little RAM. Two dev tools measure it, and any change that
could move these numbers should be checked with them rather than guessed at:

```bash
# A busy match (16 players, 150 projectiles of every type, each thrown by a character who
# throws it, deaths, explosions) through the
# real GLGameRenderer in a real window, no server: frame CPU/GPU time, bytes allocated per
# frame on the render thread, GC count, process footprint. --quality=, --players=,
# --projectiles=, --w= --h=, --screenshot=out.png. --travelling throws only projectiles that
# travel: without it the ground slams, never retired, are 120 of the 150 a few seconds in
# (render/CLAUDE.md, *The render bench's projectiles are mostly ground slams*). --effects puts a
# status effect on every player (frozen, stunned, poisoned, burning, rooted, slowed, boosted),
# which the default scene has none of — off by default so the numbers below stay comparable.
# --barriers has every fourth player hold a barrier up, turning and being struck, half of them
# allies, and
# --traps scatters 30 traps of every kind, half ours and half an enemy's, some arming, some
# going off. --divider raises the wall a Teams match opens with, beside the local player, and
# --ceasefire holds every attack the way a free-for-all's opening does (=<seconds> on either to
# watch it end). --aim has us charge a shot all through the run (as the Tidecaller, begun again
# every five seconds, the aim turning), so its lane on the ground is in the frame, and --hold=<kind>
# has us aim an attack of that kind (the primary held, or a shot, blast, fan, dash, blink or trap
# taken up and never cast, as the first character with one), its aim turning round us. The pointer is
# drawn over every frame, circling the screen, as the match's window draws it. --history keeps the
# last seconds as a match's window does for a problem report (gl/FrameHistory) and says what capturing
# cost; --history-out=dir writes the frames kept, to look at.
bazel run //src/main/scala/com/gridgame/client:render_bench -- --quality=low

# The JavaFX menus: the character select screen (the panel on a page as the lobby screens frame
# it: the world behind it, the top bar), then a match start (stage hidden), a second visit, and
# the same screen idle — live/committed heap, CPU, footprint for each phase.
bazel run //src/main/scala/com/gridgame/client:ui_bench
```

On macOS both print the footprint as Activity Monitor counts it, split into `graphics`
(GPU memory), `IOSurface` (window surfaces), and `VM_ALLOCATE` (mostly the Java heap); the render
bench also prints `driver graphics`, the graphics memory the driver owns rather than the game's
textures and buffers, which is where a window's buffers are counted (gl/CLAUDE.md, *The window
has no depth or stencil buffer*).

What keeps a frame's CPU and GPU cost down, and what to measure: render/CLAUDE.md (*Performance*);
the menus: ui/CLAUDE.md.

### JVM heap
The heap is capped and started small (`-Xms64m -Xmx768m` in the client's `jvm_flags`, and
passed through `--java-options` by `scripts/build_macos_app.sh` / `build_windows_exe.ps1` /
`build_linux_package.sh`);
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

### Linux and Chromebooks: the app with its own runtime, and a .deb

```bash
scripts/build_linux_package.sh                          # the client for x86_64 (or this Linux's processor)
scripts/build_linux_package.sh --arch arm64 --deb       # for arm64, and its .deb
scripts/build_linux_package.sh mapeditor --deb          # the map editor
#  -> dist/linux/<arch>/Grid Game/bin/Grid Game            the app, run where it is
#     dist/linux/grid-game-linux-<arch>.tar.gz              the same, to unpack anywhere
#     dist/linux/grid-game_1.0-<build>_<amd64|arm64>.deb    with --deb
```

The script builds `client_linux` or `client_linux_arm64`'s deploy jar here, then runs jpackage on
Linux, of that processor: on this machine if it is one, with a JDK 21 (and `dpkg-deb` and
`fakeroot` for `--deb`), and otherwise in a Docker container (`eclipse-temurin:21-jdk-jammy`, pulled
the first time, 1.3GB), which Docker Desktop on a Mac runs for either processor, x86_64 emulated (a
minute and a half for arm64 here, three for x86_64). jpackage doesn't cross-package, but Docker
gives it the Linux it needs, so both packages come from this Mac. What it makes, and why:

- **Its own runtime, cut down**: every JDK module but the incubating ones, without debug
  information, man pages or headers (170MB where the whole JDK, which the Mac and Windows
  scripts bundle, is 300). Modules aren't picked one by one: the game reaches some only now and
  then (the elliptic curves of the server's TLS are a module of their own in Java 21).
- **The `.deb` is how a Chromebook gets it**: with Linux turned on (Settings > About ChromeOS >
  Developers), a double-click in the Files app installs it, and the game is in the launcher. It
  depends on the libraries the game loads (GTK 3, libGL and Mesa's drivers, the X11 libraries GLFW
  loads, ALSA), by the names Debian 12 and Ubuntu 22.04 give them, which the "t64" packages of later
  releases provide, and on nothing else: its `control` is the script's own. jpackage's adds what it
  finds the runtime linking, by the names of the system it runs on, and built on Ubuntu 24.04 that
  was `libasound2t64`, which Debian 12 doesn't have: that package wouldn't have installed on a
  Chromebook. Built on 24.04 with the script's, it installed and ran on Debian 12. 101MB (arm64),
  231MB installed.
- **Its menu entry claims the game's windows** (`StartupWMClass=com.gridgame.client.ClientMain`,
  *On Linux* above), and is put in `/usr/share/applications` by the package's own `postinst` and
  taken out by its `prerm`: jpackage's use `xdg-desktop-menu`, which refuses wherever there is no
  `/etc/xdg/menus` (only a desktop's menu packages make one), and with it the whole install
  failed on a bare Debian 12. There `/usr/share/applications` may not be there yet either, and the
  `postinst` makes it (without, the install failed again on a fresh one).
- **Each build is a newer version** (`1.0-<UTC minute it was built>`), so installing a new `.deb`
  over an old one upgrades it: at the same version apt would call it installed and keep the old.

The deploy jars alone (`client_linux_deploy.jar`, `client_linux_arm64_deploy.jar`) run on any Linux
with Java 21 and a desktop: `java -Xms64m -Xmx768m -jar …`.
