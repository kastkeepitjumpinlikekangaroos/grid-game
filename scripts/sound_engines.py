#!/usr/bin/env python3
"""The family engines: the handful of shapes every attack in the game is one of.

A bolt of something, a weapon swung, a weapon thrown, a shaft flying point first, a thing lobbed,
a slam, a bloom, a wave, a gun, a chain, lightning, a machine, a blaster, holy light, a blast.
Keeping them as engines rather than 300 bespoke functions is what lets one quality change reach
the whole roster, the same reason every sprite goes through `sprite_base.generate_character`.
`sound_attacks.py` builds every character's attacks out of these; scripts/SOUNDS.md says how a
sound is designed and judged.

Every engine here is built the same way, which is the third pass's whole point (see *gestures* in
`sound_kit.py`): a HEAD (the first 30ms, which says how it began), a BODY that moves (what it is
made of, passing or leaving), and a TAIL (what it leaves behind), each with its own envelope and
its own part of the spectrum — and the weight where a small speaker can play it.
"""
import numpy as np

from sound_kit import *  # noqa: F401,F403  (the kit is this file's vocabulary)
from sound_kit import _PENTA  # noqa: F401


def lay(dur, *parts):
    """Layers placed in time: (start in seconds, signal, gain) each."""
    out = np.zeros(n_of(dur))
    for t0, sig, g in parts:
        at(out, t0, sig, g)
    return out


def with_(base, *layers):
    """A base sound plus what a character adds to it, each padded to whichever is longest."""
    n = max(len(base), *(len(l) for l in layers)) if layers else len(base)
    return mixdown(fit(base, n), *(fit(l, n) for l in layers))


def leaving(x, start=1.04, end=0.96):
    """Played a touch fast and slowing: the small fall in everything that a thing going away
    has. A few percent is all a projectile gets; more is a pass-by."""
    x = np.asarray(x, dtype=float)
    return fit(varispeed(x, np.linspace(start, end, len(x))), len(x))


# ═══════════════════════════ bolts ═══════════════════════════
# A ball of something leaving the caster. What it is made of comes from its voice; every voice
# is a head, a body leaving and a tail.
#
# History, because each of these was a fault once. A bolt was first one FM note per school of
# magic (a struck can at thirty pitches); then its element, honestly, but over a sine falling two
# octaves (a kick drum under every shot), with a dozen sine bleeps for sparkle and, for the
# schools with no material, three sawtooths holding one note: the Wizard's bolt was 43% a bare
# organ tone. Now nothing in a bolt is an oscillator on its own.

def _b_arcane(f0, dur, name):
    """Raw magic (the Wizard's): let go with a soft puff, a bright streak of air combed as it
    leaves, and the air itself singing an open fifth — high and glassy over a low hollow one —
    with a sheen of dust behind it. Nothing in it clicks."""
    rel = whoosh(dur * 0.85, lo=2200, hi=4800, end=500, peak=0.07, q=1.5, body=0.3, top=0.3,
                 flutter=0.2, rise=1.0, fall=2.4, name=name + "r")
    rel = flange(rel, 0.4, 4.5, depth=0.6, rise=True)
    ring = leaving(halo((f0 * 2, f0 * 3, f0 * 4, f0 * 6), dur * 0.8, q=30, peak=0.22, breath=0.3,
                        name=name + "h"), 1.035, 0.975)
    low = halo((f0 * 0.5, f0 * 0.75, f0), dur * 0.7, q=18, peak=0.15, breath=0.3, name=name + "l")
    dust = shimmer(dur, 3600, 11500, density=[(0.0, 0.3), (0.1, 1.0), (0.6, 0.4), (1.0, 0.05)], name=name + "d")
    return lay(dur, (0.0, snap("soft", name + "s"), 0.3), (0.002, norm(rel), 0.8), (0.004, ring, 0.6),
               (0.0, low, 0.2), (0.01, dust, 0.3))


def _b_mystic(f0, dur, name):
    """A spirit's breath (the Yokai's fox-fire, the Djinn's smoke): a bamboo flute's note — air
    singing on a harmonic series, scooped up into its pitch the way a player bends into one —
    with the chiff of the breath that starts it and a whisper under it. Eastern, not arcane."""
    n = n_of(dur)
    note = halo((f0, f0 * 2, f0 * 3, f0 * 4), dur, q=34, peak=0.3,
                gains=(1.0, 0.5, 0.3, 0.12), breath=0.45, wander=0.3, name=name + "t")
    note = fit(varispeed(note, glide([(0.0, 0.93), (0.18, 1.0), (1.0, 0.985)], dur)), n)
    chiff = bandpass(noise(0.04, name + "c"), 1800, 7500) * decay_env(0.04, attack=0.003)
    whisper = breath(dur, [(0.0, "ee"), (0.5, "uh"), (1.0, "oo")], name=name + "w") * arc(dur, 0.25, 1.0, 1.6)
    drift = whoosh(dur, lo=500, hi=1900, end=420, peak=0.2, q=1.2, body=0.5, top=0.1, flutter=0.4,
                   name=name + "a")
    return lay(dur, (0.0, norm(chiff), 0.4), (0.0, note, 0.4), (0.0, norm(whisper), 0.34), (0.0, drift, 0.5))


def _b_rune(f0, dur, name):
    """A carved rune firing: the stone it is cut in cracks, a deep thrum comes out of it and
    the cut lines burn for a moment."""
    chip = grains(0.05, rate=380, band=(380, 2800), length=(0.001, 0.005), density=[(0.0, 1.0), (1.0, 0.1)],
                  name=name + "g")
    thrum = halo((f0, f0 * 1.5, f0 * 2, f0 * 3), dur, q=24, peak=0.16, breath=0.2,
                 name=name + "t") * nlfo(dur, 19.0, 0.6, 1.0, name + "tr")
    burn = sizzle(dur * 0.6, band=(2200, 6500), crackle=0.7, name=name + "z") * arc(dur * 0.6, 0.08, 1.0, 2.0)
    streak = whoosh(dur, lo=420, hi=1700, end=300, peak=0.1, q=1.3, body=0.7, top=0.05, flutter=0.3,
                    name=name + "a")
    return lay(dur, (0.0, chip, 0.5), (0.0, norm(thrum), 0.62), (0.008, norm(burn), 0.2), (0.0, streak, 0.45))


def _b_star(f0, dur, name):
    """Starlight (the Astronomer's): a high glassy fifth of singing air, bright air streaking
    with it, and a long sheen of dust behind."""
    sing = halo((f0 * 2, f0 * 3, f0 * 4), dur * 0.7, q=40, peak=0.2, breath=0.2, name=name + "s")
    dust = shimmer(dur, 4200, 13000, density=[(0.0, 0.4), (0.1, 1.0), (0.45, 0.35), (1.0, 0.0)], name=name + "d")
    air_ = whoosh(dur, lo=1500, hi=5200, end=1200, peak=0.1, q=1.2, body=0.2, top=0.5, flutter=0.0,
                  rise=1.0, fall=2.6, name=name + "a")
    return lay(dur, (0.0, snap("soft", name + "p"), 0.25), (0.004, sing, 0.36), (0.0, dust, 0.6), (0.0, air_, 0.6))


def _b_soul(f0, dur, name):
    """A ghost: cold air let out of a mouth that isn't there, with three thin voices in it that
    never quite agree. Nothing starts it — it swells — and it drifts rather than flies."""
    whoo = breath(dur, [(0.0, "oo"), (0.45, "uh"), (1.0, "oo")], band=(220, 4200), name=name + "b")
    whoo = whoo * arc(dur, 0.2, 1.3, 1.5)
    moan = voices(dur * 0.9, [(0.0, f0 * 2.0), (0.3, f0 * 2.4), (1.0, f0 * 1.75)], [(0.0, "oo"), (1.0, "uh")],
                  count=3, spread=0.03, stagger=0.02, breath_=0.85, open_q=0.4,
                  amp_keys=((0.0, 0.0), (0.2, 0.9), (0.7, 0.6), (1.0, 0.0)), name=name + "m")
    cold = halo((f0 * 4, f0 * 6), dur, q=30, peak=0.35, breath=0.3, wander=0.4, name=name + "c")
    drift = whoosh(dur, lo=300, hi=1500, end=260, peak=0.25, q=1.2, body=0.6, top=0.1, flutter=0.5,
                   rise=1.4, fall=1.8, name=name + "a")
    return lay(dur, (0.0, norm(whoo), 0.7), (0.0, moan, 0.26), (0.0, cold, 0.22), (0.0, drift, 0.42))


def _b_dark(f0, dur, name):
    """Necrotic energy: a death rattle — a throat with almost no voice left in it, slow and
    broken — moving through the air, whispers riding it, and a hollow low fifth under it."""
    rattle_ = throat(glide([(0.0, f0 * 0.55), (1.0, f0 * 0.4)], dur), dur, rough=0.9, breath_=0.45, name=name + "g")
    rattle_ = saturate(svf(rattle_, glide([(0.0, 2600), (0.35, 1500), (1.0, 600)], dur), q=1.6, mode="lp"), 2.4)
    rattle_ = highpass(rattle_, 110.0)     # the rattle is its clicks, not a hum under them
    rattle_ *= arc(dur, 0.08, 1.0, 1.7)
    whisper = breath(dur, [(0.0, "ee"), (0.4, "ah"), (1.0, "uh")], name=name + "w") * arc(dur, 0.18, 1.0, 1.8)
    hollow = halo((f0, f0 * 1.5, f0 * 2), dur, q=20, peak=0.2, breath=0.25, name=name + "c")
    streak = whoosh(dur, lo=320, hi=1300, end=200, peak=0.1, q=1.4, body=0.8, top=0.0, flutter=0.3,
                    name=name + "a")
    return lay(dur, (0.0, snap("soft", name + "s"), 0.3), (0.0, norm(rattle_), 0.6), (0.0, norm(whisper), 0.26),
               (0.0, hollow, 0.42), (0.0, streak, 0.5))


