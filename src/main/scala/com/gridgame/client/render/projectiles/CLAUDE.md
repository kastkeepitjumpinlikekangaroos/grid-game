# client/render/projectiles

How every projectile type is drawn. `GLProjectileRenderers` is the facade: the registry mapping each
type to a renderer, the lookup, the anchor for tethers, and the drawing of a projectile flying over
the terrain or sinking into what stopped it. The renderers themselves are in a file per family:

| File | Holds |
|---|---|
| `ProjectileKit` | what every renderer shares: the maths, `fadeLine`, glows, trails, ink |
| `Silhouettes` | the local-frame `Part` system for objects with a real-world shape |
| `Orbs` | `energyBolt` and its `ORB_*` styles |
| `Blades`, `Shafts`, `Lobbed`, `Chains` | thrown weapons, point-first shafts, lobbed objects, restraints |
| `AoeRings`, `Waves` | ground blasts, crescent sweeps |
| `Missiles`, `Bolts`, `Beams` | bullets, fists, rockets; laser bolts, rail slugs, siphons, the gorgon eye; beam-like one-offs |
| `Tethers`, `Grabs` | pulls tied to their thrower; grabs and hands |
| `Electricity` | lightning and thunder |
| `Heavies`, `Undead`, `Bites` | boulders and the heavy one-offs; the undead's; jaws, fangs, talons, swarms |

The renderer (client/render, `ProjectilePainter`) calls in with each projectile's hitbox in screen
space.

All 176 projectile types are registered in `GLProjectileRenderers.registry` (`Map[Byte, Renderer]`, flattened into `_rendererLUT` for O(1) lookup with no `Option` allocation). Projectiles use **standard alpha blending** for solid, visible shapes — the bloom post-processor provides natural glow on bright elements.

`Renderer` is a single-method trait, `apply(proj, sx, sy, sb, tick)`, not a
`(Projectile, Float, Float, ShapeBatch, Int) => Unit`: `scala.Function5` isn't specialized,
so calling one boxed both coordinates and the tick — three allocations per projectile per
frame. Factory lambdas (`(proj, sx, sy, sb, tick) => …` returned as a `Renderer`) convert to
it directly; a method in the registry goes through `asRenderer(drawX)`, not `(drawX _)`.

## Silhouettes: how a projectile says whose ability it is

Anything with a recognisable real-world shape — an axe, a katana, a femur, a playing
card, a shovel, a grenade, a spear, an arrow — is authored **once in a local frame**
(+x along the object, +y across it, both roughly within [-1.3, 1.3]) as an array of
convex `Part`s, then stamped through one of two transforms:

| Transform | Used by | What it does |
|---|---|---|
| `drawParts` / `blitPart` | tumbling objects (`bladeSpinner`, `lobbed`) | rotate by the spin angle, squash y by `ISO_Y` into the ground plane, scale |
| `drawPartsDir` / `blitPartDir` | objects flying point-first (`flyingShaft`, `fistProj`) | pure screen rotation onto the travel vector, narrowing only the cross-axis by `FLAT_Y` |

The direction-aligned transform deliberately does **not** apply `ISO_Y`: the travel
vector handed to a renderer is already in screen space, and squashing it again shortens a
spear thrown "north" to two thirds of one thrown "east".

