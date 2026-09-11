#!/usr/bin/env python3
"""Renders every sound in sounds/ as a spectrogram contact sheet.

The audio equivalent of `projectile_gallery`, and for the same reason: you
cannot judge a set of 180 assets by opening them one at a time, and the
problems that matter are the ones visible when they sit side by side.

A spectrogram shows the things that actually make a game sound cheap, and shows
them faster than listening does:

    horizontal lines after the onset   a struck object ringing — "clank". A few
                                       of them at inharmonic spacings IS the
                                       sound of hitting a metal bucket.
    a sloped line                      a pitch glide. One descending glide on a
                                       tonal element is the cartoon "boing";
                                       thirty bolts all doing it is why a set
                                       reads as generic.
    a solid vertical stripe            a broadband transient — the contact. Its
                                       absence is why a sound has no impact.
    a smooth cloud                     noise. Good for air, fire, gas; if it is
                                       ALL cloud the sound has no identity.
    everything in the middle third     the 2-5kHz fatigue band.

Requires numpy and Pillow (pip install numpy Pillow).

    python3 scripts/sound_gallery.py out/dir            # every sound
    python3 scripts/sound_gallery.py out/dir atk_axe    # only matching names

SND_DIR overrides which directory is read, for auditioning a build before it
replaces sounds/.
"""
import glob
import math
import os
import sys
import wave

import numpy as np
from PIL import Image, ImageDraw

SR = 44100
CELL_W, CELL_H = 300, 150      # spectrogram box per sound
WAVE_H = 26                    # envelope strip under it
PAD, LABEL_H = 10, 16
COLS, ROWS = 4, 6

F_LO, F_HI = 60.0, 16000.0     # log-frequency axis, roughly the audible span
DB_FLOOR = -68.0


def load(path):
    with wave.open(path, "rb") as f:
        ch = f.getnchannels()
        d = np.frombuffer(f.readframes(f.getnframes()), dtype=np.int16).astype(float) / 32768.0
    return d.reshape(-1, 2).mean(axis=1) if ch == 2 else d


def spectrogram(x, width, height):
    """Log-frequency STFT magnitude in dB, resampled to a width x height grid."""
    nfft, hop = 2048, max(1, len(x) // max(1, width))
    frames = max(1, (len(x) - nfft) // hop + 1)
    win = np.hanning(nfft)
    cols = np.zeros((frames, nfft // 2 + 1))
    for i in range(frames):
        seg = x[i * hop: i * hop + nfft]
        if len(seg) < nfft:
            seg = np.pad(seg, (0, nfft - len(seg)))
        cols[i] = np.abs(np.fft.rfft(seg * win))
    freqs = np.fft.rfftfreq(nfft, 1.0 / SR)
    # log-frequency bins, so an octave takes the same vertical space everywhere
    edges = np.logspace(math.log10(F_LO), math.log10(F_HI), height + 1)
    grid = np.zeros((height, frames))
    for r in range(height):
        m = (freqs >= edges[r]) & (freqs < edges[r + 1])
        if m.any():
            grid[r] = cols[:, m].max(axis=1)
        elif r:
            grid[r] = grid[r - 1]
    grid = 20 * np.log10(grid / (grid.max() + 1e-12) + 1e-12)
    grid = np.clip((grid - DB_FLOOR) / (-DB_FLOOR), 0.0, 1.0)
    # time-resample to the cell width
    idx = np.clip((np.arange(width) * frames / width).astype(int), 0, frames - 1)
    return grid[::-1][:, idx]            # flip so high frequencies are at the top


def colourise(v):
    """Dark -> magenta -> orange -> white. High contrast in the quiet range,
    where ring tails and noise floors live."""
    v = np.clip(v, 0, 1)
    r = np.clip(3.2 * v - 0.25, 0, 1)
    g = np.clip(2.6 * v - 1.05, 0, 1)
    b = np.clip(np.where(v < 0.45, 2.0 * v, 1.9 - 2.1 * v) + 0.12 * v, 0, 1)
    return (np.dstack([r, g, b]) * 255).astype(np.uint8)


ENV_FLOOR_DB = -48.0


def envelope_strip(x, width, height):
    """Peak envelope on a dB scale, with -12/-24/-36dB guides.

    Linear was useless here: a tail at -35dB is half a pixel tall, so a sound
    that decays properly and one that holds level both draw as a solid bar and
    you cannot tell them apart — which is exactly the judgement this strip
    exists to make."""
    img = np.zeros((height, width, 3), dtype=np.uint8)
    for g in (-12.0, -24.0, -36.0):
        row = int((1.0 - g / ENV_FLOOR_DB) * (height - 1))
        img[max(0, min(height - 1, height - 1 - row))] = (40, 40, 50)
    step = max(1, len(x) // width)
    for c in range(width):
        seg = x[c * step:(c + 1) * step]
        if not len(seg):
            break
        db = 20 * math.log10(max(np.max(np.abs(seg)), 1e-6))
        h = int(max(0.0, 1.0 - db / ENV_FLOOR_DB) * (height - 1))
        img[height - 1 - h:height, c] = (90, 190, 230)
    return img


def render(paths, out_path, title):
    n = len(paths)
    cw, chh = CELL_W + PAD, CELL_H + WAVE_H + LABEL_H + PAD
    sheet = Image.new("RGB", (COLS * cw + PAD, ROWS * chh + PAD + 20), (14, 14, 20))
    d = ImageDraw.Draw(sheet)
    d.text((PAD, 5), title, fill=(210, 210, 225))
    for i, p in enumerate(paths[: COLS * ROWS]):
        x = load(p)
        col, row = i % COLS, i // COLS
        ox, oy = PAD + col * cw, 22 + PAD + row * chh
        sheet.paste(Image.fromarray(colourise(spectrogram(x, CELL_W, CELL_H))), (ox, oy))
        sheet.paste(Image.fromarray(envelope_strip(x, CELL_W, WAVE_H)), (ox, oy + CELL_H))
        name = os.path.basename(p)[:-4]
        d.text((ox + 2, oy + CELL_H + WAVE_H + 1),
               "%s  %.2fs" % (name, len(x) / SR), fill=(200, 200, 215))
        # octave guides: 125 / 500 / 2k / 8k Hz, so the fatigue band is locatable
        for f in (125, 500, 2000, 8000):
            yy = oy + int(CELL_H * (1 - math.log10(f / F_LO) / math.log10(F_HI / F_LO)))
            d.line([(ox, yy), (ox + 4, yy)], fill=(120, 120, 140))
        d.rectangle([ox, oy, ox + CELL_W - 1, oy + CELL_H - 1], outline=(60, 60, 75))
    sheet.save(out_path)
    return out_path


def main():
    out_dir = sys.argv[1] if len(sys.argv) > 1 else "out/soundgallery"
    match = sys.argv[2] if len(sys.argv) > 2 else ""
    os.makedirs(out_dir, exist_ok=True)
    root = os.path.join(os.environ.get('SND_DIR', os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'sounds')))
    paths = sorted(p for p in glob.glob(os.path.join(root, "*.wav"))
                   if match in os.path.basename(p) and "music" not in os.path.basename(p))
    per = COLS * ROWS
    for page in range((len(paths) + per - 1) // per):
        chunk = paths[page * per:(page + 1) * per]
        out = os.path.join(out_dir, "sounds_%02d.png" % (page + 1))
        render(chunk, out, "grid game sounds — page %d/%d" % (page + 1, (len(paths) + per - 1) // per))
        print(out)


if __name__ == "__main__":
    main()
