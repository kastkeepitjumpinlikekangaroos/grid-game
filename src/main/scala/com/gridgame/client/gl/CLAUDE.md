# client/gl

The OpenGL and GLFW primitives the renderer draws with. Nothing here knows about the game: the
batches, shaders, textures, fonts, the window, the post-processor, the light map, the quality tiers,
the GPU profiler.

## The files

| File | Purpose |
|------|---------|
| `ShapeBatch.scala` | Batched colored 2D primitives: fillRect, fillOval, fillOvalSoft, fillPolygon (**convex only**), fillFan (star-shaped outline, fanned from an explicit centre — stars, sunbursts, faceted hulls), fillRibbon (a band given as an outer run plus the inner run reversed — crescent blades), fillArcBand (ring segment with an alpha ramp — gauges, crescents, shockwaves), fillStarFlare (4-point glint), fillRoundedRect(Gradient), strokeLine, strokeLineSoft, and the joined strokes — strokeArc, strokeOval, strokePolygon, strokeRect, strokeRoundedRect, strokePolyline, strokePolylineTapered, strokePolylineVar (a width and an alpha of its own at every point — a limb, a comet tail, a bolt's channel) (see *Strokes are one band*); and the smooth shapes, whose edges fade over a pixel — fillOvalSmooth, strokeOvalSmooth, strokePathSmooth (a path stroked, open or closed, an alpha at every point, offset to lie beside it), fillRibbonSmooth (fillRibbon's band with an alpha at every point) (see *Smooth edges*). Oval segment counts follow the oval's size on screen (`pixelsPerUnit`, set per pass). Every shape writes each vertex once and its triangles as indices (see *A shape's vertices are shared*). Supports additive blend mode toggle, plus an alpha multiplier and a scale-about-pivot applied to every vertex (`setAlphaMultiplier` / `setScaleAbout`; reset by `begin`). |
| `SpriteBatch.scala` | Batched textured quads with per-vertex tint/alpha, four vertices a quad and one element buffer for them all, and `drawQuad` for any four corners (the ground's diamonds), whose colour a batch made with a shader of its own can read as four numbers a quad (the ground's edges carry the layers round their cell in it). Flushes on texture change. `setBlending(false)` for opaque geometry. |
| `ShaderProgram.scala` | GLSL shader compilation + embedded shader source: ColorShader (pos+color), TextureShader (pos+texcoord+color), BloomExtract, GaussianBlur, Composite (bloom+vignette+overlay) |
| `PostProcessor.scala` | Post-processing FBO pipeline: Scene FBO → Bloom extract (half-res) → Blur H → Blur V → quarter-res pair → Composite. `GRIDGAME_HOLECHECK=1` clears the scene to magenta, so any pixel the terrain leaves uncovered shows. |
| `GLTexture.scala` | PNG loading via STB image → GL texture (`loadInspected` lets the caller measure or amend the pixels before upload; `decode` hands them to a texture built out of them). FBO creation for render-to-texture. `padTransparent` (colour into transparent texels, cell by cell; `padTransparentRings` for many rings at once) and mipmapped atlases: `allocate`, `upload` and `halve` build one a piece at a time with halvings of its own, in small scratch buffers (`allocPixels`/`freePixels`), which the tile atlas needs (render/CLAUDE.md, *The terrain is filtered as the characters are*). `fromArgb` makes one of an AWT picture's pixels (the pointer's). |
| `GLFontRenderer.scala` | AWT-based font rasterization → GL texture atlas. Rasterized at the display's pixel density (`pixelScale`) so text is sharp on HiDPI screens; metrics and sizes in units; every draw takes a `scale`. Outlined text with drop shadows, and a heavy eight-copy outline for text over the world. Three sizes (14/22/44). `digitsCentredOn` places a number in a box by its digits' ink (see *Rules*). |
| `GLWindow.scala` | GLFW window create/show/resize, and its end: `retire` (hidden for good) then `GLWindow.destroySettled` once macOS has finished with it, or `destroy` at once for a window macOS can't still be using (see *A window macOS may still be using is retired*); a blank system cursor over it for a game that draws its own pointer (`hideSystemCursor`, not `GLFW_CURSOR_HIDDEN`: see render/CLAUDE.md, *The pointer is ours*), and whether the mouse is over it with the focus, and where (`readPointer`, `onPointerMoved`); on Linux, the app it belongs to (`windowClass`) and the screen's pixels a unit (`unitScale`) (see *On Linux, GLFW runs on X11*) |
| `GLFWManager.scala` | Singleton `ensureInitialized()` shared by ControllerHandler and GLWindow, which on Linux puts GLFW on X11; `terminate` leaves GLFW to the process's exit while a retired window is still waiting |
| `LightSystem.scala` | The dynamic light map (quarter-res soft blobs, multiplied in by the composite) and `LightPool`, which keeps a busy frame's strongest lights |
| `GpuProfiler.scala` | Dev tool: fragments shaded and GPU time per frame phase, with `GRIDGAME_GPU_PROFILE=1` |
| `Matrix4.scala` | Orthographic projection matrix |
| `TextureRegion.scala` | Case class for (texture, u, v, u2, v2) sub-regions |

