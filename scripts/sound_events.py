#!/usr/bin/env python3
"""What happens to a player, and what an explosive does when it goes off.

Spawn, death and the three hits are heard more than any attack in the game, so they get the most
structure and the tightest lengths: a hitmarker heard two hundred times a match has to be 130ms
and sweet; a death has to read as a designed moment, which means it resolves musically (in A
minor, with the soundtrack) rather than just stopping. Then the detonations (`AbilitySounds
.forBlast` picks one by what blew up, and whose it was), a shot stopped on a barrier, and a trap
going off.
"""
import numpy as np

from sound_engines import *  # noqa: F401,F403
from sound_attacks import flame_gout, glass_clink


def mallet(f, dur, bright=0.3, name="mlt"):
    """One soft note: a felt mallet on something that sings — the fundamental, an octave over
    it that dies sooner, and a breath of air on the same pitch so it is never a bare sine."""
    tt = t_axis(dur)
    env = decay_env(dur, attack=0.006)
    tone = np.sin(2 * np.pi * f * tt) + bright * np.sin(4 * np.pi * f * tt) * np.exp(-tt * (5.0 / dur))
    air_ = halo((f, f * 2), dur, q=50, attack=0.006, t60=dur, breath=0.0, name=name)
    return norm(tone * env + air_ * 0.35)


def gen_spawn():
    """Respawn: the air gathers, you are there, and a rising A-minor figure says go — three
    soft notes over a chord that swells under them. Short — this plays while the player is
    already trying to move."""
    dur = 0.8
    gather = svf(pink(0.3, "spg"), glide([(0.0, 5200), (1.0, 700)], 0.3), q=3.0, mode="bp") * swell_in(0.3, 2.2)
    figure = np.zeros(n_of(dur))
    for i, f in enumerate((440.0, 523.25, 659.25)):
        at(figure, 0.3 + i * 0.055, mallet(f, 0.34, name="spn%d" % i) * (0.75 + 0.1 * i))
    chord = halo((N["A3"], N["E4"], N["A4"], N["C5"]), 0.5, q=40, peak=0.3, breath=0.2, name="sph")
    out = lay(dur, (0.0, norm(gather), 0.5), (0.27, snap("pop", "spp"), 0.5), (0.28, weight(96.0, 0.2, name="spw"), 0.4),
              (0.0, figure, 0.8), (0.28, chord, 0.4),
              (0.28, shimmer(0.5, 4000, 11000, rate=110, density=[(0.0, 1.0), (0.5, 0.5), (1.0, 0.1)], name="sps"), 0.26))
    return reverb(out, decay=1.1, damp=6800, mix=0.30, name="spawn", predelay=0.012, size=1.2)


sound("spawn", gen_spawn, 0.66, "event", dict(width=1.0, transient=0.8, tail=(1.2, 0.24)), held=True)


def gen_death():
    """You died. The body hits the ground, the light goes out of the air — a chord sinking as
    it fades — and two notes fall a minor third: the gesture that makes it land as a moment
    instead of as a noise."""
    dur = 1.25
    fall = mixdown(strike("flesh", 104, 0.35, hardness=1.0, damp=0.8, name="dfl"), pad_to(thud(88, 0.4, drive=1.7, name="ddr") * 0.6, 0.35))
    # the light going out: a chord of singing air, slowing as it dies, so all of it sinks together
    out_ = halo((N["A3"], N["C4"], N["E4"]), 0.9, q=36, attack=0.01, t60=0.8, breath=0.3, name="dlo")
    out_ = fit(varispeed(out_, glide([(0.0, 1.0), (0.3, 0.9), (1.0, 0.55)], 0.9)), n_of(0.9))
    last = bandpass(pink(0.5, "dex"), 300, 3200) * arc(0.5, 0.15, 1.0, 1.6)
    knell = np.zeros(n_of(dur))
    for i, (f, t0, g) in enumerate(((220.0, 0.16, 0.8), (174.61, 0.42, 0.62))):  # A3 -> F3
        at(knell, t0, mallet(f, 0.8, bright=0.45, name="dk%d" % i) * g)
    out = lay(dur, (0.0, fall, 0.9), (0.02, out_, 0.4), (0.12, norm(last), 0.25), (0.0, knell, 0.5),
              (0.0, weight(78.0, 0.4, name="dsb"), 0.45))
    return reverb(out, decay=1.5, damp=2800, mix=0.32, name="death", predelay=0.018, size=1.5)


