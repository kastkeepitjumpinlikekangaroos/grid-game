#!/usr/bin/env python3
"""Which sounds are a struck piece of metal, and which are full of clinks? Measures both, for
every sound.

The audio counterpart of `render_audit.py`. Two faults this set kept falling into, neither of
which shows in the numbers anyone normally checks (level, length, band):

CLANG. A pitched layer with an instant attack and an exponential decay, whose partials are not a
harmonic series: the acoustic definition of a struck metal object, and what a fire bolt, a thrown
axe and a rope all came out as — "a tin can".

  1. An STFT of the mono mix (2048 window, 256 hop).
  2. From 25ms after the onset, a bin is a *peak* if it is a local maximum and
     stands 14dB above the 20th percentile of the spectrum within half an octave
     of it (a percentile, not a median, so a dense comb of partials — an FM
     spectrum — still stands out against the gaps between its own teeth).
  3. Peaks are chained frame to frame while they stay within 1.5% in frequency;
     a chain lasting 80ms or more is a *ringing partial*. Noise never rings that
     long in one place, a glide never stays in one place, a ring does.
  4. A ringing partial is *struck* if it dies away from a peak near its start,
     rather than swelling or holding as a voice or a held note does.

  ring    share of the 150Hz-10kHz energy (over 600ms) that is ringing partials
  struck  share of it that is struck partials
  inharm  share of the struck energy no harmonic series explains
  clang   struck x (0.35 + 0.65 inharm) x (0.3 + 0.7 x its share in 400Hz-5kHz):
          struck, inharmonic, in the band where a can rings

A voice rings but is not struck; a string or a chord is struck but harmonic, so
both score low. A bolt of fire should not ring at all.

TICKS. Brief events that poke out of a sound: a few milliseconds of something bright, there and
gone — "clinky". The clang measure cannot see them (nothing under 80ms rings), and they were its
blind spot twice: thrown weapons' clinks, and then a "glitter" made of tiny chimes that put a
handful of pins in a katana's cut and forty other sounds.

  1. A spectrogram in 24 bands from 1.2 to 14kHz (about a critical band each), 1.5ms a frame,
     smoothed to about 6ms.
  2. A band-frame is a *tick* if it stands 12dB above that band's loudest moment 8-30ms EARLIER
     and 9dB above its quietest within 25ms AFTER (it is over at once: not the start of something
     that stays), within 30dB of the sound's loudest band-frame. The sound's own first onset is
     how it begins, not a tick in it.
  3. Ticks touching in time and band are one event.

  ticks   how many such events are in the sound

Smooth noise, however bright, has none; a whoosh has none; a cloud of chimes has dozens. Many
sounds are MEANT to be several events — hoofbeats, three rounds, a chain's links, lightning
jumping, rubble, a glitch — and are listed (`MEANT_TO_TICK`) with why; anything else with more
than TICK_LIMIT is a sound with clinks in it.

    python3 scripts/sound_audit.py                 # every sound, worst clang first
    python3 scripts/sound_audit.py --ticks         # worst ticks first
    python3 scripts/sound_audit.py atk_axe          # only matching names
    python3 scripts/sound_audit.py --check          # exit 1 if anything rings or ticks
                                                    # that should not

`--check` fails on any sound with clang over CLANG_LIMIT that is not in MEANT_TO_RING — the
sounds that are, legitimately, a struck bell or a plucked string — or with more ticks than
TICK_LIMIT that is not in MEANT_TO_TICK. SND_DIR overrides the directory read, to audit a build
before it replaces sounds/. Needs numpy.
"""
import glob
import os
import sys
import wave

import numpy as np
from numpy.lib.stride_tricks import sliding_window_view

SR = 44100
NFFT = 2048
HOP = 256
FREQS = np.fft.rfftfreq(NFFT, 1.0 / SR)
MIN_RING_S = 0.08
CLANG_LIMIT = 0.12