## A window macOS may still be using is retired

`glfwDestroyWindow` frees GLFW's record of a window while AppKit can still hold the NSWindow and
GLFW's view in it: measured, both lived on 50-200ms after it returned, windowed, in F11's full
screen and in macOS's own. GLFW's view reads that record whenever AppKit asks it anything
(`-[GLFWContentView isOpaque]` is `[window->ns.object isOpaque]`). In macOS's own full screen (the
green button, ⌃⌘F) AppKit is still taking the window out of it when `glfwDestroyWindow` returns,
and redraws the view on the way: a Core Animation commit asked the view whether it was opaque, and
the client crashed in `objc_msgSend` at the end of a match (2026-10-09). So a match's window is
never destroyed at once (`MatchWindow`):

- `GLWindow.retire` takes it off the screen, so the menus come straight back. It puts the
  system's arrow back first; in GLFW's own full screen (F11) it turns auto-iconify off, or losing
  the focus as it hides would minimise it into the Dock, and leaves that full screen once hidden, so
  a display mode GLFW changed is put back while the record still exists. On macOS it then closes
  the NSWindow, as `glfwDestroyWindow` would, with `releasedWhenClosed` off so it stays alive for
  GLFW, which still needs it and only ever releases it by closing it; `destroy` releases it after
  `glfwDestroyWindow`. Hidden alone, a window stays in macOS's own full screen, its Space with it,
  for good; taken out with `toggleFullScreen:`, AppKit puts it back on the screen, over the menus,
  as the animation ends. Closed, the Space goes and nothing comes back.
- `GLWindow.destroySettled`, every quarter second from `MatchWindow`, destroys it once it is out of
  macOS's own full screen (asked of AppKit, `styleMask`: GLFW has no word for that full screen) and
  has been off the screen two seconds; AppKit dropped its own references to it 0.2-0.7s after the
  close. It also puts back away any retired window AppKit has shown again, which it does to one
  closed while it was still going into its full screen: the Space goes, the window stays on screen.
- `GLFWManager.terminate` leaves GLFW to the process's exit while one is still waiting, since
  `glfwTerminate` destroys every window there is.

It was rare because macOS 26 zeroes a freed block: the view mostly read nil and nothing happened,
and it crashed when the block had been handed out again first, here to the scoreboard being built
in the same moment. Under `MallocZeroOnFree=0 MallocScribble=1` freed memory reads `0x55…` instead,
and every stale read crashes. A throwaway harness that opened a `GLWindow` from JavaFX, put it in a
full screen and closed it as `MatchWindow` does, run that way: destroyed at once, a window in
macOS's own full screen crashed at the first close in 6 runs of 6; retired, 22 closes in 22
survived: 17 in macOS's own full screen (4 with a new match opened 0.3s after the last, 5 closed
while it was still going into it), 3 in F11's and 2 in a window. No Space was left behind, and no
window came back but the one AppKit shows again mid-transition, put away within 0.3s.

## On Linux, GLFW runs on X11

LWJGL's GLFW (3.5 before release, in LWJGL 3.3.4) has both of Linux's display systems in it, and
given the choice takes Wayland wherever there is one: GNOME and KDE now, a Chromebook's Linux.
JavaFX's windows are always X11's, on a Wayland desktop through XWayland, so `GLFWManager` has GLFW
use X11 too wherever there is an X display (`DISPLAY`), and only otherwise choose for itself. On
Wayland GLFW can neither give a window its icon nor place it, and `GLWindow` does both (`setIcon`,
the centring in `create`): each printed a GLFW error with a stack trace, in a probe doing as
`GLWindow` does under weston with XWayland (2026-10-16). On X11 there both did their job, and the
context (OpenGL 4.5 core, Mesa) was the same.

