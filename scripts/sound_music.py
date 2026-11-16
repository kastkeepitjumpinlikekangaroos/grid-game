#!/usr/bin/env python3
"""The two music loops: the menus' (calm) and a match's (faster, more intense), both in A minor.

Synthesized like everything else here, and mixed as they go; `generate_sounds.py` only levels and
limits them (no presence dip, which would take the air off the pads).
"""
import numpy as np

from sound_kit import *  # noqa: F401,F403


def _debris(dur, count=12, band=(500, 5000), decay=(9.0, 22.0), spread=(0.02, 0.8),
           wet=0.0, name="deb"):
    """The rubble of that impact, as rubble was made when the track was: the kit's `debris` has
    changed since, and the track must not."""
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


def _impact(dur=1.1, size=1.0, name="boom"):
    """The impact that opens the battle theme's second section: the detonation as it was when the
    track was made, kept here so the track stays the track."""
    n = n_of(dur)
    sub_dur = dur * 0.5
    sub = sine(seg_env([(0.0, 95 * size), (0.25, 52 * size), (1.0, 26 * size)], sub_dur), sub_dur)
    sub *= exp_env(sub_dur, attack=0.004, decay=3.2)
    sub = saturate(sub, 2.4)

    blast = svf(pink(dur, name + "b"), seg_env([(0.0, 8000), (0.08, 2400), (0.45, 700), (1.0, 220)], dur), q=1.6)
    blast *= exp_env(dur, attack=0.002, decay=3.6)
    crack = saturate(click(0.035, fc=1800, name=name + "c", shape=1.4), 3.2)

    shrapnel = _debris(dur, count=22, band=(350, 4500), spread=(0.05, 0.78), name=name + "d")

    out = np.zeros(n)
    out[:len(sub)] += sub
    out += blast * 0.75
    out[:len(crack)] += crack * 0.85
    out += shrapnel * 0.5
    out = saturate(out, 1.8)
    return reverb(out, decay=1.3 * size, damp=3200, mix=0.32, name="boom", predelay=0.014, size=1.5)


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

    at_st(buf, bar * 8, _impact(dur=0.9, size=0.7, name="battleimp"), 0.22)

    buf = sidechain(buf, kicks, beat, amount=0.42, recover=0.22)
    buf = stereo_reverb(buf, decay=1.2, damp=6000, mix=0.18, name="battleverb")
    return make_loop(buf, n_of(loop_dur))