# Sounds that are meant to ring: a melody, a chord, a string, a bell that is the
# point of the sound. Anything not here that rings inharmonically is a fault.
MEANT_TO_RING = {
    "atk_hypnotic_melody",   # the Musician's melody IS a figure on a lyre
    "atk_lute_chord",        # a plucked chord
    "atk_lullaby",           # three plucked notes: the tune is the ability
    "atk_song_wave",         # two notes on a reed
    "atk_charm_bolt",        # a harp run
    "cast_ench_blink",       # the same harp, running out
    "atk_lucky_streak",      # a win paying out: a run of chimes
    "cast_astral_project",   # a stroke up the chimes and one down
    "atk_puppet_string", "atk_puppet_pull", "atk_marionette",   # strings, plucked
    "cast_aegis",            # a shield of holy light: voices holding a chord
    "spawn",                 # the respawn figure: three soft notes
    "death",                 # the knell
    "hit_dealt",             # the hitmarker's two notes have to cut through everything
    "atk_howl",              # a held, sung note — a voice rings, it is not struck
}

TICK_LIMIT = 4

# Sounds that are meant to be several brief events, by what they are. Anything not here with more
# than TICK_LIMIT ticks has clinks in it that nobody put there on purpose.
MEANT_TO_TICK = set("""
    atk_lag_spike atk_virus_glitch atk_glitch_bolt atk_data_bolt cast_system_hop cast_glitch_blink
    atk_beam_railgun atk_time_bolt atk_electric_burst atk_ion_shot atk_chain_lightning atk_tesla_fork
    atk_tesla_arc atk_tesla_chain atk_lightning_crack atk_thunder_strike atk_sting atk_turret_laser
    atk_sniper_beam atk_inq_chain
    atk_triple_blast atk_suppress_fire atk_strafing_run atk_mech_gun atk_pilot_guns atk_multi_shot
    atk_knife_spray atk_card_fan atk_ember_fan atk_prism atk_flurry atk_berserk_axe atk_hydra_spit
    atk_hydra_heads atk_bear_maul atk_blood_frenzy atk_axe_spin
    atk_chain_bolt atk_lockdown atk_shackles atk_death_grip atk_meat_hook atk_scales_judgment
    atk_bone_cage atk_bone_axe atk_bone_boomerang atk_marionette cast_snare_mine cast_anvil_trap
    cast_headless_charge cast_bull_charge cast_gallop cast_ground_charge cast_rage_charge cast_lion_charge
    cast_beast_charge atk_centaur_arrow atk_lance
    atk_bat_swarm cast_aerial_charge cast_sky_dance cast_dive_bomb cast_sky_dive cast_swoop atk_talon_grab
    atk_griffin_rake atk_hawk_dive atk_pin_down atk_gust atk_flame_trail atk_harpy_screech atk_harpy_shriek
    cast_mist_form cast_rebirth atk_fiend_eye
    atk_ice_quake atk_tremor_slam atk_avalanche_crush atk_earthsplitter atk_grasping_dead atk_ground_pound
    atk_seismic_root atk_shield_bash atk_mech_slam atk_horn_flip atk_raise_dead atk_boulder_rumble
    atk_giant_boulder atk_hurl_boulder atk_stone_fist atk_ape_throw atk_ice_chunk atk_shell_charge
    atk_eruption cast_stone_wall cast_burrow cast_open_grave cast_leap
    explosion blast_cannonball blast_mud blast_rocket blast_cluster blast_bomb blast_acid blast_magma
    atk_inferno_blast atk_death_coil atk_hailstorm atk_freeze_ray atk_beam_ice cast_ice_blink cast_ice_wall
    atk_frost_nova cast_flame_burst
    cast_leaf_cloak cast_natures_leap atk_vine_whip atk_druid_vine atk_root_pull atk_mantis_strike atk_entangle
    atk_root_burst atk_gale cast_dust_devil cast_wind_form atk_sand_blast
    atk_claw_swipe atk_bear_swipe atk_fenrir_rend atk_talon_swipe atk_ghoul_rake atk_mantis_scythe
    cast_rune_trap cast_binding_rune cast_web_trap cast_deploy_mine cast_carapace trap_place trap_snap
    atk_lucky_streak atk_hypnotic_melody atk_lullaby atk_lute_chord atk_charm_bolt cast_ench_blink
    cast_astral_project cast_illusion cast_time_stop atk_temporal_rift atk_puppet_string atk_puppet_pull
    spawn death
""".split())
# Why, group by group as they are set out above: a glitch is a stutter; electricity jumps; a fan,
# a burst or a flurry is three or more in one sound; a chain is its links and bones knock; hooves;
# wings (a beat is an event); the ground giving and what it throws up; a blast's debris; fire's
# and ice's crackle; leaves and grit; a claw's tearing; a trap's mechanism and a rune's strokes;
# and the tunes, which are notes.

