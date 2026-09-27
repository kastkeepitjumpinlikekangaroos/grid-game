# Sound effects and music

All audio is procedurally synthesized (numpy oscillators/noise, no samples or external
audio libraries — zero licensing concerns) into `sounds/*.wav`, mirroring how sprites are
generated.

```bash
# Requires numpy: pip install numpy
python3 scripts/generate_sounds.py   # -> sounds/*.wav (196 files, ~25s)

# Look at what you just made — the contact sheet is the review loop (see below).
# Needs Pillow as well as numpy: pip install numpy Pillow
python3 scripts/sound_gallery.py /tmp/sndgallery

# Does anything ring like struck metal that shouldn't? (see *Measuring it*; numpy only)
python3 scripts/sound_audit.py --check
```

`scripts/generate_sounds.py` is a sound-design toolkit, not tone+noise bursts. Alongside
FM (`fm`), a time-varying resonant state-variable filter (`svf`), fast static resonant
filters (`res_lp`/`res_bp`), causal RBJ biquads (`eq`, used wherever a transient or the
master EQ is involved — the zero-phase FFT filters pre-ring, which puts a ghost tick
before a click), `saturate`, `bitcrush`, convolution `reverb`, `delay_fx` and `chorus`,
four engines carry most of the character:

- **What things are made of** — the layers a projectile is built from when it is a thing
  rather than a note, all noise-excited or harmonic: `grains` (a Poisson cloud of
  millisecond noise grains: fire pops, ice fracturing, sparks, gravel), `bubbles` (sines
  rising as they close — the cue for liquid), `sizzle` (frying: acid, lava, a fuse),
  `squelch` (mud, flesh), `breath` (a whisper: formants over noise), `choir` (voices on a
  chord), `glow` (detuned saws swelled in through a falling lowpass — the only tone a magic
  bolt gets), `pluck` (Karplus-Strong: a string, every partial harmonic), `rattle` (chain
  links: dozens of few-millisecond clinks), `rip` (tearing), `crack` (a whip's N-wave),
  `buzz` (a swarm), `scrape` (a blade's gliding "shing"), `creak` (wood under strain),
  `heartbeat`, `whump` (a pressure bloom) and `flange` (a sweeping comb: energy streaking).
- **`modal` + `MATERIALS`** — a struck object rings at frequency ratios fixed by its
  shape, each partial decaying at its own rate. Those two tables *are* the difference
  between iron, wood, bone, stone, ice, glass, chitin and flesh. It is for things that
  really are struck — a trap's jaws, a slam's contact, the spawn and death bells — and
  through `strike` (= `modal` + the bright edge of the contact) it used to be under
  nearly everything, which is where the tin cans came from (see the fourth rule below).
- **`cry`/`glottal`/`tract`** — source-filter voice synthesis: a glottal pulse train with
  jitter and shimmer through formants that *move*. Every howl, screech, wail, bellow,
  roar and death exhale in the game comes out of this one throat and differs by pitch
  contour, vowel path and roughness.
- **`air`** — a whoosh is pink noise through a resonance tracing a doppler arc, with slow
  turbulence, an optional edge tone, and a `body` layer an octave and a half below
  standing in for the mass of air displaced. Without that body a swing is 100% 2-5kHz
  hiss.

Attacks are then assembled by family engines (`bolt`, `swing`, `thrown`, `shaft`,
`lobbed`, `slam`, `burst`, `wavefront`, `gun`, `chain`, `elec`, `machine`, and `blaster`
and `radiance` for lasers and holy light) plus ~60 one-offs. Keeping families as engines
rather than 150 bespoke functions is what lets one quality change reach the whole roster,
the same reason every sprite goes through `sprite_base.generate_character`. `bolt` is a
table of voices, one per element (`_b_fire`, `_b_ice`, `_b_poison`, `_b_soul`, …), under a
shared flight, weight and room. There is no `beam` engine any more: of the ten things it
voiced as a held FM-and-saw note only the railgun is anything like a beam on screen, so
each is now what the renderer draws — a blaster bolt, a comet of frost, a travelling
whirlpool, a vine, Medusa's gaze, a wad of bandages, a void lance, a searing eye.

Generators output mono; every sound then goes through the shared `master()` chain —
`transient_shape` → `compress` → `sub_boost` → `presence_dip` → `air_tame` → `stereoize`
(decorrelated width) → `stereo_reverb` (separate impulse response per channel) → optional
`auto_pan`/`ping_pong` → `stereo_glue` (music only) → limiting. Per-sound `level`s give
the mix real dynamics (a thrown card is quiet, thunder is loud) — the set spans ~12dB.
Output is 16-bit **stereo** 44.1kHz. `sounds/` is ~33MB, the largest asset directory; the
two music loops are ~8.5MB of it.

Three rules the earlier version of this file broke, and why they matter:

- **Length is bounded by `SHOOT_COOLDOWN_MS` (500).** `MAX_DUR` caps each sound by class
  (`bolt` 0.55s, `melee` 0.6s, `ability` 0.9s, `heavy` 1.5s). An attack with more than
  ~0.5s of audible energy is still sounding when its own next shot fires; stacked across
  a firefight that is the difference between distinct attacks and mush.
- **`presence_dip` (a wide −3.5dB bell at 3.2kHz) is applied to every SFX.** The ear's
  sensitivity peaks at 3-4kHz, so 150 attacks all piling energy there is physically
  tiring inside a minute — more than any individual sound, that was what made the old
  set shrill. Median 2-5kHz energy fraction is now 0.03 (it was 0.17, with a long tail
  up to 0.76).
- **Identity goes in the body, not on top.** A bolt has a short low push under its launch,
  and what names it rides over that. Built the other way round — all identity, no body —
  bolts read as UI beeps and vanish the moment anything else plays.
- **A projectile sounds like what it is made of.** A fire bolt is combustion (a whump,
  a flickering roar, pops), a frost shard fracture (a cloud of bright grains, a thin
  whistle, a cold hiss), a toxic orb liquid (bubbles, a gulp, gas), a ghost breath. Every
  bolt used to be one FM note per school of magic — a pitched tone with inharmonic
  sidebands, a 1.5ms attack and an exponential decay — and however the ratio and filter
  were set, **a pitched layer that starts instantly and dies away exponentially is, to the
  ear, a struck object; with inharmonic partials it is a tin can.** The flame bolt was 96%
  ringing partials. `sound_audit.py` flagged 28 sounds; it flags none now. A magic school
  with no material (arcane, runes, stars, the dark) gets `glow`: harmonic, swelled in over
  at least 6ms, and never the loudest layer.

## Judging a change: the spectrogram gallery

`scripts/sound_gallery.py` renders every sound as a log-frequency spectrogram
with a dB envelope strip underneath, 24 to a contact sheet. It exists for the
same reason `projectile_gallery` does — 196 assets cannot be judged one at a
time, and the faults that matter are the ones visible when they sit side by
side. **Run it after any change here.** What to look for:

| in the picture | in the sound |
|---|---|
| parallel lines sloping down | a pitch glide. One is a cartoon "boing"; a whole family doing it is why a set reads as generic |
| horizontal lines after the onset | a struck object ringing. A few, inharmonic, is the sound of hitting a metal bucket |
| a flat-topped envelope strip | no shape. The ear reads shape first, so this is heard as generic texture however good the texture is |
| energy filling 60Hz-16kHz | no focus. Once every sound occupies the whole range, none of them is distinguishable |
| no vertical stripe at the onset | no transient, so no impact |

Three faults found exactly this way, after the numbers said the set was fine:

- **every bolt was a slide whistle.** `bolt()`'s pitch envelope slid a harmonic
  stack down most of an octave across the whole sound. It is now a fast, small
  settle inside the first 35ms — a launch, not a swoop.
- **nothing decayed.** Generator-stage reverb and the master send were both
  near 60% wet, in series, which turned a bolt that reaches -44dB by 250ms dry
  into -20dB. Hence `GEN_REVERB` and, as a backstop, `enforce_shape()`: a
  per-class amplitude *ceiling* (flat for `hold`, then down to -60dB over
  `t60`) that ducks anything refusing to fall away. Deliberately held sounds —
  beams, howls, gas clouds — are listed in `SUSTAINED` and get the `drone`
  contour instead.
- **everything was full-spectrum.** Summing five layers and saturating the
  result fills the whole band on every sound. `air_tame` is now a real -7dB
  shelf at 9kHz, with `BRIGHT`/`SPARK` for the sounds that have earned their
  top end (ice, glass, sparkle, electricity, the hitmarker).

## Measuring it: the clang audit

`scripts/sound_audit.py` measures the tin can directly, for every sound, because a spectrogram
only shows it to someone who is looking and the numbers anyone normally checks (level, length,
band) never show it at all. It finds the spectral peaks that keep ringing in one place for 80ms
or more after the onset (noise never does, a glide never stays put), keeps the *struck* ones
(dying away from a peak near their start, rather than swelling or holding as a voice does), and
scores `clang` = the share of the sound that is struck partials, weighted up when they are
inharmonic and when they sit in the 400Hz-5kHz band a can rings in. A voice rings but is not
struck, a string or a chord is struck but harmonic, so both score low. `--check` exits 1 if
anything over the limit is not in `MEANT_TO_RING` (the lyre melody, the harp charm, the howl,
the hitmarker, the spawn and death bells). Run it after any change here, with the gallery.

**The clang rule.** A modal bank is a *colour under* an impact, never the impact itself.
Ways to turn the whole game into someone hitting a metal bucket, all of which this file has
done at some point:

- letting the partials lead — `MATERIALS["ring"]` is 0.14-0.55 for everything except
  `bell` for exactly this reason, and the decay rates are weapon rates, not the
  instrument rates a physical-modelling paper gives you;
- ringing a material once per rotation in `thrown()`, which is *literally* banging on
  metal at 7Hz. The tumble is air being chopped;
- ringing it at all when nothing is struck: a weapon leaving a hand makes no metal sound,
  so `thrown()` is air, the arm's push and whatever the object carries (a bone's clatter, a
  card's flick and flutter, a cursed blade's whisper), and a swing's cut is what its edge
  *does* (`_edge`: a blade scrapes, a claw rips, a maw slaps) — the old swing rang the
  weapon's material, and a claw that rings is a fork dropped on a tin plate;
- a pitched layer with a struck envelope standing in for an element — the FM bolt above,
  a steel strike for holy light, a wood block for a rope or a playing card, an iron bell
  for a sistrum (which is a rattle);
- building a debris scatter out of `strike()` — a dozen tuned resonators inside half a
  second. `debris()` exists for this: each grain is its own short filtered noise burst;
- a high-Q resonance riding on noise (`air(q=…)` is capped at 3.0). Narrow resonance on
  noise is the sound of blowing across a bottle, and it is hollow in the same way on
  every sound that uses it.

`thud()` covers the other half: a body impact is one damped sine with no overtone series.
A drum-membrane mode set at 80Hz is a tom, and a tom under every hit is a bucket.

Avoid also: sustained pure tones (the old horn was a 2.2s sine stack — a foghorn),
melodic arpeggios as attacks (the old data bolt was a five-note chiptune riff fired twice
a second), tremolo rates in the 20-60Hz roughness band (the old stinger was a kazoo), and
a global `tanh` on the master bus (it costs several dB of crest on *every* sound —
`_soft_knee` rounds off only what is above the knee).

Which sound the game plays for each event, and the mixer that plays it: src/main/scala/com/gridgame/client/audio/CLAUDE.md.
