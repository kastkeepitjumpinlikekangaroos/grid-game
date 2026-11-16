# scripts

The asset generators and their galleries (Python), and the packaging scripts. Every asset in
`sprites/`, `sounds/` and the generated maps in `worlds/` comes from here.

| Assets | Generate | Judge | Doc |
|---|---|---|---|
| tiles | `python3 scripts/generate_tiles.py` → `sprites/tiles.png` | `python3 scripts/tile_gallery.py /tmp/tilegallery` | below |
| maps | `python3 scripts/generate_maps.py` → every map but the four space arenas | `--preview <dir>`; `python3 scripts/map_audit.py --out <dir>` (is there room to walk on every map) | below |
| character sprites | `python3 scripts/generate_<name>.py`, `generate_all_new_characters.py` | `python3 scripts/sprite_gallery.py /tmp/spritegallery` (the roster), `python3 scripts/sprite_review.py /tmp/review --chars wolf --against sprites` (one character, close up and as the game shows it; `--lint`) | [SPRITES.md](SPRITES.md) |
| sounds | `python3 scripts/generate_sounds.py` → `sounds/*.wav` | by ear: `python3 scripts/sound_audition.py /tmp/audition --before HEAD` (a page, a button for each attack of each character); `sound_roster.py`, `sound_gallery.py`, `sound_audit.py --check` | [SOUNDS.md](SOUNDS.md) |
| items | `python3 scripts/generate_items.py` → `sprites/items.png` | `python3 scripts/item_gallery.py /tmp/itemgallery` | below |
| the app icon | `python3 scripts/generate_icon.py` (`generate_wizard.py` runs it too) | | below |
| readability | (the client's `render_audit`) | `python3 scripts/render_audit.py /tmp/audit` | client/render/CLAUDE.md |

`scripts/balance/` is not an asset's: it is the balance passes' kit, the simulator's runs and the
fit that moves the roster's numbers (*Balance*, at the foot of this file).

The macOS `.app`, the Windows `.exe` and the Linux app and `.deb` (`build_macos_app.sh`,
`build_windows_exe.ps1`, `build_linux_package.sh`) are in src/main/scala/com/gridgame/client/CLAUDE.md
(*Packaging the apps*).

The generators need Pillow (and numpy for the sounds and the audit): `pip install Pillow numpy`,
in a virtualenv if the system Python has neither.

Sprites are pre-rendered images loaded at runtime.

## Tile Sprites
```bash
# Requires Pillow: pip install Pillow
python3 scripts/generate_tiles.py                  # -> sprites/tiles.png
python3 scripts/tile_gallery.py /tmp/tilegallery   # look at it
python3 scripts/tile_gallery.py /tmp/tilegallery --map worlds/the_meadow.json --at 60,52
```

- **Output**: `sprites/tiles.png`, one 80x112 cell per tile id across (2x the 40x56 display
  cell) and four rows down, 56 tiles. The diamond a tile stands on is centred at (40, 92). (The
  game's texture wraps that one row into bands, to stay inside 4096 texels across: `GLTileRenderer`.)
- **The rows depend on the tile's form** (`TileForm`, mirrored in the script's `TILES`): four
  variants for ground and props, four animation frames for pools and moving blocks, one frame
  four times for a block that doesn't move.
- **Drawn in MapleStory's style with the characters' own kit**: `sprite_base`'s `cel`, `blob`,
  `ink`, `shade` and `lit`, supersampled 4x and resampled down. Flat cel fills, one hard shadow
  tone away from the upper-left light, outlines in a dark version of each part's hue. Round trees,
  fat spotted mushrooms, a scalloped grass lip over brown soil on a cliff.
- **A prop is finished as the characters are**: inked `PROP_INK` (1.35) times heavier than it is
  authored, through `PropDraw`, and ringed with the characters' own contour (`add_contour`), so a
  tree reads at the weight of the figure beside it. Inked lighter and unringed, beside sprites drawn
  pixel for pixel (since the renderer filters the tiles as it does the characters), the terrain read
  as cruder art than the characters standing on it.
- Three rules specific to terrain, all from how the renderer tiles it:
  - **Ground and pools are seamless.** They are cut to the same hard-edged diamond the tileset
    has always used, so neighbours meet exactly. Nothing on them is outlined or shaded toward an
    edge, because any mark that touches the edge of one tile shows up as a grid across a field
    of them. Details sit in the middle. A ground that grows is its own flat colour, its tile's
    `color`, all the way to its edge: where two kinds of it meet, the renderer draws one soft, inked
    edge over the tiles' staircase and paints the higher ground's colour over its neighbour's corner
    (`GroundEdges`; render/CLAUDE.md, *Where two kinds of ground meet*), so the tile must be that
    colour there (`GroundEdgesTest` checks the atlas). This gallery doesn't draw those edges: judge
    them in the game.
  - **A block only inks its bottom edge.** Its top meets its neighbours' and its side faces are
    covered by the block in front, so a line anywhere else draws a seam between every pair of
    blocks in a wall. What is on a side face shows only on the outside of a mass of blocks, which
    is exactly where a cliff's grass lip belongs.
  - **Walkable ground stays quiet.** It covers most of the screen and everything has to read on
    it. The first pass sprinkled little flowers and light flecks on every grass tile, and in game
    the meadow read as confetti. No crumbs either: specks of a pixel or two (on the path, the sand,
    the snow) read as noise beside the characters' clean shapes; a few pebbles or ripples a tile do
    the job. And a detail that one variant has alone sits at the same spot in every tile of it, which
    the renderer picks by position: a lone white flower on a grass variant was a grid of dots.
- Props can be drawn bigger than they are authored (`PROP_SCALE`, through `PropDraw`), which scales
  about the prop's foot but leaves line weights alone, so every prop is inked at the same weight.
- **The space maps' tiles** follow the same rules. The deck (`circuit`) is plates two cells across:
  its four frames are a plate's four quarters, which the game picks by where a cell is
  (`Tile.variantAt`), so every cell draws the whole plate and keeps its quarter; a plate a cell was a
  grid of bathroom tiles, and the thin bright circuit traces before them read as static. Open space
  (`void`) is a pool, flat, its four frames four skies picked by position rather than an animation
  (`STILL_POOLS`, `Tile.animates`): animated, every cell showed the same stars and they stood in a
  lattice. The deck meets it in a rim drawn by the game (`GroundEdges`). The cover (`toxic` jelly,
  `obsidian` glass, `plasma` cores) is props, each cell one rounded object inked all round
  (`rounded_block`): as blocks filling their cells they met in a staircase of seams.
- **The volcano, the graveyard and the desert** (tiles 42-55) follow the same rules, each in its
  own light. The volcano's floor (`cinder`) is dark and warm with nothing glowing on it, so the lava
  beside it is what glows; its trees are burnt black and broken short, embers in their bark, and its
  boulders (`magma_rock`) are split by glowing cracks. The graveyard is painted as moonlight makes it
  (grey-green `dead_grass`, lilac stone, wood gone grey) and the game dims it a little further and
  lets its `lantern`s (a lamp on a post, a carved pumpkin, candles on a stone: a light each in the
  game) put the warmth back; its wood is `cypress`es with bare `dead_tree`s among them, since bare
  trees alone, thin as they are, stood beyond the wall like the bars of a cage. The desert's deep
  sand (`dune`) is the pale beach sand's darker, rippled kin, so the two can lie in drifts across
  one another; `mesa` is the red rock that closes its maps in, with nothing on its top (a mark on
  each was a grid of dots over a plateau of them).
- **A prop a map sets out at even steps picks its variants another way** (`Tile.scattered`, for the
  tombstone and the lantern): rows of graves every fourth cell were one stone repeated, because the
  usual rule (`Tile.variantAt`) repeats every four cells along a row or a column.
- Water and ice have no tile overlay in `TerrainPainter.drawOverlays`: the tiles animate and
  shine on their own. The old glints, ripples and frost needles had never actually been drawn,
  because they were collected only for walkable tiles. Once pools were drawn with the ground they
  turned a sea into static and a frozen pond into flocks of white birds. Lava keeps its overlay
  (a vent blistering and bursting), on about a cell in five: a lake of it is more cells than there
  are overlays to go round, and its tile is calmer for the same reason, slow swirls and one plate of
  crust (two plates and a hot spot a cell were confetti over a lake).
- **Judge a change in the gallery, then in the game.** `tile_gallery.py` writes a contact sheet,
  each tile as a patch in a field (seams and grids show up here), little scenes with characters
  in them for scale, and optionally a window of a real map. The engine's grade still changes
  everything, so finish with `render_bench -- --map=... --at=x,y --screenshot=out.png`.
- `TileTest` checks the atlas has a column for every tile and draws each as its form says (a
  ground tile covers its diamond, a prop leaves its ground showing).

## Maps
Nineteen maps in `WorldRegistry` (display names come from the file names; a map's index is kept
with every match played on it, so a new map goes on the end of the list): four space arenas, **The
Cell, The Hive, The Nexus, The Colony** (a deck of plates floating in open space, rimmed and glowing
where it ends, volcanic glass round its edge and jelly and glass for cover), and fifteen that
`scripts/generate_maps.py` writes.

Every map has the same shape, because it plays well: a round arena of about 0.4x the world's
width in radius, in a square world, closed off by terrain nobody can cross, with cover in horseshoes,
arcs and blocks over 2-8% of the arena. They differ in size and in what they are made of. A theme
has a small, a medium and a large map (the Colony's size, 240 cells: a long walk between fights,
for a full lobby); the three bright themes that came first have a medium one and, since 2026-11-14,
a large one:

| Map | Size, arena | Ground | Cover | Beyond the arena |
|---|---|---|---|---|
| The Meadow | 120, r 46 | grass, flower patches, dirt paths (a wheel round a pond) | fairy rings (a horseshoe of trees open to the pond, a giant mushroom at its back), giant mushrooms, grass-topped cliff outcrops, hedges, rocks | a forest behind a flowering hedge; `sky` |
| The Wildwood | 240, r 100 | the Meadow's, with a river round it bridged on the axes and diagonals (seven planks wide), two ring roads | the Meadow's, thickets in solid clumps, rings of old stones on cobbles | the same forest; `sky` |
| The Lagoon | 130, r 50 | sandy beach round grassy middle, boardwalks out from a deck five planks wide round a pool | palm groves (horseshoes), rock pools with coral at the water on two sides, a ring of broken columns, rocks | shallows, then deep sea; `sea` |
| The Archipelago | 240, r 100 | seventeen islands (one in the middle, eight round it, eight far out) joined by boardwalks seven planks wide along the axes, the diagonals and two rings; over a third of the arena is water, which shots fly over | palms, rock pools, coral, columns | the same sea; `sea` |
| The Snowglobe | 100, r 38 | snow, a frozen pond with a fir in the middle, icy patches | snowmen round the pond, snow forts (short ice walls facing the middle), L-shaped ice blocks, pine groves (horseshoes) | a pine forest; `snow` |
| The Tundra | 240, r 100 | snow, a frozen lake, a frozen river winding round it (ice all the way: crossed anywhere), hot springs | crags of rock (`mountain`) and pine woods in solid clumps, pine groves, ice blocks and forts, snowmen | the same forest; `snow` |
| The Crucible | 90, r 34 | scorched rock (`cinder`), drifts of ash, a pool of lava in the middle and one on each axis | basalt columns, horseshoes of burnt trees, hot boulders, the odd spire of volcanic glass | a moat of lava, then the crater's wall of basalt; `volcano` |
| The Caldera | 140, r 54 | the same, round a lake of lava with an island in it (four causeways five wide), a river of lava crossed on the axes and diagonals (seven wide), a ring road of ash, a pair of pools either side of each road out | the same, and basalt outcrops | the same; `volcano` |
| The Cinderlands | 240, r 100 | the same, round a great crater (an island round its heart, four causeways seven wide), eight lesser ones off the roads, two rivers of lava, roads of ash | the same, and burnt woods in solid clumps | the same; `volcano` |
| The Crypt | 90, r 34 | grey-green grass (`dead_grass`), four cobbled paths from a crypt in a paved yard | graves in rows of three with a lantern at the head of a row, broken mausoleum walls, copses of cypress and bare trees (horseshoes) | the cemetery's wall, a dark wood beyond; `night` |
| The Graveyard | 140, r 54 | the same, round a ruined chapel (four doors five wide), a ring road | the same, and four walled vaults eleven cells across with a gate five wide in each side | the same; `night` |
| The Necropolis | 240, r 100 | the same, as a city: cobbled avenues every 26 cells, a boulevard round the middle, a great tomb in its square, a lantern at each corner of a block | each block one of four things: a mausoleum in its walled court (gates five wide), a dead wood round a clearing, a plot with a corner of wall at each corner, rows of graves | the same; `night` |
| The Tomb | 90, r 34 | deep sand (`dune`) with drifts of pale sand, four paved ways from a square tomb in a paved court | obelisks lining the ways, ruined walls, cacti, rocks, bones | a plateau of red rock (`mesa`); `desert` |
| The Oasis | 140, r 54 | the same, round a pool with palms at the water, grass and a beach, a lesser pool on each diagonal | ruined courts on the axes (eleven across, gates five wide), palm and cactus groves, mesa outcrops | the same; `desert` |
| The Dunes | 240, r 100 | the same, round a great tomb; eight oases, a dry river bed winding round | mesas standing in clumps, courts, ruins, groves | the same; `desert` |

The three newest themes are for the characters who had no ground of their own (2026-11-14): the
fire elementals and the Phoenix (the volcano), the fifteen undead and their kind (the graveyard),
and the Mummy, Anubis, the Sphinx, the Sandstorm, the Scorpion and the Djinn (the desert). Still
without one: the sky's (Cloudrunner, Windwalker, Stormcaller, Harpy, Griffin, Valkyrie), the swamp's
(Mudslinger, Hydra, Plague Doctor; `marsh`, `moss` and `thorns` are drawn and unused), the
castle's (the medieval dozen; `wall`, `cobblestone` and `stone` are there) and Greek myth's.

- **Symmetric under all eight of the square's symmetries.** Every decision about a cell is made
  from its offset to the centre with the signs dropped and the axes sorted. That makes a Teams
  match the same match from either half (the divider is the centre column), and
  `WorldMapsTest` checks every generated map is a mirror image across it. The four space maps are
  centred half a cell off the divider's line, so they are only nearly symmetric.
- **Checked before writing**, as `WorldMapsTest` checks every registered map: spawn points on open
  ground with open ground two cells all round them, none on the divider's column, as many in each
  half, all open ground one region, and room to walk everywhere (next). Spawns come from slots in
  one eighth of the arena, mirrored into the other seven. `--preview <dir>` draws each map
  top-down, and beside it `<map>_audit.png`, the audit's picture of it; the script prints each
  map's cover (what stops a shot) apart from its water and lava (what only stops a walk).
- **There is room to walk and to fight everywhere, on all nineteen** (2026-11-15; the report was
  the Archipelago's bridges, "too narrow and hard to walk in", and choke points one cell wide on
  others). A player stands on one cell and one key walks a *diagonal* of the grid (W is (-1, -1));
  a diagonal step goes between two open cells only, and a blocked one slides along whichever side
  is open. So a gap one cell wide in a wall is slid straight past unless the player stops in
  front of it and holds two keys, a staircase of planks along a diagonal can only be walked down
  its middle, and a bridge three wide leaves nowhere to step aside from a shot that flies over
  the water. `scripts/map_audit.py` measures it (its docstring has the terms) and two rules hold,
  which `generate_maps.py` checks before it writes a map and `WorldMapsTest` (`MapRoom`) for
  every registered one:
  - **no tight ground**: every open cell has a 3x3 of open ground round it or beside it, and
    those join up. No gap, door or path is one or two cells wide, and no two corners squeeze a way
    between them;
  - **nothing of any size behind a choke**: the same with 5x5, counting no lone prop (a rock or a
    tree by itself is walked round either side), leaves no piece of ground bigger than an alcove
    (48 cells: a spawn pod, the cup of a horseshoe of trees). So a bridge, a causeway, a gate into
    a court and the way round any wall is five cells wide or more.

  Before it: 64% of the Archipelago's ground was tight (boardwalks three wide, two round the
  rings, a single plank over the middle pool), 29% of the Tundra's, 22% of the Graveyard's, and
  every map had some; the Graveyard's vaults had gates of one cell, every space map's spawn pods a
  door of one, and the woods of the Wildwood, the Tundra, the Cinderlands and the Necropolis were
  trees one or two cells apart. How a map is kept to the rules:
  - What is built is built to them: a bridge or a causeway is `on_axis(a, 2)` and up
    (`on_diagonal(a, b, 4)`: a diagonal wants more, being walked down its middle), a court's gate
    five (`square_ring`), a grove of trees a `horseshoe` with one wide mouth facing the middle
    (they were rings with three or four gaps of two or three cells), a pool's palms or coral stand
    at the water, not a cell back from it, and nothing built stands on an axis or a diagonal of a
    big map but the roads, or just past a crossing.
  - Loose cover (the odd rock, tree, cactus) goes on last, by `Arena.scatter`, which leaves out
    any that would stand within three cells of other cover (of two such, the one that drew lower).
  - Woods, crags and mesas are solid clumps of noise, kept `off_the_ways` (the axes, the diagonals,
    the arena's edge) and a dozen cells from whatever is built: something to go round.
  - Graves stand three side by side, five cells to the next three and to the next row
    (`grave_at`), a lantern at the head of a row (they stood every fourth cell both ways).
  - `Arena.open_up` ends every map: ground still too narrow becomes the cover beside it
    (`map_audit.fill_tight`: two clumps a cell apart become one, the strip between a rock and the
    edge closes), and a pocket that leaves is filled (`fill_pockets`); more than 60 cells walled
    in is a fault in the map, reported, not filled.
- **The four space arenas were drawn by hand**, so they were repaired, once, by
  `python3 scripts/map_audit.py --repair worlds/the_cell.json ...`: a door one cell wide into a
  spawn pod became three (five into the Colony's big pods), a pod's mouth squeezed between two
  corners lost the corners, and what was still tight became jelly or the deck's edge (the Hive's
  and the Cell's two-wide corridors round their cores, the strip of deck behind a pod at the
  rim). By hand before it: the Nexus's core block came out (the room round it was a corridor
  three wide), and three cells of jelly left standing in its doorway after. A map edited in the
  map editor is held to the same rules by `WorldMapsTest`; `--out <dir>` shows where it fails
  (red one cell wide, orange tight, yellow behind a choke).
