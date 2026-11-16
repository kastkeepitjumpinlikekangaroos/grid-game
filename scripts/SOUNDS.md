# Sound effects and music

All audio is procedurally synthesized (numpy oscillators/noise, no samples or external
audio libraries — zero licensing concerns) into `sounds/*.wav`, mirroring how sprites are
generated.

```bash
# Requires numpy: pip install numpy
python3 scripts/generate_sounds.py                  # -> sounds/*.wav (359 files, about ten seconds)
python3 scripts/generate_sounds.py wizard blink     # only the sounds whose names match
python3 scripts/generate_sounds.py --out /tmp/snd   # somewhere else, to judge before replacing

python3 scripts/sound_audition.py /tmp/audition --before HEAD   # LISTEN: a page, a button an attack
python3 scripts/sound_roster.py                     # what each character's three attacks play
python3 scripts/sound_audit.py --check              # does anything ring like struck metal, or clink?
python3 scripts/sound_gallery.py /tmp/sndgallery    # every sound as a spectrogram (needs Pillow)
```

| File | What is in it |
|---|---|
| `generate_sounds.py` | the driver: makes every registered sound (in parallel; the output is the same whatever `--jobs` is), the music, and cross-checks itself against the game |
| `sound_kit.py` | what a sound is built from, the master chain, levelling, and the registry (`sound(name, generator, level, class, master)`) |
| `sound_engines.py` | the family engines: `bolt`, `swing`, `thrown`, `shaft`, `lobbed`, `slam`, `burst`, `wavefront`, `gun`, `chain`, `radiance`, `elec`, `machine`, `blaster`, `explosion` |
| `sound_attacks.py` | every character's attacks that throw something, grouped as the roster is |
| `sound_casts.py` | every character's abilities that throw nothing: dashes, blinks, phases, barriers, traps set; R |
| `sound_events.py` | spawn, death, the three hits, the detonations, a barrier's block, traps going off |
| `sound_music.py` | the two loops |

**Every character's three attacks have a sound of their own** (332 of the files), as each has a
look of its own; which the game plays for what is `AbilitySounds.scala`
(src/main/scala/com/gridgame/client/audio/CLAUDE.md). The generator and that file must agree, both
ways: it refuses to run if the game names a sound it doesn't make, or it makes one nothing plays.

## What a sound is made of

`sound_kit.py` is a sound-design toolkit, not tone+noise bursts. Alongside `fm`, a time-varying
resonant state-variable filter (`svf`), fast static resonant filters (`res_lp`/`res_bp`, and
`reson`, the causal one), causal RBJ biquads (`eq`, used wherever a transient or the master EQ is
involved — the zero-phase FFT filters pre-ring, which puts a ghost tick before a click),
`saturate`, `bitcrush`, convolution `reverb`, `delay_fx` and `chorus`:

- **What things are made of** — the layers a projectile is built from when it is a thing
  rather than a note, all noise-excited or harmonic: `grains` (a Poisson cloud of
  millisecond noise grains: fire pops, ice fracturing, sparks, gravel), `bubbles` (sines
  rising as they close — the cue for liquid; many, small and quiet, or they are a cartoon),
  `sizzle` (frying: acid, lava, a fuse), `squelch` (mud, flesh), `breath` (a whisper: formants
  over noise), `choir` (voices on a chord), `pluck` (Karplus-Strong: a string, every partial
  harmonic), `rattle` (chain links: dozens of few-millisecond clinks), `rip` (tearing), `crack`
  (a whip's N-wave), `buzz` (a swarm), `scrape` (a blade's gliding "shing"), `creak` (wood under
  strain), `heartbeat`, `whump` (a pressure bloom) and `flange` (a sweeping comb: energy
  streaking).
