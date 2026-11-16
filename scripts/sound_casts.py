#!/usr/bin/env python3
"""What an ability that throws nothing sounds like: every character's own.

A third of the roster's abilities fire no projectile — a dash, a blink, a phase, a barrier, a
trap — and each kind used to make one sound for everybody: the Wizard's Blink, the Barbarian's
Leap, the Hawk's Dive Bomb, the Spider's Burrow and the Phoenix's Rebirth were all the same
cartoon zip-and-pop. They are the same move to the rules and nothing alike to the player, so each
has its own here, built from what its character is. `AbilitySounds.scala` says which character
hears which (and falls back to the plain `dash`, `teleport`, `phase_shift`, `barrier_up` and
`trap_place` at the foot of this file for a cast it has no row for).

Four shapes, as there are four kinds of cast:

    dash     a push-off, the rush of going, and whatever is doing the going
    blink    leaving (the air closes on where you were), and arriving (it opens where you are):
             two short events with almost nothing between, because the move itself takes no time
    phase    becoming something else for a while: a change that swells in and settles
    barrier  something raised and set: it goes up, and it locks
"""
import numpy as np

from sound_engines import *  # noqa: F401,F403
from sound_attacks import (snarl, snake_hiss, leaves, hooves, three_throats, griffin_cry, crockery, jingle,
                           glass_clink, lamp_smoke, glitch, coil_hum, moans, wood_clack)

DASH_M = dict(width=0.8, transient=1.3, pan=(-0.6, 0.6), tail=(0.4, 0.14))
BLINK_M = dict(width=0.9, transient=1.2, tail=(0.7, 0.2))
PHASE_M = dict(width=1.0, transient=0.6, tail=(1.1, 0.26))
WALL_M = dict(width=0.85, transient=1.1, tail=(0.7, 0.2))
TRAP_M = dict(width=0.6, transient=1.4, comp=(0.26, 3.0), tail=(0.35, 0.12))


# ═══════════════════════════ dashes ═══════════════════════════

def push_off(kind, name):
    """How a dash begins: what is pushed against, and with what."""
    if kind == "boot":
        return mixdown(strike("earth", 130, 0.12, hardness=0.8, damp=1.4, name=name) * 0.7,
                       grains(0.06, rate=180, band=(300, 2400), length=(0.002, 0.006), name=name + "g") * 0.4)
    if kind == "claws":
        return mixdown(rip(0.08, rate=(220.0, 90.0), band=(500, 4000), name=name),
                       pad_to(thud(120, 0.1, drive=1.6, name=name + "t") * 0.5, 0.1))
    if kind == "pads":
        return mixdown(thud(105, 0.1, drive=1.5, name=name) * 0.7, snap("soft", name + "s") * 0.6)
    if kind == "heavy":
        return mixdown(thud(88, 0.18, drive=2.0, name=name), grains(0.1, rate=160, band=(200, 1600), length=(0.003, 0.009), name=name + "g") * 0.5)
    if kind == "cloth":
        return bandpass(pink(0.1, name), 700, 5200) * arc(0.1, 0.2, 1.0, 1.6)
    if kind == "wing":
        return wings(0.16, 7.0, 1.0, (180, 2200), name)
    raise ValueError(kind)


def dash(dur=0.36, start="boot", band=(400, 3200), body=0.7, top=0.2, peak=0.35, heft=0.3, name="ds"):
    """A dash: the push-off, and the rush of air as the body goes — a pass-by, loudest in the
    middle of the move."""
    lo, hi = band
    rush = whoosh(dur, lo=lo, hi=hi, end=lo * 0.8, peak=peak, q=1.5, body=body, top=top, flutter=0.25,
                  rise=1.5, fall=1.8, name=name + "a")
    out = lay(dur, (0.0, rush, 0.8), (0.0, weight(96.0, 0.14, name=name + "w"), heft))
    if start is not None:
        at(out, 0.0, norm(push_off(start, name + "p")) * 0.55)
    return out


def room(x, decay=0.42, damp=5200, mix=0.22, name="ds", drive=1.7):
    return reverb(saturate(x, drive), decay=decay, damp=damp, mix=mix, name=name, predelay=0.006)