- **A big map is made of the small one's parts** (`on_axis`, `on_diagonal`, `knot`, `ell`, `bar`,
  `square_ring`, `horseshoe`, `ring_cell`): more of them, further apart, with something that takes a walk to get
  round (a river with crossings, a ring of islands, a city's blocks) rather than the small map
  scaled up, which would be the Colony again: a plain with cover a long way apart.
- **Lava and water are flown over** (design/combat.md): a river of either stops a walk and not a
  shot, so a crossing is somewhere to be shot at from both banks.
- **Backgrounds.** `sky` is MapleStory's: blue paling to the horizon, fat outlined clouds, rolling
  hills with round trees on them. `sea` is open water to a horizon with islands on it: the
  Lagoon's sea runs to the edge of the world, and the sky's green hills behind it read as the
  sea stopping at a field. `snow` is winter hills with firs, with snow falling in front of the
  world. `desert` is the sky's again over dunes, with buttes and a pair of pyramids on the horizon
  (it was an orange dusk from before the bright maps, which no map used). `volcano` is a sky black
  with smoke over a glowing horizon, a mountain with fire in its mouth, embers rising in front of
  the world; `night` a big moon in a violet sky over hills with crosses and bare trees along them.
  Snow and embers are the only weather drawn. `space`, `cityscape` and `ocean` are as they were.
  See client/render/CLAUDE.md, *Grading*, for how each is graded.

## Item Sprites
```bash
python3 scripts/generate_items.py                    # -> sprites/items.png (Pillow only)
python3 scripts/item_gallery.py /tmp/itemgallery     # look at it, from the draw functions as they are
python3 scripts/generate_items.py --out /tmp/x       # or write the atlas somewhere else first
```

- **Output**: `sprites/items.png`, a 4x4 grid of 128x128 cells: the gem's eight frames (0-7), the
  heart, the star, the shield and the fence (8-11), and a twinkle (12); the rest empty.
  `ItemSprites.scala` reads the same numbers, and `ItemSpritesTest` holds the atlas to them.
- **Drawn with the characters' kit, as the props are**: `cel`, `ink`, `shade`, `lit`, flat tones
  with one hard shadow away from the upper-left light, supersampled 4x, the lines 1.5 times the
  kit's weight (`ITEM_INK`: a cell's unit is a little smaller than a character's) and the
  characters' contour ring round each (`add_contour`). An item lies among the characters and has to
  look as if it belongs to them; the renderer filters it as it filters them
  (client/render/CLAUDE.md, *An item is drawn as the characters are*).
- **A cell is 64 authoring units, drawn 40 world units across** (`CELL_UNITS`; `ItemPainter.CELL`),
  and an item's art is about 45 of the 64, centred on the cell's middle: the renderer floats, turns
  and swells an item about that point, and the HUD sizes its picture by the cell. The art keeps six
  texels clear of its cell's edge (`MARGIN`), so the texture's halvings never mix one cell with the
  next. `build_atlas` refuses art that is off centre or in the margin.
- **The gem is modelled, not drawn**: an eight-sided brilliant (a table, a crown of sixteen facets,
  a pavilion of eight) seen from a little above, each facet a flat tone by how it faces the light,
  the edges between them thin and pale, the outline inked. Its eight frames are eight steps of the
  45 degrees that bring it back to where it began, so the renderer loops them and the stone turns
  for ever. The other four are one picture each, moved by the renderer (the heart's beat, the star's
  rocking); the twinkle is white, for the renderer to turn and size, with a thin line round it,
  which is what shows on snow.
- **Big shapes, two or three colours each.** At Low on an ordinary screen an item is under 30
  pixels across: a silhouette nobody else has (a cut stone, a heart, a fat star, a heater shield,
  three boards on two rails) and nothing thinner than about two units survive that. The colours
  are each item's own (`ItemType.colorRGB`, which tints its slot in the HUD), a little lighter, so
  its shadow tone and its ink have room under it.
- **Judge it in `item_gallery.py`'s game sheet** (`items_game.png`): the five beside a character,
  floating over their shadows on the maps' five grounds in the engine's colours, at High and at Low
  on a HiDPI and an ordinary screen (at Low drawn small and scaled back up, as the display does).
  `items_close.png` is every cell enlarged, for faults. Then in the game: the golden frames'
  `meadow_items`, `snowglobe_items`, `lagoon_items` and `hive_items`, at `--quality=low` too.

## Application Icon
```bash
python3 scripts/generate_icon.py   # -> sprites/icon_wizard_{128,256,1024}.png, .ico, and on a Mac .icns
```
Renders the wizard's standing front frame (frame 0 of `generate_wizard.py`'s `draw_wizard`)
standalone, crops/centers it, and exports at icon sizes. It draws through `sprite_base.ScaledDraw` at
`RENDER_SCALE` and applies the same `finish_frame` detail pass at each output size, so the
1024px icon is rendered at native resolution rather than a 64px sprite blown up 16x.
**`generate_wizard.py` runs it after writing his sheet**, because the icon is the in-game wizard:
when he was redrawn and it wasn't, both apps went on showing the old wizard.
The PNGs are the JavaFX Stage/Taskbar icon (`ClientMain.loadAppIcons`/`setDockIcon`,
`MapEditorApp`) and the GLFW in-game window icon (`GLWindow.setIcon`), read from `sprites/` on the
classpath, so every binary carries them, the `_windows` ones (the Windows release jar) included.
The `.icns` is consumed by `scripts/build_macos_app.sh`, the `.ico` by
`scripts/build_windows_exe.ps1`, and the 256px PNG by `scripts/build_linux_package.sh` (the Linux
app's and its menu entry's icon). Pillow writes `.ico` directly (cross-platform); `.icns` needs
macOS's `iconutil`, so elsewhere the committed one is left as it is.