Two things X11 leaves to the app, which macOS's GLFW does itself:

- **Whose window it is.** A desktop groups windows, and finds their launcher, by their X11 class
  (`WM_CLASS`; a Wayland app id). GLFW names a window after its title unless told, so `GLWindow`
  takes a `windowClass`, and the client gives the name JavaFX gives the menus' windows
  (client/CLAUDE.md, *On Linux*).
- **How big a unit is.** On X11, GLFW sizes a window and places the pointer in the screen's pixels,
  where a desktop scaled to 200% has every app draw twice the size. Given the size of the menus
  in their units, the match's window filled a quarter of such a screen, and everything in it was
  drawn half size. `unitScale` (the menus' scale, JavaFX's `Screen.getOutputScaleX`, on Linux) makes
  the window that many pixels a unit and gives its size and the pointer in units, as macOS gives
  points: the framebuffer is in pixels, so the renderer sees a HiDPI screen as on a Mac (fonts at
  `fbWidth / width`, the quality tiers' `sceneScale`). With `GDK_SCALE=2` on a 3200x1800 screen
  (Xvfb), the window then filled the screen, laid out as at 1600x900, its text sharp, its pointer
  and aim where the mouse was. Windows sizes a window in pixels too, and likely has the same
  trouble; `unitScale` is left at 1 there, unchecked.

## The window has no depth or stencil buffer

Nothing is drawn with a depth or a stencil test: the world is drawn in painter's order into the
post-processor's targets, which have neither, and the window only takes the composite, the
overlay over the world and the HUD. So `GLWindow.create` asks GLFW for neither buffer
(`GLFW_DEPTH_BITS` and `GLFW_STENCIL_BITS` 0). GLFW's defaults ask for both, and macOS gave the
window 32 bits of depth and 8 of stencil a pixel, held for the whole match: 18MB of graphics
memory at 2560x1440, 56MB at 4480x2520. Measured with `footprint` on a match's frames at
2560x1440, the driver's own graphics memory (`Owned physical footprint (unmapped) (graphics)`,
which the render bench prints as `driver graphics`) went from 65 to 46MB; every golden frame is
byte for byte what it was.

## Batch Management
`RenderContext` has `beginShapes()` / `beginSprites()` / `endAll()`, which every painter draws through, to minimize state transitions. Only one batch (shape or sprite) is active at a time; calling `beginShapes()` while the sprite batch is active will end the sprite batch first, and vice versa.

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
reserve its vertices and indices with `ensureCapacity` before writing them; `vertex` and `tri`
only have a backstop.

**A shape's vertices are shared.** Every `ShapeBatch` primitive writes each of its vertices once
and then its triangles as 16-bit indices into them, counted from the flush's first vertex
(`glDrawElementsBaseVertex`), into a second ring beside the vertices' (mapped the same way): a fan
of n triangles is n + 2 vertices where it was 3n, a band of n quads (a ring, a stroke, an arc) 2n + 2
where it was 6n, a rect 4 where it was 6. A sprite is four vertices, its two triangles from one
element buffer made once for every flush. The triangles are the ones drawn before, in the same
order, from the same floats: a shared vertex is the same expression on the same inputs in every
triangle that uses it (the closing point of an oval is still its own vertex, since the table's
sin(2π) is -2.4e-16, not 0). So the frame is the same to the bit, which is what keeps this true:
every golden frame at every tier, and the projectile gallery's sheets of every projectile and every
blast, compared byte for byte with the build before. A new primitive keeps it so by writing its
vertices in the order its triangles first use them and its triangles in the order it would have
drawn them. `polyStroke` and `smoothMitres` work out each edge's normal once (`edgeNormals`) for
the points at both its ends, which each used to work it out for themselves, to the same floats;
the rounded rectangles' corners come from tables made once with the arithmetic each point used.
Measured back to back on the Hive (High, 2560x1440, 16 players, 150 projectiles that travel,
four alternating pairs): 259,000 shape vertices a frame became 112,000 and 11,000 sprite vertices
7,300, and a frame took a fifth less CPU to build (mean 2.01 → 1.59 ms, median 1.81 → 1.38),
allocating nothing more. Of the 130 or so draws a frame, about 75 are a switch between the shape
and the sprite batch and 40 an additive toggle (a blast's sparks, mostly); each is a draw call,
which on this machine is where the driver spends most of its time.

## Rules the primitives follow

- **Fonts are rasterized at the display's pixel density** (`GLFontRenderer`'s `pixelScale`,
  made again if the window moves to another screen): rasterized at 1x and magnified, all text
  on a HiDPI screen — the HUD included — was soft.