def _b_leech(f0, dur, name):
    """Life being drawn out (the Lich's): the dark bolt with a wet pull rising through it and
    a heart beating twice."""
    base = _b_dark(f0, dur, name)
    suck = svf(pink(dur, name + "s"), glide([(0.0, 320), (1.0, 1700)], dur), q=2.6, mode="bp")
    suck *= swell_in(dur, 1.5) * seg_env([(0.0, 1.0), (0.85, 1.0), (1.0, 0.0)], dur)
    beat = heartbeat(0.12, min(dur, 0.34), f0=78.0, name=name + "h")
    return mixdown(base * 0.8, norm(suck) * 0.3, pad_to(beat, dur) * 0.42)


def _b_fire(f0, dur, name):
    """Combustion: it catches with a whump, roars as it goes with the flames licking unevenly,
    hisses, and pops. The band rides a little with f0, so a heavier flame is darker."""
    k = (f0 / 220.0) ** 0.5
    catch = whump(min(0.16, dur * 0.45), f=(190 * k, 64), name=name + "ig")
    roar_ = whoosh(dur, lo=520 * k, hi=2600 * k, end=520 * k, peak=0.12, q=1.2, body=0.7, top=0.15,
                   flutter=0.0, rise=1.2, fall=1.8, name=name + "r")
    roar_ = roar_ * nlfo(dur, 26.0, 0.35, 1.5, name + "lk")
    hiss = highpass(noise(dur, name + "h"), 3800) * nlfo(dur, 34.0, 0.0, 1.0, name + "hz") ** 2 * arc(dur, 0.1, 1.0, 2.2)
    pops = grains(dur, rate=60, band=(900, 6500), length=(0.0005, 0.003),
                  density=[(0.0, 1.0), (0.3, 0.7), (1.0, 0.15)], name=name + "p")
    return lay(dur, (0.0, catch, 0.5), (0.0, norm(roar_), 0.9), (0.0, norm(hiss), 0.16), (0.01, pops, 0.34))


def _b_ember(f0, dur, name):
    """A spark more than a flame: a tick, a quick flare and a spray of bright crackle off it."""
    flare = whoosh(dur, lo=1500, hi=4200, end=1100, peak=0.1, q=1.4, body=0.3, top=0.3, flutter=0.3,
                   rise=1.0, fall=2.6, name=name + "f")
    sparks = grains(dur, rate=150, band=(1800, 9500), length=(0.0003, 0.0018),
                    density=[(0.0, 1.0), (0.25, 0.8), (1.0, 0.1)], name=name + "s")
    catch = whump(0.1, f=(250, 95), name=name + "ig")
    return lay(dur, (0.0, snap("tick", name + "t"), 0.4), (0.0, flare, 0.75), (0.0, sparks, 0.5), (0.0, catch, 0.45))


def _b_molten(f0, dur, name):
    """A glob of lava: the heavy shove of it leaving, a low roar, and the rock itself — thick,
    slow bubbles bursting — spitting as it meets the air."""
    shove = whump(min(0.22, dur * 0.55), f=(150, 52), name=name + "ig")
    roar_ = whoosh(dur, lo=300, hi=1400, end=320, peak=0.15, q=1.2, body=0.9, top=0.0, flutter=0.0,
                   rise=1.2, fall=1.6, name=name + "r") * nlfo(dur, 17.0, 0.4, 1.4, name + "lk")
    blorp = squelch(dur * 0.8, f=(700, 160), q=2.6, lumps=5, name=name + "b")
    hiss = sizzle(dur, band=(2200, 7500), crackle=0.5, name=name + "z") * arc(dur, 0.2, 1.0, 1.6)
    return lay(dur, (0.0, shove, 0.8), (0.0, norm(roar_), 0.65), (0.01, blorp, 0.45), (0.0, norm(hiss), 0.26))


def _b_ice(f0, dur, name):
    """A shard of ice: it cracks into being (a bright cloud of fracture, and the crunch of the
    break), sings for a moment the way struck glass does — four clean partials, high and brief —
    whistles as it goes, and sheds frost."""
    crackle = grains(dur, rate=300, band=(2600, 9000), length=(0.0006, 0.002),
                     density=[(0.0, 1.0), (0.08, 0.5), (0.22, 0.05), (1.0, 0.0)], name=name + "c")
    crunch = grains(0.07, rate=150, band=(900, 3600), length=(0.002, 0.006), density=[(0.0, 1.0), (1.0, 0.1)],
                    name=name + "k")
    glass = halo((f0 * 4, f0 * 6, f0 * 8, f0 * 12), dur * 0.8, q=85, attack=0.004, t60=dur * 0.5, breath=0.0,
                 name=name + "g")
    whistle = whoosh(dur, lo=2400, hi=6200, end=1800, peak=0.1, q=2.0, body=0.15, top=0.5, flutter=0.15,
                     rise=1.0, fall=2.6, name=name + "a")
    frost = shimmer(dur, 5000, 13000, rate=140, grain=(0.004, 0.014), bell=0.4,
                    density=[(0.0, 0.3), (0.15, 1.0), (0.6, 0.4), (1.0, 0.05)], name=name + "f")
    return lay(dur, (0.0, crackle, 0.32), (0.0, crunch, 0.3), (0.003, glass, 0.22), (0.0, whistle, 0.65),
               (0.02, frost, 0.34))


def _b_poison(f0, dur, name):
    """Something toxic and liquid: a wet gulp as it leaves, small bubbles bursting all over it,
    and gas hissing off."""
    gulp = squelch(0.16, f=(1200, 260), q=3.0, lumps=2, name=name + "g")
    bub = bubbles(dur, count=16, f=(420, 1500), rise=(1.25, 1.8), size=(0.008, 0.026), span=(0.03, 0.85),
                  name=name + "b") * arc(dur, 0.15, 1.0, 1.4)
    hiss = gas(dur, band=(700, 4200), fizz=0.35, name=name + "h")
    slosh = whoosh(dur, lo=350, hi=1300, end=300, peak=0.12, q=1.6, body=0.7, top=0.0, flutter=0.4,
                   name=name + "s")
    return lay(dur, (0.0, gulp, 0.6), (0.0, bub, 0.32), (0.0, norm(hiss), 0.34), (0.0, slosh, 0.5))


def _b_acid(f0, dur, name):
    """Spat venom: the 'ptt' of it leaving a mouth, the thin hiss of it in the air, and the
    sizzle of it eating whatever it touches."""
    spit = bandpass(noise(0.05, name + "s"), 900, 5200) * exp_env(0.05, attack=0.0008, decay=11.0)
    hiss = whoosh(dur, lo=3200, hi=7000, end=2600, peak=0.1, q=1.6, body=0.0, top=0.6, flutter=0.2,
                  rise=1.0, fall=2.2, name=name + "h")
    fry = sizzle(dur, band=(2400, 8500), crackle=0.7, name=name + "z") * arc(dur, 0.18, 1.0, 1.5)
    wet = squelch(0.12, f=(1500, 420), q=2.6, lumps=2, name=name + "w")
    return lay(dur, (0.0, norm(spit), 0.75), (0.0, hiss, 0.36), (0.0, norm(fry), 0.36), (0.0, wet, 0.4))


def _b_mud(f0, dur, name):
    """A glob of mud: a heavy wet shlorp, clods pulling apart, and a dull push."""
    body = squelch(dur, f=(1000, 170), q=3.4, lumps=5, name=name + "q")
    push = whump(min(0.2, dur * 0.55), f=(140, 55), name=name + "w")
    clods = grains(dur * 0.6, rate=45, band=(250, 1500), length=(0.004, 0.011), q=(0.8, 1.5),
                   density=[(0.0, 1.0), (1.0, 0.1)], name=name + "c")
    return lay(dur, (0.0, body, 0.85), (0.0, push, 0.65), (0.01, clods, 0.3))


def _b_sand(f0, dur, name):
    """A fistful of grit: thrown with a puff, a hiss of grains, whirled as it goes."""
    grit = grains(dur, rate=900, band=(1100, 7500), length=(0.0002, 0.0012),
                  density=[(0.0, 1.0), (0.2, 0.75), (1.0, 0.05)], name=name + "g")
    whirl = whoosh(dur, lo=500, hi=2400, end=420, peak=0.14, q=1.5, body=0.6, top=0.1, flutter=0.6,
                   name=name + "a")
    push = whump(0.14, f=(170, 62), name=name + "w")
    return lay(dur, (0.0, snap("soft", name + "s"), 0.4), (0.0, grit, 0.55), (0.0, whirl, 0.6), (0.0, push, 0.4))


def _b_charm(f0, dur, name):
    """A love charm (the Enchantress's): a harp running up to a soft sung breath, a heart
    beating once under it, and dust."""
    harp = np.zeros(n_of(dur))
    for i, m in enumerate((0, 3, 7, 12)):
        f = f0 * 2 ** (m / 12.0)
        at(harp, 0.012 + i * 0.03, pluck(f, dur, t60=0.5, bright=0.6, name=name + "p%d" % i) * (0.8 - 0.1 * i))
    sigh = halo((f0, f0 * 1.5, f0 * 2), dur, q=36, peak=0.4, breath=0.3, name=name + "h")
    beat = heartbeat(0.11, min(dur, 0.3), f0=82.0, name=name + "b")
    dust = shimmer(dur, 4000, 11000, rate=90, density=[(0.0, 0.2), (0.3, 1.0), (1.0, 0.2)], name=name + "m")
    return lay(dur, (0.0, norm(harp), 0.6), (0.03, sigh, 0.36), (0.0, pad_to(beat, dur), 0.26), (0.0, dust, 0.26))


