#!/usr/bin/env python3
"""Which sounds are a struck piece of metal? Measures it, for every sound.

The audio counterpart of `render_audit.py`. The fault it looks for is the one
this set kept falling into: a pitched layer with an instant attack and an
exponential decay, whose partials are not a harmonic series. That is the
acoustic definition of a struck metal object, and it is what a fire bolt, a
thrown axe and a rope all came out as — "a tin can". It is also invisible in
the numbers anyone normally checks (level, length, band), so it is measured
here directly:

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

Reported per sound:

  ring    share of the 150Hz-10kHz energy (over 600ms) that is ringing partials
  struck  share of it that is struck partials
  inharm  share of the struck energy no harmonic series explains
  clang   struck x (0.35 + 0.65 inharm) x (0.3 + 0.7 x its share in 400Hz-5kHz):
          struck, inharmonic, in the band where a can rings

A voice rings but is not struck; a string or a chord is struck but harmonic, so
both score low. A bolt of fire should not ring at all.

    python3 scripts/sound_audit.py                 # every sound, worst first
    python3 scripts/sound_audit.py atk_axe          # only matching names
    python3 scripts/sound_audit.py --check          # exit 1 if anything that
                                                    # should not ring does

`--check` fails on any sound with clang over CLANG_LIMIT that is not in
MEANT_TO_RING — the sounds that are, legitimately, a struck bell or a plucked
string. SND_DIR overrides the directory read, to audit a build before it
replaces sounds/. Needs numpy.
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
    "atk_hypnotic_melody",   # the Musician's melody IS a mallet figure
    "atk_lute_chord",        # a plucked chord
    "atk_song_wave",         # a reed/brass swell
    "atk_charm_bolt",        # a harp run
    "spawn",                 # the respawn figure: three bell notes
    "death",                 # the knell
    "hit_dealt",             # the hitmarker's tick has to cut through everything
    "atk_howl",              # a held, sung note — a voice rings, it is not struck
}

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
    match = args[0] if args else ""
    root = os.environ.get("SND_DIR", os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "sounds"))
    rows = []
    for p in sorted(glob.glob(os.path.join(root, "*.wav"))):
        name = os.path.basename(p)[:-4]
        if "music" in name or match not in name:
            continue
        r = analyse(load(p))
        if r is not None:
            rows.append((name, r))
    rows.sort(key=lambda kv: -kv[1]["clang"])
    print("%-24s %6s %6s %6s %6s  %s" % ("sound", "clang", "ring", "struck", "inharm",
                                          "loudest struck partials (Hz, share)"))
    faults = []
    for name, r in rows:
        flag = ""
        if r["clang"] > CLANG_LIMIT:
            flag = "  (meant to ring)" if name in MEANT_TO_RING else "  <- rings like struck metal"
            if name not in MEANT_TO_RING:
                faults.append(name)
        print("%-24s %6.3f %6.3f %6.3f %6.2f  %s%s" % (name, r["clang"], r["ring"], r["struck"], r["inharm"],
                                                     " ".join("%d:%.2f" % pp for pp in r["parts"]), flag))
    if check and faults:
        print("\n%d sound(s) ring like struck metal: %s" % (len(faults), ", ".join(faults)))
        sys.exit(1)


if __name__ == "__main__":
    main()