- **A number in a box is placed by its ink, not by its line** (`GLFontRenderer.digitsCentredOn`).
  Every font in the chain draws on the tallest one's baseline, Noto's, so a line is much taller
  than Exo 2's digits and they sit low in it: at 44, 7 units lower than Exo 2 alone would put
  them, in a line 66 tall. The K/D counts, drawn a fixed 18 below the top of their 56-unit
  panel, hung half out of its bottom.
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
- **Smooth edges** — every shape above has a hard edge, which a pixel is either inside or not, so
  a curve or a slanted line is a staircase, and at Low, under a pixel a unit, a coarse one. The
  smooth shapes (`fillOvalSmooth`, `strokeOvalSmooth`, `strokePathSmooth`, `fillRibbonSmooth`) fade
  each edge out over one pixel of the target (`pixelsPerUnit`), centred on the edge: a fill is inset
  by half a pixel and ringed by a band from its alpha to none, a stroke is a core with such a band
  either side, and a line thinner than a pixel is drawn a pixel wide and as much fainter. A smooth
  edge shows a polygon's corners a staircase hid (a dot drawn with eight segments read as an
  octagon), so their ovals take as many segments as keep every chord within a quarter of a pixel of
  the curve, up to 256 (turned step by step past the sine table's 64). Their paths are mitred as
  *Strokes are one band*'s are, and pointed out of a closed path whichever way round it runs, so
  `strokePathSmooth`'s `offset` can lay an ink line just outside a rim. They cost about three times
  a hard stroke's vertices, so they are for what the eye rests on: our aim (render/CLAUDE.md, *An aim
  is drawn in one hand*).
- **A filled outline is only as good as its triangulation** — `ShapeBatch.fillPolygon` fans
  from vertex 0, which is right for a convex part and wrong, silently, for everything else:
  the stroked outline beside it keeps tracing the true shape, so the result reads as a
  rendering glitch rather than as geometry the renderer cannot express. That is why a
  throwing star built as a polar radius per vertex came out as a crumpled dart, and why
  both crescent blades filled their own hollow and read as grey slabs. `fillFan` (fan from
  the centre the radii were measured from) and `fillRibbon` (quad per segment across a
  band) cover the two shapes that kept being asked for, and `GRIDGAME_POLYCHECK=1` makes
  the failure loud instead of silent.

## Post-Processing
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

The composite writes every pixel of the display whole (alpha 1), so it is drawn with blending
off: on, the blend read every pixel of the display back only to multiply it by zero, the same
pixels coming out. It is the largest target a frame touches, and at Low on a HiDPI screen,
where the scene is a third of the display's pixels, the composite is two fifths of the frame's
fragments (render bench, the Hive: 3.7 of 9.5 million).

**The bloom carries a grey haze.** The bloom passes are drawn with blending on (every batch
leaves it on) over targets cleared with whatever `glClearColor` was set last, which is the light
map's ambient (`LightSystem.renderLightMap`). Where nothing is bright the extract writes nothing
over that grey, so every bloom target is the ambient level all over with the bright parts on top,
and the composite's screen blend (`bloom * strength * (1 - scene)`) lifts the whole frame about a
tenth of the way to white: 0.625 × 0.16 on the bright maps, 0.55 × 0.2 on space, about 0.5 × 0.22
on the dark ones. Read back after a frame (2026-10-15), the extract and both blurs were nowhere
darker than 0.549 on the Hive and 0.624 on the Meadow, and on average within a hundredth of that.
It is part of how every map looks now. Clearing the bloom targets to black, or drawing their
passes without blending, would take it away and make the bloom only what is bright: a change to
the look, to be judged by eye on every map, and not something to fix in passing. Until then the
clears and the blending of the bloom passes stay as they are.

## Graphics quality tiers (`RenderQuality`)

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
auto — which only steps down mid-match — can't do it. Not on Linux: nothing scales an X11 window
up, and on a desktop at 200% the menus came out half size.