def _b_nano(f0, dur, name):
    """A swarm of machines: a thin beating buzz, the ticking of a thousand tiny parts, and the
    whirr of them holding formation."""
    sw = buzz(dur, rate=210, band=(2200, 8000), bugs=5, name=name + "b") * arc(dur, 0.1, 1.0, 1.5)
    ticks = grains(dur, rate=280, band=(3500, 11000), length=(0.0002, 0.0007),
                   density=[(0.0, 1.0), (0.4, 0.6), (1.0, 0.1)], name=name + "t")
    whirr = comb(bandpass(noise(dur, name + "w"), 600, 6000), glide([(0.0, 900.0), (0.3, 1500.0), (1.0, 1100.0)], dur),
                 fb=0.82, damp=0.3) * arc(dur, 0.12, 1.0, 1.8)
    return lay(dur, (0.0, snap("tick", name + "s"), 0.3), (0.0, norm(sw), 0.6), (0.0, ticks, 0.16),
               (0.0, norm(whirr), 0.36))


def _b_electric(f0, dur, name):
    """A sting of current: the snap of the discharge, an arc jumping about, a crackle of sparks,
    and the buzz of the charge behind it."""
    arc_ = zap_arc(dur, 900, 8000, q=7, steps=max(20, int(80 * dur / 0.3)), name=name + "a")
    arc_ *= exp_env(dur, attack=0.001, decay=7.0)
    sparks = grains(dur, rate=160, band=(2000, 9500), length=(0.0002, 0.001),
                    density=[(0.0, 1.0), (0.3, 0.5), (1.0, 0.05)], name=name + "s")
    hum = throat(118.0, dur, rough=0.25, breath_=0.2, name=name + "h")
    hum = lowpass(hum, 1500) * exp_env(dur, attack=0.004, decay=9.0)
    return lay(dur, (0.0, crack(0.02, name=name + "c"), 0.5), (0.0, norm(arc_), 0.65), (0.0, sparks, 0.4),
               (0.0, norm(hum), 0.2))


def _b_neutral(f0, dur, name):
    """The Spaceman's sidearm: see `blaster` — this is its plainest setting."""
    return blaster(dur, top=f0 * 11.0, bottom=f0 * 1.9, ring=0.8, name=name)


#           identity       f0  weight (gain, Hz)   tail        drive
BOLT_VOICES = {
    "neutral":  dict(make=_b_neutral,  f0=220, thump=0.24, wf=118, tail=(.30, .13), drive=1.2),
    "arcane":   dict(make=_b_arcane,   f0=330, thump=0.25, wf=115, tail=(.55, .22), drive=1.1),
    "mystic":   dict(make=_b_mystic,   f0=587, thump=0.15, wf=122, tail=(.65, .24), drive=1.1),
    "rune":     dict(make=_b_rune,     f0=110, thump=0.30, wf=97, tail=(.45, .20), drive=1.3),
    "star":     dict(make=_b_star,     f0=440, thump=0.13, wf=131, tail=(.70, .26), drive=1.1),
    "soul":     dict(make=_b_soul,     f0=131, thump=0.13, wf=110, tail=(.70, .26), drive=1.2),
    "dark":     dict(make=_b_dark,     f0=110, thump=0.33, wf=92, tail=(.55, .22), drive=1.4),
    "leech":    dict(make=_b_leech,    f0=131, thump=0.21, wf=97, tail=(.50, .20), drive=1.4),
    "fire":     dict(make=_b_fire,     f0=165, thump=0.18, wf=107, tail=(.35, .16), drive=1.5),
    "ember":    dict(make=_b_ember,    f0=330, thump=0.15, wf=137, tail=(.30, .14), drive=1.4),
    "molten":   dict(make=_b_molten,   f0=98,  thump=0.27, wf=82, tail=(.45, .20), drive=1.6),
    "ice":      dict(make=_b_ice,      f0=330, thump=0.17, wf=125, tail=(.55, .22), drive=1.1),
    "poison":   dict(make=_b_poison,   f0=147, thump=0.22, wf=105, tail=(.38, .17), drive=1.4),
    "acid":     dict(make=_b_acid,     f0=185, thump=0.18, wf=120, tail=(.34, .16), drive=1.4),
    "mud":      dict(make=_b_mud,      f0=87,  thump=0.18, wf=87, tail=(.28, .13), drive=1.5),
    "sand":     dict(make=_b_sand,     f0=131, thump=0.15, wf=110, tail=(.32, .15), drive=1.3),
    "charm":    dict(make=_b_charm,    f0=330, thump=0.09, wf=125, tail=(.80, .28), drive=1.05),
    "nano":     dict(make=_b_nano,     f0=247, thump=0.15, wf=130, tail=(.30, .14), drive=1.1),
    "electric": dict(make=_b_electric, f0=175, thump=0.18, wf=120, tail=(.34, .16), drive=1.3),
}


def bolt(voice, f0=None, dur=0.40, name="bolt", extra=None):
    """A ball of something leaving the caster. What it is made of comes from its voice (above);
    what every bolt shares is here: the weight of the launch, and the room.

    The weight is `weight`: a low push that settles and holds. Without it a bolt has no physical
    size and reads as a UI blip; as a sine falling two octaves (which it was) it is a kick drum."""
    p = BOLT_VOICES[voice]
    f0 = p["f0"] if f0 is None else f0
    out = norm(fit(p["make"](f0, dur, name + voice), n_of(dur)))
    if p["thump"] > 0:
        out = mixdown(out, pad_to(weight(p["wf"], min(0.14, dur * 0.5), name=name + "wt") * p["thump"], dur))
    out = saturate(out, p["drive"])
    if extra is not None:
        out = mixdown(out, pad_to(extra, len(out) / SR))
    return reverb(out, decay=p["tail"][0], damp=5600, mix=p["tail"][1], name="bolt", predelay=0.004)


# ── blasters ──
# A blaster bolt is the one place a falling pitch belongs: it is what the weapon is. What it must
# not be is an oscillator sliding down, which is a toy. The famous one was a hammer on a radio
# mast's guy-wire: a long steel cable is dispersive, the high frequencies of the blow arrive first
# and the low ones after, so a click comes out as a falling 'pchew' with the wire's own rattle in
# it. That is a burst of NOISE ringing in a resonance that falls — `comb` — and it is what this is.

def blaster(dur=0.28, top=2600.0, bottom=420.0, ring=0.85, buzz_hz=70.0, grit=0.5, fall=0.075, name="blst"):
    """A blaster bolt: a hard, bright 'pchew'. A spit of noise rung in a resonance that falls two
    octaves and more in `fall` seconds — a discharge, over before it can be heard as a note —
    with a little of the same fall as a rough tone under it for an edge, and the spark of the
    trigger."""
    n = n_of(dur)
    tt = t_axis(dur)
    drop = min(0.5, fall / dur)
    pitch = glide([(0.0, top), (drop, bottom * 1.25), (1.0, bottom * 0.7)], dur)
    burst_ = bandpass(noise(dur, name + "n"), 500, 9000) * np.exp(-tt * 30.0)
    wire = comb(burst_, pitch, fb=ring, damp=0.28) * decay_env(dur, attack=0.001, t60=dur * 0.8)
    edge = supersaw(pitch * 0.5, dur, voices=3, spread=0.01, name=name + "e")
    edge = edge * (0.6 + 0.4 * nlfo(dur, buzz_hz, 0.0, 1.0, name + "b")) * np.exp(-tt * 20.0)
    edge = svf(edge, glide([(0.0, 8000), (drop, 3600), (1.0, 1400)], dur), q=1.2)
    spit = zap_arc(0.05, 1500, 9000, q=6, steps=12, name=name + "z") * exp_env(0.05, attack=0.0005, decay=6.0)
    return lay(dur, (0.0, norm(wire), 0.8), (0.0, norm(edge), 0.3), (0.0, norm(spit), 0.35 * grit * 2),
               (0.0, snap("hard", name + "s"), 0.3))


# ═══════════════════════════ swung, thrown, loosed ═══════════════════════════

SWING_VOICES = {
    #          band (lo, hi)   q    what the edge does            weight  tail
    "steel":  dict(band=(700, 4200),  q=1.8, top=.30, cut="scrape", cut_g=.30, grit=.06, wt=.22, drive=1.2, tail=(.30, .14)),
    "katana": dict(band=(900, 5200),  q=1.8, top=.40, cut="shing",  cut_g=.34, grit=.00, wt=.12, drive=1.05, tail=(.40, .17)),
    "heavy":  dict(band=(380, 2600),  q=1.6, top=.15, cut="thud",   cut_g=.40, grit=.26, wt=.40, drive=1.5, tail=(.32, .15)),
    "claw":   dict(band=(500, 3200),  q=1.7, top=.20, cut="rip",    cut_g=.36, grit=.22, wt=.25, drive=1.4, tail=(.26, .13)),
    "talon":  dict(band=(800, 4400),  q=1.9, top=.30, cut="rip",    cut_g=.30, grit=.14, wt=.15, drive=1.3, tail=(.24, .12)),
    "scythe": dict(band=(420, 3000),  q=1.8, top=.25, cut="scrape", cut_g=.24, grit=.10, wt=.30, drive=1.3, tail=(.50, .20)),
    "holy":   dict(band=(800, 4200),  q=1.8, top=.35, cut="shine",  cut_g=.34, grit=.00, wt=.20, drive=1.1, tail=(.75, .24)),
    "cursed": dict(band=(320, 2200),  q=1.7, top=.10, cut="whisper", cut_g=.38, grit=.28, wt=.32, drive=1.6, tail=(.65, .23)),
    "bone":   dict(band=(420, 2800),  q=1.7, top=.15, cut="clack",  cut_g=.45, grit=.28, wt=.25, drive=1.5, tail=(.30, .14)),
    "wet":    dict(band=(280, 2000),  q=1.6, top=.05, cut="slap",   cut_g=.55, grit=.40, wt=.35, drive=1.6, tail=(.26, .13)),
    "chitin": dict(band=(900, 5000),  q=1.9, top=.30, cut="click",  cut_g=.45, grit=.12, wt=.12, drive=1.3, tail=(.22, .11)),
}


