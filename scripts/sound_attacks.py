#!/usr/bin/env python3
"""What every attack that throws something sounds like as it is thrown: every character's own.

A character's three attacks each have a sound that is THEIRS, as each has a look that is theirs
(render/projectiles/CLAUDE.md, *A projectile is drawn as its thrower's*): the Necromancer's bolt is
a skull in green fire and sounds like one, the Poltergeist's is the crockery, the Plague Doctor's
glob has its flies. A sound is shared only where the thing is the same thing in the same hands (a
frag grenade is a frag grenade). `AbilitySounds.scala` says which character hears which; the
abilities that throw nothing are in `sound_casts.py`.

They are grouped as the roster is (common/model/roster): the first twelve, the elementals, the
undead, the medieval, the science fiction, the beasts, the myths and the specialists. Within a
group a sound sits with the character it was made for.
"""
import numpy as np

from sound_engines import *  # noqa: F401,F403


# ═══════════════════════════ what several of them are made of ═══════════════════════════

def snarl(f0=120.0, dur=0.26, rough=0.75, size=1.0, name="snl"):
    """A short growl behind a blow: the animal that is doing it."""
    return roar(dur, f0=(f0 * 0.9, f0 * 1.2, f0 * 0.75), vowels=("uh", "ah", "aw"), size=size, rough=rough,
                breath_=0.55, peak=0.15, name=name)


def snake_hiss(dur=0.3, name="hss"):
    """A snake: a thin 'sss' that opens and dies, with the dry rattle of its breath in it."""
    s = svf(noise(dur, name + "n"), glide([(0.0, 5200), (0.3, 7400), (1.0, 4600)], dur), q=1.8, mode="bp")
    s = s * arc(dur, 0.2, 0.8, 1.4) * nlfo(dur, 28.0, 0.65, 1.0, name + "f")
    return norm(s)


def flies(dur=0.4, name="fly"):
    """Flies round something rotten: a few thin wingbeat buzzes, never quite together."""
    return buzz(dur, rate=230, band=(1500, 6000), bugs=3, name=name) * arc(dur, 0.3, 0.8, 1.2)


def glass_clink(count=3, name="gcl"):
    """Glass touching glass: a few tiny bright clinks, each over in milliseconds."""
    return rattle(0.07, count=count, band=(3600, 9500), q=14.0, cluster=1.0, name=name)


def bone_clack(dur=0.2, rate=60, name="bcl"):
    """Dry bones knocking on each other."""
    return grains(dur, rate=rate, band=(650, 2800), length=(0.002, 0.006), q=(0.8, 1.2),
                  density=[(0.0, 1.0), (0.3, 0.6), (1.0, 0.0)], name=name)


def leaves(dur=0.4, rate=140, name="lvs"):
    """Leaves thrashing."""
    return grains(dur, rate=rate, band=(1500, 7000), length=(0.001, 0.005), level=(0.2, 0.7),
                  density=[(0.0, 0.6), (0.2, 1.0), (1.0, 0.1)], name=name)


def siphon(dur, name):
    """A travelling whirlpool: air spiralling round a core (a band that circles and pulses,
    faster as it tightens), and a suction rising as it draws everything in."""
    n = n_of(dur)
    rate = glide([(0.0, 5.0), (1.0, 9.0)], dur) * nlfo(dur, 2.0, 0.92, 1.08, name + "rj")
    turn = np.sin(2 * np.pi * np.cumsum(rate) / SR)
    swirl = svf(pink(dur, name + "s"), 900 + 500 * turn, q=2.2, mode="bp") * (0.6 + 0.4 * turn)
    swirl *= seg_env([(0.0, 0.0), (0.15, 1.0), (0.8, 0.8), (1.0, 0.0)], dur)
    suck = svf(pink(dur, name + "k"), glide([(0.0, 3200), (1.0, 280)], dur), q=2.4, mode="bp")
    suck *= np.linspace(0.2, 1.0, n) ** 1.6 * seg_env([(0.0, 1.0), (0.88, 1.0), (1.0, 0.0)], dur)
    return mixdown(norm(swirl) * 0.6, norm(suck) * 0.5)


def flame_gout(dur, size=1.0, name="fgt"):
    """A mass of fire let go: the air rushing in to feed it, the WHOOMPH of it catching, and the
    roar going away — the flames licking unevenly, the whole of it falling in pitch as it leaves
    — with the crackle of what burns. `size` over 1 is a bigger, darker fire."""
    n = n_of(dur)
    k = 1.0 / size ** 0.5
    inrush = svf(pink(0.05, name + "i"), glide([(0.0, 400 * k), (1.0, 2200 * k)], 0.05), q=1.4, mode="bp")
    inrush = norm(inrush * swell_in(0.05, 2.0))
    catch = whump(min(0.3, dur * 0.45), f=(170 * k, 58 * k), name=name + "w")
    roar_ = whoosh(dur, lo=380 * k, hi=2100 * k, end=330 * k, peak=0.14, q=1.1, body=0.9, top=0.12,
                   flutter=0.0, rise=1.2, fall=1.5, name=name + "r")
    roar_ = leaving(roar_ * nlfo(dur, 23.0, 0.3, 1.5, name + "lk"), 1.08, 0.86)
    rumble = bandpass(pink(dur, name + "rm"), 70, 260) * nlfo(dur, 14.0, 0.2, 1.0, name + "rl") * arc(dur, 0.15, 1.0, 1.3)
    hiss = highpass(noise(dur, name + "h"), 3600) * nlfo(dur, 30.0, 0.0, 1.0, name + "hz") ** 2 * arc(dur, 0.12, 1.0, 2.0)
    pops = grains(dur, rate=40 * size, band=(800, 4500), length=(0.001, 0.004),
                  density=[(0.0, 0.6), (0.15, 1.0), (1.0, 0.25)], name=name + "p")
    return lay(dur, (0.0, inrush, 0.3), (0.035, catch, 0.75), (0.03, norm(roar_), 0.9), (0.03, norm(rumble), 0.3),
               (0.03, norm(hiss), 0.14), (0.04, pops, 0.2))


def tentacle(dur=0.4, size=1.0, alien=False, name="tnt"):
    """A tentacle lashing out and taking hold: the wet whip of it through the air, the slap
    where it lands and the suckers gripping."""
    lash = whoosh(dur * 0.6, lo=320 / size, hi=1900 / size, end=380 / size, peak=0.4, q=1.6, body=0.8,
                  top=0.05, flutter=0.25, rise=1.6, fall=2.0, name=name + "l")
    wet = squelch(dur * 0.7, f=(1500 / size, 300 / size), q=2.8, lumps=4, name=name + "w")
    slap = mixdown(strike("flesh", 150 / size, 0.14, hardness=0.95, name=name + "s"), snap("soft", name + "sp") * 0.5)
    grip = bubbles(0.14, count=6, f=(500 / size, 1400 / size), rise=(1.15, 1.5), size=(0.008, 0.02),
                   span=(0.0, 0.8), name=name + "g")
    out = lay(dur, (0.0, lash, 0.7), (0.02, wet, 0.5), (dur * 0.42, slap, 0.8), (dur * 0.46, grip, 0.25))
    if alien:
        # not of this sea: the same wet thing, ringing in a tube
        warble = comb(wet, glide([(0.0, 520.0), (0.5, 900.0), (1.0, 640.0)], len(wet) / SR), fb=0.8, damp=0.4)
        out = mixdown(out, pad_to(norm(warble) * 0.3, dur))
    return reverb(saturate(out, 2.0), decay=0.45, damp=3800, mix=0.22, name="tent")


def frost_jet(dur, name):
    """Freezing air let out under pressure, ice forming along it in a crackle that runs its
    whole length, and the cold weight of it arriving."""
    howl = whoosh(dur, lo=700, hi=3600, end=900, peak=0.2, q=1.6, body=0.6, top=0.4, flutter=0.4,
                  rise=1.0, fall=1.2, name=name + "w")
    freeze = grains(dur, rate=110, band=(2200, 8500), length=(0.0006, 0.0024),
                    density=[(0.0, 0.3), (0.1, 1.0), (0.5, 0.4), (1.0, 0.0)], name=name + "c")
    crunch = grains(dur, rate=45, band=(700, 3200), length=(0.002, 0.007),
                    density=[(0.0, 0.0), (0.2, 1.0), (1.0, 0.2)], name=name + "k")
    frost = shimmer(dur, 4500, 12500, rate=110, grain=(0.004, 0.014), bell=0.4,
                    density=[(0.0, 0.2), (0.3, 1.0), (1.0, 0.2)], name=name + "s")
    return mixdown(howl * 0.7, freeze * 0.22, crunch * 0.22, frost * 0.34,
                   pad_to(whump(0.3, f=(120.0, 50.0), name=name + "d"), dur) * 0.4)


# ═══════════════════════════ the first twelve ═══════════════════════════

# — Spaceman —
sound("atk_normal_bolt", lambda: bolt("neutral", N["A3"], dur=0.3), 0.50, "bolt", BOLT_M)
sound("atk_alien_tentacle", lambda: tentacle(0.4, size=0.9, alien=True, name="atn"), 0.60, "melee", dict(PUNCHY, width=0.55))


def gen_freeze_ray():
    """The Spaceman's freeze ray: a ray gun charging up its little rising whine and letting go,
    and the frost that comes out of it."""
    dur = 0.6
    whine = comb(bandpass(noise(0.12, "frn"), 800, 7000), glide([(0.0, 700.0), (1.0, 2600.0)], 0.12), fb=0.88, damp=0.3)
    whine = norm(whine * swell_in(0.12, 1.5))
    out = lay(dur, (0.0, whine, 0.4), (0.1, snap("hard", "frs"), 0.3), (0.1, frost_jet(dur - 0.1, "frj"), 0.9))
    return reverb(saturate(out, 1.4), decay=0.5, damp=5600, mix=0.22, name="fr", predelay=0.006)


sound("atk_freeze_ray", gen_freeze_ray, 0.58, "ability", dict(width=0.8, transient=1.0, tail=(0.6, 0.18), **BRIGHT),
      held=True)

# — Gladiator —
sound("atk_arena_axe", lambda: with_(swing("heavy", 0.36, name="gla") * 0.9,
                                     pad_to(scrape(0.12, lo=2000, hi=4800, bands=2, q=4.0, name="glas") * 0.22, 0.3)),
      0.62, "melee", SWEEP)
sound("atk_lasso", lambda: chain("rope", name="las"), 0.56, "melee", dict(width=0.7, transient=1.4, tail=(0.35, 0.14)))

# — Wraith —
sound("atk_soul_bolt", lambda: bolt("soul", N["C3"]), 0.56, "bolt", BOLT_M)


def gen_haunt():
    """The Wraith's Haunt: a slow, heavy ghost — the soul bolt lower and longer, a cold breath
    drawn IN before it (it is coming for someone), and the dead air it moves through."""
    dur = 0.5
    intake = svf(pink(0.14, "hti"), glide([(0.0, 500), (1.0, 2600)], 0.14), q=2.0, mode="bp") * swell_in(0.14, 2.2)
    body = bolt("soul", N["A2"], dur=0.42, name="hnt")
    low = halo((N["A2"], N["E3"]), 0.42, q=20, attack=0.05, t60=0.42, breath=0.3, wander=0.4, name="hnl")
    return lay(dur, (0.0, norm(intake), 0.35), (0.08, body, 0.9), (0.08, low, 0.3))


sound("atk_haunt_bolt", gen_haunt, 0.60, "ability", GHOST, held=True)

# — Wizard —
sound("atk_arcane_bolt", lambda: bolt("arcane", N["E4"]), 0.56, "bolt", BOLT_M)


def gen_fireball():
    """The Wizard's and the Pyromancer's Fireball: a slow, great mass of fire leaving the hand."""
    out = flame_gout(0.75, size=1.25, name="fb")
    return reverb(saturate(out, 1.8), decay=0.65, damp=4200, mix=0.24, name="fb", predelay=0.006)


sound("atk_fireball", gen_fireball, 0.74, "ability", dict(BIG, sub=(46, 0.4)))

# — Tidecaller —
def gen_splash():
    """The Tidecaller's trident of water: flung with a slosh, a slug of water tearing through
    the air and shedding drops."""
    dur = 0.4
    slosh = water(0.22, size=1.5, bubbles=6, foam=0.6, name="spw")
    fly = whoosh(dur, lo=600, hi=3000, end=500, peak=0.12, q=1.5, body=0.7, top=0.3, flutter=0.3,
                 rise=1.0, fall=2.0, name="spa")
    drops = bubbles(dur, count=14, f=(700, 2400), rise=(1.15, 1.6), size=(0.005, 0.016), span=(0.05, 0.8), name="spd")
    out = lay(dur, (0.0, slosh, 0.8), (0.0, fly, 0.6), (0.02, drops * arc(dur, 0.2, 1.0, 1.5), 0.25),
              (0.0, weight(100.0, 0.12, name="spt"), 0.35))
    return reverb(saturate(out, 1.6), decay=0.45, damp=4800, mix=0.22, name="spl")


sound("atk_splash", gen_splash, 0.60, "melee", dict(width=0.65, transient=1.4, tail=(0.45, 0.16)))


def gen_tidal_wave():
    """One great wave: it rears with a rising roar, the crest hisses over, and the mass of it
    rolls away."""
    dur = 0.95
    body = water(dur, size=0.75, bubbles=16, foam=0.85, name="tide")
    rear = whoosh(dur, lo=220, hi=1500, end=260, peak=0.35, q=1.1, body=1.0, top=0.25, flutter=0.3,
                  rise=1.8, fall=1.6, name="tdr")
    crest = highpass(noise(dur, "tdc"), 3200) * arc(dur, 0.42, 2.4, 2.0) * nlfo(dur, 18.0, 0.5, 1.0, "tdcf")
    roll = bandpass(pink(dur, "tdl"), 60, 300) * arc(dur, 0.4, 1.4, 1.2) * nlfo(dur, 9.0, 0.6, 1.0, "tdlf")
    out = mixdown(body * 0.8, rear * 0.7, norm(crest) * 0.24, norm(roll) * 0.25)
    return reverb(saturate(out, 1.6), decay=1.1, damp=3400, mix=0.28, name="tide", predelay=0.012, size=1.4)


sound("atk_tidal_wave", gen_tidal_wave, 0.82, "heavy", dict(HEAVY, sub=(48, 0.2)), held=True)


def gen_geyser():
    """A geyser going up: the ground's pressure letting go — a jet of steam, the column of
    water behind it, and the rumble under both."""
    dur = 0.65
    jet = gas(dur, band=(700, 6000), fizz=0.7, pitchfall=False, name="gey")
    column = water(dur * 0.8, size=1.1, bubbles=10, foam=0.5, name="geyw")
    up = whoosh(dur, lo=260, hi=2400, end=700, peak=0.2, q=1.3, body=0.9, top=0.2, flutter=0.3,
                rise=1.3, fall=1.3, name="geyu")
    out = lay(dur, (0.0, norm(jet), 0.7), (0.02, column, 0.6), (0.0, up, 0.55), (0.0, weight(78.0, 0.3, name="geyt"), 0.4))
    return reverb(saturate(out, 1.8), decay=0.7, damp=4600, mix=0.24, name="gey", predelay=0.006)


sound("atk_geyser_burst", gen_geyser, 0.70, "ability", dict(width=0.85, transient=1.0, tail=(0.7, 0.2)), held=True)

# — Soldier —
sound("atk_gunshot", lambda: gun("rifle"), 0.74, "melee", GUN_M)
sound("atk_grenade_lob", lambda: lobbed("grenade", 0.45), 0.60, "melee", dict(BIG, sub=(44, 0.3)))


def rocket(dur=0.7, size=1.0, name="rk"):
    """A rocket leaving the tube: the ignition's pop, the motor's roar tearing as it burns, and
    the whole of it going away from you."""
    ignite = mixdown(snap("hard", name + "i") * 0.8, whump(0.12, f=(230.0 / size, 90.0), name=name + "w") * 0.6)
    thrust = whoosh(dur, lo=500 / size, hi=2400 / size, end=900 / size, peak=0.25, q=1.4, body=0.8, top=0.3,
                    flutter=0.0, rise=1.2, fall=1.0, name=name + "t")
    thrust = leaving(thrust * nlfo(dur, 46.0, 0.45, 1.3, name + "tr"), 1.15, 0.82)
    scream = svf(noise(dur, name + "s"), glide([(0.0, 1800.0), (0.3, 3400.0), (1.0, 1500.0)], dur), q=3.0, mode="bp")
    scream = norm(scream * arc(dur, 0.3, 1.5, 1.2)) * 0.25
    out = lay(dur, (0.0, ignite, 0.9), (0.01, norm(thrust), 0.85), (0.01, scream, 1.0))
    return reverb(saturate(out, 2.0), decay=0.7, damp=4200, mix=0.24, name="rk", predelay=0.006)


sound("atk_rocket_launch", rocket, 0.78, "ability", dict(width=0.7, transient=1.2, sub=(46, 0.3), pan=(-0.3, 0.8),
                                                         tail=(0.7, 0.2)), held=True)

# — Raptor —
sound("atk_talon_swipe", lambda: with_(swing("talon", 0.3, name="rpt") * 0.9, wings(0.2, 9.0, 0.3, name="rptw")),
      0.56, "melee", SWEEP)


def gen_gust():
    """The Raptor's Gust — an eagle made of wind: two great wingbeats' worth of air thrown
    forward, and its cry in it."""
    base = wavefront("wind", 0.5, name="gst")
    return with_(base * 0.9, wings(0.4, 6.0, 0.5, (160, 1800), "gstw"),
                 pad_to(screech(0.3, f0=(1900.0, 2700.0, 1700.0), rough=0.5, breath_=0.5, name="gsts") * 0.16, 0.5))


sound("atk_gust", gen_gust, 0.60, "melee", dict(width=0.9, transient=0.7, pan=(-0.7, 0.7)))

# — Assassin —
sound("atk_throwing_star", lambda: thrown("shuriken", dur=0.28, name="ast"), 0.48, "melee", dict(FLY, **BRIGHT))
sound("atk_poison_dart", lambda: with_(shaft("dart", name="pd"),
                                       pad_to(gas(0.2, band=(1800, 7000), fizz=0.8, name="pdg") * 0.2, 0.3)),
      0.48, "tiny", FLY)

# — Warden —
sound("atk_chain_bolt", lambda: chain("iron"), 0.60, "melee", dict(width=0.8, transient=1.5, tail=(0.45, 0.16)))


def gen_lockdown():
    """The Warden's Lockdown: a chain flung to shackle — shorter and heavier than his bolt, and
    ending in the lock's clack."""
    base = chain("iron", 0.36, name="lkd")
    lock = mixdown(snap("hard", "lkk"), thud(150, 0.1, drive=1.6, name="lkt") * 0.8,
                   pad_to(rattle(0.05, count=4, band=(1500, 5000), q=8.0, cluster=1.0, name="lkr") * 0.6, 0.1))
    out = fit(base, n_of(0.5))
    at(out, 0.27, norm(lock) * 0.6)
    return out


sound("atk_lockdown", gen_lockdown, 0.62, "melee", dict(width=0.8, transient=1.5, tail=(0.45, 0.16)))

# — Samurai —
sound("atk_katana_slash", lambda: swing("katana", 0.3), 0.56, "melee", dict(SWEEP, **BRIGHT))


def gen_crescent_cut():
    """The Samurai's Crescent Cut: the blade's song, long and clean, and the cut itself going
    out wide — air parting in front of it, a breath of blossom in its wake. One stroke: nothing in
    it ticks."""
    dur = 0.55
    draw = steel_song(0.36, f=(4300.0, 5900.0, 7800.0), rise=1.07, q=55.0, peak=0.2, name="ccd")
    cut = wavefront("impact", 0.45, name="ccw")
    part = whoosh(0.4, lo=1200, hi=6500, end=900, peak=0.15, q=1.6, body=0.3, top=0.5, flutter=0.0,
                  rise=1.0, fall=2.6, name="ccp")
    blossom = shimmer(dur, 3000, 9000, density=[(0.0, 0.0), (0.3, 1.0), (1.0, 0.2)], name="ccb")
    return lay(dur, (0.0, draw, 0.32), (0.04, cut, 0.8), (0.04, part, 0.5), (0.1, blossom, 0.08),
               (0.04, weight(96.0, 0.14, name="ccwt"), 0.3))


sound("atk_crescent_cut", gen_crescent_cut, 0.66, "melee", dict(SWEEP, **BRIGHT))

# — Plague Doctor —
sound("atk_plague_bolt", lambda: with_(bolt("poison", N["E3"]), flies(0.36, "plf") * 0.18), 0.47, "bolt", BOLT_M)
sound("atk_miasma", lambda: with_(burst("gas", 0.8, name="mia"),
                                  voices(0.6, [(0.0, 130.0), (0.5, 110.0), (1.0, 92.0)], [(0.0, "oh"), (1.0, "oo")],
                                         count=3, spread=0.05, breath_=0.9, open_q=0.4, name="miav") * 0.16),
      0.64, "ability", dict(WIDE, tail=(1.0, 0.24)), held=True)
sound("atk_blight_bomb", lambda: with_(lobbed("flask", 0.55, name="bb"),
                                       pad_to(gas(0.5, band=(900, 6000), fizz=0.9, name="bbs") * 0.35, 0.55),
                                       glass_clink(3, "bbc") * 0.3),
      0.66, "ability", dict(BIG, sub=(42, 0.3)))

# — Vampire —
def gen_blood_fang():
    """The Vampire's bite: the fangs going in — a dry snap, the crunch, the give of flesh — and
    the draw of blood through them."""
    dur = 0.36
    bite = mixdown(snap("hard", "bfk") * 0.9, grains(0.07, rate=260, band=(500, 3000), length=(0.001, 0.004),
                                                   name="bfc") * 0.5)
    slurp = squelch(0.22, f=(500, 1500), q=3.0, lumps=4, name="bfq")
    lunge = whoosh(0.14, lo=500, hi=2600, end=600, peak=0.5, q=1.6, body=0.6, top=0.1, name="bfl")
    out = lay(dur, (0.0, lunge, 0.4), (0.05, bite, 0.9), (0.05, thud(130, 0.14, drive=1.8, name="bft"), 0.55),
              (0.13, slurp, 0.5))
    return reverb(saturate(out, 2.0), decay=0.4, damp=3800, mix=0.2, name="bf")