## Balance

`scripts/balance/` is what a balance pass is made with (design/characters.md, *Balance in
free-for-alls against bots*, says what the passes found and what they aim at): thousands of
free-for-alls of bots played on candidate rosters, and a fit that moves each attack's damage and
each character's health toward the middle of its role.

```bash
export BALANCE_DIR=/tmp/balance            # a work folder, outside the repo
scripts/balance/setup.sh                   # the simulator built, the roster's sources kept, numpy
P="$BALANCE_DIR/venv/bin/python"; cd scripts/balance
./round.sh base "$BALANCE_DIR/cand/base/levers.json" 101      # the roster as it is, both ways
$P analyze.py "$BALANCE_DIR/cand/base/roster.tsv" "$BALANCE_DIR"/runs/base_{A,B}.csv
$P solve.py "$BALANCE_DIR/r1.json" base    # a step toward the targets...
./round.sh r1 "$BALANCE_DIR/r1.json" 102   # ...built and played, on line-ups of its own
$P solve.py "$BALANCE_DIR/r2.json" base:0.5 r1               # and so on, older rounds counting less
$P snap.py r5 "$BALANCE_DIR/final.json" 0.3 r4 r5            # the last one, without its nudges
./round.sh final "$BALANCE_DIR/final.json" 101               # the same line-ups as `base`: before and after
$P compare.py base base final final; $P changes.py final
cp "$BALANCE_DIR"/cand/final/roster/*.scala ../../src/main/scala/com/gridgame/common/model/roster/
$P web.py "$BALANCE_DIR/cand/final/roster.tsv" ../../docs/index.html ../../docs/index.html
```