def edge(kind, dur, name):
    """What an edge does as it passes: a blade SINGS (`steel_song`: its own high resonances,
    swelling and gone, with nothing struck and nothing granular in it), a claw RIPS, a maw SLAPS
    wet, bone CLACKS, chitin CLICKS. The first swing rang the weapon's material here, and a claw
    that rings is a fork dropped on a tin plate; the third had a cloud of little chimes on a
    blade's cut, and a katana went by like a dropped handful of pins."""
    if kind == "scrape":
        # steel through the air: the blade's own song, low in it and brief
        return steel_song(dur, f=(3100.0, 4500.0, 6200.0), rise=1.06, q=36.0, peak=0.3, name=name)
    if kind == "shing":
        # a fine blade: the same song, higher, cleaner and longer — one smooth stroke
        return steel_song(dur, f=(4300.0, 5900.0, 7800.0), rise=1.05, q=55.0, peak=0.25, name=name)
    if kind == "rip":
        return rip(dur, rate=(120.0, 40.0), band=(500, 3600), name=name)
    if kind == "slap":
        return mixdown(squelch(dur, f=(1400, 300), q=2.6, lumps=2, name=name) * 0.8,
                       rip(dur * 0.7, rate=(140.0, 40.0), band=(300, 2400), name=name + "r") * 0.5)
    if kind == "clack":
        return grains(dur, rate=40, band=(600, 2200), length=(0.003, 0.007), q=(0.8, 1.3),
                      density=[(0.0, 1.0), (0.3, 0.5), (1.0, 0.0)], name=name)
    if kind == "click":
        return grains(dur, rate=55, band=(1500, 5000), length=(0.002, 0.004), q=(0.8, 1.3),
                      density=[(0.0, 1.0), (0.4, 0.4), (1.0, 0.0)], name=name)
    if kind == "shine":
        return mixdown(shimmer(dur, 3200, 10000, density=[(0.0, 1.0), (1.0, 0.15)], name=name) * 0.8,
                       steel_song(dur, f=(3600.0, 5200.0, 7000.0), rise=1.04, q=45.0, peak=0.3, name=name + "s") * 0.5)
    if kind == "whisper":
        return breath(dur, [(0.0, "ee"), (0.5, "ah"), (1.0, "oo")], name=name)
    if kind == "thud":
        return thud(120, dur, drive=1.8, name=name)
    raise ValueError(kind)


def swing(voice, dur=0.34, name="sw"):
    """An edge moving through air and connecting.

    The air is a pass-by (`whoosh`): the band climbs as the edge comes round and falls as it
    goes, loudest where it passes — a swing that sustains for its whole length reads as wind,
    not as a cut. At the cut the edge does whatever edges of its kind do (`edge`), and the arm
    behind it is a short low push."""
    p = SWING_VOICES[voice]
    lo, hi = p["band"]
    rush = whoosh(dur, lo=lo, hi=hi, end=lo * 0.8, peak=0.28, q=p["q"], body=0.25 + 1.4 * p["wt"], top=p["top"],
                  flutter=0.0, rise=1.5, fall=2.4, name=name + "a")
    grit = saturate(bandpass(pink(dur, name + "g"), 160, 1200), 3.0) * arc(dur, 0.3, 1.5, 3.0) * p["grit"]
    # the edge sounds with the stroke, from just before it passes to its end: begun at the pass
    # and cut short, as it was, it was a separate little event on top of the swing
    cut_t = dur * 0.12
    cut = norm(edge(p["cut"], dur * 0.85, name + "e")) * p["cut_g"]
    out = mixdown(rush * 0.85, grit)
    at(out, cut_t, cut)
    at(out, dur * 0.1, weight(105.0, min(0.14, dur * 0.45), name=name + "w") * p["wt"])
    out = saturate(out, p["drive"])
    return reverb(out, decay=p["tail"][0], damp=6200, mix=p["tail"][1], name="sw", predelay=0.004)


THROWN_VOICES = {
    #          turns/s  band           air it shoves  mass  grit  what it carries       tail
    "axe":    dict(rate=7.0,  band=(380, 2300),  body=.85, mass=.42, grit=.24, detail=None,      tail=(.36, .16)),
    "hammer": dict(rate=5.0,  band=(260, 1700),  body=.90, mass=.60, grit=.28, detail=None,      tail=(.42, .18)),
    "bone":   dict(rate=8.5,  band=(460, 2700),  body=.55, mass=.28, grit=.20, detail="clatter", tail=(.32, .15)),
    "shuriken": dict(rate=15.0, band=(1300, 5600), body=.25, mass=.12, grit=.06, detail="shing", tail=(.22, .11)),
    "knife":  dict(rate=10.0, band=(900, 4200),  body=.30, mass=.16, grit=.10, detail="shing",   tail=(.24, .12)),
    "card":   dict(rate=11.0, band=(1100, 5200), body=.05, mass=.00, grit=.00, detail="flick",   tail=(.18, .09)),
    "cursed": dict(rate=6.0,  band=(230, 1600),  body=.85, mass=.46, grit=.34, detail="whisper", tail=(.60, .23)),
    "holy":   dict(rate=7.5,  band=(700, 3600),  body=.60, mass=.34, grit=.06, detail="shine",   tail=(.75, .25)),
    "shovel": dict(rate=5.5,  band=(260, 1900),  body=.85, mass=.50, grit=.30, detail="dirt",    tail=(.38, .17)),
    "head":   dict(rate=4.0,  band=(160, 1200),  body=1.0, mass=.62, grit=.40, detail="snort",   tail=(.36, .16)),
}


def carried(kind, dur, name):
    """What a thrown thing brings with it besides its own tumble."""
    if kind == "clatter":
        # a skeleton's bone: dry and hollow, knocking on itself as it leaves
        return grains(dur, rate=55, band=(700, 2800), length=(0.002, 0.006), q=(0.8, 1.2),
                      density=[(0.0, 1.0), (0.2, 0.6), (0.6, 0.1), (1.0, 0.0)], name=name)
    if kind == "shing":
        # the thin blade leaving the fingers: a short scrape, gone in a blink
        return pad_to(steel_song(min(dur, 0.12), f=(4000.0, 5600.0, 7400.0), rise=1.04, q=45.0, peak=0.2, name=name), dur)
    if kind == "flick":
        # a card snapped off the top of the deck, then paper riding the air: the flutter is
        # uneven, as paper is (it was a square wave at 46Hz: a kazoo)
        snap_ = bandpass(noise(0.012, name + "s"), 1800, 8000) * exp_env(0.012, attack=0.0004, decay=5.0)
        flutter = bandpass(noise(dur, name + "f"), 1100, 5200) * nlfo(dur, 38.0, 0.0, 1.0, name + "fl") ** 2
        flutter *= arc(dur, 0.12, 1.0, 1.6)
        return mixdown(pad_to(norm(snap_), dur), norm(flutter) * 0.5)
    if kind == "whisper":
        return breath(dur, [(0.0, "ee"), (0.4, "ah"), (1.0, "oo")], name=name) * \
            seg_env([(0.0, 0.0), (0.2, 1.0), (1.0, 0.2)], dur)
    if kind == "shine":
        return mixdown(shimmer(dur, 3200, 10500, density=[(0.0, 0.5), (0.2, 1.0), (1.0, 0.2)], name=name),
                       halo((N["A4"], N["E5"], N["A5"]), dur, q=40, peak=0.3, name=name + "c") * 0.5)
    if kind == "dirt":
        # the spadeful of earth coming off it
        return grains(dur, rate=160, band=(250, 2200), length=(0.002, 0.008), q=(0.7, 1.4),
                      density=[(0.0, 0.2), (0.15, 1.0), (0.6, 0.4), (1.0, 0.0)], name=name)
    if kind == "snort":
        snort = bandpass(pink(0.2, name + "n"), 250, 2400) * seg_env([(0.0, 0.0), (0.1, 1.0), (1.0, 0.0)], 0.2)
        return pad_to(norm(snort), dur)
    raise ValueError(kind)


def tumble(dur, rate, slow=0.82, name="tmb"):
    """The loudness of a thing turning end over end: a peak each time an edge comes round, the
    turns a little uneven and slowing as the air takes them. (It was a sine: every throw in the
    game went round like a fan.)"""
    n = n_of(dur)
    r = np.linspace(rate, rate * slow, n) * nlfo(dur, 3.0, 0.94, 1.06, name)
    ph = np.cumsum(r) / SR
    spin = (0.5 + 0.5 * np.sin(2 * np.pi * ph - np.pi / 2)) ** 2.2
    return 0.18 + 0.82 * lowpass(spin, 120, order=1)