sound("atk_blood_fang", gen_blood_fang, 0.64, "melee", dict(PUNCHY, sub=(50, 0.25)))


def gen_bat_swarm():
    """A swarm of bats let out: leather wings beating over one another, and their squeaks —
    thin, very high, and each gone as soon as it comes."""
    dur = 0.6
    n = n_of(dur)
    flap = mixdown(wings(dur, 17.0, 1.0, (350, 4200), "bat1"), wings(dur, 13.5, 0.8, (300, 3600), "bat2"),
                   wings(dur, 21.0, 0.6, (500, 5000), "bat3"))
    sq = np.zeros(n)
    rng = np.random.default_rng(seed_of("batscr"))
    for i in range(9):
        t0 = rng.uniform(0.0, dur * 0.72)
        f0 = rng.uniform(4200, 7200)
        s = screech(rng.uniform(0.03, 0.07), f0=(f0, f0 * 1.1, f0 * 0.7), rough=0.3, breath_=0.3, name="bs%d" % i)
        at(sq, t0, s * rng.uniform(0.3, 0.8))
    out = mixdown(norm(flap) * 0.8, sq * 0.22)
    return reverb(saturate(out, 1.6), decay=0.6, damp=6000, mix=0.24, name="bat", predelay=0.006)


sound("atk_bat_swarm", gen_bat_swarm, 0.62, "ability", dict(width=1.0, transient=0.8, pan=(-0.8, 0.6)))


# ═══════════════════════════ elementals ═══════════════════════════

# — Pyromancer —
sound("atk_flame_bolt", lambda: bolt("fire", N["A3"]), 0.58, "bolt", BOLT_M)


def gen_combust():
    """The Pyromancer bursting into flame: fire catching all round at once — a whoomph with no
    throw in it — and burning on a moment."""
    dur = 0.6
    out = lay(dur, (0.0, whump(0.3, f=(180.0, 60.0), name="cmw"), 0.8),
              (0.0, fire(dur, low=110, roar=1.0, crackles=22, name="cmf") * arc(dur, 0.08, 1.0, 1.2), 0.75),
              (0.0, whoosh(0.3, lo=500, hi=3200, end=600, peak=0.1, q=1.1, body=0.6, top=0.3, name="cmo"), 0.5))
    return reverb(saturate(out, 1.8), decay=0.6, damp=4200, mix=0.24, name="cmb", predelay=0.008)


sound("atk_combust", gen_combust, 0.70, "ability", dict(BIG, sub=(46, 0.35)))

# — Cryomancer —
sound("atk_frost_shard", lambda: bolt("ice", N["E4"]), 0.62, "bolt", dict(BOLT_M, **BRIGHT))
sound("atk_beam_ice", lambda: reverb(saturate(mixdown(frost_jet(0.6, "ib"),
                                                     halo((N["E5"] * 2, N["A5"] * 2 * 1.5), 0.6, q=70, attack=0.03,
                                                          t60=0.6, breath=0.0, wander=0.3, name="ibh") * 0.2), 1.4),
                                     decay=0.6, damp=5600, mix=0.24, name="ib", predelay=0.006),
      0.58, "ability", dict(width=0.8, transient=1.0, tail=(0.6, 0.18), **BRIGHT), held=True)


def gen_frost_nova():
    """Frost bursting out all round the Cryomancer: the air cracking as it freezes, a ring of
    it going out, glass singing, and frost settling."""
    dur = 0.7
    crack_ = grains(0.12, rate=420, band=(1800, 11000), length=(0.0004, 0.003), density=[(0.0, 1.0), (1.0, 0.1)],
                    name="fnc")
    ring_ = whoosh(0.4, lo=900, hi=6000, end=1400, peak=0.12, q=1.4, body=0.5, top=0.5, flutter=0.2,
                   rise=1.0, fall=1.6, name="fnr")
    glass = halo((N["E6"], N["E6"] * 1.5, N["E6"] * 2, N["E6"] * 3), 0.5, q=80, attack=0.006, t60=0.4, breath=0.0,
                 name="fng")
    frost = shimmer(dur, 4500, 13000, rate=160, grain=(0.004, 0.014), bell=0.4,
                    density=[(0.0, 0.5), (0.2, 1.0), (1.0, 0.15)], name="fnf")
    out = lay(dur, (0.0, crack_, 0.36), (0.0, ring_, 0.7), (0.01, glass, 0.3), (0.0, frost, 0.38),
              (0.0, whump(0.25, f=(140.0, 60.0), name="fnw"), 0.5))
    return reverb(saturate(out, 1.4), decay=0.8, damp=6000, mix=0.26, name="fn", predelay=0.008)


sound("atk_frost_nova", gen_frost_nova, 0.66, "ability", dict(WIDE, tail=(0.8, 0.2), **BRIGHT))

# — Stormcaller —
sound("atk_lightning_crack", lambda: elec(0.5, size=1.0, colour=(600, 9000), forks=2, name="ltg"),
      0.74, "melee", dict(width=1.0, transient=1.6, sub=(48, 0.3), comp=(0.17, 5.0), tail=(0.9, 0.22), **SPARK))
sound("atk_chain_lightning", lambda: elec(0.62, size=0.9, colour=(900, 11000), forks=4, name="chl"),
      0.74, "ability", dict(width=1.0, transient=1.5, sub=(50, 0.25), comp=(0.18, 5.0), tail=(0.9, 0.22), **SPARK))


def gen_thunder_strike():
    """A thunderbolt: the strike, the clap that follows it — the sky splitting, not a bomb — and
    the thunder rolling away, grumbling as it goes."""
    dur = 1.3
    strike_ = elec(0.55, size=1.3, colour=(400, 8000), forks=3, name="thn")
    clap = explosion(dur=0.9, size=1.2, name="thb")
    roll = bandpass(pink(dur, "thr"), 50, 380) * arc(dur, 0.12, 1.0, 1.3) * nlfo(dur, 7.0, 0.25, 1.0, "thrl")
    return mixdown(pad_to(strike_, dur) * 0.85, pad_to(clap, dur) * 0.75, norm(roll) * 0.5)


sound("atk_thunder_strike", gen_thunder_strike, 0.90, "heavy", dict(HEAVY, sub=(38, 0.4), tail=(1.6, 0.28)))

# — Earthshaker —
def boulder(dur=0.5, size=1.0, name="bld"):
    """A boulder hurled: the heave of it, then rock turning over through the air — a grinding
    rumble with the knock of it coming round — going away."""
    n = n_of(dur)
    grind = saturate(bandpass(pink(dur, name + "g"), 180 / size, 1900 / size), 2.4)
    grind = leaving(grind * arc(dur, 0.15, 1.0, 1.3) * tumble(dur, 5.0 / size, name=name + "t"), 1.06, 0.9)
    rum = bandpass(pink(dur, name + "r"), 60 / size, 320 / size) * arc(dur, 0.2, 1.0, 1.4) * nlfo(dur, 10.0, 0.5, 1.0, name + "rl")
    knocks = np.zeros(n)
    rng = np.random.default_rng(seed_of(name + "k"))
    for _ in range(7):
        t0 = rng.uniform(0.02, dur * 0.75)
        kd = rng.uniform(0.07, 0.13)
        k = mixdown(thud(rng.uniform(110, 190) / size, kd, drive=2.0, name=name + "k%d" % rng.integers(1 << 20)),
                    bandpass(noise(kd, name + "n%d" % rng.integers(1 << 20)), 300, 2400) * exp_env(kd, decay=16.0) * 0.8)
        at(knocks, t0, k * rng.uniform(0.3, 0.7) * (1.0 - 0.5 * t0 / dur))
    out = lay(dur, (0.0, weight(84.0 / size ** 0.5, 0.18, name=name + "h"), 0.55), (0.0, norm(grind), 0.6),
              (0.0, norm(rum), 0.45), (0.0, knocks, 0.6))
    return reverb(saturate(out, 1.8), decay=0.95, damp=2800, mix=0.28, name="bldr", predelay=0.01, size=1.3)


sound("atk_boulder_rumble", boulder, 0.78, "melee", dict(HEAVY, sub=(40, 0.35), tail=(1.0, 0.22)))
sound("atk_seismic_root", lambda: mixdown(slam("earth", 0.75, name="ssr"),
                                          pad_to(burst("root", 0.6, name="ssrb") * 0.5, 0.75)),
      0.84, "heavy", dict(HEAVY, sub=(40, 0.35)))

# — Windwalker —
def gen_wind_blade():
    """The Windwalker's blade of wind: a thin, fast slice of air — a 'fwip' with a whistle on its
    edge — nothing behind it but more air."""
    dur = 0.3
    slice_ = whoosh(dur, lo=900, hi=5200, end=1100, peak=0.25, q=2.2, body=0.5, top=0.4, flutter=0.1,
                    rise=1.6, fall=2.6, name="wbs")
    whistle = svf(noise(dur, "wbw"), glide([(0.0, 2600.0), (0.25, 4400.0), (1.0, 2200.0)], dur), q=6.0, mode="bp")
    whistle = norm(whistle * arc(dur, 0.25, 1.6, 2.4)) * 0.2
    out = mixdown(slice_ * 0.85, whistle)
    return reverb(out, decay=0.4, damp=6000, mix=0.18, name="wb", predelay=0.004)


sound("atk_wind_blade", gen_wind_blade, 0.54, "melee", dict(width=0.9, transient=0.9, pan=(-0.6, 0.6), **BRIGHT))
sound("atk_gale", lambda: with_(wavefront("wind", 0.6, name="gal"), leaves(0.5, 110, "gall") * 0.2,
                                pad_to(weight(90.0, 0.2, name="galw") * 0.3, 0.6)),
      0.64, "ability", dict(width=0.95, transient=0.7, pan=(-0.7, 0.7)))

# — Magma Knight —
sound("atk_magma_ball", lambda: bolt("molten", N["F2"]), 0.66, "bolt", dict(BOLT_M, sub=(44, 0.25)))


def gen_eruption():
    """The Magma Knight's Eruption: rock splitting, and what was under it thrown out — fire,
    gas, and gouts of lava."""
    dur = 0.95
    split = slam("stone", 0.45, name="erc")
    spew = mixdown(flame_gout(dur * 0.7, size=1.4, name="ers") * 0.85,
                   gas(dur * 0.7, band=(300, 4200), fizz=0.6, pitchfall=False, name="erg") * 0.4)
    lava = squelch(0.4, f=(600, 140), q=2.4, lumps=6, name="erl")
    out = lay(dur, (0.0, split, 0.9), (0.07, spew, 0.9), (0.1, lava, 0.3))
    return saturate(out, 1.6)


sound("atk_eruption", gen_eruption, 0.84, "heavy", dict(HEAVY, sub=(40, 0.35)))

# — Frostbite —
sound("atk_frostbite_shard", lambda: with_(bolt("ice", N["A4"], dur=0.3, name="fbs") * 0.9,
                                           pad_to(grains(0.08, rate=110, band=(900, 4000), length=(0.002, 0.006),
                                                         density=[(0.0, 1.0), (1.0, 0.0)], name="fbk") * 0.22, 0.3)),
      0.60, "bolt", dict(TIGHT, **BRIGHT))
sound("atk_ice_spike", lambda: with_(shaft("spike", 0.34, name="isp"),
                                     pad_to(halo((N["E6"], N["E6"] * 1.5), 0.2, q=80, attack=0.004, t60=0.18,
                                                 breath=0.0, name="ispg") * 0.25, 0.34)),
      0.60, "melee", dict(FLY, **BRIGHT))

# — Sandstorm —
sound("atk_sand_shot", lambda: with_(bolt("sand", N["C3"]),
                                     pad_to(scrape(0.1, lo=1800, hi=4200, bands=2, q=3.5, name="sss") * 0.14, 0.2)),
      0.54, "bolt", BOLT_M)
sound("atk_sand_blast", lambda: with_(wavefront("sand", name="sbl"),
                                      grains(0.5, rate=700, band=(1200, 7000), length=(0.0002, 0.0012),
                                             density=[(0.0, 0.3), (0.3, 1.0), (1.0, 0.1)], name="sblg") * 0.3),
      0.60, "melee", dict(width=0.85, transient=0.9, pan=(-0.6, 0.6)))

# — Thornweaver —
sound("atk_thorn_shot", lambda: shaft("thorn"), 0.52, "melee", FLY)


def vine_lash(dur=0.5, woody=0.5, leafy=0.35, name="vl"):
    """A vine lashing out and catching: a whippy swish, the crack at its end, leaves thrashing,
    and the green wood of it creaking as it takes the strain."""
    swish = whoosh(0.26, lo=500, hi=3200, end=600, peak=0.6, q=1.8, body=0.6, top=0.2, flutter=0.15,
                   rise=2.0, fall=2.0, name=name + "s")
    out = lay(dur, (0.0, swish, 0.6), (0.0, leaves(dur, 140, name + "l"), leafy), (0.2, crack(0.025, name=name + "k"), 0.5),
              (0.22, creak(0.22, rate=(35.0, 80.0), body=(240.0, 540.0), q=3.5, name=name + "c"), woody))
    return reverb(saturate(out, 1.6), decay=0.4, damp=5000, mix=0.18, name="vl", predelay=0.004)


sound("atk_vine_whip", vine_lash, 0.72, "melee", dict(width=0.75, transient=1.4, tail=(0.4, 0.14)))
sound("atk_entangle", lambda: with_(burst("thorn", 0.7, name="ent"), leaves(0.6, 120, "entl") * 0.25),
      0.70, "ability", dict(BIG, sub=(46, 0.3)))

# — Cloudrunner —
def gen_cloud_puff():
    """The Cloudrunner's cloudlet: a soft, round puff of air — 'poof' — with nothing sharp in
    it, and a breath of mist behind."""
    dur = 0.34
    puff = whoosh(dur, lo=300, hi=1700, end=350, peak=0.14, q=1.0, body=1.0, top=0.1, flutter=0.2,
                  rise=1.0, fall=1.8, name="cpp")
    mist = highpass(pink(dur, "cpm"), 2400) * arc(dur, 0.2, 1.0, 1.6)
    out = lay(dur, (0.0, snap("soft", "cps"), 0.5), (0.0, puff, 0.85), (0.0, norm(mist), 0.14),
              (0.0, weight(104.0, 0.12, name="cpw"), 0.3))
    return reverb(out, decay=0.4, damp=4800, mix=0.2, name="cp", predelay=0.004)


sound("atk_cloud_puff", gen_cloud_puff, 0.54, "melee", dict(width=0.9, transient=0.8, pan=(-0.5, 0.5)))


def gen_hailstorm():
    """A storm cloud sent out: wind, thunder muttering inside it, and the hail it is full of
    rattling against itself."""
    dur = 0.8
    wind = wavefront("wind", 0.7, name="hlw")
    mutter = bandpass(pink(dur, "hlt"), 55, 320) * arc(dur, 0.3, 1.2, 1.3) * nlfo(dur, 8.0, 0.2, 1.0, "hltl")
    hail = grains(dur, rate=45, band=(1500, 5500), length=(0.002, 0.005), q=(0.8, 1.3),
                  density=[(0.0, 0.3), (0.3, 1.0), (1.0, 0.4)], name="hlh")
    return lay(dur, (0.0, wind, 0.8), (0.0, norm(mutter), 0.5), (0.05, hail, 0.16))


sound("atk_hailstorm", gen_hailstorm, 0.72, "ability", dict(WIDE, sub=(44, 0.3), tail=(0.9, 0.22)), held=True)

# — Inferno —
sound("atk_flame_wave", lambda: wavefront("flame"), 0.66, "melee", dict(width=0.85, transient=0.9,
                                                                         sub=(48, 0.3), pan=(-0.6, 0.6)))


def gen_inferno_blast():
    """The Inferno Blast: a great gout of fire thrown — and the living flame that throws it
    roaring with it."""
    dur = 0.95
    out = lay(dur, (0.0, flame_gout(dur, size=1.6, name="inf"), 0.95),
              (0.03, roar(0.7, f0=(100.0, 135.0, 80.0), vowels=("aw", "ah", "aw"), size=1.3, rough=0.8,
                          breath_=0.7, name="infv"), 0.26))
    return reverb(saturate(out, 1.7), decay=0.9, damp=3600, mix=0.26, name="inf", predelay=0.01, size=1.3)


sound("atk_inferno_blast", gen_inferno_blast, 0.86, "heavy", dict(HEAVY, sub=(40, 0.45)), held=True)


def vortex(dur=0.85, name="vx"):
    """Everything nearby falls inward: a band of air turning faster and faster as it tightens,
    and the pull rising with it. It never resolves."""
    core = burst("vortex", dur, name=name)
    whine = svf(noise(dur, name + "wh"), glide([(0.0, 400.0), (1.0, 1900.0)], dur), q=5.0, mode="bp")
    whine = norm(whine * swell_in(dur, 2.2)) * 0.14
    return mixdown(core, pad_to(whine, len(core) / SR))


sound("atk_fire_vortex", lambda: with_(vortex(0.85, "fvx") * 0.85,
                                       fire(0.8, low=100, roar=0.9, crackles=24, name="fvf") * swell_in(0.8, 1.2) * 0.5),
      0.76, "heavy", dict(width=1.0, transient=0.6, sub=(40, 0.35), tail=(1.3, 0.28)), held=True)

# — Glacier —
sound("atk_glacier_shard", lambda: with_(bolt("ice", 196.0, dur=0.44, name="gls") * 0.85,
                                         pad_to(grains(0.2, rate=90, band=(700, 3600), length=(0.002, 0.007),
                                                       density=[(0.0, 1.0), (1.0, 0.1)], name="glc") * 0.5, 0.44),
                                         pad_to(whump(0.2, f=(130.0, 55.0), name="glw") * 0.4, 0.44)),
      0.60, "bolt", dict(BOLT_M, **BRIGHT))


def gen_ricochet_shard():
    """The Glacier's Ricochet Shard: an ice shard thrown hard and spinning — it sings as it
    turns, a glassy note coming round and round — made to glance off things."""
    dur = 0.42
    base = bolt("ice", N["E4"], dur=dur, name="rcs")
    sing = halo((N["E6"] * 1.5, N["E6"] * 2), dur, q=60, peak=0.25, breath=0.1, name="rcg")
    sing = sing * tumble(dur, 14.0, name="rct")
    return with_(base * 0.9, sing * 0.1)


sound("atk_ricochet_shard", gen_ricochet_shard, 0.62, "bolt", dict(BOLT_M, **BRIGHT))

# — Mudslinger —
sound("atk_mud_glob", lambda: bolt("mud", N["F2"]), 0.50, "bolt", BOLT_M)
sound("atk_mud_bomb", lambda: lobbed("mud", 0.55), 0.62, "ability", dict(BIG, sub=(44, 0.3)))

# — Ember —
sound("atk_ember_shot", lambda: bolt("ember", N["E4"], dur=0.26), 0.62, "bolt", TIGHT)


def gen_ember_fan():
    """Three embers flicked off the fingers, one after another: tk-tk-tk and a spray of sparks."""
    dur = 0.36
    out = np.zeros(n_of(dur))
    for i in range(3):
        at(out, i * 0.035, bolt("ember", N["E4"] * (1.0 + 0.12 * i), dur=0.22, name="emf%d" % i), 0.8 - 0.12 * i)
    return out


sound("atk_ember_fan", gen_ember_fan, 0.62, "bolt", TIGHT)

# — Avalanche —
sound("atk_ice_chunk", lambda: with_(boulder(0.6, size=0.9, name="avb") * 0.6,
                                     pad_to(mixdown(crack(0.02, name="avk") * 0.5,
                                                    grains(0.34, rate=120, band=(700, 4200), length=(0.002, 0.008),
                                                           density=[(0.0, 1.0), (0.4, 0.5), (1.0, 0.1)], name="avi")), 0.5) * 0.6,
                                     shimmer(0.5, 4500, 12000, rate=120, grain=(0.004, 0.012), bell=0.4,
                                             density=[(0.0, 1.0), (0.3, 0.4), (1.0, 0.0)], name="avd") * 0.25),
      0.72, "melee", dict(BIG, sub=(46, 0.3), **BRIGHT))


def gen_avalanche():
    """An avalanche let go: a mass of snow and ice moving — a roar with no edge to it — and ice
    breaking up inside it."""
    dur = 1.0
    mass = whoosh(dur, lo=160, hi=900, end=180, peak=0.4, q=1.0, body=1.0, top=0.0, flutter=0.3,
                  rise=1.5, fall=1.4, name="avm")
    hiss = bandpass(pink(dur, "avh"), 900, 6000) * arc(dur, 0.4, 1.4, 1.4) * nlfo(dur, 15.0, 0.6, 1.0, "avhl")
    cracks = debris(dur, count=15, band=(600, 6000), decay=(10.0, 26.0), spread=(0.02, 0.85), name="avc") * 0.55
    crunch = grains(dur, rate=50, band=(400, 2400), length=(0.004, 0.012), density=[(0.0, 0.3), (0.4, 1.0), (1.0, 0.3)],
                    name="avx")
    out = saturate(mixdown(mass * 0.9, norm(hiss) * 0.3, cracks * 0.7, crunch * 0.4), 1.8)
    return reverb(out, decay=1.1, damp=3200, mix=0.28, name="av", predelay=0.012, size=1.35)


sound("atk_avalanche_crush", gen_avalanche, 0.84, "heavy", dict(HEAVY, sub=(40, 0.4)), held=True)
sound("atk_ice_quake", lambda: mixdown(slam("ice", 0.85), pad_to(gen_avalanche() * 0.4, 0.85)),
      0.84, "heavy", dict(HEAVY, sub=(42, 0.35), **BRIGHT))


# ═══════════════════════════ the undead ═══════════════════════════

