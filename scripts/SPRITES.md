# Character sprites

The original 12 characters each have a dedicated generator script (`scripts/generate_<name>.py`;
the Spaceman's is `generate_spaceman.py` -> `sprites/character.png`). The other 100 live in seven
category files (`generate_{elementals,undead,medieval,scifi,beasts,mythological,specialists}.py`)
and are generated in batch.

```bash
# Individual character (original 12)
python3 scripts/generate_gladiator.py   # -> sprites/gladiator.png
python3 scripts/generate_wizard.py      # -> sprites/wizard.png
# etc.

# Batch generate the other 100 (all ~10s, pure Pillow, no numpy)
python3 scripts/generate_all_new_characters.py
```

Each sprite sheet contains 4 directions x 4 animation frames. Frame 0 of each row is also the idle
pose (the renderer shows it whenever a player stands still), so the walk is stand / stride / stand /
stride.

**A character stays inside its tile**: nothing in any frame more than 26.6 of the 64 authoring units
either side of the frame's middle, which is 20 of the 48 it is drawn at, a tile's half-width. The
renderer puts a sprite in front of a wall or behind it as one no wider than its tile, so whatever
reaches past it lies over the corner of a wall level with it, as the Chameleon's tongue did until it
was drawn shorter. `CharacterRosterTest` checks every sheet.

**Every script goes through `sprite_base.generate_character`** — the individual
ones only own their `draw_<name>` function, they do not run their own render
loop. That single chokepoint is what lets a rendering change apply to all 112
characters at once, so keep it that way rather than re-adding a per-script loop.

## Judging a change: the sprite gallery

```bash
python3 scripts/sprite_gallery.py /tmp/spritegallery                  # contact sheets
python3 scripts/sprite_gallery.py /tmp/spritegallery --chars wolf,bear
python3 scripts/sprite_gallery.py /tmp/spritegallery --detail         # 4x4 per character
```

The review loop for character art, in the same spirit as `sound_gallery.py` and
the projectile gallery: 112 characters cannot be judged one at a time, and the
faults that matter are the ones that only show up when they sit side by side —
a roster where forty characters share one bald head, or where every silhouette
is the same rounded slab. Each cell draws the character as the game shows it
(the engine's colours, on its shadow: `sprite_review.in_game`), head-on at the
size a HiDPI screen shows it (154px a frame) and then front, side and back at an
ordinary screen's (`PLAYER_DISPLAY_SIZE_PX` 48 x `CAMERA_ZOOM` 1.6 ~= 77px), on the
deck, the grass and the snow. **Run it after any change here**; judging a sprite
at its 128px sheet resolution and in its sheet's own colours flatters it, and
judging it on one ground hides contrast faults. `sprite_base.render_sheet(draw_func)`
returns a sheet as an image without writing it, for a scratch preview while
iterating on one character.

## Working on one character: the review sheets

```bash
python3 scripts/sprite_review.py /tmp/review --chars wolf,bear --against sprites
python3 scripts/sprite_review.py /tmp/review --lint                 # the whole roster, measured
```

The gallery shows the roster from the sheets in `sprites/`; this is the other half of the loop,
one character at a time, drawn from the draw functions as they are now, so nothing has to be
written to `sprites/` to look at an edit. For each character: all sixteen frames enlarged pixel
for pixel (`_close_a/b.png`, the ground a red line), the character **as the game shows it**
(`_game.png`: standing and striding in every facing, 154px and 77px a frame, on the maps' five
grounds, in the engine's colours, on its shadow), and with `--against` a directory of sheets,
before beside after (`_compare_a/b.png`). The close-ups find faults; the game page is the judge.

**The engine does not show a sheet's colours as they are.** Its grade and lighting lift the dark
end, brighten the middle and compress the light end: a sheet's 40 is shown as about 62, its 128 as
158, its 220 as 225 and its 255 as 240, on grounds brighter than the tiles' own colours. Darks
stay apart (two tones 40 apart at the dark end are about 48 apart in a match), so a dark character
keeps its planes; it is pale-on-pale detail that flattens (white on cream, pale blue on white), on
the very characters that also have to hold on snow and sand. The tool's `ENGINE` and `GROUNDS` were
measured from the render audit's pictures (2026-11-10): the pixels well inside a flat area of each
sheet against the same place in the frame the engine drew, a quadratic for each channel, the bright
maps and the space maps' deck apart, within four or five levels on average. They want measuring
again if the grade changes, **and from flat areas**: a line fitted through every pixel of a frame
against the engine's picture of it came out 0.58 x + 77, far flatter than the truth, because the two
pictures never line up to the pixel and every stray pair pulls the slope toward nothing. A review of
the whole roster was briefed on that figure ("darks merge into one lump") before it was caught. The
render audit (client/render/CLAUDE.md) is still the last word, through the real renderer.

`--lint` prints what can be measured: a sheet cut off by its frame or wider than its tile (`FAIL`),
and what is worth a look and may be meant (`look`): hovering over its shadow, standing at different
heights in different facings, two standing frames that differ, a stride that is the same frame
twice, a rim close to a ground's colour.

## Style: MapleStory

The roster is drawn in MapleStory's style, and each rule of it lives in one place in
`sprite_base`, so a new character gets it by using the kit rather than by imitation:

| Rule | Where it lives |
|---|---|
| **About 2 heads tall**: a huge round head (12.4 x 11.2 units, crown at y ≈ 9) over a small slim body 22 units from chin to sole, short legs, small round shoes. With the feet at y = 54 of a 64-unit frame, a taller figure is only possible by shrinking the skull | `rig` |
| **Eyes are the face**: a tall white nearly filled by the iris, the iris darkening toward the top, a heavy upper lash flicking out at the outer corner, a big catchlight upper-left and a small one lower-right. No nose; a tiny mouth; thin high brows | `ms_eye`, `ms_brow`, `ms_mouth`, `head_face` |
| **Hair is silhouette**: one mass of crown, fringe and side locks with a gloss band across the crown | `rig_hair` + `HAIR_STYLES` |
| **Flat cel shading**: every shape is a flat fill, one hard-edged shadow tone on the side away from the light (upper left), an optional hard highlight. No gradients, no noise | `cel` |
| **Outlines dark but never black**: each part is lined in a dark, slightly richer version of its own hue | `ink`, applied automatically by `ScaledDraw` |
| **Gear is oversized**: staffs taller than the wearer, hats wider than the shoulders, shields half the body | by convention, and the gear helpers (`blade`, `spear`, `bow`, `round_shield`) |

**The proportions are deliberately chibi, and that is not a fault to fix.** An earlier pass
moved the rig to realistic thirds and it was a straight downgrade: the sprites came out small,
spindly and interchangeable, and the roster lost its charm. Do not shrink the head.

## The kit (`sprite_base.py`)

Everything is drawn as polygons (`Poly`, `Ell`, `RRect`, `Limb`), because a polygon maps exactly
through `ScaledDraw`, can be shifted (which is how `cel` finds a shape's shadow side), and has one
continuous outline — a limb built as a polygon plus a separate round cap had a line drawn across
its tip.

| Helper | What it is for |
|---|---|
| `cel(draw, shape, color, sh=, hi=, regions=)` | fill flat, lay the shadow band (`sh` is how far the lit face shifts toward the light), optional highlight rim, extra `(shape, colour)` regions clipped to the shape, then the ink line on top |
| `blob(draw, shapes, color)` | the union of several shapes as **one** cel-shaded mass with one outline — beards, clouds, fur ruffs, foliage, smoke. Drawing overlapping ellipses one by one inks a line across every seam |
| `bez(p0, p1, p2, n)` | points along a curve from `p0` to `p2` pulled toward `p1`, for an edge that has to bend where an arc won't do — a widow's peak, a collar's wing, a grin |
| `draw.sub()` / `draw.merge(layer, clip=)` | a transparent layer in the same coordinate space; drawing `fill=CLEAR` on it erases. How hoods cut their face opening and how `cel` cuts a shadow band |
| `shade(c)`, `lit(c)`, `ink(c)`, `mix(a, b, t)` | one cel step darker (hue-shifted toward violet, not toward mud), one step lighter, the line colour, a blend |
| `Fixed(r, g, b)` | an outline colour used exactly as given, never re-inked from the fill |
| `rig` + `rig_legs` / `rig_torso` / `rig_robe` / `rig_arms` / `rig_hand` / `rig_head` / `rig_hair` / `rig_hood` / `rig_cape` / `rig_belt` | the humanoid, see below |
| `head_skull`, `head_face`, `face_anchor` | a head without the standard face, a face without the skull, and where the eyes sit — for masks, visors, beards and eyepatches |
| `blade`, `spear`, `bow`, `round_shield`, `gem`, `xform` | gear authored once in a local frame (+x along the object from the grip) and placed with `xform(pts, x, y, angle, scale, flip)` |
| `flame`, `cloud`, `crystal`, `bolt`, `leaf`, `sparkle`, `star` | effects in flat cel tones |
| `creature_setup`, `eye_pair`, `quad_legs`, `draw_paw`, `rot_pts` | the non-humanoids: frame layout, a pair of eyes (far one narrowed in profile), a quadruped's trot |

A humanoid is `rig(...)` plus the rig calls, with its costume hung off the anchors
(`shoulder(side)`, `hand(side)`, `hand_at(r, side, reach, lift, out)`, `foot(side)`, `hip(side)`,
`sh_y`, `waist_y`, `hip_y`, `head_cy`, `hx`) in between. `build` scales the body's width — 1.3 is a
bruiser, 0.9 a waif — and `head` the skull, for the few whose hat needs headroom. `r.d` is the facing
(-1/0/+1), `r.back` the up view, `r.near(side)` whether a limb is on the camera side, `r.ph` the
stride phase. Draw order changes with the facing, which is what the `layer` arguments are for:

```
rig_hair(layer="back") -> rig_cape(layer="under") -> rig_legs -> rig_arms(layer="far")
  -> rig_torso / rig_robe -> rig_arms(layer="near") -> rig_head(hair=, style=) -> hat
  -> rig_cape(layer="over")
```

The far arm is behind the torso in profile; long hair and capes are behind the body head-on but
over it from behind. Anything a hand holds is drawn after the arm and before `rig_hand`, so the
fingers close over it. Pass `hat=True` to `rig_head`/`rig_hair` when something is worn on the
crown: the gloss band is left off and the brows are drawn so the hat can sit on them.

Things learned the hard way, all of which the gallery caught:

- **Everything around the head is sized to the head.** `rig_head`, `rig_hair` and `rig_hood`
  derive from `r.head_rx`/`head_ry`; anything hand-placed (a helm, a crown, a mask) has to be
  checked against it, or the headgear ends up too small for its own skull.
- **Headroom is 15 units over a bare crown, and none under the feet.** The frame shows y = -6 to
  58 of the space a draw function authors in (`HEADROOM`; *Rendering pipeline* below), the crown
  near y = 9 and the soles on 54. A hat, crest or flame may go up to y = -5.5; what hangs lower
  than y = 57.5 (a sword's tip, a cloud underfoot) is cut off. `sprite_review.py --lint` fails a
  sheet drawn up to either edge. Until 2026-11-10 the frame was y = 0 to 64, nine characters' hats
  and flames were cut flat by its top, fifteen more were drawn hard against it, and the advice
  here was to shrink the skull.
- **Streaming hair must fall.** Hair locks that rise away from the head read as horns or wings at
  77px (the banshee came back horned twice). Start every lock at the head and let it fall, curling
  at the tip.
- **Draw order is load-bearing.** A glow painted *after* the thing it lights swallows it; auras,
  wings, tails and back hair go behind.
- **Value contrast, not just hue.** Three near-identical darks merge into one blob at 77px (the
  gorilla, the first death knight and magma knight were both "dark ball with horns"). Give every
  character one shape nobody else in its category has — the magma knight's helm is a volcano.
- **Claws and fangs authored at 3.5 units land at four screen pixels** and read as a row of shark
  teeth. Keep them ~2.
- **A quadruped seen dead-on has no silhouette.** The DOWN/UP rows of the animals are the animal
  coming at (or leaving) the camera, head biggest, body falling away behind.
- **Similar creatures need different species, not different colours.** The Raptor is a bald eagle
  (white head, brown body), the Hawk a slate-blue falcon with a barred chest, the Griffin a gold
  eagle-lion, the Phoenix a bird made of fire.
- **`rig(bob=)` takes the bob for this frame**, a number, not the per-frame list.
- **The kit's profile ear is a ring, and a ring by an eye reads as an eye.** Beside the
  Cyclops's one eye it was a second one, beside the Jester's wink a monocle: leave the ear off
  in profile, or draw it as a C well back from the face.
- **A lash flick on an eye with no partner reads as a wink.** The Cyclops's eye is drawn by
  hand, with a heavy lid and no flick.
- **White lined in the kit's ink reads as hair.** Snow heaped on the Glacier's head came out a
  white wig, and a band of it round the crown a headband. Snow lies on top, in lumps, lined in a
  pale blue (`_snow`).
- **A palette says things the shapes don't.** The Jester in red and green motley was a
  Christmas elf at 77px whatever its hat did; in red and purple it is a jester.
- **Characters drawn as a pair of halves keep each half on its own side as they turn.** The
  Jester's right half is red head-on (the camera's left), from behind (the camera's right) and
  walking right, where his right side is the near one; a profile drawn the same colour both ways
  swaps colours as he turns round.

From the roster-wide review of 2026-11 (every character looked at in all four facings, close up
and as the game shows it, by `sprite_review.py`):

- **The facing nobody looked at is where a character is unfinished.** Most of what that review
  found was in the UP rows and the profiles: a featureless ball of hair colour over a plain back,
  a prop that only existed head-on (a staff, a wand, a quiver, the Minotaur's axe), a weapon that
  changed hands as its owner turned. Players run up the screen as much as down it. A back wants
  what its owner carries there: the cape's emblem, the quiver, the braid, the pack, the pelt's
  tail, the mask's cord tied at the nape.
- **What a character wears has to read as what it is.** The Berserker's wolf pelt was a grey
  steel dome until it got ears, a snout over his brow, fangs and a tail; the Deathknight's "skull
  visor" was dark sockets on a dark helm, and there was no skull on the sheet; the Banshee's
  streaming hair was a white spider. Name the thing, then check a stranger would name it too, at
  77px.
- **One hue from head to foot is one shape**, whatever the linework inside it says. The Griffin
  was a yellow chick until its head went pale gold and its wings got teal tips; the Centaur's
  hair, horse and bow were three browns; the Yokai's white hair, white robe and white mask were
  one pale mass, and on snow not even that. Give a character two or three areas that differ in
  hue or value, and let the linework be detail.
- **A four-legged body head-on needs depth, not four legs in a row**: chest and forelegs
  forward, haunches behind, narrower and a tone darker (the Centaur was a girl sitting in a box;
  the Sphinx a pharaoh's head on a stool).
- **A staff held straight up in the near hand cuts the face in two in profile.** Lean it, hold it
  out to the side, or carry it across the body.
- **Thin things are all outline.** A shape under about 2.5 units wide inked at `lw=1.0` is mostly
  its own line: pale staffs, gold hems, bolts and ribbons come out dark, and a 1.6-unit trim band
  reads as ink, not as colour. Draw it wider, ink it lighter (`lw` 0.6), or leave the line off.
  The same goes for hairlines inside a shape: the MagmaKnight's lava cracks and a mummy's
  half-unit wrap lines were gone at 77px; fewer, wider marks read.
- **Headroom is for silhouettes.** With fifteen units over a crown, the Reaper's scythe, the
  Chronomancer's clock, the Samurai's kasa, the Druid's antlers and the Chef's toque grew into
  the size that makes them the character's outline.
- **Something that circles a character crosses its face on every stride.** The Yokai's foxfires
  sat on her mouth, the Poltergeist's teacup on its; hold what floats out to the sides and let it
  bob.
- **From behind, the skull under the hair's hem is hair.** `rig_hair` fills it with the hair's
  shadow tone: bare, every haired head seen from behind had a row of pale teeth at its nape,
  which three reviewers reported separately.

## Rendering pipeline (`sprite_base.py`)

Draw functions author in a 64-unit space with the soles on y = 54. They never draw at that size:

```
ScaledDraw onto a 256px canvas  →  Lanczos down to 128  →  finish_frame  →  quantize
   (supersampling)                                          (contour ring)    (255-colour palette)
```

- **The frame is y = -6 to 58 of that space** (`HEADROOM` = 6: `render_frame` hands every draw
  function `oy = HEADROOM`, and everything one draws hangs off `oy`). So in a sheet's frame the
  soles are on `FOOT_LINE`, 60 of its 64 units, and that line is what the game stands on the
  ground: the renderer and the menus place a frame by `Constants.SPRITE_FOOT_LINE`, which has to
  change with `HEADROOM`. It was y = 0 to 64, with ten empty units under the feet and none over
  the head, and the renderer stood the frame's bottom edge on the ground: every character hung
  7.5 units over its own shadow.

- **`ScaledDraw`** proxies `ImageDraw` and multiplies every coordinate, radius and stroke width.
  It also re-inks outlines: any dark outline on a filled shape is replaced with `ink(fill)`, so
  even an old call site passing `OUTLINE` gets a coloured line. Box primitives map `x1` to
  `(x1 + 1) * scale - 1` so a scaled rectangle exactly covers its source pixels; `point` fills a
  whole `scale x scale` block.
- **`finish_frame`** is just `add_contour`: a one-pixel ring outside the silhouette in a darker
  version of the edge it borders, laid *behind* the frame so the anti-aliased edge survives. It
  is what keeps a character readable over both pale sand and dark water. The shading itself is
  authored in the art by `cel`: the previous image-space pass (bevel, rim light, occlusion ramp
  and a fixed grain field) was painterly by design and fought flat colour everywhere.
- Sheets are saved as a **255-colour palette PNG** with alpha in `tRNS`. Palette alphas ≥250 are
  snapped to 255 (the octree splits on alpha too, and would otherwise leave every body pixel at
  252-254). Both loaders expand it back to RGBA: `stbi_load_from_memory(..., 4)` on the GL side,
  `javafx.scene.image.Image` on the UI side.

A sprite is displayed at `PLAYER_DISPLAY_SIZE_PX` (48) times `CAMERA_ZOOM` (1.6) ≈ 77px, where
one authoring unit is about 1.2 screen pixels. **Judge any change at that size**, not at the
128px sheet resolution.
