# client/gl

The OpenGL and GLFW primitives the renderer draws with. Nothing here knows about the game: the
batches, shaders, textures, fonts, the window, the post-processor, the light map, the quality tiers,
the GPU profiler.

## The files

| File | Purpose |
|------|---------|
| `ShapeBatch.scala` | Batched colored 2D primitives: fillRect, fillOval, fillOvalSoft, fillPolygon (**convex only**), fillFan (star-shaped outline, fanned from an explicit centre — stars, sunbursts, faceted hulls), fillRibbon (a band given as an outer run plus the inner run reversed — crescent blades), fillArcBand (ring segment with an alpha ramp — gauges, crescents, shockwaves), fillStarFlare (4-point glint), fillRoundedRect(Gradient), strokeLine, strokeLineSoft, and the joined strokes — strokeArc, strokeOval, strokePolygon, strokeRect, strokeRoundedRect, strokePolyline, strokePolylineTapered, strokePolylineVar (a width and an alpha of its own at every point — a limb, a comet tail, a bolt's channel) (see *Strokes are one band*). Oval segment counts follow the oval's size on screen (`pixelsPerUnit`, set per pass). Supports additive blend mode toggle, plus an alpha multiplier and a scale-about-pivot applied to every vertex (`setAlphaMultiplier` / `setScaleAbout`; reset by `begin`). |
| `SpriteBatch.scala` | Batched textured quads with per-vertex tint/alpha, and `drawQuad` for any four corners (the ground's diamonds). Flushes on texture change. `setBlending(false)` for opaque geometry. |
| `ShaderProgram.scala` | GLSL shader compilation + embedded shader source: ColorShader (pos+color), TextureShader (pos+texcoord+color), BloomExtract, GaussianBlur, Composite (bloom+vignette+overlay) |
| `PostProcessor.scala` | Post-processing FBO pipeline: Scene FBO → Bloom extract (half-res) → Blur H → Blur V → quarter-res pair → Composite. `GRIDGAME_HOLECHECK=1` clears the scene to magenta, so any pixel the terrain leaves uncovered shows. |
| `GLTexture.scala` | PNG loading via STB image → GL texture (`loadInspected` lets the caller measure or amend the pixels before upload). FBO creation for render-to-texture. `padTransparent` (colour into transparent texels, cell by cell) and mipmapped atlases. |
| `GLFontRenderer.scala` | AWT-based font rasterization → GL texture atlas. Rasterized at the display's pixel density (`pixelScale`) so text is sharp on HiDPI screens; metrics and sizes in units; every draw takes a `scale`. Outlined text with drop shadows, and a heavy eight-copy outline for text over the world. Three sizes (14/22/44). |
| `GLWindow.scala` | GLFW window create/show/destroy/resize |
| `GLFWManager.scala` | Singleton `ensureInitialized()` shared by ControllerHandler and GLWindow |
| `LightSystem.scala` | The dynamic light map (quarter-res soft blobs, multiplied in by the composite) and `LightPool`, which keeps a busy frame's strongest lights |
| `GpuProfiler.scala` | Dev tool: fragments shaded and GPU time per frame phase, with `GRIDGAME_GPU_PROFILE=1` |
| `Matrix4.scala` | Orthographic projection matrix |
| `TextureRegion.scala` | Case class for (texture, u, v, u2, v2) sub-regions |

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
reserve its vertices with `ensureCapacity` before writing them; `vertex` only has a backstop.

## Rules the primitives follow

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
auto — which only steps down mid-match — can't do it.
