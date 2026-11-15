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
  public API is small: `render`, `drawCursor`, `camera`, `onCamera` (where the match's window reads
  the pointer into the world, once each frame's camera has moved), `resetVisualPosition`, `dispose`,
  `seedRandom`.

| Painter | Draws |
|---|---|
| `world/BackgroundPainter` | the sky, sea, city, desert, space, snow, volcano or night behind the map, cached in a target of its own |
| `world/TerrainPainter` | the ground (every flat tile as an opaque diamond), animated tile overlays, water reflections, block shadows, each block or prop in the depth pass, and the light the terrain itself sheds (*The terrain's own light*) |
| `world/GroundEdgePainter` | one soft, inked edge wherever two kinds of natural ground meet, over the ground's diamonds (*Where two kinds of ground meet*) |
| `world/PlayerPainter` | players (everyone else where RemoteMotion has them, and us): sprite and the effects on them in the depth pass, their shadows and our ring on the ground before it (`drawGroundMarks`); notes their barriers, glows and name plates for later passes; their lights |
| `world/StatusEffectPainter` | frost, stun stars, poison, roots, a slow, a speed boost, a shield bubble |
| `world/GlowEffectPainter` | the glowing effects (a gem, a charge, a phase, a burn, a hit's flash, a cast's flash), deferred to one additive pass |
| `world/ItemPainter`, `world/ItemTrack` | items on the ground, each a sprite of `ItemSprites`' floating over its shadow in the depth pass, its shadow and the light it sheds drawn with the ground (`drawGroundMarks`), popping into being as it appears and going with a puff when taken (`ItemTrack` says which is which); the items' pictures in the HUD's inventory (`drawIcon`) (*An item is drawn as the characters are*) |
| `world/ProjectilePainter` | projectiles through GLProjectileRenderers: their lights (in their look's own colour where it has one, `glowOf`, else the thrower's), wall-passers lifted over the terrain, stopped ones fading, pulls reeled back in to their throwers (`drawReeled`: render/projectiles, *Pulls are tied to whoever is pulling*) |
| `world/BarrierPainter` | raised barriers (design/barriers.md) |
| `world/OpeningPainter` | the Teams wall (design/match-flow.md); the opening's countdown is the HUD's (`hud/MatchInfoPainter`) |
| `world/TrapPainter` | traps on the ground and going off (design/traps.md): inked on a seat of darker ground, ours sending a ring over the ground they watch, a glint now and then on every one (*A trap reads on every ground*) |
| `world/AimArrowPainter`, `world/AimGeometry` | our attack's aim while we aim it, in one hand (*An aim is drawn in one hand*): a charged shot's lane on the ground to its whole reach, walls or not (*A charged shot's aim is a lane on the ground*), and any other attack being aimed, a shot, a fan, a dash, a blink or a trap, where it will go (*An aimed attack shows where it will go*); where it lies, which moves with us as our sprite does (`AimGeometry`: *An aim moves with us*) |
| `world/DeathAndTeleportPainter` | deaths and teleports |
| `world/BlastPainter` | the frame's blasts, in their two layers, their lights and the air they shake (render/blasts) |
| `world/BloodPainter`, `world/Blood` | the blood a hit draws: its drops in the air over the frame, their splats on the ground (*A hit draws blood*) |
| `world/WorldOverlayPainter` | name plates, health bars and damage numbers, over the finished frame |
| `world/ParticleSpawner` | what the frame throws into the particle systems: weather, footsteps, trails (a projectile's embers in its look's colour, as its light), sparks, bursts |
| `hud/HudPainter`, `hud/HudStyle` | the HUD, in the menus' dark glass (*The HUD is the menus' dark glass*): the tray along the bottom (`HudTray`: the ability slots, the items and how many we carry, the charge's gauge over it), the notice that we are too full to pick up the item within reach (design/combat.md, *Item Types*), the notice that F8 marked a moment for a problem report (`Diagnostics`' for three seconds; design/diagnostics.md; the golden scene `lagoon_moment_marked`), and the parts below; `HudStyle` is the look they all draw with (the palette, panes of glass, wells, keycaps, small capitals, a character's bust) |
| `hud/AbilityBarPainter`, `MatchInfoPainter`, `KillBannerPainter`, `ChatPainter`, `PracticePainter`, `DeathScreenPainter` | the ability slots (the one being aimed ringed: *An aimed attack shows where it will go*) and R's beside them, its seconds left over its sweep (design/characters.md, *R resets the cooldowns*), each cooldown swept inside its own slot with its seconds left; the clock, our score and our run of kills in a row beside it, the opening's countdown and FIGHT! as it ends, the kill feed; a kill of ours announced (*A kill is announced*); chat; practice's pill and key; the respawn countdown and game over screen |
| `hud/ScoreboardPainter` | the scoreboard Tab (a controller's Back) holds up, over most of the screen, over the HUD: everyone placed as the match's end will place them (`LiveStandings`), a section a team in Teams (*The scoreboard Tab holds up*) |
| `hud/MatchMenuPainter` | the menu Esc (a controller's Start) brings up, over everything but the pointer: the graphics settings and the way out of the match (`MatchMenu`; *The menu Esc brings up*) |
| `hud/CursorPainter` | the mouse's pointer, a crosshair over everything, drawn by `drawCursor` after the frame (*The pointer is ours*) |

The painters are all one render thread's: nothing in them is synchronized, and none allocates per
frame (see *Performance*).

## The utilities in core

| File | Purpose |
|------|---------|
| `GameCamera.scala` | Holds visualX/Y, smooth lerp, screen shake, and the zoom each frame is drawn at (`playersZoom`: the settings' camera zoom, *The camera's zoom*), which the mouse is read into the world with. Provides camera offsets, on the render target's pixel grid when it is told the grid. |
| `MatchMenu.scala` | The menu Esc brings up in a match: open or not, its layout in the window's units, what each control does, the mouse's press and drag, the keyboard's and a controller's focus, leaving on a second press (*The menu Esc brings up*). No GL: `hud/MatchMenuPainter` draws it |
| `LiveStandings.scala` | Where everyone stands in the match being played, for the scoreboard Tab holds up: gathered from the client, placed as `MatchResults` places them, in arrays it keeps (*The scoreboard Tab holds up*) |
| `IsometricTransform.scala` | `worldToScreen(wx,wy,cam)`, `screenToWorld(sx,sy,cam,zoom)`, `viewInsideWorld` (is the whole screen over the map?) |
| `EntityCollector.scala` | Collects items/projectiles/players for the depth pass by the diagonal of the map they are drawn on (`diagonalOf`: `floor(x + y)`), each where it is drawn, us where the camera draws us. Diagonals are a flat array over the visible window, each a linked list of pooled entries back to front (`takeDiagonal` / `takeRemaining`) — no map, no boxed keys, no allocation per frame. See *Everything is sorted where it is drawn* |
| `RemoteMotion.scala` | Where another player is drawn: walked along the cells the server has put them on, at their measured pace, never past the newest, and slid by a knockback. See *Other players are walked along the cells they were heard on* |
| `GLTileRenderer.scala` | Loads `sprites/tiles.png` as a GL texture, filtered and mipmapped, each cell in a slot with a gutter of its own border texels (*The terrain is filtered as the characters are*); returns full or transparent-margin-trimmed TextureRegion per tile ID + frame, and `drawDiamond` draws a flat tile as exactly its diamond |
| `GLSpriteGenerator.scala` | Packs character sprite sheets into one growable, mipmapped GL atlas |
| `ItemSprites.scala` | Loads `sprites/items.png` (the five items, the gem's eight frames of turning, a twinkle) as a GL texture, filtered through two halvings of its own, its clear texels given the colour of the art beside them; a TextureRegion per item and frame |
| `ParticleSystem.scala`, `DamageNumberSystem.scala` | pooled particles and damage numbers |
| `FrameClock.scala` | where the renderer reads the time: the system's, a match window's frame (`onFrames`: *Everything moving is placed at the frame's time*), or GoldenFrames' stopped one |

## Rendering Pipeline
```
PostProcessor.beginScene()        -- bind scene FBO (sized by the quality tier)
GLGameRenderer.render()           -- all game drawing into scene FBO
  Background (only if the screen runs off the map) → Ground (opaque diamonds) →
  the edges where two kinds of ground meet → tile overlays, reflections → blood's splats → Traps → Blasts' ground layer → our aim → block shadows →
  the items' shadows and light, the players' shadows and our ring →
  depth pass (a diagonal of the map at a time, far to near: its blocks and props, then the
  items, projectiles and players with their effects standing on it, back to front) →
  flying projectiles → Barriers → the opening's wall → deferred glows → items being taken →
  deaths, teleports → Blasts' air layer → blood in the air → particles
PostProcessor.endScene()          -- bloom extract → blur H → blur V →
                                     composite (scene + bloom + vignette + overlay)
                                     upscales to the real framebuffer
World overlay                     -- name plates, health bars, damage numbers: world
                                     positions, drawn after the composite at full resolution,
                                     one size on the screen at any zoom
HUD                               -- drawn after the composite, always at full resolution
Scoreboard, menu                  -- while Tab is held / while Esc has the menu up, over the HUD
GLGameRenderer.drawCursor()       -- the pointer, last, where the mouse is as the frame is done
                                     (not over the menu: the system's arrow shows there)
```

## Design decisions

- **A tile's form decides how it is drawn** (`TileForm` in `Tile.scala`). Walkable *ground* and
  flat *pools* (water, lava, the space maps' open space) are drawn in the ground pass. *Blocks*
  (walls, cliffs, ice blocks) and *props* (trees, rocks, bushes, snowmen, the space maps' jelly, glass
  and plasma) are drawn in depth order with the players. A prop's
  sprite leaves the ground round it showing, and the ground pass lays under it whatever it is
  standing in (`Tile.groundUnder`: its own `ground` if a neighbour is that, else the first
  walkable neighbour, else its own). So one palm serves a beach and a meadow, and a tree deep in
  a forest still has grass under it. Only blocks cast the edge shadows; a prop carries its own
  round shadow. Water used to be a raised 14px block, which read as an aquarium, not a pond.
  Frames mean different things per form: ground and props have four variants picked by
  position (`Tile.variantAt`: the space maps' deck picks a quarter of a plate two cells across by
  where a cell is), pools and blocks four animation frames, unless the tile doesn't animate
  (`Tile.animates`: open space shows four skies by position, or its stars would stand in a lattice).
  The map editor draws the same passes, but for the edges between kinds of ground.
- **Where two kinds of ground meet** (`world/GroundEdgePainter`, `GroundEdges` in common/model,
  which says which ground spills over which and how its edge looks). The tiles meet in a staircase
  of diamonds, which a map makes of every curve and slope it draws: the Meadow's paths and pond,
  the Lagoon's beaches, the Snowglobe's frozen pond, the round edge of every space arena. Over that,
  every cell an edge passes through is drawn again, after the ground, by a shader. Grass, snow, sand,
  path, dirt, ice, water, deep water, the space maps' deck and the open space round it, and since
  the volcano, graveyard and desert maps lava, scorched rock, ash, the graveyard's grass and the
  desert's deep sand each have a layer (fifteen, all the shader's four bits hold), and at each pixel a bilinear blend of the cells' "this layer or higher", a value at each
  cell's centre, says what shows there: the edge runs where it is one half, so a straight run of
  cells keeps its edge on the cells' own edge, a corner is rounded, and a staircase turns into a
  curve. The higher ground is inked along its edge as the characters are, and stands on a riser
  where the edge faces the camera: a step of soil or snow, or, for the deck over open space, its
  hull, nearly three times as tall (`GroundEdges.RiserHeights`). Water carries foam where anything
  meets it, lava a band of its white heat fading into it under a step of dark crust, and space a
  glow that fades out from the deck's edge, the field that keeps the arena
  in; a pixel showing the cell's own ground is left alone, so its tile shows through. Ground built in
  pieces (boardwalks, flagstones, metal) and blocks take no part: a cell reads one as its own
  ground, so they keep their straight edges. A tile has its layer's colour all the way to its edge
  (`GroundEdgesTest` checks the atlas; the deck's plates, two cells across, are the one exception, a
  shade off its margin), which is what lets the higher ground spill over a neighbour without a
  seam.
  - Everything a pixel reads is in its cell and the eight round it, the point a riser's height above
    it included, so each cell's quad carries their nine layers in its colour and the shader reads no
    texture; a pixel with nothing but its cell's own ground round it is let go at once, and a quarter
    of a cell that is all such pixels isn't drawn. Read from a texture of the map's layers, nine
    fetches a pixel over every cell an edge touched (a third of the screen round the Meadow's pond)
    cost 2-3 ms of GPU a frame at 2560x1600. Now, on the render bench (the Meadow, 16 players, 150
    projectiles, back to back with the build before), a frame takes as long to build on the CPU and
    the GPU within the bench's noise and allocates nothing more; the GPU profiler puts 0.65 ms more
    in the ground phase.
  - `GroundEdges.shade` is the same thing on the CPU, line for line, for the menus' map pictures
    (ui/MapArt); `GroundEdgesTest` holds it to one edge, the same drawn from either cell it passes
    between, mirrored with the ground, never over the middle of a cell. A change to one is a change
    to the other. Nothing is drawn at a cell's centre, so a player stands on what their cell is; a
    riser and its foam come closest, hanging into a pool in a corner of higher ground.
  - The layers are read from the map once, and again round any cell whose tile has changed (a fence
    put down or gone), looked for among the cells on screen every frame.
  - With the restyled tiles (scripts/CLAUDE.md, *Tile Sprites*), the render audit of the three bright
    maps (2026-10-15) has characters, projectiles and traps reading as they did on every ground:
    each median, 10th percentile and minimum within a point or two, but the boardwalk's lowest
    projectile (22 to 20) and trap (91 to 84).
- **Everything is sorted where it is drawn, diagonal by diagonal** (`EntityCollector`, the depth
  pass in `GLGameRenderer.render`). The blocks and props are drawn a diagonal of the map at a time
  (`v = wx + wy`, how far down the screen), far to near, and after each diagonal's blocks
  everything standing on it, back to front: whatever is drawn at (x, y) goes after every block on
  diagonal `floor(x + y)` and before every block on the next. A block on a later diagonal is
  nearer than anything on an earlier one wherever the two overlap, so that is right for anyone
  anywhere between two cells, with a sprite narrower than a tile (the widest sheet reaches 19.5
  units either side of the feet at 48, a tile 20; only the chameleon's, 22, is wider).
  - It was walked a row at a time, each entity on the cell it was rounded to and us on the cell
    we stand on, which is the one we are stepping into or were pushed to. We are drawn where the
    camera is, a step's glide and the camera's ease behind that (~1.2 cells as we walk, up to 1.7,
    several in a knockback's slide), so a wall in front of where we were drawn but in an earlier
    row was drawn under us, and we stood on its top; one behind us in a later row was drawn over
    us. And for anyone between two rows with a wall on either side no cell in row order was right
    (stepping from (x, y) to (x, y+1), the wall on (x+1, y) needs drawing after them and the one on
    (x-1, y+1) before them, and rows put (x+1, y) first). Modelled over walks in every direction
    beside the walls and props of all seven maps (a wall a 36-unit cube, the sprite a 27x41
    billboard), we were drawn over a wall by 25 pixels or more in 2.4% of the frames a wall
    overlapped us, up to 513 of the sprite's ~1100, and under one behind us in 3.2%, up to 1017;
    anyone else, rounded, in 0.5% and 0.7%; now in none. Shots are sorted the same way, so a big
    one behind a tree is no longer drawn over it.
  - The players' shadows (56 units across) and our ring (68) are wider than a tile. Drawn with the
    sprite, in its place among the walls, they lay over the foot of a wall beside or behind the
    player, and the diagonal order alone would have laid them on a wall level with us on our left,
    which rows happened to draw after us. They are drawn with the ground instead
    (`PlayerPainter.drawGroundMarks`, after the blocks' shadows), so a wall covers its part of them
    as it covers a trap.
  - What no order could draw right is gone from the game instead. A diagonal step (one of W, A, S
    or D alone takes one) may no longer cut a wall's corner (design/characters.md, *A diagonal step
    goes between open cells*): the sprite stood in the wall on both sides of the corner for half the
    step. And no sprite is wider than its tile: the Chameleon's tongue reached 22 units out, and
    `CharacterRosterTest` holds every sheet within 20. Still drawn over the whole depth pass on
    purpose: barriers, glows, deaths and teleports, name plates. A sprite swelling on a hit (18%,
    fading over half a second) reaches a few units past its tile while it lasts.
  - `DepthOrderTest` pins it, and fails with us sorted on our cell or anyone rounded to one; the
    golden frames `colony_knocked_by_pillar` (drawn beside the Colony's pillar, standing past it)
    and `cell_beside_wall` hold it. Measured back to back on the render bench (the Colony and the
    Hive, High, 16 players, 150 shots), it costs nothing: a frame as quick to build within the
    bench's noise (3.4-4.4 ms either way), and 2.6KB allocated a frame either way over 3600 frames
    on the Colony (900-frame runs differ by a tenth or two of a KB from run to run, a one-off
    spread over the frames, not a cost of each).
- **A player stands on the ground** (`PlayerPainter`, `Constants.SPRITE_FOOT_LINE`). A sprite is
  drawn with its soles on the point of the ground its player is at: where its shadow and our ring
  are drawn, where a trap under it lies and where a shot at it arrives. A sheet's frame has room
  under the soles (they are on 60 of its 64 units: scripts/SPRITES.md, *Rendering pipeline*), so
  it is the frame's foot line that goes on the ground, not its bottom edge, and a sprite swells
  about that line as it breathes or is hit, the feet staying where they are. What is drawn on a
  player (a shield's bubble, a stun's stars, the glows, a death) is centred on the middle of the
  figure, 16.5 units above the soles, and the health bar and name hang from 40.5 above them: six
  clear of a bare crown and over the tallest hat a frame has room for (`PLAYER_MIDDLE_ABOVE_FEET`,
  `PLAYER_PLATE_ABOVE_FEET`). The menus stand their characters the same way (`WorldBackdrop.stand`,
  the character panel's hero).
  - Until 2026-11-10 a frame was stood on its bottom edge with the soles ten of its 64 units above
    that, so every character hung 7.5 units over the ground and 8.5 over the middle of its own
    shadow (27 pixels on a HiDPI screen, measured in the render audit's pictures): clear of the
    shadow's dark middle altogether, feet in the back quarter of the cell, standing still the top
    of a hop. In front of a wall the feet were 2.5 units from its foot and the ring lay out in
    front of them; behind one the whole figure showed over it, shins and all, where now the wall
    covers the legs it stands in front of. A knock-up's lift read as less than it was, since
    everyone was already off the ground.
  - Everything hung off the frame moved down with it and keeps its place on the figure: the plate
    is as far over a head as it was, the effects where they were on the body. The measurements
    are from the soles now, so they hold whatever a frame's margins are; `SPRITE_FOOT_LINE` is the
    one number that has to follow the sheets (`sprite_base.HEADROOM`), and `CharacterRosterTest`
    checks the roster's soles are on it.
- **Tiles are culled against the visible diamond, not its bounding box** — the screen rect
  maps to a diamond in world space, whose AABB holds ~2.7x as many cells as are on screen.
  `render` computes bounds in the projection's own axes (`u = wx - wy`, `v = wx + wy`) and
  clips each row (the ground: `rowLo`/`rowHi`, verified against brute force over 400 camera
  positions with zero tiles missed and 6% overdraw) or diagonal (the depth pass: `diagLo`/`diagHi`,
  rounded in, so exactly the cells on screen; `DepthOrderTest` checks it against brute force over
  400 camera positions and screen shapes). The depth pass walks a few diagonals further for the
  entities (`entPad`), because a player sprite and its name plate hang above their own cell.
- **The ground is drawn as opaque diamonds** (`GLTileRenderer.drawDiamond`) — every ground and
  pool tile is exactly its diamond, so the ground pass draws each tile as a quad through the four
  corners, with blending off, and covers every pixel once. Drawn as the cell's bounding rect it
  shaded and blended the whole screen twice, since each tile's transparent corners lie under its
  neighbours (7.4 → 3.7 million fragments a frame at 2560x1440). Neighbours meet exactly because
  their shared corners are computed from the integer lattice (`u = wx - wy`, `v = wx + wy`), so
  they are the same floats. The tile's edge in the PNG is a staircase either side of the true edge,
  so the pixels just inside it sample texels just outside, which are transparent — and black in the
  PNG: their neighbours' colour is given them at load (*The terrain is filtered as the characters
  are*). `TileTest`
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
  target's pixels per unit). The terrain used to be sampled nearest-neighbour at a scale that isn't
  a whole number (80 texels onto 64 or 128 pixels), so at a sub-pixel offset which texels got
  doubled or dropped depended on the offset, and with the camera gliding between pixels every
  tile's detail crawled whenever anyone moved. It is filtered now, but an offset on the grid keeps
  the ground's diamonds' hard edges and the edge pass's lines steady. Entities still move smoothly:
  only the offset is rounded, never their positions. The mouse reads the same offsets. `CameraTest`
  pins it.
- **The terrain is filtered as the characters are** (`GLTileRenderer`): linearly, through two
  halvings. Picked a texel per pixel, every line and outline in the tile art was a staircase of
  doubled and dropped texels beside the characters' smooth ones, which is most of why the terrain
  read as cruder art than the characters standing on it. Filtered, a pixel reads a little round its
  texel, so at load each atlas cell is laid in a slot of its own with a four-texel gutter of its own
  border texels round it, and its clear texels take the colour of the art beside them, ring by ring
  (`GLTexture.padTransparentRings`): a sample at the edge of a cell, or a halving of one, never
  reads the next cell in the atlas or the black a PNG leaves in a clear texel. Without them every
  corner of the ground's diamonds carried a dark dash. On the render bench (the Hive, and the Meadow
  with the edge pass off) it costs nothing that shows. The texture is 9MB with its halvings (6MB
  before), and the process's footprint is what it was: the texture is built a slot at a time, the
  halvings made by `GLTexture.halve`, in three small buffers. With the volcano's, the graveyard's
  and the desert's tiles (56 in all) it is 12MB, and the one row of tiles the PNG is, 4928 texels
  across in slots, is wrapped into two bands of 28 so the texture stays inside 4096 across, which
  is all the oldest GPUs this runs on promise. Halved by the driver
  (`glGenerateMipmap`) it took 11MB more graphics memory than it holds, and a 7MB copy of the
  atlas, freed, stayed dirty in the process all match (macOS keeps a large freed block). It loads
  in ~90ms at the start of a match.
- **Name plates, health bars and damage numbers are drawn over the finished frame** — after
  the composite, at the display's full resolution, with the world's projection so each lands
  where it did. In the scene they were only as sharp as the scene (a third of the display's
  pixels at Low), dimmed by the light map and tinted by the grade: a white damage number on a
  dark map was muddy grey, a pale yellow one on snow all but invisible. Damage numbers now have
  a heavy eight-copy outline; a name on its dark plate needs no outline at all, which was four
  more copies of every glyph. Name text is 12 world units high (it was 14 with a wider plate,
  and a crowd's plates covered the heads of whoever stood a row behind). The overlay fades in
  with the match, as the world does.
- **A hit draws blood** (`world/BloodPainter`, which draws it, and `world/Blood`, which works it out;
  `HitLog` in client/game). Every hit the server tells us of (a HIT or a PIERCE: a shot, a splash, a
  slam or an explosion catching someone; never a shielded or phased player, whom nothing strikes)
  spurts a few drops of blood from the middle of the struck figure, where it is drawn: 5 for the
  lightest blow, more the harder the type can strike, 12 at most (`Blood.dropsFor`; an arrow's 25 is
  8), and none for a type that can hurt nobody (a haste). They fly away from the projectile: from
  where a blast went off, and the way a shot was going, since a shot's hit circle can find a figure
  from just past its middle (a melee swing does); about a quarter of them back out the way it came;
  all round for a slam on top of them. Each arcs down under a gravity snappier than the world's (460
  units a second each second; a figure is 48 units), slowing as it flies out, and lands within two
  cells, leaving a splat where it lands on ground anyone can walk on (none in water, a
  wall or off the map): spreading for 60ms as it lands, darkening as it dries, fading out between
  2.3 and 3.5 seconds after the hit. A drop in the air is a red teardrop with a dark rim, drawn out
  behind it the faster it goes, drawn over the frame after the blasts' air layer, so it shows over
  the hit's own flash; a splat is drawn with the ground before the traps, so a trap still reads where
  a fight has been, and under the players' shadows and whatever stands in front of it. Blood is
  opaque and deep red, never added as light: the hit's flash (`GlowEffectPainter`) is a glow in the
  colour of what struck, which on sand and snow adds next to nothing, and in a firefight is one glow
  among the shots' and the blasts'. Hits were hard to see (2026-11-13).
  - Each hit is heard once, in order, through a ring the packet thread writes and the render thread
    reads (`HitLog`: who, where the projectile was as it struck, its heading and type, when), so a
    fan's three shots striking in one tick are three spurts. The renderer takes them as each frame
    begins (`BloodPainter.collect`, after the entities are collected), from where the struck player
    is drawn then, lifted with them if knocked up; a hit heard longer ago than a spurt lasts (before
    this match's window drew anything, or under the game over screen), on someone who has left, or
    more than a screen away is dropped.
  - Every drop's whole flight is worked out as its hit is taken, from a seed of the hit's own, and
    where it is at any moment is a function of the time since the hit (`Blood.flight`, `velocity`):
    frames at any rate draw it the same, the golden frames' stopped clock draws one moment of it, and
    nothing steps it from frame to frame. It is timed on the frame's time of day (`frameTimeMs`), as
    the hit's flash is. 96 spurts are kept, the oldest giving way.
  - It costs nothing that shows. On the render bench with `--hits` (the Hive at 67,65, High,
    4480x2404, 16 players, 150 travelling projectiles, every blow a hit: 21 a second, more than any
    real fight), built with and without it and run alternately, a frame took as long to build within
    the bench's noise (3.32 ms against 3.27 on its slower level, 2.01 against 2.02 on its faster), and
    the GPU shaded 0.07 million more fragments of 66.5 million. Taking hits and making spurts allocate
    nothing (`BloodTest`, which also holds every drop's arc, which way each kind of hit sprays, what
    bleeds and how much, and where a splat may lie); the golden frames `meadow_blood`,
    `snowglobe_blood` and `hive_blood` hold its look, players struck 20ms to 2.9s before the frame.
- **In daylight a shot carries no light** (`RenderContext.projectileLights`, off for `sky`, `sea` and
  `snow`): its pool only brightened the sand or snow round it toward white, washing out the pale
  projectiles in exactly the place they were hardest to see.
- **A trap reads on every ground** (`world/TrapPainter`; design/traps.md, *Drawn*). Each kind fills
  most of its cell (32 units of its 40), inked in a dark version of its own colour on a seat of
  darker ground, with something bright on it: white teeth, a yellow-and-black hazard band, a
  glowing split, a white-hot or pale glyph, silk. The ink and the seat carry it over snow and sand,
  the bright part over the dark floors, and on a dark map a rune, a pod and a lit diode light the
  ground round them. Something on each moves, on the frame's clock and each trap in its own time:
  ours and our allies' send a ring out over the ground they watch as they arm and every three
  seconds after, and every armed trap catches a glint now and then, which is how an enemy's (drawn
  at half strength) is found. The glows and glints of all of them go into one additive pass after
  the traps, so the blend switches once a frame, not once a trap. They were flat decals 24 units
  across in their own colour: the snare's pale web was gone on snow and ice, the bear trap's tan in
  sand and paths, and an enemy's, at 30%, was nothing at all on most ground. Judged with the render
  audit's traps (*Readability audit*): the weakest ground for one of ours went from 0 strong (the
  snare on snow; 27-46 for the other kinds) to 47 (89-110 for the others), and for an enemy's from
  0 for five kinds of six to 38-99. Measured with the render bench's `--traps` back to back (the
  Hive, High, 16 players, 150 projectiles, 30 traps on screen), a frame takes as long to build
  within the bench's noise (1.7-2.4 ms against 1.8-2.0), allocates nothing more (3.1-4.2KB a frame
  either way, no GC), and the pass the traps are drawn in shades about half a million fragments
  more of the frame's 70 million. Every golden frame with no trap in it is byte for byte as it was.
- **An item is drawn as the characters are** (`world/ItemPainter`, `ItemSprites`;
  `scripts/generate_items.py`, scripts/CLAUDE.md, *Item Sprites*). The five items are sprites,
  drawn with the characters' own kit: flat cel tones, one hard shadow away from the light, every
  part inked in a dark version of its own colour, the contour ring round the whole. A cell of their
  atlas is 128 texels for the 40 units it is drawn across (a texel a pixel at High on a HiDPI screen
  at the camera's widest; a character's frame is 128 for 48), filtered linearly through two halvings
  as the characters' and the terrain's atlases are, so at every tier an item is exactly as smooth as
  the figure beside it. They were built every frame out of the shape batch's hard-edged polygons: a
  flat six-sided gem with half-unit lines to its middle, a heart of two discs and a triangle, none
  of them inked. Beside inked, shaded, filtered sprites they read as placeholders, and at Low, where
  the world is drawn at a third of the display's pixels and scaled up, every edge of one was a
  coarse staircase in a frame where everything else was smooth (2026-11-13). The HUD's inventory
  showed the same shapes, and shows the same sprites now (`drawIcon`).
  - **It floats over the middle of its cell**, its middle 16 units above the ground, rising and
    falling 2, over a soft shadow on the point of the ground it lies at, where a player's is. It was
    drawn centred on that point with its shadow 13 units under it, in the cell below. Each kind has
    a life of its own: the gem turns (eight frames are an eighth of a turn of an eight-sided stone,
    so they loop, 90ms each), the heart beats, the star rocks ten degrees either way and the shield
    and the fence four, and a glint comes and goes on each every 1.7 seconds at one of three places
    (the atlas's twinkle, white with a thin line round it, which is what shows on snow). All of it
    is placed by the frame's clock, each item in its own time (its id). The old ones moved by
    counting frames, twice as fast at 120 Hz as at 60.
  - **What lies under it is drawn with the ground** (`drawGroundMarks`, before the players'
    shadows): its shadow, and the light it sheds, every item's in one additive pass. A wall in front
    of an item covers them as it covers a trap. Round the item itself is a soft disc of the same
    light, blended as everything in the depth pass is, so the blend never changes for an item and
    the wall covers that too. In daylight (`projectileLights` off) an item sheds no light on the
    ground, as a shot sheds none, and the disc is at two fifths: added light only washed sand and
    snow toward white, the item's own shadow with them, and there the ink, the contour and the
    shadow carry an item as they carry a character. The old glows (two ovals up to 84 units across and two turning wedges of
    light, per item) went into the additive pass after the whole depth pass, over any wall in front.
  - **It pops into being once, and goes with a puff when it is taken** (`world/ItemTrack`). Every
    item on the client's ground is looked at every frame, on screen or not (`collect`): one is new
    the first frame it is there, and grows from nothing a little past its size and back over 420ms;
    one that was on screen a frame ago and is nowhere now was taken, and swells, rises and fades
    where it floated over 220ms (`drawTaken`, after the depth pass) in ten sparks, little stars of
    light on a dark map and of the item's own colour in daylight. The painter used to remember only
    the items it had drawn: walking away from one was taken for a pickup, its sparks thrown at the
    screen's edge, and walking back to it made it pop into being again. The sparks were hard
    six-sided dots that all began on one point. After a gap between frames (the game over screen, a
    rejoin, a replay's jump) nothing is shown going. An item is known by its id, since the client
    makes a new `Item` whenever its SPAWN is said again.
  - It costs nothing that shows. On the render bench (the Hive at 67,65, 16 players, 150 travelling
    projectiles, ten items in view), the old painter's classes put ahead of the new on the
    classpath for every other run: a frame took as long to build within the bench's noise (Low: 1.29
    to 1.91 ms against 1.28 to 1.91) and allocated a little less (3.6KB against 4.0KB), and in a
    quiet scene at High on 4480x2404 the GPU shaded 34.95 million fragments a frame against 35.24:
    the old glows were 0.56 million in the effects pass, the new shadows and light add 0.14 to the
    ground marks and the sprites and their discs 0.12 to the depth pass. The atlas is 1.3MB of
    texture with its halvings. An item's sprite is one more texture among the depth pass's two, so
    an item can end a draw call either side of it; ten of them showed in nothing measured.
  - `ItemSpritesTest` holds the atlas to what the renderer takes it for (a cell for every frame,
    each item in the middle of its cell and clear of its edge, the gem's frames stepping round, each
    item in its own colour); `ItemTrackTest` and `ItemPainterTest` hold appearing and being taken
    (the second fails with the old remembering put back); the golden frames `meadow_items`,
    `snowglobe_items`, `lagoon_items` and `hive_items` hold the look, each kind twice at two moments
    of its motion. Judge a change to the art with `scripts/item_gallery.py` (the items beside a
    character on five grounds at High and at Low), then in those frames at `--quality=low`.
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
- **Everything moving is placed at the frame's time** (`GameClient.frameNanos`). A match's frame is
  timed on the display's cadence (`gl/FramePacer`: gl/CLAUDE.md, *A frame is timed on the
  display's cadence*), and `MatchWindow` gives the client that time as each frame begins
  (`beginFrame`) and the renderer a clock that reads it (`FrameClock.onFrames`): our steps are taken
  by it (the input handlers), glided on it (`LocalMovement`, design/characters.md, *A step glides
  from when it fell due*), a dash and a knockback's slide are drawn by it, the projectiles are flown
  to it (`flyProjectiles`), and everyone else is walked on it (`RemoteMotion`, with the frame's
  paced delta). Anything with no window pacing it (a test, a dev tool) reads the client's own clock
  (`nanoClock`). The rest of the frame's time (`frameTimeMs`: a sprite's stride, a pulse, an
  effect's seconds left) stays the system's time of day, which their timestamps are on; a
  millisecond off there moves nothing a step's worth.
  - Our steps used to glide from the frame that took them, over the gap since the step before, on
    the system's time of day. A step is taken on the first frame after it falls due: at 60 fps a
    melee character's 53ms step comes three frames after the last and now and then four, and a
    ranged character's 50ms one on every third frame only while the frames keep exact time (a
    millisecond's wander or a busy machine puts some on the second or the fourth). Each step that
    came a frame early or late cut a glide short and jumped the rest, or ran it out early and stood
    still. Measured walking the Hive in the real loop and renderer (the screen on, 60 Hz), as the
    Ghoul: the sprite stood still in 18 frames of 470 and moved up to 1.68 times its pace in others,
    and the camera, which follows it (and draws us), moved the whole world under us 0.59 to 1.19
    times its average 20 pixels a frame (sd 14%); as the Spaceman on a busy machine, a third of the
    steps came off their frame, and the world moved 0.5 to 1.6 times its average (sd 22%). Glided
    from when each step fell due, the glides follow on from one another exactly: the same walks
    (the same steps on the same frames) are drawn at their pace to within 1% from frame to frame,
    the world scrolling 20 or 21 pixels a frame (the camera's whole pixels: sd 2%).
  - A dash was drawn on the cell it had reached, which comes a whole one every other frame or so (10
    cells in 350ms is 0.48 a frame): it moved in jumps of a cell. It is drawn along its line now.
- **Other players are walked along the cells they were heard on** (`RemoteMotion`, one per player
  in `EntityCollector`). Every cell the server puts them on is a waypoint; the drawn player walks
  from one to the next at the pace they come in at (measured, so a bot's slower walk, a slow and a
  boost come out right), keeping far enough behind the newest that a step that arrives a little late
  finds it still walking. Further behind than walking leaves it (a dash), it catches up; a
  move of more than 4.5 cells between two updates (a blink, a respawn) is drawn at once. It is
  never drawn past the newest cell, and it is sorted where it is drawn (*Everything is sorted where
  it is drawn*). It used to guess a velocity from the time between the last two changes it saw and
  run them on at it for up to 75ms after the news stopped, through whatever was in the way: two
  updates a frame apart read as 55 cells a second, and a dash (a cell a frame, 60 cells a second)
  ran the drawn player up to four cells past where it ended, into the tree that ended it, before
  pulling them back. Measured in a live match, a dashing player was drawn ahead of where the server
  had them in 18% of frames, up to 3 cells; now in none. `RemoteMotionTest` pins it; six of its
  seven cases fail on the old smoothing.
  - A walker keeps an even pace, but their cells don't come evenly: each step is taken on one of
    their frames, the first after it falls due (a 50ms step two, three or four frames after the
    last at 60 fps), and the network adds its own. So the pace is measured over the last 0.6s of
    cells unless the last quarter second is more than a fifth off that (a slow, a boost, a stop:
    then that); and the walk is eased toward having 30ms of its pace left to walk to the newest
    cell as the next one arrives (`BUFFER_S`, the most a walker's frame and a home network make a
    step late), judged at each arrival and averaged over four. Until 2026-11-12 the pace was the
    last quarter second's, moving with every step a frame early or late, and the walk kept 0.8 of a
    cell behind the newest, judged every frame: that rose a cell with each arrival and fell away
    till the next, so the walk sped up and slowed down with it three times a step, and in cells it
    was a third less time in hand for a diagonal walker (whose steps are 1.4 cells) than for one
    walking straight, so it caught up with the newest cell and stood still whenever one came a
    little late. Catching up begins past 2 cells or 0.1s of the pace, whichever is more, for the
    same reason. On a diagonal walk heard as another client steps it (its frames wandering a
    millisecond, the network 20-28ms), drawn by the real loop and renderer with the screen on, a
    frame's step was anything from nothing to 1.25 times the pace (sd 19%; 10 frames of 294 under
    half of it, 4 standing still), and is now 0.80 to 1.26 (sd 3.5%, none); over simulated walks
    on a Wi-Fi's 60-90ms, frames under half the pace went from 19-32 in ten seconds to under one.
    The price is that everyone else is drawn about 45ms behind the newest cell heard of them, where
    it was about 25ms.
- **A knockback is slid, not walked or jumped.** A push is a server move the server also counts as
  a knockback (design/combat.md), and a new count in a player's updates makes `RemoteMotion` slide
  them from where they are drawn to where it put them: out faster than anyone runs and slowing to
  a stop (`KnockbackSlide`, ~200ms for 3 cells, the same whatever pace they had been walking at),
  with the walk going on from its end. `PlayerPainter` holds their stride while it lasts (a shove
  is not a walk), and they are neither turned to face the way they were thrown nor shown
  teleporting (`OtherPlayers`). A short push used to be walked there at their own pace (a bot's
  at half a player's), a long one jumped, with a teleport's flash; the local player blinked
  there, and the camera glided after. Filmed through the real server tick, two clients and this
  renderer, the victim moved 9 cells in one frame under a Tidal Wave from three cells and 25 under
  a fan of gusts; now 3 and 5 cells over 9 and 11 frames, never more than a cell a frame.
- **An aim is drawn in one hand** (`AimArrowPainter`). Whatever is aimed, its aim is made of the
  same few things: a sheet of translucent gold lying on the ground in the shape of what it shows (a
  lane, a disc, a row of dots), rimmed in the HUD's gold (the aimed slot's rim: 1, 0.85, 0.51) with
  a dark ink line just outside the rim, and every edge faded over a pixel (gl/CLAUDE.md, *Smooth
  edges*). A lane is one band with rounded ends, fading in from just outside our ring, cut square at
  the end of its range, and a soft light runs out along it every 1.3 seconds (a dash's in 0.65, a
  charge's quicker as it builds); a disc where something lands has a dot at its heart, and a lane
  pointing at a disc stops a little short of it. The ink carries it over snow and sand and the gold
  over grass and the dark floors: four palettes were put side by side over snow, sand, grass and the
  Hive's teal, and the HUD's gold with a fill of (1, 0.78, 0.26) and the ink at 75% read on all four.
  It replaced aims drawn in hard-edged strokes, which looked cut out of the pixel grid: a lane was
  two pale cream rails with chevrons marching between them, crossing and doubling at every joint,
  rings and ellipses were staircases, the dots octagons, a dash's lane ran on under its landing
  ring, a gem's three shots were three lanes tangled into one another, and on snow and sand the
  cream was only its thin ink. What each kind of aim shows is in the two bullets below, and where it
  lies as we walk in *An aim moves with us*.
- **A charged shot's aim is a lane on the ground** (while we charge a primary that scales with
  charge): one lane on to where a full charge would carry the shot, filled as far as the charge would
  carry it now and only a faint promise beyond, so the charge reads as distance, with a disc where it
  would land now. It runs its whole reach whatever is in the way, through walls and props, though
  the shot will stop at the first of them: from 2026-09 to 2026-11 it was cut square at that face,
  but a lane whose length jumped with every wall the pointer swept past was harder to aim with than
  one that always shows the range (the user's call, 2026-11-08); the wall is there on screen to see,
  and the aim, drawn in the ground pass, runs under it. Its gold is deep
  as it starts and bright at full, when a ripple breathes off the disc as the gauge's does round our
  feet; never the gauge's white-hot, which on snow left only the ink. On a dark map weak lights 1.2
  cells apart light the ground it runs over: away from our own light the light map dimmed the gold to
  khaki, and lights further apart glowed in beads. Before the lane it was a translucent "cylinder"
  that read as a glitch: a slab 3.6 cells wide over a darker body, both in the player's random colour
  mixed with a green-to-red heat ramp (a grey-olive smear on the Hive's teal floor, a dirt track on
  snow); a spine of 14 separate quads whose width stepped at each; half-rings on one side of it only,
  racing along it on the frame count; and the whole throbbing twice a second. The golden frames
  `hive_charging` and `snowglobe_charged` (its whole reach through the trees) hold it.
- **An aimed attack shows where it will go** (`AimArrowPainter.drawAimed`). Q or E is aimed from its
  key to the click that casts it, and the primary while its button is held (design/characters.md,
  *Casting mode*), and while one is its aim lies on the ground, worked out by what the cast itself
  will do — except that a shot's lane always runs its whole range (as the charged lane does,
  above): a shot is a lane to the end of its range, through any wall in the way, rounded there, with
  the disc of the blast it sets off at the end of its range, an explosive's or a splash's; a gem's
  three shots, five degrees apart, are one lane widening to take them in; a fan is a narrower lane
  for each of its projectiles, each its whole range, and an arc at their reach; a dash is the lane to a disc where it ends
  (`GameClient.dashLanding`, over whatever lies between, as a dash goes), inside a thin ring of how
  far it can go, since it stops at the cursor; a blink is dots to a disc where it lands
  (`blinkLanding`: its whole range, short of the first cell it can't land on); a trap is dots to the
  ground it watches where it lands (`trapLanding`), inside the ring of how far it can be thrown. A
  slam goes off as its key goes down, so has no aim. It is gold while the click (or, for the primary,
  letting go) would set it off, and a dimmer stone grey while it wouldn't (the primary's half second,
  a ceasefire, a hold, a root under a dash or a blink), easing between the two over 70ms, with white
  lights rather than gold. An ability's shows as its key goes down and fades in over 90ms; the
  primary's only once held 60ms, so a click shows nothing. The HUD rings the ability's slot in the
  same gold or grey (`hud/AbilityBarPainter`), so which ability the next click casts can be read off
  the HUD too. The aim is drawn in the ground pass, so a wall or a tree in front of it covers it, and
  lit as that is on a dark map. `GameClientAimTest` holds what is shown to what is cast, and the
  golden frames `meadow_aim_blast`, `hive_aim_fan`, `colony_aim_dash`, `snowglobe_aim_blink`,
  `cell_aim_trap`, `meadow_aim_primary`, `meadow_aim_gem` and `colony_aim_ceasefire` (grey) how each
  looks, aimed by the first character in the roster with one (`devtools/HeldAims`, so a scene
  survives a kit changing).
- **An aim moves with us** (`world/AimGeometry`, which works out where it lies for the painter).
  Our cell, which every cast starts from, runs up to a cell and a half ahead of where we are drawn
  as we walk: a step puts us on the next cell at once (every 50ms at a ranged character's pace), the
  sprite glides there over the step, and the camera follows the sprite. The aim was worked out from
  our cell and drawn from the sprite, so it swung with every step, 20 times a second: filmed walking
  with the pointer still and six cells off, a lane's angle on the screen sawed between 150.8 and
  158.3 degrees in jumps of up to 5.7, while standing it pointed at 141.3, at the pointer; and a
  dash's, blink's or throw's landing hopped a cell at a time. The pointer was read into the world
  after each frame, with that frame's camera, too, so the aim drawn in the next was a frame of the
  camera's travel behind the crosshair. Now:
  - The pointer is read into the world as soon as each frame has moved the camera, before anything
    is drawn (`GLGameRenderer.onCamera`, which `MatchWindow` points at
    `GLMouseHandler.refreshWorldPosition`), and the aim runs from where we are drawn toward it, so
    walking with the mouse still it stays put on the screen, as our sprite does: the same walk holds
    the lane between 141.27 and 141.41 degrees, the camera's rounding to whole pixels. A cast still
    goes from our cell toward the pointer, so a shot and its lane meet at the pointer.
  - A landing is snapped to a cell as the cast snaps it, but from where we are drawn
    (`AimGeometry.openLanding`: each cast's own rule with nothing in the way, in its own arithmetic),
    and moved by whatever the terrain, the map's edge or a barrier takes off the cast's own landing.
    On open ground it moves exactly as we do, and standing it is the very cell the cast puts us or
    the trap on. What the terrain takes off is worked out from our cell, so it shifts as we step along
    a wall, and is eased (60ms, or 35ms for a change of a cell and a half or more), as the snapped
    landing is from cell to cell as the pointer sweeps it (35ms).
  - A controller's stick aims a way from our cell (`ControllerHandler`: a point ten cells out), not a
    point on the screen, so its aim keeps the stick's heading from where we are drawn
    (`RenderContext.aimAtPointer` is false while the stick aims, and in the dev tools, which set the
    aim point themselves and never walk).
  `AimGeometryTest` walks us as the renderer would and holds all of it: the aim not turning, a
  landing not moving on open ground and the cast's own cell standing, what a wall takes off shown,
  the stick's heading, and `openLanding` to the casts aimed every way at every distance.
  What it costs was measured back to back with the build before, alternating, in a copy of the
  render bench drawn into a hidden window (the screen was locked, which the bench's own window, made
  from the JavaFX thread, doesn't survive): the Hive, High, 2560x1440, 16 players, each kind of aim
  turning slowly round us. A frame took as long to build within the noise (0.8-1.2 ms both ways, its
  median jumping between two levels from run to run), allocated nothing more (1.7KB a frame, or 2.4KB
  with a trap aimed, both builds), and the pass the aim is drawn in shaded about as many fragments:
  0.05-0.16 million against 0.06-0.22, a fan's three lanes 0.11 against its spokes' 0.06, of the
  frame's 15.5. A lane's stations are a quarter of a cell apart only where its fill changes along it
  (as it fades in, round its light) and a cell and a half apart elsewhere, which halves its vertices.
- **The pointer is ours** (`hud/CursorPainter`). Over the match's window the system's arrow is
  hidden and the renderer draws a crosshair where the mouse is, last, over everything, the HUD
  included: the arrow, small and thin, was lost in a fight among the blasts, sparks and name plates.
  It is a gold ring with four white ticks crossing it and a white dot on the hotspot, 40 units
  across (macOS's arrow is 10 by 17), every line inked and the whole casting a soft shadow: the
  ink carries it over snow, sand and a blast's white heart, the white and the gold over the dark
  maps. Tried over the golden frames' firefights on four grounds at 1x and 2x, a white crosshair
  the size of the arrow read as one more name plate (white on dark ink is what a plate is), and a
  cross with no ring was lost among the sparks; the gold ring is on no plate or panel and stayed
  easy to find on every ground. It is drawn from a picture made once with Java2D at the display's
  pixel density and put down a texel to a pixel: the shape batch's shapes have hard edges, which
  make a ring a staircase at 1x, and this is the one thing the eye is always on. The picture is an
  odd number of pixels across at 1x, so the hotspot is a pixel's centre and a 3-unit line covers
  three whole pixels, and even at 2x, where the hotspot is a corner (`artSize`, `cornerPx`).
  - It is drawn by a call of its own after the frame (`GLGameRenderer.drawCursor`, from
    `MatchWindow`), where the mouse is as the frame is finished (`GLWindow.readPointer`) rather than
    where it was when the frame began. A pointer the game draws is still a frame or so behind the
    hand, where the system's is moved by the window server; every game that draws its own crosshair
    pays that, and the aim and the way we face are that far behind anyway, so the crosshair is on
    what we are aiming at.
  - It is only drawn over the window's content while the window has the focus: over the title bar,
    another window, or with another app in front, the system's pointer is showing and ours would be a
    second one. Nor while a controller's right stick aims (`ControllerHandler.stickAiming`): the aim
    is the stick's then, and a crosshair where the mouse rests would lie about it.
  - The system's is hidden by a blank cursor on the window (`GLWindow.hideSystemCursor`), not
    `GLFW_CURSOR_HIDDEN`: on macOS GLFW hides that for the whole app (`[NSCursor hide]`) and shows it
    again only when the mouse leaves the window, so a match window closed under the mouse, which is
    every game over, would leave the menus with no pointer. A cursor set on a window is that
    window's alone, and `GLWindow.retire` puts the arrow back before it hides the window.
  - Measured with the render bench (the Hive, 16 players, 150 projectiles, back to back), it costs
    nothing that shows: a frame took 4.5 and 5.0 ms to build without it and 4.5 and 5.3 with it,
    within the bench's noise, and allocated 3.6-3.8KB either way (the picture is made once, at the
    first frame). `CursorTest` holds the picture (whole at every scale, the dot on the hotspot, put
    down on whole pixels) and where it is ours; the golden frames `hive_cursor` and
    `snowglobe_cursor` hold it where we aim, and the render bench draws it circling the screen.
- **The camera's zoom** is the player's (`RenderQuality.zoomPercent`, `GameCamera.playersZoom`). The
  widest view, 100%, is `Constants.CAMERA_ZOOM` (1.6 of the window's units a unit of the world), the
  view every match had before the setting (2026-11-13); the player can come in to 200% in steps of
  10, in the settings' card (ui/screens/SettingsDialog) or the match's menu (below), kept with the
  quality and the resolution. Nothing else changes it (gl/CLAUDE.md, *Graphics quality tiers*).
  - The renderer reads it each frame: the view is the window over it (`canvasW`, `canvasH`), and the
    culling, the light map and the particles follow; the cached background is drawn again when the
    view changes size. The camera keeps the zoom of each update, so the mouse is read into the world
    with the frame's own (`screenToWorld`), and a shake moves the view as far on the screen at any
    zoom (`CameraTest`).
  - **Name plates, health bars and damage numbers keep their size on the screen.** They are drawn in
    the widest view's units, each where its player is drawn times the zoom over the widest
    (`RenderContext.overlayScale`, in `WorldOverlayPainter`). Drawn with the world, they grew with it:
    at 200% a plate was wider than its character, and a crowd's plates buried the fight (the golden
    frame `meadow_zoomed` is that crowd).
  - Zoomed in, the art is magnified: on a HiDPI screen a tile's 80 texels across its 40 units are 3.2
    pixels a texel at 200% (1.6 at 100%), a character's frame 1.9 (1.2). The filtered atlases keep it
    soft rather than blocky.
  - At 100% every number in the frame is what it was (the factors are exactly 1, and a product with
    1 is the number itself): built from one snapshot of the tree with and without the zoom, every
    golden frame but the two zoomed scenes came out byte for byte the same, at High and at Low. The
    dev tools draw at 100% unless `--zoom=` says (the render bench's `--zoom=200`).
  - Closer is a little cheaper on the whole and dearer at its worst: on the render bench (the Hive at
    67,65, High, 4480x2404, 16 players, 150 shots that travel, alternated with 100%), at 200% a frame
    took 1.44-1.46 ms of CPU to build against 1.52-1.53 and the GPU 13.6 ms on average against
    14.3-14.7 (fewer cells on screen), but 22.7 at the 99th percentile against 17.6-18.0: a blast or
    a glow covers four times the pixels.
- **The menu Esc brings up** (`MatchMenu`, which lays it out and says what each control does, and
  `hud/MatchMenuPainter`). Esc used to arm leaving the match, the HUD asking for a second within three
  seconds; it brings up a menu now, and Esc again, or Resume, puts it away. A card of the menus' dark
  glass over a shade: the graphics quality, the resolution (the display's own and those below it, as
  the settings' card offers them), the camera's zoom (a step out, a track, a step in), Leave match (End
  practice in practice) and Resume, and the keys worth knowing. Each setting is taken and saved as it
  is picked, as the settings' card takes it, and seen at once in the match under it.
  - Nothing pauses a match others are playing: it goes on round the card. The card can't be seen
    through: the name plates under it are white text too, and at 94% they read through it.
  - While it is up it has the keyboard, the mouse and a controller: nothing walks, aims, fires or
    casts, what was aimed or held is put away as it comes up (as the chat puts it away), a click it
    took fires nothing even let go of after it has gone, and the aim stays where it was. F7, F8 and
    F11 work as ever. The system's arrow shows over it (`GLWindow.showSystemCursor`), as over the
    menus, and the crosshair isn't drawn.
  - Leaving takes Leave pressed twice within three seconds: the first turns it red ("Press again to
    leave"), anything else pressed takes it back. That is what Esc's own two presses were for: a
    stray press never ends a match.
  - The arrow keys move between its rows and change the setting on one (held, they go on), Enter or
    Space presses the button picked (Resume, as it comes up); a controller's Start brings it up and
    puts it away, its pad, A and B work it. Where they are shows in sky blue once they are used.
    Words too wide for their button (a translation) are drawn smaller.
  - It costs the GPU the shade over the whole window: 1.0-1.6 ms a frame on the render bench at High
    on the 4480x2404 iMac (`--menu`), the CPU nothing that shows, and it allocates nothing more
    (3.7-3.9 KB a frame either way). Nobody fights from the menu.
  - `MatchMenuTest` holds the model, `GameClientMenuTest` the real handlers with it; the golden frames
    `meadow_menu`, `snowglobe_menu_leave` (at 150%) and `practice_menu_end` how it looks.
- **The scoreboard Tab holds up** (`LiveStandings`, `hud/ScoreboardPainter`). Held, Tab (a
  controller's Back) puts the match's standings over most of the screen, between the HUD's top row
  (the clock, our kills and deaths) and its bar along the bottom, which stay showing (half under its
  card they read as clutter: `TopClear`, `BottomClear`, to move with them); let go of, it goes, and
  the game can be played under it. Everyone in the match, us in gold, anyone dead just now with a skull
  and anyone who has left (still scored by the server), each with the character they play, its
  portrait, the player's name, kills, deaths and score (kills less deaths), placed exactly as the
  match's end will place them (`MatchResults`: the score, then kills, identical lines sharing a place;
  in Teams a section a team with its kills and score, the one ahead marked); along its top the mode,
  the map, the time left and our ping, and in practice its tallies along the bottom.
  - The kills and deaths are the client's own count of the match's KILLs (`MatchProgress.killsOf`,
    `deathsOf`): each carries its killer's count as the server keeps it, and whoever they killed has
    a death more. The whole run of them comes by TCP from the match's start, so it is the count the
    match's end scores.
  - Rebuilt in arrays it keeps when a KILL, a join or a leave changes them, and once a second for the
    rest; nothing allocated as it is drawn. Never left up: the chat, the menu and the window losing the
    focus take it down, and Tab let go of always does.
  - It is held up mid-fight, so it is cheap: the game round it is shaded, not the game under its card,
    which can't be seen through. Shaded under the card as well, it cost the GPU 2.6 ms a frame on the
    render bench at High on the 4480x2404 iMac (`--scoreboard`), where a frame already takes 14-15;
    now 0.2-0.3 ms, the CPU about 0.05 ms, allocating nothing more (3.8-3.9 KB a frame either way).
  - `LiveStandingsTest` holds its places to `MatchResults`' over random lines; the golden frames
    `meadow_scoreboard` and `hive_scoreboard_teams` how it looks.

- **The HUD is the menus' dark glass** (`hud/HudStyle`, which every HUD painter draws through). The
  menus were made to look like the HUD (ui/CLAUDE.md, *The look*), and then looked better than it: the
  HUD was hard-edged rounded rectangles in a dozen shades of navy, a dark bar the width of the screen
  along the bottom, a kill feed of 14-unit lines hugging the screen's right edge (sliding in, the newest
  ran off it), and practice's tallies as lines of plain text down the left with a green PRACTICE over
  the grass, nearly invisible, where the clock goes. Now everything is in the menus' terms (ui/theme.css's
  palette, as constants): panes of navy glass, lighter toward the top, with a rim of light brightest
  along the top and a soft shadow that stands them off a bright map; dark wells for what sits in them
  (a slot, a face); keycaps as the menus draw them, with a lip; labels in small capitals in the muted
  grey (12 units); names in 17; numbers in Exo 2 Black, as the scoreboard's stat tiles set theirs (26);
  and what is said big lettered in Exo 2 Black Italic, inked and with a hard shadow, as the
  scoreboard's "Victory!" is (`gl/DisplayFont`). The gold is ours and what is picked; kills are the
  scoreboard's green and deaths its red. Every edge is smooth (`ShapeBatch.fillRoundedRectSmooth`,
  `strokeRoundedRectSmooth`, `shadowRoundedRect`: gl/CLAUDE.md, *Smooth edges*), as JavaFX draws the
  menus'.
  - Where things are, in the window's units: our score top left (kills, deaths, and kills less deaths,
    what the match is won on: design/match-flow.md), and our run of kills beside it while there is one;
    the clock top centre, the opening's countdown under it; the kill feed down the right; a kill of ours
    announced big over the top of the screen (*A kill is announced*); one tray of glass along the
    bottom, centred (`HudTray`), the ability slots and, past a hairline, the item slots with how many we
    carry (gold when it is all we may), each slot its key's keycap at its corner, an item's count on a
    badge, a cooldown's seconds over its sweep; the charge's gauge over the tray, in the gold of its lane
    on the ground; notices over the tray as pills with a dot of their colour; chat over the tray at the
    left, the line being typed in a field rimmed in sky blue with its keys under it.
  - Nothing is text straight on the world: a hint in the muted grey on grass or sand could not be read
    (the chat's keys, practice's Esc), so every line has a pane under it.
  - The feed shows who killed whom by their characters' faces, each a bust cut from the first frame of
    its sheet facing us (`HudStyle.bust`): from just over the top of its art (`GLSpriteGenerator.artTop`,
    measured as its sheet loads), so a squat character's head is in it as a tall one's hat is. A row
    is a pane, ours on gold and our death on berry, a skull between the two; it slides in, flashes, and
    after seven seconds fades and closes up, so those under it move up rather than jump.
  - Practice has the menus' own three tallies where a match's score goes (kills in green, its best run
    in gold, its accuracy in sky blue: practice's results show them in those colours), a pill where the
    clock goes saying PRACTICE in its green with the time the session has left, and the key that ends
    it on a pane under its tallies. It drew a white cross over our own character at every kill; a kill
    of ours is announced as a match's is.
  - The respawn's pane, over the middle of the screen where the fight is, is opaque (`solid`): name
    plates showing through, even at 95%, read as part of it.
  - It costs nothing that shows, once its text and pictures are drawn in as few textures as it can.
    Every change of texture is a draw (gl/CLAUDE.md, *One stream for shapes and sprites*), and its first
    version changed at nearly every element: five font atlases, the characters' and the items'
    pictures, each label in a size of its own beside the next. The HUD's fonts now share their pages
    (`gl/GlyphPages`: all but the damage numbers' large one), and the feed draws every row's pane and
    faces before any of its names. Measured on the render bench (the Hive at 67,65, High, 4480x2404,
    16 players, 150 travelling projectiles; with and without `--kills`), the old HUD and the new built
    and run alternately: 72 to 76 draws a frame either way (what is in view moves it), a frame's CPU
    2.06-2.10 ms with the old and 1.77-1.96 with the new (the bench's runs land on two levels), and
    3.9-5.2 KB allocated a frame either way, no collections. The display font's letters (the
    capitals, digits and marks of every title, at 60 units on a HiDPI screen) take two pages of
    coverage, 1MB, where RGBA would take four; the three new fonts' glyphs share the pages the old two
    had.
- **A kill is announced** (`hud/KillBannerPainter`; `MatchProgress.killStreak`, `lastKill` in
  client/game). Every kill of ours puts up a title big over the top of the screen (its middle 18% of
  the way down), lettered as the menus' titles are, saying what our run of kills in a row has come to:
  ELIMINATED, DOUBLE KILL, TRIPLE KILL, MEGA KILL, RAMPAGE, UNSTOPPABLE, GODLIKE, then LEGENDARY for
  every kill after; FIRST BLOOD for the match's first kill if it is ours (not in practice, whose
  targets don't fight back). From the second its count stands beside it (×2, ×3), and its colour heats
  up as the run goes on: gold, ember, berry, violet. It pops in (half as big again, landing a little
  under its size over 110ms and settling on it by 190), a burst of its colour behind it, light runs
  over its letters, and a ring goes out from a run of five or more; under it, on a pill, whom we took,
  by their face and name. It is up for 2.4s, going over the last 0.38 as it rises a little, and the
  next kill puts it up again a count higher. A kill was a line in the corner's feed, the size of
  everyone else's, and practice alone counted kills close together, in a box the size of the feed's
  lines (the user asked for kills to be flagged big, and a run's count to keep climbing, 2026-11-13).
  - Our run is our kills since our last death (`LocalPlayer.died`, whether the update or the KILL told
    us first), and a new match starts without one. In practice nothing kills us, so a run there also ends
    when the next kill is more than `PracticeComboMs` (4s) coming; its best is practice's Best Combo.
    Practice's old combo lapsed after 2s, and the HUD lapsed it by writing the client's state from the
    render thread.
  - While the run is two or more, a pane beside the score keeps its count (IN A ROW, ×N, a flame in
    a badge of its colour), bumping with every kill; in practice a bar along its foot runs out with the
    time left for the next.
  - The packet thread hands the renderer our newest kill as one object (`KillMoment`: when, the run,
    whom, first blood), so a frame never draws one kill's count with another's name. Every title is
    rasterized as the match begins (`DisplayFont.prewarm`): the first kill is no moment to rasterize one.
  - The opening's end is said the same way: FIGHT! lettered big where a kill is announced, for 1.4s.
  - `KillBannerTest` holds the titles, the colours, how a title lands and every word in every language;
    `GameClientMatchTest` the run (ours and nobody else's, ended by a death however we heard of it, in
    practice by a pause too, first blood once a match and never in practice, nothing carried into the
    next match) and the feed's characters, teams and which kills are ours; `DisplayFontTest` the letters
    themselves. The golden frames `meadow_first_blood`, `hive_triple_kill` (with its light running over
    it), `snowglobe_rampage` (its ring, on snow), `lagoon_practice_combo` and `practice_chat` hold the
    look; the render bench's `--kills` puts a kill in the frame every 70 frames, every other one ours.

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
`desert` is one of the bright ones too, since its maps were made (2026-11-14).

`space` is a station's deck floating in the dark, and is graded nearly as the bright maps are:
`toneMap` 0, bloom above 0.9 at 0.2 (shots and blasts still glow in the dark), vignette 0.16,
`LightSystem.gain` 0.6 and an ambient of 0.55, the art a little dimmed so shots and players still
light it. Under the old dark grade, ACES over the art at 60%, the deck and everyone on it came out
murky. `volcano` and `night` are graded the same but for the ambient, 0.54 and 0.52: their tiles are
painted as dark as they should look, the game dims them a little further, and what lights them back
up is in the scene (*The terrain's own light*). With lanterns as bright as the first ones were (96
units wide at 0.55) and the deck's ambient, the graveyard was a pale haze: every lantern and every
player's pool of light added to ground already near its full brightness has nothing left to do but
wash it out. How dark they may be is set by the characters, who are ringed in dark ink and need
ground lighter than it: the render audit (2026-11-14, High) put the volcano's first floor, a darker
rock at an ambient of 0.50, at an edge of 26.6 for the median character and 18.6 for the worst,
under the weakest ground there was (the space deck: 41.0, lowest tenth 32.3, worst 27.6). Lightened
and at these ambients the scorched rock is 33.9 (27.9, 24.5: the Monk, brown on brown), ash 36.2
(31.1, 28.1), the graveyard's grass 36.5 (31.0, 27.5) and its cobbles 38.0 (32.8, 27.7): the deck's
company, and no lighter than they need be. The desert's grounds measure as the bright maps' do (deep
sand 64.9, 55.9, 43.0). The other dark backgrounds
(`cityscape`, `ocean`) keep the old settings; no map uses them.

**The terrain's own light** (`TerrainPainter.drawGround`, as it lays each cell). Lava lights the
ground beside it: one light to every four cells each way, 120 units wide, so a lake's run together.
A light a cell, as it was when no map had lava, filled the light pool from a lake's far rows and
left its near ones dark (the pool holds the strongest, and equal lights are kept in the order they
come). A lantern sheds a warm pool 80 units wide, flickering a little, each in its own time, and a
hot boulder a small one. The volcano's embers are weather, as the snow is (`ParticleSpawner`): few,
small and rising, so none is taken for a shot.

## Performance

### In game: the render thread allocates next to nothing
A busy frame allocated 167KB (10MB/s) and now allocates ~3.5KB, with no GC during a
15-second bench run where there were nine. What it took, and what to keep that way:
- `EntityCollector` is a flat array of linked lists (by diagonal now, by cell then), not a
  `Map[Long, ArrayBuffer]` — the boxed cell key looked up for every visible cell was two thirds
  of all allocation, and cells the renderer removed from the map never returned their buffers to
  the pool.
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

### In game: the render bench's projectiles are mostly ground slams
The render bench throws every projectile type in turn and retires each at the end of its range,
but a ground slam (speed 0, range 0: Shield Bash's shockwave, Axe Spin, the Howl, Frost Nova and
sixteen more) never travels past 0, so it is never retired. A few seconds in, 120 of the bench's
150 projectiles are those twenty slams six deep, each an expanding `aoeRing`, and that is the scene
every number above with 150 projectiles was measured on: a CPU profile of it was half `AoeRings`
and the rings it strokes. In a match a slam goes off at once. With only the types that travel
(2026-10-15, the Hive, High, 2560x1440) a frame took 1.6-2.0 ms to build where the default mix took
2.2-3.3, and the shapes it drew were 259,000 vertices a frame where the slams made 345,000. Profile
with a mix that travels (`--travelling`) before optimising for projectiles.

### In game: what the GPU's time goes to (ablation, 2026-11-08)
Fragment counts say what a weak GPU pays; on this machine (M4, a 4480x2404 display) the render
bench was also run with each part of the frame switched off in turn, to see what each costs in time
(`--travelling --novsync --at=65,65` on the Hive). The one surprise was the composite: its shader's
arithmetic, every branch included, was a third of the frame at Low, which is why it is now a
program per combination (gl/CLAUDE.md, *The composite is a program per combination of its parts*).
After that, at High: the 150 projectiles ~1.5 ms, the bloom chain ~0.9, the ground ~0.7, the
composite's sharpened full-resolution pass the most of what is left; at Low a frame is mostly the
composite at the display's resolution and the time it takes the GPU to answer a `glFinish`.

### In game: what building a frame costs
On that fair mix, after `ShapeBatch` came to share its vertices (gl/CLAUDE.md, *A shape's
vertices are shared*), a frame builds in about 1.6 ms at High: projectiles are half of the
render thread's Java time and blasts a seventh, their own geometry more than the primitives that
draw it, and of the time in the driver most goes to the 130 or so draw calls a frame (a switch
between the shape and the sprite batch, or an additive toggle, ends one) and the rest to copying
the vertices (since 2026-11-08 a switch between shapes and sprites is no longer one: gl/CLAUDE.md,
*One stream for shapes and sprites*). The GPU shades about 39 million fragments a frame: the depth pass 21, the post chain
6.9, the ground 3.7, the light map 1.9.

## Readability audit (dev tool)

```bash
bazel run //src/main/scala/com/gridgame/client:render_audit -- --out=/tmp/audit
bazel run //src/main/scala/com/gridgame/client:render_audit -- --out=/tmp/audit --maps=the_snowglobe --what=projectiles
bazel run //src/main/scala/com/gridgame/client:render_audit -- --out=/tmp/audit --what=traps
python3 scripts/render_audit.py /tmp/audit     # report.txt and worst_*.png (needs numpy and Pillow)
```

The gallery judges a projectile over three flat bands under the dark maps' grade. This judges
the whole roster, every projectile and every kind of trap over every map's real ground — each
walkable tile covering 2% of its open ground, and each pool covering 2% of it (no trap lies on a
pool) — through the real `GLGameRenderer`: the map's own background, grade, lighting, name plates,
at the player's size. A projectile is shot as every type's own look and then as every character's
own look for a type it throws (`TYPE@Character`, render/projectiles: *A projectile is drawn as its
thrower's*); a trap as ours and as an enemy's, armed. A map whose ground and sky an earlier one
already had (the four space arenas) is shot once. Every picture is taken twice, as rendered and
with what is judged taken out (a character keeps its shadow, light and name plate and loses only
its sprite; a projectile or a trap goes altogether), so the difference between the two is exactly
what it adds to the frame. The script measures that difference as CIE76 colour difference in Lab:
for characters, `edge` (the upper quartile over the silhouette's outer 6px, which is what
separates a figure from the ground); for projectiles and traps, `strong` (pixels per 1000 of its
box differing by more than 20, what reads at a glance; a trap's box is its cell and the next), with
each trap's weakest ground listed. `edge` means little for a trap that lights the ground round it,
since its whole box differs. It writes the worst of each, and the worst on each map, as contact
sheets beside the same ground without them.

Measured at High (2026-09-23): every character's edge is at least 23 on the space maps'
circuit (Windwalker, Mudslinger, Serpent and Nanoswarm, teal and green on a teal floor, are the
lowest, still readable by their contour) and at least 39 on every bright ground. The weakest
projectiles on bright ground were down to 0.6-4 strong: the lightning family and the eleven
wave crescents, pale, translucent, and in lightning's case strobing. After inking the waves
round and joining the lightning's strokes, the lowest on any ground is 8 (the Sniper Beam, a
small inked dart on snow) and the 10th percentile on snow went from 18 to 30. Traps, measured
2026-10-14: *A trap reads on every ground*.

Characters measured again 2026-11-11, after the roster's art was reviewed and redrawn where it
was weak (scripts/SPRITES.md) and with every sprite standing on its shadow (*A player stands on
the ground*): at least 27.6 on the circuit (the Mudslinger, Serpent, Mantis and Windwalker, as
before) and at least 34 on every bright ground (the Photon's pale rays on the boardwalk; everyone
else 40 or more), with medians of 41 on the circuit, 54 on the boardwalk, 60 to 62 on path, sand,
snow and ice, and 78 on grass. Standing on its shadow costs a figure about three points of `edge`
whatever its art, since the rim at its feet now lies over the shadow: the same art measured under
the old placement and the new differed by that much on every ground, and the new art under the
new placement measured the same as the old art or better (the lowest tenth on grass 67.8 to 69.7).
Compare a change to the art between two audits made with the same renderer.

## Golden frames (dev tool)

```bash
bazel run //src/main/scala/com/gridgame/client:golden_frames -- /tmp/golden                 # every scene, High
bazel run //src/main/scala/com/gridgame/client:golden_frames -- /tmp/golden --quality=low   # (or medium)
bazel run //src/main/scala/com/gridgame/client:golden_frames -- /tmp/golden --scenes=meadow,snow
bazel run //src/main/scala/com/gridgame/client:golden_frames -- /tmp/golden --scenes=meadow_menu --lang=ko --zoom=150
```

Fixed scenes — players, projectiles, blasts, barriers, traps, the divider, the HUD (R cooling down
among it; a kill announced and a run of them; practice's), the edges of the maps, a charged shot's aim, every kind of attack being aimed, the pointer,
walls in front of and beside us, a hit's blood at every stage, the five items on four grounds, the
menu Esc brings up, the scoreboard Tab holds up, the camera zoomed all the way in — drawn by the real GLGameRenderer on a stopped clock (`FrameClock`) with its randomness seeded
(`seedRandom`), so the same build draws the same pixels every time. It is the check for a change to
the renderer that is meant to change nothing: write the frames from both builds and compare them
byte for byte (`cmp`). A change that is meant to show must be judged by eye, in the galleries and in
a match.