`Part`s must be convex — `ShapeBatch.fillPolygon` fan-triangulates from vertex 0 — so a
curved blade is split into two convex spans rather than described in one loop. Each part
carries a material colour plus a `tint` weight toward the projectile's registered colour,
so steel stays steel while the energy parts take the character's palette. Round details a
polygon list cannot express (bone knobs, card pips, a cursed blade's aura) live in
`weaponDetail`.

This exists because the previous `spinner` built its outline from a polar radius per
vertex, which can only ever describe a star: an axe, a katana, a femur and a playing card
all came out as the same spinning lens. A local-frame silhouette can carry a haft at one
end and a head at the other, so it still reads as an axe at every spin angle.

## Heads sit on the hitbox

`(sx, sy)` is where the projectile's hitbox is. Draw the thing that hits **there**, and let
anything elongated — a trail, a tether, a wake, the bolt a lightning strike just drew —
trail *behind* it through `fadeLine`, which narrows and fades to nothing instead of ending
in a hard edge.

Never draw a body out *ahead* of `(sx, sy)`. Seven renderers used to (via a `beamTip`
helper, since removed): beams, the charge shot, the tentacle, the tethers, lightning, the
rocket and the shark jaw each ran a line `worldLen` world units forward and capped it with
a disc. The talon and the blood fang were the same bug in another shape — claws and fangs
running 24-36px forward from a knuckle or gum at the hitbox, so the points that read as the
projectile arrived a third of a tile before the bite did. They now close *on* the hitbox:
the foot/jaw sits a claw's length behind and the points land at `(sx, sy)`. That produced
two problems:
- **It looked wrong.** A stroked line with a ball on the end is the silhouette of a snake,
  not of an ability, and the whip and vine styles wiggled along their length so they
  literally slithered.
- **It lied about the hitbox.** The disc that read as the projectile's head arrived 100–150px
  before the damage did.

## Terrain: stopped by it, or flying over it

**Collision cells match the drawn tiles.** The renderer draws tile `(c, r)` centred on world
`(c, r)`, so it covers `[c − 0.5, c + 0.5)`; `Projectile.getCellX/Y` is `floor(x + 0.5)` to
match. It used to truncate (`x.toInt`), which put every wall half a tile down-screen of its
sprite: shots heading toward the camera sank halfway into wall blocks (and, bucketed into the
wall's own draw slot, were drawn *over* the block face) before vanishing, shots heading away
stopped short, and projectiles flew half a tile off the bottom edges of the map.
`Projectile.ricochet` snaps to the faces at `c ± 0.5` for the same reason.
`ProjectileTerrainTest` pins all of this.

**A stopped projectile sinks into what it hit.** The server still removes a projectile on the
first half-cell sub-step that lands in a blocking cell, so the DESPAWN position can be up to
half a cell *inside* the wall. On DESPAWN the client runs `TerrainImpact.resolve`, which walks
back along the heading to the wall face, stops the projectile there, and keeps it in
`GameClient.fadingProjectiles` for `TerrainImpact.FADE_MS` (260 ms). `EntityCollector` puts it
in the depth pass, so walls in front still cover it, and it is dropped once expired.
`GLProjectileRenderers.drawAbsorbed` draws it shrinking toward the impact point while it fades,
with a contact flash, a ring across the face and chips kicked back in the colour of the tile it
struck (`Tile.color`: grey off stone, green off a tree, dust at the map edge). Explosives still
explode, now centred on the face. A despawn at the end of range in open ground fades the same
way, without the puff.

The fade and shrink come from `ShapeBatch.setAlphaMultiplier` / `setScaleAbout`, applied in
`vertex`, so any renderer can be faded or shrunk without knowing it. `begin` resets them.

**Wall-passers fly.** Types with `passesThroughWalls` are drawn `flyLift` above their ground
point (20 px with a slow bob). Their bodies go in `drawFlyingProjectiles`, after the depth pass,
so no wall block can slice through them. Their shadow goes down *in* the depth pass, on the
surface below: `surfaceLift` raises it onto the top face of an elevated tile, so the shadow
climbs over the wall the projectile clears. Their particle trails spawn at the same height.

## Orbs: a lit sphere, a comet tail, and a shape outside both

Nearly thirty characters' primaries are an `energyBolt`, so the orb is the thing seen most in
a match. Every style shares one anatomy: `cometTail` (a band of light as wide as the orb where it
leaves it, narrowing to a wisp — and straight, since a tail swung side to side behind a round
head is a tadpole), a modest glow, `orbBody` (an inked rim, the colour at full strength at the
edge lightening to a hot core that breathes: it glows from inside), and `shedMotes` off its back.
A style is whatever it adds *outside* that sphere.

What it replaced, and why, since each was tried: the whole orb pulsed between 100% and 30% alpha
three times a second (a strobe, and on pale ground its dim frames weren't there); a dozen white
specks orbited *inside* the body and read as dirt on a flat disc; `orbBody`'s first pass, lit
from outside with a hard specular spot and a rim light, made every orb a glass marble (and put a
smile under the soul bolt's eye sockets); and a halo 3.2 times its size tinted the ground for no
read, about 0.7 million fragments an orb at 4K. Measured back to back against the old renderer
(`render_bench --types=`, 150 of them at 4480x2404), the orbs' depth pass went from 106 to 73
million fragments and frame building from 8.5 to 7.0 ms; the tethered grabs cost what the
free-flying ones did, and the whole roster's mix is 5% fewer fragments at the same CPU.

## Pulls are tied to whoever is pulling (tethers)

A grab that flies free reads as a glove somebody threw — the Bear's and Gorilla's was a brown
mitten on an empty box-shaped cuff, and seven characters shared a knot with three sausage fingers.
Every pull is drawn tied back to its thrower: the Kraken's and the Spaceman's tentacle (suckers down its underside,
a tip curling round what it caught), the Druid's, Thornweaver's and Treant's vine (thorns raked
back, leaves, a tendril), a leash of spirit to the Bear's and Gorilla's paw print and the
Griffin's talon, a spectral chain to the Death Knight's sickle hook, a rope to the Gladiator's
grapple and the Chef's meat hook. `ProjectilePainter.anchorToThrower` says where the thrower stands
(`setAnchor`, looked up only for `wantsAnchor` types: our camera position, or anyone else's
smoothed one), and without one (they died, or a dev tool has no players) the tether runs back to
`Projectile.originX/Y`. `layTether` lays it out — bowed, swaying slowly, and cut off at the
projectile's range so a thrower who has blinked away doesn't drag it across the screen —
`shapeTether` gives it a width per point, `strokeTether` strokes it in layers with
`strokePolylineVar`. The head is still at the hitbox; the tether only trails it. The Grasping
Dead isn't a pull and isn't tethered: skeletal hands claw up out of graves along its path, on
points fixed to the ground, the one at the head reaching and the ones behind sinking back.

## Electricity is fixed to the ground

A bolt's channel stays where it was drawn. Its kinks are keyed on points fixed to the ground
along the flight line (`along = x·ux + y·uy`), so as the head flies on it carves the channel
and leaves it standing behind it, fading. A kink every other node alternates sides by an uneven
amount (`kink`: the lightning-bolt zigzag, never more than ~55° off the line, so no mitre
folds), the nodes between are knocked aside by a crackle re-struck twenty times a second, forks
flash off it forward and out, and the head is a ball of light with arcs crackling all round it.
Thunder Strike is a storm cloud rolling over the target spot and striking it fifteen times a
second, onto the hitbox.

The bolt it replaced was a zigzag built relative to the head and re-rolled whole every three
frames, with kinks up to 23px either side of segments 7px long: a sawtooth stick carried along
by the head, jumping to a new shape twenty times a second, every hairpin mitred into a needle,
three prongs out ahead of the head like the legs of a bug. Two tries on the way that don't work:
a smooth meander is a worm, and kinks small beside the stroke's width are a noodle.

**15 pattern factories** (configurable colour + size, most also taking a `kind`):
- `energyBolt(r, g, b, size, style)` — glowing orb (see *Orbs* above). `style` picks what
  stands outside the sphere, named by the `ORB_*` constants: `ORB_PLAIN` (the bare orb),
  `ORB_FIRE` (a flame teardrop with tongues peeling off its edges), `ORB_RUNE` (a magic circle
  in the ground plane, the orb inside it), `ORB_ORBIT` (a tilted, precessing ring the orb passes
  through, with motes riding it), `ORB_HEART` (a heart beating lub-dub — charms),
  `ORB_ASTRAL` (a five-point star in an orbit of stars — the Astronomer), `ORB_SPIRIT` (a thin
  ghostly orb with wisps peeling off it), `ORB_TOXIC` (bubbles bursting on it, drops falling),
  `ORB_SWARM` (nanites on orbits of their own round a small core), `ORB_SAND` (arms of grit
  whirling out past its rim, grains shed), `ORB_MUD` (a lumpy wet glob flinging drops, with no
  light at all) and `ORB_SHOCK` (arcs crackling off it). The outer shape is what distinguishes
  bolts; inner detail is invisible at the size a projectile is actually displayed. **Whatever
  the style draws has to stand outside the body** — a feature inside the 0.90 x 0.68 sz ellipse
  is painted over and the bolt comes out plain. That is what happened to the old rune ring (at
  0.88 x 0.64 sz) and cloud lobes (the body's own colour at half alpha, behind an inked
  ellipse): four bolts each, all reading as featureless eggs.
- `laserBolt(kind, …)` — blaster bolt: a short capsule, round at the hitbox and drawn to a
  point behind, with a dissolving afterglow. Kinds: plain, prismatic fringes (Photon),
  rings of force pulsing off the head (Cyclops).
- `railSlug(…)` — a dense dart shedding electromagnetic coil rings that widen and fade
  behind it (Railgunner).
- `siphonVortex(kind, …)` — drain abilities as a travelling whirlpool, motes spiralling
  *into* a dark core: blood sheds drips, life drain beats a heart, soul drain stares back.
- `tether(kind, …)` — a pull tied back to its thrower (see *Pulls are tied to whoever is
  pulling*): tentacle, vine, spirit paw, spirit talon, death hook on a chain.
- `gorgonEye(petrify)` — Medusa's gaze as a blinking almond eye with a slit pupil, ringed
  by crumbling stone.
- `bladeSpinner(kind, …)` — thrown weapon tumbling end over end (axe, bone axe, katana,
  chef's knife, sword, femur, cursed blade, playing card). Sells the rotation with a
  swept arc band and silhouette ghosts rather than by smearing the shape.
- `flyingShaft(kind, …)` — shaft flying point-first (spear, arrow, poison arrow, blowdart,
  thorn, ice spike, void lance).
- `shuriken(r, g, b, size)` — four-bladed throwing star, one swept blade stamped at exact
  quarter turns, with a rimmed centre hole. Spin reads from a band swept across the blade
  tips; nothing is drawn on a radius out from the hub.
- `lobbed(kind, …)` — object on an arc (bomb, flask, shovel, hammer, horn, spiked mine,
  ice chunk, mud glob) with landing shadow, target ring and bounce.
- `aoeRing(kind, …)` — ground blast as filled shockwave bands over a darkened scorch.
  `kind` picks what it throws off: quake rubble, water, spores, flame, pressure rings,
  inward-falling motes, roots.
- `wave(kind, …)` — crescent sweep built from a real arc band whose centre is solved in
  the ellipse's own parameter space so it stays square to the travel direction at every
  heading. Kinds: wind, sand, sonic, flame, acid, impact, water.
- `chainProj(kind, …)` — thrown restraint: a grappling hook (or, `CHN_HOOK`, a single meat
  hook) on a twisted rope running all the way back to the thrower's hand; a tumbling manacle
  trailing swinging links; a loop of links spinning around a padlock.
- `bulletProj`, `fistProj` — small fast round; gauntleted punch.

**32 specialized `draw*` renderers** for one-off projectiles: lightning (`lightningBolt(r, g,
b, heavy)` — colour is a parameter so a storm reads yellow and a tesla coil reads arc-cyan;
`heavy` is chain lightning's thicker bolt), thunder strike, grasping dead, frost comet (ice
beam), bandage wad, tongue lash, boulder (faceted tumbling hull), shark jaw, bat swarm, shadow
bolt, inferno blast, geyser, wail, raise dead, and more. The fireball is an `ORB_FIRE` orb.

**Things to watch when editing these files:**
- **Kind constants are `final val`s** (compile-time constants, in the family object that uses
  them). The registry is built while `GLProjectileRenderers` initialises; a plain `val` kind read
  before its object had initialised would read `0`, and silently render the wrong kind.
- **`fillPolygon` fan-triangulates from vertex 0, so it fills convex outlines only** — and
  the failure is silent, because the `strokePolygon` beside it still traces the true shape.
  A star fills as a lopsided blob with its notches bridged; a crescent fills its own hollow
  and reads as a slab. Anything built as a radius per vertex (star, sunburst, faceted hull)
  goes through `fillFan` with the centre those radii were measured from; a band built as an
  outer run plus the reversed inner run goes through `fillRibbon`. Run any client binary
  with **`GRIDGAME_POLYCHECK=1`** and every non-convex call site prints once — the
  projectile gallery covers the whole roster in a single pass. This caught eight renderers
  at once (the shuriken, both crescent blades, the holy star, two faceted hulls, the soul
  wisp's tail) plus three authored `Part` silhouettes.
- **A stroke of uniform width is a bar, whatever colour it is.** `strokeLineSoft` at
  `sz * 0.34` gave `ORB_FIRE` eight flat yellow paddles stuck round a disc — a cog, not a
  flame. Anything meant to taper is either a triangle (`fillPolygon`; a triangle is always
  convex) or a `fadeLine`, which narrows *and* fades to nothing.
- `fillArcBand` ramps alpha **along the sweep**, not radially. A radial falloff has to be
  built by nesting bands at constant alpha; using the ramp for it leaves one horn of a
  crescent bright and the other invisible.
- **A bend in a translucent line needs a joined stroke.** A zigzag or a curve built from
  `strokeLine` quads overlaps itself on the outside of every bend, and with any alpha below 1
  each overlap blends twice: the lightning bolts came out as chains of translucent rectangles.
  `strokePolyline` / `strokePolylineTapered` mitre the joins (see *Strokes are one band*).
- **Ink the whole silhouette, not only its leading edge.** A pale shape reads on sand and snow
  only by its outline. The wave crescents were inked along the front alone, with a body at
  35-45% alpha, and all eleven waves faded into the ground behind their front; the audit had
  Acid Spray at 4 strong pixels per 1000 on sand, and 32 once inked round and filled denser.
- **Never flicker a whole projectile toward zero.** `sin(phase * 8)` lands on an unrelated value
  every frame, so a flicker between 0 and 1 is a strobe, and on a dim frame on pale ground the
  shot isn't there. Flicker the core; keep the body up. A slow pulse is no better: every orb,
  the fireball and the inferno blast throbbed down to 20-30% three times a second.
- **In a match the client flies its projectiles** (`NetProjectile`, see *Flown between the
  server's ticks*): `getX`/`getY` is where one is drawn this frame and `getDistanceTraveled` how
  far it has come as the server counts it, as in the gallery, the bench and the audit, which fly
  theirs with `moveStep`. It used to be moved only to where the server said, so the distance was
  0 in a match: the rope measured itself by it, and in a real match no rope was ever drawn. Where
  it started is `Projectile.originX/Y`, the rope's end when nobody is left to hold it.
- A block literal on the line after an expression is parsed as an *argument* to it
  (`val n = 9` followed by `{ … }` becomes `9 { … }`). Use a plain `var`/`while` at
  statement level rather than a `{ … }` wrapper.

To add a new projectile renderer:
1. Add an entry to the `registry` map in `GLProjectileRenderers` (`asRenderer(drawX)` for a
   `draw*` method, the factory call as it is for a pattern), in its family's section
2. Either use a pattern factory (`energyBolt(r, g, b, size, style)`, `bladeSpinner(kind, …)`,
   …), add a `kind`/`Part` array if the object has its own silhouette (Silhouettes), or write a
   specialized `draw*` method in the family file it belongs with
3. The renderer receives screen-space coordinates (sx, sy) already transformed from world space.
   That point is the hitbox: draw the head there and trail anything elongated behind it
4. Check it in the gallery (below) — judge at the size the player sees, over all three
   terrain bands — and then on every map's real ground with the readability audit (below the
   gallery), which is where a pale shape on snow or sand shows up

## Design decisions

- **A projectile's silhouette carries its identity, not its palette** — the pattern
  factories used to describe shapes in polar form (a radius per vertex) or as a stroked
  line from the caster, so a whole family came out identical: every melee weapon was the
  same spinning lens, every thrown object the same grey disc, every wave the same
  triangle, and a spear or arrow was a ~190px hairline drawn `worldLen` world units long.
  Recognisable objects are now authored as convex `Part` silhouettes in a local frame and
  stamped per frame (see *Projectile Rendering System*), and the family factories take a
  `kind`. Colour differentiates within a family; shape differentiates between them.
- **A pull is tied to whoever is pulling** — tentacles, vines, leashes, chains and ropes run from
  the hitbox back to the thrower (see *Pulls are tied to whoever is pulling*). A grab flying free
  was a glove somebody threw.
- **A bolt's channel is fixed to the ground** — its kinks stay where they were drawn as the head
  flies on (see *Electricity is fixed to the ground*); built relative to the head and re-rolled
  every few frames, it was a sawtooth stick jumping about.
- **An orb glows from inside and streams a comet tail** — the colour strongest at its rim,
  lightening to a hot core, and a tail as wide as it is (see *Orbs*). Lit from outside it was a
  glass marble; pulsing its alpha, a strobe.
- **A projectile's head sits on its hitbox** — nothing is drawn ahead of `(sx, sy)`; trails,
  tethers and wakes stream out behind it and fade (`fadeLine`). Beams, tethers, lightning,
  the rocket and the jaw used to run a line several tiles ahead of the hitbox to a disc,
  which both looked like a snake and showed the head arriving 100–150px before the damage.
  The talon and the blood fang ran their claws and fangs forward the same way; they now
  close on the hitbox with the foot and the gum trailing behind.
- **An energy bolt's identity is whatever stands outside the orb** — every `energyBolt`
  draws the same 0.90 x 0.68 sz ellipse, so a style's shape only exists where it clears
  that ellipse, and only if it is not a uniform-width stroke. Three of the five styles
  failed one of those tests at once: the rune ring was drawn *inside* the body, the cloud's
  lobes were the body's own colour behind an inked ellipse, and the fire tongues were
  9px-wide soft strokes. Eleven bolts across nine characters came out as plain orbs or as
  a disc with paddles. The fourth, the soul wisp, failed differently — a tapering tail two
  and a half diameters long, swung side to side by a sine, which with a round head in front
  of it is a tadpole swimming, and which five characters' primaries all shared. Those bolts
  now sit inside a tilted ring they pass through (`ORB_ORBIT`), which reads as an orbit
  precisely *because* the orb occludes half of it.
- **Standard alpha blending for projectiles** — additive blending (`GL_SRC_ALPHA, GL_ONE`) makes projectiles invisible on bright terrain and removes all visual distinction. Standard blending with high alpha (0.7-0.95) produces solid, visible, distinct shapes. Bloom post-processor handles glow naturally.

## Projectile gallery (dev tool)

```bash
bazel run //src/main/scala/com/gridgame/client:projectile_gallery -- out/dir
bazel run //src/main/scala/com/gridgame/client:projectile_gallery -- out/dir --bench
bazel run //src/main/scala/com/gridgame/client:projectile_gallery -- out/dir --focus=LIGHTNING,TENTACLE
```

`--focus=` writes one `focus_<NAME>.png` per type instead: four headings across (right, left,
up, down on screen) and four moments down, the projectile further along each row. It is the
loop for anything drawn along or behind the flight path — a tail, a tether, a bolt's channel —
which the contact sheets only ever show flying one way.

Renders every registered projectile type into contact-sheet PNGs (10 pages x 4 animation
ticks) plus an `index.txt` naming each cell. It also writes `impacts_NN.png` (projectiles sinking
into a wall block across the fade) and `flyers_00.png` (wall-passers crossing one). Each cell draws one projectile over real
isometric tiles banded dark stone / grass / sand, at `CAMERA_ZOOM`, with a 48-unit player
footprint box for scale — a projectile that reads on one ground can disappear on another,
and judging any of this at 1:1 flatters it by a third. Each cell advances the projectile to
**35% of its own effective range**, not a fixed number of steps: at six steps every
short-range type (a talon, a fang, a punch are all `maxRange` 3-4) was past its end of range
and drawn in its dissipation, at 30% alpha and 130% scale, which is not a state a player
ever aims at. This is the loop to use for any
projectile art change; it needs no server, no login and no match. Its target compiles only the
ten GL files it needs, so it keeps building while unrelated client code is mid-edit.

`--bench` times a screenful of projectiles against a ground-only baseline, so an art
change can be checked against the frame budget instead of guessed at. Measured on this
machine after the orbital-bolt pass: 16 projectiles cost **0.11 ms/frame** over a 0.68 ms
ground+post baseline — about 7us each, against a 16.7 ms budget. (The same 16 cost 0.157 ms
before that pass: an orbit ring drawn as two arcs is cheaper than the swinging tail and the
soft-stroke cloud it replaced.)

The readability audit (every projectile over every map's real ground) is the renderer's:
client/render/CLAUDE.md.