- **How things move** (the third pass: *Gestures*, below) — `glide` (a frequency curve straight
  in log frequency), `nlfo` (an irregular wobble), `arc` (the loudness of something passing),
  `whoosh` (air shoved aside by a pass-by: band and loudness peak together), `varispeed` /
  `leaving` (the whole of a sound falling as it goes away), `halo` (pitched air: noise made to
  sing on a chord — the only tone a magic gets), `supersaw`/`blsaw` (a stack of voices, never one
  oscillator, and no aliasing), `comb` (a resonance that glides: a blaster's ring, a servo, data),
  `shimmer` (glitter: a sheen of bright air with two or three high notes sung in it — continuous,
  nothing in it begins or ends), `steel_song` (a blade singing: the smooth 'shiiing' of an edge
  moving fast), `chime_run` (a few soft notes, for the two or three abilities that are a tune),
  `snap` (the first few milliseconds), `weight` (the low push of something leaving or landing).
- **`modal` + `MATERIALS`** — a struck object rings at frequency ratios fixed by its
  shape, each partial decaying at its own rate. For things that really are struck — a trap's
  jaws, a slam's contact — and through `strike` (= `modal` + the bright edge of the contact) it
  used to be under nearly everything, which is where the tin cans came from (the fourth rule
  below).
- **Throats** — `cry`/`glottal`/`tract` is source-filter voice synthesis: a clean glottal pulse
  train through formants that move, which is a vowel, sung. It is right for a howl and a wail, and
  only in company (`voices`: several throats a little apart, since one synthetic voice alone is a
  machine saying "ah"). Everything that growls, roars, snarls or screeches is `roar` and `screech`
  (`throat`, `pulses`): an animal's roar is mostly noise — folds slapping irregularly, every other
  one weaker, turbulence through them — in a long loose throat, driven hard. There are no human
  effort grunts: a synthesized "uh" is nobody's idea of effort, and a thrown spear is an arm and a
  cloak.

Attacks are assembled by the family engines (`sound_engines.py`) plus what each character adds.
Keeping families as engines rather than 300 bespoke functions is what lets one quality change
reach the whole roster, the same reason every sprite goes through
`sprite_base.generate_character`. `bolt` is a table of voices, one per element (`_b_fire`,
`_b_ice`, `_b_poison`, `_b_soul`, …). There is no `beam` engine: of the ten things it voiced as a
held FM-and-saw note only the railgun is anything like a beam on screen, so each is what the
renderer draws — a blaster bolt, a comet of frost, a travelling whirlpool, a vine, Medusa's gaze,
a wad of bandages, a void lance, a searing eye.

## Gestures: why the set read as "silly and basic", and what a sound is now

Two passes had made every sound honest about what it was made of and none ring like a struck can,
and the set still read as cheap. Three faults, none of which those passes looked for:

- **Bare oscillators doing the gesture.** A sine sliding down two octaves under every bolt (a
  kick drum), FM zips for a teleport (a cartoon), a dozen sine bleeps for "sparkle", an FM swoop
  under every wave (a "wub"), sine LFOs and square gates for wings and flutter (a helicopter, a
  kazoo), a bare sawtooth for a servo, a vowel sung by a machine for every beast. A naked
  oscillator is the one thing nothing in the world sounds like, and the ear picks it out at once.
  **Nothing in a sound is an oscillator on its own now.** A pitch is air made to sing (`halo`),
  strings (`pluck`), voices in company, or a resonance ringing with noise (`comb`); whatever
  wobbles wobbles irregularly (`nlfo`); a frequency moves on a curve the ear hears as even
  (`glide`: a sweep straight in Hz is the synth-sweep sound). (The blaster is the one place a
  falling pitch belongs — it is what the weapon is — and it is a spit of noise rung in a falling
  resonance, the hammer on the guy-wire, not a tone sliding.)
- **One layer, one envelope.** Most sounds were a single cloud of filtered noise with a single
  decay: measured, the median share of a sound's spectrogram that one spectrum times one envelope
  explains was 0.86. **A sound is a head, a body and a tail**: the first 30ms, which say how it
  began (`snap`); what it is made of, moving — passing or leaving, band and loudness together
  (`whoosh`, `leaving`); and what it leaves behind — each with its own envelope and its own part of
  the spectrum.
- **The weight all under 120Hz.** A third of the set's energy was down there, in forty sounds over
  60% of it (the Earthshaker's slam 95%, a hit taken 90%): on a laptop, where nothing under 150Hz
  is played, a slam was a faint hiss, and on headphones every impact was the same boom. Weight is
  `weight`: a push an octave higher that settles and holds (it does not go on falling as it dies,
  which is the difference between weight and a kick drum), driven enough to carry harmonics, with
  the puff of air it shoves; a slam has the crunch of the ground giving and the air shoved out, in
  the low mids where every speaker is; and `tame_sub` turns down the bottom of any sound over its
  class's limit (`SUB_LIMIT`). The set's bass share is 0.21 (it was 0.34), its presence and air
  0.12 and 0.13 (0.07 each).

## Clinks: what that pass got wrong

The third pass replaced the sparkle's bleeps with "glitter": a cloud of tiny chimes, a few
milliseconds of one pitch each, on the theory that enough of them are heard as a cloud. They are
heard as what each one is. **A tick with a pitch is a clink**, and the ear finds a pitch under
noise far more easily than its level says. The glitter went into forty sounds, with a click on
the front of a hundred more, chains of thirty-eight bright links, mail jingling on every armoured
charge, a jester's bells on every card — and the first thing said of the set was that it was
"clinky, like the Samurai's": a katana whose cut had a handful of pins dropped in it, and a dash
that ended on a bright click. Counted (`sound_audit.py`'s ticks), the set had more of them a sound
than the one it replaced.

- **Detail is smooth, or it is the point.** A sound may be made of several events when that is
  what it is — hoofbeats, three rounds, a chain's links, lightning jumping, rubble, a glitch — and
  is then listed in the audit's `MEANT_TO_TICK` with why. Anything else has no more than four
  brief events in it. Glitter is `shimmer`, a sheen with nothing in it that begins or ends; a
  blade's ring is `steel_song`, friction and not a blow; a chain's links are dull knocks, broad
  and under 6kHz (`rattle`), half as many; rubble (`debris`), crackle (`grains`) and tearing
  (`rip`) have edges of a millisecond, not steps.
- **An edge sounds with the stroke.** A swing's steel begins before the blade passes and ends
  with it; begun at the pass and cut short it was a second little event on top of the swing.
- **Only the first edge of a sound is sharpened** (`transient_shape`), by a few dB. It used to
  lift every edge in the sound by how far it stood over the moment before, up to seven times: so
  every grain of a tail that came after a quiet moment was thrown forward 14dB, and soft detail
  became a row of clicks.
- **Air gusts, it doesn't flutter** (`whoosh`): a random wobble 21 times a second and deep is a
  stutter, and a swing came out ragged. Eight a second and slight; a blade's stroke has none.
- **A primary is short and does not hold**: `bolt`, `melee` or `tiny`, never `held`, which the
  generator refuses otherwise. Twenty-three were of a long class or held (a boulder thrown twice a
  second lasted a second and a quarter, a star bolt was a chord held for the whole gap between
  shots); none is now louder than 12dB under its peak for more than a fifth of a second.
- **Whoever can't listen offers a choice.** `sound_audition.py --variants <dir>` puts
  alternatives of one sound at the top of the page (a katana as air only, with its steel, with
  more of it): which is right is a question for an ear, and it is quicker asked than guessed.

And one about the whole set: **a level is how loud a sound is, not how tall.** `save_wav` levelled
by peak, and two sounds at one peak are 10dB apart to the ear — a click and a roar. `heard_level`
is the loudest tenth of a second, weighted as it is heard (nothing counted under 120Hz); a sound's
`level` in the tables names that (0.6 is the reference), and the limiter rounds off what it puts
over the ceiling, up to 4dB (a sound that would need more is left quieter: it is all peak).

## The master chain

Generators output mono; every sound then goes through the shared `master()` chain —
`transient_shape` → `compress` → `sub_boost` (the weight) → `presence_dip` → `air_tame` →
`stereoize` (decorrelated width) → `stereo_reverb` (separate impulse response per channel) →
optional `auto_pan`/`ping_pong` → `stereo_glue` (music only) — then a 30Hz high-pass, the class's
shape, the trim, `tame_sub`, levelling and limiting. Output is 16-bit **stereo** 44.1kHz.
`sounds/` is 48MB, the largest asset directory; the two music loops are 8.5MB of it. The client
holds a match's worth decoded, not all of it (client/audio/CLAUDE.md).

Rules, and why they matter:

- **Length is bounded by `SHOOT_COOLDOWN_MS` (500).** `MAX_DUR` caps each sound by class
  (`bolt` 0.55s, `melee` 0.6s, `ability` 0.9s, `heavy` 1.25s), and a primary is never of the last
  two (*Clinks*, above). An attack with more than
  ~0.5s of audible energy is still sounding when its own next shot fires; stacked across
  a firefight that is the difference between distinct attacks and mush. A tail is cut where it
  last passes -37dB under the sound's peak (`trim_tail`): under the music and the next sound it
  is not heard, and at -52dB, where the cut was, most sounds ran to their class's cap on reverb.
- **`presence_dip` (a wide −3.5dB bell at 3.2kHz) is applied to every SFX.** The ear's
  sensitivity peaks at 3-4kHz, so 300 attacks all piling energy there is physically
  tiring inside a minute — more than any individual sound, that was what made the first
  set shrill.
- **Identity goes in the body, not on top.** A bolt has a short low push under its launch,
  and what names it rides over that. Built the other way round — all identity, no body —
  bolts read as UI beeps and vanish the moment anything else plays.
- **A projectile sounds like what it is made of.** A fire bolt is combustion (a whump,
  a flickering roar, pops), a frost shard fracture (a cloud of bright grains, a thin
  whistle, a cold hiss), a toxic orb liquid (bubbles, a gulp, gas), a ghost breath. Every
  bolt used to be one FM note per school of magic — a pitched tone with inharmonic
  sidebands, a 1.5ms attack and an exponential decay — and however the ratio and filter
  were set, **a pitched layer that starts instantly and dies away exponentially is, to the
  ear, a struck object; with inharmonic partials it is a tin can.** A magic school with no
  material (arcane, runes, stars, the dark) gets `halo`, swelling to a peak and going (`peak=`),
  never starting and dying — only glass and strings do that.
- **A sound is its character's.** The thing a projectile type names is drawn, and heard, as its
  thrower's: a base engine plus what that character adds — a snarl, wingbeats, a sistrum, the
  crockery, a clock. The primary is what a player hears for a whole match.
- **A fan is three in the sound itself**: the mixer makes one sound of a fan's projectiles, so its
  sound is made as three in quick succession.
- **A thrown bomb's sound is the throw**, and its detonation its blast, played when it goes off.

## Judging a change

**By ear first.** `scripts/sound_audition.py <dir>` writes a page with a row a character and a
button for each of its three attacks, playing what the game plays (and, with `--before <rev>`,
what it played at that commit beside it; "play twice" for how a held primary repeats). It is the
only tool here that answers the question the set is for: does this sound like that attack, in
that character's hands? Whoever changes a sound without being able to listen (an agent) hands
this page to someone who can.

**The spectrogram gallery.** `scripts/sound_gallery.py` renders every sound as a log-frequency
spectrogram with a dB envelope strip underneath, 24 to a contact sheet: 359 assets cannot be
judged one at a time, and the faults that matter are the ones visible when they sit side by side.

| in the picture | in the sound |
|---|---|
| parallel lines sloping down | a pitch glide. One is a cartoon "boing"; a whole family doing it is why a set reads as generic |
| horizontal lines after the onset | a struck object ringing. A few, inharmonic, is the sound of hitting a metal bucket |
| a single thin line, sloping or flat | a bare oscillator |
| a flat-topped envelope strip | no shape. The ear reads shape first, so this is heard as generic texture however good the texture is |
| energy filling 60Hz-16kHz | no focus. Once every sound occupies the whole range, none of them is distinguishable |
| everything under the 125Hz tick | nothing, on a laptop |
| no vertical stripe at the onset | no transient, so no impact |

Three faults found exactly this way, after the numbers said the set was fine: every bolt was a
slide whistle (a pitch envelope sliding a harmonic stack down most of an octave); nothing decayed
(two reverbs near 60% wet in series — hence `GEN_REVERB` and `enforce_shape`, a per-class
amplitude *ceiling* that ducks anything refusing to fall away; a sound meant to hold is registered
`held=True` and gets the `drone` contour); everything was full-spectrum (`air_tame` is a real
-7dB shelf at 9kHz, with `BRIGHT`/`SPARK` for the sounds that have earned their top end).

**The audit: clang and ticks.** `scripts/sound_audit.py` also counts, for every sound, the brief
events that poke out of it (`ticks`: a band of the spectrogram standing 12dB over itself a moment
before and 9dB over itself a moment after — there and gone), which is what "clinky" is, and
`--check` fails on more than four in a sound that is not in `MEANT_TO_TICK` (`--ticks` sorts by
them). Smooth noise has none however bright; the cloud of chimes had dozens. And it measures the
tin can directly. It
finds the spectral peaks that keep ringing in one place for 80ms or more after the onset (noise
never does, a glide never stays put), keeps the *struck* ones (dying away from a peak near their
start, rather than swelling or holding as a voice does), and scores `clang` = the share of the
sound that is struck partials, weighted up when they are inharmonic and when they sit in the
400Hz-5kHz band a can rings in. `--check` exits 1 if anything over the limit is not in
`MEANT_TO_RING` (the tunes, the strings, the chimes, the howl, the hitmarker, the spawn and death
notes). Run it after any change here.

**The clang rule.** A modal bank is a *colour under* an impact, never the impact itself.
Ways to turn the whole game into someone hitting a metal bucket, all of which these files have
done at some point:

- letting the partials lead — `MATERIALS["ring"]` is 0.14-0.55 for everything except
  `bell` for exactly this reason, and the decay rates are weapon rates, not the
  instrument rates a physical-modelling paper gives you;
- ringing a material once per rotation in `thrown()`, which is *literally* banging on
  metal at 7Hz. The tumble is air being chopped (`tumble`: uneven, and slowing);
- ringing it at all when nothing is struck: a weapon leaving a hand makes no metal sound,
  so `thrown()` is air, the arm's push and whatever the object carries, and a swing's cut is
  what its edge *does* (`edge`: a blade scrapes, a claw rips, a maw slaps, chitin clicks);
- a pitched layer with a struck envelope standing in for an element — the FM bolt, a steel
  strike for holy light, a wood block for a rope or a playing card, an iron bell for a sistrum
  (which is a rattle);
- building a debris scatter out of `strike()` — a dozen tuned resonators inside half a
  second. `debris()` exists for this: each grain is its own short filtered noise burst;
- a high-Q resonance riding on noise where a chord isn't the point (`whoosh(q=…)` is capped at
  3.0). Narrow resonance on noise is the sound of blowing across a bottle.

`thud()` covers the other half: a body impact is one damped sine with no overtone series, and the
dull slap of air it pushes (the part a small speaker plays).

Avoid also: sustained pure tones (the old horn was a 2.2s sine stack — a foghorn), melodic
arpeggios as attacks (the old data bolt was a five-note chiptune riff fired twice a second),
tremolo rates in the 20-60Hz roughness band unless harshness is the point (a screech), and a
global `tanh` on the master bus (it costs several dB of crest on *every* sound — `_soft_knee`
rounds off only what is above the knee).

## Adding a sound

`sound("name", generator, level, class, master, held=...)` in the module it belongs to (an
engine call with what the character adds is usually all of it: see any character's block in
`sound_attacks.py`), and its name in `AbilitySounds.scala` (a character's row, a type's `set`, a
`blast`) or `AudioManager.EVENT_SOUNDS`. Then `generate_sounds.py <name>`, the audit, and the
audition page. A sound with a pitch that is the point of it goes in `AbilitySounds`' `tuned`
list, so the mixer leaves its pitch alone; one meant to ring, in the audit's `MEANT_TO_RING`.

Which sound the game plays for each event, and the mixer that plays them:
src/main/scala/com/gridgame/client/audio/CLAUDE.md.