sound("death", gen_death, 0.84, "event", dict(width=0.9, transient=1.0, sub=(44, 0.3), tail=(1.4, 0.26)))


def gen_hit_taken():
    """You were hit. Meaty, low-mid, unmistakable — and deliberately dark above 5kHz, because
    this is the sound a losing player hears most and a bright one turns a bad fight into a
    headache. All of it is where a small speaker can play it: it was 90% under 120Hz."""
    dur = 0.36
    body = mixdown(strike("flesh", 150, 0.24, hardness=1.0, damp=0.9, name="htb"), pad_to(thud(126, 0.2, drive=2.0, name="htd") * 0.5, 0.24))
    # the slap: 400-2500Hz is where "something struck me" lives
    slap = bandpass(noise(0.13, "hta"), 400, 2500) * exp_env(0.13, attack=0.0008, decay=10.0)
    armour_ = bandpass(noise(0.06, "htm"), 900, 5200) * exp_env(0.06, attack=0.0005, decay=22.0)
    wind = bandpass(pink(0.16, "htg"), 300, 2200) * arc(0.16, 0.2, 1.0, 1.8)       # the breath knocked out
    out = lay(dur, (0.0, body, 0.8), (0.0, slap, 1.0), (0.004, armour_, 0.5), (0.03, norm(wind), 0.3),
              (0.0, weight(118.0, 0.16, name="hts"), 0.3))
    out = eq(saturate(out, 2.2), "lp", 7000, q=0.8)
    return reverb(out, decay=0.5, damp=2600, mix=0.22, name="ht", predelay=0.005)


sound("hit_taken", gen_hit_taken, 0.80, "melee", dict(width=0.28, transient=1.9, sub=(54, 0.25), comp=(0.26, 3.2),
                                                     air=(10000, -3.0), tail=(0.4, 0.10)))


def gen_hit_dealt():
    """The hitmarker. 130ms: a soft 't' and two small round notes a fourth apart (E and A, the
    music's own), the second higher, so landing a shot feels like an answer rather than a click.
    Two hundred a match, so nothing in it is sharp: an octave higher with a click on the front, as
    it was, it was a spoon on a glass."""
    dur = 0.16
    out = lay(dur, (0.0, snap("hard", "hd1"), 0.7), (0.004, mallet(659.25, 0.08, bright=0.35, name="hdn1"), 0.45),
              (0.032, mallet(880.0, 0.11, bright=0.35, name="hdn2"), 0.55),
              (0.0, thud(210, 0.06, drive=1.6, name="hdb"), 0.4))
    return reverb(saturate(out, 1.6), decay=0.18, damp=7500, mix=0.12, name="hd")


sound("hit_dealt", gen_hit_dealt, 0.62, "tiny", dict(width=0.22, transient=2.0, comp=(0.18, 5.0), dip=(3400, -2.5),
                                                    air=(12000, -1.5), tail=(0.2, 0.07)))


def gen_hit_other():
    """Someone else traded a hit. Same event, heard from over there: duller, more room, so it
    never competes with your own feedback."""
    dur = 0.3
    body = mixdown(strike("flesh", 138, 0.2, hardness=0.85, damp=1.1, name="hob"), pad_to(thud(112, 0.22, drive=1.7, name="hod") * 0.6, 0.2))
    smack = bandpass(noise(0.11, "hos"), 380, 3000) * exp_env(0.11, attack=0.001, decay=11.0)
    thwack = bandpass(noise(0.07, "hot"), 700, 4200) * exp_env(0.07, attack=0.0006, decay=16.0)
    out = lay(dur, (0.0, body, 0.6), (0.0, smack, 1.3), (0.003, thwack, 0.8))
    out = eq(saturate(out, 2.0), "lp", 6000, q=0.8)
    return reverb(out, decay=0.5, damp=3200, mix=0.26, name="ho", predelay=0.008)


sound("hit_other", gen_hit_other, 0.56, "melee", dict(width=0.7, transient=1.5, comp=(0.2, 4.5), tail=(0.5, 0.16)))


# ═══════════════════════════ detonations ═══════════════════════════
# `explosion` is a frag grenade, and it used to be every damaging explosion in the game. An
# explosive goes off as itself, and as its thrower's where the thrower makes it something else:
# a pirate's cannonball is not a soldier's grenade.