def curse(dur=0.7, name="hex"):
    """A curse spoken: whispering mouths, a chord that will not sit right (two notes a semitone
    apart, sung by the air), and a rattle in the throat of whoever says it."""
    wrong = halo((N["A2"], N["A2"] * 1.0595, N["E3"], N["A3"] * 1.0595), dur, q=30, attack=0.06, t60=dur,
                 breath=0.2, wander=0.5, name=name + "d")
    whisper = breath(dur, [(0.0, "uh"), (0.3, "ee"), (0.6, "ah"), (1.0, "oo")], name=name + "w")
    whisper = whisper * arc(dur, 0.2, 0.8, 1.2) * nlfo(dur, 9.5, 0.4, 1.0, name + "wt")
    hiss = breath(dur, [(0.0, "ee"), (0.5, "eh"), (1.0, "ee")], band=(1500, 8000), name=name + "h") * arc(dur, 0.35, 1.0, 1.4)
    rattle_ = throat(glide([(0.0, 52.0), (1.0, 40.0)], dur), dur, rough=0.9, breath_=0.4, name=name + "r")
    rattle_ = highpass(lowpass(rattle_, 2200), 120.0) * arc(dur, 0.15, 1.0, 1.4)
    out = saturate(mixdown(wrong * 0.5, norm(whisper) * 0.55, norm(hiss) * 0.2, norm(rattle_) * 0.3), 1.8)
    out = delay_fx(out, 0.11, feedback=0.36, mix=0.22, taps=3)
    return reverb(out, decay=1.1, damp=3600, mix=0.30, name="hex", predelay=0.014)


def moans(dur, lo=95.0, hi=160.0, count=3, name="mn"):
    """The dead finding their voices: slow groans out of ruined throats, each on its own time."""
    out = np.zeros(n_of(dur))
    rng = np.random.default_rng(seed_of(name))
    for i in range(count):
        f = rng.uniform(lo, hi)
        d = dur * rng.uniform(0.6, 0.9)
        g = roar(d, f0=(f * 0.9, f, f * 0.74), vowels=("oo", "uh", "oo"), size=1.1, rough=0.55, breath_=0.7,
                 drive=1.8, peak=0.3, name=name + str(i))
        at(out, rng.uniform(0.0, dur * 0.2), g * rng.uniform(0.6, 1.0))
    return norm(out)


def dark_streak(dur=0.45, f0=98.0, name="shad"):
    """A shard of shadow: a dark streak of air, the death rattle moving in it, and a breath."""
    whoosh_ = whoosh(dur, lo=700, hi=2000, end=260, peak=0.08, q=1.7, body=0.7, top=0.0, flutter=0.3,
                     rise=1.0, fall=1.6, name=name)
    body = _dark_body(f0, dur, name + "bd")
    return mixdown(whoosh_ * 0.55, body * 0.7, pad_to(whump(0.2, f=(160.0, 60.0), name=name + "w"), dur) * 0.4)


def _dark_body(f0, dur, name):
    from sound_engines import _b_dark
    return norm(_b_dark(f0, dur, name))


# — Necromancer —
sound("atk_death_bolt", lambda: with_(bolt("dark", N["A2"]),
                                      grains(0.4, rate=30, band=(1200, 5000), length=(0.001, 0.003),
                                             density=[(0.0, 1.0), (1.0, 0.2)], name="ncf") * 0.1),
      0.62, "bolt", BOLT_M)


def gen_raise_dead():
    """The dead raised: the ground giving, and what was under it groaning as it comes up — a
    chorus of ruined throats, not a filtered drone."""
    dur = 1.1
    earth = debris(dur, count=13, band=(140, 1400), decay=(12.0, 26.0), spread=(0.0, 0.7), name="rddirt")
    heave = bandpass(pink(dur, "rdh"), 70, 400) * arc(dur, 0.2, 1.2, 1.6) * nlfo(dur, 9.0, 0.5, 1.0, "rdhl")
    dead = moans(dur, 90.0, 150.0, count=4, name="rdm")
    cold = halo((N["A2"], N["E3"], N["A3"]), dur, q=22, attack=0.1, t60=dur, breath=0.3, wander=0.4, name="rdc")
    out = saturate(mixdown(dead * 0.6, earth * 0.4, norm(heave) * 0.35, cold * 0.25), 1.6)
    return reverb(out, decay=1.4, damp=3200, mix=0.34, name="rd", predelay=0.018, size=1.5)


sound("atk_raise_dead", gen_raise_dead, 0.70, "heavy", dict(GHOST, tail=(1.7, 0.3)), held=True)


def gen_soul_harvest():
    """Souls pulled out of everyone near: the air drawn in toward the Necromancer, and thin
    voices rising as they are taken."""
    dur = 1.0
    pull = burst("soul", dur, name="sh")
    taken = np.zeros(n_of(dur))
    rng = np.random.default_rng(seed_of("shv"))
    for i in range(4):
        vd = rng.uniform(0.3, 0.5)
        f = rng.uniform(190, 400)
        v = voices(vd, [(0.0, f), (1.0, f * 2.0)], [(0.0, "oo"), (1.0, "ee")], count=2, spread=0.03, breath_=0.7,
                   open_q=0.4, amp_keys=((0.0, 0.0), (0.2, 0.8), (1.0, 0.0)), name="shv%d" % i)
        at(taken, rng.uniform(0.0, dur * 0.5), v * rng.uniform(0.5, 1.0))
    return mixdown(pull, pad_to(taken * 0.2, len(pull) / SR))


sound("atk_soul_harvest", gen_soul_harvest, 0.74, "heavy", dict(GHOST, tail=(1.5, 0.3)), held=True)

# — Skeleton King —
sound("atk_bone_axe", lambda: with_(swing("bone", 0.36, name="bax") * 0.9, pad_to(bone_clack(0.16, 30, "baxc") * 0.2, 0.3)),
      0.60, "melee", SWEEP)
sound("atk_bone_boomerang", lambda: with_(thrown("bone", dur=0.55, name="bbm"),
                                          pad_to(thrown("bone", dur=0.3, name="bbm2") * 0.35, 0.55)),
      0.60, "melee", SWEEP)


def gen_bone_cage():
    """The Bone Cage: a bone flung, and a cage of them springing up — a run of dry clacks, fast,
    closing with a knock."""
    dur = 0.6
    throw = thrown("bone", dur=0.36, name="bcg")
    spring = grains(0.26, rate=150, band=(600, 3200), length=(0.002, 0.007), q=(0.8, 1.2),
                    density=[(0.0, 0.4), (0.5, 1.0), (1.0, 0.6)], name="bcs")
    out = fit(throw, n_of(dur))
    at(out, 0.2, spring * 0.6)
    at(out, 0.44, thud(140, 0.12, drive=1.6, name="bct") * 0.5)
    return out


sound("atk_bone_cage", gen_bone_cage, 0.62, "ability", SWEEP)

# — Banshee —
def keen(dur, f0, name, count=3, breath_=0.5):
    """A banshee's voice: several throats on one cry, a little apart, with the air tearing
    round them."""
    v = voices(dur, f0, [(0.0, "ah"), (0.4, "ee"), (1.0, "eh")], count=count, spread=0.03, stagger=0.012,
               breath_=breath_, amp_keys=((0.0, 0.0), (0.08, 1.0), (0.55, 0.7), (1.0, 0.0)), name=name)
    tear = whoosh(dur, lo=900, hi=5000, end=1200, peak=0.15, q=1.6, body=0.3, top=0.4, flutter=0.3, name=name + "a")
    return mixdown(fit(v, n_of(dur)) * 0.62, tear * 0.6)


sound("atk_banshee_cry", lambda: reverb(saturate(keen(0.42, [(0.0, 560.0), (0.18, 1100.0), (1.0, 500.0)], "bcry"), 1.6),
                                        decay=0.75, damp=4800, mix=0.26, name="bcry", predelay=0.008),
      0.60, "melee", dict(GHOST, tail=(1.1, 0.24)))


def gen_echo_bolt():
    """The Banshee's Echo Bolt: a cry that arrives, and arrives again, and again, fainter."""
    dur = 0.5
    v = keen(0.2, [(0.0, 640.0), (1.0, 400.0)], "eb", count=2)
    out = lay(dur, (0.0, v, 0.9), (0.13, lowpass(v, 3200), 0.45), (0.25, lowpass(v, 1800), 0.2),
              (0.0, weight(104.0, 0.12, name="ebw"), 0.3))
    return reverb(out, decay=0.9, damp=4400, mix=0.30, name="eb", predelay=0.012)


sound("atk_echo_bolt", gen_echo_bolt, 0.60, "ability", dict(GHOST, pingpong=(0.13, 0.3, 0.2)), held=True)


def gen_wail():
    """The Banshee's Wail: a voice that should not still be a voice — four of them — rising,
    held, and falling away."""
    dur = 1.0
    v = voices(dur, [(0.0, 380.0), (0.22, 940.0), (0.55, 780.0), (1.0, 340.0)],
               [(0.0, "ah"), (0.35, "eh"), (0.7, "uh"), (1.0, "oo")], count=4, spread=0.04, stagger=0.02,
               breath_=0.4, amp_keys=((0.0, 0.0), (0.12, 1.0), (0.68, 0.8), (1.0, 0.0)), name="wail")
    under = voices(dur, [(0.0, 208.0), (0.3, 590.0), (1.0, 190.0)], [(0.0, "oo"), (0.5, "uh"), (1.0, "oo")],
                   count=2, spread=0.05, breath_=0.6, name="wail2")
    tear = whoosh(dur, lo=800, hi=5200, end=900, peak=0.3, q=1.4, body=0.3, top=0.4, flutter=0.4, name="wailb")
    out = mixdown(fit(v, n_of(dur)) * 0.8, fit(under, n_of(dur)) * 0.3, tear * 0.3)
    return reverb(out, decay=1.6, damp=4200, mix=0.34, name="wail", predelay=0.024, size=1.7)


sound("atk_wail_shriek", gen_wail, 0.74, "heavy", dict(GHOST, pingpong=(0.19, 0.36, 0.24), tail=(1.6, 0.3)), held=True)

# — Lich —
sound("atk_leech_bolt", lambda: bolt("leech", N["C3"], dur=0.42),
      0.58, "bolt", BOLT_M)
sound("atk_grave_ice", lambda: with_(bolt("ice", N["A3"], dur=0.42, name="gri") * 0.85,
                                     breath(0.4, [(0.0, "oo"), (1.0, "uh")], band=(200, 2600), name="grw") *
                                     arc(0.4, 0.2, 1.0, 1.5) * 0.3),
      0.60, "bolt", dict(BOLT_M, **BRIGHT))


def gen_soul_drain():
    """The Lich draining a soul: the whirlpool, and the soul in it — thin voices drawn out of
    someone, whispering as they go."""
    dur = 0.6
    base = siphon(dur, "sdr")
    soul = voices(dur, [(0.0, 420.0), (0.5, 520.0), (1.0, 300.0)], [(0.0, "ee"), (0.5, "ah"), (1.0, "oo")],
                  count=2, spread=0.03, breath_=0.8, open_q=0.4,
                  amp_keys=((0.0, 0.0), (0.3, 0.8), (0.8, 0.6), (1.0, 0.0)), name="sdv")
    whisper = breath(dur, [(0.0, "oo"), (0.5, "ee"), (1.0, "uh")], name="sdw")
    out = mixdown(base * 0.8, fit(soul, n_of(dur)) * 0.26, whisper * 0.3)
    return reverb(saturate(out, 1.4), decay=0.8, damp=4200, mix=0.26, name="sd", predelay=0.01)


sound("atk_soul_drain", gen_soul_drain, 0.60, "ability", dict(width=0.9, transient=0.7, tail=(0.8, 0.22)), held=True)

# — Ghoul —
sound("atk_ghoul_rake", lambda: with_(swing("wet", 0.32, name="ghr") * 0.9,
                                      pad_to(squelch(0.2, f=(1300, 320), q=2.6, lumps=3, name="ghw") * 0.35, 0.32),
                                      snarl(150.0, 0.22, rough=0.9, size=0.9, name="ghv") * 0.28),
      0.62, "melee", SWEEP)


def chomp(name, size=1.0):
    """Jaws snapping shut: teeth meeting (a hard dry clack, no ring), bone crunching under
    them, and the wet of the mouth."""
    out = np.zeros(n_of(0.3))
    clack = mixdown(click(0.006, fc=1600 / size, name=name + "1", shape=1.1),
                    bandpass(noise(0.025, name + "1n"), 700 / size, 3200 / size) * exp_env(0.025, attack=0.0004, decay=9.0) * 0.7)
    crunch = grains(0.1, rate=300, band=(350 / size, 2400 / size), length=(0.001, 0.005), name=name + "2")
    at(out, 0.0, norm(clack) * 0.9)
    at(out, 0.004, crunch * 0.55)
    at(out, 0.0, thud(105 / size, 0.16, drive=2.0, name=name + "3") * 0.7)
    at(out, 0.03, squelch(0.14, f=(1100 / size, 300 / size), q=2.8, lumps=2, name=name + "4") * 0.35)
    return out


def gen_devour():
    """The Ghoul's Devour: a rasping snarl, and a bite that takes a mouthful."""
    dur = 0.5
    out = pad_to(snarl(120.0, dur, rough=0.9, size=0.95, name="dev") * 0.6, dur)
    at(out, 0.06, chomp("dv") * 0.9)
    at(out, 0.2, squelch(0.2, f=(900, 250), q=3.0, lumps=4, name="dvq") * 0.4)
    return reverb(saturate(out, 2.0), decay=0.5, damp=3400, mix=0.22, name="dev", predelay=0.006)


sound("atk_devour_bite", gen_devour, 0.70, "ability", dict(PUNCHY, sub=(46, 0.3)))

# — Reaper —
def gen_scythe():
    """The Reaper's scythe: a long, slow blade through the air, the cold it leaves, and a
    breath that is not his."""
    dur = 0.5
    base = swing("scythe", dur, name="scy")
    cold = halo((N["A2"], N["E3"]), dur, q=20, attack=0.08, t60=dur, breath=0.4, name="scyc")
    sigh = breath(dur, [(0.0, "aw"), (1.0, "oo")], band=(200, 2600), name="scyg") * arc(dur, 0.35, 1.2, 1.4)
    return with_(base * 0.9, cold * 0.22, norm(sigh) * 0.25)


sound("atk_scythe_swing", gen_scythe, 0.62, "melee", dict(SWEEP, tail=(0.7, 0.2)))


def gen_reap():
    """Reap: the scythe swung wide, and the souls it drags behind it crying out as they come."""
    dur = 0.7
    base = gen_scythe()
    souls = np.zeros(n_of(dur))
    rng = np.random.default_rng(seed_of("reap"))
    for i in range(3):
        f = rng.uniform(280, 520)
        v = voices(0.3, [(0.0, f), (1.0, f * 1.7)], [(0.0, "oo"), (1.0, "ee")], count=2, spread=0.03, breath_=0.7,
                   amp_keys=((0.0, 0.0), (0.2, 0.8), (1.0, 0.0)), name="rp%d" % i)
        at(souls, rng.uniform(0.1, dur * 0.5), v * rng.uniform(0.5, 0.9))
    souls = delay_fx(souls * 0.3, 0.08, feedback=0.4, mix=0.3, taps=3)
    return reverb(mixdown(pad_to(base, dur) * 0.85, pad_to(souls, dur)), decay=1.0, damp=4200,
                  mix=0.28, name="rp", predelay=0.012)


sound("atk_reap_slash", gen_reap, 0.68, "ability", dict(width=0.9, transient=1.2, pan=(-0.5, 0.5), tail=(1.0, 0.22)))

# — Shade —
sound("atk_shadow_bolt", lambda: reverb(saturate(mixdown(dark_streak(0.42, 98.0, "shd"),
                                                         breath(0.3, [(0.0, "ee"), (1.0, "eh")], band=(1200, 7000),
                                                                name="shg") * arc(0.3, 0.2, 1.0, 1.6) * 0.2), 1.7),
                                        decay=0.85, damp=2800, mix=0.30, name="shad", predelay=0.01),
      0.60, "bolt", dict(WIDE, transient=1.0, tail=(0.8, 0.2)))


def gen_shadow_bind():
    """Shadow Bind: the Shade's bolt, with what it will do when it lands already in it —
    tendrils drawing tight, a creak and a snap."""
    dur = 0.55
    out = fit(dark_streak(0.45, 87.0, "shb"), n_of(dur))
    at(out, 0.2, creak(0.2, rate=(60.0, 140.0), body=(300.0, 700.0), q=3.5, name="shbc") * 0.4)
    at(out, 0.38, crack(0.02, name="shbk") * 0.3)
    return reverb(saturate(out, 1.7), decay=0.85, damp=2800, mix=0.30, name="shad", predelay=0.01)


sound("atk_shadow_bind", gen_shadow_bind, 0.64, "ability", dict(WIDE, tail=(1.0, 0.22)))

# — Revenant —
sound("atk_drain_blade", lambda: with_(swing("cursed", 0.36, name="drb") * 0.9,
                                       pad_to(heartbeat(0.12, 0.3, f0=78.0, name="drh") * 0.3, 0.36)),
      0.64, "melee", dict(SWEEP, tail=(0.7, 0.22)))


def gen_life_drain():
    """Blood and life drawn out (the Revenant's): the whirlpool, wet, with a heart labouring
    in it."""
    dur = 0.6
    base = siphon(dur, "ldr")
    wet = squelch(dur, f=(700, 1600), q=2.6, lumps=5, name="ldq")
    beats = np.zeros(n_of(dur))
    beat = heartbeat(0.13, 0.3, f0=76.0, name="ldh1")
    at(beats, 0.02, beat)
    at(beats, 0.3, beat * 0.8)
    out = mixdown(base * 0.75, wet * 0.3, beats * 0.5)
    return reverb(saturate(out, 1.5), decay=0.6, damp=3800, mix=0.22, name="ld", predelay=0.008)


sound("atk_life_drain", gen_life_drain, 0.60, "ability", dict(width=0.8, transient=0.9, tail=(0.6, 0.18)), held=True)

# — Gravedigger —
sound("atk_shovel_swing", lambda: with_(swing("heavy", 0.38, name="shv") * 0.9,
                                        pad_to(grains(0.2, rate=150, band=(250, 2200), length=(0.002, 0.008),
                                                      density=[(0.0, 0.3), (0.3, 1.0), (1.0, 0.0)], name="shvd") * 0.3, 0.38),
                                        pad_to(scrape(0.08, lo=1400, hi=3200, bands=2, q=3.0, name="shvs") * 0.16, 0.2)),
      0.62, "melee", SWEEP)


def gen_grasping_dead():
    """The Grasping Dead: hands clawing up out of the ground — earth breaking, fingers
    scrabbling at it, and the groan of whoever they belong to."""
    dur = 0.8
    earth = debris(dur, count=14, band=(160, 1600), decay=(12.0, 26.0), spread=(0.0, 0.6), name="gdd")
    scrabble = rip(0.4, rate=(90.0, 30.0), band=(400, 3200), name="gds")
    groan = moans(dur * 0.8, 100.0, 150.0, count=2, name="gdm")
    out = lay(dur, (0.0, thud(104, 0.25, drive=1.8, name="gdt"), 0.4), (0.0, earth, 0.6), (0.08, scrabble, 0.45),
              (0.05, groan, 0.45))
    return reverb(saturate(out, 1.7), decay=1.0, damp=3200, mix=0.28, name="gd", predelay=0.014)


sound("atk_grasping_dead", gen_grasping_dead, 0.70, "ability", dict(BIG, sub=None, tail=(1.1, 0.24)))

# — Dullahan —
sound("atk_cursed_blade", lambda: swing("cursed", 0.4, name="cbl"), 0.64, "melee", dict(SWEEP, tail=(0.8, 0.22)))
sound("atk_boomerang_blade", lambda: with_(thrown("cursed", dur=0.55, name="bmr"),
                                           pad_to(thrown("cursed", dur=0.3, name="bmr2") * 0.35, 0.55)),
      0.64, "ability", dict(SWEEP, tail=(0.8, 0.22)))

# — Phantom —
sound("atk_phantom_blade", lambda: with_(bolt("soul", N["A2"], dur=0.4, name="phb") * 0.85,
                                         pad_to(edge("shing", 0.16, "phbs") * arc(0.16, 0.2, 1.0, 1.6) * 0.2, 0.3)),
      0.58, "bolt", BOLT_M)
sound("atk_shadow_haunt", lambda: reverb(saturate(mixdown(dark_streak(0.45, 82.0, "shh"),
                                                          norm(_soul(0.45, "shhs")) * 0.4), 1.6),
                                         decay=0.9, damp=3000, mix=0.3, name="shh", predelay=0.012),
      0.62, "ability", GHOST)


def _soul(dur, name):
    from sound_engines import _b_soul
    return _b_soul(N["A2"], dur, name)


# — Mummy —
def gen_scarab():
    """The Mummy's scarab: a beetle the size of a fist — its wing-cases clicking open, the low
    drone of its wings — and the dust of the tomb it came out of."""
    dur = 0.4
    drone = buzz(dur, rate=110, band=(500, 4200), bugs=3, name="scb") * arc(dur, 0.12, 1.0, 1.4)
    out = lay(dur, (0.0, chitin(0.08, (1800, 7000), 0.9, "scc"), 0.5), (0.0, norm(drone), 0.6),
              (0.0, whoosh(dur, lo=500, hi=2200, end=450, peak=0.12, q=1.4, body=0.6, top=0.1, name="sca"), 0.5),
              (0.0, dust(dur, "scd"), 0.3), (0.0, weight(110.0, 0.12, name="scw"), 0.28))
    return reverb(saturate(out, 1.4), decay=0.5, damp=3800, mix=0.2, name="scar", predelay=0.006)


sound("atk_scarab_shot", gen_scarab, 0.54, "bolt", BOLT_M)