_TICK_NFFT, _TICK_HOP = 512, 64
_TICK_EDGES = np.geomspace(1200.0, 14000.0, 25)
_TICK_FREQ = np.fft.rfftfreq(_TICK_NFFT, 1.0 / SR)
_TICK_BANK = np.stack([((_TICK_FREQ >= _TICK_EDGES[i]) & (_TICK_FREQ < _TICK_EDGES[i + 1])).astype(float)
                       for i in range(len(_TICK_EDGES) - 1)])


def ticks(x, rise=12.0, fall=9.0):
    """How many brief events poke out of `x` (see TICKS in this file's docstring)."""
    if len(x) < 4 * _TICK_NFFT:
        x = np.pad(x, (0, 4 * _TICK_NFFT - len(x)))
    x = np.pad(x, (_TICK_NFFT, _TICK_NFFT))
    win = np.hanning(_TICK_NFFT)
    frames = (len(x) - _TICK_NFFT) // _TICK_HOP + 1
    idx = np.arange(_TICK_NFFT)[None, :] + _TICK_HOP * np.arange(frames)[:, None]
    power = np.abs(np.fft.rfft(x[idx] * win, axis=1)) ** 2
    bands = power @ _TICK_BANK.T
    k = np.ones(3) / 3.0
    bands = np.apply_along_axis(lambda v: np.convolve(v, k, mode="same"), 0, bands)
    db = 10 * np.log10(bands + 1e-12)
    n_t, n_b = db.shape
    top = db.max()
    f8, f30, f25 = int(0.008 * SR / _TICK_HOP), int(0.030 * SR / _TICK_HOP), int(0.025 * SR / _TICK_HOP)
    tick = np.zeros_like(db, dtype=bool)
    for t in range(n_t):
        a, b = max(0, t - f30), max(0, t - f8)
        before = db[a:b].max(axis=0) if b > a else np.full(n_b, -120.0)
        after = db[t + 3:min(n_t, t + f25)].min(axis=0) if t + 4 < n_t else np.full(n_b, -120.0)
        tick[t] = (db[t] - before >= rise) & (db[t] - after >= fall) & (db[t] > top - 30)
    loud = np.nonzero(db.max(axis=1) > top - 30)[0]
    if len(loud):
        tick[:loud[0] + int(0.012 * SR / _TICK_HOP)] = False       # how the sound begins is not a tick in it
    events = 0
    seen = np.zeros_like(tick)
    for t, b in zip(*np.nonzero(tick)):
        if seen[t, b]:
            continue
        events += 1
        stack = [(t, b)]
        seen[t, b] = True
        while stack:
            tt, bb = stack.pop()
            for dt in (-2, -1, 0, 1, 2):
                for d_b in (-1, 0, 1):
                    u, v = tt + dt, bb + d_b
                    if 0 <= u < n_t and 0 <= v < n_b and tick[u, v] and not seen[u, v]:
                        seen[u, v] = True
                        stack.append((u, v))
    return events


