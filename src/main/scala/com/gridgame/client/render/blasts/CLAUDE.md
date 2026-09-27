# client/render/blasts

`client/render/blasts/`. A blast is what an area attack sets off: an explosion
(`explosionConfig`) where it stopped, a splash where it struck (`aoeOnHit`) or at the end of its
range (`aoeOnMaxRange`), a ground slam (a zero-range projectile with both), or a mine going off.
There used to be two animations for all of them — a fireball and a set of rings — tinted with the
thrower's random player colour (`Player.generateColorFromUUID`), so a Pyromancer's and a Plague
Doctor's could come out the same green. A slam was both at once: every slam in the game, a Wolf's
howl, the Spider's web, Medusa's gaze, went off as a grenade and played the grenade's bang over its
own sound.

- **Picked by who threw it** (`styleOf(characterId, projectileType)`): a table by projectile type
  and a second by (character, type) for a type thrown by characters with nothing in common — the
  same shape as `AbilitySounds`' two tables. Six characters slam a `TREMOR_SLAM` (the Banshee's
  wail, the Shadowfiend's burst, the Nanoswarm's swarm, the Harpy's shriek, the Golem's quake, the
  Beetle's tremor) and four throw a `GRENADE` (the Soldier's frag, the Bombardier's comic bomb, the
  Railgunner's EMP, the Pirate's cannonball). The client records the thrower's character with each
  blast (`GameClient.blast`, through `characterIdOf` when the packet arrives). `BlastStyleTest`
  holds the roster to it: every attack that blasts has a style, and no two characters share one.
- **68 styles, 16 engines.** An engine is a family's anatomy — the fireball every explosion shares,
  a liquid's splat and droplets, a quake's cracks and rubble, electricity's bolts and burnt scar —
  and a style is its thrower's palette and motif: a skull rises out of the Plague Doctor's miasma,
  souls stream in to the Necromancer, the Chronomancer's clock runs backwards, the Wolf's howl has
  a moon. One change to an engine reaches every blast in its family, as with the projectile
  factories and the sound engines.
- **Two layers.** GROUND (the scorch, cracks, webs, glyphs, the ring racing out, and the back half
  of anything standing up out of the ground) is drawn in the ground pass after the traps, so the
  players standing in a blast and the walls in front of it cover it. AIR (the flash, fireballs,
  debris, smoke, the front half of what stands up) is drawn over everything, where all of it used
  to be: a scorch mark lay across the sprites of everyone standing in it.
- **The real footprint.** A blast of radius r reaches r cells in every direction, which on screen
  is an ellipse √2·r half-tiles across (`footprintW`/`footprintH`). The old rings were drawn at r,
  71% of the area that was hit.
- **One blast per event** (`GameClient.getBlasts`, keyed by projectile and server tick,
  `blastKey`): the HIT on whoever a splash struck and the HITs on everyone it caught arrive in one
  tick, and are one blast; a piercing splash that goes off again later is another. A splash at the
  end of a range isn't drawn where the terrain stopped the shot first, since the server sets none
  off there.
- **Sound**: `explosion.wav` plays only for an explosion that deals damage. A slam's, the web's and
  the ink's explosion (0/0 damage) exists only to be drawn, and their own sound played when they
  were cast.
- **Light and shudder**: each style has a light colour and strength (none for mud and ink) and a
  heaviness, which drives the post-processor's radial warp and a small camera shake, once, when a
  heavy blast goes off within 6 cells of us.

**Cost.** Everything is a function of the blast's progress and seed: no state, no allocation.
Measured back to back against the old animations (`render_bench --blasts=14`, the same stream of
events, the Meadow at High on a 3200x1800 framebuffer): blasts shaded 3.1 million fragments a frame
against 5.9, frame building fell from 4.25 to 3.9 ms and GPU time from 15.2 to 14.5 ms, allocation
stayed at 3.1 KB a frame with no GC; at Low, 0.4 million fragments against 0.9. In the ordinary busy
scene (150 projectiles) the blasts went from 3.3 to 2.2 million. The old explosion's 800x600 flash
rectangle alone was more fragments than a dozen of the new blasts. A crowd of blasts draws fewer of
its small things (`detail`, from 1 down to 0.5, and 0.65 at Low); what makes each recognisable —
its shape, its glyph — is always drawn whole.

Rules learned making them, in the spirit of the projectile ones:
- **What an explosion throws is drawn before its fireball**, so it comes out of it. Drawn after,
  the chips were grey specks painted across the flame.
- **A fireball comes apart as it cools**: its lobes drift apart, rise and shrink. Held together to
  the end, it went down its heat ramp as one dark red ball.
- **Smoke is lit a tone, not highlighted.** Lit hard from one side, every puff was a glass marble
  and a cluster of them a pile of pebbles. Dust rolling along the ground is big, flat and barely
  inked.
- **A splat's outline is a sum of waves**, not a random radius per point, which came out a jagged
  polygon. A blob's gloss is a small offset glint: a lit core in its middle made every glob of lava
  a coin and every glob of sludge an eye.
- **Anything standing on the ground is drawn by the layer it stands in** (`mine(y)`), far to near.
- **A cross lying on the ground runs along the screen's axes**; along the ground's own, it is an X.
- **A curl belongs at the tip** (`tendril` weights it that way): bent more than about a radian and
  a half, a vine or a tentacle bows over into a croquet hoop.

**Judge a change in the gallery**: `projectile_gallery -- out --blasts` (or `--blasts=wail,web`,
by style name) writes one row per style at five moments of its life, at its real size over the
three grounds, with a stand-in player in the middle so the two layers can be told apart;
`blasts_NN.txt` names each row and who sets it off. Then look at it in a match: `render_bench
--blasts --screenshot=out.png`, on a bright map and a dark one.

To give an attack a blast: add a style constant and its `style(...)` line (name, duration, light,
heaviness), draw it in its family's engine (or add an engine), and map it with `byType`, or with
`only` for one character's take on a shared type. `BlastStyleTest` fails until every attack that
blasts has a style of its own.

## The files

`GLBlastRenderers` is the facade: the styles (`S_*`) and their table (name, duration, light,
heaviness), who throws what (`byType`, `only`, `styleOf`, `trapStyle`), the footprint, and `draw`,
which sends each style to its engine. `BlastKit` is the blast being drawn (where, how big, how far
through its life, its seed and layer), its timing arithmetic and hashes; `Pieces` the building
blocks the engines share (flashes, rings, scorches, cracks, debris, smoke, flames, spikes,
tendrils, bolts, motes, splats). Each engine is a file: `Explosions`, `Fire`, `Liquids`, `Water`,
`Electric`, `Earth`, `Ice`, `Nature`, `Dark`, `Inward`, `Holy`, `Sonic`, `Nanites`, `Stone`. The
frame's blasts are gathered and drawn by `BlastPainter` (client/render/world).

## Design decision

- **A blast is its thrower's** — picked by who threw it as well as what (see *Blasts*), drawn in a
  layer on the ground under the players and a layer over them, at the footprint it really hits.
  Every blast was one of two animations in the player's random colour, and a slam was both.