BLAST_M = dict(width=1.0, transient=1.2, sub=(44, 0.38), comp=(0.18, 4.5), tail=(1.3, 0.26))

sound("explosion", lambda: explosion(dur=1.2, size=1.1), 0.90, "heavy", dict(BLAST_M, comp=(0.16, 5.0), tail=(1.5, 0.26)))


def gen_blast_rocket():
    """A rocket's warhead: sharper and tighter than a grenade — a hard crack, a shorter boom —
    and the metal of it scattering."""
    dur = 1.0
    out = fit(explosion(dur=0.9, size=0.9, name="brk"), n_of(dur))
    at(out, 0.0, crack(0.03, name="brkk") * 0.6)
    out += debris(dur, count=16, band=(900, 6000), decay=(18.0, 40.0), spread=(0.04, 0.7), name="brkd") * 0.3
    return out


sound("blast_rocket", gen_blast_rocket, 0.90, "heavy", BLAST_M)


def gen_blast_cannonball():
    """A cannonball landing: no fuse, no flash — an iron ball coming down on something. A
    great dull blow, the ground thrown up, and splinters."""
    dur = 1.1
    hit = slam("earth", 0.9, name="bcb")
    out = fit(hit, n_of(dur))
    at(out, 0.0, explosion(dur=0.7, size=1.2, name="bcbx") * 0.5)
    out += debris(dur, count=18, band=(200, 2400), spread=(0.04, 0.8), name="bcbd") * 0.4
    return saturate(out, 1.5)


sound("blast_cannonball", gen_blast_cannonball, 0.90, "heavy", BLAST_M)


def gen_blast_bomb():
    """The Bombardier's bomb — the round black kind with a fuse: a fat, satisfying BOOM, more
    thump than crack, and a whoosh of smoke after it."""
    dur = 1.1
    out = fit(explosion(dur=1.0, size=1.3, name="bbm"), n_of(dur))
    at(out, 0.0, whump(0.4, f=(170.0, 56.0), name="bbmw") * 0.6)
    at(out, 0.06, gas(0.6, band=(300, 3600), fizz=0.4, pitchfall=False, name="bbmg") * arc(0.6, 0.15, 1.0, 1.3) * 0.3)
    return saturate(out, 1.5)


sound("blast_bomb", gen_blast_bomb, 0.90, "heavy", BLAST_M)


def gen_blast_fire(dur=1.1, name="bfr"):
    """A fire explosion — napalm, an inferno going up. Not a bang but a WHOOMPH: a deep bloom of
    pressure, flame roaring up out of it, and the crackle of what it set burning."""
    bloom = whump(0.5, f=(130.0, 44.0), name=name + "w")
    roar_ = flame_gout(dur * 0.9, size=1.8, name=name + "f")
    burn = grains(dur, rate=70, band=(900, 6000), length=(0.0005, 0.003),
                  density=[(0.0, 0.2), (0.2, 1.0), (1.0, 0.3)], name=name + "c")
    out = mixdown(pad_to(bloom, dur) * 0.8, pad_to(roar_, dur) * 0.9, burn * 0.3)
    return reverb(saturate(out, 1.7), decay=1.2, damp=3200, mix=0.3, name="bfr", predelay=0.012, size=1.5)


sound("blast_fire", gen_blast_fire, 0.90, "heavy", dict(BLAST_M, transient=0.9, comp=(0.2, 4.0), tail=(1.4, 0.26)))


def gen_blast_magma(dur=1.1, name="bmg"):
    """The ground splitting and spitting molten rock: a hard crack, the rock thrown up, fire
    roaring out, and lava slopping down."""
    out = lay(dur, (0.0, slam("stone", 0.5, name=name + "s"), 0.9), (0.03, flame_gout(dur * 0.8, size=1.6, name=name + "f"), 0.6),
              (0.08, squelch(0.5, f=(600, 130), q=2.4, lumps=8, name=name + "b"), 0.4),
              (0.05, sizzle(0.6, band=(2200, 7500), crackle=0.6, name=name + "z") * arc(0.6, 0.2, 1.0, 1.3), 0.14))
    return reverb(saturate(out, 1.7), decay=1.1, damp=3000, mix=0.28, name="bmg", predelay=0.012, size=1.4)


sound("blast_magma", gen_blast_magma, 0.90, "heavy", dict(BLAST_M, transient=1.1, comp=(0.2, 4.0), tail=(1.4, 0.26)))