def thrown(voice, dur=0.44, name="th"):
    """A weapon tumbling end over end.

    The tumble is the identity: one air pulse per rotation, the rate set by the object's size.
    It is AIR, not impacts — nothing is struck when a weapon leaves a hand. This used to ring
    the weapon's material once on release, which with the tumble on top was a bucket kicked down
    the stairs."""
    p = THROWN_VOICES[voice]
    n = n_of(dur)
    spin = tumble(dur, p["rate"], name=name + "sp")
    lo, hi = p["band"]
    rush = whoosh(dur, lo=lo, hi=hi, end=lo * 0.75, peak=0.2, q=1.6, body=p["body"], top=0.15,
                  flutter=0.15, rise=1.2, fall=1.5, name=name + "a") * spin
    grit = saturate(bandpass(pink(dur, name + "g"), 130, 1100), 2.4) * spin * swell_env(dur, 1.0) * p["grit"]
    layers = [norm(rush) * 0.85, grit]
    if p["mass"] > 0:
        # the arm: a short low push as it is let go
        layers.append(pad_to(weight(100.0, 0.14, name=name + "w") * p["mass"], dur))
    if p["detail"] is not None:
        layers.append(norm(fit(carried(p["detail"], dur, name + "d"), n)) * 0.45)
    return reverb(saturate(mixdown(*layers), 1.8), decay=p["tail"][0], damp=6000, mix=p["tail"][1],
                  name="th", predelay=0.004)


SHAFT_VOICES = {
    #           release           band          q    flut  mass  hiss  tail
    "arrow":  dict(rel="string", band=(900, 3800),  q=1.9, flut=.20, mass=.32, hiss=.07, tail=(.26, .12)),
    "spear":  dict(rel="heave",  band=(380, 2100),  q=1.7, flut=.06, mass=.55, hiss=.03, tail=(.32, .15)),
    "thorn":  dict(rel="snap",   band=(700, 3000),  q=1.8, flut=.10, mass=.26, hiss=.05, tail=(.22, .11)),
    "dart":   dict(rel="puff",   band=(800, 3400),  q=1.9, flut=.06, mass=.18, hiss=.10, tail=(.20, .10)),
    "spike":  dict(rel="crackle", band=(1000, 4000), q=1.9, flut=.08, mass=.34, hiss=.08, tail=(.30, .14)),
}


def shaft(voice, dur=0.30, name="sh"):
    """Something long and pointed flying point-first. Two thirds of the sound is the release —
    a bowstring, an arm's heave, a puff of breath — because that is the part that happens near
    the listener; then the thin hiss of it going, and its fletching."""
    p = SHAFT_VOICES[voice]
    n = n_of(dur)
    if p["rel"] == "string":
        # a bowstring is a STRING: a low, harmonic thrum that dies in a moment, the slap of it on
        # the bracer, and the limbs' creak letting go. (It was a struck block of wood.)
        rel = pluck(98.0, 0.16, t60=0.11, bright=0.7, pick=0.12, name=name + "s") * 0.9
        rel *= exp_env(0.16, attack=0.001, decay=4.0)
        rel = mixdown(rel, snap("hard", name + "sc") * 0.4)
    elif p["rel"] == "heave":
        # an arm and a cloak: cloth pulled taut and a body's push. (It was a voice grunting 'uh',
        # which from a synthesizer is nobody's idea of effort.)
        cloth = bandpass(pink(0.12, name + "c"), 500, 4200) * arc(0.12, 0.25, 1.0, 2.0)
        rel = mixdown(norm(cloth) * 0.5, snap("soft", name + "g") * 0.6)
    elif p["rel"] == "puff":
        rel = highpass(pink(0.07, name + "p"), 1800) * exp_env(0.07, attack=0.002, decay=9.0) * 0.9
    elif p["rel"] == "crackle":
        rel = grains(0.08, rate=500, band=(2500, 10000), length=(0.0003, 0.0014),
                     density=[(0.0, 1.0), (1.0, 0.0)], name=name + "ck") * 0.8
    else:
        # a thorn snapped off: a dry woody crack with a little knock in it
        rel = mixdown(click(0.006, fc=2400, name=name + "sn", shape=1.2) * 0.8,
                      bandpass(noise(0.03, name + "sk"), 500, 2200) * exp_env(0.03, attack=0.0005, decay=9.0) * 0.5)
    lo, hi = p["band"]
    fly = whoosh(dur, lo=hi, hi=hi * 1.25, end=lo, peak=0.06, q=p["q"], body=0.7, top=0.25, flutter=0.1,
                 rise=1.0, fall=2.4, name=name + "a")
    flut = bandpass(noise(dur, name + "f"), 1400, 5000) * nlfo(dur, 30.0, 0.0, 1.0, name + "fl") ** 2
    flut *= swell_env(dur, 1.4) * p["flut"] * 1.5
    hiss = bandpass(pink(dur, name + "h"), 2600, 8000) * swell_env(dur, 2.0) * p["hiss"]
    out = mixdown(fly * 0.75, flut, hiss, pad_to(weight(110.0, 0.12, name=name + "w") * p["mass"], dur))
    out[:len(rel)] += rel[:n]
    return reverb(saturate(out, 1.8), decay=p["tail"][0], damp=6400, mix=p["tail"][1],
                  name="sh", predelay=0.003)


LOB_VOICES = {
    #           what it carries up        what lands (only where nothing else will)  heave  tail
    "grenade": dict(carry="pin",    land=None,    heave=.42, tail=(.45, .18)),
    "bomb":    dict(carry="fuse",   land=None,    heave=.36, tail=(.40, .17)),
    "mine":    dict(carry="arm",    land="thunk", heave=.24, tail=(.35, .16)),
    "flask":   dict(carry="potion", land=None,    heave=.27, tail=(.40, .18)),
    "mud":     dict(carry="glop",   land=None,    heave=.36, tail=(.30, .14)),
    "anvil":   dict(carry=None,     land="anvil", heave=.55, tail=(.60, .22)),
    "ink":     dict(carry="squirt", land="splat", heave=.24, tail=(.32, .15)),
    "rune":    dict(carry="glow",   land="rune",  heave=.21, tail=(.70, .26)),
}


def _lob_carry(kind, dur, name):
    """What goes up with it."""
    if kind == "pin":
        # the pin drawn and the spoon flicking off: two small clicks of light metal, not a
        # struck plate
        pin = rattle(0.05, count=3, band=(3000, 7000), q=12.0, cluster=1.0, name=name + "p")
        spoon = scrape(0.07, lo=3600, hi=6800, bands=2, q=4.0, name=name + "s") * 0.5
        out = np.zeros(n_of(dur))
        at(out, 0.0, pin)
        at(out, 0.05, spoon)
        return out
    if kind == "fuse":
        # a cartoon bomb's lit fuse, sputtering all the way up
        f = sizzle(dur, band=(2600, 8500), crackle=0.9, name=name + "f")
        return f * seg_env([(0.0, 0.0), (0.03, 1.0), (0.8, 0.8), (1.0, 0.0)], dur)
    if kind == "potion":
        # the cork out, then liquid slopping about inside the glass
        slosh = bubbles(dur, count=10, f=(300, 1100), rise=(1.2, 1.7), size=(0.01, 0.03), span=(0.1, 0.8),
                        name=name + "b")
        wet = svf(pink(dur, name + "w"), glide([(0.0, 600), (0.5, 1400), (1.0, 500)], dur), q=2.0, mode="bp")
        wet *= arc(dur, 0.25, 1.0, 1.6) * nlfo(dur, 9.0, 0.4, 1.0, name + "wl")
        return mixdown(pad_to(snap("pop", name + "c"), dur), slosh * 0.4, norm(wet) * 0.4)
    if kind == "glop":
        return squelch(min(dur, 0.3), f=(900, 180), q=3.2, lumps=4, name=name)
    if kind == "squirt":
        jet = svf(pink(0.18, name + "j"), glide([(0.0, 3000), (1.0, 700)], 0.18), q=2.2, mode="bp")
        jet *= exp_env(0.18, attack=0.004, decay=5.0)
        return mixdown(norm(jet), squelch(0.2, f=(1200, 300), q=2.8, lumps=3, name=name + "q") * 0.6)
    if kind == "arm":
        return mixdown(click(0.005, fc=2600, name=name + "a") * 0.8,
                       pad_to(grains(0.06, rate=90, band=(1500, 5000), length=(0.001, 0.003),
                                     name=name + "g") * 0.4, 0.06))
    if kind == "glow":
        # a glyph catching light as it goes: a soft sizzle and a low bloom
        return mixdown(sizzle(dur, band=(1800, 7000), crackle=0.6, name=name + "z") *
                       seg_env([(0.0, 0.0), (0.2, 1.0), (1.0, 0.2)], dur) * 0.6,
                       pad_to(whump(0.2, f=(160.0, 60.0), name=name + "w") * 0.5, dur))
    raise ValueError(kind)


def _lob_land(kind, dur, name):
    """What it does when it comes down, for the ones nothing else voices."""
    if kind == "splat":
        return mixdown(water(dur, size=1.2, bubbles=10, foam=0.8, name=name + "w"),
                       squelch(dur * 0.8, f=(700, 140), q=3.0, lumps=4, name=name + "q") * 0.7)
    if kind == "thunk":
        return mixdown(thud(100, dur, drive=1.6, name=name + "t"),
                       grains(dur * 0.5, rate=120, band=(200, 1600), length=(0.002, 0.006), name=name + "e") * 0.4)
    if kind == "anvil":
        # tons of iron on dirt: the body is the thud and the dust; the metal is a dull, heavily
        # damped clunk under it, not a note
        return mixdown(thud(72, dur, drive=2.0, name=name + "t"),
                       strike("steel", 140, dur * 0.6, hardness=0.8, damp=3.2, name=name + "s", ring=0.1) * 0.6,
                       debris(dur, count=10, band=(200, 2000), name=name + "d") * 0.5)
    if kind == "rune":
        return mixdown(thud(90, dur, drive=1.5, name=name + "t"),
                       shimmer(dur, 2600, 8000, rate=90, density=[(0.0, 1.0), (1.0, 0.1)], name=name + "s") * 0.5)
    raise ValueError(kind)