# log-frequency grid the baseline is taken on: 48 bins an octave, 60Hz-16kHz
_LOG_F = np.geomspace(60.0, 16000.0, int(48 * np.log2(16000.0 / 60.0)))


def load(path):
    with wave.open(path, "rb") as f:
        ch = f.getnchannels()
        d = np.frombuffer(f.readframes(f.getnframes()), dtype=np.int16).astype(float) / 32768.0
    return d.reshape(-1, 2).mean(axis=1) if ch == 2 else d


def _stft(x):
    win = np.hanning(NFFT)
    frames = max(1, (len(x) - NFFT) // HOP + 1)
    out = np.zeros((frames, NFFT // 2 + 1))
    for i in range(frames):
        seg = x[i * HOP:i * HOP + NFFT]
        if len(seg) < NFFT:
            seg = np.pad(seg, (0, NFFT - len(seg)))
        out[i] = np.abs(np.fft.rfft(seg * win)) ** 2
    return out


def _baseline(db):
    """20th percentile within +-half an octave of every bin, per frame."""
    logdb = np.stack([np.interp(_LOG_F, FREQS, row) for row in db])
    padded = np.pad(logdb, ((0, 0), (24, 24)), mode="edge")
    base = np.percentile(sliding_window_view(padded, 49, axis=1), 20, axis=-1)
    return np.stack([np.interp(FREQS, _LOG_F, row) for row in base])


def _harmonic_share(freqs, energy):
    """The most ringing energy any one harmonic series (f0 45Hz-1.6kHz, up to the
    24th harmonic, 3% tolerance) accounts for."""
    best = 0.0
    for f0 in np.geomspace(45.0, 1600.0, 900):
        h = freqs / f0
        k = np.round(h)
        near = (np.abs(h - k) <= 0.03 * np.maximum(k, 1.0) ** 0.5) & (k >= 1) & (k <= 24)
        best = max(best, energy[near].sum())
    return best / (energy.sum() + 1e-18)


def analyse(x):
    if len(x) < NFFT:
        x = np.pad(x, (0, NFFT - len(x)))
    spec = _stft(x)
    power = spec.sum(axis=1)
    if power.max() <= 0:
        return None
    onset = int(np.argmax(power > power.max() * 0.05))
    start = onset + int(0.025 * SR / HOP)
    end = min(len(spec), onset + int(0.6 * SR / HOP))
    if end - start < 4:
        return dict(ring=0.0, struck=0.0, inharm=0.0, clang=0.0, parts=[])
    band = (FREQS >= 150) & (FREQS <= 10000)
    total = spec[start:end][:, band].sum()
    db = 10 * np.log10(spec[start:end] + 1e-14)
    base = _baseline(db)
    chains, live = [], []
    for fi in range(end - start):
        p, d, b = spec[start + fi], db[fi], base[fi]
        pk = (d[1:-1] > d[:-2]) & (d[1:-1] >= d[2:]) & (d[1:-1] - b[1:-1] >= 14.0)
        idx = np.nonzero(pk)[0] + 1
        idx = idx[(FREQS[idx] >= 150) & (FREQS[idx] <= 10000) & (d[idx] > d.max() - 50)]
        taken, nxt = set(), []
        for k in idx:
            a, c = d[k - 1], d[k + 1]
            off = 0.5 * (a - c) / (a - 2 * d[k] + c + 1e-12)
            f = FREQS[k] + off * SR / NFFT
            e = p[k - 1] + p[k] + p[k + 1]
            hit = next((ci for ci, ch in enumerate(live)
                        if ci not in taken and abs(ch[0] - f) <= 0.015 * f + 12), None)
            if hit is None:
                ch = [f, e, 1, [f], [e]]
            else:
                taken.add(hit)
                ch = live[hit]
                ch[0] = 0.7 * ch[0] + 0.3 * f
                ch[1] += e
                ch[2] += 1
                ch[3].append(f)
                ch[4].append(e)
            nxt.append(ch)
        chains.extend(ch for ci, ch in enumerate(live) if ci not in taken)
        live = nxt
    chains.extend(live)
    ringing = [c for c in chains if c[2] >= MIN_RING_S * SR / HOP]
    if not ringing:
        return dict(ring=0.0, struck=0.0, inharm=0.0, clang=0.0, parts=[])
    ring = min(1.0, sum(c[1] for c in ringing) / (total + 1e-18))
    struck = [c for c in ringing if _is_struck(c[3], c[4])]
    if not struck:
        return dict(ring=ring, struck=0.0, inharm=0.0, clang=0.0, parts=[])
    fr = np.array([c[0] for c in struck])
    en = np.array([c[1] for c in struck])
    share = min(1.0, en.sum() / (total + 1e-18))
    inharm = 1.0 - _harmonic_share(fr, en)
    mid = en[(fr >= 400) & (fr <= 5000)].sum() / en.sum()
    clang = share * (0.35 + 0.65 * inharm) * (0.3 + 0.7 * mid)
    order = np.argsort(-en)[:5]
    return dict(ring=ring, struck=share, inharm=inharm, clang=clang,
                parts=[(int(fr[i]), float(en[i] / en.sum())) for i in order])


def _is_struck(freqs, energy):
    """Dies away from a peak near its start: struck, rather than sung or swelled.

    (Frequency steadiness would be the other half of the physics, but a 46ms
    window measures a 200Hz partial to a few percent, which is more than a
    voice's vibrato, so it cannot tell them apart here.)"""
    e = np.asarray(energy, dtype=float)
    k = int(np.argmax(e))
    return k <= max(2, 0.4 * len(e)) and e[-1] < 0.5 * e[k]


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    check = "--check" in sys.argv
    by_ticks = "--ticks" in sys.argv
    match = args[0] if args else ""
    root = os.environ.get("SND_DIR", os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "sounds"))
    rows = []
    for p in sorted(glob.glob(os.path.join(root, "*.wav"))):
        name = os.path.basename(p)[:-4]
        if "music" in name or match not in name:
            continue
        x = load(p)
        r = analyse(x)
        if r is not None:
            r["ticks"] = ticks(x)
            rows.append((name, r))
    rows.sort(key=lambda kv: -kv[1]["ticks" if by_ticks else "clang"])
    print("%-24s %6s %6s %6s %6s %5s  %s" % ("sound", "clang", "ring", "struck", "inharm", "ticks",
                                              "loudest struck partials (Hz, share)"))
    rings, clinks = [], []
    for name, r in rows:
        flag = ""
        if r["clang"] > CLANG_LIMIT:
            flag = "  (meant to ring)" if name in MEANT_TO_RING else "  <- rings like struck metal"
            if name not in MEANT_TO_RING:
                rings.append(name)
        if r["ticks"] > TICK_LIMIT:
            flag += "  (meant to tick)" if name in MEANT_TO_TICK else "  <- has clinks in it"
            if name not in MEANT_TO_TICK:
                clinks.append(name)
        print("%-24s %6.3f %6.3f %6.3f %6.2f %5d  %s%s" % (name, r["clang"], r["ring"], r["struck"], r["inharm"],
                                                         r["ticks"], " ".join("%d:%.2f" % pp for pp in r["parts"]), flag))
    total = sum(r["ticks"] for _, r in rows)
    quiet = sum(1 for _, r in rows if r["ticks"] == 0)
    print("\n%d sounds, %d ticks between them, %d with none" % (len(rows), total, quiet))
    if rings:
        print("%d sound(s) ring like struck metal: %s" % (len(rings), ", ".join(rings)))
    if clinks:
        print("%d sound(s) have clinks in them: %s" % (len(clinks), ", ".join(clinks)))
    if check and (rings or clinks):
        sys.exit(1)


if __name__ == "__main__":
    main()
