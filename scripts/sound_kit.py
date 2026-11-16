#!/usr/bin/env python3
"""The sound-design kit: everything a sound is built from, and the chain that finishes it.

`generate_sounds.py` is the driver (the table of every sound, and `main`); the sounds themselves are
in `sound_attacks.py` (what a projectile sounds like as it is thrown), `sound_casts.py` (an ability
that throws nothing: a dash, a blink, a phase, a barrier, a trap) and `sound_events.py` (hits, deaths,
blasts, traps going off), and the music in `sound_music.py`. How they are designed and judged is
scripts/SOUNDS.md.

Layout of this file:

    oscillators/envelopes -> filters (causal biquads + zero-phase FFT) -> modal synthesis ->
    vocal synthesis -> air/fire/water -> what things are made of -> shaping/space -> stereo ->
    master chain and output -> gestures (motion, glitter, pitched air, beasts' throats)

Generators produce mono. Every sound then goes through the shared `master()` chain:

    transient shaping -> compression -> weight -> presence dip -> stereo decorrelation/width ->
    true-stereo reverb send -> auto-pan -> limiting

Output is deterministic: every random source is seeded by name.
"""
import numpy as np
import os
import wave
import zlib

SR = 44100
OUT_DIR = os.path.join(os.path.dirname(__file__), "..", "sounds")


def seed_of(name):
    """Deterministic seed — Python's hash() is salted per process."""
    return zlib.crc32(str(name).encode()) & 0x7FFFFFFF


# ─────────────────────────── oscillators & envelopes ───────────────────────────

def n_of(dur):
    return max(1, int(dur * SR))


def t_axis(dur):
    return np.arange(n_of(dur)) / SR


def as_array(v, n):
    if np.isscalar(v):
        return np.full(n, float(v))
    v = np.asarray(v, dtype=float)
    if len(v) == n:
        return v
    idx = np.linspace(0, len(v) - 1, n)
    return np.interp(idx, np.arange(len(v)), v)


def phase_of(freq, n):
    return 2 * np.pi * np.cumsum(as_array(freq, n)) / SR


def sine(freq, dur, phase=0.0):
    n = n_of(dur)
    return np.sin(phase_of(freq, n) + phase)


def saw(freq, dur):
    n = n_of(dur)
    ph = np.cumsum(as_array(freq, n)) / SR
    return 2.0 * (ph % 1.0) - 1.0


def square(freq, dur, duty=0.5):
    n = n_of(dur)
    ph = np.cumsum(as_array(freq, n)) / SR
    return np.where((ph % 1.0) < duty, 1.0, -1.0)


def fm(carrier, ratio, index, dur, feedback_ratio=None):
    """Classic 2-operator FM. carrier/index may be arrays (envelopes).

    Inharmonic ratios (1.41, 2.73, 3.7...) give metallic/alien timbres;
    integer ratios give bell/brass-like harmonic tones.
    """
    n = n_of(dur)
    car = as_array(carrier, n)
    idx = as_array(index, n)
    mod_ph = 2 * np.pi * np.cumsum(car * ratio) / SR
    mod = np.sin(mod_ph)
    if feedback_ratio is not None:
        mod = mod + 0.5 * np.sin(2 * np.pi * np.cumsum(car * feedback_ratio) / SR)
    return np.sin(2 * np.pi * np.cumsum(car) / SR + idx * mod)


def noise(dur, name="n"):
    return np.random.default_rng(seed_of(name)).normal(0.0, 0.4, n_of(dur))


def pink(dur, name="p"):
    """1/f noise. Real-world air, fire and rubble are pink, not white — white
    noise is the single loudest tell that a sound was synthesized."""
    n = n_of(dur)
    w = np.random.default_rng(seed_of(name)).normal(0.0, 1.0, n)
    spec = np.fft.rfft(w)
    f = np.fft.rfftfreq(n, 1.0 / SR)
    f[0] = f[1] if len(f) > 1 else 1.0
    spec /= np.sqrt(f / 40.0 + 1.0)
    out = np.fft.irfft(spec, n)
    return out / (np.std(out) + 1e-9) * 0.4


def seg_env(points, dur):
    """Piecewise-linear envelope from [(time_frac, value), ...]."""
    n = n_of(dur)
    xs = np.array([p[0] for p in points]) * (n - 1)
    ys = np.array([p[1] for p in points], dtype=float)
    return np.interp(np.arange(n), xs, ys)


def exp_env(dur, attack=0.003, decay=5.0, floor=0.0):
    n = n_of(dur)
    a = max(1, int(attack * SR))
    a = min(a, n - 1) if n > 1 else 1
    body = np.exp(-decay * np.linspace(0, 1, n - a)) + floor
    return np.concatenate([np.linspace(0, 1, a, endpoint=False), body])[:n]


def ad_env(dur, attack=0.01, hold=0.2, release=0.5, curve=2.0):
    return seg_env([(0.0, 0.0), (attack, 1.0), (attack + hold, 0.85), (1.0, 0.0)], dur) ** (1.0 / curve)


def swell_env(dur, power=1.0):
    n = n_of(dur)
    return np.sin(np.linspace(0, np.pi, n)) ** power


def tremolo(n, rate, depth=0.6, shape="sine"):
    tt = np.arange(n) / SR
    if shape == "sine":
        lfo = 0.5 + 0.5 * np.sin(2 * np.pi * rate * tt)
    else:
        lfo = (np.sin(2 * np.pi * rate * tt) > 0).astype(float)
    return 1.0 - depth + depth * lfo


def click(dur=0.004, fc=3000, name="c", shape=1.4):
    """The bright edge of a contact — high-passed, so it reads as "hard"."""
    n = n_of(dur)
    c = highpass(noise(dur, name), fc)
    return c * (np.linspace(1.0, 0.0, n) ** shape)


def contact(dur=0.008, name="x", shape=1.6):
    """The contact burst itself: BROADBAND, because that is what a collision is.

    Kept separate from `click` on purpose. Feeding a high-passed click into a
    material's absorption filter (which is what this file used to do) leaves
    nothing at all — the filters cancel — so every soft-material impact came out
    as 98% sub-200Hz thump with no information about what had hit you."""
    n = n_of(dur)
    c = highpass(pink(dur, name), 90, order=1)
    c = c * (np.linspace(1.0, 0.0, n) ** shape)
    return c / (np.max(np.abs(c)) + 1e-9)


# ─────────────────────────── filters ───────────────────────────
# Two kinds, used deliberately:
#   * zero-phase FFT filters — fast, no phase smear, but they PRE-ring, which on
#     a hard transient puts a ghost tick before the tick. Fine for noise beds.
#   * causal biquads — the real thing, used wherever a transient or the master
#     EQ is involved.

def spectral_filter(x, kind, fc, order=2):
    """Zero-phase Butterworth-magnitude filter. Smooth rolloff (no brickwall ringing)."""
    n = len(x)
    if n < 8:
        return x
    spec = np.fft.rfft(x)
    f = np.fft.rfftfreq(n, 1.0 / SR)
    f = np.maximum(f, 1e-6)
    if kind == "lp":
        mag = 1.0 / np.sqrt(1.0 + (f / fc) ** (2 * order))
    elif kind == "hp":
        mag = 1.0 / np.sqrt(1.0 + (fc / f) ** (2 * order))
    else:
        raise ValueError(kind)
    return np.fft.irfft(spec * mag, n)


def lowpass(x, fc, order=2):
    return spectral_filter(x, "lp", fc, order)


def highpass(x, fc, order=2):
    return spectral_filter(x, "hp", fc, order)


def bandpass(x, lo, hi, order=2):
    return highpass(lowpass(x, hi, order), lo, order)


def biquad(x, b, a):
    """Direct-form-I biquad. Causal, so transients keep their leading edge."""
    b0, b1, b2 = b
    a1, a2 = a[1] / a[0], a[2] / a[0]
    b0, b1, b2 = b0 / a[0], b1 / a[0], b2 / a[0]
    n = len(x)
    y = [0.0] * n
    x1 = x2 = y1 = y2 = 0.0
    # plain lists in the loop: indexing a numpy array a sample at a time is most of the cost
    for i, xi in enumerate(np.asarray(x, dtype=float).tolist()):
        yi = b0 * xi + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        y[i] = yi
        x2, x1 = x1, xi
        y2, y1 = y1, yi
    return np.nan_to_num(np.array(y))


def _rbj(kind, fc, q, gain_db=0.0):
    """RBJ audio-EQ-cookbook coefficients."""
    w = 2.0 * np.pi * max(20.0, min(fc, SR * 0.45)) / SR
    cw, sw = np.cos(w), np.sin(w)
    alpha = sw / (2.0 * max(0.05, q))
    A = 10.0 ** (gain_db / 40.0)
    if kind == "peak":
        b = (1 + alpha * A, -2 * cw, 1 - alpha * A)
        a = (1 + alpha / A, -2 * cw, 1 - alpha / A)
    elif kind == "lowshelf":
        s = 2.0 * np.sqrt(A) * alpha
        b = (A * ((A + 1) - (A - 1) * cw + s), 2 * A * ((A - 1) - (A + 1) * cw),
             A * ((A + 1) - (A - 1) * cw - s))
        a = ((A + 1) + (A - 1) * cw + s, -2 * ((A - 1) + (A + 1) * cw),
             (A + 1) + (A - 1) * cw - s)
    elif kind == "highshelf":
        s = 2.0 * np.sqrt(A) * alpha
        b = (A * ((A + 1) + (A - 1) * cw + s), -2 * A * ((A - 1) + (A + 1) * cw),
             A * ((A + 1) + (A - 1) * cw - s))
        a = ((A + 1) - (A - 1) * cw + s, 2 * ((A - 1) - (A + 1) * cw),
             (A + 1) - (A - 1) * cw - s)
    elif kind == "lp":
        b = ((1 - cw) / 2, 1 - cw, (1 - cw) / 2)
        a = (1 + alpha, -2 * cw, 1 - alpha)
    elif kind == "hp":
        b = ((1 + cw) / 2, -(1 + cw), (1 + cw) / 2)
        a = (1 + alpha, -2 * cw, 1 - alpha)
    elif kind == "bp":
        b = (alpha, 0.0, -alpha)
        a = (1 + alpha, -2 * cw, 1 - alpha)
    else:
        raise ValueError(kind)
    return b, a


def eq(x, kind, fc, q=0.9, gain_db=0.0):
    if len(x) < 8:
        return x
    b, a = _rbj(kind, fc, q, gain_db)
    return biquad(x, b, a)


def res_bp(x, fc, q=6.0):
    """Static resonant bandpass (analog magnitude) — the building block for formants."""
    n = len(x)
    if n < 8:
        return x
    spec = np.fft.rfft(x)
    f = np.fft.rfftfreq(n, 1.0 / SR)
    w = f / max(fc, 20.0)
    mag = (w / max(q, 0.1)) / np.sqrt((1.0 - w ** 2) ** 2 + (w / max(q, 0.1)) ** 2)
    return np.fft.irfft(spec * np.minimum(mag, 8.0), n)


def res_lp(x, fc, q=4.0):
    """Static resonant lowpass via its analog magnitude response (fast, vectorized)."""
    n = len(x)
    if n < 8:
        return x
    spec = np.fft.rfft(x)
    f = np.fft.rfftfreq(n, 1.0 / SR)
    w = f / max(fc, 20.0)
    mag = 1.0 / np.sqrt((1.0 - w ** 2) ** 2 + (w / max(q, 0.1)) ** 2)
    mag = np.minimum(mag, 12.0)
    return np.fft.irfft(spec * mag, n)


def svf(x, cutoff, q=2.0, mode="lp"):
    """Chamberlin state-variable filter — supports a time-varying cutoff (sweeps).

    Sample-by-sample, so only used where the sweep is the point of the sound.
    """
    n = len(x)
    fc = np.clip(as_array(cutoff, n), 25.0, 16000.0)
    f = 2.0 * np.sin(np.pi * fc / SR)
    damp = min(2.0, max(0.05, 1.0 / q))
    out = [0.0] * n
    low = 0.0
    band = 0.0
    pick = 0 if mode == "lp" else 1 if mode == "bp" else 2
    xs = np.asarray(x, dtype=float).tolist()
    for i, fi in enumerate(f.tolist()):
        low += fi * band
        high = xs[i] - low - damp * band
        band += fi * high
        out[i] = low if pick == 0 else band if pick == 1 else high
    return np.nan_to_num(np.clip(np.array(out), -8.0, 8.0))


# Vowel formant frequencies — running a source through these makes it read as a
# voice/creature rather than as filtered noise.
VOWELS = {
    "ah": (730, 1090, 2440),
    "oo": (300, 870, 2240),
    "eh": (530, 1840, 2480),
    "uh": (640, 1190, 2390),
    "ee": (270, 2290, 3010),
    "aw": (570, 840, 2410),
    "er": (490, 1350, 1690),
    "oh": (500, 850, 2500),
}


def formant(x, vowel="ah", amount=0.7, q=9.0):
    f1, f2, f3 = VOWELS[vowel]
    voiced = res_bp(x, f1, q) * 1.0 + res_bp(x, f2, q) * 0.55 + res_bp(x, f3, q) * 0.3
    peak = np.max(np.abs(voiced))
    if peak > 1e-9:
        voiced = voiced / peak * (np.max(np.abs(x)) + 1e-9)
    return x * (1.0 - amount) + voiced * amount