def armour(dur=0.3, count=10, name="arm"):
    """Plate moving with the body: a few dull knocks, low and soft. (It had mail jingling over
    it: a charge sounded like a drawer of spoons.)"""
    return debris(dur, count=max(3, count // 2), band=(700, 3600), decay=(22.0, 40.0), name=name) * 0.6


def gallop(dur, beats=4, f=118.0, name="glp"):
    """Hooves at the gallop: da-da-dum, da-da-dum."""
    out = np.zeros(n_of(dur))
    pattern = (0.0, 0.09, 0.2, 0.31, 0.4, 0.51)[:beats]
    for i, t0 in enumerate(pattern):
        if t0 < dur - 0.08:
            at(out, t0, hooves(1, name=name + str(i)) * (0.8 + 0.2 * ((i + 1) % 2)) * (f / 118.0) ** 0)
    return out


# — the plain one —
sound("dash", lambda: room(with_(dash(0.34, "boot", name="dsh"),
                                 pad_to(push_off("cloth", "dshc") * 0.4, 0.2))), 0.58, "melee", DASH_M)

# — winged —
sound("cast_swoop", lambda: room(with_(dash(0.4, "wing", band=(500, 3800), peak=0.4, heft=0.2, name="swp"),
                                       wings(0.36, 9.0, 0.5, (200, 2400), "swpw"),
                                       pad_to(screech(0.28, f0=(1900.0, 2800.0, 1700.0), rough=0.5, breath_=0.5, name="swpv") * 0.2, 0.4))),
      0.62, "melee", DASH_M)
sound("cast_sky_dive", lambda: room(with_(dash(0.46, "wing", band=(350, 3000), body=0.9, peak=0.55, heft=0.4, name="skd"),
                                          wings(0.3, 7.0, 0.55, (160, 2000), "skdw"),
                                          pad_to(griffin_cry(0.34, "skdv") * 0.3, 0.46),
                                          lay(0.46, (0.34, thud(100, 0.12, drive=1.8, name="skdl"), 0.5)))),
      0.66, "melee", dict(DASH_M, sub=(52, 0.22)))
sound("cast_aerial_charge", lambda: room(with_(dash(0.4, "wing", band=(500, 3600), peak=0.4, heft=0.25, name="aer"),
                                               wings(0.36, 8.0, 0.5, (200, 2400), "aerw"),
                                               pad_to(armour(0.3, 8, "aera") * 0.3, 0.4))),
      0.62, "melee", DASH_M)

# — blades —
def gen_iaijutsu():
    """Iaijutsu: the draw and the cut are one move — the sword sings out of the scabbard as he
    goes (one long clean stroke of it, climbing), the air parts thin and fast, and the blade is
    home again with a soft knock before anyone has seen it."""
    dur = 0.42
    draw = steel_song(0.32, f=(4300.0, 5900.0, 7800.0), rise=1.1, q=55.0, peak=0.18, name="iad")
    go = whoosh(0.3, lo=1200, hi=6000, end=1400, peak=0.3, q=1.8, body=0.4, top=0.5, flutter=0.0, rise=1.4, fall=2.4, name="iag")
    # the blade home again: the guard meeting the scabbard's mouth is wood on wood, a soft low
    # knock. As a bright click it was the clink at the end of every dash
    home = mixdown(thud(260, 0.05, drive=1.2, name="iah") * 0.6, snap("soft", "iahc") * 0.5)
    out = lay(dur, (0.0, draw, 0.42), (0.02, go, 0.8), (0.0, norm(push_off("cloth", "iac")), 0.3),
              (0.34, norm(home), 0.12))
    return room(out, mix=0.2, drive=1.2)


sound("cast_iaijutsu", gen_iaijutsu, 0.60, "melee", dict(DASH_M, **BRIGHT))


def gen_death_dash():
    """The Reaper's Death Dash: no footfall at all — cold air where he was, the scythe trailing,
    and the souls he passes through sighing."""
    dur = 0.5
    go = dash(dur, None, band=(300, 2200), body=0.9, top=0.05, peak=0.4, heft=0.2, name="ddh")
    cold = halo((N["A2"], N["E3"], N["A3"]), dur, q=22, peak=0.4, breath=0.4, name="ddhc")
    sighs = voices(0.4, [(0.0, 300.0), (1.0, 520.0)], [(0.0, "oo"), (1.0, "ee")], count=3, spread=0.1, stagger=0.05,
                   breath_=0.8, open_q=0.4, amp_keys=((0.0, 0.0), (0.3, 0.8), (1.0, 0.0)), name="ddhv")
    blade = scrape(0.2, lo=2000, hi=4600, bands=2, q=4.0, name="ddhs") * arc(0.2, 0.4, 1.4, 1.6)
    return room(with_(go, cold * 0.3, pad_to(sighs * 0.18, dur), lay(dur, (0.1, norm(blade), 0.16))), decay=0.9, damp=3800, mix=0.28)


sound("cast_death_dash", gen_death_dash, 0.62, "ability", dict(DASH_M, tail=(0.9, 0.22)))

# — armour —
sound("cast_rage_charge", lambda: room(with_(dash(0.42, "heavy", band=(350, 2800), body=0.9, heft=0.45, name="rgc"),
                                             lay(0.42, (0.14, hooves(1, name="rgc1"), 0.5), (0.27, hooves(1, name="rgc2"), 0.5)),
                                             pad_to(roar(0.36, f0=(150.0, 190.0, 130.0), vowels=("ah", "ah", "aw"), size=0.95,
                                                         rough=0.7, breath_=0.6, name="rgcv") * 0.3, 0.42))),
      0.66, "melee", dict(DASH_M, sub=(52, 0.22)))
sound("cast_unholy_charge", lambda: room(with_(dash(0.42, "heavy", band=(300, 2400), body=0.9, heft=0.45, name="unh"),
                                               pad_to(armour(0.36, 12, "unha") * 0.4, 0.42),
                                               halo((N["A2"], N["E3"]), 0.42, q=20, peak=0.35, breath=0.4, name="unhd") * 0.3,
                                               breath(0.4, [(0.0, "ee"), (1.0, "uh")], name="unhw") * arc(0.4, 0.3, 1.0, 1.4) * 0.2),
                                         decay=0.7, damp=3600, mix=0.24),
      0.66, "melee", dict(DASH_M, sub=(50, 0.22), tail=(0.7, 0.2)))
sound("cast_spectral_charge", lambda: room(with_(dash(0.42, None, band=(350, 2600), body=0.8, heft=0.25, name="spc"),
                                                 pad_to(armour(0.36, 10, "spca") * 0.35, 0.42),
                                                 breath(0.42, [(0.0, "oo"), (0.5, "uh"), (1.0, "oo")], band=(220, 4200), name="spcb") *
                                                 arc(0.42, 0.35, 1.2, 1.4) * 0.4),
                                           decay=0.8, damp=4000, mix=0.26),
      0.62, "melee", dict(DASH_M, tail=(0.8, 0.22)))
sound("cast_headless_charge", lambda: room(with_(dash(0.5, None, band=(300, 2400), body=0.9, heft=0.3, name="hdl"),
                                                 gallop(0.5, 5, name="hdlg") * 0.6, pad_to(armour(0.4, 9, "hdla") * 0.3, 0.5),
                                                 breath(0.5, [(0.0, "oo"), (1.0, "uh")], band=(220, 3600), name="hdlb") *
                                                 arc(0.5, 0.4, 1.2, 1.4) * 0.3),
                                           decay=0.8, damp=3800, mix=0.26),
      0.66, "ability", dict(DASH_M, sub=(52, 0.2), tail=(0.8, 0.22)))
sound("cast_ground_charge", lambda: room(with_(dash(0.45, "heavy", band=(260, 2000), body=1.0, top=0.05, heft=0.5, name="gch"),
                                               lay(0.45, (0.13, thud(92, 0.14, drive=2.0, name="gch1"), 0.6),
                                                   (0.26, thud(88, 0.14, drive=2.0, name="gch2"), 0.6)),
                                               debris(0.45, count=10, band=(250, 2200), spread=(0.05, 0.8), name="gchd") * 0.3),
                                         decay=0.6, damp=3200, mix=0.24),
      0.70, "ability", dict(DASH_M, sub=(48, 0.28)))
sound("cast_magma_charge", lambda: room(with_(dash(0.45, "heavy", band=(300, 2400), body=0.9, heft=0.4, name="mgc"),
                                              pad_to(armour(0.36, 8, "mgca") * 0.25, 0.45),
                                              fire(0.45, low=110, roar=0.9, crackles=16, name="mgcf") * arc(0.45, 0.3, 1.0, 1.2) * 0.55,
                                              sizzle(0.45, band=(2200, 7500), crackle=0.6, name="mgcz") * arc(0.45, 0.4, 1.0, 1.3) * 0.16)),
      0.68, "ability", dict(DASH_M, sub=(50, 0.25)))

# — monks and darts —
sound("cast_palm_strike", lambda: room(lay(0.36, (0.0, dash(0.26, "pads", band=(600, 4200), body=0.5, top=0.3, peak=0.4, heft=0.15, name="plm"), 1.0),
                                           (0.0, norm(push_off("cloth", "plmc")), 0.4),
                                           (0.2, mixdown(strike("flesh", 150, 0.14, hardness=1.0, name="plmh"), snap("soft", "plms") * 0.6), 0.55),
                                           (0.2, weight(100.0, 0.14, name="plmw"), 0.35)), mix=0.18),
      0.62, "melee", DASH_M)

# — machines —
def jets(dur, size=1.0, name="jet"):
    """A jet lit and burning: the pop of ignition, the tearing roar of the burn, the heat of
    it hissing."""
    burn = whoosh(dur, lo=500 / size, hi=2600 / size, end=700 / size, peak=0.2, q=1.3, body=0.9, top=0.3, flutter=0.0,
                  rise=1.0, fall=1.2, name=name + "b") * nlfo(dur, 50.0, 0.5, 1.3, name + "t")
    return lay(dur, (0.0, snap("hard", name + "i"), 0.5), (0.0, whump(0.14, f=(220.0 / size, 90.0), name=name + "w"), 0.6),
               (0.0, norm(burn), 0.9))


sound("cast_jump_jets", lambda: room(with_(jets(0.6, size=1.3, name="jmp"),
                                           pad_to(machine(0.3, servo=(180, 600), clank=0.7, whine=0.3, name="jmps") * 0.4, 0.6),
                                           lay(0.6, (0.46, thud(84, 0.14, drive=2.0, name="jmpl"), 0.5))), decay=0.6, damp=4200),
      0.74, "ability", dict(DASH_M, sub=(48, 0.28)), held=True)
sound("cast_jet_dash", lambda: room(with_(jets(0.36, size=0.8, name="jtd"),
                                          pad_to(norm(svf(noise(0.3, "jtdw"), glide([(0.0, 2400.0), (1.0, 5200.0)], 0.3), q=4.0, mode="bp") *
                                                      arc(0.3, 0.3, 1.0, 1.6)) * 0.14, 0.36))),
      0.62, "melee", DASH_M)

# — beasts —
sound("cast_ghoul_lunge", lambda: room(with_(dash(0.34, "claws", band=(400, 3000), heft=0.25, name="glu"),
                                             pad_to(snarl(160.0, 0.28, rough=0.95, size=0.9, name="gluv") * 0.4, 0.34))),
      0.62, "melee", DASH_M)
sound("cast_savage_leap", lambda: room(with_(dash(0.42, "claws", band=(300, 2600), body=0.9, peak=0.45, heft=0.35, name="svl"),
                                             pad_to(roar(0.38, f0=(74.0, 100.0, 62.0), size=1.5, rough=0.9, name="svlv") * 0.45, 0.42))),
      0.68, "melee", dict(DASH_M, sub=(50, 0.22)))
sound("cast_pounce", lambda: room(with_(dash(0.4, "pads", band=(350, 2800), body=0.8, peak=0.45, heft=0.3, name="pnc"),
                                        pad_to(snarl(110.0, 0.3, rough=0.6, size=1.3, name="pncv") * 0.3, 0.4),
                                        lay(0.4, (0.3, push_off("pads", "pncl"), 0.4)))),
      0.62, "melee", DASH_M)
sound("cast_lion_charge", lambda: room(with_(dash(0.46, "pads", band=(300, 2600), body=0.9, heft=0.35, name="lch"),
                                             lay(0.46, (0.12, push_off("pads", "lch1"), 0.45), (0.25, push_off("pads", "lch2"), 0.45)),
                                             pad_to(roar(0.44, f0=(96.0, 130.0, 80.0), size=1.35, rough=0.8, breath_=0.5, peak=0.25, name="lchv") * 0.45, 0.46))),
      0.70, "ability", dict(DASH_M, sub=(50, 0.22)))
sound("cast_beast_charge", lambda: room(with_(dash(0.44, "claws", band=(320, 2600), body=0.9, heft=0.35, name="bch"),
                                              lay(0.44, (0.0, squelch(0.12, f=(700, 1500), q=2.6, lumps=2, name="bchq"), 0.25),
                                                  (0.13, push_off("pads", "bch1"), 0.4), (0.26, push_off("pads", "bch2"), 0.4)),
                                              pad_to(snarl(118.0, 0.36, rough=0.8, size=1.2, name="bchv") * 0.4, 0.44))),
      0.66, "melee", dict(DASH_M, sub=(50, 0.22)))
sound("cast_hellfire_pounce", lambda: room(with_(dash(0.42, "claws", band=(300, 2600), body=0.9, heft=0.35, name="hfp"),
                                                 pad_to(three_throats(0.3, "hfpv") * 0.4, 0.42),
                                                 lay(0.42, (0.24, whump(0.18, f=(180.0, 64.0), name="hfpw"), 0.6),
                                                     (0.24, fire(0.18, low=110, roar=0.9, crackles=8, name="hfpf"), 0.5)))),
      0.70, "ability", dict(DASH_M, sub=(50, 0.25)))
sound("cast_bull_charge", lambda: room(with_(dash(0.55, "heavy", band=(260, 2200), body=1.0, top=0.05, heft=0.5, name="bul"),
                                             gallop(0.55, 6, name="bulg") * 0.7,
                                             pad_to(roar(0.5, f0=(88.0, 128.0, 76.0), vowels=("aw", "ah", "aw"), size=1.45, rough=0.75,
                                                         breath_=0.5, peak=0.25, name="bulv") * 0.45, 0.55)),
                                       decay=0.6, damp=3200, mix=0.24),
      0.74, "ability", dict(DASH_M, sub=(48, 0.28)), held=True)
sound("cast_gallop", lambda: room(with_(dash(0.5, None, band=(350, 2600), body=0.8, heft=0.2, name="gal"), gallop(0.5, 5, name="galg") * 0.8)),
      0.64, "melee", dict(DASH_M, sub=(54, 0.2)))


def gen_slither():
    """The Serpent slithering aside: scales over the ground — a dry, sliding rasp — and a hiss."""
    dur = 0.36
    rasp = grains(dur, rate=700, band=(900, 6000), length=(0.0003, 0.0016), density=[(0.0, 0.5), (0.3, 1.0), (1.0, 0.2)], name="sltg")
    slide = whoosh(dur, lo=500, hi=2600, end=600, peak=0.35, q=1.4, body=0.5, top=0.2, flutter=0.4, name="slta")
    return room(mixdown(rasp * 0.45, slide * 0.7, snake_hiss(0.3, "slth") * 0.3), mix=0.18)


sound("cast_slither", gen_slither, 0.56, "melee", DASH_M)

# — wind —
sound("cast_tailwind", lambda: room(with_(dash(0.4, None, band=(400, 3600), body=0.7, top=0.4, peak=0.4, heft=0.1, name="twd"),
                                          norm(svf(pink(0.4, "twdh"), glide([(0.0, 500.0), (0.4, 1200.0), (1.0, 600.0)], 0.4), q=5.0, mode="bp") *
                                               arc(0.4, 0.4, 1.4, 1.8)) * 0.25), mix=0.2),
      0.58, "melee", dict(DASH_M, width=0.95))


# ═══════════════════════════ blinks ═══════════════════════════

def vanish(dur=0.14, lo=400.0, hi=4200.0, name="van"):
    """Leaving: the air closing on where you were — a short rush that climbs and is cut off."""
    v = svf(pink(dur, name), glide([(0.0, lo), (1.0, hi)], dur), q=2.2, mode="bp") * swell_in(dur, 2.2)
    return norm(v)


def arrive(dur=0.26, hi=5200.0, lo=500.0, pop="soft", heft=0.4, name="arr"):
    """Arriving: the air opening where you are — a soft knock, and a rush that falls away."""
    out_ = whoosh(dur, lo=hi, hi=hi * 1.1, end=lo, peak=0.04, q=1.4, body=0.6, top=0.3, flutter=0.15, rise=1.0, fall=2.2, name=name)
    return lay(dur, (0.0, snap(pop, name + "p"), 0.6), (0.0, out_, 0.8), (0.0, weight(104.0, 0.12, name=name + "w"), heft))


def blink(gap=0.16, name="blk", out_kw=None, in_kw=None):
    """A blink: gone, and there. The two halves butt up against each other — the move takes no
    time, so nor does the sound."""
    a = vanish(name=name + "o", **(out_kw or {}))
    b = arrive(name=name + "i", **(in_kw or {}))
    return lay(gap + len(b) / SR, (0.0, a, 0.6), (gap, b, 0.9))


def gen_blink():
    """The Wizard's Blink: the arcane way of it — the air snatched shut with a rising shiver
    of glitter, and opened again on a soft chord and falling dust."""
    base = blink(0.15, "wbk")
    dur = len(base) / SR
    up = shimmer(0.15, 3000, 10000, rate=180, grain=(0.004, 0.014), density=[(0.0, 0.2), (1.0, 1.0)], name="wbku") * swell_in(0.15, 1.5)
    chord = halo((N["E4"] * 2, N["E4"] * 3, N["E4"] * 4), 0.3, q=34, peak=0.2, breath=0.3, name="wbkh")
    dust = shimmer(0.34, 3600, 11500, rate=130, density=[(0.0, 1.0), (0.4, 0.5), (1.0, 0.05)], name="wbkd")
    out = with_(base, lay(dur, (0.0, up, 0.3), (0.15, chord, 0.4), (0.16, dust, 0.3)))
    return reverb(out, decay=0.7, damp=6500, mix=0.26, name="tp", predelay=0.008)


sound("cast_blink", gen_blink, 0.62, "ability", dict(BLINK_M, **BRIGHT))
sound("teleport", lambda: reverb(blink(0.15, "tlp"), decay=0.6, damp=6000, mix=0.24, name="tp", predelay=0.008),
      0.60, "ability", BLINK_M)


def gen_ench_blink():
    """The Enchantress's Blink: a harp run up and out, and a sigh where she lands."""
    base = blink(0.16, "ebk", in_kw=dict(heft=0.25))
    dur = len(base) / SR
    harp = np.zeros(n_of(dur))
    for i, m in enumerate((0, 3, 7, 12, 15)):
        at(harp, i * 0.026, pluck(N["E4"] * 2 ** (m / 12.0), 0.3, t60=0.4, bright=0.6, name="ebkp%d" % i) * (0.8 - 0.1 * i))
    sigh = halo((N["E5"], N["E5"] * 1.5), 0.3, q=40, peak=0.3, breath=0.3, name="ebkh")
    out = with_(base * 0.8, norm(harp) * 0.45, lay(dur, (0.16, sigh, 0.3),
                                                 (0.16, shimmer(0.3, 4000, 11000, rate=110, density=[(0.0, 1.0), (1.0, 0.1)], name="ebkd"), 0.26)))
    return reverb(out, decay=0.8, damp=6800, mix=0.28, name="tp", predelay=0.008)


sound("cast_ench_blink", gen_ench_blink, 0.60, "ability", dict(BLINK_M, **BRIGHT), held=True)
sound("cast_polter_blink", lambda: reverb(with_(blink(0.17, "pbk", out_kw=dict(lo=300.0, hi=2600.0), in_kw=dict(hi=3200.0, lo=350.0, heft=0.2)),
                                                breath(0.4, [(0.0, "oo"), (0.5, "uh"), (1.0, "oo")], band=(250, 3600), name="pbkb") *
                                                arc(0.4, 0.4, 1.0, 1.2) * 0.4,
                                                lay(0.4, (0.17, crockery(0.14, 3, "pbkc"), 0.22))),
                                          decay=0.9, damp=4600, mix=0.3, name="pbk", predelay=0.01),
      0.58, "ability", BLINK_M, held=True)


def gen_ice_blink():
    """Ice Blink: Frostbite breaks apart like a pane — and freezes back together, somewhere
    else, with a creak."""
    dur = 0.46
    shatter = grains(0.16, rate=420, band=(1800, 11000), length=(0.0004, 0.003), density=[(0.0, 1.0), (0.5, 0.4), (1.0, 0.1)], name="ibks")
    form = grains(0.2, rate=260, band=(2200, 10000), length=(0.0003, 0.002), density=[(0.0, 0.2), (0.6, 1.0), (1.0, 0.3)], name="ibkf")
    glass = halo((N["E6"], N["E6"] * 1.5, N["E6"] * 2), 0.24, q=80, attack=0.004, t60=0.2, breath=0.0, name="ibkg")
    out = lay(dur, (0.0, shatter, 0.42), (0.0, snap("hard", "ibkk"), 0.4), (0.18, form, 0.28), (0.2, glass, 0.26),
              (0.18, arrive(0.24, hi=6500.0, lo=1500.0, heft=0.25, name="ibka"), 0.6),
              (0.2, shimmer(0.26, 5000, 13000, rate=140, grain=(0.004, 0.012), bell=0.4, density=[(0.0, 1.0), (1.0, 0.1)], name="ibkd"), 0.28))
    return reverb(out, decay=0.6, damp=7000, mix=0.24, name="ibk", predelay=0.008)


sound("cast_ice_blink", gen_ice_blink, 0.62, "ability", dict(BLINK_M, **BRIGHT))


def gen_bog_hop():
    """Bog Hop: a frog's leap out of mud — the suck of the legs coming free, the air, and the
    splat where he comes down."""
    dur = 0.5
    out = lay(dur, (0.0, squelch(0.16, f=(500, 1500), q=3.0, lumps=3, name="bgo"), 0.7), (0.0, snap("pop", "bgp"), 0.5),
              (0.05, whoosh(0.26, lo=400, hi=2400, end=500, peak=0.5, q=1.5, body=0.7, top=0.1, name="bga"), 0.5),
              (0.3, mixdown(squelch(0.2, f=(1100, 200), q=3.0, lumps=4, name="bgl"), thud(110, 0.14, drive=1.6, name="bgt") * 0.6), 0.8),
              (0.3, water(0.18, size=1.6, bubbles=5, foam=0.5, name="bgw"), 0.3))
    return room(out, mix=0.2, name="bog")


sound("cast_bog_hop", gen_bog_hop, 0.62, "ability", dict(BLINK_M, width=0.7, tail=(0.4, 0.14)))


def shadow_step(name, smoke=0.0, fire_=0.0, cloth=0.0):
    """Stepping through shadow: a soft implosion — the dark closing — a held breath of nothing,
    and the dark opening again, lower."""
    base = blink(0.18, name, out_kw=dict(lo=1800.0, hi=300.0), in_kw=dict(hi=1800.0, lo=220.0, heft=0.45))
    dur = len(base) / SR
    hush = breath(dur, [(0.0, "ee"), (0.5, "uh"), (1.0, "oo")], name=name + "w") * arc(dur, 0.4, 1.0, 1.2)
    dark = halo((N["A2"], N["E3"]), dur, q=20, peak=0.5, breath=0.3, name=name + "d")
    out = with_(base, norm(hush) * 0.3, dark * 0.3)
    if smoke > 0:
        out = with_(out, lay(dur, (0.18, gas(0.3, band=(400, 4200), fizz=0.4, name=name + "s"), smoke)))
    if fire_ > 0:
        out = with_(out, lay(dur, (0.18, fire(0.26, low=100, roar=0.9, crackles=10, name=name + "f"), fire_)))
    if cloth > 0:
        out = with_(out, lay(dur, (0.0, norm(push_off("cloth", name + "c")), cloth), (0.19, norm(push_off("cloth", name + "c2")), cloth)))
    return reverb(saturate(out, 1.5), decay=0.9, damp=3000, mix=0.3, name="shs", predelay=0.01)


sound("cast_shadow_step", lambda: shadow_step("sst"), 0.62, "ability", BLINK_M)
sound("cast_rogue_step", lambda: shadow_step("rst", smoke=0.4, cloth=0.4), 0.60, "ability", BLINK_M)
sound("cast_warlock_step", lambda: shadow_step("wst", fire_=0.5), 0.64, "ability", BLINK_M)
sound("cast_phase_out", lambda: reverb(with_(blink(0.2, "pho", out_kw=dict(lo=300.0, hi=2400.0), in_kw=dict(hi=2800.0, lo=300.0, pop="soft", heft=0.2)),
                                             pad_to(voices(0.4, [(0.0, 260.0), (0.5, 330.0), (1.0, 230.0)], [(0.0, "oo"), (1.0, "uh")], count=3,
                                                           spread=0.04, breath_=0.85, open_q=0.4, name="phov") * 0.22, 0.5),
                                             halo((N["C4"], N["G4"]), 0.5, q=30, peak=0.5, breath=0.3, wander=0.3, name="phoh") * 0.26),
                                       decay=1.1, damp=4200, mix=0.32, name="pho", predelay=0.012),
      0.58, "ability", dict(PHASE_M, transient=0.9), held=True)
sound("cast_natures_leap", lambda: reverb(with_(blink(0.17, "ntl", out_kw=dict(lo=500.0, hi=3000.0), in_kw=dict(hi=3600.0, lo=500.0, heft=0.3)),
                                                lay(0.45, (0.0, leaves(0.2, 200, "ntl1"), 0.5), (0.17, leaves(0.28, 180, "ntl2"), 0.5),
                                                    (0.17, creak(0.16, rate=(50.0, 110.0), body=(300.0, 700.0), q=3.5, name="ntlc"), 0.3))),
                                          decay=0.5, damp=5200, mix=0.22, name="ntl", predelay=0.008),
      0.60, "ability", BLINK_M)


def gen_leap(heavy=1.0, name="lep", voice=None, dur=0.56):
    """A leap, not a spell: the push off the ground, the air, and the landing — everything the
    leaper weighs coming down at once."""
    out = lay(dur, (0.0, norm(push_off("heavy", name + "p")), 0.6),
              (0.03, whoosh(0.32, lo=300, hi=2400, end=450, peak=0.5, q=1.5, body=0.8, top=0.1, rise=1.4, fall=1.6, name=name + "a"), 0.6),
              (0.34, slam("earth", 0.24, name=name + "l"), 0.8 * heavy))
    if voice is not None:
        at(out, 0.02, voice)
    return saturate(out, 1.5)


sound("cast_leap", lambda: gen_leap(1.0, "lep", roar(0.3, f0=(150.0, 190.0, 130.0), vowels=("ah", "ah", "aw"), size=0.95, rough=0.7,
                                                     breath_=0.6, name="lepv") * 0.3),
      0.74, "ability", dict(BIG, sub=(48, 0.28), tail=(0.6, 0.18)))


def gen_dive_bomb():
    """The Hawk's Dive Bomb: wings folded, a stoop — the air screaming thinner and thinner —
    and the strike of the landing."""
    dur = 0.5
    stoop = svf(noise(0.3, "dvs"), glide([(0.0, 1400.0), (1.0, 5200.0)], 0.3), q=4.0, mode="bp") * swell_in(0.3, 1.8)
    out = lay(dur, (0.0, wings(0.14, 9.0, 1.0, (200, 2400), "dvw"), 0.5), (0.02, norm(stoop), 0.4),
              (0.02, whoosh(0.3, lo=500, hi=3600, end=1200, peak=0.85, q=1.5, body=0.6, top=0.3, rise=2.0, fall=1.0, name="dva"), 0.6),
              (0.3, mixdown(thud(110, 0.14, drive=1.8, name="dvl"), norm(push_off("claws", "dvc")) * 0.7), 0.7),
              (0.04, screech(0.26, f0=(2000.0, 3000.0, 1900.0), rough=0.55, name="dvv"), 0.2))
    return room(out, mix=0.2, name="dvb")


sound("cast_dive_bomb", gen_dive_bomb, 0.66, "ability", dict(DASH_M, **BRIGHT))


def gen_trick():
    """The Jester's Trick: a stage magician's exit — pop, a puff of smoke, the cap's bells —
    and he is over there, taking a bow."""
    dur = 0.46
    out = lay(dur, (0.0, snap("pop", "trk1"), 0.9), (0.0, gas(0.2, band=(500, 5000), fizz=0.6, name="trks"), 0.5),
              (0.2, snap("pop", "trk2"), 0.8),
              (0.2, gas(0.24, band=(500, 5000), fizz=0.6, name="trks2"), 0.45), (0.22, jingle(0.16, 3, "trkj2"), 0.22),
              (0.2, weight(110.0, 0.1, name="trkw"), 0.3))
    return room(out, mix=0.2, name="trk")


sound("cast_trick", gen_trick, 0.58, "ability", dict(BLINK_M, **BRIGHT))


def gen_system_hop(glitchy=False):
    """Through the network: taken apart into bits — a rasp of data running up and cut off —
    carried, and put together again at the other end with a little confirming blip of noise."""
    dur = 0.46
    up = comb(bitcrush(noise(0.16, "syu"), bits=3, hold=6), glide([(0.0, 400.0), (1.0, 3200.0)], 0.16), fb=0.86, damp=0.25) * swell_in(0.16, 1.4)
    down = comb(bitcrush(noise(0.2, "syd"), bits=3, hold=5), glide([(0.0, 3600.0), (1.0, 500.0)], 0.2), fb=0.86, damp=0.25)
    down = down * decay_env(0.2, attack=0.001)
    out = lay(dur, (0.0, norm(up), 0.6), (0.18, norm(down), 0.7), (0.18, snap("tick", "syk"), 0.5), (0.18, weight(110.0, 0.1, name="syw"), 0.3))
    if glitchy:
        out = with_(out, lay(dur, (0.0, glitch(0.14, "gbk1"), 0.5), (0.17, glitch(0.1, "gbk2"), 0.45), (0.3, glitch(0.06, "gbk3"), 0.3)))
    return reverb(saturate(out, 1.5), decay=0.35, damp=6500, mix=0.18, name="sys", predelay=0.004)


sound("cast_system_hop", gen_system_hop, 0.58, "ability", dict(BLINK_M, pingpong=(0.06, 0.3, 0.2), **BRIGHT))
sound("cast_glitch_blink", lambda: gen_system_hop(True), 0.60, "ability", dict(BLINK_M, pingpong=(0.045, 0.3, 0.22), **BRIGHT))


def gen_disperse():
    """Disperse: the swarm letting go of its shape — a buzz flying apart, thinning — and
    pulling itself together again elsewhere."""
    dur = 0.6
    apart = buzz(0.26, rate=220, band=(2000, 8000), bugs=6, name="dspa") * arc(0.26, 0.1, 1.0, 1.4)
    apart = fit(varispeed(apart, np.linspace(1.0, 1.5, len(apart))), len(apart))
    together = buzz(0.3, rate=200, band=(2000, 8000), bugs=6, name="dspt") * arc(0.3, 0.75, 1.6, 1.4)
    ticks = grains(dur, rate=220, band=(3500, 11000), length=(0.0002, 0.0007), density=[(0.0, 1.0), (0.45, 0.2), (0.8, 1.0), (1.0, 0.2)], name="dspk")
    out = lay(dur, (0.0, norm(apart), 0.6), (0.28, norm(together), 0.6), (0.0, ticks, 0.36), (0.5, snap("tick", "dsps"), 0.4))
    return reverb(out, decay=0.4, damp=6500, mix=0.2, name="dsp", predelay=0.006)


sound("cast_disperse", gen_disperse, 0.58, "ability", dict(BLINK_M, **BRIGHT), held=True)


def gen_void_step():
    """Void Step: the world pulled in after him — a suction, a deep drop — and let go again."""
    dur = 0.5
    out = lay(dur, (0.0, vanish(0.16, lo=300.0, hi=4200.0, name="vso"), 0.55),
              (0.15, mixdown(weight(72.0, 0.3, name="vsw"), whump(0.28, f=(180.0, 58.0), name="vsh") * 0.7), 0.8),
              (0.15, flange(whoosh(0.34, lo=1600, hi=1900, end=200, peak=0.05, q=1.4, body=0.7, top=0.0, flutter=0.4, rise=1.0, fall=1.4,
                                   name="vss"), 0.5, 6.5, rise=False), 0.5))
    return reverb(saturate(out, 1.6), decay=1.0, damp=2600, mix=0.3, name="void", predelay=0.012, size=1.4)


sound("cast_void_step", gen_void_step, 0.66, "ability", dict(BLINK_M, sub=(44, 0.28), pingpong=(0.12, 0.3, 0.16), tail=(1.0, 0.24)))


def gen_light_speed():
    """Light Speed: no leaving and no arriving — one streak, over as it starts, with the snap of
    the air it went through and light scattering off the end of it."""
    dur = 0.34
    streak = whoosh(0.14, lo=2000, hi=9000, end=3000, peak=0.3, q=2.0, body=0.3, top=0.6, flutter=0.0, rise=1.2, fall=2.4, name="lss")
    out = lay(dur, (0.0, streak, 0.8), (0.03, crack(0.016, name="lsk"), 0.4),
              (0.04, halo((N["E6"], N["E6"] * 1.5, N["E6"] * 2), 0.26, q=40, peak=0.3, breath=0.2, name="lsh"), 0.14),
              (0.04, shimmer(0.3, 5000, 14000, rate=200, grain=(0.004, 0.012), density=[(0.0, 1.0), (0.4, 0.4), (1.0, 0.05)], name="lsd"), 0.36),
              (0.03, weight(120.0, 0.08, name="lsw"), 0.25))
    return reverb(out, decay=0.5, damp=7500, mix=0.2, name="ls", predelay=0.004)


sound("cast_light_speed", gen_light_speed, 0.60, "melee", dict(BLINK_M, **BRIGHT))


def gen_burrow():
    """The Spider's Burrow: down — legs scrabbling earth aside, the soil closing over — and up
    again, the ground breaking."""
    dur = 0.6
    down = mixdown(rip(0.2, rate=(160.0, 70.0), band=(300, 2600), name="bwd"),
                   grains(0.22, rate=170, band=(200, 1800), length=(0.002, 0.008), density=[(0.0, 1.0), (1.0, 0.3)], name="bwg") * 0.7)
    under = bandpass(pink(0.2, "bwu"), 60, 300) * arc(0.2, 0.5, 1.0, 1.0)
    up = mixdown(debris(0.26, count=12, band=(200, 2200), spread=(0.0, 0.6), name="bwb"), thud(104, 0.14, drive=1.7, name="bwt") * 0.6)
    out = lay(dur, (0.0, norm(down), 0.6), (0.14, norm(under), 0.3), (0.32, norm(up), 0.7),
              (0.34, chitin(0.14, (1500, 6500), 0.7, "bwc"), 0.4))
    return room(out, mix=0.2, damp=3600, name="bur")


sound("cast_burrow", gen_burrow, 0.62, "ability", dict(BLINK_M, width=0.7, tail=(0.4, 0.14)))


def gen_rebirth():
    """The Phoenix's Rebirth: it goes up in its own fire — a whoomph, the roar of it — and
    comes out of the flame somewhere else, wings spread, with the cry."""
    dur = 0.7
    out = lay(dur, (0.0, whump(0.24, f=(180.0, 62.0), name="rbw"), 0.8), (0.0, fire(0.34, low=110, roar=1.0, crackles=16, name="rbf"), 0.7),
              (0.26, fire(0.36, low=120, roar=0.9, crackles=14, name="rbf2") * arc(0.36, 0.1, 1.0, 1.3), 0.6),
              (0.28, wings(0.34, 8.0, 0.8, (200, 2600), "rbwg"), 0.6),
              (0.28, screech(0.36, f0=(1500.0, 2500.0, 1300.0), rough=0.5, breath_=0.45, name="rbv"), 0.26),
              (0.28, shimmer(0.4, 4000, 13000, rate=110, density=[(0.0, 1.0), (1.0, 0.1)], name="rbs"), 0.22))
    return reverb(saturate(out, 1.7), decay=0.8, damp=4600, mix=0.26, name="rb", predelay=0.01)


sound("cast_rebirth", gen_rebirth, 0.72, "ability", dict(BLINK_M, sub=(50, 0.25)), held=True)


def gen_drift():
    """The Jellyfish's Drift: a pulse of its bell and a slide through water — a soft surge,
    bubbles let go behind it."""
    dur = 0.5
    surge = whoosh(dur, lo=250, hi=1500, end=300, peak=0.3, q=1.3, body=1.0, top=0.0, flutter=0.3, rise=1.4, fall=1.6, name="drs")
    surge = lowpass(surge, 2600)
    bub = bubbles(dur, count=18, f=(400, 1800), rise=(1.15, 1.6), size=(0.008, 0.024), span=(0.1, 0.9), name="drb") * arc(dur, 0.4, 1.0, 1.2)
    out = lay(dur, (0.0, snap("soft", "drp"), 0.5), (0.0, surge, 0.8), (0.04, bub, 0.34),
              (0.0, water(0.3, size=1.3, bubbles=5, foam=0.3, name="drw"), 0.3))
    return reverb(out, decay=0.6, damp=3600, mix=0.26, name="drf", predelay=0.008)


sound("cast_drift", gen_drift, 0.56, "ability", dict(PHASE_M, transient=0.9, tail=(0.7, 0.2)), held=True)


def gen_mirage():
    """The Djinn's Mirage: heat-haze — the air shimmering, the lamp's smoke curling — he was
    never where you thought."""
    dur = 0.6
    from sound_engines import _b_mystic
    haze = shimmer(dur, 2600, 9000, rate=120, grain=(0.006, 0.02), density=[(0.0, 0.4), (0.4, 1.0), (1.0, 0.2)], name="mrs")
    haze = haze * nlfo(dur, 13.0, 0.4, 1.0, "mrsl")
    out = lay(dur, (0.0, vanish(0.16, lo=500.0, hi=3000.0, name="mro"), 0.35), (0.0, norm(lamp_smoke(dur, "mrg")), 0.4),
              (0.0, haze, 0.3), (0.12, norm(_b_mystic(N["A4"], 0.4, "mrm")), 0.4),
              (0.18, arrive(0.24, hi=3200.0, lo=500.0, heft=0.2, name="mri"), 0.5))
    return reverb(out, decay=0.9, damp=5600, mix=0.28, name="mir", predelay=0.01)


sound("cast_mirage", gen_mirage, 0.58, "ability", dict(PHASE_M, transient=0.9), held=True)


def gen_astral_project():
    """Astral Project: up among the stars and down again — four soft rising notes as he goes,
    a held breath of high air singing, and the air opening where he lands."""
    dur = 0.6
    out = lay(dur, (0.0, chime_run(0.4, lo=1300, hi=4200, up=True, notes=4, ring=0.24, name="aspu"), 0.3),
              (0.0, vanish(0.16, lo=800.0, hi=5200.0, name="aspo"), 0.35),
              (0.1, halo((N["A5"], N["E6"], N["A6"]), 0.4, q=40, peak=0.4, breath=0.25, name="asph"), 0.32),
              (0.28, arrive(0.26, hi=5200.0, lo=900.0, heft=0.2, name="aspi"), 0.5),
              (0.0, shimmer(dur, 4200, 13000, density=[(0.0, 0.5), (0.5, 1.0), (1.0, 0.2)], name="asps"), 0.3))
    return reverb(out, decay=0.9, damp=7000, mix=0.28, name="asp", predelay=0.01)


sound("cast_astral_project", gen_astral_project, 0.58, "ability", dict(BLINK_M, **BRIGHT), held=True)


# ═══════════════════════════ phases ═══════════════════════════

def gen_phase_shift():
    """The Wraith going ethereal: the world's sound drawn thin and far away — a slow shiver in
    the air, a cold chord swelling under it, a breath."""
    dur = 0.68
    shiver = svf(pink(dur, "ph"), glide([(0.0, 620), (0.5, 2100), (1.0, 1000)], dur), q=3.0, mode="bp")
    shiver = shiver * nlfo(dur, 6.5, 0.4, 1.0, "phl") * swell_env(dur, 1.0)
    cold = halo((N["A3"], N["E4"], N["A4"]), dur, q=36, peak=0.45, breath=0.3, wander=0.4, name="phc")
    gone = breath(dur, [(0.0, "oo"), (0.5, "uh"), (1.0, "oo")], band=(220, 4200), name="phb") * arc(dur, 0.4, 1.2, 1.3)
    return reverb(mixdown(norm(shiver) * 0.45, cold * 0.4, norm(gone) * 0.4), decay=1.1, damp=6200, mix=0.30, name="ph", predelay=0.012)


sound("phase_shift", gen_phase_shift, 0.56, "ability", PHASE_M, held=True)
sound("cast_phase_shift", gen_phase_shift, 0.56, "ability", PHASE_M, held=True)


def gen_smoke_bomb():
    """A smoke bomb thrown at the feet: the pellet's crack, and the smoke hissing out thick."""
    dur = 0.55
    out = lay(dur, (0.0, crack(0.018, name="smk"), 0.6), (0.0, snap("pop", "smp"), 0.7), (0.0, whump(0.16, f=(190.0, 70.0), name="smw"), 0.5),
              (0.01, gas(dur, band=(350, 4600), fizz=0.5, pitchfall=False, name="smg") * arc(dur, 0.12, 1.0, 1.3), 0.7))
    return room(out, mix=0.24, damp=4200, name="smk", drive=1.5)


sound("cast_smoke_bomb", gen_smoke_bomb, 0.60, "ability", dict(PHASE_M, transient=1.2, tail=(0.7, 0.2)), held=True)
sound("cast_mist_form", lambda: reverb(mixdown(gas(0.7, band=(300, 3800), fizz=0.3, pitchfall=False, name="mst") * arc(0.7, 0.3, 1.2, 1.2) * 0.6,
                                               breath(0.7, [(0.0, "ah"), (0.5, "oo"), (1.0, "oo")], band=(220, 4200), name="mstb") *
                                               arc(0.7, 0.3, 1.2, 1.3) * 0.4,
                                               halo((N["A2"], N["E3"], N["C4"]), 0.7, q=26, peak=0.4, breath=0.3, wander=0.4, name="msth") * 0.3,
                                               pad_to(wings(0.3, 16.0, 0.3, (400, 4000), "mstw"), 0.7)),
                                       decay=1.1, damp=4200, mix=0.3, name="mst", predelay=0.012),
      0.58, "ability", PHASE_M, held=True)
sound("cast_wind_form", lambda: reverb(mixdown(whoosh(0.7, lo=300, hi=3200, end=600, peak=0.4, q=1.3, body=0.7, top=0.4, flutter=0.4, rise=1.4, fall=1.3, name="wnf") * 0.8,
                                               norm(svf(pink(0.7, "wnfh"), glide([(0.0, 400.0), (0.45, 1300.0), (1.0, 700.0)], 0.7), q=5.0, mode="bp") *
                                                    arc(0.7, 0.45, 1.4, 1.5) * nlfo(0.7, 9.0, 0.5, 1.0, "wnfl")) * 0.35,
                                               leaves(0.6, 60, "wnfv") * 0.12),
                                       decay=0.9, damp=5600, mix=0.26, name="wnf", predelay=0.01),
      0.58, "ability", dict(PHASE_M, pan=(-0.6, 0.6)), held=True)


def gen_dust_devil():
    """The Sandstorm becoming one: sand lifted and spun — a turning wind, the hiss of grit in
    it, faster as it tightens."""
    dur = 0.7
    spin = np.sin(2 * np.pi * np.cumsum(glide([(0.0, 4.0), (1.0, 11.0)], dur) * nlfo(dur, 2.0, 0.93, 1.07, "ddj")) / SR)
    wind = whoosh(dur, lo=300, hi=2600, end=600, peak=0.5, q=1.4, body=0.8, top=0.2, flutter=0.2, rise=1.3, fall=1.3, name="ddw") * (0.6 + 0.4 * spin)
    grit = grains(dur, rate=900, band=(1100, 7500), length=(0.0002, 0.0012), density=[(0.0, 0.2), (0.5, 1.0), (1.0, 0.4)], name="ddg") * (0.6 + 0.4 * spin)
    return reverb(mixdown(norm(wind) * 0.8, grit * 0.4, dust(dur, "ddd") * 0.3), decay=0.8, damp=4600, mix=0.24, name="dd", predelay=0.01)


sound("cast_dust_devil", gen_dust_devil, 0.60, "ability", dict(PHASE_M, pan=(-0.6, 0.6)), held=True)
sound("cast_flame_burst", lambda: reverb(saturate(lay(0.55, (0.0, snap("tick", "flbt"), 0.4), (0.0, whump(0.2, f=(230.0, 90.0), name="flbw"), 0.7),
                                                      (0.0, fire(0.5, low=130, roar=0.9, crackles=30, name="flbf") * arc(0.5, 0.08, 1.0, 1.3), 0.7),
                                                      (0.0, grains(0.5, rate=150, band=(1800, 9500), length=(0.0003, 0.0018),
                                                                   density=[(0.0, 1.0), (0.4, 0.6), (1.0, 0.2)], name="flbs"), 0.3)), 1.7),
                                         decay=0.6, damp=4600, mix=0.24, name="flb", predelay=0.008),
      0.64, "ability", dict(PHASE_M, transient=1.1, tail=(0.7, 0.2)))


def gen_time_stop():
    """Time Stop: a clock running down — tick, tock, slower, slower — the air itself dragging
    to a halt and holding."""
    dur = 0.8
    out = np.zeros(n_of(dur))
    t, gap, i = 0.0, 0.06, 0
    while t < dur * 0.7 and i < 9:
        k = mixdown(snap("tick", "tst%d" % i), click(0.005, fc=1400 if i % 2 else 3000, name="tsc%d" % i) * 0.6)
        at(out, t, norm(k) * (0.5 - 0.03 * i))
        t += gap
        gap *= 1.35
        i += 1
    drag = halo((N["A3"], N["E4"], N["A4"]), dur, q=40, peak=0.3, breath=0.3, name="tsh")
    drag = fit(varispeed(drag, glide([(0.0, 1.3), (0.7, 0.7), (1.0, 0.62)], dur)), n_of(dur))
    slow = whoosh(dur, lo=2400, hi=2600, end=200, peak=0.05, q=1.4, body=0.6, top=0.1, flutter=0.0, rise=1.0, fall=1.0, name="tsw")
    return reverb(mixdown(out, drag * 0.4, slow * 0.4), decay=1.0, damp=5000, mix=0.28, name="ts", predelay=0.012)


sound("cast_time_stop", gen_time_stop, 0.60, "ability", dict(PHASE_M, transient=1.0, pingpong=(0.1, 0.3, 0.2)), held=True)


def gen_deep_dive():
    """The Shark's Deep Dive: under — the splash of going in, and then everything heard from
    below the surface, dull and close, bubbles going up."""
    dur = 0.7
    splash = water(0.26, size=0.9, bubbles=8, foam=0.9, name="dpd")
    under = lowpass(pink(dur, "dpu"), 500) * arc(dur, 0.3, 1.0, 1.0) * nlfo(dur, 6.0, 0.5, 1.0, "dpul")
    bub = bubbles(dur, count=22, f=(300, 1400), rise=(1.2, 1.8), size=(0.01, 0.03), span=(0.15, 0.95), name="dpb")
    out = lay(dur, (0.0, splash, 0.8), (0.0, thud(100, 0.16, drive=1.7, name="dpt"), 0.4), (0.08, norm(under), 0.5),
              (0.1, lowpass(bub, 2400), 0.34))
    return reverb(out, decay=0.7, damp=2600, mix=0.26, name="dpd", predelay=0.01)


sound("cast_deep_dive", gen_deep_dive, 0.62, "ability", dict(PHASE_M, transient=1.1, tail=(0.7, 0.2)), held=True)
sound("cast_leaf_cloak", lambda: reverb(mixdown(leaves(0.6, 220, "lfc") * arc(0.6, 0.3, 1.0, 1.3) * 0.6,
                                                leaves(0.5, 120, "lfc2") * 0.3,
                                                whoosh(0.5, lo=600, hi=3600, end=900, peak=0.3, q=1.3, body=0.4, top=0.3, flutter=0.3, name="lfcw") * 0.4,
                                                pad_to(chitin(0.12, (2000, 8000), 0.6, "lfck"), 0.6)),
                                        decay=0.5, damp=5600, mix=0.22, name="lfc", predelay=0.008),
      0.54, "ability", dict(PHASE_M, tail=(0.6, 0.18)))


def gen_camouflage():
    """The Chameleon's Camouflage: its skin changing — a soft ripple running over it, a wet
    shimmer — and it is the colour of whatever is behind it."""
    dur = 0.55
    ripple = svf(pink(dur, "cmr"), glide([(0.0, 600.0), (0.5, 2600.0), (1.0, 1400.0)], dur), q=3.0, mode="bp")
    ripple = ripple * nlfo(dur, 17.0, 0.2, 1.0, "cmrl") * arc(dur, 0.4, 1.2, 1.4)
    skin = bubbles(dur, count=20, f=(900, 3200), rise=(1.1, 1.4), size=(0.005, 0.014), span=(0.0, 0.9), name="cmb") * arc(dur, 0.4, 1.0, 1.3)
    out = mixdown(norm(ripple) * 0.6, skin * 0.26, shimmer(dur, 3000, 9000, rate=70, grain=(0.006, 0.02), bell=0.0,
                                                           density=[(0.0, 0.3), (0.5, 1.0), (1.0, 0.2)], name="cms") * 0.2)
    return reverb(out, decay=0.6, damp=5600, mix=0.24, name="cam", predelay=0.008)


sound("cast_camouflage", gen_camouflage, 0.52, "ability", dict(PHASE_M, tail=(0.7, 0.2)), held=True)
sound("cast_ink_cloud", lambda: reverb(saturate(lay(0.7, (0.0, snap("pop", "ikp"), 0.5),
                                                    (0.0, water(0.3, size=1.0, bubbles=10, foam=0.7, name="ikw"), 0.6),
                                                    (0.02, squelch(0.3, f=(1100, 250), q=2.8, lumps=4, name="ikq"), 0.5),
                                                    (0.05, gas(0.65, band=(200, 2600), fizz=0.3, pitchfall=False, name="ikg") * arc(0.65, 0.25, 1.0, 1.2), 0.6),
                                                    (0.1, lowpass(bubbles(0.5, count=16, f=(300, 1200), rise=(1.2, 1.7), size=(0.01, 0.03), name="ikb"), 2400), 0.3)), 1.5),
                                       decay=0.8, damp=3000, mix=0.26, name="ink", predelay=0.01),
      0.62, "ability", dict(PHASE_M, transient=1.0, tail=(0.8, 0.22)), held=True)
sound("cast_sky_dance", lambda: reverb(mixdown(wings(0.7, 7.0, 1.0, (180, 2400), "skw"),
                                               whoosh(0.7, lo=300, hi=2800, end=1200, peak=0.6, q=1.3, body=0.7, top=0.3, flutter=0.3, rise=1.5, fall=1.2, name="ska") * 0.5,
                                               pad_to(screech(0.3, f0=(1400.0, 2200.0, 1300.0), rough=0.6, breath_=0.5, name="skv") * 0.16, 0.7)),
                                       decay=0.7, damp=5200, mix=0.24, name="sky", predelay=0.01),
      0.60, "ability", dict(PHASE_M, transient=0.9, tail=(0.7, 0.2)), held=True)


def gen_illusion():
    """The Yokai's Illusion: a fox's trick — a small bell rung once somewhere, the flute's
    note bending away, and paper charms whispering as she fades."""
    dur = 0.65
    from sound_engines import _b_mystic
    note = norm(_b_mystic(N["D5"], 0.5, "ilm"))
    note = fit(varispeed(note, glide([(0.0, 1.0), (0.5, 1.0), (1.0, 0.9)], 0.5)), len(note))
    bell = chime_run(0.14, lo=2000, hi=2400, up=True, notes=1, ring=0.14, name="ilb")
    paper = bandpass(noise(dur, "ilp"), 1100, 5200) * nlfo(dur, 30.0, 0.0, 1.0, "ilpl") ** 2 * arc(dur, 0.4, 1.0, 1.3)
    out = lay(dur, (0.0, bell, 0.22), (0.02, note, 0.4), (0.0, norm(paper), 0.26),
              (0.0, shimmer(dur, 3000, 9000, rate=70, density=[(0.0, 0.5), (0.5, 1.0), (1.0, 0.2)], name="ils"), 0.2))
    return reverb(out, decay=1.0, damp=5600, mix=0.3, name="ill", predelay=0.012)


sound("cast_illusion", gen_illusion, 0.56, "ability", PHASE_M, held=True)


# ═══════════════════════════ barriers ═══════════════════════════

def gen_barrier_up():
    """A wall of light going up in front of you: a short upward rush of energy that locks with
    a soft, low thump and hums for a moment after."""
    dur = 0.6
    rush = svf(pink(0.16, "bur"), glide([(0.0, 380), (1.0, 2400)], 0.16), q=2.6, mode="bp") * swell_in(0.16, 1.8)
    hum = halo((N["A2"], N["E3"], N["A3"]), 0.44, q=40, attack=0.02, t60=0.4, breath=0.15, name="buh") * nlfo(0.44, 7.0, 0.7, 1.0, "buhl")
    out = lay(dur, (0.0, norm(rush), 0.55), (0.14, thud(104, 0.26, drive=1.8, name="bul"), 0.8),
              (0.14, bandpass(contact(0.012, name="buc"), 300, 2200), 0.35), (0.15, hum, 0.36),
              (0.13, shimmer(0.3, 2600, 7000, rate=60, density=[(0.0, 1.0), (1.0, 0.1)], name="bug"), 0.12))
    return reverb(out, decay=0.55, damp=5000, mix=0.22, name="bu", predelay=0.01)


sound("barrier_up", gen_barrier_up, 0.6, "ability", WALL_M)


def planted(mat="steel", f0=150.0, name="plt"):
    """A heavy shield set on the ground: the thunk of its foot, and the dull knock of what it
    is made of — damped at once, since a hand is on it."""
    return mixdown(thud(96, 0.22, drive=1.9, name=name + "t"),
                   strike(mat, f0, 0.16, hardness=0.9, damp=3.0, name=name + "s", ring=0.1) * 0.6,
                   pad_to(grains(0.08, rate=150, band=(250, 1800), length=(0.002, 0.007), name=name + "g") * 0.4, 0.22))


sound("cast_scutum", lambda: room(lay(0.5, (0.0, norm(push_off("cloth", "sctc")), 0.3),
                                      (0.0, whoosh(0.16, lo=300, hi=1800, end=400, peak=0.7, q=1.5, body=0.8, top=0.05, rise=1.8, fall=1.4, name="scta"), 0.4),
                                      (0.12, planted("wood", 170.0, "sctp"), 0.9)),
                                  decay=0.4, damp=4200, mix=0.2, name="sct"),
      0.66, "ability", dict(WALL_M, sub=(50, 0.25), tail=(0.4, 0.14)))
sound("cast_bulwark", lambda: room(lay(0.55, (0.0, armour(0.2, 8, "blwa"), 0.3),
                                       (0.0, whoosh(0.16, lo=300, hi=1800, end=400, peak=0.7, q=1.5, body=0.8, top=0.05, rise=1.8, fall=1.4, name="blwh"), 0.4),
                                       (0.12, planted("steel", 150.0, "blwp"), 1.0),
                                       (0.2, planted("steel", 190.0, "blwq"), 0.5)),
                                   decay=0.45, damp=4200, mix=0.2, name="blw"),
      0.70, "ability", dict(WALL_M, sub=(48, 0.28), tail=(0.45, 0.15)))
sound("cast_aegis", lambda: reverb(mixdown(lay(0.7, (0.0, norm(svf(pink(0.16, "aeg"), glide([(0.0, 500), (1.0, 3000)], 0.16), q=2.4, mode="bp") * swell_in(0.16, 1.8)), 0.5),
                                               (0.14, thud(108, 0.24, drive=1.7, name="aegt"), 0.6)),
                                           pad_to(lay(0.7, (0.12, radiance(0.55, notes=(N["A3"], N["E4"], N["A4"]), name="aegr") * arc(0.55, 0.5, 1.0, 1.2), 0.9)), 0.7)),
                                   decay=0.9, damp=6400, mix=0.26, name="aeg", predelay=0.01),
      0.66, "ability", dict(WALL_M, comp=(0.32, 2.4), tail=(0.9, 0.22), **BRIGHT), held=True)
sound("cast_ice_wall", lambda: reverb(saturate(lay(0.65, (0.0, grains(0.4, rate=130, band=(1800, 8000), length=(0.0008, 0.003),
                                                                    density=[(0.0, 0.4), (0.4, 1.0), (1.0, 0.2)], name="iwc"), 0.3),
                                                   (0.0, whoosh(0.34, lo=900, hi=4200, end=1600, peak=0.8, q=1.5, body=0.5, top=0.4, rise=1.8, fall=1.0, name="iwr"), 0.5),
                                                   (0.0, creak(0.34, rate=(60.0, 170.0), body=(900.0, 2100.0), q=4.0, name="iwk"), 0.3),
                                                   (0.3, mixdown(thud(110, 0.24, drive=1.8, name="iwt"), crack(0.02, name="iwx") * 0.5), 0.8),
                                                   (0.3, halo((N["E6"], N["E6"] * 1.5, N["E6"] * 2), 0.3, q=80, attack=0.004, t60=0.26, breath=0.0, name="iwg"), 0.22),
                                                   (0.3, shimmer(0.34, 5000, 13000, rate=120, grain=(0.004, 0.012), bell=0.4,
                                                                 density=[(0.0, 1.0), (1.0, 0.1)], name="iwf"), 0.26)), 1.4),
                                      decay=0.6, damp=6500, mix=0.22, name="iw", predelay=0.01),
      0.66, "ability", dict(WALL_M, **BRIGHT))
sound("cast_carapace", lambda: room(lay(0.5, (0.0, chitin(0.2, (900, 4000), 0.9, "crpa"), 0.4),
                                        (0.0, whoosh(0.2, lo=400, hi=2200, end=600, peak=0.7, q=1.4, body=0.8, top=0.05, rise=1.6, fall=1.4, name="crpw"), 0.45),
                                        (0.04, grains(0.16, rate=35, band=(700, 2600), length=(0.004, 0.008), q=(0.8, 1.2), name="crpk"), 0.3),
                                        (0.18, mixdown(thud(104, 0.2, drive=1.9, name="crpt"), pad_to(click(0.006, fc=1500, name="crpc") * 0.6, 0.2)), 0.9)),
                                    decay=0.35, damp=4600, mix=0.18, name="crp"),
      0.64, "ability", dict(WALL_M, sub=(52, 0.22), tail=(0.35, 0.13)))
sound("cast_stone_wall", lambda: reverb(saturate(lay(0.75, (0.0, saturate(bandpass(pink(0.4, "stwg"), 160, 1700), 2.8) * arc(0.4, 0.5, 1.2, 1.4), 0.55),
                                                     (0.0, bandpass(pink(0.45, "stwr"), 55, 300) * arc(0.45, 0.5, 1.0, 1.2) * nlfo(0.45, 12.0, 0.5, 1.0, "stwl"), 0.4),
                                                     (0.0, debris(0.5, count=12, band=(250, 2400), spread=(0.0, 0.8), name="stwd"), 0.4),
                                                     (0.34, slam("stone", 0.4, name="stws"), 0.8)), 1.6),
                                        decay=0.7, damp=3200, mix=0.24, name="stw", predelay=0.012),
      0.74, "ability", dict(WALL_M, sub=(46, 0.3)))


# ═══════════════════════════ traps set ═══════════════════════════

def gen_trap_place():
    """A trap set down: the weight of it landing on earth, the catch taking, and the small
    click of the mechanism being armed. Deliberately quiet and short — the point of laying one is
    that the other side does not notice, and everyone nearby hears it."""
    dur = 0.38
    out = lay(dur, (0.0, thud(92, 0.2, drive=1.4, name="tpl"), 0.8),
              (0.004, bandpass(noise(0.07, "tpe"), 200, 1800) * exp_env(0.07, attack=0.001, decay=14.0), 0.45),
              # the catch takes: one short iron partial, heavily damped. Left ringing this is the bucket
              (0.1, strike("iron", 620, 0.16, hardness=1.2, damp=2.6, name="tpc", ring=0.18), 0.42),
              (0.17, click(0.006, fc=2600, name="tpk"), 0.35))
    return reverb(saturate(out, 1.5), decay=0.3, damp=4200, mix=0.15, name="tp", predelay=0.004)


sound("trap_place", gen_trap_place, 0.5, "melee", TRAP_M)


def toss(name, lo=350.0):
    return whoosh(0.2, lo=lo, hi=lo * 6, end=lo * 1.3, peak=0.4, q=1.6, body=0.7, top=0.1, name=name)


sound("cast_snare_mine", lambda: room(lay(0.5, (0.0, toss("snmt"), 0.4), (0.02, rattle(0.16, count=9, band=(1600, 6000), q=9.0, cluster=0.8, name="snmc"), 0.4),
                                          (0.2, thud(96, 0.18, drive=1.5, name="snml"), 0.7),
                                          (0.24, creak(0.12, rate=(120.0, 260.0), body=(900.0, 1900.0), q=4.0, name="snmr"), 0.3),
                                          (0.36, mixdown(snap("hard", "snmk"), click(0.006, fc=2200, name="snmk2") * 0.6), 0.4)),
                                      decay=0.3, damp=4200, mix=0.15, name="snm", drive=1.5),
      0.54, "melee", TRAP_M)


def gen_open_grave():
    """Open Grave: the spade going in twice, and the earth off it — a grave dug in a hurry."""
    dur = 0.6
    out = np.zeros(n_of(dur))
    for i, t0 in enumerate((0.0, 0.24)):
        dig = mixdown(scrape(0.1, lo=1400, hi=3000, bands=2, q=3.0, name="ogs%d" % i) * 0.5,
                      grains(0.14, rate=220, band=(200, 2000), length=(0.002, 0.008), density=[(0.0, 1.0), (1.0, 0.2)], name="ogd%d" % i),
                      pad_to(thud(100, 0.12, drive=1.5, name="ogt%d" % i) * 0.6, 0.14))
        at(out, t0, norm(dig) * (0.8 - 0.1 * i))
        at(out, t0 + 0.12, grains(0.14, rate=120, band=(200, 1500), length=(0.003, 0.01), density=[(0.0, 0.5), (0.4, 1.0), (1.0, 0.0)], name="ogf%d" % i) * 0.4)
    return room(out, decay=0.35, damp=3400, mix=0.16, name="og", drive=1.5)


sound("cast_open_grave", gen_open_grave, 0.56, "melee", TRAP_M)


def gen_web_trap():
    """A web spun across the ground: three strands paid out, quick, each a thin whine and a
    sticky tack as it is fixed."""
    dur = 0.5
    out = np.zeros(n_of(dur))
    for i in range(3):
        strand = svf(noise(0.12, "wts%d" % i), glide([(0.0, 3000.0 + 400 * i), (1.0, 1400.0)], 0.12), q=7.0, mode="bp") * decay_env(0.12, attack=0.004)
        at(out, i * 0.11, norm(strand) * 0.35)
        at(out, i * 0.11, whoosh(0.1, lo=1400, hi=5600, end=1800, peak=0.25, q=2.2, body=0.2, top=0.4, name="wtw%d" % i) * 0.4)
        at(out, i * 0.11 + 0.09, squelch(0.07, f=(1900, 800), q=2.6, lumps=1, name="wtq%d" % i) * 0.35)
    return room(out, decay=0.3, damp=5200, mix=0.16, name="wt", drive=1.4)


sound("cast_web_trap", gen_web_trap, 0.52, "melee", TRAP_M)


def gen_deploy_mine():
    """A mine laid: it is set down, a servo seats it, and it arms — two soft pips, the second
    higher — which is the only thing here that is meant to be a tone."""
    dur = 0.55
    out = lay(dur, (0.0, toss("dmt", 300.0), 0.35), (0.14, thud(98, 0.16, drive=1.5, name="dml"), 0.7),
              (0.14, click(0.006, fc=2000, name="dmc"), 0.4), (0.18, machine(0.16, servo=(500, 1100), clank=0.3, whine=0.4, name="dms"), 0.4))
    for i, (t0, f) in enumerate(((0.36, 1318.5), (0.45, 1760.0))):
        pip = (np.sin(2 * np.pi * f * t_axis(0.05)) + 0.3 * np.sin(4 * np.pi * f * t_axis(0.05))) * decay_env(0.05, attack=0.004)
        at(out, t0, pip * 0.16)
    return room(out, decay=0.3, damp=5200, mix=0.15, name="dm", drive=1.4)


sound("cast_deploy_mine", gen_deploy_mine, 0.54, "melee", TRAP_M)
sound("cast_anvil_trap", lambda: room(lay(0.55, (0.0, toss("avtt", 260.0), 0.4), (0.16, thud(84, 0.24, drive=1.9, name="avtl"), 0.9),
                                          (0.16, strike("steel", 150, 0.2, hardness=0.9, damp=3.2, name="avts", ring=0.1), 0.6),
                                          (0.18, debris(0.2, count=8, band=(250, 2200), name="avtd"), 0.35),
                                          (0.38, mixdown(snap("hard", "avtk"), rattle(0.05, count=3, band=(1500, 5000), q=8.0, cluster=1.0, name="avtr") * 0.6), 0.4)),
                                      decay=0.4, damp=3800, mix=0.18, name="avt", drive=1.6),
      0.62, "melee", dict(TRAP_M, sub=(50, 0.25)))


def gen_rune_carve(burn=False):
    """A rune cut into the ground: the chisel's strokes — stone scored, three times — and the
    lines taking light (or, burned in, taking fire)."""
    dur = 0.6
    out = np.zeros(n_of(dur))
    for i in range(3):
        stroke = mixdown(scrape(0.08, lo=1600, hi=3600, bands=2, q=3.5, name="rcs%d%d" % (i, burn)) * 0.6,
                         grains(0.08, rate=300, band=(500, 3000), length=(0.001, 0.004), name="rcg%d%d" % (i, burn)))
        at(out, i * 0.1, norm(stroke) * (0.6 - 0.06 * i))
    if burn:
        at(out, 0.3, sizzle(0.3, band=(2000, 7500), crackle=0.8, name="rcz") * arc(0.3, 0.15, 1.0, 1.4) * 0.3)
        at(out, 0.3, whump(0.16, f=(170.0, 70.0), name="rcw") * 0.4)
        at(out, 0.3, halo((N["A2"], N["E3"]), 0.3, q=26, peak=0.3, breath=0.3, name="rchb") * 0.3)
    else:
        at(out, 0.3, halo((N["A3"], N["E4"], N["A4"]), 0.3, q=36, peak=0.3, breath=0.2, name="rch") * 0.34)
        at(out, 0.3, shimmer(0.3, 2800, 8000, rate=80, density=[(0.0, 1.0), (1.0, 0.1)], name="rcsh") * 0.2)
    return reverb(out, decay=0.5, damp=5000, mix=0.2, name="rc", predelay=0.006)


sound("cast_binding_rune", gen_rune_carve, 0.54, "melee", dict(TRAP_M, width=0.7, tail=(0.5, 0.16)))
sound("cast_rune_trap", lambda: gen_rune_carve(True), 0.56, "melee", dict(TRAP_M, width=0.7, tail=(0.5, 0.16)))


# ═══════════════════════════ R ═══════════════════════════

def gen_cooldown_reset():
    """R: both abilities ready again. A spin-up — air drawn in up a rising sweep, streaking as
    it climbs — that lands on a settled chord sung by the air, with a glint on it: charged, never
    struck. Short, as the casts it frees follow straight after it."""
    dur = 0.55
    rise_d = 0.2
    rush = svf(pink(rise_d, "crr"), glide([(0.0, 520), (1.0, 3600)], rise_d), q=2.4, mode="bp") * swell_in(rise_d, 1.6)
    rush = flange(rush, lo_ms=0.4, hi_ms=4.0, depth=0.7, rise=False)
    spin = comb(bandpass(pink(rise_d, "crs"), 200, 5000), glide([(0.0, 220.0), (1.0, 440.0)], rise_d), fb=0.88, damp=0.4) * swell_in(rise_d, 1.8)
    chord_d = dur - rise_d + 0.03
    chord = halo((N["A4"], N["E5"], N["A5"]), chord_d, q=44, peak=0.18, breath=0.2, name="crg")
    out = lay(dur, (0.0, norm(rush), 0.5), (0.0, norm(spin), 0.3), (rise_d - 0.03, chord, 0.55),
              (rise_d - 0.03, whump(0.12, f=(190.0, 100.0), name="crw"), 0.35),
              (rise_d - 0.01, shimmer(0.26, 3500, 9000, rate=90, density=[(0.0, 1.0), (1.0, 0.1)], name="crsh"), 0.2))
    return reverb(out, decay=0.6, damp=6000, mix=0.24, name="cr", predelay=0.01)


sound("cooldown_reset", gen_cooldown_reset, 0.58, "ability", dict(width=0.9, transient=0.9, tail=(0.7, 0.2)))