def gen_bandage_whip():
    """The Mummy's bandage: old linen unwinding in a run of flaps, the snap as it goes taut,
    and dust coming off it."""
    dur = 0.5
    flap = rip(dur * 0.7, rate=(70.0, 24.0), band=(300, 3200), name="bwf")
    swish = whoosh(0.3, lo=400, hi=2600, end=500, peak=0.5, q=1.6, body=0.6, top=0.1, flutter=0.3, name="bws")
    out = lay(dur, (0.0, swish, 0.5), (0.0, flap, 0.55), (dur * 0.52, crack(0.022, name="bwk"), 0.5),
              (dur * 0.52, thud(130, 0.1, drive=1.4, name="bwt"), 0.35), (0.0, dust(dur, "bwd"), 0.25))
    return reverb(saturate(out, 1.6), decay=0.45, damp=4200, mix=0.2, name="bw", predelay=0.004)


sound("atk_bandage_whip", gen_bandage_whip, 0.72, "melee", dict(width=0.75, transient=1.3, tail=(0.45, 0.15)))
sound("atk_mummy_curse", lambda: with_(curse(0.7, "mcr"), dust(0.6, "mcd") * 0.3), 0.62, "ability",
      dict(GHOST, pingpong=(0.11, 0.36, 0.22)), held=True)

# — Death Knight —
sound("atk_deathknight_blade", lambda: with_(swing("cursed", 0.4, name="dkb") * 0.9,
                                             pad_to(debris(0.3, count=8, band=(1200, 6000), decay=(30.0, 60.0), name="dkp") * 0.3, 0.4),
                                             shimmer(0.4, 4500, 12000, rate=100, grain=(0.004, 0.012), bell=0.4,
                                                     density=[(0.0, 0.5), (0.3, 1.0), (1.0, 0.0)], name="dkf") * 0.2),
      0.66, "melee", dict(SWEEP, tail=(0.7, 0.22)))


def gen_death_grip():
    """Death Grip: a chain of death thrown to take hold — the links, cold and heavy, a dark
    breath running down them, and the grip closing."""
    dur = 0.55
    base = chain("iron", 0.42, name="dgr")
    out = fit(base, n_of(dur))
    at(out, 0.0, dark_streak(0.4, 82.0, "dgd") * 0.5)
    at(out, 0.3, mixdown(thud(100, 0.16, drive=1.8, name="dgt"), snap("soft", "dgs") * 0.5) * 0.5)
    return reverb(saturate(out, 1.6), decay=0.7, damp=3200, mix=0.24, name="dg", predelay=0.008)


sound("atk_death_grip", gen_death_grip, 0.66, "ability", dict(width=0.85, transient=1.3, tail=(0.7, 0.2)))

# — Shadowfiend —
sound("atk_fiend_eye", lambda: with_(bolt("dark", N["A2"] * 0.89, dur=0.42, name="fey") * 0.9,
                                     wings(0.36, 15.0, 0.4, (400, 3600), "feyw"),
                                     snake_hiss(0.3, "feyh") * 0.14),
      0.62, "bolt", BOLT_M)
sound("atk_shadow_burst", lambda: mixdown(slam("dark", 0.85, name="sbs"), pad_to(burst("vortex", 0.7, name="sbsv") * 0.5, 0.85),
                                          pad_to(moans(0.6, 80.0, 120.0, count=2, name="sbsm") * 0.3, 0.85)),
      0.80, "heavy", dict(HEAVY, sub=(38, 0.4)))


def gen_void_maw():
    """The Shadowfiend's Void Pull: a maw opened in the air — the pull of it, and the growl in
    its throat, and teeth."""
    dur = 0.85
    out = fit(vortex(dur, "vmw") * 0.85, n_of(dur + 0.2))
    at(out, 0.1, roar(0.7, f0=(70.0, 92.0, 58.0), vowels=("aw", "oh", "oo"), size=1.5, rough=0.85, breath_=0.5,
                      peak=0.5, name="vmr") * 0.4)
    at(out, 0.62, chomp("vmc", size=1.3) * 0.35)
    return out


sound("atk_void_maw", gen_void_maw, 0.76, "heavy", dict(width=1.0, transient=0.6, sub=(38, 0.4), tail=(1.3, 0.28)),
      held=True)

# — Poltergeist —
def crockery(dur=0.2, count=7, name="crk"):
    """China knocking on china: little dull-bright clinks, a cupboard's worth."""
    return rattle(dur, count=count, band=(1700, 5200), q=7.0, cluster=0.8, name=name)


def gen_polter_hurl(dur=0.42, heavy=False, name="plt"):
    """The Poltergeist throwing the crockery: nothing in the room but the room's own things,
    flung — the rush of one going, the rest rattling on the shelf, and the cold breath of
    whatever threw it."""
    lo = 300 if heavy else 500
    fly = whoosh(dur, lo=lo, hi=lo * 6, end=lo, peak=0.2, q=1.5, body=0.8 if heavy else 0.5, top=0.2, flutter=0.2,
                 rise=1.2, fall=2.0, name=name + "a") * tumble(dur, 9.0 if not heavy else 6.0, name=name + "t")
    ghost = breath(dur, [(0.0, "oo"), (0.5, "uh"), (1.0, "oo")], band=(250, 3600), name=name + "g") * arc(dur, 0.25, 1.0, 1.5)
    out = lay(dur, (0.0, norm(fly), 0.75), (0.0, crockery(0.16, 4 if not heavy else 3, name + "c"), 0.3),
              (0.0, norm(ghost), 0.3), (0.0, weight(100.0 if not heavy else 84.0, 0.14, name=name + "w"), 0.3 if not heavy else 0.5))
    if heavy:
        at(out, 0.0, creak(0.14, rate=(60.0, 120.0), body=(300.0, 800.0), q=3.0, name=name + "k") * 0.3)
    return reverb(saturate(out, 1.5), decay=0.6, damp=4800, mix=0.22, name="plt", predelay=0.006)


sound("atk_poltergeist_hurl", gen_polter_hurl, 0.60, "melee", dict(width=0.85, transient=1.5, tail=(0.6, 0.2)))
sound("atk_fling", lambda: gen_polter_hurl(0.5, heavy=True, name="flg"), 0.68, "ability",
      dict(width=0.85, transient=1.5, sub=(48, 0.3), tail=(0.6, 0.2)))


# ═══════════════════════════ the medieval ═══════════════════════════

# — Paladin —
sound("atk_holy_blade", lambda: swing("holy", 0.36, name="hbl"), 0.62, "melee", dict(SWEEP, tail=(0.9, 0.24)))


def gen_judgment():
    """The Paladin's Judgment: a lance of holy light thrown — it leaves like a spear, and
    sings as it goes."""
    dur = 0.5
    lance = whoosh(dur, lo=900, hi=4800, end=900, peak=0.1, q=1.6, body=0.6, top=0.4, flutter=0.1,
                   rise=1.0, fall=2.0, name="jdl")
    light = radiance(dur, notes=(N["A3"], N["E4"], N["A4"]), name="jdr")
    out = lay(dur, (0.0, snap("hard", "jds"), 0.3), (0.0, lance, 0.6), (0.0, light, 0.8),
              (0.0, weight(98.0, 0.14, name="jdw"), 0.4))
    return reverb(out, decay=0.8, damp=6400, mix=0.24, name="jd", predelay=0.01)


sound("atk_judgment", gen_judgment, 0.70, "ability", dict(BIG, sub=(50, 0.2), comp=(0.32, 2.4), **BRIGHT))

# — Ranger —
sound("atk_arrow_shot", lambda: shaft("arrow"), 0.54, "melee", FLY)
sound("atk_poison_arrow", lambda: mixdown(shaft("arrow", name="pa"),
                                          pad_to(gas(0.28, band=(900, 6200), fizz=0.9, name="pag") * 0.3, 0.3)),
      0.56, "melee", FLY)


def gen_multi_shot():
    """Three arrows off one draw: the string three times, a breath apart."""
    dur = 0.4
    out = np.zeros(n_of(dur))
    for i in range(3):
        at(out, i * 0.032, shaft("arrow", 0.28, name="ms%d" % i), 0.9 - 0.12 * i)
    return out


sound("atk_multi_shot", gen_multi_shot, 0.58, "melee", FLY)

# — Berserker —
def gen_berserk_axe():
    """The Berserker's axes: two of them, one after the other — he does not wait for the first
    to finish."""
    dur = 0.4
    out = np.zeros(n_of(dur))
    at(out, 0.0, swing("heavy", 0.28, name="bza"), 0.9)
    at(out, 0.1, swing("heavy", 0.28, name="bzb"), 0.75)
    return out


sound("atk_berserk_axe", gen_berserk_axe, 0.64, "melee", SWEEP)


def gen_axe_spin():
    """Axe Spin: the Berserker whirling — the axes coming round and round, four times, faster
    than they should, each pass a cut."""
    dur = 0.7
    spin = tumble(dur, 6.5, slow=1.15, name="axs")
    rush = whoosh(dur, lo=380, hi=2600, end=500, peak=0.4, q=1.6, body=0.9, top=0.2, flutter=0.1,
                  rise=1.2, fall=1.2, name="axr") * spin
    cut = scrape(dur, lo=2200, hi=5200, bands=2, q=4.0, name="axc") * spin ** 2
    out = lay(dur, (0.0, norm(rush), 0.85), (0.0, norm(cut), 0.2), (0.0, weight(96.0, 0.2, name="axw"), 0.4))
    return reverb(saturate(out, 1.7), decay=0.5, damp=5200, mix=0.2, name="axs", predelay=0.006)


sound("atk_axe_spin", gen_axe_spin, 0.70, "ability", dict(width=0.95, transient=1.2, pan=(-0.7, 0.7), tail=(0.5, 0.16)))

# — Crusader —
sound("atk_crusader_blade", lambda: with_(swing("steel", 0.36, name="crb") * 0.9,
                                          pad_to(weight(92.0, 0.16, name="crw") * 0.25, 0.36)),
      0.62, "melee", SWEEP)
sound("atk_shield_bash", lambda: slam("shield", 0.7), 0.78, "ability", dict(BIG, sub=(46, 0.3)))

# — Druid —
sound("atk_druid_thorn", lambda: with_(shaft("thorn", 0.3, name="drt"), leaves(0.26, 90, "drtl") * 0.22), 0.52, "melee", FLY)
sound("atk_druid_vine", lambda: vine_lash(0.5, woody=0.35, leafy=0.5, name="drv"), 0.72, "melee",
      dict(width=0.75, transient=1.4, tail=(0.4, 0.14)))

# — Bard —
def strum(notes, dur=0.5, gap=0.014, t60=0.55, name="lut"):
    """A chord strummed on gut strings, low to high."""
    out = np.zeros(n_of(dur))
    for i, f in enumerate(notes):
        at(out, i * gap, pluck(f, dur, t60=t60, bright=0.6, pick=0.22, name=name + str(i)) * (0.9 - 0.1 * i))
    return norm(out)


sound("atk_lute_chord", lambda: with_(strum((220.0, 261.63, 329.63, 440.0)), wavefront("sonic", 0.4, name="lutw") * 0.3),
      0.58, "melee", dict(width=0.8, transient=1.2, tail=(0.7, 0.22)))


def gen_lullaby():
    """The Bard's Lullaby: three slow notes falling, plucked soft, with a hum under them and
    the hush they leave."""
    dur = 0.8
    out = np.zeros(n_of(dur))
    for i, f in enumerate((N["E5"], N["C5"], N["A4"])):
        at(out, i * 0.16, pluck(f, 0.5, t60=0.6, bright=0.35, pick=0.3, name="lul%d" % i) * (0.9 - 0.12 * i))
    hum = halo((N["A3"], N["E4"], N["A4"]), dur, q=34, peak=0.4, breath=0.25, wander=0.3, name="lulh")
    hush = shimmer(dur, 3500, 9000, rate=50, grain=(0.008, 0.03), density=[(0.0, 0.2), (0.5, 1.0), (1.0, 0.3)], name="luls")
    return reverb(mixdown(norm(out) * 0.7, hum * 0.3, hush * 0.14), decay=1.1, damp=6000, mix=0.3, name="lul", predelay=0.012)


sound("atk_lullaby", gen_lullaby, 0.58, "ability", dict(WIDE, tail=(1.2, 0.26)), held=True)


def sonic_boom(notes, dur=0.75, name="sb"):
    """A shockwave with a musical core, not a bomb: the low strings hit hard, and the air
    going out from them."""
    front = wavefront("sonic", 0.4, name=name + "f")
    core = np.zeros(n_of(dur))
    for i, f in enumerate(notes):
        at(core, i * 0.006, pluck(f, dur, t60=0.5, bright=0.75, pick=0.18, name=name + "p%d" % i) * (1.0 - 0.15 * i))
    out = mixdown(np.zeros(n_of(dur)), front * 0.8, saturate(norm(core), 2.0) * 0.6,
                  pad_to(weight(80.0, 0.3, name=name + "w") * 0.6, dur))
    return reverb(saturate(out, 1.7), decay=0.9, damp=4000, mix=0.28, name="sb", predelay=0.01, size=1.3)


sound("atk_bard_boom", lambda: sonic_boom((110.0, 164.81, 220.0, 329.63), name="bbm"), 0.78, "heavy",
      dict(BIG, sub=(42, 0.3)))

# — Monk —
def punch(charged=False, name="pn"):
    """A fist: the short rush of the arm, and the blow landing in something that gives. Charged,
    the wind-up is longer and the air goes out of the room with it."""
    dur = 0.34 if not charged else 0.44
    t0 = 0.1 if charged else 0.05
    windup = whoosh(t0 + 0.08, lo=300, hi=1900, end=500, peak=0.7, q=1.6, body=0.7, top=0.1, rise=2.0, fall=1.5,
                    name=name + "w")
    land = mixdown(strike("flesh", 128, 0.22, hardness=1.0, damp=0.9, name=name + "l"),
                   bandpass(noise(0.1, name + "c"), 400, 3600) * exp_env(0.1, attack=0.001, decay=12.0) * 0.5)
    out = lay(dur, (0.0, windup, 0.55 if charged else 0.3), (t0, land, 1.15 if charged else 1.0),
              (t0, weight(92.0, 0.18, name=name + "s"), 0.6 if charged else 0.45))
    if charged:
        at(out, t0, whoosh(0.2, lo=600, hi=3000, end=500, peak=0.05, q=1.2, body=0.5, top=0.2, rise=1.0, fall=2.2,
                           name=name + "k") * 0.4)
    return reverb(saturate(out, 2.4), decay=0.4, damp=3200, mix=0.2, name="pn")


sound("atk_charge_fist", lambda: punch(True), 0.72, "melee", dict(PUNCHY, sub=(46, 0.3)))


def gen_flurry():
    """The Monk's Flurry: three blows inside a quarter of a second."""
    dur = 0.5
    out = np.zeros(n_of(dur))
    for i in range(3):
        at(out, i * 0.075, punch(False, name="fl%d" % i), 0.85 + 0.08 * i)
    return out


sound("atk_flurry", gen_flurry, 0.70, "melee", dict(PUNCHY, sub=(48, 0.28)))

# — Cleric —
sound("atk_holy_bolt", lambda: reverb(mixdown(radiance(0.36, notes=(N["A3"], N["E4"], N["A4"]), name="hb"),
                                              pad_to(weight(110.0, 0.11, name="hbw") * 0.3, 0.36)),
                                      decay=0.6, damp=7000, mix=0.2, name="hb", predelay=0.008),
      0.62, "melee", dict(width=0.75, transient=1.1, comp=(0.32, 2.4), tail=(0.6, 0.18), **BRIGHT))


def gen_smite(big=False):
    """The Cleric's Smite: a pillar of light slamming down — the rush of it falling, a choir
    crying out as it lands, the deep blow of it, and light spraying off."""
    dur = 0.75 if not big else 0.9
    notes = (N["A3"], N["E4"], N["A4"], N["E5"]) if not big else (N["A2"], N["A3"], N["E4"], N["A4"], N["E5"])
    fall = whoosh(0.16, lo=5200, hi=6000, end=900, peak=0.7, q=1.6, body=0.4, top=0.4, rise=2.0, fall=1.0, name="smf")
    stab = choir(notes, 0.5, vowels=((0.0, "ah"), (1.0, "aw")), name="smc" + ("b" if big else ""))
    stab *= seg_env([(0.0, 0.0), (0.03, 1.0), (0.3, 0.55), (1.0, 0.0)], 0.5)
    glory = halo(tuple(f * 2 for f in notes[:3]), 0.5, q=36, peak=0.2, breath=0.25, name="smh")
    blow = mixdown(thud(96, 0.3, drive=2.0, name="smt") * 0.7, whump(0.3, f=(190.0, 70.0), name="smw"))
    spray = shimmer(0.6, 3000, 12000, rate=150, density=[(0.0, 1.0), (0.4, 0.5), (1.0, 0.1)], name="sms")
    out = lay(dur, (0.0, fall, 0.4), (0.12, stab, 0.42), (0.12, glory, 0.3), (0.12, norm(blow), 0.5), (0.13, spray, 0.4))
    return reverb(saturate(out, 1.6), decay=1.0, damp=6400, mix=0.26, name="sm", predelay=0.012, size=1.3)


sound("atk_smite", gen_smite, 0.72, "ability", dict(BIG, sub=(50, 0.22), comp=(0.32, 2.4), **BRIGHT))


def gen_divine_judgment():
    """Divine Judgment: an orb of light let go — a swell of voices and singing air that
    gathers as it leaves, with the weight of what it will do."""
    dur = 0.8
    light = radiance(dur, notes=(N["A3"], N["E4"], N["A4"], N["E5"]), name="djr")
    gather = whoosh(dur, lo=500, hi=3600, end=1200, peak=0.6, q=1.3, body=0.6, top=0.3, flutter=0.2,
                    rise=1.6, fall=1.4, name="djg")
    out = lay(dur, (0.0, light * arc(dur, 0.45, 1.3, 1.2), 0.9), (0.0, gather, 0.4), (0.0, weight(84.0, 0.2, name="djw"), 0.4))
    return reverb(out, decay=1.1, damp=6400, mix=0.28, name="dj", predelay=0.012, size=1.3)


sound("atk_divine_judgment", gen_divine_judgment, 0.72, "ability", dict(BIG, sub=(48, 0.22), comp=(0.32, 2.4), **BRIGHT),
      held=True)

# — Rogue —
sound("atk_shuriken", lambda: thrown("shuriken", dur=0.3), 0.48, "melee", dict(FLY, **BRIGHT))


def gen_knife_spray():
    """Three poisoned blades off the fingers: shing-shing-shing, and the hiss of what is on
    them."""
    dur = 0.4
    out = np.zeros(n_of(dur))
    for i in range(3):
        at(out, i * 0.03, thrown("knife", dur=0.26, name="ks%d" % i), 0.85 - 0.1 * i)
    at(out, 0.02, gas(0.3, band=(1800, 7000), fizz=0.8, name="ksg") * 0.18)
    return out


sound("atk_knife_spray", gen_knife_spray, 0.54, "melee", dict(FLY, **BRIGHT))

# — Barbarian —
sound("atk_barbarian_axe", lambda: with_(swing("heavy", 0.42, name="bba"),
                                         pad_to(weight(84.0, 0.18, name="bbaw") * 0.3, 0.42)),
      0.66, "melee", SWEEP)


def gen_earthsplitter():
    """Earthsplitter: the axe into the ground, and the ground tearing open in a line away from
    it — a crack running, rock bursting up along it."""
    dur = 0.85
    hit = slam("earth", 0.6, name="esp")
    run = rip(0.5, rate=(120.0, 40.0), band=(200, 2200), name="espr")
    run = leaving(run, 1.1, 0.8)
    rock = debris(dur, count=16, band=(250, 2600), spread=(0.1, 0.8), name="espd")
    out = fit(hit, n_of(dur))
    at(out, 0.06, run * 0.5)
    out += rock * 0.4
    return saturate(out, 1.5)


sound("atk_earthsplitter", gen_earthsplitter, 0.84, "heavy", dict(HEAVY, sub=(40, 0.35)))

# — Enchantress —
sound("atk_charm_bolt", lambda: bolt("charm", N["E4"]), 0.50, "bolt", dict(BOLT_M, tail=(0.7, 0.22), **BRIGHT))

# — Jester —
def jingle(dur=0.16, count=5, name="jng"):
    """The bells on a jester's cap: a few tiny, bright jingles."""
    return rattle(dur, count=count, band=(4200, 9500), q=16.0, cluster=0.8, name=name)


sound("atk_jester_card", lambda: thrown("card", dur=0.26, name="jcd"),
      0.46, "tiny", FLY)


def gen_card_fan():
    """Three cards dealt off the deck at speed: a riffle."""
    dur = 0.34
    out = np.zeros(n_of(dur))
    for i in range(3):
        at(out, i * 0.028, thrown("card", dur=0.22, name="cf%d" % i), 0.9 - 0.1 * i)
    return out


sound("atk_card_fan", gen_card_fan, 0.50, "tiny", FLY)

# — Valkyrie —
sound("atk_spear_thrust", lambda: with_(shaft("spear", name="spr"), wings(0.2, 8.0, 0.25, name="sprw")),
      0.58, "melee", FLY)
sound("atk_pinning_spear", lambda: with_(shaft("spear", 0.4, name="pin") * 1.0,
                                         pad_to(edge("shing", 0.14, "pins") * arc(0.14, 0.2, 1.0, 1.6) * 0.22, 0.3),
                                         pad_to(weight(88.0, 0.16, name="pinw") * 0.35, 0.4)),
      0.66, "ability", FLY)

# — Warlock —
sound("atk_warlock_bolt", lambda: with_(bolt("dark", 110.0, dur=0.44, name="wlb") * 0.9,
                                        roar(0.34, f0=(110.0, 90.0, 72.0), vowels=("ah", "aw", "oo"), size=1.3, rough=0.8,
                                             breath_=0.5, peak=0.2, name="wlv") * 0.24),
      0.64, "bolt", BOLT_M)


def gen_death_coil():
    """The Warlock's Death Coil: dark fire with something alive in it, turning on itself as it
    goes — and whatever is in it growling."""
    dur = 0.7
    coil = siphon(dur, "dcl")
    fire_ = flame_gout(dur, size=1.5, name="dcf")
    out = lay(dur, (0.0, fire_, 0.8), (0.0, coil, 0.4),
              (0.03, roar(0.6, f0=(140.0, 100.0, 76.0), vowels=("ah", "aw", "oo"), size=1.35, rough=0.85, breath_=0.5,
                          peak=0.25, name="dcv"), 0.32))
    return reverb(saturate(out, 1.8), decay=0.8, damp=3400, mix=0.26, name="dc", predelay=0.01)