def gen_blast_toxic(dur=1.0, name="btx"):
    """A toxic bomb bursting — the Plague Doctor's: a wet pop as the vessel gives, a cloud
    hissing out of it, and it bubbling where it lands."""
    pop = mixdown(whump(0.22, f=(190.0, 68.0), name=name + "w"), water(0.2, size=1.4, bubbles=6, foam=0.7, name=name + "p") * 0.6,
                  pad_to(glass_clink(4, name + "g") * 0.4, 0.2))
    cloud = gas(dur, band=(300, 4200), fizz=0.6, pitchfall=False, name=name + "g") * seg_env([(0.0, 0.0), (0.08, 1.0), (0.5, 0.7), (1.0, 0.0)], dur)
    bub = bubbles(dur, count=22, f=(300, 1400), rise=(1.2, 1.8), size=(0.008, 0.028), span=(0.1, 0.85), name=name + "b")
    out = mixdown(pad_to(norm(pop), dur) * 0.9, norm(cloud) * 0.55, bub * 0.26)
    return reverb(saturate(out, 1.6), decay=1.0, damp=3400, mix=0.28, name="btx", predelay=0.01, size=1.3)


sound("blast_toxic", gen_blast_toxic, 0.84, "heavy", dict(BLAST_M, transient=0.9, sub=(48, 0.28), comp=(0.22, 3.6), tail=(1.2, 0.26)))


def gen_blast_acid(dur=0.9, name="bac"):
    """An acid flask breaking: glass shattering into a spray of shards, the splash, and the
    acid eating in with a hiss."""
    shards = rattle(0.3, count=18, band=(2200, 6000), q=4.5, cluster=0.5, name=name + "g") * seg_env([(0.0, 1.0), (0.3, 0.6), (1.0, 0.0)], 0.3)
    bite = sizzle(dur, band=(2200, 8500), crackle=0.8, name=name + "z") * seg_env([(0.0, 0.0), (0.12, 1.0), (1.0, 0.0)], dur)
    out = mixdown(pad_to(shards, dur) * 0.6, pad_to(water(0.45, size=1.0, bubbles=14, foam=0.9, name=name + "w"), dur) * 0.6,
                  bite * 0.35, pad_to(whump(0.2, f=(170.0, 66.0), name=name + "t"), dur) * 0.6)
    return reverb(saturate(out, 1.6), decay=0.9, damp=4200, mix=0.26, name="bac", predelay=0.01, size=1.2)


sound("blast_acid", gen_blast_acid, 0.84, "heavy", dict(BLAST_M, sub=(50, 0.25), comp=(0.22, 3.6), tail=(1.0, 0.24), **BRIGHT))


def gen_blast_mud(dur=0.9, name="bmd"):
    """A mud bomb landing: one great wet SPLAT, clods of it thrown about and slapping down, and
    the dull weight of it."""
    splat = mixdown(squelch(0.4, f=(900, 140), q=3.0, lumps=6, name=name + "q"), water(0.35, size=0.8, bubbles=8, foam=0.6, name=name + "w") * 0.6)
    clods = grains(dur, rate=40, band=(200, 1600), length=(0.004, 0.012), q=(0.8, 1.5),
                   density=[(0.0, 0.0), (0.12, 1.0), (0.7, 0.4), (1.0, 0.0)], name=name + "c")
    out = mixdown(pad_to(norm(splat), dur) * 0.9, pad_to(thud(80, 0.36, drive=2.0, name=name + "t"), dur) * 0.6, clods * 0.4)
    return reverb(saturate(out, 1.8), decay=0.8, damp=3000, mix=0.24, name="bmd", predelay=0.01, size=1.2)


sound("blast_mud", gen_blast_mud, 0.86, "heavy", dict(BLAST_M, width=0.95, sub=(46, 0.32), comp=(0.2, 4.0), tail=(0.9, 0.22)))


def gen_blast_cluster(dur=1.1, name="bcl"):
    """A cluster bomb opening: not one blast but a string of small ones, walking across the
    ground."""
    out = np.zeros(n_of(dur))
    rng = np.random.default_rng(seed_of(name))
    t = 0.0
    for i in range(5):
        at(out, t, explosion(dur=0.55, size=0.55 + 0.08 * rng.uniform(), name=name + str(i)),
           rng.uniform(0.55, 0.9) * (1.0 - 0.08 * i))
        t += rng.uniform(0.06, 0.11)
    return saturate(out, 1.5)


