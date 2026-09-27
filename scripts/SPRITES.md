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
is the same rounded slab. Each cell draws the character at the size the player
sees (`PLAYER_DISPLAY_SIZE_PX` 48 x `CAMERA_ZOOM` 1.6 ~= 77px) and again at 2x,
over the three terrain bands it has to read against. **Run it after any change
here**; judging a sprite at its 128px sheet resolution flatters it, and judging
it on one ground hides contrast faults. `sprite_base.render_sheet(draw_func)`
returns a sheet as an image without writing it, for a scratch preview while
iterating on one character.

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
- **There is no headroom.** The crown sits near y = 9 of the 64-unit frame, so a hat, crest or
  flame drawn above it has about 9 units before the top clips it. A character whose silhouette
  lives above the skull needs a smaller skull (`rig(..., head=0.9)`), not a taller hat.
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

## Rendering pipeline (`sprite_base.py`)

Draw functions author in a 64x64 space with the feet at y = 54. They never draw at that size:

```
ScaledDraw onto a 256px canvas  →  Lanczos down to 128  →  finish_frame  →  quantize
   (supersampling)                                          (contour ring)    (255-colour palette)
```

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