sound("atk_death_coil", gen_death_coil, 0.76, "ability", dict(BIG, sub=(42, 0.35)), held=True)

# — Inquisitor —
def gen_inquisitor_bolt():
    """Zeal, not grace: holy fire — voices hard on an open fifth over flame, with a crack of
    judgement at the front."""
    dur = 0.34
    from sound_engines import _b_fire
    ch = choir((N["A3"], N["E4"]), dur, vowels=((0.0, "ah"), (1.0, "aw")), name="inqc")
    ch *= seg_env([(0.0, 0.0), (0.04, 1.0), (0.35, 0.6), (1.0, 0.0)], dur)
    out = mixdown(ch * 0.26, norm(_b_fire(196.0, dur, "inqf")) * 0.7, pad_to(crack(0.02, name="inqk") * 0.3, dur),
                  pad_to(weight(104.0, 0.14, name="inqt") * 0.3, dur))
    return reverb(saturate(out, 1.6), decay=0.5, damp=4800, mix=0.2, name="inq", predelay=0.006)


sound("atk_inquisitor_bolt", gen_inquisitor_bolt, 0.56, "melee", dict(width=0.7, transient=1.5, tail=(0.55, 0.18)))
sound("atk_inq_chain", lambda: with_(chain("iron", name="iqc"),
                                     pad_to(zap_arc(0.2, 1200, 9000, q=7, steps=24, name="iqz") * exp_env(0.2, attack=0.001, decay=5.0) * 0.3, 0.42)),
      0.62, "melee", dict(width=0.8, transient=1.5, tail=(0.45, 0.16), **SPARK))


def gen_shackles():
    """Shackles: a heavy chain with two cuffs on it — the links, and the cuffs knocking as it
    flies, ready to close."""
    base = chain("iron", 0.46, name="shk")
    out = fit(base, n_of(0.6))
    for i, t0 in enumerate((0.3, 0.38)):
        at(out, t0, mixdown(snap("hard", "shkk%d" % i), thud(140 - 20 * i, 0.1, drive=1.6, name="shkt%d" % i) * 0.8) * 0.45)
    return out


sound("atk_shackles", gen_shackles, 0.66, "ability", dict(width=0.85, transient=1.5, sub=(50, 0.2), tail=(0.5, 0.16)))


# ═══════════════════════════ science fiction ═══════════════════════════

# — Cyborg —
sound("atk_cyborg_gun", lambda: with_(gun("light", name="cyg"),
                                      pad_to(machine(0.14, servo=(500, 1100), clank=0.25, whine=0.4, name="cys") * 0.25, 0.3)),
      0.66, "melee", dict(GUN_M, width=0.5, transient=2.2, tail=(0.4, 0.14)))
sound("atk_arm_rocket", lambda: with_(rocket(0.65, size=0.85, name="ark"),
                                      pad_to(machine(0.16, servo=(300, 800), clank=0.9, whine=0.3, name="arm") * 0.4, 0.3)),
      0.76, "ability", dict(width=0.7, transient=1.2, sub=(48, 0.28), pan=(-0.3, 0.8), tail=(0.7, 0.2)), held=True)


def gen_overclock():
    """The Cyborg overclocking: systems run up past their rating — a turbine climbing, the
    coils arcing as it peaks — and the vents letting go."""
    dur = 0.8
    up = glide([(0.0, 160.0), (0.75, 620.0), (1.0, 700.0)], dur * 0.75)
    rise = comb(bandpass(pink(dur * 0.75, "ovr"), 150, 6000), up, fb=0.9, damp=0.35) * swell_in(dur * 0.75, 1.8)
    coil = zap_arc(dur * 0.5, 900, 9000, q=8, steps=60, name="ovc")
    coil *= seg_env([(0.0, 0.0), (0.6, 1.0), (1.0, 0.2)], dur * 0.5)
    vent = gas(0.3, band=(700, 6500), fizz=0.8, name="ovv")
    # a relay closing as the key goes down: the spin-up alone is 120ms of nothing much, and a cast
    # that makes no sound as it is pressed feels as if it hasn't been
    out = lay(dur, (0.0, snap("hard", "ovk"), 0.35), (0.0, thud(160, 0.08, drive=1.6, name="ovr2"), 0.35),
              (0.0, norm(rise), 0.5), (dur * 0.45, norm(coil), 0.4), (dur * 0.6, vent, 0.6),
              (dur * 0.6, weight(92.0, 0.2, name="ovt"), 0.5))
    return reverb(saturate(out, 1.9), decay=0.65, damp=4600, mix=0.24, name="ov", predelay=0.006)


sound("atk_overclock", gen_overclock, 0.72, "ability", dict(width=0.85, transient=1.1, tail=(0.7, 0.2)), held=True)

