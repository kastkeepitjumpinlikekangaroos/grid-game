# scripts

The asset generators and their galleries (Python), and the packaging scripts. Every asset in
`sprites/`, `sounds/` and the generated maps in `worlds/` comes from here.

| Assets | Generate | Judge | Doc |
|---|---|---|---|
| tiles | `python3 scripts/generate_tiles.py` → `sprites/tiles.png` | `python3 scripts/tile_gallery.py /tmp/tilegallery` | below |
| maps | `python3 scripts/generate_maps.py` → the Meadow, the Lagoon, the Snowglobe | `--preview <dir>` | below |
| character sprites | `python3 scripts/generate_<name>.py`, `generate_all_new_characters.py` | `python3 scripts/sprite_gallery.py /tmp/spritegallery` | [SPRITES.md](SPRITES.md) |
| sounds | `python3 scripts/generate_sounds.py` → `sounds/*.wav` | `sound_gallery.py`, `sound_audit.py --check` | [SOUNDS.md](SOUNDS.md) |
| the app icon | `python3 scripts/generate_icon.py`, `scripts/generate_icns.sh` | | below |
| readability | (the client's `render_audit`) | `python3 scripts/render_audit.py /tmp/audit` | client/render/CLAUDE.md |

The macOS `.app` and the Windows `.exe` (`build_macos_app.sh`, `build_windows_exe.ps1`) are in
src/main/scala/com/gridgame/client/CLAUDE.md (*Packaging the apps*).

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
- Water and ice have no tile overlay in `TerrainPainter.drawOverlays`: the tiles animate and
  shine on their own. The old glints, ripples and frost needles had never actually been drawn,
  because they were collected only for walkable tiles. Once pools were drawn with the ground they
  turned a sea into static and a frozen pond into flocks of white birds. Lava keeps its overlay.
- **Judge a change in the gallery, then in the game.** `tile_gallery.py` writes a contact sheet,
  each tile as a patch in a field (seams and grids show up here), little scenes with characters
  in them for scale, and optionally a window of a real map. The engine's grade still changes
  everything, so finish with `render_bench -- --map=... --at=x,y --screenshot=out.png`.
- `TileTest` checks the atlas has a column for every tile and draws each as its form says (a
  ground tile covers its diamond, a prop leaves its ground showing).

## Maps
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

## Application Icon
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