# ─────────────────────────── modal synthesis ───────────────────────────
# A struck object rings at frequency ratios fixed by its shape, with a decay
# rate per partial fixed by its material. Those two tables ARE the difference
# between "iron", "wood", "bone" and "ice" — no amount of filtering noise gets
# there, which is why every impact in the game is built from this.
#
#   ratios  partial frequencies as multiples of f0
#   gains   relative amplitude of each partial
#   decay   e-folding rate per partial (higher = shorter); scaled by `damp`
#   damp    global decay multiplier — wood and flesh dump energy fast, metal doesn't
#   spread  random detune per partial, so two hits are never identical
#   noise   how much of the exciter's own noise survives into the body

# `ring` is how much of the sound the partials are allowed to be. It is low for
# almost everything on purpose: a real weapon impact is mostly the CONTACT —
# broadband, over in 30ms — with the material's modes as a brief colour under
# it. Let the modes lead and every impact in the game turns into somebody
# hitting a bucket, whatever the ratios say. Decay rates are correspondingly
# weapon rates, not instrument rates; only `bell` is allowed to sing.
MATERIALS = {
    # free-free metal bar: the classic vibraphone/anvil ratio set
    "iron":   dict(ratios=(1.0, 2.756, 5.404, 8.933, 13.35), gains=(1.0, .55, .32, .18, .09),
                   decay=(11.0, 15.0, 20.0, 28.0, 38.0), damp=1.0, spread=.004, noise=.7, tilt=1.0, ring=.34),
    # struck plate: dense and inharmonic — armour, shields, hulls
    "steel":  dict(ratios=(1.0, 1.72, 2.43, 3.14, 4.28, 5.61), gains=(1.0, .6, .45, .3, .2, .12),
                   decay=(14.0, 18.0, 23.0, 29.0, 36.0, 45.0), damp=1.3, spread=.006, noise=.75, tilt=1.0, ring=.30),
    # small bright steel — knives, shuriken, coins
    "blade":  dict(ratios=(1.0, 2.41, 4.12, 6.7, 9.9), gains=(1.0, .7, .45, .28, .15),
                   decay=(16.0, 21.0, 28.0, 36.0, 48.0), damp=1.5, spread=.008, noise=.7, tilt=1.2, ring=.38),
    # struck bar of wood: heavily damped, only a couple of usable partials
    "wood":   dict(ratios=(1.0, 3.01, 6.0, 10.1), gains=(1.0, .42, .2, .1),
                   decay=(20.0, 27.0, 36.0, 47.0), damp=1.0, spread=.01, noise=.75, tilt=.8, ring=.35),
    # rock: dense, near-noise, dies almost immediately
    "stone":  dict(ratios=(1.0, 1.47, 2.09, 2.71, 3.55, 4.62), gains=(1.0, .8, .62, .45, .3, .2),
                   decay=(26.0, 32.0, 38.0, 45.0, 54.0, 64.0), damp=1.0, spread=.03, noise=.88, tilt=.7, ring=.22),
    # bone: dry, hollow, brief ring
    "bone":   dict(ratios=(1.0, 2.6, 4.7, 7.1), gains=(1.0, .5, .28, .15),
                   decay=(17.0, 23.0, 30.0, 40.0), damp=1.0, spread=.012, noise=.7, tilt=1.0, ring=.40),
    # glass: bright, high, clean
    "glass":  dict(ratios=(1.0, 2.32, 4.25, 6.63, 9.38), gains=(1.0, .62, .4, .25, .14),
                   decay=(8.0, 11.0, 15.0, 19.0, 25.0), damp=1.1, spread=.005, noise=.55, tilt=1.4, ring=.55),
    # ice: glass with more damping and a crackle exciter
    "ice":    dict(ratios=(1.0, 2.1, 3.7, 5.9, 8.4), gains=(1.0, .66, .42, .26, .15),
                   decay=(13.0, 17.0, 23.0, 30.0, 38.0), damp=1.2, spread=.02, noise=.7, tilt=1.3, ring=.45),
    # chitin/shell — insects, carapace
    "chitin": dict(ratios=(1.0, 2.14, 3.63, 5.2), gains=(1.0, .6, .35, .2),
                   decay=(24.0, 32.0, 42.0, 54.0), damp=1.1, spread=.02, noise=.8, tilt=1.1, ring=.28),
    # a body: no ring at all, just displaced mass
    "flesh":  dict(ratios=(1.0, 1.93), gains=(1.0, .3),
                   decay=(26.0, 40.0), damp=1.0, spread=.02, noise=.85, tilt=.55, ring=.30),
    # earth/dirt — a spade, a grave, a footfall
    "earth":  dict(ratios=(1.0, 1.6, 2.4), gains=(1.0, .55, .3),
                   decay=(34.0, 44.0, 55.0), damp=1.0, spread=.05, noise=.94, tilt=.6, ring=.14),
    # the one thing here that is meant to be heard as a note
    "bell":   dict(ratios=(0.5, 1.0, 2.0, 3.01, 4.1), gains=(.42, 1.0, .5, .2, .1),
                   decay=(1.6, 2.2, 3.6, 5.4, 8.0), damp=1.0, spread=.002,
                   noise=.16, tilt=1.0, ring=1.0),
}


def modal(material, f0, dur, damp=1.0, amp=1.0, exciter=None, name="mod", noise_mix=None, ring=None):
    """Struck-object body: a bank of decaying partials excited by a contact burst.

    `exciter` colours the strike (a hard hit is broadband, a glancing one is not);
    the partials decide what was struck.
    """
    p = MATERIALS[material]
    n = n_of(dur)
    rng = np.random.default_rng(seed_of(name + material + str(round(f0, 2))))
    tt = np.arange(n) / SR
    out = np.zeros(n)
    d_scale = p["damp"] * damp
    for ratio, g, d in zip(p["ratios"], p["gains"], p["decay"]):
        f = f0 * ratio * (1.0 + rng.normal(0.0, p["spread"]))
        if f > SR * 0.47 or f < 12:
            continue
        ph = rng.uniform(0, 2 * np.pi)
        out += g * np.sin(2 * np.pi * f * tt + ph) * np.exp(-d * d_scale * tt)
    peak = np.max(np.abs(out))
    if peak > 1e-9:
        out /= peak
    # The exciter's own noise is what makes the strike sound like contact rather
    # than like a synth note being switched on.
    if exciter is None:
        exciter = contact(0.008, name=name + "x")
    ex = fit(np.asarray(exciter, dtype=float), n)
    nm = p["noise"] if noise_mix is None else noise_mix
    # `tilt` is the material's high-frequency absorption: flesh swallows the top
    # of a contact, iron keeps it. It shapes the burst, it does not delete it.
    ex = res_lp(ex, 700.0 + 2600.0 * p["tilt"] + f0 * 1.5, q=1.1)
    ex = ex / (np.max(np.abs(ex)) + 1e-9)
    r = p["ring"] if ring is None else ring
    return (out * r + ex * nm * 1.15) * amp


def thud(f0, dur, drive=1.9, name="th"):
    """The low displacement of a body impact: one damped sine with no partials, and the dull
    slap of air it pushes.

    The `drum` material was standing in for this and it is exactly wrong — a
    circular membrane's mode ratios (1, 1.59, 2.14, 2.30 ...) at 80Hz are a
    tom, and a tom under every hit is the "metal bucket" in the mix. A person
    being hit displaces mass; it has a pitch and no overtone series.

    The pitch settles a third inside the first tenth of the sound and then holds. It used to go on
    falling an octave and a half as it died, which is a tom being detuned; and it was a sine alone,
    which small speakers (most of them) don't play at all: the slap is the part a laptop hears."""
    f = glide([(0.0, f0 * 1.35), (0.1, f0), (1.0, f0 * 0.8)], dur)
    tone = saturate(sine(f, dur) * exp_env(dur, attack=0.0015, decay=6.5), drive)
    slap = lowpass(pink(dur, name + "s"), min(900.0, f0 * 7.0)) * exp_env(dur, attack=0.001, decay=13.0)
    slap = slap / (np.max(np.abs(slap)) + 1e-9)
    return tone * 0.8 + slap * 0.45


def debris(dur, count=12, band=(500, 5000), decay=(9.0, 22.0), spread=(0.02, 0.8),
           wet=0.0, name="deb"):
    """Rubble, shrapnel, splinters, scattered ice.

    Deliberately NOT a scatter of `strike`s. Twelve tuned resonators going off
    inside half a second is the sound of somebody hitting a bucket twelve
    times — the scatter reads as clatter only if each grain is a short filtered
    noise burst with its own random band, which is also what a chip of rock
    actually is."""
    n = n_of(dur)
    out = np.zeros(n)
    rng = np.random.default_rng(seed_of(name))
    # Two thirds of the chips asked for, none of them brighter than 4kHz, each with a
    # millisecond and a half to speak: a scatter of twenty bright ones with instant edges was a
    # scatter of clicks, and under a slam the clicks were what was heard
    count = max(2, int(round(count * 0.65)))
    top = min(band[1], 4000.0)
    for _ in range(count):
        t0 = rng.uniform(spread[0], dur * spread[1])
        gd = rng.uniform(0.008, 0.03)
        lo = rng.uniform(min(band[0], top * 0.4), top * 0.5)
        g = bandpass(noise(gd, name + str(rng.integers(1 << 20))), lo, lo * rng.uniform(2.2, 4.5))
        g *= decay_env(gd, attack=0.0015, t60=gd * 6.9 / rng.uniform(*decay) * 2.2)
        at(out, t0, g * rng.uniform(0.35, 1.0) * np.exp(-2.0 * t0 / dur))
    if wet > 0:
        out = mixdown(out * (1.0 - wet * 0.4), water(dur, size=2.4, bubbles=int(6 * wet),
                                                     foam=0.4, name=name + "w") * wet * 0.5)
    peak = np.max(np.abs(out))
    return out / (peak + 1e-9) if peak > 1e-9 else out


def strike(material, f0, dur, hardness=1.0, damp=1.0, amp=1.0, name="hit", ring=None):
    """modal() plus the bright edge of the contact, which is the layer people
    actually hear as "how hard". Hardness picks how bright and how short."""
    h = max(0.3, hardness)
    tilt = MATERIALS[material]["tilt"]
    ex = contact(0.003 + 0.009 / h, name=name + "ex", shape=1.0 + h)
    body = modal(material, f0, dur, damp=damp, amp=amp, exciter=ex, name=name, ring=ring)
    tick = click(0.0015 + 0.004 / h, fc=600 + 2600 * h * tilt, name=name + "tk", shape=1.2)
    k = min(len(tick), len(body))
    body[:k] += tick[:k] * 0.45 * h * (0.4 + 0.6 * tilt)
    return body


# ─────────────────────────── vocal synthesis ───────────────────────────
# Source-filter: a glottal pulse train (with the jitter and shimmer that make a
# voice sound alive) through formants that MOVE. A static formant filter over
# noise is the sound of a synthesizer pretending; moving formants over a glottal
# source is the sound of a throat.

def glottal(f0_env, dur, open_q=0.55, jitter=0.012, shimmer=0.08, growl=0.0, name="g"):
    n = n_of(dur)
    rng = np.random.default_rng(seed_of(name + "glot"))
    f = as_array(f0_env, n)
    # jitter: slow random pitch wander, which is what separates a living voice
    # from an oscillator
    if jitter > 0:
        wob = lowpass(rng.normal(0, 1, n), 11, order=1)
        wob /= (np.std(wob) + 1e-9)
        f = f * (1.0 + jitter * wob)
    ph = (np.cumsum(f) / SR) % 1.0
    oq = max(0.15, min(0.9, open_q))
    # Rosenberg-ish pulse: a smooth opening, then a hard closure — the closure
    # discontinuity is the actual excitation
    g = np.where(ph < oq, 0.5 - 0.5 * np.cos(np.pi * ph / oq), 0.0)
    g = np.concatenate([[0.0], np.diff(g)]) * SR / (np.maximum(f, 1.0) * 4.0)
    if growl > 0:
        # subharmonic + amplitude roughness = a growl rather than a hum
        sub = np.sin(2 * np.pi * np.cumsum(f * 0.5) / SR)
        g = g * (1.0 - growl * 0.5) + sub * growl * 0.5
        g *= 1.0 - growl * 0.45 * (0.5 + 0.5 * np.sin(2 * np.pi * np.cumsum(np.full(n, 48.0)) / SR))
    if shimmer > 0:
        amp = lowpass(rng.normal(0, 1, n), 8, order=1)
        amp /= (np.std(amp) + 1e-9)
        g *= 1.0 + shimmer * amp
    return np.nan_to_num(g)


def _formant_path(keys, dur, which):
    """Build a per-sample formant track from [(t_frac, vowel_or_(f1,f2,f3)), ...]."""
    pts = []
    for t, v in keys:
        fs = VOWELS[v] if isinstance(v, str) else v
        pts.append((t, fs[which]))
    return seg_env(pts, dur)