def lobbed(voice, dur=0.66, name="lob"):
    """An object thrown up on an arc. What is heard is the throw — the arm, and whatever the
    object does on the way up (a pin, a fuse, a potion's cork) — and, only where nothing else in
    the game sounds when it comes down, the landing. A bomb's detonation is its blast, played
    when it actually goes off."""
    p = LOB_VOICES[voice]
    out = np.zeros(n_of(dur))
    arm = whoosh(min(dur, 0.3), lo=320, hi=2200, end=500, peak=0.3, q=1.6, body=0.8, top=0.1, flutter=0.2,
                 name=name + "arc")
    at(out, 0.0, arm * 0.5)
    at(out, 0.0, weight(96.0, 0.14, name=name + "h") * p["heave"])
    if p["carry"] is not None:
        at(out, 0.0, norm(fit(_lob_carry(p["carry"], dur, name + "c"), n_of(dur))) * 0.6)
    if p["land"] is not None:
        at(out, dur * 0.5, norm(_lob_land(p["land"], dur * 0.5, name + "l")) * 0.9)
    return reverb(saturate(out, 1.8), decay=p["tail"][0], damp=4600, mix=p["tail"][1], name="lob",
                  predelay=0.006)


# ═══════════════════════════ ground, clouds, waves ═══════════════════════════

SLAM_VOICES = {
    #            mat      f0   body crunch deb  rumble crack tail       size  damp  ring
    "earth":   dict(mat="earth", f0=82,  sub=1.0, crn=.9, deb=.85, rum=.9, crk=.4, tail=(1.0, .26), size=1.3, damp=1.0, ring=None),
    "stone":   dict(mat="stone", f0=98,  sub=.95, crn=1.0, deb=1.0, rum=.8, crk=.6, tail=(.95, .26), size=1.25, damp=1.0, ring=None),
    "ice":     dict(mat="ice",   f0=210, sub=.75, crn=.7, deb=.75, rum=.6, crk=1.0, tail=(1.0, .28), size=1.2, damp=1.4, ring=.12),
    # a mech fist and a shield are metal, but a heavy one struck into the ground or a body: the
    # thud and the scatter carry it, the metal is a short dull clunk
    "metal":   dict(mat="steel", f0=140, sub=.70, crn=.6, deb=.35, rum=.4, crk=.6, tail=(.85, .26), size=1.0, damp=2.6, ring=.10),
    "shield":  dict(mat="steel", f0=170, sub=.60, crn=.5, deb=.25, rum=.3, crk=.6, tail=(.80, .25), size=1.0, damp=3.0, ring=.08),
    "flesh":   dict(mat="flesh", f0=76,  sub=1.0, crn=.6, deb=.45, rum=.85, crk=.2, tail=(.75, .24), size=1.15, damp=1.0, ring=None),
    "dark":    dict(mat="flesh", f0=68,  sub=1.0, crn=.4, deb=.30, rum=1.0, crk=.2, tail=(1.2, .30), size=1.4, damp=0.9, ring=None),
    "wood":    dict(mat="wood",  f0=120, sub=.80, crn=.9, deb=.80, rum=.6, crk=.5, tail=(.80, .25), size=1.2, damp=1.2, ring=None),
}


def slam(voice, dur=0.9, name="slam"):
    """A body or a weapon meeting the ground, and the ground answering: the contact, the
    material under it, the crunch of the surface giving, the air it shoves out, what it throws
    up and the rumble after.

    Not an explosion (the first set routed every ground ability through the bomb generator). And
    not a sub-bass drop with some hiss over it, which is what it then was: 95% of the Earthshaker's
    slam lay under 120Hz, so on a laptop the earth split in silence. The crunch and the shove of
    air are in the low mids, where every speaker is."""
    p = SLAM_VOICES[voice]
    icy = p["mat"] == "ice"
    hit = strike(p["mat"], p["f0"], dur * 0.75, hardness=1.0, damp=0.85 * p["damp"], name=name + "h",
                 ring=p["ring"])
    body = mixdown(thud(p["f0"] * 0.95, dur * 0.5, drive=2.2, name=name + "t"),
                   pad_to(weight(66.0, dur * 0.45, name=name + "w") * 0.7, dur * 0.5))
    crunch = grains(0.16, rate=260, band=(900, 5000) if icy else (220, 1800), length=(0.003, 0.012),
                    q=(0.7, 1.5), density=[(0.0, 1.0), (0.4, 0.5), (1.0, 0.0)], name=name + "cr")
    shove = whoosh(0.3, lo=260, hi=1500, end=200, peak=0.05, q=1.1, body=0.8, top=0.0, flutter=0.2,
                   rise=1.0, fall=2.6, name=name + "sv")
    rum = bandpass(pink(dur, name + "r"), 55, 360) * ad_env(dur, attack=0.02, hold=0.35, curve=1.4)
    rum *= nlfo(dur, 13.0, 0.5, 1.0, name + "rl")
    crk = click(0.03, fc=1800, name=name + "c", shape=1.6)
    deb = debris(dur, count=int(18 * p["deb"]) + 4,
                 band=(300, 2600) if not icy else (900, 6000),
                 spread=(0.03, 0.7), name=name + "d") * 0.55
    out = mixdown(hit * 0.9, pad_to(norm(body) * p["sub"] * 0.8, dur), pad_to(crunch * p["crn"] * 0.55, dur),
                  pad_to(shove * 0.45, dur), norm(rum) * p["rum"] * 0.35, deb * p["deb"])
    out[:len(crk)] += crk * p["crk"]
    out = saturate(out, 1.9)
    return reverb(out, decay=p["tail"][0], damp=3400, mix=p["tail"][1], name="slam",
                  predelay=0.012, size=p["size"])


BURST_VOICES = {
    #            kind    band            low   grain  what rides it   tail
    "gas":     dict(kind="gas",  band=(400, 5200), low=.25, grain=.15, voice=None,     tail=(.85, .26)),
    "spore":   dict(kind="gas",  band=(300, 3400), low=.32, grain=.40, voice=None,     tail=(.95, .28)),
    "root":    dict(kind="wood", band=(180, 2400), low=.50, grain=.85, voice="creak",  tail=(.80, .25)),
    "thorn":   dict(kind="wood", band=(400, 4200), low=.36, grain=.95, voice="creak",  tail=(.70, .24)),
    "vortex":  dict(kind="suck", band=(120, 3600), low=.55, grain=.20, voice="whirl",  tail=(1.1, .30)),
    "soul":    dict(kind="suck", band=(260, 4200), low=.38, grain=.10, voice="moan",   tail=(1.3, .32)),
    "water":   dict(kind="water", band=(300, 4600), low=.50, grain=.30, voice=None,    tail=(.85, .26)),
    "surge":   dict(kind="elec", band=(600, 8000), low=.32, grain=.25, voice="hum",    tail=(.70, .24)),
    "swarm":   dict(kind="swarm", band=(900, 7000), low=.25, grain=.60, voice=None,    tail=(.65, .22)),
}


def creak(dur, rate=(45.0, 110.0), body=(350.0, 750.0), q=4.0, name="ck"):
    """Wood — or a root, or a rope — under strain: stick-slip friction. A train of tiny slips
    whose rate is the low pitch you hear, each ringing the body of the wood. It groans; it never
    rings."""
    n = n_of(dur)
    rng = np.random.default_rng(seed_of(name))
    rt = seg_env([(0.0, rate[0]), (0.6, (rate[0] + rate[1]) * 0.55), (1.0, rate[1])], dur)
    imp = np.zeros(n)
    t = 0.0
    while True:
        i = int(t * SR)
        if i >= n:
            break
        imp[i] = rng.uniform(0.4, 1.0)
        t += rng.uniform(0.8, 1.2) / rt[i]
    out = res_bp(imp, body[0], q) + res_bp(imp, body[1], q) * 0.6
    out *= seg_env([(0.0, 0.0), (0.1, 1.0), (0.75, 0.8), (1.0, 0.0)], dur)
    return norm(out)


def _burst_voice(kind, dur, name):
    if kind == "creak":
        return creak(dur, rate=(38.0, 95.0), body=(260.0, 620.0), q=3.5, name=name)
    if kind == "whirl":
        # the vortex spinning up: a low band turning faster and faster
        n = n_of(dur)
        spin = np.sin(2 * np.pi * np.cumsum(glide([(0.0, 3.0), (1.0, 11.0)], dur)) / SR)
        w = svf(pink(dur, name + "w"), glide([(0.0, 900), (1.0, 300)], dur), q=1.6, mode="bp")
        return norm(w * (0.55 + 0.45 * spin) * np.linspace(0.3, 1.0, n))
    if kind == "moan":
        return voices(dur * 0.85, [(0.0, 190.0), (0.5, 250.0), (1.0, 150.0)], [(0.0, "oo"), (1.0, "uh")],
                      count=4, spread=0.12, stagger=0.06, breath_=0.75, open_q=0.4,
                      amp_keys=((0.0, 0.0), (0.3, 0.9), (1.0, 0.0)), name=name)
    if kind == "hum":
        return lowpass(throat(118.0, dur, rough=0.2, breath_=0.15, name=name), 1400) * swell_env(dur, 1.2)
    raise ValueError(kind)