sound("blast_cluster", gen_blast_cluster, 0.90, "heavy", dict(BLAST_M, sub=(46, 0.3), comp=(0.16, 5.0), tail=(1.4, 0.26)))


# ═══════════════════════════ barriers and traps ═══════════════════════════

def gen_barrier_block():
    """A shot stopped dead on a barrier: a dull, soft thump of energy and a short fizz as the
    shot comes apart on it. Nothing that rings: this is heard every time anyone fires into a
    shield, so a partial left ringing here is someone hitting a bucket all fight long."""
    dur = 0.26
    fizz = bandpass(noise(0.07, "bbf"), 700, 2600) * exp_env(0.07, attack=0.0006, decay=17.0)
    give = bandpass(pink(0.09, "bbg"), 180, 900) * decay_env(0.09, attack=0.002)
    out = lay(dur, (0.0, thud(132, 0.16, drive=1.9, name="bbt"), 0.7), (0.0, norm(give), 0.7), (0.002, fizz, 0.5))
    out = eq(saturate(out, 1.8), "lp", 6000, q=0.8)
    return reverb(out, decay=0.35, damp=3000, mix=0.16, name="bb", predelay=0.004)


sound("barrier_block", gen_barrier_block, 0.54, "melee", dict(width=0.5, transient=1.6, comp=(0.22, 4.0), tail=(0.35, 0.12)))


def gen_trap_snap():
    """Jaws closing on somebody. The loudest thing a trap does: a hard contact, a short dense
    ring off the steel, the low displacement of something being caught, and the chain snatching
    tight after it. The ring is kept short and damped — a partial left singing here plays every
    time anyone is caught all match, which is the clang rule exactly."""
    dur = 0.5
    out = lay(dur, (0.0, strike("steel", 340, 0.24, hardness=1.8, damp=2.2, name="tsn", ring=0.26), 0.95),
              (0.0, thud(108, 0.26, drive=2.2, name="tsb"), 0.8),
              (0.05, debris(0.26, count=6, band=(900, 4200), decay=(14.0, 26.0), name="tsc"), 0.3))
    return reverb(eq(saturate(out, 2.0), "lp", 8000, q=0.8), decay=0.42, damp=3800, mix=0.18, name="ts", predelay=0.005)


sound("trap_snap", gen_trap_snap, 0.74, "melee", dict(width=0.6, transient=1.9, sub=(52, 0.25), comp=(0.22, 3.6), tail=(0.45, 0.14)))


def gen_trap_poison():
    """A spore pod bursting: a wet split, then the gas going out of it. The hiss falls away
    rather than holding — this is the pod giving way, not a cloud sitting on the ground."""
    dur = 0.5
    split = mixdown(water(0.16, size=1.2, bubbles=9, foam=0.85, name="tpw") * 0.8, thud(126, 0.14, drive=1.8, name="tpt") * 0.5,
                    pad_to(snap("pop", "tpp") * 0.6, 0.16))
    out = lay(dur, (0.0, split, 1.0), (0.03, gas(0.36, band=(260, 3200), fizz=0.5, name="tpg"), 0.55))
    out = eq(saturate(out, 1.6), "lp", 7000, q=0.7)
    return reverb(out, decay=0.4, damp=3000, mix=0.18, name="tpo", predelay=0.006)


sound("trap_poison", gen_trap_poison, 0.58, "ability", dict(width=0.8, transient=1.0, tail=(0.6, 0.18)))


def gen_trap_ignite():
    """A rune catching: the intake as it takes, then a short roar of flame off the ground. Over
    inside half a second — what it leaves burning is on the victim, not in the mix."""
    dur = 0.52
    catch = whoosh(0.1, lo=700, hi=3000, end=1200, peak=0.8, q=1.8, body=0.6, top=0.1, rise=2.0, fall=1.0, name="tia")
    out = lay(dur, (0.0, catch, 0.5), (0.04, fire(0.34, low=120, roar=0.8, crackles=9, name="tif"), 0.75),
              (0.04, whump(0.2, f=(170.0, 62.0), name="tiw"), 0.5))
    out = eq(saturate(out, 1.7), "lp", 6800, q=0.7)
    return reverb(out, decay=0.42, damp=3000, mix=0.18, name="ti", predelay=0.008)


sound("trap_ignite", gen_trap_ignite, 0.62, "ability", dict(width=0.75, transient=1.2, tail=(0.7, 0.2)))