def tract(x, keys, dur, q=(11.0, 9.0, 7.0), gains=(1.0, 0.6, 0.32), breath=0.0, name="tr"):
    """Time-varying vocal tract. `keys` are vowel keyframes; the formants glide
    between them, which is what makes a cry sound like a mouth moving."""
    n = len(x)
    out = np.zeros(n)
    for i in range(3):
        track = _formant_path(keys, len(x) / SR, i)
        out += svf(x, track, q=q[i], mode="bp") * gains[i]
    if breath > 0:
        # amplitude-follow the glottal source so the breath sits INSIDE the
        # voice. Scaled up it just buries the harmonics and the cry turns back
        # into filtered noise, which is what the formants are here to avoid.
        b = fit(bandpass(noise(len(x) / SR, name + "br"), 1200, 6000), n)
        env = np.abs(x) / (np.max(np.abs(x)) + 1e-9)
        out = out * (1.0 - breath * 0.25) + b * breath * env * 1.1
    # lip radiation tilts a voice up, but only gently — and the chest still has
    # to be there underneath, or a cry is all mouth and no animal
    out = eq(out, "highshelf", 1800, q=0.7, gain_db=2.0)
    out = eq(out, "lowshelf", 260, q=0.7, gain_db=2.5)
    peak = np.max(np.abs(out))
    return out / (peak + 1e-9)


def cry(dur, f0_keys, vowels, growl=0.0, breath=0.25, jitter=0.015, open_q=0.55,
        amp_keys=((0.0, 0.0), (0.12, 1.0), (0.7, 0.85), (1.0, 0.0)), name="cry"):
    """One creature vocalization. Everything that howls, screeches, wails,
    bellows or roars in this game comes from here, so they share a throat and
    differ by pitch contour, vowel path and roughness."""
    f0 = seg_env(f0_keys, dur)
    src = glottal(f0, dur, open_q=open_q, jitter=jitter, growl=growl, name=name)
    v = tract(src, vowels, dur, breath=breath, name=name)
    return v * seg_env(list(amp_keys), dur)


# ─────────────────────────── air, fire, water ───────────────────────────

def air(dur, band=(500, 4000), q=5.0, arc=1.2, turb=0.5, edge=0.0, body=0.6, name="air"):
    """A whoosh. Not `noise` through a filter: pink noise through a resonance
    that traces a doppler arc, amplitude-modulated by slow turbulence, with an
    optional edge tone — the faint whistle a real fast-moving object makes.

    `body` is the layer that stops it being all edge: an octave-and-a-half below
    the resonance, tracking it, standing in for the mass of air actually being
    displaced. Without it a swing is 100% 2-5kHz hiss, which is both thin and
    the most fatiguing thing you can put in a mix."""
    n = n_of(dur)
    src = pink(dur, name + "p")
    lo, hi = band
    centre = seg_env([(0.0, lo), (0.45, hi), (1.0, lo * 0.85)], dur)
    # Q is deliberately low. A resonant peak riding on noise reads as a tube or
    # a bottle being blown across — hollow, and identical on every sound that
    # uses it, which is most of them. Moving air is broadband; the doppler
    # feel comes from the centre SWEEPING, not from how narrow it is.
    out = svf(src, centre, q=min(q, 3.0), mode="bp")
    if body > 0:
        out = out + svf(src, np.maximum(centre * 0.26, 90.0), q=1.1, mode="lp") * body * 1.5
    if turb > 0:
        t = lowpass(np.abs(pink(dur, name + "t")), 17, order=1)
        t /= (np.max(t) + 1e-9)
        out *= (1.0 - turb) + turb * (0.35 + 1.3 * t)
    if edge > 0:
        e = sine(centre * 0.98, dur) * edge * 0.35
        out += e
    return out * swell_env(dur, arc)


def fire(dur, low=140.0, roar=1.0, crackles=22, name="fire"):
    """Combustion: a turbulent roar plus discrete pops. The pops are what make
    it read as fire rather than as wind."""
    n = n_of(dur)
    body = svf(pink(dur, name + "r"), seg_env([(0.0, 600), (0.3, 2400), (1.0, 700)], dur), q=2.2)
    flut = lowpass(np.abs(pink(dur, name + "f")), 19, order=1)
    flut = 0.55 + 1.7 * flut / (np.max(flut) + 1e-9)
    body *= flut * ad_env(dur, attack=0.03, hold=0.35, curve=1.3) * roar
    # the rumble is turbulence in the low mids, gusting: it was a sine sliding down an octave,
    # which under every fire in the game was a drum
    rumble = bandpass(pink(dur, name + "rm"), low * 0.6, low * 2.6)
    flut2 = lowpass(np.abs(pink(dur, name + "rf")), 11, order=1)
    rumble *= (0.4 + 1.2 * flut2 / (np.max(flut2) + 1e-9)) * ad_env(dur, attack=0.02, hold=0.4, curve=1.4)
    rumble = rumble / (np.max(np.abs(rumble)) + 1e-9) * 0.5
    pops = np.zeros(n)
    rng = np.random.default_rng(seed_of(name + "cr"))
    for _ in range(crackles):
        t0 = rng.uniform(0.0, dur * 0.82)
        gd = rng.uniform(0.003, 0.014)
        g = highpass(noise(gd, name + "g" + str(rng.integers(1 << 20))), 2600)
        g *= exp_env(gd, decay=15) * rng.uniform(0.08, 0.3)
        at(pops, t0, g)
    return saturate(mixdown(body * 0.85, rumble, pops * 0.8), 2.0)


def water(dur, size=1.0, bubbles=10, foam=0.5, name="wat"):
    """Moving water: a low surge, broadband foam, and pitch-rising bubbles.
    The rising bubble glissando is the cue the ear uses for "liquid"."""
    n = n_of(dur)
    surge = svf(pink(dur, name + "s"), seg_env([(0.0, 300 / size), (0.4, 2200), (1.0, 420)], dur), q=2.0)
    surge *= seg_env([(0.0, 0.05), (0.35, 1.0), (0.75, 0.6), (1.0, 0.0)], dur)
    fo = highpass(pink(dur, name + "f"), 3800) * seg_env([(0.0, 0.0), (0.4, 0.85), (1.0, 0.05)], dur)
    # the mass of it moving: low-mid noise swelling and going, where a speaker can play it (it
    # was a sine falling from 78Hz to 36: most of a splash's energy, and none of what was heard)
    low = bandpass(pink(dur, name + "l"), 70 / size, 300 / size)
    low *= seg_env([(0.0, 0.25), (0.35, 1.0), (1.0, 0.0)], dur)
    low = low / (np.max(np.abs(low)) + 1e-9) * 0.7
    bub = np.zeros(n)
    rng = np.random.default_rng(seed_of(name + "b"))
    for _ in range(bubbles):
        t0 = rng.uniform(dur * 0.2, dur * 0.9)
        bd = rng.uniform(0.02, 0.055)
        f0 = rng.uniform(280, 900)
        b = sine(seg_env([(0.0, f0), (1.0, f0 * rng.uniform(2.0, 3.4))], bd), bd)
        at(bub, t0, b * exp_env(bd, decay=9) * rng.uniform(0.05, 0.18))
    return saturate(mixdown(surge * 0.8, fo * foam, low * 0.75, bub), 1.7)


def gas(dur, band=(400, 5200), fizz=0.5, pitchfall=True, name="gas"):
    """A pressurized release / cloud bloom — hiss with a falling resonance and
    a bed of fine fizz. Poison, steam, aerosol."""
    n = n_of(dur)
    centre = seg_env([(0.0, band[1]), (0.35, band[1] * 0.5), (1.0, band[0])], dur) if pitchfall \
        else seg_env([(0.0, band[0]), (0.5, band[1]), (1.0, band[0])], dur)
    body = svf(pink(dur, name + "h"), centre, q=2.6, mode="bp")
    body *= seg_env([(0.0, 0.0), (0.06, 1.0), (0.45, 0.55), (1.0, 0.0)], dur)
    fz = highpass(noise(dur, name + "z"), 2600) * seg_env([(0.0, 0.2), (0.3, 0.7), (1.0, 0.0)], dur)
    return saturate(mixdown(body, fz * fizz * 0.5), 1.6)


def sparkle(dur, lo=3000, hi=11000, count=14, rise=True, name="spk"):
    """Glitter: magic dust, ice crystals, embers. A cloud of tiny chimes (`shimmer`), `count` to
    0.4s of it and thinning as it goes.

    Each used to be a sine 20-70ms long whose pitch slid up an eighth as it rang, a dozen to a
    sound: a dozen little bleeps, which is what a cheap game's magic is. A grain now holds its
    pitch, is over in a few milliseconds, and there are enough of them that the ear hears the
    cloud and not its grains. (`rise` is kept for the callers; nothing slides.)"""
    rate = max(30.0, count * 5.5 / max(dur, 0.05) * 0.4)
    return shimmer(dur, lo=lo, hi=hi, rate=rate, density=[(0.0, 1.0), (0.35, 0.6), (1.0, 0.12)],
                   name=name)