def burst(voice, dur=0.8, name="bst"):
    """An area effect that blooms rather than detonates — gas, roots, a vortex, a swarm. These
    used to share the explosion generator, which is why a poison cloud and a frag grenade were
    the same event."""
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
        core = svf(pink(dur, name + "s"), glide([(0.0, p["band"][1]), (1.0, p["band"][0])], dur),
                   q=2.5, mode="bp")
        core *= np.linspace(0.15, 1.0, n) ** 2.0
    elif k == "swarm":
        core = buzz(dur, rate=150, band=p["band"], bugs=6, name=name + "sw") * swell_env(dur, 1.1)
    else:  # wood: fibres tearing and splitting
        core = saturate(bandpass(pink(dur, name + "wd"), *p["band"]), 2.6)
        core *= seg_env([(0.0, 0.2), (0.15, 1.0), (0.6, 0.5), (1.0, 0.0)], dur)
    # splintering wood, snapping fibre, scattering shell — filtered grains, not a chord of
    # resonators
    scatter = debris(dur, count=int(22 * p["grain"]) + 3,
                     band=(200, 2400) if k == "wood" else (700, 5600),
                     decay=(14.0, 34.0), spread=(0.0, 0.75), name=name + "gr") * 0.5
    layers = [norm(core) * 0.85, scatter * p["grain"],
              pad_to(weight(72.0, dur * 0.5, name=name + "lw") * p["low"], dur)]
    if p["voice"] is not None:
        layers.append(norm(fit(_burst_voice(p["voice"], dur, name + "v"), n)) * 0.45)
    out = saturate(mixdown(*layers), 1.9)
    return reverb(out, decay=p["tail"][0], damp=4200, mix=p["tail"][1], name="bst",
                  predelay=0.008, size=1.2)


WAVE_VOICES = {
    #           band            q   howl (lo, hi)  drive body  tail
    "wind":   dict(band=(240, 2400),  q=1.6, tone=(260, 760),  drive=1.2, body=.55, tail=(.44, .19)),
    "sonic":  dict(band=(360, 3600),  q=1.8, tone=(320, 900),  drive=1.4, body=.60, tail=(.48, .21)),
    "sand":   dict(band=(300, 2900),  q=1.5, tone=(240, 620),  drive=1.5, body=.50, tail=(.38, .17)),
    "flame":  dict(band=(280, 2200),  q=1.3, tone=(200, 520),  drive=1.8, body=.55, tail=(.44, .19)),
    "acid":   dict(band=(440, 3400),  q=1.6, tone=(300, 700),  drive=1.6, body=.45, tail=(.38, .17)),
    "impact": dict(band=(180, 1900),  q=1.4, tone=(170, 420),  drive=1.9, body=.75, tail=(.42, .19)),
    "water":  dict(band=(260, 2800),  q=1.4, tone=(190, 480),  drive=1.3, body=.60, tail=(.52, .22)),
}


def wavefront(voice, dur=0.52, name="wv"):
    """A sweeping crescent — wind, sound, sand, flame — passing: the rush of it, and under it
    the howl a gust makes, which is noise held in a resonance that rises and falls with the
    gust's speed. (The howl was an FM oscillator swooping up a fifth and back: the 'wub' under
    every wave in the game.)"""
    p = WAVE_VOICES[voice]
    lo, hi = p["band"]
    sweep = whoosh(dur, lo=lo, hi=hi, end=lo * 0.8, peak=0.3, q=p["q"], body=0.85, top=0.2,
                   flutter=0.35, rise=1.5, fall=2.0, name=name + "a")
    tl, th = p["tone"]
    howl = svf(pink(dur, name + "h"), glide([(0.0, tl), (0.4, th), (1.0, tl * 0.85)], dur), q=5.0, mode="bp")
    howl = norm(howl * arc(dur, 0.32, 1.4, 1.8) * nlfo(dur, 11.0, 0.6, 1.0, name + "hl")) * p["body"]
    if voice == "flame":
        sweep = mixdown(sweep * 0.7, fire(dur, low=110, roar=0.8, crackles=14, name=name + "f") * 0.6)
    if voice == "water":
        sweep = mixdown(sweep * 0.6, water(dur, size=1.4, bubbles=7, foam=0.6, name=name + "w") * 0.7)
    if voice == "acid":
        sweep = mixdown(sweep * 0.75, gas(dur, band=(800, 6000), fizz=1.0, name=name + "g") * 0.5)
    out = saturate(mixdown(sweep, howl), p["drive"])
    return reverb(out, decay=p["tail"][0], damp=5400, mix=p["tail"][1], name="wv", predelay=0.005)


# ═══════════════════════════ guns, chains, light, lightning ═══════════════════════════

GUN_VOICES = {
    #            crack  body_hi bodydec weight mech  smoke slap tail        size
    "rifle":   dict(crk=1.0, hi=6500, dec=11.0, sub=.40, mech=.28, smk=.10, slap=.22, tail=(.50, .20), size=1.0),
    "heavy":   dict(crk=1.1, hi=5200, dec=8.0,  sub=.60, mech=.34, smk=.12, slap=.26, tail=(.70, .24), size=1.3),
    "light":   dict(crk=.85, hi=8000, dec=14.0, sub=.26, mech=.30, smk=.06, slap=.16, tail=(.38, .17), size=0.8),
    "flint":   dict(crk=.75, hi=3600, dec=6.5,  sub=.50, mech=.45, smk=.60, slap=.20, tail=(.85, .27), size=1.3),
    "cannon":  dict(crk=.60, hi=2400, dec=4.0,  sub=.75, mech=.15, smk=.75, slap=.30, tail=(1.1, .30), size=1.6),
}


def gun(voice, dur=0.34, name="gun"):
    """A firearm. The crack is 15ms of it; everything else is the muzzle blast expanding, the
    action cycling, the report coming back off whatever is out there, and the room."""
    p = GUN_VOICES[voice]
    crack_ = saturate(click(0.014, fc=1600, name=name + "c", shape=1.1), 5.0) * p["crk"]
    body = svf(noise(0.2, name + "b"), glide([(0.0, p["hi"]), (1.0, 460)], 0.2), q=2.4)
    body *= exp_env(0.2, attack=0.0008, decay=p["dec"])
    # the action cycling: a couple of dry clicks, not a tuned plate
    mech = mixdown(bandpass(noise(0.02, name + "m1"), 1800, 9000) * exp_env(0.02, decay=30.0) * 0.7,
                   bandpass(noise(0.035, name + "m2"), 700, 4000) * exp_env(0.035, decay=22.0) * 0.5)
    smoke = gas(0.3, band=(500, 5000), fizz=0.5, name=name + "s") * p["smk"]
    out = np.zeros(n_of(dur))
    out[:len(body)] += body * 0.8
    at(out, 0.0, weight(92.0, 0.13, name=name + "w") * p["sub"])
    out[:len(crack_)] += crack_
    at(out, 0.045, mech * p["mech"])
    at(out, 0.01, smoke)
    # the report coming back: the blast again, duller and a tenth of a second late. It is what
    # says the shot was fired out of doors, and without it a gun is a cap pistol
    back = lowpass(body, 1800) * p["slap"]
    at(out, 0.085 * p["size"], back)
    return reverb(saturate(out, 1.9), decay=p["tail"][0], damp=3600, mix=p["tail"][1],
                  name="gun", predelay=0.008, size=p["size"])


def chain(voice, dur=0.42, name="ch"):
    """Something long thrown to catch someone and haul them in.

    iron   a chain: the whoosh of the throw, then dozens of links clinking — each a few
           milliseconds, each its own pitch — and the heavy catch of the shackle at the end
    rope   a rope: a lasso's whirl, fibres rustling, and the creak and snap of it pulling taut
    string a puppeteer's string: plucked, and plucked again tighter as it bites
    """
    n = n_of(dur)
    out = np.zeros(n + n_of(0.16))
    if voice == "iron":
        throw = whoosh(dur, lo=450, hi=2600, end=500, peak=0.25, q=1.6, body=0.7, top=0.1, flutter=0.25,
                       name=name + "a")
        links = rattle(dur * 0.8, count=16, band=(1400, 5200), q=4.5, cluster=0.7, name=name + "l")
        links *= seg_env([(0.0, 1.0), (0.5, 0.75), (1.0, 0.25)], dur * 0.8)
        catch = mixdown(thud(110, 0.14, drive=1.6, name=name + "t"),
                        rattle(0.09, count=4, band=(1400, 5000), q=4.5, cluster=1.0, name=name + "c") * 0.6)
        at(out, 0.0, throw * 0.55)
        at(out, 0.0, weight(118.0, 0.14, name=name + "h") * 0.3)
        at(out, 0.02, links * 0.6)
        at(out, dur * 0.62, norm(catch) * 0.6)
        tail = (0.38, 0.16)
    elif voice == "rope":
        whirl = whoosh(dur, lo=300, hi=1800, end=350, peak=0.3, q=1.4, body=0.8, top=0.05, flutter=0.2,
                       name=name + "a")
        whirl *= 0.55 + 0.45 * np.sin(2 * np.pi * np.cumsum(np.full(n, 5.5) * nlfo(dur, 2.0, 0.9, 1.1, name + "wl")) / SR)
        rustle = grains(dur, rate=110, band=(700, 3800), length=(0.001, 0.004), level=(0.1, 0.5),
                        density=[(0.0, 1.0), (0.6, 0.6), (1.0, 0.1)], name=name + "r")
        taut = mixdown(crack(0.02, name=name + "k") * 0.5, thud(130, 0.12, drive=1.5, name=name + "t") * 0.6,
                       pad_to(creak(0.13, rate=(70.0, 150.0), body=(480.0, 1050.0), q=4.0, name=name + "c") * 0.5, 0.12))
        at(out, 0.0, norm(whirl) * 0.6)
        at(out, 0.0, rustle * 0.35)
        at(out, dur * 0.58, norm(taut) * 0.65)
        tail = (0.28, 0.13)
    else:
        whip = whoosh(dur, lo=1400, hi=6000, end=1600, peak=0.15, q=1.6, body=0.2, top=0.3, flutter=0.1,
                      name=name + "a")
        first = pluck(N["E4"], dur, t60=0.3, bright=0.8, pick=0.15, name=name + "p1")
        tight = pluck(N["B4"], dur * 0.7, t60=0.22, bright=0.9, pick=0.12, name=name + "p2")
        at(out, 0.0, whip * 0.4)
        at(out, 0.0, first * 0.55)
        at(out, dur * 0.3, tight * 0.4)
        tail = (0.32, 0.15)
    return reverb(saturate(out, 1.8), decay=tail[0], damp=6200, mix=tail[1], name="ch", predelay=0.004)