# — Hacker —
def data_packet(dur=0.24, glitchy=0.0, name="db"):
    """One packet leaving: a burst of data — a rasp of bits ringing in a short line, falling —
    with the tick of the key that sent it. (It was a square-wave chirp: a telephone.) `glitchy`
    breaks it up."""
    n = n_of(dur)
    tt = t_axis(dur)
    bits = bitcrush(noise(dur, name + "n"), bits=3, hold=7) * np.exp(-tt * 22.0)
    line = comb(bits, glide([(0.0, 2400.0), (0.3, 900.0), (1.0, 620.0)], dur), fb=0.84, damp=0.25)
    line = norm(line * decay_env(dur, attack=0.001, t60=dur * 0.8))
    if glitchy > 0:
        rng = np.random.default_rng(seed_of(name + "gl"))
        gate = np.repeat((rng.uniform(size=n // 500 + 2) > glitchy * 0.5).astype(float), 500)[:n]
        line = line * lowpass(gate, 900, order=1)
    sweep = svf(noise(dur, name + "s"), glide([(0.0, 2600.0), (1.0, 8000.0)], dur), q=3.0, mode="bp")
    sweep = norm(sweep * exp_env(dur, attack=0.005, decay=9.0)) * 0.2
    out = lay(dur, (0.0, snap("tick", name + "k"), 0.5), (0.0, line, 0.8), (0.0, sweep, 1.0),
              (0.0, weight(120.0, 0.08, name=name + "w"), 0.25))
    return reverb(saturate(out, 1.5), decay=0.3, damp=7000, mix=0.18, name="db", predelay=0.004)


sound("atk_data_bolt", data_packet, 0.52, "tiny", dict(TIGHT, pingpong=(0.055, 0.24, 0.16), **BRIGHT))


def glitch(dur=0.42, name="virus"):
    """Corruption: the signal stutters and drops bits — bursts of crushed noise in a ringing
    line, each cut off mid-word — over a hum that is wrong."""
    rng = np.random.default_rng(seed_of(name))
    n = n_of(dur)
    out = np.zeros(n)
    t = 0.0
    while t < dur * 0.72:
        sd = rng.uniform(0.015, 0.05)
        f = rng.uniform(180, 1600)
        s = comb(bitcrush(noise(sd, name + str(int(t * 1e4))), bits=int(rng.integers(2, 5)), hold=int(rng.integers(4, 14))),
                 f, fb=0.8, damp=0.3)
        at(out, t, norm(s) * exp_env(sd, attack=0.001, decay=rng.uniform(3, 8)) * rng.uniform(0.3, 0.7))
        t += sd * rng.uniform(0.5, 1.1)
    hum = bitcrush(lowpass(throat(glide([(0.0, 98.0), (1.0, 62.0)], dur), dur, rough=0.3, breath_=0.1, name=name + "h"), 1200),
                   bits=4, hold=8) * exp_env(dur, decay=4.0)
    out = saturate(mixdown(out, norm(hum) * 0.3, pad_to(weight(104.0, 0.12, name=name + "w") * 0.3, dur)), 1.8)
    return reverb(out, decay=0.35, damp=5200, mix=0.16, name="virus")


sound("atk_virus_glitch", glitch, 0.58, "bolt", dict(width=0.9, transient=1.5, tail=(0.3, 0.12)))

# — Mech Pilot —
def gen_mech_gun():
    """The Mech Pilot's autocannon: two heavy rounds and the feed cycling under them."""
    dur = 0.4
    out = np.zeros(n_of(dur))
    for i in range(2):
        at(out, i * 0.085, gun("heavy", 0.3, name="mg%d" % i), 1.0 - 0.2 * i)
    return with_(out, pad_to(machine(0.24, servo=(160, 420), clank=0.4, whine=0.2, name="mgs") * 0.3, dur))


sound("atk_mech_gun", gen_mech_gun, 0.78, "melee", dict(width=0.6, transient=2.0, sub=(52, 0.28), comp=(0.2, 4.0),
                                                           tail=(0.6, 0.18)))
sound("atk_mech_slam", lambda: mixdown(slam("metal", 0.8), pad_to(machine(0.4, servo=(200, 700), clank=0.8, name="mcs") * 0.6, 0.8),
                                       pad_to(gas(0.3, band=(600, 5000), fizz=0.6, name="mcv") * 0.2, 0.8)),
      0.82, "heavy", dict(HEAVY, sub=(44, 0.32)))

# — Android —
sound("atk_beam_laser", lambda: reverb(saturate(blaster(0.28, top=2800.0, bottom=440.0, name="lsr"), 1.5),
                                       decay=0.3, damp=6000, mix=0.14, name="lsr", predelay=0.003),
      0.64, "bolt", dict(TIGHT, **BRIGHT))


def gen_ion_shot():
    """The Android's Ion Shot: a capacitor charged in a blink and dumped — a rising whine, the
    discharge with its arc, and the hum of the charge it carries."""
    dur = 0.5
    charge = comb(bandpass(noise(0.1, "ionc"), 900, 8000), glide([(0.0, 900.0), (1.0, 3600.0)], 0.1), fb=0.9, damp=0.3)
    charge = norm(charge * swell_in(0.1, 1.6))
    shot = blaster(0.36, top=3400.0, bottom=300.0, ring=0.9, buzz_hz=110.0, fall=0.11, name="ions")
    arc_ = zap_arc(0.3, 1200, 9500, q=7, steps=36, name="iona") * exp_env(0.3, attack=0.001, decay=5.0)
    out = lay(dur, (0.0, charge, 0.4), (0.09, shot, 0.9), (0.09, norm(arc_), 0.35), (0.09, weight(96.0, 0.14, name="ionw"), 0.35))
    return reverb(saturate(out, 1.5), decay=0.5, damp=5600, mix=0.2, name="ion", predelay=0.005)


sound("atk_ion_shot", gen_ion_shot, 0.70, "ability", dict(width=0.85, transient=1.4, tail=(0.6, 0.18), **SPARK))

# — Chronomancer —
def gen_time_bolt():
    """The Chronomancer's bolt: a clock's tick and tock, and the discharge heard slightly out of
    order — its tail arriving before it does."""
    dur = 0.5
    core = elec(0.3, size=0.7, colour=(700, 9000), forks=2, name="tmb")
    rev = core[::-1] * np.linspace(0.0, 1.0, len(core)) ** 2.4 * 0.4
    tick = mixdown(snap("tick", "tmt"), bandpass(noise(0.012, "tmtn"), 2400, 7000) * exp_env(0.012, attack=0.0003, decay=6.0) * 0.6)
    tock = mixdown(click(0.005, fc=1400, name="tmk"), bandpass(noise(0.016, "tmkn"), 900, 3200) * exp_env(0.016, attack=0.0003, decay=6.0) * 0.6)
    warp = halo((N["A4"], N["E5"]), dur, q=40, peak=0.5, breath=0.2, name="tmw")
    warp = fit(varispeed(warp, glide([(0.0, 1.2), (0.5, 0.85), (1.0, 1.05)], dur)), n_of(dur))
    return lay(dur, (0.0, norm(tick), 0.5), (0.09, norm(tock), 0.45), (0.0, rev, 1.0), (0.14, core, 0.9), (0.0, warp, 0.16))


sound("atk_time_bolt", gen_time_bolt, 0.66, "melee", dict(width=0.95, transient=1.0, pingpong=(0.1, 0.3, 0.2),
                                                             tail=(0.9, 0.24), **SPARK))


def gravity_knot(dur=0.5, name="grav"):
    """A knot of gravity: a deep throb, uneven, bending as space warps round it, and the air
    falling into it."""
    bend = glide([(0.0, 120.0), (0.35, 76.0), (0.7, 96.0), (1.0, 70.0)], dur)
    throb = throat(bend, dur, rough=0.15, breath_=0.1, name=name + "t")
    throb = saturate(lowpass(throb, 700) * arc(dur, 0.08, 1.0, 1.2) * nlfo(dur, 9.0, 0.45, 1.0, name + "tl"), 2.0)
    fall = svf(pink(dur, name), glide([(0.0, 3400), (0.5, 500), (1.0, 900)], dur), q=2.0, mode="bp") * swell_env(dur, 1.3)
    warp = flange(whoosh(dur, lo=300, hi=2200, end=350, peak=0.3, q=1.3, body=0.6, top=0.1, flutter=0.3, name=name + "w"), 0.4, 5.0)
    out = mixdown(norm(throb) * 0.6, norm(fall) * 0.4, norm(warp) * 0.45)
    return reverb(saturate(out, 1.7), decay=1.0, damp=2800, mix=0.28, name="grav", predelay=0.01, size=1.3)


def gen_temporal_rift():
    """A Temporal Rift thrown: gravity's throb, and time going wrong round it — ticks coming
    faster and faster, then none."""
    dur = 0.6
    out = fit(gravity_knot(0.55, "trf"), n_of(dur))
    t, gap = 0.02, 0.09
    i = 0
    while t < dur * 0.8 and i < 10:
        at(out, t, snap("tick", "trt%d" % i) * (0.35 - 0.02 * i))
        t += gap
        gap = max(0.012, gap * 0.78)
        i += 1
    return out


sound("atk_temporal_rift", gen_temporal_rift, 0.68, "ability", dict(width=0.95, transient=0.9, sub=(44, 0.3),
                                                                     tail=(1.0, 0.24)), held=True)

# — Graviton —
sound("atk_gravity_ball", lambda: gravity_knot(0.42, "grb"), 0.62, "melee", dict(width=0.95, transient=0.8, sub=(44, 0.28),
                                                                                  tail=(1.0, 0.24)))


def gen_repulse():
    """Repulse: gravity turned inside out — everything drawn in for an instant, then thrown
    out, the air going with it."""
    dur = 0.7
    pull = svf(pink(0.16, "rpp"), glide([(0.0, 300), (1.0, 3200)], 0.16), q=2.2, mode="bp") * swell_in(0.16, 2.4)
    out_ = whoosh(0.5, lo=3000, hi=3600, end=260, peak=0.04, q=1.2, body=0.9, top=0.2, flutter=0.2, rise=1.0, fall=1.6, name="rpo")
    out = lay(dur, (0.0, norm(pull), 0.5), (0.15, weight(72.0, 0.35, name="rpw"), 0.8), (0.15, out_, 0.8),
              (0.15, gravity_knot(0.45, "rpg"), 0.4))
    return saturate(out, 1.6)


sound("atk_repulse", gen_repulse, 0.78, "heavy", dict(HEAVY, sub=(42, 0.36)))
sound("atk_vortex_bomb", lambda: with_(vortex(0.85, "vxb"), gravity_knot(0.6, "vxg") * 0.3), 0.74, "heavy",
      dict(width=1.0, transient=0.6, sub=(40, 0.36), tail=(1.3, 0.28)), held=True)

# — Tesla —
def coil_hum(dur, name):
    """A coil's mains hum: rough, low, with the buzz of the windings on it."""
    return lowpass(throat(119.0, dur, rough=0.2, breath_=0.15, name=name), 1100) * ad_env(dur, attack=0.005, hold=0.4, curve=1.4)


sound("atk_tesla_arc", lambda: with_(elec(0.42, size=0.8, colour=(1400, 12000), forks=3, name="tsl") * 0.9,
                                     coil_hum(0.42, "tslh") * 0.3),
      0.70, "melee", dict(width=0.9, transient=1.5, tail=(0.7, 0.2), **SPARK))
sound("atk_tesla_chain", lambda: with_(elec(0.6, size=0.85, colour=(1200, 12000), forks=5, name="tsc") * 0.9,
                                       coil_hum(0.6, "tsch") * 0.3),
      0.72, "ability", dict(width=1.0, transient=1.5, tail=(0.8, 0.2), **SPARK))


def gen_tesla_fork():
    """Lightning Fork: one discharge that splits and splits again — three cracks, each a
    little later and a little higher."""
    dur = 0.6
    out = np.zeros(n_of(dur))
    for i, (t0, lo) in enumerate(((0.0, 1200), (0.07, 1800), (0.13, 2600))):
        at(out, t0, elec(0.34, size=0.7 - 0.08 * i, colour=(lo, 12000), forks=2, name="tsf%d" % i), 0.9 - 0.15 * i)
    return with_(out, coil_hum(dur, "tsfh") * 0.28)


sound("atk_tesla_fork", gen_tesla_fork, 0.72, "ability", dict(width=1.0, transient=1.5, tail=(0.8, 0.2), **SPARK))

# — Nanoswarm —
sound("atk_nano_bolt", lambda: bolt("nano", N["A4"], dur=0.34), 0.50, "bolt", dict(TIGHT, **BRIGHT))
sound("atk_nano_burst", lambda: mixdown(burst("swarm", 0.7, name="nb"),
                                        pad_to(debris(0.7, count=42, band=(1600, 9000), decay=(24.0, 50.0), spread=(0.0, 0.8),
                                                      name="nbt") * 0.4, 0.86),
                                        pad_to(whump(0.3, f=(170.0, 60.0), name="nbw") * 0.6, 0.86)),
      0.72, "ability", dict(WIDE, sub=(48, 0.25), tail=(0.7, 0.2)))

# — Voidwalker —
def gen_void_bolt():
    """The Voidwalker's bolt: sound is pulled in, then something collapses — a suction rising
    into it, a deep drop as it goes, and a dark streak warping past."""
    dur = 0.46
    pull_d = 0.14
    pull = svf(pink(pull_d, "void"), glide([(0.0, 400), (1.0, 4200)], pull_d), q=2.4, mode="bp") * swell_in(pull_d, 2.4)
    drop = mixdown(weight(74.0, 0.3, name="vdw"), whump(0.3, f=(190.0, 60.0), name="vdh") * 0.7)
    streak = flange(whoosh(dur - pull_d, lo=1600, hi=2000, end=220, peak=0.05, q=1.4, body=0.7, top=0.0, flutter=0.4,
                           rise=1.0, fall=1.4, name="vds"), 0.5, 6.5, rise=False)
    out = lay(dur, (0.0, norm(pull), 0.5), (pull_d - 0.01, norm(drop), 0.8), (pull_d - 0.02, norm(streak), 0.55))
    return reverb(saturate(out, 1.7), decay=1.0, damp=2600, mix=0.3, name="void", predelay=0.012, size=1.4)


sound("atk_void_bolt", gen_void_bolt, 0.66, "melee", dict(width=1.0, transient=0.7, sub=(42, 0.3),
                                                          pingpong=(0.14, 0.3, 0.16), tail=(1.0, 0.24)))


def gen_gravity_lance():
    """The Voidwalker's lance: space pulled in behind it as it goes — a deep uneven throb, a
    suction that rises, and a dark streak of air."""
    dur = 0.6
    throb = lowpass(throat(glide([(0.0, 86.0), (1.0, 62.0)], dur), dur, rough=0.2, breath_=0.1, name="glt"), 600)
    throb = throb * nlfo(dur, 9.0, 0.3, 1.0, "gltl") * seg_env([(0.0, 0.0), (0.08, 1.0), (0.8, 0.8), (1.0, 0.0)], dur)
    suck = svf(pink(dur, "glk"), glide([(0.0, 3600), (1.0, 220)], dur), q=2.6, mode="bp") * np.linspace(0.25, 1.0, n_of(dur)) ** 1.8
    streak = flange(whoosh(dur, lo=250, hi=1800, end=260, peak=0.2, q=1.4, body=0.8, top=0.0, flutter=0.3, name="gla"), 0.6, 7.0)
    out = mixdown(saturate(norm(throb), 2.0) * 0.55, norm(suck) * 0.45, norm(streak) * 0.5)
    return reverb(saturate(out, 1.7), decay=1.0, damp=3000, mix=0.28, name="gl", predelay=0.01, size=1.3)


sound("atk_gravity_lance", gen_gravity_lance, 0.68, "ability", dict(width=0.95, transient=0.7, sub=(44, 0.3),
                                                                     tail=(1.0, 0.24)), held=True)

# — Photon —
def gen_photon_beam():
    """Photon's bolt: a blaster bolt split through a prism — the bolt, a second, higher one a
    hair behind, and a spray of light off them."""
    dur = 0.34
    out = lay(dur, (0.0, blaster(0.3, top=3200.0, bottom=520.0, name="pho1"), 0.8),
              (0.012, blaster(0.28, top=4800.0, bottom=880.0, buzz_hz=95.0, name="pho2"), 0.35),
              (0.0, shimmer(dur, 5000, 14000, rate=150, grain=(0.004, 0.012), density=[(0.0, 1.0), (1.0, 0.1)],
                            name="phos"), 0.3))
    return reverb(saturate(out, 1.4), decay=0.5, damp=6400, mix=0.18, name="pho", predelay=0.004)


sound("atk_photon_beam", gen_photon_beam, 0.64, "bolt", dict(width=0.8, transient=1.2, tail=(0.5, 0.18), **BRIGHT))


def gen_prism():
    """Prism: one beam into three — three bolts a breath apart, each higher than the last, and
    the glass singing."""
    dur = 0.42
    out = np.zeros(n_of(dur))
    for i in range(3):
        at(out, i * 0.03, blaster(0.26, top=2600.0 * (1.0 + 0.25 * i), bottom=420.0 * (1.0 + 0.25 * i), name="prs%d" % i),
           0.8 - 0.12 * i)
    glass = halo((N["E6"], N["E6"] * 1.5, N["E6"] * 2), dur, q=70, attack=0.004, t60=dur * 0.6, breath=0.0, name="prsg")
    out = mixdown(out, glass * 0.14)
    return reverb(saturate(out, 1.4), decay=0.5, damp=6400, mix=0.18, name="prs", predelay=0.004)


sound("atk_prism", gen_prism, 0.68, "ability", dict(width=0.9, transient=1.3, tail=(0.5, 0.18), **BRIGHT))

# — Railgunner —
def gen_sniper_shot():
    """The Railgunner's primary: a slug down a rail — the crack, a zip of the round leaving,
    and one short stutter of the coils."""
    dur = 0.4
    shot = gun("light", 0.3, name="snp")
    zip_ = svf(noise(0.12, "snz"), glide([(0.0, 7000.0), (1.0, 1800.0)], 0.12), q=3.0, mode="bp") * exp_env(0.12, attack=0.001, decay=5.0)
    coil = zap_arc(0.18, 900, 8000, q=7, steps=20, name="snc") * nlfo(0.18, 30.0, 0.0, 1.0, "sncl") ** 2 * exp_env(0.18, attack=0.001, decay=6.0)
    return lay(dur, (0.0, shot, 0.85), (0.006, norm(zip_), 0.4), (0.01, norm(coil), 0.25))


sound("atk_sniper_beam", gen_sniper_shot, 0.72, "melee", dict(width=0.5, transient=2.1, comp=(0.18, 5.0), tail=(0.5, 0.16)))


def gen_railgun():
    """The Railgun Shot: the coils dumping at once — a crack like the air splitting, a deep
    punch, the slug's thin scream going away and the coil rings stuttering off the muzzle."""
    dur = 0.8
    punch_ = mixdown(thud(82, 0.4, drive=2.2, name="rgt"), whump(0.3, f=(170.0, 55.0), name="rgw") * 0.7)
    scream = svf(noise(0.35, "rgs"), glide([(0.0, 7400.0), (1.0, 2600.0)], 0.35), q=6.0, mode="bp") * exp_env(0.35, attack=0.001, decay=5.0)
    rings = zap_arc(dur, 700, 9000, q=7, steps=int(90 * dur), name="rgz")
    stut = np.sin(2 * np.pi * np.cumsum(glide([(0.0, 26.0), (1.0, 9.0)], dur)) / SR)
    rings *= (0.5 + 0.5 * stut) ** 2 * exp_env(dur, attack=0.001, decay=4.0)
    out = lay(dur, (0.0, crack(0.035, name="rgk"), 1.0), (0.0, norm(punch_), 0.7), (0.005, norm(scream), 0.2),
              (0.01, norm(rings), 0.35))
    return reverb(saturate(out, 2.0), decay=1.0, damp=3600, mix=0.28, name="rg", predelay=0.01, size=1.4)


sound("atk_beam_railgun", gen_railgun, 0.84, "heavy", dict(width=0.8, transient=1.8, sub=(46, 0.35), comp=(0.2, 4.0),
                                                           tail=(0.9, 0.22)))

# — Bombardier —
sound("atk_comic_bomb", lambda: lobbed("bomb", 0.5), 0.58, "melee", dict(BIG, sub=(48, 0.25)))


def gen_cluster_bomb():
    """The Bombardier's cluster bomb: the launcher's deep thoonk and a canister of bomblets
    rattling inside it as it goes up."""
    dur = 0.7
    thoonk = mixdown(thud(84, 0.3, drive=2.0, name="cbt"), whump(0.25, f=(150.0, 55.0), name="cbw") * 0.7)
    gasp = svf(pink(0.2, "cbg"), glide([(0.0, 2400), (1.0, 600)], 0.2), q=1.4, mode="bp") * exp_env(0.2, attack=0.002, decay=5.0)
    out = lay(dur, (0.0, norm(thoonk), 1.0), (0.0, norm(gasp), 0.45),
              (0.04, rattle(0.5, count=9, band=(900, 4000), q=4.5, cluster=0.6, name="cbr"), 0.25),
              (0.02, whoosh(0.5, lo=350, hi=2200, end=500, peak=0.3, q=1.6, body=0.8, top=0.1, name="cba"), 0.4))
    return reverb(saturate(out, 1.8), decay=0.7, damp=4000, mix=0.22, name="cb", predelay=0.008)


sound("atk_cluster_bomb", gen_cluster_bomb, 0.84, "heavy", dict(HEAVY, sub=(46, 0.3)))

# — Sentinel —
sound("atk_turret_laser", lambda: lay(0.44, (0.0, machine(0.16, servo=(420, 900), clank=0.35, whine=0.5, name="tur"), 0.45),
                                      (0.08, blaster(0.3, top=2400.0, bottom=390.0, buzz_hz=60.0, name="turb"), 0.9)),
      0.60, "melee", dict(width=0.65, transient=1.2, tail=(0.5, 0.16)))


def gen_suppress():
    """Suppressing fire: a three-round burst from a mounted gun."""
    dur = 0.42
    out = np.zeros(n_of(dur))
    for i in range(3):
        at(out, i * 0.075, gun("light", 0.28, name="bf%d" % i), 0.95 - 0.12 * i)
    return out


sound("atk_suppress_fire", gen_suppress, 0.78, "ability", dict(GUN_M, width=0.6, transient=2.0, tail=(0.6, 0.18)))

# — Pilot —
def gen_pilot_guns():
    """The Pilot's twin guns: two heavy rounds so close they are nearly one."""
    dur = 0.36
    out = np.zeros(n_of(dur))
    at(out, 0.0, gun("heavy", 0.3, name="pg0"), 1.0)
    at(out, 0.028, gun("heavy", 0.3, name="pg1"), 0.8)
    return out


sound("atk_pilot_guns", gen_pilot_guns, 0.80, "melee", dict(GUN_M, width=0.7, transient=2.1, tail=(0.7, 0.2)))


def prop_engine(dur, lo=70.0, hi=100.0, name="prop"):
    """A biplane's engine going over: the pistons a rough low rattle, the propeller chopping
    the air, the whole of it rising as it comes and falling as it goes."""
    f = glide([(0.0, lo), (0.45, hi), (1.0, lo * 0.85)], dur)
    pistons = throat(f, dur, rough=0.25, breath_=0.3, name=name)
    pistons = saturate(lowpass(pistons, 2400), 2.4)
    chop = bandpass(pink(dur, name + "c"), 200, 2600) * (0.5 + 0.5 * np.sin(2 * np.pi * np.cumsum(f * 0.5) / SR)) ** 2
    return norm(mixdown(norm(pistons) * 0.7, norm(chop) * 0.5) * arc(dur, 0.45, 1.3, 1.3))


def gen_strafing_run():
    """A strafing run: the biplane coming over, and its guns — three rounds — as it passes."""
    dur = 0.7
    out = np.zeros(n_of(dur))
    at(out, 0.0, prop_engine(dur, name="str") * 0.6)
    for i in range(3):
        at(out, 0.12 + i * 0.07, gun("rifle", 0.28, name="sr%d" % i), 0.8 - 0.08 * i)
    return out


sound("atk_strafing_run", gen_strafing_run, 0.80, "ability", dict(GUN_M, width=0.9, transient=1.8, pan=(-0.6, 0.6),
                                                                   tail=(0.7, 0.2)), held=True)


def gen_napalm():
    """A napalm strike called in: an engine tearing over, low, and the falling whistle of what
    it dropped. The fire is the blast, when it lands."""
    dur = 0.95
    over = prop_engine(dur, lo=95.0, hi=150.0, name="npj")
    rush = whoosh(dur, lo=500, hi=3200, end=450, peak=0.35, q=1.2, body=0.8, top=0.2, flutter=0.3, name="npr")
    # the bomb falling: air whistling past its fins, which is noise in a narrow band, not a tone
    fall = svf(noise(0.5, "npw"), glide([(0.0, 2400.0), (1.0, 1100.0)], 0.5), q=8.0, mode="bp")
    fall = norm(fall * seg_env([(0.0, 0.0), (0.3, 1.0), (1.0, 0.3)], 0.5))
    out = lay(dur, (0.0, over, 0.7), (0.0, rush, 0.6), (0.4, fall, 0.2))
    return reverb(saturate(out, 1.8), decay=0.9, damp=3600, mix=0.26, name="np", predelay=0.01, size=1.4)


sound("atk_napalm_strike", gen_napalm, 0.82, "heavy", dict(HEAVY, sub=(46, 0.3)), held=True)

# — Glitcher —
sound("atk_glitch_bolt", lambda: data_packet(0.26, glitchy=0.8, name="glb"), 0.52, "tiny",
      dict(TIGHT, pingpong=(0.04, 0.3, 0.2), **BRIGHT))


def gen_lag_spike():
    """A Lag Spike: a corrupted packet — it leaves, stalls, and leaves again."""
    dur = 0.5
    pk = glitch(0.2, name="lag")
    out = np.zeros(n_of(dur))
    at(out, 0.0, pk)
    at(out, 0.22, pk * 0.85)
    at(out, 0.31, pk[:n_of(0.06)] * 0.6)
    return out


sound("atk_lag_spike", gen_lag_spike, 0.62, "ability", dict(width=0.9, transient=1.5, tail=(0.35, 0.14)))


# ═══════════════════════════ beasts ═══════════════════════════

# — Wolf —
sound("atk_claw_swipe", lambda: swing("claw", 0.3), 0.58, "melee", SWEEP)


def gen_momentum_strike():
    """The Wolf's Momentum Strike: a body arriving at speed, with the snarl it arrives on."""
    dur = 0.45
    rush = whoosh(0.22, lo=250, hi=2200, end=400, peak=0.7, q=1.6, body=0.8, top=0.1, rise=2.0, fall=1.5, name="ms")
    hit = mixdown(strike("flesh", 115, 0.26, hardness=1.0, damp=0.85, name="msh"), thud(104, 0.26, drive=1.9, name="msd") * 0.5)
    out = lay(dur, (0.0, rush, 0.55), (0.16, hit, 1.0), (0.16, weight(88.0, 0.2, name="msw"), 0.5),
              (0.08, snarl(135.0, 0.3, rough=0.8, name="mss"), 0.4))
    return reverb(saturate(out, 2.0), decay=0.5, damp=3200, mix=0.22, name="ms", predelay=0.006)


sound("atk_momentum_strike", gen_momentum_strike, 0.72, "ability", dict(PUNCHY, sub=(50, 0.28)))


def gen_howl():
    """The Wolf's howl: rise, hold, fall — the shape is the whole animal — with the growl
    under it and the pack answering, faintly, from further off."""
    dur = 1.05
    v = cry(dur, [(0.0, 180), (0.18, 330), (0.55, 350), (0.8, 300), (1.0, 200)],
            [(0.0, "oo"), (0.25, "ah"), (0.75, "ah"), (1.0, "oo")],
            growl=0.12, breath=0.3, jitter=0.012, open_q=0.62,
            amp_keys=((0.0, 0.0), (0.14, 1.0), (0.72, 0.92), (1.0, 0.0)), name="howl")
    far = lowpass(voices(dur * 0.8, [(0.0, 210.0), (0.25, 390.0), (0.7, 400.0), (1.0, 250.0)],
                         [(0.0, "oo"), (0.3, "ah"), (1.0, "oo")], count=2, spread=0.06, stagger=0.1, breath_=0.4,
                         name="howlf"), 1800)
    low = snarl(100.0, dur * 0.5, rough=0.5, name="howl2")
    out = mixdown(v * 0.9, pad_to(low * 0.25, dur))
    at(out, 0.2, far * 0.16)
    return reverb(out, decay=1.5, damp=3600, mix=0.32, name="howl", predelay=0.02, size=1.6)


sound("atk_howl", gen_howl, 0.76, "heavy", dict(GHOST, tail=(1.5, 0.3)), held=True)

# — Serpent —
sound("atk_venom_bolt", lambda: with_(bolt("acid", N["G3"]), snake_hiss(0.3, "vnh") * 0.2), 0.56, "bolt", BOLT_M)


def venom_spit(dur=0.34, name="vs"):
    """Venom spat: the hiss before it, the wet 'pt' of it leaving, and it fizzing in the air."""
    spit = mixdown(highpass(noise(0.05, name + "1"), 2200) * exp_env(0.05, decay=13.0),
                   squelch(0.14, f=(1600, 500), q=2.6, lumps=2, name=name + "2") * 0.7)
    out = lay(dur, (0.0, snake_hiss(0.16, name + "h"), 0.4), (0.06, norm(spit), 0.9),
              (0.08, gas(dur * 0.8, band=(900, 6500), fizz=1.0, name=name + "3"), 0.3))
    return reverb(saturate(out, 1.8), decay=0.35, damp=5000, mix=0.2, name="vs")


sound("atk_venom_spit", venom_spit, 0.54, "melee", dict(TIGHT, pan=(-0.4, 0.4)))

# — Spider —
def gen_web_shot():
    """The Spider's web: a 'thwip' — silk leaving the spinneret under pressure — the strand
    whining thin as it pays out, and the sticky slap of it."""
    dur = 0.34
    thwip = whoosh(0.16, lo=1400, hi=6000, end=1800, peak=0.2, q=2.4, body=0.2, top=0.4, rise=1.2, fall=2.4, name="wb")
    strand = svf(noise(0.2, "wbst"), glide([(0.0, 3400.0), (1.0, 1300.0)], 0.2), q=7.0, mode="bp") * exp_env(0.2, attack=0.004, decay=6.0)
    stick = mixdown(bandpass(noise(0.14, "wbs"), 600, 4000) * exp_env(0.14, attack=0.002, decay=10.0),
                    squelch(0.14, f=(1800, 600), q=2.6, lumps=3, name="wbw") * 0.6)
    out = lay(dur, (0.0, snap("soft", "wbp"), 0.4), (0.0, thwip, 0.7), (0.03, norm(strand), 0.22), (0.1, norm(stick), 0.6))
    return reverb(saturate(out, 1.8), decay=0.34, damp=5200, mix=0.2, name="wb")


sound("atk_web_shot", gen_web_shot, 0.54, "melee", dict(TIGHT, width=0.55))

# — Bear —
sound("atk_bear_swipe", lambda: with_(swing("claw", 0.36, name="bes") * 0.9,
                                      pad_to(weight(84.0, 0.2, name="besw") * 0.4, 0.36),
                                      snarl(84.0, 0.26, rough=0.8, size=1.3, name="besv") * 0.22),
      0.68, "melee", dict(PUNCHY, sub=(50, 0.28)))


def grab(dur=0.4, size=1.0, name="grb", voice=None):
    """Something taken hold of: the reach, the hands (or paws, or talons) closing, the breath
    going out of whoever is caught."""
    reach = whoosh(0.18, lo=300 / size, hi=2000 / size, end=500 / size, peak=0.7, q=1.6, body=0.7, top=0.1, rise=1.8,
                   fall=1.5, name=name)
    catch = mixdown(strike("flesh", 160 / size, 0.16, hardness=1.0, name=name + "c"),
                    bandpass(noise(0.09, name + "cl"), 900, 5000) * exp_env(0.09, decay=11.0) * 0.5)
    out = lay(dur, (0.0, reach, 0.45), (0.12, catch, 1.0), (0.12, weight(92.0 / size ** 0.5, 0.16, name=name + "w"), 0.4))
    if voice is not None:
        at(out, 0.1, voice)
    return reverb(saturate(out, 2.0), decay=0.4, damp=3600, mix=0.2, name="grb")


sound("atk_bear_hug", lambda: grab(0.42, size=1.3, name="bhg", voice=snarl(80.0, 0.3, rough=0.8, size=1.35, name="bhv") * 0.4),
      0.64, "melee", dict(PUNCHY, width=0.5))


def gen_bear_maul():
    """Maul: the Bear raking all round itself — three great swipes run together, and the roar."""
    dur = 0.6
    out = np.zeros(n_of(dur))
    for i in range(3):
        at(out, i * 0.09, swing("claw", 0.3, name="bm%d" % i), 0.9 - 0.08 * i)
    at(out, 0.0, roar(0.55, f0=(78.0, 104.0, 66.0), size=1.35, rough=0.85, breath_=0.5, name="bmv") * 0.5)
    at(out, 0.05, weight(78.0, 0.3, name="bmw") * 0.4)
    return out


sound("atk_bear_maul", gen_bear_maul, 0.76, "ability", dict(PUNCHY, sub=(48, 0.3), width=0.8))

# — Scorpion —
def gen_stinger(big=False):
    """The Scorpion's sting: the tail cocking (a dry chitin tick), a hard puncture, and venom
    going in. Chitin is dry: it clicks and scrapes, it does not ring."""
    dur = 0.28 if not big else 0.4
    lift = chitin(0.06 if not big else 0.12, (2000, 8000), 0.8, "st1" + ("b" if big else ""))
    stab = mixdown(snap("hard", "st2") * 0.9, thud(140, 0.1, drive=1.8, name="st3") * 0.6)
    t0 = 0.055 if not big else 0.11
    out = lay(dur, (0.0, lift, 1.0), (t0, stab, 1.0), (t0 + 0.02, gas(0.14 if not big else 0.24, band=(1200, 7000), fizz=0.8, name="st4"), 0.3))
    if big:
        at(out, 0.0, whoosh(0.14, lo=900, hi=4200, end=1000, peak=0.7, q=1.8, body=0.3, top=0.3, name="st5") * 0.4)
    return reverb(saturate(out, 2.0), decay=0.3, damp=5600, mix=0.18, name="st")


sound("atk_stinger", gen_stinger, 0.54, "melee", dict(TIGHT, width=0.55))
sound("atk_paralytic_sting", lambda: gen_stinger(True), 0.60, "melee", dict(TIGHT, width=0.6))
sound("atk_acid_spray", lambda: wavefront("acid"), 0.60, "melee", dict(width=0.85, transient=1.0, pan=(-0.6, 0.6)))

# — Hawk —
sound("atk_hawk_dive", lambda: with_(swing("talon", 0.28, name="hwd") * 0.85, wings(0.26, 13.0, 0.4, name="hww")),
      0.56, "melee", SWEEP)
sound("atk_pin_down", lambda: with_(swing("talon", 0.34, name="pnd") * 0.9, wings(0.3, 11.0, 0.45, name="pndw"),
                                    pad_to(screech(0.26, f0=(2100.0, 3100.0, 1800.0), rough=0.55, name="pndv") * 0.22, 0.34),
                                    pad_to(weight(100.0, 0.14, name="pndt") * 0.35, 0.34)),
      0.64, "ability", SWEEP)

# — Shark —
def gen_shark_rush(big=False):
    """The Shark: the water gets out of the way, then the bite."""
    dur = 0.46 if not big else 0.55
    rush = water(0.3, size=0.9, bubbles=9, foam=0.8, name="shk" + ("b" if big else ""))
    out = lay(dur, (0.0, rush, 0.8), (0.16, chomp("shkc", size=1.2 if big else 1.0), 0.9))
    if big:
        at(out, 0.3, creak(0.2, rate=(40.0, 90.0), body=(200.0, 500.0), q=3.0, name="shkd") * 0.3)
    return reverb(saturate(out, 1.8), decay=0.5, damp=3600, mix=0.22, name="shk")


sound("atk_shark_rush", gen_shark_rush, 0.66, "melee", dict(PUNCHY, width=0.6))
sound("atk_jaw_chomp", lambda: gen_shark_rush(True), 0.70, "ability", dict(PUNCHY, width=0.65))

# — Beetle —
sound("atk_shell_charge", lambda: with_(boulder(0.5, size=0.8, name="btl") * 0.45, chitin(0.3, (900, 5200), 0.55, "besc"),
                                        pad_to(mixdown(thud(108, 0.26, drive=1.9, name="bec") * 0.8,
                                                       pad_to(click(0.006, fc=1500, name="beck") * 0.6, 0.26)), 0.4)),
      0.70, "melee", dict(BIG, sub=(50, 0.25)))
sound("atk_horn_flip", lambda: with_(slam("earth", 0.7, name="hfl") * 0.8, chitin(0.3, (800, 4800), 0.6, "hflc"),
                                     pad_to(whoosh(0.3, lo=300, hi=2400, end=700, peak=0.5, q=1.5, body=0.8, top=0.1,
                                                   rise=1.6, fall=1.4, name="hflu") * 0.5, 0.7)),
      0.80, "heavy", dict(HEAVY, sub=(44, 0.3)))

# — Treant —
sound("atk_treant_bark", lambda: with_(shaft("thorn", 0.34, name="trb") * 0.85,
                                       pad_to(mixdown(creak(0.16, rate=(50.0, 120.0), body=(300.0, 700.0), q=3.5, name="trc") * 0.6,
                                                      grains(0.1, rate=160, band=(400, 2400), length=(0.002, 0.006), name="trw") * 0.5),
                                              0.34) * 0.6,
                                       dust(0.3, "trd") * 0.25),
      0.52, "melee", FLY)
sound("atk_root_pull", lambda: vine_lash(0.55, woody=0.7, leafy=0.15, name="rtp"), 0.72, "melee",
      dict(width=0.75, transient=1.4, tail=(0.4, 0.14)))
sound("atk_root_burst", lambda: burst("root", 0.75), 0.72, "ability", dict(BIG, sub=(48, 0.32)))

# — Phoenix —
sound("atk_phoenix_flare", lambda: with_(bolt("fire", 293.66, dur=0.42, name="phx") * 0.85,
                                         shimmer(0.42, 4000, 13000, rate=110, density=[(0.0, 1.0), (1.0, 0.1)], name="phs") * 0.26,
                                         wings(0.3, 10.0, 0.3, name="phw")),
      0.60, "bolt", dict(BOLT_M, tail=(0.6, 0.2)))
sound("atk_flame_trail", lambda: with_(wavefront("flame", 0.6, name="ftr"), wings(0.5, 8.0, 0.4, (200, 2400), "ftrw"),
                                       pad_to(screech(0.4, f0=(1500.0, 2400.0, 1300.0), rough=0.5, breath_=0.5, name="ftrv") * 0.16, 0.6)),
      0.68, "ability", dict(width=0.9, transient=0.9, sub=(50, 0.25), pan=(-0.6, 0.6)))

# — Hydra —
def gen_hydra_spit(heads=3, gap=0.038, dur=0.5):
    """The Hydra: several heads, slightly out of time with each other."""
    out = np.zeros(n_of(dur))
    for i, f in enumerate((196.0, 174.61, 220.0)[:heads]):
        at(out, i * gap, bolt("acid", f, dur=0.34, name="hyd%d" % i) * (0.7 - 0.14 * i))
    return out


sound("atk_hydra_spit", gen_hydra_spit, 0.58, "melee", BOLT_M)
sound("atk_hydra_heads", lambda: with_(gen_hydra_spit(3, 0.055, 0.6), snake_hiss(0.4, "hydh") * 0.3,
                                       pad_to(snarl(96.0, 0.4, rough=0.8, size=1.3, name="hydv") * 0.25, 0.6)),
      0.66, "ability", dict(BOLT_M, width=0.9))
sound("atk_poison_cloud", lambda: burst("spore", 0.85), 0.66, "ability", dict(WIDE, tail=(1.1, 0.26)), held=True)

# — Mantis —
sound("atk_mantis_scythe", lambda: with_(swing("chitin", 0.26, name="mas") * 0.9, chitin(0.2, (2000, 9000), 0.42, "masc")),
      0.56, "melee", dict(SWEEP, width=0.6))
sound("atk_mantis_strike", lambda: with_(swing("chitin", 0.34, name="mst") * 0.9, chitin(0.26, (1800, 9000), 0.5, "mstc"),
                                         leaves(0.3, 120, "mstl") * 0.2, pad_to(weight(104.0, 0.14, name="mstw") * 0.3, 0.34)),
      0.64, "ability", dict(SWEEP, width=0.7))

# — Jellyfish —
sound("atk_sting", lambda: with_(bolt("electric", N["E4"], dur=0.3),
                                 bubbles(0.3, count=8, f=(500, 1600), rise=(1.15, 1.5), size=(0.008, 0.02), name="jlb") * 0.14),
      0.58, "bolt", dict(TIGHT, **SPARK))
sound("atk_electric_burst", lambda: with_(burst("surge", 0.6, name="elb"),
                                          pad_to(crack(0.025, name="elbk") * 0.5, 0.6),
                                          water(0.4, size=1.2, bubbles=8, foam=0.4, name="elbw") * 0.3),
      0.72, "ability", dict(WIDE, sub=(50, 0.22), tail=(0.7, 0.2), **SPARK))

# — Gorilla —
sound("atk_ape_throw", lambda: with_(boulder(0.5, name="ape") * 0.85,
                                     roar(0.34, f0=(96.0, 120.0, 80.0), size=1.25, rough=0.7, breath_=0.5, name="apev") * 0.3),
      0.78, "melee", dict(HEAVY, sub=(44, 0.3)))
sound("atk_primate_grab", lambda: grab(0.42, size=1.2, name="pgb",
                                       voice=roar(0.26, f0=(120.0, 150.0, 100.0), vowels=("oo", "oo", "uh"), size=1.2, rough=0.6, name="pgv") * 0.35),
      0.64, "melee", dict(PUNCHY, width=0.5))
sound("atk_ground_pound", lambda: mixdown(slam("flesh", 0.8), pad_to(slam("earth", 0.6, name="gpe") * 0.5, 0.8),
                                          pad_to(roar(0.4, f0=(84.0, 110.0, 70.0), size=1.3, rough=0.8, name="gp") * 0.4, 0.8)),
      0.84, "heavy", dict(HEAVY, sub=(42, 0.36)))

# — Chameleon —
sound("atk_chameleon_dart", lambda: with_(shaft("dart", name="chd"),
                                          pad_to(squelch(0.08, f=(1800, 700), q=2.6, lumps=1, name="chdq") * 0.3, 0.3)),
      0.48, "tiny", FLY)


def gen_tongue():
    """The Chameleon's tongue: an elastic launch — a wet 'thp' and the stretch of it, taut and
    thin — the sticky contact, and the snap back."""
    dur = 0.42
    launch = mixdown(highpass(noise(0.03, "tg1"), 1800) * exp_env(0.03, decay=12.0) * 0.7, snap("pop", "tgp") * 0.6)
    stretch = svf(noise(0.2, "tgs"), glide([(0.0, 900.0), (0.7, 2400.0), (1.0, 2000.0)], 0.2), q=6.0, mode="bp")
    stretch = norm(stretch * arc(0.2, 0.6, 1.4, 2.0))
    splat = squelch(0.16, f=(1700, 500), q=2.8, lumps=3, name="tgw")
    out = lay(dur, (0.0, launch, 0.9), (0.0, stretch, 0.3), (0.14, splat, 0.7), (0.14, snap("soft", "tgc"), 0.5),
              (0.26, launch, 0.45))
    return reverb(saturate(out, 1.9), decay=0.35, damp=4600, mix=0.2, name="tg")


sound("atk_tongue_lash", gen_tongue, 0.56, "melee", dict(TIGHT, width=0.55))


# ═══════════════════════════ the myths ═══════════════════════════

# — Minotaur —
def gen_horn_gore():
    """The Minotaur's horns: the head going down and in — a snort, the heavy rush of it, and
    the blow."""
    dur = 0.38
    snort = bandpass(pink(0.14, "hgs"), 250, 2400) * arc(0.14, 0.2, 1.0, 1.6) * nlfo(0.14, 40.0, 0.5, 1.0, "hgsl")
    rush = whoosh(0.22, lo=260, hi=1800, end=400, peak=0.7, q=1.5, body=0.9, top=0.05, rise=1.8, fall=1.5, name="hgr")
    blow = mixdown(strike("flesh", 110, 0.2, hardness=1.0, name="hgb"), thud(100, 0.2, drive=1.9, name="hgt") * 0.6)
    out = lay(dur, (0.0, norm(snort), 0.45), (0.0, rush, 0.55), (0.15, blow, 1.0), (0.15, weight(84.0, 0.18, name="hgw"), 0.45))
    return reverb(saturate(out, 2.0), decay=0.5, damp=3200, mix=0.22, name="hg", predelay=0.006)


sound("atk_horn_gore", gen_horn_gore, 0.70, "melee", dict(PUNCHY, sub=(50, 0.28)))
sound("atk_horn_toss", lambda: with_(thrown("head", 0.46, name="htx"),
                                     pad_to(roar(0.3, f0=(92.0, 124.0, 80.0), vowels=("aw", "ah", "aw"), size=1.4, rough=0.7,
                                                 name="htv") * 0.3, 0.46)),
      0.68, "ability", dict(SWEEP, sub=(48, 0.28)))

# — Medusa —
def snakes(dur, count=3, name="snk"):
    """A head of snakes: several hisses, each starting and stopping when it likes."""
    out = np.zeros(n_of(dur))
    rng = np.random.default_rng(seed_of(name))
    for i in range(count):
        d = dur * rng.uniform(0.5, 0.9)
        at(out, rng.uniform(0.0, dur - d), snake_hiss(d, name + str(i)) * rng.uniform(0.6, 1.0))
    return norm(out)


sound("atk_medusa_spit", lambda: with_(bolt("acid", 174.61, dur=0.4, name="mds") * 0.85, snakes(0.36, 2, "mdv") * 0.3),
      0.58, "bolt", BOLT_M)


def stone_gaze(dur=0.6, wide=False, name="sg"):
    """Medusa's gaze: the snakes in her hair hissing, and the grinding crackle of flesh turning
    to stone under it."""
    turn = grains(dur, rate=180, band=(300, 3200), length=(0.002, 0.008),
                  density=[(0.0, 0.0), (0.2, 0.6), (0.7, 1.0), (1.0, 0.3)], name=name + "g")
    grind = saturate(svf(pink(dur, name + "r"), glide([(0.0, 300), (1.0, 900)], dur), q=1.8, mode="bp"), 2.6)
    grind *= seg_env([(0.0, 0.0), (0.3, 0.6), (0.8, 1.0), (1.0, 0.0)], dur)
    out = mixdown(snakes(dur, 3, name + "h") * 0.45, turn * 0.5, norm(grind) * 0.45)
    if wide:
        out = mixdown(out, pad_to(whump(0.3, f=(150.0, 60.0), name=name + "w") * 0.5, dur),
                      whoosh(dur, lo=400, hi=2600, end=500, peak=0.15, q=1.2, body=0.7, top=0.1, name=name + "o") * 0.4)
    return reverb(saturate(out, 1.5), decay=0.7, damp=3800, mix=0.24, name="sg", predelay=0.008)


sound("atk_petrify", stone_gaze, 0.62, "ability", dict(width=0.9, transient=0.8, tail=(0.7, 0.2)), held=True)
sound("atk_stone_gaze", lambda: stone_gaze(0.75, wide=True, name="sgw"), 0.72, "ability",
      dict(WIDE, sub=(48, 0.25), tail=(0.9, 0.22)), held=True)

# — Cerberus —
def three_throats(dur=0.3, name="cev"):
    out = np.zeros(n_of(dur + 0.1))
    for i, f in enumerate((96.0, 116.0, 84.0)):
        at(out, 0.01 + i * 0.035, snarl(f, dur, rough=0.85, size=1.15, name=name + str(i)) * (0.8 - 0.14 * i))
    return norm(out)


sound("atk_hellhound_spit", lambda: with_(bolt("molten", 87.31, dur=0.46, name="cer") * 0.85, three_throats(0.3) * 0.32),
      0.68, "bolt", dict(BOLT_M, sub=(46, 0.25)))


def gen_triple_blast():
    """Three heads, three gouts of flame, a breath apart."""
    dur = 0.5
    out = np.zeros(n_of(dur))
    for i, f in enumerate((220.0, 196.0, 247.0)):
        at(out, i * 0.045, bolt("fire", f, dur=0.36, name="tb%d" % i), 0.85 - 0.1 * i)
    return with_(out, three_throats(0.3, "tbv") * 0.26)


sound("atk_triple_blast", gen_triple_blast, 0.70, "ability", dict(BOLT_M, width=0.9, sub=(48, 0.25)))

# — Centaur —
def hooves(count=2, gap=0.07, name="hf"):
    """Hoofbeats on turf: dull knocks with grit in them."""
    out = np.zeros(n_of(gap * count + 0.16))
    for i in range(count):
        h = mixdown(thud(118 - 8 * i, 0.12, drive=1.8, name=name + str(i)),
                    grains(0.07, rate=160, band=(300, 2200), length=(0.002, 0.006), name=name + "g%d" % i) * 0.45,
                    pad_to(click(0.004, fc=1400, name=name + "c%d" % i) * 0.4, 0.12))
        at(out, i * gap, h * (1.0 - 0.15 * i))
    return out


sound("atk_centaur_arrow", lambda: with_(shaft("arrow", name="cta"), hooves(1, name="ctah") * 0.3), 0.54, "melee", FLY)
sound("atk_lance", lambda: with_(shaft("spear", 0.4, name="lnc"), hooves(2, 0.08, "lnch") * 0.4,
                                 pad_to(weight(90.0, 0.16, name="lncw") * 0.3, 0.4)),
      0.64, "ability", FLY)

# — Kraken —
sound("atk_tentacle_slap", lambda: with_(tentacle(0.42, size=1.3, name="krt"),
                                         water(0.3, size=1.0, bubbles=7, foam=0.6, name="krw") * 0.3),
      0.62, "melee", dict(PUNCHY, width=0.55))


def gen_ink_snare():
    """The Kraken's ink: squirted in a jet, a dark cloud of it billowing where it goes."""
    dur = 0.55
    jet = svf(pink(0.2, "inkj"), glide([(0.0, 3000), (1.0, 700)], 0.2), q=2.0, mode="bp") * exp_env(0.2, attack=0.004, decay=5.0)
    out = lay(dur, (0.0, norm(jet), 0.6), (0.0, water(0.34, size=1.1, bubbles=12, foam=0.9, name="ink"), 0.7),
              (0.02, squelch(0.3, f=(1100, 250), q=2.8, lumps=4, name="inkq"), 0.5),
              (0.1, gas(dur * 0.8, band=(200, 2600), fizz=0.3, name="inkg"), 0.4), (0.0, weight(88.0, 0.2, name="inkw"), 0.4))
    return reverb(saturate(out, 1.7), decay=0.6, damp=3200, mix=0.24, name="ink", predelay=0.008)


sound("atk_ink_snare", gen_ink_snare, 0.64, "ability", dict(BIG, sub=(46, 0.28)))

# — Sphinx —
def sphinx_voice(dur, name):
    """The Sphinx speaking: a whisper with a voice barely in it, and the purr of a very large
    cat under the words."""
    whisper = breath(dur, [(0.0, "oo"), (0.35, "ee"), (0.7, "ah"), (1.0, "oo")], name=name) * arc(dur, 0.3, 1.0, 1.3)
    purr = lowpass(throat(glide([(0.0, 30.0), (1.0, 24.0)], dur), dur, rough=0.3, breath_=0.5, name=name + "p"), 900)
    purr = highpass(purr, 100.0) * arc(dur, 0.3, 1.0, 1.2)
    return mixdown(norm(whisper) * 0.7, norm(purr) * 0.35)


def gen_sphinx_charm():
    """The Sphinx's Eye of Horus: a breath of old magic — a low reed, a whisper, the desert
    air shimmering."""
    dur = 0.4
    from sound_engines import _b_mystic
    reed = norm(_b_mystic(N["D4"], dur, "sphm"))
    out = mixdown(reed * 0.6, sphinx_voice(dur, "sphv") * 0.4,
                  shimmer(dur, 2600, 8000, rate=70, density=[(0.0, 0.4), (0.3, 1.0), (1.0, 0.1)], name="sphs") * 0.2,
                  pad_to(weight(104.0, 0.12, name="sphw") * 0.25, dur))
    return reverb(out, decay=0.7, damp=5000, mix=0.24, name="sph", predelay=0.008)


sound("atk_sphinx_charm", gen_sphinx_charm, 0.56, "bolt", dict(BOLT_M, tail=(0.7, 0.22)))


def gen_sphinx_riddle():
    """A riddle posed: a question you cannot answer, not a blast — the whisper, longer, asked
    twice (the second time is the echo off the stone)."""
    dur = 0.7
    from sound_engines import _b_mystic
    ask = sphinx_voice(0.5, "srv")
    out = lay(dur, (0.0, norm(_b_mystic(N["A3"], 0.6, "srm")), 0.45), (0.0, ask, 0.6), (0.2, lowpass(ask, 2400), 0.25),
              (0.0, shimmer(0.6, 2600, 8000, rate=60, density=[(0.0, 0.3), (0.4, 1.0), (1.0, 0.2)], name="srs"), 0.2))
    return reverb(out, decay=1.0, damp=4600, mix=0.3, name="sr", predelay=0.014)


sound("atk_sphinx_riddle", gen_sphinx_riddle, 0.60, "ability", dict(GHOST, tail=(1.0, 0.26)), held=True)

# — Cyclops —
sound("atk_giant_boulder", lambda: with_(boulder(0.5, size=1.25, name="cyc") * 0.9,
                                         roar(0.36, f0=(74.0, 92.0, 60.0), vowels=("uh", "ah", "aw"), size=1.6, rough=0.6,
                                              breath_=0.5, name="cycv") * 0.3),
      0.84, "melee", dict(HEAVY, sub=(42, 0.34)))
sound("atk_hurl_boulder", lambda: with_(boulder(1.0, size=1.5, name="cyh") * 0.95,
                                        roar(0.6, f0=(70.0, 96.0, 56.0), vowels=("uh", "ah", "aw"), size=1.6, rough=0.7,
                                             breath_=0.5, peak=0.3, name="cyhv") * 0.4,
                                        pad_to(creak(0.2, rate=(30.0, 60.0), body=(160.0, 420.0), q=3.0, name="cyhc") * 0.25, 0.4)),
      0.88, "heavy", dict(HEAVY, sub=(40, 0.38)), held=True)


def gen_eye_beam():
    """The Cyclops' eye: a searing beam — the sizzle of what it burns, rings of force throbbing
    off it unevenly, and the giant's growl behind it."""
    dur = 0.62
    sear = sizzle(dur, band=(1800, 8000), crackle=0.8, name="eys") * seg_env([(0.0, 0.0), (0.06, 1.0), (0.8, 0.8), (1.0, 0.0)], dur)
    beam = whoosh(dur, lo=800, hi=2200, end=600, peak=0.2, q=1.3, body=0.6, top=0.2, flutter=0.0, rise=1.0, fall=1.2, name="eyr")
    beam = beam * nlfo(dur, 12.0, 0.4, 1.0, "eyrl")
    growl = roar(dur * 0.8, f0=(92.0, 120.0, 84.0), vowels=("aw", "uh", "oo"), size=1.6, rough=0.7, breath_=0.4, name="eyv")
    out = mixdown(norm(sear) * 0.42, norm(beam) * 0.6, pad_to(growl, dur) * 0.3,
                  pad_to(whump(0.25, f=(140.0, 55.0), name="eyw"), dur) * 0.45)
    return reverb(saturate(out, 1.8), decay=0.7, damp=4200, mix=0.24, name="ey", predelay=0.008)


sound("atk_eye_beam", gen_eye_beam, 0.72, "ability", dict(width=0.85, transient=1.0, sub=(48, 0.28), tail=(0.7, 0.2)),
      held=True)

# — Harpy —
sound("atk_harpy_screech", lambda: reverb(saturate(with_(
    whoosh(0.3, lo=900, hi=4600, end=1000, peak=0.2, q=1.8, body=0.4, top=0.4, flutter=0.3, name="hps") * 0.7,
    wings(0.26, 14.0, 0.5, (400, 3600), "hpw"),
    pad_to(screech(0.22, f0=(1400.0, 2300.0, 1200.0), rough=0.7, breath_=0.45, name="hpv") * 0.34, 0.3)), 1.6),
    decay=0.5, damp=5200, mix=0.22, name="hp", predelay=0.006),
      0.58, "melee", dict(width=0.9, transient=1.0, pan=(-0.5, 0.5), tail=(0.5, 0.16)))


def gen_harpy_shriek():
    """The Harpy's Shriek: a bird's scream with a woman's in it — high, torn, breaking up into
    noise — and the beat of her wings throwing it outward."""
    dur = 0.6
    bird = screech(dur, f0=(1200.0, 2200.0, 1000.0), rough=0.75, breath_=0.45, peak=0.15, name="hsk")
    woman = voices(dur, [(0.0, 720.0), (0.15, 1320.0), (0.5, 1140.0), (1.0, 660.0)], [(0.0, "eh"), (0.4, "ah"), (1.0, "eh")],
                   count=2, spread=0.03, breath_=0.5, open_q=0.42,
                   amp_keys=((0.0, 0.0), (0.07, 1.0), (0.6, 0.75), (1.0, 0.0)), name="hsw")
    out = mixdown(bird * 0.6, fit(woman, n_of(dur)) * 0.35, wings(dur, 9.0, 0.6, (200, 2400), "hswg"),
                  whoosh(dur, lo=500, hi=4000, end=600, peak=0.15, q=1.2, body=0.6, top=0.3, name="hso") * 0.45)
    return reverb(saturate(out, 1.7), decay=0.8, damp=5200, mix=0.26, name="scr", predelay=0.008)


sound("atk_harpy_shriek", gen_harpy_shriek, 0.72, "ability", dict(width=0.95, transient=0.9, tail=(0.8, 0.22)), held=True)

# — Griffin —
def griffin_cry(dur, name):
    """A griffin: an eagle's scream on a lion's chest."""
    return mixdown(pad_to(screech(dur, f0=(900.0, 1500.0, 780.0), rough=0.6, breath_=0.4, name=name) * 0.6, dur),
                   pad_to(snarl(100.0, dur, rough=0.7, size=1.3, name=name + "l") * 0.5, dur))


sound("atk_griffin_rake", lambda: with_(swing("talon", 0.36, name="grr") * 0.85, wings(0.36, 8.5, 0.45, (180, 2200), "grw"),
                                        pad_to(weight(96.0, 0.16, name="grwt") * 0.3, 0.36)),
      0.64, "melee", dict(SWEEP, width=0.85))
sound("atk_talon_grab", lambda: with_(grab(0.42, size=1.1, name="tgb"), wings(0.4, 8.0, 0.5, (180, 2200), "tgbw"),
                                      pad_to(griffin_cry(0.3, "tgbv") * 0.3, 0.42)),
      0.66, "ability", dict(PUNCHY, width=0.7))

# — Anubis —
def sistrum(dur=0.26, count=18, name="sis"):
    """A sistrum shaken: a rattle of loose metal discs, which is what one is."""
    return rattle(dur, count=count, band=(2600, 8000), q=8.0, cluster=0.8, name=name)


sound("atk_anubis_bolt", lambda: with_(bolt("dark", 130.81, dur=0.44, name="anb") * 0.85,
                                       pad_to(sistrum(0.2, 6, "ansi") * 0.2, 0.44), dust(0.4, "andust") * 0.3),
      0.60, "bolt", dict(BOLT_M, tail=(0.7, 0.22)))
sound("atk_anubis_curse", lambda: with_(curse(0.7, "acr"), pad_to(sistrum(0.3, 7, "acs") * 0.2, 0.7),
                                        pad_to(snarl(150.0, 0.34, rough=0.8, size=0.95, name="acj") * 0.3, 0.7)),
      0.64, "ability", dict(GHOST, pingpong=(0.11, 0.36, 0.22)), held=True)


def gen_scales_judgment():
    """The Scales of Judgment: the scales' chains running out, the pans dropping — one heavy
    verdict on the ground — and what is found wanting beginning to burn."""
    dur = 0.85
    chains_ = rattle(0.3, count=12, band=(1400, 5000), q=4.5, cluster=0.6, name="sjc") * seg_env([(0.0, 0.5), (0.8, 1.0), (1.0, 0.2)], 0.3)
    verdict = slam("stone", 0.6, name="sjv")
    doom = halo((N["A2"], N["E3"], N["A3"]), 0.6, q=22, peak=0.2, breath=0.3, name="sjd")
    burn = sizzle(0.5, band=(1800, 7000), crackle=0.7, name="sjb") * arc(0.5, 0.2, 1.0, 1.4)
    out = lay(dur, (0.0, chains_, 0.4), (0.18, verdict, 0.9), (0.18, doom, 0.3), (0.24, norm(burn), 0.22),
              (0.18, dust(0.5, "sjs"), 0.3))
    return saturate(out, 1.5)


sound("atk_scales_judgment", gen_scales_judgment, 0.82, "heavy", dict(HEAVY, sub=(44, 0.32)))

# — Yokai —
sound("atk_mystic_bolt", lambda: with_(bolt("mystic", N["D5"]),
                                       grains(0.36, rate=40, band=(1400, 6000), length=(0.0006, 0.002),
                                              density=[(0.0, 0.5), (0.3, 1.0), (1.0, 0.2)], name="mysf") * 0.14),
      0.56, "bolt", BOLT_M)


def gen_binding_talisman():
    """The Yokai's ofuda: a paper talisman flicked out — the snap of it, paper riding the air —
    the note of the spirit that goes with it, and the bind drawing tight."""
    dur = 0.55
    paper = carried("flick", 0.3, "bts")
    from sound_engines import _b_mystic
    out = lay(dur, (0.0, norm(paper), 0.5), (0.0, norm(_b_mystic(N["A4"], 0.45, "btm")), 0.6),
              (0.26, creak(0.16, rate=(80.0, 170.0), body=(500.0, 1100.0), q=4.0, name="btc"), 0.3),
              (0.4, crack(0.016, name="btk"), 0.22))
    return reverb(out, decay=0.7, damp=5600, mix=0.24, name="bt", predelay=0.008)


sound("atk_binding_talisman", gen_binding_talisman, 0.60, "ability", dict(WIDE, tail=(0.8, 0.22)), held=True)

# — Golem —
sound("atk_stone_fist", lambda: with_(boulder(0.5, size=1.1, name="gf") * 0.6,
                                      saturate(bandpass(pink(0.45, "gfg"), 180, 1600), 2.8) * arc(0.45, 0.15, 1.0, 1.6) * 0.5,
                                      pad_to(strike("stone", 98, 0.3, hardness=1.0, name="gff") * 0.7, 0.5)),
      0.78, "melee", dict(HEAVY, sub=(44, 0.3)))
sound("atk_tremor_slam", lambda: mixdown(slam("stone", 0.9, name="trm"),
                                         pad_to(saturate(bandpass(pink(0.5, "trmg"), 160, 1400), 2.6) * arc(0.5, 0.1, 1.0, 1.4) * 0.3, 0.9)),
      0.86, "heavy", HEAVY)

# — Djinn —
def lamp_smoke(dur, name):
    return gas(dur, band=(300, 3400), fizz=0.3, pitchfall=False, name=name)


sound("atk_djinn_bolt", lambda: with_(bolt("mystic", 440.0, dur=0.44, name="djb") * 0.8, norm(lamp_smoke(0.44, "djg")) * 0.32,
                                      shimmer(0.44, 3000, 9000, rate=90, density=[(0.0, 0.5), (0.3, 1.0), (1.0, 0.1)], name="djs") * 0.22),
      0.58, "bolt", dict(BOLT_M, tail=(0.75, 0.24)))


def gen_djinn_whirlwind():
    """The Djinn's Whirlwind: the lamp's smoke wound up into a spinning wind that pulls at
    everything — the turning of it, its howl, and the shimmer of the wish."""
    dur = 0.8
    spin = np.sin(2 * np.pi * np.cumsum(glide([(0.0, 5.0), (1.0, 12.0)], dur) * nlfo(dur, 2.0, 0.93, 1.07, "dwj")) / SR)
    wind = whoosh(dur, lo=300, hi=2600, end=500, peak=0.5, q=1.5, body=0.8, top=0.2, flutter=0.2, rise=1.3, fall=1.3, name="dww")
    wind = wind * (0.6 + 0.4 * spin)
    howl = svf(pink(dur, "dwh"), glide([(0.0, 400.0), (0.6, 1100.0), (1.0, 700.0)], dur), q=5.0, mode="bp") * arc(dur, 0.5, 1.4, 1.4)
    out = mixdown(norm(wind) * 0.8, norm(howl) * 0.35, norm(lamp_smoke(dur, "dwg")) * 0.3,
                  shimmer(dur, 3000, 9000, rate=80, density=[(0.0, 0.3), (0.5, 1.0), (1.0, 0.3)], name="dws") * 0.2,
                  pad_to(weight(86.0, 0.25, name="dwt") * 0.35, dur))
    return reverb(saturate(out, 1.5), decay=0.9, damp=4600, mix=0.26, name="dw", predelay=0.01)


sound("atk_djinn_whirlwind", gen_djinn_whirlwind, 0.72, "ability", dict(width=1.0, transient=0.7, sub=(48, 0.22),
                                                                         pan=(-0.6, 0.6), tail=(1.0, 0.24)), held=True)

# — Fenrir —
sound("atk_fenrir_rend", lambda: with_(swing("claw", 0.4, name="fer") * 0.9,
                                       roar(0.36, f0=(70.0, 92.0, 60.0), size=1.5, rough=0.9, breath_=0.5, name="fev") * 0.38,
                                       pad_to(weight(78.0, 0.2, name="fet") * 0.35, 0.4)),
      0.72, "melee", dict(PUNCHY, sub=(48, 0.3)))


def gen_blood_frenzy():
    """Blood Frenzy: Fenrir savaging everything in reach — a run of rends, the tearing, a
    roar from the bottom of him, and a heart going hard."""
    dur = 0.7
    out = np.zeros(n_of(dur))
    for i in range(4):
        at(out, i * 0.075, swing("claw", 0.26, name="bfz%d" % i), 0.85 - 0.06 * i)
    at(out, 0.0, roar(0.65, f0=(66.0, 96.0, 58.0), size=1.5, rough=0.95, breath_=0.5, peak=0.25, name="bfzv") * 0.5)
    at(out, 0.1, heartbeat(0.14, 0.34, f0=80.0, name="bfzh") * 0.4)
    at(out, 0.12, squelch(0.3, f=(900, 300), q=2.6, lumps=4, name="bfzq") * 0.25)
    return out


sound("atk_blood_frenzy", gen_blood_frenzy, 0.80, "heavy", dict(PUNCHY, sub=(46, 0.32), width=0.85, tail=(0.6, 0.18)))

# — Chimera —
sound("atk_chimera_breath", lambda: with_(bolt("fire", 146.83, dur=0.44, name="chb") * 0.85,
                                          roar(0.34, f0=(100.0, 128.0, 86.0), size=1.3, rough=0.8, breath_=0.55, name="chv") * 0.3),
      0.64, "bolt", BOLT_M)
sound("atk_serpent_strike", lambda: with_(venom_spit(0.4, "srs"), snake_hiss(0.34, "srh") * 0.3,
                                          pad_to(norm(_fire(0.3, "srf")) * 0.3, 0.4)),
      0.62, "ability", dict(TIGHT, pan=(-0.4, 0.4)))


def _fire(dur, name):
    from sound_engines import _b_fire
    return _b_fire(220.0, dur, name)


# ═══════════════════════════ the specialists ═══════════════════════════

# — Alchemist —
def gen_vial():
    """The Alchemist's vial: a little glass thing thrown — its contents slopping and fizzing,
    the glass ticking against the others on his belt."""
    dur = 0.36
    fly = whoosh(dur, lo=700, hi=3200, end=700, peak=0.15, q=1.5, body=0.4, top=0.2, rise=1.0, fall=2.0, name="vlf") * tumble(dur, 9.0, name="vlt")
    slosh = bubbles(dur, count=12, f=(500, 1800), rise=(1.15, 1.6), size=(0.006, 0.02), span=(0.0, 0.8), name="vlb")
    fizz = gas(dur, band=(1500, 7000), fizz=1.0, name="vlg")
    out = lay(dur, (0.0, glass_clink(3, "vlc"), 0.45), (0.0, norm(fly), 0.65), (0.0, slosh * arc(dur, 0.2, 1.0, 1.4), 0.26),
              (0.0, norm(fizz), 0.22), (0.0, weight(112.0, 0.1, name="vlw"), 0.25))
    return reverb(saturate(out, 1.4), decay=0.4, damp=5600, mix=0.18, name="vl", predelay=0.004)


sound("atk_alchemist_vial", gen_vial, 0.52, "bolt", dict(BOLT_M, **BRIGHT))
sound("atk_explosive_potion", lambda: with_(lobbed("flask", 0.6, name="exp"),
                                            pad_to(sizzle(0.5, band=(2200, 8000), crackle=0.9, name="expf") *
                                                   arc(0.5, 0.15, 1.0, 1.2) * 0.3, 0.6)),
      0.66, "ability", dict(BIG, sub=(50, 0.25), **BRIGHT))
sound("atk_acid_flask", lambda: with_(lobbed("flask", 0.55, name="acf"),
                                      pad_to(gas(0.45, band=(1400, 7000), fizz=1.0, name="acfg") * 0.3, 0.55),
                                      glass_clink(2, "acfc") * 0.3),
      0.66, "ability", dict(BIG, sub=(50, 0.25), **BRIGHT))

# — Puppeteer —
sound("atk_puppet_string", lambda: chain("string"), 0.54, "melee", dict(width=0.75, transient=1.5, tail=(0.4, 0.16)))


def wood_clack(dur=0.16, count=5, name="wcl"):
    """A marionette's limbs knocking: small dry wooden clacks."""
    return grains(dur, rate=count / dur, band=(700, 2400), length=(0.003, 0.008), q=(0.8, 1.2), name=name)


def gen_puppet_pull():
    """Puppet String: a string thrown and hauled on — plucked as it lands, then creaking as it
    takes the weight."""
    dur = 0.55
    out = fit(chain("string", 0.4, name="ppl"), n_of(dur))
    at(out, 0.24, creak(0.24, rate=(90.0, 200.0), body=(600.0, 1300.0), q=4.0, name="pplc") * 0.35)
    at(out, 0.2, pluck(N["A4"], 0.3, t60=0.25, bright=0.85, pick=0.1, name="pplp") * 0.3)
    return out


sound("atk_puppet_pull", gen_puppet_pull, 0.58, "ability", dict(width=0.75, transient=1.5, tail=(0.4, 0.16)), held=True)


def gen_marionette():
    """Marionette: a handful of strings at once — a jangle of them, plucked together — and the
    wooden limbs they jerk knocking against each other."""
    dur = 0.55
    strings = np.zeros(n_of(dur))
    for i, f in enumerate((N["E4"], N["A4"], N["B4"], N["E5"])):
        at(strings, 0.005 * i, pluck(f, 0.4, t60=0.28, bright=0.85, pick=0.12, name="mar%d" % i) * (0.9 - 0.1 * i))
    whip = whoosh(0.3, lo=1400, hi=6000, end=1600, peak=0.15, q=1.6, body=0.2, top=0.3, name="mara")
    out = lay(dur, (0.0, whip, 0.4), (0.0, norm(strings), 0.6), (0.2, wood_clack(0.2, 6, "marw"), 0.4),
              (0.36, crack(0.014, name="mark"), 0.2))
    return reverb(saturate(out, 1.6), decay=0.4, damp=6000, mix=0.18, name="mar", predelay=0.004)


sound("atk_marionette", gen_marionette, 0.60, "ability", dict(width=0.8, transient=1.5, tail=(0.45, 0.16)), held=True)

# — Gambler —
def chips(dur=0.14, count=4, name="chp"):
    """Casino chips: clay on clay, a few short dull clicks."""
    return rattle(dur, count=count, band=(1500, 4200), q=6.0, cluster=1.0, name=name)


sound("atk_gambler_card", lambda: with_(thrown("card", dur=0.28, name="gcd"),
                                        pad_to(weight(120.0, 0.08, name="gcw") * 0.12, 0.28)),
      0.46, "tiny", FLY)


def gen_lucky_streak():
    """Lucky Streak: a hand dealt fast and a run of luck paying out — the riffle, a few chips
    pushed across the felt, and five soft rising notes that mean a win."""
    dur = 0.7
    riffle = carried("flick", 0.3, "lkr")
    out = lay(dur, (0.0, norm(riffle), 0.5), (0.1, chips(0.34, 9, "lkc"), 0.3),
              (0.16, chime_run(0.4, lo=1300, hi=4200, up=True, notes=5, ring=0.22, name="lkw"), 0.34),
              (0.2, shimmer(0.5, 4000, 11000, density=[(0.0, 0.4), (0.4, 1.0), (1.0, 0.2)], name="lks"), 0.24))
    return reverb(out, decay=0.6, damp=6400, mix=0.22, name="lk", predelay=0.008)


sound("atk_lucky_streak", gen_lucky_streak, 0.60, "ability", dict(WIDE, tail=(0.7, 0.2), **BRIGHT), held=True)
sound("atk_wild_card", lambda: with_(thrown("card", dur=0.4, name="wcd") * 1.0,
                                     shimmer(0.4, 3600, 10000, density=[(0.0, 0.3), (0.2, 1.0), (1.0, 0.1)], name="wcc") * 0.2,
                                     pad_to(whoosh(0.3, lo=900, hi=4200, end=900, peak=0.2, q=1.6, body=0.3, top=0.3, name="wca") * 0.45, 0.4)),
      0.54, "melee", dict(FLY, **BRIGHT))

# — Blacksmith —
sound("atk_hammer_swing", lambda: with_(swing("heavy", 0.4, name="hms"), pad_to(weight(80.0, 0.2, name="hmsw") * 0.4, 0.4)),
      0.68, "melee", dict(SWEEP, sub=(50, 0.25)))
sound("atk_hammer_throw", lambda: thrown("hammer", 0.5), 0.70, "ability", dict(SWEEP, sub=(48, 0.3)))

# — Pirate —
sound("atk_flintlock", lambda: gun("flint", 0.5), 0.80, "melee", dict(GUN_M, width=0.65, transient=1.8, tail=(0.9, 0.24)))
sound("atk_grapple", lambda: with_(chain("rope", name="grp"),
                                   pad_to(rattle(0.1, count=3, band=(1400, 5000), q=4.5, cluster=1.0, name="grph") * 0.3, 0.3)),
      0.58, "melee", dict(width=0.7, transient=1.4, tail=(0.35, 0.14)))


def gen_cannon_fire():
    """The Pirate's cannon going off: the fuse catching, the roar of black powder and the
    smoke — and the ball's whistle leaving."""
    dur = 0.9
    whistle = svf(noise(0.3, "cnw"), glide([(0.0, 2400.0), (1.0, 1300.0)], 0.3), q=3.0, mode="bp")
    return lay(dur, (0.0, sizzle(0.07, band=(2800, 8500), crackle=0.9, name="cnf"), 0.3), (0.06, gun("cannon", 0.7, name="cng"), 1.0),
               (0.1, norm(whistle) * exp_env(0.3, attack=0.02, decay=4.0), 0.15))


sound("atk_cannonball", gen_cannon_fire, 0.84, "heavy", dict(HEAVY, comp=(0.2, 4.0)))

# — Chef —
sound("atk_chef_knife", lambda: with_(swing("steel", 0.28, name="chk") * 0.9,
                                      pad_to(edge("shing", 0.1, "chks") * arc(0.1, 0.2, 1.0, 1.6) * 0.2, 0.2)),
      0.56, "melee", dict(SWEEP, **BRIGHT))
sound("atk_meat_hook", lambda: with_(chain("iron", 0.4, name="mhk"),
                                     pad_to(squelch(0.16, f=(1200, 300), q=2.8, lumps=3, name="mhkq") * 0.3, 0.5)),
      0.62, "ability", dict(width=0.8, transient=1.5, tail=(0.45, 0.16)))


def gen_flambe():
    """The Chef's Flambé: gas ignition — the igniter ticking, then the whump of a pan going
    up, not a fireball."""
    dur = 0.46
    out = np.zeros(n_of(dur))
    for i in range(3):
        at(out, 0.005 + i * 0.022, snap("tick", "fl%d" % i) * (0.55 - 0.12 * i))
    catch = gas(0.3, band=(200, 3600), fizz=0.35, name="flw") * seg_env([(0.0, 0.0), (0.04, 1.0), (1.0, 0.0)], 0.3)
    at(out, 0.07, catch * 1.1)
    at(out, 0.07, whump(0.2, f=(170.0, 62.0), name="fll") * 0.6)
    at(out, 0.09, fire(dur * 0.8, low=110, roar=0.7, crackles=10, name="flf") * 0.7)
    return reverb(saturate(out, 1.9), decay=0.5, damp=4400, mix=0.22, name="fl")


sound("atk_flambe", gen_flambe, 0.66, "ability", dict(BIG, sub=(50, 0.28)))

# — Musician —
def reed(f, dur, name):
    """A reed's note: air singing on a harmonic series, breathy, swelling in."""
    return halo((f, f * 2, f * 3, f * 4, f * 5), dur, q=60, peak=0.25, gains=(1.0, 0.6, 0.45, 0.25, 0.15), breath=0.25,
                wander=0.2, name=name)


def gen_song_wave():
    """The Musician's riff: two quick notes on a reed, and the air they push."""
    dur = 0.5
    out = lay(dur, (0.0, reed(N["A3"] * 2, 0.2, "sg1"), 0.6), (0.11, reed(N["C4"] * 2, 0.32, "sg2"), 0.6),
              (0.0, wavefront("sonic", 0.42, name="sgw"), 0.42))
    return reverb(out, decay=0.6, damp=5600, mix=0.22, name="sg", predelay=0.008)


sound("atk_song_wave", gen_song_wave, 0.60, "melee", dict(width=0.85, transient=0.9, tail=(0.8, 0.24)))


def gen_hypnotic_melody():
    """The Musician's Hypnotic Melody: three notes of an A-minor arpeggio on a lyre, doubled by
    a breathy reed, with the echo of it hanging. This is the one place in the game a melodic
    figure belongs — it is the ability."""
    dur = 0.7
    out = np.zeros(n_of(dur))
    for i, f in enumerate((N["A4"], N["C5"], N["E5"])):
        note = mixdown(pluck(f, 0.5, t60=0.7, bright=0.5, pick=0.2, name="hm%d" % i) * 0.8, pad_to(reed(f, 0.4, "hmf%d" % i) * 0.3, 0.5))
        at(out, i * 0.09, note * (0.9 - 0.1 * i))
    out = delay_fx(out, 0.14, feedback=0.3, mix=0.25, taps=3)
    hush = shimmer(dur, 3500, 10000, rate=60, density=[(0.0, 0.3), (0.5, 1.0), (1.0, 0.3)], name="hms") * 0.18
    return reverb(mixdown(norm(out) * 0.8, hush), decay=1.0, damp=7000, mix=0.30, name="hm", predelay=0.01)


sound("atk_hypnotic_melody", gen_hypnotic_melody, 0.58, "ability", dict(WIDE, tail=(1.2, 0.26)), held=True)
sound("atk_musician_boom", lambda: sonic_boom((110.0, 220.0, 329.63, 440.0), name="mbm"), 0.78, "heavy", dict(BIG, sub=(42, 0.3)))

# — Astronomer —
sound("atk_star_bolt", lambda: bolt("star", N["A4"]), 0.52, "bolt", dict(BOLT_M, **BRIGHT))


def gen_meteor():
    """A falling star called down: it comes in from a long way up — a rush that builds, fire
    on it, the air torn — and is gone into the ground (its landing is its blast)."""
    dur = 0.85
    fall = whoosh(dur, lo=200, hi=2400, end=900, peak=0.8, q=1.3, body=0.9, top=0.2, flutter=0.2, rise=2.2, fall=1.0, name="mtf")
    burn = fire(dur, low=100, roar=0.8, crackles=18, name="mtb") * swell_in(dur, 1.4)
    tear = svf(noise(dur, "mtt"), glide([(0.0, 3600.0), (1.0, 1200.0)], dur), q=4.0, mode="bp") * swell_in(dur, 2.0)
    trail = shimmer(dur, 3500, 11000, rate=80, density=[(0.0, 1.0), (0.6, 0.6), (1.0, 0.2)], name="mts")
    out = mixdown(fall * 0.8, norm(burn) * 0.5, norm(tear) * 0.2, trail * 0.2)
    return reverb(saturate(out, 1.6), decay=0.9, damp=3600, mix=0.26, name="mt", predelay=0.01, size=1.4)


sound("atk_meteor", gen_meteor, 0.80, "heavy", dict(HEAVY, sub=(46, 0.28)), held=True)

# — Runesmith —
sound("atk_rune_bolt", lambda: bolt("rune", N["A2"]), 0.52, "bolt", BOLT_M)

# — Shapeshifter —
def gen_shift_strike():
    """The Shapeshifter's fist: the limb becoming something else on the way in — a wet
    stretch, a snarl that wasn't there a moment ago — and landing."""
    dur = 0.42
    morph = svf(pink(0.2, "shm"), glide([(0.0, 400), (1.0, 2600)], 0.2), q=2.4, mode="bp") * swell_in(0.2, 1.8)
    out = lay(dur, (0.0, norm(morph), 0.35), (0.02, squelch(0.14, f=(700, 1500), q=2.6, lumps=2, name="shq"), 0.25),
              (0.12, punch(False, name="shp"), 0.9), (0.08, snarl(130.0, 0.24, rough=0.75, name="shv"), 0.3))
    return out


sound("atk_shift_strike", gen_shift_strike, 0.68, "melee", dict(PUNCHY, sub=(52, 0.26)))
sound("atk_feral_roar", lambda: reverb(mixdown(roar(0.8, f0=(92.0, 140.0, 74.0), vowels=("uh", "ah", "aw"), size=1.3, rough=0.85,
                                                    breath_=0.5, peak=0.2, name="frr"),
                                               whoosh(0.6, lo=300, hi=2600, end=400, peak=0.15, q=1.2, body=0.8, top=0.1, name="frw") * 0.4,
                                               pad_to(weight(84.0, 0.3, name="frt") * 0.4, 0.8)),
                                       decay=1.0, damp=3600, mix=0.28, name="frr", predelay=0.014, size=1.4),
      0.80, "heavy", dict(width=0.95, transient=0.9, sub=(46, 0.3), tail=(1.1, 0.24)), held=True)