def zap_arc(dur, lo=500, hi=9000, q=9.0, steps=90, name="arc"):
    """Electrical arcing: a resonant band that jumps randomly. The jump rate is
    the whole effect — smooth it and it turns into wind."""
    n = n_of(dur)
    rng = np.random.default_rng(seed_of(name))
    cut = np.repeat(rng.uniform(lo, hi, steps), max(1, n // steps + 1))[:n]
    cut = np.pad(cut, (0, max(0, n - len(cut))), mode="edge")
    return svf(noise(dur, name + "n"), lowpass(cut, 320, order=1), q=q, mode="bp")


# ─────────────────────────── what things are made of ───────────────────────────
# The layers a projectile is built from when it is a THING rather than a note:
# a flame is combustion, a frost shard is fracture, a toxic orb is liquid, a
# ghost is breath. Every one of these is noise-excited or harmonic. None of them
# is a pitched tone with an instant attack and an exponential decay, because
# that envelope on a pitched sound is, to the ear, a struck object — and a
# struck object with inharmonic partials is a tin can, which is what thirty
# primaries in this file used to be (see `bolt`).

def grains(dur, rate=40.0, band=(900, 7000), length=(0.0006, 0.004), q=(0.7, 1.6),
           level=(0.25, 1.0), density=None, name="gr"):
    """A Poisson cloud of very short noise grains — fire pops, ice fracturing,
    sparks, twigs snapping, gravel. Each grain is a millisecond or two of noise in
    its own band; the ear hears the cloud as combustion or fracture according to
    the band and the rate, never as a pitch.

    `density` (points for seg_env) shapes the rate over the sound, so a crackle
    can burst at ignition and thin out as it flies."""
    n = n_of(dur)
    out = np.zeros(n)
    rng = np.random.default_rng(seed_of(name))
    dens = seg_env(density, dur) if density is not None else np.ones(n)
    # thin a uniform Poisson stream by the density envelope
    t = 0.0
    while True:
        t += rng.exponential(1.0 / max(rate, 1e-3))
        if t >= dur:
            break
        if rng.uniform() > dens[min(n - 1, int(t * SR))]:
            continue
        gd = rng.uniform(*length)
        lo = np.exp(rng.uniform(np.log(band[0]), np.log(band[1])))
        g = noise(gd + 0.002, name + str(rng.integers(1 << 30)))
        g = bandpass(g, lo / rng.uniform(*q), lo * rng.uniform(*q)) if len(g) > 16 else g
        m = n_of(gd)
        g = g[:m] * (np.linspace(1.0, 0.0, m) ** 1.5)
        k = max(1, min(m // 3, int(0.0004 * SR)))          # a few samples to rise: a step is a click
        g[:k] *= np.linspace(0.0, 1.0, k, endpoint=False)
        at(out, t, g * rng.uniform(*level))
    peak = np.max(np.abs(out))
    return out / peak if peak > 1e-9 else out


def dust(dur, name="dst"):
    """Dry grit on the air — sand, a spade's earth, an old tomb: pink noise held
    in the low mids and falling away."""
    return bandpass(pink(dur, name), 200, 1700) * exp_env(dur, attack=0.004, decay=6.0)


def bubbles(dur, count=8, f=(260, 900), rise=(1.5, 3.0), size=(0.018, 0.05), span=(0.0, 0.9),
            name="bub"):
    """Bubbles forming and bursting: each one a sine whose pitch rises as it
    closes (the Minnaert resonance of a shrinking cavity). That upward chirp is the
    cue the ear takes as liquid — a bubble whose pitch fell would be a raindrop on
    a drum, and one that held its pitch would be a bell."""
    n = n_of(dur)
    out = np.zeros(n)
    rng = np.random.default_rng(seed_of(name))
    for _ in range(count):
        t0 = rng.uniform(dur * span[0], dur * span[1])
        bd = rng.uniform(*size)
        f0 = np.exp(rng.uniform(np.log(f[0]), np.log(f[1])))
        b = sine(seg_env([(0.0, f0), (1.0, f0 * rng.uniform(*rise))], bd), bd)
        at(out, t0, b * exp_env(bd, attack=0.002, decay=7.0) * rng.uniform(0.35, 1.0))
    peak = np.max(np.abs(out))
    return out / peak if peak > 1e-9 else out


def sizzle(dur, band=(2600, 9000), crackle=0.6, name="szl"):
    """Frying: a hiss bed broken up by tiny spits. Acid on stone, molten rock in
    air, a lit fuse. The spits are what separate it from wind."""
    n = n_of(dur)
    bed = bandpass(noise(dur, name + "b"), band[0], band[1])
    flut = lowpass(np.abs(noise(dur, name + "f")), 40, order=1)
    bed *= 0.45 + 1.4 * flut / (np.max(flut) + 1e-9)
    spits = grains(dur, rate=90, band=(band[0] * 0.8, band[1]), length=(0.0004, 0.0025),
                   name=name + "s")
    out = bed / (np.max(np.abs(bed)) + 1e-9) * 0.55 + spits * crackle
    return out / (np.max(np.abs(out)) + 1e-9)


def squelch(dur, f=(1300, 220), q=3.2, lumps=4, name="sq"):
    """A wet, sticky mass moving: low noise through a resonance that sweeps down
    (the 'shlorp'), with a few gluey pops where it pulls apart. Mud, flesh, slime."""
    n = n_of(dur)
    body = svf(pink(dur, name + "b"), seg_env([(0.0, f[0]), (0.35, f[0] * 0.55), (1.0, f[1])], dur),
               q=q, mode="bp")
    body *= seg_env([(0.0, 0.0), (0.05, 1.0), (0.4, 0.6), (1.0, 0.0)], dur)
    pops = bubbles(dur, count=lumps, f=(120, 380), rise=(1.3, 2.2), size=(0.02, 0.05),
                   span=(0.08, 0.8), name=name + "p")
    out = mixdown(body / (np.max(np.abs(body)) + 1e-9), pops * 0.45)
    return saturate(out, 1.6)


def breath(dur, vowels, band=(300, 6000), name="br"):
    """A whisper: noise through a moving vocal tract, no voice at all. Ghosts,
    curses, a djinn's smoke. Formants over noise read as a mouth; the same noise
    through a plain filter reads as wind."""
    src = bandpass(pink(dur, name + "n"), band[0], band[1])
    return tract(src, vowels, dur, q=(8.0, 7.0, 6.0), gains=(1.0, 0.8, 0.5), name=name)


def choir(freqs, dur, vowels=((0.0, "ah"), (1.0, "oh")), vib=5.2, breath_=0.45, depth=0.015, name="cho"):
    """A few voices singing a chord: harmonic sources (glottal pulses) through a
    moving tract, each with its own vibrato and a slow swell. Holy light is sung,
    not struck — a bell under every holy bolt is the steeple-sized tin can."""
    keys = list(vowels)
    out = np.zeros(n_of(dur))
    rng = np.random.default_rng(seed_of(name))
    for i, f in enumerate(freqs):
        ph = rng.uniform(0, 2 * np.pi)
        vr = vib * rng.uniform(0.9, 1.1)
        f_env = f * (1.0 + depth * np.sin(2 * np.pi * vr * t_axis(dur) + ph))
        src = glottal(f_env, dur, open_q=0.62, jitter=0.012, shimmer=0.08, name=name + str(i))
        v = tract(src, keys, dur, breath=breath_, name=name + "t" + str(i))
        out += v * (1.0 / (1.0 + 0.35 * i))
    return out / (np.max(np.abs(out)) + 1e-9)


def glow(f0, dur, voices=3, detune=0.011, cut=(5.5, 1.7), q=1.3, attack=0.012, decay=5.0,
         settle=1.06, vib=(5.5, 0.004), name="glw"):
    """The tonal heart of a magic bolt: detuned saws — every partial harmonic, so
    there is nothing to clang — opening fast and dimming through a sweeping
    lowpass as the bolt leaves. The attack is never shorter than ~6ms: a pitched
    tone that starts instantly and falls away exponentially is a struck object,
    whatever it was meant to be."""
    n = n_of(dur)
    rng = np.random.default_rng(seed_of(name))
    tt = t_axis(dur)
    pitch = seg_env([(0.0, f0 * settle), (min(0.4, 0.05 / dur), f0), (1.0, f0 * 0.985)], dur)
    out = np.zeros(n)
    for v in range(voices):
        off = (v - (voices - 1) / 2.0) * detune
        wob = 1.0 + vib[1] * np.sin(2 * np.pi * vib[0] * rng.uniform(0.85, 1.15) * tt + rng.uniform(0, 6.3))
        ph = np.cumsum(as_array(pitch * (1.0 + off) * wob, n)) / SR + rng.uniform()
        out += 2.0 * (ph % 1.0) - 1.0
    out /= voices
    out = svf(out, seg_env([(0.0, f0 * cut[0]), (0.3, f0 * (cut[0] + cut[1]) * 0.5), (1.0, f0 * cut[1])], dur),
              q=q)
    a = min(0.9, max(0.006, attack) / dur)
    env = seg_env([(0.0, 0.0), (a, 1.0), (1.0, 1.0)], dur) * np.exp(-decay * tt)
    return out * env / (np.max(np.abs(out * env)) + 1e-9)


def pluck(f0, dur, t60=0.6, bright=0.55, pick=0.3, name="pk"):
    """Karplus-Strong: a burst of noise circulating in a delay line one period
    long, averaged a little on every pass. Every partial is an exact harmonic and
    the high ones die first, which is precisely what a string is and what a
    struck bar or a can is not. Bowstrings, lutes, harps, puppet strings."""
    n = n_of(dur)
    L = max(2, int(round(SR / f0)))
    rng = np.random.default_rng(seed_of(name))
    exc = rng.uniform(-1.0, 1.0, L)
    # brightness: how hard it was plucked (a soft excitation is a darker string)
    k = max(1, int((1.0 - bright) * 8))
    exc = np.convolve(exc, np.ones(k) / k, mode="same")
    # pick position: a comb on the excitation, as plucking a third of the way along
    # removes every third harmonic
    d = max(1, int(pick * L))
    exc = exc - np.roll(exc, d)
    g = 0.001 ** (1.0 / max(1.0, t60 * f0))
    y = np.zeros(n + 1)
    y[1:min(n, L) + 1] = exc[:min(n, L)]
    a = 0.5 * g
    for start in range(L + 1, n + 1, L):
        end = min(n + 1, start + L)
        idx = np.arange(start, end)
        y[idx] += a * (y[idx - L] + y[idx - L - 1])
    out = y[1:]
    return out / (np.max(np.abs(out)) + 1e-9)


def rattle(dur, count=26, band=(2400, 9000), q=9.0, cluster=0.7, name="rat"):
    """Links of a chain catching each other: dozens of clinks, each a few
    milliseconds of noise through a narrow band at its own random pitch. Short
    and many, it is a chain; long and few, it is a bucket — which is what three
    tuned iron strikes used to be."""
    n = n_of(dur)
    out = np.zeros(n)
    rng = np.random.default_rng(seed_of(name))
    t = rng.uniform(0.0, 0.01)
    # Each link is a dull little knock: a broad band (never narrower than a fifth of its
    # centre), nothing over 6kHz, a millisecond to speak. Narrow and bright (q 8-16, up to
    # 10kHz, 0.3ms), as they were, each one had a pitch, and a pitch that lasts 5ms is a clink:
    # a chain sounded like cutlery
    q = min(q, 4.5)
    top = min(band[1], 6000.0)
    for _ in range(count):
        gd = rng.uniform(0.005, 0.013)
        f = np.exp(rng.uniform(np.log(min(band[0], top * 0.6)), np.log(top)))
        g = res_bp(noise(gd + 0.004, name + str(rng.integers(1 << 30))), f, q=q)[:n_of(gd)]
        g *= decay_env(gd, attack=0.001)
        at(out, t, g * rng.uniform(0.3, 1.0))
        # links come in clusters as the chain snakes: short gaps inside, longer between
        t += rng.exponential(dur / count * (0.35 if rng.uniform() < cluster else 1.6))
        if t >= dur:
            break
    peak = np.max(np.abs(out))
    return out / peak if peak > 1e-9 else out


def rip(dur, rate=(160.0, 40.0), band=(400, 3800), name="rip"):
    """Tearing: a run of micro-bursts that slows as the tear finishes — cloth,
    hide, flesh parting under a claw. The run is the whole cue; a claw that only
    whooshes has missed."""
    n = n_of(dur)
    out = np.zeros(n)
    rng = np.random.default_rng(seed_of(name))
    rt = seg_env([(0.0, rate[0]), (1.0, rate[1])], dur)
    t = 0.0
    while t < dur:
        i = min(n - 1, int(t * SR))
        gd = rng.uniform(0.003, 0.008)
        g = bandpass(noise(gd + 0.003, name + str(rng.integers(1 << 30))), band[0], min(band[1], 3600.0))[:n_of(gd)]
        g *= np.linspace(1.0, 0.1, len(g))
        k = max(1, min(len(g) // 3, int(0.0006 * SR)))     # each fibre gives; it doesn't click
        g[:k] *= np.linspace(0.0, 1.0, k, endpoint=False)
        at(out, t, g * rng.uniform(0.4, 1.0) * (1.0 - 0.6 * t / dur))
        t += rng.uniform(0.5, 1.5) / rt[i]
    peak = np.max(np.abs(out))
    return out / peak if peak > 1e-9 else out


def crack(dur=0.03, name="crk"):
    """A whip or a sonic crack: an N-wave (a sharp pressure spike and its
    rebound, under a millisecond each) with a spray of broadband noise. Louder and
    shorter than any contact, and it has no body at all."""
    n = n_of(dur)
    out = np.zeros(n)
    w = n_of(0.0007)
    out[:w] = np.linspace(1.0, -1.0, w)
    out[w:2 * w] = np.linspace(-1.0, 0.0, w)
    spray = highpass(noise(dur, name), 1500) * (np.linspace(1.0, 0.0, n) ** 3.0)
    out = out * 0.9 + spray / (np.max(np.abs(spray)) + 1e-9) * 0.6
    return out / (np.max(np.abs(out)) + 1e-9)


def buzz(dur, rate=190.0, band=(1200, 6500), bugs=4, name="bz"):
    """A swarm: several wingbeat buzzes, each noise gated at its own rate a little
    apart from the others, so they beat. Insects, nanites, flies over a corpse."""
    n = n_of(dur)
    out = np.zeros(n)
    rng = np.random.default_rng(seed_of(name))
    tt = t_axis(dur)
    for b in range(bugs):
        r = rate * rng.uniform(0.8, 1.25)
        drift = 1.0 + 0.06 * np.sin(2 * np.pi * rng.uniform(1.5, 4.0) * tt + rng.uniform(0, 6.3))
        gate = (0.5 + 0.5 * np.sin(2 * np.pi * np.cumsum(np.full(n, r) * drift) / SR)) ** 3
        src = bandpass(noise(dur, name + str(b)), band[0] * rng.uniform(0.8, 1.2), band[1])
        out += src * gate * rng.uniform(0.5, 1.0)
    return out / (np.max(np.abs(out)) + 1e-9)


def scrape(dur, lo=2600, hi=6200, bands=3, q=5.0, name="scr"):
    """An edge sliding along an edge — the 'shing' of a drawn blade. Continuous
    noise through a few resonances that GLIDE as the contact point moves. It is
    the gliding that makes it steel on steel: the same peaks held still, excited
    once, are a struck bar."""
    n = n_of(dur)
    out = np.zeros(n)
    rng = np.random.default_rng(seed_of(name))
    src = noise(dur, name + "n")
    for b in range(bands):
        f0 = lo * rng.uniform(0.9, 1.3) * (1.0 + 0.5 * b)
        f1 = min(hi * (1.0 + 0.3 * b), 12000.0)
        out += svf(src, seg_env([(0.0, f0), (1.0, f1)], dur), q=q, mode="bp") * (1.0 / (1 + b))
    out *= seg_env([(0.0, 0.0), (0.12, 1.0), (0.5, 0.6), (1.0, 0.0)], dur)
    return out / (np.max(np.abs(out)) + 1e-9)


def heartbeat(gap=0.13, dur=0.34, f0=58.0, name="hb"):
    """Lub-dub: two soft, low thuds, the second quieter. No overtones, no click."""
    out = np.zeros(n_of(dur))
    at(out, 0.0, thud(f0, 0.16, drive=1.3, name=name + "1"))
    at(out, gap, thud(f0 * 0.9, 0.16, drive=1.3, name=name + "2") * 0.62)
    return out


def whump(dur=0.2, f=(210.0, 55.0), name="wh"):
    """A pressure bloom — gas catching, a mass of air shoved aside, a cloud going up. Low-passed
    noise whose cutoff falls, and the push of it under that.

    The push is `weight`: it used to be a sine falling two octaves in the time, and a sine that
    falls two octaves as it dies is a kick drum, under every flame and every cloud in the game."""
    body = svf(pink(dur, name + "p"), glide([(0.0, f[0] * 6), (0.25, f[0] * 2.4), (1.0, f[1] * 2)], dur),
               q=0.9, mode="lp")
    body *= exp_env(dur, attack=0.005, decay=5.5)
    push = weight(max(60.0, f[1] * 1.35), dur, name=name + "w")
    out = body / (np.max(np.abs(body)) + 1e-9) + push * 0.42
    return out / (np.max(np.abs(out)) + 1e-9)


def flange(x, lo_ms=0.5, hi_ms=5.0, depth=0.8, rise=True):
    """A comb whose teeth sweep: the phasing 'jet' of energy streaking past. The
    teeth never stand still, so it never settles into the fixed resonances a
    static comb (or a can) has."""
    n = len(x)
    d = seg_env([(0.0, hi_ms if not rise else lo_ms), (1.0, lo_ms if not rise else hi_ms)], n / SR)
    idx = np.arange(n, dtype=float)
    return x + depth * np.interp(idx - d * 0.001 * SR, idx, x)


# ─────────────────────────── shaping & space ───────────────────────────

def saturate(x, drive=1.6):
    return np.tanh(x * drive) / np.tanh(drive)


def bitcrush(x, bits=5, hold=6):
    levels = 2 ** bits
    q = np.round(x * levels) / levels
    if hold > 1:
        n = len(q)
        idx = (np.arange(n) // hold) * hold
        q = q[np.minimum(idx, n - 1)]
    return q


def fft_convolve(x, h):
    n = len(x) + len(h) - 1
    nfft = 1 << (n - 1).bit_length()
    return np.fft.irfft(np.fft.rfft(x, nfft) * np.fft.rfft(h, nfft), nfft)[:n]


_IR_CACHE = {}


def _impulse_response(decay, damp, name, predelay=0.0, size=1.0):
    key = (round(decay, 3), round(damp, 1), name, round(predelay, 4), round(size, 2))
    if key in _IR_CACHE:
        return _IR_CACHE[key]
    n = n_of(decay)
    ir = np.random.default_rng(seed_of("ir" + str(key))).normal(0, 1, n)
    ir *= np.exp(-np.linspace(0.0, 7.0, n))
    ir = lowpass(ir, damp, order=1)
    # sparse early reflections give the space a size; scaling their delays is
    # what makes a corridor sound different from an arena
    for tap, g in ((0.011, 0.5), (0.019, -0.38), (0.031, 0.3), (0.047, -0.22)):
        i = int(tap * size * SR)
        if i < n:
            ir[i] += g
    if predelay > 0:
        ir = np.concatenate([np.zeros(n_of(predelay)), ir])
    ir /= np.sqrt(np.sum(ir ** 2)) + 1e-9
    _IR_CACHE[key] = ir
    return ir


# The generator-stage reverb only places a sound in the arena; the master send
# does the actual space. Two sends at full wet in series is what turned every
# envelope in the game into a rectangle — a bolt that decays to -44dB by 250ms
# dry came out at -20dB, so nothing had a shape and everything sounded the same.
GEN_REVERB = 0.42


def reverb(x, decay=0.6, damp=5000.0, mix=0.25, name="room", predelay=0.0, size=1.0):
    mix = mix * GEN_REVERB
    if mix <= 0:
        return x
    ir = _impulse_response(decay, damp, name, predelay, size)
    wet = fft_convolve(x, ir)
    out = np.zeros(len(wet))
    out[:len(x)] += x * (1.0 - mix)
    out += wet * mix * 1.3
    return out


def env_follow(x, attack_ms=3.0, release_ms=80.0):
    """Asymmetric envelope follower — fast attack / slow release is what gives punch."""
    n = len(x)
    a = float(np.exp(-1.0 / max(1.0, attack_ms * 0.001 * SR)))
    r = float(np.exp(-1.0 / max(1.0, release_ms * 0.001 * SR)))
    out = [0.0] * n
    e = 0.0
    for i, m in enumerate(np.abs(x).tolist()):
        c = a if m > e else r
        e = m + c * (e - m)
        out[i] = e
    return np.array(out)


def compress(x, thresh=0.22, ratio=4.0, attack_ms=2.0, release_ms=90.0, makeup=None):
    """Soft-knee compressor. Raises density so sounds punch at the same peak level."""
    env = env_follow(x, attack_ms, release_ms) + 1e-9
    over = np.maximum(env / thresh, 1.0)
    gain = over ** (1.0 / ratio - 1.0)
    y = x * gain
    if makeup is None:
        makeup = (1.0 / thresh) ** (1.0 - 1.0 / ratio) ** 1.0
        makeup = min(makeup, 3.0)
    return y * makeup


def transient_shape(x, boost=1.2, fast_ms=1.5, slow_ms=45.0):
    """Attack enhancer: lifts the first few milliseconds of a sound — its leading edge, which
    reads as 'snap' — by up to `boost` x 3dB, and nothing else in it.

    It used to lift every edge in the sound, by how far it stood over the level just before it and
    up to seven times: so each grain of a crackle, each chime of a glitter, each link of a chain
    that came after a quiet moment was thrown forward by 14dB or more, and a tail of soft detail
    became a row of clicks. Whatever a sound's detail is, it now stays where it was mixed."""
    if boost <= 0:
        return x
    mag = np.abs(x)
    peak = np.max(mag) if len(mag) else 0.0
    if peak <= 1e-9:
        return x
    onset = int(np.argmax(mag > 0.1 * peak))
    tt = (np.arange(len(x)) - onset) / SR
    lift = 10.0 ** (boost * 3.0 / 20.0) - 1.0
    gain = 1.0 + lift * np.where(tt >= 0, np.exp(-np.maximum(tt, 0.0) / 0.008), np.exp(np.minimum(tt, 0.0) / 0.002))
    return x * gain


def sub_boost(x, freq=48.0, amount=0.5, decay=5.0, name="sub"):
    """The weight under an impact, so it feels physical: a low push an octave above `freq`, with
    the harmonics that carry it on a speaker with no bass.

    This was a sine falling from 2.6x `freq` to 0.8x of it (125Hz to 37Hz for the usual 46), and
    most of it lay under 60Hz: a third of the whole set's energy was below 120Hz, in forty sounds
    more than 60% of it, and on a laptop, where nothing under 150Hz is played, a slam was a faint
    hiss. On headphones it was every impact as the same boom. The push is now at 1.7x `freq`,
    falls a fifth and no further, and is driven hard enough that its third harmonic says where it
    is to a speaker that can't play the fundamental."""
    # ...and it is over in a quarter of a second or so, however long the sound: a push, not a hum
    # held under the whole of it
    dur = min(len(x) / SR, 0.16 + 0.22 * min(1.0, len(x) / SR))
    s = sine(glide([(0.0, freq * 2.3), (0.12, freq * 1.7), (1.0, freq * 1.45)], dur), dur)
    s *= exp_env(dur, attack=0.004, decay=decay * 1.5)
    s = saturate(s, 3.0)
    if len(s) < len(x):
        s = np.pad(s, (0, len(x) - len(s)))
    return x + s[: len(x)] * amount * 0.6


def presence_dip(x, fc=3200.0, gain_db=-3.5, q=0.75):
    """A wide cut where the ear is most sensitive.

    Fletcher-Munson puts our sensitivity peak around 3-4kHz, so a set of 150
    attack sounds that all pile energy there is physically tiring inside a
    minute — that, more than any individual sound, is what made the old set
    read as shrill. A broad -3.5dB bell keeps every sound's identity (it is far
    too wide to remove a formant) while taking the sting out of a firefight."""
    return eq(x, "peak", fc, q=q, gain_db=gain_db)


def air_tame(x, fc=9000.0, gain_db=-7.0):
    """Top shelf, and not a gentle one.

    Spectral FOCUS is most of what separates one sound from another. Summing
    four or five layers and saturating the result fills the spectrum from 60Hz
    to 16kHz on every sound in the set, and once they all occupy the whole
    range they are all the same sound with a different envelope — which is
    exactly what "generic" means. Real objects are band-limited; sounds that
    have earned their air (ice, glass, sparkle) ask for it per-sound."""
    return eq(x, "highshelf", fc, q=0.7, gain_db=gain_db)


def _soft_knee(y, knee=0.8):
    """Round off only what is above the knee. Running the whole signal through
    tanh (which is what this used to do) costs several dB of crest factor on
    every sound — it is the difference between an anvil that clangs and an anvil
    that thuds."""
    a = np.abs(y)
    hot = a > knee
    if not np.any(hot):
        return y
    y = y.copy()
    y[hot] = np.sign(y[hot]) * (knee + np.tanh((a[hot] - knee) * 4.0) * (1.0 - knee))
    return y


def limit(x, ceiling=0.985, lookahead_ms=1.5):
    """Soft limiter — catches peaks without the flat-topping that hard clipping causes."""
    env = env_follow(x, 0.15, 30.0)
    over = np.maximum(env / ceiling, 1.0)
    gain = 1.0 / over
    k = max(1, int(lookahead_ms * 0.001 * SR))
    gain = np.minimum(gain, np.roll(gain, -k))
    gain = lowpass(gain, 3000, order=1)
    return _soft_knee(x * np.clip(gain, 0.05, 1.0))


def chorus(x, rate=0.7, depth_ms=7.0, voices=3, mix=0.4, name="ch"):
    """Modulated fractional delays — thickens and adds movement."""
    n = len(x)
    idx = np.arange(n, dtype=float)
    out = x * (1.0 - mix * 0.5)
    rng = np.random.default_rng(seed_of(name))
    for v in range(voices):
        ph = rng.uniform(0, 2 * np.pi)
        lfo = (depth_ms * 0.001 * SR) * (0.5 + 0.5 * np.sin(2 * np.pi * rate * idx / SR + ph))
        out += np.interp(idx - lfo - 4.0, idx, x) * (mix / voices)
    return out


def delay_fx(x, time_s=0.16, feedback=0.35, mix=0.3, taps=6):
    step = int(time_s * SR)
    if step <= 0:
        return x
    out = np.zeros(len(x) + step * taps)
    out[:len(x)] += x
    g = feedback
    for i in range(1, taps + 1):
        start = step * i
        out[start:start + len(x)] += x * mix * g
        g *= feedback
    return out


def mixdown(*parts):
    n = max(len(p) for p in parts)
    out = np.zeros(n)
    for p in parts:
        out[:len(p)] += p
    return out


def at(buf, start_sec, sig, gain=1.0):
    start = int(start_sec * SR)
    if start >= len(buf):
        return
    end = min(len(buf), start + len(sig))
    buf[start:end] += sig[:end - start] * gain


def pad_to(x, dur):
    n = n_of(dur)
    return np.pad(x, (0, max(0, n - len(x))))[:n]


def fit(x, n):
    """Pad or truncate to exactly n samples. Converting seconds to samples and
    back is not round-trip exact, so two layers of the same nominal duration can
    differ by a sample; this is where that gets absorbed."""
    if len(x) == n:
        return x
    return x[:n] if len(x) > n else np.pad(x, (0, n - len(x)))


# ─────────────────────────── stereo ───────────────────────────

def is_stereo(x):
    return np.asarray(x).ndim == 2


def to_stereo(x):
    x = np.asarray(x, dtype=float)
    return x if x.ndim == 2 else np.stack([x, x], axis=1)


def stereoize(x, width=0.65, name="w"):
    """Mono -> wide stereo via decorrelated early taps + mid/side width.

    Decorrelation (rather than plain duplication) is what makes a sound occupy
    space instead of sitting as a point in the middle of the head.
    """
    n = len(x)
    l = x.astype(float).copy()
    r = x.astype(float).copy()
    rng = np.random.default_rng(seed_of(name))
    for ch, sign in ((l, 1.0), (r, -1.0)):
        for _ in range(3):
            d = int(rng.uniform(0.0006, 0.009) * SR)
            if d < n:
                ch[d:] += x[: n - d] * rng.uniform(0.06, 0.15) * sign
    mid = (l + r) * 0.5
    side = (l - r) * 0.5 * (width * 2.0)
    return np.stack([mid + side, mid - side], axis=1)


def stereo_reverb(st, decay=0.7, damp=5000.0, mix=0.18, name="sv", predelay=0.0, size=1.0):
    """True-stereo reverb: a different impulse response per channel."""
    if mix <= 0:
        return st
    il = _impulse_response(decay, damp, name + "L", predelay, size)
    ir = _impulse_response(decay, damp * 0.92, name + "R", predelay * 1.15, size * 1.07)
    wl = fft_convolve(st[:, 0], il)
    wr = fft_convolve(st[:, 1], ir)
    n = len(wl)
    out = np.zeros((n, 2))
    out[: len(st), 0] += st[:, 0] * (1.0 - mix)
    out[: len(st), 1] += st[:, 1] * (1.0 - mix)
    out[:, 0] += wl * mix * 1.3
    out[:, 1] += wr * mix * 1.3
    return out


def stereo_glue(st, thresh=0.28, ratio=2.6, attack_ms=9.0, release_ms=150.0):
    """Bus compression for the music: ONE linked gain across both channels, so
    the stereo image stays put, slow enough to leave the drum transients alone
    while lifting everything between them.

    The music used to get its density from the master limiter's global tanh.
    Replacing that with a knee (which is right — it was flattening every sound
    effect in the game) took ~3dB of loudness off both tracks, so the density
    is now made here, where it belongs, instead of as clipping."""
    linked = np.max(np.abs(st), axis=1)
    env = env_follow(linked, attack_ms, release_ms) + 1e-9
    over = np.maximum(env / thresh, 1.0)
    gain = over ** (1.0 / ratio - 1.0)
    makeup = min((1.0 / thresh) ** (1.0 - 1.0 / ratio), 2.5)
    return (st.T * (gain * makeup)).T


def auto_pan(st, start=-0.7, end=0.7, curve=1.0):
    """Sweeps the image across the field — used for whooshes and passing shots."""
    n = len(st)
    p = np.linspace(0.0, 1.0, n) ** curve
    pan = start + (end - start) * p
    lg = np.sqrt(np.clip((1.0 - pan) * 0.5, 0.0, 1.0))
    rg = np.sqrt(np.clip((1.0 + pan) * 0.5, 0.0, 1.0))
    out = st.copy()
    out[:, 0] *= lg * 1.35
    out[:, 1] *= rg * 1.35
    return out


def ping_pong(x, time_s=0.18, feedback=0.42, mix=0.32, taps=6):
    """Stereo delay that bounces L/R — instant depth on magic/void/teleport sounds."""
    st = to_stereo(x) if not is_stereo(x) else x.copy()
    step = int(time_s * SR)
    n = len(st) + step * taps
    out = np.zeros((n, 2))
    out[: len(st)] += st
    g = feedback
    for i in range(1, taps + 1):
        s = step * i
        ch = i % 2
        out[s: s + len(st), ch] += st[:, ch] * mix * g * 1.5
        out[s: s + len(st), 1 - ch] += st[:, 1 - ch] * mix * g * 0.35
        g *= feedback
    return out


def pan_mono(x, pan=0.0):
    """Constant-power pan of a mono signal into a stereo pair."""
    a = (pan + 1.0) * 0.25 * np.pi
    return np.stack([x * np.cos(a) * 1.25, x * np.sin(a) * 1.25], axis=1)


def at_st(buf, t, sig, gain=1.0, pan=0.0):
    s = sig if is_stereo(sig) else pan_mono(sig, pan)
    start = int(t * SR)
    if start >= len(buf):
        return
    end = min(len(buf), start + len(s))
    buf[start:end] += s[: end - start] * gain


# ─────────────────────────── output ───────────────────────────

def trim_tail(x, thresh=1.4e-2, max_dur=2.2):
    """Cut the inaudible end of the reverb tail (-37dB under the sound's peak) and cap total
    length.

    Long quiet tails cost file size (and the client's memory: every sound is held decoded) but
    contribute nothing under gameplay, where they sit far below the music and the next sound
    effect. The cut was at -52dB, and most sounds ran to their class's cap on a tail nobody could
    hear: a third of the set's bytes.
    """
    if len(x) == 0:
        return x
    mag = np.max(np.abs(x), axis=1) if is_stereo(x) else np.abs(x)
    peak = np.max(mag)
    if peak <= 0:
        return x
    idx = np.nonzero(mag > thresh * peak)[0]
    end = len(x) if len(idx) == 0 else min(len(x), idx[-1] + int(0.045 * SR))
    end = min(end, n_of(max_dur))
    return x[:end]


def fade_edges(x, fade_in=0.0005, fade_out=0.04):
    """Tiny edge fades to avoid DC-step clicks. fade_in is deliberately sub-millisecond —
    a longer one would scale down the attack transient, which is the punch."""
    n = len(x)
    a = min(n // 2, n_of(fade_in))
    b = min(n // 2, n_of(fade_out))
    if a > 1:
        ramp = np.linspace(0, 1, a)
        x[:a] = (x[:a].T * ramp).T
    if b > 1:
        ramp = np.linspace(1, 0, b)
        x[-b:] = (x[-b:].T * ramp).T
    return x


def limit_stereo(st):
    linked = np.max(np.abs(st), axis=1)
    env = env_follow(linked, 0.4, 60.0)
    over = np.maximum(env / 0.92, 1.0)
    gain = lowpass(1.0 / over, 2200, order=1)
    gain = np.clip(gain, 0.05, 1.0)
    return _soft_knee((st.T * gain).T, knee=0.82)


def master(x, width=0.65, transient=1.0, comp=(0.30, 2.8), sub=None,
           tail=(0.7, 0.18), pan=None, pingpong=None, name="s",
           dip=(3200.0, -3.5), air=(11000.0, -3.0), predelay=0.0, room=1.0, glue=None):
    """Shared finishing chain: punch in mono, then tone, then space and width.

    The tone stage (`dip` / `air`) runs before the stereo spread so both channels
    get the identical curve — EQ'ing after decorrelation would smear the image."""
    if not is_stereo(x):
        if transient > 0:
            x = transient_shape(x, boost=transient)
        if comp is not None:
            x = compress(x, thresh=comp[0], ratio=comp[1])
        if sub is not None:
            x = sub_boost(x, freq=sub[0], amount=sub[1], name=name + "sub")
        if dip is not None:
            x = presence_dip(x, fc=dip[0], gain_db=dip[1])
        if air is not None:
            x = air_tame(x, fc=air[0], gain_db=air[1])
        st = stereoize(x, width=width, name=name)
    else:
        st = x
    if pingpong is not None:
        st = ping_pong(st, time_s=pingpong[0], feedback=pingpong[1], mix=pingpong[2])
    if tail is not None:
        st = stereo_reverb(st, decay=tail[0], damp=5200, mix=tail[1], name=name,
                           predelay=predelay, size=room)
    if pan is not None:
        st = auto_pan(st, pan[0], pan[1])
    if glue is not None:
        st = stereo_glue(st, thresh=glue[0], ratio=glue[1])
    return st


# The contour each class has to fit inside: flat for `hold` seconds, then down
# to -60dB over `t60`. This is a ceiling, not a multiply — anything already
# decaying faster is untouched, anything that is not gets ducked onto the
# curve. It exists because the ear reads SHAPE first: a sound that holds level
# for its whole length is heard as generic texture however well synthesized the
# texture is, which is what a wall of flat envelopes in the gallery looked like.
SHAPES = dict(tiny=(0.03, 0.22), bolt=(0.05, 0.30), melee=(0.07, 0.38),
              ability=(0.16, 0.70), heavy=(0.25, 0.90), event=(0.30, 1.10),
              drone=(0.50, 1.10))


def enforce_shape(st, clas, floor_db=-42.0):
    hold, t60 = SHAPES.get(clas, SHAPES["ability"])
    n = len(st)
    tt = np.arange(n) / SR
    ceil = np.minimum(1.0, np.exp(-6.9 * np.maximum(0.0, tt - hold) / t60))
    env = env_follow(np.max(np.abs(st), axis=1), 1.0, 40.0) + 1e-9
    peak = np.max(env)
    gain = np.minimum(1.0, ceil * peak / env)
    # never gate outright — a tail that ducks to silence sounds edited
    gain = np.maximum(gain, 10.0 ** (floor_db / 20.0))
    gain = lowpass(gain, 60.0, order=1)
    return (st.T * np.clip(gain, 0.0, 1.0)).T


# How long each class of sound is allowed to be. SHOOT_COOLDOWN_MS is 500, so a
# primary attack whose tail runs past ~0.55s is still sounding when its own next
# shot fires; stacked over a firefight that is the difference between a set of
# distinct attacks and undifferentiated mush. (`generate_sounds.py` holds every primary to
# `bolt`, `melee` or `tiny`, and to no held contour: a boulder thrown twice a second was a
# second and a quarter long, three of them sounding at once.)
MAX_DUR = dict(bolt=0.55, melee=0.6, ability=0.9, heavy=1.25, event=1.6, tiny=0.25)


def heard_level(st):
    """How loud a sound is, in dB: the loudest tenth of a second of it, weighted as it is heard
    — nothing counted under 120Hz (most of what a game is played on doesn't play it, and the
    rest plays it quietly), the presence band counted a little more. Two sounds at the same
    figure sound as loud as each other; two at the same PEAK do not, by 10dB and more — a click
    and a roar — which is what levelling by peak, as this file did, made of the set."""
    out = 0.0
    for c in range(st.shape[1]):
        x = st[:, c]
        n = len(x)
        f = np.maximum(np.fft.rfftfreq(n, 1.0 / SR), 1e-3)
        hp = 1.0 / np.sqrt(1.0 + (120.0 / f) ** 4)
        shelf = 1.0 + (10 ** (3.0 / 20) - 1.0) / np.sqrt(1.0 + (1500.0 / f) ** 4)
        k = np.fft.irfft(np.fft.rfft(x) * hp * shelf, n)
        w = min(n, int(0.1 * SR))
        c2 = np.cumsum(np.concatenate([[0.0], k * k]))
        out += (c2[w:] - c2[:-w]).max() / w
    return 10 * np.log10(out + 1e-12)


# How much of a sound may lie under 120Hz, by class. Past it, the bottom is turned down until it
# doesn't: a guard, since a sound mixed by adding layers ends up with as much bass as its layers
# happen to have, and a speaker that can't play it leaves a hole where the sound was.
SUB_LIMIT = dict(tiny=0.25, bolt=0.32, melee=0.38, ability=0.42, heavy=0.5, event=0.5)


def tame_sub(st, clas):
    limit = SUB_LIMIT.get(clas, 0.42)
    mono = st.mean(axis=1)
    spec = np.abs(np.fft.rfft(mono)) ** 2
    f = np.fft.rfftfreq(len(mono), 1.0 / SR)
    share = spec[f < 120.0].sum() / (spec.sum() + 1e-18)
    if share <= limit:
        return st
    cut_db = 10 * np.log10(limit * (1.0 - share) / (share * (1.0 - limit)))
    cut_db = max(-14.0, cut_db * 1.15)          # the shelf's skirt reaches a little above 120Hz
    return np.stack([eq(st[:, 0], "lowshelf", 125.0, q=0.7, gain_db=cut_db),
                     eq(st[:, 1], "lowshelf", 125.0, q=0.7, gain_db=cut_db)], axis=1)


# `level` 0.6 is this loud (heard_level), and every other level is as far from it as its ratio to
# 0.6: the levels in the tables keep the meaning they had (a thrown card is quiet, thunder is
# loud), but now say how loud a sound is rather than how high its tallest sample
LEVEL_REF, LEVEL_REF_DB = 0.6, -13.0     # where the set sat, by ear, when it was levelled by peak
PEAK_CEILING = 0.97


def save_wav(name, signal, level=0.85, trim=True, clas="bolt", shape=None, by="loudness", **master_kw):
    x = np.nan_to_num(np.asarray(signal, dtype=float))
    x = master(x, name=name, **master_kw)
    # DC blocker rather than a mean subtraction: asymmetric saturation and the
    # limiter knee leave a small offset that a single mean does not remove, and
    # an offset costs headroom for nothing.
    # ...and nothing under 30Hz at all: no speaker a player has plays it, and it takes headroom from
    # what they do hear
    if by == "peak":
        x = np.stack([highpass(x[:, 0], 18.0, order=1), highpass(x[:, 1], 18.0, order=1)], axis=1)   # the music keeps its bottom
    else:
        x = np.stack([highpass(x[:, 0], 30.0, order=2), highpass(x[:, 1], 30.0, order=2)], axis=1)
    if shape is not False:
        x = enforce_shape(x, shape if isinstance(shape, str) else clas)
    if trim:
        x = trim_tail(x, max_dur=MAX_DUR.get(clas, 1.2))
    if by != "peak" and len(x) > 64:
        x = tame_sub(x, clas)
    peak = np.max(np.abs(x)) if len(x) else 0.0
    if by == "peak":
        # the music: already mixed, levelled by its peak
        if peak > 1e-9:
            x = x / peak * min(level * 1.06, 0.995)
        x = limit_stereo(x)
        x = fade_edges(x.copy(), fade_out=0.012)     # a loop's ends, as they were made
        peak = np.max(np.abs(x))
        if peak > 1e-9:
            x = x / peak * level
    elif peak > 1e-9:
        # to the loudness its level names; what that puts over the ceiling the limiter rounds
        # off, up to 4dB of it (a sound that would need more is left quieter instead: it is all
        # peak, and squashing it flat to make it loud is how a gunshot turns into a cough)
        x = x / peak
        want = LEVEL_REF_DB + 20 * np.log10(level / LEVEL_REF)
        gain = 10 ** ((want - heard_level(x)) / 20.0)
        gain = min(gain, PEAK_CEILING * 10 ** (4.0 / 20.0))
        x = limit_stereo(x * gain)
        x = fade_edges(x.copy())
        peak = np.max(np.abs(x))
        if peak > PEAK_CEILING:
            x = x / peak * PEAK_CEILING
    inter = np.clip(x, -1.0, 1.0).reshape(-1)
    pcm = (inter * 32767).astype(np.int16)
    path = os.path.join(OUT_DIR, name + ".wav")
    with wave.open(path, "wb") as f:
        f.setnchannels(2)
        f.setsampwidth(2)
        f.setframerate(SR)
        f.writeframes(pcm.tobytes())
    return path


# ═══════════════════════════ gestures ═══════════════════════════
# What the first two passes over this set left it without. Every sound in it was honest about what
# it was made of (the second pass) and none rang like a struck can (the first), and it still read
# as "silly and basic", because of how the pieces MOVED:
#
#   * bare oscillators doing the gesture — a sine sliding down under every bolt (a kick drum), FM
#     zips for a teleport (a cartoon), a dozen sine bleeps for "sparkle", sine LFOs and square
#     gates for wings and flutter (a helicopter). A naked oscillator is the one thing nothing in
#     the world sounds like, and the ear picks it out at once;
#   * one layer, one envelope — most sounds were a single cloud of filtered noise with a single
#     decay. Measured (the share of a sound's spectrogram that one spectrum times one envelope
#     explains), the set's median was 0.86. A designed sound is a transient, a body and a tail that
#     each move their own way;
#   * the weight all under 120Hz, where a laptop plays nothing.
#
# So: frequencies move on curves the ear hears as even (`glide`), whatever wobbles wobbles
# irregularly (`nlfo`), moving air passes by (`whoosh`, `varispeed`), a tone is air made to sing
# (`halo`) or a stack of voices (`supersaw`) and never one oscillator, glitter is a cloud of chimes
# (`shimmer`), weight is a push with harmonics (`weight`), and a beast's voice is a rough throat
# (`roar`, `screech`), not a vowel sung by a machine.

def norm(x):
    """Scale to a peak of 1 (silence stays silence)."""
    x = np.asarray(x, dtype=float)
    peak = np.max(np.abs(x)) if len(x) else 0.0
    return x / peak if peak > 1e-9 else x


def glide(points, dur):
    """A frequency curve through [(time_frac, hz), ...], straight in LOG frequency: equal times
    for equal musical intervals, which is how the ear hears pitch and how anything real changes
    it. `seg_env` on a frequency is straight in Hz, so a fall of two octaves spends half its time
    on the top one and drops through the bottom one: the synth-sweep sound."""
    n = n_of(dur)
    xs = np.array([p[0] for p in points]) * (n - 1)
    ys = np.log(np.maximum(1e-3, np.array([p[1] for p in points], dtype=float)))
    return np.exp(np.interp(np.arange(n), xs, ys))


def nlfo(dur, rate=12.0, lo=0.0, hi=1.0, name="lfo"):
    """A slow random wobble between `lo` and `hi`, with nothing in it faster than `rate` Hz. For
    everything that flutters, gusts or wavers: a sine LFO is a siren and a square gate is a
    helicopter, and both say 'machine' in a cycle and a half."""
    n = n_of(dur)
    w = np.random.default_rng(seed_of(name + "nlfo")).normal(0.0, 1.0, n + 64)
    w = lowpass(w, max(0.5, rate), order=2)[32:32 + n]
    a, b = np.percentile(w, 3), np.percentile(w, 97)
    w = np.clip((w - a) / (b - a + 1e-12), 0.0, 1.0)
    return lo + (hi - lo) * w


def arc(dur, peak=0.3, rise=1.5, fall=2.5):
    """The loudness of something passing: up to 1 at `peak` (a fraction of the sound) and away.
    `rise` over 1 swells in late, `fall` over 1 drops away early."""
    t = np.linspace(0.0, 1.0, n_of(dur))
    up = np.clip(t / max(peak, 1e-4), 0.0, 1.0) ** rise
    down = np.clip((1.0 - t) / max(1.0 - peak, 1e-4), 0.0, 1.0) ** fall
    return np.where(t < peak, up, down)


def swell_in(dur, power=2.0):
    """Nothing to full over the whole length: something gathering."""
    return np.linspace(0.0, 1.0, n_of(dur)) ** power


def decay_env(dur, attack=0.004, t60=None):
    """A soft-edged attack and an exponential fall to -60dB at `t60` seconds (the whole length by
    default). The attack is a raised cosine: a straight ramp has a corner at each end, and the
    corner is a click."""
    n = n_of(dur)
    tt = np.arange(n) / SR
    a = max(1, min(n - 1, int(attack * SR)))
    env = np.exp(-6.9 * tt / (t60 if t60 else dur))
    env[:a] *= 0.5 - 0.5 * np.cos(np.pi * np.arange(a) / a)
    return env


def _polyblep(ph, dt):
    out = np.zeros_like(ph)
    m = ph < dt
    x = ph[m] / dt[m]
    out[m] = x + x - x * x - 1.0
    m = ph > 1.0 - dt
    x = (ph[m] - 1.0) / dt[m]
    out[m] = x * x + x + x + 1.0
    return out


def blsaw(freq, dur, phase=0.0):
    """A sawtooth without the aliasing: `saw` folds everything above 22kHz back down as a hash of
    tones that belong to no pitch, which on anything over a few hundred Hz is the 'digital' in
    a digital sound."""
    n = n_of(dur)
    dt = np.clip(as_array(freq, n) / SR, 1e-6, 0.45)
    ph = (np.cumsum(dt) + phase) % 1.0
    return 2.0 * ph - 1.0 - _polyblep(ph, dt)


def supersaw(freq, dur, voices=7, spread=0.012, name="ssw"):
    """A stack of sawtooths a few cents apart, each starting where it likes: the voices beat
    against each other and no two cycles are alike, which is the difference between an ensemble
    and an oscillator."""
    n = n_of(dur)
    f = as_array(freq, n)
    rng = np.random.default_rng(seed_of(name))
    out = np.zeros(n)
    for v in range(voices):
        off = ((v / max(1, voices - 1)) * 2.0 - 1.0) * spread if voices > 1 else 0.0
        g = 1.0 - 0.45 * abs(off) / max(spread, 1e-9)        # the middle voices lead
        out += blsaw(f * (1.0 + off), dur, phase=rng.uniform()) * g
    return out / voices


def varispeed(x, rate):
    """Play `x` at a speed that changes as it goes (`rate`: 1 is as recorded). Everything in it
    moves together, pitch and texture, which is what a thing going away from you does and what a
    filter sweeping over a still sound does not."""
    x = np.asarray(x, dtype=float)
    n = len(x)
    pos = np.cumsum(as_array(rate, n)) - as_array(rate, n)[0]
    keep = pos < n - 1
    return np.interp(pos[keep], np.arange(n), x)


def whoosh(dur, lo=400.0, hi=3000.0, end=None, peak=0.35, q=1.3, body=0.5, top=0.2, flutter=0.3,
           rise=1.6, fall=2.4, name="wsh"):
    """Air shoved aside by something passing: a band of pink noise that climbs to `hi` as it
    comes and falls to `end` as it goes, loudest as it passes, with the mass of air under it
    (`body`), the hiss over it (`top`) and the gusting that no moving thing is without
    (`flutter`). `air` with the gesture in it: the band and the loudness peak together, and both
    move the way a pass-by does."""
    n = n_of(dur)
    end = lo * 0.7 if end is None else end
    centre = glide([(0.0, lo), (peak, hi), (1.0, end)], dur)
    src = pink(dur, name + "p")
    out = svf(src, centre, q=min(q, 3.0), mode="bp")
    if body > 0:
        out = out + svf(src, np.maximum(centre * 0.3, 90.0), q=0.9, mode="lp") * body
    if top > 0:
        hiss = highpass(noise(dur, name + "h"), 4800)
        out = out + hiss * top * (centre / max(hi, 1.0)) ** 1.5 * 2.0
    env = arc(dur, peak, rise, fall)
    if flutter > 0:
        # slow and slight: at 21 times a second and deep, as this was, air doesn't gust, it
        # stutters, and a swing came out ragged where it should be one clean stroke
        depth = flutter * 0.45
        env = env * (1.0 - depth + depth * nlfo(dur, 8.0, 0.3, 1.5, name + "f"))
    return norm(out * env)


# Everything pitched is tuned to A minor, the key of both music tracks, so a firefight stays
# harmonically coherent instead of sounding like an argument.
N = dict(A1=55.00, E2=82.41, F2=87.31, A2=110.00, C3=130.81, D3=146.83, E3=164.81, F3=174.61, G3=196.00,
         A3=220.00, C4=261.63, D4=293.66, E4=329.63, F4=349.23, G4=392.00, A4=440.00, B4=493.88,
         C5=523.25, D5=587.33, E5=659.25, G5=783.99, A5=880.00, C6=1046.50, E6=1318.51, A6=1760.00)

# A minor pentatonic, the music's key: what any pitch in a sound is drawn from, so a firefight
# and its soundtrack agree
_PENTA = (0, 3, 5, 7, 10)


def scale_freqs(lo, hi, degrees=_PENTA, root=55.0):
    out = []
    for octave in range(0, 10):
        for d in degrees:
            f = root * 2.0 ** (octave + d / 12.0)
            if lo <= f <= hi:
                out.append(f)
    return np.array(out)


def shimmer(dur, lo=3000.0, hi=11000.0, rate=80.0, grain=(0.006, 0.026), density=None, bell=0.3,
            spread=0.004, tilt=0.6, name="shm"):
    """Glitter: a sheen of bright air that wavers, with the air singing two or three high notes
    of the music's scale inside it. `density` (points for seg_env) shapes how much of it there is
    over the sound; `tilt` darkens its top.

    Nothing in it begins or ends: no grain, no chime, no tick. It was a cloud of tiny chimes, a
    few milliseconds of one pitch each, on the theory that enough of them are heard as a cloud.
    They are heard as what they are — a tick with a pitch is a CLINK, and the ear finds a pitch
    under noise far more easily than its level says — so a katana's cut, a wizard's bolt and
    forty other sounds each had a handful of glass beads dropped on a plate in them. (`rate`,
    `grain`, `bell` and `spread` are what the cloud was made with, kept for the callers.)"""
    n = n_of(dur)
    dens = seg_env(density, dur) if density is not None else np.ones(n)
    dens = lowpass(dens, 9.0, order=1)
    air_ = bandpass(noise(dur, name + "a"), lo, hi)
    air_ = lowpass(air_, lo + (hi - lo) * (1.0 - 0.6 * tilt), order=1)
    air_ = norm(air_) * nlfo(dur, 9.0, 0.45, 1.0, name + "w")
    notes = scale_freqs(lo, min(hi, 8400.0))
    out = air_ * 0.75
    if len(notes):
        rng = np.random.default_rng(seed_of(name))
        src = noise(dur, name + "s")
        pick = notes[np.sort(rng.choice(len(notes), size=min(3, len(notes)), replace=False))]
        sing = np.zeros(n)
        for i, f in enumerate(pick):
            sing += norm(reson(src, f, 70.0)) * nlfo(dur, 4.0, 0.25, 1.0, name + "g%d" % i) / (1.0 + 0.4 * i)
        out = out + norm(sing) * 0.5
    # half the level its peak would give it: it is continuous where the cloud of chimes was
    # sparse, and at the gains the cloud was mixed at it would be twice as loud as it was
    return norm(out * dens) * 0.5


def chime_run(dur, lo=1500.0, hi=6000.0, up=True, notes=9, ring=0.09, jitter=0.2, name="chr"):
    """A run of soft chimes, up or down the music's scale — a harp's or a celesta's way of
    saying 'magic', for the few abilities that are that (a lucky hand paying out, a walk among
    the stars). At most six notes however many are asked for, each struck softly (a felt mallet:
    6ms to speak) and let ring a fifth of a second and more, so it is a phrase and not a rattle
    of ticks: nine of them in a fifth of a second, 90ms each, was a pocketful of change."""
    n = n_of(dur)
    out = np.zeros(n)
    rng = np.random.default_rng(seed_of(name))
    scale = scale_freqs(lo, hi)
    if len(scale) == 0:
        return out
    count = max(1, min(6, notes, int(dur / 0.045) + 1))
    idx = np.round(np.linspace(0, len(scale) - 1, count)).astype(int)
    if not up:
        idx = idx[::-1]
    held = max(ring, 0.2)
    span = max(0.0, dur * 0.75)
    for i, k in enumerate(idx):
        t0 = span * i / max(1, count)
        f = scale[k] * (1.0 + rng.normal(0.0, 0.0015))
        m = n_of(held)
        tt = np.arange(m) / SR
        env = decay_env(held, attack=0.006)
        g = np.sin(2 * np.pi * f * tt) + 0.3 * np.sin(2 * np.pi * f * 2.0 * tt) * np.exp(-tt * (6.0 / held))
        at(out, max(0.0, t0), g * env * (1.0 - 0.4 * i / max(1, count)))
    return norm(out)


def steel_song(dur, f=(4300.0, 5900.0, 7800.0), rise=1.05, q=55.0, peak=0.25, name="stl"):
    """A blade singing: the long 'shiiing' of an edge moving fast or leaving its scabbard. Air
    held in three narrow resonances high up (a blade's own, far above anything a can rings at),
    each climbing a little as the stroke gathers speed, swelling in and dying away — friction,
    not a blow, so it has no beginning to click and no grain to tick: one smooth stroke of sound."""
    n = n_of(dur)
    src = noise(dur, name)
    out = np.zeros(n)
    for i, f0 in enumerate(f):
        band = svf(src, glide([(0.0, f0), (1.0, f0 * rise)], dur), q=q, mode="bp")
        out += norm(band) * nlfo(dur, 6.0, 0.6, 1.0, name + str(i)) / (1.0 + 0.5 * i)
    return norm(out * arc(dur, peak, 1.2, 1.8))


def reson(x, fc, q=30.0, tail=None):
    """One resonance, causal: what `res_bp` is the magnitude of, with its phase, so it rings
    AFTER what excites it and not half before (a zero-phase filter rings both ways in time, and on
    an FFT the half that comes before wraps round to the end of the sound). Padded for the ring
    it leaves, and cut back to the input's length."""
    n = len(x)
    if n < 8:
        return x
    pad = int(SR * (tail if tail is not None else min(0.4, 7.0 * q / (np.pi * max(fc, 30.0)))))
    m = n + pad
    spec = np.fft.rfft(x, m)
    w = np.fft.rfftfreq(m, 1.0 / SR) / max(fc, 20.0)
    jw = 1j * w
    h = (jw / q) / (1.0 - w * w + jw / q)
    return np.fft.irfft(spec * h, m)[:n]


def halo(freqs, dur, q=40.0, attack=0.012, t60=None, gains=None, breath=0.12, wander=0.0, peak=None, name="halo"):
    """Pitched air: noise made to sing on a chord by a resonance at each of its notes. The tone
    of a magic that has no material — it swells in (nothing is struck), every partial is where
    the chord puts it (nothing is inharmonic), and because it is noise underneath, no two cycles
    of it are alike, so it shimmers by itself where an oscillator would sit there and hum.

    `q` is how clearly it sings (20 is a breath with a pitch in it, 80 nearly a glass harmonica);
    `wander` drifts the excitation's loudness, slowly, so a long one breathes. With `peak` (a
    fraction of the sound) it swells to there and goes, instead of starting and dying away: a
    tone that starts at once and dies is a struck one, however soft its first few milliseconds,
    and only glass and strings should be."""
    n = n_of(dur)
    if peak is not None:
        env = arc(dur, peak, 1.4, 1.8)
    else:
        env = decay_env(dur, attack=attack, t60=t60 if t60 else dur)
    if wander > 0:
        env = env * (1.0 - wander + wander * nlfo(dur, 7.0, 0.3, 1.5, name + "w"))
    x = pink(dur, name) * env
    out = np.zeros(n)
    for i, f in enumerate(freqs):
        g = gains[i] if gains is not None else 1.0 / (1.0 + 0.45 * i)
        out += norm(reson(x, f, q)) * g
    out = norm(out)
    if breath > 0:
        out = out + norm(bandpass(x, min(freqs) * 0.8, max(freqs) * 2.5)) * breath
    return norm(out)


def comb(x, freq, fb=0.8, damp=0.35):
    """A resonance that glides: the signal fed back on itself after one period of `freq`, which
    may move. A jet's phasing roar, a blaster's ring, energy streaking down a tube. `damp` takes
    the top off each pass, as every real resonator does."""
    xs = np.asarray(x, dtype=float).tolist()
    n = len(xs)
    d = (SR / np.clip(as_array(freq, n), 40.0, 11000.0)).tolist()
    y = [0.0] * n
    lp = 0.0
    for i in range(n):
        p = i - d[i]
        if p >= 0.0:
            k = int(p)
            fr = p - k
            v = y[k] * (1.0 - fr) + y[k + 1] * fr
        else:
            v = 0.0
        lp = v + damp * (lp - v)
        y[i] = xs[i] + fb * lp
    return np.nan_to_num(np.clip(np.array(y), -12.0, 12.0))


def snap(kind="hard", name="snp"):
    """The first few milliseconds, which say how the thing began:

    hard   a short spit of noise — something released, a 't'
    soft   a puff: a breath of low-mid noise with no edge — a 'pf'
    pop    a cork, a bubble, a cavity closing — a short low knock with air round it
    tick   a small dry tick, for small precise things (a clock, a catch)
    """
    if kind == "hard":
        # a spit of noise with a quick edge and no click in front of it: 't', not 'tk'. The click
        # (2ms of everything over 2.6kHz) under a hundred sounds was a hundred ticks
        sp = bandpass(noise(0.018, name + "n"), 700, 5200) * decay_env(0.018, attack=0.0012)
        return norm(sp)
    if kind == "soft":
        s = bandpass(pink(0.03, name + "n"), 250, 2600)
        return norm(s * decay_env(0.03, attack=0.003))
    if kind == "pop":
        k = sine(glide([(0.0, 240.0), (1.0, 150.0)], 0.03), 0.03) * decay_env(0.03, attack=0.0015)
        a = bandpass(noise(0.02, name + "n"), 600, 4000) * exp_env(0.02, attack=0.0005, decay=8.0)
        return mixdown(norm(k) * 0.8, norm(a) * 0.5)
    if kind == "tick":
        sp = bandpass(noise(0.009, name + "n"), 1500, 6000) * decay_env(0.009, attack=0.0008)
        return norm(sp)
    raise ValueError(kind)


def weight(f0=80.0, dur=0.16, name="wt"):
    """The low push of something leaving or landing: a tone that settles a fifth in its first
    25ms and then holds, driven enough to have harmonics, with the puff of air it shoves. Soft
    at the front (4ms) — it is displaced air, not a beater on a skin — and it does not go on
    falling as it dies, which is the whole difference between weight and a kick drum."""
    f = glide([(0.0, f0 * 1.5), (min(0.5, 0.025 / dur), f0), (1.0, f0 * 0.88)], dur)
    env = decay_env(dur, attack=0.004)
    tone = saturate(sine(f, dur) * env, 2.4)
    puff = lowpass(pink(dur, name + "p"), f0 * 4.5) * env ** 1.6
    return norm(tone + norm(puff) * 0.45)


# ── throats ──
# `cry` is a clean glottal pulse train through three sharp formants: a vowel, sung. That is right
# for a howl or a wail and wrong for everything that growls, roars, snarls or screeches, which it
# used to voice as well — a bear's maul came with a machine saying "uh". An animal's roar is
# mostly NOISE: the folds slap together irregularly (each period a different length and loudness,
# every other one weaker), turbulence rushes through them, and the throat is long and loose.

def pulses(f0_env, dur, jitter=0.08, shimmer_=0.3, sub=0.3, name="pls"):
    """An irregular pulse train: each period off by `jitter`, each pulse its own loudness, and
    every other one weaker by `sub` (the period-doubling that makes a voice rough)."""
    n = n_of(dur)
    f = as_array(f0_env, n)
    out = np.zeros(n)
    rng = np.random.default_rng(seed_of(name + "pls"))
    t, k = 0.0, 0
    while True:
        i = int(t * SR)
        if i >= n:
            break
        a = 1.0 + shimmer_ * rng.normal()
        if k % 2:
            a *= 1.0 - sub * rng.uniform(0.4, 1.0)
        out[i] += max(0.1, a)
        t += (1.0 / max(20.0, f[i])) * max(0.3, 1.0 + jitter * rng.normal())
        k += 1
    return out


def throat(f0_env, dur, rough=0.5, breath_=0.5, name="thr"):
    """A rough voice's source: slapping folds, and the turbulence that rushes through them as
    they open."""
    n = n_of(dur)
    p = pulses(f0_env, dur, jitter=0.02 + 0.12 * rough, shimmer_=0.1 + 0.4 * rough,
               sub=0.15 + 0.7 * rough, name=name)
    k = np.exp(-np.arange(int(0.005 * SR)) / (0.0009 * SR))
    voiced = np.convolve(p, k)[:n]
    voiced = voiced - lowpass(voiced, 40.0, order=1)          # no DC from a train of one-sided pulses
    gate = np.convolve(p, np.hanning(int(0.007 * SR)))[:n]
    gate = gate / (np.max(gate) + 1e-9)
    turb = bandpass(noise(dur, name + "t"), 450, 6500) * (0.25 + 0.75 * gate)
    return norm(voiced) * (1.0 - 0.6 * breath_) + norm(turb) * breath_


def roar(dur, f0=(95.0, 125.0, 72.0), vowels=("uh", "ah", "aw"), size=1.0, rough=0.6, breath_=0.5,
         drive=2.8, peak=0.18, name="roar"):
    """A beast's roar, growl or snarl. `f0` is the fold rate at the start, the peak and the end
    (low: a bear is 70-110, a wolf's snarl 110-160); `size` lengthens the throat (formants at
    1/size: over 1 is a bigger animal); `rough` is how broken the voice is and `breath_` how much
    of it is air. Driven hard afterwards, since a roar is a throat past its limit."""
    f = glide([(0.0, f0[0]), (0.28, f0[1]), (1.0, f0[2])], dur) * nlfo(dur, 9.0, 0.95, 1.05, name + "j")
    src = throat(f, dur, rough=rough, breath_=breath_, name=name)
    ts = np.linspace(0.0, 1.0, len(vowels))
    keys = [(t, tuple(x / size for x in VOWELS[v])) for t, v in zip(ts, vowels)]
    v = tract(src, keys, dur, q=(6.0, 5.5, 5.0), gains=(1.0, 0.75, 0.45), name=name)
    chest = bandpass(pink(dur, name + "c"), 70.0 / size, 260.0 / size)
    chest = norm(chest * nlfo(dur, 30.0, 0.3, 1.0, name + "cg"))
    out = saturate(v * 0.9 + chest * 0.45, drive)
    env = arc(dur, peak, 1.0, 1.4) * nlfo(dur, 16.0, 0.75, 1.0, name + "a")
    return norm(out * env)


def screech(dur, f0=(1700.0, 2900.0, 1500.0), rough=0.6, breath_=0.4, peak=0.2, name="skr"):
    """A raptor's cry: high, harsh and falling away — 'kree-arr'. A tone with its second and
    third harmonics whose pitch shakes, torn up at a rate the ear hears as harshness (which is
    exactly what it is for), with the hiss of the breath in the same band."""
    n = n_of(dur)
    f = glide([(0.0, f0[0]), (peak, f0[1]), (1.0, f0[2])], dur) * nlfo(dur, 45.0, 0.965, 1.035, name + "j")
    ph = 2 * np.pi * np.cumsum(f) / SR
    tone = np.sin(ph) + 0.55 * np.sin(2 * ph + 0.7) + 0.3 * np.sin(3 * ph + 1.9)
    tear = (1.0 - rough) + rough * nlfo(dur, 85.0, 0.0, 1.0, name + "r") ** 1.5 * 1.6
    hiss = svf(noise(dur, name + "n"), f * 1.4, q=1.6, mode="bp")
    out = (norm(tone) * (1.0 - breath_) + norm(hiss) * breath_) * tear
    out = saturate(out, 1.8)
    return norm(out * arc(dur, peak * 0.6, 0.8, 1.6))


def voices(dur, f0_keys, vowels, count=3, spread=0.035, stagger=0.03, breath_=0.5, name="vcs", **kw):
    """Several throats on one cry, a little apart in pitch and in time: a wail, a choir of the
    dead, anything that should sound like more than one mouth — or like no mouth on earth. One
    synthetic voice alone is a machine saying 'ah'; three of them never quite together is a
    sound."""
    out = np.zeros(n_of(dur + stagger * count))
    rng = np.random.default_rng(seed_of(name))
    for i in range(count):
        det = 1.0 + spread * ((i / max(1, count - 1)) * 2.0 - 1.0) if count > 1 else 1.0
        keys = [(t, f * det * (1.0 + rng.normal(0.0, 0.006))) for t, f in f0_keys]
        v = cry(dur, keys, vowels, breath=breath_, jitter=0.02 + 0.012 * i, name=name + str(i), **kw)
        at(out, rng.uniform(0.0, stagger) * i, v * (1.0 if i == 0 else rng.uniform(0.55, 0.85)))
    return norm(out)


# ═══════════════════════════ the table of sounds ═══════════════════════════
# Master presets. Sounds allowed to keep their top end: ice, glass, glitter, electricity, and the
# hitmarker, which has to cut through everything. Everything else is band-limited by `air_tame`,
# because a set where all the sounds occupy 60Hz-16kHz is a set where none of them is
# distinguishable from the others.
BRIGHT = dict(air=(12000.0, -2.0))
SPARK = dict(air=(10000.0, -4.5))      # electricity: bright, but not sibilant
BOLT_M = dict(width=0.55, transient=1.35, comp=(0.30, 2.8), tail=(0.32, 0.13))
TIGHT = dict(width=0.5, transient=1.55, comp=(0.30, 2.8), tail=(0.26, 0.11))
SWEEP = dict(width=0.7, transient=0.6, pan=(-0.55, 0.55), tail=(0.34, 0.14))
FLY = dict(width=0.55, transient=1.5, pan=(-0.45, 0.45), tail=(0.30, 0.12))
PUNCHY = dict(width=0.45, transient=1.8, comp=(0.28, 3.2), sub=(54, 0.28), tail=(0.34, 0.13))
WIDE = dict(width=0.9, transient=0.85, tail=(0.8, 0.20))
HEAVY = dict(width=0.95, transient=1.2, sub=(44, 0.36), comp=(0.26, 3.0), tail=(1.1, 0.24))
BIG = dict(width=0.85, transient=1.1, sub=(48, 0.3), comp=(0.28, 2.8), tail=(0.85, 0.22))
GHOST = dict(width=1.0, transient=0.5, tail=(1.3, 0.26))
GUN_M = dict(width=0.55, transient=2.2, sub=(58, 0.22), comp=(0.18, 5.5), tail=(0.5, 0.16))

# name -> (generator, level, length class, master kwargs, shape): every sound in the game but the
# music. A module of sounds adds its own with `sound`.
REGISTRY = {}


def sound(name, fn, level, clas="bolt", master_kw=None, held=False, **more):
    """One sound of the set: its file's name, what makes it, how loud it is (see `save_wav`), the
    class that bounds its length and its shape, and how it is finished. `held` is for the ones
    meant to hold — a beam, a howl, a cloud blooming — which get the `drone` contour instead of
    their class's, so the shape guarantee does not flatten the one thing they are for."""
    if name in REGISTRY:
        raise SystemExit("duplicate sound name: " + name)
    kw = dict(master_kw or {})
    kw.update(more)
    REGISTRY[name] = (fn, level, clas, kw, "drone" if held else None)


def render(name, out_dir=None):
    """Make one registered sound and write it."""
    global OUT_DIR
    fn, level, clas, kw, shape = REGISTRY[name]
    if out_dir is not None:
        OUT_DIR = out_dir
    return save_wav(name, fn(), level=level, clas=clas, shape=shape, **kw)
