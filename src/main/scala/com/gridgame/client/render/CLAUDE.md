# client/render

The game's renderer: `GLGameRenderer.render` draws one frame, pass by pass, into the post-processor's
scene target and then over the finished frame. Three Bazel libraries, each on the ones before it:

- **core** — this directory but for GLGameRenderer: `RenderContext` (what every painter shares:
  the batches and which is open, the fonts, the light map and post-processor, the particle
  systems, the camera, the frame's clock, size, camera offsets and the cells on screen), and the
  utilities below.
- **world/** and **hud/** — the painters, a class each, each owning its scratch buffers and drawing
  one part of the frame. A painter imports the context (`import ctx._`) and names any other painter
  it uses in its constructor, so what it depends on is in its first line.
- **GLGameRenderer** — makes the painters, sets up each frame, and runs the passes in order. Its
  public API is small: `render`, `camera`, `resetVisualPosition`, `dispose`, `seedRandom`.

| Painter | Draws |
|---|---|
| `world/BackgroundPainter` | the sky, sea, city, desert, space or snow behind the map, cached in a target of its own |
| `world/TerrainPainter` | the ground (every flat tile as an opaque diamond), animated tile overlays, water reflections, block shadows, and each block or prop in the depth pass |
| `world/PlayerPainter` | players (everyone else where RemoteMotion has them, and us): shadow, sprite, the effects on them; notes their barriers, glows and name plates for later passes; their lights |
| `world/StatusEffectPainter` | frost, stun stars, poison, roots, a slow, a speed boost, a shield bubble |
| `world/GlowEffectPainter` | the glowing effects (a gem, a charge, a phase, a burn, a hit's flash, a cast's flash), deferred to one additive pass |
| `world/ItemPainter` | items on the ground, their glows, pickup sparkles; the item shapes the HUD's inventory uses |
| `world/ProjectilePainter` | projectiles through GLProjectileRenderers: their lights, wall-passers lifted over the terrain, stopped ones fading |
| `world/BarrierPainter` | raised barriers (design/barriers.md) |
| `world/OpeningPainter` | the Teams wall, and the opening's countdown for the HUD (design/match-flow.md) |
| `world/TrapPainter` | traps on the ground and going off (design/traps.md) |
| `world/AimArrowPainter` | the arrow toward the cursor |
| `world/DeathAndTeleportPainter` | deaths and teleports |
| `world/BlastPainter` | the frame's blasts, in their two layers, their lights and the air they shake (render/blasts) |
| `world/WorldOverlayPainter` | name plates, health bars and damage numbers, over the finished frame |
| `world/ParticleSpawner` | what the frame throws into the particle systems: weather, footsteps, trails, sparks, bursts |
| `hud/HudPainter` | the HUD: the bottom bar (inventory, charge bar), the leave prompt, and the parts below |
| `hud/AbilityBarPainter`, `MatchInfoPainter`, `ChatPainter`, `PracticePainter`, `DeathScreenPainter` | the ability slots, the clock and kills and kill feed, chat, practice's tallies, the respawn countdown and game over screen |

The painters are all one render thread's: nothing in them is synchronized, and none allocates per
frame (see *Performance*).

## The utilities in core

| File | Purpose |
|------|---------|
| `GameCamera.scala` | Holds visualX/Y, smooth lerp, screen shake, zoom. Provides camera offsets, on the render target's pixel grid when it is told the grid. |
| `IsometricTransform.scala` | `worldToScreen(wx,wy,cam)`, `screenToWorld(sx,sy,cam,zoom)`, `viewInsideWorld` (is the whole screen over the map?) |
| `EntityCollector.scala` | Collects items/projectiles/players by grid cell for depth-sorted rendering. Cells are a flat grid over the visible window, each a linked list of pooled entries (`takeCell` / `takeRemaining`) — no map, no boxed keys, no allocation per frame |
| `RemoteMotion.scala` | Where another player is drawn: walked along the cells the server has put them on, at their measured pace, never past the newest. See *Other players are walked along the cells they were heard on* |
| `GLTileRenderer.scala` | Loads `sprites/tiles.png` as a GL texture (transparent texels padded with their neighbours' colour); returns full or transparent-margin-trimmed TextureRegion per tile ID + frame, and `drawDiamond` draws a flat tile as exactly its diamond |
| `GLSpriteGenerator.scala` | Packs character sprite sheets into one growable, mipmapped GL atlas |
| `ParticleSystem.scala`, `DamageNumberSystem.scala` | pooled particles and damage numbers |
| `FrameClock.scala` | where the renderer reads the time: the system's, or GoldenFrames' stopped one |

## Rendering Pipeline
```
PostProcessor.beginScene()        -- bind scene FBO (sized by the quality tier)
GLGameRenderer.render()           -- all game drawing into scene FBO
  Background (only if the screen runs off the map) → Ground (opaque diamonds) →
  tile overlays, reflections → Traps → Blasts' ground layer → Aim arrow → block shadows →
  depth pass (blocks and props, items, projectiles, players with their effects) →
  flying projectiles → Barriers → the opening's wall → deferred glows →
  deaths, teleports → Blasts' air layer → particles
PostProcessor.endScene()          -- bloom extract → blur H → blur V →
                                     composite (scene + bloom + vignette + overlay)
                                     upscales to the real framebuffer
World overlay                     -- name plates, health bars, damage numbers: world
                                     positions, drawn after the composite at full resolution
HUD                               -- drawn after the composite, always at full resolution
```

## Design decisions

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
- **In daylight a shot carries no light** (`RenderContext.projectileLights`, off for `sky`, `sea` and
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
- **Other players are walked along the cells they were heard on** (`RemoteMotion`, one per player
  in `EntityCollector`). Every cell the server puts them on is a waypoint; the drawn player walks
  from one to the next at the pace the last quarter second of them came in at (measured, so a
  bot's slower walk, a slow and a boost come out right), keeping about 0.8 of a cell behind the
  newest so a step that arrives a little late finds it still walking. Further behind than walking
  leaves it (a dash), it catches up; a move of more than 4.5 cells between two updates (a blink, a
  respawn) is drawn at once. It is never drawn past the newest cell, and it is sorted into the
  depth order of the cell it is drawn in. It used to guess a velocity from the time between the
  last two changes it saw and run them on at it for up to 75ms after the news stopped, through
  whatever was in the way: two updates a frame apart read as 55 cells a second, and a dash (a cell
  a frame, 60 cells a second) ran the drawn player up to four cells past where it ended, into the
  tree that ended it, before pulling them back. Measured in a live match, a dashing player was
  drawn ahead of where the server had them in 18% of frames, up to 3 cells; now in none.
  `RemoteMotionTest` pins it; six of its seven cases fail on the old smoothing.

## Grading

**A bright map is graded differently from a dark one.** The composite's ACES curve lifts
mid-tones and pulls highlights down. That is what keeps the space maps legible, and it turned a
meadow pastel: authored grass (0.50, 0.81, 0.31) came out (0.62, 0.76, 0.45). So `GLGameRenderer.setGrade`
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

## Performance

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

## Readability audit (dev tool)

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

## Golden frames (dev tool)

```bash
bazel run //src/main/scala/com/gridgame/client:golden_frames -- /tmp/golden                 # every scene, High
bazel run //src/main/scala/com/gridgame/client:golden_frames -- /tmp/golden --quality=low   # (or medium)
bazel run //src/main/scala/com/gridgame/client:golden_frames -- /tmp/golden --scenes=meadow,snow
```

Fixed scenes — players, projectiles, blasts, barriers, traps, the divider, the HUD, the edges of the
maps — drawn by the real GLGameRenderer on a stopped clock (`FrameClock`) with its randomness seeded
(`seedRandom`), so the same build draws the same pixels every time. It is the check for a change to
the renderer that is meant to change nothing: write the frames from both builds and compare them
byte for byte (`cmp`). A change that is meant to show must be judged by eye, in the galleries and in
a match.
