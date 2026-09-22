#!/usr/bin/env python3
"""Procedurally synthesizes all game audio (sfx + music) into sounds/*.wav.

Everything is generated from numpy oscillators/noise — no samples, no external
audio libraries, zero licensing concerns.

WHAT MAKES A SOUND READ AS REAL (and what the earlier version of this file got
wrong): a synthesized impact is not a sine sweep plus noise. It is

    contact   0-5ms    the click of two surfaces meeting
    modes     5-300ms  the struck object ringing at ITS OWN frequency ratios,
                       each partial decaying at its own rate — this is what
                       says "iron" vs "wood" vs "bone" vs "ice"
    body      the low-frequency displacement of air, which is what makes an
              impact feel physical rather than heard
    space     early reflections + tail, sized to the event

and a creature sound is not filtered noise. It is a glottal pulse train with
pitch jitter driving a vocal tract whose formants MOVE. Both are implemented
below (`modal`, `MATERIALS`, `cry`, `tract`) and every impact/voice in the game
goes through them.

Layout:

    oscillators/envelopes -> filters (causal biquads + zero-phase FFT) ->
    shaping/space -> modal synthesis -> vocal synthesis -> air/whoosh ->
    master chain -> family engines -> one-off sounds -> music -> driver

Generators produce mono. Every sound then goes through the shared `master()`
chain, exactly like a real sound-design session:

    transient shaping -> compression -> sub reinforcement -> presence dip ->
    stereo decorrelation/width -> true-stereo reverb send -> auto-pan -> limiting

The presence dip (a wide -3.5dB bell around 3.2kHz) is not optional polish: the
ear's sensitivity peaks there, so a mix of 150 attack sounds that all put their
energy in that band is physically tiring within a minute. Attack sounds are also
kept SHORT — `SHOOT_COOLDOWN_MS` is 500ms, so anything with more than ~0.5s of
audible energy overlaps its own next shot and turns a firefight into mush.

Pitched material is tuned to A minor so it agrees with the music instead of
clashing with it. Output is 16-bit stereo WAV.

Run after adding a new ProjectileType/archetype, the same way
scripts/generate_tiles.py is rerun after adding a tile. Takes ~2-4 minutes
(the time-varying filters, modal banks and dynamics run sample-by-sample).
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
    y = np.empty(n)
    x1 = x2 = y1 = y2 = 0.0
    for i in range(n):
        xi = x[i]
        yi = b0 * xi + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        y[i] = yi
        x2, x1 = x1, xi
        y2, y1 = y1, yi
    return np.nan_to_num(y)


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
    out = np.empty(n)
    low = 0.0
    band = 0.0
    for i in range(n):
        fi = f[i]
        low += fi * band
        high = x[i] - low - damp * band
        band += fi * high
        if mode == "lp":
            out[i] = low
        elif mode == "bp":
            out[i] = band
        else:
            out[i] = high
    return np.nan_to_num(np.clip(out, -8.0, 8.0))


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
    """The low displacement of a body impact: one damped sine, no partials.

    The `drum` material was standing in for this and it is exactly wrong — a
    circular membrane's mode ratios (1, 1.59, 2.14, 2.30 ...) at 80Hz are a
    tom, and a tom under every hit is the "metal bucket" in the mix. A person
    being hit displaces mass; it has a pitch and no overtone series."""
    f = seg_env([(0.0, f0 * 1.45), (0.12, f0), (1.0, f0 * 0.42)], dur)
    return saturate(sine(f, dur) * exp_env(dur, attack=0.0015, decay=6.5), drive)


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
    for _ in range(count):
        t0 = rng.uniform(spread[0], dur * spread[1])
        gd = rng.uniform(0.006, 0.03)
        lo = rng.uniform(band[0], band[1] * 0.5)
        g = bandpass(noise(gd, name + str(rng.integers(1 << 20))), lo, lo * rng.uniform(2.2, 4.5))
        g *= exp_env(gd, attack=0.0004, decay=rng.uniform(*decay))
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
    rumble = sine(seg_env([(0.0, low * 1.5), (1.0, low * 0.7)], dur), dur)
    rumble *= ad_env(dur, attack=0.02, hold=0.4, curve=1.4) * 0.45
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
    low = sine(seg_env([(0.0, 78 / size), (1.0, 36 / size)], dur), dur)
    low *= seg_env([(0.0, 0.25), (0.35, 1.0), (1.0, 0.0)], dur)
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
    """Scattered high grains — magic dust, ice crystals, embers. Discrete grains
    rather than a hiss bed, so it twinkles instead of sizzling."""
    n = n_of(dur)
    out = np.zeros(n)
    rng = np.random.default_rng(seed_of(name))
    for _ in range(count):
        t0 = rng.uniform(0.0, dur * 0.85)
        gd = rng.uniform(0.02, 0.07)
        f = rng.uniform(lo, hi)
        g = sine(f * (np.linspace(1.0, 1.12, n_of(gd)) if rise else np.linspace(1.0, 0.9, n_of(gd))), gd)
        at(out, t0, g * exp_env(gd, attack=0.001, decay=7.0) * rng.uniform(0.08, 0.3))
    return out


def zap_arc(dur, lo=500, hi=9000, q=9.0, steps=90, name="arc"):
    """Electrical arcing: a resonant band that jumps randomly. The jump rate is
    the whole effect — smooth it and it turns into wind."""
    n = n_of(dur)
    rng = np.random.default_rng(seed_of(name))
    cut = np.repeat(rng.uniform(lo, hi, steps), max(1, n // steps + 1))[:n]
    cut = np.pad(cut, (0, max(0, n - len(cut))), mode="edge")
    return svf(noise(dur, name + "n"), lowpass(cut, 320, order=1), q=q, mode="bp")


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
    mag = np.abs(x)
    out = np.empty(n)
    e = 0.0
    for i in range(n):
        m = mag[i]
        c = a if m > e else r
        e = m + c * (e - m)
        out[i] = e
    return out


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
    """Attack enhancer — emphasises the leading edge, which reads as 'snap'."""
    if boost <= 0:
        return x
    fast = env_follow(x, 0.4, fast_ms)
    slow = env_follow(x, 0.4, slow_ms)
    diff = np.maximum(0.0, fast - slow) / (slow + 1e-4)
    return x * (1.0 + boost * np.minimum(diff, 3.0))


def sub_boost(x, freq=48.0, amount=0.5, decay=5.0, name="sub"):
    """Adds a dedicated sub-bass drop under an impact so it feels physical."""
    dur = len(x) / SR
    s = sine(seg_env([(0.0, freq * 2.6), (0.15, freq * 1.2), (1.0, freq * 0.8)], dur), dur)
    s *= exp_env(dur, attack=0.002, decay=decay)
    s = saturate(s, 1.6)
    if len(s) < len(x):
        s = np.pad(s, (0, len(x) - len(s)))
    return x + s[: len(x)] * amount


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

def trim_tail(x, thresh=2.5e-3, max_dur=2.2):
    """Cut the inaudible end of the reverb tail (-52dB) and cap total length.

    Long quiet tails cost file size but contribute nothing under gameplay, where
    they sit far below the music and the next sound effect.
    """
    if len(x) == 0:
        return x
    mag = np.max(np.abs(x), axis=1) if is_stereo(x) else np.abs(x)
    peak = np.max(mag)
    if peak <= 0:
        return x
    idx = np.nonzero(mag > thresh * peak)[0]
    end = len(x) if len(idx) == 0 else min(len(x), idx[-1] + int(0.02 * SR))
    end = min(end, n_of(max_dur))
    return x[:end]


def fade_edges(x, fade_in=0.0005, fade_out=0.012):
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
              ability=(0.16, 0.70), heavy=(0.30, 1.10), event=(0.30, 1.10),
              drone=(0.55, 1.40))


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
# distinct attacks and undifferentiated mush.
MAX_DUR = dict(bolt=0.55, melee=0.6, ability=0.9, heavy=1.5, event=1.6, tiny=0.25)


def save_wav(name, signal, level=0.85, trim=True, clas="bolt", shape=None, **master_kw):
    x = np.nan_to_num(np.asarray(signal, dtype=float))
    x = master(x, name=name, **master_kw)
    # DC blocker rather than a mean subtraction: asymmetric saturation and the
    # limiter knee leave a small offset that a single mean does not remove, and
    # an offset costs headroom for nothing.
    x = np.stack([highpass(x[:, 0], 18.0, order=1), highpass(x[:, 1], 18.0, order=1)], axis=1)
    if shape is not False:
        x = enforce_shape(x, shape if isinstance(shape, str) else clas)
    if trim:
        x = trim_tail(x, max_dur=MAX_DUR.get(clas, 1.2))
    peak = np.max(np.abs(x)) if len(x) else 0.0
    if peak > 1e-9:
        x = x / peak * min(level * 1.06, 0.995)
    x = limit_stereo(x)
    # fade before the final normalise so the edge ramps can't shave the peak
    x = fade_edges(x.copy())
    peak = np.max(np.abs(x))
    if peak > 1e-9:
        x = x / peak * level
    inter = np.clip(x, -1.0, 1.0).reshape(-1)
    pcm = (inter * 32767).astype(np.int16)
    path = os.path.join(OUT_DIR, name + ".wav")
    with wave.open(path, "wb") as f:
        f.setnchannels(2)
        f.setsampwidth(2)
        f.setframerate(SR)
        f.writeframes(pcm.tobytes())
    return path


# ═══════════════════════════ family engines ═══════════════════════════
# Every attack in the game is one of these thirteen shapes. Keeping them as
# engines rather than 120 bespoke functions is what lets a quality change (a
# better transient, a tamer top end) reach the whole roster at once — the same
# reason every character sprite goes through sprite_base.generate_character.


def _texture(kind, dur, name):
    """The character layer of an attack: quiet, high, and the thing that says
    'fire' or 'bone' or 'circuitry'. Deliberately never the loudest layer —
    the old set put its identity in the 2-5kHz band at full level, which is
    both fatiguing and, once four players are firing, indistinguishable."""
    if kind == "soft":
        return bandpass(pink(dur, name), 600, 2800) * exp_env(dur, attack=0.002, decay=9.0)
    if kind == "fizz":
        return highpass(noise(dur, name), 2200) * exp_env(dur, attack=0.001, decay=15.0)
    if kind == "grit":
        return saturate(bandpass(pink(dur, name), 180, 1400), 3.0) * exp_env(dur, decay=8.0)
    if kind == "crystal":
        return sparkle(dur, 2600, 9000, count=9, name=name) * 0.9
    if kind == "wet":
        w = bandpass(pink(dur, name), 300, 2400) * exp_env(dur, decay=7.0)
        return mixdown(w, water(dur * 0.7, size=2.2, bubbles=5, foam=0.15, name=name + "w") * 0.5)
    if kind == "spark":
        return zap_arc(dur, 900, 7000, q=8, steps=34, name=name) * exp_env(dur, decay=11.0)
    if kind == "dust":
        return bandpass(pink(dur, name), 200, 1700) * exp_env(dur, attack=0.004, decay=6.0)
    if kind == "void":
        v = lowpass(pink(dur, name), 900)
        return v * (np.linspace(0.0, 1.0, n_of(dur)) ** 2.4)
    if kind == "paper":
        p = bandpass(noise(dur, name), 900, 4200) * exp_env(dur, attack=0.001, decay=18.0)
        return p * tremolo(n_of(dur), 42, 0.5, shape="pulse")
    if kind == "bone":
        return modal("bone", 620, dur, damp=1.6, name=name) * 0.5
    if kind == "none":
        return np.zeros(n_of(dur))
    raise ValueError(kind)


#            f0    rise fall ratio index      decay lp          tex        tex_g drive sub   tail
BOLT_VOICES = {
    "neutral":  dict(f0=196, rise=1.30, fall=0.94, ratio=2.0,  index=(4.5, 0.4), decay=9.0,  lp=(3400, 1.0), tex="soft",    tex_g=.26, drive=1.2, sub=.55, tail=(.30, .13)),
    "arcane":   dict(f0=262, rise=1.42, fall=0.95, ratio=1.41, index=(6.0, 0.9), decay=8.0,  lp=(4600, 1.4), tex="crystal", tex_g=.30, drive=1.1, sub=.42, tail=(.55, .22)),
    "mystic":   dict(f0=175, rise=1.26, fall=0.96, ratio=3.02, index=(3.4, 0.8), decay=7.0,  lp=(3800, 1.6), tex="crystal", tex_g=.22, drive=1.1, sub=.48, tail=(.65, .24)),
    "rune":     dict(f0=147, rise=1.28, fall=0.93, ratio=1.99, index=(5.0, 1.2), decay=8.5,  lp=(2900, 1.2), tex="dust",    tex_g=.30, drive=1.6, sub=.62, tail=(.45, .20)),
    "star":     dict(f0=330, rise=1.38, fall=0.96, ratio=5.02, index=(2.6, 0.4), decay=7.0,  lp=(6200, 1.1), tex="crystal", tex_g=.34, drive=1.1, sub=.30, tail=(.70, .26)),
    "soul":     dict(f0=131, rise=1.24, fall=0.94, ratio=1.73, index=(5.5, 1.1), decay=6.5,  lp=(2100, 1.0), tex="void",    tex_g=.28, drive=1.5, sub=.66, tail=(.70, .26)),
    "dark":     dict(f0=110, rise=1.22, fall=0.92, ratio=1.73, index=(6.5, 1.3), decay=7.5,  lp=(1700, 1.0), tex="grit",    tex_g=.30, drive=1.9, sub=.72, tail=(.55, .22)),
    "dust":     dict(f0=123, rise=1.20, fall=0.93, ratio=2.41, index=(5.0, 0.9), decay=8.5,  lp=(2000, 0.9), tex="dust",    tex_g=.38, drive=1.6, sub=.60, tail=(.40, .18)),
    "fire":     dict(f0=165, rise=1.30, fall=0.94, ratio=2.73, index=(7.0, 1.4), decay=7.5,  lp=(3000, 1.1), tex="fizz",    tex_g=.24, drive=2, sub=.58, tail=(.35, .16)),
    "molten":   dict(f0=98,  rise=1.20, fall=0.91, ratio=1.26, index=(8.0, 1.8), decay=6.0,  lp=(1500, 1.0), tex="grit",    tex_g=.34, drive=2.3, sub=.80, tail=(.45, .20)),
    "ice":      dict(f0=294, rise=1.36, fall=0.96, ratio=2.01, index=(3.0, 0.5), decay=9.0,  lp=(5200, 1.3), tex="crystal", tex_g=.32, drive=1.1, sub=.36, tail=(.55, .22)),
    "poison":   dict(f0=147, rise=1.24, fall=0.92, ratio=3.31, index=(5.5, 1.0), decay=8.0,  lp=(2400, 1.1), tex="wet",     tex_g=.34, drive=1.6, sub=.55, tail=(.38, .17)),
    "acid":     dict(f0=185, rise=1.26, fall=0.93, ratio=3.71, index=(5.0, 0.8), decay=9.0,  lp=(2800, 1.2), tex="wet",     tex_g=.38, drive=1.7, sub=.48, tail=(.34, .16)),
    "mud":      dict(f0=87,  rise=1.18, fall=0.90, ratio=1.26, index=(7.0, 1.6), decay=10.0, lp=(1100, 0.9), tex="wet",     tex_g=.44, drive=1.9, sub=.72, tail=(.28, .13)),
    "sand":     dict(f0=131, rise=1.22, fall=0.92, ratio=2.31, index=(5.0, 1.0), decay=10.0, lp=(2600, 0.9), tex="dust",    tex_g=.46, drive=1.5, sub=.50, tail=(.32, .15)),
    "tech":     dict(f0=220, rise=1.46, fall=0.95, ratio=4.00, index=(3.0, 0.2), decay=11.0, lp=(5000, 1.6), tex="spark",   tex_g=.26, drive=1.1, sub=.40, tail=(.26, .12)),
    "nano":     dict(f0=247, rise=1.32, fall=0.96, ratio=6.11, index=(2.4, 0.3), decay=12.0, lp=(5800, 1.5), tex="fizz",    tex_g=.30, drive=1.1, sub=.34, tail=(.30, .14)),
    "electric": dict(f0=175, rise=1.38, fall=0.94, ratio=2.99, index=(4.0, 0.5), decay=10.0, lp=(4200, 1.4), tex="spark",   tex_g=.36, drive=1.4, sub=.46, tail=(.34, .16)),
    "charm":    dict(f0=349, rise=1.28, fall=0.97, ratio=2.00, index=(2.2, 0.3), decay=6.0,  lp=(5400, 1.0), tex="crystal", tex_g=.30, drive=1.1, sub=.28, tail=(.80, .28)),
    "holy":     dict(f0=262, rise=1.20, fall=0.97, ratio=2.00, index=(2.0, 0.3), decay=5.0,  lp=(5000, 1.0), tex="crystal", tex_g=.26, drive=1.1, sub=.40, tail=(.90, .30)),
    "void":     dict(f0=73,  rise=1.16, fall=0.90, ratio=1.33, index=(8.0, 2.0), decay=5.0,  lp=(1200, 1.0), tex="void",    tex_g=.34, drive=2, sub=.85, tail=(.90, .30)),
    "paper":    dict(f0=294, rise=1.28, fall=0.96, ratio=4.71, index=(2.0, 0.3), decay=14.0, lp=(4400, 1.0), tex="paper",   tex_g=.50, drive=1.1, sub=.22, tail=(.24, .11)),
}


def bolt(voice, f0=None, dur=0.40, name="bolt", tex_g=None, extra=None):
    """A cast projectile leaving the caster.

    Structure is deliberately upside-down from where this file started: the
    fundamental sits at 90-350Hz with a short sub under it (weight), and the
    timbre that identifies the school of magic rides ON TOP at roughly -12dB.
    Bolts built the other way round — all identity, no body — are the "thin,
    beepy" sound the old set had, and they vanish the moment anything else
    is playing."""
    p = BOLT_VOICES[voice]
    f0 = p["f0"] if f0 is None else f0
    n = n_of(dur)

    # The launch drop is FAST and SMALL. Sliding a harmonic stack down an
    # octave over the whole sound is a slide whistle — the cartoon "boing" —
    # and with every bolt in the game doing it, the whole set collapses into
    # one "pew" with different filters on it. A real launch is a brief pitch
    # settle in the first few tens of milliseconds, then a steady note.
    pitch = seg_env([(0.0, f0 * p["rise"]), (0.035, f0 * 1.02), (0.14, f0),
                     (1.0, f0 * p["fall"])], dur)
    index = seg_env([(0.0, p["index"][0]), (0.10, p["index"][0] * 0.42), (1.0, p["index"][1])], dur)
    core = fm(pitch, p["ratio"], index, dur) * exp_env(dur, attack=0.0015, decay=p["decay"])
    core = eq(core, "lp", p["lp"][0], q=p["lp"][1])

    # weight: a fast low thump under the launch. Without it a bolt has no
    # physical size and reads as a UI beep.
    bd = min(0.16, dur * 0.55)
    low = sine(seg_env([(0.0, f0 * 0.62), (1.0, f0 * 0.34)], bd), bd) * exp_env(bd, attack=0.001, decay=8.0)
    low = saturate(low, 1.8) * p["sub"]

    tex = _texture(p["tex"], dur, name + "tex") * (p["tex_g"] if tex_g is None else tex_g)
    lead = click(0.005, fc=1600 + 2200 * min(1.0, f0 / 260.0), name=name + "cl", shape=1.3)

    out = mixdown(core * 0.9, tex, pad_to(low, dur))
    out = saturate(out, p["drive"])
    out[:len(lead)] += lead * 0.45
    if extra is not None:
        out = mixdown(out, pad_to(extra, len(out) / SR))
    return reverb(out, decay=p["tail"][0], damp=5600, mix=p["tail"][1], name="bolt", predelay=0.004)


BEAM_VOICES = {
    "ice":     dict(base=330, ratio=2.01, idx=(3.0, 0.8), sweep=(7000, 2600), q=6, band=(2200, 7000), mix=0.45, drive=1.1, rv=(0.5, 0.24)),
    "drain":   dict(base=110, ratio=1.49, idx=(6.0, 2.0), sweep=(2000, 380), q=6, band=(90, 1200), mix=0.42, drive=1.9, rv=(0.7, 0.28), fall=True),
    "vine":    dict(base=98,  ratio=3.11, idx=(6.5, 1.5), sweep=(2200, 620), q=4, band=(150, 1900), mix=0.5, drive=1.7, rv=(0.4, 0.20)),
    "laser":   dict(base=440, ratio=1.0,  idx=(2.0, 0.4), sweep=(8000, 3200), q=8, band=(3000, 9000), mix=0.30, drive=1.4, rv=(0.28, 0.15)),
    "railgun": dict(base=73,  ratio=2.5,  idx=(9.0, 1.0), sweep=(7000, 700), q=7, band=(900, 9000), mix=0.48, drive=2.5, rv=(0.8, 0.28)),
    "stone":   dict(base=110, ratio=1.87, idx=(8.0, 3.0), sweep=(1700, 430), q=3, band=(240, 2400), mix=0.62, drive=2, rv=(0.55, 0.24)),
    "whip":    dict(base=196, ratio=2.66, idx=(6.0, 0.6), sweep=(6000, 1200), q=5, band=(900, 6500), mix=0.55, drive=1.9, rv=(0.32, 0.17), crack=True),
    "gravity": dict(base=49,  ratio=1.33, idx=(10.0, 2.5), sweep=(1000, 220), q=5, band=(40, 620), mix=0.36, drive=2.2, rv=(1.0, 0.30)),
    "eye":     dict(base=87,  ratio=1.62, idx=(7.0, 1.6), sweep=(3200, 800), q=5, band=(300, 3600), mix=0.44, drive=2, rv=(0.7, 0.26)),
}


BEAM_TEXTURE = {
    # what each beam throws off while it is being held — the layer that says
    # which beam it is. A sweep and a cutoff alone leave ten held synth notes.
    "ice":     lambda d, n: sparkle(d, 3000, 11000, count=int(26 * d), name=n) * 0.55,
    "laser":   lambda d, n: sine(seg_env([(0.0, 5200.0), (1.0, 4600.0)], d), d)
                            * ad_env(d, attack=0.01, hold=0.5, curve=1.2) * 0.10,
    "railgun": lambda d, n: zap_arc(d, 500, 9000, q=6, steps=int(70 * d), name=n)
                            * seg_env([(0.0, 1.0), (0.3, 0.3), (1.0, 0.0)], d) * 0.5,
    "drain":   lambda d, n: (lambda nn: bandpass(pink(d, n), 150, 1800)
                             * tremolo(nn, 7.5, 0.75) * 0.6)(n_of(d)),
    "vine":    lambda d, n: debris(d, count=int(22 * d), band=(180, 2200),
                                   decay=(18.0, 40.0), name=n) * 0.5,
    "stone":   lambda d, n: saturate(bandpass(pink(d, n), 140, 1500), 3.2)
                            * ad_env(d, attack=0.02, hold=0.5, curve=1.3) * 0.55,
    "whip":    lambda d, n: debris(d * 0.5, count=5, band=(900, 6000),
                                   decay=(26.0, 60.0), name=n) * 0.45,
    "gravity": lambda d, n: sine(seg_env([(0.0, 34.0), (0.5, 21.0), (1.0, 27.0)], d), d)
                            * swell_env(d, 1.2) * 0.5,
    "eye":     lambda d, n: cry(d, [(0.0, 96.0), (0.5, 128.0), (1.0, 88.0)],
                                [(0.0, "aw"), (0.5, "uh"), (1.0, "oo")], growl=0.55,
                                breath=0.3, name=n) * 0.42,
}


def beam(voice, dur=0.52, name="beam"):
    """A channelled beam. The identity is in the sweep of the filter, the noise
    band riding it, and `BEAM_TEXTURE` — a beam with a static filter sounds like
    a held synth note, which is what a laser is not."""
    p = BEAM_VOICES[voice]
    base = p["base"]
    if p.get("fall"):
        pitch = seg_env([(0.0, base * 3.0), (0.25, base * 1.4), (1.0, base * 0.62)], dur)
    else:
        pitch = seg_env([(0.0, base * 0.58), (0.07, base * 1.12), (0.35, base), (1.0, base * 0.94)], dur)
    lfo = 1.0 + 0.016 * np.sin(2 * np.pi * 6.5 * t_axis(dur))
    index = seg_env([(0.0, p["idx"][0]), (0.2, p["idx"][0] * 0.5), (1.0, p["idx"][1])], dur)

    body = fm(pitch * lfo, p["ratio"], index, dur)
    body += 0.32 * saw(pitch * 0.501, dur)
    body *= ad_env(dur, attack=0.014, hold=0.42, curve=1.4)
    body = svf(body, seg_env([(0.0, p["sweep"][0]), (0.3, p["sweep"][1] * 1.5), (1.0, p["sweep"][1])], dur), q=p["q"])

    tex = bandpass(pink(dur, name + "t"), *p["band"]) * ad_env(dur, attack=0.01, hold=0.5, curve=1.2)
    out = saturate(mixdown(body * (1 - p["mix"] * 0.5), tex * p["mix"]), p["drive"])
    out = mixdown(out, fit(BEAM_TEXTURE[voice](dur, name + "bt"), len(out)))

    if p.get("crack"):
        cr = click(0.018, fc=2600, name=name + "c", shape=1.6)
        out[:len(cr)] += cr * 1.1
    out = chorus(out, rate=1.1, depth_ms=5.0, voices=2, mix=0.26, name="beam" + name)
    return reverb(out, decay=p["rv"][0], damp=5600, mix=p["rv"][1], name="beam", predelay=0.005)


SWING_VOICES = {
    #          band          q   edge  mat      f0    ring  grit  drive tail
    "steel":  dict(band=(900, 4200),  q=6.5, edge=.16, mat="blade", f0=1150, ring=.40, grit=.16, drive=1.4, tail=(.30, .14)),
    "katana": dict(band=(1100, 5000), q=7.5, edge=.22, mat="blade", f0=1480, ring=.46, grit=.10, drive=1.2, tail=(.34, .15)),
    "heavy":  dict(band=(500, 2600),  q=5.0, edge=.08, mat="iron",  f0=380,  ring=.36, grit=.28, drive=1.6, tail=(.32, .15)),
    "claw":   dict(band=(600, 3100),  q=5.0, edge=.05, mat="chitin", f0=720, ring=.30, grit=.48, drive=1.7, tail=(.26, .13), modal=.15),
    "talon":  dict(band=(1000, 4400), q=6.0, edge=.09, mat="chitin", f0=1250, ring=.32, grit=.40, drive=1.5, tail=(.24, .12), modal=.15),
    "scythe": dict(band=(520, 2800),  q=5.0, edge=.16, mat="blade", f0=620,  ring=.34, grit=.20, drive=1.5, tail=(.50, .20)),
    "holy":   dict(band=(950, 4000),  q=5.5, edge=.14, mat="bell",  f0=523,  ring=.42, grit=.06, drive=1.1, tail=(.75, .24)),
    "cursed": dict(band=(360, 2100),  q=4.5, edge=.11, mat="iron",  f0=260,  ring=.36, grit=.34, drive=1.9, tail=(.65, .23)),
    "bone":   dict(band=(480, 2700),  q=5.0, edge=.06, mat="bone",  f0=520,  ring=.40, grit=.34, drive=1.6, tail=(.30, .14), modal=.55),
    "wet":    dict(band=(300, 1900),  q=3.6, edge=.03, mat="flesh", f0=180,  ring=.38, grit=.50, drive=1.8, tail=(.26, .13), modal=.2),
}


def swing(voice, dur=0.34, name="sw"):
    """An edge moving through air and connecting.

    Three layers the old slash sounds were missing: the resonance traces a
    doppler arc rather than sitting still, a faint edge tone rides its peak
    (the whistle a real blade makes), and the weapon's own material rings
    briefly after the cut, which is what separates steel from bone from claw."""
    p = SWING_VOICES[voice]
    n = n_of(dur)
    rush = air(dur, band=p["band"], q=p["q"], arc=1.5, turb=0.35, edge=p["edge"], body=0.7, name=name + "a")
    # peaks where the edge passes, then gets out of the way — a swing that
    # sustains for its whole length reads as wind, not as a cut
    rush *= seg_env([(0.0, 0.04), (0.34, 1.0), (0.5, 0.45), (1.0, 0.0)], dur) ** 1.25
    grit = saturate(bandpass(pink(dur, name + "g"), 160, 1200), 3.0) * exp_env(dur, decay=9.0) * p["grit"]
    cut_t = dur * 0.42
    ring = strike(p["mat"], p["f0"], dur * 0.55, hardness=0.85, damp=1.4, name=name + "r",
                  ring=MATERIALS[p["mat"]]["ring"] * p.get("modal", 1.0)) * p["ring"]
    out = mixdown(rush * 0.85, grit)
    at(out, cut_t, ring)
    out = saturate(out, p["drive"])
    return reverb(out, decay=p["tail"][0], damp=6200, mix=p["tail"][1], name="sw", predelay=0.004)


THROWN_VOICES = {
    #          mat      f0    rate  band          mass  grit  tail
    "axe":    dict(mat="iron",  f0=300, rate=7.0, band=(420, 2400), mass=.70, grit=.24, tail=(.36, .16)),
    "hammer": dict(mat="iron",  f0=190, rate=5.0, band=(280, 1700), mass=.95, grit=.28, tail=(.42, .18)),
    "bone":   dict(mat="bone",  f0=430, rate=8.5, band=(460, 2700), mass=.50, grit=.32, tail=(.32, .15)),
    "shuriken": dict(mat="blade", f0=1500, rate=13.0, band=(1100, 5000), mass=.22, grit=.12, tail=(.22, .11)),
    "knife":  dict(mat="blade", f0=1050, rate=10.0, band=(850, 4000), mass=.30, grit=.14, tail=(.24, .12)),
    "card":   dict(mat="wood",  f0=900, rate=11.0, band=(600, 3000), mass=.14, grit=.08, tail=(.18, .09)),
    "cursed": dict(mat="iron",  f0=230, rate=6.0, band=(230, 1600), mass=.75, grit=.36, tail=(.60, .23)),
    "holy":   dict(mat="bell",  f0=523, rate=7.5, band=(700, 3600), mass=.55, grit=.08, tail=(.75, .25)),
    "shovel": dict(mat="steel", f0=260, rate=5.5, band=(260, 1900), mass=.80, grit=.48, tail=(.38, .17)),
    "head":   dict(mat="flesh", f0=120, rate=4.0, band=(160, 1200), mass=1.0, grit=.52, tail=(.36, .16)),
}


def thrown(voice, dur=0.44, name="th"):
    """A weapon tumbling end over end.

    The tumble is the identity: one air pulse per rotation, with the object's
    material ringing on each pass. A thrown axe that is one continuous whoosh
    is indistinguishable from a thrown anything — this is the audio equivalent
    of the silhouette work in GLProjectileRenderers."""
    p = THROWN_VOICES[voice]
    n = n_of(dur)
    # The tumble is AIR, not repeated impacts: an object turning over in flight
    # chops the airflow, it does not strike anything. Ringing the material once
    # per rotation — which is what this used to do — is the exact sound of
    # somebody banging a bucket at 7Hz.
    spin = tremolo(n, p["rate"], depth=0.62, shape="sine")
    spin = lowpass(spin, 90, order=1)
    rush = air(dur, band=p["band"], q=4.0, arc=1.1, turb=0.3, body=0.85, name=name + "a") * spin
    grit = saturate(bandpass(pink(dur, name + "g"), 130, 1100), 2.4) * spin * swell_env(dur, 1.0) * p["grit"]
    # the weapon rings ONCE, as it leaves the hand
    release = strike(p["mat"], p["f0"], 0.2, hardness=0.85, damp=1.3, name=name + "r",
                     ring=MATERIALS[p["mat"]]["ring"] * 0.7) * 0.5
    mass = sine(seg_env([(0.0, 175), (1.0, 58)], 0.16), 0.16) * exp_env(0.16, decay=6.0) * p["mass"]
    out = mixdown(rush * 0.85, grit, pad_to(release, dur), pad_to(saturate(mass, 2.0), dur))
    return reverb(saturate(out, 2.0), decay=p["tail"][0], damp=6000, mix=p["tail"][1],
                  name="th", predelay=0.004)


SHAFT_VOICES = {
    #           release           band          q   flut  mass  hiss  tail
    "arrow":  dict(rel="string", band=(900, 3800), q=6.0, flut=.20, mass=.55, hiss=.07, tail=(.26, .12)),
    "spear":  dict(rel="grunt",  band=(380, 2100), q=4.5, flut=.06, mass=.95, hiss=.03, tail=(.32, .15)),
    "thorn":  dict(rel="snap",   band=(700, 3000), q=5.5, flut=.10, mass=.45, hiss=.05, tail=(.22, .11)),
    "dart":   dict(rel="puff",   band=(800, 3400), q=6.0, flut=.06, mass=.34, hiss=.10, tail=(.20, .10)),
    "spike":  dict(rel="snap",   band=(1000, 4000), q=6.5, flut=.08, mass=.60, hiss=.08, tail=(.30, .14)),
}


def shaft(voice, dur=0.30, name="sh"):
    """Something long and pointed flying point-first. Two thirds of the sound
    is the release — a bowstring, a thrown grunt, a puff of breath — because
    that is the part that happens near the listener."""
    p = SHAFT_VOICES[voice]
    n = n_of(dur)
    if p["rel"] == "string":
        rel = modal("wood", 210, 0.1, damp=2.4, name=name + "s") * 0.8
        rel = mixdown(rel, click(0.004, fc=1800, name=name + "sc") * 0.6)
    elif p["rel"] == "grunt":
        rel = cry(0.14, [(0, 150), (1.0, 96)], [(0, "uh"), (1.0, "aw")], growl=0.25, breath=0.5,
                  amp_keys=((0, 0), (0.1, 1.0), (1.0, 0.0)), name=name + "g") * 0.55
    elif p["rel"] == "puff":
        rel = highpass(pink(0.07, name + "p"), 1800) * exp_env(0.07, attack=0.002, decay=9.0) * 0.9
    else:
        rel = click(0.006, fc=2400, name=name + "sn", shape=1.2) * 0.9
    fly = air(dur, band=p["band"], q=p["q"], arc=1.8, turb=0.2, edge=0.1, body=0.8, name=name + "a")
    fly *= exp_env(dur, attack=0.004, decay=5.0)
    flut = bandpass(noise(dur, name + "f"), 1400, 5000) * tremolo(n, 17, 0.75, shape="pulse")
    flut *= swell_env(dur, 1.4) * p["flut"]
    mass = sine(seg_env([(0.0, 190), (1.0, 62)], 0.14), 0.14) * exp_env(0.14, decay=6.5) * p["mass"]
    hiss = bandpass(pink(dur, name + "h"), 2600, 8000) * swell_env(dur, 2.0) * p["hiss"]
    out = mixdown(fly * 0.75, flut, hiss, pad_to(saturate(mass, 2.0), dur))
    out[:len(rel)] += rel
    return reverb(saturate(out, 1.9), decay=p["tail"][0], damp=6400, mix=p["tail"][1],
                  name="sh", predelay=0.003)


LOB_VOICES = {
    #           mat      f0    size  wet   deb  fizz  tail
    "grenade": dict(mat="steel", f0=190, size=.85, wet=.0,  deb=.35, fizz=.10, tail=(.55, .22)),
    "mine":    dict(mat="steel", f0=340, size=.35, wet=.0,  deb=.10, fizz=.25, tail=(.35, .16)),
    "flask":   dict(mat="glass", f0=880, size=.45, wet=.55, deb=.45, fizz=.55, tail=(.45, .20)),
    "mud":     dict(mat="earth", f0=110, size=.70, wet=.85, deb=.20, fizz=.05, tail=(.30, .14)),
    "anvil":   dict(mat="iron",  f0=155, size=1.0, wet=.0,  deb=.25, fizz=.0,  tail=(.75, .26)),
    "rock":    dict(mat="stone", f0=130, size=.95, wet=.0,  deb=.55, fizz=.0,  tail=(.55, .22)),
    "ink":     dict(mat="flesh", f0=95,  size=.60, wet=1.0, deb=.10, fizz=.25, tail=(.32, .15)),
    "rune":    dict(mat="stone", f0=330, size=.45, wet=.0,  deb=.12, fizz=.30, tail=(.70, .26)),
    "cannon":  dict(mat="iron",  f0=98,  size=1.0, wet=.0,  deb=.40, fizz=.0,  tail=(.85, .28)),
}


def lobbed(voice, dur=0.66, name="lob"):
    """An object thrown on an arc and landing. The arc is short and quiet; the
    landing is the sound. `mat` decides what it lands as."""
    p = LOB_VOICES[voice]
    arc_d = dur * 0.5
    arc = air(arc_d, band=(400, 2400), q=5.0, arc=1.6, turb=0.3, name=name + "arc") * 0.45
    imp_t = dur * 0.5
    imp_d = dur - imp_t
    land = strike(p["mat"], p["f0"], imp_d, hardness=0.9, damp=1.0, name=name + "l")
    sub = sine(seg_env([(0.0, 130 * p["size"]), (1.0, 38 * p["size"])], min(0.26, imp_d)),
               min(0.26, imp_d)) * exp_env(min(0.26, imp_d), decay=6.0)
    layers = [land * 0.9, pad_to(saturate(sub, 2.4) * p["size"], imp_d)]
    if p["wet"] > 0:
        layers.append(water(imp_d * 0.8, size=1.6, bubbles=6, foam=0.5, name=name + "w") * p["wet"] * 0.7)
    if p["deb"] > 0:
        layers.append(debris(imp_d, count=int(13 * p["deb"]) + 3,
                             band=(400, 4000) if p["mat"] != "glass" else (1800, 9000),
                             wet=p["wet"] * 0.5, name=name + "d") * p["deb"] * 0.6)
    if p["fizz"] > 0:
        layers.append(gas(imp_d * 0.9, band=(700, 6000), fizz=0.8, name=name + "f") * p["fizz"] * 0.5)
    impact = saturate(mixdown(*layers), 2.2)
    out = np.zeros(n_of(dur))
    out[:len(arc)] += arc
    at(out, imp_t, impact)
    return reverb(out, decay=p["tail"][0], damp=4600, mix=p["tail"][1], name="lob", predelay=0.006)


SLAM_VOICES = {
    #            mat      f0   sub  deb   rumble crack tail       size
    "earth":   dict(mat="earth", f0=78,  sub=1.0, deb=.85, rum=.9, crk=.4, tail=(1.0, .26), size=1.3, damp=1.0),
    "stone":   dict(mat="stone", f0=95,  sub=.95, deb=1.0, rum=.8, crk=.6, tail=(.95, .26), size=1.25, damp=1.0),
    "ice":     dict(mat="ice",   f0=210, sub=.75, deb=.75, rum=.6, crk=1.0, tail=(1.0, .28), size=1.2, damp=1.4),
    "metal":   dict(mat="steel", f0=140, sub=.70, deb=.35, rum=.4, crk=.6, tail=(.85, .26), size=1.0, damp=2.0),
    "shield":  dict(mat="iron",  f0=190, sub=.60, deb=.25, rum=.3, crk=.6, tail=(.80, .25), size=1.0, damp=2.2),
    "flesh":   dict(mat="flesh", f0=70,  sub=1.0, deb=.45, rum=.85, crk=.2, tail=(.75, .24), size=1.15, damp=1.0),
    "dark":    dict(mat="flesh", f0=62,  sub=1.0, deb=.30, rum=1.0, crk=.2, tail=(1.2, .30), size=1.4, damp=0.9),
}


def slam(voice, dur=0.9, name="slam"):
    """A body or a weapon meeting the ground, and the ground answering.

    Not an explosion: the old set routed every ground ability through the bomb
    generator, so a druid growing roots and a barbarian splitting the earth both
    detonated. A slam is contact + material + displaced debris + rumble."""
    p = SLAM_VOICES[voice]
    n = n_of(dur)
    hit = strike(p["mat"], p["f0"], dur * 0.75, hardness=1.0, damp=0.85 * p["damp"], name=name + "h")
    sub = sine(seg_env([(0.0, 88), (0.2, 46), (1.0, 26)], dur * 0.6), dur * 0.6)
    sub *= exp_env(dur * 0.6, attack=0.003, decay=3.4)
    rum = lowpass(pink(dur, name + "r"), 240, order=1) * ad_env(dur, attack=0.02, hold=0.35, curve=1.4)
    crk = click(0.03, fc=1800, name=name + "c", shape=1.6)
    deb = debris(dur, count=int(18 * p["deb"]) + 4,
                 band=(300, 2600) if p["mat"] != "ice" else (900, 6000),
                 spread=(0.03, 0.7), name=name + "d") * 0.55
    out = mixdown(hit * 0.9, pad_to(saturate(sub, 2.4) * p["sub"], dur),
                  rum * p["rum"] * 0.6, deb * p["deb"])
    out[:len(crk)] += crk * p["crk"]
    out = saturate(out, 1.9)
    return reverb(out, decay=p["tail"][0], damp=3400, mix=p["tail"][1], name="slam",
                  predelay=0.012, size=p["size"])


BURST_VOICES = {
    #            kind    band            low   grain  tone         tail
    "gas":     dict(kind="gas",  band=(400, 5200), low=.35, grain=.15, tone=None,        tail=(.85, .26)),
    "spore":   dict(kind="gas",  band=(300, 3400), low=.45, grain=.40, tone=None,        tail=(.95, .28)),
    "root":    dict(kind="wood", band=(180, 2400), low=.70, grain=.85, tone=(110, 3.0),  tail=(.80, .25)),
    "thorn":   dict(kind="wood", band=(400, 4200), low=.50, grain=.95, tone=(160, 4.0),  tail=(.70, .24)),
    "vortex":  dict(kind="suck", band=(120, 3600), low=.85, grain=.20, tone=(58, 1.6),   tail=(1.1, .30)),
    "soul":    dict(kind="suck", band=(260, 4200), low=.55, grain=.10, tone=(147, 1.4),  tail=(1.3, .32)),
    "water":   dict(kind="water", band=(300, 4600), low=.70, grain=.30, tone=None,       tail=(.85, .26)),
    "surge":   dict(kind="elec", band=(600, 8000), low=.45, grain=.25, tone=(220, 2.4),  tail=(.70, .24)),
    "swarm":   dict(kind="swarm", band=(900, 7000), low=.35, grain=.60, tone=None,       tail=(.65, .22)),
}


def burst(voice, dur=0.8, name="bst"):
    """An area effect that blooms rather than detonates — gas, roots, a vortex,
    a swarm. These used to share the explosion generator, which is why a poison
    cloud and a frag grenade were the same event."""
    p = BURST_VOICES[voice]
    n = n_of(dur)
    k = p["kind"]
    if k == "gas":
        core = gas(dur, band=p["band"], fizz=0.7, name=name + "g")
    elif k == "water":
        core = water(dur, size=1.2, bubbles=12, foam=0.7, name=name + "w")
    elif k == "elec":
        core = zap_arc(dur, p["band"][0], p["band"][1], q=7, steps=70, name=name + "e")
        core *= seg_env([(0.0, 0.1), (0.25, 1.0), (1.0, 0.0)], dur)
    elif k == "suck":
        # reversed swell: energy pulled inward, then the collapse
        core = svf(pink(dur, name + "s"), seg_env([(0.0, p["band"][1]), (1.0, p["band"][0])], dur),
                   q=5, mode="bp")
        core *= np.linspace(0.15, 1.0, n) ** 2.0
    elif k == "swarm":
        core = bandpass(pink(dur, name + "sw"), *p["band"]) * tremolo(n, 47, 0.8, shape="pulse")
        core *= swell_env(dur, 1.1)
    else:  # wood: fibres tearing and splitting
        core = saturate(bandpass(pink(dur, name + "wd"), *p["band"]), 2.6)
        core *= seg_env([(0.0, 0.2), (0.15, 1.0), (0.6, 0.5), (1.0, 0.0)], dur)
    # splintering wood, snapping fibre, scattering shell — filtered grains, not
    # a chord of resonators
    grains = debris(dur, count=int(22 * p["grain"]) + 3,
                    band=(200, 2400) if k == "wood" else (700, 5600),
                    decay=(14.0, 34.0), spread=(0.0, 0.75), name=name + "gr") * 0.5
    low = sine(seg_env([(0.0, 96), (1.0, 40)], dur * 0.7), dur * 0.7) * exp_env(dur * 0.7, decay=3.6)
    layers = [core * 0.85, grains * p["grain"], pad_to(saturate(low, 2.2) * p["low"], dur)]
    if p["tone"] is not None:
        tf, td = p["tone"]
        tone = fm(seg_env([(0.0, tf * 1.6), (1.0, tf * 0.8)], dur), 1.41,
                  seg_env([(0.0, 7.0), (1.0, 1.5)], dur), dur)
        layers.append(tone * exp_env(dur, attack=0.01, decay=td) * 0.5)
    out = saturate(mixdown(*layers), 1.9)
    return reverb(out, decay=p["tail"][0], damp=4200, mix=p["tail"][1], name="bst",
                  predelay=0.008, size=1.2)


WAVE_VOICES = {
    #           band            q   tone           drive body  tail
    "wind":   dict(band=(240, 2400),  q=4.0, tone=(150, 480),  drive=1.2, body=.75, tail=(.44, .19)),
    "sonic":  dict(band=(360, 3600),  q=5.0, tone=(220, 440),  drive=1.4, body=.90, tail=(.48, .21)),
    "sand":   dict(band=(300, 2900),  q=3.2, tone=(130, 340),  drive=1.5, body=.80, tail=(.38, .17)),
    "flame":  dict(band=(280, 2200),  q=2.4, tone=(110, 280),  drive=1.9, body=.85, tail=(.44, .19)),
    "acid":   dict(band=(440, 3400),  q=3.6, tone=(170, 380),  drive=1.6, body=.75, tail=(.38, .17)),
    "impact": dict(band=(180, 1900),  q=3.0, tone=(90, 220),   drive=2, body=1.1, tail=(.42, .19)),
    "water":  dict(band=(260, 2800),  q=2.8, tone=(105, 250),  drive=1.3, body=.95, tail=(.52, .22)),
}


def wavefront(voice, dur=0.52, name="wv"):
    """A sweeping crescent — wind, sound, sand, flame. Built on `air` so it gets
    the doppler arc and turbulence, with a low tonal element underneath that
    gives it a front edge instead of being pure hiss."""
    p = WAVE_VOICES[voice]
    sweep = air(dur, band=p["band"], q=p["q"], arc=1.05, turb=0.4, edge=0.04, body=0.85, name=name + "a")
    sweep *= seg_env([(0.0, 0.08), (0.3, 1.0), (0.55, 0.5), (1.0, 0.0)], dur) ** 1.15
    lo, hi = p["tone"]
    tone = fm(seg_env([(0.0, lo), (0.5, hi), (1.0, lo * 0.9)], dur), 1.5,
              seg_env([(0.0, 5.0), (1.0, 0.6)], dur), dur)
    tone *= seg_env([(0.0, 0.0), (0.25, 1.0), (1.0, 0.0)], dur) ** 1.2 * p["body"] * 0.7
    if voice == "flame":
        sweep = mixdown(sweep * 0.7, fire(dur, low=110, roar=0.8, crackles=14, name=name + "f") * 0.6)
    if voice == "water":
        sweep = mixdown(sweep * 0.6, water(dur, size=1.4, bubbles=7, foam=0.6, name=name + "w") * 0.7)
    if voice == "acid":
        sweep = mixdown(sweep * 0.75, gas(dur, band=(800, 6000), fizz=1.0, name=name + "g") * 0.5)
    out = saturate(mixdown(sweep, tone), p["drive"])
    return reverb(out, decay=p["tail"][0], damp=5400, mix=p["tail"][1], name="wv", predelay=0.005)


GUN_VOICES = {
    #            crack  body_hi bodydec sub   mech  smoke tail        size
    "rifle":   dict(crk=1.0, hi=6500, dec=11.0, sub=.55, mech=.28, smk=.10, tail=(.50, .20), size=1.0),
    "heavy":   dict(crk=1.1, hi=5200, dec=8.0,  sub=.85, mech=.34, smk=.12, tail=(.70, .24), size=1.3),
    "light":   dict(crk=.85, hi=8000, dec=14.0, sub=.35, mech=.30, smk=.06, tail=(.38, .17), size=0.8),
    "flint":   dict(crk=.75, hi=3600, dec=6.5,  sub=.70, mech=.45, smk=.60, tail=(.85, .27), size=1.3),
    "cannon":  dict(crk=.60, hi=2400, dec=4.0,  sub=1.0, mech=.15, smk=.75, tail=(1.1, .30), size=1.6),
}


def gun(voice, dur=0.34, name="gun"):
    """A firearm. The crack is 15ms of it; everything else is the muzzle blast
    expanding, the action cycling, and the room answering."""
    p = GUN_VOICES[voice]
    crack = saturate(click(0.014, fc=1600, name=name + "c", shape=1.1), 5.0) * p["crk"]
    body = svf(noise(0.2, name + "b"), seg_env([(0.0, p["hi"]), (1.0, 460)], 0.2), q=2.4)
    body *= exp_env(0.2, attack=0.0008, decay=p["dec"])
    thump = sine(seg_env([(0.0, 122), (1.0, 42)], 0.13), 0.13) * exp_env(0.13, decay=7.0)
    # the action cycling: a couple of dry clicks, not a tuned plate
    mech = mixdown(bandpass(noise(0.02, name + "m1"), 1800, 9000) * exp_env(0.02, decay=30.0) * 0.7,
                   bandpass(noise(0.035, name + "m2"), 700, 4000) * exp_env(0.035, decay=22.0) * 0.5)
    smoke = gas(0.3, band=(500, 5000), fizz=0.5, name=name + "s") * p["smk"]
    out = np.zeros(n_of(dur))
    out[:len(body)] += body * 0.8
    out[:len(thump)] += saturate(thump, 2.4) * p["sub"]
    out[:len(crack)] += crack
    at(out, 0.045, mech * p["mech"])
    at(out, 0.01, smoke)
    return reverb(saturate(out, 1.9), decay=p["tail"][0], damp=3600, mix=p["tail"][1],
                  name="gun", predelay=0.008, size=p["size"])


CHAIN_VOICES = {
    "iron":   dict(mat="iron", f0=1450, hits=3, decay=0.66, rasp=.9, ring=.20, tail=(.38, .16)),
    "rope":   dict(mat="wood", f0=420,  hits=3, decay=0.80, rasp=1.2, ring=.08, tail=(.28, .13)),
    "string": dict(mat="wood", f0=900,  hits=3, decay=0.68, rasp=.95, ring=.14, tail=(.32, .15)),
}


def chain(voice, dur=0.42, name="ch"):
    """Links or fibres, each catching the next. The falling pitch across the
    hits is what makes it read as a chain paying out rather than a rattle."""
    p = CHAIN_VOICES[voice]
    n = n_of(dur) + n_of(0.16)
    out = np.zeros(n)
    for i in range(p["hits"]):
        f = p["f0"] * (p["decay"] ** i) + 180
        g = strike(p["mat"], f, 0.09, hardness=0.95 - 0.08 * i, damp=2.6, name=name + str(i),
                   ring=p["ring"])
        at(out, i * (dur * 0.68 / p["hits"]), g, 1.0 - i * 0.11)
    # the rattle between the links carries the sound; the links themselves only
    # tint it
    rasp = bandpass(pink(dur, name + "r"), 400, 3600) * swell_env(dur, 1.2) * p["rasp"] * 0.55
    rasp = mixdown(rasp, debris(dur * 0.8, count=9, band=(700, 5200), decay=(20.0, 44.0),
                                spread=(0.0, 0.8), name=name + "rt") * p["rasp"] * 0.45)
    out[:len(rasp)] += rasp
    return reverb(saturate(out, 2.2), decay=p["tail"][0], damp=6200, mix=p["tail"][1],
                  name="ch", predelay=0.004)


CHIME_VOICES = {
    #           f0     mat     damp  strike shim  choir tail
    "holy":   dict(f0=523.25, mat="bell", damp=9.0, stk=.95, shim=.40, choir=.18, tail=(.65, .22)),
    "smite":  dict(f0=392.00, mat="bell", damp=3.4, stk=.95, shim=.45, choir=.30, tail=(1.0, .28)),
    "rune":   dict(f0=220.00, mat="stone", damp=1.4, stk=.75, shim=.22, choir=.18, tail=(.8, .26)),
}


def chime(voice, dur=0.7, name="chm"):
    """A struck resonant object used as magic. Short — the old holy bolt rang
    for 2.2 seconds, so a cleric firing twice a second built a chord cluster
    that never resolved."""
    p = CHIME_VOICES[voice]
    body = modal(p["mat"], p["f0"], dur, damp=p["damp"], name=name)
    stk = click(0.008, fc=3200, name=name + "s", shape=1.6) * p["stk"]
    shim = sparkle(dur, 4000, 12000, count=10, name=name + "sp") * p["shim"]
    ch = fm(p["f0"] * 0.5, 2.0, seg_env([(0.0, 1.2), (1.0, 0.2)], dur), dur)
    ch = chorus(ch * swell_env(dur, 1.5) * p["choir"] * 0.5, rate=0.5, depth_ms=9, voices=3,
                mix=0.5, name=name + "c")
    out = mixdown(body * 0.75, shim, ch)
    out[:len(stk)] += stk
    return reverb(out, decay=p["tail"][0], damp=7000, mix=p["tail"][1], name="chm", predelay=0.01)


def elec(dur=0.5, size=1.0, colour=(600, 9000), forks=1, name="el"):
    """Lightning. The arc is a resonance jumping at audio-adjacent rates; the
    crack is the air column collapsing; the rumble is the distance."""
    n = n_of(dur)
    arc = zap_arc(dur, colour[0], colour[1], q=9, steps=int(90 * size), name=name)
    arc *= exp_env(dur, attack=0.001, decay=6.0 / size)
    crack = saturate(click(0.028, fc=1400, name=name + "c", shape=1.3), 4.0)
    rum = lowpass(pink(dur, name + "r"), 380, order=1) * exp_env(dur, attack=0.012, decay=3.0) * 0.55
    out = mixdown(arc * 0.8, rum * size)
    out[:len(crack)] += crack * size
    rng = np.random.default_rng(seed_of(name + "fk"))
    for i in range(forks - 1):
        t0 = rng.uniform(0.05, dur * 0.55)
        fd = rng.uniform(0.1, 0.22)
        f = zap_arc(fd, colour[0] * 1.4, colour[1], q=10, steps=30, name=name + "f" + str(i))
        at(out, t0, f * exp_env(fd, decay=9.0) * rng.uniform(0.3, 0.6))
    return reverb(saturate(out, 2.1), decay=0.85 * size, damp=3800, mix=0.26, name="el",
                  predelay=0.01, size=1.2)


def machine(dur=0.45, servo=(300, 900), clank=1.0, whine=0.4, name="mch"):
    """Servos and mechanism — the sound of something built rather than grown."""
    n = n_of(dur)
    sv = saw(seg_env([(0.0, servo[0]), (0.5, servo[1]), (1.0, servo[0] * 0.8)], dur), dur)
    sv = res_lp(sv, 1800, q=3.0) * ad_env(dur, attack=0.01, hold=0.4, curve=1.3) * 0.4
    wh = sine(seg_env([(0.0, 2400), (1.0, 3600)], dur), dur) * swell_env(dur, 2.0) * whine * 0.16
    ck = mixdown(strike("steel", 620, 0.11, hardness=1.0, damp=2.4, name=name + "k1") * 0.8,
                 pad_to(thud(150, 0.16, drive=1.6, name=name + "k2") * 0.6, 0.11))
    out = mixdown(sv, wh)
    at(out, dur * 0.25, ck * clank)
    return reverb(saturate(out, 2.0), decay=0.45, damp=5000, mix=0.2, name="mch", predelay=0.005)


# ═══════════════════════════ fire, blast, weather ═══════════════════════════

def gen_explosion(dur=1.1, size=1.0, name="boom"):
    """Layered detonation: sub drop + broadband burst + crack + debris + hall."""
    n = n_of(dur)
    sub_dur = dur * 0.5
    sub = sine(seg_env([(0.0, 95 * size), (0.25, 52 * size), (1.0, 26 * size)], sub_dur), sub_dur)
    sub *= exp_env(sub_dur, attack=0.004, decay=3.2)
    sub = saturate(sub, 2.4)

    blast = svf(pink(dur, name + "b"), seg_env([(0.0, 8000), (0.08, 2400), (0.45, 700), (1.0, 220)], dur), q=1.6)
    blast *= exp_env(dur, attack=0.002, decay=3.6)
    crack = saturate(click(0.035, fc=1800, name=name + "c", shape=1.4), 3.2)

    shrapnel = debris(dur, count=22, band=(350, 4500), spread=(0.05, 0.78), name=name + "d")

    out = np.zeros(n)
    out[:len(sub)] += sub
    out += blast * 0.75
    out[:len(crack)] += crack * 0.85
    out += shrapnel * 0.5
    out = saturate(out, 1.8)
    return reverb(out, decay=1.3 * size, damp=3200, mix=0.32, name="boom", predelay=0.014, size=1.5)


def gen_fireball():
    """Wizard / Pyromancer: a mass of fire leaving the hand."""
    dur = 0.62
    body = fire(dur, low=120, roar=1.0, crackles=24, name="fb")
    swell = air(dur, band=(300, 2200), q=3.0, arc=1.2, turb=0.6, name="fbs") * 0.4
    ign = mixdown(click(0.012, fc=900, name="fbi", shape=1.0) * 0.7,
                  gas(0.14, band=(600, 5200), fizz=0.9, name="fbg") * 0.6)
    out = mixdown(body * 0.9, swell)
    out[:len(ign)] += ign
    return reverb(saturate(out, 2.0), decay=0.65, damp=4200, mix=0.24, name="fb", predelay=0.006)


def gen_demon_fire():
    """Warlock: the same throw, but something is alive inside it."""
    dur = 0.72
    base = gen_fireball()
    voice = cry(dur, [(0, 150), (0.4, 96), (1.0, 74)], [(0, "ah"), (0.5, "aw"), (1.0, "oo")],
                growl=0.62, breath=0.25, jitter=0.02, name="dfv")
    voice = saturate(voice, 2.6) * 0.55
    return mixdown(pad_to(base, max(len(base), len(voice)) / SR), voice)


def gen_flambe():
    """Chef: gas ignition — the whump of a pan going up, not a fireball."""
    dur = 0.46
    tick = np.zeros(n_of(dur))
    for i in range(3):
        at(tick, 0.005 + i * 0.022, strike("steel", 2600, 0.03, hardness=1.0, damp=3.0,
                                           name="fl" + str(i)) * (0.5 - 0.12 * i))
    whump = gas(0.3, band=(200, 3600), fizz=0.35, name="flw")
    whump *= seg_env([(0.0, 0.0), (0.04, 1.0), (1.0, 0.0)], 0.3)
    body = fire(dur * 0.8, low=95, roar=0.7, crackles=10, name="flf") * 0.7
    low = sine(seg_env([(0.0, 120), (1.0, 44)], 0.2), 0.2) * exp_env(0.2, decay=6.0) * 0.7
    out = np.zeros(n_of(dur))
    at(out, 0.07, whump * 1.1)
    at(out, 0.07, pad_to(saturate(low, 2.2), dur - 0.07))
    at(out, 0.09, body)
    out += tick
    return reverb(saturate(out, 2.0), decay=0.5, damp=4400, mix=0.22, name="fl")


def gen_meteor():
    """Astronomer: a rock arriving from a long way up."""
    dur = 0.85
    fall = air(dur * 0.7, band=(160, 1500), q=3.0, arc=2.4, turb=0.5, name="mtf")
    fall *= np.linspace(0.05, 1.0, n_of(dur * 0.7)) ** 2.0
    burn = fire(dur * 0.7, low=90, roar=0.75, crackles=14, name="mtb") * 0.55
    land = slam("stone", 0.5, name="mtl")
    out = np.zeros(n_of(dur + 0.5))
    out[:len(fall)] += fall * 0.8
    out[:len(burn)] += burn
    at(out, dur * 0.55, land * 0.9)
    return saturate(out, 1.7)


def gen_inferno_blast():
    dur = 0.95
    roar = fire(dur, low=85, roar=1.2, crackles=30, name="inf")
    boom = gen_explosion(dur=0.8, size=0.8, name="infb")
    beast = cry(dur * 0.8, [(0, 108), (0.35, 138), (1.0, 82)], [(0, "aw"), (0.5, "ah"), (1.0, "aw")],
                growl=0.7, breath=0.3, name="infv") * 0.35
    return saturate(mixdown(roar * 0.75, boom * 0.8, pad_to(beast, dur)), 1.6)


def gen_eruption():
    """Magma Knight: the ground opening, then what comes out of it."""
    dur = 0.95
    crack = slam("stone", 0.45, name="erc")
    spew = mixdown(fire(dur * 0.7, low=78, roar=1.0, crackles=22, name="ers") * 0.85,
                   gas(dur * 0.7, band=(300, 4200), fizz=0.6, pitchfall=False, name="erg") * 0.5)
    sub = sine(seg_env([(0.0, 72), (1.0, 30)], dur * 0.6), dur * 0.6) * exp_env(dur * 0.6, decay=3.0)
    out = mixdown(np.zeros(n_of(dur)), crack * 0.9, saturate(sub, 2.4) * 0.8)
    at(out, 0.09, spew)
    return saturate(out, 1.7)


def gen_napalm():
    """Pilot: something is dropped, then a wall of fire blooms."""
    dur = 0.95
    whistle = air(0.34, band=(900, 2600), q=11, arc=2.0, turb=0.1, edge=0.5, name="npw") * 0.5
    bloom = fire(dur * 0.65, low=95, roar=1.15, crackles=28, name="npf")
    thump = sine(seg_env([(0.0, 110), (1.0, 36)], 0.26), 0.26) * exp_env(0.26, decay=5.0)
    out = np.zeros(n_of(dur))
    out[:len(whistle)] += whistle
    at(out, 0.3, bloom * 0.95)
    at(out, 0.3, saturate(thump, 2.4) * 0.85)
    return reverb(saturate(out, 1.8), decay=0.9, damp=3600, mix=0.26, name="np", predelay=0.01)


def gen_cluster_bomb():
    """Bombardier: one launch, several separate detonations."""
    dur = 1.1
    out = np.zeros(n_of(dur))
    at(out, 0.0, lobbed("grenade", 0.34, name="cbl") * 0.55)
    rng = np.random.default_rng(seed_of("cluster"))
    for i in range(5):
        t0 = 0.3 + i * rng.uniform(0.055, 0.11)
        at(out, t0, gen_explosion(dur=0.5, size=0.5 + 0.12 * i, name="cb%d" % i),
           rng.uniform(0.45, 0.8))
    return saturate(out, 1.6)


def gen_rocket():
    dur = 0.7
    ignite = mixdown(click(0.02, fc=1800, name="rki", shape=1.2) * 0.8,
                     strike("steel", 900, 0.08, hardness=1.0, damp=2.4, name="rkc") * 0.4)
    thrust = svf(pink(dur, "rkt"), seg_env([(0.0, 500), (0.3, 2000), (1.0, 1100)], dur), q=2.2)
    thrust *= ad_env(dur, attack=0.025, hold=0.5, curve=1.3)
    dop = fm(seg_env([(0.0, 260), (1.0, 760)], dur), 2.0, seg_env([(0.0, 6), (1.0, 1.5)], dur), dur)
    dop *= ad_env(dur, attack=0.04, hold=0.5, curve=1.2) * 0.35
    out = saturate(mixdown(thrust * 0.8, dop), 2.3)
    out[:len(ignite)] += ignite
    return reverb(out, decay=0.7, damp=4200, mix=0.24, name="rk", predelay=0.006)


def gen_thunder_strike():
    dur = 1.3
    strike_ = elec(0.55, size=1.3, colour=(400, 8000), forks=3, name="thn")
    boom = gen_explosion(dur=1.1, size=1.25, name="thb")
    sub = sine(seg_env([(0.0, 58), (1.0, 24)], 0.9), 0.9) * exp_env(0.9, attack=0.005, decay=2.6)
    return mixdown(pad_to(strike_, dur) * 0.85, pad_to(boom, dur) * 0.9,
                   pad_to(saturate(sub, 2.0), dur) * 0.75)


def gen_boulder_rumble():
    """Rock tumbling: a rumble bed, grinding, and discrete knocks as it turns."""
    dur = 0.85
    n = n_of(dur)
    rum = lowpass(pink(dur, "bldr"), 240, order=1) * ad_env(dur, attack=0.04, hold=0.45, curve=1.4)
    grind = saturate(bandpass(pink(dur, "grnd"), 260, 1900), 2.4) * ad_env(dur, attack=0.03, hold=0.4, curve=1.3) * 0.42
    # the rock turning over: low thuds, not tuned rings
    knocks = np.zeros(n)
    rng = np.random.default_rng(seed_of("knock"))
    for _ in range(8):
        t0 = rng.uniform(0.01, dur * 0.8)
        kd = rng.uniform(0.07, 0.14)
        k = sine(seg_env([(0.0, rng.uniform(95, 175)), (1.0, 42)], kd), kd) * exp_env(kd, decay=9.0)
        k = mixdown(saturate(k, 2.2), bandpass(noise(kd, "kn%d" % rng.integers(1 << 20)), 200, 1800)
                    * exp_env(kd, decay=16.0) * 0.8)
        at(knocks, t0, k * rng.uniform(0.3, 0.7))
    out = saturate(mixdown(rum * 0.9, grind, knocks * 0.75), 1.9)
    return reverb(out, decay=0.95, damp=2800, mix=0.28, name="bldr", predelay=0.01, size=1.3)


def gen_avalanche():
    """Avalanche: mass moving, ice fracturing inside it."""
    dur = 1.0
    n = n_of(dur)
    body = lowpass(pink(dur, "avb"), 400, order=1) * ad_env(dur, attack=0.05, hold=0.5, curve=1.5)
    hiss = bandpass(pink(dur, "avh"), 900, 6000) * ad_env(dur, attack=0.06, hold=0.45, curve=1.4) * 0.32
    cracks = debris(dur, count=15, band=(600, 6000), decay=(10.0, 26.0), spread=(0.02, 0.85),
                    name="avc") * 0.55
    out = saturate(mixdown(body * 0.95, hiss, cracks * 0.8), 1.8)
    return reverb(out, decay=1.1, damp=3200, mix=0.28, name="av", predelay=0.012, size=1.35)


def gen_tidal_wave():
    dur = 0.95
    body = water(dur, size=0.75, bubbles=16, foam=0.85, name="tide")
    rumble = sine(seg_env([(0.0, 70), (1.0, 34)], dur), dur) * seg_env([(0.0, 0.2), (0.4, 1.0), (1.0, 0.0)], dur)
    return reverb(saturate(mixdown(body * 0.95, rumble * 0.7), 1.7), decay=1.1, damp=3400,
                  mix=0.28, name="tide", predelay=0.012, size=1.4)


def gen_geyser():
    dur = 0.65
    jet = gas(dur, band=(700, 6000), fizz=0.7, pitchfall=False, name="gey")
    surge = water(dur * 0.8, size=1.1, bubbles=10, foam=0.5, name="geyw") * 0.7
    push = fm(seg_env([(0.0, 150), (0.3, 520), (1.0, 260)], dur), 1.5,
              seg_env([(0.0, 7), (1.0, 1)], dur), dur)
    push *= seg_env([(0.0, 0.0), (0.18, 0.9), (1.0, 0.0)], dur) * 0.45
    return reverb(saturate(mixdown(jet * 0.85, surge, push), 1.9), decay=0.7, damp=4600,
                  mix=0.24, name="gey", predelay=0.006)


def gen_splash():
    """Tidecaller primary: a compact slug of water thrown and landing."""
    dur = 0.42
    throw = air(0.18, band=(400, 2400), q=4.0, arc=1.4, turb=0.4, name="spa") * 0.4
    hit = water(0.3, size=1.6, bubbles=8, foam=0.75, name="spw")
    thud = sine(seg_env([(0.0, 145), (1.0, 52)], 0.12), 0.12) * exp_env(0.12, decay=8.0) * 0.6
    out = np.zeros(n_of(dur))
    out[:len(throw)] += throw
    at(out, 0.12, hit * 0.95)
    at(out, 0.12, saturate(thud, 2.2))
    return reverb(saturate(out, 1.8), decay=0.45, damp=4800, mix=0.22, name="spl")


# ═══════════════════════════ creatures ═══════════════════════════
# All of these come out of `cry` — one throat, differing by pitch contour, vowel
# path and roughness. That is why a wolf, a harpy and a minotaur now sound
# related but never interchangeable, where before a "howl" and a "shriek" were
# both routed to the explosion generator.

def gen_howl():
    """Wolf: rise, hold, fall — the shape is the whole animal."""
    dur = 1.05
    v = cry(dur, [(0.0, 180), (0.18, 330), (0.55, 350), (0.8, 300), (1.0, 200)],
            [(0.0, "oo"), (0.25, "ah"), (0.75, "ah"), (1.0, "oo")],
            growl=0.12, breath=0.22, jitter=0.008, open_q=0.62,
            amp_keys=((0.0, 0.0), (0.14, 1.0), (0.72, 0.92), (1.0, 0.0)), name="howl")
    low = cry(dur, [(0.0, 90), (0.3, 118), (1.0, 82)], [(0.0, "oo"), (1.0, "aw")],
              growl=0.3, breath=0.1, amp_keys=((0.0, 0.0), (0.2, 0.7), (0.8, 0.5), (1.0, 0.0)),
              name="howl2") * 0.45
    out = mixdown(v * 0.9, low)
    out = chorus(out, rate=0.4, depth_ms=6, voices=2, mix=0.22, name="howlc")
    return reverb(out, decay=1.5, damp=3600, mix=0.32, name="howl", predelay=0.02, size=1.6)


def gen_screech():
    """Harpy: bird, not ghost — high, fast vibrato, breaks into noise."""
    dur = 0.52
    v = cry(dur, [(0.0, 720), (0.15, 1320), (0.5, 1140), (1.0, 660)],
            [(0.0, "eh"), (0.4, "ah"), (1.0, "eh")],
            growl=0.18, breath=0.45, jitter=0.035, open_q=0.42,
            amp_keys=((0.0, 0.0), (0.07, 1.0), (0.6, 0.75), (1.0, 0.0)), name="scr")
    rasp = bandpass(noise(dur, "scrr"), 2200, 8000) * tremolo(n_of(dur), 33, 0.7) * swell_env(dur, 1.2) * 0.3
    wings = bandpass(pink(dur, "scrw"), 300, 2400) * tremolo(n_of(dur), 12, 0.85, shape="pulse") * 0.25
    return reverb(saturate(mixdown(v * 0.85, rasp, wings), 1.8), decay=0.7, damp=5200,
                  mix=0.26, name="scr", predelay=0.008)


def gen_wail():
    """Banshee: a voice that should not still be a voice."""
    dur = 1.0
    v = cry(dur, [(0.0, 380), (0.22, 940), (0.55, 780), (1.0, 340)],
            [(0.0, "ah"), (0.35, "eh"), (0.7, "uh"), (1.0, "oo")],
            growl=0.08, breath=0.35, jitter=0.028, open_q=0.5,
            amp_keys=((0.0, 0.0), (0.12, 1.0), (0.68, 0.8), (1.0, 0.0)), name="wail")
    ghost = cry(dur, [(0.0, 208), (0.3, 590), (1.0, 190)], [(0.0, "oo"), (0.5, "uh"), (1.0, "oo")],
                breath=0.5, jitter=0.04, name="wail2") * 0.4
    air_ = bandpass(pink(dur, "wailb"), 800, 5200) * swell_env(dur, 1.3) * 0.22
    out = mixdown(v * 0.85, ghost, air_)
    out = chorus(out, rate=0.55, depth_ms=12, voices=3, mix=0.35, name="wailc")
    return reverb(out, decay=1.6, damp=4200, mix=0.34, name="wail", predelay=0.024, size=1.7)


def gen_banshee_cry():
    """Banshee primary: the wail compressed into something fireable twice a second."""
    dur = 0.46
    v = cry(dur, [(0.0, 520), (0.18, 1120), (1.0, 470)],
            [(0.0, "ah"), (0.4, "ee"), (1.0, "eh")],
            breath=0.4, jitter=0.03, amp_keys=((0.0, 0.0), (0.08, 1.0), (0.55, 0.7), (1.0, 0.0)),
            name="bcry")
    sweep = air(dur, band=(600, 4600), q=5.0, arc=1.2, turb=0.4, name="bcrya") * 0.4
    return reverb(saturate(mixdown(v * 0.9, sweep), 1.7), decay=0.75, damp=4800, mix=0.26,
                  name="bcry", predelay=0.008)


def gen_bull_bellow():
    """Minotaur: chest, nostrils and horn — deliberately short. A sustained
    low tone here is a foghorn, which is what this used to be."""
    dur = 0.62
    v = cry(dur, [(0.0, 88), (0.2, 132), (0.6, 118), (1.0, 74)],
            [(0.0, "aw"), (0.4, "ah"), (1.0, "aw")],
            growl=0.55, breath=0.3, jitter=0.02, open_q=0.7,
            amp_keys=((0.0, 0.0), (0.08, 1.0), (0.65, 0.85), (1.0, 0.0)), name="bull")
    snort = bandpass(noise(0.16, "snrt"), 200, 2200) * exp_env(0.16, attack=0.004, decay=8.0) * 0.4
    horn = strike("bone", 320, 0.3, hardness=0.7, damp=1.4, name="hrn") * 0.35
    out = mixdown(saturate(v, 2.4) * 0.9, pad_to(snort, dur), pad_to(horn, dur))
    return reverb(out, decay=0.85, damp=3000, mix=0.28, name="bull", predelay=0.012, size=1.3)


def gen_roar(f0=96, dur=0.5, growl=0.7, name="roar"):
    """Generic beast roar — bear, fenrir, ghoul."""
    v = cry(dur, [(0.0, f0 * 0.85), (0.25, f0 * 1.25), (1.0, f0 * 0.8)],
            [(0.0, "uh"), (0.4, "ah"), (1.0, "aw")],
            growl=growl, breath=0.28, jitter=0.022, open_q=0.68,
            amp_keys=((0.0, 0.0), (0.06, 1.0), (0.6, 0.8), (1.0, 0.0)), name=name)
    return saturate(v, 2.6)


def gen_raise_dead():
    """A chorus of throats out of the ground, not a filtered drone."""
    dur = 1.15
    voices = np.zeros(n_of(dur))
    for i, (f, det, g) in enumerate(((104, 1.0, 0.7), (139, 1.008, 0.45), (156, 0.991, 0.4))):
        v = cry(dur, [(0.0, f * det * 0.9), (0.4, f * det), (1.0, f * det * 0.78)],
                [(0.0, "oo"), (0.45, "uh"), (1.0, "oo")],
                growl=0.35, breath=0.3, jitter=0.03, open_q=0.6,
                amp_keys=((0.0, 0.0), (0.22, 0.95), (0.7, 0.8), (1.0, 0.0)), name="rd%d" % i)
        voices = mixdown(voices, v * g)
    dirt = debris(dur, count=11, band=(120, 1200), decay=(12.0, 26.0), spread=(0.0, 0.7),
                  name="rddirt") * 0.45
    out = saturate(mixdown(voices * 0.75, dirt * 0.7), 1.8)
    out = chorus(out, rate=0.3, depth_ms=13, voices=3, mix=0.35, name="rd")
    return reverb(out, decay=1.4, damp=3200, mix=0.34, name="rd", predelay=0.018, size=1.5)


def gen_bat_swarm():
    dur = 0.62
    n = n_of(dur)
    wings = bandpass(pink(dur, "bat"), 350, 4200) * tremolo(n, 21, 0.9, shape="pulse") * swell_env(dur, 0.85)
    scr = np.zeros(n)
    rng = np.random.default_rng(seed_of("batscr"))
    for _ in range(8):
        t0 = rng.uniform(0.0, dur * 0.72)
        sd = rng.uniform(0.05, 0.11)
        f0 = rng.uniform(2200, 3800)
        s = cry(sd, [(0.0, f0), (1.0, f0 * 0.55)], [(0.0, "ee"), (1.0, "eh")],
                breath=0.5, jitter=0.04, amp_keys=((0.0, 0.0), (0.1, 1.0), (1.0, 0.0)),
                name="bs" + str(rng.integers(1 << 20)))
        at(scr, t0, s * rng.uniform(0.14, 0.3))
    return reverb(saturate(mixdown(wings * 0.75, scr), 1.7), decay=0.6, damp=6000, mix=0.24,
                  name="bat", predelay=0.006)


def gen_blood_fang():
    dur = 0.34
    snap = strike("bone", 340, 0.1, hardness=1.0, damp=1.6, name="bfs")
    flesh = strike("flesh", 130, 0.18, hardness=0.9, damp=1.0, name="bff") * 0.85
    slurp = fm(seg_env([(0.0, 190), (1.0, 470)], dur), 1.26, seg_env([(0.0, 6), (1.0, 1.5)], dur), dur)
    slurp *= seg_env([(0.0, 0.0), (0.4, 0.6), (1.0, 0.0)], dur) * 0.4
    wet = water(dur * 0.7, size=2.4, bubbles=5, foam=0.2, name="bfw") * 0.35
    out = mixdown(pad_to(snap, dur), pad_to(flesh, dur), slurp, pad_to(wet, dur))
    return reverb(saturate(out, 2.4), decay=0.4, damp=3800, mix=0.2, name="bf")


def gen_devour():
    dur = 0.5
    growl = gen_roar(f0=88, dur=dur, growl=0.75, name="dev") * 0.75
    chomp = mixdown(strike("bone", 300, 0.12, hardness=1.0, damp=1.5, name="dvc"),
                    strike("flesh", 110, 0.2, hardness=1.0, damp=0.9, name="dvf") * 0.9)
    out = pad_to(growl, dur)
    at(out, 0.015, chomp * 0.9)
    return reverb(saturate(out, 2.2), decay=0.5, damp=3400, mix=0.22, name="dev", predelay=0.006)


def gen_jaw_chomp():
    dur = 0.3
    snap = strike("bone", 420, 0.09, hardness=1.0, damp=1.8, name="jw1")
    crunch = strike("bone", 180, 0.16, hardness=0.9, damp=1.2, name="jw2") * 0.8
    flesh = strike("flesh", 105, 0.14, hardness=0.85, damp=1.0, name="jw3") * 0.7
    out = mixdown(pad_to(snap, dur), pad_to(crunch, dur), pad_to(flesh, dur))
    return reverb(saturate(out, 2.6), decay=0.35, damp=4000, mix=0.2, name="jw")


def gen_shark_rush():
    """Shark: the water gets out of the way, then the bite."""
    dur = 0.46
    rush = water(0.3, size=0.9, bubbles=9, foam=0.8, name="shk") * 0.8
    bite = gen_jaw_chomp() * 0.85
    out = np.zeros(n_of(dur))
    out[:len(rush)] += rush
    at(out, 0.16, bite)
    return reverb(saturate(out, 1.8), decay=0.5, damp=3600, mix=0.22, name="shk")


def gen_tentacle():
    dur = 0.4
    wet = bandpass(pink(dur, "tent"), 120, 1700) * exp_env(dur, attack=0.004, decay=6.0)
    wet = svf(wet, seg_env([(0.0, 2000), (1.0, 380)], dur), q=4)
    slap = strike("flesh", 150, 0.14, hardness=0.95, damp=1.0, name="tsl")
    squelch = water(dur * 0.8, size=2.6, bubbles=7, foam=0.3, name="tw") * 0.5
    out = mixdown(wet * 0.8, pad_to(slap, dur), pad_to(squelch, dur))
    return reverb(saturate(out, 2.2), decay=0.45, damp=3800, mix=0.22, name="tent")


def gen_grab():
    """Bear hug / primate grab — cloth, hand, and a held-in grunt."""
    dur = 0.38
    reach = air(0.16, band=(300, 2000), q=4.0, arc=1.5, turb=0.4, name="grb") * 0.4
    catch = mixdown(strike("flesh", 160, 0.16, hardness=1.0, damp=1.0, name="grc"),
                    bandpass(noise(0.09, "grcl"), 900, 5000) * exp_env(0.09, decay=11.0) * 0.5)
    grunt = cry(0.2, [(0, 128), (1.0, 92)], [(0, "uh"), (1.0, "aw")], growl=0.5, breath=0.35,
                amp_keys=((0, 0), (0.12, 1.0), (1.0, 0.0)), name="grg") * 0.45
    out = np.zeros(n_of(dur))
    out[:len(reach)] += reach
    at(out, 0.11, catch)
    at(out, 0.13, grunt)
    return reverb(saturate(out, 2.2), decay=0.4, damp=3600, mix=0.2, name="grb")


def gen_tongue():
    """Chameleon: elastic launch, wet contact, snap back."""
    dur = 0.42
    launch = mixdown(highpass(noise(0.03, "tg1"), 1800) * exp_env(0.03, decay=12.0) * 0.7,
                     sine(seg_env([(0.0, 900), (1.0, 260)], 0.06), 0.06) * exp_env(0.06, decay=9.0) * 0.5)
    stretch = fm(seg_env([(0.0, 620), (1.0, 180)], 0.2), 2.66, seg_env([(0.0, 7), (1.0, 1)], 0.2), 0.2)
    stretch *= exp_env(0.2, decay=6.0) * 0.4
    splat = water(0.16, size=2.8, bubbles=4, foam=0.6, name="tgw") * 0.7
    out = np.zeros(n_of(dur))
    out[:len(launch)] += launch
    out[:len(stretch)] += stretch
    at(out, 0.14, splat)
    at(out, 0.26, launch * 0.5)
    return reverb(saturate(out, 2.0), decay=0.35, damp=4600, mix=0.2, name="tg")


def gen_venom_spit():
    dur = 0.34
    hiss = cry(0.16, [(0, 260), (1.0, 190)], [(0, "ee"), (1.0, "eh")], breath=0.9, jitter=0.05,
               amp_keys=((0, 0), (0.15, 1.0), (1.0, 0.0)), name="vsp") * 0.5
    spit = mixdown(highpass(noise(0.05, "vs1"), 2200) * exp_env(0.05, decay=13.0),
                   water(0.2, size=2.6, bubbles=5, foam=0.5, name="vs2") * 0.8)
    fizz = gas(dur * 0.8, band=(900, 6500), fizz=1.0, name="vs3") * 0.35
    out = np.zeros(n_of(dur))
    out[:len(hiss)] += hiss
    at(out, 0.06, spit * 0.9)
    at(out, 0.08, fizz)
    return reverb(saturate(out, 1.9), decay=0.35, damp=5000, mix=0.2, name="vs")


def gen_stinger():
    """Scorpion: chitin, then a hard puncture. No buzz — the old one read as a kazoo."""
    dur = 0.28
    lift = strike("chitin", 1400, 0.07, hardness=0.9, damp=2.4, name="st1") * 0.55
    stab = mixdown(strike("chitin", 620, 0.12, hardness=1.0, damp=1.4, name="st2"),
                   strike("flesh", 150, 0.1, hardness=1.0, damp=1.2, name="st3") * 0.6)
    venom = gas(0.14, band=(1200, 7000), fizz=0.8, name="st4") * 0.3
    out = np.zeros(n_of(dur))
    out[:len(lift)] += lift
    at(out, 0.055, stab)
    at(out, 0.075, venom)
    return reverb(saturate(out, 2.3), decay=0.3, damp=5600, mix=0.18, name="st")


def gen_web_shot():
    dur = 0.34
    thwip = air(0.16, band=(1200, 5600), q=8.0, arc=2.2, turb=0.15, edge=0.2, name="wb") * 0.7
    stick = mixdown(bandpass(noise(0.16, "wbs"), 600, 4000) * exp_env(0.16, attack=0.002, decay=10.0),
                    water(0.14, size=3.0, bubbles=3, foam=0.4, name="wbw") * 0.5)
    strand = fm(seg_env([(0.0, 780), (1.0, 240)], 0.2), 2.66, seg_env([(0.0, 6), (1.0, 0.8)], 0.2), 0.2)
    strand *= exp_env(0.2, decay=7.0) * 0.32
    out = np.zeros(n_of(dur))
    out[:len(thwip)] += thwip
    at(out, 0.08, stick * 0.8)
    at(out, 0.06, strand)
    return reverb(saturate(out, 1.9), decay=0.34, damp=5200, mix=0.2, name="wb")


def gen_ink_snare():
    dur = 0.55
    burst_ = water(0.34, size=1.1, bubbles=12, foam=0.9, name="ink")
    low = sine(seg_env([(0.0, 105), (1.0, 40)], 0.24), 0.24) * exp_env(0.24, decay=5.0) * 0.7
    spread = gas(dur * 0.8, band=(200, 2600), fizz=0.3, name="inkg") * 0.45
    out = np.zeros(n_of(dur))
    out[:len(burst_)] += burst_ * 0.9
    out[:len(low)] += saturate(low, 2.2)
    at(out, 0.1, spread)
    return reverb(saturate(out, 1.8), decay=0.6, damp=3200, mix=0.24, name="ink", predelay=0.008)


# ═══════════════════════════ arcane, tech, void ═══════════════════════════

def gen_curse_hex():
    """A muttered curse: a dissonant stack under whispered consonants."""
    dur = 0.7
    drone = np.zeros(n_of(dur))
    for f, g in ((110.0, 1.0), (116.5, 0.55), (164.8, 0.35)):  # minor-second grind
        drone += fm(f, 1.41, seg_env([(0.0, 7.0), (1.0, 3.4)], dur), dur, feedback_ratio=2.73) * g
    drone *= seg_env([(0.0, 0.0), (0.1, 1.0), (0.55, 0.75), (1.0, 0.0)], dur)
    drone = saturate(drone, 2.2)
    grit = saturate(bandpass(pink(dur, "hexg"), 240, 2600), 2.4) * swell_env(dur, 1.2) * 0.34
    whisper = cry(dur, [(0.0, 118), (0.5, 96), (1.0, 108)], [(0.0, "uh"), (0.4, "ee"), (1.0, "oo")],
                  breath=0.95, jitter=0.05, open_q=0.35,
                  amp_keys=((0.0, 0.0), (0.15, 0.8), (0.7, 0.6), (1.0, 0.0)), name="hexv")
    whisper *= tremolo(n_of(dur), 9.5, 0.5)
    out = saturate(mixdown(drone * 0.5, whisper * 0.55, grit), 2.0)
    out = delay_fx(out, 0.11, feedback=0.36, mix=0.24, taps=4)
    return reverb(out, decay=1.1, damp=3600, mix=0.30, name="hex", predelay=0.014)


def gen_void_bolt():
    """Sound is pulled in, then something collapses."""
    dur = 0.6
    swell = svf(pink(dur * 0.55, "void"), seg_env([(0.0, 320), (1.0, 4600)], dur * 0.55), q=6, mode="bp")
    swell *= np.linspace(0, 1, n_of(dur * 0.55)) ** 2.4
    collapse = fm(seg_env([(0.0, 1250), (1.0, 38)], dur * 0.45), 1.73,
                  seg_env([(0.0, 3), (1.0, 11)], dur * 0.45), dur * 0.45)
    collapse *= exp_env(dur * 0.45, attack=0.002, decay=3.6)
    sub = sine(seg_env([(0.0, 66), (1.0, 27)], 0.3), 0.3) * exp_env(0.3, decay=4.0)
    out = np.zeros(n_of(dur))
    out[:len(swell)] += swell * 0.5
    at(out, dur * 0.5, saturate(collapse, 2.6) * 0.9)
    at(out, dur * 0.5, saturate(sub, 2.2) * 0.8)
    return reverb(out, decay=1.2, damp=2600, mix=0.32, name="void", predelay=0.012, size=1.4)


def gen_gravity_ball():
    """Collapse then rebound, ring-modulated so it never settles on a pitch."""
    dur = 0.75
    pitch = seg_env([(0.0, 480), (0.42, 44), (0.72, 160), (1.0, 62)], dur)
    body = fm(pitch, 1.33, seg_env([(0.0, 4), (0.5, 11), (1.0, 3)], dur), dur)
    ring = np.sin(2 * np.pi * np.cumsum(as_array(pitch * 0.37, n_of(dur))) / SR)
    body = body * (0.65 + 0.35 * ring)
    body *= seg_env([(0.0, 0.0), (0.08, 1.0), (0.65, 0.75), (1.0, 0.0)], dur)
    sub = sine(seg_env([(0.0, 82), (1.0, 28)], dur), dur) * swell_env(dur, 1.2) * 0.6
    pull = svf(pink(dur, "grav"), seg_env([(0.0, 1800), (0.5, 260), (1.0, 800)], dur), q=6, mode="bp")
    pull *= swell_env(dur, 1.5) * 0.28
    return reverb(saturate(mixdown(body * 0.8, sub, pull), 2.0), decay=1.2, damp=2800,
                  mix=0.30, name="grav", predelay=0.01, size=1.3)


def gen_vortex_pull():
    """Everything nearby falls inward. Rising, never resolving."""
    dur = 0.85
    core = burst("vortex", dur, name="vx")
    whine = sine(seg_env([(0.0, 180), (1.0, 720)], dur), dur) * (np.linspace(0, 1, n_of(dur)) ** 2.2) * 0.2
    return mixdown(core, pad_to(whine, len(core) / SR))


def gen_soul_harvest():
    dur = 1.0
    pull = burst("soul", dur, name="sh")
    voices = np.zeros(n_of(dur))
    rng = np.random.default_rng(seed_of("shv"))
    for i in range(4):
        t0 = rng.uniform(0.0, dur * 0.5)
        vd = rng.uniform(0.3, 0.5)
        f = rng.uniform(180, 420)
        v = cry(vd, [(0.0, f), (1.0, f * 2.1)], [(0.0, "oo"), (1.0, "ee")], breath=0.5,
                jitter=0.03, amp_keys=((0.0, 0.0), (0.2, 0.8), (1.0, 0.0)), name="shv%d" % i)
        at(voices, t0, v * rng.uniform(0.18, 0.34))
    return mixdown(pull, pad_to(voices, len(pull) / SR))


def gen_data_bolt():
    """Hacker: one packet leaving. A single chirp, not a five-note melody —
    an arpeggio fired twice a second is a ringtone, not a weapon."""
    dur = 0.24
    chirp = square(seg_env([(0.0, 1750), (1.0, 620)], 0.05), 0.05, duty=0.35)
    chirp = bitcrush(chirp, bits=5, hold=3) * exp_env(0.05, attack=0.001, decay=8.0)
    body = fm(seg_env([(0.0, 440), (1.0, 190)], 0.16), 4.0, seg_env([(0.0, 3.5), (1.0, 0.3)], 0.16), 0.16)
    body *= exp_env(0.16, attack=0.001, decay=10.0) * 0.7
    low = sine(seg_env([(0.0, 130), (1.0, 58)], 0.1), 0.1) * exp_env(0.1, decay=9.0) * 0.55
    sweep = svf(noise(dur, "dbs"), seg_env([(0.0, 2600), (1.0, 8000)], dur), q=8, mode="bp")
    sweep *= exp_env(dur, attack=0.005, decay=9.0) * 0.2
    out = mixdown(pad_to(chirp * 0.6, dur), pad_to(body, dur), pad_to(saturate(low, 2.0), dur), sweep)
    return reverb(saturate(out, 1.6), decay=0.3, damp=7000, mix=0.18, name="db", predelay=0.004)


def gen_virus_glitch():
    """Corruption: the signal stutters and drops bits. Kept dry and short so it
    reads as damage rather than as chiptune."""
    dur = 0.42
    rng = np.random.default_rng(seed_of("virus"))
    out = np.zeros(n_of(dur))
    t = 0.0
    while t < dur * 0.72:
        sd = rng.uniform(0.015, 0.05)
        f = rng.uniform(120, 1400)
        s = square(f, sd, duty=rng.uniform(0.15, 0.5)) * exp_env(sd, attack=0.001, decay=rng.uniform(5, 13))
        s = bitcrush(s, bits=int(rng.integers(2, 5)), hold=int(rng.integers(4, 16)))
        at(out, t, s * rng.uniform(0.25, 0.6))
        t += sd * rng.uniform(0.5, 1.1)
    hum = saw(seg_env([(0.0, 98), (1.0, 62)], dur), dur) * exp_env(dur, decay=4.0) * 0.34
    hum = bitcrush(hum, bits=4, hold=8)
    low = sine(seg_env([(0.0, 120), (1.0, 48)], 0.12), 0.12) * exp_env(0.12, decay=8.0) * 0.5
    out = saturate(mixdown(out, hum, pad_to(saturate(low, 2.0), dur)), 2.0)
    return reverb(out, decay=0.35, damp=5200, mix=0.16, name="virus")


def gen_overclock():
    """Cyborg: a system pushed past its rating — rising, then it lets go."""
    dur = 0.8
    rise = saw(seg_env([(0.0, 110), (0.75, 440), (1.0, 620)], dur * 0.75), dur * 0.75)
    rise = res_lp(rise, 2600, q=4.0) * (np.linspace(0, 1, n_of(dur * 0.75)) ** 1.8) * 0.5
    coil = zap_arc(dur * 0.5, 900, 9000, q=8, steps=60, name="ovc")
    coil *= seg_env([(0.0, 0.0), (0.6, 1.0), (1.0, 0.2)], dur * 0.5) * 0.7
    vent = gas(0.3, band=(700, 6500), fizz=0.8, name="ovv") * 0.6
    thump = sine(seg_env([(0.0, 120), (1.0, 44)], 0.2), 0.2) * exp_env(0.2, decay=6.0)
    out = np.zeros(n_of(dur))
    out[:len(rise)] += rise
    at(out, dur * 0.45, coil)
    at(out, dur * 0.6, vent)
    at(out, dur * 0.6, saturate(thump, 2.4) * 0.7)
    return reverb(saturate(out, 2.0), decay=0.65, damp=4600, mix=0.24, name="ov", predelay=0.006)


def gen_nano_burst():
    dur = 0.7
    swarm = burst("swarm", dur, name="nb")
    ticks = debris(dur, count=42, band=(1600, 9000), decay=(24.0, 50.0), spread=(0.0, 0.8),
                   name="nbt") * 0.4
    return mixdown(swarm, pad_to(ticks, len(swarm) / SR))


def gen_shadow_bolt():
    dur = 0.45
    whoosh = svf(pink(dur, "shad"), seg_env([(0.0, 2000), (0.4, 760), (1.0, 260)], dur), q=4, mode="bp")
    whoosh *= exp_env(dur, attack=0.008, decay=5.0)
    body = fm(seg_env([(0.0, 220), (1.0, 78)], dur), 1.73, seg_env([(0.0, 9), (1.0, 2.5)], dur), dur)
    body *= exp_env(dur, attack=0.004, decay=4.5) * 0.75
    breath = cry(dur * 0.7, [(0.0, 130), (1.0, 88)], [(0.0, "oo"), (1.0, "uh")], breath=0.9,
                 jitter=0.04, amp_keys=((0.0, 0.0), (0.2, 0.6), (1.0, 0.0)), name="shb") * 0.28
    out = mixdown(whoosh * 0.6, saturate(body, 2.6), pad_to(breath, dur))
    return reverb(out, decay=0.85, damp=2800, mix=0.30, name="shad", predelay=0.01)


def gen_echo_bolt():
    """Banshee Q: a cry that arrives twice."""
    dur = 0.5
    v = cry(0.22, [(0.0, 620), (1.0, 380)], [(0.0, "ah"), (1.0, "oo")], breath=0.4, jitter=0.03,
            amp_keys=((0.0, 0.0), (0.1, 1.0), (1.0, 0.0)), name="eb")
    out = np.zeros(n_of(dur))
    at(out, 0.0, v * 0.9)
    at(out, 0.13, v * 0.45)
    at(out, 0.24, v * 0.2)
    low = sine(seg_env([(0.0, 118), (1.0, 52)], 0.12), 0.12) * exp_env(0.12, decay=8.0) * 0.55
    out[:len(low)] += saturate(low, 2.0)
    return reverb(out, decay=0.9, damp=4400, mix=0.30, name="eb", predelay=0.012)


def gen_hypnotic_melody():
    """Musician: three notes of an A-minor arpeggio on a soft mallet. This is
    the one place in the game a melodic figure belongs — it is the ability."""
    dur = 0.7
    out = np.zeros(n_of(dur))
    for i, m in enumerate((69, 72, 76)):  # A C E
        f = 440.0 * (2.0 ** ((m - 69) / 12.0))
        note = modal("bell", f, 0.42, damp=2.0, name="hm%d" % i)
        note = mixdown(note, sine(f * 2, 0.42) * exp_env(0.42, attack=0.004, decay=5.0) * 0.18)
        at(out, i * 0.075, note * (0.9 - 0.12 * i))
    shimmer = sparkle(dur, 3500, 10000, count=8, name="hms") * 0.3
    return reverb(mixdown(out * 0.8, shimmer), decay=1.0, damp=7000, mix=0.30, name="hm",
                  predelay=0.01)


def gen_sonic_boom():
    """Bard / Musician: a shockwave with a musical core, not a bomb."""
    dur = 0.75
    front = wavefront("sonic", 0.4, name="sbf") * 0.8
    core = np.zeros(n_of(dur))
    for f, g in ((110.0, 1.0), (164.81, 0.5), (220.0, 0.35)):  # A E A power stack
        core += fm(seg_env([(0.0, f * 1.6), (0.1, f), (1.0, f * 0.9)], dur), 2.0,
                   seg_env([(0.0, 5.0), (1.0, 0.6)], dur), dur) * g
    core *= exp_env(dur, attack=0.003, decay=4.5) * 0.5
    sub = sine(seg_env([(0.0, 78), (1.0, 32)], 0.35), 0.35) * exp_env(0.35, decay=4.0)
    out = mixdown(np.zeros(n_of(dur)), front, saturate(core, 2.2), saturate(sub, 2.4) * 0.85)
    return reverb(saturate(out, 1.8), decay=0.9, damp=4000, mix=0.28, name="sb", predelay=0.01, size=1.3)


def gen_punch(charged=False):
    dur = 0.34 if not charged else 0.44
    windup = air(0.14, band=(300, 1900), q=4.0, arc=1.6, turb=0.4, name="pw") * (0.55 if charged else 0.3)
    land = mixdown(strike("flesh", 128, 0.22, hardness=1.0, damp=0.9, name="pl"),
                   bandpass(noise(0.1, "pc"), 400, 3600) * exp_env(0.1, attack=0.001, decay=12.0) * 0.5)
    sub = sine(seg_env([(0.0, 165), (1.0, 46)], 0.2), 0.2) * exp_env(0.2, decay=6.5)
    out = np.zeros(n_of(dur))
    out[:len(windup)] += windup
    t0 = 0.1 if charged else 0.05
    at(out, t0, land * (1.15 if charged else 1.0))
    at(out, t0, saturate(sub, 3.0) * (1.0 if charged else 0.8))
    if charged:
        ki = fm(seg_env([(0.0, 240), (1.0, 110)], 0.2), 1.5, seg_env([(0.0, 6), (1.0, 1)], 0.2), 0.2)
        at(out, t0, saturate(ki, 2.4) * exp_env(0.2, decay=6.0) * 0.35)
    return reverb(saturate(out, 2.6), decay=0.4, damp=3200, mix=0.2, name="pn")


def gen_momentum_strike():
    """Wolf Q: a body arriving at speed."""
    dur = 0.45
    rush = air(0.2, band=(250, 2200), q=4.5, arc=2.0, turb=0.5, name="ms") * 0.55
    hit = mixdown(strike("flesh", 105, 0.26, hardness=1.0, damp=0.85, name="msh"),
                  pad_to(thud(90, 0.26, drive=1.9, name="msd") * 0.6, 0.26))
    sub = sine(seg_env([(0.0, 140), (1.0, 40)], 0.24), 0.24) * exp_env(0.24, decay=5.0)
    snarl = gen_roar(f0=112, dur=0.22, growl=0.8, name="mss") * 0.4
    out = np.zeros(n_of(dur))
    out[:len(rush)] += rush
    at(out, 0.16, hit)
    at(out, 0.16, saturate(sub, 2.8) * 0.9)
    at(out, 0.14, snarl)
    return reverb(saturate(out, 2.2), decay=0.5, damp=3200, mix=0.22, name="ms", predelay=0.006)


def gen_scythe():
    dur = 0.55
    base = swing("scythe", dur, name="scy")
    groan = cry(dur, [(0.0, 132), (1.0, 88)], [(0.0, "aw"), (1.0, "oo")], growl=0.4, breath=0.3,
                amp_keys=((0.0, 0.0), (0.25, 0.7), (1.0, 0.0)), name="scyg") * 0.4
    return mixdown(base * 0.9, pad_to(groan, len(base) / SR))


def gen_reap():
    dur = 0.7
    base = gen_scythe()
    souls = np.zeros(n_of(dur))
    rng = np.random.default_rng(seed_of("reap"))
    for i in range(3):
        t0 = rng.uniform(0.1, dur * 0.5)
        f = rng.uniform(280, 560)
        v = cry(0.3, [(0.0, f), (1.0, f * 1.8)], [(0.0, "oo"), (1.0, "ee")], breath=0.55,
                jitter=0.035, amp_keys=((0.0, 0.0), (0.2, 0.8), (1.0, 0.0)), name="rp%d" % i)
        at(souls, t0, v * rng.uniform(0.2, 0.35))
    souls = delay_fx(souls, 0.08, feedback=0.4, mix=0.3, taps=3)
    return reverb(mixdown(pad_to(base, dur) * 0.85, pad_to(souls, dur)), decay=1.0, damp=4200,
                  mix=0.28, name="rp", predelay=0.012)


# ═══════════════════════════ character voices ═══════════════════════════
# Where several characters share a projectile type, the base sound is whichever
# of them the projectile was named for, and everyone else gets a voice here.
# A gorilla, a golem and a beetle all "throw a boulder" in the data; only one of
# them should sound like a rock rolling downhill.


def _with(base, *layers):
    """Base sound plus character layers, each padded to whichever is longest."""
    n = max(len(base), *(len(l) for l in layers)) if layers else len(base)
    return mixdown(fit(base, n), *(fit(l, n) for l in layers))


def _effort(f0=120, dur=0.3, growl=0.45, gain=0.4, name="ef"):
    """The grunt of something heavy being thrown. Half of what makes a big
    attack feel big is hearing the creature that made it."""
    return cry(dur, [(0.0, f0 * 1.1), (0.35, f0), (1.0, f0 * 0.72)],
               [(0.0, "uh"), (0.5, "ah"), (1.0, "aw")], growl=growl, breath=0.35,
               amp_keys=((0.0, 0.0), (0.1, 1.0), (0.6, 0.7), (1.0, 0.0)), name=name) * gain


def _wings(dur=0.4, rate=11.0, gain=0.35, band=(260, 2600), name="wg"):
    """Feathers, not a whoosh: each beat is its own broadband pulse."""
    n = n_of(dur)
    w = bandpass(pink(dur, name), *band) * tremolo(n, rate, 0.9, shape="pulse")
    return w * swell_env(dur, 0.9) * gain


def _scrape(dur=0.22, band=(1400, 7000), gain=0.3, name="sc"):
    """Chitin on chitin — dry, granular, no ring."""
    return debris(dur, count=16, band=band, decay=(30.0, 70.0), spread=(0.0, 0.7),
                  name=name) * gain


# — rock-throwers ————————————————————————————————————————————————
def gen_ape_throw():
    return _with(gen_boulder_rumble() * 0.8, _effort(96, 0.34, 0.7, 0.5, "apev"),
                 _wings(0.0001))


def gen_giant_boulder():
    return _with(gen_boulder_rumble() * 0.95, _effort(74, 0.42, 0.65, 0.55, "cycv"),
                 pad_to(thud(56, 0.34, drive=1.7, name="cyct") * 0.6, 0.42))


def gen_stone_fist():
    """Golem: the thrower is made of the same thing it throws — grinding, not tumbling."""
    dur = 0.6
    grind = saturate(bandpass(pink(dur, "gfg"), 180, 1600), 2.8) * ad_env(dur, attack=0.02, hold=0.4, curve=1.4)
    return _with(gen_boulder_rumble() * 0.6, grind * 0.7,
                 pad_to(strike("stone", 88, 0.3, hardness=1.0, name="gff") * 0.8, dur))


def gen_shell_charge():
    """Beetle: carapace, not knuckles."""
    return _with(gen_boulder_rumble() * 0.5, _scrape(0.3, (900, 5200), 0.55, "besc"),
                 pad_to(strike("chitin", 260, 0.26, hardness=1.0, name="bec") * 0.7, 0.4))


def gen_ice_chunk():
    """Avalanche throws ice, not rock."""
    return _with(gen_boulder_rumble() * 0.45,
                 pad_to(strike("ice", 420, 0.34, hardness=1.0, damp=1.1, name="avi") * 0.85, 0.5),
                 debris(0.5, count=14, band=(900, 7000), decay=(12.0, 30.0), name="avd") * 0.4)


# — clawed beasts ——————————————————————————————————————————————
def gen_bear_maul():
    return _with(swing("wet", 0.38, name="bem") * 0.85, _effort(76, 0.32, 0.8, 0.5, "bev"),
                 pad_to(thud(84, 0.24, drive=1.9, name="bet") * 0.5, 0.38))


def gen_mantis_scythe():
    """Two chitin blades, faster than anything with fur."""
    return _with(swing("talon", 0.26, name="mas") * 0.9, _scrape(0.2, (2000, 9000), 0.42, "masc"),
                 pad_to(strike("chitin", 1600, 0.14, hardness=1.0, damp=2.0, name="mac") * 0.5, 0.26))


def gen_fenrir_rend():
    return _with(swing("claw", 0.44, name="fer") * 0.9,
                 gen_roar(f0=72, dur=0.4, growl=0.85, name="fev") * 0.55,
                 pad_to(thud(66, 0.3, drive=1.9, name="fet") * 0.45, 0.44))


def gen_ghoul_rake():
    return _with(swing("wet", 0.32, name="ghr") * 0.9,
                 pad_to(water(0.22, size=2.6, bubbles=5, foam=0.5, name="ghw") * 0.45, 0.32),
                 cry(0.24, [(0, 132), (1.0, 92)], [(0, "uh"), (1.0, "oo")], growl=0.7, breath=0.6,
                     amp_keys=((0, 0), (0.15, 0.8), (1.0, 0.0)), name="ghv") * 0.35)


# — winged ——————————————————————————————————————————————————
def gen_hawk_dive():
    return _with(swing("talon", 0.3, name="hwd") * 0.85, _wings(0.3, 13.0, 0.4, name="hww"),
                 cry(0.2, [(0, 1500), (0.3, 2400), (1.0, 1200)], [(0, "ee"), (1.0, "eh")],
                     breath=0.5, jitter=0.04, amp_keys=((0, 0), (0.1, 0.8), (1.0, 0.0)),
                     name="hwv") * 0.3)


def gen_griffin_rake():
    return _with(swing("talon", 0.38, name="grr") * 0.85, _wings(0.42, 8.5, 0.45, (180, 2200), "grw"),
                 cry(0.28, [(0, 620), (0.3, 980), (1.0, 520)], [(0, "ah"), (1.0, "eh")],
                     growl=0.3, breath=0.4, amp_keys=((0, 0), (0.12, 0.85), (1.0, 0.0)),
                     name="grv") * 0.38)


# — fire-breathers ——————————————————————————————————————————
def gen_phoenix_flare():
    return _with(bolt("fire", 293.66, dur=0.42, name="phx") * 0.85,
                 sparkle(0.45, 4000, 13000, count=13, name="phs") * 0.4,
                 cry(0.3, [(0, 1200), (0.25, 1900), (1.0, 1000)], [(0, "ee"), (1.0, "ah")],
                     breath=0.45, jitter=0.03, amp_keys=((0, 0), (0.12, 0.7), (1.0, 0.0)),
                     name="phv") * 0.28)


def gen_chimera_breath():
    return _with(bolt("fire", 146.83, dur=0.46, name="chb") * 0.85,
                 gen_roar(f0=104, dur=0.36, growl=0.75, name="chv") * 0.42)


def gen_hellhound_spit():
    """Cerberus: three throats, so three staggered growls under the magma."""
    base = bolt("molten", 87.31, dur=0.48, name="cer")
    out = np.zeros(len(base))
    for i, f in enumerate((88.0, 104.0, 76.0)):
        at(out, 0.01 + i * 0.035, gen_roar(f0=f, dur=0.3, growl=0.8, name="cev%d" % i) * (0.34 - 0.06 * i))
    return _with(base * 0.85, out)


# — dark casters ——————————————————————————————————————————
def gen_warlock_bolt():
    return _with(bolt("dark", 110.0, dur=0.44, name="wlb") * 0.9,
                 cry(0.34, [(0, 116), (0.4, 88), (1.0, 70)], [(0, "ah"), (0.5, "aw"), (1.0, "oo")],
                     growl=0.7, breath=0.3, amp_keys=((0, 0), (0.12, 0.75), (1.0, 0.0)),
                     name="wlv") * 0.4)


def gen_anubis_bolt():
    """Egyptian: a struck bronze sistrum and desert grit over the death bolt."""
    return _with(bolt("dark", 130.81, dur=0.44, name="anb") * 0.85,
                 pad_to(modal("bell", 784.0, 0.3, damp=7.0, name="ansi") * 0.3, 0.44),
                 _texture("dust", 0.4, "andust") * 0.3)


def gen_deathknight_blade():
    """Plate armour moving with the swing."""
    return _with(thrown("cursed", 0.46, name="dkb") * 0.9,
                 pad_to(debris(0.3, count=8, band=(1200, 6000), decay=(30.0, 60.0), name="dkp") * 0.3, 0.46))


def gen_poltergeist_hurl():
    """Not a bolt at all — a handful of objects thrown by something unseen."""
    dur = 0.46
    out = debris(dur, count=10, band=(500, 4600), decay=(16.0, 34.0), spread=(0.0, 0.55), name="plt") * 0.7
    return _with(bolt("soul", 130.81, dur=dur, name="plb") * 0.6, out,
                 pad_to(air(0.3, band=(300, 1800), q=2.4, arc=1.6, turb=0.5, name="pla") * 0.35, dur))


def gen_djinn_bolt():
    """Smoke and struck brass."""
    return _with(bolt("mystic", 220.0, dur=0.44, name="djb") * 0.85,
                 pad_to(modal("bell", 587.33, 0.32, damp=5.5, name="djbr") * 0.34, 0.44),
                 gas(0.4, band=(300, 3400), fizz=0.3, name="djg") * 0.3)


# — tech ————————————————————————————————————————————————
def gen_photon_beam():
    return _with(beam("laser", 0.5, name="pho") * 0.85,
                 sparkle(0.5, 5000, 14000, count=14, name="phos") * 0.42,
                 pad_to(modal("glass", 1760.0, 0.3, damp=4.0, name="phog") * 0.28, 0.5))


def gen_turret_laser():
    """Sentinel: a mounted weapon — servo, then the shot."""
    dur = 0.44
    out = np.zeros(n_of(dur))
    at(out, 0.0, machine(0.16, servo=(420, 900), clank=0.35, whine=0.5, name="tur") * 0.45)
    at(out, 0.08, beam("laser", 0.34, name="turb") * 0.9)
    return out


def gen_mech_gun():
    """Mech Pilot: an autocannon, not a sidearm."""
    dur = 0.4
    out = np.zeros(n_of(dur))
    for i in range(2):
        at(out, i * 0.085, gun("heavy", 0.3, name="mg%d" % i), 1.0 - 0.2 * i)
    return _with(out, pad_to(machine(0.24, servo=(160, 420), clank=0.4, whine=0.2, name="mgs") * 0.3, dur))


def gen_tesla_arc():
    """A coil, not weather: a mains hum under the discharge."""
    dur = 0.42
    hum = saw(seg_env([(0.0, 120.0), (1.0, 118.0)], dur), dur)
    hum = res_lp(hum, 900, q=3.0) * ad_env(dur, attack=0.005, hold=0.4, curve=1.4) * 0.3
    return _with(elec(dur, size=0.8, colour=(1400, 12000), forks=3, name="tsl") * 0.9, hum)


def gen_time_bolt():
    """Chronomancer: the same discharge heard slightly out of order."""
    dur = 0.55
    core = elec(0.34, size=0.7, colour=(700, 9000), forks=2, name="tmb")
    rev = core[::-1] * np.linspace(0.0, 1.0, len(core)) ** 2.4 * 0.45
    out = np.zeros(n_of(dur))
    at(out, 0.0, rev)
    at(out, 0.16, core * 0.9)
    wob = sine(seg_env([(0.0, 330.0), (0.5, 196.0), (1.0, 262.0)], dur), dur)
    return _with(out, wob * swell_env(dur, 1.6) * 0.22)


# — the rest ————————————————————————————————————————————
def gen_glacier_shard():
    return _with(bolt("ice", 196.0, dur=0.46, name="gls") * 0.85,
                 pad_to(strike("ice", 620, 0.32, hardness=1.0, damp=1.0, name="glc") * 0.6, 0.46))


def gen_medusa_spit():
    return _with(bolt("acid", 174.61, dur=0.4, name="mds") * 0.85,
                 cry(0.3, [(0, 340), (1.0, 260)], [(0, "ee"), (0.5, "eh"), (1.0, "ee")],
                     breath=0.95, jitter=0.06, open_q=0.3,
                     amp_keys=((0, 0), (0.2, 0.7), (1.0, 0.0)), name="mdv") * 0.4)


def gen_hydra_spit():
    """Several heads, slightly out of time with each other."""
    dur = 0.5
    out = np.zeros(n_of(dur))
    for i, f in enumerate((196.0, 174.61, 220.0)):
        at(out, i * 0.038, bolt("acid", f, dur=0.34, name="hyd%d" % i) * (0.7 - 0.14 * i))
    return out


def gen_treant_bark():
    return _with(shaft("thorn", 0.34, name="trb") * 0.85,
                 pad_to(strike("wood", 220, 0.24, hardness=0.9, name="trw") * 0.55, 0.34),
                 _texture("dust", 0.3, "trd") * 0.25)


def gen_inquisitor_bolt():
    """Zeal, not grace: the bell is struck harder and there is iron in it."""
    return _with(chime("holy", 0.3) * 0.8,
                 pad_to(strike("iron", 420, 0.22, hardness=1.0, damp=2.2, name="inqi") * 0.45, 0.3),
                 pad_to(thud(140, 0.16, drive=1.7, name="inqt") * 0.45, 0.3))


def gen_lute_chord():
    """Bard: a plucked chord, which is what a bard attacks with."""
    dur = 0.5
    out = np.zeros(n_of(dur))
    for i, f in enumerate((220.0, 261.63, 329.63, 440.0)):   # Am
        pluck = mixdown(strike("wood", f, 0.34, hardness=0.9, damp=0.6, name="lut%d" % i) * 0.5,
                        sine(f, 0.36) * exp_env(0.36, attack=0.002, decay=6.0) * 0.55,
                        sine(f * 2, 0.3) * exp_env(0.3, attack=0.002, decay=9.0) * 0.2)
        at(out, i * 0.014, pluck * (0.9 - 0.1 * i))
    return _with(out, wavefront("sonic", 0.4, name="lutw") * 0.3)


def gen_song_wave():
    """Musician: a reed/brass swell rather than an abstract shockwave."""
    dur = 0.55
    body = np.zeros(n_of(dur))
    for h, g in ((1, 1.0), (2, .5), (3, .34), (4, .18), (5, .1)):
        body += sine(220.0 * h * (1.0 + 0.01 * np.sin(2 * np.pi * 5.0 * t_axis(dur))), dur) * g
    body *= seg_env([(0.0, 0.0), (0.12, 1.0), (0.6, 0.8), (1.0, 0.0)], dur) * 0.4
    body = res_lp(saturate(body, 1.8), 1800, q=2.2)
    breath = bandpass(pink(dur, "sgb"), 900, 5000) * swell_env(dur, 1.3) * 0.18
    return _with(body, breath, wavefront("sonic", 0.42, name="sgw") * 0.4)


def gen_sphinx_riddle():
    """A question you cannot answer, not a blast."""
    dur = 0.6
    whisper = cry(dur, [(0.0, 210), (0.45, 300), (1.0, 190)],
                  [(0.0, "oo"), (0.35, "ee"), (0.7, "ah"), (1.0, "oo")],
                  breath=0.9, jitter=0.04, open_q=0.35,
                  amp_keys=((0.0, 0.0), (0.2, 0.7), (0.7, 0.55), (1.0, 0.0)), name="sph")
    return _with(chime("holy", 0.4) * 0.45, whisper * 0.5,
                 pad_to(sparkle(0.5, 2600, 8000, count=9, name="sphs") * 0.3, dur))


def gen_shift_strike():
    """Shapeshifter: the limb becomes something else on the way in."""
    dur = 0.42
    morph = svf(pink(0.2, "shm"), seg_env([(0.0, 400), (1.0, 2600)], 0.2), q=2.4, mode="bp")
    morph *= (np.linspace(0.0, 1.0, n_of(0.2)) ** 1.8) * 0.4
    out = np.zeros(n_of(dur))
    at(out, 0.0, morph)
    at(out, 0.12, gen_punch(False) * 0.9)
    at(out, 0.1, gen_roar(f0=132, dur=0.22, growl=0.6, name="shv") * 0.3)
    return out


# ═══════════════════════════ event sounds ═══════════════════════════
# These four — spawn, death, hit taken, hit dealt — are heard more than any
# attack in the game, so they get the most structure and the tightest lengths.
# A hitmarker you hear two hundred times a match has to be 130ms and sweet; a
# death has to read as a designed moment, which means it resolves musically
# (in A minor, with the soundtrack) rather than just stopping.

def gen_spawn():
    """Respawn: energy gathers, you rematerialize, and a rising A-minor figure
    says go. Short — this plays while the player is already trying to move."""
    dur = 0.8
    n = n_of(dur)
    gather = svf(pink(0.3, "spg"), seg_env([(0.0, 5200), (1.0, 700)], 0.3), q=5, mode="bp")
    gather *= (np.linspace(0.0, 1.0, n_of(0.3)) ** 2.2) * 0.55
    form = mixdown(click(0.01, fc=2600, name="spc", shape=1.4) * 0.55,
                   strike("glass", 1320, 0.16, hardness=0.9, damp=2.6, name="spf") * 0.45)
    figure = np.zeros(n)
    for i, f in enumerate((440.0, 523.25, 659.25)):
        note = modal("bell", f, 0.34, damp=1.9, name="spn%d" % i)
        note = mixdown(note * 0.8, sine(f, 0.34) * exp_env(0.34, attack=0.004, decay=4.0) * 0.3)
        at(figure, 0.3 + i * 0.055, note * (0.75 + 0.1 * i))
    bloom = sine(seg_env([(0.0, 55), (1.0, 110)], 0.4), 0.4) * swell_env(0.4, 1.6) * 0.45
    shim = sparkle(dur, 4000, 11000, count=12, name="sps") * 0.28
    out = np.zeros(n)
    out[:len(gather)] += gather
    at(out, 0.27, form)
    at(out, 0.3, saturate(bloom, 1.8))
    out += figure * 0.9 + shim
    return reverb(out, decay=1.1, damp=6800, mix=0.30, name="spawn", predelay=0.012, size=1.2)


def gen_death():
    """You died. Body hits the ground, the light goes out, and two bell notes
    fall a minor third — the gesture that makes it land as a moment instead of
    as a noise."""
    dur = 1.25
    n = n_of(dur)
    fall = mixdown(strike("flesh", 96, 0.35, hardness=1.0, damp=0.8, name="dfl"),
                   pad_to(thud(76, 0.4, drive=1.7, name="ddr") * 0.65, 0.35))
    collapse = fm(seg_env([(0.0, 460), (0.35, 190), (1.0, 54)], 0.7), 1.73,
                  seg_env([(0.0, 4), (1.0, 10)], 0.7), 0.7)
    collapse *= exp_env(0.7, attack=0.005, decay=3.2)
    collapse = svf(collapse, seg_env([(0.0, 3600), (1.0, 300)], 0.7), q=4)
    exhale = cry(0.5, [(0.0, 128), (1.0, 72)], [(0.0, "ah"), (0.5, "uh"), (1.0, "oo")],
                 growl=0.25, breath=0.75, jitter=0.03,
                 amp_keys=((0.0, 0.0), (0.12, 0.8), (1.0, 0.0)), name="dex") * 0.5
    knell = np.zeros(n)
    for i, (f, t0, g) in enumerate(((220.0, 0.16, 0.8), (174.61, 0.42, 0.62))):  # A3 -> F3
        at(knell, t0, modal("bell", f, 0.85, damp=1.0, name="dk%d" % i) * g)
    sub = sine(seg_env([(0.0, 72), (1.0, 26)], 0.6), 0.6) * exp_env(0.6, attack=0.004, decay=3.0)
    out = np.zeros(n)
    out[:len(fall)] += fall * 0.95
    at(out, 0.02, saturate(collapse, 2.2) * 0.6)
    at(out, 0.14, exhale)
    out += knell * 0.55
    out[:len(sub)] += saturate(sub, 2.2) * 0.8
    return reverb(out, decay=1.5, damp=2800, mix=0.32, name="death", predelay=0.018, size=1.5)


def gen_hit_taken():
    """You were hit. Meaty, low, unmistakable — and deliberately dark above
    5kHz, because this is the sound a losing player hears most and a bright one
    turns a bad fight into a headache."""
    dur = 0.42
    body = mixdown(strike("flesh", 112, 0.26, hardness=1.0, damp=0.9, name="htb"),
                   pad_to(thud(84, 0.28, drive=2, name="htd") * 0.7, 0.26))
    # the slap: 400-2500Hz is where "something struck me" lives. Rolling this
    # off leaves a sound that is felt but carries no information.
    slap = bandpass(noise(0.13, "hta"), 400, 2500) * exp_env(0.13, attack=0.0008, decay=10.0) * 0.9
    armour = bandpass(noise(0.06, "htm"), 900, 5200) * exp_env(0.06, attack=0.0005, decay=22.0) * 0.55
    # a very short dissonant low dyad: reads as "wrong" without being a musical event
    warn = (sine(55.0, 0.16) + sine(58.27, 0.16) * 0.8) * exp_env(0.16, attack=0.003, decay=5.0)
    grunt = cry(0.24, [(0.0, 168), (1.0, 108)], [(0.0, "uh"), (1.0, "aw")], growl=0.45,
                breath=0.4, amp_keys=((0.0, 0.0), (0.1, 0.75), (1.0, 0.0)), name="htg") * 0.5
    sub = sine(seg_env([(0.0, 118), (1.0, 38)], 0.22), 0.22) * exp_env(0.22, decay=6.0)
    out = np.zeros(n_of(dur))
    out[:len(body)] += body * 0.85
    out[:len(slap)] += slap
    at(out, 0.004, armour)
    out[:len(warn)] += saturate(warn, 2.0) * 0.35
    at(out, 0.03, grunt)
    out[:len(sub)] += saturate(sub, 2.6) * 0.55
    out = eq(saturate(out, 2.2), "lp", 7000, q=0.8)
    return reverb(out, decay=0.5, damp=2600, mix=0.22, name="ht", predelay=0.005)


def gen_hit_dealt():
    """The hitmarker. 130ms, bright but narrow-band, with a small upward
    interval so landing a shot feels like an answer rather than a tick."""
    dur = 0.16
    tick = strike("blade", 2100, 0.05, hardness=1.0, damp=3.2, name="hd1") * 0.85
    tock = strike("blade", 1180, 0.07, hardness=0.8, damp=2.4, name="hd2") * 0.5
    confirm = mixdown(sine(659.25, 0.05) * exp_env(0.05, attack=0.002, decay=7.0),
                      sine(880.0, 0.06) * exp_env(0.06, attack=0.002, decay=6.0) * 0.8)
    body = sine(seg_env([(0.0, 300), (1.0, 165)], 0.07), 0.07) * exp_env(0.07, decay=9.0) * 0.4
    out = np.zeros(n_of(dur))
    out[:len(tick)] += tick
    out[:len(tock)] += tock
    at(out, 0.028, confirm * 0.3)
    out[:len(body)] += body
    return reverb(saturate(out, 2.0), decay=0.18, damp=7500, mix=0.12, name="hd")


def gen_hit_other():
    """Someone else traded a hit. Same event, heard from over there: duller,
    more room, no vocal, so it never competes with your own feedback."""
    dur = 0.3
    body = mixdown(strike("flesh", 128, 0.2, hardness=0.85, damp=1.1, name="hob"),
                   pad_to(thud(98, 0.22, drive=1.7, name="hod") * 0.6, 0.2))
    smack = bandpass(noise(0.11, "hos"), 380, 3000) * exp_env(0.11, attack=0.001, decay=11.0) * 1.4
    thwack = bandpass(noise(0.07, "hot"), 700, 4200) * exp_env(0.07, attack=0.0006, decay=16.0) * 0.8
    out = np.zeros(n_of(dur))
    out[:len(body)] += body * 0.62
    out[:len(smack)] += smack
    at(out, 0.003, thwack)
    out = eq(saturate(out, 2.0), "lp", 6000, q=0.8)
    return reverb(out, decay=0.5, damp=3200, mix=0.26, name="ho", predelay=0.008)


def gen_dash():
    dur = 0.34
    push = strike("earth", 120, 0.12, hardness=0.8, damp=1.4, name="dsp") * 0.6
    cloth = bandpass(pink(0.2, "dsc"), 700, 5200) * seg_env([(0.0, 0.0), (0.2, 1.0), (1.0, 0.0)], 0.2) * 0.5
    rush = air(dur, band=(400, 3200), q=5.0, arc=1.5, turb=0.45, name="dsa") * 0.8
    thrust = fm(seg_env([(0.0, 150), (0.4, 330), (1.0, 130)], dur), 1.5,
                seg_env([(0.0, 5), (1.0, 0.8)], dur), dur)
    thrust *= swell_env(dur, 1.9) * 0.35
    out = mixdown(rush, thrust)
    out[:len(push)] += push
    at(out, 0.03, cloth)
    return reverb(saturate(out, 1.9), decay=0.42, damp=5200, mix=0.22, name="ds")


def gen_teleport():
    """Zip out, gap, pop in. The gap is what sells it — a continuous sound
    reads as a whoosh, not as an absence."""
    dur = 0.5
    out_d, in_d = 0.18, 0.24
    zip_out = fm(seg_env([(0.0, 330), (1.0, 2400)], out_d), 2.0,
                 seg_env([(0.0, 5), (1.0, 0.8)], out_d), out_d)
    zip_out *= exp_env(out_d, attack=0.003, decay=4.0)
    zip_out = eq(zip_out, "lp", 6000, q=0.9)
    pop_in = fm(seg_env([(0.0, 2100), (1.0, 300)], in_d), 1.41,
                seg_env([(0.0, 7), (1.0, 1)], in_d), in_d)
    pop_in *= exp_env(in_d, attack=0.001, decay=6.0)
    thud = sine(seg_env([(0.0, 130), (1.0, 46)], 0.12), 0.12) * exp_env(0.12, decay=8.0) * 0.5
    shim = sparkle(dur, 3200, 9000, count=9, name="tp") * 0.3
    out = np.zeros(n_of(dur))
    out[:len(zip_out)] += zip_out * 0.55
    at(out, 0.23, pop_in * 0.7)
    at(out, 0.23, saturate(thud, 2.2))
    out += shim
    out = delay_fx(out, 0.06, feedback=0.26, mix=0.2, taps=3)
    return reverb(saturate(out, 1.7), decay=0.7, damp=6500, mix=0.26, name="tp", predelay=0.008)


def gen_phase_shift():
    dur = 0.68
    n = n_of(dur)
    shimmer = svf(pink(dur, "ph"), seg_env([(0.0, 620), (0.5, 2100), (1.0, 1000)], dur), q=4, mode="bp")
    shimmer *= tremolo(n, 6.5, 0.5) * swell_env(dur, 1.0) * 0.45
    pad = np.zeros(n)
    for f, det in ((220.0, 1.0), (329.63, 1.006), (440.0, 0.994)):
        pad += fm(f * det, 2.01, seg_env([(0.0, 0.8), (0.5, 3.5), (1.0, 0.6)], dur), dur) * 0.4
    pad *= swell_env(dur, 1.4) * 0.34
    pad = chorus(pad, rate=0.6, depth_ms=10, voices=3, mix=0.45, name="phc")
    ring = np.sin(2 * np.pi * 6.5 * t_axis(dur))
    pad = pad * (0.72 + 0.28 * ring)
    return reverb(mixdown(shimmer, pad), decay=1.1, damp=6200, mix=0.30, name="ph", predelay=0.012)


def gen_barrier_up():
    """A wall of light going up in front of you: a short upward rush of energy that locks with
    a soft, low thump and hums for a moment after. The lock is a damped thud, not a struck plate:
    this is a heavy shield being set, not a chime."""
    dur = 0.62
    n = n_of(dur)
    rush_d = 0.16
    rush = svf(pink(rush_d, "bur"), seg_env([(0.0, 380), (1.0, 2400)], rush_d), q=3.0, mode="bp")
    rush *= (np.linspace(0.0, 1.0, n_of(rush_d)) ** 1.8) * 0.55
    lock = thud(96, 0.3, drive=1.8, name="bul")
    seat = bandpass(contact(0.012, name="buc"), 300, 2200) * 0.35
    # the field settling: a low fifth that beats slowly and dies away
    hum_d = 0.46
    hum = sine(110.0, hum_d) + sine(165.3, hum_d) * 0.55 + sine(220.4, hum_d) * 0.2
    hum *= exp_env(hum_d, attack=0.015, decay=5.0) * (0.8 + 0.2 * np.sin(2 * np.pi * 7.0 * t_axis(hum_d)))
    hum = chorus(hum, rate=0.7, depth_ms=5.0, voices=2, mix=0.3, name="buh")
    glint = sparkle(0.3, 2600, 7000, count=5, rise=False, name="bug") * 0.12
    out = np.zeros(n)
    out[:len(rush)] += rush
    at(out, rush_d - 0.02, saturate(lock, 1.6) * 0.9)
    at(out, rush_d - 0.02, seat)
    at(out, rush_d - 0.01, hum * 0.42)
    at(out, rush_d - 0.03, glint)
    return reverb(out, decay=0.55, damp=5000, mix=0.22, name="bu", predelay=0.01)


def gen_trap_place():
    """A trap set down: the weight of it landing on earth, the catch taking, and the small click
    of the mechanism being armed. Deliberately quiet and short — the point of laying one is that
    the other side does not notice, and everyone nearby hears it."""
    dur = 0.38
    out = np.zeros(n_of(dur))
    # It lands on ground, not on a struck plate
    land = thud(78, 0.2, drive=1.4, name="tpl")
    earth = bandpass(noise(0.07, "tpe"), 200, 1800) * exp_env(0.07, attack=0.001, decay=14.0) * 0.45
    out[:len(land)] += land * 0.8
    at(out, 0.004, earth)
    # The catch takes: one short iron partial, heavily damped. Left ringing this is the bucket.
    at(out, 0.1, strike("iron", 620, 0.16, hardness=1.2, damp=2.6, name="tpc", ring=0.18) * 0.42)
    # And the arming click
    at(out, 0.17, click(0.006, fc=2600, name="tpk") * 0.35)
    return reverb(saturate(out, 1.5), decay=0.3, damp=4200, mix=0.15, name="tp", predelay=0.004)


def gen_trap_snap():
    """Jaws closing on somebody. The loudest thing a trap does: a hard contact, a short dense ring
    off the steel, the low displacement of something being caught, and the chain snatching tight
    after it. The ring is kept short and damped — a partial left singing here plays every time
    anyone is caught all match, which is the clang rule exactly."""
    dur = 0.5
    out = np.zeros(n_of(dur))
    snap = strike("steel", 340, 0.24, hardness=1.8, damp=2.2, name="tsn", ring=0.26)
    body = thud(96, 0.26, drive=2.2, name="tsb")
    out[:len(snap)] += snap * 0.95
    out[:len(body)] += body * 0.85
    at(out, 0.05, debris(0.26, count=6, band=(900, 4200), decay=(14.0, 26.0), name="tsc") * 0.3)
    return reverb(eq(saturate(out, 2.0), "lp", 8000, q=0.8), decay=0.42, damp=3800, mix=0.18,
                  name="ts", predelay=0.005)


def gen_trap_poison():
    """A spore pod bursting: a wet split, then the gas going out of it. The hiss falls away rather
    than holding — this is the pod giving way, not a cloud sitting on the ground."""
    dur = 0.5
    out = np.zeros(n_of(dur))
    split = mixdown(water(0.16, size=1.2, bubbles=9, foam=0.85, name="tpw") * 0.8,
                    thud(120, 0.14, drive=1.8, name="tpt") * 0.5)
    out[:len(split)] += split
    at(out, 0.03, gas(0.36, band=(260, 3200), fizz=0.5, name="tpg") * 0.55)
    # Banded: a burst that reaches the top of the spectrum is every other burst in the set
    out = eq(saturate(out, 1.6), "lp", 7000, q=0.7)
    return reverb(out, decay=0.4, damp=3000, mix=0.18, name="tpo", predelay=0.006)


def gen_trap_ignite():
    """A rune catching: the intake as it takes, then a short roar of flame off the ground. Over
    inside half a second — what it leaves burning is on the victim, not in the mix."""
    dur = 0.52
    out = np.zeros(n_of(dur))
    catch = air(0.1, band=(700, 3000), q=2.6, arc=2.4, turb=0.2, body=0.6, name="tia") * 0.5
    out[:len(catch)] += catch
    # Few crackles and a short roar: a rune taking, not the wall of flame a napalm strike is
    at(out, 0.04, fire(0.34, low=120, roar=0.8, crackles=9, name="tif") * 0.75)
    at(out, 0.04, saturate(sine(seg_env([(0.0, 150), (1.0, 52)], 0.22), 0.22) *
                           exp_env(0.22, decay=5.5), 2.2) * 0.6)
    # Kept under 7kHz. Fire that runs to the top of the band is the same cloud as every other
    # fire in the set, and there are a lot of them.
    out = eq(saturate(out, 1.7), "lp", 6800, q=0.7)
    return reverb(out, decay=0.42, damp=3000, mix=0.18, name="ti", predelay=0.008)


def gen_barrier_block():
    """A shot stopped dead on a barrier: a dull, soft thump of energy and a short fizz as the
    shot comes apart on it. One damped sine for the body and nothing that rings. This is heard
    every time anyone fires into a shield, so it is the clang rule twice over: a partial left
    ringing here is someone hitting a bucket all fight long."""
    dur = 0.26
    body = thud(82, 0.2, drive=1.9, name="bbt")
    # the field giving under it: a short drop in pitch, gone in under 100ms
    give_d = 0.09
    give = sine(seg_env([(0.0, 196), (1.0, 118)], give_d), give_d) * exp_env(give_d, attack=0.001, decay=9.0) * 0.45
    fizz = bandpass(noise(0.07, "bbf"), 700, 2600) * exp_env(0.07, attack=0.0006, decay=17.0) * 0.5
    out = np.zeros(n_of(dur))
    out[:len(body)] += body * 0.9
    out[:len(give)] += saturate(give, 2.0)
    at(out, 0.002, fizz)
    out = eq(saturate(out, 1.8), "lp", 6000, q=0.8)
    return reverb(out, decay=0.35, damp=3000, mix=0.16, name="bb", predelay=0.004)


# ═══════════════════════════ music ═══════════════════════════

def midi_hz(m):
    return 440.0 * (2.0 ** ((m - 69) / 12.0))


def m_note(m_num, dur, kind="saw", detune=0.006, voices=3, fc=(2500, 700), q=3.0, drive=1.2, decay=4.0, attack=0.006):
    """One synth note: detuned voices → filter envelope → saturation."""
    f = midi_hz(m_num)
    n = n_of(dur)
    sig = np.zeros(n)
    for v in range(voices):
        off = (v - (voices - 1) / 2.0) * detune
        fv = f * (1.0 + off)
        if kind == "saw":
            sig += saw(fv, dur)
        elif kind == "square":
            sig += square(fv, dur, duty=0.42)
        else:
            sig += sine(fv, dur)
    sig /= voices
    sig *= exp_env(dur, attack=attack, decay=decay)
    sig = svf(sig, seg_env([(0.0, fc[0]), (0.35, (fc[0] + fc[1]) / 2), (1.0, fc[1])], dur), q=q)
    return saturate(sig, drive)


def m_bell(m_num, dur):
    f = midi_hz(m_num)
    out = np.zeros(n_of(dur))
    for p, g, d in ((1.0, 1.0, 3.0), (2.0, 0.45, 4.5), (2.76, 0.28, 6.0), (5.4, 0.12, 8.0)):
        out += sine(f * p, dur) * exp_env(dur, attack=0.003, decay=d) * g
    return out * 0.5


def m_pad(chord, dur, fc_lo=420, fc_hi=1500):
    n = n_of(dur)
    sig = np.zeros(n)
    for m_num in chord:
        f = midi_hz(m_num)
        for det in (0.994, 1.0, 1.007):
            sig += saw(f * det, dur) * 0.25
    sig /= len(chord)
    sweep = seg_env([(0.0, fc_lo), (0.45, fc_hi), (1.0, fc_lo * 1.2)], dur)
    sig = svf(sig, sweep, q=3.5)
    sig *= seg_env([(0.0, 0.0), (0.18, 1.0), (0.8, 0.9), (1.0, 0.15)], dur)
    return saturate(sig, 1.4) * 0.5


def drum_kick(dur=0.32, f0=150, f1=44, drive=2.3):
    body = sine(seg_env([(0.0, f0), (0.12, f1 * 1.5), (1.0, f1)], dur), dur)
    body *= exp_env(dur, attack=0.001, decay=6.0)
    click = highpass(noise(0.006, "kick"), 2500) * np.linspace(1, 0, n_of(0.006))
    out = saturate(body, drive)
    out[:len(click)] += click * 0.5
    return out


def drum_snare(dur=0.26, name="sn"):
    tone = (sine(190, dur) + sine(278, dur) * 0.7) * exp_env(dur, attack=0.001, decay=9.0)
    rattle = bandpass(noise(dur, name), 900, 8000) * exp_env(dur, attack=0.001, decay=7.0)
    out = saturate(tone * 0.45 + rattle * 0.8, 2.0)
    return reverb(out, decay=0.35, damp=6000, mix=0.2, name="sn")


def drum_hat(dur=0.06, open_hat=False, name="hh"):
    d = 0.26 if open_hat else dur
    h = highpass(noise(d, name), 7000) * exp_env(d, attack=0.0005, decay=6.0 if open_hat else 16.0)
    return h * 0.5


def make_loop(buf, loop_len_samples):
    """Wrap the reverb/delay tail back to the head so the loop is seamless."""
    out = buf[:loop_len_samples].copy()
    tail = buf[loop_len_samples:]
    k = min(len(tail), loop_len_samples)
    if k > 0:
        out[:k] += tail[:k]
    return out


def sidechain(buf, hit_times, beat, amount=0.4, recover=0.30):
    """Ducks the mix on every kick — the 'pump' that makes modern game music breathe."""
    n = len(buf)
    duck = np.ones(n)
    rn = n_of(recover)
    shape = 1.0 - amount * (1.0 - np.linspace(0.0, 1.0, rn) ** 0.45)
    for t in hit_times:
        s = int(t * SR)
        if s >= n:
            continue
        e = min(n, s + rn)
        duck[s:e] = np.minimum(duck[s:e], shape[: e - s])
    return (buf.T * duck).T


def riser(dur, f0=200, f1=4000, name="ris"):
    n = n_of(dur)
    sweep = svf(noise(dur, name), seg_env([(0.0, f0), (1.0, f1)], dur), q=7, mode="bp")
    tone = saw(seg_env([(0.0, f0 * 0.5), (1.0, f1 * 0.4)], dur), dur) * 0.3
    return (sweep + tone) * (np.linspace(0, 1, n) ** 2.2)


def tom_fill(beat, count=6, name="tom"):
    out = np.zeros(n_of(beat * count * 0.25 + 0.4))
    for i in range(count):
        f0 = 220 - i * 22
        d = 0.16
        t = sine(seg_env([(0.0, f0), (1.0, f0 * 0.45)], d), d) * exp_env(d, attack=0.001, decay=7.0)
        nz = bandpass(noise(d, name + str(i)), 200, 3000) * exp_env(d, decay=10.0) * 0.35
        at(out, i * beat * 0.25, saturate(t + nz, 2.2) * (0.6 + 0.4 * i / count))
    return out


def gen_menu_theme():
    bpm = 84.0
    beat = 60.0 / bpm
    bar = beat * 4
    bars = 8
    loop_dur = bar * bars
    buf = np.zeros((n_of(loop_dur + 6.0), 2))

    # i - VI - III - VII in A minor, two bars each
    chords = [
        ([57, 60, 64], 33),   # Am
        ([53, 57, 60], 29),   # F
        ([48, 52, 55], 24),   # C
        ([50, 55, 59], 31),   # G
    ]

    for i in range(4):
        chord, bass = chords[i]
        t0 = i * bar * 2
        # spread the pad voices across the field instead of stacking them dead centre
        for vi, m_num in enumerate([c + 12 for c in chord]):
            v = m_pad([m_num], bar * 2, fc_lo=380, fc_hi=1600)
            v = chorus(v, rate=0.28 + vi * 0.11, depth_ms=9, voices=2, mix=0.45, name="mpad%d%d" % (i, vi))
            at_st(buf, t0, v, 0.34, pan=(-0.65, 0.15, 0.7)[vi])
        at_st(buf, t0, m_note(bass, bar * 2, kind="sine", voices=1, fc=(400, 200), decay=1.2, attack=0.05), 0.42)
        at_st(buf, t0 + bar, m_note(bass, bar, kind="sine", voices=1, fc=(400, 200), decay=1.4, attack=0.05), 0.3)

    # bell arpeggio, bounced across the stereo field
    arp = np.zeros(len(buf))
    for b in range(bars):
        chord, _ = chords[(b // 2) % 4]
        pattern = [chord[0] + 12, chord[1] + 12, chord[2] + 12, chord[1] + 24,
                   chord[2] + 12, chord[1] + 12, chord[0] + 24, chord[1] + 12]
        for i, m_num in enumerate(pattern):
            at(arp, b * bar + i * (beat / 2), m_bell(m_num, 0.9), 0.16)
    buf += ping_pong(arp, beat * 0.75, feedback=0.36, mix=0.34, taps=5)[:len(buf)]

    # melody (bars 3-4 and 7-8), with a soft harmony a third below on the reprise
    mel = [(0.0, 69, 1.5), (1.5, 72, 0.5), (2.0, 76, 1.5), (3.5, 74, 0.5),
           (4.0, 72, 1.0), (5.0, 69, 1.0), (6.0, 67, 1.0), (7.0, 69, 1.0)]
    mel_buf = np.zeros((len(buf), 2))  # stereo: the harmony sits opposite the lead
    for rep, t_start in enumerate((bar * 2, bar * 6)):
        for (tb, m_num, db) in mel:
            for voice, (off, gain, pan) in enumerate((((0, 0.22, -0.12)), ((-3, 0.12, 0.3)))):
                if voice == 1 and rep == 0:
                    continue  # harmony only on the reprise, so the loop develops
                ns = m_note(m_num + off, db * beat * 0.95, kind="sine", voices=2, detune=0.004,
                            fc=(2200, 900), q=2.0, decay=1.6, attack=0.03)
                ns *= 1.0 + 0.04 * np.sin(2 * np.pi * 5.0 * np.arange(len(ns)) / SR)
                at_st(mel_buf, t_start + tb * beat, ns, gain, pan=pan)
    buf += ping_pong(mel_buf, beat * 1.5, feedback=0.3, mix=0.26, taps=4)[:len(buf)]

    kicks = []
    for b in range(bars):
        at_st(buf, b * bar, drum_kick(0.4, f0=120, f1=40, drive=1.6), 0.3)
        kicks.append(b * bar)
        at_st(buf, b * bar + beat * 2, drum_hat(0.08, name="mh%d" % b), 0.18, pan=0.25)
        if b % 2 == 1:
            at_st(buf, b * bar + beat * 3, drum_hat(0.1, name="mh2%d" % b), 0.12, pan=-0.3)

    buf = sidechain(buf, kicks, beat, amount=0.22, recover=0.35)
    buf = stereo_reverb(buf, decay=2.2, damp=5000, mix=0.28, name="menuverb")
    return make_loop(buf, n_of(loop_dur))


def gen_battle_theme():
    bpm = 150.0
    beat = 60.0 / bpm
    bar = beat * 4
    bars = 16  # two 8-bar sections so the loop doesn't wear out as fast
    loop_dur = bar * bars
    buf = np.zeros((n_of(loop_dur + 4.0), 2))

    # A: Am Am F G Am Am F E(maj) | B: same harmony, higher energy
    base_prog = [([57, 60, 64], 33), ([57, 60, 64], 33), ([53, 57, 60], 29), ([50, 55, 59], 31),
                 ([57, 60, 64], 33), ([57, 60, 64], 33), ([53, 57, 60], 29), ([52, 56, 59], 28)]
    prog = base_prog + base_prog

    for b in range(bars):
        chord, bass = prog[b]
        section_b = b >= 8
        # filter opens across each 8-bar section so energy builds into the turnaround
        openness = (b % 8) / 7.0
        bass_hi = 1500 + 1500 * openness + (600 if section_b else 0)

        # driving 16th bass with octave jumps
        for s in range(16):
            t = b * bar + s * (beat / 4)
            m_num = bass if s % 4 != 3 else bass + 12
            if s % 8 == 6:
                m_num = bass + 7
            nb = m_note(m_num, beat * 0.28, kind="saw", voices=2, detune=0.008,
                        fc=(bass_hi, 300), q=5.0, drive=2.3, decay=9.0, attack=0.002)
            at_st(buf, t, nb, 0.34)

        # power stab on the downbeat
        stab = np.zeros(n_of(beat * 0.8))
        for m_num in (bass + 12, bass + 19, bass + 24):
            stab += m_note(m_num, beat * 0.8, kind="saw", voices=2, detune=0.012,
                           fc=(3500, 800), q=4.0, drive=2, decay=7.0, attack=0.003)
        at_st(buf, b * bar, stab / 3.0, 0.3)

    # lead arpeggio — octave up and busier in section B
    arp = np.zeros(len(buf))
    for b in range(bars):
        chord, _ = prog[b]
        section_b = b >= 8
        seq = [chord[0], chord[1], chord[2], chord[1] + 12, chord[2], chord[1], chord[0] + 12, chord[2]]
        for s in range(16):
            m_num = seq[s % len(seq)] + (24 if section_b else 12)
            t = b * bar + s * (beat / 4)
            na = m_note(m_num, beat * 0.3, kind="saw", voices=2, detune=0.01,
                        fc=(5200, 1400), q=6.0, drive=1.6, decay=11.0, attack=0.002)
            at(arp, t, na, 0.15 if not section_b else 0.17)
    buf += ping_pong(arp, beat * 0.75, feedback=0.32, mix=0.3, taps=5)[:len(buf)]

    # drums
    kicks = []
    for b in range(bars):
        fill_bar = (b % 8) == 7
        for i in range(4):
            t = b * bar + i * beat
            at_st(buf, t, drum_kick(0.26, f0=160, f1=46, drive=2.7), 0.55)
            kicks.append(t)
        at_st(buf, b * bar + beat * 0.5, drum_kick(0.2, f0=140, f1=44, drive=2.3), 0.25)
        for i in (1, 3):
            at_st(buf, b * bar + i * beat, drum_snare(0.24, name="bs%d%d" % (b, i)), 0.42)
        if b >= 8:  # ghost notes add drive to the second section
            at_st(buf, b * bar + beat * 2.75, drum_snare(0.14, name="gh%d" % b), 0.14)
        for s in range(8):
            t = b * bar + s * (beat / 2)
            accent = 0.22 if s % 2 == 0 else 0.13
            at_st(buf, t, drum_hat(0.05, open_hat=(s == 7), name="bh%d%d" % (b, s)),
                  accent, pan=-0.25 if s % 2 else 0.25)
        if fill_bar:
            at_st(buf, b * bar + beat * 2, tom_fill(beat, count=8, name="tf%d" % b), 0.4)
            at_st(buf, b * bar + beat * 2, riser(beat * 2, 300, 6000, name="rs%d" % b), 0.16)

    at_st(buf, bar * 8, gen_explosion(dur=0.9, size=0.7, name="battleimp"), 0.22)

    buf = sidechain(buf, kicks, beat, amount=0.42, recover=0.22)
    buf = stereo_reverb(buf, decay=1.2, damp=6000, mix=0.18, name="battleverb")
    return make_loop(buf, n_of(loop_dur))



# ═══════════════════════════ driver ═══════════════════════════

# Everything pitched is tuned to A minor, the key of both music tracks, so a
# firefight stays harmonically coherent instead of sounding like an argument.
N = dict(F2=87.31, A2=110.00, C3=130.81, D3=146.83, E3=164.81, F3=174.61, G3=196.00,
         A3=220.00, C4=261.63, D4=293.66, E4=329.63, F4=349.23, G4=392.00, A4=440.00,
         C5=523.25, D5=587.33, E5=659.25, G5=783.99, A5=880.00, C6=1046.50, E6=1318.51)

# Master presets. `level` gives the mix real dynamics — a thrown card is not as
# loud as a thunderclap, and normalizing everything to the same peak (which is
# what the old set did) throws that away.
# Sounds allowed to keep their top end: ice, glass, sparkle, electricity, and
# the hitmarker, which has to cut through everything. Everything else is band-
# limited by `air_tame`, because a set where all 180 sounds occupy 60Hz-16kHz
# is a set where none of them is distinguishable from the others.
BRIGHT = dict(air=(12000.0, -2.0))
SPARK = dict(air=(10000.0, -4.5))      # electricity: bright, but not sibilant
BOLT_M = dict(width=0.55, transient=1.35, comp=(0.30, 2.8), tail=(0.32, 0.13))
TIGHT = dict(width=0.5, transient=1.55, comp=(0.30, 2.8), tail=(0.26, 0.11))
SWEEP = dict(width=0.7, transient=1.3, pan=(-0.55, 0.55), tail=(0.34, 0.14))
FLY = dict(width=0.55, transient=1.5, pan=(-0.45, 0.45), tail=(0.30, 0.12))
PUNCHY = dict(width=0.45, transient=1.8, comp=(0.28, 3.2), sub=(46, 0.42), tail=(0.34, 0.13))
WIDE = dict(width=0.9, transient=0.85, tail=(0.8, 0.20))
HEAVY = dict(width=0.95, transient=1.2, sub=(36, 0.55), comp=(0.26, 3.0), tail=(1.1, 0.24))
BIG = dict(width=0.85, transient=1.1, sub=(42, 0.45), comp=(0.28, 2.8), tail=(0.85, 0.22))
GHOST = dict(width=1.0, transient=0.5, tail=(1.3, 0.26))


def _sounds():
    """(filename, generator, level, length-class, master kwargs).

    Grouped the way a player experiences them, not the way the code is
    organised: what a character's basic attack sounds like, then what their
    abilities sound like. AbilitySounds.scala maps ProjectileType (and, where a
    projectile is shared by characters with clashing fantasies, the character
    itself) onto these names."""
    S = []
    A = S.append

    # ── magic bolts: primary attacks, so the shortest and quietest in the set ──
    A(("atk_normal_bolt", lambda: bolt("neutral", N["A3"]), 0.50, "bolt", BOLT_M))
    A(("atk_arcane_bolt", lambda: bolt("arcane", N["E4"]), 0.56, "bolt", BOLT_M))
    A(("atk_mystic_bolt", lambda: bolt("mystic", N["D4"]), 0.56, "bolt", BOLT_M))
    A(("atk_rune_bolt", lambda: bolt("rune", N["A2"]), 0.58, "bolt", BOLT_M))
    A(("atk_star_bolt", lambda: bolt("star", N["A4"]), 0.52, "bolt", dict(BOLT_M, **BRIGHT)))
    A(("atk_soul_bolt", lambda: bolt("soul", N["C3"]), 0.56, "bolt", BOLT_M))
    A(("atk_haunt_bolt", lambda: bolt("soul", N["A2"], dur=0.46), 0.60, "bolt", GHOST))
    A(("atk_death_bolt", lambda: bolt("dark", N["A2"]), 0.62, "bolt", BOLT_M))
    A(("atk_leech_bolt", lambda: bolt("dark", N["C3"], dur=0.42), 0.58, "bolt", BOLT_M))
    A(("atk_plague_bolt", lambda: bolt("poison", N["E3"]), 0.56, "bolt", BOLT_M))
    A(("atk_venom_bolt", lambda: bolt("acid", N["G3"]), 0.56, "bolt", BOLT_M))
    A(("atk_flame_bolt", lambda: bolt("fire", N["A3"]), 0.58, "bolt", BOLT_M))
    A(("atk_ember_shot", lambda: bolt("fire", N["E4"], dur=0.30), 0.52, "bolt", TIGHT))
    A(("atk_magma_ball", lambda: bolt("molten", N["F2"]), 0.66, "bolt", dict(BOLT_M, sub=(40, 0.42))))
    A(("atk_frost_shard", lambda: bolt("ice", N["E4"], extra=strike(
        "ice", 1480, 0.3, hardness=0.9, damp=1.8, name="fsx") * 0.45), 0.56, "bolt", dict(BOLT_M, **BRIGHT)))
    A(("atk_mud_glob", lambda: bolt("mud", N["F2"]), 0.58, "bolt", BOLT_M))
    A(("atk_sand_shot", lambda: bolt("sand", N["C3"]), 0.54, "bolt", BOLT_M))
    A(("atk_charm_bolt", lambda: bolt("charm", N["E5"]), 0.50, "bolt", dict(BOLT_M, tail=(0.7, 0.22), **BRIGHT)))
    A(("atk_nano_bolt", lambda: bolt("nano", N["A4"]), 0.48, "bolt", dict(TIGHT, **BRIGHT)))
    A(("atk_sting", lambda: bolt("electric", N["E4"], dur=0.30), 0.50, "bolt", dict(TIGHT, **BRIGHT)))
    A(("atk_shadow_bolt", gen_shadow_bolt, 0.62, "ability", dict(WIDE, tail=(1.0, 0.22))))
    A(("atk_void_bolt", gen_void_bolt, 0.70, "ability", dict(width=1.0, transient=0.7, sub=(32, 0.7),
                                                             pingpong=(0.14, 0.34, 0.2), tail=(1.2, 0.26))))
    A(("atk_gravity_ball", gen_gravity_ball, 0.70, "ability", dict(width=0.95, transient=0.7,
                                                                   sub=(30, 0.58), tail=(1.2, 0.26))))
    A(("atk_data_bolt", gen_data_bolt, 0.50, "tiny", dict(TIGHT, pingpong=(0.055, 0.24, 0.16), **BRIGHT)))
    A(("atk_virus_glitch", gen_virus_glitch, 0.58, "bolt", dict(width=0.9, transient=1.5, tail=(0.3, 0.12))))
    A(("atk_echo_bolt", gen_echo_bolt, 0.60, "ability", dict(GHOST, pingpong=(0.13, 0.3, 0.2))))
    A(("atk_curse_hex", gen_curse_hex, 0.62, "ability", dict(GHOST, pingpong=(0.11, 0.36, 0.22))))
    A(("atk_holy_bolt", lambda: mixdown(
        chime("holy", 0.34), pad_to(strike("steel", 780, 0.18, hardness=1.0, damp=2.2, name="hbs") * 0.4, 0.34),
        pad_to(sine(seg_env([(0.0, 175), (1.0, 96)], 0.11), 0.11) * exp_env(0.11, decay=8.0) * 0.5, 0.34)),
       0.62, "melee", dict(width=0.75, transient=1.4, comp=(0.32, 2.4), tail=(0.6, 0.18), **BRIGHT)))
    A(("atk_smite", lambda: mixdown(chime("smite", 0.7) * 1.3, pad_to(slam("metal", 0.4, name="smt") * 0.3, 0.7),
                                    pad_to(sparkle(0.55, 4000, 12000, count=14, name="smts") * 0.5, 0.7),
                                    pad_to(strike("bell", 1046.5, 0.5, hardness=1.0, damp=2.0, name="smb") * 0.5, 0.7)),
       0.72, "ability", dict(BIG, sub=(50, 0.3), comp=(0.32, 2.4), **BRIGHT)))
    A(("atk_hypnotic_melody", gen_hypnotic_melody, 0.58, "ability", dict(WIDE, tail=(1.2, 0.26))))

    # ── beams ──
    for v, lvl in (("ice", 0.58), ("laser", 0.56), ("railgun", 0.82), ("drain", 0.60),
                   ("vine", 0.56), ("stone", 0.62), ("whip", 0.58), ("gravity", 0.68), ("eye", 0.70)):
        sub = (42, 0.5) if v in ("railgun", "gravity", "eye") else None
        # the cyclops' beam is named for the eye, not for the family
        nm = "atk_eye_beam" if v == "eye" else "atk_beam_" + v
        kw = dict(width=0.75, transient=0.9, sub=sub, tail=(0.6, 0.18))
        if v in ("ice", "laser"):
            kw.update(BRIGHT)
        A((nm, (lambda vv: lambda: beam(vv))(v), lvl, "ability", kw))
    A(("atk_sniper_beam", lambda: mixdown(gun("light", 0.3, name="snp") * 0.85,
                                          pad_to(beam("laser", 0.34, name="snb") * 0.5, 0.34)),
       0.72, "ability", dict(width=0.5, transient=1.8, comp=(0.18, 5.0), tail=(0.5, 0.16))))

    # ── thrown weapons: the tumble rate and the material are the identity ──
    A(("atk_axe_throw", lambda: thrown("axe"), 0.62, "melee", SWEEP))
    A(("atk_hammer_throw", lambda: thrown("hammer"), 0.68, "melee", dict(SWEEP, sub=(44, 0.5))))
    A(("atk_bone_throw", lambda: thrown("bone"), 0.58, "melee", SWEEP))
    A(("atk_shuriken", lambda: thrown("shuriken", dur=0.3), 0.48, "melee", dict(FLY, **BRIGHT)))
    A(("atk_knife_throw", lambda: thrown("knife", dur=0.32), 0.50, "melee", dict(FLY, **BRIGHT)))
    A(("atk_card_throw", lambda: thrown("card", dur=0.26), 0.44, "tiny", FLY))
    A(("atk_cursed_blade", lambda: thrown("cursed"), 0.64, "melee", dict(SWEEP, tail=(0.8, 0.22))))
    A(("atk_holy_blade", lambda: thrown("holy"), 0.62, "melee", dict(SWEEP, tail=(0.9, 0.24))))
    A(("atk_boomerang_blade", lambda: mixdown(thrown("axe", dur=0.5, name="bmr"),
                                              pad_to(thrown("axe", dur=0.3, name="bmr2") * 0.4, 0.5)),
       0.62, "melee", SWEEP))
    A(("atk_shovel_throw", lambda: thrown("shovel"), 0.62, "melee", SWEEP))
    A(("atk_head_throw", lambda: thrown("head"), 0.66, "melee", dict(SWEEP, sub=(42, 0.42))))

    # ── swung weapons and claws ──
    A(("atk_katana_slash", lambda: swing("katana"), 0.56, "melee", dict(SWEEP, **BRIGHT)))
    A(("atk_claw_swipe", lambda: swing("claw"), 0.58, "melee", SWEEP))
    A(("atk_talon_swipe", lambda: swing("talon"), 0.56, "melee", SWEEP))
    A(("atk_scythe_swing", gen_scythe, 0.62, "melee", dict(SWEEP, tail=(0.7, 0.2))))
    A(("atk_reap_slash", gen_reap, 0.68, "ability", dict(width=0.9, transient=1.2,
                                                         pan=(-0.5, 0.5), tail=(1.0, 0.22))))

    # ── shafts ──
    A(("atk_spear_thrust", lambda: shaft("spear"), 0.58, "melee", FLY))
    A(("atk_arrow_shot", lambda: shaft("arrow"), 0.54, "melee", FLY))
    A(("atk_poison_arrow", lambda: mixdown(shaft("arrow", name="pa"),
                                           pad_to(gas(0.28, band=(900, 6200), fizz=0.9, name="pag") * 0.35, 0.3)),
       0.56, "melee", FLY))
    A(("atk_thorn_shot", lambda: shaft("thorn"), 0.52, "melee", FLY))
    A(("atk_ice_spike", lambda: shaft("spike"), 0.58, "melee", dict(FLY, **BRIGHT)))
    A(("atk_poison_dart", lambda: shaft("dart"), 0.48, "tiny", FLY))
    A(("atk_venom_spit", gen_venom_spit, 0.54, "melee", dict(TIGHT, pan=(-0.4, 0.4))))
    A(("atk_stinger", gen_stinger, 0.54, "melee", dict(TIGHT, width=0.55)))

    # ── firearms ──
    A(("atk_gunshot", lambda: gun("rifle"), 0.74, "melee", dict(width=0.55, transient=1.9,
                                                                sub=(50, 0.45), comp=(0.18, 5.5), tail=(0.5, 0.16))))
    A(("atk_gunshot_heavy", lambda: gun("heavy"), 0.82, "ability", dict(width=0.6, transient=1.8,
                                                                        sub=(42, 0.48), comp=(0.17, 5.5), tail=(0.7, 0.2))))
    A(("atk_gunshot_light", lambda: gun("light"), 0.66, "melee", dict(width=0.5, transient=2.0,
                                                                      sub=(54, 0.35), comp=(0.19, 5.5), tail=(0.4, 0.14))))
    A(("atk_flintlock", lambda: gun("flint"), 0.80, "ability", dict(width=0.65, transient=1.6,
                                                                    sub=(44, 0.48), comp=(0.17, 5.0), tail=(0.9, 0.24))))
    A(("atk_suppress_fire", lambda: _burst_fire(), 0.78, "ability", dict(width=0.6, transient=1.7,
                                                                         sub=(48, 0.36), comp=(0.17, 5.5), tail=(0.6, 0.18))))
    A(("atk_rocket_launch", gen_rocket, 0.78, "ability", dict(width=0.7, transient=1.2, sub=(42, 0.42),
                                                              pan=(-0.3, 0.8), tail=(0.7, 0.2))))

    # ── lobbed / traps ──
    A(("atk_grenade_lob", lambda: lobbed("grenade"), 0.68, "ability", dict(BIG, sub=(44, 0.42))))
    A(("atk_cannonball", lambda: lobbed("cannon", 0.8), 0.84, "heavy", HEAVY))
    A(("atk_mine_deploy", lambda: lobbed("mine", 0.45), 0.56, "melee", dict(TIGHT, width=0.6)))
    A(("atk_anvil_trap", lambda: lobbed("anvil", 0.75), 0.76, "ability", dict(BIG, sub=(40, 0.5))))
    A(("atk_rune_trap", lambda: mixdown(lobbed("rune", 0.55), pad_to(chime("rune", 0.5) * 0.45, 0.55)),
       0.62, "ability", dict(WIDE, tail=(0.9, 0.22))))
    A(("atk_flask_shatter", lambda: lobbed("flask", 0.6), 0.66, "ability", dict(BIG, sub=(48, 0.4), **BRIGHT)))
    A(("atk_mud_bomb", lambda: lobbed("mud", 0.55), 0.62, "ability", dict(BIG, sub=(40, 0.48))))
    A(("atk_blight_bomb", lambda: mixdown(lobbed("flask", 0.55, name="bb"),
                                          pad_to(burst("spore", 0.6, name="bbs") * 0.7, 0.6)),
       0.68, "ability", dict(BIG, sub=(42, 0.5))))
    A(("atk_frost_trap", lambda: mixdown(lobbed("mine", 0.4, name="ft"),
                                         pad_to(strike("ice", 900, 0.35, hardness=1.0, damp=1.2, name="ftx") * 0.7, 0.45)),
       0.60, "ability", dict(WIDE, tail=(0.8, 0.2), **BRIGHT)))
    A(("atk_ink_snare", gen_ink_snare, 0.64, "ability", dict(BIG, sub=(40, 0.42))))
    A(("atk_cluster_bomb", gen_cluster_bomb, 0.86, "heavy", HEAVY))
    A(("atk_napalm_strike", gen_napalm, 0.82, "heavy", dict(HEAVY, sub=(38, 0.52))))

    # ── ground slams: contact and consequence, never a detonation ──
    A(("atk_quake_slam", lambda: slam("earth"), 0.86, "heavy", HEAVY))
    A(("atk_ice_quake", lambda: mixdown(slam("ice", 0.85), pad_to(gen_avalanche() * 0.5, 0.85)),
       0.84, "heavy", dict(HEAVY, sub=(38, 0.48))))
    A(("atk_shield_bash", lambda: slam("shield", 0.7), 0.78, "ability", dict(BIG, sub=(40, 0.48))))
    A(("atk_mech_slam", lambda: mixdown(slam("metal", 0.8), pad_to(machine(0.4, servo=(200, 700),
                                                                           clank=0.8, name="mcs") * 0.6, 0.8)),
       0.82, "heavy", dict(HEAVY, sub=(38, 0.5))))
    A(("atk_ground_pound", lambda: mixdown(slam("flesh", 0.8), pad_to(gen_roar(f0=84, dur=0.4,
                                                                                growl=0.8, name="gp") * 0.5, 0.8)),
       0.84, "heavy", dict(HEAVY, sub=(34, 0.55))))
    A(("atk_shadow_burst", lambda: mixdown(slam("dark", 0.85), pad_to(burst("vortex", 0.7, name="sbs") * 0.6, 0.85)),
       0.80, "heavy", dict(HEAVY, sub=(32, 0.55))))
    A(("atk_nano_burst", gen_nano_burst, 0.70, "ability", dict(WIDE, tail=(0.7, 0.2))))
    A(("atk_soul_harvest", gen_soul_harvest, 0.74, "heavy", dict(GHOST, tail=(1.5, 0.3))))
    A(("atk_root_burst", lambda: burst("root", 0.75), 0.72, "ability", dict(BIG, sub=(44, 0.55))))
    A(("atk_thorn_wall", lambda: burst("thorn", 0.7), 0.70, "ability", dict(BIG, sub=(46, 0.45))))

    # ── clouds, vortices, surges ──
    A(("atk_miasma", lambda: burst("gas", 0.8), 0.64, "ability", dict(WIDE, tail=(1.0, 0.24))))
    A(("atk_poison_cloud", lambda: burst("spore", 0.85), 0.66, "ability", dict(WIDE, tail=(1.1, 0.26))))
    A(("atk_vortex_pull", gen_vortex_pull, 0.74, "heavy", dict(width=1.0, transient=0.6,
                                                               sub=(30, 0.58), tail=(1.3, 0.28))))
    A(("atk_overclock", gen_overclock, 0.72, "ability", dict(width=0.85, transient=1.1, tail=(0.7, 0.2))))
    A(("atk_splash", gen_splash, 0.60, "melee", dict(width=0.65, transient=1.4, tail=(0.45, 0.16))))

    # ── waves ──
    A(("atk_wind_wave", lambda: wavefront("wind"), 0.58, "melee", dict(width=0.9, transient=0.7, pan=(-0.7, 0.7))))
    A(("atk_sand_blast", lambda: wavefront("sand"), 0.60, "melee", dict(width=0.85, transient=0.9, pan=(-0.6, 0.6))))
    A(("atk_flame_wave", lambda: wavefront("flame"), 0.66, "ability", dict(width=0.85, transient=0.9,
                                                                      sub=(44, 0.5), pan=(-0.6, 0.6))))
    A(("atk_acid_spray", lambda: wavefront("acid"), 0.60, "melee", dict(width=0.85, transient=1.0, pan=(-0.6, 0.6))))
    A(("atk_sonic_wave", lambda: wavefront("sonic"), 0.60, "melee", dict(width=0.9, transient=0.9, pan=(0.65, -0.65))))
    A(("atk_sword_wave", lambda: mixdown(wavefront("impact", 0.45, name="swv"),
                                         pad_to(swing("steel", 0.34, name="swb") * 0.7, 0.45)),
       0.64, "melee", SWEEP))
    A(("atk_momentum_strike", gen_momentum_strike, 0.72, "ability", dict(PUNCHY, sub=(38, 0.52))))
    A(("atk_sonic_boom", gen_sonic_boom, 0.78, "heavy", dict(BIG, sub=(36, 0.5))))
    A(("atk_banshee_cry", gen_banshee_cry, 0.64, "ability", dict(GHOST, tail=(1.1, 0.24))))
    A(("atk_harpy_screech", gen_screech, 0.66, "ability", dict(width=0.9, transient=0.9, tail=(0.8, 0.22))))
    A(("atk_wail_shriek", gen_wail, 0.74, "heavy", dict(GHOST, pingpong=(0.19, 0.36, 0.24), tail=(1.6, 0.3))))
    A(("atk_howl", gen_howl, 0.76, "heavy", dict(GHOST, tail=(1.5, 0.3))))

    # ── chains and ropes ──
    A(("atk_chain_bolt", lambda: chain("iron"), 0.60, "melee", dict(width=0.8, transient=1.5, tail=(0.45, 0.16))))
    A(("atk_rope_throw", lambda: chain("rope"), 0.56, "melee", dict(width=0.7, transient=1.4, tail=(0.35, 0.14))))
    A(("atk_puppet_string", lambda: chain("string"), 0.54, "melee", dict(width=0.75, transient=1.5, tail=(0.4, 0.16))))

    # ── creatures ──
    A(("atk_jaw_chomp", gen_jaw_chomp, 0.66, "melee", PUNCHY))
    A(("atk_devour_bite", gen_devour, 0.70, "ability", dict(PUNCHY, sub=(42, 0.48))))
    A(("atk_blood_fang", gen_blood_fang, 0.64, "melee", PUNCHY))
    A(("atk_bat_swarm", gen_bat_swarm, 0.62, "ability", dict(width=1.0, transient=0.8, pan=(-0.8, 0.6))))
    A(("atk_shark_rush", gen_shark_rush, 0.66, "melee", dict(PUNCHY, width=0.6)))
    A(("atk_bull_bellow", gen_bull_bellow, 0.72, "ability", dict(width=0.8, transient=0.7,
                                                                 sub=(42, 0.42), tail=(0.9, 0.24))))
    A(("atk_tentacle_slap", gen_tentacle, 0.60, "melee", dict(PUNCHY, width=0.55)))
    A(("atk_grab", gen_grab, 0.60, "melee", dict(PUNCHY, width=0.5)))
    A(("atk_tongue_lash", gen_tongue, 0.56, "melee", dict(TIGHT, width=0.55)))
    A(("atk_web_shot", gen_web_shot, 0.54, "melee", dict(TIGHT, width=0.55)))
    A(("atk_punch", lambda: gen_punch(False), 0.68, "melee", dict(PUNCHY, sub=(46, 0.48))))
    A(("atk_charge_fist", lambda: gen_punch(True), 0.74, "ability", dict(PUNCHY, sub=(40, 0.55))))
    A(("atk_raise_dead", gen_raise_dead, 0.70, "heavy", dict(GHOST, tail=(1.7, 0.3))))

    # ── the big ones ──
    A(("atk_fireball", gen_fireball, 0.72, "ability", dict(BIG, sub=(44, 0.55))))
    A(("atk_demon_fire", gen_demon_fire, 0.76, "ability", dict(BIG, sub=(38, 0.48))))
    A(("atk_flambe", gen_flambe, 0.66, "ability", dict(BIG, sub=(46, 0.5))))
    A(("atk_meteor", gen_meteor, 0.86, "heavy", HEAVY))
    A(("atk_inferno_blast", gen_inferno_blast, 0.86, "heavy", dict(HEAVY, sub=(34, 0.8))))
    A(("atk_eruption", gen_eruption, 0.84, "heavy", dict(HEAVY, sub=(34, 0.55))))
    A(("atk_thunder_strike", gen_thunder_strike, 0.94, "heavy", dict(HEAVY, sub=(30, 0.62), tail=(1.6, 0.28))))
    A(("atk_lightning_crack", lambda: elec(0.5, size=1.0, colour=(600, 9000), forks=2, name="ltg"),
       0.78, "ability", dict(width=1.0, transient=1.6, sub=(44, 0.4), comp=(0.17, 5.0), tail=(0.9, 0.22), **SPARK)))
    A(("atk_chain_lightning", lambda: elec(0.62, size=0.9, colour=(900, 11000), forks=4, name="chl"),
       0.76, "ability", dict(width=1.0, transient=1.5, sub=(46, 0.35), comp=(0.18, 5.0), tail=(0.9, 0.22), **SPARK)))
    A(("atk_boulder_rumble", gen_boulder_rumble, 0.80, "heavy", dict(HEAVY, sub=(36, 0.8), tail=(1.0, 0.22))))
    A(("atk_avalanche_crush", gen_avalanche, 0.84, "heavy", dict(HEAVY, sub=(34, 0.55))))
    A(("atk_tidal_wave", gen_tidal_wave, 0.82, "heavy", dict(HEAVY, sub=(36, 0.7))))
    A(("atk_geyser_burst", gen_geyser, 0.70, "ability", dict(width=0.85, transient=1.0, tail=(0.7, 0.2))))

    # ── character voices: same projectile, different creature holding it ──
    A(("atk_ape_throw", gen_ape_throw, 0.80, "heavy", dict(HEAVY, sub=(38, 0.5))))
    A(("atk_giant_boulder", gen_giant_boulder, 0.86, "heavy", dict(HEAVY, sub=(32, 0.6))))
    A(("atk_stone_fist", gen_stone_fist, 0.78, "heavy", dict(HEAVY, sub=(38, 0.5))))
    A(("atk_shell_charge", gen_shell_charge, 0.70, "ability", dict(BIG, sub=(44, 0.4))))
    A(("atk_ice_chunk", gen_ice_chunk, 0.72, "ability", dict(BIG, sub=(42, 0.4), **BRIGHT)))
    A(("atk_bear_maul", gen_bear_maul, 0.72, "melee", dict(PUNCHY, sub=(40, 0.55))))
    A(("atk_mantis_scythe", gen_mantis_scythe, 0.56, "melee", dict(SWEEP, width=0.6)))
    A(("atk_fenrir_rend", gen_fenrir_rend, 0.74, "melee", dict(PUNCHY, sub=(38, 0.55))))
    A(("atk_ghoul_rake", gen_ghoul_rake, 0.62, "melee", SWEEP))
    A(("atk_hawk_dive", gen_hawk_dive, 0.58, "melee", SWEEP))
    A(("atk_griffin_rake", gen_griffin_rake, 0.64, "melee", dict(SWEEP, width=0.85)))
    A(("atk_phoenix_flare", gen_phoenix_flare, 0.60, "bolt", dict(BOLT_M, tail=(0.6, 0.2))))
    A(("atk_chimera_breath", gen_chimera_breath, 0.64, "bolt", BOLT_M))
    A(("atk_hellhound_spit", gen_hellhound_spit, 0.68, "bolt", dict(BOLT_M, sub=(40, 0.5))))
    A(("atk_warlock_bolt", gen_warlock_bolt, 0.64, "bolt", BOLT_M))
    A(("atk_anubis_bolt", gen_anubis_bolt, 0.60, "bolt", dict(BOLT_M, tail=(0.7, 0.22))))
    A(("atk_deathknight_blade", gen_deathknight_blade, 0.66, "melee", dict(SWEEP, tail=(0.7, 0.22))))
    A(("atk_poltergeist_hurl", gen_poltergeist_hurl, 0.60, "melee", dict(width=0.85, transient=1.5, tail=(0.6, 0.2))))
    A(("atk_djinn_bolt", gen_djinn_bolt, 0.58, "bolt", dict(BOLT_M, tail=(0.75, 0.24))))
    A(("atk_photon_beam", gen_photon_beam, 0.58, "ability", dict(width=0.8, transient=1.0, tail=(0.7, 0.22), **BRIGHT)))
    A(("atk_turret_laser", gen_turret_laser, 0.58, "ability", dict(width=0.65, transient=1.2, tail=(0.5, 0.16))))
    A(("atk_mech_gun", gen_mech_gun, 0.80, "ability", dict(width=0.6, transient=1.7, sub=(44, 0.5),
                                                            comp=(0.2, 4.0), tail=(0.6, 0.18))))
    A(("atk_tesla_arc", gen_tesla_arc, 0.72, "ability", dict(width=0.9, transient=1.5, tail=(0.7, 0.2), **SPARK)))
    A(("atk_time_bolt", gen_time_bolt, 0.66, "ability", dict(width=0.95, transient=1.0,
                                                              pingpong=(0.1, 0.3, 0.2), tail=(0.9, 0.24))))
    A(("atk_glacier_shard", gen_glacier_shard, 0.60, "bolt", dict(BOLT_M, **BRIGHT)))
    A(("atk_medusa_spit", gen_medusa_spit, 0.58, "bolt", BOLT_M))
    A(("atk_hydra_spit", gen_hydra_spit, 0.60, "melee", BOLT_M))
    A(("atk_treant_bark", gen_treant_bark, 0.56, "melee", FLY))
    A(("atk_inquisitor_bolt", gen_inquisitor_bolt, 0.56, "melee", dict(width=0.7, transient=1.5, tail=(0.55, 0.18))))
    A(("atk_lute_chord", gen_lute_chord, 0.58, "melee", dict(width=0.8, transient=1.2, tail=(0.7, 0.22))))
    A(("atk_song_wave", gen_song_wave, 0.60, "ability", dict(width=0.85, transient=0.9, tail=(0.8, 0.24))))
    A(("atk_sphinx_riddle", gen_sphinx_riddle, 0.56, "ability", dict(GHOST, tail=(1.0, 0.26))))
    A(("atk_shift_strike", gen_shift_strike, 0.68, "melee", dict(PUNCHY, sub=(44, 0.5))))
    return S


def _burst_fire():
    """A three-round burst — suppressing fire, which is what Mech Pilot and
    Sentinel are actually doing when they fire a 'chain'."""
    dur = 0.42
    out = np.zeros(n_of(dur))
    for i in range(3):
        at(out, i * 0.075, gun("light", 0.28, name="bf%d" % i), 0.95 - 0.12 * i)
    return out


# Sounds that are MEANT to hold: a beam is channelled, a howl is a held note,
# a gas cloud blooms. These get the `drone` contour instead of their class's,
# so the shape guarantee does not flatten the one thing they are for.
SUSTAINED = {
    "atk_beam_ice", "atk_beam_laser", "atk_beam_railgun", "atk_beam_drain", "atk_beam_vine",
    "atk_beam_stone", "atk_beam_whip", "atk_beam_gravity", "atk_eye_beam", "atk_photon_beam",
    "atk_howl", "atk_wail_shriek", "atk_banshee_cry", "atk_harpy_screech", "atk_raise_dead",
    "atk_bull_bellow", "atk_sphinx_riddle", "atk_hypnotic_melody", "atk_song_wave",
    "atk_lute_chord", "atk_soul_harvest", "atk_vortex_pull", "atk_miasma", "atk_poison_cloud",
    "atk_curse_hex", "atk_gravity_ball", "atk_void_bolt", "atk_overclock", "atk_time_bolt",
    "atk_tidal_wave", "atk_geyser_burst", "atk_inferno_blast", "atk_rocket_launch",
    "atk_echo_bolt", "atk_haunt_bolt",
    "spawn",
}


EVENTS = [
    ("spawn", gen_spawn, 0.68, "event", dict(width=1.0, transient=0.8, tail=(1.2, 0.24))),
    ("death", gen_death, 0.84, "event", dict(width=0.9, transient=1.0, sub=(36, 0.5), tail=(1.4, 0.26))),
    # hit_taken/hit_dealt stay narrow and centred — they are about you, not about
    # a place in the arena, and a wide image on a sound this frequent is fatiguing
    ("hit_taken", gen_hit_taken, 0.82, "melee", dict(width=0.28, transient=1.9, sub=(42, 0.38),
                                                     comp=(0.26, 3.2), air=(10000, -3.0), tail=(0.4, 0.10))),
    ("hit_dealt", gen_hit_dealt, 0.62, "tiny", dict(width=0.22, transient=2.0, comp=(0.18, 5.0),
                                                    dip=(3400, -2.5), air=(12000, -1.5),
                                                    tail=(0.2, 0.07))),
    ("hit_other", gen_hit_other, 0.56, "melee", dict(width=0.7, transient=1.5, comp=(0.2, 4.5),
                                                     tail=(0.5, 0.16))),
    ("explosion", lambda: gen_explosion(dur=1.2, size=1.1), 0.92, "heavy",
     dict(width=1.0, transient=1.2, sub=(34, 0.6), comp=(0.16, 5.0), tail=(1.5, 0.26))),
    ("dash", gen_dash, 0.58, "melee", dict(width=0.8, transient=1.3, pan=(-0.6, 0.6), tail=(0.4, 0.14))),
    ("teleport", gen_teleport, 0.62, "ability", dict(width=0.9, transient=1.2,
                                                     pingpong=(0.07, 0.3, 0.22), tail=(0.9, 0.22))),
    ("phase_shift", gen_phase_shift, 0.56, "ability", dict(width=1.0, transient=0.5, tail=(1.3, 0.28))),
    ("barrier_up", gen_barrier_up, 0.6, "ability", dict(width=0.85, transient=1.1, tail=(0.7, 0.2))),
    # heard for every shot a barrier stops, so short, narrow, and well down in the mix
    ("barrier_block", gen_barrier_block, 0.54, "melee", dict(width=0.5, transient=1.6, comp=(0.22, 4.0),
                                                             tail=(0.35, 0.12))),
    # A trap being laid is heard by everyone nearby, so it sits low; the snap it makes later is
    # the payoff and is allowed to be loud.
    ("trap_place", gen_trap_place, 0.5, "melee", dict(width=0.55, transient=1.4, comp=(0.26, 3.0),
                                                      tail=(0.3, 0.11))),
    ("trap_snap", gen_trap_snap, 0.74, "melee", dict(width=0.6, transient=1.9, sub=(44, 0.4),
                                                     comp=(0.22, 3.6), tail=(0.45, 0.14))),
    ("trap_poison", gen_trap_poison, 0.58, "ability", dict(width=0.8, transient=1.0,
                                                           tail=(0.6, 0.18))),
    ("trap_ignite", gen_trap_ignite, 0.62, "ability", dict(width=0.75, transient=1.2,
                                                           tail=(0.7, 0.2))),
]


def _check_scala_mapping(generated):
    """Cross-check against AbilitySounds.scala.

    The two files have to agree — a projectile whose sound name has no file
    silently falls back to the plain bolt, which is exactly the kind of thing
    nobody notices until a character has been shipped sounding like the
    default. Checking here means it is caught the moment sounds are rebuilt."""
    scala = os.path.join(os.path.dirname(__file__), "..", "src", "main", "scala",
                         "com", "gridgame", "client", "audio", "AbilitySounds.scala")
    if not os.path.exists(scala):
        return
    import re
    named = set(re.findall(r'"(atk_[a-z0-9_]+)"', open(scala).read()))
    missing = sorted(named - generated)
    unused = sorted(n for n in generated if n.startswith("atk_") and n not in named)
    if missing:
        raise SystemExit("AbilitySounds.scala names sounds that are not generated: "
                         + ", ".join(missing))
    if unused:
        print("  note: generated but unmapped in AbilitySounds.scala: " + ", ".join(unused))


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    names = set()
    count = 0
    for name, fn, lvl, clas, kw in _sounds() + EVENTS:
        if name in names:
            raise SystemExit("duplicate sound name: " + name)
        names.add(name)
        save_wav(name, fn(), level=lvl, clas=clas,
                 shape="drone" if name in SUSTAINED else None, **kw)
        count += 1

    # Music is already stereo and already mixed, so master() only limits it —
    # no presence dip, which would take the air off the pads. shape=False: the
    # class contours are for one-shots, and the default ("bolt") ducked each
    # loop to the -42dB floor 50ms in, which played as silence.
    save_wav("music_menu", gen_menu_theme(), level=0.74, trim=False, shape=False, tail=None,
             dip=None, air=None, glue=(0.30, 2.2))
    save_wav("music_battle", gen_battle_theme(), level=0.94, trim=False, shape=False, tail=None,
             dip=None, air=None, glue=(0.26, 2.8))
    count += 2

    _check_scala_mapping(names)
    print("generated %d sounds in %s" % (count, os.path.normpath(OUT_DIR)))


if __name__ == "__main__":
    main()