- **The simulator** is `//src/test/scala/com/gridgame/server:balance_sim` (`BalanceSim.scala`):
  mixed free-for-alls of fourteen bots (five fighters, nine ranged, dealt evenly from the roster by
  the seed, every map in turn), launched as a lobby's are and played on a clock of their own
  (`SimKit`), five minutes in about three seconds. The same seed and the same code play the same
  match, to the byte of the results, however busy the machine: two rosters played on one seed meet
  the same line-ups. It writes a line a player: kills, deaths, place, and what dealt each kill (from
  the match's journal). `run.sh` plays a run's matches in several processes at once.
- **Two ways of playing** (`round.sh`): `_A`, the bots as they are, and `_B`, the way a player
  plays, every bot firing its primary each half second (`pace=500,550`) and the melee ones swinging
  at all a swing will strike (`reach=1`). Both are settings on `BotController` that nothing in the
  game sets. What a character is steered by is a mix of the two (`solve.py`'s `WA`), and why is in
  design/characters.md.
- **A candidate roster is the roster's own sources with levers applied** (`build.sh`): a multiplier
  for each projectile type on every damage number it has (the hit, a splash, a blast's centre and
  edge, a poison's and a burn's total, a charge's and a distance's two ends), a health for a
  character, a cooldown for an ability (`levers.json`: `proj` by type id, `hp` by character id,
  `cd` by `<id>:q|e`, `trap` by name). `apply.py` writes them into a copy of the sources as they
  were at `setup.sh` (never onto a candidate, so rounding doesn't add up) and keeps the roster's
  rules as it goes (no hit over 55, a blast's edge at most three quarters of its centre), `sync.py`
  moves each ability's listed damage with its projectile's, and the toolchain's own `scalac`
  compiles the result against the simulator's jars in a few seconds; its classes go first on the
  simulator's classpath. Nothing is built in the tree, and a pass ends by copying the last
  candidate's sources into it: `RosterDump` (in `BalanceSim.scala`) run on the tree's build then
  writes the same `roster.tsv` as the candidate's, which is the check that the tree has what was
  measured.
- **The fit** (`solve.py`): a character's score moves by a slope times the log of its damage,
  each attack counted by its share of the character's kills, and a slope times the log of its
  health. `calib.py` measures the slopes on a roster with every lever moved at random
  (`perturb.py`), played on the baseline's seed; on 2026-11-15 they were 28.5 kills less deaths a
  match per log of damage for a ranged character and 21.5 to 25 for a fighter, 14 and 23 per log
  of health, and that model alone foretold a change with r = 0.96. A step is ridge least squares
  toward each role's target, inside the bounds; rounds on other seeds are pooled, carried to the
  current roster by the model. Raise the pull on health (`MU`) while attacks can still move, and
  lower it (60) when two who share an attack are stuck on either side of the middle.
- **Reading it**: `analyze.py` (every character, both ways and the mix), `compare.py` (a pass's
  before and after: the roles, the spread within each, who wins most), `show.py` (who is still off
  its target, and its numbers then and now), `changes.py` (every number a candidate moves).
  `snap.py` drops the levers worth next to nothing to every thrower of their attack. Judge that by
  what a move is worth, not its size: a point on a 22-damage primary is more than a kill less a
  death a match.
- **The website's numbers** (`web.py`): the damage on every character card's ability lines written
  from a `roster.tsv`, the rest of each line left as it is (`gendocs` writes the health pills:
  tools/CLAUDE.md).
- A round of 1,184 matches each way takes about eight minutes on an idle machine. Check a
  finished pass on a seed no round used.