def radiance(dur, notes=None, name="rad"):
    """Holy light: voices swelling on an open fifth, the air itself singing the same chord
    above them, glitter over it and a warm push of air. Sung, never struck — every holy ability
    here used to be a bell, and a cleric firing twice a second was a steeple full of tin cans."""
    notes = notes or (N["A3"], N["E4"], N["A4"])
    ch = choir(notes, dur, vowels=((0.0, "ah"), (1.0, "oh")), name=name + "c")
    ch *= seg_env([(0.0, 0.0), (0.22, 1.0), (0.6, 0.7), (1.0, 0.0)], dur)
    glory = halo(tuple(f * 2 for f in notes), dur, q=36, peak=0.3, breath=0.25, name=name + "h")
    shim = shimmer(dur, 3500, 11000, rate=130, density=[(0.0, 0.4), (0.25, 1.0), (1.0, 0.15)], name=name + "s")
    breeze = flange(whoosh(dur, lo=700, hi=4200, end=800, peak=0.2, q=1.4, body=0.5, top=0.3, flutter=0.2,
                           name=name + "a"), rise=False)
    return mixdown(ch * 0.24, glory * 0.26, shim * 0.4, norm(breeze) * 0.55)


def elec(dur=0.5, size=1.0, colour=(600, 9000), forks=1, name="el"):
    """Lightning. The arc is a resonance jumping at audio-adjacent rates; the crack is the air
    column collapsing; the rumble is the distance; and the sparks are what is left in the air."""
    arc_ = zap_arc(dur, colour[0], colour[1], q=9, steps=int(90 * size), name=name)
    arc_ *= exp_env(dur, attack=0.001, decay=6.0 / size)
    crack_ = saturate(click(0.028, fc=1400, name=name + "c", shape=1.3), 4.0)
    rum = bandpass(pink(dur, name + "r"), 70, 420) * exp_env(dur, attack=0.012, decay=3.0) * 0.45
    rum *= nlfo(dur, 14.0, 0.5, 1.0, name + "rl")
    sparks = grains(dur, rate=120, band=(2500, 10500), length=(0.0002, 0.001),
                    density=[(0.0, 1.0), (0.3, 0.5), (1.0, 0.1)], name=name + "sp")
    out = mixdown(arc_ * 0.8, rum * size, sparks * 0.22)
    out[:len(crack_)] += crack_ * size
    at(out, 0.0, weight(84.0, min(dur, 0.2), name=name + "w") * 0.3 * size)
    rng = np.random.default_rng(seed_of(name + "fk"))
    for i in range(forks - 1):
        t0 = rng.uniform(0.05, dur * 0.55)
        fd = rng.uniform(0.1, 0.22)
        f = zap_arc(fd, colour[0] * 1.4, colour[1], q=10, steps=30, name=name + "f" + str(i))
        at(out, t0, f * exp_env(fd, decay=9.0) * rng.uniform(0.3, 0.6))
    return reverb(saturate(out, 2.1), decay=0.85 * size, damp=3800, mix=0.26, name="el",
                  predelay=0.01, size=1.2)


def machine(dur=0.45, servo=(300, 900), clank=1.0, whine=0.4, name="mch"):
    """Servos and mechanism — the sound of something built rather than grown: a motor's whirr
    running up and back (its gears a rattle that keeps time with it), a thin whine over it, and a
    joint locking. The whirr is noise rung in a resonance that follows the motor's speed; it was a
    bare sawtooth sweeping up and down, which is a synth doing an impression of one."""
    n = n_of(dur)
    speed = glide([(0.0, servo[0]), (0.5, servo[1]), (1.0, servo[0] * 0.8)], dur)
    env = ad_env(dur, attack=0.01, hold=0.4, curve=1.3)
    whirr = comb(bandpass(pink(dur, name + "w"), 200, 5000), speed, fb=0.86, damp=0.4)
    gears = pulses(speed * 0.25, dur, jitter=0.02, shimmer_=0.2, sub=0.2, name=name + "g")
    gears = bandpass(np.convolve(gears, np.hanning(24))[:n], 700, 5000)
    wh = svf(noise(dur, name + "h"), speed * 4.0, q=3.0, mode="bp") * swell_env(dur, 2.0) * whine
    # a joint locking: a dull thunk and the rattle of a mechanism, not a plate being struck
    ck = mixdown(thud(150, 0.16, drive=1.6, name=name + "k2") * 0.7,
                 pad_to(grains(0.06, rate=180, band=(1200, 6000), length=(0.001, 0.004),
                               density=[(0.0, 1.0), (1.0, 0.0)], name=name + "k1") * 0.45, 0.16),
                 pad_to(click(0.004, fc=2200, name=name + "k3") * 0.5, 0.16))
    out = mixdown(norm(whirr) * env * 0.4, norm(gears) * env * 0.22, norm(wh) * 0.2)
    at(out, dur * 0.25, ck * clank)
    return reverb(saturate(out, 2.0), decay=0.45, damp=5000, mix=0.2, name="mch", predelay=0.005)


# ═══════════════════════════ blast ═══════════════════════════

def explosion(dur=1.1, size=1.0, name="boom"):
    """A detonation: the crack, the blast (noise that starts over the whole band and closes
    down as it spreads), the punch of it in the chest, the boom in the low mids that is the part
    a small speaker plays, what it throws about, and the rumble dying off."""
    n = n_of(dur)
    blast = svf(pink(dur, name + "b"), glide([(0.0, 8000), (0.08, 2400), (0.45, 700), (1.0, 220)], dur), q=1.6)
    blast *= exp_env(dur, attack=0.002, decay=3.6)
    crack_ = saturate(click(0.035, fc=1800, name=name + "c", shape=1.4), 3.2)
    punch = mixdown(thud(74.0 / size ** 0.5, dur * 0.45, drive=2.4, name=name + "t"),
                    pad_to(weight(62.0 / size ** 0.5, dur * 0.4, name=name + "w") * 0.8, dur * 0.45))
    boom = saturate(bandpass(pink(dur, name + "m"), 110, 620), 2.6) * exp_env(dur, attack=0.003, decay=5.0)
    rum = bandpass(pink(dur, name + "r"), 45, 220) * exp_env(dur, attack=0.02, decay=2.6)
    rum *= nlfo(dur, 11.0, 0.5, 1.0, name + "rl")
    shrapnel = debris(dur, count=22, band=(350, 4500), spread=(0.05, 0.78), name=name + "d")
    out = np.zeros(n)
    at(out, 0.0, norm(punch) * 0.8)
    out += blast * 0.75 + norm(boom) * 0.5 + norm(rum) * 0.3
    out[:len(crack_)] += crack_ * 0.85
    out += shrapnel * 0.5
    out = saturate(out, 1.8)
    return reverb(out, decay=1.3 * size, damp=3200, mix=0.32, name="boom", predelay=0.014, size=1.5)


# ═══════════════════════════ what a creature adds ═══════════════════════════

def wings(dur=0.4, rate=11.0, gain=0.35, band=(260, 2600), name="wg"):
    """Wingbeats: each one a soft whump of air with the rustle of feathers on it, a little
    uneven, as flapping is. (They were noise switched on and off by a square wave, which is a
    helicopter.)"""
    n = n_of(dur)
    out = np.zeros(n)
    rng = np.random.default_rng(seed_of(name))
    t = rng.uniform(0.0, 0.3) / rate
    while t < dur * 0.92:
        bd = min(0.9 / rate, dur - t)
        if bd < 0.02:
            break
        beat = whoosh(bd, lo=band[0], hi=band[1] * rng.uniform(0.7, 1.0), end=band[0] * 0.8, peak=0.3, q=1.2,
                      body=0.9, top=0.15, flutter=0.3, rise=1.4, fall=2.0, name=name + str(int(t * 1000)))
        at(out, t, beat * rng.uniform(0.7, 1.0))
        t += rng.uniform(0.88, 1.12) / rate
    return norm(out) * swell_env(dur, 0.6) * gain


def chitin(dur=0.22, band=(1400, 7000), gain=0.3, name="sc"):
    """Chitin on chitin — dry, granular, no ring."""
    return debris(dur, count=16, band=band, decay=(30.0, 70.0), spread=(0.0, 0.7), name=name) * gain
